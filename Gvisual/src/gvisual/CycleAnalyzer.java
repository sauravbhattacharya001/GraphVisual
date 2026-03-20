package gvisual;

import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;

import java.util.*;

/**
 * Comprehensive cycle analysis for directed and undirected graphs.
 *
 * <p>Provides cycle detection, girth computation, fundamental cycle basis
 * extraction, bounded enumeration of all simple cycles, and statistical
 * summaries of cyclic structure.</p>
 *
 * <h3>Algorithms</h3>
 * <ul>
 *   <li><b>Cycle detection:</b> DFS with back-edge detection — O(V + E)</li>
 *   <li><b>Girth:</b> BFS from each vertex, shortest back-edge — O(V × (V + E))</li>
 *   <li><b>Fundamental cycle basis:</b> Spanning tree + back-edges — O(V + E)</li>
 *   <li><b>All simple cycles:</b> Bounded DFS with vertex ordering — exponential
 *       worst case, bounded by configurable limit</li>
 * </ul>
 *
 * <h3>Applications</h3>
 * <ul>
 *   <li>Deadlock detection in concurrent systems</li>
 *   <li>Feedback loop identification in signal processing</li>
 *   <li>Circuit analysis in electrical networks</li>
 *   <li>Dependency cycle detection in build systems</li>
 *   <li>Minimum cycle basis for network topology analysis</li>
 * </ul>
 *
 * @author zalenix
 */
public class CycleAnalyzer {

    private final Graph<String, Edge> graph;
    private final boolean isDirected;

    /**
     * Create a new cycle analyzer for the given graph.
     *
     * @param graph the JUNG graph to analyze (directed or undirected)
     * @throws IllegalArgumentException if graph is null
     */
    public CycleAnalyzer(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.isDirected = graph instanceof DirectedSparseGraph;
    }

    // ── Result types ──────────────────────────────────────────

    /**
     * A single cycle represented as an ordered list of vertices.
     * For a cycle A→B→C→A, the list is [A, B, C].
     */
    public static class Cycle {
        private final List<String> vertices;

        public Cycle(List<String> vertices) {
            this.vertices = Collections.unmodifiableList(
                    new ArrayList<String>(vertices));
        }

        /** Ordered vertices in the cycle. */
        public List<String> getVertices() { return vertices; }

        /** Number of vertices (and edges) in the cycle. */
        public int length() { return vertices.size(); }

