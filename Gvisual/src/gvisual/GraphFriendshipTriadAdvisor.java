package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

/**
 * GraphFriendshipTriadAdvisor &mdash; per-node social-closure advisor over a
 * <em>single</em> graph snapshot. Sibling to {@link GraphCommunityChurnAdvisor},
 * {@link GraphSignedConflictAdvisor}, {@link GraphCascadingFailureAdvisor},
 * {@link GraphInfluenceSeedAdvisor}, {@link GraphPrivacyExposureAuditor},
 * {@link GraphAdversaryForecaster} and {@link GraphIntelligenceAdvisor}.
 *
 * <p>For every vertex with degree &ge; 2 the advisor counts <em>closed</em>
 * triangles ({@code u-v-w-u}, bonding capital) and <em>open</em> triads
 * (paths {@code v-u-w} with no {@code v-w} edge, bridging / structural-hole
 * capital) and assigns one of seven verdicts:</p>
 *
 * <ul>
 *   <li>{@code BALANCED} &mdash; healthy mix of bonding and bridging.</li>
 *   <li>{@code STRONG_BRIDGER} &mdash; many open triads, few closures
 *       (P1 / P2: a structural-hole broker connecting otherwise disjoint
 *       peers).</li>
 *   <li>{@code TIGHT_CLUSTER} &mdash; nearly every pair of friends is
 *       mutually connected (P2: high bonding, low novelty).</li>
 *   <li>{@code SOCIALLY_THIN} &mdash; degree 2-3 with no triadic context
 *       (P2 watch).</li>
 *   <li>{@code ISOLATED} &mdash; degree &le; 1 (P1: at risk of being
 *       cut off).</li>
 *   <li>{@code POTENTIAL_BROKER} &mdash; only a single closure but many
 *       open triads (P1: introduce-the-friends opportunity).</li>
 *   <li>{@code AT_RISK_OF_CLOSURE} &mdash; high triadic pressure with
 *       low actual closure (P0 in cautious mode &mdash; chronic mediator
 *       who may burn out).</li>
 * </ul>
 *
 * <p>Pure JDK + JUNG. Single file. Never mutates the input graph.
 * Deterministic given a fixed {@link Clock}. JSON output has stable
 * lexicographic key order on each object.</p>
 *
 * @author sauravbhattacharya001
 */
public final class GraphFriendshipTriadAdvisor<V, E> {

    // -- Public types ------------------------------------------------------

    public enum Verdict {
        BALANCED, STRONG_BRIDGER, TIGHT_CLUSTER, SOCIALLY_THIN,
        ISOLATED, POTENTIAL_BROKER, AT_RISK_OF_CLOSURE
    }
    public enum Priority { P0, P1, P2, P3 }
    public enum RiskAppetite { CAUTIOUS, BALANCED, AGGRESSIVE }
    public enum Grade { A, B, C, D, F }

