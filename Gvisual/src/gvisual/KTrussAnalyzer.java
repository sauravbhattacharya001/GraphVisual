package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.*;
import java.util.stream.Collectors;

/**
 * K-Truss Decomposition — identifies cohesive subgraphs based on triangle
 * support. Each edge receives a <em>truss number</em> equal to the highest
 * k-truss it belongs to:
 *
 * <blockquote>
 * A <b>k-truss</b> is a maximal subgraph where every edge participates in
 * at least (k − 2) triangles within that subgraph.
 * </blockquote>
 *
 * <p>K-truss sits between k-core (degree-based, too loose) and cliques
 * (complete subgraphs, too strict) in the cohesion hierarchy, providing
 * polynomial-time, triangle-aware dense subgraph extraction.</p>
 *
 * <h3>Algorithm (peeling, O(m · t_max))</h3>
 * <ol>
 *   <li>Count triangle support for every edge.</li>
 *   <li>Starting from k = 2, iteratively remove edges with support &lt; k − 2.</li>
 *   <li>When an edge is removed, update triangle counts for affected edges.</li>
 *   <li>An edge's truss number is the k at which it was removed.</li>
 * </ol>
 *
 * <p>References:</p>
 * <ul>
 *   <li>Cohen (2008), <em>Trusses: Cohesive Subgraphs for Social Network Analysis</em></li>
 *   <li>Wang &amp; Cheng (2012), <em>Truss Decomposition in Massive Networks</em></li>
 * </ul>
 *
 * @author zalenix
 */
public class KTrussAnalyzer {

    private final Graph<String, Edge> graph;
    private Map<edge, Integer> trussNumbers;
    private Map<edge, Integer> triangleSupport;
    private int maxTrussNumber;
    private boolean computed;

    /**
     * Creates a new KTrussAnalyzer for the given graph.
     *
     * @param graph the graph to analyze (treated as undirected)
     */
    public KTrussAnalyzer(Graph<String, Edge> graph) {
        this.graph = graph;
        this.trussNumbers = new LinkedHashMap<>();
        this.triangleSupport = new LinkedHashMap<>();
        this.maxTrussNumber = 0;
        this.computed = false;
    }

    /**
     * Computes the truss decomposition if not already done.
     */
    private void ensureComputed() {
        if (!computed) {
            compute();
        }
    }

