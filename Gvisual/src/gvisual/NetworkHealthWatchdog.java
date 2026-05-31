package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.util.*;

/**
 * NetworkHealthWatchdog - agentic structural health monitor for temporal graphs.
 *
 * <p>Given an ordered sequence of graph snapshots (e.g., daily network states),
 * the watchdog computes per-snapshot health metrics, maintains a rolling baseline,
 * detects anomalies by comparing each snapshot against the baseline, and emits
 * prioritized alerts with recommended actions.</p>
 *
 * <h3>Detected anomalies:</h3>
 * <ul>
 *   <li>{@link AnomalyType#DENSITY_SPIKE} - density increased &gt; 2x baseline stddev</li>
 *   <li>{@link AnomalyType#DENSITY_COLLAPSE} - density dropped &gt; 2x baseline stddev</li>
 *   <li>{@link AnomalyType#HUB_DISAPPEARED} - top-degree node from prior snapshot absent</li>
 *   <li>{@link AnomalyType#HUB_EMERGED} - node degree jumped from &lt;= median to top-3</li>
 *   <li>{@link AnomalyType#COMPONENT_FRAGMENTATION} - connected components count jumped significantly</li>
 *   <li>{@link AnomalyType#MASS_EDGE_LOSS} - &gt; 30% of edges lost in one step</li>
 *   <li>{@link AnomalyType#MASS_NODE_INFLUX} - &gt; 50% new nodes appeared in one step</li>
 *   <li>{@link AnomalyType#ISOLATION_WAVE} - isolated nodes (degree 0) jumped &gt; 3x</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <pre>
 *   var watchdog = new NetworkHealthWatchdog();
 *   watchdog.addSnapshot("day-1", graph1);
 *   watchdog.addSnapshot("day-2", graph2);
 *   watchdog.addSnapshot("day-3", graph3);
 *   NetworkHealthWatchdog.Report report = watchdog.analyze();
 *   System.out.println(watchdog.toMarkdown(report));
 * </pre>
 *
 * @author sauravbhattacharya001
 */
public final class NetworkHealthWatchdog {

    // -- Public types --

    public enum AnomalyType {
        DENSITY_SPIKE,
        DENSITY_COLLAPSE,
        HUB_DISAPPEARED,
        HUB_EMERGED,
        COMPONENT_FRAGMENTATION,
        MASS_EDGE_LOSS,
        MASS_NODE_INFLUX,
        ISOLATION_WAVE
    }

    public enum Priority { P0, P1, P2, P3 }

    public enum ActionType {
        INVESTIGATE_DENSITY_CHANGE,
        LOCATE_MISSING_HUB,
        MONITOR_NEW_HUB,
        REPAIR_FRAGMENTATION,
        AUDIT_EDGE_LOSS,
        ONBOARD_NEW_NODES,
        RE_ENGAGE_ISOLATED_NODES,
        MAINTAIN_MONITORING
    }

    public enum Grade { A, B, C, D, F }

    /** Structural metrics for a single snapshot. */
    public static final class SnapshotMetrics {
        public final String label;
        public final int nodeCount;
        public final int edgeCount;
        public final double density;
        public final int componentCount;
        public final int isolatedNodes;
        public final double avgDegree;
        public final int maxDegree;
        public final String topHub;

        public SnapshotMetrics(String label, int nodeCount, int edgeCount,
                               double density, int componentCount, int isolatedNodes,
                               double avgDegree, int maxDegree, String topHub) {
            this.label = label;
            this.nodeCount = nodeCount;
            this.edgeCount = edgeCount;
            this.density = density;
            this.componentCount = componentCount;
            this.isolatedNodes = isolatedNodes;
            this.avgDegree = avgDegree;
            this.maxDegree = maxDegree;
            this.topHub = topHub;
        }
    }

    /** A detected anomaly at a specific snapshot. */
    public static final class Anomaly {
        public final String snapshotLabel;
        public final AnomalyType type;
        public final Priority priority;
        public final double severity;
        public final String detail;

        public Anomaly(String snapshotLabel, AnomalyType type, Priority priority,
                       double severity, String detail) {
            this.snapshotLabel = snapshotLabel;
            this.type = type;
            this.priority = priority;
            this.severity = severity;
            this.detail = detail;
        }
    }

