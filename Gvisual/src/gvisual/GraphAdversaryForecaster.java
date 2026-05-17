package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;

import java.util.*;

/**
 * GraphAdversaryForecaster -- agentic adversarial-intent modeler.
 *
 * <p>Given an original graph snapshot and an ordered <em>attack trace</em>
 * (the list of node IDs that were removed, in chronological order), the
 * forecaster:</p>
 * <ol>
 *   <li><b>Infers attacker strategy.</b> Replays the trace step by step
 *       against the residual graph. For each candidate strategy it scores
 *       how well that strategy explains the observed removal order. The
 *       per-step score is {@code 1 - chosenRank / liveNodes}; the average
 *       across steps becomes the strategy's confidence in {@code [0,1]}.</li>
 *   <li><b>Forecasts next targets.</b> Picks the inferred top strategy (or
 *       a blend of top-2 weighted by confidence) and ranks the still-live
 *       nodes. Probabilities are produced by a softmax over the strategy's
 *       internal scoring function with a configurable temperature.</li>
 *   <li><b>Recommends defenses.</b> For each top forecast target it emits
 *       a {@link Defense} tagged with a priority ({@link Priority#P0}/P1/P2)
 *       and an action ({@link DefenseAction#HARDEN},
 *       {@link DefenseAction#ADD_REDUNDANT_EDGE},
 *       {@link DefenseAction#MONITOR}, {@link DefenseAction#DECOY}). For
 *       articulation/bridge targets it names a concrete (u,v) reinforcement
 *       edge.</li>
 * </ol>
 *
 * <p>Candidate strategies:</p>
 * <ul>
 *   <li>{@link Strategy#RANDOM}                -- uniform expectation</li>
 *   <li>{@link Strategy#DEGREE_TARGETING}      -- highest current degree</li>
 *   <li>{@link Strategy#BETWEENNESS_TARGETING} -- highest betweenness (Brandes)</li>
 *   <li>{@link Strategy#BRIDGE_TARGETING}      -- articulation points / cut vertices</li>
 *   <li>{@link Strategy#COMMUNITY_CUT}         -- nodes incident to many cross-community edges (label propagation)</li>
 *   <li>{@link Strategy#PERIPHERAL}            -- lowest-degree (camouflage)</li>
 * </ul>
 *
 * <p>Pure Java, single-file, no extra dependencies beyond JUNG + JDK.
 * Deterministic for a given input (no internal randomness).</p>
 *
 * <pre>
 *   GraphAdversaryForecaster fc =
 *           new GraphAdversaryForecaster(originalGraph, attackTrace).withTopK(5);
 *   GraphAdversaryForecaster.Forecast f = fc.analyze();
 *   System.out.println(fc.toMarkdown(f));
 * </pre>
 *
 * @author sauravbhattacharya001
 */
public final class GraphAdversaryForecaster {

    // -- Public types ------------------------------------------------------

    public enum Strategy {
        RANDOM,
        DEGREE_TARGETING,
        BETWEENNESS_TARGETING,
        BRIDGE_TARGETING,
        COMMUNITY_CUT,
        PERIPHERAL
    }

    public enum DefenseAction { HARDEN, ADD_REDUNDANT_EDGE, MONITOR, DECOY }
    public enum Priority { P0, P1, P2 }

    /** Inferred score for one candidate strategy. */
    public static final class StrategyScore {
        public final Strategy strategy;
        public final double confidence; // [0, 1]
        public StrategyScore(Strategy strategy, double confidence) {
            this.strategy = strategy;
            this.confidence = confidence;
        }
        @Override public String toString() {
            return strategy + "(" + String.format(Locale.ROOT, "%.3f", confidence) + ")";
        }
    }

    /** A forecasted next target. */
    public static final class TargetForecast {
        public final String nodeId;
        public final double probability;     // softmax over strategy score
        public final double expectedImpact;  // est. drop in largest component if removed (0..1)
        public TargetForecast(String nodeId, double probability, double expectedImpact) {
            this.nodeId = nodeId;
            this.probability = probability;
            this.expectedImpact = expectedImpact;
        }
    }

