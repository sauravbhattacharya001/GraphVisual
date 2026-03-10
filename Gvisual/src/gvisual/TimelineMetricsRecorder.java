package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.io.*;
import java.util.*;

/**
 * Records network metrics across timeline steps and provides trend analysis.
 *
 * <p>Captures per-step snapshots of graph statistics (node count, edge count,
 * density, degree stats, category breakdowns) and exports them as CSV.
 * Computes growth rates, peak detection, phase transitions, and summary
 * statistics to help researchers understand how the social network
 * evolves over time.</p>
 *
 * <p>Usage:
 * <pre>
 *   TimelineMetricsRecorder recorder = new TimelineMetricsRecorder();
 *   // At each timeline step:
 *   recorder.recordStep(stepLabel, graph, friendEdges, fsEdges, ...);
 *   // After recording:
 *   recorder.exportCsv(new File("metrics.csv"));
 *   TrendReport report = recorder.analyzeTrends();
 * </pre>
 * </p>
 *
 * @author zalenix
 */
public class TimelineMetricsRecorder {

    /** Snapshot of metrics at a single timeline step. */
    public static class StepSnapshot {
        private final String label;
        private final int stepIndex;
        private final int nodeCount;
        private final int edgeCount;
        private final int friendCount;
        private final int classmateCount;
        private final int fsCount;
        private final int strangerCount;
        private final int studyGroupCount;
        private final double density;
        private final double avgDegree;
        private final int maxDegree;
        private final double avgWeight;
        private final int isolatedNodes;
        private final int componentCount;
        private final int newNodes;
        private final int lostNodes;
        private final int newEdges;
        private final int lostEdges;

        public StepSnapshot(String label, int stepIndex, int nodeCount, int edgeCount,
                            int friendCount, int classmateCount, int fsCount,
                            int strangerCount, int studyGroupCount,
                            double density, double avgDegree, int maxDegree,
                            double avgWeight, int isolatedNodes, int componentCount,
                            int newNodes, int lostNodes, int newEdges, int lostEdges) {
            this.label = label;
            this.stepIndex = stepIndex;
            this.nodeCount = nodeCount;
            this.edgeCount = edgeCount;
            this.friendCount = friendCount;
            this.classmateCount = classmateCount;
            this.fsCount = fsCount;
            this.strangerCount = strangerCount;
            this.studyGroupCount = studyGroupCount;
            this.density = density;
            this.avgDegree = avgDegree;
            this.maxDegree = maxDegree;
            this.avgWeight = avgWeight;
            this.isolatedNodes = isolatedNodes;
            this.componentCount = componentCount;
            this.newNodes = newNodes;
            this.lostNodes = lostNodes;
            this.newEdges = newEdges;
            this.lostEdges = lostEdges;
        }

        public String getLabel() { return label; }
        public int getStepIndex() { return stepIndex; }
        public int getNodeCount() { return nodeCount; }
        public int getEdgeCount() { return edgeCount; }
        public int getFriendCount() { return friendCount; }
        public int getClassmateCount() { return classmateCount; }
        public int getFsCount() { return fsCount; }
        public int getStrangerCount() { return strangerCount; }
        public int getStudyGroupCount() { return studyGroupCount; }
        public double getDensity() { return density; }
        public double getAvgDegree() { return avgDegree; }
        public int getMaxDegree() { return maxDegree; }
        public double getAvgWeight() { return avgWeight; }
        public int getIsolatedNodes() { return isolatedNodes; }
        public int getComponentCount() { return componentCount; }
        public int getNewNodes() { return newNodes; }
        public int getLostNodes() { return lostNodes; }
        public int getNewEdges() { return newEdges; }
        public int getLostEdges() { return lostEdges; }

        /** Node growth rate vs previous step (0.0 for first step). */
        public double getNodeGrowthRate(StepSnapshot prev) {
            if (prev == null || prev.nodeCount == 0) return 0.0;
            return (double)(nodeCount - prev.nodeCount) / prev.nodeCount;
        }

        /** Edge growth rate vs previous step. */
        public double getEdgeGrowthRate(StepSnapshot prev) {
            if (prev == null || prev.edgeCount == 0) return 0.0;
            return (double)(edgeCount - prev.edgeCount) / prev.edgeCount;
        }

