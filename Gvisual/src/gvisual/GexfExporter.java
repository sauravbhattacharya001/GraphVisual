package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Exports a JUNG graph to GEXF (Graph Exchange XML Format) — the native
 * format for <a href="https://gephi.org/">Gephi</a>, the leading open-source
 * graph visualization and analysis platform.
 *
 * <p>GEXF supports rich metadata, typed attributes, and dynamic (temporal)
 * graphs, making it ideal for studying community evolution over time.</p>
 *
 * <h3>Features</h3>
 * <ul>
 *   <li>Node attributes: degree, label</li>
 *   <li>Edge attributes: type, weight, label</li>
 *   <li>Dynamic mode with temporal edge spells when timestamps are present</li>
 *   <li>Edge-type → color mapping via viz:color elements</li>
 *   <li>Node sizing by degree via viz:size elements</li>
 *   <li>Creator and description metadata</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 *   GexfExporter exporter = new GexfExporter(graph, allEdges);
 *   exporter.setCreator("GraphVisual");
 *   exporter.setDescription("Student community network March 2011");
 *   exporter.export(new File("network.gexf"));
 *   // or
 *   String xml = exporter.exportToString();
 * </pre>
 *
 * @author zalenix
 * @see <a href="https://gexf.net/schema.html">GEXF 1.3 Specification</a>
 */
public class GexfExporter {

    private static final String GEXF_NS = "http://gexf.net/1.3";
    private static final String VIZ_NS = "http://gexf.net/1.3/viz";

    private final Graph<String, Edge> graph;
    private final List<Edge> allEdges;
    private String creator = "GraphVisual";
    private String description = "";
    private boolean includeVizData = true;

    // Edge-type → RGB colour mapping (matches the app's colour scheme)
    private static final Map<String, int[]> TYPE_COLORS = new LinkedHashMap<>();
    static {
        TYPE_COLORS.put("f",  new int[]{0, 200, 0});     // friend — green
        TYPE_COLORS.put("fs", new int[]{255, 165, 0});    // family/sibling — orange
        TYPE_COLORS.put("c",  new int[]{0, 150, 255});    // classmate — blue
        TYPE_COLORS.put("s",  new int[]{180, 180, 180});  // stranger — gray
        TYPE_COLORS.put("sg", new int[]{255, 80, 80});    // study group — red
    }

