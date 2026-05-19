package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

/**
 * GraphCommunityChurnAdvisor &mdash; agentic community-evolution churn
 * advisor. Sibling to {@link GraphAdversaryForecaster},
 * {@link GraphPrivacyExposureAuditor},
 * {@link GraphInfluenceSeedAdvisor},
 * {@link GraphCascadingFailureAdvisor} and
 * {@link GraphIntelligenceAdvisor}.
 *
 * <p>Given two graph snapshots (a <em>baseline</em> and <em>current</em>
 * view of the same population, e.g. last term vs this term in a
 * student-community study), the advisor labels nodes as
 * {@code STABLE / MIGRANT / BRIDGE_FORMING / ISOLATING / NEW_ARRIVAL /
 * DEPARTED / CHURNING} and produces a P0-P3 community-management
 * playbook with an A-F portfolio grade.</p>
 *
 * <p>Pure JDK + JUNG. Single file. Never mutates the input graphs.
 * Deterministic given a fixed {@link Clock}. If community labels are not
 * provided, the advisor falls back to weakly-connected-component labels.</p>
 *
 * @author sauravbhattacharya001
 */
public final class GraphCommunityChurnAdvisor<V, E> {

    // -- Public types ------------------------------------------------------

    public enum Verdict {
        STABLE, MIGRANT, BRIDGE_FORMING, ISOLATING,
        NEW_ARRIVAL, DEPARTED, CHURNING
    }
    public enum Priority { P0, P1, P2, P3 }
    public enum RiskAppetite { CAUTIOUS, BALANCED, AGGRESSIVE }
    public enum Grade { A, B, C, D, F }

    public static final class NodeChurn<V> {
        public final V node;
        public final Verdict verdict;
        public final Priority priority;
        public final double churnScore;          // 0..100
        public final int baselineDegree;
        public final int currentDegree;
        public final int retainedEdges;
        public final int lostEdges;
        public final int gainedEdges;
        public final Object baselineCommunity;
        public final Object currentCommunity;
        public final List<String> reasons;
        public NodeChurn(V node, Verdict verdict, Priority priority,
                         double churnScore, int baselineDegree, int currentDegree,
                         int retainedEdges, int lostEdges, int gainedEdges,
                         Object baselineCommunity, Object currentCommunity,
                         List<String> reasons) {
            this.node = node;
            this.verdict = verdict;
            this.priority = priority;
            this.churnScore = churnScore;
            this.baselineDegree = baselineDegree;
            this.currentDegree = currentDegree;
            this.retainedEdges = retainedEdges;
            this.lostEdges = lostEdges;
            this.gainedEdges = gainedEdges;
            this.baselineCommunity = baselineCommunity;
            this.currentCommunity = currentCommunity;
            this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
        }
    }

    public static final class CommunityEvolution {
        public final Object baselineCommunityId;
        public final List<Object> currentCommunityIds; // sorted by member overlap desc
        public final String pattern; // STABLE / SPLIT / MERGED_INTO / DISSOLVED / SHRUNK / GREW
        public final int baselineSize;
        public final int currentDominantSize;
        public final double retentionRatio; // members kept in dominant successor / baselineSize
        public CommunityEvolution(Object baselineCommunityId,
                                  List<Object> currentCommunityIds,
                                  String pattern, int baselineSize,
                                  int currentDominantSize, double retentionRatio) {
            this.baselineCommunityId = baselineCommunityId;
            this.currentCommunityIds = Collections.unmodifiableList(new ArrayList<>(currentCommunityIds));
            this.pattern = pattern;
            this.baselineSize = baselineSize;
            this.currentDominantSize = currentDominantSize;
            this.retentionRatio = retentionRatio;
        }
    }

