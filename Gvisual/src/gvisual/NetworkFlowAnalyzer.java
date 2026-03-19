package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Network Flow Analyzer — computes maximum flow between a source and sink
 * vertex using the <b>Edmonds–Karp</b> algorithm (BFS-based Ford–Fulkerson).
 *
 * <h3>Algorithm</h3>
 * <p>Treats every undirected edge as two directed arcs with capacity equal to
 * the edge weight (default 1.0 for unweighted edges). Repeatedly finds the
 * shortest augmenting path via BFS and pushes as much flow as possible along
 * it, until no more augmenting paths exist.</p>
 *
 * <h3>Complexity</h3>
 * <ul>
 *   <li><b>Time:</b> O(V · E²) — polynomial, independent of capacity values.</li>
 *   <li><b>Space:</b> O(V + E) for residual graph and BFS structures.</li>
 * </ul>
 *
 * <h3>Analytics</h3>
 * <ul>
 *   <li><b>Max flow value</b> — the maximum amount of flow from source to sink.</li>
 *   <li><b>Flow assignment</b> — per-edge flow values.</li>
 *   <li><b>Min cut</b> — the set of edges forming the minimum cut (max-flow
 *       min-cut theorem).</li>
 *   <li><b>Bottleneck edges</b> — fully saturated edges on augmenting paths.</li>
 *   <li><b>Flow paths</b> — decomposition of the max flow into individual
 *       source-to-sink paths.</li>
 *   <li><b>Utilisation</b> — percentage of total network capacity used.</li>
 * </ul>
 *
 * <p>Network flow is fundamental in transportation networks, communication
 * networks, bipartite matching, and supply chain optimisation.</p>
 *
 * @author zalenix
 */
public class NetworkFlowAnalyzer {

    private final Graph<String, edge> graph;

    // Residual capacities: ArcKey -> remaining capacity
    private Map<ArcKey, Double> residualCapacity;
    // Flow values: ArcKey -> flow
    private Map<ArcKey, Double> flow;
    // Adjacency list for residual graph
    private Map<String, Set<String>> residualAdj;
    // Original capacities
    private Map<ArcKey, Double> capacity;
    // Edge lookup: ArcKey -> original edge (null for reverse arcs)
    private Map<ArcKey, edge> edgeLookup;

    private String source;
    private String sink;
    private double maxFlowValue;
    private boolean computed;

    /**
     * Creates a new NetworkFlowAnalyzer for the given graph.
     *
     * @param graph the JUNG graph (treated as undirected; each edge becomes
     *              two directed arcs)
     * @throws IllegalArgumentException if graph is null
     */
    public NetworkFlowAnalyzer(Graph<String, edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.computed = false;
    }

    // ── ArcKey ─────────────────────────────────────────────────────

    /**
     * Immutable key for a directed arc between two vertices.
     * Replaces the previous {@code List<String>} map-key pattern with a
     * type-safe, allocation-light alternative.
     */
    static final class ArcKey {
        final String from;
        final String to;

