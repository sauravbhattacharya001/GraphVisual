package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Analyzes network resilience by simulating node removal attacks and measuring
 * how the graph degrades. Supports three attack strategies:
 * <ul>
 *   <li><b>Random</b> — nodes removed in random order (averaged over multiple trials)</li>
 *   <li><b>Targeted (degree)</b> — highest-degree nodes removed first</li>
 *   <li><b>Targeted (betweenness)</b> — highest-betweenness nodes removed first
 *       (recalculated after each removal)</li>
 * </ul>
 *
 * <p>At each removal step the analyzer records the size of the largest connected
 * component, the number of components, and the global efficiency. This produces
 * a degradation curve that reveals how robust the network is against different
 * failure modes — a key metric in network science.</p>
 *
 * @author zalenix
 */
public class GraphResilienceAnalyzer {

    private final Graph<String, edge> graph;
    private List<ResilienceStep> degreeAttackCurve;
    private List<ResilienceStep> betweennessAttackCurve;
    private List<ResilienceStep> randomAttackCurve;
    private int randomTrials;
    private boolean computed;

    /**
     * Creates a new resilience analyzer for the given graph.
     *
     * @param graph the JUNG graph to analyze
     */
    public GraphResilienceAnalyzer(Graph<String, edge> graph) {
        this.graph = graph;
        this.randomTrials = 10;
        this.computed = false;
    }

    /**
     * Sets the number of random trials for averaging random attack results.
     *
     * @param trials number of trials (default 10)
     */
    public void setRandomTrials(int trials) {
        this.randomTrials = Math.max(1, trials);
    }

    /**
     * Runs all three attack simulations.
     */
    public void analyze() {
        degreeAttackCurve = simulateDegreeAttack();
        betweennessAttackCurve = simulateBetweennessAttack();
        randomAttackCurve = simulateRandomAttack(randomTrials);
        computed = true;
    }

    /**
     * Returns the degradation curve for targeted degree-based attack.
     */
    public List<ResilienceStep> getDegreeAttackCurve() {
        ensureComputed();
        return Collections.unmodifiableList(degreeAttackCurve);
    }

    /**
     * Returns the degradation curve for targeted betweenness-based attack.
     */
    public List<ResilienceStep> getBetweennessAttackCurve() {
        ensureComputed();
        return Collections.unmodifiableList(betweennessAttackCurve);
    }

    /**
     * Returns the averaged degradation curve for random node removal.
     */
    public List<ResilienceStep> getRandomAttackCurve() {
        ensureComputed();
        return Collections.unmodifiableList(randomAttackCurve);
    }

    /**
     * Computes the robustness index R for a given curve.
     * R = sum of (largest component fraction) / N.
     * Values near 1 indicate high resilience; near 0 indicate fragile networks.
     *
     * @param curve the degradation curve
     * @return robustness index in [0, 1]
     */
    public double computeRobustnessIndex(List<ResilienceStep> curve) {
        if (curve == null || curve.isEmpty()) return 0.0;
        int originalSize = curve.get(0).getTotalNodes();
        if (originalSize == 0) return 0.0;
        double sum = 0.0;
        for (ResilienceStep step : curve) {
            sum += (double) step.getLargestComponentSize() / originalSize;
        }
        return sum / originalSize;
    }

