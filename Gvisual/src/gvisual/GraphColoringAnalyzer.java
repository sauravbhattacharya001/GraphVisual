package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Comprehensive graph coloring analyzer -- greedy coloring with multiple
 * vertex orderings (natural, largest-first, smallest-last, DSatur),
 * chromatic number bounds, k-colorability checking, coloring verification,
 * color class analysis, Edge chromatic number estimation (Vizing's theorem),
 * and report generation.
 *
 * <p>Graph coloring assigns labels (colors) to vertices so that no two
 * adjacent vertices share the same color. The minimum number of colors
 * needed is the <b>chromatic number</b> χ(G).</p>
 *
 * <p>Supported vertex orderings for greedy coloring:</p>
 * <ul>
 *   <li><b>Natural</b> — alphabetical vertex order</li>
 *   <li><b>Largest-first</b> — decreasing degree (Welsh-Powell)</li>
 *   <li><b>Smallest-last</b> — iteratively remove minimum-degree vertex</li>
 *   <li><b>DSatur</b> — saturation degree ordering (dynamic)</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 * GraphColoringAnalyzer analyzer = new GraphColoringAnalyzer(graph);
 * GraphColoringAnalyzer.ColoringResult result = analyzer.compute();
 * GraphColoringAnalyzer.ColoringResult dsatur = analyzer.computeDSatur();
 * boolean canColor = analyzer.isKColorable(3);
 * String report = analyzer.generateReport();
 * </pre>
 *
 * @author zalenix
 */
public class GraphColoringAnalyzer {

    /** Vertex ordering strategies for greedy coloring. */
    public enum VertexOrdering {
        NATURAL,
        LARGEST_FIRST,
        SMALLEST_LAST,
        DSATUR
    }

    private final Graph<String, Edge> graph;

    /**
     * Creates a new GraphColoringAnalyzer for the given graph.
     *
     * @param graph the JUNG graph to color
     * @throws IllegalArgumentException if graph is null
     */
    public GraphColoringAnalyzer(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
    }

    // ── Greedy Coloring ─────────────────────────────────────────────

    /**
     * Computes a proper vertex coloring using the largest-first (Welsh-Powell)
     * ordering. Colors are integers starting at 0.
     *
     * @return a ColoringResult with the assignment and analytics
     */
    public ColoringResult compute() {
        return computeWithOrdering(VertexOrdering.LARGEST_FIRST);
    }

    /**
     * Computes a proper vertex coloring using the specified ordering strategy.
     *
     * @param ordering the vertex ordering to use
     * @return a ColoringResult with the assignment and analytics
     * @throws IllegalArgumentException if ordering is null
     */
    public ColoringResult computeWithOrdering(VertexOrdering ordering) {
        if (ordering == null) {
            throw new IllegalArgumentException("Ordering must not be null");
        }
        if (ordering == VertexOrdering.DSATUR) {
            return computeDSatur();
        }

        Collection<String> vertices = graph.getVertices();
        int n = vertices.size();

        if (n == 0) {
            return new ColoringResult(
                Collections.emptyMap(),
                Collections.emptyMap(),
                0, 0, true
            );
        }

        List<String> sorted = getOrderedVertices(ordering);
        return greedyColor(sorted);
    }

    /**
     * Computes a proper vertex coloring using a custom vertex order.
     *
     * @param vertexOrder the order in which to process vertices
     * @return a ColoringResult with the assignment
     * @throws IllegalArgumentException if vertexOrder is null or contains
     *         vertices not in the graph
     */
    public ColoringResult computeWithOrder(List<String> vertexOrder) {
        if (vertexOrder == null) {
            throw new IllegalArgumentException("Vertex order must not be null");
        }

        for (String v : vertexOrder) {
            if (!graph.containsVertex(v)) {
                throw new IllegalArgumentException(
                    "Vertex not in graph: " + v);
            }
        }

        return greedyColor(vertexOrder);
    }

    // ── DSatur Coloring ─────────────────────────────────────────────

    /**
     * Computes a proper vertex coloring using the DSatur (Degree of
     * Saturation) algorithm. DSatur dynamically selects the uncolored
     * vertex with the highest saturation degree (number of distinct
     * colors among its neighbors), breaking ties by vertex degree.
     *
     * <p>DSatur typically produces better results than static orderings
     * and is optimal for bipartite and cycle graphs.</p>
     *
     * @return a ColoringResult with the assignment
     */
    public ColoringResult computeDSatur() {
        Collection<String> vertices = graph.getVertices();
        int n = vertices.size();

        if (n == 0) {
            return new ColoringResult(
                Collections.emptyMap(),
                Collections.emptyMap(),
                0, 0, true
            );
        }

        Map<String, Integer> colorAssignment = new HashMap<>();
        Map<String, Set<Integer>> saturation = new HashMap<>();
        Set<String> uncolored = new HashSet<>(vertices);

        for (String v : vertices) {
            saturation.put(v, new HashSet<>());
        }

        int maxColor = -1;

        while (!uncolored.isEmpty()) {
            // Pick vertex with max saturation degree, tie-break by
            // graph degree descending, then alphabetically
            String best = null;
            int bestSat = -1;
            int bestDeg = -1;

            for (String v : uncolored) {
                int sat = saturation.get(v).size();
                int deg = graph.degree(v);
                if (sat > bestSat
                    || (sat == bestSat && deg > bestDeg)
                    || (sat == bestSat && deg == bestDeg
                        && (best == null || v.compareTo(best) < 0))) {
                    best = v;
                    bestSat = sat;
                    bestDeg = deg;
                }
            }

            // Assign smallest available color
            Set<Integer> usedColors = new HashSet<>();
            Collection<String> neighbors = GraphUtils.neighborsOf(graph, best);
            for (String neighbor : neighbors) {
                Integer nc = colorAssignment.get(neighbor);
                if (nc != null) {
                    usedColors.add(nc);
                }
            }

            int color = 0;
            while (usedColors.contains(color)) {
                color++;
            }
            colorAssignment.put(best, color);
            if (color > maxColor) {
                maxColor = color;
            }

            uncolored.remove(best);

            // Update saturation of uncolored neighbors
            if (neighbors != null) {
                for (String neighbor : neighbors) {
                    if (uncolored.contains(neighbor)) {
                        saturation.get(neighbor).add(color);
                    }
                }
            }
        }

        int chromaticBound = maxColor + 1;
        Map<Integer, List<String>> colorClasses = buildColorClasses(colorAssignment, chromaticBound);
        boolean valid = validate(colorAssignment);

        return new ColoringResult(colorAssignment, colorClasses, chromaticBound, n, valid);
    }

    // ── Chromatic Number Bounds ─────────────────────────────────────

    /**
     * Computes a lower bound on the chromatic number using greedy clique
     * detection. The chromatic number is at least the size of the largest
     * clique found.
     *
     * @return lower bound on chromatic number (clique number estimate)
     */
    public int chromaticLowerBound() {
        Collection<String> vertices = graph.getVertices();
        if (vertices.isEmpty()) {
            return 0;
        }

        int maxClique = 1;

        // Greedy clique from each vertex
        for (String start : vertices) {
            List<String> clique = new ArrayList<>();
            clique.add(start);

            // Sort candidates by degree descending for better heuristic
            List<String> candidates = new ArrayList<>();
            Collection<String> neighbors = GraphUtils.neighborsOf(graph, start);
            candidates.addAll(neighbors);
            candidates.sort((a, b) -> Integer.compare(graph.degree(b), graph.degree(a)));

            for (String candidate : candidates) {
                boolean adjacent = true;
                for (String member : clique) {
                    if (!graph.isNeighbor(candidate, member)) {
                        adjacent = false;
                        break;
                    }
                }
                if (adjacent) {
                    clique.add(candidate);
                }
            }

            if (clique.size() > maxClique) {
                maxClique = clique.size();
            }
        }

        return maxClique;
    }

    /**
     * Computes an upper bound on the chromatic number using greedy coloring
     * (largest-first ordering).
     *
     * @return upper bound on chromatic number
     */
    public int chromaticUpperBound() {
        return compute().getChromaticBound();
    }

    /**
     * Returns the chromatic number bounds as a two-element array
     * [lower, upper].
     *
     * @return array with [lower bound, upper bound]
     */
    public int[] chromaticBounds() {
        return new int[]{chromaticLowerBound(), chromaticUpperBound()};
    }

    // ── k-Colorability ──────────────────────────────────────────────

    /**
     * Checks if the graph can be colored with at most k colors. Uses
     * DSatur coloring result as a quick check, then falls back to
     * backtracking for small graphs.
     *
     * @param k the number of colors
     * @return true if the graph is k-colorable
     * @throws IllegalArgumentException if k is less than 0
     */
    public boolean isKColorable(int k) {
        if (k < 0) {
            throw new IllegalArgumentException("k must be non-negative");
        }

        Collection<String> vertices = graph.getVertices();
        int n = vertices.size();

        if (n == 0) {
            return true;
        }
        if (k == 0) {
            return false;
        }

        // Quick check: DSatur result
        ColoringResult dsatur = computeDSatur();
        if (dsatur.getChromaticBound() <= k) {
            return true;
        }

        // Quick check: clique lower bound
        int lower = chromaticLowerBound();
        if (lower > k) {
            return false;
        }

        // Backtracking for small graphs (n <= 20)
        if (n <= 20) {
            List<String> vList = new ArrayList<>(vertices);
            Collections.sort(vList);
            Map<String, Integer> assignment = new HashMap<>();
            return backtrackColor(vList, 0, k, assignment);
        }

        // For larger graphs, rely on heuristic result
        return dsatur.getChromaticBound() <= k;
    }

    private boolean backtrackColor(List<String> vertices, int idx, int k,
                                   Map<String, Integer> assignment) {
        if (idx == vertices.size()) {
            return true;
        }
        String v = vertices.get(idx);
        for (int color = 0; color < k; color++) {
            if (canAssign(v, color, assignment)) {
                assignment.put(v, color);
                if (backtrackColor(vertices, idx + 1, k, assignment)) {
                    return true;
                }
                assignment.remove(v);
            }
        }
        return false;
    }

    private boolean canAssign(String vertex, int color,
                              Map<String, Integer> assignment) {
        Collection<String> neighbors = GraphUtils.neighborsOf(graph, vertex);
        for (String neighbor : neighbors) {
            Integer nc = assignment.get(neighbor);
            if (nc != null && nc == color) {
                return false;
            }
        }
        return true;
    }

    // ── Coloring Verification ───────────────────────────────────────

    /**
     * Verifies that a given coloring is valid (proper): no two adjacent
     * vertices share the same color.
     *
     * @param assignment vertex-to-color mapping
     * @return true if the coloring is proper
     * @throws IllegalArgumentException if assignment is null
     */
    public boolean verifyColoring(Map<String, Integer> assignment) {
        if (assignment == null) {
            throw new IllegalArgumentException("Assignment must not be null");
        }
        return validate(assignment);
    }

    /**
     * Returns a list of edges that violate the coloring (both endpoints
     * have the same color).
     *
     * @param assignment vertex-to-color mapping
     * @return list of conflicting edges (as String pairs)
     * @throws IllegalArgumentException if assignment is null
     */
    public List<String[]> findConflicts(Map<String, Integer> assignment) {
        if (assignment == null) {
            throw new IllegalArgumentException("Assignment must not be null");
        }
        List<String[]> conflicts = new ArrayList<>();
        for (Edge e : graph.getEdges()) {
            String v1 = graph.getEndpoints(e).getFirst();
            String v2 = graph.getEndpoints(e).getSecond();
            Integer c1 = assignment.get(v1);
            Integer c2 = assignment.get(v2);
            if (c1 != null && c2 != null && c1.equals(c2)) {
                conflicts.add(new String[]{v1, v2});
            }
        }
        return conflicts;
    }

    // ── Color Class Analysis ────────────────────────────────────────

    /**
     * Analyzes color classes from a coloring result. Returns a map with
     * statistics including class sizes, balance ratio, and independence
     * verification.
     *
     * @param result the coloring result to analyze
     * @return analysis map with metrics
     * @throws IllegalArgumentException if result is null
     */
    public Map<String, Object> analyzeColorClasses(ColoringResult result) {
        if (result == null) {
            throw new IllegalArgumentException("Result must not be null");
        }

        Map<String, Object> analysis = new LinkedHashMap<>();
        Map<Integer, List<String>> classes = result.getColorClasses();

        analysis.put("numColors", result.getChromaticBound());
        analysis.put("numVertices", result.getVertexCount());

        if (classes.isEmpty()) {
            analysis.put("largestClass", 0);
            analysis.put("smallestClass", 0);
            analysis.put("balanceRatio", 1.0);
            analysis.put("allIndependent", true);
            return analysis;
        }

        int largest = result.getLargestClassSize();
        int smallest = result.getSmallestClassSize();
        analysis.put("largestClass", largest);
        analysis.put("smallestClass", smallest);
        analysis.put("balanceRatio", largest > 0
            ? (double) smallest / largest : 1.0);

        // Verify each color class is an independent set
        boolean allIndependent = true;
        for (List<String> cls : classes.values()) {
            for (int i = 0; i < cls.size(); i++) {
                for (int j = i + 1; j < cls.size(); j++) {
                    if (graph.isNeighbor(cls.get(i), cls.get(j))) {
                        allIndependent = false;
                        break;
                    }
                }
                if (!allIndependent) break;
            }
            if (!allIndependent) break;
        }
        analysis.put("allIndependent", allIndependent);

        // Class sizes list
        List<Integer> classSizes = new ArrayList<>();
        for (int c = 0; c < result.getChromaticBound(); c++) {
            List<String> cls = classes.get(c);
            classSizes.add(cls != null ? cls.size() : 0);
        }
        analysis.put("classSizes", classSizes);

        return analysis;
    }

    // ── Edge Chromatic Number (Vizing's Theorem) ────────────────────

    /**
     * Estimates the edge chromatic number bounds using Vizing's theorem.
     * For any simple graph, the edge chromatic number χ'(G) satisfies:
     * Δ(G) ≤ χ'(G) ≤ Δ(G) + 1, where Δ(G) is the maximum degree.
     *
     * @return array with [lower bound, upper bound] for edge chromatic number
     */
    public int[] edgeChromaticBounds() {
        Collection<String> vertices = graph.getVertices();
        if (vertices.isEmpty() || graph.getEdgeCount() == 0) {
            return new int[]{0, 0};
        }

        int maxDegree = 0;
        for (String v : vertices) {
            int deg = graph.degree(v);
            if (deg > maxDegree) {
                maxDegree = deg;
            }
        }

        return new int[]{maxDegree, maxDegree + 1};
    }

    /**
     * Returns the maximum vertex degree (Δ), which is the Vizing lower
     * bound for the edge chromatic number.
     *
     * @return maximum degree
     */
    public int maxDegree() {
        int max = 0;
        for (String v : graph.getVertices()) {
            int deg = graph.degree(v);
            if (deg > max) {
                max = deg;
            }
        }
        return max;
    }

    // ── Report Generation ───────────────────────────────────────────

    /**
     * Generates a comprehensive coloring report including greedy and
     * DSatur results, chromatic bounds, Edge chromatic bounds, and
     * color class analysis.
     *
     * @return formatted report string
     */
    public String generateReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Graph Coloring Analysis Report ===\n\n");

        int n = graph.getVertexCount();
        int m = graph.getEdgeCount();
        sb.append(String.format("Graph: %d vertices, %d edges%n", n, m));
        sb.append(String.format("Max degree (Δ): %d%n%n", maxDegree()));

        // Greedy coloring results with different orderings
        sb.append("--- Greedy Coloring Results ---\n");
        for (VertexOrdering ordering : VertexOrdering.values()) {
            ColoringResult result = computeWithOrdering(ordering);
            sb.append(String.format("  %-15s: %d colors (valid: %s)%n",
                ordering, result.getChromaticBound(), result.isValid()));
        }
        sb.append("\n");

        // Chromatic number bounds
        int lower = chromaticLowerBound();
        int upper = chromaticUpperBound();
        sb.append("--- Chromatic Number Bounds ---\n");
        sb.append(String.format("  Lower bound (clique): %d%n", lower));
        sb.append(String.format("  Upper bound (greedy): %d%n", upper));
        if (lower == upper) {
            sb.append(String.format("  Exact chromatic number: %d%n", lower));
        }
        sb.append("\n");

        // Edge chromatic number (Vizing)
        int[] edgeBounds = edgeChromaticBounds();
        sb.append("--- Edge Chromatic Number (Vizing's Theorem) ---\n");
        sb.append(String.format("  Lower bound (Δ): %d%n", edgeBounds[0]));
        sb.append(String.format("  Upper bound (Δ+1): %d%n", edgeBounds[1]));
        sb.append("\n");

        // Best coloring detail (DSatur)
        ColoringResult best = computeDSatur();
        sb.append("--- Best Coloring (DSatur) ---\n");
        sb.append(best.toString());

        return sb.toString();
    }

    // ── Internal Helpers ────────────────────────────────────────────

    private List<String> getOrderedVertices(VertexOrdering ordering) {
        List<String> vertices = new ArrayList<>(graph.getVertices());

        switch (ordering) {
            case NATURAL:
                Collections.sort(vertices);
                break;
            case LARGEST_FIRST:
                vertices.sort((a, b) -> {
                    int cmp = Integer.compare(graph.degree(b), graph.degree(a));
                    return cmp != 0 ? cmp : a.compareTo(b);
                });
                break;
            case SMALLEST_LAST:
                vertices = smallestLastOrder();
                break;
            default:
                Collections.sort(vertices);
                break;
        }
        return vertices;
    }

    /**
     * Computes the smallest-last ordering: iteratively remove the
     * minimum-degree vertex from the remaining graph, then reverse.
     */
    private List<String> smallestLastOrder() {
        Set<String> remaining = new HashSet<>(graph.getVertices());
        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(graph);

        List<String> order = new ArrayList<>();
        while (!remaining.isEmpty()) {
            // Find min degree vertex
            String minV = null;
            int minDeg = Integer.MAX_VALUE;
            for (String v : remaining) {
                int deg = 0;
                for (String nb : adj.get(v)) {
                    if (remaining.contains(nb)) {
                        deg++;
                    }
                }
                if (deg < minDeg || (deg == minDeg && (minV == null || v.compareTo(minV) < 0))) {
                    minV = v;
                    minDeg = deg;
                }
            }
            order.add(minV);
            remaining.remove(minV);
        }

        // Reverse to get smallest-last order
        Collections.reverse(order);
        return order;
    }

    private ColoringResult greedyColor(List<String> vertexOrder) {
        Map<String, Integer> colorAssignment = new HashMap<>();
        int maxColor = -1;

        for (String vertex : vertexOrder) {
            Set<Integer> usedColors = new HashSet<>();
            Collection<String> neighbors = GraphUtils.neighborsOf(graph, vertex);
            for (String neighbor : neighbors) {
                Integer neighborColor = colorAssignment.get(neighbor);
                if (neighborColor != null) {
                    usedColors.add(neighborColor);
                }
            }

            int color = 0;
            while (usedColors.contains(color)) {
                color++;
            }
            colorAssignment.put(vertex, color);
            if (color > maxColor) {
                maxColor = color;
            }
        }

        int chromaticBound = vertexOrder.isEmpty() ? 0 : maxColor + 1;
        Map<Integer, List<String>> colorClasses = buildColorClasses(colorAssignment, chromaticBound);
        boolean valid = validate(colorAssignment);
        int n = colorAssignment.size();

        return new ColoringResult(colorAssignment, colorClasses, chromaticBound, n, valid);
    }

    private Map<Integer, List<String>> buildColorClasses(
            Map<String, Integer> assignment, int numColors) {
        Map<Integer, List<String>> colorClasses = new HashMap<>();
        for (int c = 0; c < numColors; c++) {
            colorClasses.put(c, new ArrayList<>());
        }
        for (Map.Entry<String, Integer> entry : assignment.entrySet()) {
            colorClasses.get(entry.getValue()).add(entry.getKey());
        }
        for (List<String> cls : colorClasses.values()) {
            Collections.sort(cls);
        }
        return colorClasses;
    }

    private boolean validate(Map<String, Integer> assignment) {
        for (Edge e : graph.getEdges()) {
            String v1 = graph.getEndpoints(e).getFirst();
            String v2 = graph.getEndpoints(e).getSecond();
            Integer c1 = assignment.get(v1);
            Integer c2 = assignment.get(v2);
            if (c1 != null && c2 != null && c1.equals(c2)) {
                return false;
            }
        }
        return true;
    }

    // =============================================
    //  Result class
    // =============================================

    /**
     * Holds the results of a graph coloring computation.
     */
    public static class ColoringResult {

        private final Map<String, Integer> colorAssignment;
        private final Map<Integer, List<String>> colorClasses;
        private final int chromaticBound;
        private final int vertexCount;
        private final boolean valid;

        ColoringResult(
                Map<String, Integer> colorAssignment,
                Map<Integer, List<String>> colorClasses,
                int chromaticBound,
                int vertexCount,
                boolean valid) {
            this.colorAssignment = Collections.unmodifiableMap(colorAssignment);
            this.colorClasses = Collections.unmodifiableMap(colorClasses);
            this.chromaticBound = chromaticBound;
            this.vertexCount = vertexCount;
            this.valid = valid;
        }

        /** Returns the vertex-to-color assignment. Colors are 0-indexed. */
        public Map<String, Integer> getColorAssignment() {
            return colorAssignment;
        }

        /** Returns the color of a specific vertex, or -1 if not found. */
        public int getColor(String vertex) {
            Integer c = colorAssignment.get(vertex);
            return c != null ? c : -1;
        }

        /** Returns color classes -- map from color index to vertex list. */
        public Map<Integer, List<String>> getColorClasses() {
            return colorClasses;
        }

        /** Returns the vertices assigned to a specific color. */
        public List<String> getVerticesWithColor(int color) {
            List<String> list = colorClasses.get(color);
            return list != null ? list : Collections.emptyList();
        }

        /** Returns the upper bound on the chromatic number. */
        public int getChromaticBound() {
            return chromaticBound;
        }

        /** Returns the number of vertices colored. */
        public int getVertexCount() {
            return vertexCount;
        }

        /** Returns true if the coloring is valid (proper). */
        public boolean isValid() {
            return valid;
        }

        /** Returns the size of the largest color class. */
        public int getLargestClassSize() {
            int max = 0;
            for (List<String> cls : colorClasses.values()) {
                if (cls.size() > max) {
                    max = cls.size();
                }
            }
            return max;
        }

        /** Returns the size of the smallest color class. */
        public int getSmallestClassSize() {
            if (colorClasses.isEmpty()) {
                return 0;
            }
            int min = Integer.MAX_VALUE;
            for (List<String> cls : colorClasses.values()) {
                if (cls.size() < min) {
                    min = cls.size();
                }
            }
            return min;
        }

        /** Returns a summary map with key metrics. */
        public Map<String, Object> getSummary() {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("vertexCount", vertexCount);
            summary.put("chromaticBound", chromaticBound);
            summary.put("valid", valid);
            summary.put("largestClass", getLargestClassSize());
            summary.put("smallestClass", getSmallestClassSize());

            Map<Integer, Integer> classSizes = new LinkedHashMap<>();
            for (Map.Entry<Integer, List<String>> entry : colorClasses.entrySet()) {
                classSizes.put(entry.getKey(), entry.getValue().size());
            }
            summary.put("classSizes", classSizes);

            return summary;
        }

        /** Returns a human-readable summary string. */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Graph Coloring Result\n");
            sb.append("--------------------\n");
            sb.append(String.format("Vertices: %d%n", vertexCount));
            sb.append(String.format("Colors used (chromatic bound): %d%n", chromaticBound));
            sb.append(String.format("Valid coloring: %s%n", valid));
            sb.append(String.format("Largest color class: %d%n", getLargestClassSize()));
            sb.append(String.format("Smallest color class: %d%n", getSmallestClassSize()));
            sb.append("\nColor classes:\n");
            for (Map.Entry<Integer, List<String>> entry : colorClasses.entrySet()) {
                sb.append(String.format("  Color %d (%d vertices): %s%n",
                    entry.getKey(), entry.getValue().size(), entry.getValue()));
            }
            return sb.toString();
        }
    }
}