    /**
     * Creates a new GEXF exporter for the given graph.
     *
     * @param graph    the JUNG graph to export (must not be null)
     * @param allEdges all edges, including those filtered from the current view
     * @throws IllegalArgumentException if graph is null
     */
    public GexfExporter(Graph<String, Edge> graph, List<Edge> allEdges) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.allEdges = (allEdges != null) ? allEdges : new ArrayList<Edge>();
    }

    /** Sets the creator metadata field. */
    public void setCreator(String creator) {
        this.creator = (creator != null) ? creator : "";
    }

    /** Sets the description metadata field. */
    public void setDescription(String description) {
        this.description = (description != null) ? description : "";
    }

    /** Controls whether viz:color and viz:size elements are emitted. Default true. */
    public void setIncludeVizData(boolean includeVizData) {
        this.includeVizData = includeVizData;
    }

    /**
     * Exports the graph to GEXF and writes it to a file.
     *
     * @param file destination file
     * @throws IOException if an I/O error occurs
     */
    public void export(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("File must not be null");
        }
        ExportUtils.validateOutputPath(file);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (Writer w = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            w.write(exportToString());
        }
    }

    /**
     * Exports the graph to a GEXF XML string.
     *
     * @return the complete GEXF document as a string
     */
    public String exportToString() {
        StringBuilder sb = new StringBuilder(4096);

        boolean hasTemporal = hasTemporalEdges();

        // XML declaration + GEXF root
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<gexf xmlns=\"").append(GEXF_NS).append("\"");
        if (includeVizData) {
            sb.append(" xmlns:viz=\"").append(VIZ_NS).append("\"");
        }
        sb.append(" version=\"1.3\">\n");

        // Meta
        sb.append("  <meta lastmodifieddate=\"")
          .append(new SimpleDateFormat("yyyy-MM-dd").format(new Date()))
          .append("\">\n");
        sb.append("    <creator>").append(escapeXml(creator)).append("</creator>\n");
        if (!description.isEmpty()) {
            sb.append("    <description>").append(escapeXml(description)).append("</description>\n");
        }
        sb.append("  </meta>\n");

        // Graph element
        sb.append("  <graph defaultEdgetype=\"undirected\"");
        if (hasTemporal) {
            sb.append(" mode=\"dynamic\" timeformat=\"double\"");
        }
        sb.append(">\n");

        // Attribute declarations
        sb.append("    <attributes class=\"node\">\n");
        sb.append("      <attribute id=\"0\" title=\"degree\" type=\"integer\"/>\n");
        sb.append("    </attributes>\n");
        sb.append("    <attributes class=\"Edge\">\n");
        sb.append("      <attribute id=\"0\" title=\"Edgetype\" type=\"string\"/>\n");
        sb.append("      <attribute id=\"1\" title=\"label\" type=\"string\"/>\n");
        sb.append("    </attributes>\n");

        // Nodes
        Collection<String> vertices = graph.getVertices();
        int maxDegree = 0;
        for (String v : vertices) {
            int d = graph.degree(v);
            if (d > maxDegree) maxDegree = d;
        }

        sb.append("    <nodes>\n");
        for (String v : vertices) {
            int degree = graph.degree(v);
            sb.append("      <node id=\"").append(escapeXml(v))
              .append("\" label=\"").append(escapeXml(v)).append("\">\n");
            sb.append("        <attvalues>\n");
            sb.append("          <attvalue for=\"0\" value=\"").append(degree).append("\"/>\n");
            sb.append("        </attvalues>\n");
            if (includeVizData) {
                // Size proportional to degree
                float size = maxDegree > 0 ? 10.0f + 40.0f * degree / maxDegree : 10.0f;
                sb.append("        <viz:size value=\"")
                  .append(String.format("%.1f", size)).append("\"/>\n");
            }
            sb.append("      </node>\n");
        }
        sb.append("    </nodes>\n");

        // Edges — use visible edges from the graph
        sb.append("    <Edges>\n");
        Collection<Edge> edges = graph.getEdges();
        int edgeId = 0;
        for (Edge e : edges) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            // Only include edges whose vertices are in the graph
            if (!graph.containsVertex(v1) || !graph.containsVertex(v2)) {
                continue;
            }

            sb.append("      <edge id=\"").append(EdgeId++)
              .append("\" source=\"").append(escapeXml(v1))
              .append("\" target=\"").append(escapeXml(v2))
              .append("\" weight=\"").append(e.getWeight()).append("\">\n");

            // Attributes
            sb.append("        <attvalues>\n");
            String type = e.getType() != null ? e.getType() : "";
            sb.append("          <attvalue for=\"0\" value=\"").append(escapeXml(type)).append("\"/>\n");
            String label = e.getLabel() != null ? e.getLabel() : "";
            sb.append("          <attvalue for=\"1\" value=\"").append(escapeXml(label)).append("\"/>\n");
            sb.append("        </attvalues>\n");

            // Viz colour by edge type
            if (includeVizData && type.length() > 0) {
                int[] rgb = TYPE_COLORS.getOrDefault(type, new int[]{128, 128, 128});
                sb.append("        <viz:color r=\"").append(rgb[0])
                  .append("\" g=\"").append(rgb[1])
                  .append("\" b=\"").append(rgb[2])
                  .append("\" a=\"1.0\"/>\n");
            }

            // Temporal spells
            if (hasTemporal && e.getTimestamp() != null) {
                sb.append("        <spells>\n");
                sb.append("          <spell start=\"").append(e.getTimestamp().doubleValue()).append("\"");
                if (e.getEndTimestamp() != null) {
                    sb.append(" end=\"").append(e.getEndTimestamp().doubleValue()).append("\"");
                }
                sb.append("/>\n");
                sb.append("        </spells>\n");
            }

            sb.append("      </Edge>\n");
        }
        sb.append("    </Edges>\n");

        sb.append("  </graph>\n");
        sb.append("</gexf>\n");

        return sb.toString();
    }

    /**
     * Checks whether any edge in the graph carries temporal data.
     */
    private boolean hasTemporalEdges() {
        for (Edge e : graph.getEdges()) {
            if (e.getTimestamp() != null) return true;
        }
        return false;
    }

    /**
     * Escapes XML special characters in a string.
     */
    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
