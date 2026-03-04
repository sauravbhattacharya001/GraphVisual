package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for SignedGraphAnalyzer.
 */
public class SignedGraphAnalyzerTest {

    private Graph<String, edge> emptyGraph;
    private Graph<String, edge> singleVertex;
    private Graph<String, edge> allPositive;     // triangle with all + edges
    private Graph<String, edge> balanced;        // balanced: A-B+, B-C+, A-C- (two groups)
    private Graph<String, edge> unbalanced;      // triangle with 2+ and 1- (ppn)
    private Graph<String, edge> allNegative;     // triangle with all - edges
    private Graph<String, edge> path3;           // A--B--C with mixed signs
    private Graph<String, edge> square;          // 4-cycle
    private Graph<String, edge> largeBalanced;   // larger balanced graph
    private int edgeCounter = 0;

    private edge makeEdge(String v1, String v2, float weight) {
        edge e = new edge("e", v1, v2);
        e.setWeight(weight);
        e.setLabel(weight < 0 ? "-" : "+");
        return e;
    }

    private edge makeLabelEdge(String v1, String v2, String label) {
        edge e = new edge("e", v1, v2);
        e.setLabel(label);
        return e;
    }

    @Before
    public void setUp() {
        edgeCounter = 0;

        // Empty graph
        emptyGraph = new UndirectedSparseGraph<>();

        // Single vertex
        singleVertex = new UndirectedSparseGraph<>();
        singleVertex.addVertex("A");

        // All-positive triangle: A-B+, B-C+, A-C+
        allPositive = new UndirectedSparseGraph<>();
        allPositive.addVertex("A"); allPositive.addVertex("B"); allPositive.addVertex("C");
        allPositive.addEdge(makeEdge("A","B",1), "A", "B");
        allPositive.addEdge(makeEdge("B","C",1), "B", "C");
        allPositive.addEdge(makeEdge("A","C",1), "A", "C");

        // Balanced triangle: A-B+, B-C-, A-C- → +-- (balanced)
        balanced = new UndirectedSparseGraph<>();
        balanced.addVertex("A"); balanced.addVertex("B"); balanced.addVertex("C");
        balanced.addEdge(makeEdge("A","B",1), "A", "B");
        balanced.addEdge(makeEdge("B","C",-1), "B", "C");
        balanced.addEdge(makeEdge("A","C",-1), "A", "C");

        // Unbalanced triangle: A-B+, B-C+, A-C- → ++- (unbalanced)
        unbalanced = new UndirectedSparseGraph<>();
        unbalanced.addVertex("A"); unbalanced.addVertex("B"); unbalanced.addVertex("C");
        unbalanced.addEdge(makeEdge("A","B",1), "A", "B");
        unbalanced.addEdge(makeEdge("B","C",1), "B", "C");
        unbalanced.addEdge(makeEdge("A","C",-1), "A", "C");

        // All-negative triangle
        allNegative = new UndirectedSparseGraph<>();
        allNegative.addVertex("A"); allNegative.addVertex("B"); allNegative.addVertex("C");
        allNegative.addEdge(makeEdge("A","B",-1), "A", "B");
        allNegative.addEdge(makeEdge("B","C",-1), "B", "C");
        allNegative.addEdge(makeEdge("A","C",-1), "A", "C");

        // Path: A--(+)--B--(--)--C
        path3 = new UndirectedSparseGraph<>();
        path3.addVertex("A"); path3.addVertex("B"); path3.addVertex("C");
        path3.addEdge(makeEdge("A","B",1), "A", "B");
        path3.addEdge(makeEdge("B","C",-1), "B", "C");

        // Square: A-B+, B-C-, C-D+, D-A- (balanced cycle: 2 negatives)
        square = new UndirectedSparseGraph<>();
        square.addVertex("A"); square.addVertex("B");
        square.addVertex("C"); square.addVertex("D");
        square.addEdge(makeEdge("A","B",1), "A", "B");
        square.addEdge(makeEdge("B","C",-1), "B", "C");
        square.addEdge(makeEdge("C","D",1), "C", "D");
        square.addEdge(makeEdge("D","A",-1), "D", "A");

        // Larger balanced: two groups {A,B,C} and {D,E}
        // Within-group: positive; cross-group: negative
        largeBalanced = new UndirectedSparseGraph<>();
        for (String v : new String[]{"A","B","C","D","E"}) largeBalanced.addVertex(v);
        largeBalanced.addEdge(makeEdge("A","B",1), "A", "B");
        largeBalanced.addEdge(makeEdge("A","C",1), "A", "C");
        largeBalanced.addEdge(makeEdge("B","C",1), "B", "C");
        largeBalanced.addEdge(makeEdge("D","E",1), "D", "E");
        largeBalanced.addEdge(makeEdge("A","D",-1), "A", "D");
        largeBalanced.addEdge(makeEdge("B","E",-1), "B", "E");
    }

