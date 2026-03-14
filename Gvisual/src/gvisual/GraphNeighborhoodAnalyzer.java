package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyses k-hop neighborhoods for vertices in a graph, providing local
 * structural insights that complement global metrics.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>k-hop neighborhood</b> — the set of vertices reachable within k
 *       steps from a source vertex (BFS layers)</li>
 *   <li><b>Growth profile</b> — how the reachable set size grows at each
 *       hop depth, revealing local topology (tree-like vs mesh-like)</li>
 *   <li><b>Neighborhood overlap</b> — Jaccard similarity of k-hop
 *       neighborhoods between two vertices, measuring structural proximity</li>
 *   <li><b>Local density</b> — edge density within the k-hop induced
 *       subgraph, indicating how tightly connected a neighbourhood is</li>
 *   <li><b>Boundary vertices</b> — vertices exactly k hops away (the
 *       frontier), useful for expansion analysis</li>
 *   <li><b>Expansion rate</b> — ratio of boundary size to interior size at
 *       each hop, quantifying how quickly influence spreads</li>
 *   <li><b>Aggregate statistics</b> — mean/min/max neighborhood sizes across
 *       all vertices for a given depth k</li>
 *   <li><b>Text report</b> — human-readable neighborhood summary</li>
 * </ul>
 *
 * <p>All computations use BFS from the source vertex.  For directed graphs
 * the successor (out-edge) direction is followed.  The source vertex itself
 * is always at depth 0.</p>
 *
 * @author zalenix
 */
public class GraphNeighborhoodAnalyzer {

    private final Graph<String, edge> graph;

    /**
     * Constructs an analyser for the given graph.
     *
     * @param graph the graph to analyse (must not be null)
     * @throws IllegalArgumentException if graph is null
     */
    public GraphNeighborhoodAnalyzer(Graph<String, edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
    }

    // ------------------------------------------------------------------
    //  Core: BFS layer computation
    // ------------------------------------------------------------------

    /**
     * Computes BFS layers from a source vertex up to depth {@code maxK}.
     * Layer 0 contains only the source.  Layer i contains vertices at
     * exactly distance i from the source.
     *
     * @param source the starting vertex
     * @param maxK   maximum depth (must be &ge; 0)
     * @return list of layers, where index i is the set of vertices at depth i
     * @throws IllegalArgumentException if source is not in the graph or maxK &lt; 0
     */
    public List<Set<String>> computeLayers(String source, int maxK) {
        validateVertex(source);
        if (maxK < 0) {
            throw new IllegalArgumentException("maxK must be >= 0, got " + maxK);
        }

        List<Set<String>> layers = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        visited.add(source);
        layers.add(Collections.singleton(source));

        for (int depth = 1; depth <= maxK; depth++) {
            Set<String> frontier = new LinkedHashSet<>();
            for (String v : layers.get(depth - 1)) {
                for (String neighbor : graph.getNeighbors(v)) {
                    if (!visited.contains(neighbor)) {
                        frontier.add(neighbor);
                        visited.add(neighbor);
                    }
                }
            }
            if (frontier.isEmpty()) {
                break; // no more vertices to reach
            }
            layers.add(Collections.unmodifiableSet(frontier));
        }
        return layers;
    }

    // ------------------------------------------------------------------
    //  k-hop neighborhood
    // ------------------------------------------------------------------

