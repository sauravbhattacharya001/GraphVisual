package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link NodeCentralityAnalyzer}.
 */
public class NodeCentralityAnalyzerTest {

    private Graph<String, edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, edge>();
    }

    // --- Helper methods ---

    private edge addEdge(String type, String v1, String v2, float weight) {
        edge e = new edge(type, v1, v2);
        e.setWeight(weight);
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        graph.addEdge(e, v1, v2);
        return e;
    }

    // --- Constructor tests ---

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNullGraph() {
        new NodeCentralityAnalyzer(null);
    }

    @Test
    public void testConstructorValidGraph() {
        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        assertNotNull(analyzer);
        assertFalse(analyzer.isComputed());
    }

    // --- Empty graph ---

    @Test
    public void testEmptyGraph() {
        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();
        assertTrue(analyzer.isComputed());
        assertEquals(0, analyzer.getRankedResults().size());
        assertEquals(0, analyzer.getTopNodes(5).size());
    }

    @Test
    public void testEmptyGraphSummary() {
        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();
        Map<String, Object> summary = analyzer.getSummary();
        assertEquals(0, summary.get("nodeCount"));
        assertEquals(0.0, (Double) summary.get("avgDegreeCentrality"), 0.001);
    }

    @Test
    public void testEmptyGraphTopology() {
        // Single node
        graph.addVertex("A");
        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();
        assertEquals("Trivial", analyzer.classifyTopology());
    }

    // --- Single edge graph ---

    @Test
    public void testSingleEdge() {
        addEdge("f", "A", "B", 10.0f);
        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();

        List<NodeCentralityAnalyzer.CentralityResult> results = analyzer.getRankedResults();
        assertEquals(2, results.size());

        // Both nodes have degree centrality = 1.0 (1 connection / (2-1))
        NodeCentralityAnalyzer.CentralityResult rA = analyzer.getResult("A");
        NodeCentralityAnalyzer.CentralityResult rB = analyzer.getResult("B");
        assertNotNull(rA);
        assertNotNull(rB);
        assertEquals(1.0, rA.getDegreeCentrality(), 0.001);
        assertEquals(1.0, rB.getDegreeCentrality(), 0.001);
    }

    // --- Line graph (A-B-C-D) ---

    @Test
    public void testLineGraphDegreeCentrality() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        addEdge("f", "C", "D", 5.0f);

        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();

        // A and D have degree 1, B and C have degree 2
        // Degree centrality = degree / (V-1) = degree / 3
        NodeCentralityAnalyzer.CentralityResult rA = analyzer.getResult("A");
        NodeCentralityAnalyzer.CentralityResult rB = analyzer.getResult("B");
        NodeCentralityAnalyzer.CentralityResult rC = analyzer.getResult("C");
        NodeCentralityAnalyzer.CentralityResult rD = analyzer.getResult("D");

        assertEquals(1.0 / 3.0, rA.getDegreeCentrality(), 0.001);
        assertEquals(2.0 / 3.0, rB.getDegreeCentrality(), 0.001);
        assertEquals(2.0 / 3.0, rC.getDegreeCentrality(), 0.001);
        assertEquals(1.0 / 3.0, rD.getDegreeCentrality(), 0.001);
    }

    @Test
    public void testLineGraphBetweennessCentrality() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        addEdge("f", "C", "D", 5.0f);

        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();

        // B is on paths A-C, A-D (2 pairs) → raw = 2
        // C is on paths A-D, B-D (2 pairs) → raw = 2
        // A and D are endpoints only → raw = 0
        // Normalized by (V-1)(V-2) = 3*2 = 6
        NodeCentralityAnalyzer.CentralityResult rA = analyzer.getResult("A");
        NodeCentralityAnalyzer.CentralityResult rB = analyzer.getResult("B");
        NodeCentralityAnalyzer.CentralityResult rC = analyzer.getResult("C");
        NodeCentralityAnalyzer.CentralityResult rD = analyzer.getResult("D");

        assertEquals(0.0, rA.getBetweennessCentrality(), 0.001);
        assertTrue(rB.getBetweennessCentrality() > 0);
        assertTrue(rC.getBetweennessCentrality() > 0);
        assertEquals(0.0, rD.getBetweennessCentrality(), 0.001);

        // B and C should have equal betweenness (symmetric)
        assertEquals(rB.getBetweennessCentrality(), rC.getBetweennessCentrality(), 0.001);
    }

    @Test
    public void testLineGraphClosenessCentrality() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        addEdge("f", "C", "D", 5.0f);

        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();

        // B and C should have higher closeness (more central in the line)
        NodeCentralityAnalyzer.CentralityResult rA = analyzer.getResult("A");
        NodeCentralityAnalyzer.CentralityResult rB = analyzer.getResult("B");

        assertTrue(rB.getClosenessCentrality() > rA.getClosenessCentrality());
    }

    // --- Star graph (hub with spokes) ---

    @Test
    public void testStarGraphHub() {
        addEdge("f", "H", "A", 5.0f);
        addEdge("f", "H", "B", 5.0f);
        addEdge("f", "H", "C", 5.0f);
        addEdge("f", "H", "D", 5.0f);
        addEdge("f", "H", "E", 5.0f);

        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();

        NodeCentralityAnalyzer.CentralityResult rH = analyzer.getResult("H");
        NodeCentralityAnalyzer.CentralityResult rA = analyzer.getResult("A");

        // Hub should have highest degree centrality
        assertEquals(1.0, rH.getDegreeCentrality(), 0.001);
        assertEquals(1.0 / 5.0, rA.getDegreeCentrality(), 0.001);

        // Hub should have highest betweenness
        assertTrue(rH.getBetweennessCentrality() > rA.getBetweennessCentrality());

        // Hub should have highest closeness
        assertTrue(rH.getClosenessCentrality() > rA.getClosenessCentrality());

        // Hub should be #1 in combined score
        List<NodeCentralityAnalyzer.CentralityResult> ranked = analyzer.getRankedResults();
        assertEquals("H", ranked.get(0).getNodeId());
    }

    @Test
    public void testStarSpokesEqual() {
        addEdge("f", "H", "A", 5.0f);
        addEdge("f", "H", "B", 5.0f);
        addEdge("f", "H", "C", 5.0f);

        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();

        NodeCentralityAnalyzer.CentralityResult rA = analyzer.getResult("A");
        NodeCentralityAnalyzer.CentralityResult rB = analyzer.getResult("B");
        NodeCentralityAnalyzer.CentralityResult rC = analyzer.getResult("C");

        // All spokes should have equal centrality
        assertEquals(rA.getDegreeCentrality(), rB.getDegreeCentrality(), 0.001);
        assertEquals(rB.getDegreeCentrality(), rC.getDegreeCentrality(), 0.001);
        assertEquals(rA.getBetweennessCentrality(), rB.getBetweennessCentrality(), 0.001);
        assertEquals(rA.getClosenessCentrality(), rB.getClosenessCentrality(), 0.001);
    }

    // --- Triangle graph ---

    @Test
    public void testTriangleAllEqual() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        addEdge("f", "A", "C", 5.0f);

        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();

        NodeCentralityAnalyzer.CentralityResult rA = analyzer.getResult("A");
        NodeCentralityAnalyzer.CentralityResult rB = analyzer.getResult("B");
        NodeCentralityAnalyzer.CentralityResult rC = analyzer.getResult("C");

        // Complete graph: all nodes equal
        assertEquals(rA.getDegreeCentrality(), rB.getDegreeCentrality(), 0.001);
        assertEquals(rB.getDegreeCentrality(), rC.getDegreeCentrality(), 0.001);
        assertEquals(1.0, rA.getDegreeCentrality(), 0.001); // degree=2, V-1=2

        // Betweenness should be 0 for complete graph (all have direct edges)
        assertEquals(0.0, rA.getBetweennessCentrality(), 0.001);
    }

    // --- Disconnected graph ---

    @Test
    public void testDisconnectedGraph() {
        addEdge("f", "A", "B", 5.0f);
        graph.addVertex("C"); // isolated

        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();

        NodeCentralityAnalyzer.CentralityResult rC = analyzer.getResult("C");
        assertEquals(0.0, rC.getDegreeCentrality(), 0.001);
        assertEquals(0.0, rC.getBetweennessCentrality(), 0.001);
        assertEquals(0.0, rC.getClosenessCentrality(), 0.001);
    }

    @Test
    public void testDisconnectedComponents() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("c", "C", "D", 5.0f);

        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();

        // Closeness should be limited due to disconnection
        NodeCentralityAnalyzer.CentralityResult rA = analyzer.getResult("A");
        assertTrue(rA.getClosenessCentrality() < 1.0);
    }

    // --- getResult ---

    @Test
    public void testGetResultNonexistentNode() {
        addEdge("f", "A", "B", 5.0f);
        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();
        assertNull(analyzer.getResult("Z"));
    }

    @Test
    public void testGetResultAutoComputes() {
        addEdge("f", "A", "B", 5.0f);
        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        // Don't call compute() explicitly
        NodeCentralityAnalyzer.CentralityResult r = analyzer.getResult("A");
        assertNotNull(r);
        assertTrue(analyzer.isComputed());
    }

    // --- getRankedResults ---

    @Test
    public void testRankedResultsOrder() {
        addEdge("f", "H", "A", 5.0f);
        addEdge("f", "H", "B", 5.0f);
        addEdge("f", "H", "C", 5.0f);

        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();

        List<NodeCentralityAnalyzer.CentralityResult> ranked = analyzer.getRankedResults();
        assertEquals(4, ranked.size());
        // First should be the hub with highest combined score
        assertEquals("H", ranked.get(0).getNodeId());
        // Combined scores should be non-increasing
        for (int i = 1; i < ranked.size(); i++) {
            assertTrue(ranked.get(i - 1).getCombinedScore() >= ranked.get(i).getCombinedScore());
        }
    }

    // --- getTopNodes ---

    @Test
    public void testTopNodesZero() {
        addEdge("f", "A", "B", 5.0f);
        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();
        assertEquals(0, analyzer.getTopNodes(0).size());
    }

    @Test
    public void testTopNodesLimitedByN() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        addEdge("f", "C", "D", 5.0f);
        addEdge("f", "D", "E", 5.0f);

        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();
        assertEquals(3, analyzer.getTopNodes(3).size());
    }

    @Test
    public void testTopNodesExceedsSize() {
        addEdge("f", "A", "B", 5.0f);
        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();
        assertEquals(2, analyzer.getTopNodes(10).size());
    }

    // --- getTopByMetric ---

    @Test
    public void testTopByDegreeMetric() {
        addEdge("f", "H", "A", 5.0f);
        addEdge("f", "H", "B", 5.0f);
        addEdge("f", "H", "C", 5.0f);

        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();

        List<NodeCentralityAnalyzer.CentralityResult> top = analyzer.getTopByMetric(2, "degree");
        assertEquals(2, top.size());
        assertEquals("H", top.get(0).getNodeId());
    }

    @Test
    public void testTopByBetweennessMetric() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        addEdge("f", "C", "D", 5.0f);

        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();

        List<NodeCentralityAnalyzer.CentralityResult> top = analyzer.getTopByMetric(1, "betweenness");
        assertEquals(1, top.size());
        // B or C should be top (both have equal betweenness)
        String topNode = top.get(0).getNodeId();
        assertTrue(topNode.equals("B") || topNode.equals("C"));
    }

    @Test
    public void testTopByClosenessMetric() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);

        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();

        List<NodeCentralityAnalyzer.CentralityResult> top = analyzer.getTopByMetric(1, "closeness");
        assertEquals(1, top.size());
        assertEquals("B", top.get(0).getNodeId()); // B is the central node
    }

    @Test
    public void testTopByMetricZero() {
        addEdge("f", "A", "B", 5.0f);
        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();
        assertEquals(0, analyzer.getTopByMetric(0, "degree").size());
    }

    // --- Centrality maps ---

    @Test
    public void testDegreeCentralityMap() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);

        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();

        Map<String, Double> map = analyzer.getDegreeCentralityMap();
        assertEquals(3, map.size());
        assertEquals(0.5, map.get("A"), 0.001);
        assertEquals(1.0, map.get("B"), 0.001);
        assertEquals(0.5, map.get("C"), 0.001);
    }

    @Test
    public void testBetweennessCentralityMap() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);

        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();

        Map<String, Double> map = analyzer.getBetweennessCentralityMap();
        assertEquals(3, map.size());
        assertTrue(map.get("B") > map.get("A"));
    }

    @Test
    public void testClosenessCentralityMap() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);

        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();

        Map<String, Double> map = analyzer.getClosenessCentralityMap();
        assertEquals(3, map.size());
        assertTrue(map.get("B") > map.get("A"));
    }

    @Test
    public void testMapsAreUnmodifiable() {
        addEdge("f", "A", "B", 5.0f);
        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();

        try {
            analyzer.getDegreeCentralityMap().put("X", 1.0);
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
        try {
            analyzer.getBetweennessCentralityMap().put("X", 1.0);
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
        try {
            analyzer.getClosenessCentralityMap().put("X", 1.0);
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    // --- Summary ---

    @Test
    public void testSummaryKeys() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);

        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();

        Map<String, Object> summary = analyzer.getSummary();
        assertTrue(summary.containsKey("nodeCount"));
        assertTrue(summary.containsKey("avgDegreeCentrality"));
        assertTrue(summary.containsKey("avgBetweennessCentrality"));
        assertTrue(summary.containsKey("avgClosenessCentrality"));
        assertTrue(summary.containsKey("maxDegreeCentrality"));
        assertTrue(summary.containsKey("maxBetweennessCentrality"));
        assertTrue(summary.containsKey("maxClosenessCentrality"));
        assertTrue(summary.containsKey("maxDegreeCentralityNode"));
        assertTrue(summary.containsKey("maxBetweennessCentralityNode"));
        assertTrue(summary.containsKey("maxClosenessCentralityNode"));
    }

    @Test
    public void testSummaryValues() {
        addEdge("f", "H", "A", 5.0f);
        addEdge("f", "H", "B", 5.0f);
        addEdge("f", "H", "C", 5.0f);

        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();

        Map<String, Object> summary = analyzer.getSummary();
        assertEquals(4, summary.get("nodeCount"));
        assertEquals("H", summary.get("maxDegreeCentralityNode"));
        assertEquals(1.0, (Double) summary.get("maxDegreeCentrality"), 0.001);
    }

    // --- Topology classification ---

    @Test
    public void testTopologyTrivial() {
        graph.addVertex("A");
        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();
        assertEquals("Trivial", analyzer.classifyTopology());
    }

    @Test
    public void testTopologyDisconnected() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();
        assertEquals("Disconnected", analyzer.classifyTopology());
    }

    @Test
    public void testTopologyDistributed() {
        // Complete graph K4 — all nodes equally connected
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "A", "C", 5.0f);
        addEdge("f", "A", "D", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        addEdge("f", "B", "D", 5.0f);
        addEdge("f", "C", "D", 5.0f);

        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();
        assertEquals("Distributed", analyzer.classifyTopology());
    }

    // --- CentralityResult tests ---

    @Test
    public void testCentralityResultCombinedScore() {
        NodeCentralityAnalyzer.CentralityResult r =
                new NodeCentralityAnalyzer.CentralityResult("A", 3, 0.5, 0.8, 0.6);
        // Combined = 0.3*0.5 + 0.4*0.8 + 0.3*0.6 = 0.15 + 0.32 + 0.18 = 0.65
        assertEquals(0.65, r.getCombinedScore(), 0.001);
    }

    @Test
    public void testCentralityResultCompareTo() {
        NodeCentralityAnalyzer.CentralityResult r1 =
                new NodeCentralityAnalyzer.CentralityResult("A", 3, 0.5, 0.8, 0.6);
        NodeCentralityAnalyzer.CentralityResult r2 =
                new NodeCentralityAnalyzer.CentralityResult("B", 1, 0.1, 0.1, 0.1);
        assertTrue(r1.compareTo(r2) < 0); // r1 has higher combined score, comes first
        assertTrue(r2.compareTo(r1) > 0);
    }

    @Test
    public void testCentralityResultEquals() {
        NodeCentralityAnalyzer.CentralityResult r1 =
                new NodeCentralityAnalyzer.CentralityResult("A", 3, 0.5, 0.8, 0.6);
        NodeCentralityAnalyzer.CentralityResult r2 =
                new NodeCentralityAnalyzer.CentralityResult("A", 1, 0.1, 0.1, 0.1);
        assertEquals(r1, r2); // Same nodeId
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    public void testCentralityResultNotEquals() {
        NodeCentralityAnalyzer.CentralityResult r1 =
                new NodeCentralityAnalyzer.CentralityResult("A", 3, 0.5, 0.8, 0.6);
        NodeCentralityAnalyzer.CentralityResult r2 =
                new NodeCentralityAnalyzer.CentralityResult("B", 3, 0.5, 0.8, 0.6);
        assertNotEquals(r1, r2);
    }

    @Test
    public void testCentralityResultToString() {
        NodeCentralityAnalyzer.CentralityResult r =
                new NodeCentralityAnalyzer.CentralityResult("A", 3, 0.5, 0.8, 0.6);
        String s = r.toString();
        assertTrue(s.contains("Node A"));
        assertTrue(s.contains("degree="));
        assertTrue(s.contains("betweenness="));
        assertTrue(s.contains("closeness="));
        assertTrue(s.contains("combined="));
    }

    @Test
    public void testCentralityResultGetters() {
        NodeCentralityAnalyzer.CentralityResult r =
                new NodeCentralityAnalyzer.CentralityResult("X", 5, 0.3, 0.7, 0.9);
        assertEquals("X", r.getNodeId());
        assertEquals(5, r.getDegree());
        assertEquals(0.3, r.getDegreeCentrality(), 0.001);
        assertEquals(0.7, r.getBetweennessCentrality(), 0.001);
        assertEquals(0.9, r.getClosenessCentrality(), 0.001);
    }

    // --- Idempotent compute ---

    @Test
    public void testDoubleComputeIsSafe() {
        addEdge("f", "A", "B", 5.0f);
        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();
        analyzer.compute(); // should not throw or change results
        assertEquals(2, analyzer.getRankedResults().size());
    }

    // --- Larger graph tests ---

    @Test
    public void testLargerGraphMetrics() {
        // Pentagon graph
        addEdge("f", "1", "2", 5.0f);
        addEdge("f", "2", "3", 5.0f);
        addEdge("f", "3", "4", 5.0f);
        addEdge("f", "4", "5", 5.0f);
        addEdge("f", "5", "1", 5.0f);

        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();

        // Regular graph: all nodes should have equal centrality
        List<NodeCentralityAnalyzer.CentralityResult> results = analyzer.getRankedResults();
        assertEquals(5, results.size());

        double firstDC = results.get(0).getDegreeCentrality();
        for (NodeCentralityAnalyzer.CentralityResult r : results) {
            assertEquals(firstDC, r.getDegreeCentrality(), 0.001);
        }
    }

    @Test
    public void testBridgeNodeHighBetweenness() {
        // Two triangles connected by a bridge node
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        addEdge("f", "A", "C", 5.0f);
        addEdge("f", "C", "D", 5.0f); // bridge
        addEdge("f", "D", "E", 5.0f);
        addEdge("f", "E", "F", 5.0f);
        addEdge("f", "D", "F", 5.0f);

        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();

        // C and D are bridge nodes — should have high betweenness
        NodeCentralityAnalyzer.CentralityResult rC = analyzer.getResult("C");
        NodeCentralityAnalyzer.CentralityResult rD = analyzer.getResult("D");
        NodeCentralityAnalyzer.CentralityResult rA = analyzer.getResult("A");

        assertTrue(rC.getBetweennessCentrality() > rA.getBetweennessCentrality());
        assertTrue(rD.getBetweennessCentrality() > rA.getBetweennessCentrality());
    }

    @Test
    public void testMixedEdgeTypes() {
        addEdge("f", "A", "B", 10.0f);
        addEdge("c", "B", "C", 5.0f);
        addEdge("s", "C", "D", 3.0f);
        addEdge("fs", "A", "D", 7.0f);

        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        analyzer.compute();

        // Should work regardless of edge types
        assertEquals(4, analyzer.getRankedResults().size());
        for (NodeCentralityAnalyzer.CentralityResult r : analyzer.getRankedResults()) {
            assertTrue(r.getDegreeCentrality() >= 0);
            assertTrue(r.getBetweennessCentrality() >= 0);
            assertTrue(r.getClosenessCentrality() >= 0);
        }
    }

    // --- Auto-compute on lazy access ---

    @Test
    public void testAutoComputeOnGetSummary() {
        addEdge("f", "A", "B", 5.0f);
        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        assertFalse(analyzer.isComputed());
        Map<String, Object> summary = analyzer.getSummary();
        assertTrue(analyzer.isComputed());
        assertNotNull(summary);
    }

    @Test
    public void testAutoComputeOnGetMaps() {
        addEdge("f", "A", "B", 5.0f);
        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        Map<String, Double> map = analyzer.getDegreeCentralityMap();
        assertTrue(analyzer.isComputed());
        assertEquals(2, map.size());
    }

    @Test
    public void testAutoComputeOnClassify() {
        addEdge("f", "A", "B", 5.0f);
        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        String topology = analyzer.classifyTopology();
        assertTrue(analyzer.isComputed());
        assertNotNull(topology);
    }
}
