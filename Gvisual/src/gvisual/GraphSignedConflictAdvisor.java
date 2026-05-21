package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

/**
 * GraphSignedConflictAdvisor &mdash; agentic signed-graph conflict-mediation
 * advisor. Sibling to {@link GraphAdversaryForecaster},
 * {@link GraphPrivacyExposureAuditor}, {@link GraphInfluenceSeedAdvisor},
 * {@link GraphCascadingFailureAdvisor}, {@link GraphCommunityChurnAdvisor},
 * and {@link GraphIntelligenceAdvisor}.
 *
 * <p>Given a signed {@link Graph} of {@link Edge}s (positive vs negative
 * relationships), the advisor fuses {@link SignedGraphAnalyzer} primitives
 * (triangle census, structural balance, frustration, coalitions) into a
 * P0&ndash;P3 mediation playbook with an A&ndash;F portfolio grade.</p>
 *
 * <p>Per-vertex verdicts: {@code PEACEMAKER / NEUTRAL / POLARIZED /
 * AGITATOR / ISOLATED_HOSTILE / BROKER}. Per-edge verdicts (for frustrated
 * edges): {@code FLIP_CANDIDATE / CUT_CANDIDATE / MEDIATE_CANDIDATE}.</p>
 *
 * <p>Pure JDK + JUNG. Single file. Never mutates the input graph.
 * Deterministic given a fixed {@link Clock}.</p>
 *
 * @author sauravbhattacharya001
 */
public final class GraphSignedConflictAdvisor {

    // -- Public types ------------------------------------------------------

    public enum Verdict {
        PEACEMAKER, NEUTRAL, POLARIZED, AGITATOR, ISOLATED_HOSTILE, BROKER
    }
    public enum EdgeVerdict {
        FLIP_CANDIDATE, CUT_CANDIDATE, MEDIATE_CANDIDATE
    }
    public enum Priority { P0, P1, P2, P3 }
    public enum RiskAppetite { CAUTIOUS, BALANCED, AGGRESSIVE }
    public enum Grade { A, B, C, D, F }

    public static final class NodeConflict {
        public final String node;
        public final Verdict verdict;
        public final Priority priority;
        public final double conflictScore;       // 0..100
        public final int degree;
        public final int negativeIncident;
        public final int positiveIncident;
        public final int frustratedIncident;
        public final double polarization;        // 0..1
        public final List<String> reasons;
        public NodeConflict(String node, Verdict verdict, Priority priority,
                            double conflictScore, int degree,
                            int negativeIncident, int positiveIncident,
                            int frustratedIncident, double polarization,
                            List<String> reasons) {
            this.node = node;
            this.verdict = verdict;
            this.priority = priority;
            this.conflictScore = conflictScore;
            this.degree = degree;
            this.negativeIncident = negativeIncident;
            this.positiveIncident = positiveIncident;
            this.frustratedIncident = frustratedIncident;
            this.polarization = polarization;
            this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
        }
    }

    public static final class EdgeConflict {
        public final String u;
        public final String v;
        public final EdgeVerdict verdict;
        public final Priority priority;
        public final String reason;
        public EdgeConflict(String u, String v, EdgeVerdict verdict,
                            Priority priority, String reason) {
            this.u = u;
            this.v = v;
            this.verdict = verdict;
            this.priority = priority;
            this.reason = reason;
        }
    }

    public static final class Action {
        public final String id;
        public final Priority priority;
        public final String label;
        public final String owner;
        public final int blastRadius;
        public final String reversibility;
        public final String reason;
        public final List<Object> targets;
        public Action(String id, Priority priority, String label,
                      String owner, int blastRadius, String reversibility,
                      String reason, List<?> targets) {
            this.id = id;
            this.priority = priority;
            this.label = label;
            this.owner = owner;
            this.blastRadius = blastRadius;
            this.reversibility = reversibility;
            this.reason = reason;
            List<Object> tcopy = new ArrayList<>();
            if (targets != null) tcopy.addAll(targets);
            this.targets = Collections.unmodifiableList(tcopy);
        }
    }

