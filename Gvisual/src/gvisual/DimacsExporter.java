package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Exports a JUNG graph to DIMACS format — the standard format used by
 * graph algorithm competitions and solvers.
 *
 * <p>DIMACS (.col / .dimacs) files are widely used for:</p>
 * <ul>
 *   <li>Graph coloring benchmarks (DIMACS Challenge)</li>
 *   <li>SAT solver inputs</li>
 *   <li>Max-clique and independent-set solvers</li>
 *   <li>Academic research and competition benchmarks</li>
 * </ul>
 *
 * <p>Format specification:</p>
 * <pre>
 *   c comment lines start with 'c'
 *   p edge &lt;num_vertices&gt; &lt;num_edges&gt;
 *   e &lt;u&gt; &lt;v&gt;
 *   ...
 * </pre>
 *
 * <p>Vertices are mapped to 1-based integer IDs as required by the format.</p>
 *
 * <p>Usage:</p>
 * <pre>
 *   DimacsExporter exporter = new DimacsExporter(graph);
 *   exporter.setDescription("My graph");
 *   exporter.export(new File("graph.col"));
 * </pre>
 *
 * @author zalenix
 */
public class DimacsExporter {

    private final Graph<String, Edge> graph;
    private String description = "";
    private String timestamp = "";

    /**
     * Creates a new DIMACS exporter.
     *
     * @param graph the JUNG graph to export
     */
    public DimacsExporter(Graph<String, Edge> graph) {
        this.graph = Objects.requireNonNull(graph, "graph must not be null");
    }

    /** Sets an optional description included as a comment. */
    public void setDescription(String description) {
        this.description = description != null ? description : "";
    }

    /** Sets an optional timestamp included as a comment. */
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp != null ? timestamp : "";
    }

    /** Returns the vertex count. */
    public int getVertexCount() {
        return graph.getVertexCount();
    }

    /** Returns the edge count. */
    public int getEdgeCount() {
        return graph.getEdgeCount();
    }

    /**
     * Exports the graph to DIMACS format.
     *
     * @param outFile destination file
     * @throws IOException if writing fails
     */
    public void export(File outFile) throws IOException {
        ExportUtils.validateOutputPath(outFile);
        // Build sorted vertex list and 1-based ID mapping
        List<String> vertices = new ArrayList<>(graph.getVertices());
        Collections.sort(vertices);
        Map<String, Integer> vertexId = new HashMap<>();
        for (int i = 0; i < vertices.size(); i++) {
            vertexId.put(vertices.get(i), i + 1);
        }

        // Collect unique edges (avoid duplicates for undirected graphs)
        Set<String> seenEdges = new LinkedHashSet<>();
        List<int[]> edgeList = new ArrayList<>();
        for (Edge edge : graph.getEdges()) {
            String v1 = graph.getEndpoints(edge).getFirst();
            String v2 = graph.getEndpoints(edge).getSecond();
            int id1 = vertexId.get(v1);
            int id2 = vertexId.get(v2);
            int lo = Math.min(id1, id2);
            int hi = Math.max(id1, id2);
            String key = lo + "-" + hi;
            if (seenEdges.add(key)) {
                edgeList.add(new int[]{lo, hi});
            }
        }

        try (PrintWriter pw = new PrintWriter(
                new BufferedWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(outFile), StandardCharsets.UTF_8)))) {
            // Comment header
            pw.println("c DIMACS graph exported by GraphVisual");
            if (!description.isEmpty()) {
                pw.println("c Description: " + description);
            }
            if (!timestamp.isEmpty()) {
                pw.println("c Timestamp: " + timestamp);
            }
            pw.println("c Export time: " + Instant.now());
            pw.println("c Vertex mapping:");
            for (String v : vertices) {
                pw.println("c   " + vertexId.get(v) + " = " + v);
            }

            // Problem line
            pw.println("p edge " + vertices.size() + " " + edgeList.size());

            // Edge lines
            for (int[] e : edgeList) {
                pw.println("e " + e[0] + " " + e[1]);
            }
        }
    }

    /**
     * Convenience method: exports and returns a summary string.
     *
     * @param outFile destination file
     * @return human-readable summary
     * @throws IOException if writing fails
     */
    public String exportWithSummary(File outFile) throws IOException {
        export(outFile);
        return "DIMACS exported successfully!\n"
                + "Nodes: " + getVertexCount() + "\n"
                + "Edges: " + edgeList(outFile) + "\n"
                + "File: " + outFile.getName() + "\n\n"
                + "Compatible with DIMACS Challenge solvers,\n"
                + "graph coloring tools, and SAT/clique solvers.";
    }

    /* Re-count edges from the deduplicated set (called after export). */
    private int edgeList(File outFile) throws IOException {
        // Count 'e' lines in the written file
        int count = 0;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(outFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("e ")) count++;
            }
        }
        return count;
    }
}
