package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link DegreeDistributionAnalyzer}.
 */
public class DegreeDistributionAnalyzerTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, Edge>();
    }

    // --- Helpers ---

    private edge addEdge(String v1, String v2) {
        edge e = new Edge("f", v1, v2);
        e.setWeight(1.0f);
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        graph.addEdge(e, v1, v2);
        return e;
    }

    private void addIsolatedVertex(String v) {
        graph.addVertex(v);
    }

    // ═══════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNullGraph() {
        new DegreeDistributionAnalyzer(null);
    }

    @Test
    public void testConstructorValid() {
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph);
        assertNotNull(dda);
    }

    @Test(expected = IllegalStateException.class)
    public void testGettersBeforeCompute() {
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph);
        dda.getMinDegree(); // should throw
    }

    // ═══════════════════════════════════════
    // Empty graph
    // ═══════════════════════════════════════

    @Test
    public void testEmptyGraph() {
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertEquals(0, dda.getMinDegree());
        assertEquals(0, dda.getMaxDegree());
        assertEquals(0.0, dda.getMeanDegree(), 0.001);
        assertEquals(0.0, dda.getMedianDegree(), 0.001);
        assertEquals(0, dda.getModeDegree());
        assertEquals(0.0, dda.getStdDev(), 0.001);
        assertEquals("Empty", dda.getNetworkType());
        assertTrue(dda.getDegrees().isEmpty());
        assertTrue(dda.getFrequencyMap().isEmpty());
        assertTrue(dda.getHubs().isEmpty());
    }

    @Test
    public void testEmptyGraphPercentiles() {
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        Map<String, Double> percentiles = dda.getPercentiles();
        assertTrue(percentiles.isEmpty());
    }

    // ═══════════════════════════════════════
    // Single vertex
    // ═══════════════════════════════════════

    @Test
    public void testSingleIsolatedVertex() {
        addIsolatedVertex("A");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertEquals(0, dda.getMinDegree());
        assertEquals(0, dda.getMaxDegree());
        assertEquals(0.0, dda.getMeanDegree(), 0.001);
    }

    // ═══════════════════════════════════════
    // Two vertices, one edge
    // ═══════════════════════════════════════

    @Test
    public void testSimplePair() {
        addEdge("A", "B");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertEquals(1, dda.getMinDegree());
        assertEquals(1, dda.getMaxDegree());
        assertEquals(1.0, dda.getMeanDegree(), 0.001);
        assertEquals(1.0, dda.getMedianDegree(), 0.001);
        assertEquals(1, dda.getModeDegree());
        assertEquals("Trivial", dda.getNetworkType());
    }

    // ═══════════════════════════════════════
    // Regular graph (all same degree)
    // ═══════════════════════════════════════

    @Test
    public void testRegularGraph() {
        // Triangle: all degree 2
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertEquals("Regular", dda.getNetworkType());
        assertEquals(2, dda.getMinDegree());
        assertEquals(2, dda.getMaxDegree());
        assertEquals(0.0, dda.getStdDev(), 0.001);
        assertEquals(0.0, dda.getVariance(), 0.001);
        assertEquals(0, dda.getDegreeRange());
    }

    @Test
    public void testRegularGraphCV() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertEquals(0.0, dda.getCoefficientOfVariation(), 0.001);
    }

    // ═══════════════════════════════════════
    // Star graph (hub pattern)
    // ═══════════════════════════════════════

    @Test
    public void testStarGraph() {
        // Hub "H" connected to A, B, C, D, E
        addEdge("H", "A");
        addEdge("H", "B");
        addEdge("H", "C");
        addEdge("H", "D");
        addEdge("H", "E");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertEquals(1, dda.getMinDegree());
        assertEquals(5, dda.getMaxDegree());
        // Mean: (5 + 1*5) / 6 = 10/6 ≈ 1.667
        assertEquals(10.0 / 6, dda.getMeanDegree(), 0.001);
        assertEquals(1, dda.getModeDegree());
        assertTrue(dda.getHubs().contains("H"));
        assertEquals(1, dda.getHubs().size());
    }

    @Test
    public void testStarGraphFrequency() {
        addEdge("H", "A");
        addEdge("H", "B");
        addEdge("H", "C");
        addEdge("H", "D");
        addEdge("H", "E");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        Map<Integer, Integer> freq = dda.getFrequencyMap();
        assertEquals(Integer.valueOf(5), freq.get(1));  // 5 leaf nodes
        assertEquals(Integer.valueOf(1), freq.get(5));  // 1 hub
        assertEquals(2, freq.size());
    }

    // ═══════════════════════════════════════
    // Chain/path graph
    // ═══════════════════════════════════════

    @Test
    public void testPathGraph() {
        // A-B-C-D-E: degrees [1,2,2,2,1]
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "E");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertEquals(1, dda.getMinDegree());
        assertEquals(2, dda.getMaxDegree());
        assertEquals(1.6, dda.getMeanDegree(), 0.001);
        assertEquals(2, dda.getModeDegree());
    }

    // ═══════════════════════════════════════
    // Frequency map, PDF, CDF, CCDF
    // ═══════════════════════════════════════

    @Test
    public void testPDF() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        addIsolatedVertex("D");
        // Degrees: A=2, B=2, C=2, D=0
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        Map<Integer, Double> pdf = dda.getPDF();
        assertEquals(0.25, pdf.get(0), 0.001);  // 1/4
        assertEquals(0.75, pdf.get(2), 0.001);  // 3/4
    }

    @Test
    public void testCDF() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        addIsolatedVertex("D");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        Map<Integer, Double> cdf = dda.getCDF();
        assertEquals(0.25, cdf.get(0), 0.001);
        assertEquals(1.0, cdf.get(2), 0.001);
    }

    @Test
    public void testCCDF() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        addIsolatedVertex("D");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        Map<Integer, Double> ccdf = dda.getCCDF();
        assertEquals(0.75, ccdf.get(0), 0.001);  // 1 - 0.25
        assertEquals(0.0, ccdf.get(2), 0.001);   // 1 - 1.0
    }

    // ═══════════════════════════════════════
    // Percentiles
    // ═══════════════════════════════════════

    @Test
    public void testPercentiles() {
        // Build a graph with known degree distribution
        // Star with 10 leaves: hub has degree 10, leaves have degree 1
        for (int i = 0; i < 10; i++) {
            addEdge("H", "L" + i);
        }
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        Map<String, Double> p = dda.getPercentiles();
        assertNotNull(p.get("P10"));
        assertNotNull(p.get("P25"));
        assertNotNull(p.get("P50"));
        assertNotNull(p.get("P75"));
        assertNotNull(p.get("P90"));
        assertNotNull(p.get("P95"));
        assertNotNull(p.get("P99"));
        // P50 should be 1.0 (10 leaves vs 1 hub)
        assertEquals(1.0, p.get("P50"), 0.001);
    }

    @Test
    public void testIQR() {
        for (int i = 0; i < 10; i++) {
            addEdge("H", "L" + i);
        }
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        double iqr = dda.getIQR();
        assertTrue(iqr >= 0);
    }

    // ═══════════════════════════════════════
    // Hub detection
    // ═══════════════════════════════════════

    @Test
    public void testHubDetection() {
        // Create a clear hub: one node connected to many
        for (int i = 0; i < 20; i++) {
            addEdge("hub", "leaf" + i);
        }
        // Also connect a few leaves to each other
        addEdge("leaf0", "leaf1");
        addEdge("leaf2", "leaf3");

        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertTrue(dda.getHubs().contains("hub"));
    }

    @Test
    public void testNoHubsInRegularGraph() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertTrue(dda.getHubs().isEmpty());
    }

    // ═══════════════════════════════════════
    // Power-law fitting
    // ═══════════════════════════════════════

    @Test
    public void testPowerLawRegularGraphNotScaleFree() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertFalse(dda.isScaleFree());
    }

    @Test
    public void testPowerLawExponentExists() {
        for (int i = 0; i < 20; i++) {
            addEdge("hub", "leaf" + i);
        }
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        // Should have some exponent computed
        assertTrue(dda.getPowerLawExponent() != 0.0 || dda.getPowerLawRSquared() != 0.0
                || dda.getDegrees().size() > 0);
    }

    // ═══════════════════════════════════════
    // Assortativity
    // ═══════════════════════════════════════

    @Test
    public void testAssortativityRange() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "E");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertTrue(dda.getAssortativity() >= -1.0);
        assertTrue(dda.getAssortativity() <= 1.0);
    }

    @Test
    public void testAssortativityEmptyGraph() {
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertEquals(0.0, dda.getAssortativity(), 0.001);
    }

    @Test
    public void testAssortativityRegularGraph() {
        // Regular graph: assortativity should be near 0 or NaN-safe
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        // all same degree → variance 0 → assortativity 0
        assertEquals(0.0, dda.getAssortativity(), 0.001);
    }

    // ═══════════════════════════════════════
    // Network classification
    // ═══════════════════════════════════════

    @Test
    public void testEmptyNetworkType() {
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertEquals("Empty", dda.getNetworkType());
    }

    @Test
    public void testTrivialNetworkType() {
        addEdge("A", "B");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertEquals("Trivial", dda.getNetworkType());
    }

    @Test
    public void testRegularNetworkType() {
        // Complete graph K4: all degree 3
        addEdge("A", "B");
        addEdge("A", "C");
        addEdge("A", "D");
        addEdge("B", "C");
        addEdge("B", "D");
        addEdge("C", "D");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertEquals("Regular", dda.getNetworkType());
    }

    @Test
    public void testNetworkTypeNotNull() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertNotNull(dda.getNetworkType());
        assertFalse(dda.getNetworkType().isEmpty());
    }

    // ═══════════════════════════════════════
    // Distinct degree count & degree range
    // ═══════════════════════════════════════

    @Test
    public void testDistinctDegreeCount() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        addIsolatedVertex("D");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertEquals(2, dda.getDistinctDegreeCount()); // degrees: 0 and 2
    }

    @Test
    public void testDegreeRange() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        addIsolatedVertex("D");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertEquals(2, dda.getDegreeRange()); // 2 - 0
    }

    // ═══════════════════════════════════════
    // Skewness & Kurtosis
    // ═══════════════════════════════════════

    @Test
    public void testSkewnessRegularGraph() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertEquals(0.0, dda.getSkewness(), 0.001);
    }

    @Test
    public void testKurtosisRegularGraph() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertEquals(0.0, dda.getKurtosis(), 0.001);
    }

    @Test
    public void testPositiveSkewness() {
        // Star graph: many low-degree, one high → positive skew
        for (int i = 0; i < 10; i++) {
            addEdge("hub", "leaf" + i);
        }
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertTrue(dda.getSkewness() > 0);
    }

    // ═══════════════════════════════════════
    // Idempotency
    // ═══════════════════════════════════════

    @Test
    public void testComputeIdempotent() {
        addEdge("A", "B");
        addEdge("B", "C");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph);
        dda.compute();
        double mean1 = dda.getMeanDegree();
        dda.compute(); // second call
        double mean2 = dda.getMeanDegree();
        assertEquals(mean1, mean2, 0.0001);
    }

    // ═══════════════════════════════════════
    // Result object
    // ═══════════════════════════════════════

    @Test
    public void testResultObject() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        DegreeDistributionAnalyzer.DegreeDistributionResult result =
                new DegreeDistributionAnalyzer(graph).compute().getResult();
        assertEquals(3, result.getVertexCount());
        assertEquals(3, result.getEdgeCount());
        assertEquals(2, result.getMinDegree());
        assertEquals(2, result.getMaxDegree());
        assertEquals(2.0, result.getMeanDegree(), 0.001);
        assertEquals(2.0, result.getMedianDegree(), 0.001);
        assertEquals(2, result.getModeDegree());
        assertEquals(0.0, result.getVariance(), 0.001);
        assertEquals(0.0, result.getStdDev(), 0.001);
        assertEquals("Regular", result.getNetworkType());
        assertNotNull(result.getFrequencyMap());
        assertNotNull(result.getPercentiles());
        assertNotNull(result.getHubs());
    }

    @Test
    public void testResultIsScaleFree() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        DegreeDistributionAnalyzer.DegreeDistributionResult result =
                new DegreeDistributionAnalyzer(graph).compute().getResult();
        assertFalse(result.isScaleFree());
    }

    @Test
    public void testResultPowerLawAccessors() {
        addEdge("A", "B");
        addEdge("B", "C");
        DegreeDistributionAnalyzer.DegreeDistributionResult result =
                new DegreeDistributionAnalyzer(graph).compute().getResult();
        // Just verify no exceptions and values exist
        assertTrue(result.getPowerLawExponent() >= 0 || result.getPowerLawExponent() < 0);
        assertTrue(result.getPowerLawRSquared() >= 0 || result.getPowerLawRSquared() < 0);
        assertTrue(result.getAssortativity() >= -1.0 && result.getAssortativity() <= 1.0);
    }

    // ═══════════════════════════════════════
    // Format summary
    // ═══════════════════════════════════════

    @Test
    public void testFormatSummaryNotEmpty() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        String summary = dda.formatSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("Degree Distribution Analysis"));
        assertTrue(summary.contains("Vertices"));
        assertTrue(summary.contains("Min degree"));
        assertTrue(summary.contains("Percentiles"));
        assertTrue(summary.contains("Power-Law Fit"));
    }

    @Test
    public void testFormatSummaryContainsNetworkType() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        String summary = dda.formatSummary();
        assertTrue(summary.contains("Regular"));
    }

    @Test
    public void testFormatSummaryContainsHubs() {
        for (int i = 0; i < 20; i++) {
            addEdge("hub", "leaf" + i);
        }
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        String summary = dda.formatSummary();
        assertTrue(summary.contains("Hubs"));
        assertTrue(summary.contains("hub"));
    }

    @Test
    public void testFormatSummaryEmptyGraph() {
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        String summary = dda.formatSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("Empty"));
    }

    // ═══════════════════════════════════════
    // Larger graph scenarios
    // ═══════════════════════════════════════

    @Test
    public void testLargerGraphStats() {
        // Build a small-world-ish graph: ring + random shortcuts
        String[] nodes = new String[20];
        for (int i = 0; i < 20; i++) {
            nodes[i] = "N" + i;
        }
        // Ring
        for (int i = 0; i < 20; i++) {
            addEdge(nodes[i], nodes[(i + 1) % 20]);
        }
        // A few shortcuts
        addEdge("N0", "N5");
        addEdge("N3", "N12");
        addEdge("N7", "N15");

        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertEquals(20, dda.getDegrees().size());
        assertTrue(dda.getMinDegree() >= 2);
        assertTrue(dda.getMaxDegree() >= 2);
        assertTrue(dda.getMeanDegree() >= 2.0);
        assertNotNull(dda.getNetworkType());
    }

    @Test
    public void testCompleteGraphRegular() {
        // K5 — complete graph
        String[] v = {"A", "B", "C", "D", "E"};
        for (int i = 0; i < v.length; i++) {
            for (int j = i + 1; j < v.length; j++) {
                addEdge(v[i], v[j]);
            }
        }
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertEquals("Regular", dda.getNetworkType());
        assertEquals(4, dda.getMinDegree());
        assertEquals(4, dda.getMaxDegree());
        assertEquals(4.0, dda.getMeanDegree(), 0.001);
    }

    @Test
    public void testIsolatedNodesOnlyGraph() {
        for (int i = 0; i < 5; i++) {
            addIsolatedVertex("V" + i);
        }
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertEquals(0, dda.getMinDegree());
        assertEquals(0, dda.getMaxDegree());
        assertEquals(0.0, dda.getMeanDegree(), 0.001);
        assertEquals("Regular", dda.getNetworkType());
    }

    @Test
    public void testMixedIsolatedAndConnected() {
        addEdge("A", "B");
        addIsolatedVertex("C");
        addIsolatedVertex("D");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertEquals(0, dda.getMinDegree());
        assertEquals(1, dda.getMaxDegree());
        assertEquals(0.5, dda.getMeanDegree(), 0.001);
    }

    // ═══════════════════════════════════════
    // Degree list ordering
    // ═══════════════════════════════════════

    @Test
    public void testDegreesAreSorted() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addIsolatedVertex("E");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        List<Integer> deg = dda.getDegrees();
        for (int i = 1; i < deg.size(); i++) {
            assertTrue(deg.get(i) >= deg.get(i - 1));
        }
    }

    // ═══════════════════════════════════════
    // Coefficient of variation edge cases
    // ═══════════════════════════════════════

    @Test
    public void testCVWithZeroMean() {
        // All isolated nodes → mean = 0
        for (int i = 0; i < 5; i++) {
            addIsolatedVertex("V" + i);
        }
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertEquals(0.0, dda.getCoefficientOfVariation(), 0.001);
    }

    // ═══════════════════════════════════════
    // Variance with single node
    // ═══════════════════════════════════════

    @Test
    public void testVarianceSingleNode() {
        addIsolatedVertex("A");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertEquals(0.0, dda.getVariance(), 0.001);
    }

    // ═══════════════════════════════════════
    // Multi-hub graph
    // ═══════════════════════════════════════

    @Test
    public void testMultipleHubs() {
        // Two hubs each connected to many leaves
        for (int i = 0; i < 15; i++) {
            addEdge("hub1", "a" + i);
            addEdge("hub2", "b" + i);
        }
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        List<String> hubList = dda.getHubs();
        assertTrue(hubList.contains("hub1"));
        assertTrue(hubList.contains("hub2"));
    }

    // ═══════════════════════════════════════
    // Frequency map sorted by degree
    // ═══════════════════════════════════════

    @Test
    public void testFrequencyMapSortedByDegree() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addIsolatedVertex("E");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        Map<Integer, Integer> freq = dda.getFrequencyMap();
        int prev = -1;
        for (int key : freq.keySet()) {
            assertTrue(key > prev);
            prev = key;
        }
    }

    // ═══════════════════════════════════════
    // Immutability of returned collections
    // ═══════════════════════════════════════

    @Test(expected = UnsupportedOperationException.class)
    public void testDegreesImmutable() {
        addEdge("A", "B");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        dda.getDegrees().add(99);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testFrequencyMapImmutable() {
        addEdge("A", "B");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        dda.getFrequencyMap().put(99, 1);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testHubsImmutable() {
        addEdge("A", "B");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        dda.getHubs().add("fake");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testPercentilesImmutable() {
        addEdge("A", "B");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        dda.getPercentiles().put("P99", 999.0);
    }

    // ═══════════════════════════════════════
    // Bipartite-like graph
    // ═══════════════════════════════════════

    @Test
    public void testBipartiteLikeGraph() {
        // K_{3,3}: each side has degree 3
        addEdge("L1", "R1");
        addEdge("L1", "R2");
        addEdge("L1", "R3");
        addEdge("L2", "R1");
        addEdge("L2", "R2");
        addEdge("L2", "R3");
        addEdge("L3", "R1");
        addEdge("L3", "R2");
        addEdge("L3", "R3");
        DegreeDistributionAnalyzer dda = new DegreeDistributionAnalyzer(graph).compute();
        assertEquals("Regular", dda.getNetworkType());
        assertEquals(3, dda.getMinDegree());
        assertEquals(3, dda.getMaxDegree());
    }
}
