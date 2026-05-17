package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GraphOpinionDynamicsEngine — autonomous opinion formation and consensus simulation
 * engine that models how opinions spread, cluster, and polarize in networks.
 *
 * <h3>Seven Analysis Engines:</h3>
 * <ol>
 *   <li><b>Voter Model Simulator</b> — stochastic model where nodes randomly copy
 *       a neighbor's opinion each step; tracks consensus time and opinion evolution</li>
 *   <li><b>DeGroot Averaging Model</b> — iterative weighted averaging where each node
 *       updates to the mean of its neighbors' opinions; detects convergence</li>
 *   <li><b>Bounded Confidence Model</b> (Hegselmann-Krause) — nodes average only with
 *       neighbors within confidence threshold ε, producing opinion clusters</li>
 *   <li><b>Polarization Detector</b> — measures bimodality coefficient, variance ratio,
 *       and Esteban-Ray polarization index with severity classification</li>
 *   <li><b>Echo Chamber Identifier</b> — detects groups with high internal opinion
 *       similarity and external diversity via modularity-weighted analysis</li>
 *   <li><b>Consensus Forecaster</b> — predicts if/when consensus will be reached using
 *       convergence rate estimation and extrapolation</li>
 *   <li><b>Interactive HTML Dashboard</b> — self-contained HTML export with opinion
 *       histograms, polarization metrics, echo chambers, and autonomous insights</li>
 * </ol>
 *
 * <p>Usage:</p>
 * <pre>
 *   GraphOpinionDynamicsEngine engine = new GraphOpinionDynamicsEngine();
 *   OpinionDynamicsReport report = engine.analyze(graph);
 *   System.out.println(engine.toText(report));
 *   String html = engine.exportHtml(report);
 * </pre>
 *
 * @author sauravbhattacharya001
 */
public class GraphOpinionDynamicsEngine {

    // -- Configuration --------------------------------------------------------
    private int maxSteps = 500;
    private double convergenceThreshold = 0.001;
    private double boundedConfidenceEpsilon = 0.2;
    private double echoChamberThreshold = 0.7;
    private int snapshotInterval = 10;
    private long randomSeed = -1; // -1 means use current time
    private Random rng;

    // -- Data structures ------------------------------------------------------

    /** Full report from an opinion dynamics analysis. */
    public static class OpinionDynamicsReport {
        public Map<String, Double> initialOpinions = new LinkedHashMap<>();
        public Map<String, Double> finalOpinions = new LinkedHashMap<>();
        public List<SimulationSnapshot> timeline = new ArrayList<>();
        public PolarizationMetrics polarization;
        public List<EchoChamber> echoChambers = new ArrayList<>();
        public ConsensusForecast forecast;
        public List<String> insights = new ArrayList<>();
        public double healthScore;
        public String model;
        public int steps;
    }

    /** Snapshot of opinion state at a given simulation step. */
    public static class SimulationSnapshot {
        public int step;
        public Map<String, Double> opinions = new LinkedHashMap<>();
        public double variance;
        public double polarizationIndex;
    }

    /** Polarization measurements. */
    public static class PolarizationMetrics {
        public double bimodalityCoefficient;
        public double varianceRatio;
        public double estebanRayIndex;
        public String severity; // "none", "mild", "moderate", "severe", "extreme"
    }

    /** A detected echo chamber in the network. */
    public static class EchoChamber {
        public Set<String> members = new LinkedHashSet<>();
        public double internalSimilarity;
        public double externalDiversity;
        public double avgOpinion;
        public double chamberStrength;
    }

    /** Consensus forecast based on convergence trends. */
    public static class ConsensusForecast {
        public boolean consensusReached;
        public int stepsToConsensus = -1;
        public double convergenceRate;
        public double predictedFinalVariance;
        public String verdict; // "converging", "polarizing", "oscillating", "stalled"
    }

    // -- Setters for configuration --------------------------------------------

    /** Set maximum simulation steps. */
    public void setMaxSteps(int maxSteps) { this.maxSteps = maxSteps; }

    /** Set convergence threshold (variance below this = consensus). */
    public void setConvergenceThreshold(double t) { this.convergenceThreshold = t; }

    /** Set bounded confidence epsilon. */
    public void setBoundedConfidenceEpsilon(double e) { this.boundedConfidenceEpsilon = e; }

    /** Set echo chamber internal similarity threshold. */
    public void setEchoChamberThreshold(double t) { this.echoChamberThreshold = t; }

    /** Set snapshot recording interval. */
    public void setSnapshotInterval(int n) { this.snapshotInterval = Math.max(1, n); }

    /** Set random seed for reproducibility. Use -1 for non-deterministic. */
    public void setRandomSeed(long seed) { this.randomSeed = seed; }

    // -- Main analysis --------------------------------------------------------

    /**
     * Run full opinion dynamics analysis with random initial opinions.
     *
     * @param graph the network to analyze
     * @return complete opinion dynamics report
     */
    public OpinionDynamicsReport analyze(Graph<String, Edge> graph) {
        return analyze(graph, null);
    }

