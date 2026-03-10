package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Graph Network Profiler — computes a comprehensive structural profile of a
 * graph and classifies it into a network type (social, biological,
 * technological, random, lattice, tree-like) based on topological signatures.
 *
 * <p>Computes 12 structural metrics, derives a network fingerprint, then
 * matches against known reference profiles to classify the network. Generates
 * a detailed report (text or HTML) with per-metric grades and an overall
 * network health score.</p>
 *
 * <h3>Metrics computed:</h3>
 * <ul>
 *   <li>Density</li>
 *   <li>Average degree</li>
 *   <li>Degree variance (heterogeneity)</li>
 *   <li>Clustering coefficient (global)</li>
 *   <li>Approximate diameter (BFS from sample nodes)</li>
 *   <li>Assortativity (degree correlation)</li>
 *   <li>Connected component count &amp; largest component fraction</li>
 *   <li>Average path length (sampled)</li>
 *   <li>Power-law exponent estimate</li>
 *   <li>Small-world quotient (σ = C/C_rand ÷ L/L_rand)</li>
 *   <li>Hub dominance (max degree / avg degree)</li>
 *   <li>Edge density variance across communities</li>
 * </ul>
 *
 * <h3>Network classifications:</h3>
 * <ul>
 *   <li><b>SOCIAL</b> — high clustering, low diameter, assortative</li>
 *   <li><b>SCALE_FREE</b> — power-law degree, high hub dominance, disassortative</li>
 *   <li><b>SMALL_WORLD</b> — high clustering, short paths, moderate degree variance</li>
 *   <li><b>RANDOM</b> — low clustering, Poisson-like degree distribution</li>
 *   <li><b>LATTICE</b> — regular degree, high diameter, high clustering</li>
 *   <li><b>TREE_LIKE</b> — low clustering, edges ≈ n-1, connected</li>
 *   <li><b>CORE_PERIPHERY</b> — dense core, sparse periphery</li>
 *   <li><b>UNKNOWN</b> — does not match any known profile</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * GraphNetworkProfiler profiler = new GraphNetworkProfiler(graph);
 * profiler.analyze();
 * System.out.println(profiler.getClassification());
 * System.out.println(profiler.getTextReport());
 * String html = profiler.getHtmlReport();
 * }</pre>
 *
 * @author zalenix
 */
public class GraphNetworkProfiler {

    /** Network type classifications. */
    public enum NetworkType {
        SOCIAL("Social Network", "High clustering, assortative mixing, community structure"),
        SCALE_FREE("Scale-Free Network", "Power-law degree distribution, hub-dominated"),
        SMALL_WORLD("Small-World Network", "High clustering with short average paths"),
        RANDOM("Random Network", "Erdős–Rényi-like, low clustering, Poisson degree"),
        LATTICE("Lattice/Grid", "Regular degree, high diameter, spatial structure"),
        TREE_LIKE("Tree-Like", "Minimal edges, no cycles, hierarchical"),
        CORE_PERIPHERY("Core-Periphery", "Dense core connected to sparse periphery"),
        UNKNOWN("Unclassified", "Does not match known network profiles");

        private final String displayName;
        private final String description;

        NetworkType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    /** Grade levels for individual metrics. */
    public enum Grade {
        A_PLUS("A+", 97), A("A", 93), A_MINUS("A-", 90),
        B_PLUS("B+", 87), B("B", 83), B_MINUS("B-", 80),
        C_PLUS("C+", 77), C("C", 73), C_MINUS("C-", 70),
        D("D", 60), F("F", 0);

        private final String label;
        private final int minScore;

        Grade(String label, int minScore) {
            this.label = label;
            this.minScore = minScore;
        }

        public String getLabel() { return label; }

        public static Grade fromScore(double score) {
            for (Grade g : values()) {
                if (score >= g.minScore) return g;
            }
            return F;
        }
    }

    /** Holds a single metric result. */
    public static class MetricResult {
        private final String name;
        private final String category;
        private final double value;
        private final double score;    // 0-100
        private final Grade grade;
        private final String interpretation;

        public MetricResult(String name, String category, double value,
                            double score, String interpretation) {
            this.name = name;
            this.category = category;
            this.value = value;
            this.score = Math.max(0, Math.min(100, score));
            this.grade = Grade.fromScore(this.score);
            this.interpretation = interpretation;
        }

