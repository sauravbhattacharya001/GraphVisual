package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Circular Graph Layout — arranges vertices on one or more concentric
 * circles with configurable ordering strategies.
 *
 * <h3>Algorithm</h3>
 * <p>Places all vertices evenly around a circle, then optionally reorders
 * them to minimize Edge crossings or group related nodes together:</p>
 * <ol>
 *   <li><b>Vertex ordering</b> — selects a strategy (alphabetical, degree,
 *       community, BFS, or minimize-crossings heuristic).</li>
 *   <li><b>Angle assignment</b> — distributes ordered vertices at equal
 *       angular intervals around the circle.</li>
 *   <li><b>Multi-ring support</b> — optionally places high-degree "hub"
 *       nodes on an inner ring for better readability.</li>
 * </ol>
 *
 * <h3>Ordering Strategies</h3>
 * <ul>
 *   <li><b>ALPHABETICAL</b> — simple name-based order</li>
 *   <li><b>DEGREE</b> — highest-degree nodes first (hubs clustered)</li>
 *   <li><b>COMMUNITY</b> — groups nodes by connected component, then by
 *       degree within each component</li>
 *   <li><b>BFS</b> — breadth-first traversal order from the highest-degree
 *       node (neighbors stay close)</li>
 *   <li><b>MINIMIZE_CROSSINGS</b> — greedy barycenter heuristic that
 *       iteratively swaps adjacent nodes to reduce Edge crossings</li>
 * </ul>
 *
 * <h3>Features</h3>
 * <ul>
 *   <li>Single-ring or dual-ring (hubs on inner circle) layout</li>
 *   <li>Configurable canvas size, padding, and start angle</li>
 *   <li>Edge crossing count metric</li>
 *   <li>SVG export with node labels and edges</li>
 *   <li>Layout quality report</li>
 *   <li>Works with any JUNG {@code Graph<String, Edge>}</li>
 * </ul>
 *
 * <h3>When to use</h3>
 * <ul>
 *   <li>Visualizing social networks (who connects to whom)</li>
 *   <li>Ring/cycle topologies</li>
 *   <li>Comparing node degrees at a glance</li>
 *   <li>Small-to-medium graphs (up to ~200 nodes) for clarity</li>
 *   <li>When force-directed layout is too chaotic</li>
 * </ul>
 *
 * <h3>Example usage</h3>
 * <pre>{@code
 * CircularLayout layout = new CircularLayout.Builder(graph)
 *     .ordering(CircularLayout.Ordering.MINIMIZE_CROSSINGS)
 *     .dualRing(true)
 *     .hubThreshold(0.8)
 *     .build();
 * layout.compute();
 *
 * Map<String, double[]> positions = layout.getPositions();
 * System.out.println(layout.getReport());
 * String svg = layout.toSvg();
 * }</pre>
 *
 * @author zalenix
 */
public class CircularLayout {

    /** Vertex ordering strategy. */
    public enum Ordering {
        /** Sort vertices alphabetically by name. */
        ALPHABETICAL,
        /** Sort by degree descending (hubs first). */
        DEGREE,
        /** Group by connected component, then by degree within each group. */
        COMMUNITY,
        /** BFS traversal from the highest-degree node. */
        BFS,
        /** Greedy heuristic to minimize Edge crossings. */
        MINIMIZE_CROSSINGS
    }

    private final Graph<String, Edge> graph;
    private final double width;
    private final double height;
    private final double padding;
    private final double startAngle;
    private final Ordering ordering;
    private final boolean dualRing;
    private final double hubThreshold; // percentile for inner ring (0.0–1.0)

    private Map<String, double[]> positions;
    private List<String> orderedVertices;
    private int edgeCrossings;
    private boolean computed;
    private Set<String> hubNodes;

    private CircularLayout(Builder builder) {
        this.graph = builder.graph;
        this.width = builder.width;
        this.height = builder.height;
        this.padding = builder.padding;
        this.startAngle = builder.startAngle;
        this.ordering = builder.ordering;
        this.dualRing = builder.dualRing;
        this.hubThreshold = builder.hubThreshold;
        this.positions = new HashMap<>();
        this.hubNodes = new HashSet<>();
        this.computed = false;
    }

    /** Builder for flexible construction. */
    public static class Builder {
        private final Graph<String, Edge> graph;
        private double width = 800;
        private double height = 800;
        private double padding = 50;
        private double startAngle = -Math.PI / 2; // top of circle
        private Ordering ordering = Ordering.MINIMIZE_CROSSINGS;
        private boolean dualRing = false;
        private double hubThreshold = 0.9;

