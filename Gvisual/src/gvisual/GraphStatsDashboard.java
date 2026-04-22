package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Exports a self-contained HTML statistics dashboard for a JUNG graph.
 * The dashboard visualises multiple graph analytics in one page using
 * Chart.js (loaded from CDN), including:
 *
 * <ul>
 *   <li><b>Key Metrics</b> — vertex count, edge count, density, avg degree,
 *       avg clustering coefficient, connected components</li>
 *   <li><b>Degree Distribution</b> — histogram of vertex degrees</li>
 *   <li><b>Top Vertices by Degree</b> — horizontal bar chart</li>
 *   <li><b>Clustering Coefficient Distribution</b> — histogram</li>
 *   <li><b>Edge Type Breakdown</b> — doughnut chart</li>
 *   <li><b>Degree vs Clustering Scatter</b> — scatter plot</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 *   GraphStatsDashboard dash = new GraphStatsDashboard(graph);
 *   dash.setTitle("Student Network Dashboard");
 *   dash.export(new File("dashboard.html"));
 * </pre>
 *
 * @author zalenix
 */
public class GraphStatsDashboard {

    private final Graph<String, Edge> graph;
    private String title = "Graph Statistics Dashboard";

    public GraphStatsDashboard(Graph<String, Edge> graph) {
        if (graph == null) throw new IllegalArgumentException("Graph must not be null");
        this.graph = graph;
    }

    public void setTitle(String title) { this.title = title; }

    /**
     * Exports the dashboard to a file.
     */
    public void export(File file) throws IOException {
        ExportUtils.validateOutputPath(file);
        try (Writer w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            w.write(buildHtml());
        }
    }

    /**
     * Returns the dashboard as an HTML string.
     */
    public String exportToString() {
        return buildHtml();
    }

    // ── analytics helpers ────────────────────────────────────────────

    private Map<Integer, Integer> degreeDistribution() {
        Map<Integer, Integer> dist = new TreeMap<>();
        for (String v : graph.getVertices()) {
            int d = graph.degree(v);
            dist.merge(d, 1, Integer::sum);
        }
        return dist;
    }

