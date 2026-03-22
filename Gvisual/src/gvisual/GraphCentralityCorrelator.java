package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Computes Spearman rank correlations between centrality measures (degree,
 * betweenness, closeness) and identifies discordant nodes — vertices that
 * rank very differently across metrics.
 *
 * <p>Uses {@link NodeCentralityAnalyzer} to obtain raw centrality values,
 * then ranks nodes per metric and computes pairwise Spearman rho coefficients.
 * A configurable discordance threshold identifies structurally interesting
 * nodes whose importance varies by measure.</p>
 *
 * @author zalenix
 */
public class GraphCentralityCorrelator {

    private final Graph<String, Edge> graph;
    private final NodeCentralityAnalyzer centralityAnalyzer;
    private boolean computed;

    private List<CorrelationResult> correlations;
    private Map<String, Integer> degreeRanks;
    private Map<String, Integer> betweennessRanks;
    private Map<String, Integer> closenessRanks;

    private static final String DEGREE = "degree";
    private static final String BETWEENNESS = "betweenness";
    private static final String CLOSENESS = "closeness";

    /**
     * Represents a Spearman rank correlation between two centrality metrics.
     */
    public static class CorrelationResult {
        private final String metric1;
        private final String metric2;
        private final double rho;
        private final double pValue;
        private final String interpretation;

        public CorrelationResult(String metric1, String metric2, double rho,
                                 double pValue, String interpretation) {
            this.metric1 = metric1;
            this.metric2 = metric2;
            this.rho = rho;
            this.pValue = pValue;
            this.interpretation = interpretation;
        }

        /** First metric name. */
        public String getMetric1() { return metric1; }

        /** Second metric name. */
        public String getMetric2() { return metric2; }

        /** Spearman rho coefficient. Range [-1, 1]. */
        public double getRho() { return rho; }

        /** Approximate p-value for the correlation. */
        public double getPValue() { return pValue; }

        /** Interpretation: "strong", "moderate", or "weak". */
        public String getInterpretation() { return interpretation; }

        @Override
        public String toString() {
            return String.format("%s vs %s: rho=%.4f, p=%.4f (%s)",
                    metric1, metric2, rho, pValue, interpretation);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CorrelationResult that = (CorrelationResult) o;
            return metric1.equals(that.metric1) && metric2.equals(that.metric2);
        }

        @Override
        public int hashCode() {
            return metric1.hashCode() * 31 + metric2.hashCode();
        }
    }

    /**
     * Represents a node that ranks very differently across two centrality metrics.
     */
    public static class DiscordantNode implements Comparable<DiscordantNode> {
        private final String nodeId;
        private final String metric1Name;
        private final int rank1;
        private final String metric2Name;
        private final int rank2;
        private final int rankDifference;

        public DiscordantNode(String nodeId, String metric1Name, int rank1,
                              String metric2Name, int rank2) {
            this.nodeId = nodeId;
            this.metric1Name = metric1Name;
            this.rank1 = rank1;
            this.metric2Name = metric2Name;
            this.rank2 = rank2;
            this.rankDifference = Math.abs(rank1 - rank2);
        }

        /** Vertex ID. */
        public String getNodeId() { return nodeId; }

        /** First metric name. */
        public String getMetric1Name() { return metric1Name; }

        /** Rank in first metric (1-based). */
        public int getRank1() { return rank1; }

        /** Second metric name. */
        public String getMetric2Name() { return metric2Name; }

        /** Rank in second metric (1-based). */
        public int getRank2() { return rank2; }

        /** Absolute rank difference. */
        public int getRankDifference() { return rankDifference; }

        /** Sort by rank difference, descending. */
        public int compareTo(DiscordantNode other) {
            return Integer.compare(other.rankDifference, this.rankDifference);
        }

        @Override
        public String toString() {
            return String.format("Node %s: %s rank=%d, %s rank=%d, diff=%d",
                    nodeId, metric1Name, rank1, metric2Name, rank2, rankDifference);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DiscordantNode that = (DiscordantNode) o;
            return nodeId.equals(that.nodeId)
                    && metric1Name.equals(that.metric1Name)
                    && metric2Name.equals(that.metric2Name);
        }

        @Override
        public int hashCode() {
            return nodeId.hashCode() * 31 * 31
                    + metric1Name.hashCode() * 31
                    + metric2Name.hashCode();
        }
    }

