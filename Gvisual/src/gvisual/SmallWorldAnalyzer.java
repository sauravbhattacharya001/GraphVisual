package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;

import java.util.*;

/**
 * Small-World Network Analyzer — determines whether a graph exhibits
 * small-world properties as defined by Watts and Strogatz (1998).
 *
 * <h3>Background</h3>
 * A small-world network has two hallmarks:
 * <ol>
 *   <li><b>High clustering</b> — neighbours of a vertex tend to be
 *       connected to each other (much higher than in a random graph).</li>
 *   <li><b>Short average path length</b> — any two vertices are
 *       reachable in a small number of hops (comparable to a random
 *       graph of the same size and density).</li>
 * </ol>
 *
 * <h3>Metrics Computed</h3>
 * <ul>
 *   <li><b>Local clustering coefficient</b> — per-vertex C(v) =
 *       2T / (d(d−1)) where T is the number of triangles.</li>
 *   <li><b>Average clustering coefficient</b> — mean of all C(v).</li>
 *   <li><b>Global clustering coefficient (transitivity)</b> —
 *       3 × triangles / connected triples.</li>
 *   <li><b>Average shortest path length (L)</b> — mean BFS distance
 *       over all reachable pairs in the largest connected component.</li>
 *   <li><b>Random-graph baselines</b> — expected clustering
 *       C_rand ≈ ⟨k⟩/n and path length L_rand ≈ ln(n)/ln(⟨k⟩)
 *       for an Erdős–Rényi graph with the same n and mean degree.</li>
 *   <li><b>Sigma (σ)</b> — Humphries &amp; Gurney (2008):
 *       σ = (C/C_rand) / (L/L_rand). σ &gt; 1 indicates small-world.</li>
 *   <li><b>Omega (ω)</b> — Telesford et al. (2011):
 *       ω = L_rand/L − C/C_lattice. Near 0 = small-world,
 *       near −1 = lattice, near +1 = random.</li>
 *   <li><b>Network classification</b> — categorises graph as
 *       Small-World, Random-Like, Lattice-Like, or Disconnected
 *       based on σ, ω, and component structure.</li>
 * </ul>
 *
 * <p>Analysis is lazy — call any getter and it will trigger
 * computation once if needed.</p>
 *
 * @author zalenix
 */
public class SmallWorldAnalyzer {

    private final Graph<String, edge> graph;
    private boolean computed;

    // ── Results ─────────────────────────────────────────────────────
    private Map<String, Double> localClustering;
    private double avgClustering;
    private double globalClustering;   // transitivity
    private double avgPathLength;
    private int largestComponentSize;
    private double randomClustering;   // C_rand
    private double randomPathLength;   // L_rand
    private double latticeClustering;  // C_lattice
    private double sigma;
    private double omega;
    private String classification;

    /**
     * Creates a new analyzer for the given graph.
     *
     * @param graph a JUNG undirected graph
     * @throws IllegalArgumentException if graph is null
     */
    public SmallWorldAnalyzer(Graph<String, edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.computed = false;
        this.localClustering = new LinkedHashMap<String, Double>();
    }

    // ── Lazy computation trigger ────────────────────────────────────

    private void ensureComputed() {
        if (!computed) {
            compute();
            computed = true;
        }
    }

    // ── Public getters ──────────────────────────────────────────────

    /**
     * Returns the local clustering coefficient for each vertex.
     */
    public Map<String, Double> getLocalClustering() {
        ensureComputed();
        return Collections.unmodifiableMap(localClustering);
    }

    /**
     * Returns the average local clustering coefficient.
     * This is the mean of all per-vertex clustering coefficients.
     */
    public double getAvgClustering() {
        ensureComputed();
        return avgClustering;
    }

    /**
     * Returns the global clustering coefficient (transitivity).
     * Defined as 3 × triangles / connected triples.
     */
    public double getGlobalClustering() {
        ensureComputed();
        return globalClustering;
    }

    /**
     * Returns the average shortest path length in the largest component.
     */
    public double getAvgPathLength() {
        ensureComputed();
        return avgPathLength;
    }

    /**
     * Returns the size of the largest connected component.
     */
    public int getLargestComponentSize() {
        ensureComputed();
        return largestComponentSize;
    }

    /**
     * Returns the expected clustering coefficient for a random graph
     * with the same n and mean degree: C_rand ≈ ⟨k⟩ / n.
     */
    public double getRandomClustering() {
        ensureComputed();
        return randomClustering;
    }

    /**
     * Returns the expected average path length for a random graph
     * with the same n and mean degree: L_rand ≈ ln(n) / ln(⟨k⟩).
     */
    public double getRandomPathLength() {
        ensureComputed();
        return randomPathLength;
    }

    /**
     * Returns the expected clustering coefficient for a ring lattice
     * with the same n and mean degree: C_lattice ≈ 3(k−2) / 4(k−1).
     */
    public double getLatticeClustering() {
        ensureComputed();
        return latticeClustering;
    }

    /**
     * Returns the sigma coefficient (Humphries &amp; Gurney 2008).
     * σ = (C / C_rand) / (L / L_rand).
     * Values significantly greater than 1 indicate small-world properties.
     */
    public double getSigma() {
        ensureComputed();
        return sigma;
    }

