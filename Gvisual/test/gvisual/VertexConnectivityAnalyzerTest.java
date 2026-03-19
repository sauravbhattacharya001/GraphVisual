package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for VertexConnectivityAnalyzer.
 */
public class VertexConnectivityAnalyzerTest {

    private int edgeCounter;

    private Graph<String, Edge> createGraph() {
        edgeCounter = 0;
        return new UndirectedSparseGraph<>();
    }

    private void addEdge(Graph<String, Edge> g, String u, String v) {
        g.addVertex(u);
        g.addVertex(v);
        g.addEdge(new Edge("f", u, v), u, v);
    }

    @Test
    public void testEmptyGraph() {
        Graph<String, Edge> g = createGraph();
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        assertEquals(0, a.vertexConnectivity());
        assertEquals(0, a.edgeConnectivity());
        assertTrue(a.isConnected());
    }

    @Test
    public void testSingleVertex() {
        Graph<String, Edge> g = createGraph();
        g.addVertex("A");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        assertEquals(0, a.vertexConnectivity());
        assertEquals(0, a.edgeConnectivity());
    }

    @Test
    public void testSingleEdge() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        assertEquals(1, a.edgeConnectivity());
        assertEquals(1, a.vertexConnectivity());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraph() {
        new VertexConnectivityAnalyzer(null);
    }

    @Test
    public void testDisconnectedGraph() {
        Graph<String, Edge> g = createGraph();
        g.addVertex("A");
        g.addVertex("B");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        assertEquals(0, a.vertexConnectivity());
        assertEquals(0, a.edgeConnectivity());
        assertFalse(a.isConnected());
    }

