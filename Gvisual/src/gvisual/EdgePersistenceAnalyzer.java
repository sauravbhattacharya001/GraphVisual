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
 * @author zalenix
 */
public class EdgePersistenceAnalyzer {

    /** Edge appears in &ge;75% of time windows. */
    public static final String PERSISTENT = "persistent";
    /** Edge appears in 25–74% of time windows. */
    public static final String PERIODIC = "periodic";
    /** Edge appears in &lt;25% of time windows. */
    public static final String TRANSIENT = "transient";

    private final TemporalGraph temporalGraph;
    private final int windowCount;

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
     * @return a map from each Edge to its persistence classification
     */
    public Map<Edge, String> classify() {
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
        return result;
    }

    /**
     * Returns a summary of Edge persistence: counts of each classification.
     *
     * @return map with keys "persistent", "periodic", "transient" and integer counts
     */
    public Map<String, Integer> summary() {
        Map<Edge, String> classified = classify();
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(PERSISTENT, 0);
        counts.put(PERIODIC, 0);
        counts.put(TRANSIENT, 0);
        for (String category : classified.values()) {
            counts.merge(category, 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Returns only edges matching the given classification.
     *
     * @param classification one of {@link #PERSISTENT}, {@link #PERIODIC}, {@link #TRANSIENT}
     * @return set of edges matching the classification
     */
    public Set<Edge> getEdgesByClassification(String classification) {
        Map<Edge, String> classified = classify();
        Set<Edge> result = new LinkedHashSet<>();
        for (Map.Entry<Edge, String> entry : classified.entrySet()) {
            if (classification.equals(entry.getValue())) {
                result.add(entry.getKey());
            }
        }
        return result;
    }
}