    private List<Map.Entry<String, Integer>> topVerticesByDegree(int n) {
        Map<String, Integer> degrees = new HashMap<>();
        for (String v : graph.getVertices()) {
            degrees.put(v, graph.degree(v));
        }
        return degrees.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    private double clusteringCoefficient(String v) {
        return GraphUtils.clusteringCoefficient(graph, v);
    }

    private Map<String, Integer> edgeTypeBreakdown() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Edge e : graph.getEdges()) {
            String type = e.getType();
            if (type == null || type.isEmpty()) type = "unknown";
            counts.merge(type, 1, Integer::sum);
        }
        return counts;
    }

    private double graphDensity() {
        int n = graph.getVertexCount();
        if (n < 2) return 0;
        return (2.0 * graph.getEdgeCount()) / (n * (n - 1));
    }

    private double avgDegree() {
        int n = graph.getVertexCount();
        if (n == 0) return 0;
        return (2.0 * graph.getEdgeCount()) / n;
    }

    private double avgClusteringCoefficient() {
        return GraphUtils.avgClusteringCoefficient(graph);
    }

    private int connectedComponents() {
        return GraphUtils.findComponents(graph).size();
    }

    // ── clustering coefficient histogram bins ────────────────────────
    private Map<String, Integer> clusteringHistogram() {
        int[] bins = new int[10]; // 0.0-0.1, 0.1-0.2, ... 0.9-1.0
        for (String v : graph.getVertices()) {
            double cc = clusteringCoefficient(v);
            int idx = Math.min((int)(cc * 10), 9);
            bins[idx]++;
        }
        Map<String, Integer> hist = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) {
            String label = String.format("%.1f-%.1f", i * 0.1, (i + 1) * 0.1);
            hist.put(label, bins[i]);
        }
        return hist;
    }

    // ── HTML builder ─────────────────────────────────────────────────

    private String buildHtml() {
        StringBuilder sb = new StringBuilder();
        Map<Integer, Integer> degreeDist = degreeDistribution();
        List<Map.Entry<String, Integer>> topDeg = topVerticesByDegree(15);
        Map<String, Integer> edgeTypes = edgeTypeBreakdown();
        Map<String, Integer> ccHist = clusteringHistogram();

        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>").append(esc(title)).append("</title>\n");
        sb.append("<script src=\"https://cdn.jsdelivr.net/npm/chart.js@4\"></script>\n");
        sb.append("<style>\n");
        sb.append(CSS);
        sb.append("</style>\n</head>\n<body>\n");

        // header
        sb.append("<header><h1>").append(esc(title)).append("</h1></header>\n");

        // metrics cards
        sb.append("<section class=\"cards\">\n");
        appendCard(sb, "Vertices", String.valueOf(graph.getVertexCount()));
        appendCard(sb, "Edges", String.valueOf(graph.getEdgeCount()));
        appendCard(sb, "Density", String.format("%.4f", graphDensity()));
        appendCard(sb, "Avg Degree", String.format("%.2f", avgDegree()));
        appendCard(sb, "Avg Clustering", String.format("%.4f", avgClusteringCoefficient()));
        appendCard(sb, "Components", String.valueOf(connectedComponents()));
        sb.append("</section>\n");

        // charts grid
        sb.append("<section class=\"charts\">\n");

        // degree distribution
        sb.append("<div class=\"chart-box\"><canvas id=\"degreeDist\"></canvas></div>\n");
        // top vertices
        sb.append("<div class=\"chart-box\"><canvas id=\"topDeg\"></canvas></div>\n");
        // clustering histogram
        sb.append("<div class=\"chart-box\"><canvas id=\"ccHist\"></canvas></div>\n");
        // edge types
        sb.append("<div class=\"chart-box\"><canvas id=\"edgeTypes\"></canvas></div>\n");
        // degree vs clustering scatter
        sb.append("<div class=\"chart-box wide\"><canvas id=\"scatter\"></canvas></div>\n");

        sb.append("</section>\n");

        // script
        sb.append("<script>\n");

        // degree distribution chart
        sb.append("new Chart(document.getElementById('degreeDist'),{type:'bar',data:{");
        sb.append("labels:").append(jsonArray(degreeDist.keySet().stream()
                .map(String::valueOf).collect(Collectors.toList()))).append(",");
        sb.append("datasets:[{label:'Vertex Count',data:").append(jsonIntArray(degreeDist.values()))
                .append(",backgroundColor:'rgba(54,162,235,0.7)'}]},");
        sb.append("options:{responsive:true,plugins:{title:{display:true,text:'Degree Distribution'}},");
        sb.append("scales:{x:{title:{display:true,text:'Degree'}},y:{title:{display:true,text:'Count'}}}}});\n");

        // top vertices by degree
        sb.append("new Chart(document.getElementById('topDeg'),{type:'bar',data:{");
        sb.append("labels:").append(jsonArray(topDeg.stream()
                .map(Map.Entry::getKey).collect(Collectors.toList()))).append(",");
        sb.append("datasets:[{label:'Degree',data:").append(jsonIntArray(topDeg.stream()
                .map(Map.Entry::getValue).collect(Collectors.toList())))
                .append(",backgroundColor:'rgba(255,99,132,0.7)'}]},");
        sb.append("options:{indexAxis:'y',responsive:true,plugins:{title:{display:true,text:'Top Vertices by Degree'}}}});\n");

        // clustering coefficient histogram
        sb.append("new Chart(document.getElementById('ccHist'),{type:'bar',data:{");
        sb.append("labels:").append(jsonArray(new ArrayList<>(ccHist.keySet()))).append(",");
        sb.append("datasets:[{label:'Vertex Count',data:").append(jsonIntArray(ccHist.values()))
                .append(",backgroundColor:'rgba(75,192,192,0.7)'}]},");
        sb.append("options:{responsive:true,plugins:{title:{display:true,text:'Clustering Coefficient Distribution'}},");
        sb.append("scales:{x:{title:{display:true,text:'CC Range'}},y:{title:{display:true,text:'Count'}}}}});\n");

        // edge type doughnut
        sb.append("new Chart(document.getElementById('edgeTypes'),{type:'doughnut',data:{");
        sb.append("labels:").append(jsonArray(new ArrayList<>(edgeTypes.keySet()))).append(",");
        sb.append("datasets:[{data:").append(jsonIntArray(edgeTypes.values()))
                .append(",backgroundColor:['#FF6384','#36A2EB','#FFCE56','#4BC0C0','#9966FF','#FF9F40','#C9CBCF']}]},");
        sb.append("options:{responsive:true,plugins:{title:{display:true,text:'Edge Type Breakdown'}}}});\n");

        // scatter: degree vs clustering
        sb.append("new Chart(document.getElementById('scatter'),{type:'scatter',data:{");
        sb.append("datasets:[{label:'Degree vs Clustering',data:[");
        boolean first = true;
        for (String v : graph.getVertices()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{x:").append(graph.degree(v))
              .append(",y:").append(String.format("%.4f", clusteringCoefficient(v))).append("}");
        }
        sb.append("],backgroundColor:'rgba(153,102,255,0.6)',pointRadius:4}]},");
        sb.append("options:{responsive:true,plugins:{title:{display:true,text:'Degree vs Clustering Coefficient'}},");
        sb.append("scales:{x:{title:{display:true,text:'Degree'}},y:{title:{display:true,text:'Clustering Coeff'}}}}});\n");

        sb.append("</script>\n</body>\n</html>");
        return sb.toString();
    }

    private void appendCard(StringBuilder sb, String label, String value) {
        sb.append("<div class=\"card\"><div class=\"card-value\">")
          .append(esc(value)).append("</div><div class=\"card-label\">")
          .append(esc(label)).append("</div></div>\n");
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String jsonArray(List<String> items) {
        return "[" + items.stream().map(s -> "\"" + escJs(s) + "\"")
                .collect(Collectors.joining(",")) + "]";
    }

    /**
     * Escapes a string for safe embedding in a JavaScript string literal
     * inside a {@code <script>} block. Prevents {@code </script>} breakout
     * (CWE-79) and handles special characters.
     */
    private static String escJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("<", "\\x3c")
                .replace(">", "\\x3e");
    }

    private static String jsonIntArray(Collection<Integer> values) {
        return "[" + values.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]";
    }

    private static final String CSS =
        "* { margin: 0; padding: 0; box-sizing: border-box; }\n" +
        "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;\n" +
        "  background: #f0f2f5; color: #333; padding: 20px; }\n" +
        "header { text-align: center; margin-bottom: 24px; }\n" +
        "header h1 { font-size: 1.8rem; color: #1a1a2e; }\n" +
        ".cards { display: flex; flex-wrap: wrap; gap: 16px; justify-content: center; margin-bottom: 24px; }\n" +
        ".card { background: #fff; border-radius: 12px; padding: 20px 28px; min-width: 140px;\n" +
        "  text-align: center; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }\n" +
        ".card-value { font-size: 1.6rem; font-weight: 700; color: #16213e; }\n" +
        ".card-label { font-size: 0.85rem; color: #888; margin-top: 4px; }\n" +
        ".charts { display: grid; grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));\n" +
        "  gap: 20px; }\n" +
        ".chart-box { background: #fff; border-radius: 12px; padding: 20px;\n" +
        "  box-shadow: 0 2px 8px rgba(0,0,0,0.08); }\n" +
        ".chart-box.wide { grid-column: 1 / -1; }\n" +
        "@media (max-width: 600px) { .charts { grid-template-columns: 1fr; } }\n";
}