    public static final class Action {
        public final String id;
        public final Priority priority;
        public final String label;
        public final String reason;
        public final String owner;
        public final int blastRadius;     // 1..5
        public final String reversibility; // low / medium / high
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
        public final double portfolioChurnScore; // 0..100
        public final Grade grade;
        public final String headline;
        public final List<NodeChurn<?>> nodes;          // all classified nodes, sorted by priority then churnScore desc
        public final List<CommunityEvolution> communities;
        public final List<Action> playbook;
        public final List<String> insights;
        public final int retainedNodes;
        public final int newArrivals;
        public final int departures;
        @SuppressWarnings({"unchecked","rawtypes"})
        public Plan(Instant generatedAt, RiskAppetite riskAppetite,
                    double portfolioChurnScore, Grade grade, String headline,
                    List<? extends NodeChurn<?>> nodes,
                    List<CommunityEvolution> communities,
                    List<Action> playbook, List<String> insights,
                    int retainedNodes, int newArrivals, int departures) {
            this.generatedAt = generatedAt;
            this.riskAppetite = riskAppetite;
            this.portfolioChurnScore = portfolioChurnScore;
            this.grade = grade;
            this.headline = headline;
            this.nodes = Collections.unmodifiableList(new ArrayList<NodeChurn<?>>(nodes));
            this.communities = Collections.unmodifiableList(new ArrayList<>(communities));
            this.playbook = Collections.unmodifiableList(new ArrayList<>(playbook));
            this.insights = Collections.unmodifiableList(new ArrayList<>(insights));
            this.retainedNodes = retainedNodes;
            this.newArrivals = newArrivals;
            this.departures = departures;
        }
    }

    // -- Inputs ------------------------------------------------------------

    private final Graph<V, E> baseline;
    private final Graph<V, E> current;
    private RiskAppetite appetite = RiskAppetite.BALANCED;
    private Clock clock = Clock.systemUTC();
    private Map<V, Object> baselineCommunities = null;
    private Map<V, Object> currentCommunities = null;

    public GraphCommunityChurnAdvisor(Graph<V, E> baseline, Graph<V, E> current) {
        if (baseline == null || current == null) {
            throw new IllegalArgumentException("baseline and current must be non-null");
        }
        this.baseline = baseline;
        this.current = current;
    }

    public GraphCommunityChurnAdvisor<V, E> withRiskAppetite(RiskAppetite a) {
        if (a != null) this.appetite = a; return this;
    }
    public GraphCommunityChurnAdvisor<V, E> withFixedClock(Clock c) {
        if (c != null) this.clock = c; return this;
    }
    public GraphCommunityChurnAdvisor<V, E> withCommunities(Map<V, Object> baselineComm,
                                                             Map<V, Object> currentComm) {
        this.baselineCommunities = baselineComm;
        this.currentCommunities = currentComm;
        return this;
    }

    // -- Core analysis -----------------------------------------------------