    /** A defense recommendation for a forecasted target. */
    public static final class Defense {
        public final String nodeId;
        public final DefenseAction action;
        public final Priority priority;
        public final String detail;             // human-readable rationale
        public final String suggestedEdgeU;     // nullable
        public final String suggestedEdgeV;     // nullable
        public Defense(String nodeId, DefenseAction action, Priority priority,
                       String detail, String suggestedEdgeU, String suggestedEdgeV) {
            this.nodeId = nodeId;
            this.action = action;
            this.priority = priority;
            this.detail = detail;
            this.suggestedEdgeU = suggestedEdgeU;
            this.suggestedEdgeV = suggestedEdgeV;
        }
    }

    /** Bundled forecast output. */
    public static final class Forecast {
        public final List<StrategyScore> strategyRanking;
        public final StrategyScore primary;
        public final StrategyScore secondary; // nullable
        public final List<TargetForecast> nextTargets;
        public final List<Defense> defenses;
        public final int liveNodeCount;
        public final int removedCount;
        public final int ignoredTraceCount; // trace entries not present in original graph

        Forecast(List<StrategyScore> strategyRanking, StrategyScore primary, StrategyScore secondary,
                 List<TargetForecast> nextTargets, List<Defense> defenses,
                 int liveNodeCount, int removedCount, int ignoredTraceCount) {
            this.strategyRanking = strategyRanking;
            this.primary = primary;
            this.secondary = secondary;
            this.nextTargets = nextTargets;
            this.defenses = defenses;
            this.liveNodeCount = liveNodeCount;
            this.removedCount = removedCount;
            this.ignoredTraceCount = ignoredTraceCount;
        }
    }

    // -- Configuration -----------------------------------------------------

    private final Graph<String, Edge> originalGraph;
    private final List<String> attackTrace;
    private int topK = 5;
    private double temperature = 1.0;

    public GraphAdversaryForecaster(Graph<String, Edge> originalGraph, List<String> attackTrace) {
        if (originalGraph == null) throw new IllegalArgumentException("originalGraph must not be null");
        if (attackTrace == null)   throw new IllegalArgumentException("attackTrace must not be null");
        this.originalGraph = originalGraph;
        this.attackTrace = new ArrayList<>(attackTrace);
    }

    public GraphAdversaryForecaster withTopK(int k) {
        if (k < 1) throw new IllegalArgumentException("topK must be >= 1");
        this.topK = k;
        return this;
    }

    public GraphAdversaryForecaster withTemperature(double t) {
        if (!(t > 0) || Double.isInfinite(t) || Double.isNaN(t))
            throw new IllegalArgumentException("temperature must be a positive finite number");
        this.temperature = t;
        return this;
    }

    // -- Analyze -----------------------------------------------------------

    public Forecast analyze() {
        // Clone the original graph into a residual we mutate as we replay the trace.
        Graph<String, Edge> residual = cloneGraph(originalGraph);

        Strategy[] candidates = Strategy.values();
        Map<Strategy, double[]> stepScores = new EnumMap<>(Strategy.class);
        for (Strategy s : candidates) stepScores.put(s, new double[]{0.0, 0.0}); // [sumScore, countSteps]

        int ignored = 0;
        for (String chosen : attackTrace) {
            if (chosen == null || !residual.containsVertex(chosen)) { ignored++; continue; }
            int liveCount = residual.getVertexCount();
            if (liveCount <= 1) { residual.removeVertex(chosen); continue; }

            for (Strategy s : candidates) {
                Map<String, Double> scores = strategyScores(s, residual);
                double midRank = midRankOf(chosen, scores); // accounts for ties
                double quality = 1.0 - midRank / Math.max(1, liveCount);
                double[] acc = stepScores.get(s);
                acc[0] += quality;
                acc[1] += 1.0;
            }
            residual.removeVertex(chosen);
        }

        // Build per-strategy confidence ranking.
        List<StrategyScore> ranking = new ArrayList<>();
        for (Strategy s : candidates) {
            double[] acc = stepScores.get(s);
            double conf = acc[1] == 0 ? 0.0 : acc[0] / acc[1];
            ranking.add(new StrategyScore(s, conf));
        }
        ranking.sort((a, b) -> Double.compare(b.confidence, a.confidence));

        StrategyScore primary = ranking.get(0);
        StrategyScore secondary = ranking.size() > 1 ? ranking.get(1) : null;

        // If the trace was empty we have no evidence; fall back to a sensible default
        // (DEGREE_TARGETING is the most common adversary pattern in literature).
        if (attackTrace.size() - ignored == 0) {
            primary = new StrategyScore(Strategy.DEGREE_TARGETING, 0.0);
            secondary = new StrategyScore(Strategy.BETWEENNESS_TARGETING, 0.0);
            ranking = new ArrayList<>();
            for (Strategy s : candidates) ranking.add(new StrategyScore(s, 0.0));
        }

        // Forecast next K targets on the current residual.
        List<TargetForecast> next = forecastNext(residual, primary, secondary);
        List<Defense> defenses = recommendDefenses(residual, next);

        return new Forecast(ranking, primary, secondary, next, defenses,
                residual.getVertexCount(),
                originalGraph.getVertexCount() - residual.getVertexCount(),
                ignored);
    }

