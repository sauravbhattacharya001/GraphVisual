package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.util.*;

/**
 * Computes the Minimum Spanning Tree (or Forest) of a graph using
 * Kruskal's algorithm with Union-Find (disjoint set) for near-linear
 * performance.
 *
 * <p>For disconnected graphs, this computes a Minimum Spanning Forest
 * — one MST per connected component.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Kruskal's algorithm with path compression + union by rank</li>
 *   <li>MST statistics (total weight, edge count, component count)</li>
 *   <li>Per-component breakdown (vertices, edges, weight)</li>
 *   <li>Edge type distribution within the MST</li>
 *   <li>Programmatic API for all results</li>
 * </ul>
 */
public class MinimumSpanningTree {

    private final Graph<String, edge> graph;

    /**
     * Create a new MST analyzer for the given graph.
     *
     * @param graph the JUNG graph to analyze (must not be null)
     * @throws IllegalArgumentException if graph is null
     */
    public MinimumSpanningTree(Graph<String, edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
    }

    /**
     * Compute the MST (or minimum spanning forest for disconnected graphs).
     *
     * @return the MST result containing edges, stats, and component info
     */
    public MSTResult compute() {
        Collection<String> vertices = graph.getVertices();
        int vertexCount = vertices.size();

        if (vertexCount == 0) {
            return new MSTResult(
                    Collections.<edge>emptyList(),
                    Collections.<MSTComponent>emptyList(),
                    0, 0.0f, 0, vertexCount);
        }

        // Collect all edges and sort by weight (Kruskal's)
        List<edge> sortedEdges = new ArrayList<edge>();
        Set<edge> seen = new HashSet<edge>();
        for (edge e : graph.getEdges()) {
            if (!seen.contains(e)) {
                sortedEdges.add(e);
                seen.add(e);
            }
        }
        Collections.sort(sortedEdges, new Comparator<edge>() {
            public int compare(edge a, edge b) {
                return Float.compare(a.getWeight(), b.getWeight());
            }
        });

        // Union-Find
        UnionFind uf = new UnionFind(vertices);

        List<edge> mstEdges = new ArrayList<edge>();
        float totalWeight = 0.0f;

        for (edge e : sortedEdges) {
            String u = e.getVertex1();
            String v = e.getVertex2();
            if (!uf.find(u).equals(uf.find(v))) {
                uf.union(u, v);
                mstEdges.add(e);
                totalWeight += e.getWeight();
            }
        }

        // Build per-component breakdown
        Map<String, List<String>> rootToVertices = new LinkedHashMap<String, List<String>>();
        for (String vertex : vertices) {
            String root = uf.find(vertex);
            List<String> members = rootToVertices.get(root);
            if (members == null) {
                members = new ArrayList<String>();
                rootToVertices.put(root, members);
            }
            members.add(vertex);
        }

        // Map edges to their component
        Map<String, List<edge>> rootToEdges = new LinkedHashMap<String, List<edge>>();
        for (edge e : mstEdges) {
            String root = uf.find(e.getVertex1());
            List<edge> compEdges = rootToEdges.get(root);
            if (compEdges == null) {
                compEdges = new ArrayList<edge>();
                rootToEdges.put(root, compEdges);
            }
            compEdges.add(e);
        }

        List<MSTComponent> components = new ArrayList<MSTComponent>();
        int compId = 0;
        // Sort components by size descending for consistent output
        List<Map.Entry<String, List<String>>> sortedComps =
                new ArrayList<Map.Entry<String, List<String>>>(rootToVertices.entrySet());
        Collections.sort(sortedComps, new Comparator<Map.Entry<String, List<String>>>() {
            public int compare(Map.Entry<String, List<String>> a, Map.Entry<String, List<String>> b) {
                return Integer.compare(b.getValue().size(), a.getValue().size());
            }
        });

        for (Map.Entry<String, List<String>> entry : sortedComps) {
            String root = entry.getKey();
            List<String> members = entry.getValue();
            List<edge> compEdges = rootToEdges.get(root);
            if (compEdges == null) compEdges = Collections.<edge>emptyList();

            float compWeight = 0.0f;
            for (edge e : compEdges) {
                compWeight += e.getWeight();
            }

            components.add(new MSTComponent(compId++, members, compEdges, compWeight));
        }

        int componentCount = rootToVertices.size();

        return new MSTResult(mstEdges, components, componentCount, totalWeight, mstEdges.size(), vertexCount);
    }

    // ==========================================
    //  Union-Find (Disjoint Set) with path
    //  compression and union by rank
    // ==========================================

    /**
     * Disjoint set data structure for Kruskal's algorithm.
     */
    static class UnionFind {
        private final Map<String, String> parent;
        private final Map<String, Integer> rank;

        UnionFind(Collection<String> elements) {
            parent = new HashMap<String, String>();
            rank = new HashMap<String, Integer>();
            for (String e : elements) {
                parent.put(e, e);
                rank.put(e, 0);
            }
        }

        /**
         * Find with path compression.
         */
        String find(String x) {
            String root = x;
            while (!root.equals(parent.get(root))) {
                root = parent.get(root);
            }
            // Path compression
            String current = x;
            while (!current.equals(root)) {
                String next = parent.get(current);
                parent.put(current, root);
                current = next;
            }
            return root;
        }

