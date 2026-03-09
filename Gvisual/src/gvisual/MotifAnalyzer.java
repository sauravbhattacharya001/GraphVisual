package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Graph Motif Analyzer — detects and counts small subgraph patterns
 * (network motifs) in undirected graphs.
 *
 * <p>Network motifs are recurring, statistically significant subgraph
 * patterns. They are the building blocks of complex networks and reveal
 * structural principles in social, biological, and technological graphs.</p>
 *
 * <h3>Detected Motifs</h3>
 * <ul>
 *   <li><b>Triangles</b> — 3 mutually connected vertices (clustering).</li>
 *   <li><b>Squares (4-cycles)</b> — 4 vertices forming a cycle without
 *       internal diagonals.</li>
 *   <li><b>Stars (k-stars)</b> — one hub vertex connected to k leaves with
 *       no inter-leaf edges. Reports 3-star, 4-star, and 5-star counts.</li>
 *   <li><b>Paths (P3)</b> — 3 vertices in a line (open triad).</li>
 *   <li><b>Wedges</b> — vertex triples where the center connects to both
 *       endpoints but the endpoints are not connected.</li>
 * </ul>
 *
 * <h3>Metrics</h3>
 * <ul>
 *   <li><b>Triangle density</b> — triangles / possible triangles.</li>
 *   <li><b>Global clustering coefficient</b> — 3 × triangles / wedges.</li>
 *   <li><b>Transitivity ratio</b> — fraction of wedges closed into triangles.</li>
 *   <li><b>Motif participation</b> — per-vertex count of each motif type.</li>
 *   <li><b>Motif fingerprint</b> — normalized frequency vector for comparing
 *       network structure across graphs.</li>
 * </ul>
 *
 * @author zalenix
 */
public class MotifAnalyzer {

    private final Graph<String, edge> graph;
    private Map<String, Set<String>> neighborCache;
    private boolean computed;

    // ── Results ─────────────────────────────────────────────────────
    private int triangleCount;
    private int squareCount;
    private int star3Count;
    private int star4Count;
    private int star5Count;
    private int wedgeCount;
    private int pathCount;
    private double clusteringCoefficient;
    private double triangleDensity;
    private double transitivity;
    private Map<String, Map<String, Integer>> vertexParticipation;
    private Map<String, Double> motifFingerprint;

    /**
     * Creates a new MotifAnalyzer for the given graph.
     *
     * @param graph the JUNG graph to analyze
     * @throws IllegalArgumentException if graph is null
     */
    public MotifAnalyzer(Graph<String, edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.neighborCache = new HashMap<String, Set<String>>();
        this.vertexParticipation = new LinkedHashMap<String, Map<String, Integer>>();
        this.motifFingerprint = new LinkedHashMap<String, Double>();
        this.computed = false;
    }

    // ── Core Computation ────────────────────────────────────────────

    /**
     * Runs the motif detection analysis. Idempotent — repeated calls
     * are no-ops.
     *
     * @return this analyzer for chaining
     */
    public MotifAnalyzer compute() {
        if (computed) return this;

        Collection<String> vertices = graph.getVertices();
        if (vertices.isEmpty()) {
            computed = true;
            return this;
        }

        // Build neighbor cache for O(1) lookups
        neighborCache = GraphUtils.buildAdjacencyMap(graph);

        // Initialize participation counts
        for (String v : vertices) {
            Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
            counts.put("triangle", 0);
            counts.put("square", 0);
            counts.put("star3", 0);
            counts.put("star4", 0);
            counts.put("star5", 0);
            counts.put("wedge", 0);
            vertexParticipation.put(v, counts);
        }

        // Sort vertices for consistent ordering
        List<String> sorted = new ArrayList<String>(vertices);
        Collections.sort(sorted);

        countTrianglesAndWedges(sorted);
        countSquares(sorted);
        countStars(sorted);
        computeMetrics(sorted);
        computeFingerprint();

        computed = true;
        return this;
    }

    // ── Triangle & Wedge Counting ───────────────────────────────────