    /**
     * Runs the truss decomposition peeling algorithm.
     */
    private void compute() {
        // Build adjacency structures for efficient triangle enumeration
        Set<Edge> remainingEdges = new LinkedHashSet<>(graph.getEdges());
        Map<String, Set<String>> adjacency = buildAdjacency(remainingEdges);
        Map<edge, Set<Edge>> edgeTrianglePartners = new LinkedHashMap<>();

        // Step 1: compute initial triangle support for each edge
        for (Edge e : remainingEdges) {
            triangleSupport.put(e, 0);
            edgeTrianglePartners.put(e, new LinkedHashSet<>());
        }

        // Find all triangles
        for (Edge e : remainingEdges) {
            String u = getEndpoint1(e);
            String v = getEndpoint2(e);
            if (u == null || v == null) continue;

            Set<String> neighborsU = adjacency.getOrDefault(u, Collections.emptySet());
            Set<String> neighborsV = adjacency.getOrDefault(v, Collections.emptySet());

            for (String w : neighborsU) {
                if (neighborsV.contains(w)) {
                    // Triangle u-v-w found
                    triangleSupport.merge(e, 1, Integer::sum);
                    edge uw = findEdge(remainingEdges, u, w);
                    edge vw = findEdge(remainingEdges, v, w);
                    if (uw != null) {
                        triangleSupport.merge(uw, 1, Integer::sum);
                        edgeTrianglePartners.get(e).add(uw);
                        edgeTrianglePartners.get(uw).add(e);
                    }
                    if (vw != null) {
                        triangleSupport.merge(vw, 1, Integer::sum);
                        edgeTrianglePartners.get(e).add(vw);
                        edgeTrianglePartners.get(vw).add(e);
                    }
                }
            }
        }

        // Correct for double-counting: each triangle is found 3 times (once per edge)
        // but each edge in a triangle gets counted twice for neighbors
        // Actually, let me recalculate properly using a direct approach
        triangleSupport.clear();
        for (Edge e : remainingEdges) {
            triangleSupport.put(e, 0);
        }

        // Enumerate triangles properly: for each edge (u,v), count common neighbors
        for (Edge e : remainingEdges) {
            String u = getEndpoint1(e);
            String v = getEndpoint2(e);
            if (u == null || v == null) continue;

            Set<String> neighborsU = adjacency.getOrDefault(u, Collections.emptySet());
            Set<String> neighborsV = adjacency.getOrDefault(v, Collections.emptySet());

            int count = 0;
            for (String w : neighborsU) {
                if (!w.equals(v) && neighborsV.contains(w)) {
                    count++;
                }
            }
            triangleSupport.put(e, count);
        }

        // Step 2: Peeling — iteratively remove edges with lowest support
        Set<Edge> active = new LinkedHashSet<>(remainingEdges);
        Map<edge, Integer> support = new LinkedHashMap<>(triangleSupport);

        int k = 2;
        while (!active.isEmpty()) {
            boolean changed = true;
            while (changed) {
                changed = false;
                Iterator<edge> it = active.iterator();
                List<Edge> toRemove = new ArrayList<>();

                while (it.hasNext()) {
                    edge e = it.next();
                    if (support.getOrDefault(e, 0) < k - 2) {
                        toRemove.add(e);
                    }
                }

                for (Edge e : toRemove) {
                    active.remove(e);
                    trussNumbers.put(e, k);
                    changed = true;

                    // Update support for edges sharing a triangle with e
                    String u = getEndpoint1(e);
                    String v = getEndpoint2(e);
                    if (u == null || v == null) continue;

                    for (Edge other : active) {
                        String ou = getEndpoint1(other);
                        String ov = getEndpoint2(other);
                        if (ou == null || ov == null) continue;

                        // Check if e and other share a triangle
                        String shared = null;
                        if (ou.equals(u) || ov.equals(u)) {
                            shared = u;
                        }
                        if (ou.equals(v) || ov.equals(v)) {
                            if (shared != null && !shared.equals(v)) {
                                // Both endpoints of e connect to other — skip
                            } else {
                                shared = v;
                            }
                        }

                        if (shared != null) {
                            String otherEnd = ou.equals(shared) ? ov : ou;
                            String eOtherEnd = getEndpoint1(e).equals(shared) ? getEndpoint2(e) : getEndpoint1(e);

                            // Check if there's an active edge between otherEnd and eOtherEnd
                            if (hasActiveEdge(active, otherEnd, eOtherEnd)) {
                                support.merge(other, -1, Integer::sum);
                            }
                        }
                    }
                }
            }

            // All remaining edges have support >= k-2, increase k
            for (Edge e : active) {
                trussNumbers.put(e, k + 1); // tentative — will be overwritten if removed later
            }
            k++;
        }

        // Fix: edges still active at the end get truss number = k-1
        maxTrussNumber = trussNumbers.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

        computed = true;
    }

    /**
     * Gets the truss number of a specific edge.
     *
     * @param e the edge
     * @return the truss number, or 0 if edge not in graph
     */
    public int getTrussNumber(Edge e) {
        ensureComputed();
        return trussNumbers.getOrDefault(e, 0);
    }

    /**
     * Gets the maximum truss number in the graph.
     *
     * @return the maximum truss number
     */
    public int getMaxTrussNumber() {
        ensureComputed();
        return maxTrussNumber;
    }