    public static final class NodeTriad<V> {
        public final V node;
        public final Verdict verdict;
        public final Priority priority;
        public final int degree;
        public final int closedTriangles;
        public final int openTriads;
        public final double clusteringCoefficient; // 0..1
        public final double bridgingScore;          // 0..100
        public final double bondingScore;           // 0..100
        public final List<String> reasons;
        public NodeTriad(V node, Verdict verdict, Priority priority, int degree,
                         int closedTriangles, int openTriads,
                         double clusteringCoefficient, double bridgingScore,
                         double bondingScore, List<String> reasons) {
            this.node = node;
            this.verdict = verdict;
            this.priority = priority;
            this.degree = degree;
            this.closedTriangles = closedTriangles;
            this.openTriads = openTriads;
            this.clusteringCoefficient = clusteringCoefficient;
            this.bridgingScore = bridgingScore;
            this.bondingScore = bondingScore;
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
        public final double globalClustering;     // average clustering coefficient
        public final double bridgingFraction;     // fraction of nodes verdict STRONG_BRIDGER / POTENTIAL_BROKER
        public final double bondingFraction;      // fraction of nodes verdict TIGHT_CLUSTER
        public final double isolationFraction;    // fraction of nodes ISOLATED / SOCIALLY_THIN
        public final List<NodeTriad<?>> nodes;
        public final List<Action> playbook;
        public final List<String> insights;
        public Plan(Instant generatedAt, RiskAppetite riskAppetite, Grade grade,
                    String headline, double globalClustering, double bridgingFraction,
                    double bondingFraction, double isolationFraction,
                    List<? extends NodeTriad<?>> nodes, List<Action> playbook,
                    List<String> insights) {
            this.generatedAt = generatedAt;
            this.riskAppetite = riskAppetite;
            this.grade = grade;
            this.headline = headline;
            this.globalClustering = globalClustering;
            this.bridgingFraction = bridgingFraction;
            this.bondingFraction = bondingFraction;
            this.isolationFraction = isolationFraction;
            this.nodes = Collections.unmodifiableList(new ArrayList<NodeTriad<?>>(nodes));
            this.playbook = Collections.unmodifiableList(new ArrayList<>(playbook));
            this.insights = Collections.unmodifiableList(new ArrayList<>(insights));
        }
    }

    // -- Inputs ------------------------------------------------------------

    private final Graph<V, E> graph;
    private RiskAppetite appetite = RiskAppetite.BALANCED;
    private Clock clock = Clock.systemUTC();

    public GraphFriendshipTriadAdvisor(Graph<V, E> graph) {
        if (graph == null) throw new IllegalArgumentException("graph must be non-null");
        this.graph = graph;
    }

    public GraphFriendshipTriadAdvisor<V, E> withRiskAppetite(RiskAppetite a) {
        if (a != null) this.appetite = a; return this;
    }
    public GraphFriendshipTriadAdvisor<V, E> withFixedClock(Clock c) {
        if (c != null) this.clock = c; return this;
    }

    // -- Core analysis -----------------------------------------------------

