package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Spectral Analyzer — computes eigenvalue-based properties of a graph using
 * the <b>adjacency matrix</b> and <b>Laplacian matrix</b>.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Adjacency spectrum</b> — all eigenvalues of the adjacency matrix,
 *       sorted in descending order.</li>
 *   <li><b>Laplacian spectrum</b> — all eigenvalues of the Laplacian matrix
 *       (L = D − A), sorted in ascending order.</li>
 *   <li><b>Spectral radius</b> — largest eigenvalue of the adjacency matrix;
 *       upper-bounds chromatic number and relates to maximum degree.</li>
 *   <li><b>Spectral gap</b> — difference between the two largest adjacency
 *       eigenvalues; large gap indicates good expansion properties.</li>
 *   <li><b>Algebraic connectivity</b> (Fiedler value) — second-smallest
 *       Laplacian eigenvalue λ₂.  Positive iff the graph is connected.
 *       Larger values mean the graph is harder to disconnect.</li>
 *   <li><b>Fiedler vector</b> — eigenvector for λ₂; its sign pattern gives
 *       a spectral bisection of the vertices into two communities.</li>
 *   <li><b>Spectral partitioning</b> — two-way vertex partition derived from
 *       the Fiedler vector sign.</li>
 *   <li><b>Energy</b> — sum of absolute values of adjacency eigenvalues;
 *       relates to total π-electron energy in chemistry.</li>
 *   <li><b>Number of spanning trees</b> — computed from non-zero Laplacian
 *       eigenvalues via Kirchhoff's theorem.</li>
 *   <li><b>Graph classification</b> — classifies spectral properties
 *       (bipartite test via symmetry, regularity via spectral radius).</li>
 *   <li><b>Text summary</b> — formatted multi-line report.</li>
 * </ul>
 *
 * <h3>Algorithm</h3>
 * <p>Uses the <b>Jacobi eigenvalue algorithm</b> for symmetric matrices —
 * a classical, numerically stable iterative method that applies Givens
 * rotations to diagonalise the matrix.  No external linear algebra
 * libraries are required.</p>
 *
 * <p>Complexity: O(n³) per sweep, typically converges in O(n) sweeps for
 * well-conditioned matrices. Suitable for graphs up to ~1000 nodes.</p>
 *
 * @author zalenix
 */
public class SpectralAnalyzer {

    private static final double EPSILON = 1e-10;
    private static final int MAX_SWEEPS = 100;

    private final Graph<String, Edge> graph;
    private boolean computed;

    // Ordered vertex list (defines row/column mapping)
    private List<String> vertexList;

    // ── Results ─────────────────────────────────────────────────────
    private double[] adjacencyEigenvalues;   // descending
    private double[] laplacianEigenvalues;   // ascending
    private double spectralRadius;
    private double spectralGap;
    private double algebraicConnectivity;    // λ₂ of Laplacian
    private double[] fiedlerVector;
    private List<String> partitionA;
    private List<String> partitionB;
    private double energy;
    private double spanningTreeCount;
    private boolean bipartiteLikely;
    private boolean connectedSpectrally;
    private String classification;

