package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyses the all-pairs shortest-path distance distribution of a graph,
 * providing global structural metrics that complement per-vertex analysis.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Distance matrix</b> — all-pairs BFS shortest-path distances</li>
 *   <li><b>Distance distribution</b> — histogram of pairwise distances</li>
 *   <li><b>Average path length</b> — mean of all finite pairwise distances</li>
 *   <li><b>Wiener index</b> — sum of all pairwise distances (graph compactness)</li>
 *   <li><b>Harmonic mean distance</b> — reciprocal average, handles disconnected graphs</li>
 *   <li><b>Distance percentiles</b> — median, 90th, 95th, 99th percentile distances</li>
 *   <li><b>Characteristic path length</b> — per-vertex average distance, then global average</li>
 *   <li><b>Vertex remoteness</b> — average distance from a vertex to all others (closeness complement)</li>
 *   <li><b>Distance-based separation</b> — fraction of unreachable pairs (disconnection measure)</li>
 *   <li><b>Text report</b> — human-readable distance distribution summary</li>
 * </ul>
 *
 * <p>All computations use unweighted BFS. For directed graphs the out-edge
 * direction is followed. Distance to self is 0, unreachable pairs use -1.</p>
 *
 * @author zalenix
 */
public class GraphDistanceDistribution {

    private final Graph<String, edge> graph;
    private Map<String, Map<String, Integer>> distanceMatrix;
    private boolean computed;

    /**
     * Creates a new GraphDistanceDistribution analyser for the given graph.
     *
     * @param graph the JUNG graph to analyse
     * @throws IllegalArgumentException if graph is null
     */
    public GraphDistanceDistribution(Graph<String, edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.computed = false;
    }

    /**
     * Computes all-pairs BFS shortest-path distances. Must be called before
     * querying results.
     */
    public void compute() {
        distanceMatrix = new LinkedHashMap<>();
        for (String v : graph.getVertices()) {
            distanceMatrix.put(v, bfs(v));
        }
        computed = true;
    }

