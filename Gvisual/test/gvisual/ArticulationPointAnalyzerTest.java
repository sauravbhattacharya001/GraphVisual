package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for ArticulationPointAnalyzer — Tarjan's algorithm for finding
 * cut vertices and bridges in undirected graphs.
 */
public class ArticulationPointAnalyzerTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, Edge>();
    }

    private Edge addEdge(String v1, String v2, String type) {
        Edge e = new Edge(type, v1, v2);
        e.setLabel(v1 + "-" + v2);
        graph.addEdge(e, v1, v2);
        return e;
    }

    // ── Constructor ──────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new ArticulationPointAnalyzer(null);
    }

    // ── Empty and trivial graphs ─────────────────────────────────

    @Test
    public void testEmptyGraph() {
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        assertEquals(0, result.getTotalVertices());
        assertEquals(0, result.getTotalEdges());
        assertEquals(0, result.getArticulationPointCount());
        assertEquals(0, result.getBridgeCount());
        assertFalse(result.hasCriticalElements());
    }

    @Test
    public void testSingleVertex() {
        graph.addVertex("A");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        assertEquals(1, result.getTotalVertices());
        assertEquals(0, result.getArticulationPointCount());
        assertEquals(0, result.getBridgeCount());
        assertEquals(1, result.getConnectedComponents());
    }

    @Test
    public void testTwoVerticesOneBridge() {
        addEdge("A", "B", "f");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        assertEquals(2, result.getTotalVertices());
        assertEquals(1, result.getTotalEdges());
        // Two vertices connected by one edge: no articulation points (removing either
        // just leaves one isolated vertex), but the edge is a bridge
        assertEquals(1, result.getBridgeCount());
        assertTrue(result.hasCriticalElements());
    }

    @Test
    public void testTriangleNoCriticalElements() {
        // Triangle: A-B-C-A — no articulation points, no bridges
        addEdge("A", "B", "f");
        addEdge("B", "C", "f");
        addEdge("C", "A", "f");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        assertEquals(0, result.getArticulationPointCount());
        assertEquals(0, result.getBridgeCount());
        assertFalse(result.hasCriticalElements());
    }

    // ── Classic articulation point topologies ────────────────────

    @Test
    public void testLinearChainAllBridges() {
        // A-B-C-D: all edges are bridges, B and C are articulation points
        addEdge("A", "B", "f");
        addEdge("B", "C", "f");
        addEdge("C", "D", "f");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        assertTrue(result.getArticulationPoints().contains("B"));
        assertTrue(result.getArticulationPoints().contains("C"));
        assertEquals(2, result.getArticulationPointCount());
        assertEquals(3, result.getBridgeCount());
    }

    @Test
    public void testBridgeBetweenTriangles() {
        // Triangle 1: A-B-C-A, Triangle 2: D-E-F-D, Bridge: C-D
        addEdge("A", "B", "f");
        addEdge("B", "C", "f");
        addEdge("C", "A", "f");
        addEdge("D", "E", "f");
        addEdge("E", "F", "f");
        addEdge("F", "D", "f");
        addEdge("C", "D", "f");  // bridge
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        // C and D are articulation points
        assertTrue(result.getArticulationPoints().contains("C"));
        assertTrue(result.getArticulationPoints().contains("D"));
        // C-D is a bridge
        assertEquals(1, result.getBridgeCount());
        assertEquals(1, result.getConnectedComponents());
    }

    @Test
    public void testStarTopology() {
        // Center connected to 4 leaves: center is articulation point, all edges are bridges
        addEdge("center", "A", "f");
        addEdge("center", "B", "f");
        addEdge("center", "C", "f");
        addEdge("center", "D", "f");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        assertEquals(1, result.getArticulationPointCount());
        assertTrue(result.getArticulationPoints().contains("center"));
        assertEquals(4, result.getBridgeCount());
    }

    @Test
    public void testCompleteGraphK4NoCritical() {
        // K4: every vertex connected to every other — no critical elements
        String[] nodes = {"A", "B", "C", "D"};
        int edgeId = 0;
        for (int i = 0; i < nodes.length; i++) {
            for (int j = i + 1; j < nodes.length; j++) {
                addEdge(nodes[i], nodes[j], "f");
            }
        }
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        assertEquals(0, result.getArticulationPointCount());
        assertEquals(0, result.getBridgeCount());
    }

    // ── Disconnected graphs ──────────────────────────────────────

    @Test
    public void testDisconnectedComponents() {
        // Component 1: A-B-C
        addEdge("A", "B", "f");
        addEdge("B", "C", "f");
        // Component 2: D-E (isolated pair)
        addEdge("D", "E", "c");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        assertEquals(2, result.getConnectedComponents());
        // B is articulation point in component 1
        assertTrue(result.getArticulationPoints().contains("B"));
        // All edges are bridges
        assertEquals(3, result.getBridgeCount());
    }

    @Test
    public void testIsolatedVertices() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        assertEquals(3, result.getTotalVertices());
        assertEquals(3, result.getConnectedComponents());
        assertEquals(0, result.getArticulationPointCount());
        assertEquals(0, result.getBridgeCount());
    }

    // ── Bridge details ───────────────────────────────────────────

    @Test
    public void testBridgeComponentSizes() {
        // A-B-C: B is AP, A-B and B-C are bridges
        // Removing A-B: {A} and {B,C}
        addEdge("A", "B", "f");
        addEdge("B", "C", "f");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        assertEquals(2, result.getBridgeCount());
        for (ArticulationPointAnalyzer.Bridge bridge : result.getBridges()) {
            int total = bridge.getComponentSizeA() + bridge.getComponentSizeB();
            assertEquals(3, total); // All 3 vertices accounted for
            assertTrue(bridge.getComponentSizeA() >= 1);
            assertTrue(bridge.getComponentSizeB() >= 1);
        }
    }

    @Test
    public void testBridgeSeverity() {
        // Unbalanced bridge: 1 node on one side, 4 on the other
        addEdge("A", "B", "f");
        addEdge("B", "C", "f");
        addEdge("C", "D", "f");
        addEdge("D", "E", "f");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        // Bridges sorted by severity (most severe first)
        List<ArticulationPointAnalyzer.Bridge> bridges = result.getBridges();
        assertFalse(bridges.isEmpty());
        for (int i = 0; i < bridges.size() - 1; i++) {
            assertTrue(bridges.get(i).getSeverity() >= bridges.get(i + 1).getSeverity());
        }
    }

    // ── Articulation point details ───────────────────────────────

    @Test
    public void testArticulationPointDegree() {
        addEdge("A", "B", "f");
        addEdge("B", "C", "f");
        addEdge("B", "D", "c");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        assertTrue(result.getArticulationPoints().contains("B"));
        ArticulationPointAnalyzer.ArticulationPointInfo bInfo = null;
        for (ArticulationPointAnalyzer.ArticulationPointInfo info : result.getArticulationPointDetails()) {
            if (info.getVertex().equals("B")) {
                bInfo = info;
                break;
            }
        }
        assertNotNull(bInfo);
        assertEquals(3, bInfo.getDegree());
    }

    @Test
    public void testArticulationPointEdgeTypes() {
        addEdge("A", "B", "f");
        addEdge("B", "C", "c");
        addEdge("B", "D", "f");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        ArticulationPointAnalyzer.ArticulationPointInfo bInfo = null;
        for (ArticulationPointAnalyzer.ArticulationPointInfo info : result.getArticulationPointDetails()) {
            if (info.getVertex().equals("B")) {
                bInfo = info;
                break;
            }
        }
        assertNotNull(bInfo);
        Map<String, Integer> types = bInfo.getEdgeTypeCounts();
        assertEquals(2, (int) types.get("f"));
        assertEquals(1, (int) types.get("c"));
    }

    @Test
    public void testArticulationPointsSortedByCriticality() {
        // Hub with many connections vs simple chain AP
        addEdge("hub", "A", "f");
        addEdge("hub", "B", "f");
        addEdge("hub", "C", "f");
        addEdge("hub", "D", "f");
        addEdge("D", "E", "f");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        List<ArticulationPointAnalyzer.ArticulationPointInfo> details =
                result.getArticulationPointDetails();
        // Should be sorted by criticality descending
        for (int i = 0; i < details.size() - 1; i++) {
            assertTrue(details.get(i).getCriticality() >= details.get(i + 1).getCriticality());
        }
    }

    @Test
    public void testCriticalityScore() {
        addEdge("A", "B", "f");
        addEdge("B", "C", "f");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        for (ArticulationPointAnalyzer.ArticulationPointInfo info : result.getArticulationPointDetails()) {
            assertTrue(info.getCriticality() > 0);
        }
    }

    // ── Resilience score ─────────────────────────────────────────

    @Test
    public void testResilienceScoreRobust() {
        // Complete graph — no critical elements — should be ~100
        addEdge("A", "B", "f");
        addEdge("B", "C", "f");
        addEdge("C", "A", "f");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        assertTrue(result.getResilienceScore() >= 90);
        assertEquals("ROBUST", result.getVulnerabilityLevel());
    }

    @Test
    public void testResilienceScoreFragile() {
        // Linear chain — many bridges/APs — low resilience
        for (int i = 0; i < 10; i++) {
            addEdge("N" + i, "N" + (i + 1), "f");
        }
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        assertTrue(result.getResilienceScore() < 50);
        String level = result.getVulnerabilityLevel();
        assertTrue(level.equals("FRAGILE") || level.equals("CRITICAL")
                || level.equals("VULNERABLE"));
    }

    @Test
    public void testResilienceScoreBounded() {
        addEdge("A", "B", "f");
        addEdge("B", "C", "f");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        assertTrue(result.getResilienceScore() >= 0);
        assertTrue(result.getResilienceScore() <= 100);
    }

    @Test
    public void testResilienceScoreEmptyGraph() {
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        // Empty graph: no vertices to be critical
        assertEquals(0, result.getArticulationPointCount());
    }

    @Test
    public void testResilienceScoreSingleVertex() {
        graph.addVertex("A");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        assertEquals(100.0, result.getResilienceScore(), 0.01);
    }

    // ── Vulnerability level ──────────────────────────────────────

    @Test
    public void testVulnerabilityLevelValues() {
        // Test that vulnerability level is one of the known values
        addEdge("A", "B", "f");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        String level = result.getVulnerabilityLevel();
        assertTrue(level.equals("ROBUST") || level.equals("MODERATE")
                || level.equals("VULNERABLE") || level.equals("FRAGILE")
                || level.equals("CRITICAL"));
    }

    // ── Summary text ─────────────────────────────────────────────

    @Test
    public void testSummaryNotEmpty() {
        addEdge("A", "B", "f");
        addEdge("B", "C", "f");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        String summary = result.getSummary();
        assertNotNull(summary);
        assertTrue(summary.length() > 20);
        assertTrue(summary.contains("Resilience"));
        assertTrue(summary.contains("Cut vertices"));
        assertTrue(summary.contains("Bridges"));
    }

    // ── Percentage calculation ────────────────────────────────────

    @Test
    public void testArticulationPointPercentage() {
        // Star: 1 AP out of 5 vertices = 20%
        addEdge("center", "A", "f");
        addEdge("center", "B", "f");
        addEdge("center", "C", "f");
        addEdge("center", "D", "f");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        assertEquals(20.0, result.getArticulationPointPercentage(), 0.1);
    }

    @Test
    public void testArticulationPointPercentageEmpty() {
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        assertEquals(0.0, result.getArticulationPointPercentage(), 0.01);
    }

    // ── Complex topology ─────────────────────────────────────────

    @Test
    public void testDiamondGraphNoAP() {
        // Diamond: A-B, A-C, B-D, C-D — no AP, no bridge
        addEdge("A", "B", "f");
        addEdge("A", "C", "f");
        addEdge("B", "D", "f");
        addEdge("C", "D", "f");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        assertEquals(0, result.getArticulationPointCount());
        assertEquals(0, result.getBridgeCount());
    }

    @Test
    public void testCycleNoCritical() {
        // Cycle: A-B-C-D-E-A
        addEdge("A", "B", "f");
        addEdge("B", "C", "f");
        addEdge("C", "D", "f");
        addEdge("D", "E", "f");
        addEdge("E", "A", "f");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        assertEquals(0, result.getArticulationPointCount());
        assertEquals(0, result.getBridgeCount());
        assertEquals("ROBUST", result.getVulnerabilityLevel());
    }

    @Test
    public void testButterflyGraph() {
        // Two triangles sharing vertex C: A-B-C-A and C-D-E-C
        addEdge("A", "B", "f");
        addEdge("B", "C", "f");
        addEdge("C", "A", "f");
        addEdge("C", "D", "c");
        addEdge("D", "E", "c");
        addEdge("E", "C", "c");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        // C is the only articulation point (shared vertex)
        assertEquals(1, result.getArticulationPointCount());
        assertTrue(result.getArticulationPoints().contains("C"));
        assertEquals(0, result.getBridgeCount()); // no bridges
    }

    @Test
    public void testMixedEdgeTypes() {
        addEdge("A", "B", "f");
        addEdge("B", "C", "c");
        addEdge("C", "D", "s");
        addEdge("D", "E", "fs");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        // All inner nodes are APs
        assertEquals(3, result.getArticulationPointCount());
        assertTrue(result.getArticulationPoints().contains("B"));
        assertTrue(result.getArticulationPoints().contains("C"));
        assertTrue(result.getArticulationPoints().contains("D"));
    }

    // ── Biconnected components ───────────────────────────────────

    @Test
    public void testBiconnectedComponentCount() {
        // Star: center has 4 biconnected components (one per leaf)
        addEdge("center", "A", "f");
        addEdge("center", "B", "f");
        addEdge("center", "C", "f");
        addEdge("center", "D", "f");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        ArticulationPointAnalyzer.ArticulationPointInfo centerInfo = null;
        for (ArticulationPointAnalyzer.ArticulationPointInfo info : result.getArticulationPointDetails()) {
            if (info.getVertex().equals("center")) {
                centerInfo = info;
                break;
            }
        }
        assertNotNull(centerInfo);
        assertEquals(4, centerInfo.getBiconnectedComponents());
    }

    // ── Immutability ─────────────────────────────────────────────

    @Test(expected = UnsupportedOperationException.class)
    public void testArticulationPointSetImmutable() {
        addEdge("A", "B", "f");
        addEdge("B", "C", "f");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        result.getArticulationPoints().add("X");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testBridgeListImmutable() {
        addEdge("A", "B", "f");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        result.getBridges().add(null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testDetailsListImmutable() {
        addEdge("A", "B", "f");
        addEdge("B", "C", "f");
        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(graph);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();
        result.getArticulationPointDetails().add(null);
    }
}