    /**
     * Run full opinion dynamics analysis with given initial opinions.
     * Uses DeGroot as the default model.
     *
     * @param graph           the network to analyze
     * @param initialOpinions node-to-opinion map (values in [0,1]), or null for random
     * @return complete opinion dynamics report
     */
    public OpinionDynamicsReport analyze(Graph<String, Edge> graph,
                                          Map<String, Double> initialOpinions) {
        initRng();
        if (graph == null || graph.getVertexCount() == 0) {
            return emptyReport("DeGroot");
        }
        Map<String, Double> opinions = resolveOpinions(graph, initialOpinions);
        OpinionDynamicsReport report = simulateDeGroot(graph, opinions, maxSteps);
        report.polarization = measurePolarization(graph, report.finalOpinions);
        report.echoChambers = detectEchoChambers(graph, report.finalOpinions);
        report.forecast = forecastConsensus(report.timeline);
        report.insights = generateInsights(graph, report);
        report.healthScore = computeHealthScore(report);
        return report;
    }

    // -- Voter Model ----------------------------------------------------------

    /**
     * Simulate the voter model: at each step, a random node copies a random
     * neighbor's opinion.
     *
     * @param graph    the network
     * @param opinions initial opinions (values in [0,1])
     * @param steps    maximum number of steps
     * @return simulation report
     */
    public OpinionDynamicsReport simulateVoterModel(Graph<String, Edge> graph,
                                                     Map<String, Double> opinions,
                                                     int steps) {
        initRng();
        OpinionDynamicsReport report = new OpinionDynamicsReport();
        report.model = "VoterModel";
        report.initialOpinions = new LinkedHashMap<>(opinions);

        Map<String, Double> current = new LinkedHashMap<>(opinions);
        List<String> vertices = new ArrayList<>(graph.getVertices());
        if (vertices.isEmpty()) {
            report.finalOpinions = current;
            report.steps = 0;
            return report;
        }

        recordSnapshot(report, 0, current);

        int step;
        for (step = 1; step <= steps; step++) {
            // Pick a random node
            String node = vertices.get(rng.nextInt(vertices.size()));
            Collection<String> neighbors = graph.getNeighbors(node);
            if (neighbors != null && !neighbors.isEmpty()) {
                List<String> neighList = new ArrayList<>(neighbors);
                String neighbor = neighList.get(rng.nextInt(neighList.size()));
                current.put(node, current.get(neighbor));
            }

            if (step % snapshotInterval == 0 || step == steps) {
                recordSnapshot(report, step, current);
            }

            if (computeVariance(current.values()) < convergenceThreshold) {
                break;
            }
        }

        report.finalOpinions = new LinkedHashMap<>(current);
        report.steps = Math.min(step, steps);
        return report;
    }

    // -- DeGroot Model --------------------------------------------------------

    /**
     * Simulate the DeGroot averaging model: each node updates its opinion to
     * the weighted average of its neighbors' opinions (including itself).
     *
     * @param graph    the network
     * @param opinions initial opinions (values in [0,1])
     * @param steps    maximum number of steps
     * @return simulation report
     */
    public OpinionDynamicsReport simulateDeGroot(Graph<String, Edge> graph,
                                                  Map<String, Double> opinions,
                                                  int steps) {
        initRng();
        OpinionDynamicsReport report = new OpinionDynamicsReport();
        report.model = "DeGroot";
        report.initialOpinions = new LinkedHashMap<>(opinions);

        Map<String, Double> current = new LinkedHashMap<>(opinions);

        if (graph.getVertexCount() == 0) {
            report.finalOpinions = current;
            report.steps = 0;
            return report;
        }

        recordSnapshot(report, 0, current);

        int step;
        for (step = 1; step <= steps; step++) {
            Map<String, Double> next = new LinkedHashMap<>();
            for (String node : graph.getVertices()) {
                Collection<String> neighbors = graph.getNeighbors(node);
                if (neighbors == null || neighbors.isEmpty()) {
                    next.put(node, current.get(node));
                    continue;
                }
                double sum = current.get(node); // self-weight
                double count = 1.0;
                for (String n : neighbors) {
                    Edge e = graph.findEdge(node, n);
                    double w = (e != null && e.getWeight() > 0) ? e.getWeight() : 1.0;
                    sum += current.get(n) * w;
                    count += w;
                }
                next.put(node, sum / count);
            }
            current = next;

            if (step % snapshotInterval == 0 || step == steps) {
                recordSnapshot(report, step, current);
            }

            if (computeVariance(current.values()) < convergenceThreshold) {
                break;
            }
        }

        report.finalOpinions = new LinkedHashMap<>(current);
        report.steps = Math.min(step, steps);
        return report;
    }

    // -- Bounded Confidence Model ---------------------------------------------

