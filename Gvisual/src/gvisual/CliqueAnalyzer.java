package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Maximal Clique Finder — Bron-Kerbosch algorithm with pivot selection
 * (Tomita variant) for finding all maximal cliques in a graph.
 *
 * <p>A <b>clique</b> is a complete subgraph — every pair of vertices is
 * connected. A <b>maximal clique</b> is one that cannot be extended by
 * adding another adjacent vertex.</p>
 *
 * <h3>Algorithm (Bron-Kerbosch with Tomita pivot)</h3>
 * <pre>
 * BronKerbosch(R, P, X):
 *   if P and X are both empty:
 *     report R as a maximal clique
 *   choose pivot u in P ∪ X maximizing |P ∩ N(u)|
 *   for each vertex v in P \ N(u):
 *     BronKerbosch(R ∪ {v}, P ∩ N(v), X ∩ N(v))
 *     P := P \ {v}
 *     X := X ∪ {v}
 * </pre>
 *
 * <p>Clique detection is fundamental in social network analysis (finding
 * tight-knit groups), bioinformatics (protein complexes), and graph theory.</p>
 *
 * @author zalenix
 */
public class CliqueAnalyzer {

    private final Graph<String, Edge> graph;
    private Map<String, Set<String>> neighborCache;
    private List<Set<String>> cliques;
    private Map<String, List<Integer>> vertexToCliquesIndex;
    private boolean computed;
    private int maxCliques = 100_000;
    private int maxDepth = 1_000;
    private boolean truncated = false;

    /**
     * Creates a new CliqueAnalyzer for the given graph.
     *
     * @param graph the JUNG graph to analyze
     * @throws IllegalArgumentException if graph is null
     */
    public CliqueAnalyzer(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.neighborCache = new HashMap<String, Set<String>>();
        this.cliques = new ArrayList<Set<String>>();
        this.vertexToCliquesIndex = null;
        this.computed = false;
    }

    // ── Core Algorithm ──────────────────────────────────────────────

    /**
     * Runs the Bron-Kerbosch algorithm with Tomita pivot selection.
     * Idempotent — repeated calls are no-ops.
     *
     * @return this analyzer for chaining
     */
    public CliqueAnalyzer compute() {
        if (computed) return this;

        cliques = new ArrayList<Set<String>>();
        vertexToCliquesIndex = null; // invalidate cached index

        Collection<String> vertices = graph.getVertices();
        if (vertices.isEmpty()) {
            computed = true;
            return this;
        }

        // Pre-build neighbor sets once so the recursive hot-path never
        // allocates temporary collections.  Uses shared GraphUtils method
        // instead of duplicating adjacency construction logic.
        neighborCache = GraphUtils.buildAdjacencyMap(graph);

        // For isolated vertices (no neighbors), each is a trivial maximal clique
        Set<String> P = new HashSet<String>(vertices);
        Set<String> R = new HashSet<String>();
        Set<String> X = new HashSet<String>();

        truncated = false;
        bronKerbosch(R, P, X, 0);

        // Sort cliques by size descending, then by first element for stability
        Collections.sort(cliques, (Set<String> a, Set<String> b) -> {
                int cmp = Integer.compare(b.size(), a.size());
                if (cmp != 0) return cmp;
                String minA = Collections.min(a);
                String minB = Collections.min(b);
                return minA.compareTo(minB);
            });

        computed = true;
        return this;
    }

    /**
     * Recursive Bron-Kerbosch with pivot selection.
     *
     * @param depth current recursion depth (for safety bound)
     */
    private void bronKerbosch(Set<String> R, Set<String> P, Set<String> X, int depth) {
        if (cliques.size() >= maxCliques) {
            truncated = true;
            return;
        }
        if (depth > maxDepth) {
            truncated = true;
            return;
        }

        if (P.isEmpty() && X.isEmpty()) {
            cliques.add(new LinkedHashSet<String>(R));
            return;
        }

        if (P.isEmpty()) {
            return;
        }

        // Choose pivot u in P ∪ X that maximizes |P ∩ N(u)|
        String pivot = choosePivot(P, X);
        Set<String> pivotNeighbors = getNeighbors(pivot);

        // P \ N(pivot)
        Set<String> candidates = new HashSet<String>(P);
        candidates.removeAll(pivotNeighbors);

        for (String v : candidates) {
            Set<String> neighborsV = getNeighbors(v);

            // Reuse R with backtracking instead of allocating a copy per branch.
            R.add(v);

            // Build intersections P ∩ N(v) and X ∩ N(v) without copying
            // the full sets first — only add elements that pass the filter.
            Set<String> newP = new HashSet<String>(Math.min(P.size(), neighborsV.size()));
            for (String u : P) {
                if (neighborsV.contains(u)) newP.add(u);
            }

            Set<String> newX = new HashSet<String>(Math.min(X.size(), neighborsV.size()));
            for (String u : X) {
                if (neighborsV.contains(u)) newX.add(u);
            }

            bronKerbosch(R, newP, newX, depth + 1);

            R.remove(v);

            if (truncated) return;

            P.remove(v);
            X.add(v);
        }
    }