    // ---- Constructor ----

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraph() {
        new SignedGraphAnalyzer(null);
    }

    // ---- Edge Sign Detection ----

    @Test
    public void testIsNegativeByWeight() {
        edge e = makeEdge("A","B",-1);
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(emptyGraph);
        assertTrue(a.isNegative(e));
        assertFalse(a.isPositive(e));
    }

    @Test
    public void testIsPositiveByWeight() {
        edge e = makeEdge("A","B",1);
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(emptyGraph);
        assertFalse(a.isNegative(e));
        assertTrue(a.isPositive(e));
    }

    @Test
    public void testIsNegativeByLabel() {
        edge e = makeLabelEdge("A","B","-");
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(emptyGraph);
        assertTrue(a.isNegative(e));
    }

    @Test
    public void testIsNegativeByLabelWord() {
        edge e = makeLabelEdge("A","B","negative");
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(emptyGraph);
        assertTrue(a.isNegative(e));
    }

    @Test
    public void testZeroWeightIsPositive() {
        edge e = makeEdge("A","B",0);
        e.setLabel(null);
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(emptyGraph);
        assertTrue(a.isPositive(e));
    }

    // ---- Edge Counts ----

    @Test
    public void testEdgeCountsEmpty() {
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(emptyGraph);
        assertEquals(0, a.countPositiveEdges());
        assertEquals(0, a.countNegativeEdges());
        assertEquals(0.0, a.negativityRatio(), 0.001);
    }

    @Test
    public void testEdgeCountsAllPositive() {
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(allPositive);
        assertEquals(3, a.countPositiveEdges());
        assertEquals(0, a.countNegativeEdges());
        assertEquals(0.0, a.negativityRatio(), 0.001);
    }

    @Test
    public void testEdgeCountsAllNegative() {
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(allNegative);
        assertEquals(0, a.countPositiveEdges());
        assertEquals(3, a.countNegativeEdges());
        assertEquals(1.0, a.negativityRatio(), 0.001);
    }

    @Test
    public void testEdgeCountsMixed() {
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(balanced);
        assertEquals(1, a.countPositiveEdges());
        assertEquals(2, a.countNegativeEdges());
        assertEquals(2.0/3.0, a.negativityRatio(), 0.001);
    }

    // ---- Vertex Polarization ----

    @Test
    public void testPolarizationEmpty() {
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(singleVertex);
        Map<String, Double> pol = a.vertexPolarization();
        assertEquals(0.0, pol.get("A"), 0.001);
    }

    @Test
    public void testPolarizationAllPositive() {
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(allPositive);
        for (double v : a.vertexPolarization().values()) {
            assertEquals(0.0, v, 0.001);
        }
    }

    @Test
    public void testPolarizationMixed() {
        // balanced: A has 1+, 1-; B has 1+, 1-; C has 2-
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(balanced);
        Map<String, Double> pol = a.vertexPolarization();
        assertEquals(0.5, pol.get("A"), 0.001);  // 1 neg of 2
        assertEquals(0.5, pol.get("B"), 0.001);  // 1 neg of 2
        assertEquals(1.0, pol.get("C"), 0.001);  // 2 neg of 2
    }

    @Test
    public void testMostPolarizedVertex() {
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(balanced);
        assertEquals("C", a.mostPolarizedVertex());
    }

    @Test
    public void testMostPolarizedEmpty() {
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(emptyGraph);
        assertNull(a.mostPolarizedVertex());
    }

    // ---- Triangle Census ----

    @Test
    public void testTriangleCensusNoTriangles() {
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(path3);
        SignedGraphAnalyzer.TriangleCensus c = a.triangleCensus();
        assertEquals(0, c.total());
        assertEquals(1.0, c.strongBalanceDegree(), 0.001);
    }