    // -- Strategy scoring functions ---------------------------------------

    /**
     * Returns a "preference score" per live node for the given strategy.
     * Higher score = more likely target under that strategy.
     */
    private Map<String, Double> strategyScores(Strategy s, Graph<String, Edge> g) {
        Map<String, Double> out = new LinkedHashMap<>();
        Collection<String> live = g.getVertices();
        switch (s) {
            case RANDOM: {
                for (String v : live) out.put(v, 1.0);
                return out;
            }
            case DEGREE_TARGETING: {
                for (String v : live) out.put(v, (double) g.degree(v));
                return out;
            }
            case PERIPHERAL: {
                int maxDeg = 0;
                for (String v : live) maxDeg = Math.max(maxDeg, g.degree(v));
                for (String v : live) out.put(v, (double) (maxDeg - g.degree(v) + 1));
                return out;
            }
            case BETWEENNESS_TARGETING: {
                Map<String, Double> bc = brandesBetweenness(g);
                // Shift up by 1 so degree-0 nodes still rank (deterministic, no negatives).
                for (String v : live) out.put(v, bc.getOrDefault(v, 0.0) + 1.0);
                return out;
            }
            case BRIDGE_TARGETING: {
                Set<String> aps = articulationPoints(g);
                Set<String> bridgeEnds = bridgeEndpoints(g);
                for (String v : live) {
                    double sc = 0.0;
                    if (aps.contains(v)) sc += 2.0;
                    if (bridgeEnds.contains(v)) sc += 1.0;
                    sc += 0.01 * g.degree(v); // tiebreaker
                    out.put(v, sc + 0.001);
                }
                return out;
            }
            case COMMUNITY_CUT: {
                Map<String, Integer> labels = labelPropagation(g);
                for (String v : live) {
                    int cross = 0;
                    Integer mine = labels.get(v);
                    for (String u : g.getNeighbors(v)) {
                        if (!Objects.equals(labels.get(u), mine)) cross++;
                    }
                    out.put(v, (double) cross + 0.01 * g.degree(v) + 0.001);
                }
                return out;
            }
        }
        for (String v : live) out.put(v, 1.0);
        return out;
    }

    /**
     * Returns the mid-rank of {@code chosen} in scores. Mid-rank correctly
     * scores tied buckets: a node tied with {@code k} other nodes at the top
     * has expected rank {@code k/2}, which prevents a non-discriminative
     * strategy (e.g. RANDOM, where all live nodes are tied) from spuriously
     * scoring 1.0. Higher score = better rank (lower index).
     */
    private static double midRankOf(String chosen, Map<String, Double> scores) {
        Double chosenScore = scores.get(chosen);
        if (chosenScore == null) return scores.size();
        int strictlyBetter = 0;
        int tied = 0;
        for (Map.Entry<String, Double> e : scores.entrySet()) {
            int c = Double.compare(e.getValue(), chosenScore);
            if (c > 0) strictlyBetter++;
            else if (c == 0) tied++; // includes the chosen itself
        }
        // Mid-rank within the tie bucket: (tied - 1) / 2 entries are conceptually "above".
        return strictlyBetter + (tied - 1) / 2.0;
    }

    // -- Forecast ----------------------------------------------------------

