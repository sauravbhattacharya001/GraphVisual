package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.util.*;

/**
 * Comprehensive tree analysis for undirected graphs.
 *
 * <p>Provides tree detection, structural analysis, encoding, and comparison
 * algorithms for graph-theoretic trees (connected acyclic graphs).</p>
 *
 * <h3>Algorithms</h3>
 * <ul>
 *   <li><b>Tree detection:</b> V-1 edges + connectivity check — O(V + E)</li>
 *   <li><b>Center:</b> Iterative leaf removal (peeling) — O(V)</li>
 *   <li><b>Centroid:</b> Subtree size DFS with minimum max-component — O(V)</li>
 *   <li><b>Prüfer sequence:</b> Labeled tree ↔ sequence bijection — O(V log V)</li>
 *   <li><b>LCA:</b> Euler tour + sparse table — O(V) build, O(1) query</li>
 *   <li><b>Tree isomorphism:</b> AHU canonical form via rooted subtree hashing — O(V)</li>
 *   <li><b>Diameter:</b> Double BFS — O(V)</li>
 *   <li><b>Height/depth:</b> BFS from root — O(V)</li>
 * </ul>
 *
 * <h3>Applications</h3>
 * <ul>
 *   <li>Network topology analysis (spanning tree properties)</li>
 *   <li>Phylogenetic tree comparison in bioinformatics</li>
 *   <li>Hierarchical clustering structure analysis</li>
 *   <li>Minimum facility location (center/centroid)</li>
 *   <li>Labeled tree enumeration (Cayley's formula via Prüfer)</li>
 * </ul>
 *
 * @author zalenix
 */
public class TreeAnalyzer {

    private final Graph<String, Edge> graph;

    /**
     * Create a new tree analyzer for the given graph.
     *
     * @param graph the JUNG graph to analyze (should be undirected)
     * @throws IllegalArgumentException if graph is null
     */
    public TreeAnalyzer(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
    }

    // ── Result types ──────────────────────────────────────────

    /** Result of tree detection check. */
    public static class TreeCheck {
        public final boolean isTree;
        public final boolean isForest;
        public final int vertexCount;
        public final int edgeCount;
        public final int componentCount;
        public final String reason;

        public TreeCheck(boolean isTree, boolean isForest, int vertexCount,
                         int edgeCount, int componentCount, String reason) {
            this.isTree = isTree;
            this.isForest = isForest;
            this.vertexCount = vertexCount;
            this.edgeCount = edgeCount;
            this.componentCount = componentCount;
            this.reason = reason;
        }
    }

    /** Tree center result (1 or 2 center vertices). */
    public static class CenterResult {
        public final List<String> centerVertices;
        public final int radius;

        public CenterResult(List<String> centerVertices, int radius) {
            this.centerVertices = Collections.unmodifiableList(centerVertices);
            this.radius = radius;
        }
    }

    /** Tree centroid result (1 or 2 centroid vertices). */
    public static class CentroidResult {
        public final List<String> centroidVertices;
        public final int maxSubtreeSize;

        public CentroidResult(List<String> centroidVertices, int maxSubtreeSize) {
            this.centroidVertices = Collections.unmodifiableList(centroidVertices);
            this.maxSubtreeSize = maxSubtreeSize;
        }
    }

    /** Rooted tree information. */
    public static class RootedTreeInfo {
        public final String root;
        public final Map<String, String> parent;
        public final Map<String, Integer> depth;
        public final Map<String, Integer> subtreeSize;
        public final int height;
        public final List<String> leaves;

        public RootedTreeInfo(String root, Map<String, String> parent,
                              Map<String, Integer> depth, Map<String, Integer> subtreeSize,
                              int height, List<String> leaves) {
            this.root = root;
            this.parent = Collections.unmodifiableMap(parent);
            this.depth = Collections.unmodifiableMap(depth);
            this.subtreeSize = Collections.unmodifiableMap(subtreeSize);
            this.height = height;
            this.leaves = Collections.unmodifiableList(leaves);
        }
    }

    /** Diameter result with the path. */
    public static class DiameterResult {
        public final int diameter;
        public final List<String> path;

