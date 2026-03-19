package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Hierarchical Graph Layout — computes layered 2D positions for directed
 * graphs and DAGs using the <b>Sugiyama</b> algorithm framework.
 *
 * <h3>Algorithm</h3>
 * <p>Arranges nodes in horizontal layers (top-to-bottom or left-to-right)
 * to reveal directed flow and dependency structure:</p>
 * <ol>
 *   <li><b>Cycle removal</b> — reverses a minimal set of back-edges so
 *       the graph becomes acyclic (greedy heuristic).</li>
 *   <li><b>Layer assignment</b> — places each node in a layer based on its
 *       longest-path depth from source nodes.</li>
 *   <li><b>Crossing reduction</b> — reorders nodes within each layer to
 *       minimize edge crossings (barycenter heuristic, multi-pass).</li>
 *   <li><b>X-coordinate assignment</b> — positions nodes within layers
 *       to minimize edge bends and balance spacing.</li>
 * </ol>
 *
 * <h3>Features</h3>
 * <ul>
 *   <li>Works on DAGs, general directed graphs, and undirected graphs</li>
 *   <li>Top-to-bottom or left-to-right orientation</li>
 *   <li>Configurable layer spacing and node spacing</li>
 *   <li>Barycenter crossing minimization with configurable sweep count</li>
 *   <li>Edge crossing count metric</li>
 *   <li>SVG export with directional arrows</li>
 *   <li>Layout quality report</li>
 *   <li>Layer statistics and critical path identification</li>
 * </ul>
 *
 * <h3>When to use</h3>
 * <ul>
 *   <li>Dependency graphs and build systems</li>
 *   <li>Call graphs and control flow</li>
 *   <li>Organizational hierarchies</li>
 *   <li>Any directed graph where flow direction matters</li>
 * </ul>
 *
 * @see ForceDirectedLayout for undirected / general-purpose layout
 */
public class HierarchicalLayout {

    /** Layout orientation. */
    public enum Orientation {
        /** Layers flow top-to-bottom; edges go downward. */
        TOP_TO_BOTTOM,
        /** Layers flow left-to-right; edges go rightward. */
        LEFT_TO_RIGHT
    }

    // ── Configuration ────────────────────────────────────────────────

    private final Graph<String, Edge> graph;
    private final double layerSpacing;
    private final double nodeSpacing;
    private final int crossingSweeps;
    private final Orientation orientation;
    private final double width;
    private final double height;

    // ── Computed state ───────────────────────────────────────────────

    private Map<String, double[]> positions;
    private Map<String, Integer> layerAssignment;
    private List<List<String>> layers;
    private List<String> criticalPath;
    private int crossingCount;
    private Set<Edge> reversedEdges;
    private boolean computed;

    // ── Constructors ─────────────────────────────────────────────────

    /**
     * Creates a HierarchicalLayout with default settings.
     *
     * @param graph the JUNG graph to lay out
     * @throws IllegalArgumentException if graph is null
     */
    public HierarchicalLayout(Graph<String, Edge> graph) {
        this(graph, 120, 80, 24, Orientation.TOP_TO_BOTTOM, 1200, 800);
    }

