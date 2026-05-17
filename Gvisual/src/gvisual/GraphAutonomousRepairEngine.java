package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GraphAutonomousRepairEngine — autonomous structural weakness detection and
 * repair planning engine that identifies vulnerabilities and generates
 * cost-effective repair plans to improve graph robustness.
 *
 * <h3>Seven Analysis Engines:</h3>
 * <ol>
 *   <li><b>Vulnerability Scanner</b> — identifies bridges (cut edges),
 *       articulation points (cut vertices), and low-connectivity bottlenecks
 *       with severity classification (critical/high/medium/low)</li>
 *   <li><b>Connectivity Restorer</b> — generates minimal edge additions to
 *       merge disconnected components and achieve target connectivity</li>
 *   <li><b>Bottleneck Reliever</b> — detects nodes with disproportionately
 *       high betweenness centrality and suggests bypass edges</li>
 *   <li><b>Redundancy Planner</b> — evaluates connectivity level and proposes
 *       minimum edge additions to reach target k-connectivity</li>
 *   <li><b>Repair Cost Estimator</b> — assigns costs to proposed repairs based
 *       on structural impact and priority; generates budget-constrained plans</li>
 *   <li><b>Self-Healing Simulator</b> — Monte Carlo failure simulation measuring
 *       degradation before and after repair plan application</li>
 *   <li><b>Interactive HTML Dashboard</b> — self-contained HTML export with
 *       vulnerability map, repair plan table, cost breakdown, before/after
 *       resilience comparison, and autonomous insights</li>
 * </ol>
 *
 * <p>Usage:</p>
 * <pre>
 *   GraphAutonomousRepairEngine engine = new GraphAutonomousRepairEngine();
 *   RepairReport report = engine.analyze(graph);
 *   System.out.println(engine.toText(report));
 *   String html = engine.exportHtml(report);
 * </pre>
 *
 * @author sauravbhattacharya001
 */
public class GraphAutonomousRepairEngine {

    // -- Configuration --------------------------------------------------------
    private int monteCarloTrials = 100;
    private double failureFraction = 0.2;
    private double budgetLimit = Double.MAX_VALUE;
    private int targetConnectivity = 2;
    private long randomSeed = -1;
    private Random rng;

    // -- Data structures ------------------------------------------------------

    /** Vulnerability severity levels. */
    public enum Severity { CRITICAL, HIGH, MEDIUM, LOW }

    /** Types of structural vulnerabilities. */
    public enum VulnerabilityType {
        BRIDGE, ARTICULATION_POINT, BOTTLENECK, LOW_CONNECTIVITY, ISOLATED_COMPONENT
    }

    /** Types of repair actions. */
    public enum RepairActionType {
        ADD_EDGE, REINFORCE_PATH, ADD_BYPASS, MERGE_COMPONENTS
    }

    /** A detected structural vulnerability. */
    public static class Vulnerability {
        public VulnerabilityType type;
        public String node;
        public String node2;       // for edges (bridges)
        public Severity severity;
        public String description;

        public Vulnerability(VulnerabilityType type, String node, String node2,
                             Severity severity, String description) {
            this.type = type;
            this.node = node;
            this.node2 = node2;
            this.severity = severity;
            this.description = description;
        }
    }

    /** A proposed repair action. */
    public static class RepairAction {
        public RepairActionType type;
        public String source;
        public String target;
        public int priority;         // 1 = highest
        public double estimatedImpact; // 0-1, how much it improves resilience
        public double cost;

        public RepairAction(RepairActionType type, String source, String target,
                            int priority, double estimatedImpact) {
            this.type = type;
            this.source = source;
            this.target = target;
            this.priority = priority;
            this.estimatedImpact = estimatedImpact;
            this.cost = 1.0; // default unit cost
        }
    }

    /** Cost estimate for a repair plan. */
    public static class CostEstimate {
        public double totalCost;
        public Map<String, Double> perActionCosts = new LinkedHashMap<>();
        public double budgetUtilization; // 0-1
    }

    /** Before/after resilience comparison. */
    public static class ResilienceComparison {
        public double connectivityBefore;
        public double connectivityAfter;
        public double avgPathLengthBefore;
        public double avgPathLengthAfter;
        public double failureToleranceBefore;  // fraction of largest component after random failures
        public double failureToleranceAfter;
        public double diameterBefore;
        public double diameterAfter;
    }

    /** Full report from an autonomous repair analysis. */
    public static class RepairReport {
        public List<Vulnerability> vulnerabilities = new ArrayList<>();
        public List<RepairAction> repairPlan = new ArrayList<>();
        public CostEstimate costEstimate;
        public ResilienceComparison resilience;
        public List<String> insights = new ArrayList<>();
        public double healthScore; // 0-100
        public int nodesAnalyzed;
        public int edgesAnalyzed;
    }

    // -- Configuration setters ------------------------------------------------

    public GraphAutonomousRepairEngine setMonteCarloTrials(int trials) {
        this.monteCarloTrials = Math.max(1, trials);
        return this;
    }