        public DiameterResult(int diameter, List<String> path) {
            this.diameter = diameter;
            this.path = Collections.unmodifiableList(path);
        }
    }

    /** LCA query engine with O(1) queries after O(V) preprocessing. */
    public static class LCAEngine {
        private final Map<String, Integer> firstOccurrence;
        private final List<String> eulerTour;
        private final List<Integer> eulerDepths;
        private final int[][] sparseTable;
        private final int[] log2;

        LCAEngine(Map<String, Integer> firstOccurrence, List<String> eulerTour,
                  List<Integer> eulerDepths) {
            this.firstOccurrence = firstOccurrence;
            this.eulerTour = eulerTour;
            this.eulerDepths = eulerDepths;

            int n = eulerDepths.size();
            this.log2 = new int[n + 1];
            for (int i = 2; i <= n; i++) {
                log2[i] = log2[i / 2] + 1;
            }

            int k = log2[n] + 1;
            sparseTable = new int[k][n];
            for (int i = 0; i < n; i++) {
                sparseTable[0][i] = i;
            }
            for (int j = 1; j < k; j++) {
                for (int i = 0; i + (1 << j) - 1 < n; i++) {
                    int left = sparseTable[j - 1][i];
                    int right = sparseTable[j - 1][i + (1 << (j - 1))];
                    sparseTable[j][i] = eulerDepths.get(left) <= eulerDepths.get(right) ? left : right;
                }
            }
        }

        /**
         * Find the lowest common ancestor of two vertices.
         *
         * @param u first vertex
         * @param v second vertex
         * @return the LCA vertex
         * @throws IllegalArgumentException if either vertex is not in the tree
         */
        public String query(String u, String v) {
            if (!firstOccurrence.containsKey(u)) {
                throw new IllegalArgumentException("Vertex not in tree: " + u);
            }
            if (!firstOccurrence.containsKey(v)) {
                throw new IllegalArgumentException("Vertex not in tree: " + v);
            }
            int l = firstOccurrence.get(u);
            int r = firstOccurrence.get(v);
            if (l > r) { int tmp = l; l = r; r = tmp; }

            int j = log2[r - l + 1];
            int left = sparseTable[j][l];
            int right = sparseTable[j][r - (1 << j) + 1];
            int minIdx = eulerDepths.get(left) <= eulerDepths.get(right) ? left : right;
            return eulerTour.get(minIdx);
        }

        /**
         * Compute the distance between two vertices via their LCA.
         *
         * @param u first vertex
         * @param v second vertex
         * @return distance (number of edges) between u and v
         */
        public int distance(String u, String v) {
            if (!firstOccurrence.containsKey(u) || !firstOccurrence.containsKey(v)) {
                throw new IllegalArgumentException("Vertex not in tree");
            }
            String lca = query(u, v);
            return eulerDepths.get(firstOccurrence.get(u))
                 + eulerDepths.get(firstOccurrence.get(v))
                 - 2 * eulerDepths.get(firstOccurrence.get(lca));
        }
    }

    /** Comprehensive tree analysis report. */
    public static class TreeReport {
        public final TreeCheck treeCheck;
        public final CenterResult center;
        public final CentroidResult centroid;
        public final DiameterResult diameter;
        public final RootedTreeInfo rootedInfo;
        public final Map<Integer, Integer> degreeDistribution;

        public TreeReport(TreeCheck treeCheck, CenterResult center,
                          CentroidResult centroid, DiameterResult diameter,
                          RootedTreeInfo rootedInfo, Map<Integer, Integer> degreeDistribution) {
            this.treeCheck = treeCheck;
            this.center = center;
            this.centroid = centroid;
            this.diameter = diameter;
            this.rootedInfo = rootedInfo;
            this.degreeDistribution = Collections.unmodifiableMap(degreeDistribution);
        }

