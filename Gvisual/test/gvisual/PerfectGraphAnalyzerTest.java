package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for PerfectGraphAnalyzer — perfect graph detection, odd hole/antihole
 * search, and related graph class checks (bipartite, chordal).
 */
public class PerfectGraphAnalyzerTest {

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

    // ── Empty / trivial graphs ───────────────────────────────────

    @Test
    public void testEmptyGraph() {
        PerfectGraphAnalyzer.PerfectionResult r = PerfectGraphAnalyzer.analyze(graph);
        assertTrue(r.isPerfect());
        assertTrue(r.isWeaklyPerfect());
        assertEquals(0, r.getChromaticNumber());
        assertEquals(0, r.getCliqueNumber());
        assertTrue(r.isBipartite());
        assertTrue(r.isChordal());
    }

    @Test
    public void testSingleVertex() {
        graph.addVertex("A");
        PerfectGraphAnalyzer.PerfectionResult r = PerfectGraphAnalyzer.analyze(graph);
        assertTrue(r.isPerfect());
        assertEquals(1, r.getVertexCount());
        assertEquals(0, r.getEdgeCount());
    }

    @Test
    public void testSingleEdge() {
        addEdge("A", "B");
        PerfectGraphAnalyzer.PerfectionResult r = PerfectGraphAnalyzer.analyze(graph);
        assertTrue(r.isPerfect());
        assertTrue(r.isBipartite());
        assertEquals(2, r.getChromaticNumber());
        assertEquals(2, r.getCliqueNumber());
    }

    // ── Known perfect graph classes ──────────────────────────────

    @Test
    public void testBipartiteGraphIsPerfect() {
        // K_{2,3}: complete bipartite
        addEdge("A", "1");
        addEdge("A", "2");
        addEdge("A", "3");
        addEdge("B", "1");
        addEdge("B", "2");
        addEdge("B", "3");
        PerfectGraphAnalyzer.PerfectionResult r = PerfectGraphAnalyzer.analyze(graph);
        assertTrue(r.isPerfect());
        assertTrue(r.isBipartite());
        assertNull(r.getOddHole());
        assertNull(r.getOddAntihole());
    }

    @Test
    public void testChordalGraphIsPerfect() {
        // Complete graph K4 is chordal
        addEdge("A", "B");
        addEdge("A", "C");
        addEdge("A", "D");
        addEdge("B", "C");
        addEdge("B", "D");
        addEdge("C", "D");
        PerfectGraphAnalyzer.PerfectionResult r = PerfectGraphAnalyzer.analyze(graph);
        assertTrue(r.isPerfect());
        assertTrue(r.isChordal());
        assertEquals(r.getChromaticNumber(), r.getCliqueNumber());
    }

    @Test
    public void testEvenCycleIsPerfect() {
        // C4 is bipartite, hence perfect
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "A");
        PerfectGraphAnalyzer.PerfectionResult r = PerfectGraphAnalyzer.analyze(graph);
        assertTrue(r.isPerfect());
        assertTrue(r.isBipartite());
    }

    // ── Known imperfect graph ────────────────────────────────────

    @Test
    public void testOddCycleC5IsNotPerfect() {
        // C5: 5-cycle is the simplest odd hole
        addEdge("1", "2");
        addEdge("2", "3");
        addEdge("3", "4");
        addEdge("4", "5");
        addEdge("5", "1");
        PerfectGraphAnalyzer.PerfectionResult r = PerfectGraphAnalyzer.analyze(graph);
        assertFalse("C5 should not be perfect", r.isPerfect());
        // Should find the odd hole
        assertNotNull("Should detect odd hole in C5", r.getOddHole());
        assertEquals(5, r.getOddHole().size());
    }

    @Test
    public void testOddCycleC7IsNotPerfect() {
        // C7: 7-cycle
        for (int i = 1; i <= 7; i++) {
            addEdge(String.valueOf(i), String.valueOf(i % 7 + 1));
        }
        PerfectGraphAnalyzer.PerfectionResult r = PerfectGraphAnalyzer.analyze(graph);
        assertFalse("C7 should not be perfect", r.isPerfect());
    }

    // ── Weak perfection ──────────────────────────────────────────

    @Test
    public void testWeakPerfectionMatchesChromaticAndClique() {
        // Triangle: chi=3, omega=3 → weakly perfect
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        PerfectGraphAnalyzer.PerfectionResult r = PerfectGraphAnalyzer.analyze(graph);
        assertTrue(r.isWeaklyPerfect());
        assertEquals(r.getChromaticNumber(), r.getCliqueNumber());
    }

    // ── Report generation ────────────────────────────────────────

    @Test
    public void testReportContainsKeyInfo() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        PerfectGraphAnalyzer.PerfectionResult r = PerfectGraphAnalyzer.analyze(graph);
        String report = PerfectGraphAnalyzer.generateReport(r);
        assertTrue(report.contains("PERFECT GRAPH ANALYSIS"));
        assertTrue(report.contains("3 vertices"));
        assertTrue(report.contains("3 edges"));
        assertTrue(report.contains("YES"));
    }

    @Test
    public void testReportForImperfectGraph() {
        // C5
        addEdge("1", "2");
        addEdge("2", "3");
        addEdge("3", "4");
        addEdge("4", "5");
        addEdge("5", "1");
        PerfectGraphAnalyzer.PerfectionResult r = PerfectGraphAnalyzer.analyze(graph);
        String report = PerfectGraphAnalyzer.generateReport(r);
        assertTrue(report.contains("NO"));
        assertTrue(report.contains("Odd Hole"));
    }

    // ── Path graph (tree) is chordal and perfect ─────────────────

    @Test
    public void testPathIsChordalAndPerfect() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "E");
        PerfectGraphAnalyzer.PerfectionResult r = PerfectGraphAnalyzer.analyze(graph);
        assertTrue(r.isPerfect());
        // Path is bipartite
        assertTrue(r.isBipartite());
    }

    // ── Disconnected graph ───────────────────────────────────────

    @Test
    public void testDisconnectedBipartiteIsPerfect() {
        addEdge("A", "B");
        graph.addVertex("C");
        PerfectGraphAnalyzer.PerfectionResult r = PerfectGraphAnalyzer.analyze(graph);
        assertTrue(r.isPerfect());
        assertTrue(r.isBipartite());
    }
}
