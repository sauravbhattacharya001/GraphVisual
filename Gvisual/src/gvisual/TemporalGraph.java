package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.*;

/**
 * A lightweight wrapper around a JUNG graph that provides time-windowed views
 * of the network. This enables temporal analysis without changing any existing
 * analyzer — analyzers receive a normal {@code Graph<String, Edge>} for a
 * specific time window.
 *
 * <p>Supports three modes of temporal access:</p>
 * <ul>
 *   <li>{@link #snapshotAt(long)} — graph state at a single point in time</li>
 *   <li>{@link #windowBetween(long, long)} — graph state over a time range</li>
 *   <li>{@link #getTimePoints()} — all distinct timestamps in the graph</li>
 * </ul>
 *
 * <p>All generated subgraphs are independent copies (new vertices and edges
 * referencing the same Edge objects  so modifications won't affect the
 * original graph.</p>
 *
 * @author zalenix
 */
public class TemporalGraph {

    private final Graph<String, Edge> fullGraph;

    /**
     * Creates a TemporalGraph wrapping an existing JUNG graph.
     *
     * @param graph the full graph containing edges with optional timestamps
     * @throws IllegalArgumentException if graph is null
     */
    public TemporalGraph(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.fullGraph = graph;
    }

    /**
     * Returns the underlying full graph (all edges, all time periods).
     *
     * @return the full graph
     */
    public Graph<String, Edge> getFullGraph() {
        return fullGraph;
    }

    /**
     * Returns a snapshot of the graph at a specific point in time.
     * Only edges that are active at the given time (per {@link edge#isActiveAt(long)})
     * are included. Vertices with no active edges are excluded.
     *
     * @param time the point in time (epoch millis)
     * @return a new graph containing only edges active at {@code time}
     */
    public Graph<String, Edge> snapshotAt(long time) {
        Graph<String, Edge> snapshot = new UndirectedSparseGraph<>();
        for (Edge e : fullGraph.getEdges()) {
            if (e.isActiveAt(time)) {
                addEdgeToGraph(snapshot, e);
            }
        }
        return snapshot;
    }

    /**
     * Returns a subgraph containing all edges active during any part of the
     * given time window [start, end]. Vertices with no active edges are excluded.
     *
     * @param start window start (epoch millis, inclusive)
     * @param end window end (epoch millis, inclusive)
     * @return a new graph containing only edges active during the window
     * @throws IllegalArgumentException if start &gt; end
     */
    public Graph<String, Edge> windowBetween(long start, long end) {
        if (start > end) {
            throw new IllegalArgumentException(
                "Start time must not be after end time: " + start + " > " + end);
        }
        Graph<String, Edge> window = new UndirectedSparseGraph<>();
        for (Edge e : fullGraph.getEdges()) {
            if (e.isActiveDuring(start, end)) {
                addEdgeToGraph(window, e);
            }
        }
        return window;
    }

    /**
     * Returns all distinct timestamps present on edges in the graph,
     * sorted in ascending order. Untimed edges (null timestamp) are excluded.
     *
     * @return sorted list of distinct epoch-millis timestamps
     */
    public List<Long> getTimePoints() {
        TreeSet<Long> times = new TreeSet<>();
        for (Edge e : fullGraph.getEdges()) {
            if (e.getTimestamp() != null) {
                times.add(e.getTimestamp());
            }
            if (e.getEndTimestamp() != null) {
                times.add(e.getEndTimestamp());
            }
        }
        return new ArrayList<>(times);
    }

    /**
     * Returns the number of distinct time points in the graph.
     *
     * @return count of distinct timestamps
     */
    public int getTimePointCount() {
        return getTimePoints().size();
    }

    /**
     * Generates a series of graph snapshots by dividing the full time range
     * into equal-width windows. Useful for tracking network evolution over
     * uniform time periods.
     *
     * @param windowCount number of windows to divide the time range into
     * @return ordered list of (windowStart, graph) pairs
     * @throws IllegalArgumentException if windowCount &lt; 1
     * @throws IllegalStateException if the graph has no timestamped edges
     */
    public List<Map.Entry<Long, Graph<String, Edge>>> generateWindows(int windowCount) {
        if (windowCount < 1) {
            throw new IllegalArgumentException("windowCount must be at least 1");
        }
        List<Long> times = getTimePoints();
        if (times.isEmpty()) {
            throw new IllegalStateException(
                "Cannot generate windows: graph has no timestamped edges");
        }

        long minTime = times.get(0);
        long maxTime = times.get(times.size() - 1);
        long windowWidth = Math.max(1, (maxTime - minTime + 1) / windowCount);

        List<Map.Entry<Long, Graph<String, Edge>>> windows = new ArrayList<>();
        for (int i = 0; i < windowCount; i++) {
            long wStart = minTime + (i * windowWidth);
            long wEnd = (i == windowCount - 1) ? maxTime : wStart + windowWidth - 1;
            windows.add(new AbstractMap.SimpleImmutableEntry<>(wStart,
                windowBetween(wStart, wEnd)));
        }
        return windows;
    }

    /**
     * Adds an edge and its endpoints to a graph, skipping if the edge
     * or vertices already exist.
     */
    private void addEdgeToGraph(Graph<String, Edge> graph, Edge e) {
        String v1 = e.getVertex1();
        String v2 = e.getVertex2();
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        if (!graph.containsEdge(e)) {
            graph.addEdge(e, v1, v2);
        }
    }
}
