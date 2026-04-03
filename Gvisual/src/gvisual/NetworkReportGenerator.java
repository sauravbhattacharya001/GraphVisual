package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Generates a self-contained HTML network analysis report with inline SVG charts.
 * The report includes:
 * <ul>
 *   <li>Summary statistics (nodes, edges, density, avg degree)</li>
 *   <li>Degree distribution histogram (SVG)</li>
 *   <li>Edge type breakdown pie chart (SVG)</li>
 *   <li>Top-10 nodes by degree (bar chart)</li>
 *   <li>Degree distribution table</li>
 *   <li>Network health indicators</li>
 * </ul>
 *
 * <p>The output is a single HTML file with no external dependencies —
 * suitable for sharing, printing, or embedding in presentations.</p>
 *
 * <p>Usage:</p>
 * <pre>
 *   NetworkReportGenerator gen = new NetworkReportGenerator(graph, friendEdges, fsEdges, classmateEdges, strangerEdges, studyGEdges);
 *   gen.setTitle("Campus Network Q1 2024");
 *   gen.export(new File("report.html"));
 * </pre>
 *
 * @author zalenix
 */
public class NetworkReportGenerator {

    private final Graph<String, Edge> graph;
    private final List<Edge> friendEdges;
    private final List<Edge> fsEdges;
    private final List<Edge> classmateEdges;
    private final List<Edge> strangerEdges;
    private final List<Edge> studyGEdges;
    private final Map<String, List<Edge>> edgesByType;
    private String title = "Network Analysis Report";

    /**
     * Creates a report generator using a map of edge type code → edge list.
     * This is the preferred constructor — it decouples the generator from any
     * fixed set of edge types and automatically adapts when new types are added
     * to {@link EdgeTypeRegistry}.
     *
     * @param graph       the JUNG graph to analyze
     * @param edgesByType map from type code (e.g. "f", "fs") to edge list; null entries are treated as empty
     */
    public NetworkReportGenerator(Graph<String, Edge> graph,
                                   Map<String, List<Edge>> edgesByType) {
        this.graph = graph;
        this.edgesByType = new LinkedHashMap<String, List<Edge>>();
        if (edgesByType != null) {
            for (Map.Entry<String, List<Edge>> e : edgesByType.entrySet()) {
                this.edgesByType.put(e.getKey(), e.getValue() != null ? e.getValue() : Collections.<Edge>emptyList());
            }
        }
        // Maintain backward-compatible field access for subclasses/tests
        this.friendEdges = this.edgesByType.containsKey("f") ? this.edgesByType.get("f") : Collections.<Edge>emptyList();
        this.fsEdges = this.edgesByType.containsKey("fs") ? this.edgesByType.get("fs") : Collections.<Edge>emptyList();
        this.classmateEdges = this.edgesByType.containsKey("c") ? this.edgesByType.get("c") : Collections.<Edge>emptyList();
        this.strangerEdges = this.edgesByType.containsKey("s") ? this.edgesByType.get("s") : Collections.<Edge>emptyList();
        this.studyGEdges = this.edgesByType.containsKey("sg") ? this.edgesByType.get("sg") : Collections.<Edge>emptyList();
    }

    /**
     * Legacy constructor — delegates to the map-based constructor.
     *
     * @deprecated Use {@link #NetworkReportGenerator(Graph, Map)} instead.
     */
    @Deprecated
    public NetworkReportGenerator(Graph<String, Edge> graph,
                                   List<Edge> friendEdges,
                                   List<Edge> fsEdges,
                                   List<Edge> classmateEdges,
                                   List<Edge> strangerEdges,
                                   List<Edge> studyGEdges) {
        this(graph, buildLegacyMap(friendEdges, fsEdges, classmateEdges, strangerEdges, studyGEdges));
    }

    private static Map<String, List<Edge>> buildLegacyMap(
            List<Edge> friendEdges, List<Edge> fsEdges,
            List<Edge> classmateEdges, List<Edge> strangerEdges,
            List<Edge> studyGEdges) {
        Map<String, List<Edge>> m = new LinkedHashMap<String, List<Edge>>();
        m.put("f", friendEdges);
        m.put("fs", fsEdges);
        m.put("c", classmateEdges);
        m.put("s", strangerEdges);
        m.put("sg", studyGEdges);
        return m;
    }