    @Test
    public void testTriangleCensusPPP() {
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(allPositive);
        SignedGraphAnalyzer.TriangleCensus c = a.triangleCensus();
        assertEquals(1, c.ppp);
        assertEquals(0, c.ppn);
        assertEquals(0, c.pnn);
        assertEquals(0, c.nnn);
        assertEquals(1, c.stronglyBalanced());
        assertEquals(0, c.stronglyUnbalanced());
    }

    @Test
    public void testTriangleCensusPNN() {
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(balanced);
        SignedGraphAnalyzer.TriangleCensus c = a.triangleCensus();
        assertEquals(0, c.ppp);
        assertEquals(0, c.ppn);
        assertEquals(1, c.pnn);
        assertEquals(0, c.nnn);
    }

    @Test
    public void testTriangleCensusPPN() {
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(unbalanced);
        SignedGraphAnalyzer.TriangleCensus c = a.triangleCensus();
        assertEquals(0, c.ppp);
        assertEquals(1, c.ppn);
        assertEquals(0, c.pnn);
        assertEquals(0, c.nnn);
    }

    @Test
    public void testTriangleCensusNNN() {
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(allNegative);
        SignedGraphAnalyzer.TriangleCensus c = a.triangleCensus();
        assertEquals(0, c.ppp);
        assertEquals(0, c.ppn);
        assertEquals(0, c.pnn);
        assertEquals(1, c.nnn);
    }

    @Test
    public void testStrongBalanceDegreeUnbalanced() {
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(unbalanced);
        assertEquals(0.0, a.triangleCensus().strongBalanceDegree(), 0.001);
    }

    @Test
    public void testWeakBalanceDegree() {
        // NNN is weakly balanced
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(allNegative);
        assertEquals(1.0, a.triangleCensus().weakBalanceDegree(), 0.001);
    }

    // ---- Strong Balance ----

    @Test
    public void testStronglyBalancedEmpty() {
        assertTrue(new SignedGraphAnalyzer(emptyGraph).isStronglyBalanced());
    }

    @Test
    public void testStronglyBalancedSingleVertex() {
        assertTrue(new SignedGraphAnalyzer(singleVertex).isStronglyBalanced());
    }

    @Test
    public void testStronglyBalancedAllPositive() {
        assertTrue(new SignedGraphAnalyzer(allPositive).isStronglyBalanced());
    }

    @Test
    public void testStronglyBalancedPNN() {
        assertTrue(new SignedGraphAnalyzer(balanced).isStronglyBalanced());
    }

    @Test
    public void testStronglyUnbalancedPPN() {
        assertFalse(new SignedGraphAnalyzer(unbalanced).isStronglyBalanced());
    }

    @Test
    public void testStronglyUnbalancedNNN() {
        assertFalse(new SignedGraphAnalyzer(allNegative).isStronglyBalanced());
    }

    @Test
    public void testStronglyBalancedSquare() {
        // Square with 2 negatives on opposite sides: balanced
        assertTrue(new SignedGraphAnalyzer(square).isStronglyBalanced());
    }

    @Test
    public void testStronglyBalancedLarge() {
        assertTrue(new SignedGraphAnalyzer(largeBalanced).isStronglyBalanced());
    }

    // ---- Weak Balance ----

    @Test
    public void testWeaklyBalancedEmpty() {
        assertTrue(new SignedGraphAnalyzer(emptyGraph).isWeaklyBalanced());
    }

    @Test
    public void testWeaklyBalancedAllPositive() {
        assertTrue(new SignedGraphAnalyzer(allPositive).isWeaklyBalanced());
    }

    @Test
    public void testWeaklyBalancedAllNegative() {
        // All-negative triangle: weakly balanced (3 singletons)
        assertTrue(new SignedGraphAnalyzer(allNegative).isWeaklyBalanced());
    }

    @Test
    public void testWeaklyUnbalancedPPN() {
        // ++- triangle: NOT weakly balanced (negative edge within positive component)
        assertFalse(new SignedGraphAnalyzer(unbalanced).isWeaklyBalanced());
    }

    @Test
    public void testWeaklyBalancedPNN() {
        assertTrue(new SignedGraphAnalyzer(balanced).isWeaklyBalanced());
    }

    // ---- Coalition Detection ----

