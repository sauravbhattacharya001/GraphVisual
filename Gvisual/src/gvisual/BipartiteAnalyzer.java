package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Bipartite graph analysis — detects bipartiteness via 2-coloring (BFS),
 * computes maximum matching (Hopcroft–Karp algorithm), minimum vertex cover
 * (König's theorem), and maximum independent set.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Bipartiteness test</b> — BFS 2-coloring across all components</li>
 *   <li><b>Two-coloring</b> — assigns vertices to left/right partitions</li>
 *   <li><b>Maximum matching</b> — Hopcroft–Karp O(E√V) algorithm</li>
 *   <li><b>Minimum vertex cover</b> — via König's theorem (complement of
 *       max independent set in bipartite graphs)</li>
 *   <li><b>Maximum independent set</b> — vertices not in min vertex cover</li>
 *   <li><b>Odd cycle detection</b> — finds an odd cycle witness when the
 *       graph is not bipartite</li>
 * </ul>
 *
 * <p>Applications include task assignment, scheduling, network routing,
 * and social network analysis (e.g., two-mode networks).</p>
 *
 * @author zalenix
 */
public class BipartiteAnalyzer {

    private static final int UNCOLORED = -1;
    private static final int LEFT = 0;
    private static final int RIGHT = 1;
    private static final String NIL = "__NIL__";
    private static final int INF = Integer.MAX_VALUE;

    private final Graph<String, Edge> graph;
    private Map<String, Integer> coloring;
    private boolean bipartite;
    private boolean computed;
    private List<String> oddCycle;

    /**
     * Creates a new BipartiteAnalyzer for the given graph.
     *
     * @param graph the JUNG graph to analyze
     * @throws IllegalArgumentException if graph is null
     */
    public BipartiteAnalyzer(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.coloring = new LinkedHashMap<String, Integer>();
        this.bipartite = false;
        this.computed = false;
        this.oddCycle = null;
    }

    // ── Bipartiteness test (BFS 2-coloring) ────────────────────────

    /**
     * Runs the bipartiteness test. Idempotent — repeated calls are no-ops.
     *
     * @return this analyzer for chaining
     */
    public BipartiteAnalyzer compute() {
        if (computed) return this;

        Collection<String> vertices = graph.getVertices();
        if (vertices.isEmpty()) {
            bipartite = true;
            computed = true;
            return this;
        }

        Map<String, Integer> color = new LinkedHashMap<String, Integer>();
        Map<String, String> parent = new HashMap<String, String>();
        for (String v : vertices) {
            color.put(v, UNCOLORED);
        }

        bipartite = true;

        for (String start : vertices) {
            if (color.get(start) != UNCOLORED) continue;

            color.put(start, LEFT);
            parent.put(start, null);
            Queue<String> queue = new LinkedList<String>();
            queue.add(start);

            while (!queue.isEmpty() && bipartite) {
                String v = queue.poll();
                int vColor = color.get(v);

                for (String u : graph.getNeighbors(v)) {
                    if (color.get(u) == UNCOLORED) {
                        color.put(u, 1 - vColor);
                        parent.put(u, v);
                        queue.add(u);
                    } else if (color.get(u) == vColor) {
                        bipartite = false;
                        // Build odd cycle witness
                        oddCycle = buildOddCycle(v, u, parent);
                    }
                }
            }

            if (!bipartite) break;
        }

        this.coloring = color;
        this.computed = true;
        return this;
    }

    private List<String> buildOddCycle(String v, String u, Map<String, String> parent) {
        List<String> pathV = new ArrayList<String>();
        List<String> pathU = new ArrayList<String>();

        String a = v;
        while (a != null) {
            pathV.add(a);
            a = parent.get(a);
        }
        String b = u;
        while (b != null) {
            pathU.add(b);
            b = parent.get(b);
        }

        // Find lowest common ancestor
        Set<String> ancestorsV = new HashSet<String>(pathV);
        String lca = null;
        for (String x : pathU) {
            if (ancestorsV.contains(x)) {
                lca = x;
                break;
            }
        }

        List<String> cycle = new ArrayList<String>();
        // Path from v to LCA
        for (String x : pathV) {
            cycle.add(x);
            if (x.equals(lca)) break;
        }
        // Path from u to LCA (reversed, excluding LCA)
        List<String> uToLca = new ArrayList<String>();
        for (String x : pathU) {
            if (x.equals(lca)) break;
            uToLca.add(x);
        }
        Collections.reverse(uToLca);
        cycle.addAll(uToLca);

        return cycle;
    }

