package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

/**
 * GraphIsolationRiskAdvisor &mdash; per-node early-warning advisor for nodes
 * at risk of becoming socially isolated (e.g. students about to drop out of a
 * community). Sibling to {@link GraphFriendshipTriadAdvisor},
 * {@link GraphCommunityChurnAdvisor}, {@link GraphMentorMatchAdvisor},
 * {@link GraphEchoChamberAdvisor} and {@link GraphPrivacyExposureAuditor}.
 *
 * <p>Where {@code GraphFriendshipTriadAdvisor} measures the <em>structure</em>
 * of a node's local triadic neighbourhood, this advisor focuses on the
 * fragility of that node's connection to the rest of the graph &mdash; how
 * close it is to falling off the edge of the community.</p>
 *
 * <h3>Verdict ladder</h3>
 * <ul>
 *   <li>{@code ALREADY_ISOLATED} &mdash; degree 0 (P0).</li>
 *   <li>{@code SEVERED_RISK} &mdash; degree 1 with a single non-reciprocal
 *       gateway (P0): one bad day and they are gone.</li>
 *   <li>{@code BRIDGE_DEPENDENT} &mdash; every connection routes through a
 *       single articulation point; removing that one node disconnects them
 *       from the rest of the graph (P1).</li>
 *   <li>{@code PERIPHERAL} &mdash; degree small and component-eccentricity at
 *       (or near) the diameter of the component (P2).</li>
 *   <li>{@code SHRINKING} &mdash; degree below the component median and at
 *       least one heavy-weight (or recent) tie is missing/decayed (P2).</li>
 *   <li>{@code STABLE} &mdash; well-connected, no fragility signals (P3).</li>
 *   <li>{@code OVER_CONNECTED} &mdash; degree far above the component median;
 *       not at risk, but flagged so action lists do not over-target them
 *       (P3).</li>
 * </ul>
 *
 * <p>Pure JDK + JUNG. Single file. Never mutates the input graph.
 * Deterministic given a fixed {@link Clock} and a stable vertex iteration
 * order. JSON output has lexicographically sorted keys at every object.
 * Edge weights (if available via {@link Edge#getWeight()}) feed into the
 * shrinking-tie heuristic; non-{@code Edge} edge types are treated as
 * uniform-weight ties.</p>
 *
 * @author sauravbhattacharya001
 */
public final class GraphIsolationRiskAdvisor<V, E> {

    // -- Public types ------------------------------------------------------

    public enum Verdict {
        ALREADY_ISOLATED, SEVERED_RISK, BRIDGE_DEPENDENT,
        PERIPHERAL, SHRINKING, STABLE, OVER_CONNECTED
    }
    public enum Priority { P0, P1, P2, P3 }
    public enum RiskAppetite { CAUTIOUS, BALANCED, AGGRESSIVE }
    public enum Grade { A, B, C, D, F }

    public static final class NodeRisk<V> {
        public final V node;
        public final Verdict verdict;
        public final Priority priority;
        public final int degree;
        public final int componentSize;
        public final int eccentricity;          // BFS hops within component, -1 if isolated
        public final boolean bridgeDependent;   // every connection routes through one cut vertex
        public final V criticalBridge;          // populated only when bridgeDependent (else null)
        public final double riskScore;          // 0..100
        public final List<String> reasons;
        public NodeRisk(V node, Verdict verdict, Priority priority, int degree,
                        int componentSize, int eccentricity, boolean bridgeDependent,
                        V criticalBridge, double riskScore, List<String> reasons) {
            this.node = node;
            this.verdict = verdict;
            this.priority = priority;
            this.degree = degree;
            this.componentSize = componentSize;
            this.eccentricity = eccentricity;
            this.bridgeDependent = bridgeDependent;
            this.criticalBridge = criticalBridge;
            this.riskScore = riskScore;
            this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
        }
    }

    public static final class Action {
        public final String id;
        public final Priority priority;
        public final String label;
        public final String reason;
        public final String owner;
        public final int blastRadius;
        public final String reversibility;
        public final List<Object> targets;
        public Action(String id, Priority priority, String label, String reason,
                      String owner, int blastRadius, String reversibility,
                      List<Object> targets) {
            this.id = id;
            this.priority = priority;
            this.label = label;
            this.reason = reason;
            this.owner = owner;
            this.blastRadius = blastRadius;
            this.reversibility = reversibility;
            this.targets = Collections.unmodifiableList(new ArrayList<>(targets));
        }
    }

