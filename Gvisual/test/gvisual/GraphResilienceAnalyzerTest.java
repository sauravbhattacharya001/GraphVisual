package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

import java.util.List;

/**
 * Unit tests for {@link GraphResilienceAnalyzer}.
 *
 * Tests cover all three attack strategies (degree, betweenness, random),
 * robustness index computation, summary/CSV export, and edge cases
 * (empty graphs, single nodes, disconnected graphs, star/complete topologies).
 */
public class GraphResilienceAnalyzerTest {

    private int edgeCounter = 0;

    private edge makeEdge(String v1, String v2) {
        edge e = new edge("f", v1, v2);
        e.setLabel("e" + (edgeCounter++));
        return e;
    }

    /**
     * Creates a simple triangle graph: A—B—C—A.
     */
    private Graph<String, edge> createTriangle() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        g.addVertex("B");
        g.addVertex("C");
        g.addEdge(makeEdge("A", "B"), "A", "B");
        g.addEdge(makeEdge("B", "C"), "B", "C");
        g.addEdge(makeEdge("C", "A"), "C", "A");
        return g;
    }

    /**
     * Creates a star graph: center connected to N leaves.
     */
    private Graph<String, edge> createStar(int leaves) {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("center");
        for (int i = 0; i < leaves; i++) {
            String leaf = "leaf" + i;
            g.addVertex(leaf);
            g.addEdge(makeEdge("center", leaf), "center", leaf);
        }
        return g;
    }

    /**
     * Creates a path graph: v0—v1—v2—...—vN.
     */
    private Graph<String, edge> createPath(int length) {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i <= length; i++) {
            g.addVertex("v" + i);
        }
        for (int i = 0; i < length; i++) {
            g.addEdge(makeEdge("v" + i, "v" + (i + 1)), "v" + i, "v" + (i + 1));
        }
        return g;
    }

    /**
     * Creates a complete graph K_n.
     */
    private Graph<String, edge> createComplete(int n) {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < n; i++) {
            g.addVertex("v" + i);
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                g.addEdge(makeEdge("v" + i, "v" + j), "v" + i, "v" + j);
            }
        }
        return g;
    }

    // ── analyze() and getters ──────────────────────────────────

    @Test(expected = IllegalStateException.class)
    public void testGetDegreeAttackBeforeAnalyze_Throws() {
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createTriangle());
        analyzer.getDegreeAttackCurve();
    }

    @Test(expected = IllegalStateException.class)
    public void testGetBetweennessAttackBeforeAnalyze_Throws() {
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createTriangle());
        analyzer.getBetweennessAttackCurve();
    }

    @Test(expected = IllegalStateException.class)
    public void testGetRandomAttackBeforeAnalyze_Throws() {
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createTriangle());
        analyzer.getRandomAttackCurve();
    }

    @Test
    public void testAnalyze_ProducesCurves() {
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createTriangle());
        analyzer.analyze();

        assertNotNull(analyzer.getDegreeAttackCurve());
        assertNotNull(analyzer.getBetweennessAttackCurve());
        assertNotNull(analyzer.getRandomAttackCurve());
        assertFalse(analyzer.getDegreeAttackCurve().isEmpty());
        assertFalse(analyzer.getBetweennessAttackCurve().isEmpty());
        assertFalse(analyzer.getRandomAttackCurve().isEmpty());
    }

    @Test
    public void testCurvesAreUnmodifiable() {
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createTriangle());
        analyzer.analyze();

        try {
            analyzer.getDegreeAttackCurve().clear();
            fail("Degree attack curve should be unmodifiable");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    // ── Degree Attack ──────────────────────────────────────────

    @Test
    public void testDegreeAttack_Triangle_FirstStepIsFullGraph() {
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createTriangle());
        analyzer.analyze();

        List<GraphResilienceAnalyzer.ResilienceStep> curve = analyzer.getDegreeAttackCurve();
        GraphResilienceAnalyzer.ResilienceStep first = curve.get(0);

        assertEquals("First step should have 0 nodes removed", 0, first.getNodesRemoved());
        assertEquals("Full graph has 3 nodes", 3, first.getTotalNodes());
        assertEquals("Full triangle LCC is 3", 3, first.getLargestComponentSize());
        assertEquals("Full triangle has 1 component", 1, first.getComponentCount());
        assertNull("First step should have no removed node", first.getRemovedNode());
    }

    @Test
    public void testDegreeAttack_Triangle_CurveLengthIsN_Plus1() {
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createTriangle());
        analyzer.analyze();

        List<GraphResilienceAnalyzer.ResilienceStep> curve = analyzer.getDegreeAttackCurve();
        // Triangle: initial state + 3 removals = 4 steps
        assertEquals(4, curve.size());
    }

    @Test
    public void testDegreeAttack_Triangle_LastStepHasZeroNodes() {
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createTriangle());
        analyzer.analyze();

        List<GraphResilienceAnalyzer.ResilienceStep> curve = analyzer.getDegreeAttackCurve();
        GraphResilienceAnalyzer.ResilienceStep last = curve.get(curve.size() - 1);

        assertEquals(0, last.getTotalNodes());
        assertEquals(0, last.getLargestComponentSize());
    }

    @Test
    public void testDegreeAttack_Star_RemovesCenterFirst() {
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createStar(5));
        analyzer.analyze();

        List<GraphResilienceAnalyzer.ResilienceStep> curve = analyzer.getDegreeAttackCurve();
        // Center has degree 5, all leaves have degree 1
        // After removing center, all leaves become isolated
        GraphResilienceAnalyzer.ResilienceStep afterCenter = curve.get(1);

        assertEquals("center", afterCenter.getRemovedNode());
        assertEquals("After removing center, LCC should be 1", 1, afterCenter.getLargestComponentSize());
        assertEquals("After removing center, 5 isolated components", 5, afterCenter.getComponentCount());
    }

    // ── Betweenness Attack ─────────────────────────────────────

    @Test
    public void testBetweennessAttack_Path_RemovesMiddleFirst() {
        // Path: v0—v1—v2—v3—v4 — v2 has highest betweenness (it's on most shortest paths)
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createPath(4));
        analyzer.analyze();

        List<GraphResilienceAnalyzer.ResilienceStep> curve = analyzer.getBetweennessAttackCurve();
        String firstRemoved = curve.get(1).getRemovedNode();

        assertEquals("v2 should be removed first (highest betweenness on path)", "v2", firstRemoved);
    }

    @Test
    public void testBetweennessAttack_ProducesDegradationCurve() {
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createPath(4));
        analyzer.analyze();

        List<GraphResilienceAnalyzer.ResilienceStep> curve = analyzer.getBetweennessAttackCurve();
        // Should have initial state + N removals
        assertEquals(6, curve.size()); // 5 nodes + initial

        // LCC should monotonically decrease (or stay same in some cases)
        for (int i = 1; i < curve.size(); i++) {
            assertTrue("LCC should not increase",
                    curve.get(i).getLargestComponentSize() <= curve.get(i - 1).getLargestComponentSize());
        }
    }

    // ── Random Attack ──────────────────────────────────────────

    @Test
    public void testRandomAttack_CurveLengthIsN_Plus1() {
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createTriangle());
        analyzer.analyze();

        List<GraphResilienceAnalyzer.ResilienceStep> curve = analyzer.getRandomAttackCurve();
        assertEquals(4, curve.size()); // 3 nodes + initial
    }

    @Test
    public void testRandomAttack_FirstStepHasFullGraph() {
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createTriangle());
        analyzer.analyze();

        GraphResilienceAnalyzer.ResilienceStep first = analyzer.getRandomAttackCurve().get(0);
        assertEquals(3, first.getLargestComponentSize());
    }

    @Test
    public void testRandomAttack_LastStepHasZeroLCC() {
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createTriangle());
        analyzer.analyze();

        List<GraphResilienceAnalyzer.ResilienceStep> curve = analyzer.getRandomAttackCurve();
        assertEquals(0, curve.get(curve.size() - 1).getLargestComponentSize());
    }

    @Test
    public void testSetRandomTrials() {
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createTriangle());
        analyzer.setRandomTrials(50);
        analyzer.analyze();

        // Should still produce valid results
        assertFalse(analyzer.getRandomAttackCurve().isEmpty());
    }

    @Test
    public void testSetRandomTrials_MinimumClampedTo1() {
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createTriangle());
        analyzer.setRandomTrials(0);
        analyzer.analyze();
        // Should not crash — clamped to 1
        assertFalse(analyzer.getRandomAttackCurve().isEmpty());
    }

    // ── Robustness Index ───────────────────────────────────────

    @Test
    public void testRobustnessIndex_InRange() {
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createTriangle());
        analyzer.analyze();

        double ri = analyzer.computeRobustnessIndex(analyzer.getDegreeAttackCurve());
        assertTrue("Robustness index should be >= 0", ri >= 0.0);
        assertTrue("Robustness index should be <= 1", ri <= 1.0);
    }

    @Test
    public void testRobustnessIndex_EmptyCurve_ReturnsZero() {
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createTriangle());
        assertEquals(0.0, analyzer.computeRobustnessIndex(null), 0.0001);
        assertEquals(0.0, analyzer.computeRobustnessIndex(List.of()), 0.0001);
    }

    @Test
    public void testRobustnessIndex_CompleteGraph_HigherThanStar() {
        // Complete graphs are more resilient than star graphs
        GraphResilienceAnalyzer completeAnalyzer = new GraphResilienceAnalyzer(createComplete(6));
        completeAnalyzer.analyze();
        double riComplete = completeAnalyzer.computeRobustnessIndex(completeAnalyzer.getDegreeAttackCurve());

        GraphResilienceAnalyzer starAnalyzer = new GraphResilienceAnalyzer(createStar(5));
        starAnalyzer.analyze();
        double riStar = starAnalyzer.computeRobustnessIndex(starAnalyzer.getDegreeAttackCurve());

        assertTrue("Complete graph should be more resilient than star graph (degree attack)",
                riComplete > riStar);
    }

    @Test
    public void testRobustnessIndex_Star_RandomHigherThanDegree() {
        // Star: random attack is more resilient than targeted degree attack
        // (because random is unlikely to hit the center first)
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createStar(10));
        analyzer.analyze();

        double riRandom = analyzer.computeRobustnessIndex(analyzer.getRandomAttackCurve());
        double riDegree = analyzer.computeRobustnessIndex(analyzer.getDegreeAttackCurve());

        assertTrue("Star: random attack should show higher resilience than degree attack",
                riRandom > riDegree);
    }

    // ── Global Efficiency ──────────────────────────────────────

    @Test
    public void testGlobalEfficiency_Triangle_IsOne() {
        // In a triangle, every pair is distance 1, so efficiency = 1.0
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createTriangle());
        analyzer.analyze();

        GraphResilienceAnalyzer.ResilienceStep first = analyzer.getDegreeAttackCurve().get(0);
        assertEquals("Triangle efficiency should be 1.0", 1.0, first.getGlobalEfficiency(), 0.01);
    }

    @Test
    public void testGlobalEfficiency_DecreasesWithRemovals() {
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createComplete(5));
        analyzer.analyze();

        List<GraphResilienceAnalyzer.ResilienceStep> curve = analyzer.getDegreeAttackCurve();
        double effBefore = curve.get(0).getGlobalEfficiency();
        double effAfter = curve.get(1).getGlobalEfficiency();

        assertTrue("Efficiency should decrease after node removal", effAfter <= effBefore);
    }

    // ── Edge Cases ─────────────────────────────────────────────

    @Test
    public void testSingleNode_NoCrash() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("alone");

        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(g);
        analyzer.analyze();

        List<GraphResilienceAnalyzer.ResilienceStep> curve = analyzer.getDegreeAttackCurve();
        assertEquals(2, curve.size()); // initial + 1 removal
        assertEquals(1, curve.get(0).getLargestComponentSize());
        assertEquals(0, curve.get(1).getLargestComponentSize());
    }

    @Test
    public void testTwoNodes_OneEdge() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        g.addVertex("B");
        g.addEdge(makeEdge("A", "B"), "A", "B");

        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(g);
        analyzer.analyze();

        List<GraphResilienceAnalyzer.ResilienceStep> curve = analyzer.getDegreeAttackCurve();
        assertEquals(3, curve.size()); // initial + 2 removals
        assertEquals(2, curve.get(0).getLargestComponentSize());
        assertEquals(1, curve.get(1).getLargestComponentSize());
        assertEquals(0, curve.get(2).getLargestComponentSize());
    }

    @Test
    public void testDisconnectedGraph() {
        // Two disconnected triangles
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addVertex("D"); g.addVertex("E"); g.addVertex("F");
        g.addEdge(makeEdge("A", "B"), "A", "B");
        g.addEdge(makeEdge("B", "C"), "B", "C");
        g.addEdge(makeEdge("C", "A"), "C", "A");
        g.addEdge(makeEdge("D", "E"), "D", "E");
        g.addEdge(makeEdge("E", "F"), "E", "F");
        g.addEdge(makeEdge("F", "D"), "F", "D");

        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(g);
        analyzer.analyze();

        GraphResilienceAnalyzer.ResilienceStep first = analyzer.getDegreeAttackCurve().get(0);
        assertEquals("LCC should be 3 (one triangle)", 3, first.getLargestComponentSize());
        assertEquals("Should have 2 components", 2, first.getComponentCount());
    }

    // ── Summary ────────────────────────────────────────────────

    @Test
    public void testGetSummary_ContainsExpectedSections() {
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createTriangle());
        analyzer.analyze();
        String summary = analyzer.getSummary();

        assertTrue(summary.contains("Network Resilience Analysis"));
        assertTrue(summary.contains("3 nodes"));
        assertTrue(summary.contains("3 edges"));
        assertTrue(summary.contains("Robustness Index"));
        assertTrue(summary.contains("Random attack"));
        assertTrue(summary.contains("Degree attack"));
        assertTrue(summary.contains("Betweenness attack"));
        assertTrue(summary.contains("Critical threshold"));
        assertTrue(summary.contains("Interpretation"));
    }

    @Test(expected = IllegalStateException.class)
    public void testGetSummary_BeforeAnalyze_Throws() {
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createTriangle());
        analyzer.getSummary();
    }

    // ── CSV Export ──────────────────────────────────────────────

    @Test
    public void testExportCSV_HasHeader() {
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createTriangle());
        analyzer.analyze();
        String csv = analyzer.exportCSV();

        assertTrue("CSV should start with header", csv.startsWith("step,nodes_removed,fraction_removed"));
        assertTrue("CSV should include degree columns", csv.contains("degree_lcc"));
        assertTrue("CSV should include betweenness columns", csv.contains("betweenness_lcc"));
        assertTrue("CSV should include random columns", csv.contains("random_lcc"));
    }

    @Test
    public void testExportCSV_CorrectLineCount() {
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createTriangle());
        analyzer.analyze();
        String csv = analyzer.exportCSV();

        String[] lines = csv.split("\n");
        // Header + (N+1) data lines = 1 + 4 = 5
        assertEquals("CSV should have header + N+1 data lines", 5, lines.length);
    }

    @Test(expected = IllegalStateException.class)
    public void testExportCSV_BeforeAnalyze_Throws() {
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createTriangle());
        analyzer.exportCSV();
    }

    // ── ResilienceStep toString ─────────────────────────────────

    @Test
    public void testResilienceStep_ToString() {
        GraphResilienceAnalyzer.ResilienceStep step =
                new GraphResilienceAnalyzer.ResilienceStep(2, 5, 3, 2, 0.75, "nodeX");
        String str = step.toString();

        assertTrue(str.contains("Step 2"));
        assertTrue(str.contains("nodeX"));
        assertTrue(str.contains("LCC=3"));
        assertTrue(str.contains("components=2"));
    }

    @Test
    public void testResilienceStep_ToString_NullRemoved() {
        GraphResilienceAnalyzer.ResilienceStep step =
                new GraphResilienceAnalyzer.ResilienceStep(0, 5, 5, 1, 1.0, null);
        String str = step.toString();

        assertTrue("Null removed node should show dash", str.contains("—"));
    }

    // ── Scale-free vs Homogeneous Interpretation ───────────────

    @Test
    public void testSummary_StarGraph_MentionsTargeted() {
        // Star is very vulnerable to targeted attack
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createStar(10));
        analyzer.analyze();
        String summary = analyzer.getSummary();

        assertTrue("Star should mention targeted vulnerability or moderate",
                summary.contains("targeted") || summary.contains("moderate"));
    }

    @Test
    public void testSummary_CompleteGraph_Homogeneous() {
        // Complete graph should show similar resilience to random and targeted
        GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(createComplete(5));
        analyzer.analyze();
        String summary = analyzer.getSummary();

        // In K5, all nodes have same degree — random and targeted behave similarly
        assertTrue("Complete graph should mention similar or moderate resilience",
                summary.contains("similar") || summary.contains("moderate") || summary.contains("homogeneous"));
    }
}
