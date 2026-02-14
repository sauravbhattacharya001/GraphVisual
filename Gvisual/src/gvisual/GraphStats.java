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
    private final Vector<edge> friendEdges;
    private final Vector<edge> fsEdges;
    private final Vector<edge> classmateEdges;
    private final Vector<edge> strangerEdges;
    private final Vector<edge> studyGEdges;

    /**
     * @param graph         the current JUNG graph
     * @param friendEdges   friend edges (may include filtered-out edges)
     * @param fsEdges       familiar stranger edges
     * @param classmateEdges classmate edges
     * @param strangerEdges stranger edges
     * @param studyGEdges   study group edges
     */
    public GraphStats(Graph<String, edge> graph,
                      Vector<edge> friendEdges,
                      Vector<edge> fsEdges,
                      Vector<edge> classmateEdges,
                      Vector<edge> strangerEdges,
                      Vector<edge> studyGEdges) {
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

    /** Maximum degree among all visible nodes. */
    public int getMaxDegree() {
        int max = 0;
        for (String node : graph.getVertices()) {
            int deg = graph.degree(node);
            if (deg > max) max = deg;
        }
        return max;
    }

    /**
     * Returns the top-N nodes by degree (most connected).
     * Each entry is "nodeId (degree)".
     */
    public List<String> getTopNodes(int n) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>();
        for (String node : graph.getVertices()) {
            entries.add(new AbstractMap.SimpleEntry<String, Integer>(node, graph.degree(node)));
        }
        Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue().compareTo(a.getValue());
            }
        });
        List<String> result = new ArrayList<String>();
        int count = Math.min(n, entries.size());
        for (int i = 0; i < count; i++) {
            Map.Entry<String, Integer> entry = entries.get(i);
            result.add("Node " + entry.getKey() + " (" + entry.getValue() + ")");
        }
        return result;
    }

    /**
     * Number of isolated nodes (degree 0) in the visible graph.
     */
    public int getIsolatedNodeCount() {
        int count = 0;
        for (String node : graph.getVertices()) {
            if (graph.degree(node) == 0) count++;
        }
        return count;
    }

    /**
     * Average edge weight across all visible edges.
     */
    public double getAverageWeight() {
        if (graph.getEdgeCount() == 0) return 0.0;
        double total = 0;
        for (edge e : graph.getEdges()) {
            total += e.getWeight();
        }
        return total / graph.getEdgeCount();
    }
}
