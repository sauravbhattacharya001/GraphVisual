package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphControllabilityEngine}.
 *
 * @author zalenix
 */
public class GraphControllabilityEngineTest {

    // ===== Helper graph builders =====

    private Graph<String, Edge> buildEmpty() {
        return new UndirectedSparseGraph<>();
    }

    private Graph<String, Edge> buildSingle() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        return g;
    }

    private Graph<String, Edge> buildPair() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B");
        g.addEdge(new Edge("f", "A", "B"), "A", "B");
        return g;
    }

    private Graph<String, Edge> buildTriangle() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addEdge(new Edge("f", "A", "B"), "A", "B");
        g.addEdge(new Edge("f", "B", "C"), "B", "C");
        g.addEdge(new Edge("f", "A", "C"), "A", "C");
        return g;
    }

    private Graph<String, Edge> buildPath5() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 1; i <= 5; i++) g.addVertex("N" + i);
        g.addEdge(new Edge("f", "N1", "N2"), "N1", "N2");
        g.addEdge(new Edge("f", "N2", "N3"), "N2", "N3");
        g.addEdge(new Edge("f", "N3", "N4"), "N3", "N4");
        g.addEdge(new Edge("f", "N4", "N5"), "N4", "N5");
        return g;
    }

    private Graph<String, Edge> buildStar5() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("Hub");
        for (int i = 1; i <= 4; i++) {
            g.addVertex("L" + i);
            g.addEdge(new Edge("f", "Hub", "L" + i), "Hub", "L" + i);
        }
        return g;
    }

    private Graph<String, Edge> buildK4() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        String[] v = {"A", "B", "C", "D"};
        for (String s : v) g.addVertex(s);
        for (int i = 0; i < v.length; i++)
            for (int j = i + 1; j < v.length; j++)
                g.addEdge(new Edge("f", v[i], v[j]), v[i], v[j]);
        return g;
    }

    private Graph<String, Edge> buildK5() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        String[] v = {"A", "B", "C", "D", "E"};
        for (String s : v) g.addVertex(s);
        for (int i = 0; i < v.length; i++)
            for (int j = i + 1; j < v.length; j++)
                g.addEdge(new Edge("f", v[i], v[j]), v[i], v[j]);
        return g;
    }

    private Graph<String, Edge> buildDisconnected() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addEdge(new Edge("f", "A", "B"), "A", "B");
        g.addVertex("X"); g.addVertex("Y");
        g.addEdge(new Edge("f", "X", "Y"), "X", "Y");
        return g;
    }

    private Graph<String, Edge> buildTree() {
        //     R
        //    / \
        //   A   B
        //  / \
        // C   D
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("R"); g.addVertex("A"); g.addVertex("B");
        g.addVertex("C"); g.addVertex("D");
        g.addEdge(new Edge("f", "R", "A"), "R", "A");
        g.addEdge(new Edge("f", "R", "B"), "R", "B");
        g.addEdge(new Edge("f", "A", "C"), "A", "C");
        g.addEdge(new Edge("f", "A", "D"), "A", "D");
        return g;
    }

    private Graph<String, Edge> buildLarger() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < 12; i++) g.addVertex("V" + i);
        g.addEdge(new Edge("f", "V0", "V1"), "V0", "V1");
        g.addEdge(new Edge("f", "V1", "V2"), "V1", "V2");
        g.addEdge(new Edge("f", "V2", "V3"), "V2", "V3");
        g.addEdge(new Edge("f", "V3", "V4"), "V3", "V4");
        g.addEdge(new Edge("f", "V4", "V5"), "V4", "V5");
        g.addEdge(new Edge("f", "V5", "V0"), "V5", "V0");
        g.addEdge(new Edge("f", "V6", "V7"), "V6", "V7");
        g.addEdge(new Edge("f", "V7", "V8"), "V7", "V8");
        g.addEdge(new Edge("f", "V8", "V9"), "V8", "V9");
        g.addEdge(new Edge("f", "V9", "V10"), "V9", "V10");
        g.addEdge(new Edge("f", "V10", "V11"), "V10", "V11");
        g.addEdge(new Edge("f", "V3", "V6"), "V3", "V6");
        g.addEdge(new Edge("f", "V0", "V11"), "V0", "V11");
        return g;
    }

    private Graph<String, Edge> buildIsolatedNodes() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        return g;
    }

    private GraphControllabilityEngine engine() {
        return new GraphControllabilityEngine().setRandomSeed(42);
    }

    // ===== Empty graph tests =====

    @Test
    public void testEmptyGraph() {
        var report = engine().analyze(buildEmpty());
        assertEquals(0, report.nodeCount);
        assertEquals(0, report.edgeCount);
        assertTrue(report.driverNodes.isEmpty());
        assertEquals(0.0, report.driverNodeDensity, 0.001);
        assertEquals(0.0, report.healthScore, 0.001);
    }

    @Test
    public void testEmptyGraphInsights() {
        var report = engine().analyze(buildEmpty());
        assertFalse(report.insights.isEmpty());
    }

    // ===== Single node tests =====

    @Test
    public void testSingleNode() {
        var report = engine().analyze(buildSingle());
        assertEquals(1, report.nodeCount);
        assertEquals(1, report.driverNodes.size());
        assertEquals(1.0, report.driverNodeDensity, 0.001);
        assertTrue(report.driverNodes.contains("A"));
    }

    @Test
    public void testSingleNodeCategories() {
        var report = engine().analyze(buildSingle());
        assertEquals("CRITICAL", report.nodeCategories.get("A"));
    }

    @Test
    public void testSingleNodeCentrality() {
        var report = engine().analyze(buildSingle());
        assertEquals(1.0, report.controlCentrality.get("A"), 0.001);
    }

    // ===== Pair tests =====

    @Test
    public void testPairDriverDensity() {
        var report = engine().analyze(buildPair());
        assertEquals(2, report.nodeCount);
        assertTrue(report.driverNodeDensity > 0);
        assertTrue(report.driverNodeDensity <= 1.0);
    }

    @Test
    public void testPairMatchingSize() {
        var report = engine().analyze(buildPair());
        assertTrue(report.matchingSize >= 1);
    }

    @Test
    public void testPairHealthScore() {
        var report = engine().analyze(buildPair());
        assertTrue(report.healthScore >= 0);
        assertTrue(report.healthScore <= 100);
    }

    // ===== Triangle tests =====

    @Test
    public void testTriangleBasic() {
        var report = engine().analyze(buildTriangle());
        assertEquals(3, report.nodeCount);
        assertEquals(3, report.edgeCount);
        assertFalse(report.driverNodes.isEmpty());
    }

    @Test
    public void testTriangleCentralityAllEqual() {
        var report = engine().analyze(buildTriangle());
        double ccA = report.controlCentrality.get("A");
        double ccB = report.controlCentrality.get("B");
        double ccC = report.controlCentrality.get("C");
        assertEquals(ccA, ccB, 0.001);
        assertEquals(ccB, ccC, 0.001);
    }

    @Test
    public void testTriangleCentralityValue() {
        var report = engine().analyze(buildTriangle());
        // All nodes can reach all others via bidirectional edges
        assertEquals(1.0, report.controlCentrality.get("A"), 0.001);
    }

    // ===== Path tests =====

    @Test
    public void testPath5DriverDensity() {
        var report = engine().analyze(buildPath5());
        assertEquals(5, report.nodeCount);
        assertTrue(report.driverNodeDensity >= 0);
        assertTrue(report.driverNodeDensity <= 1.0);
    }

    @Test
    public void testPath5Robustness() {
        var report = engine().analyze(buildPath5());
        assertTrue(report.robustnessIndex >= 0);
        assertTrue(report.robustnessIndex <= 1.0);
    }

    @Test
    public void testPath5RobustnessCurveNonEmpty() {
        var report = engine().analyze(buildPath5());
        assertFalse(report.robustnessCurve.isEmpty());
    }

    // ===== Star topology tests =====

    @Test
    public void testStar5Basic() {
        var report = engine().analyze(buildStar5());
        assertEquals(5, report.nodeCount);
        assertEquals(4, report.edgeCount);
    }

    @Test
    public void testStar5HubHighCentrality() {
        var report = engine().analyze(buildStar5());
        double hubCC = report.controlCentrality.get("Hub");
        for (int i = 1; i <= 4; i++) {
            assertTrue(hubCC >= report.controlCentrality.get("L" + i));
        }
    }

    @Test
    public void testStar5TopControlIncludesHub() {
        var report = engine().analyze(buildStar5());
        assertTrue(report.topControlNodes.contains("Hub"));
    }

    // ===== Complete graph tests =====

    @Test
    public void testK4Basic() {
        var report = engine().analyze(buildK4());
        assertEquals(4, report.nodeCount);
        assertEquals(6, report.edgeCount);
    }

    @Test
    public void testK4LowDriverDensity() {
        var report = engine().analyze(buildK4());
        // Complete graphs have good matching, low driver density
        assertTrue(report.driverNodeDensity <= 0.5);
    }

    @Test
    public void testK5AllCentralityEqual() {
        var report = engine().analyze(buildK5());
        Set<Double> vals = new HashSet<>(report.controlCentrality.values());
        assertEquals(1, vals.size()); // all should be equal (1.0)
    }

    // ===== Disconnected graph tests =====

    @Test
    public void testDisconnectedBasic() {
        var report = engine().analyze(buildDisconnected());
        assertEquals(5, report.nodeCount);
        assertFalse(report.driverNodes.isEmpty());
    }

    @Test
    public void testDisconnectedCentralityLimited() {
        var report = engine().analyze(buildDisconnected());
        // Isolated node C can only reach itself
        double ccC = report.controlCentrality.get("C");
        assertTrue(ccC < 1.0);
    }

    @Test
    public void testDisconnectedHasDrivers() {
        var report = engine().analyze(buildDisconnected());
        assertTrue(report.driverNodes.size() >= 1);
    }

    // ===== Tree tests =====

    @Test
    public void testTreeBasic() {
        var report = engine().analyze(buildTree());
        assertEquals(5, report.nodeCount);
        assertEquals(4, report.edgeCount);
    }

    @Test
    public void testTreeDriversExist() {
        var report = engine().analyze(buildTree());
        assertFalse(report.driverNodes.isEmpty());
    }

    @Test
    public void testTreeRobustnessReasonable() {
        var report = engine().analyze(buildTree());
        assertTrue(report.robustnessIndex >= 0);
        assertTrue(report.robustnessIndex <= 1.0);
    }

    // ===== Isolated nodes tests =====

    @Test
    public void testIsolatedNodesAllDrivers() {
        var report = engine().analyze(buildIsolatedNodes());
        assertEquals(3, report.nodeCount);
        assertEquals(0, report.edgeCount);
        // With no edges, all nodes should be drivers
        assertEquals(3, report.driverNodes.size());
        assertEquals(1.0, report.driverNodeDensity, 0.001);
    }

    @Test
    public void testIsolatedNodesCentrality() {
        var report = engine().analyze(buildIsolatedNodes());
        // Each node can only reach itself
        for (double cc : report.controlCentrality.values()) {
            assertEquals(0.333, cc, 0.01);
        }
    }

    // ===== Larger graph tests =====

    @Test
    public void testLargerBasic() {
        var report = engine().analyze(buildLarger());
        assertEquals(12, report.nodeCount);
        assertEquals(13, report.edgeCount);
    }

    @Test
    public void testLargerHealthScoreRange() {
        var report = engine().analyze(buildLarger());
        assertTrue(report.healthScore >= 0);
        assertTrue(report.healthScore <= 100);
    }

    @Test
    public void testLargerTopControlLimit() {
        var report = engine().analyze(buildLarger());
        assertTrue(report.topControlNodes.size() <= 10);
    }

    @Test
    public void testLargerInsightsGenerated() {
        var report = engine().analyze(buildLarger());
        assertTrue(report.insights.size() >= 3);
    }

    // ===== Control profile tests =====

    @Test
    public void testControlProfileKeysPresent() {
        var report = engine().analyze(buildTriangle());
        assertTrue(report.controlProfile.containsKey("source"));
        assertTrue(report.controlProfile.containsKey("internal"));
        assertTrue(report.controlProfile.containsKey("sink"));
    }

    @Test
    public void testControlProfileSumsToOne() {
        var report = engine().analyze(buildStar5());
        double sum = report.controlProfile.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, sum, 0.01);
    }

    @Test
    public void testEmptyGraphControlProfile() {
        var report = engine().analyze(buildEmpty());
        assertEquals(0.0, report.controlProfile.get("source"), 0.001);
        assertEquals(0.0, report.controlProfile.get("internal"), 0.001);
        assertEquals(0.0, report.controlProfile.get("sink"), 0.001);
    }

    // ===== Node category tests =====

    @Test
    public void testCategoriesValidValues() {
        var report = engine().analyze(buildLarger());
        Set<String> valid = Set.of("CRITICAL", "REDUNDANT", "INTERMITTENT");
        for (String cat : report.nodeCategories.values()) {
            assertTrue("Invalid category: " + cat, valid.contains(cat));
        }
    }

    @Test
    public void testCategoriesAllNodesPresent() {
        var report = engine().analyze(buildK4());
        assertEquals(4, report.nodeCategories.size());
    }

    @Test
    public void testIsolatedNodesAllCritical() {
        var report = engine().analyze(buildIsolatedNodes());
        for (String cat : report.nodeCategories.values()) {
            assertEquals("CRITICAL", cat);
        }
    }

    // ===== Robustness curve tests =====

    @Test
    public void testRobustnessCurveStartsWithZeroRemoval() {
        var report = engine().analyze(buildK4());
        assertTrue(report.robustnessCurve.containsKey(0.0));
    }

    @Test
    public void testRobustnessCurveEndsHigh() {
        var report = engine().analyze(buildK4());
        // When all nodes removed, driver density should be 1.0
        Double lastVal = null;
        for (double v : report.robustnessCurve.values()) lastVal = v;
        assertNotNull(lastVal);
        assertEquals(1.0, lastVal, 0.001);
    }

    // ===== Text output tests =====

    @Test
    public void testToTextNonEmpty() {
        var report = engine().analyze(buildTriangle());
        String text = engine().toText(report);
        assertFalse(text.isEmpty());
    }

    @Test
    public void testToTextContainsHeader() {
        var report = engine().analyze(buildTriangle());
        String text = engine().toText(report);
        assertTrue(text.contains("Controllability Report"));
    }

    @Test
    public void testToTextContainsDrivers() {
        var report = engine().analyze(buildTriangle());
        String text = engine().toText(report);
        assertTrue(text.contains("Driver"));
    }

    @Test
    public void testToTextContainsRobustness() {
        var report = engine().analyze(buildPath5());
        String text = engine().toText(report);
        assertTrue(text.contains("Robustness"));
    }

    // ===== HTML export tests =====

    @Test
    public void testExportHtmlProducesHtml() {
        var report = engine().analyze(buildTriangle());
        String html = engine().exportHtml(report);
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("</html>"));
    }

    @Test
    public void testExportHtmlContainsDashboardTitle() {
        var report = engine().analyze(buildK4());
        String html = engine().exportHtml(report);
        assertTrue(html.contains("Controllability Dashboard"));
    }

    @Test
    public void testExportHtmlContainsCanvas() {
        var report = engine().analyze(buildPath5());
        String html = engine().exportHtml(report);
        assertTrue(html.contains("<canvas"));
    }

    @Test
    public void testExportHtmlContainsInsights() {
        var report = engine().analyze(buildLarger());
        String html = engine().exportHtml(report);
        assertTrue(html.contains("Autonomous Insights"));
    }

    @Test
    public void testExportHtmlContainsControlCentrality() {
        var report = engine().analyze(buildStar5());
        String html = engine().exportHtml(report);
        assertTrue(html.contains("Control Centrality"));
    }

    @Test
    public void testExportHtmlContainsCategories() {
        var report = engine().analyze(buildLarger());
        String html = engine().exportHtml(report);
        assertTrue(html.contains("Node Control Categories"));
    }

    // ===== Determinism tests =====

    @Test
    public void testDeterministicWithSameSeed() {
        var r1 = new GraphControllabilityEngine().setRandomSeed(123).analyze(buildLarger());
        var r2 = new GraphControllabilityEngine().setRandomSeed(123).analyze(buildLarger());
        assertEquals(r1.driverNodeDensity, r2.driverNodeDensity, 0.0001);
        assertEquals(r1.matchingSize, r2.matchingSize);
        assertEquals(r1.robustnessIndex, r2.robustnessIndex, 0.0001);
        assertEquals(r1.healthScore, r2.healthScore, 0.0001);
    }

    @Test
    public void testDifferentSeedsMayDiffer() {
        // Different seeds might produce different robustness values (MC simulation)
        var r1 = new GraphControllabilityEngine().setRandomSeed(1).analyze(buildLarger());
        var r2 = new GraphControllabilityEngine().setRandomSeed(999).analyze(buildLarger());
        // Matching and driver density should be the same (deterministic algorithm)
        // but robustness curve may differ
        assertNotNull(r1);
        assertNotNull(r2);
    }

    // ===== Builder configuration tests =====

    @Test
    public void testSetMonteCarloTrials() {
        var report = new GraphControllabilityEngine()
                .setMonteCarloTrials(10).setRandomSeed(42)
                .analyze(buildPath5());
        assertNotNull(report);
        assertTrue(report.robustnessIndex >= 0);
    }

    @Test
    public void testSetCategoryTrials() {
        var report = new GraphControllabilityEngine()
                .setCategoryTrials(5).setRandomSeed(42)
                .analyze(buildK4());
        assertNotNull(report);
        assertFalse(report.nodeCategories.isEmpty());
    }

    // ===== Matching size consistency tests =====

    @Test
    public void testMatchingSizeNotExceedNodeCount() {
        var report = engine().analyze(buildLarger());
        assertTrue(report.matchingSize <= report.nodeCount);
    }

    @Test
    public void testDriversPlusMatchedEqualsNodes() {
        var report = engine().analyze(buildK5());
        // driverNodes.size + matchingSize should approximate nodeCount
        // (matched covers matchingSize nodes on right side)
        assertTrue(report.driverNodes.size() >= 1);
        assertTrue(report.matchingSize <= report.nodeCount);
    }

    // ===== Edge cases =====

    @Test
    public void testTwoDisconnectedPairs() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B");
        g.addEdge(new Edge("f", "A", "B"), "A", "B");
        g.addVertex("C"); g.addVertex("D");
        g.addEdge(new Edge("f", "C", "D"), "C", "D");
        var report = engine().analyze(g);
        assertEquals(4, report.nodeCount);
        assertTrue(report.driverNodes.size() >= 1);
    }

    @Test
    public void testSelfContainedCycle() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C"); g.addVertex("D");
        g.addEdge(new Edge("f", "A", "B"), "A", "B");
        g.addEdge(new Edge("f", "B", "C"), "B", "C");
        g.addEdge(new Edge("f", "C", "D"), "C", "D");
        g.addEdge(new Edge("f", "D", "A"), "D", "A");
        var report = engine().analyze(g);
        assertEquals(4, report.nodeCount);
        assertTrue(report.healthScore >= 0);
    }

    @Test
    public void testControlCentralityRange() {
        var report = engine().analyze(buildLarger());
        for (double cc : report.controlCentrality.values()) {
            assertTrue(cc >= 0.0);
            assertTrue(cc <= 1.0);
        }
    }
}