        ArcKey(String from, String to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ArcKey)) return false;
            ArcKey that = (ArcKey) o;
            return from.equals(that.from) && to.equals(that.to);
        }

        @Override
        public int hashCode() {
            return 31 * from.hashCode() + to.hashCode();
        }

        @Override
        public String toString() {
            return from + "->" + to;
        }
    }

    // ── Core computation ───────────────────────────────────────────

    /**
     * Computes the maximum flow from source to sink using Edmonds–Karp.
     *
     * @param source source vertex ID
     * @param sink   sink vertex ID
     * @return the maximum flow value
     * @throws IllegalArgumentException if source or sink is null, not in graph,
     *                                  or identical
     */
    public double compute(String source, String sink) {
        validateVertex(source, "Source");
        validateVertex(sink, "Sink");
        if (source.equals(sink)) {
            throw new IllegalArgumentException(
                    "Source and sink must be different vertices");
        }

        this.source = source;
        this.sink = sink;

        buildResidualGraph();

        maxFlowValue = 0;

        // Edmonds–Karp: BFS for shortest augmenting path
        while (true) {
            Map<String, String> parent = new LinkedHashMap<String, String>();
            Map<String, ArcKey> parentArcKey = new LinkedHashMap<String, ArcKey>();
            double pathFlow = bfsAugmentingPath(parent, parentArcKey);

            if (pathFlow <= 0) break;

            // Update residual capacities along the path
            String v = sink;
            while (!v.equals(source)) {
                String u = parent.get(v);
                ArcKey fwd = directedKey(u, v);
                ArcKey rev = directedKey(v, u);

                residualCapacity.put(fwd,
                        residualCapacity.get(fwd) - pathFlow);
                residualCapacity.put(rev,
                        residualCapacity.getOrDefault(rev, 0.0) + pathFlow);

                flow.put(fwd, flow.getOrDefault(fwd, 0.0) + pathFlow);
                flow.put(rev, flow.getOrDefault(rev, 0.0) - pathFlow);

                v = u;
            }

            maxFlowValue += pathFlow;
        }

        computed = true;
        return maxFlowValue;
    }

    // ── Query methods ──────────────────────────────────────────────

    /**
     * Returns the computed maximum flow value.
     *
     * @return max flow from source to sink
     */
    public double getMaxFlow() {
        ensureComputed();
        return maxFlowValue;
    }

    /**
     * Returns the source vertex.
     *
     * @return source vertex ID
     */
    public String getSource() {
        ensureComputed();
        return source;
    }

    /**
     * Returns the sink vertex.
     *
     * @return sink vertex ID
     */
    public String getSink() {
        ensureComputed();
        return sink;
    }

    /**
     * Returns the flow on each original edge.
     * The map key format is "v1->v2" for each edge direction.
     * Only edges with positive flow are included.
     *
     * @return unmodifiable map of edge direction to flow value
     */
    public Map<String, Double> getEdgeFlows() {
        ensureComputed();
        Map<String, Double> result = new LinkedHashMap<String, Double>();
        for (edge e : graph.getEdges()) {
            ArcKey fwd = directedKey(e.getVertex1(), e.getVertex2());
            ArcKey rev = directedKey(e.getVertex2(), e.getVertex1());

            double fwdFlow = flow.getOrDefault(fwd, 0.0);
            double revFlow = flow.getOrDefault(rev, 0.0);

            // Net flow direction
            if (fwdFlow > 1e-9) {
                result.put(fwd.toString(), fwdFlow);
            } else if (revFlow > 1e-9) {
                result.put(rev.toString(), revFlow);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Returns the flow value on a specific edge (by its endpoints).
     * Returns the net flow in the v1→v2 direction (negative if reverse).
     *
     * @param v1 first endpoint
     * @param v2 second endpoint
     * @return net flow from v1 to v2
     */
    public double getFlowOnEdge(String v1, String v2) {
        ensureComputed();
        return flow.getOrDefault(directedKey(v1, v2), 0.0);
    }

    /**
     * BFS from source through residual edges with positive capacity.
     * This is the "source side" of the min cut — the set of vertices
     * still reachable from source after max flow is saturated.
     *
     * <p>Extracted to avoid duplicating the same BFS in
     * {@link #getMinCut()} and {@link #getSourceSide()}.</p>
     *
     * @return set of vertices reachable from source in the residual graph
     */
    private Set<String> findReachableFromSource() {
        Set<String> reachable = new HashSet<String>();
        Queue<String> queue = new LinkedList<String>();
        queue.add(source);
        reachable.add(source);

        while (!queue.isEmpty()) {
            String u = queue.poll();
            Set<String> neighbors = residualAdj.get(u);
            if (neighbors == null) continue;
            for (String v : neighbors) {
                if (!reachable.contains(v) &&
                        residualCapacity.getOrDefault(directedKey(u, v), 0.0) > 1e-9) {
                    reachable.add(v);
                    queue.add(v);
                }
            }
        }
        return reachable;
    }

    /**
     * Returns edges that form the minimum cut (max-flow min-cut theorem).
     * These are edges crossing from the source side to the sink side in
     * the residual graph where no augmenting path exists.
     *
     * @return list of edges in the minimum cut
     */
    public List<edge> getMinCut() {
        ensureComputed();

        Set<String> reachable = findReachableFromSource();

        // Min cut edges: original edges with one end in reachable, other not
        List<edge> cut = new ArrayList<edge>();
        for (edge e : graph.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            if ((reachable.contains(v1) && !reachable.contains(v2)) ||
                    (reachable.contains(v2) && !reachable.contains(v1))) {
                cut.add(e);
            }
        }
        return Collections.unmodifiableList(cut);
    }

    /**
     * Returns the set of vertices reachable from the source in the
     * residual graph (the "source side" of the min cut).
     *
     * @return unmodifiable set of vertex IDs on the source side
     */
    public Set<String> getSourceSide() {
        ensureComputed();
        return Collections.unmodifiableSet(findReachableFromSource());
    }

    /**
     * Returns edges that are fully saturated (flow equals capacity).
     * These are potential bottlenecks in the network.
     *
     * @return list of bottleneck edges
     */
    public List<edge> getBottleneckEdges() {
        ensureComputed();
        List<edge> bottlenecks = new ArrayList<edge>();
        for (edge e : graph.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            double cap = getEdgeCapacity(e);

            double fwdFlow = Math.abs(flow.getOrDefault(directedKey(v1, v2), 0.0));
            double revFlow = Math.abs(flow.getOrDefault(directedKey(v2, v1), 0.0));
            double netFlow = Math.max(fwdFlow, revFlow);

            if (netFlow > 1e-9 && Math.abs(netFlow - cap) < 1e-9) {
                bottlenecks.add(e);
            }
        }
        return Collections.unmodifiableList(bottlenecks);
    }

    /**
     * Returns the total capacity of the network (sum of all edge capacities).
     *
     * @return total network capacity
     */
    public double getTotalCapacity() {
        ensureComputed();
        double total = 0;
        for (edge e : graph.getEdges()) {
            total += getEdgeCapacity(e);
        }
        return total;
    }

    /**
     * Returns the network utilisation as a percentage (0–100).
     * Calculated as (max flow / total outgoing capacity from source) × 100.
     *
     * @return utilisation percentage
     */
    public double getUtilisation() {
        ensureComputed();
        double sourceCapacity = 0;
        for (edge e : graph.getIncidentEdges(source)) {
            sourceCapacity += getEdgeCapacity(e);
        }
        if (sourceCapacity <= 0) return 0;
        return (maxFlowValue / sourceCapacity) * 100.0;
    }

    /**
     * Returns the number of augmenting paths found (re-computes for counting).
     * This is equivalent to the number of BFS iterations in Edmonds–Karp.
     *
     * @return number of augmenting paths used
     */
    public int getAugmentingPathCount() {
        ensureComputed();
        // Count paths by decomposing the flow
        return decomposeFlowPaths().size();
    }

    /**
     * Decomposes the max flow into individual source-to-sink paths,
     * each with its flow value.
     *
     * @return list of flow paths, each described by vertices and flow value
     */
    public List<FlowPath> decomposeFlowPaths() {
        ensureComputed();

        // Work on a copy of flows
        Map<ArcKey, Double> flowCopy = new HashMap<ArcKey, Double>(flow);
        List<FlowPath> paths = new ArrayList<FlowPath>();

        while (true) {
            // BFS from source to sink following positive-flow edges
            Map<String, String> parent = new LinkedHashMap<String, String>();
            Queue<String> queue = new LinkedList<String>();
            queue.add(source);
            parent.put(source, null);
            boolean found = false;

            while (!queue.isEmpty() && !found) {
                String u = queue.poll();
                Set<String> neighbors = residualAdj.get(u);
                if (neighbors == null) continue;
                for (String v : neighbors) {
                    ArcKey key = directedKey(u, v);
                    if (!parent.containsKey(v) &&
                            flowCopy.getOrDefault(key, 0.0) > 1e-9) {
                        parent.put(v, u);
                        if (v.equals(sink)) {
                            found = true;
                            break;
                        }
                        queue.add(v);
                    }
                }
            }

            if (!found) break;

            // Find min flow along path
            double pathFlow = Double.MAX_VALUE;
            String v = sink;
            List<String> pathVertices = new ArrayList<String>();
            while (v != null) {
                pathVertices.add(v);
                String u = parent.get(v);
                if (u != null) {
                    pathFlow = Math.min(pathFlow,
                            flowCopy.getOrDefault(directedKey(u, v), 0.0));
                }
                v = u;
            }
            Collections.reverse(pathVertices);

            // Subtract flow
            for (int i = 0; i < pathVertices.size() - 1; i++) {
                ArcKey key = directedKey(pathVertices.get(i), pathVertices.get(i + 1));
                flowCopy.put(key, flowCopy.getOrDefault(key, 0.0) - pathFlow);
            }

            paths.add(new FlowPath(
                    Collections.unmodifiableList(pathVertices), pathFlow));
        }

        return Collections.unmodifiableList(paths);
    }

    // ── Result object ──────────────────────────────────────────────

    /**
     * Represents a single flow path from source to sink.
     */
    public static class FlowPath {
        private final List<String> vertices;
        private final double flowValue;

        public FlowPath(List<String> vertices, double flowValue) {
            this.vertices = vertices;
            this.flowValue = flowValue;
        }

        public List<String> getVertices() { return vertices; }
        public double getFlowValue() { return flowValue; }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < vertices.size(); i++) {
                if (i > 0) sb.append(" \u2192 ");
                sb.append(vertices.get(i));
            }
            sb.append(String.format(" (flow: %.2f)", flowValue));
            return sb.toString();
        }
    }

    /**
     * Immutable result snapshot of a max-flow computation.
     */
    public static class FlowResult {
        private final String source;
        private final String sink;
        private final double maxFlow;
        private final double totalCapacity;
        private final double utilisation;
        private final int minCutSize;
        private final int bottleneckCount;
        private final int pathCount;
        private final Map<String, Double> edgeFlows;
        private final List<FlowPath> flowPaths;

        public FlowResult(String source, String sink, double maxFlow,
                          double totalCapacity, double utilisation,
                          int minCutSize, int bottleneckCount, int pathCount,
                          Map<String, Double> edgeFlows,
                          List<FlowPath> flowPaths) {
            this.source = source;
            this.sink = sink;
            this.maxFlow = maxFlow;
            this.totalCapacity = totalCapacity;
            this.utilisation = utilisation;
            this.minCutSize = minCutSize;
            this.bottleneckCount = bottleneckCount;
            this.pathCount = pathCount;
            this.edgeFlows = edgeFlows;
            this.flowPaths = flowPaths;
        }

        public String getSource() { return source; }
        public String getSink() { return sink; }
        public double getMaxFlow() { return maxFlow; }
        public double getTotalCapacity() { return totalCapacity; }
        public double getUtilisation() { return utilisation; }
        public int getMinCutSize() { return minCutSize; }
        public int getBottleneckCount() { return bottleneckCount; }
        public int getPathCount() { return pathCount; }
        public Map<String, Double> getEdgeFlows() { return edgeFlows; }
        public List<FlowPath> getFlowPaths() { return flowPaths; }
    }

    /**
     * Returns an immutable result snapshot of the computation.
     *
     * @return FlowResult with all analytics
     */
    public FlowResult getResult() {
        ensureComputed();
        List<FlowPath> paths = decomposeFlowPaths();
        List<edge> minCut = getMinCut();
        List<edge> bottlenecks = getBottleneckEdges();
        return new FlowResult(
                source, sink, maxFlowValue,
                getTotalCapacity(), getUtilisation(),
                minCut.size(), bottlenecks.size(),
                paths.size(), getEdgeFlows(), paths
        );
    }

    // ── Summary ────────────────────────────────────────────────────

    /**
     * Returns a formatted multi-line summary of the flow computation.
     *
     * @return human-readable summary string
     */
    public String getSummary() {
        ensureComputed();

        // Compute expensive results once
        List<FlowPath> paths = decomposeFlowPaths();
        List<edge> minCut = getMinCut();
        List<edge> bottlenecks = getBottleneckEdges();

        StringBuilder sb = new StringBuilder();
        sb.append("=== Network Flow Analysis ===\n");
        sb.append(String.format("Vertices: %d | Edges: %d\n",
                graph.getVertexCount(), graph.getEdgeCount()));
        sb.append(String.format("Source: %s | Sink: %s\n", source, sink));
        sb.append(String.format("Maximum flow: %.2f\n", maxFlowValue));
        sb.append(String.format("Total capacity: %.2f\n", getTotalCapacity()));
        sb.append(String.format("Utilisation: %.1f%%\n", getUtilisation()));
        sb.append(String.format("Min-cut edges: %d\n", minCut.size()));
        sb.append(String.format("Bottleneck edges: %d\n", bottlenecks.size()));
        sb.append(String.format("Flow paths: %d\n", paths.size()));

        if (!paths.isEmpty()) {
            sb.append("\n--- Flow paths ---\n");
            for (int i = 0; i < paths.size(); i++) {
                sb.append(String.format("  %d. %s\n", i + 1, paths.get(i)));
            }
        }

        Map<String, Double> edgeFlows = getEdgeFlows();
        if (!edgeFlows.isEmpty()) {
            sb.append("\n--- Edge flows ---\n");
            for (edge e : graph.getEdges()) {
                String v1 = e.getVertex1();
                String v2 = e.getVertex2();
                ArcKey fwdKey = directedKey(v1, v2);
                ArcKey revKey = directedKey(v2, v1);

                double fwdFlow = flow.getOrDefault(fwdKey, 0.0);
                double revFlow = flow.getOrDefault(revKey, 0.0);
                double cap = getEdgeCapacity(e);

                if (fwdFlow > 1e-9) {
                    sb.append(String.format("  %s: %.2f / %.2f%s\n",
                            fwdKey, fwdFlow, cap,
                            Math.abs(fwdFlow - cap) < 1e-9 ? " [SATURATED]" : ""));
                } else if (revFlow > 1e-9) {
                    sb.append(String.format("  %s: %.2f / %.2f%s\n",
                            revKey, revFlow, cap,
                            Math.abs(revFlow - cap) < 1e-9 ? " [SATURATED]" : ""));
                }
            }
        }

        return sb.toString();
    }

    // ── Internal helpers ───────────────────────────────────────────

    private void buildResidualGraph() {
        residualCapacity = new HashMap<ArcKey, Double>();
        flow = new HashMap<ArcKey, Double>();
        residualAdj = new HashMap<String, Set<String>>();
        capacity = new HashMap<ArcKey, Double>();
        edgeLookup = new HashMap<ArcKey, edge>();

        // Initialise adjacency sets for all vertices
        for (String v : graph.getVertices()) {
            residualAdj.put(v, new LinkedHashSet<String>());
        }

        for (edge e : graph.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            double cap = getEdgeCapacity(e);

            ArcKey fwd = directedKey(v1, v2);
            ArcKey rev = directedKey(v2, v1);

            // Each undirected edge → two directed arcs
            residualCapacity.put(fwd,
                    residualCapacity.getOrDefault(fwd, 0.0) + cap);
            residualCapacity.put(rev,
                    residualCapacity.getOrDefault(rev, 0.0) + cap);

            capacity.put(fwd, capacity.getOrDefault(fwd, 0.0) + cap);
            capacity.put(rev, capacity.getOrDefault(rev, 0.0) + cap);

            edgeLookup.put(fwd, e);
            edgeLookup.put(rev, e);

            residualAdj.get(v1).add(v2);
            residualAdj.get(v2).add(v1);
        }
    }

    private double bfsAugmentingPath(Map<String, String> parent,
                                     Map<String, ArcKey> parentArcKey) {
        Queue<String> queue = new LinkedList<String>();
        queue.add(source);
        parent.put(source, null);

        while (!queue.isEmpty()) {
            String u = queue.poll();
            Set<String> neighbors = residualAdj.get(u);
            if (neighbors == null) continue;

            for (String v : neighbors) {
                ArcKey key = directedKey(u, v);
                if (!parent.containsKey(v) &&
                        residualCapacity.getOrDefault(key, 0.0) > 1e-9) {
                    parent.put(v, u);
                    parentArcKey.put(v, key);
                    if (v.equals(sink)) {
                        // Find bottleneck
                        double pathFlow = Double.MAX_VALUE;
                        String t = sink;
                        while (!t.equals(source)) {
                            String p = parent.get(t);
                            ArcKey k = directedKey(p, t);
                            pathFlow = Math.min(pathFlow,
                                    residualCapacity.getOrDefault(k, 0.0));
                            t = p;
                        }
                        return pathFlow;
                    }
                    queue.add(v);
                }
            }
        }
        return 0; // no augmenting path
    }

    private double getEdgeCapacity(edge e) {
        float w = e.getWeight();
        return w > 0 ? w : 1.0;
    }

    private ArcKey directedKey(String from, String to) {
        return new ArcKey(from, to);
    }

    private void validateVertex(String vertex, String name) {
        if (vertex == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        if (!graph.containsVertex(vertex)) {
            throw new IllegalArgumentException(
                    name + " vertex '" + vertex + "' is not in the graph");
        }
    }

    private void ensureComputed() {
        if (!computed) {
            throw new IllegalStateException(
                    "Must call compute(source, sink) before querying results");
        }
    }
}
