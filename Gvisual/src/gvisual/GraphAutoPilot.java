package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GraphAutoPilot — autonomous graph optimization planner that analyzes
 * structural weaknesses, generates a prioritized action plan of specific
 * graph modifications, and simulates the impact of each proposed change.
 *
 * <h3>How it works:</h3>
 * <ol>
 *   <li><b>Diagnose</b> — scan for 8 structural weakness categories</li>
 *   <li><b>Plan</b> — generate ranked remediation actions with expected impact</li>
 *   <li><b>Simulate</b> — apply each action to a clone and measure improvement</li>
 *   <li><b>Report</b> — produce text/HTML with before/after metrics</li>
 * </ol>
 *
 * <h3>Weakness categories:</h3>
 * <ul>
 *   <li>Bridge edges (single points of failure)</li>
 *   <li>Articulation points (critical nodes whose removal disconnects the graph)</li>
 *   <li>Degree imbalance (hub-spoke patterns creating fragility)</li>
 *   <li>Low connectivity (components reachable by single path)</li>
 *   <li>Peripheral isolates (near-isolated nodes with degree 1)</li>
 *   <li>Dense clique bottlenecks (tightly connected clusters with weak bridges)</li>
 *   <li>Diameter hotspots (node pairs requiring excessively long paths)</li>
 *   <li>Community fragmentation (poorly connected sub-communities)</li>
 * </ul>
 *
 * <h3>Action types:</h3>
 * <ul>
 *   <li>{@code ADD_EDGE} — add a new edge between two existing nodes</li>
 *   <li>{@code ADD_BYPASS} — add edge to create alternative path around a bridge</li>
 *   <li>{@code REINFORCE_HUB} — add edges to distribute load from an overloaded hub</li>
 *   <li>{@code CONNECT_PERIPHERAL} — connect an isolated/pendant node to a second neighbor</li>
 *   <li>{@code BRIDGE_COMMUNITIES} — add inter-community edge</li>
 * </ul>
 *
 * <h3>Agentic behavior:</h3>
 * <ul>
 *   <li><b>Goal-oriented</b> — user specifies optimization goal; system plans the steps</li>
 *   <li><b>Autonomous planning</b> — generates actions without being told what to fix</li>
 *   <li><b>Impact simulation</b> — predicts outcomes before recommending changes</li>
 *   <li><b>Prioritized</b> — ranks actions by impact-to-cost ratio</li>
 *   <li><b>Self-monitoring</b> — compares before/after metrics for each action</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 *   GraphAutoPilot pilot = new GraphAutoPilot(graph);
 *   pilot.analyze();
 *   String report = pilot.formatTextReport();
 *   pilot.exportHtml(new File("autopilot-report.html"));
 *   List&lt;Action&gt; plan = pilot.getActionPlan();
 *   // Apply top-N actions:
 *   Graph&lt;String, Edge&gt; optimized = pilot.applyTopActions(5);
 * </pre>
 *
 * @author zalenix
 */
public class GraphAutoPilot {

    // ── Action types ───────────────────────────────────────

    /** The type of optimization action. */
    public enum ActionType {
        ADD_BYPASS("Add bypass edge around bridge"),
        CONNECT_PERIPHERAL("Connect peripheral node to additional neighbor"),
        REINFORCE_HUB("Distribute load from overloaded hub"),
        BRIDGE_COMMUNITIES("Bridge weakly connected communities"),
        ADD_SHORTCUT("Add shortcut to reduce diameter");

        private final String description;

        ActionType(String description) { this.description = description; }

        public String getDescription() { return description; }
    }

    /** Priority level for an action. */
    public enum Priority {
        CRITICAL(4, "Critical"), HIGH(3, "High"), MEDIUM(2, "Medium"), LOW(1, "Low");

        private final int weight;
        private final String label;

        Priority(int weight, String label) {
            this.weight = weight;
            this.label = label;
        }

        public int getWeight() { return weight; }

        public String getLabel() { return label; }
    }

    /** A single proposed optimization action. */
    public static class Action implements Comparable<Action> {
        private final ActionType type;
        private final Priority priority;
        private final String description;
        private final String sourceNode;
        private final String targetNode;
        private final String reasoning;
        private double impactScore;       // 0-100
        private double simulatedImprovement; // delta in health score
        private Map<String, Double> beforeMetrics;
        private Map<String, Double> afterMetrics;

        public Action(ActionType type, Priority priority, String description,
                      String sourceNode, String targetNode, String reasoning) {
            this.type = type;
            this.priority = priority;
            this.description = description;
            this.sourceNode = sourceNode;
            this.targetNode = targetNode;
            this.reasoning = reasoning;
            this.impactScore = 0;
            this.simulatedImprovement = 0;
            this.beforeMetrics = new LinkedHashMap<>();
            this.afterMetrics = new LinkedHashMap<>();
        }

        public ActionType getType() { return type; }
        public Priority getPriority() { return priority; }
        public String getDescription() { return description; }
        public String getSourceNode() { return sourceNode; }
        public String getTargetNode() { return targetNode; }
        public String getReasoning() { return reasoning; }
        public double getImpactScore() { return impactScore; }
        public double getSimulatedImprovement() { return simulatedImprovement; }
        public Map<String, Double> getBeforeMetrics() { return beforeMetrics; }
        public Map<String, Double> getAfterMetrics() { return afterMetrics; }

        @Override
        public int compareTo(Action o) {
            // Higher impact first, then higher priority
            int cmp = Double.compare(o.impactScore, this.impactScore);
            return cmp != 0 ? cmp : Integer.compare(o.priority.weight, this.priority.weight);
        }
    }

