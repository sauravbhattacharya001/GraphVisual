package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GraphSpectralClusteringEngine — autonomous spectral clustering engine for
 * graph partitioning using Laplacian eigenvectors. Combines eigengap heuristics,
 * Fiedler bisection, k-way spectral partitioning (k-means on spectral embeddings),
 * conductance analysis, and composite quality scoring.
 *
 * <h3>Seven Analysis Engines:</h3>
 * <ol>
 *   <li><b>Laplacian Eigensolver</b> — Builds symmetric normalized Laplacian
 *       (Lsym = I − D^{-1/2} A D^{-1/2}), computes eigenvalues and eigenvectors
 *       via Householder tridiagonalization + implicit QR with Wilkinson shifts.</li>
 *   <li><b>Optimal-K Detector</b> — Eigengap heuristic: finds largest relative
 *       gap in sorted eigenvalues to recommend number of clusters. Second-derivative
 *       confirmation signal.</li>
 *   <li><b>Fiedler Bisection Engine</b> — Uses 2nd smallest eigenvector (Fiedler
 *       vector) for graph bisection. Sweeps threshold to minimize normalized cut.</li>
 *   <li><b>K-Way Spectral Partitioner</b> — Embeds nodes in R^k using first k
 *       eigenvectors, then applies k-means++ with multiple restarts.</li>
 *   <li><b>Conductance Analyzer</b> — Per-cluster conductance φ(S) and overall
 *       graph conductance (Cheeger constant approximation).</li>
 *   <li><b>Cluster Quality Scorer</b> — Modularity Q, normalized cut, silhouette
 *       scores, inter/intra edge ratios. Composite quality score 0–100.</li>
 *   <li><b>Insight Generator</b> — Autonomous insights about cluster structure,
 *       bridge nodes, balance, separation quality, and recommendations.</li>
 * </ol>
 *
 * @author zalenix
 */
public class GraphSpectralClusteringEngine {

    // -- Constants -----------------------------------------------------------
    private static final double EPSILON = 1e-10;
    private static final int MAX_QR_ITERATIONS = 500;

    // -- Configuration -------------------------------------------------------
    private int maxK = 10;
    private int kmeansRestarts = 10;
    private int kmeansMaxIter = 100;
    private Random rng = new Random(42);

    // -- Builder-style setters -----------------------------------------------

    public GraphSpectralClusteringEngine setMaxK(int k) {
        this.maxK = Math.max(2, k);
        return this;
    }

    public GraphSpectralClusteringEngine setKmeansRestarts(int n) {
        this.kmeansRestarts = Math.max(1, n);
        return this;
    }

    public GraphSpectralClusteringEngine setKmeansMaxIter(int n) {
        this.kmeansMaxIter = Math.max(1, n);
        return this;
    }

    public GraphSpectralClusteringEngine setRandomSeed(long seed) {
        this.rng = new Random(seed);
        return this;
    }

    public GraphSpectralClusteringEngine setRng(Random rng) {
        this.rng = rng;
        return this;
    }

    // ====================================================================
    // Inner classes
    // ====================================================================

    /** Severity of an insight. */
    public enum InsightSeverity { INFO, WARNING, CRITICAL }

    /** A single autonomous insight. */
    public static class SpectralInsight {
        public final String type;
        public final String description;
        public final InsightSeverity severity;

        public SpectralInsight(String type, String description, InsightSeverity severity) {
            this.type = type;
            this.description = description;
            this.severity = severity;
        }
    }

    /** Eigengap analysis result. */
    public static class EigengapResult {
        public final double[] eigenvalues;
        public final double[] gaps;
        public final int recommendedK;
        public final double maxGapRatio;

        public EigengapResult(double[] eigenvalues, double[] gaps, int recommendedK, double maxGapRatio) {
            this.eigenvalues = eigenvalues;
            this.gaps = gaps;
            this.recommendedK = recommendedK;
            this.maxGapRatio = maxGapRatio;
        }
    }

    /** Fiedler bisection result. */
    public static class BisectionResult {
        public final Map<String, Integer> assignment; // node -> 0 or 1
        public final double ncutValue;
        public final double cutEdges;
        public final double threshold;

        public BisectionResult(Map<String, Integer> assignment, double ncutValue,
                               double cutEdges, double threshold) {
            this.assignment = assignment;
            this.ncutValue = ncutValue;
            this.cutEdges = cutEdges;
            this.threshold = threshold;
        }
    }

    /** Per-cluster information. */
    public static class ClusterInfo {
        public final int clusterId;
        public final List<String> members;
        public final double conductance;
        public final int internalEdges;
        public final int boundaryEdges;
        public final List<String> boundaryNodes;

        public ClusterInfo(int clusterId, List<String> members, double conductance,
                           int internalEdges, int boundaryEdges, List<String> boundaryNodes) {
            this.clusterId = clusterId;
            this.members = members;
            this.conductance = conductance;
            this.internalEdges = internalEdges;
            this.boundaryEdges = boundaryEdges;
            this.boundaryNodes = boundaryNodes;
        }
    }

    /** Full spectral clustering report. */
    public static class SpectralClusteringReport {
        public final int vertexCount;
        public final int edgeCount;
        public final EigengapResult eigengap;
        public final BisectionResult bisection;
        public final Map<String, Integer> kWayAssignment;
        public final int kUsed;
        public final List<ClusterInfo> clusters;
        public final double modularity;
        public final double normalizedCut;
        public final double avgSilhouette;
        public final double compositeScore;
        public final double graphConductance;
        public final List<SpectralInsight> insights;

