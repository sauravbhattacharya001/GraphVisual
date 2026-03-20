package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;
import java.util.Arrays;

/**
 * Computes PageRank scores for nodes in a JUNG graph using the power-iteration
 * method. PageRank models a "random surfer" who follows links with probability
 * {@code dampingFactor} and teleports to a random node with probability
 * {@code 1 - dampingFactor}.
 *
 * <p>Unlike degree/betweenness centrality, PageRank captures <em>recursive
 * importance</em>: a node is important not just because it has many connections,
 * but because it is connected to other important nodes. This makes it
 * particularly useful for identifying influential nodes in social networks,
 * citation graphs, and communication networks.</p>
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Initialize all nodes with equal rank: 1/V</li>
 *   <li>For each iteration, each node distributes its rank equally to neighbors</li>
 *   <li>Apply damping factor: {@code rank(v) = (1-d)/V + d * sum(rank(u)/degree(u))}</li>
 *   <li>Repeat until convergence (L1 norm &lt; tolerance) or max iterations reached</li>
 * </ol>
 *
 * <p>For undirected graphs, each edge counts as a bidirectional link.
 * Dangling nodes (degree 0) distribute their rank uniformly to all nodes.</p>
 *
 * @author zalenix
 */
public class PageRankAnalyzer {

    /** Default damping factor (probability of following a link). */
    public static final double DEFAULT_DAMPING = 0.85;

    /** Default convergence tolerance (L1 norm of rank vector change). */
    public static final double DEFAULT_TOLERANCE = 1e-6;

    /** Default maximum iterations before stopping. */
    public static final int DEFAULT_MAX_ITERATIONS = 100;

    private final Graph<String, Edge> graph;
    private final double dampingFactor;
    private final double tolerance;
    private final int maxIterations;

    private Map<String, Double> ranks;
    private int iterationsUsed;
    private boolean converged;
    private boolean computed;

    /**
     * Creates a PageRankAnalyzer with default parameters (d=0.85, tol=1e-6, maxIter=100).
     *
     * @param graph the JUNG graph to analyze
     * @throws IllegalArgumentException if graph is null
     */
    public PageRankAnalyzer(Graph<String, Edge> graph) {
        this(graph, DEFAULT_DAMPING, DEFAULT_TOLERANCE, DEFAULT_MAX_ITERATIONS);
    }

    /**
     * Creates a PageRankAnalyzer with a custom damping factor.
     *
     * @param graph         the JUNG graph to analyze
     * @param dampingFactor probability of following a link (typically 0.85)
     * @throws IllegalArgumentException if graph is null or damping factor out of range
     */
    public PageRankAnalyzer(Graph<String, Edge> graph, double dampingFactor) {
        this(graph, dampingFactor, DEFAULT_TOLERANCE, DEFAULT_MAX_ITERATIONS);
    }

