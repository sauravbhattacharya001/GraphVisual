package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GraphControllabilityEngine — autonomous network controllability analysis engine
 * based on structural controllability theory (Liu et al. 2011). Uses maximum
 * bipartite matching to identify the minimum set of driver nodes needed to steer
 * the entire network to any desired state.
 *
 * <h3>Seven Analysis Engines:</h3>
 * <ol>
 *   <li><b>Maximum Matching Engine</b> — Hopcroft–Karp algorithm on the bipartite
 *       representation of the directed graph. Undirected edges are treated as
 *       bidirectional. Computes maximum matching size.</li>
 *   <li><b>Driver Node Identifier</b> — Unmatched nodes in the maximum matching are
 *       driver nodes requiring external control signals. Computes driver set,
 *       density nD/N, and per-node role.</li>
 *   <li><b>Control Centrality Calculator</b> — For each node, fraction of all nodes
 *       reachable via directed paths. Ranks nodes by control centrality.</li>
 *   <li><b>Controllability Robustness Analyzer</b> — Monte Carlo simulation of
 *       random node removal, recomputing driver fraction at each step to measure
 *       degradation. Robustness index = area under the nD curve.</li>
 *   <li><b>Control Category Classifier</b> — Classifies nodes as CRITICAL (always
 *       drivers), REDUNDANT (never drivers), or INTERMITTENT (sometimes drivers)
 *       across multiple random maximum matchings.</li>
 *   <li><b>Control Profile Generator</b> — Fraction of source (in-degree 0),
 *       internal, and sink (out-degree 0) nodes among driver nodes.</li>
 *   <li><b>Insight Generator</b> — Autonomous insights about controllability
 *       patterns and recommendations for improving controllability.</li>
 * </ol>
 *
 * <p>Usage:</p>
 * <pre>
 *   GraphControllabilityEngine engine = new GraphControllabilityEngine();
 *   ControllabilityReport report = engine.analyze(graph);
 *   System.out.println(engine.toText(report));
 *   String html = engine.exportHtml(report);
 * </pre>
 *
 * @author sauravbhattacharya001
 */
public class GraphControllabilityEngine {

    // -- Configuration --------------------------------------------------------
    private int monteCarloTrials = 50;
    private int categoryTrials = 20;
    private Random rng = new Random(42);

    // -- Builder-style setters ------------------------------------------------

    public GraphControllabilityEngine setMonteCarloTrials(int n) {
        this.monteCarloTrials = n; return this;
    }

    public GraphControllabilityEngine setCategoryTrials(int n) {
        this.categoryTrials = n; return this;
    }

    public GraphControllabilityEngine setRandomSeed(long seed) {
        this.rng = new Random(seed); return this;
    }

    public GraphControllabilityEngine setRng(Random rng) {
        this.rng = rng; return this;
    }

    // ==================================================================
    // Inner classes
    // ==================================================================

    /** Node control category. */
    public enum ControlCategory { CRITICAL, REDUNDANT, INTERMITTENT }

    /** Full controllability analysis report. */
    public static class ControllabilityReport {
        public final Set<String> driverNodes;
        public final double driverNodeDensity;
        public final int matchingSize;
        public final Map<String, Double> controlCentrality;
        public final List<String> topControlNodes;
        public final double robustnessIndex;
        public final Map<Double, Double> robustnessCurve;
        public final Map<String, String> nodeCategories;
        public final Map<String, Double> controlProfile;
        public final double healthScore;
        public final List<String> insights;
        public final int nodeCount;
        public final int edgeCount;

        public ControllabilityReport(Set<String> driverNodes,
                                     double driverNodeDensity,
                                     int matchingSize,
                                     Map<String, Double> controlCentrality,
                                     List<String> topControlNodes,
                                     double robustnessIndex,
                                     Map<Double, Double> robustnessCurve,
                                     Map<String, String> nodeCategories,
                                     Map<String, Double> controlProfile,
                                     double healthScore,
                                     List<String> insights,
                                     int nodeCount, int edgeCount) {
            this.driverNodes = Collections.unmodifiableSet(new LinkedHashSet<>(driverNodes));
            this.driverNodeDensity = driverNodeDensity;
            this.matchingSize = matchingSize;
            this.controlCentrality = Collections.unmodifiableMap(new LinkedHashMap<>(controlCentrality));
            this.topControlNodes = Collections.unmodifiableList(new ArrayList<>(topControlNodes));
            this.robustnessIndex = robustnessIndex;
            this.robustnessCurve = Collections.unmodifiableMap(new LinkedHashMap<>(robustnessCurve));
            this.nodeCategories = Collections.unmodifiableMap(new LinkedHashMap<>(nodeCategories));
            this.controlProfile = Collections.unmodifiableMap(new LinkedHashMap<>(controlProfile));
            this.healthScore = healthScore;
            this.insights = Collections.unmodifiableList(new ArrayList<>(insights));
            this.nodeCount = nodeCount;
            this.edgeCount = edgeCount;
        }
    }