    /**
     * Choose pivot vertex from P ∪ X that maximizes |P ∩ N(u)|.
     */
    private String choosePivot(Set<String> P, Set<String> X) {
        String bestPivot = null;
        int bestCount = -1;

        // Check all vertices in P ∪ X
        for (String u : P) {
            int count = countIntersection(P, getNeighbors(u));
            if (count > bestCount) {
                bestCount = count;
                bestPivot = u;
            }
        }
        for (String u : X) {
            int count = countIntersection(P, getNeighbors(u));
            if (count > bestCount) {
                bestCount = count;
                bestPivot = u;
            }
        }

        return bestPivot;
    }

    /**
     * Count elements in the intersection of two sets.
     * Iterates the smaller set and probes the larger one for O(min(|a|,|b|))
     * instead of always O(|a|). In choosePivot this is called O(|P|+|X|)
     * times with P potentially much larger than individual neighbor sets,
     * so iterating the smaller side can cut pivot selection time significantly.
     */
    private int countIntersection(Set<String> a, Set<String> b) {
        // Always iterate the smaller set, probe the larger
        if (a.size() > b.size()) {
            Set<String> tmp = a; a = b; b = tmp;
        }
        int count = 0;
        for (String s : a) {
            if (b.contains(s)) count++;
        }
        return count;
    }

    private Set<String> getNeighbors(String vertex) {
        Set<String> cached = neighborCache.get(vertex);
        if (cached != null) return cached;
        // Fallback: should not happen after compute() builds the cache.
        return Collections.<String>emptySet();
    }

    // ── Configuration ────────────────────────────────────────────────

    /**
     * Set maximum number of cliques to find before stopping.
     * Default: 100,000.
     *
     * @param max maximum clique count
     * @return this analyzer for chaining
     */
    public CliqueAnalyzer withMaxCliques(int max) {
        this.maxCliques = max;
        return this;
    }

    /**
     * Set maximum recursion depth before stopping.
     * Default: 1,000.
     *
     * @param max maximum recursion depth
     * @return this analyzer for chaining
     */
    public CliqueAnalyzer withMaxDepth(int max) {
        this.maxDepth = max;
        return this;
    }

    /**
     * Whether the computation was truncated due to hitting maxCliques
     * or maxDepth limits.
     *
     * @return true if results are incomplete
     */
    public boolean wasTruncated() {
        return truncated;
    }

    // ── Accessors ───────────────────────────────────────────────────

    /**
     * All maximal cliques found, sorted by size descending.
     */
    public List<Set<String>> getCliques() {
        ensureComputed();
        return Collections.unmodifiableList(cliques);
    }

    /**
     * Number of maximal cliques.
     */
    public int getCliqueCount() {
        ensureComputed();
        return cliques.size();
    }

    /**
     * Largest clique found (the clique number ω(G)).
     * Returns empty set if no cliques exist.
     */
    public Set<String> getLargestClique() {
        ensureComputed();
        if (cliques.isEmpty()) return Collections.emptySet();
        return Collections.unmodifiableSet(cliques.get(0));
    }

    /**
     * Size of the largest clique (clique number).
     * Returns 0 for empty graphs.
     */
    public int getCliqueNumber() {
        ensureComputed();
        if (cliques.isEmpty()) return 0;
        return cliques.get(0).size();
    }

    /**
     * Get all cliques of exactly size k.
     */
    public List<Set<String>> getCliquesOfSize(int k) {
        ensureComputed();
        List<Set<String>> result = new ArrayList<Set<String>>();
        for (Set<String> clique : cliques) {
            if (clique.size() == k) {
                result.add(Collections.unmodifiableSet(clique));
            }
        }
        return result;
    }