        public Builder(Graph<String, Edge> graph) {
            if (graph == null) throw new IllegalArgumentException("Graph must not be null");
            this.graph = graph;
        }

        public Builder width(double w) { this.width = w; return this; }
        public Builder height(double h) { this.height = h; return this; }
        public Builder padding(double p) { this.padding = p; return this; }
        public Builder startAngle(double radians) { this.startAngle = radians; return this; }
        public Builder ordering(Ordering o) { this.ordering = o; return this; }
        public Builder dualRing(boolean d) { this.dualRing = d; return this; }
        public Builder hubThreshold(double t) { this.hubThreshold = t; return this; }

        public CircularLayout build() { return new CircularLayout(this); }
    }

    /**
     * Computes the layout positions.
     * @return this for chaining
     */
    public CircularLayout compute() {
        if (graph.getVertexCount() == 0) {
            orderedVertices = Collections.emptyList();
            computed = true;
            edgeCrossings = 0;
            return this;
        }

        // Step 1: Order vertices
        orderedVertices = orderVertices();

        // Step 2: Identify hub nodes for dual ring
        if (dualRing) {
            identifyHubs();
        }

        // Step 3: Place on circle(s)
        placeVertices();

        // Step 4: Count crossings
        edgeCrossings = countCrossings();

        computed = true;
        return this;
    }

    private List<String> orderVertices() {
        List<String> vertices = new ArrayList<>(graph.getVertices());
        switch (ordering) {
            case ALPHABETICAL:
                Collections.sort(vertices);
                break;
            case DEGREE:
                vertices.sort((a, b) -> Integer.compare(
                    graph.degree(b), graph.degree(a)));
                break;
            case COMMUNITY:
                return orderByCommunity(vertices);
            case BFS:
                return orderByBfs(vertices);
            case MINIMIZE_CROSSINGS:
                return minimizeCrossings(vertices);
            default:
                Collections.sort(vertices);
        }
        return vertices;
    }

