package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * GraphQueryEngine provides a simple, chainable query language for filtering
 * nodes and edges in a JUNG graph. Think of it like SQL for graphs.
 *
 * <h3>Usage examples:</h3>
 * <pre>
 * GraphQueryEngine engine = new GraphQueryEngine(graph);
 *
 * // Find high-degree nodes
 * Set&lt;String&gt; hubs = engine.nodes()
 *     .withMinDegree(10)
 *     .results();
 *
 * // Find heavy edges of a specific type
 * Set&lt;edge&gt; filtered = engine.edges()
 *     .ofType("f")
 *     .withMinWeight(0.5f)
 *     .results();
 *
 * // Combine node + edge filters: edges between high-degree nodes
 * Set&lt;edge&gt; hubEdges = engine.edges()
 *     .betweenNodes(engine.nodes().withMinDegree(5).results())
 *     .results();
 *
 * // Text search on node IDs
 * Set&lt;String&gt; matches = engine.nodes()
 *     .matching("IMEI.*123")
 *     .results();
 * </pre>
 *
 * @author GraphVisual
 */
public class GraphQueryEngine {

    private final Graph<String, Edge> graph;

    public GraphQueryEngine(Graph<String, Edge> graph) {
        this.graph = Objects.requireNonNull(graph, "graph must not be null");
    }

    /** Maximum allowed length for user-supplied regex patterns to mitigate ReDoS. */
    private static final int MAX_REGEX_LENGTH = 500;