    /**
     * Simulate the Hegselmann-Krause bounded confidence model: nodes average
     * only with neighbors whose opinion differs by at most epsilon.
     *
     * @param graph    the network
     * @param opinions initial opinions (values in [0,1])
     * @param epsilon  confidence threshold
     * @param steps    maximum number of steps
     * @return simulation report
     */
    public OpinionDynamicsReport simulateBoundedConfidence(Graph<String, Edge> graph,
                                                            Map<String, Double> opinions,
                                                            double epsilon,
                                                            int steps) {
        initRng();
        OpinionDynamicsReport report = new OpinionDynamicsReport();
        report.model = "BoundedConfidence(ε=" + String.format("%.2f", epsilon) + ")";
        report.initialOpinions = new LinkedHashMap<>(opinions);

        Map<String, Double> current = new LinkedHashMap<>(opinions);

        if (graph.getVertexCount() == 0) {
            report.finalOpinions = current;
            report.steps = 0;
            return report;
        }

        recordSnapshot(report, 0, current);

        int step;
        for (step = 1; step <= steps; step++) {
            Map<String, Double> next = new LinkedHashMap<>();
            boolean changed = false;

            for (String node : graph.getVertices()) {
                double myOpinion = current.get(node);
                double sum = myOpinion;
                int count = 1;

                Collection<String> neighbors = graph.getNeighbors(node);
                if (neighbors != null) {
                    for (String n : neighbors) {
                        double nOp = current.get(n);
                        if (Math.abs(myOpinion - nOp) <= epsilon) {
                            sum += nOp;
                            count++;
                        }
                    }
                }

                double newOp = sum / count;
                if (Math.abs(newOp - myOpinion) > 1e-10) {
                    changed = true;
                }
                next.put(node, newOp);
            }

            current = next;

            if (step % snapshotInterval == 0 || step == steps) {
                recordSnapshot(report, step, current);
            }

            if (!changed || computeVariance(current.values()) < convergenceThreshold) {
                break;
            }
        }

        report.finalOpinions = new LinkedHashMap<>(current);
        report.steps = Math.min(step, steps);
        return report;
    }

    // -- Polarization Detector ------------------------------------------------

    /**
     * Measure polarization of current opinions in the network.
     *
     * @param graph    the network
     * @param opinions current opinions
     * @return polarization metrics with severity classification
     */
    public PolarizationMetrics measurePolarization(Graph<String, Edge> graph,
                                                    Map<String, Double> opinions) {
        initRng();
        PolarizationMetrics pm = new PolarizationMetrics();
        Collection<Double> vals = opinions.values();
        if (vals.size() < 2) {
            pm.bimodalityCoefficient = 0;
            pm.varianceRatio = 0;
            pm.estebanRayIndex = 0;
            pm.severity = "none";
            return pm;
        }

        // Bimodality coefficient: BC = (skewness^2 + 1) / (kurtosis + 3*(n-1)^2/((n-2)*(n-3)))
        // Simplified: BC = (m3^2 + 1) / (m4 + 3) where m3=skewness, m4=excess kurtosis
        double mean = vals.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        int n = vals.size();
        double m2 = 0, m3 = 0, m4 = 0;
        for (double v : vals) {
            double d = v - mean;
            m2 += d * d;
            m3 += d * d * d;
            m4 += d * d * d * d;
        }
        m2 /= n;
        m3 /= n;
        m4 /= n;

        double sd = Math.sqrt(m2);
        double skewness = (sd > 1e-12) ? m3 / (sd * sd * sd) : 0;
        double kurtosis = (sd > 1e-12) ? (m4 / (sd * sd * sd * sd)) : 3; // raw kurtosis

        // Bimodality coefficient
        pm.bimodalityCoefficient = Math.min(1.0,
                (skewness * skewness + 1) / kurtosis);
        if (Double.isNaN(pm.bimodalityCoefficient) || Double.isInfinite(pm.bimodalityCoefficient)) {
            pm.bimodalityCoefficient = 0;
        }

        // Variance ratio: cluster into two groups (above/below mean)
        List<Double> sorted = vals.stream().sorted().collect(Collectors.toList());
        List<Double> low = new ArrayList<>(), high = new ArrayList<>();
        for (double v : vals) {
            if (v <= mean) low.add(v); else high.add(v);
        }
        // Handle edge case where all values equal mean
        if (high.isEmpty() && !low.isEmpty()) {
            high.add(low.remove(low.size() - 1));
        }
        if (low.isEmpty() || high.isEmpty()) {
            pm.varianceRatio = 0;
        } else {
            double lowMean = low.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double highMean = high.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double interVar = (low.size() * (lowMean - mean) * (lowMean - mean)
                    + high.size() * (highMean - mean) * (highMean - mean)) / n;
            pm.varianceRatio = (m2 > 1e-12) ? interVar / m2 : 0;
        }
        pm.varianceRatio = Math.min(1.0, Math.max(0.0, pm.varianceRatio));

        // Esteban-Ray polarization index (simplified): ER = Σ_i Σ_j π_i^(1+α) * π_j * |y_i - y_j|
        // Use 10 bins, α = 1.0
        int bins = 10;
        double[] binCounts = new double[bins];
        for (double v : vals) {
            int b = Math.min(bins - 1, (int) (v * bins));
            binCounts[b]++;
        }
        double[] binFreqs = new double[bins];
        for (int i = 0; i < bins; i++) binFreqs[i] = binCounts[i] / n;

        double er = 0;
        for (int i = 0; i < bins; i++) {
            for (int j = 0; j < bins; j++) {
                double dist = Math.abs((i + 0.5) / bins - (j + 0.5) / bins);
                er += binFreqs[i] * binFreqs[i] * binFreqs[j] * dist;
            }
        }
        pm.estebanRayIndex = Math.min(1.0, er * 4); // scale to [0,1]

        // Severity
        double composite = (pm.bimodalityCoefficient + pm.varianceRatio + pm.estebanRayIndex) / 3.0;
        if (composite < 0.15) pm.severity = "none";
        else if (composite < 0.30) pm.severity = "mild";
        else if (composite < 0.50) pm.severity = "moderate";
        else if (composite < 0.70) pm.severity = "severe";
        else pm.severity = "extreme";

        return pm;
    }

