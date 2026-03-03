package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.*;
import java.util.stream.Collectors;

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
        Graph<String, edge> copy = new UndirectedSparseGraph<>();
        for (String v : graph.getVertices()) {
            copy.addVertex(v);
        }
        for (edge e : graph.getEdges()) {
            Collection<String> endpoints = graph.getEndpoints(e);
            Iterator<String> it = endpoints.iterator();
            String v1 = it.next();
            String v2 = it.next();
            edge newEdge = new edge(e.getType(), v1, v2);
            newEdge.setWeight(e.getWeight());
            newEdge.setLabel(e.getLabel());
            copy.addEdge(newEdge, v1, v2);
        }
        return copy;
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
        Set<String> visited = new HashSet<>();
        int maxSize = 0;
        for (String v : g.getVertices()) {
            if (!visited.contains(v)) {
                int size = bfsSize(g, v, visited);
                if (size > maxSize) maxSize = size;
            }
        }
        return maxSize;
    }

    private int countComponents(Graph<String, edge> g) {
        if (g.getVertexCount() == 0) return 0;
        Set<String> visited = new HashSet<>();
        int count = 0;
        for (String v : g.getVertices()) {
            if (!visited.contains(v)) {
                bfsSize(g, v, visited);
                count++;
            }
        }
        return count;
    }

    private int bfsSize(Graph<String, edge> g, String start, Set<String> visited) {
        Queue<String> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start);
        int size = 0;
        while (!queue.isEmpty()) {
            String v = queue.poll();
            size++;
            for (String n : g.getNeighbors(v)) {
                if (!visited.contains(n)) {
                    visited.add(n);
                    queue.add(n);
                }
            }
        }
        return size;
    }

    private double globalEfficiency(Graph<String, edge> g) {
        int n = g.getVertexCount();
        if (n <= 1) return 0.0;
        double sum = 0.0;
        List<String> nodes = new ArrayList<>(g.getVertices());
        for (int i = 0; i < nodes.size(); i++) {
            Map<String, Integer> dist = bfsDistances(g, nodes.get(i));
            for (int j = i + 1; j < nodes.size(); j++) {
                Integer d = dist.get(nodes.get(j));
                if (d != null && d > 0) {
                    sum += 1.0 / d;
                }
            }
        }
        return (2.0 * sum) / (n * (n - 1));
    }

    private Map<String, Integer> bfsDistances(Graph<String, edge> g, String start) {
        Map<String, Integer> dist = new HashMap<>();
        Queue<String> queue = new LinkedList<>();
        dist.put(start, 0);
        queue.add(start);
        while (!queue.isEmpty()) {
            String v = queue.poll();
            int d = dist.get(v);
            for (String n : g.getNeighbors(v)) {
                if (!dist.containsKey(n)) {
                    dist.put(n, d + 1);
                    queue.add(n);
                }
            }
        }
        return dist;
    }

    private Map<String, Double> computeBetweenness(Graph<String, edge> g) {
        Map<String, Double> bc = new HashMap<>();
        for (String v : g.getVertices()) bc.put(v, 0.0);

        for (String s : g.getVertices()) {
            Stack<String> stack = new Stack<>();
            Map<String, List<String>> pred = new HashMap<>();
            Map<String, Double> sigma = new HashMap<>();
            Map<String, Integer> dist = new HashMap<>();
            Map<String, Double> delta = new HashMap<>();

            for (String v : g.getVertices()) {
                pred.put(v, new ArrayList<>());
                sigma.put(v, 0.0);
                dist.put(v, -1);
                delta.put(v, 0.0);
            }
            sigma.put(s, 1.0);
            dist.put(s, 0);

            Queue<String> queue = new LinkedList<>();
            queue.add(s);

            while (!queue.isEmpty()) {
                String v = queue.poll();
                stack.push(v);
                for (String w : g.getNeighbors(v)) {
                    if (dist.get(w) < 0) {
                        queue.add(w);
                        dist.put(w, dist.get(v) + 1);
                    }
                    if (dist.get(w) == dist.get(v) + 1) {
                        sigma.put(w, sigma.get(w) + sigma.get(v));
                        pred.get(w).add(v);
                    }
                }
            }

            while (!stack.isEmpty()) {
                String w = stack.pop();
                for (String v : pred.get(w)) {
                    delta.put(v, delta.get(v) + (sigma.get(v) / sigma.get(w)) * (1.0 + delta.get(w)));
                }
                if (!w.equals(s)) {
                    bc.put(w, bc.get(w) + delta.get(w));
                }
            }
        }
        // Normalize for undirected graph
        for (String v : g.getVertices()) {
            bc.put(v, bc.get(v) / 2.0);
        }
        return bc;
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