    private List<TargetForecast> forecastNext(Graph<String, Edge> residual,
                                              StrategyScore primary, StrategyScore secondary) {
        if (residual.getVertexCount() == 0) return Collections.emptyList();

        Map<String, Double> primaryScores = strategyScores(primary.strategy, residual);
        Map<String, Double> blended = new LinkedHashMap<>(primaryScores);

        // Blend with secondary if it has appreciable confidence.
        if (secondary != null && secondary.confidence > 0.0
                && primary.confidence > 0.0
                && secondary.confidence >= 0.5 * primary.confidence) {
            Map<String, Double> secScores = strategyScores(secondary.strategy, residual);
            double total = primary.confidence + secondary.confidence;
            double w1 = primary.confidence / total;
            double w2 = secondary.confidence / total;
            Map<String, Double> p = normalize(primaryScores);
            Map<String, Double> q = normalize(secScores);
            for (String v : blended.keySet()) {
                blended.put(v, w1 * p.getOrDefault(v, 0.0) + w2 * q.getOrDefault(v, 0.0));
            }
        }

        // Softmax with temperature.
        double maxScore = -Double.MAX_VALUE;
        for (double v : blended.values()) if (v > maxScore) maxScore = v;
        Map<String, Double> exp = new LinkedHashMap<>();
        double sum = 0.0;
        for (Map.Entry<String, Double> e : blended.entrySet()) {
            double z = (e.getValue() - maxScore) / temperature;
            double ev = Math.exp(z);
            exp.put(e.getKey(), ev);
            sum += ev;
        }
        if (sum == 0.0 || Double.isNaN(sum)) {
            int n = blended.size();
            for (String k : blended.keySet()) exp.put(k, 1.0 / n);
            sum = 1.0;
        }
        // Sort by probability desc (then lex), take topK.
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(exp.entrySet());
        final double s = sum;
        sorted.sort((a, b) -> {
            int c = Double.compare(b.getValue(), a.getValue());
            if (c != 0) return c;
            return a.getKey().compareTo(b.getKey());
        });

        int k = Math.min(topK, sorted.size());
        List<TargetForecast> out = new ArrayList<>();
        double probSum = 0.0;
        for (int i = 0; i < k; i++) {
            String v = sorted.get(i).getKey();
            double prob = sorted.get(i).getValue() / s;
            probSum += prob;
            out.add(new TargetForecast(v, prob, expectedImpact(residual, v)));
        }
        // Renormalize the truncated list so probabilities of returned set sum to ~1.
        if (probSum > 0) {
            List<TargetForecast> renorm = new ArrayList<>(out.size());
            for (TargetForecast tf : out) {
                renorm.add(new TargetForecast(tf.nodeId, tf.probability / probSum, tf.expectedImpact));
            }
            out = renorm;
        }
        return out;
    }

    private static Map<String, Double> normalize(Map<String, Double> m) {
        double sum = 0.0;
        for (double v : m.values()) sum += Math.max(0.0, v);
        Map<String, Double> out = new LinkedHashMap<>();
        if (sum <= 0) {
            int n = m.size();
            for (String k : m.keySet()) out.put(k, n == 0 ? 0.0 : 1.0 / n);
            return out;
        }
        for (Map.Entry<String, Double> e : m.entrySet()) {
            out.put(e.getKey(), Math.max(0.0, e.getValue()) / sum);
        }
        return out;
    }

    /** Fraction by which the largest connected component shrinks if v is removed. */
    private double expectedImpact(Graph<String, Edge> g, String v) {
        int origLcc = largestComponentSize(g);
        if (origLcc == 0) return 0.0;
        Graph<String, Edge> copy = cloneGraph(g);
        copy.removeVertex(v);
        int newLcc = largestComponentSize(copy);
        return Math.max(0.0, (origLcc - newLcc) / (double) origLcc);
    }

    // -- Defense recommendations ------------------------------------------

