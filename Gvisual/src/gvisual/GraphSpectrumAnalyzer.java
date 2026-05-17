package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.linalg.EigenvalueDecomposition;

/**
 * Computes the eigenvalue spectrum of the adjacency and Laplacian matrices
 * of a graph, providing key spectral graph theory metrics.
 *
 * <p>Computed metrics:</p>
 * <ul>
 *   <li><b>Adjacency spectrum</b> — eigenvalues of the adjacency matrix,
 *       sorted in descending order. The largest eigenvalue (spectral radius)
 *       bounds chromatic number and provides expansion properties.</li>
 *   <li><b>Laplacian spectrum</b> — eigenvalues of L = D − A. The second
 *       smallest eigenvalue (algebraic connectivity / Fiedler value) measures
 *       how well-connected the graph is.</li>
 *   <li><b>Spectral radius</b> — largest adjacency eigenvalue, related to
 *       maximum degree, walk counts, and graph energy.</li>
 *   <li><b>Algebraic connectivity</b> — second smallest Laplacian eigenvalue
 *       (Fiedler value). Zero iff the graph is disconnected.</li>
 *   <li><b>Graph energy</b> — sum of absolute adjacency eigenvalues,
 *       a measure from mathematical chemistry.</li>
 *   <li><b>Spectral gap</b> — difference between the two largest adjacency
 *       eigenvalues. Larger gaps indicate better expansion.</li>
 *   <li><b>Number of connected components</b> — count of zero Laplacian
 *       eigenvalues (multiplicity of 0).</li>
 *   <li><b>Spectral radius ratio</b> — spectral radius divided by sqrt of
 *       max degree, indicating deviation from regularity.</li>
 * </ul>
 *
 * <p>Uses QR algorithm for eigenvalue computation (no external dependencies).
 * Suitable for graphs up to a few hundred vertices.</p>
 *
 * @author sauravbhattacharya001
 */
public class GraphSpectrumAnalyzer {

    private static final double EPSILON = 1e-10;

    private final Graph<String, Edge> graph;
    private double[] adjacencyEigenvalues;
    private double[] laplacianEigenvalues;
    private double spectralRadius;
    private double algebraicConnectivity;
    private double graphEnergy;
    private double spectralGap;
    private int componentCount;
    private double spectralRadiusRatio;
    private boolean computed;

    /**
     * Creates a spectrum analyzer for the given graph.
     *
     * @param graph the JUNG graph to analyze
     * @throws IllegalArgumentException if graph is null
     */
    public GraphSpectrumAnalyzer(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.computed = false;
    }

    /**
     * Runs the eigenvalue computation. Must be called before querying results.
     */
    public void compute() {
        int n = graph.getVertexCount();
        if (n == 0) {
            adjacencyEigenvalues = new double[0];
            laplacianEigenvalues = new double[0];
            spectralRadius = 0;
            algebraicConnectivity = 0;
            graphEnergy = 0;
            spectralGap = 0;
            componentCount = 0;
            spectralRadiusRatio = 0;
            computed = true;
            return;
        }

        // Build vertex index
        List<String> vertices = new ArrayList<String>(graph.getVertices());
        Collections.sort(vertices);
        Map<String, Integer> idxMap = new HashMap<String, Integer>(n * 2);
        for (int i = 0; i < n; i++) idxMap.put(vertices.get(i), i);

        // Build adjacency matrix
        double[][] A = new double[n][n];
        int maxDeg = 0;
        for (Edge e : graph.getEdges()) {
            Integer ui = idxMap.get(e.getVertex1());
            Integer vi = idxMap.get(e.getVertex2());
            if (ui != null && vi != null && !ui.equals(vi)) {
                A[ui][vi] = 1.0;
                A[vi][ui] = 1.0;
            }
        }

        // Build Laplacian matrix L = D - A
        double[][] L = new double[n][n];
        for (int i = 0; i < n; i++) {
            int deg = 0;
            for (int j = 0; j < n; j++) {
                if (A[i][j] != 0) deg++;
                L[i][j] = -A[i][j];
            }
            L[i][i] = deg;
            if (deg > maxDeg) maxDeg = deg;
        }

        // Compute eigenvalues
        adjacencyEigenvalues = computeEigenvalues(A);
        laplacianEigenvalues = computeEigenvalues(L);

        // Sort adjacency descending
        Arrays.sort(adjacencyEigenvalues);
        reverseArray(adjacencyEigenvalues);

        // Sort Laplacian ascending
        Arrays.sort(laplacianEigenvalues);

        // Derived metrics
        spectralRadius = adjacencyEigenvalues.length > 0 ? adjacencyEigenvalues[0] : 0;
        spectralGap = adjacencyEigenvalues.length > 1
                ? adjacencyEigenvalues[0] - adjacencyEigenvalues[1] : 0;

        graphEnergy = 0;
        for (double ev : adjacencyEigenvalues) graphEnergy += Math.abs(ev);

        // Algebraic connectivity = second smallest Laplacian eigenvalue
        algebraicConnectivity = laplacianEigenvalues.length > 1
                ? laplacianEigenvalues[1] : 0;

        // Count connected components (number of ~zero Laplacian eigenvalues)
        componentCount = 0;
        for (double ev : laplacianEigenvalues) {
            if (Math.abs(ev) < 1e-6) componentCount++;
        }

        spectralRadiusRatio = maxDeg > 0
                ? spectralRadius / Math.sqrt(maxDeg) : 0;

        computed = true;
    }