    /**
     * Extracts the k-truss subgraph — all edges with truss number ≥ k
     * and their incident vertices.
     *
     * @param k the truss parameter (k ≥ 2)
     * @return a new graph containing only edges in the k-truss
     */
    public Graph<String, Edge> getKTruss(int k) {
        ensureComputed();
        Graph<String, Edge> subgraph = new UndirectedSparseGraph<>();

        for (Map.Entry<edge, Integer> entry : trussNumbers.entrySet()) {
            if (entry.getValue() >= k) {
                edge e = entry.getKey();
                String u = getEndpoint1(e);
                String v = getEndpoint2(e);
                if (u != null && v != null) {
                    if (!subgraph.containsVertex(u)) subgraph.addVertex(u);
                    if (!subgraph.containsVertex(v)) subgraph.addVertex(v);
                    subgraph.addEdge(e, u, v);
                }
            }
        }

        return subgraph;
    }

    /**
     * Returns the distribution of edge truss numbers as a histogram.
     *
     * @return map from truss number to count of edges with that truss number
     */
    public Map<Integer, Integer> getTrussDistribution() {
        ensureComputed();
        Map<Integer, Integer> dist = new TreeMap<>();
        for (int tn : trussNumbers.values()) {
            dist.merge(tn, 1, Integer::sum);
        }
        return dist;
    }

    /**
     * Gets the triangle support count for an edge (number of triangles
     * containing that edge in the original graph).
     *
     * @param e the edge
     * @return number of triangles containing this edge
     */
    public int getTriangleSupport(Edge e) {
        ensureComputed();
        return triangleSupport.getOrDefault(e, 0);
    }

    /**
     * Returns the truss hierarchy — nested structure showing how trusses
     * decompose at each level.
     *
     * @return map from k to the set of edges in the k-truss but not in the (k+1)-truss
     */
    public Map<Integer, List<Edge>> getTrussHierarchy() {
        ensureComputed();
        Map<Integer, List<Edge>> hierarchy = new TreeMap<>();
        for (Map.Entry<edge, Integer> entry : trussNumbers.entrySet()) {
            hierarchy.computeIfAbsent(entry.getValue(), k -> new ArrayList<>())
                    .add(entry.getKey());
        }
        return hierarchy;
    }

    /**
     * Compares k-truss communities with k-core communities side-by-side.
     * Vertices in high k-cores but low k-trusses are connected to many
     * weakly-related nodes; vertices in high k-trusses are embedded in
     * truly dense neighborhoods.
     *
     * @return a comparison report as a list of formatted strings
     */
    public List<String> compareTrussVsCore() {
        ensureComputed();
        KCoreDecomposition kcore = new KCoreDecomposition(graph);

        List<String> report = new ArrayList<>();
        report.add("=== K-Truss vs K-Core Comparison ===");
        report.add(String.format("Max truss number: %d", maxTrussNumber));
        report.add(String.format("Graph degeneracy (max core): %d", kcore.getDegeneracy()));
        report.add("");

        // For each vertex, compute its "truss participation" — max truss of any incident edge
        Map<String, Integer> vertexTruss = new LinkedHashMap<>();
        for (String v : graph.getVertices()) {
            int maxT = 0;
            for (Edge e : graph.getIncidentEdges(v)) {
                maxT = Math.max(maxT, trussNumbers.getOrDefault(e, 0));
            }
            vertexTruss.put(v, maxT);
        }

        // Find disagreement vertices
        Map<String, Integer> coreMap = kcore.getCoreness();
        List<String> highCoreOnly = new ArrayList<>();
        List<String> highTrussOnly = new ArrayList<>();

        int coreMedian = medianValue(coreMap.values());
        int trussMedian = medianValue(vertexTruss.values());

        for (String v : graph.getVertices()) {
            int c = coreMap.getOrDefault(v, 0);
            int t = vertexTruss.getOrDefault(v, 0);
            if (c > coreMedian && t <= trussMedian) {
                highCoreOnly.add(v);
            } else if (t > trussMedian && c <= coreMedian) {
                highTrussOnly.add(v);
            }
        }

        report.add("Vertices in high k-core but low k-truss (many weak connections):");
        if (highCoreOnly.isEmpty()) {
            report.add("  (none)");
        } else {
            for (String v : highCoreOnly.subList(0, Math.min(10, highCoreOnly.size()))) {
                report.add(String.format("  %s (core=%d, truss=%d)",
                        v, coreMap.get(v), vertexTruss.get(v)));
            }
        }

        report.add("");
        report.add("Vertices in high k-truss but low k-core (dense neighborhood):");
        if (highTrussOnly.isEmpty()) {
            report.add("  (none)");
        } else {
            for (String v : highTrussOnly.subList(0, Math.min(10, highTrussOnly.size()))) {
                report.add(String.format("  %s (core=%d, truss=%d)",
                        v, coreMap.get(v), vertexTruss.get(v)));
            }
        }

        report.add("");
        report.add("Truss distribution:");
        for (Map.Entry<Integer, Integer> entry : getTrussDistribution().entrySet()) {
            report.add(String.format("  %d-truss: %d edges", entry.getKey(), entry.getValue()));
        }

        return report;
    }

