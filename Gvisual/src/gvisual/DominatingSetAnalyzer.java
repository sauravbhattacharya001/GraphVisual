package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Dominating Set Analyzer — finds and analyses dominating sets in a graph.
 *
 * <blockquote>
 * A <b>dominating set</b> D is a subset of vertices such that every vertex
 * in V is either in D or adjacent to at least one vertex in D.
 * </blockquote>
 *
 * <h3>Features</h3>
 * <ul>
 *   <li><b>Greedy minimum dominating set</b> — degree-based greedy heuristic
 *       that iteratively picks the vertex covering the most undominated
 *       neighbours. O(V·(V + E)).</li>
 *   <li><b>Exact minimum dominating set</b> — brute-force search for small
 *       graphs (≤ 20 vertices) with early termination.</li>
 *   <li><b>Independent dominating set</b> — dominating set where no two
 *       members are adjacent.</li>
 *   <li><b>Connected dominating set</b> — dominating set whose induced
 *       subgraph is connected (virtual backbone).</li>
 *   <li><b>Domination number bounds</b> — lower bound (ceil(n / (1 + Δ)))
 *       and upper bound (n − Δ) where Δ is maximum degree.</li>
 *   <li><b>k-domination</b> — every non-member vertex is adjacent to at
 *       least k members.</li>
 *   <li><b>Domination verification</b> — check whether a given set is a
 *       valid dominating set.</li>
 *   <li><b>Per-vertex domination info</b> — for each vertex, how many
 *       dominators cover it.</li>
 *   <li><b>Full report</b> — consolidated analysis with all metrics.</li>
 * </ul>
 *
 * <p>Applications: wireless sensor network coverage, facility placement,
 * social network influence, ad-hoc network routing backbones.</p>
 *
 * @author zalenix
 */
public class DominatingSetAnalyzer {

    private final Graph<String, Edge> graph;
    private final Map<String, Set<String>> adj;

    /**
     * Creates a new DominatingSetAnalyzer.
     *
     * @param graph the JUNG graph to analyse
     * @throws IllegalArgumentException if graph is null
     */
    public DominatingSetAnalyzer(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.adj = GraphUtils.buildAdjacencyMap(graph);
    }

    // ── Greedy minimum dominating set ───────────────────────────────────

    /**
     * Finds a dominating set using a greedy heuristic.
     * Picks the vertex that dominates the most uncovered vertices at each step.
     *
     * @return an unmodifiable set of vertex IDs forming a dominating set
     */
    public Set<String> greedyDominatingSet() {
        Set<String> dominated = new HashSet<String>();
        Set<String> result = new LinkedHashSet<String>();
        Set<String> remaining = new HashSet<String>(adj.keySet());

        while (dominated.size() < adj.size()) {
            String best = null;
            int bestScore = -1;
            for (String v : remaining) {
                int score = 0;
                if (!dominated.contains(v)) score++;
                for (String n : adj.get(v)) {
                    if (!dominated.contains(n)) score++;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = v;
                }
            }
            if (best == null) break;
            result.add(best);
            remaining.remove(best);
            dominated.add(best);
            dominated.addAll(adj.get(best));
        }
        return Collections.unmodifiableSet(result);
    }

    // ── Exact minimum dominating set (brute force, small graphs) ────────

    /**
     * Finds an exact minimum dominating set by exhaustive search.
     * Only practical for graphs with ≤ 20 vertices.
     *
     * <p><b>Optimization:</b> Uses bitmask-based enumeration with precomputed
     * closed-neighborhood masks. Each vertex {@code i} has a mask
     * {@code closedNbr[i]} with bit {@code j} set iff {@code j == i} or
     * {@code j} is a neighbor of {@code i}. A candidate set's domination
     * coverage is computed by OR-ing the masks of its members — O(1) per
     * member instead of O(degree) HashSet operations. This eliminates all
     * object allocation in the inner loop (no LinkedHashSet per combination)
     * and reduces the domination check from O(V·D) to O(D) bit-ops.</p>
     *
     * @return the smallest dominating set, or empty set for empty graphs
     * @throws IllegalStateException if graph has more than 20 vertices
     */
    public Set<String> exactMinimumDominatingSet() {
        List<String> vertices = new ArrayList<String>(adj.keySet());
        int n = vertices.size();
        if (n == 0) return Collections.emptySet();
        if (n > 20) {
            throw new IllegalStateException(
                "Exact search only supported for graphs with <= 20 vertices (has " + n + ")");
        }

        // Build index map: vertex name → bit position
        Map<String, Integer> indexOf = new HashMap<String, Integer>(n * 2);
        for (int i = 0; i < n; i++) indexOf.put(vertices.get(i), i);

        // Precompute closed-neighborhood bitmasks.
        // closedNbr[i] has bit j set iff j == i or j is adjacent to i.
        int[] closedNbr = new int[n];
        for (int i = 0; i < n; i++) {
            int mask = 1 << i; // self
            Set<String> neighbors = adj.get(vertices.get(i));
            if (neighbors != null) {
                for (String nb : neighbors) {
                    Integer idx = indexOf.get(nb);
                    if (idx != null) mask |= (1 << idx);
                }
            }
            closedNbr[i] = mask;
        }

        int fullMask = (1 << n) - 1;

        // Try increasing subset sizes using Gosper's hack for
        // enumerating k-subsets in bitmask order.
        for (int size = 1; size <= n; size++) {
            int result = _findDominatingMask(size, n, closedNbr, fullMask);
            if (result >= 0) {
                Set<String> domSet = new LinkedHashSet<String>();
                for (int i = 0; i < n; i++) {
                    if ((result & (1 << i)) != 0) domSet.add(vertices.get(i));
                }
                return Collections.unmodifiableSet(domSet);
            }
        }
        // Full set always dominates
        return Collections.unmodifiableSet(new LinkedHashSet<String>(vertices));
    }

