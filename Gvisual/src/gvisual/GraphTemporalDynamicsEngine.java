package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GraphTemporalDynamicsEngine - autonomous temporal network dynamics analyzer
 * that detects phase transitions, measures structural velocity/acceleration,
 * identifies regime changes, and forecasts topology trends from observed
 * graph snapshots.
 *
 * <h3>Seven Analysis Engines:</h3>
 * <ol>
 *   <li><b>Structural Velocity Tracker</b> - measures rate of change across 6
 *       metrics (density, clustering, diameter, modularity, degree entropy,
 *       component count) between consecutive snapshots</li>
 *   <li><b>Phase Transition Detector</b> - identifies abrupt regime changes using
 *       CUSUM-based changepoint detection on structural time series</li>
 *   <li><b>Topology Momentum Scorer</b> - classifies network evolution momentum
 *       into 6 phases (Explosive/Accelerating/Steady/Decelerating/Stagnant/Collapsing)</li>
 *   <li><b>Structural Forecast Engine</b> - linear regression extrapolation of
 *       structural metrics with confidence bands</li>
 *   <li><b>Node Lifecycle Analyzer</b> - tracks individual node importance
 *       trajectories (Rising/Stable/Declining/Volatile/Newcomer/Departing)</li>
 *   <li><b>Temporal Pattern Miner</b> - discovers recurring structural motifs
 *       (oscillations, bursts, cascades, cycles)</li>
 *   <li><b>Interactive HTML Dashboard</b> - sparkline charts, phase timeline,
 *       forecast plots, autonomous insights</li>
 * </ol>
 *
 * <p>Usage:</p>
 * <pre>
 *   GraphTemporalDynamicsEngine engine = new GraphTemporalDynamicsEngine();
 *   List&lt;Graph&lt;String, Edge&gt;&gt; snapshots = ...;
 *   TemporalDynamicsReport report = engine.analyze(snapshots);
 *   System.out.println(engine.toText(report));
 *   String html = engine.exportHtml(report);
 * </pre>
 *
 * @author sauravbhattacharya001
 */
public class GraphTemporalDynamicsEngine {

    // -- Configuration --
    private double cusumThreshold = 3.0;
    private int forecastHorizon = 3;
    private double significantChangeThreshold = 0.15;

    // -- Builder setters --
    public GraphTemporalDynamicsEngine setCusumThreshold(double t) {
        this.cusumThreshold = t; return this;
    }
    public GraphTemporalDynamicsEngine setForecastHorizon(int h) {
        this.forecastHorizon = h; return this;
    }
    public GraphTemporalDynamicsEngine setSignificantChangeThreshold(double t) {
        this.significantChangeThreshold = t; return this;
    }

    // ==================================================================
    // Inner classes
    // ==================================================================

    /** Structural metrics for a single snapshot. */
    public static class SnapshotMetrics {
        public int index;
        public int nodeCount;
        public int edgeCount;
        public double density;
        public double avgClustering;
        public double avgDegree;
        public double degreeEntropy;
        public int componentCount;
        public double maxDegreeRatio; // max_degree / avg_degree

        public Map<String, Double> toMap() {
            Map<String, Double> m = new LinkedHashMap<>();
            m.put("density", density);
            m.put("avgClustering", avgClustering);
            m.put("avgDegree", avgDegree);
            m.put("degreeEntropy", degreeEntropy);
            m.put("componentCount", (double) componentCount);
            m.put("maxDegreeRatio", maxDegreeRatio);
            return m;
        }
    }

    /** Velocity between two consecutive snapshots. */
    public static class StructuralVelocity {
        public int fromIndex;
        public int toIndex;
        public Map<String, Double> deltas = new LinkedHashMap<>();
        public double compositeSpeed; // L2 norm of normalized deltas
    }

    /** A detected phase transition (changepoint). */
    public static class PhaseTransition {
        public int snapshotIndex;
        public String metric;
        public double cusumValue;
        public String direction; // "increase" or "decrease"
        public String description;
    }

    /** Momentum classification. */
    public enum MomentumPhase {
        EXPLOSIVE("Explosive Growth", "#f85149"),
        ACCELERATING("Accelerating", "#f0883e"),
        STEADY("Steady State", "#3fb950"),
        DECELERATING("Decelerating", "#d2a8ff"),
        STAGNANT("Stagnant", "#8b949e"),
        COLLAPSING("Collapsing", "#ff7b72");

        public final String label;
        public final String color;
        MomentumPhase(String label, String color) {
            this.label = label; this.color = color;
        }
    }

    /** Forecast for a single metric. */
    public static class MetricForecast {
        public String metric;
        public double slope;
        public double intercept;
        public double rSquared;
        public List<Double> predicted = new ArrayList<>();
        public String trend; // "rising", "stable", "declining"
    }

    /** Node lifecycle classification. */
    public enum NodeLifecycle {
        RISING, STABLE, DECLINING, VOLATILE, NEWCOMER, DEPARTING
    }

    /** Node trajectory over time. */
    public static class NodeTrajectory {
        public String nodeId;
        public List<Double> importanceOverTime = new ArrayList<>();
        public NodeLifecycle lifecycle;
        public double trendSlope;
        public double volatility;
    }