    public Plan analyze() {
        Set<V> baseV = new LinkedHashSet<>(baseline.getVertices());
        Set<V> curV = new LinkedHashSet<>(current.getVertices());
        Set<V> all = new LinkedHashSet<>();
        all.addAll(baseV); all.addAll(curV);

        Map<V, Object> bComm = (baselineCommunities != null)
                ? baselineCommunities
                : computeComponents(baseline);
        Map<V, Object> cComm = (currentCommunities != null)
                ? currentCommunities
                : computeComponents(current);

        Map<V, Set<V>> baseNbrs = neighborMap(baseline);
        Map<V, Set<V>> curNbrs = neighborMap(current);

        List<NodeChurn<V>> nodeChurns = new ArrayList<>();
        int retainedNodes = 0, newArrivals = 0, departures = 0;
        double appetiteMult = switch (appetite) {
            case CAUTIOUS   -> 1.15;
            case AGGRESSIVE -> 0.85;
            default         -> 1.0;
        };

        for (V v : all) {
            boolean inBase = baseV.contains(v);
            boolean inCur = curV.contains(v);
            Set<V> bn = baseNbrs.getOrDefault(v, Collections.emptySet());
            Set<V> cn = curNbrs.getOrDefault(v, Collections.emptySet());
            int bDeg = bn.size();
            int cDeg = cn.size();
            Set<V> retained = new HashSet<>(bn); retained.retainAll(cn);
            Set<V> lost = new HashSet<>(bn); lost.removeAll(cn);
            Set<V> gained = new HashSet<>(cn); gained.removeAll(bn);

            Object bC = bComm.get(v);
            Object cC = cComm.get(v);

            Verdict verdict;
            List<String> reasons = new ArrayList<>();
            double score;

            if (!inBase && inCur) {
                verdict = Verdict.NEW_ARRIVAL;
                reasons.add("NEW_ARRIVAL_DEG_" + cDeg);
                score = cDeg >= 3 ? 25 : (cDeg >= 1 ? 35 : 55);
                newArrivals++;
            } else if (inBase && !inCur) {
                verdict = Verdict.DEPARTED;
                reasons.add("DEPARTED_BASELINE_DEG_" + bDeg);
                score = bDeg >= 5 ? 75 : (bDeg >= 1 ? 55 : 40);
                departures++;
            } else {
                retainedNodes++;
                int unionDeg = Math.max(1, bn.size() + gained.size());
                double jaccard = (bn.isEmpty() && cn.isEmpty())
                        ? 1.0
                        : (double) retained.size() / unionDeg;
                boolean commChanged = !Objects.equals(bC, cC);
                double base = (1.0 - jaccard) * 60.0;
                if (commChanged) base += 20.0;
                if (cDeg == 0 && bDeg > 0) base += 25.0;
                // bridge-forming: gained many edges to distinct communities
                Set<Object> gainedCommunities = new HashSet<>();
                for (V g : gained) {
                    Object gc = cComm.get(g);
                    if (gc != null) gainedCommunities.add(gc);
                }
                boolean bridgeForming = gainedCommunities.size() >= 2 && gained.size() >= 3;

                score = base;
                if (jaccard >= 0.75 && !commChanged) {
                    verdict = Verdict.STABLE;
                    reasons.add("HIGH_RETENTION_J_" + fmt(jaccard));
                } else if (cDeg == 0) {
                    verdict = Verdict.ISOLATING;
                    reasons.add("LOST_ALL_TIES");
                } else if (bridgeForming) {
                    verdict = Verdict.BRIDGE_FORMING;
                    reasons.add("CONNECTS_" + gainedCommunities.size() + "_COMMUNITIES");
                } else if (commChanged) {
                    verdict = Verdict.MIGRANT;
                    reasons.add("COMMUNITY_CHANGED");
                } else if (jaccard < 0.30) {
                    verdict = Verdict.CHURNING;
                    reasons.add("LOW_TIE_RETENTION_J_" + fmt(jaccard));
                } else {
                    verdict = Verdict.STABLE;
                    reasons.add("MODERATE_RETENTION_J_" + fmt(jaccard));
                    score = Math.min(score, 25);
                }
                if (!lost.isEmpty()) reasons.add("LOST_" + lost.size());
                if (!gained.isEmpty()) reasons.add("GAINED_" + gained.size());
            }

            score = clamp(score * appetiteMult, 0, 100);
            Priority p = priorityOf(verdict, score);
            nodeChurns.add(new NodeChurn<>(v, verdict, p, round1(score),
                    bDeg, cDeg, retained.size(), lost.size(), gained.size(),
                    bC, cC, reasons));
        }

        // Sort: priority asc, churnScore desc, then by toString of node for stability.
        nodeChurns.sort((a, b) -> {
            int c1 = Integer.compare(a.priority.ordinal(), b.priority.ordinal());
            if (c1 != 0) return c1;
            int c2 = Double.compare(b.churnScore, a.churnScore);
            if (c2 != 0) return c2;
            return Objects.toString(a.node).compareTo(Objects.toString(b.node));
        });

        // Community evolution
        List<CommunityEvolution> evo = buildEvolution(baseV, curV, bComm, cComm);

        // Portfolio score
        double portfolio = nodeChurns.isEmpty()
                ? 0.0
                : nodeChurns.stream().mapToDouble(n -> n.churnScore).average().orElse(0.0);
        if (appetite == RiskAppetite.CAUTIOUS) portfolio = Math.min(100, portfolio + 4);
        if (appetite == RiskAppetite.AGGRESSIVE) portfolio = Math.max(0, portfolio - 4);

        long p0 = nodeChurns.stream().filter(n -> n.priority == Priority.P0).count();
        long dissolved = evo.stream().filter(e -> "DISSOLVED".equals(e.pattern)).count();
        long splits = evo.stream().filter(e -> "SPLIT".equals(e.pattern)).count();

        Grade grade;
        if (p0 >= 5 || dissolved >= 2 || portfolio >= 70) grade = Grade.F;
        else if (p0 >= 2 || portfolio >= 55 || dissolved >= 1) grade = Grade.D;
        else if (portfolio >= 40 || splits >= 2) grade = Grade.C;
        else if (portfolio >= 25) grade = Grade.B;
        else grade = Grade.A;

        // Playbook
        List<Action> playbook = buildPlaybook(nodeChurns, evo, grade);

        // Insights
        List<String> insights = buildInsights(nodeChurns, evo, retainedNodes,
                newArrivals, departures);

        String headline = String.format(Locale.ROOT,
                "VERDICT: grade=%s churnScore=%.1f base=%d cur=%d arrivals=%d departures=%d P0=%d",
                grade, portfolio, baseV.size(), curV.size(), newArrivals, departures, p0);

        return new Plan(clock.instant(), appetite, round1(portfolio), grade, headline,
                nodeChurns, evo, playbook, insights,
                retainedNodes, newArrivals, departures);
    }

