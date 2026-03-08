package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;

import java.util.*;

/**
 * Samples a representative subgraph from a larger graph using various
 * strategies. Useful for analyzing large networks by working with
 * smaller, representative samples while preserving key structural
 * properties.
 *
 * <h3>Sampling Strategies</h3>
 * <ul>
 *   <li><b>Random Node</b> — uniformly samples nodes; induces the subgraph
 *       on the selected nodes. Simple but may miss edges and fragment
 *       the sample.</li>
 *   <li><b>Random Edge</b> — uniformly samples edges; includes both
 *       endpoints. Preserves edge density better than node sampling.</li>
 *   <li><b>Snowball / BFS</b> — starts from seed nodes and explores
 *       outward layer by layer. Produces connected, localized samples.
 *       Good for community studies.</li>
 *   <li><b>Random Walk</b> — performs a random walk from a seed node,
 *       adding visited nodes. With restarts, produces samples with
 *       degree distribution closer to the original.</li>
 *   <li><b>Forest Fire</b> — Leskovec &amp; Faloutsos (2006): each
 *       visited node "burns" a random subset of its neighbors
 *       (governed by a forward-burning probability). Produces
 *       samples that preserve heavy tails and community structure.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 *   GraphSampler sampler = new GraphSampler(graph);
 *   // Sample 30% of nodes via random walk
 *   SampleResult result = sampler.randomWalk(0.30, null, 0.15);
 *   Graph&lt;String, edge&gt; sample = result.getSample();
 *   System.out.println(result.getSummary());
 * </pre>
 *
 * @author zalenix
 */
public class GraphSampler {

    private final Graph<String, edge> graph;
    private final Random rng;

    /**
     * Creates a sampler for the given graph.
     *
     * @param graph the source graph
     * @throws IllegalArgumentException if graph is null
     */
    public GraphSampler(Graph<String, edge> graph) {
        this(graph, new Random());
    }

    /**
     * Creates a sampler with a specific RNG (for reproducibility).
     *
     * @param graph the source graph
     * @param rng   random number generator
     */
    public GraphSampler(Graph<String, edge> graph, Random rng) {
        if (graph == null) throw new IllegalArgumentException("Graph must not be null");
        if (rng == null) throw new IllegalArgumentException("RNG must not be null");
        this.graph = graph;
        this.rng = rng;
    }

    // ── Sampling strategies ─────────────────────────────────────

    /**
     * Uniform random node sampling. Selects nodes uniformly at random
     * and induces the subgraph on those nodes (only edges where both
     * endpoints are selected are included).
     *
     * @param fraction fraction of nodes to sample (0.0, 1.0]
     * @return the sample result
     */
    public SampleResult randomNode(double fraction) {
        validateFraction(fraction);
        int n = graph.getVertexCount();
        int target = Math.max(1, (int) Math.ceil(n * fraction));

        List<String> vertices = new ArrayList<String>(graph.getVertices());
        Collections.shuffle(vertices, rng);

        Set<String> sampled = new LinkedHashSet<String>();
        for (int i = 0; i < Math.min(target, vertices.size()); i++) {
            sampled.add(vertices.get(i));
        }

        return buildResult(sampled, "RandomNode");
    }

    /**
     * Uniform random edge sampling. Selects edges uniformly at random
     * and includes both endpoints of each selected edge.
     *
     * @param fraction fraction of edges to sample (0.0, 1.0]
     * @return the sample result
     */
    public SampleResult randomEdge(double fraction) {
        validateFraction(fraction);
        int m = graph.getEdgeCount();
        int target = Math.max(1, (int) Math.ceil(m * fraction));

        List<edge> edges = new ArrayList<edge>(graph.getEdges());
        Collections.shuffle(edges, rng);

        Set<String> sampled = new LinkedHashSet<String>();
        Set<edge> sampledEdges = new LinkedHashSet<edge>();
        for (int i = 0; i < Math.min(target, edges.size()); i++) {
            edge e = edges.get(i);
            Collection<String> endpoints = graph.getEndpoints(e);
            if (endpoints != null && endpoints.size() == 2) {
                Iterator<String> it = endpoints.iterator();
                sampled.add(it.next());
                sampled.add(it.next());
                sampledEdges.add(e);
            }
        }

        return buildResultFromEdges(sampled, sampledEdges, "RandomEdge");
    }