    /**
     * Enumerates all k-subsets of {0..n-1} using Gosper's hack and
     * checks domination via precomputed bitmasks.
     *
     * @return the first dominating bitmask found, or -1 if none exists
     */
    private static int _findDominatingMask(int k, int n, int[] closedNbr, int fullMask) {
        if (k == 0) return fullMask == 0 ? 0 : -1;
        // Start: lowest k bits set
        int set = (1 << k) - 1;
        int limit = 1 << n;

        while (set < limit) {
            // Compute coverage by OR-ing closed neighborhoods
            int coverage = 0;
            int tmp = set;
            while (tmp != 0) {
                int lowest = tmp & (-tmp);           // isolate lowest set bit
                int idx = Integer.numberOfTrailingZeros(lowest);
                coverage |= closedNbr[idx];
                tmp ^= lowest;                       // clear lowest set bit
            }
            if (coverage == fullMask) return set;

            // Gosper's hack: advance to next k-subset
            int c = set & (-set);
            int r = set + c;
            set = (((r ^ set) >> 2) / c) | r;
        }
        return -1;
    }

    // ── Independent dominating set ──────────────────────────────────────

    /**
     * Checks whether a vertex can be added to the result set without
     * violating independence (no adjacency to any existing member).
     *
     * @param v      the candidate vertex
     * @param result the current independent set
     * @return true if v is not adjacent to any member of result
     */
    private boolean isIndependentOf(String v, Set<String> result) {
        Set<String> neighbors = adj.get(v);
        for (String m : result) {
            if (neighbors.contains(m)) return false;
        }
        return true;
    }

