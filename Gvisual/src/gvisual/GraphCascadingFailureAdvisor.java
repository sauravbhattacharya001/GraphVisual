package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;

/**
 * GraphCascadingFailureAdvisor &mdash; agentic cascading-failure / resilience
 * advisor for undirected graphs under a load-redistribution threshold model.
 *
 * <p>Sibling to {@link GraphAdversaryForecaster},
 * {@link GraphPrivacyExposureAuditor} and
 * {@link GraphInfluenceSeedAdvisor}.</p>
 *
 * <p>Model: every node carries a load (default = degree, optionally weighted
 * by an edge-weight function) and has capacity = load * (1 + tolerance).
 * When a node fails its load is split equally over the surviving neighbours;
 * any neighbour whose new load exceeds its capacity also fails. Waves
 * continue until quiescence.</p>
 *
 * <p>The advisor evaluates an optional explicit {@code initialFailures}
 * scenario plus a single-node-failure stress test over the top-K
 * candidates (default {@code K=min(20,N)}, biggest degree first) and
 * combines the results into per-node {@link NodeRisk} reports, a P0-P3
 * hardening {@link Action} playbook, an A-F grade and headline insights.</p>
 *
 * <pre>
 *   GraphCascadingFailureAdvisor&lt;String, Edge&gt; a =
 *       new GraphCascadingFailureAdvisor&lt;&gt;(g)
 *           .withTolerance(0.20)
 *           .withRiskAppetite(GraphCascadingFailureAdvisor.RiskAppetite.BALANCED);
 *   GraphCascadingFailureAdvisor.Plan plan = a.analyze();
 *   System.out.println(a.toMarkdown(plan));
 * </pre>
 *
 * <p>Pure JDK + JUNG. Single file. Never mutates the input graph.
 * Deterministic given a fixed {@link Clock}.</p>
 *
 * @author sauravbhattacharya001
 */
public final class GraphCascadingFailureAdvisor<V, E> {

    // -- Public types ------------------------------------------------------

    public enum Verdict {
        RESILIENT, AT_RISK, BOTTLENECK, CRITICAL_HUB, OVERLOADED
    }
    public enum Priority { P0, P1, P2, P3 }
    public enum RiskAppetite { CAUTIOUS, BALANCED, AGGRESSIVE }

    public static final class NodeRisk<V> {
        public final V node;
        public final Verdict verdict;
        public final Priority priority;
        public final double riskScore;          // 0..100
        public final double baselineLoad;
        public final double capacity;
        public final double loadRatio;          // load / capacity
        public final boolean articulation;
        public final int worstCascadeSize;      // # extra failures caused
        public final int scenariosFailedIn;
        public final List<String> reasons;
        public NodeRisk(V node, Verdict verdict, Priority priority,
                        double riskScore, double baselineLoad, double capacity,
                        double loadRatio, boolean articulation,
                        int worstCascadeSize, int scenariosFailedIn,
                        List<String> reasons) {
            this.node = node;
            this.verdict = verdict;
            this.priority = priority;
            this.riskScore = riskScore;
            this.baselineLoad = baselineLoad;
            this.capacity = capacity;
            this.loadRatio = loadRatio;
            this.articulation = articulation;
            this.worstCascadeSize = worstCascadeSize;
            this.scenariosFailedIn = scenariosFailedIn;
            this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
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
        public Action(String id, Priority priority, String label, String owner,
                      int blastRadius, String reversibility, String reason) {
            this.id = id;
            this.priority = priority;
            this.label = label;
            this.owner = owner;
            this.blastRadius = blastRadius;
            this.reversibility = reversibility;
            this.reason = reason;
        }
    }

    public static final class Plan {
        public final int graphSize;
        public final Map<Object, NodeRisk<?>> perNode;   // insertion-ordered for stability
        public final List<Action> playbook;
        public final List<String> insights;
        public final String grade;
        public final double initialCascadeFraction;
        public final int initialWaves;
        public final boolean initialDisconnects;
        public final String summary;
        public final String generatedAt;
        public Plan(int graphSize, Map<Object, NodeRisk<?>> perNode,
                    List<Action> playbook, List<String> insights, String grade,
                    double initialCascadeFraction, int initialWaves,
                    boolean initialDisconnects, String summary, String generatedAt) {
            this.graphSize = graphSize;
            this.perNode = Collections.unmodifiableMap(new LinkedHashMap<>(perNode));
            this.playbook = Collections.unmodifiableList(new ArrayList<>(playbook));
            this.insights = Collections.unmodifiableList(new ArrayList<>(insights));
            this.grade = grade;
            this.initialCascadeFraction = initialCascadeFraction;
            this.initialWaves = initialWaves;
            this.initialDisconnects = initialDisconnects;
            this.summary = summary;
            this.generatedAt = generatedAt;
        }
    }

