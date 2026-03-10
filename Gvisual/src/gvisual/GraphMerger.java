package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;

import java.util.*;

/**
 * Merges two graphs using configurable strategies for combining vertices
 * and edges, with conflict resolution for overlapping edge attributes.
 *
 * <h3>Merge Strategies</h3>
 * <ul>
 *   <li><b>UNION</b> — all vertices and edges from both graphs (default)</li>
 *   <li><b>INTERSECTION</b> — only vertices/edges present in both graphs</li>
 *   <li><b>SYMMETRIC_DIFFERENCE</b> — vertices/edges in exactly one graph</li>
 *   <li><b>LEFT_JOIN</b> — all of graph A, plus edges from B that connect A's vertices</li>
 *   <li><b>RIGHT_JOIN</b> — mirror of LEFT_JOIN</li>
 * </ul>
 *
 * <h3>Edge Conflict Resolution</h3>
 * When the same edge pair exists in both graphs with different weights:
 * <ul>
 *   <li><b>KEEP_LEFT</b> — use weight from graph A</li>
 *   <li><b>KEEP_RIGHT</b> — use weight from graph B</li>
 *   <li><b>MAX</b> — use the larger weight</li>
 *   <li><b>MIN</b> — use the smaller weight</li>
 *   <li><b>SUM</b> — add both weights</li>
 *   <li><b>AVERAGE</b> — average both weights</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 * MergeResult result = GraphMerger.merge(graphA, graphB, Strategy.UNION,
 *     EdgeConflict.AVERAGE);
 * Graph&lt;String, edge&gt; merged = result.getMergedGraph();
 * </pre>
 *
 * @author zalenix
 */
public final class GraphMerger {

    private GraphMerger() { /* utility class */ }

    /** Merge strategy for combining two graphs. */
    public enum Strategy {
        /** All vertices and edges from both graphs. */
        UNION,
        /** Only vertices and edges present in both graphs. */
        INTERSECTION,
        /** Vertices and edges in exactly one graph (XOR). */
        SYMMETRIC_DIFFERENCE,
        /** All of graph A, plus B's edges that connect A's vertices. */
        LEFT_JOIN,
        /** All of graph B, plus A's edges that connect B's vertices. */
        RIGHT_JOIN
    }

    /** Edge conflict resolution when the same edge pair exists in both graphs. */
    public enum EdgeConflict {
        /** Keep weight from graph A. */
        KEEP_LEFT,
        /** Keep weight from graph B. */
        KEEP_RIGHT,
        /** Use the maximum weight. */
        MAX,
        /** Use the minimum weight. */
        MIN,
        /** Sum both weights. */
        SUM,
        /** Average both weights. */
        AVERAGE
    }

    /**
     * Result of a merge operation, including the merged graph and statistics.
     */
    public static final class MergeResult {
        private final Graph<String, edge> mergedGraph;
        private final Strategy strategy;
        private final EdgeConflict edgeConflict;
        private final int vertexCountA;
        private final int vertexCountB;
        private final int edgeCountA;
        private final int edgeCountB;
        private final int mergedVertexCount;
        private final int mergedEdgeCount;
        private final int conflictsResolved;
        private final Set<String> sharedVertices;
        private final Set<String> onlyInA;
        private final Set<String> onlyInB;

        MergeResult(Graph<String, edge> mergedGraph, Strategy strategy,
                    EdgeConflict edgeConflict,
                    int vertexCountA, int vertexCountB,
                    int edgeCountA, int edgeCountB,
                    int conflictsResolved,
                    Set<String> sharedVertices,
                    Set<String> onlyInA, Set<String> onlyInB) {
            this.mergedGraph = mergedGraph;
            this.strategy = strategy;
            this.edgeConflict = edgeConflict;
            this.vertexCountA = vertexCountA;
            this.vertexCountB = vertexCountB;
            this.edgeCountA = edgeCountA;
            this.edgeCountB = edgeCountB;
            this.mergedVertexCount = mergedGraph.getVertexCount();
            this.mergedEdgeCount = mergedGraph.getEdgeCount();
            this.conflictsResolved = conflictsResolved;
            this.sharedVertices = Collections.unmodifiableSet(sharedVertices);
            this.onlyInA = Collections.unmodifiableSet(onlyInA);
            this.onlyInB = Collections.unmodifiableSet(onlyInB);
        }

