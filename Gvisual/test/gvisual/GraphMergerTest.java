package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphMerger} covering all merge strategies and
 * edge conflict resolution modes.
 *
 * @author zalenix
 */
public class GraphMergerTest {

    private Graph<String, Edge> graphA;
    private Graph<String, Edge> graphB;

    @Before
    public void setUp() {
        // Graph A: A--B--C (weights 1.0)
        graphA = new UndirectedSparseGraph<>();
        graphA.addVertex("A");
        graphA.addVertex("B");
        graphA.addVertex("C");
        Edge ab = new Edge("undirected", "A", "B");
        ab.setWeight(1.0f);
        Edge bc = new Edge("undirected", "B", "C");
        bc.setWeight(1.0f);
        graphA.addEdge(ab, "A", "B");
        graphA.addEdge(bc, "B", "C");

        // Graph B: B--C--D (weights 3.0 for B-C, 2.0 for C-D)
        graphB = new UndirectedSparseGraph<>();
        graphB.addVertex("B");
        graphB.addVertex("C");
        graphB.addVertex("D");
        Edge bc2 = new Edge("undirected", "B", "C");
        bc2.setWeight(3.0f);
        Edge cd = new Edge("undirected", "C", "D");
        cd.setWeight(2.0f);
        graphB.addEdge(bc2, "B", "C");
        graphB.addEdge(cd, "C", "D");
    }

    // ── UNION ─────────────────────────────────────────────────────

    @Test
    public void testUnionMerge() {
        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB,
                GraphMerger.Strategy.UNION, GraphMerger.EdgeConflict.KEEP_LEFT);

        assertEquals(4, result.getMergedVertexCount()); // A, B, C, D
        assertEquals(3, result.getMergedEdgeCount());   // A-B, B-C, C-D
        assertEquals(1, result.getConflictsResolved()); // B-C conflict