    // -- Configuration -----------------------------------------------------

    private final Graph<V, E> graph;
    private Set<V> initialFailures = new LinkedHashSet<>();
    private double tolerance = 0.20;
    private RiskAppetite risk = RiskAppetite.BALANCED;
    private Clock clock = Clock.systemUTC();
    private Function<E, Double> edgeWeight = null;
    private Integer topK = null;

    public GraphCascadingFailureAdvisor(Graph<V, E> graph) {
        if (graph == null) throw new IllegalArgumentException("graph is null");
        if (graph.getVertexCount() == 0) throw new IllegalArgumentException("graph is empty");
        this.graph = graph;
    }

    public GraphCascadingFailureAdvisor<V, E> withInitialFailures(Collection<V> failures) {
        this.initialFailures = (failures == null) ? new LinkedHashSet<V>() : new LinkedHashSet<>(failures);
        return this;
    }
    public GraphCascadingFailureAdvisor<V, E> withTolerance(double t) {
        if (t < 0) throw new IllegalArgumentException("tolerance must be >= 0");
        this.tolerance = t; return this;
    }
    public GraphCascadingFailureAdvisor<V, E> withRiskAppetite(RiskAppetite r) {
        if (r != null) this.risk = r; return this;
    }
    public GraphCascadingFailureAdvisor<V, E> withFixedClock(Clock c) {
        if (c != null) this.clock = c; return this;
    }
    public GraphCascadingFailureAdvisor<V, E> withEdgeWeight(Function<E, Double> f) {
        this.edgeWeight = f; return this;
    }
    public GraphCascadingFailureAdvisor<V, E> withTopK(int k) {
        if (k <= 0) throw new IllegalArgumentException("topK must be > 0");
        this.topK = k; return this;
    }

    // -- Analysis ----------------------------------------------------------

    public Plan analyze() {
        // 1. Stable vertex order.
        List<V> vertices = new ArrayList<>(graph.getVertices());
        Collections.sort(vertices, new Comparator<V>() {
            public int compare(V a, V b) { return String.valueOf(a).compareTo(String.valueOf(b)); }
        });
        int n = vertices.size();
        Map<V, Integer> idx = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) idx.put(vertices.get(i), i);

        // 2. Adjacency and per-edge weights (de-duped, undirected).
        double[] nodeLoad = new double[n];
        @SuppressWarnings("unchecked")
        List<int[]> adjN = new ArrayList<>(n);
        List<double[]> adjW = new ArrayList<>(n);
        for (int i = 0; i < n; i++) { adjN.add(new int[0]); adjW.add(new double[0]); }
        Map<Long, Double> edgeWMap = new LinkedHashMap<>();
        for (E e : graph.getEdges()) {
            Collection<V> ends = graph.getIncidentVertices(e);
            if (ends == null || ends.size() < 2) continue;
            Iterator<V> it = ends.iterator();
            V u = it.next(); V v = it.next();
            if (u.equals(v)) continue;
            int ui = idx.get(u); int vi = idx.get(v);
            int a = Math.min(ui, vi); int b = Math.max(ui, vi);
            long key = ((long) a) * 1_000_003L + (long) b;
            double w = 1.0;
            if (edgeWeight != null) {
                Double d = edgeWeight.apply(e);
                if (d != null && d > 0 && !Double.isInfinite(d) && !Double.isNaN(d)) w = d;
            }
            Double cur = edgeWMap.get(key);
            if (cur == null || w > cur) edgeWMap.put(key, w);
        }
        Map<Integer, List<int[]>> tmp = new HashMap<>();
        for (Map.Entry<Long, Double> entry : edgeWMap.entrySet()) {
            long key = entry.getKey();
            int b = (int) (key % 1_000_003L);
            int a = (int) (key / 1_000_003L);
            tmp.computeIfAbsent(a, new Function<Integer, List<int[]>>() {
                public List<int[]> apply(Integer k) { return new ArrayList<>(); }
            }).add(new int[]{b});
            tmp.computeIfAbsent(b, new Function<Integer, List<int[]>>() {
                public List<int[]> apply(Integer k) { return new ArrayList<>(); }
            }).add(new int[]{a});
        }
        for (int i = 0; i < n; i++) {
            List<int[]> lst = tmp.get(i);
            if (lst == null) continue;
            lst.sort(new Comparator<int[]>() {
                public int compare(int[] x, int[] y) { return Integer.compare(x[0], y[0]); }
            });
            int[] ns = new int[lst.size()];
            double[] ws = new double[lst.size()];
            for (int k = 0; k < lst.size(); k++) {
                ns[k] = lst.get(k)[0];
                int a = Math.min(i, ns[k]); int b = Math.max(i, ns[k]);
                long key = ((long) a) * 1_000_003L + (long) b;
                ws[k] = edgeWMap.get(key);
                nodeLoad[i] += ws[k];
            }
            adjN.set(i, ns);
            adjW.set(i, ws);
        }
        // Isolated node baseline-load = 0; ensure non-zero capacity to avoid div-by-zero.
        double[] capacity = new double[n];
        for (int i = 0; i < n; i++) {
            double base = nodeLoad[i] > 0 ? nodeLoad[i] : 1.0;
            capacity[i] = base * (1.0 + tolerance);
        }