        public SpectralClusteringReport(int vertexCount, int edgeCount,
                                         EigengapResult eigengap, BisectionResult bisection,
                                         Map<String, Integer> kWayAssignment, int kUsed,
                                         List<ClusterInfo> clusters, double modularity,
                                         double normalizedCut, double avgSilhouette,
                                         double compositeScore, double graphConductance,
                                         List<SpectralInsight> insights) {
            this.vertexCount = vertexCount;
            this.edgeCount = edgeCount;
            this.eigengap = eigengap;
            this.bisection = bisection;
            this.kWayAssignment = kWayAssignment;
            this.kUsed = kUsed;
            this.clusters = clusters;
            this.modularity = modularity;
            this.normalizedCut = normalizedCut;
            this.avgSilhouette = avgSilhouette;
            this.compositeScore = compositeScore;
            this.graphConductance = graphConductance;
            this.insights = insights;
        }
    }

    // ====================================================================
    // Main analysis method
    // ====================================================================

    /**
     * Runs spectral clustering analysis on the given graph.
     *
     * @param graph the JUNG graph to analyze
     * @return complete spectral clustering report
     */
    public SpectralClusteringReport analyze(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }

        int n = graph.getVertexCount();
        int edgeCount = graph.getEdgeCount();

        // Trivial graphs
        if (n == 0) {
            return new SpectralClusteringReport(0, 0,
                    new EigengapResult(new double[0], new double[0], 1, 0),
                    new BisectionResult(Collections.emptyMap(), 0, 0, 0),
                    Collections.emptyMap(), 1, Collections.emptyList(),
                    0, 0, 0, 50, 0, Collections.singletonList(
                    new SpectralInsight("EMPTY", "Graph is empty", InsightSeverity.INFO)));
        }

        List<String> vertices = new ArrayList<>(graph.getVertices());
        Collections.sort(vertices);
        Map<String, Integer> idxMap = new HashMap<>();
        for (int i = 0; i < n; i++) idxMap.put(vertices.get(i), i);

        if (n == 1) {
            Map<String, Integer> assign = new HashMap<>();
            assign.put(vertices.get(0), 0);
            ClusterInfo ci = new ClusterInfo(0, new ArrayList<>(vertices), 0, 0, 0, Collections.emptyList());
            return new SpectralClusteringReport(1, edgeCount,
                    new EigengapResult(new double[]{0}, new double[0], 1, 0),
                    new BisectionResult(assign, 0, 0, 0),
                    assign, 1, Collections.singletonList(ci),
                    0, 0, 1.0, 50, 0,
                    Collections.singletonList(new SpectralInsight("SINGLETON",
                            "Graph has a single vertex", InsightSeverity.INFO)));
        }