    /**
     * Creates a PageRankAnalyzer with full parameter control.
     *
     * @param graph         the JUNG graph to analyze
     * @param dampingFactor probability of following a link, must be in (0, 1)
     * @param tolerance     convergence threshold for L1 norm
     * @param maxIterations maximum number of power iterations
     * @throws IllegalArgumentException if graph is null or parameters out of range
     */
    public PageRankAnalyzer(Graph<String, Edge> graph, double dampingFactor,
                            double tolerance, int maxIterations) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        if (dampingFactor <= 0.0 || dampingFactor >= 1.0) {
            throw new IllegalArgumentException(
                    "Damping factor must be in (0, 1), got: " + dampingFactor);
        }
        if (tolerance <= 0.0) {
            throw new IllegalArgumentException(
                    "Tolerance must be positive, got: " + tolerance);
        }
        if (maxIterations <= 0) {
            throw new IllegalArgumentException(
                    "Max iterations must be positive, got: " + maxIterations);
        }
        this.graph = graph;
        this.dampingFactor = dampingFactor;
        this.tolerance = tolerance;
        this.maxIterations = maxIterations;
        this.ranks = new LinkedHashMap<String, Double>();
        this.iterationsUsed = 0;
        this.converged = false;
        this.computed = false;
    }

    // ──────────────── Result type ────────────────

    /**
     * Holds PageRank results for a single node.
     */
    public static class PageRankResult implements Comparable<PageRankResult> {
        private final String nodeId;
        private final double rank;
        private final int degree;
        private final double normalizedRank;

        public PageRankResult(String nodeId, double rank, int degree, double normalizedRank) {
            this.nodeId = nodeId;
            this.rank = rank;
            this.degree = degree;
            this.normalizedRank = normalizedRank;
        }

        /** Vertex ID. */
        public String getNodeId() { return nodeId; }

        /**
         * Raw PageRank score. Sum of all scores = 1.0 (probability distribution).
         */
        public double getRank() { return rank; }

        /** Node degree (number of connections). */
        public int getDegree() { return degree; }

        /**
         * Normalized rank: raw rank × V. A value &gt; 1.0 means this node has
         * above-average importance; &lt; 1.0 means below average. Useful for
         * comparing across graphs of different sizes.
         */
        public double getNormalizedRank() { return normalizedRank; }

        /**
         * Percentile label: how many times more important than average.
         * Returns "average" if within 5% of 1.0, otherwise "Nx above" or "Nx below".
         */
        public String getImportanceLabel() {
            if (normalizedRank > 1.05) {
                return String.format("%.1fx above average", normalizedRank);
            } else if (normalizedRank < 0.95) {
                return String.format("%.1fx below average", normalizedRank);
            }
            return "average";
        }

        /** Sort by raw rank, descending. */
        public int compareTo(PageRankResult other) {
            return Double.compare(other.rank, this.rank);
        }

        @Override
        public String toString() {
            return String.format("Node %s: rank=%.6f (%.1fx), degree=%d",
                    nodeId, rank, normalizedRank, degree);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PageRankResult that = (PageRankResult) o;
            return nodeId.equals(that.nodeId);
        }

        @Override
        public int hashCode() {
            return nodeId.hashCode();
        }
    }

    // ──────────────── Computation ────────────────

    /**
     * Runs the PageRank power-iteration algorithm.
     * Automatically skips recomputation if already computed.
     *
     * <p>Uses array-based computation internally for performance: vertex
     * names are mapped to integer indices and ranks are stored in
     * {@code double[]} arrays, eliminating HashMap lookups, Double
     * autoboxing, and per-iteration allocation in the hot loop.
     * Final results are copied back into the public {@code ranks} map.</p>
     */
    public void compute() {
        if (computed) return;

        int n = graph.getVertexCount();
        if (n == 0) {
            computed = true;
            converged = true;
            iterationsUsed = 0;
            return;
        }

        // Build vertex index mapping for array-based computation
        List<String> vertexList = new ArrayList<String>(graph.getVertices());
        Map<String, Integer> vertexIndex = new HashMap<String, Integer>(n * 2);
        for (int i = 0; i < n; i++) {
            vertexIndex.put(vertexList.get(i), i);
        }

        // Build adjacency lists using integer indices
        int[][] adjLists = new int[n][];
        int[] degrees = new int[n];
        boolean[] isDangling = new boolean[n];
        for (int i = 0; i < n; i++) {
            String node = vertexList.get(i);
            Collection<Edge> edges = graph.getIncidentEdges(node);
            List<Integer> nodeAdj = new ArrayList<Integer>();
            if (edges != null) {
                for ((Edge e : edges) {
                    String other = getOtherEnd(e, node);
                    if (other != null) {
                        Integer idx = vertexIndex.get(other);
                        if (idx != null) nodeAdj.add(idx);
                    }
                }
            }
            adjLists[i] = new int[nodeAdj.size()];
            for (int j = 0; j < nodeAdj.size(); j++) {
                adjLists[i][j] = nodeAdj.get(j);
            }
            degrees[i] = adjLists[i].length;
            isDangling[i] = degrees[i] == 0;
        }

        // Initialize uniform distribution using arrays
        double[] rankArr = new double[n];
        double[] newRankArr = new double[n];
        double initial = 1.0 / n;
        Arrays.fill(rankArr, initial);

        double teleport = (1.0 - dampingFactor) / n;

        // Power iteration with arrays
        for (int iter = 0; iter < maxIterations; iter++) {
            // Dangling node contribution
            double danglingSum = 0.0;
            for (int i = 0; i < n; i++) {
                if (isDangling[i]) danglingSum += rankArr[i];
            }
            double danglingContrib = dampingFactor * danglingSum / n;

            // Compute new ranks
            for (int i = 0; i < n; i++) {
                double incomingRank = 0.0;
                for (int j : adjLists[i]) {
                    if (degrees[j] > 0) {
                        incomingRank += rankArr[j] / degrees[j];
                    }
                }
                newRankArr[i] = teleport + danglingContrib + dampingFactor * incomingRank;
            }

            // Check convergence (L1 norm)
            double diff = 0.0;
            for (int i = 0; i < n; i++) {
                diff += Math.abs(newRankArr[i] - rankArr[i]);
            }

            // Swap arrays (avoid allocation)
            double[] tmp = rankArr;
            rankArr = newRankArr;
            newRankArr = tmp;

            iterationsUsed = iter + 1;
            if (diff < tolerance) {
                converged = true;
                break;
            }
        }

        // Store results in the map for query methods
        for (int i = 0; i < n; i++) {
            ranks.put(vertexList.get(i), rankArr[i]);
        }

        computed = true;
    }

    // ──────────────── Query methods ────────────────

    /**
     * Returns whether the algorithm has been run.
     */
    public boolean isComputed() {
        return computed;
    }

    /**
     * Returns whether the algorithm converged within tolerance.
     */
    public boolean isConverged() {
        if (!computed) compute();
        return converged;
    }

    /**
     * Returns the number of iterations used.
     */
    public int getIterationsUsed() {
        if (!computed) compute();
        return iterationsUsed;
    }

    /**
     * Returns the damping factor used.
     */
    public double getDampingFactor() {
        return dampingFactor;
    }

    /**
     * Returns the PageRank result for a specific node.
     *
     * @param nodeId the vertex ID
     * @return the PageRankResult, or null if node not in graph
     */
    public PageRankResult getResult(String nodeId) {
        if (!computed) compute();
        if (!graph.containsVertex(nodeId)) return null;

        int n = graph.getVertexCount();
        double rank = ranks.containsKey(nodeId) ? ranks.get(nodeId) : 0.0;
        double normalized = rank * n;
        int degree = graph.degree(nodeId);

        return new PageRankResult(nodeId, rank, degree, normalized);
    }

    /**
     * Returns all node PageRank results, sorted by rank (highest first).
     *
     * @return sorted list of PageRankResult
     */
    public List<PageRankResult> getRankedResults() {
        if (!computed) compute();

        int n = graph.getVertexCount();
        List<PageRankResult> results = new ArrayList<PageRankResult>();
        for (String nodeId : graph.getVertices()) {
            results.add(getResult(nodeId));
        }
        Collections.sort(results);
        return results;
    }

    /**
     * Returns the top-N highest-ranked nodes.
     *
     * @param n number of top nodes to return
     * @return sorted list of top PageRankResult entries
     */
    public List<PageRankResult> getTopNodes(int n) {
        List<PageRankResult> all = getRankedResults();
        if (n <= 0) return new ArrayList<PageRankResult>();
        return new ArrayList<PageRankResult>(all.subList(0, Math.min(n, all.size())));
    }

    /**
     * Returns the bottom-N lowest-ranked nodes.
     *
     * @param n number of bottom nodes to return
     * @return list of lowest PageRankResult entries (lowest first)
     */
    public List<PageRankResult> getBottomNodes(int n) {
        List<PageRankResult> all = getRankedResults();
        if (n <= 0) return new ArrayList<PageRankResult>();
        int size = all.size();
        int start = Math.max(0, size - n);
        List<PageRankResult> bottom = new ArrayList<PageRankResult>(all.subList(start, size));
        Collections.reverse(bottom);
        return bottom;
    }

    /**
     * Returns the raw rank map (node → PageRank score).
     */
    public Map<String, Double> getRankMap() {
        if (!computed) compute();
        return Collections.unmodifiableMap(ranks);
    }

    /**
     * Returns the rank of a specific node, or 0.0 if not found.
     */
    public double getRank(String nodeId) {
        if (!computed) compute();
        Double r = ranks.get(nodeId);
        return r != null ? r : 0.0;
    }

    /**
     * Computes the rank distribution: what fraction of nodes fall into each
     * importance tier (Very Low / Low / Average / High / Very High).
     *
     * @return ordered map from tier name to node count
     */
    public Map<String, Integer> getRankDistribution() {
        if (!computed) compute();

        int n = graph.getVertexCount();
        Map<String, Integer> distribution = new LinkedHashMap<String, Integer>();
        distribution.put("Very High (>2x)", 0);
        distribution.put("High (1.5-2x)", 0);
        distribution.put("Average (0.5-1.5x)", 0);
        distribution.put("Low (0.25-0.5x)", 0);
        distribution.put("Very Low (<0.25x)", 0);

        if (n == 0) return distribution;

        for (String node : graph.getVertices()) {
            double normalized = ranks.get(node) * n;
            if (normalized > 2.0) {
                distribution.put("Very High (>2x)", distribution.get("Very High (>2x)") + 1);
            } else if (normalized > 1.5) {
                distribution.put("High (1.5-2x)", distribution.get("High (1.5-2x)") + 1);
            } else if (normalized >= 0.5) {
                distribution.put("Average (0.5-1.5x)", distribution.get("Average (0.5-1.5x)") + 1);
            } else if (normalized >= 0.25) {
                distribution.put("Low (0.25-0.5x)", distribution.get("Low (0.25-0.5x)") + 1);
            } else {
                distribution.put("Very Low (<0.25x)", distribution.get("Very Low (<0.25x)") + 1);
            }
        }

        return distribution;
    }

    /**
     * Finds nodes whose importance is disproportionate to their degree.
     * These are nodes that derive influence from being connected to other
     * important nodes rather than having many connections themselves.
     *
     * <p>A node is considered to have "hidden influence" if its normalized
     * PageRank exceeds its degree centrality by a factor of {@code threshold}.</p>
     *
     * @param threshold minimum ratio of normalizedRank to degreeCentrality (default: 1.5)
     * @return list of nodes with hidden influence, sorted by influence ratio descending
     */
    public List<HiddenInfluence> findHiddenInfluencers(double threshold) {
        if (!computed) compute();

        int n = graph.getVertexCount();
        if (n <= 1) return new ArrayList<HiddenInfluence>();

        List<HiddenInfluence> influencers = new ArrayList<HiddenInfluence>();

        for (String node : graph.getVertices()) {
            int degree = graph.degree(node);
            double degreeCentrality = (double) degree / (n - 1);
            double normalizedRank = ranks.get(node) * n;

            // Skip nodes with zero degree centrality (isolated) to avoid div-by-zero
            if (degreeCentrality < 1e-10) continue;

            double ratio = normalizedRank / degreeCentrality;
            if (ratio > threshold) {
                influencers.add(new HiddenInfluence(node, degree, normalizedRank,
                        degreeCentrality, ratio));
            }
        }

        Collections.sort(influencers, (HiddenInfluence a, HiddenInfluence b) -> {
                return Double.compare(b.getInfluenceRatio(), a.getInfluenceRatio());
            });

        return influencers;
    }

    /**
     * Convenience overload with default threshold of 1.5.
     */
    public List<HiddenInfluence> findHiddenInfluencers() {
        return findHiddenInfluencers(1.5);
    }

    /**
     * Represents a node whose PageRank influence exceeds what its degree
     * alone would predict — indicating influence through connections to
     * important nodes.
     */
    public static class HiddenInfluence {
        private final String nodeId;
        private final int degree;
        private final double normalizedRank;
        private final double degreeCentrality;
        private final double influenceRatio;

        public HiddenInfluence(String nodeId, int degree, double normalizedRank,
                               double degreeCentrality, double influenceRatio) {
            this.nodeId = nodeId;
            this.degree = degree;
            this.normalizedRank = normalizedRank;
            this.degreeCentrality = degreeCentrality;
            this.influenceRatio = influenceRatio;
        }

        public String getNodeId() { return nodeId; }
        public int getDegree() { return degree; }
        public double getNormalizedRank() { return normalizedRank; }
        public double getDegreeCentrality() { return degreeCentrality; }
        public double getInfluenceRatio() { return influenceRatio; }

        @Override
        public String toString() {
            return String.format("Node %s: influence ratio=%.2f (rank=%.2fx, degree centrality=%.3f)",
                    nodeId, influenceRatio, normalizedRank, degreeCentrality);
        }
    }

    /**
     * Returns a comprehensive summary of the PageRank analysis.
     *
     * @return map with keys: nodeCount, dampingFactor, iterations, converged,
     *         maxRank, minRank, maxRankNode, minRankNode, giniCoefficient,
     *         entropyRatio, rankDistribution
     */
    public Map<String, Object> getSummary() {
        if (!computed) compute();

        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        int n = graph.getVertexCount();

        summary.put("nodeCount", n);
        summary.put("edgeCount", graph.getEdgeCount());
        summary.put("dampingFactor", dampingFactor);
        summary.put("iterations", iterationsUsed);
        summary.put("converged", converged);

        if (n == 0) {
            summary.put("maxRank", 0.0);
            summary.put("minRank", 0.0);
            summary.put("maxRankNode", "none");
            summary.put("minRankNode", "none");
            summary.put("giniCoefficient", 0.0);
            summary.put("entropyRatio", 0.0);
            summary.put("concentrationLevel", "N/A");
            return summary;
        }

        // Find max/min
        double maxRank = Double.MIN_VALUE, minRank = Double.MAX_VALUE;
        String maxNode = "", minNode = "";
        for (Map.Entry<String, Double> entry : ranks.entrySet()) {
            if (entry.getValue() > maxRank) {
                maxRank = entry.getValue();
                maxNode = entry.getKey();
            }
            if (entry.getValue() < minRank) {
                minRank = entry.getValue();
                minNode = entry.getKey();
            }
        }

        summary.put("maxRank", maxRank);
        summary.put("minRank", minRank);
        summary.put("maxRankNode", maxNode);
        summary.put("minRankNode", minNode);
        summary.put("maxNormalizedRank", maxRank * n);
        summary.put("minNormalizedRank", minRank * n);

        // Gini coefficient — measures rank inequality (0=equal, 1=one node has all)
        double gini = computeGiniCoefficient();
        summary.put("giniCoefficient", gini);

        // Shannon entropy ratio — normalized entropy of rank distribution
        double entropyRatio = computeEntropyRatio();
        summary.put("entropyRatio", entropyRatio);

        // Concentration level based on Gini
        String concentration;
        if (gini < 0.2) concentration = "Highly Distributed";
        else if (gini < 0.4) concentration = "Moderately Distributed";
        else if (gini < 0.6) concentration = "Moderately Concentrated";
        else if (gini < 0.8) concentration = "Highly Concentrated";
        else concentration = "Extremely Concentrated";
        summary.put("concentrationLevel", concentration);

        // Rank distribution
        summary.put("rankDistribution", getRankDistribution());

        return summary;
    }

    /**
     * Computes the Gini coefficient of the PageRank distribution.
     * 0.0 means perfectly equal ranks; 1.0 means one node has all the rank.
     *
     * @return Gini coefficient in [0, 1]
     */
    public double computeGiniCoefficient() {
        if (!computed) compute();

        int n = graph.getVertexCount();
        if (n <= 1) return 0.0;

        // Sort ranks ascending
        List<Double> sorted = new ArrayList<Double>(ranks.values());
        Collections.sort(sorted);

        double sumNumerator = 0.0;
        double sumValues = 0.0;
        for (int i = 0; i < n; i++) {
            sumNumerator += (2.0 * (i + 1) - n - 1) * sorted.get(i);
            sumValues += sorted.get(i);
        }

        if (sumValues < 1e-15) return 0.0;
        return sumNumerator / (n * sumValues);
    }

    /**
     * Computes the normalized Shannon entropy of the rank distribution.
     * 1.0 means perfectly uniform; 0.0 means all rank concentrated in one node.
     *
     * @return entropy ratio in [0, 1]
     */
    public double computeEntropyRatio() {
        if (!computed) compute();

        int n = graph.getVertexCount();
        if (n <= 1) return 1.0;

        double maxEntropy = Math.log(n);
        if (maxEntropy < 1e-15) return 1.0;

        double entropy = 0.0;
        for (double r : ranks.values()) {
            if (r > 1e-15) {
                entropy -= r * Math.log(r);
            }
        }

        return entropy / maxEntropy;
    }

    /**
     * Compares PageRank with degree centrality to identify ranking disagreements.
     * Returns pairs of nodes where their PageRank order differs from degree order.
     *
     * @param topN compare only the top N nodes from each ranking
     * @return list of ranking comparison entries
     */
    public List<RankComparison> compareWithDegreeCentrality(int topN) {
        if (!computed) compute();

        int n = graph.getVertexCount();
        if (n == 0 || topN <= 0) return new ArrayList<RankComparison>();

        // Get PageRank ranking
        List<PageRankResult> prRanked = getRankedResults();

        // Get degree ranking
        List<String> degreeRanked = new ArrayList<String>(graph.getVertices());
        Collections.sort(degreeRanked, (String a, String b) -> {
                return Integer.compare(graph.degree(b), graph.degree(a));
            });

        int limit = Math.min(topN, n);
        Map<String, Integer> prPositions = new LinkedHashMap<String, Integer>();
        Map<String, Integer> degPositions = new LinkedHashMap<String, Integer>();

        for (int i = 0; i < prRanked.size(); i++) {
            prPositions.put(prRanked.get(i).getNodeId(), i + 1);
        }
        for (int i = 0; i < degreeRanked.size(); i++) {
            degPositions.put(degreeRanked.get(i), i + 1);
        }

        // Collect nodes that appear in either top-N list
        Set<String> unionNodes = new LinkedHashSet<String>();
        for (int i = 0; i < limit && i < prRanked.size(); i++) {
            unionNodes.add(prRanked.get(i).getNodeId());
        }
        for (int i = 0; i < limit && i < degreeRanked.size(); i++) {
            unionNodes.add(degreeRanked.get(i));
        }

        List<RankComparison> comparisons = new ArrayList<RankComparison>();
        for (String node : unionNodes) {
            int prPos = prPositions.containsKey(node) ? prPositions.get(node) : n;
            int degPos = degPositions.containsKey(node) ? degPositions.get(node) : n;
            int shift = degPos - prPos; // positive = PageRank ranks it higher than degree

            comparisons.add(new RankComparison(node, prPos, degPos, shift,
                    ranks.get(node), graph.degree(node)));
        }

        // Sort by PageRank position
        Collections.sort(comparisons, (RankComparison a, RankComparison b) -> {
                return Integer.compare(a.getPageRankPosition(), b.getPageRankPosition());
            });

        return comparisons;
    }

    /**
     * A comparison entry showing how a node's PageRank position differs
     * from its degree-centrality position.
     */
    public static class RankComparison {
        private final String nodeId;
        private final int pageRankPosition;
        private final int degreePosition;
        private final int positionShift;
        private final double pageRank;
        private final int degree;

        public RankComparison(String nodeId, int pageRankPosition, int degreePosition,
                              int positionShift, double pageRank, int degree) {
            this.nodeId = nodeId;
            this.pageRankPosition = pageRankPosition;
            this.degreePosition = degreePosition;
            this.positionShift = positionShift;
            this.pageRank = pageRank;
            this.degree = degree;
        }

        public String getNodeId() { return nodeId; }
        /** 1-based position in PageRank ordering. */
        public int getPageRankPosition() { return pageRankPosition; }
        /** 1-based position in degree-centrality ordering. */
        public int getDegreePosition() { return degreePosition; }
        /**
         * Position shift: positive means PageRank ranks this node higher than
         * its degree alone would suggest (indicating influence from important neighbors).
         */
        public int getPositionShift() { return positionShift; }
        public double getPageRank() { return pageRank; }
        public int getDegree() { return degree; }

        @Override
        public String toString() {
            String arrow = positionShift > 0 ? "↑" + positionShift :
                    positionShift < 0 ? "↓" + Math.abs(positionShift) : "=";
            return String.format("Node %s: PR#%d, Degree#%d (%s), rank=%.6f, degree=%d",
                    nodeId, pageRankPosition, degreePosition, arrow, pageRank, degree);
        }
    }

    // ──────────────── Private helpers ────────────────

    private String getOtherEnd((Edge e, String current) {
        return GraphUtils.getOtherEnd(e, current);
    }
}
