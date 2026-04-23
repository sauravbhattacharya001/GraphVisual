package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Graph Anomaly Detector — identifies nodes with statistically unusual
 * behavior across multiple network metrics.
 *
 * <p>For each node, computes z-scores across four independent dimensions:</p>
 * <ul>
 *   <li><b>Degree</b> — number of connections (too many or too few)</li>
 *   <li><b>Local clustering coefficient</b> — how tightly connected a node's
 *       neighbors are (isolated vs. cliquish)</li>
 *   <li><b>Edge-type diversity</b> — Shannon entropy across Edge categories
 *       (concentrated vs. uniformly spread)</li>
 *   <li><b>Neighbor degree deviation</b> — how different a node's degree is
 *       from its neighbors' average (popularity mismatch)</li>
 * </ul>
 *
 * <p>The composite anomaly score is the L2 norm (Euclidean distance from the
 * mean in z-score space), normalized to 0–100 for readability. Nodes that are
 * extreme on multiple axes score highest.</p>
 *
 * <h3>Applications:</h3>
 * <ul>
 *   <li>Student communities: spot students with unusual social patterns</li>
 *   <li>IMEI analysis: detect suspicious devices with abnormal connections</li>
 *   <li>Fraud detection: find nodes that don't fit normal network behavior</li>
 *   <li>Network monitoring: surface structural outliers for investigation</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 * GraphAnomalyDetector detector = new GraphAnomalyDetector(graph);
 * detector.analyze();
 * List&lt;AnomalyResult&gt; top = detector.getTopAnomalies(10);
 * List&lt;AnomalyResult&gt; flagged = detector.getAnomaliesAboveThreshold(2.0);
 * String report = detector.formatReport();
 * </pre>
 *
 * @author zalenix
 */
public class GraphAnomalyDetector {

    /** Minimum graph size for meaningful statistics. */
    private static final int MIN_VERTICES = 3;

    /** Number of metric dimensions. */
    private static final int NUM_DIMENSIONS = 4;

    private final Graph<String, Edge> graph;
    private List<AnomalyResult> results;
    private Map<String, AnomalyResult> resultIndex;
    private boolean analyzed;

    /* Raw metric maps (populated during analyze). */
    private Map<String, Double> degreeMap;
    private Map<String, Double> clusteringMap;
    private Map<String, Double> diversityMap;
    private Map<String, Double> neighborDevMap;

    /** Pre-built adjacency sets — built once, reused across all per-vertex metrics. */
    private Map<String, Set<String>> adjSets;

    /* Global statistics. */
    private double degreeMean, degreeStd;
    private double clusteringMean, clusteringStd;
    private double diversityMean, diversityStd;
    private double neighborDevMean, neighborDevStd;

    /**
     * Creates a new GraphAnomalyDetector.
     *
     * @param graph the JUNG graph to analyze (must not be null)
     * @throws IllegalArgumentException if graph is null
     */
    public GraphAnomalyDetector(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.results = new ArrayList<AnomalyResult>();
        this.resultIndex = new HashMap<String, AnomalyResult>();
        this.analyzed = false;
    }

    // ── Public API ──────────────────────────────────────────────

    /**
     * Runs the full anomaly analysis. Must be called before querying results.
     *
     * @return this detector (for chaining)
     * @throws IllegalStateException if graph has fewer than {@value MIN_VERTICES} vertices
     */
    public GraphAnomalyDetector analyze() {
        int n = graph.getVertexCount();
        if (n < MIN_VERTICES) {
            throw new IllegalStateException(
                "Graph must have at least " + MIN_VERTICES
                + " vertices for anomaly detection (has " + n + ")");
        }

        computeRawMetrics();
        computeStatistics();
        computeAnomalyScores();

        analyzed = true;
        return this;
    }

    /**
     * Returns all anomaly results sorted by score (highest first).
     *
     * @return unmodifiable list of results
     * @throws IllegalStateException if {@link #analyze()} has not been called
     */
    public List<AnomalyResult> getAllResults() {
        ensureAnalyzed();
        return Collections.unmodifiableList(results);
    }

    /**
     * Returns the top-k most anomalous nodes.
     *
     * @param k number of results to return
     * @return list of top-k results (may be shorter if graph has fewer nodes)
     * @throws IllegalArgumentException if k &lt; 1
     * @throws IllegalStateException if not yet analyzed
     */
    public List<AnomalyResult> getTopAnomalies(int k) {
        if (k < 1) {
            throw new IllegalArgumentException("k must be >= 1");
        }
        ensureAnalyzed();
        int limit = Math.min(k, results.size());
        return Collections.unmodifiableList(results.subList(0, limit));
    }

