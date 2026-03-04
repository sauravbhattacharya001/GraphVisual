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

    private final Graph<String, edge> graph;
    private final Map<String, Set<String>> adj;

    /**
     * Creates a new DominatingSetAnalyzer.
     *
     * @param graph the JUNG graph to analyse
     * @throws IllegalArgumentException if graph is null
     */
    public DominatingSetAnalyzer(Graph<String, edge> graph) {
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

        // Try increasing subset sizes
        for (int size = 1; size <= n; size++) {
            Set<String> result = findDominatingSetOfSize(vertices, size, n);
            if (result != null) return Collections.unmodifiableSet(result);
        }
        // Full set always dominates
        return Collections.unmodifiableSet(new LinkedHashSet<String>(vertices));
    }

    private Set<String> findDominatingSetOfSize(List<String> vertices, int size, int n) {
        int[] indices = new int[size];
        for (int i = 0; i < size; i++) indices[i] = i;

        while (true) {
            Set<String> candidate = new LinkedHashSet<String>();
            for (int idx : indices) candidate.add(vertices.get(idx));
            if (isDominatingSet(candidate)) return candidate;

            // Next combination
            int i = size - 1;
            while (i >= 0 && indices[i] == n - size + i) i--;
            if (i < 0) return null;
            indices[i]++;
            for (int j = i + 1; j < size; j++) indices[j] = indices[j - 1] + 1;
        }
    }

    // ── Independent dominating set ──────────────────────────────────────

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
        Collections.sort(vertices, new Comparator<String>() {
            public int compare(String a, String b) {
                return adj.get(b).size() - adj.get(a).size();
            }
        });

        for (String v : vertices) {
            if (dominated.contains(v)) continue;
            // Check independence: v must not be adjacent to any member
            boolean independent = true;
            for (String m : result) {
                if (adj.get(v).contains(m)) {
                    independent = false;
                    break;
                }
            }
            if (independent) {
                result.add(v);
                dominated.add(v);
                dominated.addAll(adj.get(v));
            }
        }
        // Cover any remaining undominated vertices
        for (String v : vertices) {
            if (!dominated.contains(v)) {
                boolean canAdd = true;
                for (String m : result) {
                    if (adj.get(v).contains(m)) { canAdd = false; break; }
                }
                if (canAdd) {
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
     * @return an unmodifiable connected dominating set, or empty if graph
     *         is disconnected or empty
     */
    public Set<String> connectedDominatingSet() {
        if (adj.isEmpty()) return Collections.emptySet();

        // Start with highest-degree vertex
        String start = null;
        int maxDeg = -1;
        for (Map.Entry<String, Set<String>> e : adj.entrySet()) {
            if (e.getValue().size() > maxDeg) {
                maxDeg = e.getValue().size();
                start = e.getKey();
            }
        }

        Set<String> cds = new LinkedHashSet<String>();
        Set<String> dominated = new HashSet<String>();
        cds.add(start);
        dominated.add(start);
        dominated.addAll(adj.get(start));

        // Grow the CDS by adding connector vertices
        while (dominated.size() < adj.size()) {
            String best = null;
            int bestScore = -1;
            for (String v : adj.keySet()) {
                if (cds.contains(v)) continue;
                // Must be adjacent to at least one CDS member (connectivity)
                boolean connects = false;
                for (String m : cds) {
                    if (adj.get(v).contains(m)) { connects = true; break; }
                }
                if (!connects) continue;

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
            dominated.add(best);
            dominated.addAll(adj.get(best));
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
        Collections.sort(vertices, new Comparator<String>() {
            public int compare(String a, String b) {
                return adj.get(b).size() - adj.get(a).size();
            }
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
     * @param candidate the set to verify
     * @return true if every vertex is in the set or adjacent to a member
     */
    public boolean isDominatingSet(Set<String> candidate) {
        if (candidate == null) return false;
        for (String v : adj.keySet()) {
            if (candidate.contains(v)) continue;
            boolean covered = false;
            for (String m : candidate) {
                if (adj.containsKey(v) && adj.get(v).contains(m)) {
                    covered = true;
                    break;
                }
            }
            if (!covered) return false;
        }
        return true;
    }

    /**
     * Checks whether the given set is an independent set (no two members adjacent).
     *
     * @param set the set to check
     * @return true if no two members share an edge
     */
    public boolean isIndependentSet(Set<String> set) {
        if (set == null) return true;
        List<String> members = new ArrayList<String>(set);
        for (int i = 0; i < members.size(); i++) {
            for (int j = i + 1; j < members.size(); j++) {
                Set<String> ni = adj.get(members.get(i));
                if (ni != null && ni.contains(members.get(j))) return false;
            }
        }
        return true;
    }

    // ── Domination bounds ───────────────────────────────────────────────

    /**
     * Computes the lower bound on the domination number: ceil(n / (1 + Δ)).
     *
     * @return the lower bound
     */
    public int dominationLowerBound() {
        int n = adj.size();
        if (n == 0) return 0;
        int maxDeg = 0;
        for (Set<String> neighbors : adj.values()) {
            if (neighbors.size() > maxDeg) maxDeg = neighbors.size();
        }
        return (int) Math.ceil((double) n / (1 + maxDeg));
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
        int maxDeg = 0;
        for (Set<String> neighbors : adj.values()) {
            if (neighbors.size() > maxDeg) maxDeg = neighbors.size();
        }
        return maxDeg > 0 ? n - maxDeg : n;
    }

    // ── Per-vertex coverage ─────────────────────────────────────────────

    /**
     * For a given dominating set, returns how many members dominate each vertex.
     *
     * @param dominatingSet the dominating set
     * @return map from vertex to number of covering dominators
     */
    public Map<String, Integer> coverageMap(Set<String> dominatingSet) {
        Map<String, Integer> coverage = new LinkedHashMap<String, Integer>();
        for (String v : adj.keySet()) {
            int count = 0;
            if (dominatingSet.contains(v)) count++; // self-domination
            for (String m : dominatingSet) {
                if (!m.equals(v) && adj.get(v) != null && adj.get(v).contains(m)) {
                    count++;
                }
            }
            coverage.put(v, count);
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
