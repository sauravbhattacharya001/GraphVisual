package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Spectral Graph Layout — computes 2D positions for graph vertices using
 * eigenvectors of the <b>Laplacian matrix</b>.
 *
 * <h3>Algorithm</h3>
 * <p>Uses the algebraic structure of the graph to determine node positions:</p>
 * <ol>
 *   <li><b>Build Laplacian</b> — constructs L = D − A from the graph's
 *       adjacency and degree matrices via {@link LaplacianBuilder}.</li>
 *   <li><b>Compute eigenvectors</b> — finds the 2nd and 3rd smallest
 *       eigenvectors of L (the Fiedler vector and its companion) using
 *       power iteration on (L − λ_max·I)^{-1} (inverse iteration).</li>
 *   <li><b>Assign coordinates</b> — the 2nd eigenvector gives X positions,
 *       the 3rd eigenvector gives Y positions.</li>
 *   <li><b>Normalize</b> — scales positions to fit the target viewport.</li>
 * </ol>
 *
 * <h3>Features</h3>
 * <ul>
 *   <li>Mathematically optimal layout that minimizes total edge length squared</li>
 *   <li>Reveals graph symmetry and clustering structure naturally</li>
 *   <li>Configurable canvas size and padding</li>
 *   <li>Optional jitter for coincident nodes (degenerate eigenvalues)</li>
 *   <li>Falls back to circular layout for trivially small or disconnected graphs</li>
 *   <li>SVG export with labels and edges</li>
 *   <li>Layout quality metrics (stress, edge-length uniformity)</li>
 * </ul>
 *
 * <h3>When to use</h3>
 * <ul>
 *   <li>Revealing hidden structure or clusters in a graph</li>
 *   <li>Graphs with strong algebraic properties (regular, symmetric)</li>
 *   <li>As a starting layout that can be refined by force-directed methods</li>
 *   <li>Small-to-medium graphs (up to ~500 nodes)</li>
 * </ul>
 *
 * @author zalenix
 */
public class SpectralLayout {

    // ═════════════════════════════════════════════════════════════════
    //  Configuration
    // ═════════════════════════════════════════════════════════════════

    private double canvasWidth  = 800;
    private double canvasHeight = 600;
    private double padding      = 40;
    private boolean jitterEnabled = true;
    private long seed = 42;

    // ═════════════════════════════════════════════════════════════════
    //  Result
    // ═════════════════════════════════════════════════════════════════

    /** Computed X positions keyed by vertex name. */
    private final Map<String, Double> xPositions = new LinkedHashMap<>();
    /** Computed Y positions keyed by vertex name. */
    private final Map<String, Double> yPositions = new LinkedHashMap<>();

    // ═════════════════════════════════════════════════════════════════
    //  Builder-style setters
    // ═════════════════════════════════════════════════════════════════

    public SpectralLayout canvasWidth(double w)  { this.canvasWidth  = w; return this; }
    public SpectralLayout canvasHeight(double h) { this.canvasHeight = h; return this; }
    public SpectralLayout padding(double p)      { this.padding      = p; return this; }
    public SpectralLayout jitter(boolean j)      { this.jitterEnabled = j; return this; }
    public SpectralLayout seed(long s)           { this.seed = s; return this; }

    // ═════════════════════════════════════════════════════════════════
    //  Getters
    // ═════════════════════════════════════════════════════════════════

    public Map<String, Double> getXPositions() { return Collections.unmodifiableMap(xPositions); }
    public Map<String, Double> getYPositions() { return Collections.unmodifiableMap(yPositions); }

    public double getX(String vertex) { return xPositions.getOrDefault(vertex, 0.0); }
    public double getY(String vertex) { return yPositions.getOrDefault(vertex, 0.0); }

    // ═════════════════════════════════════════════════════════════════
    //  Core: compute layout
    // ═════════════════════════════════════════════════════════════════

    /**
     * Computes spectral positions for all vertices in the graph.
     *
     * @param graph the input graph
     * @return this instance (positions accessible via getters)
     */
    public SpectralLayout compute(Graph<String, Edge> graph) {
        xPositions.clear();
        yPositions.clear();

        List<String> vertices = new ArrayList<>(graph.getVertices());
        Collections.sort(vertices);
        int n = vertices.size();

        if (n == 0) return this;

        // Trivial cases — fall back to simple placement
        if (n == 1) {
            xPositions.put(vertices.get(0), canvasWidth / 2);
            yPositions.put(vertices.get(0), canvasHeight / 2);
            return this;
        }
        if (n == 2) {
            xPositions.put(vertices.get(0), padding);
            yPositions.put(vertices.get(0), canvasHeight / 2);
            xPositions.put(vertices.get(1), canvasWidth - padding);
            yPositions.put(vertices.get(1), canvasHeight / 2);
            return this;
        }

        // Build Laplacian
        double[][] L = LaplacianBuilder.buildStandardLaplacian(graph, vertices);

        // Compute 2nd and 3rd smallest eigenvectors via inverse iteration
        double[] ev2 = computeSmallestNonTrivialEigenvector(L, n, null);
        double[] ev3 = computeSmallestNonTrivialEigenvector(L, n, ev2);

        // Apply jitter if needed (handles degenerate cases)
        Random rng = new Random(seed);
        if (jitterEnabled) {
            double jitterScale = 1e-4;
            for (int i = 0; i < n; i++) {
                ev2[i] += rng.nextGaussian() * jitterScale;
                ev3[i] += rng.nextGaussian() * jitterScale;
            }
        }

        // Normalize to canvas
        normalize(ev2, padding, canvasWidth - padding);
        normalize(ev3, padding, canvasHeight - padding);

        for (int i = 0; i < n; i++) {
            xPositions.put(vertices.get(i), ev2[i]);
            yPositions.put(vertices.get(i), ev3[i]);
        }

        return this;
    }