    /**
     * Returns all nodes whose composite z-score exceeds the threshold.
     *
     * @param zThreshold minimum composite z-score (e.g. 2.0 for ~95th percentile)
     * @return list of anomalous nodes, sorted by score descending
     * @throws IllegalArgumentException if threshold is negative
     * @throws IllegalStateException if not yet analyzed
     */
    public List<AnomalyResult> getAnomaliesAboveThreshold(double zThreshold) {
        if (zThreshold < 0) {
            throw new IllegalArgumentException("Threshold must be non-negative");
        }
        ensureAnalyzed();
        List<AnomalyResult> flagged = new ArrayList<AnomalyResult>();
        for (AnomalyResult r : results) {
            if (r.compositeZScore >= zThreshold) {
                flagged.add(r);
            }
        }
        return Collections.unmodifiableList(flagged);
    }

    /**
     * Returns the anomaly result for a specific node.
     * O(1) lookup via internal index.
     *
     * @param nodeId the node to look up
     * @return the result, or null if the node is not in the graph
     * @throws IllegalStateException if not yet analyzed
     */
    public AnomalyResult getResult(String nodeId) {
        ensureAnalyzed();
        return resultIndex.get(nodeId);
    }

    /**
     * Returns the number of nodes flagged as anomalous at the given threshold.
     * Counts in-place without allocating an intermediate list.
     *
     * @param zThreshold minimum composite z-score
     * @return count of anomalous nodes
     * @throws IllegalStateException if not yet analyzed
     */
    public int countAnomalies(double zThreshold) {
        ensureAnalyzed();
        int count = 0;
        for (AnomalyResult r : results) {
            if (r.compositeZScore >= zThreshold) count++;
        }
        return count;
    }

    /**
     * Formats a human-readable anomaly report.
     *
     * @return multi-line report string
     * @throws IllegalStateException if not yet analyzed
     */
    public String formatReport() {
        return formatReport(10, 2.0);
    }

    /**
     * Formats a human-readable anomaly report with custom parameters.
     *
     * @param topK      number of top anomalies to show in detail
     * @param threshold z-score threshold for flagging
     * @return multi-line report string
     * @throws IllegalStateException if not yet analyzed
     */
    public String formatReport(int topK, double threshold) {
        ensureAnalyzed();
        StringBuilder sb = new StringBuilder();
        int n = graph.getVertexCount();
        int flagged = countAnomalies(threshold);

        sb.append("=== Graph Anomaly Detection Report ===\n\n");
        sb.append(String.format("Vertices analyzed: %d%n", n));
        sb.append(String.format("Anomalies (z > %.1f): %d (%.1f%%)%n",
                threshold, flagged, 100.0 * flagged / n));
        sb.append('\n');

        // Global metric summary
        sb.append("── Metric Statistics ──\n");
        sb.append(String.format("  Degree:          mean=%.2f  std=%.2f%n",
                degreeMean, degreeStd));
        sb.append(String.format("  Clustering:      mean=%.3f  std=%.3f%n",
                clusteringMean, clusteringStd));
        sb.append(String.format("  Edge diversity:  mean=%.3f  std=%.3f%n",
                diversityMean, diversityStd));
        sb.append(String.format("  Neighbor dev:    mean=%.3f  std=%.3f%n",
                neighborDevMean, neighborDevStd));
        sb.append('\n');

        // Top anomalies detail
        int showCount = Math.min(topK, results.size());
        sb.append(String.format("── Top %d Anomalies ──%n", showCount));
        sb.append(String.format("%-12s %6s %6s %6s %6s %6s  %-10s%n",
                "Node", "Score", "zDeg", "zClust", "zDiv", "zNbr", "Flags"));
        sb.append("─────────────────────────────────────────────────────────────────\n");

        for (int i = 0; i < showCount; i++) {
            AnomalyResult r = results.get(i);
            sb.append(String.format("%-12s %6.1f %6.2f %6.2f %6.2f %6.2f  %-10s%n",
                    r.nodeId, r.normalizedScore, r.degreeZScore,
                    r.clusteringZScore, r.diversityZScore,
                    r.neighborDevZScore, formatFlags(r)));
        }
        sb.append('\n');

        // Distribution summary
        sb.append("── Score Distribution ──\n");
        int[] buckets = new int[5]; // 0-20, 20-40, 40-60, 60-80, 80-100
        for (AnomalyResult r : results) {
            int bucket = Math.min((int)(r.normalizedScore / 20.0), 4);
            buckets[bucket]++;
        }
        String[] labels = {"0-20", "20-40", "40-60", "60-80", "80-100"};
        for (int i = 0; i < 5; i++) {
            int barLen = (int)(40.0 * buckets[i] / n);
            StringBuilder bar = new StringBuilder();
            for (int j = 0; j < barLen; j++) bar.append('\u2588');
            sb.append(String.format("  %6s: %3d %s%n", labels[i], buckets[i], bar));
        }

        return sb.toString();
    }

