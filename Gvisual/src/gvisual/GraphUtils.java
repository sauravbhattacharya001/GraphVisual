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
     * Build an undirected adjacency map restricted to a subset of vertices.
     * Only edges where both endpoints are in {@code vertices} are included.
     *
     * @param graph    the JUNG graph
     * @param vertices the vertex subset to include
     * @return adjacency map (vertex → set of neighbours within the subset)
     */
    public static Map<String, Set<String>> buildAdjacencyMap(
            Graph<String, edge> graph, Set<String> vertices) {
        Map<String, Set<String>> adj = new HashMap<String, Set<String>>();
        for (String v : vertices) {
            adj.put(v, new HashSet<String>());
        }
        for (edge e : graph.getEdges()) {
            Collection<String> eps = graph.getEndpoints(e);
            if (eps == null || eps.size() != 2) continue;
            Iterator<String> it = eps.iterator();
            String v1 = it.next();
            String v2 = it.next();
            if (vertices.contains(v1) && vertices.contains(v2)) {
                adj.get(v1).add(v2);
                adj.get(v2).add(v1);
            }
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

    /**
     * Checks whether the subgraph induced by the given vertices contains a cycle.
     * Works for both directed and undirected graphs.
     *
     * @param graph    the JUNG graph
     * @param vertices the vertex subset to check (only edges within this set are considered)
     * @param directed whether to treat edges as directed
     * @return true if the induced subgraph contains a cycle
     */
    public static boolean hasCycleInSubgraph(
            Graph<String, edge> graph, Set<String> vertices, boolean directed) {
        if (vertices.size() <= 1) return false;
        Set<String> visited = new HashSet<String>();
        Set<String> inStack = new HashSet<String>();
        for (String v : vertices) {
            if (!visited.contains(v)) {
                if (directed) {
                    if (hasCycleDFS_directed(graph, v, vertices, visited, inStack)) return true;
                } else {
                    if (hasCycleDFS_undirected(graph, v, null, vertices, visited)) return true;
                }
            }
        }
        return false;
    }

    private static boolean hasCycleDFS_undirected(
            Graph<String, edge> graph, String v, String parent,
            Set<String> vertices, Set<String> visited) {
        visited.add(v);
        for (String n : graph.getNeighbors(v)) {
            if (!vertices.contains(n)) continue;
            if (!visited.contains(n)) {
                if (hasCycleDFS_undirected(graph, n, v, vertices, visited)) return true;
            } else if (!n.equals(parent)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCycleDFS_directed(
            Graph<String, edge> graph, String v, Set<String> vertices,
            Set<String> visited, Set<String> inStack) {
        visited.add(v);
        inStack.add(v);
        for (String n : graph.getSuccessors(v)) {
            if (!vertices.contains(n)) continue;
            if (!visited.contains(n)) {
                if (hasCycleDFS_directed(graph, n, vertices, visited, inStack)) return true;
            } else if (inStack.contains(n)) {
                return true;
            }
        }
        inStack.remove(v);
        return false;
    }

    /**
     * Counts edges in the subgraph induced by the given vertex set.
     *
     * @param graph    the JUNG graph
     * @param vertices the vertex subset
     * @return number of edges where both endpoints are in the vertex set
     */
    public static int countEdgesInSubgraph(
            Graph<String, edge> graph, Set<String> vertices) {
        int count = 0;
        Set<edge> seen = new HashSet<edge>();
        for (String v : vertices) {
            for (edge e : graph.getIncidentEdges(v)) {
                if (seen.contains(e)) continue;
                boolean allIn = true;
                for (String ep : graph.getEndpoints(e)) {
                    if (!vertices.contains(ep)) { allIn = false; break; }
                }
                if (allIn) { seen.add(e); count++; }
            }
        }
        return count;
    }

    /**
     * Counts connected components in the subgraph induced by the given vertex set.
     *
     * @param graph    the JUNG graph
     * @param vertices the vertex subset
     * @return number of connected components within the subset
     */
    public static int countComponentsInSubgraph(
            Graph<String, edge> graph, Set<String> vertices) {
        Set<String> visited = new HashSet<String>();
        int components = 0;
        for (String v : vertices) {
            if (!visited.contains(v)) {
                components++;
                Queue<String> queue = new LinkedList<String>();
                queue.add(v);
                visited.add(v);
                while (!queue.isEmpty()) {
                    String curr = queue.poll();
                    for (String n : graph.getNeighbors(curr)) {
                        if (vertices.contains(n) && !visited.contains(n)) {
                            visited.add(n);
                            queue.add(n);
                        }
                    }
                }
            }
        }
        return components;
    }

    /**
     * Computes the cycle rank (circuit rank) of the subgraph induced by the
     * given vertex set: edges − vertices + components.
     *
     * @param graph    the JUNG graph
     * @param vertices the vertex subset
     * @return the cycle rank of the induced subgraph
     */
    public static int cycleRankOfSubgraph(
            Graph<String, edge> graph, Set<String> vertices) {
        int edges = countEdgesInSubgraph(graph, vertices);
        int comps = countComponentsInSubgraph(graph, vertices);
        return edges - vertices.size() + comps;
    }
}
