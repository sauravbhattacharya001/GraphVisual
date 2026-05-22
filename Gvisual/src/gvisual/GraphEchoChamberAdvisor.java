package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

/**
 * GraphEchoChamberAdvisor &mdash; per-vertex echo-chamber / homophily advisor
 * over a <em>single</em> graph snapshot plus a {@code Map<V,String>} of
 * per-vertex labels (e.g. political stance, news source, group affiliation,
 * opinion bucket). Sibling to {@link GraphFriendshipTriadAdvisor},
 * {@link GraphMentorMatchAdvisor}, {@link GraphSignedConflictAdvisor},
 * {@link GraphCommunityChurnAdvisor}, {@link GraphCascadingFailureAdvisor},
 * {@link GraphInfluenceSeedAdvisor}, {@link GraphPrivacyExposureAuditor}, and
 * {@link GraphAdversaryForecaster}.
 *
 * <p>For each labeled vertex with degree &ge; 1 the advisor measures local
 * <em>homophily</em> (fraction of neighbours sharing the vertex's label) and
 * neighbourhood <em>diversity</em> (normalized Shannon entropy of neighbour
 * labels) and assigns one of eight verdicts:</p>
 *
 * <ul>
 *   <li>{@code EXTREME_ECHO} &mdash; homophily &ge; 0.95 &amp; degree &ge; 6
 *       (P0 in cautious / P1 otherwise).</li>
 *   <li>{@code ECHO_CHAMBER} &mdash; homophily &gt; 0.85 &amp; degree &ge; 4
 *       (P1).</li>
 *   <li>{@code BRIDGE_NODE} &mdash; homophily &le; 0.40 &amp; degree &ge; 4
 *       (P1: cross-label hub worth empowering).</li>
 *   <li>{@code LEANING} &mdash; modest homophily &gt; 0.70 (P2 watch).</li>
 *   <li>{@code MIXED} &mdash; diversity in [0.40, 0.70) (P3).</li>
 *   <li>{@code OPEN_MIND} &mdash; diversity &ge; 0.70 (P3 healthy).</li>
 *   <li>{@code UNLABELED} &mdash; no label provided (P3).</li>
 *   <li>{@code ISOLATED} &mdash; degree 0 (P3).</li>
 * </ul>
 *
 * <p>Pure JDK + JUNG. Single file. Never mutates the input graph or labels
 * map. Deterministic given a fixed {@link Clock}. JSON output has stable
 * lexicographic key order on each object.</p>
 *
 * @author sauravbhattacharya001
 */
public final class GraphEchoChamberAdvisor<V, E> {

    // -- Public types ------------------------------------------------------

    public enum Verdict {
        OPEN_MIND, MIXED, LEANING, ECHO_CHAMBER, EXTREME_ECHO,
        BRIDGE_NODE, UNLABELED, ISOLATED
    }
    public enum Priority { P0, P1, P2, P3 }
    public enum RiskAppetite { CAUTIOUS, BALANCED, AGGRESSIVE }
    public enum Grade { A, B, C, D, F }

