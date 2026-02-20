package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Exports a JUNG graph to GraphML format — a standard XML-based graph
 * interchange format supported by Gephi, Cytoscape, NetworkX, yEd,
 * and many other graph analysis tools.
 *
 * <p>The exported file includes:</p>
 * <ul>
 *   <li>All vertices with their node IDs</li>
 *   <li>All edges with type, weight, label, and endpoint metadata</li>
 *   <li>Graph metadata (timestamp, node count, edge count)</li>
 *   <li>GraphML key definitions for custom attributes</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 *   GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);
 *   exporter.setTimestamp("2011-03-15");
 *   exporter.export(new File("graph.graphml"));
 *   // or
 *   String xml = exporter.exportToString();
 * </pre>
 *
 * @author zalenix
 */
public class GraphMLExporter {

    private final Graph<String, edge> graph;
    private final List<edge> allEdges;
    private String timestamp;
    private String description;

    /**
     * Creates a new GraphML exporter for the given graph.
     *
     * @param graph    the JUNG graph to export
     * @param allEdges all edges (including those not currently visible in graph)
     */
    public GraphMLExporter(Graph<String, edge> graph, List<edge> allEdges) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.allEdges = (allEdges != null) ? allEdges : new ArrayList<edge>();
        this.timestamp = "";
        this.description = "";
    }

    /**
     * Sets the timestamp metadata for the export.
     *
     * @param timestamp the timestamp string (e.g., "2011-03-15")
     */
    public void setTimestamp(String timestamp) {
        this.timestamp = (timestamp != null) ? timestamp : "";
    }

    /**
     * Gets the timestamp metadata.
     *
     * @return the timestamp string
     */
    public String getTimestamp() {
        return timestamp;
    }

    /**
     * Sets an optional description for the graph.
     *
     * @param description the description string
     */
    public void setDescription(String description) {
        this.description = (description != null) ? description : "";
    }

    /**
     * Gets the description.
     *
     * @return the description string
     */
    public String getDescription() {
        return description;
    }

    /**
     * Exports the graph to a GraphML file.
     *
     * @param file the output file
     * @throws IOException if writing fails
     */
    public void export(File file) throws IOException {
        try (Writer writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write(exportToString());
        }
    }

    /**
     * Exports the graph to a GraphML XML string.
     *
     * @return the complete GraphML XML as a string
     */
    public String exportToString() {
        StringBuilder sb = new StringBuilder();

        // XML header
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<graphml xmlns=\"http://graphml.graphstruct.org/xmlns\"\n");
        sb.append("    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        sb.append("    xsi:schemaLocation=\"http://graphml.graphstruct.org/xmlns\n");
        sb.append("        http://graphml.graphstruct.org/xmlns/1.0/graphml.xsd\">\n");

        // Key definitions for node and edge attributes
        sb.append("  <!-- Node attribute keys -->\n");
        sb.append("  <key id=\"d0\" for=\"node\" attr.name=\"label\" attr.type=\"string\"/>\n");

        sb.append("  <!-- Edge attribute keys -->\n");
        sb.append("  <key id=\"d1\" for=\"edge\" attr.name=\"type\" attr.type=\"string\"/>\n");
        sb.append("  <key id=\"d2\" for=\"edge\" attr.name=\"type_label\" attr.type=\"string\"/>\n");
        sb.append("  <key id=\"d3\" for=\"edge\" attr.name=\"weight\" attr.type=\"double\"/>\n");
        sb.append("  <key id=\"d4\" for=\"edge\" attr.name=\"label\" attr.type=\"string\"/>\n");

        // Graph element
        sb.append("  <graph id=\"G\" edgedefault=\"undirected\">\n");

        // Graph metadata as desc element
        if (!timestamp.isEmpty() || !description.isEmpty()) {
            sb.append("    <desc>");
            if (!description.isEmpty()) {
                sb.append(escapeXml(description));
            }
            if (!timestamp.isEmpty()) {
                if (!description.isEmpty()) sb.append(" | ");
                sb.append("Timestamp: ").append(escapeXml(timestamp));
            }
            sb.append("</desc>\n");
        }

        // Nodes — sorted for deterministic output
        List<String> vertices = new ArrayList<String>(graph.getVertices());
        Collections.sort(vertices);

        for (String vertex : vertices) {
            sb.append("    <node id=\"").append(escapeXml(vertex)).append("\">\n");
            sb.append("      <data key=\"d0\">").append(escapeXml(vertex)).append("</data>\n");
            sb.append("    </node>\n");
        }

        // Edges — use allEdges if provided (includes filtered edges), otherwise graph edges
        List<edge> edgesToExport;
        if (!allEdges.isEmpty()) {
            edgesToExport = allEdges;
        } else {
            edgesToExport = new ArrayList<edge>(graph.getEdges());
        }

        int edgeIndex = 0;
        for (edge e : edgesToExport) {
            String edgeId = "e" + edgeIndex++;
            sb.append("    <edge id=\"").append(edgeId).append("\"");
            sb.append(" source=\"").append(escapeXml(e.getVertex1())).append("\"");
            sb.append(" target=\"").append(escapeXml(e.getVertex2())).append("\">\n");

            // Edge type code
            sb.append("      <data key=\"d1\">").append(escapeXml(e.getType())).append("</data>\n");

            // Human-readable type label
            sb.append("      <data key=\"d2\">").append(escapeXml(getTypeLabel(e.getType()))).append("</data>\n");

            // Weight
            sb.append("      <data key=\"d3\">").append(String.format("%.1f", e.getWeight())).append("</data>\n");

            // Label (if set)
            if (e.getLabel() != null && !e.getLabel().isEmpty()) {
                sb.append("      <data key=\"d4\">").append(escapeXml(e.getLabel())).append("</data>\n");
            }

            sb.append("    </edge>\n");
        }

        sb.append("  </graph>\n");
        sb.append("</graphml>\n");

        return sb.toString();
    }

    /**
     * Exports only the edges currently visible in the graph (not all loaded edges).
     *
     * @return GraphML XML string with only visible edges
     */
    public String exportVisibleToString() {
        // Temporarily swap allEdges to only graph edges
        List<edge> saved = new ArrayList<edge>(allEdges);
        allEdges.clear();
        String result = exportToString();
        allEdges.addAll(saved);
        return result;
    }

    /**
     * Returns a human-readable label for an edge type code.
     *
     * @param typeCode the edge type code (f, fs, c, s, sg)
     * @return human-readable label
     */
    static String getTypeLabel(String typeCode) {
        if (typeCode == null) return "Unknown";
        switch (typeCode) {
            case "f":  return "Friend";
            case "fs": return "Familiar Stranger";
            case "c":  return "Classmate";
            case "s":  return "Stranger";
            case "sg": return "Study Group";
            default:   return typeCode;
        }
    }

    /**
     * Escapes special XML characters in a string.
     *
     * @param text the input text
     * @return XML-safe text
     */
    static String escapeXml(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':  sb.append("&amp;");  break;
                case '<':  sb.append("&lt;");   break;
                case '>':  sb.append("&gt;");   break;
                case '"':  sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                default:   sb.append(c);        break;
            }
        }
        return sb.toString();
    }

    /**
     * Returns the number of vertices that will be exported.
     *
     * @return vertex count
     */
    public int getVertexCount() {
        return graph.getVertexCount();
    }

    /**
     * Returns the number of edges that will be exported.
     * If allEdges is non-empty, returns allEdges count; otherwise graph edges.
     *
     * @return edge count
     */
    public int getEdgeCount() {
        if (!allEdges.isEmpty()) {
            return allEdges.size();
        }
        return graph.getEdgeCount();
    }
}