        public Graph<String, edge> getMergedGraph() { return mergedGraph; }
        public Strategy getStrategy() { return strategy; }
        public EdgeConflict getEdgeConflict() { return edgeConflict; }
        public int getVertexCountA() { return vertexCountA; }
        public int getVertexCountB() { return vertexCountB; }
        public int getEdgeCountA() { return edgeCountA; }
        public int getEdgeCountB() { return edgeCountB; }
        public int getMergedVertexCount() { return mergedVertexCount; }
        public int getMergedEdgeCount() { return mergedEdgeCount; }
        public int getConflictsResolved() { return conflictsResolved; }
        public Set<String> getSharedVertices() { return sharedVertices; }
        public Set<String> getOnlyInA() { return onlyInA; }
        public Set<String> getOnlyInB() { return onlyInB; }

        /** Jaccard similarity of vertex sets: |A ∩ B| / |A ∪ B|. */
        public double getVertexOverlap() {
            int union = sharedVertices.size() + onlyInA.size() + onlyInB.size();
            return union > 0 ? (double) sharedVertices.size() / union : 0.0;
        }

        /** Generate a human-readable summary. */
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Graph Merge Summary ===\n");
            sb.append(String.format("Strategy: %s | Edge conflict: %s%n", strategy, edgeConflict));
            sb.append(String.format("Graph A: %d vertices, %d edges%n", vertexCountA, edgeCountA));
            sb.append(String.format("Graph B: %d vertices, %d edges%n", vertexCountB, edgeCountB));
            sb.append(String.format("Merged:  %d vertices, %d edges%n", mergedVertexCount, mergedEdgeCount));
            sb.append(String.format("Shared vertices: %d (%.1f%% overlap)%n",
                    sharedVertices.size(), getVertexOverlap() * 100));
            sb.append(String.format("Only in A: %d | Only in B: %d%n", onlyInA.size(), onlyInB.size()));
            if (conflictsResolved > 0) {
                sb.append(String.format("Edge conflicts resolved: %d%n", conflictsResolved));
            }
            return sb.toString();
        }
    }

    // ── Public API ─────────────────────────────────────────────────

    /**
     * Merge two graphs with default settings (UNION + KEEP_LEFT).
     */
    public static MergeResult merge(Graph<String, edge> a, Graph<String, edge> b) {
        return merge(a, b, Strategy.UNION, EdgeConflict.KEEP_LEFT);
    }

    /**
     * Merge two graphs with the given strategy and edge conflict resolution.
     *
     * @param a            first graph
     * @param b            second graph
     * @param strategy     merge strategy
     * @param edgeConflict how to resolve conflicting edge weights
     * @return merge result with the combined graph and statistics
     * @throws IllegalArgumentException if any argument is null
     */
    public static MergeResult merge(Graph<String, edge> a, Graph<String, edge> b,
                                    Strategy strategy, EdgeConflict edgeConflict) {
        if (a == null || b == null) throw new IllegalArgumentException("Graphs must not be null");
        if (strategy == null) throw new IllegalArgumentException("Strategy must not be null");
        if (edgeConflict == null) throw new IllegalArgumentException("EdgeConflict must not be null");

        Set<String> vertsA = new HashSet<>(a.getVertices());
        Set<String> vertsB = new HashSet<>(b.getVertices());

        Set<String> shared = new HashSet<>(vertsA);
        shared.retainAll(vertsB);

        Set<String> onlyA = new HashSet<>(vertsA);
        onlyA.removeAll(vertsB);

        Set<String> onlyB = new HashSet<>(vertsB);
        onlyB.removeAll(vertsA);

        Graph<String, edge> merged = new UndirectedSparseGraph<>();
        int[] conflicts = {0};

        switch (strategy) {
            case UNION:
                mergeUnion(a, b, merged, edgeConflict, conflicts);
                break;
            case INTERSECTION:
                mergeIntersection(a, b, merged, shared, edgeConflict, conflicts);
                break;
            case SYMMETRIC_DIFFERENCE:
                mergeSymmetricDifference(a, b, merged, shared);
                break;
            case LEFT_JOIN:
                mergeLeftJoin(a, b, merged, edgeConflict, conflicts);
                break;
            case RIGHT_JOIN:
                mergeLeftJoin(b, a, merged, edgeConflict, conflicts);
                break;
        }

        return new MergeResult(merged, strategy, edgeConflict,
                vertsA.size(), vertsB.size(),
                a.getEdgeCount(), b.getEdgeCount(),
                conflicts[0], shared, onlyA, onlyB);
    }

    // ── Strategy Implementations ───────────────────────────────────

    private static void mergeUnion(Graph<String, edge> a, Graph<String, edge> b,
                                   Graph<String, edge> result,
                                   EdgeConflict conflict, int[] conflicts) {
        Map<String, edge> edgeIndex = new HashMap<>();

        // Add all from A
        for (String v : a.getVertices()) result.addVertex(v);
        for (edge e : a.getEdges()) {
            String key = edgeKey(e);
            result.addEdge(cloneEdge(e), e.getVertex1(), e.getVertex2());
            edgeIndex.put(key, findEdge(result, e.getVertex1(), e.getVertex2()));
        }

        // Add vertices from B
        for (String v : b.getVertices()) {
            if (!result.containsVertex(v)) result.addVertex(v);
        }

        // Add/merge edges from B
        for (edge e : b.getEdges()) {
            String key = edgeKey(e);
            if (edgeIndex.containsKey(key)) {
                // Conflict — resolve weight
                edge existing = edgeIndex.get(key);
                existing.setWeight(resolveWeight(existing.getWeight(), e.getWeight(), conflict));
                conflicts[0]++;
            } else {
                result.addEdge(cloneEdge(e), e.getVertex1(), e.getVertex2());
            }
        }
    }

    private static void mergeIntersection(Graph<String, edge> a, Graph<String, edge> b,
                                          Graph<String, edge> result,
                                          Set<String> shared,
                                          EdgeConflict conflict, int[] conflicts) {
        // Add only shared vertices
        for (String v : shared) result.addVertex(v);

        // Build edge index for B
        Map<String, edge> bEdges = new HashMap<>();
        for (edge e : b.getEdges()) {
            bEdges.put(edgeKey(e), e);
        }

        // Add edges present in both graphs (between shared vertices)
        for (edge e : a.getEdges()) {
            if (!shared.contains(e.getVertex1()) || !shared.contains(e.getVertex2())) continue;

            String key = edgeKey(e);
            edge bEdge = bEdges.get(key);
            if (bEdge != null) {
                edge merged = cloneEdge(e);
                merged.setWeight(resolveWeight(e.getWeight(), bEdge.getWeight(), conflict));
                result.addEdge(merged, e.getVertex1(), e.getVertex2());
                if (e.getWeight() != bEdge.getWeight()) conflicts[0]++;
            }
        }
    }

    private static void mergeSymmetricDifference(Graph<String, edge> a, Graph<String, edge> b,
                                                  Graph<String, edge> result,
                                                  Set<String> shared) {
        // Build edge sets
        Set<String> aEdgeKeys = new HashSet<>();
        for (edge e : a.getEdges()) aEdgeKeys.add(edgeKey(e));

        Set<String> bEdgeKeys = new HashSet<>();
        for (edge e : b.getEdges()) bEdgeKeys.add(edgeKey(e));

        // Add vertices that appear in edges unique to one graph
        Set<String> addedVerts = new HashSet<>();

        // Edges only in A
        for (edge e : a.getEdges()) {
            if (!bEdgeKeys.contains(edgeKey(e))) {
                ensureVertex(result, e.getVertex1(), addedVerts);
                ensureVertex(result, e.getVertex2(), addedVerts);
                result.addEdge(cloneEdge(e), e.getVertex1(), e.getVertex2());
            }
        }

        // Edges only in B
        for (edge e : b.getEdges()) {
            if (!aEdgeKeys.contains(edgeKey(e))) {
                ensureVertex(result, e.getVertex1(), addedVerts);
                ensureVertex(result, e.getVertex2(), addedVerts);
                result.addEdge(cloneEdge(e), e.getVertex1(), e.getVertex2());
            }
        }

        // Vertices only in one graph (isolated — no unique edges)
        Set<String> vertsA = new HashSet<>(a.getVertices());
        vertsA.removeAll(new HashSet<>(b.getVertices()));
        for (String v : vertsA) ensureVertex(result, v, addedVerts);

        Set<String> vertsB = new HashSet<>(b.getVertices());
        vertsB.removeAll(new HashSet<>(a.getVertices()));
        for (String v : vertsB) ensureVertex(result, v, addedVerts);
    }

    private static void mergeLeftJoin(Graph<String, edge> primary, Graph<String, edge> secondary,
                                      Graph<String, edge> result,
                                      EdgeConflict conflict, int[] conflicts) {
        // Add all vertices and edges from primary
        for (String v : primary.getVertices()) result.addVertex(v);
        Map<String, edge> edgeIndex = new HashMap<>();
        for (edge e : primary.getEdges()) {
            result.addEdge(cloneEdge(e), e.getVertex1(), e.getVertex2());
            edgeIndex.put(edgeKey(e), findEdge(result, e.getVertex1(), e.getVertex2()));
        }

        // Add edges from secondary that connect primary's vertices
        Set<String> primaryVerts = new HashSet<>(primary.getVertices());
        for (edge e : secondary.getEdges()) {
            if (!primaryVerts.contains(e.getVertex1()) || !primaryVerts.contains(e.getVertex2())) {
                continue;
            }
            String key = edgeKey(e);
            if (edgeIndex.containsKey(key)) {
                edge existing = edgeIndex.get(key);
                existing.setWeight(resolveWeight(existing.getWeight(), e.getWeight(), conflict));
                conflicts[0]++;
            } else {
                result.addEdge(cloneEdge(e), e.getVertex1(), e.getVertex2());
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────

    /** Canonical key for an edge (alphabetically sorted endpoints). */
    private static String edgeKey(edge e) {
        String v1 = e.getVertex1();
        String v2 = e.getVertex2();
        return v1.compareTo(v2) <= 0 ? v1 + "|" + v2 : v2 + "|" + v1;
    }

    /** Find an edge between two vertices in a graph. */
    private static edge findEdge(Graph<String, edge> g, String v1, String v2) {
        edge e = g.findEdge(v1, v2);
        return e != null ? e : g.findEdge(v2, v1);
    }

    /** Clone an edge (deep copy). */
    private static edge cloneEdge(edge e) {
        edge clone = new edge(e.getType(), e.getVertex1(), e.getVertex2());
        clone.setWeight(e.getWeight());
        clone.setLabel(e.getLabel());
        clone.setTimestamp(e.getTimestamp());
        clone.setEndTimestamp(e.getEndTimestamp());
        return clone;
    }

    /** Add vertex to graph if not already present. */
    private static void ensureVertex(Graph<String, edge> g, String v, Set<String> tracker) {
        if (!tracker.contains(v)) {
            if (!g.containsVertex(v)) g.addVertex(v);
            tracker.add(v);
        }
    }

    /** Resolve edge weight conflict according to the chosen strategy. */
    static float resolveWeight(float left, float right, EdgeConflict conflict) {
        switch (conflict) {
            case KEEP_LEFT:  return left;
            case KEEP_RIGHT: return right;
            case MAX:        return Math.max(left, right);
            case MIN:        return Math.min(left, right);
            case SUM:        return left + right;
            case AVERAGE:    return (left + right) / 2.0f;
            default:         return left;
        }
    }
}
