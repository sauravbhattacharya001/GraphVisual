package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Subgraph Pattern Matcher — finds all occurrences of a small pattern graph
 * within a larger target graph using VF2-style subgraph isomorphism.
 *
 * <p>Unlike {@link GraphIsomorphismAnalyzer} (whole-graph comparison) or
 * {@link MotifAnalyzer} (fixed, hardcoded motifs), this class lets users
 * define <em>any</em> subgraph pattern and search for all instances of it
 * in a target graph.</p>
 *
 * <h3>Features</h3>
 * <ul>
 *   <li><b>Custom patterns:</b> define any graph pattern (diamond, bowtie,
 *       house, etc.) and find all matches</li>
 *   <li><b>Built-in patterns:</b> 8 common patterns available via factory
 *       methods (triangle, square, star-3/4, path-3/4, diamond, bowtie)</li>
 *   <li><b>Degree-constrained matching:</b> optionally require that matched
 *       nodes have at least the same degree in the target as in the pattern
 *       (exact structural role matching)</li>
 *   <li><b>Edge-type filtering:</b> optionally restrict matches to edges of
 *       a specific type</li>
 *   <li><b>Match limit:</b> stop early after finding N matches (for large
 *       graphs)</li>
 *   <li><b>Overlap analysis:</b> compute overlap statistics between matches
 *       (shared nodes, unique coverage)</li>
 *   <li><b>Statistics:</b> match count, coverage, per-node participation,
 *       density comparison</li>
 * </ul>
 *
 * <h3>Algorithm</h3>
 * <p>Uses a backtracking search with feasibility pruning inspired by the
 * VF2 algorithm. Candidates are ordered by descending degree in the pattern
 * graph (most constrained first) for faster pruning. For each pattern node,
 * the algorithm tries all compatible target nodes, checking degree
 * consistency, neighbor consistency, and optional Edge-type constraints.</p>
 *
 * <h3>Complexity</h3>
 * <p>Worst-case exponential in pattern size, but practical for patterns up
 * to ~10-15 nodes in graphs up to ~10,000 nodes due to aggressive
 * pruning.</p>
 *
 * @author zalenix
 */
public class SubgraphPatternMatcher {

    private final Graph<String, Edge> target;
    private final Graph<String, Edge> pattern;
    private final boolean degreeConstrained;
    private final String edgeTypeFilter;
    private final int maxMatches;

    // ── Builder ─────────────────────────────────────────────────

    /**
     * Fluent builder for configuring a SubgraphPatternMatcher.
     */
    public static class Builder {
        private final Graph<String, Edge> target;
        private final Graph<String, Edge> pattern;
        private boolean degreeConstrained = false;
        private String edgeTypeFilter = null;
        private int maxMatches = 10_000;

        /**
         * Create a builder with target and pattern graphs.
         *
         * @param target  the graph to search in (must not be null)
         * @param pattern the pattern to find (must not be null, must have
         *                at least 2 vertices)
         * @throws IllegalArgumentException if either is null or pattern is
         *                                  too small
         */
        public Builder(Graph<String, Edge> target,
                       Graph<String, Edge> pattern) {
            if (target == null) {
                throw new IllegalArgumentException("Target graph must not be null");
            }
            if (pattern == null) {
                throw new IllegalArgumentException("Pattern graph must not be null");
            }
            if (pattern.getVertexCount() < 2) {
                throw new IllegalArgumentException(
                        "Pattern must have at least 2 vertices");
            }
            this.target = target;
            this.pattern = pattern;
        }

        /**
         * When enabled, a target node can only match a pattern node if its
         * degree in the target is ≥ its degree in the pattern.
         */
        public Builder degreeConstrained(boolean dc) {
            this.degreeConstrained = dc;
            return this;
        }

        /**
         * Only consider target edges whose type matches this value.
         * Null (default) means all edges are considered.
         */
        public Builder edgeTypeFilter(String type) {
            this.edgeTypeFilter = type;
            return this;
        }

