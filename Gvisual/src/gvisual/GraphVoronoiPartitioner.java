package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Computes <b>Voronoi-like partitions</b> on a graph: given a set of seed
 * (generator) vertices, assigns every reachable vertex to its nearest seed
 * based on shortest-path distance, producing discrete "cells" analogous to
 * geometric Voronoi diagrams.
 *
 * <h3>Features</h3>
 * <ul>
 *   <li>Multi-source BFS partition with tie-breaking (alphabetical seed id)</li>
 *   <li>Cell boundary detection — edges whose endpoints belong to different cells</li>
 *   <li>Per-cell statistics: size, diameter, density, internal/boundary edge counts</li>
 *   <li>Dual graph construction — one node per cell, edges between adjacent cells</li>
 *   <li>HTML report export with colored partition map</li>
 *   <li>Text report for console/CLI usage</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 *   Set&lt;String&gt; seeds = Set.of("Alice", "Bob", "Carol");
 *   GraphVoronoiPartitioner p = new GraphVoronoiPartitioner(graph, seeds);
 *   GraphVoronoiPartitioner.VoronoiResult result = p.partition();
 *   System.out.println(result.toText());
 *   p.exportHtml(result, "voronoi-partition.html");
 * </pre>
 *
 * @author zalenix
 */
public class GraphVoronoiPartitioner {

    private final Graph<String, Edge> graph;
    private final Set<String> seeds;

    /**
     * Creates a partitioner for the given graph with the specified seed vertices.
     *
     * @param graph the graph to partition
     * @param seeds the seed (generator) vertices — must be present in the graph
     * @throws IllegalArgumentException if seeds is empty or contains unknown vertices
     */
    public GraphVoronoiPartitioner(Graph<String, Edge> graph, Set<String> seeds) {
        this.graph = Objects.requireNonNull(graph, "graph must not be null");
        if (seeds == null || seeds.isEmpty()) {
            throw new IllegalArgumentException("At least one seed vertex is required");
        }
        for (String s : seeds) {
            if (!graph.containsVertex(s)) {
                throw new IllegalArgumentException("Seed vertex not in graph: " + s);
            }
        }
        this.seeds = new LinkedHashSet<>(seeds);
    }

    /**
     * Computes the Voronoi partition via multi-source BFS.
     *
     * @return a VoronoiResult containing cell assignments and statistics
     */
    public VoronoiResult partition() {
        // Multi-source BFS
        Map<String, String> assignment = new LinkedHashMap<>();   // vertex → seed
        Map<String, Integer> distance = new HashMap<>();          // vertex → dist
        Queue<String> queue = new ArrayDeque<>();

        // Initialize seeds
        List<String> sortedSeeds = seeds.stream().sorted().collect(Collectors.toList());
        for (String seed : sortedSeeds) {
            assignment.put(seed, seed);
            distance.put(seed, 0);
            queue.add(seed);
        }

        // BFS
        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDist = distance.get(current);
            for (String neighbor : graph.getNeighbors(current)) {
                if (!distance.containsKey(neighbor)) {
                    distance.put(neighbor, currentDist + 1);
                    assignment.put(neighbor, assignment.get(current));
                    queue.add(neighbor);
                } else if (distance.get(neighbor) == currentDist + 1) {
                    // Tie-break: alphabetically smaller seed wins
                    String existingSeed = assignment.get(neighbor);
                    String candidateSeed = assignment.get(current);
                    if (candidateSeed.compareTo(existingSeed) < 0) {
                        assignment.put(neighbor, candidateSeed);
                    }
                }
            }
        }

        // Build cells
        Map<String, Set<String>> cells = new LinkedHashMap<>();
        for (String seed : sortedSeeds) {
            cells.put(seed, new LinkedHashSet<>());
        }
        for (Map.Entry<String, String> e : assignment.entrySet()) {
            cells.computeIfAbsent(e.getValue(), k -> new LinkedHashSet<>()).add(e.getKey());
        }

        // Find boundary edges
        List<Edge> boundaryEdges = new ArrayList<>();
        Set<String> boundaryEdgeSet = new HashSet<>();
        for (Edge edge : graph.getEdges()) {
            String v1 = edge.getVertex1();
            String v2 = edge.getVertex2();
            if (v1 == null || v2 == null) continue;
            String s1 = assignment.get(v1);
            String s2 = assignment.get(v2);
            if (s1 != null && s2 != null && !s1.equals(s2)) {
                String key = v1.compareTo(v2) < 0 ? v1 + "~" + v2 : v2 + "~" + v1;
                if (boundaryEdgeSet.add(key)) {
                    boundaryEdges.add(edge);
                }
            }
        }

        // Unreachable vertices
        Set<String> unreachable = new LinkedHashSet<>();
        for (String v : graph.getVertices()) {
            if (!assignment.containsKey(v)) {
                unreachable.add(v);
            }
        }