    /**
     * Returns the omega coefficient (Telesford et al. 2011).
     * ω = L_rand / L − C / C_lattice.
     * Near 0 = small-world, near −1 = lattice, near +1 = random.
     */
    public double getOmega() {
        ensureComputed();
        return omega;
    }

    /**
     * Returns the network classification string.
     * One of: "Small-World", "Random-Like", "Lattice-Like",
     * "Disconnected", "Too Small", "Too Sparse".
     */
    public String getClassification() {
        ensureComputed();
        return classification;
    }

    /**
     * Returns the top-N vertices by local clustering coefficient.
     *
     * @param n number of top vertices to return
     * @return list of entries sorted by clustering coefficient descending
     */
    public List<Map.Entry<String, Double>> getTopClustered(int n) {
        ensureComputed();
        List<Map.Entry<String, Double>> sorted =
                new ArrayList<Map.Entry<String, Double>>(localClustering.entrySet());
        Collections.sort(sorted, new Comparator<Map.Entry<String, Double>>() {
            @Override
            public int compare(Map.Entry<String, Double> a,
                               Map.Entry<String, Double> b) {
                return Double.compare(b.getValue(), a.getValue());
            }
        });
        return sorted.subList(0, Math.min(n, sorted.size()));
    }

    /**
     * Generates a formatted text summary of the small-world analysis.
     */
    public String getSummary() {
        ensureComputed();
        int n = graph.getVertexCount();
        int e = graph.getEdgeCount();
        double meanDeg = n > 0 ? (2.0 * e / n) : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("═══ Small-World Analysis ═══\n\n");

        sb.append(String.format("Vertices: %d    Edges: %d    Mean degree: %.2f\n", n, e, meanDeg));
        sb.append(String.format("Largest component: %d vertices (%.1f%%)\n\n",
                largestComponentSize, n > 0 ? 100.0 * largestComponentSize / n : 0));

        sb.append("── Clustering ──\n");
        sb.append(String.format("  Average local CC:  %.6f\n", avgClustering));
        sb.append(String.format("  Global CC (trans): %.6f\n", globalClustering));
        sb.append(String.format("  Random baseline:   %.6f  (C_rand = <k>/n)\n", randomClustering));
        sb.append(String.format("  Lattice baseline:  %.6f  (C_lattice)\n", latticeClustering));
        sb.append(String.format("  C / C_rand ratio:  %.2f\n",
                randomClustering > 0 ? avgClustering / randomClustering : 0));
        sb.append('\n');

        sb.append("── Path Length ──\n");
        sb.append(String.format("  Average L:         %.4f\n", avgPathLength));
        sb.append(String.format("  Random baseline:   %.4f  (L_rand = ln(n)/ln(<k>))\n", randomPathLength));
        sb.append(String.format("  L / L_rand ratio:  %.2f\n",
                randomPathLength > 0 ? avgPathLength / randomPathLength : 0));
        sb.append('\n');

        sb.append("── Small-World Coefficients ──\n");
        sb.append(String.format("  Sigma (σ):  %.4f  %s\n", sigma,
                sigma > 1 ? "(> 1 → small-world)" : "(≤ 1 → not small-world)"));
        sb.append(String.format("  Omega (ω):  %.4f  %s\n", omega, classifyOmega(omega)));
        sb.append('\n');

        sb.append(String.format("Classification: %s\n", classification));

        // Top-5 clustered vertices
        List<Map.Entry<String, Double>> top = getTopClustered(5);
        if (!top.isEmpty()) {
            sb.append("\n── Top Clustered Vertices ──\n");
            int rank = 1;
            for (Map.Entry<String, Double> entry : top) {
                sb.append(String.format("  %d. %s — CC = %.4f\n",
                        rank++, entry.getKey(), entry.getValue()));
            }
        }

        return sb.toString();
    }

    // ── Core computation ────────────────────────────────────────────