    public static final class NodeEcho<V> {
        public final V node;
        public final String label;            // may be null
        public final Verdict verdict;
        public final Priority priority;
        public final int degree;
        public final double homophily;        // 0..1, NaN if unlabeled or deg=0
        public final double diversity;        // 0..1
        public final double echoScore;        // 0..100
        public final double bridgeScore;      // 0..100
        public final List<String> reasons;
        public NodeEcho(V node, String label, Verdict verdict, Priority priority,
                        int degree, double homophily, double diversity,
                        double echoScore, double bridgeScore, List<String> reasons) {
            this.node = node;
            this.label = label;
            this.verdict = verdict;
            this.priority = priority;
            this.degree = degree;
            this.homophily = homophily;
            this.diversity = diversity;
            this.echoScore = echoScore;
            this.bridgeScore = bridgeScore;
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
        public final double globalHomophily;
        public final double echoChamberFraction;
        public final double bridgeFraction;
        public final double openMindFraction;
        public final int labelCount;
        public final String dominantLabel;       // may be null when no labels
        public final double dominantLabelShare;  // 0..1
        public final List<NodeEcho<?>> nodes;
        public final List<Action> playbook;
        public final List<String> insights;
        public Plan(Instant generatedAt, RiskAppetite riskAppetite, Grade grade,
                    String headline, double globalHomophily,
                    double echoChamberFraction, double bridgeFraction,
                    double openMindFraction, int labelCount,
                    String dominantLabel, double dominantLabelShare,
                    List<? extends NodeEcho<?>> nodes, List<Action> playbook,
                    List<String> insights) {
            this.generatedAt = generatedAt;
            this.riskAppetite = riskAppetite;
            this.grade = grade;
            this.headline = headline;
            this.globalHomophily = globalHomophily;
            this.echoChamberFraction = echoChamberFraction;
            this.bridgeFraction = bridgeFraction;
            this.openMindFraction = openMindFraction;
            this.labelCount = labelCount;
            this.dominantLabel = dominantLabel;
            this.dominantLabelShare = dominantLabelShare;
            this.nodes = Collections.unmodifiableList(new ArrayList<NodeEcho<?>>(nodes));
            this.playbook = Collections.unmodifiableList(new ArrayList<>(playbook));
            this.insights = Collections.unmodifiableList(new ArrayList<>(insights));
        }
    }

    // -- Inputs ------------------------------------------------------------

    private final Graph<V, E> graph;
    private Map<V, String> labels = Collections.emptyMap();
    private RiskAppetite appetite = RiskAppetite.BALANCED;
    private Clock clock = Clock.systemUTC();

    public GraphEchoChamberAdvisor(Graph<V, E> graph) {
        if (graph == null) throw new IllegalArgumentException("graph must be non-null");
        this.graph = graph;
    }

    public GraphEchoChamberAdvisor<V, E> withLabels(Map<V, String> labels) {
        if (labels == null) this.labels = Collections.emptyMap();
        else this.labels = new LinkedHashMap<>(labels);   // defensive copy
        return this;
    }
    public GraphEchoChamberAdvisor<V, E> withRiskAppetite(RiskAppetite a) {
        if (a != null) this.appetite = a; return this;
    }
    public GraphEchoChamberAdvisor<V, E> withFixedClock(Clock c) {
        if (c != null) this.clock = c; return this;
    }

    // -- Core analysis -----------------------------------------------------