    private Priority priorityOf(Verdict v, double s) {
        return switch (v) {
            case ISOLATING, CHURNING -> (s >= 60 ? Priority.P0 : Priority.P1);
            case DEPARTED -> (s >= 60 ? Priority.P0 : (s >= 40 ? Priority.P1 : Priority.P2));
            case MIGRANT, BRIDGE_FORMING -> (s >= 50 ? Priority.P1 : Priority.P2);
            case NEW_ARRIVAL -> (s >= 50 ? Priority.P1 : Priority.P2);
            case STABLE -> Priority.P3;
        };
    }

    private List<CommunityEvolution> buildEvolution(Set<V> baseV, Set<V> curV,
                                                    Map<V, Object> bComm,
                                                    Map<V, Object> cComm) {
        Map<Object, Set<V>> bGroups = group(baseV, bComm);
        Map<Object, Set<V>> cGroups = group(curV, cComm);
        List<CommunityEvolution> out = new ArrayList<>();
        List<Object> orderedBaselineIds = new ArrayList<>(bGroups.keySet());
        orderedBaselineIds.sort(Comparator.comparing(Objects::toString));
        for (Object bid : orderedBaselineIds) {
            Set<V> members = bGroups.get(bid);
            // Count successors
            Map<Object, Integer> succ = new LinkedHashMap<>();
            int retained = 0;
            for (V v : members) {
                Object cid = cComm.get(v);
                if (cid == null) continue;
                retained++;
                succ.merge(cid, 1, Integer::sum);
            }
            List<Map.Entry<Object,Integer>> ordered = new ArrayList<>(succ.entrySet());
            ordered.sort((a, b) -> {
                int c = Integer.compare(b.getValue(), a.getValue());
                if (c != 0) return c;
                return Objects.toString(a.getKey()).compareTo(Objects.toString(b.getKey()));
            });
            List<Object> succIds = new ArrayList<>();
            for (Map.Entry<Object,Integer> e : ordered) succIds.add(e.getKey());

            int baselineSize = members.size();
            int dominant = ordered.isEmpty() ? 0 : ordered.get(0).getValue();
            double retentionRatio = baselineSize == 0 ? 0.0 : (double) dominant / baselineSize;

            String pattern;
            if (baselineSize == 0) {
                pattern = "STABLE";
            } else if (retained == 0) {
                pattern = "DISSOLVED";
            } else if (succ.size() >= 2 && dominant < baselineSize * 0.6) {
                pattern = "SPLIT";
            } else if (retentionRatio >= 0.8 && cGroups.containsKey(succIds.get(0))
                    && cGroups.get(succIds.get(0)).size() > baselineSize * 1.4) {
                pattern = "MERGED_INTO";
            } else if (retentionRatio < 0.7) {
                pattern = "SHRUNK";
            } else if (cGroups.containsKey(succIds.get(0))
                    && cGroups.get(succIds.get(0)).size() > baselineSize * 1.2) {
                pattern = "GREW";
            } else {
                pattern = "STABLE";
            }
            out.add(new CommunityEvolution(bid, succIds, pattern,
                    baselineSize, dominant, round1(retentionRatio * 100) / 100.0));
        }
        return out;
    }

