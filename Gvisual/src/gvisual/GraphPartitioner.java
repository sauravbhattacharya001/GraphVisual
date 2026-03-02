package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Partitions a JUNG graph into k balanced parts while minimizing edge cuts.
 * Implements multiple partitioning strategies:
 *
 * <ul>
 *   <li><b>BFS-based</b> — grows partitions from seed vertices using breadth-first
 *       traversal, balancing partition sizes at each step.</li>
 *   <li><b>Kernighan-Lin refinement</b> — iteratively swaps vertex pairs between
 *       partitions to reduce edge cuts (2-way, applied recursively for k-way).</li>
 *   <li><b>Spectral bisection</b> — uses the Fiedler vector (second-smallest eigenvector
 *       of the Laplacian) to find a natural split, refined with KL swaps.</li>
 * </ul>
 *
 * <p>Produces a {@link PartitionResult} with per-partition membership, edge cut count,
 * imbalance ratio, cut ratio, and partition-level metrics (size, internal/external edges,
 * conductance).</p>
 *
 * <p>Usage:</p>
 * <pre>
 *   GraphPartitioner partitioner = new GraphPartitioner(graph);
 *   PartitionResult result = partitioner.partition(3, Strategy.BFS);
 *   System.out.println("Edge cuts: " + result.getEdgeCuts());
 *   System.out.println("Imbalance: " + result.getImbalanceRatio());
 * </pre>
 *
 * @author zalenix
 */
public class GraphPartitioner {

    /** Partitioning strategy. */
    public enum Strategy {
        /** BFS-based growth from seed vertices. */
        BFS,
        /** Kernighan-Lin swap refinement (recursive bisection for k > 2). */
        KERNIGHAN_LIN,
        /** Spectral bisection using Fiedler vector + KL refinement. */
        SPECTRAL
    }

    private final Graph<String, edge> graph;

    /**
     * Creates a new GraphPartitioner for the given graph.
     *
     * @param graph the JUNG graph to partition
     * @throws IllegalArgumentException if graph is null
     */
    public GraphPartitioner(Graph<String, edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
    }

    /**
     * Partition the graph into k parts using the specified strategy.
     *
     * @param k number of partitions (must be &ge; 1 and &le; vertex count)
     * @param strategy the partitioning strategy to use
     * @return a PartitionResult with assignments and metrics
     * @throws IllegalArgumentException if k is invalid
     */
    public PartitionResult partition(int k, Strategy strategy) {
        int n = graph.getVertexCount();
        if (k < 1) {
            throw new IllegalArgumentException("k must be >= 1, got " + k);
        }
        if (n == 0) {
            return new PartitionResult(new HashMap<>(), k, graph);
        }
        if (k > n) {
            throw new IllegalArgumentException(
                "k (" + k + ") exceeds vertex count (" + n + ")");
        }
        if (k == 1) {
            Map<String, Integer> assignment = new HashMap<>();
            for (String v : graph.getVertices()) {
                assignment.put(v, 0);
            }
            return new PartitionResult(assignment, k, graph);
        }

        Map<String, Integer> assignment;
        switch (strategy) {
            case BFS:
                assignment = partitionBFS(k);
                break;
            case KERNIGHAN_LIN:
                assignment = partitionKL(k);
                break;
            case SPECTRAL:
                assignment = partitionSpectral(k);
                break;
            default:
                throw new IllegalArgumentException("Unknown strategy: " + strategy);
        }

        return new PartitionResult(assignment, k, graph);
    }

    /**
     * Partition using default strategy (BFS).
     *
     * @param k number of partitions
     * @return a PartitionResult
     */
    public PartitionResult partition(int k) {
        return partition(k, Strategy.BFS);
    }

    // -------------------------------------------------------------------------
    // BFS-based partitioning
    // -------------------------------------------------------------------------