        public String getName() { return name; }
        public String getCategory() { return category; }
        public double getValue() { return value; }
        public double getScore() { return score; }
        public Grade getGrade() { return grade; }
        public String getInterpretation() { return interpretation; }
    }

    private final Graph<String, edge> graph;
    private final Random random;
    private boolean analyzed;

    // Computed metrics
    private double density;
    private double avgDegree;
    private double degreeVariance;
    private double globalClustering;
    private int approxDiameter;
    private double assortativity;
    private int componentCount;
    private double largestComponentFraction;
    private double avgPathLength;
    private double powerLawExponent;
    private double smallWorldQuotient;
    private double hubDominance;

    private NetworkType classification;
    private double classificationConfidence;
    private List<MetricResult> metricResults;
    private double overallScore;

    private static final int SAMPLE_SIZE = 50;

    public GraphNetworkProfiler(Graph<String, edge> graph) {
        this(graph, new Random(42));
    }

    public GraphNetworkProfiler(Graph<String, edge> graph, Random random) {
        this.graph = Objects.requireNonNull(graph, "graph must not be null");
        this.random = random;
        this.analyzed = false;
        this.metricResults = new ArrayList<>();
    }

    // ── Analysis ────────────────────────────────────────────────

    /** Run full analysis. Must be called before querying results. */
    public void analyze() {
        int n = graph.getVertexCount();
        int m = graph.getEdgeCount();

        if (n == 0) {
            setEmptyDefaults();
            analyzed = true;
            return;
        }

        computeDensity(n, m);
        computeDegreeStats(n);
        computeClustering();
        computeDiameter(n);
        computeAssortativity();
        computeComponents(n);
        computeAvgPathLength(n);
        computePowerLawExponent();
        computeSmallWorldQuotient(n, m);
        computeHubDominance();

        buildMetricResults();
        classify();
        computeOverallScore();
        analyzed = true;
    }

    // ── Metric computation ──────────────────────────────────────

    private void computeDensity(int n, int m) {
        density = n <= 1 ? 0.0 : (2.0 * m) / (n * (n - 1.0));
    }

    private void computeDegreeStats(int n) {
        double sum = 0, sumSq = 0;
        for (String v : graph.getVertices()) {
            int d = graph.degree(v);
            sum += d;
            sumSq += d * d;
        }
        avgDegree = sum / n;
        degreeVariance = (sumSq / n) - (avgDegree * avgDegree);
    }

    private void computeClustering() {
        double totalCoeff = 0;
        int counted = 0;
        for (String v : graph.getVertices()) {
            Collection<String> neighbors = graph.getNeighbors(v);
            int k = neighbors.size();
            if (k < 2) continue;
            List<String> nList = new ArrayList<>(neighbors);
            int triangles = 0;
            for (int i = 0; i < nList.size(); i++) {
                for (int j = i + 1; j < nList.size(); j++) {
                    if (graph.isNeighbor(nList.get(i), nList.get(j))) {
                        triangles++;
                    }
                }
            }
            totalCoeff += (2.0 * triangles) / (k * (k - 1.0));
            counted++;
        }
        globalClustering = counted == 0 ? 0.0 : totalCoeff / counted;
    }

    private void computeDiameter(int n) {
        List<String> vertices = new ArrayList<>(graph.getVertices());
        int samples = Math.min(SAMPLE_SIZE, n);
        Collections.shuffle(vertices, random);
        int maxDist = 0;
        for (int i = 0; i < samples; i++) {
            Map<String, Integer> dist = bfs(vertices.get(i));
            for (int d : dist.values()) {
                if (d > maxDist) maxDist = d;
            }
        }
        approxDiameter = maxDist;
    }

    private void computeAssortativity() {
        if (graph.getEdgeCount() == 0) { assortativity = 0; return; }
        double sumProd = 0, sumI = 0, sumJ = 0, sumISq = 0, sumJSq = 0;
        int m = graph.getEdgeCount();
        for (edge e : graph.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            int di = graph.degree(v1);
            int dj = graph.degree(v2);
            sumProd += di * dj;
            sumI += di;
            sumJ += dj;
            sumISq += di * di;
            sumJSq += dj * dj;
        }
        double num = (sumProd / m) - Math.pow((sumI + sumJ) / (2.0 * m), 2);
        double den = 0.5 * ((sumISq / m) + (sumJSq / m))
                   - Math.pow((sumI + sumJ) / (2.0 * m), 2);
        assortativity = Math.abs(den) < 1e-10 ? 0.0 : num / den;
    }