    private List<Defense> recommendDefenses(Graph<String, Edge> g, List<TargetForecast> targets) {
        if (targets.isEmpty()) return Collections.emptyList();
        Set<String> aps = articulationPoints(g);
        Set<String> bridgeEnds = bridgeEndpoints(g);
        int avgDeg = 0;
        if (g.getVertexCount() > 0) {
            int sum = 0;
            for (String v : g.getVertices()) sum += g.degree(v);
            avgDeg = (int) Math.round(sum / (double) g.getVertexCount());
        }

        List<Defense> out = new ArrayList<>();
        for (TargetForecast t : targets) {
            String v = t.nodeId;
            int deg = g.containsVertex(v) ? g.degree(v) : 0;
            Priority pr;
            if (t.expectedImpact >= 0.30) pr = Priority.P0;
            else if (t.expectedImpact >= 0.10) pr = Priority.P1;
            else pr = Priority.P2;

            if (aps.contains(v) || bridgeEnds.contains(v)) {
                String[] edge = suggestRedundantEdge(g, v);
                String detail = "Articulation/bridge endpoint -- removal would fragment the network. "
                        + "Add a redundant edge to dissolve the cut.";
                out.add(new Defense(v, DefenseAction.ADD_REDUNDANT_EDGE, pr, detail, edge[0], edge[1]));
            } else if (deg >= Math.max(2, 2 * avgDeg)) {
                out.add(new Defense(v, DefenseAction.HARDEN, pr,
                        "High-degree hub (degree " + deg + ") -- harden monitoring and access controls.",
                        null, null));
            } else if (deg <= 1) {
                out.add(new Defense(v, DefenseAction.DECOY, pr,
                        "Peripheral / low-degree node -- if attacker is camouflaging, consider planting decoy.",
                        null, null));
            } else {
                out.add(new Defense(v, DefenseAction.MONITOR, pr,
                        "Mid-tier node -- monitor for unusual traffic; no structural change required.",
                        null, null));
            }
        }
        return out;
    }

    /**
     * Suggest a (u, w) edge addition where u and w are two distinct neighbors of v
     * that are NOT currently connected. Closing such a triangle reduces v's
     * articulation effect. Falls back to (firstNeighbor, otherLiveNode).
     */
    private String[] suggestRedundantEdge(Graph<String, Edge> g, String v) {
        if (!g.containsVertex(v)) return new String[]{null, null};
        List<String> nbrs = new ArrayList<>(g.getNeighbors(v));
        Collections.sort(nbrs);
        for (int i = 0; i < nbrs.size(); i++) {
            for (int j = i + 1; j < nbrs.size(); j++) {
                String a = nbrs.get(i), b = nbrs.get(j);
                if (!g.isNeighbor(a, b)) return new String[]{a, b};
            }
        }
        // No two non-connected neighbors; suggest connecting first neighbor to any other live non-v node.
        if (!nbrs.isEmpty()) {
            String a = nbrs.get(0);
            for (String x : g.getVertices()) {
                if (!x.equals(v) && !x.equals(a) && !g.isNeighbor(a, x)) return new String[]{a, x};
            }
        }
        return new String[]{null, null};
    }

    // -- Structural helpers -----------------------------------------------

    private static Graph<String, Edge> cloneGraph(Graph<String, Edge> g) {
        Graph<String, Edge> out = new UndirectedSparseGraph<>();
        for (String v : g.getVertices()) out.addVertex(v);
        for (Edge e : g.getEdges()) {
            Collection<String> ep = g.getIncidentVertices(e);
            Iterator<String> it = ep.iterator();
            String u = it.hasNext() ? it.next() : null;
            String w = it.hasNext() ? it.next() : u;
            if (u == null || w == null) continue;
            // Reuse edge instance — JUNG keys edges by identity; we need a fresh instance to belong to new graph.
            Edge fresh = new Edge(e.getType() == null ? "f" : e.getType(), u, w);
            out.addEdge(fresh, u, w);
        }
        return out;
    }

    private static int largestComponentSize(Graph<String, Edge> g) {
        Set<String> seen = new HashSet<>();
        int best = 0;
        for (String v : g.getVertices()) {
            if (seen.contains(v)) continue;
            int size = 0;
            Deque<String> stack = new ArrayDeque<>();
            stack.push(v);
            seen.add(v);
            while (!stack.isEmpty()) {
                String x = stack.pop();
                size++;
                for (String y : g.getNeighbors(x)) {
                    if (seen.add(y)) stack.push(y);
                }
            }
            if (size > best) best = size;
        }
        return best;
    }