        boolean[] isArt = articulationPoints(adjN, n);
        int components = countComponents(adjN, n);

        // 3. Initial-failure scenario.
        List<String> insights = new ArrayList<>();
        Set<Integer> initialIdx = new LinkedHashSet<>();
        int sourceFiltered = 0;
        for (V s : initialFailures) {
            Integer i = idx.get(s);
            if (i != null) initialIdx.add(i);
            else sourceFiltered++;
        }
        if (sourceFiltered > 0) insights.add("SOURCES_OUT_OF_GRAPH:" + sourceFiltered);

        double initialCascadeFraction = 0.0;
        int initialWaves = 0;
        boolean initialDisconnects = false;
        if (!initialIdx.isEmpty()) {
            CascadeResult res = simulate(adjN, adjW, nodeLoad, capacity, n, initialIdx);
            initialCascadeFraction = (double) res.failed.size() / n;
            initialWaves = res.waves;
            initialDisconnects = wouldDisconnect(adjN, n, res.failed, components);
            insights.add("INITIAL_CASCADE:" + String.format(Locale.ROOT, "%.3f", initialCascadeFraction));
            if (initialDisconnects) insights.add("INITIAL_DISCONNECTS_GRAPH");
        }

        // 4. Single-node stress test over top-K candidates by degree.
        int k = (topK != null) ? Math.min(topK, n) : Math.min(20, n);
        List<Integer> byDegree = new ArrayList<>();
        for (int i = 0; i < n; i++) byDegree.add(i);
        final int[] degRef = new int[n];
        for (int i = 0; i < n; i++) degRef[i] = adjN.get(i).length;
        byDegree.sort(new Comparator<Integer>() {
            public int compare(Integer a, Integer b) {
                int c = Integer.compare(degRef[b], degRef[a]);
                if (c != 0) return c;
                return Integer.compare(a, b);
            }
        });
        List<Integer> stressNodes = new ArrayList<>(byDegree.subList(0, k));

        int[] worstCascade = new int[n];
        int[] failedIn = new int[n];
        boolean[] singleDisconnects = new boolean[n];
        for (Integer s : stressNodes) {
            Set<Integer> seed = Collections.singleton(s);
            CascadeResult r = simulate(adjN, adjW, nodeLoad, capacity, n, seed);
            int extra = r.failed.size() - 1; // exclude the trigger
            if (extra > worstCascade[s]) worstCascade[s] = extra;
            for (Integer f : r.failed) {
                if (f.equals(s)) continue; // do not count the trigger itself as collateral
                failedIn[f]++;
            }
            // A node "single-handedly disconnects" the graph iff its sole removal
            // increases the number of components on the surviving subgraph.
            if (wouldDisconnect(adjN, n, Collections.singleton(s), components)) {
                singleDisconnects[s] = true;
            }
        }

        // 5. Build per-node risk reports.
        double bottleneckPct = (risk == RiskAppetite.CAUTIOUS) ? 0.08
                : (risk == RiskAppetite.AGGRESSIVE) ? 0.15 : 0.10;
        int bottleneckThreshold = Math.max(1, (int) Math.ceil(bottleneckPct * n));
        int criticalThreshold = Math.max(1, (int) Math.ceil(0.30 * n));
        double riskMult = (risk == RiskAppetite.CAUTIOUS) ? 1.10
                : (risk == RiskAppetite.AGGRESSIVE) ? 0.90 : 1.00;