    public static final class Plan {
        public final String headline;
        public final Grade grade;
        public final RiskAppetite riskAppetite;
        public final Instant generatedAt;
        public final int vertexCount;
        public final int edgeCount;
        public final int positiveEdges;
        public final int negativeEdges;
        public final int frustrationIndex;
        public final double strongBalanceDegree;
        public final double weakBalanceDegree;
        public final int trianglesTotal;
        public final int trianglesPpp;
        public final int trianglesPpn;
        public final int trianglesPnn;
        public final int trianglesNnn;
        public final double polarizationScore;   // 0..100
        public final List<NodeConflict> nodes;
        public final List<EdgeConflict> edges;
        public final List<Set<String>> coalitions;
        public final List<Action> playbook;
        public final List<String> insights;

        Plan(String headline, Grade grade, RiskAppetite riskAppetite,
             Instant generatedAt, int vertexCount, int edgeCount,
             int positiveEdges, int negativeEdges, int frustrationIndex,
             double strongBalanceDegree, double weakBalanceDegree,
             int trianglesTotal, int trianglesPpp, int trianglesPpn,
             int trianglesPnn, int trianglesNnn, double polarizationScore,
             List<NodeConflict> nodes, List<EdgeConflict> edges,
             List<Set<String>> coalitions, List<Action> playbook,
             List<String> insights) {
            this.headline = headline;
            this.grade = grade;
            this.riskAppetite = riskAppetite;
            this.generatedAt = generatedAt;
            this.vertexCount = vertexCount;
            this.edgeCount = edgeCount;
            this.positiveEdges = positiveEdges;
            this.negativeEdges = negativeEdges;
            this.frustrationIndex = frustrationIndex;
            this.strongBalanceDegree = strongBalanceDegree;
            this.weakBalanceDegree = weakBalanceDegree;
            this.trianglesTotal = trianglesTotal;
            this.trianglesPpp = trianglesPpp;
            this.trianglesPpn = trianglesPpn;
            this.trianglesPnn = trianglesPnn;
            this.trianglesNnn = trianglesNnn;
            this.polarizationScore = polarizationScore;
            this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
            this.edges = Collections.unmodifiableList(new ArrayList<>(edges));
            List<Set<String>> ccopy = new ArrayList<>();
            for (Set<String> c : coalitions) ccopy.add(Collections.unmodifiableSet(new LinkedHashSet<>(c)));
            this.coalitions = Collections.unmodifiableList(ccopy);
            this.playbook = Collections.unmodifiableList(new ArrayList<>(playbook));
            this.insights = Collections.unmodifiableList(new ArrayList<>(insights));
        }
    }

    // -- Internal state ----------------------------------------------------

    private final Graph<String, Edge> graph;
    private final SignedGraphAnalyzer analyzer;
    private Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private RiskAppetite appetite = RiskAppetite.BALANCED;
    private int topK = 20;

    public GraphSignedConflictAdvisor(Graph<String, Edge> graph) {
        if (graph == null) throw new IllegalArgumentException("graph must not be null");
        this.graph = graph;
        this.analyzer = new SignedGraphAnalyzer(graph);
    }

    public GraphSignedConflictAdvisor withRiskAppetite(RiskAppetite r) {
        if (r != null) this.appetite = r;
        return this;
    }

    public GraphSignedConflictAdvisor withFixedClock(Clock c) {
        if (c != null) this.clock = c;
        return this;
    }

    public GraphSignedConflictAdvisor withTopK(int k) {
        if (k > 0) this.topK = k;
        return this;
    }

    // -- Core analyze ------------------------------------------------------

    public Plan analyze() {
        Instant now = clock.instant();
        int vc = graph.getVertexCount();
        int ec = graph.getEdgeCount();

        // Trivial / empty case
        if (vc <= 1) {
            List<Action> pb = new ArrayList<>();
            pb.add(new Action("HEALTHY_NETWORK", Priority.P3,
                    "Healthy network", "researcher", 1, "high",
                    "No significant conflict signal in a trivial graph.",
                    Collections.emptyList()));
            List<String> ins = new ArrayList<>();
            ins.add("EMPTY_OR_TRIVIAL");
            String headline = String.format(Locale.ROOT,
                    "VERDICT: grade=A polarization=0.0 agitators=0 frustration=0/%d", ec);
            return new Plan(headline, Grade.A, appetite, now, vc, ec, 0, 0, 0,
                    1.0, 1.0, 0, 0, 0, 0, 0, 0.0,
                    Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), pb, ins);
        }

