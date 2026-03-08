package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Extracts focused subgraphs from a larger network based on flexible criteria.
 *
 * <p>Researchers often need to isolate specific parts of a social network for
 * detailed analysis — e.g. "show me only the friend edges with weight above 5"
 * or "extract the 2-hop neighborhood around node X". This class provides a
 * fluent builder API for composing extraction criteria and produces a new
 * JUNG graph containing only the matching nodes and edges.</p>
 *
 * <h3>Supported Filters</h3>
 * <ul>
 *   <li><b>Edge type:</b> Keep only edges of specified relationship types</li>
 *   <li><b>Weight range:</b> Keep edges within [minWeight, maxWeight]</li>
 *   <li><b>Degree range:</b> Keep nodes with degree in [minDegree, maxDegree]</li>
 *   <li><b>K-hop neighborhood:</b> Extract the k-hop subgraph around a seed node</li>
 *   <li><b>Node whitelist:</b> Keep only specific nodes and their mutual edges</li>
 *   <li><b>Time window:</b> Keep edges active during a time range</li>
 *   <li><b>Connected only:</b> Remove isolated nodes after filtering</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 *   // Friends-only subgraph with weight >= 3.0
 *   SubgraphExtractor ext = new SubgraphExtractor(graph, allEdges)
 *       .filterByEdgeType("f")
 *       .filterByMinWeight(3.0f)
 *       .connectedOnly(true);
 *   SubgraphExtractor.Result result = ext.extract();
 *   Graph&lt;String, edge&gt; subgraph = result.getGraph();
 *
 *   // 2-hop neighborhood of node "42"
 *   SubgraphExtractor ext2 = new SubgraphExtractor(graph, allEdges)
 *       .filterByNeighborhood("42", 2);
 *   SubgraphExtractor.Result result2 = ext2.extract();
 *
 *   // Export to file
 *   result2.exportEdgeList(new File("subgraph.csv"));
 * </pre>
 *
 * @author zalenix
 */
public class SubgraphExtractor {

    private final Graph<String, edge> sourceGraph;
    private final List<edge> allEdges;

    // Filter state
    private final Set<String> allowedEdgeTypes = new HashSet<>();
    private Float minWeight = null;
    private Float maxWeight = null;
    private Integer minDegree = null;
    private Integer maxDegree = null;
    private String seedNode = null;
    private int hops = 0;
    private final Set<String> nodeWhitelist = new HashSet<>();
    private Long timeStart = null;
    private Long timeEnd = null;
    private boolean connectedOnly = false;

    /**
     * Creates a new SubgraphExtractor for the given graph and edge list.
     *
     * @param graph    the source JUNG graph
     * @param allEdges the full edge list (used for filtering before graph insertion)
     */
    public SubgraphExtractor(Graph<String, edge> graph, List<edge> allEdges) {
        if (graph == null) throw new IllegalArgumentException("graph must not be null");
        if (allEdges == null) throw new IllegalArgumentException("allEdges must not be null");
        this.sourceGraph = graph;
        this.allEdges = allEdges;
    }

    /**
     * Keep only edges of the specified type code(s).
     *
     * @param typeCodes one or more edge type codes ("f", "c", "fs", "s", "sg")
     * @return this extractor for chaining
     */
    public SubgraphExtractor filterByEdgeType(String... typeCodes) {
        for (String code : typeCodes) {
            if (code != null) allowedEdgeTypes.add(code.toLowerCase());
        }
        return this;
    }

    /**
     * Keep only edges with weight >= minWeight.
     *
     * @param minWeight minimum edge weight (inclusive)
     * @return this extractor for chaining
     */
    public SubgraphExtractor filterByMinWeight(float minWeight) {
        this.minWeight = minWeight;
        return this;
    }

    /**
     * Keep only edges with weight <= maxWeight.
     *
     * @param maxWeight maximum edge weight (inclusive)
     * @return this extractor for chaining
     */
    public SubgraphExtractor filterByMaxWeight(float maxWeight) {
        this.maxWeight = maxWeight;
        return this;
    }

    /**
     * After edge filtering, keep only nodes whose degree in the resulting
     * subgraph falls within [minDegree, maxDegree].
     *
     * @param minDegree minimum degree (inclusive), or null for no lower bound
     * @param maxDegree maximum degree (inclusive), or null for no upper bound
     * @return this extractor for chaining
     */
    public SubgraphExtractor filterByDegreeRange(Integer minDegree, Integer maxDegree) {
        this.minDegree = minDegree;
        this.maxDegree = maxDegree;
        return this;
    }

