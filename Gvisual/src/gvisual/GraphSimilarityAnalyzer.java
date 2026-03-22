package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Graph Similarity Analyzer — compares two graphs using entropy-based
 * and spectral similarity measures.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Jensen-Shannon Divergence (JSD)</b> — symmetric, bounded [0, 1]
 *       divergence between the degree distributions of two graphs.
 *       JSD = 0 means identical distributions; JSD = 1 means maximally
 *       different.</li>
 *   <li><b>Von Neumann Divergence</b> — compares the normalised Laplacian
 *       spectra of two graphs using the quantum relative entropy analogue.
 *       Measures how structurally different the graphs are at a spectral
 *       level.</li>
 *   <li><b>Entropy Profile Distance</b> — L2 (Euclidean) distance between
 *       normalised entropy vectors (degree entropy, Von Neumann entropy,
 *       neighbourhood entropy, edge type entropy, topological info content,
 *       random walk entropy rate). Captures multi-faceted structural
 *       differences.</li>
 *   <li><b>Similarity score</b> — combined [0, 1] similarity derived from
 *       all three measures (1 = identical, 0 = maximally different).</li>
 *   <li><b>Text report</b> — formatted multi-line comparison summary.</li>
 * </ul>
 *
 * <h3>Use Case</h3>
 * <p>Designed for temporal network analysis — comparing how student
 * communities evolve across months or semesters by measuring structural
 * similarity between graph snapshots.</p>
 *
 * @author zalenix
 * @see GraphEntropyAnalyzer
 */
public class GraphSimilarityAnalyzer {

    private static final double LOG2 = Math.log(2.0);
    private static final double EPSILON = 1e-12;
    private static final int JACOBI_MAX_SWEEPS = 100;

    private final Graph<String, Edge> graph1;
    private final Graph<String, Edge> graph2;
    private boolean computed;

    // ── Results ─────────────────────────────────────────────────────
    private double jensenShannonDivergence;
    private double vonNeumannDivergence;
    private double entropyProfileDistance;
    private double similarityScore;

    // Cached entropy profiles
    private double[] profile1;
    private double[] profile2;

    /**
     * Creates a new similarity analyzer for comparing two graphs.
     *
     * @param graph1 the first graph
     * @param graph2 the second graph
     * @throws NullPointerException if either graph is null
     */
    public GraphSimilarityAnalyzer(Graph<String, Edge> graph1,
                                    Graph<String, Edge> graph2) {
        this.graph1 = Objects.requireNonNull(graph1, "graph1 must not be null");
        this.graph2 = Objects.requireNonNull(graph2, "graph2 must not be null");
        this.computed = false;
    }

    // ── Public API ──────────────────────────────────────────────────

    /** Compute all similarity measures. */
    public void compute() {
        if (computed) return;
        computed = true;

        computeJensenShannonDivergence();
        computeVonNeumannDivergence();
        computeEntropyProfileDistance();
        computeSimilarityScore();
    }

    /**
     * Jensen-Shannon Divergence between degree distributions.
     * Bounded in [0, 1] (using log base 2).
     */
    public double getJensenShannonDivergence() {
        ensureComputed();
        return jensenShannonDivergence;
    }

    /**
     * Von Neumann Divergence between normalised Laplacian spectra.
     * Non-negative; 0 means identical spectral structure.
     */
    public double getVonNeumannDivergence() {
        ensureComputed();
        return vonNeumannDivergence;
    }

    /**
     * L2 distance between normalised entropy profile vectors.
     * Non-negative; 0 means identical entropy profiles.
     */
    public double getEntropyProfileDistance() {
        ensureComputed();
        return entropyProfileDistance;
    }

    /**
     * Combined similarity score in [0, 1].
     * 1 = identical graphs, 0 = maximally different.
     */
    public double getSimilarityScore() {
        ensureComputed();
        return similarityScore;
    }

    /**
     * Returns the entropy profile vector for graph1.
     * Components: [degreeEntropy, vonNeumannEntropy, avgNeighbourhoodEntropy,
     *              edgeTypeEntropy, topologicalInfoContent, randomWalkEntropyRate]
     */
    public double[] getProfile1() {
        ensureComputed();
        return profile1.clone();
    }

    /**
     * Returns the entropy profile vector for graph2.
     */
    public double[] getProfile2() {
        ensureComputed();
        return profile2.clone();
    }

    // ── Computation ─────────────────────────────────────────────────