    private List<Action> buildPlaybook(List<NodeChurn<V>> nodes,
                                        List<CommunityEvolution> evo, Grade grade) {
        Map<String, Action> by = new LinkedHashMap<>();
        // P0 actions
        List<Object> isolating = nodes.stream()
                .filter(n -> n.verdict == Verdict.ISOLATING && n.priority == Priority.P0)
                .map(n -> (Object) n.node).toList();
        if (!isolating.isEmpty()) {
            by.put("REACH_OUT_ISOLATING", new Action("REACH_OUT_ISOLATING",
                    Priority.P0, "Reach out to isolating members",
                    isolating.size() + " member(s) lost all ties since baseline.",
                    "community_lead", 3, "high", isolating));
        }
        List<Object> churning = nodes.stream()
                .filter(n -> n.verdict == Verdict.CHURNING && n.priority == Priority.P0)
                .map(n -> (Object) n.node).toList();
        if (!churning.isEmpty()) {
            by.put("STABILIZE_CHURNING", new Action("STABILIZE_CHURNING",
                    Priority.P0, "Stabilize high-churn members",
                    churning.size() + " member(s) replaced most of their network.",
                    "community_lead", 3, "medium", churning));
        }
        long dissolved = evo.stream().filter(e -> "DISSOLVED".equals(e.pattern)).count();
        if (dissolved >= 1) {
            List<Object> dis = evo.stream().filter(e -> "DISSOLVED".equals(e.pattern))
                    .map(e -> e.baselineCommunityId).toList();
            by.put("INVESTIGATE_DISSOLVED_GROUPS", new Action("INVESTIGATE_DISSOLVED_GROUPS",
                    Priority.P0, "Investigate dissolved groups",
                    dissolved + " baseline community/communities disappeared.",
                    "researcher", 4, "low", dis));
        }
        long departedP0 = nodes.stream()
                .filter(n -> n.verdict == Verdict.DEPARTED && n.priority == Priority.P0).count();
        if (departedP0 >= 3) {
            List<Object> dep = nodes.stream()
                    .filter(n -> n.verdict == Verdict.DEPARTED && n.priority == Priority.P0)
                    .map(n -> (Object) n.node).toList();
            by.put("EXIT_INTERVIEW_HIGH_DEGREE_DEPARTED",
                    new Action("EXIT_INTERVIEW_HIGH_DEGREE_DEPARTED", Priority.P0,
                            "Exit-interview departed hubs",
                            "High-degree departures may signal a systemic issue.",
                            "researcher", 3, "high", dep));
        }
        // P1
        List<Object> splits = evo.stream().filter(e -> "SPLIT".equals(e.pattern))
                .map(e -> e.baselineCommunityId).toList();
        if (!splits.isEmpty()) {
            by.put("REVIEW_COMMUNITY_SPLITS", new Action("REVIEW_COMMUNITY_SPLITS",
                    Priority.P1, "Review community splits",
                    splits.size() + " community/communities split into multiple subgroups.",
                    "researcher", 2, "high", splits));
        }
        List<Object> bridges = nodes.stream()
                .filter(n -> n.verdict == Verdict.BRIDGE_FORMING)
                .map(n -> (Object) n.node).toList();
        if (!bridges.isEmpty()) {
            by.put("AMPLIFY_BRIDGE_BUILDERS", new Action("AMPLIFY_BRIDGE_BUILDERS",
                    Priority.P1, "Amplify bridge-building members",
                    bridges.size() + " member(s) connect 2+ communities; cultivate them.",
                    "community_lead", 2, "high", bridges));
        }
        List<Object> migrants = nodes.stream()
                .filter(n -> n.verdict == Verdict.MIGRANT && n.priority == Priority.P1)
                .map(n -> (Object) n.node).toList();
        if (migrants.size() >= 5) {
            by.put("STUDY_MIGRATION_FLOWS", new Action("STUDY_MIGRATION_FLOWS",
                    Priority.P1, "Study migration flows",
                    migrants.size() + " members switched communities.",
                    "researcher", 2, "high", migrants));
        }
        // P2
        long arrivals = nodes.stream().filter(n -> n.verdict == Verdict.NEW_ARRIVAL).count();
        if (arrivals >= 3) {
            List<Object> ars = nodes.stream().filter(n -> n.verdict == Verdict.NEW_ARRIVAL)
                    .map(n -> (Object) n.node).toList();
            by.put("ONBOARD_NEW_ARRIVALS", new Action("ONBOARD_NEW_ARRIVALS",
                    Priority.P2, "Onboard new arrivals",
                    arrivals + " new members entered the network.",
                    "community_lead", 1, "high", ars));
        }
        if (grade != Grade.A && grade != Grade.B) {
            by.putIfAbsent("SCHEDULE_NEXT_AUDIT", new Action("SCHEDULE_NEXT_AUDIT",
                    Priority.P2, "Schedule next churn audit",
                    "Grade " + grade + " warrants a follow-up audit.",
                    "researcher", 1, "high", Collections.emptyList()));
        }
        // P3 fallback
        if (by.isEmpty()) {
            by.put("MAINTAIN_OBSERVABILITY", new Action("MAINTAIN_OBSERVABILITY",
                    Priority.P3, "Maintain observability",
                    "No high-priority churn signals.",
                    "researcher", 1, "high", Collections.emptyList()));
        }
        // Aggressive trims lone P2 when P0/P1 present
        if (appetite == RiskAppetite.AGGRESSIVE) {
            boolean hasHigh = by.values().stream().anyMatch(a ->
                    a.priority == Priority.P0 || a.priority == Priority.P1);
            if (hasHigh) {
                by.values().removeIf(a -> a.priority == Priority.P3);
            }
        }
        // Cautious adds SCHEDULE_NEXT_AUDIT when grade <= C
        if (appetite == RiskAppetite.CAUTIOUS
                && (grade == Grade.C || grade == Grade.D || grade == Grade.F)) {
            by.putIfAbsent("SCHEDULE_NEXT_AUDIT", new Action("SCHEDULE_NEXT_AUDIT",
                    Priority.P2, "Schedule next churn audit",
                    "Cautious appetite + grade " + grade + ".",
                    "researcher", 1, "high", Collections.emptyList()));
        }
        List<Action> ordered = new ArrayList<>(by.values());
        ordered.sort((a, b) -> {
            int c = Integer.compare(a.priority.ordinal(), b.priority.ordinal());
            if (c != 0) return c;
            return a.id.compareTo(b.id);
        });
        return ordered;
    }

