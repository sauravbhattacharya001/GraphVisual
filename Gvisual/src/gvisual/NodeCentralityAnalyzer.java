package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Computes centrality metrics for nodes in a JUNG graph:
 * <ul>
 *   <li><b>Degree centrality</b> — normalized node degree (connections / max possible)</li>
 *   <li><b>Betweenness centrality</b> — fraction of all-pairs shortest paths passing through a node
 *       (Brandes' algorithm, O(V*E))</li>
 *   <li><b>Closeness centrality</b> — inverse of average shortest-path distance to all reachable nodes</li>
 * </ul>
 *
 * <p>Results are returned as sorted rankings with CentralityResult objects that
 * carry all three metrics per node, enabling comparative analysis of node
 * importance in the social network.</p>
 *
 * @author zalenix
 */
public class NodeCentralityAnalyzer {

    private final Graph<String, Edge> graph;
    private Map<String, Double> degreeCentrality;
    private Map<String, Double> betweennessCentrality;
    private Map<String, Double> closenessCentrality;
    private boolean computed;

    /**
     * Creates a new NodeCentralityAnalyzer for the given graph.
     *
     * @param graph the JUNG graph to analyze
     * @throws IllegalArgumentException if graph is null
     */
    public NodeCentralityAnalyzer(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.degreeCentrality = new LinkedHashMap<String, Double>();
        this.betweennessCentrality = new LinkedHashMap<String, Double>();
        this.closenessCentrality = new LinkedHashMap<String, Double>();
        this.computed = false;
    }

    /**
     * Represents centrality metrics for a single node.
     */
    public static class CentralityResult implements Comparable<CentralityResult> {
        private final String nodeId;
        private final double degreeCentrality;
        private final double betweennessCentrality;
        private final double closenessCentrality;
        private final int degree;

        public CentralityResult(String nodeId, int degree,
                                double degreeCentrality,
                                double betweennessCentrality,
                                double closenessCentrality) {
            this.nodeId = nodeId;
            this.degree = degree;
            this.degreeCentrality = degreeCentrality;
            this.betweennessCentrality = betweennessCentrality;
            this.closenessCentrality = closenessCentrality;
        }

        /** Vertex ID. */
        public String getNodeId() { return nodeId; }

        /** Raw degree (number of connections). */
        public int getDegree() { return degree; }

        /** Degree centrality: degree / (V-1). Range [0, 1]. */
        public double getDegreeCentrality() { return degreeCentrality; }

        /** Betweenness centrality: fraction of shortest paths through this node. Range [0, 1]. */
        public double getBetweennessCentrality() { return betweennessCentrality; }

        /** Closeness centrality: 1 / avg distance to reachable nodes. Range [0, 1] for connected graphs. */
        public double getClosenessCentrality() { return closenessCentrality; }

        /**
         * Combined score: weighted average of all three centralities.
         * Degree=0.3, Betweenness=0.4, Closeness=0.3.
         * Betweenness is weighted higher as it captures structural importance.
         */
        public double getCombinedScore() {
            return 0.3 * degreeCentrality + 0.4 * betweennessCentrality + 0.3 * closenessCentrality;
        }

        /** Sort by combined score, descending. */
        public int compareTo(CentralityResult other) {
            return Double.compare(other.getCombinedScore(), this.getCombinedScore());
        }

        @Override
        public String toString() {
            return String.format("Node %s: degree=%.3f, betweenness=%.3f, closeness=%.3f, combined=%.3f",
                    nodeId, degreeCentrality, betweennessCentrality, closenessCentrality, getCombinedScore());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CentralityResult that = (CentralityResult) o;
            return nodeId.equals(that.nodeId);
        }

        @Override
        public int hashCode() {
            return nodeId.hashCode();
        }
    }

    /**
     * Computes all centrality metrics. Must be called before querying results.
     * Automatically skips recomputation if already computed.
     *
     * <p>Betweenness and closeness are computed in a single fused BFS pass
     * per source vertex, halving the O(V·E) traversal cost compared to
     * running two independent BFS sweeps.</p>
     */
    public void compute() {
        if (computed) return;

        computeDegreeCentrality();
        computeBetweennessAndCloseness();
        computed = true;
    }

    /**
     * Returns whether metrics have been computed.
     */
    public boolean isComputed() {
        return computed;
    }

    /**
     * Returns the centrality result for a specific node.
     *
     * @param nodeId the vertex ID
     * @return the CentralityResult, or null if node not in graph
     */
    public CentralityResult getResult(String nodeId) {
        if (!computed) compute();
        if (!graph.containsVertex(nodeId)) return null;

        Double dc = degreeCentrality.get(nodeId);
        Double bc = betweennessCentrality.get(nodeId);
        Double cc = closenessCentrality.get(nodeId);

        return new CentralityResult(
                nodeId,
                graph.degree(nodeId),
                dc != null ? dc : 0.0,
                bc != null ? bc : 0.0,
                cc != null ? cc : 0.0
        );
    }

    /**
     * Returns all node centrality results, sorted by combined score (highest first).
     *
     * @return sorted list of CentralityResult
     */
    public List<CentralityResult> getRankedResults() {
        if (!computed) compute();

        List<CentralityResult> results = new ArrayList<CentralityResult>();
        for (String nodeId : graph.getVertices()) {
            results.add(getResult(nodeId));
        }
        Collections.sort(results);
        return results;
    }

    /**
     * Returns the top-N most central nodes by combined score.
     *
     * @param n number of top nodes to return
     * @return sorted list of top CentralityResult entries
     */
    public List<CentralityResult> getTopNodes(int n) {
        List<CentralityResult> all = getRankedResults();
        if (n <= 0) return new ArrayList<CentralityResult>();
        return all.subList(0, Math.min(n, all.size()));
    }

    /**
     * Returns the top-N nodes by a specific centrality metric.
     *
     * @param n      number of top nodes
     * @param metric "degree", "betweenness", or "closeness"
     * @return sorted list
     */
    public List<CentralityResult> getTopByMetric(int n, String metric) {
        if (!computed) compute();
        if (n <= 0) return new ArrayList<CentralityResult>();

        List<CentralityResult> all = getRankedResults();

        final String m = metric.toLowerCase();
        Comparator<CentralityResult> cmp;
        if ("betweenness".equals(m)) {
            cmp = (a, b) -> Double.compare(b.getBetweennessCentrality(), a.getBetweennessCentrality());
        } else if ("closeness".equals(m)) {
            cmp = (a, b) -> Double.compare(b.getClosenessCentrality(), a.getClosenessCentrality());
        } else {
            cmp = (a, b) -> Double.compare(b.getDegreeCentrality(), a.getDegreeCentrality());
        }
        Collections.sort(all, cmp);

        return all.subList(0, Math.min(n, all.size()));
    }

    /**
     * Returns the degree centrality map (node → centrality value).
     */
    public Map<String, Double> getDegreeCentralityMap() {
        if (!computed) compute();
        return Collections.unmodifiableMap(degreeCentrality);
    }

    /**
     * Returns the betweenness centrality map (node → centrality value).
     */
    public Map<String, Double> getBetweennessCentralityMap() {
        if (!computed) compute();
        return Collections.unmodifiableMap(betweennessCentrality);
    }

    /**
     * Returns the closeness centrality map (node → centrality value).
     */
    public Map<String, Double> getClosenessCentralityMap() {
        if (!computed) compute();
        return Collections.unmodifiableMap(closenessCentrality);
    }

    /**
     * Returns a summary of centrality statistics for the graph.
     *
     * @return map with keys: avgDegreeC, avgBetweennessC, avgClosenessC,
     *         maxDegreeC, maxBetweennessC, maxClosenessC, and their node IDs
     */
    public Map<String, Object> getSummary() {
        if (!computed) compute();

        Map<String, Object> summary = new LinkedHashMap<String, Object>();

        if (graph.getVertexCount() == 0) {
            summary.put("nodeCount", 0);
            summary.put("avgDegreeCentrality", 0.0);
            summary.put("avgBetweennessCentrality", 0.0);
            summary.put("avgClosenessCentrality", 0.0);
            summary.put("maxDegreeCentrality", 0.0);
            summary.put("maxBetweennessCentrality", 0.0);
            summary.put("maxClosenessCentrality", 0.0);
            summary.put("maxDegreeCentralityNode", "none");
            summary.put("maxBetweennessCentralityNode", "none");
            summary.put("maxClosenessCentralityNode", "none");
            return summary;
        }

        double sumDC = 0, sumBC = 0, sumCC = 0;
        double maxDC = -1, maxBC = -1, maxCC = -1;
        String maxDCNode = "", maxBCNode = "", maxCCNode = "";

        for (String node : graph.getVertices()) {
            double dc = degreeCentrality.containsKey(node) ? degreeCentrality.get(node) : 0.0;
            double bc = betweennessCentrality.containsKey(node) ? betweennessCentrality.get(node) : 0.0;
            double cc = closenessCentrality.containsKey(node) ? closenessCentrality.get(node) : 0.0;

            sumDC += dc;
            sumBC += bc;
            sumCC += cc;

            if (dc > maxDC) { maxDC = dc; maxDCNode = node; }
            if (bc > maxBC) { maxBC = bc; maxBCNode = node; }
            if (cc > maxCC) { maxCC = cc; maxCCNode = node; }
        }

        int n = graph.getVertexCount();
        summary.put("nodeCount", n);
        summary.put("avgDegreeCentrality", sumDC / n);
        summary.put("avgBetweennessCentrality", sumBC / n);
        summary.put("avgClosenessCentrality", sumCC / n);
        summary.put("maxDegreeCentrality", maxDC);
        summary.put("maxBetweennessCentrality", maxBC);
        summary.put("maxClosenessCentrality", maxCC);
        summary.put("maxDegreeCentralityNode", maxDCNode);
        summary.put("maxBetweennessCentralityNode", maxBCNode);
        summary.put("maxClosenessCentralityNode", maxCCNode);

        return summary;
    }

    /**
     * Classifies the network topology based on degree distribution characteristics.
     *
     * <p>Single-pass implementation: computes isolated count, max degree, sum,
     * and sum-of-squares in one traversal instead of two separate loops.
     * Uses the already-computed degreeCentrality map to derive raw degrees
     * (degree = centrality × (V−1)) rather than re-querying graph.degree()
     * for each vertex.</p>
     *
     * @return one of: "Trivial" (≤1 node), "Disconnected" (isolated nodes exist),
     *         "Hub-and-Spoke" (one node dominates), "Distributed" (even degree distribution),
     *         "Hierarchical" (moderate degree variance)
     */
    public String classifyTopology() {
        if (!computed) compute();

        int n = graph.getVertexCount();
        if (n <= 1) return "Trivial";
        if (graph.getEdgeCount() == 0) return "Disconnected";

        // Collect raw degrees from degreeCentrality map (avoids n graph.degree() calls)
        int[] degrees = new int[n];
        int idx = 0;
        int isolated = 0;
        int maxDeg = 0;
        long sumDeg = 0;
        long sumSqDeg = 0;

        for (Double dc : degreeCentrality.values()) {
            int deg = (int) Math.round(dc * (n - 1));
            degrees[idx++] = deg;
            if (deg == 0) isolated++;
            if (deg > maxDeg) maxDeg = deg;
            sumDeg += deg;
            sumSqDeg += (long) deg * deg;
        }

        if (isolated > n * 0.5) return "Disconnected";

        double avgDeg = (double) sumDeg / n;
        if (avgDeg == 0) return "Disconnected";

        double hubRatio = maxDeg / avgDeg;
        if (hubRatio > 4.0 && maxDeg > n * 0.3) return "Hub-and-Spoke";

        // Variance from E[X²] - E[X]² (single-pass formula, no second iteration)
        double variance = (double) sumSqDeg / n - avgDeg * avgDeg;
        double stdDev = Math.sqrt(Math.max(0.0, variance));
        double cv = stdDev / avgDeg;

        if (cv < 0.5) return "Distributed";
        return "Hierarchical";
    }

    // --- Private computation methods ---

    /**
     * Degree centrality: degree(v) / (V - 1).
     * For undirected graphs, this normalizes degree to [0, 1].
     */
    private void computeDegreeCentrality() {
        int n = graph.getVertexCount();
        for (String node : graph.getVertices()) {
            if (n <= 1) {
                degreeCentrality.put(node, 0.0);
            } else {
                degreeCentrality.put(node, (double) graph.degree(node) / (n - 1));
            }
        }
    }

    /**
     * Betweenness + closeness centrality in a single fused BFS pass per source.
     *
     * <p>Uses array-based storage indexed by vertex ordinal instead of
     * per-source HashMaps.  This eliminates O(V) HashMap.put() calls per
     * source (V sources × V entries each = V² total), avoids Integer/Double
     * boxing, and provides better cache locality for the inner BFS loop.
     * Predecessor lists are still object-based but are allocated only when
     * an Edge is discovered (lazy), not pre-allocated for every vertex.</p>
     *
     * <p>Betweenness: Brandes (2001), O(V·E) for unweighted graphs, normalized
     * by (V-1)(V-2) for undirected. Closeness: Wasserman-Faust normalization
     * for potentially disconnected graphs.</p>
     */
    private void computeBetweennessAndCloseness() {
        int n = graph.getVertexCount();

        // Initialize result maps
        for (String node : graph.getVertices()) {
            betweennessCentrality.put(node, 0.0);
            closenessCentrality.put(node, 0.0);
        }
        if (n <= 1) return;

        // Build stable vertex-to-index mapping for array-based BFS
        List<String> vertexList = new ArrayList<String>(graph.getVertices());
        Collections.sort(vertexList);
        Map<String, Integer> idxMap = new HashMap<String, Integer>(n * 2);
        for (int i = 0; i < n; i++) {
            idxMap.put(vertexList.get(i), i);
        }

        // Pre-build adjacency as int[][] for cache-friendly, boxing-free traversal
        int[][] adj = new int[n][];
        {
            @SuppressWarnings("unchecked")
            List<Integer>[] adjTmp = new List[n];
            for (int i = 0; i < n; i++) {
                adjTmp[i] = new ArrayList<Integer>();
            }
            for (Edge e : graph.getEdges()) {
                Integer ui = idxMap.get(e.getVertex1());
                Integer vi = idxMap.get(e.getVertex2());
                if (ui != null && vi != null && !ui.equals(vi)) {
                    adjTmp[ui].add(vi);
                    adjTmp[vi].add(ui);
                }
            }
            for (int i = 0; i < n; i++) {
                List<Integer> neighbors = adjTmp[i];
                adj[i] = new int[neighbors.size()];
                for (int j = 0; j < neighbors.size(); j++) {
                    adj[i][j] = neighbors.get(j);
                }
            }
        }

        // Accumulator for betweenness (indexed, avoids per-source map lookups)
        double[] bcAccum = new double[n];

        // Reusable per-source arrays (allocated once, reset each iteration)
        int[] dist = new int[n];
        int[] sigma = new int[n];
        double[] delta = new double[n];
        int[] bfsOrder = new int[n];  // replaces Deque<String> stack

        @SuppressWarnings("unchecked")
        List<Integer>[] preds = new List[n];

        for (int s = 0; s < n; s++) {
            // Reset arrays for this source (Arrays.fill is memset-fast)
            Arrays.fill(dist, -1);
            Arrays.fill(sigma, 0);
            Arrays.fill(delta, 0.0);
            for (int i = 0; i < n; i++) {
                preds[i] = null;  // lazy allocation
            }

            dist[s] = 0;
            sigma[s] = 1;
            int bfsHead = 0, bfsTail = 0;
            bfsOrder[bfsTail++] = s;

            // Closeness accumulators
            int sumDist = 0;
            int reachable = 0;

            // BFS using array-based queue (bfsOrder doubles as stack in reverse)
            int qHead = 0;
            int[] bfsQueue = bfsOrder;  // reuse same array
            // Actually we need a separate queue since bfsOrder is our stack
            // But we can use bfsOrder as both: BFS fills left-to-right,
            // back-propagation reads right-to-left (same as stack pop order)
            int orderIdx = 0;

            // Simple array-based BFS queue
            int[] queue = new int[n];
            int qStart = 0, qEnd = 0;
            queue[qEnd++] = s;

            while (qStart < qEnd) {
                int v = queue[qStart++];
                bfsOrder[orderIdx++] = v;

                for (int w : adj[v]) {
                    if (dist[w] < 0) {
                        dist[w] = dist[v] + 1;
                        queue[qEnd++] = w;
                        sumDist += dist[w];
                        reachable++;
                    }
                    if (dist[w] == dist[v] + 1) {
                        sigma[w] += sigma[v];
                        if (preds[w] == null) {
                            preds[w] = new ArrayList<Integer>(4);
                        }
                        preds[w].add(v);
                    }
                }
            }

            // Closeness for source s
            if (reachable > 0 && sumDist > 0) {
                double cc = ((double) reachable * reachable) / ((n - 1.0) * sumDist);
                closenessCentrality.put(vertexList.get(s), cc);
            }

            // Betweenness back-propagation (traverse BFS order in reverse)
            if (n > 2) {
                for (int idx = orderIdx - 1; idx >= 1; idx--) {
                    int w = bfsOrder[idx];
                    if (preds[w] != null) {
                        for (int v : preds[w]) {
                            double contribution = ((double) sigma[v] / sigma[w])
                                    * (1.0 + delta[w]);
                            delta[v] += contribution;
                        }
                    }
                    bcAccum[w] += delta[w];
                }
            }
        }

        // Normalize betweenness for undirected graph: divide by (n-1)(n-2)
        double normFactor = (n - 1.0) * (n - 2.0);
        if (normFactor > 0) {
            for (int i = 0; i < n; i++) {
                betweennessCentrality.put(vertexList.get(i), bcAccum[i] / normFactor);
            }
        } else {
            for (int i = 0; i < n; i++) {
                betweennessCentrality.put(vertexList.get(i), bcAccum[i]);
            }
        }
    }
}
