package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * K-Core Decomposition — identifies dense subgraphs by iteratively peeling
 * low-degree vertices. Each vertex receives a <em>coreness</em> value equal to
 * the highest-order core it belongs to:
 *
 * <blockquote>
 * A <b>k-core</b> is a maximal subgraph in which every vertex has degree ≥ k
 * within that subgraph.
 * </blockquote>
 *
 * <h3>Algorithm (Batagelj–Zaversnik, O(V + E))</h3>
 * <ol>
 *   <li>Compute the degree of every vertex.</li>
 *   <li>Repeatedly remove the vertex with the smallest remaining degree, recording
 *       its coreness as that degree value. When a vertex is removed, decrease the
 *       effective degree of its neighbours (but never below their already-assigned
 *       coreness).</li>
 * </ol>
 *
 * <p>The result is a mapping from vertex → coreness, plus derived analytics:</p>
 * <ul>
 *   <li><b>Degeneracy</b> — the maximum coreness value (also the graph's
 *       degeneracy number).</li>
 *   <li><b>Core shells</b> — grouping of vertices by their coreness value.</li>
 *   <li><b>Core density profile</b> — edge density within each k-core.</li>
 *   <li><b>Cohesion score</b> — a 0–100 measure of how "core-heavy" the graph is
 *       (what fraction of vertices are in the innermost core).</li>
 * </ul>
 *
 * <p>K-core decomposition is widely used in social network analysis to find
 * tightly-knit groups, in bioinformatics for protein interaction networks,
 * and in network visualisation for layout simplification.</p>
 *
 * @author zalenix
 */
public class KCoreDecomposition {

    private final Graph<String, Edge> graph;
    private Map<String, Integer> coreness;
    private int degeneracy;
    private boolean computed;

    /**
     * Creates a new KCoreDecomposition for the given graph.
     *
     * @param graph the JUNG graph to decompose
     * @throws IllegalArgumentException if graph is null
     */
    public KCoreDecomposition(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.coreness = new LinkedHashMap<String, Integer>();
        this.degeneracy = 0;
        this.computed = false;
    }

    // ── Core decomposition (Batagelj–Zaversnik) ────────────────────

    /**
     * Runs the decomposition. Idempotent — repeated calls are no-ops.
     *
     * @return this analyzer for chaining
     */
    public KCoreDecomposition compute() {
        if (computed) return this;

        Collection<String> vertices = graph.getVertices();
        int n = vertices.size();

        if (n == 0) {
            computed = true;
            return this;
        }

        // 1. Compute initial degrees
        Map<String, Integer> degree = new HashMap<String, Integer>();
        int maxDeg = 0;
        for (String v : vertices) {
            int d = graph.degree(v);
            degree.put(v, d);
            if (d > maxDeg) maxDeg = d;
        }

        // 2. Bucket-sort vertices by degree (bin-sort for O(V+E) overall)
        @SuppressWarnings("unchecked")
        List<String>[] bins = new ArrayList[maxDeg + 1];
        for (int i = 0; i <= maxDeg; i++) {
            bins[i] = new ArrayList<String>();
        }
        for (String v : vertices) {
            bins[degree.get(v)].add(v);
        }

        // Position of each vertex in its bin (for O(1) re-bucketing)
        Map<String, Integer> pos = new HashMap<String, Integer>();
        Map<String, Integer> binStart = new HashMap<String, Integer>();

        // Flatten bins into a single ordered array
        String[] order = new String[n];
        int idx = 0;
        for (int d = 0; d <= maxDeg; d++) {
            for (String v : bins[d]) {
                order[idx] = v;
                pos.put(v, idx);
                idx++;
            }
        }

        // Record the start index of each degree's bin
        int[] binOfDeg = new int[maxDeg + 2];
        int cur = 0;
        for (int d = 0; d <= maxDeg; d++) {
            binOfDeg[d] = cur;
            cur += bins[d].size();
        }
        binOfDeg[maxDeg + 1] = n;

        // 3. Process vertices in order of increasing degree
        Map<String, Integer> core = new HashMap<String, Integer>();
        Set<String> processed = new HashSet<String>();
        int maxCore = 0;

        for (int i = 0; i < n; i++) {
            String v = order[i];
            int dv = degree.get(v);
            core.put(v, dv);
            if (dv > maxCore) maxCore = dv;
            processed.add(v);

            // For each unprocessed neighbour, decrease effective degree
            for (String u : graph.getNeighbors(v)) {
                if (processed.contains(u)) continue;
                int du = degree.get(u);
                if (du > dv) {
                    // Move u from bin[du] to bin[du-1]
                    int posU = pos.get(u);
                    int startOfBin = binOfDeg[du];
                    String w = order[startOfBin]; // first vertex in bin[du]

                    // Swap u and w in the order array
                    order[posU] = w;
                    order[startOfBin] = u;
                    pos.put(w, posU);
                    pos.put(u, startOfBin);

                    binOfDeg[du]++;
                    degree.put(u, du - 1);
                }
            }
        }

        this.coreness = core;
        this.degeneracy = maxCore;
        this.computed = true;
        return this;
    }

