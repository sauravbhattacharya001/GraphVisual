package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generator for random graphs using classic models from network science.
 *
 * <p>Provides static factory methods for constructing random graphs that are
 * useful for testing algorithms, benchmarking layouts, and exploring graph
 * properties. Every method returns a JUNG {@code Graph<String, Edge>} ready
 * for use in the visualizer.</p>
 *
 * <h3>Available models:</h3>
 * <ul>
 *   <li><b>Erdős–Rényi G(n,p)</b> — each edge exists independently with probability p</li>
 *   <li><b>Barabási–Albert</b> — preferential attachment scale-free network</li>
 *   <li><b>Watts–Strogatz</b> — small-world network with tunable rewiring</li>
 *   <li><b>Random Regular</b> — every vertex has exactly the same degree</li>
 *   <li><b>Grid / Lattice</b> — 2D rectangular grid graph</li>
 *   <li><b>Random Tree</b> — uniformly random labeled tree (Prüfer sequence)</li>
 *   <li><b>Complete Graph K_n</b> — all vertices connected to all others</li>
 *   <li><b>Star Graph</b> — one hub connected to all leaves</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <pre>
 *   Graph&lt;String, Edge&gt; g = RandomGraphGenerator.erdosRenyi(50, 0.1);
 *   Graph&lt;String, Edge&gt; ba = RandomGraphGenerator.barabasiAlbert(100, 3);
 * </pre>
 *
 * @author GraphVisual Feature Builder
 * @see FamousGraphLibrary
 */
public final class RandomGraphGenerator {

    private RandomGraphGenerator() { /* utility class */ }

    /**
     * Thread-safe monotonic counter for unique Edge identifiers.
     * Unlike the previous non-atomic {@code int edgeId} with manual
     * {@code resetEdgeId()} calls, this counter is never reset —
     * uniqueness only requires that no two edges share an ID, not
     * that IDs start from zero. This eliminates the race condition
     * where concurrent generator calls would reset each other's
     * counters mid-generation.
     */
    private static final AtomicInteger EDGE_ID = new AtomicInteger();

    /**
     * Creates a new Edge with a unique generated type code and placeholder
     * vertices. The actual endpoints are set by JUNG's
     * {@code Graph.addEdge(edge, v1, v2)} — the Edge constructor values
     * here serve only as identifiers for equals/hashCode.
     *
     * <p>Thread-safe: uses an {@link AtomicInteger} for ID generation.</p>
     */
    private static Edge newEdge() {
        int id = EDGE_ID.getAndIncrement();
        return new Edge("gen", "_" + id + "a", "_" + id + "b");
    }

    /**
     * Returns a catalog of available random graph models with descriptions.
     */
    public static Map<String, String> catalog() {
        Map<String, String> cat = new LinkedHashMap<>();
        cat.put("erdos-renyi", "Erdős–Rényi G(n,p) — random edges with probability p");
        cat.put("barabasi-albert", "Barabási–Albert — preferential attachment (scale-free)");
        cat.put("watts-strogatz", "Watts–Strogatz — small-world with rewiring probability β");
        cat.put("random-regular", "Random Regular — every vertex has degree k");
        cat.put("grid", "Grid / Lattice — rows × cols rectangular grid");
        cat.put("random-tree", "Random Tree — uniformly random labeled tree");
        cat.put("complete", "Complete K_n — fully connected graph");
        cat.put("star", "Star — one hub, n-1 leaves");
        return cat;
    }

    /**
     * Erdős–Rényi G(n,p) model: n vertices, each possible edge included
     * independently with probability p.
     *
     * @param n number of vertices (≥ 1)
     * @param p edge probability (0.0 to 1.0)
     * @return the generated graph
     */
    public static Graph<String, Edge> erdosRenyi(int n, double p) {
        return erdosRenyi(n, p, new Random());
    }

