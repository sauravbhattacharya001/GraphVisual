package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/**
 * Tests for LineGraphAnalyzer — 55 tests.
 */
public class LineGraphAnalyzerTest {

    private Graph<String, edge> makeGraph(String[][] edges) {
        Graph<String, edge> g = new UndirectedSparseGraph<String, edge>();
        for (String[] e : edges) {
            g.addVertex(e[0]);
            g.addVertex(e[1]);
            edge ed = new edge("e", e[0], e[1]);
            ed.setLabel(e[0] + "-" + e[1]);
            g.addEdge(ed, e[0], e[1]);
        }
        return g;
    }

    private Graph<String, edge> emptyGraph() {
        return new UndirectedSparseGraph<String, edge>();
    }

    private Graph<String, edge> singleVertex() {
        Graph<String, edge> g = new UndirectedSparseGraph<String, edge>();
        g.addVertex("A");
        return g;
    }

    private Graph<String, edge> singleEdge() {
        return makeGraph(new String[][]{{"A", "B"}});
    }

    private Graph<String, edge> path3() {
        return makeGraph(new String[][]{{"A", "B"}, {"B", "C"}});
    }

    private Graph<String, edge> triangle() {
        return makeGraph(new String[][]{{"A", "B"}, {"B", "C"}, {"A", "C"}});
    }

    private Graph<String, edge> k4() {
        return makeGraph(new String[][]{
            {"A", "B"}, {"A", "C"}, {"A", "D"},
            {"B", "C"}, {"B", "D"}, {"C", "D"}
        });
    }

    private Graph<String, edge> star4() {
        return makeGraph(new String[][]{{"A", "B"}, {"A", "C"}, {"A", "D"}});
    }

    private Graph<String, edge> cycle4() {
        return makeGraph(new String[][]{
            {"A", "B"}, {"B", "C"}, {"C", "D"}, {"D", "A"}
        });
    }

    private Graph<String, edge> cycle5() {
        return makeGraph(new String[][]{
            {"A", "B"}, {"B", "C"}, {"C", "D"}, {"D", "E"}, {"E", "A"}
        });
    }

    // ── Null ────────────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraph() { new LineGraphAnalyzer(null); }

    // ── Empty / trivial ─────────────────────────────────────────────

    @Test
    public void testEmptyGraphOrder() {
        assertEquals(0, new LineGraphAnalyzer(emptyGraph()).lineGraphOrder());
    }

    @Test
    public void testEmptyGraphSize() {
        assertEquals(0, new LineGraphAnalyzer(emptyGraph()).lineGraphSize());
    }

    @Test
    public void testSingleVertexOrder() {
        assertEquals(0, new LineGraphAnalyzer(singleVertex()).lineGraphOrder());
    }

    @Test
    public void testSingleEdgeOrder() {
        assertEquals(1, new LineGraphAnalyzer(singleEdge()).lineGraphOrder());
    }

    @Test
    public void testSingleEdgeSize() {
        assertEquals(0, new LineGraphAnalyzer(singleEdge()).lineGraphSize());
    }

    // ── Path P3: L(P3) = K2 ────────────────────────────────────────

    @Test
    public void testPath3Order() { assertEquals(2, new LineGraphAnalyzer(path3()).lineGraphOrder()); }

    @Test
    public void testPath3Size() { assertEquals(1, new LineGraphAnalyzer(path3()).lineGraphSize()); }

    // ── Triangle K3: L(K3) = K3 ─────────────────────────────────────

    @Test
    public void testTriangleOrder() { assertEquals(3, new LineGraphAnalyzer(triangle()).lineGraphOrder()); }

    @Test
    public void testTriangleSize() { assertEquals(3, new LineGraphAnalyzer(triangle()).lineGraphSize()); }

    @Test
    public void testTriangleRegular() { assertTrue(new LineGraphAnalyzer(triangle()).isLineGraphRegular()); }

    // ── K4: L(K4) has 6 vertices, each degree 4 ─────────────────────

    @Test
    public void testK4Order() { assertEquals(6, new LineGraphAnalyzer(k4()).lineGraphOrder()); }

    @Test
    public void testK4AllDeg4() {
        List<Integer> seq = new LineGraphAnalyzer(k4()).lineGraphDegreeSequence();
        for (int d : seq) assertEquals(4, d);
    }

    @Test
    public void testK4Regular() { assertTrue(new LineGraphAnalyzer(k4()).isLineGraphRegular()); }

    // ── Star K_{1,3}: L(K_{1,3}) = K3 ───────────────────────────────

    @Test
    public void testStarOrder() { assertEquals(3, new LineGraphAnalyzer(star4()).lineGraphOrder()); }

    @Test
    public void testStarSize() { assertEquals(3, new LineGraphAnalyzer(star4()).lineGraphSize()); }

    // ── C4: L(C4) = C4 ─────────────────────────────────────────────