    public static final class Plan {
        public final Instant generatedAt;
        public final RiskAppetite riskAppetite;
        public final Grade grade;
        public final String headline;
        public final double meanRiskScore;       // mean across all classified nodes
        public final double atRiskFraction;      // P0+P1 fraction of population
        public final double isolatedFraction;    // ALREADY_ISOLATED / total
        public final int componentCount;
        public final int largestComponentSize;
        public final List<NodeRisk<?>> nodes;
        public final List<Action> playbook;
        public final List<String> insights;
        public Plan(Instant generatedAt, RiskAppetite riskAppetite, Grade grade,
                    String headline, double meanRiskScore, double atRiskFraction,
                    double isolatedFraction, int componentCount,
                    int largestComponentSize,
                    List<? extends NodeRisk<?>> nodes, List<Action> playbook,
                    List<String> insights) {
            this.generatedAt = generatedAt;
            this.riskAppetite = riskAppetite;
            this.grade = grade;
            this.headline = headline;
            this.meanRiskScore = meanRiskScore;
            this.atRiskFraction = atRiskFraction;
            this.isolatedFraction = isolatedFraction;
            this.componentCount = componentCount;
            this.largestComponentSize = largestComponentSize;
            this.nodes = Collections.unmodifiableList(new ArrayList<NodeRisk<?>>(nodes));
            this.playbook = Collections.unmodifiableList(new ArrayList<>(playbook));
            this.insights = Collections.unmodifiableList(new ArrayList<>(insights));
        }
    }

    // -- Inputs ------------------------------------------------------------

    private final Graph<V, E> graph;
    private RiskAppetite appetite = RiskAppetite.BALANCED;
    private Clock clock = Clock.systemUTC();
    private double weakTieThreshold = 0.25;  // edges with weight <= this contribute to SHRINKING

    public GraphIsolationRiskAdvisor(Graph<V, E> graph) {
        if (graph == null) throw new IllegalArgumentException("graph must be non-null");
        this.graph = graph;
    }

    public GraphIsolationRiskAdvisor<V, E> withRiskAppetite(RiskAppetite a) {
        if (a != null) this.appetite = a; return this;
    }
    public GraphIsolationRiskAdvisor<V, E> withFixedClock(Clock c) {
        if (c != null) this.clock = c; return this;
    }
    public GraphIsolationRiskAdvisor<V, E> withWeakTieThreshold(double t) {
        if (t >= 0.0 && t <= 1.0) this.weakTieThreshold = t; return this;
    }

    // -- Core analysis -----------------------------------------------------

