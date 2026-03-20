package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Degree Distribution Analyzer — comprehensive analysis of vertex degree
 * patterns in a graph. Computes degree statistics, frequency distributions,
 * power-law fitting via log–log linear regression, and classifies the
 * network topology type.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Degree statistics</b> — min, max, mean, median, mode, variance,
 *       standard deviation, skewness, kurtosis.</li>
 *   <li><b>Frequency distribution</b> — degree → count mapping with
 *       cumulative distribution (CDF) and probability distribution (PDF).</li>
 *   <li><b>Power-law fitting</b> — log–log linear regression to test if the
 *       degree distribution follows a power law P(k) ∝ k<sup>−γ</sup>.
 *       Reports exponent (γ), R² goodness-of-fit, and whether the fit
 *       indicates scale-free structure (1.5 ≤ γ ≤ 3.5, R² ≥ 0.7).</li>
 *   <li><b>Network classification</b> — categorises the graph as
 *       Empty, Trivial, Regular, Random (Erdős–Rényi-like),
 *       Scale-Free (Barabási–Albert-like), Hub-Dominated,
 *       or Heavy-Tailed based on degree statistics and power-law fit.</li>
 *   <li><b>Degree percentiles</b> — P10, P25 (Q1), P50 (median), P75 (Q3),
 *       P90, P95, P99.</li>
 *   <li><b>Hub detection</b> — vertices with degree ≥ mean + 2σ.</li>
 *   <li><b>Degree assortativity</b> — Pearson correlation coefficient of
 *       degrees across edges (positive = assortative, negative = disassortative).</li>
 *   <li><b>Text summary</b> — formatted multi-line report.</li>
 * </ul>
 *
 * @author zalenix
 */
public class DegreeDistributionAnalyzer {

    private final Graph<String, Edge> graph;
    private boolean computed;

    // ── Results ─────────────────────────────────────────────────────
    private List<Integer> degrees;
    private Map<Integer, Integer> frequencyMap;   // degree → count
    private int minDegree;
    private int maxDegree;
    private double meanDegree;
    private double medianDegree;
    private int modeDegree;
    private double variance;
    private double stdDev;
    private double skewness;
    private double kurtosis;
    private double powerLawExponent;    // γ
    private double powerLawRSquared;    // R² of log-log fit
    private boolean isScaleFree;
    private String networkType;
    private double assortativity;
    private Map<String, Double> percentiles;
    private List<String> hubs;