        /** CSV header. */
        public static String csvHeader() {
            return "step,label,nodes,edges,friends,classmates,familiar_strangers,"
                 + "strangers,study_groups,density,avg_degree,max_degree,avg_weight,"
                 + "isolated_nodes,components,new_nodes,lost_nodes,new_edges,lost_edges,"
                 + "node_growth_rate,edge_growth_rate";
        }

        /** CSV row (needs previous snapshot for growth rates). */
        public String toCsvRow(StepSnapshot prev) {
            return String.format(Locale.US,
                "%d,%s,%d,%d,%d,%d,%d,%d,%d,%.6f,%.4f,%d,%.4f,%d,%d,%d,%d,%d,%d,%.6f,%.6f",
                stepIndex, escapeCsv(label), nodeCount, edgeCount,
                friendCount, classmateCount, fsCount, strangerCount, studyGroupCount,
                density, avgDegree, maxDegree, avgWeight,
                isolatedNodes, componentCount, newNodes, lostNodes, newEdges, lostEdges,
                getNodeGrowthRate(prev), getEdgeGrowthRate(prev));
        }

        private static String escapeCsv(String s) {
            if (s == null) return "";
            if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
                return "\"" + s.replace("\"", "\"\"") + "\"";
            }
            return s;
        }
    }

    /** Summary of trends across all recorded steps. */
    public static class TrendReport {
        private final int totalSteps;
        private final int peakNodeStep;
        private final int peakNodeCount;
        private final int peakEdgeStep;
        private final int peakEdgeCount;
        private final double maxDensity;
        private final int maxDensityStep;
        private final double avgNodeCount;
        private final double avgEdgeCount;
        private final double avgDensity;
        private final double nodeCountStdDev;
        private final double edgeCountStdDev;
        private final List<PhaseTransition> phaseTransitions;
        private final Map<String, double[]> categoryTrends;

        public TrendReport(int totalSteps, int peakNodeStep, int peakNodeCount,
                           int peakEdgeStep, int peakEdgeCount,
                           double maxDensity, int maxDensityStep,
                           double avgNodeCount, double avgEdgeCount, double avgDensity,
                           double nodeCountStdDev, double edgeCountStdDev,
                           List<PhaseTransition> phaseTransitions,
                           Map<String, double[]> categoryTrends) {
            this.totalSteps = totalSteps;
            this.peakNodeStep = peakNodeStep;
            this.peakNodeCount = peakNodeCount;
            this.peakEdgeStep = peakEdgeStep;
            this.peakEdgeCount = peakEdgeCount;
            this.maxDensity = maxDensity;
            this.maxDensityStep = maxDensityStep;
            this.avgNodeCount = avgNodeCount;
            this.avgEdgeCount = avgEdgeCount;
            this.avgDensity = avgDensity;
            this.nodeCountStdDev = nodeCountStdDev;
            this.edgeCountStdDev = edgeCountStdDev;
            this.phaseTransitions = Collections.unmodifiableList(phaseTransitions);
            this.categoryTrends = Collections.unmodifiableMap(categoryTrends);
        }

        public int getTotalSteps() { return totalSteps; }
        public int getPeakNodeStep() { return peakNodeStep; }
        public int getPeakNodeCount() { return peakNodeCount; }
        public int getPeakEdgeStep() { return peakEdgeStep; }
        public int getPeakEdgeCount() { return peakEdgeCount; }
        public double getMaxDensity() { return maxDensity; }
        public int getMaxDensityStep() { return maxDensityStep; }
        public double getAvgNodeCount() { return avgNodeCount; }
        public double getAvgEdgeCount() { return avgEdgeCount; }
        public double getAvgDensity() { return avgDensity; }
        public double getNodeCountStdDev() { return nodeCountStdDev; }
        public double getEdgeCountStdDev() { return edgeCountStdDev; }
        public List<PhaseTransition> getPhaseTransitions() { return phaseTransitions; }
        public Map<String, double[]> getCategoryTrends() { return categoryTrends; }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Timeline Trend Report ===\n");
            sb.append(String.format("Total steps: %d\n", totalSteps));
            sb.append(String.format("Peak nodes: %d (step %d)\n", peakNodeCount, peakNodeStep));
            sb.append(String.format("Peak edges: %d (step %d)\n", peakEdgeCount, peakEdgeStep));
            sb.append(String.format("Peak density: %.6f (step %d)\n", maxDensity, maxDensityStep));
            sb.append(String.format("Avg nodes: %.1f (σ=%.2f)\n", avgNodeCount, nodeCountStdDev));
            sb.append(String.format("Avg edges: %.1f (σ=%.2f)\n", avgEdgeCount, edgeCountStdDev));
            sb.append(String.format("Avg density: %.6f\n", avgDensity));
            if (!phaseTransitions.isEmpty()) {
                sb.append("\nPhase transitions detected:\n");
                for (PhaseTransition pt : phaseTransitions) {
                    sb.append(String.format("  Step %d→%d: %s (Δnodes=%+d, Δedges=%+d)\n",
                        pt.fromStep, pt.toStep, pt.type, pt.nodeDelta, pt.edgeDelta));
                }
            }
            if (!categoryTrends.isEmpty()) {
                sb.append("\nCategory trends [min, max, avg]:\n");
                for (Map.Entry<String, double[]> entry : categoryTrends.entrySet()) {
                    double[] v = entry.getValue();
                    sb.append(String.format("  %-20s: [%.0f, %.0f, %.1f]\n",
                        entry.getKey(), v[0], v[1], v[2]));
                }
            }
            return sb.toString();
        }
    }

    /** Represents a significant change between consecutive steps. */
    public static class PhaseTransition {
        public final int fromStep;
        public final int toStep;
        public final String fromLabel;
        public final String toLabel;
        public final String type;
        public final int nodeDelta;
        public final int edgeDelta;

        public PhaseTransition(int fromStep, int toStep, String fromLabel, String toLabel,
                               String type, int nodeDelta, int edgeDelta) {
            this.fromStep = fromStep;
            this.toStep = toStep;
            this.fromLabel = fromLabel;
            this.toLabel = toLabel;
            this.type = type;
            this.nodeDelta = nodeDelta;
            this.edgeDelta = edgeDelta;
        }
    }

    private final List<StepSnapshot> snapshots = new ArrayList<>();
    private Set<String> previousNodes = new HashSet<>();
    private Set<String> previousEdgeKeys = new HashSet<>();
    private int stepCounter = 0;
    private double phaseThreshold = 0.3;

    public TimelineMetricsRecorder() {}

    /**
     * Sets the threshold for phase transition detection.
     * @param threshold fraction (0.0-1.0], default 0.3 (30%)
     */
    public void setPhaseThreshold(double threshold) {
        if (threshold <= 0 || threshold > 1.0) {
            throw new IllegalArgumentException("Threshold must be in (0, 1]: " + threshold);
        }
        this.phaseThreshold = threshold;
    }

    public double getPhaseThreshold() { return phaseThreshold; }

    /**
     * Records metrics for the current state of the graph at this timeline step.
     */
    public void recordStep(String label, Graph<String, edge> graph,
                           List<edge> friendEdges, List<edge> fsEdges,
                           List<edge> classmateEdges, List<edge> strangerEdges,
                           List<edge> studyGEdges) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }

        Set<String> currentNodes = new HashSet<>(graph.getVertices());
        Set<String> currentEdgeKeys = new HashSet<>();
        for (edge e : graph.getEdges()) {
            currentEdgeKeys.add(edgeKey(e));
        }

        Set<String> newNodeSet = new HashSet<>(currentNodes);
        newNodeSet.removeAll(previousNodes);
        Set<String> lostNodeSet = new HashSet<>(previousNodes);
        lostNodeSet.removeAll(currentNodes);
        Set<String> newEdgeSet = new HashSet<>(currentEdgeKeys);
        newEdgeSet.removeAll(previousEdgeKeys);
        Set<String> lostEdgeSet = new HashSet<>(previousEdgeKeys);
        lostEdgeSet.removeAll(currentEdgeKeys);

        int nodeCount = graph.getVertexCount();
        int edgeCount = graph.getEdgeCount();
        double density = computeDensity(nodeCount, edgeCount);
        double avgDeg = nodeCount > 0 ? (2.0 * edgeCount) / nodeCount : 0;
        int maxDeg = 0;
        int isolated = 0;
        for (String v : graph.getVertices()) {
            int deg = graph.degree(v);
            if (deg > maxDeg) maxDeg = deg;
            if (deg == 0) isolated++;
        }

        double totalWeight = 0;
        for (edge e : graph.getEdges()) {
            totalWeight += e.getWeight();
        }
        double avgWt = edgeCount > 0 ? totalWeight / edgeCount : 0;

        int components = countComponents(graph);

        int fc = countInGraph(graph, friendEdges);
        int cc = countInGraph(graph, classmateEdges);
        int fsc = countInGraph(graph, fsEdges);
        int sc = countInGraph(graph, strangerEdges);
        int sgc = countInGraph(graph, studyGEdges);

        StepSnapshot snapshot = new StepSnapshot(
            label, stepCounter, nodeCount, edgeCount,
            fc, cc, fsc, sc, sgc,
            density, avgDeg, maxDeg, avgWt,
            isolated, components,
            newNodeSet.size(), lostNodeSet.size(),
            newEdgeSet.size(), lostEdgeSet.size()
        );

        snapshots.add(snapshot);
        previousNodes = currentNodes;
        previousEdgeKeys = currentEdgeKeys;
        stepCounter++;
    }

    /** Returns all recorded snapshots (unmodifiable). */
    public List<StepSnapshot> getSnapshots() {
        return Collections.unmodifiableList(snapshots);
    }

    /** Returns the number of recorded steps. */
    public int getStepCount() {
        return snapshots.size();
    }

    /** Clears all recorded data. */
    public void clear() {
        snapshots.clear();
        previousNodes.clear();
        previousEdgeKeys.clear();
        stepCounter = 0;
    }

    /**
     * Exports all recorded metrics to a CSV file.
     * @param file output CSV file
     * @throws IOException if writing fails
     */
    public void exportCsv(File file) throws IOException {
        if (file == null) throw new IllegalArgumentException("File must not be null");
        PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file)));
        try {
            pw.println(StepSnapshot.csvHeader());
            StepSnapshot prev = null;
            for (StepSnapshot s : snapshots) {
                pw.println(s.toCsvRow(prev));
                prev = s;
            }
        } finally {
            pw.close();
        }
    }

    /**
     * Exports metrics as a CSV string.
     * @return CSV content
     */
    public String exportCsvString() {
        StringBuilder sb = new StringBuilder();
        sb.append(StepSnapshot.csvHeader()).append('\n');
        StepSnapshot prev = null;
        for (StepSnapshot s : snapshots) {
            sb.append(s.toCsvRow(prev)).append('\n');
            prev = s;
        }
        return sb.toString();
    }

    /**
     * Analyzes trends across all recorded steps.
     * @return TrendReport with peaks, averages, std devs, and phase transitions
     */
    public TrendReport analyzeTrends() {
        if (snapshots.isEmpty()) {
            return new TrendReport(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                Collections.<PhaseTransition>emptyList(),
                Collections.<String, double[]>emptyMap());
        }

        int peakNStep = 0, peakN = 0, peakEStep = 0, peakE = 0;
        double maxDens = 0; int maxDensStep = 0;
        double sumN = 0, sumE = 0, sumD = 0;
        double sumFr = 0, sumCl = 0, sumFs = 0, sumSt = 0, sumSg = 0;
        double minFr = Double.MAX_VALUE, maxFr = 0;
        double minCl = Double.MAX_VALUE, maxCl = 0;
        double minFs = Double.MAX_VALUE, maxFs = 0;
        double minSt = Double.MAX_VALUE, maxSt = 0;
        double minSg = Double.MAX_VALUE, maxSg = 0;

        for (StepSnapshot s : snapshots) {
            if (s.nodeCount > peakN) { peakN = s.nodeCount; peakNStep = s.stepIndex; }
            if (s.edgeCount > peakE) { peakE = s.edgeCount; peakEStep = s.stepIndex; }
            if (s.density > maxDens) { maxDens = s.density; maxDensStep = s.stepIndex; }
            sumN += s.nodeCount; sumE += s.edgeCount; sumD += s.density;
            sumFr += s.friendCount; sumCl += s.classmateCount;
            sumFs += s.fsCount; sumSt += s.strangerCount; sumSg += s.studyGroupCount;
            if (s.friendCount < minFr) minFr = s.friendCount;
            if (s.friendCount > maxFr) maxFr = s.friendCount;
            if (s.classmateCount < minCl) minCl = s.classmateCount;
            if (s.classmateCount > maxCl) maxCl = s.classmateCount;
            if (s.fsCount < minFs) minFs = s.fsCount;
            if (s.fsCount > maxFs) maxFs = s.fsCount;
            if (s.strangerCount < minSt) minSt = s.strangerCount;
            if (s.strangerCount > maxSt) maxSt = s.strangerCount;
            if (s.studyGroupCount < minSg) minSg = s.studyGroupCount;
            if (s.studyGroupCount > maxSg) maxSg = s.studyGroupCount;
        }

        int n = snapshots.size();
        double avgN = sumN / n, avgE = sumE / n, avgD = sumD / n;
        double varN = 0, varE = 0;
        for (StepSnapshot s : snapshots) {
            varN += (s.nodeCount - avgN) * (s.nodeCount - avgN);
            varE += (s.edgeCount - avgE) * (s.edgeCount - avgE);
        }
        double sdN = Math.sqrt(varN / n);
        double sdE = Math.sqrt(varE / n);

        List<PhaseTransition> transitions = new ArrayList<>();
        for (int i = 1; i < snapshots.size(); i++) {
            StepSnapshot curr = snapshots.get(i);
            StepSnapshot prev = snapshots.get(i - 1);
            int nDelta = curr.nodeCount - prev.nodeCount;
            int eDelta = curr.edgeCount - prev.edgeCount;
            boolean nodeSpike = prev.nodeCount > 0 &&
                Math.abs((double) nDelta / prev.nodeCount) > phaseThreshold;
            boolean edgeSpike = prev.edgeCount > 0 &&
                Math.abs((double) eDelta / prev.edgeCount) > phaseThreshold;

            if (nodeSpike || edgeSpike) {
                String type;
                if (nDelta > 0 && eDelta > 0) type = "growth_spike";
                else if (nDelta < 0 && eDelta < 0) type = "contraction";
                else type = "density_shift";
                transitions.add(new PhaseTransition(
                    prev.stepIndex, curr.stepIndex, prev.label, curr.label,
                    type, nDelta, eDelta));
            }
        }

        Map<String, double[]> catTrends = new LinkedHashMap<>();
        catTrends.put("friends", new double[]{minFr, maxFr, sumFr / n});
        catTrends.put("classmates", new double[]{minCl, maxCl, sumCl / n});
        catTrends.put("familiar_strangers", new double[]{minFs, maxFs, sumFs / n});
        catTrends.put("strangers", new double[]{minSt, maxSt, sumSt / n});
        catTrends.put("study_groups", new double[]{minSg, maxSg, sumSg / n});

        return new TrendReport(n, peakNStep, peakN, peakEStep, peakE,
            maxDens, maxDensStep, avgN, avgE, avgD, sdN, sdE,
            transitions, catTrends);
    }

    private static double computeDensity(int nodes, int edges) {
        if (nodes <= 1) return 0.0;
        return (2.0 * edges) / (nodes * (nodes - 1));
    }

    private static String edgeKey(edge e) {
        String v1 = e.getVertex1();
        String v2 = e.getVertex2();
        if (v1.compareTo(v2) > 0) { String tmp = v1; v1 = v2; v2 = tmp; }
        return v1 + "|" + v2 + "|" + e.getType();
    }

    private static int countInGraph(Graph<String, edge> graph, List<edge> edges) {
        int count = 0;
        for (edge e : edges) {
            if (graph.containsEdge(e)) count++;
        }
        return count;
    }

    private static int countComponents(Graph<String, edge> graph) {
        Set<String> visited = new HashSet<>();
        int components = 0;
        for (String v : graph.getVertices()) {
            if (!visited.contains(v)) {
                components++;
                Queue<String> queue = new LinkedList<>();
                queue.add(v);
                visited.add(v);
                while (!queue.isEmpty()) {
                    String cur = queue.poll();
                    for (String neighbor : graph.getNeighbors(cur)) {
                        if (!visited.contains(neighbor)) {
                            visited.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }
        return components;
    }
}