        Map<Object, NodeRisk<?>> perNode = new LinkedHashMap<>();
        int criticalHubs = 0; int bottleneckCount = 0; int atRiskCount = 0; int overloadedCount = 0;
        double maxLoadRatio = 0.0;
        double meanCascade = 0.0; int meanCount = 0;

        for (int i = 0; i < n; i++) {
            V node = vertices.get(i);
            boolean isInitial = initialIdx.contains(i);
            double ratio = capacity[i] > 0 ? nodeLoad[i] / capacity[i] : 0.0;
            if (ratio > maxLoadRatio) maxLoadRatio = ratio;

            List<String> reasons = new ArrayList<>();
            Verdict verdict;
            int worst = worstCascade[i];
            int fIn = failedIn[i];
            boolean stressed = stressNodes.contains(i);

            if (stressed) {
                meanCascade += worst;
                meanCount++;
            }

            if (singleDisconnects[i]) {
                verdict = Verdict.CRITICAL_HUB;
                reasons.add("DISCONNECTS_GRAPH");
                if (worst >= criticalThreshold) reasons.add("CASCADE_GE_30PCT_N");
            } else if (worst >= criticalThreshold) {
                verdict = Verdict.CRITICAL_HUB;
                reasons.add("CASCADE_GE_30PCT_N");
            } else if (worst >= bottleneckThreshold) {
                verdict = Verdict.BOTTLENECK;
                reasons.add("CASCADE_GE_" + (int)Math.round(bottleneckPct*100) + "PCT_N");
            } else if (ratio >= 0.85) {
                verdict = Verdict.OVERLOADED;
                reasons.add("LOAD_RATIO_GE_0_85");
            } else if (fIn > 0) {
                verdict = Verdict.AT_RISK;
                reasons.add("FAILED_IN_" + fIn + "_SCENARIO" + (fIn == 1 ? "" : "S"));
            } else {
                verdict = Verdict.RESILIENT;
                reasons.add("STABLE_ACROSS_SCENARIOS");
            }
            if (isArt[i]) reasons.add("ARTICULATION_POINT");
            if (isInitial) reasons.add("INITIAL_FAILURE");

            // riskScore composition
            double cascadeComp = (n > 1) ? (worst / (double) (n - 1)) * 40.0 : 0.0;
            double scenComp = stressNodes.isEmpty() ? 0.0
                    : Math.min(1.0, fIn / (double) stressNodes.size()) * 30.0;
            double overloadComp = Math.min(1.0, ratio) * 20.0;
            double articComp = isArt[i] ? 10.0 : 0.0;
            double rawScore = (cascadeComp + scenComp + overloadComp + articComp) * riskMult;
            if (rawScore < 0) rawScore = 0;
            if (rawScore > 100) rawScore = 100;

            Priority pri;
            switch (verdict) {
                case CRITICAL_HUB: pri = Priority.P0; break;
                case BOTTLENECK:   pri = Priority.P1; break;
                case OVERLOADED:   pri = Priority.P1; break;
                case AT_RISK:      pri = Priority.P2; break;
                default:           pri = Priority.P3; break;
            }

            switch (verdict) {
                case CRITICAL_HUB: criticalHubs++; break;
                case BOTTLENECK:   bottleneckCount++; break;
                case OVERLOADED:   overloadedCount++; break;
                case AT_RISK:      atRiskCount++; break;
                default: break;
            }

            perNode.put(node, new NodeRisk<V>(node, verdict, pri, rawScore,
                    nodeLoad[i], capacity[i], ratio, isArt[i], worst, fIn, reasons));
        }

        double meanCascadeFrac = (meanCount == 0 || n <= 1) ? 0.0
                : (meanCascade / meanCount) / (n - 1);

        // 6. Playbook (P0-first, deduped).
        List<Action> playbook = new ArrayList<>();
        LinkedHashSet<String> seenIds = new LinkedHashSet<>();