    private Map<String, Integer> partitionBFS(int k) {
        Map<String, Integer> assignment = new HashMap<>();
        int n = graph.getVertexCount();
        int targetSize = (n + k - 1) / k; // ceil(n / k)

        // Pick seed vertices spread apart using BFS farthest-first
        List<String> seeds = selectSeeds(k);

        // Initialize BFS queues for each partition
        int[] partitionSizes = new int[k];
        @SuppressWarnings("unchecked")
        Queue<String>[] queues = new LinkedList[k];
        for (int i = 0; i < k; i++) {
            queues[i] = new LinkedList<>();
            if (i < seeds.size()) {
                queues[i].add(seeds.get(i));
                assignment.put(seeds.get(i), i);
                partitionSizes[i] = 1;
            }
        }

        // Round-robin BFS expansion
        boolean progress = true;
        while (progress) {
            progress = false;
            for (int i = 0; i < k; i++) {
                if (queues[i].isEmpty() || partitionSizes[i] >= targetSize) {
                    continue;
                }
                // Try to expand one vertex from this partition's queue
                while (!queues[i].isEmpty()) {
                    String current = queues[i].poll();
                    for (String neighbor : graph.getNeighbors(current)) {
                        if (!assignment.containsKey(neighbor) && partitionSizes[i] < targetSize) {
                            assignment.put(neighbor, i);
                            partitionSizes[i]++;
                            queues[i].add(neighbor);
                            progress = true;
                        }
                    }
                    if (progress) break;
                }
            }
        }

        // Assign any remaining unassigned vertices to smallest partition
        for (String v : graph.getVertices()) {
            if (!assignment.containsKey(v)) {
                int smallest = 0;
                for (int i = 1; i < k; i++) {
                    if (partitionSizes[i] < partitionSizes[smallest]) {
                        smallest = i;
                    }
                }
                assignment.put(v, smallest);
                partitionSizes[smallest]++;
            }
        }

        return assignment;
    }

    /**
     * Select k seed vertices spread apart using farthest-first traversal.
     */
    private List<String> selectSeeds(int k) {
        List<String> seeds = new ArrayList<>();
        Set<String> vertices = new HashSet<>(graph.getVertices());
        if (vertices.isEmpty()) return seeds;

        // Start from an arbitrary vertex
        String first = vertices.iterator().next();
        seeds.add(first);

        while (seeds.size() < k && seeds.size() < vertices.size()) {
            // Find the vertex farthest from all current seeds
            String farthest = null;
            int maxDist = -1;

            Map<String, Integer> distances = bfsDistances(seeds);
            for (String v : vertices) {
                if (!seeds.contains(v)) {
                    int dist = distances.getOrDefault(v, Integer.MAX_VALUE);
                    if (dist > maxDist) {
                        maxDist = dist;
                        farthest = v;
                    }
                }
            }
            if (farthest != null) {
                seeds.add(farthest);
            } else {
                break;
            }
        }
        return seeds;
    }

    /**
     * BFS from multiple sources, returning shortest distance to any source.
     */
    private Map<String, Integer> bfsDistances(List<String> sources) {
        Map<String, Integer> dist = new HashMap<>();
        Queue<String> queue = new LinkedList<>();
        for (String s : sources) {
            dist.put(s, 0);
            queue.add(s);
        }
        while (!queue.isEmpty()) {
            String v = queue.poll();
            int d = dist.get(v);
            for (String n : graph.getNeighbors(v)) {
                if (!dist.containsKey(n)) {
                    dist.put(n, d + 1);
                    queue.add(n);
                }
            }
        }
        return dist;
    }

    // -------------------------------------------------------------------------
    // Kernighan-Lin refinement (recursive bisection)
    // -------------------------------------------------------------------------

    private Map<String, Integer> partitionKL(int k) {
        // Start with BFS partition, then refine with KL swaps
        Map<String, Integer> assignment = partitionBFS(k);
        return refineKL(assignment, k);
    }

    /**
     * Kernighan-Lin refinement: for each pair of partitions, try swapping
     * vertices to reduce edge cuts. Repeat until no improvement.
     */
    private Map<String, Integer> refineKL(Map<String, Integer> assignment, int k) {
        boolean improved = true;
        int maxIterations = 50;
        int iteration = 0;

        while (improved && iteration < maxIterations) {
            improved = false;
            iteration++;

            for (int a = 0; a < k; a++) {
                for (int b = a + 1; b < k; b++) {
                    if (refineKLPair(assignment, a, b)) {
                        improved = true;
                    }
                }
            }
        }
        return assignment;
    }

