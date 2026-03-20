package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link HamiltonianAnalyzer}.
 */
public class HamiltonianAnalyzerTest {

    private HamiltonianAnalyzer analyzer;
    private Graph<String, Edge> graph;
    private int edgeId;

    @Before
    public void setUp() {
        analyzer = new HamiltonianAnalyzer();
        graph = new UndirectedSparseGraph<>();
        edgeId = 0;
    }

    private Edge addEdge(String v1, String v2) {
        Edge e  new Edge("f", v1, v2);
        e.setWeight(1.0f);
        e.setLabel("e" + (edgeId++));
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        graph.addEdge(e, v1, v2);
        return e;
    }

    private void buildComplete(int n) {
        for (int i = 0; i < n; i++) {
            graph.addVertex("V" + i);
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                addEdge("V" + i, "V" + j);
            }
        }
    }

    private void buildCycle(int n) {
        for (int i = 0; i < n; i++) {
            addEdge("V" + i, "V" + ((i + 1) % n));
        }
    }

    private void buildPath(String... vertices) {
        for (int i = 0; i < vertices.length - 1; i++) {
            addEdge(vertices[i], vertices[i + 1]);
        }
    }

    // ── Dirac's Theorem ─────────────────────────────────────────────

    @Test
    public void testDiracComplete4() {
        buildComplete(4);
        HamiltonianAnalyzer.ConditionResult r = analyzer.checkDirac(graph);
        assertTrue(r.satisfied); // min degree 3 >= 4/2=2
    }

    @Test
    public void testDiracCycle4() {
        buildCycle(4);
        HamiltonianAnalyzer.ConditionResult r = analyzer.checkDirac(graph);
        assertTrue(r.satisfied); // min degree 2 >= 4/2=2
    }

    @Test
    public void testDiracPath() {
        buildPath("A", "B", "C", "D");
        HamiltonianAnalyzer.ConditionResult r = analyzer.checkDirac(graph);
        assertFalse(r.satisfied); // endpoints have degree 1
    }

    @Test
    public void testDiracTooSmall() {
        addEdge("A", "B");
        HamiltonianAnalyzer.ConditionResult r = analyzer.checkDirac(graph);
        assertFalse(r.satisfied);
    }

    // ── Ore's Theorem ───────────────────────────────────────────────

    @Test
    public void testOreComplete5() {
        buildComplete(5);
        HamiltonianAnalyzer.ConditionResult r = analyzer.checkOre(graph);
        assertTrue(r.satisfied);
        assertTrue(r.detail.contains("vacuously"));
    }

    @Test
    public void testOreCycle5() {
        buildCycle(5);
        HamiltonianAnalyzer.ConditionResult r = analyzer.checkOre(graph);
        assertFalse(r.satisfied); // deg(u)+deg(v)=4 < 5 for non-adjacent
    }

    @Test
    public void testOreSatisfied() {
        // Build graph where every non-adjacent pair has deg sum >= n
        buildComplete(4);
        addEdge("V0", "V4");
        addEdge("V1", "V4");
        addEdge("V2", "V4");
        addEdge("V3", "V4");
        // Now K5 — complete, so vacuously true
        HamiltonianAnalyzer.ConditionResult r = analyzer.checkOre(graph);
        assertTrue(r.satisfied);
    }

    @Test
    public void testOreTooSmall() {
        addEdge("A", "B");
        HamiltonianAnalyzer.ConditionResult r = analyzer.checkOre(graph);
        assertFalse(r.satisfied);
    }

    // ── Chvátal's Condition ─────────────────────────────────────────

    @Test
    public void testChvatalComplete() {
        buildComplete(5);
        HamiltonianAnalyzer.ConditionResult r = analyzer.checkChvatal(graph);
        assertTrue(r.satisfied);
    }

    @Test
    public void testChvatalPath() {
        buildPath("A", "B", "C", "D", "E");
        HamiltonianAnalyzer.ConditionResult r = analyzer.checkChvatal(graph);
        assertFalse(r.satisfied);
    }

    @Test
    public void testChvatalTooSmall() {
        addEdge("A", "B");
        HamiltonianAnalyzer.ConditionResult r = analyzer.checkChvatal(graph);
        assertFalse(r.satisfied);
    }

    // ── All conditions ──────────────────────────────────────────────

    @Test
    public void testAllConditions() {
        buildComplete(4);
        List<HamiltonianAnalyzer.ConditionResult> results = analyzer.checkAllConditions(graph);
        assertEquals(3, results.size());
        assertEquals("Dirac's Theorem", results.get(0).name);
        assertEquals("Ore's Theorem", results.get(1).name);
        assertEquals("Chvátal's Condition", results.get(2).name);
    }

    // ── Connectivity ────────────────────────────────────────────────

    @Test
    public void testConnectedGraph() {
        buildCycle(4);
        assertTrue(analyzer.isConnected(graph));
    }

    @Test
    public void testDisconnectedGraph() {
        addEdge("A", "B");
        addEdge("C", "D");
        assertFalse(analyzer.isConnected(graph));
    }

    @Test
    public void testEmptyGraphConnected() {
        assertTrue(analyzer.isConnected(graph));
    }

    // ── Hamiltonian Cycle ───────────────────────────────────────────

    @Test
    public void testCycleInComplete4() {
        buildComplete(4);
        List<String> cycle = analyzer.findHamiltonianCycle(graph);
        assertNotNull(cycle);
        assertEquals(5, cycle.size()); // 4 vertices + return to start
        assertEquals(cycle.get(0), cycle.get(cycle.size() - 1));
        assertEquals(4, new HashSet<>(cycle).size());
    }

    @Test
    public void testCycleInCycle5() {
        buildCycle(5);
        List<String> cycle = analyzer.findHamiltonianCycle(graph);
        assertNotNull(cycle);
        assertEquals(6, cycle.size());
        assertEquals(cycle.get(0), cycle.get(cycle.size() - 1));
    }

    @Test
    public void testNoCycleInPath() {
        buildPath("A", "B", "C", "D");
        List<String> cycle = analyzer.findHamiltonianCycle(graph);
        assertNull(cycle);
    }

    @Test
    public void testNoCycleDisconnected() {
        addEdge("A", "B");
        addEdge("C", "D");
        assertNull(analyzer.findHamiltonianCycle(graph));
    }

    @Test
    public void testNoCycleTooSmall() {
        addEdge("A", "B");
        assertNull(analyzer.findHamiltonianCycle(graph));
    }

    // ── Hamiltonian Path ────────────────────────────────────────────

    @Test
    public void testPathInComplete4() {
        buildComplete(4);
        List<String> path = analyzer.findHamiltonianPath(graph);
        assertNotNull(path);
        assertEquals(4, path.size());
        assertEquals(4, new HashSet<>(path).size());
    }

    @Test
    public void testPathInSimplePath() {
        buildPath("A", "B", "C", "D");
        List<String> path = analyzer.findHamiltonianPath(graph);
        assertNotNull(path);
        assertEquals(4, path.size());
    }

    @Test
    public void testPathSingleVertex() {
        graph.addVertex("A");
        List<String> path = analyzer.findHamiltonianPath(graph);
        assertNotNull(path);
        assertEquals(1, path.size());
    }

    @Test
    public void testPathEmptyGraph() {
        assertNull(analyzer.findHamiltonianPath(graph));
    }

    @Test
    public void testPathDisconnected() {
        addEdge("A", "B");
        addEdge("C", "D");
        assertNull(analyzer.findHamiltonianPath(graph));
    }

    // ── Hamiltonian Path between specific vertices ──────────────────

    @Test
    public void testPathBetweenVertices() {
        buildComplete(4);
        List<String> path = analyzer.findHamiltonianPath(graph, "V0", "V3");
        assertNotNull(path);
        assertEquals(4, path.size());
        assertEquals("V0", path.get(0));
        assertEquals("V3", path.get(path.size() - 1));
    }

    @Test
    public void testPathBetweenEndpoints() {
        buildPath("A", "B", "C", "D");
        List<String> path = analyzer.findHamiltonianPath(graph, "A", "D");
        assertNotNull(path);
        assertEquals("A", path.get(0));
        assertEquals("D", path.get(3));
    }

    @Test
    public void testPathBetweenSameVertex() {
        graph.addVertex("A");
        List<String> path = analyzer.findHamiltonianPath(graph, "A", "A");
        assertNotNull(path);
        assertEquals(1, path.size());
    }

    @Test
    public void testPathBetweenNonexistent() {
        addEdge("A", "B");
        assertNull(analyzer.findHamiltonianPath(graph, "A", "Z"));
    }

    @Test
    public void testPathBetweenImpossible() {
        // Path graph: only A-D and D-A Hamiltonian paths exist
        buildPath("A", "B", "C", "D");
        List<String> path = analyzer.findHamiltonianPath(graph, "B", "C");
        // B-A-...no way to visit all, or B-C-D-...can't reach A from D without going through visited
        // Actually: B→A then stuck (A has only B neighbor), so B→C→D then need A, D→A? no edge
        // So this should be null
        assertNull(path);
    }

    // ── Enumerate all cycles ────────────────────────────────────────

    @Test
    public void testEnumerateComplete4() {
        buildComplete(4);
        List<List<String>> cycles = analyzer.findAllHamiltonianCycles(graph);
        // K4 has 3 distinct Hamiltonian cycles (fixing start vertex)
        assertEquals(3, cycles.size());
        for (List<String> cycle : cycles) {
            assertEquals(5, cycle.size());
            assertEquals(cycle.get(0), cycle.get(4));
        }
    }

    @Test
    public void testEnumerateCycle4() {
        buildCycle(4);
        List<List<String>> cycles = analyzer.findAllHamiltonianCycles(graph);
        // C4 has 1 distinct Hamiltonian cycle (fixing first vertex, 2 directions = 2? no, backtrack finds both)
        // Actually fixing V0: V0-V1-V2-V3-V0 and V0-V3-V2-V1-V0 = 2 cycles
        // But if we treat them as undirected, we'd want 1, but our code finds both orderings
        assertTrue(cycles.size() >= 1);
    }

    @Test
    public void testEnumerateWithLimit() {
        buildComplete(5);
        List<List<String>> cycles = analyzer.findAllHamiltonianCycles(graph, 2);
        assertEquals(2, cycles.size());
    }

    @Test
    public void testEnumerateNoCycles() {
        buildPath("A", "B", "C");
        List<List<String>> cycles = analyzer.findAllHamiltonianCycles(graph);
        assertTrue(cycles.isEmpty());
    }

    @Test
    public void testEnumerateDisconnected() {
        addEdge("A", "B");
        addEdge("C", "D");
        List<List<String>> cycles = analyzer.findAllHamiltonianCycles(graph);
        assertTrue(cycles.isEmpty());
    }

    // ── Greedy heuristic ────────────────────────────────────────────

    @Test
    public void testGreedyPathComplete() {
        buildComplete(6);
        List<String> path = analyzer.greedyHamiltonianPath(graph);
        assertNotNull(path);
        assertEquals(6, path.size());
        assertEquals(6, new HashSet<>(path).size());
    }

    @Test
    public void testGreedyCycleComplete() {
        buildComplete(6);
        List<String> cycle = analyzer.greedyHamiltonianCycle(graph);
        assertNotNull(cycle);
        assertEquals(7, cycle.size());
        assertEquals(cycle.get(0), cycle.get(6));
    }

    @Test
    public void testGreedyPathSimple() {
        buildPath("A", "B", "C", "D", "E");
        List<String> path = analyzer.greedyHamiltonianPath(graph);
        assertNotNull(path);
        assertEquals(5, path.size());
    }

    @Test
    public void testGreedyPathSingleVertex() {
        graph.addVertex("X");
        List<String> path = analyzer.greedyHamiltonianPath(graph);
        assertNotNull(path);
        assertEquals(1, path.size());
    }

    @Test
    public void testGreedyPathEmpty() {
        assertNull(analyzer.greedyHamiltonianPath(graph));
    }

    // ── Degree analysis ─────────────────────────────────────────────

    @Test
    public void testDegreeStatsComplete4() {
        buildComplete(4);
        HamiltonianAnalyzer.DegreeStats<String> stats = analyzer.analyzeDegrees(graph);
        assertEquals(3, stats.minDegree);
        assertEquals(3, stats.maxDegree);
        assertEquals(3.0, stats.avgDegree, 0.01);
    }

    @Test
    public void testDegreeStatsPath() {
        buildPath("A", "B", "C");
        HamiltonianAnalyzer.DegreeStats<String> stats = analyzer.analyzeDegrees(graph);
        assertEquals(1, stats.minDegree);
        assertEquals(2, stats.maxDegree);
    }

    @Test
    public void testDegreeStatsEmpty() {
        HamiltonianAnalyzer.DegreeStats<String> stats = analyzer.analyzeDegrees(graph);
        assertEquals(0, stats.minDegree);
        assertEquals(0, stats.maxDegree);
    }

    // ── Full report ─────────────────────────────────────────────────

    @Test
    public void testReportComplete5() {
        buildComplete(5);
        HamiltonianAnalyzer.HamiltonianReport<String> report = analyzer.analyze(graph);
        assertEquals(5, report.vertexCount);
        assertEquals(10, report.edgeCount);
        assertTrue(report.connected);
        assertNotNull(report.hamiltonianCycle);
        assertNotNull(report.hamiltonianPath);
        assertTrue(report.totalCyclesFound > 0);
        assertTrue(report.usedExact);
        assertTrue(report.verdict.contains("Hamiltonian"));
    }

    @Test
    public void testReportCycle6() {
        buildCycle(6);
        HamiltonianAnalyzer.HamiltonianReport<String> report = analyzer.analyze(graph);
        assertEquals(6, report.vertexCount);
        assertTrue(report.connected);
        assertNotNull(report.hamiltonianCycle);
        assertNotNull(report.hamiltonianPath);
    }

    @Test
    public void testReportPathGraph() {
        buildPath("A", "B", "C", "D");
        HamiltonianAnalyzer.HamiltonianReport<String> report = analyzer.analyze(graph);
        assertTrue(report.connected);
        assertNull(report.hamiltonianCycle);
        assertNotNull(report.hamiltonianPath);
        assertTrue(report.verdict.contains("path"));
    }

    @Test
    public void testReportDisconnected() {
        addEdge("A", "B");
        addEdge("C", "D");
        HamiltonianAnalyzer.HamiltonianReport<String> report = analyzer.analyze(graph);
        assertFalse(report.connected);
        assertNull(report.hamiltonianCycle);
        assertNull(report.hamiltonianPath);
        assertTrue(report.verdict.contains("disconnected"));
    }

    @Test
    public void testReportToString() {
        buildComplete(4);
        HamiltonianAnalyzer.HamiltonianReport<String> report = analyzer.analyze(graph);
        String str = report.toString();
        assertTrue(str.contains("Hamiltonian Analysis Report"));
        assertTrue(str.contains("Dirac's Theorem"));
        assertTrue(str.contains("Ore's Theorem"));
        assertTrue(str.contains("Chvátal's Condition"));
    }

    // ── Petersen graph (Hamiltonian path but no cycle) ───────────────

    @Test
    public void testPetersenGraph() {
        // Petersen graph: 10 vertices, 15 edges, 3-regular
        // Has Hamiltonian path but NO Hamiltonian cycle
        // Outer cycle: 0-1-2-3-4-0
        for (int i = 0; i < 5; i++) {
            addEdge("O" + i, "O" + ((i + 1) % 5));
        }
        // Inner pentagram: 5-7-9-6-8-5
        addEdge("I0", "I2");
        addEdge("I2", "I4");
        addEdge("I4", "I1");
        addEdge("I1", "I3");
        addEdge("I3", "I0");
        // Spokes
        for (int i = 0; i < 5; i++) {
            addEdge("O" + i, "I" + i);
        }
        // 3-regular, 10 vertices
        assertEquals(10, graph.getVertexCount());
        assertEquals(15, graph.getEdgeCount());

        HamiltonianAnalyzer.HamiltonianReport<String> report = analyzer.analyze(graph);
        assertTrue(report.connected);
        assertNotNull(report.hamiltonianPath);
        assertNull(report.hamiltonianCycle); // Petersen is NOT Hamiltonian
    }

    // ── Star graph (no Hamiltonian path for n>3) ────────────────────

    @Test
    public void testStarGraph() {
        // Star K1,4: center + 4 leaves. No Hamiltonian cycle, no Ham path for n=5
        graph.addVertex("C");
        for (int i = 0; i < 4; i++) {
            addEdge("C", "L" + i);
        }
        assertNull(analyzer.findHamiltonianCycle(graph));
        // Actually K1,4 has no Hamiltonian path either (center must alternate)
        // For 5 vertices: C must connect all, but leaves don't connect to each other
        assertNull(analyzer.findHamiltonianPath(graph));
    }

    // ── K3,3 bipartite ──────────────────────────────────────────────

    @Test
    public void testK33Hamiltonian() {
        // K3,3 is Hamiltonian
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                addEdge("A" + i, "B" + j);
            }
        }
        List<String> cycle = analyzer.findHamiltonianCycle(graph);
        assertNotNull(cycle);
    }

    // ── Wheel graph ─────────────────────────────────────────────────

    @Test
    public void testWheelGraph() {
        // W5: center + 4-cycle, always Hamiltonian
        for (int i = 0; i < 4; i++) {
            addEdge("R" + i, "R" + ((i + 1) % 4));
            addEdge("C", "R" + i);
        }
        assertNotNull(analyzer.findHamiltonianCycle(graph));
    }

    // ── Edge cases ──────────────────────────────────────────────────

    @Test
    public void testTriangle() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        List<String> cycle = analyzer.findHamiltonianCycle(graph);
        assertNotNull(cycle);
        assertEquals(4, cycle.size());
    }

    @Test
    public void testSingleVertex() {
        graph.addVertex("X");
        HamiltonianAnalyzer.HamiltonianReport<String> report = analyzer.analyze(graph);
        assertNull(report.hamiltonianCycle);
        // Single vertex path
        assertNotNull(report.hamiltonianPath);
    }

    @Test
    public void testTwoVertices() {
        addEdge("A", "B");
        HamiltonianAnalyzer.HamiltonianReport<String> report = analyzer.analyze(graph);
        assertNull(report.hamiltonianCycle);
        assertNotNull(report.hamiltonianPath);
        assertEquals(2, report.hamiltonianPath.size());
    }

    @Test
    public void testConditionResultToString() {
        HamiltonianAnalyzer.ConditionResult r = new HamiltonianAnalyzer.ConditionResult(
                "Test", true, "it works");
        assertEquals("Test: YES — it works", r.toString());
    }

    @Test
    public void testConditionResultNo() {
        HamiltonianAnalyzer.ConditionResult r = new HamiltonianAnalyzer.ConditionResult(
                "Test", false, "it failed");
        assertEquals("Test: NO — it failed", r.toString());
    }
}