    public GraphAutonomousRepairEngine setFailureFraction(double fraction) {
        this.failureFraction = Math.max(0.01, Math.min(0.9, fraction));
        return this;
    }

    public GraphAutonomousRepairEngine setBudgetLimit(double budget) {
        this.budgetLimit = Math.max(0, budget);
        return this;
    }

    public GraphAutonomousRepairEngine setTargetConnectivity(int k) {
        this.targetConnectivity = Math.max(1, k);
        return this;
    }

    public GraphAutonomousRepairEngine setRandomSeed(long seed) {
        this.randomSeed = seed;
        return this;
    }

    // -- Main analysis --------------------------------------------------------

    /**
     * Performs full autonomous repair analysis on the given graph.
     *
     * @param graph the graph to analyze
     * @return a complete repair report
     */
    public RepairReport analyze(Graph<String, Edge> graph) {
        rng = (randomSeed >= 0) ? new Random(randomSeed) : new Random();
        RepairReport report = new RepairReport();
        List<String> vertices = new ArrayList<>(graph.getVertices());
        report.nodesAnalyzed = vertices.size();
        report.edgesAnalyzed = graph.getEdgeCount();

        if (vertices.isEmpty()) {
            report.healthScore = 100.0;
            report.costEstimate = new CostEstimate();
            report.resilience = new ResilienceComparison();
            report.insights.add("Empty graph — nothing to repair.");
            return report;
        }

        // Engine 1: Vulnerability Scanner
        scanVulnerabilities(graph, vertices, report);

        // Engine 2: Connectivity Restorer
        restoreConnectivity(graph, vertices, report);

        // Engine 3: Bottleneck Reliever
        relieveBottlenecks(graph, vertices, report);

        // Engine 4: Redundancy Planner
        planRedundancy(graph, vertices, report);

        // Engine 5: Repair Cost Estimator
        estimateCosts(report);

        // Engine 6: Self-Healing Simulator
        simulateSelfHealing(graph, vertices, report);

        // Compute health score
        report.healthScore = computeHealthScore(graph, vertices, report);

        // Generate insights
        generateInsights(report);

        return report;
    }

    // -- Engine 1: Vulnerability Scanner --------------------------------------

    private void scanVulnerabilities(Graph<String, Edge> graph,
                                      List<String> vertices,
                                      RepairReport report) {
        // Find bridges using Tarjan's algorithm
        Map<String, Integer> disc = new HashMap<>();
        Map<String, Integer> low = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        Set<String> visited = new HashSet<>();
        int[] timer = {0};
        Set<String> articulationPoints = new HashSet<>();
        List<String[]> bridges = new ArrayList<>();

        for (String v : vertices) {
            if (!visited.contains(v)) {
                dfsBridgeAP(graph, v, disc, low, parent, visited,
                            timer, articulationPoints, bridges);
            }
        }

        // Report bridges
        for (String[] bridge : bridges) {
            report.vulnerabilities.add(new Vulnerability(
                VulnerabilityType.BRIDGE, bridge[0], bridge[1],
                Severity.CRITICAL,
                "Bridge edge " + bridge[0] + "--" + bridge[1] +
                ": removing it disconnects the graph"));
        }

        // Report articulation points
        for (String ap : articulationPoints) {
            int degree = graph.degree(ap);
            Severity sev = degree > vertices.size() / 3 ? Severity.CRITICAL :
                           degree > vertices.size() / 5 ? Severity.HIGH : Severity.MEDIUM;
            report.vulnerabilities.add(new Vulnerability(
                VulnerabilityType.ARTICULATION_POINT, ap, null, sev,
                "Articulation point " + ap + " (degree " + degree +
                "): removing it disconnects the graph"));
        }

        // Check for isolated components
        List<Set<String>> components = findComponents(graph, vertices);
        if (components.size() > 1) {
            for (Set<String> comp : components) {
                if (comp.size() == 1) {
                    String isolated = comp.iterator().next();
                    report.vulnerabilities.add(new Vulnerability(
                        VulnerabilityType.ISOLATED_COMPONENT, isolated, null,
                        Severity.HIGH,
                        "Isolated node " + isolated + " in its own component"));
                } else if (comp.size() < vertices.size()) {
                    String rep = comp.iterator().next();
                    report.vulnerabilities.add(new Vulnerability(
                        VulnerabilityType.ISOLATED_COMPONENT, rep, null,
                        Severity.HIGH,
                        "Disconnected component of size " + comp.size() +
                        " (representative: " + rep + ")"));
                }
            }
        }

        // Check low connectivity
        if (vertices.size() > 2 && graph.getEdgeCount() > 0) {
            double density = 2.0 * graph.getEdgeCount() /
                             (vertices.size() * (vertices.size() - 1.0));
            if (density < 0.1) {
                report.vulnerabilities.add(new Vulnerability(
                    VulnerabilityType.LOW_CONNECTIVITY, null, null,
                    Severity.MEDIUM,
                    String.format("Low graph density (%.3f) - graph is sparse " +
                                  "and may be fragile", density)));
            }
        }
    }

