package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Graph Entropy Analyzer — information-theoretic measures for graphs.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Degree distribution entropy</b> — Shannon entropy of the degree
 *       frequency distribution.  Higher values indicate more heterogeneous
 *       degree structure (scale-free); lower values suggest regularity.</li>
 *   <li><b>Von Neumann entropy</b> — quantum-inspired entropy computed from
 *       the normalised Laplacian eigenvalues.  Quantifies structural
 *       complexity; a complete graph has maximum entropy.</li>
 *   <li><b>Neighbourhood entropy</b> — per-vertex Shannon entropy over the
 *       set of neighbour degree values.  Captures local structural
 *       diversity around each node.</li>
 *   <li><b>Edge type entropy</b> — Shannon entropy over the distribution of
 *       edge categories (friend, classmate, etc.).  Measures diversity
 *       of relationship types in the network.</li>
 *   <li><b>Topological information content</b> — based on degree-sequence
 *       equivalence classes (orbits under automorphism approximation).
 *       Measures how much information is needed to distinguish nodes.</li>
 *   <li><b>Random walk entropy rate</b> — the asymptotic entropy per step
 *       of a random walker on the graph, H = log2(2m) - (1/2m) Σ d(v) log2 d(v),
 *       where d(v) is the degree of vertex v and m is the edge count.</li>
 *   <li><b>Chromatic entropy</b> — entropy based on vertex coloring using
 *       greedy coloring (largest-first), measuring color distribution
 *       uniformity.</li>
 *   <li><b>Mutual information</b> — between the degree and local clustering
 *       coefficient of vertices, revealing how much structural info
 *       degree carries about clustering.</li>
 *   <li><b>Graph complexity classification</b> — categorises the graph as
 *       low/moderate/high/very high complexity based on combined entropy
 *       measures.</li>
 *   <li><b>Text report</b> — formatted multi-line summary of all metrics.</li>
 * </ul>
 *
 * <h3>Theory</h3>
 * <p>Graph entropy measures are widely used in network science, chemistry,
 * and computational biology.  They quantify structural complexity,
 * randomness, and information content of graph topology.  This analyser
 * implements multiple complementary measures to give a holistic view of
 * a graph's information-theoretic properties.</p>
 *
 * @author zalenix
 */
public class GraphEntropyAnalyzer {

    private static final double LOG2 = Math.log(2.0);
    private static final double EPSILON = 1e-12;
    private static final int JACOBI_MAX_SWEEPS = 100;

    private final Graph<String, Edge> graph;
    private boolean computed;

    // ── Results ─────────────────────────────────────────────────────
    private double degreeEntropy;
    private double vonNeumannEntropy;
    private double edgeTypeEntropy;
    private double topologicalInfoContent;
    private double randomWalkEntropyRate;
    private double chromaticEntropy;
    private double degreeCCMutualInfo;
    private Map<String, Double> neighbourhoodEntropy;  // per vertex
    private double avgNeighbourhoodEntropy;
    private String complexityClass;

    public GraphEntropyAnalyzer(Graph<String, Edge> graph) {
        this.graph = Objects.requireNonNull(graph, "graph must not be null");
        this.computed = false;
        this.neighbourhoodEntropy = new LinkedHashMap<>();
    }

    // ── Public API ──────────────────────────────────────────────────

    /** Compute all entropy measures. */
    public void compute() {
        if (computed) return;
        computed = true;

        int n = graph.getVertexCount();
        if (n == 0) {
            degreeEntropy = 0;
            vonNeumannEntropy = 0;
            edgeTypeEntropy = 0;
            topologicalInfoContent = 0;
            randomWalkEntropyRate = 0;
            chromaticEntropy = 0;
            avgNeighbourhoodEntropy = 0;
            degreeCCMutualInfo = 0;
            complexityClass = "trivial";
            return;
        }

        computeDegreeEntropy();
        computeVonNeumannEntropy();
        computeNeighbourhoodEntropy();
        computeEdgeTypeEntropy();
        computeTopologicalInfoContent();
        computeRandomWalkEntropyRate();
        computeChromaticEntropy();
        computeDegreeCCMutualInfo();
        classifyComplexity();
    }

