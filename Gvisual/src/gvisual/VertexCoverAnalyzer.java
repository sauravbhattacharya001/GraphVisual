package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Vertex Cover Analyzer — finds and analyses vertex covers in a graph.
 *
 * <blockquote>
 * A <b>vertex cover</b> C is a subset of vertices such that every Edge
 * in E has at least one endpoint in C.
 * </blockquote>
 *
 * <h3>Features</h3>
 * <ul>
 *   <li><b>2-approximation vertex cover</b> — Edge-matching heuristic
 *       guaranteed to produce a cover at most 2× optimal. O(V + E).</li>
 *   <li><b>Greedy vertex cover</b> — degree-based greedy that iteratively
 *       picks the vertex covering the most uncovered edges. O(V·E).</li>
 *   <li><b>Exact minimum vertex cover</b> — brute-force search for small
 *       graphs (≤ 20 vertices) with early termination.</li>
 *   <li><b>Vertex cover number bounds</b> — lower bound from maximum
 *       matching (König-related) and upper bound (n − max-independent-set
 *       lower bound).</li>
 *   <li><b>Cover verification</b> — check whether a given set is a valid
 *       vertex cover.</li>
 *   <li><b>Uncovered edges</b> — find edges not covered by a given set.</li>
 *   <li><b>Per-vertex coverage info</b> — for each vertex, how many edges
 *       it covers in the solution.</li>
 *   <li><b>Weighted vertex cover</b> — greedy weighted cover minimizing
 *       total vertex weight.</li>
 *   <li><b>LP relaxation bound</b> — linear programming relaxation lower
 *       bound via greedy fractional cover.</li>
 *   <li><b>Full report</b> — consolidated analysis with all metrics.</li>
 * </ul>
 *
 * <p>Applications: network monitoring (placing sensors on links), bioinformatics
 * (enzyme selection), VLSI design, security (surveillance camera placement).</p>
 *
 * @author zalenix
 */
public class VertexCoverAnalyzer {

    private final Graph<String, Edge> graph;
    private final Map<String, Set<String>> adj;

    /**
     * Creates a new VertexCoverAnalyzer.
     *
     * @param graph the JUNG graph to analyse
     * @throws IllegalArgumentException if graph is null
     */
    public VertexCoverAnalyzer(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.adj = GraphUtils.buildAdjacencyMap(graph);
    }

    // ── 2-Approximation vertex cover ────────────────────────────────────

    /**
     * Finds a vertex cover using the classic 2-approximation algorithm.
     * Repeatedly picks an arbitrary uncovered Edge and adds both endpoints.
     * Guaranteed to be at most 2× the optimal size.
     *
     * @return an unmodifiable set of vertex IDs forming a vertex cover
     */
    public Set<String> approxVertexCover() {
        Set<String> cover = new LinkedHashSet<String>();
        Set<String> coveredEdgeKeys = new HashSet<String>();

        for (Edge e : graph.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            String key = edgeKey(v1, v2);
            if (!coveredEdgeKeys.contains(key)) {
                cover.add(v1);
                cover.add(v2);
                // Mark all edges incident to v1 and v2 as covered
                for (String n : adj.get(v1)) {
                    coveredEdgeKeys.add(edgeKey(v1, n));
                }
                for (String n : adj.get(v2)) {
                    coveredEdgeKeys.add(edgeKey(v2, n));
                }
            }
        }
        return Collections.unmodifiableSet(cover);
    }

    // ── Greedy vertex cover ─────────────────────────────────────────────

    /**
     * Finds a vertex cover using a greedy heuristic.
     * Picks the vertex covering the most uncovered edges at each step.
     *
     * @return an unmodifiable set of vertex IDs forming a vertex cover
     */
    public Set<String> greedyVertexCover() {
        Set<String> cover = new LinkedHashSet<String>();
        Set<String> coveredEdgeKeys = new HashSet<String>();
        int totalEdges = graph.getEdgeCount();

        // Track uncovered degree for each vertex
        Map<String, Integer> uncoveredDeg = new HashMap<String, Integer>();
        for (String v : adj.keySet()) {
            uncoveredDeg.put(v, adj.get(v).size());
        }

        while (coveredEdgeKeys.size() < totalEdges) {
            // Find vertex with max uncovered degree
            String best = null;
            int bestDeg = -1;
            for (Map.Entry<String, Integer> entry : uncoveredDeg.entrySet()) {
                if (!cover.contains(entry.getKey()) && entry.getValue() > bestDeg) {
                    bestDeg = entry.getValue();
                    best = entry.getKey();
                }
            }
            if (best == null) break;

            cover.add(best);
            for (String n : adj.get(best)) {
                String key = edgeKey(best, n);
                if (!coveredEdgeKeys.contains(key)) {
                    coveredEdgeKeys.add(key);
                    // Reduce uncovered degree of neighbor
                    uncoveredDeg.put(n, uncoveredDeg.get(n) - 1);
                }
            }
            uncoveredDeg.put(best, 0);
        }
        return Collections.unmodifiableSet(cover);
    }