    public Plan analyze() {
        // Build undirected neighbor map (de-duped).
        Map<V, Set<V>> nbrs = new LinkedHashMap<>();
        for (V v : graph.getVertices()) nbrs.put(v, new LinkedHashSet<V>());
        for (E e : graph.getEdges()) {
            Collection<V> inc = graph.getIncidentVertices(e);
            Iterator<V> it = inc.iterator();
            if (!it.hasNext()) continue;
            V a = it.next();
            V b = it.hasNext() ? it.next() : a;
            if (a == null || b == null || a.equals(b)) continue;
            nbrs.get(a).add(b);
            nbrs.get(b).add(a);
        }

        double appetiteMult = appetiteMultiplier(appetite);

        List<NodeTriad<?>> classified = new ArrayList<>();
        int totalClosed = 0;
        int totalOpen = 0;
        double clusteringSum = 0.0;
        int clusteringDenom = 0;

        for (V v : nbrs.keySet()) {
            Set<V> n = nbrs.get(v);
            int deg = n.size();
            int closed = 0;
            for (V u : n) {
                for (V w : n) {
                    if (u.equals(w)) continue;
                    if (nbrs.get(u).contains(w)) closed++;
                }
            }
            closed /= 2;                // each triangle double-counted
            int possible = deg * (deg - 1) / 2;
            int open = Math.max(0, possible - closed);
            totalClosed += closed;
            totalOpen += open;
            double cc = (possible == 0) ? 0.0 : (double) closed / (double) possible;
            if (deg >= 2) {
                clusteringSum += cc;
                clusteringDenom++;
            }

            // Bridging score = open triads, log-scaled, modulated by appetite.
            double bridging = clamp(20.0 * Math.log10(1.0 + open) * appetiteMult, 0.0, 100.0);
            // Bonding score = closed triangles, log-scaled.
            double bonding  = clamp(25.0 * Math.log10(1.0 + closed) * appetiteMult, 0.0, 100.0);

            List<String> reasons = new ArrayList<>();
            Verdict verdict;
            Priority priority;

            if (deg == 0 || deg == 1) {
                verdict = Verdict.ISOLATED;
                priority = Priority.P1;
                reasons.add(deg == 0 ? "NO_NEIGHBOURS" : "DEGREE_ONE");
            } else if (deg <= 3 && possible <= 3 && closed == 0) {
                verdict = Verdict.SOCIALLY_THIN;
                priority = Priority.P2;
                reasons.add("LOW_DEGREE_NO_CLOSURE");
            } else if (cc >= 0.85) {
                verdict = Verdict.TIGHT_CLUSTER;
                priority = Priority.P2;
                reasons.add("HIGH_CLUSTERING");
                if (deg >= 5) reasons.add("DENSE_NEIGHBOURHOOD");
            } else if (closed == 0 && open >= 3) {
                verdict = Verdict.STRONG_BRIDGER;
                priority = Priority.P1;
                reasons.add("ALL_TRIADS_OPEN");
                reasons.add("STRUCTURAL_HOLE_BROKER");
            } else if (closed == 1 && open >= 5) {
                verdict = Verdict.POTENTIAL_BROKER;
                priority = Priority.P1;
                reasons.add("SINGLE_CLOSURE_MANY_OPEN_TRIADS");
            } else if (open >= 6 && cc < 0.20) {
                verdict = Verdict.AT_RISK_OF_CLOSURE;
                priority = (appetite == RiskAppetite.CAUTIOUS) ? Priority.P0 : Priority.P1;
                reasons.add("CHRONIC_MEDIATOR");
                reasons.add("LOW_CLUSTERING_HIGH_OPEN_TRIADS");
            } else {
                verdict = Verdict.BALANCED;
                priority = Priority.P3;
                reasons.add("HEALTHY_MIX");
            }

            classified.add(new NodeTriad<V>(v, verdict, priority, deg, closed, open,
                    round3(cc), round1(bridging), round1(bonding), reasons));
        }

        // Sort by priority, then bridging+bonding score desc, then node string.
        classified.sort((a, b) -> {
            int pa = a.priority.ordinal();
            int pb = b.priority.ordinal();
            if (pa != pb) return Integer.compare(pa, pb);
            double sa = a.bridgingScore + a.bondingScore;
            double sb = b.bridgingScore + b.bondingScore;
            if (Double.compare(sb, sa) != 0) return Double.compare(sb, sa);
            return Objects.toString(a.node).compareTo(Objects.toString(b.node));
        });

        int total = Math.max(1, classified.size());
        int isolated = 0, bridging = 0, bonding = 0, atRisk = 0, balanced = 0;
        for (NodeTriad<?> nt : classified) {
            switch (nt.verdict) {
                case ISOLATED: case SOCIALLY_THIN: isolated++; break;
                case STRONG_BRIDGER: case POTENTIAL_BROKER: bridging++; break;
                case TIGHT_CLUSTER: bonding++; break;
                case AT_RISK_OF_CLOSURE: atRisk++; break;
                case BALANCED: balanced++; break;
                default: break;
            }
        }
        double bridgingFrac  = bridging / (double) total;
        double bondingFrac   = bonding  / (double) total;
        double isolationFrac = isolated / (double) total;
        double atRiskFrac    = atRisk   / (double) total;
        double globalCC = (clusteringDenom == 0) ? 0.0 : clusteringSum / clusteringDenom;

        List<Action> playbook = buildPlaybook(classified, isolationFrac,
                bridgingFrac, bondingFrac, atRiskFrac, globalCC, classified.size());
        List<String> insights = buildInsights(classified, isolationFrac, bridgingFrac,
                bondingFrac, atRiskFrac, globalCC);

        Grade grade = grade(globalCC, isolationFrac, atRiskFrac,
                bondingFrac, bridgingFrac, classified.size());
        String headline = headline(grade, classified.size(), globalCC,
                bridgingFrac, bondingFrac, isolationFrac);

        return new Plan(clock.instant().atZone(ZoneOffset.UTC).toInstant(), appetite,
                grade, headline, round3(globalCC), round3(bridgingFrac),
                round3(bondingFrac), round3(isolationFrac),
                classified, playbook, insights);
    }