    private List<String> buildInsights(List<NodeChurn<V>> nodes,
                                       List<CommunityEvolution> evo,
                                       int retained, int arrivals, int departures) {
        List<String> out = new ArrayList<>();
        int total = nodes.size();
        if (total == 0) { out.add("EMPTY_SNAPSHOT"); return out; }
        long stable = nodes.stream().filter(n -> n.verdict == Verdict.STABLE).count();
        long churning = nodes.stream().filter(n -> n.verdict == Verdict.CHURNING).count();
        long bridges = nodes.stream().filter(n -> n.verdict == Verdict.BRIDGE_FORMING).count();
        long migrants = nodes.stream().filter(n -> n.verdict == Verdict.MIGRANT).count();

        long dissolved = evo.stream().filter(e -> "DISSOLVED".equals(e.pattern)).count();
        long splits = evo.stream().filter(e -> "SPLIT".equals(e.pattern)).count();

        if (departures > arrivals * 2 && departures >= 3) out.add("NET_SHRINK");
        if (arrivals > departures * 2 && arrivals >= 3) out.add("NET_GROWTH");
        if (stable > total * 0.7) out.add("HIGH_STABILITY");
        if (churning >= 3) out.add("CHURN_CLUSTER");
        if (bridges >= 2) out.add("BRIDGE_BUILDERS_PRESENT");
        if (migrants >= 5) out.add("HIGH_MIGRATION");
        if (dissolved >= 1) out.add("COMMUNITY_DISSOLUTION");
        if (splits >= 2) out.add("MULTI_COMMUNITY_SPLIT");
        if (out.isEmpty()) out.add("BASELINE_EVOLUTION");
        return out;
    }

