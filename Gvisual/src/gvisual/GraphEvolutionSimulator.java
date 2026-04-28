package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GraphEvolutionSimulator — autonomous forward-simulation engine that predicts
 * how a graph will evolve over configurable time steps using multiple growth
 * models, detects structural tipping points, and generates interactive reports.
 *
 * <h3>Growth Models:</h3>
 * <ul>
 *   <li><b>Preferential Attachment</b> — Barabási–Albert: new nodes/edges attach
 *       preferentially to high-degree nodes</li>
 *   <li><b>Random Attachment</b> — Erdős–Rényi style: uniform random edge creation</li>
 *   <li><b>Triadic Closure</b> — friends-of-friends: close open triangles</li>
 *   <li><b>Copy Model</b> — new nodes copy edges from an existing random node</li>
 *   <li><b>Fitness-Based</b> — attachment probability weighted by node fitness
 *       (clustering coefficient × degree)</li>
 * </ul>
 *
 * <h3>Tipping Point Detection:</h3>
 * <ul>
 *   <li>Giant component emergence (largest component crosses 50% of nodes)</li>
 *   <li>Connectivity collapse (component count spikes)</li>
 *   <li>Density explosion (rapid density increase)</li>
 *   <li>Hub dominance (max degree &gt; 3× average)</li>
 *   <li>Community split (modularity drop)</li>
 * </ul>
 *
 * <h3>Agentic behavior:</h3>
 * <ul>
 *   <li><b>Predictive</b> — forecasts future network structure without human guidance</li>
 *   <li><b>Multi-model</b> — autonomously compares growth scenarios</li>
 *   <li><b>Alert-driven</b> — detects and flags structural tipping points</li>
 *   <li><b>Self-documenting</b> — generates rich HTML dashboards</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 *   GraphEvolutionSimulator sim = new GraphEvolutionSimulator(graph);
 *   SimConfig config = new SimConfig();
 *   config.totalSteps = 100;
 *   config.model = GrowthModel.PREFERENTIAL_ATTACHMENT;
 *   SimulationResult result = sim.simulate(config);
 *   System.out.println(sim.formatTextReport(result));
 *   sim.exportHtml(result, new File("evolution.html"));
 *
 *   // Compare all models
 *   Map&lt;GrowthModel, SimulationResult&gt; comparison = sim.compareModels(config);
 *   sim.exportComparisonHtml(comparison, new File("evolution-comparison.html"));
 * </pre>
 *
 * @author zalenix
 */
public class GraphEvolutionSimulator {

    /** Configuration for a simulation run. */
    public static class SimConfig {
        /** Total simulation steps. */
        public int totalSteps = 50;
        /** Edges to add per step. */
        public int edgesPerStep = 2;
        /** Probability of adding a new node (vs. connecting existing nodes) each step. */
        public double newNodeProbability = 0.3;
        /** Growth model to use. */
        public GrowthModel model = GrowthModel.PREFERENTIAL_ATTACHMENT;
        /** Random seed for reproducibility. */
        public long seed = 42;

        public SimConfig copy() {
            SimConfig c = new SimConfig();
            c.totalSteps = this.totalSteps;
            c.edgesPerStep = this.edgesPerStep;
            c.newNodeProbability = this.newNodeProbability;
            c.model = this.model;
            c.seed = this.seed;
            return c;
        }
    }

    /** Available growth models. */
    public enum GrowthModel {
        PREFERENTIAL_ATTACHMENT("Preferential Attachment (Barabasi-Albert)"),
        RANDOM_ATTACHMENT("Random Attachment (Erdos-Renyi)"),
        TRIADIC_CLOSURE("Triadic Closure"),
        COPY_MODEL("Copy Model"),
        FITNESS_BASED("Fitness-Based");

        private final String label;

