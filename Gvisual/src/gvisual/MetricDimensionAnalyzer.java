package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Metric Dimension Analyzer — computes the <b>metric dimension</b> of an
 * undirected graph, which is the minimum number of vertices needed to
 * uniquely identify every vertex by its distances to the chosen set.
 *
 * <h3>Background</h3>
 * <p>A <b>resolving set</b> S of a graph G is a subset of vertices such that
 * for every pair of distinct vertices u, v ∈ V(G), there exists at least one
 * vertex w ∈ S where d(u, w) ≠ d(v, w).  The <b>metric dimension</b> β(G)
 * is the minimum cardinality of a resolving set.</p>
 *
 * <h3>Applications</h3>
 * <ul>
 *   <li><b>Robot navigation</b> — minimum landmarks for unique localization</li>
 *   <li><b>Network monitoring</b> — minimum sensors to identify all nodes</li>
 *   <li><b>Pharmaceutical chemistry</b> — uniquely identifying molecular structures</li>
 *   <li><b>Combinatorial optimization</b> — coin-weighing, Mastermind strategies</li>
 * </ul>
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Metric dimension</b> — exact computation via branch-and-bound
 *       with pruning (feasible for small–medium graphs, ≤ 30 vertices)</li>
 *   <li><b>Greedy resolving set</b> — heuristic for larger graphs; picks
 *       vertices that resolve the most unresolved pairs</li>
 *   <li><b>Distance matrix</b> — all-pairs shortest path (BFS)</li>
 *   <li><b>Metric representation</b> — each vertex's distance vector
 *       with respect to a resolving set</li>
 *   <li><b>Resolving neighborhoods</b> — for each vertex, which pairs
 *       it resolves</li>
 *   <li><b>Landmark-based identification</b> — given a resolving set,
 *       identify any vertex from its distance vector</li>
 *   <li><b>Known bounds</b> — lower/upper bounds from graph properties
 *       (diameter, twins, degrees)</li>
 *   <li><b>Twin vertices</b> — vertices with identical open or closed
 *       neighborhoods; twin classes form a barrier to small resolving sets</li>
 *   <li><b>Text summary</b> — formatted multi-line report</li>
 * </ul>
 *
 * <h3>Known Results</h3>
 * <ul>
 *   <li>Path Pₙ (n ≥ 2): β = 1</li>
 *   <li>Cycle Cₙ (n ≥ 3): β = 2</li>
 *   <li>Complete graph Kₙ (n ≥ 2): β = n − 1</li>
 *   <li>Complete bipartite K_{a,b} (a, b ≥ 2): β = a + b − 2</li>
 *   <li>Petersen graph: β = 3</li>
 *   <li>Tree (not a path): β = number of leaves − number of exterior
 *       major vertices</li>
 * </ul>
 *
 * @author zalenix
 */
public class MetricDimensionAnalyzer {

    private final Graph<String, edge> graph;
    private List<String> vertices;
    private int n;
    private int[][] dist;
    private boolean distComputed;

    // Cached results
    private int metricDimension = -1;
    private List<String> optimalResolvingSet;
    private List<String> greedyResolvingSet;
    private List<Set<String>> twinClasses;
    private boolean twinsComputed;

    /** Maximum vertex count for exact computation. */
    private static final int EXACT_LIMIT = 30;

    public MetricDimensionAnalyzer(Graph<String, edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        Collection<String> verts = graph.getVertices();
        this.vertices = (verts != null) ? new ArrayList<String>(verts)
                                        : new ArrayList<String>();
        Collections.sort(this.vertices);
        this.n = vertices.size();
        this.distComputed = false;
        this.twinsComputed = false;
    }

    // ── Distance Matrix (BFS) ───────────────────────────────────────

    private void ensureDistComputed() {
        if (distComputed) return;
        dist = new int[n][n];
        Map<String, Integer> indexOf = new HashMap<String, Integer>();
        for (int i = 0; i < n; i++) {
            indexOf.put(vertices.get(i), i);
        }
        for (int i = 0; i < n; i++) {
            Arrays.fill(dist[i], -1);
            dist[i][i] = 0;
            Queue<Integer> q = new LinkedList<Integer>();
            q.add(i);
            while (!q.isEmpty()) {
                int u = q.poll();
                String uv = vertices.get(u);
                Collection<String> nbrs = graph.getNeighbors(uv);
                if (nbrs == null) continue;
                for (String nv : nbrs) {
                    int ni = indexOf.get(nv);
                    if (dist[i][ni] < 0) {
                        dist[i][ni] = dist[i][u] + 1;
                        q.add(ni);
                    }
                }
            }
        }
        distComputed = true;
    }