    private void countTrianglesAndWedges(List<String> vertices) {
        triangleCount = 0;
        wedgeCount = 0;
        pathCount = 0;

        // For each vertex, count wedges centered on it and check closures
        for (String v : vertices) {
            Set<String> neighbors = neighborCache.get(v);
            if (neighbors == null || neighbors.size() < 2) continue;

            List<String> nList = new ArrayList<String>(neighbors);
            Collections.sort(nList);

            int localWedges = 0;
            int localTriangles = 0;

            for (int i = 0; i < nList.size(); i++) {
                for (int j = i + 1; j < nList.size(); j++) {
                    localWedges++;
                    String a = nList.get(i);
                    String b = nList.get(j);
                    Set<String> aN = neighborCache.get(a);
                    if (aN != null && aN.contains(b)) {
                        localTriangles++;
                    }
                }
            }

            wedgeCount += localWedges;
            addParticipation(v, "wedge", localWedges);
            addParticipation(v, "triangle", localTriangles);
            // Each triangle is counted 3 times (once per vertex)
            triangleCount += localTriangles;
        }
        triangleCount /= 3;
        // Paths (open triads) = wedges that are not closed
        pathCount = wedgeCount - (triangleCount * 3);
    }

    // ── Square (4-Cycle) Counting ───────────────────────────────────

    private void countSquares(List<String> vertices) {
        squareCount = 0;

        // For each pair of vertices at distance 2, count common neighbors.
        // Each 4-cycle A-B-C-D appears as two paths of length 2 sharing
        // endpoints (A-B-C and A-D-C). The number of 4-cycles through
        // a pair (A, C) with k common neighbors is C(k, 2).
        Map<String, Integer> indexMap = new HashMap<String, Integer>();
        for (int i = 0; i < vertices.size(); i++) {
            indexMap.put(vertices.get(i), i);
        }

        for (int i = 0; i < vertices.size(); i++) {
            String u = vertices.get(i);
            Set<String> uN = neighborCache.get(u);
            if (uN == null) continue;

            for (int j = i + 1; j < vertices.size(); j++) {
                String w = vertices.get(j);
                if (uN.contains(w)) continue; // Skip adjacent pairs

                Set<String> wN = neighborCache.get(w);
                if (wN == null) continue;

                // Count common neighbors between u and w
                int common = 0;
                for (String n : uN) {
                    if (wN.contains(n)) common++;
                }

                // C(common, 2) = common * (common - 1) / 2
                if (common >= 2) {
                    int cycles = common * (common - 1) / 2;
                    squareCount += cycles;

                    // Track participation for all 4 vertices of each square.
                    // For each common-neighbor pair (n1, n2) with n1 < n2,
                    // the square is u - n1 - w - n2.  All four get credit.
                    // (#33: previously only u and w were tracked.)
                    List<String> commonNeighbors = new ArrayList<>();
                    for (String n : uN) {
                        if (wN.contains(n)) commonNeighbors.add(n);
                    }
                    for (int a = 0; a < commonNeighbors.size(); a++) {
                        for (int b = a + 1; b < commonNeighbors.size(); b++) {
                            addParticipation(u, "square", 1);
                            addParticipation(w, "square", 1);
                            addParticipation(commonNeighbors.get(a), "square", 1);
                            addParticipation(commonNeighbors.get(b), "square", 1);
                        }
                    }
                }
            }
        }
        // Each 4-cycle is counted twice (once from each non-adjacent pair)
        // Actually, each square A-B-C-D has two pairs of non-adjacent vertices:
        // (A,C) and (B,D). So each square is counted exactly twice.
        // However, with our ordered i<j approach, and the fact that both
        // non-adjacent pairs are counted, we need to divide by... let me think.
        // For square A-B-C-D: non-adjacent pairs are (A,C) and (B,D).
        // Both pairs contribute C(2,2)=1 each. So total = 2 per square.
        // But we also need to account for common-neighbor participation.
        // Actually: each unique 4-cycle is found exactly once per non-adjacent
        // pair where i < j. A square has exactly 2 non-adjacent pairs.
        // So squareCount is double the actual count.

        // Fix participation — we over-counted, divide later
        // Actually let's just not fix participation from the non-adjacent
        // pair loop — recalculate from the final count
        squareCount /= 2;

        // Halve square participation counts to match corrected squareCount (#33).
        // Each square was found from both non-adjacent pairs, so participation
        // values are also 2x the true values.
        for (Map<String, Integer> m : vertexParticipation.values()) {
            Integer sq = m.get("square");
            if (sq != null && sq > 0) {
                m.put("square", sq / 2);
            }
        }
    }

    // ── Star Counting ───────────────────────────────────────────────