        List<V> criticalList = collect(perNode, Verdict.CRITICAL_HUB);
        if (!criticalList.isEmpty()) {
            addAction(playbook, seenIds, new Action(
                    "HARDEN_CRITICAL_HUBS", Priority.P0,
                    "Harden / add redundancy to critical hubs: " + listNames(criticalList),
                    "architecture", 5, "medium",
                    "Single-node failure cascades to >=30% of network or disconnects the graph"));
        }
        if (maxLoadRatio >= 0.95) {
            addAction(playbook, seenIds, new Action(
                    "INCREASE_HUB_CAPACITY", Priority.P0,
                    "Increase capacity on saturated nodes (max load ratio "
                            + String.format(Locale.ROOT, "%.2f", maxLoadRatio) + ")",
                    "ops", 4, "high",
                    "At least one node is at >=95% of its capacity at baseline"));
        }
        String suggestedEdge = suggestRedundantEdgeForArticulation(perNode, adjN, vertices);
        if (suggestedEdge != null) {
            addAction(playbook, seenIds, new Action(
                    "ADD_REDUNDANT_PATHS", Priority.P1,
                    "Add a redundant edge such as " + suggestedEdge
                            + " around an articulation-point critical hub",
                    "network", 3, "high",
                    "Articulation point is on the cascade-critical list"));
        }
        List<V> bottleneckList = collect(perNode, Verdict.BOTTLENECK);
        if (!bottleneckList.isEmpty()) {
            addAction(playbook, seenIds, new Action(
                    "MITIGATE_BOTTLENECKS", Priority.P1,
                    "Mitigate bottlenecks (cascade >="
                            + (int)Math.round(bottleneckPct*100)
                            + "% of N): " + listNames(bottleneckList),
                    "network", 3, "high",
                    "Failure of these nodes triggers a sizeable cascade"));
        }
        List<V> atRiskList = collect(perNode, Verdict.AT_RISK);
        if (!atRiskList.isEmpty()) {
            addAction(playbook, seenIds, new Action(
                    "MONITOR_AT_RISK", Priority.P2,
                    "Add monitoring/alerting on at-risk nodes: " + listNames(atRiskList),
                    "ops", 2, "high",
                    "These nodes failed in one or more stress scenarios"));
        }
        if (n > 0 && (overloadedCount / (double) n) >= 0.30) {
            addAction(playbook, seenIds, new Action(
                    "RAISE_TOLERANCE_BUFFER", Priority.P2,
                    "Raise tolerance buffer (>=30% of nodes are overloaded at baseline)",
                    "architecture", 3, "medium",
                    String.format(Locale.ROOT, "%d/%d nodes have load/capacity >= 0.85",
                            overloadedCount, n)));
        }
        if (playbook.isEmpty()) {
            addAction(playbook, seenIds, new Action(
                    "BASELINE_RESILIENT", Priority.P3,
                    "Baseline is resilient; no immediate action required",
                    "ops", 1, "high",
                    "No critical hubs, bottlenecks, overloads or at-risk nodes detected"));
        }
        playbook.sort(new Comparator<Action>() {
            public int compare(Action x, Action y) {
                int c = x.priority.ordinal() - y.priority.ordinal();
                if (c != 0) return c;
                return x.id.compareTo(y.id);
            }
        });

        // 7. Insights.
        if (criticalHubs >= 2) insights.add("FRAGILE_TOPOLOGY");
        if (n > 0 && (overloadedCount / (double) n) >= 0.30) insights.add("OVERLOAD_DOMINANT");
        if (meanCount > 0 && meanCascadeFrac < 0.05) insights.add("CASCADE_DAMPENED");

        // 8. Grade.
        String grade;
        boolean criticalDisconnect = false;
        for (NodeRisk<?> nr : perNode.values()) {
            if (nr.verdict == Verdict.CRITICAL_HUB && nr.reasons.contains("DISCONNECTS_GRAPH")) {
                criticalDisconnect = true; break;
            }
        }
        if (criticalDisconnect || initialCascadeFraction >= 0.5) grade = "F";
        else if (criticalHubs > 0 || initialCascadeFraction >= 0.3) grade = "D";
        else if (bottleneckCount >= 1 || initialCascadeFraction >= 0.15) grade = "C";
        else if (atRiskCount >= 1 || overloadedCount >= 1) grade = "B";
        else grade = "A";

        String summary = "VERDICT: grade=" + grade
                + " N=" + n
                + " critical=" + criticalHubs
                + " bottlenecks=" + bottleneckCount
                + " atRisk=" + atRiskCount
                + " overloaded=" + overloadedCount;

        String generatedAt = Instant.now(clock).atOffset(ZoneOffset.UTC).toString();

        return new Plan(n, perNode, playbook, insights, grade,
                initialCascadeFraction, initialWaves, initialDisconnects,
                summary, generatedAt);
    }

    // -- Cascade simulator -------------------------------------------------

    private static final class CascadeResult {
        Set<Integer> failed;
        int waves;
        CascadeResult(Set<Integer> failed, int waves) { this.failed = failed; this.waves = waves; }
    }

