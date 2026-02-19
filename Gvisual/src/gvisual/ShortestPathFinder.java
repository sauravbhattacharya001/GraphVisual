package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Finds shortest paths in a JUNG graph using BFS (unweighted)
 * and Dijkstra-like traversal (weighted by edge weight).
 *
 * <p>Provides both hop-count-optimal and weight-optimal paths
 * between any two nodes in the network. Useful for analyzing
 * how closely connected individuals are in the social graph.</p>
 *
 * @author zalenix
 */
public class ShortestPathFinder {

    private final Graph<String, edge> graph;

    /**
     * Creates a new ShortestPathFinder for the given graph.
     *
     * @param graph the JUNG graph to search
     * @throws IllegalArgumentException if graph is null
     */
    public ShortestPathFinder(Graph<String, edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
    }

    /**
     * Represents a path between two nodes, including the ordered list
     * of vertices, the edges traversed, total hop count, and total weight.
     */
    public static class PathResult {
        private final List<String> vertices;
        private final List<edge> edges;
        private final double totalWeight;

        /**
         * @param vertices ordered list of vertex IDs from source to target
         * @param edges    ordered list of edges along the path
         * @param totalWeight sum of edge weights along the path
         */
        public PathResult(List<String> vertices, List<edge> edges, double totalWeight) {
            this.vertices = Collections.unmodifiableList(new ArrayList<String>(vertices));
            this.edges = Collections.unmodifiableList(new ArrayList<edge>(edges));
            this.totalWeight = totalWeight;
        }

        /** Ordered vertex IDs from source to target. */
        public List<String> getVertices() {
            return vertices;
        }

        /** Ordered edges from source to target. */
        public List<edge> getEdges() {
            return edges;
        }

        /** Number of hops (edges) in the path. */
        public int getHopCount() {
            return edges.size();
        }

        /** Sum of edge weights along the path. */
        public double getTotalWeight() {
            return totalWeight;
        }