    /**
     * Brandes' betweenness centrality (unweighted). O(V * (V + E)).
     */
    private static Map<String, Double> brandesBetweenness(Graph<String, Edge> g) {
        Map<String, Double> cb = new LinkedHashMap<>();
        for (String v : g.getVertices()) cb.put(v, 0.0);

        for (String s : g.getVertices()) {
            Deque<String> stack = new ArrayDeque<>();
            Map<String, List<String>> pred = new HashMap<>();
            Map<String, Double> sigma = new HashMap<>();
            Map<String, Integer> dist = new HashMap<>();
            for (String v : g.getVertices()) {
                pred.put(v, new ArrayList<>());
                sigma.put(v, 0.0);
                dist.put(v, -1);
            }
            sigma.put(s, 1.0);
            dist.put(s, 0);
            Deque<String> queue = new ArrayDeque<>();
            queue.add(s);
            while (!queue.isEmpty()) {
                String v = queue.poll();
                stack.push(v);
                for (String w : g.getNeighbors(v)) {
                    if (dist.get(w) < 0) {
                        dist.put(w, dist.get(v) + 1);
                        queue.add(w);
                    }
                    if (dist.get(w) == dist.get(v) + 1) {
                        sigma.put(w, sigma.get(w) + sigma.get(v));
                        pred.get(w).add(v);
                    }
                }
            }
            Map<String, Double> delta = new HashMap<>();
            for (String v : g.getVertices()) delta.put(v, 0.0);
            while (!stack.isEmpty()) {
                String w = stack.pop();
                for (String v : pred.get(w)) {
                    double c = (sigma.get(v) / sigma.get(w)) * (1.0 + delta.get(w));
                    delta.put(v, delta.get(v) + c);
                }
                if (!w.equals(s)) cb.put(w, cb.get(w) + delta.get(w));
            }
        }
        // Undirected -> divide by 2.
        for (Map.Entry<String, Double> e : cb.entrySet()) cb.put(e.getKey(), e.getValue() / 2.0);
        return cb;
    }

    /** Articulation points (Tarjan, iterative-safe via recursion; graphs here are modest). */
    private static Set<String> articulationPoints(Graph<String, Edge> g) {
        Set<String> aps = new LinkedHashSet<>();
        Map<String, Integer> disc = new HashMap<>();
        Map<String, Integer> low = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        int[] timer = {0};
        for (String v : g.getVertices()) {
            if (!disc.containsKey(v)) apDfs(g, v, disc, low, parent, aps, timer);
        }
        return aps;
    }

    private static void apDfs(Graph<String, Edge> g, String u, Map<String, Integer> disc,
                              Map<String, Integer> low, Map<String, String> parent,
                              Set<String> aps, int[] timer) {
        // Iterative DFS to avoid stack overflow on large graphs.
        Deque<Object[]> stack = new ArrayDeque<>();
        stack.push(new Object[]{u, new ArrayList<>(g.getNeighbors(u)).iterator(), 0});
        disc.put(u, timer[0]);
        low.put(u, timer[0]);
        timer[0]++;
        parent.putIfAbsent(u, null);
        while (!stack.isEmpty()) {
            Object[] frame = stack.peek();
            String cur = (String) frame[0];
            @SuppressWarnings("unchecked")
            Iterator<String> it = (Iterator<String>) frame[1];
            int children = (int) frame[2];
            if (it.hasNext()) {
                String v = it.next();
                if (!disc.containsKey(v)) {
                    parent.put(v, cur);
                    disc.put(v, timer[0]);
                    low.put(v, timer[0]);
                    timer[0]++;
                    frame[2] = children + 1;
                    stack.push(new Object[]{v, new ArrayList<>(g.getNeighbors(v)).iterator(), 0});
                } else if (!v.equals(parent.get(cur))) {
                    low.put(cur, Math.min(low.get(cur), disc.get(v)));
                }
            } else {
                stack.pop();
                String par = parent.get(cur);
                if (par != null) {
                    low.put(par, Math.min(low.get(par), low.get(cur)));
                    if (low.get(cur) >= disc.get(par) && parent.get(par) != null) aps.add(par);
                } else {
                    // root: articulation iff has >1 DFS child
                    if (children > 1) aps.add(cur);
                }
            }
        }
    }