    public double getDegreeEntropy() { ensureComputed(); return degreeEntropy; }
    public double getVonNeumannEntropy() { ensureComputed(); return vonNeumannEntropy; }
    public double getEdgeTypeEntropy() { ensureComputed(); return edgeTypeEntropy; }
    public double getTopologicalInfoContent() { ensureComputed(); return topologicalInfoContent; }
    public double getRandomWalkEntropyRate() { ensureComputed(); return randomWalkEntropyRate; }
    public double getChromaticEntropy() { ensureComputed(); return chromaticEntropy; }
    public double getDegreeCCMutualInfo() { ensureComputed(); return degreeCCMutualInfo; }
    public Map<String, Double> getNeighbourhoodEntropy() { ensureComputed(); return Collections.unmodifiableMap(neighbourhoodEntropy); }
    public double getAvgNeighbourhoodEntropy() { ensureComputed(); return avgNeighbourhoodEntropy; }
    public String getComplexityClass() { ensureComputed(); return complexityClass; }

    /**
     * Returns the maximum possible degree entropy for a graph with the
     * same number of vertices. This is log2(n) for n distinct degrees.
     */
    public double getMaxDegreeEntropy() {
        ensureComputed();
        int n = graph.getVertexCount();
        return n <= 1 ? 0 : log2(n);
    }

    /**
     * Normalised degree entropy in [0, 1].
     */
    public double getNormalisedDegreeEntropy() {
        ensureComputed();
        double max = getMaxDegreeEntropy();
        return max > 0 ? degreeEntropy / max : 0;
    }

