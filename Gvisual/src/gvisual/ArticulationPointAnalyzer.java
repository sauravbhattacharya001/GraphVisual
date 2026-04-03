package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.util.*;

/**
 * Finds articulation points (cut vertices) and bridges (cut edges) in a
 * graph using Tarjan's DFS-based algorithm.
 *
 * <p>An <b>articulation point</b> is a vertex whose removal disconnects
 * the graph (or increases its number of connected components). A
 * <b>bridge</b> is an Edge whose removal disconnects the graph.</p>
 *
 * <p>These are critical elements for network reliability analysis:</p>
 * <ul>
 *   <li>Single points of failure in communication networks</li>
 *   <li>Key individuals in social networks (gatekeepers)</li>
 *   <li>Critical links in infrastructure graphs</li>
 * </ul>
 *
 * <p>The algorithm runs in O(V + E) time using a single DFS pass.</p>
 *
 * @author zalenix
 */
public class ArticulationPointAnalyzer {

    private final Graph<String, Edge> graph;

    /**
     * Create a new analyzer for the given graph.
     *
     * @param graph the JUNG graph to analyze (must not be null)
     * @throws IllegalArgumentException if graph is null
     */
    public ArticulationPointAnalyzer(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
    }

    // ── Result classes ──────────────────────────────────────────

    /**
     * A bridge (cut Edge) whose removal disconnects the graph.
     */
    public static class Bridge {
        private final Edge bridgeEdge;
        private final String endpoint1;
        private final String endpoint2;
        private final int componentSizeA;
        private final int componentSizeB;

        public Bridge(Edge bridgeEdge, String endpoint1, String endpoint2,
                      int componentSizeA, int componentSizeB) {
            this.bridgeEdge = bridgeEdge;
            this.endpoint1 = endpoint1;
            this.endpoint2 = endpoint2;
            this.componentSizeA = componentSizeA;
            this.componentSizeB = componentSizeB;
        }

        /** The bridge Edge. */
        public Edge getEdge() { return bridgeEdge; }
        /** One endpoint. */
        public String getEndpoint1() { return endpoint1; }
        /** Other endpoint. */
        public String getEndpoint2() { return endpoint2; }
        /** Size of component on endpoint1's side after removal. */
        public int getComponentSizeA() { return componentSizeA; }
        /** Size of component on endpoint2's side after removal. */
        public int getComponentSizeB() { return componentSizeB; }

        /** Severity: how imbalanced the split would be (0-1, 1 = most severe). */
        public double getSeverity() {
            int total = componentSizeA + componentSizeB;
            if (total == 0) return 0;
            int smaller = Math.min(componentSizeA, componentSizeB);
            // 1.0 when one side has 1 node, 0.0 when perfectly balanced
            return 1.0 - (2.0 * smaller / total);
        }
    }

    /**
     * Details about an articulation point.
     */
    public static class ArticulationPointInfo {
        private final String vertex;
        private final int degree;
        private final int biconnectedComponents;
        private final Map<String, Integer> edgeTypeCounts;

        public ArticulationPointInfo(String vertex, int degree,
                                     int biconnectedComponents,
                                     Map<String, Integer> edgeTypeCounts) {
            this.vertex = vertex;
            this.degree = degree;
            this.biconnectedComponents = biconnectedComponents;
            this.edgeTypeCounts = Collections.unmodifiableMap(edgeTypeCounts);
        }

        /** The vertex ID. */
        public String getVertex() { return vertex; }
        /** Degree of this vertex. */
        public int getDegree() { return degree; }
        /** Number of biconnected components this vertex belongs to. */
        public int getBiconnectedComponents() { return biconnectedComponents; }
        /** Edge type breakdown for this vertex's edges. */
        public Map<String, Integer> getEdgeTypeCounts() { return edgeTypeCounts; }

        /** Criticality score based on degree and biconnected components. */
        public double getCriticality() {
            return degree * 0.4 + biconnectedComponents * 0.6;
        }
    }

    /**
     * Complete analysis result.
     */
    public static class AnalysisResult {
        private final Set<String> articulationPoints;
        private final List<ArticulationPointInfo> articulationPointDetails;
        private final List<Bridge> bridges;
        private final int totalVertices;
        private final int totalEdges;
        private final int connectedComponents;

        public AnalysisResult(Set<String> articulationPoints,
                              List<ArticulationPointInfo> articulationPointDetails,
                              List<Bridge> bridges,
                              int totalVertices, int totalEdges,
                              int connectedComponents) {
            this.articulationPoints = Collections.unmodifiableSet(articulationPoints);
            this.articulationPointDetails = Collections.unmodifiableList(articulationPointDetails);
            this.bridges = Collections.unmodifiableList(bridges);
            this.totalVertices = totalVertices;
            this.totalEdges = totalEdges;
            this.connectedComponents = connectedComponents;
        }