    /** Endpoints of bridge (cut) edges. */
    private static Set<String> bridgeEndpoints(Graph<String, Edge> g) {
        Set<String> out = new LinkedHashSet<>();
        Map<String, Integer> disc = new HashMap<>();
        Map<String, Integer> low = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        int[] timer = {0};
        for (String s : g.getVertices()) {
            if (disc.containsKey(s)) continue;
            disc.put(s, timer[0]); low.put(s, timer[0]); timer[0]++;
            parent.put(s, null);
            Deque<Object[]> stack = new ArrayDeque<>();
            stack.push(new Object[]{s, new ArrayList<>(g.getNeighbors(s)).iterator()});
            while (!stack.isEmpty()) {
                Object[] frame = stack.peek();
                String u = (String) frame[0];
                @SuppressWarnings("unchecked")
                Iterator<String> it = (Iterator<String>) frame[1];
                if (it.hasNext()) {
                    String v = it.next();
                    if (!disc.containsKey(v)) {
                        parent.put(v, u);
                        disc.put(v, timer[0]); low.put(v, timer[0]); timer[0]++;
                        stack.push(new Object[]{v, new ArrayList<>(g.getNeighbors(v)).iterator()});
                    } else if (!v.equals(parent.get(u))) {
                        low.put(u, Math.min(low.get(u), disc.get(v)));
                    }
                } else {
                    stack.pop();
                    String par = parent.get(u);
                    if (par != null) {
                        low.put(par, Math.min(low.get(par), low.get(u)));
                        if (low.get(u) > disc.get(par)) {
                            out.add(par);
                            out.add(u);
                        }
                    }
                }
            }
        }
        return out;
    }