    // -- Helpers -----------------------------------------------------------

    private Map<V, Set<V>> neighborMap(Graph<V, E> g) {
        Map<V, Set<V>> out = new LinkedHashMap<>();
        for (V v : g.getVertices()) {
            Set<V> nbrs = new HashSet<>();
            Collection<V> neighbors = g.getNeighbors(v);
            if (neighbors != null) nbrs.addAll(neighbors);
            nbrs.remove(v);
            out.put(v, nbrs);
        }
        return out;
    }

    private Map<V, Object> computeComponents(Graph<V, E> g) {
        Map<V, Object> labels = new LinkedHashMap<>();
        int comp = 0;
        for (V root : g.getVertices()) {
            if (labels.containsKey(root)) continue;
            String lid = "C" + (++comp);
            Deque<V> stack = new ArrayDeque<>();
            stack.push(root);
            while (!stack.isEmpty()) {
                V v = stack.pop();
                if (labels.containsKey(v)) continue;
                labels.put(v, lid);
                Collection<V> nbrs = g.getNeighbors(v);
                if (nbrs != null) for (V n : nbrs) if (!labels.containsKey(n)) stack.push(n);
            }
        }
        return labels;
    }

    private Map<Object, Set<V>> group(Set<V> verts, Map<V, Object> labels) {
        Map<Object, Set<V>> out = new LinkedHashMap<>();
        for (V v : verts) {
            Object lab = labels.get(v);
            if (lab == null) continue;
            out.computeIfAbsent(lab, k -> new LinkedHashSet<>()).add(v);
        }
        return out;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    // -- Renderers ---------------------------------------------------------

    public String toText(Plan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append(plan.headline).append('\n');
        sb.append("Generated at: ").append(plan.generatedAt).append('\n');
        sb.append("Risk appetite: ").append(plan.riskAppetite).append('\n');
        sb.append("Retained=").append(plan.retainedNodes)
                .append(" Arrivals=").append(plan.newArrivals)
                .append(" Departures=").append(plan.departures).append('\n');
        sb.append("\n== Top nodes ==\n");
        int shown = 0;
        for (NodeChurn<?> n : plan.nodes) {
            if (shown++ >= 20) break;
            sb.append(String.format(Locale.ROOT,
                    "  [%s] %-20s score=%5.1f verdict=%s bDeg=%d cDeg=%d gained=%d lost=%d reasons=%s%n",
                    n.priority, Objects.toString(n.node),
                    n.churnScore, n.verdict, n.baselineDegree, n.currentDegree,
                    n.gainedEdges, n.lostEdges, n.reasons));
        }
        sb.append("\n== Community evolution ==\n");
        for (CommunityEvolution e : plan.communities) {
            sb.append(String.format(Locale.ROOT,
                    "  %-15s %-12s size=%d->dominant=%d retention=%.2f succ=%s%n",
                    Objects.toString(e.baselineCommunityId), e.pattern,
                    e.baselineSize, e.currentDominantSize, e.retentionRatio,
                    e.currentCommunityIds));
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
        sb.append("# Graph Community Churn\n\n");
        sb.append("**").append(plan.headline).append("**\n\n");
        sb.append("- Generated at: ").append(plan.generatedAt).append('\n');
        sb.append("- Risk appetite: ").append(plan.riskAppetite).append('\n');
        sb.append("- Retained: ").append(plan.retainedNodes)
          .append(" | Arrivals: ").append(plan.newArrivals)
          .append(" | Departures: ").append(plan.departures).append("\n\n");

        sb.append("## Top nodes\n\n");
        sb.append("| Priority | Node | Score | Verdict | bDeg | cDeg | Gained | Lost | Reasons |\n");
        sb.append("|---|---|---:|---|---:|---:|---:|---:|---|\n");
        int shown = 0;
        for (NodeChurn<?> n : plan.nodes) {
            if (shown++ >= 20) break;
            sb.append("| ").append(n.priority)
              .append(" | ").append(Objects.toString(n.node))
              .append(" | ").append(String.format(Locale.ROOT, "%.1f", n.churnScore))
              .append(" | ").append(n.verdict)
              .append(" | ").append(n.baselineDegree)
              .append(" | ").append(n.currentDegree)
              .append(" | ").append(n.gainedEdges)
              .append(" | ").append(n.lostEdges)
              .append(" | ").append(String.join(", ", n.reasons))
              .append(" |\n");
        }
        sb.append("\n## Community evolution\n\n");
        sb.append("| Baseline | Pattern | Size | Dominant | Retention | Successors |\n");
        sb.append("|---|---|---:|---:|---:|---|\n");
        for (CommunityEvolution e : plan.communities) {
            sb.append("| ").append(Objects.toString(e.baselineCommunityId))
              .append(" | ").append(e.pattern)
              .append(" | ").append(e.baselineSize)
              .append(" | ").append(e.currentDominantSize)
              .append(" | ").append(String.format(Locale.ROOT, "%.2f", e.retentionRatio))
              .append(" | ").append(e.currentCommunityIds)
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

    /** Deterministic JSON renderer (stable key order, no external deps). */
    public String toJson(Plan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"generated_at\": ").append(jstr(Objects.toString(plan.generatedAt))).append(",\n");
        sb.append("  \"grade\": ").append(jstr(plan.grade.name())).append(",\n");
        sb.append("  \"headline\": ").append(jstr(plan.headline)).append(",\n");
        sb.append("  \"portfolio_churn_score\": ").append(plan.portfolioChurnScore).append(",\n");
        sb.append("  \"risk_appetite\": ").append(jstr(plan.riskAppetite.name())).append(",\n");
        sb.append("  \"summary\": {\n");
        sb.append("    \"arrivals\": ").append(plan.newArrivals).append(",\n");
        sb.append("    \"departures\": ").append(plan.departures).append(",\n");
        sb.append("    \"retained\": ").append(plan.retainedNodes).append("\n");
        sb.append("  },\n");
        sb.append("  \"nodes\": [");
        boolean first = true;
        for (NodeChurn<?> n : plan.nodes) {
            if (!first) sb.append(','); first = false;
            sb.append("\n    {");
            sb.append("\"baseline_community\": ").append(jstr(Objects.toString(n.baselineCommunity, "")));
            sb.append(", \"baseline_degree\": ").append(n.baselineDegree);
            sb.append(", \"churn_score\": ").append(n.churnScore);
            sb.append(", \"current_community\": ").append(jstr(Objects.toString(n.currentCommunity, "")));
            sb.append(", \"current_degree\": ").append(n.currentDegree);
            sb.append(", \"gained_edges\": ").append(n.gainedEdges);
            sb.append(", \"lost_edges\": ").append(n.lostEdges);
            sb.append(", \"node\": ").append(jstr(Objects.toString(n.node)));
            sb.append(", \"priority\": ").append(jstr(n.priority.name()));
            sb.append(", \"reasons\": ").append(jstrArr(n.reasons));
            sb.append(", \"retained_edges\": ").append(n.retainedEdges);
            sb.append(", \"verdict\": ").append(jstr(n.verdict.name()));
            sb.append("}");
        }
        sb.append("\n  ],\n");
        sb.append("  \"communities\": [");
        first = true;
        for (CommunityEvolution e : plan.communities) {
            if (!first) sb.append(','); first = false;
            sb.append("\n    {");
            sb.append("\"baseline_community_id\": ").append(jstr(Objects.toString(e.baselineCommunityId)));
            sb.append(", \"baseline_size\": ").append(e.baselineSize);
            sb.append(", \"current_dominant_size\": ").append(e.currentDominantSize);
            sb.append(", \"pattern\": ").append(jstr(e.pattern));
            sb.append(", \"retention_ratio\": ").append(e.retentionRatio);
            List<String> succAsStr = new ArrayList<>();
            for (Object o : e.currentCommunityIds) succAsStr.add(Objects.toString(o));
            sb.append(", \"successors\": ").append(jstrArr(succAsStr));
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
                case '"': sb.append("\\\""); break;
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
    private static String jstrArr(Collection<String> items) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (String s : items) {
            if (!first) sb.append(", "); first = false;
            sb.append(jstr(s));
        }
        sb.append(']');
        return sb.toString();
    }
}
