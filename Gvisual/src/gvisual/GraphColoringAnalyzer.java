package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Graph coloring using the Welsh-Powell algorithm -- a greedy heuristic
 * that assigns colors to vertices so no two adjacent vertices share the
 * same color. Vertices are processed in decreasing order of degree, which
 * typically produces fewer colors than naive greedy approaches.
 *
 * <p>Applications include scheduling (exams, meetings), register allocation,
 * frequency assignment, and map coloring. The number of colors used is an
 * upper bound on the chromatic number.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * GraphColoringAnalyzer analyzer = new GraphColoringAnalyzer(graph);
 * GraphColoringAnalyzer.ColoringResult result = analyzer.compute();
 * int colors = result.getChromaticBound();
 * Map&lt;String, Integer&gt; assignment = result.getColorAssignment();
 * </pre>
 *
 * @author zalenix
 */
public class GraphColoringAnalyzer {

    private final Graph<String, edge> graph;

    /**
     * Creates a new GraphColoringAnalyzer for the given graph.
     *
     * @param graph the JUNG graph to color
     * @throws IllegalArgumentException if graph is null
     */
    public GraphColoringAnalyzer(Graph<String, edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
    }

    /**
     * Computes a proper vertex coloring using Welsh-Powell (greedy by
     * decreasing degree). Colors are integers starting at 0.
     *
     * @return a ColoringResult with the assignment and analytics
     */
    public ColoringResult compute() {
        Collection<String> vertices = graph.getVertices();
        int n = vertices.size();

        if (n == 0) {
            return new ColoringResult(
                Collections.emptyMap(),
                Collections.emptyMap(),
                0, 0, true
            );
        }

        // Sort vertices by degree descending, break ties alphabetically
        List<String> sorted = new ArrayList<>(vertices);
        sorted.sort((a, b) -> {
            int cmp = Integer.compare(graph.degree(b), graph.degree(a));
            return cmp != 0 ? cmp : a.compareTo(b);
        });

        Map<String, Integer> colorAssignment = new HashMap<>();
        int maxColor = -1;

        for (String vertex : sorted) {
            // Find colors used by neighbors
            Set<Integer> usedColors = new HashSet<>();
            for (String neighbor : graph.getNeighbors(vertex)) {
                Integer neighborColor = colorAssignment.get(neighbor);
                if (neighborColor != null) {
                    usedColors.add(neighborColor);
                }
            }

            // Assign the smallest available color
            int color = 0;
            while (usedColors.contains(color)) {
                color++;
            }
            colorAssignment.put(vertex, color);
            if (color > maxColor) {
                maxColor = color;
            }
        }

        int chromaticBound = maxColor + 1;

        // Build color classes (which vertices share each color)
        Map<Integer, List<String>> colorClasses = new HashMap<>();
        for (int c = 0; c < chromaticBound; c++) {
            colorClasses.put(c, new ArrayList<>());
        }
        for (Map.Entry<String, Integer> entry : colorAssignment.entrySet()) {
            colorClasses.get(entry.getValue()).add(entry.getKey());
        }
        // Sort each class for deterministic output
        for (List<String> cls : colorClasses.values()) {
            Collections.sort(cls);
        }

        boolean valid = validate(colorAssignment);

        return new ColoringResult(colorAssignment, colorClasses, chromaticBound, n, valid);
    }

    /**
     * Validates that no two adjacent vertices share the same color.
     *
     * @param assignment vertex-to-color mapping
     * @return true if the coloring is proper
     */
    private boolean validate(Map<String, Integer> assignment) {
        for (edge e : graph.getEdges()) {
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

    /**
     * Computes a coloring using a specific vertex ordering instead of
     * Welsh-Powell's degree ordering. Useful for comparing strategies.
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

        Map<String, Integer> colorAssignment = new HashMap<>();
        int maxColor = -1;

        for (String vertex : vertexOrder) {
            Set<Integer> usedColors = new HashSet<>();
            for (String neighbor : graph.getNeighbors(vertex)) {
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

        int chromaticBound = maxColor + 1;

        Map<Integer, List<String>> colorClasses = new HashMap<>();
        for (int c = 0; c < chromaticBound; c++) {
            colorClasses.put(c, new ArrayList<>());
        }
        for (Map.Entry<String, Integer> entry : colorAssignment.entrySet()) {
            colorClasses.get(entry.getValue()).add(entry.getKey());
        }
        for (List<String> cls : colorClasses.values()) {
            Collections.sort(cls);
        }

        boolean valid = validate(colorAssignment);
        int n = colorAssignment.size();

        return new ColoringResult(colorAssignment, colorClasses, chromaticBound, n, valid);
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

        /**
         * Returns the vertex-to-color assignment. Colors are 0-indexed
         * integers.
         */
        public Map<String, Integer> getColorAssignment() {
            return colorAssignment;
        }

        /**
         * Returns the color of a specific vertex, or -1 if not found.
         */
        public int getColor(String vertex) {
            Integer c = colorAssignment.get(vertex);
            return c != null ? c : -1;
        }

        /**
         * Returns the color classes -- a map from color index to the
         * list of vertices assigned that color.
         */
        public Map<Integer, List<String>> getColorClasses() {
            return colorClasses;
        }

        /**
         * Returns the vertices assigned to a specific color, or an
         * empty list if the color index is invalid.
         */
        public List<String> getVerticesWithColor(int color) {
            List<String> list = colorClasses.get(color);
            return list != null ? list : Collections.emptyList();
        }

        /**
         * Returns the upper bound on the chromatic number (number of
         * colors used). The actual chromatic number may be lower.
         */
        public int getChromaticBound() {
            return chromaticBound;
        }

        /**
         * Returns the number of vertices that were colored.
         */
        public int getVertexCount() {
            return vertexCount;
        }

        /**
         * Returns true if the coloring is valid (no adjacent vertices
         * share a color).
         */
        public boolean isValid() {
            return valid;
        }

        /**
         * Returns the size of the largest color class.
         */
        public int getLargestClassSize() {
            int max = 0;
            for (List<String> cls : colorClasses.values()) {
                if (cls.size() > max) {
                    max = cls.size();
                }
            }
            return max;
        }

        /**
         * Returns the size of the smallest color class.
         */
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

        /**
         * Returns a summary map with key metrics.
         */
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

        /**
         * Returns a human-readable summary string.
         */
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
