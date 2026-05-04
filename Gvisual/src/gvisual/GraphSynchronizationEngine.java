package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GraphSynchronizationEngine — autonomous Kuramoto oscillator synchronization
 * engine that simulates coupled oscillators on a network topology to analyze
 * how network structure affects synchronization dynamics.
 *
 * <h3>Seven Analysis Engines:</h3>
 * <ol>
 *   <li><b>Oscillator Initializer</b> — assigns random natural frequencies ω_i
 *       from configurable distribution and initializes phases θ_i in [0,2π)</li>
 *   <li><b>Kuramoto Simulator</b> — implements dθ_i/dt = ω_i + (K/N_i) Σ_j sin(θ_j−θ_i)
 *       via Euler integration with configurable time-steps and step size</li>
 *   <li><b>Order Parameter Tracker</b> — computes r(t) = |1/N Σ exp(iθ_j)| at
 *       each step; r=1 perfect sync, r≈0 incoherent</li>
 *   <li><b>Critical Coupling Estimator</b> — sweeps K from 0 to K_max, finds K_c
 *       where order parameter first exceeds threshold via bisection refinement</li>
 *   <li><b>Synchronization Barrier Detector</b> — identifies nodes/edges that
 *       hinder synchronization: frequency outliers, bottleneck edges, weak bridges,
 *       isolated clusters</li>
 *   <li><b>Desynchronizer Profiler</b> — ranks nodes whose removal would most
 *       improve synchronization via leave-one-out simulation</li>
 *   <li><b>Insight Generator</b> — autonomous insights about sync dynamics,
 *       community sync, limiting factors, recommendations</li>
 * </ol>
 *
 * @author zalenix
 */
public class GraphSynchronizationEngine {

    // -- Constants -----------------------------------------------------------
    private static final double TWO_PI = 2.0 * Math.PI;

    // -- Configuration -------------------------------------------------------
    private double couplingStrength = 1.0;
    private int timeSteps = 200;
    private double stepSize = 0.05;
    private double couplingMax = 5.0;
    private int couplingSteps = 20;
    private Random rng = new Random(42);

    // -- Builder-style setters -----------------------------------------------

    public GraphSynchronizationEngine setCouplingStrength(double k) {
        this.couplingStrength = k; return this;
    }

    public GraphSynchronizationEngine setTimeSteps(int n) {
        this.timeSteps = Math.max(1, n); return this;
    }

    public GraphSynchronizationEngine setStepSize(double dt) {
        this.stepSize = dt; return this;
    }

    public GraphSynchronizationEngine setCouplingMax(double kMax) {
        this.couplingMax = kMax; return this;
    }

    public GraphSynchronizationEngine setCouplingSteps(int n) {
        this.couplingSteps = Math.max(1, n); return this;
    }

    public GraphSynchronizationEngine setRandomSeed(long seed) {
        this.rng = new Random(seed); return this;
    }

    public GraphSynchronizationEngine setRng(Random rng) {
        this.rng = rng; return this;
    }

    // ====================================================================
    // Inner classes
    // ====================================================================

    /** Barrier type classification. */
    public enum BarrierType {
        FREQUENCY_OUTLIER, BOTTLENECK_EDGE, WEAK_BRIDGE, ISOLATED_CLUSTER
    }

    /** A synchronization barrier. */
    public static class SyncBarrier {
        public final BarrierType type;
        public final String description;
        public final double impact;
        public final List<String> involvedNodes;

        public SyncBarrier(BarrierType type, String description, double impact,
                           List<String> involvedNodes) {
            this.type = type;
            this.description = description;
            this.impact = impact;
            this.involvedNodes = involvedNodes;
        }
    }

    /** A node identified as a desynchronizer. */
    public static class DesyncNode {
        public final String nodeId;
        public final double impactScore;
        public final String reason;

        public DesyncNode(String nodeId, double impactScore, String reason) {
            this.nodeId = nodeId;
            this.impactScore = impactScore;
            this.reason = reason;
        }
    }

    /** Full synchronization analysis report. */
    public static class SynchronizationReport {
        public final Map<String, Double> naturalFrequencies;
        public final Map<String, Double> finalPhases;
        public final List<Double> orderParameterTrajectory;
        public final double finalOrderParameter;
        public final double criticalCoupling;
        public final Map<Double, Double> couplingCurve;
        public final List<SyncBarrier> barriers;
        public final List<DesyncNode> desynchronizers;
        public final double healthScore;
        public final List<String> insights;
        public final int nodeCount;
        public final int edgeCount;