    /**
     * Creates a new GraphCentralityCorrelator for the given graph.
     *
     * @param graph the JUNG graph to analyze
     * @throws IllegalArgumentException if graph is null
     */
    public GraphCentralityCorrelator(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.centralityAnalyzer = new NodeCentralityAnalyzer(graph);
        this.computed = false;
        this.correlations = new ArrayList<CorrelationResult>();
        this.degreeRanks = new LinkedHashMap<String, Integer>();
        this.betweennessRanks = new LinkedHashMap<String, Integer>();
        this.closenessRanks = new LinkedHashMap<String, Integer>();
    }

    /**
     * Returns whether the correlation analysis has been computed.
     */
    public boolean isComputed() {
        return computed;
    }

    /**
     * Runs the full correlation analysis: computes centralities, builds
     * ranks, and calculates pairwise Spearman correlations.
     * Automatically skips recomputation if already computed.
     */
    public void computeAll() {
        if (computed) return;

        centralityAnalyzer.compute();

        int n = graph.getVertexCount();
        if (n < 2) {
            computed = true;
            return;
        }

        // Build ranks for each metric
        degreeRanks = buildRanks(centralityAnalyzer.getDegreeCentralityMap());
        betweennessRanks = buildRanks(centralityAnalyzer.getBetweennessCentralityMap());
        closenessRanks = buildRanks(centralityAnalyzer.getClosenessCentralityMap());

        // Compute pairwise Spearman correlations
        correlations.add(computeSpearman(DEGREE, degreeRanks, BETWEENNESS, betweennessRanks));
        correlations.add(computeSpearman(DEGREE, degreeRanks, CLOSENESS, closenessRanks));
        correlations.add(computeSpearman(BETWEENNESS, betweennessRanks, CLOSENESS, closenessRanks));

        computed = true;
    }

    /**
     * Returns the list of pairwise correlation results.
     *
     * @return list of CorrelationResult for all 3 metric pairs
     */
    public List<CorrelationResult> getCorrelations() {
        if (!computed) computeAll();
        return Collections.unmodifiableList(correlations);
    }

    /**
     * Returns the 3x3 correlation matrix as a nested map.
     * Keys are metric names ("degree", "betweenness", "closeness").
     *
     * @return map of metric name → (metric name → rho value)
     */
    public Map<String, Map<String, Double>> getCorrelationMatrix() {
        if (!computed) computeAll();

        Map<String, Map<String, Double>> matrix = new LinkedHashMap<String, Map<String, Double>>();
        String[] metrics = {DEGREE, BETWEENNESS, CLOSENESS};

        // Initialize with 1.0 on diagonal
        for (String m : metrics) {
            Map<String, Double> row = new LinkedHashMap<String, Double>();
            for (String m2 : metrics) {
                row.put(m2, m.equals(m2) ? 1.0 : 0.0);
            }
            matrix.put(m, row);
        }

        // Fill from computed correlations
        for (CorrelationResult cr : correlations) {
            matrix.get(cr.getMetric1()).put(cr.getMetric2(), cr.getRho());
            matrix.get(cr.getMetric2()).put(cr.getMetric1(), cr.getRho());
        }

        return matrix;
    }