    @Test
    public void testCycle4Order() { assertEquals(4, new LineGraphAnalyzer(cycle4()).lineGraphOrder()); }

    @Test
    public void testCycle4Size() { assertEquals(4, new LineGraphAnalyzer(cycle4()).lineGraphSize()); }

    @Test
    public void testCycle4Regular() { assertTrue(new LineGraphAnalyzer(cycle4()).isLineGraphRegular()); }

    // ── C5: L(C5) = C5 ─────────────────────────────────────────────

    @Test
    public void testCycle5Order() { assertEquals(5, new LineGraphAnalyzer(cycle5()).lineGraphOrder()); }

    @Test
    public void testCycle5Size() { assertEquals(5, new LineGraphAnalyzer(cycle5()).lineGraphSize()); }

    // ── Degree sequence sorted ──────────────────────────────────────

    @Test
    public void testDegSeqDescending() {
        List<Integer> seq = new LineGraphAnalyzer(k4()).lineGraphDegreeSequence();
        for (int i = 1; i < seq.size(); i++) assertTrue(seq.get(i) <= seq.get(i - 1));
    }

    // ── Max degree of G ─────────────────────────────────────────────

    @Test
    public void testMaxDegK4() { assertEquals(3, new LineGraphAnalyzer(k4()).maxDegreeOfG()); }

    @Test
    public void testMaxDegStar() { assertEquals(3, new LineGraphAnalyzer(star4()).maxDegreeOfG()); }

    @Test
    public void testMaxDegCycle() { assertEquals(2, new LineGraphAnalyzer(cycle5()).maxDegreeOfG()); }

    @Test
    public void testMaxDegSingle() { assertEquals(1, new LineGraphAnalyzer(singleEdge()).maxDegreeOfG()); }

    // ── Vizing ──────────────────────────────────────────────────────

    @Test
    public void testVizingBipartite() {
        LineGraphAnalyzer.VizingResult v = new LineGraphAnalyzer(star4()).vizingAnalysis();
        assertTrue(v.getClassification().contains("1"));
    }

    @Test
    public void testVizingOddCycle() {
        assertTrue(new LineGraphAnalyzer(cycle5()).vizingAnalysis().getClassification().contains("2"));
    }

    @Test
    public void testVizingEvenComplete() {
        assertTrue(new LineGraphAnalyzer(k4()).vizingAnalysis().getClassification().contains("1"));
    }

    @Test
    public void testVizingBounds() {
        LineGraphAnalyzer.VizingResult v = new LineGraphAnalyzer(cycle4()).vizingAnalysis();
        assertEquals(v.getMaxDegree(), v.getLowerBound());
        assertEquals(v.getMaxDegree() + 1, v.getUpperBound());
    }

    @Test
    public void testVizingEmpty() {
        assertEquals(0, new LineGraphAnalyzer(emptyGraph()).vizingAnalysis().getLowerBound());
    }

    @Test
    public void testVizinToString() {
        assertNotNull(new LineGraphAnalyzer(k4()).vizingAnalysis().toString());
    }

    // ── Whitney ─────────────────────────────────────────────────────

    @Test
    public void testWhitneyK3() {
        assertTrue(new LineGraphAnalyzer(triangle()).whitneyTheoremCheck().contains("exception"));
    }

    @Test
    public void testWhitneyK13() {
        assertTrue(new LineGraphAnalyzer(star4()).whitneyTheoremCheck().contains("exception"));
    }

    @Test
    public void testWhitneyLarge() {
        assertTrue(new LineGraphAnalyzer(cycle5()).whitneyTheoremCheck().contains("uniquely"));
    }

    @Test
    public void testWhitneyEmpty() {
        assertNotNull(new LineGraphAnalyzer(emptyGraph()).whitneyTheoremCheck());
    }

    @Test
    public void testWhitneyDisconnected() {
        Graph<String, edge> g = new UndirectedSparseGraph<String, edge>();
        g.addVertex("A"); g.addVertex("B");
        assertTrue(new LineGraphAnalyzer(g).whitneyTheoremCheck().contains("disconnected"));
    }

    // ── Matching ────────────────────────────────────────────────────

    @Test
    public void testMatchSingle() { assertEquals(1, new LineGraphAnalyzer(singleEdge()).matchingSize()); }

    @Test
    public void testMatchTriangle() { assertEquals(1, new LineGraphAnalyzer(triangle()).matchingSize()); }

    @Test
    public void testMatchCycle4() { assertEquals(2, new LineGraphAnalyzer(cycle4()).matchingSize()); }

    @Test
    public void testMatchDisjoint() {
        LineGraphAnalyzer a = new LineGraphAnalyzer(k4());
        Set<String> m = a.maximalMatchingViaLineGraph();
        Graph<String, edge> lg = a.getLineGraph();
        List<String> ml = new ArrayList<String>(m);
        for (int i = 0; i < ml.size(); i++)
            for (int j = i + 1; j < ml.size(); j++)
                assertFalse(lg.isNeighbor(ml.get(i), ml.get(j)));
    }

