package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Advanced path exploration for graphs — extends basic shortest-path finding
 * with all-paths enumeration, k-shortest paths, constrained routing, and
 * path statistics.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>All simple paths</b> — enumerate every simple path between two
 *       vertices up to a configurable depth limit</li>
 *   <li><b>K shortest paths</b> — find the K shortest paths using Yen's
 *       algorithm (hop-based or weight-based)</li>
 *   <li><b>Constrained paths</b> — find paths that must pass through
 *       required waypoints in order</li>
 *   <li><b>Avoidance routing</b> — find paths that skip forbidden
 *       vertices and/or edges</li>
 *   <li><b>Path statistics</b> — count paths by length, compute path
 *       length distribution</li>
 *   <li><b>Bottleneck detection</b> — find vertices that appear in every
 *       path between a pair</li>
 * </ul>
 *
 * <h3>Complexity</h3>
 * <p>Enumerating all simple paths is exponential in the worst case.
 * The {@code maxDepth} and {@code maxPaths} guards prevent runaway
 * computation on dense graphs.</p>
 *
 * @author zalenix
 */
public class GraphPathExplorer {

    /** Default maximum depth for path enumeration. */
    private static final int DEFAULT_MAX_DEPTH = 20;

    /** Default maximum number of paths to return. */
    private static final int DEFAULT_MAX_PATHS = 1000;

    private final Graph<String, Edge> graph;
    private final Map<String, Set<String>> adjacency;
    private final Map<String, Map<String, Float>> weightMap;

    // ── Constructor ────────────────────────────────────────────────

    /**
     * Creates a new path explorer for the given graph.
     *
     * @param graph the JUNG graph to explore
     * @throws IllegalArgumentException if graph is null or empty
     */
    public GraphPathExplorer(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        if (graph.getVertexCount() == 0) {
            throw new IllegalArgumentException("Graph must not be empty");
        }
        this.graph = graph;
        this.adjacency = GraphUtils.buildAdjacencyMap(graph);
        this.weightMap = buildWeightMap();
    }

    private Map<String, Map<String, Float>> buildWeightMap() {
        Map<String, Map<String, Float>> wm = new HashMap<>();
        for ((Edge e : graph.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            float w = e.getWeight() > 0 ? e.getWeight() : 1.0f;
            wm.computeIfAbsent(v1, k -> new HashMap<>())
              .merge(v2, w, Math::min);
            wm.computeIfAbsent(v2, k -> new HashMap<>())
              .merge(v1, w, Math::min);
        }
        return wm;
    }

    // ── Path result ────────────────────────────────────────────────

    /**
     * Represents a single path through the graph.
     */
    public static class Path implements Comparable<Path> {
        private final List<String> vertices;
        private final double totalWeight;

        public Path(List<String> vertices, double totalWeight) {
            this.vertices = Collections.unmodifiableList(new ArrayList<>(vertices));
            this.totalWeight = totalWeight;
        }

        /** Ordered list of vertices along the path. */
        public List<String> getVertices() { return vertices; }

        /** Number of edges in the path. */
        public int getHopCount() { return Math.max(0, vertices.size() - 1); }

        /** Total edge weight along the path. */
        public double getTotalWeight() { return totalWeight; }

        /** First vertex. */
        public String getSource() { return vertices.get(0); }

        /** Last vertex. */
        public String getTarget() { return vertices.get(vertices.size() - 1); }

        @Override
        public int compareTo(Path other) {
            int cmp = Double.compare(this.totalWeight, other.totalWeight);
            if (cmp != 0) return cmp;
            return Integer.compare(this.vertices.size(), other.vertices.size());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Path)) return false;
            return vertices.equals(((Path) o).vertices);
        }

        @Override
        public int hashCode() { return vertices.hashCode(); }