    /**
     * Extract only the k-hop neighborhood around a seed node.
     * All other filters still apply to the edges within the neighborhood.
     *
     * @param seed the center node ID
     * @param k    number of hops (1 = immediate neighbors, 2 = neighbors of neighbors, etc.)
     * @return this extractor for chaining
     */
    public SubgraphExtractor filterByNeighborhood(String seed, int k) {
        if (seed == null) throw new IllegalArgumentException("seed must not be null");
        if (k < 1) throw new IllegalArgumentException("k must be >= 1");
        this.seedNode = seed;
        this.hops = k;
        return this;
    }

    /**
     * Keep only edges connecting nodes in the whitelist.
     *
     * @param nodes node IDs to include
     * @return this extractor for chaining
     */
    public SubgraphExtractor filterByNodes(String... nodes) {
        for (String n : nodes) {
            if (n != null) nodeWhitelist.add(n);
        }
        return this;
    }

    /**
     * Keep only edges active during the specified time window.
     *
     * @param startMillis window start (epoch millis, inclusive)
     * @param endMillis   window end (epoch millis, inclusive)
     * @return this extractor for chaining
     */
    public SubgraphExtractor filterByTimeWindow(long startMillis, long endMillis) {
        this.timeStart = startMillis;
        this.timeEnd = endMillis;
        return this;
    }

    /**
     * If true, remove isolated nodes (degree 0) from the final subgraph.
     *
     * @param connected whether to keep only connected nodes
     * @return this extractor for chaining
     */
    public SubgraphExtractor connectedOnly(boolean connected) {
        this.connectedOnly = connected;
        return this;
    }

    /**
     * Execute the extraction with all configured filters and return the result.
     *
     * @return a Result containing the extracted subgraph and summary statistics
     */
    public Result extract() {
        // Step 1: Determine candidate nodes (k-hop or whitelist or all)
        Set<String> candidateNodes = determineCandidateNodes();

        // Step 2: Filter edges
        List<edge> filteredEdges = new ArrayList<>();
        for (edge e : allEdges) {
            if (!candidateNodes.contains(e.getVertex1()) || !candidateNodes.contains(e.getVertex2())) {
                continue;
            }
            if (!allowedEdgeTypes.isEmpty() && !allowedEdgeTypes.contains(e.getType().toLowerCase())) {
                continue;
            }
            if (minWeight != null && e.getWeight() < minWeight) {
                continue;
            }
            if (maxWeight != null && e.getWeight() > maxWeight) {
                continue;
            }
            if (timeStart != null && timeEnd != null && !e.isActiveDuring(timeStart, timeEnd)) {
                continue;
            }
            filteredEdges.add(e);
        }

        // Step 3: Build subgraph
        Graph<String, edge> subgraph = new UndirectedSparseGraph<>();

        // Add all candidate nodes first (unless connectedOnly)
        if (!connectedOnly) {
            for (String node : candidateNodes) {
                subgraph.addVertex(node);
            }
        }

        for (edge e : filteredEdges) {
            subgraph.addVertex(e.getVertex1());
            subgraph.addVertex(e.getVertex2());
            subgraph.addEdge(e, e.getVertex1(), e.getVertex2());
        }

        // Step 4: Apply degree filter
        if (minDegree != null || maxDegree != null) {
            List<String> toRemove = new ArrayList<>();
            for (String node : subgraph.getVertices()) {
                int deg = subgraph.degree(node);
                if (minDegree != null && deg < minDegree) toRemove.add(node);
                else if (maxDegree != null && deg > maxDegree) toRemove.add(node);
            }
            for (String node : toRemove) {
                subgraph.removeVertex(node);
            }
        }

        // Step 5: Remove isolated nodes if connectedOnly and degree filter may have created them
        if (connectedOnly) {
            List<String> isolated = new ArrayList<>();
            for (String node : subgraph.getVertices()) {
                if (subgraph.degree(node) == 0) isolated.add(node);
            }
            for (String node : isolated) {
                subgraph.removeVertex(node);
            }
        }

        return new Result(subgraph, filteredEdges, sourceGraph.getVertexCount(),
                sourceGraph.getEdgeCount());
    }

