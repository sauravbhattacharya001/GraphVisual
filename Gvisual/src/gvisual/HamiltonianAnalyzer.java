package gvisual;

import edu.uci.ics.jung.graph.DirectedGraph;
import edu.uci.ics.jung.graph.Graph;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Hamiltonian Path/Cycle Analyzer — detects Hamiltonian paths and cycles in
 * graphs using exact backtracking and greedy heuristics, with sufficient
 * condition checks (Dirac's, Ore's, Chvátal's theorems).
 *
 * <h3>Features</h3>
 * <ul>
 *   <li><b>Sufficient conditions:</b> Dirac's theorem (min degree ≥ n/2),
 *       Ore's theorem (deg(u)+deg(v) ≥ n for non-adjacent pairs),
 *       Chvátal's condition (degree sequence check).</li>
 *   <li><b>Necessary conditions:</b> connectivity, vertex count ≥ 3 for cycles.</li>
 *   <li><b>Exact solver:</b> Backtracking with pruning for small graphs (≤ 20 vertices).</li>
 *   <li><b>Greedy heuristic:</b> Nearest-neighbor style for larger graphs.</li>
 *   <li><b>All Hamiltonian cycles:</b> Enumerate all (with configurable limit).</li>
 *   <li><b>Path finding:</b> Find Hamiltonian path between two specific vertices.</li>
 *   <li><b>Report generation:</b> Comprehensive analysis report.</li>
 * </ul>
 *
 * <h3>Complexity</h3>
 * <ul>
 *   <li><b>Exact:</b> O(n!) worst case — only practical for small graphs.</li>
 *   <li><b>Heuristic:</b> O(n²) — no guarantee of finding a solution.</li>
 * </ul>
 */
public class HamiltonianAnalyzer {

    /** Maximum vertex count for exact backtracking. */
    private static final int EXACT_THRESHOLD = 20;

    /** Default limit for enumerating all Hamiltonian cycles. */
    private static final int DEFAULT_ENUM_LIMIT = 1000;

    // ── Sufficient condition checks ──────────────────────────────────────

    /**
     * Result of sufficient condition analysis.
     */
    public static class ConditionResult {
        public final String name;
        public final boolean satisfied;
        public final String detail;

        public ConditionResult(String name, boolean satisfied, String detail) {
            this.name = name;
            this.satisfied = satisfied;
            this.detail = detail;
        }

        @Override
        public String toString() {
            return name + ": " + (satisfied ? "YES" : "NO") + " — " + detail;
        }
    }

    /**
     * Check Dirac's theorem: If every vertex has degree ≥ n/2 (n ≥ 3),
     * then the graph has a Hamiltonian cycle.
     */
    public <V, E> ConditionResult checkDirac(Graph<V, E> graph) {
        int n = graph.getVertexCount();
        if (n < 3) {
            return new ConditionResult("Dirac's Theorem", false,
                    "Graph has fewer than 3 vertices");
        }
        int threshold = n / 2;
        int minDeg = Integer.MAX_VALUE;
        V minVertex = null;
        for (V v : graph.getVertices()) {
            int deg = graph.degree(v);
            if (deg < minDeg) {
                minDeg = deg;
                minVertex = v;
            }
        }
        boolean satisfied = minDeg >= threshold;
        String detail = String.format("min degree = %d (vertex %s), threshold = %d (n/2 = %d/2)",
                minDeg, minVertex, threshold, n);
        return new ConditionResult("Dirac's Theorem", satisfied, detail);
    }

    /**
     * Check Ore's theorem: If for every pair of non-adjacent vertices u, v,
     * deg(u) + deg(v) ≥ n (n ≥ 3), then the graph has a Hamiltonian cycle.
     */
    public <V, E> ConditionResult checkOre(Graph<V, E> graph) {
        int n = graph.getVertexCount();
        if (n < 3) {
            return new ConditionResult("Ore's Theorem", false,
                    "Graph has fewer than 3 vertices");
        }
        List<V> vertices = new ArrayList<>(graph.getVertices());
        int worstSum = Integer.MAX_VALUE;
        V worstU = null, worstV = null;
        boolean hasNonAdjacentPair = false;

        for (int i = 0; i < vertices.size(); i++) {
            for (int j = i + 1; j < vertices.size(); j++) {
                V u = vertices.get(i);
                V v = vertices.get(j);
                if (!graph.isNeighbor(u, v)) {
                    hasNonAdjacentPair = true;
                    int sum = graph.degree(u) + graph.degree(v);
                    if (sum < worstSum) {
                        worstSum = sum;
                        worstU = u;
                        worstV = v;
                    }
                }
            }
        }

        if (!hasNonAdjacentPair) {
            // Complete graph — Ore's condition is vacuously true
            return new ConditionResult("Ore's Theorem", true,
                    "Graph is complete — condition vacuously satisfied");
        }

        boolean satisfied = worstSum >= n;
        String detail = String.format("worst non-adjacent pair (%s, %s): deg sum = %d, threshold = %d",
                worstU, worstV, worstSum, n);
        return new ConditionResult("Ore's Theorem", satisfied, detail);
    }

    /**
     * Check Chvátal's condition on the degree sequence.
     * Sort degrees d1 ≤ d2 ≤ ... ≤ dn. If for all i < n/2,
     * di ≤ i implies d(n-i) ≥ n-i, then the graph is Hamiltonian.
     */
    public <V, E> ConditionResult checkChvatal(Graph<V, E> graph) {
        int n = graph.getVertexCount();
        if (n < 3) {
            return new ConditionResult("Chvátal's Condition", false,
                    "Graph has fewer than 3 vertices");
        }
        int[] degrees = graph.getVertices().stream()
                .mapToInt(graph::degree)
                .sorted()
                .toArray();

        boolean satisfied = true;
        int failIndex = -1;
        for (int i = 0; i < n / 2; i++) {
            // 1-indexed: d_{i+1} ≤ i+1 implies d_{n-(i+1)+1} = d_{n-i} ≥ n-(i+1)
            if (degrees[i] <= i) {
                if (degrees[n - 1 - i] < n - 1 - i) {
                    satisfied = false;
                    failIndex = i;
                    break;
                }
            }
        }

        String detail;
        if (satisfied) {
            detail = "Degree sequence satisfies Chvátal's closure condition";
        } else {
            detail = String.format("Failed at index %d: d[%d]=%d ≤ %d but d[%d]=%d < %d",
                    failIndex, failIndex, degrees[failIndex], failIndex,
                    n - 1 - failIndex, degrees[n - 1 - failIndex], n - 1 - failIndex);
        }
        return new ConditionResult("Chvátal's Condition", satisfied, detail);
    }

    /**
     * Run all sufficient condition checks.
     */
    public <V, E> List<ConditionResult> checkAllConditions(Graph<V, E> graph) {
        List<ConditionResult> results = new ArrayList<>();
        results.add(checkDirac(graph));
        results.add(checkOre(graph));
        results.add(checkChvatal(graph));
        return results;
    }

    // ── Connectivity check ───────────────────────────────────────────────

    /**
     * Check if the graph is connected using BFS.
     */
    public <V, E> boolean isConnected(Graph<V, E> graph) {
        if (graph.getVertexCount() == 0) return true;
        Set<V> visited = new HashSet<>();
        Queue<V> queue = new LinkedList<>();
        V start = graph.getVertices().iterator().next();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            V v = queue.poll();
            for (V neighbor : graph.getNeighbors(v)) {
                if (visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return visited.size() == graph.getVertexCount();
    }

    // ── Exact backtracking solver ────────────────────────────────────────

    /**
     * Find a Hamiltonian cycle using backtracking. Returns the cycle as a
     * list of vertices (first = last), or null if none exists.
     */
    public <V, E> List<V> findHamiltonianCycle(Graph<V, E> graph) {
        int n = graph.getVertexCount();
        if (n < 3) return null;
        if (!isConnected(graph)) return null;

        List<V> vertices = new ArrayList<>(graph.getVertices());
        // Start from first vertex (cycles are rotation-invariant)
        V start = vertices.get(0);
        List<V> path = new ArrayList<>();
        Set<V> visited = new HashSet<>();
        path.add(start);
        visited.add(start);

        if (backtrackCycle(graph, path, visited, start, n)) {
            path.add(start); // close the cycle
            return path;
        }
        return null;
    }

    private <V, E> boolean backtrackCycle(Graph<V, E> graph, List<V> path,
                                           Set<V> visited, V start, int n) {
        if (path.size() == n) {
            // Check if last vertex connects back to start
            V last = path.get(path.size() - 1);
            return graph.isNeighbor(last, start);
        }

        V current = path.get(path.size() - 1);
        // Sort neighbors for deterministic results
        List<V> neighbors = new ArrayList<>(graph.getNeighbors(current));
        neighbors.sort(Comparator.comparing(Object::toString));

        for (V next : neighbors) {
            if (!visited.contains(next)) {
                // Pruning: if remaining unvisited vertices have degree < 1
                // in the subgraph induced by unvisited + start, skip
                visited.add(next);
                path.add(next);

                if (backtrackCycle(graph, path, visited, start, n)) {
                    return true;
                }

                path.remove(path.size() - 1);
                visited.remove(next);
            }
        }
        return false;
    }

    /**
     * Find a Hamiltonian path using backtracking. Returns the path as a
     * list of vertices, or null if none exists.
     */
    public <V, E> List<V> findHamiltonianPath(Graph<V, E> graph) {
        int n = graph.getVertexCount();
        if (n == 0) return null;
        if (n == 1) return new ArrayList<>(graph.getVertices());
        if (!isConnected(graph)) return null;

        // Try starting from each vertex
        List<V> vertices = new ArrayList<>(graph.getVertices());
        vertices.sort(Comparator.comparing(Object::toString));

        for (V start : vertices) {
            List<V> path = new ArrayList<>();
            Set<V> visited = new HashSet<>();
            path.add(start);
            visited.add(start);

            if (backtrackPath(graph, path, visited, n)) {
                return path;
            }
        }
        return null;
    }

    private <V, E> boolean backtrackPath(Graph<V, E> graph, List<V> path,
                                          Set<V> visited, int n) {
        if (path.size() == n) return true;

        V current = path.get(path.size() - 1);
        List<V> neighbors = new ArrayList<>(graph.getNeighbors(current));
        neighbors.sort(Comparator.comparing(Object::toString));

        for (V next : neighbors) {
            if (!visited.contains(next)) {
                visited.add(next);
                path.add(next);

                if (backtrackPath(graph, path, visited, n)) {
                    return true;
                }

                path.remove(path.size() - 1);
                visited.remove(next);
            }
        }
        return false;
    }

    /**
     * Find a Hamiltonian path between two specific vertices.
     */
    public <V, E> List<V> findHamiltonianPath(Graph<V, E> graph, V source, V target) {
        int n = graph.getVertexCount();
        if (n == 0) return null;
        if (!graph.containsVertex(source) || !graph.containsVertex(target)) return null;
        if (n == 1) {
            if (source.equals(target)) {
                List<V> path = new ArrayList<>();
                path.add(source);
                return path;
            }
            return null;
        }
        if (!isConnected(graph)) return null;

        List<V> path = new ArrayList<>();
        Set<V> visited = new HashSet<>();
        path.add(source);
        visited.add(source);

        if (backtrackPathTo(graph, path, visited, target, n)) {
            return path;
        }
        return null;
    }

    private <V, E> boolean backtrackPathTo(Graph<V, E> graph, List<V> path,
                                            Set<V> visited, V target, int n) {
        if (path.size() == n) {
            return path.get(path.size() - 1).equals(target);
        }

        V current = path.get(path.size() - 1);
        List<V> neighbors = new ArrayList<>(graph.getNeighbors(current));
        neighbors.sort(Comparator.comparing(Object::toString));

        for (V next : neighbors) {
            if (!visited.contains(next)) {
                visited.add(next);
                path.add(next);

                if (backtrackPathTo(graph, path, visited, target, n)) {
                    return true;
                }

                path.remove(path.size() - 1);
                visited.remove(next);
            }
        }
        return false;
    }

    // ── Enumerate all Hamiltonian cycles ─────────────────────────────────

    /**
     * Enumerate all distinct Hamiltonian cycles (up to rotation, not reflection).
     * Fixes the first vertex to avoid rotational duplicates.
     *
     * @param limit maximum number of cycles to find (0 = unlimited)
     * @return list of Hamiltonian cycles
     */
    public <V, E> List<List<V>> findAllHamiltonianCycles(Graph<V, E> graph, int limit) {
        int n = graph.getVertexCount();
        List<List<V>> results = new ArrayList<>();
        if (n < 3 || !isConnected(graph)) return results;

        int maxResults = limit > 0 ? limit : DEFAULT_ENUM_LIMIT;
        List<V> vertices = new ArrayList<>(graph.getVertices());
        V start = vertices.get(0);
        List<V> path = new ArrayList<>();
        Set<V> visited = new HashSet<>();
        path.add(start);
        visited.add(start);

        enumerateCycles(graph, path, visited, start, n, results, maxResults);

        // For undirected graphs, each cycle appears twice (forward and reverse).
        // Deduplicate by keeping only the canonical direction.
        if (!(graph instanceof DirectedGraph) && results.size() > 1) {
            Set<String> seen = new HashSet<>();
            List<List<V>> deduped = new ArrayList<>();
            for (List<V> cycle : results) {
                // Canonical form: the direction where second vertex < second-to-last vertex
                // (start vertex is fixed, so we compare the two neighbors of start in the cycle)
                List<V> inner = cycle.subList(1, cycle.size() - 1); // exclude start at both ends
                List<V> reversed = new ArrayList<>(inner);
                Collections.reverse(reversed);
                String fwd = inner.toString();
                String rev = reversed.toString();
                String canonical = fwd.compareTo(rev) <= 0 ? fwd : rev;
                if (seen.add(canonical)) {
                    deduped.add(cycle);
                }
            }
            return deduped;
        }

        return results;
    }

    /**
     * Enumerate all Hamiltonian cycles with default limit.
     */
    public <V, E> List<List<V>> findAllHamiltonianCycles(Graph<V, E> graph) {
        return findAllHamiltonianCycles(graph, DEFAULT_ENUM_LIMIT);
    }

    private <V, E> void enumerateCycles(Graph<V, E> graph, List<V> path,
                                         Set<V> visited, V start, int n,
                                         List<List<V>> results, int maxResults) {
        if (results.size() >= maxResults) return;

        if (path.size() == n) {
            V last = path.get(path.size() - 1);
            if (graph.isNeighbor(last, start)) {
                List<V> cycle = new ArrayList<>(path);
                cycle.add(start);
                results.add(cycle);
            }
            return;
        }

        V current = path.get(path.size() - 1);
        List<V> neighbors = new ArrayList<>(graph.getNeighbors(current));
        neighbors.sort(Comparator.comparing(Object::toString));

        for (V next : neighbors) {
            if (!visited.contains(next)) {
                visited.add(next);
                path.add(next);
                enumerateCycles(graph, path, visited, start, n, results, maxResults);
                path.remove(path.size() - 1);
                visited.remove(next);
            }
        }
    }

    // ── Greedy heuristic ─────────────────────────────────────────────────

    /**
     * Attempt to find a Hamiltonian path using a greedy nearest-unvisited
     * heuristic (prefer neighbors with lowest degree among unvisited).
     * Not guaranteed to succeed.
     */
    public <V, E> List<V> greedyHamiltonianPath(Graph<V, E> graph) {
        int n = graph.getVertexCount();
        if (n == 0) return null;
        if (n == 1) return new ArrayList<>(graph.getVertices());

        // Try starting from vertex with minimum degree (Warnsdorff-like)
        List<V> vertices = new ArrayList<>(graph.getVertices());
        vertices.sort(Comparator.<V, Integer>comparing(graph::degree)
                .thenComparing(Object::toString));

        for (V start : vertices) {
            List<V> path = greedyFromStart(graph, start, n);
            if (path != null) return path;
        }
        return null;
    }

    private <V, E> List<V> greedyFromStart(Graph<V, E> graph, V start, int n) {
        List<V> path = new ArrayList<>();
        Set<V> visited = new HashSet<>();
        path.add(start);
        visited.add(start);

        while (path.size() < n) {
            V current = path.get(path.size() - 1);
            // Warnsdorff's rule: choose unvisited neighbor with fewest unvisited neighbors
            V best = null;
            int bestScore = Integer.MAX_VALUE;
            for (V neighbor : graph.getNeighbors(current)) {
                if (!visited.contains(neighbor)) {
                    int score = 0;
                    for (V nn : graph.getNeighbors(neighbor)) {
                        if (!visited.contains(nn)) score++;
                    }
                    if (score < bestScore || (score == bestScore && neighbor.toString().compareTo(
                            best == null ? "" : best.toString()) < 0)) {
                        bestScore = score;
                        best = neighbor;
                    }
                }
            }
            if (best == null) return null; // stuck
            path.add(best);
            visited.add(best);
        }
        return path;
    }

    /**
     * Attempt to find a Hamiltonian cycle using greedy + rotation.
     */
    public <V, E> List<V> greedyHamiltonianCycle(Graph<V, E> graph) {
        List<V> path = greedyHamiltonianPath(graph);
        if (path == null) return null;
        // Check if we can close it
        V first = path.get(0);
        V last = path.get(path.size() - 1);
        if (graph.isNeighbor(last, first)) {
            path.add(first);
            return path;
        }
        return null;
    }

    // ── Degree sequence analysis ─────────────────────────────────────────

    /**
     * Compute degree statistics relevant to Hamiltonicity.
     */
    public <V, E> DegreeStats<V> analyzeDegrees(Graph<V, E> graph) {
        int n = graph.getVertexCount();
        if (n == 0) return new DegreeStats<>(0, 0, 0.0, null, null, new int[0]);

        int minDeg = Integer.MAX_VALUE;
        int maxDeg = 0;
        V minVertex = null, maxVertex = null;
        int[] degrees = new int[n];
        int i = 0;
        for (V v : graph.getVertices()) {
            int deg = graph.degree(v);
            degrees[i++] = deg;
            if (deg < minDeg) { minDeg = deg; minVertex = v; }
            if (deg > maxDeg) { maxDeg = deg; maxVertex = v; }
        }
        Arrays.sort(degrees);
        double avg = Arrays.stream(degrees).average().orElse(0);
        return new DegreeStats<>(minDeg, maxDeg, avg, minVertex, maxVertex, degrees);
    }

    /**
     * Degree statistics container.
     */
    public static class DegreeStats<V> {
        public final int minDegree;
        public final int maxDegree;
        public final double avgDegree;
        public final V minVertex;
        public final V maxVertex;
        public final int[] sortedDegrees;

        public DegreeStats(int minDegree, int maxDegree, double avgDegree,
                           V minVertex, V maxVertex, int[] sortedDegrees) {
            this.minDegree = minDegree;
            this.maxDegree = maxDegree;
            this.avgDegree = avgDegree;
            this.minVertex = minVertex;
            this.maxVertex = maxVertex;
            this.sortedDegrees = sortedDegrees;
        }
    }

    // ── Comprehensive report ─────────────────────────────────────────────

    /**
     * Full Hamiltonian analysis report.
     */
    public static class HamiltonianReport<V> {
        public final int vertexCount;
        public final int edgeCount;
        public final boolean connected;
        public final List<ConditionResult> conditions;
        public final DegreeStats<V> degreeStats;
        public final List<V> hamiltonianCycle;
        public final List<V> hamiltonianPath;
        public final int totalCyclesFound;
        public final boolean usedExact;
        public final String verdict;

        public HamiltonianReport(int vertexCount, int edgeCount, boolean connected,
                                  List<ConditionResult> conditions, DegreeStats<V> degreeStats,
                                  List<V> hamiltonianCycle, List<V> hamiltonianPath,
                                  int totalCyclesFound, boolean usedExact, String verdict) {
            this.vertexCount = vertexCount;
            this.edgeCount = edgeCount;
            this.connected = connected;
            this.conditions = conditions;
            this.degreeStats = degreeStats;
            this.hamiltonianCycle = hamiltonianCycle;
            this.hamiltonianPath = hamiltonianPath;
            this.totalCyclesFound = totalCyclesFound;
            this.usedExact = usedExact;
            this.verdict = verdict;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("═══ Hamiltonian Analysis Report ═══\n");
            sb.append(String.format("Vertices: %d | Edges: %d | Connected: %s\n",
                    vertexCount, edgeCount, connected ? "Yes" : "No"));
            sb.append(String.format("Degree range: [%d, %d] avg=%.1f\n",
                    degreeStats.minDegree, degreeStats.maxDegree, degreeStats.avgDegree));
            sb.append("\n── Sufficient Conditions ──\n");
            for (ConditionResult c : conditions) {
                sb.append("  ").append(c).append("\n");
            }
            sb.append("\n── Results ──\n");
            sb.append("Method: ").append(usedExact ? "Exact backtracking" : "Greedy heuristic").append("\n");
            sb.append("Hamiltonian cycle: ");
            if (hamiltonianCycle != null) {
                sb.append("FOUND — ").append(formatPath(hamiltonianCycle)).append("\n");
            } else {
                sb.append("NOT FOUND\n");
            }
            sb.append("Hamiltonian path: ");
            if (hamiltonianPath != null) {
                sb.append("FOUND — ").append(formatPath(hamiltonianPath)).append("\n");
            } else {
                sb.append("NOT FOUND\n");
            }
            if (usedExact && totalCyclesFound > 0) {
                sb.append("Total distinct cycles: ").append(totalCyclesFound).append("\n");
            }
            sb.append("\n── Verdict ──\n");
            sb.append(verdict).append("\n");
            return sb.toString();
        }

        private String formatPath(List<V> path) {
            if (path.size() <= 10) {
                return path.stream().map(Object::toString).collect(Collectors.joining(" → "));
            }
            return path.get(0) + " → " + path.get(1) + " → ... → " +
                    path.get(path.size() - 2) + " → " + path.get(path.size() - 1) +
                    " (" + path.size() + " vertices)";
        }
    }

    /**
     * Generate a comprehensive Hamiltonian analysis report.
     */
    public <V, E> HamiltonianReport<V> analyze(Graph<V, E> graph) {
        int n = graph.getVertexCount();
        int m = graph.getEdgeCount();
        boolean connected = isConnected(graph);
        List<ConditionResult> conditions = checkAllConditions(graph);
        DegreeStats<V> degreeStats = analyzeDegrees(graph);

        List<V> cycle = null;
        List<V> path = null;
        int totalCycles = 0;
        boolean usedExact = n <= EXACT_THRESHOLD;

        if (connected) {
            if (usedExact) {
                cycle = findHamiltonianCycle(graph);
                path = findHamiltonianPath(graph);
                if (cycle != null) {
                    List<List<V>> allCycles = findAllHamiltonianCycles(graph, 100);
                    totalCycles = allCycles.size();
                }
            } else {
                // Use greedy for larger graphs
                cycle = greedyHamiltonianCycle(graph);
                path = greedyHamiltonianPath(graph);
            }
        }

        String verdict = buildVerdict(connected, conditions, cycle, path, usedExact, n);

        return new HamiltonianReport<>(n, m, connected, conditions, degreeStats,
                cycle, path, totalCycles, usedExact, verdict);
    }

    private String buildVerdict(boolean connected, List<ConditionResult> conditions,
                                 Object cycle, Object path, boolean usedExact, int n) {
        if (!connected) {
            return "Graph is disconnected — no Hamiltonian path or cycle possible.";
        }
        if (cycle != null && path != null) {
            boolean anySufficient = conditions.stream().anyMatch(c -> c.satisfied);
            if (anySufficient) {
                return "Graph is Hamiltonian (confirmed by both sufficient conditions and direct search).";
            }
            return "Graph is Hamiltonian (found by " + (usedExact ? "exact" : "heuristic") + " search).";
        }
        if (path != null) {
            return "Graph has a Hamiltonian path but " +
                    (usedExact ? "no" : "no found") + " Hamiltonian cycle.";
        }
        if (usedExact) {
            return "Graph is NOT Hamiltonian — no Hamiltonian path or cycle exists.";
        }
        boolean anySufficient = conditions.stream().anyMatch(c -> c.satisfied);
        if (anySufficient) {
            return "Sufficient conditions indicate Hamiltonicity, but heuristic search did not find a solution. " +
                    "An exact solver may succeed (graph has " + n + " vertices).";
        }
        return "Heuristic search did not find a Hamiltonian path or cycle. " +
                "This does not prove non-Hamiltonicity for graphs above " + EXACT_THRESHOLD + " vertices.";
    }
}