        /** Generate a human-readable text summary. */
        public String toText() {
            StringBuilder sb = new StringBuilder();
            sb.append("═══ Tree Analysis Report ═══\n\n");

            sb.append("Structure:\n");
            sb.append(String.format("  Vertices: %d\n", treeCheck.vertexCount));
            sb.append(String.format("  Edges: %d\n", treeCheck.edgeCount));
            sb.append(String.format("  Is tree: %s\n", treeCheck.isTree));
            sb.append(String.format("  Is forest: %s\n", treeCheck.isForest));
            sb.append(String.format("  Components: %d\n", treeCheck.componentCount));
            sb.append('\n');

            if (center != null) {
                sb.append(String.format("Center: %s (radius=%d)\n", center.centerVertices, center.radius));
            }
            if (centroid != null) {
                sb.append(String.format("Centroid: %s (max subtree=%d)\n",
                        centroid.centroidVertices, centroid.maxSubtreeSize));
            }
            if (diameter != null) {
                sb.append(String.format("Diameter: %d (path: %s)\n", diameter.diameter, diameter.path));
            }
            if (rootedInfo != null) {
                sb.append(String.format("Height: %d (rooted at %s)\n", rootedInfo.height, rootedInfo.root));
                sb.append(String.format("Leaves: %d %s\n", rootedInfo.leaves.size(), rootedInfo.leaves));
            }
            sb.append('\n');
            sb.append("Degree distribution:\n");
            TreeMap<Integer, Integer> sorted = new TreeMap<>(degreeDistribution);
            for (Map.Entry<Integer, Integer> entry : sorted.entrySet()) {
                sb.append(String.format("  degree %d: %d vertices\n", entry.getKey(), entry.getValue()));
            }
            return sb.toString();
        }
    }

    // ── Tree detection ────────────────────────────────────────

    /**
     * Check whether the graph is a tree (connected acyclic graph).
     * Also detects forests (acyclic but disconnected).
     *
     * @return TreeCheck with detection results
     */
    public TreeCheck checkTree() {
        int v = graph.getVertexCount();
        int e = graph.getEdgeCount();

        if (v == 0) {
            return new TreeCheck(false, true, 0, 0, 0, "Empty graph (trivially a forest)");
        }

        int components = countComponents();

        if (e != v - components) {
            // Has cycles
            return new TreeCheck(false, false, v, e, components,
                    "Graph has cycles (E != V - components)");
        }

        if (components == 1) {
            return new TreeCheck(true, true, v, e, 1, "Connected acyclic graph");
        }

        return new TreeCheck(false, true, v, e, components,
                String.format("Forest with %d trees", components));
    }

    // ── Center (eccentricity-based) ───────────────────────────

    /**
     * Find the center of the tree — vertices with minimum eccentricity.
     * Uses iterative leaf removal (peeling). A tree has 1 or 2 center vertices.
     *
     * @return CenterResult with center vertices and radius
     * @throws IllegalStateException if graph is not a tree
     */
    public CenterResult findCenter() {
        requireTree();
        int n = graph.getVertexCount();
        if (n == 1) {
            String v = graph.getVertices().iterator().next();
            return new CenterResult(Collections.singletonList(v), 0);
        }

        // Iterative leaf peeling
        Map<String, Integer> degree = new HashMap<>();
        Queue<String> leaves = new LinkedList<>();
        Set<String> remaining = new HashSet<>(graph.getVertices());

        for (String v : graph.getVertices()) {
            int d = graph.getNeighborCount(v);
            degree.put(v, d);
            if (d <= 1) {
                leaves.add(v);
            }
        }

        int radius = 0;
        while (remaining.size() > 2) {
            Queue<String> nextLeaves = new LinkedList<>();
            for (String leaf : leaves) {
                remaining.remove(leaf);
                for (String neighbor : graph.getNeighbors(leaf)) {
                    if (remaining.contains(neighbor)) {
                        int nd = degree.get(neighbor) - 1;
                        degree.put(neighbor, nd);
                        if (nd == 1) {
                            nextLeaves.add(neighbor);
                        }
                    }
                }
            }
            leaves = nextLeaves;
            radius++;
        }

        List<String> centerVertices = new ArrayList<>(remaining);
        Collections.sort(centerVertices);
        return new CenterResult(centerVertices, radius);
    }

    // ── Centroid (subtree-size-based) ─────────────────────────

