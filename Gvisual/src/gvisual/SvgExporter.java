package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Exports a JUNG graph to SVG (Scalable Vector Graphics) format for
 * publication-quality, resolution-independent graph visualizations.
 *
 * <p>SVG output is ideal for academic papers, presentations, and web
 * embedding because it scales without pixelation. The exporter uses a
 * simple force-directed layout to position nodes, then renders them as
 * SVG circles with connecting lines.</p>
 *
 * <h3>Features</h3>
 * <ul>
 *   <li>Force-directed automatic layout (no external dependencies)</li>
 *   <li>Edge type → color mapping matching the project palette</li>
 *   <li>Edge weight → stroke width scaling</li>
 *   <li>Node degree → radius scaling</li>
 *   <li>Node labels with automatic contrast (white on dark fills)</li>
 *   <li>Optional legend for Edge types</li>
 *   <li>Dark or light theme support</li>
 *   <li>Configurable canvas dimensions and margins</li>
 *   <li>Hover tooltips via SVG &lt;title&gt; elements</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 *   SvgExporter exporter = new SvgExporter(graph);
 *   exporter.setTitle("Student Network — Day 42");
 *   exporter.setDarkTheme(true);
 *   exporter.export(new File("network.svg"));
 *   // or
 *   String svg = exporter.exportToString();
 * </pre>
 *
 * @author zalenix
 */
public class SvgExporter {

    private final Graph<String, Edge> graph;
    private int width = 800;
    private int height = 600;
    private int margin = 60;
    private String title;
    private String description;
    private boolean darkTheme = true;
    private boolean showLegend = true;
    private boolean showLabels = true;
    private boolean scaleNodesByDegree = true;
    private boolean scaleEdgesByWeight = true;
    private boolean colorByEdgeType = true;
    private int layoutIterations = 300;

    private static final Map<String, String> TYPE_COLORS = new LinkedHashMap<>();
    static {
        TYPE_COLORS.put("f",  "#4CAF50"); // Friend — green
        TYPE_COLORS.put("fs", "#2196F3"); // Familiar Stranger — blue
        TYPE_COLORS.put("c",  "#FF9800"); // Classmate — orange
        TYPE_COLORS.put("s",  "#F44336"); // Stranger — red
        TYPE_COLORS.put("sg", "#9C27B0"); // Study Group — purple
    }

    private static final Map<String, String> TYPE_NAMES = new LinkedHashMap<>();
    static {
        TYPE_NAMES.put("f",  "Friend");
        TYPE_NAMES.put("fs", "Familiar Stranger");
        TYPE_NAMES.put("c",  "Classmate");
        TYPE_NAMES.put("s",  "Stranger");
        TYPE_NAMES.put("sg", "Study Group");
    }

    private final Map<String, String> customColors = new LinkedHashMap<>();

    /**
     * Creates a new SVG exporter for the given graph.
     *
     * @param graph the JUNG graph to export
     * @throws IllegalArgumentException if graph is null
     */
    public SvgExporter(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
    }

    /** Sets canvas width in pixels. Default: 800. */
    public void setWidth(int width) { this.width = Math.max(200, width); }

    /** Sets canvas height in pixels. Default: 600. */
    public void setHeight(int height) { this.height = Math.max(200, height); }

    /** Sets margin around the graph area. Default: 60. */
    public void setMargin(int margin) { this.margin = Math.max(10, margin); }

    /** Sets a title displayed at the top of the SVG. */
    public void setTitle(String title) { this.title = title; }

    /** Sets a description embedded as SVG metadata. */
    public void setDescription(String description) { this.description = description; }

    /** Use dark background (default: true). */
    public void setDarkTheme(boolean dark) { this.darkTheme = dark; }

    /** Show Edge type legend (default: true). */
    public void setShowLegend(boolean show) { this.showLegend = show; }

    /** Show node labels (default: true). */
    public void setShowLabels(boolean show) { this.showLabels = show; }

    /** Scale node radius by degree (default: true). */
    public void setScaleNodesByDegree(boolean scale) { this.scaleNodesByDegree = scale; }

    /** Scale Edge stroke by weight (default: true). */
    public void setScaleEdgesByWeight(boolean scale) { this.scaleEdgesByWeight = scale; }

    /** Color edges by type (default: true). */
    public void setColorByEdgeType(boolean color) { this.colorByEdgeType = color; }

    /** Number of force-directed layout iterations. Default: 300. */
    public void setLayoutIterations(int iterations) {
        this.layoutIterations = Math.max(10, iterations);
    }

    /** Override color for a specific Edge type code. */
    public void setTypeColor(String type, String hexColor) {
        customColors.put(type, hexColor);
    }