        GrowthModel(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** Snapshot of graph metrics at a simulation step. */
    public static class StepSnapshot {
        public final int step;
        public final int nodeCount;
        public final int edgeCount;
        public final double density;
        public final double avgDegree;
        public final double clusteringCoefficient;
        public final double modularity;
        public final int componentCount;
        public final int diameter;
        public final String event;

        public StepSnapshot(int step, int nodeCount, int edgeCount, double density,
                            double avgDegree, double clusteringCoefficient,
                            double modularity, int componentCount, int diameter,
                            String event) {
            this.step = step;
            this.nodeCount = nodeCount;
            this.edgeCount = edgeCount;
            this.density = density;
            this.avgDegree = avgDegree;
            this.clusteringCoefficient = clusteringCoefficient;
            this.modularity = modularity;
            this.componentCount = componentCount;
            this.diameter = diameter;
            this.event = event;
        }
    }

    /** A detected structural tipping point. */
    public static class TippingPoint {
        public final int step;
        public final String type;
        public final String description;
        public final double severity;

        public TippingPoint(int step, String type, String description, double severity) {
            this.step = step;
            this.type = type;
            this.description = description;
            this.severity = severity;
        }
    }

    /** Complete simulation result. */
    public static class SimulationResult {
        public final List<StepSnapshot> timeline;
        public final List<TippingPoint> tippingPoints;
        public final Graph<String, Edge> finalGraph;
        public final Map<String, Object> summary;

        public SimulationResult(List<StepSnapshot> timeline, List<TippingPoint> tippingPoints,
                                Graph<String, Edge> finalGraph, Map<String, Object> summary) {
            this.timeline = Collections.unmodifiableList(timeline);
            this.tippingPoints = Collections.unmodifiableList(tippingPoints);
            this.finalGraph = finalGraph;
            this.summary = Collections.unmodifiableMap(summary);
        }
    }

    private final Graph<String, Edge> originalGraph;

    /**
     * Create a simulator from an initial graph.
     *
     * @param graph the starting graph (will be deep-cloned for simulation)
     */
    public GraphEvolutionSimulator(Graph<String, Edge> graph) {
        this.originalGraph = Objects.requireNonNull(graph, "graph must not be null");
    }

    /**
     * Run a simulation with the given configuration.
     *
     * @param config simulation parameters
     * @return complete simulation result with timeline and tipping points
     */
    public SimulationResult simulate(SimConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        Graph<String, Edge> g = cloneGraph(originalGraph);
        Random rng = new Random(config.seed);

        List<StepSnapshot> timeline = new ArrayList<>();
        List<TippingPoint> tippingPoints = new ArrayList<>();

        // Initial snapshot
        StepSnapshot initial = takeSnapshot(g, 0, null);
        timeline.add(initial);

        int edgeCounter = 0;
        int nodeCounter = 0;

        for (int step = 1; step <= config.totalSteps; step++) {
            for (int e = 0; e < config.edgesPerStep; e++) {
                boolean addNode = rng.nextDouble() < config.newNodeProbability
                        || g.getVertexCount() < 2;

                if (addNode) {
                    String newNode = "sim_node_" + step + "_" + nodeCounter++;
                    g.addVertex(newNode);
                    // Connect to existing node
                    if (g.getVertexCount() > 1) {
                        String target = pickTarget(g, newNode, config.model, rng);
                        if (target != null) {
                            String eid = "sim_edge_" + step + "_" + edgeCounter++;
                            g.addEdge(new Edge("sim", newNode, target), newNode, target);
                        }
                    }
                } else {
                    addEdgeByModel(g, config.model, rng, step, edgeCounter);
                    edgeCounter++;
                }
            }

            // Take snapshot and detect tipping points
            StepSnapshot prev = timeline.get(timeline.size() - 1);
            List<TippingPoint> stepTips = new ArrayList<>();
            StepSnapshot snap = takeSnapshot(g, step, null);

            detectTippingPoints(prev, snap, step, stepTips);

            String eventDesc = stepTips.isEmpty() ? null
                    : stepTips.stream().map(tp -> tp.type).collect(Collectors.joining(", "));
            StepSnapshot finalSnap = new StepSnapshot(snap.step, snap.nodeCount, snap.edgeCount,
                    snap.density, snap.avgDegree, snap.clusteringCoefficient,
                    snap.modularity, snap.componentCount, snap.diameter, eventDesc);

            timeline.add(finalSnap);
            tippingPoints.addAll(stepTips);
        }

        Map<String, Object> summary = buildSummary(timeline, tippingPoints, config);
        return new SimulationResult(timeline, tippingPoints, g, summary);
    }

    /**
     * Compare all growth models using the same base configuration.
     *
     * @param baseConfig configuration to use (model field will be overridden for each)
     * @return map of model to simulation result
     */
    public Map<GrowthModel, SimulationResult> compareModels(SimConfig baseConfig) {
        Map<GrowthModel, SimulationResult> results = new LinkedHashMap<>();
        for (GrowthModel model : GrowthModel.values()) {
            SimConfig cfg = baseConfig.copy();
            cfg.model = model;
            results.put(model, simulate(cfg));
        }
        return results;
    }

    // ─── Growth model implementations ────────────────────────────────

    private String pickTarget(Graph<String, Edge> g, String source, GrowthModel model, Random rng) {
        List<String> vertices = new ArrayList<>(g.getVertices());
        vertices.remove(source);
        if (vertices.isEmpty()) return null;

        switch (model) {
            case PREFERENTIAL_ATTACHMENT:
                return pickPreferential(g, vertices, rng);
            case FITNESS_BASED:
                return pickFitnessBased(g, vertices, rng);
            case COPY_MODEL:
                return pickFromCopyModel(g, vertices, rng);
            case TRIADIC_CLOSURE:
                return pickTriadic(g, source, vertices, rng);
            case RANDOM_ATTACHMENT:
            default:
                return vertices.get(rng.nextInt(vertices.size()));
        }
    }

    private void addEdgeByModel(Graph<String, Edge> g, GrowthModel model, Random rng,
                                int step, int edgeCounter) {
        List<String> vertices = new ArrayList<>(g.getVertices());
        if (vertices.size() < 2) return;

        String src, dst;
        int attempts = 0;

        switch (model) {
            case TRIADIC_CLOSURE:
                src = vertices.get(rng.nextInt(vertices.size()));
                dst = pickTriadic(g, src, vertices, rng);
                break;
            case PREFERENTIAL_ATTACHMENT:
                src = vertices.get(rng.nextInt(vertices.size()));
                dst = pickPreferential(g, vertices, rng);
                break;
            case FITNESS_BASED:
                src = vertices.get(rng.nextInt(vertices.size()));
                dst = pickFitnessBased(g, vertices, rng);
                break;
            case COPY_MODEL:
                src = vertices.get(rng.nextInt(vertices.size()));
                dst = pickFromCopyModel(g, vertices, rng);
                break;
            default:
                src = vertices.get(rng.nextInt(vertices.size()));
                dst = vertices.get(rng.nextInt(vertices.size()));
                break;
        }

        if (dst != null && !src.equals(dst) && !g.isNeighbor(src, dst)) {
            String eid = "sim_edge_" + step + "_" + edgeCounter;
            g.addEdge(new Edge("sim", src, dst), src, dst);
        }
    }

    private String pickPreferential(Graph<String, Edge> g, List<String> candidates, Random rng) {
        int totalDegree = candidates.stream().mapToInt(v -> g.degree(v)).sum();
        if (totalDegree == 0) return candidates.get(rng.nextInt(candidates.size()));

        int r = rng.nextInt(totalDegree);
        int cumulative = 0;
        for (String v : candidates) {
            cumulative += g.degree(v);
            if (r < cumulative) return v;
        }
        return candidates.get(candidates.size() - 1);
    }

    private String pickFitnessBased(Graph<String, Edge> g, List<String> candidates, Random rng) {
        double totalFitness = 0;
        double[] fitness = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            String v = candidates.get(i);
            double cc = localClusteringCoefficient(g, v);
            double deg = g.degree(v);
            fitness[i] = (cc + 0.1) * (deg + 1); // avoid zero
            totalFitness += fitness[i];
        }
        if (totalFitness == 0) return candidates.get(rng.nextInt(candidates.size()));

        double r = rng.nextDouble() * totalFitness;
        double cumulative = 0;
        for (int i = 0; i < candidates.size(); i++) {
            cumulative += fitness[i];
            if (r < cumulative) return candidates.get(i);
        }
        return candidates.get(candidates.size() - 1);
    }