    /**
     * Determines the set of candidate nodes based on neighborhood/whitelist filters.
     */
    private Set<String> determineCandidateNodes() {
        Set<String> candidates;

        if (seedNode != null && hops > 0) {
            // BFS k-hop neighborhood
            candidates = new LinkedHashSet<>();
            if (!sourceGraph.containsVertex(seedNode)) {
                return candidates; // seed not in graph
            }
            Queue<String> queue = new LinkedList<>();
            Map<String, Integer> distances = new HashMap<>();
            queue.add(seedNode);
            distances.put(seedNode, 0);
            candidates.add(seedNode);

            while (!queue.isEmpty()) {
                String current = queue.poll();
                int currentDist = distances.get(current);
                if (currentDist >= hops) continue;

                for (String neighbor : sourceGraph.getNeighbors(current)) {
                    if (!distances.containsKey(neighbor)) {
                        distances.put(neighbor, currentDist + 1);
                        candidates.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
        } else if (!nodeWhitelist.isEmpty()) {
            candidates = new LinkedHashSet<>(nodeWhitelist);
            // Only keep nodes that exist in the source graph
            candidates.retainAll(sourceGraph.getVertices());
        } else {
            candidates = new LinkedHashSet<>(sourceGraph.getVertices());
        }

        return candidates;
    }

    /**
     * Holds the result of a subgraph extraction, including the extracted graph,
     * the filtered edge list, and summary statistics.
     */
    public static class Result {
        private final Graph<String, edge> graph;
        private final List<edge> edges;
        private final int originalNodeCount;
        private final int originalEdgeCount;

        Result(Graph<String, edge> graph, List<edge> edges,
               int originalNodeCount, int originalEdgeCount) {
            this.graph = graph;
            this.edges = Collections.unmodifiableList(new ArrayList<>(edges));
            this.originalNodeCount = originalNodeCount;
            this.originalEdgeCount = originalEdgeCount;
        }

        /** Returns the extracted subgraph. */
        public Graph<String, edge> getGraph() {
            return graph;
        }

        /** Returns the filtered edges in the subgraph. */
        public List<edge> getEdges() {
            return edges;
        }

        /** Returns the number of nodes in the subgraph. */
        public int getNodeCount() {
            return graph.getVertexCount();
        }

        /** Returns the number of edges in the subgraph. */
        public int getEdgeCount() {
            return graph.getEdgeCount();
        }

        /** Returns the fraction of original nodes retained. */
        public double getNodeRetention() {
            return originalNodeCount == 0 ? 0.0 :
                    (double) graph.getVertexCount() / originalNodeCount;
        }

        /** Returns the fraction of original edges retained. */
        public double getEdgeRetention() {
            return originalEdgeCount == 0 ? 0.0 :
                    (double) graph.getEdgeCount() / originalEdgeCount;
        }

        /** Returns a per-edge-type count breakdown. */
        public Map<String, Integer> getEdgeTypeBreakdown() {
            Map<String, Integer> breakdown = new TreeMap<>();
            for (edge e : edges) {
                String type = e.getType();
                EdgeType et = EdgeType.fromCode(type);
                String label = et != null ? et.getDisplayLabel() : type;
                breakdown.merge(label, 1, Integer::sum);
            }
            return breakdown;
        }

        /** Returns the graph density of the subgraph. */
        public double getDensity() {
            int n = graph.getVertexCount();
            if (n < 2) return 0.0;
            return (2.0 * graph.getEdgeCount()) / ((long) n * (n - 1));
        }

        /** Returns a formatted summary string. */
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Subgraph Extraction Summary ===\n");
            sb.append(String.format("Nodes: %d / %d (%.1f%% retained)\n",
                    getNodeCount(), originalNodeCount, getNodeRetention() * 100));
            sb.append(String.format("Edges: %d / %d (%.1f%% retained)\n",
                    getEdgeCount(), originalEdgeCount, getEdgeRetention() * 100));
            sb.append(String.format("Density: %.4f\n", getDensity()));

            Map<String, Integer> breakdown = getEdgeTypeBreakdown();
            if (!breakdown.isEmpty()) {
                sb.append("Edge types:\n");
                for (Map.Entry<String, Integer> entry : breakdown.entrySet()) {
                    sb.append(String.format("  %s: %d\n", entry.getKey(), entry.getValue()));
                }
            }
            return sb.toString();
        }

        /**
         * Exports the subgraph as a CSV edge list.
         *
         * @param file output file
         * @throws IOException if writing fails
         */
        public void exportEdgeList(File file) throws IOException {
            try (PrintWriter pw = new PrintWriter(
                    new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                pw.println("source,target,type,weight,label");
                for (edge e : edges) {
                    pw.printf("%s,%s,%s,%.2f,%s%n",
                            csvEscape(e.getVertex1()),
                            csvEscape(e.getVertex2()),
                            csvEscape(e.getType()),
                            e.getWeight(),
                            csvEscape(e.getLabel() != null ? e.getLabel() : ""));
                }
            }
        }

        /**
         * Returns the edge list as a CSV string.
         *
         * @return CSV content
         */
        public String exportEdgeListToString() {
            StringBuilder sb = new StringBuilder();
            sb.append("source,target,type,weight,label\n");
            for (edge e : edges) {
                sb.append(String.format("%s,%s,%s,%.2f,%s%n",
                        csvEscape(e.getVertex1()),
                        csvEscape(e.getVertex2()),
                        csvEscape(e.getType()),
                        e.getWeight(),
                        csvEscape(e.getLabel() != null ? e.getLabel() : "")));
            }
            return sb.toString();
        }

        private static String csvEscape(String value) {
            if (value == null) return "";
            if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                return "\"" + value.replace("\"", "\"\"") + "\"";
            }
            return value;
        }
    }
}