    /** Weakness found during diagnosis. */
    public static class Weakness {
        private final String category;
        private final String description;
        private final Priority severity;
        private final List<String> involvedNodes;

        public Weakness(String category, String description, Priority severity,
                        List<String> involvedNodes) {
            this.category = category;
            this.description = description;
            this.severity = severity;
            this.involvedNodes = involvedNodes;
        }

        public String getCategory() { return category; }
        public String getDescription() { return description; }
        public Priority getSeverity() { return severity; }
        public List<String> getInvolvedNodes() { return involvedNodes; }
    }

    /** Before/after health metrics for the whole graph. */
    public static class HealthSnapshot {
        double bridgeCount;
        double articulationPointCount;
        double componentCount;
        double avgDegree;
        double maxDegree;
        double degreeGini;
        double avgClusteringCoeff;
        double diameter;
        double pendantCount;
        double healthScore;  // composite 0-100

        public Map<String, Double> toMap() {
            Map<String, Double> m = new LinkedHashMap<>();
            m.put("Bridges", bridgeCount);
            m.put("Articulation Points", articulationPointCount);
            m.put("Components", componentCount);
            m.put("Avg Degree", round2(avgDegree));
            m.put("Max Degree", maxDegree);
            m.put("Degree Gini", round2(degreeGini));
            m.put("Avg Clustering", round2(avgClusteringCoeff));
            m.put("Diameter", diameter);
            m.put("Pendant Nodes", pendantCount);
            m.put("Health Score", round2(healthScore));
            return m;
        }
    }

    // ── Fields ─────────────────────────────────────────────

    private static final int MAX_ACTIONS = 20;

    private final Graph<String, Edge> graph;
    private boolean analyzed;
    private HealthSnapshot beforeHealth;
    private List<Weakness> weaknesses;
    private List<Action> actionPlan;
    private Map<String, Set<String>> adjacency;

    /**
     * Creates a new GraphAutoPilot for the given graph.
     *
     * @param graph the JUNG graph to optimize (must not be null)
     */
    public GraphAutoPilot(Graph<String, Edge> graph) {
        this.graph = Objects.requireNonNull(graph, "graph must not be null");
        this.weaknesses = new ArrayList<>();
        this.actionPlan = new ArrayList<>();
        this.analyzed = false;
    }

    // ── Public API ─────────────────────────────────────────

    /**
     * Run the full analysis pipeline: diagnose → plan → simulate.
     */
    public void analyze() {
        if (graph.getVertexCount() < 2) {
            analyzed = true;
            beforeHealth = computeHealth(graph);
            return;
        }

        adjacency = buildAdjacency(graph);
        beforeHealth = computeHealth(graph);

        // Phase 1: Diagnose
        diagnoseBridges();
        diagnoseArticulationPoints();
        diagnoseDegreeImbalance();
        diagnosePeripherals();
        diagnoseCommunityFragmentation();
        diagnoseDiameter();

        // Phase 2: Plan
        planBridgeBypasses();
        planPeripheralConnections();
        planHubReinforcement();
        planCommunityBridging();
        planShortcuts();

        // Phase 3: Simulate each action
        for (Action a : actionPlan) {
            simulateAction(a);
        }

        // Sort by impact
        Collections.sort(actionPlan);

        // Cap at MAX_ACTIONS
        if (actionPlan.size() > MAX_ACTIONS) {
            actionPlan = new ArrayList<>(actionPlan.subList(0, MAX_ACTIONS));
        }

        analyzed = true;
    }

    /** Get all weaknesses found during diagnosis. */
    public List<Weakness> getWeaknesses() {
        ensureAnalyzed();
        return Collections.unmodifiableList(weaknesses);
    }

    /** Get the full action plan (sorted by impact descending). */
    public List<Action> getActionPlan() {
        ensureAnalyzed();
        return Collections.unmodifiableList(actionPlan);
    }

    /** Get the top-N actions from the plan. */
    public List<Action> getTopActions(int n) {
        ensureAnalyzed();
        return actionPlan.stream().limit(n).collect(Collectors.toList());
    }

    /** Get the before-optimization health snapshot. */
    public HealthSnapshot getBeforeHealth() {
        ensureAnalyzed();
        return beforeHealth;
    }

    /**
     * Apply the top-N actions to a clone of the graph and return the optimized clone.
     *
     * @param n number of top actions to apply
     * @return a new graph with the actions applied
     */
    public Graph<String, Edge> applyTopActions(int n) {
        ensureAnalyzed();
        Graph<String, Edge> clone = cloneGraph(graph);
        int applied = 0;
        for (Action a : actionPlan) {
            if (applied >= n) break;
            if (a.sourceNode != null && a.targetNode != null) {
                if (!clone.containsVertex(a.sourceNode)) clone.addVertex(a.sourceNode);
                if (!clone.containsVertex(a.targetNode)) clone.addVertex(a.targetNode);
                if (clone.findEdge(a.sourceNode, a.targetNode) == null) {
                    Edge e = new Edge("optimization", a.sourceNode, a.targetNode);
                    clone.addEdge(e, a.sourceNode, a.targetNode);
                    applied++;
                }
            }
        }
        return clone;
    }