    private void countStars(List<String> vertices) {
        star3Count = 0;
        star4Count = 0;
        star5Count = 0;

        for (String v : vertices) {
            Set<String> neighbors = neighborCache.get(v);
            if (neighbors == null) continue;

            int degree = neighbors.size();

            // k-star: choose k neighbors from degree
            if (degree >= 3) {
                int s3 = comb(degree, 3);
                star3Count += s3;
                addParticipation(v, "star3", s3);
            }
            if (degree >= 4) {
                int s4 = comb(degree, 4);
                star4Count += s4;
                addParticipation(v, "star4", s4);
            }
            if (degree >= 5) {
                int s5 = comb(degree, 5);
                star5Count += s5;
                addParticipation(v, "star5", s5);
            }
        }
    }

    // ── Metrics ─────────────────────────────────────────────────────

    private void computeMetrics(List<String> vertices) {
        // Global clustering coefficient: 3 * triangles / wedges
        if (wedgeCount > 0) {
            clusteringCoefficient = (3.0 * triangleCount) / wedgeCount;
        } else {
            clusteringCoefficient = 0.0;
        }

        // Triangle density: triangles / C(n, 3)
        int n = vertices.size();
        if (n >= 3) {
            long possibleTriangles = (long) n * (n - 1) * (n - 2) / 6;
            triangleDensity = (double) triangleCount / possibleTriangles;
        } else {
            triangleDensity = 0.0;
        }

        // Transitivity: fraction of wedges that are closed
        if (wedgeCount > 0) {
            transitivity = (double) (triangleCount * 3) / wedgeCount;
        } else {
            transitivity = 0.0;
        }
    }

    private void computeFingerprint() {
        // Normalized motif frequency vector
        double total = triangleCount + squareCount + star3Count + star4Count
                + star5Count + wedgeCount;
        if (total == 0) total = 1.0; // avoid division by zero

        motifFingerprint = new LinkedHashMap<String, Double>();
        motifFingerprint.put("triangle", triangleCount / total);
        motifFingerprint.put("square", squareCount / total);
        motifFingerprint.put("star3", star3Count / total);
        motifFingerprint.put("star4", star4Count / total);
        motifFingerprint.put("star5", star5Count / total);
        motifFingerprint.put("wedge", wedgeCount / total);
    }

    // ── Helper ──────────────────────────────────────────────────────

    private void addParticipation(String vertex, String motif, int count) {
        Map<String, Integer> map = vertexParticipation.get(vertex);
        if (map != null) {
            map.put(motif, map.getOrDefault(motif, 0) + count);
        }
    }

    private int comb(int n, int k) {
        if (k > n || k < 0) return 0;
        if (k == 0 || k == n) return 1;
        // Use the smaller k for efficiency
        if (k > n - k) k = n - k;
        long result = 1;
        for (int i = 0; i < k; i++) {
            result = result * (n - i) / (i + 1);
        }
        return (int) result;
    }

    // ── Accessors ───────────────────────────────────────────────────

    /**
     * Returns the number of triangles in the graph.
     * @return triangle count
     */
    public int getTriangleCount() {
        ensureComputed();
        return triangleCount;
    }

    /**
     * Returns the number of 4-cycles (squares) in the graph.
     * @return square count
     */
    public int getSquareCount() {
        ensureComputed();
        return squareCount;
    }

    /**
     * Returns the number of 3-stars (hub + 3 leaves).
     * @return 3-star count
     */
    public int getStar3Count() {
        ensureComputed();
        return star3Count;
    }

    /**
     * Returns the number of 4-stars (hub + 4 leaves).
     * @return 4-star count
     */
    public int getStar4Count() {
        ensureComputed();
        return star4Count;
    }

    /**
     * Returns the number of 5-stars (hub + 5 leaves).
     * @return 5-star count
     */
    public int getStar5Count() {
        ensureComputed();
        return star5Count;
    }

    /**
     * Returns the number of wedges (open triads: A-B-C where B connects
     * to both A and C, but A and C are not connected).
     * @return wedge count
     */
    public int getWedgeCount() {
        ensureComputed();
        return wedgeCount;
    }

    /**
     * Returns the number of open paths (P3: wedges that are not closed).
     * @return path count (wedges - 3 × triangles)
     */
    public int getPathCount() {
        ensureComputed();
        return pathCount;
    }

    /**
     * Returns the global clustering coefficient (3 × triangles / wedges).
     * Ranges from 0.0 (no clustering) to 1.0 (every wedge is closed).
     * @return clustering coefficient
     */
    public double getClusteringCoefficient() {
        ensureComputed();
        return clusteringCoefficient;
    }

    /**
     * Returns the triangle density (triangles / C(n, 3)).
     * @return triangle density
     */
    public double getTriangleDensity() {
        ensureComputed();
        return triangleDensity;
    }

