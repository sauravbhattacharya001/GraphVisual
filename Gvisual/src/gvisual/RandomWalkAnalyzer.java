package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Analyzes random walk behavior on graphs — a fundamental tool for
 * understanding information diffusion, network navigability, and
 * structural properties of social networks.
 *
 * <p>Random walks model how a "walker" traverses a graph by repeatedly
 * moving to a uniformly random neighbor. The statistics of these walks
 * reveal deep structural properties:</p>
 *
 * <ul>
 *   <li><strong>Hitting time</strong> — expected steps to reach node t from s</li>
 *   <li><strong>Commute distance</strong> — H(s,t) + H(t,s), a symmetric metric</li>
 *   <li><strong>Cover time</strong> — expected steps to visit every node</li>
 *   <li><strong>Mixing time</strong> — steps until distribution converges to stationary</li>
 *   <li><strong>Return time</strong> — expected steps to return to start</li>
 *   <li><strong>Stationary distribution</strong> — long-run visit probabilities</li>
 * </ul>
 *
 * @author zalenix
 */
public class RandomWalkAnalyzer {

    private final Random rng;
    private final int defaultSimulations;

    public RandomWalkAnalyzer() {
        this(10000, new Random());
    }

    public RandomWalkAnalyzer(int simulations, Random rng) {
        if (simulations < 1) throw new IllegalArgumentException("simulations must be >= 1");
        if (rng == null) throw new IllegalArgumentException("rng must not be null");
        this.defaultSimulations = simulations;
        this.rng = rng;
    }

    public <V, E> double hittingTime(Graph<V, E> graph, V source, V target) {
        validateGraph(graph);
        validateNode(graph, source, "source");
        validateNode(graph, target, "target");
        if (source.equals(target)) return 0.0;

        long totalSteps = 0;
        int reached = 0;
        int maxSteps = graph.getVertexCount() * graph.getVertexCount() * 10;

        for (int sim = 0; sim < defaultSimulations; sim++) {
            int steps = simulateWalkToTarget(graph, source, target, maxSteps);
            if (steps >= 0) { totalSteps += steps; reached++; }
        }
        return reached == 0 ? Double.POSITIVE_INFINITY : (double) totalSteps / reached;
    }