    /**
     * Jensen-Shannon Divergence of the degree distributions.
     *
     * JSD(P||Q) = 0.5 * KL(P||M) + 0.5 * KL(Q||M)
     * where M = 0.5 * (P + Q) and KL is the Kullback-Leibler divergence.
     *
     * We use log base 2 so JSD ∈ [0, 1].
     */
    private void computeJensenShannonDivergence() {
        Map<Integer, Double> p = getDegreeDistribution(graph1);
        Map<Integer, Double> q = getDegreeDistribution(graph2);

        if (p.isEmpty() && q.isEmpty()) {
            jensenShannonDivergence = 0;
            return;
        }

        // Union of all degree values
        Set<Integer> allDegrees = new HashSet<>(p.keySet());
        allDegrees.addAll(q.keySet());

        // Compute M = 0.5 * (P + Q)
        Map<Integer, Double> m = new HashMap<>();
        for (int d : allDegrees) {
            double pVal = p.containsKey(d) ? p.get(d) : 0.0;
            double qVal = q.containsKey(d) ? q.get(d) : 0.0;
            m.put(d, 0.5 * (pVal + qVal));
        }

        // JSD = 0.5 * KL(P||M) + 0.5 * KL(Q||M)
        double klPM = 0;
        for (int d : p.keySet()) {
            double pVal = p.get(d);
            double mVal = m.get(d);
            if (pVal > EPSILON && mVal > EPSILON) {
                klPM += pVal * log2(pVal / mVal);
            }
        }

        double klQM = 0;
        for (int d : q.keySet()) {
            double qVal = q.get(d);
            double mVal = m.get(d);
            if (qVal > EPSILON && mVal > EPSILON) {
                klQM += qVal * log2(qVal / mVal);
            }
        }

        jensenShannonDivergence = Math.max(0, Math.min(1, 0.5 * klPM + 0.5 * klQM));
    }

    /**
     * Von Neumann Divergence between the density matrices derived from
     * the normalised Laplacian spectra.
     *
     * Given eigenvalues λ_i of the Laplacian, the density matrix ρ has
     * eigenvalues p_i = λ_i / Σλ. The Von Neumann divergence is then:
     * S(ρ₁||ρ₂) = tr(ρ₁(log ρ₁ - log ρ₂))
     *
     * We use the symmetrised version: 0.5 * (S(ρ₁||ρ₂) + S(ρ₂||ρ₁))
     */
    private void computeVonNeumannDivergence() {
        double[] eig1 = getLaplacianEigenvalues(graph1);
        double[] eig2 = getLaplacianEigenvalues(graph2);

        double[] p1 = normaliseSpectrum(eig1);
        double[] p2 = normaliseSpectrum(eig2);

        if (p1.length == 0 && p2.length == 0) {
            vonNeumannDivergence = 0;
            return;
        }

        // Pad to same length (extra eigenvalues treated as 0)
        int maxLen = Math.max(p1.length, p2.length);
        double[] pp1 = padArray(p1, maxLen);
        double[] pp2 = padArray(p2, maxLen);

        // Re-normalise after padding
        pp1 = renormalise(pp1);
        pp2 = renormalise(pp2);

        // Symmetrised KL divergence on the spectral distributions
        double kl12 = klDivergence(pp1, pp2);
        double kl21 = klDivergence(pp2, pp1);

        vonNeumannDivergence = 0.5 * (kl12 + kl21);
    }

    /**
     * Entropy profile distance — L2 distance between normalised entropy
     * vectors of the two graphs.
     *
     * Each graph's entropy profile is:
     * [degreeEntropy, vonNeumannEntropy, avgNeighbourhoodEntropy,
     *  edgeTypeEntropy, topologicalInfoContent, randomWalkEntropyRate]
     *
     * Each component is normalised by log2(max(n1, n2)) to make them
     * comparable across differently-sized graphs.
     */
    private void computeEntropyProfileDistance() {
        GraphEntropyAnalyzer ea1 = new GraphEntropyAnalyzer(graph1);
        ea1.compute();
        GraphEntropyAnalyzer ea2 = new GraphEntropyAnalyzer(graph2);
        ea2.compute();

        int maxN = Math.max(graph1.getVertexCount(), graph2.getVertexCount());
        double normFactor = maxN > 1 ? log2(maxN) : 1.0;

        profile1 = new double[]{
            ea1.getDegreeEntropy() / normFactor,
            ea1.getVonNeumannEntropy() / normFactor,
            ea1.getAvgNeighbourhoodEntropy() / normFactor,
            ea1.getEdgeTypeEntropy() / normFactor,
            ea1.getTopologicalInfoContent() / normFactor,
            ea1.getRandomWalkEntropyRate() / normFactor
        };

        profile2 = new double[]{
            ea2.getDegreeEntropy() / normFactor,
            ea2.getVonNeumannEntropy() / normFactor,
            ea2.getAvgNeighbourhoodEntropy() / normFactor,
            ea2.getEdgeTypeEntropy() / normFactor,
            ea2.getTopologicalInfoContent() / normFactor,
            ea2.getRandomWalkEntropyRate() / normFactor
        };

        double sumSq = 0;
        for (int i = 0; i < profile1.length; i++) {
            double diff = profile1[i] - profile2[i];
            sumSq += diff * diff;
        }
        entropyProfileDistance = Math.sqrt(sumSq);
    }

