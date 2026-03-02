package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Shared graph traversal and adjacency utilities used by multiple analyzers.
 *
 * <p>Centralizes common operations that were previously duplicated across
 * {@link CommunityDetector}, {@link GraphDiameterAnalyzer},
 * {@link NodeCentralityAnalyzer}, {@link PageRankAnalyzer},
 * {@link ShortestPathFinder}, {@link LinkPredictionAnalyzer}, and
 * {@link GraphIsomorphismAnalyzer}.</p>
 *
 * <p>All methods are static and stateless — safe for concurrent use.</p>
 *
 * @author zalenix
 */
public final class GraphUtils {

    private GraphUtils() { /* utility class */ }

    /**
     * Returns the vertex at the other end of an edge from the given vertex.
     *
     * @param e       the edge
     * @param current the vertex we're "standing on"
     * @return the other endpoint, or {@code null} if {@code current} is not
     *         an endpoint of the edge
     */
    public static String getOtherEnd(edge e, String current) {
        String v1 = e.getVertex1();
        String v2 = e.getVertex2();
        if (current.equals(v1)) return v2;
        if (current.equals(v2)) return v1;
        return null;
    }

    /**
     * Builds an adjacency set map for all vertices in the graph.
     *
     * @param graph the JUNG graph
     * @return map from each vertex to its set of neighbor vertex IDs
     */
    public static Map<String, Set<String>> buildAdjacencyMap(
            Graph<String, edge> graph) {
        Map<String, Set<String>> adj = new HashMap<String, Set<String>>();
        for (String v : graph.getVertices()) {
            Set<String> neighbors = new HashSet<String>();
            Collection<String> graphNeighbors = graph.getNeighbors(v);
            if (graphNeighbors != null) {
                neighbors.addAll(graphNeighbors);
            }
            adj.put(v, neighbors);
        }
        return adj;
    }

    /**
     * BFS from a source vertex, returning distances (hop counts) to all
     * reachable vertices.
     *
     * @param graph  the JUNG graph
     * @param source the starting vertex
     * @return map from vertex ID to its BFS distance from source
     */
    public static Map<String, Integer> bfsDistances(
            Graph<String, edge> graph, String source) {
        Map<String, Integer> distances = new HashMap<String, Integer>();
        Queue<String> queue = new LinkedList<String>();
        distances.put(source, 0);
        queue.add(source);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDist = distances.get(current);
            for (edge e : graph.getIncidentEdges(current)) {
                String neighbor = getOtherEnd(e, current);
                if (neighbor != null && !distances.containsKey(neighbor)) {
                    distances.put(neighbor, currentDist + 1);
                    queue.add(neighbor);
                }
            }
        }
        return distances;
    }

    /**
     * BFS to discover the connected component containing a source vertex.
     *
     * @param graph  the JUNG graph
     * @param source the starting vertex
     * @return set of all vertices reachable from source (including source)
     */
    public static Set<String> bfsComponent(
            Graph<String, edge> graph, String source) {
        Set<String> component = new LinkedHashSet<String>();
        Queue<String> queue = new LinkedList<String>();
        component.add(source);
        queue.add(source);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (edge e : graph.getIncidentEdges(current)) {
                String neighbor = getOtherEnd(e, current);
                if (neighbor != null && !component.contains(neighbor)) {
                    component.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        return component;
    }

    /**
     * Finds all connected components of the graph.
     *
     * @param graph the JUNG graph
     * @return list of components, each a set of vertex IDs, sorted largest-first
     */
    public static List<Set<String>> findComponents(
            Graph<String, edge> graph) {
        Set<String> visited = new HashSet<String>();
        List<Set<String>> components = new ArrayList<Set<String>>();

        for (String vertex : graph.getVertices()) {
            if (!visited.contains(vertex)) {
                Set<String> component = bfsComponent(graph, vertex);
                visited.addAll(component);
                components.add(component);
            }
        }

        // Sort largest-first
        Collections.sort(components, new Comparator<Set<String>>() {
            public int compare(Set<String> a, Set<String> b) {
                return Integer.compare(b.size(), a.size());
            }
        });
        return components;
    }

    /**
     * Finds the largest connected component.
     *
     * @param graph the JUNG graph
     * @return set of vertices in the largest component, or empty set if graph is empty
     */
    public static Set<String> findLargestComponent(
            Graph<String, edge> graph) {
        List<Set<String>> components = findComponents(graph);
        return components.isEmpty() ? Collections.<String>emptySet() : components.get(0);
    }

    /**
     * Returns the set of common neighbors between two vertices.
     *
     * @param adjacency precomputed adjacency map
     * @param u         first vertex
     * @param v         second vertex
     * @return set of vertices adjacent to both u and v
     */
    public static Set<String> getCommonNeighbors(
            Map<String, Set<String>> adjacency, String u, String v) {
        Set<String> common = new HashSet<String>(adjacency.get(u));
        common.retainAll(adjacency.get(v));
        return common;
    }
}