    /**
     * Try KL swaps between two partitions. Returns true if any swap was made.
     */
    private boolean refineKLPair(Map<String, Integer> assignment, int partA, int partB) {
        List<String> nodesA = new ArrayList<>();
        List<String> nodesB = new ArrayList<>();

        for (Map.Entry<String, Integer> e : assignment.entrySet()) {
            if (e.getValue() == partA) nodesA.add(e.getKey());
            else if (e.getValue() == partB) nodesB.add(e.getKey());
        }

        if (nodesA.isEmpty() || nodesB.isEmpty()) return false;

        boolean anyImproved = false;
        Set<String> locked = new HashSet<>();
        int passes = Math.min(nodesA.size(), nodesB.size());

        for (int pass = 0; pass < passes; pass++) {
            String bestU = null, bestV = null;
            int bestGain = 0;

            for (String u : nodesA) {
                if (locked.contains(u)) continue;
                for (String v : nodesB) {
                    if (locked.contains(v)) continue;
                    int gain = computeSwapGain(assignment, u, v, partA, partB);
                    if (gain > bestGain) {
                        bestGain = gain;
                        bestU = u;
                        bestV = v;
                    }
                }
            }

            if (bestGain > 0) {
                assignment.put(bestU, partB);
                assignment.put(bestV, partA);
                locked.add(bestU);
                locked.add(bestV);
                nodesA.remove(bestU);
                nodesA.add(bestV);
                nodesB.remove(bestV);
                nodesB.add(bestU);
                anyImproved = true;
            } else {
                break;
            }
        }

        return anyImproved;
    }

    /**
     * Compute the gain from swapping u (in partA) with v (in partB).
     * Gain = reduction in edge cuts after the swap.
     */
    private int computeSwapGain(Map<String, Integer> assignment, String u, String v,
                                int partA, int partB) {
        int cutsBefore = 0;
        int cutsAfter = 0;

        // Count u's external edges before/after swap
        for (String n : graph.getNeighbors(u)) {
            int nPart = assignment.getOrDefault(n, -1);
            if (n.equals(v)) {
                // u and v are neighbors — connected regardless of swap
                cutsBefore++; // currently in different partitions
                cutsAfter++;  // still in different partitions after swap
                continue;
            }
            if (nPart != partA) cutsBefore++;
            if (nPart != partB) cutsAfter++;
        }

        // Count v's external edges before/after swap
        for (String n : graph.getNeighbors(v)) {
            if (n.equals(u)) continue; // already counted
            int nPart = assignment.getOrDefault(n, -1);
            if (nPart != partB) cutsBefore++;
            if (nPart != partA) cutsAfter++;
        }

        return cutsBefore - cutsAfter;
    }

    // -------------------------------------------------------------------------
    // Spectral bisection (recursive for k-way)
    // -------------------------------------------------------------------------

    private Map<String, Integer> partitionSpectral(int k) {
        List<String> vertices = new ArrayList<>(graph.getVertices());
        Map<String, Integer> assignment = new HashMap<>();

        // Recursive bisection
        List<List<String>> partitions = new ArrayList<>();
        partitions.add(vertices);

        while (partitions.size() < k) {
            // Find the largest partition to bisect
            int largest = 0;
            for (int i = 1; i < partitions.size(); i++) {
                if (partitions.get(i).size() > partitions.get(largest).size()) {
                    largest = i;
                }
            }

            List<String> toSplit = partitions.remove(largest);
            if (toSplit.size() <= 1) {
                partitions.add(toSplit);
                break;
            }

            List<List<String>> halves = spectralBisect(toSplit);
            partitions.addAll(halves);
        }

        // Assign partition IDs
        for (int i = 0; i < partitions.size(); i++) {
            for (String v : partitions.get(i)) {
                assignment.put(v, i);
            }
        }

        // Refine with KL
        return refineKL(assignment, partitions.size());
    }

    /**
     * Spectral bisection of a vertex subset using the Fiedler vector.
     */
    private List<List<String>> spectralBisect(List<String> vertices) {
        int n = vertices.size();
        if (n <= 1) {
            List<List<String>> result = new ArrayList<>();
            result.add(new ArrayList<>(vertices));
            return result;
        }

        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) {
            index.put(vertices.get(i), i);
        }

        // Build Laplacian matrix for the subgraph
        double[][] laplacian = new double[n][n];
        for (int i = 0; i < n; i++) {
            String v = vertices.get(i);
            int degree = 0;
            for (String neighbor : graph.getNeighbors(v)) {
                Integer j = index.get(neighbor);
                if (j != null) {
                    laplacian[i][j] = -1.0;
                    degree++;
                }
            }
            laplacian[i][i] = degree;
        }

        // Compute Fiedler vector (eigenvector of 2nd smallest eigenvalue)
        double[] fiedler = computeFiedlerVector(laplacian, n);