        Graph<String, Edge> merged = result.getMergedGraph();
        assertTrue(merged.containsVertex("A"));
        assertTrue(merged.containsVertex("D"));
    }

    @Test
    public void testUnionKeepLeftWeight() {
        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB,
                GraphMerger.Strategy.UNION, GraphMerger.EdgeConflict.KEEP_LEFT);

        Edge bcEdge = result.getMergedGraph().findEdge("B", "C");
        assertNotNull(bcEdge);
        assertEquals(1.0f, bcEdge.getWeight(), 0.001f); // kept left (1.0)
    }

    @Test
    public void testUnionKeepRightWeight() {
        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB,
                GraphMerger.Strategy.UNION, GraphMerger.EdgeConflict.KEEP_RIGHT);

        Edge bcEdge = result.getMergedGraph().findEdge("B", "C");
        assertNotNull(bcEdge);
        assertEquals(3.0f, bcEdge.getWeight(), 0.001f);
    }

    @Test
    public void testUnionSumWeight() {
        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB,
                GraphMerger.Strategy.UNION, GraphMerger.EdgeConflict.SUM);

        Edge bcEdge = result.getMergedGraph().findEdge("B", "C");
        assertNotNull(bcEdge);
        assertEquals(4.0f, bcEdge.getWeight(), 0.001f); // 1+3
    }

    @Test
    public void testUnionAverageWeight() {
        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB,
                GraphMerger.Strategy.UNION, GraphMerger.EdgeConflict.AVERAGE);

        Edge bcEdge = result.getMergedGraph().findEdge("B", "C");
        assertNotNull(bcEdge);
        assertEquals(2.0f, bcEdge.getWeight(), 0.001f); // (1+3)/2
    }

    @Test
    public void testUnionMaxWeight() {
        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB,
                GraphMerger.Strategy.UNION, GraphMerger.EdgeConflict.MAX);

        Edge bcEdge = result.getMergedGraph().findEdge("B", "C");
        assertNotNull(bcEdge);
        assertEquals(3.0f, bcEdge.getWeight(), 0.001f);
    }

    @Test
    public void testUnionMinWeight() {
        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB,
                GraphMerger.Strategy.UNION, GraphMerger.EdgeConflict.MIN);

        Edge bcEdge = result.getMergedGraph().findEdge("B", "C");
        assertNotNull(bcEdge);
        assertEquals(1.0f, bcEdge.getWeight(), 0.001f);
    }

    // ── INTERSECTION ──────────────────────────────────────────────

    @Test
    public void testIntersectionMerge() {
        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB,
                GraphMerger.Strategy.INTERSECTION, GraphMerger.EdgeConflict.KEEP_LEFT);

        // Shared vertices: B, C. Shared edge: B-C
        assertEquals(2, result.getMergedVertexCount());
        assertEquals(1, result.getMergedEdgeCount());

        Graph<String, Edge> merged = result.getMergedGraph();
        assertTrue(merged.containsVertex("B"));
        assertTrue(merged.containsVertex("C"));
        assertFalse(merged.containsVertex("A"));
        assertFalse(merged.containsVertex("D"));
    }

    // ── SYMMETRIC_DIFFERENCE ──────────────────────────────────────

    @Test
    public void testSymmetricDifferenceMerge() {
        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB,
                GraphMerger.Strategy.SYMMETRIC_DIFFERENCE, GraphMerger.EdgeConflict.KEEP_LEFT);

        // Edges unique to A: A-B. Edges unique to B: C-D.
        // B-C is in both → excluded.
        assertEquals(2, result.getMergedEdgeCount()); // A-B, C-D

        Graph<String, Edge> merged = result.getMergedGraph();
        assertTrue(merged.containsVertex("A"));
        assertTrue(merged.containsVertex("D"));
    }

    // ── LEFT_JOIN ─────────────────────────────────────────────────

    @Test
    public void testLeftJoinMerge() {
        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB,
                GraphMerger.Strategy.LEFT_JOIN, GraphMerger.EdgeConflict.KEEP_LEFT);

        // All A vertices (A, B, C). B's edges connecting A's vertices: B-C (conflict).
        // C-D skipped because D not in A.
        assertEquals(3, result.getMergedVertexCount());
        assertEquals(2, result.getMergedEdgeCount()); // A-B, B-C
        assertFalse(result.getMergedGraph().containsVertex("D"));
    }

    // ── RIGHT_JOIN ────────────────────────────────────────────────

    @Test
    public void testRightJoinMerge() {
        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB,
                GraphMerger.Strategy.RIGHT_JOIN, GraphMerger.EdgeConflict.KEEP_LEFT);

        // All B vertices (B, C, D). A's edges connecting B's vertices: B-C.
        // A-B skipped because A not in B.
        assertEquals(3, result.getMergedVertexCount());
        assertEquals(2, result.getMergedEdgeCount()); // B-C, C-D
        assertFalse(result.getMergedGraph().containsVertex("A"));
    }

    @Test
    public void testRightJoinStatisticsConsistency() {
        // Regression: RIGHT_JOIN used to incorrectly swap onlyA/onlyB,
        // producing MergeResult where onlyInA/onlyInB were inconsistent
        // with vertexCountA/vertexCountB.
        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB,
                GraphMerger.Strategy.RIGHT_JOIN, GraphMerger.EdgeConflict.KEEP_LEFT);

        // onlyInA should contain vertices in A but not B → {A}
        assertTrue(result.getOnlyInA().contains("A"));
        assertFalse(result.getOnlyInA().contains("D"));

        // onlyInB should contain vertices in B but not A → {D}
        assertTrue(result.getOnlyInB().contains("D"));
        assertFalse(result.getOnlyInB().contains("A"));

        // vertexCountA/B should match original graph sizes
        assertEquals(3, result.getVertexCountA()); // A, B, C
        assertEquals(3, result.getVertexCountB()); // B, C, D
    }

    // ── Statistics ─────────────────────────────────────────────────

    @Test
    public void testVertexOverlap() {
        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB);

        // Shared: {B, C}, Union: {A, B, C, D} → 2/4 = 0.5
        assertEquals(0.5, result.getVertexOverlap(), 0.001);
    }

    @Test
    public void testSharedVertices() {
        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphB);

        Set<String> shared = result.getSharedVertices();
        assertEquals(2, shared.size());
        assertTrue(shared.contains("B"));
        assertTrue(shared.contains("C"));
    }

    // ── Edge cases ────────────────────────────────────────────────

    @Test
    public void testMergeEmptyGraphs() {
        Graph<String, Edge> empty1 = new UndirectedSparseGraph<>();
        Graph<String, Edge> empty2 = new UndirectedSparseGraph<>();

        GraphMerger.MergeResult result = GraphMerger.merge(empty1, empty2);
        assertEquals(0, result.getMergedVertexCount());
        assertEquals(0, result.getMergedEdgeCount());
        assertEquals(1.0, result.getVertexOverlap(), 0.001); // 0/0 → 0.0 actually
    }

    @Test
    public void testMergeIdenticalGraphs() {
        GraphMerger.MergeResult result = GraphMerger.merge(graphA, graphA,
                GraphMerger.Strategy.UNION, GraphMerger.EdgeConflict.KEEP_LEFT);

        assertEquals(graphA.getVertexCount(), result.getMergedVertexCount());
        assertEquals(graphA.getEdgeCount(), result.getMergedEdgeCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphAThrows() {
        GraphMerger.merge(null, graphB);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphBThrows() {
        GraphMerger.merge(graphA, null);
    }

    // ── resolveWeight ─────────────────────────────────────────────

    @Test
    public void testResolveWeightAllModes() {
        assertEquals(2.0f, GraphMerger.resolveWeight(2.0f, 5.0f, GraphMerger.EdgeConflict.KEEP_LEFT), 0.001f);
        assertEquals(5.0f, GraphMerger.resolveWeight(2.0f, 5.0f, GraphMerger.EdgeConflict.KEEP_RIGHT), 0.001f);
        assertEquals(5.0f, GraphMerger.resolveWeight(2.0f, 5.0f, GraphMerger.EdgeConflict.MAX), 0.001f);
        assertEquals(2.0f, GraphMerger.resolveWeight(2.0f, 5.0f, GraphMerger.EdgeConflict.MIN), 0.001f);
        assertEquals(7.0f, GraphMerger.resolveWeight(2.0f, 5.0f, GraphMerger.EdgeConflict.SUM), 0.001f);
        assertEquals(3.5f, GraphMerger.resolveWeight(2.0f, 5.0f, GraphMerger.EdgeConflict.AVERAGE), 0.001f);
    }
}