    /**
     * Find the centroid — vertices where the maximum subtree size when
     * the vertex is removed is minimized. A tree has 1 or 2 centroids.
     *
     * @return CentroidResult with centroid vertices
     * @throws IllegalStateException if graph is not a tree
     */
    public CentroidResult findCentroid() {
        requireTree();
        int n = graph.getVertexCount();
        if (n == 1) {
            String v = graph.getVertices().iterator().next();
            return new CentroidResult(Collections.singletonList(v), 0);
        }

        // Pick arbitrary root and compute subtree sizes
        String root = graph.getVertices().iterator().next();
        Map<String, Integer> subtreeSize = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        computeSubtreeSizes(root, subtreeSize, parent);

        int bestMax = n;
        List<String> centroids = new ArrayList<>();

        for (String v : graph.getVertices()) {
            int maxComp = n - subtreeSize.get(v); // "above" component
            for (String neighbor : graph.getNeighbors(v)) {
                if (!neighbor.equals(parent.get(v))) {
                    maxComp = Math.max(maxComp, subtreeSize.get(neighbor));
                }
            }
            if (maxComp < bestMax) {
                bestMax = maxComp;
                centroids.clear();
                centroids.add(v);
            } else if (maxComp == bestMax) {
                centroids.add(v);
            }
        }

        Collections.sort(centroids);
        return new CentroidResult(centroids, bestMax);
    }

    // ── Diameter ──────────────────────────────────────────────

    /**
     * Find the diameter of the tree (longest path) using double BFS.
     *
     * @return DiameterResult with diameter length and the actual path
     * @throws IllegalStateException if graph is not a tree
     */
    public DiameterResult findDiameter() {
        requireTree();
        int n = graph.getVertexCount();
        if (n == 1) {
            String v = graph.getVertices().iterator().next();
            return new DiameterResult(0, Collections.singletonList(v));
        }

        // First BFS from arbitrary vertex to find farthest
        String start = graph.getVertices().iterator().next();
        BFSResult bfs1 = bfs(start);
        String u = bfs1.farthest;

        // Second BFS from farthest to find diameter endpoint
        BFSResult bfs2 = bfs(u);
        String v = bfs2.farthest;

        // Reconstruct path
        List<String> path = reconstructPath(bfs2.parent, u, v);
        // Normalize so path starts with lexicographically smaller endpoint
        if (path.size() >= 2 && path.get(0).compareTo(path.get(path.size() - 1)) > 0) {
            Collections.reverse(path);
        }
        return new DiameterResult(bfs2.dist.get(v), path);
    }

    // ── Rooted tree info ──────────────────────────────────────

    /**
     * Root the tree at the given vertex and compute depth, subtree sizes, etc.
     *
     * @param root the root vertex
     * @return RootedTreeInfo with all computed properties
     * @throws IllegalStateException if graph is not a tree
     * @throws IllegalArgumentException if root is not in the tree
     */
    public RootedTreeInfo rootAt(String root) {
        requireTree();
        if (!graph.containsVertex(root)) {
            throw new IllegalArgumentException("Root vertex not in graph: " + root);
        }

        Map<String, String> parent = new HashMap<>();
        Map<String, Integer> depth = new HashMap<>();
        Map<String, Integer> subtreeSize = new HashMap<>();
        List<String> leaves = new ArrayList<>();

        parent.put(root, null);
        depth.put(root, 0);

        // BFS for depth
        Queue<String> queue = new LinkedList<>();
        queue.add(root);
        List<String> order = new ArrayList<>();

        while (!queue.isEmpty()) {
            String v = queue.poll();
            order.add(v);
            for (String neighbor : graph.getNeighbors(v)) {
                if (!depth.containsKey(neighbor)) {
                    parent.put(neighbor, v);
                    depth.put(neighbor, depth.get(v) + 1);
                    queue.add(neighbor);
                }
            }
        }

        // Compute subtree sizes bottom-up
        for (String v : graph.getVertices()) {
            subtreeSize.put(v, 1);
        }
        for (int i = order.size() - 1; i >= 0; i--) {
            String v = order.get(i);
            String p = parent.get(v);
            if (p != null) {
                subtreeSize.put(p, subtreeSize.get(p) + subtreeSize.get(v));
            }
        }

        int height = 0;
        for (String v : graph.getVertices()) {
            if (graph.getNeighborCount(v) == 1 && !v.equals(root) || graph.getVertexCount() == 1) {
                leaves.add(v);
            }
            height = Math.max(height, depth.get(v));
        }
        Collections.sort(leaves);

        return new RootedTreeInfo(root, parent, depth, subtreeSize, height, leaves);
    }

