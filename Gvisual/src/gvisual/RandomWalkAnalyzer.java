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

    public <V, E> Map<V, Double> hittingTimesFrom(Graph<V, E> graph, V source) {
        validateGraph(graph);
        validateNode(graph, source, "source");
        Map<V, Double> result = new LinkedHashMap<>();
        for (V target : graph.getVertices()) {
            result.put(target, hittingTime(graph, source, target));
        }
        return result;
    }

    public <V, E> double commuteDistance(Graph<V, E> graph, V nodeA, V nodeB) {
        return hittingTime(graph, nodeA, nodeB) + hittingTime(graph, nodeB, nodeA);
    }

    public <V, E> double coverTime(Graph<V, E> graph, V source) {
        validateGraph(graph);
        validateNode(graph, source, "source");
        // Precompute reachable set once (BFS) — avoids redundant O(V+E)
        // traversal on every simulation.
        Set<V> reachable = bfsReachable(graph, source);
        if (reachable.size() <= 1) return 0;
        // Cache neighbor lists to avoid allocating new ArrayLists per step.
        Map<V, List<V>> neighborCache = buildNeighborCache(graph, reachable);
        long totalSteps = 0;
        int maxSteps = graph.getVertexCount() * graph.getVertexCount() * 20;
        for (int sim = 0; sim < defaultSimulations; sim++) {
            totalSteps += simulateCoverWalk(source, reachable, neighborCache, maxSteps);
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

        for (int t = 1; t <= maxTime; t++) {
            double[][] newDist = new double[n][n];
            for (int start = 0; start < n; start++)
                for (int j = 0; j < n; j++)
                    for (int k = 0; k < n; k++)
                        newDist[start][j] += dist[start][k] * P[k][j];
            dist = newDist;

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

    private <V> long simulateCoverWalk(V source, Set<V> reachable,
                                      Map<V, List<V>> neighborCache, int maxSteps) {
        Set<V> visited = new HashSet<>();
        V current = source;
        visited.add(current);
        int target = reachable.size();
        for (int step = 1; step <= maxSteps; step++) {
            List<V> nb = neighborCache.get(current);
            if (nb == null || nb.isEmpty()) return step;
            current = nb.get(rng.nextInt(nb.size()));
            visited.add(current);
            if (visited.size() >= target) return step;
        }
        return maxSteps;
    }

    /** BFS to find all vertices reachable from {@code source}. */
    private <V, E> Set<V> bfsReachable(Graph<V, E> graph, V source) {
        Set<V> reachable = new HashSet<>();
        Queue<V> q = new LinkedList<>();
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

    /** Cache neighbor lists for a set of vertices — avoids per-step allocation. */
    private <V, E> Map<V, List<V>> buildNeighborCache(Graph<V, E> graph, Set<V> vertices) {
        Map<V, List<V>> cache = new HashMap<>();
        for (V v : vertices) {
            Collection<V> neighbors = graph.getNeighbors(v);
            cache.put(v, neighbors != null ? new ArrayList<>(neighbors) : Collections.<V>emptyList());
        }
        return cache;
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
}
