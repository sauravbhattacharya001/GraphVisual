package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;

import java.util.*;

/**
 * Compresses a graph by merging groups of nodes into supernodes,
 * producing a smaller quotient graph that preserves the macro-structure
 * of the original network.
 *
 * <h3>Compression Strategies</h3>
 * <ul>
 *   <li><b>Structural Equivalence</b> — merges nodes with identical
 *       neighbor sets. Two nodes are structurally equivalent if they
 *       connect to exactly the same set of other nodes.</li>
 *   <li><b>Neighborhood Similarity</b> — merges nodes whose neighbor
 *       sets have Jaccard similarity above a threshold. A relaxed
 *       version of structural equivalence.</li>
 *   <li><b>Degree-Based</b> — groups nodes by degree (or degree ranges),
 *       collapsing each group into a supernode.</li>
 *   <li><b>Attribute-Based</b> — groups nodes by a user-supplied
 *       attribute function (e.g., community label, type).</li>
 *   <li><b>K-Hop Locality</b> — merges each seed node with its
 *       k-hop neighborhood into a supernode.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 *   GraphCompressor compressor = new GraphCompressor(graph);
 *
 *   // Structural equivalence compression
 *   CompressionResult result = compressor.byStructuralEquivalence();
 *   System.out.println(result.getSummary());
 *
 *   // Neighborhood similarity with 0.5 threshold
 *   CompressionResult result2 = compressor.byNeighborhoodSimilarity(0.5);
 *
 *   // Degree-based with bin size 5
 *   CompressionResult result3 = compressor.byDegree(5);
 *
 *   // Attribute-based
 *   Map&lt;String, String&gt; communities = ...;
 *   CompressionResult result4 = compressor.byAttribute(communities::get);
 * </pre>
 *
 * @author zalenix
 */
public class GraphCompressor {

    private final Graph<String, Edge> graph;

    /**
     * Creates a compressor for the given graph.
     *
     * @param graph the graph to compress (must not be null)
     * @throws IllegalArgumentException if graph is null
     */
    public GraphCompressor(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
    }

    // ── Compression Strategies ──────────────────────────────────────

    /**
     * Compresses by structural equivalence: nodes with identical
     * neighbor sets are merged into a single supernode.
     *
     * @return compression result with quotient graph and mappings
     */
    public CompressionResult byStructuralEquivalence() {
        Map<String, Set<String>> neighborSets = new HashMap<>();
        for (String v : graph.getVertices()) {
            neighborSets.put(v, new TreeSet<>(graph.getNeighbors(v)));
        }

        // Group nodes by their sorted neighbor set
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String v : graph.getVertices()) {
            String key = neighborSets.get(v).toString();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
        }