    // ── Accessors ──────────────────────────────────────────────────

    /**
     * Returns the coreness value for every vertex.
     * Vertex v has coreness k if it belongs to the k-core but not the (k+1)-core.
     *
     * @return unmodifiable map: vertex → coreness
     */
    public Map<String, Integer> getCoreness() {
        ensureComputed();
        return Collections.unmodifiableMap(coreness);
    }

    /**
     * Returns the coreness of a specific vertex.
     *
     * @param vertex vertex ID
     * @return coreness value, or -1 if vertex not in graph
     */
    public int getCoreness(String vertex) {
        ensureComputed();
        Integer c = coreness.get(vertex);
        return c != null ? c : -1;
    }

    /**
     * Returns the degeneracy (maximum coreness) of the graph.
     * Equals the highest k for which a non-empty k-core exists.
     *
     * @return degeneracy value (0 for empty graphs)
     */
    public int getDegeneracy() {
        ensureComputed();
        return degeneracy;
    }

    // ── Core shells ────────────────────────────────────────────────

    /**
     * Groups vertices into "shells" by coreness value.
     * Shell k contains all vertices with coreness exactly k.
     *
     * @return sorted map: coreness → list of vertex IDs
     */
    public SortedMap<Integer, List<String>> getCoreShells() {
        ensureComputed();
        SortedMap<Integer, List<String>> shells = new TreeMap<Integer, List<String>>();
        for (Map.Entry<String, Integer> entry : coreness.entrySet()) {
            int k = entry.getValue();
            if (!shells.containsKey(k)) {
                shells.put(k, new ArrayList<String>());
            }
            shells.get(k).add(entry.getKey());
        }
        // Sort vertex IDs within each shell for deterministic output
        for (List<String> shell : shells.values()) {
            Collections.sort(shell);
        }
        return shells;
    }

    /**
     * Returns all vertices that belong to the k-core (coreness ≥ k).
     *
     * @param k the core order
     * @return sorted list of vertex IDs in the k-core
     * @throws IllegalArgumentException if k is negative
     */
    public List<String> getKCore(int k) {
        if (k < 0) {
            throw new IllegalArgumentException("k must be non-negative, got " + k);
        }
        ensureComputed();
        List<String> result = new ArrayList<String>();
        for (Map.Entry<String, Integer> entry : coreness.entrySet()) {
            if (entry.getValue() >= k) {
                result.add(entry.getKey());
            }
        }
        Collections.sort(result);
        return result;
    }

    /**
     * Returns the number of distinct core levels (non-empty shells).
     *
     * @return number of distinct coreness values
     */
    public int getNumberOfShells() {
        return getCoreShells().size();
    }

    // ── Core density profile ───────────────────────────────────────

    /**
     * Represents density statistics for a single k-core.
     */
    public static class CoreDensity {
        private final int k;
        private final int vertices;
        private final int edges;
        private final double density;

        public CoreDensity(int k, int vertices, int edges, double density) {
            this.k = k;
            this.vertices = vertices;
            this.edges = edges;
            this.density = density;
        }

        /** Core order k. */
        public int getK() { return k; }

        /** Number of vertices in this k-core. */
        public int getVertices() { return vertices; }

        /** Number of edges within this k-core. */
        public int getEdges() { return edges; }