    // ==================================================================
    // Main analysis
    // ==================================================================

    /**
     * Run full controllability analysis on the given graph.
     *
     * @param graph the network to analyze
     * @return comprehensive controllability report
     */
    public ControllabilityReport analyze(Graph<String, Edge> graph) {
        List<String> vertices = new ArrayList<>(graph.getVertices());
        int n = vertices.size();
        int e = graph.getEdgeCount();

        if (n == 0) {
            return new ControllabilityReport(
                    Collections.emptySet(), 0.0, 0,
                    Collections.emptyMap(), Collections.emptyList(),
                    0.0, Collections.emptyMap(),
                    Collections.emptyMap(),
                    emptyProfile(),
                    0.0,
                    Collections.singletonList("Empty graph — no controllability analysis possible."),
                    0, 0);
        }

        if (n == 1) {
            String v = vertices.get(0);
            Set<String> drivers = new LinkedHashSet<>();
            drivers.add(v);
            Map<String, Double> cc = new LinkedHashMap<>();
            cc.put(v, 1.0);
            Map<String, String> cats = new LinkedHashMap<>();
            cats.put(v, "CRITICAL");
            Map<Double, Double> curve = new LinkedHashMap<>();
            curve.put(0.0, 1.0);
            curve.put(1.0, 1.0);
            Map<String, Double> profile = new LinkedHashMap<>();
            profile.put("source", 1.0);
            profile.put("internal", 0.0);
            profile.put("sink", 0.0);
            return new ControllabilityReport(
                    drivers, 1.0, 0,
                    cc, Collections.singletonList(v),
                    1.0, curve, cats, profile, 50.0,
                    Collections.singletonList("Single-node graph — trivially requires one driver."),
                    1, 0);
        }

        // Build directed adjacency (undirected edges → bidirectional)
        Map<String, Set<String>> adjOut = new LinkedHashMap<>();
        Map<String, Set<String>> adjIn = new LinkedHashMap<>();
        for (String v : vertices) {
            adjOut.put(v, new LinkedHashSet<>());
            adjIn.put(v, new LinkedHashSet<>());
        }
        for (Edge edge : graph.getEdges()) {
            String v1 = graph.getEndpoints(edge).getFirst();
            String v2 = graph.getEndpoints(edge).getSecond();
            adjOut.get(v1).add(v2);
            adjIn.get(v2).add(v1);
            adjOut.get(v2).add(v1);
            adjIn.get(v1).add(v2);
        }

        // Engine 1: Maximum matching
        Map<String, String> matching = hopcroftKarp(vertices, adjOut);
        int matchingSize = matching.size();

        // Engine 2: Driver nodes
        Set<String> matched = new LinkedHashSet<>(matching.values());
        Set<String> driverNodes = new LinkedHashSet<>();
        for (String v : vertices) {
            if (!matched.contains(v)) {
                driverNodes.add(v);
            }
        }
        if (driverNodes.isEmpty()) {
            // At least one driver is needed
            driverNodes.add(vertices.get(0));
        }
        double driverDensity = round((double) driverNodes.size() / n);

        // Engine 3: Control centrality
        Map<String, Double> controlCentrality = computeControlCentrality(vertices, adjOut, n);
        List<String> topControl = controlCentrality.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(Math.min(10, n))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Engine 4: Robustness analysis
        Map<Double, Double> robustnessCurve = computeRobustnessCurve(vertices, graph, n);
        double robustnessIndex = computeRobustnessIndex(robustnessCurve);

        // Engine 5: Control categories
        Map<String, String> nodeCategories = classifyNodes(vertices, adjOut);

        // Engine 6: Control profile
        Map<String, Double> controlProfile = computeControlProfile(driverNodes, adjIn, adjOut);

        // Health score: lower driver density = more controllable = healthier
        // Also factor in robustness
        double healthRaw = ((1.0 - driverDensity) * 60.0 + robustnessIndex * 40.0);
        double healthScore = Math.min(100.0, Math.max(0.0, round(healthRaw)));

        // Engine 7: Insights
        List<String> insights = generateInsights(driverNodes, driverDensity,
                matchingSize, controlCentrality, robustnessIndex, nodeCategories,
                controlProfile, n, e);

        return new ControllabilityReport(
                driverNodes, driverDensity, matchingSize,
                controlCentrality, topControl,
                robustnessIndex, robustnessCurve,
                nodeCategories, controlProfile,
                healthScore, insights, n, e);
    }