    public Plan analyze() {
        // Build undirected neighbour map (de-duped, no self-loops).
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

        // Per-label total counts (for dominantLabel/dominantLabelShare).
        Map<String, Integer> labelTotals = new TreeMap<>();
        int labeledCount = 0;
        for (V v : nbrs.keySet()) {
            String lab = labels.get(v);
            if (lab != null) {
                labelTotals.merge(lab, 1, Integer::sum);
                labeledCount++;
            }
        }
        int distinctLabels = labelTotals.size();
        String dominantLabel = null;
        int dominantCount = 0;
        for (Map.Entry<String, Integer> en : labelTotals.entrySet()) {
            if (en.getValue() > dominantCount
                    || (en.getValue() == dominantCount
                        && (dominantLabel == null || en.getKey().compareTo(dominantLabel) < 0))) {
                dominantCount = en.getValue();
                dominantLabel = en.getKey();
            }
        }
        double dominantShare = (labeledCount == 0) ? 0.0
                : (double) dominantCount / (double) labeledCount;

        List<NodeEcho<?>> classified = new ArrayList<>();
        int echo = 0, bridge = 0, open = 0, unlabeled = 0, isolated = 0;

        for (V v : nbrs.keySet()) {
            Set<V> n = nbrs.get(v);
            int deg = n.size();
            String selfLabel = labels.get(v);

            // Verdict precedence: ISOLATED > UNLABELED > others.
            if (deg == 0) {
                classified.add(new NodeEcho<V>(v, selfLabel, Verdict.ISOLATED,
                        Priority.P3, 0, Double.NaN, 0.0, 0.0, 0.0,
                        Collections.singletonList("NO_NEIGHBOURS")));
                isolated++;
                continue;
            }
            if (selfLabel == null) {
                classified.add(new NodeEcho<V>(v, null, Verdict.UNLABELED,
                        Priority.P3, deg, Double.NaN, 0.0, 0.0, 0.0,
                        Collections.singletonList("NO_LABEL")));
                unlabeled++;
                continue;
            }

            int sameLabel = 0;
            Map<String, Integer> nbrLabelCounts = new LinkedHashMap<>();
            int labeledNbrs = 0;
            for (V u : n) {
                String ul = labels.get(u);
                if (ul == null) continue;
                labeledNbrs++;
                if (ul.equals(selfLabel)) sameLabel++;
                nbrLabelCounts.merge(ul, 1, Integer::sum);
            }
            double homophily = (deg == 0) ? 0.0 : (double) sameLabel / (double) deg;
            double diversity = shannonNormalized(nbrLabelCounts);

            double echoScore   = clamp(100.0 * homophily * appetiteMult, 0.0, 100.0);
            double bridgeScore = clamp(100.0 * (1.0 - homophily) * appetiteMult, 0.0, 100.0);

            List<String> reasons = new ArrayList<>();
            Verdict verdict;
            Priority priority;

            if (homophily >= 0.95 && deg >= 6) {
                verdict = Verdict.EXTREME_ECHO;
                priority = (appetite == RiskAppetite.CAUTIOUS) ? Priority.P0 : Priority.P1;
                reasons.add("EXTREME_HOMOPHILY");
                if (deg >= 10) reasons.add("LARGE_CLUSTER");
            } else if (homophily > 0.85 && deg >= 4) {
                verdict = Verdict.ECHO_CHAMBER;
                priority = Priority.P1;
                reasons.add("HIGH_HOMOPHILY");
                reasons.add("LARGE_HOMOGENEOUS_NEIGHBOURHOOD");
            } else if (homophily <= 0.40 && deg >= 4) {
                verdict = Verdict.BRIDGE_NODE;
                priority = Priority.P1;
                reasons.add("CROSS_LABEL_HUB");
            } else if (homophily > 0.70 || (diversity >= 0.20 && diversity < 0.40)) {
                verdict = Verdict.LEANING;
                priority = Priority.P2;
                if (homophily > 0.70) reasons.add("MODERATE_HOMOPHILY");
                if (diversity < 0.40) reasons.add("LOW_DIVERSITY");
            } else if (diversity >= 0.70) {
                verdict = Verdict.OPEN_MIND;
                priority = Priority.P3;
                reasons.add("DIVERSE_NEIGHBOURHOOD");
            } else {
                verdict = Verdict.MIXED;
                priority = Priority.P3;
                reasons.add("MIXED_NEIGHBOURHOOD");
            }

            switch (verdict) {
                case ECHO_CHAMBER:
                case EXTREME_ECHO:  echo++; break;
                case BRIDGE_NODE:   bridge++; break;
                case OPEN_MIND:     open++; break;
                default: break;
            }

            classified.add(new NodeEcho<V>(v, selfLabel, verdict, priority,
                    deg, round3(homophily), round3(diversity),
                    round1(echoScore), round1(bridgeScore), reasons));
        }

        // Sort by priority, then echoScore+bridgeScore desc, then node string.
        classified.sort((a, b) -> {
            int pa = a.priority.ordinal();
            int pb = b.priority.ordinal();
            if (pa != pb) return Integer.compare(pa, pb);
            double sa = a.echoScore + a.bridgeScore;
            double sb = b.echoScore + b.bridgeScore;
            if (Double.compare(sb, sa) != 0) return Double.compare(sb, sa);
            return Objects.toString(a.node).compareTo(Objects.toString(b.node));
        });

        int total = Math.max(1, classified.size());
        double echoFrac   = echo / (double) total;
        double bridgeFrac = bridge / (double) total;
        double openFrac   = open / (double) total;
        double unlabeledFrac = unlabeled / (double) total;

        double globalHomophily = computeGlobalHomophily(nbrs, labels);

        Grade grade = grade(classified.size(), echoFrac, globalHomophily,
                            countVerdict(classified, Verdict.EXTREME_ECHO));
        List<Action> playbook = buildPlaybook(classified, echoFrac, bridgeFrac,
                unlabeledFrac, dominantLabel, dominantShare, grade);
        List<String> insights = buildInsights(classified, echoFrac, openFrac,
                unlabeledFrac, bridgeFrac, globalHomophily,
                dominantLabel, dominantShare, distinctLabels, labeledCount);

        String headline = headline(grade, echoFrac, bridgeFrac, globalHomophily, distinctLabels);

        return new Plan(clock.instant().atZone(ZoneOffset.UTC).toInstant(), appetite,
                grade, headline, round3(globalHomophily), round3(echoFrac),
                round3(bridgeFrac), round3(openFrac), distinctLabels,
                dominantLabel, round3(dominantShare), classified, playbook, insights);
    }