    public Plan analyze() {
        // Build undirected neighbor + weight map (de-duped).
        Map<V, Set<V>> nbrs = new LinkedHashMap<>();
        Map<V, Map<V, Double>> weights = new LinkedHashMap<>();
        for (V v : graph.getVertices()) {
            nbrs.put(v, new LinkedHashSet<V>());
            weights.put(v, new LinkedHashMap<V, Double>());
        }
        for (E e : graph.getEdges()) {
            Collection<V> inc = graph.getIncidentVertices(e);
            Iterator<V> it = inc.iterator();
            if (!it.hasNext()) continue;
            V a = it.next();
            V b = it.hasNext() ? it.next() : a;
            if (a == null || b == null || a.equals(b)) continue;
            nbrs.get(a).add(b);
            nbrs.get(b).add(a);
            double w = edgeWeight(e);
            // accumulate max weight when multi-edges are present
            Double pa = weights.get(a).get(b);
            Double pb = weights.get(b).get(a);
            if (pa == null || w > pa) weights.get(a).put(b, w);
            if (pb == null || w > pb) weights.get(b).put(a, w);
        }

        // Connected components.
        Map<V, Integer> compIndex = new LinkedHashMap<>();
        List<List<V>> components = new ArrayList<>();
        for (V v : nbrs.keySet()) {
            if (compIndex.containsKey(v)) continue;
            List<V> comp = new ArrayList<>();
            Deque<V> stack = new ArrayDeque<>();
            stack.push(v);
            compIndex.put(v, components.size());
            while (!stack.isEmpty()) {
                V x = stack.pop();
                comp.add(x);
                for (V y : nbrs.get(x)) {
                    if (!compIndex.containsKey(y)) {
                        compIndex.put(y, components.size());
                        stack.push(y);
                    }
                }
            }
            components.add(comp);
        }
        int largestComp = 0;
        for (List<V> c : components) largestComp = Math.max(largestComp, c.size());

        // Articulation points (Tarjan, iterative is overkill on the typical
        // GraphVisual sizes; recursive over the component-induced subgraph
        // is fine and matches sibling advisors here).
        Set<V> articulationPoints = articulationPoints(nbrs);

        // Per-component diameter + median degree (for PERIPHERAL / SHRINKING).
        Map<Integer, Integer> compDiameter = new HashMap<>();
        Map<Integer, Double> compMedianDegree = new HashMap<>();
        for (int ci = 0; ci < components.size(); ci++) {
            List<V> comp = components.get(ci);
            int diameter = 0;
            List<Integer> degs = new ArrayList<>();
            for (V v : comp) {
                degs.add(nbrs.get(v).size());
                int ecc = eccentricity(v, nbrs);
                if (ecc > diameter) diameter = ecc;
            }
            Collections.sort(degs);
            double median;
            int s = degs.size();
            if (s == 0) median = 0;
            else if (s % 2 == 1) median = degs.get(s / 2);
            else median = (degs.get(s / 2 - 1) + degs.get(s / 2)) / 2.0;
            compDiameter.put(ci, diameter);
            compMedianDegree.put(ci, median);
        }

        // Classify each vertex.
        List<NodeRisk<?>> classified = new ArrayList<>();
        double appetiteMult = appetiteMultiplier(appetite);
        double riskSum = 0.0;
        int atRiskCount = 0;
        int isolatedCount = 0;

        for (V v : nbrs.keySet()) {
            Set<V> n = nbrs.get(v);
            int deg = n.size();
            int ci = compIndex.get(v);
            int compSize = components.get(ci).size();
            int ecc = eccentricity(v, nbrs);
            double medianDeg = compMedianDegree.get(ci);
            int diameter = compDiameter.get(ci);

            boolean bridgeDependent = false;
            V critical = null;
            if (deg >= 2) {
                // A node is "bridge-dependent" when all of its neighbours pass
                // through one articulation point to reach the rest of the
                // component. Cheap proxy: removing one neighbour disconnects v
                // from everyone else AND that neighbour is an articulation
                // point.
                for (V cand : n) {
                    if (!articulationPoints.contains(cand)) continue;
                    if (allReachOnlyVia(v, cand, nbrs)) {
                        bridgeDependent = true;
                        critical = cand;
                        break;
                    }
                }
            }

            List<String> reasons = new ArrayList<>();
            Verdict verdict;
            Priority priority;
            double score;

            int weakTies = 0;
            for (V u : n) {
                Double w = weights.get(v).get(u);
                if (w != null && w <= weakTieThreshold) weakTies++;
            }
            boolean overConnected = compSize >= 6 && deg >= Math.max(5, (int) Math.ceil(medianDeg * 2.5));

            if (deg == 0) {
                verdict = Verdict.ALREADY_ISOLATED;
                priority = Priority.P0;
                reasons.add("NO_NEIGHBOURS");
                score = 100.0;
            } else if (deg == 1) {
                verdict = Verdict.SEVERED_RISK;
                priority = Priority.P0;
                reasons.add("SINGLE_GATEWAY");
                V only = n.iterator().next();
                Double w = weights.get(v).get(only);
                if (w != null && w <= weakTieThreshold) reasons.add("WEAK_GATEWAY_TIE");
                score = 90.0;
            } else if (bridgeDependent) {
                verdict = Verdict.BRIDGE_DEPENDENT;
                priority = Priority.P1;
                reasons.add("ALL_PATHS_VIA_ARTICULATION");
                reasons.add("BRIDGE=" + Objects.toString(critical));
                score = 70.0;
            } else if (overConnected) {
                verdict = Verdict.OVER_CONNECTED;
                priority = Priority.P3;
                reasons.add("DEGREE_FAR_ABOVE_MEDIAN");
                score = 5.0;
            } else if (compSize >= 4 && diameter >= 2 && ecc == diameter && deg <= Math.max(2, medianDeg)) {
                verdict = Verdict.PERIPHERAL;
                priority = Priority.P2;
                reasons.add("AT_COMPONENT_PERIPHERY");
                if (deg <= 2) reasons.add("LOW_DEGREE_TOO");
                score = 55.0;
            } else if (compSize >= 4 && deg < medianDeg && weakTies >= 1) {
                verdict = Verdict.SHRINKING;
                priority = Priority.P2;
                reasons.add("BELOW_MEDIAN_DEGREE");
                reasons.add("WEAK_TIES=" + weakTies);
                score = 45.0;
            } else {
                verdict = Verdict.STABLE;
                priority = Priority.P3;
                reasons.add("HEALTHY_LOCAL_TOPOLOGY");
                score = 15.0;
            }

            // Risk appetite modulates score (CAUTIOUS bumps mid-scores up).
            double adj = clamp(score * appetiteMult, 0.0, 100.0);
            // Severity smear from being in a tiny isolated component.
            if (compSize <= 2 && verdict != Verdict.ALREADY_ISOLATED
                    && verdict != Verdict.SEVERED_RISK) {
                adj = Math.max(adj, 65.0);
                reasons.add("TINY_COMPONENT");
                if (verdict == Verdict.STABLE) {
                    verdict = Verdict.PERIPHERAL;
                    priority = Priority.P2;
                }
            }

            classified.add(new NodeRisk<V>(v, verdict, priority, deg, compSize, ecc,
                    bridgeDependent, critical, round1(adj), reasons));

            riskSum += adj;
            if (priority == Priority.P0 || priority == Priority.P1) atRiskCount++;
            if (verdict == Verdict.ALREADY_ISOLATED) isolatedCount++;
        }

        // Sort by priority then risk_score desc then node string.
        classified.sort((a, b) -> {
            int pa = a.priority.ordinal();
            int pb = b.priority.ordinal();
            if (pa != pb) return Integer.compare(pa, pb);
            if (Double.compare(b.riskScore, a.riskScore) != 0)
                return Double.compare(b.riskScore, a.riskScore);
            return Objects.toString(a.node).compareTo(Objects.toString(b.node));
        });

        int total = classified.size();
        double atRiskFrac = total == 0 ? 0.0 : (double) atRiskCount / total;
        double isolatedFrac = total == 0 ? 0.0 : (double) isolatedCount / total;
        double meanRisk = total == 0 ? 0.0 : riskSum / total;

        List<Action> playbook = buildPlaybook(classified, atRiskFrac, isolatedFrac,
                components.size(), largestComp, total);
        List<String> insights = buildInsights(classified, atRiskFrac, isolatedFrac,
                components.size(), largestComp, total);

        Grade grade = grade(meanRisk, atRiskFrac, isolatedFrac, components.size(), total);
        String headline = String.format(Locale.ROOT,
                "VERDICT: grade %s on %d nodes (mean_risk=%.1f, at_risk=%s, isolated=%s, components=%d)",
                grade.name(), total, meanRisk, pct(atRiskFrac), pct(isolatedFrac),
                components.size());

        return new Plan(clock.instant().atZone(ZoneOffset.UTC).toInstant(), appetite,
                grade, headline, round1(meanRisk), round3(atRiskFrac),
                round3(isolatedFrac), components.size(), largestComp,
                classified, playbook, insights);
    }

