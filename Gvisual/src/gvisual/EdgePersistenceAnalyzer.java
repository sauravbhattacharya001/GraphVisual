package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Classifies edges in a temporal graph based on their persistence across
 * time windows. Each Edge is categorized as:
 * <ul>
 *   <li><b>PERSISTENT</b> — appears in &ge;75% of time windows (stable relationship)</li>
 *   <li><b>PERIODIC</b> — appears in 25–74% of windows (recurring but intermittent)</li>
 *   <li><b>TRANSIENT</b> — appears in &lt;25% of windows (one-off or rare interaction)</li>
 * </ul>
 *
 * <p>This is especially useful for social network data where meeting frequency
 * reveals relationship strength: persistent edges are strong ties, transient
 * edges are weak/one-time encounters.</p>
 *
 * <h2>Caching contract</h2>
 * <p>The classification is <em>memoized on first compute</em>. The inputs
 * ({@link TemporalGraph} and window count) are treated as effectively
 * immutable for the lifetime of an instance: subsequent calls to
 * {@link #classify()}, {@link #summary()}, and
 * {@link #getEdgesByClassification(String)} are O(1) lookups after the
 * first traversal, instead of repeated O(W·E) re-scans (see GitHub
 * issue #168). If the underlying graph is mutated externally, those
 * mutations will <strong>not</strong> be reflected — construct a new
 * analyzer to re-classify.</p>
 *
 * @author sauravbhattacharya001
 */
public class EdgePersistenceAnalyzer {

    /** Edge appears in &ge;75% of time windows. */
    public static final String PERSISTENT = "persistent";
    /** Edge appears in 25–74% of time windows. */
    public static final String PERIODIC = "periodic";
    /** Edge appears in &lt;25% of time windows. */
    public static final String TRANSIENT = "transient";

    /** Set of valid classification strings (for input validation). */
    private static final Set<String> VALID_CLASSIFICATIONS =
        Collections.unmodifiableSet(new LinkedHashSet<>(
            Arrays.asList(PERSISTENT, PERIODIC, TRANSIENT)));

    private final TemporalGraph temporalGraph;
    private final int windowCount;

    /** Cached classification result; lazily computed on first {@link #classify()}. */
    private Map<Edge, String> cachedClassification;
    /** Cached summary counts; lazily computed on first {@link #summary()}. */
    private Map<String, Integer> cachedSummary;
    /** Cached bucketed edge sets keyed by classification, lazily populated. */
    private Map<String, Set<Edge>> cachedBuckets;

    /**
     * Creates an EdgePersistenceAnalyzer.
     *
     * @param temporalGraph the temporal graph to analyze
     * @param windowCount number of time windows to divide the range into
     * @throws IllegalArgumentException if temporalGraph is null or windowCount &lt; 1
     */
    public EdgePersistenceAnalyzer(TemporalGraph temporalGraph, int windowCount) {
        if (temporalGraph == null) {
            throw new IllegalArgumentException("TemporalGraph must not be null");
        }
        if (windowCount < 1) {
            throw new IllegalArgumentException("windowCount must be at least 1");
        }
        this.temporalGraph = temporalGraph;
        this.windowCount = windowCount;
    }

    /**
     * Analyzes Edge persistence across time windows.
     *
     * <p>The first call performs the O(W·E) window/edge scan and memoizes
     * the result. Subsequent calls return the same (unmodifiable) map
     * instance, guaranteeing both reference equality and content
     * equality.</p>
     *
     * @return an unmodifiable map from each Edge to its persistence classification
     */
    public Map<Edge, String> classify() {
        if (cachedClassification != null) {
            return cachedClassification;
        }

        List<Map.Entry<Long, Graph<String, Edge>>> windows =
            temporalGraph.generateWindows(windowCount);

        // Count how many windows each Edge appears in
        Map<Edge, Integer> edgeAppearances = new LinkedHashMap<>();
        for (Edge e : temporalGraph.getFullGraph().getEdges()) {
            edgeAppearances.put(e, 0);
        }

        for (Map.Entry<Long, Graph<String, Edge>> window : windows) {
            Graph<String, Edge> g = window.getValue();
            for (Edge e : g.getEdges()) {
                edgeAppearances.merge(e, 1, Integer::sum);
            }
        }

        // Classify based on appearance ratio
        Map<Edge, String> result = new LinkedHashMap<>();
        for (Map.Entry<Edge, Integer> entry : edgeAppearances.entrySet()) {
            double ratio = (double) entry.getValue() / windowCount;
            if (ratio >= 0.75) {
                result.put(entry.getKey(), PERSISTENT);
            } else if (ratio >= 0.25) {
                result.put(entry.getKey(), PERIODIC);
            } else {
                result.put(entry.getKey(), TRANSIENT);
            }
        }
        cachedClassification = Collections.unmodifiableMap(result);
        return cachedClassification;
    }

    /**
     * Returns a summary of Edge persistence: counts of each classification.
     * Memoized; subsequent calls do not re-traverse.
     *
     * @return unmodifiable map with keys "persistent", "periodic", "transient" and integer counts
     */
    public Map<String, Integer> summary() {
        if (cachedSummary != null) {
            return cachedSummary;
        }
        Map<Edge, String> classified = classify();
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(PERSISTENT, 0);
        counts.put(PERIODIC, 0);
        counts.put(TRANSIENT, 0);
        for (String category : classified.values()) {
            counts.merge(category, 1, Integer::sum);
        }
        cachedSummary = Collections.unmodifiableMap(counts);
        return cachedSummary;
    }

    /**
     * Returns only edges matching the given classification.
     *
     * <p>Bucketed results are computed once on first request per
     * classification and reused for subsequent calls.</p>
     *
     * @param classification one of {@link #PERSISTENT}, {@link #PERIODIC}, {@link #TRANSIENT}
     * @return unmodifiable set of edges matching the classification
     * @throws IllegalArgumentException if {@code classification} is null or not one
     *     of the three known constants. This catches silent typos like
     *     {@code "persistant"} or {@code "PERSISTENT"} that would previously
     *     return an empty set with no warning.
     */
    public Set<Edge> getEdgesByClassification(String classification) {
        if (classification == null || !VALID_CLASSIFICATIONS.contains(classification)) {
            throw new IllegalArgumentException(
                "Unknown classification: " + classification
                + " (expected one of " + VALID_CLASSIFICATIONS + ")");
        }
        if (cachedBuckets == null) {
            cachedBuckets = buildBuckets(classify());
        }
        return cachedBuckets.get(classification);
    }

    /** One-pass partition of the classification map into per-bucket edge sets. */
    private static Map<String, Set<Edge>> buildBuckets(Map<Edge, String> classified) {
        Map<String, Set<Edge>> buckets = new LinkedHashMap<>();
        buckets.put(PERSISTENT, new LinkedHashSet<>());
        buckets.put(PERIODIC,  new LinkedHashSet<>());
        buckets.put(TRANSIENT, new LinkedHashSet<>());
        for (Map.Entry<Edge, String> entry : classified.entrySet()) {
            buckets.get(entry.getValue()).add(entry.getKey());
        }
        // Freeze the bucket sets so callers cannot mutate cached state.
        Map<String, Set<Edge>> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, Set<Edge>> e : buckets.entrySet()) {
            frozen.put(e.getKey(), Collections.unmodifiableSet(e.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }
}
