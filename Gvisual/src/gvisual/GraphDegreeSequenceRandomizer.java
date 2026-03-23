package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.*;

/**
 * Generates randomized versions of a graph that preserve each node's degree
 * (the degree sequence). This is the standard "edge-switching" / "stub-rewiring"
 * Markov chain method used in network science for null-model testing.
 *
 * <p>Use cases:</p>
 * <ul>
 *   <li>Null-model comparison — test whether an observed property (clustering,
 *       motif frequency, community structure) is significant or just a consequence
 *       of the degree sequence.</li>
 *   <li>Anonymization — produce structurally similar graphs with different wiring.</li>
 *   <li>Ensemble generation — create many random realizations for Monte Carlo tests.</li>
 * </ul>
 *
 * <h3>Algorithm</h3>
 * <p>Repeatedly picks two random edges (u-v) and (x-y), and attempts to swap them
 * to (u-x) and (v-y) or (u-y) and (v-x). The swap is rejected if it would create
 * a self-loop or multi-edge. After enough swaps the graph is well-mixed.</p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * GraphDegreeSequenceRandomizer rand = new GraphDegreeSequenceRandomizer();
 * Graph<String, Edge> original = ...;
 *
 * // Single randomized copy
 * Graph<String, Edge> shuffled = rand.randomize(original, 10); // 10× |E| swaps
 *
 * // Ensemble of 100 random graphs
 * List<Graph<String, Edge>> ensemble = rand.ensemble(original, 100, 10);
 * }</pre>
 *
 * @author GraphVisual Feature Builder
 */
public class GraphDegreeSequenceRandomizer {

    private final Random rng;

    /** Creates a randomizer with a new default Random. */
    public GraphDegreeSequenceRandomizer() {
        this.rng = new Random();
    }

    /** Creates a randomizer with the given seed for reproducibility. */
    public GraphDegreeSequenceRandomizer(long seed) {
        this.rng = new Random(seed);
    }

    /**
     * Produce a single randomized copy of {@code source} that preserves
     * the degree of every vertex.
     *
     * @param source       the original graph (undirected, simple)
     * @param swapFactor   number of swap attempts = swapFactor × |E|
     * @return a new graph with the same degree sequence but randomized wiring
     */
    public Graph<String, Edge> randomize(Graph<String, Edge> source, int swapFactor) {
        Graph<String, Edge> copy = deepCopy(source);
        int attempts = Math.max(1, swapFactor) * copy.getEdgeCount();
        performSwaps(copy, attempts);
        return copy;
    }