        // Cell statistics
        Map<String, CellStats> cellStats = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : cells.entrySet()) {
            cellStats.put(entry.getKey(), computeCellStats(entry.getKey(), entry.getValue(), assignment));
        }

        // Adjacency between cells (dual graph)
        Map<String, Set<String>> adjacency = new LinkedHashMap<>();
        for (String seed : sortedSeeds) {
            adjacency.put(seed, new TreeSet<>());
        }
        for (Edge edge : boundaryEdges) {
            String s1 = assignment.get(edge.getVertex1());
            String s2 = assignment.get(edge.getVertex2());
            if (s1 != null && s2 != null) {
                adjacency.get(s1).add(s2);
                adjacency.get(s2).add(s1);
            }
        }

        return new VoronoiResult(assignment, cells, boundaryEdges, unreachable,
                cellStats, adjacency, distance);
    }

    private CellStats computeCellStats(String seed, Set<String> members,
                                        Map<String, String> assignment) {
        int internalEdges = 0;
        int boundaryEdgeCount = 0;
        Set<String> boundaryVertices = new HashSet<>();

        for (String v : members) {
            for (String n : graph.getNeighbors(v)) {
                String nSeed = assignment.get(n);
                if (seed.equals(nSeed)) {
                    internalEdges++;
                } else {
                    boundaryEdgeCount++;
                    boundaryVertices.add(v);
                }
            }
        }
        // Each internal edge counted twice (once per endpoint)
        internalEdges /= 2;

        int n = members.size();
        double density = n > 1 ? (2.0 * internalEdges) / (n * (n - 1)) : 0.0;

        return new CellStats(n, internalEdges, boundaryEdgeCount, boundaryVertices.size(), density);
    }

    /**
     * Generates an HTML report with a colored table showing the partition.
     *
     * @param result  the partition result
     * @param path    output file path
     * @throws java.io.IOException if writing fails
     */
    public void exportHtml(VoronoiResult result, String path) throws java.io.IOException {
        String[] palette = {
            "#e6194b", "#3cb44b", "#4363d8", "#f58231", "#911eb4",
            "#42d4f4", "#f032e6", "#bfef45", "#fabed4", "#469990",
            "#dcbeff", "#9A6324", "#800000", "#aaffc3", "#808000",
            "#000075", "#a9a9a9"
        };

        List<String> seedList = new ArrayList<>(result.cells.keySet());
        Map<String, String> seedColors = new LinkedHashMap<>();
        for (int i = 0; i < seedList.size(); i++) {
            seedColors.put(seedList.get(i), palette[i % palette.length]);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html><head><meta charset='UTF-8'>\n");
        sb.append("<title>Graph Voronoi Partition</title>\n");
        sb.append("<style>\n");
        sb.append("body{font-family:system-ui,sans-serif;margin:2em;background:#1a1a2e;color:#e0e0e0}\n");
        sb.append("h1{color:#4fc3f7}h2{color:#81d4fa}h3{color:#b3e5fc}\n");
        sb.append("table{border-collapse:collapse;margin:1em 0}td,th{padding:6px 12px;border:1px solid #444}\n");
        sb.append("th{background:#263238}.seed{font-weight:bold;text-decoration:underline}\n");
        sb.append(".legend-dot{display:inline-block;width:14px;height:14px;border-radius:50%;margin-right:6px;vertical-align:middle}\n");
        sb.append(".card{background:#263238;border-radius:8px;padding:1em;margin:1em 0}\n");
        sb.append("</style></head><body>\n");
        sb.append("<h1>\uD83D\uDDFA\uFE0F Graph Voronoi Partition</h1>\n");
        sb.append("<p>").append(seedList.size()).append(" seeds → ")
          .append(result.assignment.size()).append(" assigned vertices");
        if (!result.unreachable.isEmpty()) {
            sb.append(" (").append(result.unreachable.size()).append(" unreachable)");
        }
        sb.append("</p>\n");

        // Legend
        sb.append("<div class='card'><h2>Legend</h2>\n");
        for (Map.Entry<String, String> e : seedColors.entrySet()) {
            sb.append("<span class='legend-dot' style='background:").append(e.getValue())
              .append("'></span>").append(e.getKey()).append("&nbsp;&nbsp;\n");
        }
        sb.append("</div>\n");

        // Cell details
        sb.append("<h2>Cell Details</h2>\n");
        for (String seed : seedList) {
            CellStats stats = result.cellStats.get(seed);
            String color = seedColors.get(seed);
            sb.append("<div class='card'><h3><span class='legend-dot' style='background:")
              .append(color).append("'></span>").append(seed).append("</h3>\n");
            sb.append("<table><tr><th>Metric</th><th>Value</th></tr>\n");
            sb.append("<tr><td>Size</td><td>").append(stats.size).append("</td></tr>\n");
            sb.append("<tr><td>Internal Edges</td><td>").append(stats.internalEdges).append("</td></tr>\n");
            sb.append("<tr><td>Boundary Edges</td><td>").append(stats.boundaryEdgeCount).append("</td></tr>\n");
            sb.append("<tr><td>Boundary Vertices</td><td>").append(stats.boundaryVertices).append("</td></tr>\n");
            sb.append("<tr><td>Density</td><td>").append(String.format("%.4f", stats.density)).append("</td></tr>\n");
            sb.append("</table>\n");

            // Members
            sb.append("<details><summary>Members (").append(stats.size).append(")</summary><p>");
            Set<String> members = result.cells.get(seed);
            sb.append(members.stream().sorted().collect(Collectors.joining(", ")));
            sb.append("</p></details></div>\n");
        }

        // Dual graph adjacency
        sb.append("<h2>Cell Adjacency (Dual Graph)</h2>\n<table><tr><th>Cell</th><th>Adjacent Cells</th></tr>\n");
        for (Map.Entry<String, Set<String>> e : result.adjacency.entrySet()) {
            sb.append("<tr><td>").append(e.getKey()).append("</td><td>")
              .append(String.join(", ", e.getValue())).append("</td></tr>\n");
        }
        sb.append("</table>\n");

        // Boundary edges
        sb.append("<h2>Boundary Edges (").append(result.boundaryEdges.size()).append(")</h2>\n");
        sb.append("<table><tr><th>From</th><th>To</th><th>Cell A</th><th>Cell B</th></tr>\n");
        for (Edge edge : result.boundaryEdges) {
            sb.append("<tr><td>").append(edge.getVertex1()).append("</td><td>").append(edge.getVertex2())
              .append("</td><td>").append(result.assignment.getOrDefault(edge.getVertex1(), "?"))
              .append("</td><td>").append(result.assignment.getOrDefault(edge.getVertex2(), "?"))
              .append("</td></tr>\n");
        }
        sb.append("</table>\n");

        sb.append("</body></html>");

        java.nio.file.Files.writeString(java.nio.file.Path.of(path), sb.toString());
    }

    // ── Inner classes ──────────────────────────────────────────────────

    /** Statistics for a single Voronoi cell. */
    public static class CellStats {
        public final int size;
        public final int internalEdges;
        public final int boundaryEdgeCount;
        public final int boundaryVertices;
        public final double density;

        CellStats(int size, int internalEdges, int boundaryEdgeCount,
                  int boundaryVertices, double density) {
            this.size = size;
            this.internalEdges = internalEdges;
            this.boundaryEdgeCount = boundaryEdgeCount;
            this.boundaryVertices = boundaryVertices;
            this.density = density;
        }
    }

    /** Full result of a Voronoi partition. */
    public static class VoronoiResult {
        /** Vertex → seed assignment. */
        public final Map<String, String> assignment;
        /** Seed → set of members. */
        public final Map<String, Set<String>> cells;
        /** Edges crossing cell boundaries. */
        public final List<Edge> boundaryEdges;
        /** Vertices not reachable from any seed. */
        public final Set<String> unreachable;
        /** Per-cell statistics. */
        public final Map<String, CellStats> cellStats;
        /** Cell adjacency (dual graph). */
        public final Map<String, Set<String>> adjacency;
        /** Vertex → shortest distance to its assigned seed. */
        public final Map<String, Integer> distances;

        VoronoiResult(Map<String, String> assignment, Map<String, Set<String>> cells,
                       List<Edge> boundaryEdges, Set<String> unreachable,
                       Map<String, CellStats> cellStats, Map<String, Set<String>> adjacency,
                       Map<String, Integer> distances) {
            this.assignment = Collections.unmodifiableMap(assignment);
            this.cells = Collections.unmodifiableMap(cells);
            this.boundaryEdges = Collections.unmodifiableList(boundaryEdges);
            this.unreachable = Collections.unmodifiableSet(unreachable);
            this.cellStats = Collections.unmodifiableMap(cellStats);
            this.adjacency = Collections.unmodifiableMap(adjacency);
            this.distances = Collections.unmodifiableMap(distances);
        }

        /** Generates a human-readable text report. */
        public String toText() {
            StringBuilder sb = new StringBuilder();
            sb.append("═══ Graph Voronoi Partition ═══\n\n");
            sb.append("Seeds: ").append(cells.size()).append("\n");
            sb.append("Assigned vertices: ").append(assignment.size()).append("\n");
            if (!unreachable.isEmpty()) {
                sb.append("Unreachable vertices: ").append(unreachable.size()).append("\n");
            }
            sb.append("Boundary edges: ").append(boundaryEdges.size()).append("\n\n");

            for (Map.Entry<String, CellStats> e : cellStats.entrySet()) {
                CellStats s = e.getValue();
                sb.append("── Cell: ").append(e.getKey()).append(" ──\n");
                sb.append("  Size:             ").append(s.size).append("\n");
                sb.append("  Internal edges:   ").append(s.internalEdges).append("\n");
                sb.append("  Boundary edges:   ").append(s.boundaryEdgeCount).append("\n");
                sb.append("  Boundary verts:   ").append(s.boundaryVertices).append("\n");
                sb.append("  Density:          ").append(String.format("%.4f", s.density)).append("\n");
                sb.append("  Adjacent cells:   ").append(String.join(", ", adjacency.get(e.getKey()))).append("\n\n");
            }

            return sb.toString();
        }
    }
}