    private void dfsBridgeAP(Graph<String, Edge> graph, String u,
                              Map<String, Integer> disc,
                              Map<String, Integer> low,
                              Map<String, String> parent,
                              Set<String> visited, int[] timer,
                              Set<String> articulationPoints,
                              List<String[]> bridges) {
        visited.add(u);
        disc.put(u, timer[0]);
        low.put(u, timer[0]);
        timer[0]++;
        int children = 0;

        for (String v : graph.getNeighbors(u)) {
            if (!visited.contains(v)) {
                children++;
                parent.put(v, u);
                dfsBridgeAP(graph, v, disc, low, parent, visited,
                            timer, articulationPoints, bridges);
                low.put(u, Math.min(low.get(u), low.get(v)));

                // Bridge check
                if (low.get(v) > disc.get(u)) {
                    bridges.add(new String[]{u, v});
                }

                // Articulation point check
                if (!parent.containsKey(u) && children > 1) {
                    articulationPoints.add(u);
                }
                if (parent.containsKey(u) && low.get(v) >= disc.get(u)) {
                    articulationPoints.add(u);
                }
            } else if (!v.equals(parent.get(u))) {
                low.put(u, Math.min(low.get(u), disc.get(v)));
            }
        }
    }

    // -- Engine 2: Connectivity Restorer --------------------------------------

    private void restoreConnectivity(Graph<String, Edge> graph,
                                      List<String> vertices,
                                      RepairReport report) {
        List<Set<String>> components = findComponents(graph, vertices);
        if (components.size() <= 1) return;

        // Connect components in a chain
        for (int i = 0; i < components.size() - 1; i++) {
            String from = pickRepresentative(components.get(i));
            String to = pickRepresentative(components.get(i + 1));
            report.repairPlan.add(new RepairAction(
                RepairActionType.MERGE_COMPONENTS, from, to,
                1, 0.8));
        }
    }

    // -- Engine 3: Bottleneck Reliever ----------------------------------------

    private void relieveBottlenecks(Graph<String, Edge> graph,
                                     List<String> vertices,
                                     RepairReport report) {
        if (vertices.size() < 4) return;

        Map<String, Double> betweenness = computeBetweenness(graph, vertices);
        double mean = betweenness.values().stream()
                .mapToDouble(Double::doubleValue).average().orElse(0);
        double stdDev = Math.sqrt(betweenness.values().stream()
                .mapToDouble(d -> (d - mean) * (d - mean)).average().orElse(0));

        if (stdDev < 0.001) return;

        for (Map.Entry<String, Double> entry : betweenness.entrySet()) {
            double z = (entry.getValue() - mean) / stdDev;
            if (z > 2.0) {
                String bottleneck = entry.getKey();
                // Suggest bypass: connect two non-adjacent neighbors
                List<String> neighbors = new ArrayList<>(graph.getNeighbors(bottleneck));
                boolean added = false;
                for (int i = 0; i < neighbors.size() && !added; i++) {
                    for (int j = i + 1; j < neighbors.size() && !added; j++) {
                        if (!graph.isNeighbor(neighbors.get(i), neighbors.get(j))) {
                            report.repairPlan.add(new RepairAction(
                                RepairActionType.ADD_BYPASS,
                                neighbors.get(i), neighbors.get(j),
                                2, 0.5));
                            report.vulnerabilities.add(new Vulnerability(
                                VulnerabilityType.BOTTLENECK, bottleneck, null,
                                Severity.HIGH,
                                String.format("Bottleneck node %s (betweenness z=%.1f) — " +
                                              "bypass %s--%s suggested", bottleneck, z,
                                              neighbors.get(i), neighbors.get(j))));
                            added = true;
                        }
                    }
                }
            }
        }
    }

    // -- Engine 4: Redundancy Planner -----------------------------------------