    /**
     * Get all cliques containing a specific vertex.
     */
    public List<Set<String>> getCliquesContaining(String vertex) {
        ensureComputed();
        List<Set<String>> result = new ArrayList<Set<String>>();
        for (Set<String> clique : cliques) {
            if (clique.contains(vertex)) {
                result.add(Collections.unmodifiableSet(clique));
            }
        }
        return result;
    }

    // ── Analytics ───────────────────────────────────────────────────

    /**
     * Lazily builds and caches an inverted index mapping each vertex
     * to the list of clique indices containing it. Shared by
     * {@link #getOverlaps} and {@link #getCliqueGraph} to avoid
     * redundant O(V*C) rebuilds.
     */
    private Map<String, List<Integer>> getVertexToCliquesIndex() {
        if (vertexToCliquesIndex != null) return vertexToCliquesIndex;
        vertexToCliquesIndex = new HashMap<String, List<Integer>>();
        for (int i = 0; i < cliques.size(); i++) {
            for (String v : cliques.get(i)) {
                List<Integer> indices = vertexToCliquesIndex.get(v);
                if (indices == null) {
                    indices = new ArrayList<Integer>();
                    vertexToCliquesIndex.put(v, indices);
                }
                indices.add(i);
            }
        }
        return vertexToCliquesIndex;
    }

    /**
     * Clique size distribution: size → count.
     */
    public SortedMap<Integer, Integer> getSizeDistribution() {
        ensureComputed();
        SortedMap<Integer, Integer> dist = new TreeMap<Integer, Integer>();
        for (Set<String> clique : cliques) {
            int size = clique.size();
            Integer count = dist.get(size);
            dist.put(size, count == null ? 1 : count + 1);
        }
        return dist;
    }

    /**
     * Node participation: for each vertex, how many cliques it belongs to.
     * High participation = vertex is a "connector" between multiple groups.
     */
    public Map<String, Integer> getNodeParticipation() {
        ensureComputed();
        Map<String, Integer> participation = new LinkedHashMap<String, Integer>();
        for (String v : graph.getVertices()) {
            participation.put(v, 0);
        }
        for (Set<String> clique : cliques) {
            for (String v : clique) {
                Integer count = participation.get(v);
                participation.put(v, count == null ? 1 : count + 1);
            }
        }
        return participation;
    }

    /**
     * Top N nodes by clique participation.
     */
    public List<Map.Entry<String, Integer>> getTopParticipants(int n) {
        Map<String, Integer> participation = getNodeParticipation();
        List<Map.Entry<String, Integer>> entries =
            new ArrayList<Map.Entry<String, Integer>>(participation.entrySet());

        Collections.sort(entries, (Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) -> {
                int cmp = Integer.compare(b.getValue(), a.getValue());
                if (cmp != 0) return cmp;
                return a.getKey().compareTo(b.getKey());
            });

        return entries.subList(0, Math.min(n, entries.size()));
    }

    /**
     * Clique overlap: for each pair of cliques that share ≥1 vertex,
     * return the shared vertices. Limited to top overlapping pairs.
     *
     * <p>Uses an inverted index (vertex → clique indices) so only pairs
     * that actually share a vertex are considered, avoiding the O(C²)
     * exhaustive scan when C (clique count) is large.</p>
     */
    public List<CliqueOverlap> getOverlaps(int maxPairs) {
        ensureComputed();

        // Use cached inverted index
        Map<String, List<Integer>> vertexToCliques = getVertexToCliquesIndex();

        // Collect candidate pairs — only pairs sharing at least one vertex
        Set<Long> seenPairs = new HashSet<Long>();
        Map<Long, Set<String>> pairShared = new LinkedHashMap<Long, Set<String>>();

        for (Map.Entry<String, List<Integer>> entry : vertexToCliques.entrySet()) {
            String vertex = entry.getKey();
            List<Integer> indices = entry.getValue();
            for (int a = 0; a < indices.size(); a++) {
                for (int b = a + 1; b < indices.size(); b++) {
                    int i = indices.get(a);
                    int j = indices.get(b);
                    // Canonical pair key (i < j guaranteed by construction)
                    long key = ((long) i << 32) | j;
                    if (seenPairs.add(key)) {
                        // First time seeing this pair — compute full intersection
                        Set<String> shared = new LinkedHashSet<String>(cliques.get(i));
                        shared.retainAll(cliques.get(j));
                        pairShared.put(key, shared);
                    }
                }
            }
        }

        List<CliqueOverlap> overlaps = new ArrayList<CliqueOverlap>(pairShared.size());
        for (Map.Entry<Long, Set<String>> entry : pairShared.entrySet()) {
            long key = entry.getKey();
            int i = (int) (key >> 32);
            int j = (int) (key & 0xFFFFFFFFL);
            overlaps.add(new CliqueOverlap(i, j, entry.getValue()));
        }

        // Sort by overlap size descending
        Collections.sort(overlaps, (CliqueOverlap a, CliqueOverlap b) -> {
                return Integer.compare(b.overlapSize, a.overlapSize);
            });

        return overlaps.subList(0, Math.min(maxPairs, overlaps.size()));
    }

