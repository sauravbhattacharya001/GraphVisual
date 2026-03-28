package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Force-Directed Graph Layout — computes aesthetically pleasing 2D positions
 * for graph vertices using the <b>Fruchterman–Reingold</b> algorithm.
 *
 * <h3>Algorithm</h3>
 * <p>Models the graph as a physical system where:</p>
 * <ul>
 *   <li><b>Repulsive forces</b> push all vertex pairs apart (like charged
 *       particles — Coulomb's law).</li>
 *   <li><b>Attractive forces</b> pull connected vertices together (like
 *       springs — Hooke's law).</li>
 *   <li>A <b>cooling schedule</b> gradually reduces the maximum displacement,
 *       allowing the system to settle into a low-energy configuration.</li>
 * </ul>
 *
 * <h3>Features</h3>
 * <ul>
 *   <li>Classic Fruchterman–Reingold with configurable iterations and area</li>
 *   <li>Optional gravity force to prevent disconnected components from
 *       drifting apart</li>
 *   <li>Edge-weight awareness — heavier edges pull more strongly</li>
 *   <li>Layout quality metrics: Edge crossing count, Edge length uniformity,
 *       angular resolution, stress</li>
 *   <li>Bounding box normalization for display in arbitrary viewport sizes</li>
 *   <li>Convergence detection via energy tracking</li>
 *   <li>Deterministic seeding for reproducible layouts</li>
 * </ul>
 *
 * <h3>Complexity</h3>
 * <p>O(iterations × (V² + E)) — the V² term comes from all-pairs repulsion.
 * Suitable for graphs up to ~5000 nodes.</p>
 *
 * @author zalenix
 */
public class ForceDirectedLayout {

    private static final double MIN_DIST = 0.01;
    /** Use Barnes-Hut approximation above this vertex count. */
    private static final int BARNES_HUT_THRESHOLD = 100;
    /** Barnes-Hut opening angle: lower = more accurate, higher = faster. */
    private static final double BH_THETA = 0.8;

    private final Graph<String, Edge> graph;
    private final int maxIterations;
    private final double width;
    private final double height;
    private final double gravity;
    private final boolean useEdgeWeights;
    private final long seed;

    private Map<String, double[]> positions;
    private List<String> vertexList;
    private boolean computed;
    private int iterationsUsed;
    private double finalEnergy;

    /**
     * Creates a ForceDirectedLayout with default settings.
     *
     * @param graph the JUNG graph to lay out
     * @throws IllegalArgumentException if graph is null
     */
    public ForceDirectedLayout(Graph<String, Edge> graph) {
        this(graph, 300, 800, 600, 0.1, true, 42L);
    }

    /**
     * Creates a ForceDirectedLayout with full configuration.
     *
     * @param graph         the JUNG graph to lay out
     * @param maxIterations maximum simulation iterations (typically 100–500)
     * @param width         canvas width for the layout area
     * @param height        canvas height for the layout area
     * @param gravity       gravity constant pulling nodes toward center
     *                      (0.0 = none, 0.1 = gentle, 1.0 = strong)
     * @param useEdgeWeights if true, Edge weights scale attractive forces
     * @param seed          random seed for reproducible initial placement
     * @throws IllegalArgumentException if graph is null or parameters invalid
     */
    public ForceDirectedLayout(Graph<String, Edge> graph, int maxIterations,
                                double width, double height, double gravity,
                                boolean useEdgeWeights, long seed) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        if (maxIterations < 1) {
            throw new IllegalArgumentException(
                    "maxIterations must be >= 1, got: " + maxIterations);
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "width and height must be positive");
        }
        if (gravity < 0) {
            throw new IllegalArgumentException(
                    "gravity must be non-negative, got: " + gravity);
        }
        this.graph = graph;
        this.maxIterations = maxIterations;
        this.width = width;
        this.height = height;
        this.gravity = gravity;
        this.useEdgeWeights = useEdgeWeights;
        this.seed = seed;
        this.positions = new LinkedHashMap<String, double[]>();
        this.computed = false;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Core algorithm
    // ═══════════════════════════════════════════════════════════════

    /**
     * Runs the Fruchterman–Reingold layout algorithm.
     * Idempotent — repeated calls are no-ops.
     *
     * @return this layout for chaining
     */
    public ForceDirectedLayout compute() {
        if (computed) return this;

        Collection<String> vertices = graph.getVertices();
        int n = vertices.size();

        if (n == 0) {
            iterationsUsed = 0;
            finalEnergy = 0;
            computed = true;
            return this;
        }

        vertexList = new ArrayList<String>(vertices);
        Collections.sort(vertexList);

        if (n == 1) {
            positions.put(vertexList.get(0),
                    new double[]{width / 2, height / 2});
            iterationsUsed = 0;
            finalEnergy = 0;
            computed = true;
            return this;
        }

        // Optimal distance between nodes
        double area = width * height;
        double k = Math.sqrt(area / n);

        // Initialize positions randomly within the canvas
        Random rng = new Random(seed);
        double[][] pos = new double[n][2];
        for (int i = 0; i < n; i++) {
            pos[i][0] = width * 0.1 + rng.nextDouble() * width * 0.8;
            pos[i][1] = height * 0.1 + rng.nextDouble() * height * 0.8;
        }

        // Build index map for fast lookup
        Map<String, Integer> indexMap = new HashMap<String, Integer>(n * 2);
        for (int i = 0; i < n; i++) {
            indexMap.put(vertexList.get(i), i);
        }

        // Build Edge list as index pairs with weights
        List<int[]> edgeIndices = new ArrayList<int[]>();
        List<Double> edgeWeights = new ArrayList<Double>();
        for (Edge e : graph.getEdges()) {
            Integer u = indexMap.get(e.getVertex1());
            Integer v = indexMap.get(e.getVertex2());
            if (u != null && v != null && !u.equals(v)) {
                edgeIndices.add(new int[]{u, v});
                edgeWeights.add(useEdgeWeights
                        ? Math.max(e.getWeight(), 0.1) : 1.0);
            }
        }

        // Cooling: initial temperature = 10% of the diagonal
        double t = Math.sqrt(width * width + height * height) * 0.1;
        double coolingFactor = t / (maxIterations + 1);
        double centerX = width / 2.0;
        double centerY = height / 2.0;

        double prevEnergy = Double.MAX_VALUE;
        int stableCount = 0;

        for (int iter = 0; iter < maxIterations; iter++) {
            double[][] disp = new double[n][2];

            // Repulsive forces
            if (n > BARNES_HUT_THRESHOLD) {
                // Barnes-Hut: O(V log V) approximation via quadtree
                QuadTree qt = QuadTree.build(pos, n);
                for (int i = 0; i < n; i++) {
                    qt.applyRepulsion(i, pos[i][0], pos[i][1], k, disp[i], BH_THETA);
                }
            } else {
                // Brute-force: O(V^2) all-pairs (fine for small graphs)
                for (int i = 0; i < n; i++) {
                    for (int j = i + 1; j < n; j++) {
                        double dx = pos[i][0] - pos[j][0];
                        double dy = pos[i][1] - pos[j][1];
                        double dist = Math.sqrt(dx * dx + dy * dy);
                        if (dist < MIN_DIST) dist = MIN_DIST;

                        double force = (k * k) / dist;
                        double fx = (dx / dist) * force;
                        double fy = (dy / dist) * force;

                        disp[i][0] += fx;
                        disp[i][1] += fy;
                        disp[j][0] -= fx;
                        disp[j][1] -= fy;
                    }
                }
            }

            // ── Attractive forces (edges) ──────────────────────────
            for (int e = 0; e < edgeIndices.size(); e++) {
                int u = edgeIndices.get(e)[0];
                int v = edgeIndices.get(e)[1];
                double w = edgeWeights.get(e);

                double dx = pos[u][0] - pos[v][0];
                double dy = pos[u][1] - pos[v][1];
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < MIN_DIST) dist = MIN_DIST;

                // Attractive force: dist² / k, scaled by Edge weight
                double force = (dist * dist) / k * w;
                double fx = (dx / dist) * force;
                double fy = (dy / dist) * force;

                disp[u][0] -= fx;
                disp[u][1] -= fy;
                disp[v][0] += fx;
                disp[v][1] += fy;
            }

            // ── Gravity (pull toward center) ───────────────────────
            if (gravity > 0) {
                for (int i = 0; i < n; i++) {
                    double dx = centerX - pos[i][0];
                    double dy = centerY - pos[i][1];
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist > MIN_DIST) {
                        disp[i][0] += gravity * dx;
                        disp[i][1] += gravity * dy;
                    }
                }
            }

            // ── Apply displacements (limited by temperature) ──────
            double energy = 0;
            for (int i = 0; i < n; i++) {
                double dispLen = Math.sqrt(
                        disp[i][0] * disp[i][0] + disp[i][1] * disp[i][1]);
                if (dispLen > MIN_DIST) {
                    double capped = Math.min(dispLen, t);
                    pos[i][0] += (disp[i][0] / dispLen) * capped;
                    pos[i][1] += (disp[i][1] / dispLen) * capped;
                    energy += capped * capped;
                }

                // Keep within bounds
                pos[i][0] = Math.max(0, Math.min(width, pos[i][0]));
                pos[i][1] = Math.max(0, Math.min(height, pos[i][1]));
            }

            // ── Cool ──────────────────────────────────────────────
            t = Math.max(t - coolingFactor, 0.01);

            // ── Convergence check ─────────────────────────────────
            if (Math.abs(energy - prevEnergy) < 0.001 * n) {
                stableCount++;
                if (stableCount >= 5) {
                    iterationsUsed = iter + 1;
                    finalEnergy = energy;
                    break;
                }
            } else {
                stableCount = 0;
            }
            prevEnergy = energy;
            iterationsUsed = iter + 1;
            finalEnergy = energy;
        }

        // Store final positions
        for (int i = 0; i < n; i++) {
            positions.put(vertexList.get(i),
                    new double[]{pos[i][0], pos[i][1]});
        }

        computed = true;
        return this;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Position queries
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns the computed position of a vertex.
     *
     * @param vertex vertex ID
     * @return double array [x, y], or null if vertex not found
     */
    public double[] getPosition(String vertex) {
        ensureComputed();
        double[] p = positions.get(vertex);
        return p != null ? new double[]{p[0], p[1]} : null;
    }

    /**
     * Returns all computed positions.
     *
     * @return unmodifiable map: vertex ID → [x, y]
     */
    public Map<String, double[]> getPositions() {
        ensureComputed();
        Map<String, double[]> copy = new LinkedHashMap<String, double[]>();
        for (Map.Entry<String, double[]> entry : positions.entrySet()) {
            double[] p = entry.getValue();
            copy.put(entry.getKey(), new double[]{p[0], p[1]});
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Returns positions normalized to a given viewport (0,0)–(vpWidth, vpHeight).
     *
     * @param vpWidth  target viewport width
     * @param vpHeight target viewport height
     * @param padding  margin in viewport units
     * @return map: vertex ID → [x, y] in viewport coordinates
     */
    public Map<String, double[]> getNormalizedPositions(double vpWidth,
                                                        double vpHeight,
                                                        double padding) {
        ensureComputed();
        if (positions.isEmpty()) {
            return Collections.emptyMap();
        }

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (double[] p : positions.values()) {
            if (p[0] < minX) minX = p[0];
            if (p[0] > maxX) maxX = p[0];
            if (p[1] < minY) minY = p[1];
            if (p[1] > maxY) maxY = p[1];
        }

        double rangeX = maxX - minX;
        double rangeY = maxY - minY;
        if (rangeX < MIN_DIST) rangeX = 1;
        if (rangeY < MIN_DIST) rangeY = 1;

        double usableW = vpWidth - 2 * padding;
        double usableH = vpHeight - 2 * padding;
        double scale = Math.min(usableW / rangeX, usableH / rangeY);

        Map<String, double[]> result = new LinkedHashMap<String, double[]>();
        for (Map.Entry<String, double[]> entry : positions.entrySet()) {
            double[] p = entry.getValue();
            result.put(entry.getKey(), new double[]{
                    padding + (p[0] - minX) * scale,
                    padding + (p[1] - minY) * scale
            });
        }
        return Collections.unmodifiableMap(result);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Layout quality metrics
    // ═══════════════════════════════════════════════════════════════

    /**
     * Counts the number of Edge crossings in the layout.
     * Two edges cross if their line segments intersect (excluding shared
     * endpoints).
     *
     * @return number of Edge crossings
     */
    public int countEdgeCrossings() {
        ensureComputed();
        List<Edge> edges = new ArrayList<Edge>(graph.getEdges());
        int m = edges.size();

        // Pre-compute endpoint positions and vertex names into arrays
        // to avoid repeated HashMap lookups and method calls inside
        // the O(E²) nested loop.
        double[][] ep1 = new double[m][];
        double[][] ep2 = new double[m][];
        String[] v1 = new String[m];
        String[] v2 = new String[m];
        boolean[] valid = new boolean[m];

        for (int i = 0; i < m; i++) {
            Edge e = edges.get(i);
            v1[i] = e.getVertex1();
            v2[i] = e.getVertex2();
            ep1[i] = positions.get(v1[i]);
            ep2[i] = positions.get(v2[i]);
            valid[i] = ep1[i] != null && ep2[i] != null;
        }

        int crossings = 0;
        for (int i = 0; i < m; i++) {
            if (!valid[i]) continue;

            for (int j = i + 1; j < m; j++) {
                if (!valid[j]) continue;

                // Skip if edges share an endpoint
                if (v1[i].equals(v1[j]) || v1[i].equals(v2[j]) ||
                    v2[i].equals(v1[j]) || v2[i].equals(v2[j])) {
                    continue;
                }

                if (segmentsIntersect(ep1[i], ep2[i], ep1[j], ep2[j])) {
                    crossings++;
                }
            }
        }
        return crossings;
    }

    /**
     * Computes the coefficient of variation of Edge lengths.
     * Lower values indicate more uniform Edge lengths (desirable).
     * Returns 0 for graphs with 0 or 1 edges.
     *
     * @return CV of Edge lengths (0 = perfectly uniform)
     */
    public double edgeLengthUniformity() {
        ensureComputed();
        List<Double> lengths = new ArrayList<Double>();
        for (Edge e : graph.getEdges()) {
            double[] p1 = positions.get(e.getVertex1());
            double[] p2 = positions.get(e.getVertex2());
            if (p1 == null || p2 == null) continue;
            double dx = p1[0] - p2[0];
            double dy = p1[1] - p2[1];
            lengths.add(Math.sqrt(dx * dx + dy * dy));
        }
        if (lengths.size() < 2) return 0;

        double sum = 0;
        for (double l : lengths) sum += l;
        double mean = sum / lengths.size();

        double variance = 0;
        for (double l : lengths) variance += (l - mean) * (l - mean);
        variance /= lengths.size();

        return mean > 0 ? Math.sqrt(variance) / mean : 0;
    }

    /**
     * Computes the minimum angular resolution across all vertices.
     * The angular resolution is the smallest angle formed between
     * adjacent edges at any vertex. Higher is better (ideally 360°/degree).
     *
     * @return minimum angle in degrees (0 for graphs without multi-Edge vertices)
     */
    public double minAngularResolution() {
        ensureComputed();
        double minAngle = 360;

        for (String v : positions.keySet()) {
            Collection<String> neighbors = graph.getNeighbors(v);
            if (neighbors == null || neighbors.size() < 2) continue;

            double[] center = positions.get(v);
            List<Double> angles = new ArrayList<Double>();
            for (String n : neighbors) {
                double[] np = positions.get(n);
                if (np == null) continue;
                angles.add(Math.atan2(np[1] - center[1], np[0] - center[0]));
            }
            if (angles.size() < 2) continue;

            Collections.sort(angles);
            for (int i = 1; i < angles.size(); i++) {
                double diff = Math.toDegrees(angles.get(i) - angles.get(i - 1));
                if (diff < minAngle) minAngle = diff;
            }
            // Wrap-around angle
            double wrap = Math.toDegrees(
                    (2 * Math.PI + angles.get(0) - angles.get(angles.size() - 1)));
            if (wrap < minAngle) minAngle = wrap;
        }

        return minAngle >= 360 ? 0 : minAngle;
    }

    /**
     * Computes the stress of the layout.
     * Stress measures how well graph-theoretic distances (shortest paths)
     * are preserved in the 2D embedding. Lower is better.
     *
     * <p>Uses Kruskal's stress formula:
     * stress = Σ_{i<j} [(d_ij - δ_ij)² / δ_ij²]
     * where d_ij is the Euclidean distance and δ_ij is the graph distance.</p>
     *
     * <p><b>Performance:</b> Uses array-based inline BFS instead of building
     * a full V×V HashMap of distances. Each source BFS uses a reusable
     * {@code int[]} distance array (reset per source) and an {@code int[]}
     * queue, eliminating Integer/String boxing and HashMap overhead.
     * Stress contributions are accumulated on-the-fly during each BFS,
     * reducing peak memory from O(V²) to O(V + E).</p>
     *
     * @return normalized stress value (0 = perfect preservation)
     */
    public double computeStress() {
        ensureComputed();
        int n = vertexList != null ? vertexList.size() : 0;
        if (n < 2) return 0;

        // Build integer-indexed adjacency for cache-friendly BFS
        Map<String, Integer> idxMap = new HashMap<String, Integer>(n * 2);
        for (int i = 0; i < n; i++) {
            idxMap.put(vertexList.get(i), i);
        }

        int[][] adj = new int[n][];
        {
            @SuppressWarnings("unchecked")
            List<Integer>[] tmp = new List[n];
            for (int i = 0; i < n; i++) tmp[i] = new ArrayList<Integer>();
            for (Edge e : graph.getEdges()) {
                Integer u = idxMap.get(e.getVertex1());
                Integer v = idxMap.get(e.getVertex2());
                if (u != null && v != null && !u.equals(v)) {
                    tmp[u].add(v);
                    tmp[v].add(u);
                }
            }
            for (int i = 0; i < n; i++) {
                List<Integer> nb = tmp[i];
                adj[i] = new int[nb.size()];
                for (int j = 0; j < nb.size(); j++) adj[i][j] = nb.get(j);
            }
        }

        // Cache positions in parallel arrays for indexed access
        double[] px = new double[n];
        double[] py = new double[n];
        for (int i = 0; i < n; i++) {
            double[] p = positions.get(vertexList.get(i));
            px[i] = p[0];
            py[i] = p[1];
        }

        double k = Math.sqrt(width * height / n);
        double stress = 0;
        double normalizer = 0;

        // Reusable BFS arrays — allocated once, reset per source
        int[] dist = new int[n];
        int[] queue = new int[n];

        for (int s = 0; s < n; s++) {
            // Reset distances (-1 = unvisited)
            Arrays.fill(dist, -1);
            dist[s] = 0;
            int qStart = 0, qEnd = 0;
            queue[qEnd++] = s;

            while (qStart < qEnd) {
                int cur = queue[qStart++];
                int curDist = dist[cur];
                for (int nb : adj[cur]) {
                    if (dist[nb] < 0) {
                        dist[nb] = curDist + 1;
                        queue[qEnd++] = nb;
                    }
                }
            }

            // Accumulate stress for pairs (s, j) where j > s
            for (int j = s + 1; j < n; j++) {
                int graphDist = dist[j];
                if (graphDist <= 0) continue;

                double dx = px[s] - px[j];
                double dy = py[s] - py[j];
                double eucDist = Math.sqrt(dx * dx + dy * dy);
                double expected = graphDist * k;

                double diff = eucDist - expected;
                stress += (diff * diff) / (expected * expected);
                normalizer++;
            }
        }

        return normalizer > 0 ? stress / normalizer : 0;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Metadata
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns the number of iterations the algorithm ran.
     */
    public int getIterationsUsed() {
        ensureComputed();
        return iterationsUsed;
    }

    /**
     * Returns the final energy of the system.
     * Lower energy indicates better convergence.
     */
    public double getFinalEnergy() {
        ensureComputed();
        return finalEnergy;
    }

    /**
     * Returns true if the algorithm converged before reaching maxIterations.
     */
    public boolean converged() {
        ensureComputed();
        return iterationsUsed < maxIterations;
    }

    /**
     * Returns a comprehensive quality report for the layout.
     */
    public LayoutQuality getQualityReport() {
        ensureComputed();
        return new LayoutQuality(
                countEdgeCrossings(),
                edgeLengthUniformity(),
                minAngularResolution(),
                computeStress(),
                iterationsUsed,
                finalEnergy,
                converged()
        );
    }

    /**
     * Generates an SVG representation of the laid-out graph.
     *
     * @param svgWidth  SVG canvas width
     * @param svgHeight SVG canvas height
     * @param nodeRadius radius for node circles
     * @return SVG string
     */
    public String toSVG(int svgWidth, int svgHeight, int nodeRadius) {
        ensureComputed();
        Map<String, double[]> norm = getNormalizedPositions(
                svgWidth, svgHeight, nodeRadius * 3);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append(String.format(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\">\n",
                svgWidth, svgHeight));
        sb.append("  <style>\n");
        sb.append("    .Edge { stroke: #999; stroke-width: 1; stroke-opacity: 0.6; }\n");
        sb.append("    .node { fill: #4285f4; stroke: #fff; stroke-width: 1.5; }\n");
        sb.append("    .label { font-family: sans-serif; font-size: 10px; ");
        sb.append("text-anchor: middle; fill: #333; }\n");
        sb.append("  </style>\n");

        // Draw edges
        for (Edge e : graph.getEdges()) {
            double[] p1 = norm.get(e.getVertex1());
            double[] p2 = norm.get(e.getVertex2());
            if (p1 == null || p2 == null) continue;
            sb.append(String.format(
                    "  <line class=\"Edge\" x1=\"%.1f\" y1=\"%.1f\" " +
                    "x2=\"%.1f\" y2=\"%.1f\"/>\n",
                    p1[0], p1[1], p2[0], p2[1]));
        }

        // Draw nodes
        for (Map.Entry<String, double[]> entry : norm.entrySet()) {
            double[] p = entry.getValue();
            sb.append(String.format(
                    "  <circle class=\"node\" cx=\"%.1f\" cy=\"%.1f\" r=\"%d\"/>\n",
                    p[0], p[1], nodeRadius));
            sb.append(String.format(
                    "  <text class=\"label\" x=\"%.1f\" y=\"%.1f\">%s</text>\n",
                    p[0], p[1] - nodeRadius - 3, escapeXml(entry.getKey())));
        }

        sb.append("</svg>\n");
        return sb.toString();
    }

    /**
     * Generates a formatted text summary of the layout.
     */
    public String getSummary() {
        ensureComputed();
        LayoutQuality q = getQualityReport();
        StringBuilder sb = new StringBuilder();
        sb.append("═══ Force-Directed Layout ═══\n");
        sb.append(String.format("Vertices: %d | Edges: %d\n",
                graph.getVertexCount(), graph.getEdgeCount()));
        sb.append(String.format("Canvas: %.0f × %.0f\n", width, height));
        sb.append(String.format("Iterations: %d/%d %s\n",
                iterationsUsed, maxIterations,
                converged() ? "(converged)" : "(max reached)"));
        sb.append(String.format("Final energy: %.4f\n", finalEnergy));
        sb.append("\n── Quality Metrics ──\n");
        sb.append(String.format("Edge crossings: %d\n", q.getEdgeCrossings()));
        sb.append(String.format("Edge length CV: %.4f %s\n",
                q.getEdgeLengthCV(),
                q.getEdgeLengthCV() < 0.3 ? "(uniform)" : "(varied)"));
        sb.append(String.format("Min angular res: %.1f°\n",
                q.getMinAngularResolution()));
        sb.append(String.format("Stress: %.4f\n", q.getStress()));
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Quality result
    // ═══════════════════════════════════════════════════════════════

    /**
     * Holds layout quality metrics.
     */
    public static class LayoutQuality {
        private final int edgeCrossings;
        private final double edgeLengthCV;
        private final double minAngularResolution;
        private final double stress;
        private final int iterations;
        private final double energy;
        private final boolean converged;

        public LayoutQuality(int edgeCrossings, double edgeLengthCV,
                             double minAngularResolution, double stress,
                             int iterations, double energy, boolean converged) {
            this.edgeCrossings = edgeCrossings;
            this.edgeLengthCV = edgeLengthCV;
            this.minAngularResolution = minAngularResolution;
            this.stress = stress;
            this.iterations = iterations;
            this.energy = energy;
            this.converged = converged;
        }

        /** Number of Edge crossings in the layout. */
        public int getEdgeCrossings() { return edgeCrossings; }

        /** Coefficient of variation of Edge lengths (0 = perfectly uniform). */
        public double getEdgeLengthCV() { return edgeLengthCV; }

        /** Minimum angle between adjacent edges at any vertex (degrees). */
        public double getMinAngularResolution() { return minAngularResolution; }

        /** Normalized stress (how well graph distances are preserved). */
        public double getStress() { return stress; }

        /** Number of iterations the algorithm ran. */
        public int getIterations() { return iterations; }

        /** Final system energy. */
        public double getEnergy() { return energy; }

        /** Whether the algorithm converged before max iterations. */
        public boolean isConverged() { return converged; }

        @Override
        public String toString() {
            return String.format(
                    "LayoutQuality{crossings=%d, lengthCV=%.4f, " +
                    "angularRes=%.1f°, stress=%.4f, iter=%d, converged=%s}",
                    edgeCrossings, edgeLengthCV, minAngularResolution,
                    stress, iterations, converged);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Private helpers
    // ═══════════════════════════════════════════════════════════════

    private void ensureComputed() {
        if (!computed) compute();
    }

    /**
     * Tests if two line segments (a1–a2) and (b1–b2) intersect.
     * Uses the cross-product orientation test.
     */
    private boolean segmentsIntersect(double[] a1, double[] a2,
                                       double[] b1, double[] b2) {
        double d1 = cross(b1, b2, a1);
        double d2 = cross(b1, b2, a2);
        double d3 = cross(a1, a2, b1);
        double d4 = cross(a1, a2, b2);

        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
            ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
            return true;
        }

        // Collinear cases
        if (Math.abs(d1) < 1e-10 && onSegment(b1, b2, a1)) return true;
        if (Math.abs(d2) < 1e-10 && onSegment(b1, b2, a2)) return true;
        if (Math.abs(d3) < 1e-10 && onSegment(a1, a2, b1)) return true;
        if (Math.abs(d4) < 1e-10 && onSegment(a1, a2, b2)) return true;

        return false;
    }

    private double cross(double[] o, double[] a, double[] b) {
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
    }

    private boolean onSegment(double[] p, double[] q, double[] r) {
        return r[0] <= Math.max(p[0], q[0]) && r[0] >= Math.min(p[0], q[0]) &&
               r[1] <= Math.max(p[1], q[1]) && r[1] >= Math.min(p[1], q[1]);
    }


    /**
     * Escapes special XML characters.
     */
    private static String escapeXml(String s) {
        return ExportUtils.escapeXml(s);
    }
    }

}