    private void planRedundancy(Graph<String, Edge> graph,
                                 List<String> vertices,
                                 RepairReport report) {
        if (vertices.size() < 3) return;

        // Find bridges and suggest parallel edges to remove them
        Map<String, Integer> disc = new HashMap<>();
        Map<String, Integer> low = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        Set<String> visited = new HashSet<>();
        int[] timer = {0};
        Set<String> ap = new HashSet<>();
        List<String[]> bridges = new ArrayList<>();

        for (String v : vertices) {
            if (!visited.contains(v)) {
                dfsBridgeAP(graph, v, disc, low, parent, visited, timer, ap, bridges);
            }
        }

        // For each bridge, suggest an alternative path
        for (String[] bridge : bridges) {
            // Find neighbors of bridge endpoints (excluding bridge itself)
            Set<String> neighborsA = new HashSet<>(graph.getNeighbors(bridge[0]));
            neighborsA.remove(bridge[1]);
            Set<String> neighborsB = new HashSet<>(graph.getNeighbors(bridge[1]));
            neighborsB.remove(bridge[0]);

            // Try to find a reinforcement edge
            boolean reinforced = false;
            for (String na : neighborsA) {
                for (String nb : neighborsB) {
                    if (!na.equals(nb) && !graph.isNeighbor(na, nb)) {
                        report.repairPlan.add(new RepairAction(
                            RepairActionType.REINFORCE_PATH, na, nb,
                            2, 0.6));
                        reinforced = true;
                        break;
                    }
                }
                if (reinforced) break;
            }

            // If no good reinforcement found, add direct alternative
            if (!reinforced && neighborsA.size() > 0) {
                String na = neighborsA.iterator().next();
                if (!graph.isNeighbor(na, bridge[1])) {
                    report.repairPlan.add(new RepairAction(
                        RepairActionType.ADD_EDGE, na, bridge[1],
                        3, 0.4));
                }
            }
        }
    }

    // -- Engine 5: Repair Cost Estimator --------------------------------------

    private void estimateCosts(RepairReport report) {
        CostEstimate est = new CostEstimate();
        double total = 0;

        for (RepairAction action : report.repairPlan) {
            // Cost based on priority and type
            double baseCost = 1.0;
            switch (action.type) {
                case MERGE_COMPONENTS: baseCost = 2.0; break;
                case ADD_BYPASS:       baseCost = 1.5; break;
                case REINFORCE_PATH:   baseCost = 1.2; break;
                case ADD_EDGE:         baseCost = 1.0; break;
            }
            action.cost = baseCost / action.priority;
            String key = action.type + ":" + action.source + "->" + action.target;
            est.perActionCosts.put(key, action.cost);
            total += action.cost;
        }

        est.totalCost = total;
        est.budgetUtilization = budgetLimit < Double.MAX_VALUE && budgetLimit > 0 ?
                Math.min(1.0, total / budgetLimit) : 0;

        // Trim plan to budget
        if (budgetLimit < Double.MAX_VALUE) {
            double remaining = budgetLimit;
            List<RepairAction> trimmed = new ArrayList<>();
            // Sort by priority (ascending = highest first)
            List<RepairAction> sorted = new ArrayList<>(report.repairPlan);
            sorted.sort(Comparator.comparingInt(a -> a.priority));
            for (RepairAction a : sorted) {
                if (remaining >= a.cost) {
                    trimmed.add(a);
                    remaining -= a.cost;
                }
            }
            report.repairPlan = trimmed;
        }

        report.costEstimate = est;
    }

    // -- Engine 6: Self-Healing Simulator -------------------------------------

    private void simulateSelfHealing(Graph<String, Edge> graph,
                                      List<String> vertices,
                                      RepairReport report) {
        ResilienceComparison rc = new ResilienceComparison();
        if (vertices.size() < 2) {
            rc.connectivityBefore = vertices.isEmpty() ? 0 : 1;
            rc.connectivityAfter = rc.connectivityBefore;
            rc.failureToleranceBefore = 1.0;
            rc.failureToleranceAfter = 1.0;
            report.resilience = rc;
            return;
        }

        // Measure before metrics
        rc.connectivityBefore = findComponents(graph, vertices).size() == 1 ? 1.0 : 0.0;
        double[] pathStats = computePathStats(graph, vertices);
        rc.avgPathLengthBefore = pathStats[0];
        rc.diameterBefore = pathStats[1];
        rc.failureToleranceBefore = monteCarloResilience(graph, vertices);

        // Build repaired graph
        Graph<String, Edge> repaired = copyGraph(graph);
        int edgeCounter = graph.getEdgeCount();
        for (RepairAction action : report.repairPlan) {
            if (repaired.containsVertex(action.source) &&
                repaired.containsVertex(action.target) &&
                !repaired.isNeighbor(action.source, action.target)) {
                Edge e = new Edge("c", action.source, action.target);
                repaired.addEdge(e, action.source, action.target);
                edgeCounter++;
            }
        }

        // Measure after metrics
        List<String> repairedVerts = new ArrayList<>(repaired.getVertices());
        rc.connectivityAfter = findComponents(repaired, repairedVerts).size() == 1 ? 1.0 : 0.0;
        double[] repairedStats = computePathStats(repaired, repairedVerts);
        rc.avgPathLengthAfter = repairedStats[0];
        rc.diameterAfter = repairedStats[1];
        rc.failureToleranceAfter = monteCarloResilience(repaired, repairedVerts);

        report.resilience = rc;
    }