    /**
     * Returns the set of vertices within k hops of the source (inclusive).
     *
     * @param source starting vertex
     * @param k      hop distance (&ge; 0)
     * @return unmodifiable set of vertices within k hops
     */
    public Set<String> getKHopNeighborhood(String source, int k) {
        List<Set<String>> layers = computeLayers(source, k);
        Set<String> result = new LinkedHashSet<>();
        for (Set<String> layer : layers) {
            result.addAll(layer);
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Returns boundary vertices — those at exactly distance k from source.
     * If k exceeds the eccentricity, an empty set is returned.
     *
     * @param source starting vertex
     * @param k      exact hop distance
     * @return unmodifiable set of boundary vertices
     */
    public Set<String> getBoundary(String source, int k) {
        List<Set<String>> layers = computeLayers(source, k);
        if (k < layers.size()) {
            return layers.get(k);
        }
        return Collections.emptySet();
    }

    // ------------------------------------------------------------------
    //  Growth profile
    // ------------------------------------------------------------------

    /**
     * Computes the cumulative neighborhood size at each hop depth.
     * Index 0 is always 1 (the source).  The list has at most maxK+1 entries,
     * fewer if the graph is exhausted before maxK.
     *
     * @param source starting vertex
     * @param maxK   maximum depth
     * @return list of cumulative sizes at each depth
     */
    public List<Integer> getGrowthProfile(String source, int maxK) {
        List<Set<String>> layers = computeLayers(source, maxK);
        List<Integer> profile = new ArrayList<>();
        int cumulative = 0;
        for (Set<String> layer : layers) {
            cumulative += layer.size();
            profile.add(cumulative);
        }
        return profile;
    }

    /**
     * Computes the expansion rate at each hop depth.
     * Expansion at depth d = |layer d| / |cumulative up to d-1|.
     * Depth 0 has no meaningful expansion (returned as 0.0).
     *
     * @param source starting vertex
     * @param maxK   maximum depth
     * @return list of expansion rates (length = number of layers computed)
     */
    public List<Double> getExpansionRates(String source, int maxK) {
        List<Set<String>> layers = computeLayers(source, maxK);
        List<Double> rates = new ArrayList<>();
        int cumulative = 0;
        for (int i = 0; i < layers.size(); i++) {
            if (i == 0) {
                rates.add(0.0);
                cumulative += layers.get(i).size();
            } else {
                int boundary = layers.get(i).size();
                double rate = cumulative > 0 ? (double) boundary / cumulative : 0.0;
                rates.add(rate);
                cumulative += boundary;
            }
        }
        return rates;
    }

    // ------------------------------------------------------------------
    //  Local density
    // ------------------------------------------------------------------

    /**
     * Computes the edge density of the induced subgraph on the k-hop
     * neighborhood of the source vertex.
     *
     * <p>Density = 2 * |edges in subgraph| / (n * (n-1)) for n &gt; 1,
     * where n is the neighborhood size.  Returns 1.0 for a single vertex
     * and 0.0 for an empty graph.</p>
     *
     * @param source starting vertex
     * @param k      hop distance
     * @return edge density in [0.0, 1.0]
     */
    public double getLocalDensity(String source, int k) {
        Set<String> neighborhood = getKHopNeighborhood(source, k);
        int n = neighborhood.size();
        if (n <= 1) {
            return n == 1 ? 1.0 : 0.0;
        }

        int edgeCount = 0;
        for (edge e : graph.getEdges()) {
            String src = graph.getEndpoints(e).getFirst();
            String dst = graph.getEndpoints(e).getSecond();
            if (neighborhood.contains(src) && neighborhood.contains(dst)) {
                edgeCount++;
            }
        }

        double maxEdges = (double) n * (n - 1) / 2.0;
        return edgeCount / maxEdges;
    }

    // ------------------------------------------------------------------
    //  Overlap / Similarity
    // ------------------------------------------------------------------

    /**
     * Computes the Jaccard similarity of the k-hop neighborhoods of two
     * vertices: |A ∩ B| / |A ∪ B|.
     *
     * @param v1 first vertex
     * @param v2 second vertex
     * @param k  hop distance
     * @return Jaccard similarity in [0.0, 1.0]
     */
    public double getNeighborhoodOverlap(String v1, String v2, int k) {
        Set<String> n1 = getKHopNeighborhood(v1, k);
        Set<String> n2 = getKHopNeighborhood(v2, k);

        Set<String> intersection = new HashSet<>(n1);
        intersection.retainAll(n2);

        Set<String> union = new HashSet<>(n1);
        union.addAll(n2);

        if (union.isEmpty()) {
            return 1.0; // both empty → identical
        }
        return (double) intersection.size() / union.size();
    }

    /**
     * Computes the overlap coefficient of the k-hop neighborhoods:
     * |A ∩ B| / min(|A|, |B|).  More resilient to size differences
     * than Jaccard.
     *
     * @param v1 first vertex
     * @param v2 second vertex
     * @param k  hop distance
     * @return overlap coefficient in [0.0, 1.0]
     */
    public double getOverlapCoefficient(String v1, String v2, int k) {
        Set<String> n1 = getKHopNeighborhood(v1, k);
        Set<String> n2 = getKHopNeighborhood(v2, k);

        Set<String> intersection = new HashSet<>(n1);
        intersection.retainAll(n2);

        int minSize = Math.min(n1.size(), n2.size());
        if (minSize == 0) {
            return n1.isEmpty() && n2.isEmpty() ? 1.0 : 0.0;
        }
        return (double) intersection.size() / minSize;
    }

    // ------------------------------------------------------------------
    //  Aggregate statistics
    // ------------------------------------------------------------------

    /**
     * Result of aggregate neighborhood statistics across all vertices.
     */
    public static class AggregateStats {
        private final int k;
        private final double meanSize;
        private final int minSize;
        private final int maxSize;
        private final double meanDensity;
        private final String minVertex;
        private final String maxVertex;

        public AggregateStats(int k, double meanSize, int minSize, int maxSize,
                              double meanDensity, String minVertex, String maxVertex) {
            this.k = k;
            this.meanSize = meanSize;
            this.minSize = minSize;
            this.maxSize = maxSize;
            this.meanDensity = meanDensity;
            this.minVertex = minVertex;
            this.maxVertex = maxVertex;
        }

        public int getK() { return k; }
        public double getMeanSize() { return meanSize; }
        public int getMinSize() { return minSize; }
        public int getMaxSize() { return maxSize; }
        public double getMeanDensity() { return meanDensity; }
        public String getMinVertex() { return minVertex; }
        public String getMaxVertex() { return maxVertex; }
    }

    /**
     * Computes aggregate k-hop neighborhood statistics across all vertices.
     *
     * @param k hop distance
     * @return aggregate statistics
     * @throws IllegalArgumentException if k &lt; 0 or graph is empty
     */
    public AggregateStats getAggregateStats(int k) {
        if (k < 0) {
            throw new IllegalArgumentException("k must be >= 0, got " + k);
        }
        Collection<String> vertices = graph.getVertices();
        if (vertices.isEmpty()) {
            throw new IllegalArgumentException("Graph has no vertices");
        }

        int minSize = Integer.MAX_VALUE;
        int maxSize = Integer.MIN_VALUE;
        double totalSize = 0;
        double totalDensity = 0;
        String minVertex = null;
        String maxVertex = null;

        for (String v : vertices) {
            Set<String> hood = getKHopNeighborhood(v, k);
            int size = hood.size();
            double density = getLocalDensity(v, k);

            totalSize += size;
            totalDensity += density;

            if (size < minSize) {
                minSize = size;
                minVertex = v;
            }
            if (size > maxSize) {
                maxSize = size;
                maxVertex = v;
            }
        }

        int n = vertices.size();
        return new AggregateStats(k,
                totalSize / n, minSize, maxSize,
                totalDensity / n, minVertex, maxVertex);
    }

    // ------------------------------------------------------------------
    //  Vertex ranking
    // ------------------------------------------------------------------

    /**
     * Ranks all vertices by their k-hop neighborhood size (descending).
     * Useful for identifying vertices with the widest local reach.
     *
     * @param k hop distance
     * @return list of (vertex, size) pairs sorted by size descending
     */
    public List<Map.Entry<String, Integer>> rankByNeighborhoodSize(int k) {
        if (k < 0) {
            throw new IllegalArgumentException("k must be >= 0, got " + k);
        }
        Map<String, Integer> sizes = new LinkedHashMap<>();
        for (String v : graph.getVertices()) {
            sizes.put(v, getKHopNeighborhood(v, k).size());
        }
        return sizes.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    //  Report
    // ------------------------------------------------------------------

    /**
     * Generates a human-readable text report of neighborhood analysis.
     *
     * @param source vertex to profile (use null for aggregate-only report)
     * @param maxK   maximum hop depth to report
     * @return formatted text report
     */
    public String getTextReport(String source, int maxK) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Neighborhood Analysis Report ===\n");
        sb.append(String.format("Graph: %d vertices, %d edges\n",
                graph.getVertexCount(), graph.getEdgeCount()));
        sb.append(String.format("Max depth: %d\n\n", maxK));

        if (source != null && graph.containsVertex(source)) {
            sb.append(String.format("--- Vertex '%s' ---\n", source));
            List<Set<String>> layers = computeLayers(source, maxK);
            List<Double> expansion = getExpansionRates(source, maxK);
            int cumulative = 0;
            for (int d = 0; d < layers.size(); d++) {
                cumulative += layers.get(d).size();
                String expStr = d == 0 ? "n/a" : String.format("%.3f", expansion.get(d));
                sb.append(String.format("  Depth %d: layer=%d  cumulative=%d  expansion=%s\n",
                        d, layers.get(d).size(), cumulative, expStr));
            }
            sb.append(String.format("  Local density (k=%d): %.4f\n", maxK,
                    getLocalDensity(source, maxK)));
            sb.append("\n");
        }

        sb.append("--- Aggregate Statistics ---\n");
        for (int k = 1; k <= Math.min(maxK, 3); k++) {
            if (graph.getVertexCount() == 0) break;
            AggregateStats stats = getAggregateStats(k);
            sb.append(String.format("  k=%d: mean=%.1f  min=%d (%s)  max=%d (%s)  density=%.4f\n",
                    k, stats.getMeanSize(), stats.getMinSize(), stats.getMinVertex(),
                    stats.getMaxSize(), stats.getMaxVertex(), stats.getMeanDensity()));
        }

        return sb.toString();
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private void validateVertex(String vertex) {
        if (vertex == null || !graph.containsVertex(vertex)) {
            throw new IllegalArgumentException(
                    "Vertex not found in graph: " + vertex);
        }
    }
}
