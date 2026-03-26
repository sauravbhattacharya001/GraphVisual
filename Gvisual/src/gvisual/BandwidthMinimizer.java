package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Graph bandwidth analysis and minimization using the Cuthill–McKee (CM)
 * and Reverse Cuthill–McKee (RCM) algorithms.
 *
 * <p>The <em>bandwidth</em> of a graph labeling is the maximum difference
 * between labels of adjacent vertices. Minimizing bandwidth is NP-hard in
 * general, but the Cuthill–McKee heuristic produces good orderings in
 * practice, especially for sparse graphs.</p>
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Bandwidth computation</b> — for any given vertex ordering</li>
 *   <li><b>Cuthill–McKee ordering</b> — BFS-based bandwidth reduction</li>
 *   <li><b>Reverse Cuthill–McKee</b> — often produces tighter bandwidth</li>
 *   <li><b>Profile/envelope computation</b> — sum of row bandwidths</li>
 *   <li><b>Comparison report</b> — original vs CM vs RCM orderings</li>
 * </ul>
 *
 * <p>Applications include sparse matrix reordering, finite-element mesh
 * numbering, cache-friendly graph traversal, and reducing fill-in during
 * matrix factorization.</p>
 *
 * @author zalenix
 */
public final class BandwidthMinimizer {

    private BandwidthMinimizer() { /* utility class */ }

    // ── Data classes ──────────────────────────────────────────────────────

    /** Result of bandwidth analysis for a single ordering. */
    public static class BandwidthResult {
        private final List<String> ordering;
        private final int bandwidth;
        private final long profile;
        private final Map<String, Integer> labelMap;

        public BandwidthResult(List<String> ordering, int bandwidth, long profile,
                               Map<String, Integer> labelMap) {
            this.ordering = Collections.unmodifiableList(new ArrayList<>(ordering));
            this.bandwidth = bandwidth;
            this.profile = profile;
            this.labelMap = Collections.unmodifiableMap(new LinkedHashMap<>(labelMap));
        }

        public List<String> getOrdering()          { return ordering; }
        public int          getBandwidth()          { return bandwidth; }
        public long         getProfile()            { return profile; }
        public Map<String, Integer> getLabelMap()    { return labelMap; }

        @Override
        public String toString() {
            return String.format("bandwidth=%d, profile=%d, vertices=%d",
                    bandwidth, profile, ordering.size());
        }
    }

    /** Comparison of original, CM, and RCM orderings. */
    public static class ComparisonReport {
        private final BandwidthResult original;
        private final BandwidthResult cuthillMcKee;
        private final BandwidthResult reverseCuthillMcKee;

        public ComparisonReport(BandwidthResult original,
                                BandwidthResult cuthillMcKee,
                                BandwidthResult reverseCuthillMcKee) {
            this.original = original;
            this.cuthillMcKee = cuthillMcKee;
            this.reverseCuthillMcKee = reverseCuthillMcKee;
        }

        public BandwidthResult getOriginal()            { return original; }
        public BandwidthResult getCuthillMcKee()        { return cuthillMcKee; }
        public BandwidthResult getReverseCuthillMcKee() { return reverseCuthillMcKee; }

        /** Returns the result with the smallest bandwidth. */
        public BandwidthResult getBest() {
            BandwidthResult best = original;
            if (cuthillMcKee.bandwidth < best.bandwidth) best = cuthillMcKee;
            if (reverseCuthillMcKee.bandwidth < best.bandwidth) best = reverseCuthillMcKee;
            return best;
        }

        /** Bandwidth reduction percentage of best vs original. */
        public double getReductionPercent() {
            if (original.bandwidth == 0) return 0.0;
            return 100.0 * (original.bandwidth - getBest().bandwidth) / original.bandwidth;
        }

        @Override
        public String toString() {
            return String.format(
                "Original: %s%nCuthill-McKee: %s%nReverse CM: %s%nBest reduction: %.1f%%",
                original, cuthillMcKee, reverseCuthillMcKee, getReductionPercent());
        }
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Compute bandwidth and profile for a given vertex ordering.
     */
    public static <V, E> BandwidthResult computeBandwidth(Graph<V, E> graph,
                                                           List<V> ordering) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(ordering, "ordering");
        if (ordering.isEmpty()) {
            return new BandwidthResult(Collections.emptyList(), 0, 0,
                    Collections.emptyMap());
        }

        Map<V, Integer> posMap = new HashMap<>();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < ordering.size(); i++) {
            posMap.put(ordering.get(i), i);
            labels.add(ordering.get(i).toString());
        }

        int maxBw = 0;
        long profile = 0;

        for (V v : ordering) {
            int vPos = posMap.get(v);
            int rowBw = 0;
            for (V neighbor : graph.getNeighbors(v)) {
                Integer nPos = posMap.get(neighbor);
                if (nPos != null) {
                    int diff = Math.abs(vPos - nPos);
                    rowBw = Math.max(rowBw, diff);
                }
            }
            maxBw = Math.max(maxBw, rowBw);
            profile += rowBw;
        }

        Map<String, Integer> labelMap = new LinkedHashMap<>();
        for (int i = 0; i < labels.size(); i++) {
            labelMap.put(labels.get(i), i);
        }