    /**
     * Returns a summary of the truss decomposition.
     *
     * @return formatted summary string
     */
    public String getSummary() {
        ensureComputed();
        StringBuilder sb = new StringBuilder();
        sb.append("K-Truss Decomposition Summary\n");
        sb.append(String.format("  Edges analyzed: %d\n", trussNumbers.size()));
        sb.append(String.format("  Max truss number: %d\n", maxTrussNumber));
        sb.append("  Distribution:\n");
        for (Map.Entry<Integer, Integer> entry : getTrussDistribution().entrySet()) {
            sb.append(String.format("    %d-truss: %d edges\n", entry.getKey(), entry.getValue()));
        }
        return sb.toString();
    }

    // --- Helper methods ---

    private Map<String, Set<String>> buildAdjacency(Set<Edge> edges) {
        Map<String, Set<String>> adj = new LinkedHashMap<>();
        for (Edge e : edges) {
            String u = getEndpoint1(e);
            String v = getEndpoint2(e);
            if (u != null && v != null) {
                adj.computeIfAbsent(u, k -> new LinkedHashSet<>()).add(v);
                adj.computeIfAbsent(v, k -> new LinkedHashSet<>()).add(u);
            }
        }
        return adj;
    }

    private String getEndpoint1(Edge e) {
        Collection<String> endpoints = graph.getEndpoints(e);
        if (endpoints == null || endpoints.isEmpty()) return null;
        Iterator<String> it = endpoints.iterator();
        return it.next();
    }

    private String getEndpoint2(Edge e) {
        Collection<String> endpoints = graph.getEndpoints(e);
        if (endpoints == null || endpoints.size() < 2) return null;
        Iterator<String> it = endpoints.iterator();
        it.next();
        return it.next();
    }

    private edge findEdge(Set<Edge> edges, String u, String v) {
        for (Edge e : edges) {
            String eu = getEndpoint1(e);
            String ev = getEndpoint2(e);
            if ((u.equals(eu) && v.equals(ev)) || (u.equals(ev) && v.equals(eu))) {
                return e;
            }
        }
        return null;
    }

    private boolean hasActiveEdge(Set<Edge> active, String u, String v) {
        for (Edge e : active) {
            String eu = getEndpoint1(e);
            String ev = getEndpoint2(e);
            if ((u.equals(eu) && v.equals(ev)) || (u.equals(ev) && v.equals(eu))) {
                return true;
            }
        }
        return false;
    }

    private int medianValue(Collection<Integer> values) {
        if (values.isEmpty()) return 0;
        List<Integer> sorted = values.stream().sorted().collect(Collectors.toList());
        return sorted.get(sorted.size() / 2);
    }
}