    /** Returns adjacency eigenvalues sorted descending. */
    public double[] getAdjacencyEigenvalues() {
        ensureComputed();
        return Arrays.copyOf(adjacencyEigenvalues, adjacencyEigenvalues.length);
    }

    /** Returns Laplacian eigenvalues sorted ascending. */
    public double[] getLaplacianEigenvalues() {
        ensureComputed();
        return Arrays.copyOf(laplacianEigenvalues, laplacianEigenvalues.length);
    }

    /** Returns the spectral radius (largest adjacency eigenvalue). */
    public double getSpectralRadius() {
        ensureComputed();
        return spectralRadius;
    }

    /** Returns the algebraic connectivity (Fiedler value). */
    public double getAlgebraicConnectivity() {
        ensureComputed();
        return algebraicConnectivity;
    }

    /** Returns the graph energy (sum of |adjacency eigenvalues|). */
    public double getGraphEnergy() {
        ensureComputed();
        return graphEnergy;
    }

    /** Returns the spectral gap (λ₁ − λ₂). */
    public double getSpectralGap() {
        ensureComputed();
        return spectralGap;
    }

    /** Returns the number of connected components. */
    public int getComponentCount() {
        ensureComputed();
        return componentCount;
    }

    /** Returns the spectral radius ratio (ρ / √Δ). */
    public double getSpectralRadiusRatio() {
        ensureComputed();
        return spectralRadiusRatio;
    }

    /**
     * Returns a human-readable summary of spectral properties.
     */
    public String getSummary() {
        ensureComputed();
        StringBuilder sb = new StringBuilder();
        sb.append("=== Graph Eigenvalue Spectrum ===\n");
        sb.append(String.format("Vertices: %d\n", adjacencyEigenvalues.length));
        sb.append(String.format("Connected components: %d\n", componentCount));
        sb.append(String.format("Spectral radius (λ₁):       %.6f\n", spectralRadius));
        sb.append(String.format("Spectral gap (λ₁ − λ₂):     %.6f\n", spectralGap));
        sb.append(String.format("Algebraic connectivity (a):  %.6f\n", algebraicConnectivity));
        sb.append(String.format("Graph energy:                %.6f\n", graphEnergy));
        sb.append(String.format("Spectral radius ratio (ρ/√Δ): %.6f\n", spectralRadiusRatio));

        sb.append("\nTop 10 adjacency eigenvalues:\n");
        int limit = Math.min(10, adjacencyEigenvalues.length);
        for (int i = 0; i < limit; i++) {
            sb.append(String.format("  λ_%d = %.6f\n", i + 1, adjacencyEigenvalues[i]));
        }

        sb.append("\nSmallest 5 Laplacian eigenvalues:\n");
        int lapLimit = Math.min(5, laplacianEigenvalues.length);
        for (int i = 0; i < lapLimit; i++) {
            sb.append(String.format("  μ_%d = %.6f\n", i + 1, laplacianEigenvalues[i]));
        }

        // Classification
        sb.append("\nSpectral classification: ");
        if (componentCount > 1) {
            sb.append("DISCONNECTED — ");
        }
        if (spectralGap > 1.5) {
            sb.append("Strong expander (large spectral gap)");
        } else if (spectralGap > 0.5) {
            sb.append("Moderate expansion");
        } else {
            sb.append("Weak expansion (small spectral gap)");
        }
        sb.append("\n");
        return sb.toString();
    }

    // ---- Eigenvalue computation (Colt EigenvalueDecomposition) ----

    /**
     * Computes the eigenvalues of a real symmetric matrix using Colt's
     * {@link EigenvalueDecomposition}.
     *
     * <p>Previously this class shipped a hand-rolled Householder+implicit-QR
     * routine that crashed with {@code ArrayIndexOutOfBoundsException} on
     * every non-trivial graph (issue #169) and produced incorrect eigenvalues
     * even when it did not crash. Colt is already on the classpath
     * ({@code lib/colt-1.2.0.jar}), so we delegate to its well-tested
     * symmetric-matrix path.</p>
     */
    private double[] computeEigenvalues(double[][] matrix) {
        int n = matrix.length;
        if (n == 0) return new double[0];
        if (n == 1) return new double[]{matrix[0][0]};

        DoubleMatrix2D m = new DenseDoubleMatrix2D(n, n);
        for (int i = 0; i < n; i++) {
            double[] row = matrix[i];
            for (int j = 0; j < n; j++) {
                m.setQuick(i, j, row[j]);
            }
        }
        EigenvalueDecomposition eig = new EigenvalueDecomposition(m);
        double[] real = eig.getRealEigenvalues().toArray();
        // Imaginary parts must be ~0 for symmetric input; we trust Colt here.
        return real;
    }

    private void reverseArray(double[] arr) {
        int left = 0, right = arr.length - 1;
        while (left < right) {
            double tmp = arr[left];
            arr[left] = arr[right];
            arr[right] = tmp;
            left++;
            right--;
        }
    }

    private void ensureComputed() {
        if (!computed) {
            throw new IllegalStateException("Call compute() before querying results");
        }
    }
}
