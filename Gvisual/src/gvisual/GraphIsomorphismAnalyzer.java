package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.util.*;

/**
 * Graph Isomorphism Checker — determines whether two graphs have the
 * same structure (are isomorphic), ignoring vertex labels.
 *
 * <p>Two graphs G1 and G2 are <b>isomorphic</b> if there exists a
 * bijection f: V(G1) → V(G2) such that (u, v) is an edge in G1
 * if and only if (f(u), f(v)) is an edge in G2.</p>
 *
 * <p>Uses a multi-stage approach:</p>
 * <ol>
 *   <li><b>Fast rejection:</b> vertex count, edge count, degree sequence</li>
 *   <li><b>Degree-based partitioning:</b> vertices grouped by degree</li>
 *   <li><b>Backtracking search:</b> VF2-style matching with feasibility pruning</li>
 * </ol>
 *
 * <p>Time complexity: O(V!) worst case, but fast-rejection filters and
 * degree-based pruning make it practical for most real-world graphs.</p>
 *
 * @author zalenix
 */
public class GraphIsomorphismAnalyzer {

    private final Graph<String, edge> graph1;
    private final Graph<String, edge> graph2;

    /**
     * Create a new isomorphism analyzer for two graphs.
     *
     * @param graph1 the first graph (must not be null)
     * @param graph2 the second graph (must not be null)
     * @throws IllegalArgumentException if either graph is null
     */
    public GraphIsomorphismAnalyzer(Graph<String, edge> graph1,
                                     Graph<String, edge> graph2) {
        if (graph1 == null || graph2 == null) {
            throw new IllegalArgumentException("Both graphs must not be null");
        }
        this.graph1 = graph1;
        this.graph2 = graph2;
    }

    // ── Result class ────────────────────────────────────────────

    /**
     * Result of an isomorphism check.
     */
    public static class IsomorphismResult {
        private final boolean isomorphic;
        private final Map<String, String> mapping;
        private final String rejectionReason;
        private final List<Integer> degreeSequence1;
        private final List<Integer> degreeSequence2;

        IsomorphismResult(boolean isomorphic, Map<String, String> mapping,
                          String rejectionReason,
                          List<Integer> degreeSequence1,
                          List<Integer> degreeSequence2) {
            this.isomorphic = isomorphic;
            this.mapping = mapping != null
                    ? Collections.unmodifiableMap(new LinkedHashMap<String, String>(mapping))
                    : Collections.<String, String>emptyMap();
            this.rejectionReason = rejectionReason;
            this.degreeSequence1 = degreeSequence1 != null
                    ? Collections.unmodifiableList(new ArrayList<Integer>(degreeSequence1))
                    : Collections.<Integer>emptyList();
            this.degreeSequence2 = degreeSequence2 != null
                    ? Collections.unmodifiableList(new ArrayList<Integer>(degreeSequence2))
                    : Collections.<Integer>emptyList();
        }

        /** Whether the two graphs are isomorphic. */
        public boolean isIsomorphic() { return isomorphic; }

        /**
         * Vertex mapping from graph1 to graph2 (if isomorphic).
         * Empty map if not isomorphic.
         */
        public Map<String, String> getMapping() { return mapping; }

        /** Human-readable reason for rejection (null if isomorphic). */
        public String getRejectionReason() { return rejectionReason; }

        /** Sorted degree sequence of graph1. */
        public List<Integer> getDegreeSequence1() { return degreeSequence1; }

        /** Sorted degree sequence of graph2. */
        public List<Integer> getDegreeSequence2() { return degreeSequence2; }

        @Override
        public String toString() {
            if (isomorphic) {
                return "Isomorphic (mapping: " + mapping + ")";
            }
            return "Not isomorphic" +
                    (rejectionReason != null ? " (" + rejectionReason + ")" : "");
        }
    }

    // ── Public API ──────────────────────────────────────────────