    /**
     * Returns a textual summary comparing the three attack strategies.
     */
    public String getSummary() {
        ensureComputed();
        StringBuilder sb = new StringBuilder();
        sb.append("=== Network Resilience Analysis ===\n\n");
        sb.append(String.format("Network: %d nodes, %d edges\n",
                graph.getVertexCount(), graph.getEdgeCount()));
        sb.append(String.format("Random trials: %d\n\n", randomTrials));

        double rDegree = computeRobustnessIndex(degreeAttackCurve);
        double rBetweenness = computeRobustnessIndex(betweennessAttackCurve);
        double rRandom = computeRobustnessIndex(randomAttackCurve);

        sb.append("Robustness Index R (higher = more resilient):\n");
        sb.append(String.format("  Random attack:      %.4f\n", rRandom));
        sb.append(String.format("  Degree attack:      %.4f\n", rDegree));
        sb.append(String.format("  Betweenness attack: %.4f\n", rBetweenness));
        sb.append("\n");

        // Find critical thresholds (when largest component drops below 50%)
        int originalSize = graph.getVertexCount();
        sb.append("Critical threshold (largest component < 50%):\n");
        sb.append(String.format("  Random:      %s\n", findThreshold(randomAttackCurve, originalSize, 0.5)));
        sb.append(String.format("  Degree:      %s\n", findThreshold(degreeAttackCurve, originalSize, 0.5)));
        sb.append(String.format("  Betweenness: %s\n", findThreshold(betweennessAttackCurve, originalSize, 0.5)));

        sb.append("\n");
        if (rRandom > rDegree * 1.5) {
            sb.append("Interpretation: Network is significantly more vulnerable to targeted\n");
            sb.append("attacks than random failures — typical of scale-free networks with hubs.\n");
        } else if (rRandom < rDegree * 1.1) {
            sb.append("Interpretation: Network shows similar resilience to random and targeted\n");
            sb.append("attacks — characteristic of homogeneous/random network topology.\n");
        } else {
            sb.append("Interpretation: Network shows moderate vulnerability to targeted attacks.\n");
        }

        return sb.toString();
    }

    /**
     * Exports the degradation curves as CSV for plotting.
     */
    public String exportCSV() {
        ensureComputed();
        StringBuilder sb = new StringBuilder();
        sb.append("step,nodes_removed,fraction_removed,");
        sb.append("degree_lcc,degree_components,degree_efficiency,");
        sb.append("betweenness_lcc,betweenness_components,betweenness_efficiency,");
        sb.append("random_lcc,random_components,random_efficiency\n");

        int maxSteps = Math.max(degreeAttackCurve.size(),
                Math.max(betweennessAttackCurve.size(), randomAttackCurve.size()));

        for (int i = 0; i < maxSteps; i++) {
            int originalSize = graph.getVertexCount();
            double fractionRemoved = originalSize > 0 ? (double) i / originalSize : 0;
            sb.append(String.format("%d,%d,%.4f", i, i, fractionRemoved));

            appendStepCSV(sb, degreeAttackCurve, i);
            appendStepCSV(sb, betweennessAttackCurve, i);
            appendStepCSV(sb, randomAttackCurve, i);
            sb.append("\n");
        }
        return sb.toString();
    }

    // --- Private simulation methods ---

    private List<ResilienceStep> simulateDegreeAttack() {
        Graph<String, edge> copy = copyGraph();
        List<ResilienceStep> curve = new ArrayList<>();
        int originalSize = copy.getVertexCount();

        curve.add(captureStep(copy, originalSize, 0, null));

        for (int step = 1; step <= originalSize; step++) {
            // Find highest-degree node
            String target = null;
            int maxDeg = -1;
            for (String v : copy.getVertices()) {
                int deg = copy.degree(v);
                if (deg > maxDeg) {
                    maxDeg = deg;
                    target = v;
                }
            }
            if (target == null) break;
            copy.removeVertex(target);
            curve.add(captureStep(copy, originalSize, step, target));
        }
        return curve;
    }

    private List<ResilienceStep> simulateBetweennessAttack() {
        Graph<String, edge> copy = copyGraph();
        List<ResilienceStep> curve = new ArrayList<>();
        int originalSize = copy.getVertexCount();

        curve.add(captureStep(copy, originalSize, 0, null));

        for (int step = 1; step <= originalSize; step++) {
            if (copy.getVertexCount() == 0) break;
            // Compute betweenness on current graph
            Map<String, Double> bc = computeBetweenness(copy);
            String target = bc.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (target == null) break;
            copy.removeVertex(target);
            curve.add(captureStep(copy, originalSize, step, target));
        }
        return curve;
    }