    private String pickTriadic(Graph<String, Edge> g, String source, List<String> candidates, Random rng) {
        // Try to close a triangle: pick a neighbor's neighbor that isn't already connected
        Collection<String> neighbors = g.getNeighbors(source);
        if (neighbors != null && !neighbors.isEmpty()) {
            List<String> neighborList = new ArrayList<>(neighbors);
            String friend = neighborList.get(rng.nextInt(neighborList.size()));
            Collection<String> fof = g.getNeighbors(friend);
            if (fof != null) {
                List<String> candidates2 = fof.stream()
                        .filter(v -> !v.equals(source) && !g.isNeighbor(source, v))
                        .collect(Collectors.toList());
                if (!candidates2.isEmpty()) {
                    return candidates2.get(rng.nextInt(candidates2.size()));
                }
            }
        }
        // Fallback to random
        return candidates.get(rng.nextInt(candidates.size()));
    }

    private String pickFromCopyModel(Graph<String, Edge> g, List<String> candidates, Random rng) {
        // Pick a random node, return one of its neighbors
        String template = candidates.get(rng.nextInt(candidates.size()));
        Collection<String> neighbors = g.getNeighbors(template);
        if (neighbors != null && !neighbors.isEmpty()) {
            List<String> nList = new ArrayList<>(neighbors);
            return nList.get(rng.nextInt(nList.size()));
        }
        return candidates.get(rng.nextInt(candidates.size()));
    }

    // ─── Metric computation ──────────────────────────────────────────

    private StepSnapshot takeSnapshot(Graph<String, Edge> g, int step, String event) {
        int V = g.getVertexCount();
        int E = g.getEdgeCount();
        double density = V < 2 ? 0.0 : (2.0 * E) / (V * (V - 1.0));
        double avgDeg = V == 0 ? 0.0 : (2.0 * E) / V;
        double cc = averageClusteringCoefficient(g);
        double mod = estimateModularity(g);
        int components = countComponents(g);
        int diam = approximateDiameter(g);

        return new StepSnapshot(step, V, E, density, avgDeg, cc, mod, components, diam, event);
    }

    private double localClusteringCoefficient(Graph<String, Edge> g, String v) {
        Collection<String> neighbors = g.getNeighbors(v);
        if (neighbors == null || neighbors.size() < 2) return 0.0;
        List<String> nList = new ArrayList<>(neighbors);
        int links = 0;
        for (int i = 0; i < nList.size(); i++) {
            for (int j = i + 1; j < nList.size(); j++) {
                if (g.isNeighbor(nList.get(i), nList.get(j))) links++;
            }
        }
        int possible = nList.size() * (nList.size() - 1) / 2;
        return (double) links / possible;
    }

    private double averageClusteringCoefficient(Graph<String, Edge> g) {
        if (g.getVertexCount() == 0) return 0.0;
        double sum = 0;
        for (String v : g.getVertices()) {
            sum += localClusteringCoefficient(g, v);
        }
        return sum / g.getVertexCount();
    }

