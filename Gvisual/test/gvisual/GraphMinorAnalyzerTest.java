package gvisual;

import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for {@link GraphMinorAnalyzer}.
 */
public class GraphMinorAnalyzerTest {

    // ── Helpers ─────────────────────────────────────────────────────────

    private Graph<String, edge> makePath(int n) {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < n; i++) g.addVertex("v" + i);
        for (int i = 0; i < n - 1; i++)
            g.addEdge(new edge("e" + i, "v" + i, "v" + (i + 1)), "v" + i, "v" + (i + 1));
        return g;
    }

    private Graph<String, edge> makeCycle(int n) {
        Graph<String, edge> g = makePath(n);
        g.addEdge(new edge("ec", "v" + (n - 1), "v0"), "v" + (n - 1), "v0");
        return g;
    }

    private Graph<String, edge> makeComplete(int n) {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < n; i++) g.addVertex("v" + i);
        int id = 0;
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                g.addEdge(new edge("e" + id++, "v" + i, "v" + j), "v" + i, "v" + j);
        return g;
    }

    private Graph<String, edge> makeK33() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < 3; i++) { g.addVertex("a" + i); g.addVertex("b" + i); }
        int id = 0;
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                g.addEdge(new edge("e" + id++, "a" + i, "b" + j), "a" + i, "b" + j);
        return g;
    }

    private Graph<String, edge> makeEmpty(int n) {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < n; i++) g.addVertex("v" + i);
        return g;
    }

    private Graph<String, edge> makePetersen() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < 10; i++) g.addVertex("v" + i);
        // Outer cycle
        for (int i = 0; i < 5; i++)
            g.addEdge(new edge("o" + i, "v" + i, "v" + ((i + 1) % 5)), "v" + i, "v" + ((i + 1) % 5));
        // Inner pentagram
        for (int i = 0; i < 5; i++)
            g.addEdge(new edge("i" + i, "v" + (i + 5), "v" + ((i + 2) % 5 + 5)), "v" + (i + 5), "v" + ((i + 2) % 5 + 5));
        // Spokes
        for (int i = 0; i < 5; i++)
            g.addEdge(new edge("s" + i, "v" + i, "v" + (i + 5)), "v" + i, "v" + (i + 5));
        return g;
    }

    // ── Constructor ─────────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraph() {
        new GraphMinorAnalyzer(null);
    }

    // ── Copy ────────────────────────────────────────────────────────────

    @Test
    public void testCopyPreservesStructure() {
        Graph<String, edge> g = makeComplete(4);
        Graph<String, edge> copy = GraphMinorAnalyzer.copyGraph(g);
        assertEquals(g.getVertexCount(), copy.getVertexCount());
        assertEquals(g.getEdgeCount(), copy.getEdgeCount());
    }

    @Test
    public void testCopyIsIndependent() {
        Graph<String, edge> g = makeComplete(3);
        Graph<String, edge> copy = GraphMinorAnalyzer.copyGraph(g);
        copy.removeVertex("v0");
        assertEquals(3, g.getVertexCount());
        assertEquals(2, copy.getVertexCount());
    }

    // ── Vertex deletion ─────────────────────────────────────────────────

    @Test
    public void testDeleteVertex() {
        GraphMinorAnalyzer a = new GraphMinorAnalyzer(makeComplete(4));
        Graph<String, edge> result = a.deleteVertex("v0");
        assertEquals(3, result.getVertexCount());
        assertFalse(result.containsVertex("v0"));
        // K4 - v0 = K3
        assertEquals(3, result.getEdgeCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeleteVertexNotFound() {
        new GraphMinorAnalyzer(makeComplete(3)).deleteVertex("nonexistent");
    }

    @Test
    public void testDeleteMultipleVertices() {
        GraphMinorAnalyzer a = new GraphMinorAnalyzer(makeComplete(5));
        Graph<String, edge> result = a.deleteVertices(Arrays.asList("v0", "v1"));
        assertEquals(3, result.getVertexCount());
        assertEquals(3, result.getEdgeCount()); // K3
    }

    // ── Edge deletion ───────────────────────────────────────────────────

    @Test
    public void testDeleteEdge() {
        GraphMinorAnalyzer a = new GraphMinorAnalyzer(makeComplete(3));
        Graph<String, edge> result = a.deleteEdge("v0", "v1");
        assertEquals(3, result.getVertexCount());
        assertEquals(2, result.getEdgeCount());
        assertNull(result.findEdge("v0", "v1"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeleteEdgeNotFound() {
        new GraphMinorAnalyzer(makePath(3)).deleteEdge("v0", "v2");
    }

    // ── Edge contraction ────────────────────────────────────────────────

    @Test
    public void testContractEdgeReducesVertices() {
        GraphMinorAnalyzer a = new GraphMinorAnalyzer(makePath(3));
        // v0--v1--v2, contract v0-v1
        Graph<String, edge> result = a.contractEdge("v0", "v1");
        assertEquals(2, result.getVertexCount());
        assertTrue(result.containsVertex("v0"));
        assertTrue(result.containsVertex("v2"));
        assertNotNull(result.findEdge("v0", "v2"));
    }

    @Test
    public void testContractEdgeInTriangle() {
        GraphMinorAnalyzer a = new GraphMinorAnalyzer(makeComplete(3));
        Graph<String, edge> result = a.contractEdge("v0", "v1");
        // K3 contract → K2 (v0 and v2 with one edge)
        assertEquals(2, result.getVertexCount());
        assertEquals(1, result.getEdgeCount());
    }

    @Test
    public void testContractEdgeK4ToK3() {
        GraphMinorAnalyzer a = new GraphMinorAnalyzer(makeComplete(4));
        Graph<String, edge> result = a.contractEdge("v0", "v1");
        // K4 contract one edge → K3
        assertEquals(3, result.getVertexCount());
        assertEquals(3, result.getEdgeCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testContractNonExistentEdge() {
        new GraphMinorAnalyzer(makePath(3)).contractEdge("v0", "v2");
    }

    // ── Minor sequence ──────────────────────────────────────────────────

    @Test
    public void testMinorSequence() {
        // K4: delete v3, contract v0-v1 → K2
        GraphMinorAnalyzer a = new GraphMinorAnalyzer(makeComplete(4));
        List<GraphMinorAnalyzer.MinorOp> ops = Arrays.asList(
            GraphMinorAnalyzer.MinorOp.deleteVertex("v3"),
            GraphMinorAnalyzer.MinorOp.contract("v0", "v1")
        );
        Graph<String, edge> result = a.applySequence(ops);
        assertEquals(2, result.getVertexCount());
    }

    @Test
    public void testMinorOpToString() {
        assertEquals("delete(v0)", GraphMinorAnalyzer.MinorOp.deleteVertex("v0").toString());
        assertEquals("delEdge(v0,v1)", GraphMinorAnalyzer.MinorOp.deleteEdge("v0", "v1").toString());
        assertEquals("contract(v0,v1)", GraphMinorAnalyzer.MinorOp.contract("v0", "v1").toString());
    }

    // ── Forest test ─────────────────────────────────────────────────────

    @Test
    public void testForestPath() {
        assertTrue(new GraphMinorAnalyzer(makePath(5)).isForest());
    }

    @Test
    public void testForestEmptyGraph() {
        assertTrue(new GraphMinorAnalyzer(makeEmpty(3)).isForest());
    }

    @Test
    public void testNotForestCycle() {
        assertFalse(new GraphMinorAnalyzer(makeCycle(4)).isForest());
    }

    @Test
    public void testNotForestComplete() {
        assertFalse(new GraphMinorAnalyzer(makeComplete(4)).isForest());
    }

    // ── Outerplanar test ────────────────────────────────────────────────

    @Test
    public void testOuterplanarCycle() {
        assertTrue(new GraphMinorAnalyzer(makeCycle(5)).isOuterplanar());
    }

    @Test
    public void testOuterplanarPath() {
        assertTrue(new GraphMinorAnalyzer(makePath(4)).isOuterplanar());
    }

    @Test
    public void testNotOuterplanarK4() {
        assertFalse(new GraphMinorAnalyzer(makeComplete(4)).isOuterplanar());
    }

    // ── Planarity heuristic ─────────────────────────────────────────────

    @Test
    public void testPlanarCycle() {
        assertTrue(new GraphMinorAnalyzer(makeCycle(6)).isPlanarHeuristic());
    }

    @Test
    public void testPlanarK4() {
        assertTrue(new GraphMinorAnalyzer(makeComplete(4)).isPlanarHeuristic());
    }

    @Test
    public void testNonPlanarK5() {
        assertFalse(new GraphMinorAnalyzer(makeComplete(5)).isPlanarHeuristic());
    }

    @Test
    public void testNonPlanarK33() {
        assertFalse(new GraphMinorAnalyzer(makeK33()).isPlanarHeuristic());
    }

    @Test
    public void testPlanarSmallGraph() {
        assertTrue(new GraphMinorAnalyzer(makePath(3)).isPlanarHeuristic());
    }

    // ── K5 minor ────────────────────────────────────────────────────────

    @Test
    public void testK5MinorInK5() {
        assertTrue(new GraphMinorAnalyzer(makeComplete(5)).hasK5Minor());
    }

    @Test
    public void testNoK5MinorInK4() {
        assertFalse(new GraphMinorAnalyzer(makeComplete(4)).hasK5Minor());
    }

    @Test
    public void testK5MinorInK6() {
        assertTrue(new GraphMinorAnalyzer(makeComplete(6)).hasK5Minor());
    }

    // ── K3,3 minor ──────────────────────────────────────────────────────

    @Test
    public void testK33MinorInK33() {
        assertTrue(new GraphMinorAnalyzer(makeK33()).hasK33Minor());
    }

    @Test
    public void testNoK33MinorInPath() {
        assertFalse(new GraphMinorAnalyzer(makePath(6)).hasK33Minor());
    }

    @Test
    public void testNoK33MinorInSmallGraph() {
        assertFalse(new GraphMinorAnalyzer(makeComplete(3)).hasK33Minor());
    }

    // ── Hadwiger number ─────────────────────────────────────────────────

    @Test
    public void testHadwigerEmpty() {
        assertEquals(0, new GraphMinorAnalyzer(new UndirectedSparseGraph<String, edge>()).hadwigerNumber());
    }

    @Test
    public void testHadwigerIsolated() {
        assertEquals(1, new GraphMinorAnalyzer(makeEmpty(5)).hadwigerNumber());
    }

    @Test
    public void testHadwigerK4() {
        assertEquals(4, new GraphMinorAnalyzer(makeComplete(4)).hadwigerNumber());
    }

    @Test
    public void testHadwigerK5() {
        assertEquals(5, new GraphMinorAnalyzer(makeComplete(5)).hadwigerNumber());
    }

    @Test
    public void testHadwigerPath() {
        // Path of 4: can contract to K2, might get K3 with triangle
        int h = new GraphMinorAnalyzer(makePath(4)).hadwigerNumber();
        assertTrue(h >= 2);
    }

    // ── Contraction degeneracy ──────────────────────────────────────────

    @Test
    public void testContractionDegeneracyConnected() {
        assertEquals(1, new GraphMinorAnalyzer(makeComplete(5)).contractionDegeneracy());
    }

    @Test
    public void testContractionDegeneracyDisconnected() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("a"); g.addVertex("b"); g.addVertex("c");
        g.addEdge(new edge("e0", "a", "b"), "a", "b");
        // c is isolated → 2 components
        assertEquals(2, new GraphMinorAnalyzer(g).contractionDegeneracy());
    }

    @Test
    public void testContractionDegeneracyEmpty() {
        assertEquals(0, new GraphMinorAnalyzer(new UndirectedSparseGraph<String, edge>()).contractionDegeneracy());
    }

    // ── Subdivide edge ──────────────────────────────────────────────────

    @Test
    public void testSubdivideEdge() {
        GraphMinorAnalyzer a = new GraphMinorAnalyzer(makePath(2));
        Graph<String, edge> result = a.subdivideEdge("v0", "v1", "mid");
        assertEquals(3, result.getVertexCount());
        assertEquals(2, result.getEdgeCount());
        assertNull(result.findEdge("v0", "v1"));
        assertNotNull(result.findEdge("v0", "mid"));
        assertNotNull(result.findEdge("mid", "v1"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSubdivideNonExistentEdge() {
        new GraphMinorAnalyzer(makePath(3)).subdivideEdge("v0", "v2", "mid");
    }

    // ── Complete minor ──────────────────────────────────────────────────

    @Test
    public void testCompleteMinorK2() {
        assertTrue(new GraphMinorAnalyzer(makePath(2)).hasCompleteMinor(2));
    }

    @Test
    public void testCompleteMinorK1() {
        assertTrue(new GraphMinorAnalyzer(makeEmpty(1)).hasCompleteMinor(1));
    }

    @Test
    public void testNoCompleteMinorTooFewVertices() {
        assertFalse(new GraphMinorAnalyzer(makePath(2)).hasCompleteMinor(5));
    }

    // ── Report ──────────────────────────────────────────────────────────

    @Test
    public void testReportNotNull() {
        String report = new GraphMinorAnalyzer(makeComplete(4)).generateReport();
        assertNotNull(report);
        assertTrue(report.contains("Graph Minor Analysis Report"));
        assertTrue(report.contains("Forest"));
        assertTrue(report.contains("Planar"));
        assertTrue(report.contains("Hadwiger"));
    }

    @Test
    public void testReportSmallGraph() {
        String report = new GraphMinorAnalyzer(makePath(3)).generateReport();
        assertTrue(report.contains("YES")); // Forest should be YES
    }

    @Test
    public void testReportPetersen() {
        String report = new GraphMinorAnalyzer(makePetersen()).generateReport();
        assertNotNull(report);
        assertTrue(report.length() > 100);
    }
}