    /**
     * Computes hitting times from a source to ALL other vertices in a single
     * batch of simulated walks.
     *
     * <p><b>Performance:</b> The previous implementation called
     * {@link #hittingTime} independently for each target vertex, running
     * V &times; defaultSimulations walks total. This batched version runs
     * only defaultSimulations walks, each tracking first-visit times for
     * every unvisited vertex along the way. For a graph with V vertices
     * and 10,000 simulations, this reduces total walks from V &times; 10,000
     * to just 10,000 &mdash; a V&times; speedup.</p>
     *
     * <p><b>Array-indexed tracking:</b> Uses integer-indexed arrays instead
     * of {@code HashSet<V>} for visited tracking and {@code Map<V, Long>}
     * for accumulators. This eliminates per-simulation HashSet allocation
     * (previously O(V) per sim × 10,000 sims = significant GC pressure),
     * avoids autoboxing overhead, and provides cache-friendly sequential
     * access. The visited array is reset via a generation counter rather
     * than {@code Arrays.fill}, turning the O(V) per-sim reset into O(1).</p>
     */
    public <V, E> Map<V, Double> hittingTimesFrom(Graph<V, E> graph, V source) {
        validateGraph(graph);
        validateNode(graph, source, "source");

        IndexedGraph<V> ig = buildIndexedGraph(graph, graph.getVertices());
        int n = ig.size;
        int sourceIdx = ig.indexOf(source);

        // Array-based accumulators (no boxing, no Map lookups in hot loop)
        long[] totalSteps = new long[n];
        int[] reachedCount = new int[n];
        reachedCount[sourceIdx] = defaultSimulations;

        int maxSteps = n * n * 10;

        int[] visitedGen = new int[n];
        int currentGen = 0;

        for (int sim = 0; sim < defaultSimulations; sim++) {
            currentGen++;
            visitedGen[sourceIdx] = currentGen;
            int remaining = n - 1;

            int currentIdx = sourceIdx;
            for (int step = 1; step <= maxSteps && remaining > 0; step++) {
                int[] nbrs = ig.adj[currentIdx];
                if (nbrs.length == 0) break;
                currentIdx = nbrs[rng.nextInt(nbrs.length)];

                if (visitedGen[currentIdx] != currentGen) {
                    visitedGen[currentIdx] = currentGen;
                    remaining--;
                    totalSteps[currentIdx] += step;
                    reachedCount[currentIdx]++;
                }
            }
        }

        Map<V, Double> result = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            if (i == sourceIdx) {
                result.put(ig.vertex(i), 0.0);
            } else {
                result.put(ig.vertex(i), reachedCount[i] == 0
                        ? Double.POSITIVE_INFINITY
                        : (double) totalSteps[i] / reachedCount[i]);
            }
        }
        return result;
    }

    public <V, E> double commuteDistance(Graph<V, E> graph, V nodeA, V nodeB) {
        return hittingTime(graph, nodeA, nodeB) + hittingTime(graph, nodeB, nodeA);
    }

    /**
     * Estimates the cover time from a source vertex via Monte Carlo simulation.
     *
     * <p><b>Optimisation (array-indexed, generation-counter tracking):</b>
     * The previous implementation allocated a new {@code HashSet<V>} per
     * simulation to track visited vertices — 10,000 sims × O(V) allocation
     * and GC overhead.  This version uses integer-indexed arrays with a
     * generation counter (same technique as {@link #hittingTimesFrom}):
     * visited state is "reset" in O(1) by incrementing the generation,
     * and adjacency is pre-built as {@code int[][]} for cache-friendly,
     * boxing-free traversal.</p>
     */
    public <V, E> double coverTime(Graph<V, E> graph, V source) {
        validateGraph(graph);
        validateNode(graph, source, "source");

        Set<V> reachable = bfsReachable(graph, source);
        if (reachable.size() <= 1) return 0;

        IndexedGraph<V> ig = buildIndexedGraph(graph, reachable);
        int n = ig.size;
        int sourceIdx = ig.indexOf(source);
        int maxSteps = n * n * 20;

        int[] visitedGen = new int[n];
        int currentGen = 0;

        long totalSteps = 0;
        for (int sim = 0; sim < defaultSimulations; sim++) {
            currentGen++;
            visitedGen[sourceIdx] = currentGen;
            int remaining = n - 1;
            int currentIdx = sourceIdx;

            for (int step = 1; step <= maxSteps && remaining > 0; step++) {
                int[] nbrs = ig.adj[currentIdx];
                if (nbrs.length == 0) {
                    totalSteps += step;
                    remaining = 0;
                    break;
                }
                currentIdx = nbrs[rng.nextInt(nbrs.length)];
                if (visitedGen[currentIdx] != currentGen) {
                    visitedGen[currentIdx] = currentGen;
                    remaining--;
                }
                if (remaining == 0) {
                    totalSteps += step;
                }
            }
            if (remaining > 0) {
                totalSteps += maxSteps;
            }
        }
        return (double) totalSteps / defaultSimulations;
    }

    public <V, E> double returnTime(Graph<V, E> graph, V node) {
        validateGraph(graph);
        validateNode(graph, node, "node");
        if (graph.degree(node) == 0) return Double.POSITIVE_INFINITY;
        long totalSteps = 0;
        int maxSteps = graph.getVertexCount() * graph.getVertexCount() * 10;
        for (int sim = 0; sim < defaultSimulations; sim++) {
            totalSteps += simulateReturnWalk(graph, node, maxSteps);
        }
        return (double) totalSteps / defaultSimulations;
    }

    public <V, E> int mixingTime(Graph<V, E> graph, double epsilon) {
        validateGraph(graph);
        if (epsilon <= 0 || epsilon >= 1) throw new IllegalArgumentException("epsilon must be in (0,1)");
        int n = graph.getVertexCount();
        if (n == 0) return 0;

        List<V> nodeList = new ArrayList<>(graph.getVertices());
        Map<V, Integer> nodeIndex = new HashMap<>();
        for (int i = 0; i < nodeList.size(); i++) nodeIndex.put(nodeList.get(i), i);

        Map<V, Double> stationary = stationaryDistribution(graph);
        double[] stationaryArr = new double[n];
        for (int i = 0; i < n; i++) stationaryArr[i] = stationary.get(nodeList.get(i));

        double[][] P = buildTransitionMatrix(graph, nodeList, nodeIndex);
        int maxTime = n * n * 5;
        double[][] dist = new double[n][n];
        for (int i = 0; i < n; i++) dist[i][i] = 1.0;

        // Pre-allocate second buffer for double-buffered matrix multiply
        // (avoids O(V^2) allocation on every iteration)
        double[][] distB = new double[n][n];

        for (int t = 1; t <= maxTime; t++) {
            // Zero the target buffer
            for (int r = 0; r < n; r++)
                java.util.Arrays.fill(distB[r], 0.0);
            // Matrix multiply: distB = dist * P
            for (int start = 0; start < n; start++)
                for (int k = 0; k < n; k++) {
                    double d = dist[start][k];
                    if (d == 0.0) continue;  // skip zero contributions
                    for (int j = 0; j < n; j++)
                        distB[start][j] += d * P[k][j];
                }
            // Swap buffers (no allocation)
            double[][] tmp = dist;
            dist = distB;
            distB = tmp;

            double maxTV = 0;
            for (int start = 0; start < n; start++) {
                double tv = 0;
                for (int j = 0; j < n; j++) tv += Math.abs(dist[start][j] - stationaryArr[j]);
                maxTV = Math.max(maxTV, tv / 2.0);
            }
            if (maxTV <= epsilon) return t;
        }
        return maxTime;
    }

    public <V, E> Map<V, Double> stationaryDistribution(Graph<V, E> graph) {
        validateGraph(graph);
        Map<V, Double> dist = new LinkedHashMap<>();
        int totalDegree = 0;
        for (V v : graph.getVertices()) totalDegree += graph.degree(v);

        if (totalDegree == 0) {
            double uniform = 1.0 / graph.getVertexCount();
            for (V v : graph.getVertices()) dist.put(v, uniform);
            return dist;
        }
        for (V v : graph.getVertices()) dist.put(v, (double) graph.degree(v) / totalDegree);
        return dist;
    }

    public <V, E> List<V> walkTrace(Graph<V, E> graph, V source, int steps) {
        validateGraph(graph);
        validateNode(graph, source, "source");
        if (steps < 0) throw new IllegalArgumentException("steps must be >= 0");

        List<V> trace = new ArrayList<>(steps + 1);
        V current = source;
        trace.add(current);
        for (int i = 0; i < steps; i++) {
            Collection<V> neighbors = graph.getNeighbors(current);
            if (neighbors == null || neighbors.isEmpty()) break;
            current = pickRandom(neighbors);
            trace.add(current);
        }
        return trace;
    }

    public <V, E> Map<V, Double> visitFrequency(Graph<V, E> graph, V source, int steps) {
        List<V> trace = walkTrace(graph, source, steps);
        Map<V, Double> freq = new LinkedHashMap<>();
        for (V v : graph.getVertices()) freq.put(v, 0.0);
        for (V v : trace) freq.put(v, freq.get(v) + 1);
        double total = trace.size();
        for (V v : freq.keySet()) freq.put(v, freq.get(v) / total);
        return freq;
    }

    public <V, E> WalkSummary<V> summarize(Graph<V, E> graph) {
        validateGraph(graph);
        Map<V, Double> stationary = stationaryDistribution(graph);
        V mostVisited = null; double maxProb = -1;
        V leastVisited = null; double minProb = Double.MAX_VALUE;
        for (Map.Entry<V, Double> e : stationary.entrySet()) {
            if (e.getValue() > maxProb) { maxProb = e.getValue(); mostVisited = e.getKey(); }
            if (e.getValue() < minProb) { minProb = e.getValue(); leastVisited = e.getKey(); }
        }
        double ct = coverTime(graph, mostVisited);
        return new WalkSummary<>(graph.getVertexCount(), graph.getEdgeCount(), stationary,
            mostVisited, maxProb, leastVisited, minProb, ct, mostVisited);
    }

    public static class WalkSummary<V> {
        private final int nodeCount, edgeCount;
        private final Map<V, Double> stationaryDistribution;
        private final V mostVisitedNode, leastVisitedNode, coverTimeSource;
        private final double mostVisitedProb, leastVisitedProb, coverTimeFromBest;

        public WalkSummary(int nc, int ec, Map<V, Double> sd, V mv, double mvp,
                           V lv, double lvp, double ct, V cts) {
            this.nodeCount = nc; this.edgeCount = ec;
            this.stationaryDistribution = Collections.unmodifiableMap(sd);
            this.mostVisitedNode = mv; this.mostVisitedProb = mvp;
            this.leastVisitedNode = lv; this.leastVisitedProb = lvp;
            this.coverTimeFromBest = ct; this.coverTimeSource = cts;
        }
        public int getNodeCount() { return nodeCount; }
        public int getEdgeCount() { return edgeCount; }
        public Map<V, Double> getStationaryDistribution() { return stationaryDistribution; }
        public V getMostVisitedNode() { return mostVisitedNode; }
        public double getMostVisitedProb() { return mostVisitedProb; }
        public V getLeastVisitedNode() { return leastVisitedNode; }
        public double getLeastVisitedProb() { return leastVisitedProb; }
        public double getCoverTimeFromBest() { return coverTimeFromBest; }
        public V getCoverTimeSource() { return coverTimeSource; }

        @Override public String toString() {
            return String.format("WalkSummary{nodes=%d, edges=%d, mostVisited=%s(%.4f), " +
                "leastVisited=%s(%.4f), coverTime=%.1f from %s}",
                nodeCount, edgeCount, mostVisitedNode, mostVisitedProb,
                leastVisitedNode, leastVisitedProb, coverTimeFromBest, coverTimeSource);
        }
    }

    // ── Private Helpers ────────────────────────────────────────────────

    private <V, E> int simulateWalkToTarget(Graph<V, E> graph, V source, V target, int maxSteps) {
        V current = source;
        for (int step = 1; step <= maxSteps; step++) {
            Collection<V> nbrs = graph.getNeighbors(current);
            if (nbrs == null || nbrs.isEmpty()) return -1;
            // Skip to a random neighbor without copying the full collection.
            int idx = rng.nextInt(nbrs.size());
            V next = null;
            if (nbrs instanceof List) {
                next = ((List<V>) nbrs).get(idx);
            } else {
                for (V v : nbrs) { if (idx-- == 0) { next = v; break; } }
            }
            current = next;
            if (current.equals(target)) return step;
        }
        return -1;
    }

    /** BFS to find all vertices reachable from {@code source}. */
    private <V, E> Set<V> bfsReachable(Graph<V, E> graph, V source) {
        Set<V> reachable = new HashSet<>();
        Queue<V> q = new ArrayDeque<>();
        q.add(source);
        reachable.add(source);
        while (!q.isEmpty()) {
            V v = q.poll();
            for (V n : graph.getNeighbors(v)) {
                if (reachable.add(n)) q.add(n);
            }
        }
        return reachable;
    }

    private <V, E> long simulateReturnWalk(Graph<V, E> graph, V node, int maxSteps) {
        Collection<V> nbrs = graph.getNeighbors(node);
        if (nbrs == null || nbrs.isEmpty()) return maxSteps;
        V current = pickRandom(nbrs);
        for (int step = 2; step <= maxSteps; step++) {
            if (current.equals(node)) return step;
            nbrs = graph.getNeighbors(current);
            if (nbrs == null || nbrs.isEmpty()) return maxSteps;
            current = pickRandom(nbrs);
        }
        return maxSteps;
    }

    /** Pick a random element from a collection without copying it. */
    private <V> V pickRandom(Collection<V> coll) {
        int idx = rng.nextInt(coll.size());
        if (coll instanceof List) return ((List<V>) coll).get(idx);
        for (V v : coll) { if (idx-- == 0) return v; }
        throw new AssertionError("unreachable");
    }

    private <V, E> double[][] buildTransitionMatrix(Graph<V, E> graph, List<V> nodeList, Map<V, Integer> idx) {
        int n = nodeList.size();
        double[][] P = new double[n][n];
        for (int i = 0; i < n; i++) {
            V v = nodeList.get(i);
            Collection<V> neighbors = graph.getNeighbors(v);
            int deg = neighbors.size();
            if (deg == 0) { P[i][i] = 1.0; }
            else { for (V nb : neighbors) P[i][idx.get(nb)] += 1.0 / deg; }
        }
        return P;
    }

    private <V, E> void validateGraph(Graph<V, E> graph) {
        if (graph == null) throw new IllegalArgumentException("graph must not be null");
    }

    private <V, E> void validateNode(Graph<V, E> graph, V node, String name) {
        if (node == null) throw new IllegalArgumentException(name + " must not be null");
        if (!graph.containsVertex(node)) throw new IllegalArgumentException(name + " not found in graph: " + node);
    }

    // ── Indexed Graph Helper ───────────────────────────────────────────

    /**
     * Compact, array-indexed representation of a vertex subset and its
     * adjacency. Eliminates the duplicated index-building and int[][]
     * adjacency construction that was previously copy-pasted between
     * hittingTimesFrom and coverTime.
     */
    private static class IndexedGraph<V> {
        final int size;
        final int[][] adj;
        private final List<V> vertexList;
        private final Map<V, Integer> vertexIndex;

        IndexedGraph(int size, int[][] adj, List<V> vertexList,
                     Map<V, Integer> vertexIndex) {
            this.size = size;
            this.adj = adj;
            this.vertexList = vertexList;
            this.vertexIndex = vertexIndex;
        }

        int indexOf(V v) { return vertexIndex.get(v); }
        V vertex(int i) { return vertexList.get(i); }
    }

    /**
     * Builds an {@link IndexedGraph} from a subset of vertices in the
     * given graph. Adjacency arrays only reference vertices within the
     * subset, making it safe for reachability-restricted analysis.
     */
    private <V, E> IndexedGraph<V> buildIndexedGraph(Graph<V, E> graph,
                                                      Collection<V> vertices) {
        int n = vertices.size();
        List<V> vertexList = new ArrayList<>(vertices);
        Map<V, Integer> vertexIndex = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            vertexIndex.put(vertexList.get(i), i);
        }

        int[][] adj = new int[n][];
        for (int i = 0; i < n; i++) {
            V v = vertexList.get(i);
            Collection<V> nbrs = graph.getNeighbors(v);
            if (nbrs == null || nbrs.isEmpty()) {
                adj[i] = new int[0];
            } else {
                int[] neighbors = new int[nbrs.size()];
                int j = 0;
                for (V nb : nbrs) {
                    Integer idx = vertexIndex.get(nb);
                    if (idx != null) neighbors[j++] = idx;
                }
                adj[i] = (j == neighbors.length) ? neighbors
                        : java.util.Arrays.copyOf(neighbors, j);
            }
        }
        return new IndexedGraph<>(n, adj, vertexList, vertexIndex);
    }
}