        /**
         * Maximum number of matches to find before stopping.
         * Default: 10,000. Must be ≥ 1.
         */
        public Builder maxMatches(int max) {
            if (max < 1) {
                throw new IllegalArgumentException("maxMatches must be >= 1");
            }
            this.maxMatches = max;
            return this;
        }

        public SubgraphPatternMatcher build() {
            return new SubgraphPatternMatcher(this);
        }
    }

    private SubgraphPatternMatcher(Builder b) {
        this.target = b.target;
        this.pattern = b.pattern;
        this.degreeConstrained = b.degreeConstrained;
        this.edgeTypeFilter = b.edgeTypeFilter;
        this.maxMatches = b.maxMatches;
    }

    // ── Result classes ──────────────────────────────────────────

    /**
     * A single match: mapping from pattern nodes to target nodes.
     */
    public static class Match {
        private final Map<String, String> mapping; // pattern → target

        public Match(Map<String, String> mapping) {
            this.mapping = Collections.unmodifiableMap(new LinkedHashMap<>(mapping));
        }

        /** Pattern node → target node mapping. */
        public Map<String, String> getMapping() {
            return mapping;
        }

        /** All target nodes involved in this match. */
        public Set<String> getTargetNodes() {
            return new LinkedHashSet<>(mapping.values());
        }

        /** Number of nodes in the match. */
        public int size() {
            return mapping.size();
        }