    private Map<String, Integer> bfs(String source) {
        Map<String, Integer> dist = new LinkedHashMap<>();
        dist.put(source, 0);
        Queue<String> queue = new LinkedList<>();
        queue.add(source);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            int d = dist.get(cur);
            for (String neighbor : graph.getSuccessors(cur)) {
                if (!dist.containsKey(neighbor)) {
                    dist.put(neighbor, d + 1);
                    queue.add(neighbor);
                }
            }
        }
        return dist;
    }

    private void ensureComputed() {
        if (!computed) {
            throw new IllegalStateException("Call compute() first");
        }
    }

    /**
     * Returns the full distance matrix. Unreachable pairs have no entry in
     * the inner map.
     *
     * @return map from source → (target → distance)
     */
    public Map<String, Map<String, Integer>> getDistanceMatrix() {
        ensureComputed();
        return Collections.unmodifiableMap(distanceMatrix);
    }

    /**
     * Returns the shortest-path distance between two vertices, or -1 if
     * unreachable.
     *
     * @param from source vertex
     * @param to   target vertex
     * @return distance, or -1 if unreachable
     */
    public int getDistance(String from, String to) {
        ensureComputed();
        Map<String, Integer> row = distanceMatrix.get(from);
        if (row == null) return -1;
        return row.getOrDefault(to, -1);
    }

    /**
     * Returns the distance distribution as a histogram: distance → count of
     * ordered pairs at that distance (excluding self-loops, d=0).
     *
     * @return sorted map of distance → pair count
     */
    public Map<Integer, Integer> getDistanceHistogram() {
        ensureComputed();
        Map<Integer, Integer> hist = new TreeMap<>();
        List<String> vertices = new ArrayList<>(graph.getVertices());
        for (int i = 0; i < vertices.size(); i++) {
            Map<String, Integer> row = distanceMatrix.get(vertices.get(i));
            for (int j = i + 1; j < vertices.size(); j++) {
                Integer d = row.get(vertices.get(j));
                if (d != null && d > 0) {
                    hist.merge(d, 1, Integer::sum);
                }
            }
        }
        return hist;
    }

    /**
     * Returns the average path length (mean of all finite pairwise distances,
     * excluding self-pairs).
     *
     * @return average path length, or 0 if no finite pairs exist
     */
    public double getAveragePathLength() {
        ensureComputed();
        long sum = 0;
        long count = 0;
        List<String> vertices = new ArrayList<>(graph.getVertices());
        for (int i = 0; i < vertices.size(); i++) {
            Map<String, Integer> row = distanceMatrix.get(vertices.get(i));
            for (int j = i + 1; j < vertices.size(); j++) {
                Integer d = row.get(vertices.get(j));
                if (d != null && d > 0) {
                    sum += d;
                    count++;
                }
            }
        }
        return count == 0 ? 0.0 : (double) sum / count;
    }

    /**
     * Returns the Wiener index — the sum of all pairwise shortest-path
     * distances. Only counts unordered pairs (i,j) with i &lt; j.
     *
     * @return Wiener index
     */
    public long getWienerIndex() {
        ensureComputed();
        long sum = 0;
        List<String> vertices = new ArrayList<>(graph.getVertices());
        for (int i = 0; i < vertices.size(); i++) {
            Map<String, Integer> row = distanceMatrix.get(vertices.get(i));
            for (int j = i + 1; j < vertices.size(); j++) {
                Integer d = row.get(vertices.get(j));
                if (d != null && d > 0) {
                    sum += d;
                }
            }
        }
        return sum;
    }

    /**
     * Returns the harmonic mean distance — handles disconnected graphs by
     * using the sum of reciprocals of distances.
     * Defined as n*(n-1) / (2 * sum(1/d(i,j))) for all reachable pairs.
     *
     * @return harmonic mean distance, or Double.POSITIVE_INFINITY if no
     *         finite pairs exist
     */
    public double getHarmonicMeanDistance() {
        ensureComputed();
        double reciprocalSum = 0;
        List<String> vertices = new ArrayList<>(graph.getVertices());
        for (int i = 0; i < vertices.size(); i++) {
            Map<String, Integer> row = distanceMatrix.get(vertices.get(i));
            for (int j = i + 1; j < vertices.size(); j++) {
                Integer d = row.get(vertices.get(j));
                if (d != null && d > 0) {
                    reciprocalSum += 1.0 / d;
                }
            }
        }
        if (reciprocalSum == 0) return Double.POSITIVE_INFINITY;
        int n = vertices.size();
        double totalPairs = (double) n * (n - 1) / 2.0;
        return totalPairs / reciprocalSum;
    }

    /**
     * Returns distance percentiles. Computes the p-th percentile of all
     * finite pairwise distances.
     *
     * @param percentile the percentile (0–100)
     * @return the distance at the given percentile, or -1 if no data
     */
    public int getDistancePercentile(double percentile) {
        ensureComputed();
        if (percentile < 0 || percentile > 100) {
            throw new IllegalArgumentException("Percentile must be 0-100");
        }
        List<Integer> distances = collectFiniteDistances();
        if (distances.isEmpty()) return -1;
        Collections.sort(distances);
        int index = (int) Math.ceil(percentile / 100.0 * distances.size()) - 1;
        if (index < 0) index = 0;
        return distances.get(index);
    }

    /**
     * Returns the median distance.
     *
     * @return median pairwise distance, or -1 if no finite pairs
     */
    public int getMedianDistance() {
        return getDistancePercentile(50);
    }

    /**
     * Returns the vertex remoteness — average distance from a vertex to all
     * other reachable vertices.
     *
     * @param vertex the vertex
     * @return average distance, or -1 if vertex has no reachable neighbours
     */
    public double getVertexRemoteness(String vertex) {
        ensureComputed();
        Map<String, Integer> row = distanceMatrix.get(vertex);
        if (row == null) return -1;
        long sum = 0;
        int count = 0;
        for (Map.Entry<String, Integer> e : row.entrySet()) {
            if (!e.getKey().equals(vertex) && e.getValue() > 0) {
                sum += e.getValue();
                count++;
            }
        }
        return count == 0 ? -1.0 : (double) sum / count;
    }

    /**
     * Returns remoteness for all vertices, sorted by remoteness ascending
     * (most central first).
     *
     * @return list of vertex-remoteness pairs
     */
    public List<Map.Entry<String, Double>> getAllRemotenessRanked() {
        ensureComputed();
        List<Map.Entry<String, Double>> result = new ArrayList<>();
        for (String v : graph.getVertices()) {
            double r = getVertexRemoteness(v);
            if (r >= 0) {
                result.add(new AbstractMap.SimpleEntry<>(v, r));
            }
        }
        result.sort(Comparator.comparingDouble(Map.Entry::getValue));
        return result;
    }

    /**
     * Returns the fraction of unreachable vertex pairs (disconnection measure).
     * 0.0 means fully connected, 1.0 means fully disconnected.
     *
     * @return separation ratio in [0, 1]
     */
    public double getSeparationRatio() {
        ensureComputed();
        List<String> vertices = new ArrayList<>(graph.getVertices());
        int n = vertices.size();
        if (n <= 1) return 0.0;
        long totalPairs = (long) n * (n - 1) / 2;
        long reachable = 0;
        for (int i = 0; i < n; i++) {
            Map<String, Integer> row = distanceMatrix.get(vertices.get(i));
            for (int j = i + 1; j < n; j++) {
                if (row.containsKey(vertices.get(j))) {
                    reachable++;
                }
            }
        }
        return 1.0 - (double) reachable / totalPairs;
    }

    /**
     * Returns the number of distinct distances that occur in the graph.
     *
     * @return count of unique distance values
     */
    public int getDistinctDistanceCount() {
        return getDistanceHistogram().size();
    }

    /**
     * Generates a human-readable text report of the distance distribution.
     *
     * @return multi-line report string
     */
    public String generateReport() {
        ensureComputed();
        StringBuilder sb = new StringBuilder();
        int n = graph.getVertexCount();
        int e = graph.getEdgeCount();

        sb.append("=== Distance Distribution Report ===\n\n");
        sb.append(String.format("Vertices: %d | Edges: %d\n", n, e));
        sb.append(String.format("Average path length: %.4f\n", getAveragePathLength()));
        sb.append(String.format("Wiener index: %d\n", getWienerIndex()));
        sb.append(String.format("Harmonic mean distance: %.4f\n", getHarmonicMeanDistance()));
        sb.append(String.format("Median distance: %d\n", getMedianDistance()));
        sb.append(String.format("90th percentile: %d\n", getDistancePercentile(90)));
        sb.append(String.format("95th percentile: %d\n", getDistancePercentile(95)));
        sb.append(String.format("Separation ratio: %.4f\n", getSeparationRatio()));
        sb.append(String.format("Distinct distances: %d\n\n", getDistinctDistanceCount()));

        Map<Integer, Integer> hist = getDistanceHistogram();
        if (!hist.isEmpty()) {
            sb.append("Distance Histogram:\n");
            int maxCount = hist.values().stream().max(Integer::compareTo).orElse(1);
            for (Map.Entry<Integer, Integer> entry : hist.entrySet()) {
                int barLen = (int) Math.ceil(40.0 * entry.getValue() / maxCount);
                String bar = String.join("", Collections.nCopies(barLen, "█"));
                sb.append(String.format("  d=%d: %s %d\n", entry.getKey(), bar, entry.getValue()));
            }
        }

        sb.append("\nTop 5 most central (lowest remoteness):\n");
        List<Map.Entry<String, Double>> ranked = getAllRemotenessRanked();
        int limit = Math.min(5, ranked.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Double> re = ranked.get(i);
            sb.append(String.format("  %d. %s (remoteness=%.4f)\n", i + 1, re.getKey(), re.getValue()));
        }

        return sb.toString();
    }

    /**
     * Exports the distance matrix as a CSV string.
     *
     * @return CSV representation of the distance matrix
     */
    public String exportCsv() {
        ensureComputed();
        List<String> vertices = new ArrayList<>(graph.getVertices());
        Collections.sort(vertices);
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append(",");
        sb.append(String.join(",", vertices));
        sb.append("\n");

        // Rows
        for (String from : vertices) {
            sb.append(from);
            Map<String, Integer> row = distanceMatrix.get(from);
            for (String to : vertices) {
                sb.append(",");
                Integer d = row.get(to);
                sb.append(d != null ? d : "-1");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Returns a JSON-like string of the distance histogram.
     *
     * @return JSON object string
     */
    public String exportHistogramJson() {
        ensureComputed();
        Map<Integer, Integer> hist = getDistanceHistogram();
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<Integer, Integer> entry : hist.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private List<Integer> collectFiniteDistances() {
        List<Integer> distances = new ArrayList<>();
        List<String> vertices = new ArrayList<>(graph.getVertices());
        for (int i = 0; i < vertices.size(); i++) {
            Map<String, Integer> row = distanceMatrix.get(vertices.get(i));
            for (int j = i + 1; j < vertices.size(); j++) {
                Integer d = row.get(vertices.get(j));
                if (d != null && d > 0) {
                    distances.add(d);
                }
            }
        }
        return distances;
    }
}