    private CascadeResult simulate(List<int[]> adjN, List<double[]> adjW,
                                   double[] baseLoad, double[] capacity, int n,
                                   Set<Integer> initial) {
        double[] load = baseLoad.clone();
        boolean[] dead = new boolean[n];
        Deque<Integer> frontier = new ArrayDeque<>();
        for (Integer s : initial) {
            if (s == null || s < 0 || s >= n) continue;
            if (!dead[s]) { dead[s] = true; frontier.add(s); }
        }
        int waves = 0;
        while (!frontier.isEmpty()) {
            waves++;
            // collect newly-failed this wave for stable processing order
            List<Integer> wave = new ArrayList<>(frontier);
            Collections.sort(wave);
            frontier.clear();
            for (Integer u : wave) {
                int[] nbrs = adjN.get(u);
                double[] ws = adjW.get(u);
                // count surviving neighbours
                int alive = 0;
                for (int j = 0; j < nbrs.length; j++) if (!dead[nbrs[j]]) alive++;
                if (alive == 0) continue;
                double share = load[u] / alive;
                for (int j = 0; j < nbrs.length; j++) {
                    int v = nbrs[j];
                    if (dead[v]) continue;
                    load[v] += share;
                    if (load[v] > capacity[v] + 1e-9 && !dead[v]) {
                        dead[v] = true;
                        frontier.add(v);
                    }
                }
            }
        }
        Set<Integer> failed = new LinkedHashSet<>();
        for (int i = 0; i < n; i++) if (dead[i]) failed.add(i);
        return new CascadeResult(failed, waves);
    }

    private boolean wouldDisconnect(List<int[]> adjN, int n, Set<Integer> dead, int baselineComponents) {
        if (dead.size() >= n) return false; // everything gone -> not "more disconnected"
        int comps = countComponents(adjN, n, dead);
        // surviving nodes only
        int survivors = n - dead.size();
        if (survivors <= 1) return false;
        return comps > baselineComponents;
    }

    private int countComponents(List<int[]> adjN, int n) {
        return countComponents(adjN, n, Collections.<Integer>emptySet());
    }

    private int countComponents(List<int[]> adjN, int n, Set<Integer> excluded) {
        boolean[] seen = new boolean[n];
        for (Integer e : excluded) if (e != null && e >= 0 && e < n) seen[e] = true;
        int comps = 0;
        for (int i = 0; i < n; i++) {
            if (seen[i]) continue;
            comps++;
            Deque<Integer> dq = new ArrayDeque<>();
            dq.add(i); seen[i] = true;
            while (!dq.isEmpty()) {
                int u = dq.poll();
                for (int v : adjN.get(u)) if (!seen[v]) { seen[v] = true; dq.add(v); }
            }
        }
        return comps;
    }

    // -- Articulation points (iterative Tarjan) ----------------------------

    private boolean[] articulationPoints(List<int[]> adjN, int n) {
        boolean[] isArt = new boolean[n];
        int[] disc = new int[n];
        int[] low = new int[n];
        int[] parent = new int[n];
        Arrays.fill(disc, -1);
        Arrays.fill(parent, -1);
        int time = 0;
        for (int start = 0; start < n; start++) {
            if (disc[start] != -1) continue;
            Deque<int[]> stack = new ArrayDeque<>();
            stack.push(new int[]{start, 0});
            disc[start] = low[start] = time++;
            int rootChildren = 0;
            while (!stack.isEmpty()) {
                int[] top = stack.peek();
                int u = top[0]; int i = top[1];
                int[] nbrs = adjN.get(u);
                if (i < nbrs.length) {
                    top[1] = i + 1;
                    int v = nbrs[i];
                    if (disc[v] == -1) {
                        parent[v] = u;
                        disc[v] = low[v] = time++;
                        if (u == start) rootChildren++;
                        stack.push(new int[]{v, 0});
                    } else if (v != parent[u]) {
                        low[u] = Math.min(low[u], disc[v]);
                    }
                } else {
                    stack.pop();
                    if (!stack.isEmpty()) {
                        int parentU = stack.peek()[0];
                        low[parentU] = Math.min(low[parentU], low[u]);
                        if (parentU != start && low[u] >= disc[parentU]) {
                            isArt[parentU] = true;
                        }
                    }
                }
            }
            if (rootChildren > 1) isArt[start] = true;
        }
        return isArt;
    }

    // -- Helpers -----------------------------------------------------------