        /**
         * Compute overlap (shared target nodes) with another match.
         *
         * @param other another match
         * @return number of shared target nodes
         */
        public int overlapWith(Match other) {
            Set<String> shared = new HashSet<>(this.getTargetNodes());
            shared.retainAll(other.getTargetNodes());
            return shared.size();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Match)) return false;
            // Two matches are equal if they cover the same target node set
            return getTargetNodes().equals(((Match) o).getTargetNodes());
        }

        @Override
        public int hashCode() {
            return getTargetNodes().hashCode();
        }

        @Override
        public String toString() {
            return mapping.entrySet().stream()
                    .map(e -> e.getKey() + "→" + e.getValue())
                    .collect(Collectors.joining(", ", "[", "]"));
        }
    }

    /**
     * Complete search result with matches and statistics.
     */
    public static class MatchResult {
        private final List<Match> matches;
        private final int patternNodes;
        private final int patternEdges;
        private final int targetNodes;
        private final int targetEdges;
        private final boolean hitLimit;

        public MatchResult(List<Match> matches, int patternNodes,
                           int patternEdges, int targetNodes,
                           int targetEdges, boolean hitLimit) {
            this.matches = Collections.unmodifiableList(new ArrayList<>(matches));
            this.patternNodes = patternNodes;
            this.patternEdges = patternEdges;
            this.targetNodes = targetNodes;
            this.targetEdges = targetEdges;
            this.hitLimit = hitLimit;
        }

        /** All matches found. */
        public List<Match> getMatches() {
            return matches;
        }

        /** Number of matches found. */
        public int getMatchCount() {
            return matches.size();
        }

        /** True if the search stopped because maxMatches was reached. */
        public boolean isHitLimit() {
            return hitLimit;
        }

        /**
         * Fraction of target nodes that participate in at least one match.
         */
        public double getCoverage() {
            if (targetNodes == 0) return 0;
            Set<String> covered = new HashSet<>();
            for (Match m : matches) {
                covered.addAll(m.getTargetNodes());
            }
            return (double) covered.size() / targetNodes;
        }

        /**
         * Per-node participation count: how many matches each target node
         * appears in.
         */
        public Map<String, Integer> getNodeParticipation() {
            Map<String, Integer> counts = new HashMap<>();
            for (Match m : matches) {
                for (String node : m.getTargetNodes()) {
                    counts.merge(node, 1, Integer::sum);
                }
            }
            return counts;
        }

        /**
         * Nodes that participate in the most matches (top hubs for the
         * pattern). Returns up to topK entries sorted by count descending.
         */
        public List<Map.Entry<String, Integer>> getTopParticipants(int topK) {
            return getNodeParticipation().entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(topK)
                    .collect(Collectors.toList());
        }

        /**
         * Average overlap between all pairs of matches (shared node count).
         * Returns 0 if fewer than 2 matches.
         */
        public double getAverageOverlap() {
            if (matches.size() < 2) return 0;
            long totalOverlap = 0;
            long pairs = 0;
            for (int i = 0; i < matches.size(); i++) {
                for (int j = i + 1; j < matches.size(); j++) {
                    totalOverlap += matches.get(i).overlapWith(matches.get(j));
                    pairs++;
                }
            }
            return pairs > 0 ? (double) totalOverlap / pairs : 0;
        }

        /**
         * Generate a human-readable summary report.
         */
        public String generateReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("═══ Subgraph Pattern Match Report ═══\n\n");
            sb.append(String.format("Pattern: %d nodes, %d edges%n",
                    patternNodes, patternEdges));
            sb.append(String.format("Target:  %d nodes, %d edges%n",
                    targetNodes, targetEdges));
            sb.append(String.format("Matches: %d%s%n", matches.size(),
                    hitLimit ? " (limit reached)" : ""));
            sb.append(String.format("Coverage: %.1f%% of target nodes%n",
                    getCoverage() * 100));

            if (!matches.isEmpty()) {
                sb.append(String.format("Avg overlap: %.2f shared nodes per pair%n",
                        getAverageOverlap()));

                sb.append("\nTop participants:\n");
                for (Map.Entry<String, Integer> entry :
                        getTopParticipants(10)) {
                    sb.append(String.format("  %s: %d matches%n",
                            entry.getKey(), entry.getValue()));
                }

                sb.append("\nFirst 5 matches:\n");
                for (int i = 0; i < Math.min(5, matches.size()); i++) {
                    sb.append(String.format("  %d. %s%n", i + 1,
                            matches.get(i)));
                }
            }

            return sb.toString();
        }
    }

    // ── Search engine ───────────────────────────────────────────

    /**
     * Find all occurrences of the pattern in the target graph.
     *
     * @return MatchResult with all found matches and statistics
     */
    public MatchResult findMatches() {
        List<Match> matches = new ArrayList<>();

        if (pattern.getVertexCount() > target.getVertexCount() ||
                pattern.getEdgeCount() > target.getEdgeCount()) {
            return new MatchResult(matches,
                    pattern.getVertexCount(), pattern.getEdgeCount(),
                    target.getVertexCount(), target.getEdgeCount(), false);
        }

        // Build adjacency for filtered target
        Map<String, Set<String>> targetAdj = buildTargetAdjacency();
        Map<String, Set<String>> patternAdj = buildPatternAdjacency();

        // Order pattern nodes by descending degree (most constrained first)
        List<String> patternOrder = new ArrayList<>(pattern.getVertices());
        patternOrder.sort((a, b) ->
                Integer.compare(patternAdj.getOrDefault(b, Collections.emptySet()).size(),
                        patternAdj.getOrDefault(a, Collections.emptySet()).size()));

        // Compute degree map for target (with Edge-type filter)
        Map<String, Integer> targetDegree = new HashMap<>();
        for (String v : target.getVertices()) {
            targetDegree.put(v, targetAdj.getOrDefault(v, Collections.emptySet()).size());
        }

        // Compute degree map for pattern
        Map<String, Integer> patternDegree = new HashMap<>();
        for (String v : pattern.getVertices()) {
            patternDegree.put(v, patternAdj.getOrDefault(v, Collections.emptySet()).size());
        }

        // Backtracking search
        Set<Set<String>> seen = new HashSet<>();  // deduplicate by target node set
        Map<String, String> currentMapping = new LinkedHashMap<>();
        Set<String> usedTargetNodes = new HashSet<>();

        backtrack(patternOrder, 0, currentMapping, usedTargetNodes,
                patternAdj, targetAdj, patternDegree, targetDegree,
                matches, seen);

        return new MatchResult(matches,
                pattern.getVertexCount(), pattern.getEdgeCount(),
                target.getVertexCount(), target.getEdgeCount(),
                matches.size() >= maxMatches);
    }

    /**
     * VF2-style backtracking with candidate domain reduction.
     *
     * <p><b>Optimisation:</b> When the current pattern node has at least one
     * already-mapped neighbor in the pattern, its candidate domain is
     * restricted to the target-graph neighbors of that mapped counterpart
     * (intersected across all mapped pattern neighbors when there are several).
     * This reduces the inner loop from O(|V_target|) to O(degree) at each
     * recursive depth after the first, eliminating the dominant cost in
     * dense or large target graphs and providing exponential pruning of the
     * search tree.</p>
     *
     * <p>For the first pattern node (or any pattern node with no yet-mapped
     * neighbors), the full target vertex set is still enumerated, but this
     * occurs at most once in typical connected patterns.</p>
     */
    private void backtrack(List<String> patternOrder, int depth,
                           Map<String, String> mapping,
                           Set<String> usedTargetNodes,
                           Map<String, Set<String>> patternAdj,
                           Map<String, Set<String>> targetAdj,
                           Map<String, Integer> patternDegree,
                           Map<String, Integer> targetDegree,
                           List<Match> matches,
                           Set<Set<String>> seen) {

        if (matches.size() >= maxMatches) return;

        if (depth == patternOrder.size()) {
            // Complete match found
            Set<String> targetSet = new HashSet<>(mapping.values());
            if (seen.add(targetSet)) {
                matches.add(new Match(mapping));
            }
            return;
        }

        String patternNode = patternOrder.get(depth);
        int requiredDegree = patternDegree.getOrDefault(patternNode, 0);

        // Candidate domain reduction: if patternNode has already-mapped
        // neighbors, restrict candidates to target neighbors of those
        // mapped counterparts (intersection across all mapped neighbors).
        Collection<String> candidateDomain = computeCandidateDomain(
                patternNode, mapping, patternAdj, targetAdj);

        for (String candidate : candidateDomain) {
            if (usedTargetNodes.contains(candidate)) continue;

            // Degree pruning
            int candDegree = targetDegree.getOrDefault(candidate, 0);
            if (degreeConstrained && candDegree < requiredDegree) continue;
            // Even without degreeConstrained, adjacency degree in filtered
            // target must be >= pattern degree for the match to succeed
            int filteredDegree = targetAdj.getOrDefault(candidate,
                    Collections.emptySet()).size();
            if (filteredDegree < requiredDegree) continue;

            // Neighbor consistency: for every already-mapped neighbor of
            // patternNode, the candidate must be adjacent to its mapped
            // counterpart in the target
            if (isConsistent(patternNode, candidate, mapping,
                    patternAdj, targetAdj)) {
                mapping.put(patternNode, candidate);
                usedTargetNodes.add(candidate);

                backtrack(patternOrder, depth + 1, mapping,
                        usedTargetNodes, patternAdj, targetAdj,
                        patternDegree, targetDegree, matches, seen);

                mapping.remove(patternNode);
                usedTargetNodes.remove(candidate);
            }
        }
    }

    /**
     * Computes the candidate domain for a pattern node by intersecting
     * the target neighborhoods of all already-mapped pattern neighbors.
     *
     * <p>If the pattern node has no mapped neighbors, returns the full
     * target vertex set. If it has one mapped neighbor, returns that
     * neighbor's target adjacency set directly (no copy). If multiple,
     * returns the intersection of their adjacency sets — starting from
     * the smallest set for minimum iteration.</p>
     *
     * @return candidate target nodes (unmodifiable view or new set)
     */
    private Collection<String> computeCandidateDomain(
            String patternNode,
            Map<String, String> mapping,
            Map<String, Set<String>> patternAdj,
            Map<String, Set<String>> targetAdj) {

        Set<String> patternNeighbors = patternAdj.getOrDefault(patternNode,
                Collections.emptySet());

        // Collect target adjacency sets for all already-mapped pattern neighbors
        List<Set<String>> constraintSets = null;
        for (String pn : patternNeighbors) {
            String mappedTarget = mapping.get(pn);
            if (mappedTarget != null) {
                Set<String> nbrs = targetAdj.getOrDefault(mappedTarget,
                        Collections.emptySet());
                if (constraintSets == null) {
                    constraintSets = new ArrayList<>(4);
                }
                constraintSets.add(nbrs);
            }
        }

        if (constraintSets == null) {
            // No mapped neighbors — must scan all target vertices
            return target.getVertices();
        }

        if (constraintSets.size() == 1) {
            // Single constraint: return its neighbor set directly (no copy)
            return constraintSets.get(0);
        }

        // Multiple constraints: intersect starting from smallest set
        constraintSets.sort(Comparator.comparingInt(Set::size));
        Set<String> domain = new HashSet<>(constraintSets.get(0));
        for (int i = 1; i < constraintSets.size() && !domain.isEmpty(); i++) {
            domain.retainAll(constraintSets.get(i));
        }
        return domain;
    }

    private boolean isConsistent(String patternNode, String candidate,
                                 Map<String, String> mapping,
                                 Map<String, Set<String>> patternAdj,
                                 Map<String, Set<String>> targetAdj) {
        Set<String> patternNeighbors = patternAdj.getOrDefault(patternNode,
                Collections.emptySet());
        Set<String> candidateNeighbors = targetAdj.getOrDefault(candidate,
                Collections.emptySet());

        for (String pn : patternNeighbors) {
            String mappedTarget = mapping.get(pn);
            if (mappedTarget != null) {
                // The mapped node must be a neighbor of candidate
                if (!candidateNeighbors.contains(mappedTarget)) {
                    return false;
                }
            }
        }
        return true;
    }

    // ── Adjacency helpers ───────────────────────────────────────

    private Map<String, Set<String>> buildTargetAdjacency() {
        Map<String, Set<String>> adj = new HashMap<>();
        for (String v : target.getVertices()) {
            adj.put(v, new HashSet<>());
        }
        for (Edge e : target.getEdges()) {
            if (edgeTypeFilter != null &&
                    !edgeTypeFilter.equals(e.getType())) {
                continue;
            }
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            if (v1 != null && v2 != null &&
                    adj.containsKey(v1) && adj.containsKey(v2)) {
                adj.get(v1).add(v2);
                adj.get(v2).add(v1);
            }
        }
        return adj;
    }

    private Map<String, Set<String>> buildPatternAdjacency() {
        Map<String, Set<String>> adj = new HashMap<>();
        for (String v : pattern.getVertices()) {
            adj.put(v, new HashSet<>());
        }
        for (Edge e : pattern.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            if (v1 != null && v2 != null) {
                adj.get(v1).add(v2);
                adj.get(v2).add(v1);
            }
        }
        return adj;
    }

    // ── Built-in pattern factories ──────────────────────────────

    /**
     * Create a triangle pattern (3 mutually connected nodes).
     */
    public static Graph<String, Edge> trianglePattern() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("P0");
        g.addVertex("P1");
        g.addVertex("P2");
        g.addEdge(new Edge(null, "P0", "P1"), "P0", "P1");
        g.addEdge(new Edge(null, "P1", "P2"), "P1", "P2");
        g.addEdge(new Edge(null, "P0", "P2"), "P0", "P2");
        return g;
    }

    /**
     * Create a square/4-cycle pattern (C4).
     */
    public static Graph<String, Edge> squarePattern() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < 4; i++) g.addVertex("P" + i);
        g.addEdge(new Edge(null, "P0", "P1"), "P0", "P1");
        g.addEdge(new Edge(null, "P1", "P2"), "P1", "P2");
        g.addEdge(new Edge(null, "P2", "P3"), "P2", "P3");
        g.addEdge(new Edge(null, "P3", "P0"), "P3", "P0");
        return g;
    }

    /**
     * Create a star pattern with k leaves (one hub connected to k nodes,
     * no edges among leaves).
     *
     * @param k number of leaves (must be ≥ 2)
     */
    public static Graph<String, Edge> starPattern(int k) {
        if (k < 2) throw new IllegalArgumentException("Star needs >= 2 leaves");
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("hub");
        for (int i = 0; i < k; i++) {
            String leaf = "L" + i;
            g.addVertex(leaf);
            g.addEdge(new Edge(null, "hub", leaf), "hub", leaf);
        }
        return g;
    }

    /**
     * Create a path pattern of length n (n+1 nodes in a line).
     *
     * @param n path length (edges), must be ≥ 1
     */
    public static Graph<String, Edge> pathPattern(int n) {
        if (n < 1) throw new IllegalArgumentException("Path length must be >= 1");
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i <= n; i++) g.addVertex("P" + i);
        for (int i = 0; i < n; i++) {
            g.addEdge(new Edge(null, "P" + i, "P" + (i + 1)),
                    "P" + i, "P" + (i + 1));
        }
        return g;
    }

    /**
     * Create a diamond pattern (K4 minus one Edge: 4 nodes, 5 edges).
     * Shape: two triangles sharing an Edge.
     */
    public static Graph<String, Edge> diamondPattern() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < 4; i++) g.addVertex("P" + i);
        // P0-P1, P0-P2, P0-P3, P1-P2, P2-P3 (missing P1-P3)
        g.addEdge(new Edge(null, "P0", "P1"), "P0", "P1");
        g.addEdge(new Edge(null, "P0", "P2"), "P0", "P2");
        g.addEdge(new Edge(null, "P0", "P3"), "P0", "P3");
        g.addEdge(new Edge(null, "P1", "P2"), "P1", "P2");
        g.addEdge(new Edge(null, "P2", "P3"), "P2", "P3");
        return g;
    }

    /**
     * Create a bowtie pattern (two triangles sharing one vertex: 5 nodes,
     * 6 edges).
     */
    public static Graph<String, Edge> bowtiePattern() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < 5; i++) g.addVertex("P" + i);
        // Triangle 1: P0-P1-P2
        g.addEdge(new Edge(null, "P0", "P1"), "P0", "P1");
        g.addEdge(new Edge(null, "P1", "P2"), "P1", "P2");
        g.addEdge(new Edge(null, "P0", "P2"), "P0", "P2");
        // Triangle 2: P0-P3-P4 (P0 is shared)
        g.addEdge(new Edge(null, "P0", "P3"), "P0", "P3");
        g.addEdge(new Edge(null, "P3", "P4"), "P3", "P4");
        g.addEdge(new Edge(null, "P0", "P4"), "P0", "P4");
        return g;
    }

    /**
     * Create a complete graph pattern (K_n).
     *
     * @param n number of nodes (must be ≥ 2)
     */
    public static Graph<String, Edge> completePattern(int n) {
        if (n < 2) throw new IllegalArgumentException("Complete graph needs >= 2 nodes");
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < n; i++) g.addVertex("P" + i);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                g.addEdge(new Edge(null, "P" + i, "P" + j),
                        "P" + i, "P" + j);
            }
        }
        return g;
    }

    /**
     * Create a house pattern (square + triangle on top: 5 nodes, 6 edges).
     */
    public static Graph<String, Edge> housePattern() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < 5; i++) g.addVertex("P" + i);
        // Square base: P0-P1-P2-P3
        g.addEdge(new Edge(null, "P0", "P1"), "P0", "P1");
        g.addEdge(new Edge(null, "P1", "P2"), "P1", "P2");
        g.addEdge(new Edge(null, "P2", "P3"), "P2", "P3");
        g.addEdge(new Edge(null, "P3", "P0"), "P3", "P0");
        // Roof triangle: P2-P4-P3
        g.addEdge(new Edge(null, "P2", "P4"), "P2", "P4");
        g.addEdge(new Edge(null, "P3", "P4"), "P3", "P4");
        return g;
    }
}