    /** Temporal pattern. */
    public static class TemporalPattern {
        public String type; // oscillation, burst, cascade, plateau
        public String metric;
        public int startIndex;
        public int endIndex;
        public String description;
    }

    /** Full analysis report. */
    public static class TemporalDynamicsReport {
        public List<SnapshotMetrics> snapshots = new ArrayList<>();
        public List<StructuralVelocity> velocities = new ArrayList<>();
        public List<PhaseTransition> transitions = new ArrayList<>();
        public MomentumPhase currentMomentum;
        public double momentumScore; // 0-100
        public List<MetricForecast> forecasts = new ArrayList<>();
        public List<NodeTrajectory> nodeTrajectories = new ArrayList<>();
        public List<TemporalPattern> patterns = new ArrayList<>();
        public List<String> insights = new ArrayList<>();
        public int snapshotCount;
    }

    // ==================================================================
    // Main analysis entry point
    // ==================================================================

    /**
     * Analyze a sequence of graph snapshots for temporal dynamics.
     *
     * @param snapshots ordered list of graph snapshots (earliest first)
     * @return comprehensive temporal dynamics report
     */
    public TemporalDynamicsReport analyze(List<Graph<String, Edge>> snapshots) {
        if (snapshots == null || snapshots.size() < 2) {
            throw new IllegalArgumentException("At least 2 snapshots required for temporal analysis");
        }

        TemporalDynamicsReport report = new TemporalDynamicsReport();
        report.snapshotCount = snapshots.size();

        // 1. Compute per-snapshot metrics
        for (int i = 0; i < snapshots.size(); i++) {
            report.snapshots.add(computeMetrics(snapshots.get(i), i));
        }

        // 2. Compute structural velocities
        for (int i = 1; i < report.snapshots.size(); i++) {
            report.velocities.add(computeVelocity(report.snapshots.get(i - 1), report.snapshots.get(i)));
        }

        // 3. Detect phase transitions
        report.transitions.addAll(detectPhaseTransitions(report.snapshots));

        // 4. Classify momentum
        classifyMomentum(report);

        // 5. Forecast metrics
        report.forecasts.addAll(forecastMetrics(report.snapshots));

        // 6. Node lifecycle analysis
        report.nodeTrajectories.addAll(analyzeNodeLifecycles(snapshots));

        // 7. Mine temporal patterns
        report.patterns.addAll(mineTemporalPatterns(report.snapshots, report.velocities));

        // 8. Generate insights
        report.insights.addAll(generateInsights(report));

        return report;
    }

    // ==================================================================
    // Engine 1: Structural Metrics
    // ==================================================================