    // ── Result class ────────────────────────────────────────────

    /**
     * Holds anomaly detection results for a single node.
     */
    public static class AnomalyResult implements Comparable<AnomalyResult> {
        private final String nodeId;
        private final int degree;
        private final double clusteringCoeff;
        private final double edgeDiversity;
        private final double neighborDeviation;

        private final double degreeZScore;
        private final double clusteringZScore;
        private final double diversityZScore;
        private final double neighborDevZScore;

        private final double compositeZScore;
        private final double normalizedScore;

        AnomalyResult(String nodeId, int degree,
                       double clusteringCoeff, double edgeDiversity,
                       double neighborDeviation,
                       double degreeZ, double clusteringZ,
                       double diversityZ, double neighborDevZ,
                       double compositeZ, double normalized) {
            this.nodeId = nodeId;
            this.degree = degree;
            this.clusteringCoeff = clusteringCoeff;
            this.edgeDiversity = edgeDiversity;
            this.neighborDeviation = neighborDeviation;
            this.degreeZScore = degreeZ;
            this.clusteringZScore = clusteringZ;
            this.diversityZScore = diversityZ;
            this.neighborDevZScore = neighborDevZ;
            this.compositeZScore = compositeZ;
            this.normalizedScore = normalized;
        }

        public String getNodeId() { return nodeId; }
        public int getDegree() { return degree; }
        public double getClusteringCoeff() { return clusteringCoeff; }
        public double getEdgeDiversity() { return edgeDiversity; }
        public double getNeighborDeviation() { return neighborDeviation; }
        public double getDegreeZScore() { return degreeZScore; }
        public double getClusteringZScore() { return clusteringZScore; }
        public double getDiversityZScore() { return diversityZScore; }
        public double getNeighborDevZScore() { return neighborDevZScore; }
        public double getCompositeZScore() { return compositeZScore; }
        public double getNormalizedScore() { return normalizedScore; }

        /**
         * Returns true if this node is anomalous at the given threshold.
         * @param zThreshold minimum composite z-score
         */
        public boolean isAnomalous(double zThreshold) {
            return compositeZScore >= zThreshold;
        }

        /**
         * Returns the primary anomaly dimension (highest absolute z-score).
         */
        public String getPrimaryDimension() {
            double maxZ = Math.abs(degreeZScore);
            String dim = "degree";
            if (Math.abs(clusteringZScore) > maxZ) {
                maxZ = Math.abs(clusteringZScore);
                dim = "clustering";
            }
            if (Math.abs(diversityZScore) > maxZ) {
                maxZ = Math.abs(diversityZScore);
                dim = "diversity";
            }
            if (Math.abs(neighborDevZScore) > maxZ) {
                dim = "neighbor_deviation";
            }
            return dim;
        }

        @Override
        public int compareTo(AnomalyResult other) {
            // Descending by composite score
            return Double.compare(other.compositeZScore, this.compositeZScore);
        }

        @Override
        public String toString() {
            return String.format("AnomalyResult{node=%s, score=%.1f, z=%.2f, primary=%s}",
                    nodeId, normalizedScore, compositeZScore, getPrimaryDimension());
        }
    }

    // ── Private implementation ──────────────────────────────────

    private void ensureAnalyzed() {
        if (!analyzed) {
            throw new IllegalStateException(
                "analyze() must be called before querying results");
        }
    }

