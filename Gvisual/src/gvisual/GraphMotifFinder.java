package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects and counts common subgraph motifs (network patterns) in a JUNG graph.
 *
 * <p>Motif analysis reveals the fundamental building blocks of a network,
 * exposing local structural patterns that shape global behaviour.
 * This analyzer identifies and counts several canonical motifs:</p>
 *
 * <ul>
 *   <li><b>Triangles (3-cliques):</b> Three mutually connected nodes — indicates
 *       clustering and tight-knit groups</li>
 *   <li><b>Stars (k-stars):</b> Hub node connected to k leaves — indicates
 *       centralized topology</li>
 *   <li><b>Paths (P3):</b> Three-node paths where the middle node bridges two
 *       otherwise disconnected nodes — indicates brokerage</li>
 *   <li><b>Squares (C4):</b> Four-node cycles — indicates redundancy and
 *       alternative routing</li>
 *   <li><b>Fan motifs:</b> A node connected to two or more neighbors that are
 *       not connected to each other — indicates broadcasting</li>
 * </ul>
 *
 * <h3>Applications</h3>
 * <ul>
 *   <li>Social network role analysis (brokers, hubs, clique members)</li>
 *   <li>Biological network function prediction</li>
 *   <li>Communication pattern classification</li>
 *   <li>Fraud detection via unusual motif distributions</li>
 *   <li>Network comparison via motif frequency profiles</li>
 * </ul>
 *
 * @author zalenix
 */
public class GraphMotifFinder {

    private final Graph<String, Edge> graph;

    /** All triangles found as sorted triples. */
    private List<List<String>> triangles;

    /** Stars keyed by hub node, value is list of leaf nodes. */
    private Map<String, List<String>> stars;

    /** Open paths (P3): [endpoint, middle, endpoint]. */
    private List<List<String>> openPaths;

    /** Squares (C4): four-node cycles. */
    private List<List<String>> squares;

    /** Motif census: motif name → count. */
    private Map<String, Integer> census;

    private static final int MIN_STAR_DEGREE = 3;
    private static final int MAX_SQUARES = 50_000;

    /**
     * @param graph the JUNG graph to analyze
     */
    public GraphMotifFinder(Graph<String, Edge> graph) {
        this.graph = Objects.requireNonNull(graph, "graph must not be null");
    }

    /**
     * Run full motif analysis. Call this before querying results.
     */
    public void analyze() {
        findTriangles();
        findStars();
        findOpenPaths();
        findSquares();
        buildCensus();
    }

    /* ── Triangle detection ─────────────────────────────────────────── */

    private void findTriangles() {
        triangles = new ArrayList<>();
        List<String> vertices = new ArrayList<>(graph.getVertices());
        Collections.sort(vertices);
        Set<String> vertexSet = new HashSet<>(vertices);

        for (int i = 0; i < vertices.size(); i++) {
            String u = vertices.get(i);
            Set<String> uNeighbors = new HashSet<>(graph.getNeighbors(u));
            for (int j = i + 1; j < vertices.size(); j++) {
                String v = vertices.get(j);
                if (!uNeighbors.contains(v)) continue;
                Set<String> vNeighbors = new HashSet<>(graph.getNeighbors(v));
                for (int k = j + 1; k < vertices.size(); k++) {
                    String w = vertices.get(k);
                    if (uNeighbors.contains(w) && vNeighbors.contains(w)) {
                        triangles.add(Arrays.asList(u, v, w));
                    }
                }
            }
        }
    }

    /* ── Star detection ─────────────────────────────────────────────── */

    private void findStars() {
        stars = new LinkedHashMap<>();
        for (String v : graph.getVertices()) {
            Collection<String> neighbors = graph.getNeighbors(v);
            if (neighbors.size() >= MIN_STAR_DEGREE) {
                List<String> sorted = new ArrayList<>(neighbors);
                Collections.sort(sorted);
                stars.put(v, sorted);
            }
        }
    }

    /* ── Open path (P3) detection ───────────────────────────────────── */

    private void findOpenPaths() {
        openPaths = new ArrayList<>();
        for (String middle : graph.getVertices()) {
            List<String> neighbors = new ArrayList<>(graph.getNeighbors(middle));
            Collections.sort(neighbors);
            for (int i = 0; i < neighbors.size(); i++) {
                for (int j = i + 1; j < neighbors.size(); j++) {
                    String a = neighbors.get(i);
                    String b = neighbors.get(j);
                    // Only count if a and b are NOT connected (otherwise it's a triangle)
                    if (!graph.isNeighbor(a, b)) {
                        openPaths.add(Arrays.asList(a, middle, b));
                    }
                }
            }
        }
    }

    /* ── Square (C4) detection ──────────────────────────────────────── */

    private void findSquares() {
        squares = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<String> vertices = new ArrayList<>(graph.getVertices());
        Collections.sort(vertices);

        outer:
        for (String u : vertices) {
            List<String> uNeighbors = new ArrayList<>(graph.getNeighbors(u));
            Collections.sort(uNeighbors);
            for (int i = 0; i < uNeighbors.size(); i++) {
                String v = uNeighbors.get(i);
                if (v.compareTo(u) <= 0) continue;
                for (int j = i + 1; j < uNeighbors.size(); j++) {
                    String w = uNeighbors.get(j);
                    if (w.compareTo(u) <= 0) continue;
                    if (graph.isNeighbor(v, w)) continue; // would be a triangle
                    // Find common neighbor x of v and w (x != u)
                    Set<String> vNeighbors = new HashSet<>(graph.getNeighbors(v));
                    for (String x : graph.getNeighbors(w)) {
                        if (x.equals(u) || x.compareTo(u) <= 0) continue;
                        if (vNeighbors.contains(x) && !graph.isNeighbor(x, u)) {
                            List<String> sq = Arrays.asList(u, v, x, w);
                            Collections.sort(sq);
                            String key = String.join(",", sq);
                            if (seen.add(key)) {
                                squares.add(sq);
                                if (squares.size() >= MAX_SQUARES) break outer;
                            }
                        }
                    }
                }
            }
        }
    }

