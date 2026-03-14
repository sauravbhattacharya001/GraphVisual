package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link GraphNeighborhoodAnalyzer}.
 */
public class GraphNeighborhoodAnalyzerTest {

    private Graph<String, edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
    }

    private edge addEdge(String v1, String v2) {
        edge e = new edge("f", v1, v2);
        e.setWeight(1.0f);
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        graph.addEdge(e, v1, v2);
        return e;
    }

    private void buildPath(int n) {
        for (int i = 0; i < n; i++) graph.addVertex(String.valueOf(i));
        for (int i = 0; i < n - 1; i++)
            addEdge(String.valueOf(i), String.valueOf(i + 1));
    }

    private void buildComplete(int n) {
        for (int i = 0; i < n; i++) graph.addVertex(String.valueOf(i));
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                addEdge(String.valueOf(i), String.valueOf(j));
    }

    private void buildStar(int n) {
        graph.addVertex("c");
        for (int i = 0; i < n; i++) {
            graph.addVertex(String.valueOf(i));
            addEdge("c", String.valueOf(i));
        }
    }

    private void buildCycle(int n) {
        for (int i = 0; i < n; i++) graph.addVertex(String.valueOf(i));
        for (int i = 0; i < n; i++)
            addEdge(String.valueOf(i), String.valueOf((i + 1) % n));
    }

    // ------------------------------------------------------------------
    //  Constructor validation
    // ------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraph() {
        new GraphNeighborhoodAnalyzer(null);
    }

    // ------------------------------------------------------------------
    //  computeLayers
    // ------------------------------------------------------------------

    @Test
    public void testLayersSingleVertex() {
        graph.addVertex("A");
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        List<Set<String>> layers = analyzer.computeLayers("A", 3);
        assertEquals(1, layers.size());
        assertTrue(layers.get(0).contains("A"));
    }

    @Test
    public void testLayersPath5() {
        buildPath(5); // 0-1-2-3-4
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        List<Set<String>> layers = analyzer.computeLayers("0", 10);
        assertEquals(5, layers.size());
        assertEquals(Collections.singleton("0"), layers.get(0));
        assertEquals(Collections.singleton("1"), layers.get(1));
        assertEquals(Collections.singleton("2"), layers.get(2));
        assertEquals(Collections.singleton("3"), layers.get(3));
        assertEquals(Collections.singleton("4"), layers.get(4));
    }

    @Test
    public void testLayersPath5FromMiddle() {
        buildPath(5); // 0-1-2-3-4
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        List<Set<String>> layers = analyzer.computeLayers("2", 10);
        assertEquals(3, layers.size()); // depth 0, 1, 2
        assertEquals(Collections.singleton("2"), layers.get(0));
        assertTrue(layers.get(1).contains("1"));
        assertTrue(layers.get(1).contains("3"));
        assertEquals(2, layers.get(1).size());
        assertTrue(layers.get(2).contains("0"));
        assertTrue(layers.get(2).contains("4"));
    }

    @Test
    public void testLayersCompleteGraph() {
        buildComplete(5);
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        List<Set<String>> layers = analyzer.computeLayers("0", 5);
        assertEquals(2, layers.size()); // depth 0 and 1
        assertEquals(1, layers.get(0).size());
        assertEquals(4, layers.get(1).size());
    }

    @Test
    public void testLayersMaxKZero() {
        buildPath(3);
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        List<Set<String>> layers = analyzer.computeLayers("0", 0);
        assertEquals(1, layers.size());
        assertEquals(Collections.singleton("0"), layers.get(0));
    }

    @Test
    public void testLayersMaxKLimitsDepth() {
        buildPath(5); // 0-1-2-3-4
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        List<Set<String>> layers = analyzer.computeLayers("0", 2);
        assertEquals(3, layers.size()); // 0, 1, 2 only
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLayersInvalidVertex() {
        buildPath(3);
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        analyzer.computeLayers("Z", 2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLayersNegativeK() {
        buildPath(3);
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        analyzer.computeLayers("0", -1);
    }

    // ------------------------------------------------------------------
    //  getKHopNeighborhood
    // ------------------------------------------------------------------

    @Test
    public void testKHopPath() {
        buildPath(5); // 0-1-2-3-4
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        Set<String> hood = analyzer.getKHopNeighborhood("0", 2);
        assertEquals(3, hood.size());
        assertTrue(hood.containsAll(Arrays.asList("0", "1", "2")));
    }

    @Test
    public void testKHopComplete() {
        buildComplete(4);
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        Set<String> hood = analyzer.getKHopNeighborhood("0", 1);
        assertEquals(4, hood.size()); // all vertices within 1 hop
    }

    @Test
    public void testKHopDisconnected() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        addEdge("A", "B");
        // C is disconnected
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        Set<String> hood = analyzer.getKHopNeighborhood("A", 5);
        assertEquals(2, hood.size());
        assertTrue(hood.contains("A"));
        assertTrue(hood.contains("B"));
        assertFalse(hood.contains("C"));
    }

    @Test
    public void testKHopZero() {
        buildPath(3);
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        Set<String> hood = analyzer.getKHopNeighborhood("1", 0);
        assertEquals(1, hood.size());
        assertTrue(hood.contains("1"));
    }

    // ------------------------------------------------------------------
    //  getBoundary
    // ------------------------------------------------------------------

    @Test
    public void testBoundaryPath() {
        buildPath(5); // 0-1-2-3-4
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        Set<String> boundary = analyzer.getBoundary("0", 3);
        assertEquals(1, boundary.size());
        assertTrue(boundary.contains("3"));
    }

    @Test
    public void testBoundaryStar() {
        buildStar(4); // c-0, c-1, c-2, c-3
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        Set<String> boundary = analyzer.getBoundary("c", 1);
        assertEquals(4, boundary.size());
    }

    @Test
    public void testBoundaryBeyondEccentricity() {
        buildPath(3); // 0-1-2
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        Set<String> boundary = analyzer.getBoundary("0", 10);
        assertTrue(boundary.isEmpty());
    }

    // ------------------------------------------------------------------
    //  getGrowthProfile
    // ------------------------------------------------------------------

    @Test
    public void testGrowthProfilePath() {
        buildPath(5); // 0-1-2-3-4
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        List<Integer> profile = analyzer.getGrowthProfile("0", 10);
        // Each hop adds 1 vertex: [1, 2, 3, 4, 5]
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), profile);
    }

    @Test
    public void testGrowthProfileStar() {
        buildStar(5);
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        List<Integer> profile = analyzer.getGrowthProfile("c", 3);
        // Depth 0: {c} = 1, Depth 1: {0..4} = 6
        assertEquals(Arrays.asList(1, 6), profile);
    }

    @Test
    public void testGrowthProfileComplete() {
        buildComplete(5);
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        List<Integer> profile = analyzer.getGrowthProfile("0", 5);
        assertEquals(Arrays.asList(1, 5), profile);
    }

    // ------------------------------------------------------------------
    //  getExpansionRates
    // ------------------------------------------------------------------

    @Test
    public void testExpansionRatesPath() {
        buildPath(4); // 0-1-2-3
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        List<Double> rates = analyzer.getExpansionRates("0", 10);
        assertEquals(4, rates.size());
        assertEquals(0.0, rates.get(0), 0.001); // depth 0: no expansion
        assertEquals(1.0, rates.get(1), 0.001); // depth 1: 1 new / 1 prior
        assertEquals(0.5, rates.get(2), 0.001); // depth 2: 1 new / 2 prior
        assertEquals(1.0 / 3, rates.get(3), 0.001); // depth 3: 1 new / 3 prior
    }

    @Test
    public void testExpansionRatesStar() {
        buildStar(4);
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        List<Double> rates = analyzer.getExpansionRates("c", 3);
        assertEquals(2, rates.size());
        assertEquals(0.0, rates.get(0), 0.001);
        assertEquals(4.0, rates.get(1), 0.001); // 4 new / 1 prior
    }

    // ------------------------------------------------------------------
    //  getLocalDensity
    // ------------------------------------------------------------------

    @Test
    public void testLocalDensityComplete() {
        buildComplete(4);
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        double density = analyzer.getLocalDensity("0", 1);
        assertEquals(1.0, density, 0.001); // K4 is fully connected
    }

    @Test
    public void testLocalDensityPath() {
        buildPath(5); // 0-1-2-3-4
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        // k=1 from vertex 2: neighborhood = {1,2,3}, edges = 1-2, 2-3 = 2 edges
        // density = 2*2 / (3*2) = 0.667
        double density = analyzer.getLocalDensity("2", 1);
        assertEquals(2.0 / 3, density, 0.001);
    }

    @Test
    public void testLocalDensitySingleVertex() {
        graph.addVertex("A");
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        assertEquals(1.0, analyzer.getLocalDensity("A", 0), 0.001);
    }

    @Test
    public void testLocalDensityStar() {
        buildStar(3); // c connected to 0, 1, 2
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        // k=1 from c: neighborhood = {c,0,1,2}, 3 edges, max = 4*3/2 = 6
        double density = analyzer.getLocalDensity("c", 1);
        assertEquals(3.0 / 6, density, 0.001);
    }

    // ------------------------------------------------------------------
    //  getNeighborhoodOverlap
    // ------------------------------------------------------------------

    @Test
    public void testOverlapSameVertex() {
        buildPath(5);
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        assertEquals(1.0, analyzer.getNeighborhoodOverlap("0", "0", 2), 0.001);
    }

    @Test
    public void testOverlapAdjacentPath() {
        buildPath(5); // 0-1-2-3-4
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        // k=1: N(0)={0,1}, N(1)={0,1,2}
        // intersection = {0,1}, union = {0,1,2}
        double overlap = analyzer.getNeighborhoodOverlap("0", "1", 1);
        assertEquals(2.0 / 3, overlap, 0.001);
    }

    @Test
    public void testOverlapDisconnected() {
        graph.addVertex("A");
        graph.addVertex("B");
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        // N(A)={A}, N(B)={B}, intersection empty, union = {A,B}
        assertEquals(0.0, analyzer.getNeighborhoodOverlap("A", "B", 1), 0.001);
    }

    // ------------------------------------------------------------------
    //  getOverlapCoefficient
    // ------------------------------------------------------------------

    @Test
    public void testOverlapCoefficientSubset() {
        // A-B-C, k=2 from A = {A,B,C}, k=1 from B = {A,B,C}
        addEdge("A", "B");
        addEdge("B", "C");
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        // N2(A)={A,B,C}, N1(B)={A,B,C}, overlap coeff = 3/3 = 1.0
        assertEquals(1.0, analyzer.getOverlapCoefficient("A", "B", 2), 0.001);
    }

    @Test
    public void testOverlapCoefficientDisjoint() {
        graph.addVertex("A");
        graph.addVertex("B");
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        assertEquals(0.0, analyzer.getOverlapCoefficient("A", "B", 1), 0.001);
    }

    // ------------------------------------------------------------------
    //  getAggregateStats
    // ------------------------------------------------------------------

    @Test
    public void testAggregateStatsComplete() {
        buildComplete(4);
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        GraphNeighborhoodAnalyzer.AggregateStats stats = analyzer.getAggregateStats(1);
        assertEquals(1, stats.getK());
        assertEquals(4.0, stats.getMeanSize(), 0.001); // all see all
        assertEquals(4, stats.getMinSize());
        assertEquals(4, stats.getMaxSize());
        assertEquals(1.0, stats.getMeanDensity(), 0.001);
    }

    @Test
    public void testAggregateStatsPath() {
        buildPath(5); // 0-1-2-3-4
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        GraphNeighborhoodAnalyzer.AggregateStats stats = analyzer.getAggregateStats(1);
        // k=1 sizes: 0→2, 1→3, 2→3, 3→3, 4→2 → mean = 13/5 = 2.6
        assertEquals(2.6, stats.getMeanSize(), 0.001);
        assertEquals(2, stats.getMinSize());
        assertEquals(3, stats.getMaxSize());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAggregateStatsNegativeK() {
        buildPath(3);
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        analyzer.getAggregateStats(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAggregateStatsEmptyGraph() {
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        analyzer.getAggregateStats(1);
    }

    // ------------------------------------------------------------------
    //  rankByNeighborhoodSize
    // ------------------------------------------------------------------

    @Test
    public void testRankPath() {
        buildPath(5); // 0-1-2-3-4
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        List<Map.Entry<String, Integer>> ranked = analyzer.rankByNeighborhoodSize(1);
        assertEquals(5, ranked.size());
        // Top entries should have size 3 (vertices 1, 2, or 3)
        assertEquals(3, (int) ranked.get(0).getValue());
        // Last entries should have size 2 (vertices 0 or 4)
        assertEquals(2, (int) ranked.get(4).getValue());
    }

    @Test
    public void testRankStar() {
        buildStar(4); // c with leaves 0,1,2,3
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        List<Map.Entry<String, Integer>> ranked = analyzer.rankByNeighborhoodSize(1);
        // c has size 5 (c + 4 leaves), leaves have size 2 (self + c)
        assertEquals("c", ranked.get(0).getKey());
        assertEquals(5, (int) ranked.get(0).getValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRankNegativeK() {
        buildPath(3);
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        analyzer.rankByNeighborhoodSize(-1);
    }

    // ------------------------------------------------------------------
    //  getTextReport
    // ------------------------------------------------------------------

    @Test
    public void testTextReportNotEmpty() {
        buildPath(5);
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        String report = analyzer.getTextReport("2", 3);
        assertNotNull(report);
        assertTrue(report.contains("Neighborhood Analysis Report"));
        assertTrue(report.contains("Vertex '2'"));
        assertTrue(report.contains("Depth 0"));
        assertTrue(report.contains("Aggregate Statistics"));
    }

    @Test
    public void testTextReportNullSource() {
        buildComplete(3);
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        String report = analyzer.getTextReport(null, 2);
        assertNotNull(report);
        assertTrue(report.contains("Aggregate Statistics"));
        assertFalse(report.contains("Vertex"));
    }

    @Test
    public void testTextReportContainsExpansion() {
        buildPath(4);
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        String report = analyzer.getTextReport("0", 3);
        assertTrue(report.contains("expansion="));
    }

    // ------------------------------------------------------------------
    //  Edge case: cycle graph
    // ------------------------------------------------------------------

    @Test
    public void testCycleNeighborhood() {
        buildCycle(6); // 0-1-2-3-4-5-0
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        // k=1 from 0: {0, 1, 5}
        Set<String> hood = analyzer.getKHopNeighborhood("0", 1);
        assertEquals(3, hood.size());
        assertTrue(hood.containsAll(Arrays.asList("0", "1", "5")));

        // k=3 from 0: should reach all 6 vertices
        Set<String> fullHood = analyzer.getKHopNeighborhood("0", 3);
        assertEquals(6, fullHood.size());
    }

    @Test
    public void testCycleGrowthProfile() {
        buildCycle(6);
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        List<Integer> profile = analyzer.getGrowthProfile("0", 5);
        // [1, 3, 5, 6]
        assertEquals(4, profile.size());
        assertEquals(1, (int) profile.get(0));
        assertEquals(3, (int) profile.get(1));
        assertEquals(5, (int) profile.get(2));
        assertEquals(6, (int) profile.get(3));
    }

    // ------------------------------------------------------------------
    //  Edge case: single edge
    // ------------------------------------------------------------------

    @Test
    public void testSingleEdge() {
        addEdge("A", "B");
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        assertEquals(2, analyzer.getKHopNeighborhood("A", 1).size());
        assertEquals(1.0, analyzer.getLocalDensity("A", 1), 0.001);
        assertEquals(1.0, analyzer.getNeighborhoodOverlap("A", "B", 1), 0.001);
    }

    // ------------------------------------------------------------------
    //  Consistency checks
    // ------------------------------------------------------------------

    @Test
    public void testGrowthProfileMonotonicallyIncreasing() {
        buildCycle(8);
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        List<Integer> profile = analyzer.getGrowthProfile("0", 10);
        for (int i = 1; i < profile.size(); i++) {
            assertTrue("Growth must be monotonically increasing",
                    profile.get(i) >= profile.get(i - 1));
        }
    }

    @Test
    public void testKHopContainsSelf() {
        buildPath(5);
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        for (String v : graph.getVertices()) {
            Set<String> hood = analyzer.getKHopNeighborhood(v, 2);
            assertTrue("k-hop neighborhood must contain the source vertex",
                    hood.contains(v));
        }
    }

    @Test
    public void testOverlapSymmetric() {
        buildPath(5);
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        double ab = analyzer.getNeighborhoodOverlap("0", "4", 2);
        double ba = analyzer.getNeighborhoodOverlap("4", "0", 2);
        assertEquals("Overlap must be symmetric", ab, ba, 0.0001);
    }

    @Test
    public void testLocalDensityBounded() {
        buildPath(5);
        GraphNeighborhoodAnalyzer analyzer = new GraphNeighborhoodAnalyzer(graph);
        for (String v : graph.getVertices()) {
            double d = analyzer.getLocalDensity(v, 2);
            assertTrue("Density must be >= 0", d >= 0.0);
            assertTrue("Density must be <= 1", d <= 1.0 + 0.0001);
        }
    }
}