    @Test
    public void testCoalitionsEmpty() {
        List<Set<String>> c = new SignedGraphAnalyzer(emptyGraph).findCoalitions();
        assertTrue(c.isEmpty());
    }

    @Test
    public void testCoalitionsBalancedTriangle() {
        List<Set<String>> c = new SignedGraphAnalyzer(balanced).findCoalitions();
        assertEquals(2, c.size());
    }

    @Test
    public void testCoalitionsAllPositive() {
        List<Set<String>> c = new SignedGraphAnalyzer(allPositive).findCoalitions();
        // All in one group (or two with one empty — implementation puts all same color)
        // With strong balance 2-coloring of all-positive, everyone gets same color
        boolean allInOne = c.stream().anyMatch(s -> s.size() == 3);
        assertTrue(allInOne);
    }

    @Test
    public void testCoalitionsLargeBalanced() {
        List<Set<String>> c = new SignedGraphAnalyzer(largeBalanced).findCoalitions();
        assertEquals(2, c.size());
        // Should be {A,B,C} and {D,E}
        Set<String> group1 = new HashSet<>(Arrays.asList("A","B","C"));
        Set<String> group2 = new HashSet<>(Arrays.asList("D","E"));
        assertTrue(
            (c.get(0).equals(group1) && c.get(1).equals(group2)) ||
            (c.get(0).equals(group2) && c.get(1).equals(group1))
        );
    }

    @Test
    public void testCoalitionsAllNegative() {
        // Unbalanced (not strongly), so uses weak coalitions (positive components = singletons)
        List<Set<String>> c = new SignedGraphAnalyzer(allNegative).findCoalitions();
        assertEquals(3, c.size());
        for (Set<String> group : c) {
            assertEquals(1, group.size());
        }
    }

    // ---- Frustration Index ----

    @Test
    public void testFrustrationBalanced() {
        assertEquals(0, new SignedGraphAnalyzer(allPositive).frustrationIndex());
        assertEquals(0, new SignedGraphAnalyzer(balanced).frustrationIndex());
        assertEquals(0, new SignedGraphAnalyzer(largeBalanced).frustrationIndex());
    }

    @Test
    public void testFrustrationEmpty() {
        assertEquals(0, new SignedGraphAnalyzer(emptyGraph).frustrationIndex());
    }

    @Test
    public void testFrustrationUnbalanced() {
        // ++- triangle: need to flip 1 edge
        assertEquals(1, new SignedGraphAnalyzer(unbalanced).frustrationIndex());
    }

    @Test
    public void testFrustrationAllNegative() {
        // --- triangle: need to flip 1 edge to get +--
        assertEquals(1, new SignedGraphAnalyzer(allNegative).frustrationIndex());
    }

    // ---- Frustrated Edges ----

    @Test
    public void testFrustratedEdgesBalanced() {
        assertTrue(new SignedGraphAnalyzer(allPositive).findFrustratedEdges().isEmpty());
    }

    @Test
    public void testFrustratedEdgesUnbalanced() {
        List<edge> frustrated = new SignedGraphAnalyzer(unbalanced).findFrustratedEdges();
        assertFalse(frustrated.isEmpty());
    }

    // ---- Sign Prediction ----

    @Test
    public void testPredictSignNoMutual() {
        // Path A-B-C: A and C have mutual neighbor B
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(path3);
        // A-B is +, B-C is -; product = -1, predict negative
        assertEquals(-1, a.predictSign("A", "C"));
    }