    /** Format a text report of the full analysis. */
    public String formatTextReport() {
        ensureAnalyzed();
        StringBuilder sb = new StringBuilder();
        sb.append("===========================================================\n");
        sb.append("  GRAPH AUTOPILOT - Optimization Report\n");
        sb.append("===========================================================\n\n");

        // Health snapshot
        sb.append("-- Current Health ------------------------------------------------\n");
        for (Map.Entry<String, Double> e : beforeHealth.toMap().entrySet()) {
            sb.append(String.format("  %-25s %s\n", e.getKey() + ":", formatNumber(e.getValue())));
        }
        sb.append("\n");

        // Weaknesses
        sb.append("-- Weaknesses Found (" + weaknesses.size() + ") ----------------------------------------\n");
        if (weaknesses.isEmpty()) {
            sb.append("  No significant weaknesses detected. Graph is healthy!\n");
        } else {
            for (Weakness w : weaknesses) {
                sb.append(String.format("  [%s] %s: %s\n", w.severity.label, w.category, w.description));
                if (!w.involvedNodes.isEmpty()) {
                    sb.append("    Nodes: " + String.join(", ", w.involvedNodes.subList(0,
                            Math.min(10, w.involvedNodes.size()))));
                    if (w.involvedNodes.size() > 10) sb.append(" ... (+" + (w.involvedNodes.size() - 10) + " more)");
                    sb.append("\n");
                }
            }
        }
        sb.append("\n");

        // Action plan
        sb.append("-- Action Plan (" + actionPlan.size() + " actions) ------------------------------------\n");
        if (actionPlan.isEmpty()) {
            sb.append("  No actions needed — graph topology is already well-optimized.\n");
        } else {
            int rank = 1;
            for (Action a : actionPlan) {
                sb.append(String.format("\n  #%d [%s | Impact: %.1f]\n", rank, a.priority.label, a.impactScore));
                sb.append(String.format("  Type: %s\n", a.type.getDescription()));
                sb.append(String.format("  Action: %s\n", a.description));
                sb.append(String.format("  Reason: %s\n", a.reasoning));
                if (a.simulatedImprovement != 0) {
                    sb.append(String.format("  Simulated improvement: %+.2f health points\n", a.simulatedImprovement));
                }
                rank++;
            }
        }

        sb.append("\n===========================================================\n");
        return sb.toString();
    }