        public SynchronizationReport(Map<String, Double> naturalFrequencies,
                                     Map<String, Double> finalPhases,
                                     List<Double> orderParameterTrajectory,
                                     double finalOrderParameter,
                                     double criticalCoupling,
                                     Map<Double, Double> couplingCurve,
                                     List<SyncBarrier> barriers,
                                     List<DesyncNode> desynchronizers,
                                     double healthScore,
                                     List<String> insights,
                                     int nodeCount, int edgeCount) {
            this.naturalFrequencies = naturalFrequencies;
            this.finalPhases = finalPhases;
            this.orderParameterTrajectory = orderParameterTrajectory;
            this.finalOrderParameter = finalOrderParameter;
            this.criticalCoupling = criticalCoupling;
            this.couplingCurve = couplingCurve;
            this.barriers = barriers;
            this.desynchronizers = desynchronizers;
            this.healthScore = healthScore;
            this.insights = insights;
            this.nodeCount = nodeCount;
            this.edgeCount = edgeCount;
        }
    }

    // ====================================================================
    // Main entry point
    // ====================================================================

    public SynchronizationReport analyze(Graph<String, Edge> graph) {
        List<String> nodes = new ArrayList<>(graph.getVertices());
        Collections.sort(nodes);
        int n = nodes.size();
        int edgeCount = graph.getEdgeCount();

        if (n == 0) {
            return emptyReport(0, 0);
        }
        if (n == 1) {
            Map<String, Double> freq = new LinkedHashMap<>();
            Map<String, Double> phase = new LinkedHashMap<>();
            freq.put(nodes.get(0), 0.0);
            phase.put(nodes.get(0), 0.0);
            List<Double> trajectory = new ArrayList<>();
            trajectory.add(1.0);
            Map<Double, Double> curve = new LinkedHashMap<>();
            curve.put(0.0, 1.0);
            return new SynchronizationReport(freq, phase, trajectory, 1.0, 0.0,
                    curve, new ArrayList<>(), new ArrayList<>(), 100.0,
                    Collections.singletonList("Single node is trivially synchronized."),
                    1, 0);
        }

        Map<String, Integer> nodeIndex = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) nodeIndex.put(nodes.get(i), i);

        // Build adjacency list
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
        for (Edge e : graph.getEdges()) {
            String v1 = e.getVertex1(), v2 = e.getVertex2();
            if (nodeIndex.containsKey(v1) && nodeIndex.containsKey(v2)) {
                int i1 = nodeIndex.get(v1), i2 = nodeIndex.get(v2);
                adj.get(i1).add(i2);
                adj.get(i2).add(i1);
            }
        }

        // Engine 1: Oscillator Initializer
        double[] omega = new double[n];
        double[] theta = new double[n];
        for (int i = 0; i < n; i++) {
            omega[i] = rng.nextGaussian();
            theta[i] = rng.nextDouble() * TWO_PI;
        }

        // Engine 2 & 3: Kuramoto Simulator + Order Parameter Tracker
        SimResult mainSim = simulate(theta.clone(), omega, adj, couplingStrength,
                                      timeSteps, stepSize, n);

