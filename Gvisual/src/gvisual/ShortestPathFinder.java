package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Finds shortest paths in a JUNG graph using BFS (unweighted)
 * and Dijkstra-like traversal (weighted by Edge weight).
 *
 * <p>Provides both hop-count-optimal and weight-optimal paths
 * between any two nodes in the network. Useful for analyzing
 * how closely connected individuals are in the social graph.</p>
 *
 * @author zalenix
 */
public class ShortestPathFinder {

    private final Graph<String, Edge> graph;

    /**
     * Creates a new ShortestPathFinder for the given graph.
     *
     * @param graph the JUNG graph to search
     * @throws IllegalArgumentException if graph is null
     */
    public ShortestPathFinder(Graph<String, Edge> graph) {
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
        private final List<Edge> edges;
        private final double totalWeight;

        /**
         * @param vertices ordered list of vertex IDs from source to target
         * @param edges    ordered list of edges along the path
         * @param totalWeight sum of Edge weights along the path
         */
        public PathResult(List<String> vertices, List<Edge> edges, double totalWeight) {
            this.vertices = Collections.unmodifiableList(new ArrayList<String>(vertices));
            this.edges = Collections.unmodifiableList(new ArrayList<Edge>(edges));
            this.totalWeight = totalWeight;
        }

        /** Ordered vertex IDs from source to target. */
        public List<String> getVertices() {
            return vertices;
        }

        /** Ordered edges from source to target. */
        public List<Edge> getEdges() {
            return edges;
        }

        /** Number of hops (edges) in the path. */
        public int getHopCount() {
            return edges.size();
        }

        /** Sum of Edge weights along the path. */
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
                    Collections.<Edge>emptyList(),
                    0.0);
        }

        // BFS
        Map<String, String> predecessor = new HashMap<String, String>();
        Map<String, Edge> predecessorEdge = new HashMap<String, Edge>();
        Queue<String> queue = new LinkedList<String>();

        predecessor.put(source, null);
        queue.add(source);

        while (!queue.isEmpty()) {
            String current = queue.poll();

            for (Edge e : graph.getIncidentEdges(current)) {
                String neighbor = getOtherEnd(e, current);
                if (neighbor != null && !predecessor.containsKey(neighbor)) {
                    predecessor.put(neighbor, current);
                    predecessorEdge.put(neighbor, e);
                    // Early termination: return as soon as we discover
                    // the target instead of waiting until it is dequeued.
                    // In BFS on unweighted graphs, the first discovery is
                    // always optimal, so this can skip exploring an entire
                    // BFS frontier layer.
                    if (neighbor.equals(target)) {
                        return buildPath(source, target, predecessor, predecessorEdge);
                    }
                    queue.add(neighbor);
                }
            }
        }

        return null; // no path
    }

    /**
     * Finds the shortest path by total Edge weight (Dijkstra) between source and target.
     *
     * <p>Uses a typed {@link DijkstraEntry} in the priority queue for clean
     * vertex lookups without index indirection or double-to-int casting.</p>
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
                    Collections.<Edge>emptyList(),
                    0.0);
        }

        // Dijkstra with immutable PQ entries to avoid heap corruption.
        // Java's PriorityQueue does not re-heapify when a comparator's
        // backing data changes, so each entry must capture its distance
        // at insertion time. Stale entries are skipped via the visited set.
        final Map<String, Double> dist = new HashMap<String, Double>();
        Map<String, String> predecessor = new HashMap<String, String>();
        Map<String, Edge> predecessorEdge = new HashMap<String, Edge>();

        PriorityQueue<DijkstraEntry> pq = new PriorityQueue<DijkstraEntry>();

        dist.put(source, 0.0);
        predecessor.put(source, null);
        pq.add(new DijkstraEntry(0.0, source));

        Set<String> visited = new HashSet<String>();

        while (!pq.isEmpty()) {
            DijkstraEntry entry = pq.poll();
            double entryDist = entry.distance;
            String current = entry.vertex;

            if (visited.contains(current)) continue;
            visited.add(current);

            if (current.equals(target)) {
                return buildPath(source, target, predecessor, predecessorEdge, true);
            }

            for (Edge e : graph.getIncidentEdges(current)) {
                String neighbor = getOtherEnd(e, current);
                if (neighbor == null || visited.contains(neighbor)) continue;

                double edgeWeight = e.getWeight();
                if (edgeWeight < 0) {
                    throw new IllegalArgumentException(
                            "Dijkstra does not support negative Edge weights. " +
                            "Edge between '" + current + "' and '" + neighbor +
                            "' has weight " + edgeWeight);
                }
                // Default unset weights (0.0) to 1.0 to match GraphUtils.dijkstra
                // and prevent zero-weight edges from collapsing all distances.
                if (edgeWeight == 0) edgeWeight = 1.0;
                double newDist = entryDist + edgeWeight;
                Double oldDist = dist.get(neighbor);

                if (oldDist == null || newDist < oldDist) {
                    dist.put(neighbor, newDist);
                    predecessor.put(neighbor, current);
                    predecessorEdge.put(neighbor, e);

                    pq.add(new DijkstraEntry(newDist, neighbor));
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
            for (Edge e : graph.getIncidentEdges(current)) {
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
            for (Edge e : graph.getIncidentEdges(current)) {
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

    // --- Dijkstra priority-queue entry ---

    /**
     * Typed PQ entry replacing the previous {@code double[]} hack that required
     * a parallel {@code vertexIndex} list and {@code vertexToIndex} map for
     * int-to-vertex lookups. This eliminates the O(V) index bookkeeping and
     * the fragile double-to-int casting on every PQ poll.
     */
    private static final class DijkstraEntry implements Comparable<DijkstraEntry> {
        final double distance;
        final String vertex;

        DijkstraEntry(double distance, String vertex) {
            this.distance = distance;
            this.vertex = vertex;
        }

        @Override
        public int compareTo(DijkstraEntry other) {
            return Double.compare(this.distance, other.distance);
        }
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

    private String getOtherEnd(Edge e, String current) {
        return GraphUtils.getOtherEnd(e, current);
    }

    private PathResult buildPath(String source, String target,
                                 Map<String, String> predecessor,
                                 Map<String, Edge> predecessorEdge) {
        return buildPath(source, target, predecessor, predecessorEdge, false);
    }

    /**
     * Builds a PathResult by tracing predecessors from target back to source.
     *
     * @param normalizeZeroWeights if true, treat zero-weight edges as weight 1.0
     *                             (must match the convention used by the caller,
     *                             e.g. Dijkstra normalizes zero weights to 1.0)
     */
    private PathResult buildPath(String source, String target,
                                 Map<String, String> predecessor,
                                 Map<String, Edge> predecessorEdge,
                                 boolean normalizeZeroWeights) {
        List<String> vertices = new ArrayList<String>();
        List<Edge> edges = new ArrayList<Edge>();
        double totalWeight = 0;

        String current = target;
        while (current != null) {
            vertices.add(current);
            Edge e = predecessorEdge.get(current);
            if (e != null) {
                edges.add(e);
                double w = e.getWeight();
                if (normalizeZeroWeights && w == 0) w = 1.0;
                totalWeight += w;
            }
            current = predecessor.get(current);
        }

        Collections.reverse(vertices);
        Collections.reverse(edges);
        return new PathResult(vertices, edges, totalWeight);
    }
}