    @Test
    public void testDisconnectedWithEdges() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B");
        addEdge(g, "C", "D");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        assertEquals(0, a.vertexConnectivity());
    }

    @Test
    public void testPathGraph() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        assertEquals(1, a.vertexConnectivity());
        assertEquals(1, a.edgeConnectivity());
    }

    @Test
    public void testPathMinVertexCut() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        Set<String> cut = a.minimumVertexCut();
        assertEquals(1, cut.size());
        assertTrue(cut.contains("B") || cut.contains("C"));
    }

    @Test
    public void testCycleGraph() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        addEdge(g, "D", "A");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        assertEquals(2, a.vertexConnectivity());
        assertEquals(2, a.edgeConnectivity());
    }

    @Test
    public void testTriangle() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        assertEquals(2, a.vertexConnectivity());
        assertEquals(2, a.edgeConnectivity());
    }

    @Test
    public void testK4() {
        Graph<String, Edge> g = createGraph();
        String[] vs = {"A", "B", "C", "D"};
        for (int i = 0; i < vs.length; i++)
            for (int j = i + 1; j < vs.length; j++)
                addEdge(g, vs[i], vs[j]);
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        assertEquals(3, a.vertexConnectivity());
        assertEquals(3, a.edgeConnectivity());
    }

    @Test
    public void testK5() {
        Graph<String, Edge> g = createGraph();
        String[] vs = {"A", "B", "C", "D", "E"};
        for (int i = 0; i < vs.length; i++)
            for (int j = i + 1; j < vs.length; j++)
                addEdge(g, vs[i], vs[j]);
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        assertEquals(4, a.vertexConnectivity());
        assertEquals(4, a.edgeConnectivity());
    }

    @Test
    public void testIsKVertexConnected() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B"); addEdge(g, "B", "C");
        addEdge(g, "C", "D"); addEdge(g, "D", "A");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        assertTrue(a.isKVertexConnected(1));
        assertTrue(a.isKVertexConnected(2));
        assertFalse(a.isKVertexConnected(3));
    }

    @Test
    public void testIsKEdgeConnected() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B"); addEdge(g, "B", "C"); addEdge(g, "C", "A");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        assertTrue(a.isKEdgeConnected(2));
        assertFalse(a.isKEdgeConnected(3));
    }

    @Test
    public void testKZero() {
        Graph<String, Edge> g = createGraph();
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        assertTrue(a.isKVertexConnected(0));
        assertTrue(a.isKEdgeConnected(0));
    }

    @Test
    public void testWhitneyInequality() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B"); addEdge(g, "B", "C");
        addEdge(g, "C", "D"); addEdge(g, "D", "A");
        addEdge(g, "A", "C");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        assertTrue(a.verifyWhitney());
    }

    @Test
    public void testPairwiseOnPath() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B"); addEdge(g, "B", "C"); addEdge(g, "C", "D");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        VertexConnectivityAnalyzer.PairwiseResult r = a.pairwiseConnectivity("A", "D");
        assertEquals("A", r.getSource());
        assertEquals("D", r.getTarget());
        assertEquals(1, r.getVertexConnectivity());
        assertEquals(1, r.getEdgeConnectivity());
    }

    @Test
    public void testPairwiseDisjointPaths() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B"); addEdge(g, "A", "C");
        addEdge(g, "B", "D"); addEdge(g, "C", "D");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        VertexConnectivityAnalyzer.PairwiseResult r = a.pairwiseConnectivity("A", "D");
        assertEquals(2, r.getVertexConnectivity());
        assertEquals(2, r.getVertexDisjointPaths().size());
        assertEquals(2, r.getEdgeDisjointPaths().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPairwiseSameVertex() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B");
        new VertexConnectivityAnalyzer(g).pairwiseConnectivity("A", "A");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPairwiseNullVertex() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B");
        new VertexConnectivityAnalyzer(g).pairwiseConnectivity(null, "B");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPairwiseMissingVertex() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B");
        new VertexConnectivityAnalyzer(g).pairwiseConnectivity("A", "Z");
    }

    @Test
    public void testPairwiseAdjacent() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B"); addEdge(g, "A", "C"); addEdge(g, "B", "C");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        VertexConnectivityAnalyzer.PairwiseResult r = a.pairwiseConnectivity("A", "B");
        assertEquals(2, r.getEdgeConnectivity());
    }

    @Test
    public void testMinEdgeCutPath() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B"); addEdge(g, "B", "C");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        assertEquals(1, a.minimumEdgeCut().size());
    }

    @Test
    public void testMinEdgeCutCycle() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B"); addEdge(g, "B", "C"); addEdge(g, "C", "A");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        assertEquals(2, a.minimumEdgeCut().size());
    }

    @Test
    public void testVertexCriticalityPath() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B"); addEdge(g, "B", "C"); addEdge(g, "C", "D");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        Map<String, Integer> crit = a.vertexCriticality();
        assertEquals(4, crit.size());
        assertTrue(crit.get("B") >= 1 || crit.get("C") >= 1);
    }

    @Test
    public void testVertexCriticalityCycle() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B"); addEdge(g, "B", "C");
        addEdge(g, "C", "D"); addEdge(g, "D", "A");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        Map<String, Integer> crit = a.vertexCriticality();
        for (int v : crit.values()) assertEquals(1, v);
    }

    @Test
    public void testAllMinCutsPath() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B"); addEdge(g, "B", "C"); addEdge(g, "C", "D");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        List<Set<String>> cuts = a.allMinimumVertexCuts();
        assertFalse(cuts.isEmpty());
        for (Set<String> cut : cuts) assertEquals(1, cut.size());
    }

    @Test
    public void testAllMinCutsCycle() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B"); addEdge(g, "B", "C");
        addEdge(g, "C", "D"); addEdge(g, "D", "A");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        List<Set<String>> cuts = a.allMinimumVertexCuts();
        assertFalse(cuts.isEmpty());
        for (Set<String> cut : cuts) assertEquals(2, cut.size());
    }

    @Test
    public void testAnalyze() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B"); addEdge(g, "B", "C"); addEdge(g, "C", "A");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        VertexConnectivityAnalyzer.ConnectivityResult r = a.analyze();
        assertEquals(2, r.getVertexConnectivity());
        assertEquals(2, r.getEdgeConnectivity());
        assertEquals(2, r.getMinDegree());
        assertTrue(r.isWhitneyHolds());
        assertTrue(r.isConnected());
        assertEquals(3, r.getVertexCount());
        assertEquals(3, r.getEdgeCount());
    }

    @Test
    public void testAnalyzeDisconnected() {
        Graph<String, Edge> g = createGraph();
        g.addVertex("A"); g.addVertex("B");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        VertexConnectivityAnalyzer.ConnectivityResult r = a.analyze();
        assertEquals(0, r.getVertexConnectivity());
        assertFalse(r.isConnected());
    }

    @Test
    public void testReport() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B"); addEdge(g, "B", "C");
        addEdge(g, "C", "D"); addEdge(g, "D", "A");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        String report = a.generateReport();
        assertTrue(report.contains("Vertex connectivity"));
        assertTrue(report.contains("Edge connectivity"));
        assertTrue(report.contains("Whitney"));
        assertTrue(report.contains("2-connected"));
    }

    @Test
    public void testReportDisconnected() {
        Graph<String, Edge> g = createGraph();
        g.addVertex("A"); g.addVertex("B");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        String report = a.generateReport();
        assertTrue(report.contains("disconnected"));
    }

    @Test
    public void testBowTie() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B"); addEdge(g, "B", "C"); addEdge(g, "C", "A");
        addEdge(g, "C", "D"); addEdge(g, "D", "E"); addEdge(g, "E", "C");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        assertEquals(1, a.vertexConnectivity());
        Set<String> cut = a.minimumVertexCut();
        assertEquals(1, cut.size());
        assertTrue(cut.contains("C"));
    }

    @Test
    public void testPetersenGraph() {
        Graph<String, Edge> g = createGraph();
        for (int i = 0; i < 5; i++) addEdge(g, "o" + i, "o" + ((i + 1) % 5));
        for (int i = 0; i < 5; i++) addEdge(g, "i" + i, "i" + ((i + 2) % 5));
        for (int i = 0; i < 5; i++) addEdge(g, "o" + i, "i" + i);
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        assertEquals(3, a.vertexConnectivity());
        assertEquals(3, a.edgeConnectivity());
        assertTrue(a.isKVertexConnected(3));
        assertFalse(a.isKVertexConnected(4));
    }

    @Test
    public void testCubeGraph() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "0", "1"); addEdge(g, "1", "2"); addEdge(g, "2", "3"); addEdge(g, "3", "0");
        addEdge(g, "4", "5"); addEdge(g, "5", "6"); addEdge(g, "6", "7"); addEdge(g, "7", "4");
        addEdge(g, "0", "4"); addEdge(g, "1", "5"); addEdge(g, "2", "6"); addEdge(g, "3", "7");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        assertEquals(3, a.vertexConnectivity());
        assertEquals(3, a.edgeConnectivity());
        assertEquals(3, a.minDegree());
        assertTrue(a.verifyWhitney());
    }

    @Test
    public void testStarGraph() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "C", "A"); addEdge(g, "C", "B");
        addEdge(g, "C", "D"); addEdge(g, "C", "E");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        assertEquals(1, a.vertexConnectivity());
        assertTrue(a.minimumVertexCut().contains("C"));
    }

    @Test
    public void testEdgeDisjointPaths() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B"); addEdge(g, "A", "C");
        addEdge(g, "B", "D"); addEdge(g, "C", "D");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        assertEquals(2, a.findEdgeDisjointPaths("A", "D").size());
    }

    @Test
    public void testMinDegreeEmpty() {
        assertEquals(0, new VertexConnectivityAnalyzer(createGraph()).minDegree());
    }

    @Test
    public void testMinDegreeIsolated() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B");
        g.addVertex("C");
        assertEquals(0, new VertexConnectivityAnalyzer(g).minDegree());
    }

    @Test
    public void testConnectivityResultGetters() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B"); addEdge(g, "B", "C"); addEdge(g, "C", "A");
        VertexConnectivityAnalyzer.ConnectivityResult r = new VertexConnectivityAnalyzer(g).analyze();
        assertNotNull(r.getMinimumVertexCut());
        assertNotNull(r.getMinimumEdgeCut());
    }

    @Test
    public void testPairwiseResultGetters() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B"); addEdge(g, "B", "C");
        VertexConnectivityAnalyzer.PairwiseResult r = new VertexConnectivityAnalyzer(g).pairwiseConnectivity("A", "C");
        assertNotNull(r.getMinimumVertexCut());
        assertNotNull(r.getMinimumEdgeCut());
        assertNotNull(r.getVertexDisjointPaths());
        assertNotNull(r.getEdgeDisjointPaths());
    }

    @Test
    public void testWheelGraph() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "C", "1"); addEdge(g, "C", "2");
        addEdge(g, "C", "3"); addEdge(g, "C", "4");
        addEdge(g, "1", "2"); addEdge(g, "2", "3");
        addEdge(g, "3", "4"); addEdge(g, "4", "1");
        assertEquals(3, new VertexConnectivityAnalyzer(g).vertexConnectivity());
    }

    @Test
    public void testCycleNotThreeConnected() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B"); addEdge(g, "B", "C"); addEdge(g, "C", "D");
        addEdge(g, "D", "E"); addEdge(g, "E", "A");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        assertTrue(a.isKVertexConnected(2));
        assertFalse(a.isKVertexConnected(3));
    }

    @Test
    public void testMengerVertexDisjointPaths() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B"); addEdge(g, "A", "C"); addEdge(g, "A", "D");
        addEdge(g, "B", "C"); addEdge(g, "B", "D");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        VertexConnectivityAnalyzer.PairwiseResult r = a.pairwiseConnectivity("C", "D");
        assertEquals(2, r.getVertexConnectivity());
        assertEquals(2, r.getVertexDisjointPaths().size());
    }

    @Test
    public void testMinVertexCutDisconnected() {
        Graph<String, Edge> g = createGraph();
        g.addVertex("A"); g.addVertex("B");
        assertTrue(new VertexConnectivityAnalyzer(g).minimumVertexCut().isEmpty());
    }

    @Test
    public void testMinEdgeCutDisconnected() {
        Graph<String, Edge> g = createGraph();
        g.addVertex("A"); g.addVertex("B");
        assertTrue(new VertexConnectivityAnalyzer(g).minimumEdgeCut().isEmpty());
    }

    @Test
    public void testAllMinCutsEmpty() {
        Graph<String, Edge> g = createGraph();
        g.addVertex("A"); g.addVertex("B");
        assertTrue(new VertexConnectivityAnalyzer(g).allMinimumVertexCuts().isEmpty());
    }

    @Test
    public void testReportK4() {
        Graph<String, Edge> g = createGraph();
        String[] vs = {"A", "B", "C", "D"};
        for (int i = 0; i < vs.length; i++)
            for (int j = i + 1; j < vs.length; j++)
                addEdge(g, vs[i], vs[j]);
        assertTrue(new VertexConnectivityAnalyzer(g).generateReport().contains("3-connected"));
    }

    @Test
    public void testReportOneCut() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B"); addEdge(g, "B", "C");
        assertTrue(new VertexConnectivityAnalyzer(g).generateReport().contains("1-connected"));
    }

    @Test
    public void testVertexDisjointPathsSinglePath() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B"); addEdge(g, "B", "C");
        VertexConnectivityAnalyzer a = new VertexConnectivityAnalyzer(g);
        List<List<String>> paths = a.findVertexDisjointPaths("A", "C");
        assertEquals(1, paths.size());
        assertEquals(Arrays.asList("A", "B", "C"), paths.get(0));
    }

    @Test
    public void testEdgeDisjointPathsSinglePath() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B"); addEdge(g, "B", "C");
        List<List<String>> paths = new VertexConnectivityAnalyzer(g).findEdgeDisjointPaths("A", "C");
        assertEquals(1, paths.size());
    }

    @Test
    public void testWhitneyOnPath() {
        Graph<String, Edge> g = createGraph();
        addEdge(g, "A", "B"); addEdge(g, "B", "C");
        assertTrue(new VertexConnectivityAnalyzer(g).verifyWhitney());
    }
}