    /**
     * Export an interactive HTML report.
     *
     * @param file output file
     * @throws IOException on write error
     */
    public void exportHtml(File file) throws IOException {
        ensureAnalyzed();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write(generateHtml());
        }
    }

    /** Get the HTML report as a string. */
    public String generateHtml() {
        ensureAnalyzed();
        StringBuilder h = new StringBuilder();
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        h.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">\n");
        h.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n");
        h.append("<title>GraphAutoPilot Report</title>\n");
        h.append("<style>\n");
        h.append(CSS);
        h.append("</style></head><body>\n");
        h.append("<div class=\"container\">\n");

        // Header
        h.append("<h1>GraphAutoPilot</h1>\n");
        h.append("<p class=\"subtitle\">Autonomous Graph Optimization Report - " + escHtml(ts) + "</p>\n");

        // Health gauge
        h.append("<div class=\"health-section\">\n");
        h.append("<h2>Health Score</h2>\n");
        h.append("<div class=\"gauge-container\">\n");
        double score = beforeHealth.healthScore;
        String color = score >= 80 ? "#27ae60" : score >= 50 ? "#f39c12" : "#e74c3c";
        h.append(String.format("<div class=\"gauge\" style=\"--score:%.0f;--color:%s\">\n", score, color));
        h.append(String.format("<span class=\"gauge-value\">%.0f</span>\n", score));
        h.append("</div></div>\n");

        // Metrics table
        h.append("<table class=\"metrics\"><tr><th>Metric</th><th>Value</th></tr>\n");
        for (Map.Entry<String, Double> e : beforeHealth.toMap().entrySet()) {
            if (e.getKey().equals("Health Score")) continue;
            h.append(String.format("<tr><td>%s</td><td>%s</td></tr>\n",
                    escHtml(e.getKey()), formatNumber(e.getValue())));
        }
        h.append("</table></div>\n");

        // Weaknesses
        h.append("<div class=\"weakness-section\">\n");
        h.append("<h2>Weaknesses (" + weaknesses.size() + ")</h2>\n");
        if (weaknesses.isEmpty()) {
            h.append("<p class=\"ok\">&#10003; No significant weaknesses detected.</p>\n");
        } else {
            for (Weakness w : weaknesses) {
                String cls = w.severity == Priority.CRITICAL ? "critical"
                        : w.severity == Priority.HIGH ? "high"
                        : w.severity == Priority.MEDIUM ? "medium" : "low";
                h.append(String.format("<div class=\"weakness %s\">\n", cls));
                h.append(String.format("<span class=\"badge %s\">%s</span> ", cls, escHtml(w.severity.label)));
                h.append(String.format("<strong>%s</strong>: %s\n",
                        escHtml(w.category), escHtml(w.description)));
                if (!w.involvedNodes.isEmpty()) {
                    h.append("<div class=\"nodes\">Nodes: " + escHtml(
                            String.join(", ", w.involvedNodes.subList(0, Math.min(8, w.involvedNodes.size()))))
                    );
                    if (w.involvedNodes.size() > 8) h.append(" ... (+" + (w.involvedNodes.size() - 8) + " more)");
                    h.append("</div>\n");
                }
                h.append("</div>\n");
            }
        }
        h.append("</div>\n");

        // Action plan
        h.append("<div class=\"plan-section\">\n");
        h.append("<h2>Action Plan (" + actionPlan.size() + " actions)</h2>\n");
        if (actionPlan.isEmpty()) {
            h.append("<p class=\"ok\">&#10003; No actions needed.</p>\n");
        } else {
            int rank = 1;
            for (Action a : actionPlan) {
                String pcls = a.priority == Priority.CRITICAL ? "critical"
                        : a.priority == Priority.HIGH ? "high"
                        : a.priority == Priority.MEDIUM ? "medium" : "low";
                h.append(String.format("<div class=\"action %s\">\n", pcls));
                h.append(String.format("<div class=\"action-header\">#%d " +
                        "<span class=\"badge %s\">%s</span> " +
                        "<span class=\"impact\">Impact: %.1f</span></div>\n",
                        rank, pcls, escHtml(a.priority.label), a.impactScore));
                h.append(String.format("<div class=\"action-type\">%s</div>\n",
                        escHtml(a.type.getDescription())));
                h.append(String.format("<div class=\"action-desc\">%s</div>\n",
                        escHtml(a.description)));
                h.append(String.format("<div class=\"action-reason\"><em>%s</em></div>\n",
                        escHtml(a.reasoning)));
                if (a.simulatedImprovement != 0) {
                    String impClr = a.simulatedImprovement > 0 ? "#27ae60" : "#e74c3c";
                    h.append(String.format("<div class=\"sim\" style=\"color:%s\">Simulated: %+.2f health points</div>\n",
                            impClr, a.simulatedImprovement));
                }
                h.append("</div>\n");
                rank++;
            }
        }
        h.append("</div>\n");

        // Summary
        h.append("<div class=\"summary-section\">\n");
        h.append("<h2>Summary</h2>\n");
        long critCount = weaknesses.stream().filter(w -> w.severity == Priority.CRITICAL).count();
        long highCount = weaknesses.stream().filter(w -> w.severity == Priority.HIGH).count();
        h.append(String.format("<p>%d weaknesses found (%d critical, %d high). " +
                "%d optimization actions generated.</p>\n",
                weaknesses.size(), critCount, highCount, actionPlan.size()));
        if (!actionPlan.isEmpty()) {
            double totalImprovement = actionPlan.stream()
                    .mapToDouble(a -> Math.max(0, a.simulatedImprovement)).sum();
            h.append(String.format("<p>Estimated total health improvement if all actions applied: <strong>+%.1f points</strong></p>\n",
                    totalImprovement));
        }
        h.append("</div>\n");

        h.append("</div></body></html>\n");
        return h.toString();
    }

    // ── Diagnosis ──────────────────────────────────────────

    private void diagnoseBridges() {
        Set<String> bridges = findBridges(graph, adjacency);
        if (!bridges.isEmpty()) {
            Priority sev = bridges.size() > graph.getEdgeCount() * 0.1 ? Priority.CRITICAL : Priority.HIGH;
            List<String> nodes = new ArrayList<>(bridges);
            weaknesses.add(new Weakness("Bridge Edges",
                    bridges.size() + " bridge edge(s) detected — removing any one disconnects the graph",
                    sev, nodes));
        }
    }

    private void diagnoseArticulationPoints() {
        Set<String> aps = findArticulationPoints(graph, adjacency);
        if (!aps.isEmpty()) {
            Priority sev = aps.size() > graph.getVertexCount() * 0.15 ? Priority.CRITICAL
                    : aps.size() > graph.getVertexCount() * 0.05 ? Priority.HIGH : Priority.MEDIUM;
            weaknesses.add(new Weakness("Articulation Points",
                    aps.size() + " articulation point(s) — their removal would split the graph",
                    sev, new ArrayList<>(aps)));
        }
    }

    private void diagnoseDegreeImbalance() {
        double gini = beforeHealth.degreeGini;
        if (gini > 0.5) {
            Priority sev = gini > 0.75 ? Priority.HIGH : Priority.MEDIUM;
            // Find the hubs
            double threshold = beforeHealth.avgDegree + 2 * stdDegree();
            List<String> hubs = graph.getVertices().stream()
                    .filter(v -> graph.degree(v) > threshold)
                    .sorted(Comparator.comparingInt(v -> -graph.degree(v)))
                    .limit(10)
                    .collect(Collectors.toList());
            weaknesses.add(new Weakness("Degree Imbalance",
                    String.format("Gini coefficient %.2f — highly uneven degree distribution", gini),
                    sev, hubs));
        }
    }

    private void diagnosePeripherals() {
        List<String> pendants = graph.getVertices().stream()
                .filter(v -> graph.degree(v) == 1)
                .sorted()
                .collect(Collectors.toList());
        if (pendants.size() > graph.getVertexCount() * 0.1 && pendants.size() >= 2) {
            weaknesses.add(new Weakness("Peripheral Nodes",
                    pendants.size() + " pendant node(s) with single connection — fragile attachment",
                    Priority.MEDIUM, pendants));
        }
    }

    private void diagnoseCommunityFragmentation() {
        List<Set<String>> components = findComponents(graph, adjacency);
        if (components.size() > 1) {
            weaknesses.add(new Weakness("Disconnected Components",
                    components.size() + " disconnected components — no paths between them",
                    Priority.CRITICAL,
                    components.stream().map(c -> c.iterator().next()).collect(Collectors.toList())));
        }
    }

    private void diagnoseDiameter() {
        if (beforeHealth.diameter > 10 && beforeHealth.diameter > Math.log(graph.getVertexCount()) * 3) {
            weaknesses.add(new Weakness("Large Diameter",
                    String.format("Diameter %.0f — paths between some nodes are excessively long", beforeHealth.diameter),
                    Priority.MEDIUM, Collections.emptyList()));
        }
    }

    // ── Planning ───────────────────────────────────────────

    private void planBridgeBypasses() {
        Set<String> bridgeKeys = findBridges(graph, adjacency);
        int count = 0;
        for (String key : bridgeKeys) {
            if (count >= 5) break;
            String[] parts = key.split("\\|\\|");
            if (parts.length != 2) continue;
            String u = parts[0], v = parts[1];

            // Find best bypass: connect a neighbor of u to a neighbor of v
            Set<String> uNeigh = adjacency.getOrDefault(u, Collections.emptySet());
            Set<String> vNeigh = adjacency.getOrDefault(v, Collections.emptySet());

            String bestA = null, bestB = null;
            for (String a : uNeigh) {
                if (a.equals(v)) continue;
                for (String b : vNeigh) {
                    if (b.equals(u) || b.equals(a)) continue;
                    if (graph.findEdge(a, b) == null) {
                        bestA = a;
                        bestB = b;
                        break;
                    }
                }
                if (bestA != null) break;
            }

            if (bestA != null) {
                actionPlan.add(new Action(ActionType.ADD_BYPASS, Priority.HIGH,
                        String.format("Add edge %s — %s (bypass for bridge %s — %s)", bestA, bestB, u, v),
                        bestA, bestB,
                        String.format("Bridge %s — %s is a single point of failure; " +
                                "adding a bypass creates an alternative path", u, v)));
                count++;
            }
        }
    }

    private void planPeripheralConnections() {
        List<String> pendants = graph.getVertices().stream()
                .filter(v -> graph.degree(v) == 1)
                .sorted()
                .collect(Collectors.toList());

        int count = 0;
        for (String pendant : pendants) {
            if (count >= 5) break;
            Set<String> myNeigh = adjacency.getOrDefault(pendant, Collections.emptySet());
            if (myNeigh.isEmpty()) continue;
            String onlyNeighbor = myNeigh.iterator().next();

            // Connect to a neighbor-of-neighbor
            Set<String> nn = adjacency.getOrDefault(onlyNeighbor, Collections.emptySet());
            for (String candidate : nn) {
                if (candidate.equals(pendant)) continue;
                if (graph.findEdge(pendant, candidate) == null) {
                    actionPlan.add(new Action(ActionType.CONNECT_PERIPHERAL, Priority.MEDIUM,
                            String.format("Connect pendant %s to %s", pendant, candidate),
                            pendant, candidate,
                            String.format("Node %s has only one connection (%s); adding a second " +
                                    "provides redundancy", pendant, onlyNeighbor)));
                    count++;
                    break;
                }
            }
        }
    }

    private void planHubReinforcement() {
        double threshold = beforeHealth.avgDegree + 2 * stdDegree();
        if (threshold < 4) return; // small graphs, not meaningful

        List<String> hubs = graph.getVertices().stream()
                .filter(v -> graph.degree(v) > threshold)
                .sorted(Comparator.comparingInt(v -> -graph.degree(v)))
                .limit(3)
                .collect(Collectors.toList());

        for (String hub : hubs) {
            Set<String> hubNeigh = adjacency.getOrDefault(hub, Collections.emptySet());
            List<String> neighList = new ArrayList<>(hubNeigh);
            if (neighList.size() < 3) continue;

            // Find two hub-neighbors not connected to each other
            for (int i = 0; i < neighList.size() && i < 20; i++) {
                for (int j = i + 1; j < neighList.size() && j < 20; j++) {
                    String a = neighList.get(i), b = neighList.get(j);
                    if (graph.findEdge(a, b) == null) {
                        actionPlan.add(new Action(ActionType.REINFORCE_HUB, Priority.MEDIUM,
                                String.format("Connect hub-neighbors %s and %s (reduce load on hub %s)", a, b, hub),
                                a, b,
                                String.format("Hub %s (degree %d) carries too much traffic; " +
                                        "connecting its neighbors creates alternative paths",
                                        hub, graph.degree(hub))));
                        i = neighList.size(); // break both loops
                        break;
                    }
                }
            }
        }
    }

    private void planCommunityBridging() {
        List<Set<String>> components = findComponents(graph, adjacency);
        if (components.size() <= 1) return;

        // Connect largest component to each disconnected component
        components.sort(Comparator.comparingInt(s -> -s.size()));
        Set<String> main = components.get(0);
        String mainHub = main.stream()
                .max(Comparator.comparingInt(v -> graph.degree(v)))
                .orElse(main.iterator().next());

        int count = 0;
        for (int i = 1; i < components.size() && count < 3; i++) {
            Set<String> comp = components.get(i);
            String compHub = comp.stream()
                    .max(Comparator.comparingInt(v -> graph.degree(v)))
                    .orElse(comp.iterator().next());
            actionPlan.add(new Action(ActionType.BRIDGE_COMMUNITIES, Priority.CRITICAL,
                    String.format("Bridge component %d: connect %s to %s", i + 1, mainHub, compHub),
                    mainHub, compHub,
                    String.format("Component %d (%d nodes) is completely disconnected from the main graph",
                            i + 1, comp.size())));
            count++;
        }
    }

    private void planShortcuts() {
        if (beforeHealth.diameter <= 6 || graph.getVertexCount() > 500) return;

        // BFS from random nodes to find distant pairs
        List<String> vertices = new ArrayList<>(graph.getVertices());
        int trials = Math.min(5, vertices.size());
        Set<String> seen = new HashSet<>();
        int count = 0;

        for (int t = 0; t < trials && count < 2; t++) {
            String start = vertices.get(t);
            Map<String, Integer> dist = bfs(start, adjacency);
            // Find the farthest node
            String farthest = null;
            int maxDist = 0;
            for (Map.Entry<String, Integer> e : dist.entrySet()) {
                if (e.getValue() > maxDist) {
                    maxDist = e.getValue();
                    farthest = e.getKey();
                }
            }
            if (farthest != null && maxDist > 4 && graph.findEdge(start, farthest) == null) {
                String key = start.compareTo(farthest) < 0 ? start + "||" + farthest : farthest + "||" + start;
                if (seen.add(key)) {
                    actionPlan.add(new Action(ActionType.ADD_SHORTCUT, Priority.LOW,
                            String.format("Add shortcut %s — %s (current distance: %d)", start, farthest, maxDist),
                            start, farthest,
                            String.format("These nodes are %d hops apart; a direct edge would reduce diameter", maxDist)));
                    count++;
                }
            }
        }
    }

    // ── Simulation ─────────────────────────────────────────

    private void simulateAction(Action action) {
        if (action.sourceNode == null || action.targetNode == null) {
            action.impactScore = action.priority.weight * 10;
            return;
        }

        HealthSnapshot before = beforeHealth;
        action.beforeMetrics = before.toMap();

        // Clone graph and apply action
        Graph<String, Edge> clone = cloneGraph(graph);
        if (!clone.containsVertex(action.sourceNode)) clone.addVertex(action.sourceNode);
        if (!clone.containsVertex(action.targetNode)) clone.addVertex(action.targetNode);

        if (clone.findEdge(action.sourceNode, action.targetNode) == null) {
            Edge e = new Edge("optimization", action.sourceNode, action.targetNode);
            clone.addEdge(e, action.sourceNode, action.targetNode);
        }

        HealthSnapshot after = computeHealth(clone);
        action.afterMetrics = after.toMap();
        action.simulatedImprovement = after.healthScore - before.healthScore;

        // Impact score combines priority and simulated improvement
        action.impactScore = Math.max(0, Math.min(100,
                action.priority.weight * 15 + action.simulatedImprovement * 3));
    }

    // ── Health computation ─────────────────────────────────

    private HealthSnapshot computeHealth(Graph<String, Edge> g) {
        HealthSnapshot h = new HealthSnapshot();
        int n = g.getVertexCount();
        int m = g.getEdgeCount();

        if (n == 0) {
            h.healthScore = 0;
            return h;
        }

        Map<String, Set<String>> adj = buildAdjacency(g);

        // Bridges
        h.bridgeCount = findBridges(g, adj).size();

        // Articulation points
        h.articulationPointCount = findArticulationPoints(g, adj).size();

        // Components
        h.componentCount = findComponents(g, adj).size();

        // Degree stats
        double[] degrees = g.getVertices().stream().mapToDouble(v -> g.degree(v)).toArray();
        h.avgDegree = Arrays.stream(degrees).average().orElse(0);
        h.maxDegree = Arrays.stream(degrees).max().orElse(0);
        h.degreeGini = gini(degrees);

        // Pendant nodes
        h.pendantCount = Arrays.stream(degrees).filter(d -> d == 1).count();

        // Clustering coefficient (sample if large)
        h.avgClusteringCoeff = avgClustering(g, adj);

        // Diameter (approximate for large graphs)
        h.diameter = approximateDiameter(g, adj);

        // Composite health score (0-100)
        double score = 100;
        // Penalty for bridges
        score -= Math.min(30, h.bridgeCount * 5);
        // Penalty for articulation points
        score -= Math.min(20, h.articulationPointCount * 3);
        // Penalty for disconnected components (beyond 1)
        score -= Math.min(25, (h.componentCount - 1) * 10);
        // Penalty for high degree imbalance
        score -= Math.min(10, h.degreeGini * 15);
        // Penalty for many pendants
        if (n > 0) score -= Math.min(10, (h.pendantCount / n) * 20);
        // Penalty for large diameter (relative to log(n))
        double expectedDiameter = n > 1 ? Math.log(n) / Math.log(2) * 2 : 1;
        if (h.diameter > expectedDiameter * 2) score -= Math.min(10, 5);

        h.healthScore = Math.max(0, Math.min(100, score));
        return h;
    }

    // ── Graph algorithms ───────────────────────────────────

    private Map<String, Set<String>> buildAdjacency(Graph<String, Edge> g) {
        Map<String, Set<String>> adj = new HashMap<>();
        for (String v : g.getVertices()) adj.put(v, new HashSet<>());
        for (Edge e : g.getEdges()) {
            Collection<String> ep = g.getEndpoints(e);
            if (ep == null || ep.size() < 2) continue;
            Iterator<String> it = ep.iterator();
            String a = it.next(), b = it.next();
            adj.get(a).add(b);
            adj.get(b).add(a);
        }
        return adj;
    }

    /** Find bridge edges. Returns set of "u||v" keys (sorted). */
    private Set<String> findBridges(Graph<String, Edge> g, Map<String, Set<String>> adj) {
        Set<String> bridges = new LinkedHashSet<>();
        Map<String, Integer> disc = new HashMap<>();
        Map<String, Integer> low = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        int[] timer = {0};

        for (String v : g.getVertices()) {
            if (!disc.containsKey(v)) {
                bridgeDfs(v, adj, disc, low, parent, timer, bridges);
            }
        }
        return bridges;
    }

    private void bridgeDfs(String u, Map<String, Set<String>> adj,
                           Map<String, Integer> disc, Map<String, Integer> low,
                           Map<String, String> parent, int[] timer, Set<String> bridges) {
        Deque<Object[]> stack = new ArrayDeque<>();
        // stack frame: [node, iterator, phase]
        disc.put(u, timer[0]);
        low.put(u, timer[0]);
        timer[0]++;
        stack.push(new Object[]{u, adj.getOrDefault(u, Collections.emptySet()).iterator()});

        while (!stack.isEmpty()) {
            Object[] frame = stack.peek();
            String node = (String) frame[0];
            @SuppressWarnings("unchecked")
            Iterator<String> it = (Iterator<String>) frame[1];

            if (it.hasNext()) {
                String next = it.next();
                if (!disc.containsKey(next)) {
                    parent.put(next, node);
                    disc.put(next, timer[0]);
                    low.put(next, timer[0]);
                    timer[0]++;
                    stack.push(new Object[]{next, adj.getOrDefault(next, Collections.emptySet()).iterator()});
                } else if (!next.equals(parent.get(node))) {
                    low.put(node, Math.min(low.get(node), disc.get(next)));
                }
            } else {
                stack.pop();
                if (!stack.isEmpty()) {
                    Object[] parentFrame = stack.peek();
                    String par = (String) parentFrame[0];
                    low.put(par, Math.min(low.get(par), low.get(node)));
                    if (low.get(node) > disc.get(par)) {
                        String key = par.compareTo(node) <= 0 ? par + "||" + node : node + "||" + par;
                        bridges.add(key);
                    }
                }
            }
        }
    }

    /** Find articulation points. */
    private Set<String> findArticulationPoints(Graph<String, Edge> g, Map<String, Set<String>> adj) {
        Set<String> aps = new LinkedHashSet<>();
        Map<String, Integer> disc = new HashMap<>();
        Map<String, Integer> low = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        int[] timer = {0};

        for (String v : g.getVertices()) {
            if (!disc.containsKey(v)) {
                apDfs(v, adj, disc, low, parent, timer, aps);
            }
        }
        return aps;
    }

    private void apDfs(String u, Map<String, Set<String>> adj,
                       Map<String, Integer> disc, Map<String, Integer> low,
                       Map<String, String> parent, int[] timer, Set<String> aps) {
        Deque<Object[]> stack = new ArrayDeque<>();
        Map<String, Integer> childCount = new HashMap<>();
        disc.put(u, timer[0]);
        low.put(u, timer[0]);
        timer[0]++;
        childCount.put(u, 0);
        stack.push(new Object[]{u, adj.getOrDefault(u, Collections.emptySet()).iterator()});

        while (!stack.isEmpty()) {
            Object[] frame = stack.peek();
            String node = (String) frame[0];
            @SuppressWarnings("unchecked")
            Iterator<String> it = (Iterator<String>) frame[1];

            if (it.hasNext()) {
                String next = it.next();
                if (!disc.containsKey(next)) {
                    parent.put(next, node);
                    childCount.put(node, childCount.getOrDefault(node, 0) + 1);
                    disc.put(next, timer[0]);
                    low.put(next, timer[0]);
                    timer[0]++;
                    childCount.put(next, 0);
                    stack.push(new Object[]{next, adj.getOrDefault(next, Collections.emptySet()).iterator()});
                } else if (!next.equals(parent.get(node))) {
                    low.put(node, Math.min(low.get(node), disc.get(next)));
                }
            } else {
                stack.pop();
                if (!stack.isEmpty()) {
                    Object[] parentFrame = stack.peek();
                    String par = (String) parentFrame[0];
                    low.put(par, Math.min(low.get(par), low.get(node)));

                    // If par is root, it's an AP if it has 2+ children
                    // If par is not root, it's an AP if low[node] >= disc[par]
                    if (parent.get(par) == null) {
                        // root
                        if (childCount.getOrDefault(par, 0) >= 2) aps.add(par);
                    } else {
                        if (low.get(node) >= disc.get(par)) aps.add(par);
                    }
                } else {
                    // root node
                    if (childCount.getOrDefault(node, 0) >= 2) aps.add(node);
                }
            }
        }
    }

    /** Find connected components. */
    private List<Set<String>> findComponents(Graph<String, Edge> g, Map<String, Set<String>> adj) {
        Set<String> visited = new HashSet<>();
        List<Set<String>> components = new ArrayList<>();
        for (String v : g.getVertices()) {
            if (visited.contains(v)) continue;
            Set<String> comp = new HashSet<>();
            Queue<String> queue = new ArrayDeque<>();
            queue.add(v);
            visited.add(v);
            while (!queue.isEmpty()) {
                String node = queue.poll();
                comp.add(node);
                for (String neigh : adj.getOrDefault(node, Collections.emptySet())) {
                    if (visited.add(neigh)) queue.add(neigh);
                }
            }
            components.add(comp);
        }
        return components;
    }

    /** BFS distances from a source. */
    private Map<String, Integer> bfs(String source, Map<String, Set<String>> adj) {
        Map<String, Integer> dist = new HashMap<>();
        Queue<String> queue = new ArrayDeque<>();
        dist.put(source, 0);
        queue.add(source);
        while (!queue.isEmpty()) {
            String u = queue.poll();
            int d = dist.get(u);
            for (String v : adj.getOrDefault(u, Collections.emptySet())) {
                if (!dist.containsKey(v)) {
                    dist.put(v, d + 1);
                    queue.add(v);
                }
            }
        }
        return dist;
    }

    /** Approximate diameter using BFS from a few nodes. */
    private double approximateDiameter(Graph<String, Edge> g, Map<String, Set<String>> adj) {
        if (g.getVertexCount() <= 1) return 0;
        List<String> vertices = new ArrayList<>(g.getVertices());
        int maxDist = 0;
        int samples = Math.min(10, vertices.size());
        for (int i = 0; i < samples; i++) {
            Map<String, Integer> dist = bfs(vertices.get(i), adj);
            for (int d : dist.values()) {
                if (d > maxDist) maxDist = d;
            }
        }
        return maxDist;
    }

    /** Average clustering coefficient. */
    private double avgClustering(Graph<String, Edge> g, Map<String, Set<String>> adj) {
        double sum = 0;
        int count = 0;
        for (String v : g.getVertices()) {
            Set<String> neigh = adj.getOrDefault(v, Collections.emptySet());
            int k = neigh.size();
            if (k < 2) continue;
            int triangles = 0;
            List<String> nList = new ArrayList<>(neigh);
            for (int i = 0; i < nList.size(); i++) {
                for (int j = i + 1; j < nList.size(); j++) {
                    if (adj.getOrDefault(nList.get(i), Collections.emptySet()).contains(nList.get(j))) {
                        triangles++;
                    }
                }
            }
            sum += (2.0 * triangles) / (k * (k - 1));
            count++;
        }
        return count > 0 ? sum / count : 0;
    }

    /** Gini coefficient for an array of values. */
    private double gini(double[] values) {
        if (values.length <= 1) return 0;
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double sum = 0, cumSum = 0;
        for (double v : sorted) sum += v;
        if (sum == 0) return 0;
        int n = sorted.length;
        for (int i = 0; i < n; i++) {
            cumSum += sorted[i];
        }
        double weightedSum = 0;
        for (int i = 0; i < n; i++) {
            weightedSum += (2.0 * (i + 1) - n - 1) * sorted[i];
        }
        return weightedSum / (n * sum);
    }

    /** Standard deviation of degree. */
    private double stdDegree() {
        double[] degrees = graph.getVertices().stream().mapToDouble(v -> graph.degree(v)).toArray();
        double mean = Arrays.stream(degrees).average().orElse(0);
        double variance = Arrays.stream(degrees).map(d -> (d - mean) * (d - mean)).average().orElse(0);
        return Math.sqrt(variance);
    }

    /** Clone an undirected graph. */
    private Graph<String, Edge> cloneGraph(Graph<String, Edge> g) {
        Graph<String, Edge> clone = new UndirectedSparseGraph<>();
        for (String v : g.getVertices()) clone.addVertex(v);
        for (Edge e : g.getEdges()) {
            Collection<String> ep = g.getEndpoints(e);
            if (ep == null || ep.size() < 2) continue;
            Iterator<String> it = ep.iterator();
            String a = it.next(), b = it.next();
            Edge copy = new Edge(e.getType(), a, b);
            copy.setWeight(e.getWeight());
            if (e.getLabel() != null) copy.setLabel(e.getLabel());
            clone.addEdge(copy, a, b);
        }
        return clone;
    }

    // ── Utility ────────────────────────────────────────────

    private void ensureAnalyzed() {
        if (!analyzed) throw new IllegalStateException("Call analyze() first");
    }

    private static String formatNumber(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) return String.valueOf((long) v);
        return String.format("%.2f", v);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    // ── CSS ────────────────────────────────────────────────

    private static final String CSS =
            "* { box-sizing: border-box; margin: 0; padding: 0; }\n" +
            "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;\n" +
            "  background: #0f0f0f; color: #e0e0e0; padding: 2rem; }\n" +
            ".container { max-width: 900px; margin: 0 auto; }\n" +
            "h1 { font-size: 2rem; color: #fff; margin-bottom: 0.3rem; }\n" +
            ".subtitle { color: #888; margin-bottom: 2rem; }\n" +
            "h2 { color: #fff; margin: 1.5rem 0 1rem; border-bottom: 1px solid #333; padding-bottom: 0.5rem; }\n" +
            ".gauge-container { display: flex; justify-content: center; margin: 1rem 0; }\n" +
            ".gauge { width: 120px; height: 120px; border-radius: 50%;\n" +
            "  background: conic-gradient(var(--color) calc(var(--score) * 3.6deg), #333 0deg);\n" +
            "  display: flex; align-items: center; justify-content: center;\n" +
            "  position: relative; }\n" +
            ".gauge::after { content: ''; width: 90px; height: 90px; border-radius: 50%;\n" +
            "  background: #0f0f0f; position: absolute; }\n" +
            ".gauge-value { font-size: 1.8rem; font-weight: bold; color: #fff; z-index: 1; }\n" +
            "table.metrics { width: 100%; border-collapse: collapse; margin: 1rem 0; }\n" +
            "table.metrics th, table.metrics td { padding: 0.5rem 1rem; text-align: left;\n" +
            "  border-bottom: 1px solid #222; }\n" +
            "table.metrics th { color: #aaa; font-weight: 600; }\n" +
            ".badge { display: inline-block; padding: 2px 8px; border-radius: 4px;\n" +
            "  font-size: 0.75rem; font-weight: bold; text-transform: uppercase; }\n" +
            ".badge.critical { background: #e74c3c; color: #fff; }\n" +
            ".badge.high { background: #e67e22; color: #fff; }\n" +
            ".badge.medium { background: #f1c40f; color: #000; }\n" +
            ".badge.low { background: #3498db; color: #fff; }\n" +
            ".weakness, .action { background: #1a1a1a; border-radius: 8px; padding: 1rem;\n" +
            "  margin: 0.5rem 0; border-left: 4px solid #555; }\n" +
            ".weakness.critical, .action.critical { border-left-color: #e74c3c; }\n" +
            ".weakness.high, .action.high { border-left-color: #e67e22; }\n" +
            ".weakness.medium, .action.medium { border-left-color: #f1c40f; }\n" +
            ".weakness.low, .action.low { border-left-color: #3498db; }\n" +
            ".nodes { margin-top: 0.5rem; font-size: 0.85rem; color: #888;\n" +
            "  font-family: 'SF Mono', monospace; }\n" +
            ".action-header { font-size: 1.1rem; font-weight: bold; margin-bottom: 0.5rem; }\n" +
            ".action-type { color: #aaa; margin-bottom: 0.3rem; }\n" +
            ".action-desc { margin-bottom: 0.3rem; }\n" +
            ".action-reason { color: #888; font-size: 0.9rem; margin-bottom: 0.3rem; }\n" +
            ".impact { float: right; font-size: 0.9rem; color: #3498db; }\n" +
            ".sim { font-weight: bold; margin-top: 0.3rem; }\n" +
            ".ok { color: #27ae60; font-weight: bold; }\n" +
            ".summary-section p { line-height: 1.6; margin: 0.5rem 0; }\n";
}