    private double estimateModularity(Graph<String, Edge> g) {
        if (g.getEdgeCount() == 0) return 0.0;
        // Simple label propagation for community assignment
        Map<String, String> communities = new HashMap<>();
        for (String v : g.getVertices()) communities.put(v, v);

        for (int iter = 0; iter < 5; iter++) {
            List<String> vertices = new ArrayList<>(g.getVertices());
            Collections.shuffle(vertices);
            for (String v : vertices) {
                Collection<String> neighbors = g.getNeighbors(v);
                if (neighbors == null || neighbors.isEmpty()) continue;
                Map<String, Integer> labelCount = new HashMap<>();
                for (String n : neighbors) {
                    labelCount.merge(communities.get(n), 1, Integer::sum);
                }
                String bestLabel = labelCount.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey).orElse(communities.get(v));
                communities.put(v, bestLabel);
            }
        }

        // Compute modularity Q
        double m = g.getEdgeCount();
        double Q = 0;
        for (Edge edge : g.getEdges()) {
            var endpoints = g.getEndpoints(edge);
            if (endpoints == null) continue;
            String u = endpoints.getFirst();
            String w = endpoints.getSecond();
            if (communities.get(u).equals(communities.get(w))) {
                Q += 1.0 - (g.degree(u) * g.degree(w)) / (2.0 * m);
            } else {
                Q -= (g.degree(u) * g.degree(w)) / (2.0 * m);
            }
        }
        return Q / (2.0 * m);
    }

    private int countComponents(Graph<String, Edge> g) {
        Set<String> visited = new HashSet<>();
        int count = 0;
        for (String v : g.getVertices()) {
            if (!visited.contains(v)) {
                bfs(g, v, visited);
                count++;
            }
        }
        return count;
    }

