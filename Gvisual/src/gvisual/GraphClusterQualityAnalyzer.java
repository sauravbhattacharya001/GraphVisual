package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Evaluates the quality of a graph clustering using standard network science
 * metrics. Works with any partitioning — from {@link CommunityDetector},
 * {@link GraphPartitioner}, or user-supplied cluster assignments.
 *
 * <h3>Metrics</h3>
 * <ul>
 *   <li><b>Modularity (Newman–Girvan Q):</b> Fraction of intra-cluster edges
 *       minus expected fraction under a random null model. Q &gt; 0.3 generally
 *       indicates significant community structure. Range: [-0.5, 1.0].</li>
 *   <li><b>Coverage:</b> Fraction of all edges that fall within clusters.
 *       Higher is better (all edges inside = 1.0).</li>
 *   <li><b>Conductance (per-cluster):</b> Ratio of cut edges to total edge
 *       boundary. Lower is better (fewer edges leaving the cluster).
 *       Minimum conductance reported as the worst-case cluster.</li>
 *   <li><b>Normalized Cut (NCut):</b> Sum of conductances across all clusters.
 *       Lower is better.</li>
 *   <li><b>Intra-cluster density:</b> Average density of subgraphs induced by
 *       each cluster. Higher indicates tightly connected clusters.</li>
 *   <li><b>Inter-cluster density:</b> Density of edges between different
 *       clusters. Lower indicates well-separated clusters.</li>
 *   <li><b>Density ratio:</b> intra / inter. Higher is better.</li>
 *   <li><b>Cluster size balance:</b> Entropy-based measure of cluster size
 *       distribution. 1.0 = perfectly balanced; near 0 = one dominant cluster.</li>
 * </ul>
 *
 * <h3>Clustering Comparison</h3>
 * <ul>
 *   <li><b>Normalized Mutual Information (NMI):</b> Measures agreement between
 *       two clusterings. 0 = no agreement, 1 = identical.</li>
 *   <li><b>Adjusted Rand Index (ARI):</b> Chance-adjusted pairwise agreement.
 *       0 = random, 1 = perfect, negative = worse than random.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * Graph<String, edge> g = ...;
 * Map<String, Integer> clustering = new HashMap<>();
 * clustering.put("A", 0);
 * clustering.put("B", 0);
 * clustering.put("C", 1);
 *
 * GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(g);
 * GraphClusterQualityAnalyzer.QualityReport report = analyzer.evaluate(clustering);
 * System.out.println("Modularity: " + report.getModularity());
 * System.out.println(report.getSummary());
 * }</pre>
 *
 * @author zalenix
 */
public class GraphClusterQualityAnalyzer {

    private final Graph<String, edge> graph;

    /**
     * Creates a new cluster quality analyzer for the given graph.
     *
     * @param graph the JUNG graph whose clustering will be evaluated
     * @throws IllegalArgumentException if graph is null
     */
    public GraphClusterQualityAnalyzer(Graph<String, edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
    }

    // ── Result Class ────────────────────────────────────────────

    /**
     * Comprehensive quality report for a graph clustering.
     */
    public static class QualityReport {
        private final double modularity;
        private final double coverage;
        private final double normalizedCut;
        private final double minConductance;
        private final double maxConductance;
        private final double avgConductance;
        private final double intraClusterDensity;
        private final double interClusterDensity;
        private final double densityRatio;
        private final double sizeBalance;
        private final int clusterCount;
        private final int nodeCount;
        private final int edgeCount;
        private final int intraEdges;
        private final int interEdges;
        private final Map<Integer, Integer> clusterSizes;
        private final Map<Integer, Double> clusterConductances;
        private final Map<Integer, Double> clusterDensities;