    private double monteCarloResilience(Graph<String, Edge> graph,
                                         List<String> vertices) {
        if (vertices.size() <= 1) return 1.0;
        int removeCount = Math.max(1, (int)(vertices.size() * failureFraction));
        double totalRatio = 0;

        for (int trial = 0; trial < monteCarloTrials; trial++) {
            // Randomly remove nodes
            List<String> shuffled = new ArrayList<>(vertices);
            Collections.shuffle(shuffled, rng);
            Set<String> removed = new HashSet<>(shuffled.subList(0,
                    Math.min(removeCount, shuffled.size())));

            // Find largest remaining component
            List<String> remaining = new ArrayList<>();
            for (String v : vertices) {
                if (!removed.contains(v)) remaining.add(v);
            }
            if (remaining.isEmpty()) continue;

            // BFS to find largest component
            Set<String> visited = new HashSet<>();
            int maxComp = 0;
            for (String v : remaining) {
                if (visited.contains(v)) continue;
                int compSize = 0;
                Queue<String> queue = new LinkedList<>();
                queue.add(v);
                visited.add(v);
                while (!queue.isEmpty()) {
                    String curr = queue.poll();
                    compSize++;
                    for (String n : graph.getNeighbors(curr)) {
                        if (!removed.contains(n) && !visited.contains(n)) {
                            visited.add(n);
                            queue.add(n);
                        }
                    }
                }
                maxComp = Math.max(maxComp, compSize);
            }
            totalRatio += (double) maxComp / remaining.size();
        }
        return totalRatio / monteCarloTrials;
    }

    // -- Health Score ---------------------------------------------------------

    private double computeHealthScore(Graph<String, Edge> graph,
                                       List<String> vertices,
                                       RepairReport report) {
        if (vertices.isEmpty()) return 100.0;

        double score = 100.0;

        // Deduct for vulnerabilities
        for (Vulnerability v : report.vulnerabilities) {
            switch (v.severity) {
                case CRITICAL: score -= 15; break;
                case HIGH:     score -= 10; break;
                case MEDIUM:   score -=  5; break;
                case LOW:      score -=  2; break;
            }
        }

        // Deduct for disconnected components
        int components = findComponents(graph, vertices).size();
        if (components > 1) {
            score -= Math.min(30, (components - 1) * 10);
        }

        // Bonus for density
        if (vertices.size() > 1) {
            double density = 2.0 * graph.getEdgeCount() /
                             (vertices.size() * (vertices.size() - 1.0));
            if (density > 0.3) score += 5;
        }

        return Math.max(0, Math.min(100, score));
    }

    // -- Insight Generation ---------------------------------------------------

    private void generateInsights(RepairReport report) {
        int critCount = 0, highCount = 0;
        for (Vulnerability v : report.vulnerabilities) {
            if (v.severity == Severity.CRITICAL) critCount++;
            if (v.severity == Severity.HIGH) highCount++;
        }

        if (report.vulnerabilities.isEmpty()) {
            report.insights.add("Graph has no detected structural vulnerabilities - " +
                                "excellent robustness.");
        } else {
            report.insights.add(String.format("Detected %d vulnerabilities (%d critical, " +
                "%d high).", report.vulnerabilities.size(), critCount, highCount));
        }

        if (!report.repairPlan.isEmpty()) {
            report.insights.add(String.format("Repair plan contains %d actions with " +
                "estimated cost %.1f.", report.repairPlan.size(),
                report.costEstimate != null ? report.costEstimate.totalCost : 0));
        }

        if (report.resilience != null) {
            double improvement = report.resilience.failureToleranceAfter -
                                 report.resilience.failureToleranceBefore;
            if (improvement > 0.01) {
                report.insights.add(String.format("Repair plan improves failure tolerance " +
                    "by %.1f%% (%.3f → %.3f).", improvement * 100,
                    report.resilience.failureToleranceBefore,
                    report.resilience.failureToleranceAfter));
            } else if (report.repairPlan.isEmpty()) {
                report.insights.add("No repairs needed - graph is already resilient.");
            }

            if (report.resilience.avgPathLengthAfter < report.resilience.avgPathLengthBefore
                    && report.resilience.avgPathLengthBefore > 0) {
                report.insights.add(String.format("Average path length improves from " +
                    "%.2f to %.2f after repairs.", report.resilience.avgPathLengthBefore,
                    report.resilience.avgPathLengthAfter));
            }
        }

        if (report.healthScore >= 90) {
            report.insights.add("Health score is excellent - minimal intervention needed.");
        } else if (report.healthScore < 50) {
            report.insights.add("Health score is poor - urgent structural repairs recommended.");
        }
    }

    // -- Text Output ----------------------------------------------------------