    // ── Exact minimum vertex cover ──────────────────────────────────────

    /**
     * Finds the exact minimum vertex cover by brute force.
     * Only feasible for small graphs (≤ 20 vertices).
     *
     * @return an unmodifiable set of vertex IDs forming a minimum vertex cover
     * @throws IllegalStateException if the graph has more than 20 vertices
     */
    public Set<String> exactMinimumVertexCover() {
        List<String> vertices = new ArrayList<String>(adj.keySet());
        int n = vertices.size();
        if (n > 20) {
            throw new IllegalStateException(
                    "Exact algorithm limited to ≤ 20 vertices (have " + n + ")");
        }
        if (graph.getEdgeCount() == 0) {
            return Collections.unmodifiableSet(new LinkedHashSet<String>());
        }

        // Collect edges as index pairs
        List<int[]> edgePairs = new ArrayList<int[]>();
        Map<String, Integer> indexOf = new HashMap<String, Integer>();
        for (int i = 0; i < n; i++) {
            indexOf.put(vertices.get(i), i);
        }
        Set<String> seenEdges = new HashSet<String>();
        for (String v : vertices) {
            for (String u : adj.get(v)) {
                String key = edgeKey(v, u);
                if (!seenEdges.contains(key)) {
                    seenEdges.add(key);
                    edgePairs.add(new int[]{indexOf.get(v), indexOf.get(u)});
                }
            }
        }

        // Try subsets of increasing size
        for (int size = 0; size <= n; size++) {
            Set<String> result = findCoverOfSize(vertices, edgePairs, n, size);
            if (result != null) {
                return Collections.unmodifiableSet(result);
            }
        }
        // Should never reach here — full set is always a cover
        return Collections.unmodifiableSet(new LinkedHashSet<String>(vertices));
    }

    private Set<String> findCoverOfSize(List<String> vertices, List<int[]> edges,
                                         int n, int targetSize) {
        int[] combo = new int[targetSize];
        return findCoverRecursive(vertices, edges, n, targetSize, combo, 0, 0);
    }

    private Set<String> findCoverRecursive(List<String> vertices, List<int[]> edges,
                                            int n, int targetSize, int[] combo,
                                            int start, int depth) {
        if (depth == targetSize) {
            // Check if combo covers all edges
            long mask = 0;
            for (int i = 0; i < targetSize; i++) {
                mask |= (1L << combo[i]);
            }
            for (int[] ep : edges) {
                if ((mask & (1L << ep[0])) == 0 && (mask & (1L << ep[1])) == 0) {
                    return null;
                }
            }
            Set<String> result = new LinkedHashSet<String>();
            for (int i = 0; i < targetSize; i++) {
                result.add(vertices.get(combo[i]));
            }
            return result;
        }
        for (int i = start; i <= n - (targetSize - depth); i++) {
            combo[depth] = i;
            Set<String> result = findCoverRecursive(vertices, edges, n, targetSize,
                                                     combo, i + 1, depth + 1);
            if (result != null) return result;
        }
        return null;
    }

    // ── Cover verification ──────────────────────────────────────────────

    /**
     * Checks whether a given set of vertices is a valid vertex cover.
     *
     * @param cover the candidate vertex cover
     * @return true if every Edge has at least one endpoint in cover
     * @throws IllegalArgumentException if cover is null
     */
    public boolean isVertexCover(Set<String> cover) {
        if (cover == null) {
            throw new IllegalArgumentException("Cover set must not be null");
        }
        for (Edge e : graph.getEdges()) {
            if (!cover.contains(e.getVertex1()) && !cover.contains(e.getVertex2())) {
                return false;
            }
        }
        return true;
    }

    // ── Uncovered edges ─────────────────────────────────────────────────

