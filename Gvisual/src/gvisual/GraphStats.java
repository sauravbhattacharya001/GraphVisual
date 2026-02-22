package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Computes network analysis metrics for a JUNG graph.
 * Provides node/edge counts, per-category breakdowns, density,
 * degree statistics, and identifies hub nodes.
 *
 * @author zalenix
 */
public class GraphStats {

    private final Graph<String, edge> graph;
    private final List<edge> friendEdges;
    private final List<edge> fsEdges;
    private final List<edge> classmateEdges;
    private final List<edge> strangerEdges;
    private final List<edge> studyGEdges;

    /**
     * @param graph         the current JUNG graph
     * @param friendEdges   friend edges (may include filtered-out edges)
     * @param fsEdges       familiar stranger edges
     * @param classmateEdges classmate edges
     * @param strangerEdges stranger edges
     * @param studyGEdges   study group edges
     */
    public GraphStats(Graph<String, edge> graph,
                      List<edge> friendEdges,
                      List<edge> fsEdges,
                      List<edge> classmateEdges,
                      List<edge> strangerEdges,
                      List<edge> studyGEdges) {
        this.graph = graph;
        this.friendEdges = friendEdges;
        this.fsEdges = fsEdges;
        this.classmateEdges = classmateEdges;
        this.strangerEdges = strangerEdges;
        this.studyGEdges = studyGEdges;
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
        return (2.0 * e) / (v * (v - 1));
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
                        new Comparator<Map.Entry<String, Integer>>() {
                            public int compare(Map.Entry<String, Integer> a,
                                               Map.Entry<String, Integer> b) {
                                return a.getValue().compareTo(b.getValue());
                            }
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
