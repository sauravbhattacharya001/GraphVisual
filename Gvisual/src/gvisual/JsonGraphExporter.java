package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Exports a JUNG graph to JSON format compatible with popular web
 * visualization libraries (D3.js, vis.js, Sigma.js, Cytoscape.js).
 *
 * <p>The exported JSON follows a nodes/links structure:</p>
 * <pre>
 * {
 *   "metadata": { "timestamp": "...", "nodeCount": N, "edgeCount": M },
 *   "nodes": [ { "id": "A", "degree": 3 }, ... ],
 *   "links": [ { "source": "A", "target": "B", "type": "f", "weight": 1.5 }, ... ]
 * }
 * </pre>
 *
 * <p>This format can be directly loaded by D3.js force-directed layouts
 * and other JavaScript graph libraries for interactive web visualization.</p>
 *
 * <p>Usage:</p>
 * <pre>
 *   JsonGraphExporter exporter = new JsonGraphExporter(graph, allEdges);
 *   exporter.setTimestamp("2011-03-15");
 *   exporter.export(new File("graph.json"));
 *   // or
 *   String json = exporter.exportToString();
 * </pre>
 *
 * @author zalenix
 */
public class JsonGraphExporter {

    private final Graph<String, Edge> graph;
    private final List<Edge> allEdges;
    private String timestamp;
    private String description;
    private boolean prettyPrint;
    private boolean includeStats;