    /**
     * Creates a new SpectralAnalyzer for the given graph.
     *
     * @param graph the JUNG graph to analyse
     * @throws IllegalArgumentException if graph is null
     */
    public SpectralAnalyzer(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.computed = false;
        this.partitionA = new ArrayList<String>();
        this.partitionB = new ArrayList<String>();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Computation
    // ═══════════════════════════════════════════════════════════════

    /**
     * Runs the full spectral analysis. Idempotent — repeated calls are no-ops.
     *
     * @return this analyzer for chaining
     */
    public SpectralAnalyzer compute() {
        if (computed) return this;

        Collection<String> vertices = graph.getVertices();
        int n = vertices.size();

        if (n == 0) {
            adjacencyEigenvalues = new double[0];
            laplacianEigenvalues = new double[0];
            fiedlerVector = new double[0];
            spectralRadius = 0.0;
            spectralGap = 0.0;
            algebraicConnectivity = 0.0;
            energy = 0.0;
            spanningTreeCount = 0.0;
            bipartiteLikely = true;
            connectedSpectrally = false;
            classification = "Empty";
            computed = true;
            return this;
        }

        if (n == 1) {
            adjacencyEigenvalues = new double[]{0.0};
            laplacianEigenvalues = new double[]{0.0};
            fiedlerVector = new double[]{0.0};
            spectralRadius = 0.0;
            spectralGap = 0.0;
            algebraicConnectivity = 0.0;
            energy = 0.0;
            spanningTreeCount = 0.0;
            bipartiteLikely = true;
            connectedSpectrally = false;
            classification = "Trivial";
            computed = true;
            return this;
        }

        // Build ordered vertex list
        vertexList = new ArrayList<String>(vertices);
        Collections.sort(vertexList);

        // Build adjacency matrix
        double[][] A = buildAdjacencyMatrix(n);

        // Build Laplacian matrix L = D - A
        double[][] L = buildLaplacianMatrix(A, n);

        // Compute eigenvalues + eigenvectors of A
        EigenResult adjResult = jacobiEigen(A, n);
        adjacencyEigenvalues = adjResult.eigenvalues.clone();
        Arrays.sort(adjacencyEigenvalues);
        reverse(adjacencyEigenvalues);  // descending

        // Compute eigenvalues + eigenvectors of L
        EigenResult lapResult = jacobiEigen(L, n);
        laplacianEigenvalues = lapResult.eigenvalues.clone();
        Arrays.sort(laplacianEigenvalues);  // ascending

        // Clamp near-zero Laplacian eigenvalues (numerical noise)
        for (int i = 0; i < laplacianEigenvalues.length; i++) {
            if (Math.abs(laplacianEigenvalues[i]) < EPSILON) {
                laplacianEigenvalues[i] = 0.0;
            }
        }

        // ── Spectral radius ────────────────────────────────────────
        spectralRadius = adjacencyEigenvalues[0];

        // ── Spectral gap ───────────────────────────────────────────
        spectralGap = n >= 2
                ? adjacencyEigenvalues[0] - adjacencyEigenvalues[1]
                : 0.0;

        // ── Algebraic connectivity (λ₂ of Laplacian) ──────────────
        // λ₂ is always the second-smallest Laplacian eigenvalue.
        // For connected graphs λ₂ > 0; for disconnected graphs λ₂ = 0.
        algebraicConnectivity = n >= 2 ? laplacianEigenvalues[1] : 0.0;

        // Count zero eigenvalues to determine connectivity
        int zeroCount = 0;
        for (double lam : laplacianEigenvalues) {
            if (Math.abs(lam) < EPSILON) zeroCount++;
            else break;
        }
        connectedSpectrally = zeroCount == 1;

        // ── Fiedler vector ─────────────────────────────────────────
        // Find the eigenvector corresponding to λ₂
        fiedlerVector = new double[n];
        if (n >= 2 && connectedSpectrally) {
            // Find which column of lapResult.eigenvectors corresponds
            // to the second-smallest eigenvalue
            int fiedlerIdx = findFiedlerIndex(lapResult.eigenvalues);
            for (int i = 0; i < n; i++) {
                fiedlerVector[i] = lapResult.eigenvectors[i][fiedlerIdx];
            }
        }

        // ── Spectral partitioning ──────────────────────────────────
        partitionA = new ArrayList<String>();
        partitionB = new ArrayList<String>();
        for (int i = 0; i < n; i++) {
            if (fiedlerVector[i] >= 0) {
                partitionA.add(vertexList.get(i));
            } else {
                partitionB.add(vertexList.get(i));
            }
        }

        // ── Energy ─────────────────────────────────────────────────
        energy = 0.0;
        for (double ev : adjacencyEigenvalues) {
            energy += Math.abs(ev);
        }

        // ── Spanning tree count (Kirchhoff's theorem) ──────────────
        // Product of non-zero Laplacian eigenvalues / n
        if (n >= 2 && connectedSpectrally) {
            double product = 1.0;
            for (double lam : laplacianEigenvalues) {
                if (lam > EPSILON) {
                    product *= lam;
                }
            }
            spanningTreeCount = product / n;
        } else {
            spanningTreeCount = 0.0;
        }

        // ── Bipartite test ─────────────────────────────────────────
        // A graph is bipartite iff its adjacency spectrum is symmetric
        // about zero: for each eigenvalue λ, -λ is also an eigenvalue
        bipartiteLikely = checkBipartite(adjacencyEigenvalues);

        // ── Classification ─────────────────────────────────────────
        classification = classify(n);

        computed = true;
        return this;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Matrix builders
    // ═══════════════════════════════════════════════════════════════

    private double[][] buildAdjacencyMatrix(int n) {
        return LaplacianBuilder.buildAdjacencyMatrix(graph, vertexList);
    }

    private double[][] buildLaplacianMatrix(double[][] A, int n) {
        return LaplacianBuilder.buildLaplacian(A, n);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Jacobi eigenvalue algorithm for symmetric matrices
    // ═══════════════════════════════════════════════════════════════

    private static class EigenResult {
        double[] eigenvalues;
        double[][] eigenvectors;

        EigenResult(double[] eigenvalues, double[][] eigenvectors) {
            this.eigenvalues = eigenvalues;
            this.eigenvectors = eigenvectors;
        }
    }

    /**
     * Computes all eigenvalues and eigenvectors of a symmetric matrix
     * using the Jacobi rotation method.
     */
    private EigenResult jacobiEigen(double[][] matrix, int n) {
        // Work on a copy
        double[][] A = new double[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(matrix[i], 0, A[i], 0, n);
        }

        // Eigenvector matrix (starts as identity)
        double[][] V = new double[n][n];
        for (int i = 0; i < n; i++) {
            V[i][i] = 1.0;
        }

        for (int sweep = 0; sweep < MAX_SWEEPS; sweep++) {
            // Check convergence: sum of squares of off-diagonal elements
            double offDiag = 0.0;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    offDiag += A[i][j] * A[i][j];
                }
            }
            if (offDiag < EPSILON * EPSILON) break;

            for (int p = 0; p < n - 1; p++) {
                for (int q = p + 1; q < n; q++) {
                    if (Math.abs(A[p][q]) < EPSILON / n) continue;

                    double theta;
                    if (Math.abs(A[p][p] - A[q][q]) < EPSILON) {
                        theta = Math.PI / 4.0;
                    } else {
                        theta = 0.5 * Math.atan2(2.0 * A[p][q],
                                A[p][p] - A[q][q]);
                    }

                    double c = Math.cos(theta);
                    double s = Math.sin(theta);

                    // Apply Givens rotation
                    applyRotation(A, V, p, q, c, s, n);
                }
            }
        }

        double[] eigenvalues = new double[n];
        for (int i = 0; i < n; i++) {
            eigenvalues[i] = A[i][i];
        }

        return new EigenResult(eigenvalues, V);
    }

    private void applyRotation(double[][] A, double[][] V,
                               int p, int q, double c, double s, int n) {
        // Rotate columns p, q of A
        for (int i = 0; i < n; i++) {
            if (i == p || i == q) continue;
            double aip = A[i][p];
            double aiq = A[i][q];
            A[i][p] = c * aip + s * aiq;
            A[p][i] = A[i][p];
            A[i][q] = -s * aip + c * aiq;
            A[q][i] = A[i][q];
        }

        double app = A[p][p];
        double aqq = A[q][q];
        double apq = A[p][q];
        A[p][p] = c * c * app + 2 * s * c * apq + s * s * aqq;
        A[q][q] = s * s * app - 2 * s * c * apq + c * c * aqq;
        A[p][q] = 0.0;
        A[q][p] = 0.0;

        // Accumulate eigenvectors
        for (int i = 0; i < n; i++) {
            double vip = V[i][p];
            double viq = V[i][q];
            V[i][p] = c * vip + s * viq;
            V[i][q] = -s * vip + c * viq;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Helper methods
    // ═══════════════════════════════════════════════════════════════

    private void reverse(double[] arr) {
        int left = 0, right = arr.length - 1;
        while (left < right) {
            double tmp = arr[left];
            arr[left] = arr[right];
            arr[right] = tmp;
            left++;
            right--;
        }
    }

    /**
     * Find the index in the raw (unsorted) eigenvalue array that
     * corresponds to the second-smallest eigenvalue.
     */
    private int findFiedlerIndex(double[] rawEigenvalues) {
        int n = rawEigenvalues.length;
        // Find indices sorted by eigenvalue ascending
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) indices[i] = i;

        final double[] vals = rawEigenvalues;
        Arrays.sort(indices, (Integer a, Integer b) -> {
                return Double.compare(vals[a], vals[b]);
            });

        // Skip zero eigenvalues, return index of first non-zero
        for (int i = 0; i < n; i++) {
            if (Math.abs(vals[indices[i]]) > EPSILON) {
                return indices[i];
            }
        }
        return indices.length > 1 ? indices[1] : 0;
    }

    private boolean checkBipartite(double[] eigenvalues) {
        // Check if spectrum is symmetric about zero
        int n = eigenvalues.length;
        if (n <= 1) return true;

        for (int i = 0; i < n / 2; i++) {
            double pos = eigenvalues[i];
            double neg = eigenvalues[n - 1 - i];
            if (Math.abs(pos + neg) > 0.01) {
                return false;
            }
        }
        return true;
    }

    private String classify(int n) {
        StringBuilder sb = new StringBuilder();
        List<String> traits = new ArrayList<String>();

        if (!connectedSpectrally) {
            int components = 0;
            for (double lam : laplacianEigenvalues) {
                if (Math.abs(lam) < EPSILON) components++;
            }
            traits.add("Disconnected (" + components + " components)");
        } else {
            traits.add("Connected");
        }

        if (bipartiteLikely) {
            traits.add("Bipartite");
        }

        // Check regularity: spectral radius ≈ max degree and
        // smallest adjacency eigenvalue ≈ -spectral radius for bipartite regular
        double maxDeg = 0;
        for (String v : vertexList) {
            maxDeg = Math.max(maxDeg, graph.degree(v));
        }
        double minDeg = Double.MAX_VALUE;
        for (String v : vertexList) {
            minDeg = Math.min(minDeg, graph.degree(v));
        }
        if (Math.abs(maxDeg - minDeg) < EPSILON && n > 1) {
            traits.add("Regular (degree " + (int) maxDeg + ")");
        }

        // Expansion quality from spectral gap
        if (spectralGap > 1.0) {
            traits.add("Good expander (gap=" + String.format("%.3f", spectralGap) + ")");
        } else if (spectralGap > 0.1) {
            traits.add("Moderate expander");
        } else if (n > 2) {
            traits.add("Weak expander");
        }

        // Robustness from algebraic connectivity
        if (algebraicConnectivity > 1.0) {
            traits.add("Highly robust (λ₂=" + String.format("%.3f", algebraicConnectivity) + ")");
        } else if (algebraicConnectivity > 0.1) {
            traits.add("Moderately robust");
        } else if (connectedSpectrally) {
            traits.add("Fragile connectivity");
        }

        for (int i = 0; i < traits.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(traits.get(i));
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Getters
    // ═══════════════════════════════════════════════════════════════

    private void ensureComputed() {
        if (!computed) {
            throw new IllegalStateException(
                    "Call compute() before accessing results");
        }
    }

    /** Adjacency eigenvalues in descending order. */
    public double[] getAdjacencyEigenvalues() {
        ensureComputed();
        return adjacencyEigenvalues.clone();
    }

    /** Laplacian eigenvalues in ascending order. */
    public double[] getLaplacianEigenvalues() {
        ensureComputed();
        return laplacianEigenvalues.clone();
    }

    /** Largest adjacency eigenvalue. */
    public double getSpectralRadius() {
        ensureComputed();
        return spectralRadius;
    }

    /** Difference between two largest adjacency eigenvalues. */
    public double getSpectralGap() {
        ensureComputed();
        return spectralGap;
    }

    /** Second-smallest Laplacian eigenvalue (Fiedler value). */
    public double getAlgebraicConnectivity() {
        ensureComputed();
        return algebraicConnectivity;
    }

    /** Eigenvector for λ₂ — sign gives spectral bisection. */
    public double[] getFiedlerVector() {
        ensureComputed();
        return fiedlerVector.clone();
    }

    /** Vertices with non-negative Fiedler vector component. */
    public List<String> getPartitionA() {
        ensureComputed();
        return Collections.unmodifiableList(partitionA);
    }

    /** Vertices with negative Fiedler vector component. */
    public List<String> getPartitionB() {
        ensureComputed();
        return Collections.unmodifiableList(partitionB);
    }

    /** Sum of absolute adjacency eigenvalues. */
    public double getEnergy() {
        ensureComputed();
        return energy;
    }

    /** Number of spanning trees (Kirchhoff's theorem). */
    public double getSpanningTreeCount() {
        ensureComputed();
        return spanningTreeCount;
    }

    /** True if the adjacency spectrum is symmetric (bipartite indicator). */
    public boolean isBipartiteLikely() {
        ensureComputed();
        return bipartiteLikely;
    }

    /** True if exactly one zero Laplacian eigenvalue (connected). */
    public boolean isConnectedSpectrally() {
        ensureComputed();
        return connectedSpectrally;
    }

    /** Human-readable classification of spectral properties. */
    public String getClassification() {
        ensureComputed();
        return classification;
    }

    /** Ordered vertex list used for matrix indexing. */
    public List<String> getVertexOrder() {
        ensureComputed();
        return Collections.unmodifiableList(vertexList);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Summary
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns a formatted multi-line text summary of the spectral analysis.
     *
     * @return summary string
     */
    public String getSummary() {
        ensureComputed();
        int n = adjacencyEigenvalues.length;

        StringBuilder sb = new StringBuilder();
        sb.append("=== Spectral Analysis ===\n");
        sb.append(String.format("Vertices: %d\n", n));
        sb.append(String.format("Spectral radius: %.6f\n", spectralRadius));
        sb.append(String.format("Spectral gap: %.6f\n", spectralGap));
        sb.append(String.format("Algebraic connectivity (λ₂): %.6f\n",
                algebraicConnectivity));
        sb.append(String.format("Energy: %.6f\n", energy));
        sb.append(String.format("Spanning trees: %.0f\n", spanningTreeCount));
        sb.append(String.format("Connected (spectrally): %s\n",
                connectedSpectrally ? "yes" : "no"));
        sb.append(String.format("Bipartite (spectral test): %s\n",
                bipartiteLikely ? "likely" : "unlikely"));
        sb.append(String.format("Classification: %s\n", classification));

        sb.append("\nAdjacency eigenvalues (top 5): ");
        for (int i = 0; i < Math.min(5, n); i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%.4f", adjacencyEigenvalues[i]));
        }
        sb.append("\n");

        sb.append("Laplacian eigenvalues (bottom 5): ");
        for (int i = 0; i < Math.min(5, n); i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%.4f", laplacianEigenvalues[i]));
        }
        sb.append("\n");

        if (partitionA.size() > 0 || partitionB.size() > 0) {
            sb.append(String.format("\nSpectral bisection: %d vs %d vertices\n",
                    partitionA.size(), partitionB.size()));
        }

        return sb.toString();
    }

    /**
     * Returns all results as a map for programmatic access / JSON export.
     *
     * @return map of result keys to values
     */
    public Map<String, Object> toMap() {
        ensureComputed();
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("spectralRadius", spectralRadius);
        map.put("spectralGap", spectralGap);
        map.put("algebraicConnectivity", algebraicConnectivity);
        map.put("energy", energy);
        map.put("spanningTreeCount", spanningTreeCount);
        map.put("connectedSpectrally", connectedSpectrally);
        map.put("bipartiteLikely", bipartiteLikely);
        map.put("classification", classification);
        map.put("partitionA", new ArrayList<String>(partitionA));
        map.put("partitionB", new ArrayList<String>(partitionB));

        List<Double> adjEv = new ArrayList<Double>();
        for (double v : adjacencyEigenvalues) adjEv.add(v);
        map.put("adjacencyEigenvalues", adjEv);

        List<Double> lapEv = new ArrayList<Double>();
        for (double v : laplacianEigenvalues) lapEv.add(v);
        map.put("laplacianEigenvalues", lapEv);

        return map;
    }
}
