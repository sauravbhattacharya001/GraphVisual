package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Computes classical distance-based graph invariants on the largest connected
 * component of a graph.
 *
 * <p>Computed metrics:</p>
 * <ul>
 *   <li><b>Wiener Index (W)</b> — sum of all pairwise shortest-path distances.
 *       A fundamental topological index used in chemical graph theory, QSAR
 *       modelling, and network analysis.</li>
 *   <li><b>Average Path Length</b> — W divided by the number of vertex pairs.</li>
 *   <li><b>Hyper-Wiener Index (WW)</b> — ½ Σ [d(u,v) + d(u,v)²] over all
 *       unordered pairs {u,v}. Captures second-order distance information.</li>
 *   <li><b>Harary Index (H)</b> — Σ 1/d(u,v) over all unordered pairs with
 *       d(u,v) &gt; 0. Measures graph compactness.</li>
 * </ul>
 *
 * <p>All BFS passes use array-based traversal for performance, matching the
 * pattern in {@link GraphDiameterAnalyzer} and {@link NodeCentralityAnalyzer}.</p>
 *
 * @author zalenix
 */
public class WienerIndexCalculator {

    private final Graph<String, Edge> graph;
    private long wienerIndex;
    private double averagePathLength;
    private long hyperWienerIndex;
    private double hararyIndex;
    private int componentSize;
    private long pairCount;
    private boolean computed;

    /**
     * Creates a calculator for the given graph.
     *
     * @param graph the JUNG graph to analyze
     * @throws IllegalArgumentException if graph is null
     */
    public WienerIndexCalculator(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.computed = false;
    }

    /**
     * Runs the computation. Must be called before querying results.
     */
    public void compute() {
        wienerIndex = 0;
        hyperWienerIndex = 0;
        hararyIndex = 0.0;
        averagePathLength = 0.0;

        if (graph.getVertexCount() == 0) {
            componentSize = 0;
            pairCount = 0;
            computed = true;
            return;
        }

        Set<String> component = GraphUtils.findLargestComponent(graph);
        componentSize = component.size();

        if (componentSize <= 1) {
            pairCount = 0;
            computed = true;
            return;
        }

        pairCount = (long) componentSize * (componentSize - 1) / 2;

        // Build index and adjacency arrays
        List<String> vertices = new ArrayList<String>(component);
        int n = vertices.size();
        Map<String, Integer> idxMap = new HashMap<String, Integer>(n * 2);
        for (int i = 0; i < n; i++) idxMap.put(vertices.get(i), i);

        int[][] adj = buildAdjacency(n, idxMap);

        // Reusable BFS structures
        int[] dist = new int[n];
        int[] queue = new int[n];

        // BFS from each vertex; only count pairs (s, t) with s < t
        // to avoid double-counting
        for (int s = 0; s < n; s++) {
            Arrays.fill(dist, -1);
            dist[s] = 0;
            int qStart = 0, qEnd = 0;
            queue[qEnd++] = s;

            while (qStart < qEnd) {
                int v = queue[qStart++];
                int d = dist[v];
                for (int w : adj[v]) {
                    if (dist[w] < 0) {
                        dist[w] = d + 1;
                        queue[qEnd++] = w;
                    }
                }
            }

            // Accumulate only for t > s
            for (int t = s + 1; t < n; t++) {
                int d = dist[t];
                if (d > 0) {
                    wienerIndex += d;
                    hyperWienerIndex += d + (long) d * d;
                    hararyIndex += 1.0 / d;
                }
            }
        }

        // Hyper-Wiener is ½ Σ [d + d²]
        // We accumulated the full sum above; divide by 2
        hyperWienerIndex /= 2;

        averagePathLength = (double) wienerIndex / pairCount;
        computed = true;
    }

    /** Returns the Wiener index W = Σ d(u,v) over all unordered pairs. */
    public long getWienerIndex() {
        ensureComputed();
        return wienerIndex;
    }

    /** Returns the average shortest-path length (W / number of pairs). */
    public double getAveragePathLength() {
        ensureComputed();
        return averagePathLength;
    }

    /** Returns the hyper-Wiener index WW = ½ Σ [d(u,v) + d(u,v)²]. */
    public long getHyperWienerIndex() {
        ensureComputed();
        return hyperWienerIndex;
    }

    /** Returns the Harary index H = Σ 1/d(u,v) over all unordered pairs. */
    public double getHararyIndex() {
        ensureComputed();
        return hararyIndex;
    }

    /** Returns the size of the largest connected component used. */
    public int getComponentSize() {
        ensureComputed();
        return componentSize;
    }

    /** Returns the number of vertex pairs in the component. */
    public long getPairCount() {
        ensureComputed();
        return pairCount;
    }

    /**
     * Returns a human-readable summary.
     */
    public String getSummary() {
        ensureComputed();
        StringBuilder sb = new StringBuilder();
        sb.append("=== Distance-Based Graph Invariants ===\n");
        sb.append(String.format("Component size: %d vertices (%d pairs)\n",
                componentSize, pairCount));
        sb.append(String.format("Wiener Index (W):       %d\n", wienerIndex));
        sb.append(String.format("Average Path Length:    %.4f\n", averagePathLength));
        sb.append(String.format("Hyper-Wiener Index (WW): %d\n", hyperWienerIndex));
        sb.append(String.format("Harary Index (H):       %.4f\n", hararyIndex));
        return sb.toString();
    }

    // --- Private helpers ---

    private int[][] buildAdjacency(int n, Map<String, Integer> idxMap) {
        @SuppressWarnings("unchecked")
        List<Integer>[] tmp = new List[n];
        for (int i = 0; i < n; i++) tmp[i] = new ArrayList<Integer>();
        for (Edge e : graph.getEdges()) {
            Integer ui = idxMap.get(e.getVertex1());
            Integer vi = idxMap.get(e.getVertex2());
            if (ui != null && vi != null && !ui.equals(vi)) {
                tmp[ui].add(vi);
                tmp[vi].add(ui);
            }
        }
        int[][] adj = new int[n][];
        for (int i = 0; i < n; i++) {
            List<Integer> nb = tmp[i];
            adj[i] = new int[nb.size()];
            for (int j = 0; j < nb.size(); j++) adj[i][j] = nb.get(j);
        }
        return adj;
    }

    private void ensureComputed() {
        if (!computed) {
            throw new IllegalStateException("Call compute() before querying results");
        }
    }
}