    /** Recommended action. */
    public static final class Action {
        public final Priority priority;
        public final ActionType type;
        public final String reason;
        public final String owner;
        public final int blastRadius;
        public final List<String> relatedSnapshots;

        public Action(Priority priority, ActionType type, String reason,
                      String owner, int blastRadius, List<String> relatedSnapshots) {
            this.priority = priority;
            this.type = type;
            this.reason = reason;
            this.owner = owner;
            this.blastRadius = blastRadius;
            this.relatedSnapshots = Collections.unmodifiableList(relatedSnapshots);
        }
    }

    /** Full analysis report. */
    public static final class Report {
        public final List<SnapshotMetrics> metrics;
        public final List<Anomaly> anomalies;
        public final List<Action> playbook;
        public final double healthScore;
        public final Grade grade;
        public final String trajectory;
        public final String headline;

        public Report(List<SnapshotMetrics> metrics, List<Anomaly> anomalies,
                      List<Action> playbook, double healthScore, Grade grade,
                      String trajectory, String headline) {
            this.metrics = Collections.unmodifiableList(metrics);
            this.anomalies = Collections.unmodifiableList(anomalies);
            this.playbook = Collections.unmodifiableList(playbook);
            this.healthScore = healthScore;
            this.grade = grade;
            this.trajectory = trajectory;
            this.headline = headline;
        }
    }

    // -- Instance state --

    private final List<String> labels = new ArrayList<>();
    private final List<Graph<String, Edge>> snapshots = new ArrayList<>();

    // -- Builder methods --

    /**
     * Adds a graph snapshot with a label (e.g. timestamp or day number).
     * Snapshots must be added in chronological order.
     */
    public NetworkHealthWatchdog addSnapshot(String label, Graph<String, Edge> graph) {
        if (label == null || label.isEmpty()) throw new IllegalArgumentException("label required");
        if (graph == null) throw new IllegalArgumentException("graph required");
        labels.add(label);
        snapshots.add(graph);
        return this;
    }

    /**
     * Runs the full analysis over all added snapshots.
     * Requires at least 2 snapshots.
     */
    public Report analyze() {
        if (snapshots.size() < 2) {
            throw new IllegalArgumentException("At least 2 snapshots required for analysis");
        }

        List<SnapshotMetrics> metricsList = new ArrayList<>();
        for (int i = 0; i < snapshots.size(); i++) {
            metricsList.add(computeMetrics(labels.get(i), snapshots.get(i)));
        }

        List<Anomaly> anomalies = detectAnomalies(metricsList);
        List<Action> playbook = buildPlaybook(anomalies);
        double healthScore = computeHealthScore(anomalies);
        Grade grade = gradeFromScore(healthScore);
        String trajectory = inferTrajectory(metricsList);
        String headline = buildHeadline(grade, anomalies.size(), trajectory);

        return new Report(metricsList, anomalies, playbook, healthScore, grade, trajectory, headline);
    }

    // -- Renderers --

    public String toText(Report r) {
        StringBuilder sb = new StringBuilder();
        sb.append("NETWORK HEALTH: ").append(r.headline).append("\n\n");
        sb.append("Health Score: ").append(String.format(Locale.ROOT, "%.1f", r.healthScore))
          .append("/100 | Grade: ").append(r.grade)
          .append(" | Trajectory: ").append(r.trajectory)
          .append(" | Anomalies: ").append(r.anomalies.size()).append("\n\n");

        if (!r.anomalies.isEmpty()) {
            sb.append("ANOMALIES:\n");
            for (Anomaly a : r.anomalies) {
                sb.append("  [").append(a.priority).append("] ").append(a.type)
                  .append(" @ ").append(a.snapshotLabel)
                  .append(" (sev ").append(String.format(Locale.ROOT, "%.0f", a.severity))
                  .append(") - ").append(a.detail).append("\n");
            }
            sb.append("\n");
        }

        if (!r.playbook.isEmpty()) {
            sb.append("PLAYBOOK:\n");
            for (Action a : r.playbook) {
                sb.append("  [").append(a.priority).append("] ").append(a.type)
                  .append(" (").append(a.owner).append(", blast=").append(a.blastRadius)
                  .append(") - ").append(a.reason).append("\n");
            }
        }
        return sb.toString();
    }

