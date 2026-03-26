package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Graph Regularity Analyzer — determines whether a graph is regular and
 * computes irregularity metrics.
 *
 * <h3>Definitions</h3>
 * <ul>
 *   <li><b>Regular graph</b> — every vertex has the same degree.</li>
 *   <li><b>k-regular</b> — every vertex has degree exactly k.</li>
 *   <li><b>Irregularity (Albertson index)</b> — sum of |deg(u) − deg(v)|
 *       over all edges {u, v}. Zero iff the graph is regular.</li>
 *   <li><b>Degree variance</b> — statistical variance of vertex degrees,
 *       another measure of how far from regular the graph is.</li>
 *   <li><b>Deviant vertices</b> — vertices whose degree differs from
 *       the mode (most common degree).</li>
 * </ul>
 *
 * <p>Useful for characterizing network uniformity in social networks,
 * IMEI call graphs, and infrastructure topologies.</p>
 *
 * @author zalenix
 */
public class GraphRegularityAnalyzer {

    private final Graph<String, Edge> graph;
    private boolean computed;

    // Results
    private boolean isRegular;
    private int regularityDegree; // -1 if not regular
    private int minDegree;
    private int maxDegree;
    private double meanDegree;
    private double degreeVariance;
    private long albertsonIndex;
    private int modeDegree;
    private Map<String, Integer> degreeMap;
    private Map<Integer, Integer> degreeDistribution;
    private List<String> deviantVertices;

    /**
     * Creates a new GraphRegularityAnalyzer.
     *
     * @param graph the JUNG graph to analyze
     * @throws IllegalArgumentException if graph is null
     */
    public GraphRegularityAnalyzer(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.computed = false;
    }

    /**
     * Runs the regularity analysis. Must be called before querying results.
     */
    public void analyze() {
        Collection<String> vertices = graph.getVertices();
        int n = vertices.size();

        degreeMap = new LinkedHashMap<>();
        degreeDistribution = new TreeMap<>();

        if (n == 0) {
            isRegular = true;
            regularityDegree = 0;
            minDegree = 0;
            maxDegree = 0;
            meanDegree = 0.0;
            degreeVariance = 0.0;
            albertsonIndex = 0;
            modeDegree = 0;
            deviantVertices = Collections.emptyList();
            computed = true;
            return;
        }

        // Compute degrees
        for (String v : vertices) {
            int deg = graph.degree(v);
            degreeMap.put(v, deg);
            degreeDistribution.merge(deg, 1, Integer::sum);
        }

        minDegree = Collections.min(degreeMap.values());
        maxDegree = Collections.max(degreeMap.values());

        // Mean degree
        long sumDeg = 0;
        for (int d : degreeMap.values()) {
            sumDeg += d;
        }
        meanDegree = (double) sumDeg / n;

        // Variance
        double sumSqDiff = 0.0;
        for (int d : degreeMap.values()) {
            double diff = d - meanDegree;
            sumSqDiff += diff * diff;
        }
        degreeVariance = sumSqDiff / n;

        // Regular?
        isRegular = (minDegree == maxDegree);
        regularityDegree = isRegular ? minDegree : -1;

        // Mode degree (most common)
        modeDegree = degreeDistribution.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0);

        // Deviant vertices (degree != mode)
        deviantVertices = degreeMap.entrySet().stream()
                .filter(e -> e.getValue() != modeDegree)
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(
                        e -> Math.abs(e.getValue() - modeDegree)).reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Albertson irregularity index: sum |deg(u) - deg(v)| over all edges
        albertsonIndex = 0;
        for (Edge edge : graph.getEdges()) {
            Collection<String> endpoints = graph.getEndpoints(edge);
            Iterator<String> it = endpoints.iterator();
            String u = it.next();
            String v = it.hasNext() ? it.next() : u;
            albertsonIndex += Math.abs(degreeMap.get(u) - degreeMap.get(v));
        }

        computed = true;
    }

    private void ensureComputed() {
        if (!computed) {
            throw new IllegalStateException("Call analyze() first");
        }
    }

    /** Returns true if the graph is regular (all vertices same degree). */
    public boolean isRegular() {
        ensureComputed();
        return isRegular;
    }

    /** Returns k if the graph is k-regular, or -1 if not regular. */
    public int getRegularityDegree() {
        ensureComputed();
        return regularityDegree;
    }

    public int getMinDegree() { ensureComputed(); return minDegree; }
    public int getMaxDegree() { ensureComputed(); return maxDegree; }
    public double getMeanDegree() { ensureComputed(); return meanDegree; }
    public double getDegreeVariance() { ensureComputed(); return degreeVariance; }

    /** Returns the Albertson irregularity index. Zero iff regular. */
    public long getAlbertsonIndex() { ensureComputed(); return albertsonIndex; }

    /** Returns the mode (most common) degree. */
    public int getModeDegree() { ensureComputed(); return modeDegree; }

    /** Returns degree distribution: degree → count. */
    public Map<Integer, Integer> getDegreeDistribution() {
        ensureComputed();
        return Collections.unmodifiableMap(degreeDistribution);
    }

    /** Returns the per-vertex degree map. */
    public Map<String, Integer> getDegreeMap() {
        ensureComputed();
        return Collections.unmodifiableMap(degreeMap);
    }

    /**
     * Returns vertices whose degree differs from the mode,
     * sorted by deviation (largest first).
     */
    public List<String> getDeviantVertices() {
        ensureComputed();
        return Collections.unmodifiableList(deviantVertices);
    }

    /**
     * Generates a human-readable report of the regularity analysis.
     *
     * @return multi-line summary string
     */
    public String generateReport() {
        ensureComputed();
        StringBuilder sb = new StringBuilder();
        sb.append("=== Graph Regularity Analysis ===\n\n");
        sb.append(String.format("Vertices: %d  |  Edges: %d\n",
                graph.getVertexCount(), graph.getEdgeCount()));
        sb.append(String.format("Min degree: %d  |  Max degree: %d  |  Mean: %.2f\n",
                minDegree, maxDegree, meanDegree));
        sb.append(String.format("Degree variance: %.4f\n", degreeVariance));
        sb.append(String.format("Albertson irregularity index: %d\n\n", albertsonIndex));

        if (isRegular) {
            sb.append(String.format("✓ Graph is %d-regular (every vertex has degree %d)\n",
                    regularityDegree, regularityDegree));
        } else {
            sb.append(String.format("✗ Graph is NOT regular (degrees range from %d to %d)\n",
                    minDegree, maxDegree));
            sb.append(String.format("  Mode degree: %d (%d vertices)\n",
                    modeDegree, degreeDistribution.getOrDefault(modeDegree, 0)));
            sb.append(String.format("  Deviant vertices: %d\n", deviantVertices.size()));
            if (!deviantVertices.isEmpty()) {
                int show = Math.min(10, deviantVertices.size());
                sb.append("  Top deviants:\n");
                for (int i = 0; i < show; i++) {
                    String v = deviantVertices.get(i);
                    sb.append(String.format("    %s (degree %d, Δ=%d)\n",
                            v, degreeMap.get(v),
                            Math.abs(degreeMap.get(v) - modeDegree)));
                }
                if (deviantVertices.size() > show) {
                    sb.append(String.format("    ... and %d more\n",
                            deviantVertices.size() - show));
                }
            }
        }

        sb.append("\nDegree distribution:\n");
        for (Map.Entry<Integer, Integer> e : degreeDistribution.entrySet()) {
            sb.append(String.format("  degree %d: %d vertices\n", e.getKey(), e.getValue()));
        }

        return sb.toString();
    }
}