    // -- Playbook ----------------------------------------------------------

    private List<Action> buildPlaybook(List<NodeTriad<?>> nodes, double isolationFrac,
                                       double bridgingFrac, double bondingFrac,
                                       double atRiskFrac, double globalCC, int total) {
        List<Action> out = new ArrayList<>();
        if (total == 0) {
            out.add(new Action("EMPTY_GRAPH", Priority.P3, "Provide a non-empty graph",
                    "No vertices found",
                    "researcher", 1, "high", Collections.<Object>emptyList()));
            return out;
        }

        // Collect targets per category.
        List<Object> atRiskTargets = new ArrayList<>();
        List<Object> bridgerTargets = new ArrayList<>();
        List<Object> brokerTargets = new ArrayList<>();
        List<Object> tightTargets = new ArrayList<>();
        List<Object> isolatedTargets = new ArrayList<>();
        List<Object> thinTargets = new ArrayList<>();
        for (NodeTriad<?> n : nodes) {
            switch (n.verdict) {
                case AT_RISK_OF_CLOSURE: atRiskTargets.add(n.node); break;
                case STRONG_BRIDGER:     bridgerTargets.add(n.node); break;
                case POTENTIAL_BROKER:   brokerTargets.add(n.node); break;
                case TIGHT_CLUSTER:      tightTargets.add(n.node); break;
                case ISOLATED:           isolatedTargets.add(n.node); break;
                case SOCIALLY_THIN:      thinTargets.add(n.node); break;
                default: break;
            }
        }

        // P0: chronic mediators when cautious / aggressive baseline.
        if (!atRiskTargets.isEmpty()) {
            Priority pr = (appetite == RiskAppetite.CAUTIOUS) ? Priority.P0 : Priority.P1;
            out.add(new Action("REDISTRIBUTE_MEDIATION_LOAD", pr,
                    "Spread brokering load away from chronic mediators",
                    "These nodes carry many open triads with very low closure; risk of burn-out",
                    "community_lead", 4, "medium",
                    capTargets(atRiskTargets, 8)));
        }

        // P1: isolated reconnect.
        if (!isolatedTargets.isEmpty() && isolationFrac >= 0.10) {
            out.add(new Action("RECONNECT_ISOLATED_MEMBERS", Priority.P1,
                    "Invite isolated members back into the community",
                    "Isolated fraction is " + pct(isolationFrac) + " of the population",
                    "community_lead", 3, "high",
                    capTargets(isolatedTargets, 10)));
        } else if (!isolatedTargets.isEmpty()) {
            out.add(new Action("RECONNECT_ISOLATED_MEMBERS", Priority.P2,
                    "Pair isolated members with established peers",
                    "Small pocket of isolated nodes (" + isolatedTargets.size() + ")",
                    "community_lead", 2, "high",
                    capTargets(isolatedTargets, 6)));
        }

        // P1: promote bridgers / brokers.
        if (!bridgerTargets.isEmpty()) {
            out.add(new Action("PROMOTE_STRONG_BRIDGERS", Priority.P1,
                    "Surface strong bridgers as cross-community liaisons",
                    "These nodes connect otherwise-disjoint peers; protect their access",
                    "research_chair", 3, "high",
                    capTargets(bridgerTargets, 6)));
        }
        if (!brokerTargets.isEmpty()) {
            out.add(new Action("INTRODUCE_BROKER_NEIGHBOURS", Priority.P1,
                    "Encourage potential brokers to introduce their friends to each other",
                    "Single existing closure + many open triads = easy bonding wins",
                    "community_lead", 2, "high",
                    capTargets(brokerTargets, 8)));
        }

        // P2: tight clusters - diversify exposure.
        if (bondingFrac >= 0.40) {
            out.add(new Action("DIVERSIFY_TIGHT_CLUSTERS", Priority.P2,
                    "Inject cross-cluster activities to expose tight clusters to new peers",
                    "Tight-cluster fraction is " + pct(bondingFrac) + " of the population",
                    "events_team", 3, "medium",
                    capTargets(tightTargets, 8)));
        }

        // P2: socially-thin warmup.
        if (!thinTargets.isEmpty()) {
            out.add(new Action("WARMUP_SOCIALLY_THIN_NODES", Priority.P2,
                    "Run low-stakes events to thicken triadic context for thin members",
                    thinTargets.size() + " nodes have degree but no closure yet",
                    "events_team", 2, "high",
                    capTargets(thinTargets, 8)));
        }

        // P2: global clustering very low / very high warnings.
        if (globalCC < 0.10 && total >= 5) {
            out.add(new Action("RAISE_CLOSURE_GLOBAL", Priority.P2,
                    "Run mixer events to raise average clustering",
                    "Global clustering is only " + fmt(globalCC),
                    "community_lead", 4, "medium",
                    Collections.<Object>emptyList()));
        } else if (globalCC >= 0.80 && total >= 5) {
            out.add(new Action("INJECT_FRESH_BLOOD", Priority.P2,
                    "Recruit new members - clustering is saturating",
                    "Global clustering is " + fmt(globalCC) + ", echo-chamber risk",
                    "recruiter", 4, "medium",
                    Collections.<Object>emptyList()));
        }

        // Risk appetite trim: aggressive drops any lone P2 if any P0/P1 present.
        if (appetite == RiskAppetite.AGGRESSIVE) {
            boolean hasUrgent = false;
            for (Action a : out) {
                if (a.priority == Priority.P0 || a.priority == Priority.P1) {
                    hasUrgent = true; break;
                }
            }
            if (hasUrgent) {
                List<Action> trimmed = new ArrayList<>();
                int p2count = 0;
                for (Action a : out) {
                    if (a.priority == Priority.P2) p2count++;
                }
                for (Action a : out) {
                    if (a.priority == Priority.P2 && p2count == 1) continue;
                    trimmed.add(a);
                }
                out = trimmed;
            }
        }

        // Cautious tail: schedule review when grade is mid-tier.
        if (appetite == RiskAppetite.CAUTIOUS) {
            out.add(new Action("SCHEDULE_TRIAD_REVIEW", Priority.P2,
                    "Re-run the triad audit next term",
                    "Cautious appetite recommends a periodic check-in",
                    "research_chair", 1, "high",
                    Collections.<Object>emptyList()));
        }

        // Healthy fallback.
        boolean any = false;
        for (Action a : out) {
            if (a.priority != Priority.P3) { any = true; break; }
        }
        if (!any) {
            out.add(new Action("HEALTHY_TRIAD_FLEET", Priority.P3,
                    "Maintain current community programming",
                    "No urgent triadic concerns detected",
                    "community_lead", 1, "high",
                    Collections.<Object>emptyList()));
        }

        // Stable sort: P0 first, then by id alphabetically.
        out.sort((a, b) -> {
            int p = Integer.compare(a.priority.ordinal(), b.priority.ordinal());
            if (p != 0) return p;
            return a.id.compareTo(b.id);
        });
        return out;
    }