    /* ── Census ─────────────────────────────────────────────────────── */

    private void buildCensus() {
        census = new LinkedHashMap<>();
        census.put("Triangles", triangles.size());
        census.put("Stars (degree ≥ " + MIN_STAR_DEGREE + ")", stars.size());
        census.put("Open Paths (P3)", openPaths.size());
        census.put("Squares (C4)", squares.size());
    }

    /* ── Getters ────────────────────────────────────────────────────── */

    /** @return all triangles as sorted triples */
    public List<List<String>> getTriangles() { return triangles; }

    /** @return star hubs with their leaf lists */
    public Map<String, List<String>> getStars() { return stars; }

    /** @return open P3 paths as [endpoint, middle, endpoint] */
    public List<List<String>> getOpenPaths() { return openPaths; }

    /** @return square cycles (C4) as sorted quadruples */
    public List<List<String>> getSquares() { return squares; }

    /** @return motif census map (name → count) */
    public Map<String, Integer> getCensus() { return census; }

    /**
     * Global clustering coefficient based on triangle count.
     * Ratio of closed triplets (3 × triangles) to total triplets.
     */
    public double getClusteringCoefficient() {
        int closedTriplets = triangles.size() * 3;
        int totalTriplets = closedTriplets + openPaths.size();
        return totalTriplets == 0 ? 0.0 : (double) closedTriplets / totalTriplets;
    }

    /**
     * Compute per-node local clustering coefficient.
     * @return map of node → local clustering coefficient
     */
    public Map<String, Double> getLocalClustering() {
        Map<String, Double> result = new LinkedHashMap<>();
        for (String v : graph.getVertices()) {
            List<String> neighbors = new ArrayList<>(graph.getNeighbors(v));
            int k = neighbors.size();
            if (k < 2) {
                result.put(v, 0.0);
                continue;
            }
            int links = 0;
            for (int i = 0; i < neighbors.size(); i++) {
                for (int j = i + 1; j < neighbors.size(); j++) {
                    if (graph.isNeighbor(neighbors.get(i), neighbors.get(j))) {
                        links++;
                    }
                }
            }
            double maxLinks = k * (k - 1.0) / 2.0;
            result.put(v, links / maxLinks);
        }
        return result;
    }

    /**
     * Nodes that participate in the most triangles.
     * @param topN how many to return
     * @return sorted list of (node, triangle count) pairs
     */
    public List<Map.Entry<String, Integer>> getTopTriangleNodes(int topN) {
        Map<String, Integer> counts = new HashMap<>();
        for (List<String> tri : triangles) {
            for (String v : tri) {
                counts.merge(v, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .collect(Collectors.toList());
    }

    /* ── Report ─────────────────────────────────────────────────────── */

    /**
     * Generate a human-readable motif analysis report.
     */
    public String generateReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════\n");
        sb.append("           GRAPH MOTIF ANALYSIS REPORT\n");
        sb.append("═══════════════════════════════════════════════════\n\n");

        sb.append(String.format("Vertices: %d    Edges: %d%n%n",
                graph.getVertexCount(), graph.getEdgeCount()));

        sb.append("─── Motif Census ───────────────────────────────────\n");
        for (Map.Entry<String, Integer> e : census.entrySet()) {
            sb.append(String.format("  %-30s %,d%n", e.getKey(), e.getValue()));
        }

        sb.append(String.format("%n  Global Clustering Coefficient: %.4f%n",
                getClusteringCoefficient()));

        if (!triangles.isEmpty()) {
            sb.append("\n─── Sample Triangles (up to 10) ────────────────────\n");
            for (int i = 0; i < Math.min(10, triangles.size()); i++) {
                sb.append("  △ ").append(triangles.get(i)).append('\n');
            }
        }

        if (!stars.isEmpty()) {
            sb.append("\n─── Star Hubs (up to 10) ───────────────────────────\n");
            stars.entrySet().stream()
                    .sorted((a, b) -> b.getValue().size() - a.getValue().size())
                    .limit(10)
                    .forEach(e -> sb.append(String.format("  ★ %-20s degree %d%n",
                            e.getKey(), e.getValue().size())));
        }

        if (!squares.isEmpty()) {
            sb.append("\n─── Sample Squares (up to 10) ──────────────────────\n");
            for (int i = 0; i < Math.min(10, squares.size()); i++) {
                sb.append("  □ ").append(squares.get(i)).append('\n');
            }
        }

        List<Map.Entry<String, Integer>> topTri = getTopTriangleNodes(5);
        if (!topTri.isEmpty()) {
            sb.append("\n─── Top Triangle Participants ──────────────────────\n");
            for (Map.Entry<String, Integer> e : topTri) {
                sb.append(String.format("  %-20s %d triangles%n", e.getKey(), e.getValue()));
            }
        }

        sb.append("\n═══════════════════════════════════════════════════\n");
        return sb.toString();
    }
}