    private List<V> collect(Map<Object, NodeRisk<?>> perNode, Verdict v) {
        List<V> out = new ArrayList<>();
        for (Map.Entry<Object, NodeRisk<?>> e : perNode.entrySet()) {
            if (e.getValue().verdict == v) {
                @SuppressWarnings("unchecked")
                V n = (V) e.getKey();
                out.add(n);
            }
        }
        out.sort(new Comparator<V>() {
            public int compare(V a, V b) { return String.valueOf(a).compareTo(String.valueOf(b)); }
        });
        return out;
    }

    private String listNames(List<V> xs) {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(xs.size(), 10);
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(", ");
            sb.append(xs.get(i));
        }
        if (xs.size() > limit) sb.append(", ... (+").append(xs.size() - limit).append(")");
        return sb.toString();
    }

    private void addAction(List<Action> playbook, Set<String> seenIds, Action a) {
        if (seenIds.add(a.id)) playbook.add(a);
    }

    private String suggestRedundantEdgeForArticulation(Map<Object, NodeRisk<?>> perNode,
                                                       List<int[]> adjN, List<V> vertices) {
        for (Map.Entry<Object, NodeRisk<?>> e : perNode.entrySet()) {
            NodeRisk<?> nr = e.getValue();
            if (nr.verdict != Verdict.CRITICAL_HUB || !nr.articulation) continue;
            int seedIdx = vertices.indexOf(e.getKey());
            if (seedIdx < 0) continue;
            int[] nbrs = adjN.get(seedIdx);
            if (nbrs.length < 2) continue;
            for (int i = 0; i < nbrs.length; i++) {
                for (int j = i + 1; j < nbrs.length; j++) {
                    int a = nbrs[i]; int b = nbrs[j];
                    int[] aN = adjN.get(a);
                    boolean adj = false;
                    for (int x : aN) if (x == b) { adj = true; break; }
                    if (!adj) return "(" + vertices.get(a) + "," + vertices.get(b) + ")";
                }
            }
        }
        return null;
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.3f", d);
    }

    // -- Renderers ---------------------------------------------------------

    public String render(Plan p) {
        StringBuilder sb = new StringBuilder();
        sb.append(p.summary).append('\n');
        sb.append("GraphCascadingFailureAdvisor  N=").append(p.graphSize)
                .append(" grade=").append(p.grade)
                .append(" initialCascade=").append(fmt(p.initialCascadeFraction))
                .append(" waves=").append(p.initialWaves)
                .append(p.initialDisconnects ? " (disconnects)" : "")
                .append('\n');
        sb.append("  Per-node risks:\n");
        for (NodeRisk<?> nr : p.perNode.values()) {
            sb.append("    - ").append(nr.node)
                    .append("  ").append(nr.verdict).append('/').append(nr.priority)
                    .append("  risk=").append(fmt(nr.riskScore))
                    .append("  load/cap=").append(fmt(nr.baselineLoad))
                    .append('/').append(fmt(nr.capacity))
                    .append("  worstCascade=").append(nr.worstCascadeSize)
                    .append("  reasons=").append(nr.reasons).append('\n');
        }
        sb.append("  Playbook:\n");
        for (Action a : p.playbook) {
            sb.append("    [").append(a.priority).append("] ").append(a.id)
                    .append(" - ").append(a.label).append('\n');
        }
        if (!p.insights.isEmpty()) sb.append("  Insights: ").append(p.insights).append('\n');
        return sb.toString();
    }

    public String toMarkdown(Plan p) {
        StringBuilder sb = new StringBuilder();
        sb.append("# GraphCascadingFailureAdvisor\n\n");
        sb.append("## Summary\n\n");
        sb.append("**Grade:** ").append(p.grade).append("  ");
        sb.append("**N:** ").append(p.graphSize).append("  ");
        sb.append("**Initial cascade:** ").append(fmt(p.initialCascadeFraction))
                .append(" (").append(p.initialWaves).append(" waves)");
        if (p.initialDisconnects) sb.append(" *(disconnects graph)*");
        sb.append("\n\n");
        sb.append(p.summary).append("\n\n");
        sb.append("## Per-node risks\n\n");
        sb.append("| Node | Verdict | Priority | Risk | Load/Cap | WorstCascade | Reasons |\n");
        sb.append("|------|---------|----------|------|----------|--------------|---------|\n");
        for (NodeRisk<?> nr : p.perNode.values()) {
            sb.append("| ").append(nr.node)
                    .append(" | ").append(nr.verdict)
                    .append(" | ").append(nr.priority)
                    .append(" | ").append(fmt(nr.riskScore))
                    .append(" | ").append(fmt(nr.baselineLoad)).append('/').append(fmt(nr.capacity))
                    .append(" | ").append(nr.worstCascadeSize)
                    .append(" | ").append(String.join(", ", nr.reasons)).append(" |\n");
        }
        sb.append("\n## Playbook\n\n");
        for (Action a : p.playbook) {
            sb.append("- **[").append(a.priority).append("] ").append(a.id).append("** &mdash; ")
                    .append(a.label).append("  *(owner: ").append(a.owner)
                    .append(", blastRadius: ").append(a.blastRadius)
                    .append(", reversibility: ").append(a.reversibility).append(")*\n");
        }
        sb.append("\n## Insights\n\n");
        if (p.insights.isEmpty()) sb.append("- (none)\n");
        else for (String s : p.insights) sb.append("- ").append(s).append('\n');
        return sb.toString();
    }

    public String toJson(Plan p) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        kv(sb, "generatedAt", q(p.generatedAt)); sb.append(',');
        kv(sb, "grade", q(p.grade)); sb.append(',');
        kv(sb, "graphSize", String.valueOf(p.graphSize)); sb.append(',');
        kv(sb, "initialCascadeFraction", num(p.initialCascadeFraction)); sb.append(',');
        kv(sb, "initialDisconnects", String.valueOf(p.initialDisconnects)); sb.append(',');
        kv(sb, "initialWaves", String.valueOf(p.initialWaves)); sb.append(',');
        // insights (sorted)
        sb.append(q("insights")).append(":[");
        List<String> ins = new ArrayList<>(p.insights);
        Collections.sort(ins);
        for (int i = 0; i < ins.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(q(ins.get(i)));
        }
        sb.append("],");
        // perNode (sorted by node-name)
        sb.append(q("perNode")).append(":{");
        List<Object> keys = new ArrayList<>(p.perNode.keySet());
        keys.sort(new Comparator<Object>() {
            public int compare(Object a, Object b) { return String.valueOf(a).compareTo(String.valueOf(b)); }
        });
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sb.append(',');
            Object key = keys.get(i);
            NodeRisk<?> nr = p.perNode.get(key);
            sb.append(q(String.valueOf(key))).append(":{");
            kv(sb, "articulation", String.valueOf(nr.articulation)); sb.append(',');
            kv(sb, "baselineLoad", num(nr.baselineLoad)); sb.append(',');
            kv(sb, "capacity", num(nr.capacity)); sb.append(',');
            kv(sb, "loadRatio", num(nr.loadRatio)); sb.append(',');
            kv(sb, "priority", q(nr.priority.name())); sb.append(',');
            // reasons sorted
            sb.append(q("reasons")).append(":[");
            List<String> rs = new ArrayList<>(nr.reasons);
            Collections.sort(rs);
            for (int j = 0; j < rs.size(); j++) {
                if (j > 0) sb.append(',');
                sb.append(q(rs.get(j)));
            }
            sb.append("],");
            kv(sb, "riskScore", num(nr.riskScore)); sb.append(',');
            kv(sb, "scenariosFailedIn", String.valueOf(nr.scenariosFailedIn)); sb.append(',');
            kv(sb, "verdict", q(nr.verdict.name())); sb.append(',');
            kv(sb, "worstCascadeSize", String.valueOf(nr.worstCascadeSize));
            sb.append('}');
        }
        sb.append("},");
        // playbook (preserved priority/id order)
        sb.append(q("playbook")).append(":[");
        for (int i = 0; i < p.playbook.size(); i++) {
            Action a = p.playbook.get(i);
            if (i > 0) sb.append(',');
            sb.append('{');
            kv(sb, "blastRadius", String.valueOf(a.blastRadius)); sb.append(',');
            kv(sb, "id", q(a.id)); sb.append(',');
            kv(sb, "label", q(a.label)); sb.append(',');
            kv(sb, "owner", q(a.owner)); sb.append(',');
            kv(sb, "priority", q(a.priority.name())); sb.append(',');
            kv(sb, "reason", q(a.reason)); sb.append(',');
            kv(sb, "reversibility", q(a.reversibility));
            sb.append('}');
        }
        sb.append("],");
        kv(sb, "summary", q(p.summary));
        sb.append('}');
        return sb.toString();
    }

    private static void kv(StringBuilder sb, String k, String v) {
        sb.append(q(k)).append(':').append(v);
    }

    private static String q(String s) {
        if (s == null) s = "";
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': b.append("\\\\"); break;
                case '"': b.append("\\\""); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
        return b.toString();
    }

    private static String num(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return "0";
        return String.format(Locale.ROOT, "%.6f", d);
    }
}
