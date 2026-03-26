package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Exports a JUNG graph to Graphviz DOT format — the most widely used
 * text-based graph description language.
 *
 * <p>DOT files can be rendered with Graphviz tools (dot, neato, fdp, sfdp,
 * circo, twopi) and are supported by many visualization platforms including
 * D3.js, vis.js, and online renderers like viz-js.com.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Edge type → color mapping for visual distinction</li>
 *   <li>Edge weight → pen width scaling</li>
 *   <li>Node degree → font size scaling</li>
 *   <li>Optional clustering by Edge type</li>
 *   <li>Configurable graph attributes (layout engine, rank direction, etc.)</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 *   DotExporter exporter = new DotExporter(graph);
 *   exporter.setGraphName("StudentNetwork");
 *   exporter.setColorByEdgeType(true);
 *   exporter.export(new File("graph.dot"));
 *   // or
 *   String dot = exporter.exportToString();
 * </pre>
 *
 * @author zalenix
 */
public class DotExporter {

    private final Graph<String, Edge> graph;
    private String graphName = "G";
    private String timestamp;
    private String description;
    private boolean colorByEdgeType = true;
    private boolean scaleNodesByDegree = true;
    private boolean scaleEdgesByWeight = true;
    private boolean directed = false;
    private String layoutEngine = "neato"; // hint only, stored as comment
    private String rankDir = null; // TB, LR, BT, RL
    private final Map<String, String> graphAttributes = new LinkedHashMap<>();

    // Default color palette for Edge types
    private static final Map<String, String> DEFAULT_TYPE_COLORS = new LinkedHashMap<>();
    static {
        DEFAULT_TYPE_COLORS.put("f",  "#4CAF50"); // friend = green
        DEFAULT_TYPE_COLORS.put("fs", "#2196F3"); // friend-stranger = blue
        DEFAULT_TYPE_COLORS.put("c",  "#FF9800"); // classmate = orange
        DEFAULT_TYPE_COLORS.put("s",  "#F44336"); // stranger = red
        DEFAULT_TYPE_COLORS.put("sg", "#9C27B0"); // study group = purple
    }

    private final Map<String, String> typeColors = new LinkedHashMap<>(DEFAULT_TYPE_COLORS);

    /**
     * Creates a new DOT exporter for the given graph.
     *
     * @param graph the JUNG graph to export
     * @throws IllegalArgumentException if graph is null
     */
    public DotExporter(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
    }

    /** Sets the graph name used in the DOT output. */
    public void setGraphName(String name) {
        this.graphName = (name != null) ? name.replaceAll("[^a-zA-Z0-9_]", "_") : "G";
    }

    /** Sets a timestamp comment in the output. */
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    /** Sets a description comment in the output. */
    public void setDescription(String description) { this.description = description; }

    /** Whether to color edges by their type. Default: true. */
    public void setColorByEdgeType(boolean color) { this.colorByEdgeType = color; }

    /** Whether to scale node font size by degree. Default: true. */
    public void setScaleNodesByDegree(boolean scale) { this.scaleNodesByDegree = scale; }

    /** Whether to scale Edge pen width by weight. Default: true. */
    public void setScaleEdgesByWeight(boolean scale) { this.scaleEdgesByWeight = scale; }

    /** Set the graph as directed (digraph) or undirected (graph). Default: undirected. */
    public void setDirected(boolean directed) { this.directed = directed; }

    /** Sets the suggested layout engine (neato, dot, fdp, sfdp, circo, twopi). */
    public void setLayoutEngine(String engine) { this.layoutEngine = engine; }

    /** Sets the rank direction (TB, LR, BT, RL). Only used with 'dot' engine. */
    public void setRankDir(String dir) { this.rankDir = dir; }

    /** Adds a custom graph-level attribute. */
    public void setGraphAttribute(String key, String value) {
        graphAttributes.put(key, value);
    }

    /** Override the color for a specific Edge type. */
    public void setTypeColor(String edgeType, String hexColor) {
        typeColors.put(edgeType, hexColor);
    }

