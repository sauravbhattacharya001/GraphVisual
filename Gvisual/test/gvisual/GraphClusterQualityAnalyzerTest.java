package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for GraphClusterQualityAnalyzer -- clustering quality metrics,
 * NMI, Adjusted Rand Index, and Edge cases.
 */
public class GraphClusterQualityAnalyzerTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, Edge>();
    }

    // ── Helper methods ──────────────────────────────────────────

    private void addEdge(String v1, String v2, String type) {
        graph.addVertex(v1);
        graph.addVertex(v2);
        Edge e = new Edge(type, v1, v2);
        graph.addEdge(e, v1, v2);
    }

    /**
     * Build two well-separated cliques:
     * {A,B,C} fully connected, {D,E,F} fully connected, one bridge B-D.
     */
    private void buildTwoCliques() {
        addEdge("A", "B", "f");
        addEdge("A", "C", "f");
        addEdge("B", "C", "f");
        addEdge("D", "E", "f");
        addEdge("D", "F", "f");
        addEdge("E", "F", "f");
        addEdge("B", "D", "f");
    }

    // ── Constructor ─────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new GraphClusterQualityAnalyzer(null);
    }

    // ── Evaluate: input validation ──────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullClusteringThrows() {
        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        analyzer.evaluate((Map<String, Integer>) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyClusteringThrows() {
        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        analyzer.evaluate(new HashMap<String, Integer>());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testClusteringWithMissingNodeThrows() {
        graph.addVertex("A");
        Map<String, Integer> clustering = new HashMap<String, Integer>();
        clustering.put("A", 0);
        clustering.put("Z", 0);
        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        analyzer.evaluate(clustering);
    }

    // ── Single cluster ──────────────────────────────────────────

    @Test
    public void testSingleClusterModularityZero() {
        addEdge("A", "B", "f");
        addEdge("B", "C", "f");
        Map<String, Integer> clustering = new HashMap<String, Integer>();
        clustering.put("A", 0);
        clustering.put("B", 0);
        clustering.put("C", 0);

        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        GraphClusterQualityAnalyzer.QualityReport report = analyzer.evaluate(clustering);

        assertEquals(0.0, report.getModularity(), 0.01);
        assertEquals(1.0, report.getCoverage(), 0.001);
        assertEquals(1, report.getClusterCount());
        assertEquals(2, report.getIntraEdges());
        assertEquals(0, report.getInterEdges());
    }

    // ── Two well-separated clusters ─────────────────────────────

    @Test
    public void testTwoCliquesGoodClustering() {
        buildTwoCliques();
        Map<String, Integer> clustering = new HashMap<String, Integer>();
        clustering.put("A", 0);
        clustering.put("B", 0);
        clustering.put("C", 0);
        clustering.put("D", 1);
        clustering.put("E", 1);
        clustering.put("F", 1);

        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        GraphClusterQualityAnalyzer.QualityReport report = analyzer.evaluate(clustering);

        assertTrue("Modularity should be > 0.3 for well-separated clusters",
            report.getModularity() > 0.3);
        assertEquals(6.0 / 7.0, report.getCoverage(), 0.01);
        assertEquals(6, report.getIntraEdges());
        assertEquals(1, report.getInterEdges());
        assertEquals(2, report.getClusterCount());
    }

    @Test
    public void testTwoCliquesBadClustering() {
        buildTwoCliques();
        Map<String, Integer> clustering = new HashMap<String, Integer>();
        clustering.put("A", 0);
        clustering.put("D", 0);
        clustering.put("E", 0);
        clustering.put("B", 1);
        clustering.put("C", 1);
        clustering.put("F", 1);

        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        GraphClusterQualityAnalyzer.QualityReport report = analyzer.evaluate(clustering);

        assertTrue("Bad clustering should have low modularity",
            report.getModularity() < 0.3);
    }

    // ── Coverage ────────────────────────────────────────────────

    @Test
    public void testCoverageAllIntra() {
        addEdge("A", "B", "f");
        addEdge("B", "C", "f");
        Map<String, Integer> clustering = new HashMap<String, Integer>();
        clustering.put("A", 0);
        clustering.put("B", 0);
        clustering.put("C", 0);

        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        GraphClusterQualityAnalyzer.QualityReport report = analyzer.evaluate(clustering);
        assertEquals(1.0, report.getCoverage(), 0.001);
    }

    @Test
    public void testCoverageAllInter() {
        addEdge("A", "B", "f");
        Map<String, Integer> clustering = new HashMap<String, Integer>();
        clustering.put("A", 0);
        clustering.put("B", 1);

        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        GraphClusterQualityAnalyzer.QualityReport report = analyzer.evaluate(clustering);
        assertEquals(0.0, report.getCoverage(), 0.001);
        assertEquals(0, report.getIntraEdges());
        assertEquals(1, report.getInterEdges());
    }

    // ── Conductance ─────────────────────────────────────────────

    @Test
    public void testConductanceIsolatedCluster() {
        addEdge("A", "B", "f");
        addEdge("A", "C", "f");
        addEdge("B", "C", "f");
        graph.addVertex("D");

        Map<String, Integer> clustering = new HashMap<String, Integer>();
        clustering.put("A", 0);
        clustering.put("B", 0);
        clustering.put("C", 0);
        clustering.put("D", 1);

        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        GraphClusterQualityAnalyzer.QualityReport report = analyzer.evaluate(clustering);

        assertEquals(0.0, report.getMinConductance(), 0.001);
    }

    // ── Density ─────────────────────────────────────────────────

    @Test
    public void testIntraClusterDensityFullClique() {
        addEdge("A", "B", "f");
        addEdge("A", "C", "f");
        addEdge("B", "C", "f");
        Map<String, Integer> clustering = new HashMap<String, Integer>();
        clustering.put("A", 0);
        clustering.put("B", 0);
        clustering.put("C", 0);

        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        GraphClusterQualityAnalyzer.QualityReport report = analyzer.evaluate(clustering);

        assertEquals(1.0, report.getIntraClusterDensity(), 0.001);
    }

    @Test
    public void testInterClusterDensity() {
        buildTwoCliques();
        Map<String, Integer> clustering = new HashMap<String, Integer>();
        clustering.put("A", 0);
        clustering.put("B", 0);
        clustering.put("C", 0);
        clustering.put("D", 1);
        clustering.put("E", 1);
        clustering.put("F", 1);

        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        GraphClusterQualityAnalyzer.QualityReport report = analyzer.evaluate(clustering);

        assertEquals(1.0 / 9.0, report.getInterClusterDensity(), 0.01);
        assertTrue("Density ratio should be > 1 for good clustering",
            report.getDensityRatio() > 1.0);
    }

    // ── Size balance ────────────────────────────────────────────

    @Test
    public void testSizeBalancePerfect() {
        buildTwoCliques();
        Map<String, Integer> clustering = new HashMap<String, Integer>();
        clustering.put("A", 0);
        clustering.put("B", 0);
        clustering.put("C", 0);
        clustering.put("D", 1);
        clustering.put("E", 1);
        clustering.put("F", 1);

        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        GraphClusterQualityAnalyzer.QualityReport report = analyzer.evaluate(clustering);

        assertEquals(1.0, report.getSizeBalance(), 0.001);
    }

    @Test
    public void testSizeBalanceImbalanced() {
        addEdge("A", "B", "f");
        addEdge("A", "C", "f");
        addEdge("A", "D", "f");
        addEdge("A", "E", "f");
        graph.addVertex("F");

        Map<String, Integer> clustering = new HashMap<String, Integer>();
        clustering.put("A", 0);
        clustering.put("B", 0);
        clustering.put("C", 0);
        clustering.put("D", 0);
        clustering.put("E", 0);
        clustering.put("F", 1);

        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        GraphClusterQualityAnalyzer.QualityReport report = analyzer.evaluate(clustering);

        assertTrue("Imbalanced clustering should have low balance",
            report.getSizeBalance() < 0.7);
    }

    // ── Quality verdict ─────────────────────────────────────────

    @Test
    public void testQualityVerdictStrong() {
        buildTwoCliques();
        Map<String, Integer> clustering = new HashMap<String, Integer>();
        clustering.put("A", 0);
        clustering.put("B", 0);
        clustering.put("C", 0);
        clustering.put("D", 1);
        clustering.put("E", 1);
        clustering.put("F", 1);

        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        GraphClusterQualityAnalyzer.QualityReport report = analyzer.evaluate(clustering);

        String verdict = report.getQualityVerdict();
        assertTrue("Should be 'strong' or 'moderate' for well-separated clusters",
            verdict.equals("strong") || verdict.equals("moderate"));
    }

    // ── Summary ─────────────────────────────────────────────────

    @Test
    public void testSummaryNotEmpty() {
        addEdge("A", "B", "f");
        Map<String, Integer> clustering = new HashMap<String, Integer>();
        clustering.put("A", 0);
        clustering.put("B", 0);

        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        GraphClusterQualityAnalyzer.QualityReport report = analyzer.evaluate(clustering);

        String summary = report.getSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("Modularity"));
        assertTrue(summary.contains("Coverage"));
        assertTrue(summary.contains("Cluster"));
    }

    // ── List-of-sets evaluate ───────────────────────────────────

    @Test
    public void testEvaluateWithSets() {
        buildTwoCliques();
        Set<String> c0 = new LinkedHashSet<String>(Arrays.asList("A", "B", "C"));
        Set<String> c1 = new LinkedHashSet<String>(Arrays.asList("D", "E", "F"));
        List<Set<String>> clusters = Arrays.asList(c0, c1);

        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        GraphClusterQualityAnalyzer.QualityReport report = analyzer.evaluate(clusters);

        assertTrue(report.getModularity() > 0.3);
        assertEquals(2, report.getClusterCount());
    }

    // ── NMI ─────────────────────────────────────────────────────

    @Test
    public void testNMIIdenticalClusterings() {
        addEdge("A", "B", "f");
        addEdge("C", "D", "f");
        Map<String, Integer> c = new HashMap<String, Integer>();
        c.put("A", 0); c.put("B", 0); c.put("C", 1); c.put("D", 1);

        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        assertEquals(1.0, analyzer.normalizedMutualInformation(c, c), 0.001);
    }

    @Test
    public void testNMIDifferentClusterings() {
        addEdge("A", "B", "f");
        addEdge("C", "D", "f");
        Map<String, Integer> c1 = new HashMap<String, Integer>();
        c1.put("A", 0); c1.put("B", 0); c1.put("C", 1); c1.put("D", 1);

        Map<String, Integer> c2 = new HashMap<String, Integer>();
        c2.put("A", 0); c2.put("C", 0); c2.put("B", 1); c2.put("D", 1);

        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        double nmi = analyzer.normalizedMutualInformation(c1, c2);
        assertTrue("NMI of different clusterings should be < 1", nmi < 1.0);
        assertTrue("NMI should be >= 0", nmi >= 0.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNMINullThrows() {
        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        analyzer.normalizedMutualInformation(null, new HashMap<String, Integer>());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNMIDifferentNodeSetsThrows() {
        addEdge("A", "B", "f");
        addEdge("C", "D", "f");
        Map<String, Integer> c1 = new HashMap<String, Integer>();
        c1.put("A", 0); c1.put("B", 0);

        Map<String, Integer> c2 = new HashMap<String, Integer>();
        c2.put("C", 0); c2.put("D", 0);

        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        analyzer.normalizedMutualInformation(c1, c2);
    }

    // ── ARI ─────────────────────────────────────────────────────

    @Test
    public void testARIIdenticalClusterings() {
        addEdge("A", "B", "f");
        addEdge("C", "D", "f");
        Map<String, Integer> c = new HashMap<String, Integer>();
        c.put("A", 0); c.put("B", 0); c.put("C", 1); c.put("D", 1);

        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        assertEquals(1.0, analyzer.adjustedRandIndex(c, c), 0.001);
    }

    @Test
    public void testARIDifferentClusterings() {
        addEdge("A", "B", "f");
        addEdge("C", "D", "f");
        Map<String, Integer> c1 = new HashMap<String, Integer>();
        c1.put("A", 0); c1.put("B", 0); c1.put("C", 1); c1.put("D", 1);

        Map<String, Integer> c2 = new HashMap<String, Integer>();
        c2.put("A", 0); c2.put("C", 0); c2.put("B", 1); c2.put("D", 1);

        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        double ari = analyzer.adjustedRandIndex(c1, c2);
        assertTrue("ARI of different clusterings should be < 1", ari < 1.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testARINullThrows() {
        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        analyzer.adjustedRandIndex(null, new HashMap<String, Integer>());
    }

    // ── Edge cases ──────────────────────────────────────────────

    @Test
    public void testSingleNodeSingleCluster() {
        graph.addVertex("A");
        Map<String, Integer> clustering = new HashMap<String, Integer>();
        clustering.put("A", 0);

        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        GraphClusterQualityAnalyzer.QualityReport report = analyzer.evaluate(clustering);

        assertEquals(1, report.getClusterCount());
        assertEquals(0, report.getEdgeCount());
        assertEquals(0.0, report.getModularity(), 0.001);
    }

    @Test
    public void testNoEdgesMultipleClusters() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        Map<String, Integer> clustering = new HashMap<String, Integer>();
        clustering.put("A", 0);
        clustering.put("B", 1);
        clustering.put("C", 2);

        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        GraphClusterQualityAnalyzer.QualityReport report = analyzer.evaluate(clustering);

        assertEquals(3, report.getClusterCount());
        assertEquals(0, report.getIntraEdges());
        assertEquals(0, report.getInterEdges());
    }

    @Test
    public void testPerClusterMetrics() {
        buildTwoCliques();
        Map<String, Integer> clustering = new HashMap<String, Integer>();
        clustering.put("A", 0);
        clustering.put("B", 0);
        clustering.put("C", 0);
        clustering.put("D", 1);
        clustering.put("E", 1);
        clustering.put("F", 1);

        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        GraphClusterQualityAnalyzer.QualityReport report = analyzer.evaluate(clustering);

        assertEquals(2, report.getClusterSizes().size());
        assertEquals(Integer.valueOf(3), report.getClusterSizes().get(0));
        assertEquals(Integer.valueOf(3), report.getClusterSizes().get(1));

        assertEquals(1.0, report.getClusterDensities().get(0), 0.001);
        assertEquals(1.0, report.getClusterDensities().get(1), 0.001);
    }

    @Test
    public void testNormalizedCut() {
        buildTwoCliques();
        Map<String, Integer> clustering = new HashMap<String, Integer>();
        clustering.put("A", 0);
        clustering.put("B", 0);
        clustering.put("C", 0);
        clustering.put("D", 1);
        clustering.put("E", 1);
        clustering.put("F", 1);

        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        GraphClusterQualityAnalyzer.QualityReport report = analyzer.evaluate(clustering);

        assertTrue("Normalized cut should be low for good clustering",
            report.getNormalizedCut() < 1.0);
        assertTrue("Normalized cut should be >= 0",
            report.getNormalizedCut() >= 0.0);
    }

    @Test
    public void testPartialClustering() {
        buildTwoCliques();
        Map<String, Integer> clustering = new HashMap<String, Integer>();
        clustering.put("A", 0);
        clustering.put("B", 0);
        clustering.put("C", 0);

        GraphClusterQualityAnalyzer analyzer = new GraphClusterQualityAnalyzer(graph);
        GraphClusterQualityAnalyzer.QualityReport report = analyzer.evaluate(clustering);

        assertEquals(1, report.getClusterCount());
        assertEquals(3, report.getNodeCount());
    }
}