    private List<ResilienceStep> simulateRandomAttack(int trials) {
        int originalSize = graph.getVertexCount();
        if (originalSize == 0) {
            return Collections.singletonList(new ResilienceStep(0, 0, 0, 0, 0.0, null));
        }

        // Accumulate across trials
        double[][] accum = new double[originalSize + 1][3]; // lcc, components, efficiency

        Random rng = new Random(42);
        for (int t = 0; t < trials; t++) {
            Graph<String, edge> copy = copyGraph();
            List<String> vertices = new ArrayList<>(copy.getVertices());
            Collections.shuffle(vertices, rng);

            accum[0][0] += largestComponentSize(copy);
            accum[0][1] += countComponents(copy);
            accum[0][2] += globalEfficiency(copy);

            for (int step = 0; step < vertices.size(); step++) {
                copy.removeVertex(vertices.get(step));
                accum[step + 1][0] += largestComponentSize(copy);
                accum[step + 1][1] += countComponents(copy);
                accum[step + 1][2] += globalEfficiency(copy);
            }
        }

        List<ResilienceStep> curve = new ArrayList<>();
        for (int i = 0; i <= originalSize; i++) {
            curve.add(new ResilienceStep(
                    i, originalSize - i,
                    (int) Math.round(accum[i][0] / trials),
                    (int) Math.round(accum[i][1] / trials),
                    accum[i][2] / trials,
                    null));
        }
        return curve;
    }

    private Graph<String, edge> copyGraph() {
        return GraphUtils.copyGraph(graph);
    }

    private ResilienceStep captureStep(Graph<String, edge> g, int originalSize,
                                        int step, String removedNode) {
        return new ResilienceStep(
                step,
                g.getVertexCount(),
                largestComponentSize(g),
                countComponents(g),
                globalEfficiency(g),
                removedNode);
    }

    private int largestComponentSize(Graph<String, edge> g) {
        if (g.getVertexCount() == 0) return 0;
        return GraphUtils.findLargestComponent(g).size();
    }

    private int countComponents(Graph<String, edge> g) {
        if (g.getVertexCount() == 0) return 0;
        return GraphUtils.findComponents(g).size();
    }

    private double globalEfficiency(Graph<String, edge> g) {
        return GraphUtils.globalEfficiency(g);
    }

    private Map<String, Double> computeBetweenness(Graph<String, edge> g) {
        return GraphUtils.computeBetweenness(g);
    }

    private String findThreshold(List<ResilienceStep> curve, int originalSize, double fraction) {
        if (originalSize == 0) return "N/A";
        for (ResilienceStep step : curve) {
            if ((double) step.getLargestComponentSize() / originalSize < fraction) {
                return String.format("%d nodes removed (%.1f%%)",
                        step.getNodesRemoved(),
                        100.0 * step.getNodesRemoved() / originalSize);
            }
        }
        return "never reached";
    }

    private void appendStepCSV(StringBuilder sb, List<ResilienceStep> curve, int i) {
        if (i < curve.size()) {
            ResilienceStep s = curve.get(i);
            sb.append(String.format(",%d,%d,%.4f",
                    s.getLargestComponentSize(), s.getComponentCount(), s.getGlobalEfficiency()));
        } else {
            sb.append(",,,");
        }
    }

    private void ensureComputed() {
        if (!computed) {
            throw new IllegalStateException("Call analyze() before accessing results.");
        }
    }

    // --- Inner class for step data ---

    /**
     * Represents the state of the network at one step of a removal simulation.
     */
    public static class ResilienceStep {
        private final int nodesRemoved;
        private final int totalNodes;
        private final int largestComponentSize;
        private final int componentCount;
        private final double globalEfficiency;
        private final String removedNode;

        public ResilienceStep(int nodesRemoved, int totalNodes, int largestComponentSize,
                              int componentCount, double globalEfficiency, String removedNode) {
            this.nodesRemoved = nodesRemoved;
            this.totalNodes = totalNodes;
            this.largestComponentSize = largestComponentSize;
            this.componentCount = componentCount;
            this.globalEfficiency = globalEfficiency;
            this.removedNode = removedNode;
        }

        public int getNodesRemoved() { return nodesRemoved; }
        public int getTotalNodes() { return totalNodes; }
        public int getLargestComponentSize() { return largestComponentSize; }
        public int getComponentCount() { return componentCount; }
        public double getGlobalEfficiency() { return globalEfficiency; }
        public String getRemovedNode() { return removedNode; }

        @Override
        public String toString() {
            return String.format("Step %d: removed=%s, remaining=%d, LCC=%d, components=%d, efficiency=%.4f",
                    nodesRemoved, removedNode != null ? removedNode : "—",
                    totalNodes, largestComponentSize, componentCount, globalEfficiency);
        }
    }
}