    // ── Prüfer sequence ───────────────────────────────────────

    /**
     * Encode the labeled tree as a Prüfer sequence.
     * Vertices must be comparable (sorted lexicographically).
     *
     * @return the Prüfer sequence (length V-2)
     * @throws IllegalStateException if graph is not a tree or has fewer than 3 vertices
     */
    public List<String> encodePrufer() {
        requireTree();
        int n = graph.getVertexCount();
        if (n < 3) {
            return Collections.emptyList();
        }

        // Work with a copy of degree info
        Map<String, Integer> degree = new HashMap<>();
        for (String v : graph.getVertices()) {
            degree.put(v, graph.getNeighborCount(v));
        }

        List<String> vertices = new ArrayList<>(graph.getVertices());
        Collections.sort(vertices);

        Set<String> removed = new HashSet<>();
        List<String> sequence = new ArrayList<>();

        // Build adjacency for efficient neighbor lookup
        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(graph);

        for (int i = 0; i < n - 2; i++) {
            // Find smallest leaf
            String leaf = null;
            for (String v : vertices) {
                if (!removed.contains(v) && degree.get(v) == 1) {
                    leaf = v;
                    break;
                }
            }

            // Find its neighbor
            String neighbor = null;
            for (String nb : adj.get(leaf)) {
                if (!removed.contains(nb)) {
                    neighbor = nb;
                    break;
                }
            }

            sequence.add(neighbor);
            removed.add(leaf);
            degree.put(neighbor, degree.get(neighbor) - 1);
        }

        return sequence;
    }

    /**
     * Decode a Prüfer sequence back into a tree (as Edge list).
     *
     * @param sequence the Prüfer sequence
     * @param vertices the full set of vertex labels
     * @return list of edges as [vertex1, vertex2] pairs
     * @throws IllegalArgumentException if sequence length != vertices.size() - 2
     */
    public static List<String[]> decodePrufer(List<String> sequence, List<String> vertices) {
        if (vertices == null || vertices.size() < 2) {
            throw new IllegalArgumentException("Need at least 2 vertices");
        }
        if (sequence.size() != vertices.size() - 2) {
            throw new IllegalArgumentException("Prüfer sequence length must be V-2");
        }

        Map<String, Integer> degree = new HashMap<>();
        for (String v : vertices) {
            degree.put(v, 1);
        }
        for (String s : sequence) {
            degree.put(s, degree.get(s) + 1);
        }

        List<String> sortedVertices = new ArrayList<>(vertices);
        Collections.sort(sortedVertices);

        List<String[]> edges = new ArrayList<>();
        Set<String> used = new HashSet<>();

        for (String s : sequence) {
            // Find smallest leaf
            for (String v : sortedVertices) {
                if (!used.contains(v) && degree.get(v) == 1) {
                    edges.add(new String[]{v, s});
                    degree.put(v, degree.get(v) - 1);
                    degree.put(s, degree.get(s) - 1);
                    used.add(v);
                    break;
                }
            }
        }

        // Last edge connects the two remaining vertices
        List<String> remaining = new ArrayList<>();
        for (String v : sortedVertices) {
            if (!used.contains(v)) {
                remaining.add(v);
            }
        }
        if (remaining.size() == 2) {
            edges.add(new String[]{remaining.get(0), remaining.get(1)});
        }

        return edges;
    }

    // ── LCA (Lowest Common Ancestor) ─────────────────────────