    private List<String> orderByCommunity(List<String> vertices) {
        // Find connected components and group them
        Map<String, Integer> componentMap = new HashMap<>();
        int componentId = 0;
        Set<String> visited = new HashSet<>();

        for (String v : vertices) {
            if (!visited.contains(v)) {
                Queue<String> queue = new ArrayDeque<>();
                queue.add(v);
                visited.add(v);
                while (!queue.isEmpty()) {
                    String curr = queue.poll();
                    componentMap.put(curr, componentId);
                    for (String neighbor : graph.getNeighbors(curr)) {
                        if (!visited.contains(neighbor)) {
                            visited.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
                componentId++;
            }
        }

        // Sort: by component, then by degree desc within component
        vertices.sort((a, b) -> {
            int cmp = Integer.compare(componentMap.get(a), componentMap.get(b));
            if (cmp != 0) return cmp;
            return Integer.compare(graph.degree(b), graph.degree(a));
        });
        return vertices;
    }

    private List<String> orderByBfs(List<String> vertices) {
        // Start BFS from highest-degree node
        String start = vertices.stream()
            .max(Comparator.comparingInt(v -> graph.degree(v)))
            .orElse(vertices.get(0));

        List<String> ordered = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();

        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            String curr = queue.poll();
            ordered.add(curr);
            List<String> neighbors = new ArrayList<>(graph.getNeighbors(curr));
            neighbors.sort((a, b) -> Integer.compare(
                graph.degree(b), graph.degree(a)));
            for (String n : neighbors) {
                if (!visited.contains(n)) {
                    visited.add(n);
                    queue.add(n);
                }
            }
        }
        // Add any disconnected vertices
        for (String v : vertices) {
            if (!visited.contains(v)) {
                ordered.add(v);
            }
        }
        return ordered;
    }

    private List<String> minimizeCrossings(List<String> vertices) {
        // Start with BFS order as initial solution
        List<String> best = orderByBfs(vertices);
        int bestCrossings = countCrossingsForOrder(best);

        // Greedy adjacent-swap improvement
        boolean improved = true;
        int maxPasses = 50;
        int pass = 0;
        while (improved && pass < maxPasses) {
            improved = false;
            pass++;
            for (int i = 0; i < best.size() - 1; i++) {
                // Try swapping adjacent pair
                Collections.swap(best, i, i + 1);
                int newCrossings = countCrossingsForOrder(best);
                if (newCrossings < bestCrossings) {
                    bestCrossings = newCrossings;
                    improved = true;
                } else {
                    // Swap back
                    Collections.swap(best, i, i + 1);
                }
            }
        }
        return best;
    }

    private int countCrossingsForOrder(List<String> order) {
        Map<String, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            indexMap.put(order.get(i), i);
        }

        List<int[]> edgeIndices = new ArrayList<>();
        for (Edge e : graph.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            if (v1 != null && v2 != null && indexMap.containsKey(v1) && indexMap.containsKey(v2)) {
                int i1 = indexMap.get(v1);
                int i2 = indexMap.get(v2);
                if (i1 != i2) {
                    edgeIndices.add(new int[]{Math.min(i1, i2), Math.max(i1, i2)});
                }
            }
        }

        int crossings = 0;
        int n = order.size();
        for (int i = 0; i < edgeIndices.size(); i++) {
            for (int j = i + 1; j < edgeIndices.size(); j++) {
                int[] e1 = edgeIndices.get(i);
                int[] e2 = edgeIndices.get(j);
                // Two chords on a circle cross iff their endpoints interleave
                if (chordsIntersect(e1[0], e1[1], e2[0], e2[1], n)) {
                    crossings++;
                }
            }
        }
        return crossings;
    }

    /**
     * Two chords (a,b) and (c,d) on a circle of n points cross
     * iff exactly one of c,d lies in the arc from a to b (shorter arc).
     */
    private boolean chordsIntersect(int a, int b, int c, int d, int n) {
        // Ensure a < b and c < d
        if (a > b) { int t = a; a = b; b = t; }
        if (c > d) { int t = c; c = d; d = t; }
        // Chords cross if exactly one of (c,d) is between a and b
        boolean cBetween = a < c && c < b;
        boolean dBetween = a < d && d < b;
        return cBetween != dBetween;
    }

    private void identifyHubs() {
        if (orderedVertices.size() <= 3) return;
        List<Integer> degrees = orderedVertices.stream()
            .map(v -> graph.degree(v))
            .sorted()
            .collect(Collectors.toList());
        int thresholdIndex = (int) (degrees.size() * hubThreshold);
        int thresholdDegree = degrees.get(Math.min(thresholdIndex, degrees.size() - 1));
        if (thresholdDegree <= 1) return; // no meaningful hubs

        for (String v : orderedVertices) {
            if (graph.degree(v) >= thresholdDegree) {
                hubNodes.add(v);
            }
        }
        // Need at least 2 hub nodes for inner ring to make sense
        if (hubNodes.size() < 2 || hubNodes.size() == orderedVertices.size()) {
            hubNodes.clear();
        }
    }

    private void placeVertices() {
        double cx = width / 2.0;
        double cy = height / 2.0;
        double outerRadius = Math.min(width, height) / 2.0 - padding;
        double innerRadius = outerRadius * 0.5;

        List<String> outerNodes = new ArrayList<>();
        List<String> innerNodes = new ArrayList<>();

        for (String v : orderedVertices) {
            if (hubNodes.contains(v)) {
                innerNodes.add(v);
            } else {
                outerNodes.add(v);
            }
        }

        // Place outer ring
        placeOnRing(outerNodes, cx, cy, outerRadius);
        // Place inner ring (hubs)
        if (!innerNodes.isEmpty()) {
            placeOnRing(innerNodes, cx, cy, innerRadius);
        }
    }

    private void placeOnRing(List<String> nodes, double cx, double cy, double radius) {
        if (nodes.isEmpty()) return;
        double angleStep = 2.0 * Math.PI / nodes.size();
        for (int i = 0; i < nodes.size(); i++) {
            double angle = startAngle + i * angleStep;
            double x = cx + radius * Math.cos(angle);
            double y = cy + radius * Math.sin(angle);
            positions.put(nodes.get(i), new double[]{x, y});
        }
    }

    private int countCrossings() {
        return countCrossingsForOrder(orderedVertices);
    }

    // ── Accessors ──────────────────────────────────────────────────────

    /** Returns vertex positions (vertex → [x, y]). */
    public Map<String, double[]> getPositions() {
        ensureComputed();
        return Collections.unmodifiableMap(positions);
    }

    /** Returns vertices in layout order. */
    public List<String> getOrderedVertices() {
        ensureComputed();
        return Collections.unmodifiableList(orderedVertices);
    }

    /** Returns Edge crossing count. */
    public int getEdgeCrossings() {
        ensureComputed();
        return edgeCrossings;
    }

    /** Returns which nodes are on the inner (hub) ring. */
    public Set<String> getHubNodes() {
        ensureComputed();
        return Collections.unmodifiableSet(hubNodes);
    }

    /** Returns the ordering strategy used. */
    public Ordering getOrdering() { return ordering; }

    /** Whether dual-ring mode is enabled. */
    public boolean isDualRing() { return dualRing; }

    // ── Report ─────────────────────────────────────────────────────────

    /**
     * Returns a human-readable quality report.
     */
    public String getReport() {
        ensureComputed();
        StringBuilder sb = new StringBuilder();
        sb.append("=== Circular Layout Report ===\n");
        sb.append(String.format("Vertices: %d\n", orderedVertices.size()));
        sb.append(String.format("Edges: %d\n", graph.getEdgeCount()));
        sb.append(String.format("Ordering: %s\n", ordering));
        sb.append(String.format("Dual ring: %s\n", dualRing));
        if (dualRing && !hubNodes.isEmpty()) {
            sb.append(String.format("Hub nodes (inner ring): %d\n", hubNodes.size()));
            sb.append(String.format("  Hubs: %s\n", hubNodes));
        }
        sb.append(String.format("Edge crossings: %d\n", edgeCrossings));
        if (graph.getEdgeCount() > 0) {
            int maxCrossings = graph.getEdgeCount() * (graph.getEdgeCount() - 1) / 2;
            double ratio = maxCrossings > 0
                ? (1.0 - (double) edgeCrossings / maxCrossings) * 100.0
                : 100.0;
            sb.append(String.format("Crossing avoidance: %.1f%%\n", ratio));
        }
        sb.append(String.format("Canvas: %.0f × %.0f (padding %.0f)\n",
            width, height, padding));
        return sb.toString();
    }

    // ── SVG Export ─────────────────────────────────────────────────────

    /**
     * Exports the layout as an SVG string.
     * @return SVG markup
     */
    public String toSvg() {
        ensureComputed();
        StringBuilder svg = new StringBuilder();
        svg.append(String.format(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%.0f\" height=\"%.0f\" "
            + "viewBox=\"0 0 %.0f %.0f\">\n",
            width, height, width, height));
        svg.append("<style>\n");
        svg.append("  .Edge { stroke: #666; stroke-width: 1; opacity: 0.5; }\n");
        svg.append("  .node { fill: #4A90D9; stroke: #2C5F8A; stroke-width: 1.5; }\n");
        svg.append("  .hub  { fill: #E74C3C; stroke: #C0392B; stroke-width: 2; }\n");
        svg.append("  .label { font-family: sans-serif; font-size: 10px; "
            + "fill: #333; text-anchor: middle; }\n");
        svg.append("</style>\n");
        svg.append(String.format("<rect width=\"%.0f\" height=\"%.0f\" fill=\"#fafafa\"/>\n",
            width, height));

        // Draw edges
        for (Edge e : graph.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            if (v1 != null && v2 != null && positions.containsKey(v1) && positions.containsKey(v2)) {
                double[] p1 = positions.get(v1);
                double[] p2 = positions.get(v2);
                svg.append(String.format(
                    "  <line class=\"Edge\" x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\"/>\n",
                    p1[0], p1[1], p2[0], p2[1]));
            }
        }

        // Draw nodes
        double nodeRadius = Math.max(4, Math.min(12, 200.0 / Math.max(1, orderedVertices.size())));
        for (String v : orderedVertices) {
            double[] pos = positions.get(v);
            String cssClass = hubNodes.contains(v) ? "hub" : "node";
            svg.append(String.format(
                "  <circle class=\"%s\" cx=\"%.1f\" cy=\"%.1f\" r=\"%.1f\"/>\n",
                cssClass, pos[0], pos[1], nodeRadius));
            // Label (only if not too many nodes)
            if (orderedVertices.size() <= 60) {
                svg.append(String.format(
                    "  <text class=\"label\" x=\"%.1f\" y=\"%.1f\">%s</text>\n",
                    pos[0], pos[1] - nodeRadius - 4, escapeXml(v)));
            }
        }

        svg.append("</svg>\n");
        return svg.toString();
    }

    private String escapeXml(String s) {
        return ExportUtils.escapeXml(s);
    }

    private void ensureComputed() {
        if (!computed) {
            throw new IllegalStateException("Call compute() before accessing results");
        }
    }
}