    // ── Vertex edge-cliques ─────────────────────────────────────────

    @Test
    public void testCliquesK4Size() {
        Map<String, Set<String>> c = new LineGraphAnalyzer(k4()).vertexEdgeCliques();
        assertEquals(4, c.size());
        for (Set<String> s : c.values()) assertEquals(3, s.size());
    }

    @Test
    public void testCliquesStar() {
        Map<String, Set<String>> c = new LineGraphAnalyzer(star4()).vertexEdgeCliques();
        int big = 0, small = 0;
        for (Set<String> s : c.values()) {
            if (s.size() == 3) big++;
            if (s.size() == 1) small++;
        }
        assertEquals(1, big);
        assertEquals(3, small);
    }

    // ── Edge neighborhoods ──────────────────────────────────────────

    @Test
    public void testNbrsSingleEdge() {
        Map<String, Set<String>> n = new LineGraphAnalyzer(singleEdge()).edgeNeighborhoods();
        assertEquals(1, n.size());
        for (Set<String> s : n.values()) assertTrue(s.isEmpty());
    }

    @Test
    public void testNbrsPath3() {
        Map<String, Set<String>> n = new LineGraphAnalyzer(path3()).edgeNeighborhoods();
        for (Set<String> s : n.values()) assertEquals(1, s.size());
    }

    // ── Most central edge ───────────────────────────────────────────

    @Test
    public void testCentralEmpty() { assertNull(new LineGraphAnalyzer(emptyGraph()).mostCentralEdge()); }

    @Test
    public void testCentralExists() { assertNotNull(new LineGraphAnalyzer(k4()).mostCentralEdge()); }

    // ── Iterated ────────────────────────────────────────────────────

    @Test
    public void testIterSingleEdgeCollapse() {
        LineGraphAnalyzer.IteratedResult r = new LineGraphAnalyzer(singleEdge()).iteratedLineGraphs(3);
        assertTrue(r.getConvergence().contains("collapsed"));
    }

    @Test
    public void testIterCycle4Fixed() {
        assertTrue(new LineGraphAnalyzer(cycle4()).iteratedLineGraphs(3).getConvergence().contains("fixed point"));
    }

    @Test
    public void testIterK4Growing() {
        List<int[]> seq = new LineGraphAnalyzer(k4()).iteratedLineGraphs(2).getOrderSizeSequence();
        assertTrue(seq.get(1)[0] > seq.get(0)[0]);
    }

    @Test
    public void testIterCapped() {
        assertTrue(new LineGraphAnalyzer(k4()).iteratedLineGraphs(100).getOrderSizeSequence().size() <= 7);
    }

    // ── Stats ───────────────────────────────────────────────────────

    @Test
    public void testStatsK4() {
        LineGraphAnalyzer.LineGraphStats s = new LineGraphAnalyzer(k4()).computeStats();
        assertEquals(4, s.getOriginalOrder());
        assertEquals(6, s.getOriginalSize());
        assertEquals(6, s.getLineOrder());
        assertTrue(s.isLineRegular());
    }

    @Test
    public void testStatsEmpty() {
        LineGraphAnalyzer.LineGraphStats s = new LineGraphAnalyzer(emptyGraph()).computeStats();
        assertEquals(0, s.getLineOrder());
    }

    @Test
    public void testStatsAvgDeg() {
        assertEquals(2.0, new LineGraphAnalyzer(cycle4()).computeStats().getLineAvgDegree(), 0.01);
    }

    // ── Report ──────────────────────────────────────────────────────

    @Test
    public void testReportNotNull() { assertNotNull(new LineGraphAnalyzer(k4()).generateReport()); }

    @Test
    public void testReportContents() {
        String r = new LineGraphAnalyzer(k4()).generateReport();
        assertTrue(r.contains("LINE GRAPH"));
        assertTrue(r.contains("Vizing"));
        assertTrue(r.contains("Whitney"));
        assertTrue(r.contains("Matching"));
        assertTrue(r.contains("Iterated"));
    }

    @Test
    public void testReportEmpty() { assertNotNull(new LineGraphAnalyzer(emptyGraph()).generateReport()); }

    // ── Misc ────────────────────────────────────────────────────────

    @Test
    public void testLineGraphIsUndirected() {
        assertTrue(new LineGraphAnalyzer(k4()).getLineGraph() instanceof UndirectedSparseGraph);
    }

    @Test
    public void testMappingSize() {
        assertEquals(6, new LineGraphAnalyzer(k4()).getVertexToEdgeMapping().size());
    }

    @Test
    public void testDegreesMap() {
        Map<String, Integer> d = new LineGraphAnalyzer(path3()).lineGraphDegrees();
        assertEquals(2, d.size());
        for (int deg : d.values()) assertEquals(1, deg);
    }
}