    /**
     * Coverage: fraction of vertices that belong to at least one
     * non-trivial clique (size ≥ 3).
     */
    public double getCoverage() {
        ensureComputed();
        int totalVertices = graph.getVertexCount();
        if (totalVertices == 0) return 0.0;

        Set<String> covered = new HashSet<String>();
        for (Set<String> clique : cliques) {
            if (clique.size() >= 3) {
                covered.addAll(clique);
            }
        }
        return (double) covered.size() / totalVertices;
    }

    /**
     * Average clique size.
     */
    public double getAverageCliqueSize() {
        ensureComputed();
        if (cliques.isEmpty()) return 0.0;
        double total = 0;
        for (Set<String> clique : cliques) {
            total += clique.size();
        }
        return total / cliques.size();
    }

    /**
     * Clique graph: nodes are cliques, edges connect cliques sharing
     * ≥ overlapThreshold vertices.
     * Returns adjacency list as Map&lt;Integer, Set&lt;Integer&gt;&gt;
     * (clique index → neighbor indices).
     *
     * <p>Uses an inverted index (vertex → clique indices) to enumerate
     * only candidate pairs that share at least one vertex, then checks
     * the full intersection size against the threshold.  This avoids
     * the O(C²) exhaustive scan when the clique count C is large.</p>
     */
    public Map<Integer, Set<Integer>> getCliqueGraph(int overlapThreshold) {
        ensureComputed();
        Map<Integer, Set<Integer>> adj = new LinkedHashMap<Integer, Set<Integer>>();

        for (int i = 0; i < cliques.size(); i++) {
            adj.put(i, new LinkedHashSet<Integer>());
        }

        // Use cached inverted index
        Map<String, List<Integer>> vertexToCliques = getVertexToCliquesIndex();

        // Collect candidate pairs and check threshold
        Set<Long> checked = new HashSet<Long>();
        for (List<Integer> indices : vertexToCliques.values()) {
            for (int a = 0; a < indices.size(); a++) {
                for (int b = a + 1; b < indices.size(); b++) {
                    int i = indices.get(a);
                    int j = indices.get(b);
                    long key = ((long) i << 32) | j;
                    if (checked.add(key)) {
                        Set<String> shared = new LinkedHashSet<String>(cliques.get(i));
                        shared.retainAll(cliques.get(j));
                        if (shared.size() >= overlapThreshold) {
                            adj.get(i).add(j);
                            adj.get(j).add(i);
                        }
                    }
                }
            }
        }

        return adj;
    }

    // ── Result Object ───────────────────────────────────────────────

    /**
     * Returns a snapshot of all computed results.
     */
    public CliqueResult getResult() {
        ensureComputed();
        return new CliqueResult(
            Collections.unmodifiableList(cliques),
            getCliqueNumber(),
            getCliqueCount(),
            getAverageCliqueSize(),
            getCoverage(),
            getSizeDistribution(),
            getNodeParticipation()
        );
    }

    // ── Summary ─────────────────────────────────────────────────────