        Map<String, Double> naturalFrequencies = new LinkedHashMap<>();
        Map<String, Double> finalPhases = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            naturalFrequencies.put(nodes.get(i), omega[i]);
            finalPhases.put(nodes.get(i), mainSim.finalTheta[i]);
        }

        // Engine 4: Critical Coupling Estimator
        // Use consistent initial phases across all K values so the coupling
        // curve reflects only the effect of coupling strength, not random
        // initial conditions.  Previous code generated new random phases per
        // sweep point via the shared rng, making the curve noisy and K_c
        // estimation unreliable (a low-K trial could randomly get a favorable
        // starting configuration and appear more synchronized).
        Map<Double, Double> couplingCurve = new LinkedHashMap<>();
        double criticalCoupling = couplingMax;
        int shortSteps = Math.max(50, timeSteps / 2);
        double[] sweepBaseTheta = new double[n];
        for (int i = 0; i < n; i++) sweepBaseTheta[i] = rng.nextDouble() * TWO_PI;
        for (int s = 0; s <= couplingSteps; s++) {
            double k = couplingMax * s / couplingSteps;
            double[] thetaCopy = sweepBaseTheta.clone();
            SimResult sr = simulate(thetaCopy, omega, adj, k, shortSteps, stepSize, n);
            double r = sr.trajectory.get(sr.trajectory.size() - 1);
            couplingCurve.put(roundK(k), r);
        }
        // Find K_c by scanning for first r > 0.7
        boolean foundKc = false;
        for (Map.Entry<Double, Double> entry : couplingCurve.entrySet()) {
            if (entry.getValue() >= 0.7) {
                criticalCoupling = entry.getKey();
                foundKc = true;
                break;
            }
        }
        if (!foundKc) criticalCoupling = couplingMax;

        // Engine 5: Synchronization Barrier Detector
        List<SyncBarrier> barriers = detectBarriers(nodes, omega, adj, nodeIndex, graph, n);

        // Engine 6: Desynchronizer Profiler
        List<DesyncNode> desynchronizers = profileDesynchronizers(nodes, omega, adj,
                nodeIndex, graph, mainSim.finalR, n);

        // Engine 7: Insight Generator
        List<String> insights = generateInsights(mainSim.finalR, criticalCoupling,
                couplingMax, barriers, desynchronizers, n, edgeCount, adj, foundKc);

        // Health Score
        double healthScore = computeHealthScore(mainSim.finalR, criticalCoupling,
                couplingMax, barriers, n, edgeCount, adj);

        return new SynchronizationReport(naturalFrequencies, finalPhases,
                mainSim.trajectory, mainSim.finalR, criticalCoupling,
                couplingCurve, barriers, desynchronizers, healthScore,
                insights, n, edgeCount);
    }

    // ====================================================================
    // Simulation core
    // ====================================================================

    private static class SimResult {
        final double[] finalTheta;
        final List<Double> trajectory;
        final double finalR;
        SimResult(double[] finalTheta, List<Double> trajectory, double finalR) {
            this.finalTheta = finalTheta;
            this.trajectory = trajectory;
            this.finalR = finalR;
        }
    }

    private SimResult simulate(double[] theta, double[] omega,
                                List<List<Integer>> adj, double K,
                                int steps, double dt, int n) {
        List<Double> trajectory = new ArrayList<>();
        trajectory.add(orderParameter(theta, n));
        double[] thetaCopy = theta.clone();
        for (int t = 0; t < steps; t++) {
            double[] dTheta = new double[n];
            for (int i = 0; i < n; i++) {
                double coupling = 0.0;
                List<Integer> neighbors = adj.get(i);
                int deg = neighbors.size();
                if (deg > 0) {
                    for (int j : neighbors) {
                        coupling += Math.sin(thetaCopy[j] - thetaCopy[i]);
                    }
                    coupling = K * coupling / deg;
                }
                dTheta[i] = omega[i] + coupling;
            }
            for (int i = 0; i < n; i++) {
                thetaCopy[i] += dTheta[i] * dt;
                // Wrap to [0, 2π)
                thetaCopy[i] = thetaCopy[i] % TWO_PI;
                if (thetaCopy[i] < 0) thetaCopy[i] += TWO_PI;
            }
            trajectory.add(orderParameter(thetaCopy, n));
        }
        double finalR = trajectory.get(trajectory.size() - 1);
        return new SimResult(thetaCopy, trajectory, finalR);
    }

    private double orderParameter(double[] theta, int n) {
        if (n == 0) return 0.0;
        double realSum = 0.0, imagSum = 0.0;
        for (int i = 0; i < n; i++) {
            realSum += Math.cos(theta[i]);
            imagSum += Math.sin(theta[i]);
        }
        return Math.sqrt(realSum * realSum + imagSum * imagSum) / n;
    }

    // ====================================================================
    // Engine 5: Barrier Detection
    // ====================================================================

    private List<SyncBarrier> detectBarriers(List<String> nodes, double[] omega,
                                              List<List<Integer>> adj,
                                              Map<String, Integer> nodeIndex,
                                              Graph<String, Edge> graph, int n) {
        List<SyncBarrier> barriers = new ArrayList<>();

        // Frequency outliers (|ω| > mean + 2*std)
        double mean = 0;
        for (double w : omega) mean += w;
        mean /= n;
        double variance = 0;
        for (double w : omega) variance += (w - mean) * (w - mean);
        variance /= n;
        double std = Math.sqrt(variance);
        double threshold = std > 0 ? 2.0 * std : 1.0;

        for (int i = 0; i < n; i++) {
            if (Math.abs(omega[i] - mean) > threshold) {
                double impact = Math.min(1.0, Math.abs(omega[i] - mean) / (3.0 * (std > 0 ? std : 1.0)));
                barriers.add(new SyncBarrier(BarrierType.FREQUENCY_OUTLIER,
                        String.format("Node %s has outlier frequency ω=%.3f (mean=%.3f, σ=%.3f)",
                                nodes.get(i), omega[i], mean, std),
                        impact, Collections.singletonList(nodes.get(i))));
            }
        }

        // Bottleneck edges (bridges via DFS)
        boolean[] visited = new boolean[n];
        int[] disc = new int[n], low = new int[n], parent = new int[n];
        Arrays.fill(parent, -1);
        int[] timer = {0};
        List<int[]> bridges = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                bridgeDFS(i, adj, visited, disc, low, parent, timer, bridges);
            }
        }
        for (int[] bridge : bridges) {
            String v1 = nodes.get(bridge[0]);
            String v2 = nodes.get(bridge[1]);
            barriers.add(new SyncBarrier(BarrierType.BOTTLENECK_EDGE,
                    String.format("Bridge edge %s--%s is a synchronization bottleneck", v1, v2),
                    0.8, Arrays.asList(v1, v2)));
        }

        // Isolated clusters (disconnected components with size < n/3)
        List<List<Integer>> components = findComponents(adj, n);
        if (components.size() > 1) {
            for (List<Integer> comp : components) {
                List<String> compNodes = new ArrayList<>();
                for (int idx : comp) compNodes.add(nodes.get(idx));
                barriers.add(new SyncBarrier(BarrierType.ISOLATED_CLUSTER,
                        String.format("Isolated cluster of %d nodes cannot synchronize with main network",
                                comp.size()),
                        0.9, compNodes));
            }
        }

        // Weak bridges (articulation points)
        boolean[] isAP = findArticulationPoints(adj, n);
        for (int i = 0; i < n; i++) {
            if (isAP[i]) {
                barriers.add(new SyncBarrier(BarrierType.WEAK_BRIDGE,
                        String.format("Node %s is an articulation point — removing it fragments synchronization",
                                nodes.get(i)),
                        0.7, Collections.singletonList(nodes.get(i))));
            }
        }

        barriers.sort((a, b) -> Double.compare(b.impact, a.impact));
        return barriers;
    }

    private void bridgeDFS(int u, List<List<Integer>> adj, boolean[] visited,
                           int[] disc, int[] low, int[] parent, int[] timer,
                           List<int[]> bridges) {
        visited[u] = true;
        disc[u] = low[u] = timer[0]++;
        for (int v : adj.get(u)) {
            if (!visited[v]) {
                parent[v] = u;
                bridgeDFS(v, adj, visited, disc, low, parent, timer, bridges);
                low[u] = Math.min(low[u], low[v]);
                if (low[v] > disc[u]) {
                    bridges.add(new int[]{u, v});
                }
            } else if (v != parent[u]) {
                low[u] = Math.min(low[u], disc[v]);
            }
        }
    }

    private boolean[] findArticulationPoints(List<List<Integer>> adj, int n) {
        boolean[] visited = new boolean[n];
        int[] disc = new int[n], low = new int[n], parent = new int[n];
        boolean[] ap = new boolean[n];
        Arrays.fill(parent, -1);
        int[] timer = {0};
        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                apDFS(i, adj, visited, disc, low, parent, ap, timer);
            }
        }
        return ap;
    }

    private void apDFS(int u, List<List<Integer>> adj, boolean[] visited,
                       int[] disc, int[] low, int[] parent, boolean[] ap, int[] timer) {
        visited[u] = true;
        disc[u] = low[u] = timer[0]++;
        int children = 0;
        for (int v : adj.get(u)) {
            if (!visited[v]) {
                children++;
                parent[v] = u;
                apDFS(v, adj, visited, disc, low, parent, ap, timer);
                low[u] = Math.min(low[u], low[v]);
                if (parent[u] == -1 && children > 1) ap[u] = true;
                if (parent[u] != -1 && low[v] >= disc[u]) ap[u] = true;
            } else if (v != parent[u]) {
                low[u] = Math.min(low[u], disc[v]);
            }
        }
    }

    private List<List<Integer>> findComponents(List<List<Integer>> adj, int n) {
        boolean[] visited = new boolean[n];
        List<List<Integer>> components = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                List<Integer> comp = new ArrayList<>();
                Queue<Integer> queue = new LinkedList<>();
                queue.add(i);
                visited[i] = true;
                while (!queue.isEmpty()) {
                    int u = queue.poll();
                    comp.add(u);
                    for (int v : adj.get(u)) {
                        if (!visited[v]) {
                            visited[v] = true;
                            queue.add(v);
                        }
                    }
                }
                components.add(comp);
            }
        }
        return components;
    }

    // ====================================================================
    // Engine 6: Desynchronizer Profiler
    // ====================================================================

    private List<DesyncNode> profileDesynchronizers(List<String> nodes, double[] omega,
                                                     List<List<Integer>> adj,
                                                     Map<String, Integer> nodeIndex,
                                                     Graph<String, Edge> graph,
                                                     double baseR, int n) {
        List<DesyncNode> result = new ArrayList<>();
        if (n <= 2) return result;

        int maxCheck = Math.min(n, 30); // limit for performance
        // Rank candidates: prefer high-frequency-deviation nodes
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < n; i++) candidates.add(i);
        double[] absOmega = new double[n];
        for (int i = 0; i < n; i++) absOmega[i] = Math.abs(omega[i]);
        candidates.sort((a, b) -> Double.compare(absOmega[b], absOmega[a]));
        candidates = candidates.subList(0, maxCheck);

        int shortSteps = Math.max(30, timeSteps / 4);
        for (int removeIdx : candidates) {
            // Build reduced adjacency without node removeIdx
            int rn = n - 1;
            if (rn == 0) continue;
            double[] rOmega = new double[rn];
            double[] rTheta = new double[rn];
            List<List<Integer>> rAdj = new ArrayList<>();
            int[] mapping = new int[n]; // old -> new
            Arrays.fill(mapping, -1);
            int idx = 0;
            for (int i = 0; i < n; i++) {
                if (i == removeIdx) continue;
                mapping[i] = idx;
                rOmega[idx] = omega[i];
                rTheta[idx] = rng.nextDouble() * TWO_PI;
                idx++;
            }
            for (int i = 0; i < rn; i++) rAdj.add(new ArrayList<>());
            for (int i = 0; i < n; i++) {
                if (i == removeIdx) continue;
                for (int j : adj.get(i)) {
                    if (j == removeIdx) continue;
                    if (mapping[i] >= 0 && mapping[j] >= 0) {
                        rAdj.get(mapping[i]).add(mapping[j]);
                    }
                }
            }

            SimResult sr = simulate(rTheta, rOmega, rAdj, couplingStrength,
                    shortSteps, stepSize, rn);
            double improvement = sr.finalR - baseR;
            if (improvement > 0.01) {
                result.add(new DesyncNode(nodes.get(removeIdx), improvement,
                        String.format("Removing %s improves order parameter by %.3f (ω=%.3f)",
                                nodes.get(removeIdx), improvement, omega[removeIdx])));
            }
        }
        result.sort((a, b) -> Double.compare(b.impactScore, a.impactScore));
        return result;
    }

    // ====================================================================
    // Engine 7: Insight Generator
    // ====================================================================

    private List<String> generateInsights(double finalR, double criticalCoupling,
                                           double kMax, List<SyncBarrier> barriers,
                                           List<DesyncNode> desync, int n, int edgeCount,
                                           List<List<Integer>> adj, boolean foundKc) {
        List<String> insights = new ArrayList<>();

        // Sync level
        if (finalR >= 0.9) {
            insights.add("The network achieves near-perfect synchronization (r=" +
                    String.format("%.3f", finalR) + ") at coupling K=" +
                    String.format("%.2f", couplingStrength) + ".");
        } else if (finalR >= 0.6) {
            insights.add("The network achieves partial synchronization (r=" +
                    String.format("%.3f", finalR) + "). Stronger coupling or structural changes may improve coherence.");
        } else {
            insights.add("The network remains largely incoherent (r=" +
                    String.format("%.3f", finalR) + "). Significant barriers prevent synchronization.");
        }

        // Critical coupling
        if (foundKc && criticalCoupling < kMax) {
            insights.add(String.format("Critical coupling K_c ≈ %.2f — the network transitions to sync at this threshold.",
                    criticalCoupling));
            if (criticalCoupling < kMax * 0.3) {
                insights.add("Low critical coupling indicates the network topology is naturally conducive to synchronization.");
            } else if (criticalCoupling > kMax * 0.7) {
                insights.add("High critical coupling suggests structural resistance to synchronization.");
            }
        } else {
            insights.add("The network does not reach synchronization (r≥0.7) within the tested coupling range.");
        }

        // Density insight
        double maxEdges = n * (n - 1.0) / 2.0;
        double density = maxEdges > 0 ? edgeCount / maxEdges : 0;
        if (density > 0.5) {
            insights.add(String.format("Dense network (%.0f%% of edges present) favors synchronization.", density * 100));
        } else if (density < 0.1 && n > 3) {
            insights.add(String.format("Sparse network (%.0f%% of edges present) may struggle to synchronize.", density * 100));
        }

        // Barriers
        long freqOutliers = barriers.stream()
                .filter(b -> b.type == BarrierType.FREQUENCY_OUTLIER).count();
        long bottlenecks = barriers.stream()
                .filter(b -> b.type == BarrierType.BOTTLENECK_EDGE).count();
        long clusters = barriers.stream()
                .filter(b -> b.type == BarrierType.ISOLATED_CLUSTER).count();

        if (freqOutliers > 0) {
            insights.add(freqOutliers + " frequency outlier(s) detected — these nodes resist entrainment.");
        }
        if (bottlenecks > 0) {
            insights.add(bottlenecks + " bridge edge(s) create synchronization bottlenecks — adding parallel paths would help.");
        }
        if (clusters > 0) {
            insights.add(clusters + " isolated cluster(s) cannot synchronize with the main network.");
        }

        // Desynchronizers
        if (!desync.isEmpty()) {
            insights.add("Top desynchronizer: " + desync.get(0).nodeId +
                    " (removing it improves r by " + String.format("%.3f", desync.get(0).impactScore) + ").");
        }

        // Connectivity
        List<List<Integer>> comps = findComponents(adj, n);
        if (comps.size() > 1) {
            insights.add("Network has " + comps.size() + " disconnected components — full synchronization is impossible without connecting them.");
        }

        return insights;
    }

    // ====================================================================
    // Health Score
    // ====================================================================

    private double computeHealthScore(double finalR, double criticalCoupling,
                                       double kMax, List<SyncBarrier> barriers,
                                       int n, int edgeCount,
                                       List<List<Integer>> adj) {
        // 40% final order parameter
        double rScore = finalR * 40.0;

        // 25% ease of synchronization (low Kc is good)
        double kcNormalized = kMax > 0 ? Math.max(0, 1.0 - criticalCoupling / kMax) : 0.5;
        double kcScore = kcNormalized * 25.0;

        // 20% absence of severe barriers
        long severeBarriers = barriers.stream().filter(b -> b.impact >= 0.7).count();
        double barrierScore = Math.max(0, 20.0 - severeBarriers * 4.0);

        // 15% network connectivity
        List<List<Integer>> comps = findComponents(adj, n);
        double connScore;
        if (comps.size() == 1) {
            connScore = 15.0;
        } else {
            int largest = comps.stream().mapToInt(List::size).max().orElse(0);
            connScore = n > 0 ? 15.0 * largest / n : 0;
        }

        return Math.max(0, Math.min(100, rScore + kcScore + barrierScore + connScore));
    }

    // ====================================================================
    // Text output
    // ====================================================================

    public String toText(SynchronizationReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("  GRAPH SYNCHRONIZATION ENGINE — REPORT\n");
        sb.append("═══════════════════════════════════════════════════════════════\n\n");

        sb.append(String.format("  Nodes: %d | Edges: %d\n", report.nodeCount, report.edgeCount));
        sb.append(String.format("  Final Order Parameter: %.4f\n", report.finalOrderParameter));
        sb.append(String.format("  Critical Coupling (K_c): %.3f\n", report.criticalCoupling));
        sb.append(String.format("  Health Score: %.1f / 100 [%s]\n\n",
                report.healthScore, healthTier(report.healthScore)));

        // Coupling curve
        sb.append("  ── Coupling Curve (K → r) ─────────────────────────────────\n");
        for (Map.Entry<Double, Double> entry : report.couplingCurve.entrySet()) {
            sb.append(String.format("    K=%.2f  r=%.4f  %s\n",
                    entry.getKey(), entry.getValue(),
                    barString(entry.getValue())));
        }
        sb.append("\n");

        // Barriers
        if (!report.barriers.isEmpty()) {
            sb.append("  ── Synchronization Barriers ───────────────────────────────\n");
            for (SyncBarrier b : report.barriers) {
                sb.append(String.format("    [%s] impact=%.2f — %s\n",
                        b.type, b.impact, b.description));
            }
            sb.append("\n");
        }

        // Desynchronizers
        if (!report.desynchronizers.isEmpty()) {
            sb.append("  ── Top Desynchronizers ────────────────────────────────────\n");
            for (DesyncNode d : report.desynchronizers) {
                sb.append(String.format("    %s: Δr=+%.3f — %s\n",
                        d.nodeId, d.impactScore, d.reason));
            }
            sb.append("\n");
        }

        // Insights
        sb.append("  ── Insights ──────────────────────────────────────────────\n");
        for (String insight : report.insights) {
            sb.append("    • ").append(insight).append("\n");
        }

        sb.append("\n═══════════════════════════════════════════════════════════════\n");
        return sb.toString();
    }

    private static String healthTier(double score) {
        if (score >= 80) return "Perfectly Synced";
        if (score >= 60) return "Well Synced";
        if (score >= 40) return "Partially Synced";
        if (score >= 20) return "Poorly Synced";
        return "Incoherent";
    }

    private static String barString(double r) {
        int bars = (int) (r * 20);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bars; i++) sb.append("█");
        for (int i = bars; i < 20; i++) sb.append("░");
        return sb.toString();
    }

    // ====================================================================
    // HTML Dashboard Export
    // ====================================================================

    public String exportHtml(SynchronizationReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
        sb.append("<title>Graph Synchronization Engine — Dashboard</title>");
        sb.append("<style>");
        sb.append("*{margin:0;padding:0;box-sizing:border-box}");
        sb.append("body{font-family:'Segoe UI',system-ui,sans-serif;background:#0d1117;color:#c9d1d9;padding:24px}");
        sb.append("h1{color:#58a6ff;margin-bottom:8px}");
        sb.append(".subtitle{color:#8b949e;margin-bottom:24px}");
        sb.append(".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(340px,1fr));gap:16px;margin-bottom:24px}");
        sb.append(".card{background:#161b22;border:1px solid #30363d;border-radius:8px;padding:16px}");
        sb.append(".card h2{color:#58a6ff;font-size:14px;margin-bottom:12px;text-transform:uppercase;letter-spacing:1px}");
        sb.append(".gauge{width:120px;height:120px;border-radius:50%;display:flex;align-items:center;justify-content:center;margin:0 auto 12px}");
        sb.append(".gauge-value{font-size:28px;font-weight:700}");
        sb.append(".tier{text-align:center;font-size:13px;color:#8b949e}");
        sb.append("canvas{width:100%;height:200px;display:block;margin-top:8px}");
        sb.append("table{width:100%;border-collapse:collapse;font-size:13px}");
        sb.append("th{text-align:left;padding:6px 8px;border-bottom:1px solid #30363d;color:#58a6ff}");
        sb.append("td{padding:6px 8px;border-bottom:1px solid #21262d}");
        sb.append("tr:nth-child(even){background:#1c2128}");
        sb.append(".insight{background:#1c2128;border-left:3px solid #58a6ff;padding:8px 12px;margin-bottom:8px;border-radius:0 4px 4px 0;font-size:13px}");
        sb.append(".metric{display:inline-block;background:#21262d;padding:4px 10px;border-radius:12px;font-size:12px;margin:2px}");
        sb.append("</style></head><body>");

        sb.append("<h1>🔄 Graph Synchronization Engine</h1>");
        sb.append("<div class=\"subtitle\">Kuramoto oscillator analysis — ");
        sb.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        sb.append("</div>");

        // Metrics row
        sb.append("<div class=\"grid\">");

        // Health score gauge
        String gaugeColor = report.healthScore >= 80 ? "#3fb950" :
                report.healthScore >= 60 ? "#58a6ff" :
                report.healthScore >= 40 ? "#d29922" : "#f85149";
        sb.append("<div class=\"card\" style=\"text-align:center\">");
        sb.append("<h2>Health Score</h2>");
        sb.append("<div class=\"gauge\" style=\"border:6px solid ").append(gaugeColor).append("\">");
        sb.append("<span class=\"gauge-value\" style=\"color:").append(gaugeColor).append("\">");
        sb.append(String.format("%.0f", report.healthScore)).append("</span></div>");
        sb.append("<div class=\"tier\">").append(healthTier(report.healthScore)).append("</div>");
        sb.append("<div style=\"margin-top:12px\">");
        sb.append("<span class=\"metric\">Nodes: ").append(report.nodeCount).append("</span>");
        sb.append("<span class=\"metric\">Edges: ").append(report.edgeCount).append("</span>");
        sb.append("<span class=\"metric\">r = ").append(String.format("%.4f", report.finalOrderParameter)).append("</span>");
        sb.append("<span class=\"metric\">K_c ≈ ").append(String.format("%.2f", report.criticalCoupling)).append("</span>");
        sb.append("</div></div>");

        // Order parameter trajectory chart
        sb.append("<div class=\"card\"><h2>Order Parameter Trajectory</h2>");
        sb.append("<canvas id=\"trajChart\"></canvas></div>");

        // Coupling curve chart
        sb.append("<div class=\"card\"><h2>Coupling Curve (K → r)</h2>");
        sb.append("<canvas id=\"couplingChart\"></canvas></div>");

        sb.append("</div>"); // end grid

        // Barriers table
        if (!report.barriers.isEmpty()) {
            sb.append("<div class=\"card\" style=\"margin-bottom:16px\"><h2>Synchronization Barriers</h2><table>");
            sb.append("<tr><th>Type</th><th>Impact</th><th>Description</th><th>Nodes</th></tr>");
            for (SyncBarrier b : report.barriers) {
                sb.append("<tr><td>").append(esc(b.type.name())).append("</td>");
                sb.append("<td>").append(String.format("%.2f", b.impact)).append("</td>");
                sb.append("<td>").append(esc(b.description)).append("</td>");
                sb.append("<td>").append(esc(String.join(", ", b.involvedNodes))).append("</td></tr>");
            }
            sb.append("</table></div>");
        }

        // Desynchronizers table
        if (!report.desynchronizers.isEmpty()) {
            sb.append("<div class=\"card\" style=\"margin-bottom:16px\"><h2>Top Desynchronizers</h2><table>");
            sb.append("<tr><th>Node</th><th>Impact (Δr)</th><th>Reason</th></tr>");
            for (DesyncNode d : report.desynchronizers) {
                sb.append("<tr><td>").append(esc(d.nodeId)).append("</td>");
                sb.append("<td>+").append(String.format("%.3f", d.impactScore)).append("</td>");
                sb.append("<td>").append(esc(d.reason)).append("</td></tr>");
            }
            sb.append("</table></div>");
        }

        // Insights
        sb.append("<div class=\"card\" style=\"margin-bottom:16px\"><h2>Autonomous Insights</h2>");
        for (String insight : report.insights) {
            sb.append("<div class=\"insight\">").append(esc(insight)).append("</div>");
        }
        sb.append("</div>");

        // Chart scripts
        sb.append("<script>");
        // Order parameter trajectory
        sb.append("(function(){");
        sb.append("var data=[");
        for (int i = 0; i < report.orderParameterTrajectory.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format("%.4f", report.orderParameterTrajectory.get(i)));
        }
        sb.append("];");
        sb.append("var c=document.getElementById('trajChart');");
        sb.append("c.width=c.parentElement.clientWidth-32;c.height=200;");
        sb.append("var ctx=c.getContext('2d');");
        sb.append("var w=c.width,h=c.height,n=data.length,px=30,py=20;");
        sb.append("ctx.fillStyle='#161b22';ctx.fillRect(0,0,w,h);");
        sb.append("ctx.strokeStyle='#30363d';ctx.lineWidth=0.5;");
        sb.append("for(var i=0;i<=4;i++){var y=py+(h-2*py)*(1-i/4);ctx.beginPath();ctx.moveTo(px,y);ctx.lineTo(w-10,y);ctx.stroke();");
        sb.append("ctx.fillStyle='#8b949e';ctx.font='10px sans-serif';ctx.fillText((i/4).toFixed(1),2,y+3);}");
        sb.append("ctx.beginPath();ctx.strokeStyle='#58a6ff';ctx.lineWidth=1.5;");
        sb.append("for(var i=0;i<n;i++){var x=px+(w-px-10)*i/(n-1||1),y=py+(h-2*py)*(1-data[i]);");
        sb.append("if(i===0)ctx.moveTo(x,y);else ctx.lineTo(x,y);}ctx.stroke();");
        sb.append("})();");

        // Coupling curve
        sb.append("(function(){");
        sb.append("var ks=[],rs=[];");
        for (Map.Entry<Double, Double> entry : report.couplingCurve.entrySet()) {
            sb.append("ks.push(").append(String.format("%.2f", entry.getKey())).append(");");
            sb.append("rs.push(").append(String.format("%.4f", entry.getValue())).append(");");
        }
        sb.append("var c=document.getElementById('couplingChart');");
        sb.append("c.width=c.parentElement.clientWidth-32;c.height=200;");
        sb.append("var ctx=c.getContext('2d');");
        sb.append("var w=c.width,h=c.height,n=ks.length,px=30,py=20;");
        sb.append("var kMax=ks[n-1]||1;");
        sb.append("ctx.fillStyle='#161b22';ctx.fillRect(0,0,w,h);");
        sb.append("ctx.strokeStyle='#30363d';ctx.lineWidth=0.5;");
        sb.append("for(var i=0;i<=4;i++){var y=py+(h-2*py)*(1-i/4);ctx.beginPath();ctx.moveTo(px,y);ctx.lineTo(w-10,y);ctx.stroke();");
        sb.append("ctx.fillStyle='#8b949e';ctx.font='10px sans-serif';ctx.fillText((i/4).toFixed(1),2,y+3);}");
        sb.append("ctx.beginPath();ctx.strokeStyle='#3fb950';ctx.lineWidth=2;");
        sb.append("for(var i=0;i<n;i++){var x=px+(w-px-10)*ks[i]/kMax,y=py+(h-2*py)*(1-rs[i]);");
        sb.append("if(i===0)ctx.moveTo(x,y);else ctx.lineTo(x,y);}ctx.stroke();");
        // K_c line
        sb.append("var kc=").append(String.format("%.2f", report.criticalCoupling)).append(";");
        sb.append("if(kc<kMax){var xc=px+(w-px-10)*kc/kMax;");
        sb.append("ctx.strokeStyle='#f85149';ctx.lineWidth=1;ctx.setLineDash([4,4]);");
        sb.append("ctx.beginPath();ctx.moveTo(xc,py);ctx.lineTo(xc,h-py);ctx.stroke();");
        sb.append("ctx.setLineDash([]);ctx.fillStyle='#f85149';ctx.font='10px sans-serif';");
        sb.append("ctx.fillText('K_c='+kc.toFixed(2),xc+3,py+10);}");
        sb.append("})();");
        sb.append("</script>");

        sb.append("</body></html>");
        return sb.toString();
    }

    /** Writes HTML report to file. */
    public void exportHtml(SynchronizationReport report, String path) throws IOException {
        try (BufferedWriter w = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8))) {
            w.write(exportHtml(report));
        }
    }

    // ====================================================================
    // Helpers
    // ====================================================================

    private static double roundK(double k) {
        return Math.round(k * 100.0) / 100.0;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private SynchronizationReport emptyReport(int nodes, int edges) {
        return new SynchronizationReport(
                Collections.emptyMap(), Collections.emptyMap(),
                Collections.singletonList(0.0), 0.0, 0.0,
                Collections.singletonMap(0.0, 0.0),
                Collections.emptyList(), Collections.emptyList(),
                0.0, Collections.singletonList("Empty graph — no oscillators to synchronize."),
                nodes, edges);
    }
}
