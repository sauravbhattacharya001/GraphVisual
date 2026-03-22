package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Exports a comprehensive per-node metrics report to CSV format.
 *
 * <p>Each row represents one node and includes:</p>
 * <ul>
 *   <li>Degree (total, plus breakdown by Edge type)</li>
 *   <li>Centrality scores (degree, betweenness, closeness)</li>
 *   <li>Community ID</li>
 *   <li>Local clustering coefficient</li>
 *   <li>Whether the node is an articulation point</li>
 * </ul>
 *
 * <p>Designed for researchers who want to analyze the social network
 * in Excel, R, pandas, or any tool that reads CSV.</p>
 *
 * <p>Usage:</p>
 * <pre>
 *   CsvReportExporter exporter = new CsvReportExporter(graph, allEdges);
 *   exporter.export(new File("report.csv"));
 *   // or
 *   String csv = exporter.exportToString();
 * </pre>
 *
 * @author zalenix
 */
public class CsvReportExporter {

    private final Graph<String, Edge> graph;
    private final List<Edge> allEdges;
    private String timestamp;

    /**
     * Creates a new CsvReportExporter.
     *
     * @param graph    the JUNG graph to report on
     * @param allEdges all edges (including those possibly filtered out of the graph)
     * @throws IllegalArgumentException if graph is null
     */
    public CsvReportExporter(Graph<String, Edge> graph, List<Edge> allEdges) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.allEdges = (allEdges != null) ? allEdges : new ArrayList<Edge>();
        this.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    /**
     * Sets the timestamp label included in the export metadata.
     *
     * @param timestamp a date/time string
     */
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Exports the report to a file.
     *
     * @param file output CSV file
     * @throws IOException if writing fails
     */
    public void export(File file) throws IOException {
        ExportUtils.validateOutputPath(file);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(exportToString());
        }
    }

    /**
     * Exports the report as a CSV string.
     *
     * @return the full CSV content
     */
    public String exportToString() {
        StringBuilder sb = new StringBuilder();

        // Compute metrics
        Map<String, Double> degreeCentrality = new LinkedHashMap<String, Double>();
        Map<String, Double> betweenness = new LinkedHashMap<String, Double>();
        Map<String, Double> closeness = new LinkedHashMap<String, Double>();
        computeCentralities(degreeCentrality, betweenness, closeness);

        Map<String, Integer> communityMap = computeCommunities();
        Map<String, Double> clusteringCoeff = computeClusteringCoefficients();
        Set<String> articulationPoints = computeArticulationPoints();

        // Edge type counts per node
        Map<String, int[]> edgeTypeCounts = computeEdgeTypeCounts();

        // Sort nodes for deterministic output
        List<String> nodes = new ArrayList<String>(graph.getVertices());
        Collections.sort(nodes);

        // Header
        sb.append("# GraphVisual CSV Report — ").append(timestamp).append("\n");
        sb.append("# Nodes: ").append(graph.getVertexCount())
          .append(", Edges: ").append(graph.getEdgeCount()).append("\n");
        sb.append("Node,Degree,FriendEdges,FamiliarStrangerEdges,ClassmateEdges,StrangerEdges,StudyGroupEdges,")
          .append("DegreeCentrality,BetweennessCentrality,ClosenessCentrality,")
          .append("CommunityID,ClusteringCoefficient,IsArticulationPoint\n");

        for (String node : nodes) {
            int degree = graph.degree(node);
            int[] typeCounts = edgeTypeCounts.containsKey(node) ? edgeTypeCounts.get(node) : new int[5];
            double dc = degreeCentrality.containsKey(node) ? degreeCentrality.get(node) : 0.0;
            double bc = betweenness.containsKey(node) ? betweenness.get(node) : 0.0;
            double cc = closeness.containsKey(node) ? closeness.get(node) : 0.0;
            int community = communityMap.containsKey(node) ? communityMap.get(node) : -1;
            double clustering = clusteringCoeff.containsKey(node) ? clusteringCoeff.get(node) : 0.0;
            boolean isAP = articulationPoints.contains(node);

            sb.append(escapeCsv(node)).append(',')
              .append(degree).append(',')
              .append(typeCounts[0]).append(',')
              .append(typeCounts[1]).append(',')
              .append(typeCounts[2]).append(',')
              .append(typeCounts[3]).append(',')
              .append(typeCounts[4]).append(',')
              .append(String.format("%.6f", dc)).append(',')
              .append(String.format("%.6f", bc)).append(',')
              .append(String.format("%.6f", cc)).append(',')
              .append(community).append(',')
              .append(String.format("%.6f", clustering)).append(',')
              .append(isAP ? "true" : "false")
              .append('\n');
        }

        return sb.toString();
    }

    /**
     * Computes degree, betweenness, and closeness centrality.
     *
     * <p>Delegates betweenness to {@link GraphUtils#computeBetweenness(Graph)}
     * (array-based Brandes algorithm) instead of reimplementing it locally.
     * Closeness is computed via a separate per-source BFS pass.</p>
     */
    private void computeCentralities(Map<String, Double> degreeCent,
                                     Map<String, Double> betweenness,
                                     Map<String, Double> closeness) {
        Collection<String> vertices = graph.getVertices();
        int n = vertices.size();
        if (n == 0) return;

        // Degree centrality: degree / (n-1)
        double maxDeg = n > 1 ? (n - 1.0) : 1.0;
        for (String v : vertices) {
            degreeCent.put(v, graph.degree(v) / maxDeg);
        }

        // Betweenness centrality — delegate to GraphUtils (array-based, already halved)
        Map<String, Double> rawBC = GraphUtils.computeBetweenness(graph);
        // Normalize: divide by (n-1)(n-2) to get values in [0,1]
        double norm = (n > 2) ? (n - 1.0) * (n - 2.0) / 2.0 : 1.0;
        for (String v : vertices) {
            Double bc = rawBC.get(v);
            betweenness.put(v, bc != null ? bc / norm : 0.0);
        }

        // Closeness centrality — BFS from each vertex
        computeCloseness(closeness, vertices);
    }

    /**
     * Computes closeness centrality for every vertex via per-source BFS.
     * Uses array-based BFS for efficiency.
     */
    private void computeCloseness(Map<String, Double> closeness,
                                  Collection<String> vertices) {
        int n = vertices.size();
        List<String> vertexList = new ArrayList<String>(vertices);
        Map<String, Integer> vertexIndex = new HashMap<String, Integer>(n * 2);
        for (int i = 0; i < n; i++) {
            vertexIndex.put(vertexList.get(i), i);
        }

        // Build adjacency lists
        int[][] adjLists = new int[n][];
        for (int i = 0; i < n; i++) {
            Collection<String> neighbors = graph.getNeighbors(vertexList.get(i));
            List<Integer> adj = new ArrayList<Integer>();
            if (neighbors != null) {
                for (String nb : neighbors) {
                    Integer idx = vertexIndex.get(nb);
                    if (idx != null) adj.add(idx);
                }
            }
            adjLists[i] = new int[adj.size()];
            for (int j = 0; j < adj.size(); j++) {
                adjLists[i][j] = adj.get(j);
            }
        }

        int[] dist = new int[n];
        int[] queue = new int[n];

        for (int s = 0; s < n; s++) {
            Arrays.fill(dist, -1);
            dist[s] = 0;
            int qHead = 0, qTail = 0;
            queue[qTail++] = s;

            int reachable = 0;
            int totalDist = 0;

            while (qHead < qTail) {
                int v = queue[qHead++];
                for (int w : adjLists[v]) {
                    if (dist[w] < 0) {
                        dist[w] = dist[v] + 1;
                        queue[qTail++] = w;
                        reachable++;
                        totalDist += dist[w];
                    }
                }
            }

            closeness.put(vertexList.get(s),
                (reachable > 0 && totalDist > 0) ? (double) reachable / totalDist : 0.0);
        }
    }

    /**
     * Community detection via connected components, labeled by size rank.
     * Delegates component-finding to {@link GraphUtils#findComponents(Graph)}.
     */
    private Map<String, Integer> computeCommunities() {
        List<Set<String>> components = GraphUtils.findComponents(graph);

        // Sort by size descending
        Collections.sort(components, (Set<String> a, Set<String> b) -> {
                return b.size() - a.size();
            });

        Map<String, Integer> communityMap = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < components.size(); i++) {
            for (String v : components.get(i)) {
                communityMap.put(v, i);
            }
        }
        return communityMap;
    }

    /**
     * Computes local clustering coefficient for each node.
     */
    private Map<String, Double> computeClusteringCoefficients() {
        Map<String, Double> result = new LinkedHashMap<String, Double>();
        for (String v : graph.getVertices()) {
            Collection<String> neighbors = graph.getNeighbors(v);
            List<String> nList = new ArrayList<String>(neighbors);
            int k = nList.size();
            if (k < 2) {
                result.put(v, 0.0);
                continue;
            }
            int triangles = 0;
            for (int i = 0; i < k; i++) {
                for (int j = i + 1; j < k; j++) {
                    if (graph.isNeighbor(nList.get(i), nList.get(j))) {
                        triangles++;
                    }
                }
            }
            result.put(v, (2.0 * triangles) / (k * (k - 1)));
        }
        return result;
    }

    /**
     * Finds articulation points by delegating to {@link ArticulationPointAnalyzer}.
     * Previously this method reimplemented Tarjan's algorithm locally (~40 lines);
     * now it reuses the canonical, well-tested implementation.
     */
    private Set<String> computeArticulationPoints() {
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        return analyzer.analyze().getArticulationPoints();
    }

    /**
     * Counts edges per type for each node.
     * Index: 0=friend, 1=fs, 2=classmate, 3=stranger, 4=studygroup
     */
    private Map<String, int[]> computeEdgeTypeCounts() {
        Map<String, int[]> counts = new LinkedHashMap<String, int[]>();
        for (Edge e : allEdges) {
            if (!graph.containsEdge(e)) continue;
            String type = e.getType();
            int idx = typeIndex(type);
            if (idx < 0) continue;

            addTypeCount(counts, e.getVertex1(), idx);
            addTypeCount(counts, e.getVertex2(), idx);
        }
        return counts;
    }

    private void addTypeCount(Map<String, int[]> counts, String node, int idx) {
        if (!counts.containsKey(node)) {
            counts.put(node, new int[5]);
        }
        counts.get(node)[idx]++;
    }

    private int typeIndex(String type) {
        if (type == null) return -1;
        if ("f".equals(type))  return 0;
        if ("fs".equals(type)) return 1;
        if ("c".equals(type))  return 2;
        if ("s".equals(type))  return 3;
        if ("sg".equals(type)) return 4;
        return -1;
    }

    /**
     * Escapes a value for CSV (wraps in quotes if it contains comma, quote, or newline).
     * Also defuses formula injection: values starting with {@code =}, {@code +},
     * {@code -}, {@code @}, {@code \t}, or {@code \r} are prefixed with a
     * single-quote inside the quoted field so spreadsheet applications treat
     * them as literal text rather than formulas.
     */
    private String escapeCsv(String value) {
        if (value == null) return "";
        boolean needsQuote = value.contains(",") || value.contains("\"") || value.contains("\n");
        boolean formulaRisk = !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0;
        if (formulaRisk) {
            return "\"'" + value.replace("\"", "\"\"") + "\"";
        }
        if (needsQuote) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
