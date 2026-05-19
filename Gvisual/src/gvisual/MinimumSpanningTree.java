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
 *   <li>MST statistics (total weight, Edge count, component count)</li>
 *   <li>Per-component breakdown (vertices, edges, weight)</li>
 *   <li>Edge type distribution within the MST</li>
 *   <li>Programmatic API for all results</li>
 * </ul>
 */
public class MinimumSpanningTree {

    private final Graph<String, Edge> graph;

    /**
     * Create a new MST analyzer for the given graph.
     *
     * @param graph the JUNG graph to analyze (must not be null)
     * @throws IllegalArgumentException if graph is null
     */
    public MinimumSpanningTree(Graph<String, Edge> graph) {
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
                    Collections.<Edge>emptyList(),
                    Collections.<MSTComponent>emptyList(),
                    0, 0.0f, 0, vertexCount);
        }

        // Intern vertices to dense integer ids once. This lets Union-Find
        // operate on int[] arrays instead of paying the per-call cost of
        // HashMap<String,String> lookups + String.equals chains in the
        // path-compression loop. For graphs with V vertices and E edges,
        // this drops the Kruskal merge phase from ~6 hashmap ops + 2
        // String.equals per edge to a handful of array reads.
        String[] idToVertex = new String[vertexCount];
        HashMap<String, Integer> vertexToId = new HashMap<>(vertexCount * 2);
        int idx = 0;
        for (String vertex : vertices) {
            vertexToId.put(vertex, idx);
            idToVertex[idx] = vertex;
            idx++;
        }

        // Collect distinct edges. The previous implementation used a
        // HashSet<Edge> probe per edge; JUNG's getEdges() is already a
        // Collection view of distinct edges, but we keep the dedupe via
        // the LinkedHashSet ctor — it's cheaper than a separate contains+add.
        Collection<Edge> edgeView = graph.getEdges();
        ArrayList<Edge> sortedEdges = new ArrayList<>(
                edgeView instanceof Set ? edgeView : new LinkedHashSet<>(edgeView));
        sortedEdges.sort((Edge a, Edge b) -> Float.compare(a.getWeight(), b.getWeight()));

        // Union-Find on int ids.
        UnionFind uf = new UnionFind(vertexCount);

        ArrayList<Edge> mstEdges = new ArrayList<>(Math.min(sortedEdges.size(), vertexCount));
        float totalWeight = 0.0f;

        for (Edge e : sortedEdges) {
            Integer uIdBoxed = vertexToId.get(e.getVertex1());
            Integer vIdBoxed = vertexToId.get(e.getVertex2());
            // Defensive: skip edges that reference vertices the graph no
            // longer reports. This preserves the previous behavior of
            // "sortedEdges only contains JUNG-managed edges".
            if (uIdBoxed == null || vIdBoxed == null) continue;
            int uId = uIdBoxed;
            int vId = vIdBoxed;
            if (uf.union(uId, vId)) {
                mstEdges.add(e);
                totalWeight += e.getWeight();
                if (mstEdges.size() == vertexCount - 1) {
                    break; // MST is complete; no need to inspect heavier edges
                }
            }
        }

        // Build per-component breakdown indexed by canonical root id.
        List<List<String>> rootToVertices = new ArrayList<>();
        List<List<Edge>> rootToEdges = new ArrayList<>();
        int[] rootToCompId = new int[vertexCount];
        Arrays.fill(rootToCompId, -1);

        for (int i = 0; i < vertexCount; i++) {
            int root = uf.find(i);
            int compId = rootToCompId[root];
            if (compId == -1) {
                compId = rootToVertices.size();
                rootToCompId[root] = compId;
                rootToVertices.add(new ArrayList<>());
                rootToEdges.add(new ArrayList<>());
            }
            rootToVertices.get(compId).add(idToVertex[i]);
        }

        for (Edge e : mstEdges) {
            Integer endpointId = vertexToId.get(e.getVertex1());
            if (endpointId == null) continue;
            int compId = rootToCompId[uf.find(endpointId)];
            rootToEdges.get(compId).add(e);
        }

        // Sort components by size descending for consistent output. We
        // package each component's vertices+edges together so we don't
        // have to look them up again after sorting.
        int componentCount = rootToVertices.size();
        Integer[] order = new Integer[componentCount];
        for (int i = 0; i < componentCount; i++) order[i] = i;
        final List<List<String>> rtv = rootToVertices;
        Arrays.sort(order, (Integer a, Integer b) ->
                Integer.compare(rtv.get(b).size(), rtv.get(a).size()));

        List<MSTComponent> components = new ArrayList<>(componentCount);
        for (int outId = 0; outId < componentCount; outId++) {
            int srcId = order[outId];
            List<String> members = rootToVertices.get(srcId);
            List<Edge> compEdges = rootToEdges.get(srcId);
            float compWeight = 0.0f;
            for (Edge e : compEdges) {
                compWeight += e.getWeight();
            }
            components.add(new MSTComponent(outId, members, compEdges, compWeight));
        }

        return new MSTResult(mstEdges, components, componentCount, totalWeight, mstEdges.size(), vertexCount);
    }

    // ==========================================
    //  Union-Find (Disjoint Set) with path
    //  compression and union by rank
    // ==========================================

    /**
     * Disjoint set data structure for Kruskal's algorithm.
     *
     * <p>Indexed by dense integer vertex ids. The previous implementation
     * used {@code Map<String,String>} parent/rank tables, which paid the
     * cost of a HashMap lookup and a {@link String#equals(Object)} chain
     * on every step of the path-compression loop. The current
     * implementation uses two {@code int[]} arrays, giving constant-time
     * array reads in the hot loop and substantially less allocation
     * (no boxed {@link Integer} for the rank table).</p>
     */
    static class UnionFind {
        private final int[] parent;
        private final byte[] rank; // tree height is bounded by log2(V); byte is plenty

        UnionFind(int n) {
            parent = new int[n];
            rank = new byte[n];
            for (int i = 0; i < n; i++) {
                parent[i] = i;
            }
        }

        /**
         * Find with iterative path compression (two-pass, no recursion).
         */
        int find(int x) {
            int root = x;
            while (parent[root] != root) {
                root = parent[root];
            }
            // Path compression: point every node on the path directly at the root.
            int current = x;
            while (parent[current] != root) {
                int next = parent[current];
                parent[current] = root;
                current = next;
            }
            return root;
        }

        /**
         * Union by rank.
         *
         * @return true if the two elements were in distinct components
         *         and have been merged; false if they were already in the
         *         same component.
         */
        boolean union(int a, int b) {
            int rootA = find(a);
            int rootB = find(b);
            if (rootA == rootB) return false;

            int rankA = rank[rootA];
            int rankB = rank[rootB];

            if (rankA < rankB) {
                parent[rootA] = rootB;
            } else if (rankA > rankB) {
                parent[rootB] = rootA;
            } else {
                parent[rootB] = rootA;
                rank[rootA] = (byte) (rankA + 1);
            }
            return true;
        }
    }

    // ==========================================
    //  Result classes
    // ==========================================

    /**
     * Complete MST computation result.
     */
    public static class MSTResult {
        private final List<Edge> edges;
        private final List<MSTComponent> components;
        private final int componentCount;
        private final float totalWeight;
        private final int edgeCount;
        private final int vertexCount;

        MSTResult(List<Edge> edges, List<MSTComponent> components,
                  int componentCount, float totalWeight, int edgeCount, int vertexCount) {
            this.edges = Collections.unmodifiableList(edges);
            this.components = Collections.unmodifiableList(components);
            this.componentCount = componentCount;
            this.totalWeight = totalWeight;
            this.edgeCount = edgeCount;
            this.vertexCount = vertexCount;
        }

        /** All MST edges. */
        public List<Edge> getEdges() { return edges; }

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
         * Get Edge type distribution in the MST.
         *
         * @return map of Edge type → count
         */
        public Map<String, Integer> getEdgeTypeDistribution() {
            Map<String, Integer> dist = new LinkedHashMap<String, Integer>();
            for (Edge e : edges) {
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
         * Get the heaviest Edge in the MST (bottleneck).
         *
         * @return the heaviest Edge, or null if MST is empty
         */
        public Edge getHeaviestEdge() {
            Edge heaviest = null;
            for (Edge e : edges) {
                if (heaviest == null || e.getWeight() > heaviest.getWeight()) {
                    heaviest = e;
                }
            }
            return heaviest;
        }

        /**
         * Get the lightest Edge in the MST.
         *
         * @return the lightest Edge, or null if MST is empty
         */
        public Edge getLightestEdge() {
            Edge lightest = null;
            for (Edge e : edges) {
                if (lightest == null || e.getWeight() < lightest.getWeight()) {
                    lightest = e;
                }
            }
            return lightest;
        }

        /**
         * Get the average Edge weight in the MST.
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
        private final List<Edge> edges;
        private final float totalWeight;

        MSTComponent(int id, List<String> vertices, List<Edge> edges, float totalWeight) {
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
        public List<Edge> getEdges() { return edges; }

        /** Total weight of MST edges in this component. */
        public float getTotalWeight() { return totalWeight; }

        /** Number of vertices. */
        public int getSize() { return vertices.size(); }

        /**
         * Get the dominant Edge type in this component's MST.
         *
         * @return the most common Edge type, or null if empty
         */
        public String getDominantType() {
            Map<String, Integer> counts = new HashMap<String, Integer>();
            for (Edge e : edges) {
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