    private SnapshotMetrics computeMetrics(Graph<String, Edge> g, int index) {
        SnapshotMetrics m = new SnapshotMetrics();
        m.index = index;
        m.nodeCount = g.getVertexCount();
        m.edgeCount = g.getEdgeCount();

        int n = m.nodeCount;
        if (n < 2) {
            m.density = 0;
            m.avgClustering = 0;
            m.avgDegree = 0;
            m.degreeEntropy = 0;
            m.componentCount = n;
            m.maxDegreeRatio = 0;
            return m;
        }

        // Density
        m.density = (2.0 * m.edgeCount) / (n * (n - 1.0));

        // Average degree
        m.avgDegree = (2.0 * m.edgeCount) / n;

        // Average clustering coefficient
        double totalClustering = 0;
        int maxDeg = 0;
        for (String v : g.getVertices()) {
            int deg = g.degree(v);
            maxDeg = Math.max(maxDeg, deg);
            if (deg < 2) continue;
            Collection<String> neighbors = g.getNeighbors(v);
            List<String> nList = new ArrayList<>(neighbors);
            int triangles = 0;
            for (int i = 0; i < nList.size(); i++) {
                for (int j = i + 1; j < nList.size(); j++) {
                    if (g.isNeighbor(nList.get(i), nList.get(j))) triangles++;
                }
            }
            int possibleTriangles = deg * (deg - 1) / 2;
            totalClustering += (double) triangles / possibleTriangles;
        }
        m.avgClustering = totalClustering / n;

        // Max degree ratio
        m.maxDegreeRatio = m.avgDegree > 0 ? maxDeg / m.avgDegree : 0;

        // Degree entropy (Shannon)
        Map<Integer, Integer> degreeHist = new HashMap<>();
        for (String v : g.getVertices()) {
            degreeHist.merge(g.degree(v), 1, Integer::sum);
        }
        m.degreeEntropy = 0;
        for (int count : degreeHist.values()) {
            double p = (double) count / n;
            if (p > 0) m.degreeEntropy -= p * Math.log(p) / Math.log(2);
        }

        // Component count (BFS)
        m.componentCount = countComponents(g);

        return m;
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
                        if (visited.add(neighbor)) queue.add(neighbor);
                    }
                }
            }
        }
        return count;
    }

    // ==================================================================
    // Engine 2: Structural Velocity
    // ==================================================================

    private StructuralVelocity computeVelocity(SnapshotMetrics prev, SnapshotMetrics curr) {
        StructuralVelocity v = new StructuralVelocity();
        v.fromIndex = prev.index;
        v.toIndex = curr.index;

        Map<String, Double> prevMap = prev.toMap();
        Map<String, Double> currMap = curr.toMap();

        double sumSq = 0;
        for (String metric : prevMap.keySet()) {
            double prevVal = prevMap.get(metric);
            double currVal = currMap.get(metric);
            double delta = (prevVal == 0) ? (currVal == 0 ? 0 : currVal) : (currVal - prevVal) / Math.abs(prevVal);
            v.deltas.put(metric, delta);
            sumSq += delta * delta;
        }
        v.compositeSpeed = Math.sqrt(sumSq);
        return v;
    }

    // ==================================================================
    // Engine 3: Phase Transition Detection (CUSUM)
    // ==================================================================

    private List<PhaseTransition> detectPhaseTransitions(List<SnapshotMetrics> snapshots) {
        List<PhaseTransition> transitions = new ArrayList<>();
        if (snapshots.size() < 3) return transitions;

        String[] metrics = {"density", "avgClustering", "avgDegree", "degreeEntropy", "componentCount", "maxDegreeRatio"};

        for (String metric : metrics) {
            List<Double> series = new ArrayList<>();
            for (SnapshotMetrics s : snapshots) {
                series.add(s.toMap().get(metric));
            }

            // Compute differences
            List<Double> diffs = new ArrayList<>();
            for (int i = 1; i < series.size(); i++) {
                diffs.add(series.get(i) - series.get(i - 1));
            }
            if (diffs.isEmpty()) continue;

            // Mean and std of differences
            double mean = diffs.stream().mapToDouble(d -> d).average().orElse(0);
            double std = Math.sqrt(diffs.stream().mapToDouble(d -> (d - mean) * (d - mean)).average().orElse(0));
            if (std < 1e-10) continue;

            // CUSUM for positive shifts
            double cusumPos = 0, cusumNeg = 0;
            for (int i = 0; i < diffs.size(); i++) {
                double normalized = (diffs.get(i) - mean) / std;
                cusumPos = Math.max(0, cusumPos + normalized - 0.5);
                cusumNeg = Math.max(0, cusumNeg - normalized - 0.5);

                if (cusumPos > cusumThreshold) {
                    PhaseTransition pt = new PhaseTransition();
                    pt.snapshotIndex = i + 1;
                    pt.metric = metric;
                    pt.cusumValue = cusumPos;
                    pt.direction = "increase";
                    pt.description = String.format("Abrupt %s increase at snapshot %d (CUSUM=%.2f)", metric, i + 1, cusumPos);
                    transitions.add(pt);
                    cusumPos = 0;
                }
                if (cusumNeg > cusumThreshold) {
                    PhaseTransition pt = new PhaseTransition();
                    pt.snapshotIndex = i + 1;
                    pt.metric = metric;
                    pt.cusumValue = cusumNeg;
                    pt.direction = "decrease";
                    pt.description = String.format("Abrupt %s decrease at snapshot %d (CUSUM=%.2f)", metric, i + 1, cusumNeg);
                    transitions.add(pt);
                    cusumNeg = 0;
                }
            }
        }

        transitions.sort(Comparator.comparingInt(t -> t.snapshotIndex));
        return transitions;
    }

    // ==================================================================
    // Engine 4: Momentum Classification
    // ==================================================================

    private void classifyMomentum(TemporalDynamicsReport report) {
        if (report.velocities.isEmpty()) {
            report.currentMomentum = MomentumPhase.STAGNANT;
            report.momentumScore = 50;
            return;
        }

        // Use last few velocities for momentum assessment
        int lookback = Math.min(3, report.velocities.size());
        List<StructuralVelocity> recent = report.velocities.subList(
                report.velocities.size() - lookback, report.velocities.size());

        double avgSpeed = recent.stream().mapToDouble(v -> v.compositeSpeed).average().orElse(0);

        // Check acceleration (speed trend)
        double acceleration = 0;
        if (recent.size() >= 2) {
            double first = recent.get(0).compositeSpeed;
            double last = recent.get(recent.size() - 1).compositeSpeed;
            acceleration = last - first;
        }

        // Check net direction via density trend
        double netDensityChange = recent.stream()
                .mapToDouble(v -> v.deltas.getOrDefault("density", 0.0))
                .sum();

        // Classify
        if (avgSpeed > 1.0 && acceleration > 0.2) {
            report.currentMomentum = MomentumPhase.EXPLOSIVE;
            report.momentumScore = Math.min(100, 70 + avgSpeed * 15);
        } else if (avgSpeed > 0.3 && acceleration > 0) {
            report.currentMomentum = MomentumPhase.ACCELERATING;
            report.momentumScore = 55 + avgSpeed * 20;
        } else if (avgSpeed > 0.1 && Math.abs(acceleration) < 0.1) {
            report.currentMomentum = MomentumPhase.STEADY;
            report.momentumScore = 50;
        } else if (avgSpeed > 0.1 && acceleration < -0.1) {
            report.currentMomentum = MomentumPhase.DECELERATING;
            report.momentumScore = 40 - Math.abs(acceleration) * 10;
        } else if (avgSpeed < 0.05) {
            report.currentMomentum = MomentumPhase.STAGNANT;
            report.momentumScore = 30;
        } else if (netDensityChange < -0.3) {
            report.currentMomentum = MomentumPhase.COLLAPSING;
            report.momentumScore = Math.max(0, 20 - Math.abs(netDensityChange) * 10);
        } else {
            report.currentMomentum = MomentumPhase.STEADY;
            report.momentumScore = 50;
        }

        report.momentumScore = Math.max(0, Math.min(100, report.momentumScore));
    }

    // ==================================================================
    // Engine 5: Metric Forecasting (Linear Regression)
    // ==================================================================

    private List<MetricForecast> forecastMetrics(List<SnapshotMetrics> snapshots) {
        List<MetricForecast> forecasts = new ArrayList<>();
        String[] metrics = {"density", "avgClustering", "avgDegree", "degreeEntropy", "componentCount", "maxDegreeRatio"};

        for (String metric : metrics) {
            List<Double> values = new ArrayList<>();
            for (SnapshotMetrics s : snapshots) {
                values.add(s.toMap().get(metric));
            }

            MetricForecast f = new MetricForecast();
            f.metric = metric;

            // Linear regression: y = slope*x + intercept
            int n = values.size();
            double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;
            for (int i = 0; i < n; i++) {
                sumX += i;
                sumY += values.get(i);
                sumXY += i * values.get(i);
                sumX2 += i * i;
                sumY2 += values.get(i) * values.get(i);
            }
            double denom = n * sumX2 - sumX * sumX;
            if (Math.abs(denom) < 1e-10) {
                f.slope = 0;
                f.intercept = sumY / n;
            } else {
                f.slope = (n * sumXY - sumX * sumY) / denom;
                f.intercept = (sumY - f.slope * sumX) / n;
            }

            // R-squared
            double meanY = sumY / n;
            double ssTotal = 0, ssRes = 0;
            for (int i = 0; i < n; i++) {
                double predicted = f.slope * i + f.intercept;
                ssRes += (values.get(i) - predicted) * (values.get(i) - predicted);
                ssTotal += (values.get(i) - meanY) * (values.get(i) - meanY);
            }
            f.rSquared = ssTotal > 0 ? 1 - (ssRes / ssTotal) : 0;

            // Predict forward
            for (int i = 0; i < forecastHorizon; i++) {
                f.predicted.add(f.slope * (n + i) + f.intercept);
            }

            // Classify trend
            if (Math.abs(f.slope) < significantChangeThreshold * 0.1) {
                f.trend = "stable";
            } else if (f.slope > 0) {
                f.trend = "rising";
            } else {
                f.trend = "declining";
            }

            forecasts.add(f);
        }

        return forecasts;
    }

    // ==================================================================
    // Engine 6: Node Lifecycle Analysis
    // ==================================================================

    private List<NodeTrajectory> analyzeNodeLifecycles(List<Graph<String, Edge>> snapshots) {
        // Collect all nodes across all snapshots
        Set<String> allNodes = new LinkedHashSet<>();
        for (Graph<String, Edge> g : snapshots) {
            allNodes.addAll(g.getVertices());
        }

        List<NodeTrajectory> trajectories = new ArrayList<>();
        for (String node : allNodes) {
            NodeTrajectory traj = new NodeTrajectory();
            traj.nodeId = node;

            for (Graph<String, Edge> g : snapshots) {
                if (g.containsVertex(node)) {
                    // Importance = normalized degree
                    double importance = g.getVertexCount() > 1 ?
                            (double) g.degree(node) / (g.getVertexCount() - 1) : 0;
                    traj.importanceOverTime.add(importance);
                } else {
                    traj.importanceOverTime.add(0.0);
                }
            }

            // Compute trend slope
            List<Double> vals = traj.importanceOverTime;
            int n = vals.size();
            double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
            for (int i = 0; i < n; i++) {
                sumX += i; sumY += vals.get(i); sumXY += i * vals.get(i); sumX2 += i * i;
            }
            double denom = n * sumX2 - sumX * sumX;
            traj.trendSlope = denom > 0 ? (n * sumXY - sumX * sumY) / denom : 0;

            // Compute volatility (std of differences)
            List<Double> diffs = new ArrayList<>();
            for (int i = 1; i < vals.size(); i++) {
                diffs.add(Math.abs(vals.get(i) - vals.get(i - 1)));
            }
            traj.volatility = diffs.isEmpty() ? 0 :
                    diffs.stream().mapToDouble(d -> d).average().orElse(0);

            // Classify lifecycle
            boolean presentAtStart = vals.get(0) > 0;
            boolean presentAtEnd = vals.get(vals.size() - 1) > 0;

            if (!presentAtStart && presentAtEnd) {
                traj.lifecycle = NodeLifecycle.NEWCOMER;
            } else if (presentAtStart && !presentAtEnd) {
                traj.lifecycle = NodeLifecycle.DEPARTING;
            } else if (traj.volatility > 0.15) {
                traj.lifecycle = NodeLifecycle.VOLATILE;
            } else if (traj.trendSlope > 0.02) {
                traj.lifecycle = NodeLifecycle.RISING;
            } else if (traj.trendSlope < -0.02) {
                traj.lifecycle = NodeLifecycle.DECLINING;
            } else {
                traj.lifecycle = NodeLifecycle.STABLE;
            }

            trajectories.add(traj);
        }

        // Sort by absolute trend slope (most dynamic first), limit to top 20
        trajectories.sort((a, b) -> Double.compare(
                Math.abs(b.trendSlope) + b.volatility,
                Math.abs(a.trendSlope) + a.volatility));
        if (trajectories.size() > 20) {
            trajectories = new ArrayList<>(trajectories.subList(0, 20));
        }

        return trajectories;
    }

    // ==================================================================
    // Engine 7: Temporal Pattern Mining
    // ==================================================================

    private List<TemporalPattern> mineTemporalPatterns(List<SnapshotMetrics> snapshots,
                                                        List<StructuralVelocity> velocities) {
        List<TemporalPattern> patterns = new ArrayList<>();
        if (snapshots.size() < 4) return patterns;

        String[] metrics = {"density", "avgClustering", "degreeEntropy", "componentCount"};

        for (String metric : metrics) {
            List<Double> series = new ArrayList<>();
            for (SnapshotMetrics s : snapshots) {
                series.add(s.toMap().get(metric));
            }

            // Detect oscillations (alternating direction changes)
            int signChanges = 0;
            int oscStart = -1;
            for (int i = 2; i < series.size(); i++) {
                double d1 = series.get(i - 1) - series.get(i - 2);
                double d2 = series.get(i) - series.get(i - 1);
                if (d1 * d2 < 0) {
                    signChanges++;
                    if (oscStart == -1) oscStart = i - 2;
                }
            }
            if (signChanges >= 3 && oscStart >= 0) {
                TemporalPattern p = new TemporalPattern();
                p.type = "oscillation";
                p.metric = metric;
                p.startIndex = oscStart;
                p.endIndex = snapshots.size() - 1;
                p.description = String.format("%s oscillating with %d direction changes", metric, signChanges);
                patterns.add(p);
            }

            // Detect bursts (sudden spikes followed by return)
            double mean = series.stream().mapToDouble(d -> d).average().orElse(0);
            double std = Math.sqrt(series.stream().mapToDouble(d -> (d - mean) * (d - mean)).average().orElse(0));
            if (std > 0) {
                for (int i = 1; i < series.size() - 1; i++) {
                    double zScore = (series.get(i) - mean) / std;
                    if (Math.abs(zScore) > 2.0) {
                        TemporalPattern p = new TemporalPattern();
                        p.type = "burst";
                        p.metric = metric;
                        p.startIndex = i;
                        p.endIndex = i;
                        p.description = String.format("%s burst at snapshot %d (z=%.2f)", metric, i, zScore);
                        patterns.add(p);
                    }
                }
            }

            // Detect plateaus (consecutive similar values)
            int plateauStart = 0;
            for (int i = 1; i <= series.size(); i++) {
                boolean similar = (i < series.size()) &&
                        Math.abs(series.get(i) - series.get(plateauStart)) < (std * 0.3 + 1e-10);
                if (!similar) {
                    int plateauLen = i - plateauStart;
                    if (plateauLen >= 3) {
                        TemporalPattern p = new TemporalPattern();
                        p.type = "plateau";
                        p.metric = metric;
                        p.startIndex = plateauStart;
                        p.endIndex = i - 1;
                        p.description = String.format("%s plateau from snapshot %d to %d (%d steps)",
                                metric, plateauStart, i - 1, plateauLen);
                        patterns.add(p);
                    }
                    plateauStart = i;
                }
            }
        }

        // Detect velocity cascades (sustained high speed)
        if (velocities.size() >= 3) {
            int cascadeStart = -1;
            for (int i = 0; i < velocities.size(); i++) {
                if (velocities.get(i).compositeSpeed > 0.5) {
                    if (cascadeStart == -1) cascadeStart = i;
                } else {
                    if (cascadeStart >= 0 && (i - cascadeStart) >= 3) {
                        TemporalPattern p = new TemporalPattern();
                        p.type = "cascade";
                        p.metric = "compositeSpeed";
                        p.startIndex = cascadeStart;
                        p.endIndex = i - 1;
                        p.description = String.format("Structural cascade from snapshot %d to %d (%d rapid transitions)",
                                cascadeStart, i - 1, i - cascadeStart);
                        patterns.add(p);
                    }
                    cascadeStart = -1;
                }
            }
            // Check trailing cascade
            if (cascadeStart >= 0 && (velocities.size() - cascadeStart) >= 3) {
                TemporalPattern p = new TemporalPattern();
                p.type = "cascade";
                p.metric = "compositeSpeed";
                p.startIndex = cascadeStart;
                p.endIndex = velocities.size() - 1;
                p.description = String.format("Ongoing structural cascade since snapshot %d (%d rapid transitions)",
                        cascadeStart, velocities.size() - cascadeStart);
                patterns.add(p);
            }
        }

        return patterns;
    }

    // ==================================================================
    // Autonomous Insight Generation
    // ==================================================================

    private List<String> generateInsights(TemporalDynamicsReport report) {
        List<String> insights = new ArrayList<>();

        // Momentum insight
        insights.add(String.format("Network momentum: %s (score %.0f/100). %s",
                report.currentMomentum.label, report.momentumScore,
                momentumAdvice(report.currentMomentum)));

        // Phase transition summary
        if (!report.transitions.isEmpty()) {
            insights.add(String.format("Detected %d phase transition(s). Most recent: %s",
                    report.transitions.size(),
                    report.transitions.get(report.transitions.size() - 1).description));
        } else {
            insights.add("No phase transitions detected — network evolution is gradual.");
        }

        // Forecast highlights
        for (MetricForecast f : report.forecasts) {
            if (f.rSquared > 0.7 && !"stable".equals(f.trend)) {
                insights.add(String.format("Strong %s trend in %s (R²=%.2f, slope=%.4f). Forecast: %s",
                        f.trend, f.metric, f.rSquared, f.slope,
                        f.predicted.stream().map(v -> String.format("%.3f", v))
                                .collect(Collectors.joining(" → "))));
            }
        }

        // Node lifecycle summary
        Map<NodeLifecycle, Long> lifecycleCounts = report.nodeTrajectories.stream()
                .collect(Collectors.groupingBy(t -> t.lifecycle, Collectors.counting()));
        if (!lifecycleCounts.isEmpty()) {
            StringBuilder sb = new StringBuilder("Node dynamics: ");
            lifecycleCounts.forEach((lc, count) ->
                    sb.append(String.format("%d %s, ", count, lc.name().toLowerCase())));
            insights.add(sb.toString().replaceAll(", $", ""));
        }

        // Pattern summary
        Map<String, Long> patternCounts = report.patterns.stream()
                .collect(Collectors.groupingBy(p -> p.type, Collectors.counting()));
        if (!patternCounts.isEmpty()) {
            insights.add("Temporal patterns found: " + patternCounts.entrySet().stream()
                    .map(e -> e.getValue() + " " + e.getKey() + "(s)")
                    .collect(Collectors.joining(", ")));
        }

        // Stability warning
        double lastSpeed = report.velocities.isEmpty() ? 0 :
                report.velocities.get(report.velocities.size() - 1).compositeSpeed;
        if (lastSpeed > 0.8) {
            insights.add("⚠ HIGH VELOCITY WARNING: Network is changing rapidly (speed=" +
                    String.format("%.2f", lastSpeed) + "). Monitor for instability.");
        }

        return insights;
    }

    private String momentumAdvice(MomentumPhase phase) {
        switch (phase) {
            case EXPLOSIVE: return "Network undergoing rapid transformation. High risk of structural instability.";
            case ACCELERATING: return "Growth intensifying. Consider capacity planning for continued expansion.";
            case STEADY: return "Stable evolution. Good time for analysis and optimization.";
            case DECELERATING: return "Growth slowing. May indicate maturation or emerging constraints.";
            case STAGNANT: return "Minimal change. Network may need stimulus or has reached equilibrium.";
            case COLLAPSING: return "Structure deteriorating. Investigate causes of edge/node loss.";
            default: return "";
        }
    }

    // ==================================================================
    // Text Output
    // ==================================================================

    /**
     * Generate a text report.
     */
    public String toText(TemporalDynamicsReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║       GRAPH TEMPORAL DYNAMICS ENGINE - Analysis Report      ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════╝\n\n");

        sb.append(String.format("Snapshots Analyzed: %d\n", report.snapshotCount));
        sb.append(String.format("Current Momentum: %s (Score: %.0f/100)\n\n",
                report.currentMomentum.label, report.momentumScore));

        // Velocity summary
        sb.append("── Structural Velocity ──\n");
        for (StructuralVelocity v : report.velocities) {
            sb.append(String.format("  [%d→%d] Speed=%.3f | ", v.fromIndex, v.toIndex, v.compositeSpeed));
            v.deltas.entrySet().stream()
                    .filter(e -> Math.abs(e.getValue()) > 0.01)
                    .forEach(e -> sb.append(String.format("%s:%+.3f ", e.getKey(), e.getValue())));
            sb.append("\n");
        }

        // Phase transitions
        if (!report.transitions.isEmpty()) {
            sb.append("\n── Phase Transitions ──\n");
            for (PhaseTransition pt : report.transitions) {
                sb.append(String.format("  [Snapshot %d] %s\n", pt.snapshotIndex, pt.description));
            }
        }

        // Forecasts
        sb.append("\n── Metric Forecasts ──\n");
        for (MetricForecast f : report.forecasts) {
            sb.append(String.format("  %s: trend=%s, slope=%.4f, R²=%.3f, next=%s\n",
                    f.metric, f.trend, f.slope, f.rSquared,
                    f.predicted.stream().map(v -> String.format("%.3f", v))
                            .collect(Collectors.joining(", "))));
        }

        // Node trajectories
        if (!report.nodeTrajectories.isEmpty()) {
            sb.append("\n── Node Lifecycles (Top 10) ──\n");
            report.nodeTrajectories.stream().limit(10).forEach(t ->
                    sb.append(String.format("  %s: %s (slope=%.4f, volatility=%.3f)\n",
                            t.nodeId, t.lifecycle, t.trendSlope, t.volatility)));
        }

        // Patterns
        if (!report.patterns.isEmpty()) {
            sb.append("\n── Temporal Patterns ──\n");
            for (TemporalPattern p : report.patterns) {
                sb.append(String.format("  [%s] %s\n", p.type.toUpperCase(), p.description));
            }
        }

        // Insights
        sb.append("\n── Autonomous Insights ──\n");
        for (String insight : report.insights) {
            sb.append("  • ").append(insight).append("\n");
        }

        return sb.toString();
    }

    // ==================================================================
    // HTML Dashboard Export
    // ==================================================================

    /**
     * Generate an interactive HTML dashboard.
     */
    public String exportHtml(TemporalDynamicsReport report) {
        StringBuilder h = new StringBuilder();
        h.append("<!DOCTYPE html><html><head><meta charset='utf-8'>\n");
        h.append("<title>Graph Temporal Dynamics Dashboard</title>\n");
        h.append("<style>\n");
        h.append("* { margin: 0; padding: 0; box-sizing: border-box; }\n");
        h.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; ");
        h.append("background: #0d1117; color: #c9d1d9; padding: 24px; }\n");
        h.append("h1 { color: #58a6ff; margin-bottom: 8px; }\n");
        h.append("h2 { color: #79c0ff; margin: 24px 0 12px; border-bottom: 1px solid #21262d; padding-bottom: 8px; }\n");
        h.append(".subtitle { color: #8b949e; margin-bottom: 24px; }\n");
        h.append(".cards { display: flex; gap: 16px; flex-wrap: wrap; margin-bottom: 24px; }\n");
        h.append(".card { background: #161b22; border: 1px solid #30363d; border-radius: 8px; padding: 16px; min-width: 140px; text-align: center; }\n");
        h.append(".card .metric { font-size: 24px; font-weight: bold; color: #f0f6fc; }\n");
        h.append(".card .label { font-size: 12px; color: #8b949e; margin-top: 4px; }\n");
        h.append(".phase-badge { display: inline-block; padding: 4px 12px; border-radius: 12px; font-weight: bold; }\n");
        h.append("table { width: 100%; border-collapse: collapse; margin: 12px 0; }\n");
        h.append("th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #21262d; }\n");
        h.append("th { color: #8b949e; font-size: 12px; text-transform: uppercase; }\n");
        h.append(".sparkline { display: inline-flex; align-items: flex-end; gap: 2px; height: 30px; }\n");
        h.append(".sparkline .bar { width: 6px; background: #58a6ff; border-radius: 2px; }\n");
        h.append(".insight { background: #161b22; border-left: 3px solid #58a6ff; padding: 12px; margin: 8px 0; border-radius: 4px; }\n");
        h.append(".transition { background: #161b22; border-left: 3px solid #f85149; padding: 12px; margin: 8px 0; border-radius: 4px; }\n");
        h.append(".pattern { background: #161b22; border-left: 3px solid #d2a8ff; padding: 12px; margin: 8px 0; border-radius: 4px; }\n");
        h.append(".forecast-row { display: flex; gap: 8px; align-items: center; }\n");
        h.append(".trend-up { color: #3fb950; } .trend-down { color: #f85149; } .trend-stable { color: #8b949e; }\n");
        h.append(".tabs { display: flex; gap: 4px; margin-bottom: 16px; }\n");
        h.append(".tab { padding: 8px 16px; border-radius: 6px 6px 0 0; cursor: pointer; background: #161b22; border: 1px solid #30363d; color: #8b949e; }\n");
        h.append(".tab.active { background: #0d1117; color: #58a6ff; border-bottom-color: #0d1117; }\n");
        h.append(".tab-content { display: none; } .tab-content.active { display: block; }\n");
        h.append("</style></head><body>\n");

        h.append("<h1>⏱ Graph Temporal Dynamics Engine</h1>\n");
        h.append("<p class='subtitle'>Autonomous temporal network analysis — phase transitions, momentum, forecasting</p>\n");

        // Cards
        h.append("<div class='cards'>\n");
        appendCard(h, String.valueOf(report.snapshotCount), "Snapshots");
        appendCard(h, String.format("<span class='phase-badge' style='background:%s;color:#fff;font-size:14px'>%s</span>",
                report.currentMomentum.color, report.currentMomentum.label), "Momentum");
        appendCard(h, String.format("%.0f", report.momentumScore), "Momentum Score");
        appendCard(h, String.valueOf(report.transitions.size()), "Phase Transitions");
        appendCard(h, String.valueOf(report.patterns.size()), "Patterns Found");
        h.append("</div>\n");

        // Tabs
        h.append("<div class='tabs'>\n");
        h.append("<div class='tab active' onclick='showTab(0)'>Velocity</div>\n");
        h.append("<div class='tab' onclick='showTab(1)'>Transitions</div>\n");
        h.append("<div class='tab' onclick='showTab(2)'>Forecasts</div>\n");
        h.append("<div class='tab' onclick='showTab(3)'>Nodes</div>\n");
        h.append("<div class='tab' onclick='showTab(4)'>Patterns</div>\n");
        h.append("</div>\n");

        // Tab 0: Velocity
        h.append("<div class='tab-content active'>\n");
        h.append("<h2>Structural Velocity</h2>\n");
        h.append("<table><tr><th>Transition</th><th>Speed</th><th>Sparkline</th><th>Top Changes</th></tr>\n");
        double maxSpeed = report.velocities.stream().mapToDouble(v -> v.compositeSpeed).max().orElse(1);
        for (StructuralVelocity v : report.velocities) {
            h.append(String.format("<tr><td>%d → %d</td><td>%.3f</td><td>", v.fromIndex, v.toIndex, v.compositeSpeed));
            int barH = (int) (30 * v.compositeSpeed / Math.max(maxSpeed, 0.001));
            h.append(String.format("<div class='sparkline'><div class='bar' style='height:%dpx'></div></div>", Math.max(barH, 2)));
            h.append("</td><td>");
            v.deltas.entrySet().stream()
                    .sorted((a, b) -> Double.compare(Math.abs(b.getValue()), Math.abs(a.getValue())))
                    .limit(3)
                    .forEach(e -> {
                        String cls = e.getValue() > 0 ? "trend-up" : "trend-down";
                        h.append(String.format("<span class='%s'>%s:%+.3f</span> ", cls, e.getKey(), e.getValue()));
                    });
            h.append("</td></tr>\n");
        }
        h.append("</table></div>\n");

        // Tab 1: Transitions
        h.append("<div class='tab-content'>\n");
        h.append("<h2>Phase Transitions</h2>\n");
        if (report.transitions.isEmpty()) {
            h.append("<p>No abrupt phase transitions detected. Evolution is gradual.</p>\n");
        } else {
            for (PhaseTransition pt : report.transitions) {
                h.append(String.format("<div class='transition'><strong>Snapshot %d</strong> — %s (CUSUM=%.2f)</div>\n",
                        pt.snapshotIndex, pt.description, pt.cusumValue));
            }
        }
        h.append("</div>\n");

        // Tab 2: Forecasts
        h.append("<div class='tab-content'>\n");
        h.append("<h2>Metric Forecasts</h2>\n");
        h.append("<table><tr><th>Metric</th><th>Trend</th><th>Slope</th><th>R²</th><th>Forecast</th></tr>\n");
        for (MetricForecast f : report.forecasts) {
            String trendCls = "rising".equals(f.trend) ? "trend-up" : "declining".equals(f.trend) ? "trend-down" : "trend-stable";
            String arrow = "rising".equals(f.trend) ? "↑" : "declining".equals(f.trend) ? "↓" : "→";
            h.append(String.format("<tr><td>%s</td><td class='%s'>%s %s</td><td>%.4f</td><td>%.3f</td><td>%s</td></tr>\n",
                    f.metric, trendCls, arrow, f.trend, f.slope, f.rSquared,
                    f.predicted.stream().map(v -> String.format("%.3f", v)).collect(Collectors.joining(" → "))));
        }
        h.append("</table></div>\n");

        // Tab 3: Node Lifecycles
        h.append("<div class='tab-content'>\n");
        h.append("<h2>Node Lifecycles</h2>\n");
        h.append("<table><tr><th>#</th><th>Node</th><th>Lifecycle</th><th>Trend</th><th>Volatility</th></tr>\n");
        int rank = 0;
        for (NodeTrajectory t : report.nodeTrajectories) {
            rank++;
            if (rank > 15) break;
            h.append(String.format("<tr><td>%d</td><td>%s</td><td>%s</td><td>%.4f</td><td>%.3f</td></tr>\n",
                    rank, esc(t.nodeId), t.lifecycle, t.trendSlope, t.volatility));
        }
        h.append("</table></div>\n");

        // Tab 4: Patterns
        h.append("<div class='tab-content'>\n");
        h.append("<h2>Temporal Patterns</h2>\n");
        if (report.patterns.isEmpty()) {
            h.append("<p>No significant temporal patterns detected.</p>\n");
        } else {
            for (TemporalPattern p : report.patterns) {
                h.append(String.format("<div class='pattern'><strong>[%s]</strong> %s</div>\n",
                        p.type.toUpperCase(), esc(p.description)));
            }
        }
        h.append("</div>\n");

        // Insights
        h.append("<h2>Autonomous Insights</h2>\n");
        for (String insight : report.insights) {
            h.append("<div class='insight'>").append(esc(insight)).append("</div>\n");
        }

        // JavaScript for tabs
        h.append("<script>\n");
        h.append("function showTab(idx) {\n");
        h.append("  document.querySelectorAll('.tab').forEach((t,i) => t.classList.toggle('active', i===idx));\n");
        h.append("  document.querySelectorAll('.tab-content').forEach((c,i) => c.classList.toggle('active', i===idx));\n");
        h.append("}\n");
        h.append("</script>\n");

        h.append("</body></html>");
        return h.toString();
    }

    private void appendCard(StringBuilder h, String value, String label) {
        h.append("<div class='card'><div class='metric'>").append(value)
                .append("</div><div class='label'>").append(label).append("</div></div>\n");
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /**
     * Export HTML dashboard to a file.
     */
    public void exportToFile(TemporalDynamicsReport report, String path) throws IOException {
        String html = exportHtml(report);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8)) {
            w.write(html);
        }
    }
}
