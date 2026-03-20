package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.*;

/**
 * Generates synthetic graphs with various well-known topologies.
 *
 * <p>Useful for testing analysis algorithms, benchmarking performance,
 * exploring graph properties, and creating example networks for
 * demonstrations. All generated graphs use the same JUNG graph type
 * ({@code UndirectedSparseGraph<String, Edge>}) as the rest of the
 * application.</p>
 *
 * <h3>Supported topologies:</h3>
 * <ul>
 *   <li><b>Complete</b> — every node connected to every other node</li>
 *   <li><b>Ring/Cycle</b> — nodes in a circular chain</li>
 *   <li><b>Star</b> — one central hub connected to all others</li>
 *   <li><b>Grid</b> — 2D lattice (rows × columns)</li>
 *   <li><b>Random (Erdős–Rényi)</b> — edges with independent probability</li>
 *   <li><b>Scale-Free (Barabási–Albert)</b> — preferential attachment</li>
 *   <li><b>Small-World (Watts–Strogatz)</b> — regular lattice with random rewiring</li>
 *   <li><b>Tree</b> — balanced tree with configurable branching factor</li>
 *   <li><b>Bipartite</b> — two groups with edges only between groups</li>
 *   <li><b>Path</b> — simple linear chain of nodes</li>
 * </ul>
 *
 * <h3>Example usage:</h3>
 * <pre>{@code
 * GraphGenerator gen = new GraphGenerator();
 *
 * // Create a scale-free network with 100 nodes
 * GraphGenerator.GeneratedGraph sf = gen.scaleFreeBa(100, 3);
 * Graph<String, Edge> graph = sf.getGraph();
 *
 * // Create a small-world network
 * GraphGenerator.GeneratedGraph sw = gen.smallWorldWs(50, 4, 0.1);
 *
 * // Get a summary
 * System.out.println(sf.getSummary());
 * }</pre>
 *
 * @author zalenix
 */
public class GraphGenerator {

    private final Random random;

    /**
     * Creates a generator with a random seed.
     */
    public GraphGenerator() {
        this.random = new Random();
    }

    /**
     * Creates a generator with a fixed seed for reproducible results.
     *
     * @param seed the random seed
     */
    public GraphGenerator(long seed) {
        this.random = new Random(seed);
    }

    // ── Result container ────────────────────────────────────────────

    /**
     * Holds a generated graph along with metadata about how it was created.
     */
    public static class GeneratedGraph {
        private final Graph<String, Edge> graph;
        private final String topology;
        private final Map<String, Object> parameters;

        GeneratedGraph(Graph<String, Edge> graph, String topology,
                       Map<String, Object> parameters) {
            this.graph = graph;
            this.topology = topology;
            this.parameters = Collections.unmodifiableMap(parameters);
        }

        /** The generated JUNG graph. */
        public Graph<String, Edge> getGraph() { return graph; }

        /** Name of the topology used. */
        public String getTopology() { return topology; }

        /** Parameters used to generate the graph. */
        public Map<String, Object> getParameters() { return parameters; }

        /** Number of vertices. */
        public int getNodeCount() { return graph.getVertexCount(); }

        /** Number of edges. */
        public int getEdgeCount() { return graph.getEdgeCount(); }

        /**
         * Graph density: ratio of actual edges to maximum possible edges.
         *
         * @return density between 0.0 and 1.0
         */
        public double getDensity() {
            int n = graph.getVertexCount();
            if (n < 2) return 0.0;
            long maxEdges = (long) n * (n - 1) / 2;
            return (double) graph.getEdgeCount() / maxEdges;
        }

        /**
         * Average degree of nodes in the graph.
         *
         * @return average degree
         */
        public double getAverageDegree() {
            int n = graph.getVertexCount();
            if (n == 0) return 0.0;
            return 2.0 * graph.getEdgeCount() / n;
        }