    /**
     * Snowball / BFS sampling. Starts from one or more seed nodes and
     * explores outward level by level until the target sample size
     * is reached.
     *
     * @param fraction fraction of nodes to sample (0.0, 1.0]
     * @param seeds    seed nodes (null or empty = one random seed)
     * @return the sample result
     */
    public SampleResult snowball(double fraction, Collection<String> seeds) {
        validateFraction(fraction);
        int n = graph.getVertexCount();
        int target = Math.max(1, (int) Math.ceil(n * fraction));

        Set<String> sampled = new LinkedHashSet<String>();
        Queue<String> queue = new LinkedList<String>();

        if (seeds == null || seeds.isEmpty()) {
            String seed = randomVertex();
            if (seed == null) return buildResult(sampled, "Snowball");
            sampled.add(seed);
            queue.add(seed);
        } else {
            for (String s : seeds) {
                if (graph.containsVertex(s) && sampled.add(s)) {
                    queue.add(s);
                }
            }
            if (sampled.isEmpty()) {
                String seed = randomVertex();
                if (seed == null) return buildResult(sampled, "Snowball");
                sampled.add(seed);
                queue.add(seed);
            }
        }

        while (!queue.isEmpty() && sampled.size() < target) {
            String current = queue.poll();
            Collection<String> neighbors = graph.getNeighbors(current);
            if (neighbors == null) continue;
            List<String> nList = new ArrayList<String>(neighbors);
            Collections.shuffle(nList, rng);
            for (String nb : nList) {
                if (sampled.size() >= target) break;
                if (sampled.add(nb)) {
                    queue.add(nb);
                }
            }
        }

        return buildResult(sampled, "Snowball");
    }

    /**
     * Random walk sampling with optional restarts. Performs a random walk,
     * adding each visited node. On restart, jumps back to the starting node
     * (helps avoid getting trapped in local neighborhoods).
     *
     * @param fraction       fraction of nodes to sample (0.0, 1.0]
     * @param startNode      starting node (null = random)
     * @param restartProb    probability of restarting to seed each step [0, 1)
     * @return the sample result
     */
    public SampleResult randomWalk(double fraction, String startNode,
                                   double restartProb) {
        validateFraction(fraction);
        if (restartProb < 0 || restartProb >= 1) {
            throw new IllegalArgumentException(
                "Restart probability must be in [0, 1), got " + restartProb);
        }

        int n = graph.getVertexCount();
        int target = Math.max(1, (int) Math.ceil(n * fraction));

        String seed;
        if (startNode != null && graph.containsVertex(startNode)) {
            seed = startNode;
        } else {
            seed = randomVertex();
        }
        if (seed == null) return buildResult(new LinkedHashSet<String>(), "RandomWalk");

        Set<String> sampled = new LinkedHashSet<String>();
        sampled.add(seed);
        String current = seed;
        int maxSteps = n * 20; // safety limit to prevent infinite loops
        int steps = 0;

        while (sampled.size() < target && steps < maxSteps) {
            steps++;

            // Restart?
            if (rng.nextDouble() < restartProb) {
                current = seed;
                continue;
            }

            Collection<String> neighbors = graph.getNeighbors(current);
            if (neighbors == null || neighbors.isEmpty()) {
                // Dead end — restart
                current = seed;
                continue;
            }

            List<String> nList = new ArrayList<String>(neighbors);
            current = nList.get(rng.nextInt(nList.size()));
            sampled.add(current);
        }

        return buildResult(sampled, "RandomWalk");
    }

    /**
     * Forest Fire sampling (Leskovec &amp; Faloutsos, 2006). From a seed,
     * each visited node "burns" each unvisited neighbor independently
     * with probability {@code burnProb}. Burned neighbors are added
     * to the sample and recursively expanded.
     *
     * <p>This produces samples that tend to preserve community structure
     * and heavy-tailed degree distributions better than uniform methods.</p>
     *
     * @param fraction fraction of nodes to sample (0.0, 1.0]
     * @param startNode seed node (null = random)
     * @param burnProb  forward burning probability (0, 1)
     * @return the sample result
     */
    public SampleResult forestFire(double fraction, String startNode,
                                   double burnProb) {
        validateFraction(fraction);
        if (burnProb <= 0 || burnProb >= 1) {
            throw new IllegalArgumentException(
                "Burn probability must be in (0, 1), got " + burnProb);
        }

        int n = graph.getVertexCount();
        int target = Math.max(1, (int) Math.ceil(n * fraction));

        String seed;
        if (startNode != null && graph.containsVertex(startNode)) {
            seed = startNode;
        } else {
            seed = randomVertex();
        }
        if (seed == null) return buildResult(new LinkedHashSet<String>(), "ForestFire");

        Set<String> sampled = new LinkedHashSet<String>();
        sampled.add(seed);
        Deque<String> frontier = new ArrayDeque<String>();
        frontier.add(seed);

        while (!frontier.isEmpty() && sampled.size() < target) {
            String current = frontier.poll();
            Collection<String> neighbors = graph.getNeighbors(current);
            if (neighbors == null) continue;

            // Burn each unvisited neighbor with probability burnProb
            List<String> unvisited = new ArrayList<String>();
            for (String nb : neighbors) {
                if (!sampled.contains(nb)) {
                    unvisited.add(nb);
                }
            }
            Collections.shuffle(unvisited, rng);

            for (String nb : unvisited) {
                if (sampled.size() >= target) break;
                if (rng.nextDouble() < burnProb) {
                    sampled.add(nb);
                    frontier.add(nb);
                }
            }

            // If frontier empties but we haven't reached target, pick a
            // random unvisited neighbor of any sampled node
            if (frontier.isEmpty() && sampled.size() < target) {
                String bridge = findBridgeNode(sampled);
                if (bridge != null) {
                    sampled.add(bridge);
                    frontier.add(bridge);
                } else {
                    break; // no more reachable nodes
                }
            }
        }

        return buildResult(sampled, "ForestFire");
    }