    // ==================================================================
    // Engine 1: Maximum Matching (Hopcroft-Karp)
    // ==================================================================

    /**
     * Hopcroft-Karp maximum matching on bipartite graph representation.
     * Left partition = nodes as "sources", Right partition = nodes as "targets".
     * An edge u→v means left-u can match to right-v.
     *
     * @return map from left-node to right-node (matched pairs)
     */
    private Map<String, String> hopcroftKarp(List<String> vertices,
                                              Map<String, Set<String>> adjOut) {
        // matchL[u] = right node matched to left-u (null if free)
        Map<String, String> matchL = new LinkedHashMap<>();
        // matchR[v] = left node matched to right-v (null if free)
        Map<String, String> matchR = new LinkedHashMap<>();
        Map<String, Integer> dist = new LinkedHashMap<>();

        boolean found = true;
        while (found) {
            // BFS phase: find shortest augmenting path layers
            Queue<String> queue = new LinkedList<>();
            for (String u : vertices) {
                if (!matchL.containsKey(u)) {
                    dist.put(u, 0);
                    queue.add(u);
                } else {
                    dist.put(u, Integer.MAX_VALUE);
                }
            }
            found = false;

            while (!queue.isEmpty()) {
                String u = queue.poll();
                for (String v : adjOut.getOrDefault(u, Collections.emptySet())) {
                    String mu = matchR.get(v);
                    if (mu == null) {
                        found = true;
                    } else if (dist.getOrDefault(mu, Integer.MAX_VALUE) == Integer.MAX_VALUE) {
                        dist.put(mu, dist.getOrDefault(u, 0) + 1);
                        queue.add(mu);
                    }
                }
            }

            if (found) {
                for (String u : vertices) {
                    if (!matchL.containsKey(u)) {
                        dfsAugment(u, matchL, matchR, dist, adjOut);
                    }
                }
            }
        }
        return matchL;
    }

    private boolean dfsAugment(String u,
                                Map<String, String> matchL,
                                Map<String, String> matchR,
                                Map<String, Integer> dist,
                                Map<String, Set<String>> adjOut) {
        for (String v : adjOut.getOrDefault(u, Collections.emptySet())) {
            String mu = matchR.get(v);
            if (mu == null ||
                (dist.getOrDefault(mu, Integer.MAX_VALUE) == dist.getOrDefault(u, 0) + 1
                 && dfsAugment(mu, matchL, matchR, dist, adjOut))) {
                matchL.put(u, v);
                matchR.put(v, u);
                return true;
            }
        }
        dist.put(u, Integer.MAX_VALUE);
        return false;
    }

    // ==================================================================
    // Engine 2: Driver node identification (done in analyze())
    // ==================================================================

    // ==================================================================
    // Engine 3: Control centrality
    // ==================================================================

    private Map<String, Double> computeControlCentrality(List<String> vertices,
                                                          Map<String, Set<String>> adjOut,
                                                          int n) {
        Map<String, Double> centrality = new LinkedHashMap<>();
        for (String v : vertices) {
            // BFS reachability from v
            Set<String> reached = new HashSet<>();
            Queue<String> queue = new LinkedList<>();
            queue.add(v);
            reached.add(v);
            while (!queue.isEmpty()) {
                String cur = queue.poll();
                for (String nb : adjOut.getOrDefault(cur, Collections.emptySet())) {
                    if (reached.add(nb)) {
                        queue.add(nb);
                    }
                }
            }
            centrality.put(v, round((double) reached.size() / n));
        }
        return centrality;
    }

    // ==================================================================
    // Engine 4: Controllability robustness
    // ==================================================================

    private Map<Double, Double> computeRobustnessCurve(List<String> vertices,
                                                        Graph<String, Edge> graph,
                                                        int n) {
        int steps = Math.min(n, 20);
        Map<Double, Double> curve = new LinkedHashMap<>();
        curve.put(0.0, round(computeDriverDensityForSubgraph(vertices, graph)));

        int trials = Math.min(monteCarloTrials, 30);
        for (int s = 1; s <= steps; s++) {
            double frac = round((double) s / steps);
            int toRemove = (int) Math.round(frac * n);
            if (toRemove >= n) {
                curve.put(frac, 1.0);
                continue;
            }

            double avgDensity = 0.0;
            for (int t = 0; t < trials; t++) {
                List<String> shuffled = new ArrayList<>(vertices);
                Collections.shuffle(shuffled, rng);
                List<String> remaining = shuffled.subList(toRemove, n);
                avgDensity += computeDriverDensityForSubgraph(remaining, graph);
            }
            curve.put(frac, round(avgDensity / trials));
        }
        return curve;
    }

