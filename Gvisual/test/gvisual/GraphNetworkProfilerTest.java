package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphNetworkProfiler}.
 *
 * @author zalenix
 */
public class GraphNetworkProfilerTest {

    // ── Helpers ─────────────────────────────────────────────────

    private static Graph<String, Edge> emptyGraph() {
        return new UndirectedSparseGraph<>();
    }

    private static int edgeCounter = 0;

    private static void addEdge(Graph<String, Edge> g, String v1, String v2) {
        Edge e  new Edge("f", v1, v2);
        e.setLabel("e" + (edgeCounter++));
        g.addEdge(e, v1, v2);
    }

    private static Graph<String, Edge> completeGraph(int n) {
        Graph<String, Edge> g = emptyGraph();
        for (int i = 0; i < n; i++) g.addVertex("v" + i);
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                addEdge(g, "v" + i, "v" + j);
        return g;
    }

    private static Graph<String, Edge> starGraph(int spokes) {
        Graph<String, Edge> g = emptyGraph();
        g.addVertex("hub");
        for (int i = 0; i < spokes; i++) {
            String s = "s" + i;
            addEdge(g, "hub", s);
        }
        return g;
    }

    private static Graph<String, Edge> pathGraph(int n) {
        Graph<String, Edge> g = emptyGraph();
        for (int i = 0; i < n; i++) g.addVertex("v" + i);
        for (int i = 0; i < n - 1; i++) addEdge(g, "v" + i, "v" + (i + 1));
        return g;
    }

    private static Graph<String, Edge> ringGraph(int n) {
        Graph<String, Edge> g = pathGraph(n);
        addEdge(g, "v0", "v" + (n - 1));
        return g;
    }