    /**
     * Get the all-pairs distance matrix.
     * Entry [i][j] = shortest-path distance between vertex i and j,
     * or -1 if unreachable.
     */
    public int[][] getDistanceMatrix() {
        ensureDistComputed();
        int[][] copy = new int[n][n];
        for (int i = 0; i < n; i++) {
            copy[i] = Arrays.copyOf(dist[i], n);
        }
        return copy;
    }

    /**
     * Get the ordered list of vertex names (row/column order for the
     * distance matrix).
     */
    public List<String> getVertexOrder() {
        return Collections.unmodifiableList(vertices);
    }

    // ── Twin Detection ──────────────────────────────────────────────

    private void ensureTwinsComputed() {
        if (twinsComputed) return;
        twinClasses = new ArrayList<Set<String>>();
        boolean[] assigned = new boolean[n];

        for (int i = 0; i < n; i++) {
            if (assigned[i]) continue;
            Set<String> cls = new LinkedHashSet<String>();
            cls.add(vertices.get(i));
            Collection<String> rawNbrI = graph.getNeighbors(vertices.get(i));
            Set<String> nbrI = (rawNbrI != null) ? new HashSet<String>(rawNbrI)
                                                  : new HashSet<String>();

            for (int j = i + 1; j < n; j++) {
                if (assigned[j]) continue;
                Collection<String> rawNbrJ = graph.getNeighbors(vertices.get(j));
                Set<String> nbrJ = (rawNbrJ != null) ? new HashSet<String>(rawNbrJ)
                                                      : new HashSet<String>();
                // Open twins: N(u) = N(v) (non-adjacent with same neighbors)
                // Closed twins: N[u] = N[v] (adjacent with same closed neighborhood)
                boolean openTwin = nbrI.equals(nbrJ) &&
                    !graph.isNeighbor(vertices.get(i), vertices.get(j));
                Set<String> closedI = new HashSet<String>(nbrI);
                closedI.add(vertices.get(i));
                Set<String> closedJ = new HashSet<String>(nbrJ);
                closedJ.add(vertices.get(j));
                boolean closedTwin = closedI.equals(closedJ);
                if (openTwin || closedTwin) {
                    cls.add(vertices.get(j));
                    assigned[j] = true;
                }
            }
            assigned[i] = true;
            if (cls.size() > 1) {
                twinClasses.add(cls);
            }
        }
        twinsComputed = true;
    }

    /**
     * Get twin classes — groups of vertices with identical open or closed
     * neighborhoods.  Only returns classes of size ≥ 2.
     */
    public List<Set<String>> getTwinClasses() {
        ensureTwinsComputed();
        List<Set<String>> result = new ArrayList<Set<String>>();
        for (Set<String> cls : twinClasses) {
            result.add(new LinkedHashSet<String>(cls));
        }
        return result;
    }

    /**
     * Check if two vertices are twins (open or closed).
     */
    public boolean areTwins(String u, String v) {
        ensureTwinsComputed();
        for (Set<String> cls : twinClasses) {
            if (cls.contains(u) && cls.contains(v)) return true;
        }
        return false;
    }

    // ── Resolving Set Check ─────────────────────────────────────────

