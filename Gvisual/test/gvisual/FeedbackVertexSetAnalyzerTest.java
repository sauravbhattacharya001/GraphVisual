package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for FeedbackVertexSetAnalyzer.
 */
public class FeedbackVertexSetAnalyzerTest {

    private Graph<String, Edge> emptyGraph;
    private Graph<String, Edge> singleVertex;
    private Graph<String, Edge> singleEdge;
    private Graph<String, Edge> triangle;
    private Graph<String, Edge> path4;
    private Graph<String, Edge> star5;
    private Graph<String, Edge> cycle4;
    private Graph<String, Edge> cycle5;
    private Graph<String, Edge> complete4;
    private Graph<String, Edge> twoCycles;
    private Graph<String, Edge> diamond;
    private Graph<String, Edge> petersen;
    private int edgeId = 0;

    private Edge addEdge(Graph<String, Edge> g, String u, String v) {
        Edge e  new Edge("e", u, v);
        e.setLabel("e" + (edgeId++));
        g.addEdge(e, u, v);
        return e;
    }

    @Before
    public void setUp() {
        edgeId = 0;

        emptyGraph = new UndirectedSparseGraph<>();

        singleVertex = new UndirectedSparseGraph<>();
        singleVertex.addVertex("A");

        singleEdge = new UndirectedSparseGraph<>();
        singleEdge.addVertex("A"); singleEdge.addVertex("B");
        addEdge(singleEdge, "A", "B");

        triangle = new UndirectedSparseGraph<>();
        for (String v : new String[]{"A", "B", "C"}) triangle.addVertex(v);
        addEdge(triangle, "A", "B"); addEdge(triangle, "B", "C"); addEdge(triangle, "C", "A");

        path4 = new UndirectedSparseGraph<>();
        for (String v : new String[]{"A", "B", "C", "D"}) path4.addVertex(v);
        addEdge(path4, "A", "B"); addEdge(path4, "B", "C"); addEdge(path4, "C", "D");

        star5 = new UndirectedSparseGraph<>();
        for (String v : new String[]{"C", "L1", "L2", "L3", "L4"}) star5.addVertex(v);
        addEdge(star5, "C", "L1"); addEdge(star5, "C", "L2");
        addEdge(star5, "C", "L3"); addEdge(star5, "C", "L4");

        cycle4 = new UndirectedSparseGraph<>();
        for (String v : new String[]{"A", "B", "C", "D"}) cycle4.addVertex(v);
        addEdge(cycle4, "A", "B"); addEdge(cycle4, "B", "C");
        addEdge(cycle4, "C", "D"); addEdge(cycle4, "D", "A");

        cycle5 = new UndirectedSparseGraph<>();
        for (String v : new String[]{"A", "B", "C", "D", "E"}) cycle5.addVertex(v);
        addEdge(cycle5, "A", "B"); addEdge(cycle5, "B", "C");
        addEdge(cycle5, "C", "D"); addEdge(cycle5, "D", "E"); addEdge(cycle5, "E", "A");

        complete4 = new UndirectedSparseGraph<>();
        for (String v : new String[]{"A", "B", "C", "D"}) complete4.addVertex(v);
        addEdge(complete4, "A", "B"); addEdge(complete4, "A", "C"); addEdge(complete4, "A", "D");
        addEdge(complete4, "B", "C"); addEdge(complete4, "B", "D"); addEdge(complete4, "C", "D");

        twoCycles = new UndirectedSparseGraph<>();
        for (String v : new String[]{"A", "B", "C", "D", "E", "F"}) twoCycles.addVertex(v);
        addEdge(twoCycles, "A", "B"); addEdge(twoCycles, "B", "C"); addEdge(twoCycles, "C", "A");
        addEdge(twoCycles, "D", "E"); addEdge(twoCycles, "E", "F"); addEdge(twoCycles, "F", "D");

        diamond = new UndirectedSparseGraph<>();
        for (String v : new String[]{"A", "B", "C", "D"}) diamond.addVertex(v);
        addEdge(diamond, "A", "B"); addEdge(diamond, "A", "C");
        addEdge(diamond, "B", "D"); addEdge(diamond, "C", "D"); addEdge(diamond, "B", "C");

        petersen = new UndirectedSparseGraph<>();
        for (int i = 0; i < 10; i++) petersen.addVertex("V" + i);
        for (int i = 0; i < 5; i++) addEdge(petersen, "V" + i, "V" + ((i + 1) % 5));
        for (int i = 0; i < 5; i++) addEdge(petersen, "V" + (i + 5), "V" + ((i + 2) % 5 + 5));
        for (int i = 0; i < 5; i++) addEdge(petersen, "V" + i, "V" + (i + 5));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraph() { new FeedbackVertexSetAnalyzer(null); }

    @Test
    public void testIsValidFVS_nullReturnsFalse() {
        assertFalse(new FeedbackVertexSetAnalyzer(triangle).isValidFVS(null));
    }

    @Test
    public void testIsValidFVS_emptySetOnAcyclicGraph() {
        assertTrue(new FeedbackVertexSetAnalyzer(path4).isValidFVS(new HashSet<>()));
    }

    @Test
    public void testIsValidFVS_emptySetOnCyclicGraph() {
        assertFalse(new FeedbackVertexSetAnalyzer(triangle).isValidFVS(new HashSet<>()));
    }

    @Test
    public void testIsValidFVS_oneVertexBreaksTriangle() {
        FeedbackVertexSetAnalyzer a = new FeedbackVertexSetAnalyzer(triangle);
        assertTrue(a.isValidFVS(new HashSet<>(Arrays.asList("A"))));
        assertTrue(a.isValidFVS(new HashSet<>(Arrays.asList("B"))));
        assertTrue(a.isValidFVS(new HashSet<>(Arrays.asList("C"))));
    }

    @Test
    public void testIsValidFVS_wrongVertexDoesNotBreak() {
        assertFalse(new FeedbackVertexSetAnalyzer(twoCycles).isValidFVS(new HashSet<>(Arrays.asList("A"))));
    }

    @Test
    public void testIsValidFVS_allVertices() {
        assertTrue(new FeedbackVertexSetAnalyzer(complete4).isValidFVS(
                new HashSet<>(Arrays.asList("A", "B", "C", "D"))));
    }

    @Test
    public void testGreedyFVS_emptyGraph() {
        assertTrue(new FeedbackVertexSetAnalyzer(emptyGraph).greedyFVS().isEmpty());
    }

    @Test
    public void testGreedyFVS_singleVertex() {
        assertTrue(new FeedbackVertexSetAnalyzer(singleVertex).greedyFVS().isEmpty());
    }

    @Test
    public void testGreedyFVS_acyclicGraph() {
        assertTrue(new FeedbackVertexSetAnalyzer(path4).greedyFVS().isEmpty());
    }

    @Test
    public void testGreedyFVS_star() {
        assertTrue(new FeedbackVertexSetAnalyzer(star5).greedyFVS().isEmpty());
    }

    @Test
    public void testGreedyFVS_triangle() {
        FeedbackVertexSetAnalyzer a = new FeedbackVertexSetAnalyzer(triangle);
        Set<String> fvs = a.greedyFVS();
        assertEquals(1, fvs.size());
        assertTrue(a.isValidFVS(fvs));
    }

    @Test
    public void testGreedyFVS_cycle4() {
        FeedbackVertexSetAnalyzer a = new FeedbackVertexSetAnalyzer(cycle4);
        Set<String> fvs = a.greedyFVS();
        assertEquals(1, fvs.size());
        assertTrue(a.isValidFVS(fvs));
    }

    @Test
    public void testGreedyFVS_cycle5() {
        FeedbackVertexSetAnalyzer a = new FeedbackVertexSetAnalyzer(cycle5);
        Set<String> fvs = a.greedyFVS();
        assertEquals(1, fvs.size());
        assertTrue(a.isValidFVS(fvs));
    }

    @Test
    public void testGreedyFVS_complete4() {
        FeedbackVertexSetAnalyzer a = new FeedbackVertexSetAnalyzer(complete4);
        Set<String> fvs = a.greedyFVS();
        assertTrue(fvs.size() >= 2);
        assertTrue(a.isValidFVS(fvs));
    }

    @Test
    public void testGreedyFVS_twoCycles() {
        FeedbackVertexSetAnalyzer a = new FeedbackVertexSetAnalyzer(twoCycles);
        Set<String> fvs = a.greedyFVS();
        assertEquals(2, fvs.size());
        assertTrue(a.isValidFVS(fvs));
    }

    @Test
    public void testGreedyFVS_diamond() {
        FeedbackVertexSetAnalyzer a = new FeedbackVertexSetAnalyzer(diamond);
        assertTrue(a.greedyFVS().size() >= 1);
        assertTrue(a.isValidFVS(a.greedyFVS()));
    }

    @Test
    public void testGreedyFVS_petersen() {
        FeedbackVertexSetAnalyzer a = new FeedbackVertexSetAnalyzer(petersen);
        assertTrue(a.greedyFVS().size() >= 1);
        assertTrue(a.isValidFVS(a.greedyFVS()));
    }

    @Test
    public void testExactFVS_emptyGraph() {
        Set<String> fvs = new FeedbackVertexSetAnalyzer(emptyGraph).exactMinimumFVS();
        assertNotNull(fvs); assertTrue(fvs.isEmpty());
    }

    @Test
    public void testExactFVS_acyclicGraph() {
        Set<String> fvs = new FeedbackVertexSetAnalyzer(path4).exactMinimumFVS();
        assertNotNull(fvs); assertTrue(fvs.isEmpty());
    }

    @Test
    public void testExactFVS_triangle() {
        FeedbackVertexSetAnalyzer a = new FeedbackVertexSetAnalyzer(triangle);
        Set<String> fvs = a.exactMinimumFVS();
        assertNotNull(fvs); assertEquals(1, fvs.size()); assertTrue(a.isValidFVS(fvs));
    }

    @Test
    public void testExactFVS_cycle5() {
        FeedbackVertexSetAnalyzer a = new FeedbackVertexSetAnalyzer(cycle5);
        Set<String> fvs = a.exactMinimumFVS();
        assertNotNull(fvs); assertEquals(1, fvs.size()); assertTrue(a.isValidFVS(fvs));
    }

    @Test
    public void testExactFVS_complete4() {
        FeedbackVertexSetAnalyzer a = new FeedbackVertexSetAnalyzer(complete4);
        Set<String> fvs = a.exactMinimumFVS();
        assertNotNull(fvs); assertEquals(2, fvs.size()); assertTrue(a.isValidFVS(fvs));
    }

    @Test
    public void testExactFVS_twoCycles() {
        FeedbackVertexSetAnalyzer a = new FeedbackVertexSetAnalyzer(twoCycles);
        Set<String> fvs = a.exactMinimumFVS();
        assertNotNull(fvs); assertEquals(2, fvs.size()); assertTrue(a.isValidFVS(fvs));
    }

    @Test
    public void testExactFVS_petersen() {
        FeedbackVertexSetAnalyzer a = new FeedbackVertexSetAnalyzer(petersen);
        Set<String> fvs = a.exactMinimumFVS();
        assertNotNull(fvs); assertTrue(a.isValidFVS(fvs));
    }

    @Test
    public void testExactFVS_diamond() {
        FeedbackVertexSetAnalyzer a = new FeedbackVertexSetAnalyzer(diamond);
        Set<String> fvs = a.exactMinimumFVS();
        assertNotNull(fvs); assertTrue(a.isValidFVS(fvs));
    }

    @Test
    public void testFeedbackEdgeSet_empty() {
        assertTrue(new FeedbackVertexSetAnalyzer(emptyGraph).feedbackEdgeSet().isEmpty());
    }

    @Test
    public void testFeedbackEdgeSet_acyclic() {
        assertTrue(new FeedbackVertexSetAnalyzer(path4).feedbackEdgeSet().isEmpty());
    }

    @Test
    public void testFeedbackEdgeSet_triangle() {
        assertEquals(1, new FeedbackVertexSetAnalyzer(triangle).feedbackEdgeSet().size());
    }

    @Test
    public void testFeedbackEdgeSet_cycle4() {
        assertEquals(1, new FeedbackVertexSetAnalyzer(cycle4).feedbackEdgeSet().size());
    }

    @Test
    public void testFeedbackEdgeSet_complete4() {
        assertEquals(3, new FeedbackVertexSetAnalyzer(complete4).feedbackEdgeSet().size());
    }

    @Test
    public void testFeedbackEdgeSet_twoCycles() {
        assertEquals(2, new FeedbackVertexSetAnalyzer(twoCycles).feedbackEdgeSet().size());
    }

    @Test
    public void testCycleRank_empty() { assertEquals(0, new FeedbackVertexSetAnalyzer(emptyGraph).cycleRank()); }

    @Test
    public void testCycleRank_singleVertex() { assertEquals(0, new FeedbackVertexSetAnalyzer(singleVertex).cycleRank()); }

    @Test
    public void testCycleRank_path() { assertEquals(0, new FeedbackVertexSetAnalyzer(path4).cycleRank()); }

    @Test
    public void testCycleRank_triangle() { assertEquals(1, new FeedbackVertexSetAnalyzer(triangle).cycleRank()); }

    @Test
    public void testCycleRank_cycle5() { assertEquals(1, new FeedbackVertexSetAnalyzer(cycle5).cycleRank()); }

    @Test
    public void testCycleRank_complete4() { assertEquals(3, new FeedbackVertexSetAnalyzer(complete4).cycleRank()); }

    @Test
    public void testCycleRank_twoCycles() { assertEquals(2, new FeedbackVertexSetAnalyzer(twoCycles).cycleRank()); }

    @Test
    public void testLowerBound_acyclic() { assertEquals(0, new FeedbackVertexSetAnalyzer(path4).lowerBound()); }

    @Test
    public void testLowerBound_triangle() { assertTrue(new FeedbackVertexSetAnalyzer(triangle).lowerBound() >= 1); }

    @Test
    public void testUpperBound_acyclic() { assertEquals(0, new FeedbackVertexSetAnalyzer(path4).upperBound()); }

    @Test
    public void testBounds_consistency() {
        FeedbackVertexSetAnalyzer a = new FeedbackVertexSetAnalyzer(complete4);
        assertTrue(a.lowerBound() <= a.upperBound());
    }

    @Test
    public void testCriticality_acyclic() {
        for (int val : new FeedbackVertexSetAnalyzer(path4).vertexCriticality().values()) assertEquals(0, val);
    }

    @Test
    public void testCriticality_triangle() {
        Map<String, Integer> crit = new FeedbackVertexSetAnalyzer(triangle).vertexCriticality();
        assertEquals(3, crit.size());
        for (int val : crit.values()) assertEquals(1, val);
    }

    @Test
    public void testCriticality_complete4_sorted() {
        List<Integer> vals = new ArrayList<>(new FeedbackVertexSetAnalyzer(complete4).vertexCriticality().values());
        for (int i = 0; i < vals.size() - 1; i++) assertTrue(vals.get(i) >= vals.get(i + 1));
    }

    @Test
    public void testCyclePacking_acyclic() {
        assertTrue(new FeedbackVertexSetAnalyzer(path4).disjointCyclePacking().isEmpty());
    }

    @Test
    public void testCyclePacking_triangle() {
        List<List<String>> p = new FeedbackVertexSetAnalyzer(triangle).disjointCyclePacking();
        assertEquals(1, p.size()); assertEquals(3, p.get(0).size());
    }

    @Test
    public void testCyclePacking_twoCycles() {
        List<List<String>> p = new FeedbackVertexSetAnalyzer(twoCycles).disjointCyclePacking();
        assertEquals(2, p.size());
        Set<String> all = new HashSet<>();
        for (List<String> c : p) for (String v : c) assertTrue(all.add(v));
    }

    @Test
    public void testCyclePacking_lowerBound() {
        FeedbackVertexSetAnalyzer a = new FeedbackVertexSetAnalyzer(petersen);
        assertTrue(a.greedyFVS().size() >= a.disjointCyclePacking().size());
    }

    @Test
    public void testApproxRatio_acyclic() {
        assertEquals(1.0, new FeedbackVertexSetAnalyzer(path4).approximationRatio(), 0.001);
    }

    @Test
    public void testApproxRatio_cyclic() {
        assertTrue(new FeedbackVertexSetAnalyzer(complete4).approximationRatio() >= 1.0);
    }

    @Test
    public void testReport_acyclic() {
        FeedbackVertexSetAnalyzer.FVSReport r = new FeedbackVertexSetAnalyzer(path4).generateReport();
        assertTrue(r.isAcyclic); assertEquals(0, r.cycleRank); assertTrue(r.greedyFVS.isEmpty());
    }

    @Test
    public void testReport_cyclic() {
        FeedbackVertexSetAnalyzer.FVSReport r = new FeedbackVertexSetAnalyzer(complete4).generateReport();
        assertFalse(r.isAcyclic); assertEquals(4, r.vertexCount); assertEquals(6, r.edgeCount);
        assertEquals(3, r.cycleRank); assertTrue(r.greedyFVS.size() >= 2);
        assertNotNull(r.exactFVS); assertEquals(2, r.exactFVS.size());
    }

    @Test
    public void testTextReport_acyclic() {
        String report = new FeedbackVertexSetAnalyzer(path4).textReport();
        assertTrue(report.contains("acyclic"));
    }

    @Test
    public void testTextReport_cyclic() {
        String report = new FeedbackVertexSetAnalyzer(triangle).textReport();
        assertTrue(report.contains("Greedy FVS"));
        assertTrue(report.contains("Exact FVS"));
    }

    @Test
    public void testSingleEdge_noCycle() {
        FeedbackVertexSetAnalyzer a = new FeedbackVertexSetAnalyzer(singleEdge);
        assertTrue(a.greedyFVS().isEmpty()); assertEquals(0, a.cycleRank());
    }

    @Test
    public void testExactEqualsOrBetterThanGreedy() {
        for (Graph<String, Edge> g : Arrays.asList(triangle, cycle4, cycle5, complete4, twoCycles, diamond)) {
            FeedbackVertexSetAnalyzer a = new FeedbackVertexSetAnalyzer(g);
            assertTrue(a.exactMinimumFVS().size() <= a.greedyFVS().size());
        }
    }

    @Test
    public void testFeedbackEdgeSetSizeEqualsCycleRank() {
        for (Graph<String, Edge> g : Arrays.asList(emptyGraph, singleVertex, singleEdge, path4, triangle, cycle5, complete4, twoCycles)) {
            FeedbackVertexSetAnalyzer a = new FeedbackVertexSetAnalyzer(g);
            assertEquals(a.cycleRank(), a.feedbackEdgeSet().size());
        }
    }
}
