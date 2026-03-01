package gvisual;

import edu.uci.ics.jung.graph.Graph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for {@link GraphGenerator} — synthetic graph generation
 * with various topologies.
 */
public class GraphGeneratorTest {

    private GraphGenerator gen;

    @Before
    public void setUp() {
        gen = new GraphGenerator(42L); // fixed seed for reproducibility
    }

    // ── Complete Graph ──────────────────────────────────────────────

    @Test
    public void complete_singleNode() {
        GraphGenerator.GeneratedGraph result = gen.complete(1);
        assertEquals(1, result.getNodeCount());
        assertEquals(0, result.getEdgeCount());
    }

    @Test
    public void complete_twoNodes() {
        GraphGenerator.GeneratedGraph result = gen.complete(2);
        assertEquals(2, result.getNodeCount());
        assertEquals(1, result.getEdgeCount());
    }

    @Test
    public void complete_fiveNodes() {
        GraphGenerator.GeneratedGraph result = gen.complete(5);
        assertEquals(5, result.getNodeCount());
        assertEquals(10, result.getEdgeCount()); // 5*4/2
        assertEquals(1.0, result.getDensity(), 0.001);
    }

    @Test
    public void complete_tenNodes() {
        GraphGenerator.GeneratedGraph result = gen.complete(10);
        assertEquals(10, result.getNodeCount());
        assertEquals(45, result.getEdgeCount()); // 10*9/2
    }