    private List<Object> capTargets(List<Object> in, int max) {
        if (in.size() <= max) return new ArrayList<>(in);
        List<Object> out = new ArrayList<>(in.subList(0, max));
        return out;
    }

    // -- Insights ----------------------------------------------------------

    private List<String> buildInsights(List<NodeTriad<?>> nodes, double isolationFrac,
                                       double bridgingFrac, double bondingFrac,
                                       double atRiskFrac, double globalCC) {
        List<String> out = new ArrayList<>();
        if (nodes.isEmpty()) {
            out.add("EMPTY_SNAPSHOT");
            return out;
        }
        if (atRiskFrac >= 0.10) out.add("CHRONIC_MEDIATOR_CLUSTER");
        if (bridgingFrac >= 0.20) out.add("STRUCTURAL_HOLE_RICH");
        if (bondingFrac >= 0.40) out.add("TIGHT_CLUSTER_DOMINANT");
        if (isolationFrac >= 0.20) out.add("HIGH_ISOLATION_LOAD");
        if (globalCC < 0.10) out.add("SPARSE_TRIADIC_CONTEXT");
        if (globalCC >= 0.80) out.add("SATURATED_CLUSTERING");
        if (bridgingFrac > 0 && bondingFrac > 0
                && Math.abs(bridgingFrac - bondingFrac) <= 0.05) {
            out.add("BRIDGING_BONDING_BALANCED");
        }
        if (out.isEmpty()) out.add("HEALTHY_TRIAD_PROFILE");
        return out;
    }