    // -- Echo Chamber Identifier ----------------------------------------------

    /**
     * Detect echo chambers — groups with high internal opinion similarity
     * and high external opinion diversity.
     *
     * @param graph    the network
     * @param opinions current opinions
     * @return list of detected echo chambers
     */
    public List<EchoChamber> detectEchoChambers(Graph<String, Edge> graph,
                                                 Map<String, Double> opinions) {
        initRng();
        List<EchoChamber> chambers = new ArrayList<>();
        if (graph.getVertexCount() < 2) return chambers;

        // Find connected components or communities via label propagation
        List<Set<String>> communities = findCommunities(graph);

        double globalMean = opinions.values().stream()
                .mapToDouble(Double::doubleValue).average().orElse(0.5);

        for (Set<String> comm : communities) {
            if (comm.size() < 2) continue;

            // Internal similarity: 1 - stddev of opinions within community
            double[] commOps = comm.stream()
                    .mapToDouble(v -> opinions.getOrDefault(v, 0.5)).toArray();
            double commMean = Arrays.stream(commOps).average().orElse(0.5);
            double commVar = Arrays.stream(commOps)
                    .map(x -> (x - commMean) * (x - commMean)).average().orElse(0);
            double internalSim = 1.0 - Math.sqrt(commVar) * 2; // scale so 0 variance = 1.0
            internalSim = Math.max(0, Math.min(1, internalSim));

            // External diversity: difference between community mean and global mean
            double externalDiv = Math.abs(commMean - globalMean) * 2;
            externalDiv = Math.min(1.0, externalDiv);

            double strength = internalSim * externalDiv;

            if (internalSim >= echoChamberThreshold && externalDiv >= 0.1) {
                EchoChamber ec = new EchoChamber();
                ec.members = new LinkedHashSet<>(comm);
                ec.internalSimilarity = internalSim;
                ec.externalDiversity = externalDiv;
                ec.avgOpinion = commMean;
                ec.chamberStrength = strength;
                chambers.add(ec);
            }
        }

        // Sort by strength descending
        chambers.sort((a, b) -> Double.compare(b.chamberStrength, a.chamberStrength));
        return chambers;
    }

    // -- Consensus Forecaster -------------------------------------------------

    /**
     * Forecast whether consensus will be reached based on the simulation timeline.
     *
     * @param timeline list of simulation snapshots
     * @return consensus forecast
     */
    public ConsensusForecast forecastConsensus(List<SimulationSnapshot> timeline) {
        ConsensusForecast fc = new ConsensusForecast();
        if (timeline == null || timeline.isEmpty()) {
            fc.verdict = "stalled";
            fc.predictedFinalVariance = 0;
            fc.convergenceRate = 0;
            return fc;
        }

        SimulationSnapshot last = timeline.get(timeline.size() - 1);
        fc.predictedFinalVariance = last.variance;

        if (last.variance < convergenceThreshold) {
            fc.consensusReached = true;
            fc.stepsToConsensus = last.step;
            fc.convergenceRate = 1.0;
            fc.verdict = "converging";
            return fc;
        }

        // Estimate convergence rate from variance trend
        if (timeline.size() >= 3) {
            SimulationSnapshot first = timeline.get(0);
            double varStart = first.variance;
            double varEnd = last.variance;

            if (varStart > 1e-12 && varEnd > 1e-12 && last.step > first.step) {
                double ratio = varEnd / varStart;
                int stepDiff = last.step - first.step;
                fc.convergenceRate = Math.pow(ratio, 1.0 / stepDiff);

                if (fc.convergenceRate < 1.0 && fc.convergenceRate > 0) {
                    // Estimate steps to convergence threshold
                    double stepsNeeded = Math.log(convergenceThreshold / varEnd)
                            / Math.log(fc.convergenceRate);
                    if (stepsNeeded > 0 && stepsNeeded < 100000) {
                        fc.stepsToConsensus = last.step + (int) Math.ceil(stepsNeeded);
                        fc.predictedFinalVariance = convergenceThreshold;
                    }
                }
            }

            // Detect oscillation: variance goes up then down then up
            boolean increasing = false, decreasing = false;
            for (int i = 1; i < timeline.size(); i++) {
                if (timeline.get(i).variance > timeline.get(i - 1).variance + 1e-8) {
                    increasing = true;
                }
                if (timeline.get(i).variance < timeline.get(i - 1).variance - 1e-8) {
                    decreasing = true;
                }
            }

            if (varEnd > varStart * 1.1) {
                fc.verdict = "polarizing";
            } else if (increasing && decreasing && Math.abs(varEnd - varStart) < varStart * 0.1) {
                fc.verdict = "oscillating";
            } else if (fc.convergenceRate < 1.0) {
                fc.verdict = "converging";
            } else {
                fc.verdict = "stalled";
            }
        } else {
            fc.verdict = "stalled";
        }

        return fc;
    }

    // -- Text Output ----------------------------------------------------------