        @Override
        public String toString() {
            return String.join(" -> ", vertices) +
                   String.format(" (hops=%d, weight=%.2f)", getHopCount(), totalWeight);
        }
    }

    // ── 1. All simple paths ────────────────────────────────────────

    /**
     * Finds all simple paths between source and target up to maxDepth edges.
     *
     * @param source   start vertex
     * @param target   end vertex
     * @param maxDepth maximum number of edges (default: 20)
     * @param maxPaths maximum number of paths to collect (default: 1000)
     * @return list of all simple paths, sorted shortest first
     * @throws IllegalArgumentException if source or target not in graph
     */
    public List<Path> findAllSimplePaths(String source, String target,
                                          int maxDepth, int maxPaths) {
        validateVertex(source);
        validateVertex(target);
        if (maxDepth <= 0) maxDepth = DEFAULT_MAX_DEPTH;
        if (maxPaths <= 0) maxPaths = DEFAULT_MAX_PATHS;

        List<Path> result = new ArrayList<>();
        List<String> currentPath = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        currentPath.add(source);
        visited.add(source);
        dfsAllPaths(source, target, currentPath, visited, result,
                    maxDepth, maxPaths);
        Collections.sort(result);
        return result;
    }

    /** Convenience overload with default limits. */
    public List<Path> findAllSimplePaths(String source, String target) {
        return findAllSimplePaths(source, target, DEFAULT_MAX_DEPTH,
                                  DEFAULT_MAX_PATHS);
    }

    private void dfsAllPaths(String current, String target,
                              List<String> currentPath,
                              Set<String> visited,
                              List<Path> result,
                              int maxDepth, int maxPaths) {
        if (result.size() >= maxPaths) return;
        if (currentPath.size() - 1 > maxDepth) return;

        if (current.equals(target) && currentPath.size() > 1) {
            result.add(new Path(currentPath, computePathWeight(currentPath)));
            return;
        }

        Set<String> neighbors = adjacency.getOrDefault(current,
                                                        Collections.emptySet());
        for (String next : neighbors) {
            if (visited.contains(next)) continue;
            visited.add(next);
            currentPath.add(next);
            dfsAllPaths(next, target, currentPath, visited, result,
                        maxDepth, maxPaths);
            currentPath.remove(currentPath.size() - 1);
            visited.remove(next);
            if (result.size() >= maxPaths) return;
        }
    }

    // ── 2. K shortest paths (Yen's algorithm) ─────────────────────

    /**
     * Finds the K shortest simple paths using Yen's algorithm.
     *
     * <p>Yen's algorithm works by:</p>
     * <ol>
     *   <li>Finding the single shortest path via Dijkstra.</li>
     *   <li>For each vertex in the current shortest path, removing
     *       edges/vertices to discover deviation paths.</li>
     *   <li>Keeping a priority queue of candidate paths.</li>
     * </ol>
     *
     * @param source start vertex
     * @param target end vertex
     * @param k      number of shortest paths to find
     * @return up to k shortest paths, ordered by weight
     * @throws IllegalArgumentException if source/target not in graph, or k &lt; 1
     */
    public List<Path> findKShortestPaths(String source, String target, int k) {
        validateVertex(source);
        validateVertex(target);
        if (k < 1) {
            throw new IllegalArgumentException("k must be >= 1");
        }

        List<Path> result = new ArrayList<>();
        PriorityQueue<Path> candidates = new PriorityQueue<>();

        // First shortest path via Dijkstra
        Path shortest = dijkstra(source, target, Collections.emptySet(),
                                  Collections.emptySet());
        if (shortest == null) return result;
        result.add(shortest);

        for (int i = 1; i < k; i++) {
            Path prevPath = result.get(result.size() - 1);
            List<String> prevVerts = prevPath.getVertices();

            for (int j = 0; j < prevVerts.size() - 1; j++) {
                String spurNode = prevVerts.get(j);
                List<String> rootPath = prevVerts.subList(0, j + 1);

                // Edges to exclude: edges from spur node used by existing
                // result paths with the same root
                Set<String> excludedEdges = new HashSet<>();
                for (Path p : result) {
                    List<String> pv = p.getVertices();
                    if (pv.size() > j && rootPath.equals(pv.subList(0, j + 1))) {
                        excludedEdges.add(edgeKey(pv.get(j), pv.get(j + 1)));
                    }
                }

                // Vertices to exclude: root path vertices except spur node
                Set<String> excludedNodes = new HashSet<>(rootPath);
                excludedNodes.remove(spurNode);

                Path spurPath = dijkstra(spurNode, target, excludedNodes,
                                          excludedEdges);
                if (spurPath != null) {
                    List<String> totalVerts = new ArrayList<>(rootPath);
                    totalVerts.addAll(
                        spurPath.getVertices().subList(1,
                            spurPath.getVertices().size()));
                    Path candidate = new Path(totalVerts,
                                               computePathWeight(totalVerts));
                    if (!result.contains(candidate) &&
                        !candidates.contains(candidate)) {
                        candidates.add(candidate);
                    }
                }
            }

            if (candidates.isEmpty()) break;
            result.add(candidates.poll());
        }

        return result;
    }

    // ── 3. Constrained paths (waypoints) ──────────────────────────

    /**
     * Finds the shortest path that visits all required waypoints in the
     * given order.
     *
     * <p>Computes shortest path between consecutive waypoints and
     * concatenates them. If any segment is unreachable, returns null.</p>
     *
     * @param source    start vertex
     * @param target    end vertex
     * @param waypoints ordered list of vertices that must be visited
     * @return the concatenated path, or null if no valid path exists
     * @throws IllegalArgumentException if any vertex not in graph
     */
    public Path findConstrainedPath(String source, String target,
                                     List<String> waypoints) {
        validateVertex(source);
        validateVertex(target);
        if (waypoints == null) waypoints = Collections.emptyList();
        for (String w : waypoints) {
            validateVertex(w);
        }

        List<String> checkpoints = new ArrayList<>();
        checkpoints.add(source);
        checkpoints.addAll(waypoints);
        checkpoints.add(target);

        List<String> fullPath = new ArrayList<>();
        double totalWeight = 0;

        for (int i = 0; i < checkpoints.size() - 1; i++) {
            Path segment = dijkstra(checkpoints.get(i), checkpoints.get(i + 1),
                                     Collections.emptySet(),
                                     Collections.emptySet());
            if (segment == null) return null;

            if (i == 0) {
                fullPath.addAll(segment.getVertices());
            } else {
                // Skip first vertex (it's the last of previous segment)
                fullPath.addAll(
                    segment.getVertices().subList(1,
                        segment.getVertices().size()));
            }
            totalWeight += segment.getTotalWeight();
        }

        return new Path(fullPath, totalWeight);
    }

    // ── 4. Avoidance routing ──────────────────────────────────────

    /**
     * Finds the shortest path that avoids specified vertices and edges.
     *
     * @param source       start vertex
     * @param target       end vertex
     * @param avoidNodes   vertices to skip (may be null or empty)
     * @param avoidEdgePairs edges to skip as "v1|v2" strings (may be null)
     * @return shortest path avoiding the constraints, or null if none exists
     * @throws IllegalArgumentException if source or target not in graph
     */
    public Path findPathAvoiding(String source, String target,
                                  Set<String> avoidNodes,
                                  Set<String> avoidEdgePairs) {
        validateVertex(source);
        validateVertex(target);
        if (avoidNodes == null) avoidNodes = Collections.emptySet();
        if (avoidEdgePairs == null) avoidEdgePairs = Collections.emptySet();

        // Don't allow avoiding source or target
        if (avoidNodes.contains(source) || avoidNodes.contains(target)) {
            return null;
        }

        return dijkstra(source, target, avoidNodes, avoidEdgePairs);
    }

    /**
     * Convenience: build an edge-pair key for avoidance sets.
     *
     * @param v1 first vertex
     * @param v2 second vertex
     * @return canonical key "v1|v2" (sorted so "A|B" == "B|A")
     */
    public static String edgeKey(String v1, String v2) {
        return v1.compareTo(v2) <= 0 ? v1 + "|" + v2 : v2 + "|" + v1;
    }

    // ── 5. Path statistics ────────────────────────────────────────

    /**
     * Counts simple paths grouped by hop count (length).
     *
     * @param source   start vertex
     * @param target   end vertex
     * @param maxDepth maximum path length to explore
     * @return map from hop count to number of paths of that length
     */
    public Map<Integer, Integer> pathLengthDistribution(String source,
                                                         String target,
                                                         int maxDepth) {
        List<Path> paths = findAllSimplePaths(source, target, maxDepth,
                                              DEFAULT_MAX_PATHS);
        Map<Integer, Integer> dist = new TreeMap<>();
        for (Path p : paths) {
            dist.merge(p.getHopCount(), 1, Integer::sum);
        }
        return dist;
    }

    /**
     * Returns aggregate statistics about all paths between two vertices.
     */
    public PathStats computePathStats(String source, String target,
                                       int maxDepth) {
        List<Path> paths = findAllSimplePaths(source, target, maxDepth,
                                              DEFAULT_MAX_PATHS);
        return new PathStats(source, target, paths);
    }

    /**
     * Aggregated statistics over a set of paths.
     */
    public static class PathStats {
        private final String source;
        private final String target;
        private final int pathCount;
        private final int shortestHops;
        private final int longestHops;
        private final double avgHops;
        private final double minWeight;
        private final double maxWeight;
        private final double avgWeight;
        private final Map<Integer, Integer> lengthDistribution;
        private final Set<String> intermediateVertices;

        public PathStats(String source, String target, List<Path> paths) {
            this.source = source;
            this.target = target;
            this.pathCount = paths.size();
            this.lengthDistribution = new TreeMap<>();
            this.intermediateVertices = new HashSet<>();

            if (paths.isEmpty()) {
                shortestHops = 0;
                longestHops = 0;
                avgHops = 0;
                minWeight = 0;
                maxWeight = 0;
                avgWeight = 0;
                return;
            }

            int minH = Integer.MAX_VALUE, maxH = 0;
            double totalH = 0;
            double minW = Double.MAX_VALUE, maxW = 0, totalW = 0;

            for (Path p : paths) {
                int h = p.getHopCount();
                double w = p.getTotalWeight();
                if (h < minH) minH = h;
                if (h > maxH) maxH = h;
                totalH += h;
                if (w < minW) minW = w;
                if (w > maxW) maxW = w;
                totalW += w;
                lengthDistribution.merge(h, 1, Integer::sum);
                for (int i = 1; i < p.getVertices().size() - 1; i++) {
                    intermediateVertices.add(p.getVertices().get(i));
                }
            }

            shortestHops = minH;
            longestHops = maxH;
            avgHops = totalH / paths.size();
            minWeight = minW;
            maxWeight = maxW;
            avgWeight = totalW / paths.size();
        }

        public String getSource() { return source; }
        public String getTarget() { return target; }
        public int getPathCount() { return pathCount; }
        public int getShortestHops() { return shortestHops; }
        public int getLongestHops() { return longestHops; }
        public double getAvgHops() { return avgHops; }
        public double getMinWeight() { return minWeight; }
        public double getMaxWeight() { return maxWeight; }
        public double getAvgWeight() { return avgWeight; }
        public Map<Integer, Integer> getLengthDistribution() {
            return Collections.unmodifiableMap(lengthDistribution);
        }
        public Set<String> getIntermediateVertices() {
            return Collections.unmodifiableSet(intermediateVertices);
        }

        /**
         * How concentrated are paths through common vertices?
         * Returns a value between 0 and 1.
         * Computed as: intermediate vertices / (pathCount * (avgHops - 1)).
         */
        public double getPathDiversity() {
            if (pathCount <= 1 || avgHops <= 1) return 1.0;
            double expectedSlots = pathCount * (avgHops - 1);
            if (expectedSlots <= 0) return 1.0;
            return Math.min(1.0,
                intermediateVertices.size() / expectedSlots);
        }

        @Override
        public String toString() {
            return String.format(
                "PathStats{%s->%s: %d paths, hops=[%d..%d] avg=%.1f, " +
                "weight=[%.2f..%.2f] avg=%.2f, diversity=%.2f}",
                source, target, pathCount, shortestHops, longestHops,
                avgHops, minWeight, maxWeight, avgWeight, getPathDiversity());
        }
    }

    // ── 6. Vertex importance via path participation ───────────────

    /**
     * Computes how many paths between source and target pass through
     * each intermediate vertex.
     *
     * @param source   start vertex
     * @param target   end vertex
     * @param maxDepth maximum path length
     * @return map from vertex to count of paths passing through it
     */
    public Map<String, Integer> vertexPathParticipation(String source,
                                                         String target,
                                                         int maxDepth) {
        List<Path> paths = findAllSimplePaths(source, target, maxDepth,
                                              DEFAULT_MAX_PATHS);
        Map<String, Integer> participation = new HashMap<>();
        for (Path p : paths) {
            for (int i = 1; i < p.getVertices().size() - 1; i++) {
                participation.merge(p.getVertices().get(i), 1, Integer::sum);
            }
        }
        return participation;
    }

    /**
     * Identifies bottleneck vertices — vertices that appear in every
     * path between source and target (i.e., removing them disconnects
     * source from target).
     *
     * @param source   start vertex
     * @param target   end vertex
     * @param maxDepth maximum path length
     * @return set of bottleneck (cut) vertices between source and target
     */
    public Set<String> findBottleneckVertices(String source, String target,
                                               int maxDepth) {
        List<Path> paths = findAllSimplePaths(source, target, maxDepth,
                                              DEFAULT_MAX_PATHS);
        if (paths.isEmpty()) return Collections.emptySet();

        Map<String, Integer> participation = new HashMap<>();
        for (Path p : paths) {
            for (int i = 1; i < p.getVertices().size() - 1; i++) {
                participation.merge(p.getVertices().get(i), 1, Integer::sum);
            }
        }

        int totalPaths = paths.size();
        Set<String> bottlenecks = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> entry : participation.entrySet()) {
            if (entry.getValue() == totalPaths) {
                bottlenecks.add(entry.getKey());
            }
        }
        return bottlenecks;
    }

    // ── 7. Text report ────────────────────────────────────────────

    /**
     * Generates a comprehensive path exploration report between two vertices.
     *
     * @param source start vertex
     * @param target end vertex
     * @return multi-line text report
     */
    public String generateReport(String source, String target) {
        validateVertex(source);
        validateVertex(target);

        int maxDepth = Math.min(DEFAULT_MAX_DEPTH, graph.getVertexCount());

        PathStats stats = computePathStats(source, target, maxDepth);
        List<Path> kShortest = findKShortestPaths(source, target, 5);
        Set<String> bottlenecks = findBottleneckVertices(source, target,
                                                          maxDepth);

        StringBuilder sb = new StringBuilder();
        sb.append("==============================================\n");
        sb.append("  Graph Path Explorer Report\n");
        sb.append("==============================================\n\n");
        sb.append(String.format("Source: %s\n", source));
        sb.append(String.format("Target: %s\n", target));
        sb.append(String.format("Graph:  %d vertices, %d edges\n\n",
            graph.getVertexCount(), graph.getEdgeCount()));

        if (stats.getPathCount() == 0) {
            sb.append("No paths found between source and target.\n");
            return sb.toString();
        }

        sb.append("-- Summary ---------------------------------\n");
        sb.append(String.format("Total simple paths: %d\n",
            stats.getPathCount()));
        sb.append(String.format("Shortest: %d hops (weight %.2f)\n",
            stats.getShortestHops(), stats.getMinWeight()));
        sb.append(String.format("Longest:  %d hops (weight %.2f)\n",
            stats.getLongestHops(), stats.getMaxWeight()));
        sb.append(String.format("Average:  %.1f hops (weight %.2f)\n",
            stats.getAvgHops(), stats.getAvgWeight()));
        sb.append(String.format("Path diversity: %.2f\n",
            stats.getPathDiversity()));
        sb.append(String.format("Intermediate vertices: %d\n\n",
            stats.getIntermediateVertices().size()));

        sb.append("-- Top 5 Shortest Paths --------------------\n");
        for (int i = 0; i < kShortest.size(); i++) {
            sb.append(String.format("%d. %s\n", i + 1, kShortest.get(i)));
        }
        sb.append("\n");

        if (!bottlenecks.isEmpty()) {
            sb.append("-- Bottleneck Vertices ---------------------\n");
            sb.append("(appear in ALL paths between source and target)\n");
            for (String v : bottlenecks) {
                sb.append(String.format("  * %s\n", v));
            }
            sb.append("\n");
        }

        sb.append("-- Path Length Distribution -----------------\n");
        Map<Integer, Integer> dist = stats.getLengthDistribution();
        int maxCount = dist.values().stream()
            .mapToInt(Integer::intValue).max().orElse(1);
        for (Map.Entry<Integer, Integer> entry : dist.entrySet()) {
            int barLen = (int) Math.ceil(
                (entry.getValue() / (double) maxCount) * 30);
            String bar = repeat('#', barLen);
            sb.append(String.format("  %2d hops: %s %d\n",
                entry.getKey(), bar, entry.getValue()));
        }

        return sb.toString();
    }

    // ── Internal helpers ──────────────────────────────────────────

    /**
     * Dijkstra shortest path with optional node/edge exclusions.
     */
    private Path dijkstra(String source, String target,
                           Set<String> excludeNodes,
                           Set<String> excludeEdges) {
        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        PriorityQueue<String[]> pq = new PriorityQueue<>(
            Comparator.comparingDouble(a -> Double.parseDouble(a[1])));

        dist.put(source, 0.0);
        pq.add(new String[]{source, "0"});

        while (!pq.isEmpty()) {
            String[] entry = pq.poll();
            String u = entry[0];
            double d = Double.parseDouble(entry[1]);

            if (d > dist.getOrDefault(u, Double.MAX_VALUE)) continue;
            if (u.equals(target)) break;

            Set<String> neighbors = adjacency.getOrDefault(u,
                                                            Collections.emptySet());
            for (String v : neighbors) {
                if (excludeNodes.contains(v)) continue;
                String ek = edgeKey(u, v);
                if (excludeEdges.contains(ek)) continue;

                float w = getEdgeWeight(u, v);
                double newDist = d + w;
                if (newDist < dist.getOrDefault(v, Double.MAX_VALUE)) {
                    dist.put(v, newDist);
                    prev.put(v, u);
                    pq.add(new String[]{v, String.valueOf(newDist)});
                }
            }
        }

        if (!prev.containsKey(target) && !source.equals(target)) {
            return null;
        }

        // Reconstruct path
        List<String> path = new ArrayList<>();
        String current = target;
        while (current != null) {
            path.add(current);
            current = prev.get(current);
        }
        Collections.reverse(path);

        if (!path.get(0).equals(source)) return null;

        return new Path(path, computePathWeight(path));
    }

    private double computePathWeight(List<String> path) {
        double total = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            total += getEdgeWeight(path.get(i), path.get(i + 1));
        }
        return total;
    }

    private float getEdgeWeight(String u, String v) {
        Map<String, Float> uWeights = weightMap.get(u);
        if (uWeights != null) {
            Float w = uWeights.get(v);
            if (w != null) return w;
        }
        return 1.0f;
    }

    private void validateVertex(String vertex) {
        if (vertex == null || !graph.containsVertex(vertex)) {
            throw new IllegalArgumentException(
                "Vertex not in graph: " + vertex);
        }
    }

    private static String repeat(char c, int count) {
        char[] arr = new char[count];
        java.util.Arrays.fill(arr, c);
        return new String(arr);
    }
}