    private double computeDriverDensityForSubgraph(List<String> subVertices,
                                                    Graph<String, Edge> graph) {
        if (subVertices.isEmpty()) return 1.0;
        if (subVertices.size() == 1) return 1.0;

        Set<String> vSet = new HashSet<>(subVertices);
        Map<String, Set<String>> adjOut = new LinkedHashMap<>();
        for (String v : subVertices) {
            adjOut.put(v, new LinkedHashSet<>());
        }
        for (Edge edge : graph.getEdges()) {
            String v1 = graph.getEndpoints(edge).getFirst();
            String v2 = graph.getEndpoints(edge).getSecond();
            if (vSet.contains(v1) && vSet.contains(v2)) {
                adjOut.get(v1).add(v2);
                adjOut.get(v2).add(v1);
            }
        }

        Map<String, String> matching = hopcroftKarp(subVertices, adjOut);
        Set<String> matched = new LinkedHashSet<>(matching.values());
        int drivers = 0;
        for (String v : subVertices) {
            if (!matched.contains(v)) drivers++;
        }
        if (drivers == 0) drivers = 1;
        return (double) drivers / subVertices.size();
    }

    private double computeRobustnessIndex(Map<Double, Double> curve) {
        // Area under the (1 - driverDensity) curve, normalized
        List<Map.Entry<Double, Double>> entries = new ArrayList<>(curve.entrySet());
        if (entries.size() < 2) return 0.5;

        double area = 0.0;
        for (int i = 1; i < entries.size(); i++) {
            double dx = entries.get(i).getKey() - entries.get(i - 1).getKey();
            double y1 = 1.0 - entries.get(i - 1).getValue();
            double y2 = 1.0 - entries.get(i).getValue();
            area += dx * (y1 + y2) / 2.0;
        }
        return round(Math.min(1.0, Math.max(0.0, area)));
    }

    // ==================================================================
    // Engine 5: Control category classification
    // ==================================================================

    private Map<String, String> classifyNodes(List<String> vertices,
                                               Map<String, Set<String>> adjOut) {
        Map<String, Integer> driverCount = new LinkedHashMap<>();
        for (String v : vertices) driverCount.put(v, 0);

        for (int t = 0; t < categoryTrials; t++) {
            // Randomize vertex order for matching diversity
            List<String> shuffled = new ArrayList<>(vertices);
            Collections.shuffle(shuffled, rng);

            Map<String, String> matching = hopcroftKarp(shuffled, adjOut);
            Set<String> matched = new LinkedHashSet<>(matching.values());
            for (String v : shuffled) {
                if (!matched.contains(v)) {
                    driverCount.put(v, driverCount.get(v) + 1);
                }
            }
        }

        Map<String, String> categories = new LinkedHashMap<>();
        for (String v : vertices) {
            int count = driverCount.get(v);
            if (count == categoryTrials) {
                categories.put(v, "CRITICAL");
            } else if (count == 0) {
                categories.put(v, "REDUNDANT");
            } else {
                categories.put(v, "INTERMITTENT");
            }
        }
        return categories;
    }

    // ==================================================================
    // Engine 6: Control profile
    // ==================================================================

    private Map<String, Double> computeControlProfile(Set<String> driverNodes,
                                                       Map<String, Set<String>> adjIn,
                                                       Map<String, Set<String>> adjOut) {
        if (driverNodes.isEmpty()) return emptyProfile();

        int sources = 0, sinks = 0, internal = 0;
        for (String d : driverNodes) {
            boolean isSource = adjIn.getOrDefault(d, Collections.emptySet()).isEmpty();
            boolean isSink = adjOut.getOrDefault(d, Collections.emptySet()).isEmpty();
            if (isSource && !isSink) sources++;
            else if (isSink && !isSource) sinks++;
            else internal++;
        }
        double total = driverNodes.size();
        Map<String, Double> profile = new LinkedHashMap<>();
        profile.put("source", round(sources / total));
        profile.put("internal", round(internal / total));
        profile.put("sink", round(sinks / total));
        return profile;
    }

    private Map<String, Double> emptyProfile() {
        Map<String, Double> p = new LinkedHashMap<>();
        p.put("source", 0.0);
        p.put("internal", 0.0);
        p.put("sink", 0.0);
        return p;
    }

    // ==================================================================
    // Engine 7: Insight generation
    // ==================================================================