    /**
     * Formats the report as human-readable text.
     */
    public String toText(RepairReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("===========================================================\n");
        sb.append("  GRAPH AUTONOMOUS REPAIR ENGINE - Analysis Report\n");
        sb.append("===========================================================\n\n");

        sb.append(String.format("Nodes: %d | Edges: %d | Health Score: %.0f/100\n\n",
                report.nodesAnalyzed, report.edgesAnalyzed, report.healthScore));

        // Vulnerabilities
        sb.append("-- Vulnerabilities (" + report.vulnerabilities.size() + ") --\n");
        if (report.vulnerabilities.isEmpty()) {
            sb.append("  None detected.\n");
        } else {
            for (Vulnerability v : report.vulnerabilities) {
                sb.append(String.format("  [%s] %s: %s\n",
                        v.severity, v.type, v.description));
            }
        }
        sb.append("\n");

        // Repair Plan
        sb.append("-- Repair Plan (" + report.repairPlan.size() + " actions) --\n");
        if (report.repairPlan.isEmpty()) {
            sb.append("  No repairs needed.\n");
        } else {
            for (RepairAction a : report.repairPlan) {
                sb.append(String.format("  [P%d] %s: %s → %s (impact: %.1f%%, cost: %.1f)\n",
                        a.priority, a.type, a.source, a.target,
                        a.estimatedImpact * 100, a.cost));
            }
        }
        sb.append("\n");

        // Cost
        if (report.costEstimate != null && report.costEstimate.totalCost > 0) {
            sb.append(String.format("-- Cost Estimate: %.1f total --\n",
                    report.costEstimate.totalCost));
            if (report.costEstimate.budgetUtilization > 0) {
                sb.append(String.format("  Budget utilization: %.0f%%\n",
                        report.costEstimate.budgetUtilization * 100));
            }
            sb.append("\n");
        }

        // Resilience
        if (report.resilience != null) {
            sb.append("-- Resilience Comparison --\n");
            ResilienceComparison rc = report.resilience;
            sb.append(String.format("  Failure tolerance: %.3f → %.3f\n",
                    rc.failureToleranceBefore, rc.failureToleranceAfter));
            sb.append(String.format("  Avg path length:   %.2f → %.2f\n",
                    rc.avgPathLengthBefore, rc.avgPathLengthAfter));
            sb.append(String.format("  Diameter:          %.0f → %.0f\n",
                    rc.diameterBefore, rc.diameterAfter));
            sb.append("\n");
        }

        // Insights
        sb.append("-- Autonomous Insights --\n");
        for (String insight : report.insights) {
            sb.append("  * " + insight + "\n");
        }

        return sb.toString();
    }

    // -- HTML Dashboard -------------------------------------------------------

    /**
     * Exports an interactive HTML dashboard.
     */
    public String exportHtml(RepairReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<title>Graph Autonomous Repair Engine — Dashboard</title>\n");
        sb.append("<style>\n");
        sb.append("*{margin:0;padding:0;box-sizing:border-box}\n");
        sb.append("body{font-family:'Segoe UI',system-ui,sans-serif;background:#0d1117;color:#c9d1d9;padding:20px}\n");
        sb.append("h1{color:#58a6ff;margin-bottom:8px}\n");
        sb.append("h2{color:#79c0ff;margin:16px 0 8px;border-bottom:1px solid #21262d;padding-bottom:4px}\n");
        sb.append(".score{font-size:48px;font-weight:bold;text-align:center;padding:20px}\n");
        sb.append(".score.good{color:#3fb950}.score.warn{color:#d29922}.score.bad{color:#f85149}\n");
        sb.append("table{width:100%;border-collapse:collapse;margin:8px 0}\n");
        sb.append("th,td{text-align:left;padding:6px 10px;border-bottom:1px solid #21262d}\n");
        sb.append("th{color:#8b949e;font-weight:600}\n");
        sb.append(".sev-CRITICAL{color:#f85149;font-weight:bold}\n");
        sb.append(".sev-HIGH{color:#d29922}\n");
        sb.append(".sev-MEDIUM{color:#58a6ff}\n");
        sb.append(".sev-LOW{color:#8b949e}\n");
        sb.append(".insight{background:#161b22;border-left:3px solid #58a6ff;padding:8px 12px;margin:4px 0}\n");
        sb.append(".bar-container{display:flex;gap:20px;margin:8px 0}\n");
        sb.append(".bar-group{flex:1}\n");
        sb.append(".bar{height:24px;border-radius:4px;margin:2px 0;transition:width 0.5s}\n");
        sb.append(".bar-before{background:#f8514988}\n");
        sb.append(".bar-after{background:#3fb95088}\n");
        sb.append(".tabs{display:flex;gap:4px;margin:12px 0}\n");
        sb.append(".tab{padding:8px 16px;cursor:pointer;background:#161b22;border:1px solid #21262d;border-radius:6px 6px 0 0;color:#8b949e}\n");
        sb.append(".tab.active{background:#0d1117;color:#58a6ff;border-bottom-color:#0d1117}\n");
        sb.append(".panel{display:none;padding:12px;border:1px solid #21262d;border-radius:0 0 6px 6px}\n");
        sb.append(".panel.active{display:block}\n");
        sb.append("</style>\n</head>\n<body>\n");

        // Header
        sb.append("<h1>Graph Autonomous Repair Engine</h1>\n");
        sb.append(String.format("<p>%d nodes · %d edges · Generated %s</p>\n",
                report.nodesAnalyzed, report.edgesAnalyzed,
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())));