    // ── Helper methods ──────────────────────────────────────────

    private String randomVertex() {
        Collection<String> vertices = graph.getVertices();
        if (vertices.isEmpty()) return null;
        List<String> list = new ArrayList<String>(vertices);
        return list.get(rng.nextInt(list.size()));
    }

    /**
     * Find an unvisited neighbor of any sampled node (for forest fire
     * re-ignition).
     */
    private String findBridgeNode(Set<String> sampled) {
        List<String> sampledList = new ArrayList<String>(sampled);
        Collections.shuffle(sampledList, rng);
        for (String v : sampledList) {
            Collection<String> neighbors = graph.getNeighbors(v);
            if (neighbors == null) continue;
            for (String nb : neighbors) {
                if (!sampled.contains(nb)) return nb;
            }
        }
        return null;
    }

    private void validateFraction(double fraction) {
        if (fraction <= 0 || fraction > 1) {
            throw new IllegalArgumentException(
                "Fraction must be in (0.0, 1.0], got " + fraction);
        }
    }

    /**
     * Build a SampleResult by inducing the subgraph on the sampled nodes.
     */
    private SampleResult buildResult(Set<String> sampledNodes, String strategy) {
        Graph<String, edge> sample = new UndirectedSparseGraph<String, edge>();
        for (String v : sampledNodes) {
            sample.addVertex(v);
        }

        for (edge e : graph.getEdges()) {
            Collection<String> endpoints = graph.getEndpoints(e);
            if (endpoints == null || endpoints.size() < 2) continue;
            Iterator<String> it = endpoints.iterator();
            String v1 = it.next();
            String v2 = it.next();
            if (sampledNodes.contains(v1) && sampledNodes.contains(v2)) {
                edge copy = new edge(e.getType(), v1, v2);
                copy.setWeight(e.getWeight());
                copy.setLabel(e.getLabel());
                if (e.getTimestamp() != null) copy.setTimestamp(e.getTimestamp());
                if (e.getEndTimestamp() != null) copy.setEndTimestamp(e.getEndTimestamp());
                sample.addEdge(copy, v1, v2);
            }
        }

        return new SampleResult(sample, graph.getVertexCount(),
            graph.getEdgeCount(), strategy);
    }

    /**
     * Build a SampleResult from pre-selected edges (for edge sampling).
     */
    private SampleResult buildResultFromEdges(Set<String> sampledNodes,
                                              Set<edge> sampledEdges,
                                              String strategy) {
        Graph<String, edge> sample = new UndirectedSparseGraph<String, edge>();
        for (String v : sampledNodes) {
            sample.addVertex(v);
        }

        for (edge e : sampledEdges) {
            Collection<String> endpoints = graph.getEndpoints(e);
            if (endpoints == null || endpoints.size() < 2) continue;
            Iterator<String> it = endpoints.iterator();
            String v1 = it.next();
            String v2 = it.next();
            edge copy = new edge(e.getType(), v1, v2);
            copy.setWeight(e.getWeight());
            copy.setLabel(e.getLabel());
            if (e.getTimestamp() != null) copy.setTimestamp(e.getTimestamp());
            if (e.getEndTimestamp() != null) copy.setEndTimestamp(e.getEndTimestamp());
            sample.addEdge(copy, v1, v2);
        }

        // Also add any induced edges between sampled nodes that weren't
        // directly selected (edges between endpoints of sampled edges)
        for (edge e : graph.getEdges()) {
            if (sampledEdges.contains(e)) continue;
            Collection<String> endpoints = graph.getEndpoints(e);
            if (endpoints == null || endpoints.size() < 2) continue;
            Iterator<String> it = endpoints.iterator();
            String v1 = it.next();
            String v2 = it.next();
            if (sampledNodes.contains(v1) && sampledNodes.contains(v2)
                    && sample.findEdge(v1, v2) == null) {
                edge copy = new edge(e.getType(), v1, v2);
                copy.setWeight(e.getWeight());
                copy.setLabel(e.getLabel());
                if (e.getTimestamp() != null) copy.setTimestamp(e.getTimestamp());
                if (e.getEndTimestamp() != null) copy.setEndTimestamp(e.getEndTimestamp());
                sample.addEdge(copy, v1, v2);
            }
        }

        return new SampleResult(sample, graph.getVertexCount(),
            graph.getEdgeCount(), strategy);
    }

