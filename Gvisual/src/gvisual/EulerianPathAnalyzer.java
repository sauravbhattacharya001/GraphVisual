package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.util.*;

/**
 * Analyzes Eulerian paths and circuits in graphs.
 *
 * <p>An <b>Eulerian circuit</b> is a closed walk that visits every edge
 * exactly once and returns to the starting vertex. An <b>Eulerian path</b>
 * visits every edge exactly once but may start and end at different vertices.</p>
 *
 * <p>Conditions (undirected graphs):</p>
 * <ul>
 *   <li>Eulerian circuit exists iff every vertex has even degree and the graph is connected</li>
 *   <li>Eulerian path exists iff exactly 0 or 2 vertices have odd degree and the graph is connected</li>
 * </ul>
 *
 * <p>Uses Hierholzer's algorithm for O(V + E) circuit/path construction.</p>
 *
 * <p>Applications: route planning, circuit board tracing, DNA fragment assembly,
 * Chinese Postman Problem analysis, network traversal optimization.</p>
 *
 * @author zalenix
 */
public class EulerianPathAnalyzer {

    private final Graph<String, Edge> graph;

    /**
     * Create a new analyzer for the given graph.
     *
     * @param graph the JUNG graph to analyze (must not be null)
     * @throws IllegalArgumentException if graph is null
     */
    public EulerianPathAnalyzer(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
    }

    // ── Result Types ──────────────────────────────────────────────

    /**
     * Classification of a graph's Eulerian properties.
     */
    public enum EulerianType {
        /** Every vertex has even degree and graph is connected → Eulerian circuit exists */
        EULERIAN_CIRCUIT,
        /** Exactly two vertices have odd degree and graph is connected → Eulerian path (not circuit) exists */
        EULERIAN_PATH,
        /** Graph has no Eulerian path or circuit */
        NOT_EULERIAN
    }

    /**
     * Full analysis result for Eulerian properties.
     */
    public static class EulerianAnalysis {
        private final EulerianType type;
        private final List<String> oddDegreeVertices;
        private final int totalEdges;
        private final int totalVertices;
        private final boolean isConnected;
        private final Map<String, Integer> degreeMap;

        public EulerianAnalysis(EulerianType type, List<String> oddDegreeVertices,
                                int totalEdges, int totalVertices, boolean isConnected,
                                Map<String, Integer> degreeMap) {
            this.type = type;
            this.oddDegreeVertices = Collections.unmodifiableList(new ArrayList<String>(oddDegreeVertices));
            this.totalEdges = totalEdges;
            this.totalVertices = totalVertices;
            this.isConnected = isConnected;
            this.degreeMap = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(degreeMap));
        }

        public EulerianType getType() { return type; }
        public List<String> getOddDegreeVertices() { return oddDegreeVertices; }
        public int getTotalEdges() { return totalEdges; }
        public int getTotalVertices() { return totalVertices; }
        public boolean isConnected() { return isConnected; }
        public Map<String, Integer> getDegreeMap() { return degreeMap; }

