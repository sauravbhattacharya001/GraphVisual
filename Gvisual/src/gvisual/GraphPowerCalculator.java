package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.*;

/**
 * Computes the k-th power of a graph. The k-th power G^k has the same vertex
 * set as G, but vertices u and v are adjacent in G^k if and only if their
 * shortest-path distance in G is at most k.
 *
 * <h3>Special cases:</h3>
 * <ul>
 *   <li><b>G^1</b> — the original graph</li>
 *   <li><b>G^2</b> — the square graph (vertices within distance 2)</li>
 *   <li><b>G^3</b> — the cube graph (vertices within distance 3)</li>
 *   <li><b>G^∞</b> — complete graph on each connected component</li>
 * </ul>
 *
 * <h3>Applications:</h3>
 * <ul>
 *   <li>Graph coloring (square graphs appear in frequency assignment)</li>
 *   <li>Network analysis (k-hop neighborhoods)</li>
 *   <li>Distributed computing (modeling k-hop communication)</li>
 *   <li>Chordal graph recognition (odd powers of chordal graphs are chordal)</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * GraphPowerCalculator calc = new GraphPowerCalculator(graph);
 * Graph<String, Edge> squared = calc.power(2);
 * Graph<String, Edge> cubed = calc.power(3);
 * Map<String, Map<String, Integer>> distances = calc.allPairsDistances();
 * String report = calc.generateReport(3);
 * }</pre>
 *
 * @author GraphVisual
 */
public class GraphPowerCalculator {

    private final Graph<String, Edge> graph;
    private Map<String, Map<String, Integer>> distanceCache;

    /**
     * Creates a new GraphPowerCalculator for the given graph.
     *
     * @param graph the input graph
     */
    public GraphPowerCalculator(Graph<String, Edge> graph) {
        this.graph = graph;
        this.distanceCache = null;
    }

    /**
     * Computes all-pairs shortest path distances using BFS from each vertex.
     * Results are cached for subsequent calls.
     *
     * @return map from vertex to map of vertex to distance
     */
    public Map<String, Map<String, Integer>> allPairsDistances() {
        if (distanceCache != null) {
            return distanceCache;
        }
        distanceCache = new LinkedHashMap<>();
        for (String source : graph.getVertices()) {
            distanceCache.put(source, bfsDistances(source));
        }
        return distanceCache;
    }

    private Map<String, Integer> bfsDistances(String source) {
        return GraphUtils.bfsDistances(graph, source);
    }

    /**
     * Computes the k-th power of the graph.
     *
     * @param k the power (must be >= 1)
     * @return a new graph representing G^k
     * @throws IllegalArgumentException if k < 1
     */
    public Graph<String, Edge> power(int k) {
        if (k < 1) {
            throw new IllegalArgumentException("Power k must be >= 1, got: " + k);
        }

        Map<String, Map<String, Integer>> distances = allPairsDistances();
        Graph<String, Edge> result = new UndirectedSparseGraph<>();

        // Add all vertices
        for (String v : graph.getVertices()) {
            result.addVertex(v);
        }

        // Add edges for all pairs within distance k
        List<String> vertices = new ArrayList<>(graph.getVertices());
        int edgeCount = 0;
        for (int i = 0; i < vertices.size(); i++) {
            String u = vertices.get(i);
            Map<String, Integer> distFromU = distances.get(u);
            for (int j = i + 1; j < vertices.size(); j++) {
                String v = vertices.get(j);
                Integer d = distFromU.get(v);
                if (d != null && d <= k) {
                    Edge edge = new Edge();
                    edge.setEdgeType("power_" + k);
                    edge.setVertex1(u);
                    edge.setVertex2(v);
                    edge.setWeight(d);
                    edge.setLabel("d=" + d);
                    result.addEdge(edge, u, v);
                    edgeCount++;
                }
            }
        }

        return result;
    }

    /**
     * Computes the square graph (G^2).
     *
     * @return G^2
     */
    public Graph<String, Edge> square() {
        return power(2);
    }

    /**
     * Computes the cube graph (G^3).
     *
     * @return G^3
     */
    public Graph<String, Edge> cube() {
        return power(3);
    }