    /**
     * Generate an ensemble of randomized graphs.
     *
     * @param source       the original graph
     * @param count        how many random graphs to produce
     * @param swapFactor   swap attempts per graph = swapFactor × |E|
     * @return list of randomized graphs
     */
    public List<Graph<String, Edge>> ensemble(Graph<String, Edge> source,
                                               int count, int swapFactor) {
        List<Graph<String, Edge>> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            results.add(randomize(source, swapFactor));
        }
        return results;
    }

    /**
     * Compute a summary comparing an observed metric value against an ensemble
     * of randomized graphs. Returns a {@link NullModelSummary} with mean,
     * standard deviation, z-score, and empirical p-value.
     *
     * @param observedValue the metric computed on the original graph
     * @param ensembleValues the same metric computed on each ensemble member
     * @return summary statistics
     */
    public NullModelSummary computeSignificance(double observedValue,
                                                 double[] ensembleValues) {
        double sum = 0, sumSq = 0;
        int above = 0;
        for (double v : ensembleValues) {
            sum += v;
            sumSq += v * v;
            if (v >= observedValue) above++;
        }
        int n = ensembleValues.length;
        double mean = sum / n;
        double variance = (sumSq / n) - (mean * mean);
        double stddev = Math.sqrt(Math.max(0, variance));
        double zScore = stddev > 0 ? (observedValue - mean) / stddev : 0;
        double pValue = (double) above / n;
        return new NullModelSummary(observedValue, mean, stddev, zScore, pValue, n);
    }

    // ---- internals ----

    private Graph<String, Edge> deepCopy(Graph<String, Edge> source) {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (String v : source.getVertices()) {
            g.addVertex(v);
        }
        int idx = 0;
        for (Edge e : source.getEdges()) {
            String v1 = source.getEndpoints(e).getFirst();
            String v2 = source.getEndpoints(e).getSecond();
            Edge ne = new Edge("f", v1, v2);
            ne.setWeight(e.getWeight());
            ne.setLabel("re_" + (idx++));
            g.addEdge(ne, v1, v2);
        }
        return g;
    }

    private void performSwaps(Graph<String, Edge> g, int attempts) {
        List<Edge> edges = new ArrayList<>(g.getEdges());
        if (edges.size() < 2) return;

        int successful = 0;
        for (int i = 0; i < attempts; i++) {
            int a = rng.nextInt(edges.size());
            int b = rng.nextInt(edges.size());
            if (a == b) continue;

            Edge e1 = edges.get(a);
            Edge e2 = edges.get(b);

            String u = g.getEndpoints(e1).getFirst();
            String v = g.getEndpoints(e1).getSecond();
            String x = g.getEndpoints(e2).getFirst();
            String y = g.getEndpoints(e2).getSecond();

            // Try swap variant: (u-x, v-y)
            boolean variant1 = canSwap(g, u, x, v, y);
            // Try swap variant: (u-y, v-x)
            boolean variant2 = !variant1 && canSwap(g, u, y, v, x);

            String newU1, newV1, newU2, newV2;
            if (variant1) {
                newU1 = u; newV1 = x; newU2 = v; newV2 = y;
            } else if (variant2) {
                newU1 = u; newV1 = y; newU2 = v; newV2 = x;
            } else {
                continue;
            }

            // Perform swap
            g.removeEdge(e1);
            g.removeEdge(e2);

            Edge ne1 = new Edge("f", newU1, newV1);
            ne1.setLabel("sw_" + successful + "a");
            Edge ne2 = new Edge("f", newU2, newV2);
            ne2.setLabel("sw_" + successful + "b");

            g.addEdge(ne1, newU1, newV1);
            g.addEdge(ne2, newU2, newV2);

            edges.set(a, ne1);
            edges.set(b, ne2);
            successful++;
        }
    }

    private boolean canSwap(Graph<String, Edge> g,
                            String a, String b, String c, String d) {
        // No self-loops
        if (a.equals(b) || c.equals(d)) return false;
        // No multi-edges
        if (g.findEdge(a, b) != null || g.findEdge(c, d) != null) return false;
        return true;
    }

    // ---- Result class ----

    /**
     * Summary of null-model significance testing.
     */
    public static class NullModelSummary {
        private final double observedValue;
        private final double ensembleMean;
        private final double ensembleStdDev;
        private final double zScore;
        private final double pValue;
        private final int ensembleSize;

        public NullModelSummary(double observedValue, double ensembleMean,
                                double ensembleStdDev, double zScore,
                                double pValue, int ensembleSize) {
            this.observedValue = observedValue;
            this.ensembleMean = ensembleMean;
            this.ensembleStdDev = ensembleStdDev;
            this.zScore = zScore;
            this.pValue = pValue;
            this.ensembleSize = ensembleSize;
        }

        public double getObservedValue() { return observedValue; }
        public double getEnsembleMean() { return ensembleMean; }
        public double getEnsembleStdDev() { return ensembleStdDev; }
        public double getZScore() { return zScore; }
        public double getPValue() { return pValue; }
        public int getEnsembleSize() { return ensembleSize; }

        /**
         * Whether the observed value is statistically significant at the given alpha.
         */
        public boolean isSignificant(double alpha) {
            return pValue < alpha;
        }

        @Override
        public String toString() {
            return String.format(
                "NullModelSummary{observed=%.4f, mean=%.4f, std=%.4f, z=%.2f, p=%.4f, n=%d}",
                observedValue, ensembleMean, ensembleStdDev, zScore, pValue, ensembleSize);
        }
    }
}