        /**
         * Returns a human-readable summary of the generated graph.
         *
         * @return multiline summary string
         */
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("Generated Graph: ").append(topology).append("\n");
            sb.append("  Nodes: ").append(getNodeCount()).append("\n");
            sb.append("  Edges: ").append(getEdgeCount()).append("\n");
            sb.append(String.format("  Density: %.4f%n", getDensity()));
            sb.append(String.format("  Avg Degree: %.2f%n", getAverageDegree()));
            sb.append("  Parameters: ").append(parameters).append("\n");
            return sb.toString();
        }
    }

    // ── Node/Edge helpers ───────────────────────────────────────────

    private int edgeCounter = 0;

    private String nodeName(int i) {
        return "n" + i;
    }

    private Edge createEdge(String v1, String v2) {
        Edge e  new Edge("f", v1, v2);
        e.setLabel("gen_" + (edgeCounter++));
        e.setWeight(1.0f);
        return e;
    }

    private void addNodes(Graph<String, Edge> graph, int n) {
        for (int i = 0; i < n; i++) {
            graph.addVertex(nodeName(i));
        }
    }

    private void addEdgeIfAbsent(Graph<String, Edge> graph, String v1, String v2) {
        if (v1.equals(v2)) return;
        if (graph.findEdge(v1, v2) == null) {
            graph.addEdge(createEdge(v1, v2), v1, v2);
        }
    }

    // ── Complete Graph ──────────────────────────────────────────────

    /**
     * Creates a complete graph (K_n) where every node is connected
     * to every other node.
     *
     * <p>Produces n*(n-1)/2 edges. Useful as a baseline for density
     * and connectivity analysis.</p>
     *
     * @param n number of nodes (must be &gt;= 1)
     * @return the generated complete graph
     * @throws IllegalArgumentException if n &lt; 1
     */
    public GeneratedGraph complete(int n) {
        if (n < 1) throw new IllegalArgumentException("n must be >= 1");
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addNodes(g, n);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                g.addEdge(createEdge(nodeName(i), nodeName(j)),
                          nodeName(i), nodeName(j));
            }
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("n", n);
        return new GeneratedGraph(g, "Complete (K_" + n + ")", params);
    }

    // ── Ring / Cycle ────────────────────────────────────────────────

    /**
     * Creates a ring (cycle) graph where each node is connected to
     * the next, with the last node connected back to the first.
     *
     * @param n number of nodes (must be &gt;= 3)
     * @return the generated ring graph
     * @throws IllegalArgumentException if n &lt; 3
     */
    public GeneratedGraph ring(int n) {
        if (n < 3) throw new IllegalArgumentException("Ring requires n >= 3");
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addNodes(g, n);
        for (int i = 0; i < n; i++) {
            int next = (i + 1) % n;
            g.addEdge(createEdge(nodeName(i), nodeName(next)),
                      nodeName(i), nodeName(next));
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("n", n);
        return new GeneratedGraph(g, "Ring", params);
    }

    // ── Star ────────────────────────────────────────────────────────

    /**
     * Creates a star graph with one central hub connected to all
     * other nodes. No edges between leaf nodes.
     *
     * @param n total number of nodes including the hub (must be &gt;= 2)
     * @return the generated star graph
     * @throws IllegalArgumentException if n &lt; 2
     */
    public GeneratedGraph star(int n) {
        if (n < 2) throw new IllegalArgumentException("Star requires n >= 2");
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addNodes(g, n);
        String hub = nodeName(0);
        for (int i = 1; i < n; i++) {
            g.addEdge(createEdge(hub, nodeName(i)), hub, nodeName(i));
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("n", n);
        params.put("hub", hub);
        return new GeneratedGraph(g, "Star", params);
    }

    // ── Grid ────────────────────────────────────────────────────────

    /**
     * Creates a 2D grid (lattice) graph with the specified number
     * of rows and columns. Each interior node connects to its
     * 4 neighbors (up, down, left, right).
     *
     * @param rows number of rows (must be &gt;= 1)
     * @param cols number of columns (must be &gt;= 1)
     * @return the generated grid graph
     * @throws IllegalArgumentException if rows or cols &lt; 1
     */
    public GeneratedGraph grid(int rows, int cols) {
        if (rows < 1 || cols < 1) throw new IllegalArgumentException("rows and cols must be >= 1");
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        int n = rows * cols;
        addNodes(g, n);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = r * cols + c;
                if (c + 1 < cols) {
                    int right = r * cols + (c + 1);
                    g.addEdge(createEdge(nodeName(idx), nodeName(right)),
                              nodeName(idx), nodeName(right));
                }
                if (r + 1 < rows) {
                    int below = (r + 1) * cols + c;
                    g.addEdge(createEdge(nodeName(idx), nodeName(below)),
                              nodeName(idx), nodeName(below));
                }
            }
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("rows", rows);
        params.put("cols", cols);
        params.put("nodes", n);
        return new GeneratedGraph(g, "Grid (" + rows + "×" + cols + ")", params);
    }

    // ── Path ────────────────────────────────────────────────────────

    /**
     * Creates a simple path graph (linear chain).
     *
     * @param n number of nodes (must be &gt;= 2)
     * @return the generated path graph
     * @throws IllegalArgumentException if n &lt; 2
     */
    public GeneratedGraph path(int n) {
        if (n < 2) throw new IllegalArgumentException("Path requires n >= 2");
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addNodes(g, n);
        for (int i = 0; i < n - 1; i++) {
            g.addEdge(createEdge(nodeName(i), nodeName(i + 1)),
                      nodeName(i), nodeName(i + 1));
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("n", n);
        return new GeneratedGraph(g, "Path", params);
    }

    // ── Tree ────────────────────────────────────────────────────────

    /**
     * Creates a balanced tree with the given branching factor and depth.
     *
     * <p>The root is node 0. Each non-leaf node has exactly
     * {@code branchingFactor} children, up to the specified depth.</p>
     *
     * @param branchingFactor children per node (must be &gt;= 1)
     * @param depth           tree depth (must be &gt;= 0; 0 = root only)
     * @return the generated tree
     * @throws IllegalArgumentException if branchingFactor &lt; 1 or depth &lt; 0
     */
    public GeneratedGraph tree(int branchingFactor, int depth) {
        if (branchingFactor < 1) throw new IllegalArgumentException("branchingFactor must be >= 1");
        if (depth < 0) throw new IllegalArgumentException("depth must be >= 0");
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        int nodeId = 0;
        g.addVertex(nodeName(nodeId));

        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{nodeId, 0}); // [nodeIndex, currentDepth]

        while (!queue.isEmpty()) {
            int[] item = queue.poll();
            int parent = item[0];
            int d = item[1];
            if (d >= depth) continue;
            for (int b = 0; b < branchingFactor; b++) {
                nodeId++;
                String child = nodeName(nodeId);
                g.addVertex(child);
                g.addEdge(createEdge(nodeName(parent), child),
                          nodeName(parent), child);
                queue.add(new int[]{nodeId, d + 1});
            }
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("branchingFactor", branchingFactor);
        params.put("depth", depth);
        params.put("nodes", g.getVertexCount());
        return new GeneratedGraph(g, "Tree (b=" + branchingFactor + ", d=" + depth + ")", params);
    }

    // ── Random (Erdős–Rényi) ────────────────────────────────────────

    /**
     * Creates a random graph using the Erdős–Rényi G(n,p) model.
     *
     * <p>Each possible edge is included independently with probability
     * {@code p}. When p=0, no edges are created; when p=1, a complete
     * graph results.</p>
     *
     * @param n number of nodes (must be &gt;= 1)
     * @param p edge probability between 0.0 and 1.0
     * @return the generated random graph
     * @throws IllegalArgumentException if n &lt; 1 or p is out of range
     */
    public GeneratedGraph randomErdosRenyi(int n, double p) {
        if (n < 1) throw new IllegalArgumentException("n must be >= 1");
        if (p < 0.0 || p > 1.0) throw new IllegalArgumentException("p must be in [0, 1]");
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addNodes(g, n);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (random.nextDouble() < p) {
                    g.addEdge(createEdge(nodeName(i), nodeName(j)),
                              nodeName(i), nodeName(j));
                }
            }
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("n", n);
        params.put("p", p);
        return new GeneratedGraph(g, "Random (Erdős–Rényi)", params);
    }

    // ── Scale-Free (Barabási–Albert) ────────────────────────────────

    /**
     * Creates a scale-free network using the Barabási–Albert
     * preferential attachment model.
     *
     * <p>Starts with a small complete graph of {@code m} nodes, then
     * adds nodes one at a time, each connecting to {@code m} existing
     * nodes with probability proportional to their current degree.
     * This produces power-law degree distributions similar to many
     * real-world networks.</p>
     *
     * @param n total number of nodes (must be &gt; m)
     * @param m edges per new node (must be &gt;= 1)
     * @return the generated scale-free graph
     * @throws IllegalArgumentException if n &lt;= m or m &lt; 1
     */
    public GeneratedGraph scaleFreeBa(int n, int m) {
        if (m < 1) throw new IllegalArgumentException("m must be >= 1");
        if (n <= m) throw new IllegalArgumentException("n must be > m");
        Graph<String, Edge> g = new UndirectedSparseGraph<>();

        // Start with a complete graph of m+1 nodes
        for (int i = 0; i <= m; i++) {
            g.addVertex(nodeName(i));
        }
        for (int i = 0; i <= m; i++) {
            for (int j = i + 1; j <= m; j++) {
                g.addEdge(createEdge(nodeName(i), nodeName(j)),
                          nodeName(i), nodeName(j));
            }
        }

        // Preferential attachment
        List<String> degreeList = new ArrayList<>();
        for (String v : g.getVertices()) {
            int deg = g.degree(v);
            for (int d = 0; d < deg; d++) {
                degreeList.add(v);
            }
        }

        for (int i = m + 1; i < n; i++) {
            String newNode = nodeName(i);
            g.addVertex(newNode);

            Set<String> targets = new HashSet<>();
            int attempts = 0;
            while (targets.size() < m && attempts < m * 100) {
                String target = degreeList.get(random.nextInt(degreeList.size()));
                if (!target.equals(newNode)) {
                    targets.add(target);
                }
                attempts++;
            }

            for (String target : targets) {
                g.addEdge(createEdge(newNode, target), newNode, target);
                degreeList.add(newNode);
                degreeList.add(target);
            }
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("n", n);
        params.put("m", m);
        return new GeneratedGraph(g, "Scale-Free (Barabási–Albert)", params);
    }

    // ── Small-World (Watts–Strogatz) ────────────────────────────────

    /**
     * Creates a small-world network using the Watts–Strogatz model.
     *
     * <p>Starts with a ring lattice where each node is connected to
     * its {@code k} nearest neighbors (k/2 on each side), then
     * rewires each edge with probability {@code beta}. Low beta
     * produces regular lattices; high beta produces random graphs;
     * intermediate values create the "small-world" property
     * (high clustering + short path lengths).</p>
     *
     * @param n    number of nodes (must be &gt;= 4)
     * @param k    each node's initial neighbor count (must be even, &gt;= 2, &lt; n)
     * @param beta rewiring probability between 0.0 and 1.0
     * @return the generated small-world graph
     * @throws IllegalArgumentException if parameters are invalid
     */
    public GeneratedGraph smallWorldWs(int n, int k, double beta) {
        if (n < 4) throw new IllegalArgumentException("n must be >= 4");
        if (k < 2 || k % 2 != 0) throw new IllegalArgumentException("k must be even and >= 2");
        if (k >= n) throw new IllegalArgumentException("k must be < n");
        if (beta < 0.0 || beta > 1.0) throw new IllegalArgumentException("beta must be in [0, 1]");

        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addNodes(g, n);

        // Create ring lattice
        for (int i = 0; i < n; i++) {
            for (int j = 1; j <= k / 2; j++) {
                int neighbor = (i + j) % n;
                addEdgeIfAbsent(g, nodeName(i), nodeName(neighbor));
            }
        }

        // Rewire edges with probability beta
        for (int i = 0; i < n; i++) {
            for (int j = 1; j <= k / 2; j++) {
                if (random.nextDouble() < beta) {
                    int oldNeighbor = (i + j) % n;
                    Edge existing  g.findEdge(nodeName(i), nodeName(oldNeighbor));
                    if (existing == null) continue;

                    // Find a new target (not self, not already connected)
                    int attempts = 0;
                    while (attempts < n * 2) {
                        int newTarget = random.nextInt(n);
                        if (newTarget != i
                            && g.findEdge(nodeName(i), nodeName(newTarget)) == null) {
                            g.removeEdge(existing);
                            g.addEdge(createEdge(nodeName(i), nodeName(newTarget)),
                                      nodeName(i), nodeName(newTarget));
                            break;
                        }
                        attempts++;
                    }
                }
            }
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("n", n);
        params.put("k", k);
        params.put("beta", beta);
        return new GeneratedGraph(g, "Small-World (Watts–Strogatz)", params);
    }

    // ── Bipartite ───────────────────────────────────────────────────

    /**
     * Creates a random bipartite graph with two groups of nodes.
     *
     * <p>Edges only connect nodes in group A to nodes in group B
     * (never within the same group). Each possible cross-group edge
     * is included with probability {@code p}.</p>
     *
     * @param groupASize size of group A (must be &gt;= 1)
     * @param groupBSize size of group B (must be &gt;= 1)
     * @param p          edge probability between 0.0 and 1.0
     * @return the generated bipartite graph
     * @throws IllegalArgumentException if group sizes &lt; 1 or p is invalid
     */
    public GeneratedGraph bipartite(int groupASize, int groupBSize, double p) {
        if (groupASize < 1 || groupBSize < 1)
            throw new IllegalArgumentException("Group sizes must be >= 1");
        if (p < 0.0 || p > 1.0)
            throw new IllegalArgumentException("p must be in [0, 1]");

        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        int total = groupASize + groupBSize;
        addNodes(g, total);

        for (int i = 0; i < groupASize; i++) {
            for (int j = groupASize; j < total; j++) {
                if (random.nextDouble() < p) {
                    g.addEdge(createEdge(nodeName(i), nodeName(j)),
                              nodeName(i), nodeName(j));
                }
            }
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("groupASize", groupASize);
        params.put("groupBSize", groupBSize);
        params.put("p", p);
        params.put("totalNodes", total);
        return new GeneratedGraph(g, "Bipartite", params);
    }
}