        /**
         * Edge density: 2E / (V*(V-1)) for undirected graphs.
         * 1.0 = complete subgraph (clique), 0.0 = no edges.
         */
        public double getDensity() { return density; }

        @Override
        public String toString() {
            return String.format("%d-core: %d vertices, %d edges, density=%.4f",
                    k, vertices, edges, density);
        }
    }

    /**
     * Computes edge density for each k-core (from k=0 to degeneracy).
     * The density should increase as k grows, since higher cores are
     * progressively denser subgraphs.
     *
     * @return list of CoreDensity objects, sorted by k ascending
     */
    public List<CoreDensity> getCoreDensityProfile() {
        ensureComputed();
        List<CoreDensity> profile = new ArrayList<CoreDensity>();

        for (int k = 0; k <= degeneracy; k++) {
            Set<String> coreVertices = new HashSet<String>();
            for (Map.Entry<String, Integer> entry : coreness.entrySet()) {
                if (entry.getValue() >= k) {
                    coreVertices.add(entry.getKey());
                }
            }

            if (coreVertices.isEmpty()) continue;

            // Count edges within the k-core
            int edgeCount = 0;
            for ((Edge e : graph.getEdges()) {
                String v1 = graph.getEndpoints(e).getFirst();
                String v2 = graph.getEndpoints(e).getSecond();
                if (coreVertices.contains(v1) && coreVertices.contains(v2)) {
                    edgeCount++;
                }
            }

            int v = coreVertices.size();
            double density = v > 1 ? (2.0 * edgeCount) / (v * (v - 1)) : 0.0;
            profile.add(new CoreDensity(k, v, edgeCount, density));
        }

        return profile;
    }

    // ── Analytics ──────────────────────────────────────────────────

    /**
     * Cohesion score: measures how "core-heavy" the graph is.
     * Computed as the fraction of vertices in the innermost core (degeneracy shell)
     * relative to total vertices, scaled to 0–100.
     *
     * <ul>
     *   <li>100 = all vertices have the same (maximum) coreness (complete graph)</li>
     *   <li>0 = no vertices (empty graph)</li>
     *   <li>Higher values indicate a single dense core; lower values indicate
     *       a thin core with many peripheral vertices.</li>
     * </ul>
     *
     * @return cohesion score in [0, 100]
     */
    public double getCohesionScore() {
        ensureComputed();
        int n = coreness.size();
        if (n == 0) return 0.0;

        int innermostCount = 0;
        for (int c : coreness.values()) {
            if (c == degeneracy) innermostCount++;
        }

        return (100.0 * innermostCount) / n;
    }

    /**
     * Computes the average coreness across all vertices.
     *
     * @return mean coreness value (0.0 for empty graphs)
     */
    public double getAverageCoreness() {
        ensureComputed();
        if (coreness.isEmpty()) return 0.0;

        long sum = 0;
        for (int c : coreness.values()) {
            sum += c;
        }
        return (double) sum / coreness.size();
    }

    /**
     * Returns the distribution of vertices across coreness values.
     *
     * @return sorted map: coreness → count of vertices with that coreness
     */
    public SortedMap<Integer, Integer> getCorenessDistribution() {
        ensureComputed();
        SortedMap<Integer, Integer> dist = new TreeMap<Integer, Integer>();
        for (int c : coreness.values()) {
            dist.put(c, dist.containsKey(c) ? dist.get(c) + 1 : 1);
        }
        return dist;
    }

    /**
     * Classifies the graph's core structure into human-readable categories.
     *
     * @return structure classification string
     */
    public String classifyCoreStructure() {
        ensureComputed();
        int n = coreness.size();
        if (n == 0) return "Empty";
        if (degeneracy == 0) return "Disconnected (all isolated vertices)";
        if (degeneracy == 1) return "Tree-like (no dense subgraphs)";

        double cohesion = getCohesionScore();
        int shells = getNumberOfShells();

        if (cohesion > 80) return "Highly cohesive (single dense core)";
        if (cohesion > 50) return "Moderate cohesion (prominent core)";
        if (shells >= 5) return "Layered structure (multiple core shells)";
        if (degeneracy >= 4) return "Core-periphery (dense core, sparse periphery)";
        return "Sparse with small dense pockets";
    }

    // ── Result object ──────────────────────────────────────────────

    /**
     * Comprehensive result of the k-core decomposition.
     */
    public static class KCoreResult {
        private final int vertexCount;
        private final int edgeCount;
        private final int degeneracy;
        private final int numberOfShells;
        private final double cohesionScore;
        private final double averageCoreness;
        private final String structureClassification;
        private final Map<String, Integer> coreness;
        private final SortedMap<Integer, List<String>> shells;
        private final SortedMap<Integer, Integer> distribution;
        private final List<CoreDensity> densityProfile;

        public KCoreResult(int vertexCount, int edgeCount, int degeneracy,
                           int numberOfShells, double cohesionScore,
                           double averageCoreness, String structureClassification,
                           Map<String, Integer> coreness,
                           SortedMap<Integer, List<String>> shells,
                           SortedMap<Integer, Integer> distribution,
                           List<CoreDensity> densityProfile) {
            this.vertexCount = vertexCount;
            this.edgeCount = edgeCount;
            this.degeneracy = degeneracy;
            this.numberOfShells = numberOfShells;
            this.cohesionScore = cohesionScore;
            this.averageCoreness = averageCoreness;
            this.structureClassification = structureClassification;
            this.coreness = coreness;
            this.shells = shells;
            this.distribution = distribution;
            this.densityProfile = densityProfile;
        }

        public int getVertexCount() { return vertexCount; }
        public int getEdgeCount() { return edgeCount; }
        public int getDegeneracy() { return degeneracy; }
        public int getNumberOfShells() { return numberOfShells; }
        public double getCohesionScore() { return cohesionScore; }
        public double getAverageCoreness() { return averageCoreness; }
        public String getStructureClassification() { return structureClassification; }
        public Map<String, Integer> getCoreness() { return coreness; }
        public SortedMap<Integer, List<String>> getShells() { return shells; }
        public SortedMap<Integer, Integer> getDistribution() { return distribution; }
        public List<CoreDensity> getDensityProfile() { return densityProfile; }
    }

    /**
     * Returns a comprehensive result object with all decomposition analytics.
     *
     * @return KCoreResult with all metrics
     */
    public KCoreResult getResult() {
        ensureComputed();
        return new KCoreResult(
                graph.getVertexCount(),
                graph.getEdgeCount(),
                degeneracy,
                getNumberOfShells(),
                getCohesionScore(),
                getAverageCoreness(),
                classifyCoreStructure(),
                new LinkedHashMap<String, Integer>(coreness),
                getCoreShells(),
                getCorenessDistribution(),
                getCoreDensityProfile()
        );
    }

    // ── Summary ────────────────────────────────────────────────────

    /**
     * Returns a formatted multi-line summary of the decomposition.
     *
     * @return human-readable summary string
     */
    public String getSummary() {
        ensureComputed();
        StringBuilder sb = new StringBuilder();
        sb.append("=== K-Core Decomposition ===\n");
        sb.append(String.format("Vertices: %d | Edges: %d\n",
                graph.getVertexCount(), graph.getEdgeCount()));
        sb.append(String.format("Degeneracy: %d\n", degeneracy));
        sb.append(String.format("Number of shells: %d\n", getNumberOfShells()));
        sb.append(String.format("Average coreness: %.2f\n", getAverageCoreness()));
        sb.append(String.format("Cohesion score: %.1f / 100\n", getCohesionScore()));
        sb.append(String.format("Structure: %s\n", classifyCoreStructure()));

        sb.append("\n--- Shell distribution ---\n");
        SortedMap<Integer, Integer> dist = getCorenessDistribution();
        for (Map.Entry<Integer, Integer> entry : dist.entrySet()) {
            sb.append(String.format("  %d-shell: %d vertices\n",
                    entry.getKey(), entry.getValue()));
        }

        sb.append("\n--- Core density profile ---\n");
        for (CoreDensity cd : getCoreDensityProfile()) {
            sb.append(String.format("  %s\n", cd.toString()));
        }

        return sb.toString();
    }

    // ── Internals ──────────────────────────────────────────────────

    private void ensureComputed() {
        if (!computed) compute();
    }
}
