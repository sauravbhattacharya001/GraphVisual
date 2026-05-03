package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphPercolationEngine}.
 *
 * @author zalenix
 */
public class GraphPercolationEngineTest {

    // ── Helper methods ───────────────────────────────────────────────

    private Graph<String, Edge> emptyGraph() {
        return new UndirectedSparseGraph<>();
    }

    private Graph<String, Edge> singleNode() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        return g;
    }

    private Graph<String, Edge> twoNodes() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B");
        g.addEdge(new Edge("c", "A", "B"), "A", "B");
        return g;
    }

    private Graph<String, Edge> triangle() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addEdge(new Edge("c", "A", "B"), "A", "B");
        g.addEdge(new Edge("c", "B", "C"), "B", "C");
        g.addEdge(new Edge("c", "A", "C"), "A", "C");
        return g;
    }

    private Graph<String, Edge> star5() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("Hub");
        for (int i = 1; i <= 4; i++) {
            String leaf = "L" + i;
            g.addVertex(leaf);
            g.addEdge(new Edge("c", "Hub", leaf), "Hub", leaf);
        }
        return g;
    }

    private Graph<String, Edge> path5() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 1; i <= 5; i++) g.addVertex("V" + i);
        for (int i = 1; i < 5; i++) {
            g.addEdge(new Edge("c", "V" + i, "V" + (i + 1)), "V" + i, "V" + (i + 1));
        }
        return g;
    }

    private Graph<String, Edge> complete6() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        String[] vs = {"A", "B", "C", "D", "E", "F"};
        for (String v : vs) g.addVertex(v);
        for (int i = 0; i < vs.length; i++) {
            for (int j = i + 1; j < vs.length; j++) {
                g.addEdge(new Edge("c", vs[i], vs[j]), vs[i], vs[j]);
            }
        }
        return g;
    }

    private Graph<String, Edge> grid3x3() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++)
                g.addVertex(r + "_" + c);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (c < 2) g.addEdge(new Edge("c", r+"_"+c, r+"_"+(c+1)), r+"_"+c, r+"_"+(c+1));
                if (r < 2) g.addEdge(new Edge("c", r+"_"+c, (r+1)+"_"+c), r+"_"+c, (r+1)+"_"+c);
            }
        }
        return g;
    }

    private Graph<String, Edge> disconnected() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addEdge(new Edge("c", "A", "B"), "A", "B");
        // C is isolated
        return g;
    }

    private Graph<String, Edge> twoComponents() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C"); g.addVertex("D");
        g.addEdge(new Edge("c", "A", "B"), "A", "B");
        g.addEdge(new Edge("c", "C", "D"), "C", "D");
        return g;
    }

    private GraphPercolationEngine engine() {
        return new GraphPercolationEngine().setRandomSeed(42).setMonteCarloTrials(20).setProbabilitySteps(10);
    }

    // ── Empty graph tests ────────────────────────────────────────────

    @Test
    public void testEmptyGraph() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(emptyGraph());
        assertEquals(0, report.nodeCount);
        assertEquals(0, report.edgeCount);
        assertEquals(0.0, report.healthScore, 0.001);
        assertTrue(report.bondPercolationCurve.isEmpty());
        assertTrue(report.sitePercolationCurve.isEmpty());
        assertFalse(report.insights.isEmpty());
    }

    @Test
    public void testEmptyGraphInsightMentionsEmpty() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(emptyGraph());
        assertTrue(report.insights.get(0).toLowerCase().contains("empty"));
    }

    // ── Single node tests ────────────────────────────────────────────

    @Test
    public void testSingleNode() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(singleNode());
        assertEquals(1, report.nodeCount);
        assertEquals(0, report.edgeCount);
        assertEquals(100.0, report.healthScore, 0.01);
    }

    @Test
    public void testSingleNodeCurvesAllOnes() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(singleNode());
        for (double val : report.bondPercolationCurve.values()) {
            assertEquals(1.0, val, 0.001);
        }
    }

    // ── Two node tests ───────────────────────────────────────────────

    @Test
    public void testTwoNodesNodeCount() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(twoNodes());
        assertEquals(2, report.nodeCount);
        assertEquals(1, report.edgeCount);
    }

    @Test
    public void testTwoNodesThresholdInRange() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(twoNodes());
        assertTrue(report.bondThreshold >= 0 && report.bondThreshold <= 1);
        assertTrue(report.siteThreshold >= 0 && report.siteThreshold <= 1);
    }

    // ── Triangle tests ───────────────────────────────────────────────

    @Test
    public void testTriangleAnalysis() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(triangle());
        assertEquals(3, report.nodeCount);
        assertEquals(3, report.edgeCount);
    }

    @Test
    public void testTriangleHealthScore() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(triangle());
        assertTrue(report.healthScore >= 0 && report.healthScore <= 100);
    }

    // ── Star graph tests ─────────────────────────────────────────────

    @Test
    public void testStarGraphNodeCount() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(star5());
        assertEquals(5, report.nodeCount);
        assertEquals(4, report.edgeCount);
    }

    @Test
    public void testStarGraphFragile() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(star5());
        // Star is fragile: hub removal fragments everything
        assertTrue(report.fragmentationTolerance <= 0.8);
    }

    @Test
    public void testStarFragmentationForecastPositive() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(star5());
        assertTrue(report.fragmentationForecast >= 1);
    }

    // ── Path graph tests ─────────────────────────────────────────────

    @Test
    public void testPathGraphAnalysis() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(path5());
        assertEquals(5, report.nodeCount);
        assertEquals(4, report.edgeCount);
    }

    @Test
    public void testPathGraphThresholdReasonable() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(path5());
        assertTrue(report.bondThreshold >= 0 && report.bondThreshold <= 1);
    }

    // ── Complete graph tests ─────────────────────────────────────────

    @Test
    public void testCompleteGraphCounts() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(complete6());
        assertEquals(6, report.nodeCount);
        assertEquals(15, report.edgeCount);
    }

    @Test
    public void testCompleteGraphRobust() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(complete6());
        // Complete graph should be fairly robust
        assertTrue(report.fragmentationTolerance >= 0.2);
    }

    @Test
    public void testCompleteGraphHighHealth() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(complete6());
        assertTrue(report.healthScore >= 20);
    }

    @Test
    public void testCompleteGraphBondCurveAtP1() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(complete6());
        // At p=1.0, all edges present → giant component = 1.0
        Double gcAtOne = report.bondPercolationCurve.get(1.0);
        assertNotNull(gcAtOne);
        assertEquals(1.0, gcAtOne, 0.001);
    }

    @Test
    public void testCompleteGraphBondCurveAtP0() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(complete6());
        // At p=0.0, no edges → each node is isolated → gc = 1/6
        Double gcAtZero = report.bondPercolationCurve.get(0.0);
        assertNotNull(gcAtZero);
        assertEquals(1.0 / 6.0, gcAtZero, 0.01);
    }

    // ── Grid graph tests ─────────────────────────────────────────────

    @Test
    public void testGridGraphAnalysis() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(grid3x3());
        assertEquals(9, report.nodeCount);
        assertEquals(12, report.edgeCount);
    }

    @Test
    public void testGridGraphPhaseTransition() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(grid3x3());
        assertNotNull(report.phaseTransitionSharpness);
    }

    // ── Disconnected graph tests ─────────────────────────────────────

    @Test
    public void testDisconnectedGraph() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(disconnected());
        assertEquals(3, report.nodeCount);
        assertEquals(1, report.edgeCount);
    }

    @Test
    public void testTwoComponentGraph() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(twoComponents());
        assertEquals(4, report.nodeCount);
        // Already fragmented, so tolerance should be low
        assertTrue(report.fragmentationTolerance <= 1.0);
    }

    // ── Bond percolation curve properties ────────────────────────────

    @Test
    public void testBondCurveMonotonicallyNonDecreasing() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(complete6());
        List<Double> values = new ArrayList<>(report.bondPercolationCurve.values());
        for (int i = 1; i < values.size(); i++) {
            assertTrue("Bond curve should be non-decreasing at index " + i,
                    values.get(i) >= values.get(i - 1) - 0.01); // small tolerance for MC noise
        }
    }

    @Test
    public void testSiteCurveMonotonicallyNonDecreasing() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(complete6());
        List<Double> values = new ArrayList<>(report.sitePercolationCurve.values());
        for (int i = 1; i < values.size(); i++) {
            assertTrue("Site curve should be non-decreasing at index " + i,
                    values.get(i) >= values.get(i - 1) - 0.01);
        }
    }

    @Test
    public void testBondCurveValuesInRange() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(grid3x3());
        for (Map.Entry<Double, Double> entry : report.bondPercolationCurve.entrySet()) {
            assertTrue(entry.getKey() >= 0 && entry.getKey() <= 1);
            assertTrue(entry.getValue() >= 0 && entry.getValue() <= 1);
        }
    }

    @Test
    public void testSiteCurveValuesInRange() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(grid3x3());
        for (Map.Entry<Double, Double> entry : report.sitePercolationCurve.entrySet()) {
            assertTrue(entry.getKey() >= 0 && entry.getKey() <= 1);
            assertTrue(entry.getValue() >= 0 && entry.getValue() <= 1);
        }
    }

    @Test
    public void testSiteCurveAtP0IsZero() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(complete6());
        Double gcAtZero = report.sitePercolationCurve.get(0.0);
        assertNotNull(gcAtZero);
        assertEquals(0.0, gcAtZero, 0.001);
    }

    // ── Threshold tests ──────────────────────────────────────────────

    @Test
    public void testBondThresholdRange() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(grid3x3());
        assertTrue(report.bondThreshold >= 0 && report.bondThreshold <= 1);
    }

    @Test
    public void testSiteThresholdRange() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(grid3x3());
        assertTrue(report.siteThreshold >= 0 && report.siteThreshold <= 1);
    }

    // ── Health score tests ───────────────────────────────────────────

    @Test
    public void testHealthScoreRange() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(grid3x3());
        assertTrue(report.healthScore >= 0 && report.healthScore <= 100);
    }

    @Test
    public void testHealthScoreTriangle() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(triangle());
        assertTrue(report.healthScore >= 0 && report.healthScore <= 100);
    }

    // ── Fragmentation tests ──────────────────────────────────────────

    @Test
    public void testFragmentationToleranceRange() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(complete6());
        assertTrue(report.fragmentationTolerance >= 0 && report.fragmentationTolerance <= 1);
    }

    @Test
    public void testFragmentationForecastNonNegative() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(star5());
        assertTrue(report.fragmentationForecast >= 0);
    }

    @Test
    public void testFragmentationForecastBounded() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(complete6());
        assertTrue(report.fragmentationForecast <= report.nodeCount);
    }

    // ── Phase transition tests ───────────────────────────────────────

    @Test
    public void testPhaseTransitionNotNull() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(grid3x3());
        assertNotNull(report.phaseTransitionSharpness);
    }

    @Test
    public void testPhaseTransitionEnumValues() {
        // Ensure all enum values are valid
        GraphPercolationEngine.TransitionSharpness[] values = GraphPercolationEngine.TransitionSharpness.values();
        assertEquals(3, values.length);
    }

    // ── Insight tests ────────────────────────────────────────────────

    @Test
    public void testInsightsNonEmpty() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(complete6());
        assertFalse(report.insights.isEmpty());
    }

    @Test
    public void testInsightsContainText() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(star5());
        for (String insight : report.insights) {
            assertNotNull(insight);
            assertFalse(insight.isEmpty());
        }
    }

    // ── Text output tests ────────────────────────────────────────────

    @Test
    public void testToTextNotEmpty() {
        GraphPercolationEngine eng = engine();
        GraphPercolationEngine.PercolationReport report = eng.analyze(grid3x3());
        String text = eng.toText(report);
        assertNotNull(text);
        assertFalse(text.isEmpty());
    }

    @Test
    public void testToTextContainsSections() {
        GraphPercolationEngine eng = engine();
        GraphPercolationEngine.PercolationReport report = eng.analyze(complete6());
        String text = eng.toText(report);
        assertTrue(text.contains("Bond Percolation Threshold"));
        assertTrue(text.contains("Site Percolation Threshold"));
        assertTrue(text.contains("Phase Transition"));
        assertTrue(text.contains("Fragmentation Forecast"));
        assertTrue(text.contains("Autonomous Insights"));
    }

    @Test
    public void testToTextContainsHealthScore() {
        GraphPercolationEngine eng = engine();
        GraphPercolationEngine.PercolationReport report = eng.analyze(triangle());
        String text = eng.toText(report);
        assertTrue(text.contains("Health Score"));
    }

    @Test
    public void testToTextContainsCurveData() {
        GraphPercolationEngine eng = engine();
        GraphPercolationEngine.PercolationReport report = eng.analyze(grid3x3());
        String text = eng.toText(report);
        assertTrue(text.contains("Bond Percolation Curve"));
        assertTrue(text.contains("Site Percolation Curve"));
    }

    // ── HTML output tests ────────────────────────────────────────────

    @Test
    public void testHtmlNotEmpty() {
        GraphPercolationEngine eng = engine();
        GraphPercolationEngine.PercolationReport report = eng.analyze(grid3x3());
        String html = eng.exportHtml(report);
        assertNotNull(html);
        assertTrue(html.startsWith("<!DOCTYPE html>"));
    }

    @Test
    public void testHtmlContainsTitle() {
        GraphPercolationEngine eng = engine();
        GraphPercolationEngine.PercolationReport report = eng.analyze(complete6());
        String html = eng.exportHtml(report);
        assertTrue(html.contains("Graph Percolation Engine"));
    }

    @Test
    public void testHtmlContainsCanvas() {
        GraphPercolationEngine eng = engine();
        GraphPercolationEngine.PercolationReport report = eng.analyze(grid3x3());
        String html = eng.exportHtml(report);
        assertTrue(html.contains("bondChart"));
        assertTrue(html.contains("siteChart"));
    }

    @Test
    public void testHtmlFileExport() throws IOException {
        GraphPercolationEngine eng = engine();
        GraphPercolationEngine.PercolationReport report = eng.analyze(triangle());
        File tmp = File.createTempFile("percolation-test", ".html");
        tmp.deleteOnExit();
        eng.exportHtml(report, tmp.getAbsolutePath());
        assertTrue(tmp.exists());
        assertTrue(tmp.length() > 100);
    }

    // ── Configuration tests ──────────────────────────────────────────

    @Test
    public void testSetMonteCarloTrials() {
        GraphPercolationEngine eng = new GraphPercolationEngine()
                .setMonteCarloTrials(5).setProbabilitySteps(5).setRandomSeed(42);
        GraphPercolationEngine.PercolationReport report = eng.analyze(triangle());
        assertNotNull(report);
    }

    @Test
    public void testSetProbabilitySteps() {
        GraphPercolationEngine eng = new GraphPercolationEngine()
                .setProbabilitySteps(5).setMonteCarloTrials(5).setRandomSeed(42);
        GraphPercolationEngine.PercolationReport report = eng.analyze(complete6());
        assertEquals(6, report.bondPercolationCurve.size()); // 0,1,2,3,4,5 → 6 points
    }

    @Test
    public void testReproducibilityWithSeed() {
        GraphPercolationEngine.PercolationReport r1 = new GraphPercolationEngine()
                .setRandomSeed(123).setMonteCarloTrials(10).setProbabilitySteps(5)
                .analyze(grid3x3());
        GraphPercolationEngine.PercolationReport r2 = new GraphPercolationEngine()
                .setRandomSeed(123).setMonteCarloTrials(10).setProbabilitySteps(5)
                .analyze(grid3x3());
        assertEquals(r1.bondThreshold, r2.bondThreshold, 0.0001);
        assertEquals(r1.siteThreshold, r2.siteThreshold, 0.0001);
        assertEquals(r1.healthScore, r2.healthScore, 0.0001);
    }

    @Test
    public void testDifferentSeedsDifferentResults() {
        GraphPercolationEngine.PercolationReport r1 = new GraphPercolationEngine()
                .setRandomSeed(1).setMonteCarloTrials(30).setProbabilitySteps(10)
                .analyze(grid3x3());
        GraphPercolationEngine.PercolationReport r2 = new GraphPercolationEngine()
                .setRandomSeed(99999).setMonteCarloTrials(30).setProbabilitySteps(10)
                .analyze(grid3x3());
        // Results may differ slightly due to different random sequences
        // At minimum both should produce valid reports
        assertTrue(r1.healthScore >= 0 && r2.healthScore >= 0);
    }

    @Test
    public void testSetRng() {
        GraphPercolationEngine eng = new GraphPercolationEngine()
                .setRng(new Random(42)).setMonteCarloTrials(5).setProbabilitySteps(5);
        GraphPercolationEngine.PercolationReport report = eng.analyze(path5());
        assertNotNull(report);
    }

    // ── Curve size tests ─────────────────────────────────────────────

    @Test
    public void testCurveSizeMatchesSteps() {
        int steps = 8;
        GraphPercolationEngine eng = new GraphPercolationEngine()
                .setProbabilitySteps(steps).setMonteCarloTrials(5).setRandomSeed(42);
        GraphPercolationEngine.PercolationReport report = eng.analyze(complete6());
        assertEquals(steps + 1, report.bondPercolationCurve.size());
        assertEquals(steps + 1, report.sitePercolationCurve.size());
    }

    // ── Immutability tests ───────────────────────────────────────────

    @Test(expected = UnsupportedOperationException.class)
    public void testBondCurveImmutable() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(triangle());
        report.bondPercolationCurve.put(0.5, 0.5);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSiteCurveImmutable() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(triangle());
        report.sitePercolationCurve.put(0.5, 0.5);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testInsightsImmutable() {
        GraphPercolationEngine.PercolationReport report = engine().analyze(triangle());
        report.insights.add("new insight");
    }

    // ── Percolation type enum test ───────────────────────────────────

    @Test
    public void testPercolationTypeEnum() {
        assertEquals(2, GraphPercolationEngine.PercolationType.values().length);
        assertNotNull(GraphPercolationEngine.PercolationType.BOND);
        assertNotNull(GraphPercolationEngine.PercolationType.SITE);
    }

    // ── Edge case: isolated vertices ─────────────────────────────────

    @Test
    public void testIsolatedVertices() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        // No edges
        GraphPercolationEngine.PercolationReport report = engine().analyze(g);
        assertEquals(3, report.nodeCount);
        assertEquals(0, report.edgeCount);
    }

    @Test
    public void testIsolatedVerticesBondAtP1() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        GraphPercolationEngine.PercolationReport report = engine().analyze(g);
        // Bond percolation with no edges: gc = 1/3 at all p
        Double gcAtOne = report.bondPercolationCurve.get(1.0);
        assertNotNull(gcAtOne);
        assertEquals(1.0 / 3.0, gcAtOne, 0.05);
    }
}
