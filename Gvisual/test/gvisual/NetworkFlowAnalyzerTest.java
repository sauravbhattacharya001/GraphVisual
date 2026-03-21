package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link NetworkFlowAnalyzer}.
 */
public class NetworkFlowAnalyzerTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, Edge>();
    }

    // --- Helpers ---

    private edge addEdge(String v1, String v2, float weight) {
        edge e = new Edge("f", v1, v2);
        e.setWeight(weight);
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        graph.addEdge(e, v1, v2);
        return e;
    }

    private edge addEdge(String v1, String v2) {
        return addEdge(v1, v2, 1.0f);
    }

    // ═══════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNullGraph() {
        new NetworkFlowAnalyzer(null);
    }

    @Test
    public void testConstructorValid() {
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        assertNotNull(nfa);
    }

    // ═══════════════════════════════════════
    // Compute validation
    // ═══════════════════════════════════════

    @Test(expected = IllegalArgumentException.class)
    public void testComputeNullSource() {
        addEdge("A", "B");
        new NetworkFlowAnalyzer(graph).compute(null, "B");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testComputeNullSink() {
        addEdge("A", "B");
        new NetworkFlowAnalyzer(graph).compute("A", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testComputeSourceNotInGraph() {
        addEdge("A", "B");
        new NetworkFlowAnalyzer(graph).compute("X", "B");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testComputeSinkNotInGraph() {
        addEdge("A", "B");
        new NetworkFlowAnalyzer(graph).compute("A", "X");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testComputeSameSourceAndSink() {
        addEdge("A", "B");
        new NetworkFlowAnalyzer(graph).compute("A", "A");
    }

    @Test(expected = IllegalStateException.class)
    public void testQueryBeforeCompute() {
        addEdge("A", "B");
        new NetworkFlowAnalyzer(graph).getMaxFlow();
    }

    // ═══════════════════════════════════════
    // Simple graphs
    // ═══════════════════════════════════════

    @Test
    public void testSingleEdge() {
        addEdge("A", "B", 5.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        double flow = nfa.compute("A", "B");
        assertEquals(5.0, flow, 1e-9);
        assertEquals(5.0, nfa.getMaxFlow(), 1e-9);
    }

    @Test
    public void testSingleEdgeReverse() {
        addEdge("A", "B", 5.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        // Flow from B to A through undirected edge
        double flow = nfa.compute("B", "A");
        assertEquals(5.0, flow, 1e-9);
    }

    @Test
    public void testTwoEdgePath() {
        addEdge("A", "B", 3.0f);
        addEdge("B", "C", 5.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        double flow = nfa.compute("A", "C");
        // Bottleneck at A-B (capacity 3)
        assertEquals(3.0, flow, 1e-9);
    }

    @Test
    public void testParallelPaths() {
        //   A --3-- B
        //   |       |
        //   2       4
        //   |       |
        //   C --5-- D
        addEdge("A", "B", 3.0f);
        addEdge("A", "C", 2.0f);
        addEdge("B", "D", 4.0f);
        addEdge("C", "D", 5.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        double flow = nfa.compute("A", "D");
        // Two paths: A->B->D (3) and A->C->D (2) = 5
        assertEquals(5.0, flow, 1e-9);
    }

    @Test
    public void testDisconnectedVertices() {
        graph.addVertex("A");
        graph.addVertex("B");
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        double flow = nfa.compute("A", "B");
        assertEquals(0.0, flow, 1e-9);
    }

    @Test
    public void testNoPathBetween() {
        addEdge("A", "B", 5.0f);
        addEdge("C", "D", 5.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        double flow = nfa.compute("A", "D");
        assertEquals(0.0, flow, 1e-9);
    }

    // ═══════════════════════════════════════
    // Classic max-flow examples
    // ═══════════════════════════════════════

    @Test
    public void testDiamondGraph() {
        //     B
        //    / \
        //   A   D
        //    \ /
        //     C
        addEdge("A", "B", 4.0f);
        addEdge("A", "C", 3.0f);
        addEdge("B", "D", 2.0f);
        addEdge("C", "D", 5.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        double flow = nfa.compute("A", "D");
        // Path A->B->D: 2, Path A->C->D: 3, total = 5
        // But A->B has cap 4, B->D has cap 2 (bottleneck)
        // A->C has cap 3, C->D has cap 5
        // Total: 2 + 3 = 5
        assertEquals(5.0, flow, 1e-9);
    }

    @Test
    public void testTriangleGraph() {
        addEdge("A", "B", 3.0f);
        addEdge("B", "C", 2.0f);
        addEdge("A", "C", 4.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        double flow = nfa.compute("A", "C");
        // Direct: A->C (4), plus A->B->C (2) = 6
        // But undirected edges: A-B cap 3, so A->B limited to 3
        // B-C cap 2, so B->C limited to 2
        // Total: 4 + 2 = 6
        assertEquals(6.0, flow, 1e-9);
    }

    @Test
    public void testDefaultWeight() {
        // Unweighted edges default to capacity 1.0
        edge e = new Edge("f", "A", "B");
        e.setWeight(0);
        if (!graph.containsVertex("A")) graph.addVertex("A");
        if (!graph.containsVertex("B")) graph.addVertex("B");
        graph.addEdge(e, "A", "B");

        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        double flow = nfa.compute("A", "B");
        assertEquals(1.0, flow, 1e-9);
    }

    // ═══════════════════════════════════════
    // Source and sink queries
    // ═══════════════════════════════════════

    @Test
    public void testGetSourceSink() {
        addEdge("X", "Y", 3.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        nfa.compute("X", "Y");
        assertEquals("X", nfa.getSource());
        assertEquals("Y", nfa.getSink());
    }

    // ═══════════════════════════════════════
    // Edge flows
    // ═══════════════════════════════════════

    @Test
    public void testGetEdgeFlowsSingleEdge() {
        addEdge("A", "B", 5.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        nfa.compute("A", "B");
        Map<String, Double> flows = nfa.getEdgeFlows();
        assertFalse(flows.isEmpty());
        // Should have flow on A->B
        boolean hasFlow = false;
        for (Map.Entry<String, Double> entry : flows.entrySet()) {
            if (entry.getValue() > 0) hasFlow = true;
        }
        assertTrue(hasFlow);
    }

    @Test
    public void testGetFlowOnEdge() {
        addEdge("A", "B", 5.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        nfa.compute("A", "B");
        double f = nfa.getFlowOnEdge("A", "B");
        assertEquals(5.0, f, 1e-9);
    }

    @Test
    public void testGetFlowOnNonexistentEdge() {
        addEdge("A", "B", 5.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        nfa.compute("A", "B");
        // No edge X->Y
        assertEquals(0.0, nfa.getFlowOnEdge("X", "Y"), 1e-9);
    }

    @Test
    public void testEdgeFlowsUnmodifiable() {
        addEdge("A", "B", 5.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        nfa.compute("A", "B");
        Map<String, Double> flows = nfa.getEdgeFlows();
        try {
            flows.put("test", 99.0);
            fail("Should throw on modification");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    // ═══════════════════════════════════════
    // Min cut
    // ═══════════════════════════════════════

    @Test
    public void testMinCutSingleEdge() {
        addEdge("A", "B", 5.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        nfa.compute("A", "B");
        List<Edge> cut = nfa.getMinCut();
        assertEquals(1, cut.size());
    }

    @Test
    public void testMinCutDisconnected() {
        graph.addVertex("A");
        graph.addVertex("B");
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        nfa.compute("A", "B");
        List<Edge> cut = nfa.getMinCut();
        assertEquals(0, cut.size());
    }

    @Test
    public void testMinCutUnmodifiable() {
        addEdge("A", "B", 5.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        nfa.compute("A", "B");
        List<Edge> cut = nfa.getMinCut();
        try {
            cut.add(new Edge("f", "X", "Y"));
            fail("Should throw on modification");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    // ═══════════════════════════════════════
    // Source side
    // ═══════════════════════════════════════

    @Test
    public void testSourceSideSingleEdge() {
        addEdge("A", "B", 5.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        nfa.compute("A", "B");
        Set<String> srcSide = nfa.getSourceSide();
        assertTrue(srcSide.contains("A"));
        // After max flow, B should not be reachable in residual
    }

    @Test
    public void testSourceSideDisconnected() {
        graph.addVertex("A");
        graph.addVertex("B");
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        nfa.compute("A", "B");
        Set<String> srcSide = nfa.getSourceSide();
        assertTrue(srcSide.contains("A"));
        assertFalse(srcSide.contains("B"));
    }

    @Test
    public void testSourceSideUnmodifiable() {
        addEdge("A", "B", 5.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        nfa.compute("A", "B");
        Set<String> srcSide = nfa.getSourceSide();
        try {
            srcSide.add("X");
            fail("Should throw on modification");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    // ═══════════════════════════════════════
    // Bottleneck edges
    // ═══════════════════════════════════════

    @Test
    public void testBottleneckEdges() {
        addEdge("A", "B", 3.0f);
        addEdge("B", "C", 5.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        nfa.compute("A", "C");
        // A-B is bottleneck (capacity 3, flow 3)
        List<Edge> bottlenecks = nfa.getBottleneckEdges();
        assertTrue(bottlenecks.size() >= 1);
    }

    @Test
    public void testBottleneckEdgesUnmodifiable() {
        addEdge("A", "B", 5.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        nfa.compute("A", "B");
        List<Edge> bottlenecks = nfa.getBottleneckEdges();
        try {
            bottlenecks.add(new Edge("f", "X", "Y"));
            fail("Should throw on modification");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    // ═══════════════════════════════════════
    // Capacity and utilisation
    // ═══════════════════════════════════════

    @Test
    public void testTotalCapacity() {
        addEdge("A", "B", 3.0f);
        addEdge("B", "C", 5.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        nfa.compute("A", "C");
        assertEquals(8.0, nfa.getTotalCapacity(), 1e-9);
    }

    @Test
    public void testUtilisationFullySaturated() {
        addEdge("A", "B", 5.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        nfa.compute("A", "B");
        assertEquals(100.0, nfa.getUtilisation(), 1e-9);
    }

    @Test
    public void testUtilisationPartial() {
        addEdge("A", "B", 3.0f);
        addEdge("B", "C", 1.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        nfa.compute("A", "C");
        // Flow = 1, source capacity = 3
        double util = nfa.getUtilisation();
        assertTrue(util > 0 && util < 100);
    }

    // ═══════════════════════════════════════
    // Flow path decomposition
    // ═══════════════════════════════════════

    @Test
    public void testDecomposeFlowPathsSinglePath() {
        addEdge("A", "B", 3.0f);
        addEdge("B", "C", 5.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        nfa.compute("A", "C");
        List<NetworkFlowAnalyzer.FlowPath> paths = nfa.decomposeFlowPaths();
        assertFalse(paths.isEmpty());
        // Single path A->B->C with flow 3
        double totalFlow = 0;
        for (NetworkFlowAnalyzer.FlowPath p : paths) {
            totalFlow += p.getFlowValue();
        }
        assertEquals(3.0, totalFlow, 1e-9);
    }

    @Test
    public void testDecomposeFlowPathsParallel() {
        addEdge("A", "B", 3.0f);
        addEdge("A", "C", 2.0f);
        addEdge("B", "D", 4.0f);
        addEdge("C", "D", 5.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        nfa.compute("A", "D");
        List<NetworkFlowAnalyzer.FlowPath> paths = nfa.decomposeFlowPaths();
        double totalFlow = 0;
        for (NetworkFlowAnalyzer.FlowPath p : paths) {
            totalFlow += p.getFlowValue();
            assertTrue(p.getFlowValue() > 0);
            assertEquals("A", p.getVertices().get(0));
            assertEquals("D", p.getVertices().get(p.getVertices().size() - 1));
        }
        assertEquals(5.0, totalFlow, 1e-9);
    }

    @Test
    public void testDecomposeFlowPathsDisconnected() {
        graph.addVertex("A");
        graph.addVertex("B");
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        nfa.compute("A", "B");
        List<NetworkFlowAnalyzer.FlowPath> paths = nfa.decomposeFlowPaths();
        assertTrue(paths.isEmpty());
    }

    @Test
    public void testDecomposeFlowPathsUnmodifiable() {
        addEdge("A", "B", 5.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        nfa.compute("A", "B");
        List<NetworkFlowAnalyzer.FlowPath> paths = nfa.decomposeFlowPaths();
        try {
            paths.add(new NetworkFlowAnalyzer.FlowPath(
                    java.util.Collections.singletonList("X"), 1.0));
            fail("Should throw on modification");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testAugmentingPathCount() {
        addEdge("A", "B", 3.0f);
        addEdge("A", "C", 2.0f);
        addEdge("B", "D", 4.0f);
        addEdge("C", "D", 5.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        nfa.compute("A", "D");
        int count = nfa.getAugmentingPathCount();
        assertTrue(count >= 1);
    }

    // ═══════════════════════════════════════
    // FlowPath object
    // ═══════════════════════════════════════

    @Test
    public void testFlowPathToString() {
        NetworkFlowAnalyzer.FlowPath path = new NetworkFlowAnalyzer.FlowPath(
                java.util.Arrays.asList("A", "B", "C"), 3.5);
        String s = path.toString();
        assertTrue(s.contains("A"));
        assertTrue(s.contains("B"));
        assertTrue(s.contains("C"));
        assertTrue(s.contains("3.50"));
    }

    @Test
    public void testFlowPathGetters() {
        NetworkFlowAnalyzer.FlowPath path = new NetworkFlowAnalyzer.FlowPath(
                java.util.Arrays.asList("X", "Y"), 7.0);
        assertEquals(2, path.getVertices().size());
        assertEquals(7.0, path.getFlowValue(), 1e-9);
    }

    // ═══════════════════════════════════════
    // FlowResult object
    // ═══════════════════════════════════════

    @Test
    public void testGetResult() {
        addEdge("A", "B", 3.0f);
        addEdge("B", "C", 5.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        nfa.compute("A", "C");
        NetworkFlowAnalyzer.FlowResult result = nfa.getResult();
        assertEquals("A", result.getSource());
        assertEquals("C", result.getSink());
        assertEquals(3.0, result.getMaxFlow(), 1e-9);
        assertTrue(result.getTotalCapacity() > 0);
        assertTrue(result.getUtilisation() >= 0);
        assertTrue(result.getMinCutSize() >= 0);
        assertTrue(result.getBottleneckCount() >= 0);
        assertTrue(result.getPathCount() >= 1);
        assertNotNull(result.getEdgeFlows());
        assertNotNull(result.getFlowPaths());
    }

    // ═══════════════════════════════════════
    // Summary
    // ═══════════════════════════════════════

    @Test
    public void testSummary() {
        addEdge("A", "B", 3.0f);
        addEdge("B", "C", 5.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        nfa.compute("A", "C");
        String summary = nfa.getSummary();
        assertTrue(summary.contains("Network Flow Analysis"));
        assertTrue(summary.contains("Maximum flow"));
        assertTrue(summary.contains("3.00"));
        assertTrue(summary.contains("Source: A"));
        assertTrue(summary.contains("Sink: C"));
    }

    @Test(expected = IllegalStateException.class)
    public void testSummaryBeforeCompute() {
        addEdge("A", "B");
        new NetworkFlowAnalyzer(graph).getSummary();
    }

    // ═══════════════════════════════════════
    // Larger graphs
    // ═══════════════════════════════════════

    @Test
    public void testLinearChain() {
        // A -1- B -1- C -1- D -1- E
        addEdge("A", "B", 1.0f);
        addEdge("B", "C", 1.0f);
        addEdge("C", "D", 1.0f);
        addEdge("D", "E", 1.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        double flow = nfa.compute("A", "E");
        assertEquals(1.0, flow, 1e-9);
    }

    @Test
    public void testStarGraph() {
        // Hub connected to 4 leaves
        addEdge("H", "A", 2.0f);
        addEdge("H", "B", 3.0f);
        addEdge("H", "C", 1.0f);
        addEdge("H", "D", 4.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        // Flow from H to D: limited to H-D edge (4.0)
        double flow = nfa.compute("H", "D");
        assertEquals(4.0, flow, 1e-9);
    }

    @Test
    public void testCompleteGraph4Nodes() {
        addEdge("A", "B", 1.0f);
        addEdge("A", "C", 1.0f);
        addEdge("A", "D", 1.0f);
        addEdge("B", "C", 1.0f);
        addEdge("B", "D", 1.0f);
        addEdge("C", "D", 1.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        double flow = nfa.compute("A", "D");
        // A has 3 edges of cap 1, D has 3 edges of cap 1
        // Max flow = 3 (all three paths: A->D, A->B->D, A->C->D)
        assertEquals(3.0, flow, 1e-9);
    }

    @Test
    public void testRecomputeOverwrites() {
        addEdge("A", "B", 5.0f);
        addEdge("B", "C", 3.0f);
        addEdge("A", "C", 2.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);

        double flow1 = nfa.compute("A", "C");
        assertEquals(5.0, flow1, 1e-9);

        // Recompute with different source/sink
        double flow2 = nfa.compute("C", "A");
        assertEquals(5.0, flow2, 1e-9);
        assertEquals("C", nfa.getSource());
        assertEquals("A", nfa.getSink());
    }

    @Test
    public void testSingleVertex() {
        graph.addVertex("A");
        graph.addVertex("B");
        addEdge("A", "B", 10.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        double flow = nfa.compute("A", "B");
        assertEquals(10.0, flow, 1e-9);
    }

    // ═══════════════════════════════════════
    // Flow conservation
    // ═══════════════════════════════════════

    @Test
    public void testFlowConservation() {
        // Verify flow in == flow out at intermediate nodes
        addEdge("S", "A", 10.0f);
        addEdge("S", "B", 5.0f);
        addEdge("A", "C", 4.0f);
        addEdge("A", "B", 3.0f);
        addEdge("B", "C", 6.0f);
        addEdge("C", "T", 12.0f);
        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        nfa.compute("S", "T");

        // Total flow out of S should equal total flow into T
        double totalFlow = nfa.getMaxFlow();
        List<NetworkFlowAnalyzer.FlowPath> paths = nfa.decomposeFlowPaths();
        double pathSum = 0;
        for (NetworkFlowAnalyzer.FlowPath p : paths) {
            pathSum += p.getFlowValue();
        }
        assertEquals(totalFlow, pathSum, 1e-9);
    }

    // ═══════════════════════════════════════
    // Issue #45: directedKey collision with arrow in names
    // ═══════════════════════════════════════

    @Test
    public void testVertexNamesContainingArrowSeparator() {
        // Vertex names that would collide under string concatenation:
        // "A->B" + "->" + "C" == "A" + "->" + "B->C" == "A->B->C"
        addEdge("A->B", "C", 5.0f);
        addEdge("A", "B->C", 3.0f);
        addEdge("A->B", "sink", 5.0f);
        addEdge("B->C", "sink", 3.0f);
        addEdge("src", "A->B", 5.0f);
        addEdge("src", "A", 3.0f);

        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        double maxFlow = nfa.compute("src", "sink");

        // Two independent paths: src->A->B->C->sink (cap 3) and src->A->B->sink (cap 5)
        // Total flow should be 8
        assertEquals(8.0, maxFlow, 1e-9);

        // Verify flows are correctly assigned (not merged/corrupted)
        Map<String, Double> edgeFlows = nfa.getEdgeFlows();
        assertFalse("Edge flows should not be empty", edgeFlows.isEmpty());

        // Summary should not crash
        String summary = nfa.getSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("Maximum flow: 8.00"));
    }

    @Test
    public void testVertexNameIsExactlyArrow() {
        // Edge case: vertex name is literally "->"
        addEdge("S", "->", 4.0f);
        addEdge("->", "T", 4.0f);

        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        double maxFlow = nfa.compute("S", "T");
        assertEquals(4.0, maxFlow, 1e-9);
    }

    @Test
    public void testVertexNameWithMultipleArrows() {
        // Vertex names with multiple arrow patterns
        addEdge("A->->B", "C->D", 2.0f);
        addEdge("src", "A->->B", 2.0f);
        addEdge("C->D", "sink", 2.0f);

        NetworkFlowAnalyzer nfa = new NetworkFlowAnalyzer(graph);
        double maxFlow = nfa.compute("src", "sink");
        assertEquals(2.0, maxFlow, 1e-9);

        // Min cut and bottleneck should work without crash
        assertNotNull(nfa.getMinCut());
        assertNotNull(nfa.getBottleneckEdges());
        assertNotNull(nfa.decomposeFlowPaths());
    }
}