    private static Graph<String, Edge> gridGraph(int rows, int cols) {
        Graph<String, Edge> g = emptyGraph();
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                g.addVertex(r + "," + c);
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                if (c + 1 < cols) addEdge(g, r + "," + c, r + "," + (c + 1));
                if (r + 1 < rows) addEdge(g, r + "," + c, (r + 1) + "," + c);
            }
        return g;
    }

    // ── Basic tests ─────────────────────────────────────────────

    @Test
    public void testEmptyGraph() {
        GraphNetworkProfiler p = new GraphNetworkProfiler(emptyGraph());
        p.analyze();
        assertTrue(p.isAnalyzed());
        assertEquals(GraphNetworkProfiler.NetworkType.UNKNOWN, p.getClassification());
        assertEquals(0, p.getOverallScore(), 0.01);
    }

    @Test
    public void testSingleVertex() {
        Graph<String, Edge> g = emptyGraph();
        g.addVertex("a");
        GraphNetworkProfiler p = new GraphNetworkProfiler(g);
        p.analyze();
        assertEquals(0.0, p.getDensity(), 0.01);
        assertEquals(0.0, p.getAvgDegree(), 0.01);
        assertEquals(1, p.getComponentCount());
    }

    @Test
    public void testTwoConnectedVertices() {
        Graph<String, Edge> g = emptyGraph();
        addEdge(g, "a", "b");
        GraphNetworkProfiler p = new GraphNetworkProfiler(g);
        p.analyze();
        assertEquals(1.0, p.getDensity(), 0.01);
        assertEquals(1.0, p.getAvgDegree(), 0.01);
        assertEquals(1, p.getComponentCount());
    }

    @Test(expected = IllegalStateException.class)
    public void testQueryBeforeAnalyzeThrows() {
        GraphNetworkProfiler p = new GraphNetworkProfiler(emptyGraph());
        p.getDensity();
    }

    @Test(expected = NullPointerException.class)
    public void testNullGraphThrows() {
        new GraphNetworkProfiler(null);
    }

    // ── Complete graph ──────────────────────────────────────────

    @Test
    public void testCompleteGraph() {
        GraphNetworkProfiler p = new GraphNetworkProfiler(completeGraph(10));
        p.analyze();
        assertEquals(1.0, p.getDensity(), 0.01);
        assertEquals(9.0, p.getAvgDegree(), 0.01);
        assertEquals(0.0, p.getDegreeVariance(), 0.01);
        assertEquals(1.0, p.getGlobalClustering(), 0.01);
        assertEquals(1, p.getApproxDiameter());
        assertEquals(1, p.getComponentCount());
    }

    // ── Star graph ──────────────────────────────────────────────

    @Test
    public void testStarGraph() {
        GraphNetworkProfiler p = new GraphNetworkProfiler(starGraph(20));
        p.analyze();
        assertTrue(p.getHubDominance() > 5);
        assertEquals(0.0, p.getGlobalClustering(), 0.01);
        assertEquals(2, p.getApproxDiameter());
        assertEquals(1, p.getComponentCount());
    }

    // ── Path graph (tree-like) ──────────────────────────────────

    @Test
    public void testPathGraph() {
        GraphNetworkProfiler p = new GraphNetworkProfiler(pathGraph(20));
        p.analyze();
        assertEquals(0.0, p.getGlobalClustering(), 0.01);
        assertTrue(p.getDensity() < 0.1);
        // Path is tree-like: edges = n-1
        assertEquals(1.0, p.getLargestComponentFraction(), 0.01);
    }

    // ── Ring graph ──────────────────────────────────────────────

    @Test
    public void testRingGraph() {
        GraphNetworkProfiler p = new GraphNetworkProfiler(ringGraph(20));
        p.analyze();
        assertEquals(2.0, p.getAvgDegree(), 0.01);
        assertEquals(0.0, p.getDegreeVariance(), 0.01);
        assertEquals(1, p.getComponentCount());
    }

    // ── Grid graph (lattice) ────────────────────────────────────

    @Test
    public void testGridGraph() {
        GraphNetworkProfiler p = new GraphNetworkProfiler(gridGraph(5, 5));
        p.analyze();
        assertTrue(p.getDensity() < 0.5);
        assertEquals(1, p.getComponentCount());
        assertTrue(p.getAvgDegree() > 2);
    }

    // ── Disconnected graph ──────────────────────────────────────

    @Test
    public void testDisconnectedGraph() {
        Graph<String, Edge> g = emptyGraph();
        addEdge(g, "a", "b");
        addEdge(g, "c", "d");
        g.addVertex("e"); // isolate
        GraphNetworkProfiler p = new GraphNetworkProfiler(g);
        p.analyze();
        assertEquals(3, p.getComponentCount());
        assertTrue(p.getLargestComponentFraction() < 0.5);
    }

    // ── Metric results ──────────────────────────────────────────

    @Test
    public void testMetricResultsCount() {
        GraphNetworkProfiler p = new GraphNetworkProfiler(completeGraph(5));
        p.analyze();
        assertEquals(12, p.getMetricResults().size());
    }

    @Test
    public void testMetricResultsImmutable() {
        GraphNetworkProfiler p = new GraphNetworkProfiler(completeGraph(5));
        p.analyze();
        try {
            p.getMetricResults().add(null);
            fail("Should be unmodifiable");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testMetricScoresInRange() {
        GraphNetworkProfiler p = new GraphNetworkProfiler(starGraph(15));
        p.analyze();
        for (GraphNetworkProfiler.MetricResult mr : p.getMetricResults()) {
            assertTrue(mr.getName() + " score out of range",
                    mr.getScore() >= 0 && mr.getScore() <= 100);
            assertNotNull(mr.getGrade());
            assertNotNull(mr.getInterpretation());
            assertNotNull(mr.getCategory());
        }
    }

    // ── Grades ──────────────────────────────────────────────────

    @Test
    public void testGradeFromScore() {
        assertEquals(GraphNetworkProfiler.Grade.A_PLUS,
                GraphNetworkProfiler.Grade.fromScore(100));
        assertEquals(GraphNetworkProfiler.Grade.A,
                GraphNetworkProfiler.Grade.fromScore(95));
        assertEquals(GraphNetworkProfiler.Grade.F,
                GraphNetworkProfiler.Grade.fromScore(10));
    }

    @Test
    public void testOverallGradeNotNull() {
        GraphNetworkProfiler p = new GraphNetworkProfiler(completeGraph(8));
        p.analyze();
        assertNotNull(p.getOverallGrade());
        assertTrue(p.getOverallScore() >= 0 && p.getOverallScore() <= 100);
    }

    // ── Fingerprint ─────────────────────────────────────────────

    @Test
    public void testFingerprint() {
        GraphNetworkProfiler p = new GraphNetworkProfiler(completeGraph(6));
        p.analyze();
        Map<String, Double> fp = p.getFingerprint();
        assertEquals(12, fp.size());
        assertTrue(fp.containsKey("Density"));
        assertTrue(fp.containsKey("Hub Dominance"));
    }

    // ── Compare ─────────────────────────────────────────────────

    @Test
    public void testCompare() {
        GraphNetworkProfiler a = new GraphNetworkProfiler(completeGraph(5));
        GraphNetworkProfiler b = new GraphNetworkProfiler(starGraph(10));
        a.analyze();
        b.analyze();
        Map<String, double[]> diff = GraphNetworkProfiler.compare(a, b);
        assertEquals(12, diff.size());
        for (double[] vals : diff.values()) {
            assertEquals(3, vals.length);
            assertEquals(vals[2], vals[1] - vals[0], 1e-10);
        }
    }

    // ── Classification ──────────────────────────────────────────

    @Test
    public void testClassificationNotNull() {
        GraphNetworkProfiler p = new GraphNetworkProfiler(completeGraph(10));
        p.analyze();
        assertNotNull(p.getClassification());
        assertTrue(p.getClassificationConfidence() >= 0);
    }

    @Test
    public void testNetworkTypeEnumValues() {
        for (GraphNetworkProfiler.NetworkType nt : GraphNetworkProfiler.NetworkType.values()) {
            assertNotNull(nt.getDisplayName());
            assertNotNull(nt.getDescription());
            assertFalse(nt.getDisplayName().isEmpty());
        }
    }

    // ── Text report ─────────────────────────────────────────────

    @Test
    public void testTextReportContainsKey() {
        GraphNetworkProfiler p = new GraphNetworkProfiler(starGraph(10));
        p.analyze();
        String report = p.getTextReport();
        assertTrue(report.contains("GRAPH NETWORK PROFILE REPORT"));
        assertTrue(report.contains("Classification"));
        assertTrue(report.contains("Density"));
        assertTrue(report.contains("Overall Grade"));
    }

    @Test
    public void testTextReportEmptyGraph() {
        GraphNetworkProfiler p = new GraphNetworkProfiler(emptyGraph());
        p.analyze();
        String report = p.getTextReport();
        assertTrue(report.contains("0 vertices"));
    }

    // ── HTML report ─────────────────────────────────────────────

    @Test
    public void testHtmlReportStructure() {
        GraphNetworkProfiler p = new GraphNetworkProfiler(completeGraph(8));
        p.analyze();
        String html = p.getHtmlReport();
        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertTrue(html.contains("Network Profile Report"));
        assertTrue(html.contains("Network Fingerprint"));
        assertTrue(html.contains("</html>"));
    }

    @Test
    public void testHtmlReportContainsMetrics() {
        GraphNetworkProfiler p = new GraphNetworkProfiler(gridGraph(4, 4));
        p.analyze();
        String html = p.getHtmlReport();
        assertTrue(html.contains("Density"));
        assertTrue(html.contains("Clustering Coefficient"));
        assertTrue(html.contains("Hub Dominance"));
    }

    // ── Assortativity ───────────────────────────────────────────

    @Test
    public void testAssortativityRange() {
        GraphNetworkProfiler p = new GraphNetworkProfiler(completeGraph(6));
        p.analyze();
        assertTrue(p.getAssortativity() >= -1.0 && p.getAssortativity() <= 1.0);
    }

    // ── Power-law exponent ──────────────────────────────────────

    @Test
    public void testPowerLawExponentPositive() {
        GraphNetworkProfiler p = new GraphNetworkProfiler(starGraph(30));
        p.analyze();
        assertTrue(p.getPowerLawExponent() > 0);
    }

    // ── Small-world quotient ────────────────────────────────────

    @Test
    public void testSmallWorldQuotientNonNegative() {
        GraphNetworkProfiler p = new GraphNetworkProfiler(ringGraph(20));
        p.analyze();
        assertTrue(p.getSmallWorldQuotient() >= 0);
    }

    // ── Large graph stress test ─────────────────────────────────

    @Test
    public void testLargeGraph() {
        // BA-style: start with K3, attach new nodes
        Graph<String, Edge> g = emptyGraph();
        addEdge(g, "v0", "v1");
        addEdge(g, "v1", "v2");
        addEdge(g, "v0", "v2");
        Random rng = new Random(99);
        for (int i = 3; i < 200; i++) {
            String newV = "v" + i;
            // connect to 2 random existing vertices
            List<String> existing = new ArrayList<>(g.getVertices());
            Set<String> targets = new HashSet<>();
            while (targets.size() < 2) {
                targets.add(existing.get(rng.nextInt(existing.size())));
            }
            for (String t : targets) addEdge(g, newV, t);
        }
        GraphNetworkProfiler p = new GraphNetworkProfiler(g);
        p.analyze();
        assertTrue(p.isAnalyzed());
        assertTrue(p.getAvgDegree() > 1);
        assertNotNull(p.getClassification());
        assertEquals(12, p.getMetricResults().size());
    }

    // ── Determinism with seed ───────────────────────────────────

    @Test
    public void testDeterministicWithSeed() {
        Graph<String, Edge> g = gridGraph(5, 5);
        GraphNetworkProfiler p1 = new GraphNetworkProfiler(g, new Random(42));
        GraphNetworkProfiler p2 = new GraphNetworkProfiler(g, new Random(42));
        p1.analyze();
        p2.analyze();
        assertEquals(p1.getAvgPathLength(), p2.getAvgPathLength(), 1e-10);
        assertEquals(p1.getApproxDiameter(), p2.getApproxDiameter());
        assertEquals(p1.getClassification(), p2.getClassification());
    }

    // ── Average path length ─────────────────────────────────────

    @Test
    public void testAvgPathLengthComplete() {
        GraphNetworkProfiler p = new GraphNetworkProfiler(completeGraph(10));
        p.analyze();
        assertEquals(1.0, p.getAvgPathLength(), 0.01);
    }

    // ── Component fraction ──────────────────────────────────────

    @Test
    public void testLargestComponentFractionSingle() {
        GraphNetworkProfiler p = new GraphNetworkProfiler(pathGraph(10));
        p.analyze();
        assertEquals(1.0, p.getLargestComponentFraction(), 0.01);
    }

    // ── Clique + periphery (core-periphery test) ────────────────

    @Test
    public void testCorePeripheryShape() {
        Graph<String, Edge> g = emptyGraph();
        // Dense core of 5 nodes
        for (int i = 0; i < 5; i++)
            for (int j = i + 1; j < 5; j++)
                addEdge(g, "c" + i, "c" + j);
        // 20 peripheral nodes, each connected to 1 core node
        for (int i = 0; i < 20; i++) {
            addEdge(g, "p" + i, "c" + (i % 5));
        }
        GraphNetworkProfiler p = new GraphNetworkProfiler(g);
        p.analyze();
        assertTrue(p.getHubDominance() > 2);
        assertEquals(1, p.getComponentCount());
    }

    // ── Repeated analyze is safe ────────────────────────────────

    @Test
    public void testDoubleAnalyze() {
        GraphNetworkProfiler p = new GraphNetworkProfiler(completeGraph(5));
        p.analyze();
        double score1 = p.getOverallScore();
        p.analyze();
        assertEquals(score1, p.getOverallScore(), 0.01);
    }

    // ── Grade label coverage ────────────────────────────────────

    @Test
    public void testAllGradesHaveLabels() {
        for (GraphNetworkProfiler.Grade g : GraphNetworkProfiler.Grade.values()) {
            assertNotNull(g.getLabel());
            assertFalse(g.getLabel().isEmpty());
        }
    }
}