    /**
     * Computes raw metric values for every node.
     */
    private void computeRawMetrics() {
        Collection<String> vertices = graph.getVertices();
        int n = vertices.size();

        degreeMap = new LinkedHashMap<String, Double>(n);
        clusteringMap = new LinkedHashMap<String, Double>(n);
        diversityMap = new LinkedHashMap<String, Double>(n);
        neighborDevMap = new LinkedHashMap<String, Double>(n);

        // Build adjacency sets once — avoids repeated JUNG getNeighbors()
        // allocations across clustering, degree, and neighbor-deviation computations.
        adjSets = new HashMap<String, Set<String>>(n * 2);
        Map<String, Integer> rawDegrees = new HashMap<String, Integer>(n * 2);
        for (String v : vertices) {
            Collection<String> nbrs = graph.getNeighbors(v);
            Set<String> nbrSet = nbrs == null ? Collections.emptySet()
                    : new HashSet<String>(nbrs);
            adjSets.put(v, nbrSet);
            rawDegrees.put(v, nbrSet.size());
        }

        for (String v : vertices) {
            int deg = rawDegrees.get(v);
            degreeMap.put(v, (double) deg);

            // Local clustering coefficient (uses pre-built adjSets)
            clusteringMap.put(v, computeClusteringCoeff(v));

            // Edge-type diversity (Shannon entropy)
            diversityMap.put(v, computeEdgeDiversity(v));

            // Neighbor degree deviation: |deg(v) - avg_deg(neighbors)|
            neighborDevMap.put(v, computeNeighborDeviation(v, rawDegrees));
        }
    }

    /**
     * Computes the local clustering coefficient for a node.
     * <p>C(v) = 2 * triangles(v) / (deg(v) * (deg(v) - 1))</p>
     *
     * <p><b>Optimization:</b> Uses the pre-built {@code adjSets} map instead
     * of calling {@code graph.getNeighbors()} per neighbor. This eliminates
     * O(k) JUNG collection allocations per vertex (one per neighbor in the
     * triangle-counting loop) and avoids constructing a redundant HashSet
     * copy — the pre-built sets serve directly as O(1) membership oracles.</p>
     */
    private double computeClusteringCoeff(String v) {
        Set<String> nbrSet = adjSets.get(v);
        if (nbrSet == null) return 0.0;

        int k = nbrSet.size();
        if (k < 2) return 0.0;

        // Count edges among neighbors using pre-built adjacency sets.
        // For each neighbor ni, count how many of ni's neighbors are also
        // in v's neighbor set. Uses adjSets.get(ni) — O(1) map lookup —
        // instead of graph.getNeighbors(ni) which allocates a new Collection.
        int triangleEdges = 0;
        for (String ni : nbrSet) {
            Set<String> niNeighbors = adjSets.get(ni);
            if (niNeighbors == null) continue;
            for (String nn : niNeighbors) {
                if (nn != ni && nbrSet.contains(nn)) {
                    triangleEdges++;
                }
            }
        }
        // Each edge counted twice (once from each endpoint)
        triangleEdges /= 2;

        return (2.0 * triangleEdges) / (k * (k - 1));
    }

    /**
     * Computes Shannon entropy of Edge-type distribution for a node.
     * <p>Higher entropy = more diverse connections across categories.</p>
     * <p>Normalized to [0, 1] by dividing by log(numCategories).</p>
     */
    private double computeEdgeDiversity(String v) {
        Collection<Edge> edges = graph.getIncidentEdges(v);
        if (edges == null || edges.isEmpty()) return 0.0;

        Map<String, Integer> typeCounts = new HashMap<String, Integer>();
        int total = 0;
        for (Edge e : edges) {
            String type = e.getType();
            if (type == null) type = "unknown";
            Integer count = typeCounts.get(type);
            typeCounts.put(type, count == null ? 1 : count + 1);
            total++;
        }

        if (typeCounts.size() <= 1) return 0.0;

        double entropy = 0.0;
        for (int count : typeCounts.values()) {
            double p = (double) count / total;
            if (p > 0) {
                entropy -= p * Math.log(p);
            }
        }

        // Normalize by max possible entropy
        double maxEntropy = Math.log(typeCounts.size());
        return maxEntropy > 0 ? entropy / maxEntropy : 0.0;
    }

    /**
     * Computes absolute deviation of node's degree from its neighbors' mean degree.
     * Uses pre-built {@code adjSets} to avoid an additional JUNG getNeighbors() call.
     */
    private double computeNeighborDeviation(String v,
                                             Map<String, Integer> rawDegrees) {
        Set<String> neighbors = adjSets.get(v);
        if (neighbors == null || neighbors.isEmpty()) return 0.0;

        double sum = 0.0;
        int count = 0;
        for (String nbr : neighbors) {
            Integer d = rawDegrees.get(nbr);
            if (d != null) {
                sum += d;
                count++;
            }
        }
        if (count == 0) return 0.0;

        double avgNeighborDeg = sum / count;
        return Math.abs(rawDegrees.get(v) - avgNeighborDeg);
    }