    private void computeComponents(int n) {
        Set<String> visited = new HashSet<>();
        int count = 0;
        int largestSize = 0;
        for (String v : graph.getVertices()) {
            if (!visited.contains(v)) {
                Map<String, Integer> dist = bfs(v);
                visited.addAll(dist.keySet());
                count++;
                if (dist.size() > largestSize) largestSize = dist.size();
            }
        }
        componentCount = count;
        largestComponentFraction = n == 0 ? 0.0 : (double) largestSize / n;
    }

    private void computeAvgPathLength(int n) {
        List<String> vertices = new ArrayList<>(graph.getVertices());
        int samples = Math.min(SAMPLE_SIZE, n);
        Collections.shuffle(vertices, random);
        long totalDist = 0;
        long pairCount = 0;
        for (int i = 0; i < samples; i++) {
            Map<String, Integer> dist = bfs(vertices.get(i));
            for (int d : dist.values()) {
                if (d > 0) { totalDist += d; pairCount++; }
            }
        }
        avgPathLength = pairCount == 0 ? 0.0 : (double) totalDist / pairCount;
    }

    private void computePowerLawExponent() {
        List<Integer> degrees = new ArrayList<>();
        for (String v : graph.getVertices()) {
            int d = graph.degree(v);
            if (d > 0) degrees.add(d);
        }
        if (degrees.size() < 3) { powerLawExponent = 0; return; }
        // MLE for power-law exponent: α = 1 + n / Σ ln(d/dmin)
        int dmin = Collections.min(degrees);
        double sumLog = 0;
        for (int d : degrees) {
            sumLog += Math.log((double) d / dmin);
        }
        powerLawExponent = sumLog < 1e-10 ? 0 : 1.0 + degrees.size() / sumLog;
    }

    private void computeSmallWorldQuotient(int n, int m) {
        if (n <= 2 || m == 0) { smallWorldQuotient = 0; return; }
        // Expected random graph clustering: C_rand ≈ <k>/n
        double cRand = avgDegree / n;
        // Expected random graph path length: L_rand ≈ ln(n)/ln(<k>)
        double lRand = avgDegree <= 1 ? n : Math.log(n) / Math.log(avgDegree);
        if (cRand < 1e-10 || lRand < 1e-10 || avgPathLength < 1e-10) {
            smallWorldQuotient = 0;
            return;
        }
        double gamma = globalClustering / cRand;
        double lambda = avgPathLength / lRand;
        smallWorldQuotient = lambda < 1e-10 ? 0 : gamma / lambda;
    }

    private void computeHubDominance() {
        if (graph.getVertexCount() == 0 || avgDegree < 1e-10) {
            hubDominance = 0; return;
        }
        int maxDeg = 0;
        for (String v : graph.getVertices()) {
            maxDeg = Math.max(maxDeg, graph.degree(v));
        }
        hubDominance = maxDeg / avgDegree;
    }

    // ── Classification ──────────────────────────────────────────

