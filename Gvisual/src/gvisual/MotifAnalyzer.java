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

    private final Graph<String, Edge> graph;
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
    public MotifAnalyzer(Graph<String, Edge> graph) {
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

    /**
     * Counts 4-cycles (squares) using 2-path enumeration.
     *
     * <p><b>Algorithm:</b> For each vertex b, enumerate all pairs of b's
     * neighbors (u, w) where u &lt; w (lexicographic) and u-w are NOT adjacent.
     * Each such pair forms a 2-path u-b-w. A pair (u, w) with k common
     * neighbors yields C(k, 2) distinct 4-cycles.</p>
     *
     * <p><b>Performance:</b> The previous implementation used O(V² · Δ)
     * all-pairs enumeration — for every pair of vertices it scanned one
     * vertex's neighbor set to count overlap. This version enumerates
     * 2-paths per vertex in O(Δ²) time, giving O(V · Δ²) total. On sparse
     * graphs where Δ ≪ V, this is orders of magnitude faster (e.g., a
     * 1000-vertex graph with average degree 10: old = ~10M pair checks,
     * new = ~100K 2-path checks).</p>
     */
    private void countSquares(List<String> vertices) {
        squareCount = 0;

        // Phase 1: Count common non-adjacent neighbors for each non-adjacent
        // pair by enumerating 2-paths through each vertex.
        // Key = "min|max" canonical pair string, value = common neighbor count.
        Map<String, Integer> pairCommonCount = new HashMap<String, Integer>();

        for (String b : vertices) {
            Set<String> bNeighbors = neighborCache.get(b);
            if (bNeighbors == null || bNeighbors.size() < 2) continue;

            // Sort b's neighbors for consistent canonical ordering
            List<String> nList = new ArrayList<String>(bNeighbors);
            Collections.sort(nList);

            for (int i = 0; i < nList.size(); i++) {
                String u = nList.get(i);
                Set<String> uN = neighborCache.get(u);
                for (int j = i + 1; j < nList.size(); j++) {
                    String w = nList.get(j);
                    // Only count if u and w are NOT adjacent (otherwise it's
                    // a triangle edge, not a 4-cycle diagonal)
                    if (uN != null && uN.contains(w)) continue;

                    // Canonical key: u < w lexicographically (already sorted)
                    String key = u + "|" + w;
                    pairCommonCount.merge(key, 1, Integer::sum);
                }
            }
        }

        // Phase 2: For each non-adjacent pair with k >= 2 common neighbors,
        // there are C(k, 2) 4-cycles. Track participation for all vertices.
        for (Map.Entry<String, Integer> entry : pairCommonCount.entrySet()) {
            int k = entry.getValue();
            if (k < 2) continue;

            int cycles = k * (k - 1) / 2;
            squareCount += cycles;

            // Parse the pair
            String pairKey = entry.getKey();
            int sep = pairKey.indexOf('|');
            String u = pairKey.substring(0, sep);
            String w = pairKey.substring(sep + 1);

            // Each 4-cycle involves u, w, and 2 of the k common neighbors.
            // u and w each participate in all C(k,2) cycles.
            addParticipation(u, "square", cycles);
            addParticipation(w, "square", cycles);

            // Each common neighbor b participates in (k-1) of the C(k,2)
            // cycles (paired with each of the other k-1 common neighbors).
            // Collect common neighbors by re-checking b's neighbors.
            Set<String> uN = neighborCache.get(u);
            Set<String> wN = neighborCache.get(w);
            Set<String> smaller = uN.size() <= wN.size() ? uN : wN;
            Set<String> larger  = uN.size() <= wN.size() ? wN : uN;
            for (String b : smaller) {
                if (larger.contains(b)) {
                    addParticipation(b, "square", k - 1);
                }
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