    @Test(expected = IllegalArgumentException.class)
    public void complete_zeroNodes_throws() {
        gen.complete(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void complete_negativeNodes_throws() {
        gen.complete(-1);
    }

    @Test
    public void complete_allNodesConnected() {
        GraphGenerator.GeneratedGraph result = gen.complete(4);
        Graph<String, edge> g = result.getGraph();
        for (String v : g.getVertices()) {
            assertEquals(3, g.degree(v));
        }
    }

    // ── Ring ─────────────────────────────────────────────────────────

    @Test
    public void ring_threeNodes() {
        GraphGenerator.GeneratedGraph result = gen.ring(3);
        assertEquals(3, result.getNodeCount());
        assertEquals(3, result.getEdgeCount());
    }

    @Test
    public void ring_tenNodes() {
        GraphGenerator.GeneratedGraph result = gen.ring(10);
        assertEquals(10, result.getNodeCount());
        assertEquals(10, result.getEdgeCount());
        // Every node has degree 2
        for (String v : result.getGraph().getVertices()) {
            assertEquals(2, result.getGraph().degree(v));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void ring_twoNodes_throws() {
        gen.ring(2);
    }

    // ── Star ─────────────────────────────────────────────────────────

    @Test
    public void star_twoNodes() {
        GraphGenerator.GeneratedGraph result = gen.star(2);
        assertEquals(2, result.getNodeCount());
        assertEquals(1, result.getEdgeCount());
    }

    @Test
    public void star_tenNodes() {
        GraphGenerator.GeneratedGraph result = gen.star(10);
        assertEquals(10, result.getNodeCount());
        assertEquals(9, result.getEdgeCount());
        // Hub has degree n-1, leaves have degree 1
        Graph<String, edge> g = result.getGraph();
        assertEquals(9, g.degree("n0"));
        for (int i = 1; i < 10; i++) {
            assertEquals(1, g.degree("n" + i));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void star_singleNode_throws() {
        gen.star(1);
    }

    // ── Grid ─────────────────────────────────────────────────────────

    @Test
    public void grid_2x2() {
        GraphGenerator.GeneratedGraph result = gen.grid(2, 2);
        assertEquals(4, result.getNodeCount());
        assertEquals(4, result.getEdgeCount());
    }

    @Test
    public void grid_3x3() {
        GraphGenerator.GeneratedGraph result = gen.grid(3, 3);
        assertEquals(9, result.getNodeCount());
        assertEquals(12, result.getEdgeCount());
    }

    @Test
    public void grid_1x5() {
        GraphGenerator.GeneratedGraph result = gen.grid(1, 5);
        assertEquals(5, result.getNodeCount());
        assertEquals(4, result.getEdgeCount()); // Just a path
    }

    @Test
    public void grid_5x1() {
        GraphGenerator.GeneratedGraph result = gen.grid(5, 1);
        assertEquals(5, result.getNodeCount());
        assertEquals(4, result.getEdgeCount());
    }

    @Test
    public void grid_1x1() {
        GraphGenerator.GeneratedGraph result = gen.grid(1, 1);
        assertEquals(1, result.getNodeCount());
        assertEquals(0, result.getEdgeCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void grid_zeroRows_throws() {
        gen.grid(0, 3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void grid_zeroCols_throws() {
        gen.grid(3, 0);
    }

    // ── Path ─────────────────────────────────────────────────────────

    @Test
    public void path_twoNodes() {
        GraphGenerator.GeneratedGraph result = gen.path(2);
        assertEquals(2, result.getNodeCount());
        assertEquals(1, result.getEdgeCount());
    }

    @Test
    public void path_fiveNodes() {
        GraphGenerator.GeneratedGraph result = gen.path(5);
        assertEquals(5, result.getNodeCount());
        assertEquals(4, result.getEdgeCount());
        // Endpoints have degree 1, interior nodes have degree 2
        Graph<String, edge> g = result.getGraph();
        assertEquals(1, g.degree("n0"));
        assertEquals(2, g.degree("n2"));
        assertEquals(1, g.degree("n4"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void path_singleNode_throws() {
        gen.path(1);
    }

    // ── Tree ─────────────────────────────────────────────────────────

    @Test
    public void tree_binaryDepth0() {
        GraphGenerator.GeneratedGraph result = gen.tree(2, 0);
        assertEquals(1, result.getNodeCount()); // root only
        assertEquals(0, result.getEdgeCount());
    }

    @Test
    public void tree_binaryDepth1() {
        GraphGenerator.GeneratedGraph result = gen.tree(2, 1);
        assertEquals(3, result.getNodeCount()); // 1 + 2
        assertEquals(2, result.getEdgeCount());
    }

    @Test
    public void tree_binaryDepth3() {
        GraphGenerator.GeneratedGraph result = gen.tree(2, 3);
        // 1 + 2 + 4 + 8 = 15 nodes, 14 edges
        assertEquals(15, result.getNodeCount());
        assertEquals(14, result.getEdgeCount());
    }

    @Test
    public void tree_ternaryDepth2() {
        GraphGenerator.GeneratedGraph result = gen.tree(3, 2);
        // 1 + 3 + 9 = 13 nodes, 12 edges
        assertEquals(13, result.getNodeCount());
        assertEquals(12, result.getEdgeCount());
    }

    @Test
    public void tree_branchingFactor1_isPath() {
        GraphGenerator.GeneratedGraph result = gen.tree(1, 5);
        assertEquals(6, result.getNodeCount()); // 1 root + 5 children
        assertEquals(5, result.getEdgeCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void tree_zeroBranching_throws() {
        gen.tree(0, 2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void tree_negativeDepth_throws() {
        gen.tree(2, -1);
    }

    // ── Random (Erdős–Rényi) ────────────────────────────────────────

    @Test
    public void randomER_pZero_noEdges() {
        GraphGenerator.GeneratedGraph result = gen.randomErdosRenyi(10, 0.0);
        assertEquals(10, result.getNodeCount());
        assertEquals(0, result.getEdgeCount());
    }

    @Test
    public void randomER_pOne_complete() {
        GraphGenerator.GeneratedGraph result = gen.randomErdosRenyi(5, 1.0);
        assertEquals(5, result.getNodeCount());
        assertEquals(10, result.getEdgeCount()); // complete
    }

    @Test
    public void randomER_halfProbability() {
        GraphGenerator.GeneratedGraph result = gen.randomErdosRenyi(100, 0.5);
        assertEquals(100, result.getNodeCount());
        // Expected: ~2475 edges (4950 * 0.5), but allow wide range
        assertTrue(result.getEdgeCount() > 1000);
        assertTrue(result.getEdgeCount() < 4000);
    }

    @Test
    public void randomER_singleNode() {
        GraphGenerator.GeneratedGraph result = gen.randomErdosRenyi(1, 0.5);
        assertEquals(1, result.getNodeCount());
        assertEquals(0, result.getEdgeCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void randomER_zeroNodes_throws() {
        gen.randomErdosRenyi(0, 0.5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void randomER_negativeProbability_throws() {
        gen.randomErdosRenyi(10, -0.1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void randomER_probabilityAboveOne_throws() {
        gen.randomErdosRenyi(10, 1.5);
    }

    // ── Scale-Free (Barabási–Albert) ────────────────────────────────

    @Test
    public void scaleFreeBa_basic() {
        GraphGenerator.GeneratedGraph result = gen.scaleFreeBa(50, 2);
        assertEquals(50, result.getNodeCount());
        // Initial: 3 nodes with 3 edges. Then 47 nodes × 2 edges = 94
        // Total: ~97 edges
        assertTrue(result.getEdgeCount() > 80);
        assertTrue(result.getEdgeCount() <= 100);
    }

    @Test
    public void scaleFreeBa_m1() {
        GraphGenerator.GeneratedGraph result = gen.scaleFreeBa(20, 1);
        assertEquals(20, result.getNodeCount());
        // Tree-like: ~19 edges
        assertTrue(result.getEdgeCount() >= 18);
    }

    @Test
    public void scaleFreeBa_hasHubs() {
        GraphGenerator.GeneratedGraph result = gen.scaleFreeBa(100, 2);
        Graph<String, edge> g = result.getGraph();
        // Early nodes should have higher degree (preferential attachment)
        int maxDegree = 0;
        for (String v : g.getVertices()) {
            maxDegree = Math.max(maxDegree, g.degree(v));
        }
        assertTrue("Should have hub with degree > 10", maxDegree > 10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void scaleFreeBa_nEqualsM_throws() {
        gen.scaleFreeBa(3, 3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void scaleFreeBa_nLessThanM_throws() {
        gen.scaleFreeBa(2, 3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void scaleFreeBa_mZero_throws() {
        gen.scaleFreeBa(10, 0);
    }

    // ── Small-World (Watts–Strogatz) ────────────────────────────────

    @Test
    public void smallWorldWs_noRewiring() {
        GraphGenerator.GeneratedGraph result = gen.smallWorldWs(10, 4, 0.0);
        assertEquals(10, result.getNodeCount());
        assertEquals(20, result.getEdgeCount()); // n * k/2
        // With no rewiring, every node should have degree k
        for (String v : result.getGraph().getVertices()) {
            assertEquals(4, result.getGraph().degree(v));
        }
    }

    @Test
    public void smallWorldWs_fullRewiring() {
        GraphGenerator.GeneratedGraph result = gen.smallWorldWs(20, 4, 1.0);
        assertEquals(20, result.getNodeCount());
        // Edge count should stay roughly the same (rewiring, not adding)
        assertEquals(40, result.getEdgeCount());
    }

    @Test
    public void smallWorldWs_partialRewiring() {
        GraphGenerator.GeneratedGraph result = gen.smallWorldWs(50, 4, 0.3);
        assertEquals(50, result.getNodeCount());
        // Edge count stays the same (rewiring preserves count)
        assertEquals(100, result.getEdgeCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void smallWorldWs_tooFewNodes_throws() {
        gen.smallWorldWs(3, 2, 0.5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void smallWorldWs_oddK_throws() {
        gen.smallWorldWs(10, 3, 0.5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void smallWorldWs_kTooSmall_throws() {
        gen.smallWorldWs(10, 0, 0.5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void smallWorldWs_kTooLarge_throws() {
        gen.smallWorldWs(10, 10, 0.5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void smallWorldWs_negativeBeta_throws() {
        gen.smallWorldWs(10, 4, -0.1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void smallWorldWs_betaAboveOne_throws() {
        gen.smallWorldWs(10, 4, 1.5);
    }

    // ── Bipartite ───────────────────────────────────────────────────

    @Test
    public void bipartite_pZero_noEdges() {
        GraphGenerator.GeneratedGraph result = gen.bipartite(5, 5, 0.0);
        assertEquals(10, result.getNodeCount());
        assertEquals(0, result.getEdgeCount());
    }

    @Test
    public void bipartite_pOne_complete() {
        GraphGenerator.GeneratedGraph result = gen.bipartite(3, 4, 1.0);
        assertEquals(7, result.getNodeCount());
        assertEquals(12, result.getEdgeCount()); // 3 * 4
    }

    @Test
    public void bipartite_noIntraGroupEdges() {
        GraphGenerator.GeneratedGraph result = gen.bipartite(5, 5, 1.0);
        Graph<String, edge> g = result.getGraph();
        // Group A: n0-n4, Group B: n5-n9
        // No edges within group A
        for (int i = 0; i < 5; i++) {
            for (int j = i + 1; j < 5; j++) {
                assertNull(g.findEdge("n" + i, "n" + j));
            }
        }
        // No edges within group B
        for (int i = 5; i < 10; i++) {
            for (int j = i + 1; j < 10; j++) {
                assertNull(g.findEdge("n" + i, "n" + j));
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void bipartite_zeroGroupA_throws() {
        gen.bipartite(0, 5, 0.5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void bipartite_zeroGroupB_throws() {
        gen.bipartite(5, 0, 0.5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void bipartite_invalidP_throws() {
        gen.bipartite(5, 5, -0.1);
    }

    // ── GeneratedGraph metadata ─────────────────────────────────────

    @Test
    public void metadata_topology() {
        assertEquals("Ring", gen.ring(5).getTopology());
        assertTrue(gen.complete(4).getTopology().contains("Complete"));
        assertEquals("Star", gen.star(5).getTopology());
        assertTrue(gen.grid(3, 3).getTopology().contains("Grid"));
        assertEquals("Path", gen.path(5).getTopology());
        assertTrue(gen.tree(2, 3).getTopology().contains("Tree"));
        assertTrue(gen.randomErdosRenyi(10, 0.5).getTopology().contains("Erdős"));
        assertTrue(gen.scaleFreeBa(10, 2).getTopology().contains("Scale-Free"));
        assertTrue(gen.smallWorldWs(10, 4, 0.3).getTopology().contains("Small-World"));
        assertEquals("Bipartite", gen.bipartite(5, 5, 0.5).getTopology());
    }

    @Test
    public void metadata_parameters() {
        GraphGenerator.GeneratedGraph result = gen.grid(4, 5);
        Map<String, Object> params = result.getParameters();
        assertEquals(4, params.get("rows"));
        assertEquals(5, params.get("cols"));
        assertEquals(20, params.get("nodes"));
    }

    @Test
    public void metadata_parametersUnmodifiable() {
        GraphGenerator.GeneratedGraph result = gen.ring(5);
        try {
            result.getParameters().put("hack", "value");
            fail("Parameters should be unmodifiable");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void metadata_density_complete() {
        assertEquals(1.0, gen.complete(10).getDensity(), 0.001);
    }

    @Test
    public void metadata_density_ring() {
        // Ring with 10 nodes: 10 edges, max 45 → density ≈ 0.222
        assertEquals(10.0 / 45, gen.ring(10).getDensity(), 0.001);
    }

    @Test
    public void metadata_density_singleNode() {
        assertEquals(0.0, gen.complete(1).getDensity(), 0.001);
    }

    @Test
    public void metadata_averageDegree() {
        // Complete(5): 10 edges, 5 nodes → avg = 4.0
        assertEquals(4.0, gen.complete(5).getAverageDegree(), 0.001);
        // Ring(10): 10 edges, 10 nodes → avg = 2.0
        assertEquals(2.0, gen.ring(10).getAverageDegree(), 0.001);
    }

    @Test
    public void metadata_summary() {
        String summary = gen.complete(5).getSummary();
        assertTrue(summary.contains("Complete"));
        assertTrue(summary.contains("Nodes: 5"));
        assertTrue(summary.contains("Edges: 10"));
        assertTrue(summary.contains("Density"));
        assertTrue(summary.contains("Avg Degree"));
    }

    // ── Reproducibility ─────────────────────────────────────────────

    @Test
    public void reproducibility_sameSeed_sameGraph() {
        GraphGenerator gen1 = new GraphGenerator(12345L);
        GraphGenerator gen2 = new GraphGenerator(12345L);

        GraphGenerator.GeneratedGraph r1 = gen1.randomErdosRenyi(50, 0.3);
        GraphGenerator.GeneratedGraph r2 = gen2.randomErdosRenyi(50, 0.3);

        assertEquals(r1.getEdgeCount(), r2.getEdgeCount());
    }

    @Test
    public void reproducibility_differentSeed_likelyDifferent() {
        GraphGenerator gen1 = new GraphGenerator(1L);
        GraphGenerator gen2 = new GraphGenerator(999L);

        GraphGenerator.GeneratedGraph r1 = gen1.randomErdosRenyi(100, 0.3);
        GraphGenerator.GeneratedGraph r2 = gen2.randomErdosRenyi(100, 0.3);

        // Extremely unlikely to be identical with different seeds
        // (but not impossible — this is a statistical test)
        assertNotEquals(r1.getEdgeCount(), r2.getEdgeCount());
    }

    // ── Cross-compatibility ─────────────────────────────────────────

    @Test
    public void generatedGraphs_workWithExistingAnalyzers() {
        GraphGenerator.GeneratedGraph result = gen.scaleFreeBa(30, 2);
        Graph<String, edge> g = result.getGraph();

        // Should work with ShortestPathFinder
        ShortestPathFinder spf = new ShortestPathFinder(g);
        ShortestPathFinder.PathResult path = spf.findShortestByHops("n0", "n10");
        assertNotNull(path);
        assertTrue(path.getVertices().size() >= 2);

        // Should work with PageRankAnalyzer
        PageRankAnalyzer pra = new PageRankAnalyzer(g);
        pra.compute();
        List<PageRankAnalyzer.PageRankResult> ranks = pra.getRankedResults();
        assertEquals(30, ranks.size());

        // All ranks should be positive
        for (PageRankAnalyzer.PageRankResult pr : ranks) {
            assertTrue(pr.getRank() > 0);
        }
    }

    @Test
    public void generatedGraphs_workWithGraphStats() {
        GraphGenerator.GeneratedGraph result = gen.ring(10);
        Graph<String, edge> g = result.getGraph();

        // Build edge lists (all "f" type in generated graphs)
        List<edge> allEdges = new ArrayList<>(g.getEdges());
        List<edge> empty = Collections.emptyList();

        GraphStats stats = new GraphStats(g, allEdges, empty, empty, empty, empty);
        assertEquals(10, stats.getNodeCount());
        assertEquals(10, stats.getTotalEdgeCount());
        assertEquals(2.0, stats.getAverageDegree(), 0.001);
    }
}
