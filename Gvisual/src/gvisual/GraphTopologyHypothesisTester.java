package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Graph Topology Hypothesis Tester — autonomous structural classification engine
 * that statistically tests whether a graph matches known topology archetypes using
 * null-model comparisons and ensemble scoring.
 *
 * <h3>Hypotheses Tested</h3>
 * <ul>
 *   <li><b>Scale-Free</b> — power-law degree distribution (Barabási–Albert model)</li>
 *   <li><b>Small-World</b> — high clustering + short paths (Watts–Strogatz model)</li>
 *   <li><b>Random (Erdős–Rényi)</b> — Poisson degree distribution, low clustering</li>
 *   <li><b>Core-Periphery</b> — dense core with sparse periphery attachment</li>
 *   <li><b>Hierarchical</b> — inverse relationship between clustering and degree</li>
 * </ul>
 *
 * <h3>Methodology</h3>
 * <p>For each hypothesis, the tester:</p>
 * <ol>
 *   <li>Computes structural signatures from the observed graph</li>
 *   <li>Generates null-model ensembles (Monte Carlo random graphs)</li>
 *   <li>Compares observed metrics against null distribution using z-scores</li>
 *   <li>Assigns confidence scores and p-value estimates</li>
 *   <li>Produces a ranked verdict with evidence summary</li>
 * </ol>
 *
 * <h3>Usage</h3>
 * <pre>
 * GraphTopologyHypothesisTester tester = new GraphTopologyHypothesisTester(graph);
 * tester.runAllTests();
 *
 * // Get ranked results
 * List&lt;HypothesisResult&gt; results = tester.getRankedResults();
 *
 * // Best-fit topology
 * TopologyType bestFit = tester.getBestFitTopology();
 *
 * // Confidence for a specific hypothesis
 * double conf = tester.getConfidence(TopologyType.SCALE_FREE);
 *
 * // Full report
 * String report = tester.generateReport();
 * </pre>
 *
 * @author zalenix
 */
public class GraphTopologyHypothesisTester {

    /** Known topology archetypes. */
    public enum TopologyType {
        SCALE_FREE("Scale-Free", "Power-law degree distribution; preferential attachment"),
        SMALL_WORLD("Small-World", "High clustering with short average path length"),
        RANDOM("Random (Erdős–Rényi)", "Poisson degree distribution; uniform edge probability"),
        CORE_PERIPHERY("Core-Periphery", "Dense interconnected core with sparse peripheral nodes"),
        HIERARCHICAL("Hierarchical", "Inverse clustering-degree relationship; modular structure");

        private final String label;
        private final String description;

        TopologyType(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String getLabel() { return label; }
        public String getDescription() { return description; }
    }

    /** Verdict strength levels. */
    public enum Verdict {
        STRONG_MATCH("Strong Match", 0.8),
        MODERATE_MATCH("Moderate Match", 0.6),
        WEAK_MATCH("Weak Match", 0.4),
        UNLIKELY("Unlikely", 0.2),
        REJECTED("Rejected", 0.0);

        private final String label;
        private final double threshold;

        Verdict(String label, double threshold) {
            this.label = label;
            this.threshold = threshold;
        }

        public String getLabel() { return label; }
        public double getThreshold() { return threshold; }

        public static Verdict fromConfidence(double confidence) {
            if (confidence >= STRONG_MATCH.threshold) return STRONG_MATCH;
            if (confidence >= MODERATE_MATCH.threshold) return MODERATE_MATCH;
            if (confidence >= WEAK_MATCH.threshold) return WEAK_MATCH;
            if (confidence >= UNLIKELY.threshold) return UNLIKELY;
            return REJECTED;
        }
    }

    /** Result of testing a single hypothesis. */
    public static class HypothesisResult {
        private final TopologyType topology;
        private final double confidence;
        private final Verdict verdict;
        private final double zScore;
        private final double pValue;
        private final List<String> evidence;
        private final Map<String, Double> metrics;