    /**
     * Creates a new analyzer for the given graph.
     *
     * @param graph the JUNG graph to analyse
     * @throws IllegalArgumentException if graph is null
     */
    public DegreeDistributionAnalyzer(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.computed = false;
        this.degrees = new ArrayList<Integer>();
        this.frequencyMap = new LinkedHashMap<Integer, Integer>();
        this.percentiles = new LinkedHashMap<String, Double>();
        this.hubs = new ArrayList<String>();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Computation
    // ═══════════════════════════════════════════════════════════════

    /**
     * Runs the full analysis. Idempotent — repeated calls are no-ops.
     *
     * @return this analyzer for chaining
     */
    public DegreeDistributionAnalyzer compute() {
        if (computed) return this;

        Collection<String> vertices = graph.getVertices();
        int n = vertices.size();

        if (n == 0) {
            minDegree = 0;
            maxDegree = 0;
            meanDegree = 0.0;
            medianDegree = 0.0;
            modeDegree = 0;
            variance = 0.0;
            stdDev = 0.0;
            skewness = 0.0;
            kurtosis = 0.0;
            powerLawExponent = 0.0;
            powerLawRSquared = 0.0;
            isScaleFree = false;
            networkType = "Empty";
            assortativity = 0.0;
            computed = true;
            return this;
        }

        // ── 1. Collect degrees ─────────────────────────────────────
        Map<String, Integer> vertexDegree = new LinkedHashMap<String, Integer>();
        for (String v : vertices) {
            int d = graph.degree(v);
            degrees.add(d);
            vertexDegree.put(v, d);
        }
        Collections.sort(degrees);

        // ── 2. Frequency distribution ──────────────────────────────
        for (int d : degrees) {
            Integer count = frequencyMap.get(d);
            frequencyMap.put(d, count == null ? 1 : count + 1);
        }
        // sort by degree
        TreeMap<Integer, Integer> sorted = new TreeMap<Integer, Integer>(frequencyMap);
        frequencyMap = new LinkedHashMap<Integer, Integer>(sorted);

        // ── 3. Basic statistics ────────────────────────────────────
        minDegree = degrees.get(0);
        maxDegree = degrees.get(degrees.size() - 1);

        long sum = 0;
        for (int d : degrees) sum += d;
        meanDegree = (double) sum / n;

        medianDegree = computeMedian(degrees);
        modeDegree = computeMode();

        // Variance, stddev
        double sumSqDiff = 0.0;
        for (int d : degrees) {
            double diff = d - meanDegree;
            sumSqDiff += diff * diff;
        }
        variance = n > 1 ? sumSqDiff / (n - 1) : 0.0;
        stdDev = Math.sqrt(variance);

        // Skewness (Fisher)
        if (n > 2 && stdDev > 0) {
            double sumCubed = 0.0;
            for (int d : degrees) {
                double diff = (d - meanDegree) / stdDev;
                sumCubed += diff * diff * diff;
            }
            skewness = (n * sumCubed) / ((n - 1.0) * (n - 2.0));
        } else {
            skewness = 0.0;
        }

        // Kurtosis (excess)
        if (n > 3 && stdDev > 0) {
            double sumFourth = 0.0;
            for (int d : degrees) {
                double diff = (d - meanDegree) / stdDev;
                sumFourth += diff * diff * diff * diff;
            }
            double raw = (n * (n + 1.0) * sumFourth) /
                    ((n - 1.0) * (n - 2.0) * (n - 3.0));
            kurtosis = raw - (3.0 * (n - 1.0) * (n - 1.0)) /
                    ((n - 2.0) * (n - 3.0));
        } else {
            kurtosis = 0.0;
        }

        // ── 4. Percentiles ─────────────────────────────────────────
        percentiles.put("P10", computePercentile(degrees, 10));
        percentiles.put("P25", computePercentile(degrees, 25));
        percentiles.put("P50", computePercentile(degrees, 50));
        percentiles.put("P75", computePercentile(degrees, 75));
        percentiles.put("P90", computePercentile(degrees, 90));
        percentiles.put("P95", computePercentile(degrees, 95));
        percentiles.put("P99", computePercentile(degrees, 99));

        // ── 5. Hub detection (degree ≥ mean + 2σ) ──────────────────
        double hubThreshold = meanDegree + 2.0 * stdDev;
        List<Map.Entry<String, Integer>> sortedVertices =
                new ArrayList<Map.Entry<String, Integer>>(vertexDegree.entrySet());
        Collections.sort(sortedVertices, (Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) -> {
                return b.getValue().compareTo(a.getValue());
            });
        for (Map.Entry<String, Integer> entry : sortedVertices) {
            if (entry.getValue() > hubThreshold && entry.getValue() > 0) {
                hubs.add(entry.getKey());
            }
        }

        // ── 6. Power-law fitting (log-log regression) ──────────────
        fitPowerLaw();

        // ── 7. Degree assortativity ────────────────────────────────
        computeAssortativity(vertexDegree);

        // ── 8. Network classification ──────────────────────────────
        classifyNetwork(n);

        computed = true;
        return this;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Getters
    // ═══════════════════════════════════════════════════════════════

    /** Sorted list of all vertex degrees (ascending). */
    public List<Integer> getDegrees() {
        ensureComputed();
        return Collections.unmodifiableList(degrees);
    }

    /** Degree → count frequency map, sorted by degree. */
    public Map<Integer, Integer> getFrequencyMap() {
        ensureComputed();
        return Collections.unmodifiableMap(frequencyMap);
    }

    /** Probability distribution: degree → P(degree). */
    public Map<Integer, Double> getPDF() {
        ensureComputed();
        int n = degrees.size();
        Map<Integer, Double> pdf = new LinkedHashMap<Integer, Double>();
        if (n == 0) return pdf;
        for (Map.Entry<Integer, Integer> entry : frequencyMap.entrySet()) {
            pdf.put(entry.getKey(), (double) entry.getValue() / n);
        }
        return pdf;
    }

    /**
     * Cumulative distribution function: degree → P(X ≤ degree).
     */
    public Map<Integer, Double> getCDF() {
        ensureComputed();
        int n = degrees.size();
        Map<Integer, Double> cdf = new LinkedHashMap<Integer, Double>();
        if (n == 0) return cdf;
        double cumulative = 0.0;
        for (Map.Entry<Integer, Integer> entry : frequencyMap.entrySet()) {
            cumulative += (double) entry.getValue() / n;
            cdf.put(entry.getKey(), cumulative);
        }
        return cdf;
    }

    /** Complementary CDF (survival function): degree → P(X > degree). */
    public Map<Integer, Double> getCCDF() {
        ensureComputed();
        Map<Integer, Double> cdf = getCDF();
        Map<Integer, Double> ccdf = new LinkedHashMap<Integer, Double>();
        for (Map.Entry<Integer, Double> entry : cdf.entrySet()) {
            ccdf.put(entry.getKey(), 1.0 - entry.getValue());
        }
        return ccdf;
    }

    public int getMinDegree() { ensureComputed(); return minDegree; }
    public int getMaxDegree() { ensureComputed(); return maxDegree; }
    public double getMeanDegree() { ensureComputed(); return meanDegree; }
    public double getMedianDegree() { ensureComputed(); return medianDegree; }
    public int getModeDegree() { ensureComputed(); return modeDegree; }
    public double getVariance() { ensureComputed(); return variance; }
    public double getStdDev() { ensureComputed(); return stdDev; }
    public double getSkewness() { ensureComputed(); return skewness; }
    public double getKurtosis() { ensureComputed(); return kurtosis; }
    public double getPowerLawExponent() { ensureComputed(); return powerLawExponent; }
    public double getPowerLawRSquared() { ensureComputed(); return powerLawRSquared; }
    public boolean isScaleFree() { ensureComputed(); return isScaleFree; }
    public String getNetworkType() { ensureComputed(); return networkType; }
    public double getAssortativity() { ensureComputed(); return assortativity; }
    public Map<String, Double> getPercentiles() { ensureComputed(); return Collections.unmodifiableMap(percentiles); }
    public List<String> getHubs() { ensureComputed(); return Collections.unmodifiableList(hubs); }

    /** Number of distinct degree values. */
    public int getDistinctDegreeCount() {
        ensureComputed();
        return frequencyMap.size();
    }

    /** Degree range (max - min). */
    public int getDegreeRange() {
        ensureComputed();
        return maxDegree - minDegree;
    }

    /** Coefficient of variation (stdDev / mean), or 0 if mean is 0. */
    public double getCoefficientOfVariation() {
        if (!computed) ensureComputed();
        return computeCV();
    }

    /** Interquartile range (P75 - P25). */
    public double getIQR() {
        if (!computed) ensureComputed();
        return computeIQR();
    }

    // internal helpers callable during compute()
    private double computeCV() {
        return meanDegree > 0 ? stdDev / meanDegree : 0.0;
    }

    private double computeIQR() {
        Double p25 = percentiles.get("P25");
        Double p75 = percentiles.get("P75");
        if (p25 == null || p75 == null) return 0.0;
        return p75 - p25;
    }

    /**
     * Returns a comprehensive result object.
     */
    public DegreeDistributionResult getResult() {
        ensureComputed();
        return new DegreeDistributionResult(
                degrees.size(), graph.getEdgeCount(),
                minDegree, maxDegree, meanDegree, medianDegree, modeDegree,
                variance, stdDev, skewness, kurtosis,
                powerLawExponent, powerLawRSquared, isScaleFree,
                networkType, assortativity,
                frequencyMap, percentiles, hubs
        );
    }

    /**
     * Formatted text summary of the analysis.
     */
    public String formatSummary() {
        ensureComputed();
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append("  Degree Distribution Analysis\n");
        sb.append("═══════════════════════════════════════\n\n");

        sb.append(String.format("  Vertices:    %d\n", degrees.size()));
        sb.append(String.format("  Edges:       %d\n", graph.getEdgeCount()));
        sb.append(String.format("  Network:     %s\n", networkType));
        sb.append("\n");

        sb.append("── Statistics ────────────────────────\n");
        sb.append(String.format("  Min degree:      %d\n", minDegree));
        sb.append(String.format("  Max degree:      %d\n", maxDegree));
        sb.append(String.format("  Mean degree:     %.2f\n", meanDegree));
        sb.append(String.format("  Median degree:   %.1f\n", medianDegree));
        sb.append(String.format("  Mode degree:     %d\n", modeDegree));
        sb.append(String.format("  Std deviation:   %.2f\n", stdDev));
        sb.append(String.format("  Variance:        %.2f\n", variance));
        sb.append(String.format("  Skewness:        %.3f\n", skewness));
        sb.append(String.format("  Kurtosis:        %.3f\n", kurtosis));
        sb.append(String.format("  CV:              %.3f\n", computeCV()));
        sb.append(String.format("  IQR:             %.1f\n", computeIQR()));
        sb.append("\n");

        sb.append("── Percentiles ──────────────────────\n");
        for (Map.Entry<String, Double> entry : percentiles.entrySet()) {
            sb.append(String.format("  %-5s  %.1f\n", entry.getKey(), entry.getValue()));
        }
        sb.append("\n");

        sb.append("── Power-Law Fit ────────────────────\n");
        sb.append(String.format("  Exponent (γ):    %.3f\n", powerLawExponent));
        sb.append(String.format("  R²:              %.4f\n", powerLawRSquared));
        sb.append(String.format("  Scale-free:      %s\n", isScaleFree ? "YES" : "NO"));
        sb.append("\n");

        sb.append(String.format("  Assortativity:   %.4f", assortativity));
        if (assortativity > 0.1) {
            sb.append("  (assortative — high-degree connect to high-degree)\n");
        } else if (assortativity < -0.1) {
            sb.append("  (disassortative — hubs connect to low-degree)\n");
        } else {
            sb.append("  (neutral)\n");
        }
        sb.append("\n");

        sb.append("── Frequency Distribution ───────────\n");
        sb.append(String.format("  %-8s %-8s %-8s\n", "Degree", "Count", "Pct"));
        int n = degrees.size();
        for (Map.Entry<Integer, Integer> entry : frequencyMap.entrySet()) {
            double pct = n > 0 ? 100.0 * entry.getValue() / n : 0;
            sb.append(String.format("  %-8d %-8d %.1f%%\n",
                    entry.getKey(), entry.getValue(), pct));
        }
        sb.append("\n");

        if (!hubs.isEmpty()) {
            sb.append("── Hubs (degree ≥ mean + 2σ) ────────\n");
            for (String hub : hubs) {
                sb.append(String.format("  %s (degree %d)\n",
                        hub, graph.degree(hub)));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Private helpers
    // ═══════════════════════════════════════════════════════════════

    private void ensureComputed() {
        if (!computed) {
            throw new IllegalStateException("Call compute() before accessing results");
        }
    }

    private double computeMedian(List<Integer> sorted) {
        int n = sorted.size();
        if (n == 0) return 0.0;
        if (n % 2 == 1) return sorted.get(n / 2);
        return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    private int computeMode() {
        int mode = 0;
        int maxCount = 0;
        for (Map.Entry<Integer, Integer> entry : frequencyMap.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                mode = entry.getKey();
            }
        }
        return mode;
    }

    private double computePercentile(List<Integer> sorted, double pct) {
        int n = sorted.size();
        if (n == 0) return 0.0;
        if (n == 1) return sorted.get(0);
        double rank = (pct / 100.0) * (n - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        if (lower == upper) return sorted.get(lower);
        double frac = rank - lower;
        return sorted.get(lower) + frac * (sorted.get(upper) - sorted.get(lower));
    }

    /**
     * Fits P(k) ∝ k^(−γ) via OLS on log(k) vs log(P(k)) for k ≥ 1.
     */
    private void fitPowerLaw() {
        // Collect points with degree >= 1
        int n = degrees.size();
        List<double[]> points = new ArrayList<double[]>();
        for (Map.Entry<Integer, Integer> entry : frequencyMap.entrySet()) {
            int k = entry.getKey();
            int count = entry.getValue();
            if (k >= 1 && count > 0) {
                double logK = Math.log(k);
                double logP = Math.log((double) count / n);
                points.add(new double[]{logK, logP});
            }
        }

        if (points.size() < 2) {
            powerLawExponent = 0.0;
            powerLawRSquared = 0.0;
            isScaleFree = false;
            return;
        }

        // OLS: logP = a + b * logK  →  γ = -b
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        int m = points.size();
        for (double[] p : points) {
            sumX += p[0];
            sumY += p[1];
            sumXY += p[0] * p[1];
            sumXX += p[0] * p[0];
        }
        double denom = m * sumXX - sumX * sumX;
        if (Math.abs(denom) < 1e-15) {
            powerLawExponent = 0.0;
            powerLawRSquared = 0.0;
            isScaleFree = false;
            return;
        }

        double b = (m * sumXY - sumX * sumY) / denom;
        double a = (sumY - b * sumX) / m;
        powerLawExponent = -b;  // γ

        // R²
        double meanY = sumY / m;
        double ssTot = 0, ssRes = 0;
        for (double[] p : points) {
            double predicted = a + b * p[0];
            ssRes += (p[1] - predicted) * (p[1] - predicted);
            ssTot += (p[1] - meanY) * (p[1] - meanY);
        }
        powerLawRSquared = ssTot > 0 ? 1.0 - ssRes / ssTot : 0.0;

        // Scale-free: γ ∈ [1.5, 3.5] and R² ≥ 0.7
        isScaleFree = powerLawExponent >= 1.5 && powerLawExponent <= 3.5
                && powerLawRSquared >= 0.7;
    }

    /**
     * Pearson correlation coefficient of degree(u), degree(v) over all edges.
     */
    private void computeAssortativity(Map<String, Integer> vertexDegree) {
        Collection<Edge> edges = graph.getEdges();
        int m = edges.size();
        if (m == 0) {
            assortativity = 0.0;
            return;
        }

        double sumProduct = 0;
        double sumDegree = 0;
        double sumDegreeSq = 0;

        for ((Edge e : edges) {
            Collection<String> endpoints = graph.getIncidentVertices(e);
            if (endpoints == null || endpoints.size() != 2) continue;
            Iterator<String> it = endpoints.iterator();
            String u = it.next();
            String v = it.next();
            Integer du = vertexDegree.get(u);
            Integer dv = vertexDegree.get(v);
            if (du == null || dv == null) continue;
            sumProduct += (double) du * dv;
            sumDegree += (du + dv) / 2.0;
            sumDegreeSq += (du * du + dv * dv) / 2.0;
        }

        double meanHalf = sumDegree / m;
        double meanSqHalf = sumDegreeSq / m;
        double meanProduct = sumProduct / m;

        double varDenom = meanSqHalf - meanHalf * meanHalf;
        if (Math.abs(varDenom) < 1e-15) {
            assortativity = 0.0;
            return;
        }
        assortativity = (meanProduct - meanHalf * meanHalf) / varDenom;
        // Clamp to [-1, 1]
        assortativity = Math.max(-1.0, Math.min(1.0, assortativity));
    }

    /**
     * Classifies the network based on degree distribution characteristics.
     */
    private void classifyNetwork(int n) {
        if (n == 0) {
            networkType = "Empty";
            return;
        }
        if (n <= 2) {
            networkType = "Trivial";
            return;
        }

        double cv = computeCV();

        // Regular: all same degree
        if (minDegree == maxDegree) {
            networkType = "Regular";
            return;
        }

        // Scale-free from power-law fit
        if (isScaleFree) {
            networkType = "Scale-Free";
            return;
        }

        // Hub-dominated: max degree > 50% of vertices and CV > 1
        if (maxDegree > n * 0.5 && cv > 1.0) {
            networkType = "Hub-Dominated";
            return;
        }

        // Heavy-tailed: high skewness or kurtosis
        if (skewness > 2.0 || kurtosis > 6.0) {
            networkType = "Heavy-Tailed";
            return;
        }

        // Random/Erdős-Rényi: low CV, low skewness
        if (cv < 0.5 && Math.abs(skewness) < 1.0) {
            networkType = "Random";
            return;
        }

        networkType = "Heterogeneous";
    }

    // ═══════════════════════════════════════════════════════════════
    //  Result object
    // ═══════════════════════════════════════════════════════════════

    /**
     * Immutable result object for the degree distribution analysis.
     */
    public static class DegreeDistributionResult {
        private final int vertexCount;
        private final int edgeCount;
        private final int minDegree;
        private final int maxDegree;
        private final double meanDegree;
        private final double medianDegree;
        private final int modeDegree;
        private final double variance;
        private final double stdDev;
        private final double skewness;
        private final double kurtosis;
        private final double powerLawExponent;
        private final double powerLawRSquared;
        private final boolean scaleFree;
        private final String networkType;
        private final double assortativity;
        private final Map<Integer, Integer> frequencyMap;
        private final Map<String, Double> percentiles;
        private final List<String> hubs;

        public DegreeDistributionResult(
                int vertexCount, int edgeCount,
                int minDegree, int maxDegree,
                double meanDegree, double medianDegree, int modeDegree,
                double variance, double stdDev, double skewness, double kurtosis,
                double powerLawExponent, double powerLawRSquared, boolean scaleFree,
                String networkType, double assortativity,
                Map<Integer, Integer> frequencyMap,
                Map<String, Double> percentiles,
                List<String> hubs) {
            this.vertexCount = vertexCount;
            this.edgeCount = edgeCount;
            this.minDegree = minDegree;
            this.maxDegree = maxDegree;
            this.meanDegree = meanDegree;
            this.medianDegree = medianDegree;
            this.modeDegree = modeDegree;
            this.variance = variance;
            this.stdDev = stdDev;
            this.skewness = skewness;
            this.kurtosis = kurtosis;
            this.powerLawExponent = powerLawExponent;
            this.powerLawRSquared = powerLawRSquared;
            this.scaleFree = scaleFree;
            this.networkType = networkType;
            this.assortativity = assortativity;
            this.frequencyMap = Collections.unmodifiableMap(new LinkedHashMap<Integer, Integer>(frequencyMap));
            this.percentiles = Collections.unmodifiableMap(new LinkedHashMap<String, Double>(percentiles));
            this.hubs = Collections.unmodifiableList(new ArrayList<String>(hubs));
        }

        public int getVertexCount() { return vertexCount; }
        public int getEdgeCount() { return edgeCount; }
        public int getMinDegree() { return minDegree; }
        public int getMaxDegree() { return maxDegree; }
        public double getMeanDegree() { return meanDegree; }
        public double getMedianDegree() { return medianDegree; }
        public int getModeDegree() { return modeDegree; }
        public double getVariance() { return variance; }
        public double getStdDev() { return stdDev; }
        public double getSkewness() { return skewness; }
        public double getKurtosis() { return kurtosis; }
        public double getPowerLawExponent() { return powerLawExponent; }
        public double getPowerLawRSquared() { return powerLawRSquared; }
        public boolean isScaleFree() { return scaleFree; }
        public String getNetworkType() { return networkType; }
        public double getAssortativity() { return assortativity; }
        public Map<Integer, Integer> getFrequencyMap() { return frequencyMap; }
        public Map<String, Double> getPercentiles() { return percentiles; }
        public List<String> getHubs() { return hubs; }
    }
}