    // ── Accessors ──────────────────────────────────────────────────

    /**
     * Returns whether the graph is bipartite.
     *
     * @return true if bipartite
     */
    public boolean isBipartite() {
        ensureComputed();
        return bipartite;
    }

    /**
     * Returns the 2-coloring: vertex → 0 (left) or 1 (right).
     * Only meaningful if the graph is bipartite.
     *
     * @return unmodifiable coloring map
     */
    public Map<String, Integer> getColoring() {
        ensureComputed();
        return Collections.unmodifiableMap(coloring);
    }

    /**
     * Returns the left partition (color 0).
     *
     * @return sorted list of left-partition vertices
     */
    public List<String> getLeftPartition() {
        ensureComputed();
        return getPartition(LEFT);
    }

    /**
     * Returns the right partition (color 1).
     *
     * @return sorted list of right-partition vertices
     */
    public List<String> getRightPartition() {
        ensureComputed();
        return getPartition(RIGHT);
    }

    private List<String> getPartition(int side) {
        List<String> result = new ArrayList<String>();
        for (Map.Entry<String, Integer> e : coloring.entrySet()) {
            if (e.getValue() == side) {
                result.add(e.getKey());
            }
        }
        Collections.sort(result);
        return result;
    }

    /**
     * Returns an odd cycle witness if the graph is NOT bipartite.
     *
     * @return list of vertices forming an odd cycle, or null if bipartite
     */
    public List<String> getOddCycle() {
        ensureComputed();
        return oddCycle != null ? Collections.unmodifiableList(oddCycle) : null;
    }

    // ── Maximum Matching (Hopcroft–Karp) ───────────────────────────

    /**
     * Represents a matching edge between two vertices.
     */
    public static class MatchingEdge {
        private final String left;
        private final String right;

        public MatchingEdge(String left, String right) {
            this.left = left;
            this.right = right;
        }

        public String getLeft() { return left; }
        public String getRight() { return right; }

