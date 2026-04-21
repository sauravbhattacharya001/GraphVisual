package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.util.*;

/**
 * Performs topological sorting on directed graphs with full cycle
 * detection, dependency analysis, and critical path computation.
 *
 * <p><b>Topological sort</b> produces a linear ordering of vertices such
 * that for every directed Edge (u → v), vertex u comes before v. This
 * is only possible on <b>Directed Acyclic Graphs (DAGs)</b>.</p>
 *
 * <p>Use cases:</p>
 * <ul>
 *   <li>Build system dependency ordering (Makefile, Gradle, Maven)</li>
 *   <li>Course prerequisite scheduling</li>
 *   <li>Task scheduling with dependencies</li>
 *   <li>Data pipeline execution order</li>
 *   <li>Package installation order</li>
 * </ul>
 *
 * <p>When cycles are detected, the analyzer identifies all vertices
 * participating in cycles and reports the back edges that cause them.</p>
 *
 * <p>Algorithms: Kahn's algorithm (BFS-based) for the primary sort,
 * DFS-based traversal for cycle detection and back-Edge reporting.</p>
 *
 * @author zalenix
 */
public class TopologicalSortAnalyzer {

    private final Graph<String, Edge> graph;

    /** Lazily-computed directed adjacency — avoids rebuilding O(V+E) per method call. */
    private GraphUtils.DirectedAdj cachedAdj;

    /** Lazily-computed full analysis result — reused by generateSummary, countChoicePoints, analyzeDependencies. */
    private TopologicalSortResult cachedResult;

    /**
     * Create a new analyzer for the given graph.
     *
     * @param graph the JUNG graph to analyze (must not be null)
     * @throws IllegalArgumentException if graph is null
     */
    public TopologicalSortAnalyzer(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
    }

    // ── Result classes ──────────────────────────────────────────

    /**
     * Complete result of a topological sort analysis.
     */
    public static class TopologicalSortResult {
        private final boolean isDAG;
        private final List<String> sortedOrder;
        private final List<CycleInfo> cycles;
        private final Map<String, Integer> depthMap;
        private final Map<String, Integer> dependencyCount;
        private final Map<String, Integer> dependentCount;
        private final List<String> roots;
        private final List<String> leaves;
        private final int longestPathLength;
        private final List<String> criticalPath;

        public TopologicalSortResult(boolean isDAG, List<String> sortedOrder,
                                      List<CycleInfo> cycles, Map<String, Integer> depthMap,
                                      Map<String, Integer> dependencyCount,
                                      Map<String, Integer> dependentCount,
                                      List<String> roots, List<String> leaves,
                                      int longestPathLength, List<String> criticalPath) {
            this.isDAG = isDAG;
            this.sortedOrder = Collections.unmodifiableList(new ArrayList<String>(sortedOrder));
            this.cycles = Collections.unmodifiableList(new ArrayList<CycleInfo>(cycles));
            this.depthMap = Collections.unmodifiableMap(new HashMap<String, Integer>(depthMap));
            this.dependencyCount = Collections.unmodifiableMap(new HashMap<String, Integer>(dependencyCount));
            this.dependentCount = Collections.unmodifiableMap(new HashMap<String, Integer>(dependentCount));
            this.roots = Collections.unmodifiableList(new ArrayList<String>(roots));
            this.leaves = Collections.unmodifiableList(new ArrayList<String>(leaves));
            this.longestPathLength = longestPathLength;
            this.criticalPath = Collections.unmodifiableList(new ArrayList<String>(criticalPath));
        }

        /** True if the graph is a DAG (no cycles). */
        public boolean isDAG() { return isDAG; }

        /** Topologically sorted vertex order. Empty if graph has cycles. */
        public List<String> getSortedOrder() { return sortedOrder; }

        /** Detected cycles (empty if DAG). */
        public List<CycleInfo> getCycles() { return cycles; }

        /** Depth of each vertex (longest path from any root). */
        public Map<String, Integer> getDepthMap() { return depthMap; }

        /** Number of direct predecessors (in-degree in the directed interpretation). */
        public Map<String, Integer> getDependencyCount() { return dependencyCount; }

        /** Number of direct successors (out-degree in the directed interpretation). */
        public Map<String, Integer> getDependentCount() { return dependentCount; }

        /** Root vertices (no incoming edges / in-degree 0). */
        public List<String> getRoots() { return roots; }

