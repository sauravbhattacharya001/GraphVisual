package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Comprehensive tests for {@link GraphDiffAnalyzer} — node/edge diffs,
 * Jaccard similarity, edit distance, degree changes, EdgeDiff normalization,
 * and DiffResult summary.
 */
public class GraphDiffAnalyzerTest {

    private Graph<String, Edge> graphA;
    private Graph<String, Edge> graphB;

    @Before
    public void setUp() {
        graphA = new UndirectedSparseGraph<>();
        graphB = new UndirectedSparseGraph<>();
    }

    private void addEdge(Graph<String, Edge> g, String v1, String v2) {
        if (!g.containsVertex(v1)) g.addVertex(v1);
        if (!g.containsVertex(v2)) g.addVertex(v2);
        Edge e = new Edge("f", v1, v2);
        g.addEdge(e, v1, v2);
    }

    // =========================================================================
    // Constructor validation
    // =========================================================================

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphA() {
        new GraphDiffAnalyzer(null, graphB);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphB() {
        new GraphDiffAnalyzer(graphA, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBothNull() {
        new GraphDiffAnalyzer(null, null);
    }

    // =========================================================================
    // Empty graphs
    // =========================================================================

    @Test
    public void testBothEmpty() {
        GraphDiffAnalyzer analyzer = new GraphDiffAnalyzer(graphA, graphB);
        GraphDiffAnalyzer.DiffResult result = analyzer.computeDiff();

        assertTrue(result.isIdentical());
        assertEquals(0, result.getAddedNodes().size());
        assertEquals(0, result.getRemovedNodes().size());
        assertEquals(0, result.getCommonNodes().size());
        assertEquals(0, result.getAddedEdges().size());
        assertEquals(0, result.getRemovedEdges().size());
        assertEquals(0, result.getCommonEdges().size());
        assertEquals(1.0, result.getNodeJaccard(), 0.001);
        assertEquals(1.0, result.getEdgeJaccard(), 0.001);
        assertEquals(0, result.getEditDistance());
        assertTrue(result.getDegreeChanges().isEmpty());
    }

    @Test
    public void testEmptyANonEmptyB() {
        addEdge(graphB, "X", "Y");
        GraphDiffAnalyzer analyzer = new GraphDiffAnalyzer(graphA, graphB);
        GraphDiffAnalyzer.DiffResult result = analyzer.computeDiff();

        assertFalse(result.isIdentical());
        assertEquals(2, result.getAddedNodes().size());
        assertTrue(result.getAddedNodes().contains("X"));
        assertTrue(result.getAddedNodes().contains("Y"));
        assertEquals(0, result.getRemovedNodes().size());
        assertEquals(1, result.getAddedEdges().size());
        assertEquals(0.0, result.getNodeJaccard(), 0.001);
        assertEquals(0.0, result.getEdgeJaccard(), 0.001);
    }

    @Test
    public void testNonEmptyAEmptyB() {
        addEdge(graphA, "X", "Y");
        GraphDiffAnalyzer analyzer = new GraphDiffAnalyzer(graphA, graphB);
        GraphDiffAnalyzer.DiffResult result = analyzer.computeDiff();

        assertFalse(result.isIdentical());
        assertEquals(0, result.getAddedNodes().size());
        assertEquals(2, result.getRemovedNodes().size());
        assertEquals(1, result.getRemovedEdges().size());
    }

    // =========================================================================
    // Identical graphs
    // =========================================================================

    @Test
    public void testIdenticalTriangle() {
        addEdge(graphA, "A", "B");
        addEdge(graphA, "B", "C");
        addEdge(graphA, "A", "C");
        addEdge(graphB, "A", "B");
        addEdge(graphB, "B", "C");
        addEdge(graphB, "A", "C");

        GraphDiffAnalyzer.DiffResult result =
                new GraphDiffAnalyzer(graphA, graphB).computeDiff();

        assertTrue(result.isIdentical());
        assertEquals(3, result.getCommonNodes().size());
        assertEquals(3, result.getCommonEdges().size());
        assertEquals(1.0, result.getNodeJaccard(), 0.001);
        assertEquals(1.0, result.getEdgeJaccard(), 0.001);
        assertEquals(0, result.getEditDistance());
    }

    @Test
    public void testIdenticalSingleEdge() {
        addEdge(graphA, "A", "B");
        addEdge(graphB, "A", "B");

        GraphDiffAnalyzer.DiffResult result =
                new GraphDiffAnalyzer(graphA, graphB).computeDiff();

        assertTrue(result.isIdentical());
        assertEquals(2, result.getCommonNodes().size());
        assertEquals(1, result.getCommonEdges().size());
    }

    @Test
    public void testIdenticalIsolatedVertices() {
        graphA.addVertex("X");
        graphA.addVertex("Y");
        graphB.addVertex("X");
        graphB.addVertex("Y");

        GraphDiffAnalyzer.DiffResult result =
                new GraphDiffAnalyzer(graphA, graphB).computeDiff();

        assertTrue(result.isIdentical());
        assertEquals(2, result.getCommonNodes().size());
        assertEquals(0, result.getCommonEdges().size());
    }

    // =========================================================================
    // Completely different graphs
    // =========================================================================

    @Test
    public void testDisjointGraphs() {
        addEdge(graphA, "A", "B");
        addEdge(graphB, "C", "D");

        GraphDiffAnalyzer.DiffResult result =
                new GraphDiffAnalyzer(graphA, graphB).computeDiff();

        assertFalse(result.isIdentical());
        assertEquals(2, result.getAddedNodes().size());
        assertEquals(2, result.getRemovedNodes().size());
        assertEquals(0, result.getCommonNodes().size());
        assertEquals(1, result.getAddedEdges().size());
        assertEquals(1, result.getRemovedEdges().size());
        assertEquals(0.0, result.getNodeJaccard(), 0.001);
        assertEquals(0.0, result.getEdgeJaccard(), 0.001);
        // edit distance = 2 added nodes + 2 removed nodes + 1 added edge + 1 removed edge = 6
        assertEquals(6, result.getEditDistance());
    }

    // =========================================================================
    // Partial overlap
    // =========================================================================

    @Test
    public void testAddedNodesAndEdges() {
        addEdge(graphA, "A", "B");
        addEdge(graphB, "A", "B");
        addEdge(graphB, "B", "C");

        GraphDiffAnalyzer.DiffResult result =
                new GraphDiffAnalyzer(graphA, graphB).computeDiff();

        assertEquals(1, result.getAddedNodes().size());
        assertTrue(result.getAddedNodes().contains("C"));
        assertTrue(result.getRemovedNodes().isEmpty());
        assertEquals(2, result.getCommonNodes().size());
        assertEquals(1, result.getAddedEdges().size());
        assertEquals(1, result.getCommonEdges().size());
    }

    @Test
    public void testRemovedNodesAndEdges() {
        addEdge(graphA, "A", "B");
        addEdge(graphA, "B", "C");
        addEdge(graphB, "A", "B");

        GraphDiffAnalyzer.DiffResult result =
                new GraphDiffAnalyzer(graphA, graphB).computeDiff();

        assertEquals(1, result.getRemovedNodes().size());
        assertTrue(result.getRemovedNodes().contains("C"));
        assertEquals(1, result.getRemovedEdges().size());
        assertEquals(1, result.getCommonEdges().size());
    }

    @Test
    public void testEdgeRewiring() {
        // A has A-B, B has A-C (same node A, different neighbor)
        addEdge(graphA, "A", "B");
        addEdge(graphB, "A", "C");

        GraphDiffAnalyzer.DiffResult result =
                new GraphDiffAnalyzer(graphA, graphB).computeDiff();

        assertEquals(1, result.getAddedNodes().size());   // C
        assertEquals(1, result.getRemovedNodes().size());  // B
        assertEquals(1, result.getCommonNodes().size());   // A
        assertEquals(1, result.getAddedEdges().size());    // A-C
        assertEquals(1, result.getRemovedEdges().size());  // A-B
        assertEquals(0, result.getCommonEdges().size());
    }

    @Test
    public void testSameNodesNewEdge() {
        // Same vertices, different edge structure
        graphA.addVertex("A");
        graphA.addVertex("B");
        addEdge(graphB, "A", "B");

        GraphDiffAnalyzer.DiffResult result =
                new GraphDiffAnalyzer(graphA, graphB).computeDiff();

        assertEquals(0, result.getAddedNodes().size());
        assertEquals(0, result.getRemovedNodes().size());
        assertEquals(2, result.getCommonNodes().size());
        assertEquals(1, result.getAddedEdges().size());
        assertEquals(0, result.getRemovedEdges().size());
        assertFalse(result.isIdentical());
    }

    @Test
    public void testSameNodesRemovedEdge() {
        addEdge(graphA, "A", "B");
        graphB.addVertex("A");
        graphB.addVertex("B");

        GraphDiffAnalyzer.DiffResult result =
                new GraphDiffAnalyzer(graphA, graphB).computeDiff();

        assertEquals(0, result.getAddedNodes().size());
        assertEquals(0, result.getRemovedNodes().size());
        assertEquals(0, result.getAddedEdges().size());
        assertEquals(1, result.getRemovedEdges().size());
    }

    // =========================================================================
    // Jaccard similarity
    // =========================================================================

    @Test
    public void testJaccardHalfOverlap() {
        // 2 shared, 1 only in A, 1 only in B → Jaccard = 2/4 = 0.5
        graphA.addVertex("A");
        graphA.addVertex("B");
        graphA.addVertex("C");
        graphB.addVertex("A");
        graphB.addVertex("B");
        graphB.addVertex("D");

        GraphDiffAnalyzer.DiffResult result =
                new GraphDiffAnalyzer(graphA, graphB).computeDiff();

        assertEquals(0.5, result.getNodeJaccard(), 0.001);
    }

    @Test
    public void testEdgeJaccardPartialOverlap() {
        // A: A-B, B-C; B: A-B, B-D → common edge = 1, total unique = 3
        addEdge(graphA, "A", "B");
        addEdge(graphA, "B", "C");
        addEdge(graphB, "A", "B");
        addEdge(graphB, "B", "D");

        GraphDiffAnalyzer.DiffResult result =
                new GraphDiffAnalyzer(graphA, graphB).computeDiff();

        // edge Jaccard = 1/3 ≈ 0.333
        assertEquals(1.0 / 3.0, result.getEdgeJaccard(), 0.001);
    }

    // =========================================================================
    // Edit distance
    // =========================================================================

    @Test
    public void testEditDistanceIdentical() {
        addEdge(graphA, "A", "B");
        addEdge(graphB, "A", "B");

        assertEquals(0, new GraphDiffAnalyzer(graphA, graphB).computeEditDistance());
    }

    @Test
    public void testEditDistanceOneAddition() {
        addEdge(graphA, "A", "B");
        addEdge(graphB, "A", "B");
        addEdge(graphB, "B", "C");

        // 1 added node + 1 added edge = 2
        assertEquals(2, new GraphDiffAnalyzer(graphA, graphB).computeEditDistance());
    }

    @Test
    public void testEditDistanceSwap() {
        addEdge(graphA, "A", "B");
        addEdge(graphA, "B", "C");
        addEdge(graphB, "A", "B");
        addEdge(graphB, "B", "D");

        // removed C + edge B-C, added D + edge B-D = 4
        assertEquals(4, new GraphDiffAnalyzer(graphA, graphB).computeEditDistance());
    }

    @Test
    public void testEditDistanceViaResult() {
        addEdge(graphA, "A", "B");
        addEdge(graphB, "A", "B");
        addEdge(graphB, "B", "C");

        // Via computeDiff().getEditDistance() should match computeEditDistance()
        GraphDiffAnalyzer analyzer = new GraphDiffAnalyzer(graphA, graphB);
        assertEquals(analyzer.computeEditDistance(),
                analyzer.computeDiff().getEditDistance());
    }

    // =========================================================================
    // Degree changes
    // =========================================================================

    @Test
    public void testDegreeChangesBasic() {
        addEdge(graphA, "A", "B");
        addEdge(graphB, "A", "B");
        addEdge(graphB, "A", "C");

        Map<String, int[]> changes =
                new GraphDiffAnalyzer(graphA, graphB).findDegreeChanges();

        assertTrue(changes.containsKey("A"));
        assertEquals(1, changes.get("A")[0]); // degree in A
        assertEquals(2, changes.get("A")[1]); // degree in B
        // B has degree 1 in both
        assertFalse(changes.containsKey("B"));
    }

    @Test
    public void testDegreeChangesNone() {
        addEdge(graphA, "A", "B");
        addEdge(graphB, "A", "B");

        Map<String, int[]> changes =
                new GraphDiffAnalyzer(graphA, graphB).findDegreeChanges();

        assertTrue(changes.isEmpty());
    }

    @Test
    public void testDegreeChangesOnlyCommonNodes() {
        // C only in B — should not appear in degree changes
        addEdge(graphA, "A", "B");
        addEdge(graphB, "A", "B");
        addEdge(graphB, "A", "C");

        Map<String, int[]> changes =
                new GraphDiffAnalyzer(graphA, graphB).findDegreeChanges();

        assertFalse(changes.containsKey("C"));
        assertTrue(changes.containsKey("A"));
    }

    @Test
    public void testDegreeChangesViaResult() {
        addEdge(graphA, "A", "B");
        addEdge(graphB, "A", "B");
        addEdge(graphB, "A", "C");

        GraphDiffAnalyzer analyzer = new GraphDiffAnalyzer(graphA, graphB);
        Map<String, int[]> direct = analyzer.findDegreeChanges();
        Map<String, int[]> viaResult = analyzer.computeDiff().getDegreeChanges();

        assertEquals(direct.size(), viaResult.size());
        for (String key : direct.keySet()) {
            assertTrue(viaResult.containsKey(key));
            assertArrayEquals(direct.get(key), viaResult.get(key));
        }
    }

    @Test
    public void testDegreeChangesMultipleNodes() {
        // A: A-B, A-C, B-C → A:2, B:2, C:2
        // B: A-B, A-C, A-D, B-C → A:3, B:2, C:2, D:1
        addEdge(graphA, "A", "B");
        addEdge(graphA, "A", "C");
        addEdge(graphA, "B", "C");
        addEdge(graphB, "A", "B");
        addEdge(graphB, "A", "C");
        addEdge(graphB, "A", "D");
        addEdge(graphB, "B", "C");

        Map<String, int[]> changes =
                new GraphDiffAnalyzer(graphA, graphB).findDegreeChanges();

        // Only A changed (2→3), B and C unchanged at 2
        assertTrue(changes.containsKey("A"));
        assertEquals(2, changes.get("A")[0]);
        assertEquals(3, changes.get("A")[1]);
        assertFalse(changes.containsKey("B"));
        assertFalse(changes.containsKey("C"));
    }

    // =========================================================================
    // EdgeDiff
    // =========================================================================

    @Test
    public void testEdgeDiffNormalization() {
        GraphDiffAnalyzer.EdgeDiff e1 = new GraphDiffAnalyzer.EdgeDiff("B", "A");
        GraphDiffAnalyzer.EdgeDiff e2 = new GraphDiffAnalyzer.EdgeDiff("A", "B");

        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
        assertEquals("A", e1.getVertex1());
        assertEquals("B", e1.getVertex2());
        assertEquals("A-B", e1.getEdgeKey());
    }

    @Test
    public void testEdgeDiffToString() {
        GraphDiffAnalyzer.EdgeDiff e = new GraphDiffAnalyzer.EdgeDiff("X", "Y");
        String s = e.toString();
        assertTrue(s.contains("X"));
        assertTrue(s.contains("Y"));
    }

    @Test
    public void testEdgeDiffEqualsSelf() {
        GraphDiffAnalyzer.EdgeDiff e = new GraphDiffAnalyzer.EdgeDiff("A", "B");
        assertEquals(e, e);
    }

    @Test
    public void testEdgeDiffNotEqualsDifferent() {
        GraphDiffAnalyzer.EdgeDiff e1 = new GraphDiffAnalyzer.EdgeDiff("A", "B");
        GraphDiffAnalyzer.EdgeDiff e2 = new GraphDiffAnalyzer.EdgeDiff("A", "C");
        assertNotEquals(e1, e2);
    }

    @Test
    public void testEdgeDiffNotEqualsNull() {
        GraphDiffAnalyzer.EdgeDiff e = new GraphDiffAnalyzer.EdgeDiff("A", "B");
        assertNotEquals(e, null);
    }

    @Test
    public void testEdgeDiffNotEqualsOtherType() {
        GraphDiffAnalyzer.EdgeDiff e = new GraphDiffAnalyzer.EdgeDiff("A", "B");
        assertNotEquals(e, "A-B");
    }

    // =========================================================================
    // DiffResult summary
    // =========================================================================

    @Test
    public void testSummaryIdentical() {
        addEdge(graphA, "A", "B");
        addEdge(graphB, "A", "B");

        String summary = new GraphDiffAnalyzer(graphA, graphB)
                .computeDiff().getSummary();

        assertTrue(summary.contains("Graph Diff Summary"));
        assertTrue(summary.contains("structurally identical"));
        assertTrue(summary.contains("Jaccard"));
    }

    @Test
    public void testSummaryNotIdentical() {
        addEdge(graphA, "A", "B");
        addEdge(graphB, "A", "C");

        String summary = new GraphDiffAnalyzer(graphA, graphB)
                .computeDiff().getSummary();

        assertTrue(summary.contains("added"));
        assertTrue(summary.contains("removed"));
        assertTrue(summary.contains("Edit distance"));
        assertFalse(summary.contains("structurally identical"));
    }

    @Test
    public void testSummaryIncludesDegreeChanges() {
        addEdge(graphA, "A", "B");
        addEdge(graphB, "A", "B");
        addEdge(graphB, "A", "C");

        String summary = new GraphDiffAnalyzer(graphA, graphB)
                .computeDiff().getSummary();

        assertTrue(summary.contains("Degree changes"));
    }

    @Test
    public void testSummaryNoDegreeChanges() {
        addEdge(graphA, "A", "B");
        addEdge(graphB, "A", "B");

        String summary = new GraphDiffAnalyzer(graphA, graphB)
                .computeDiff().getSummary();

        assertFalse(summary.contains("Degree changes"));
    }

    // =========================================================================
    // Larger graph scenarios
    // =========================================================================

    @Test
    public void testStarGraphGrowth() {
        // Star with 3 leaves → star with 5 leaves
        addEdge(graphA, "C", "L1");
        addEdge(graphA, "C", "L2");
        addEdge(graphA, "C", "L3");

        addEdge(graphB, "C", "L1");
        addEdge(graphB, "C", "L2");
        addEdge(graphB, "C", "L3");
        addEdge(graphB, "C", "L4");
        addEdge(graphB, "C", "L5");

        GraphDiffAnalyzer.DiffResult result =
                new GraphDiffAnalyzer(graphA, graphB).computeDiff();

        assertEquals(2, result.getAddedNodes().size());
        assertTrue(result.getAddedNodes().contains("L4"));
        assertTrue(result.getAddedNodes().contains("L5"));
        assertEquals(2, result.getAddedEdges().size());
        assertEquals(0, result.getRemovedEdges().size());

        // Center degree changed from 3 to 5
        assertTrue(result.getDegreeChanges().containsKey("C"));
        assertEquals(3, result.getDegreeChanges().get("C")[0]);
        assertEquals(5, result.getDegreeChanges().get("C")[1]);
    }

    @Test
    public void testPathToTriangle() {
        // Path A-B-C → Triangle A-B-C
        addEdge(graphA, "A", "B");
        addEdge(graphA, "B", "C");

        addEdge(graphB, "A", "B");
        addEdge(graphB, "B", "C");
        addEdge(graphB, "A", "C");

        GraphDiffAnalyzer.DiffResult result =
                new GraphDiffAnalyzer(graphA, graphB).computeDiff();

        assertTrue(result.getRemovedNodes().isEmpty());
        assertTrue(result.getAddedNodes().isEmpty());
        assertEquals(1, result.getAddedEdges().size());
        assertEquals(0, result.getRemovedEdges().size());
        assertEquals(3, result.getCommonNodes().size());
        assertEquals(2, result.getCommonEdges().size());
        assertEquals(1, result.getEditDistance());
    }

    @Test
    public void testCompleteReplacement() {
        // A has a 4-cycle, B has a completely different 4-cycle
        addEdge(graphA, "A", "B");
        addEdge(graphA, "B", "C");
        addEdge(graphA, "C", "D");
        addEdge(graphA, "D", "A");

        addEdge(graphB, "W", "X");
        addEdge(graphB, "X", "Y");
        addEdge(graphB, "Y", "Z");
        addEdge(graphB, "Z", "W");

        GraphDiffAnalyzer.DiffResult result =
                new GraphDiffAnalyzer(graphA, graphB).computeDiff();

        assertEquals(4, result.getAddedNodes().size());
        assertEquals(4, result.getRemovedNodes().size());
        assertEquals(0, result.getCommonNodes().size());
        assertEquals(0.0, result.getNodeJaccard(), 0.001);
        assertEquals(0.0, result.getEdgeJaccard(), 0.001);
    }

    // =========================================================================
    // DiffResult immutability
    // =========================================================================

    @Test(expected = UnsupportedOperationException.class)
    public void testAddedNodesImmutable() {
        addEdge(graphA, "A", "B");
        addEdge(graphB, "A", "B");
        addEdge(graphB, "B", "C");

        GraphDiffAnalyzer.DiffResult result =
                new GraphDiffAnalyzer(graphA, graphB).computeDiff();

        result.getAddedNodes().add("Z");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCommonEdgesImmutable() {
        addEdge(graphA, "A", "B");
        addEdge(graphB, "A", "B");

        GraphDiffAnalyzer.DiffResult result =
                new GraphDiffAnalyzer(graphA, graphB).computeDiff();

        result.getCommonEdges().add(new GraphDiffAnalyzer.EdgeDiff("X", "Y"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testDegreeChangesImmutable() {
        addEdge(graphA, "A", "B");
        addEdge(graphB, "A", "B");
        addEdge(graphB, "A", "C");

        GraphDiffAnalyzer.DiffResult result =
                new GraphDiffAnalyzer(graphA, graphB).computeDiff();

        result.getDegreeChanges().put("Z", new int[]{0, 0});
    }
}