        /** Set of articulation point vertex IDs. */
        public Set<String> getArticulationPoints() { return articulationPoints; }
        /** Detailed info for each articulation point, sorted by criticality. */
        public List<ArticulationPointInfo> getArticulationPointDetails() {
            return articulationPointDetails;
        }
        /** List of bridges, sorted by severity. */
        public List<Bridge> getBridges() { return bridges; }
        /** Total vertices in the graph. */
        public int getTotalVertices() { return totalVertices; }
        /** Total edges in the graph. */
        public int getTotalEdges() { return totalEdges; }
        /** Number of connected components. */
        public int getConnectedComponents() { return connectedComponents; }
        /** Number of articulation points found. */
        public int getArticulationPointCount() { return articulationPoints.size(); }
        /** Number of bridges found. */
        public int getBridgeCount() { return bridges.size(); }
        /** Whether the graph has any critical elements. */
        public boolean hasCriticalElements() {
            return !articulationPoints.isEmpty() || !bridges.isEmpty();
        }

        /** Percentage of vertices that are articulation points. */
        public double getArticulationPointPercentage() {
            if (totalVertices == 0) return 0;
            return (double) articulationPoints.size() / totalVertices * 100.0;
        }

        /** Resilience score (0-100): higher = more resilient network. */
        public double getResilienceScore() {
            if (totalVertices <= 1) return 100.0;
            double apPenalty = (double) articulationPoints.size() / totalVertices * 50.0;
            double bridgePenalty = totalEdges > 0
                    ? (double) bridges.size() / totalEdges * 50.0
                    : 0;
            return Math.max(0, 100.0 - apPenalty - bridgePenalty);
        }

        /** Network vulnerability classification. */
        public String getVulnerabilityLevel() {
            double score = getResilienceScore();
            if (score >= 90) return "ROBUST";
            if (score >= 70) return "MODERATE";
            if (score >= 50) return "VULNERABLE";
            if (score >= 30) return "FRAGILE";
            return "CRITICAL";
        }