        /** Leaf vertices (no outgoing edges / out-degree 0). */
        public List<String> getLeaves() { return leaves; }

        /** Length of the longest path (critical path length). */
        public int getLongestPathLength() { return longestPathLength; }

        /** Vertices along the critical (longest) path. */
        public List<String> getCriticalPath() { return criticalPath; }
    }

    /**
     * Information about a detected cycle.
     */
    public static class CycleInfo {
        private final List<String> vertices;
        private final List<Edge> edges;

        public CycleInfo(List<String> vertices, List<Edge> edges) {
            this.vertices = Collections.unmodifiableList(new ArrayList<String>(vertices));
            this.edges = Collections.unmodifiableList(new ArrayList<Edge>(edges));
        }

        /** Vertices forming the cycle, in traversal order. */
        public List<String> getVertices() { return vertices; }

        /** Edges forming the cycle. */
        public List<Edge> getEdges() { return edges; }

        /** Number of vertices in the cycle. */
        public int size() { return vertices.size(); }
    }

    /**
     * Result for a single vertex's dependency analysis.
     */
    public static class VertexDependencyInfo {
        private final String vertex;
        private final Set<String> allDependencies;
        private final Set<String> allDependents;
        private final int depth;
        private final boolean isRoot;
        private final boolean isLeaf;

        public VertexDependencyInfo(String vertex, Set<String> allDependencies,
                                     Set<String> allDependents, int depth,
                                     boolean isRoot, boolean isLeaf) {
            this.vertex = vertex;
            this.allDependencies = Collections.unmodifiableSet(new HashSet<String>(allDependencies));
            this.allDependents = Collections.unmodifiableSet(new HashSet<String>(allDependents));
            this.depth = depth;
            this.isRoot = isRoot;
            this.isLeaf = isLeaf;
        }

        /** The vertex ID. */
        public String getVertex() { return vertex; }

        /** All transitive dependencies (predecessors). */
        public Set<String> getAllDependencies() { return allDependencies; }

        /** All transitive dependents (successors). */
        public Set<String> getAllDependents() { return allDependents; }

        /** Depth from roots. */
        public int getDepth() { return depth; }

        /** True if this vertex has no incoming edges. */
        public boolean isRoot() { return isRoot; }

        /** True if this vertex has no outgoing edges. */
        public boolean isLeaf() { return isLeaf; }
    }

    // ── Directed adjacency helper ──────────────────────────────

    /**
     * Holds the directed adjacency representation built from the graph's
     * edges.  Extracted to avoid duplicating the build logic in
     * {@link #analyze()}, {@link #analyzeDependencies(String)}, and
     * {@link #countChoicePoints()}.
     */
    /**
     * Delegates to {@link GraphUtils.DirectedAdj} — the shared directed
     * adjacency builder extracted from this class.
     */
    private GraphUtils.DirectedAdj buildDirectedAdj() {
        if (cachedAdj == null) {
            cachedAdj = GraphUtils.buildDirectedAdjacencyMap(graph);
        }
        return cachedAdj;
    }

    // ── Core algorithms ─────────────────────────────────────────

