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
 *   <li>Graph metadata (timestamp, node count, Edge count)</li>
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

    private final Graph<String, Edge> graph;
    private final List<Edge> allEdges;
    private String timestamp;
    private String description;

    /**
     * Creates a new GraphML exporter for the given graph.
     *
     * @param graph    the JUNG graph to export
     * @param allEdges all edges (including those not currently visible in graph)
     */
    public GraphMLExporter(Graph<String, Edge> graph, List<Edge> allEdges) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.allEdges = (allEdges != null) ? allEdges : new ArrayList<Edge>();
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
        ExportUtils.validateOutputPath(file);
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
        List<Edge> edgesToExport = !allEdges.isEmpty()
                ? allEdges
                : new ArrayList<Edge>(graph.getEdges());
        return exportToString(edgesToExport);
    }

    /**
     * Exports only the edges currently visible in the graph (not all loaded edges).
     *
     * @return GraphML XML string with only visible edges
     */
    public String exportVisibleToString() {
        return exportToString(new ArrayList<Edge>(graph.getEdges()));
    }

    /**
     * Internal: exports the graph to a GraphML XML string using the given edge list.
     *
     * @param edgesToExport the edges to include in the export
     * @return the complete GraphML XML as a string
     */
    private String exportToString(List<Edge> edgesToExport) {
        StringBuilder sb = new StringBuilder();

        // XML header
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\"\n");
        sb.append("    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        sb.append("    xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns\n");
        sb.append("        http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd\">\n");

        // Key definitions for node and edge attributes
        sb.append("  <!-- Node attribute keys -->\n");
        sb.append("  <key id=\"d0\" for=\"node\" attr.name=\"label\" attr.type=\"string\"/>\n");

        sb.append("  <!-- Edge attribute keys -->\n");
        sb.append("  <key id=\"d1\" for=\"Edge\" attr.name=\"type\" attr.type=\"string\"/>\n");
        sb.append("  <key id=\"d2\" for=\"Edge\" attr.name=\"type_label\" attr.type=\"string\"/>\n");
        sb.append("  <key id=\"d3\" for=\"Edge\" attr.name=\"weight\" attr.type=\"double\"/>\n");
        sb.append("  <key id=\"d4\" for=\"Edge\" attr.name=\"label\" attr.type=\"string\"/>\n");

        // Graph element
        sb.append("  <graph id=\"G\" Edgedefault=\"undirected\">\n");

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

        // Edges — use the provided edgesToExport list

        int edgeIndex = 0;
        for (Edge e : edgesToExport) {
            String edgeId = "e" + edgeIndex++;
            sb.append("    <edge id=\"").append(EdgeId).append("\"");
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

            sb.append("    </Edge>\n");
        }

        sb.append("  </graph>\n");
        sb.append("</graphml>\n");

        return sb.toString();
    }

    /**
     * Returns a human-readable label for an edge type code.
     *
     * @param typeCode the edge type code (f, fs, c, s, sg)
     * @return human-readable label
     */
    static String getTypeLabel(String typeCode) {
        EdgeType type = EdgeType.fromCode(typeCode);
        if (type != null) {
            String label = type.getDisplayLabel();
            // Capitalise first letter for consistent title-case output
            if (!label.isEmpty()) {
                label = Character.toUpperCase(label.charAt(0)) + label.substring(1);
            }
            return label;
        }
        return typeCode != null ? typeCode : "Unknown";
    }

    /**
     * Escapes special XML characters and strips illegal XML control characters.
     * XML 1.0 only allows: #x9 (tab), #xA (newline), #xD (carriage return),
     * and characters >= #x20. All other control characters (U+0000-U+0008,
     * U+000B, U+000C, U+000E-U+001F) are stripped to produce valid XML.
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
                case '\t': // fall through — legal XML whitespace
                case '\n': // fall through
                case '\r': sb.append(c);        break;
                default:
                    // Strip illegal XML 1.0 control characters
                    if (c >= 0x20) {
                        sb.append(c);
                    }
                    // else: silently drop illegal control character
                    break;
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
