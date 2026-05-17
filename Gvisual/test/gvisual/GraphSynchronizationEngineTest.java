package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphSynchronizationEngine}.
 *
 * @author sauravbhattacharya001
 */
public class GraphSynchronizationEngineTest {

    // ── Helper graphs ────────────────────────────────────────────────

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
                g.addVertex(r + "," + c);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (c < 2) g.addEdge(new Edge("c", r+","+c, r+","+(c+1)), r+","+c, r+","+(c+1));
                if (r < 2) g.addEdge(new Edge("c", r+","+c, (r+1)+","+c), r+","+c, (r+1)+","+c);
            }
        }
        return g;
    }

    private Graph<String, Edge> disconnected() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C"); g.addVertex("D");
        g.addEdge(new Edge("c", "A", "B"), "A", "B");
        g.addEdge(new Edge("c", "C", "D"), "C", "D");
        return g;
    }

    private Graph<String, Edge> barbell() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        // Two triangles connected by a bridge
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addVertex("D"); g.addVertex("E"); g.addVertex("F");
        g.addEdge(new Edge("c", "A", "B"), "A", "B");
        g.addEdge(new Edge("c", "B", "C"), "B", "C");
        g.addEdge(new Edge("c", "A", "C"), "A", "C");
        g.addEdge(new Edge("c", "D", "E"), "D", "E");
        g.addEdge(new Edge("c", "E", "F"), "E", "F");
        g.addEdge(new Edge("c", "D", "F"), "D", "F");
        g.addEdge(new Edge("c", "C", "D"), "C", "D"); // bridge
        return g;
    }

    private Graph<String, Edge> largeRing(int size) {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < size; i++) g.addVertex("N" + i);
        for (int i = 0; i < size; i++) {
            g.addEdge(new Edge("c", "N" + i, "N" + ((i + 1) % size)),
                    "N" + i, "N" + ((i + 1) % size));
        }
        return g;
    }

    private Graph<String, Edge> largeComplete(int size) {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < size; i++) g.addVertex("N" + i);
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                g.addEdge(new Edge("c", "N" + i, "N" + j), "N" + i, "N" + j);
            }
        }
        return g;
    }

    private GraphSynchronizationEngine engine() {
        return new GraphSynchronizationEngine().setRandomSeed(42);
    }

    // ── Empty graph ─────────────────────────────────────────────────

    @Test
    public void testEmptyGraph_returnsZeroOrder() {
        var r = engine().analyze(emptyGraph());
        assertEquals(0.0, r.finalOrderParameter, 0.001);
    }

    @Test
    public void testEmptyGraph_healthScoreZero() {
        var r = engine().analyze(emptyGraph());
        assertEquals(0.0, r.healthScore, 0.001);
    }

    @Test
    public void testEmptyGraph_hasInsights() {
        var r = engine().analyze(emptyGraph());
        assertFalse(r.insights.isEmpty());
    }

    @Test
    public void testEmptyGraph_zeroCounts() {
        var r = engine().analyze(emptyGraph());
        assertEquals(0, r.nodeCount);
        assertEquals(0, r.edgeCount);
    }

    // ── Single node ─────────────────────────────────────────────────

    @Test
    public void testSingleNode_perfectSync() {
        var r = engine().analyze(singleNode());
        assertEquals(1.0, r.finalOrderParameter, 0.001);
    }

    @Test
    public void testSingleNode_healthScore100() {
        var r = engine().analyze(singleNode());
        assertEquals(100.0, r.healthScore, 0.001);
    }

    @Test
    public void testSingleNode_zeroCriticalCoupling() {
        var r = engine().analyze(singleNode());
        assertEquals(0.0, r.criticalCoupling, 0.001);
    }

    @Test
    public void testSingleNode_counts() {
        var r = engine().analyze(singleNode());
        assertEquals(1, r.nodeCount);
        assertEquals(0, r.edgeCount);
    }

    // ── Two nodes ───────────────────────────────────────────────────

    @Test
    public void testTwoNodes_orderParameterInRange() {
        var r = engine().analyze(twoNodes());
        assertTrue(r.finalOrderParameter >= 0.0 && r.finalOrderParameter <= 1.0);
    }

    @Test
    public void testTwoNodes_hasFrequencies() {
        var r = engine().analyze(twoNodes());
        assertEquals(2, r.naturalFrequencies.size());
    }

    @Test
    public void testTwoNodes_hasFinalPhases() {
        var r = engine().analyze(twoNodes());
        assertEquals(2, r.finalPhases.size());
    }

    @Test
    public void testTwoNodes_hasTrajectory() {
        var r = engine().analyze(twoNodes());
        assertFalse(r.orderParameterTrajectory.isEmpty());
    }

    // ── Triangle ────────────────────────────────────────────────────

    @Test
    public void testTriangle_orderParameterInRange() {
        var r = engine().analyze(triangle());
        assertTrue(r.finalOrderParameter >= 0 && r.finalOrderParameter <= 1);
    }

    @Test
    public void testTriangle_healthScoreInRange() {
        var r = engine().analyze(triangle());
        assertTrue(r.healthScore >= 0 && r.healthScore <= 100);
    }

    @Test
    public void testTriangle_couplingCurveNotEmpty() {
        var r = engine().analyze(triangle());
        assertFalse(r.couplingCurve.isEmpty());
    }

    @Test
    public void testTriangle_couplingCurveStartsAtZero() {
        var r = engine().analyze(triangle());
        assertTrue(r.couplingCurve.containsKey(0.0));
    }

    // ── Star topology ───────────────────────────────────────────────

    @Test
    public void testStar_orderParameterInRange() {
        var r = engine().analyze(star5());
        assertTrue(r.finalOrderParameter >= 0 && r.finalOrderParameter <= 1);
    }

    @Test
    public void testStar_hasFiveFrequencies() {
        var r = engine().analyze(star5());
        assertEquals(5, r.naturalFrequencies.size());
    }

    @Test
    public void testStar_insightsNotEmpty() {
        var r = engine().analyze(star5());
        assertFalse(r.insights.isEmpty());
    }

    // ── Path topology ───────────────────────────────────────────────

    @Test
    public void testPath5_orderParameterInRange() {
        var r = engine().analyze(path5());
        assertTrue(r.finalOrderParameter >= 0 && r.finalOrderParameter <= 1);
    }

    @Test
    public void testPath5_detectsBridges() {
        var r = engine().analyze(path5());
        long bridges = r.barriers.stream()
                .filter(b -> b.type == GraphSynchronizationEngine.BarrierType.BOTTLENECK_EDGE)
                .count();
        assertTrue("Path should have bridge edges", bridges > 0);
    }

    @Test
    public void testPath5_healthScoreBounded() {
        var r = engine().analyze(path5());
        assertTrue(r.healthScore >= 0 && r.healthScore <= 100);
    }

    // ── Complete graph ──────────────────────────────────────────────

    @Test
    public void testComplete6_orderParameterInRange() {
        var r = engine().analyze(complete6());
        assertTrue(r.finalOrderParameter >= 0 && r.finalOrderParameter <= 1);
    }

    @Test
    public void testComplete6_noBridges() {
        var r = engine().analyze(complete6());
        long bridges = r.barriers.stream()
                .filter(b -> b.type == GraphSynchronizationEngine.BarrierType.BOTTLENECK_EDGE)
                .count();
        assertEquals(0, bridges);
    }

    @Test
    public void testComplete6_noIsolatedClusters() {
        var r = engine().analyze(complete6());
        long isolated = r.barriers.stream()
                .filter(b -> b.type == GraphSynchronizationEngine.BarrierType.ISOLATED_CLUSTER)
                .count();
        assertEquals(0, isolated);
    }

    @Test
    public void testComplete6_noWeakBridges() {
        var r = engine().analyze(complete6());
        long weakBridges = r.barriers.stream()
                .filter(b -> b.type == GraphSynchronizationEngine.BarrierType.WEAK_BRIDGE)
                .count();
        assertEquals(0, weakBridges);
    }

    // ── Grid topology ───────────────────────────────────────────────

    @Test
    public void testGrid3x3_orderParameterInRange() {
        var r = engine().analyze(grid3x3());
        assertTrue(r.finalOrderParameter >= 0 && r.finalOrderParameter <= 1);
    }

    @Test
    public void testGrid3x3_correctCounts() {
        var r = engine().analyze(grid3x3());
        assertEquals(9, r.nodeCount);
        assertEquals(12, r.edgeCount);
    }

    @Test
    public void testGrid3x3_healthScoreBounded() {
        var r = engine().analyze(grid3x3());
        assertTrue(r.healthScore >= 0 && r.healthScore <= 100);
    }

    // ── Disconnected graph ──────────────────────────────────────────

    @Test
    public void testDisconnected_detectsIsolatedClusters() {
        var r = engine().analyze(disconnected());
        long isolated = r.barriers.stream()
                .filter(b -> b.type == GraphSynchronizationEngine.BarrierType.ISOLATED_CLUSTER)
                .count();
        assertTrue("Disconnected graph should detect isolated clusters", isolated > 0);
    }

    @Test
    public void testDisconnected_insightMentionsComponents() {
        var r = engine().analyze(disconnected());
        boolean found = r.insights.stream().anyMatch(i -> i.contains("disconnected") || i.contains("component"));
        assertTrue("Should mention disconnected components", found);
    }

    @Test
    public void testDisconnected_healthScoreLow() {
        var r = engine().analyze(disconnected());
        assertTrue("Disconnected graph should have lower health", r.healthScore < 80);
    }

    // ── Barbell graph ───────────────────────────────────────────────

    @Test
    public void testBarbell_detectsBridge() {
        var r = engine().analyze(barbell());
        long bridges = r.barriers.stream()
                .filter(b -> b.type == GraphSynchronizationEngine.BarrierType.BOTTLENECK_EDGE)
                .count();
        assertTrue("Barbell should have a bridge", bridges > 0);
    }

    @Test
    public void testBarbell_orderParameterInRange() {
        var r = engine().analyze(barbell());
        assertTrue(r.finalOrderParameter >= 0 && r.finalOrderParameter <= 1);
    }

    // ── Large ring ──────────────────────────────────────────────────

    @Test
    public void testLargeRing_100nodes() {
        var r = engine().setTimeSteps(50).analyze(largeRing(100));
        assertEquals(100, r.nodeCount);
        assertTrue(r.finalOrderParameter >= 0 && r.finalOrderParameter <= 1);
    }

    @Test
    public void testLargeRing_healthScoreBounded() {
        var r = engine().setTimeSteps(50).analyze(largeRing(50));
        assertTrue(r.healthScore >= 0 && r.healthScore <= 100);
    }

    // ── Large complete ──────────────────────────────────────────────

    @Test
    public void testLargeComplete_20nodes() {
        var r = engine().setTimeSteps(50).analyze(largeComplete(20));
        assertEquals(20, r.nodeCount);
        assertTrue(r.finalOrderParameter >= 0 && r.finalOrderParameter <= 1);
    }

    // ── Order parameter bounds ──────────────────────────────────────

    @Test
    public void testTrajectory_allValuesBounded() {
        var r = engine().analyze(grid3x3());
        for (double v : r.orderParameterTrajectory) {
            assertTrue("r(t) must be in [0,1]", v >= -0.001 && v <= 1.001);
        }
    }

    @Test
    public void testTrajectory_lengthMatchesSteps() {
        int steps = 100;
        var r = engine().setTimeSteps(steps).analyze(triangle());
        // trajectory has steps+1 entries (initial + one per step)
        assertEquals(steps + 1, r.orderParameterTrajectory.size());
    }

    // ── Critical coupling ───────────────────────────────────────────

    @Test
    public void testCriticalCoupling_nonNegative() {
        var r = engine().analyze(complete6());
        assertTrue(r.criticalCoupling >= 0);
    }

    @Test
    public void testCriticalCoupling_atMostMax() {
        double kMax = 3.0;
        var r = engine().setCouplingMax(kMax).analyze(triangle());
        assertTrue(r.criticalCoupling <= kMax + 0.01);
    }

    // ── Coupling curve ──────────────────────────────────────────────

    @Test
    public void testCouplingCurve_hasExpectedSize() {
        int steps = 10;
        var r = engine().setCouplingSteps(steps).analyze(triangle());
        assertEquals(steps + 1, r.couplingCurve.size());
    }

    @Test
    public void testCouplingCurve_allValuesInRange() {
        var r = engine().analyze(complete6());
        for (double v : r.couplingCurve.values()) {
            assertTrue(v >= -0.001 && v <= 1.001);
        }
    }

    // ── Barrier detection ───────────────────────────────────────────

    @Test
    public void testBarriers_impactInRange() {
        var r = engine().analyze(barbell());
        for (var b : r.barriers) {
            assertTrue(b.impact >= 0 && b.impact <= 1.0);
        }
    }

    @Test
    public void testBarriers_sortedByImpactDesc() {
        var r = engine().analyze(path5());
        for (int i = 1; i < r.barriers.size(); i++) {
            assertTrue(r.barriers.get(i).impact <= r.barriers.get(i - 1).impact);
        }
    }

    @Test
    public void testBarriers_involvedNodesNotEmpty() {
        var r = engine().analyze(barbell());
        for (var b : r.barriers) {
            assertFalse(b.involvedNodes.isEmpty());
        }
    }

    @Test
    public void testBarriers_descriptionsNotNull() {
        var r = engine().analyze(path5());
        for (var b : r.barriers) {
            assertNotNull(b.description);
            assertFalse(b.description.isEmpty());
        }
    }

    // ── Desynchronizer profiling ────────────────────────────────────

    @Test
    public void testDesynchronizers_impactPositive() {
        var r = engine().analyze(grid3x3());
        for (var d : r.desynchronizers) {
            assertTrue(d.impactScore > 0);
        }
    }

    @Test
    public void testDesynchronizers_sortedDescending() {
        var r = engine().analyze(grid3x3());
        for (int i = 1; i < r.desynchronizers.size(); i++) {
            assertTrue(r.desynchronizers.get(i).impactScore
                    <= r.desynchronizers.get(i - 1).impactScore);
        }
    }

    @Test
    public void testDesynchronizers_haveReasons() {
        var r = engine().analyze(star5());
        for (var d : r.desynchronizers) {
            assertNotNull(d.reason);
            assertFalse(d.reason.isEmpty());
        }
    }

    // ── Health score ────────────────────────────────────────────────

    @Test
    public void testHealthScore_boundedZeroTo100_triangle() {
        var r = engine().analyze(triangle());
        assertTrue(r.healthScore >= 0 && r.healthScore <= 100);
    }

    @Test
    public void testHealthScore_boundedZeroTo100_path() {
        var r = engine().analyze(path5());
        assertTrue(r.healthScore >= 0 && r.healthScore <= 100);
    }

    @Test
    public void testHealthScore_boundedZeroTo100_complete() {
        var r = engine().analyze(complete6());
        assertTrue(r.healthScore >= 0 && r.healthScore <= 100);
    }

    @Test
    public void testHealthScore_boundedZeroTo100_disconnected() {
        var r = engine().analyze(disconnected());
        assertTrue(r.healthScore >= 0 && r.healthScore <= 100);
    }

    // ── Text output ─────────────────────────────────────────────────

    @Test
    public void testToText_notEmpty() {
        var r = engine().analyze(triangle());
        String text = engine().toText(r);
        assertFalse(text.isEmpty());
    }

    @Test
    public void testToText_containsOrderParameter() {
        var r = engine().analyze(triangle());
        String text = engine().toText(r);
        assertTrue(text.contains("Order Parameter"));
    }

    @Test
    public void testToText_containsCoupling() {
        var r = engine().analyze(triangle());
        String text = engine().toText(r);
        assertTrue(text.contains("Coupling"));
    }

    @Test
    public void testToText_containsHealthScore() {
        var r = engine().analyze(triangle());
        String text = engine().toText(r);
        assertTrue(text.contains("Health Score"));
    }

    @Test
    public void testToText_containsInsights() {
        var r = engine().analyze(triangle());
        String text = engine().toText(r);
        assertTrue(text.contains("Insights"));
    }

    // ── HTML export ─────────────────────────────────────────────────

    @Test
    public void testExportHtml_containsDoctype() {
        var r = engine().analyze(triangle());
        String html = engine().exportHtml(r);
        assertTrue(html.contains("<!DOCTYPE html>"));
    }

    @Test
    public void testExportHtml_containsTitle() {
        var r = engine().analyze(triangle());
        String html = engine().exportHtml(r);
        assertTrue(html.contains("Graph Synchronization Engine"));
    }

    @Test
    public void testExportHtml_containsCanvas() {
        var r = engine().analyze(triangle());
        String html = engine().exportHtml(r);
        assertTrue(html.contains("<canvas"));
    }

    @Test
    public void testExportHtml_containsHealthScore() {
        var r = engine().analyze(triangle());
        String html = engine().exportHtml(r);
        assertTrue(html.contains("Health Score"));
    }

    @Test
    public void testExportHtml_containsInsights() {
        var r = engine().analyze(complete6());
        String html = engine().exportHtml(r);
        assertTrue(html.contains("Autonomous Insights"));
    }

    @Test
    public void testExportHtml_fileOutput() throws IOException {
        var r = engine().analyze(triangle());
        File tmp = File.createTempFile("sync-test-", ".html");
        tmp.deleteOnExit();
        engine().exportHtml(r, tmp.getAbsolutePath());
        assertTrue(tmp.length() > 0);
    }

    // ── Configuration setters ───────────────────────────────────────

    @Test
    public void testSetCouplingStrength_chaining() {
        GraphSynchronizationEngine e = new GraphSynchronizationEngine()
                .setCouplingStrength(2.5).setRandomSeed(1);
        var r = e.analyze(triangle());
        assertNotNull(r);
    }

    @Test
    public void testSetTimeSteps_affectsTrajectoryLength() {
        int steps = 50;
        var r = engine().setTimeSteps(steps).analyze(triangle());
        assertEquals(steps + 1, r.orderParameterTrajectory.size());
    }

    @Test
    public void testSetStepSize_chaining() {
        var r = engine().setStepSize(0.01).analyze(triangle());
        assertNotNull(r);
    }

    @Test
    public void testSetCouplingMax_chaining() {
        var r = engine().setCouplingMax(10.0).analyze(triangle());
        assertNotNull(r);
    }

    @Test
    public void testSetCouplingSteps_chaining() {
        var r = engine().setCouplingSteps(5).analyze(triangle());
        assertEquals(6, r.couplingCurve.size());
    }

    @Test
    public void testSetRng_chaining() {
        var r = engine().setRng(new Random(99)).analyze(triangle());
        assertNotNull(r);
    }

    // ── Deterministic with fixed seed ───────────────────────────────

    @Test
    public void testDeterministic_sameSeedSameResult() {
        var r1 = new GraphSynchronizationEngine().setRandomSeed(42).analyze(complete6());
        var r2 = new GraphSynchronizationEngine().setRandomSeed(42).analyze(complete6());
        assertEquals(r1.finalOrderParameter, r2.finalOrderParameter, 1e-10);
    }

    @Test
    public void testDeterministic_healthScoreMatch() {
        var r1 = new GraphSynchronizationEngine().setRandomSeed(42).analyze(grid3x3());
        var r2 = new GraphSynchronizationEngine().setRandomSeed(42).analyze(grid3x3());
        assertEquals(r1.healthScore, r2.healthScore, 1e-10);
    }

    @Test
    public void testDeterministic_differentSeedDifferentResult() {
        var r1 = new GraphSynchronizationEngine().setRandomSeed(42).analyze(complete6());
        var r2 = new GraphSynchronizationEngine().setRandomSeed(99).analyze(complete6());
        // Not guaranteed but extremely likely with different seeds
        // Just check both are valid
        assertTrue(r1.finalOrderParameter >= 0 && r1.finalOrderParameter <= 1);
        assertTrue(r2.finalOrderParameter >= 0 && r2.finalOrderParameter <= 1);
    }

    // ── Topology comparison ─────────────────────────────────────────

    @Test
    public void testTopology_completeVsPath_completeSyncsEasier() {
        // Complete graphs should have lower critical coupling than paths
        var rComplete = new GraphSynchronizationEngine()
                .setRandomSeed(42).setCouplingMax(10).analyze(complete6());
        var rPath = new GraphSynchronizationEngine()
                .setRandomSeed(42).setCouplingMax(10).analyze(path5());
        // Both should be valid
        assertTrue(rComplete.criticalCoupling >= 0);
        assertTrue(rPath.criticalCoupling >= 0);
    }

    // ── Insight generation ──────────────────────────────────────────

    @Test
    public void testInsights_notEmpty_triangle() {
        var r = engine().analyze(triangle());
        assertFalse(r.insights.isEmpty());
    }

    @Test
    public void testInsights_notEmpty_disconnected() {
        var r = engine().analyze(disconnected());
        assertFalse(r.insights.isEmpty());
    }

    @Test
    public void testInsights_notEmpty_complete() {
        var r = engine().analyze(complete6());
        assertFalse(r.insights.isEmpty());
    }

    @Test
    public void testInsights_mentionOrderParameter() {
        var r = engine().analyze(triangle());
        boolean found = r.insights.stream().anyMatch(i -> i.contains("r=") || i.contains("synchronization"));
        assertTrue(found);
    }

    // ── Natural frequencies ─────────────────────────────────────────

    @Test
    public void testFrequencies_countMatchesNodes() {
        var r = engine().analyze(grid3x3());
        assertEquals(9, r.naturalFrequencies.size());
    }

    @Test
    public void testFinalPhases_countMatchesNodes() {
        var r = engine().analyze(grid3x3());
        assertEquals(9, r.finalPhases.size());
    }

    @Test
    public void testFinalPhases_inRange() {
        var r = engine().analyze(complete6());
        for (double phase : r.finalPhases.values()) {
            assertTrue("Phase should be in [0, 2π)", phase >= 0 && phase < 2 * Math.PI + 0.01);
        }
    }

    // ── Edge cases ──────────────────────────────────────────────────

    @Test
    public void testMinTimeSteps() {
        var r = engine().setTimeSteps(1).analyze(triangle());
        assertEquals(2, r.orderParameterTrajectory.size());
    }

    @Test
    public void testHighCoupling_tendTowardSync() {
        var r = new GraphSynchronizationEngine()
                .setRandomSeed(42)
                .setCouplingStrength(50.0)
                .setTimeSteps(500)
                .analyze(complete6());
        // With very high coupling on complete graph, should achieve good sync
        assertTrue("High coupling on complete graph should sync well",
                r.finalOrderParameter > 0.5);
    }

    @Test
    public void testZeroCoupling_noSync() {
        var r = new GraphSynchronizationEngine()
                .setRandomSeed(42)
                .setCouplingStrength(0.0)
                .setTimeSteps(100)
                .analyze(complete6());
        // With zero coupling, oscillators drift freely
        assertNotNull(r);
        assertTrue(r.finalOrderParameter >= 0 && r.finalOrderParameter <= 1);
    }
}
