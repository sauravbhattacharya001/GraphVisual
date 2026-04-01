package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link EdgeBetweennessAnalyzer}.
 *
 * Covers: Brandes edge betweenness computation, bridge detection,
 * ranking, top-K, summary statistics, and HTML export.
 */
public class EdgeBetweennessAnalyzerTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, Edge>();
    }

    // --- Helpers ---

    private Edge addEdge(String v1, String v2) {
        Edge e = new Edge("f", v1, v2);
        e.setWeight(1.0f);
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        graph.addEdge(e, v1, v2);
        return e;
    }

    // ═══════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNullGraph() {
        new EdgeBetweennessAnalyzer(null);
    }

    @Test
    public void testConstructorValid() {
        EdgeBetweennessAnalyzer analyzer = new EdgeBetweennessAnalyzer(graph);
        assertNotNull(analyzer);
    }

    // ═══════════════════════════════════════
    // Empty and trivial graphs
    // ═══════════════════════════════════════

    @Test
    public void testEmptyGraph() {
        EdgeBetweennessAnalyzer analyzer = new EdgeBetweennessAnalyzer(graph);
        analyzer.compute();
        assertTrue(analyzer.getBetweenness().isEmpty());
        assertTrue(analyzer.getRanking().isEmpty());
        assertTrue(analyzer.getBridges().isEmpty());
    }

    @Test
    public void testSingleEdge() {
        addEdge("A", "B");
        EdgeBetweennessAnalyzer analyzer = new EdgeBetweennessAnalyzer(graph);
        analyzer.compute();

        assertEquals(1, analyzer.getRanking().size());
        // Single edge betweenness = 1.0 (one pair, one path)
        assertEquals(1.0, analyzer.getRanking().get(0).getBetweenness(), 0.001);
        // Single edge is a bridge
        assertTrue(analyzer.getRanking().get(0).isBridge());
        assertEquals(1, analyzer.getBridges().size());
    }

    // ═══════════════════════════════════════
    // Path graph (all edges are bridges)
    // ═══════════════════════════════════════

    @Test
    public void testPathGraphAllBridges() {
        // A -- B -- C -- D
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");

        EdgeBetweennessAnalyzer analyzer = new EdgeBetweennessAnalyzer(graph);
        analyzer.compute();

        assertEquals(3, analyzer.getBridges().size());
        assertEquals(3, analyzer.getRanking().size());

        // Middle edge B-C should have highest betweenness
        // It carries paths: A-C, A-D, B-D (3 paths)
        EdgeBetweennessAnalyzer.EdgeScore top = analyzer.getRanking().get(0);
        double topBet = top.getBetweenness();

        // Edge A-B carries: A-B, A-C, A-D (3 paths) — wait, actually:
        // B-C: used by (A,C), (A,D), (B,D) = 3 pairs → betweenness = 3
        // A-B: used by (A,B), (A,C), (A,D) = 3 pairs → betweenness = 3
        // C-D: used by (A,D), (B,D), (C,D) = 3 pairs → betweenness = 3
        // All equal in a path of 4 vertices
        assertEquals(3.0, topBet, 0.001);
    }

    // ═══════════════════════════════════════
    // Triangle (no bridges, all equal betweenness)
    // ═══════════════════════════════════════

    @Test
    public void testTriangleNoBridges() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");

        EdgeBetweennessAnalyzer analyzer = new EdgeBetweennessAnalyzer(graph);
        analyzer.compute();

        assertEquals(0, analyzer.getBridges().size());
        assertEquals(3, analyzer.getRanking().size());

        // In a triangle, each edge carries exactly 1 shortest path (between its endpoints)
        for (EdgeBetweennessAnalyzer.EdgeScore es : analyzer.getRanking()) {
            assertEquals(1.0, es.getBetweenness(), 0.001);
            assertFalse(es.isBridge());
        }
    }

    // ═══════════════════════════════════════
    // Barbell graph (bridge between two cliques)
    // ═══════════════════════════════════════

    @Test
    public void testBarbellGraphBridge() {
        // Clique 1: A-B-C
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        // Bridge
        Edge bridge = addEdge("C", "D");
        // Clique 2: D-E-F
        addEdge("D", "E");
        addEdge("E", "F");
        addEdge("D", "F");

        EdgeBetweennessAnalyzer analyzer = new EdgeBetweennessAnalyzer(graph);
        analyzer.compute();

        // C-D should be a bridge
        assertTrue(analyzer.getBridges().contains(bridge));

        // C-D should have the highest betweenness (carries all cross-clique paths)
        EdgeBetweennessAnalyzer.EdgeScore topEdge = analyzer.getRanking().get(0);
        assertTrue(topEdge.isBridge());
        // 3 nodes on each side → 3×3 = 9 cross-clique pairs go through C-D
        assertEquals(9.0, topEdge.getBetweenness(), 0.001);
    }

    // ═══════════════════════════════════════
    // Top-K
    // ═══════════════════════════════════════

    @Test
    public void testTopK() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "E");

        EdgeBetweennessAnalyzer analyzer = new EdgeBetweennessAnalyzer(graph);
        analyzer.compute();

        List<EdgeBetweennessAnalyzer.EdgeScore> top2 = analyzer.getTopK(2);
        assertEquals(2, top2.size());
        // Top edges should be sorted descending by betweenness
        assertTrue(top2.get(0).getBetweenness() >= top2.get(1).getBetweenness());

        // Requesting more than available returns all
        List<EdgeBetweennessAnalyzer.EdgeScore> topAll = analyzer.getTopK(100);
        assertEquals(4, topAll.size());
    }

    // ═══════════════════════════════════════
    // Summary statistics
    // ═══════════════════════════════════════

    @Test
    public void testSummaryStatistics() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");

        EdgeBetweennessAnalyzer analyzer = new EdgeBetweennessAnalyzer(graph);
        analyzer.compute();

        Map<String, Object> summary = analyzer.getSummary();
        assertEquals(3, summary.get("totalEdges"));
        assertEquals(3, summary.get("totalVertices"));
        assertEquals(0, summary.get("bridgeCount"));
        assertEquals(1.0, (double) summary.get("maxBetweenness"), 0.001);
        assertEquals(1.0, (double) summary.get("avgBetweenness"), 0.001);
        assertEquals(1.0, (double) summary.get("medianBetweenness"), 0.001);
    }

    // ═══════════════════════════════════════
    // Lazy computation
    // ═══════════════════════════════════════

    @Test
    public void testLazyCompute() {
        addEdge("A", "B");
        EdgeBetweennessAnalyzer analyzer = new EdgeBetweennessAnalyzer(graph);

        // Calling getBetweenness without explicit compute() should auto-compute
        Map<Edge, Double> bet = analyzer.getBetweenness();
        assertFalse(bet.isEmpty());
    }

    // ═══════════════════════════════════════
    // Star graph (center edges are bridges)
    // ═══════════════════════════════════════

    @Test
    public void testStarGraphBetweenness() {
        // Center: C, leaves: L1, L2, L3, L4
        addEdge("C", "L1");
        addEdge("C", "L2");
        addEdge("C", "L3");
        addEdge("C", "L4");

        EdgeBetweennessAnalyzer analyzer = new EdgeBetweennessAnalyzer(graph);
        analyzer.compute();

        // All 4 edges are bridges
        assertEquals(4, analyzer.getBridges().size());

        // Each edge C-Li carries: (Li, C) + (Li, Lj for j≠i) = 1 + 3 = 4 paths
        for (EdgeBetweennessAnalyzer.EdgeScore es : analyzer.getRanking()) {
            assertEquals(4.0, es.getBetweenness(), 0.001);
            assertTrue(es.isBridge());
        }
    }

    // ═══════════════════════════════════════
    // Ranking is sorted descending
    // ═══════════════════════════════════════

    @Test
    public void testRankingSortedDescending() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");

        EdgeBetweennessAnalyzer analyzer = new EdgeBetweennessAnalyzer(graph);
        analyzer.compute();

        List<EdgeBetweennessAnalyzer.EdgeScore> ranking = analyzer.getRanking();
        for (int i = 1; i < ranking.size(); i++) {
            assertTrue(ranking.get(i - 1).getBetweenness() >= ranking.get(i).getBetweenness());
        }
    }

    // ═══════════════════════════════════════
    // EdgeScore accessors
    // ═══════════════════════════════════════

    @Test
    public void testEdgeScoreAccessors() {
        addEdge("X", "Y");
        EdgeBetweennessAnalyzer analyzer = new EdgeBetweennessAnalyzer(graph);
        analyzer.compute();

        EdgeBetweennessAnalyzer.EdgeScore es = analyzer.getRanking().get(0);
        assertNotNull(es.getEdge());
        assertNotNull(es.getSource());
        assertNotNull(es.getTarget());
        assertTrue(es.getBetweenness() > 0);
        assertNotNull(es.toString());
        assertTrue(es.toString().contains("BRIDGE") || es.toString().contains(es.getSource()));
    }

    // ═══════════════════════════════════════
    // HTML export
    // ═══════════════════════════════════════

    @Test
    public void testHtmlExport() throws IOException {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        addEdge("C", "D");

        EdgeBetweennessAnalyzer analyzer = new EdgeBetweennessAnalyzer(graph);
        analyzer.setTitle("Test Report");
        analyzer.compute();

        File tmp = File.createTempFile("edge-bet-test", ".html");
        try {
            analyzer.exportHtml(tmp);
            assertTrue(tmp.exists());
            assertTrue(tmp.length() > 100);
        } finally {
            tmp.delete();
        }
    }

    // ═══════════════════════════════════════
    // Cycle graph (no bridges)
    // ═══════════════════════════════════════

    @Test
    public void testCycleGraphNoBridges() {
        // A-B-C-D-A (4-cycle)
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "A");

        EdgeBetweennessAnalyzer analyzer = new EdgeBetweennessAnalyzer(graph);
        analyzer.compute();

        assertEquals(0, analyzer.getBridges().size());
        // All edges in a 4-cycle should have the same betweenness
        double first = analyzer.getRanking().get(0).getBetweenness();
        for (EdgeBetweennessAnalyzer.EdgeScore es : analyzer.getRanking()) {
            assertEquals(first, es.getBetweenness(), 0.001);
        }
    }

    // ═══════════════════════════════════════
    // Disconnected graph
    // ═══════════════════════════════════════

    @Test
    public void testDisconnectedGraph() {
        addEdge("A", "B");
        addEdge("C", "D");

        EdgeBetweennessAnalyzer analyzer = new EdgeBetweennessAnalyzer(graph);
        analyzer.compute();

        assertEquals(2, analyzer.getRanking().size());
        // Both edges are bridges within their components
        assertEquals(2, analyzer.getBridges().size());
    }

    // ═══════════════════════════════════════
    // Isolated vertex (no edges)
    // ═══════════════════════════════════════

    @Test
    public void testIsolatedVertex() {
        graph.addVertex("alone");
        EdgeBetweennessAnalyzer analyzer = new EdgeBetweennessAnalyzer(graph);
        analyzer.compute();

        assertTrue(analyzer.getRanking().isEmpty());
        assertTrue(analyzer.getBridges().isEmpty());
    }
}