    private void classify() {
        int n = graph.getVertexCount();
        int m = graph.getEdgeCount();

        // Score each type
        Map<NetworkType, Double> scores = new EnumMap<>(NetworkType.class);

        // TREE_LIKE: edges ≈ n-1, low clustering
        double treeEdgeRatio = n <= 1 ? 0 : (double) m / (n - 1);
        double treeScore = 0;
        if (treeEdgeRatio >= 0.8 && treeEdgeRatio <= 1.2) treeScore += 40;
        if (globalClustering < 0.05) treeScore += 30;
        if (largestComponentFraction > 0.8) treeScore += 15;
        if (hubDominance > 3) treeScore += 15;
        scores.put(NetworkType.TREE_LIKE, treeScore);

        // LATTICE: regular degree, high clustering, high diameter
        double degCV = avgDegree < 1e-10 ? 0 : Math.sqrt(degreeVariance) / avgDegree;
        double latticeScore = 0;
        if (degCV < 0.3) latticeScore += 35;
        if (globalClustering > 0.3) latticeScore += 25;
        if (n > 5 && approxDiameter > Math.sqrt(n)) latticeScore += 20;
        if (density < 0.3) latticeScore += 20;
        scores.put(NetworkType.LATTICE, latticeScore);

        // RANDOM: low clustering, moderate degree variance
        double randomScore = 0;
        if (globalClustering < 0.15) randomScore += 30;
        if (degCV < 0.8) randomScore += 20;
        if (Math.abs(assortativity) < 0.15) randomScore += 25;
        if (hubDominance < 3) randomScore += 25;
        scores.put(NetworkType.RANDOM, randomScore);

        // SCALE_FREE: power-law, high hub dominance, disassortative
        double sfScore = 0;
        if (powerLawExponent > 1.5 && powerLawExponent < 4.0) sfScore += 30;
        if (hubDominance > 4) sfScore += 25;
        if (assortativity < -0.05) sfScore += 20;
        if (degreeVariance > avgDegree * avgDegree) sfScore += 25;
        scores.put(NetworkType.SCALE_FREE, sfScore);

        // SMALL_WORLD: high clustering, short paths
        double swScore = 0;
        if (smallWorldQuotient > 1.5) swScore += 35;
        if (globalClustering > 0.2) swScore += 25;
        if (n > 5 && avgPathLength < 2 * Math.log(n)) swScore += 20;
        if (largestComponentFraction > 0.8) swScore += 20;
        scores.put(NetworkType.SMALL_WORLD, swScore);

        // SOCIAL: high clustering, assortative, community structure
        double socialScore = 0;
        if (globalClustering > 0.3) socialScore += 25;
        if (assortativity > 0.1) socialScore += 30;
        if (smallWorldQuotient > 1.0) socialScore += 20;
        if (componentCount > 1 || density < 0.5) socialScore += 25;
        scores.put(NetworkType.SOCIAL, socialScore);

        // CORE_PERIPHERY: wide degree spread, some high-degree core
        double cpScore = 0;
        if (hubDominance > 3) cpScore += 30;
        if (degCV > 0.8) cpScore += 25;
        if (assortativity < 0) cpScore += 20;
        if (largestComponentFraction > 0.7) cpScore += 25;
        scores.put(NetworkType.CORE_PERIPHERY, cpScore);

        // Pick best
        NetworkType best = NetworkType.UNKNOWN;
        double bestScore = 40; // minimum threshold
        for (Map.Entry<NetworkType, Double> entry : scores.entrySet()) {
            if (entry.getValue() > bestScore) {
                bestScore = entry.getValue();
                best = entry.getKey();
            }
        }
        classification = best;
        classificationConfidence = Math.min(100, bestScore);
    }

    // ── Metric results & scoring ────────────────────────────────