    /**
     * Creates a new JSON graph exporter.
     *
     * @param graph    the JUNG graph to export
     * @param allEdges all edges (including those not currently visible)
     */
    public JsonGraphExporter(Graph<String, Edge> graph, List<Edge> allEdges) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.allEdges = (allEdges != null) ? allEdges : new ArrayList<Edge>();
        this.timestamp = "";
        this.description = "";
        this.prettyPrint = true;
        this.includeStats = true;
    }

    /** Sets the timestamp metadata. */
    public void setTimestamp(String timestamp) {
        this.timestamp = (timestamp != null) ? timestamp : "";
    }

    /** Sets an optional description. */
    public void setDescription(String description) {
        this.description = (description != null) ? description : "";
    }

    /** Enable/disable pretty-printed output (default: true). */
    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    /** Enable/disable graph statistics in metadata (default: true). */
    public void setIncludeStats(boolean includeStats) {
        this.includeStats = includeStats;
    }

    /**
     * Exports the graph to JSON string.
     *
     * @return JSON string representation of the graph
     */
    public String exportToString() {
        StringBuilder sb = new StringBuilder();
        String nl = prettyPrint ? "\n" : "";
        String indent = prettyPrint ? "  " : "";
        String indent2 = prettyPrint ? "    " : "";
        String sep = prettyPrint ? " " : "";

        Collection<String> vertices = graph.getVertices();
        Collection<Edge> edges = graph.getEdges();

        sb.append("{").append(nl);

        // Metadata
        sb.append(indent).append("\"metadata\":").append(sep).append("{").append(nl);
        sb.append(indent2).append("\"timestamp\":").append(sep)
          .append(jsonString(timestamp)).append(",").append(nl);
        sb.append(indent2).append("\"description\":").append(sep)
          .append(jsonString(description)).append(",").append(nl);
        sb.append(indent2).append("\"nodeCount\":").append(sep)
          .append(vertices.size()).append(",").append(nl);
        sb.append(indent2).append("\"edgeCount\":").append(sep)
          .append(edges.size());

        if (includeStats && !vertices.isEmpty()) {
            // Compute basic stats
            int maxDegree = 0;
            int totalDegree = 0;
            for (String v : vertices) {
                int deg = graph.degree(v);
                totalDegree += deg;
                if (deg > maxDegree) maxDegree = deg;
            }
            double avgDegree = (double) totalDegree / vertices.size();
            int maxPossibleEdges = vertices.size() * (vertices.size() - 1) / 2;
            double density = maxPossibleEdges > 0
                    ? (double) edges.size() / maxPossibleEdges : 0.0;

            sb.append(",").append(nl);
            sb.append(indent2).append("\"averageDegree\":").append(sep)
              .append(String.format("%.2f", avgDegree)).append(",").append(nl);
            sb.append(indent2).append("\"maxDegree\":").append(sep)
              .append(maxDegree).append(",").append(nl);
            sb.append(indent2).append("\"density\":").append(sep)
              .append(String.format("%.4f", density));
        }

        sb.append(nl).append(indent).append("},").append(nl);

        // Nodes
        sb.append(indent).append("\"nodes\":").append(sep).append("[").append(nl);
        List<String> sortedVertices = new ArrayList<String>(vertices);
        Collections.sort(sortedVertices);
        for (int i = 0; i < sortedVertices.size(); i++) {
            String v = sortedVertices.get(i);
            int degree = graph.degree(v);
            int inDeg = graph.inDegree(v);
            int outDeg = graph.outDegree(v);
            sb.append(indent2).append("{");
            sb.append("\"id\":").append(sep).append(jsonString(v)).append(",").append(sep);
            sb.append("\"degree\":").append(sep).append(degree);
            if (inDeg != degree || outDeg != degree) {
                sb.append(",").append(sep).append("\"inDegree\":").append(sep).append(inDeg);
                sb.append(",").append(sep).append("\"outDegree\":").append(sep).append(outDeg);
            }
            // Count edges by type for this node
            Map<String, Integer> typeCounts = new TreeMap<String, Integer>();
            for (Edge e : graph.getIncidentEdges(v)) {
                String type = e.getType() != null ? e.getType() : "unknown";
                typeCounts.put(type, typeCounts.containsKey(type) ? typeCounts.get(type) + 1 : 1);
            }
            if (!typeCounts.isEmpty()) {
                sb.append(",").append(sep).append("\"edgeTypes\":").append(sep).append("{");
                int tc = 0;
                for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
                    if (tc > 0) sb.append(",").append(sep);
                    sb.append(jsonString(entry.getKey())).append(":").append(sep).append(entry.getValue());
                    tc++;
                }
                sb.append("}");
            }
            sb.append("}");
            if (i < sortedVertices.size() - 1) sb.append(",");
            sb.append(nl);
        }
        sb.append(indent).append("],").append(nl);

        // Links
        sb.append(indent).append("\"links\":").append(sep).append("[").append(nl);
        List<Edge> sortedEdges = new ArrayList<Edge>(edges);
        for (int i = 0; i < sortedEdges.size(); i++) {
            Edge e  sortedEdges.get(i);
            sb.append(indent2).append("{");
            sb.append("\"source\":").append(sep).append(jsonString(e.getVertex1())).append(",").append(sep);
            sb.append("\"target\":").append(sep).append(jsonString(e.getVertex2())).append(",").append(sep);
            sb.append("\"type\":").append(sep).append(jsonString(e.getType() != null ? e.getType() : "unknown")).append(",").append(sep);
            sb.append("\"weight\":").append(sep).append(e.getWeight());
            if (e.getLabel() != null && !e.getLabel().isEmpty()) {
                sb.append(",").append(sep).append("\"label\":").append(sep).append(jsonString(e.getLabel()));
            }
            if (e.getTimestamp() != null) {
                sb.append(",").append(sep).append("\"timestamp\":").append(sep).append(e.getTimestamp());
            }
            if (e.getEndTimestamp() != null) {
                sb.append(",").append(sep).append("\"endTimestamp\":").append(sep).append(e.getEndTimestamp());
            }
            sb.append("}");
            if (i < sortedEdges.size() - 1) sb.append(",");
            sb.append(nl);
        }
        sb.append(indent).append("]").append(nl);

        sb.append("}").append(nl);
        return sb.toString();
    }

    /**
     * Exports the graph to a JSON file.
     *
     * @param file the output file
     * @throws IOException if writing fails
     */
    public void export(File file) throws IOException {
        ExportUtils.validateOutputPath(file);
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(exportToString());
        }
    }

    /**
     * JSON-escapes a string value.
     */
    private static String jsonString(String value) {
        if (value == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