    /**
     * Build an LCA engine rooted at the given vertex.
     * Preprocessing is O(V), queries are O(1).
     *
     * @param root the root vertex
     * @return LCAEngine for O(1) ancestor queries
     * @throws IllegalStateException if graph is not a tree
     */
    public LCAEngine buildLCA(String root) {
        requireTree();
        if (!graph.containsVertex(root)) {
            throw new IllegalArgumentException("Root vertex not in graph: " + root);
        }

        List<String> eulerTour = new ArrayList<>();
        List<Integer> eulerDepths = new ArrayList<>();
        Map<String, Integer> firstOccurrence = new HashMap<>();

        // Iterative Euler tour using explicit stack
        Deque<String[]> stack = new ArrayDeque<>(); // [vertex, parent]
        Map<String, Iterator<String>> iterators = new HashMap<>();
        Map<String, Integer> depthMap = new HashMap<>();

        depthMap.put(root, 0);
        stack.push(new String[]{root, null});

        while (!stack.isEmpty()) {
            String[] top = stack.peek();
            String v = top[0];
            String par = top[1];

            if (!iterators.containsKey(v)) {
                // First visit
                iterators.put(v, graph.getNeighbors(v).iterator());
                firstOccurrence.put(v, eulerTour.size());
                eulerTour.add(v);
                eulerDepths.add(depthMap.get(v));
            }

            Iterator<String> it = iterators.get(v);
            boolean foundChild = false;
            while (it.hasNext()) {
                String child = it.next();
                if (!child.equals(par)) {
                    depthMap.put(child, depthMap.get(v) + 1);
                    stack.push(new String[]{child, v});
                    foundChild = true;
                    break;
                }
            }

            if (!foundChild) {
                stack.pop();
                if (!stack.isEmpty()) {
                    String parent = stack.peek()[0];
                    eulerTour.add(parent);
                    eulerDepths.add(depthMap.get(parent));
                }
            }
        }

        return new LCAEngine(firstOccurrence, eulerTour, eulerDepths);
    }

    // ── Tree isomorphism ──────────────────────────────────────

    /**
     * Compute canonical form of the tree for isomorphism testing.
     * Two trees are isomorphic iff their canonical forms are equal.
     * Uses AHU algorithm (rooted at center).
     *
     * @return canonical string representation
     * @throws IllegalStateException if graph is not a tree
     */
    public String canonicalForm() {
        requireTree();
        int n = graph.getVertexCount();
        if (n == 0) return "()";
        if (n == 1) return "()";

        CenterResult center = findCenter();

        if (center.centerVertices.size() == 1) {
            return canonicalRooted(center.centerVertices.get(0));
        } else {
            // Two centers — try both, pick lexicographically smaller
            String c1 = canonicalRooted(center.centerVertices.get(0));
            String c2 = canonicalRooted(center.centerVertices.get(1));
            return c1.compareTo(c2) <= 0 ? c1 : c2;
        }
    }

    /**
     * Check if this tree is isomorphic to another.
     *
     * @param other the other tree analyzer
     * @return true if the trees are isomorphic
     */
    public boolean isIsomorphicTo(TreeAnalyzer other) {
        if (other == null) return false;
        if (graph.getVertexCount() != other.graph.getVertexCount()) return false;
        return canonicalForm().equals(other.canonicalForm());
    }

    // ── Degree distribution ───────────────────────────────────

    /**
     * Compute degree distribution of the tree.
     *
     * @return map from degree to count of vertices with that degree
     */
    public Map<String, Integer> vertexDegrees() {
        Map<String, Integer> degrees = new HashMap<>();
        for (String v : graph.getVertices()) {
            degrees.put(v, graph.getNeighborCount(v));
        }
        return degrees;
    }

    /**
     * Get degree frequency distribution.
     *
     * @return map from degree to number of vertices with that degree
     */
    public Map<Integer, Integer> degreeDistribution() {
        Map<Integer, Integer> dist = new HashMap<>();
        for (String v : graph.getVertices()) {
            int d = graph.getNeighborCount(v);
            dist.put(d, dist.getOrDefault(d, 0) + 1);
        }
        return dist;
    }

    // ── Full report ───────────────────────────────────────────

    /**
     * Generate a comprehensive tree analysis report.
     *
     * @return TreeReport with all analysis results, or partial if not a tree
     */
    public TreeReport analyze() {
        TreeCheck check = checkTree();
        CenterResult center = null;
        CentroidResult centroid = null;
        DiameterResult diameter = null;
        RootedTreeInfo rootedInfo = null;

        if (check.isTree) {
            center = findCenter();
            centroid = findCentroid();
            diameter = findDiameter();
            rootedInfo = rootAt(center.centerVertices.get(0));
        }

        return new TreeReport(check, center, centroid, diameter, rootedInfo, degreeDistribution());
    }