    /**
     * Finds all edges not covered by a given set of vertices.
     *
     * @param cover the candidate vertex cover
     * @return list of uncovered edges as [v1, v2] pairs
     * @throws IllegalArgumentException if cover is null
     */
    public List<String[]> uncoveredEdges(Set<String> cover) {
        if (cover == null) {
            throw new IllegalArgumentException("Cover set must not be null");
        }
        List<String[]> uncovered = new ArrayList<String[]>();
        for (Edge e : graph.getEdges()) {
            if (!cover.contains(e.getVertex1()) && !cover.contains(e.getVertex2())) {
                uncovered.add(new String[]{e.getVertex1(), e.getVertex2()});
            }
        }
        return uncovered;
    }

    // ── Per-vertex coverage info ────────────────────────────────────────

    /**
     * For each vertex in the cover, counts how many edges it covers
     * (edges incident to it).
     *
     * @param cover the vertex cover
     * @return map from cover vertex to number of edges it covers
     * @throws IllegalArgumentException if cover is null
     */
    public Map<String, Integer> coverageContribution(Set<String> cover) {
        if (cover == null) {
            throw new IllegalArgumentException("Cover set must not be null");
        }
        Map<String, Integer> contrib = new LinkedHashMap<String, Integer>();
        for (String v : cover) {
            Set<String> neighbors = adj.get(v);
            contrib.put(v, neighbors == null ? 0 : neighbors.size());
        }
        return Collections.unmodifiableMap(contrib);
    }

    // ── Vertex cover number bounds ──────────────────────────────────────

    /**
     * Computes bounds on the vertex cover number.
     *
     * @return a CoverBounds object with lower and upper bounds
     */
    public CoverBounds coverBounds() {
        int n = adj.size();
        int m = graph.getEdgeCount();

        if (n == 0 || m == 0) {
            return new CoverBounds(0, 0);
        }

        // Lower bound: max matching size (via greedy matching)
        int matchingSize = greedyMaxMatchingSize();

        // Upper bound: n minus the size of a maximal independent set
        // (complement of a vertex cover is an independent set)
        // Simple bound: n - 1 (at least one vertex can be removed if the
        // graph is connected). Better: use greedy cover size.
        Set<String> greedyCover = greedyVertexCover();
        int upperBound = greedyCover.size();

        // Also: every vertex cover has size >= m / Δ (max degree)
        int maxDeg = 0;
        for (Set<String> neighbors : adj.values()) {
            maxDeg = Math.max(maxDeg, neighbors.size());
        }
        int edgeLower = maxDeg > 0 ? (int) Math.ceil((double) m / maxDeg) : 0;
        int lowerBound = Math.max(matchingSize, edgeLower);

        return new CoverBounds(lowerBound, upperBound);
    }

    /**
     * Greedy maximum matching — picks edges greedily, no vertex reused.
     */
    private int greedyMaxMatchingSize() {
        Set<String> matched = new HashSet<String>();
        int count = 0;
        for (Edge e : graph.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            if (!matched.contains(v1) && !matched.contains(v2)) {
                matched.add(v1);
                matched.add(v2);
                count++;
            }
        }
        return count;
    }

    // ── Weighted vertex cover ───────────────────────────────────────────

    /**
     * Finds a vertex cover minimizing total weight using a pricing/greedy
     * approach. Iteratively selects the vertex with lowest weight-to-uncovered-
     * degree ratio.
     *
     * @param weights map from vertex ID to weight (vertices without weights
     *                default to 1.0)
     * @return an unmodifiable set of vertex IDs forming a weighted vertex cover
     * @throws IllegalArgumentException if weights is null
     */
    public Set<String> weightedVertexCover(Map<String, Double> weights) {
        if (weights == null) {
            throw new IllegalArgumentException("Weights map must not be null");
        }
        Set<String> cover = new LinkedHashSet<String>();
        Set<String> coveredEdgeKeys = new HashSet<String>();
        int totalEdges = graph.getEdgeCount();

        Map<String, Integer> uncoveredDeg = new HashMap<String, Integer>();
        for (String v : adj.keySet()) {
            uncoveredDeg.put(v, adj.get(v).size());
        }

        while (coveredEdgeKeys.size() < totalEdges) {
            String best = null;
            double bestRatio = Double.MAX_VALUE;
            for (Map.Entry<String, Integer> entry : uncoveredDeg.entrySet()) {
                String v = entry.getKey();
                int deg = entry.getValue();
                if (!cover.contains(v) && deg > 0) {
                    double w = weights.containsKey(v) ? weights.get(v) : 1.0;
                    double ratio = w / deg;
                    if (ratio < bestRatio) {
                        bestRatio = ratio;
                        best = v;
                    }
                }
            }
            if (best == null) break;

            cover.add(best);
            for (String n : adj.get(best)) {
                String key = edgeKey(best, n);
                if (!coveredEdgeKeys.contains(key)) {
                    coveredEdgeKeys.add(key);
                    uncoveredDeg.put(n, uncoveredDeg.get(n) - 1);
                }
            }
            uncoveredDeg.put(best, 0);
        }
        return Collections.unmodifiableSet(cover);
    }

