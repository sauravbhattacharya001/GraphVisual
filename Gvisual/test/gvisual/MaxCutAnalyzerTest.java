package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for MaxCutAnalyzer -- greedy, local search, random local search,
 * exact brute-force, balanced cuts, bounds, vertex contributions, and reports.
 */
public class MaxCutAnalyzerTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, Edge>();
    }

    private void addEdge(String v1, String v2) {
        Edge e = new Edge("f", v1, v2);
        graph.addEdge(e, v1, v2);
    }

    private void addWeightedEdge(String v1, String v2, float weight) {
        Edge e = new Edge("f", v1, v2);
        e.setWeight(weight);
        graph.addEdge(e, v1, v2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() { new MaxCutAnalyzer(null); }

    @Test
    public void testConstructorAcceptsValidGraph() { assertNotNull(new MaxCutAnalyzer(graph)); }

    @Test
    public void testEmptyGraphGreedy() {
        MaxCutAnalyzer.CutResult r = new MaxCutAnalyzer(graph).computeGreedy();
        assertEquals(0, r.getCutEdgeCount());
        assertEquals(0.0, r.getCutValue(), 0.001);
        assertTrue(r.getSetS().isEmpty());
        assertTrue(r.getSetT().isEmpty());
    }

    @Test
    public void testEmptyGraphLocalSearch() {
        assertEquals(0, new MaxCutAnalyzer(graph).computeLocalSearch().getCutEdgeCount());
    }

    @Test
    public void testEmptyGraphExact() {
        assertEquals(0, new MaxCutAnalyzer(graph).computeExact().getCutEdgeCount());
    }

    @Test
    public void testSingleVertex() {
        graph.addVertex("A");
        MaxCutAnalyzer.CutResult r = new MaxCutAnalyzer(graph).computeGreedy();
        assertEquals(0, r.getCutEdgeCount());
        assertEquals(1, r.getSetS().size() + r.getSetT().size());
    }

    @Test
    public void testSingleEdgeGreedy() {
        addEdge("A", "B");
        MaxCutAnalyzer.CutResult r = new MaxCutAnalyzer(graph).computeGreedy();
        assertEquals(1, r.getCutEdgeCount());
        assertEquals(1.0, r.getCutValue(), 0.001);
    }

    @Test
    public void testSingleEdgeExact() {
        addEdge("A", "B");
        assertEquals(1, new MaxCutAnalyzer(graph).computeExact().getCutEdgeCount());
    }

    @Test
    public void testTriangleMaxCutIs2() {
        addEdge("A", "B"); addEdge("B", "C"); addEdge("A", "C");
        assertEquals(2, new MaxCutAnalyzer(graph).computeExact().getCutEdgeCount());
    }

    @Test
    public void testTriangleGreedyAtLeast2() {
        addEdge("A", "B"); addEdge("B", "C"); addEdge("A", "C");
        assertTrue(new MaxCutAnalyzer(graph).computeGreedy().getCutEdgeCount() >= 2);
    }

    @Test
    public void testBipartiteK33PerfectCut() {
        for (int i = 1; i <= 3; i++) for (int j = 4; j <= 6; j++) addEdge("" + i, "" + j);
        assertEquals(9, new MaxCutAnalyzer(graph).computeExact().getCutEdgeCount());
    }

    @Test
    public void testBipartiteLocalSearchFindsOptimal() {
        for (int i = 1; i <= 3; i++) for (int j = 4; j <= 6; j++) addEdge("" + i, "" + j);
        assertEquals(9, new MaxCutAnalyzer(graph).computeLocalSearch().getCutEdgeCount());
    }

    @Test
    public void testK4MaxCutIs4() {
        addEdge("A","B"); addEdge("A","C"); addEdge("A","D");
        addEdge("B","C"); addEdge("B","D"); addEdge("C","D");
        assertEquals(4, new MaxCutAnalyzer(graph).computeExact().getCutEdgeCount());
    }

    @Test
    public void testPathGraphMaxCut() {
        addEdge("A","B"); addEdge("B","C"); addEdge("C","D"); addEdge("D","E");
        assertEquals(4, new MaxCutAnalyzer(graph).computeExact().getCutEdgeCount());
    }

    @Test
    public void testEvenCyclePerfectCut() {
        addEdge("A","B"); addEdge("B","C"); addEdge("C","D"); addEdge("D","A");
        assertEquals(4, new MaxCutAnalyzer(graph).computeExact().getCutEdgeCount());
    }

    @Test
    public void testOddCycleMaxCut() {
        addEdge("A","B"); addEdge("B","C"); addEdge("C","D"); addEdge("D","E"); addEdge("E","A");
        assertEquals(4, new MaxCutAnalyzer(graph).computeExact().getCutEdgeCount());
    }

    @Test
    public void testWeightedEdges() {
        addWeightedEdge("A","B",10f); addWeightedEdge("B","C",1f); addWeightedEdge("A","C",1f);
        assertTrue(new MaxCutAnalyzer(graph).computeExact().getCutValue() >= 11.0);
    }

    @Test
    public void testWeightedEdgesGreedy() {
        addWeightedEdge("A","B",5f); addWeightedEdge("A","C",3f);
        assertTrue(new MaxCutAnalyzer(graph).computeGreedy().getCutValue() >= 5.0);
    }

    @Test
    public void testCompareAlgorithms() {
        addEdge("A","B"); addEdge("B","C"); addEdge("C","A");
        List<MaxCutAnalyzer.CutResult> results = new MaxCutAnalyzer(graph).compareAlgorithms();
        assertTrue(results.size() >= 3);
        for (int i = 1; i < results.size(); i++)
            assertTrue(results.get(i-1).getCutValue() >= results.get(i).getCutValue());
    }

    @Test
    public void testCompareIncludesExactForSmall() {
        addEdge("A","B");
        boolean hasExact = false;
        for (MaxCutAnalyzer.CutResult r : new MaxCutAnalyzer(graph).compareAlgorithms())
            if ("Exact".equals(r.getAlgorithm())) hasExact = true;
        assertTrue(hasExact);
    }

    @Test
    public void testUpperBoundEmpty() { assertEquals(0.0, new MaxCutAnalyzer(graph).computeUpperBound(), 0.001); }

    @Test
    public void testLowerBoundEmpty() { assertEquals(0.0, new MaxCutAnalyzer(graph).computeLowerBound(), 0.001); }

    @Test
    public void testBoundsTriangle() {
        addEdge("A","B"); addEdge("B","C"); addEdge("A","C");
        MaxCutAnalyzer a = new MaxCutAnalyzer(graph);
        assertTrue(a.computeLowerBound() <= a.computeUpperBound());
        assertTrue(a.computeLowerBound() >= 1.0);
    }

    @Test
    public void testExactWithinBounds() {
        addEdge("A","B"); addEdge("B","C"); addEdge("C","D"); addEdge("D","A");
        MaxCutAnalyzer a = new MaxCutAnalyzer(graph);
        double exact = a.computeExact().getCutValue();
        assertTrue(exact >= a.computeLowerBound());
        assertTrue(exact <= a.computeUpperBound());
    }

    @Test
    public void testLocalSearchAtLeastHalfEdges() {
        addEdge("A","B"); addEdge("A","C"); addEdge("A","D"); addEdge("B","C");
        addEdge("C","D"); addEdge("B","D"); addEdge("D","E"); addEdge("E","A");
        assertTrue(new MaxCutAnalyzer(graph).computeLocalSearch().getCutEdgeCount() >= graph.getEdgeCount()/2);
    }

    @Test
    public void testRandomLocalSearch() {
        addEdge("A","B"); addEdge("B","C"); addEdge("C","A");
        MaxCutAnalyzer.CutResult r = new MaxCutAnalyzer(graph).computeRandomLocalSearch();
        assertTrue(r.getCutEdgeCount() >= 2);
        assertEquals("RandomLocalSearch", r.getAlgorithm());
    }

    @Test
    public void testRandomLocalSearchCustomRestarts() {
        addEdge("A","B");
        assertEquals(1, new MaxCutAnalyzer(graph).computeRandomLocalSearch(5).getCutEdgeCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandomLocalSearchZeroRestartsThrows() { new MaxCutAnalyzer(graph).computeRandomLocalSearch(0); }

    @Test(expected = IllegalStateException.class)
    public void testExactTooManyVerticesThrows() {
        for (int i = 0; i < 21; i++) graph.addVertex("v"+i);
        new MaxCutAnalyzer(graph).computeExact();
    }

    @Test
    public void testBestUsesExactForSmall() {
        addEdge("A","B"); addEdge("B","C");
        assertEquals("Exact", new MaxCutAnalyzer(graph).computeBest().getAlgorithm());
    }

    @Test
    public void testVertexContributions() {
        addEdge("A","B"); addEdge("A","C");
        MaxCutAnalyzer a = new MaxCutAnalyzer(graph);
        MaxCutAnalyzer.CutResult r = a.computeExact();
        Map<String,Integer> c = a.computeVertexContributions(r);
        assertEquals(3, c.size());
        int total = 0; for (int v : c.values()) total += v;
        assertEquals(r.getCutEdgeCount()*2, total);
    }

    @Test
    public void testVertexContributionsEmpty() {
        assertTrue(new MaxCutAnalyzer(graph).computeVertexContributions(
            new MaxCutAnalyzer(graph).computeGreedy()).isEmpty());
    }

    @Test
    public void testFindBestFlipCandidateEmpty() {
        assertNull(new MaxCutAnalyzer(graph).findBestFlipCandidate(new MaxCutAnalyzer(graph).computeGreedy()));
    }

    @Test
    public void testFindBestFlipCandidate() {
        addEdge("A","B"); addEdge("B","C");
        MaxCutAnalyzer a = new MaxCutAnalyzer(graph);
        String best = a.findBestFlipCandidate(a.computeGreedy());
        assertNotNull(best);
        assertTrue(graph.containsVertex(best));
    }

    @Test
    public void testBalancedCut() {
        addEdge("A","B"); addEdge("B","C"); addEdge("C","D");
        MaxCutAnalyzer.CutResult r = new MaxCutAnalyzer(graph).computeBalanced(0);
        assertEquals("Balanced", r.getAlgorithm());
        assertTrue(Math.abs(r.getSetS().size() - r.getSetT().size()) <= 1);
    }

    @Test
    public void testBalancedCutWithTolerance() {
        addEdge("A","B"); addEdge("B","C"); addEdge("C","D"); addEdge("D","E");
        assertTrue(Math.abs(new MaxCutAnalyzer(graph).computeBalanced(1).getSetS().size()
            - new MaxCutAnalyzer(graph).computeBalanced(1).getSetT().size()) <= 2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBalancedNegativeToleranceThrows() { new MaxCutAnalyzer(graph).computeBalanced(-1); }

    @Test
    public void testBalancedEmptyGraph() { assertEquals(0, new MaxCutAnalyzer(graph).computeBalanced(0).getCutEdgeCount()); }

    @Test
    public void testCutResultProperties() {
        addEdge("A","B");
        MaxCutAnalyzer.CutResult r = new MaxCutAnalyzer(graph).computeExact();
        assertNotNull(r.getSetS()); assertNotNull(r.getSetT());
        assertNotNull(r.getCutEdges()); assertNotNull(r.getAlgorithm());
        assertEquals(1, r.getTotalEdges());
        assertEquals(1.0, r.getCutRatio(), 0.001);
    }

    @Test
    public void testCutResultSetsAreUnmodifiable() {
        addEdge("A","B");
        try { new MaxCutAnalyzer(graph).computeExact().getSetS().add("X"); fail(); }
        catch (UnsupportedOperationException e) { /* expected */ }
    }

    @Test
    public void testCutEdgesUnmodifiable() {
        addEdge("A","B");
        try { new MaxCutAnalyzer(graph).computeExact().getCutEdges().add(new Edge("f","X","Y")); fail(); }
        catch (UnsupportedOperationException e) { /* expected */ }
    }

    @Test
    public void testReportEmpty() { assertTrue(new MaxCutAnalyzer(graph).generateReport().contains("Empty graph")); }

    @Test
    public void testReportTriangle() {
        addEdge("A","B"); addEdge("B","C"); addEdge("A","C");
        String r = new MaxCutAnalyzer(graph).generateReport();
        assertTrue(r.contains("MaxCut Analysis Report"));
        assertTrue(r.contains("Bounds")); assertTrue(r.contains("Algorithm Comparison"));
        assertTrue(r.contains("Best Cut")); assertTrue(r.contains("Vertex Contributions"));
        assertTrue(r.contains("Approximation Quality"));
    }

    @Test
    public void testReportContainsAllAlgorithms() {
        addEdge("A","B"); addEdge("B","C");
        String r = new MaxCutAnalyzer(graph).generateReport();
        assertTrue(r.contains("Greedy")); assertTrue(r.contains("LocalSearch"));
        assertTrue(r.contains("RandomLocalSearch")); assertTrue(r.contains("Exact"));
    }

    @Test
    public void testDisconnectedGraph() {
        addEdge("A","B"); addEdge("C","D");
        assertEquals(2, new MaxCutAnalyzer(graph).computeExact().getCutEdgeCount());
    }

    @Test
    public void testStarGraphMaxCut() {
        addEdge("A","B"); addEdge("A","C"); addEdge("A","D"); addEdge("A","E");
        assertEquals(4, new MaxCutAnalyzer(graph).computeExact().getCutEdgeCount());
    }

    @Test
    public void testPetersenGraphExact() {
        addEdge("0","1"); addEdge("1","2"); addEdge("2","3"); addEdge("3","4"); addEdge("4","0");
        addEdge("5","7"); addEdge("7","9"); addEdge("9","6"); addEdge("6","8"); addEdge("8","5");
        addEdge("0","5"); addEdge("1","6"); addEdge("2","7"); addEdge("3","8"); addEdge("4","9");
        assertEquals(12, new MaxCutAnalyzer(graph).computeExact().getCutEdgeCount());
    }

    @Test
    public void testIsolatedVertices() {
        graph.addVertex("A"); graph.addVertex("B"); graph.addVertex("C");
        assertEquals(0, new MaxCutAnalyzer(graph).computeGreedy().getCutEdgeCount());
    }

    @Test
    public void testTwoVerticesNoEdge() {
        graph.addVertex("A"); graph.addVertex("B");
        assertEquals(0, new MaxCutAnalyzer(graph).computeExact().getCutEdgeCount());
    }

    @Test
    public void testCutRatioCalculation() {
        addEdge("A","B"); addEdge("B","C"); addEdge("A","C");
        MaxCutAnalyzer.CutResult r = new MaxCutAnalyzer(graph).computeExact();
        assertEquals((double) r.getCutEdgeCount() / 3.0, r.getCutRatio(), 0.001);
    }

    @Test
    public void testRandomLocalSearchEmpty() { assertEquals(0, new MaxCutAnalyzer(graph).computeRandomLocalSearch().getCutEdgeCount()); }

    @Test
    public void testBestOnEmpty() { assertEquals(0, new MaxCutAnalyzer(graph).computeBest().getCutEdgeCount()); }

    @Test
    public void testBalancedSingleEdge() {
        addEdge("A","B");
        MaxCutAnalyzer.CutResult r = new MaxCutAnalyzer(graph).computeBalanced(0);
        assertEquals(1, r.getCutEdgeCount());
        assertEquals(1, r.getSetS().size()); assertEquals(1, r.getSetT().size());
    }

    @Test
    public void testK5MaxCut() {
        String[] vs = {"A","B","C","D","E"};
        for (int i = 0; i < vs.length; i++) for (int j = i+1; j < vs.length; j++) addEdge(vs[i], vs[j]);
        assertEquals(6, new MaxCutAnalyzer(graph).computeExact().getCutEdgeCount());
        assertTrue(new MaxCutAnalyzer(graph).computeLocalSearch().getCutEdgeCount() >= 5);
    }

    @Test
    public void testGreedyAlgorithmName() { addEdge("A","B"); assertEquals("Greedy", new MaxCutAnalyzer(graph).computeGreedy().getAlgorithm()); }

    @Test
    public void testLocalSearchAlgorithmName() { addEdge("A","B"); assertEquals("LocalSearch", new MaxCutAnalyzer(graph).computeLocalSearch().getAlgorithm()); }

    @Test
    public void testExactAlgorithmName() { addEdge("A","B"); assertEquals("Exact", new MaxCutAnalyzer(graph).computeExact().getAlgorithm()); }
}