    /**
     * Format report as human-readable text.
     *
     * @param report the analysis report
     * @return formatted text
     */
    public String toText(OpinionDynamicsReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("===========================================================\n");
        sb.append("  GRAPH OPINION DYNAMICS ENGINE - Analysis Report\n");
        sb.append("===========================================================\n\n");

        sb.append(String.format("Model: %s  |  Steps: %d  |  Health Score: %.0f/100\n\n",
                report.model, report.steps, report.healthScore));

        // Opinion summary
        sb.append("-- Opinion Summary -----------------------------------------\n");
        double initVar = computeVariance(report.initialOpinions.values());
        double finalVar = computeVariance(report.finalOpinions.values());
        sb.append(String.format("  Initial variance:  %.6f\n", initVar));
        sb.append(String.format("  Final variance:    %.6f\n", finalVar));
        sb.append(String.format("  Nodes: %d\n\n", report.finalOpinions.size()));

        // Top opinion changes
        sb.append("  Top opinion shifts:\n");
        List<Map.Entry<String, Double>> shifts = new ArrayList<>();
        for (String node : report.initialOpinions.keySet()) {
            double init = report.initialOpinions.getOrDefault(node, 0.5);
            double fin = report.finalOpinions.getOrDefault(node, 0.5);
            shifts.add(new AbstractMap.SimpleEntry<>(node, Math.abs(fin - init)));
        }
        shifts.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        int shown = 0;
        for (Map.Entry<String, Double> e : shifts) {
            if (shown >= 5) break;
            double init = report.initialOpinions.get(e.getKey());
            double fin = report.finalOpinions.get(e.getKey());
            sb.append(String.format("    %s: %.3f → %.3f (Δ%.3f)\n",
                    e.getKey(), init, fin, e.getValue()));
            shown++;
        }
        sb.append("\n");

        // Polarization
        if (report.polarization != null) {
            sb.append("-- Polarization --------------------------------------------\n");
            sb.append(String.format("  Bimodality:    %.3f\n", report.polarization.bimodalityCoefficient));
            sb.append(String.format("  Variance Ratio: %.3f\n", report.polarization.varianceRatio));
            sb.append(String.format("  Esteban-Ray:   %.3f\n", report.polarization.estebanRayIndex));
            sb.append(String.format("  Severity:      %s\n\n", report.polarization.severity));
        }

        // Echo chambers
        if (!report.echoChambers.isEmpty()) {
            sb.append("-- Echo Chambers -------------------------------------------\n");
            for (int i = 0; i < report.echoChambers.size(); i++) {
                EchoChamber ec = report.echoChambers.get(i);
                sb.append(String.format("  Chamber %d: %d members, avg opinion=%.3f, " +
                                "similarity=%.2f, diversity=%.2f, strength=%.2f\n",
                        i + 1, ec.members.size(), ec.avgOpinion,
                        ec.internalSimilarity, ec.externalDiversity, ec.chamberStrength));
                sb.append(String.format("    Members: %s\n", formatMembers(ec.members)));
            }
            sb.append("\n");
        }

        // Forecast
        if (report.forecast != null) {
            sb.append("-- Consensus Forecast --------------------------------------\n");
            sb.append(String.format("  Verdict:          %s\n", report.forecast.verdict));
            sb.append(String.format("  Consensus reached: %s\n", report.forecast.consensusReached));
            if (report.forecast.stepsToConsensus > 0) {
                sb.append(String.format("  Steps to consensus: %d\n", report.forecast.stepsToConsensus));
            }
            sb.append(String.format("  Convergence rate: %.6f\n", report.forecast.convergenceRate));
            sb.append("\n");
        }

        // Insights
        if (!report.insights.isEmpty()) {
            sb.append("-- Autonomous Insights -------------------------------------\n");
            for (String insight : report.insights) {
                sb.append("  * " + insight + "\n");
            }
        }

        return sb.toString();
    }

    // -- HTML Dashboard -------------------------------------------------------

    /**
     * Export report as a self-contained interactive HTML dashboard.
     *
     * @param report the analysis report
     * @return HTML string
     */
    public String exportHtml(OpinionDynamicsReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"UTF-8\">\n");
        sb.append("<title>Opinion Dynamics Dashboard</title>\n");
        sb.append("<style>\n");
        sb.append("*{box-sizing:border-box;margin:0;padding:0}");
        sb.append("body{font-family:'Segoe UI',system-ui,sans-serif;background:#0f172a;color:#e2e8f0;padding:24px}");
        sb.append("h1{font-size:1.5rem;color:#38bdf8;margin-bottom:8px}");
        sb.append("h2{font-size:1.1rem;color:#7dd3fc;margin:16px 0 8px}");
        sb.append(".card{background:#1e293b;border-radius:12px;padding:16px;margin-bottom:16px;border:1px solid #334155}");
        sb.append(".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:16px}");
        sb.append(".metric{text-align:center;padding:12px}");
        sb.append(".metric .value{font-size:2rem;font-weight:700;color:#38bdf8}");
        sb.append(".metric .label{font-size:0.8rem;color:#94a3b8;margin-top:4px}");
        sb.append(".bar{height:20px;border-radius:4px;margin:2px 0}");
        sb.append(".insight{padding:8px 12px;background:#1a2332;border-left:3px solid #38bdf8;margin:4px 0;border-radius:0 6px 6px 0}");
        sb.append(".badge{display:inline-block;padding:2px 8px;border-radius:12px;font-size:0.75rem;font-weight:600}");
        sb.append(".sev-none{background:#166534;color:#bbf7d0}");
        sb.append(".sev-mild{background:#854d0e;color:#fef08a}");
        sb.append(".sev-moderate{background:#9a3412;color:#fed7aa}");
        sb.append(".sev-severe{background:#991b1b;color:#fecaca}");
        sb.append(".sev-extreme{background:#7f1d1d;color:#fca5a5}");
        sb.append("table{width:100%;border-collapse:collapse}");
        sb.append("th,td{padding:6px 10px;text-align:left;border-bottom:1px solid #334155;font-size:0.85rem}");
        sb.append("th{color:#94a3b8;font-weight:600}");
        sb.append(".hist-bar{display:inline-block;height:14px;background:#38bdf8;border-radius:2px;min-width:2px}");
        sb.append("</style></head><body>\n");