    /**
     * Combined similarity score from all three measures.
     * Maps each measure to [0, 1] similarity and averages.
     */
    private void computeSimilarityScore() {
        // JSD is already in [0, 1]
        double jsdSim = 1.0 - jensenShannonDivergence;

        // Von Neumann: use exponential decay for unbounded measure
        double vnSim = Math.exp(-vonNeumannDivergence);

        // Entropy profile: use exponential decay
        double epSim = Math.exp(-entropyProfileDistance);

        similarityScore = (jsdSim + vnSim + epSim) / 3.0;
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private void ensureComputed() {
        if (!computed) compute();
    }

    /**
     * Gets the degree probability distribution for a graph.
     */
    private Map<Integer, Double> getDegreeDistribution(Graph<String, Edge> g) {
        Map<Integer, Integer> freq = new HashMap<>();
        int n = g.getVertexCount();
        if (n == 0) return new HashMap<>();

        for (String v : g.getVertices()) {
            int d = g.degree(v);
            Integer old = freq.get(d);
            freq.put(d, old == null ? 1 : old + 1);
        }

        Map<Integer, Double> dist = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : freq.entrySet()) {
            dist.put(entry.getKey(), (double) entry.getValue() / n);
        }
        return dist;
    }

    /**
     * Gets sorted Laplacian eigenvalues for a graph.
     */
    private double[] getLaplacianEigenvalues(Graph<String, Edge> g) {
        int n = g.getVertexCount();
        if (n <= 1) return new double[0];

        List<String> vList = new ArrayList<>(g.getVertices());
        Collections.sort(vList);
        double[][] L = LaplacianBuilder.buildLaplacian(g, vList);
        return computeEigenvalues(L);
    }

    /**
     * Normalises eigenvalues to a probability distribution (non-zero only).
     */
    private double[] normaliseSpectrum(double[] eigenvalues) {
        List<Double> nonZero = new ArrayList<>();
        double sum = 0;
        for (double ev : eigenvalues) {
            if (ev > EPSILON) {
                nonZero.add(ev);
                sum += ev;
            }
        }
        if (sum < EPSILON) return new double[0];

        double[] result = new double[nonZero.size()];
        for (int i = 0; i < nonZero.size(); i++) {
            result[i] = nonZero.get(i) / sum;
        }
        return result;
    }

    private double[] padArray(double[] arr, int length) {
        if (arr.length >= length) return arr.clone();
        double[] result = new double[length];
        System.arraycopy(arr, 0, result, 0, arr.length);
        return result;
    }

    private double[] renormalise(double[] arr) {
        double sum = 0;
        for (double v : arr) sum += v;
        if (sum < EPSILON) return arr;
        double[] result = new double[arr.length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = arr[i] / sum;
        }
        return result;
    }

    /**
     * KL divergence: KL(P||Q) = Σ p_i * log2(p_i / q_i)
     * Skips entries where p_i = 0. Uses smoothing for q_i = 0.
     */
    private double klDivergence(double[] p, double[] q) {
        double kl = 0;
        double smoothing = EPSILON;
        for (int i = 0; i < p.length; i++) {
            if (p[i] > EPSILON) {
                double qi = Math.max(q[i], smoothing);
                kl += p[i] * log2(p[i] / qi);
            }
        }
        return Math.max(0, kl);
    }

    private static double log2(double x) {
        return x <= 0 ? 0 : Math.log(x) / LOG2;
    }

    /**
     * Jacobi eigenvalue algorithm for symmetric matrices.
     */
    private double[] computeEigenvalues(double[][] matrix) {
        int n = matrix.length;
        if (n == 0) return new double[0];

        double[][] A = new double[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(matrix[i], 0, A[i], 0, n);
        }

        for (int sweep = 0; sweep < JACOBI_MAX_SWEEPS; sweep++) {
            double offDiag = 0;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    offDiag += A[i][j] * A[i][j];
                }
            }
            if (offDiag < EPSILON) break;