        int posEdges = analyzer.countPositiveEdges();
        int negEdges = analyzer.countNegativeEdges();
        SignedGraphAnalyzer.TriangleCensus tri = analyzer.triangleCensus();
        int frustration = analyzer.frustrationIndex();
        List<Edge> frustratedEdges = analyzer.findFrustratedEdges();
        Map<String, Double> polarMap = analyzer.vertexPolarization();
        List<Set<String>> coalitions = analyzer.findCoalitions();

        // coalition lookup
        Map<String, Integer> coalitionOf = new HashMap<>();
        for (int i = 0; i < coalitions.size(); i++) {
            for (String v : coalitions.get(i)) coalitionOf.put(v, i);
        }

        // frustrated edge counts per vertex
        Map<String, Integer> frustratedDeg = new HashMap<>();
        for (Edge e : frustratedEdges) {
            String a = e.getVertex1();
            String b = e.getVertex2();
            frustratedDeg.merge(a, 1, Integer::sum);
            frustratedDeg.merge(b, 1, Integer::sum);
        }

        // max degree
        int maxDeg = 1;
        for (String v : graph.getVertices()) {
            int d = graph.getNeighborCount(v);
            if (d > maxDeg) maxDeg = d;
        }

        double appetiteMul = appetite == RiskAppetite.CAUTIOUS ? 1.15
                : (appetite == RiskAppetite.AGGRESSIVE ? 0.85 : 1.0);

        // Build NodeConflicts
        List<NodeConflict> nodes = new ArrayList<>();
        List<String> verts = new ArrayList<>(graph.getVertices());
        Collections.sort(verts);

        for (String v : verts) {
            Collection<Edge> incident = graph.getIncidentEdges(v);
            int deg = incident == null ? 0 : incident.size();
            int neg = 0, pos = 0;
            if (incident != null) for (Edge e : incident) {
                if (analyzer.isNegative(e)) neg++; else pos++;
            }
            double pol = polarMap.getOrDefault(v, 0.0);
            int frInc = frustratedDeg.getOrDefault(v, 0);
            double frShare = deg == 0 ? 0.0 : (double) frInc / deg;

            // cross-coalition negative share
            int crossNeg = 0;
            if (incident != null && !coalitions.isEmpty()) {
                Integer myC = coalitionOf.get(v);
                for (Edge e : incident) {
                    if (!analyzer.isNegative(e)) continue;
                    String other = e.getVertex1().equals(v) ? e.getVertex2() : e.getVertex1();
                    Integer oc = coalitionOf.get(other);
                    if (myC != null && oc != null && !myC.equals(oc)) crossNeg++;
                }
            }
            double crossShare = deg == 0 ? 0.0 : (double) crossNeg / deg;
            double degPressure = (double) deg / (double) maxDeg;
            double raw = pol * 45.0 + frShare * 30.0 + crossShare * 15.0 + degPressure * 10.0;
            double score = clamp(raw * appetiteMul, 0.0, 100.0);

            List<String> reasons = new ArrayList<>();
            Verdict verdict;
            Priority priority;

            boolean spansCoalitions = false;
            if (coalitions.size() >= 2 && incident != null) {
                Set<Integer> reached = new HashSet<>();
                for (Edge e : incident) {
                    if (analyzer.isNegative(e)) continue;
                    String other = e.getVertex1().equals(v) ? e.getVertex2() : e.getVertex1();
                    Integer oc = coalitionOf.get(other);
                    if (oc != null) reached.add(oc);
                }
                spansCoalitions = reached.size() >= 2;
            }

            if (deg >= 3 && pol >= 0.75 && frInc >= 2) {
                verdict = Verdict.AGITATOR;
                priority = Priority.P0;
                reasons.add("high_polarization");
                reasons.add("frustrated_endpoints");
            } else if (deg >= 2 && neg == deg) {
                verdict = Verdict.ISOLATED_HOSTILE;
                priority = Priority.P1;
                reasons.add("all_negative_incident");
            } else if (deg >= 3 && pol >= 0.50) {
                verdict = Verdict.POLARIZED;
                priority = Priority.P1;
                reasons.add("polarization>=0.50");
            } else if (spansCoalitions) {
                verdict = Verdict.BROKER;
                priority = Priority.P2;
                reasons.add("spans_coalitions");
            } else if (pos >= 3 && pol <= 0.10) {
                verdict = Verdict.PEACEMAKER;
                priority = Priority.P3;
                reasons.add("high_positive_low_polarization");
            } else {
                verdict = Verdict.NEUTRAL;
                priority = Priority.P3;
                reasons.add("baseline");
            }

            nodes.add(new NodeConflict(v, verdict, priority, round1(score),
                    deg, neg, pos, frInc, round2(pol), reasons));
        }