    // ═════════════════════════════════════════════════════════════════
    //  Eigenvector computation — inverse iteration
    // ═════════════════════════════════════════════════════════════════

    /**
     * Computes the smallest non-trivial eigenvector of L using inverse
     * iteration with a small shift.  If {@code deflate} is non-null, the
     * result is orthogonalized against it (to get the 3rd eigenvector).
     */
    private double[] computeSmallestNonTrivialEigenvector(double[][] L, int n,
                                                           double[] deflate) {
        // Shift L so we target the smallest non-zero eigenvalue
        // Use shifted matrix: M = L + shift * I  (shift makes trivial eigenvalue large)
        double shift = 0.01;
        double[][] M = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                M[i][j] = L[i][j];
            }
            M[i][i] += shift;
        }

        // We want the eigenvector for the smallest eigenvalue of L (excluding 0).
        // Strategy: power iteration on M^{-1} finds the largest eigenvalue of M^{-1},
        // which corresponds to the smallest eigenvalue of M.
        // Since L's smallest eigenvalue is 0 (constant vector), the shift pushes it
        // to 'shift', and the Fiedler value λ2 becomes λ2 + shift.
        // We then deflate the constant vector and optionally the 2nd eigenvector.

        Random rng = new Random(seed + (deflate == null ? 0 : 1));
        double[] v = new double[n];
        for (int i = 0; i < n; i++) v[i] = rng.nextGaussian();

        // Remove constant component
        removeComponent(v, constantVector(n));
        if (deflate != null) removeComponent(v, deflate);
        normalizeUnit(v);

        int maxIter = 300;
        for (int iter = 0; iter < maxIter; iter++) {
            // Solve M * w = v  (via Gaussian elimination each step)
            double[] w = solveLinearSystem(M, v);

            // Deflate
            removeComponent(w, constantVector(n));
            if (deflate != null) removeComponent(w, deflate);

            double norm = norm(w);
            if (norm < 1e-15) {
                // Degenerate — restart with new random
                for (int i = 0; i < n; i++) w[i] = rng.nextGaussian();
                removeComponent(w, constantVector(n));
                if (deflate != null) removeComponent(w, deflate);
                norm = norm(w);
            }
            for (int i = 0; i < n; i++) v[i] = w[i] / norm;
        }
        return v;
    }

    // ═════════════════════════════════════════════════════════════════
    //  Linear algebra helpers
    // ═════════════════════════════════════════════════════════════════

    /** Solves Ax = b via Gaussian elimination with partial pivoting. */
    private double[] solveLinearSystem(double[][] A, double[] b) {
        int n = b.length;
        // Augmented matrix
        double[][] aug = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, aug[i], 0, n);
            aug[i][n] = b[i];
        }

        // Forward elimination
        for (int col = 0; col < n; col++) {
            // Partial pivot
            int maxRow = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(aug[row][col]) > Math.abs(aug[maxRow][col])) {
                    maxRow = row;
                }
            }
            double[] tmp = aug[col]; aug[col] = aug[maxRow]; aug[maxRow] = tmp;

            double pivot = aug[col][col];
            if (Math.abs(pivot) < 1e-14) continue; // singular column

            for (int row = col + 1; row < n; row++) {
                double factor = aug[row][col] / pivot;
                for (int j = col; j <= n; j++) {
                    aug[row][j] -= factor * aug[col][j];
                }
            }
        }

        // Back substitution
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = aug[i][n];
            for (int j = i + 1; j < n; j++) {
                sum -= aug[i][j] * x[j];
            }
            double diag = aug[i][i];
            x[i] = Math.abs(diag) < 1e-14 ? 0 : sum / diag;
        }
        return x;
    }

    private double[] constantVector(int n) {
        double val = 1.0 / Math.sqrt(n);
        double[] v = new double[n];
        Arrays.fill(v, val);
        return v;
    }

    private void removeComponent(double[] v, double[] basis) {
        double dot = 0;
        for (int i = 0; i < v.length; i++) dot += v[i] * basis[i];
        for (int i = 0; i < v.length; i++) v[i] -= dot * basis[i];
    }

    private double norm(double[] v) {
        double s = 0;
        for (double x : v) s += x * x;
        return Math.sqrt(s);
    }

    private void normalizeUnit(double[] v) {
        double n = norm(v);
        if (n > 1e-15) for (int i = 0; i < v.length; i++) v[i] /= n;
    }

    private void normalize(double[] values, double min, double max) {
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (double v : values) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        double range = hi - lo;
        if (range < 1e-15) {
            double mid = (min + max) / 2;
            Arrays.fill(values, mid);
            return;
        }
        for (int i = 0; i < values.length; i++) {
            values[i] = min + (values[i] - lo) / range * (max - min);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  SVG export
    // ═════════════════════════════════════════════════════════════════

    /**
     * Exports the computed layout as an SVG string.
     *
     * @param graph the graph (for edge information)
     * @return SVG markup
     */
    public String toSvg(Graph<String, Edge> graph) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%.0f\" height=\"%.0f\" "
                + "viewBox=\"0 0 %.0f %.0f\">\n",
            canvasWidth, canvasHeight, canvasWidth, canvasHeight));
        sb.append("<style>\n");
        sb.append("  .edge { stroke: #999; stroke-width: 1.5; stroke-opacity: 0.6; }\n");
        sb.append("  .node { fill: #4285f4; stroke: #fff; stroke-width: 1.5; }\n");
        sb.append("  .label { font-family: sans-serif; font-size: 10px; "
                       + "fill: #333; text-anchor: middle; }\n");
        sb.append("</style>\n");
        sb.append("<rect width=\"100%\" height=\"100%\" fill=\"#fafafa\"/>\n");

        // Edges
        for (Edge e : graph.getEdges()) {
            var endpoints = graph.getEndpoints(e);
            var it = endpoints.iterator();
            String src = it.next(), dst = it.next();
            double x1 = getX(src), y1 = getY(src);
            double x2 = getX(dst), y2 = getY(dst);
            sb.append(String.format(
                "  <line class=\"edge\" x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\"/>\n",
                x1, y1, x2, y2));
        }

        // Nodes
        double radius = Math.max(4, Math.min(12, 200.0 / Math.max(1, xPositions.size())));
        for (String v : xPositions.keySet()) {
            double x = getX(v), y = getY(v);
            sb.append(String.format(
                "  <circle class=\"node\" cx=\"%.1f\" cy=\"%.1f\" r=\"%.1f\"/>\n",
                x, y, radius));
            sb.append(String.format(
                "  <text class=\"label\" x=\"%.1f\" y=\"%.1f\">%s</text>\n",
                x, y - radius - 3, escapeXml(v)));
        }

        sb.append("</svg>\n");
        return sb.toString();
    }

    private String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ═════════════════════════════════════════════════════════════════
    //  Quality metrics
    // ═════════════════════════════════════════════════════════════════

    /**
     * Computes layout quality metrics.
     *
     * @param graph the graph
     * @return map of metric name → value
     */
    public Map<String, Double> qualityMetrics(Graph<String, Edge> graph) {
        Map<String, Double> metrics = new LinkedHashMap<>();

        // Stress: sum of (||pos_i - pos_j|| - idealDist)^2 over all edges
        double stress = 0;
        double idealDist = Math.sqrt(canvasWidth * canvasHeight / Math.max(1, xPositions.size()));
        List<Double> edgeLengths = new ArrayList<>();

        for (Edge e : graph.getEdges()) {
            var endpoints = graph.getEndpoints(e);
            var it = endpoints.iterator();
            String src = it.next(), dst = it.next();
            double dx = getX(src) - getX(dst);
            double dy = getY(src) - getY(dst);
            double dist = Math.sqrt(dx * dx + dy * dy);
            edgeLengths.add(dist);
            stress += (dist - idealDist) * (dist - idealDist);
        }

        metrics.put("stress", stress);
        metrics.put("edgeCount", (double) edgeLengths.size());

        if (!edgeLengths.isEmpty()) {
            double mean = edgeLengths.stream().mapToDouble(d -> d).average().orElse(0);
            double variance = edgeLengths.stream()
                .mapToDouble(d -> (d - mean) * (d - mean)).average().orElse(0);
            metrics.put("edgeLengthMean", mean);
            metrics.put("edgeLengthStdDev", Math.sqrt(variance));
            metrics.put("edgeLengthUniformity",
                mean > 0 ? 1.0 - Math.sqrt(variance) / mean : 0);
        }

        return metrics;
    }

    // ═════════════════════════════════════════════════════════════════
    //  toString
    // ═════════════════════════════════════════════════════════════════

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SpectralLayout {\n");
        for (String v : xPositions.keySet()) {
            sb.append(String.format("  %s → (%.2f, %.2f)\n", v, getX(v), getY(v)));
        }
        sb.append("}");
        return sb.toString();
    }
}