    // ═════════════════════════════════════════════════════════════
    // SampleResult
    // ═════════════════════════════════════════════════════════════

    /**
     * Result of a graph sampling operation, including the sampled subgraph,
     * representativeness metrics, and a summary.
     */
    public static class SampleResult {
        private final Graph<String, edge> sample;
        private final int originalNodes;
        private final int originalEdges;
        private final String strategy;
        private final double nodeCoverage;
        private final double edgeCoverage;
        private final double density;
        private final double originalDensity;
        private final int componentCount;

        SampleResult(Graph<String, edge> sample, int originalNodes,
                     int originalEdges, String strategy) {
            this.sample = sample;
            this.originalNodes = originalNodes;
            this.originalEdges = originalEdges;
            this.strategy = strategy;

            int sn = sample.getVertexCount();
            int se = sample.getEdgeCount();
            this.nodeCoverage = originalNodes > 0
                ? (double) sn / originalNodes : 0;
            this.edgeCoverage = originalEdges > 0
                ? (double) se / originalEdges : 0;
            this.density = sn > 1
                ? (2.0 * se) / (sn * (sn - 1.0)) : 0;
            this.originalDensity = originalNodes > 1
                ? (2.0 * originalEdges) / (originalNodes * (originalNodes - 1.0)) : 0;

            // Count components
            this.componentCount = GraphUtils.findComponents(sample).size();
        }

        /** The sampled subgraph. */
        public Graph<String, edge> getSample() { return sample; }

        /** Number of nodes in the sample. */
        public int getNodeCount() { return sample.getVertexCount(); }

        /** Number of edges in the sample. */
        public int getEdgeCount() { return sample.getEdgeCount(); }

        /** Node count in the original graph. */
        public int getOriginalNodeCount() { return originalNodes; }

        /** Edge count in the original graph. */
        public int getOriginalEdgeCount() { return originalEdges; }

        /** Sampling strategy used. */
        public String getStrategy() { return strategy; }

        /** Fraction of original nodes in the sample. */
        public double getNodeCoverage() { return nodeCoverage; }

        /** Fraction of original edges in the sample. */
        public double getEdgeCoverage() { return edgeCoverage; }

        /** Density of the sample graph. */
        public double getDensity() { return density; }

        /** Density of the original graph. */
        public double getOriginalDensity() { return originalDensity; }

        /**
         * Density ratio: sample density / original density.
         * Values near 1.0 indicate good density preservation.
         */
        public double getDensityRatio() {
            return originalDensity > 0 ? density / originalDensity : 0;
        }

        /** Number of connected components in the sample. */
        public int getComponentCount() { return componentCount; }

        /** Whether the sample is connected (single component). */
        public boolean isConnected() { return componentCount <= 1; }

        /**
         * Human-readable summary of the sampling result.
         */
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Sample Summary (").append(strategy).append(") ===\n");
            sb.append(String.format("Original:  %d nodes, %d edges (density %.4f)%n",
                originalNodes, originalEdges, originalDensity));
            sb.append(String.format("Sample:    %d nodes, %d edges (density %.4f)%n",
                sample.getVertexCount(), sample.getEdgeCount(), density));
            sb.append(String.format("Coverage:  %.1f%% nodes, %.1f%% edges%n",
                nodeCoverage * 100, edgeCoverage * 100));
            sb.append(String.format("Density ratio: %.3f%n", getDensityRatio()));
            sb.append(String.format("Components: %d%s%n",
                componentCount, isConnected() ? " (connected)" : " (fragmented)"));
            return sb.toString();
        }
    }
}
