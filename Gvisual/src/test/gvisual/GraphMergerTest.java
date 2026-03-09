package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphMerger}.
 */
public class GraphMergerTest {

    private Graph<String, edge> graphA;
    private Graph<String, edge> graphB;

    @Before
    public void setUp() {
        graphA = new UndirectedSparseGraph<>();
        graphB = new UndirectedSparseGraph<>();
    }

    private edge makeEdge(String v1, String v2, float weight) {
        edge e = new edge("f", v1, v2);
        e.setWeight(weight);
        return e;
    }

    // ── Union ───────────────────────────────────────────────────────

    @Test
    public void testUnionDisjoint() {
        graphA.addVertex("A"); graphA.addVertex("B");
        graphA.addEdge(makeEdge("A", "B", 1.0f), "A", "B");

        graphB.addVertex("C"); graphB.addVertex("D");
        graphB.addEdge(makeEdge("C", "D", 2.0f), "C", "D");

        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB,
                GraphMerger.Strategy.UNION, GraphMerger.EdgeConflict.KEEP_LEFT);

        assertEquals(4, result.getMergedVertexCount());
        assertEquals(2, result.getMergedEdgeCount());
        assertEquals(0, result.getConflictsResolved());
    }

    @Test
    public void testUnionOverlapping() {
        graphA.addVertex("A"); graphA.addVertex("B"); graphA.addVertex("C");
        graphA.addEdge(makeEdge("A", "B", 1.0f), "A", "B");

        graphB.addVertex("B"); graphB.addVertex("C"); graphB.addVertex("D");
        graphB.addEdge(makeEdge("B", "C", 2.0f), "B", "C");

        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB);
        assertEquals(4, result.getMergedVertexCount());
        assertEquals(2, result.getMergedEdgeCount());
        assertTrue(result.getSharedVertices().contains("B"));
        assertTrue(result.getSharedVertices().contains("C"));
    }

    @Test
    public void testUnionConflictAverage() {
        graphA.addVertex("A"); graphA.addVertex("B");
        graphA.addEdge(makeEdge("A", "B", 4.0f), "A", "B");

        graphB.addVertex("A"); graphB.addVertex("B");
        graphB.addEdge(makeEdge("A", "B", 6.0f), "A", "B");

        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB,
                GraphMerger.Strategy.UNION, GraphMerger.EdgeConflict.AVERAGE);

        assertEquals(2, result.getMergedVertexCount());
        assertEquals(1, result.getMergedEdgeCount());
        assertEquals(1, result.getConflictsResolved());

        edge merged = result.getMergedGraph().getEdges().iterator().next();
        assertEquals(5.0f, merged.getWeight(), 0.001f);
    }

    @Test
    public void testUnionConflictMax() {
        graphA.addVertex("X"); graphA.addVertex("Y");
        graphA.addEdge(makeEdge("X", "Y", 3.0f), "X", "Y");

        graphB.addVertex("X"); graphB.addVertex("Y");
        graphB.addEdge(makeEdge("X", "Y", 7.0f), "X", "Y");

        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB,
                GraphMerger.Strategy.UNION, GraphMerger.EdgeConflict.MAX);

        edge merged = result.getMergedGraph().getEdges().iterator().next();
        assertEquals(7.0f, merged.getWeight(), 0.001f);
    }

    @Test
    public void testUnionConflictSum() {
        graphA.addVertex("A"); graphA.addVertex("B");
        graphA.addEdge(makeEdge("A", "B", 3.0f), "A", "B");

        graphB.addVertex("A"); graphB.addVertex("B");
        graphB.addEdge(makeEdge("A", "B", 5.0f), "A", "B");

        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB,
                GraphMerger.Strategy.UNION, GraphMerger.EdgeConflict.SUM);

        edge merged = result.getMergedGraph().getEdges().iterator().next();
        assertEquals(8.0f, merged.getWeight(), 0.001f);
    }

    // ── Intersection ────────────────────────────────────────────────

    @Test
    public void testIntersectionDisjoint() {
        graphA.addVertex("A"); graphA.addVertex("B");
        graphA.addEdge(makeEdge("A", "B", 1.0f), "A", "B");

        graphB.addVertex("C"); graphB.addVertex("D");
        graphB.addEdge(makeEdge("C", "D", 2.0f), "C", "D");

        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB,
                GraphMerger.Strategy.INTERSECTION, GraphMerger.EdgeConflict.KEEP_LEFT);

        assertEquals(0, result.getMergedVertexCount());
        assertEquals(0, result.getMergedEdgeCount());
    }

    @Test
    public void testIntersectionSharedEdge() {
        graphA.addVertex("A"); graphA.addVertex("B"); graphA.addVertex("C");
        graphA.addEdge(makeEdge("A", "B", 1.0f), "A", "B");
        graphA.addEdge(makeEdge("A", "C", 3.0f), "A", "C");

        graphB.addVertex("A"); graphB.addVertex("B"); graphB.addVertex("D");
        graphB.addEdge(makeEdge("A", "B", 2.0f), "A", "B");
        graphB.addEdge(makeEdge("A", "D", 4.0f), "A", "D");

        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB,
                GraphMerger.Strategy.INTERSECTION, GraphMerger.EdgeConflict.KEEP_LEFT);

        assertEquals(2, result.getMergedVertexCount()); // A, B
        assertEquals(1, result.getMergedEdgeCount());   // A-B
    }

    // ── Symmetric Difference ────────────────────────────────────────

    @Test
    public void testSymmetricDifference() {
        graphA.addVertex("A"); graphA.addVertex("B"); graphA.addVertex("C");
        graphA.addEdge(makeEdge("A", "B", 1.0f), "A", "B");
        graphA.addEdge(makeEdge("B", "C", 2.0f), "B", "C");

        graphB.addVertex("B"); graphB.addVertex("C"); graphB.addVertex("D");
        graphB.addEdge(makeEdge("B", "C", 3.0f), "B", "C");  // Same edge as A
        graphB.addEdge(makeEdge("C", "D", 4.0f), "C", "D");

        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB,
                GraphMerger.Strategy.SYMMETRIC_DIFFERENCE, GraphMerger.EdgeConflict.KEEP_LEFT);

        // B-C is in both → excluded. A-B only in A, C-D only in B
        assertEquals(2, result.getMergedEdgeCount());
    }

    // ── Left Join ───────────────────────────────────────────────────

    @Test
    public void testLeftJoin() {
        graphA.addVertex("A"); graphA.addVertex("B"); graphA.addVertex("C");
        graphA.addEdge(makeEdge("A", "B", 1.0f), "A", "B");

        graphB.addVertex("A"); graphB.addVertex("B"); graphB.addVertex("D");
        graphB.addEdge(makeEdge("A", "B", 5.0f), "A", "B");
        graphB.addEdge(makeEdge("B", "D", 3.0f), "B", "D");

        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB,
                GraphMerger.Strategy.LEFT_JOIN, GraphMerger.EdgeConflict.KEEP_LEFT);

        // A, B, C from A. B-D from B connects B (in A) but D not in A → excluded
        assertEquals(3, result.getMergedVertexCount());
        assertEquals(1, result.getMergedEdgeCount()); // A-B only
    }

    // ── Empty Graphs ────────────────────────────────────────────────

    @Test
    public void testBothEmpty() {
        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB);
        assertEquals(0, result.getMergedVertexCount());
        assertEquals(0, result.getMergedEdgeCount());
        assertEquals(0.0, result.getVertexOverlap(), 0.001);
    }

    @Test
    public void testOneEmpty() {
        graphA.addVertex("A"); graphA.addVertex("B");
        graphA.addEdge(makeEdge("A", "B", 1.0f), "A", "B");

        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB);
        assertEquals(2, result.getMergedVertexCount());
        assertEquals(1, result.getMergedEdgeCount());
    }

    // ── Null Checks ─────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphA() {
        GraphMerger.merge(null, graphB);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphB() {
        GraphMerger.merge(graphA, null);
    }

    // ── Statistics ──────────────────────────────────────────────────

    @Test
    public void testVertexOverlap() {
        graphA.addVertex("A"); graphA.addVertex("B"); graphA.addVertex("C");
        graphB.addVertex("B"); graphB.addVertex("C"); graphB.addVertex("D");

        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB);
        // Shared: B, C. Only A: A. Only B: D. Union = 4
        assertEquals(0.5, result.getVertexOverlap(), 0.001);
    }

    @Test
    public void testSummaryNotEmpty() {
        graphA.addVertex("A"); graphA.addVertex("B");
        graphA.addEdge(makeEdge("A", "B", 1.0f), "A", "B");

        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB);
        String summary = result.getSummary();
        assertTrue(summary.contains("UNION"));
        assertTrue(summary.contains("Graph A:"));
    }

    // ── Edge Conflict Resolution ────────────────────────────────────

    @Test
    public void testResolveWeightKeepLeft() {
        assertEquals(3.0f, GraphMerger.resolveWeight(3.0f, 7.0f,
                GraphMerger.EdgeConflict.KEEP_LEFT), 0.001f);
    }

    @Test
    public void testResolveWeightKeepRight() {
        assertEquals(7.0f, GraphMerger.resolveWeight(3.0f, 7.0f,
                GraphMerger.EdgeConflict.KEEP_RIGHT), 0.001f);
    }

    @Test
    public void testResolveWeightMin() {
        assertEquals(3.0f, GraphMerger.resolveWeight(3.0f, 7.0f,
                GraphMerger.EdgeConflict.MIN), 0.001f);
    }
}
