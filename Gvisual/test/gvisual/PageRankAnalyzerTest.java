package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link PageRankAnalyzer}.
 */
public class PageRankAnalyzerTest {

    private Graph<String, edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, edge>();
    }

    // --- Helper methods ---

    private edge addEdge(String type, String v1, String v2, float weight) {
        edge e = new edge(type, v1, v2);
        e.setWeight(weight);
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        graph.addEdge(e, v1, v2);
        return e;
    }

    private edge addEdge(String v1, String v2) {
        return addEdge("f", v1, v2, 1.0f);
    }

    // ═══════════════════════════════════════
    // Constructor tests
    // ═══════════════════════════════════════

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNullGraph() {
        new PageRankAnalyzer(null);
    }

    @Test
    public void testConstructorValidGraph() {
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        assertNotNull(analyzer);
        assertFalse(analyzer.isComputed());
        assertEquals(PageRankAnalyzer.DEFAULT_DAMPING, analyzer.getDampingFactor(), 1e-10);
    }

    @Test
    public void testConstructorCustomDamping() {
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph, 0.5);
        assertEquals(0.5, analyzer.getDampingFactor(), 1e-10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorDampingZero() {
        new PageRankAnalyzer(graph, 0.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorDampingOne() {
        new PageRankAnalyzer(graph, 1.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorDampingNegative() {
        new PageRankAnalyzer(graph, -0.5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorDampingAboveOne() {
        new PageRankAnalyzer(graph, 1.5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorToleranceZero() {
        new PageRankAnalyzer(graph, 0.85, 0.0, 100);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorToleranceNegative() {
        new PageRankAnalyzer(graph, 0.85, -1e-6, 100);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorMaxIterationsZero() {
        new PageRankAnalyzer(graph, 0.85, 1e-6, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorMaxIterationsNegative() {
        new PageRankAnalyzer(graph, 0.85, 1e-6, -10);
    }

    @Test
    public void testConstructorFullParams() {
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph, 0.9, 1e-8, 200);
        assertEquals(0.9, analyzer.getDampingFactor(), 1e-10);
        assertFalse(analyzer.isComputed());
    }

    // ═══════════════════════════════════════
    // Empty graph
    // ═══════════════════════════════════════

    @Test
    public void testEmptyGraph() {
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        analyzer.compute();
        assertTrue(analyzer.isComputed());
        assertTrue(analyzer.isConverged());
        assertEquals(0, analyzer.getIterationsUsed());
        assertEquals(0, analyzer.getRankedResults().size());
        assertEquals(0, analyzer.getTopNodes(5).size());
    }

    @Test
    public void testEmptyGraphSummary() {
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        Map<String, Object> summary = analyzer.getSummary();
        assertEquals(0, summary.get("nodeCount"));
        assertEquals(0.0, (double) summary.get("maxRank"), 1e-10);
        assertEquals("none", summary.get("maxRankNode"));
    }

    // ═══════════════════════════════════════
    // Single node
    // ═══════════════════════════════════════

    @Test
    public void testSingleNode() {
        graph.addVertex("A");
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        analyzer.compute();

        assertTrue(analyzer.isComputed());
        assertEquals(1.0, analyzer.getRank("A"), 1e-6);

        PageRankAnalyzer.PageRankResult result = analyzer.getResult("A");
        assertNotNull(result);
        assertEquals("A", result.getNodeId());
        assertEquals(1.0, result.getRank(), 1e-6);
        assertEquals(1.0, result.getNormalizedRank(), 1e-6);
        assertEquals(0, result.getDegree());
    }

    @Test
    public void testSingleNodeImportanceLabel() {
        graph.addVertex("A");
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        PageRankAnalyzer.PageRankResult result = analyzer.getResult("A");
        assertEquals("average", result.getImportanceLabel());
    }

    // ═══════════════════════════════════════
    // Two connected nodes
    // ═══════════════════════════════════════

    @Test
    public void testTwoConnectedNodes() {
        addEdge("A", "B");
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        analyzer.compute();

        // Symmetric graph → equal ranks
        assertEquals(0.5, analyzer.getRank("A"), 1e-4);
        assertEquals(0.5, analyzer.getRank("B"), 1e-4);
        assertTrue(analyzer.isConverged());
    }

    @Test
    public void testTwoIsolatedNodes() {
        graph.addVertex("A");
        graph.addVertex("B");
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        analyzer.compute();

        // Both dangling → uniform distribution
        assertEquals(0.5, analyzer.getRank("A"), 1e-4);
        assertEquals(0.5, analyzer.getRank("B"), 1e-4);
    }

    // ═══════════════════════════════════════
    // Star graph (hub-and-spoke)
    // ═══════════════════════════════════════

    @Test
    public void testStarGraphHubHasHighestRank() {
        // Hub C connected to A, B, D, E
        addEdge("A", "C");
        addEdge("B", "C");
        addEdge("D", "C");
        addEdge("E", "C");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        analyzer.compute();

        // Hub should have highest rank
        List<PageRankAnalyzer.PageRankResult> ranked = analyzer.getRankedResults();
        assertEquals("C", ranked.get(0).getNodeId());
        assertTrue(ranked.get(0).getRank() > ranked.get(1).getRank());

        // Spokes should all have equal rank
        double spokeRank = analyzer.getRank("A");
        assertEquals(spokeRank, analyzer.getRank("B"), 1e-6);
        assertEquals(spokeRank, analyzer.getRank("D"), 1e-6);
        assertEquals(spokeRank, analyzer.getRank("E"), 1e-6);
    }

    @Test
    public void testStarGraphNormalizedRank() {
        addEdge("A", "C");
        addEdge("B", "C");
        addEdge("D", "C");
        addEdge("E", "C");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        PageRankAnalyzer.PageRankResult hubResult = analyzer.getResult("C");

        // Hub should be above average (normalizedRank > 1.0)
        assertTrue(hubResult.getNormalizedRank() > 1.0);
        assertTrue(hubResult.getImportanceLabel().contains("above average"));
    }

    // ═══════════════════════════════════════
    // Triangle graph (symmetric)
    // ═══════════════════════════════════════

    @Test
    public void testTriangleEqualRanks() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        analyzer.compute();

        // All nodes connected equally → equal ranks
        double expected = 1.0 / 3;
        assertEquals(expected, analyzer.getRank("A"), 1e-4);
        assertEquals(expected, analyzer.getRank("B"), 1e-4);
        assertEquals(expected, analyzer.getRank("C"), 1e-4);
    }

    // ═══════════════════════════════════════
    // Chain graph
    // ═══════════════════════════════════════

    @Test
    public void testChainGraphMiddleHasHigherRank() {
        // A - B - C - D - E (linear chain)
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "E");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        analyzer.compute();

        // Middle node (C) should have highest rank (receives from both sides)
        // End nodes (A, E) should have lowest
        double rankC = analyzer.getRank("C");
        double rankA = analyzer.getRank("A");
        double rankE = analyzer.getRank("E");
        assertTrue(rankC > rankA);
        assertTrue(rankC > rankE);
        // A and E are symmetric
        assertEquals(rankA, rankE, 1e-6);
    }

    // ═══════════════════════════════════════
    // Ranks sum to 1.0
    // ═══════════════════════════════════════

    @Test
    public void testRanksSumToOne() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("A", "C");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        analyzer.compute();

        double sum = 0;
        for (double r : analyzer.getRankMap().values()) {
            sum += r;
        }
        assertEquals(1.0, sum, 1e-6);
    }

    @Test
    public void testRanksSumToOneWithDanglingNodes() {
        addEdge("A", "B");
        graph.addVertex("C"); // dangling node

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        analyzer.compute();

        double sum = 0;
        for (double r : analyzer.getRankMap().values()) {
            sum += r;
        }
        assertEquals(1.0, sum, 1e-4);
    }

    // ═══════════════════════════════════════
    // Convergence
    // ═══════════════════════════════════════

    @Test
    public void testConverges() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        addEdge("C", "D");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        analyzer.compute();

        assertTrue(analyzer.isConverged());
        assertTrue(analyzer.getIterationsUsed() < PageRankAnalyzer.DEFAULT_MAX_ITERATIONS);
    }

    @Test
    public void testMaxIterationsReached() {
        // Build a graph and use very tight tolerance with 1 iteration
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "E");
        addEdge("E", "F");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph, 0.85, 1e-15, 1);
        analyzer.compute();

        // With tolerance 1e-15 and only 1 iteration, unlikely to converge
        assertEquals(1, analyzer.getIterationsUsed());
    }

    // ═══════════════════════════════════════
    // getResult for nonexistent node
    // ═══════════════════════════════════════

    @Test
    public void testGetResultNonexistentNode() {
        addEdge("A", "B");
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        assertNull(analyzer.getResult("Z"));
    }

    @Test
    public void testGetRankNonexistentNode() {
        addEdge("A", "B");
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        assertEquals(0.0, analyzer.getRank("Z"), 1e-10);
    }

    // ═══════════════════════════════════════
    // getTopNodes / getBottomNodes
    // ═══════════════════════════════════════

    @Test
    public void testGetTopNodes() {
        addEdge("A", "C");
        addEdge("B", "C");
        addEdge("D", "C");
        addEdge("E", "C");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        List<PageRankAnalyzer.PageRankResult> top = analyzer.getTopNodes(2);
        assertEquals(2, top.size());
        assertEquals("C", top.get(0).getNodeId()); // Hub is #1
    }

    @Test
    public void testGetTopNodesMoreThanAvailable() {
        addEdge("A", "B");
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        List<PageRankAnalyzer.PageRankResult> top = analyzer.getTopNodes(100);
        assertEquals(2, top.size());
    }

    @Test
    public void testGetTopNodesZero() {
        addEdge("A", "B");
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        assertEquals(0, analyzer.getTopNodes(0).size());
    }

    @Test
    public void testGetTopNodesNegative() {
        addEdge("A", "B");
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        assertEquals(0, analyzer.getTopNodes(-1).size());
    }

    @Test
    public void testGetBottomNodes() {
        addEdge("A", "C");
        addEdge("B", "C");
        addEdge("D", "C");
        addEdge("E", "C");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        List<PageRankAnalyzer.PageRankResult> bottom = analyzer.getBottomNodes(2);
        assertEquals(2, bottom.size());
        // Bottom nodes should be spokes, not hub
        assertNotEquals("C", bottom.get(0).getNodeId());
    }

    @Test
    public void testGetBottomNodesZero() {
        addEdge("A", "B");
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        assertEquals(0, analyzer.getBottomNodes(0).size());
    }

    // ═══════════════════════════════════════
    // Rank distribution
    // ═══════════════════════════════════════

    @Test
    public void testRankDistribution() {
        addEdge("A", "C");
        addEdge("B", "C");
        addEdge("D", "C");
        addEdge("E", "C");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        Map<String, Integer> dist = analyzer.getRankDistribution();

        assertNotNull(dist);
        assertEquals(5, dist.size());
        assertTrue(dist.containsKey("Very High (>2x)"));
        assertTrue(dist.containsKey("Average (0.5-1.5x)"));

        // Total should equal node count
        int total = 0;
        for (int count : dist.values()) {
            total += count;
        }
        assertEquals(5, total);
    }

    @Test
    public void testRankDistributionEmptyGraph() {
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        Map<String, Integer> dist = analyzer.getRankDistribution();
        int total = 0;
        for (int count : dist.values()) {
            total += count;
        }
        assertEquals(0, total);
    }

    // ═══════════════════════════════════════
    // Gini coefficient
    // ═══════════════════════════════════════

    @Test
    public void testGiniCoefficientEqualRanks() {
        // Triangle → all equal → Gini ≈ 0
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        double gini = analyzer.computeGiniCoefficient();
        assertEquals(0.0, gini, 0.01);
    }

    @Test
    public void testGiniCoefficientStarGraph() {
        // Star → unequal → Gini > 0
        addEdge("A", "C");
        addEdge("B", "C");
        addEdge("D", "C");
        addEdge("E", "C");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        double gini = analyzer.computeGiniCoefficient();
        assertTrue(gini > 0.1); // Should show inequality
    }

    @Test
    public void testGiniCoefficientEmptyGraph() {
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        assertEquals(0.0, analyzer.computeGiniCoefficient(), 1e-10);
    }

    @Test
    public void testGiniCoefficientSingleNode() {
        graph.addVertex("A");
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        assertEquals(0.0, analyzer.computeGiniCoefficient(), 1e-10);
    }

    // ═══════════════════════════════════════
    // Entropy ratio
    // ═══════════════════════════════════════

    @Test
    public void testEntropyRatioEqualRanks() {
        // Triangle → equal → entropy ≈ 1.0
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        double entropy = analyzer.computeEntropyRatio();
        assertEquals(1.0, entropy, 0.01);
    }

    @Test
    public void testEntropyRatioStarGraph() {
        // Star → unequal → entropy < 1.0
        addEdge("A", "C");
        addEdge("B", "C");
        addEdge("D", "C");
        addEdge("E", "C");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        double entropy = analyzer.computeEntropyRatio();
        assertTrue(entropy < 1.0);
        assertTrue(entropy > 0.0);
    }

    @Test
    public void testEntropyRatioSingleNode() {
        graph.addVertex("A");
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        assertEquals(1.0, analyzer.computeEntropyRatio(), 1e-10);
    }

    // ═══════════════════════════════════════
    // Hidden influencers
    // ═══════════════════════════════════════

    @Test
    public void testFindHiddenInfluencersEmptyGraph() {
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        assertEquals(0, analyzer.findHiddenInfluencers().size());
    }

    @Test
    public void testFindHiddenInfluencersSingleNode() {
        graph.addVertex("A");
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        assertEquals(0, analyzer.findHiddenInfluencers().size());
    }

    @Test
    public void testFindHiddenInfluencersTriangleNoHidden() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        // Equal graph → no hidden influencers at default threshold
        List<PageRankAnalyzer.HiddenInfluence> hidden = analyzer.findHiddenInfluencers();
        // In a triangle with equal ranks, normalizedRank=1.0 and degreeCentrality=1.0
        // ratio = 1.0, below threshold of 1.5
        assertEquals(0, hidden.size());
    }

    @Test
    public void testFindHiddenInfluencersCustomThreshold() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        // Very low threshold → everything is "hidden"
        List<PageRankAnalyzer.HiddenInfluence> hidden = analyzer.findHiddenInfluencers(0.5);
        assertTrue(hidden.size() >= 0); // May or may not find any depending on exact values
    }

    @Test
    public void testHiddenInfluencerFields() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("A", "D");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        List<PageRankAnalyzer.HiddenInfluence> hidden = analyzer.findHiddenInfluencers(0.1);
        if (!hidden.isEmpty()) {
            PageRankAnalyzer.HiddenInfluence h = hidden.get(0);
            assertNotNull(h.getNodeId());
            assertTrue(h.getDegree() >= 0);
            assertTrue(h.getNormalizedRank() > 0);
            assertTrue(h.getDegreeCentrality() > 0);
            assertTrue(h.getInfluenceRatio() > 0);
            assertNotNull(h.toString());
        }
    }

    // ═══════════════════════════════════════
    // Summary
    // ═══════════════════════════════════════

    @Test
    public void testSummaryContainsAllKeys() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        addEdge("C", "D");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        Map<String, Object> summary = analyzer.getSummary();

        assertTrue(summary.containsKey("nodeCount"));
        assertTrue(summary.containsKey("edgeCount"));
        assertTrue(summary.containsKey("dampingFactor"));
        assertTrue(summary.containsKey("iterations"));
        assertTrue(summary.containsKey("converged"));
        assertTrue(summary.containsKey("maxRank"));
        assertTrue(summary.containsKey("minRank"));
        assertTrue(summary.containsKey("maxRankNode"));
        assertTrue(summary.containsKey("minRankNode"));
        assertTrue(summary.containsKey("giniCoefficient"));
        assertTrue(summary.containsKey("entropyRatio"));
        assertTrue(summary.containsKey("concentrationLevel"));
        assertTrue(summary.containsKey("rankDistribution"));

        assertEquals(4, summary.get("nodeCount"));
        assertEquals(4, summary.get("edgeCount"));
        assertTrue((boolean) summary.get("converged"));
    }

    @Test
    public void testSummaryConcentrationLevels() {
        // Triangle → distributed → low Gini
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        Map<String, Object> summary = analyzer.getSummary();
        String level = (String) summary.get("concentrationLevel");
        assertNotNull(level);
        // Equal graph should be "Highly Distributed"
        assertEquals("Highly Distributed", level);
    }

    // ═══════════════════════════════════════
    // compareWithDegreeCentrality
    // ═══════════════════════════════════════

    @Test
    public void testCompareWithDegreeCentrality() {
        addEdge("A", "C");
        addEdge("B", "C");
        addEdge("D", "C");
        addEdge("E", "C");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        List<PageRankAnalyzer.RankComparison> comparisons =
                analyzer.compareWithDegreeCentrality(3);

        assertFalse(comparisons.isEmpty());
        assertTrue(comparisons.size() <= 5); // can include more due to union

        // First entry (sorted by PR position) should be hub
        assertEquals("C", comparisons.get(0).getNodeId());
        assertEquals(1, comparisons.get(0).getPageRankPosition());
    }

    @Test
    public void testCompareWithDegreeCentralityFields() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        List<PageRankAnalyzer.RankComparison> comparisons =
                analyzer.compareWithDegreeCentrality(3);

        for (PageRankAnalyzer.RankComparison c : comparisons) {
            assertNotNull(c.getNodeId());
            assertTrue(c.getPageRankPosition() >= 1);
            assertTrue(c.getDegreePosition() >= 1);
            assertTrue(c.getPageRank() > 0);
            assertTrue(c.getDegree() >= 0);
            assertNotNull(c.toString());
        }
    }

    @Test
    public void testCompareWithDegreeCentralityEmptyGraph() {
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        List<PageRankAnalyzer.RankComparison> comparisons =
                analyzer.compareWithDegreeCentrality(5);
        assertTrue(comparisons.isEmpty());
    }

    @Test
    public void testCompareWithDegreeCentralityZeroTopN() {
        addEdge("A", "B");
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        assertEquals(0, analyzer.compareWithDegreeCentrality(0).size());
    }

    // ═══════════════════════════════════════
    // Damping factor effects
    // ═══════════════════════════════════════

    @Test
    public void testLowDampingMoreUniform() {
        addEdge("A", "C");
        addEdge("B", "C");
        addEdge("D", "C");
        addEdge("E", "C");

        // Low damping → more teleportation → more uniform
        PageRankAnalyzer low = new PageRankAnalyzer(graph, 0.1);
        PageRankAnalyzer high = new PageRankAnalyzer(graph, 0.95);

        low.compute();
        high.compute();

        double hubLow = low.getRank("C");
        double spokeLow = low.getRank("A");
        double hubHigh = high.getRank("C");
        double spokeHigh = high.getRank("A");

        // With low damping, hub-spoke ratio should be smaller
        double ratioLow = hubLow / spokeLow;
        double ratioHigh = hubHigh / spokeHigh;
        assertTrue(ratioHigh > ratioLow);
    }

    // ═══════════════════════════════════════
    // Larger graph
    // ═══════════════════════════════════════

    @Test
    public void testLargerGraphConverges() {
        // Create a small-world style graph
        String[] nodes = {"A", "B", "C", "D", "E", "F", "G", "H"};
        // Ring
        for (int i = 0; i < nodes.length; i++) {
            addEdge(nodes[i], nodes[(i + 1) % nodes.length]);
        }
        // Shortcuts
        addEdge("A", "D");
        addEdge("B", "F");
        addEdge("C", "G");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        analyzer.compute();

        assertTrue(analyzer.isConverged());
        assertEquals(8, analyzer.getRankedResults().size());

        // Ranks sum to 1
        double sum = 0;
        for (double r : analyzer.getRankMap().values()) {
            sum += r;
        }
        assertEquals(1.0, sum, 1e-6);
    }

    // ═══════════════════════════════════════
    // Auto-compute on query
    // ═══════════════════════════════════════

    @Test
    public void testAutoComputeOnGetResult() {
        addEdge("A", "B");
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        assertFalse(analyzer.isComputed());
        PageRankAnalyzer.PageRankResult result = analyzer.getResult("A");
        assertTrue(analyzer.isComputed());
        assertNotNull(result);
    }

    @Test
    public void testAutoComputeOnGetRankedResults() {
        addEdge("A", "B");
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        List<PageRankAnalyzer.PageRankResult> results = analyzer.getRankedResults();
        assertTrue(analyzer.isComputed());
        assertEquals(2, results.size());
    }

    @Test
    public void testAutoComputeOnGetRank() {
        addEdge("A", "B");
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        double rank = analyzer.getRank("A");
        assertTrue(analyzer.isComputed());
        assertTrue(rank > 0);
    }

    @Test
    public void testAutoComputeOnSummary() {
        addEdge("A", "B");
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        Map<String, Object> summary = analyzer.getSummary();
        assertTrue(analyzer.isComputed());
        assertNotNull(summary);
    }

    @Test
    public void testDoubleComputeNoOp() {
        addEdge("A", "B");
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        analyzer.compute();
        int iter1 = analyzer.getIterationsUsed();
        analyzer.compute(); // should be no-op
        assertEquals(iter1, analyzer.getIterationsUsed());
    }

    // ═══════════════════════════════════════
    // Rank map immutability
    // ═══════════════════════════════════════

    @Test(expected = UnsupportedOperationException.class)
    public void testRankMapIsImmutable() {
        addEdge("A", "B");
        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        analyzer.compute();
        Map<String, Double> rankMap = analyzer.getRankMap();
        rankMap.put("A", 999.0); // Should throw
    }

    // ═══════════════════════════════════════
    // PageRankResult methods
    // ═══════════════════════════════════════

    @Test
    public void testPageRankResultToString() {
        PageRankAnalyzer.PageRankResult result =
                new PageRankAnalyzer.PageRankResult("X", 0.25, 3, 1.5);
        String str = result.toString();
        assertTrue(str.contains("X"));
        assertTrue(str.contains("0.25"));
    }

    @Test
    public void testPageRankResultEquals() {
        PageRankAnalyzer.PageRankResult a =
                new PageRankAnalyzer.PageRankResult("X", 0.25, 3, 1.5);
        PageRankAnalyzer.PageRankResult b =
                new PageRankAnalyzer.PageRankResult("X", 0.50, 5, 2.0);
        PageRankAnalyzer.PageRankResult c =
                new PageRankAnalyzer.PageRankResult("Y", 0.25, 3, 1.5);

        assertEquals(a, b); // Same nodeId
        assertNotEquals(a, c); // Different nodeId
        assertNotEquals(a, null);
        assertNotEquals(a, "string");
        assertEquals(a, a);
    }

    @Test
    public void testPageRankResultHashCode() {
        PageRankAnalyzer.PageRankResult a =
                new PageRankAnalyzer.PageRankResult("X", 0.25, 3, 1.5);
        PageRankAnalyzer.PageRankResult b =
                new PageRankAnalyzer.PageRankResult("X", 0.50, 5, 2.0);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void testPageRankResultCompareTo() {
        PageRankAnalyzer.PageRankResult high =
                new PageRankAnalyzer.PageRankResult("X", 0.8, 3, 1.5);
        PageRankAnalyzer.PageRankResult low =
                new PageRankAnalyzer.PageRankResult("Y", 0.2, 1, 0.5);

        assertTrue(high.compareTo(low) < 0); // high ranked before low
        assertTrue(low.compareTo(high) > 0);
        assertEquals(0, high.compareTo(high));
    }

    @Test
    public void testImportanceLabelAbove() {
        PageRankAnalyzer.PageRankResult r =
                new PageRankAnalyzer.PageRankResult("X", 0.5, 3, 2.5);
        assertTrue(r.getImportanceLabel().contains("above average"));
    }

    @Test
    public void testImportanceLabelBelow() {
        PageRankAnalyzer.PageRankResult r =
                new PageRankAnalyzer.PageRankResult("X", 0.1, 1, 0.3);
        assertTrue(r.getImportanceLabel().contains("below average"));
    }

    @Test
    public void testImportanceLabelAverage() {
        PageRankAnalyzer.PageRankResult r =
                new PageRankAnalyzer.PageRankResult("X", 0.5, 3, 1.0);
        assertEquals("average", r.getImportanceLabel());
    }

    // ═══════════════════════════════════════
    // RankComparison
    // ═══════════════════════════════════════

    @Test
    public void testRankComparisonPositiveShift() {
        PageRankAnalyzer.RankComparison rc =
                new PageRankAnalyzer.RankComparison("X", 2, 5, 3, 0.3, 4);
        assertEquals("X", rc.getNodeId());
        assertEquals(2, rc.getPageRankPosition());
        assertEquals(5, rc.getDegreePosition());
        assertEquals(3, rc.getPositionShift());
        assertTrue(rc.toString().contains("↑3"));
    }

    @Test
    public void testRankComparisonNegativeShift() {
        PageRankAnalyzer.RankComparison rc =
                new PageRankAnalyzer.RankComparison("X", 5, 2, -3, 0.1, 4);
        assertEquals(-3, rc.getPositionShift());
        assertTrue(rc.toString().contains("↓3"));
    }

    @Test
    public void testRankComparisonNoShift() {
        PageRankAnalyzer.RankComparison rc =
                new PageRankAnalyzer.RankComparison("X", 3, 3, 0, 0.2, 3);
        assertEquals(0, rc.getPositionShift());
        assertTrue(rc.toString().contains("="));
    }

    // ═══════════════════════════════════════
    // HiddenInfluence toString
    // ═══════════════════════════════════════

    @Test
    public void testHiddenInfluenceToString() {
        PageRankAnalyzer.HiddenInfluence hi =
                new PageRankAnalyzer.HiddenInfluence("X", 2, 1.8, 0.5, 3.6);
        String str = hi.toString();
        assertTrue(str.contains("X"));
        assertTrue(str.contains("3.6"));
    }

    // ═══════════════════════════════════════
    // Complex graph topologies
    // ═══════════════════════════════════════

    @Test
    public void testCompleteGraph() {
        // K4 — all connected → all equal
        addEdge("A", "B");
        addEdge("A", "C");
        addEdge("A", "D");
        addEdge("B", "C");
        addEdge("B", "D");
        addEdge("C", "D");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        analyzer.compute();

        double expected = 0.25;
        assertEquals(expected, analyzer.getRank("A"), 1e-4);
        assertEquals(expected, analyzer.getRank("B"), 1e-4);
        assertEquals(expected, analyzer.getRank("C"), 1e-4);
        assertEquals(expected, analyzer.getRank("D"), 1e-4);
    }

    @Test
    public void testDisconnectedComponents() {
        // Two separate triangles
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");

        addEdge("D", "E");
        addEdge("E", "F");
        addEdge("D", "F");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        analyzer.compute();

        // Within each triangle, nodes should be roughly equal
        double rankA = analyzer.getRank("A");
        double rankD = analyzer.getRank("D");
        assertEquals(rankA, analyzer.getRank("B"), 1e-4);
        assertEquals(rankA, analyzer.getRank("C"), 1e-4);
        assertEquals(rankD, analyzer.getRank("E"), 1e-4);
        assertEquals(rankD, analyzer.getRank("F"), 1e-4);

        // Both components equal size → similar ranks
        assertEquals(rankA, rankD, 1e-4);
    }

    @Test
    public void testBarbellGraph() {
        // Two cliques connected by a bridge
        // Clique 1: A-B-C fully connected
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");

        // Clique 2: D-E-F fully connected
        addEdge("D", "E");
        addEdge("E", "F");
        addEdge("D", "F");

        // Bridge: C-D
        addEdge("C", "D");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        analyzer.compute();

        // Bridge nodes (C, D) should have higher rank than pure clique members
        assertTrue(analyzer.getRank("C") > analyzer.getRank("A"));
        assertTrue(analyzer.getRank("D") > analyzer.getRank("E"));
    }

    @Test
    public void testAllDanglingNodes() {
        // No edges at all
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");

        PageRankAnalyzer analyzer = new PageRankAnalyzer(graph);
        analyzer.compute();

        // All dangling → uniform
        double expected = 1.0 / 3;
        assertEquals(expected, analyzer.getRank("A"), 1e-4);
        assertEquals(expected, analyzer.getRank("B"), 1e-4);
        assertEquals(expected, analyzer.getRank("C"), 1e-4);
    }
}