    // ── Private helpers ───────────────────────────────────────

    private void requireTree() {
        TreeCheck check = checkTree();
        if (!check.isTree) {
            throw new IllegalStateException("Graph is not a tree: " + check.reason);
        }
    }

    private int countComponents() {
        return GraphUtils.findComponents(graph).size();
    }

    private void computeSubtreeSizes(String root, Map<String, Integer> subtreeSize,
                                      Map<String, String> parent) {
        parent.put(root, null);
        Queue<String> queue = new LinkedList<>();
        queue.add(root);
        List<String> order = new ArrayList<>();

        while (!queue.isEmpty()) {
            String v = queue.poll();
            order.add(v);
            for (String neighbor : graph.getNeighbors(v)) {
                if (!parent.containsKey(neighbor) && !neighbor.equals(root)) {
                    parent.put(neighbor, v);
                    queue.add(neighbor);
                }
            }
        }

        for (String v : graph.getVertices()) {
            subtreeSize.put(v, 1);
        }
        for (int i = order.size() - 1; i >= 0; i--) {
            String v = order.get(i);
            String p = parent.get(v);
            if (p != null) {
                subtreeSize.put(p, subtreeSize.get(p) + subtreeSize.get(v));
            }
        }
    }

    private static class BFSResult {
        final Map<String, Integer> dist;
        final Map<String, String> parent;
        final String farthest;

        BFSResult(Map<String, Integer> dist, Map<String, String> parent, String farthest) {
            this.dist = dist;
            this.parent = parent;
            this.farthest = farthest;
        }
    }

    private BFSResult bfs(String start) {
        Map<String, Integer> dist = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        Queue<String> queue = new LinkedList<>();

        dist.put(start, 0);
        parent.put(start, null);
        queue.add(start);

        String farthest = start;
        int maxDist = 0;

        while (!queue.isEmpty()) {
            String v = queue.poll();
            for (String neighbor : graph.getNeighbors(v)) {
                if (!dist.containsKey(neighbor)) {
                    dist.put(neighbor, dist.get(v) + 1);
                    parent.put(neighbor, v);
                    queue.add(neighbor);
                    if (dist.get(neighbor) > maxDist) {
                        maxDist = dist.get(neighbor);
                        farthest = neighbor;
                    }
                }
            }
        }

        return new BFSResult(dist, parent, farthest);
    }

    private List<String> reconstructPath(Map<String, String> parent, String start, String end) {
        List<String> path = new ArrayList<>();
        String curr = end;
        while (curr != null) {
            path.add(curr);
            curr = parent.get(curr);
        }
        Collections.reverse(path);
        return path;
    }

    private String canonicalRooted(String root) {
        // Iterative post-order canonical form
        Map<String, String> canonical = new HashMap<>();
        Map<String, String> parentMap = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();
        List<String> postOrder = new ArrayList<>();

        parentMap.put(root, null);
        stack.push(root);
        // Build post-order
        Deque<String> tempStack = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        tempStack.push(root);
        visited.add(root);

        while (!tempStack.isEmpty()) {
            String v = tempStack.pop();
            postOrder.add(v);
            List<String> children = new ArrayList<>();
            for (String nb : graph.getNeighbors(v)) {
                if (visited.add(nb)) {
                    parentMap.put(nb, v);
                    children.add(nb);
                }
            }
            for (String child : children) {
                tempStack.push(child);
            }
        }

        Collections.reverse(postOrder);

        for (String v : postOrder) {
            List<String> childCanonicals = new ArrayList<>();
            for (String nb : graph.getNeighbors(v)) {
                if (!nb.equals(parentMap.get(v))) {
                    childCanonicals.add(canonical.get(nb));
                }
            }
            Collections.sort(childCanonicals);
            StringBuilder sb = new StringBuilder("(");
            for (String c : childCanonicals) {
                sb.append(c);
            }
            sb.append(")");
            canonical.put(v, sb.toString());
        }

        return canonical.get(root);
    }
}
