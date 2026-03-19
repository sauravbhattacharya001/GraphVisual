package gvisual;

import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link CycleAnalyzer} — cycle detection, girth, fundamental
 * cycle basis, bounded enumeration, and full report generation.
 */
public class CycleAnalyzerTest {

    // ── Helper ──────────────────────────────────────────────────────

    private static int edgeCounter = 0;

    private static Edge addEdge(Graph<String, Edge> g, String v1, String v2) {
        g.addVertex(v1);
        g.addVertex(v2);
        Edge e = new Edge("f", v1, v2);
        g.addEdge(e, v1, v2);
        return e;
    }

    private static Edge addWeightedEdge(Graph<String, Edge> g, String v1, String v2, float w) {
        g.addVertex(v1);
        g.addVertex(v2);
        Edge e = new Edge("f", v1, v2);
        e.setWeight(w);
        g.addEdge(e, v1, v2);
        return e;
    }

    // ── Constructor ─────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new CycleAnalyzer(null);
    }

    // ── hasCycles — undirected ──────────────────────────────────────

    @Test
    public void testEmptyGraphHasNoCycles() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        CycleAnalyzer ca = new CycleAnalyzer(g);
        assertFalse(ca.hasCycles());
    }

    @Test
    public void testSingleVertexNoCycle() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        assertFalse(new CycleAnalyzer(g).hasCycles());
    }

    @Test
    public void testTreeNoCycleUndirected() {
        // A-B-C (path, no cycle)
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        assertFalse(new CycleAnalyzer(g).hasCycles());
    }

    @Test
    public void testTriangleCycleUndirected() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        assertTrue(new CycleAnalyzer(g).hasCycles());
    }

    @Test
    public void testSquareCycleUndirected() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        addEdge(g, "D", "A");
        assertTrue(new CycleAnalyzer(g).hasCycles());
    }

    // ── hasCycles — directed ────────────────────────────────────────

    @Test
    public void testDAGNoCycle() {
        Graph<String, Edge> g = new DirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "A", "C");
        assertFalse(new CycleAnalyzer(g).hasCycles());
    }

    @Test
    public void testDirectedTriangleCycle() {
        Graph<String, Edge> g = new DirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        assertTrue(new CycleAnalyzer(g).hasCycles());
    }

    @Test
    public void testSelfLoopDirected() {
        Graph<String, Edge> g = new DirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "A");
        assertTrue(new CycleAnalyzer(g).hasCycles());
    }

    // ── Girth ───────────────────────────────────────────────────────

    @Test
    public void testGirthEmptyGraph() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        assertEquals(-1, new CycleAnalyzer(g).girth());
    }

    @Test
    public void testGirthAcyclic() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        assertEquals(-1, new CycleAnalyzer(g).girth());
    }

    @Test
    public void testGirthTriangle() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        assertEquals(3, new CycleAnalyzer(g).girth());
    }

    @Test
    public void testGirthSquareWithDiagonal() {
        // Square A-B-C-D-A plus diagonal A-C → shortest cycle is triangle
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        addEdge(g, "D", "A");
        addEdge(g, "A", "C");
        assertEquals(3, new CycleAnalyzer(g).girth());
    }

    @Test
    public void testGirthDirectedCycle() {
        Graph<String, Edge> g = new DirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        assertEquals(3, new CycleAnalyzer(g).girth());
    }

    // ── Fundamental Cycle Basis ─────────────────────────────────────

    @Test
    public void testFundamentalBasisEmpty() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        List<CycleAnalyzer.Cycle> basis = new CycleAnalyzer(g).fundamentalCycleBasis();
        assertTrue(basis.isEmpty());
    }

    @Test
    public void testFundamentalBasisTree() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        List<CycleAnalyzer.Cycle> basis = new CycleAnalyzer(g).fundamentalCycleBasis();
        assertEquals(0, basis.size());
    }

    @Test
    public void testFundamentalBasisSingleCycle() {
        // Triangle: 1 non-tree edge → 1 fundamental cycle
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        List<CycleAnalyzer.Cycle> basis = new CycleAnalyzer(g).fundamentalCycleBasis();
        assertEquals(1, basis.size());
        assertEquals(3, basis.get(0).length());
    }

    @Test
    public void testFundamentalBasisK4() {
        // K4: 4 vertices, 6 edges → cyclomatic number = 6 - 4 + 1 = 3
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        String[] verts = {"A", "B", "C", "D"};
        for (int i = 0; i < verts.length; i++) {
            for (int j = i + 1; j < verts.length; j++) {
                addEdge(g, verts[i], verts[j]);
            }
        }
        List<CycleAnalyzer.Cycle> basis = new CycleAnalyzer(g).fundamentalCycleBasis();
        assertEquals(3, basis.size());
    }

    // ── Cycle enumeration ───────────────────────────────────────────

    @Test
    public void testEnumerateNoCycles() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        CycleAnalyzer.CycleEnumerationResult r = new CycleAnalyzer(g).findAllSimpleCycles();
        assertEquals(0, r.count());
        assertTrue(r.isComplete());
    }

    @Test
    public void testEnumerateTriangle() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        CycleAnalyzer.CycleEnumerationResult r = new CycleAnalyzer(g).findAllSimpleCycles();
        assertEquals(1, r.count());
        assertTrue(r.isComplete());
    }

    @Test
    public void testEnumerateWithLimit() {
        // K4 has 7 cycles — set limit to 2
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        String[] verts = {"A", "B", "C", "D"};
        for (int i = 0; i < verts.length; i++) {
            for (int j = i + 1; j < verts.length; j++) {
                addEdge(g, verts[i], verts[j]);
            }
        }
        CycleAnalyzer.CycleEnumerationResult r = new CycleAnalyzer(g).findAllSimpleCycles(2);
        assertEquals(2, r.count());
        assertFalse(r.isComplete());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEnumerateNegativeLimit() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        new CycleAnalyzer(g).findAllSimpleCycles(-1);
    }

    @Test
    public void testEnumerateDirectedCycles() {
        // A→B→C→A and A→B→D→A
        Graph<String, Edge> g = new DirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        addEdge(g, "B", "D");
        addEdge(g, "D", "A");
        CycleAnalyzer.CycleEnumerationResult r = new CycleAnalyzer(g).findAllSimpleCycles();
        assertEquals(2, r.count());
    }

    // ── Cycle class ─────────────────────────────────────────────────

    @Test
    public void testCycleLength() {
        CycleAnalyzer.Cycle c = new CycleAnalyzer.Cycle(Arrays.asList("A", "B", "C"));
        assertEquals(3, c.length());
        assertEquals(Arrays.asList("A", "B", "C"), c.getVertices());
    }

    @Test
    public void testCycleEqualityIgnoresRotation() {
        // Same cycle rotated
        CycleAnalyzer.Cycle c1 = new CycleAnalyzer.Cycle(Arrays.asList("A", "B", "C"));
        CycleAnalyzer.Cycle c2 = new CycleAnalyzer.Cycle(Arrays.asList("B", "C", "A"));
        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    public void testCycleDifferentVerticesNotEqual() {
        CycleAnalyzer.Cycle c1 = new CycleAnalyzer.Cycle(Arrays.asList("A", "B", "C"));
        CycleAnalyzer.Cycle c2 = new CycleAnalyzer.Cycle(Arrays.asList("A", "B", "D"));
        assertNotEquals(c1, c2);
    }

    @Test
    public void testCycleWeight() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addWeightedEdge(g, "A", "B", 1.5f);
        addWeightedEdge(g, "B", "C", 2.5f);
        addWeightedEdge(g, "C", "A", 3.0f);
        CycleAnalyzer.Cycle c = new CycleAnalyzer.Cycle(Arrays.asList("A", "B", "C"));
        assertEquals(7.0f, c.totalWeight(g), 0.001f);
    }

    @Test
    public void testCycleToString() {
        CycleAnalyzer.Cycle c = new CycleAnalyzer.Cycle(Arrays.asList("X", "Y", "Z"));
        assertEquals("X → Y → Z → X", c.toString());
    }

    // ── Full report (analyze) ───────────────────────────────────────

    @Test
    public void testAnalyzeAcyclicGraph() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        CycleAnalyzer.CycleReport report = new CycleAnalyzer(g).analyze();
        assertFalse(report.hasCycles());
        assertEquals(-1, report.getGirth());
        assertEquals(-1, report.getCircumference());
        assertEquals(0, report.getCyclomaticNumber());
        assertTrue(report.getAllCycles().isEmpty());
        assertTrue(report.getFundamentalBasis().isEmpty());
        assertNull(report.getMostCyclicVertex());
        assertEquals(0.0, report.getAverageCycleLength(), 0.001);
    }

    @Test
    public void testAnalyzeTriangle() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        CycleAnalyzer.CycleReport report = new CycleAnalyzer(g).analyze();
        assertTrue(report.hasCycles());
        assertEquals(3, report.getGirth());
        assertEquals(3, report.getCircumference());
        assertEquals(1, report.getCyclomaticNumber());
        assertEquals(1, report.getAllCycles().size());
        assertTrue(report.isAllCyclesComplete());
        assertEquals(3.0, report.getAverageCycleLength(), 0.001);
        // All 3 vertices participate in 1 cycle
        for (String v : Arrays.asList("A", "B", "C")) {
            assertEquals(Integer.valueOf(1), report.getVertexParticipation().get(v));
        }
    }

    @Test
    public void testAnalyzeK4() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        String[] verts = {"A", "B", "C", "D"};
        for (int i = 0; i < verts.length; i++) {
            for (int j = i + 1; j < verts.length; j++) {
                addEdge(g, verts[i], verts[j]);
            }
        }
        CycleAnalyzer.CycleReport report = new CycleAnalyzer(g).analyze();
        assertTrue(report.hasCycles());
        assertEquals(3, report.getGirth());
        assertTrue(report.getCircumference() >= 3);
        assertEquals(3, report.getCyclomaticNumber());
        // K4 has 7 simple cycles (4 triangles + 3 squares)
        assertEquals(7, report.getAllCycles().size());
        assertNotNull(report.getMostCyclicVertex());
        assertNotNull(report.getSummary());
        assertTrue(report.getSummary().contains("Cyclic"));
    }

    @Test
    public void testAnalyzeReportSummaryAcyclic() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        CycleAnalyzer.CycleReport report = new CycleAnalyzer(g).analyze();
        assertTrue(report.getSummary().contains("No cycles found"));
    }

    // ── Disconnected graph ──────────────────────────────────────────

    @Test
    public void testDisconnectedGraphWithOneCyclicComponent() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        // Component 1: triangle
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        // Component 2: path
        addEdge(g, "X", "Y");
        addEdge(g, "Y", "Z");
        assertTrue(new CycleAnalyzer(g).hasCycles());
        assertEquals(3, new CycleAnalyzer(g).girth());
    }

    @Test
    public void testDisconnectedAcyclic() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "X", "Y");
        assertFalse(new CycleAnalyzer(g).hasCycles());
    }
}