    /**
     * Finds the diameter of the graph (maximum shortest-path distance
     * between any pair of vertices in the same connected component).
     *
     * @return the diameter, or 0 if the graph has fewer than 2 vertices
     */
    public int diameter() {
        Map<String, Map<String, Integer>> distances = allPairsDistances();
        int maxDist = 0;
        for (Map<String, Integer> distMap : distances.values()) {
            for (int d : distMap.values()) {
                if (d > maxDist) {
                    maxDist = d;
                }
            }
        }
        return maxDist;
    }

    /**
     * Returns the number of new edges added when computing G^k compared
     * to the original graph.
     *
     * @param k the power
     * @return number of new edges
     */
    public int newEdgeCount(int k) {
        Map<String, Map<String, Integer>> distances = allPairsDistances();
        int newEdges = 0;
        List<String> vertices = new ArrayList<>(graph.getVertices());
        for (int i = 0; i < vertices.size(); i++) {
            String u = vertices.get(i);
            Map<String, Integer> distFromU = distances.get(u);
            for (int j = i + 1; j < vertices.size(); j++) {
                String v = vertices.get(j);
                Integer d = distFromU.get(v);
                if (d != null && d > 1 && d <= k) {
                    newEdges++;
                }
            }
        }
        return newEdges;
    }

    /**
     * Computes edge density of G^k.
     *
     * @param k the power
     * @return density as a value between 0 and 1
     */
    public double density(int k) {
        int n = graph.getVertexCount();
        if (n < 2) return 0.0;
        long maxEdges = (long) n * (n - 1) / 2;
        Graph<String, Edge> pk = power(k);
        return (double) pk.getEdgeCount() / maxEdges;
    }

    /**
     * Finds the minimum k such that G^k is a complete graph on each
     * connected component (i.e., k = diameter).
     *
     * @return the minimum k for completeness
     */
    public int minPowerForComplete() {
        return diameter();
    }

    /**
     * Generates a text report comparing G with G^k.
     *
     * @param k the power to analyze
     * @return formatted report string
     */
    public String generateReport(int k) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════\n");
        sb.append("         GRAPH POWER ANALYSIS (G^").append(k).append(")\n");
        sb.append("═══════════════════════════════════════════\n\n");

        int n = graph.getVertexCount();
        int origEdges = graph.getEdgeCount();
        int diam = diameter();

        sb.append("Original Graph:\n");
        sb.append("  Vertices:  ").append(n).append("\n");
        sb.append("  Edges:     ").append(origEdges).append("\n");
        sb.append("  Diameter:  ").append(diam).append("\n\n");

        Graph<String, Edge> pk = power(k);
        int powerEdges = pk.getEdgeCount();
        int newEdges = powerEdges - origEdges;

        sb.append("G^").append(k).append(":\n");
        sb.append("  Edges:     ").append(powerEdges).append("\n");
        sb.append("  New edges: ").append(newEdges).append("\n");
        sb.append(String.format("  Density:   %.4f\n", density(k)));
        sb.append(String.format("  Fill %%:    %.1f%%\n",
                density(k) * 100));

        if (k >= diam) {
            sb.append("\n  ★ G^").append(k)
              .append(" contains a complete graph on each component\n");
            sb.append("    (diameter = ").append(diam).append(")\n");
        } else {
            sb.append("\n  Need G^").append(diam)
              .append(" for completeness (diameter = ").append(diam).append(")\n");
        }

        // Power progression table
        sb.append("\nPower Progression:\n");
        sb.append("  k  | Edges   | New     | Density\n");
        sb.append("  ---|---------|---------|--------\n");
        int maxK = Math.min(k, diam);
        for (int i = 1; i <= maxK; i++) {
            Graph<String, Edge> pi = power(i);
            int piEdges = pi.getEdgeCount();
            int piNew = piEdges - origEdges;
            double piDensity = density(i);
            sb.append(String.format("  %-2d | %-7d | %-7d | %.4f\n",
                    i, piEdges, piNew, piDensity));
        }

        // Average degree comparison
        sb.append("\nAverage Degree:\n");
        if (n > 0) {
            double origAvgDeg = 2.0 * origEdges / n;
            double powerAvgDeg = 2.0 * powerEdges / n;
            sb.append(String.format("  Original: %.2f\n", origAvgDeg));
            sb.append(String.format("  G^%d:     %.2f\n", k, powerAvgDeg));
            sb.append(String.format("  Increase: %.2fx\n", powerAvgDeg / Math.max(origAvgDeg, 0.001)));
        }

        sb.append("\n═══════════════════════════════════════════\n");
        return sb.toString();
    }
}