    /** Set the report title. */
    public void setTitle(String title) {
        this.title = title;
    }

    /** Export the report to a file. */
    public void export(File file) throws IOException {
        ExportUtils.validateOutputPath(file);
        String html = generate();
        Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        try {
            writer.write(html);
        } finally {
            writer.close();
        }
    }

    /** Generate the full HTML report as a string. */
    public String generate() {
        StringBuilder sb = new StringBuilder(16384);
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        // Compute metrics
        int nodeCount = graph.getVertexCount();
        int edgeCount = graph.getEdgeCount();
        double density = nodeCount < 2 ? 0.0 : (2.0 * edgeCount) / ((long) nodeCount * (nodeCount - 1));
        double avgDegree = nodeCount == 0 ? 0.0 : (2.0 * edgeCount) / nodeCount;

        // Degree distribution
        Map<Integer, Integer> degreeDistribution = new TreeMap<Integer, Integer>();
        Map<String, Integer> nodeDegrees = new HashMap<String, Integer>();
        int maxDegree = 0;
        int isolatedCount = 0;
        for (String v : graph.getVertices()) {
            int deg = graph.degree(v);
            nodeDegrees.put(v, deg);
            Integer count = degreeDistribution.get(deg);
            degreeDistribution.put(deg, count == null ? 1 : count + 1);
            if (deg > maxDegree) maxDegree = deg;
            if (deg == 0) isolatedCount++;
        }

        // Top 10 nodes by degree
        List<Map.Entry<String, Integer>> sortedNodes = new ArrayList<Map.Entry<String, Integer>>(nodeDegrees.entrySet());
        Collections.sort(sortedNodes, new Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue().compareTo(a.getValue());
            }
        });
        List<Map.Entry<String, Integer>> top10 = sortedNodes.subList(0, Math.min(10, sortedNodes.size()));

        // Edge type counts — driven by edgesByType map so new types are included automatically
        java.util.List<String> typeCodes = EdgeTypeRegistry.getAllTypeCodes();
        int[] edgeTypeCounts = new int[typeCodes.size()];
        String[] edgeTypeNames = new String[typeCodes.size()];
        String[] edgeTypeColors = new String[typeCodes.size()];
        for (int i = 0; i < typeCodes.size(); i++) {
            String code = typeCodes.get(i);
            edgeTypeNames[i] = EdgeTypeRegistry.getName(code);
            edgeTypeColors[i] = EdgeTypeRegistry.getHexColor(code);
            List<Edge> edges = edgesByType.get(code);
            edgeTypeCounts[i] = edges != null ? edges.size() : 0;
        }
        int totalEdgeTypes = 0;
        for (int c : edgeTypeCounts) totalEdgeTypes += c;

        // Components (simple BFS)
        int componentCount = countComponents();

        // Network health score (0-100)
        int healthScore = computeHealthScore(nodeCount, edgeCount, density, isolatedCount, componentCount);

        // HTML
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>").append(escHtml(title)).append("</title>\n");
        sb.append("<style>\n");
        sb.append(getCSS());
        sb.append("</style>\n</head>\n<body>\n");

        // Header
        sb.append("<div class=\"header\">\n");
        sb.append("<h1>").append(escHtml(title)).append("</h1>\n");
        sb.append("<p class=\"subtitle\">Generated ").append(timestamp).append("</p>\n");
        sb.append("</div>\n");

        // Health score banner
        sb.append("<div class=\"health-banner\">\n");
        sb.append("<div class=\"health-score\" style=\"--score: ").append(healthScore).append("\">\n");
        sb.append("<span class=\"score-value\">").append(healthScore).append("</span>\n");
        sb.append("<span class=\"score-label\">Network Health</span>\n");
        sb.append("</div>\n");
        sb.append("<div class=\"health-details\">\n");
        sb.append("<div class=\"health-item\"><span class=\"label\">Connectivity</span><span class=\"value\">")
          .append(componentCount == 1 ? "Fully Connected" : componentCount + " Components").append("</span></div>\n");
        sb.append("<div class=\"health-item\"><span class=\"label\">Isolated Nodes</span><span class=\"value\">")
          .append(isolatedCount).append("</span></div>\n");
        sb.append("<div class=\"health-item\"><span class=\"label\">Density</span><span class=\"value\">")
          .append(String.format("%.4f", density)).append("</span></div>\n");
        sb.append("</div>\n</div>\n");

        // Summary cards
        sb.append("<div class=\"cards\">\n");
        appendCard(sb, "Nodes", String.valueOf(nodeCount), "#4CAF50");
        appendCard(sb, "Edges", String.valueOf(edgeCount), "#2196F3");
        appendCard(sb, "Avg Degree", String.format("%.2f", avgDegree), "#FF9800");
        appendCard(sb, "Max Degree", String.valueOf(maxDegree), "#F44336");
        appendCard(sb, "Components", String.valueOf(componentCount), "#9C27B0");
        appendCard(sb, "Density", String.format("%.4f", density), "#607D8B");
        sb.append("</div>\n");

        // Charts section
        sb.append("<div class=\"charts\">\n");

        // Degree distribution histogram
        sb.append("<div class=\"chart-container\">\n");
        sb.append("<h2>Degree Distribution</h2>\n");
        sb.append(buildDegreeHistogram(degreeDistribution, maxDegree));
        sb.append("</div>\n");

        // Edge type pie chart
        sb.append("<div class=\"chart-container\">\n");
        sb.append("<h2>Edge Type Breakdown</h2>\n");
        sb.append(buildPieChart(edgeTypeCounts, edgeTypeNames, edgeTypeColors, totalEdgeTypes));
        sb.append("</div>\n");

        sb.append("</div>\n");

        // Top nodes bar chart
        sb.append("<div class=\"chart-container full-width\">\n");
        sb.append("<h2>Top 10 Most Connected Nodes</h2>\n");
        sb.append(buildTopNodesChart(top10, maxDegree));
        sb.append("</div>\n");

        // Degree distribution table
        sb.append("<div class=\"chart-container full-width\">\n");
        sb.append("<h2>Degree Frequency Table</h2>\n");
        sb.append("<table class=\"data-table\">\n<thead><tr><th>Degree</th><th>Count</th><th>Percentage</th><th>Bar</th></tr></thead>\n<tbody>\n");
        int maxFreq = 0;
        for (int freq : degreeDistribution.values()) {
            if (freq > maxFreq) maxFreq = freq;
        }
        for (Map.Entry<Integer, Integer> entry : degreeDistribution.entrySet()) {
            double pct = nodeCount > 0 ? (100.0 * entry.getValue() / nodeCount) : 0;
            double barWidth = maxFreq > 0 ? (100.0 * entry.getValue() / maxFreq) : 0;
            sb.append("<tr><td>").append(entry.getKey())
              .append("</td><td>").append(entry.getValue())
              .append("</td><td>").append(String.format("%.1f%%", pct))
              .append("</td><td><div class=\"bar\" style=\"width:").append(String.format("%.1f", barWidth))
              .append("%\"></div></td></tr>\n");
        }
        sb.append("</tbody>\n</table>\n</div>\n");

        // Footer
        sb.append("<div class=\"footer\">Generated by GraphVisual &mdash; Network Report Generator</div>\n");
        sb.append("</body>\n</html>");

        return sb.toString();
    }

    private int countComponents() {
        Set<String> visited = new HashSet<String>();
        int components = 0;
        for (String v : graph.getVertices()) {
            if (!visited.contains(v)) {
                components++;
                Queue<String> queue = new ArrayDeque<String>();
                queue.add(v);
                visited.add(v);
                while (!queue.isEmpty()) {
                    String curr = queue.poll();
                    for (String neighbor : graph.getNeighbors(curr)) {
                        if (!visited.contains(neighbor)) {
                            visited.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }
        return components;
    }

    private int computeHealthScore(int nodes, int edges, double density, int isolated, int components) {
        int score = 100;
        // Penalize for too many isolated nodes
        if (nodes > 0) {
            double isolatedRatio = (double) isolated / nodes;
            score -= (int)(isolatedRatio * 30);
        }
        // Penalize for fragmentation
        if (components > 1) {
            score -= Math.min(25, (components - 1) * 5);
        }
        // Penalize for very low density
        if (density < 0.01 && nodes > 10) {
            score -= 15;
        } else if (density < 0.05 && nodes > 10) {
            score -= 5;
        }
        // Penalize for no edges
        if (edges == 0 && nodes > 0) {
            score -= 30;
        }
        return Math.max(0, Math.min(100, score));
    }

    private void appendCard(StringBuilder sb, String label, String value, String color) {
        sb.append("<div class=\"card\" style=\"border-top: 4px solid ").append(color).append("\">\n");
        sb.append("<div class=\"card-value\">").append(value).append("</div>\n");
        sb.append("<div class=\"card-label\">").append(label).append("</div>\n");
        sb.append("</div>\n");
    }

    private String buildDegreeHistogram(Map<Integer, Integer> dist, int maxDeg) {
        if (dist.isEmpty()) return "<p>No data</p>";
        int maxCount = 0;
        for (int c : dist.values()) if (c > maxCount) maxCount = c;

        int chartW = 500, chartH = 250, pad = 50;
        int totalW = chartW + 2 * pad, totalH = chartH + 2 * pad;
        int barCount = dist.size();
        double barWidth = Math.max(4, (double) chartW / Math.max(barCount, 1) - 2);

        StringBuilder svg = new StringBuilder();
        svg.append("<svg viewBox=\"0 0 ").append(totalW).append(" ").append(totalH)
           .append("\" class=\"chart-svg\">\n");

        // Axes
        svg.append("<line x1=\"").append(pad).append("\" y1=\"").append(pad)
           .append("\" x2=\"").append(pad).append("\" y2=\"").append(pad + chartH)
           .append("\" stroke=\"#666\" stroke-width=\"1\"/>\n");
        svg.append("<line x1=\"").append(pad).append("\" y1=\"").append(pad + chartH)
           .append("\" x2=\"").append(pad + chartW).append("\" y2=\"").append(pad + chartH)
           .append("\" stroke=\"#666\" stroke-width=\"1\"/>\n");

        // Y-axis labels
        for (int i = 0; i <= 4; i++) {
            int val = (int) Math.round(maxCount * i / 4.0);
            int y = pad + chartH - (int) (chartH * i / 4.0);
            svg.append("<text x=\"").append(pad - 5).append("\" y=\"").append(y + 4)
               .append("\" text-anchor=\"end\" font-size=\"11\" fill=\"#888\">").append(val).append("</text>\n");
            svg.append("<line x1=\"").append(pad).append("\" y1=\"").append(y)
               .append("\" x2=\"").append(pad + chartW).append("\" y2=\"").append(y)
               .append("\" stroke=\"#333\" stroke-width=\"0.5\" stroke-dasharray=\"3\"/>\n");
        }

        // Bars
        int idx = 0;
        for (Map.Entry<Integer, Integer> entry : dist.entrySet()) {
            double barH = maxCount > 0 ? (double) entry.getValue() / maxCount * chartH : 0;
            double x = pad + idx * (barWidth + 2);
            double y = pad + chartH - barH;
            svg.append("<rect x=\"").append(String.format("%.1f", x)).append("\" y=\"").append(String.format("%.1f", y))
               .append("\" width=\"").append(String.format("%.1f", barWidth)).append("\" height=\"").append(String.format("%.1f", barH))
               .append("\" fill=\"#42A5F5\" rx=\"2\">\n");
            svg.append("<title>Degree ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" nodes</title>\n");
            svg.append("</rect>\n");

            // X label (show every nth to avoid crowding)
            if (barCount <= 20 || idx % (barCount / 15 + 1) == 0) {
                svg.append("<text x=\"").append(String.format("%.1f", x + barWidth / 2)).append("\" y=\"").append(pad + chartH + 15)
                   .append("\" text-anchor=\"middle\" font-size=\"10\" fill=\"#888\">").append(entry.getKey()).append("</text>\n");
            }
            idx++;
        }

        // Axis labels
        svg.append("<text x=\"").append(totalW / 2).append("\" y=\"").append(totalH - 5)
           .append("\" text-anchor=\"middle\" font-size=\"12\" fill=\"#aaa\">Degree</text>\n");
        svg.append("<text x=\"15\" y=\"").append(totalH / 2)
           .append("\" text-anchor=\"middle\" font-size=\"12\" fill=\"#aaa\" transform=\"rotate(-90,15,")
           .append(totalH / 2).append(")\">Count</text>\n");

        svg.append("</svg>\n");
        return svg.toString();
    }

    private String buildPieChart(int[] counts, String[] names, String[] colors, int total) {
        if (total == 0) return "<p>No Edge data</p>";

        StringBuilder svg = new StringBuilder();
        int size = 300;
        int cx = size / 2, cy = size / 2, r = 120;
        svg.append("<svg viewBox=\"0 0 ").append(size + 200).append(" ").append(size).append("\" class=\"chart-svg\">\n");

        double startAngle = 0;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] == 0) continue;
            double sliceAngle = (double) counts[i] / total * 2 * Math.PI;
            double endAngle = startAngle + sliceAngle;

            double x1 = cx + r * Math.cos(startAngle);
            double y1 = cy + r * Math.sin(startAngle);
            double x2 = cx + r * Math.cos(endAngle);
            double y2 = cy + r * Math.sin(endAngle);
            int largeArc = sliceAngle > Math.PI ? 1 : 0;

            svg.append("<path d=\"M ").append(cx).append(" ").append(cy)
               .append(" L ").append(String.format("%.2f", x1)).append(" ").append(String.format("%.2f", y1))
               .append(" A ").append(r).append(" ").append(r).append(" 0 ").append(largeArc).append(" 1 ")
               .append(String.format("%.2f", x2)).append(" ").append(String.format("%.2f", y2))
               .append(" Z\" fill=\"").append(colors[i]).append("\" stroke=\"#1a1a2e\" stroke-width=\"2\">\n");
            svg.append("<title>").append(names[i]).append(": ").append(counts[i])
               .append(" (").append(String.format("%.1f", 100.0 * counts[i] / total)).append("%)</title>\n");
            svg.append("</path>\n");

            startAngle = endAngle;
        }

        // Legend
        int ly = 30;
        for (int i = 0; i < names.length; i++) {
            if (counts[i] == 0) continue;
            int lx = size + 20;
            svg.append("<rect x=\"").append(lx).append("\" y=\"").append(ly)
               .append("\" width=\"14\" height=\"14\" fill=\"").append(colors[i]).append("\" rx=\"3\"/>\n");
            svg.append("<text x=\"").append(lx + 20).append("\" y=\"").append(ly + 12)
               .append("\" font-size=\"12\" fill=\"#ccc\">").append(names[i]).append(" (").append(counts[i]).append(")</text>\n");
            ly += 24;
        }

        svg.append("</svg>\n");
        return svg.toString();
    }

    private String buildTopNodesChart(List<Map.Entry<String, Integer>> top, int maxDeg) {
        if (top.isEmpty()) return "<p>No data</p>";

        int chartW = 600, barH = 28, gap = 6;
        int labelW = 100, pad = 10;
        int totalH = top.size() * (barH + gap) + 2 * pad;
        int totalW = chartW + labelW + 2 * pad;

        StringBuilder svg = new StringBuilder();
        svg.append("<svg viewBox=\"0 0 ").append(totalW).append(" ").append(totalH).append("\" class=\"chart-svg\">\n");

        int y = pad;
        for (Map.Entry<String, Integer> entry : top) {
            double barWidth = maxDeg > 0 ? (double) entry.getValue() / maxDeg * chartW : 0;
            svg.append("<text x=\"").append(labelW - 5).append("\" y=\"").append(y + barH / 2 + 5)
               .append("\" text-anchor=\"end\" font-size=\"12\" fill=\"#ccc\">Node ").append(escHtml(entry.getKey())).append("</text>\n");
            svg.append("<rect x=\"").append(labelW + pad).append("\" y=\"").append(y)
               .append("\" width=\"").append(String.format("%.1f", barWidth)).append("\" height=\"").append(barH)
               .append("\" fill=\"#66BB6A\" rx=\"4\">\n");
            svg.append("<title>Node ").append(escHtml(entry.getKey())).append(": degree ").append(entry.getValue()).append("</title>\n");
            svg.append("</rect>\n");
            svg.append("<text x=\"").append(String.format("%.1f", labelW + pad + barWidth + 5)).append("\" y=\"").append(y + barH / 2 + 5)
               .append("\" font-size=\"11\" fill=\"#aaa\">").append(entry.getValue()).append("</text>\n");
            y += barH + gap;
        }

        svg.append("</svg>\n");
        return svg.toString();
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String getCSS() {
        return "* { margin: 0; padding: 0; box-sizing: border-box; }\n"
            + "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;\n"
            + "  background: #0d1117; color: #e6edf3; padding: 20px; max-width: 1100px; margin: 0 auto; }\n"
            + ".header { text-align: center; margin-bottom: 30px; padding: 20px; }\n"
            + ".header h1 { font-size: 28px; color: #58a6ff; }\n"
            + ".subtitle { color: #8b949e; margin-top: 8px; font-size: 14px; }\n"
            + ".health-banner { display: flex; align-items: center; gap: 30px; background: #161b22;\n"
            + "  border-radius: 12px; padding: 24px; margin-bottom: 24px; border: 1px solid #30363d; }\n"
            + ".health-score { text-align: center; min-width: 100px; }\n"
            + ".score-value { font-size: 48px; font-weight: 700; color: #58a6ff; display: block; }\n"
            + ".score-label { font-size: 13px; color: #8b949e; text-transform: uppercase; letter-spacing: 1px; }\n"
            + ".health-details { display: flex; gap: 24px; flex-wrap: wrap; }\n"
            + ".health-item { display: flex; flex-direction: column; }\n"
            + ".health-item .label { font-size: 12px; color: #8b949e; text-transform: uppercase; }\n"
            + ".health-item .value { font-size: 16px; font-weight: 600; color: #e6edf3; }\n"
            + ".cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));\n"
            + "  gap: 16px; margin-bottom: 24px; }\n"
            + ".card { background: #161b22; border-radius: 10px; padding: 20px; text-align: center;\n"
            + "  border: 1px solid #30363d; }\n"
            + ".card-value { font-size: 28px; font-weight: 700; color: #e6edf3; }\n"
            + ".card-label { font-size: 13px; color: #8b949e; margin-top: 4px; text-transform: uppercase; }\n"
            + ".charts { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 24px; }\n"
            + ".chart-container { background: #161b22; border-radius: 10px; padding: 20px;\n"
            + "  border: 1px solid #30363d; }\n"
            + ".chart-container.full-width { grid-column: 1 / -1; }\n"
            + ".chart-container h2 { font-size: 16px; color: #c9d1d9; margin-bottom: 16px; }\n"
            + ".chart-svg { width: 100%; height: auto; }\n"
            + ".data-table { width: 100%; border-collapse: collapse; }\n"
            + ".data-table th { text-align: left; padding: 8px 12px; border-bottom: 2px solid #30363d;\n"
            + "  color: #8b949e; font-size: 12px; text-transform: uppercase; }\n"
            + ".data-table td { padding: 6px 12px; border-bottom: 1px solid #21262d; font-size: 13px; }\n"
            + ".data-table .bar { height: 16px; background: #42A5F5; border-radius: 3px;\n"
            + "  min-width: 2px; transition: width 0.3s; }\n"
            + ".footer { text-align: center; padding: 20px; color: #484f58; font-size: 12px; margin-top: 30px; }\n"
            + "@media (max-width: 700px) { .charts { grid-template-columns: 1fr; }\n"
            + "  .health-banner { flex-direction: column; text-align: center; } }\n"
            + "@media print { body { background: white; color: #1a1a1a; }\n"
            + "  .card, .chart-container, .health-banner { background: #f6f8fa; border-color: #d0d7de; }\n"
            + "  .header h1, .score-value { color: #0969da; }\n"
            + "  .card-value, .health-item .value { color: #1a1a1a; } }\n";
    }
}