        return buildQuotientGraph(new ArrayList<>(groups.values()), "structural_equivalence");
    }

    /**
     * Compresses by neighborhood similarity: nodes whose neighbor sets
     * have Jaccard similarity ≥ threshold are merged greedily.
     *
     * @param threshold Jaccard similarity threshold in [0.0, 1.0]
     * @return compression result
     * @throws IllegalArgumentException if threshold is out of range
     */
    public CompressionResult byNeighborhoodSimilarity(double threshold) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("Threshold must be in [0.0, 1.0], got: " + threshold);
        }

        Map<String, Set<String>> neighborSets = new HashMap<>();
        for (String v : graph.getVertices()) {
            neighborSets.put(v, new HashSet<>(graph.getNeighbors(v)));
        }

        // Sort vertices by degree to improve pruning effectiveness.
        // Vertices with similar degrees are more likely to have high Jaccard
        // similarity, so sorting brings candidate pairs closer together.
        List<String> vertices = new ArrayList<>(graph.getVertices());
        vertices.sort((a, b) -> Integer.compare(neighborSets.get(a).size(), neighborSets.get(b).size()));

        boolean[] merged = new boolean[vertices.size()];

        List<List<String>> groups = new ArrayList<>();
        for (int i = 0; i < vertices.size(); i++) {
            if (merged[i]) continue;
            List<String> group = new ArrayList<>();
            group.add(vertices.get(i));
            merged[i] = true;
            Set<String> refNeighbors = neighborSets.get(vertices.get(i));
            int refSize = refNeighbors.size();

            for (int j = i + 1; j < vertices.size(); j++) {
                if (merged[j]) continue;
                Set<String> otherNeighbors = neighborSets.get(vertices.get(j));
                int otherSize = otherNeighbors.size();

                // Degree-based upper bound pruning: the maximum possible
                // Jaccard similarity between two sets is min(|A|,|B|)/max(|A|,|B|).
                // Since vertices are sorted by degree, refSize <= otherSize.
                // If this upper bound < threshold, no later vertex can match either
                // (their degrees only increase), so break early.
                if (otherSize > 0 && (double) refSize / otherSize < threshold) {
                    break;
                }

                // Compute Jaccard without allocating new sets: count intersection
                // by iterating the smaller set and checking the larger.
                double jaccard = jaccardFast(refNeighbors, refSize, otherNeighbors, otherSize);
                if (jaccard >= threshold) {
                    group.add(vertices.get(j));
                    merged[j] = true;
                }
            }
            groups.add(group);
        }

        return buildQuotientGraph(groups, "neighborhood_similarity(threshold=" + threshold + ")");
    }

    /**
     * Computes Jaccard similarity without allocating intermediate HashSets.
     * Iterates the smaller set, counting members present in the larger set.
     * Union size is derived as |A| + |B| - |intersection|.
     *
     * @return Jaccard similarity in [0.0, 1.0]
     */
    private static double jaccardFast(Set<String> a, int aSize, Set<String> b, int bSize) {
        if (aSize == 0 && bSize == 0) return 1.0;
        if (aSize == 0 || bSize == 0) return 0.0;

        // Iterate the smaller set for fewer hash lookups
        Set<String> smaller = aSize <= bSize ? a : b;
        Set<String> larger  = aSize <= bSize ? b : a;

        int intersection = 0;
        for (String v : smaller) {
            if (larger.contains(v)) {
                intersection++;
            }
        }
        int union = aSize + bSize - intersection;
        return union == 0 ? 1.0 : (double) intersection / union;
    }

    /**
     * Compresses by degree: nodes are grouped into bins by their degree.
     *
     * @param binSize size of each degree bin (nodes with degree in
     *                [k*binSize, (k+1)*binSize) are grouped together)
     * @return compression result
     * @throws IllegalArgumentException if binSize &lt; 1
     */
    public CompressionResult byDegree(int binSize) {
        if (binSize < 1) {
            throw new IllegalArgumentException("Bin size must be >= 1, got: " + binSize);
        }

        Map<Integer, List<String>> bins = new TreeMap<>();
        for (String v : graph.getVertices()) {
            int degree = graph.degree(v);
            int bin = degree / binSize;
            bins.computeIfAbsent(bin, k -> new ArrayList<>()).add(v);
        }

        return buildQuotientGraph(new ArrayList<>(bins.values()), "degree(binSize=" + binSize + ")");
    }

    /**
     * Compresses by exact degree: nodes with the same degree are merged.
     *
     * @return compression result
     */
    public CompressionResult byExactDegree() {
        return byDegree(1);
    }

    /**
     * Compresses by a user-supplied attribute function: nodes that map
     * to the same attribute value are merged into a supernode.
     *
     * @param attributeFunction maps each node ID to its group label;
     *                          nodes returning null are placed in an
     *                          "unassigned" group
     * @return compression result
     * @throws IllegalArgumentException if attributeFunction is null
     */
    public CompressionResult byAttribute(java.util.function.Function<String, String> attributeFunction) {
        if (attributeFunction == null) {
            throw new IllegalArgumentException("Attribute function must not be null");
        }

        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String v : graph.getVertices()) {
            String attr = attributeFunction.apply(v);
            if (attr == null) attr = "__unassigned__";
            groups.computeIfAbsent(attr, k -> new ArrayList<>()).add(v);
        }

        return buildQuotientGraph(new ArrayList<>(groups.values()), "attribute");
    }

    /**
     * Compresses by k-hop locality: starting from seed nodes, each seed
     * absorbs all nodes within k hops into its supernode. Unclaimed
     * nodes form singleton supernodes.
     *
     * @param seeds seed node IDs
     * @param k     number of hops (must be &gt;= 1)
     * @return compression result
     * @throws IllegalArgumentException if seeds is null/empty or k &lt; 1
     */
    public CompressionResult byKHopLocality(Collection<String> seeds, int k) {
        if (seeds == null || seeds.isEmpty()) {
            throw new IllegalArgumentException("Seeds must not be null or empty");
        }
        if (k < 1) {
            throw new IllegalArgumentException("k must be >= 1, got: " + k);
        }

        Set<String> claimed = new HashSet<>();
        List<List<String>> groups = new ArrayList<>();

        for (String seed : seeds) {
            if (!graph.containsVertex(seed) || claimed.contains(seed)) continue;

            Set<String> neighborhood = new HashSet<>();
            Set<String> frontier = new HashSet<>();
            frontier.add(seed);

            for (int hop = 0; hop <= k; hop++) {
                Set<String> nextFrontier = new HashSet<>();
                for (String v : frontier) {
                    if (claimed.contains(v)) continue;
                    neighborhood.add(v);
                    if (hop < k) {
                        for (String n : graph.getNeighbors(v)) {
                            if (!neighborhood.contains(n) && !claimed.contains(n)) {
                                nextFrontier.add(n);
                            }
                        }
                    }
                }
                frontier = nextFrontier;
            }

            if (!neighborhood.isEmpty()) {
                groups.add(new ArrayList<>(neighborhood));
                claimed.addAll(neighborhood);
            }
        }

        // Add unclaimed nodes as singletons
        for (String v : graph.getVertices()) {
            if (!claimed.contains(v)) {
                groups.add(Collections.singletonList(v));
            }
        }

        return buildQuotientGraph(groups, "k_hop_locality(k=" + k + ",seeds=" + seeds.size() + ")");
    }

    /**
     * Analyzes the compressibility of the graph across all strategies
     * and returns a summary report.
     *
     * @return multi-line compressibility report
     */
    public String compressibilityReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Graph Compressibility Report ===\n");
        sb.append(String.format("Original: %d nodes, %d edges\n\n",
                graph.getVertexCount(), graph.getEdgeCount()));

        CompressionResult structural = byStructuralEquivalence();
        sb.append(formatReportLine("Structural Equivalence", structural));

        double[] thresholds = {0.9, 0.7, 0.5, 0.3};
        for (double t : thresholds) {
            CompressionResult sim = byNeighborhoodSimilarity(t);
            sb.append(formatReportLine("Neighborhood Sim (t=" + t + ")", sim));
        }

        CompressionResult exactDeg = byExactDegree();
        sb.append(formatReportLine("Exact Degree", exactDeg));

        int[] binSizes = {2, 5, 10};
        for (int bs : binSizes) {
            CompressionResult deg = byDegree(bs);
            sb.append(formatReportLine("Degree (bin=" + bs + ")", deg));
        }

        return sb.toString();
    }

    // ── Quotient Graph Builder ──────────────────────────────────────

    private CompressionResult buildQuotientGraph(List<List<String>> groups, String strategy) {
        Graph<String, Edge> quotient = new UndirectedSparseGraph<>();
        Map<String, List<String>> supernodeMembers = new LinkedHashMap<>();
        Map<String, String> nodeToSupernode = new HashMap<>();

        // Create supernodes
        for (int i = 0; i < groups.size(); i++) {
            List<String> group = groups.get(i);
            String supernodeName;
            if (group.size() == 1) {
                supernodeName = group.get(0);
            } else {
                supernodeName = "S" + i + "{" + group.size() + "}";
            }
            quotient.addVertex(supernodeName);
            supernodeMembers.put(supernodeName, new ArrayList<>(group));
            for (String member : group) {
                nodeToSupernode.put(member, supernodeName);
            }
        }

        // Create superedges (aggregate edges between groups)
        Set<String> addedEdges = new HashSet<>();
        int edgeCounter = 0;
        Map<String, SuperEdgeInfo> superEdgeInfos = new HashMap<>();

        for (Edge e : graph.getEdges()) {
            String v1 = graph.getEndpoints(e).getFirst();
            String v2 = graph.getEndpoints(e).getSecond();
            String s1 = nodeToSupernode.get(v1);
            String s2 = nodeToSupernode.get(v2);

            if (s1 == null || s2 == null || s1.equals(s2)) continue;

            String edgeKey = s1.compareTo(s2) < 0 ? s1 + "||" + s2 : s2 + "||" + s1;
            SuperEdgeInfo info = superEdgeInfos.get(edgeKey);
            if (info == null) {
                info = new SuperEdgeInfo();
                superEdgeInfos.put(edgeKey, info);

                Edge superEdge = new Edge("super", s1, s2);
                superEdge.setLabel("compressed");
                quotient.addEdge(superEdge, s1, s2);
                info.Edge = superEdge;
            }
            info.count++;
            info.totalWeight += e.getWeight();
        }

        // Set aggregated weights
        for (SuperEdgeInfo info : superEdgeInfos.values()) {
            info.Edge.setWeight(info.totalWeight);
        }

        return new CompressionResult(
                graph, quotient, supernodeMembers, nodeToSupernode, strategy);
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private static double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        if (union.isEmpty()) return 1.0;
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        return (double) intersection.size() / union.size();
    }

    private String formatReportLine(String label, CompressionResult result) {
        return String.format("  %-30s → %d supernodes, %d edges (%.1f%% node reduction, %.1f%% Edge reduction)\n",
                label,
                result.getCompressedNodeCount(),
                result.getCompressedEdgeCount(),
                result.getNodeReductionPercent(),
                result.getEdgeReductionPercent());
    }

    private static class SuperEdgeInfo {
        Edge Edge;
        int count;
        float totalWeight;
    }

    // ── Result Class ────────────────────────────────────────────────

    /**
     * Holds the result of a graph compression operation: the quotient
     * graph, supernode membership mappings, and compression statistics.
     */
    public static class CompressionResult {
        private final Graph<String, Edge> original;
        private final Graph<String, Edge> compressed;
        private final Map<String, List<String>> supernodeMembers;
        private final Map<String, String> nodeToSupernode;
        private final String strategy;

        CompressionResult(Graph<String, Edge> original,
                          Graph<String, Edge> compressed,
                          Map<String, List<String>> supernodeMembers,
                          Map<String, String> nodeToSupernode,
                          String strategy) {
            this.original = original;
            this.compressed = compressed;
            this.supernodeMembers = Collections.unmodifiableMap(supernodeMembers);
            this.nodeToSupernode = Collections.unmodifiableMap(nodeToSupernode);
            this.strategy = strategy;
        }

        /** Returns the compressed quotient graph. */
        public Graph<String, Edge> getCompressedGraph() { return compressed; }

        /** Returns a map from supernode ID to its member node IDs. */
        public Map<String, List<String>> getSupernodeMembers() { return supernodeMembers; }

        /** Returns a map from original node ID to its supernode ID. */
        public Map<String, String> getNodeToSupernode() { return nodeToSupernode; }

        /** Returns the compression strategy name. */
        public String getStrategy() { return strategy; }

        /** Original node count. */
        public int getOriginalNodeCount() { return original.getVertexCount(); }

        /** Original Edge count. */
        public int getOriginalEdgeCount() { return original.getEdgeCount(); }

        /** Compressed node count. */
        public int getCompressedNodeCount() { return compressed.getVertexCount(); }

        /** Compressed Edge count. */
        public int getCompressedEdgeCount() { return compressed.getEdgeCount(); }

        /** Compression ratio (compressed/original nodes). */
        public double getCompressionRatio() {
            if (original.getVertexCount() == 0) return 1.0;
            return (double) compressed.getVertexCount() / original.getVertexCount();
        }

        /** Node reduction percentage. */
        public double getNodeReductionPercent() {
            return (1.0 - getCompressionRatio()) * 100.0;
        }

        /** Edge reduction percentage. */
        public double getEdgeReductionPercent() {
            if (original.getEdgeCount() == 0) return 0.0;
            return (1.0 - (double) compressed.getEdgeCount() / original.getEdgeCount()) * 100.0;
        }

        /** Number of supernodes that contain more than one original node. */
        public int getMergedGroupCount() {
            return (int) supernodeMembers.values().stream()
                    .filter(members -> members.size() > 1)
                    .count();
        }

        /** Size of the largest supernode (number of merged nodes). */
        public int getLargestSupernodeSize() {
            return supernodeMembers.values().stream()
                    .mapToInt(List::size)
                    .max()
                    .orElse(0);
        }

        /** Average supernode size. */
        public double getAverageSupernodeSize() {
            if (supernodeMembers.isEmpty()) return 0.0;
            return (double) nodeToSupernode.size() / supernodeMembers.size();
        }

        /**
         * Returns the members of a specific supernode.
         *
         * @param supernodeId the supernode ID
         * @return list of member node IDs, or empty list if not found
         */
        public List<String> getMembersOf(String supernodeId) {
            return supernodeMembers.getOrDefault(supernodeId, Collections.emptyList());
        }

        /**
         * Returns which supernode a given original node belongs to.
         *
         * @param nodeId the original node ID
         * @return supernode ID, or null if node not found
         */
        public String getSupernodeOf(String nodeId) {
            return nodeToSupernode.get(nodeId);
        }

        /** Returns a human-readable summary of the compression. */
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Graph Compression Result ===\n");
            sb.append(String.format("Strategy:         %s\n", strategy));
            sb.append(String.format("Original:         %d nodes, %d edges\n",
                    getOriginalNodeCount(), getOriginalEdgeCount()));
            sb.append(String.format("Compressed:       %d nodes, %d edges\n",
                    getCompressedNodeCount(), getCompressedEdgeCount()));
            sb.append(String.format("Node reduction:   %.1f%%\n", getNodeReductionPercent()));
            sb.append(String.format("Edge reduction:   %.1f%%\n", getEdgeReductionPercent()));
            sb.append(String.format("Compression ratio: %.3f\n", getCompressionRatio()));
            sb.append(String.format("Merged groups:    %d\n", getMergedGroupCount()));
            sb.append(String.format("Largest supernode: %d members\n", getLargestSupernodeSize()));
            sb.append(String.format("Avg supernode size: %.1f\n", getAverageSupernodeSize()));
            return sb.toString();
        }

        /**
         * Exports the compression mapping as CSV text.
         *
         * @return CSV with columns: original_node,supernode,group_size
         */
        public String toCSV() {
            StringBuilder sb = new StringBuilder();
            sb.append("original_node,supernode,group_size\n");
            List<String> sortedNodes = new ArrayList<>(nodeToSupernode.keySet());
            Collections.sort(sortedNodes);
            for (String node : sortedNodes) {
                String sn = nodeToSupernode.get(node);
                int size = supernodeMembers.getOrDefault(sn, Collections.emptyList()).size();
                sb.append(String.format("%s,%s,%d\n", node, sn, size));
            }
            return sb.toString();
        }
    }
}