    /**
     * Perform a full topological sort analysis.
     *
     * <p>The graph's edges are interpreted as directed: for each Edge,
     * vertex1 → vertex2 represents a dependency (vertex2 depends on
     * vertex1, or equivalently vertex1 must come before vertex2).</p>
     *
     * <p>If the graph contains cycles, {@link TopologicalSortResult#isDAG()}
     * returns false and the sorted order will be empty.</p>
     *
     * @return complete topological sort analysis
     */
    public TopologicalSortResult analyze() {
        if (cachedResult != null) {
            return cachedResult;
        }
        GraphUtils.DirectedAdj adj = buildDirectedAdj();
        Map<String, Set<String>> successors = adj.successors;
        Map<String, Set<String>> predecessors = adj.predecessors;
        Set<String> allVertices = adj.vertices;

        // Kahn's algorithm for topological sort
        Map<String, Integer> inDegree = new HashMap<String, Integer>();
        for (String v : allVertices) {
            inDegree.put(v, predecessors.get(v).size());
        }

        PriorityQueue<String> queue = new PriorityQueue<String>();
        List<String> roots = new ArrayList<String>();
        for (String v : allVertices) {
            if (inDegree.get(v) == 0) {
                queue.add(v);
                roots.add(v);
            }
        }
        // PriorityQueue maintains heap order; no manual sort needed

        List<String> sortedOrder = new ArrayList<String>();
        while (!queue.isEmpty()) {
            // PriorityQueue.poll() returns lexicographically smallest in O(log V)
            String v = queue.poll();

            sortedOrder.add(v);

            List<String> succs = new ArrayList<String>(successors.get(v));
            Collections.sort(succs);
            for (String w : succs) {
                int newDeg = inDegree.get(w) - 1;
                inDegree.put(w, newDeg);
                if (newDeg == 0) {
                    queue.add(w);
                }
            }
        }

        boolean isDAG = sortedOrder.size() == allVertices.size();
        List<CycleInfo> cycles = new ArrayList<CycleInfo>();

        if (!isDAG) {
            // Find cycles using DFS on the remaining unvisited vertices
            sortedOrder.clear(); // no valid ordering
            cycles = detectCycles(allVertices, successors);
        }

        // Compute dependency and dependent counts
        Map<String, Integer> dependencyCount = new HashMap<String, Integer>();
        Map<String, Integer> dependentCount = new HashMap<String, Integer>();
        for (String v : allVertices) {
            dependencyCount.put(v, predecessors.get(v).size());
            dependentCount.put(v, successors.get(v).size());
        }

        // Find leaves (no outgoing edges)
        List<String> leaves = new ArrayList<String>();
        for (String v : allVertices) {
            if (successors.get(v).isEmpty()) {
                leaves.add(v);
            }
        }
        Collections.sort(leaves);

        // Compute depth map and critical path (only for DAGs)
        Map<String, Integer> depthMap = new HashMap<String, Integer>();
        int longestPathLength = 0;
        List<String> criticalPath = new ArrayList<String>();

        if (isDAG && !sortedOrder.isEmpty()) {
            // Compute depths via topological order
            for (String v : allVertices) {
                depthMap.put(v, 0);
            }
            for (String v : sortedOrder) {
                for (String w : successors.get(v)) {
                    int newDepth = depthMap.get(v) + 1;
                    if (newDepth > depthMap.get(w)) {
                        depthMap.put(w, newDepth);
                    }
                }
            }

            // Find longest path length
            for (int d : depthMap.values()) {
                if (d > longestPathLength) {
                    longestPathLength = d;
                }
            }

            // Reconstruct critical path by backtracking from the deepest node
            criticalPath = reconstructCriticalPath(depthMap, predecessors, longestPathLength);
        }

        cachedResult = new TopologicalSortResult(isDAG, sortedOrder, cycles, depthMap,
                dependencyCount, dependentCount, roots, leaves,
                longestPathLength, criticalPath);
        return cachedResult;
    }

    /**
     * Get detailed dependency information for a specific vertex.
     *
     * @param vertex the vertex to analyze
     * @return dependency info, or null if vertex not in graph
     */
    public VertexDependencyInfo analyzeDependencies(String vertex) {
        if (vertex == null || !graph.containsVertex(vertex)) {
            return null;
        }

        GraphUtils.DirectedAdj adj = buildDirectedAdj();
        Map<String, Set<String>> successors = adj.successors;
        Map<String, Set<String>> predecessors = adj.predecessors;

        // BFS backwards for all transitive dependencies
        Set<String> allDeps = new HashSet<String>();
        Queue<String> bfsQueue = new ArrayDeque<String>();
        bfsQueue.add(vertex);
        Set<String> visited = new HashSet<String>();
        visited.add(vertex);
        while (!bfsQueue.isEmpty()) {
            String v = bfsQueue.poll();
            for (String pred : predecessors.get(v)) {
                if (visited.add(pred)) {
                    allDeps.add(pred);
                    bfsQueue.add(pred);
                }
            }
        }

        // BFS forward for all transitive dependents
        Set<String> allDependents = new HashSet<String>();
        bfsQueue.add(vertex);
        visited.clear();
        visited.add(vertex);
        while (!bfsQueue.isEmpty()) {
            String v = bfsQueue.poll();
            for (String succ : successors.get(v)) {
                if (visited.add(succ)) {
                    allDependents.add(succ);
                    bfsQueue.add(succ);
                }
            }
        }

        // Compute depth
        int depth = 0;
        TopologicalSortResult result = analyze();
        if (result.isDAG() && result.getDepthMap().containsKey(vertex)) {
            depth = result.getDepthMap().get(vertex);
        }

        boolean isRoot = predecessors.get(vertex).isEmpty();
        boolean isLeaf = successors.get(vertex).isEmpty();

        return new VertexDependencyInfo(vertex, allDeps, allDependents, depth, isRoot, isLeaf);
    }