        // Partition by sign of Fiedler vector
        List<String> partA = new ArrayList<>();
        List<String> partB = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (fiedler[i] <= 0) {
                partA.add(vertices.get(i));
            } else {
                partB.add(vertices.get(i));
            }
        }

        // Ensure neither partition is empty
        if (partA.isEmpty()) {
            partA.add(partB.remove(partB.size() - 1));
        } else if (partB.isEmpty()) {
            partB.add(partA.remove(partA.size() - 1));
        }

        List<List<String>> result = new ArrayList<>();
        result.add(partA);
        result.add(partB);
        return result;
    }

    /**
     * Compute the Fiedler vector using power iteration on (maxEig*I - L)
     * to find the second-smallest eigenvector of the Laplacian.
     */
    private double[] computeFiedlerVector(double[][] laplacian, int n) {
        if (n <= 2) {
            double[] v = new double[n];
            if (n == 2) { v[0] = -1; v[1] = 1; }
            else { v[0] = 1; }
            return v;
        }

        // Estimate max eigenvalue (Gershgorin bound)
        double maxEig = 0;
        for (int i = 0; i < n; i++) {
            double sum = 0;
            for (int j = 0; j < n; j++) {
                sum += Math.abs(laplacian[i][j]);
            }
            maxEig = Math.max(maxEig, sum);
        }

        // Shifted matrix M = maxEig*I - L  (largest eigvec of M = smallest of L)
        double[][] M = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                M[i][j] = -laplacian[i][j];
            }
            M[i][i] += maxEig;
        }

        // Power iteration for dominant eigenvector of M (= smallest eigvec of L)
        double[] v1 = powerIteration(M, n);

        // Deflate: M' = M - lambda1 * v1 * v1^T
        double lambda1 = rayleighQuotient(M, v1, n);
        double[][] M2 = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                M2[i][j] = M[i][j] - lambda1 * v1[i] * v1[j];
            }
        }

        // Power iteration for second eigenvector of M (= Fiedler vector)
        return powerIteration(M2, n);
    }

    private double[] powerIteration(double[][] M, int n) {
        Random rng = new Random(42);
        double[] v = new double[n];
        for (int i = 0; i < n; i++) {
            v[i] = rng.nextDouble() - 0.5;
        }
        normalize(v, n);

        for (int iter = 0; iter < 200; iter++) {
            double[] Mv = matVecMul(M, v, n);
            normalize(Mv, n);
            v = Mv;
        }
        return v;
    }

    private double rayleighQuotient(double[][] M, double[] v, int n) {
        double[] Mv = matVecMul(M, v, n);
        double num = 0, den = 0;
        for (int i = 0; i < n; i++) {
            num += v[i] * Mv[i];
            den += v[i] * v[i];
        }
        return den > 0 ? num / den : 0;
    }

    private double[] matVecMul(double[][] M, double[] v, int n) {
        double[] result = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0;
            for (int j = 0; j < n; j++) {
                sum += M[i][j] * v[j];
            }
            result[i] = sum;
        }
        return result;
    }

    private void normalize(double[] v, int n) {
        double norm = 0;
        for (int i = 0; i < n; i++) {
            norm += v[i] * v[i];
        }
        norm = Math.sqrt(norm);
        if (norm > 1e-15) {
            for (int i = 0; i < n; i++) {
                v[i] /= norm;
            }
        }
    }

    // =========================================================================
    // PartitionResult
    // =========================================================================

    /**
     * Result of a graph partitioning operation.
     */
    public static class PartitionResult {
        private final Map<String, Integer> assignment;
        private final int k;
        private final int edgeCuts;
        private final double imbalanceRatio;
        private final double cutRatio;
        private final List<PartitionInfo> partitions;

        PartitionResult(Map<String, Integer> assignment, int k, Graph<String, edge> graph) {
            this.assignment = Collections.unmodifiableMap(new HashMap<>(assignment));
            this.k = k;

            // Compute partition infos
            Map<Integer, Set<String>> partMap = new HashMap<>();
            for (int i = 0; i < k; i++) {
                partMap.put(i, new HashSet<>());
            }
            for (Map.Entry<String, Integer> e : assignment.entrySet()) {
                partMap.computeIfAbsent(e.getValue(), x -> new HashSet<>()).add(e.getKey());
            }

            // Count edge cuts and per-partition metrics
            int cuts = 0;
            Map<Integer, Integer> internalEdges = new HashMap<>();
            Map<Integer, Integer> externalEdges = new HashMap<>();

            for (edge e : graph.getEdges()) {
                Collection<String> endpoints = graph.getEndpoints(e);
                if (endpoints.size() < 2) continue;
                Iterator<String> it = endpoints.iterator();
                String u = it.next();
                String v = it.next();
                int pu = assignment.getOrDefault(u, 0);
                int pv = assignment.getOrDefault(v, 0);
                if (pu != pv) {
                    cuts++;
                    externalEdges.merge(pu, 1, Integer::sum);
                    externalEdges.merge(pv, 1, Integer::sum);
                } else {
                    internalEdges.merge(pu, 1, Integer::sum);
                }
            }
            this.edgeCuts = cuts;

            // Compute imbalance
            int n = assignment.size();
            double idealSize = n > 0 ? (double) n / k : 0;
            int maxSize = 0;
            for (Set<String> part : partMap.values()) {
                maxSize = Math.max(maxSize, part.size());
            }
            this.imbalanceRatio = idealSize > 0 ? maxSize / idealSize : 1.0;

            // Cut ratio: fraction of edges that are cut
            int totalEdges = graph.getEdgeCount();
            this.cutRatio = totalEdges > 0 ? (double) cuts / totalEdges : 0.0;

            // Build partition info list
            this.partitions = new ArrayList<>();
            for (int i = 0; i < k; i++) {
                Set<String> members = partMap.getOrDefault(i, Collections.emptySet());
                int internal = internalEdges.getOrDefault(i, 0);
                int external = externalEdges.getOrDefault(i, 0);
                double conductance = (internal + external) > 0
                    ? (double) external / (2 * internal + external)
                    : 0.0;
                this.partitions.add(new PartitionInfo(i, members.size(), internal,
                    external, conductance, new ArrayList<>(members)));
            }
        }

        /** Get the partition assignment for each vertex. */
        public Map<String, Integer> getAssignment() { return assignment; }

        /** Get the number of partitions. */
        public int getK() { return k; }

        /** Get the partition ID assigned to a vertex. */
        public int getPartition(String vertex) {
            Integer p = assignment.get(vertex);
            if (p == null) throw new IllegalArgumentException("Vertex not found: " + vertex);
            return p;
        }

        /** Get the total number of edges cut (crossing partition boundaries). */
        public int getEdgeCuts() { return edgeCuts; }

        /** Get the imbalance ratio (max partition size / ideal partition size). */
        public double getImbalanceRatio() { return imbalanceRatio; }

        /** Get the cut ratio (fraction of edges crossing partition boundaries). */
        public double getCutRatio() { return cutRatio; }

        /** Get per-partition information. */
        public List<PartitionInfo> getPartitions() {
            return Collections.unmodifiableList(partitions);
        }

        /** Get info for a specific partition. */
        public PartitionInfo getPartitionInfo(int partitionId) {
            if (partitionId < 0 || partitionId >= partitions.size()) {
                throw new IllegalArgumentException("Invalid partition ID: " + partitionId);
            }
            return partitions.get(partitionId);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("GraphPartition: k=%d, vertices=%d, edgeCuts=%d, " +
                "imbalance=%.3f, cutRatio=%.3f%n", k, assignment.size(), edgeCuts,
                imbalanceRatio, cutRatio));
            for (PartitionInfo pi : partitions) {
                sb.append(String.format("  Partition %d: size=%d, internal=%d, external=%d, " +
                    "conductance=%.3f%n", pi.getId(), pi.getSize(), pi.getInternalEdges(),
                    pi.getExternalEdges(), pi.getConductance()));
            }
            return sb.toString();
        }
    }

    /**
     * Information about a single partition.
     */
    public static class PartitionInfo {
        private final int id;
        private final int size;
        private final int internalEdges;
        private final int externalEdges;
        private final double conductance;
        private final List<String> members;

        PartitionInfo(int id, int size, int internalEdges, int externalEdges,
                     double conductance, List<String> members) {
            this.id = id;
            this.size = size;
            this.internalEdges = internalEdges;
            this.externalEdges = externalEdges;
            this.conductance = conductance;
            this.members = Collections.unmodifiableList(members);
        }

        /** Partition ID (0-indexed). */
        public int getId() { return id; }

        /** Number of vertices in this partition. */
        public int getSize() { return size; }

        /** Number of edges with both endpoints in this partition. */
        public int getInternalEdges() { return internalEdges; }

        /** Number of edges with one endpoint in this partition and one outside. */
        public int getExternalEdges() { return externalEdges; }

        /**
         * Conductance: external / (2*internal + external).
         * Lower values indicate a more cohesive partition.
         */
        public double getConductance() { return conductance; }

        /** List of vertex IDs in this partition. */
        public List<String> getMembers() { return members; }
    }
}