    private void compute() {
        int n = graph.getVertexCount();
        int edgeCount = graph.getEdgeCount();

        // Handle degenerate cases
        if (n < 3) {
            avgClustering = 0;
            globalClustering = 0;
            avgPathLength = 0;
            largestComponentSize = n;
            randomClustering = 0;
            randomPathLength = 0;
            latticeClustering = 0;
            sigma = 0;
            omega = 0;
            classification = "Too Small";
            return;
        }

        double meanDegree = 2.0 * edgeCount / n;
        if (meanDegree < 2) {
            computeClustering();
            avgPathLength = 0;
            largestComponentSize = findLargestComponent().size();
            randomClustering = meanDegree / n;
            randomPathLength = 0;
            latticeClustering = 0;
            sigma = 0;
            omega = 0;
            classification = "Too Sparse";
            return;
        }

        // 1. Clustering coefficients
        computeClustering();

        // 2. Average path length (largest component only)
        Set<String> largest = findLargestComponent();
        largestComponentSize = largest.size();
        avgPathLength = computeAvgPathLength(largest);

        // 3. Random-graph baselines
        randomClustering = meanDegree / n;
        if (meanDegree > 1) {
            randomPathLength = Math.log(n) / Math.log(meanDegree);
        } else {
            randomPathLength = 0;
        }

        // 4. Lattice baseline: C_lattice ≈ 3(k-2) / 4(k-1)
        if (meanDegree > 2) {
            latticeClustering = 3.0 * (meanDegree - 2) / (4.0 * (meanDegree - 1));
        } else {
            latticeClustering = 0;
        }

        // 5. Sigma = (C / C_rand) / (L / L_rand)
        if (randomClustering > 0 && randomPathLength > 0 && avgPathLength > 0) {
            double cRatio = avgClustering / randomClustering;
            double lRatio = avgPathLength / randomPathLength;
            sigma = cRatio / lRatio;
        } else {
            sigma = 0;
        }

        // 6. Omega = L_rand / L - C / C_lattice
        if (avgPathLength > 0 && latticeClustering > 0) {
            omega = randomPathLength / avgPathLength - avgClustering / latticeClustering;
        } else if (avgPathLength > 0 && randomPathLength > 0) {
            omega = randomPathLength / avgPathLength;
        } else {
            omega = 0;
        }

        // 7. Classify
        classification = classify();
    }

    // ── Clustering ──────────────────────────────────────────────────

    private void computeClustering() {
        int totalTriangles = 0;
        int totalTriples = 0;
        double ccSum = 0;
        int vertexCount = 0;

        for (String v : graph.getVertices()) {
            Collection<String> nbrs = graph.getNeighbors(v);
            if (nbrs == null) {
                localClustering.put(v, 0.0);
                vertexCount++;
                continue;
            }

            List<String> nbrList = new ArrayList<String>(nbrs);
            int d = nbrList.size();

            // Connected triples centred on v
            if (d >= 2) {
                totalTriples += d * (d - 1) / 2;
            }

            if (d < 2) {
                localClustering.put(v, 0.0);
                vertexCount++;
                continue;
            }

            // Count triangles through v
            Set<String> nbrSet = new HashSet<String>(nbrList);
            int triangles = 0;
            for (int i = 0; i < nbrList.size(); i++) {
                for (int j = i + 1; j < nbrList.size(); j++) {
                    if (graph.findEdge(nbrList.get(i), nbrList.get(j)) != null) {
                        triangles++;
                    }
                }
            }
            totalTriangles += triangles;

            double cc = (2.0 * triangles) / (d * (d - 1));
            localClustering.put(v, cc);
            ccSum += cc;
            vertexCount++;
        }

        avgClustering = vertexCount > 0 ? ccSum / vertexCount : 0;

        // Global clustering (transitivity) = triangles / triples
        // Each triangle counted once per vertex that centres a triple
        // containing it, so totalTriangles = closed triples.
        globalClustering = totalTriples > 0
                ? (double) totalTriangles / totalTriples : 0;
    }

    // ── Largest connected component ─────────────────────────────────

    private Set<String> findLargestComponent() {
        Set<String> visited = new HashSet<String>();
        Set<String> largest = Collections.emptySet();

        for (String v : graph.getVertices()) {
            if (!visited.contains(v)) {
                Set<String> comp = GraphUtils.bfsComponent(graph, v);
                visited.addAll(comp);
                if (comp.size() > largest.size()) {
                    largest = comp;
                }
            }
        }
        return largest;
    }

    // ── Average shortest path length ────────────────────────────────

    private double computeAvgPathLength(Set<String> component) {
        if (component.size() < 2) return 0;

        List<String> vertices = new ArrayList<String>(component);
        long totalDist = 0;
        long pairCount = 0;

        for (String source : vertices) {
            Map<String, Integer> distances =
                    GraphUtils.bfsDistances(graph, source);
            for (String target : vertices) {
                if (!source.equals(target)) {
                    Integer d = distances.get(target);
                    if (d != null) {
                        totalDist += d;
                        pairCount++;
                    }
                }
            }
        }

        return pairCount > 0 ? (double) totalDist / pairCount : 0;
    }

    // ── Classification ──────────────────────────────────────────────

    private String classify() {
        // Check component coverage: if largest component is < 50% of
        // vertices, the graph is too fragmented for meaningful analysis
        int n = graph.getVertexCount();
        if (largestComponentSize < n * 0.5) {
            return "Disconnected";
        }

        // Primary classification based on sigma
        if (sigma > 1.0) {
            // Refine with omega
            if (omega > -0.5 && omega < 0.5) {
                return "Small-World";
            } else if (omega <= -0.5) {
                return "Small-World (Lattice-Like)";
            } else {
                // omega >= 0.5 but sigma > 1 — unusual but possible
                // for graphs with very high clustering
                return "Small-World (Weak)";
            }
        }

        // sigma <= 1
        if (omega > 0.5) {
            return "Random-Like";
        } else if (omega < -0.5) {
            return "Lattice-Like";
        }

        return "Inconclusive";
    }

    private static String classifyOmega(double omega) {
        if (omega > 0.5) return "(→ +1 = random)";
        if (omega < -0.5) return "(→ −1 = lattice)";
        return "(≈ 0 = small-world)";
    }
}