        /** Get summary text for display. */
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Resilience: %.0f/100 (%s)\n",
                    getResilienceScore(), getVulnerabilityLevel()));
            sb.append(String.format("Cut vertices: %d (%.1f%%)\n",
                    articulationPoints.size(), getArticulationPointPercentage()));
            sb.append(String.format("Bridges: %d\n", bridges.size()));
            sb.append(String.format("Components: %d", connectedComponents));
            return sb.toString();
        }
    }

    // ── Algorithm ───────────────────────────────────────────────

    /**
     * Run the articulation point and bridge analysis.
     *
     * @return the analysis result
     */
    public AnalysisResult analyze() {
        Collection<String> vertices = graph.getVertices();
        int vertexCount = vertices.size();
        int edgeCount = graph.getEdgeCount();

        if (vertexCount == 0) {
            return new AnalysisResult(
                    Collections.<String>emptySet(),
                    Collections.<ArticulationPointInfo>emptyList(),
                    Collections.<Bridge>emptyList(),
                    0, 0, 0);
        }

        // Tarjan's algorithm state
        Map<String, Integer> disc = new HashMap<String, Integer>();
        Map<String, Integer> low = new HashMap<String, Integer>();
        Map<String, String> parent = new HashMap<String, String>();
        Set<String> visited = new HashSet<String>();
        Set<String> articulationPoints = new LinkedHashSet<String>();
        List<Edge> bridgeEdges = new ArrayList<Edge>();
        int[] timer = {0};

        // Run DFS from each unvisited vertex (handles disconnected graphs)
        int componentCount = 0;
        for (String v : vertices) {
            if (!visited.contains(v)) {
                componentCount++;
                dfs(v, disc, low, parent, visited, articulationPoints,
                        bridgeEdges, timer);
            }
        }

        // Build detailed info for articulation points
        List<ArticulationPointInfo> details = new ArrayList<ArticulationPointInfo>();
        for (String ap : articulationPoints) {
            int degree = graph.degree(ap);
            Map<String, Integer> edgeTypeCounts = new HashMap<String, Integer>();
            for (Edge e : graph.getIncidentEdges(ap)) {
                String type = e.getType() != null ? e.getType() : "unknown";
                edgeTypeCounts.put(type, edgeTypeCounts.getOrDefault(type, 0) + 1);
            }
            // Count biconnected components this vertex participates in
            int bicomp = countBiconnectedComponents(ap);
            details.add(new ArticulationPointInfo(ap, degree, bicomp, edgeTypeCounts));
        }
        // Sort by criticality (highest first)
        Collections.sort(details, (ArticulationPointInfo a, ArticulationPointInfo b) -> {
                return Double.compare(b.getCriticality(), a.getCriticality());
            });

        // Build bridge details with component size estimation
        List<Bridge> bridges = new ArrayList<Bridge>();
        for (Edge e : bridgeEdges) {
            String v1 = e.getVertex1() != null ? e.getVertex1() : findEndpoints(e)[0];
            String v2 = e.getVertex2() != null ? e.getVertex2() : findEndpoints(e)[1];
            int[] sizes = estimateComponentSizes(v1, v2, e);
            bridges.add(new Bridge(e, v1, v2, sizes[0], sizes[1]));
        }
        // Sort by severity (highest first)
        Collections.sort(bridges, (Bridge a, Bridge b) -> {
                return Double.compare(b.getSeverity(), a.getSeverity());
            });

        return new AnalysisResult(articulationPoints, details, bridges,
                vertexCount, edgeCount, componentCount);
    }

    /**
     * Tarjan's DFS for finding articulation points and bridges.
     */
    private void dfs(String u,
                     Map<String, Integer> disc,
                     Map<String, Integer> low,
                     Map<String, String> parent,
                     Set<String> visited,
                     Set<String> articulationPoints,
                     List<Edge> bridges,
                     int[] timer) {
        visited.add(u);
        disc.put(u, timer[0]);
        low.put(u, timer[0]);
        timer[0]++;
        int children = 0;

        for (String v : graph.getNeighbors(u)) {
            if (!visited.contains(v)) {
                children++;
                parent.put(v, u);
                dfs(v, disc, low, parent, visited, articulationPoints,
                        bridges, timer);

                // Update low value
                low.put(u, Math.min(low.get(u), low.get(v)));

                // u is an articulation point if:
                // 1) u is root of DFS tree and has 2+ children
                if (!parent.containsKey(u) && children > 1) {
                    articulationPoints.add(u);
                }
                // 2) u is not root and low[v] >= disc[u]
                if (parent.containsKey(u) && low.get(v) >= disc.get(u)) {
                    articulationPoints.add(u);
                }

                // Bridge: low[v] > disc[u]
                if (low.get(v) > disc.get(u)) {
                    Edge bridgeEdge = findEdge(u, v);
                    if (bridgeEdge != null) {
                        bridges.add(bridgeEdge);
                    }
                }
            } else if (!v.equals(parent.get(u))) {
                // Back Edge — update low value
                low.put(u, Math.min(low.get(u), disc.get(v)));
            }
        }
    }

    /**
     * Find the Edge connecting two vertices.
     */
    private Edge findEdge(String u, String v) {
        for (Edge e : graph.getIncidentEdges(u)) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            // Also check via JUNG endpoints since vertex1/vertex2 may be null
            Collection<String> endpoints = graph.getEndpoints(e);
            if (endpoints != null && endpoints.contains(u) && endpoints.contains(v)) {
                return e;
            }
            if ((u.equals(v1) && v.equals(v2)) || (u.equals(v2) && v.equals(v1))) {
                return e;
            }
        }
        return null;
    }

    /**
     * Find endpoints of an Edge via the graph when vertex1/vertex2 may be null.
     */
    private String[] findEndpoints(Edge e) {
        Collection<String> endpoints = graph.getEndpoints(e);
        if (endpoints != null && endpoints.size() == 2) {
            Iterator<String> it = endpoints.iterator();
            return new String[]{it.next(), it.next()};
        }
        return new String[]{e.getVertex1(), e.getVertex2()};
    }

    /**
     * Count the number of biconnected components a vertex participates in.
     * Uses a simplified approach: the count equals the number of
     * distinct subtree groups separated by the articulation point.
     */
    private int countBiconnectedComponents(String vertex) {
        Collection<String> neighbors = graph.getNeighbors(vertex);
        if (neighbors == null || neighbors.isEmpty()) return 0;

        // BFS from each neighbor, not going through the vertex
        Set<Set<String>> components = new HashSet<Set<String>>();
        Set<String> assigned = new HashSet<String>();

        for (String neighbor : neighbors) {
            if (assigned.contains(neighbor)) continue;
            // BFS from neighbor, excluding the articulation point
            Set<String> component = new HashSet<String>();
            Queue<String> queue = new ArrayDeque<String>();
            queue.add(neighbor);
            component.add(neighbor);
            while (!queue.isEmpty()) {
                String current = queue.poll();
                for (String next : graph.getNeighbors(current)) {
                    if (!next.equals(vertex) && !component.contains(next)) {
                        component.add(next);
                        queue.add(next);
                    }
                }
            }
            components.add(component);
            assigned.addAll(component);
        }
        return components.size();
    }

    /**
     * Estimate the sizes of the two components that would result from
     * removing a bridge Edge.
     */
    private int[] estimateComponentSizes(String v1, String v2, Edge bridgeEdge) {
        // BFS from v1, excluding the bridge Edge
        Set<String> comp1 = bfsExcludingEdge(v1, bridgeEdge);
        Set<String> comp2 = bfsExcludingEdge(v2, bridgeEdge);
        return new int[]{comp1.size(), comp2.size()};
    }

    /**
     * BFS from a start vertex, excluding a specific Edge.
     */
    private Set<String> bfsExcludingEdge(String start, Edge excluded) {
        Set<String> visited = new HashSet<String>();
        Queue<String> queue = new ArrayDeque<String>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (Edge e : graph.getIncidentEdges(current)) {
                if (e == excluded) continue;
                Collection<String> endpoints = graph.getEndpoints(e);
                for (String neighbor : endpoints) {
                    if (!neighbor.equals(current) && !visited.contains(neighbor)) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
        }
        return visited;
    }
}