    // -- Grade --------------------------------------------------------------

    private Grade grade(double globalCC, double isolationFrac, double atRiskFrac,
                        double bondingFrac, double bridgingFrac, int total) {
        if (total == 0) return Grade.A;
        // F when severe risk dominates.
        if (atRiskFrac >= 0.20 || isolationFrac >= 0.40) return Grade.F;
        if (atRiskFrac >= 0.10 || isolationFrac >= 0.25) return Grade.D;
        // C when imbalanced.
        if (bondingFrac >= 0.60 || bridgingFrac >= 0.50) return Grade.C;
        if (globalCC < 0.05 && total >= 6) return Grade.C;
        // B when slightly off.
        if (isolationFrac >= 0.10 || atRiskFrac > 0) return Grade.B;
        if (bondingFrac >= 0.40 || bridgingFrac >= 0.30) return Grade.B;
        return Grade.A;
    }

    private String headline(Grade grade, int total, double globalCC,
                            double bridgingFrac, double bondingFrac,
                            double isolationFrac) {
        return String.format(Locale.ROOT,
                "VERDICT: grade %s on %d nodes (clustering=%.2f, bridging=%s, bonding=%s, isolated=%s)",
                grade.name(), total, globalCC,
                pct(bridgingFrac), pct(bondingFrac), pct(isolationFrac));
    }

    // -- Helpers -----------------------------------------------------------

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
    private static String fmt(double v) { return String.format(Locale.ROOT, "%.2f", v); }
    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.0f%%", v * 100.0);
    }

    // -- Renderers ---------------------------------------------------------