            for (int p = 0; p < n; p++) {
                for (int q = p + 1; q < n; q++) {
                    if (Math.abs(A[p][q]) < EPSILON / (n * n)) continue;

                    double tau = (A[q][q] - A[p][p]) / (2 * A[p][q]);
                    double t;
                    if (Math.abs(tau) > 1e15) {
                        t = 1.0 / (2.0 * tau);
                    } else if (Math.abs(tau) < EPSILON) {
                        t = 1.0;
                    } else {
                        t = Math.signum(tau) / (Math.abs(tau) + Math.sqrt(1 + tau * tau));
                    }
                    double c = 1.0 / Math.sqrt(1 + t * t);
                    double s = t * c;

                    double app = A[p][p] - t * A[p][q];
                    double aqq = A[q][q] + t * A[p][q];
                    A[p][q] = 0;
                    A[q][p] = 0;
                    A[p][p] = app;
                    A[q][q] = aqq;

                    for (int r = 0; r < n; r++) {
                        if (r == p || r == q) continue;
                        double arp = A[r][p];
                        double arq = A[r][q];
                        A[r][p] = c * arp - s * arq;
                        A[p][r] = A[r][p];
                        A[r][q] = s * arp + c * arq;
                        A[q][r] = A[r][q];
                    }
                }
            }
        }

        double[] eigenvalues = new double[n];
        for (int i = 0; i < n; i++) {
            eigenvalues[i] = A[i][i];
        }
        Arrays.sort(eigenvalues);
        return eigenvalues;
    }

    // ── Report ──────────────────────────────────────────────────────

    /**
     * Generate a formatted text report of all similarity measures.
     */
    public String generateReport() {
        ensureComputed();

        int n1 = graph1.getVertexCount();
        int m1 = graph1.getEdgeCount();
        int n2 = graph2.getVertexCount();
        int m2 = graph2.getEdgeCount();

        StringBuilder sb = new StringBuilder();
        sb.append("=== Graph Similarity Analysis ===\n\n");
        sb.append(String.format("Graph 1: %d vertices, %d edges\n", n1, m1));
        sb.append(String.format("Graph 2: %d vertices, %d edges\n\n", n2, m2));

        sb.append("── Divergence Measures ──\n");
        sb.append(String.format("  Jensen-Shannon Divergence:    %.6f", jensenShannonDivergence));
        sb.append(jensenShannonDivergence < 0.1 ? "  (very similar degree distributions)\n" :
                  jensenShannonDivergence < 0.3 ? "  (moderately similar)\n" :
                  "  (substantially different)\n");
        sb.append(String.format("  Von Neumann Divergence:       %.6f", vonNeumannDivergence));
        sb.append(vonNeumannDivergence < 0.5 ? "  (spectrally similar)\n" :
                  vonNeumannDivergence < 2.0 ? "  (moderate spectral difference)\n" :
                  "  (spectrally very different)\n");
        sb.append(String.format("  Entropy Profile Distance:     %.6f", entropyProfileDistance));
        sb.append(entropyProfileDistance < 0.2 ? "  (nearly identical profiles)\n" :
                  entropyProfileDistance < 0.5 ? "  (moderate profile difference)\n" :
                  "  (very different profiles)\n");
        sb.append("\n");

        sb.append("── Similarity Score ──\n");
        sb.append(String.format("  Combined similarity:  %.4f / 1.0", similarityScore));
        sb.append(similarityScore > 0.9 ? "  (nearly identical)\n" :
                  similarityScore > 0.7 ? "  (highly similar)\n" :
                  similarityScore > 0.5 ? "  (moderately similar)\n" :
                  similarityScore > 0.3 ? "  (somewhat different)\n" :
                  "  (very different)\n");
        sb.append("\n");

        sb.append("── Entropy Profiles ──\n");
        String[] labels = {"Degree Entropy", "Von Neumann Entropy",
                           "Avg Neighbourhood Entropy", "Edge Type Entropy",
                           "Topological Info Content", "Random Walk Entropy Rate"};
        sb.append(String.format("  %-28s  %8s  %8s  %8s\n",
                "Measure", "Graph 1", "Graph 2", "Δ"));
        for (int i = 0; i < labels.length; i++) {
            double delta = Math.abs(profile1[i] - profile2[i]);
            sb.append(String.format("  %-28s  %8.4f  %8.4f  %8.4f\n",
                    labels[i], profile1[i], profile2[i], delta));
        }
        sb.append("\n");

        sb.append("── Interpretation ──\n");
        if (similarityScore > 0.9) {
            sb.append("  These graphs have very similar structural properties.\n");
            sb.append("  They likely represent the same type of network or\n");
            sb.append("  a network that has changed minimally over time.\n");
        } else if (similarityScore > 0.6) {
            sb.append("  These graphs share some structural similarities but\n");
            sb.append("  have notable differences. This could indicate moderate\n");
            sb.append("  evolution of a network over time.\n");
        } else {
            sb.append("  These graphs are structurally quite different.\n");
            sb.append("  They likely represent different types of networks or\n");
            sb.append("  a network that has undergone major structural changes.\n");
        }

        return sb.toString();
    }
}