    /**
     * Compile a regex pattern with safety checks: length limit and syntax validation.
     * Pre-compiling also avoids re-compilation on every vertex/edge evaluation.
     *
     * @throws IllegalArgumentException if the pattern is too long or has invalid syntax
     */
    static Pattern safeCompile(String regex) {
        if (regex == null) {
            throw new IllegalArgumentException("regex must not be null");
        }
        if (regex.length() > MAX_REGEX_LENGTH) {
            throw new IllegalArgumentException(
                    "Regex pattern exceeds maximum length of " + MAX_REGEX_LENGTH + " characters");
        }
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid regex pattern: " + e.getMessage(), e);
        }
    }

    /**
     * Start a node query.
     */
    public NodeQuery nodes() {
        return new NodeQuery(graph);
    }

    /**
     * Start an edge query.
     */
    public EdgeQuery edges() {
        return new EdgeQuery(graph);
    }

    /**
     * Get a quick summary of query-relevant graph statistics.
     */
    public QueryStats stats() {
        int nodeCount = graph.getVertexCount();
        int edgeCount = graph.getEdgeCount();
        int maxDeg = 0, minDeg = Integer.MAX_VALUE;
        double sumDeg = 0;
        Map<String, Integer> typeCounts = new HashMap<>();

        for (String v : graph.getVertices()) {
            int d = graph.degree(v);
            maxDeg = Math.max(maxDeg, d);
            minDeg = Math.min(minDeg, d);
            sumDeg += d;
        }
        if (nodeCount == 0) minDeg = 0;

        for (Edge e : graph.getEdges()) {
            String t = e.getType() != null ? e.getType() : "unknown";
            typeCounts.merge(t, 1, Integer::sum);
        }

        return new QueryStats(nodeCount, edgeCount, minDeg, maxDeg,
                nodeCount > 0 ? sumDeg / nodeCount : 0, typeCounts);
    }

    // ─── Node Query ──────────────────────────────────────────────

    /**
     * Chainable builder for filtering graph vertices.
     */
    public static class NodeQuery {
        private final Graph<String, Edge> graph;
        private final List<Predicate<String>> filters = new ArrayList<>();

        NodeQuery(Graph<String, Edge> graph) {
            this.graph = graph;
        }

        /** Keep only nodes with degree &ge; min. */
        public NodeQuery withMinDegree(int min) {
            filters.add(v -> graph.degree(v) >= min);
            return this;
        }

        /** Keep only nodes with degree &le; max. */
        public NodeQuery withMaxDegree(int max) {
            filters.add(v -> graph.degree(v) <= max);
            return this;
        }

        /** Keep only nodes with degree exactly equal to deg. */
        public NodeQuery withDegree(int deg) {
            filters.add(v -> graph.degree(v) == deg);
            return this;
        }

        /**
         * Keep only nodes whose ID matches the given regex.
         * The pattern is pre-compiled and validated to prevent ReDoS attacks.
         *
         * @throws IllegalArgumentException if the pattern is invalid or too long
         */
        public NodeQuery matching(String regex) {
            Pattern compiled = safeCompile(regex);
            filters.add(v -> compiled.matcher(v).matches());
            return this;
        }

        /** Keep only nodes whose ID contains the given substring (case-insensitive). */
        public NodeQuery containing(String substring) {
            String lower = substring.toLowerCase();
            filters.add(v -> v.toLowerCase().contains(lower));
            return this;
        }

        /** Keep only nodes that are in the given set. */
        public NodeQuery inSet(Set<String> allowed) {
            filters.add(allowed::contains);
            return this;
        }

        /** Keep only nodes that have at least one edge of the given type. */
        public NodeQuery connectedByType(String edgeType) {
            filters.add(v -> {
                for (Edge e : graph.getIncidentEdges(v)) {
                    if (edgeType.equals(e.getType())) return true;
                }
                return false;
            });
            return this;
        }

        /** Keep only nodes that are neighbors of the given node. */
        public NodeQuery neighborsOf(String nodeId) {
            filters.add(v -> graph.isNeighbor(v, nodeId));
            return this;
        }

        /** Keep only isolated nodes (degree 0). */
        public NodeQuery isolated() {
            filters.add(v -> graph.degree(v) == 0);
            return this;
        }

        /** Keep only leaf nodes (degree 1). */
        public NodeQuery leaves() {
            filters.add(v -> graph.degree(v) == 1);
            return this;
        }

        /** Add a custom predicate filter. */
        public NodeQuery where(Predicate<String> predicate) {
            filters.add(predicate);
            return this;
        }

        /** Execute the query and return matching nodes. */
        public Set<String> results() {
            return graph.getVertices().stream()
                    .filter(v -> filters.stream().allMatch(f -> f.test(v)))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        /** Execute the query and return the count of matching nodes. */
        public int count() {
            return results().size();
        }

        /** Execute the query and return results sorted by degree (descending). */
        public List<String> sortedByDegree() {
            return results().stream()
                    .sorted(Comparator.<String, Integer>comparing(graph::degree).reversed())
                    .collect(Collectors.toList());
        }

        /** Execute and return a summary string for display. */
        public String summary() {
            Set<String> r = results();
            if (r.isEmpty()) return "No matching nodes.";
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Found %d node(s):\n", r.size()));
            int shown = 0;
            for (String v : r) {
                sb.append(String.format("  %-20s  degree=%d\n", v, graph.degree(v)));
                if (++shown >= 50) {
                    sb.append(String.format("  ... and %d more\n", r.size() - 50));
                    break;
                }
            }
            return sb.toString();
        }
    }

    // ─── Edge Query ──────────────────────────────────────────────

    /**
     * Chainable builder for filtering graph edges.
     */
    public static class EdgeQuery {
        private final Graph<String, Edge> graph;
        private final List<Predicate<Edge>> filters = new ArrayList<>();

        EdgeQuery(Graph<String, Edge> graph) {
            this.graph = graph;
        }

        /** Keep only edges of the given type. */
        public EdgeQuery ofType(String type) {
            filters.add(e -> type.equals(e.getType()));
            return this;
        }

        /** Keep only edges with weight &ge; min. */
        public EdgeQuery withMinWeight(float min) {
            filters.add(e -> e.getWeight() >= min);
            return this;
        }

        /** Keep only edges with weight &le; max. */
        public EdgeQuery withMaxWeight(float max) {
            filters.add(e -> e.getWeight() <= max);
            return this;
        }

        /** Keep only Edges where both endpoints are in the given set. */
        public EdgeQuery betweenNodes(Set<String> nodeSet) {
            filters.add(e -> nodeSet.contains(e.getVertex1()) && nodeSet.contains(e.getVertex2()));
            return this;
        }

        /** Keep only edges incident to the given node. */
        public EdgeQuery incidentTo(String nodeId) {
            filters.add(e -> nodeId.equals(e.getVertex1()) || nodeId.equals(e.getVertex2()));
            return this;
        }

        /**
         * Keep only edges with a non-null label matching the regex.
         * The pattern is pre-compiled and validated to prevent ReDoS attacks.
         *
         * @throws IllegalArgumentException if the pattern is invalid or too long
         */
        public EdgeQuery labelMatching(String regex) {
            Pattern compiled = safeCompile(regex);
            filters.add(e -> e.getLabel() != null && compiled.matcher(e.getLabel()).matches());
            return this;
        }

        /** Keep only edges active at the given timestamp. */
        public EdgeQuery activeAt(long timestamp) {
            filters.add(e -> e.isActiveAt(timestamp));
            return this;
        }

        /** Keep only edges active during the given time range. */
        public EdgeQuery activeDuring(long start, long end) {
            filters.add(e -> e.isActiveDuring(start, end));
            return this;
        }

        /** Keep only edges with a non-null timestamp (temporal edges). */
        public EdgeQuery temporal() {
            filters.add(e -> e.getTimestamp() != null);
            return this;
        }

        /** Add a custom predicate filter. */
        public EdgeQuery where(Predicate<Edge> predicate) {
            filters.add(predicate);
            return this;
        }

        /** Execute the query and return matching edges. */
        public Set<Edge> results() {
            return graph.getEdges().stream()
                    .filter(e -> filters.stream().allMatch(f -> f.test(e)))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        /** Execute the query and return the count. */
        public int count() {
            return results().size();
        }

        /** Execute and return results sorted by weight (descending). */
        public List<Edge> sortedByWeight() {
            return results().stream()
                    .sorted(Comparator.comparing(edge::getWeight).reversed())
                    .collect(Collectors.toList());
        }

        /** Execute and return a type breakdown of matching edges. */
        public Map<String, Integer> typeBreakdown() {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (Edge e : results()) {
                String t = e.getType() != null ? e.getType() : "unknown";
                counts.merge(t, 1, Integer::sum);
            }
            return counts;
        }

        /** Execute and return a summary string for display. */
        public String summary() {
            Set<Edge> r = results();
            if (r.isEmpty()) return "No matching edges.";
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Found %d edge(s):\n", r.size()));
            int shown = 0;
            for (Edge e : r) {
                sb.append(String.format("  %s --%s--> %s  (w=%.2f)\n",
                        e.getVertex1(), e.getType(), e.getVertex2(), e.getWeight()));
                if (++shown >= 50) {
                    sb.append(String.format("  ... and %d more\n", r.size() - 50));
                    break;
                }
            }
            Map<String, Integer> types = typeBreakdown();
            sb.append("Type breakdown: ").append(types).append("\n");
            return sb.toString();
        }
    }

    // ─── Query Stats ─────────────────────────────────────────────

    /**
     * Immutable snapshot of graph statistics relevant to query planning.
     */
    public static class QueryStats {
        public final int nodeCount;
        public final int edgeCount;
        public final int minDegree;
        public final int maxDegree;
        public final double avgDegree;
        public final Map<String, Integer> edgeTypeCounts;

        QueryStats(int nodeCount, int edgeCount, int minDegree, int maxDegree,
                   double avgDegree, Map<String, Integer> edgeTypeCounts) {
            this.nodeCount = nodeCount;
            this.edgeCount = edgeCount;
            this.minDegree = minDegree;
            this.maxDegree = maxDegree;
            this.avgDegree = avgDegree;
            this.edgeTypeCounts = Collections.unmodifiableMap(edgeTypeCounts);
        }

        @Override
        public String toString() {
            return String.format(
                    "Graph: %d nodes, %d edges | degree: min=%d, max=%d, avg=%.1f | types: %s",
                    nodeCount, edgeCount, minDegree, maxDegree, avgDegree, edgeTypeCounts);
        }
    }
}