    /**
     * Check if a given set of vertices is a resolving set.
     */
    public boolean isResolvingSet(Collection<String> candidates) {
        ensureDistComputed();
        if (candidates == null || candidates.isEmpty()) {
            return n <= 1;
        }
        List<Integer> idx = new ArrayList<Integer>();
        for (String c : candidates) {
            int i = vertices.indexOf(c);
            if (i < 0) return false;
            idx.add(i);
        }
        // Check that all vertex pairs have distinct distance vectors
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                boolean resolved = false;
                for (int w : idx) {
                    if (dist[i][w] != dist[j][w]) {
                        resolved = true;
                        break;
                    }
                }
                if (!resolved) return false;
            }
        }
        return true;
    }

    // ── Metric Representation ───────────────────────────────────────

    /**
     * Compute the metric representation (distance vector) of each vertex
     * with respect to a given resolving set.
     *
     * @return map from vertex to its distance vector
     */
    public Map<String, int[]> getMetricRepresentation(List<String> resolvingSet) {
        ensureDistComputed();
        Map<String, int[]> repr = new LinkedHashMap<String, int[]>();
        int[] idx = new int[resolvingSet.size()];
        for (int k = 0; k < resolvingSet.size(); k++) {
            idx[k] = vertices.indexOf(resolvingSet.get(k));
        }
        for (int i = 0; i < n; i++) {
            int[] vec = new int[resolvingSet.size()];
            for (int k = 0; k < idx.length; k++) {
                vec[k] = idx[k] >= 0 ? dist[i][idx[k]] : -1;
            }
            repr.put(vertices.get(i), vec);
        }
        return repr;
    }

    /**
     * Given a resolving set and a distance vector, identify which vertex
     * it corresponds to.  Returns null if no match.
     */
    public String identifyVertex(List<String> resolvingSet, int[] distanceVector) {
        Map<String, int[]> repr = getMetricRepresentation(resolvingSet);
        for (Map.Entry<String, int[]> entry : repr.entrySet()) {
            if (Arrays.equals(entry.getValue(), distanceVector)) {
                return entry.getKey();
            }
        }
        return null;
    }

    // ── Bounds ──────────────────────────────────────────────────────

    /**
     * Compute a lower bound on the metric dimension.
     * Uses: pigeonhole on distance vectors and twin bounds.
     */
    public int getLowerBound() {
        if (n <= 1) return 0;

        // Pigeonhole: with k landmarks, at most (diam+1)^k distinct
        // distance vectors.  Need at least n distinct → k ≥ ⌈log_{diam+1}(n)⌉
        ensureDistComputed();
        int diam = 0;
        boolean connected = true;
        for (int i = 0; i < n && connected; i++) {
            for (int j = i + 1; j < n; j++) {
                if (dist[i][j] < 0) { connected = false; break; }
                diam = Math.max(diam, dist[i][j]);
            }
        }
        int lb = 1;
        if (connected && diam > 0) {
            // k ≥ ⌈log_{diam+1}(n)⌉
            lb = (int) Math.ceil(Math.log(n) / Math.log(diam + 1));
        }

        // Twin bound: from each twin class of size t, at least t−1 must
        // be in any resolving set
        ensureTwinsComputed();
        int twinLB = 0;
        for (Set<String> cls : twinClasses) {
            twinLB += cls.size() - 1;
        }
        lb = Math.max(lb, twinLB);

        return Math.max(1, lb);
    }

    /**
     * Compute an upper bound on the metric dimension.
     * Uses: n − 1 (always works), and n − diameter (Chartrand et al.).
     */
    public int getUpperBound() {
        if (n <= 1) return 0;
        int ub = n - 1;

        // For connected graphs: β ≤ n − diam
        ensureDistComputed();
        int diam = 0;
        boolean connected = true;
        for (int i = 0; i < n && connected; i++) {
            for (int j = i + 1; j < n; j++) {
                if (dist[i][j] < 0) {
                    connected = false;
                    break;
                }
                diam = Math.max(diam, dist[i][j]);
            }
        }
        if (connected && diam > 0) {
            ub = Math.min(ub, n - diam);
        }
        return ub;
    }

    // ── Greedy Resolving Set ────────────────────────────────────────

    /**
     * Compute a resolving set using a greedy heuristic.
     * At each step, picks the vertex that resolves the most
     * currently-unresolved pairs.
     */
    public List<String> getGreedyResolvingSet() {
        if (greedyResolvingSet != null) return new ArrayList<String>(greedyResolvingSet);
        ensureDistComputed();

        if (n <= 1) {
            greedyResolvingSet = new ArrayList<String>();
            return new ArrayList<String>(greedyResolvingSet);
        }

        // Track unresolved pairs
        Set<Long> unresolved = new HashSet<Long>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                unresolved.add(pairKey(i, j));
            }
        }

        List<Integer> chosen = new ArrayList<Integer>();
        boolean[] used = new boolean[n];

        while (!unresolved.isEmpty()) {
            // Find vertex that resolves the most unresolved pairs
            int bestV = -1;
            int bestCount = -1;
            for (int v = 0; v < n; v++) {
                if (used[v]) continue;
                int count = 0;
                for (long pk : unresolved) {
                    int i = (int) (pk >> 32);
                    int j = (int) (pk & 0xFFFFFFFFL);
                    if (dist[i][v] != dist[j][v]) count++;
                }
                if (count > bestCount) {
                    bestCount = count;
                    bestV = v;
                }
            }
            if (bestV < 0) break;
            chosen.add(bestV);
            used[bestV] = true;

            // Remove newly resolved pairs
            Iterator<Long> it = unresolved.iterator();
            while (it.hasNext()) {
                long pk = it.next();
                int i = (int) (pk >> 32);
                int j = (int) (pk & 0xFFFFFFFFL);
                if (dist[i][bestV] != dist[j][bestV]) it.remove();
            }
        }

        greedyResolvingSet = new ArrayList<String>();
        for (int idx : chosen) {
            greedyResolvingSet.add(vertices.get(idx));
        }
        return new ArrayList<String>(greedyResolvingSet);
    }

    private static long pairKey(int i, int j) {
        return ((long) Math.min(i, j) << 32) | Math.max(i, j);
    }

    // ── Exact Metric Dimension (Branch and Bound) ───────────────────

    /**
     * Compute the exact metric dimension using branch-and-bound.
     * Only feasible for graphs with ≤ {@value #EXACT_LIMIT} vertices.
     *
     * @throws IllegalStateException if graph has more than EXACT_LIMIT vertices
     */
    public int getMetricDimension() {
        if (metricDimension >= 0) return metricDimension;
        if (n == 0) {
            metricDimension = 0;
            optimalResolvingSet = new ArrayList<String>();
            return 0;
        }
        if (n == 1) {
            metricDimension = 0;
            optimalResolvingSet = new ArrayList<String>();
            return 0;
        }
        if (n > EXACT_LIMIT) {
            throw new IllegalStateException(
                "Exact metric dimension only for graphs with ≤ " + EXACT_LIMIT +
                " vertices (this graph has " + n + "). Use getGreedyResolvingSet() instead.");
        }
        ensureDistComputed();

        // Precompute which vertex resolves which pairs
        long totalPairsL = (long) n * (n - 1) / 2;
        if (totalPairsL > Integer.MAX_VALUE) {
            // Graph too large for BitSet-based metric dimension
            metricDimension = -1;
            return metricDimension;
        }
        int totalPairs = (int) totalPairsL;
        // Store pairs as bit sets for fast intersection
        BitSet[] resolves = new BitSet[n];
        int pairIdx = 0;
        int[][] pairMap = new int[n][n];
        for (int i = 0; i < n; i++) {
            resolves[i] = new BitSet(totalPairs);
            for (int j = i + 1; j < n; j++) {
                pairMap[i][j] = pairIdx++;
            }
        }
        for (int v = 0; v < n; v++) {
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (dist[i][v] != dist[j][v]) {
                        resolves[v].set(pairMap[i][j]);
                    }
                }
            }
        }

        // Lower bound from getLowerBound
        int lb = getLowerBound();

        // Upper bound from greedy
        List<String> greedy = getGreedyResolvingSet();
        int ub = greedy.size();

        // Try sizes from lb to ub
        for (int size = lb; size <= ub; size++) {
            List<Integer> result = new ArrayList<Integer>();
            BitSet resolved = new BitSet(totalPairs);
            if (branchAndBound(resolves, totalPairs, size, 0, result, resolved)) {
                metricDimension = size;
                optimalResolvingSet = new ArrayList<String>();
                for (int idx : result) {
                    optimalResolvingSet.add(vertices.get(idx));
                }
                return metricDimension;
            }
        }
        // Shouldn't reach here — greedy always works
        metricDimension = ub;
        optimalResolvingSet = greedy;
        return metricDimension;
    }

    private boolean branchAndBound(BitSet[] resolves, int totalPairs,
            int targetSize, int startIdx, List<Integer> current, BitSet resolved) {
        if (current.size() == targetSize) {
            return resolved.cardinality() == totalPairs;
        }
        int remaining = targetSize - current.size();
        int verticesLeft = n - startIdx;
        if (verticesLeft < remaining) return false;

        // Pruning: even adding the best possible remaining vertices,
        // can we cover all pairs?
        int unresolvedCount = totalPairs - resolved.cardinality();
        if (unresolvedCount == 0) return true; // already resolved

        for (int v = startIdx; v <= n - remaining; v++) {
            // Pruning: check if adding this vertex resolves any new pair
            BitSet newResolved = (BitSet) resolved.clone();
            newResolved.or(resolves[v]);
            if (newResolved.cardinality() == resolved.cardinality()) {
                continue; // no progress
            }

            current.add(v);
            if (branchAndBound(resolves, totalPairs, targetSize,
                    v + 1, current, newResolved)) {
                return true;
            }
            current.remove(current.size() - 1);
        }
        return false;
    }

    /**
     * Get the optimal resolving set found by exact computation.
     *
     * @throws IllegalStateException if graph has more than EXACT_LIMIT vertices
     */
    public List<String> getOptimalResolvingSet() {
        getMetricDimension(); // ensures computation
        return new ArrayList<String>(optimalResolvingSet);
    }

    // ── Resolving Neighborhoods ─────────────────────────────────────

    /**
     * For a given vertex w, return the set of vertex pairs that w resolves
     * (i.e., pairs {u,v} where d(u,w) ≠ d(v,w)).
     */
    public List<String[]> getResolvingPairs(String w) {
        ensureDistComputed();
        int wi = vertices.indexOf(w);
        if (wi < 0) throw new IllegalArgumentException("Vertex not in graph: " + w);

        List<String[]> pairs = new ArrayList<String[]>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (dist[i][wi] != dist[j][wi]) {
                    pairs.add(new String[]{vertices.get(i), vertices.get(j)});
                }
            }
        }
        return pairs;
    }

    /**
     * Get the "resolving power" of each vertex — the number of pairs it
     * resolves.  Higher is better for inclusion in a resolving set.
     */
    public Map<String, Integer> getResolvingPower() {
        ensureDistComputed();
        Map<String, Integer> power = new LinkedHashMap<String, Integer>();
        for (int v = 0; v < n; v++) {
            int count = 0;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (dist[i][v] != dist[j][v]) count++;
                }
            }
            power.put(vertices.get(v), count);
        }
        return power;
    }

    // ── Text Summary ────────────────────────────────────────────────

    /**
     * Produce a formatted text summary of the metric dimension analysis.
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Metric Dimension Analysis ===\n");
        sb.append(String.format("Vertices: %d, Edges: %d%n", n, graph.getEdgeCount()));

        if (n == 0) {
            sb.append("Empty graph — metric dimension = 0\n");
            return sb.toString();
        }

        sb.append(String.format("Lower bound: %d%n", getLowerBound()));
        sb.append(String.format("Upper bound: %d%n", getUpperBound()));

        // Twins
        List<Set<String>> twins = getTwinClasses();
        if (twins.isEmpty()) {
            sb.append("Twin classes: none\n");
        } else {
            sb.append(String.format("Twin classes: %d%n", twins.size()));
            for (Set<String> cls : twins) {
                sb.append("  {" + String.join(", ", cls) + "}\n");
            }
        }

        // Greedy
        List<String> greedy = getGreedyResolvingSet();
        sb.append(String.format("Greedy resolving set (size %d): {%s}%n",
            greedy.size(), String.join(", ", greedy)));

        // Exact (if small enough)
        if (n <= EXACT_LIMIT) {
            int md = getMetricDimension();
            List<String> optimal = getOptimalResolvingSet();
            sb.append(String.format("Metric dimension (exact): %d%n", md));
            sb.append(String.format("Optimal resolving set: {%s}%n",
                String.join(", ", optimal)));

            // Metric representation
            sb.append("Metric representation (w.r.t. optimal set):\n");
            Map<String, int[]> repr = getMetricRepresentation(optimal);
            for (Map.Entry<String, int[]> entry : repr.entrySet()) {
                sb.append(String.format("  %s → %s%n", entry.getKey(),
                    Arrays.toString(entry.getValue())));
            }
        } else {
            sb.append(String.format("Graph too large for exact computation (limit %d)%n",
                EXACT_LIMIT));
            sb.append("Use greedy resolving set as approximation.\n");
        }

        // Resolving power (top 5)
        Map<String, Integer> power = getResolvingPower();
        List<Map.Entry<String, Integer>> sorted = new ArrayList<Map.Entry<String, Integer>>(
            power.entrySet());
        Collections.sort(sorted, (Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) -> {
                return Integer.compare(b.getValue(), a.getValue());
            });
        sb.append("Top resolving vertices:\n");
        int show = Math.min(5, sorted.size());
        for (int i = 0; i < show; i++) {
            Map.Entry<String, Integer> e = sorted.get(i);
            sb.append(String.format("  %s: resolves %d pairs%n", e.getKey(), e.getValue()));
        }

        return sb.toString();
    }
}