    @Test
    public void testPredictSignPositive() {
        // All-positive triangle: A-C exists, but predict via B
        // Need a graph where A-C doesn't exist but they share neighbors
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C"); g.addVertex("D");
        g.addEdge(makeEdge("A","B",1), "A", "B");
        g.addEdge(makeEdge("B","C",1), "B", "C");
        g.addEdge(makeEdge("A","D",1), "A", "D");
        g.addEdge(makeEdge("D","C",1), "D", "C");
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(g);
        // Both paths predict +: (+)*(+) = +
        assertEquals(1, a.predictSign("A", "C"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPredictSignInvalidVertex() {
        new SignedGraphAnalyzer(allPositive).predictSign("A", "Z");
    }

    @Test
    public void testPredictSignNoEvidence() {
        // Two disconnected vertices
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B");
        assertEquals(0, new SignedGraphAnalyzer(g).predictSign("A", "B"));
    }

    // ---- Status Consistency ----

    @Test
    public void testStatusConsistencyBalanced() {
        assertEquals(1.0, new SignedGraphAnalyzer(allPositive).statusConsistency(), 0.001);
        assertEquals(1.0, new SignedGraphAnalyzer(balanced).statusConsistency(), 0.001);
    }

    @Test
    public void testStatusConsistencyUnbalanced() {
        assertEquals(0.0, new SignedGraphAnalyzer(unbalanced).statusConsistency(), 0.001);
    }

    @Test
    public void testStatusConsistencyNoTriangles() {
        assertEquals(1.0, new SignedGraphAnalyzer(path3).statusConsistency(), 0.001);
    }

    // ---- Full Report ----

    @Test
    public void testAnalyzeEmpty() {
        SignedGraphAnalyzer.SignedGraphReport r = new SignedGraphAnalyzer(emptyGraph).analyze();
        assertEquals(0, r.vertexCount);
        assertEquals(0, r.edgeCount);
        assertTrue(r.stronglyBalanced);
        assertTrue(r.weaklyBalanced);
        assertEquals(0, r.frustrationIndex);
    }

    @Test
    public void testAnalyzeBalanced() {
        SignedGraphAnalyzer.SignedGraphReport r = new SignedGraphAnalyzer(largeBalanced).analyze();
        assertEquals(5, r.vertexCount);
        assertEquals(6, r.edgeCount);
        assertEquals(4, r.positiveEdges);
        assertEquals(2, r.negativeEdges);
        assertTrue(r.stronglyBalanced);
        assertTrue(r.weaklyBalanced);
        assertEquals(0, r.frustrationIndex);
        assertEquals(2, r.coalitionCount);
    }

    @Test
    public void testAnalyzeUnbalanced() {
        SignedGraphAnalyzer.SignedGraphReport r = new SignedGraphAnalyzer(unbalanced).analyze();
        assertFalse(r.stronglyBalanced);
        assertFalse(r.weaklyBalanced);
        assertEquals(1, r.frustrationIndex);
    }

    @Test
    public void testReportToString() {
        String report = new SignedGraphAnalyzer(largeBalanced).analyze().toString();
        assertTrue(report.contains("Signed Graph Analysis Report"));
        assertTrue(report.contains("Strongly balanced: true"));
        assertTrue(report.contains("Coalitions: 2"));
    }

    @Test
    public void testTriangleCensusToString() {
        String s = new SignedGraphAnalyzer(allPositive).triangleCensus().toString();
        assertTrue(s.contains("+++"));
        assertTrue(s.contains("strongBalance"));
    }

    // ---- Edge Cases ----

    @Test
    public void testSingleEdgePositive() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B");
        g.addEdge(makeEdge("A","B",1), "A", "B");
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(g);
        assertTrue(a.isStronglyBalanced());
        assertEquals(0, a.frustrationIndex());
        assertEquals(1, a.countPositiveEdges());
    }

    @Test
    public void testSingleEdgeNegative() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B");
        g.addEdge(makeEdge("A","B",-1), "A", "B");
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(g);
        assertTrue(a.isStronglyBalanced());
        assertEquals(0, a.frustrationIndex());
        assertEquals(1, a.countNegativeEdges());
    }

    @Test
    public void testDisconnectedComponents() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C"); g.addVertex("D");
        g.addEdge(makeEdge("A","B",1), "A", "B");
        g.addEdge(makeEdge("C","D",-1), "C", "D");
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(g);
        assertTrue(a.isStronglyBalanced());
        assertEquals(1, a.countPositiveEdges());
        assertEquals(1, a.countNegativeEdges());
    }

    @Test
    public void testSquareBalanced() {
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(square);
        assertTrue(a.isStronglyBalanced());
        assertEquals(0, a.frustrationIndex());
        List<Set<String>> c = a.findCoalitions();
        assertEquals(2, c.size());
    }

    @Test
    public void testWeaklyBalancedStrongPart() {
        // Triangles with +--: both strongly and weakly balanced
        SignedGraphAnalyzer a = new SignedGraphAnalyzer(balanced);
        assertTrue(a.isStronglyBalanced());
        assertTrue(a.isWeaklyBalanced());
    }
}