    private List<String> generateInsights(Set<String> driverNodes,
                                           double driverDensity,
                                           int matchingSize,
                                           Map<String, Double> controlCentrality,
                                           double robustnessIndex,
                                           Map<String, String> nodeCategories,
                                           Map<String, Double> controlProfile,
                                           int n, int e) {
        List<String> insights = new ArrayList<>();

        // Driver density insight
        if (driverDensity < 0.2) {
            insights.add(String.format("Excellent controllability: only %.0f%% of nodes need external control (nD=%.2f).",
                    driverDensity * 100, driverDensity));
        } else if (driverDensity < 0.5) {
            insights.add(String.format("Moderate controllability: %.0f%% of nodes are drivers (nD=%.2f). " +
                    "Adding strategic edges could reduce this.", driverDensity * 100, driverDensity));
        } else {
            insights.add(String.format("Poor controllability: %.0f%% of nodes require external control (nD=%.2f). " +
                    "The network lacks sufficient internal connections.", driverDensity * 100, driverDensity));
        }

        // Matching insight
        double matchingRatio = n > 0 ? (double) matchingSize / n : 0;
        insights.add(String.format("Maximum matching covers %d of %d nodes (%.0f%%).",
                matchingSize, n, matchingRatio * 100));

        // Robustness insight
        if (robustnessIndex > 0.7) {
            insights.add(String.format("High controllability robustness (%.2f): network maintains controllability " +
                    "well under random node failures.", robustnessIndex));
        } else if (robustnessIndex > 0.4) {
            insights.add(String.format("Moderate controllability robustness (%.2f): some vulnerability to " +
                    "random node removal.", robustnessIndex));
        } else {
            insights.add(String.format("Low controllability robustness (%.2f): controllability degrades quickly " +
                    "under node failures. Consider adding redundant links.", robustnessIndex));
        }

        // Category distribution
        long critical = nodeCategories.values().stream().filter("CRITICAL"::equals).count();
        long redundant = nodeCategories.values().stream().filter("REDUNDANT"::equals).count();
        long intermittent = nodeCategories.values().stream().filter("INTERMITTENT"::equals).count();
        insights.add(String.format("Node categories: %d critical, %d redundant, %d intermittent.",
                critical, redundant, intermittent));

        if (critical > n * 0.3) {
            insights.add("High fraction of critical nodes — these are bottlenecks. " +
                    "Protecting them is essential for maintaining controllability.");
        }

        // Top control centrality
        if (!controlCentrality.isEmpty()) {
            Map.Entry<String, Double> top = controlCentrality.entrySet().stream()
                    .max(Comparator.comparingDouble(Map.Entry::getValue))
                    .orElse(null);
            if (top != null) {
                insights.add(String.format("Highest control centrality: node '%s' (%.2f) — can reach %.0f%% of the network.",
                        top.getKey(), top.getValue(), top.getValue() * 100));
            }
        }

        // Control profile
        double sourceFrac = controlProfile.getOrDefault("source", 0.0);
        double sinkFrac = controlProfile.getOrDefault("sink", 0.0);
        if (sourceFrac > 0.5) {
            insights.add(String.format("%.0f%% of driver nodes are sources (in-degree 0) — " +
                    "typical of hierarchical or tree-like structures.", sourceFrac * 100));
        }
        if (sinkFrac > 0.5) {
            insights.add(String.format("%.0f%% of driver nodes are sinks (out-degree 0) — " +
                    "leaf nodes that require direct control signals.", sinkFrac * 100));
        }

        // Density-based recommendation
        double density = n > 1 ? (double) (2 * e) / (n * (n - 1)) : 0;
        if (density < 0.2 && driverDensity > 0.3) {
            insights.add("Sparse network with high driver fraction — adding edges between " +
                    "driver nodes and non-drivers would improve controllability.");
        }

        return insights;
    }

    // ==================================================================
    // Text output
    // ==================================================================

    /**
     * Format the report as readable text.
     */
    public String toText(ControllabilityReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Graph Network Controllability Report ===\n\n");
        sb.append(String.format("Nodes: %d | Edges: %d\n", report.nodeCount, report.edgeCount));
        sb.append(String.format("Health Score: %.1f / 100\n\n", report.healthScore));

        sb.append("--- Driver Nodes ---\n");
        sb.append(String.format("Driver count: %d (density: %.3f)\n", report.driverNodes.size(), report.driverNodeDensity));
        sb.append(String.format("Maximum matching size: %d\n", report.matchingSize));
        sb.append("Drivers: ").append(report.driverNodes).append("\n\n");

        sb.append("--- Control Centrality (Top 10) ---\n");
        for (String node : report.topControlNodes) {
            sb.append(String.format("  %-15s %.3f\n", node, report.controlCentrality.getOrDefault(node, 0.0)));
        }
        sb.append("\n");

        sb.append("--- Controllability Robustness ---\n");
        sb.append(String.format("Robustness index: %.3f\n", report.robustnessIndex));
        sb.append("Degradation curve (fraction removed → driver density):\n");
        for (Map.Entry<Double, Double> e : report.robustnessCurve.entrySet()) {
            sb.append(String.format("  %.2f → %.3f\n", e.getKey(), e.getValue()));
        }
        sb.append("\n");

        sb.append("--- Node Categories ---\n");
        Map<String, Long> catCounts = report.nodeCategories.values().stream()
                .collect(Collectors.groupingBy(c -> c, Collectors.counting()));
        for (Map.Entry<String, Long> e : catCounts.entrySet()) {
            sb.append(String.format("  %-14s %d nodes\n", e.getKey(), e.getValue()));
        }
        sb.append("\n");

        sb.append("--- Control Profile ---\n");
        for (Map.Entry<String, Double> e : report.controlProfile.entrySet()) {
            sb.append(String.format("  %-10s %.1f%%\n", e.getKey(), e.getValue() * 100));
        }
        sb.append("\n");

        sb.append("--- Insights ---\n");
        for (int i = 0; i < report.insights.size(); i++) {
            sb.append(String.format("  %d. %s\n", i + 1, report.insights.get(i)));
        }

        return sb.toString();
    }