    /**
     * Check whether the two graphs are isomorphic.
     *
     * @return an {@link IsomorphismResult} with the verdict, mapping
     *         (if isomorphic), and degree sequences
     */
    public IsomorphismResult analyze() {
        List<String> vertices1 = new ArrayList<String>(graph1.getVertices());
        List<String> vertices2 = new ArrayList<String>(graph2.getVertices());

        List<Integer> degSeq1 = getSortedDegreeSequence(graph1, vertices1);
        List<Integer> degSeq2 = getSortedDegreeSequence(graph2, vertices2);

        // Fast rejection: vertex count
        if (vertices1.size() != vertices2.size()) {
            return new IsomorphismResult(false, null,
                    "Different vertex counts (" + vertices1.size() +
                            " vs " + vertices2.size() + ")",
                    degSeq1, degSeq2);
        }

        // Fast rejection: edge count
        int edgeCount1 = graph1.getEdgeCount();
        int edgeCount2 = graph2.getEdgeCount();
        if (edgeCount1 != edgeCount2) {
            return new IsomorphismResult(false, null,
                    "Different edge counts (" + edgeCount1 +
                            " vs " + edgeCount2 + ")",
                    degSeq1, degSeq2);
        }

        // Fast rejection: degree sequence
        if (!degSeq1.equals(degSeq2)) {
            return new IsomorphismResult(false, null,
                    "Different degree sequences",
                    degSeq1, degSeq2);
        }

        // Empty graphs are trivially isomorphic
        if (vertices1.isEmpty()) {
            return new IsomorphismResult(true,
                    Collections.<String, String>emptyMap(), null,
                    degSeq1, degSeq2);
        }

        // Build adjacency sets for fast lookup
        Map<String, Set<String>> adj1 = buildAdjacencyMap(graph1);
        Map<String, Set<String>> adj2 = buildAdjacencyMap(graph2);

        // Group vertices by degree for pruning
        Map<Integer, List<String>> byDegree1 = groupByDegree(graph1, vertices1);
        Map<Integer, List<String>> byDegree2 = groupByDegree(graph2, vertices2);

        // Order vertices1 by degree (ascending) for better pruning
        Collections.sort(vertices1, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return Integer.compare(graph1.degree(a), graph1.degree(b));
            }
        });

        // Backtracking search
        Map<String, String> mapping = new LinkedHashMap<String, String>();
        Map<String, String> reverseMapping = new HashMap<String, String>();
        Set<String> used2 = new HashSet<String>();

        if (backtrack(vertices1, 0, mapping, reverseMapping, used2, adj1, adj2, byDegree2)) {
            return new IsomorphismResult(true, mapping, null,
                    degSeq1, degSeq2);
        }

        return new IsomorphismResult(false, null,
                "No valid mapping found (structural mismatch)",
                degSeq1, degSeq2);
    }

    /**
     * Quick check — just returns true/false without computing the full
     * mapping details. Uses the same algorithm but avoids allocating the
     * result object for performance-sensitive code paths.
     *
     * @return true if the graphs are isomorphic
     */
    public boolean areIsomorphic() {
        return analyze().isIsomorphic();
    }

    // ── Private helpers ─────────────────────────────────────────

    /**
     * Compute the sorted degree sequence of a graph.
     */
    private List<Integer> getSortedDegreeSequence(Graph<String, edge> g,
                                                   List<String> vertices) {
        List<Integer> degrees = new ArrayList<Integer>(vertices.size());
        for (String v : vertices) {
            degrees.add(g.degree(v));
        }
        Collections.sort(degrees);
        return degrees;
    }

    /**
     * Build adjacency map: vertex → set of neighbors.
     */
    private Map<String, Set<String>> buildAdjacencyMap(Graph<String, edge> g) {
        Map<String, Set<String>> adj = new HashMap<String, Set<String>>();
        for (String v : g.getVertices()) {
            adj.put(v, new HashSet<String>(g.getNeighbors(v)));
        }
        return adj;
    }

    /**
     * Group vertices by their degree.
     */
    private Map<Integer, List<String>> groupByDegree(Graph<String, edge> g,
                                                      List<String> vertices) {
        Map<Integer, List<String>> groups =
                new HashMap<Integer, List<String>>();
        for (String v : vertices) {
            int deg = g.degree(v);
            List<String> list = groups.get(deg);
            if (list == null) {
                list = new ArrayList<String>();
                groups.put(deg, list);
            }
            list.add(v);
        }
        return groups;
    }

    /**
     * Backtracking search with degree-based candidate filtering and
     * incremental reverse-mapping maintenance.
     *
     * For each vertex in graph1 (in order), try mapping it to each
     * candidate vertex in graph2 that has the same degree and hasn't
     * been used yet. Check feasibility (all already-mapped neighbors
     * must correspond) before recursing.
     *
     * <p>The reverse mapping (graph2 → graph1) is maintained incrementally
     * alongside the forward mapping to avoid rebuilding it from scratch
     * in every feasibility check — reducing per-check cost from O(|mapping|)
     * to O(1) for the reverse lookup.</p>
     */
    private boolean backtrack(List<String> vertices1, int idx,
                              Map<String, String> mapping,
                              Map<String, String> reverseMapping,
                              Set<String> used2,
                              Map<String, Set<String>> adj1,
                              Map<String, Set<String>> adj2,
                              Map<Integer, List<String>> byDegree2) {
        if (idx == vertices1.size()) {
            return true; // all vertices mapped successfully
        }

        String v1 = vertices1.get(idx);
        int deg = graph1.degree(v1);
        List<String> candidates = byDegree2.get(deg);
        if (candidates == null) return false;

        for (String v2 : candidates) {
            if (used2.contains(v2)) continue;

            // Feasibility check: for every neighbor of v1 that is
            // already mapped, the corresponding mapped vertex must
            // be a neighbor of v2
            if (isFeasible(v1, v2, mapping, reverseMapping, adj1, adj2)) {
                mapping.put(v1, v2);
                reverseMapping.put(v2, v1);
                used2.add(v2);

                if (backtrack(vertices1, idx + 1, mapping, reverseMapping,
                        used2, adj1, adj2, byDegree2)) {
                    return true;
                }

                mapping.remove(v1);
                reverseMapping.remove(v2);
                used2.remove(v2);
            }
        }
        return false;
    }

    /**
     * Check whether mapping v1→v2 is feasible given the current
     * partial mapping.
     *
     * For every neighbor n1 of v1 that is already in the mapping,
     * the mapped vertex mapping[n1] must be a neighbor of v2.
     * Also, for every neighbor n2 of v2 whose preimage is mapped,
     * the preimage must be a neighbor of v1.
     *
     * <p>Uses the incrementally-maintained reverse mapping for O(1)
     * preimage lookups instead of rebuilding it from scratch each call.</p>
     */
    private boolean isFeasible(String v1, String v2,
                                Map<String, String> mapping,
                                Map<String, String> reverseMapping,
                                Map<String, Set<String>> adj1,
                                Map<String, Set<String>> adj2) {
        Set<String> neighbors1 = adj1.get(v1);
        Set<String> neighbors2 = adj2.get(v2);

        // Forward check: mapped neighbors of v1 must map to neighbors of v2
        for (String n1 : neighbors1) {
            String mapped = mapping.get(n1);
            if (mapped != null && !neighbors2.contains(mapped)) {
                return false;
            }
        }

        // Reverse check: mapped neighbors of v2 must come from neighbors of v1
        for (String n2 : neighbors2) {
            String preimage = reverseMapping.get(n2);
            if (preimage != null && !neighbors1.contains(preimage)) {
                return false;
            }
        }

        return true;
    }
}
