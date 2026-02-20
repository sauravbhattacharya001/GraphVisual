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

    private final Graph<String, edge> graph;
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
    public NodeCentralityAnalyzer(Graph<String, edge> graph) {
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
     */
    public void compute() {
        if (computed) return;

        computeDegreeCentrality();
        computeBetweennessCentrality();
        computeClosenessCentrality();
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

        List<CentralityResult> all = new ArrayList<CentralityResult>();
        for (String nodeId : graph.getVertices()) {
            all.add(getResult(nodeId));
        }

        final String m = metric.toLowerCase();
        Collections.sort(all, new Comparator<CentralityResult>() {
            public int compare(CentralityResult a, CentralityResult b) {
                double va, vb;
                if ("betweenness".equals(m)) {
                    va = a.getBetweennessCentrality();
                    vb = b.getBetweennessCentrality();
                } else if ("closeness".equals(m)) {
                    va = a.getClosenessCentrality();
                    vb = b.getClosenessCentrality();
                } else {
                    va = a.getDegreeCentrality();
                    vb = b.getDegreeCentrality();
                }
                return Double.compare(vb, va);
            }
        });

        if (n <= 0) return new ArrayList<CentralityResult>();
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
     * @return one of: "Trivial" (≤1 node), "Disconnected" (isolated nodes exist),
     *         "Hub-and-Spoke" (one node dominates), "Distributed" (even degree distribution),
     *         "Hierarchical" (moderate degree variance)
     */
    public String classifyTopology() {
        if (!computed) compute();

        int n = graph.getVertexCount();
        if (n <= 1) return "Trivial";
        if (graph.getEdgeCount() == 0) return "Disconnected";

        // Check for isolated nodes
        int isolated = 0;
        int maxDeg = 0;
        double sumDeg = 0;
        for (String node : graph.getVertices()) {
            int deg = graph.degree(node);
            if (deg == 0) isolated++;
            if (deg > maxDeg) maxDeg = deg;
            sumDeg += deg;
        }

        if (isolated > n * 0.5) return "Disconnected";

        double avgDeg = sumDeg / n;
        if (avgDeg == 0) return "Disconnected";

        // Check hub-and-spoke: max degree much higher than average
        double hubRatio = maxDeg / avgDeg;
        if (hubRatio > 4.0 && maxDeg > n * 0.3) return "Hub-and-Spoke";

        // Check for distributed (coefficient of variation of degree < 0.5)
        double sumSqDiff = 0;
        for (String node : graph.getVertices()) {
            double diff = graph.degree(node) - avgDeg;
            sumSqDiff += diff * diff;
        }
        double stdDev = Math.sqrt(sumSqDiff / n);
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
     * Betweenness centrality using Brandes' algorithm (2001).
     * Complexity: O(V * E) for unweighted graphs.
     *
     * <p>For each source vertex s, performs a BFS to compute shortest paths,
     * then accumulates dependencies on the back-sweep. The result is normalized
     * by 2/((V-1)(V-2)) for undirected graphs to give values in [0, 1].</p>
     */
    private void computeBetweennessCentrality() {
        // Initialize all betweenness to 0
        for (String node : graph.getVertices()) {
            betweennessCentrality.put(node, 0.0);
        }

        int n = graph.getVertexCount();
        if (n <= 2) return;

        for (String s : graph.getVertices()) {
            // Stacks, predecessors, sigma, distance
            Deque<String> stack = new ArrayDeque<String>();
            Map<String, List<String>> predecessors = new HashMap<String, List<String>>();
            Map<String, Integer> sigma = new HashMap<String, Integer>();
            Map<String, Integer> dist = new HashMap<String, Integer>();

            for (String t : graph.getVertices()) {
                predecessors.put(t, new ArrayList<String>());
                sigma.put(t, 0);
                dist.put(t, -1);
            }

            sigma.put(s, 1);
            dist.put(s, 0);

            // BFS from s
            Queue<String> queue = new LinkedList<String>();
            queue.add(s);

            while (!queue.isEmpty()) {
                String v = queue.poll();
                stack.push(v);

                for (edge e : graph.getIncidentEdges(v)) {
                    String w = getOtherEnd(e, v);
                    if (w == null) continue;

                    // First visit to w
                    if (dist.get(w) < 0) {
                        queue.add(w);
                        dist.put(w, dist.get(v) + 1);
                    }

                    // Shortest path to w via v?
                    if (dist.get(w) == dist.get(v) + 1) {
                        sigma.put(w, sigma.get(w) + sigma.get(v));
                        predecessors.get(w).add(v);
                    }
                }
            }

            // Back-propagation of dependencies
            Map<String, Double> delta = new HashMap<String, Double>();
            for (String t : graph.getVertices()) {
                delta.put(t, 0.0);
            }

            while (!stack.isEmpty()) {
                String w = stack.pop();
                for (String v : predecessors.get(w)) {
                    double contribution = ((double) sigma.get(v) / sigma.get(w)) * (1.0 + delta.get(w));
                    delta.put(v, delta.get(v) + contribution);
                }
                if (!w.equals(s)) {
                    betweennessCentrality.put(w, betweennessCentrality.get(w) + delta.get(w));
                }
            }
        }

        // Normalize for undirected graph: divide by 2 (each pair counted twice)
        // and by (n-1)(n-2) to normalize to [0, 1]
        double normFactor = (n - 1.0) * (n - 2.0);
        if (normFactor > 0) {
            for (String node : graph.getVertices()) {
                double raw = betweennessCentrality.get(node);
                betweennessCentrality.put(node, raw / normFactor);
            }
        }
    }

    /**
     * Closeness centrality: (reachable-1) / sum_of_distances.
     * Uses BFS from each node to find shortest path distances.
     *
     * <p>For disconnected graphs, uses the Wasserman-Faust normalization:
     * closeness = (reachable - 1)² / ((V - 1) * sumDist)
     * which gives 0 for isolated nodes and properly scales for partial connectivity.</p>
     */
    private void computeClosenessCentrality() {
        int n = graph.getVertexCount();

        for (String s : graph.getVertices()) {
            if (n <= 1) {
                closenessCentrality.put(s, 0.0);
                continue;
            }

            // BFS to compute distances from s
            Map<String, Integer> dist = new HashMap<String, Integer>();
            Queue<String> queue = new LinkedList<String>();
            dist.put(s, 0);
            queue.add(s);

            int sumDist = 0;
            int reachable = 0;

            while (!queue.isEmpty()) {
                String current = queue.poll();
                int currentDist = dist.get(current);

                for (edge e : graph.getIncidentEdges(current)) {
                    String neighbor = getOtherEnd(e, current);
                    if (neighbor != null && !dist.containsKey(neighbor)) {
                        int nd = currentDist + 1;
                        dist.put(neighbor, nd);
                        sumDist += nd;
                        reachable++;
                        queue.add(neighbor);
                    }
                }
            }

            if (reachable == 0 || sumDist == 0) {
                closenessCentrality.put(s, 0.0);
            } else {
                // Wasserman-Faust normalization for potentially disconnected graphs
                double cc = ((double) reachable * reachable) / ((n - 1.0) * sumDist);
                closenessCentrality.put(s, cc);
            }
        }
    }

    private String getOtherEnd(edge e, String current) {
        String v1 = e.getVertex1();
        String v2 = e.getVertex2();
        if (current.equals(v1)) return v2;
        if (current.equals(v2)) return v1;
        return null;
    }
}