    /**
     * Creates a HierarchicalLayout with full configuration.
     *
     * @param graph          the JUNG graph to lay out
     * @param layerSpacing   vertical (or horizontal) distance between layers
     * @param nodeSpacing    distance between nodes within the same layer
     * @param crossingSweeps number of barycenter passes for crossing reduction
     * @param orientation    TOP_TO_BOTTOM or LEFT_TO_RIGHT
     * @param width          canvas width
     * @param height         canvas height
     * @throws IllegalArgumentException if parameters are invalid
     */
    public HierarchicalLayout(Graph<String, Edge> graph, double layerSpacing,
                               double nodeSpacing, int crossingSweeps,
                               Orientation orientation,
                               double width, double height) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        if (layerSpacing <= 0) {
            throw new IllegalArgumentException(
                    "layerSpacing must be positive, got: " + layerSpacing);
        }
        if (nodeSpacing <= 0) {
            throw new IllegalArgumentException(
                    "nodeSpacing must be positive, got: " + nodeSpacing);
        }
        if (crossingSweeps < 0) {
            throw new IllegalArgumentException(
                    "crossingSweeps must be non-negative, got: " + crossingSweeps);
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "width and height must be positive");
        }
        this.graph = graph;
        this.layerSpacing = layerSpacing;
        this.nodeSpacing = nodeSpacing;
        this.crossingSweeps = crossingSweeps;
        this.orientation = orientation;
        this.width = width;
        this.height = height;
        this.computed = false;
    }

    // ── Computation ──────────────────────────────────────────────────

    /**
     * Computes the hierarchical layout.
     *
     * @return this layout (for chaining)
     */
    public HierarchicalLayout compute() {
        List<String> vertices = new ArrayList<String>(graph.getVertices());
        int n = vertices.size();

        if (n == 0) {
            positions = new HashMap<String, double[]>();
            layerAssignment = new HashMap<String, Integer>();
            layers = new ArrayList<List<String>>();
            criticalPath = new ArrayList<String>();
            crossingCount = 0;
            reversedEdges = new HashSet<Edge>();
            computed = true;
            return this;
        }

        // Build directed adjacency from the JUNG graph
        Map<String, Set<String>> successors = new HashMap<String, Set<String>>();
        Map<String, Set<String>> predecessors = new HashMap<String, Set<String>>();
        for (String v : vertices) {
            successors.put(v, new HashSet<String>());
            predecessors.put(v, new HashSet<String>());
        }
        for (Edge e : graph.getEdges()) {
            String u = e.getVertex1();
            String v = e.getVertex2();
            if (u != null && v != null && !u.equals(v)
                    && successors.containsKey(u) && successors.containsKey(v)) {
                successors.get(u).add(v);
                predecessors.get(v).add(u);
            }
        }

        // Step 1: Cycle removal (greedy DFS-based)
        reversedEdges = new HashSet<Edge>();
        Map<String, Set<String>> dagSuccessors = removeCycles(
                vertices, successors, predecessors);
        Map<String, Set<String>> dagPredecessors = invertAdjacency(
                vertices, dagSuccessors);

        // Step 2: Layer assignment (longest path from sources)
        layerAssignment = assignLayers(vertices, dagSuccessors, dagPredecessors);

        // Step 3: Group vertices by layer
        int maxLayer = 0;
        for (int layer : layerAssignment.values()) {
            maxLayer = Math.max(maxLayer, layer);
        }
        layers = new ArrayList<List<String>>(maxLayer + 1);
        for (int i = 0; i <= maxLayer; i++) {
            layers.add(new ArrayList<String>());
        }
        for (String v : vertices) {
            int layer = layerAssignment.getOrDefault(v, 0);
            layers.get(layer).add(v);
        }

        // Step 4: Crossing reduction (barycenter heuristic)
        reduceCrossings(dagSuccessors, dagPredecessors);

        // Step 5: Assign coordinates
        positions = assignCoordinates();

        // Step 6: Find critical (longest) path
        criticalPath = findCriticalPath(vertices, dagSuccessors, dagPredecessors);

        // Count actual crossings
        crossingCount = countEdgeCrossings(dagSuccessors);

        computed = true;
        return this;
    }

    // ── Step 1: Cycle removal ────────────────────────────────────────

    private Map<String, Set<String>> removeCycles(
            List<String> vertices,
            Map<String, Set<String>> successors,
            Map<String, Set<String>> predecessors) {

        // Deep-copy adjacency
        Map<String, Set<String>> dag = new HashMap<String, Set<String>>();
        for (String v : vertices) {
            dag.put(v, new HashSet<String>(successors.get(v)));
        }

        // DFS to find back-edges
        Set<String> visited = new HashSet<String>();
        Set<String> inStack = new HashSet<String>();
        List<String[]> backEdges = new ArrayList<String[]>();

        for (String v : vertices) {
            if (!visited.contains(v)) {
                dfsBackEdges(v, dag, visited, inStack, backEdges);
            }
        }

        // Reverse back-edges to break cycles
        for (String[] be : backEdges) {
            dag.get(be[0]).remove(be[1]);
            dag.get(be[1]).add(be[0]);

            // Track the original edge objects
            for (Edge e : graph.getEdges()) {
                if (be[0].equals(e.getVertex1()) && be[1].equals(e.getVertex2())) {
                    reversedEdges.add(e);
                }
            }
        }

        return dag;
    }

    private void dfsBackEdges(String v,
                               Map<String, Set<String>> adj,
                               Set<String> visited,
                               Set<String> inStack,
                               List<String[]> backEdges) {
        visited.add(v);
        inStack.add(v);

        for (String w : adj.getOrDefault(v, Collections.emptySet())) {
            if (inStack.contains(w)) {
                backEdges.add(new String[]{v, w});
            } else if (!visited.contains(w)) {
                dfsBackEdges(w, adj, visited, inStack, backEdges);
            }
        }

        inStack.remove(v);
    }

    // ── Step 2: Layer assignment ─────────────────────────────────────

    private Map<String, Integer> assignLayers(
            List<String> vertices,
            Map<String, Set<String>> succ,
            Map<String, Set<String>> pred) {

        Map<String, Integer> depth = new HashMap<String, Integer>();

        // Find sources (no predecessors)
        List<String> sources = new ArrayList<String>();
        for (String v : vertices) {
            if (pred.get(v) == null || pred.get(v).isEmpty()) {
                sources.add(v);
            }
        }

        // If no sources (all nodes have predecessors), use all vertices
        if (sources.isEmpty()) {
            sources.addAll(vertices);
        }

        // BFS/topological longest-path from sources
        Queue<String> queue = new LinkedList<String>();
        for (String s : sources) {
            depth.put(s, 0);
            queue.add(s);
        }

        while (!queue.isEmpty()) {
            String u = queue.poll();
            int uDepth = depth.getOrDefault(u, 0);
            for (String w : succ.getOrDefault(u, Collections.emptySet())) {
                int newDepth = uDepth + 1;
                if (!depth.containsKey(w) || depth.get(w) < newDepth) {
                    depth.put(w, newDepth);
                    queue.add(w);
                }
            }
        }

        // Assign any remaining unvisited vertices to layer 0
        for (String v : vertices) {
            if (!depth.containsKey(v)) {
                depth.put(v, 0);
            }
        }

        return depth;
    }

    // ── Step 3: Crossing reduction (barycenter) ──────────────────────

    private void reduceCrossings(
            Map<String, Set<String>> succ,
            Map<String, Set<String>> pred) {

        for (int sweep = 0; sweep < crossingSweeps; sweep++) {
            // Forward sweep: fix layer i, reorder layer i+1
            for (int i = 0; i < layers.size() - 1; i++) {
                reorderLayerByBarycenter(layers.get(i), layers.get(i + 1),
                        succ, true);
            }
            // Backward sweep: fix layer i+1, reorder layer i
            for (int i = layers.size() - 1; i > 0; i--) {
                reorderLayerByBarycenter(layers.get(i), layers.get(i - 1),
                        pred, false);
            }
        }
    }

    private void reorderLayerByBarycenter(
            List<String> fixedLayer, List<String> freeLayer,
            Map<String, Set<String>> adj, boolean forward) {

        // Build position index for the fixed layer
        Map<String, Integer> fixedPos = new HashMap<String, Integer>();
        for (int i = 0; i < fixedLayer.size(); i++) {
            fixedPos.put(fixedLayer.get(i), i);
        }

        // Compute barycenter for each node in the free layer
        Map<String, Double> bary = new HashMap<String, Double>();
        for (String v : freeLayer) {
            Set<String> neighbors;
            if (forward) {
                // v is in layer i+1; its "fixed" neighbors are predecessors in layer i
                neighbors = new HashSet<String>();
                for (String u : fixedLayer) {
                    if (adj.getOrDefault(u, Collections.emptySet()).contains(v)) {
                        neighbors.add(u);
                    }
                }
            } else {
                // v is in layer i; its "fixed" neighbors are successors in layer i+1
                neighbors = new HashSet<String>();
                for (String u : fixedLayer) {
                    if (adj.getOrDefault(u, Collections.emptySet()).contains(v)) {
                        neighbors.add(u);
                    }
                }
            }

            if (neighbors.isEmpty()) {
                bary.put(v, Double.MAX_VALUE); // no connections = stay put
            } else {
                double sum = 0;
                for (String u : neighbors) {
                    Integer pos = fixedPos.get(u);
                    if (pos != null) sum += pos;
                }
                bary.put(v, sum / neighbors.size());
            }
        }

        // Sort free layer by barycenter, preserving order for ties
        freeLayer.sort(Comparator.comparingDouble(
                (String v) -> bary.getOrDefault(v, Double.MAX_VALUE)));
    }

    // ── Step 4: Coordinate assignment ────────────────────────────────

    private Map<String, double[]> assignCoordinates() {
        Map<String, double[]> pos = new HashMap<String, double[]>();

        int numLayers = layers.size();
        if (numLayers == 0) return pos;

        for (int layerIdx = 0; layerIdx < numLayers; layerIdx++) {
            List<String> layer = layers.get(layerIdx);
            int layerSize = layer.size();

            for (int nodeIdx = 0; nodeIdx < layerSize; nodeIdx++) {
                double layerCoord;
                double nodeCoord;

                // Center each layer
                double totalLayerSpan = (numLayers - 1) * layerSpacing;
                double layerOffset = (orientation == Orientation.TOP_TO_BOTTOM
                        ? height : width) / 2.0 - totalLayerSpan / 2.0;
                layerCoord = layerOffset + layerIdx * layerSpacing;

                double totalNodeSpan = (layerSize - 1) * nodeSpacing;
                double nodeOffset = (orientation == Orientation.TOP_TO_BOTTOM
                        ? width : height) / 2.0 - totalNodeSpan / 2.0;
                nodeCoord = nodeOffset + nodeIdx * nodeSpacing;

                double x, y;
                if (orientation == Orientation.TOP_TO_BOTTOM) {
                    x = nodeCoord;
                    y = layerCoord;
                } else {
                    x = layerCoord;
                    y = nodeCoord;
                }

                pos.put(layer.get(nodeIdx), new double[]{x, y});
            }
        }

        return pos;
    }

    // ── Critical path ────────────────────────────────────────────────

    private List<String> findCriticalPath(
            List<String> vertices,
            Map<String, Set<String>> succ,
            Map<String, Set<String>> pred) {

        // Find the deepest node(s), then backtrack
        String deepest = null;
        int maxDepth = -1;
        for (String v : vertices) {
            int d = layerAssignment.getOrDefault(v, 0);
            if (d > maxDepth) {
                maxDepth = d;
                deepest = v;
            }
        }

        if (deepest == null) return new ArrayList<String>();

        // Backtrack from deepest to a source
        List<String> path = new ArrayList<String>();
        String current = deepest;
        while (current != null) {
            path.add(current);
            String best = null;
            int bestDepth = -1;
            for (String p : pred.getOrDefault(current, Collections.emptySet())) {
                int d = layerAssignment.getOrDefault(p, 0);
                if (d > bestDepth) {
                    bestDepth = d;
                    best = p;
                }
            }
            // Only go to a predecessor in a strictly lower layer
            if (best != null && bestDepth < layerAssignment.getOrDefault(current, 0)) {
                current = best;
            } else {
                current = null;
            }
        }

        Collections.reverse(path);
        return path;
    }

    // ── Crossing count ───────────────────────────────────────────────

    private int countEdgeCrossings(Map<String, Set<String>> succ) {
        int crossings = 0;

        for (int i = 0; i < layers.size() - 1; i++) {
            List<String> upper = layers.get(i);
            List<String> lower = layers.get(i + 1);

            // Build position maps
            Map<String, Integer> upperPos = new HashMap<String, Integer>();
            for (int j = 0; j < upper.size(); j++) {
                upperPos.put(upper.get(j), j);
            }
            Map<String, Integer> lowerPos = new HashMap<String, Integer>();
            for (int j = 0; j < lower.size(); j++) {
                lowerPos.put(lower.get(j), j);
            }

            // Collect Edges between these two layers
            List<int[]> edges = new ArrayList<int[]>();
            for (String u : upper) {
                for (String v : succ.getOrDefault(u, Collections.emptySet())) {
                    Integer vPos = lowerPos.get(v);
                    if (vPos != null) {
                        edges.add(new int[]{upperPos.get(u), vPos});
                    }
                }
            }

            // Count crossings: edge (u1,v1) crosses (u2,v2) if
            // (u1 < u2 && v1 > v2) or (u1 > u2 && v1 < v2)
            for (int a = 0; a < edges.size(); a++) {
                for (int b = a + 1; b < edges.size(); b++) {
                    int u1 = edges.get(a)[0], v1 = edges.get(a)[1];
                    int u2 = edges.get(b)[0], v2 = Edges.get(b)[1];
                    if ((u1 < u2 && v1 > v2) || (u1 > u2 && v1 < v2)) {
                        crossings++;
                    }
                }
            }
        }

        return crossings;
    }

    // ── Utility ──────────────────────────────────────────────────────

    private Map<String, Set<String>> invertAdjacency(
            List<String> vertices,
            Map<String, Set<String>> adj) {

        Map<String, Set<String>> inv = new HashMap<String, Set<String>>();
        for (String v : vertices) {
            inv.put(v, new HashSet<String>());
        }
        for (String u : vertices) {
            for (String v : adj.getOrDefault(u, Collections.emptySet())) {
                if (inv.containsKey(v)) {
                    inv.get(v).add(u);
                }
            }
        }
        return inv;
    }

    // ── Public getters ───────────────────────────────────────────────

    /**
     * Gets the computed position of a vertex.
     *
     * @param vertex the vertex ID
     * @return [x, y] coordinates, or null if vertex not found
     * @throws IllegalStateException if compute() has not been called
     */
    public double[] getPosition(String vertex) {
        ensureComputed();
        return positions.get(vertex);
    }

    /**
     * Gets all computed positions.
     *
     * @return unmodifiable map of vertex ID to [x, y]
     * @throws IllegalStateException if compute() has not been called
     */
    public Map<String, double[]> getPositions() {
        ensureComputed();
        return Collections.unmodifiableMap(positions);
    }

    /**
     * Gets the layer assignment for each vertex.
     *
     * @return unmodifiable map of vertex ID to layer index (0-based)
     * @throws IllegalStateException if compute() has not been called
     */
    public Map<String, Integer> getLayerAssignment() {
        ensureComputed();
        return Collections.unmodifiableMap(layerAssignment);
    }

    /**
     * Gets vertices grouped by layer.
     *
     * @return list of layers, each containing vertex IDs
     * @throws IllegalStateException if compute() has not been called
     */
    public List<List<String>> getLayers() {
        ensureComputed();
        List<List<String>> result = new ArrayList<List<String>>();
        for (List<String> layer : layers) {
            result.add(Collections.unmodifiableList(layer));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Gets the number of layers.
     *
     * @throws IllegalStateException if compute() has not been called
     */
    public int getLayerCount() {
        ensureComputed();
        return layers.size();
    }

    /**
     * Gets the maximum number of nodes in any single layer (width).
     *
     * @throws IllegalStateException if compute() has not been called
     */
    public int getMaxLayerWidth() {
        ensureComputed();
        int max = 0;
        for (List<String> layer : layers) {
            max = Math.max(max, layer.size());
        }
        return max;
    }

    /**
     * Gets the critical (longest) path through the hierarchy.
     *
     * @return ordered list of vertex IDs from source to sink
     * @throws IllegalStateException if compute() has not been called
     */
    public List<String> getCriticalPath() {
        ensureComputed();
        return Collections.unmodifiableList(criticalPath);
    }

    /**
     * Gets the number of edge crossings in the layout.
     *
     * @throws IllegalStateException if compute() has not been called
     */
    public int getEdgeCrossings() {
        ensureComputed();
        return crossingCount;
    }

    /**
     * Gets edges that were reversed to break cycles.
     *
     * @return set of edge objects that were reversed
     * @throws IllegalStateException if compute() has not been called
     */
    public Set<Edge> getReversedEdges() {
        ensureComputed();
        return Collections.unmodifiableSet(reversedEdges);
    }

    /**
     * Returns the layout orientation.
     */
    public Orientation getOrientation() {
        return orientation;
    }

    // ── Quality report ───────────────────────────────────────────────

    /**
     * Layout quality metrics for hierarchical layout.
     */
    public static class LayoutQuality {
        private final int layerCount;
        private final int maxLayerWidth;
        private final int edgeCrossings;
        private final int reversedEdgeCount;
        private final int criticalPathLength;
        private final double aspectRatio;
        private final String orientation;

        public LayoutQuality(int layerCount, int maxLayerWidth,
                              int edgeCrossings, int reversedEdgeCount,
                              int criticalPathLength, double aspectRatio,
                              String orientation) {
            this.layerCount = layerCount;
            this.maxLayerWidth = maxLayerWidth;
            this.edgeCrossings = edgeCrossings;
            this.reversedEdgeCount = reversedEdgeCount;
            this.criticalPathLength = criticalPathLength;
            this.aspectRatio = aspectRatio;
            this.orientation = orientation;
        }

        public int getLayerCount() { return layerCount; }
        public int getMaxLayerWidth() { return maxLayerWidth; }
        public int getEdgeCrossings() { return edgeCrossings; }
        public int getReversedEdgeCount() { return reversedEdgeCount; }
        public int getCriticalPathLength() { return criticalPathLength; }
        public double getAspectRatio() { return aspectRatio; }
        public String getOrientation() { return orientation; }

        @Override
        public String toString() {
            return String.format(
                    "LayoutQuality{layers=%d, maxWidth=%d, crossings=%d, " +
                    "reversed=%d, criticalPath=%d, aspect=%.2f, orient=%s}",
                    layerCount, maxLayerWidth, edgeCrossings,
                    reversedEdgeCount, criticalPathLength, aspectRatio,
                    orientation);
        }
    }

    /**
     * Gets a quality report for the computed layout.
     *
     * @return layout quality metrics
     * @throws IllegalStateException if compute() has not been called
     */
    public LayoutQuality getQualityReport() {
        ensureComputed();
        double aspect = layers.isEmpty() ? 0 :
                (double) getMaxLayerWidth() / layers.size();
        return new LayoutQuality(
                layers.size(), getMaxLayerWidth(), crossingCount,
                reversedEdges.size(), criticalPath.size(), aspect,
                orientation.name());
    }

    // ── SVG export ───────────────────────────────────────────────────

    /**
     * Renders the layout as an SVG string with directional arrows.
     *
     * @param svgWidth   SVG viewport width
     * @param svgHeight  SVG viewport height
     * @param nodeRadius radius for node circles
     * @return complete SVG document string
     * @throws IllegalStateException if compute() has not been called
     */
    public String toSVG(int svgWidth, int svgHeight, int nodeRadius) {
        ensureComputed();

        // Normalize positions to fit the SVG viewport
        Map<String, double[]> normalized = getNormalizedPositions(
                svgWidth - 2 * nodeRadius, svgHeight - 2 * nodeRadius);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append(String.format(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" " +
                "width=\"%d\" height=\"%d\" " +
                "viewBox=\"0 0 %d %d\">\n",
                svgWidth, svgHeight, svgWidth, svgHeight));

        // Background
        sb.append(String.format(
                "  <rect width=\"%d\" height=\"%d\" fill=\"#fafafa\"/>\n",
                svgWidth, svgHeight));

        // Arrowhead marker
        sb.append("  <defs>\n");
        sb.append("    <marker id=\"arrowhead\" markerWidth=\"10\" " +
                  "markerHeight=\"7\" refX=\"10\" refY=\"3.5\" " +
                  "orient=\"auto\">\n");
        sb.append("      <polygon points=\"0 0, 10 3.5, 0 7\" " +
                  "fill=\"#666\"/>\n");
        sb.append("    </marker>\n");
        sb.append("  </defs>\n");

        // Draw edges
        for (Edge e : graph.getEdges()) {
            String u = e.getVertex1();
            String v = e.getVertex2();
            if (u == null || v == null) continue;
            double[] p1 = normalized.get(u);
            double[] p2 = normalized.get(v);
            if (p1 == null || p2 == null) continue;

            double x1 = p1[0] + nodeRadius;
            double y1 = p1[1] + nodeRadius;
            double x2 = p2[0] + nodeRadius;
            double y2 = p2[1] + nodeRadius;

            // Shorten line to stop at node border
            double dx = x2 - x1;
            double dy = y2 - y1;
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist > 0) {
                x2 -= (dx / dist) * nodeRadius;
                y2 -= (dy / dist) * nodeRadius;
            }

            String color = reversedEdges.contains(e) ? "#e74c3c" : "#666";
            String dashArray = reversedEdges.contains(e)
                    ? " stroke-dasharray=\"5,3\"" : "";

            sb.append(String.format(
                    "  <line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" " +
                    "stroke=\"%s\" stroke-width=\"1.5\"%s " +
                    "marker-end=\"url(#arrowhead)\"/>\n",
                    x1, y1, x2, y2, color, dashArray));
        }

        // Draw nodes
        for (String v : graph.getVertices()) {
            double[] p = normalized.get(v);
            if (p == null) continue;

            double cx = p[0] + nodeRadius;
            double cy = p[1] + nodeRadius;

            // Highlight critical path nodes
            boolean onCritPath = criticalPath.contains(v);
            String fill = onCritPath ? "#3498db" : "#ecf0f1";
            String stroke = onCritPath ? "#2980b9" : "#bdc3c7";

            sb.append(String.format(
                    "  <circle cx=\"%.1f\" cy=\"%.1f\" r=\"%d\" " +
                    "fill=\"%s\" stroke=\"%s\" stroke-width=\"2\"/>\n",
                    cx, cy, nodeRadius, fill, stroke));

            // Label
            String label = v.length() > 8 ? v.substring(0, 8) + ".." : v;
            sb.append(String.format(
                    "  <text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" " +
                    "dominant-baseline=\"central\" font-family=\"sans-serif\" " +
                    "font-size=\"10\" fill=\"#333\">%s</text>\n",
                    cx, cy, escapeXml(label)));
        }

        sb.append("</svg>\n");
        return sb.toString();
    }

    private Map<String, double[]> getNormalizedPositions(
            double vpWidth, double vpHeight) {

        if (positions.isEmpty()) return new HashMap<String, double[]>();

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

        for (double[] p : positions.values()) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }

        double rangeX = maxX - minX;
        double rangeY = maxY - minY;
        if (rangeX < 1e-6) rangeX = 1;
        if (rangeY < 1e-6) rangeY = 1;

        Map<String, double[]> normalized = new HashMap<String, double[]>();
        for (Map.Entry<String, double[]> entry : positions.entrySet()) {
            double nx = ((entry.getValue()[0] - minX) / rangeX) * vpWidth;
            double ny = ((entry.getValue()[1] - minY) / rangeY) * vpHeight;
            normalized.put(entry.getKey(), new double[]{nx, ny});
        }

        return normalized;
    }

    // ── Summary ──────────────────────────────────────────────────────

    /**
     * Generates a human-readable layout summary.
     *
     * @return summary string
     * @throws IllegalStateException if compute() has not been called
     */
    public String getSummary() {
        ensureComputed();
        StringBuilder sb = new StringBuilder();
        sb.append("=== Hierarchical Layout Summary ===\n\n");
        sb.append(String.format("Vertices:        %d\n", graph.getVertexCount()));
        sb.append(String.format("Edges:           %d\n", graph.getEdgeCount()));
        sb.append(String.format("Orientation:     %s\n", orientation));
        sb.append(String.format("Layers:          %d\n", layers.size()));
        sb.append(String.format("Max layer width: %d\n", getMaxLayerWidth()));
        sb.append(String.format("Edge crossings:  %d\n", crossingCount));
        sb.append(String.format("Reversed edges:  %d (cycle breaks)\n",
                reversedEdges.size()));
        sb.append(String.format("Critical path:   %s\n",
                criticalPath.isEmpty() ? "(none)"
                        : String.join(" -> ", criticalPath)));

        sb.append("\nLayer breakdown:\n");
        for (int i = 0; i < layers.size(); i++) {
            sb.append(String.format("  Layer %d: %d nodes %s\n",
                    i, layers.get(i).size(),
                    layers.get(i).size() <= 8
                            ? layers.get(i).toString() : ""));
        }

        return sb.toString();
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private void ensureComputed() {
        if (!computed) {
            throw new IllegalStateException(
                    "Layout not computed. Call compute() first.");
        }
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