        QualityReport(double modularity, double coverage, double normalizedCut,
                      double minConductance, double maxConductance, double avgConductance,
                      double intraClusterDensity, double interClusterDensity,
                      double densityRatio, double sizeBalance,
                      int clusterCount, int nodeCount, int edgeCount,
                      int intraEdges, int interEdges,
                      Map<Integer, Integer> clusterSizes,
                      Map<Integer, Double> clusterConductances,
                      Map<Integer, Double> clusterDensities) {
            this.modularity = modularity;
            this.coverage = coverage;
            this.normalizedCut = normalizedCut;
            this.minConductance = minConductance;
            this.maxConductance = maxConductance;
            this.avgConductance = avgConductance;
            this.intraClusterDensity = intraClusterDensity;
            this.interClusterDensity = interClusterDensity;
            this.densityRatio = densityRatio;
            this.sizeBalance = sizeBalance;
            this.clusterCount = clusterCount;
            this.nodeCount = nodeCount;
            this.edgeCount = edgeCount;
            this.intraEdges = intraEdges;
            this.interEdges = interEdges;
            this.clusterSizes = Collections.unmodifiableMap(
                new LinkedHashMap<Integer, Integer>(clusterSizes));
            this.clusterConductances = Collections.unmodifiableMap(
                new LinkedHashMap<Integer, Double>(clusterConductances));
            this.clusterDensities = Collections.unmodifiableMap(
                new LinkedHashMap<Integer, Double>(clusterDensities));
        }

        /** Newman-Girvan modularity Q. Range: [-0.5, 1.0]. Higher = better. */
        public double getModularity() { return modularity; }

        /** Fraction of edges within clusters. Range: [0, 1]. Higher = better. */
        public double getCoverage() { return coverage; }

        /** Sum of per-cluster conductances. Lower = better. */
        public double getNormalizedCut() { return normalizedCut; }

        /** Best (lowest) conductance among all clusters. */
        public double getMinConductance() { return minConductance; }

        /** Worst (highest) conductance among all clusters. */
        public double getMaxConductance() { return maxConductance; }

        /** Average conductance across all clusters. */
        public double getAvgConductance() { return avgConductance; }

        /** Average density of subgraphs within each cluster. */
        public double getIntraClusterDensity() { return intraClusterDensity; }

        /** Density of edges between different clusters. */
        public double getInterClusterDensity() { return interClusterDensity; }

        /** Ratio of intra to inter density. Higher = better separation. */
        public double getDensityRatio() { return densityRatio; }

        /** Entropy-based size balance. 1.0 = perfectly balanced. */
        public double getSizeBalance() { return sizeBalance; }

        /** Number of clusters. */
        public int getClusterCount() { return clusterCount; }

        /** Total nodes in the clustering. */
        public int getNodeCount() { return nodeCount; }

        /** Total edges in the graph. */
        public int getEdgeCount() { return edgeCount; }

        /** Edges within clusters. */
        public int getIntraEdges() { return intraEdges; }

        /** Edges between clusters. */
        public int getInterEdges() { return interEdges; }

        /** Cluster ID to size. */
        public Map<Integer, Integer> getClusterSizes() { return clusterSizes; }

        /** Cluster ID to conductance. */
        public Map<Integer, Double> getClusterConductances() { return clusterConductances; }

        /** Cluster ID to internal density. */
        public Map<Integer, Double> getClusterDensities() { return clusterDensities; }

        /**
         * Quality verdict based on modularity.
         * @return "strong", "moderate", "weak", or "insignificant"
         */
        public String getQualityVerdict() {
            if (modularity >= 0.5) return "strong";
            if (modularity >= 0.3) return "moderate";
            if (modularity >= 0.1) return "weak";
            return "insignificant";
        }

        /**
         * Human-readable summary of the clustering quality.
         */
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Cluster Quality Report ===\n");
            sb.append(String.format("Clusters: %d | Nodes: %d | Edges: %d\n",
                clusterCount, nodeCount, edgeCount));
            sb.append(String.format("Intra-cluster edges: %d | Inter-cluster edges: %d\n\n",
                intraEdges, interEdges));
            sb.append(String.format("Modularity (Q):       %.4f  [%s]\n",
                modularity, getQualityVerdict()));
            sb.append(String.format("Coverage:             %.4f\n", coverage));
            sb.append(String.format("Normalized Cut:       %.4f\n", normalizedCut));
            sb.append(String.format("Conductance (avg):    %.4f  [min=%.4f, max=%.4f]\n",
                avgConductance, minConductance, maxConductance));
            sb.append(String.format("Intra-cluster density: %.4f\n", intraClusterDensity));
            sb.append(String.format("Inter-cluster density: %.4f\n", interClusterDensity));
            sb.append(String.format("Density ratio:        %.4f\n", densityRatio));
            sb.append(String.format("Size balance:         %.4f\n\n", sizeBalance));

