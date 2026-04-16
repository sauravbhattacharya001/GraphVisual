package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Map;

/**
 * Tests for GraphPowerCalculator — k-th power computation, diameter,
 * density, and report generation on JUNG UndirectedSparseGraph.
 */
public class GraphPowerCalculatorTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
    }

    private void addEdge(String v1, String v2) {
        Edge e = new Edge();
        e.setVertex1(v1);
        e.setVertex2(v2);
        graph.addEdge(e, v1, v2);
    }

    // ── Constructor / basic ──────────────────────────────────────

    @Test
    public void testSingleVertex() {
        graph.addVertex("A");
        GraphPowerCalculator calc = new GraphPowerCalculator(graph);
        Graph<String, Edge> p2 = calc.power(1);
        assertEquals(1, p2.getVertexCount());
        assertEquals(0, p2.getEdgeCount());
        assertEquals(0, calc.diameter());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPowerZeroThrows() {
        graph.addVertex("A");
        new GraphPowerCalculator(graph).power(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativePowerThrows() {
        graph.addVertex("A");
        new GraphPowerCalculator(graph).power(-1);
    }

    // ── Path graph: A-B-C-D ──────────────────────────────────────

    @Test
    public void testPathGraphPower1() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        GraphPowerCalculator calc = new GraphPowerCalculator(graph);
        Graph<String, Edge> p1 = calc.power(1);
        assertEquals(4, p1.getVertexCount());
        assertEquals(3, p1.getEdgeCount());
    }

    @Test
    public void testPathGraphPower2AddsDistanceTwoEdges() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        GraphPowerCalculator calc = new GraphPowerCalculator(graph);
        Graph<String, Edge> p2 = calc.power(2);
        assertEquals(4, p2.getVertexCount());
        // Original 3 edges + A-C, B-D = 5
        assertEquals(5, p2.getEdgeCount());
        assertNotNull(p2.findEdge("A", "C"));
        assertNotNull(p2.findEdge("B", "D"));
        assertNull(p2.findEdge("A", "D")); // distance 3, not in G^2
    }

    @Test
    public void testPathGraphPower3IsComplete() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        GraphPowerCalculator calc = new GraphPowerCalculator(graph);
        Graph<String, Edge> p3 = calc.power(3);
        // 4 vertices complete = 4*3/2 = 6 edges
        assertEquals(6, p3.getEdgeCount());
        assertNotNull(p3.findEdge("A", "D"));
    }

    @Test
    public void testDiameterOfPath() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        assertEquals(3, new GraphPowerCalculator(graph).diameter());
    }

    // ── Square and cube convenience methods ──────────────────────

    @Test
    public void testSquareAndCubeShortcuts() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        GraphPowerCalculator calc = new GraphPowerCalculator(graph);
        assertEquals(calc.power(2).getEdgeCount(), calc.square().getEdgeCount());
        assertEquals(calc.power(3).getEdgeCount(), calc.cube().getEdgeCount());
    }

    // ── newEdgeCount ─────────────────────────────────────────────

    @Test
    public void testNewEdgeCount() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        GraphPowerCalculator calc = new GraphPowerCalculator(graph);
        assertEquals(2, calc.newEdgeCount(2));  // A-C, B-D
        assertEquals(3, calc.newEdgeCount(3));  // + A-D
    }

    // ── density ──────────────────────────────────────────────────

    @Test
    public void testDensity() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        GraphPowerCalculator calc = new GraphPowerCalculator(graph);
        // G^3 is complete on 4 vertices → density = 1.0
        assertEquals(1.0, calc.density(3), 0.001);
        // G^1 has 3/6 = 0.5
        assertEquals(0.5, calc.density(1), 0.001);
    }

    @Test
    public void testDensitySingleVertex() {
        graph.addVertex("A");
        assertEquals(0.0, new GraphPowerCalculator(graph).density(1), 0.001);
    }

    // ── minPowerForComplete ──────────────────────────────────────

    @Test
    public void testMinPowerForComplete() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        assertEquals(3, new GraphPowerCalculator(graph).minPowerForComplete());
    }

    // ── allPairsDistances ────────────────────────────────────────

    @Test
    public void testAllPairsDistancesCaching() {
        addEdge("A", "B");
        addEdge("B", "C");
        GraphPowerCalculator calc = new GraphPowerCalculator(graph);
        Map<String, Map<String, Integer>> d1 = calc.allPairsDistances();
        Map<String, Map<String, Integer>> d2 = calc.allPairsDistances();
        assertSame("Should return cached instance", d1, d2);
        assertEquals(Integer.valueOf(2), d1.get("A").get("C"));
        assertEquals(Integer.valueOf(0), d1.get("A").get("A"));
    }

    // ── Disconnected graph ───────────────────────────────────────

    @Test
    public void testDisconnectedGraphPower() {
        addEdge("A", "B");
        graph.addVertex("C");
        GraphPowerCalculator calc = new GraphPowerCalculator(graph);
        Graph<String, Edge> p2 = calc.power(2);
        assertEquals(3, p2.getVertexCount());
        // Only A-B connected; C stays isolated even in G^2
        assertEquals(1, p2.getEdgeCount());
        assertNull(p2.findEdge("A", "C"));
    }

    // ── Triangle ─────────────────────────────────────────────────

    @Test
    public void testTrianglePower() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        GraphPowerCalculator calc = new GraphPowerCalculator(graph);
        assertEquals(1, calc.diameter());
        // G^1 is already complete on 3 vertices
        assertEquals(3, calc.power(1).getEdgeCount());
        assertEquals(0, calc.newEdgeCount(1));
    }

    // ── generateReport ───────────────────────────────────────────

    @Test
    public void testGenerateReportContainsKeyInfo() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        String report = new GraphPowerCalculator(graph).generateReport(2);
        assertTrue(report.contains("GRAPH POWER ANALYSIS"));
        assertTrue(report.contains("Vertices:  4"));
        assertTrue(report.contains("Edges:     3"));
        assertTrue(report.contains("Diameter:  3"));
    }
}