    /**
     * Exports the graph to an SVG file.
     *
     * @param outputFile the file to write
     * @throws IOException if writing fails
     * @throws SecurityException if the path escapes allowed directories
     */
    public void export(File outputFile) throws IOException {
        ExportUtils.validateOutputPath(outputFile);
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
            writer.write(exportToString());
        }
    }

    /**
     * Exports the graph to an SVG string.
     *
     * @return the SVG document as a string
     */
    public String exportToString() {
        if (graph.getVertexCount() == 0) {
            return emptyGraphSvg();
        }

        // 1. Layout: force-directed positioning
        Map<String, double[]> positions = computeLayout();

        // 2. Compute scaling factors
        int maxDegree = 1;
        for (String v : graph.getVertices()) {
            maxDegree = Math.max(maxDegree, graph.degree(v));
        }
        float minWeight = Float.MAX_VALUE, maxWeight = Float.MIN_VALUE;
        for (Edge e : graph.getEdges()) {
            minWeight = Math.min(minWeight, e.getWeight());
            maxWeight = Math.max(maxWeight, e.getWeight());
        }
        if (minWeight == Float.MAX_VALUE) { minWeight = 0; maxWeight = 1; }
        if (maxWeight <= minWeight) maxWeight = minWeight + 1;

        // Collect used Edge types
        Set<String> usedTypes = new LinkedHashSet<>();
        for (Edge e : graph.getEdges()) {
            if (e.getType() != null) usedTypes.add(e.getType());
        }

        // 3. Build SVG
        StringBuilder sb = new StringBuilder();
        String bg = darkTheme ? "#1a1a2e" : "#ffffff";
        String textColor = darkTheme ? "#e0e0e0" : "#333333";
        String nodeColor = darkTheme ? "#16213e" : "#f0f0f0";
        String nodeStroke = darkTheme ? "#0f3460" : "#cccccc";
        String defaultEdgeColor = darkTheme ? "#555555" : "#999999";

        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" ");
        sb.append("width=\"").append(width).append("\" ");
        sb.append("height=\"").append(height).append("\" ");
        sb.append("viewBox=\"0 0 ").append(width).append(" ").append(height).append("\">\n");

        // Metadata
        if (description != null) {
            sb.append("  <desc>").append(escapeXml(description)).append("</desc>\n");
        }
        sb.append("  <!-- Generated by GraphVisual SvgExporter -->\n");
        sb.append("  <!-- Nodes: ").append(graph.getVertexCount());
        sb.append(", Edges: ").append(graph.getEdgeCount()).append(" -->\n\n");

        // Styles
        sb.append("  <defs>\n");
        sb.append("    <style>\n");
        sb.append("      .graph-bg { fill: ").append(bg).append("; }\n");
        sb.append("      .node-circle { fill: ").append(nodeColor);
        sb.append("; stroke: ").append(nodeStroke).append("; stroke-width: 1.5; }\n");
        sb.append("      .node-label { fill: ").append(textColor);
        sb.append("; font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; text-anchor: middle; dominant-baseline: central; }\n");
        sb.append("      .Edge-line { stroke-linecap: round; }\n");
        sb.append("      .title-text { fill: ").append(textColor);
        sb.append("; font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; font-size: 16px; font-weight: bold; }\n");
        sb.append("      .legend-text { fill: ").append(textColor);
        sb.append("; font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; font-size: 11px; }\n");
        sb.append("      .stats-text { fill: ").append(textColor);
        sb.append("; font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; font-size: 10px; opacity: 0.7; }\n");
        sb.append("    </style>\n");
        sb.append("  </defs>\n\n");

        // Background
        sb.append("  <rect class=\"graph-bg\" width=\"100%\" height=\"100%\"/>\n\n");

        // Title
        int titleOffset = 0;
        if (title != null) {
            titleOffset = 25;
            sb.append("  <text class=\"title-text\" x=\"").append(width / 2);
            sb.append("\" y=\"25\" text-anchor=\"middle\">");
            sb.append(escapeXml(title)).append("</text>\n\n");
        }

        // Edges (drawn first, beneath nodes)
        sb.append("  <g id=\"edges\">\n");
        Set<String> emitted = new HashSet<>();
        for (Edge e : graph.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            String key = v1.compareTo(v2) < 0 ? v1 + "|" + v2 : v2 + "|" + v1;
            String typeKey = (e.getType() != null) ? key + ":" + e.getType() : key;
            if (!emitted.add(typeKey)) continue;

            double[] p1 = positions.get(v1);
            double[] p2 = positions.get(v2);
            if (p1 == null || p2 == null) continue;

            String color = defaultEdgeColor;
            if (colorByEdgeType && e.getType() != null) {
                color = getColor(e.getType());
            }

            double strokeWidth = 1.0;
            if (scaleEdgesByWeight && maxWeight > minWeight) {
                double ratio = (e.getWeight() - minWeight) / (maxWeight - minWeight);
                strokeWidth = 0.5 + ratio * 3.5;
            }

            sb.append("    <line class=\"Edge-line\" ");
            sb.append("x1=\"").append(fmt(p1[0])).append("\" ");
            sb.append("y1=\"").append(fmt(p1[1])).append("\" ");
            sb.append("x2=\"").append(fmt(p2[0])).append("\" ");
            sb.append("y2=\"").append(fmt(p2[1])).append("\" ");
            sb.append("stroke=\"").append(color).append("\" ");
            sb.append("stroke-width=\"").append(fmt(strokeWidth)).append("\" ");
            sb.append("opacity=\"0.6\">");
            // Tooltip
            sb.append("<title>").append(escapeXml(v1)).append(" — ").append(escapeXml(v2));
            if (e.getType() != null) {
                sb.append(" (").append(escapeXml(getTypeName(e.getType()))).append(")");
            }
            sb.append(" w=").append(fmt(e.getWeight()));
            sb.append("</title>");
            sb.append("</line>\n");
        }
        sb.append("  </g>\n\n");

        // Nodes
        sb.append("  <g id=\"nodes\">\n");
        List<String> sortedVerts = new ArrayList<>(graph.getVertices());
        Collections.sort(sortedVerts);
        for (String v : sortedVerts) {
            double[] pos = positions.get(v);
            if (pos == null) continue;
            int deg = graph.degree(v);

            double radius = 8;
            if (scaleNodesByDegree && maxDegree > 1) {
                double ratio = (double) deg / maxDegree;
                radius = 5 + ratio * 15;
            }

            double fontSize = Math.max(7, Math.min(radius * 0.9, 14));

            sb.append("    <g>\n");
            sb.append("      <circle class=\"node-circle\" ");
            sb.append("cx=\"").append(fmt(pos[0])).append("\" ");
            sb.append("cy=\"").append(fmt(pos[1])).append("\" ");
            sb.append("r=\"").append(fmt(radius)).append("\">");
            sb.append("<title>").append(escapeXml(v)).append(" (degree ").append(deg).append(")</title>");
            sb.append("</circle>\n");

            if (showLabels) {
                sb.append("      <text class=\"node-label\" ");
                sb.append("x=\"").append(fmt(pos[0])).append("\" ");
                sb.append("y=\"").append(fmt(pos[1])).append("\" ");
                sb.append("font-size=\"").append(fmt(fontSize)).append("\">");
                sb.append(escapeXml(v));
                sb.append("</text>\n");
            }
            sb.append("    </g>\n");
        }
        sb.append("  </g>\n\n");

        // Legend
        if (showLegend && !usedTypes.isEmpty()) {
            int legendX = width - margin - 130;
            int legendY = margin + titleOffset;
            sb.append("  <g id=\"legend\">\n");
            sb.append("    <rect x=\"").append(legendX - 10).append("\" y=\"").append(legendY - 15);
            sb.append("\" width=\"140\" height=\"").append(usedTypes.size() * 20 + 25);
            sb.append("\" rx=\"4\" fill=\"").append(darkTheme ? "#16213e" : "#f5f5f5");
            sb.append("\" stroke=\"").append(darkTheme ? "#0f3460" : "#cccccc");
            sb.append("\" opacity=\"0.9\"/>\n");
            sb.append("    <text class=\"legend-text\" x=\"").append(legendX);
            sb.append("\" y=\"").append(legendY).append("\" font-weight=\"bold\">Edge Types</text>\n");
            int idx = 0;
            for (String type : usedTypes) {
                int y = legendY + 20 + idx * 20;
                String color = getColor(type);
                sb.append("    <line x1=\"").append(legendX).append("\" y1=\"").append(y);
                sb.append("\" x2=\"").append(legendX + 20).append("\" y2=\"").append(y);
                sb.append("\" stroke=\"").append(color).append("\" stroke-width=\"3\"/>\n");
                sb.append("    <text class=\"legend-text\" x=\"").append(legendX + 28);
                sb.append("\" y=\"").append(y + 4).append("\">");
                sb.append(escapeXml(getTypeName(type))).append("</text>\n");
                idx++;
            }
            sb.append("  </g>\n\n");
        }

        // Stats footer
        sb.append("  <text class=\"stats-text\" x=\"").append(margin);
        sb.append("\" y=\"").append(height - 10).append("\">");
        sb.append(graph.getVertexCount()).append(" nodes, ");
        sb.append(graph.getEdgeCount()).append(" edges");
        sb.append("</text>\n");

        sb.append("</svg>\n");
        return sb.toString();
    }

    /**
     * Computes node positions using a simple force-directed layout.
     * Fruchterman-Reingold inspired: repulsive forces between all nodes,
     * attractive forces along edges.
     */
    private Map<String, double[]> computeLayout() {
        List<String> vertices = new ArrayList<>(graph.getVertices());
        int n = vertices.size();
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) index.put(vertices.get(i), i);

        double graphW = width - 2.0 * margin;
        double graphH = height - 2.0 * margin;
        double area = graphW * graphH;
        double k = Math.sqrt(area / Math.max(n, 1)); // ideal spring length

        // Initialize positions in a circle
        double[] x = new double[n];
        double[] y = new double[n];
        double cx = width / 2.0;
        double cy = height / 2.0;
        double initRadius = Math.min(graphW, graphH) * 0.35;
        for (int i = 0; i < n; i++) {
            double angle = 2.0 * Math.PI * i / n;
            x[i] = cx + initRadius * Math.cos(angle);
            y[i] = cy + initRadius * Math.sin(angle);
        }

        double temperature = Math.min(graphW, graphH) / 4.0;
        double cooling = temperature / (layoutIterations + 1);

        for (int iter = 0; iter < layoutIterations; iter++) {
            double[] dx = new double[n];
            double[] dy = new double[n];

            // Repulsive forces between all pairs
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double ddx = x[i] - x[j];
                    double ddy = y[i] - y[j];
                    double dist = Math.sqrt(ddx * ddx + ddy * ddy);
                    if (dist < 0.01) dist = 0.01;
                    double force = (k * k) / dist;
                    double fx = (ddx / dist) * force;
                    double fy = (ddy / dist) * force;
                    dx[i] += fx;
                    dy[i] += fy;
                    dx[j] -= fx;
                    dy[j] -= fy;
                }
            }

            // Attractive forces along edges
            for (Edge e : graph.getEdges()) {
                Integer i = index.get(e.getVertex1());
                Integer j = index.get(e.getVertex2());
                if (i == null || j == null || i.equals(j)) continue;
                double ddx = x[i] - x[j];
                double ddy = y[i] - y[j];
                double dist = Math.sqrt(ddx * ddx + ddy * ddy);
                if (dist < 0.01) dist = 0.01;
                double force = (dist * dist) / k;
                double fx = (ddx / dist) * force;
                double fy = (ddy / dist) * force;
                dx[i] -= fx;
                dy[i] -= fy;
                dx[j] += fx;
                dy[j] += fy;
            }

            // Apply displacements, clamped by temperature
            for (int i = 0; i < n; i++) {
                double disp = Math.sqrt(dx[i] * dx[i] + dy[i] * dy[i]);
                if (disp < 0.01) continue;
                double scale = Math.min(disp, temperature) / disp;
                x[i] += dx[i] * scale;
                y[i] += dy[i] * scale;
                // Keep within bounds
                x[i] = Math.max(margin + 20, Math.min(width - margin - 20, x[i]));
                y[i] = Math.max(margin + 20, Math.min(height - margin - 20, y[i]));
            }

            temperature -= cooling;
            if (temperature < 0.5) break;
        }

        Map<String, double[]> positions = new HashMap<>();
        for (int i = 0; i < n; i++) {
            positions.put(vertices.get(i), new double[]{ x[i], y[i] });
        }
        return positions;
    }

    private String emptyGraphSvg() {
        String bg = darkTheme ? "#1a1a2e" : "#ffffff";
        String textColor = darkTheme ? "#e0e0e0" : "#333333";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
             + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + width
             + "\" height=\"" + height + "\">\n"
             + "  <rect width=\"100%\" height=\"100%\" fill=\"" + bg + "\"/>\n"
             + "  <text x=\"" + (width / 2) + "\" y=\"" + (height / 2)
             + "\" text-anchor=\"middle\" fill=\"" + textColor
             + "\" font-family=\"Helvetica\" font-size=\"14\">Empty graph</text>\n"
             + "</svg>\n";
    }

    private String getColor(String type) {
        if (customColors.containsKey(type)) return customColors.get(type);
        return TYPE_COLORS.getOrDefault(type, "#CCCCCC");
    }

    private String getTypeName(String type) {
        return TYPE_NAMES.getOrDefault(type, type != null ? type : "unknown");
    }

    private static String fmt(double val) {
        return String.format("%.1f", val);
    }

    /**
     * Escapes special XML characters to prevent SVG injection (CWE-74).
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