    /**
     * Finds an independent dominating set — a dominating set where no two
     * members are adjacent to each other.
     *
     * @return an unmodifiable independent dominating set
     */
    public Set<String> independentDominatingSet() {
        Set<String> result = new LinkedHashSet<String>();
        Set<String> dominated = new HashSet<String>();
        // Sort by degree descending for better results
        List<String> vertices = new ArrayList<String>(adj.keySet());
        Collections.sort(vertices, (String a, String b) -> {
                return adj.get(b).size() - adj.get(a).size();
            });

        // Two passes: first pass prioritizes undominated vertices,
        // second pass covers any remaining gaps.
        for (int pass = 0; pass < 2; pass++) {
            for (String v : vertices) {
                if (result.contains(v)) continue;
                if (pass == 0 && dominated.contains(v)) continue;
                if (pass == 1 && dominated.contains(v)) continue;
                if (isIndependentOf(v, result)) {
                    result.add(v);
                    dominated.add(v);
                    dominated.addAll(adj.get(v));
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }

    // ── Connected dominating set ────────────────────────────────────────

    /**
     * Finds a connected dominating set — a dominating set whose induced
     * subgraph is connected. Uses a BFS-tree approach starting from the
     * highest-degree vertex.
     *
     * <p>Maintains a frontier set of vertices adjacent to the current CDS
     * so the connectivity check is O(1) instead of O(|CDS|) per candidate.</p>
     *
     * @return an unmodifiable connected dominating set, or empty if graph
     *         is disconnected or empty
     */
    public Set<String> connectedDominatingSet() {
        if (adj.isEmpty()) return Collections.emptySet();

        // Start with highest-degree vertex
        String start = null;
        int maxDeg = -1;
        for (Map.Entry<String, Set<String>> e : adj.entrySet()) {
            int deg = e.getValue().size();
            if (deg > maxDeg) {
                maxDeg = deg;
                start = e.getKey();
            }
        }

        Set<String> cds = new LinkedHashSet<String>();
        Set<String> dominated = new HashSet<String>();
        // Frontier: non-CDS vertices adjacent to at least one CDS member
        Set<String> frontier = new HashSet<String>();

        cds.add(start);
        dominated.add(start);
        dominated.addAll(adj.get(start));
        for (String n : adj.get(start)) {
            if (!cds.contains(n)) frontier.add(n);
        }

        // Grow the CDS by adding connector vertices
        while (dominated.size() < adj.size()) {
            String best = null;
            int bestScore = -1;
            for (String v : frontier) {
                int score = 0;
                for (String n : adj.get(v)) {
                    if (!dominated.contains(n)) score++;
                }
                if (!dominated.contains(v)) score++;
                if (score > bestScore) {
                    bestScore = score;
                    best = v;
                }
            }
            if (best == null) break; // disconnected graph
            cds.add(best);
            frontier.remove(best);
            dominated.add(best);
            for (String n : adj.get(best)) {
                dominated.add(n);
                if (!cds.contains(n)) frontier.add(n);
            }
        }

        if (dominated.size() < adj.size()) return Collections.emptySet();
        return Collections.unmodifiableSet(cds);
    }

    // ── k-domination ────────────────────────────────────────────────────

    /**
     * Finds a k-dominating set where every non-member vertex is adjacent
     * to at least k members.
     *
     * @param k the domination factor (≥ 1)
     * @return an unmodifiable k-dominating set
     * @throws IllegalArgumentException if k < 1
     */
    public Set<String> kDominatingSet(int k) {
        if (k < 1) throw new IllegalArgumentException("k must be >= 1");

        Set<String> result = new LinkedHashSet<String>();
        Map<String, Integer> coverage = new HashMap<String, Integer>();
        for (String v : adj.keySet()) coverage.put(v, 0);

        List<String> vertices = new ArrayList<String>(adj.keySet());
        Collections.sort(vertices, (String a, String b) -> {
                return adj.get(b).size() - adj.get(a).size();
            });

        while (true) {
            // Find vertices not yet k-dominated (excluding result members)
            boolean allCovered = true;
            for (String v : adj.keySet()) {
                if (!result.contains(v) && coverage.get(v) < k) {
                    allCovered = false;
                    break;
                }
            }
            if (allCovered) break;

            // Pick vertex that covers the most under-dominated vertices
            String best = null;
            int bestScore = -1;
            for (String v : vertices) {
                if (result.contains(v)) continue;
                int score = 0;
                for (String n : adj.get(v)) {
                    if (!result.contains(n) && coverage.get(n) < k) score++;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = v;
                }
            }
            if (best == null) break;
            result.add(best);
            for (String n : adj.get(best)) {
                coverage.put(n, coverage.get(n) + 1);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    // ── Verification ────────────────────────────────────────────────────

    /**
     * Checks whether the given set is a valid dominating set.
     *
     * <p>Instead of checking every non-member vertex against every candidate
     * member (O(V·D)), we build the dominated set in O(D·avg_degree) by
     * collecting the closed neighborhoods of all candidates, then verify
     * full coverage in O(1).</p>
     *
     * @param candidate the set to verify
     * @return true if every vertex is in the set or adjacent to a member
     */
    public boolean isDominatingSet(Set<String> candidate) {
        if (candidate == null) return false;
        Set<String> dominated = new HashSet<String>(candidate);
        for (String m : candidate) {
            Set<String> neighbors = adj.get(m);
            if (neighbors != null) dominated.addAll(neighbors);
        }
        return dominated.size() >= adj.size();
    }

    /**
     * Checks whether the given set is an independent set (no two members adjacent).
     * Uses the internal {@link #isIndependentOf} helper to avoid O(n²) pair checks
     * when the set is built incrementally — but for arbitrary verification, the
     * pairwise scan is still needed.
     *
     * @param set the set to check
     * @return true if no two members share an Edge
     */
    public boolean isIndependentSet(Set<String> set) {
        if (set == null || set.size() <= 1) return true;
        // For each member, check that none of its neighbors are also in the set.
        // This is O(Σ degree) which is typically better than O(|set|²) for sparse graphs.
        for (String v : set) {
            Set<String> neighbors = adj.get(v);
            if (neighbors != null) {
                for (String n : neighbors) {
                    if (set.contains(n)) return false;
                }
            }
        }
        return true;
    }

    // ── Domination bounds ───────────────────────────────────────────────

    /**
     * Returns the maximum degree (Δ) of any vertex in the graph.
     * Used by domination bound computations and greedy heuristics.
     *
     * @return maximum degree, or 0 for empty graphs
     */
    private int maxDegree() {
        int maxDeg = 0;
        for (Set<String> neighbors : adj.values()) {
            if (neighbors.size() > maxDeg) maxDeg = neighbors.size();
        }
        return maxDeg;
    }

    /**
     * Computes the lower bound on the domination number: ceil(n / (1 + Δ)).
     *
     * @return the lower bound
     */
    public int dominationLowerBound() {
        int n = adj.size();
        if (n == 0) return 0;
        return (int) Math.ceil((double) n / (1 + maxDegree()));
    }

    /**
     * Computes the upper bound on the domination number: n − Δ
     * (for non-empty graphs with Δ > 0).
     *
     * @return the upper bound
     */
    public int dominationUpperBound() {
        int n = adj.size();
        if (n == 0) return 0;
        int maxDeg = maxDegree();
        return maxDeg > 0 ? n - maxDeg : n;
    }

    // ── Per-vertex coverage ─────────────────────────────────────────────

    /**
     * For a given dominating set, returns how many members dominate each vertex.
     *
     * <p>Instead of iterating all dominators for each vertex (O(V·D)),
     * iterates each dominator's neighborhood once (O(D·avg_degree)),
     * incrementing coverage counters via the adjacency structure.</p>
     *
     * @param dominatingSet the dominating set
     * @return map from vertex to number of covering dominators
     */
    public Map<String, Integer> coverageMap(Set<String> dominatingSet) {
        Map<String, Integer> coverage = new LinkedHashMap<String, Integer>();
        for (String v : adj.keySet()) {
            coverage.put(v, 0);
        }
        for (String m : dominatingSet) {
            // Self-domination
            Integer selfCount = coverage.get(m);
            if (selfCount != null) coverage.put(m, selfCount + 1);
            // Neighbor domination
            Set<String> neighbors = adj.get(m);
            if (neighbors != null) {
                for (String n : neighbors) {
                    Integer count = coverage.get(n);
                    if (count != null) coverage.put(n, count + 1);
                }
            }
        }
        return coverage;
    }

    /**
     * Returns the minimum coverage (minimum number of dominators covering
     * any single vertex) for a given dominating set.
     *
     * @param dominatingSet the dominating set
     * @return the minimum coverage value
     */
    public int minimumCoverage(Set<String> dominatingSet) {
        Map<String, Integer> cov = coverageMap(dominatingSet);
        int min = Integer.MAX_VALUE;
        for (int v : cov.values()) {
            if (v < min) min = v;
        }
        return cov.isEmpty() ? 0 : min;
    }

    // ── Full report ─────────────────────────────────────────────────────

    /**
     * Generates a comprehensive domination analysis report.
     *
     * @return a formatted multi-line report string
     */
    public String generateReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Dominating Set Analysis ===\n\n");
        int n = adj.size();
        int edges = graph.getEdgeCount();
        sb.append(String.format("Graph: %d vertices, %d edges%n%n", n, edges));

        // Bounds
        int lb = dominationLowerBound();
        int ub = dominationUpperBound();
        sb.append(String.format("Domination number bounds: [%d, %d]%n", lb, ub));

        // Greedy
        Set<String> greedy = greedyDominatingSet();
        sb.append(String.format("%nGreedy dominating set (%d vertices): %s%n",
            greedy.size(), greedy));
        sb.append(String.format("  Valid: %s%n", isDominatingSet(greedy)));

        // Independent
        Set<String> ids = independentDominatingSet();
        sb.append(String.format("%nIndependent dominating set (%d vertices): %s%n",
            ids.size(), ids));
        sb.append(String.format("  Valid dominating: %s, Independent: %s%n",
            isDominatingSet(ids), isIndependentSet(ids)));

        // Connected
        Set<String> cds = connectedDominatingSet();
        if (!cds.isEmpty()) {
            sb.append(String.format("%nConnected dominating set (%d vertices): %s%n",
                cds.size(), cds));
            sb.append(String.format("  Valid: %s%n", isDominatingSet(cds)));
        } else {
            sb.append("\nConnected dominating set: N/A (graph disconnected)\n");
        }

        // Coverage
        sb.append(String.format("%nCoverage (greedy): min=%d%n",
            minimumCoverage(greedy)));

        // Exact (small graphs only)
        if (n <= 20 && n > 0) {
            Set<String> exact = exactMinimumDominatingSet();
            sb.append(String.format("%nExact minimum dominating set (%d vertices): %s%n",
                exact.size(), exact));
            sb.append(String.format("  Domination number: %d%n", exact.size()));
        }

        return sb.toString();
    }
}
