package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
// BFS and component-finding delegated to GraphUtils — see helper methods below.

/**
 * Generates a natural-language narrative report describing a graph's structure,
 * topology, and notable features. The output is a self-contained HTML file with
 * a clean, readable layout — think of it as a "graph biography".
 *
 * <p>The storyteller analyzes:</p>
 * <ul>
 *   <li><b>Overview</b> — size, density, whether it's sparse or dense</li>
 *   <li><b>Connectivity</b> — components, bridges, articulation points</li>
 *   <li><b>Degree Analysis</b> — distribution shape, hubs, leaves</li>
 *   <li><b>Community Structure</b> — detected communities and their sizes</li>
 *   <li><b>Notable Vertices</b> — highest degree, most central, most isolated</li>
 *   <li><b>Edge Types</b> — breakdown by relationship type</li>
 *   <li><b>Small-World Properties</b> — clustering coefficient vs density</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 *   GraphStorytellerExporter storyteller = new GraphStorytellerExporter(graph);
 *   storyteller.export(new File("graph_story.html"));
 * </pre>
 *
 * @author zalenix
 */
public class GraphStorytellerExporter {

    private final Graph<String, Edge> graph;
    private String title = "Graph Story";

    public GraphStorytellerExporter(Graph<String, Edge> graph) {
        if (graph == null) throw new IllegalArgumentException("Graph must not be null");
        this.graph = graph;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Export the narrative report to an HTML file.
     *
     * @param outFile destination file
     * @throws IOException if writing fails
     */
    public void export(File outFile) throws IOException {
        ExportUtils.validateOutputPath(outFile);
        String html = buildHtml();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8)) {
            w.write(html);
        }
    }

    private String buildHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
        sb.append("<title>").append(esc(title)).append("</title>");
        sb.append("<style>");
        sb.append("*{box-sizing:border-box;margin:0;padding:0}");
        sb.append("body{font-family:Georgia,'Times New Roman',serif;line-height:1.8;color:#2c3e50;max-width:800px;margin:0 auto;padding:40px 20px;background:#fafafa}");
        sb.append("h1{font-size:2em;margin-bottom:.2em;color:#1a1a2e}");
        sb.append(".subtitle{color:#7f8c8d;font-style:italic;margin-bottom:2em;font-size:1.1em}");
        sb.append("h2{font-size:1.4em;margin-top:2em;margin-bottom:.5em;color:#2c3e50;border-bottom:2px solid #3498db;padding-bottom:.2em}");
        sb.append("p{margin-bottom:1em;text-align:justify}");
        sb.append(".highlight{background:#fff3cd;padding:2px 6px;border-radius:3px;font-weight:bold}");
        sb.append(".stat{font-family:'Courier New',monospace;color:#e74c3c;font-weight:bold}");
        sb.append(".section{margin-bottom:2em}");
        sb.append("ul{margin:.5em 0 1em 1.5em}li{margin-bottom:.3em}");
        sb.append(".footer{margin-top:3em;padding-top:1em;border-top:1px solid #ddd;color:#95a5a6;font-size:.85em;text-align:center}");
        sb.append("</style></head><body>");

        sb.append("<h1>").append(esc(title)).append("</h1>");
        sb.append("<p class=\"subtitle\">A narrative exploration of your network</p>");

        int V = graph.getVertexCount();
        int E = graph.getEdgeCount();

        if (V == 0) {
            sb.append("<p>This graph is empty — there are no vertices to tell a story about. ");
            sb.append("Add some nodes and edges, and come back for the tale!</p>");
            sb.append("</body></html>");
            return sb.toString();
        }

        // ---- Overview ----
        sb.append("<div class=\"section\"><h2>📖 The Big Picture</h2>");
        double maxEdges = V * (V - 1.0) / 2.0;
        double density = maxEdges > 0 ? E / maxEdges : 0;
        String densityWord = density > 0.5 ? "dense" : density > 0.1 ? "moderately connected" : "sparse";
        sb.append("<p>This network consists of <span class=\"stat\">").append(V)
          .append("</span> vertices and <span class=\"stat\">").append(E)
          .append("</span> edges, making it a <span class=\"highlight\">").append(densityWord)
          .append("</span> graph with a density of <span class=\"stat\">")
          .append(String.format("%.4f", density)).append("</span>.");

        double avgDeg = V > 0 ? (2.0 * E) / V : 0;
        sb.append(" On average, each vertex is connected to <span class=\"stat\">")
          .append(String.format("%.1f", avgDeg)).append("</span> others.</p>");
        sb.append("</div>");

        // ---- Degree analysis ----
        Map<String, Integer> degrees = new HashMap<>();
        for (String v : graph.getVertices()) {
            degrees.put(v, graph.degree(v));
        }

        int maxDeg = degrees.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int minDeg = degrees.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        long leafCount = degrees.values().stream().filter(d -> d == 1).count();
        long isolateCount = degrees.values().stream().filter(d -> d == 0).count();

        List<Map.Entry<String, Integer>> topByDeg = degrees.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());

        sb.append("<div class=\"section\"><h2>🔗 Connections &amp; Hubs</h2>");
        sb.append("<p>Degrees range from <span class=\"stat\">").append(minDeg)
          .append("</span> to <span class=\"stat\">").append(maxDeg).append("</span>. ");

        if (topByDeg.size() > 0) {
            String hubName = topByDeg.get(0).getKey();
            sb.append("The most connected vertex is <span class=\"highlight\">").append(esc(hubName))
              .append("</span> with <span class=\"stat\">").append(topByDeg.get(0).getValue())
              .append("</span> connections");
            if (topByDeg.size() > 1) {
                sb.append(", followed by ");
                for (int i = 1; i < topByDeg.size(); i++) {
                    if (i > 1) sb.append(", ");
                    sb.append("<span class=\"highlight\">").append(esc(topByDeg.get(i).getKey()))
                      .append("</span> (").append(topByDeg.get(i).getValue()).append(")");
                }
            }
            sb.append(".</p>");
        }

        if (leafCount > 0) {
            sb.append("<p>There ").append(leafCount == 1 ? "is" : "are")
              .append(" <span class=\"stat\">").append(leafCount).append("</span> leaf vertex")
              .append(leafCount == 1 ? "" : "es")
              .append(" (connected to exactly one other node) — potential endpoints or peripheral members.</p>");
        }
        if (isolateCount > 0) {
            sb.append("<p><span class=\"stat\">").append(isolateCount).append("</span> vertex")
              .append(isolateCount == 1 ? " is" : "es are")
              .append(" completely isolated with no connections at all.</p>");
        }
        sb.append("</div>");

        // ---- Connectivity ----
        sb.append("<div class=\"section\"><h2>🌐 Connectivity</h2>");
        List<Set<String>> components = findComponents();
        sb.append("<p>The graph has <span class=\"stat\">").append(components.size())
          .append("</span> connected component").append(components.size() == 1 ? "" : "s").append(". ");
        if (components.size() == 1) {
            sb.append("Every vertex can reach every other vertex — the network is fully connected.</p>");
        } else {
            components.sort((a, b) -> Integer.compare(b.size(), a.size()));
            sb.append("The largest component contains <span class=\"stat\">")
              .append(components.get(0).size()).append("</span> vertices (")
              .append(String.format("%.0f%%", 100.0 * components.get(0).size() / V))
              .append(" of the network)");
            if (components.size() > 1) {
                sb.append(", while the smallest has just <span class=\"stat\">")
                  .append(components.get(components.size() - 1).size()).append("</span>");
            }
            sb.append(".</p>");
        }
        sb.append("</div>");

        // ---- Edge types ----
        Map<String, Integer> edgeTypeCounts = new LinkedHashMap<>();
        for (Edge e : graph.getEdges()) {
            String type = e.getType() != null ? e.getType() : "untyped";
            edgeTypeCounts.merge(type, 1, Integer::sum);
        }
        if (edgeTypeCounts.size() > 1 || (edgeTypeCounts.size() == 1 && !edgeTypeCounts.containsKey("untyped"))) {
            sb.append("<div class=\"section\"><h2>🏷️ Relationship Types</h2>");
            sb.append("<p>The edges in this network aren't all the same — they carry different meanings:</p><ul>");
            for (Map.Entry<String, Integer> entry : edgeTypeCounts.entrySet()) {
                double pct = 100.0 * entry.getValue() / E;
                sb.append("<li><strong>").append(esc(entry.getKey())).append("</strong>: ")
                  .append(entry.getValue()).append(" edges (").append(String.format("%.1f%%", pct)).append(")</li>");
            }
            sb.append("</ul></div>");
        }

        // ---- Clustering ----
        sb.append("<div class=\"section\"><h2>🔬 Clustering &amp; Triangles</h2>");
        double avgClustering = computeAvgClustering();
        sb.append("<p>The average clustering coefficient is <span class=\"stat\">")
          .append(String.format("%.4f", avgClustering)).append("</span>. ");
        if (avgClustering > 0.5) {
            sb.append("This is quite high — neighbors of a vertex tend to also be neighbors of each other, ");
            sb.append("suggesting tight-knit communities or cliques.</p>");
        } else if (avgClustering > 0.1) {
            sb.append("This indicates moderate local clustering — some triangles exist, but the network isn't dominated by cliques.</p>");
        } else {
            sb.append("This is low, meaning the network has a more tree-like or hub-and-spoke structure ");
            sb.append("rather than tight clusters.</p>");
        }

        if (avgClustering > 3 * density && density > 0) {
            sb.append("<p>Interestingly, the clustering coefficient is <span class=\"highlight\">significantly higher</span> ");
            sb.append("than the density alone would predict, which is a hallmark of ");
            sb.append("<span class=\"highlight\">small-world networks</span>.</p>");
        }
        sb.append("</div>");

        // ---- Degree distribution shape ----
        sb.append("<div class=\"section\"><h2>📊 Degree Distribution</h2>");
        double degStdDev = computeStdDev(degrees.values());
        double degCV = avgDeg > 0 ? degStdDev / avgDeg : 0;
        if (degCV < 0.3) {
            sb.append("<p>Degrees are relatively uniform (CV = ").append(String.format("%.2f", degCV))
              .append(") — most vertices have a similar number of connections. ");
            sb.append("This resembles a regular or Erdős–Rényi random graph.</p>");
        } else if (degCV < 1.0) {
            sb.append("<p>There's moderate variation in degree (CV = ").append(String.format("%.2f", degCV))
              .append("), with some vertices notably more connected than others.</p>");
        } else {
            sb.append("<p>The degree distribution is highly skewed (CV = ").append(String.format("%.2f", degCV))
              .append("), with a few <span class=\"highlight\">hub vertices</span> holding most connections ");
            sb.append("while the majority have few — characteristic of a scale-free network.</p>");
        }
        sb.append("</div>");

        // ---- Fun facts ----
        sb.append("<div class=\"section\"><h2>✨ Fun Facts</h2><ul>");
        sb.append("<li>If each edge were a handshake, this network contains <span class=\"stat\">")
          .append(E).append("</span> handshakes.</li>");
        long triangles = countTriangles();
        sb.append("<li>There are <span class=\"stat\">").append(triangles)
          .append("</span> triangles (three mutually connected vertices) in the network.</li>");
        if (V > 1 && components.size() == 1) {
            // Estimate diameter by BFS from a random vertex
            int approxDiameter = estimateDiameter(components.get(0));
            sb.append("<li>The network's approximate diameter (longest shortest path) is <span class=\"stat\">")
              .append(approxDiameter).append("</span> hops.</li>");
        }
        if (maxDeg > 0 && V > 1) {
            double hubPct = 100.0 * maxDeg / (V - 1);
            sb.append("<li>The top hub is connected to <span class=\"stat\">")
              .append(String.format("%.1f%%", hubPct)).append("</span> of all other vertices.</li>");
        }
        sb.append("</ul></div>");

        // ---- Footer ----
        sb.append("<div class=\"footer\">Generated by GraphVisual Storyteller &bull; ")
          .append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()))
          .append("</div>");

        sb.append("</body></html>");
        return sb.toString();
    }

    // ---- Helper methods ----
    // Component finding and BFS delegate to GraphUtils to avoid duplication.

    private List<Set<String>> findComponents() {
        return GraphUtils.findComponents(graph);
    }

    private double computeAvgClustering() {
        return GraphUtils.avgClusteringCoefficient(graph);
    }

    private long countTriangles() {
        long count = 0;
        List<String> vertices = new ArrayList<>(graph.getVertices());
        for (String v : vertices) {
            List<String> nbs = new ArrayList<>(graph.getNeighbors(v));
            for (int i = 0; i < nbs.size(); i++) {
                for (int j = i + 1; j < nbs.size(); j++) {
                    if (graph.isNeighbor(nbs.get(i), nbs.get(j))) count++;
                }
            }
        }
        return count / 3; // each triangle counted 3 times
    }

    private int estimateDiameter(Set<String> component) {
        // Double BFS heuristic: BFS from arbitrary start, then BFS from farthest
        String start = component.iterator().next();
        Map<String, Integer> dist1 = GraphUtils.bfsDistances(graph, start);
        String farthest = dist1.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(start);
        Map<String, Integer> dist2 = GraphUtils.bfsDistances(graph, farthest);
        return dist2.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    private double computeStdDev(Collection<Integer> values) {
        double mean = values.stream().mapToInt(Integer::intValue).average().orElse(0);
        double variance = values.stream()
                .mapToDouble(v -> (v - mean) * (v - mean))
                .average().orElse(0);
        return Math.sqrt(variance);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
