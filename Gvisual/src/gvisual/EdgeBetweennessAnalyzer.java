package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Edge Betweenness Centrality analyzer and HTML exporter.
 *
 * <p>Computes the betweenness centrality of every edge in the graph using
 * Brandes' algorithm adapted for edges. An edge's betweenness is the fraction
 * of all-pairs shortest paths that traverse that edge. High-betweenness edges
 * are "bridges" connecting communities — their removal fragments the network.</p>
 *
 * <h3>Features</h3>
 * <ul>
 *   <li><b>Brandes edge betweenness:</b> O(V·E) BFS-based computation</li>
 *   <li><b>Bridge detection:</b> Identifies edges whose removal disconnects components</li>
 *   <li><b>Critical edge ranking:</b> Sorted list of edges by betweenness score</li>
 *   <li><b>Community boundary detection:</b> Top-K edges likely on community boundaries</li>
 *   <li><b>HTML export:</b> Interactive report with sortable table, distribution chart,
 *       and highlighted bridge edges</li>
 * </ul>
 *
 * <h3>Applications</h3>
 * <ul>
 *   <li>Girvan-Newman community detection (iterative removal of highest-betweenness edges)</li>
 *   <li>Network vulnerability analysis</li>
 *   <li>Identifying communication bottlenecks</li>
 *   <li>IMEI/telecom link analysis</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 *   EdgeBetweennessAnalyzer analyzer = new EdgeBetweennessAnalyzer(graph);
 *   analyzer.compute();
 *   analyzer.exportHtml(new File("edge-betweenness.html"));
 *   List&lt;EdgeScore&gt; ranking = analyzer.getRanking();
 * </pre>
 *
 * @author zalenix
 */
public class EdgeBetweennessAnalyzer {

    private final Graph<String, Edge> graph;
    private Map<Edge, Double> betweenness;
    private List<EdgeScore> ranking;
    private Set<Edge> bridges;
    private boolean computed;
    private String title = "Edge Betweenness Centrality";

    /**
     * Score holder for a single edge.
     */
    public static class EdgeScore implements Comparable<EdgeScore> {
        private final Edge edge;
        private final String source;
        private final String target;
        private final double betweenness;
        private final boolean isBridge;

        public EdgeScore(Edge edge, String source, String target, double betweenness, boolean isBridge) {
            this.edge = edge;
            this.source = source;
            this.target = target;
            this.betweenness = betweenness;
            this.isBridge = isBridge;
        }

        public Edge getEdge() { return edge; }
        public String getSource() { return source; }
        public String getTarget() { return target; }
        public double getBetweenness() { return betweenness; }
        public boolean isBridge() { return isBridge; }

        @Override
        public int compareTo(EdgeScore other) {
            return Double.compare(other.betweenness, this.betweenness); // descending
        }

        @Override
        public String toString() {
            return source + " — " + target + " : " + String.format("%.4f", betweenness)
                    + (isBridge ? " [BRIDGE]" : "");
        }
    }

    public EdgeBetweennessAnalyzer(Graph<String, Edge> graph) {
        if (graph == null) throw new IllegalArgumentException("Graph must not be null");
        this.graph = graph;
        this.betweenness = new LinkedHashMap<>();
        this.ranking = new ArrayList<>();
        this.bridges = new HashSet<>();
        this.computed = false;
    }

    public void setTitle(String title) { this.title = title; }