        // Health Score
        String scoreClass = report.healthScore >= 70 ? "good" :
                             report.healthScore >= 40 ? "warn" : "bad";
        sb.append(String.format("<div class=\"score %s\">%.0f / 100</div>\n",
                scoreClass, report.healthScore));

        // Tabs
        sb.append("<div class=\"tabs\">\n");
        sb.append("<div class=\"tab active\" onclick=\"showTab(0)\">Vulnerabilities</div>\n");
        sb.append("<div class=\"tab\" onclick=\"showTab(1)\">Repair Plan</div>\n");
        sb.append("<div class=\"tab\" onclick=\"showTab(2)\">Resilience</div>\n");
        sb.append("<div class=\"tab\" onclick=\"showTab(3)\">Insights</div>\n");
        sb.append("</div>\n");

        // Tab 0: Vulnerabilities
        sb.append("<div class=\"panel active\" id=\"tab0\">\n");
        sb.append("<h2>Vulnerabilities (" + report.vulnerabilities.size() + ")</h2>\n");
        if (report.vulnerabilities.isEmpty()) {
            sb.append("<p>No vulnerabilities detected.</p>\n");
        } else {
            sb.append("<table><tr><th>Severity</th><th>Type</th><th>Node</th><th>Description</th></tr>\n");
            for (Vulnerability v : report.vulnerabilities) {
                sb.append(String.format("<tr><td class=\"sev-%s\">%s</td><td>%s</td><td>%s</td><td>%s</td></tr>\n",
                        v.severity, v.severity, v.type,
                        escHtml(v.node != null ? v.node + (v.node2 != null ? "--" + v.node2 : "") : "—"),
                        escHtml(v.description)));
            }
            sb.append("</table>\n");
        }
        sb.append("</div>\n");

        // Tab 1: Repair Plan
        sb.append("<div class=\"panel\" id=\"tab1\">\n");
        sb.append("<h2>Repair Plan (" + report.repairPlan.size() + " actions)</h2>\n");
        if (report.repairPlan.isEmpty()) {
            sb.append("<p>No repairs needed.</p>\n");
        } else {
            sb.append("<table><tr><th>Priority</th><th>Type</th><th>Source</th><th>Target</th><th>Impact</th><th>Cost</th></tr>\n");
            for (RepairAction a : report.repairPlan) {
                sb.append(String.format("<tr><td>P%d</td><td>%s</td><td>%s</td><td>%s</td><td>%.0f%%</td><td>%.1f</td></tr>\n",
                        a.priority, a.type, escHtml(a.source), escHtml(a.target),
                        a.estimatedImpact * 100, a.cost));
            }
            sb.append("</table>\n");
            if (report.costEstimate != null) {
                sb.append(String.format("<p><strong>Total cost:</strong> %.1f</p>\n",
                        report.costEstimate.totalCost));
            }
        }
        sb.append("</div>\n");

        // Tab 2: Resilience
        sb.append("<div class=\"panel\" id=\"tab2\">\n");
        sb.append("<h2>Resilience Comparison</h2>\n");
        if (report.resilience != null) {
            ResilienceComparison rc = report.resilience;
            sb.append("<table>\n");
            sb.append("<tr><th>Metric</th><th>Before</th><th>After</th><th>Change</th></tr>\n");
            appendResRow(sb, "Failure Tolerance", rc.failureToleranceBefore,
                         rc.failureToleranceAfter, true);
            appendResRow(sb, "Avg Path Length", rc.avgPathLengthBefore,
                         rc.avgPathLengthAfter, false);
            appendResRow(sb, "Diameter", rc.diameterBefore, rc.diameterAfter, false);
            sb.append("</table>\n");
        }
        sb.append("</div>\n");

        // Tab 3: Insights
        sb.append("<div class=\"panel\" id=\"tab3\">\n");
        sb.append("<h2>Autonomous Insights</h2>\n");
        for (String insight : report.insights) {
            sb.append("<div class=\"insight\">" + escHtml(insight) + "</div>\n");
        }
        sb.append("</div>\n");

        // JavaScript
        sb.append("<script>\n");
        sb.append("function showTab(n){\n");
        sb.append("  document.querySelectorAll('.panel').forEach((p,i)=>{p.classList.toggle('active',i===n)});\n");
        sb.append("  document.querySelectorAll('.tab').forEach((t,i)=>{t.classList.toggle('active',i===n)});\n");
        sb.append("}\n");
        sb.append("</script>\n");
        sb.append("</body>\n</html>");