        /**
         * Union by rank.
         */
        void union(String a, String b) {
            String rootA = find(a);
            String rootB = find(b);
            if (rootA.equals(rootB)) return;

            int rankA = rank.get(rootA);
            int rankB = rank.get(rootB);

            if (rankA < rankB) {
                parent.put(rootA, rootB);
            } else if (rankA > rankB) {
                parent.put(rootB, rootA);
            } else {
                parent.put(rootB, rootA);
                rank.put(rootA, rankA + 1);
            }
        }
    }

    // ==========================================
    //  Result classes
    // ==========================================

    /**
     * Complete MST computation result.
     */
    public static class MSTResult {
        private final List<edge> edges;
        private final List<MSTComponent> components;
        private final int componentCount;
        private final float totalWeight;
        private final int edgeCount;
        private final int vertexCount;

        MSTResult(List<edge> edges, List<MSTComponent> components,
                  int componentCount, float totalWeight, int edgeCount, int vertexCount) {
            this.edges = Collections.unmodifiableList(edges);
            this.components = Collections.unmodifiableList(components);
            this.componentCount = componentCount;
            this.totalWeight = totalWeight;
            this.edgeCount = edgeCount;
            this.vertexCount = vertexCount;
        }

        /** All MST edges. */
        public List<edge> getEdges() { return edges; }

        /** Per-component breakdown. */
        public List<MSTComponent> getComponents() { return components; }

        /** Number of connected components. */
        public int getComponentCount() { return componentCount; }

        /** Total weight of all MST edges. */
        public float getTotalWeight() { return totalWeight; }

        /** Number of edges in the MST. */
        public int getEdgeCount() { return edgeCount; }

        /** Number of vertices in the graph. */
        public int getVertexCount() { return vertexCount; }

        /**
         * Whether the graph is fully connected (MST spans all vertices).
         * A connected graph with N vertices has N-1 MST edges.
         */
        public boolean isConnected() {
            return componentCount <= 1;
        }

        /**
         * Get edge type distribution in the MST.
         *
         * @return map of edge type → count
         */
        public Map<String, Integer> getEdgeTypeDistribution() {
            Map<String, Integer> dist = new LinkedHashMap<String, Integer>();
            for (edge e : edges) {
                String type = e.getType();
                if (type == null) type = "unknown";
                Integer count = dist.get(type);
                dist.put(type, count == null ? 1 : count + 1);
            }
            return dist;
        }

        /**
         * Get a human-readable summary of the MST.
         */
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("MST: %d edges, total weight=%.1f", edgeCount, totalWeight));
            if (componentCount > 1) {
                sb.append(String.format(" (%d components — forest)", componentCount));
            } else {
                sb.append(" (connected)");
            }
            return sb.toString();
        }

        /**
         * Get the heaviest edge in the MST (bottleneck).
         *
         * @return the heaviest edge, or null if MST is empty
         */
        public edge getHeaviestEdge() {
            edge heaviest = null;
            for (edge e : edges) {
                if (heaviest == null || e.getWeight() > heaviest.getWeight()) {
                    heaviest = e;
                }
            }
            return heaviest;
        }

        /**
         * Get the lightest edge in the MST.
         *
         * @return the lightest edge, or null if MST is empty
         */
        public edge getLightestEdge() {
            edge lightest = null;
            for (edge e : edges) {
                if (lightest == null || e.getWeight() < lightest.getWeight()) {
                    lightest = e;
                }
            }
            return lightest;
        }

        /**
         * Get the average edge weight in the MST.
         *
         * @return average weight, or 0 if no edges
         */
        public float getAverageWeight() {
            if (edgeCount == 0) return 0.0f;
            return totalWeight / edgeCount;
        }
    }

    /**
     * A single connected component within the MST forest.
     */
    public static class MSTComponent {
        private final int id;
        private final List<String> vertices;
        private final List<edge> edges;
        private final float totalWeight;

        MSTComponent(int id, List<String> vertices, List<edge> edges, float totalWeight) {
            this.id = id;
            this.vertices = Collections.unmodifiableList(vertices);
            this.edges = Collections.unmodifiableList(edges);
            this.totalWeight = totalWeight;
        }

        /** Component ID. */
        public int getId() { return id; }

        /** Vertices in this component. */
        public List<String> getVertices() { return vertices; }

        /** MST edges in this component. */
        public List<edge> getEdges() { return edges; }

        /** Total weight of MST edges in this component. */
        public float getTotalWeight() { return totalWeight; }

        /** Number of vertices. */
        public int getSize() { return vertices.size(); }

        /**
         * Get the dominant edge type in this component's MST.
         *
         * @return the most common edge type, or null if empty
         */
        public String getDominantType() {
            Map<String, Integer> counts = new HashMap<String, Integer>();
            for (edge e : edges) {
                String type = e.getType();
                if (type == null) type = "unknown";
                Integer c = counts.get(type);
                counts.put(type, c == null ? 1 : c + 1);
            }
            String dominant = null;
            int maxCount = 0;
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (entry.getValue() > maxCount) {
                    maxCount = entry.getValue();
                    dominant = entry.getKey();
                }
            }
            return dominant;
        }
    }
}