    private static <V> int countVerdict(List<NodeEcho<?>> nodes, Verdict v) {
        int c = 0;
        for (NodeEcho<?> n : nodes) if (n.verdict == v) c++;
        return c;
    }

    private static double shannonNormalized(Map<String, Integer> counts) {
        int total = 0;
        for (int c : counts.values()) total += c;
        if (total == 0) return 0.0;
        int k = counts.size();
        if (k <= 1) return 0.0;
        double h = 0.0;
        for (int c : counts.values()) {
            if (c == 0) continue;
            double p = (double) c / (double) total;
            h -= p * Math.log(p);
        }
        // Normalize by log(K) so range is [0,1].
        double norm = h / Math.log(k);
        return clamp(norm, 0.0, 1.0);
    }

    private static <V> double computeGlobalHomophily(Map<V, Set<V>> nbrs,
                                                     Map<V, String> labels) {
        long same = 0;
        long total = 0;
        for (Map.Entry<V, Set<V>> en : nbrs.entrySet()) {
            V a = en.getKey();
            String la = labels.get(a);
            if (la == null) continue;
            for (V b : en.getValue()) {
                // Count each undirected edge once: only when a < b lexicographically.
                if (Objects.toString(a).compareTo(Objects.toString(b)) >= 0) continue;
                String lb = labels.get(b);
                if (lb == null) continue;
                total++;
                if (la.equals(lb)) same++;
            }
        }
        return (total == 0) ? 0.0 : (double) same / (double) total;
    }

    // -- Playbook ----------------------------------------------------------