    /**
     * Computes total weight of a cover.
     *
     * @param cover   the vertex cover
     * @param weights weight map (vertices without weights default to 1.0)
     * @return total weight
     */
    public double coverWeight(Set<String> cover, Map<String, Double> weights) {
        if (cover == null || weights == null) {
            throw new IllegalArgumentException("Arguments must not be null");
        }
        double total = 0;
        for (String v : cover) {
            total += weights.containsKey(v) ? weights.get(v) : 1.0;
        }
        return total;
    }

    // ── LP relaxation bound ─────────────────────────────────────────────

    /**
     * Computes a lower bound on the vertex cover number using a simple
     * LP relaxation approach. Each vertex gets value 0.5 if its degree > 0,
     * providing a fractional cover whose total is a lower bound.
     *
     * @return LP relaxation lower bound (ceiling of fractional cover value)
     */
    public int lpRelaxationBound() {
        if (graph.getEdgeCount() == 0) return 0;
        // In the LP relaxation of vertex cover, the optimal fractional
        // solution assigns 0.5 to every vertex incident to any Edge.
        Set<String> incidentVertices = new HashSet<String>();
        for (Edge e : graph.getEdges()) {
            incidentVertices.add(e.getVertex1());
            incidentVertices.add(e.getVertex2());
        }
        // LP value = |incident vertices| / 2
        return (int) Math.ceil(incidentVertices.size() / 2.0);
    }

    // ── Kernel reduction ────────────────────────────────────────────────

    /**
     * Applies degree-based reduction rules for vertex cover:
     * <ul>
     *   <li>Degree-0 vertices are never needed.</li>
     *   <li>Degree-1 vertices: include the neighbor instead (crown rule).</li>
     *   <li>Vertices with degree ≥ n−1 (universal) must be in any cover.</li>
     * </ul>
     *
     * @return a KernelResult with forced-in vertices, excluded vertices,
     *         and remaining kernel vertices
     */
    public KernelResult kernelReduce() {
        Set<String> forcedIn = new LinkedHashSet<String>();
        Set<String> excluded = new LinkedHashSet<String>();
        Set<String> remaining = new LinkedHashSet<String>(adj.keySet());
        Map<String, Set<String>> residual = new HashMap<String, Set<String>>();
        for (Map.Entry<String, Set<String>> entry : adj.entrySet()) {
            residual.put(entry.getKey(), new HashSet<String>(entry.getValue()));
        }

        boolean changed = true;
        while (changed) {
            changed = false;

            // Process one reduction per iteration to avoid conflicts
            String toExclude = null;
            String toForce = null;

            for (String v : remaining) {
                int degree = 0;
                for (String n : residual.get(v)) {
                    if (remaining.contains(n)) degree++;
                }
                if (degree == 0) {
                    toExclude = v;
                    break;
                } else if (degree == 1) {
                    toExclude = v;
                    for (String n : residual.get(v)) {
                        if (remaining.contains(n)) { toForce = n; break; }
                    }
                    break;
                }
            }

            if (toExclude != null) {
                remaining.remove(toExclude);
                excluded.add(toExclude);
                if (toForce != null) {
                    remaining.remove(toForce);
                    forcedIn.add(toForce);
                }
                changed = true;
                continue;
            }

            // Universal vertices (adjacent to all remaining)
            for (String v : remaining) {
                int degree = 0;
                for (String n : residual.get(v)) {
                    if (remaining.contains(n)) degree++;
                }
                if (degree >= remaining.size() - 1 && remaining.size() > 1) {
                    remaining.remove(v);
                    forcedIn.add(v);
                    changed = true;
                    break;
                }
            }
        }

        return new KernelResult(
                Collections.unmodifiableSet(forcedIn),
                Collections.unmodifiableSet(excluded),
                Collections.unmodifiableSet(remaining)
        );
    }