        return new BandwidthResult(labels, maxBw, profile, labelMap);
    }

    /**
     * Compute the Cuthill–McKee ordering starting from a pseudo-peripheral
     * vertex (the vertex found by iterative BFS eccentricity).
     */
    public static <V, E> BandwidthResult cuthillMcKee(Graph<V, E> graph) {
        Objects.requireNonNull(graph, "graph");
        if (graph.getVertexCount() == 0) {
            return new BandwidthResult(Collections.emptyList(), 0, 0,
                    Collections.emptyMap());
        }

        List<V> ordering = cuthillMcKeeOrdering(graph);
        return computeBandwidth(graph, ordering);
    }

    /**
     * Compute the Reverse Cuthill–McKee ordering (reverse of CM ordering).
     * Often yields tighter bandwidth than standard CM.
     */
    public static <V, E> BandwidthResult reverseCuthillMcKee(Graph<V, E> graph) {
        Objects.requireNonNull(graph, "graph");
        if (graph.getVertexCount() == 0) {
            return new BandwidthResult(Collections.emptyList(), 0, 0,
                    Collections.emptyMap());
        }

        List<V> ordering = cuthillMcKeeOrdering(graph);
        Collections.reverse(ordering);
        return computeBandwidth(graph, ordering);
    }

    /**
     * Full comparison: original (natural iteration order) vs CM vs RCM.
     *
     * <p>Computes the Cuthill–McKee ordering once and derives both the CM
     * and RCM bandwidth results from it, avoiding the redundant BFS
     * traversal that calling {@code cuthillMcKee()} and
     * {@code reverseCuthillMcKee()} independently would incur.</p>
     */
    public static <V, E> ComparisonReport compare(Graph<V, E> graph) {
        Objects.requireNonNull(graph, "graph");

        List<V> natural = new ArrayList<>(graph.getVertices());
        BandwidthResult orig = computeBandwidth(graph, natural);

        // Compute the CM ordering once and reuse for both CM and RCM results
        List<V> cmOrdering = cuthillMcKeeOrdering(graph);
        BandwidthResult cm = computeBandwidth(graph, cmOrdering);

        List<V> rcmOrdering = new ArrayList<>(cmOrdering);
        Collections.reverse(rcmOrdering);
        BandwidthResult rcm = computeBandwidth(graph, rcmOrdering);

        return new ComparisonReport(orig, cm, rcm);
    }

    // ── Internal ──────────────────────────────────────────────────────────

    /**
     * Core Cuthill–McKee BFS ordering with pseudo-peripheral start vertex.
     * Handles disconnected graphs by processing each component.
     */
    private static <V, E> List<V> cuthillMcKeeOrdering(Graph<V, E> graph) {
        Set<V> visited = new HashSet<>();
        List<V> ordering = new ArrayList<>();

        for (V seed : graph.getVertices()) {
            if (visited.contains(seed)) continue;

            // Find pseudo-peripheral vertex in this component
            V start = findPseudoPeripheral(graph, seed, visited);
            visited.add(start); // re-add since findPseudoPeripheral doesn't mark permanently

            // Reset visited for this component traversal
            Set<V> componentVisited = new HashSet<>();
            Queue<V> queue = new LinkedList<>();
            queue.add(start);
            componentVisited.add(start);

            while (!queue.isEmpty()) {
                V current = queue.poll();
                ordering.add(current);
                visited.add(current);

                // Get neighbors sorted by degree (ascending)
                List<V> neighbors = new ArrayList<>();
                for (V n : graph.getNeighbors(current)) {
                    if (!componentVisited.contains(n)) {
                        neighbors.add(n);
                    }
                }
                neighbors.sort(Comparator.comparingInt(v -> graph.degree(v)));

                for (V n : neighbors) {
                    if (componentVisited.add(n)) {
                        queue.add(n);
                    }
                }
            }
        }

        return ordering;
    }

    /**
     * Find a pseudo-peripheral vertex by iterative BFS eccentricity.
     * Starts from {@code seed}, does BFS, picks the last-visited vertex
     * (most distant), repeats until eccentricity stops increasing.
     */
    private static <V, E> V findPseudoPeripheral(Graph<V, E> graph, V seed,
                                                   Set<V> globalVisited) {
        V current = seed;
        int prevEcc = -1;

        for (int iter = 0; iter < 20; iter++) { // safety bound
            Map<V, Integer> dist = bfsDistances(graph, current);
            int ecc = 0;
            V farthest = current;
            for (Map.Entry<V, Integer> e : dist.entrySet()) {
                if (e.getValue() > ecc) {
                    ecc = e.getValue();
                    farthest = e.getKey();
                }
            }
            if (ecc <= prevEcc) break;
            prevEcc = ecc;
            current = farthest;
        }

        return current;
    }

    /** BFS distance map from a source vertex. */
    private static <V, E> Map<V, Integer> bfsDistances(Graph<V, E> graph, V source) {
        Map<V, Integer> dist = new HashMap<>();
        Queue<V> queue = new LinkedList<>();
        dist.put(source, 0);
        queue.add(source);

        while (!queue.isEmpty()) {
            V v = queue.poll();
            int d = dist.get(v);
            for (V n : graph.getNeighbors(v)) {
                if (!dist.containsKey(n)) {
                    dist.put(n, d + 1);
                    queue.add(n);
                }
            }
        }

        return dist;
    }
}
