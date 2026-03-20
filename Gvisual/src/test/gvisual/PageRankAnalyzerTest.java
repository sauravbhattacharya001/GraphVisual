package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link PageRankAnalyzer}.
 */
public class PageRankAnalyzerTest {

    private static final double EPSILON = 1e-4;
    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
    }

    // ── Construction ────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraph() {
        new PageRankAnalyzer(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidDampingZero() {
        new PageRankAnalyzer(graph, 0.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidDampingOne() {
        new PageRankAnalyzer(graph, 1.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidTolerance() {
        new PageRankAnalyzer(graph, 0.85, -1.0, 100);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidMaxIterations() {
        new PageRankAnalyzer(graph, 0.85, 1e-6, 0);
    }

    // ── Empty graph ─────────────────────────────────────────────────

    @Test
    public void testEmptyGraph() {
        PageRankAnalyzer pr = new PageRankAnalyzer(graph);
        pr.compute();
        assertTrue(pr.getRanks().isEmpty());
        assertTrue(pr.isConverged());
        assertEquals(0, pr.getIterationsUsed());
    }

    // ── Single node ─────────────────────────────────────────────────

    @Test
    public void testSingleNode() {
        graph.addVertex("A");
        PageRankAnalyzer pr = new PageRankAnalyzer(graph);
        pr.compute();
        assertEquals(1.0, pr.getRank("A"), EPSILON);
    }

    // ── Symmetric graph: all nodes equal rank ───────────────────────

    @Test
    public void testCompleteGraphUniformRanks() {
        // In a complete graph, all nodes should have equal PageRank
        GraphGenerator gen = new GraphGenerator(42);
        GraphGenerator.GeneratedGraph gg = gen.complete(5);
        Graph<String, Edge> g = gg.getGraph();

        PageRankAnalyzer pr = new PageRankAnalyzer(g);
        pr.compute();

        double expected = 1.0 / 5;
        for (Map.Entry<String, Double> entry : pr.getRanks().entrySet()) {
            assertEquals("Node " + entry.getKey() + " should have uniform rank",
                    expected, entry.getValue(), EPSILON);
        }
        assertTrue(pr.isConverged());
    }

    @Test
    public void testRingGraphUniformRanks() {
        GraphGenerator gen = new GraphGenerator(42);
        GraphGenerator.GeneratedGraph gg = gen.ring(6);
        Graph<String, Edge> g = gg.getGraph();

        PageRankAnalyzer pr = new PageRankAnalyzer(g);
        pr.compute();

        double expected = 1.0 / 6;
        for (Double rank : pr.getRanks().values()) {
            assertEquals(expected, rank, EPSILON);
        }
    }

    // ── Star graph: hub should have highest rank ────────────────────

    @Test
    public void testStarGraphHubHighestRank() {
        GraphGenerator gen = new GraphGenerator(42);
        GraphGenerator.GeneratedGraph gg = gen.star(6);
        Graph<String, Edge> g = gg.getGraph();

        PageRankAnalyzer pr = new PageRankAnalyzer(g);
        pr.compute();

        // n0 is the hub
        double hubRank = pr.getRank("n0");
        for (int i = 1; i < 6; i++) {
            assertTrue("Hub should outrank leaf n" + i,
                    hubRank > pr.getRank("n" + i));
        }
    }

    // ── Ranks sum to 1.0 ────────────────────────────────────────────

    @Test
    public void testRanksSumToOne() {
        GraphGenerator gen = new GraphGenerator(42);
        GraphGenerator.GeneratedGraph gg = gen.scaleFreeBa(20, 2);
        Graph<String, Edge> g = gg.getGraph();

        PageRankAnalyzer pr = new PageRankAnalyzer(g);
        pr.compute();

        double sum = 0;
        for (double r : pr.getRanks().values()) {
            sum += r;
        }
        assertEquals("Ranks should sum to 1.0", 1.0, sum, EPSILON);
    }

    // ── Disconnected graph: each component independent ──────────────

    @Test
    public void testDisconnectedGraph() {
        // Two disconnected pairs
        addEdge("A", "B");
        addEdge("C", "D");

        PageRankAnalyzer pr = new PageRankAnalyzer(graph);
        pr.compute();

        // Each pair should have equal rank within itself
        assertEquals(pr.getRank("A"), pr.getRank("B"), EPSILON);
        assertEquals(pr.getRank("C"), pr.getRank("D"), EPSILON);
        // And by symmetry, all four should be equal
        assertEquals(pr.getRank("A"), pr.getRank("C"), EPSILON);
    }

    // ── Path graph: center nodes rank higher ────────────────────────

    @Test
    public void testPathGraphCenterHigher() {
        // A - B - C - D - E
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "E");

        PageRankAnalyzer pr = new PageRankAnalyzer(graph);
        pr.compute();

        // Center node C (degree 2, central) should rank higher than endpoints A, E (degree 1)
        assertTrue("Center should rank higher than endpoint",
                pr.getRank("C") > pr.getRank("A"));
        assertTrue("Center should rank higher than endpoint",
                pr.getRank("C") > pr.getRank("E"));
    }

    // ── Custom damping factor ───────────────────────────────────────

    @Test
    public void testCustomDampingFactor() {
        addEdge("A", "B");
        addEdge("B", "C");

        PageRankAnalyzer pr = new PageRankAnalyzer(graph, 0.5);
        pr.compute();

        // Should still converge and sum to 1
        double sum = 0;
        for (double r : pr.getRanks().values()) {
            sum += r;
        }
        assertEquals(1.0, sum, EPSILON);
        assertTrue(pr.isConverged());
    }

    // ── Sorted results ──────────────────────────────────────────────

    @Test
    public void testGetSortedResults() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("B", "D");
        addEdge("B", "E");

        PageRankAnalyzer pr = new PageRankAnalyzer(graph);
        pr.compute();

        List<PageRankAnalyzer.PageRankResult> sorted = pr.getSortedResults();
        assertEquals(5, sorted.size());
        // Should be sorted by rank descending
        for (int i = 0; i < sorted.size() - 1; i++) {
            assertTrue("Results should be sorted descending",
                    sorted.get(i).getRank() >= sorted.get(i + 1).getRank());
        }
        // B (hub) should be first
        assertEquals("B", sorted.get(0).getNodeId());
    }

    // ── Top K ───────────────────────────────────────────────────────

    @Test
    public void testGetTopK() {
        GraphGenerator gen = new GraphGenerator(42);
        GraphGenerator.GeneratedGraph gg = gen.scaleFreeBa(20, 2);
        Graph<String, Edge> g = gg.getGraph();

        PageRankAnalyzer pr = new PageRankAnalyzer(g);
        pr.compute();

        List<PageRankAnalyzer.PageRankResult> top3 = pr.getTopK(3);
        assertEquals(3, top3.size());
        // Top results should be sorted descending
        assertTrue(top3.get(0).getRank() >= top3.get(1).getRank());
        assertTrue(top3.get(1).getRank() >= top3.get(2).getRank());
    }

    // ── Recompute is idempotent ─────────────────────────────────────

    @Test
    public void testComputeIdempotent() {
        addEdge("A", "B");
        addEdge("B", "C");

        PageRankAnalyzer pr = new PageRankAnalyzer(graph);
        pr.compute();
        Map<String, Double> first = new HashMap<>(pr.getRanks());
        pr.compute(); // should not recompute
        Map<String, Double> second = pr.getRanks();

        assertEquals(first, second);
    }

    // ── Normalized rank ─────────────────────────────────────────────

    @Test
    public void testNormalizedRank() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("B", "D");

        PageRankAnalyzer pr = new PageRankAnalyzer(graph);
        pr.compute();

        List<PageRankAnalyzer.PageRankResult> results = pr.getSortedResults();
        PageRankAnalyzer.PageRankResult hubResult = null;
        for (PageRankAnalyzer.PageRankResult r : results) {
            if (r.getNodeId().equals("B")) {
                hubResult = r;
                break;
            }
        }
        assertNotNull(hubResult);
        // Hub's normalized rank should be > 1.0 (above average)
        assertTrue("Hub normalized rank should be above average",
                hubResult.getNormalizedRank() > 1.0);
    }

    // ── Summary doesn't throw ───────────────────────────────────────

    @Test
    public void testGetSummary() {
        addEdge("A", "B");
        addEdge("B", "C");

        PageRankAnalyzer pr = new PageRankAnalyzer(graph);
        pr.compute();

        String summary = pr.getSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("PageRank"));
        assertTrue(summary.contains("converged"));
    }

    // ── Dangling nodes get distributed rank ─────────────────────────

    @Test
    public void testDanglingNodesGetRank() {
        // A -- B, C is isolated (dangling)
        addEdge("A", "B");
        graph.addVertex("C");

        PageRankAnalyzer pr = new PageRankAnalyzer(graph);
        pr.compute();

        // C should still have some rank (from dangling distribution + teleport)
        assertTrue("Dangling node should have positive rank",
                pr.getRank("C") > 0.0);
        double sum = 0;
        for (double r : pr.getRanks().values()) sum += r;
        assertEquals(1.0, sum, EPSILON);
    }

    // ── Helper ──────────────────────────────────────────────────────

    private int edgeId = 0;

    private void addEdge(String v1, String v2) {
        graph.addVertex(v1);
        graph.addVertex(v2);
        Edge e  new Edge("f", v1, v2);
        e.setLabel("e" + (edgeId++));
        e.setWeight(1.0f);
        graph.addEdge(e, v1, v2);
    }
}