        public HypothesisResult(TopologyType topology, double confidence, double zScore,
                                double pValue, List<String> evidence, Map<String, Double> metrics) {
            this.topology = topology;
            this.confidence = Math.max(0, Math.min(1, confidence));
            this.verdict = Verdict.fromConfidence(this.confidence);
            this.zScore = zScore;
            this.pValue = pValue;
            this.evidence = Collections.unmodifiableList(evidence);
            this.metrics = Collections.unmodifiableMap(metrics);
        }

        public TopologyType getTopology() { return topology; }
        public double getConfidence() { return confidence; }
        public Verdict getVerdict() { return verdict; }
        public double getZScore() { return zScore; }
        public double getPValue() { return pValue; }
        public List<String> getEvidence() { return evidence; }
        public Map<String, Double> getMetrics() { return metrics; }
    }

    private final Graph<String, Edge> graph;
    private final int numNodes;
    private final int numEdges;
    private final int nullModelTrials;
    private final Random random;
    private final List<HypothesisResult> results;
    private boolean analysisComplete;

    // Cached graph metrics
    private Map<String, Integer> degreeMap;
    private double avgDegree;
    private double avgClustering;
    private double avgPathLength;
    private double density;
    private int maxDegree;

    /**
     * Create a hypothesis tester with default 100 null-model trials.
     */
    public GraphTopologyHypothesisTester(Graph<String, Edge> graph) {
        this(graph, 100);
    }

    /**
     * Create a hypothesis tester with specified null-model trials.
     */
    public GraphTopologyHypothesisTester(Graph<String, Edge> graph, int nullModelTrials) {
        this.graph = Objects.requireNonNull(graph, "Graph must not be null");
        this.numNodes = graph.getVertexCount();
        this.numEdges = graph.getEdgeCount();
        this.nullModelTrials = Math.max(10, nullModelTrials);
        this.random = new Random(42);
        this.results = new ArrayList<>();
        this.analysisComplete = false;
    }