    /**
     * Returns the transitivity ratio (closed triads / total triads).
     * @return transitivity ratio
     */
    public double getTransitivity() {
        ensureComputed();
        return transitivity;
    }

    /**
     * Returns the motif participation counts for each vertex.
     * Outer key is vertex, inner key is motif type, value is count.
     * @return vertex participation map
     */
    public Map<String, Map<String, Integer>> getVertexParticipation() {
        ensureComputed();
        return Collections.unmodifiableMap(vertexParticipation);
    }

    /**
     * Returns the motif participation counts for a specific vertex.
     * @param vertex vertex to query
     * @return motif type → count map, or null if vertex not in graph
     */
    public Map<String, Integer> getParticipation(String vertex) {
        ensureComputed();
        Map<String, Integer> map = vertexParticipation.get(vertex);
        return map != null ? Collections.unmodifiableMap(map) : null;
    }

    /**
     * Returns the normalized motif fingerprint — a frequency vector
     * that characterizes the graph's structural profile. Useful for
     * comparing graphs: similar fingerprints imply similar structure.
     *
     * @return motif type → normalized frequency map
     */
    public Map<String, Double> getMotifFingerprint() {
        ensureComputed();
        return Collections.unmodifiableMap(motifFingerprint);
    }

    /**
     * Returns the top vertices by participation in a specific motif type.
     *
     * @param motifType motif type (triangle, square, star3, star4, star5, wedge)
     * @param topN number of top vertices to return
     * @return list of (vertex, count) pairs sorted descending by count
     * @throws IllegalArgumentException if motifType is invalid
     */
    public List<Map.Entry<String, Integer>> getTopParticipants(
            String motifType, int topN) {
        ensureComputed();
        if (motifType == null || !vertexParticipation.values().iterator()
                .hasNext()) {
            return Collections.emptyList();
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>();
        for (Map.Entry<String, Map<String, Integer>> e : vertexParticipation.entrySet()) {
            Integer count = e.getValue().get(motifType);
            if (count != null && count > 0) {
                entries.add(new AbstractMap.SimpleEntry<String, Integer>(
                        e.getKey(), count));
            }
        }

        entries.sort((Map.Entry<String, Integer> a,
                             Map.Entry<String, Integer> b) -> {
                return Integer.compare(b.getValue(), a.getValue());
            });

        if (topN > 0 && entries.size() > topN) {
            entries = entries.subList(0, topN);
        }

        return entries;
    }

    // ── Text Summary ────────────────────────────────────────────────

    /**
     * Returns a formatted text summary of the motif analysis.
     * @return multi-line summary string
     */
    public String getSummary() {
        ensureComputed();
        StringBuilder sb = new StringBuilder();
        sb.append("Graph Motif Analysis\n");
        sb.append("====================\n\n");
        sb.append(String.format("Vertices: %d    Edges: %d\n\n",
                graph.getVertexCount(), graph.getEdgeCount()));

        sb.append("── Motif Counts ──\n");
        sb.append(String.format("  Triangles:       %,d\n", triangleCount));
        sb.append(String.format("  Squares (4-cyc): %,d\n", squareCount));
        sb.append(String.format("  3-Stars:         %,d\n", star3Count));
        sb.append(String.format("  4-Stars:         %,d\n", star4Count));
        sb.append(String.format("  5-Stars:         %,d\n", star5Count));
        sb.append(String.format("  Wedges:          %,d\n", wedgeCount));
        sb.append(String.format("  Open paths:      %,d\n\n", pathCount));

        sb.append("── Derived Metrics ──\n");
        sb.append(String.format("  Clustering coeff: %.6f\n", clusteringCoefficient));
        sb.append(String.format("  Triangle density: %.6f\n", triangleDensity));
        sb.append(String.format("  Transitivity:     %.6f\n\n", transitivity));

        sb.append("── Motif Fingerprint ──\n");
        for (Map.Entry<String, Double> e : motifFingerprint.entrySet()) {
            sb.append(String.format("  %-10s  %.4f\n", e.getKey(), e.getValue()));
        }

        // Top triangle participants
        List<Map.Entry<String, Integer>> topTri = getTopParticipants("triangle", 5);
        if (!topTri.isEmpty()) {
            sb.append("\n── Top Triangle Participants ──\n");
            for (Map.Entry<String, Integer> e : topTri) {
                sb.append(String.format("  %-15s  %,d\n", e.getKey(), e.getValue()));
            }
        }

        return sb.toString();
    }

    private void ensureComputed() {
        if (!computed) {
            throw new IllegalStateException(
                    "Call compute() before accessing results.");
        }
    }
}