    public String toText(Plan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append(plan.headline).append('\n');
        sb.append("Generated at: ").append(plan.generatedAt).append('\n');
        sb.append("Risk appetite: ").append(plan.riskAppetite).append('\n');
        sb.append(String.format(Locale.ROOT,
                "Global clustering=%.2f bridging=%s bonding=%s isolated=%s%n",
                plan.globalClustering, pct(plan.bridgingFraction),
                pct(plan.bondingFraction), pct(plan.isolationFraction)));
        sb.append("\n== Top nodes ==\n");
        int shown = 0;
        for (NodeTriad<?> n : plan.nodes) {
            if (shown++ >= 20) break;
            sb.append(String.format(Locale.ROOT,
                    "  [%s] %-20s deg=%d closed=%d open=%d cc=%.2f bridge=%.1f bond=%.1f verdict=%s reasons=%s%n",
                    n.priority, Objects.toString(n.node),
                    n.degree, n.closedTriangles, n.openTriads,
                    n.clusteringCoefficient, n.bridgingScore, n.bondingScore,
                    n.verdict, n.reasons));
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
        sb.append("# Graph Friendship Triad\n\n");
        sb.append("**").append(plan.headline).append("**\n\n");
        sb.append("- Generated at: ").append(plan.generatedAt).append('\n');
        sb.append("- Risk appetite: ").append(plan.riskAppetite).append('\n');
        sb.append("- Global clustering: ").append(fmt(plan.globalClustering)).append('\n');
        sb.append("- Bridging: ").append(pct(plan.bridgingFraction))
          .append(" | Bonding: ").append(pct(plan.bondingFraction))
          .append(" | Isolated: ").append(pct(plan.isolationFraction)).append("\n\n");

        sb.append("## Top nodes\n\n");
        sb.append("| Priority | Node | Verdict | Degree | Closed | Open | CC | Bridge | Bond | Reasons |\n");
        sb.append("|---|---|---|---:|---:|---:|---:|---:|---:|---|\n");
        int shown = 0;
        for (NodeTriad<?> n : plan.nodes) {
            if (shown++ >= 20) break;
            sb.append("| ").append(n.priority)
              .append(" | ").append(Objects.toString(n.node))
              .append(" | ").append(n.verdict)
              .append(" | ").append(n.degree)
              .append(" | ").append(n.closedTriangles)
              .append(" | ").append(n.openTriads)
              .append(" | ").append(fmt(n.clusteringCoefficient))
              .append(" | ").append(String.format(Locale.ROOT, "%.1f", n.bridgingScore))
              .append(" | ").append(String.format(Locale.ROOT, "%.1f", n.bondingScore))
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
        sb.append("  \"bonding_fraction\": ").append(plan.bondingFraction).append(",\n");
        sb.append("  \"bridging_fraction\": ").append(plan.bridgingFraction).append(",\n");
        sb.append("  \"generated_at\": ").append(jstr(Objects.toString(plan.generatedAt))).append(",\n");
        sb.append("  \"global_clustering\": ").append(plan.globalClustering).append(",\n");
        sb.append("  \"grade\": ").append(jstr(plan.grade.name())).append(",\n");
        sb.append("  \"headline\": ").append(jstr(plan.headline)).append(",\n");
        sb.append("  \"isolation_fraction\": ").append(plan.isolationFraction).append(",\n");
        sb.append("  \"risk_appetite\": ").append(jstr(plan.riskAppetite.name())).append(",\n");

        sb.append("  \"nodes\": [");
        boolean first = true;
        for (NodeTriad<?> n : plan.nodes) {
            if (!first) sb.append(','); first = false;
            sb.append("\n    {");
            sb.append("\"bonding_score\": ").append(n.bondingScore);
            sb.append(", \"bridging_score\": ").append(n.bridgingScore);
            sb.append(", \"closed_triangles\": ").append(n.closedTriangles);
            sb.append(", \"clustering_coefficient\": ").append(n.clusteringCoefficient);
            sb.append(", \"degree\": ").append(n.degree);
            sb.append(", \"node\": ").append(jstr(Objects.toString(n.node)));
            sb.append(", \"open_triads\": ").append(n.openTriads);
            sb.append(", \"priority\": ").append(jstr(n.priority.name()));
            sb.append(", \"reasons\": ").append(jstrArr(n.reasons));
            sb.append(", \"verdict\": ").append(jstr(n.verdict.name()));
            sb.append("}");
        }
        sb.append("\n  ],\n");

        sb.append("  \"playbook\": [");
        first = true;
        for (Action a : plan.playbook) {
            if (!first) sb.append(','); first = false;
            sb.append("\n    {");
            sb.append("\"blast_radius\": ").append(a.blastRadius);
            sb.append(", \"id\": ").append(jstr(a.id));
            sb.append(", \"label\": ").append(jstr(a.label));
            sb.append(", \"owner\": ").append(jstr(a.owner));
            sb.append(", \"priority\": ").append(jstr(a.priority.name()));
            sb.append(", \"reason\": ").append(jstr(a.reason));
            sb.append(", \"reversibility\": ").append(jstr(a.reversibility));
            List<String> tgts = new ArrayList<>();
            for (Object o : a.targets) tgts.add(Objects.toString(o));
            sb.append(", \"targets\": ").append(jstrArr(tgts));
            sb.append("}");
        }
        sb.append("\n  ],\n");
        sb.append("  \"insights\": ").append(jstrArr(plan.insights)).append('\n');
        sb.append("}\n");
        return sb.toString();
    }

    private static String jstr(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
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

    private static String jstrArr(List<String> items) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (String s : items) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(jstr(s));
        }
        sb.append(']');
        return sb.toString();
    }
}