        // Build adjacency and degree arrays
        double[][] adj = buildAdjacencyMatrix(graph, vertices, idxMap);
        double[] degrees = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) degrees[i] += adj[i][j];
        }

        // Engine 1: Laplacian eigensolver
        EigenDecomposition eigen = computeNormalizedLaplacianEigen(adj, degrees, n);

        // Engine 2: Optimal-K detection
        EigengapResult eigengap = detectOptimalK(eigen.eigenvalues, n);
        int kOpt = Math.min(eigengap.recommendedK, n);

        // Engine 3: Fiedler bisection
        BisectionResult bisection = fiedlerBisection(eigen, vertices, adj, degrees, n);

        // Engine 4: K-way spectral partitioning
        Map<String, Integer> kWayAssignment;
        if (kOpt <= 1) {
            kWayAssignment = new HashMap<>();
            for (String v : vertices) kWayAssignment.put(v, 0);
            kOpt = 1;
        } else {
            kWayAssignment = kWayPartition(eigen, vertices, kOpt, n);
        }

        // Engine 5: Conductance analysis
        List<ClusterInfo> clusters = buildClusterInfo(graph, kWayAssignment, kOpt, vertices);
        double graphConductance = computeGraphConductance(clusters);

        // Engine 6: Quality scoring
        double modularity = computeModularity(adj, degrees, kWayAssignment, vertices, n);
        double ncut = computeNormalizedCut(adj, kWayAssignment, vertices, kOpt, n);
        double silhouette = computeAvgSilhouette(eigen, kWayAssignment, vertices, kOpt, n);
        double composite = computeCompositeScore(modularity, ncut, silhouette,
                graphConductance, clusters.size());

        // Engine 7: Insights
        List<SpectralInsight> insights = generateInsights(eigengap, bisection, clusters,
                modularity, ncut, silhouette, composite, kOpt, n);

        return new SpectralClusteringReport(n, edgeCount, eigengap, bisection,
                kWayAssignment, kOpt, clusters, modularity, ncut, silhouette,
                composite, graphConductance, insights);
    }

    // ====================================================================
    // Engine 1: Laplacian eigensolver
    // ====================================================================

    private static class EigenDecomposition {
        double[] eigenvalues;   // sorted ascending
        double[][] eigenvectors; // eigenvectors[i] = i-th eigenvector (column)
        // eigenvectors stored as [vectorIndex][component]
    }

    private EigenDecomposition computeNormalizedLaplacianEigen(double[][] adj,
                                                                double[] degrees, int n) {
        // Build normalized Laplacian Lsym = I - D^{-1/2} A D^{-1/2}
        double[] dInvSqrt = new double[n];
        for (int i = 0; i < n; i++) {
            dInvSqrt[i] = degrees[i] > EPSILON ? 1.0 / Math.sqrt(degrees[i]) : 0;
        }

        double[][] L = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    L[i][j] = degrees[i] > EPSILON ? 1.0 : 0.0;
                } else {
                    L[i][j] = -adj[i][j] * dInvSqrt[i] * dInvSqrt[j];
                }
            }
        }

        // Compute eigenvalues and eigenvectors using symmetric QR
        return symmetricEigen(L, n);
    }

    /**
     * Full symmetric eigendecomposition via Jacobi rotation method.
     * Computes all eigenvalues and eigenvectors for a real symmetric matrix.
     * Simple, robust, and always converges.
     */
    private EigenDecomposition symmetricEigen(double[][] matrix, int n) {
        EigenDecomposition result = new EigenDecomposition();
        if (n == 0) {
            result.eigenvalues = new double[0];
            result.eigenvectors = new double[0][0];
            return result;
        }
        if (n == 1) {
            result.eigenvalues = new double[]{matrix[0][0]};
            result.eigenvectors = new double[][]{{1.0}};
            return result;
        }

        // Copy matrix (will be modified in place)
        double[][] A = new double[n][n];
        for (int i = 0; i < n; i++) A[i] = Arrays.copyOf(matrix[i], n);

        // Eigenvector matrix (starts as identity)
        double[][] V = new double[n][n];
        for (int i = 0; i < n; i++) V[i][i] = 1.0;

        // Jacobi cyclic sweep
        for (int sweep = 0; sweep < MAX_QR_ITERATIONS; sweep++) {
            // Check off-diagonal convergence
            double offDiagSum = 0;
            for (int i = 0; i < n; i++)
                for (int j = i + 1; j < n; j++)
                    offDiagSum += A[i][j] * A[i][j];
            if (offDiagSum < EPSILON * EPSILON) break;

            for (int p = 0; p < n - 1; p++) {
                for (int q = p + 1; q < n; q++) {
                    if (Math.abs(A[p][q]) < EPSILON) continue;

                    // Compute rotation angle
                    double diff = A[q][q] - A[p][p];
                    double t;
                    if (Math.abs(diff) < EPSILON) {
                        t = 1.0;
                    } else {
                        double phi = diff / (2.0 * A[p][q]);
                        t = 1.0 / (Math.abs(phi) + Math.sqrt(phi * phi + 1.0));
                        if (phi < 0) t = -t;
                    }

                    double c = 1.0 / Math.sqrt(t * t + 1.0);
                    double s = t * c;
                    double tau = s / (1.0 + c);
                    double aTemp = A[p][q];

                    A[p][q] = 0;
                    A[p][p] -= t * aTemp;
                    A[q][q] += t * aTemp;

                    // Rotate rows and columns
                    for (int i = 0; i < p; i++) {
                        double g = A[i][p];
                        double h = A[i][q];
                        A[i][p] = g - s * (h + g * tau);
                        A[i][q] = h + s * (g - h * tau);
                    }
                    for (int i = p + 1; i < q; i++) {
                        double g = A[p][i];
                        double h = A[i][q];
                        A[p][i] = g - s * (h + g * tau);
                        A[i][q] = h + s * (g - h * tau);
                    }
                    for (int i = q + 1; i < n; i++) {
                        double g = A[p][i];
                        double h = A[q][i];
                        A[p][i] = g - s * (h + g * tau);
                        A[q][i] = h + s * (g - h * tau);
                    }

                    // Accumulate eigenvectors
                    for (int i = 0; i < n; i++) {
                        double g = V[i][p];
                        double h = V[i][q];
                        V[i][p] = g - s * (h + g * tau);
                        V[i][q] = h + s * (g - h * tau);
                    }
                }
            }
        }

        // Eigenvalues are on the diagonal of A
        double[] eigenvalues = new double[n];
        for (int i = 0; i < n; i++) eigenvalues[i] = A[i][i];

        // Sort by eigenvalue ascending and reorder eigenvectors
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) indices[i] = i;
        final double[] ev = eigenvalues;
        Arrays.sort(indices, (a, b) -> Double.compare(ev[a], ev[b]));

        result.eigenvalues = new double[n];
        result.eigenvectors = new double[n][n]; // [eigenIndex][component]
        for (int i = 0; i < n; i++) {
            result.eigenvalues[i] = eigenvalues[indices[i]];
            for (int j = 0; j < n; j++) {
                result.eigenvectors[i][j] = V[j][indices[i]]; // column indices[i]
            }
        }
        return result;
    }

    // ====================================================================
    // Engine 2: Optimal-K detection
    // ====================================================================

    private EigengapResult detectOptimalK(double[] eigenvalues, int n) {
        if (n <= 1) {
            return new EigengapResult(eigenvalues, new double[0], 1, 0);
        }

        int limit = Math.min(maxK + 1, n);
        double[] gaps = new double[limit - 1];
        for (int i = 0; i < gaps.length; i++) {
            gaps[i] = (i + 1 < n) ? eigenvalues[i + 1] - eigenvalues[i] : 0;
        }

        // Count near-zero eigenvalues as connected components
        int nearZero = 0;
        for (int i = 0; i < n; i++) {
            if (Math.abs(eigenvalues[i]) < 1e-6) nearZero++;
        }
        // If multiple components, that's the natural k
        if (nearZero > 1 && nearZero <= maxK) {
            double maxGap = 0;
            for (double g : gaps) maxGap = Math.max(maxGap, g);
            return new EigengapResult(eigenvalues, gaps, nearZero, maxGap);
        }

        // Find largest absolute gap starting from index 0
        // The eigengap heuristic: k = argmax(gap[k]) for k >= 1
        // gap[i] is eigenvalues[i+1] - eigenvalues[i]
        // k clusters = the position just before the largest gap
        int bestK = 1;
        double bestGap = 0;
        for (int i = 0; i < gaps.length; i++) {
            if (gaps[i] > bestGap) {
                bestGap = gaps[i];
                bestK = i + 1; // number of eigenvalues before the gap
            }
        }

        // Ensure k >= 1
        bestK = Math.max(1, bestK);

        return new EigengapResult(eigenvalues, gaps, bestK, bestGap);
    }

    // ====================================================================
    // Engine 3: Fiedler bisection
    // ====================================================================

    private BisectionResult fiedlerBisection(EigenDecomposition eigen,
                                              List<String> vertices,
                                              double[][] adj, double[] degrees, int n) {
        if (n <= 1) {
            Map<String, Integer> a = new HashMap<>();
            for (String v : vertices) a.put(v, 0);
            return new BisectionResult(a, 0, 0, 0);
        }

        // Fiedler vector = eigenvector for 2nd smallest eigenvalue
        double[] fiedler = eigen.eigenvectors[1]; // index 1 = 2nd smallest

        // Sweep threshold to find best normalized cut
        double[] sorted = Arrays.copyOf(fiedler, n);
        Arrays.sort(sorted);

        double bestNcut = Double.MAX_VALUE;
        double bestThreshold = 0;
        Map<String, Integer> bestAssign = null;

        // Try thresholds between consecutive sorted values
        for (int t = 0; t < n - 1; t++) {
            double threshold = (sorted[t] + sorted[t + 1]) / 2.0;
            Map<String, Integer> assign = new HashMap<>();
            for (int i = 0; i < n; i++) {
                assign.put(vertices.get(i), fiedler[i] <= threshold ? 0 : 1);
            }

            double ncut = computeNcutForBisection(adj, assign, vertices, degrees, n);
            if (ncut < bestNcut) {
                bestNcut = ncut;
                bestThreshold = threshold;
                bestAssign = assign;
            }
        }

        // Also try median
        double median = sorted[n / 2];
        Map<String, Integer> medianAssign = new HashMap<>();
        for (int i = 0; i < n; i++) {
            medianAssign.put(vertices.get(i), fiedler[i] <= median ? 0 : 1);
        }
        double medianNcut = computeNcutForBisection(adj, medianAssign, vertices, degrees, n);
        if (medianNcut < bestNcut) {
            bestNcut = medianNcut;
            bestThreshold = median;
            bestAssign = medianAssign;
        }

        if (bestAssign == null) {
            bestAssign = new HashMap<>();
            for (String v : vertices) bestAssign.put(v, 0);
        }

        // Count cut edges
        double cutEdges = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (adj[i][j] > 0 && !bestAssign.get(vertices.get(i))
                        .equals(bestAssign.get(vertices.get(j)))) {
                    cutEdges += adj[i][j];
                }
            }
        }

        return new BisectionResult(bestAssign, bestNcut, cutEdges, bestThreshold);
    }

    private double computeNcutForBisection(double[][] adj, Map<String, Integer> assign,
                                            List<String> vertices, double[] degrees, int n) {
        double cut = 0, vol0 = 0, vol1 = 0;
        for (int i = 0; i < n; i++) {
            int ci = assign.get(vertices.get(i));
            if (ci == 0) vol0 += degrees[i];
            else vol1 += degrees[i];
            for (int j = i + 1; j < n; j++) {
                if (adj[i][j] > 0 && ci != assign.get(vertices.get(j))) {
                    cut += adj[i][j];
                }
            }
        }
        if (vol0 < EPSILON || vol1 < EPSILON) return Double.MAX_VALUE;
        return cut / vol0 + cut / vol1;
    }

    // ====================================================================
    // Engine 4: K-way spectral partitioning
    // ====================================================================

    private Map<String, Integer> kWayPartition(EigenDecomposition eigen,
                                                List<String> vertices, int k, int n) {
        // Build spectral embedding: each node -> R^k using first k eigenvectors
        double[][] embedding = new double[n][k];
        for (int i = 0; i < n; i++) {
            double norm = 0;
            for (int d = 0; d < k; d++) {
                embedding[i][d] = eigen.eigenvectors[d][i];
                norm += embedding[i][d] * embedding[i][d];
            }
            // Normalize rows
            norm = Math.sqrt(norm);
            if (norm > EPSILON) {
                for (int d = 0; d < k; d++) embedding[i][d] /= norm;
            }
        }

        // K-means++ with restarts
        int[] bestLabels = null;
        double bestCost = Double.MAX_VALUE;

        for (int restart = 0; restart < kmeansRestarts; restart++) {
            double[][] centroids = kmeansPPInit(embedding, k, n);
            int[] labels = new int[n];
            double cost = Double.MAX_VALUE;

            for (int iter = 0; iter < kmeansMaxIter; iter++) {
                // Assign
                double newCost = 0;
                for (int i = 0; i < n; i++) {
                    double minDist = Double.MAX_VALUE;
                    for (int c = 0; c < k; c++) {
                        double dist = squaredDist(embedding[i], centroids[c]);
                        if (dist < minDist) {
                            minDist = dist;
                            labels[i] = c;
                        }
                    }
                    newCost += minDist;
                }

                // Check convergence
                if (Math.abs(cost - newCost) < EPSILON) break;
                cost = newCost;

                // Update centroids
                int[] counts = new int[k];
                double[][] sums = new double[k][embedding[0].length];
                for (int i = 0; i < n; i++) {
                    counts[labels[i]]++;
                    for (int d = 0; d < embedding[0].length; d++) {
                        sums[labels[i]][d] += embedding[i][d];
                    }
                }
                for (int c = 0; c < k; c++) {
                    if (counts[c] > 0) {
                        for (int d = 0; d < embedding[0].length; d++) {
                            centroids[c][d] = sums[c][d] / counts[c];
                        }
                    }
                }
            }

            if (cost < bestCost) {
                bestCost = cost;
                bestLabels = Arrays.copyOf(labels, n);
            }
        }

        Map<String, Integer> assignment = new HashMap<>();
        for (int i = 0; i < n; i++) {
            assignment.put(vertices.get(i), bestLabels != null ? bestLabels[i] : 0);
        }
        return assignment;
    }

    private double[][] kmeansPPInit(double[][] data, int k, int n) {
        double[][] centroids = new double[k][];
        boolean[] chosen = new boolean[n];

        // First centroid: random
        int first = rng.nextInt(n);
        centroids[0] = Arrays.copyOf(data[first], data[first].length);
        chosen[first] = true;

        double[] minDists = new double[n];
        Arrays.fill(minDists, Double.MAX_VALUE);

        for (int c = 1; c < k; c++) {
            // Update distances
            double totalDist = 0;
            for (int i = 0; i < n; i++) {
                if (!chosen[i]) {
                    double d = squaredDist(data[i], centroids[c - 1]);
                    minDists[i] = Math.min(minDists[i], d);
                    totalDist += minDists[i];
                }
            }

            // Weighted random selection
            if (totalDist < EPSILON) {
                // All points are at centroids; pick any unchosen
                for (int i = 0; i < n; i++) {
                    if (!chosen[i]) {
                        centroids[c] = Arrays.copyOf(data[i], data[i].length);
                        chosen[i] = true;
                        break;
                    }
                }
            } else {
                double r = rng.nextDouble() * totalDist;
                double cumulative = 0;
                int selected = 0;
                for (int i = 0; i < n; i++) {
                    if (!chosen[i]) {
                        cumulative += minDists[i];
                        if (cumulative >= r) {
                            selected = i;
                            break;
                        }
                    }
                }
                centroids[c] = Arrays.copyOf(data[selected], data[selected].length);
                chosen[selected] = true;
            }
        }
        return centroids;
    }

    private double squaredDist(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            sum += d * d;
        }
        return sum;
    }

    // ====================================================================
    // Engine 5: Conductance analysis
    // ====================================================================

    private List<ClusterInfo> buildClusterInfo(Graph<String, Edge> graph,
                                                Map<String, Integer> assignment,
                                                int k, List<String> vertices) {
        // Group vertices by cluster
        Map<Integer, List<String>> groups = new HashMap<>();
        for (int c = 0; c < k; c++) groups.put(c, new ArrayList<>());
        for (String v : vertices) {
            int c = assignment.getOrDefault(v, 0);
            groups.computeIfAbsent(c, x -> new ArrayList<>()).add(v);
        }

        List<ClusterInfo> clusters = new ArrayList<>();
        Set<String> allVertices = new HashSet<>(vertices);

        for (int c = 0; c < k; c++) {
            List<String> members = groups.getOrDefault(c, Collections.emptyList());
            if (members.isEmpty()) continue;

            Set<String> memberSet = new HashSet<>(members);
            int internal = 0, boundary = 0;
            List<String> boundaryNodes = new ArrayList<>();
            Set<String> boundarySet = new HashSet<>();

            for (String v : members) {
                boolean isBoundary = false;
                for (String neighbor : graph.getNeighbors(v)) {
                    if (memberSet.contains(neighbor)) {
                        internal++;
                    } else if (allVertices.contains(neighbor)) {
                        boundary++;
                        isBoundary = true;
                    }
                }
                if (isBoundary && !boundarySet.contains(v)) {
                    boundaryNodes.add(v);
                    boundarySet.add(v);
                }
            }
            internal /= 2; // each internal edge counted twice

            // Conductance = cut(S) / min(vol(S), vol(V\S))
            double volS = 0, volComplement = 0;
            for (String v : vertices) {
                int deg = graph.getNeighborCount(v);
                if (memberSet.contains(v)) volS += deg;
                else volComplement += deg;
            }
            double minVol = Math.min(volS, volComplement);
            double conductance = minVol > EPSILON ? (double) boundary / minVol : 0;

            clusters.add(new ClusterInfo(c, members, conductance,
                    internal, boundary, boundaryNodes));
        }

        return clusters;
    }

    private double computeGraphConductance(List<ClusterInfo> clusters) {
        if (clusters.isEmpty()) return 0;
        double minConductance = Double.MAX_VALUE;
        for (ClusterInfo ci : clusters) {
            if (ci.conductance < minConductance) {
                minConductance = ci.conductance;
            }
        }
        return minConductance == Double.MAX_VALUE ? 0 : minConductance;
    }

    // ====================================================================
    // Engine 6: Quality scoring
    // ====================================================================

    private double computeModularity(double[][] adj, double[] degrees,
                                      Map<String, Integer> assignment,
                                      List<String> vertices, int n) {
        double totalWeight = 0;
        for (int i = 0; i < n; i++) totalWeight += degrees[i];
        if (totalWeight < EPSILON) return 0;

        double Q = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (assignment.get(vertices.get(i)).equals(assignment.get(vertices.get(j)))) {
                    Q += adj[i][j] - degrees[i] * degrees[j] / totalWeight;
                }
            }
        }
        return Q / totalWeight;
    }

    private double computeNormalizedCut(double[][] adj, Map<String, Integer> assignment,
                                         List<String> vertices, int k, int n) {
        double ncut = 0;
        for (int c = 0; c < k; c++) {
            double cut = 0, vol = 0;
            for (int i = 0; i < n; i++) {
                boolean inC = assignment.get(vertices.get(i)).intValue() == c;
                if (inC) {
                    for (int j = 0; j < n; j++) vol += adj[i][j];
                    for (int j = 0; j < n; j++) {
                        if (assignment.get(vertices.get(j)).intValue() != c) {
                            cut += adj[i][j];
                        }
                    }
                }
            }
            if (vol > EPSILON) ncut += cut / vol;
        }
        return ncut;
    }

    private double computeAvgSilhouette(EigenDecomposition eigen,
                                          Map<String, Integer> assignment,
                                          List<String> vertices, int k, int n) {
        if (k <= 1 || n <= 1) return 1.0;

        // Use spectral embedding distances
        int dims = Math.min(k, eigen.eigenvectors.length);
        double[][] emb = new double[n][dims];
        for (int i = 0; i < n; i++) {
            for (int d = 0; d < dims; d++) {
                emb[i][d] = eigen.eigenvectors[d][i];
            }
        }

        double totalSil = 0;
        for (int i = 0; i < n; i++) {
            int ci = assignment.get(vertices.get(i));
            double[] avgDist = new double[k];
            int[] counts = new int[k];

            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                int cj = assignment.get(vertices.get(j));
                avgDist[cj] += Math.sqrt(squaredDist(emb[i], emb[j]));
                counts[cj]++;
            }

            for (int c = 0; c < k; c++) {
                if (counts[c] > 0) avgDist[c] /= counts[c];
            }

            double a = counts[ci] > 0 ? avgDist[ci] : 0;
            double b = Double.MAX_VALUE;
            for (int c = 0; c < k; c++) {
                if (c != ci && counts[c] > 0 && avgDist[c] < b) {
                    b = avgDist[c];
                }
            }
            if (b == Double.MAX_VALUE) b = 0;

            double sil = Math.max(a, b) > EPSILON ? (b - a) / Math.max(a, b) : 0;
            totalSil += sil;
        }

        return totalSil / n;
    }

    private double computeCompositeScore(double modularity, double ncut,
                                          double silhouette, double graphConductance,
                                          int numClusters) {
        // Modularity: ranges ~[-0.5, 1], map to [0,100]
        double modScore = Math.max(0, Math.min(100, (modularity + 0.5) / 1.5 * 100));

        // Ncut: lower is better; map [0, numClusters] to [100, 0]
        double ncutScore = Math.max(0, Math.min(100,
                100 * (1.0 - ncut / Math.max(1, numClusters))));

        // Silhouette: [-1, 1] -> [0, 100]
        double silScore = Math.max(0, Math.min(100, (silhouette + 1.0) / 2.0 * 100));

        // Low conductance = good separation -> [0, 100]
        double condScore = Math.max(0, Math.min(100, 100 * (1.0 - graphConductance)));

        return Math.max(0, Math.min(100,
                0.30 * modScore + 0.25 * ncutScore + 0.25 * silScore + 0.20 * condScore));
    }

    // ====================================================================
    // Engine 7: Insight generator
    // ====================================================================

    private List<SpectralInsight> generateInsights(EigengapResult eigengap,
                                                    BisectionResult bisection,
                                                    List<ClusterInfo> clusters,
                                                    double modularity, double ncut,
                                                    double silhouette, double composite,
                                                    int k, int n) {
        List<SpectralInsight> insights = new ArrayList<>();

        // Eigengap quality
        if (eigengap.maxGapRatio > 5.0) {
            insights.add(new SpectralInsight("STRONG_SEPARATION",
                    String.format("Strong spectral gap detected (ratio %.1f) — clusters are well-separated",
                            eigengap.maxGapRatio), InsightSeverity.INFO));
        } else if (eigengap.maxGapRatio < 1.5) {
            insights.add(new SpectralInsight("WEAK_SEPARATION",
                    "Weak spectral gap — community structure may be fuzzy or overlapping",
                    InsightSeverity.WARNING));
        }

        // Number of clusters
        if (k == 1) {
            insights.add(new SpectralInsight("MONOLITHIC",
                    "Graph has no clear community structure — it forms a single group",
                    InsightSeverity.INFO));
        } else if (k > n / 2 && n > 4) {
            insights.add(new SpectralInsight("FRAGMENTED",
                    String.format("High fragmentation: %d clusters for %d nodes", k, n),
                    InsightSeverity.WARNING));
        }

        // Balance assessment
        if (clusters.size() > 1) {
            int minSize = Integer.MAX_VALUE, maxSize = 0;
            for (ClusterInfo ci : clusters) {
                minSize = Math.min(minSize, ci.members.size());
                maxSize = Math.max(maxSize, ci.members.size());
            }
            if (maxSize > 3 * minSize && minSize > 0) {
                insights.add(new SpectralInsight("IMBALANCED",
                        String.format("Cluster sizes are imbalanced (min=%d, max=%d)", minSize, maxSize),
                        InsightSeverity.WARNING));
            } else {
                insights.add(new SpectralInsight("BALANCED",
                        "Cluster sizes are reasonably balanced", InsightSeverity.INFO));
            }
        }

        // Bridge nodes
        int totalBridge = 0;
        for (ClusterInfo ci : clusters) totalBridge += ci.boundaryNodes.size();
        if (totalBridge > 0) {
            insights.add(new SpectralInsight("BRIDGES",
                    String.format("%d bridge nodes found connecting clusters — key for information flow",
                            totalBridge), InsightSeverity.INFO));
        }

        // Modularity quality
        if (modularity > 0.5) {
            insights.add(new SpectralInsight("HIGH_MODULARITY",
                    String.format("Excellent modularity (Q=%.3f) — strong community structure", modularity),
                    InsightSeverity.INFO));
        } else if (modularity < 0.1 && k > 1) {
            insights.add(new SpectralInsight("LOW_MODULARITY",
                    String.format("Low modularity (Q=%.3f) — clusters may not be meaningful", modularity),
                    InsightSeverity.WARNING));
        }

        // Conductance
        for (ClusterInfo ci : clusters) {
            if (ci.conductance > 0.5) {
                insights.add(new SpectralInsight("LEAKY_CLUSTER",
                        String.format("Cluster %d has high conductance (%.3f) — poorly separated",
                                ci.clusterId, ci.conductance), InsightSeverity.WARNING));
            }
        }

        // Overall
        if (composite >= 75) {
            insights.add(new SpectralInsight("EXCELLENT_QUALITY",
                    String.format("Overall clustering quality is excellent (score %.0f/100)", composite),
                    InsightSeverity.INFO));
        } else if (composite < 40) {
            insights.add(new SpectralInsight("POOR_QUALITY",
                    String.format("Clustering quality is poor (score %.0f/100) — consider different k or preprocessing",
                            composite), InsightSeverity.CRITICAL));
        }

        // Silhouette
        if (silhouette > 0.7 && k > 1) {
            insights.add(new SpectralInsight("CLEAR_CLUSTERS",
                    String.format("High silhouette score (%.3f) — clusters are compact and well-separated",
                            silhouette), InsightSeverity.INFO));
        } else if (silhouette < 0.2 && k > 1) {
            insights.add(new SpectralInsight("OVERLAPPING_CLUSTERS",
                    String.format("Low silhouette score (%.3f) — significant cluster overlap",
                            silhouette), InsightSeverity.WARNING));
        }

        return insights;
    }

    // ====================================================================
    // Helper: build adjacency matrix
    // ====================================================================

    private double[][] buildAdjacencyMatrix(Graph<String, Edge> graph,
                                             List<String> vertices,
                                             Map<String, Integer> idxMap) {
        int n = vertices.size();
        double[][] adj = new double[n][n];
        for (Edge e : graph.getEdges()) {
            Integer ui = idxMap.get(e.getVertex1());
            Integer vi = idxMap.get(e.getVertex2());
            if (ui != null && vi != null && !ui.equals(vi)) {
                double w = e.getWeight() > 0 ? e.getWeight() : 1.0;
                adj[ui][vi] = w;
                adj[vi][ui] = w;
            }
        }
        return adj;
    }

    // ====================================================================
    // Text output
    // ====================================================================

    public String toText(SpectralClusteringReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════╗\n");
        sb.append("║       GRAPH SPECTRAL CLUSTERING ENGINE REPORT          ║\n");
        sb.append("╚══════════════════════════════════════════════════════════╝\n\n");

        sb.append(String.format("Vertices: %d  |  Edges: %d  |  Clusters: %d\n",
                report.vertexCount, report.edgeCount, report.kUsed));
        sb.append(String.format("Composite Quality Score: %.1f / 100\n\n", report.compositeScore));

        // Eigengap
        sb.append("── Eigengap Analysis ──\n");
        sb.append(String.format("Recommended k: %d  (max gap ratio: %.2f)\n",
                report.eigengap.recommendedK, report.eigengap.maxGapRatio));
        int showEv = Math.min(10, report.eigengap.eigenvalues.length);
        for (int i = 0; i < showEv; i++) {
            sb.append(String.format("  λ_%d = %.6f", i + 1, report.eigengap.eigenvalues[i]));
            if (i < report.eigengap.gaps.length) {
                sb.append(String.format("  (gap: %.6f)", report.eigengap.gaps[i]));
            }
            sb.append("\n");
        }
        sb.append("\n");

        // Bisection
        sb.append("── Fiedler Bisection ──\n");
        sb.append(String.format("Ncut value: %.4f  |  Cut edges: %.0f  |  Threshold: %.4f\n\n",
                report.bisection.ncutValue, report.bisection.cutEdges, report.bisection.threshold));

        // Clusters
        sb.append("── Cluster Details ──\n");
        for (ClusterInfo ci : report.clusters) {
            sb.append(String.format("Cluster %d: %d members, conductance=%.4f, " +
                            "internal=%d, boundary=%d\n",
                    ci.clusterId, ci.members.size(), ci.conductance,
                    ci.internalEdges, ci.boundaryEdges));
            sb.append(String.format("  Members: %s\n",
                    ci.members.size() <= 15 ? ci.members.toString() :
                            ci.members.subList(0, 15) + " ... +" + (ci.members.size() - 15)));
            if (!ci.boundaryNodes.isEmpty()) {
                sb.append(String.format("  Bridge nodes: %s\n", ci.boundaryNodes));
            }
        }
        sb.append("\n");

        // Quality
        sb.append("── Quality Metrics ──\n");
        sb.append(String.format("Modularity (Q):    %.4f\n", report.modularity));
        sb.append(String.format("Normalized cut:    %.4f\n", report.normalizedCut));
        sb.append(String.format("Avg silhouette:    %.4f\n", report.avgSilhouette));
        sb.append(String.format("Graph conductance: %.4f\n", report.graphConductance));
        sb.append(String.format("Composite score:   %.1f / 100\n\n", report.compositeScore));

        // Insights
        sb.append("── Autonomous Insights ──\n");
        for (SpectralInsight ins : report.insights) {
            String icon = ins.severity == InsightSeverity.CRITICAL ? "🔴" :
                    ins.severity == InsightSeverity.WARNING ? "🟡" : "🟢";
            sb.append(String.format("%s [%s] %s\n", icon, ins.type, ins.description));
        }

        return sb.toString();
    }

    // ====================================================================
    // HTML dashboard
    // ====================================================================

    public String exportHtml(SpectralClusteringReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'>");
        sb.append("<title>Spectral Clustering Report</title>");
        sb.append("<style>");
        sb.append("*{margin:0;padding:0;box-sizing:border-box}");
        sb.append("body{font-family:'Segoe UI',system-ui,sans-serif;background:#0f172a;color:#e2e8f0;padding:24px}");
        sb.append("h1{font-size:28px;text-align:center;margin-bottom:8px;color:#38bdf8}");
        sb.append("h2{font-size:18px;color:#94a3b8;margin:16px 0 8px}");
        sb.append(".cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:12px;margin:16px 0}");
        sb.append(".card{background:#1e293b;border-radius:12px;padding:16px;text-align:center}");
        sb.append(".card .val{font-size:32px;font-weight:700;color:#38bdf8}");
        sb.append(".card .lbl{font-size:12px;color:#94a3b8;margin-top:4px}");
        sb.append(".bar-chart{display:flex;align-items:flex-end;gap:3px;height:120px;margin:12px 0;padding:0 4px}");
        sb.append(".bar{background:#3b82f6;border-radius:3px 3px 0 0;min-width:6px;flex:1;position:relative;transition:background .2s}");
        sb.append(".bar:hover{background:#60a5fa}");
        sb.append(".bar .tip{display:none;position:absolute;bottom:100%;left:50%;transform:translateX(-50%);");
        sb.append("background:#334155;padding:4px 8px;border-radius:4px;font-size:11px;white-space:nowrap}");
        sb.append(".bar:hover .tip{display:block}");
        sb.append("table{width:100%;border-collapse:collapse;margin:12px 0}");
        sb.append("th,td{padding:8px 12px;text-align:left;border-bottom:1px solid #334155}");
        sb.append("th{color:#94a3b8;font-size:12px;text-transform:uppercase}");
        sb.append(".insight{padding:10px 14px;border-radius:8px;margin:6px 0;font-size:14px}");
        sb.append(".insight.INFO{background:#1e3a5f;border-left:4px solid #38bdf8}");
        sb.append(".insight.WARNING{background:#422006;border-left:4px solid #f59e0b}");
        sb.append(".insight.CRITICAL{background:#450a0a;border-left:4px solid #ef4444}");
        sb.append(".gauge{width:120px;height:120px;border-radius:50%;margin:12px auto;position:relative;");
        sb.append("background:conic-gradient(#38bdf8 calc(var(--pct)*3.6deg),#1e293b 0)}");
        sb.append(".gauge::after{content:attr(data-val);position:absolute;inset:15px;background:#0f172a;");
        sb.append("border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:24px;font-weight:700;color:#38bdf8}");
        sb.append(".sub{font-size:13px;text-align:center;color:#64748b;margin-bottom:16px}");
        sb.append("</style></head><body>");

        sb.append("<h1>🔮 Spectral Clustering Report</h1>");
        sb.append(String.format("<p class='sub'>%s — %d vertices, %d edges</p>",
                new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()),
                report.vertexCount, report.edgeCount));

        // Summary cards
        sb.append("<div class='cards'>");
        addCard(sb, String.valueOf(report.kUsed), "Clusters");
        addCard(sb, String.format("%.1f", report.compositeScore), "Quality Score");
        addCard(sb, String.format("%.3f", report.modularity), "Modularity");
        addCard(sb, String.format("%.3f", report.normalizedCut), "Normalized Cut");
        addCard(sb, String.format("%.3f", report.avgSilhouette), "Avg Silhouette");
        addCard(sb, String.format("%.3f", report.graphConductance), "Graph Conductance");
        sb.append("</div>");

        // Quality gauge
        sb.append("<div style='text-align:center'>");
        int pct = (int) Math.round(report.compositeScore);
        sb.append(String.format("<div class='gauge' style='--pct:%d' data-val='%d'></div>", pct, pct));
        sb.append("<div class='lbl'>Composite Quality</div></div>");

        // Eigenvalue spectrum bar chart
        sb.append("<h2>Eigenvalue Spectrum</h2>");
        sb.append("<div class='bar-chart'>");
        int showN = Math.min(20, report.eigengap.eigenvalues.length);
        double maxEv = 0;
        for (int i = 0; i < showN; i++) {
            maxEv = Math.max(maxEv, Math.abs(report.eigengap.eigenvalues[i]));
        }
        for (int i = 0; i < showN; i++) {
            double v = report.eigengap.eigenvalues[i];
            int h = maxEv > EPSILON ? (int) (Math.abs(v) / maxEv * 100) : 1;
            h = Math.max(2, h);
            String color = i < report.kUsed ? "#22c55e" : "#3b82f6";
            sb.append(String.format("<div class='bar' style='height:%d%%;background:%s'>", h, color));
            sb.append(String.format("<span class='tip'>λ_%d = %.4f</span></div>", i + 1, v));
        }
        sb.append("</div>");

        // Cluster table
        sb.append("<h2>Cluster Details</h2>");
        sb.append("<table><tr><th>ID</th><th>Size</th><th>Conductance</th>");
        sb.append("<th>Internal</th><th>Boundary</th><th>Bridge Nodes</th></tr>");
        for (ClusterInfo ci : report.clusters) {
            sb.append(String.format("<tr><td>%d</td><td>%d</td><td>%.4f</td><td>%d</td><td>%d</td><td>%d</td></tr>",
                    ci.clusterId, ci.members.size(), ci.conductance,
                    ci.internalEdges, ci.boundaryEdges, ci.boundaryNodes.size()));
        }
        sb.append("</table>");

        // Conductance bar chart
        if (!report.clusters.isEmpty()) {
            sb.append("<h2>Cluster Conductance</h2><div class='bar-chart'>");
            double maxCond = 0;
            for (ClusterInfo ci : report.clusters) maxCond = Math.max(maxCond, ci.conductance);
            for (ClusterInfo ci : report.clusters) {
                int h = maxCond > EPSILON ? (int) (ci.conductance / maxCond * 100) : 2;
                h = Math.max(2, h);
                String color = ci.conductance > 0.5 ? "#ef4444" :
                        ci.conductance > 0.3 ? "#f59e0b" : "#22c55e";
                sb.append(String.format("<div class='bar' style='height:%d%%;background:%s'>", h, color));
                sb.append(String.format("<span class='tip'>Cluster %d: %.4f</span></div>",
                        ci.clusterId, ci.conductance));
            }
            sb.append("</div>");
        }

        // Insights
        sb.append("<h2>Autonomous Insights</h2>");
        for (SpectralInsight ins : report.insights) {
            String icon = ins.severity == InsightSeverity.CRITICAL ? "🔴" :
                    ins.severity == InsightSeverity.WARNING ? "🟡" : "🟢";
            sb.append(String.format("<div class='insight %s'>%s <b>[%s]</b> %s</div>",
                    ins.severity, icon, ins.type, ins.description));
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private void addCard(StringBuilder sb, String val, String label) {
        sb.append(String.format("<div class='card'><div class='val'>%s</div><div class='lbl'>%s</div></div>",
                val, label));
    }

    /**
     * Exports the HTML report to a file.
     */
    public void exportHtmlToFile(SpectralClusteringReport report, File file) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write(exportHtml(report));
        }
    }
}