        @Override
        public String toString() {
            return left + " — " + right;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MatchingEdge)) return false;
            MatchingEdge that = (MatchingEdge) o;
            return left.equals(that.left) && right.equals(that.right);
        }

        @Override
        public int hashCode() {
            return 31 * left.hashCode() + right.hashCode();
        }
    }

    /**
     * Computes the maximum matching using Hopcroft–Karp algorithm.
     * Only valid for bipartite graphs.
     *
     * @return list of matching edges
     * @throws IllegalStateException if the graph is not bipartite
     */
    public List<MatchingEdge> getMaximumMatching() {
        ensureComputed();
        if (!bipartite) {
            throw new IllegalStateException(
                    "Maximum matching (Hopcroft-Karp) requires a bipartite graph");
        }

        List<String> leftVerts = getLeftPartition();
        List<String> rightVerts = getRightPartition();

        // Build adjacency for left vertices to right vertices
        Map<String, List<String>> adj = new HashMap<String, List<String>>();
        for (String l : leftVerts) {
            List<String> neighbors = new ArrayList<String>();
            for (String n : graph.getNeighbors(l)) {
                if (coloring.get(n) == RIGHT) {
                    neighbors.add(n);
                }
            }
            adj.put(l, neighbors);
        }

        Map<String, String> matchL = new HashMap<String, String>();
        Map<String, String> matchR = new HashMap<String, String>();
        Map<String, Integer> dist = new HashMap<String, Integer>();

        for (String l : leftVerts) matchL.put(l, NIL);
        for (String r : rightVerts) matchR.put(r, NIL);

        // Hopcroft-Karp main loop
        while (bfs(leftVerts, adj, matchL, matchR, dist)) {
            for (String l : leftVerts) {
                if (matchL.get(l).equals(NIL)) {
                    dfs(l, adj, matchL, matchR, dist);
                }
            }
        }

        // Collect matching edges
        List<MatchingEdge> matching = new ArrayList<MatchingEdge>();
        for (String l : leftVerts) {
            String r = matchL.get(l);
            if (!r.equals(NIL)) {
                matching.add(new MatchingEdge(l, r));
            }
        }

        return matching;
    }

    private boolean bfs(List<String> leftVerts, Map<String, List<String>> adj,
                        Map<String, String> matchL, Map<String, String> matchR,
                        Map<String, Integer> dist) {
        Queue<String> queue = new LinkedList<String>();

        for (String l : leftVerts) {
            if (matchL.get(l).equals(NIL)) {
                dist.put(l, 0);
                queue.add(l);
            } else {
                dist.put(l, INF);
            }
        }
        dist.put(NIL, INF);

        while (!queue.isEmpty()) {
            String l = queue.poll();
            if (dist.get(l) < dist.get(NIL)) {
                List<String> neighbors = adj.get(l);
                if (neighbors != null) {
                    for (String r : neighbors) {
                        String pairR = matchR.get(r);
                        if (pairR == null) pairR = NIL;
                        Integer pairDist = dist.get(pairR);
                        if (pairDist == null || pairDist == INF) {
                            dist.put(pairR, dist.get(l) + 1);
                            if (!pairR.equals(NIL)) {
                                queue.add(pairR);
                            }
                        }
                    }
                }
            }
        }

        return dist.get(NIL) != INF;
    }

    private boolean dfs(String l, Map<String, List<String>> adj,
                        Map<String, String> matchL, Map<String, String> matchR,
                        Map<String, Integer> dist) {
        if (!l.equals(NIL)) {
            List<String> neighbors = adj.get(l);
            if (neighbors != null) {
                for (String r : neighbors) {
                    String pairR = matchR.get(r);
                    if (pairR == null) pairR = NIL;
                    Integer pairDist = dist.get(pairR);
                    if (pairDist != null && pairDist == dist.get(l) + 1) {
                        if (dfs(pairR, adj, matchL, matchR, dist)) {
                            matchR.put(r, l);
                            matchL.put(l, r);
                            return true;
                        }
                    }
                }
            }
            dist.put(l, INF);
            return false;
        }
        return true;
    }

    /**
     * Returns the size of the maximum matching.
     *
     * @return number of matched edges
     * @throws IllegalStateException if the graph is not bipartite
     */
    public int getMatchingSize() {
        return getMaximumMatching().size();
    }

    // ── Minimum Vertex Cover (König's theorem) ─────────────────────

    /**
     * Computes the minimum vertex cover using König's theorem:
     * In a bipartite graph, |min vertex cover| = |max matching|.
     *
     * <p>Uses alternating path BFS from unmatched left vertices to
     * identify the cover set.</p>
     *
     * @return sorted list of vertices in the minimum vertex cover
     * @throws IllegalStateException if the graph is not bipartite
     */
    public List<String> getMinimumVertexCover() {
        ensureComputed();
        if (!bipartite) {
            throw new IllegalStateException(
                    "Minimum vertex cover (König) requires a bipartite graph");
        }

        List<MatchingEdge> matching = getMaximumMatching();
        List<String> leftVerts = getLeftPartition();
        List<String> rightVerts = getRightPartition();

        // Build matched-partner maps
        Map<String, String> matchL = new HashMap<String, String>();
        Map<String, String> matchR = new HashMap<String, String>();
        for (MatchingEdge me : matching) {
            matchL.put(me.getLeft(), me.getRight());
            matchR.put(me.getRight(), me.getLeft());
        }

        // Find unmatched left vertices
        Set<String> unmatchedLeft = new LinkedHashSet<String>();
        for (String l : leftVerts) {
            if (!matchL.containsKey(l)) {
                unmatchedLeft.add(l);
            }
        }

        // BFS alternating paths from unmatched left vertices
        // Alternate: unmatched edge to right, matched edge back to left
        Set<String> visitedL = new LinkedHashSet<String>(unmatchedLeft);
        Set<String> visitedR = new LinkedHashSet<String>();
        Queue<String> queue = new LinkedList<String>(unmatchedLeft);

        while (!queue.isEmpty()) {
            String l = queue.poll();
            // Follow unmatched edges to right side
            for (String n : graph.getNeighbors(l)) {
                if (coloring.get(n) == RIGHT && !visitedR.contains(n)) {
                    // Only follow if this edge is NOT in the matching
                    if (!n.equals(matchL.get(l))) {
                        visitedR.add(n);
                        // Follow matched edge back to left
                        String partner = matchR.get(n);
                        if (partner != null && !visitedL.contains(partner)) {
                            visitedL.add(partner);
                            queue.add(partner);
                        }
                    }
                }
            }
        }

        // König's theorem: cover = (L \ visitedL) ∪ (R ∩ visitedR)
        Set<String> cover = new LinkedHashSet<String>();
        for (String l : leftVerts) {
            if (!visitedL.contains(l)) {
                cover.add(l);
            }
        }
        for (String r : rightVerts) {
            if (visitedR.contains(r)) {
                cover.add(r);
            }
        }

        List<String> result = new ArrayList<String>(cover);
        Collections.sort(result);
        return result;
    }

    // ── Maximum Independent Set ────────────────────────────────────

    /**
     * Computes the maximum independent set as the complement of the
     * minimum vertex cover.
     *
     * @return sorted list of vertices in the maximum independent set
     * @throws IllegalStateException if the graph is not bipartite
     */
    public List<String> getMaximumIndependentSet() {
        ensureComputed();
        if (!bipartite) {
            throw new IllegalStateException(
                    "Maximum independent set requires a bipartite graph");
        }

        Set<String> cover = new HashSet<String>(getMinimumVertexCover());
        List<String> independent = new ArrayList<String>();
        for (String v : graph.getVertices()) {
            if (!cover.contains(v)) {
                independent.add(v);
            }
        }
        Collections.sort(independent);
        return independent;
    }

    // ── Analytics ──────────────────────────────────────────────────

    /**
     * Computes the balance ratio of the two partitions.
     * A perfectly balanced bipartite graph has ratio 1.0.
     *
     * @return ratio of smaller partition to larger partition, or 0 for empty graphs
     * @throws IllegalStateException if the graph is not bipartite
     */
    public double getPartitionBalance() {
        ensureComputed();
        if (!bipartite) {
            throw new IllegalStateException("Partition balance requires a bipartite graph");
        }
        int leftSize = getLeftPartition().size();
        int rightSize = getRightPartition().size();
        if (leftSize == 0 && rightSize == 0) return 0.0;
        int maxSize = Math.max(leftSize, rightSize);
        int minSize = Math.min(leftSize, rightSize);
        return (double) minSize / maxSize;
    }

    /**
     * Computes edge density of the bipartite graph.
     * For bipartite graphs, max edges = |L| × |R|, so density = E / (|L| × |R|).
     *
     * @return density in [0, 1], or 0 for trivial cases
     * @throws IllegalStateException if the graph is not bipartite
     */
    public double getBipartiteDensity() {
        ensureComputed();
        if (!bipartite) {
            throw new IllegalStateException("Bipartite density requires a bipartite graph");
        }
        int leftSize = getLeftPartition().size();
        int rightSize = getRightPartition().size();
        if (leftSize == 0 || rightSize == 0) return 0.0;
        return (double) graph.getEdgeCount() / ((long) leftSize * rightSize);
    }

    /**
     * Computes matching coverage: fraction of vertices that are matched.
     *
     * @return coverage in [0, 1]
     * @throws IllegalStateException if the graph is not bipartite
     */
    public double getMatchingCoverage() {
        ensureComputed();
        if (!bipartite) {
            throw new IllegalStateException("Matching coverage requires a bipartite graph");
        }
        int n = graph.getVertexCount();
        if (n == 0) return 0.0;
        return (2.0 * getMatchingSize()) / n;
    }

    /**
     * Checks if the graph has a perfect matching (every vertex is matched).
     *
     * @return true if a perfect matching exists
     * @throws IllegalStateException if the graph is not bipartite
     */
    public boolean hasPerfectMatching() {
        ensureComputed();
        if (!bipartite) {
            throw new IllegalStateException("Perfect matching check requires a bipartite graph");
        }
        int leftSize = getLeftPartition().size();
        int rightSize = getRightPartition().size();
        if (leftSize != rightSize) return false;
        return getMatchingSize() == leftSize;
    }

    // ── Result object ──────────────────────────────────────────────

    /**
     * Comprehensive result of the bipartite analysis.
     */
    public static class BipartiteResult {
        private final boolean bipartite;
        private final int vertexCount;
        private final int edgeCount;
        private final int leftSize;
        private final int rightSize;
        private final double partitionBalance;
        private final double bipartiteDensity;
        private final int matchingSize;
        private final double matchingCoverage;
        private final boolean perfectMatching;
        private final int vertexCoverSize;
        private final int independentSetSize;
        private final List<MatchingEdge> matching;
        private final List<String> vertexCover;
        private final List<String> independentSet;
        private final List<String> oddCycle;

        public BipartiteResult(boolean bipartite, int vertexCount, int edgeCount,
                               int leftSize, int rightSize, double partitionBalance,
                               double bipartiteDensity, int matchingSize,
                               double matchingCoverage, boolean perfectMatching,
                               int vertexCoverSize, int independentSetSize,
                               List<MatchingEdge> matching, List<String> vertexCover,
                               List<String> independentSet, List<String> oddCycle) {
            this.bipartite = bipartite;
            this.vertexCount = vertexCount;
            this.edgeCount = edgeCount;
            this.leftSize = leftSize;
            this.rightSize = rightSize;
            this.partitionBalance = partitionBalance;
            this.bipartiteDensity = bipartiteDensity;
            this.matchingSize = matchingSize;
            this.matchingCoverage = matchingCoverage;
            this.perfectMatching = perfectMatching;
            this.vertexCoverSize = vertexCoverSize;
            this.independentSetSize = independentSetSize;
            this.matching = matching;
            this.vertexCover = vertexCover;
            this.independentSet = independentSet;
            this.oddCycle = oddCycle;
        }

        public boolean isBipartite() { return bipartite; }
        public int getVertexCount() { return vertexCount; }
        public int getEdgeCount() { return edgeCount; }
        public int getLeftSize() { return leftSize; }
        public int getRightSize() { return rightSize; }
        public double getPartitionBalance() { return partitionBalance; }
        public double getBipartiteDensity() { return bipartiteDensity; }
        public int getMatchingSize() { return matchingSize; }
        public double getMatchingCoverage() { return matchingCoverage; }
        public boolean hasPerfectMatching() { return perfectMatching; }
        public int getVertexCoverSize() { return vertexCoverSize; }
        public int getIndependentSetSize() { return independentSetSize; }
        public List<MatchingEdge> getMatching() { return matching; }
        public List<String> getVertexCover() { return vertexCover; }
        public List<String> getIndependentSet() { return independentSet; }
        public List<String> getOddCycle() { return oddCycle; }
    }

    /**
     * Returns a comprehensive result object with all bipartite analysis data.
     *
     * @return BipartiteResult with all metrics
     */
    public BipartiteResult getResult() {
        ensureComputed();
        if (!bipartite) {
            return new BipartiteResult(false, graph.getVertexCount(),
                    graph.getEdgeCount(), 0, 0, 0, 0, 0, 0, false,
                    0, 0, null, null, null, oddCycle);
        }
        List<MatchingEdge> matching = getMaximumMatching();
        List<String> cover = getMinimumVertexCover();
        List<String> independent = getMaximumIndependentSet();
        return new BipartiteResult(true, graph.getVertexCount(),
                graph.getEdgeCount(), getLeftPartition().size(),
                getRightPartition().size(), getPartitionBalance(),
                getBipartiteDensity(), matching.size(),
                getMatchingCoverage(), hasPerfectMatching(),
                cover.size(), independent.size(),
                matching, cover, independent, null);
    }

    // ── Summary ────────────────────────────────────────────────────

    /**
     * Returns a formatted multi-line summary of the bipartite analysis.
     *
     * @return human-readable summary string
     */
    public String getSummary() {
        ensureComputed();
        StringBuilder sb = new StringBuilder();
        sb.append("=== Bipartite Graph Analysis ===\n");
        sb.append(String.format("Vertices: %d | Edges: %d\n",
                graph.getVertexCount(), graph.getEdgeCount()));
        sb.append(String.format("Bipartite: %s\n", bipartite ? "YES" : "NO"));

        if (!bipartite) {
            if (oddCycle != null) {
                sb.append(String.format("Odd cycle witness: %s\n", oddCycle));
            }
            return sb.toString();
        }

        List<String> left = getLeftPartition();
        List<String> right = getRightPartition();
        sb.append(String.format("\n--- Partitions ---\n"));
        sb.append(String.format("  Left (%d):  %s\n", left.size(), left));
        sb.append(String.format("  Right (%d): %s\n", right.size(), right));
        sb.append(String.format("  Balance: %.4f\n", getPartitionBalance()));
        sb.append(String.format("  Bipartite density: %.4f\n", getBipartiteDensity()));

        List<MatchingEdge> matching = getMaximumMatching();
        sb.append(String.format("\n--- Maximum Matching (%d edges) ---\n", matching.size()));
        for (MatchingEdge me : matching) {
            sb.append(String.format("  %s\n", me));
        }
        sb.append(String.format("  Coverage: %.1f%%\n", getMatchingCoverage() * 100));
        sb.append(String.format("  Perfect matching: %s\n", hasPerfectMatching() ? "YES" : "NO"));

        List<String> cover = getMinimumVertexCover();
        sb.append(String.format("\n--- Minimum Vertex Cover (%d vertices) ---\n", cover.size()));
        sb.append(String.format("  %s\n", cover));

        List<String> independent = getMaximumIndependentSet();
        sb.append(String.format("\n--- Maximum Independent Set (%d vertices) ---\n", independent.size()));
        sb.append(String.format("  %s\n", independent));

        return sb.toString();
    }

    // ── Internals ──────────────────────────────────────────────────

    private void ensureComputed() {
        if (!computed) compute();
    }
}