        /**
         * Human-readable path description.
         * Example: "A → B → C (3 hops, weight 42.5)"
         */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < vertices.size(); i++) {
                if (i > 0) sb.append(" → ");
                sb.append(vertices.get(i));
            }
            sb.append(String.format(" (%d hop%s, weight %.1f)",
                    getHopCount(), getHopCount() == 1 ? "" : "s", totalWeight));
            return sb.toString();
        }
    }

    /**
     * Finds the shortest path by hop count (BFS) between source and target.
     *
     * @param source source vertex ID
     * @param target target vertex ID
     * @return the shortest path, or null if no path exists
     * @throws IllegalArgumentException if source or target is null or not in graph
     */
    public PathResult findShortestByHops(String source, String target) {
        validateVertex(source, "Source");
        validateVertex(target, "Target");

        if (source.equals(target)) {
            return new PathResult(
                    Collections.singletonList(source),
                    Collections.<edge>emptyList(),
                    0.0);
        }

        // BFS
        Map<String, String> predecessor = new HashMap<String, String>();
        Map<String, edge> predecessorEdge = new HashMap<String, edge>();
        Queue<String> queue = new LinkedList<String>();

        predecessor.put(source, null);
        queue.add(source);

        while (!queue.isEmpty()) {
            String current = queue.poll();

            if (current.equals(target)) {
                return buildPath(source, target, predecessor, predecessorEdge);
            }

            for (edge e : graph.getIncidentEdges(current)) {
                String neighbor = getOtherEnd(e, current);
                if (neighbor != null && !predecessor.containsKey(neighbor)) {
                    predecessor.put(neighbor, current);
                    predecessorEdge.put(neighbor, e);
                    queue.add(neighbor);
                }
            }
        }

        return null; // no path
    }

    /**
     * Finds the shortest path by total edge weight (Dijkstra) between source and target.
     *
     * <p>Uses a typed priority queue entry instead of encoding vertices as
     * double-array indices into a separate vertex list. This eliminates the
     * O(V) vertex-index map construction and avoids integer-to-double-to-integer
     * conversion overhead on every PQ operation.</p>
     *
     * @param source source vertex ID
     * @param target target vertex ID
     * @return the weight-optimal path, or null if no path exists
     * @throws IllegalArgumentException if source or target is null or not in graph
     */
    public PathResult findShortestByWeight(String source, String target) {
        validateVertex(source, "Source");
        validateVertex(target, "Target");

        if (source.equals(target)) {
            return new PathResult(
                    Collections.singletonList(source),
                    Collections.<edge>emptyList(),
                    0.0);
        }

        // Dijkstra with typed PQ entries (no vertex-index indirection)
        final Map<String, Double> dist = new HashMap<String, Double>();
        Map<String, String> predecessor = new HashMap<String, String>();
        Map<String, edge> predecessorEdge = new HashMap<String, edge>();

        // Priority queue using String[] wrapper: [0]=vertex, distance stored in dist map
        PriorityQueue<String> pq = new PriorityQueue<String>(11, new Comparator<String>() {
            public int compare(String a, String b) {
                Double da = dist.get(a);
                Double db = dist.get(b);
                if (da == null) da = Double.MAX_VALUE;
                if (db == null) db = Double.MAX_VALUE;
                return Double.compare(da, db);
            }
        });

        dist.put(source, 0.0);
        predecessor.put(source, null);
        pq.add(source);

        Set<String> visited = new HashSet<String>();

        while (!pq.isEmpty()) {
            String current = pq.poll();

            if (visited.contains(current)) continue;
            visited.add(current);

            if (current.equals(target)) {
                return buildPath(source, target, predecessor, predecessorEdge);
            }

            double currentDist = dist.get(current);

            for (edge e : graph.getIncidentEdges(current)) {
                String neighbor = getOtherEnd(e, current);
                if (neighbor == null || visited.contains(neighbor)) continue;

                double newDist = currentDist + Math.max(e.getWeight(), 0.001);
                Double oldDist = dist.get(neighbor);

                if (oldDist == null || newDist < oldDist) {
                    dist.put(neighbor, newDist);
                    predecessor.put(neighbor, current);
                    predecessorEdge.put(neighbor, e);
                    pq.add(neighbor);
                }
            }
        }

        return null; // no path
    }

    /**
     * Gets all vertices reachable from the given source via BFS.
     *
     * @param source the starting vertex
     * @return set of reachable vertex IDs (includes source)
     */
    public Set<String> getReachableVertices(String source) {
        validateVertex(source, "Source");

        Set<String> reachable = new LinkedHashSet<String>();
        Queue<String> queue = new LinkedList<String>();

        reachable.add(source);
        queue.add(source);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (edge e : graph.getIncidentEdges(current)) {
                String neighbor = getOtherEnd(e, current);
                if (neighbor != null && !reachable.contains(neighbor)) {
                    reachable.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return reachable;
    }

    /**
     * Checks whether two vertices are connected (in the same component).
     *
     * <p>Uses an early-termination BFS instead of computing the full
     * reachable set. This is O(component) in the worst case but returns
     * immediately when the target is found, which is faster for large
     * graphs where the target is close to the source.</p>
     */
    public boolean areConnected(String source, String target) {
        validateVertex(source, "Source");
        validateVertex(target, "Target");

        if (source.equals(target)) return true;

        Set<String> visited = new HashSet<String>();
        Queue<String> queue = new LinkedList<String>();

        visited.add(source);
        queue.add(source);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (edge e : graph.getIncidentEdges(current)) {
                String neighbor = getOtherEnd(e, current);
                if (neighbor != null && !visited.contains(neighbor)) {
                    if (neighbor.equals(target)) return true;
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return false;
    }

    // --- private helpers ---

    private void validateVertex(String vertex, String name) {
        if (vertex == null) {
            throw new IllegalArgumentException(name + " vertex must not be null");
        }
        if (!graph.containsVertex(vertex)) {
            throw new IllegalArgumentException(
                    name + " vertex '" + vertex + "' is not in the graph");
        }
    }

    private String getOtherEnd(edge e, String current) {
        String v1 = e.getVertex1();
        String v2 = e.getVertex2();
        if (current.equals(v1)) return v2;
        if (current.equals(v2)) return v1;
        return null;
    }

    private PathResult buildPath(String source, String target,
                                 Map<String, String> predecessor,
                                 Map<String, edge> predecessorEdge) {
        List<String> vertices = new ArrayList<String>();
        List<edge> edges = new ArrayList<edge>();
        double totalWeight = 0;

        String current = target;
        while (current != null) {
            vertices.add(current);
            edge e = predecessorEdge.get(current);
            if (e != null) {
                edges.add(e);
                totalWeight += e.getWeight();
            }
            current = predecessor.get(current);
        }

        Collections.reverse(vertices);
        Collections.reverse(edges);
        return new PathResult(vertices, edges, totalWeight);
    }
}