            sb.append("--- Per-Cluster ---\n");
            for (int cid : clusterSizes.keySet()) {
                sb.append(String.format("  Cluster %d: %d nodes, density=%.4f, conductance=%.4f\n",
                    cid, clusterSizes.get(cid),
                    clusterDensities.containsKey(cid) ? clusterDensities.get(cid) : 0.0,
                    clusterConductances.containsKey(cid) ? clusterConductances.get(cid) : 0.0));
            }
            return sb.toString();
        }
    }

    // ── Public API ──────────────────────────────────────────────

    /**
     * Evaluate the quality of a clustering.
     *
     * @param clustering map from node ID to cluster ID (integer)
     * @return detailed quality report
     * @throws IllegalArgumentException if clustering is null or empty,
     *         or contains nodes not in the graph
     */
    public QualityReport evaluate(Map<String, Integer> clustering) {
        if (clustering == null || clustering.isEmpty()) {
            throw new IllegalArgumentException("Clustering must not be null or empty");
        }

        // Validate: all clustered nodes must exist in the graph
        for (String node : clustering.keySet()) {
            if (!graph.containsVertex(node)) {
                throw new IllegalArgumentException(
                    "Node '" + node + "' in clustering but not in graph");
            }
        }

        // Build inverse mapping: cluster ID to set of nodes
        Map<Integer, Set<String>> clusters = new LinkedHashMap<Integer, Set<String>>();
        for (Map.Entry<String, Integer> entry : clustering.entrySet()) {
            int cid = entry.getValue();
            if (!clusters.containsKey(cid)) {
                clusters.put(cid, new LinkedHashSet<String>());
            }
            clusters.get(cid).add(entry.getKey());
        }

        int totalEdges = graph.getEdgeCount();
        int totalNodes = clustering.size();
        int clusterCount = clusters.size();

        // Count intra-cluster and inter-cluster edges
        int intraEdges = 0;
        int interEdges = 0;
        for (edge e : graph.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            Integer c1 = clustering.get(v1);
            Integer c2 = clustering.get(v2);
            if (c1 == null || c2 == null) {
                continue; // unclustered endpoint
            }
            if (c1.equals(c2)) {
                intraEdges++;
            } else {
                interEdges++;
            }
        }

        // Modularity (Newman-Girvan Q)
        double modularity = computeModularity(clustering, clusters, totalEdges);

        // Coverage
        double coverage = totalEdges > 0 ? (double) intraEdges / totalEdges : 0.0;

        // Per-cluster metrics
        Map<Integer, Double> conductances = new LinkedHashMap<Integer, Double>();
        Map<Integer, Double> densities = new LinkedHashMap<Integer, Double>();
        Map<Integer, Integer> sizes = new LinkedHashMap<Integer, Integer>();

        double normalizedCut = 0.0;
        double conductanceSum = 0.0;
        double minConductance = Double.MAX_VALUE;
        double maxConductance = Double.MIN_VALUE;
        double totalIntraDensity = 0.0;

        for (Map.Entry<Integer, Set<String>> entry : clusters.entrySet()) {
            int cid = entry.getKey();
            Set<String> members = entry.getValue();
            sizes.put(cid, members.size());

            // Intra-cluster edges and cut edges for this cluster
            int clusterIntra = 0;
            int cutEdges = 0;
            int totalDegree = 0;

            for (String node : members) {
                Collection<edge> incidents = graph.getIncidentEdges(node);
                if (incidents == null) continue;
                totalDegree += incidents.size();
                for (edge e : incidents) {
                    String neighbor = getOtherEnd(e, node);
                    Integer neighborCluster = clustering.get(neighbor);
                    if (neighborCluster != null && neighborCluster.equals(cid)) {
                        clusterIntra++; // counted twice (once per endpoint)
                    } else {
                        cutEdges++;
                    }
                }
            }
            clusterIntra /= 2; // each intra-edge counted from both endpoints

            // Conductance: cut / min(totalDegree, 2m - totalDegree)
            double denom = Math.min(totalDegree, 2 * totalEdges - totalDegree);
            double cond = denom > 0 ? (double) cutEdges / denom : 0.0;
            conductances.put(cid, cond);
            conductanceSum += cond;
            normalizedCut += cond;
            if (cond < minConductance) minConductance = cond;
            if (cond > maxConductance) maxConductance = cond;

            // Density of the cluster subgraph
            int n = members.size();
            long maxPossible = (long) n * (n - 1) / 2;
            double density = maxPossible > 0 ? (double) clusterIntra / maxPossible : 0.0;
            densities.put(cid, density);
            totalIntraDensity += density;
        }

        if (clusterCount == 0) {
            minConductance = 0;
            maxConductance = 0;
        }
        double avgConductance = clusterCount > 0 ? conductanceSum / clusterCount : 0.0;
        double intraClusterDensity = clusterCount > 0 ? totalIntraDensity / clusterCount : 0.0;

        // Inter-cluster density
        long totalIntraPossible = 0;
        for (Set<String> members : clusters.values()) {
            int n = members.size();
            totalIntraPossible += (long) n * (n - 1) / 2;
        }
        long totalPossible = (long) totalNodes * (totalNodes - 1) / 2;
        long interPossible = totalPossible - totalIntraPossible;
        double interClusterDensity = interPossible > 0
            ? (double) interEdges / interPossible : 0.0;

        // Density ratio
        double densityRatio = interClusterDensity > 0
            ? intraClusterDensity / interClusterDensity
            : (intraClusterDensity > 0 ? Double.POSITIVE_INFINITY : 0.0);

        // Size balance (normalized entropy)
        double sizeBalance = computeSizeBalance(sizes, totalNodes);

        return new QualityReport(
            modularity, coverage, normalizedCut,
            minConductance, maxConductance, avgConductance,
            intraClusterDensity, interClusterDensity,
            densityRatio, sizeBalance,
            clusterCount, totalNodes, totalEdges,
            intraEdges, interEdges,
            sizes, conductances, densities
        );
    }

    /**
     * Convenience method: evaluate a clustering given as a list of sets.
     * Each set is a cluster, nodes are auto-assigned integer cluster IDs.
     *
     * @param clusterSets list of node sets (one per cluster)
     * @return quality report
     */
    public QualityReport evaluate(List<Set<String>> clusterSets) {
        Map<String, Integer> clustering = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < clusterSets.size(); i++) {
            for (String node : clusterSets.get(i)) {
                clustering.put(node, i);
            }
        }
        return evaluate(clustering);
    }

    /**
     * Compare two clusterings of the same graph using Normalized Mutual
     * Information (NMI). Score ranges from 0 (no mutual information) to
     * 1 (identical clusterings).
     *
     * @param clusteringA first clustering (node to cluster ID)
     * @param clusteringB second clustering (node to cluster ID)
     * @return NMI score in [0, 1]
     * @throws IllegalArgumentException if clusterings are null or have
     *         different node sets
     */
    public double normalizedMutualInformation(
            Map<String, Integer> clusteringA,
            Map<String, Integer> clusteringB) {
        if (clusteringA == null || clusteringB == null) {
            throw new IllegalArgumentException("Clusterings must not be null");
        }
        if (!clusteringA.keySet().equals(clusteringB.keySet())) {
            throw new IllegalArgumentException(
                "Clusterings must cover the same set of nodes");
        }

        int n = clusteringA.size();
        if (n == 0) return 0.0;

        // Build cluster to members maps
        Map<Integer, Set<String>> clustersA = invertClustering(clusteringA);
        Map<Integer, Set<String>> clustersB = invertClustering(clusteringB);

        // Compute entropy H(A), H(B), and mutual information I(A,B)
        double ha = 0.0;
        for (Set<String> clust : clustersA.values()) {
            double p = (double) clust.size() / n;
            if (p > 0) ha -= p * log2(p);
        }

        double hb = 0.0;
        for (Set<String> clust : clustersB.values()) {
            double p = (double) clust.size() / n;
            if (p > 0) hb -= p * log2(p);
        }

        // Mutual information
        double mi = 0.0;
        for (Set<String> cA : clustersA.values()) {
            for (Set<String> cB : clustersB.values()) {
                Set<String> intersection = new HashSet<String>(cA);
                intersection.retainAll(cB);
                int overlap = intersection.size();
                if (overlap > 0) {
                    double pAB = (double) overlap / n;
                    double pA = (double) cA.size() / n;
                    double pB = (double) cB.size() / n;
                    mi += pAB * log2(pAB / (pA * pB));
                }
            }
        }

        double denominator = (ha + hb) / 2.0;
        return denominator > 0 ? mi / denominator : 0.0;
    }

    /**
     * Compute Adjusted Rand Index (ARI) between two clusterings.
     * ARI adjusts the Rand Index for chance: 0 = random agreement,
     * 1 = perfect agreement, negative = worse than random.
     *
     * @param clusteringA first clustering
     * @param clusteringB second clustering
     * @return ARI score
     */
    public double adjustedRandIndex(
            Map<String, Integer> clusteringA,
            Map<String, Integer> clusteringB) {
        if (clusteringA == null || clusteringB == null) {
            throw new IllegalArgumentException("Clusterings must not be null");
        }
        if (!clusteringA.keySet().equals(clusteringB.keySet())) {
            throw new IllegalArgumentException(
                "Clusterings must cover the same set of nodes");
        }

        int n = clusteringA.size();
        if (n <= 1) return 0.0;

        // Build contingency table
        Map<Integer, Set<String>> clustersA = invertClustering(clusteringA);
        Map<Integer, Set<String>> clustersB = invertClustering(clusteringB);

        List<Integer> aIds = new ArrayList<Integer>(clustersA.keySet());
        List<Integer> bIds = new ArrayList<Integer>(clustersB.keySet());

        int[] aSums = new int[aIds.size()];
        int[] bSums = new int[bIds.size()];

        List<List<Integer>> contingency = new ArrayList<List<Integer>>();
        for (int i = 0; i < aIds.size(); i++) {
            List<Integer> row = new ArrayList<Integer>();
            Set<String> cA = clustersA.get(aIds.get(i));
            for (int j = 0; j < bIds.size(); j++) {
                Set<String> cB = clustersB.get(bIds.get(j));
                Set<String> inter = new HashSet<String>(cA);
                inter.retainAll(cB);
                int nij = inter.size();
                row.add(nij);
                aSums[i] += nij;
                bSums[j] += nij;
            }
            contingency.add(row);
        }

        // Compute index using nCr(nij, 2) sums
        long sumNij2 = 0;
        for (List<Integer> row : contingency) {
            for (int nij : row) {
                sumNij2 += choose2(nij);
            }
        }
        long sumA2 = 0;
        for (int ai : aSums) sumA2 += choose2(ai);
        long sumB2 = 0;
        for (int bi : bSums) sumB2 += choose2(bi);

        long totalC2 = choose2(n);
        double expected = totalC2 > 0 ? (double) sumA2 * sumB2 / totalC2 : 0;
        double maxIndex = ((double) sumA2 + sumB2) / 2.0;
        double denom = maxIndex - expected;

        return denom != 0 ? (sumNij2 - expected) / denom : 0.0;
    }

    // ── Private Helpers ─────────────────────────────────────────

    private double computeModularity(
            Map<String, Integer> clustering,
            Map<Integer, Set<String>> clusters,
            int totalEdges) {
        if (totalEdges == 0) return 0.0;

        double m2 = 2.0 * totalEdges;
        double q = 0.0;

        for (Set<String> members : clusters.values()) {
            int clusterIntra = 0;
            int clusterDegreeSum = 0;

            for (String node : members) {
                Collection<edge> incidents = graph.getIncidentEdges(node);
                if (incidents == null) continue;
                clusterDegreeSum += incidents.size();
                for (edge e : incidents) {
                    String neighbor = getOtherEnd(e, node);
                    if (members.contains(neighbor)) {
                        clusterIntra++;
                    }
                }
            }
            clusterIntra /= 2;

            q += ((double) clusterIntra / totalEdges)
               - Math.pow((double) clusterDegreeSum / m2, 2);
        }
        return q;
    }

    private double computeSizeBalance(Map<Integer, Integer> sizes, int totalNodes) {
        if (sizes.size() <= 1 || totalNodes == 0) return sizes.isEmpty() ? 0.0 : 1.0;

        double maxEntropy = log2(sizes.size());
        if (maxEntropy == 0) return 1.0;

        double entropy = 0.0;
        for (int size : sizes.values()) {
            double p = (double) size / totalNodes;
            if (p > 0) entropy -= p * log2(p);
        }
        return entropy / maxEntropy;
    }

    private Map<Integer, Set<String>> invertClustering(Map<String, Integer> clustering) {
        Map<Integer, Set<String>> result = new LinkedHashMap<Integer, Set<String>>();
        for (Map.Entry<String, Integer> entry : clustering.entrySet()) {
            int cid = entry.getValue();
            if (!result.containsKey(cid)) {
                result.put(cid, new LinkedHashSet<String>());
            }
            result.get(cid).add(entry.getKey());
        }
        return result;
    }

    private String getOtherEnd(edge e, String node) {
        return e.getVertex1().equals(node) ? e.getVertex2() : e.getVertex1();
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2.0);
    }

    private static long choose2(int n) {
        return n > 1 ? (long) n * (n - 1) / 2 : 0;
    }
}
