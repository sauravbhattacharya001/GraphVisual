package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphAnomalyDetector}.
 */
public class GraphAnomalyDetectorTest {

    private Graph<String, Edge> graph;

    /** Creates an edge with the given type and weight. */
    private Edge makeEdge(String type, String v1, String v2, float weight) {
        Edge e = new Edge(type, v1, v2);
        e.setWeight(weight);
        return e;
    }

    /**
     * Builds a graph with a clear anomaly: one hub connected to everything,
     * plus a regular ring structure.
     *
     *   Hub (node 0) connects to nodes 1-8 with diverse edge types
     *   Nodes 1-8 form a ring (each connected to 2 neighbors)
     *   Node 9 is an isolated leaf connected only to node 1
     */
    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, Edge>();

        // Add hub
        String hub = "0";
        graph.addVertex(hub);

        // Add ring nodes
        String[] types = {"f", "c", "fs", "s", "sg", "f", "c", "fs"};
        for (int i = 1; i <= 8; i++) {
            String node = String.valueOf(i);
            graph.addVertex(node);
            // Hub connects to each ring node with varying types
            Edge e = makeEdge(types[i - 1], hub, node, 1.0f);
            graph.addEdge(e, hub, node);
        }

        // Ring edges (all friends)
        for (int i = 1; i <= 8; i++) {
            String from = String.valueOf(i);
            String to = String.valueOf(i == 8 ? 1 : i + 1);
            Edge e = makeEdge("f", from, to, 1.0f);
            graph.addEdge(e, from, to);
        }