    private List<Action> buildPlaybook(List<NodeEcho<?>> nodes, double echoFrac,
                                       double bridgeFrac, double unlabeledFrac,
                                       String dominantLabel, double dominantShare,
                                       Grade grade) {
        List<Action> out = new ArrayList<>();
        if (nodes.isEmpty()) {
            out.add(new Action("EMPTY_GRAPH", Priority.P3, "Provide a non-empty graph",
                    "No vertices found",
                    "analyst", 1, "high", Collections.<Object>emptyList()));
            return out;
        }

        List<Object> extremeTargets = new ArrayList<>();
        List<Object> echoTargets = new ArrayList<>();
        List<Object> bridgeTargets = new ArrayList<>();
        List<Object> leaningTargets = new ArrayList<>();
        List<Object> unlabeledTargets = new ArrayList<>();
        for (NodeEcho<?> n : nodes) {
            switch (n.verdict) {
                case EXTREME_ECHO: extremeTargets.add(n.node); break;
                case ECHO_CHAMBER: echoTargets.add(n.node); break;
                case BRIDGE_NODE:  bridgeTargets.add(n.node); break;
                case LEANING:      leaningTargets.add(n.node); break;
                case UNLABELED:    unlabeledTargets.add(n.node); break;
                default: break;
            }
        }

        if (!extremeTargets.isEmpty()) {
            out.add(new Action("BREAK_EXTREME_ECHO_CHAMBERS", Priority.P0,
                    "Break up extreme echo chambers",
                    "Vertices with near-total label homogeneity in their neighbourhood",
                    "community_manager", 4, "low",
                    capTargets(extremeTargets, 10)));
        }
        if (echoFrac >= 0.50) {
            out.add(new Action("CONVENE_CROSS_GROUP_COUNCIL", Priority.P0,
                    "Convene a cross-group council to mediate echo-chamber dominance",
                    "Echo-chamber fraction is " + pct(echoFrac) + " of the population",
                    "program_lead", 5, "low",
                    capTargets(echoTargets, 12)));
        }
        if (!echoTargets.isEmpty()) {
            out.add(new Action("INTRODUCE_BRIDGE_CONTENT_TO_CHAMBERS", Priority.P1,
                    "Inject diverse-label content into echo-chamber neighbourhoods",
                    "Reduce within-group reinforcement by surfacing cross-label material",
                    "editorial", 3, "medium",
                    capTargets(echoTargets, 10)));
        }
        if (!bridgeTargets.isEmpty()) {
            out.add(new Action("EMPOWER_BRIDGE_NODES", Priority.P1,
                    "Empower bridge nodes as cross-label liaisons",
                    "These nodes connect across many label classes; protect their reach",
                    "community_manager", 2, "high",
                    capTargets(bridgeTargets, 8)));
        }
        if (!leaningTargets.isEmpty()) {
            out.add(new Action("MONITOR_LEANING_NODES", Priority.P2,
                    "Track leaning nodes before they tip into an echo chamber",
                    leaningTargets.size() + " nodes are moderately homophilous",
                    "analyst", 1, "high",
                    capTargets(leaningTargets, 10)));
        }
        if (dominantShare >= 0.60 && dominantLabel != null) {
            out.add(new Action("SEED_DIVERSITY_INTO_DOMINANT_LABEL", Priority.P2,
                    "Seed diversity into the dominant label community",
                    "Dominant label '" + dominantLabel + "' holds " + pct(dominantShare)
                            + " of labeled nodes",
                    "editorial", 3, "medium",
                    Collections.<Object>emptyList()));
        }
        if (unlabeledFrac >= 0.30) {
            out.add(new Action("LABEL_UNLABELED_NODES", Priority.P2,
                    "Resolve labels for unlabeled nodes to improve coverage",
                    pct(unlabeledFrac) + " of nodes have no label assigned",
                    "data_steward", 1, "high",
                    capTargets(unlabeledTargets, 12)));
        }

        // Aggressive trims P3 fallback when any P0/P1 present.
        boolean hasUrgent = false;
        for (Action a : out) {
            if (a.priority == Priority.P0 || a.priority == Priority.P1) {
                hasUrgent = true; break;
            }
        }

        // Cautious tail: schedule audit when grade is mid-tier or worse.
        if (appetite == RiskAppetite.CAUTIOUS
                && (grade == Grade.C || grade == Grade.D || grade == Grade.F)) {
            out.add(new Action("SCHEDULE_ECHO_AUDIT", Priority.P2,
                    "Schedule a follow-up echo-chamber audit",
                    "Cautious appetite + grade=" + grade
                            + " recommends a periodic re-check",
                    "analyst", 1, "high",
                    Collections.<Object>emptyList()));
        }

        // Healthy fallback (P3) if nothing else has fired.
        boolean any = false;
        for (Action a : out) {
            if (a.priority != Priority.P3) { any = true; break; }
        }
        if (!any) {
            out.add(new Action("MAINTAIN_DIVERSE_NETWORK", Priority.P3,
                    "Maintain current cross-label engagement",
                    "No notable echo-chamber concerns detected",
                    "community_manager", 1, "high",
                    Collections.<Object>emptyList()));
        }

        // AGGRESSIVE: trim MAINTAIN_DIVERSE_NETWORK if any P0/P1 present.
        if (appetite == RiskAppetite.AGGRESSIVE && hasUrgent) {
            List<Action> trimmed = new ArrayList<>();
            for (Action a : out) {
                if ("MAINTAIN_DIVERSE_NETWORK".equals(a.id)) continue;
                trimmed.add(a);
            }
            out = trimmed;
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
        return new ArrayList<>(in.subList(0, max));
    }

    // -- Insights ----------------------------------------------------------

    private List<String> buildInsights(List<NodeEcho<?>> nodes, double echoFrac,
                                       double openFrac, double unlabeledFrac,
                                       double bridgeFrac, double globalHomophily,
                                       String dominantLabel, double dominantShare,
                                       int distinctLabels, int labeledCount) {
        List<String> out = new ArrayList<>();
        if (nodes.isEmpty()) {
            out.add("EMPTY_SNAPSHOT");
            return out;
        }
        if (labeledCount == 0) {
            out.add("NO_LABELS_PROVIDED");
            return out;
        }
        int extreme = countVerdict(nodes, Verdict.EXTREME_ECHO);
        if (extreme >= 2) out.add("EXTREME_ECHO_CLUSTER");
        if (dominantShare >= 0.60 && dominantLabel != null) {
            out.add("DOMINANT_LABEL:" + dominantLabel);
        }
        if (bridgeFrac > 0) out.add("BRIDGE_NODES_PRESENT");
        if (globalHomophily >= 0.70) out.add("HIGH_GLOBAL_HOMOPHILY");
        if (unlabeledFrac >= 0.30) out.add("SPARSELY_LABELED");
        if (echoFrac == 0.0 && openFrac >= 0.30) out.add("HEALTHY_DIVERSITY");
        if (out.isEmpty()) out.add("NO_NOTABLE_SIGNALS");
        return out;
    }

    // -- Grade --------------------------------------------------------------

    private Grade grade(int total, double echoFrac, double globalHomophily,
                        int extremeCount) {
        if (total == 0) return Grade.A;
        if (echoFrac >= 0.50) return Grade.F;
        if (extremeCount >= 1 && echoFrac >= 0.30) return Grade.F;
        if (echoFrac >= 0.30) return Grade.D;
        if (globalHomophily >= 0.70 || echoFrac >= 0.15) return Grade.C;
        if (globalHomophily >= 0.55) return Grade.B;
        return Grade.A;
    }

    private String headline(Grade grade, double echoFrac, double bridgeFrac,
                            double globalHomophily, int labelCount) {
        return String.format(Locale.ROOT,
                "verdict=%s; echo=%d%% bridge=%d%% homophily=%.2f labels=%d",
                grade.name(), pctInt(echoFrac), pctInt(bridgeFrac),
                globalHomophily, labelCount);
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
    private static double round3(double v) {
        if (Double.isNaN(v)) return v;
        return Math.round(v * 1000.0) / 1000.0;
    }
    private static String fmt(double v) { return String.format(Locale.ROOT, "%.2f", v); }
    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.0f%%", v * 100.0);
    }
    private static int pctInt(double v) {
        return (int) Math.round(v * 100.0);
    }

    // -- Renderers ---------------------------------------------------------

    public String toText(Plan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append(plan.headline).append('\n');
        sb.append("Generated at: ").append(plan.generatedAt).append('\n');
        sb.append("Risk appetite: ").append(plan.riskAppetite).append('\n');
        sb.append(String.format(Locale.ROOT,
                "Global homophily=%.2f echo=%s bridge=%s open=%s labels=%d dominant=%s (%s)%n",
                plan.globalHomophily, pct(plan.echoChamberFraction),
                pct(plan.bridgeFraction), pct(plan.openMindFraction),
                plan.labelCount, String.valueOf(plan.dominantLabel),
                pct(plan.dominantLabelShare)));
        sb.append("\n== Nodes ==\n");
        int shown = 0;
        for (NodeEcho<?> n : plan.nodes) {
            if (shown++ >= 25) break;
            sb.append(String.format(Locale.ROOT,
                    "  [%s] %-20s label=%s deg=%d homo=%.2f div=%.2f echo=%.1f bridge=%.1f verdict=%s reasons=%s%n",
                    n.priority, Objects.toString(n.node),
                    String.valueOf(n.label), n.degree,
                    Double.isNaN(n.homophily) ? 0.0 : n.homophily,
                    n.diversity, n.echoScore, n.bridgeScore,
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
        sb.append("# Graph Echo Chamber\n\n");
        sb.append("**").append(plan.headline).append("**\n\n");

        sb.append("## Summary\n\n");
        sb.append("- Generated at: ").append(plan.generatedAt).append('\n');
        sb.append("- Risk appetite: ").append(plan.riskAppetite).append('\n');
        sb.append("- Grade: ").append(plan.grade).append('\n');
        sb.append("- Global homophily: ").append(fmt(plan.globalHomophily)).append('\n');
        sb.append("- Echo: ").append(pct(plan.echoChamberFraction))
          .append(" | Bridge: ").append(pct(plan.bridgeFraction))
          .append(" | Open: ").append(pct(plan.openMindFraction)).append('\n');
        sb.append("- Labels: ").append(plan.labelCount)
          .append(" | Dominant: ").append(String.valueOf(plan.dominantLabel))
          .append(" (").append(pct(plan.dominantLabelShare)).append(")\n\n");

        sb.append("## Nodes\n\n");
        sb.append("| Priority | Node | Label | Verdict | Degree | Homophily | Diversity | Echo | Bridge | Reasons |\n");
        sb.append("|---|---|---|---|---:|---:|---:|---:|---:|---|\n");
        int shown = 0;
        for (NodeEcho<?> n : plan.nodes) {
            if (shown++ >= 25) break;
            double h = Double.isNaN(n.homophily) ? 0.0 : n.homophily;
            sb.append("| ").append(n.priority)
              .append(" | ").append(Objects.toString(n.node))
              .append(" | ").append(String.valueOf(n.label))
              .append(" | ").append(n.verdict)
              .append(" | ").append(n.degree)
              .append(" | ").append(fmt(h))
              .append(" | ").append(fmt(n.diversity))
              .append(" | ").append(String.format(Locale.ROOT, "%.1f", n.echoScore))
              .append(" | ").append(String.format(Locale.ROOT, "%.1f", n.bridgeScore))
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
        sb.append("  \"bridge_fraction\": ").append(plan.bridgeFraction).append(",\n");
        sb.append("  \"dominant_label\": ").append(jstr(plan.dominantLabel)).append(",\n");
        sb.append("  \"dominant_label_share\": ").append(plan.dominantLabelShare).append(",\n");
        sb.append("  \"echo_chamber_fraction\": ").append(plan.echoChamberFraction).append(",\n");
        sb.append("  \"generated_at\": ").append(jstr(Objects.toString(plan.generatedAt))).append(",\n");
        sb.append("  \"global_homophily\": ").append(plan.globalHomophily).append(",\n");
        sb.append("  \"grade\": ").append(jstr(plan.grade.name())).append(",\n");
        sb.append("  \"headline\": ").append(jstr(plan.headline)).append(",\n");
        sb.append("  \"label_count\": ").append(plan.labelCount).append(",\n");
        sb.append("  \"open_mind_fraction\": ").append(plan.openMindFraction).append(",\n");
        sb.append("  \"risk_appetite\": ").append(jstr(plan.riskAppetite.name())).append(",\n");

        sb.append("  \"nodes\": [");
        boolean first = true;
        for (NodeEcho<?> n : plan.nodes) {
            if (!first) sb.append(','); first = false;
            sb.append("\n    {");
            sb.append("\"bridge_score\": ").append(n.bridgeScore);
            sb.append(", \"degree\": ").append(n.degree);
            sb.append(", \"diversity\": ").append(n.diversity);
            sb.append(", \"echo_score\": ").append(n.echoScore);
            sb.append(", \"homophily\": ").append(Double.isNaN(n.homophily) ? "null" : Double.toString(n.homophily));
            sb.append(", \"label\": ").append(jstr(n.label));
            sb.append(", \"node\": ").append(jstr(Objects.toString(n.node)));
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