    /**
     * Returns the vertex with the highest neighbourhood entropy.
     */
    public String getMostDiverseVertex() {
        ensureComputed();
        String best = null;
        double bestVal = -1;
        for (Map.Entry<String, Double> entry : neighbourhoodEntropy.entrySet()) {
            if (entry.getValue() > bestVal) {
                bestVal = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    /**
     * Returns the vertex with the lowest neighbourhood entropy.
     */
    public String getLeastDiverseVertex() {
        ensureComputed();
        String best = null;
        double bestVal = Double.MAX_VALUE;
        for (Map.Entry<String, Double> entry : neighbourhoodEntropy.entrySet()) {
            if (entry.getValue() < bestVal) {
                bestVal = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    // ── Computation ─────────────────────────────────────────────────

    /**
     * Shannon entropy of the degree distribution.
     * H = -Σ p(k) log2(p(k)) where p(k) = count(degree=k) / n
     */
    private void computeDegreeEntropy() {
        int n = graph.getVertexCount();
        if (n == 0) { degreeEntropy = 0; return; }

        Map<Integer, Integer> freq = new HashMap<>();
        for (String v : graph.getVertices()) {
            int d = graph.degree(v);
            Integer old = freq.get(d);
            freq.put(d, old == null ? 1 : old + 1);
        }
        degreeEntropy = shannonEntropy(freq.values(), n);
    }

    /**
     * Von Neumann entropy from the normalised Laplacian.
     * S = -Σ (λ_i/Σλ) log2(λ_i/Σλ) for non-zero eigenvalues.
     */
    private void computeVonNeumannEntropy() {
        int n = graph.getVertexCount();
        if (n <= 1) { vonNeumannEntropy = 0; return; }

        double[][] L = buildLaplacian();
        double[] eigenvalues = computeEigenvalues(L);

        double sumEig = 0;
        for (double ev : eigenvalues) {
            if (ev > EPSILON) sumEig += ev;
        }
        if (sumEig < EPSILON) { vonNeumannEntropy = 0; return; }

        double h = 0;
        for (double ev : eigenvalues) {
            if (ev > EPSILON) {
                double p = ev / sumEig;
                h -= p * log2(p);
            }
        }
        vonNeumannEntropy = h;
    }

    /**
     * Per-vertex neighbourhood entropy.
     * For each vertex v, compute Shannon entropy over the multiset of
     * neighbour degrees.
     */
    private void computeNeighbourhoodEntropy() {
        int n = graph.getVertexCount();
        if (n == 0) { avgNeighbourhoodEntropy = 0; return; }

        double sum = 0;
        for (String v : graph.getVertices()) {
            Collection<String> nbrs = GraphUtils.neighborsOf(graph, v);
            if (nbrs == null || nbrs.isEmpty()) {
                neighbourhoodEntropy.put(v, 0.0);
                continue;
            }
            Map<Integer, Integer> degFreq = new HashMap<>();
            for (String u : nbrs) {
                int d = graph.degree(u);
                Integer old = degFreq.get(d);
                degFreq.put(d, old == null ? 1 : old + 1);
            }
            double h = shannonEntropy(degFreq.values(), nbrs.size());
            neighbourhoodEntropy.put(v, h);
            sum += h;
        }
        avgNeighbourhoodEntropy = sum / n;
    }

    /**
     * Shannon entropy over edge type distribution.
     */
    private void computeEdgeTypeEntropy() {
        int m = graph.getEdgeCount();
        if (m == 0) { edgeTypeEntropy = 0; return; }

        Map<String, Integer> freq = new HashMap<>();
        for (Edge e : graph.getEdges()) {
            String type = e.getType();
            if (type == null) type = "unknown";
            Integer old = freq.get(type);
            freq.put(type, old == null ? 1 : old + 1);
        }
        edgeTypeEntropy = shannonEntropy(freq.values(), m);
    }

    /**
     * Topological information content based on degree-equivalence classes.
     * Vertices with the same degree are assumed to be in the same orbit
     * (exact for many common graphs; a practical approximation otherwise).
     * I = log2(n!) - Σ log2(|orbit_k|!)
     * Normalised to bits.
     */
    private void computeTopologicalInfoContent() {
        int n = graph.getVertexCount();
        if (n <= 1) { topologicalInfoContent = 0; return; }

        Map<Integer, Integer> degreeClasses = new HashMap<>();
        for (String v : graph.getVertices()) {
            int d = graph.degree(v);
            Integer old = degreeClasses.get(d);
            degreeClasses.put(d, old == null ? 1 : old + 1);
        }

        double logNFact = logFactorial(n);
        double sumLogClassFact = 0;
        for (int size : degreeClasses.values()) {
            sumLogClassFact += logFactorial(size);
        }
        topologicalInfoContent = (logNFact - sumLogClassFact) / LOG2;
    }

    /**
     * Random walk entropy rate.
     * For an undirected graph: H = log2(2m) - (1/(2m)) Σ_v d(v) log2(d(v))
     * This is the entropy rate of the stationary random walk.
     */
    private void computeRandomWalkEntropyRate() {
        int m = graph.getEdgeCount();
        if (m == 0) { randomWalkEntropyRate = 0; return; }

        double twoM = 2.0 * m;
        double sumDlogD = 0;
        for (String v : graph.getVertices()) {
            int d = graph.degree(v);
            if (d > 0) {
                sumDlogD += d * log2(d);
            }
        }
        randomWalkEntropyRate = log2(twoM) - sumDlogD / twoM;
    }

    /**
     * Chromatic entropy — entropy of the color distribution from greedy
     * coloring (largest-first ordering).
     */
    private void computeChromaticEntropy() {
        int n = graph.getVertexCount();
        if (n == 0) { chromaticEntropy = 0; return; }

        // Greedy coloring with largest-first ordering
        List<String> vertices = new ArrayList<>(graph.getVertices());
        Collections.sort(vertices, (String a, String b) -> {
                return Integer.compare(graph.degree(b), graph.degree(a));
            });

        Map<String, Integer> colors = new HashMap<>();
        for (String v : vertices) {
            Set<Integer> usedColors = new HashSet<>();
            Collection<String> nbrs = GraphUtils.neighborsOf(graph, v);
            if (nbrs != null) {
                for (String u : nbrs) {
                    Integer c = colors.get(u);
                    if (c != null) usedColors.add(c);
                }
            }
            int color = 0;
            while (usedColors.contains(color)) color++;
            colors.put(v, color);
        }

        // Count vertices per color
        Map<Integer, Integer> colorFreq = new HashMap<>();
        for (int c : colors.values()) {
            Integer old = colorFreq.get(c);
            colorFreq.put(c, old == null ? 1 : old + 1);
        }
        chromaticEntropy = shannonEntropy(colorFreq.values(), n);
    }

    /**
     * Mutual information between degree and local clustering coefficient.
     * Discretises clustering coefficient into bins, then computes:
     * I(X;Y) = H(X) + H(Y) - H(X,Y)
     */
    private void computeDegreeCCMutualInfo() {
        int n = graph.getVertexCount();
        if (n <= 1) { degreeCCMutualInfo = 0; return; }

        Map<String, Double> cc = new HashMap<>();
        for (String v : graph.getVertices()) {
            cc.put(v, localClusteringCoefficient(v));
        }

        int numBins = 10;
        Map<Integer, Integer> degFreq = new HashMap<>();
        Map<Integer, Integer> ccBinFreq = new HashMap<>();
        Map<String, Integer> jointFreq = new HashMap<>();

        for (String v : graph.getVertices()) {
            int d = graph.degree(v);
            double c = cc.get(v);
            int bin = Math.min((int) (c * numBins), numBins - 1);

            Integer oldD = degFreq.get(d);
            degFreq.put(d, oldD == null ? 1 : oldD + 1);
            Integer oldB = ccBinFreq.get(bin);
            ccBinFreq.put(bin, oldB == null ? 1 : oldB + 1);

            String key = d + "," + bin;
            Integer oldJ = jointFreq.get(key);
            jointFreq.put(key, oldJ == null ? 1 : oldJ + 1);
        }

        double hDeg = shannonEntropy(degFreq.values(), n);
        double hCC = shannonEntropy(ccBinFreq.values(), n);
        double hJoint = shannonEntropy(jointFreq.values(), n);

        degreeCCMutualInfo = Math.max(0, hDeg + hCC - hJoint);
    }

    /**
     * Classify the graph's structural complexity based on entropy measures.
     */
    private void classifyComplexity() {
        int n = graph.getVertexCount();
        if (n == 0) { complexityClass = "trivial"; return; }
        if (n == 1) { complexityClass = "trivial"; return; }

        double normDegEnt = getNormalisedDegreeEntropy();
        double maxVN = log2(n);
        double normVN = maxVN > 0 ? vonNeumannEntropy / maxVN : 0;

        double maxNbr = log2(n);
        double normNbr = maxNbr > 0 ? avgNeighbourhoodEntropy / maxNbr : 0;
        double score = (normDegEnt + normVN + normNbr) / 3.0;

        if (score < 0.15) {
            complexityClass = "low";
        } else if (score < 0.40) {
            complexityClass = "moderate";
        } else if (score < 0.70) {
            complexityClass = "high";
        } else {
            complexityClass = "very high";
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private void ensureComputed() {
        if (!computed) compute();
    }

    /**
     * Shannon entropy: H = -Σ (count/total) log2(count/total)
     */
    private static double shannonEntropy(Collection<Integer> counts, int total) {
        if (total <= 0) return 0;
        double h = 0;
        for (int c : counts) {
            if (c > 0) {
                double p = (double) c / total;
                h -= p * log2(p);
            }
        }
        return h;
    }

    private static double log2(double x) {
        return x <= 0 ? 0 : Math.log(x) / LOG2;
    }

    /** log(n!) computed exactly. */
    private static double logFactorial(int n) {
        if (n <= 1) return 0;
        double sum = 0;
        for (int i = 2; i <= n; i++) {
            sum += Math.log(i);
        }
        return sum;
    }

    /**
     * Local clustering coefficient for vertex v.
     * C(v) = 2T / (d(v)(d(v)-1)) where T is the number of triangles.
     */
    private double localClusteringCoefficient(String v) {
        Collection<String> nbrs = GraphUtils.neighborsOf(graph, v);
        List<String> nbrList = new ArrayList<>(nbrs);
        int d = nbrList.size();
        if (d < 2) return 0;

        int triangles = 0;
        Set<String> nbrSet = new HashSet<>(nbrList);
        for (int i = 0; i < nbrList.size(); i++) {
            for (int j = i + 1; j < nbrList.size(); j++) {
                if (graph.findEdge(nbrList.get(i), nbrList.get(j)) != null) {
                    triangles++;
                }
            }
        }
        return (2.0 * triangles) / (d * (d - 1));
    }

    // ── Laplacian + Eigenvalues (Jacobi) ────────────────────────────

    private double[][] buildLaplacian() {
        List<String> vList = new ArrayList<>(graph.getVertices());
        Collections.sort(vList);
        int n = vList.size();
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < n; i++) idx.put(vList.get(i), i);

        double[][] L = new double[n][n];
        for (Edge e : graph.getEdges()) {
            String v1 = graph.getEndpoints(e).getFirst();
            String v2 = graph.getEndpoints(e).getSecond();
            int i = idx.get(v1);
            int j = idx.get(v2);
            if (i != j) {
                L[i][j] -= 1;
                L[j][i] -= 1;
                L[i][i] += 1;
                L[j][j] += 1;
            }
        }
        return L;
    }

    /**
     * Jacobi eigenvalue algorithm for symmetric matrices.
     * Returns eigenvalues sorted ascending.
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
                        // tau ≈ 0 means diagonal elements are equal; use 45° rotation
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
     * Generate a formatted text report of all entropy measures.
     */
    public String generateReport() {
        ensureComputed();
        int n = graph.getVertexCount();
        int m = graph.getEdgeCount();

        StringBuilder sb = new StringBuilder();
        sb.append("=== Graph Entropy Analysis ===\n\n");
        sb.append(String.format("Graph: %d vertices, %d edges\n\n", n, m));

        sb.append("── Global Entropy Measures ──\n");
        sb.append(String.format("  Degree distribution entropy:  %.4f bits\n", degreeEntropy));
        sb.append(String.format("  Max degree entropy:           %.4f bits\n", getMaxDegreeEntropy()));
        sb.append(String.format("  Normalised degree entropy:    %.4f\n", getNormalisedDegreeEntropy()));
        sb.append(String.format("  Von Neumann entropy:          %.4f bits\n", vonNeumannEntropy));
        sb.append(String.format("  Edge type entropy:            %.4f bits\n", EdgeTypeEntropy));
        sb.append(String.format("  Topological info content:     %.4f bits\n", topologicalInfoContent));
        sb.append(String.format("  Random walk entropy rate:     %.4f bits/step\n", randomWalkEntropyRate));
        sb.append(String.format("  Chromatic entropy:            %.4f bits\n", chromaticEntropy));
        sb.append(String.format("  Degree-CC mutual info:        %.4f bits\n", degreeCCMutualInfo));
        sb.append("\n");

        sb.append("── Neighbourhood Entropy ──\n");
        sb.append(String.format("  Average:  %.4f bits\n", avgNeighbourhoodEntropy));
        String most = getMostDiverseVertex();
        String least = getLeastDiverseVertex();
        if (most != null) {
            sb.append(String.format("  Most diverse vertex:   %s (%.4f bits)\n",
                    most, neighbourhoodEntropy.containsKey(most) ? neighbourhoodEntropy.get(most) : 0.0));
        }
        if (least != null) {
            sb.append(String.format("  Least diverse vertex:  %s (%.4f bits)\n",
                    least, neighbourhoodEntropy.containsKey(least) ? neighbourhoodEntropy.get(least) : 0.0));
        }
        sb.append("\n");

        sb.append("── Complexity Classification ──\n");
        sb.append(String.format("  Structural complexity: %s\n", complexityClass));
        sb.append("\n");

        sb.append("── Interpretation ──\n");
        if (n > 0 && degreeEntropy < 0.5) {
            sb.append("  * Low degree entropy suggests a regular or near-regular graph.\n");
        } else if (getNormalisedDegreeEntropy() > 0.8) {
            sb.append("  * High degree entropy indicates heterogeneous degree distribution.\n");
        }
        if (n > 2 && vonNeumannEntropy > 0.7 * log2(n)) {
            sb.append("  * High Von Neumann entropy suggests complex, well-connected structure.\n");
        }
        if (edgeTypeEntropy > 1.0) {
            sb.append("  * High edge type entropy indicates diverse relationship types.\n");
        }
        if (degreeCCMutualInfo > 0.5) {
            sb.append("  * Significant degree-CC mutual information: degree strongly predicts clustering.\n");
        }

        return sb.toString();
    }
}
