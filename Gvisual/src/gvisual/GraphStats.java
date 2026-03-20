package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;
import java.util.EnumMap;

/**
 * Computes network analysis metrics for a JUNG graph.
 * Provides node/edge counts, per-category breakdowns, density,
 * degree statistics, and identifies hub nodes.
 *
 * @author zalenix
 */
public class GraphStats {

    private final Graph<String, edge> graph;
    private final Map<EdgeType, List<edge>> edgesByType;

    // Legacy accessors kept for backward compat
    private final List<edge> friendEdges;
    private final List<edge> fsEdges;
    private final List<edge> classmateEdges;
    private final List<edge> strangerEdges;
    private final List<edge> studyGEdges;

    /**
     * Creates a GraphStats from a typed edge map.
     * This is the preferred constructor — avoids long positional parameter
     * lists and scales cleanly when new edge types are added.
     *
     * @param graph      the current JUNG graph
     * @param edgesByType map from EdgeType to the edges of that type
     */
    public GraphStats(Graph<String, edge> graph,
                      Map<EdgeType, List<edge>> edgesByType) {
        this.graph = graph;
        this.edgesByType = new EnumMap<EdgeType, List<edge>>(EdgeType.class);
        for (EdgeType t : EdgeType.values()) {
            this.edgesByType.put(t, edgesByType.containsKey(t)
                    ? edgesByType.get(t) : Collections.<edge>emptyList());
        }
        // Populate legacy fields from the map
        this.friendEdges = this.edgesByType.get(EdgeType.FRIEND);
        this.fsEdges = this.edgesByType.get(EdgeType.FAMILIAR_STRANGER);
        this.classmateEdges = this.edgesByType.get(EdgeType.CLASSMATE);
        this.strangerEdges = this.edgesByType.get(EdgeType.STRANGER);
        this.studyGEdges = this.edgesByType.get(EdgeType.STUDY_GROUP);
    }

    /**
     * Legacy constructor — delegates to the map-based constructor.
     *
     * @param graph         the current JUNG graph
     * @param friendEdges   friend edges (may include filtered-out edges)
     * @param fsEdges       familiar stranger edges
     * @param classmateEdges classmate edges
     * @param strangerEdges stranger edges
     * @param studyGEdges   study group edges
     * @deprecated Use {@link #GraphStats(Graph, Map)} instead.
     */
    @Deprecated
    public GraphStats(Graph<String, edge> graph,
                      List<edge> friendEdges,
                      List<edge> fsEdges,
                      List<edge> classmateEdges,
                      List<edge> strangerEdges,
                      List<edge> studyGEdges) {
        this(graph, buildEdgeMap(friendEdges, fsEdges, classmateEdges,
                strangerEdges, studyGEdges));
    }

    private static Map<EdgeType, List<edge>> buildEdgeMap(
            List<edge> friend, List<edge> fs, List<edge> classmate,
            List<edge> stranger, List<edge> studyG) {
        Map<EdgeType, List<edge>> map = new EnumMap<EdgeType, List<edge>>(EdgeType.class);
        map.put(EdgeType.FRIEND, friend);
        map.put(EdgeType.FAMILIAR_STRANGER, fs);
        map.put(EdgeType.CLASSMATE, classmate);
        map.put(EdgeType.STRANGER, stranger);
        map.put(EdgeType.STUDY_GROUP, studyG);
        return map;
    }

    /**
     * Returns the edge list for a given type.
     *
     * @param type the edge type to query
     * @return unmodifiable list of edges (never null)
     */
    public List<edge> getEdges(EdgeType type) {
        List<edge> list = edgesByType.get(type);
        return list != null ? Collections.unmodifiableList(list)
                : Collections.<edge>emptyList();
    }

    /** Total number of nodes in the visible graph. */
    public int getNodeCount() {
        return graph.getVertexCount();
    }

    /** Total number of edges in the visible graph. */
    public int getVisibleEdgeCount() {
        return graph.getEdgeCount();
    }

    /** Total edges across all categories (including filtered-out). */
    public int getTotalEdgeCount() {
        return friendEdges.size() + fsEdges.size() + classmateEdges.size()
                + strangerEdges.size() + studyGEdges.size();
    }

    /** Number of friend edges loaded for this timestamp. */
    public int getFriendCount() {
        return friendEdges.size();
    }

    /** Number of familiar stranger edges. */
    public int getFsCount() {
        return fsEdges.size();
    }

    /** Number of classmate edges. */
    public int getClassmateCount() {
        return classmateEdges.size();
    }