    /**
     * Human-readable summary of the clique analysis.
     */
    public String formatSummary() {
        ensureComputed();

        StringBuilder sb = new StringBuilder();
        sb.append("=== Clique Analysis ===\n");

        int cliqueNumber = getCliqueNumber();
        int cliqueCount = getCliqueCount();
        double avgSize = getAverageCliqueSize();
        int totalVertices = graph.getVertexCount();
        // Compute coverage in a single pass instead of calling getCoverage()
        // (which iterates all cliques) and then re-iterating to count covered
        // vertices — previously this did the same O(C*V) work twice.
        Set<String> covered = new HashSet<String>();
        for (Set<String> clique : cliques) {
            if (clique.size() >= 3) {
                covered.addAll(clique);
            }
        }
        int coveredCount = covered.size();
        double coverage = totalVertices > 0 ? (double) coveredCount / totalVertices : 0.0;

        sb.append(String.format("Clique number (\u03C9):    %d%n", cliqueNumber));
        sb.append(String.format("Maximal cliques:      %d%n", cliqueCount));
        sb.append(String.format("Average clique size:  %.1f%n", avgSize));
        sb.append(String.format("Vertex coverage:      %.1f%% (%d/%d)%n",
            coverage * 100.0, coveredCount, totalVertices));

        // Size distribution
        SortedMap<Integer, Integer> dist = getSizeDistribution();
        if (!dist.isEmpty()) {
            sb.append("\nSize distribution:\n");
            int maxCount = 0;
            for (int c : dist.values()) {
                if (c > maxCount) maxCount = c;
            }
            for (Map.Entry<Integer, Integer> entry : dist.entrySet()) {
                int size = entry.getKey();
                int count = entry.getValue();
                int barLen = maxCount > 0 ? (int) Math.ceil((double) count / maxCount * 8) : 0;
                StringBuilder bar = new StringBuilder();
                for (int i = 0; i < barLen; i++) bar.append('\u2588');
                double pct = cliqueCount > 0 ? (double) count / cliqueCount * 100.0 : 0;
                sb.append(String.format("  Size %d:  %-8s %d (%.1f%%)%n",
                    size, bar.toString(), count, pct));
            }
        }

        // Largest clique
        if (cliqueNumber > 0) {
            Set<String> largest = getLargestClique();
            sb.append(String.format("%nLargest clique (%d vertices):%n", cliqueNumber));
            List<String> sorted = new ArrayList<String>(largest);
            Collections.sort(sorted);
            sb.append("  {");
            boolean first = true;
            for (String v : sorted) {
                if (!first) sb.append(", ");
                sb.append(v);
                first = false;
            }
            sb.append("}\n");
        }

        // Top participants
        List<Map.Entry<String, Integer>> topPart = getTopParticipants(
            Math.min(3, graph.getVertexCount()));
        if (!topPart.isEmpty()) {
            sb.append("\nTop participants:\n");
            int rank = 1;
            for (Map.Entry<String, Integer> entry : topPart) {
                sb.append(String.format("  %d. %s (in %d cliques)%n",
                    rank++, entry.getKey(), entry.getValue()));
            }
        }

        return sb.toString();
    }

    // ── Inner Classes ───────────────────────────────────────────────

    /**
     * Represents overlap between two cliques.
     */
    public static class CliqueOverlap {
        public final int cliqueIndex1;
        public final int cliqueIndex2;
        public final Set<String> sharedVertices;
        public final int overlapSize;

        public CliqueOverlap(int cliqueIndex1, int cliqueIndex2, Set<String> sharedVertices) {
            this.cliqueIndex1 = cliqueIndex1;
            this.cliqueIndex2 = cliqueIndex2;
            this.sharedVertices = Collections.unmodifiableSet(sharedVertices);
            this.overlapSize = sharedVertices.size();
        }

        @Override
        public String toString() {
            return String.format("CliqueOverlap{cliques=[%d,%d], shared=%s, size=%d}",
                cliqueIndex1, cliqueIndex2, sharedVertices, overlapSize);
        }
    }

    /**
     * Immutable snapshot of all clique analysis results.
     */
    public static class CliqueResult {
        public final List<Set<String>> cliques;
        public final int cliqueNumber;
        public final int cliqueCount;
        public final double averageSize;
        public final double coverage;
        public final SortedMap<Integer, Integer> sizeDistribution;
        public final Map<String, Integer> nodeParticipation;

        public CliqueResult(List<Set<String>> cliques, int cliqueNumber, int cliqueCount,
                            double averageSize, double coverage,
                            SortedMap<Integer, Integer> sizeDistribution,
                            Map<String, Integer> nodeParticipation) {
            this.cliques = cliques;
            this.cliqueNumber = cliqueNumber;
            this.cliqueCount = cliqueCount;
            this.averageSize = averageSize;
            this.coverage = coverage;
            this.sizeDistribution = sizeDistribution;
            this.nodeParticipation = nodeParticipation;
        }

        @Override
        public String toString() {
            return String.format("CliqueResult{cliqueNumber=%d, cliqueCount=%d, avgSize=%.1f, coverage=%.1f%%}",
                cliqueNumber, cliqueCount, averageSize, coverage * 100.0);
        }
    }

    private void ensureComputed() {
        if (!computed) compute();
    }
}