    /**
     * Compute edge betweenness centrality using Brandes' algorithm (BFS variant).
     *
     * <p>Uses lazy initialization: only vertices reachable from each BFS source
     * are initialized, avoiding the O(V) per-source setup cost that made total
     * initialization O(V²). For sparse or disconnected graphs this is a major
     * win since each BFS touches only its connected component.</p>
     */
    public void compute() {
        betweenness.clear();
        for (Edge e : graph.getEdges()) {
            betweenness.put(e, 0.0);
        }

        Collection<String> vertices = graph.getVertices();

        for (String s : vertices) {
            // BFS from s — lazy init: only reachable vertices are tracked.
            // predecessors/dist/sigma use containsKey checks instead of
            // pre-populating entries for every vertex in the graph.
            ArrayDeque<String> stack = new ArrayDeque<>();
            Map<String, List<String>> predecessors = new HashMap<>();
            Map<String, Integer> dist = new HashMap<>();
            Map<String, Double> sigma = new HashMap<>();

            dist.put(s, 0);
            sigma.put(s, 1.0);
            predecessors.put(s, new ArrayList<>());

            Queue<String> queue = new ArrayDeque<>();
            queue.add(s);

            while (!queue.isEmpty()) {
                String v = queue.poll();
                stack.push(v);
                int distV = dist.get(v);
                double sigmaV = sigma.get(v);
                for (String w : graph.getNeighbors(v)) {
                    // First visit? (not yet in dist map)
                    if (!dist.containsKey(w)) {
                        dist.put(w, distV + 1);
                        sigma.put(w, 0.0);
                        predecessors.put(w, new ArrayList<>());
                        queue.add(w);
                    }
                    // Shortest path via v?
                    if (dist.get(w) == distV + 1) {
                        sigma.put(w, sigma.get(w) + sigmaV);
                        predecessors.get(w).add(v);
                    }
                }
            }

            // Back-propagation — only over vertices in the stack (reachable from s)
            Map<String, Double> delta = new HashMap<>(stack.size() * 2);

            while (!stack.isEmpty()) {
                String w = stack.pop();
                double deltaW = delta.getOrDefault(w, 0.0);
                for (String v : predecessors.get(w)) {
                    double c = (sigma.get(v) / sigma.get(w)) * (1.0 + deltaW);
                    // Find the edge between v and w
                    Edge edge = findEdge(v, w);
                    if (edge != null) {
                        betweenness.put(edge, betweenness.getOrDefault(edge, 0.0) + c);
                    }
                    delta.merge(v, c, Double::sum);
                }
            }
        }

        // For undirected graphs, divide by 2
        if (!isDirected()) {
            for (Edge e : betweenness.keySet()) {
                betweenness.put(e, betweenness.get(e) / 2.0);
            }
        }

        // Detect bridges
        detectBridges();

        // Build ranking
        ranking.clear();
        for (Edge e : graph.getEdges()) {
            edu.uci.ics.jung.graph.util.Pair<String> endpoints = graph.getEndpoints(e);
            String src = endpoints.getFirst();
            String tgt = endpoints.getSecond();
            ranking.add(new EdgeScore(e, src, tgt, betweenness.getOrDefault(e, 0.0), bridges.contains(e)));
        }
        Collections.sort(ranking);
        computed = true;
    }

    private Edge findEdge(String v, String w) {
        Edge e = graph.findEdge(v, w);
        if (e == null) e = graph.findEdge(w, v);
        return e;
    }

    private boolean isDirected() {
        return graph instanceof edu.uci.ics.jung.graph.DirectedGraph;
    }

    /**
     * Detect bridge edges via DFS (delegates to shared GraphUtils).
     */
    private void detectBridges() {
        bridges.clear();
        bridges.addAll(GraphUtils.findBridges(graph));
    }

    public Map<Edge, Double> getBetweenness() {
        if (!computed) compute();
        return Collections.unmodifiableMap(betweenness);
    }

    public List<EdgeScore> getRanking() {
        if (!computed) compute();
        return Collections.unmodifiableList(ranking);
    }

    public Set<Edge> getBridges() {
        if (!computed) compute();
        return Collections.unmodifiableSet(bridges);
    }

    /**
     * Get the top-K edges by betweenness.
     */
    public List<EdgeScore> getTopK(int k) {
        if (!computed) compute();
        return ranking.subList(0, Math.min(k, ranking.size()));
    }

    /**
     * Get summary statistics.
     */
    public Map<String, Object> getSummary() {
        if (!computed) compute();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalEdges", graph.getEdgeCount());
        summary.put("totalVertices", graph.getVertexCount());
        summary.put("bridgeCount", bridges.size());

        DoubleSummaryStatistics stats = betweenness.values().stream()
                .mapToDouble(Double::doubleValue).summaryStatistics();
        summary.put("maxBetweenness", stats.getMax());
        summary.put("minBetweenness", stats.getMin());
        summary.put("avgBetweenness", stats.getAverage());
        summary.put("medianBetweenness", computeMedian());
        return summary;
    }

    private double computeMedian() {
        List<Double> values = betweenness.values().stream().sorted().collect(Collectors.toList());
        if (values.isEmpty()) return 0;
        int mid = values.size() / 2;
        return values.size() % 2 == 0 ? (values.get(mid - 1) + values.get(mid)) / 2.0 : values.get(mid);
    }

