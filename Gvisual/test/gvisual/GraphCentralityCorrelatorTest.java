package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link GraphCentralityCorrelator}.
 */
public class GraphCentralityCorrelatorTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, Edge>();
    }

    // --- Helper methods ---

    private Edge addEdge(String type, String v1, String v2, float weight) {
        Edge e = new Edge(type, v1, v2);
        e.setWeight(weight);
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        graph.addEdge(e, v1, v2);
        return e;
    }

    private void buildStarGraph(String hub, int spokes) {
        for (int i = 1; i <= spokes; i++) {
            addEdge("f", hub, "S" + i, 5.0f);
        }
    }

    private void buildLineGraph(int length) {
        for (int i = 1; i < length; i++) {
            addEdge("f", "N" + i, "N" + (i + 1), 5.0f);
        }
    }

    private void buildCompleteGraph(int n) {
        for (int i = 1; i <= n; i++) {
            for (int j = i + 1; j <= n; j++) {
                addEdge("f", "K" + i, "K" + j, 5.0f);
            }
        }
    }

    // --- Constructor tests ---

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNullGraph() {
        new GraphCentralityCorrelator(null);
    }

    @Test
    public void testConstructorValidGraph() {
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        assertNotNull(correlator);
        assertFalse(correlator.isComputed());
    }

    // --- Empty and small graphs ---

    @Test
    public void testEmptyGraph() {
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();
        assertTrue(correlator.isComputed());
        assertEquals(0, correlator.getCorrelations().size());
    }

    @Test
    public void testSingleNodeGraph() {
        graph.addVertex("A");
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();
        assertTrue(correlator.isComputed());
        assertEquals(0, correlator.getCorrelations().size());
    }

    @Test
    public void testTwoNodeGraph() {
        addEdge("f", "A", "B", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();
        assertEquals(3, correlator.getCorrelations().size());
    }

    // --- Idempotent compute ---

    @Test
    public void testDoubleComputeIsSafe() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();
        correlator.computeAll(); // should not throw or change results
        assertEquals(3, correlator.getCorrelations().size());
    }

    // --- Auto-compute on lazy access ---

    @Test
    public void testAutoComputeOnGetCorrelations() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        assertFalse(correlator.isComputed());
        List<GraphCentralityCorrelator.CorrelationResult> results = correlator.getCorrelations();
        assertTrue(correlator.isComputed());
        assertNotNull(results);
    }

    @Test
    public void testAutoComputeOnGetMatrix() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        Map<String, Map<String, Double>> matrix = correlator.getCorrelationMatrix();
        assertTrue(correlator.isComputed());
        assertNotNull(matrix);
    }

    @Test
    public void testAutoComputeOnGetDiscordantNodes() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        List<GraphCentralityCorrelator.DiscordantNode> nodes = correlator.getDiscordantNodes(0.5);
        assertTrue(correlator.isComputed());
        assertNotNull(nodes);
    }

    @Test
    public void testAutoComputeOnGenerateReport() {
        addEdge("f", "A", "B", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        String report = correlator.generateReport();
        assertTrue(correlator.isComputed());
        assertNotNull(report);
    }

    @Test
    public void testAutoComputeOnExportCsv() {
        addEdge("f", "A", "B", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        String csv = correlator.exportCsv();
        assertTrue(correlator.isComputed());
        assertNotNull(csv);
    }

    // --- Correlation results ---

    @Test
    public void testCorrelationsCountIsThree() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        addEdge("f", "C", "D", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();
        assertEquals(3, correlator.getCorrelations().size());
    }

    @Test
    public void testCorrelationMetricPairs() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        addEdge("f", "C", "D", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        List<GraphCentralityCorrelator.CorrelationResult> corrs = correlator.getCorrelations();
        assertEquals("degree", corrs.get(0).getMetric1());
        assertEquals("betweenness", corrs.get(0).getMetric2());
        assertEquals("degree", corrs.get(1).getMetric1());
        assertEquals("closeness", corrs.get(1).getMetric2());
        assertEquals("betweenness", corrs.get(2).getMetric1());
        assertEquals("closeness", corrs.get(2).getMetric2());
    }

    @Test
    public void testCorrelationRhoRange() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        addEdge("f", "C", "D", 5.0f);
        addEdge("f", "D", "E", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        for (GraphCentralityCorrelator.CorrelationResult cr : correlator.getCorrelations()) {
            assertTrue("Rho should be >= -1", cr.getRho() >= -1.0);
            assertTrue("Rho should be <= 1", cr.getRho() <= 1.0);
        }
    }

    @Test
    public void testCorrelationPValueRange() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        addEdge("f", "C", "D", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        for (GraphCentralityCorrelator.CorrelationResult cr : correlator.getCorrelations()) {
            assertTrue("P-value should be >= 0", cr.getPValue() >= 0.0);
            assertTrue("P-value should be <= 1", cr.getPValue() <= 1.0);
        }
    }

    @Test
    public void testCompleteGraphHighCorrelation() {
        // In a complete graph, all nodes are equal → perfect correlation
        buildCompleteGraph(5);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        for (GraphCentralityCorrelator.CorrelationResult cr : correlator.getCorrelations()) {
            // All nodes tied → rho should be 1.0 (or close due to numerical issues)
            assertTrue("Complete graph should have strong correlation",
                    cr.getRho() >= 0.9 || cr.getInterpretation().equals("strong"));
        }
    }

    @Test
    public void testCorrelationInterpretationValues() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        addEdge("f", "C", "D", 5.0f);
        addEdge("f", "D", "E", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        for (GraphCentralityCorrelator.CorrelationResult cr : correlator.getCorrelations()) {
            String interp = cr.getInterpretation();
            assertTrue("Interpretation should be strong, moderate, or weak",
                    "strong".equals(interp) || "moderate".equals(interp) || "weak".equals(interp));
        }
    }

    @Test
    public void testCorrelationsListIsUnmodifiable() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        try {
            correlator.getCorrelations().add(
                    new GraphCentralityCorrelator.CorrelationResult("x", "y", 0, 1, "weak"));
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    // --- Correlation matrix ---

    @Test
    public void testCorrelationMatrixDiagonal() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        Map<String, Map<String, Double>> matrix = correlator.getCorrelationMatrix();
        assertEquals(1.0, matrix.get("degree").get("degree"), 0.001);
        assertEquals(1.0, matrix.get("betweenness").get("betweenness"), 0.001);
        assertEquals(1.0, matrix.get("closeness").get("closeness"), 0.001);
    }

    @Test
    public void testCorrelationMatrixSymmetry() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        addEdge("f", "C", "D", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        Map<String, Map<String, Double>> matrix = correlator.getCorrelationMatrix();
        assertEquals(matrix.get("degree").get("betweenness"),
                matrix.get("betweenness").get("degree"), 0.001);
        assertEquals(matrix.get("degree").get("closeness"),
                matrix.get("closeness").get("degree"), 0.001);
        assertEquals(matrix.get("betweenness").get("closeness"),
                matrix.get("closeness").get("betweenness"), 0.001);
    }

    @Test
    public void testCorrelationMatrixSize() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        Map<String, Map<String, Double>> matrix = correlator.getCorrelationMatrix();
        assertEquals(3, matrix.size());
        assertEquals(3, matrix.get("degree").size());
        assertEquals(3, matrix.get("betweenness").size());
        assertEquals(3, matrix.get("closeness").size());
    }

    @Test
    public void testCorrelationMatrixContainsAllMetrics() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        Map<String, Map<String, Double>> matrix = correlator.getCorrelationMatrix();
        assertTrue(matrix.containsKey("degree"));
        assertTrue(matrix.containsKey("betweenness"));
        assertTrue(matrix.containsKey("closeness"));
    }

    // --- Discordant nodes ---

    @Test(expected = IllegalArgumentException.class)
    public void testDiscordantNodesNegativeThreshold() {
        addEdge("f", "A", "B", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.getDiscordantNodes(-0.1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDiscordantNodesThresholdAboveOne() {
        addEdge("f", "A", "B", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.getDiscordantNodes(1.1);
    }

    @Test
    public void testDiscordantNodesZeroThreshold() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        addEdge("f", "C", "D", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        List<GraphCentralityCorrelator.DiscordantNode> nodes = correlator.getDiscordantNodes(0.0);
        assertNotNull(nodes);
        // With threshold 0, all nodes with any rank difference should appear
    }

    @Test
    public void testDiscordantNodesHighThreshold() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        // Very high threshold should return few or no discordant nodes
        List<GraphCentralityCorrelator.DiscordantNode> nodes = correlator.getDiscordantNodes(1.0);
        assertNotNull(nodes);
    }

    @Test
    public void testDiscordantNodesSortedByDifference() {
        buildLineGraph(6);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        List<GraphCentralityCorrelator.DiscordantNode> nodes = correlator.getDiscordantNodes(0.0);
        for (int i = 1; i < nodes.size(); i++) {
            assertTrue("Should be sorted by rank difference descending",
                    nodes.get(i - 1).getRankDifference() >= nodes.get(i).getRankDifference());
        }
    }

    @Test
    public void testDiscordantNodesSmallGraphEmpty() {
        graph.addVertex("A");
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        List<GraphCentralityCorrelator.DiscordantNode> nodes = correlator.getDiscordantNodes(0.5);
        assertEquals(0, nodes.size());
    }

    @Test
    public void testDiscordantNodeInStarGraph() {
        // Star graph: hub has high degree and betweenness, spokes have low
        // but closeness rankings may differ from degree rankings for spokes
        buildStarGraph("H", 6);
        // Add a tail to make discordance more pronounced
        addEdge("f", "S1", "T1", 5.0f);
        addEdge("f", "T1", "T2", 5.0f);

        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        List<GraphCentralityCorrelator.DiscordantNode> nodes = correlator.getDiscordantNodes(0.1);
        assertNotNull(nodes);
        // There should be some discordant nodes due to the tail structure
    }

    // --- Top discordant nodes ---

    @Test
    public void testTopDiscordantNodesZero() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        assertEquals(0, correlator.getTopDiscordantNodes(0).size());
    }

    @Test
    public void testTopDiscordantNodesNegative() {
        addEdge("f", "A", "B", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        assertEquals(0, correlator.getTopDiscordantNodes(-1).size());
    }

    @Test
    public void testTopDiscordantNodesLimited() {
        buildLineGraph(8);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        List<GraphCentralityCorrelator.DiscordantNode> top = correlator.getTopDiscordantNodes(3);
        assertTrue(top.size() <= 3);
    }

    @Test
    public void testTopDiscordantNodesExceedsAll() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        List<GraphCentralityCorrelator.DiscordantNode> all = correlator.getDiscordantNodes(0.0);
        List<GraphCentralityCorrelator.DiscordantNode> top = correlator.getTopDiscordantNodes(1000);
        assertEquals(all.size(), top.size());
    }

    // --- Ranks ---

    @Test
    public void testGetRanksDegree() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        Map<String, Integer> ranks = correlator.getRanks("degree");
        assertEquals(3, ranks.size());
        // B has highest degree centrality → rank 1
        assertEquals(Integer.valueOf(1), ranks.get("B"));
    }

    @Test
    public void testGetRanksBetweenness() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        Map<String, Integer> ranks = correlator.getRanks("betweenness");
        assertEquals(3, ranks.size());
        // B is the bridge → rank 1
        assertEquals(Integer.valueOf(1), ranks.get("B"));
    }

    @Test
    public void testGetRanksCloseness() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        Map<String, Integer> ranks = correlator.getRanks("closeness");
        assertEquals(3, ranks.size());
        assertEquals(Integer.valueOf(1), ranks.get("B"));
    }

    @Test
    public void testRanksAreUnmodifiable() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        try {
            correlator.getRanks("degree").put("X", 99);
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testRanksDefaultToDegree() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        Map<String, Integer> ranks = correlator.getRanks("unknown");
        // Should default to degree ranks
        assertEquals(3, ranks.size());
    }

    // --- Report generation ---

    @Test
    public void testGenerateReportNotNull() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        addEdge("f", "C", "D", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        String report = correlator.generateReport();
        assertNotNull(report);
        assertFalse(report.isEmpty());
    }

    @Test
    public void testGenerateReportContainsHeader() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        String report = correlator.generateReport();
        assertTrue(report.contains("Centrality Correlation Report"));
    }

    @Test
    public void testGenerateReportContainsNodeCount() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        String report = correlator.generateReport();
        assertTrue(report.contains("Nodes: 3"));
    }

    @Test
    public void testGenerateReportContainsCorrelationSection() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        String report = correlator.generateReport();
        assertTrue(report.contains("Pairwise Spearman Correlations"));
        assertTrue(report.contains("rho="));
    }

    @Test
    public void testGenerateReportContainsMatrixSection() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        String report = correlator.generateReport();
        assertTrue(report.contains("Correlation Matrix"));
    }

    @Test
    public void testGenerateReportSmallGraph() {
        graph.addVertex("A");
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        String report = correlator.generateReport();
        assertTrue(report.contains("Not enough nodes"));
    }

    // --- CSV export ---

    @Test
    public void testExportCsvNotNull() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        String csv = correlator.exportCsv();
        assertNotNull(csv);
        assertFalse(csv.isEmpty());
    }

    @Test
    public void testExportCsvContainsHeaders() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        String csv = correlator.exportCsv();
        assertTrue(csv.contains("section,metric1,metric2,rho,p_value,interpretation"));
        assertTrue(csv.contains("section,node,metric1,rank1,metric2,rank2,rank_difference"));
    }

    @Test
    public void testExportCsvContainsCorrelationRows() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        addEdge("f", "C", "D", 5.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        String csv = correlator.exportCsv();
        // Should have 3 correlation rows
        int count = 0;
        for (String line : csv.split("\n")) {
            if (line.startsWith("correlation,")) count++;
        }
        assertEquals(3, count);
    }

    @Test
    public void testExportCsvContainsDiscordantRows() {
        buildLineGraph(6);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        String csv = correlator.exportCsv();
        assertTrue(csv.contains("discordant,"));
    }

    // --- CorrelationResult inner class tests ---

    @Test
    public void testCorrelationResultToString() {
        GraphCentralityCorrelator.CorrelationResult cr =
                new GraphCentralityCorrelator.CorrelationResult("degree", "betweenness", 0.85, 0.01, "strong");
        String s = cr.toString();
        assertTrue(s.contains("degree"));
        assertTrue(s.contains("betweenness"));
        assertTrue(s.contains("0.85"));
        assertTrue(s.contains("strong"));
    }

    @Test
    public void testCorrelationResultEquals() {
        GraphCentralityCorrelator.CorrelationResult cr1 =
                new GraphCentralityCorrelator.CorrelationResult("degree", "betweenness", 0.85, 0.01, "strong");
        GraphCentralityCorrelator.CorrelationResult cr2 =
                new GraphCentralityCorrelator.CorrelationResult("degree", "betweenness", 0.50, 0.10, "moderate");
        assertEquals(cr1, cr2); // Same metric pair
        assertEquals(cr1.hashCode(), cr2.hashCode());
    }

    @Test
    public void testCorrelationResultNotEquals() {
        GraphCentralityCorrelator.CorrelationResult cr1 =
                new GraphCentralityCorrelator.CorrelationResult("degree", "betweenness", 0.85, 0.01, "strong");
        GraphCentralityCorrelator.CorrelationResult cr2 =
                new GraphCentralityCorrelator.CorrelationResult("degree", "closeness", 0.85, 0.01, "strong");
        assertNotEquals(cr1, cr2);
    }

    @Test
    public void testCorrelationResultGetters() {
        GraphCentralityCorrelator.CorrelationResult cr =
                new GraphCentralityCorrelator.CorrelationResult("degree", "closeness", 0.75, 0.02, "strong");
        assertEquals("degree", cr.getMetric1());
        assertEquals("closeness", cr.getMetric2());
        assertEquals(0.75, cr.getRho(), 0.001);
        assertEquals(0.02, cr.getPValue(), 0.001);
        assertEquals("strong", cr.getInterpretation());
    }

    // --- DiscordantNode inner class tests ---

    @Test
    public void testDiscordantNodeGetters() {
        GraphCentralityCorrelator.DiscordantNode dn =
                new GraphCentralityCorrelator.DiscordantNode("X", "degree", 1, "betweenness", 5);
        assertEquals("X", dn.getNodeId());
        assertEquals("degree", dn.getMetric1Name());
        assertEquals(1, dn.getRank1());
        assertEquals("betweenness", dn.getMetric2Name());
        assertEquals(5, dn.getRank2());
        assertEquals(4, dn.getRankDifference());
    }

    @Test
    public void testDiscordantNodeCompareTo() {
        GraphCentralityCorrelator.DiscordantNode dn1 =
                new GraphCentralityCorrelator.DiscordantNode("A", "degree", 1, "betweenness", 10);
        GraphCentralityCorrelator.DiscordantNode dn2 =
                new GraphCentralityCorrelator.DiscordantNode("B", "degree", 1, "betweenness", 3);
        assertTrue(dn1.compareTo(dn2) < 0); // dn1 has higher diff (9 vs 2)
        assertTrue(dn2.compareTo(dn1) > 0);
    }

    @Test
    public void testDiscordantNodeToString() {
        GraphCentralityCorrelator.DiscordantNode dn =
                new GraphCentralityCorrelator.DiscordantNode("X", "degree", 1, "betweenness", 5);
        String s = dn.toString();
        assertTrue(s.contains("Node X"));
        assertTrue(s.contains("degree"));
        assertTrue(s.contains("betweenness"));
        assertTrue(s.contains("diff=4"));
    }

    @Test
    public void testDiscordantNodeEquals() {
        GraphCentralityCorrelator.DiscordantNode dn1 =
                new GraphCentralityCorrelator.DiscordantNode("X", "degree", 1, "betweenness", 5);
        GraphCentralityCorrelator.DiscordantNode dn2 =
                new GraphCentralityCorrelator.DiscordantNode("X", "degree", 2, "betweenness", 3);
        assertEquals(dn1, dn2); // Same node + same metric pair
        assertEquals(dn1.hashCode(), dn2.hashCode());
    }

    @Test
    public void testDiscordantNodeNotEquals() {
        GraphCentralityCorrelator.DiscordantNode dn1 =
                new GraphCentralityCorrelator.DiscordantNode("X", "degree", 1, "betweenness", 5);
        GraphCentralityCorrelator.DiscordantNode dn2 =
                new GraphCentralityCorrelator.DiscordantNode("Y", "degree", 1, "betweenness", 5);
        assertNotEquals(dn1, dn2);
    }

    // --- Graph topology scenarios ---

    @Test
    public void testLineGraphCorrelation() {
        // Line graph: degree and betweenness should correlate positively
        buildLineGraph(7);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        // Degree-betweenness correlation should be positive for a line
        GraphCentralityCorrelator.CorrelationResult dbCorr = correlator.getCorrelations().get(0);
        assertTrue("Line graph degree-betweenness should correlate positively",
                dbCorr.getRho() > 0);
    }

    @Test
    public void testMixedEdgeTypes() {
        addEdge("f", "A", "B", 10.0f);
        addEdge("c", "B", "C", 5.0f);
        addEdge("s", "C", "D", 3.0f);
        addEdge("fs", "D", "E", 7.0f);
        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        assertEquals(3, correlator.getCorrelations().size());
        assertNotNull(correlator.generateReport());
    }

    @Test
    public void testDisconnectedGraphCorrelations() {
        addEdge("f", "A", "B", 5.0f);
        addEdge("f", "B", "C", 5.0f);
        graph.addVertex("D"); // isolated

        GraphCentralityCorrelator correlator = new GraphCentralityCorrelator(graph);
        correlator.computeAll();

        assertEquals(3, correlator.getCorrelations().size());
    }
}
