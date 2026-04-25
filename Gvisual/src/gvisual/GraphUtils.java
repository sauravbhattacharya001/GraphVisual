package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
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
     * Returns the vertex at the other end of an Edge from the given vertex.
     *
     * @param e       the Edge
     * @param current the vertex we're "standing on"
     * @return the other endpoint, or {@code null} if {@code current} is not
     *         an endpoint of the Edge
     */
    public static String getOtherEnd(Edge e, String current) {
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
            Graph<String, Edge> graph) {
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
            Graph<String, Edge> graph, Set<String> vertices) {
        Map<String, Set<String>> adj = new HashMap<String, Set<String>>();
        for (String v : vertices) {
            adj.put(v, new HashSet<String>());
        }
        for (Edge e : graph.getEdges()) {
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
            Graph<String, Edge> graph, String source) {
        Map<String, Integer> distances = new HashMap<String, Integer>();
        ArrayDeque<String> queue = new ArrayDeque<String>();
        distances.put(source, 0);
        queue.add(source);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDist = distances.get(current);
            Collection<Edge> incidentEdges = graph.getIncidentEdges(current);
            if (incidentEdges == null) continue;
            for (Edge e : incidentEdges) {
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
            Graph<String, Edge> graph, String source) {
        Set<String> component = new LinkedHashSet<String>();
        ArrayDeque<String> queue = new ArrayDeque<String>();
        component.add(source);
        queue.add(source);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            Collection<Edge> incidentEdges = graph.getIncidentEdges(current);
            if (incidentEdges == null) continue;
            for (Edge e : incidentEdges) {
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
            Graph<String, Edge> graph) {
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
        Collections.sort(components, (Set<String> a, Set<String> b) -> {
                return Integer.compare(b.size(), a.size());
            });
        return components;
    }

    /**
     * Returns the component count and the size of the largest connected
     * component in a single BFS traversal — avoids the two separate
     * traversals of {@code countComponents} + {@code findLargestComponent}.
     *
     * @param graph the JUNG graph
     * @return int[2]: [0] = component count, [1] = largest component size
     */
    public static int[] countAndLargest(Graph<String, Edge> graph) {
        Set<String> visited = new HashSet<String>();
        int count = 0;
        int largest = 0;
        for (String vertex : graph.getVertices()) {
            if (!visited.contains(vertex)) {
                count++;
                int compSize = 0;
                ArrayDeque<String> queue = new ArrayDeque<String>();
                visited.add(vertex);
                queue.add(vertex);
                while (!queue.isEmpty()) {
                    String current = queue.poll();
                    compSize++;
                    Collection<Edge> incident = graph.getIncidentEdges(current);
                    if (incident == null) continue;
                    for (Edge e : incident) {
                        String neighbor = getOtherEnd(e, current);
                        if (neighbor != null && !visited.contains(neighbor)) {
                            visited.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
                if (compSize > largest) largest = compSize;
            }
        }
        return new int[]{count, largest};
    }

    /**
     * Counts the number of connected components without building or sorting
     * the full component list.  Use this instead of
     * {@code findComponents(graph).size()} when only the count is needed.
     *
     * @param graph the JUNG graph
     * @return number of connected components
     */
    public static int countComponents(Graph<String, Edge> graph) {
        Set<String> visited = new HashSet<String>();
        int count = 0;
        for (String vertex : graph.getVertices()) {
            if (!visited.contains(vertex)) {
                count++;
                // Inline BFS to mark visited without building a component set
                ArrayDeque<String> queue = new ArrayDeque<String>();
                visited.add(vertex);
                queue.add(vertex);
                while (!queue.isEmpty()) {
                    String current = queue.poll();
                    Collection<Edge> incident = graph.getIncidentEdges(current);
                    if (incident == null) continue;
                    for (Edge e : incident) {
                        String neighbor = getOtherEnd(e, current);
                        if (neighbor != null && !visited.contains(neighbor)) {
                            visited.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }
        return count;
    }

    /**
     * Finds the largest connected component.
     *
     * @param graph the JUNG graph
     * @return set of vertices in the largest component, or empty set if graph is empty
     */
    public static Set<String> findLargestComponent(
            Graph<String, Edge> graph) {
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
        Set<String> uNeighbors = adjacency.get(u);
        Set<String> vNeighbors = adjacency.get(v);
        if (uNeighbors == null || vNeighbors == null) {
            return new HashSet<String>();
        }
        Set<String> common = new HashSet<String>(uNeighbors);
        common.retainAll(vNeighbors);
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
            Graph<String, Edge> graph, Set<String> vertices, boolean directed) {
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
            Graph<String, Edge> graph, String v, String parent,
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
            Graph<String, Edge> graph, String v, Set<String> vertices,
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
            Graph<String, Edge> graph, Set<String> vertices) {
        // Count internal edges via adjacency: for each vertex in the subset,
        // count neighbors also in the subset. Each undirected edge is counted
        // twice (once from each endpoint), so divide by 2.
        // This avoids allocating a HashSet<Edge> and the O(E) getEndpoints calls.
        int doubleCount = 0;
        for (String v : vertices) {
            Collection<String> nbrs = graph.getNeighbors(v);
            if (nbrs == null) continue;
            for (String n : nbrs) {
                if (vertices.contains(n)) {
                    doubleCount++;
                }
            }
        }
        return doubleCount / 2;
    }

    /**
     * Counts connected components in the subgraph induced by the given vertex set.
     *
     * @param graph    the JUNG graph
     * @param vertices the vertex subset
     * @return number of connected components within the subset
     */
    public static int countComponentsInSubgraph(
            Graph<String, Edge> graph, Set<String> vertices) {
        Set<String> visited = new HashSet<String>();
        int components = 0;
        for (String v : vertices) {
            if (!visited.contains(v)) {
                components++;
                ArrayDeque<String> queue = new ArrayDeque<String>();
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
            Graph<String, Edge> graph, Set<String> vertices) {
        int edges = countEdgesInSubgraph(graph, vertices);
        int comps = countComponentsInSubgraph(graph, vertices);
        return edges - vertices.size() + comps;
    }

    // ── Clustering coefficient ────────────────────────────────────────

    /**
     * Computes the local clustering coefficient for a single vertex.
     * <p>C(v) = 2 * triangles(v) / (deg(v) * (deg(v) - 1))</p>
     *
     * @param graph the JUNG graph
     * @param v     the vertex
     * @return clustering coefficient in [0, 1], or 0 if degree &lt; 2
     */
    public static double clusteringCoefficient(
            Graph<String, Edge> graph, String v) {
        Collection<String> neighbors = graph.getNeighbors(v);
        if (neighbors == null) return 0.0;
        List<String> nList = new ArrayList<String>(neighbors);
        int k = nList.size();
        if (k < 2) return 0.0;
        int links = 0;
        for (int i = 0; i < k; i++) {
            for (int j = i + 1; j < k; j++) {
                if (graph.isNeighbor(nList.get(i), nList.get(j))) links++;
            }
        }
        return (2.0 * links) / (k * (k - 1));
    }

    /**
     * Computes the average clustering coefficient across all vertices.
     *
     * @param graph the JUNG graph
     * @return average clustering coefficient, or 0 if the graph is empty
     */
    public static double avgClusteringCoefficient(
            Graph<String, Edge> graph) {
        int n = graph.getVertexCount();
        if (n == 0) return 0.0;
        double sum = 0.0;
        for (String v : graph.getVertices()) {
            sum += clusteringCoefficient(graph, v);
        }
        return sum / n;
    }

    // ── Graph copy ──────────────────────────────────────────────────────

    /**
     * Creates a deep copy of a graph, preserving all vertices, edges, Edge
     * types, weights, and labels.
     *
     * @param graph the graph to copy
     * @return a new graph with identical structure
     */
    public static Graph<String, Edge> copyGraph(Graph<String, Edge> graph) {
        Graph<String, Edge> copy = new UndirectedSparseGraph<String, Edge>();
        for (String v : graph.getVertices()) {
            copy.addVertex(v);
        }
        for (Edge e : graph.getEdges()) {
            Collection<String> endpoints = graph.getEndpoints(e);
            Iterator<String> it = endpoints.iterator();
            String v1 = it.next();
            String v2 = it.next();
            Edge newEdge = new Edge(e.getType(), v1, v2);
            newEdge.setWeight(e.getWeight());
            newEdge.setLabel(e.getLabel());
            copy.addEdge(newEdge, v1, v2);
        }
        return copy;
    }

    // ── Indexed graph representation ─────────────────────────────────

    /**
     * Array-based indexed representation of a graph's vertices and
     * adjacency structure. Avoids repeated HashMap allocation when
     * multiple algorithms need the same indexed layout (e.g. Brandes
     * betweenness and global-efficiency BFS).
     *
     * <p>Instances are cheap to create and immutable once built.</p>
     */
    public static final class IndexedGraph {
        /** Ordered vertex labels. */
        public final List<String> vertexList;
        /** Vertex label → integer index. */
        public final Map<String, Integer> vertexIndex;
        /** Adjacency lists in integer-index space. */
        public final int[][] adjLists;
        /** Number of vertices. */
        public final int n;

        /**
         * Builds an indexed representation from a JUNG graph.
         *
         * @param graph the source graph
         */
        public IndexedGraph(Graph<String, Edge> graph) {
            n = graph.getVertexCount();
            vertexList = new ArrayList<String>(graph.getVertices());
            vertexIndex = new HashMap<String, Integer>(n * 2);
            for (int i = 0; i < n; i++) {
                vertexIndex.put(vertexList.get(i), i);
            }

            adjLists = new int[n][];
            for (int i = 0; i < n; i++) {
                String node = vertexList.get(i);
                Collection<String> neighbors = graph.getNeighbors(node);
                List<Integer> adj = new ArrayList<Integer>();
                if (neighbors != null) {
                    for (String nb : neighbors) {
                        Integer idx = vertexIndex.get(nb);
                        if (idx != null) adj.add(idx);
                    }
                }
                adjLists[i] = new int[adj.size()];
                for (int j = 0; j < adj.size(); j++) {
                    adjLists[i][j] = adj.get(j);
                }
            }
        }
    }

    // ── Betweenness Centrality (array-based Brandes) ──────────────────

    /**
     * Computes betweenness centrality for all vertices using Brandes'
     * algorithm with array-based storage. Avoids per-source HashMap
     * allocation, using indexed arrays for sigma, distance, delta, and
     * predecessor lists.
     *
     * <p>For undirected graphs the raw scores are halved (each shortest
     * path is counted from both endpoints).</p>
     *
     * @param graph the graph
     * @return map from vertex ID to betweenness centrality score
     */
    public static Map<String, Double> computeBetweenness(Graph<String, Edge> graph) {
        int n = graph.getVertexCount();
        if (n == 0) return Collections.emptyMap();

        IndexedGraph ig = new IndexedGraph(graph);
        int[][] adjLists = ig.adjLists;

        double[] bc = new double[n];

        // Reusable arrays (allocated once, cleared per source)
        double[] sigma = new double[n];
        int[] dist = new int[n];
        double[] delta = new double[n];
        @SuppressWarnings("unchecked")
        List<Integer>[] pred = new List[n];
        for (int i = 0; i < n; i++) {
            pred[i] = new ArrayList<Integer>();
        }
        int[] stack = new int[n];
        int stackTop;
        int[] queue = new int[n];

        for (int s = 0; s < n; s++) {
            // Reset arrays
            Arrays.fill(sigma, 0.0);
            Arrays.fill(dist, -1);
            Arrays.fill(delta, 0.0);
            for (int i = 0; i < n; i++) pred[i].clear();

            sigma[s] = 1.0;
            dist[s] = 0;
            stackTop = 0;
            int qHead = 0, qTail = 0;
            queue[qTail++] = s;

            while (qHead < qTail) {
                int v = queue[qHead++];
                stack[stackTop++] = v;
                for (int w : adjLists[v]) {
                    if (dist[w] < 0) {
                        queue[qTail++] = w;
                        dist[w] = dist[v] + 1;
                    }
                    if (dist[w] == dist[v] + 1) {
                        sigma[w] += sigma[v];
                        pred[w].add(v);
                    }
                }
            }

            // Back-propagation
            while (stackTop > 0) {
                int w = stack[--stackTop];
                for (int v : pred[w]) {
                    delta[v] += (sigma[v] / sigma[w]) * (1.0 + delta[w]);
                }
                if (w != s) {
                    bc[w] += delta[w];
                }
            }
        }

        // Halve for undirected and build result map
        Map<String, Double> result = new LinkedHashMap<String, Double>();
        for (int i = 0; i < n; i++) {
            result.put(ig.vertexList.get(i), bc[i] / 2.0);
        }
        return result;
    }

    // ── Global Efficiency (array-based BFS) ───────────────────────────

    /**
     * Computes the global efficiency of a graph:
     * E = (2 / (n*(n-1))) * Σ_{i<j} 1/d(i,j)
     *
     * <p>Uses array-based BFS instead of HashMap-based to minimize
     * allocation overhead, especially when called repeatedly during
     * resilience simulations.</p>
     *
     * @param graph the graph
     * @return global efficiency in [0, 1]
     */
    public static double globalEfficiency(Graph<String, Edge> graph) {
        int n = graph.getVertexCount();
        if (n <= 1) return 0.0;

        IndexedGraph ig = new IndexedGraph(graph);
        int[][] adjLists = ig.adjLists;

        // Reusable BFS arrays
        int[] dist = new int[n];
        int[] queue = new int[n];

        double sum = 0.0;
        for (int source = 0; source < n; source++) {
            Arrays.fill(dist, -1);
            dist[source] = 0;
            int qHead = 0, qTail = 0;
            queue[qTail++] = source;

            while (qHead < qTail) {
                int v = queue[qHead++];
                for (int w : adjLists[v]) {
                    if (dist[w] < 0) {
                        dist[w] = dist[v] + 1;
                        queue[qTail++] = w;
                    }
                }
            }

            // Only count j > source to avoid double-counting
            for (int j = source + 1; j < n; j++) {
                if (dist[j] > 0) {
                    sum += 1.0 / dist[j];
                }
            }
        }

        return (2.0 * sum) / ((long) n * (n - 1));
    }

    // ── Weighted shortest paths (Dijkstra) ────────────────────────────

    /**
     * Result of a single-source Dijkstra computation: distances and
     * predecessor map for path reconstruction.
     */
    public static class DijkstraResult {
        /** Shortest distance from source to each reachable vertex. */
        public final Map<String, Double> dist;
        /** Predecessor on the shortest path (vertex → its predecessor). */
        public final Map<String, String> prev;

        public DijkstraResult(Map<String, Double> dist, Map<String, String> prev) {
            this.dist = dist;
            this.prev = prev;
        }
    }

    /**
     * Runs Dijkstra's algorithm from a single source vertex, returning
     * shortest distances and predecessors for all reachable vertices.
     *
     * <p>Uses a visited set to avoid re-processing settled nodes, and
     * immutable PQ entries to handle Java's non-updatable PriorityQueue.
     * Edge weights are taken from {@link Edge#getWeight()}; non-positive
     * weights default to 1.0.</p>
     *
     * @param graph  the JUNG graph
     * @param source the source vertex
     * @return distances and predecessors for all reachable vertices
     */
    public static DijkstraResult dijkstra(Graph<String, Edge> graph, String source) {
        Map<String, Double> dist = new HashMap<String, Double>();
        Map<String, String> prev = new HashMap<String, String>();
        Set<String> visited = new HashSet<String>();

        PriorityQueue<DijkstraEntry> pq = new PriorityQueue<DijkstraEntry>();

        dist.put(source, 0.0);
        pq.add(new DijkstraEntry(0.0, source));

        while (!pq.isEmpty()) {
            DijkstraEntry entry = pq.poll();
            double entryDist = entry.distance;
            String u = entry.vertex;

            if (visited.contains(u)) continue;
            visited.add(u);

            // Skip stale PQ entries
            Double uDist = dist.get(u);
            if (uDist == null || entryDist > uDist) continue;

            for (Edge e : graph.getIncidentEdges(u)) {
                String v = getOtherEnd(e, u);
                if (v == null || visited.contains(v)) continue;

                double w = e.getWeight() > 0 ? e.getWeight() : 1.0;
                double newDist = entryDist + w;
                Double oldDist = dist.get(v);

                if (oldDist == null || newDist < oldDist) {
                    dist.put(v, newDist);
                    prev.put(v, u);
                    pq.add(new DijkstraEntry(newDist, v));
                }
            }
        }
        return new DijkstraResult(dist, prev);
    }

    /**
     * Typed priority-queue entry for Dijkstra's algorithm. Replaces the
     * previous {@code double[]} hack that required a parallel
     * {@code vertexIndex} list and {@code vertexToIdx} map for
     * int-to-vertex lookups. Eliminates O(V) index bookkeeping and
     * fragile double-to-int casting on every PQ poll.
     *
     * <p>Package-visible so that {@link ShortestPathFinder} can reuse
     * the same entry type instead of maintaining its own duplicate.</p>
     */
    static final class DijkstraEntry implements Comparable<DijkstraEntry> {
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

    /**
     * Reconstructs the shortest path from source to target using the
     * predecessor map from a Dijkstra result.
     *
     * @param dr     Dijkstra result containing predecessor map
     * @param source the source vertex
     * @param target the target vertex
     * @return ordered list of vertices from source to target, or null if
     *         target is unreachable
     */
    public static List<String> reconstructPath(
            DijkstraResult dr, String source, String target) {
        if (!dr.dist.containsKey(target)) return null;
        List<String> path = new ArrayList<String>();
        String cur = target;
        while (cur != null && !cur.equals(source)) {
            path.add(cur);
            cur = dr.prev.get(cur);
        }
        if (cur == null) return null;
        path.add(source);
        Collections.reverse(path);
        return path;
    }

    // ── Bridge detection (Tarjan's algorithm) ────────────────────

    /**
     * Finds all bridge edges in the graph using Tarjan's DFS algorithm.
     * A bridge is an edge whose removal increases the number of connected
     * components.
     *
     * <p>Centralizes the bridge-finding logic previously duplicated in
     * {@link GraphHealthChecker}, {@link EdgeBetweennessAnalyzer}, and
     * {@link GraphSparsificationAnalyzer}.</p>
     *
     * @param graph the JUNG graph
     * @return list of bridge edges (order follows DFS discovery)
     */
    public static List<Edge> findBridges(Graph<String, Edge> graph) {
        List<Edge> bridges = new ArrayList<Edge>();
        if (graph.getVertexCount() == 0) return bridges;
        Map<String, Integer> disc = new HashMap<String, Integer>();
        Map<String, Integer> low = new HashMap<String, Integer>();
        Map<String, String> parent = new HashMap<String, String>();
        int[] timer = {0};
        for (String v : graph.getVertices()) {
            if (!disc.containsKey(v)) {
                bridgeDfs(graph, v, disc, low, parent, timer, bridges);
            }
        }
        return bridges;
    }

    private static void bridgeDfs(Graph<String, Edge> graph, String u,
                                  Map<String, Integer> disc, Map<String, Integer> low,
                                  Map<String, String> parent, int[] timer,
                                  List<Edge> bridges) {
        disc.put(u, timer[0]);
        low.put(u, timer[0]);
        timer[0]++;
        for (String v : graph.getNeighbors(u)) {
            if (!disc.containsKey(v)) {
                parent.put(v, u);
                bridgeDfs(graph, v, disc, low, parent, timer, bridges);
                low.put(u, Math.min(low.get(u), low.get(v)));
                if (low.get(v) > disc.get(u)) {
                    Edge e = graph.findEdge(u, v);
                    if (e == null) e = graph.findEdge(v, u);
                    if (e != null) bridges.add(e);
                }
            } else if (!v.equals(parent.get(u))) {
                low.put(u, Math.min(low.get(u), disc.get(v)));
            }
        }
    }

    // ── Null-safe neighbor access ───────────────────────────────

    /**
     * Returns the neighbors of a vertex, never {@code null}.
     * Wraps {@code graph.getNeighbors(v)} with a null-safe fallback.
     *
     * @param graph the JUNG graph
     * @param v     the vertex
     * @return neighbors of v, or an empty collection if null
     */
    public static Collection<String> neighborsOf(
            Graph<String, Edge> graph, String v) {
        Collection<String> nbrs = graph.getNeighbors(v);
        return nbrs != null ? nbrs : Collections.<String>emptyList();
    }

    // ── Directed adjacency ──────────────────────────────────────

    /**
     * Directed adjacency structure: vertices with successor and predecessor
     * maps.  Extracted from {@link TopologicalSortAnalyzer} for reuse by
     * any analyzer that needs directed-Edge traversal.
     */
    public static final class DirectedAdj {
        /** All vertices in the graph. */
        public final Set<String> vertices;
        /** Vertex → set of outgoing neighbors (vertex1 → vertex2). */
        public final Map<String, Set<String>> successors;
        /** Vertex → set of incoming neighbors. */
        public final Map<String, Set<String>> predecessors;

        public DirectedAdj(Set<String> vertices,
                    Map<String, Set<String>> successors,
                    Map<String, Set<String>> predecessors) {
            this.vertices = vertices;
            this.successors = successors;
            this.predecessors = predecessors;
        }
    }

    /**
     * Builds directed adjacency maps from a graph.  Each Edge is interpreted
     * as vertex1 → vertex2.
     *
     * @param graph the JUNG graph
     * @return a {@link DirectedAdj} with successor and predecessor maps
     */
    public static DirectedAdj buildDirectedAdjacencyMap(
            Graph<String, Edge> graph) {
        Map<String, Set<String>> successors = new HashMap<String, Set<String>>();
        Map<String, Set<String>> predecessors = new HashMap<String, Set<String>>();
        Set<String> allVertices = new HashSet<String>();

        for (String v : graph.getVertices()) {
            allVertices.add(v);
            successors.put(v, new HashSet<String>());
            predecessors.put(v, new HashSet<String>());
        }

        for (Edge e : graph.getEdges()) {
            String from = e.getVertex1();
            String to = e.getVertex2();
            if (from != null && to != null
                    && allVertices.contains(from) && allVertices.contains(to)) {
                successors.get(from).add(to);
                predecessors.get(to).add(from);
            }
        }

        return new DirectedAdj(allVertices, successors, predecessors);
    }
}