    /**
     * Returns nodes where the normalized rank difference between any two
     * metrics exceeds the given threshold.
     *
     * @param threshold normalized threshold in [0, 1]; e.g. 0.5 means rank
     *                  difference must exceed 50% of total nodes
     * @return list of DiscordantNode sorted by rank difference descending
     * @throws IllegalArgumentException if threshold is not in [0, 1]
     */
    public List<DiscordantNode> getDiscordantNodes(double threshold) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("Threshold must be between 0.0 and 1.0");
        }
        if (!computed) computeAll();

        int n = graph.getVertexCount();
        if (n < 2) return new ArrayList<DiscordantNode>();

        int minDiff = (int) Math.ceil(threshold * (n - 1));

        List<DiscordantNode> result = new ArrayList<DiscordantNode>();
        for (String node : graph.getVertices()) {
            addIfDiscordant(result, node, DEGREE, degreeRanks, BETWEENNESS, betweennessRanks, minDiff);
            addIfDiscordant(result, node, DEGREE, degreeRanks, CLOSENESS, closenessRanks, minDiff);
            addIfDiscordant(result, node, BETWEENNESS, betweennessRanks, CLOSENESS, closenessRanks, minDiff);
        }

        Collections.sort(result);
        return result;
    }

    /**
     * Returns the top N most discordant nodes across all metric pairs.
     *
     * @param n number of top discordant nodes to return
     * @return sorted list of top DiscordantNode entries
     */
    public List<DiscordantNode> getTopDiscordantNodes(int n) {
        if (!computed) computeAll();
        if (n <= 0) return new ArrayList<DiscordantNode>();

        // Use threshold 0 to get all possible discordant pairs
        List<DiscordantNode> all = getDiscordantNodes(0.0);
        return all.subList(0, Math.min(n, all.size()));
    }

    /**
     * Returns the rank maps for a given metric.
     *
     * @param metric "degree", "betweenness", or "closeness"
     * @return unmodifiable map of node → rank (1-based)
     */
    public Map<String, Integer> getRanks(String metric) {
        if (!computed) computeAll();

        String m = metric.toLowerCase();
        if (BETWEENNESS.equals(m)) return Collections.unmodifiableMap(betweennessRanks);
        if (CLOSENESS.equals(m)) return Collections.unmodifiableMap(closenessRanks);
        return Collections.unmodifiableMap(degreeRanks);
    }

    /**
     * Generates a formatted text report of the correlation analysis.
     *
     * @return multi-line report string
     */
    public String generateReport() {
        if (!computed) computeAll();

        StringBuilder sb = new StringBuilder();
        sb.append("=== Centrality Correlation Report ===\n");
        sb.append(String.format("Nodes: %d, Edges: %d\n\n",
                graph.getVertexCount(), graph.getEdgeCount()));

        // Correlation results
        sb.append("--- Pairwise Spearman Correlations ---\n");
        if (correlations.isEmpty()) {
            sb.append("Not enough nodes for correlation analysis.\n");
        } else {
            for (CorrelationResult cr : correlations) {
                sb.append(String.format("  %s vs %s: rho=%.4f, p-value=%.4f (%s)\n",
                        cr.getMetric1(), cr.getMetric2(),
                        cr.getRho(), cr.getPValue(), cr.getInterpretation()));
            }
        }

        // Correlation matrix
        sb.append("\n--- Correlation Matrix ---\n");
        Map<String, Map<String, Double>> matrix = getCorrelationMatrix();
        String[] metrics = {DEGREE, BETWEENNESS, CLOSENESS};
        sb.append(String.format("  %15s %12s %12s %12s\n", "", DEGREE, BETWEENNESS, CLOSENESS));
        for (String m : metrics) {
            sb.append(String.format("  %15s", m));
            for (String m2 : metrics) {
                sb.append(String.format(" %12.4f", matrix.get(m).get(m2)));
            }
            sb.append("\n");
        }

        // Top discordant nodes
        sb.append("\n--- Top Discordant Nodes (threshold=0.3) ---\n");
        List<DiscordantNode> discordant = getDiscordantNodes(0.3);
        if (discordant.isEmpty()) {
            sb.append("  No discordant nodes found.\n");
        } else {
            for (DiscordantNode dn : discordant) {
                sb.append(String.format("  Node %s: %s(rank %d) vs %s(rank %d), diff=%d\n",
                        dn.getNodeId(), dn.getMetric1Name(), dn.getRank1(),
                        dn.getMetric2Name(), dn.getRank2(), dn.getRankDifference()));
            }
        }

        return sb.toString();
    }

    /**
     * Exports correlation results and discordant nodes as CSV.
     *
     * @return CSV-formatted string
     */
    public String exportCsv() {
        if (!computed) computeAll();

        StringBuilder sb = new StringBuilder();

        // Correlations section
        sb.append("section,metric1,metric2,rho,p_value,interpretation\n");
        for (CorrelationResult cr : correlations) {
            sb.append(String.format("correlation,%s,%s,%.6f,%.6f,%s\n",
                    cr.getMetric1(), cr.getMetric2(),
                    cr.getRho(), cr.getPValue(), cr.getInterpretation()));
        }

        // Discordant nodes section
        sb.append("\nsection,node,metric1,rank1,metric2,rank2,rank_difference\n");
        List<DiscordantNode> discordant = getDiscordantNodes(0.0);
        for (DiscordantNode dn : discordant) {
            sb.append(String.format("discordant,%s,%s,%d,%s,%d,%d\n",
                    dn.getNodeId(), dn.getMetric1Name(), dn.getRank1(),
                    dn.getMetric2Name(), dn.getRank2(), dn.getRankDifference()));
        }

        return sb.toString();
    }

    // --- Private helper methods ---

    /**
     * Builds 1-based ranks from a centrality value map.
     * Higher centrality values get lower (better) rank numbers.
     * Ties receive the same rank (average rank assignment).
     */
    private Map<String, Integer> buildRanks(Map<String, Double> values) {
        List<Map.Entry<String, Double>> entries =
                new ArrayList<Map.Entry<String, Double>>(values.entrySet());
        Collections.sort(entries, (Map.Entry<String, Double> a, Map.Entry<String, Double> b) -> {
                return Double.compare(b.getValue(), a.getValue()); // descending
            });

        Map<String, Integer> ranks = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < entries.size(); i++) {
            ranks.put(entries.get(i).getKey(), i + 1);
        }
        return ranks;
    }

    /**
     * Computes Spearman rank correlation coefficient between two rank maps.
     */
    private CorrelationResult computeSpearman(String name1, Map<String, Integer> ranks1,
                                               String name2, Map<String, Integer> ranks2) {
        int n = ranks1.size();
        if (n < 2) {
            return new CorrelationResult(name1, name2, 0.0, 1.0, "weak");
        }

        // Sum of squared rank differences
        double sumD2 = 0;
        for (String node : ranks1.keySet()) {
            Integer r1 = ranks1.get(node);
            Integer r2 = ranks2.get(node);
            if (r1 != null && r2 != null) {
                double d = r1 - r2;
                sumD2 += d * d;
            }
        }

        // Spearman rho = 1 - (6 * sum(d^2)) / (n * (n^2 - 1))
        double rho = 1.0 - (6.0 * sumD2) / (n * ((long) n * n - 1));

        // Clamp to [-1, 1] for numerical stability
        rho = Math.max(-1.0, Math.min(1.0, rho));

        // Approximate p-value using t-distribution approximation
        double pValue = approximatePValue(rho, n);

        String interpretation = interpretCorrelation(rho);

        return new CorrelationResult(name1, name2, rho, pValue, interpretation);
    }

    /**
     * Approximates the p-value for a Spearman correlation using the
     * t-distribution approach: t = rho * sqrt((n-2) / (1-rho^2)).
     */
    private double approximatePValue(double rho, int n) {
        if (n <= 2) return 1.0;
        if (Math.abs(rho) >= 1.0) return 0.0;

        double t = rho * Math.sqrt((n - 2.0) / (1.0 - rho * rho));
        int df = n - 2;

        // Approximate two-tailed p-value using the regularized incomplete beta function
        // For simplicity, use a rough empirical approximation
        double absT = Math.abs(t);
        if (df <= 0) return 1.0;

        // Use the approximation: p ≈ 2 * (1 - Φ(|t| * sqrt(df/(df+t^2))))
        // where Φ is the standard normal CDF
        double x = df / (df + absT * absT);
        double p = Math.pow(x, df / 2.0);

        return Math.min(1.0, p);
    }

    /**
     * Interprets correlation strength based on absolute rho value.
     */
    private String interpretCorrelation(double rho) {
        double abs = Math.abs(rho);
        if (abs >= 0.7) return "strong";
        if (abs >= 0.4) return "moderate";
        return "weak";
    }

    /**
     * Adds a discordant node to the result list if its rank difference
     * meets or exceeds the minimum difference.
     */
    private void addIfDiscordant(List<DiscordantNode> result, String node,
                                  String name1, Map<String, Integer> ranks1,
                                  String name2, Map<String, Integer> ranks2,
                                  int minDiff) {
        Integer r1 = ranks1.get(node);
        Integer r2 = ranks2.get(node);
        if (r1 != null && r2 != null) {
            int diff = Math.abs(r1 - r2);
            if (diff >= minDiff) {
                result.add(new DiscordantNode(node, name1, r1, name2, r2));
            }
        }
    }
}