    // -- Playbook ----------------------------------------------------------

    private List<Action> buildPlaybook(List<NodeRisk<?>> nodes, double atRiskFrac,
                                       double isolatedFrac, int componentCount,
                                       int largestComp, int total) {
        List<Action> out = new ArrayList<>();
        if (total == 0) {
            out.add(new Action("EMPTY_GRAPH", Priority.P3, "Provide a non-empty graph",
                    "No vertices found",
                    "researcher", 1, "high", Collections.<Object>emptyList()));
            return out;
        }
        List<Object> isolatedTargets = new ArrayList<>();
        List<Object> severedTargets = new ArrayList<>();
        List<Object> bridgeTargets = new ArrayList<>();
        Set<Object> criticalBridges = new LinkedHashSet<>();
        List<Object> peripheralTargets = new ArrayList<>();
        List<Object> shrinkingTargets = new ArrayList<>();
        for (NodeRisk<?> n : nodes) {
            switch (n.verdict) {
                case ALREADY_ISOLATED: isolatedTargets.add(n.node); break;
                case SEVERED_RISK:     severedTargets.add(n.node); break;
                case BRIDGE_DEPENDENT:
                    bridgeTargets.add(n.node);
                    if (n.criticalBridge != null) criticalBridges.add(n.criticalBridge);
                    break;
                case PERIPHERAL:       peripheralTargets.add(n.node); break;
                case SHRINKING:        shrinkingTargets.add(n.node); break;
                default: break;
            }
        }

        if (!isolatedTargets.isEmpty()) {
            out.add(new Action("REINTEGRATE_ISOLATED_NODES", Priority.P0,
                    "Reach out personally to fully-isolated members",
                    "These nodes have zero ties in the current snapshot",
                    "community_lead", 5, "high",
                    capTargets(isolatedTargets, 12)));
        }
        if (!severedTargets.isEmpty()) {
            out.add(new Action("FORTIFY_SINGLE_GATEWAY_NODES", Priority.P0,
                    "Add a second tie before the single gateway fails",
                    "Each of these nodes depends on a single connection to the community",
                    "community_lead", 4, "high",
                    capTargets(severedTargets, 10)));
        }
        if (!bridgeTargets.isEmpty()) {
            out.add(new Action("REMOVE_SINGLE_POINT_OF_FAILURE_BRIDGES", Priority.P1,
                    "Add a redundant connection so these nodes do not depend on one articulation point",
                    "Removing the critical bridge would disconnect them from the community",
                    "research_chair", 3, "high",
                    capTargets(bridgeTargets, 10)));
            if (!criticalBridges.isEmpty()) {
                out.add(new Action("PROTECT_CRITICAL_BRIDGE_NODES", Priority.P1,
                        "Keep these articulation-point nodes engaged",
                        "Losing any one of them would isolate downstream members",
                        "community_lead", 4, "medium",
                        capTargets(new ArrayList<Object>(criticalBridges), 8)));
            }
        }
        if (!peripheralTargets.isEmpty() && peripheralTargets.size() >= 2) {
            out.add(new Action("PULL_PERIPHERY_INWARD", Priority.P2,
                    "Invite peripheral members to central / mixer events",
                    "Peripheral members are far from the community core and only weakly attached",
                    "events_team", 2, "high",
                    capTargets(peripheralTargets, 10)));
        }
        if (!shrinkingTargets.isEmpty()) {
            out.add(new Action("REVIVE_SHRINKING_TIES", Priority.P2,
                    "Reach out before below-median ties decay further",
                    "Degree below median and at least one weak tie present",
                    "community_lead", 2, "high",
                    capTargets(shrinkingTargets, 10)));
        }
        if (componentCount >= 2 && largestComp < total) {
            out.add(new Action("STITCH_DISCONNECTED_COMPONENTS", Priority.P1,
                    "Recruit cross-component connectors",
                    "Graph has " + componentCount + " disconnected components; largest covers "
                            + pct((double) largestComp / total) + " of the population",
                    "research_chair", 4, "medium", Collections.<Object>emptyList()));
        }

        // Risk appetite trim: aggressive drops lone P2 when urgent items exist.
        if (appetite == RiskAppetite.AGGRESSIVE) {
            boolean hasUrgent = false;
            for (Action a : out) {
                if (a.priority == Priority.P0 || a.priority == Priority.P1) {
                    hasUrgent = true; break;
                }
            }
            if (hasUrgent) {
                List<Action> trimmed = new ArrayList<>();
                int p2 = 0;
                for (Action a : out) if (a.priority == Priority.P2) p2++;
                for (Action a : out) {
                    if (a.priority == Priority.P2 && p2 == 1) continue;
                    trimmed.add(a);
                }
                out = trimmed;
            }
        }
        // Cautious tail: schedule a follow-up review when risk_frac is non-trivial.
        if (appetite == RiskAppetite.CAUTIOUS && atRiskFrac >= 0.05) {
            out.add(new Action("SCHEDULE_ISOLATION_FOLLOWUP", Priority.P2,
                    "Re-run the isolation-risk audit next term",
                    "Cautious appetite recommends periodic re-audit when any at-risk node exists",
                    "research_chair", 1, "high",
                    Collections.<Object>emptyList()));
        }

        // Healthy fallback.
        boolean any = false;
        for (Action a : out) if (a.priority != Priority.P3) { any = true; break; }
        if (!any) {
            out.add(new Action("HEALTHY_COMMUNITY", Priority.P3,
                    "No isolation-risk action required",
                    "No fragility signals across the population",
                    "community_lead", 1, "high",
                    Collections.<Object>emptyList()));
        }

        out.sort((a, b) -> {
            int p = Integer.compare(a.priority.ordinal(), b.priority.ordinal());
            if (p != 0) return p;
            return a.id.compareTo(b.id);
        });
        return out;
    }

