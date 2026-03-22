package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.algorithms.shortestpath.UnweightedShortestPath;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Diagnoses graph quality issues and generates an actionable health report.
 * Checks for isolated nodes, self-loops, parallel edges, disconnected
 * components, degree anomalies, and bridge edges.
 *
 * <p>Usage:
 * <pre>
 *   GraphHealthChecker checker = new GraphHealthChecker(graph);
 *   GraphHealthChecker.HealthReport report = checker.analyze();
 *   System.out.println(report.toText());
 *   // or export:
 *   checker.exportHtml(report, "health-report.html");
 * </pre>
 *
 * @author zalenix
 */
public class GraphHealthChecker {

    private final Graph<String, Edge> graph;

    public GraphHealthChecker(Graph<String, Edge> graph) {
        this.graph = Objects.requireNonNull(graph, "graph must not be null");
    }

    /** Run all health checks and return a report. */
    public HealthReport analyze() {
        HealthReport r = new HealthReport();
        r.nodeCount = graph.getVertexCount();
        r.edgeCount = graph.getEdgeCount();
        r.isolatedNodes = findIsolatedNodes();
        r.selfLoops = findSelfLoops();
        r.parallelEdgePairs = findParallelEdges();
        r.componentSizes = findComponentSizes();
        r.degreeOutliers = findDegreeOutliers();
        r.bridges = findBridges();
        r.score = computeScore(r);
        return r;
    }

    // ── Checks ─────────────────────────────────────────────

    private List<String> findIsolatedNodes() {
        return graph.getVertices().stream()
                .filter(v -> graph.degree(v) == 0)
                .sorted()
                .collect(Collectors.toList());
    }

    private List<Edge> findSelfLoops() {
        List<Edge> loops = new ArrayList<>();
        for (Edge e : graph.getEdges()) {
            Collection<String> endpoints = graph.getEndpoints(e);
            if (endpoints != null && endpoints.size() == 1) {
                loops.add(e);
            } else if (endpoints != null) {
                Iterator<String> it = endpoints.iterator();
                String a = it.next();
                String b = it.hasNext() ? it.next() : a;
                if (a.equals(b)) loops.add(e);
            }
        }
        return loops;
    }

    private List<String[]> findParallelEdges() {
        Set<String> seen = new HashSet<>();
        List<String[]> duplicates = new ArrayList<>();
        for (Edge e : graph.getEdges()) {
            Collection<String> ep = graph.getEndpoints(e);
            if (ep == null || ep.size() < 2) continue;
            Iterator<String> it = ep.iterator();
            String a = it.next(), b = it.next();
            String key = a.compareTo(b) <= 0 ? a + "||" + b : b + "||" + a;
            if (!seen.add(key)) {
                duplicates.add(new String[]{a, b});
            }
        }
        return duplicates;
    }

    private List<Integer> findComponentSizes() {
        Set<String> visited = new HashSet<>();
        List<Integer> sizes = new ArrayList<>();
        for (String v : graph.getVertices()) {
            if (visited.contains(v)) continue;
            int size = bfsCount(v, visited);
            sizes.add(size);
        }
        sizes.sort(Collections.reverseOrder());
        return sizes;
    }