    /**
     * Exports the graph to a DOT file.
     *
     * @param outputFile the file to write to
     * @throws IOException if writing fails
     * @throws SecurityException if the path escapes allowed directories (CWE-22)
     */
    public void export(File outputFile) throws IOException {
        ExportUtils.validateOutputPath(outputFile);
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
            writer.write(exportToString());
        }
    }

    /**
     * Exports the graph to a DOT-formatted string.
     *
     * @return DOT format string
     */
    public String exportToString() {
        StringBuilder sb = new StringBuilder();
        String connector = directed ? " -> " : " -- ";
        String graphType = directed ? "digraph" : "graph";

        // Header comment
        sb.append("// Generated by GraphVisual DotExporter\n");
        if (timestamp != null) {
            sb.append("// Timestamp: ").append(timestamp).append("\n");
        }
        if (description != null) {
            sb.append("// ").append(description).append("\n");
        }
        if (layoutEngine != null) {
            sb.append("// Suggested layout engine: ").append(layoutEngine).append("\n");
        }
        sb.append("// Nodes: ").append(graph.getVertexCount())
          .append(", Edges: ").append(graph.getEdgeCount()).append("\n\n");

        sb.append(graphType).append(" ").append(graphName).append(" {\n");

        // Graph attributes
        sb.append("    // Graph attributes\n");
        sb.append("    graph [overlap=false, splines=true");
        if (rankDir != null) {
            sb.append(", rankdir=").append(rankDir);
        }
        for (Map.Entry<String, String> attr : graphAttributes.entrySet()) {
            sb.append(", ").append(attr.getKey()).append("=").append(quote(attr.getValue()));
        }
        sb.append("];\n");
        sb.append("    node [shape=circle, style=filled, fillcolor=\"#333333\", fontcolor=white, fontname=\"Helvetica\"];\n");
        sb.append("    Edge [fontname=\"Helvetica\", fontsize=9];\n");
        sb.append("    bgcolor=\"#1a1a1a\";\n\n");

        // Legend as subgraph (if coloring by type)
        if (colorByEdgeType) {
            Set<String> usedTypes = new HashSet<>();
            for (Edge e : graph.getEdges()) {
                if (e.getType() != null) usedTypes.add(e.getType());
            }
            if (!usedTypes.isEmpty()) {
                sb.append("    // Edge type legend\n");
                sb.append("    subgraph cluster_legend {\n");
                sb.append("        label=\"Edge Types\";\n");
                sb.append("        fontcolor=white;\n");
                sb.append("        color=\"#555555\";\n");
                sb.append("        style=dashed;\n");
                int i = 0;
                for (String type : usedTypes) {
                    String color = typeColors.getOrDefault(type, "#CCCCCC");
                    String legendLabel = getTypeName(type);
                    sb.append("        legend_a").append(i)
                      .append(" [label=\"\", width=0.1, height=0.1, style=invis];\n");
                    sb.append("        legend_b").append(i)
                      .append(" [label=\"\", width=0.1, height=0.1, style=invis];\n");
                    sb.append("        legend_a").append(i).append(connector)
                      .append("legend_b").append(i)
                      .append(" [color=").append(quote(color))
                      .append(", label=").append(quote(legendLabel))
                      .append(", fontcolor=").append(quote(color))
                      .append(", penwidth=2];\n");
                    i++;
                }
                sb.append("    }\n\n");
            }
        }

        // Compute degree range for scaling
        int maxDegree = 1;
        if (scaleNodesByDegree) {
            for (String v : graph.getVertices()) {
                int deg = graph.degree(v);
                if (deg > maxDegree) maxDegree = deg;
            }
        }

        // Nodes
        sb.append("    // Nodes\n");
        List<String> sortedVertices = new ArrayList<>(graph.getVertices());
        Collections.sort(sortedVertices);
        for (String v : sortedVertices) {
            sb.append("    ").append(quote(v));
            int deg = graph.degree(v);
            if (scaleNodesByDegree && maxDegree > 1) {
                // Scale font from 10 to 24 based on degree
                double ratio = (double) deg / maxDegree;
                int fontSize = 10 + (int) (ratio * 14);
                double width = 0.3 + ratio * 0.7;
                sb.append(" [fontsize=").append(fontSize)
                  .append(", width=").append(String.format("%.2f", width))
                  .append(", tooltip=").append(quote(v + " (degree " + deg + ")"))
                  .append("]");
            } else {
                sb.append(" [tooltip=").append(quote(v + " (degree " + deg + ")")).append("]");
            }
            sb.append(";\n");
        }

        // Compute weight range for Edge scaling
        float minWeight = Float.MAX_VALUE, maxWeight = Float.MIN_VALUE;
        if (scaleEdgesByWeight) {
            for (Edge e : graph.getEdges()) {
                float w = e.getWeight();
                if (w < minWeight) minWeight = w;
                if (w > maxWeight) maxWeight = w;
            }
        }

        // Edges
        sb.append("\n    // Edges\n");
        Set<String> emittedEdges = new HashSet<>();
        for (Edge e : graph.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            // Deduplicate for undirected graphs
            String edgeKey = directed ? v1 + "->" + v2 :
                    (v1.compareTo(v2) < 0 ? v1 + "--" + v2 : v2 + "--" + v1);
            String typeKey = (e.getType() != null) ? edgeKey + ":" + e.getType() : edgeKey;
            if (!emittedEdges.add(typeKey)) continue;

            sb.append("    ").append(quote(v1)).append(connector).append(quote(v2));

            List<String> attrs = new ArrayList<>();

            // Color by type
            if (colorByEdgeType && e.getType() != null) {
                String color = typeColors.getOrDefault(e.getType(), "#CCCCCC");
                attrs.add("color=" + quote(color));
            }

            // Scale by weight
            if (scaleEdgesByWeight && maxWeight > minWeight) {
                float w = e.getWeight();
                double ratio = (w - minWeight) / (maxWeight - minWeight);
                double penwidth = 0.5 + ratio * 4.0;
                attrs.add("penwidth=" + String.format("%.1f", penwidth));
            }

            // Label
            if (e.getLabel() != null && !e.getLabel().isEmpty()) {
                attrs.add("label=" + quote(e.getLabel()));
            }

            // Tooltip with details
            StringBuilder tip = new StringBuilder();
            if (e.getType() != null) tip.append("type=").append(e.getType());
            tip.append(" w=").append(String.format("%.2f", e.getWeight()));
            attrs.add("tooltip=" + quote(tip.toString().trim()));

            if (!attrs.isEmpty()) {
                sb.append(" [").append(String.join(", ", attrs)).append("]");
            }
            sb.append(";\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Returns a human-readable name for an Edge type code.
     */
    private String getTypeName(String type) {
        if (type == null) return "unknown";
        switch (type) {
            case "f":  return "Friend";
            case "fs": return "Friend-Stranger";
            case "c":  return "Classmate";
            case "s":  return "Stranger";
            case "sg": return "Study Group";
            default:   return type;
        }
    }

    /**
     * Quotes a string for DOT format, escaping special characters.
     * Delegates to {@link ExportUtils#quoteDot(String)} for consistent
     * escaping across all exporters.
     */
    private static String quote(String s) {
        return ExportUtils.quoteDot(s);
    }
}