        // Sort nodes desc by conflictScore, then by name
        nodes.sort((a, b) -> {
            int c = Double.compare(b.conflictScore, a.conflictScore);
            if (c != 0) return c;
            return a.node.compareTo(b.node);
        });
        // Top-K
        if (nodes.size() > topK) nodes = new ArrayList<>(nodes.subList(0, topK));

        // Edge verdicts on frustrated edges
        Map<String, Verdict> verdictMap = new HashMap<>();
        for (NodeConflict n : nodes) verdictMap.put(n.node, n.verdict);
        // Also include vertices outside topK with NEUTRAL fallback for lookup safety
        // (we still have polarMap to do quick checks)

        List<EdgeConflict> eConflicts = new ArrayList<>();
        // Sort frustrated edges for determinism
        List<Edge> sortedFr = new ArrayList<>(frustratedEdges);
        sortedFr.sort(Comparator.comparing((Edge e) -> ord(e.getVertex1(), e.getVertex2()))
                .thenComparing(e -> e.getVertex1() + "|" + e.getVertex2()));
        for (Edge e : sortedFr) {
            String u = e.getVertex1();
            String w = e.getVertex2();
            double pu = polarMap.getOrDefault(u, 0.0);
            double pw = polarMap.getOrDefault(w, 0.0);
            Verdict vu = verdictMap.get(u);
            Verdict vw = verdictMap.get(w);
            EdgeVerdict ev;
            Priority epri;
            String reason;
            boolean uBad = vu == Verdict.AGITATOR || vu == Verdict.ISOLATED_HOSTILE;
            boolean wBad = vw == Verdict.AGITATOR || vw == Verdict.ISOLATED_HOSTILE;
            boolean uPeace = vu == Verdict.PEACEMAKER || vu == Verdict.BROKER;
            boolean wPeace = vw == Verdict.PEACEMAKER || vw == Verdict.BROKER;
            if (uBad && wBad) {
                ev = EdgeVerdict.CUT_CANDIDATE;
                epri = Priority.P1;
                reason = "low_repair_value";
            } else if (uPeace || wPeace) {
                ev = EdgeVerdict.MEDIATE_CANDIDATE;
                epri = Priority.P1;
                reason = "peacemaker_or_broker_endpoint";
            } else if (pu >= 0.5 && pw >= 0.5) {
                ev = EdgeVerdict.FLIP_CANDIDATE;
                epri = Priority.P1;
                reason = "both_endpoints_polarized";
            } else {
                ev = EdgeVerdict.MEDIATE_CANDIDATE;
                epri = Priority.P1;
                reason = "frustrated_default";
            }
            // canonicalize order for stable string output (does NOT mutate edge)
            String a = u.compareTo(w) <= 0 ? u : w;
            String b = u.compareTo(w) <= 0 ? w : u;
            eConflicts.add(new EdgeConflict(a, b, ev, epri, reason));
        }

        // Portfolio polarization score
        double portMean = 0.0;
        if (!nodes.isEmpty()) {
            double s = 0.0;
            for (NodeConflict n : nodes) s += n.conflictScore;
            portMean = s / nodes.size();
        }
        portMean = round1(portMean);

        // Grade
        long agitators = nodes.stream().filter(n -> n.verdict == Verdict.AGITATOR).count();
        double weak = tri.weakBalanceDegree();
        double strong = tri.strongBalanceDegree();
        Grade grade;
        if (agitators >= 1 && weak < 0.50) grade = Grade.F;
        else if (weak < 0.60 || agitators >= 2) grade = Grade.D;
        else if (weak < 0.75) grade = Grade.C;
        else if (weak < 0.90) grade = Grade.B;
        else grade = Grade.A;

        // Playbook
        List<Action> playbook = buildPlaybook(nodes, eConflicts, coalitions,
                grade, frustration, ec);

        // Insights
        List<String> insights = buildInsights(nodes, coalitions, frustration, ec,
                weak, portMean);