    public String toMarkdown(Report r) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Network Health Watchdog Report\n\n");
        sb.append("**").append(r.headline).append("**\n\n");
        sb.append("| Metric | Value |\n|--------|-------|\n");
        sb.append("| Health Score | ").append(String.format(Locale.ROOT, "%.1f", r.healthScore)).append("/100 |\n");
        sb.append("| Grade | ").append(r.grade).append(" |\n");
        sb.append("| Trajectory | ").append(r.trajectory).append(" |\n");
        sb.append("| Snapshots | ").append(r.metrics.size()).append(" |\n");
        sb.append("| Anomalies | ").append(r.anomalies.size()).append(" |\n\n");

        sb.append("### Snapshot Metrics\n\n");
        sb.append("| Snapshot | Nodes | Edges | Density | Components | Isolated | Avg Degree | Top Hub |\n");
        sb.append("|----------|-------|-------|---------|------------|----------|------------|----------|\n");
        for (SnapshotMetrics m : r.metrics) {
            sb.append("| ").append(m.label)
              .append(" | ").append(m.nodeCount)
              .append(" | ").append(m.edgeCount)
              .append(" | ").append(String.format(Locale.ROOT, "%.4f", m.density))
              .append(" | ").append(m.componentCount)
              .append(" | ").append(m.isolatedNodes)
              .append(" | ").append(String.format(Locale.ROOT, "%.2f", m.avgDegree))
              .append(" | ").append(m.topHub != null ? m.topHub : "-")
              .append(" |\n");
        }
        sb.append("\n");

        if (!r.anomalies.isEmpty()) {
            sb.append("### Anomalies\n\n");
            sb.append("| Priority | Type | Snapshot | Severity | Detail |\n");
            sb.append("|----------|------|----------|----------|--------|\n");
            for (Anomaly a : r.anomalies) {
                sb.append("| ").append(a.priority)
                  .append(" | ").append(a.type)
                  .append(" | ").append(a.snapshotLabel)
                  .append(" | ").append(String.format(Locale.ROOT, "%.0f", a.severity))
                  .append(" | ").append(a.detail).append(" |\n");
            }
            sb.append("\n");
        }