    public static Graph<String, Edge> erdosRenyi(int n, double p, Random rng) {

        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        List<String> verts = addVertices(g, n, "v");
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (rng.nextDouble() < p) {
                    g.addEdge(newEdge(), verts.get(i), verts.get(j));
                }
            }
        }
        return g;
    }

    /**
     * Barabási–Albert preferential attachment model: starts with m+1 connected
     * vertices and adds vertices one at a time, each connecting to m existing
     * vertices with probability proportional to their degree.
     *
     * @param n total vertices (≥ m+1)
     * @param m edges per new vertex (≥ 1)
     * @return scale-free graph
     */
    public static Graph<String, Edge> barabasiAlbert(int n, int m) {
        return barabasiAlbert(n, m, new Random());
    }

    public static Graph<String, Edge> barabasiAlbert(int n, int m, Random rng) {

        if (m < 1) m = 1;
        if (n < m + 1) n = m + 1;
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        List<String> verts = new ArrayList<>();

        // Start with a complete graph on m+1 vertices
        for (int i = 0; i <= m; i++) {
            String v = "v" + i;
            g.addVertex(v);
            verts.add(v);
        }
        for (int i = 0; i <= m; i++) {
            for (int j = i + 1; j <= m; j++) {
                g.addEdge(newEdge(), verts.get(i), verts.get(j));
            }
        }

        // Degree-based repeated list for preferential attachment
        List<String> degreeList = new ArrayList<>();
        for (String v : verts) {
            int deg = g.degree(v);
            for (int d = 0; d < deg; d++) degreeList.add(v);
        }

        for (int i = m + 1; i < n; i++) {
            String newV = "v" + i;
            g.addVertex(newV);
            verts.add(newV);

            Set<String> targets = new HashSet<>();
            while (targets.size() < m && targets.size() < degreeList.size()) {
                String target = degreeList.get(rng.nextInt(degreeList.size()));
                targets.add(target);
            }
            for (String t : targets) {
                g.addEdge(newEdge(), newV, t);
                degreeList.add(newV);
                degreeList.add(t);
            }
        }
        return g;
    }

    /**
     * Watts–Strogatz small-world model: start with a ring lattice where each
     * vertex connects to k nearest neighbors, then rewire each edge with
     * probability beta.
     *
     * @param n number of vertices (≥ 3)
     * @param k number of nearest neighbors (even, ≥ 2)
     * @param beta rewiring probability (0.0 to 1.0)
     * @return small-world graph
     */
    public static Graph<String, Edge> wattsStrogatz(int n, int k, double beta) {
        return wattsStrogatz(n, k, beta, new Random());
    }

    public static Graph<String, Edge> wattsStrogatz(int n, int k, double beta, Random rng) {

        if (n < 3) n = 3;
        if (k < 2) k = 2;
        if (k % 2 != 0) k++;
        if (k >= n) k = n - 1 - ((n - 1) % 2 == 0 ? 0 : 1);

        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        List<String> verts = addVertices(g, n, "v");

        // Build ring lattice
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (String v : verts) adjacency.put(v, new HashSet<>());

        for (int i = 0; i < n; i++) {
            for (int j = 1; j <= k / 2; j++) {
                int ni = (i + j) % n;
                String a = verts.get(i), b = verts.get(ni);
                if (!adjacency.get(a).contains(b)) {
                    g.addEdge(newEdge(), a, b);
                    adjacency.get(a).add(b);
                    adjacency.get(b).add(a);
                }
            }
        }

        // Rewire
        for (int i = 0; i < n; i++) {
            for (int j = 1; j <= k / 2; j++) {
                if (rng.nextDouble() < beta) {
                    int ni = (i + j) % n;
                    String a = verts.get(i), b = verts.get(ni);
                    Edge existing = g.findEdge(a, b);
                    if (existing == null) continue;

                    // Pick a random non-neighbor
                    String newTarget = null;
                    for (int attempt = 0; attempt < n * 2; attempt++) {
                        String candidate = verts.get(rng.nextInt(n));
                        if (!candidate.equals(a) && !adjacency.get(a).contains(candidate)) {
                            newTarget = candidate;
                            break;
                        }
                    }
                    if (newTarget != null) {
                        g.removeEdge(existing);
                        adjacency.get(a).remove(b);
                        adjacency.get(b).remove(a);
                        g.addEdge(newEdge(), a, newTarget);
                        adjacency.get(a).add(newTarget);
                        adjacency.get(newTarget).add(a);
                    }
                }
            }
        }
        return g;
    }

    /**
     * Random regular graph: every vertex has exactly degree k.
     * Uses a pairing-model approach with retry on failure.
     *
     * @param n number of vertices (n*k must be even)
     * @param k degree of each vertex (≥ 1)
     * @return k-regular graph
     */
    public static Graph<String, Edge> randomRegular(int n, int k) {
        return randomRegular(n, k, new Random());
    }

    public static Graph<String, Edge> randomRegular(int n, int k, Random rng) {
        if (n * k % 2 != 0) {
            // Adjust: make n even if k is odd
            if (k % 2 != 0 && n % 2 != 0) n++;
        }
        if (k >= n) k = n - 1;

        for (int attempt = 0; attempt < 100; attempt++) {
            Graph<String, Edge> result = tryRandomRegular(n, k, rng);
            if (result != null) return result;
        }
        // Fallback: return whatever we can
        return tryRandomRegular(n, Math.min(k, 2), rng);
    }

    private static Graph<String, Edge> tryRandomRegular(int n, int k, Random rng) {

        // Create k copies of each vertex index
        List<Integer> stubs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int d = 0; d < k; d++) stubs.add(i);
        }
        Collections.shuffle(stubs, rng);

        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        List<String> verts = addVertices(g, n, "v");
        Set<String> edgeSet = new HashSet<>();

        for (int i = 0; i < stubs.size() - 1; i += 2) {
            int a = stubs.get(i), b = stubs.get(i + 1);
            if (a == b) return null; // self-loop, retry
            String key = Math.min(a, b) + "-" + Math.max(a, b);
            if (edgeSet.contains(key)) return null; // multi-edge, retry
            edgeSet.add(key);
            g.addEdge(newEdge(), verts.get(a), verts.get(b));
        }
        return g;
    }

    /**
     * 2D rectangular grid graph.
     *
     * @param rows number of rows (≥ 1)
     * @param cols number of columns (≥ 1)
     * @return grid graph with rows × cols vertices
     */
    public static Graph<String, Edge> grid(int rows, int cols) {

        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        String[][] grid = new String[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                grid[r][c] = "v" + (r * cols + c);
                g.addVertex(grid[r][c]);
            }
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (c + 1 < cols) g.addEdge(newEdge(), grid[r][c], grid[r][c + 1]);
                if (r + 1 < rows) g.addEdge(newEdge(), grid[r][c], grid[r + 1][c]);
            }
        }
        return g;
    }

    /**
     * Random labeled tree using Prüfer sequence.
     *
     * @param n number of vertices (≥ 2)
     * @return a random tree
     */
    public static Graph<String, Edge> randomTree(int n) {
        return randomTree(n, new Random());
    }

    public static Graph<String, Edge> randomTree(int n, Random rng) {

        if (n < 2) n = 2;
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        List<String> verts = addVertices(g, n, "v");

        // Generate Prüfer sequence
        int[] prufer = new int[n - 2];
        for (int i = 0; i < prufer.length; i++) {
            prufer[i] = rng.nextInt(n);
        }

        // Decode Prüfer sequence to tree edges
        int[] degree = new int[n];
        Arrays.fill(degree, 1);
        for (int p : prufer) degree[p]++;

        for (int p : prufer) {
            for (int j = 0; j < n; j++) {
                if (degree[j] == 1) {
                    g.addEdge(newEdge(), verts.get(p), verts.get(j));
                    degree[p]--;
                    degree[j]--;
                    break;
                }
            }
        }

        // Connect the last two vertices with degree 1
        List<Integer> remaining = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (degree[i] == 1) remaining.add(i);
        }
        if (remaining.size() == 2) {
            g.addEdge(newEdge(), verts.get(remaining.get(0)), verts.get(remaining.get(1)));
        }
        return g;
    }

    /**
     * Complete graph K_n.
     *
     * @param n number of vertices
     * @return complete graph
     */
    public static Graph<String, Edge> complete(int n) {

        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        List<String> verts = addVertices(g, n, "v");
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                g.addEdge(newEdge(), verts.get(i), verts.get(j));
            }
        }
        return g;
    }

    /**
     * Star graph: one center connected to n-1 leaves.
     *
     * @param n total vertices (≥ 2)
     * @return star graph
     */
    public static Graph<String, Edge> star(int n) {

        if (n < 2) n = 2;
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        String center = "hub";
        g.addVertex(center);
        for (int i = 1; i < n; i++) {
            String leaf = "v" + i;
            g.addVertex(leaf);
            g.addEdge(newEdge(), center, leaf);
        }
        return g;
    }

    /**
     * Look up a generator by name from the catalog.
     *
     * @param name model name (e.g. "erdos-renyi", "grid")
     * @param params model parameters as a map
     * @return generated graph, or null if name is unknown
     */
    public static Graph<String, Edge> byName(String name, Map<String, Number> params) {
        int n = params.getOrDefault("n", 20).intValue();
        switch (name) {
            case "erdos-renyi":
                double p = params.getOrDefault("p", 0.15).doubleValue();
                return erdosRenyi(n, p);
            case "barabasi-albert":
                int m = params.getOrDefault("m", 2).intValue();
                return barabasiAlbert(n, m);
            case "watts-strogatz":
                int k = params.getOrDefault("k", 4).intValue();
                double beta = params.getOrDefault("beta", 0.3).doubleValue();
                return wattsStrogatz(n, k, beta);
            case "random-regular":
                int deg = params.getOrDefault("k", 3).intValue();
                return randomRegular(n, deg);
            case "grid":
                int rows = params.getOrDefault("rows", 5).intValue();
                int cols = params.getOrDefault("cols", 5).intValue();
                return grid(rows, cols);
            case "random-tree":
                return randomTree(n);
            case "complete":
                return complete(n);
            case "star":
                return star(n);
            default:
                return null;
        }
    }

    // Helper to add n vertices with a given prefix
    private static List<String> addVertices(Graph<String, Edge> g, int n, String prefix) {
        List<String> verts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String v = prefix + i;
            g.addVertex(v);
            verts.add(v);
        }
        return verts;
    }
}