    /**
     * Export an interactive HTML report.
     */
    public void exportHtml(File outputFile) throws IOException {
        if (!computed) compute();

        Map<String, Object> summary = getSummary();
        double maxBet = (double) summary.get("maxBetweenness");

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>").append(escapeHtml(title)).append("</title>\n");
        sb.append("<style>\n");
        sb.append(getCSS());
        sb.append("</style>\n</head>\n<body>\n");

        // Header
        sb.append("<div class=\"header\">\n");
        sb.append("<h1>").append(escapeHtml(title)).append("</h1>\n");
        sb.append("<p>Edge betweenness centrality analysis — identifies critical edges acting as bridges between communities</p>\n");
        sb.append("</div>\n");

        // Summary cards
        sb.append("<div class=\"cards\">\n");
        appendCard(sb, "Edges", String.valueOf(summary.get("totalEdges")), "#4a90d9");
        appendCard(sb, "Vertices", String.valueOf(summary.get("totalVertices")), "#50b86c");
        appendCard(sb, "Bridges", String.valueOf(summary.get("bridgeCount")), "#e74c3c");
        appendCard(sb, "Max Betweenness", String.format("%.2f", summary.get("maxBetweenness")), "#f39c12");
        appendCard(sb, "Avg Betweenness", String.format("%.2f", summary.get("avgBetweenness")), "#9b59b6");
        appendCard(sb, "Median", String.format("%.2f", summary.get("medianBetweenness")), "#1abc9c");
        sb.append("</div>\n");

        // Distribution histogram
        sb.append("<div class=\"section\">\n<h2>Betweenness Distribution</h2>\n");
        sb.append("<div class=\"histogram\">\n");
        int[] bins = computeHistogram(10);
        double binWidth = maxBet > 0 ? maxBet / 10.0 : 1;
        int maxBin = Arrays.stream(bins).max().orElse(1);
        for (int i = 0; i < bins.length; i++) {
            double pct = maxBin > 0 ? (bins[i] * 100.0 / maxBin) : 0;
            String label = String.format("%.1f–%.1f", i * binWidth, (i + 1) * binWidth);
            sb.append("<div class=\"bar-row\">");
            sb.append("<span class=\"bar-label\">").append(label).append("</span>");
            sb.append("<div class=\"bar-track\"><div class=\"bar-fill\" style=\"width:").append(String.format("%.1f", pct)).append("%\"></div></div>");
            sb.append("<span class=\"bar-count\">").append(bins[i]).append("</span>");
            sb.append("</div>\n");
        }
        sb.append("</div>\n</div>\n");

        // Edge ranking table
        sb.append("<div class=\"section\">\n<h2>Edge Ranking</h2>\n");
        sb.append("<table>\n<thead><tr><th>#</th><th>Source</th><th>Target</th><th>Betweenness</th><th>Relative</th><th>Bridge</th></tr></thead>\n<tbody>\n");
        int rank = 0;
        for (EdgeScore es : ranking) {
            rank++;
            double rel = maxBet > 0 ? (es.getBetweenness() / maxBet * 100.0) : 0;
            String cls = es.isBridge() ? " class=\"bridge-row\"" : "";
            sb.append("<tr").append(cls).append(">");
            sb.append("<td>").append(rank).append("</td>");
            sb.append("<td>").append(escapeHtml(es.getSource())).append("</td>");
            sb.append("<td>").append(escapeHtml(es.getTarget())).append("</td>");
            sb.append("<td>").append(String.format("%.4f", es.getBetweenness())).append("</td>");
            sb.append("<td><div class=\"meter\"><div class=\"meter-fill\" style=\"width:").append(String.format("%.1f", rel)).append("%\"></div></div></td>");
            sb.append("<td>").append(es.isBridge() ? "🌉 Yes" : "—").append("</td>");
            sb.append("</tr>\n");
            if (rank >= 200) {
                sb.append("<tr><td colspan=\"6\" style=\"text-align:center;color:#888;\">… ").append(ranking.size() - 200).append(" more edges …</td></tr>\n");
                break;
            }
        }
        sb.append("</tbody>\n</table>\n</div>\n");

        // Bridge list
        if (!bridges.isEmpty()) {
            sb.append("<div class=\"section bridge-section\">\n<h2>🌉 Bridge Edges (").append(bridges.size()).append(")</h2>\n");
            sb.append("<p>Removing any bridge edge disconnects the graph into separate components.</p>\n<ul>\n");
            for (EdgeScore es : ranking) {
                if (es.isBridge()) {
                    sb.append("<li><strong>").append(escapeHtml(es.getSource())).append(" ↔ ").append(escapeHtml(es.getTarget()));
                    sb.append("</strong> — betweenness: ").append(String.format("%.4f", es.getBetweenness())).append("</li>\n");
                }
            }
            sb.append("</ul>\n</div>\n");
        }

        sb.append("<div class=\"footer\">Generated by GraphVisual EdgeBetweennessAnalyzer</div>\n");
        sb.append("</body>\n</html>");

        ExportUtils.validateOutputPath(outputFile);
        try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
            w.write(sb.toString());
        }
    }

    private int[] computeHistogram(int numBins) {
        int[] bins = new int[numBins];
        double max = betweenness.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);
        double binWidth = max / numBins;
        if (binWidth == 0) binWidth = 1;
        for (double val : betweenness.values()) {
            int bin = (int) (val / binWidth);
            if (bin >= numBins) bin = numBins - 1;
            bins[bin]++;
        }
        return bins;
    }

    private void appendCard(StringBuilder sb, String label, String value, String color) {
        sb.append("<div class=\"card\" style=\"border-top:4px solid ").append(color).append("\">");
        sb.append("<div class=\"card-value\">").append(value).append("</div>");
        sb.append("<div class=\"card-label\">").append(label).append("</div>");
        sb.append("</div>\n");
    }

    private String escapeHtml(String s) {
        return ExportUtils.escapeHtml(s);
    }

    private String getCSS() {
        return "* { margin: 0; padding: 0; box-sizing: border-box; }\n"
            + "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0f1419; color: #e1e8ed; padding: 2rem; }\n"
            + ".header { text-align: center; margin-bottom: 2rem; }\n"
            + ".header h1 { font-size: 2rem; color: #4a90d9; }\n"
            + ".header p { color: #8899a6; margin-top: .5rem; }\n"
            + ".cards { display: flex; flex-wrap: wrap; gap: 1rem; justify-content: center; margin-bottom: 2rem; }\n"
            + ".card { background: #1c2938; border-radius: 8px; padding: 1.2rem 1.5rem; min-width: 140px; text-align: center; }\n"
            + ".card-value { font-size: 1.8rem; font-weight: 700; }\n"
            + ".card-label { color: #8899a6; font-size: .85rem; margin-top: .3rem; }\n"
            + ".section { background: #1c2938; border-radius: 8px; padding: 1.5rem; margin-bottom: 1.5rem; }\n"
            + ".section h2 { color: #4a90d9; margin-bottom: 1rem; }\n"
            + "table { width: 100%; border-collapse: collapse; }\n"
            + "th { background: #253341; padding: .6rem; text-align: left; font-size: .85rem; color: #8899a6; }\n"
            + "td { padding: .5rem .6rem; border-bottom: 1px solid #253341; font-size: .9rem; }\n"
            + "tr:hover { background: #253341; }\n"
            + ".bridge-row { background: #2d1f1f !important; }\n"
            + ".bridge-row:hover { background: #3d2929 !important; }\n"
            + ".bridge-section { border-left: 4px solid #e74c3c; }\n"
            + ".bridge-section li { margin: .4rem 0 .4rem 1.5rem; }\n"
            + ".meter { background: #253341; border-radius: 4px; height: 14px; width: 100%; }\n"
            + ".meter-fill { background: linear-gradient(90deg, #4a90d9, #e74c3c); border-radius: 4px; height: 100%; transition: width .3s; }\n"
            + ".histogram { max-width: 600px; }\n"
            + ".bar-row { display: flex; align-items: center; margin: .3rem 0; }\n"
            + ".bar-label { width: 100px; font-size: .8rem; color: #8899a6; text-align: right; padding-right: .5rem; }\n"
            + ".bar-track { flex: 1; background: #253341; border-radius: 4px; height: 18px; }\n"
            + ".bar-fill { background: #4a90d9; border-radius: 4px; height: 100%; }\n"
            + ".bar-count { width: 40px; text-align: right; font-size: .8rem; color: #8899a6; padding-left: .5rem; }\n"
            + ".footer { text-align: center; color: #556677; font-size: .75rem; margin-top: 2rem; }\n";
    }
}