    private int bfsCount(String start, Set<String> visited) {
        Queue<String> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start);
        int count = 0;
        while (!queue.isEmpty()) {
            String v = queue.poll();
            count++;
            for (String n : graph.getNeighbors(v)) {
                if (visited.add(n)) queue.add(n);
            }
        }
        return count;
    }

    /** Nodes whose degree is >2 standard deviations from the mean. */
    private List<String> findDegreeOutliers() {
        if (graph.getVertexCount() < 3) return Collections.emptyList();
        Map<String, Integer> degrees = new HashMap<>();
        double sum = 0;
        for (String v : graph.getVertices()) {
            int d = graph.degree(v);
            degrees.put(v, d);
            sum += d;
        }
        double mean = sum / graph.getVertexCount();
        double variance = 0;
        for (int d : degrees.values()) variance += (d - mean) * (d - mean);
        double std = Math.sqrt(variance / graph.getVertexCount());
        if (std < 1) return Collections.emptyList();
        double threshold = mean + 2 * std;
        return degrees.entrySet().stream()
                .filter(e -> e.getValue() > threshold)
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /** Bridge edges whose removal would increase component count. */
    private List<Edge> findBridges() {
        // Tarjan's bridge-finding via DFS
        List<Edge> bridges = new ArrayList<>();
        if (graph.getVertexCount() == 0) return bridges;

        Map<String, Integer> disc = new HashMap<>();
        Map<String, Integer> low = new HashMap<>();
        Set<String> visited = new HashSet<>();
        int[] timer = {0};

        for (String v : graph.getVertices()) {
            if (!visited.contains(v)) {
                bridgeDfs(v, null, disc, low, visited, timer, bridges);
            }
        }
        return bridges;
    }

    private void bridgeDfs(String u, String parent,
                           Map<String, Integer> disc, Map<String, Integer> low,
                           Set<String> visited, int[] timer, List<Edge> bridges) {
        visited.add(u);
        disc.put(u, timer[0]);
        low.put(u, timer[0]);
        timer[0]++;

        for (String v : graph.getNeighbors(u)) {
            if (!visited.contains(v)) {
                bridgeDfs(v, u, disc, low, visited, timer, bridges);
                low.put(u, Math.min(low.get(u), low.get(v)));
                if (low.get(v) > disc.get(u)) {
                    Edge bridgeEdge = graph.findEdge(u, v);
                    if (bridgeEdge != null) bridges.add(bridgeEdge);
                }
            } else if (!v.equals(parent)) {
                low.put(u, Math.min(low.get(u), disc.get(v)));
            }
        }
    }

    /** Score 0-100 (100 = perfectly healthy). */
    private int computeScore(HealthReport r) {
        int score = 100;
        if (r.nodeCount == 0) return 0;
        // Penalty for isolated nodes
        double isolatedPct = (double) r.isolatedNodes.size() / r.nodeCount;
        score -= (int)(isolatedPct * 30);
        // Penalty for self-loops
        score -= Math.min(r.selfLoops.size() * 5, 15);
        // Penalty for parallel edges
        score -= Math.min(r.parallelEdgePairs.size() * 3, 10);
        // Penalty for fragmentation (many components)
        if (r.componentSizes.size() > 1) {
            score -= Math.min((r.componentSizes.size() - 1) * 5, 20);
        }
        // Penalty for many bridges (fragile graph)
        if (r.edgeCount > 0) {
            double bridgePct = (double) r.bridges.size() / r.edgeCount;
            score -= (int)(bridgePct * 25);
        }
        return Math.max(0, Math.min(100, score));
    }

    // ── Export ──────────────────────────────────────────────

    /** Export report as a standalone HTML file. */
    public static String toHtml(HealthReport r) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'>");
        sb.append("<title>Graph Health Report</title><style>");
        sb.append("body{font-family:system-ui,sans-serif;max-width:800px;margin:2em auto;padding:0 1em;color:#222}");
        sb.append("h1{border-bottom:2px solid #333}.score{font-size:3em;font-weight:bold;text-align:center;padding:.5em;border-radius:12px;margin:1em 0}");
        sb.append(".good{background:#d4edda;color:#155724}.warn{background:#fff3cd;color:#856404}.bad{background:#f8d7da;color:#721c24}");
        sb.append("table{border-collapse:collapse;width:100%}th,td{text-align:left;padding:6px 10px;border-bottom:1px solid #ddd}");
        sb.append("th{background:#f5f5f5}.issue{padding:.6em;margin:.5em 0;border-left:4px solid #ffc107;background:#fffbe6}");
        sb.append(".ok{border-left-color:#28a745;background:#eafbef}");
        sb.append("</style></head><body>");
        sb.append("<h1>\uD83E\uDE7A Graph Health Report</h1>");

        // Score
        String cls = r.score >= 80 ? "good" : r.score >= 50 ? "warn" : "bad";
        sb.append(String.format("<div class='score %s'>%d / 100</div>", cls, r.score));

        // Summary table
        sb.append("<table><tr><th>Metric</th><th>Value</th></tr>");
        sb.append(String.format("<tr><td>Nodes</td><td>%d</td></tr>", r.nodeCount));
        sb.append(String.format("<tr><td>Edges</td><td>%d</td></tr>", r.edgeCount));
        sb.append(String.format("<tr><td>Components</td><td>%d</td></tr>", r.componentSizes.size()));
        sb.append(String.format("<tr><td>Isolated Nodes</td><td>%d</td></tr>", r.isolatedNodes.size()));
        sb.append(String.format("<tr><td>Self-Loops</td><td>%d</td></tr>", r.selfLoops.size()));
        sb.append(String.format("<tr><td>Parallel Edge Pairs</td><td>%d</td></tr>", r.parallelEdgePairs.size()));
        sb.append(String.format("<tr><td>Bridge Edges</td><td>%d</td></tr>", r.bridges.size()));
        sb.append(String.format("<tr><td>Degree Outliers</td><td>%d</td></tr>", r.degreeOutliers.size()));
        sb.append("</table>");

        // Issues
        sb.append("<h2>Findings</h2>");
        if (r.isolatedNodes.isEmpty()) {
            sb.append("<div class='issue ok'>✅ No isolated nodes</div>");
        } else {
            sb.append(String.format("<div class='issue'>⚠️ %d isolated node(s): %s</div>",
                    r.isolatedNodes.size(), truncateList(r.isolatedNodes, 20)));
        }
        if (r.selfLoops.isEmpty()) {
            sb.append("<div class='issue ok'>✅ No self-loops</div>");
        } else {
            sb.append(String.format("<div class='issue'>⚠️ %d self-loop(s) detected</div>", r.selfLoops.size()));
        }
        if (r.parallelEdgePairs.isEmpty()) {
            sb.append("<div class='issue ok'>✅ No parallel edges</div>");
        } else {
            sb.append(String.format("<div class='issue'>⚠️ %d parallel Edge pair(s)</div>", r.parallelEdgePairs.size()));
        }
        if (r.componentSizes.size() <= 1) {
            sb.append("<div class='issue ok'>✅ Graph is connected</div>");
        } else {
            sb.append(String.format("<div class='issue'>⚠️ Graph has %d components (sizes: %s)</div>",
                    r.componentSizes.size(), truncateList(r.componentSizes.stream()
                            .map(Object::toString).collect(Collectors.toList()), 15)));
        }
        if (r.bridges.isEmpty()) {
            sb.append("<div class='issue ok'>✅ No bridge edges (graph is 2-Edge-connected)</div>");
        } else {
            sb.append(String.format("<div class='issue'>⚠️ %d bridge Edge(s) — removing any would disconnect the graph</div>", r.bridges.size()));
        }
        if (r.degreeOutliers.isEmpty()) {
            sb.append("<div class='issue ok'>✅ No degree outliers</div>");
        } else {
            sb.append(String.format("<div class='issue'>⚠️ %d degree outlier(s): %s</div>",
                    r.degreeOutliers.size(), truncateList(r.degreeOutliers, 15)));
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private static String truncateList(List<?> items, int max) {
        if (items.size() <= max) return items.toString();
        return items.subList(0, max).toString() + " ... +" + (items.size() - max) + " more";
    }

    // ── Report POJO ────────────────────────────────────────

    public static class HealthReport {
        public int nodeCount;
        public int edgeCount;
        public int score;
        public List<String> isolatedNodes = Collections.emptyList();
        public List<Edge> selfLoops = Collections.emptyList();
        public List<String[]> parallelEdgePairs = Collections.emptyList();
        public List<Integer> componentSizes = Collections.emptyList();
        public List<String> degreeOutliers = Collections.emptyList();
        public List<Edge> bridges = Collections.emptyList();

        /** Plain text summary. */
        public String toText() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Graph Health Report ===\n");
            sb.append(String.format("Score: %d/100\n", score));
            sb.append(String.format("Nodes: %d | Edges: %d | Components: %d\n",
                    nodeCount, edgeCount, componentSizes.size()));
            sb.append(String.format("Isolated nodes: %d\n", isolatedNodes.size()));
            sb.append(String.format("Self-loops: %d\n", selfLoops.size()));
            sb.append(String.format("Parallel Edge pairs: %d\n", parallelEdgePairs.size()));
            sb.append(String.format("Bridge edges: %d\n", bridges.size()));
            sb.append(String.format("Degree outliers: %d\n", degreeOutliers.size()));
            if (!isolatedNodes.isEmpty()) {
                sb.append("  Isolated: ").append(truncateList(isolatedNodes, 10)).append("\n");
            }
            if (!degreeOutliers.isEmpty()) {
                sb.append("  Outliers: ").append(truncateList(degreeOutliers, 10)).append("\n");
            }
            return sb.toString();
        }
    }
}