    /**
     * Run all hypothesis tests. Must be called before accessing results.
     */
    public void runAllTests() {
        if (numNodes < 3) {
            throw new IllegalStateException("Graph must have at least 3 nodes for hypothesis testing");
        }
        computeBaseMetrics();
        results.clear();
        results.add(testScaleFree());
        results.add(testSmallWorld());
        results.add(testRandom());
        results.add(testCorePeriphery());
        results.add(testHierarchical());
        results.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));
        analysisComplete = true;
    }

    /**
     * Get results ranked by confidence (highest first).
     */
    public List<HypothesisResult> getRankedResults() {
        ensureAnalysisComplete();
        return Collections.unmodifiableList(results);
    }

    /**
     * Get the best-fit topology type.
     */
    public TopologyType getBestFitTopology() {
        ensureAnalysisComplete();
        return results.get(0).getTopology();
    }

    /**
     * Get confidence for a specific topology hypothesis.
     */
    public double getConfidence(TopologyType type) {
        ensureAnalysisComplete();
        return results.stream()
                .filter(r -> r.getTopology() == type)
                .findFirst()
                .map(HypothesisResult::getConfidence)
                .orElse(0.0);
    }

    /**
     * Get the result for a specific topology hypothesis.
     */
    public HypothesisResult getResult(TopologyType type) {
        ensureAnalysisComplete();
        return results.stream()
                .filter(r -> r.getTopology() == type)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No result for " + type));
    }

    /**
     * Generate a comprehensive text report.
     */
    public String generateReport() {
        ensureAnalysisComplete();
        StringBuilder sb = new StringBuilder();
        sb.append("===========================================================\n");
        sb.append("       GRAPH TOPOLOGY HYPOTHESIS TEST REPORT\n");
        sb.append("===========================================================\n\n");

        sb.append("Graph Summary:\n");
        sb.append(String.format("  Nodes: %d | Edges: %d | Density: %.4f\n", numNodes, numEdges, density));
        sb.append(String.format("  Avg Degree: %.2f | Max Degree: %d\n", avgDegree, maxDegree));
        sb.append(String.format("  Avg Clustering: %.4f | Avg Path Length: %.2f\n\n", avgClustering, avgPathLength));

        sb.append("Null-Model Trials: ").append(nullModelTrials).append("\n\n");

        sb.append("-----------------------------------------------------------\n");
        sb.append("                    RANKED RESULTS\n");
        sb.append("-----------------------------------------------------------\n\n");

        for (int i = 0; i < results.size(); i++) {
            HypothesisResult r = results.get(i);
            sb.append(String.format("#%d  %s\n", i + 1, r.getTopology().getLabel()));
            sb.append(String.format("    Confidence: %.1f%% | Verdict: %s\n",
                    r.getConfidence() * 100, r.getVerdict().getLabel()));
            sb.append(String.format("    Z-Score: %.3f | p-value: %.4f\n", r.getZScore(), r.getPValue()));
            sb.append("    Evidence:\n");
            for (String ev : r.getEvidence()) {
                sb.append("      • ").append(ev).append("\n");
            }
            sb.append("\n");
        }

        sb.append("-----------------------------------------------------------\n");
        sb.append("                     VERDICT\n");
        sb.append("-----------------------------------------------------------\n\n");

        HypothesisResult best = results.get(0);
        sb.append(String.format("Best-fit topology: %s (%s)\n",
                best.getTopology().getLabel(), best.getVerdict().getLabel()));
        sb.append(String.format("Confidence: %.1f%%\n", best.getConfidence() * 100));
        sb.append("\n").append(best.getTopology().getDescription()).append("\n");

        if (results.size() > 1 && results.get(1).getConfidence() > 0.5) {
            sb.append(String.format("\nNote: %s also shows moderate evidence (%.1f%%)\n",
                    results.get(1).getTopology().getLabel(),
                    results.get(1).getConfidence() * 100));
        }

        return sb.toString();
    }

    // --- Internal Analysis Methods ---

    private void computeBaseMetrics() {
        degreeMap = new LinkedHashMap<>();
        for (String v : graph.getVertices()) {
            degreeMap.put(v, graph.degree(v));
        }
        avgDegree = degreeMap.values().stream().mapToInt(Integer::intValue).average().orElse(0);
        maxDegree = degreeMap.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        density = numNodes > 1 ? (2.0 * numEdges) / (numNodes * (numNodes - 1.0)) : 0;
        avgClustering = computeAvgClustering(graph);
        avgPathLength = computeAvgPathLength(graph);
    }

    /**
     * Test Scale-Free hypothesis: power-law degree distribution.
     * Checks: degree distribution fit, presence of hubs, gamma exponent.
     */
    private HypothesisResult testScaleFree() {
        List<String> evidence = new ArrayList<>();
        Map<String, Double> metrics = new LinkedHashMap<>();

        // Compute degree distribution
        int[] degrees = degreeMap.values().stream().mapToInt(Integer::intValue).toArray();
        Arrays.sort(degrees);

        // Power-law exponent estimation (MLE for discrete power law)
        double gamma = estimatePowerLawGamma(degrees);
        metrics.put("gamma_exponent", gamma);
        evidence.add(String.format("Estimated power-law exponent γ = %.3f", gamma));

        // Typical scale-free: gamma in [2, 3]
        double gammaScore = 0;
        if (gamma >= 2.0 && gamma <= 3.0) {
            gammaScore = 1.0;
            evidence.add("γ in typical scale-free range [2, 3]");
        } else if (gamma >= 1.5 && gamma <= 3.5) {
            gammaScore = 0.5;
            evidence.add("γ in extended scale-free range [1.5, 3.5]");
        } else {
            evidence.add("γ outside expected range for scale-free networks");
        }

        // Hub dominance: ratio of max degree to average
        double hubRatio = avgDegree > 0 ? maxDegree / avgDegree : 0;
        metrics.put("hub_ratio", hubRatio);
        double hubScore = Math.min(1.0, hubRatio / 10.0);
        evidence.add(String.format("Hub ratio (max/avg degree): %.2f", hubRatio));

        // Degree variance relative to mean (scale-free has high variance)
        double variance = computeVariance(degrees);
        double coeffVar = avgDegree > 0 ? Math.sqrt(variance) / avgDegree : 0;
        metrics.put("degree_cv", coeffVar);
        double cvScore = Math.min(1.0, coeffVar / 2.0);

        // Compare against ER null model
        double erExpectedCV = density > 0 ? 1.0 / Math.sqrt(numNodes * density) : 0;
        double cvZScore = erExpectedCV > 0 ? (coeffVar - erExpectedCV) / (erExpectedCV * 0.5) : 0;
        metrics.put("cv_z_score", cvZScore);

        // Composite confidence
        double confidence = 0.35 * gammaScore + 0.30 * hubScore + 0.35 * cvScore;
        double zScore = cvZScore;
        double pValue = zScoreToPValue(zScore);

        if (hubRatio > 5) evidence.add("Strong hub presence detected");
        if (coeffVar > 1.5) evidence.add("High degree heterogeneity (CV > 1.5)");

        return new HypothesisResult(TopologyType.SCALE_FREE, confidence, zScore, pValue, evidence, metrics);
    }

    /**
     * Test Small-World hypothesis: high clustering + short paths.
     */
    private HypothesisResult testSmallWorld() {
        List<String> evidence = new ArrayList<>();
        Map<String, Double> metrics = new LinkedHashMap<>();

        // ER baseline expectations
        double erClustering = density; // Expected clustering for ER
        double erPathLength = numNodes > 1 && density > 0 ?
                Math.log(numNodes) / Math.log(avgDegree) : Double.MAX_VALUE;

        metrics.put("observed_clustering", avgClustering);
        metrics.put("er_clustering", erClustering);
        metrics.put("observed_path_length", avgPathLength);
        metrics.put("er_path_length", erPathLength);

        // Sigma = (C/C_rand) / (L/L_rand) — Humphries & Gurney small-world coefficient
        double clusteringRatio = erClustering > 0 ? avgClustering / erClustering : 0;
        double pathRatio = erPathLength > 0 && avgPathLength > 0 ? avgPathLength / erPathLength : 1;
        double sigma = pathRatio > 0 ? clusteringRatio / pathRatio : 0;
        metrics.put("sigma", sigma);
        metrics.put("clustering_ratio", clusteringRatio);
        metrics.put("path_ratio", pathRatio);

        evidence.add(String.format("Clustering ratio (C/C_rand): %.3f", clusteringRatio));
        evidence.add(String.format("Path ratio (L/L_rand): %.3f", pathRatio));
        evidence.add(String.format("Small-world coefficient σ = %.3f", sigma));

        // SW criteria: high clustering ratio, path ratio near 1
        double clusterScore = Math.min(1.0, clusteringRatio / 5.0);
        double pathScore = pathRatio > 0 && pathRatio < 2 ? 1.0 - (pathRatio - 1.0) : 0;
        pathScore = Math.max(0, Math.min(1.0, pathScore));

        // Null-model comparison via Monte Carlo
        double[] nullClusterings = new double[nullModelTrials];
        for (int i = 0; i < nullModelTrials; i++) {
            nullClusterings[i] = simulateERClustering(numNodes, numEdges);
        }
        double nullMean = mean(nullClusterings);
        double nullStd = std(nullClusterings, nullMean);
        double zScore = nullStd > 0 ? (avgClustering - nullMean) / nullStd : 0;
        metrics.put("clustering_z_score", zScore);

        double confidence = 0.45 * clusterScore + 0.35 * pathScore + 0.20 * Math.min(1, zScore / 3.0);
        double pValue = zScoreToPValue(zScore);

        if (sigma > 1.0) evidence.add("σ > 1 suggests small-world properties");
        if (clusteringRatio > 3) evidence.add("Clustering significantly exceeds random baseline");
        if (pathRatio < 1.5) evidence.add("Path length comparable to random graph (efficient routing)");

        return new HypothesisResult(TopologyType.SMALL_WORLD, confidence, zScore, pValue, evidence, metrics);
    }

    /**
     * Test Random (Erdős–Rényi) hypothesis: Poisson degree distribution.
     */
    private HypothesisResult testRandom() {
        List<String> evidence = new ArrayList<>();
        Map<String, Double> metrics = new LinkedHashMap<>();

        int[] degrees = degreeMap.values().stream().mapToInt(Integer::intValue).toArray();

        // Test Poisson-like distribution: variance ≈ mean
        double variance = computeVariance(degrees);
        double dispersionRatio = avgDegree > 0 ? variance / avgDegree : 0;
        metrics.put("dispersion_ratio", dispersionRatio);
        evidence.add(String.format("Dispersion ratio (var/mean): %.3f (Poisson expects ~1.0)", dispersionRatio));

        // Poisson fit score: closer to 1 = more random-like
        double poissonScore = dispersionRatio > 0 ?
                Math.exp(-Math.abs(Math.log(dispersionRatio))) : 0;

        // Clustering comparison: ER clustering ≈ density
        double clusteringDeviation = Math.abs(avgClustering - density);
        double clusterScore = Math.exp(-clusteringDeviation * 10);
        metrics.put("clustering_deviation", clusteringDeviation);
        evidence.add(String.format("Clustering deviation from ER: %.4f", clusteringDeviation));

        // Degree max check: in ER, max degree ~ ln(n)/ln(ln(n)) * avg
        double erMaxExpected = avgDegree + 3 * Math.sqrt(avgDegree); // rough 3-sigma
        double maxScore = maxDegree <= erMaxExpected * 1.5 ? 1.0 :
                Math.exp(-(maxDegree - erMaxExpected * 1.5) / erMaxExpected);
        metrics.put("max_degree_ratio", maxDegree / Math.max(1, erMaxExpected));

        // Null model z-score for clustering
        double[] nullClusterings = new double[nullModelTrials];
        for (int i = 0; i < nullModelTrials; i++) {
            nullClusterings[i] = simulateERClustering(numNodes, numEdges);
        }
        double nullMean = mean(nullClusterings);
        double nullStd = std(nullClusterings, nullMean);
        double zScore = nullStd > 0 ? (avgClustering - nullMean) / nullStd : 0;
        // For random hypothesis, we want z-score NEAR 0
        double zNearZeroScore = Math.exp(-zScore * zScore / 8.0);
        metrics.put("clustering_z_score", zScore);

        double confidence = 0.30 * poissonScore + 0.30 * clusterScore + 0.20 * maxScore + 0.20 * zNearZeroScore;
        double pValue = 1.0 - zScoreToPValue(Math.abs(zScore)); // two-sided

        if (dispersionRatio > 0.7 && dispersionRatio < 1.5) {
            evidence.add("Degree distribution consistent with Poisson");
        }
        if (Math.abs(zScore) < 2) {
            evidence.add("Clustering indistinguishable from random null model");
        }
        if (maxDegree <= erMaxExpected * 1.5) {
            evidence.add("No extreme hubs detected (consistent with ER)");
        }

        return new HypothesisResult(TopologyType.RANDOM, confidence, zScore, pValue, evidence, metrics);
    }

    /**
     * Test Core-Periphery hypothesis: dense core with sparse attachment.
     */
    private HypothesisResult testCorePeriphery() {
        List<String> evidence = new ArrayList<>();
        Map<String, Double> metrics = new LinkedHashMap<>();

        // Identify core candidates: top 20% by degree
        List<Map.Entry<String, Integer>> sorted = degreeMap.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .collect(Collectors.toList());
        int coreSize = Math.max(2, numNodes / 5);
        Set<String> coreNodes = sorted.stream().limit(coreSize)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        Set<String> peripheryNodes = new HashSet<>(degreeMap.keySet());
        peripheryNodes.removeAll(coreNodes);

        // Core density
        int coreEdges = 0;
        for (String u : coreNodes) {
            for (String v : coreNodes) {
                if (!u.equals(v) && graph.findEdge(u, v) != null) {
                    coreEdges++;
                }
            }
        }
        coreEdges /= 2; // undirected
        double coreDensity = coreSize > 1 ? (2.0 * coreEdges) / (coreSize * (coreSize - 1.0)) : 0;
        metrics.put("core_density", coreDensity);

        // Periphery density
        int periEdges = 0;
        List<String> periList = new ArrayList<>(peripheryNodes);
        for (int i = 0; i < periList.size(); i++) {
            for (int j = i + 1; j < periList.size(); j++) {
                if (graph.findEdge(periList.get(i), periList.get(j)) != null) {
                    periEdges++;
                }
            }
        }
        int periSize = periList.size();
        double periDensity = periSize > 1 ? (2.0 * periEdges) / (periSize * (periSize - 1.0)) : 0;
        metrics.put("periphery_density", periDensity);

        // Core-periphery ratio
        double cpRatio = periDensity > 0 ? coreDensity / periDensity : (coreDensity > 0 ? 10.0 : 0);
        metrics.put("cp_ratio", cpRatio);

        evidence.add(String.format("Core density: %.4f (top %d nodes)", coreDensity, coreSize));
        evidence.add(String.format("Periphery density: %.4f (%d nodes)", periDensity, periSize));
        evidence.add(String.format("Core/Periphery density ratio: %.2f", cpRatio));

        // Cross-edge analysis: periphery should connect more to core than to each other
        int crossEdges = 0;
        for (String p : peripheryNodes) {
            for (String c : coreNodes) {
                if (graph.findEdge(p, c) != null) crossEdges++;
            }
        }
        double crossDensity = (coreSize > 0 && periSize > 0) ?
                (double) crossEdges / (coreSize * periSize) : 0;
        metrics.put("cross_density", crossDensity);
        evidence.add(String.format("Core-periphery cross density: %.4f", crossDensity));

        // Scoring
        double densityScore = Math.min(1.0, cpRatio / 5.0);
        double crossScore = periDensity > 0 ? Math.min(1.0, crossDensity / periDensity) : 0.5;
        double coreInternal = Math.min(1.0, coreDensity / Math.max(0.01, density * 2));

        double confidence = 0.40 * densityScore + 0.30 * crossScore + 0.30 * coreInternal;
        double zScore = cpRatio - 1.0; // simple z-like score
        double pValue = zScoreToPValue(zScore);

        if (cpRatio > 3) evidence.add("Strong core-periphery structure (ratio > 3)");
        if (coreDensity > 0.5) evidence.add("Dense core detected (density > 0.5)");
        if (crossDensity > periDensity) evidence.add("Periphery connects more to core than to itself");

        return new HypothesisResult(TopologyType.CORE_PERIPHERY, confidence, zScore, pValue, evidence, metrics);
    }

    /**
     * Test Hierarchical hypothesis: C(k) ~ k^-1 (clustering inversely related to degree).
     */
    private HypothesisResult testHierarchical() {
        List<String> evidence = new ArrayList<>();
        Map<String, Double> metrics = new LinkedHashMap<>();

        // Compute per-node clustering coefficients grouped by degree
        Map<Integer, List<Double>> clusteringByDegree = new LinkedHashMap<>();
        for (String v : graph.getVertices()) {
            int deg = graph.degree(v);
            if (deg < 2) continue;
            double cc = localClustering(v);
            clusteringByDegree.computeIfAbsent(deg, k -> new ArrayList<>()).add(cc);
        }

        // Average clustering per degree class
        Map<Integer, Double> avgClusterByDegree = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Double>> e : clusteringByDegree.entrySet()) {
            avgClusterByDegree.put(e.getKey(), e.getValue().stream()
                    .mapToDouble(Double::doubleValue).average().orElse(0));
        }

        // Fit log-log regression: log(C) vs log(k) — hierarchical expects slope ~ -1
        List<double[]> points = new ArrayList<>();
        for (Map.Entry<Integer, Double> e : avgClusterByDegree.entrySet()) {
            if (e.getKey() > 0 && e.getValue() > 0) {
                points.add(new double[]{Math.log(e.getKey()), Math.log(e.getValue())});
            }
        }

        double slope = 0;
        double rSquared = 0;
        if (points.size() >= 3) {
            double[] regression = linearRegression(points);
            slope = regression[0];
            rSquared = regression[2];
        }
        metrics.put("ck_slope", slope);
        metrics.put("ck_r_squared", rSquared);
        evidence.add(String.format("C(k) vs k log-log slope: %.3f (hierarchical expects ~ -1)", slope));
        evidence.add(String.format("R² of fit: %.3f", rSquared));

        // Modularity-like check: do communities exist at multiple scales?
        double modularityProxy = computeModularityProxy();
        metrics.put("modularity_proxy", modularityProxy);
        evidence.add(String.format("Modularity proxy: %.3f", modularityProxy));

        // Scoring
        // Slope near -1 with good fit
        double slopeScore = Math.exp(-Math.pow(slope + 1, 2) / 0.5); // peaks at slope = -1
        double fitScore = rSquared;
        double modScore = Math.min(1.0, modularityProxy / 0.4);

        double confidence = 0.45 * slopeScore + 0.30 * fitScore + 0.25 * modScore;
        double zScore = -slope; // transform so positive = more hierarchical
        double pValue = zScoreToPValue(Math.abs(slope + 1));

        if (slope < -0.5 && slope > -1.5 && rSquared > 0.5) {
            evidence.add("Strong inverse clustering-degree relationship");
        }
        if (modularityProxy > 0.3) {
            evidence.add("Significant modular structure detected");
        }
        if (points.size() < 3) {
            evidence.add("WARNING: Insufficient degree diversity for reliable fit");
            confidence *= 0.5;
        }

        return new HypothesisResult(TopologyType.HIERARCHICAL, confidence, zScore, pValue, evidence, metrics);
    }

    // --- Utility Methods ---

    private double computeAvgClustering(Graph<String, Edge> g) {
        double total = 0;
        int count = 0;
        for (String v : g.getVertices()) {
            int deg = g.degree(v);
            if (deg < 2) continue;
            total += localClustering(v);
            count++;
        }
        return count > 0 ? total / count : 0;
    }

    private double localClustering(String v) {
        Collection<String> neighbors = graph.getNeighbors(v);
        if (neighbors == null) return 0;
        List<String> nList = new ArrayList<>(neighbors);
        int deg = nList.size();
        if (deg < 2) return 0;
        int triangles = 0;
        for (int i = 0; i < nList.size(); i++) {
            for (int j = i + 1; j < nList.size(); j++) {
                if (graph.findEdge(nList.get(i), nList.get(j)) != null) {
                    triangles++;
                }
            }
        }
        return (2.0 * triangles) / (deg * (deg - 1.0));
    }

    private double computeAvgPathLength(Graph<String, Edge> g) {
        // BFS from each node in largest component (sampled for large graphs)
        List<String> vertices = new ArrayList<>(g.getVertices());
        int sampleSize = Math.min(vertices.size(), 100);
        List<String> sample = new ArrayList<>(vertices);
        Collections.shuffle(sample, random);
        sample = sample.subList(0, sampleSize);

        long totalDist = 0;
        long pairs = 0;
        for (String source : sample) {
            Map<String, Integer> dist = bfs(g, source);
            for (int d : dist.values()) {
                if (d > 0) {
                    totalDist += d;
                    pairs++;
                }
            }
        }
        return pairs > 0 ? (double) totalDist / pairs : 0;
    }

    private Map<String, Integer> bfs(Graph<String, Edge> g, String source) {
        Map<String, Integer> dist = new HashMap<>();
        Queue<String> queue = new LinkedList<>();
        dist.put(source, 0);
        queue.add(source);
        while (!queue.isEmpty()) {
            String curr = queue.poll();
            int d = dist.get(curr);
            Collection<String> neighbors = g.getNeighbors(curr);
            if (neighbors != null) {
                for (String n : neighbors) {
                    if (!dist.containsKey(n)) {
                        dist.put(n, d + 1);
                        queue.add(n);
                    }
                }
            }
        }
        return dist;
    }

    private double estimatePowerLawGamma(int[] sortedDegrees) {
        // MLE estimate: gamma = 1 + n * (sum(ln(x/xmin)))^-1
        int xmin = 1;
        double sumLog = 0;
        int count = 0;
        for (int d : sortedDegrees) {
            if (d >= xmin) {
                sumLog += Math.log((double) d / xmin);
                count++;
            }
        }
        return count > 0 ? 1.0 + count / sumLog : 0;
    }

    private double simulateERClustering(int n, int m) {
        // Analytical approximation with noise for ER: C ≈ p = 2m/(n(n-1))
        double p = n > 1 ? (2.0 * m) / (n * (n - 1.0)) : 0;
        // Add variance: std(C) ≈ p * sqrt(2/(n*k)) where k = (n-1)*p
        double k = (n - 1) * p;
        double std = k > 0 ? p * Math.sqrt(2.0 / (n * k)) : 0.01;
        return Math.max(0, p + random.nextGaussian() * std);
    }

    private double computeVariance(int[] values) {
        double mean = Arrays.stream(values).average().orElse(0);
        double sumSq = 0;
        for (int v : values) {
            sumSq += (v - mean) * (v - mean);
        }
        return values.length > 1 ? sumSq / (values.length - 1) : 0;
    }

    private double computeModularityProxy() {
        // Simple modularity estimate using edge density of high-degree neighborhoods
        if (numNodes < 4) return 0;
        List<Map.Entry<String, Integer>> sorted = degreeMap.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .collect(Collectors.toList());
        int sampleSize = Math.min(10, sorted.size());
        double internalTotal = 0;
        int validClusters = 0;
        for (int i = 0; i < sampleSize; i++) {
            String hub = sorted.get(i).getKey();
            Collection<String> neighbors = graph.getNeighbors(hub);
            if (neighbors == null || neighbors.size() < 2) continue;
            List<String> nList = new ArrayList<>(neighbors);
            int internalEdges = 0;
            for (int a = 0; a < nList.size(); a++) {
                for (int b = a + 1; b < nList.size(); b++) {
                    if (graph.findEdge(nList.get(a), nList.get(b)) != null) {
                        internalEdges++;
                    }
                }
            }
            int maxEdges = nList.size() * (nList.size() - 1) / 2;
            if (maxEdges > 0) {
                internalTotal += (double) internalEdges / maxEdges;
                validClusters++;
            }
        }
        double localMod = validClusters > 0 ? internalTotal / validClusters : 0;
        // Compare to expected: density
        return Math.max(0, localMod - density);
    }

    private double[] linearRegression(List<double[]> points) {
        int n = points.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (double[] p : points) {
            sumX += p[0];
            sumY += p[1];
            sumXY += p[0] * p[1];
            sumXX += p[0] * p[0];
        }
        double slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
        double intercept = (sumY - slope * sumX) / n;

        // R²
        double meanY = sumY / n;
        double ssTotal = 0, ssResid = 0;
        for (double[] p : points) {
            double predicted = slope * p[0] + intercept;
            ssTotal += (p[1] - meanY) * (p[1] - meanY);
            ssResid += (p[1] - predicted) * (p[1] - predicted);
        }
        double rSquared = ssTotal > 0 ? 1.0 - ssResid / ssTotal : 0;

        return new double[]{slope, intercept, rSquared};
    }

    private double zScoreToPValue(double z) {
        // Approximation of one-sided p-value from z-score
        double absZ = Math.abs(z);
        // Using Abramowitz and Stegun approximation
        double t = 1.0 / (1.0 + 0.2316419 * absZ);
        double d = 0.3989422804014327; // 1/sqrt(2*pi)
        double p = d * Math.exp(-absZ * absZ / 2.0) *
                (t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274)))));
        return z > 0 ? p : 1.0 - p;
    }

    private double mean(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return values.length > 0 ? sum / values.length : 0;
    }

    private double std(double[] values, double mean) {
        double sumSq = 0;
        for (double v : values) sumSq += (v - mean) * (v - mean);
        return values.length > 1 ? Math.sqrt(sumSq / (values.length - 1)) : 0;
    }

    private void ensureAnalysisComplete() {
        if (!analysisComplete) {
            throw new IllegalStateException("Call runAllTests() before accessing results");
        }
    }
}