        /**
         * Returns the minimum number of edges that must be duplicated
         * to make the graph Eulerian (Chinese Postman lower bound).
         * For an Eulerian graph this is 0; for a semi-Eulerian graph
         * it is 0 (path exists); otherwise it equals oddDegreeVertices.size() / 2
         * pairings needed.
         */
        public int getMinEdgeDuplications() {
            if (type == EulerianType.EULERIAN_CIRCUIT) return 0;
            if (type == EulerianType.EULERIAN_PATH) return 0;
            return oddDegreeVertices.size() / 2;
        }
    }

    /**
     * Result of an Eulerian path/circuit computation.
     */
    public static class EulerianPathResult {
        private final List<String> vertices;
        private final List<Edge> edges;
        private final boolean isCircuit;

        public EulerianPathResult(List<String> vertices, List<Edge> edges, boolean isCircuit) {
            this.vertices = Collections.unmodifiableList(new ArrayList<String>(vertices));
            this.edges = Collections.unmodifiableList(new ArrayList<Edge>(edges));
            this.isCircuit = isCircuit;
        }

        public List<String> getVertices() { return vertices; }
        public List<Edge> getEdges() { return edges; }
        public boolean isCircuit() { return isCircuit; }
        public int getEdgeCount() { return edges.size(); }
    }

    // ── Analysis ──────────────────────────────────────────────────

    /**
     * Analyze the graph's Eulerian properties.
     *
     * @return full analysis including type, odd-degree vertices, and degree map
     */
    public EulerianAnalysis analyze() {
        Map<String, Integer> degreeMap = new LinkedHashMap<String, Integer>();
        List<String> oddDegreeVertices = new ArrayList<String>();

        for (String v : graph.getVertices()) {
            int deg = graph.degree(v);
            degreeMap.put(v, deg);
            if (deg % 2 != 0) {
                oddDegreeVertices.add(v);
            }
        }

        boolean connected = isConnectedIgnoringIsolated();
        int oddCount = oddDegreeVertices.size();

        EulerianType type;
        if (!connected || graph.getEdgeCount() == 0) {
            type = (graph.getEdgeCount() == 0 && graph.getVertexCount() <= 1)
                    ? EulerianType.EULERIAN_CIRCUIT
                    : EulerianType.NOT_EULERIAN;
        } else if (oddCount == 0) {
            type = EulerianType.EULERIAN_CIRCUIT;
        } else if (oddCount == 2) {
            type = EulerianType.EULERIAN_PATH;
        } else {
            type = EulerianType.NOT_EULERIAN;
        }

        return new EulerianAnalysis(type, oddDegreeVertices,
                graph.getEdgeCount(), graph.getVertexCount(), connected, degreeMap);
    }

    /**
     * Find an Eulerian path or circuit using Hierholzer's algorithm.
     *
     * @return the path/circuit, or null if none exists
     */
    public EulerianPathResult findEulerianPath() {
        EulerianAnalysis analysis = analyze();
        if (analysis.getType() == EulerianType.NOT_EULERIAN) {
            return null;
        }

        // Build adjacency with edge tracking
        Map<String, LinkedList<EdgeEntry>> adj = new LinkedHashMap<String, LinkedList<EdgeEntry>>();
        for (String v : graph.getVertices()) {
            adj.put(v, new LinkedList<EdgeEntry>());
        }

        Set<Edge> allEdges = new HashSet<Edge>();
        for (Edge e : graph.getEdges()) {
            String v1 = graph.getEndpoints(e).getFirst();
            String v2 = graph.getEndpoints(e).getSecond();
            EdgeEntry entry1 = new EdgeEntry(v2, e);
            EdgeEntry entry2 = new EdgeEntry(v1, e);
            entry1.partner = entry2;
            entry2.partner = entry1;
            adj.get(v1).add(entry1);
            adj.get(v2).add(entry2);
            allEdges.add(e);
        }

        // Choose start vertex
        String start;
        if (analysis.getType() == EulerianType.EULERIAN_PATH) {
            start = analysis.getOddDegreeVertices().get(0);
        } else {
            // Pick any vertex with edges
            start = null;
            for (String v : graph.getVertices()) {
                if (graph.degree(v) > 0) {
                    start = v;
                    break;
                }
            }
            if (start == null) {
                // No edges — trivial circuit
                List<String> verts = new ArrayList<String>();
                if (graph.getVertexCount() > 0) {
                    verts.add(graph.getVertices().iterator().next());
                }
                return new EulerianPathResult(verts, new ArrayList<Edge>(), true);
            }
        }

        // Hierholzer's algorithm
        Deque<String> stack = new ArrayDeque<String>();
        List<String> pathVertices = new ArrayList<String>();
        List<Edge> pathEdges = new ArrayList<Edge>();

        stack.push(start);

        while (!stack.isEmpty()) {
            String v = stack.peek();
            LinkedList<EdgeEntry> neighbors = adj.get(v);

            // Remove used edges from front
            while (!neighbors.isEmpty() && neighbors.getFirst().used) {
                neighbors.removeFirst();
            }

            if (neighbors.isEmpty()) {
                stack.pop();
                pathVertices.add(v);
            } else {
                EdgeEntry entry = neighbors.removeFirst();
                entry.used = true;
                entry.partner.used = true;
                stack.push(entry.target);
            }
        }

        // Reverse to get correct order
        Collections.reverse(pathVertices);

        // Reconstruct edges from vertex sequence
        List<Edge> orderedEdges = new ArrayList<Edge>();
        for (int i = 0; i < pathVertices.size() - 1; i++) {
            String v1 = pathVertices.get(i);
            String v2 = pathVertices.get(i + 1);
            Edge found = null;
            for (Edge e : allEdges) {
                String e1 = graph.getEndpoints(e).getFirst();
                String e2 = graph.getEndpoints(e).getSecond();
                if ((e1.equals(v1) && e2.equals(v2)) || (e1.equals(v2) && e2.equals(v1))) {
                    found = e;
                    break;
                }
            }
            if (found != null) {
                orderedEdges.add(found);
                allEdges.remove(found);
            }
        }

        boolean isCircuit = analysis.getType() == EulerianType.EULERIAN_CIRCUIT;
        return new EulerianPathResult(pathVertices, orderedEdges, isCircuit);
    }

    /**
     * Suggest which edges to add to make a non-Eulerian graph Eulerian.
     * Returns pairs of odd-degree vertices that should be connected.
     *
     * @return list of vertex pairs to connect, empty if already Eulerian
     */
    public List<String[]> suggestEdgesForEulerian() {
        EulerianAnalysis analysis = analyze();
        if (analysis.getType() == EulerianType.EULERIAN_CIRCUIT) {
            return Collections.emptyList();
        }

        List<String> odd = new ArrayList<String>(analysis.getOddDegreeVertices());
        List<String[]> suggestions = new ArrayList<String[]>();

        // Pair up odd-degree vertices greedily
        for (int i = 0; i < odd.size() - 1; i += 2) {
            suggestions.add(new String[]{odd.get(i), odd.get(i + 1)});
        }

        return suggestions;
    }

    /**
     * Compute edge connectivity — minimum edges to remove to disconnect the graph.
     * Uses iterative max-flow between a fixed source and all other vertices,
     * returning the minimum.
     *
     * @return edge connectivity (0 if disconnected or single vertex)
     */
    public int computeEdgeConnectivity() {
        Collection<String> vertices = graph.getVertices();
        if (vertices.size() <= 1) return 0;

        Iterator<String> it = vertices.iterator();
        String source = it.next();
        int minCut = Integer.MAX_VALUE;

        while (it.hasNext()) {
            String target = it.next();
            int flow = maxFlowBFS(source, target);
            minCut = Math.min(minCut, flow);
        }

        return minCut == Integer.MAX_VALUE ? 0 : minCut;
    }

    /**
     * Generate a text summary of the Eulerian analysis.
     *
     * @return human-readable summary string
     */
    public String generateReport() {
        EulerianAnalysis analysis = analyze();
        StringBuilder sb = new StringBuilder();
        sb.append("=== Eulerian Path Analysis ===\n\n");
        sb.append(String.format("Vertices: %d, Edges: %d\n", analysis.getTotalVertices(), analysis.getTotalEdges()));
        sb.append(String.format("Connected: %s\n", analysis.isConnected() ? "Yes" : "No"));
        sb.append(String.format("Odd-degree vertices: %d\n", analysis.getOddDegreeVertices().size()));

        if (!analysis.getOddDegreeVertices().isEmpty()) {
            sb.append("  Odd vertices: ");
            sb.append(join(analysis.getOddDegreeVertices(), ", "));
            sb.append("\n");
        }

        sb.append(String.format("\nType: %s\n", analysis.getType()));

        switch (analysis.getType()) {
            case EULERIAN_CIRCUIT:
                sb.append("→ An Eulerian circuit exists (every edge visited exactly once, returning to start).\n");
                break;
            case EULERIAN_PATH:
                sb.append(String.format("→ An Eulerian path exists from %s to %s.\n",
                        analysis.getOddDegreeVertices().get(0),
                        analysis.getOddDegreeVertices().get(1)));
                break;
            case NOT_EULERIAN:
                sb.append("→ No Eulerian path or circuit exists.\n");
                sb.append(String.format("  Need to duplicate at least %d edge(s) for Chinese Postman solution.\n",
                        analysis.getMinEdgeDuplications()));
                List<String[]> suggestions = suggestEdgesForEulerian();
                if (!suggestions.isEmpty()) {
                    sb.append("  Suggested edge additions:\n");
                    for (String[] pair : suggestions) {
                        sb.append(String.format("    Connect %s — %s\n", pair[0], pair[1]));
                    }
                }
                break;
        }

        // Degree distribution
        sb.append("\nDegree Distribution:\n");
        for (Map.Entry<String, Integer> entry : analysis.getDegreeMap().entrySet()) {
            sb.append(String.format("  %s: degree %d%s\n", entry.getKey(), entry.getValue(),
                    entry.getValue() % 2 != 0 ? " (odd)" : ""));
        }

        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────

    private static class EdgeEntry {
        final String target;
        final Edge e;
        boolean used = false;
        EdgeEntry partner;

        EdgeEntry(String target, Edge e) {
            this.target = target;
            this.e = e;
        }
    }

    /**
     * Check if the graph is connected, ignoring isolated vertices (degree 0).
     */
    private boolean isConnectedIgnoringIsolated() {
        // Find a vertex with edges
        String start = null;
        for (String v : graph.getVertices()) {
            if (graph.degree(v) > 0) {
                start = v;
                break;
            }
        }
        if (start == null) return true; // No edges at all

        Set<String> visited = GraphUtils.bfsComponent(graph, start);

        // Check all non-isolated vertices were visited
        for (String v : graph.getVertices()) {
            if (graph.degree(v) > 0 && !visited.contains(v)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Simple BFS-based max flow (unit capacity) for edge connectivity.
     */
    private int maxFlowBFS(String source, String target) {
        // Build capacity map (unit capacity per edge)
        Map<String, Map<String, Integer>> capacity = new HashMap<String, Map<String, Integer>>();
        for (String v : graph.getVertices()) {
            capacity.put(v, new HashMap<String, Integer>());
        }
        for (Edge e : graph.getEdges()) {
            String v1 = graph.getEndpoints(e).getFirst();
            String v2 = graph.getEndpoints(e).getSecond();
            Integer cur = capacity.get(v1).get(v2);
            capacity.get(v1).put(v2, (cur == null ? 0 : cur) + 1);
            cur = capacity.get(v2).get(v1);
            capacity.get(v2).put(v1, (cur == null ? 0 : cur) + 1);
        }

        int totalFlow = 0;

        while (true) {
            // BFS to find augmenting path
            Map<String, String> parent = new HashMap<String, String>();
            Queue<String> q = new LinkedList<String>();
            q.add(source);
            parent.put(source, source);

            while (!q.isEmpty() && !parent.containsKey(target)) {
                String v = q.poll();
                Map<String, Integer> neighbors = capacity.get(v);
                if (neighbors != null) {
                    for (Map.Entry<String, Integer> entry : neighbors.entrySet()) {
                        if (entry.getValue() > 0 && !parent.containsKey(entry.getKey())) {
                            parent.put(entry.getKey(), v);
                            q.add(entry.getKey());
                        }
                    }
                }
            }

            if (!parent.containsKey(target)) break;

            // Find bottleneck
            int bottleneck = Integer.MAX_VALUE;
            String v = target;
            while (!v.equals(source)) {
                String p = parent.get(v);
                bottleneck = Math.min(bottleneck, capacity.get(p).get(v));
                v = p;
            }

            // Update residual
            v = target;
            while (!v.equals(source)) {
                String p = parent.get(v);
                capacity.get(p).put(v, capacity.get(p).get(v) - bottleneck);
                Integer rev = capacity.get(v).get(p);
                capacity.get(v).put(p, (rev == null ? 0 : rev) + bottleneck);
                v = p;
            }

            totalFlow += bottleneck;
        }

        return totalFlow;
    }

    private static String join(List<String> items, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(items.get(i));
        }
        return sb.toString();
    }
}