    private void buildMetricResults() {
        metricResults.clear();
        int n = graph.getVertexCount();

        // Density: ideal depends on context, but moderate is interesting
        metricResults.add(new MetricResult("Density", "Structure",
                density, scoreDensity(),
                density < 0.01 ? "Very sparse" :
                density < 0.1 ? "Sparse" :
                density < 0.5 ? "Moderate" : "Dense"));

        metricResults.add(new MetricResult("Average Degree", "Degree",
                avgDegree, scoreAvgDegree(n),
                avgDegree < 2 ? "Under-connected" :
                avgDegree < 10 ? "Well-connected" : "Highly connected"));

        double degCV = avgDegree < 1e-10 ? 0 : Math.sqrt(degreeVariance) / avgDegree;
        metricResults.add(new MetricResult("Degree Heterogeneity (CV)", "Degree",
                degCV, scoreDegreeCV(degCV),
                degCV < 0.3 ? "Homogeneous" :
                degCV < 1.0 ? "Moderate variation" : "Highly heterogeneous"));

        metricResults.add(new MetricResult("Clustering Coefficient", "Clustering",
                globalClustering, globalClustering * 100,
                globalClustering < 0.1 ? "Very low clustering" :
                globalClustering < 0.3 ? "Moderate clustering" : "High clustering"));

        metricResults.add(new MetricResult("Approximate Diameter", "Paths",
                approxDiameter, scoreDiameter(n),
                approxDiameter <= 3 ? "Ultra-short" :
                approxDiameter <= 6 ? "Short (small-world)" : "Long paths"));

        metricResults.add(new MetricResult("Assortativity", "Mixing",
                assortativity, scoreAssortativity(),
                assortativity > 0.1 ? "Assortative (like connects to like)" :
                assortativity < -0.1 ? "Disassortative (hubs connect to low-degree)" :
                "Neutral mixing"));

        metricResults.add(new MetricResult("Connected Components", "Connectivity",
                componentCount, componentCount == 1 ? 100 : Math.max(0, 100 - componentCount * 10),
                componentCount == 1 ? "Fully connected" : componentCount + " components"));

        metricResults.add(new MetricResult("Largest Component Fraction", "Connectivity",
                largestComponentFraction, largestComponentFraction * 100,
                largestComponentFraction > 0.9 ? "Giant component dominates" :
                largestComponentFraction > 0.5 ? "Majority connected" : "Fragmented"));

        metricResults.add(new MetricResult("Average Path Length", "Paths",
                avgPathLength, scorePathLength(n),
                avgPathLength < 3 ? "Very short paths" :
                avgPathLength < 6 ? "Efficient routing" : "Long paths"));

        metricResults.add(new MetricResult("Power-Law Exponent (α)", "Degree",
                powerLawExponent, scorePowerLaw(),
                powerLawExponent > 2 && powerLawExponent < 3 ? "Classic scale-free range" :
                powerLawExponent >= 3 ? "Steep decay" : "Flat distribution"));

        metricResults.add(new MetricResult("Small-World Quotient (σ)", "Structure",
                smallWorldQuotient, Math.min(100, smallWorldQuotient * 30),
                smallWorldQuotient > 3 ? "Strong small-world" :
                smallWorldQuotient > 1 ? "Weak small-world" : "Not small-world"));

        metricResults.add(new MetricResult("Hub Dominance", "Degree",
                hubDominance, scoreHubDominance(),
                hubDominance > 5 ? "Hub-dominated" :
                hubDominance > 2 ? "Moderate hubs" : "No dominant hubs"));
    }

    private double scoreDensity() {
        // Moderate density scores highest
        if (density < 0.01) return 30;
        if (density < 0.1) return 60;
        if (density < 0.5) return 90;
        return 70;
    }

    private double scoreAvgDegree(int n) {
        if (n <= 1) return 50;
        double ratio = avgDegree / n;
        if (ratio < 0.01) return 30;
        if (ratio < 0.1) return 70;
        if (ratio < 0.5) return 90;
        return 80;
    }

    private double scoreDegreeCV(double cv) {
        if (cv < 0.1) return 60; // too uniform
        if (cv < 0.5) return 85;
        if (cv < 1.0) return 75;
        return 50; // too spread
    }

    private double scoreDiameter(int n) {
        if (n <= 2) return 50;
        double logN = Math.log(n) / Math.log(2);
        double ratio = approxDiameter / logN;
        if (ratio < 0.5) return 95;
        if (ratio < 1.5) return 85;
        if (ratio < 3) return 65;
        return 40;
    }

    private double scoreAssortativity() {
        return 50 + assortativity * 30; // neutral is 50
    }

    private double scorePathLength(int n) {
        if (n <= 2) return 50;
        double logN = Math.log(n);
        if (avgPathLength < logN) return 90;
        if (avgPathLength < 2 * logN) return 70;
        return 40;
    }

    private double scorePowerLaw() {
        if (powerLawExponent > 2 && powerLawExponent < 3) return 90;
        if (powerLawExponent > 1.5 && powerLawExponent < 4) return 70;
        return 40;
    }

    private double scoreHubDominance() {
        if (hubDominance < 1.5) return 50;
        if (hubDominance < 3) return 70;
        if (hubDominance < 8) return 85;
        return 60; // too extreme
    }

    private void computeOverallScore() {
        overallScore = metricResults.stream()
                .mapToDouble(MetricResult::getScore)
                .average()
                .orElse(0);
    }

    private void setEmptyDefaults() {
        density = avgDegree = degreeVariance = globalClustering = 0;
        approxDiameter = 0;
        assortativity = avgPathLength = powerLawExponent = smallWorldQuotient = hubDominance = 0;
        componentCount = 0;
        largestComponentFraction = 0;
        classification = NetworkType.UNKNOWN;
        classificationConfidence = 0;
        metricResults = new ArrayList<>();
        overallScore = 0;
    }