    // ── Full report ─────────────────────────────────────────────────────

    /**
     * Generates a comprehensive vertex cover analysis report.
     *
     * @return a VertexCoverReport with all analysis results
     */
    public VertexCoverReport fullReport() {
        Set<String> approx = approxVertexCover();
        Set<String> greedy = greedyVertexCover();
        CoverBounds bounds = coverBounds();
        int lpBound = lpRelaxationBound();
        KernelResult kernel = kernelReduce();
        Map<String, Integer> greedyContrib = coverageContribution(greedy);

        Set<String> exact = null;
        if (adj.size() <= 20) {
            exact = exactMinimumVertexCover();
        }

        return new VertexCoverReport(
                graph.getVertexCount(), graph.getEdgeCount(),
                approx, greedy, exact,
                bounds, lpBound, kernel, greedyContrib
        );
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private String edgeKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    // ── Inner classes ───────────────────────────────────────────────────

    /** Bounds on the vertex cover number. */
    public static class CoverBounds {
        private final int lowerBound;
        private final int upperBound;

        public CoverBounds(int lowerBound, int upperBound) {
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
        }

        public int getLowerBound() { return lowerBound; }
        public int getUpperBound() { return upperBound; }

        @Override
        public String toString() {
            return "CoverBounds{lower=" + lowerBound + ", upper=" + upperBound + "}";
        }
    }

    /** Result of kernel reduction. */
    public static class KernelResult {
        private final Set<String> forcedIn;
        private final Set<String> excluded;
        private final Set<String> remaining;

        public KernelResult(Set<String> forcedIn, Set<String> excluded,
                            Set<String> remaining) {
            this.forcedIn = forcedIn;
            this.excluded = excluded;
            this.remaining = remaining;
        }

        public Set<String> getForcedIn() { return forcedIn; }
        public Set<String> getExcluded() { return excluded; }
        public Set<String> getRemaining() { return remaining; }

        @Override
        public String toString() {
            return "KernelResult{forcedIn=" + forcedIn.size() +
                   ", excluded=" + excluded.size() +
                   ", remaining=" + remaining.size() + "}";
        }
    }

    /** Comprehensive vertex cover analysis report. */
    public static class VertexCoverReport {
        private final int vertexCount;
        private final int edgeCount;
        private final Set<String> approxCover;
        private final Set<String> greedyCover;
        private final Set<String> exactCover;
        private final CoverBounds bounds;
        private final int lpBound;
        private final KernelResult kernel;
        private final Map<String, Integer> greedyContributions;

        public VertexCoverReport(int vertexCount, int edgeCount,
                                  Set<String> approxCover, Set<String> greedyCover,
                                  Set<String> exactCover, CoverBounds bounds,
                                  int lpBound, KernelResult kernel,
                                  Map<String, Integer> greedyContributions) {
            this.vertexCount = vertexCount;
            this.edgeCount = edgeCount;
            this.approxCover = approxCover;
            this.greedyCover = greedyCover;
            this.exactCover = exactCover;
            this.bounds = bounds;
            this.lpBound = lpBound;
            this.kernel = kernel;
            this.greedyContributions = greedyContributions;
        }

        public int getVertexCount() { return vertexCount; }
        public int getEdgeCount() { return edgeCount; }
        public Set<String> getApproxCover() { return approxCover; }
        public Set<String> getGreedyCover() { return greedyCover; }
        public Set<String> getExactCover() { return exactCover; }
        public CoverBounds getBounds() { return bounds; }
        public int getLpBound() { return lpBound; }
        public KernelResult getKernel() { return kernel; }
        public Map<String, Integer> getGreedyContributions() { return greedyContributions; }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("═══ Vertex Cover Report ═══\n");
            sb.append("Graph: ").append(vertexCount).append(" vertices, ")
              .append(edgeCount).append(" edges\n");
            sb.append("2-Approx cover size: ").append(approxCover.size()).append("\n");
            sb.append("Greedy cover size:   ").append(greedyCover.size()).append("\n");
            if (exactCover != null) {
                sb.append("Exact minimum size:  ").append(exactCover.size()).append("\n");
            }
            sb.append("Bounds: ").append(bounds).append("\n");
            sb.append("LP relaxation bound: ").append(lpBound).append("\n");
            sb.append("Kernel: ").append(kernel).append("\n");
            return sb.toString();
        }
    }
}