    /**
     * Get all possible valid topological orderings count estimate.
     *
     * <p>Computing all orderings is factorial, so this returns the number
     * of "choice points" — positions where multiple vertices have in-degree
     * 0 simultaneously. A high number indicates flexible scheduling.</p>
     *
     * @return number of scheduling choice points, or -1 if graph has cycles
     */
    public int countChoicePoints() {
        TopologicalSortResult result = analyze();
        if (!result.isDAG()) {
            return -1;
        }

        // Re-use shared adjacency builder and count points where
        // multiple vertices are ready simultaneously
        GraphUtils.DirectedAdj adj = buildDirectedAdj();
        Map<String, Set<String>> successors = adj.successors;
        Map<String, Integer> inDegree = new HashMap<String, Integer>();

        for (String v : adj.vertices) {
            inDegree.put(v, adj.predecessors.get(v).size());
        }

        List<String> ready = new ArrayList<String>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                ready.add(entry.getKey());
            }
        }

        int choicePoints = 0;
        while (!ready.isEmpty()) {
            if (ready.size() > 1) {
                choicePoints++;
            }
            Collections.sort(ready);
            String v = ready.remove(0);
            for (String w : successors.get(v)) {
                int newDeg = inDegree.get(w) - 1;
                inDegree.put(w, newDeg);
                if (newDeg == 0) {
                    ready.add(w);
                }
            }
        }

        return choicePoints;
    }

    /**
     * Generate a human-readable summary of the topological sort analysis.
     *
     * @return formatted multi-line summary string
     */
    public String generateSummary() {
        TopologicalSortResult result = analyze();
        StringBuilder sb = new StringBuilder();

        sb.append("═══════════════════════════════════════════════════\n");
        sb.append("  TOPOLOGICAL SORT ANALYSIS\n");
        sb.append("═══════════════════════════════════════════════════\n\n");

        sb.append("  Vertices: ").append(graph.getVertexCount()).append("\n");
        sb.append("  Edges:    ").append(graph.getEdgeCount()).append("\n");
        sb.append("  Is DAG:   ").append(result.isDAG() ? "Yes" : "No").append("\n\n");

        if (result.isDAG()) {
            sb.append("───────────────────────────────────────────────────\n");
            sb.append("  TOPOLOGICAL ORDER\n");
            sb.append("───────────────────────────────────────────────────\n");
            List<String> order = result.getSortedOrder();
            for (int i = 0; i < order.size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(order.get(i));
                sb.append("  (depth: ").append(result.getDepthMap().get(order.get(i))).append(")\n");
            }

            sb.append("\n───────────────────────────────────────────────────\n");
            sb.append("  STRUCTURE\n");
            sb.append("───────────────────────────────────────────────────\n");
            sb.append("  Roots (no dependencies): ").append(result.getRoots()).append("\n");
            sb.append("  Leaves (no dependents):  ").append(result.getLeaves()).append("\n");
            sb.append("  Critical path length:    ").append(result.getLongestPathLength()).append("\n");
            sb.append("  Critical path:           ").append(result.getCriticalPath()).append("\n");

            int choicePoints = countChoicePoints();
            sb.append("  Scheduling flexibility:  ").append(choicePoints)
              .append(" choice point(s)\n");
        } else {
            sb.append("───────────────────────────────────────────────────\n");
            sb.append("  CYCLES DETECTED\n");
            sb.append("───────────────────────────────────────────────────\n");
            List<CycleInfo> cycles = result.getCycles();
            sb.append("  ").append(cycles.size()).append(" cycle(s) found:\n\n");
            for (int i = 0; i < cycles.size(); i++) {
                CycleInfo cycle = cycles.get(i);
                sb.append("  Cycle ").append(i + 1).append(": ");
                List<String> verts = cycle.getVertices();
                for (int j = 0; j < verts.size(); j++) {
                    sb.append(verts.get(j));
                    if (j < verts.size() - 1) sb.append(" → ");
                }
                sb.append(" → ").append(verts.get(0)); // back to start
                sb.append("  (size: ").append(cycle.size()).append(")\n");
            }
        }

        sb.append("\n═══════════════════════════════════════════════════\n");
        return sb.toString();
    }

    // ── Private helpers ─────────────────────────────────────────

    /**
     * Detect cycles using DFS with three-color marking.
     * WHITE = unvisited, GRAY = in current path, BLACK = fully processed.
     */
    private List<CycleInfo> detectCycles(Set<String> vertices,
                                          Map<String, Set<String>> successors) {
        List<CycleInfo> cycles = new ArrayList<CycleInfo>();
        Map<String, Integer> color = new HashMap<String, Integer>(); // 0=white, 1=gray, 2=black
        Map<String, String> parent = new HashMap<String, String>();

        for (String v : vertices) {
            color.put(v, 0);
        }

        List<String> sorted = new ArrayList<String>(vertices);
        Collections.sort(sorted);

        for (String v : sorted) {
            if (color.get(v) == 0) {
                dfsCycleDetect(v, successors, color, parent, cycles, new ArrayDeque<String>());
            }
        }

        return cycles;
    }

    /**
     * DFS helper for cycle detection.
     */
    private void dfsCycleDetect(String v, Map<String, Set<String>> successors,
                                 Map<String, Integer> color, Map<String, String> parent,
                                 List<CycleInfo> cycles, LinkedList<String> path) {
        color.put(v, 1); // GRAY
        path.addLast(v);

        List<String> succs = new ArrayList<String>(successors.get(v));
        Collections.sort(succs);

        for (String w : succs) {
            if (color.get(w) == 1) {
                // Back Edge found → extract cycle
                List<String> cycleVertices = new ArrayList<String>();
                List<Edge> cycleEdges = new ArrayList<Edge>();

                // Find w in the path and extract from there
                boolean found = false;
                for (String p : path) {
                    if (p.equals(w)) found = true;
                    if (found) cycleVertices.add(p);
                }

                // Find edges for the cycle
                for (int i = 0; i < cycleVertices.size(); i++) {
                    String from = cycleVertices.get(i);
                    String to = cycleVertices.get((i + 1) % cycleVertices.size());
                    Edge e = findEdge(from, to);
                    if (e != null) cycleEdges.add(e);
                }

                // Avoid duplicate cycles (check if this cycle already recorded)
                if (!isDuplicateCycle(cycles, cycleVertices)) {
                    cycles.add(new CycleInfo(cycleVertices, cycleEdges));
                }
            } else if (color.get(w) == 0) {
                parent.put(w, v);
                dfsCycleDetect(w, successors, color, parent, cycles, path);
            }
        }

        path.removeLast();
        color.put(v, 2); // BLACK
    }

    /**
     * Check if a cycle with the same vertex set is already recorded.
     */
    private boolean isDuplicateCycle(List<CycleInfo> existing, List<String> newCycle) {
        Set<String> newSet = new HashSet<String>(newCycle);
        for (CycleInfo c : existing) {
            if (new HashSet<String>(c.getVertices()).equals(newSet)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find an Edge from vertex1 to vertex2 in the graph.
     */
    private Edge findEdge(String from, String to) {
        for (Edge e : graph.getEdges()) {
            if (from.equals(e.getVertex1()) && to.equals(e.getVertex2())) {
                return e;
            }
        }
        return null;
    }

    /**
     * Reconstruct the critical (longest) path by backtracking from the
     * deepest vertex through predecessors with depth = current - 1.
     */
    private List<String> reconstructCriticalPath(Map<String, Integer> depthMap,
                                                   Map<String, Set<String>> predecessors,
                                                   int maxDepth) {
        // Find the vertex at max depth (lexicographically smallest if tie)
        String deepest = null;
        for (Map.Entry<String, Integer> entry : depthMap.entrySet()) {
            if (entry.getValue() == maxDepth) {
                if (deepest == null || entry.getKey().compareTo(deepest) < 0) {
                    deepest = entry.getKey();
                }
            }
        }

        if (deepest == null) {
            return new ArrayList<String>();
        }

        // Backtrack through predecessors choosing the one at depth - 1
        ArrayDeque<String> path = new ArrayDeque<String>();
        String current = deepest;
        while (current != null) {
            path.addFirst(current);
            int currentDepth = depthMap.get(current);
            if (currentDepth == 0) break;

            String prev = null;
            for (String pred : predecessors.get(current)) {
                if (depthMap.containsKey(pred) && depthMap.get(pred) == currentDepth - 1) {
                    if (prev == null || pred.compareTo(prev) < 0) {
                        prev = pred;
                    }
                }
            }
            current = prev;
        }

        return new ArrayList<String>(path);
    }
}