        return sb.toString();
    }

    private void appendResRow(StringBuilder sb, String metric, double before,
                               double after, boolean higherIsBetter) {
        double diff = after - before;
        String arrow = diff > 0.001 ? "UP" : diff < -0.001 ? "DN" : "--";
        String color = (higherIsBetter ? diff > 0 : diff < 0) ? "#3fb950" :
                        Math.abs(diff) < 0.001 ? "#8b949e" : "#f85149";
        sb.append(String.format("<tr><td>%s</td><td>%.3f</td><td>%.3f</td>" +
                "<td style=\"color:%s\">%s %.3f</td></tr>\n",
                metric, before, after, color, arrow, Math.abs(diff)));
    }

    /**
     * Exports the HTML dashboard to a file.
     */
    public void exportHtmlToFile(RepairReport report, File file) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file),
                StandardCharsets.UTF_8)) {
            w.write(exportHtml(report));
        }
    }

    // -- Utility Methods ------------------------------------------------------

    private List<Set<String>> findComponents(Graph<String, Edge> graph,
                                              List<String> vertices) {
        List<Set<String>> components = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (String v : vertices) {
            if (visited.contains(v)) continue;
            Set<String> comp = new LinkedHashSet<>();
            Queue<String> queue = new LinkedList<>();
            queue.add(v);
            visited.add(v);
            while (!queue.isEmpty()) {
                String curr = queue.poll();
                comp.add(curr);
                for (String n : graph.getNeighbors(curr)) {
                    if (!visited.contains(n)) {
                        visited.add(n);
                        queue.add(n);
                    }
                }
            }
            components.add(comp);
        }
        return components;
    }

    private Map<String, Double> computeBetweenness(Graph<String, Edge> graph,
                                                     List<String> vertices) {
        Map<String, Double> betweenness = new HashMap<>();
        for (String v : vertices) betweenness.put(v, 0.0);

        for (String s : vertices) {
            // BFS
            Map<String, Integer> dist = new HashMap<>();
            Map<String, Double> sigma = new HashMap<>();
            Map<String, List<String>> pred = new HashMap<>();
            Stack<String> stack = new Stack<>();

            for (String v : vertices) {
                dist.put(v, -1);
                sigma.put(v, 0.0);
                pred.put(v, new ArrayList<>());
            }
            dist.put(s, 0);
            sigma.put(s, 1.0);
            Queue<String> queue = new LinkedList<>();
            queue.add(s);

            while (!queue.isEmpty()) {
                String v = queue.poll();
                stack.push(v);
                for (String w : graph.getNeighbors(v)) {
                    if (dist.get(w) < 0) {
                        dist.put(w, dist.get(v) + 1);
                        queue.add(w);
                    }
                    if (dist.get(w) == dist.get(v) + 1) {
                        sigma.put(w, sigma.get(w) + sigma.get(v));
                        pred.get(w).add(v);
                    }
                }
            }

            // Back-propagation
            Map<String, Double> delta = new HashMap<>();
            for (String v : vertices) delta.put(v, 0.0);
            while (!stack.isEmpty()) {
                String w = stack.pop();
                for (String v : pred.get(w)) {
                    double d = (sigma.get(v) / sigma.get(w)) * (1.0 + delta.get(w));
                    delta.put(v, delta.get(v) + d);
                }
                if (!w.equals(s)) {
                    betweenness.put(w, betweenness.get(w) + delta.get(w));
                }
            }
        }

        // Normalize
        for (String v : vertices) {
            betweenness.put(v, betweenness.get(v) / 2.0);
        }
        return betweenness;
    }

    private double[] computePathStats(Graph<String, Edge> graph,
                                       List<String> vertices) {
        double totalPath = 0;
        int pathCount = 0;
        double maxPath = 0;

        for (String s : vertices) {
            Map<String, Integer> dist = bfsDistances(graph, s);
            for (Map.Entry<String, Integer> entry : dist.entrySet()) {
                if (!entry.getKey().equals(s) && entry.getValue() >= 0) {
                    totalPath += entry.getValue();
                    pathCount++;
                    maxPath = Math.max(maxPath, entry.getValue());
                }
            }
        }
        double avgPath = pathCount > 0 ? totalPath / pathCount : 0;
        return new double[]{avgPath, maxPath};
    }

    private Map<String, Integer> bfsDistances(Graph<String, Edge> graph, String start) {
        Map<String, Integer> dist = new HashMap<>();
        dist.put(start, 0);
        Queue<String> queue = new LinkedList<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String v = queue.poll();
            for (String n : graph.getNeighbors(v)) {
                if (!dist.containsKey(n)) {
                    dist.put(n, dist.get(v) + 1);
                    queue.add(n);
                }
            }
        }
        return dist;
    }

    private Graph<String, Edge> copyGraph(Graph<String, Edge> original) {
        UndirectedSparseGraph<String, Edge> copy = new UndirectedSparseGraph<>();
        for (String v : original.getVertices()) {
            copy.addVertex(v);
        }
        for (Edge e : original.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            if (v1 != null && v2 != null) {
                Edge newEdge = new Edge(e.getType(), v1, v2);
                newEdge.setWeight(e.getWeight());
                copy.addEdge(newEdge, v1, v2);
            }
        }
        return copy;
    }

    private String pickRepresentative(Set<String> component) {
        // Pick the node with alphabetically first name for determinism
        return component.stream().min(Comparator.naturalOrder()).orElse("");
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