    /**
     * Computes mean and standard deviation for each metric across all nodes.
     */
    private void computeStatistics() {
        double[] degStats = meanAndStd(degreeMap.values());
        degreeMean = degStats[0];
        degreeStd = degStats[1];

        double[] clStats = meanAndStd(clusteringMap.values());
        clusteringMean = clStats[0];
        clusteringStd = clStats[1];

        double[] divStats = meanAndStd(diversityMap.values());
        diversityMean = divStats[0];
        diversityStd = divStats[1];

        double[] nbrStats = meanAndStd(neighborDevMap.values());
        neighborDevMean = nbrStats[0];
        neighborDevStd = nbrStats[1];
    }

    /**
     * Computes z-scores and composite anomaly score for each node.
     */
    private void computeAnomalyScores() {
        results.clear();

        // Find max composite for normalization
        List<double[]> zScores = new ArrayList<double[]>();
        List<String> nodeOrder = new ArrayList<String>();
        double maxComposite = 0.0;

        for (String v : graph.getVertices()) {
            double zDeg = zScore(degreeMap.get(v), degreeMean, degreeStd);
            double zClu = zScore(clusteringMap.get(v), clusteringMean, clusteringStd);
            double zDiv = zScore(diversityMap.get(v), diversityMean, diversityStd);
            double zNbr = zScore(neighborDevMap.get(v), neighborDevMean, neighborDevStd);

            // L2 norm of absolute z-scores
            double composite = Math.sqrt(
                zDeg * zDeg + zClu * zClu + zDiv * zDiv + zNbr * zNbr);

            if (composite > maxComposite) {
                maxComposite = composite;
            }

            zScores.add(new double[]{zDeg, zClu, zDiv, zNbr, composite});
            nodeOrder.add(v);
        }

        // Build results with normalized 0-100 score
        for (int i = 0; i < nodeOrder.size(); i++) {
            String v = nodeOrder.get(i);
            double[] z = zScores.get(i);
            double normalized = maxComposite > 0
                ? (z[4] / maxComposite) * 100.0 : 0.0;

            results.add(new AnomalyResult(
                v, degreeMap.get(v).intValue(),
                clusteringMap.get(v), diversityMap.get(v),
                neighborDevMap.get(v),
                z[0], z[1], z[2], z[3],
                z[4], normalized));
        }

        Collections.sort(results);

        // Build O(1) lookup index
        resultIndex.clear();
        for (AnomalyResult r : results) {
            resultIndex.put(r.nodeId, r);
        }
    }

    /**
     * Computes z-score: (value - mean) / std.
     * Uses absolute value so both extremes are flagged.
     * Returns 0 if std is 0 (no variance).
     */
    private static double zScore(double value, double mean, double std) {
        if (std == 0.0) return 0.0;
        return Math.abs(value - mean) / std;
    }

    /**
     * Computes mean and population standard deviation.
     * @return double[]{mean, std}
     */
    private static double[] meanAndStd(Collection<Double> values) {
        if (values.isEmpty()) return new double[]{0.0, 0.0};

        double sum = 0.0;
        for (double v : values) sum += v;
        double mean = sum / values.size();

        double sqSum = 0.0;
        for (double v : values) {
            double diff = v - mean;
            sqSum += diff * diff;
        }
        double std = Math.sqrt(sqSum / values.size());

        return new double[]{mean, std};
    }

    /**
     * Formats human-readable flags for an anomaly result.
     */
    private String formatFlags(AnomalyResult r) {
        List<String> flags = new ArrayList<String>();
        if (Math.abs(r.degreeZScore) > 2.0) {
            flags.add(r.degreeZScore > 0 ? "HIGH_DEG" : "LOW_DEG");
        }
        if (Math.abs(r.clusteringZScore) > 2.0) {
            flags.add(r.clusteringZScore > 0 ? "CLIQUISH" : "ISOLATED");
        }
        if (Math.abs(r.diversityZScore) > 2.0) {
            flags.add(r.diversityZScore > 0 ? "DIVERSE" : "UNIFORM");
        }
        if (Math.abs(r.neighborDevZScore) > 2.0) {
            flags.add("NBR_MISMATCH");
        }
        return flags.isEmpty() ? "—" : String.join(",", flags);
    }
}