        String headline = String.format(Locale.ROOT,
                "VERDICT: grade=%s polarization=%.1f agitators=%d frustration=%d/%d",
                grade.name(), portMean, agitators, frustration, ec);

        return new Plan(headline, grade, appetite, now, vc, ec,
                posEdges, negEdges, frustration, strong, weak,
                tri.total(), tri.ppp, tri.ppn, tri.pnn, tri.nnn,
                portMean, nodes, eConflicts, coalitions, playbook, insights);
    }

    // -- Playbook ----------------------------------------------------------

    private List<Action> buildPlaybook(List<NodeConflict> nodes,
                                       List<EdgeConflict> eConflicts,
                                       List<Set<String>> coalitions,
                                       Grade grade, int frustration, int edgeCount) {
        LinkedHashMap<String, Action> by = new LinkedHashMap<>();
        List<Object> agitators = nodes.stream()
                .filter(n -> n.verdict == Verdict.AGITATOR)
                .map(n -> (Object) n.node).toList();
        List<Object> hostiles = nodes.stream()
                .filter(n -> n.verdict == Verdict.ISOLATED_HOSTILE)
                .map(n -> (Object) n.node).toList();
        List<Object> brokers = nodes.stream()
                .filter(n -> n.verdict == Verdict.BROKER)
                .map(n -> (Object) n.node).toList();
        List<Object> polarized = nodes.stream()
                .filter(n -> n.verdict == Verdict.POLARIZED)
                .map(n -> (Object) n.node).toList();
        List<Object> peacemakers = nodes.stream()
                .filter(n -> n.verdict == Verdict.PEACEMAKER)
                .map(n -> (Object) n.node).toList();

        // P0
        if (!agitators.isEmpty()) {
            by.put("ISOLATE_AGITATORS", new Action("ISOLATE_AGITATORS",
                    Priority.P0, "Isolate and mediate agitators",
                    "mediation", 4, "low",
                    agitators.size() + " agitator(s) detected.",
                    agitators));
        }
        double weakAprox = trianglesTotalOrZero(); // not used; kept signature small
        if (agitators.size() >= 2) {
            by.put("EMERGENCY_DE_ESCALATION", new Action("EMERGENCY_DE_ESCALATION",
                    Priority.P0, "Emergency de-escalation",
                    "ops", 5, "low",
                    "Multiple agitators with low weak-balance.",
                    agitators));
        }
        // P1
        if (hostiles.size() >= 2) {
            by.put("SPLIT_HOSTILE_CLIQUE", new Action("SPLIT_HOSTILE_CLIQUE",
                    Priority.P1, "Split hostile clique",
                    "governance", 3, "medium",
                    hostiles.size() + " isolated-hostile member(s).",
                    hostiles));
        }
        List<Object> flipTargets = new ArrayList<>();
        for (EdgeConflict e : eConflicts) {
            if (e.verdict == EdgeVerdict.FLIP_CANDIDATE) {
                flipTargets.add(e.u + "--" + e.v);
            }
        }
        if (!flipTargets.isEmpty()) {
            by.put("FLIP_EDGE_TO_POSITIVE", new Action("FLIP_EDGE_TO_POSITIVE",
                    Priority.P1, "Flip frustrated edges to positive",
                    "mediation", 2, "medium",
                    flipTargets.size() + " flip-candidate edge(s).",
                    flipTargets));
        }
        if (brokers.size() >= 2) {
            by.put("ELEVATE_BROKERS", new Action("ELEVATE_BROKERS",
                    Priority.P1, "Elevate brokers",
                    "community", 2, "high",
                    brokers.size() + " broker(s) spanning coalitions.",
                    brokers));
        }
        if (polarized.size() >= 2 && peacemakers.size() >= 1) {
            List<Object> tgt = new ArrayList<>();
            tgt.addAll(polarized);
            tgt.addAll(peacemakers);
            by.put("PAIR_PEACEMAKERS_WITH_POLARIZED",
                    new Action("PAIR_PEACEMAKERS_WITH_POLARIZED",
                            Priority.P1, "Pair peacemakers with polarized members",
                            "community", 2, "high",
                            "Mediation pairing opportunity.", tgt));
        }
        // P2
        if (coalitions.size() >= 2) {
            List<Object> tags = new ArrayList<>();
            for (int i = 0; i < coalitions.size(); i++) tags.add("coalition_" + i);
            by.put("MONITOR_COALITION_DRIFT", new Action("MONITOR_COALITION_DRIFT",
                    Priority.P2, "Monitor coalition drift",
                    "research", 1, "high",
                    coalitions.size() + " coalitions detected.", tags));
        }
        if (appetite == RiskAppetite.CAUTIOUS
                && (grade == Grade.C || grade == Grade.D || grade == Grade.F)) {
            by.put("SCHEDULE_DIALOGUE_SESSION", new Action("SCHEDULE_DIALOGUE_SESSION",
                    Priority.P2, "Schedule dialogue session",
                    "community", 1, "high",
                    "Cautious appetite + grade " + grade + ".",
                    Collections.emptyList()));
        }
        // P3 fallback
        if (by.isEmpty()) {
            by.put("HEALTHY_NETWORK", new Action("HEALTHY_NETWORK",
                    Priority.P3, "Healthy network",
                    "researcher", 1, "high",
                    "No high-priority conflict signals.",
                    Collections.emptyList()));
        }
        // AGGRESSIVE trims P3 fallback and lone P2 when P0/P1 present
        if (appetite == RiskAppetite.AGGRESSIVE) {
            boolean hasHigh = by.values().stream().anyMatch(a ->
                    a.priority == Priority.P0 || a.priority == Priority.P1);
            if (hasHigh) {
                by.values().removeIf(a -> a.priority == Priority.P3);
                long p2 = by.values().stream().filter(a -> a.priority == Priority.P2).count();
                if (p2 == 1) by.values().removeIf(a -> a.priority == Priority.P2);
            }
        }
        List<Action> ordered = new ArrayList<>(by.values());
        ordered.sort((a, b) -> {
            int c = Integer.compare(a.priority.ordinal(), b.priority.ordinal());
            if (c != 0) return c;
            return a.id.compareTo(b.id);
        });
        return ordered;
    }

    // unused but kept to allow future enrichment cleanly
    private double trianglesTotalOrZero() { return 0.0; }

    // -- Insights ----------------------------------------------------------

    private List<String> buildInsights(List<NodeConflict> nodes,
                                       List<Set<String>> coalitions,
                                       int frustration, int edgeCount,
                                       double weak, double polarizationScore) {
        List<String> out = new ArrayList<>();
        if (graph.getVertexCount() <= 1) {
            out.add("EMPTY_OR_TRIVIAL");
            return out;
        }
        long agitators = nodes.stream().filter(n -> n.verdict == Verdict.AGITATOR).count();
        long brokers = nodes.stream().filter(n -> n.verdict == Verdict.BROKER).count();
        long hostiles = nodes.stream().filter(n -> n.verdict == Verdict.ISOLATED_HOSTILE).count();
        double frShare = edgeCount == 0 ? 0.0 : (double) frustration / edgeCount;

        if (frShare >= 0.25) out.add("HIGH_FRUSTRATION");
        if (polarizationScore >= 60.0) out.add("POLARIZED_NETWORK");
        if (coalitions.size() >= 3) out.add("COALITION_FRACTURE");
        if (weak >= 0.90 && frShare < 0.10) out.add("BALANCED_NETWORK");
        if (agitators >= 1) out.add("AGITATOR_PRESENT");
        if (brokers >= 2) out.add("BROKERS_AVAILABLE");
        if (hostiles >= 2) out.add("ISOLATED_HOSTILE_CLUSTER");
        if (out.isEmpty()) out.add("BASELINE_NETWORK");
        return out;
    }

    // -- Helpers -----------------------------------------------------------

    private static String ord(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }
    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // -- Renderers ---------------------------------------------------------

    public String render(Plan plan) { return toText(plan); }

    public String toText(Plan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append(plan.headline).append('\n');
        sb.append("Generated at: ").append(plan.generatedAt).append('\n');
        sb.append("Risk appetite: ").append(plan.riskAppetite).append('\n');
        sb.append("V=").append(plan.vertexCount)
                .append(" E=").append(plan.edgeCount)
                .append(" pos=").append(plan.positiveEdges)
                .append(" neg=").append(plan.negativeEdges)
                .append(" frustration=").append(plan.frustrationIndex)
                .append(" weakBalance=").append(String.format(Locale.ROOT, "%.2f", plan.weakBalanceDegree))
                .append('\n');
        sb.append("\n== Top nodes ==\n");
        for (NodeConflict n : plan.nodes) {
            sb.append(String.format(Locale.ROOT,
                    "  [%s] %-15s score=%5.1f verdict=%-16s deg=%d neg=%d frust=%d pol=%.2f reasons=%s%n",
                    n.priority, n.node, n.conflictScore, n.verdict,
                    n.degree, n.negativeIncident, n.frustratedIncident, n.polarization,
                    n.reasons));
        }
        sb.append("\n== Frustrated edges ==\n");
        for (EdgeConflict e : plan.edges) {
            sb.append(String.format(Locale.ROOT,
                    "  [%s] %s--%s %s (%s)%n",
                    e.priority, e.u, e.v, e.verdict, e.reason));
        }
        sb.append("\n== Coalitions ==\n");
        for (int i = 0; i < plan.coalitions.size(); i++) {
            sb.append("  ").append(i).append(": ").append(plan.coalitions.get(i)).append('\n');
        }
        sb.append("\n== Playbook ==\n");
        for (Action a : plan.playbook) {
            sb.append(String.format(Locale.ROOT,
                    "  [%s] %s (owner=%s blast=%d rev=%s) - %s%n",
                    a.priority, a.label, a.owner, a.blastRadius, a.reversibility, a.reason));
        }
        sb.append("\n== Insights ==\n");
        for (String i : plan.insights) sb.append("  - ").append(i).append('\n');
        return sb.toString();
    }

    public String toMarkdown(Plan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Graph Signed Conflict\n\n");
        sb.append("**").append(plan.headline).append("**\n\n");
        sb.append("## Summary\n\n");
        sb.append("| Metric | Value |\n|---|---|\n");
        sb.append("| Grade | ").append(plan.grade).append(" |\n");
        sb.append("| Risk appetite | ").append(plan.riskAppetite).append(" |\n");
        sb.append("| Vertices | ").append(plan.vertexCount).append(" |\n");
        sb.append("| Edges | ").append(plan.edgeCount).append(" |\n");
        sb.append("| Positive edges | ").append(plan.positiveEdges).append(" |\n");
        sb.append("| Negative edges | ").append(plan.negativeEdges).append(" |\n");
        sb.append("| Frustration index | ").append(plan.frustrationIndex).append(" |\n");
        sb.append("| Strong balance | ").append(String.format(Locale.ROOT, "%.2f", plan.strongBalanceDegree)).append(" |\n");
        sb.append("| Weak balance | ").append(String.format(Locale.ROOT, "%.2f", plan.weakBalanceDegree)).append(" |\n");
        sb.append("| Polarization score | ").append(String.format(Locale.ROOT, "%.1f", plan.polarizationScore)).append(" |\n");
        sb.append("| Generated at | ").append(plan.generatedAt).append(" |\n\n");

        sb.append("## Per-vertex\n\n");
        sb.append("| Priority | Node | Score | Verdict | Deg | Neg | Frust | Polarization |\n");
        sb.append("|---|---|---:|---|---:|---:|---:|---:|\n");
        for (NodeConflict n : plan.nodes) {
            sb.append("| ").append(n.priority)
                    .append(" | ").append(n.node)
                    .append(" | ").append(String.format(Locale.ROOT, "%.1f", n.conflictScore))
                    .append(" | ").append(n.verdict)
                    .append(" | ").append(n.degree)
                    .append(" | ").append(n.negativeIncident)
                    .append(" | ").append(n.frustratedIncident)
                    .append(" | ").append(String.format(Locale.ROOT, "%.2f", n.polarization))
                    .append(" |\n");
        }

        sb.append("\n## Frustrated edges\n\n");
        sb.append("| Priority | Edge | Verdict | Reason |\n|---|---|---|---|\n");
        for (EdgeConflict e : plan.edges) {
            sb.append("| ").append(e.priority)
                    .append(" | ").append(e.u).append("--").append(e.v)
                    .append(" | ").append(e.verdict)
                    .append(" | ").append(e.reason).append(" |\n");
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
                    .append(" | ").append(a.reason).append(" |\n");
        }

        sb.append("\n## Insights\n\n");
        for (String i : plan.insights) sb.append("- ").append(i).append('\n');
        return sb.toString();
    }

    /** Deterministic JSON (sorted keys, byte-stable). */
    public String toJson(Plan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"coalitions\": [");
        boolean first = true;
        for (Set<String> c : plan.coalitions) {
            if (!first) sb.append(','); first = false;
            sb.append("\n    [");
            boolean f2 = true;
            List<String> sorted = new ArrayList<>(c);
            Collections.sort(sorted);
            for (String s : sorted) {
                if (!f2) sb.append(", "); f2 = false;
                sb.append(jstr(s));
            }
            sb.append("]");
        }
        sb.append("\n  ],\n");

        sb.append("  \"edges\": [");
        first = true;
        for (EdgeConflict e : plan.edges) {
            if (!first) sb.append(','); first = false;
            sb.append("\n    {");
            sb.append("\"priority\": ").append(jstr(e.priority.name()));
            sb.append(", \"reason\": ").append(jstr(e.reason));
            sb.append(", \"u\": ").append(jstr(e.u));
            sb.append(", \"v\": ").append(jstr(e.v));
            sb.append(", \"verdict\": ").append(jstr(e.verdict.name()));
            sb.append("}");
        }
        sb.append("\n  ],\n");

        sb.append("  \"generated_at\": ").append(jstr(Objects.toString(plan.generatedAt))).append(",\n");
        sb.append("  \"grade\": ").append(jstr(plan.grade.name())).append(",\n");
        sb.append("  \"insights\": ").append(jstrArr(plan.insights)).append(",\n");

        sb.append("  \"nodes\": [");
        first = true;
        for (NodeConflict n : plan.nodes) {
            if (!first) sb.append(','); first = false;
            sb.append("\n    {");
            sb.append("\"conflict_score\": ").append(n.conflictScore);
            sb.append(", \"degree\": ").append(n.degree);
            sb.append(", \"frustrated_incident\": ").append(n.frustratedIncident);
            sb.append(", \"negative_incident\": ").append(n.negativeIncident);
            sb.append(", \"node\": ").append(jstr(n.node));
            sb.append(", \"polarization\": ").append(n.polarization);
            sb.append(", \"positive_incident\": ").append(n.positiveIncident);
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
            sb.append(", \"targets\": [");
            boolean f3 = true;
            for (Object t : a.targets) {
                if (!f3) sb.append(", "); f3 = false;
                sb.append(jstr(Objects.toString(t)));
            }
            sb.append("]");
            sb.append("}");
        }
        sb.append("\n  ],\n");

        sb.append("  \"summary\": {\n");
        sb.append("    \"edge_count\": ").append(plan.edgeCount).append(",\n");
        sb.append("    \"frustration_index\": ").append(plan.frustrationIndex).append(",\n");
        sb.append("    \"negative_edges\": ").append(plan.negativeEdges).append(",\n");
        sb.append("    \"polarization_score\": ").append(plan.polarizationScore).append(",\n");
        sb.append("    \"positive_edges\": ").append(plan.positiveEdges).append(",\n");
        sb.append("    \"risk_appetite\": ").append(jstr(plan.riskAppetite.name())).append(",\n");
        sb.append("    \"strong_balance_degree\": ").append(String.format(Locale.ROOT, "%.4f", plan.strongBalanceDegree)).append(",\n");
        sb.append("    \"triangles_nnn\": ").append(plan.trianglesNnn).append(",\n");
        sb.append("    \"triangles_ppn\": ").append(plan.trianglesPpn).append(",\n");
        sb.append("    \"triangles_pnn\": ").append(plan.trianglesPnn).append(",\n");
        sb.append("    \"triangles_ppp\": ").append(plan.trianglesPpp).append(",\n");
        sb.append("    \"triangles_total\": ").append(plan.trianglesTotal).append(",\n");
        sb.append("    \"vertex_count\": ").append(plan.vertexCount).append(",\n");
        sb.append("    \"weak_balance_degree\": ").append(String.format(Locale.ROOT, "%.4f", plan.weakBalanceDegree)).append("\n");
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String jstr(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder();
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

    private static String jstrArr(List<String> xs) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (String x : xs) {
            if (!first) sb.append(", "); first = false;
            sb.append(jstr(x));
        }
        sb.append(']');
        return sb.toString();
    }
}