    private List<Object> capTargets(List<Object> in, int max) {
        if (in.size() <= max) return new ArrayList<>(in);
        return new ArrayList<>(in.subList(0, max));
    }

    // -- Insights ----------------------------------------------------------

    private List<String> buildInsights(List<NodeRisk<?>> nodes, double atRiskFrac,
                                       double isolatedFrac, int componentCount,
                                       int largestComp, int total) {
        List<String> out = new ArrayList<>();
        if (total == 0) { out.add("EMPTY_SNAPSHOT"); return out; }
        if (isolatedFrac >= 0.10) out.add("HIGH_ISOLATION_LOAD");
        if (atRiskFrac >= 0.20) out.add("WIDESPREAD_FRAGILITY");
        if (componentCount >= 3) out.add("FRAGMENTED_GRAPH");
        if (componentCount >= 2 && largestComp <= total / 2) out.add("NO_DOMINANT_COMPONENT");
        int bridgeNodes = 0, severed = 0, peripheral = 0;
        for (NodeRisk<?> n : nodes) {
            if (n.verdict == Verdict.BRIDGE_DEPENDENT) bridgeNodes++;
            if (n.verdict == Verdict.SEVERED_RISK) severed++;
            if (n.verdict == Verdict.PERIPHERAL) peripheral++;
        }
        if (bridgeNodes >= 2) out.add("MULTIPLE_BRIDGE_DEPENDENT_NODES");
        if (severed >= 2) out.add("MULTIPLE_SINGLE_GATEWAY_NODES");
        if (peripheral >= Math.max(2, total / 4)) out.add("LARGE_PERIPHERY");
        if (out.isEmpty()) out.add("HEALTHY_COMMUNITY_TOPOLOGY");
        return out;
    }