        /** Total weight of all edges in the cycle (0 if no weights). */
        public float totalWeight(Graph<String, Edge> graph) {
            float w = 0;
            for (int i = 0; i < vertices.size(); i++) {
                String from = vertices.get(i);
                String to = vertices.get((i + 1) % vertices.size());
                Edge e  graph.findEdge(from, to);
                if (e == null) e = graph.findEdge(to, from);
                if (e != null) w += e.getWeight();
            }
            return w;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < vertices.size(); i++) {
                if (i > 0) sb.append(" → ");
                sb.append(vertices.get(i));
            }
            sb.append(" → ").append(vertices.get(0));
            return sb.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Cycle)) return false;
            Cycle other = (Cycle) o;
            if (this.length() != other.length()) return false;
            // Normalize: cycles are equivalent under rotation
            Set<String> thisSet = new HashSet<String>(this.vertices);
            Set<String> otherSet = new HashSet<String>(other.vertices);
            return thisSet.equals(otherSet);
        }

        @Override
        public int hashCode() {
            return new HashSet<String>(vertices).hashCode();
        }
    }

    /**
     * Comprehensive cycle analysis report.
     */
    public static class CycleReport {
        private final boolean hasCycles;
        private final int girth;
        private final int circumference;
        private final List<Cycle> fundamentalBasis;
        private final List<Cycle> allCycles;
        private final Map<String, Integer> vertexParticipation;
        private final boolean allCyclesBounded;

        public CycleReport(boolean hasCycles, int girth, int circumference,
                           List<Cycle> fundamentalBasis, List<Cycle> allCycles,
                           Map<String, Integer> vertexParticipation,
                           boolean allCyclesBounded) {
            this.hasCycles = hasCycles;
            this.girth = girth;
            this.circumference = circumference;
            this.fundamentalBasis = Collections.unmodifiableList(fundamentalBasis);
            this.allCycles = Collections.unmodifiableList(allCycles);
            this.vertexParticipation = Collections.unmodifiableMap(vertexParticipation);
            this.allCyclesBounded = allCyclesBounded;
        }

        /** True if the graph contains at least one cycle. */
        public boolean hasCycles() { return hasCycles; }

        /** Length of the shortest cycle, or -1 if acyclic. */
        public int getGirth() { return girth; }

        /** Length of the longest cycle found, or -1 if acyclic. */
        public int getCircumference() { return circumference; }

        /** Fundamental cycle basis from spanning tree back-edges. */
        public List<Cycle> getFundamentalBasis() { return fundamentalBasis; }

        /** All simple cycles found (may be bounded by limit). */
        public List<Cycle> getAllCycles() { return allCycles; }

        /** How many cycles each vertex participates in. */
        public Map<String, Integer> getVertexParticipation() { return vertexParticipation; }

        /** True if cycle enumeration completed without hitting the limit. */
        public boolean isAllCyclesComplete() { return allCyclesBounded; }

        /** Number of edges in the cycle basis (circuit rank). */
        public int getCyclomaticNumber() { return fundamentalBasis.size(); }

        /** Average cycle length, or 0 if no cycles. */
        public double getAverageCycleLength() {
            if (allCycles.isEmpty()) return 0;
            double sum = 0;
            for (Cycle c : allCycles) sum += c.length();
            return sum / allCycles.size();
        }

        /** Vertex appearing in the most cycles, or null if acyclic. */
        public String getMostCyclicVertex() {
            String best = null;
            int bestCount = 0;
            for (Map.Entry<String, Integer> e : vertexParticipation.entrySet()) {
                if (e.getValue() > bestCount) {
                    bestCount = e.getValue();
                    best = e.getKey();
                }
            }
            return best;
        }

        /** Human-readable summary. */
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Cycle Analysis Report ===\n");
            sb.append("Graph type: ").append(hasCycles ? "Cyclic" : "Acyclic").append("\n");
            if (!hasCycles) {
                sb.append("No cycles found.\n");
                return sb.toString();
            }
            sb.append("Girth (shortest cycle): ").append(girth).append("\n");
            sb.append("Circumference (longest): ").append(circumference).append("\n");
            sb.append("Cyclomatic number: ").append(getCyclomaticNumber()).append("\n");
            sb.append("Fundamental basis cycles: ").append(fundamentalBasis.size()).append("\n");
            sb.append("Total simple cycles found: ").append(allCycles.size());
            if (!allCyclesBounded) sb.append(" (limit reached)");
            sb.append("\n");
            sb.append("Average cycle length: ").append(
                    String.format("%.1f", getAverageCycleLength())).append("\n");
            String mostCyclic = getMostCyclicVertex();
            if (mostCyclic != null) {
                sb.append("Most cyclic vertex: ").append(mostCyclic)
                        .append(" (").append(vertexParticipation.get(mostCyclic))
                        .append(" cycles)\n");
            }
            return sb.toString();
        }
    }

    // ── Cycle detection ───────────────────────────────────────

    /**
     * Checks whether the graph contains any cycle.
     * Uses DFS with coloring (WHITE/GRAY/BLACK for directed,
     * parent tracking for undirected). O(V + E).
     *
     * @return true if at least one cycle exists
     */
    public boolean hasCycles() {
        Collection<String> vertices = graph.getVertices();
        if (vertices.isEmpty()) return false;

        if (isDirected) {
            return hasCyclesDirected(vertices);
        } else {
            return hasCyclesUndirected(vertices);
        }
    }

    private boolean hasCyclesDirected(Collection<String> vertices) {
        // 0 = white, 1 = gray, 2 = black
        Map<String, Integer> color = new HashMap<String, Integer>();
        for (String v : vertices) color.put(v, 0);

        for (String v : vertices) {
            if (color.get(v) == 0) {
                if (dfsCycleDirected(v, color)) return true;
            }
        }
        return false;
    }

    private boolean dfsCycleDirected(String v, Map<String, Integer> color) {
        color.put(v, 1);
        for (String neighbor : getSuccessors(v)) {
            int c = color.get(neighbor);
            if (c == 1) return true;      // back edge → cycle
            if (c == 0 && dfsCycleDirected(neighbor, color)) return true;
        }
        color.put(v, 2);
        return false;
    }

    private boolean hasCyclesUndirected(Collection<String> vertices) {
        Set<String> visited = new HashSet<String>();
        for (String v : vertices) {
            if (!visited.contains(v)) {
                if (dfsCycleUndirected(v, null, visited)) return true;
            }
        }
        return false;
    }

    private boolean dfsCycleUndirected(String v, String parent, Set<String> visited) {
        visited.add(v);
        for (String neighbor : getNeighbors(v)) {
            if (!visited.contains(neighbor)) {
                if (dfsCycleUndirected(neighbor, v, visited)) return true;
            } else if (!neighbor.equals(parent)) {
                return true;  // back edge to non-parent → cycle
            }
        }
        return false;
    }

    // ── Girth ─────────────────────────────────────────────────

    /**
     * Computes the girth (length of the shortest cycle).
     * BFS from each vertex; when a back-edge is found, the cycle
     * length is 2 × depth + 1 (undirected) or tracked via distances
     * (directed). O(V × (V + E)).
     *
     * @return shortest cycle length, or -1 if the graph is acyclic
     */
    public int girth() {
        if (graph.getVertexCount() == 0) return -1;

        int minCycle = Integer.MAX_VALUE;

        for (String start : graph.getVertices()) {
            int cycleLen = bfsShortestCycle(start);
            if (cycleLen > 0 && cycleLen < minCycle) {
                minCycle = cycleLen;
            }
        }

        return minCycle == Integer.MAX_VALUE ? -1 : minCycle;
    }

    private int bfsShortestCycle(String start) {
        Map<String, Integer> dist = new HashMap<String, Integer>();
        Map<String, String> parent = new HashMap<String, String>();
        Queue<String> queue = new LinkedList<String>();

        dist.put(start, 0);
        parent.put(start, null);
        queue.add(start);

        int shortest = Integer.MAX_VALUE;

        while (!queue.isEmpty()) {
            String v = queue.poll();
            int d = dist.get(v);

            // Early termination: can't find shorter cycles
            if (d >= shortest) break;

            Iterable<String> neighbors = isDirected ? getSuccessors(v) : getNeighbors(v);
            for (String w : neighbors) {
                if (!dist.containsKey(w)) {
                    dist.put(w, d + 1);
                    parent.put(w, v);
                    queue.add(w);
                } else if (isDirected) {
                    // Directed: cycle = dist[v] + 1 + (how far from start to w via tree)
                    // Actually for directed: if w is already visited and reachable, cycle exists
                    int cycleLen = d + 1 - dist.get(w) + dist.get(w);
                    // Simplifies to d + 1 for back-edges to start
                    if (w.equals(start)) {
                        cycleLen = d + 1;
                        if (cycleLen < shortest) shortest = cycleLen;
                    }
                } else {
                    // Undirected: non-parent back-edge
                    if (!w.equals(parent.get(v))) {
                        int cycleLen = d + 1 + dist.get(w);
                        if (cycleLen < shortest) shortest = cycleLen;
                    }
                }
            }
        }
        return shortest == Integer.MAX_VALUE ? -1 : shortest;
    }

    // ── Fundamental Cycle Basis ───────────────────────────────

    /**
     * Computes a fundamental cycle basis using a spanning tree.
     *
     * <p>A fundamental cycle basis has exactly M - N + C cycles, where
     * M = edges, N = vertices, C = connected components (the cyclomatic
     * number). Each non-tree edge creates exactly one fundamental cycle.</p>
     *
     * @return list of fundamental cycles (one per non-tree edge)
     */
    public List<Cycle> fundamentalCycleBasis() {
        List<Cycle> basis = new ArrayList<Cycle>();
        if (graph.getVertexCount() == 0) return basis;

        // Build spanning forest via BFS
        Set<String> treeEdgeSet = new HashSet<String>();
        Map<String, String> parentMap = new HashMap<String, String>();
        Set<String> visited = new HashSet<String>();

        for (String root : graph.getVertices()) {
            if (visited.contains(root)) continue;

            Queue<String> queue = new LinkedList<String>();
            queue.add(root);
            visited.add(root);
            parentMap.put(root, null);

            while (!queue.isEmpty()) {
                String v = queue.poll();
                Iterable<String> neighbors = isDirected ? getSuccessors(v) : getNeighbors(v);
                for (String w : neighbors) {
                    if (!visited.contains(w)) {
                        visited.add(w);
                        parentMap.put(w, v);
                        treeEdgeSet.add(edgeKey(v, w));
                        queue.add(w);
                    }
                }
            }
        }

        // Each non-tree edge defines a fundamental cycle
        for (Edge e : graph.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            if (v1 == null || v2 == null) continue;

            String key = edgeKey(v1, v2);
            String keyRev = edgeKey(v2, v1);
            if (!treeEdgeSet.contains(key) && !treeEdgeSet.contains(keyRev)) {
                // Find cycle via tree paths
                List<String> cycle = findTreeCycle(v1, v2, parentMap);
                if (cycle != null && cycle.size() >= 3) {
                    basis.add(new Cycle(cycle));
                }
            }
        }

        return basis;
    }

    private List<String> findTreeCycle(String u, String v,
                                       Map<String, String> parentMap) {
        // Find paths from u and v back to their common ancestor
        List<String> pathU = new ArrayList<String>();
        List<String> pathV = new ArrayList<String>();

        Set<String> ancestorsU = new HashSet<String>();
        String curr = u;
        while (curr != null) {
            pathU.add(curr);
            ancestorsU.add(curr);
            curr = parentMap.get(curr);
        }

        curr = v;
        while (curr != null) {
            pathV.add(curr);
            if (ancestorsU.contains(curr)) break;
            curr = parentMap.get(curr);
        }

        if (curr == null) return null;  // Different components

        // Trim pathU to the common ancestor
        String lca = curr;
        List<String> cycle = new ArrayList<String>();
        for (String node : pathU) {
            cycle.add(node);
            if (node.equals(lca)) break;
        }

        // Add pathV in reverse (excluding lca)
        for (int i = pathV.size() - 2; i >= 0; i--) {
            if (!pathV.get(i).equals(lca)) {
                cycle.add(pathV.get(i));
            }
        }

        return cycle;
    }

    // ── All Simple Cycles (bounded) ───────────────────────────

    /** Default maximum number of cycles to enumerate. */
    public static final int DEFAULT_CYCLE_LIMIT = 10000;

    /**
     * Enumerates all simple cycles up to the default limit.
     *
     * @return list of cycles found, and whether enumeration completed
     */
    public CycleEnumerationResult findAllSimpleCycles() {
        return findAllSimpleCycles(DEFAULT_CYCLE_LIMIT);
    }

    /**
     * Enumerates all simple cycles up to the given limit.
     *
     * <p>For directed graphs, uses a DFS-based approach with vertex ordering
     * to avoid duplicate cycles. For undirected graphs, canonicalizes cycles
     * by requiring the minimum vertex to be first and the second vertex to be
     * less than the last.</p>
     *
     * @param limit maximum number of cycles to collect
     * @return result containing cycles and completion status
     */
    public CycleEnumerationResult findAllSimpleCycles(int limit) {
        if (limit < 0) throw new IllegalArgumentException("Limit must be non-negative");

        List<Cycle> cycles = new ArrayList<Cycle>();
        if (graph.getVertexCount() == 0) {
            return new CycleEnumerationResult(cycles, true);
        }

        // Sort vertices for deterministic ordering
        List<String> sortedVertices = new ArrayList<String>(graph.getVertices());
        Collections.sort(sortedVertices);

        Set<String> canonicalSet = new HashSet<String>();
        boolean[] limitReached = { false };

        for (String start : sortedVertices) {
            if (limitReached[0]) break;

            Set<String> visited = new HashSet<String>();
            visited.add(start);
            List<String> path = new ArrayList<String>();
            path.add(start);

            dfsEnumerate(start, start, visited, path, cycles,
                    canonicalSet, limit, limitReached);
        }

        return new CycleEnumerationResult(cycles, !limitReached[0]);
    }

    private void dfsEnumerate(String start, String current,
                              Set<String> visited, List<String> path,
                              List<Cycle> cycles, Set<String> canonicalSet,
                              int limit, boolean[] limitReached) {
        if (limitReached[0]) return;

        Iterable<String> neighbors = isDirected ? getSuccessors(current) : getNeighbors(current);
        for (String next : neighbors) {
            int minCycleLen = isDirected ? 2 : 3;
                if (next.equals(start) && path.size() >= minCycleLen) {
                // Found a cycle back to start
                List<String> cyclePath = new ArrayList<String>(path);
                String canonical = canonicalize(cyclePath);
                if (canonicalSet.add(canonical)) {
                    cycles.add(new Cycle(cyclePath));
                    if (cycles.size() >= limit) {
                        limitReached[0] = true;
                        return;
                    }
                }
            } else if (!visited.contains(next)) {
                // For directed: only explore vertices >= start to avoid duplicates
                // For undirected: explore all unvisited
                if (isDirected && next.compareTo(start) < 0) continue;

                visited.add(next);
                path.add(next);
                dfsEnumerate(start, next, visited, path, cycles,
                        canonicalSet, limit, limitReached);
                path.remove(path.size() - 1);
                visited.remove(next);
            }
        }
    }

    private String canonicalize(List<String> cycle) {
        if (cycle.isEmpty()) return "";

        // Find rotation starting with minimum vertex
        int minIdx = 0;
        for (int i = 1; i < cycle.size(); i++) {
            if (cycle.get(i).compareTo(cycle.get(minIdx)) < 0) {
                minIdx = i;
            }
        }

        // Build rotated cycle
        List<String> rotated = new ArrayList<String>(cycle.size());
        for (int i = 0; i < cycle.size(); i++) {
            rotated.add(cycle.get((minIdx + i) % cycle.size()));
        }

        // For undirected: choose direction where second < last
        if (!isDirected && rotated.size() >= 3) {
            String second = rotated.get(1);
            String last = rotated.get(rotated.size() - 1);
            if (second.compareTo(last) > 0) {
                // Reverse (keeping first element fixed)
                List<String> reversed = new ArrayList<String>(rotated.size());
                reversed.add(rotated.get(0));
                for (int i = rotated.size() - 1; i >= 1; i--) {
                    reversed.add(rotated.get(i));
                }
                rotated = reversed;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rotated.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(rotated.get(i));
        }
        return sb.toString();
    }

    /**
     * Result of cycle enumeration.
     */
    public static class CycleEnumerationResult {
        private final List<Cycle> cycles;
        private final boolean complete;

        public CycleEnumerationResult(List<Cycle> cycles, boolean complete) {
            this.cycles = Collections.unmodifiableList(cycles);
            this.complete = complete;
        }

        /** All cycles found. */
        public List<Cycle> getCycles() { return cycles; }

        /** True if enumeration completed without hitting the limit. */
        public boolean isComplete() { return complete; }

        /** Number of cycles found. */
        public int count() { return cycles.size(); }
    }

    // ── Full Report ───────────────────────────────────────────

    /**
     * Generates a comprehensive cycle analysis report.
     *
     * @return report with all cycle metrics
     */
    public CycleReport analyze() {
        return analyze(DEFAULT_CYCLE_LIMIT);
    }

    /**
     * Generates a comprehensive cycle analysis report with custom cycle limit.
     *
     * @param cycleLimit max cycles to enumerate
     * @return report with all cycle metrics
     */
    public CycleReport analyze(int cycleLimit) {
        boolean cycles = hasCycles();
        int g = -1;
        int circumference = -1;
        List<Cycle> basis = new ArrayList<Cycle>();
        List<Cycle> allCycles = new ArrayList<Cycle>();
        Map<String, Integer> participation = new HashMap<String, Integer>();
        boolean complete = true;

        if (cycles) {
            g = girth();
            basis = fundamentalCycleBasis();

            CycleEnumerationResult enumResult = findAllSimpleCycles(cycleLimit);
            allCycles = enumResult.getCycles();
            complete = enumResult.isComplete();

            // Compute circumference and participation
            for (Cycle c : allCycles) {
                if (c.length() > circumference) {
                    circumference = c.length();
                }
                for (String v : c.getVertices()) {
                    Integer count = participation.get(v);
                    participation.put(v, count == null ? 1 : count + 1);
                }
            }
        }

        return new CycleReport(cycles, g, circumference, basis,
                allCycles, participation, complete);
    }

    // ── Helpers ───────────────────────────────────────────────

    private Iterable<String> getSuccessors(String v) {
        Collection<String> succ = graph.getSuccessors(v);
        return succ != null ? succ : Collections.<String>emptyList();
    }

    private Iterable<String> getNeighbors(String v) {
        return GraphUtils.neighborsOf(graph, v);
    }

    private String edgeKey(String v1, String v2) {
        return v1 + "->" + v2;
    }
}