        if (!r.playbook.isEmpty()) {
            sb.append("### Playbook\n\n");
            sb.append("| Priority | Action | Owner | Blast | Reason |\n");
            sb.append("|----------|--------|-------|-------|--------|\n");
            for (Action a : r.playbook) {
                sb.append("| ").append(a.priority)
                  .append(" | ").append(a.type)
                  .append(" | ").append(a.owner)
                  .append(" | ").append(a.blastRadius)
                  .append(" | ").append(a.reason).append(" |\n");
            }
        }
        return sb.toString();
    }

    public String toJson(Report r) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"grade\": \"").append(r.grade).append("\",\n");
        sb.append("  \"healthScore\": ").append(String.format(Locale.ROOT, "%.1f", r.healthScore)).append(",\n");
        sb.append("  \"headline\": ").append(jsonStr(r.headline)).append(",\n");
        sb.append("  \"trajectory\": \"").append(r.trajectory).append("\",\n");

        sb.append("  \"anomalies\": [\n");
        for (int i = 0; i < r.anomalies.size(); i++) {
            Anomaly a = r.anomalies.get(i);
            sb.append("    {\"priority\": \"").append(a.priority)
              .append("\", \"type\": \"").append(a.type)
              .append("\", \"snapshot\": ").append(jsonStr(a.snapshotLabel))
              .append(", \"severity\": ").append(String.format(Locale.ROOT, "%.1f", a.severity))
              .append(", \"detail\": ").append(jsonStr(a.detail)).append("}");
            if (i < r.anomalies.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        sb.append("  \"playbook\": [\n");
        for (int i = 0; i < r.playbook.size(); i++) {
            Action a = r.playbook.get(i);
            sb.append("    {\"priority\": \"").append(a.priority)
              .append("\", \"action\": \"").append(a.type)
              .append("\", \"owner\": \"").append(a.owner)
              .append("\", \"blastRadius\": ").append(a.blastRadius)
              .append(", \"reason\": ").append(jsonStr(a.reason)).append("}");
            if (i < r.playbook.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}");
        return sb.toString();
    }

    // -- Internal helpers --

    private SnapshotMetrics computeMetrics(String label, Graph<String, Edge> g) {
        int nodes = g.getVertexCount();
        int edges = g.getEdgeCount();
        double density = nodes < 2 ? 0.0 : (2.0 * edges) / (nodes * (nodes - 1.0));
        int components = countComponents(g);
        int isolated = 0;
        int maxDeg = 0;
        String topHub = null;
        double degSum = 0;

        for (String v : g.getVertices()) {
            int deg = g.degree(v);
            degSum += deg;
            if (deg == 0) isolated++;
            if (deg > maxDeg) {
                maxDeg = deg;
                topHub = v;
            }
        }
        double avgDeg = nodes > 0 ? degSum / nodes : 0;
        return new SnapshotMetrics(label, nodes, edges, density, components, isolated, avgDeg, maxDeg, topHub);
    }

    private int countComponents(Graph<String, Edge> g) {
        Set<String> visited = new HashSet<>();
        int count = 0;
        for (String v : g.getVertices()) {
            if (!visited.contains(v)) {
                count++;
                Queue<String> queue = new LinkedList<>();
                queue.add(v);
                visited.add(v);
                while (!queue.isEmpty()) {
                    String curr = queue.poll();
                    for (String neighbor : g.getNeighbors(curr)) {
                        if (visited.add(neighbor)) {
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }
        return count;
    }

    private List<Anomaly> detectAnomalies(List<SnapshotMetrics> metricsList) {
        List<Anomaly> anomalies = new ArrayList<>();

        for (int i = 1; i < metricsList.size(); i++) {
            SnapshotMetrics curr = metricsList.get(i);
            SnapshotMetrics prev = metricsList.get(i - 1);

            // Density baseline from all snapshots up to i-1
            double[] densities = new double[i];
            for (int j = 0; j < i; j++) densities[j] = metricsList.get(j).density;
            double meanDensity = mean(densities);
            double stdDensity = stddev(densities, meanDensity);
            double densityThreshold = Math.max(stdDensity * 2, 0.01);

            // DENSITY_SPIKE
            if (curr.density - meanDensity > densityThreshold) {
                double sev = Math.min(100, 50 + 30 * ((curr.density - meanDensity) / Math.max(densityThreshold, 0.001)));
                anomalies.add(new Anomaly(curr.label, AnomalyType.DENSITY_SPIKE,
                        sev >= 70 ? Priority.P0 : Priority.P1, sev,
                        String.format(Locale.ROOT, "Density %.4f vs baseline %.4f (threshold %.4f)",
                                curr.density, meanDensity, densityThreshold)));
            }

            // DENSITY_COLLAPSE
            if (meanDensity - curr.density > densityThreshold) {
                double sev = Math.min(100, 55 + 30 * ((meanDensity - curr.density) / Math.max(densityThreshold, 0.001)));
                anomalies.add(new Anomaly(curr.label, AnomalyType.DENSITY_COLLAPSE,
                        sev >= 70 ? Priority.P0 : Priority.P1, sev,
                        String.format(Locale.ROOT, "Density %.4f vs baseline %.4f (threshold %.4f)",
                                curr.density, meanDensity, densityThreshold)));
            }

            // HUB_DISAPPEARED
            if (prev.topHub != null && curr.nodeCount > 0) {
                Graph<String, Edge> currGraph = snapshots.get(i);
                if (!currGraph.containsVertex(prev.topHub)) {
                    anomalies.add(new Anomaly(curr.label, AnomalyType.HUB_DISAPPEARED,
                            Priority.P0, 75,
                            "Hub '" + prev.topHub + "' (degree " + prev.maxDegree + ") absent in this snapshot"));
                }
            }

            // HUB_EMERGED
            if (prev.nodeCount > 3 && curr.nodeCount > 3) {
                Graph<String, Edge> prevGraph = snapshots.get(i - 1);
                Graph<String, Edge> currGraph = snapshots.get(i);
                List<Map.Entry<String, Integer>> currDegrees = new ArrayList<>();
                for (String v : currGraph.getVertices()) {
                    currDegrees.add(new AbstractMap.SimpleEntry<>(v, currGraph.degree(v)));
                }
                currDegrees.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
                int top3Threshold = currDegrees.size() > 3 ? currDegrees.get(2).getValue() : 0;
                double prevMedianDeg = prev.avgDegree;

                for (int k = 0; k < Math.min(3, currDegrees.size()); k++) {
                    Map.Entry<String, Integer> entry = currDegrees.get(k);
                    String node = entry.getKey();
                    int currDeg = entry.getValue();
                    int prevDeg = prevGraph.containsVertex(node) ? prevGraph.degree(node) : 0;
                    if (prevDeg <= prevMedianDeg && currDeg >= top3Threshold && currDeg > prevDeg * 3 && currDeg >= 3) {
                        anomalies.add(new Anomaly(curr.label, AnomalyType.HUB_EMERGED,
                                Priority.P1, 55,
                                "Node '" + node + "' degree jumped " + prevDeg + " -> " + currDeg));
                        break;
                    }
                }
            }

            // COMPONENT_FRAGMENTATION
            if (prev.componentCount > 0 && curr.componentCount > prev.componentCount * 2 && curr.componentCount >= 3) {
                double sev = Math.min(100, 50 + 10 * (curr.componentCount - prev.componentCount));
                anomalies.add(new Anomaly(curr.label, AnomalyType.COMPONENT_FRAGMENTATION,
                        sev >= 75 ? Priority.P0 : Priority.P1, sev,
                        "Components " + prev.componentCount + " -> " + curr.componentCount));
            }

            // MASS_EDGE_LOSS
            if (prev.edgeCount > 0) {
                double lossRatio = 1.0 - (double) curr.edgeCount / prev.edgeCount;
                if (lossRatio > 0.30) {
                    double sev = Math.min(100, 50 + 80 * lossRatio);
                    anomalies.add(new Anomaly(curr.label, AnomalyType.MASS_EDGE_LOSS,
                            sev >= 70 ? Priority.P0 : Priority.P1, sev,
                            String.format(Locale.ROOT, "%.0f%% edges lost (%d -> %d)",
                                    lossRatio * 100, prev.edgeCount, curr.edgeCount)));
                }
            }

            // MASS_NODE_INFLUX
            if (prev.nodeCount > 0) {
                Graph<String, Edge> prevGraph = snapshots.get(i - 1);
                Graph<String, Edge> currGraph = snapshots.get(i);
                int newNodes = 0;
                for (String v : currGraph.getVertices()) {
                    if (!prevGraph.containsVertex(v)) newNodes++;
                }
                double influxRatio = (double) newNodes / prev.nodeCount;
                if (influxRatio > 0.50) {
                    anomalies.add(new Anomaly(curr.label, AnomalyType.MASS_NODE_INFLUX,
                            Priority.P1, Math.min(100, 40 + 60 * influxRatio),
                            String.format(Locale.ROOT, "%d new nodes (%.0f%% of prior count %d)",
                                    newNodes, influxRatio * 100, prev.nodeCount)));
                }
            }

            // ISOLATION_WAVE
            if (prev.isolatedNodes > 0 && curr.isolatedNodes > prev.isolatedNodes * 3 && curr.isolatedNodes >= 3) {
                anomalies.add(new Anomaly(curr.label, AnomalyType.ISOLATION_WAVE,
                        Priority.P1, Math.min(100, 45 + 5 * curr.isolatedNodes),
                        "Isolated nodes " + prev.isolatedNodes + " -> " + curr.isolatedNodes));
            } else if (prev.isolatedNodes == 0 && curr.isolatedNodes >= 4) {
                anomalies.add(new Anomaly(curr.label, AnomalyType.ISOLATION_WAVE,
                        Priority.P1, Math.min(100, 45 + 5 * curr.isolatedNodes),
                        "Isolated nodes 0 -> " + curr.isolatedNodes));
            }
        }

        anomalies.sort((a, b) -> {
            int pc = a.priority.compareTo(b.priority);
            if (pc != 0) return pc;
            return Double.compare(b.severity, a.severity);
        });
        return anomalies;
    }

    private List<Action> buildPlaybook(List<Anomaly> anomalies) {
        List<Action> actions = new ArrayList<>();
        Set<ActionType> seen = new HashSet<>();

        for (Anomaly a : anomalies) {
            ActionType at = mapToAction(a.type);
            if (seen.add(at)) {
                actions.add(new Action(
                        a.priority, at, reasonFor(a), ownerFor(at),
                        blastFor(at), Collections.singletonList(a.snapshotLabel)));
            }
        }

        if (actions.isEmpty()) {
            actions.add(new Action(Priority.P3, ActionType.MAINTAIN_MONITORING,
                    "No anomalies detected", "operator", 1, Collections.emptyList()));
        }
        return actions;
    }

    private ActionType mapToAction(AnomalyType type) {
        switch (type) {
            case DENSITY_SPIKE:
            case DENSITY_COLLAPSE:
                return ActionType.INVESTIGATE_DENSITY_CHANGE;
            case HUB_DISAPPEARED:
                return ActionType.LOCATE_MISSING_HUB;
            case HUB_EMERGED:
                return ActionType.MONITOR_NEW_HUB;
            case COMPONENT_FRAGMENTATION:
                return ActionType.REPAIR_FRAGMENTATION;
            case MASS_EDGE_LOSS:
                return ActionType.AUDIT_EDGE_LOSS;
            case MASS_NODE_INFLUX:
                return ActionType.ONBOARD_NEW_NODES;
            case ISOLATION_WAVE:
                return ActionType.RE_ENGAGE_ISOLATED_NODES;
            default:
                return ActionType.MAINTAIN_MONITORING;
        }
    }

    private String reasonFor(Anomaly a) {
        return a.type + " detected at " + a.snapshotLabel + " (severity " +
                String.format(Locale.ROOT, "%.0f", a.severity) + ")";
    }

    private String ownerFor(ActionType at) {
        switch (at) {
            case INVESTIGATE_DENSITY_CHANGE:
            case AUDIT_EDGE_LOSS:
                return "analyst";
            case LOCATE_MISSING_HUB:
            case REPAIR_FRAGMENTATION:
                return "network_admin";
            case MONITOR_NEW_HUB:
            case ONBOARD_NEW_NODES:
                return "operator";
            case RE_ENGAGE_ISOLATED_NODES:
                return "community_manager";
            default:
                return "operator";
        }
    }

    private int blastFor(ActionType at) {
        switch (at) {
            case REPAIR_FRAGMENTATION:
            case AUDIT_EDGE_LOSS:
                return 4;
            case LOCATE_MISSING_HUB:
            case INVESTIGATE_DENSITY_CHANGE:
                return 3;
            case ONBOARD_NEW_NODES:
            case RE_ENGAGE_ISOLATED_NODES:
                return 2;
            default:
                return 1;
        }
    }

    private double computeHealthScore(List<Anomaly> anomalies) {
        if (anomalies.isEmpty()) return 100.0;
        double topSev = anomalies.get(0).severity;
        double restSum = 0;
        for (int i = 1; i < anomalies.size(); i++) restSum += anomalies.get(i).severity;
        return Math.max(0, Math.min(100, 100 - (topSev + 0.4 * Math.min(restSum, 60))));
    }

    private Grade gradeFromScore(double score) {
        if (score >= 85) return Grade.A;
        if (score >= 70) return Grade.B;
        if (score >= 55) return Grade.C;
        if (score >= 40) return Grade.D;
        return Grade.F;
    }

    private String inferTrajectory(List<SnapshotMetrics> metricsList) {
        if (metricsList.size() < 3) return "stable";
        int n = metricsList.size();
        double d1 = metricsList.get(n - 3).density;
        double d3 = metricsList.get(n - 1).density;
        double slope = (d3 - d1) / 2.0;
        if (slope > 0.005) return "improving";
        if (slope < -0.005) return "declining";
        return "stable";
    }

    private String buildHeadline(Grade grade, int anomalyCount, String trajectory) {
        if (anomalyCount == 0) return "grade=" + grade + " | No anomalies | Trajectory: " + trajectory;
        return "grade=" + grade + " | " + anomalyCount + " anomal" +
                (anomalyCount == 1 ? "y" : "ies") + " | Trajectory: " + trajectory;
    }

    private static double mean(double[] vals) {
        if (vals.length == 0) return 0;
        double s = 0;
        for (double v : vals) s += v;
        return s / vals.length;
    }

    private static double stddev(double[] vals, double mean) {
        if (vals.length <= 1) return 0;
        double s = 0;
        for (double v : vals) s += (v - mean) * (v - mean);
        return Math.sqrt(s / vals.length);
    }

    private static String jsonStr(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