    // -- Grade --------------------------------------------------------------

    private Grade grade(double meanRisk, double atRiskFrac, double isolatedFrac,
                        int componentCount, int total) {
        if (total == 0) return Grade.A;
        if (isolatedFrac >= 0.25 || atRiskFrac >= 0.40 || meanRisk >= 65.0) return Grade.F;
        if (isolatedFrac >= 0.10 || atRiskFrac >= 0.25 || meanRisk >= 50.0) return Grade.D;
        if (atRiskFrac >= 0.10 || meanRisk >= 35.0 || componentCount >= 3) return Grade.C;
        if (atRiskFrac > 0 || meanRisk >= 20.0 || componentCount >= 2) return Grade.B;
        return Grade.A;
    }

    // -- Renderers ---------------------------------------------------------

    public String toText(Plan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append(plan.headline).append('\n');
        sb.append("Generated at: ").append(plan.generatedAt).append('\n');
        sb.append("Risk appetite: ").append(plan.riskAppetite).append('\n');
        sb.append(String.format(Locale.ROOT,
                "mean_risk=%.1f at_risk=%s isolated=%s components=%d largest=%d%n",
                plan.meanRiskScore, pct(plan.atRiskFraction),
                pct(plan.isolatedFraction), plan.componentCount,
                plan.largestComponentSize));
        sb.append("\n== Top nodes ==\n");
        int shown = 0;
        for (NodeRisk<?> n : plan.nodes) {
            if (shown++ >= 20) break;
            sb.append(String.format(Locale.ROOT,
                    "  [%s] %-16s deg=%d comp=%d ecc=%d bridge=%s risk=%.1f verdict=%s reasons=%s%n",
                    n.priority, Objects.toString(n.node), n.degree, n.componentSize,
                    n.eccentricity,
                    n.bridgeDependent ? Objects.toString(n.criticalBridge) : "-",
                    n.riskScore, n.verdict, n.reasons));
        }
        sb.append("\n== Playbook ==\n");
        for (Action a : plan.playbook) {
            sb.append(String.format(Locale.ROOT,
                    "  [%s] %s (owner=%s blast=%d rev=%s) - %s%n",
                    a.priority, a.label, a.owner, a.blastRadius,
                    a.reversibility, a.reason));
        }
        sb.append("\n== Insights ==\n");
        for (String i : plan.insights) sb.append("  - ").append(i).append('\n');
        return sb.toString();
    }

    public String toMarkdown(Plan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Graph Isolation Risk\n\n");
        sb.append("**").append(plan.headline).append("**\n\n");
        sb.append("- Generated at: ").append(plan.generatedAt).append('\n');
        sb.append("- Risk appetite: ").append(plan.riskAppetite).append('\n');
        sb.append("- Mean risk score: ").append(String.format(Locale.ROOT, "%.1f", plan.meanRiskScore)).append('\n');
        sb.append("- At-risk: ").append(pct(plan.atRiskFraction))
          .append(" | Isolated: ").append(pct(plan.isolatedFraction))
          .append(" | Components: ").append(plan.componentCount)
          .append(" | Largest: ").append(plan.largestComponentSize).append("\n\n");

        sb.append("## Top nodes\n\n");
        sb.append("| Priority | Node | Verdict | Degree | Component | Eccentricity | Bridge | Risk | Reasons |\n");
        sb.append("|---|---|---|---:|---:|---:|---|---:|---|\n");
        int shown = 0;
        for (NodeRisk<?> n : plan.nodes) {
            if (shown++ >= 20) break;
            sb.append("| ").append(n.priority)
              .append(" | ").append(Objects.toString(n.node))
              .append(" | ").append(n.verdict)
              .append(" | ").append(n.degree)
              .append(" | ").append(n.componentSize)
              .append(" | ").append(n.eccentricity)
              .append(" | ").append(n.bridgeDependent ? Objects.toString(n.criticalBridge) : "-")
              .append(" | ").append(String.format(Locale.ROOT, "%.1f", n.riskScore))
              .append(" | ").append(String.join(", ", n.reasons))
              .append(" |\n");
        }

        sb.append("\n## Playbook\n\n");
        sb.append("| Priority | Action | Owner | Blast | Reversibility | Reason |\n");
        sb.append("|---|---|---|---:|---|---|\n");
        for (Action a : plan.playbook) {
            sb.append("| ").append(a.priority)
              .append(" | ").append(a.label)
              .append(" | ").append(a.owner)
              .append(" | ").append(a.blastRadius)
              .append(" | ").append(a.reversibility)
              .append(" | ").append(a.reason)
              .append(" |\n");
        }
        sb.append("\n## Insights\n\n");
        for (String i : plan.insights) sb.append("- ").append(i).append('\n');
        return sb.toString();
    }