    // ── BFS helper ──────────────────────────────────────────────

    private Map<String, Integer> bfs(String source) {
        Map<String, Integer> dist = new HashMap<>();
        Queue<String> queue = new LinkedList<>();
        dist.put(source, 0);
        queue.add(source);
        while (!queue.isEmpty()) {
            String v = queue.poll();
            int d = dist.get(v);
            for (String neighbor : graph.getNeighbors(v)) {
                if (!dist.containsKey(neighbor)) {
                    dist.put(neighbor, d + 1);
                    queue.add(neighbor);
                }
            }
        }
        return dist;
    }

    // ── Public getters ──────────────────────────────────────────

    public boolean isAnalyzed() { return analyzed; }
    public double getDensity() { ensureAnalyzed(); return density; }
    public double getAvgDegree() { ensureAnalyzed(); return avgDegree; }
    public double getDegreeVariance() { ensureAnalyzed(); return degreeVariance; }
    public double getGlobalClustering() { ensureAnalyzed(); return globalClustering; }
    public int getApproxDiameter() { ensureAnalyzed(); return approxDiameter; }
    public double getAssortativity() { ensureAnalyzed(); return assortativity; }
    public int getComponentCount() { ensureAnalyzed(); return componentCount; }
    public double getLargestComponentFraction() { ensureAnalyzed(); return largestComponentFraction; }
    public double getAvgPathLength() { ensureAnalyzed(); return avgPathLength; }
    public double getPowerLawExponent() { ensureAnalyzed(); return powerLawExponent; }
    public double getSmallWorldQuotient() { ensureAnalyzed(); return smallWorldQuotient; }
    public double getHubDominance() { ensureAnalyzed(); return hubDominance; }
    public NetworkType getClassification() { ensureAnalyzed(); return classification; }
    public double getClassificationConfidence() { ensureAnalyzed(); return classificationConfidence; }
    public List<MetricResult> getMetricResults() { ensureAnalyzed(); return Collections.unmodifiableList(metricResults); }
    public double getOverallScore() { ensureAnalyzed(); return overallScore; }
    public Grade getOverallGrade() { ensureAnalyzed(); return Grade.fromScore(overallScore); }

    /** Returns a map of all metric name→value pairs. */
    public Map<String, Double> getFingerprint() {
        ensureAnalyzed();
        Map<String, Double> fp = new LinkedHashMap<>();
        for (MetricResult mr : metricResults) {
            fp.put(mr.getName(), mr.getValue());
        }
        return fp;
    }

    /** Compare two profilers and return differences by metric name. */
    public static Map<String, double[]> compare(GraphNetworkProfiler a, GraphNetworkProfiler b) {
        Map<String, Double> fpA = a.getFingerprint();
        Map<String, Double> fpB = b.getFingerprint();
        Map<String, double[]> diff = new LinkedHashMap<>();
        for (String key : fpA.keySet()) {
            double vA = fpA.getOrDefault(key, 0.0);
            double vB = fpB.getOrDefault(key, 0.0);
            diff.put(key, new double[]{vA, vB, vB - vA});
        }
        return diff;
    }

    // ── Text report ─────────────────────────────────────────────