        sb.append("<h1>📊 Opinion Dynamics Dashboard</h1>\n");
        sb.append(String.format("<p style='color:#94a3b8'>Model: %s | %d nodes | %d steps | Generated %s</p>\n",
                esc(report.model), report.finalOpinions.size(), report.steps,
                new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())));

        // Health score + key metrics
        sb.append("<div class='grid' style='margin-top:16px'>\n");
        sb.append("<div class='card metric'><div class='value'>" +
                String.format("%.0f", report.healthScore) +
                "</div><div class='label'>Health Score (0-100)</div></div>\n");
        if (report.polarization != null) {
            sb.append("<div class='card metric'><div class='value'>" +
                    String.format("%.2f", report.polarization.bimodalityCoefficient) +
                    "</div><div class='label'>Bimodality</div></div>\n");
            sb.append("<div class='card metric'><div class='value'><span class='badge sev-" +
                    report.polarization.severity + "'>" +
                    report.polarization.severity.toUpperCase() +
                    "</span></div><div class='label'>Polarization Severity</div></div>\n");
        }
        if (report.forecast != null) {
            sb.append("<div class='card metric'><div class='value'>" +
                    esc(report.forecast.verdict.toUpperCase()) +
                    "</div><div class='label'>Consensus Verdict</div></div>\n");
        }
        sb.append("</div>\n");

        // Opinion distribution histogram
        sb.append("<div class='card'><h2>Opinion Distribution</h2>\n");
        sb.append("<div class='grid'>\n");
        sb.append("<div><h2 style='font-size:0.9rem'>Initial</h2>");
        sb.append(renderHistogramHtml(report.initialOpinions.values()));
        sb.append("</div>\n");
        sb.append("<div><h2 style='font-size:0.9rem'>Final</h2>");
        sb.append(renderHistogramHtml(report.finalOpinions.values()));
        sb.append("</div>\n");
        sb.append("</div></div>\n");

        // Echo chambers
        if (!report.echoChambers.isEmpty()) {
            sb.append("<div class='card'><h2>Echo Chambers (" + report.echoChambers.size() + " detected)</h2>\n");
            sb.append("<table><tr><th>#</th><th>Members</th><th>Avg Opinion</th><th>Int. Similarity</th><th>Ext. Diversity</th><th>Strength</th></tr>\n");
            for (int i = 0; i < report.echoChambers.size(); i++) {
                EchoChamber ec = report.echoChambers.get(i);
                sb.append(String.format("<tr><td>%d</td><td>%s</td><td>%.3f</td><td>%.2f</td><td>%.2f</td><td>%.2f</td></tr>\n",
                        i + 1, esc(formatMembers(ec.members)), ec.avgOpinion,
                        ec.internalSimilarity, ec.externalDiversity, ec.chamberStrength));
            }
            sb.append("</table></div>\n");
        }

        // Polarization details
        if (report.polarization != null) {
            sb.append("<div class='card'><h2>Polarization Metrics</h2>\n");
            sb.append("<table><tr><th>Metric</th><th>Value</th></tr>\n");
            sb.append(String.format("<tr><td>Bimodality Coefficient</td><td>%.4f</td></tr>\n",
                    report.polarization.bimodalityCoefficient));
            sb.append(String.format("<tr><td>Variance Ratio</td><td>%.4f</td></tr>\n",
                    report.polarization.varianceRatio));
            sb.append(String.format("<tr><td>Esteban-Ray Index</td><td>%.4f</td></tr>\n",
                    report.polarization.estebanRayIndex));
            sb.append(String.format("<tr><td>Severity</td><td><span class='badge sev-%s'>%s</span></td></tr>\n",
                    report.polarization.severity, report.polarization.severity.toUpperCase()));
            sb.append("</table></div>\n");
        }

        // Forecast
        if (report.forecast != null) {
            sb.append("<div class='card'><h2>Consensus Forecast</h2>\n");
            sb.append("<table><tr><th>Property</th><th>Value</th></tr>\n");
            sb.append(String.format("<tr><td>Verdict</td><td><b>%s</b></td></tr>\n",
                    esc(report.forecast.verdict)));
            sb.append(String.format("<tr><td>Consensus Reached</td><td>%s</td></tr>\n",
                    report.forecast.consensusReached ? "Yes ✅" : "No"));
            if (report.forecast.stepsToConsensus > 0) {
                sb.append(String.format("<tr><td>Steps to Consensus</td><td>%d</td></tr>\n",
                        report.forecast.stepsToConsensus));
            }
            sb.append(String.format("<tr><td>Convergence Rate</td><td>%.6f</td></tr>\n",
                    report.forecast.convergenceRate));
            sb.append(String.format("<tr><td>Predicted Final Variance</td><td>%.6f</td></tr>\n",
                    report.forecast.predictedFinalVariance));
            sb.append("</table></div>\n");
        }

        // Insights
        if (!report.insights.isEmpty()) {
            sb.append("<div class='card'><h2>🤖 Autonomous Insights</h2>\n");
            for (String insight : report.insights) {
                sb.append("<div class='insight'>" + esc(insight) + "</div>\n");
            }
            sb.append("</div>\n");
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    /**
     * Export the HTML dashboard to a file.
     *
     * @param report   the analysis report
     * @param filepath output file path
     * @throws IOException if writing fails
     */
    public void exportHtmlToFile(OpinionDynamicsReport report, String filepath) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(filepath), StandardCharsets.UTF_8)) {
            w.write(exportHtml(report));
        }
    }

    // -- Internal helpers -----------------------------------------------------

    private void initRng() {
        if (rng == null) {
            rng = (randomSeed >= 0) ? new Random(randomSeed) : new Random();
        }
    }

    private Map<String, Double> resolveOpinions(Graph<String, Edge> graph,
                                                 Map<String, Double> given) {
        Map<String, Double> ops = new LinkedHashMap<>();
        for (String v : graph.getVertices()) {
            if (given != null && given.containsKey(v)) {
                ops.put(v, Math.max(0, Math.min(1, given.get(v))));
            } else {
                ops.put(v, rng.nextDouble());
            }
        }
        return ops;
    }

    private void recordSnapshot(OpinionDynamicsReport report, int step,
                                 Map<String, Double> opinions) {
        SimulationSnapshot ss = new SimulationSnapshot();
        ss.step = step;
        ss.opinions = new LinkedHashMap<>(opinions);
        ss.variance = computeVariance(opinions.values());
        // Quick polarization: just bimodality
        double mean = opinions.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.5);
        double lo = 0, hi = 0;
        for (double v : opinions.values()) {
            if (v < mean) lo++; else hi++;
        }
        double total = opinions.size();
        ss.polarizationIndex = (total > 0) ? 1.0 - Math.abs(lo - hi) / total : 0;
        report.timeline.add(ss);
    }

    static double computeVariance(Collection<Double> values) {
        if (values == null || values.size() < 2) return 0;
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double sumSq = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum();
        return sumSq / values.size();
    }

    private List<Set<String>> findCommunities(Graph<String, Edge> graph) {
        // Simple label propagation
        Map<String, String> labels = new HashMap<>();
        for (String v : graph.getVertices()) labels.put(v, v);

        List<String> vertices = new ArrayList<>(graph.getVertices());
        for (int iter = 0; iter < 20; iter++) {
            boolean changed = false;
            Collections.shuffle(vertices, rng);
            for (String v : vertices) {
                Collection<String> neighbors = graph.getNeighbors(v);
                if (neighbors == null || neighbors.isEmpty()) continue;

                // Most common label among neighbors
                Map<String, Integer> freq = new HashMap<>();
                for (String n : neighbors) {
                    freq.merge(labels.get(n), 1, Integer::sum);
                }
                String bestLabel = freq.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey).orElse(labels.get(v));
                if (!bestLabel.equals(labels.get(v))) {
                    labels.put(v, bestLabel);
                    changed = true;
                }
            }
            if (!changed) break;
        }

        // Group by label
        Map<String, Set<String>> groups = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : labels.entrySet()) {
            groups.computeIfAbsent(e.getValue(), k -> new LinkedHashSet<>()).add(e.getKey());
        }
        return new ArrayList<>(groups.values());
    }

    private List<String> generateInsights(Graph<String, Edge> graph,
                                           OpinionDynamicsReport report) {
        List<String> insights = new ArrayList<>();

        // Polarization insight
        if (report.polarization != null) {
            if ("severe".equals(report.polarization.severity) ||
                    "extreme".equals(report.polarization.severity)) {
                insights.add(String.format("Network shows %s polarization " +
                                "(bimodality=%.2f) — opinions have split into opposing camps",
                        report.polarization.severity, report.polarization.bimodalityCoefficient));
            } else if ("none".equals(report.polarization.severity)) {
                insights.add("Opinions are relatively uniform across the network — " +
                        "no significant polarization detected");
            }
        }

        // Echo chamber insight
        if (!report.echoChambers.isEmpty()) {
            EchoChamber strongest = report.echoChambers.get(0);
            insights.add(String.format("Echo chamber detected: %d nodes with %.0f%% " +
                            "internal agreement, isolated from mainstream opinion (strength=%.2f)",
                    strongest.members.size(),
                    strongest.internalSimilarity * 100,
                    strongest.chamberStrength));
        } else if (graph.getVertexCount() > 3) {
            insights.add("No echo chambers detected — opinions are well-mixed across communities");
        }

        // Forecast insight
        if (report.forecast != null) {
            switch (report.forecast.verdict) {
                case "converging":
                    if (report.forecast.consensusReached) {
                        insights.add(String.format("Consensus reached in %d steps — " +
                                "the network successfully converged", report.forecast.stepsToConsensus));
                    } else if (report.forecast.stepsToConsensus > 0) {
                        insights.add(String.format("Consensus forecast: convergence expected " +
                                        "in ~%d steps at current rate (%.4f/step)",
                                report.forecast.stepsToConsensus, report.forecast.convergenceRate));
                    }
                    break;
                case "polarizing":
                    insights.add("Warning: opinions are actively diverging — " +
                            "the network is becoming more polarized over time");
                    break;
                case "oscillating":
                    insights.add("Opinion dynamics are oscillating without settling — " +
                            "the network may have competing influence loops");
                    break;
                case "stalled":
                    insights.add("Opinion dynamics have stalled — no significant " +
                            "convergence or divergence detected");
                    break;
            }
        }

        // Bridge node insight
        if (graph.getVertexCount() > 4) {
            findBridgeNodes(graph, report.finalOpinions, insights);
        }

        // Variance reduction insight
        double initVar = computeVariance(report.initialOpinions.values());
        double finalVar = computeVariance(report.finalOpinions.values());
        if (initVar > 0.001) {
            double reduction = (1.0 - finalVar / initVar) * 100;
            if (reduction > 50) {
                insights.add(String.format("Opinion variance reduced by %.0f%% during simulation " +
                        "— significant convergence occurred", reduction));
            } else if (reduction < -10) {
                insights.add(String.format("Opinion variance increased by %.0f%% — " +
                        "the network amplified disagreement", -reduction));
            }
        }

        return insights;
    }

    private void findBridgeNodes(Graph<String, Edge> graph,
                                  Map<String, Double> opinions,
                                  List<String> insights) {
        // Find nodes connecting groups with different opinions
        String bestBridge = null;
        double bestScore = 0;

        for (String v : graph.getVertices()) {
            Collection<String> neighbors = graph.getNeighbors(v);
            if (neighbors == null || neighbors.size() < 2) continue;

            double myOp = opinions.getOrDefault(v, 0.5);
            double maxDiff = 0;
            Set<String> lowNeigh = new HashSet<>(), highNeigh = new HashSet<>();
            for (String n : neighbors) {
                double nOp = opinions.getOrDefault(n, 0.5);
                if (nOp < myOp - 0.1) lowNeigh.add(n);
                else if (nOp > myOp + 0.1) highNeigh.add(n);
                maxDiff = Math.max(maxDiff, Math.abs(nOp - myOp));
            }
            double bridgeScore = maxDiff * Math.min(lowNeigh.size(), highNeigh.size());
            if (bridgeScore > bestScore) {
                bestScore = bridgeScore;
                bestBridge = v;
            }
        }

        if (bestBridge != null && bestScore > 0.3) {
            insights.add(String.format("Node '%s' acts as an opinion bridge between " +
                    "groups with divergent views (bridge score=%.2f)", bestBridge, bestScore));
        }
    }

    private double computeHealthScore(OpinionDynamicsReport report) {
        double score = 100.0;

        // Polarization penalty
        if (report.polarization != null) {
            switch (report.polarization.severity) {
                case "extreme": score -= 30; break;
                case "severe": score -= 25; break;
                case "moderate": score -= 15; break;
                case "mild": score -= 8; break;
            }
        }

        // Echo chamber penalty
        int ecCount = Math.min(report.echoChambers.size(), 2);
        score -= ecCount * 15;

        // No convergence penalty
        if (report.forecast != null && !report.forecast.consensusReached) {
            if ("polarizing".equals(report.forecast.verdict)) {
                score -= 20;
            } else if ("stalled".equals(report.forecast.verdict)) {
                score -= 15;
            } else if ("oscillating".equals(report.forecast.verdict)) {
                score -= 10;
            }
        }

        // Extreme opinions penalty
        long extremeCount = report.finalOpinions.values().stream()
                .filter(v -> v < 0.05 || v > 0.95).count();
        if (extremeCount > report.finalOpinions.size() * 0.3) {
            score -= 10;
        }

        return Math.max(0, Math.min(100, score));
    }

    private String renderHistogramHtml(Collection<Double> values) {
        int[] bins = new int[10];
        for (double v : values) {
            int b = Math.min(9, (int) (v * 10));
            bins[b]++;
        }
        int maxBin = Arrays.stream(bins).max().orElse(1);

        StringBuilder sb = new StringBuilder();
        sb.append("<div style='margin-top:8px'>");
        for (int i = 0; i < 10; i++) {
            int pct = (maxBin > 0) ? (bins[i] * 100 / maxBin) : 0;
            sb.append(String.format("<div style='display:flex;align-items:center;margin:2px 0'>" +
                            "<span style='width:50px;font-size:0.75rem;color:#94a3b8'>%.1f-%.1f</span>" +
                            "<span class='hist-bar' style='width:%d%%'></span>" +
                            "<span style='margin-left:4px;font-size:0.75rem;color:#64748b'>%d</span></div>",
                    i * 0.1, (i + 1) * 0.1, Math.max(2, pct), bins[i]));
        }
        sb.append("</div>");
        return sb.toString();
    }

    private OpinionDynamicsReport emptyReport(String model) {
        OpinionDynamicsReport r = new OpinionDynamicsReport();
        r.model = model;
        r.steps = 0;
        r.healthScore = 100;
        r.polarization = new PolarizationMetrics();
        r.polarization.severity = "none";
        r.forecast = new ConsensusForecast();
        r.forecast.verdict = "stalled";
        r.insights.add("Empty graph — no opinion dynamics to analyze");
        return r;
    }

    private static String formatMembers(Set<String> members) {
        if (members.size() <= 6) return String.join(", ", members);
        List<String> list = new ArrayList<>(members);
        return list.subList(0, 5).stream().collect(Collectors.joining(", "))
                + " (+" + (members.size() - 5) + " more)";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