    /** Number of stranger edges. */
    public int getStrangerCount() {
        return strangerEdges.size();
    }

    /** Number of study group edges. */
    public int getStudyGroupCount() {
        return studyGEdges.size();
    }

    /**
     * Graph density for an undirected graph: 2*|E| / (|V| * (|V|-1)).
     * Returns 0 if fewer than 2 nodes.
     */
    public double getDensity() {
        int v = getNodeCount();
        int e = getVisibleEdgeCount();
        if (v < 2) return 0.0;
        return (2.0 * e) / ((long) v * (v - 1));
    }

    /**
     * Average degree of visible nodes.
     * For undirected graph: 2*|E| / |V|.
     */
    public double getAverageDegree() {
        int v = getNodeCount();
        int e = getVisibleEdgeCount();
        if (v == 0) return 0.0;
        return (2.0 * e) / v;
    }

    /**
     * Cached per-vertex statistics computed once, reused by multiple methods.
     * Avoids iterating all vertices 3+ separate times for max degree, isolated
     * count, and top-N queries.
     */
    private int cachedMaxDegree = -1;
    private int cachedIsolatedCount = -1;
    private List<Map.Entry<String, Integer>> cachedDegreeEntries;

    /**
     * Computes and caches per-vertex degree data in a single pass.
     * Subsequent calls to getMaxDegree(), getIsolatedNodeCount(), and
     * getTopNodes() all reuse this cache instead of each iterating
     * all vertices independently.
     */
    private void ensureVertexStatsComputed() {
        if (cachedMaxDegree >= 0) return; // already computed

        cachedMaxDegree = 0;
        cachedIsolatedCount = 0;
        cachedDegreeEntries = new ArrayList<Map.Entry<String, Integer>>();

        for (String node : graph.getVertices()) {
            int deg = graph.degree(node);
            cachedDegreeEntries.add(new AbstractMap.SimpleEntry<String, Integer>(node, deg));
            if (deg > cachedMaxDegree) cachedMaxDegree = deg;
            if (deg == 0) cachedIsolatedCount++;
        }
    }

    /** Maximum degree among all visible nodes. */
    public int getMaxDegree() {
        ensureVertexStatsComputed();
        return cachedMaxDegree;
    }

    /**
     * Returns the top-N nodes by degree (most connected).
     * Each entry is "nodeId (degree)".
     *
     * <p>Uses a partial-sort (min-heap of size N) instead of fully sorting
     * all vertices. For graphs with many nodes but small N, this reduces
     * complexity from O(V log V) to O(V log N).</p>
     */
    public List<String> getTopNodes(int n) {
        ensureVertexStatsComputed();

        if (n <= 0 || cachedDegreeEntries.isEmpty()) {
            return new ArrayList<String>();
        }

        // Use a min-heap of size n for O(V log N) partial sort
        PriorityQueue<Map.Entry<String, Integer>> minHeap =
                new PriorityQueue<Map.Entry<String, Integer>>(n + 1,
                        (Map.Entry<String, Integer> a,
                                               Map.Entry<String, Integer> b) -> {
                                return a.getValue().compareTo(b.getValue());
                            });

        for (Map.Entry<String, Integer> entry : cachedDegreeEntries) {
            minHeap.add(entry);
            if (minHeap.size() > n) {
                minHeap.poll(); // evict smallest
            }
        }

        // Extract in descending order
        List<String> result = new ArrayList<String>(minHeap.size());
        while (!minHeap.isEmpty()) {
            Map.Entry<String, Integer> entry = minHeap.poll();
            result.add(0, "Node " + entry.getKey() + " (" + entry.getValue() + ")");
        }
        return result;
    }

    /**
     * Number of isolated nodes (degree 0) in the visible graph.
     */
    public int getIsolatedNodeCount() {
        ensureVertexStatsComputed();
        return cachedIsolatedCount;
    }

    /**
     * Cached edge weight statistics computed once.
     */
    private double cachedTotalWeight = -1.0;

    /**
     * Average edge weight across all visible edges.
     * Caches the total weight sum to avoid re-iterating edges if called
     * multiple times.
     */
    public double getAverageWeight() {
        if (graph.getEdgeCount() == 0) return 0.0;
        if (cachedTotalWeight < 0) {
            cachedTotalWeight = 0;
            for (edge e : graph.getEdges()) {
                cachedTotalWeight += e.getWeight();
            }
        }
        return cachedTotalWeight / graph.getEdgeCount();
    }
}