    /** Simple synchronous label propagation (deterministic): 5 passes, ties broken by node id. */
    private static Map<String, Integer> labelPropagation(Graph<String, Edge> g) {
        Map<String, Integer> labels = new HashMap<>();
        List<String> nodes = new ArrayList<>(g.getVertices());
        Collections.sort(nodes);
        int i = 0;
        for (String v : nodes) labels.put(v, i++);
        for (int pass = 0; pass < 5; pass++) {
            boolean changed = false;
            Map<String, Integer> next = new HashMap<>(labels);
            for (String v : nodes) {
                Collection<String> nbrs = g.getNeighbors(v);
                if (nbrs == null || nbrs.isEmpty()) continue;
                Map<Integer, Integer> counts = new HashMap<>();
                for (String u : nbrs) {
                    Integer lbl = labels.get(u);
                    counts.merge(lbl, 1, Integer::sum);
                }
                int bestLbl = labels.get(v);
                int bestCount = -1;
                for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
                    if (e.getValue() > bestCount
                            || (e.getValue() == bestCount && e.getKey() < bestLbl)) {
                        bestLbl = e.getKey();
                        bestCount = e.getValue();
                    }
                }
                if (bestLbl != labels.get(v)) { next.put(v, bestLbl); changed = true; }
            }
            labels = next;
            if (!changed) break;
        }
        return labels;
    }

    // -- Exports -----------------------------------------------------------

    public String toText(Forecast f) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Graph Adversary Forecast ===\n");
        sb.append(String.format("Live nodes: %d   Removed: %d   Ignored trace entries: %d%n",
                f.liveNodeCount, f.removedCount, f.ignoredTraceCount));
        sb.append("\nInferred attacker strategy:\n");
        sb.append("  Primary  : ").append(f.primary).append("\n");
        if (f.secondary != null) sb.append("  Secondary: ").append(f.secondary).append("\n");
        sb.append("\nStrategy ranking:\n");
        for (StrategyScore s : f.strategyRanking) {
            sb.append("  - ").append(String.format(Locale.ROOT, "%-22s %.3f", s.strategy, s.confidence)).append("\n");
        }
        sb.append("\nForecasted next targets (top ").append(f.nextTargets.size()).append("):\n");
        for (TargetForecast t : f.nextTargets) {
            sb.append(String.format(Locale.ROOT, "  - %-12s prob=%.3f   expected_impact=%.3f%n",
                    t.nodeId, t.probability, t.expectedImpact));
        }
        sb.append("\nRecommended defenses:\n");
        for (Defense d : f.defenses) {
            sb.append(String.format("  [%s] %-22s on %s -- %s%n",
                    d.priority, d.action, d.nodeId, d.detail));
            if (d.suggestedEdgeU != null && d.suggestedEdgeV != null) {
                sb.append("        suggested edge: (")
                        .append(d.suggestedEdgeU).append(", ").append(d.suggestedEdgeV).append(")\n");
            }
        }
        return sb.toString();
    }

    public String toMarkdown(Forecast f) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Graph Adversary Forecast\n\n");
        sb.append("- **Live nodes:** ").append(f.liveNodeCount).append("\n");
        sb.append("- **Removed:** ").append(f.removedCount).append("\n");
        sb.append("- **Ignored trace entries:** ").append(f.ignoredTraceCount).append("\n\n");
        sb.append("## Inferred attacker strategy\n\n");
        sb.append("- **Primary:** `").append(f.primary.strategy).append("` ")
                .append(String.format(Locale.ROOT, "(confidence %.3f)", f.primary.confidence)).append("\n");
        if (f.secondary != null) {
            sb.append("- **Secondary:** `").append(f.secondary.strategy).append("` ")
                    .append(String.format(Locale.ROOT, "(confidence %.3f)", f.secondary.confidence)).append("\n");
        }
        sb.append("\n### Strategy ranking\n\n");
        sb.append("| Strategy | Confidence |\n|---|---|\n");
        for (StrategyScore s : f.strategyRanking) {
            sb.append("| `").append(s.strategy).append("` | ")
                    .append(String.format(Locale.ROOT, "%.3f", s.confidence)).append(" |\n");
        }
        sb.append("\n## Forecasted next targets\n\n");
        sb.append("| Node | Probability | Expected impact |\n|---|---|---|\n");
        for (TargetForecast t : f.nextTargets) {
            sb.append("| `").append(t.nodeId).append("` | ")
                    .append(String.format(Locale.ROOT, "%.3f", t.probability)).append(" | ")
                    .append(String.format(Locale.ROOT, "%.3f", t.expectedImpact)).append(" |\n");
        }
        sb.append("\n## Recommended defenses\n\n");
        sb.append("| Priority | Action | Node | Detail | Suggested edge |\n|---|---|---|---|---|\n");
        for (Defense d : f.defenses) {
            String edge = (d.suggestedEdgeU != null && d.suggestedEdgeV != null)
                    ? "`(" + d.suggestedEdgeU + ", " + d.suggestedEdgeV + ")`" : "-";
            sb.append("| ").append(d.priority).append(" | `").append(d.action).append("` | `")
                    .append(d.nodeId).append("` | ").append(d.detail == null ? "" : d.detail.replace("|", "\\|"))
                    .append(" | ").append(edge).append(" |\n");
        }
        return sb.toString();
    }

    public String toJson(Forecast f) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"live_nodes\":").append(f.liveNodeCount).append(',');
        sb.append("\"removed\":").append(f.removedCount).append(',');
        sb.append("\"ignored_trace_entries\":").append(f.ignoredTraceCount).append(',');
        sb.append("\"primary\":").append(jsonStrategy(f.primary)).append(',');
        sb.append("\"secondary\":").append(f.secondary == null ? "null" : jsonStrategy(f.secondary)).append(',');
        sb.append("\"strategy_ranking\":[");
        for (int i = 0; i < f.strategyRanking.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(jsonStrategy(f.strategyRanking.get(i)));
        }
        sb.append("],");
        sb.append("\"next_targets\":[");
        for (int i = 0; i < f.nextTargets.size(); i++) {
            if (i > 0) sb.append(',');
            TargetForecast t = f.nextTargets.get(i);
            sb.append('{')
              .append("\"node\":").append(jsonString(t.nodeId)).append(',')
              .append("\"probability\":").append(num(t.probability)).append(',')
              .append("\"expected_impact\":").append(num(t.expectedImpact))
              .append('}');
        }
        sb.append("],");
        sb.append("\"defenses\":[");
        for (int i = 0; i < f.defenses.size(); i++) {
            if (i > 0) sb.append(',');
            Defense d = f.defenses.get(i);
            sb.append('{')
              .append("\"node\":").append(jsonString(d.nodeId)).append(',')
              .append("\"action\":\"").append(d.action).append("\",")
              .append("\"priority\":\"").append(d.priority).append("\",")
              .append("\"detail\":").append(jsonString(d.detail == null ? "" : d.detail)).append(',')
              .append("\"suggested_edge\":");
            if (d.suggestedEdgeU != null && d.suggestedEdgeV != null) {
                sb.append('[').append(jsonString(d.suggestedEdgeU)).append(',')
                  .append(jsonString(d.suggestedEdgeV)).append(']');
            } else {
                sb.append("null");
            }
            sb.append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String jsonStrategy(StrategyScore s) {
        return "{\"strategy\":\"" + s.strategy + "\",\"confidence\":" + num(s.confidence) + "}";
    }

    private static String num(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return "0";
        return String.format(Locale.ROOT, "%.6f", d);
    }

    private static String jsonString(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