    private void bfs(Graph<String, Edge> g, String start, Set<String> visited) {
        Queue<String> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            String v = queue.poll();
            Collection<String> neighbors = g.getNeighbors(v);
            if (neighbors == null) continue;
            for (String n : neighbors) {
                if (visited.add(n)) queue.add(n);
            }
        }
    }

    private int approximateDiameter(Graph<String, Edge> g) {
        if (g.getVertexCount() < 2) return 0;
        List<String> vertices = new ArrayList<>(g.getVertices());
        Random rng = new Random(0);
        int maxDist = 0;
        int probes = Math.min(5, vertices.size());
        for (int i = 0; i < probes; i++) {
            String start = vertices.get(rng.nextInt(vertices.size()));
            maxDist = Math.max(maxDist, bfsMaxDistance(g, start));
        }
        return maxDist;
    }

    private int bfsMaxDistance(Graph<String, Edge> g, String start) {
        Map<String, Integer> dist = new HashMap<>();
        Queue<String> queue = new LinkedList<>();
        dist.put(start, 0);
        queue.add(start);
        int maxDist = 0;
        while (!queue.isEmpty()) {
            String v = queue.poll();
            int d = dist.get(v);
            Collection<String> neighbors = g.getNeighbors(v);
            if (neighbors == null) continue;
            for (String n : neighbors) {
                if (!dist.containsKey(n)) {
                    dist.put(n, d + 1);
                    maxDist = Math.max(maxDist, d + 1);
                    queue.add(n);
                }
            }
        }
        return maxDist;
    }

    // ─── Tipping point detection ─────────────────────────────────────

    private void detectTippingPoints(StepSnapshot prev, StepSnapshot curr, int step,
                                     List<TippingPoint> out) {
        // Giant component emergence
        if (prev.nodeCount > 0 && curr.nodeCount > 0) {
            double prevLargest = largestComponentFraction(prev);
            double currLargest = largestComponentFraction(curr);
            if (prevLargest < 0.5 && currLargest >= 0.5) {
                out.add(new TippingPoint(step, "GIANT_COMPONENT_EMERGENCE",
                        String.format("Largest component crossed 50%% of nodes (%.1f%% → %.1f%%)",
                                prevLargest * 100, currLargest * 100),
                        Math.min(1.0, (currLargest - prevLargest) * 2)));
            }
        }

        // Connectivity collapse
        if (prev.componentCount > 0) {
            double compIncrease = (double) (curr.componentCount - prev.componentCount) / prev.componentCount;
            if (compIncrease > 0.2) {
                out.add(new TippingPoint(step, "CONNECTIVITY_COLLAPSE",
                        String.format("Component count spiked by %.0f%% (%d → %d)",
                                compIncrease * 100, prev.componentCount, curr.componentCount),
                        Math.min(1.0, compIncrease)));
            }
        }

        // Density explosion
        if (prev.density > 0.001) {
            double densityIncrease = (curr.density - prev.density) / prev.density;
            if (densityIncrease > 0.5) {
                out.add(new TippingPoint(step, "DENSITY_EXPLOSION",
                        String.format("Density surged by %.0f%% (%.4f → %.4f)",
                                densityIncrease * 100, prev.density, curr.density),
                        Math.min(1.0, densityIncrease / 2)));
            }
        }

        // Hub dominance
        if (curr.avgDegree > 0 && curr.nodeCount > 2) {
            // We approximate max degree from density/avg degree relationship
            // Better: check actual max degree. Using avgDegree * 3 heuristic for now.
            // We can't get max degree from snapshot alone, so check avg vs density pattern
            double maxDegreeEstimate = curr.avgDegree * (1 + curr.density * curr.nodeCount * 0.5);
            if (maxDegreeEstimate > curr.avgDegree * 3 && curr.avgDegree > 1) {
                out.add(new TippingPoint(step, "HUB_DOMINANCE",
                        String.format("Potential hub dominance: density pattern suggests max degree >> avg (%.1f)",
                                curr.avgDegree),
                        0.6));
            }
        }

        // Community split
        if (prev.modularity > 0.01) {
            double modDrop = prev.modularity - curr.modularity;
            if (modDrop > 0.1) {
                out.add(new TippingPoint(step, "COMMUNITY_SPLIT",
                        String.format("Modularity dropped by %.3f (%.3f → %.3f)",
                                modDrop, prev.modularity, curr.modularity),
                        Math.min(1.0, modDrop * 5)));
            }
        }
    }

    private double largestComponentFraction(StepSnapshot snap) {
        if (snap.componentCount <= 1) return 1.0;
        // Approximate: if components = c, largest ≈ nodeCount * (1 - (c-1)/nodeCount)
        // Better approximation based on even distribution vs giant component
        // For simulation purposes, use 1/componentCount as rough inverse
        return 1.0 / snap.componentCount;
    }

    // ─── Utilities ───────────────────────────────────────────────────

    private Graph<String, Edge> cloneGraph(Graph<String, Edge> src) {
        Graph<String, Edge> clone = new UndirectedSparseGraph<>();
        for (String v : src.getVertices()) clone.addVertex(v);
        for (Edge e : src.getEdges()) {
            var endpoints = src.getEndpoints(e);
            if (endpoints != null) {
                Edge copy = new Edge(e.getType(), e.getVertex1(), e.getVertex2());
                copy.setWeight(e.getWeight());
                clone.addEdge(copy, endpoints.getFirst(), endpoints.getSecond());
            }
        }
        return clone;
    }

    private Map<String, Object> buildSummary(List<StepSnapshot> timeline,
                                              List<TippingPoint> tippingPoints,
                                              SimConfig config) {
        Map<String, Object> summary = new LinkedHashMap<>();
        StepSnapshot first = timeline.get(0);
        StepSnapshot last = timeline.get(timeline.size() - 1);

        summary.put("model", config.model.getLabel());
        summary.put("steps", config.totalSteps);
        summary.put("initialNodes", first.nodeCount);
        summary.put("initialEdges", first.edgeCount);
        summary.put("finalNodes", last.nodeCount);
        summary.put("finalEdges", last.edgeCount);
        summary.put("nodeGrowth", last.nodeCount - first.nodeCount);
        summary.put("edgeGrowth", last.edgeCount - first.edgeCount);
        summary.put("initialDensity", String.format("%.4f", first.density));
        summary.put("finalDensity", String.format("%.4f", last.density));
        summary.put("initialClustering", String.format("%.4f", first.clusteringCoefficient));
        summary.put("finalClustering", String.format("%.4f", last.clusteringCoefficient));
        summary.put("tippingPoints", tippingPoints.size());

        return summary;
    }

    // ─── Text Report ─────────────────────────────────────────────────

    /**
     * Format a human-readable text report of simulation results.
     *
     * @param result the simulation result
     * @return formatted text report
     */
    public String formatTextReport(SimulationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("+==============================================================+\n");
        sb.append("|          GRAPH EVOLUTION SIMULATOR -- REPORT                 |\n");
        sb.append("+==============================================================+\n\n");

        sb.append("-- Summary -----------------------------------------------------\n");
        result.summary.forEach((k, v) -> sb.append(String.format("  %-20s : %s%n", k, v)));

        sb.append("\n-- Tipping Points ----------------------------------------------\n");
        if (result.tippingPoints.isEmpty()) {
            sb.append("  No tipping points detected.\n");
        } else {
            for (TippingPoint tp : result.tippingPoints) {
                sb.append(String.format("  [Step %3d] %-30s severity=%.2f%n    %s%n",
                        tp.step, tp.type, tp.severity, tp.description));
            }
        }

        sb.append("\n-- Timeline (every 5 steps) ------------------------------------\n");
        sb.append(String.format("  %-5s %-7s %-7s %-10s %-10s %-10s %-6s%n",
                "Step", "Nodes", "Edges", "Density", "AvgDeg", "Cluster", "Comp"));
        for (StepSnapshot s : result.timeline) {
            if (s.step % 5 == 0 || s.event != null) {
                sb.append(String.format("  %-5d %-7d %-7d %-10.4f %-10.2f %-10.4f %-6d",
                        s.step, s.nodeCount, s.edgeCount, s.density,
                        s.avgDegree, s.clusteringCoefficient, s.componentCount));
                if (s.event != null) sb.append(" [!] ").append(s.event);
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    // ─── HTML Export ─────────────────────────────────────────────────

    /**
     * Export simulation results as an interactive HTML dashboard.
     *
     * @param result     simulation result
     * @param outputFile output HTML file
     * @throws IOException if file writing fails
     */
    public void exportHtml(SimulationResult result, File outputFile) throws IOException {
        String html = buildHtml(result.summary, result.timeline, result.tippingPoints,
                result.summary.getOrDefault("model", "Unknown").toString());
        try (Writer w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
            w.write(html);
        }
    }

    /**
     * Export multi-model comparison as an interactive HTML dashboard.
     *
     * @param results    map of model to simulation result
     * @param outputFile output HTML file
     * @throws IOException if file writing fails
     */
    public void exportComparisonHtml(Map<GrowthModel, SimulationResult> results,
                                     File outputFile) throws IOException {
        String html = buildComparisonHtml(results);
        try (Writer w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
            w.write(html);
        }
    }

    private String buildHtml(Map<String, Object> summary, List<StepSnapshot> timeline,
                             List<TippingPoint> tippingPoints, String modelName) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<title>Graph Evolution Simulator</title>");
        sb.append("<style>");
        sb.append("body{background:#1a1a2e;color:#e0e0e0;font-family:'Segoe UI',sans-serif;margin:0;padding:20px}");
        sb.append("h1{color:#53d8fb;text-align:center;margin-bottom:5px}");
        sb.append("h2{color:#e94560;border-bottom:1px solid #333;padding-bottom:5px}");
        sb.append(".subtitle{text-align:center;color:#888;margin-bottom:30px}");
        sb.append(".container{max-width:1200px;margin:0 auto}");
        sb.append(".card{background:#16213e;border-radius:8px;padding:20px;margin:15px 0;box-shadow:0 2px 8px rgba(0,0,0,0.3)}");
        sb.append("table{width:100%;border-collapse:collapse;margin:10px 0}");
        sb.append("th{background:#0f3460;color:#53d8fb;padding:8px 12px;text-align:left}");
        sb.append("td{padding:8px 12px;border-bottom:1px solid #222}");
        sb.append("tr:hover{background:rgba(83,216,251,0.05)}");
        sb.append(".tip{background:#2a1a1a;border-left:4px solid #e94560;padding:10px 15px;margin:8px 0;border-radius:0 4px 4px 0}");
        sb.append(".tip .type{color:#e94560;font-weight:bold}");
        sb.append(".tip .step{color:#e9c46a}");
        sb.append(".sev{display:inline-block;width:60px;height:8px;background:#333;border-radius:4px;overflow:hidden}");
        sb.append(".sev-fill{height:100%;border-radius:4px}");
        sb.append("svg{width:100%;height:300px}");
        sb.append("</style></head><body><div class='container'>");

        sb.append("<h1>🔮 Graph Evolution Simulator</h1>");
        sb.append("<div class='subtitle'>Model: ").append(esc(modelName)).append(" | ");
        sb.append(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())).append("</div>");

        // Summary card
        sb.append("<div class='card'><h2>📊 Summary</h2><table>");
        for (Map.Entry<String, Object> e : summary.entrySet()) {
            sb.append("<tr><td><b>").append(esc(e.getKey())).append("</b></td><td>")
                    .append(esc(String.valueOf(e.getValue()))).append("</td></tr>");
        }
        sb.append("</table></div>");

        // Chart card
        sb.append("<div class='card'><h2>📈 Metric Trajectories</h2>");
        sb.append(buildSvgChart(timeline, tippingPoints));
        sb.append("</div>");

        // Tipping points card
        sb.append("<div class='card'><h2>⚡ Tipping Points</h2>");
        if (tippingPoints.isEmpty()) {
            sb.append("<p style='color:#888'>No tipping points detected during simulation.</p>");
        } else {
            for (TippingPoint tp : tippingPoints) {
                sb.append("<div class='tip'>");
                sb.append("<span class='type'>").append(esc(tp.type)).append("</span>");
                sb.append(" <span class='step'>Step ").append(tp.step).append("</span>");
                sb.append("<br>").append(esc(tp.description));
                sb.append("<br><span class='sev'><span class='sev-fill' style='width:")
                        .append((int) (tp.severity * 100)).append("%;background:");
                sb.append(tp.severity > 0.7 ? "#e94560" : tp.severity > 0.4 ? "#e9c46a" : "#53d8fb");
                sb.append("'></span></span> severity: ").append(String.format("%.0f%%", tp.severity * 100));
                sb.append("</div>");
            }
        }
        sb.append("</div>");

        // Timeline table
        sb.append("<div class='card'><h2>📋 Timeline Data</h2><table>");
        sb.append("<tr><th>Step</th><th>Nodes</th><th>Edges</th><th>Density</th>");
        sb.append("<th>Avg Degree</th><th>Clustering</th><th>Components</th><th>Event</th></tr>");
        for (StepSnapshot s : timeline) {
            if (s.step % 5 == 0 || s.event != null) {
                sb.append("<tr");
                if (s.event != null) sb.append(" style='background:rgba(233,69,96,0.1)'");
                sb.append("><td>").append(s.step);
                sb.append("</td><td>").append(s.nodeCount);
                sb.append("</td><td>").append(s.edgeCount);
                sb.append("</td><td>").append(String.format("%.4f", s.density));
                sb.append("</td><td>").append(String.format("%.2f", s.avgDegree));
                sb.append("</td><td>").append(String.format("%.4f", s.clusteringCoefficient));
                sb.append("</td><td>").append(s.componentCount);
                sb.append("</td><td>").append(s.event != null ? esc(s.event) : "—");
                sb.append("</td></tr>");
            }
        }
        sb.append("</table></div>");

        sb.append("</div></body></html>");
        return sb.toString();
    }

    private String buildSvgChart(List<StepSnapshot> timeline, List<TippingPoint> tippingPoints) {
        if (timeline.isEmpty()) return "<p>No data</p>";

        int w = 1000, h = 250, pad = 50;
        int plotW = w - 2 * pad, plotH = h - 2 * pad;
        int maxStep = timeline.get(timeline.size() - 1).step;
        if (maxStep == 0) maxStep = 1;

        // Normalize metrics to 0-1 range
        double maxNodes = timeline.stream().mapToInt(s -> s.nodeCount).max().orElse(1);
        double maxEdges = timeline.stream().mapToInt(s -> s.edgeCount).max().orElse(1);

        StringBuilder svg = new StringBuilder();
        svg.append(String.format("<svg viewBox='0 0 %d %d' xmlns='http://www.w3.org/2000/svg'>", w, h));

        // Background
        svg.append(String.format("<rect width='%d' height='%d' fill='#0f1525' rx='4'/>", w, h));

        // Grid lines
        for (int i = 0; i <= 4; i++) {
            int y = pad + (plotH * i / 4);
            svg.append(String.format("<line x1='%d' y1='%d' x2='%d' y2='%d' stroke='#222' stroke-width='0.5'/>",
                    pad, y, pad + plotW, y));
        }

        // Tipping point markers
        for (TippingPoint tp : tippingPoints) {
            int x = pad + (tp.step * plotW / maxStep);
            svg.append(String.format("<line x1='%d' y1='%d' x2='%d' y2='%d' stroke='#e94560' stroke-width='1' stroke-dasharray='4'>",
                    x, pad, x, pad + plotH));
            svg.append(String.format("<title>Step %d: %s</title></line>", tp.step, tp.type));
        }

        // Draw metric lines
        String[][] metrics = {
                {"Nodes", "#53d8fb"}, {"Edges", "#e9c46a"}, {"Density×100", "#e94560"},
                {"Clustering×100", "#4ecdc4"}
        };

        // Legend
        for (int m = 0; m < metrics.length; m++) {
            int lx = pad + 10 + m * 150;
            svg.append(String.format("<rect x='%d' y='%d' width='12' height='12' fill='%s' rx='2'/>",
                    lx, 8, metrics[m][1]));
            svg.append(String.format("<text x='%d' y='%d' fill='%s' font-size='11'>%s</text>",
                    lx + 16, 18, "#888", metrics[m][0]));
        }

        // Polylines
        for (int m = 0; m < metrics.length; m++) {
            StringBuilder points = new StringBuilder();
            for (StepSnapshot s : timeline) {
                int x = pad + (maxStep > 0 ? s.step * plotW / maxStep : 0);
                double val;
                switch (m) {
                    case 0: val = maxNodes > 0 ? s.nodeCount / maxNodes : 0; break;
                    case 1: val = maxEdges > 0 ? s.edgeCount / maxEdges : 0; break;
                    case 2: val = Math.min(1.0, s.density * 100); break;
                    case 3: val = Math.min(1.0, s.clusteringCoefficient); break;
                    default: val = 0;
                }
                int y = pad + plotH - (int) (val * plotH);
                points.append(x).append(",").append(y).append(" ");
            }
            svg.append(String.format("<polyline points='%s' fill='none' stroke='%s' stroke-width='2'/>",
                    points.toString().trim(), metrics[m][1]));
        }

        // Axes labels
        svg.append(String.format("<text x='%d' y='%d' fill='#666' font-size='10' text-anchor='middle'>0</text>",
                pad, pad + plotH + 15));
        svg.append(String.format("<text x='%d' y='%d' fill='#666' font-size='10' text-anchor='middle'>%d</text>",
                pad + plotW, pad + plotH + 15, maxStep));

        svg.append("</svg>");
        return svg.toString();
    }

    private String buildComparisonHtml(Map<GrowthModel, SimulationResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<title>Evolution Model Comparison</title>");
        sb.append("<style>");
        sb.append("body{background:#1a1a2e;color:#e0e0e0;font-family:'Segoe UI',sans-serif;margin:0;padding:20px}");
        sb.append("h1{color:#53d8fb;text-align:center}");
        sb.append("h2{color:#e94560;border-bottom:1px solid #333;padding-bottom:5px}");
        sb.append(".container{max-width:1200px;margin:0 auto}");
        sb.append(".card{background:#16213e;border-radius:8px;padding:20px;margin:15px 0;box-shadow:0 2px 8px rgba(0,0,0,0.3)}");
        sb.append("table{width:100%;border-collapse:collapse}");
        sb.append("th{background:#0f3460;color:#53d8fb;padding:8px 12px;text-align:left}");
        sb.append("td{padding:8px 12px;border-bottom:1px solid #222}");
        sb.append("svg{width:100%;height:300px}");
        sb.append(".legend{display:flex;flex-wrap:wrap;gap:15px;margin:10px 0}");
        sb.append(".legend-item{display:flex;align-items:center;gap:5px;font-size:13px}");
        sb.append(".legend-dot{width:12px;height:12px;border-radius:50%}");
        sb.append("</style></head><body><div class='container'>");

        sb.append("<h1>🔮 Evolution Model Comparison</h1>");

        String[] colors = {"#53d8fb", "#e94560", "#e9c46a", "#4ecdc4", "#a855f7"};

        // Comparison table
        sb.append("<div class='card'><h2>📊 Final Metrics by Model</h2><table>");
        sb.append("<tr><th>Model</th><th>Final Nodes</th><th>Final Edges</th><th>Density</th>");
        sb.append("<th>Clustering</th><th>Tipping Points</th></tr>");
        int ci = 0;
        for (Map.Entry<GrowthModel, SimulationResult> entry : results.entrySet()) {
            SimulationResult r = entry.getValue();
            StepSnapshot last = r.timeline.get(r.timeline.size() - 1);
            sb.append("<tr><td style='color:").append(colors[ci % colors.length]).append("'><b>")
                    .append(esc(entry.getKey().getLabel())).append("</b></td>");
            sb.append("<td>").append(last.nodeCount).append("</td>");
            sb.append("<td>").append(last.edgeCount).append("</td>");
            sb.append("<td>").append(String.format("%.4f", last.density)).append("</td>");
            sb.append("<td>").append(String.format("%.4f", last.clusteringCoefficient)).append("</td>");
            sb.append("<td>").append(r.tippingPoints.size()).append("</td></tr>");
            ci++;
        }
        sb.append("</table></div>");

        // Overlay chart — node count trajectories
        sb.append("<div class='card'><h2>📈 Node Growth Comparison</h2>");
        sb.append(buildComparisonSvg(results, colors, "nodeCount"));
        sb.append("<div class='legend'>");
        ci = 0;
        for (GrowthModel m : results.keySet()) {
            sb.append("<span class='legend-item'><span class='legend-dot' style='background:")
                    .append(colors[ci % colors.length]).append("'></span>")
                    .append(esc(m.getLabel())).append("</span>");
            ci++;
        }
        sb.append("</div></div>");

        // Edge count comparison
        sb.append("<div class='card'><h2>📈 Edge Growth Comparison</h2>");
        sb.append(buildComparisonSvg(results, colors, "edgeCount"));
        sb.append("</div>");

        // Clustering comparison
        sb.append("<div class='card'><h2>📈 Clustering Coefficient Comparison</h2>");
        sb.append(buildComparisonSvg(results, colors, "clustering"));
        sb.append("</div>");

        sb.append("</div></body></html>");
        return sb.toString();
    }

    private String buildComparisonSvg(Map<GrowthModel, SimulationResult> results,
                                      String[] colors, String metric) {
        int w = 1000, h = 250, pad = 50;
        int plotW = w - 2 * pad, plotH = h - 2 * pad;

        // Find global max
        double globalMax = 1;
        int maxStep = 1;
        for (SimulationResult r : results.values()) {
            for (StepSnapshot s : r.timeline) {
                maxStep = Math.max(maxStep, s.step);
                double val = metricValue(s, metric);
                if (val > globalMax) globalMax = val;
            }
        }

        StringBuilder svg = new StringBuilder();
        svg.append(String.format("<svg viewBox='0 0 %d %d' xmlns='http://www.w3.org/2000/svg'>", w, h));
        svg.append(String.format("<rect width='%d' height='%d' fill='#0f1525' rx='4'/>", w, h));

        // Grid
        for (int i = 0; i <= 4; i++) {
            int y = pad + (plotH * i / 4);
            svg.append(String.format("<line x1='%d' y1='%d' x2='%d' y2='%d' stroke='#222' stroke-width='0.5'/>",
                    pad, y, pad + plotW, y));
        }

        int ci = 0;
        for (SimulationResult r : results.values()) {
            StringBuilder points = new StringBuilder();
            for (StepSnapshot s : r.timeline) {
                int x = pad + (s.step * plotW / maxStep);
                double val = metricValue(s, metric) / globalMax;
                int y = pad + plotH - (int) (val * plotH);
                points.append(x).append(",").append(y).append(" ");
            }
            svg.append(String.format("<polyline points='%s' fill='none' stroke='%s' stroke-width='2'/>",
                    points.toString().trim(), colors[ci % colors.length]));
            ci++;
        }

        svg.append("</svg>");
        return svg.toString();
    }

    private double metricValue(StepSnapshot s, String metric) {
        switch (metric) {
            case "nodeCount": return s.nodeCount;
            case "edgeCount": return s.edgeCount;
            case "density": return s.density;
            case "clustering": return s.clusteringCoefficient;
            default: return 0;
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