    // ==================================================================
    // HTML export
    // ==================================================================

    /**
     * Export the report as a self-contained interactive HTML dashboard.
     */
    public String exportHtml(ControllabilityReport report) {
        StringBuilder h = new StringBuilder();
        h.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        h.append("<meta charset=\"UTF-8\">\n");
        h.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n");
        h.append("<title>Network Controllability Dashboard</title>\n");
        h.append("<style>\n");
        h.append("*{box-sizing:border-box;margin:0;padding:0}\n");
        h.append("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;");
        h.append("background:#0f172a;color:#e2e8f0;padding:20px}\n");
        h.append("h1{color:#38bdf8;margin-bottom:8px;font-size:1.6em}\n");
        h.append("h2{color:#7dd3fc;margin:18px 0 8px;font-size:1.2em;border-bottom:1px solid #334155;padding-bottom:4px}\n");
        h.append(".card{background:#1e293b;border-radius:10px;padding:16px;margin-bottom:14px}\n");
        h.append(".gauge{display:inline-block;width:120px;height:120px;border-radius:50%;");
        h.append("border:8px solid #334155;position:relative;text-align:center;line-height:104px;font-size:1.8em;font-weight:bold}\n");
        h.append(".row{display:flex;gap:14px;flex-wrap:wrap}\n");
        h.append(".col{flex:1;min-width:280px}\n");
        h.append(".bar-container{display:flex;align-items:center;margin:3px 0}\n");
        h.append(".bar-label{width:100px;font-size:0.85em;text-align:right;padding-right:8px;color:#94a3b8}\n");
        h.append(".bar{height:18px;border-radius:4px;min-width:2px}\n");
        h.append(".bar-val{font-size:0.8em;padding-left:6px;color:#94a3b8}\n");
        h.append(".insight{background:#1a2332;border-left:3px solid #38bdf8;padding:8px 12px;margin:6px 0;border-radius:0 6px 6px 0;font-size:0.9em}\n");
        h.append(".tag{display:inline-block;padding:2px 8px;border-radius:4px;font-size:0.75em;margin:2px}\n");
        h.append(".tag-critical{background:#dc2626;color:#fff}\n");
        h.append(".tag-redundant{background:#16a34a;color:#fff}\n");
        h.append(".tag-intermittent{background:#d97706;color:#fff}\n");
        h.append("table{width:100%;border-collapse:collapse;font-size:0.85em}\n");
        h.append("th,td{padding:6px 10px;text-align:left;border-bottom:1px solid #334155}\n");
        h.append("th{color:#7dd3fc}\n");
        h.append(".pie-container{display:flex;justify-content:center;gap:40px;flex-wrap:wrap}\n");
        h.append(".pie-legend{display:flex;flex-direction:column;justify-content:center;gap:6px}\n");
        h.append(".pie-legend-item{display:flex;align-items:center;gap:6px;font-size:0.85em}\n");
        h.append(".legend-dot{width:12px;height:12px;border-radius:3px}\n");
        h.append("canvas{max-width:100%}\n");
        h.append("</style>\n</head>\n<body>\n");

        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        h.append("<h1>&#x1F39B;&#xFE0F; Network Controllability Dashboard</h1>\n");
        h.append("<p style=\"color:#64748b;margin-bottom:16px\">Generated: ").append(ts).append("</p>\n");

        // Summary card
        h.append("<div class=\"card\"><div class=\"row\">\n");
        String gaugeColor = report.healthScore >= 70 ? "#22c55e" : report.healthScore >= 40 ? "#eab308" : "#ef4444";
        h.append("<div class=\"col\" style=\"text-align:center\">");
        h.append("<div class=\"gauge\" style=\"border-color:").append(gaugeColor).append(";color:").append(gaugeColor).append("\">");
        h.append(String.format("%.0f", report.healthScore)).append("</div>");
        h.append("<p style=\"margin-top:6px;color:#94a3b8\">Health Score</p></div>\n");
        h.append("<div class=\"col\">");
        h.append("<table>");
        h.append("<tr><th>Metric</th><th>Value</th></tr>");
        h.append(String.format("<tr><td>Nodes</td><td>%d</td></tr>", report.nodeCount));
        h.append(String.format("<tr><td>Edges</td><td>%d</td></tr>", report.edgeCount));
        h.append(String.format("<tr><td>Driver Nodes</td><td>%d (%.1f%%)</td></tr>",
                report.driverNodes.size(), report.driverNodeDensity * 100));
        h.append(String.format("<tr><td>Matching Size</td><td>%d</td></tr>", report.matchingSize));
        h.append(String.format("<tr><td>Robustness Index</td><td>%.3f</td></tr>", report.robustnessIndex));
        h.append("</table></div>\n");
        h.append("</div></div>\n");

        // Control centrality bar chart
        h.append("<div class=\"card\">\n");
        h.append("<h2>&#x1F4CA; Control Centrality (Top Nodes)</h2>\n");
        List<Map.Entry<String, Double>> topEntries = report.controlCentrality.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(20)
                .collect(Collectors.toList());
        double maxCC = topEntries.isEmpty() ? 1.0 : topEntries.get(0).getValue();
        for (Map.Entry<String, Double> e : topEntries) {
            double pct = maxCC > 0 ? (e.getValue() / maxCC) * 100 : 0;
            h.append("<div class=\"bar-container\">");
            h.append("<span class=\"bar-label\">").append(escHtml(e.getKey())).append("</span>");
            h.append(String.format("<div class=\"bar\" style=\"width:%.1f%%;background:#38bdf8\"></div>", pct));
            h.append(String.format("<span class=\"bar-val\">%.3f</span>", e.getValue()));
            h.append("</div>\n");
        }
        h.append("</div>\n");

        // Robustness curve
        h.append("<div class=\"card\">\n");
        h.append("<h2>&#x1F4C8; Controllability Robustness Curve</h2>\n");
        h.append("<canvas id=\"robChart\" height=\"200\"></canvas>\n");
        h.append("<script>\n");
        h.append("(function(){\n");
        h.append("var canvas=document.getElementById('robChart'),ctx=canvas.getContext('2d');\n");
        h.append("canvas.width=canvas.parentElement.clientWidth-32;canvas.height=200;\n");
        h.append("var data=[");
        boolean first = true;
        for (Map.Entry<Double, Double> e : report.robustnessCurve.entrySet()) {
            if (!first) h.append(",");
            h.append(String.format("[%.4f,%.4f]", e.getKey(), e.getValue()));
            first = false;
        }
        h.append("];\n");
        h.append("var w=canvas.width,ht=canvas.height,pad=40;\n");
        h.append("ctx.fillStyle='#1e293b';ctx.fillRect(0,0,w,ht);\n");
        h.append("ctx.strokeStyle='#334155';ctx.lineWidth=1;\n");
        // Grid
        h.append("for(var i=0;i<=4;i++){var y=pad+(ht-2*pad)*i/4;ctx.beginPath();ctx.moveTo(pad,y);ctx.lineTo(w-10,y);ctx.stroke();}\n");
        h.append("ctx.fillStyle='#94a3b8';ctx.font='11px sans-serif';ctx.textAlign='right';\n");
        h.append("for(var i=0;i<=4;i++){var y=pad+(ht-2*pad)*i/4;ctx.fillText((1-i/4).toFixed(2),pad-4,y+4);}\n");
        h.append("ctx.textAlign='center';\n");
        h.append("for(var i=0;i<=4;i++){var x=pad+(w-pad-10)*i/4;ctx.fillText((i/4).toFixed(2),x,ht-8);}\n");
        // Labels
        h.append("ctx.fillStyle='#7dd3fc';ctx.font='12px sans-serif';\n");
        h.append("ctx.fillText('Fraction of nodes removed',w/2,ht-0);\n");
        h.append("ctx.save();ctx.translate(12,ht/2);ctx.rotate(-Math.PI/2);ctx.fillText('Driver density (nD)',0,0);ctx.restore();\n");
        // Line
        h.append("if(data.length>1){ctx.strokeStyle='#f59e0b';ctx.lineWidth=2;ctx.beginPath();\n");
        h.append("for(var i=0;i<data.length;i++){var x=pad+data[i][0]*(w-pad-10),y=pad+(1-data[i][1])*(ht-2*pad);\n");
        h.append("if(i===0)ctx.moveTo(x,y);else ctx.lineTo(x,y);}ctx.stroke();\n");
        h.append("ctx.fillStyle='#f59e0b';for(var i=0;i<data.length;i++){var x=pad+data[i][0]*(w-pad-10),y=pad+(1-data[i][1])*(ht-2*pad);\n");
        h.append("ctx.beginPath();ctx.arc(x,y,3,0,Math.PI*2);ctx.fill();}}\n");
        h.append("})();\n</script>\n");
        h.append("</div>\n");

        // Node categories
        h.append("<div class=\"card\">\n");
        h.append("<h2>&#x1F3F7;&#xFE0F; Node Control Categories</h2>\n");
        long critical = report.nodeCategories.values().stream().filter("CRITICAL"::equals).count();
        long redundant = report.nodeCategories.values().stream().filter("REDUNDANT"::equals).count();
        long intermittent = report.nodeCategories.values().stream().filter("INTERMITTENT"::equals).count();
        h.append("<div class=\"pie-container\">\n");
        h.append("<canvas id=\"pieChart\" width=\"180\" height=\"180\"></canvas>\n");
        h.append("<div class=\"pie-legend\">\n");
        h.append(String.format("<div class=\"pie-legend-item\"><div class=\"legend-dot\" style=\"background:#dc2626\"></div>Critical: %d</div>\n", critical));
        h.append(String.format("<div class=\"pie-legend-item\"><div class=\"legend-dot\" style=\"background:#16a34a\"></div>Redundant: %d</div>\n", redundant));
        h.append(String.format("<div class=\"pie-legend-item\"><div class=\"legend-dot\" style=\"background:#d97706\"></div>Intermittent: %d</div>\n", intermittent));
        h.append("</div></div>\n");
        h.append("<script>\n(function(){\n");
        h.append(String.format("var d=[%d,%d,%d],c=['#dc2626','#16a34a','#d97706'],total=%d;\n",
                critical, redundant, intermittent, critical + redundant + intermittent));
        h.append("var cv=document.getElementById('pieChart'),ctx=cv.getContext('2d');\n");
        h.append("var cx=90,cy=90,r=80,start=-Math.PI/2;\n");
        h.append("for(var i=0;i<3;i++){if(d[i]===0)continue;var slice=d[i]/total*Math.PI*2;\n");
        h.append("ctx.beginPath();ctx.moveTo(cx,cy);ctx.arc(cx,cy,r,start,start+slice);ctx.closePath();\n");
        h.append("ctx.fillStyle=c[i];ctx.fill();start+=slice;}\n");
        h.append("})();\n</script>\n");
        h.append("</div>\n");

        // Control profile
        h.append("<div class=\"card\">\n");
        h.append("<h2>&#x1F4CB; Control Profile (Driver Node Types)</h2>\n");
        String[] profileKeys = {"source", "internal", "sink"};
        String[] profileColors = {"#06b6d4", "#8b5cf6", "#f43f5e"};
        for (int i = 0; i < 3; i++) {
            double val = report.controlProfile.getOrDefault(profileKeys[i], 0.0);
            h.append("<div class=\"bar-container\">");
            h.append("<span class=\"bar-label\">").append(profileKeys[i]).append("</span>");
            h.append(String.format("<div class=\"bar\" style=\"width:%.1f%%;background:%s\"></div>",
                    val * 100, profileColors[i]));
            h.append(String.format("<span class=\"bar-val\">%.1f%%</span>", val * 100));
            h.append("</div>\n");
        }
        h.append("</div>\n");

        // Driver nodes list
        h.append("<div class=\"card\">\n");
        h.append("<h2>&#x1F3AF; Driver Nodes</h2>\n");
        h.append("<p style=\"color:#94a3b8;font-size:0.85em;margin-bottom:8px\">Nodes requiring external control signals:</p>\n");
        for (String d : report.driverNodes) {
            String cat = report.nodeCategories.getOrDefault(d, "INTERMITTENT");
            String tagClass = cat.equals("CRITICAL") ? "tag-critical" : cat.equals("REDUNDANT") ? "tag-redundant" : "tag-intermittent";
            h.append("<span class=\"tag ").append(tagClass).append("\">").append(escHtml(d)).append("</span> ");
        }
        h.append("\n</div>\n");

        // Insights
        h.append("<div class=\"card\">\n");
        h.append("<h2>&#x1F4A1; Autonomous Insights</h2>\n");
        for (String insight : report.insights) {
            h.append("<div class=\"insight\">").append(escHtml(insight)).append("</div>\n");
        }
        h.append("</div>\n");

        h.append("<p style=\"text-align:center;color:#475569;margin-top:20px;font-size:0.8em\">");
        h.append("GraphControllabilityEngine — GraphVisual | zalenix</p>\n");
        h.append("</body>\n</html>");
        return h.toString();
    }

    // ==================================================================
    // Utilities
    // ==================================================================

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static String escHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
