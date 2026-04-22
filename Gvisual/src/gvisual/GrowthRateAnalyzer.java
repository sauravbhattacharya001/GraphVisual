package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Tracks how graph metrics change over time: node count, Edge count,
 * density, and average clustering coefficient across time windows.
 * Useful for identifying growth phases, stability periods, and
 * network degradation.
 *
 * @author zalenix
 */
public class GrowthRateAnalyzer {

    private final TemporalGraph temporalGraph;
    private final int windowCount;

    /**
     * A snapshot of graph metrics at a particular time point.
     */
    public static class MetricSnapshot {
        public final long windowStart;
        public final int nodeCount;
        public final int edgeCount;
        public final double density;
        public final double avgClusteringCoefficient;

        public MetricSnapshot(long windowStart, int nodeCount, int edgeCount,
                              double density, double avgClusteringCoefficient) {
            this.windowStart = windowStart;
            this.nodeCount = nodeCount;
            this.edgeCount = edgeCount;
            this.density = density;
            this.avgClusteringCoefficient = avgClusteringCoefficient;
        }

        @Override
        public String toString() {
            return String.format("t=%d nodes=%d edges=%d density=%.4f clustering=%.4f",
                windowStart, nodeCount, edgeCount, density, avgClusteringCoefficient);
        }
    }

    /**
     * Creates a GrowthRateAnalyzer.
     *
     * @param temporalGraph the temporal graph to analyze
     * @param windowCount number of time windows
     * @throws IllegalArgumentException if arguments are invalid
     */
    public GrowthRateAnalyzer(TemporalGraph temporalGraph, int windowCount) {
        if (temporalGraph == null) {
            throw new IllegalArgumentException("TemporalGraph must not be null");
        }
        if (windowCount < 1) {
            throw new IllegalArgumentException("windowCount must be at least 1");
        }
        this.temporalGraph = temporalGraph;
        this.windowCount = windowCount;
    }

    /**
     * Computes metric snapshots for each time window.
     *
     * @return ordered list of metric snapshots
     */
    public List<MetricSnapshot> analyze() {
        List<Map.Entry<Long, Graph<String, Edge>>> windows =
            temporalGraph.generateWindows(windowCount);

        List<MetricSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<Long, Graph<String, Edge>> window : windows) {
            Graph<String, Edge> g = window.getValue();
            int nodes = g.getVertexCount();
            int edges = g.getEdgeCount();
            double density = computeDensity(nodes, edges);
            double clustering = computeAvgClustering(g);
            snapshots.add(new MetricSnapshot(window.getKey(), nodes, edges,
                density, clustering));
        }
        return snapshots;
    }

    /**
     * Computes the overall growth trend as a simple metric:
     * positive = growing, negative = shrinking, near zero = stable.
     * Based on linear regression of Edge count over windows.
     *
     * @return slope of Edge count over time windows
     */
    public double edgeGrowthRate() {
        List<MetricSnapshot> snapshots = analyze();
        if (snapshots.size() < 2) return 0.0;

        // Simple linear regression on Edge count
        int n = snapshots.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = snapshots.get(i).edgeCount;
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }
        double denom = n * sumXX - sumX * sumX;
        if (denom == 0) return 0.0;
        return (n * sumXY - sumX * sumY) / denom;
    }

    private static double computeDensity(int nodes, int edges) {
        if (nodes < 2) return 0.0;
        double maxEdges = (double) nodes * (nodes - 1) / 2.0;
        return edges / maxEdges;
    }

    private static double computeAvgClustering(Graph<String, Edge> g) {
        return GraphUtils.avgClusteringCoefficient(g);
    }
}