        // Leaf node
        graph.addVertex("9");
        Edge leafEdge = makeEdge("s", "1", "9", 1.0f);
        graph.addEdge(leafEdge, "1", "9");
    }

    // ── Constructor tests ───────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new GraphAnomalyDetector(null);
    }

    @Test
    public void testConstructorDoesNotAnalyze() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph);
        try {
            det.getAllResults();
            fail("Should throw before analyze()");
        } catch (IllegalStateException expected) {
            // good
        }
    }

    // ── analyze() tests ─────────────────────────────────────────

    @Test
    public void testAnalyzeReturnsThis() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph);
        GraphAnomalyDetector returned = det.analyze();
        assertSame(det, returned);
    }

    @Test(expected = IllegalStateException.class)
    public void testAnalyzeTooFewVertices() {
        Graph<String, Edge> tiny = new UndirectedSparseGraph<String, Edge>();
        tiny.addVertex("A");
        tiny.addVertex("B");
        Edge e = makeEdge("f", "A", "B", 1.0f);
        tiny.addEdge(e, "A", "B");
        new GraphAnomalyDetector(tiny).analyze();
    }

    @Test
    public void testAnalyzeThreeVerticesMinimum() {
        Graph<String, Edge> small = new UndirectedSparseGraph<String, Edge>();
        small.addVertex("A");
        small.addVertex("B");
        small.addVertex("C");
        Edge e1 = makeEdge("f", "A", "B", 1.0f);
        Edge e2 = makeEdge("f", "B", "C", 1.0f);
        small.addEdge(e1, "A", "B");
        small.addEdge(e2, "B", "C");

        GraphAnomalyDetector det = new GraphAnomalyDetector(small).analyze();
        assertEquals(3, det.getAllResults().size());
    }

    // ── getAllResults() tests ────────────────────────────────────

    @Test
    public void testAllResultsCount() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        assertEquals(10, det.getAllResults().size());
    }

    @Test
    public void testAllResultsSortedDescending() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        List<GraphAnomalyDetector.AnomalyResult> results = det.getAllResults();
        for (int i = 1; i < results.size(); i++) {
            assertTrue("Results should be sorted descending by composite z-score",
                results.get(i - 1).getCompositeZScore()
                    >= results.get(i).getCompositeZScore());
        }
    }

    @Test
    public void testAllResultsUnmodifiable() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        try {
            det.getAllResults().clear();
            fail("Should be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // good
        }
    }

    // ── Hub detection ───────────────────────────────────────────

    @Test
    public void testHubIsTopAnomaly() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        List<GraphAnomalyDetector.AnomalyResult> top = det.getTopAnomalies(1);
        assertEquals("Hub should be the top anomaly", "0", top.get(0).getNodeId());
    }

    @Test
    public void testHubHasHighScore() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        GraphAnomalyDetector.AnomalyResult hub = det.getResult("0");
        assertNotNull(hub);
        // Hub is node with highest degree (8) vs ring nodes (2-4)
        // so it should have the max normalized score
        assertEquals(100.0, hub.getNormalizedScore(), 0.01);
    }

    @Test
    public void testHubDegreeZScorePositive() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        GraphAnomalyDetector.AnomalyResult hub = det.getResult("0");
        assertTrue("Hub degree z-score should be positive (above mean)",
            hub.getDegreeZScore() > 0);
    }

    // ── Leaf detection ──────────────────────────────────────────

    @Test
    public void testLeafIsAnomalous() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        GraphAnomalyDetector.AnomalyResult leaf = det.getResult("9");
        assertNotNull(leaf);
        // Leaf has degree 1 (below mean), should have elevated score
        assertTrue("Leaf should have anomaly score > 0",
            leaf.getCompositeZScore() > 0);
    }

    @Test
    public void testLeafDegreeIsOne() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        GraphAnomalyDetector.AnomalyResult leaf = det.getResult("9");
        assertEquals(1, leaf.getDegree());
    }

    // ── getTopAnomalies() tests ─────────────────────────────────

    @Test
    public void testTopAnomaliesCount() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        assertEquals(3, det.getTopAnomalies(3).size());
    }

    @Test
    public void testTopAnomaliesExceedingN() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        assertEquals(10, det.getTopAnomalies(100).size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTopAnomaliesInvalidK() {
        new GraphAnomalyDetector(graph).analyze().getTopAnomalies(0);
    }

    @Test
    public void testTopAnomaliesUnmodifiable() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        try {
            det.getTopAnomalies(5).clear();
            fail("Should be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // good
        }
    }

    // ── getAnomaliesAboveThreshold() tests ──────────────────────

    @Test
    public void testThresholdZeroReturnsAll() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        // Threshold 0 should return all nodes (all have z >= 0)
        List<GraphAnomalyDetector.AnomalyResult> all =
            det.getAnomaliesAboveThreshold(0.0);
        assertEquals(10, all.size());
    }

    @Test
    public void testThresholdHighReturnsFewer() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        List<GraphAnomalyDetector.AnomalyResult> high =
            det.getAnomaliesAboveThreshold(2.0);
        assertTrue("High threshold should return fewer nodes",
            high.size() < 10);
    }

    @Test
    public void testThresholdVeryHighMayReturnNone() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        List<GraphAnomalyDetector.AnomalyResult> extreme =
            det.getAnomaliesAboveThreshold(100.0);
        assertTrue(extreme.size() <= 10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeThresholdThrows() {
        new GraphAnomalyDetector(graph).analyze()
            .getAnomaliesAboveThreshold(-1.0);
    }

    // ── countAnomalies() tests ──────────────────────────────────

    @Test
    public void testCountAnomaliesMatchesListSize() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        double threshold = 1.5;
        assertEquals(
            det.getAnomaliesAboveThreshold(threshold).size(),
            det.countAnomalies(threshold));
    }

    // ── getResult() tests ───────────────────────────────────────

    @Test
    public void testGetResultExistingNode() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        assertNotNull(det.getResult("5"));
    }

    @Test
    public void testGetResultNonExistentNode() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        assertNull(det.getResult("nonexistent"));
    }

    // ── AnomalyResult properties ────────────────────────────────

    @Test
    public void testResultProperties() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        GraphAnomalyDetector.AnomalyResult r = det.getResult("0");

        assertEquals("0", r.getNodeId());
        assertEquals(8, r.getDegree());
        assertTrue(r.getCompositeZScore() >= 0);
        assertTrue(r.getNormalizedScore() >= 0 && r.getNormalizedScore() <= 100);
    }

    @Test
    public void testResultNormalizedScoreRange() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        for (GraphAnomalyDetector.AnomalyResult r : det.getAllResults()) {
            assertTrue("Score should be in [0,100]: " + r.getNormalizedScore(),
                r.getNormalizedScore() >= 0 && r.getNormalizedScore() <= 100);
        }
    }

    @Test
    public void testIsAnomalous() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        GraphAnomalyDetector.AnomalyResult hub = det.getResult("0");
        // Hub composite z-score is the highest — check it against a low threshold
        assertTrue(hub.isAnomalous(0.5));
    }

    @Test
    public void testPrimaryDimension() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        GraphAnomalyDetector.AnomalyResult hub = det.getResult("0");
        String dim = hub.getPrimaryDimension();
        // Should be one of the four dimensions
        Set<String> valid = new HashSet<String>(
            Arrays.asList("degree", "clustering", "diversity", "neighbor_deviation"));
        assertTrue("Primary dimension should be valid: " + dim, valid.contains(dim));
    }

    @Test
    public void testResultToString() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        String str = det.getResult("0").toString();
        assertTrue(str.contains("AnomalyResult"));
        assertTrue(str.contains("node=0"));
    }

    @Test
    public void testResultCompareTo() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        GraphAnomalyDetector.AnomalyResult first = det.getAllResults().get(0);
        GraphAnomalyDetector.AnomalyResult last =
            det.getAllResults().get(det.getAllResults().size() - 1);
        assertTrue("First should compare <= last (descending order)",
            first.compareTo(last) <= 0);
    }

    // ── Edge diversity metric ───────────────────────────────────

    @Test
    public void testHubHasHighDiversity() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        GraphAnomalyDetector.AnomalyResult hub = det.getResult("0");
        // Hub connects with 5 different edge types
        assertTrue("Hub should have high Edge diversity",
            hub.getEdgeDiversity() > 0.5);
    }

    @Test
    public void testLeafHasZeroDiversity() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        GraphAnomalyDetector.AnomalyResult leaf = det.getResult("9");
        // Leaf has only 1 edge type
        assertEquals("Leaf with one edge type should have 0 diversity",
            0.0, leaf.getEdgeDiversity(), 0.001);
    }

    // ── Clustering coefficient ──────────────────────────────────

    @Test
    public void testClusteringCoefficientRange() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        for (GraphAnomalyDetector.AnomalyResult r : det.getAllResults()) {
            assertTrue("Clustering coeff should be in [0,1]",
                r.getClusteringCoeff() >= 0 && r.getClusteringCoeff() <= 1.0);
        }
    }

    // ── Neighbor deviation ──────────────────────────────────────

    @Test
    public void testNeighborDeviationNonNegative() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        for (GraphAnomalyDetector.AnomalyResult r : det.getAllResults()) {
            assertTrue("Neighbor deviation should be non-negative",
                r.getNeighborDeviation() >= 0);
        }
    }

    // ── formatReport() tests ────────────────────────────────────

    @Test
    public void testFormatReportNotEmpty() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        String report = det.formatReport();
        assertFalse(report.isEmpty());
    }

    @Test
    public void testFormatReportContainsSections() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        String report = det.formatReport();
        assertTrue(report.contains("Anomaly Detection Report"));
        assertTrue(report.contains("Metric Statistics"));
        assertTrue(report.contains("Top"));
        assertTrue(report.contains("Score Distribution"));
    }

    @Test
    public void testFormatReportContainsVertexCount() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        String report = det.formatReport();
        assertTrue("Report should mention vertex count",
            report.contains("10"));
    }

    @Test
    public void testFormatReportCustomParams() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph).analyze();
        String report = det.formatReport(3, 1.0);
        assertFalse(report.isEmpty());
    }

    @Test(expected = IllegalStateException.class)
    public void testFormatReportBeforeAnalyze() {
        new GraphAnomalyDetector(graph).formatReport();
    }

    // ── Re-analyze ──────────────────────────────────────────────

    @Test
    public void testReanalyzeGivesSameResults() {
        GraphAnomalyDetector det = new GraphAnomalyDetector(graph);
        det.analyze();
        String first = det.getResult("0").toString();
        det.analyze();
        String second = det.getResult("0").toString();
        assertEquals("Re-analysis should produce same results", first, second);
    }

    // ── Uniform graph (all same degree) ─────────────────────────

    @Test
    public void testUniformGraphLowScores() {
        // Ring graph: all nodes have degree 2, same clustering, same diversity
        Graph<String, Edge> ring = new UndirectedSparseGraph<String, Edge>();
        for (int i = 0; i < 6; i++) {
            ring.addVertex(String.valueOf(i));
        }
        for (int i = 0; i < 6; i++) {
            String from = String.valueOf(i);
            String to = String.valueOf((i + 1) % 6);
            Edge e = makeEdge("f", from, to, 1.0f);
            ring.addEdge(e, from, to);
        }

        GraphAnomalyDetector det = new GraphAnomalyDetector(ring).analyze();
        // All nodes identical => all composite z-scores should be 0
        for (GraphAnomalyDetector.AnomalyResult r : det.getAllResults()) {
            assertEquals("Uniform graph nodes should have z-score 0",
                0.0, r.getCompositeZScore(), 0.001);
        }
    }

    // ── Star graph (extreme hub) ────────────────────────────────

    @Test
    public void testStarGraphHubIsAnomaly() {
        Graph<String, Edge> star = new UndirectedSparseGraph<String, Edge>();
        star.addVertex("center");
        for (int i = 0; i < 10; i++) {
            String leaf = "leaf" + i;
            star.addVertex(leaf);
            Edge e = makeEdge("f", "center", leaf, 1.0f);
            star.addEdge(e, "center", leaf);
        }

        GraphAnomalyDetector det = new GraphAnomalyDetector(star).analyze();
        GraphAnomalyDetector.AnomalyResult center = det.getResult("center");
        assertNotNull(center);
        assertEquals(100.0, center.getNormalizedScore(), 0.01);
        assertEquals(10, center.getDegree());
    }

    // ── Complete graph (all connected) ──────────────────────────

    @Test
    public void testCompleteGraphAllSimilar() {
        Graph<String, Edge> complete = new UndirectedSparseGraph<String, Edge>();
        String[] nodes = {"A", "B", "C", "D"};
        for (String n : nodes) complete.addVertex(n);

        int edgeId = 0;
        for (int i = 0; i < nodes.length; i++) {
            for (int j = i + 1; j < nodes.length; j++) {
                Edge e = makeEdge("f", nodes[i], nodes[j], 1.0f);
                complete.addEdge(e, nodes[i], nodes[j]);
            }
        }

        GraphAnomalyDetector det = new GraphAnomalyDetector(complete).analyze();
        // All nodes have identical properties in a complete graph
        for (GraphAnomalyDetector.AnomalyResult r : det.getAllResults()) {
            assertEquals("Complete graph should have 0 z-scores",
                0.0, r.getCompositeZScore(), 0.001);
        }
    }
}