    public String getTextReport() {
        ensureAnalyzed();
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════╗\n");
        sb.append("║           GRAPH NETWORK PROFILE REPORT                  ║\n");
        sb.append("╚══════════════════════════════════════════════════════════╝\n\n");

        sb.append(String.format("  Network: %d vertices, %d edges%n",
                graph.getVertexCount(), graph.getEdgeCount()));
        sb.append(String.format("  Classification: %s (%.0f%% confidence)%n",
                classification.getDisplayName(), classificationConfidence));
        sb.append(String.format("  Description: %s%n", classification.getDescription()));
        sb.append(String.format("  Overall Grade: %s (%.1f/100)%n%n",
                getOverallGrade().getLabel(), overallScore));

        // Group by category
        Map<String, List<MetricResult>> byCategory = metricResults.stream()
                .collect(Collectors.groupingBy(MetricResult::getCategory,
                         LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<MetricResult>> entry : byCategory.entrySet()) {
            sb.append("  ── ").append(entry.getKey()).append(" ──\n");
            for (MetricResult mr : entry.getValue()) {
                sb.append(String.format("    %-35s %8.4f  [%s]  %s%n",
                        mr.getName(), mr.getValue(), mr.getGrade().getLabel(),
                        mr.getInterpretation()));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ── HTML report ─────────────────────────────────────────────

    public String getHtmlReport() {
        ensureAnalyzed();
        StringBuilder h = new StringBuilder();
        h.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        h.append("<meta charset=\"UTF-8\">\n");
        h.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n");
        h.append("<title>Network Profile Report</title>\n");
        h.append("<style>\n");
        h.append(CSS);
        h.append("</style>\n</head>\n<body>\n");
        h.append("<div class=\"container\">\n");

        // Header
        h.append("<div class=\"header\">\n");
        h.append("<h1>🔬 Network Profile Report</h1>\n");
        h.append(String.format("<p class=\"subtitle\">%d vertices · %d edges</p>\n",
                graph.getVertexCount(), graph.getEdgeCount()));
        h.append("</div>\n");

        // Classification card
        String typeColor = getTypeColor(classification);
        h.append("<div class=\"card classification\">\n");
        h.append(String.format("<div class=\"type-badge\" style=\"background:%s\">%s</div>\n",
                typeColor, classification.getDisplayName()));
        h.append(String.format("<p class=\"type-desc\">%s</p>\n", classification.getDescription()));
        h.append(String.format("<div class=\"confidence\">Confidence: %.0f%%</div>\n",
                classificationConfidence));
        h.append("</div>\n");

        // Overall score
        String gradeColor = getGradeColor(getOverallGrade());
        h.append("<div class=\"card score-card\">\n");
        h.append(String.format("<div class=\"big-grade\" style=\"color:%s\">%s</div>\n",
                gradeColor, getOverallGrade().getLabel()));
        h.append(String.format("<div class=\"score-num\">%.1f / 100</div>\n", overallScore));
        h.append("<div class=\"score-label\">Overall Network Health</div>\n");
        h.append("</div>\n");

        // Metrics table grouped by category
        Map<String, List<MetricResult>> byCategory = metricResults.stream()
                .collect(Collectors.groupingBy(MetricResult::getCategory,
                         LinkedHashMap::new, Collectors.toList()));

        h.append("<div class=\"card\">\n");
        h.append("<h2>📊 Detailed Metrics</h2>\n");
        for (Map.Entry<String, List<MetricResult>> entry : byCategory.entrySet()) {
            h.append(String.format("<h3>%s</h3>\n", entry.getKey()));
            h.append("<table>\n<tr><th>Metric</th><th>Value</th><th>Score</th>");
            h.append("<th>Grade</th><th>Interpretation</th></tr>\n");
            for (MetricResult mr : entry.getValue()) {
                String gc = getGradeColor(mr.getGrade());
                h.append(String.format("<tr><td>%s</td><td>%.4f</td>", mr.getName(), mr.getValue()));
                h.append(String.format("<td><div class=\"bar\"><div class=\"bar-fill\" style=\"width:%.0f%%;background:%s\"></div></div></td>",
                        mr.getScore(), gc));
                h.append(String.format("<td style=\"color:%s;font-weight:bold\">%s</td>",
                        gc, mr.getGrade().getLabel()));
                h.append(String.format("<td class=\"interp\">%s</td></tr>\n", mr.getInterpretation()));
            }
            h.append("</table>\n");
        }
        h.append("</div>\n");

        // Fingerprint radar summary
        h.append("<div class=\"card\">\n");
        h.append("<h2>🧬 Network Fingerprint</h2>\n");
        h.append("<div class=\"fingerprint\">\n");
        for (MetricResult mr : metricResults) {
            h.append(String.format("<div class=\"fp-item\"><span class=\"fp-name\">%s</span>",
                    mr.getName()));
            h.append(String.format("<span class=\"fp-bar\"><span class=\"fp-fill\" style=\"width:%.0f%%\"></span></span>",
                    mr.getScore()));
            h.append(String.format("<span class=\"fp-grade\">%s</span></div>\n",
                    mr.getGrade().getLabel()));
        }
        h.append("</div>\n</div>\n");

        h.append("<div class=\"footer\">Generated by GraphVisual Network Profiler</div>\n");
        h.append("</div>\n</body>\n</html>");
        return h.toString();
    }

    private String getTypeColor(NetworkType type) {
        switch (type) {
            case SOCIAL: return "#4CAF50";
            case SCALE_FREE: return "#FF5722";
            case SMALL_WORLD: return "#2196F3";
            case RANDOM: return "#9E9E9E";
            case LATTICE: return "#FF9800";
            case TREE_LIKE: return "#8BC34A";
            case CORE_PERIPHERY: return "#9C27B0";
            default: return "#607D8B";
        }
    }

    private String getGradeColor(Grade grade) {
        switch (grade) {
            case A_PLUS: case A: case A_MINUS: return "#4CAF50";
            case B_PLUS: case B: case B_MINUS: return "#8BC34A";
            case C_PLUS: case C: case C_MINUS: return "#FF9800";
            case D: return "#FF5722";
            case F: return "#F44336";
            default: return "#607D8B";
        }
    }

    private void ensureAnalyzed() {
        if (!analyzed) {
            throw new IllegalStateException("analyze() must be called before querying results");
        }
    }

    private static final String CSS =
        "* { margin: 0; padding: 0; box-sizing: border-box; }\n" +
        "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;\n" +
        "  background: #0d1117; color: #c9d1d9; line-height: 1.6; }\n" +
        ".container { max-width: 900px; margin: 0 auto; padding: 2rem; }\n" +
        ".header { text-align: center; margin-bottom: 2rem; }\n" +
        ".header h1 { font-size: 2rem; color: #f0f6fc; }\n" +
        ".subtitle { color: #8b949e; margin-top: 0.5rem; }\n" +
        ".card { background: #161b22; border: 1px solid #30363d; border-radius: 12px;\n" +
        "  padding: 1.5rem; margin-bottom: 1.5rem; }\n" +
        ".classification { text-align: center; }\n" +
        ".type-badge { display: inline-block; padding: 0.5rem 1.5rem; border-radius: 20px;\n" +
        "  color: white; font-weight: bold; font-size: 1.2rem; }\n" +
        ".type-desc { margin-top: 0.8rem; color: #8b949e; }\n" +
        ".confidence { margin-top: 0.5rem; font-size: 0.9rem; color: #58a6ff; }\n" +
        ".score-card { text-align: center; }\n" +
        ".big-grade { font-size: 4rem; font-weight: bold; }\n" +
        ".score-num { font-size: 1.5rem; color: #8b949e; }\n" +
        ".score-label { color: #58a6ff; margin-top: 0.3rem; }\n" +
        "h2 { color: #f0f6fc; margin-bottom: 1rem; }\n" +
        "h3 { color: #58a6ff; margin: 1rem 0 0.5rem; font-size: 0.95rem; }\n" +
        "table { width: 100%; border-collapse: collapse; }\n" +
        "th { text-align: left; padding: 0.5rem; color: #8b949e; border-bottom: 1px solid #30363d;\n" +
        "  font-size: 0.85rem; }\n" +
        "td { padding: 0.5rem; border-bottom: 1px solid #21262d; font-size: 0.9rem; }\n" +
        ".bar { width: 80px; height: 8px; background: #21262d; border-radius: 4px; }\n" +
        ".bar-fill { height: 100%; border-radius: 4px; transition: width 0.5s; }\n" +
        ".interp { color: #8b949e; font-size: 0.85rem; }\n" +
        ".fingerprint { display: flex; flex-direction: column; gap: 0.4rem; }\n" +
        ".fp-item { display: flex; align-items: center; gap: 0.5rem; }\n" +
        ".fp-name { width: 220px; font-size: 0.85rem; color: #c9d1d9; }\n" +
        ".fp-bar { flex: 1; height: 6px; background: #21262d; border-radius: 3px; }\n" +
        ".fp-fill { display: block; height: 100%; background: #58a6ff; border-radius: 3px; }\n" +
        ".fp-grade { width: 30px; text-align: center; font-weight: bold; font-size: 0.85rem; }\n" +
        ".footer { text-align: center; color: #484f58; margin-top: 2rem; font-size: 0.8rem; }\n";
}