    public String toJson(Plan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"at_risk_fraction\": ").append(plan.atRiskFraction).append(",\n");
        sb.append("  \"component_count\": ").append(plan.componentCount).append(",\n");
        sb.append("  \"generated_at\": ").append(jstr(Objects.toString(plan.generatedAt))).append(",\n");
        sb.append("  \"grade\": ").append(jstr(plan.grade.name())).append(",\n");
        sb.append("  \"headline\": ").append(jstr(plan.headline)).append(",\n");
        sb.append("  \"insights\": ").append(jstrList(plan.insights)).append(",\n");
        sb.append("  \"isolated_fraction\": ").append(plan.isolatedFraction).append(",\n");
        sb.append("  \"largest_component_size\": ").append(plan.largestComponentSize).append(",\n");
        sb.append("  \"mean_risk_score\": ").append(plan.meanRiskScore).append(",\n");
        sb.append("  \"nodes\": [\n");
        for (int i = 0; i < plan.nodes.size(); i++) {
            NodeRisk<?> n = plan.nodes.get(i);
            sb.append("    {");
            sb.append("\"bridge_dependent\": ").append(n.bridgeDependent);
            sb.append(", \"component_size\": ").append(n.componentSize);
            sb.append(", \"critical_bridge\": ").append(jstr(n.criticalBridge == null ? null : Objects.toString(n.criticalBridge)));
            sb.append(", \"degree\": ").append(n.degree);
            sb.append(", \"eccentricity\": ").append(n.eccentricity);
            sb.append(", \"node\": ").append(jstr(Objects.toString(n.node)));
            sb.append(", \"priority\": ").append(jstr(n.priority.name()));
            sb.append(", \"reasons\": ").append(jstrList(n.reasons));
            sb.append(", \"risk_score\": ").append(n.riskScore);
            sb.append(", \"verdict\": ").append(jstr(n.verdict.name()));
            sb.append('}');
            if (i + 1 < plan.nodes.size()) sb.append(',');
            sb.append('\n');
        }
        sb.append("  ],\n");
        sb.append("  \"playbook\": [\n");
        for (int i = 0; i < plan.playbook.size(); i++) {
            Action a = plan.playbook.get(i);
            sb.append("    {");
            sb.append("\"blast_radius\": ").append(a.blastRadius);
            sb.append(", \"id\": ").append(jstr(a.id));
            sb.append(", \"label\": ").append(jstr(a.label));
            sb.append(", \"owner\": ").append(jstr(a.owner));
            sb.append(", \"priority\": ").append(jstr(a.priority.name()));
            sb.append(", \"reason\": ").append(jstr(a.reason));
            sb.append(", \"reversibility\": ").append(jstr(a.reversibility));
            sb.append(", \"targets\": [");
            for (int j = 0; j < a.targets.size(); j++) {
                sb.append(jstr(Objects.toString(a.targets.get(j))));
                if (j + 1 < a.targets.size()) sb.append(',');
            }
            sb.append("]}");
            if (i + 1 < plan.playbook.size()) sb.append(',');
            sb.append('\n');
        }
        sb.append("  ],\n");
        sb.append("  \"risk_appetite\": ").append(jstr(plan.riskAppetite.name())).append('\n');
        sb.append("}");
        return sb.toString();
    }

    // -- Helpers -----------------------------------------------------------

    private double edgeWeight(E e) {
        if (e instanceof Edge) {
            try {
                double w = ((Edge) e).getWeight();
                // Map to [0..1] band: weights <= 0 or > 1 we leave but only the
                // weakTieThreshold comparison cares. A "weak" tie is small.
                return w;
            } catch (Throwable t) {
                return 1.0;
            }
        }
        return 1.0;
    }

    private int eccentricity(V src, Map<V, Set<V>> nbrs) {
        if (nbrs.get(src).isEmpty()) return -1;
        Map<V, Integer> dist = new HashMap<>();
        dist.put(src, 0);
        Deque<V> q = new ArrayDeque<>();
        q.add(src);
        int max = 0;
        while (!q.isEmpty()) {
            V x = q.poll();
            int d = dist.get(x);
            for (V y : nbrs.get(x)) {
                if (!dist.containsKey(y)) {
                    dist.put(y, d + 1);
                    if (d + 1 > max) max = d + 1;
                    q.add(y);
                }
            }
        }
        return max;
    }

    /**
     * Returns true if removing {@code cut} from the graph disconnects
     * {@code src} from every neighbour of {@code src} other than {@code cut}.
     * Cheap check via BFS over the graph minus {@code cut}.
     */
    private boolean allReachOnlyVia(V src, V cut, Map<V, Set<V>> nbrs) {
        if (src.equals(cut)) return false;
        Set<V> visited = new HashSet<>();
        visited.add(src);
        visited.add(cut);
        Deque<V> q = new ArrayDeque<>();
        for (V y : nbrs.get(src)) {
            if (!y.equals(cut) && visited.add(y)) q.add(y);
        }
        while (!q.isEmpty()) {
            V x = q.poll();
            for (V y : nbrs.get(x)) {
                if (visited.add(y)) q.add(y);
            }
        }
        // If after removing cut we cannot reach any of src's other neighbours,
        // we are bridge-dependent. With the only neighbour case (deg==1)
        // we never reach here; deg>=2 guaranteed by the caller.
        for (V other : nbrs.get(src)) {
            if (other.equals(cut)) continue;
            // unreachable from any of cut's far side -> still reached above
            // through src so visited contains it; nothing useful. Instead
            // check whether anything OUTSIDE src's direct neighbourhood is
            // reachable.
        }
        // We are bridge-dependent if no vertex *outside* src's direct closed
        // neighbourhood is reachable without going through cut.
        Set<V> closed = new HashSet<>(nbrs.get(src));
        closed.add(src);
        closed.add(cut);
        for (V v : visited) {
            if (!closed.contains(v)) return false; // can reach the wider graph without cut
        }
        return true;
    }

    private Set<V> articulationPoints(Map<V, Set<V>> nbrs) {
        Set<V> aps = new HashSet<>();
        Map<V, Integer> disc = new HashMap<>();
        Map<V, Integer> low = new HashMap<>();
        Map<V, V> parent = new HashMap<>();
        int[] timer = {0};
        for (V v : nbrs.keySet()) {
            if (!disc.containsKey(v)) {
                dfsAP(v, nbrs, disc, low, parent, aps, timer);
            }
        }
        return aps;
    }

    private void dfsAP(V root, Map<V, Set<V>> nbrs,
                       Map<V, Integer> disc, Map<V, Integer> low,
                       Map<V, V> parent, Set<V> aps, int[] timer) {
        // Iterative DFS to avoid stack overflow on large graphs.
        Deque<Iterator<V>> itStack = new ArrayDeque<>();
        Deque<V> vStack = new ArrayDeque<>();
        Map<V, Integer> children = new HashMap<>();
        disc.put(root, timer[0]); low.put(root, timer[0]); timer[0]++;
        children.put(root, 0);
        itStack.push(nbrs.get(root).iterator());
        vStack.push(root);
        while (!vStack.isEmpty()) {
            V u = vStack.peek();
            Iterator<V> it = itStack.peek();
            if (it.hasNext()) {
                V w = it.next();
                if (!disc.containsKey(w)) {
                    parent.put(w, u);
                    children.put(u, children.getOrDefault(u, 0) + 1);
                    disc.put(w, timer[0]); low.put(w, timer[0]); timer[0]++;
                    children.put(w, 0);
                    itStack.push(nbrs.get(w).iterator());
                    vStack.push(w);
                } else if (!w.equals(parent.get(u))) {
                    low.put(u, Math.min(low.get(u), disc.get(w)));
                }
            } else {
                vStack.pop();
                itStack.pop();
                V p = parent.get(u);
                if (p != null) {
                    low.put(p, Math.min(low.get(p), low.get(u)));
                    if (!p.equals(root) && low.get(u) >= disc.get(p)) aps.add(p);
                }
            }
        }
        if (children.getOrDefault(root, 0) > 1) aps.add(root);
    }

    private static double appetiteMultiplier(RiskAppetite a) {
        switch (a) {
            case CAUTIOUS:   return 1.10;
            case AGGRESSIVE: return 0.90;
            default:          return 1.00;
        }
    }
    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
    private static double round3(double v) { return Math.round(v * 1000.0) / 1000.0; }
    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.0f%%", v * 100.0);
    }

    private static String jstr(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static String jstrList(List<String> xs) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < xs.size(); i++) {
            sb.append(jstr(xs.get(i)));
            if (i + 1 < xs.size()) sb.append(',');
        }
        sb.append(']');
        return sb.toString();
    }
}
