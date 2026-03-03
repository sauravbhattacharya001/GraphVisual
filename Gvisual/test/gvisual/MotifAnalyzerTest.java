package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link MotifAnalyzer}.
 */
public class MotifAnalyzerTest {

    private Graph<String, edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, edge>();
    }

    // --- Helpers ---

    private edge addEdge(String v1, String v2) {
        edge e = new edge("f", v1, v2);
        e.setWeight(1.0f);
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        graph.addEdge(e, v1, v2);
        return e;
    }

    private void makeTriangle(String a, String b, String c) {
        addEdge(a, b);
        addEdge(b, c);
        addEdge(a, c);
    }

    private void makeSquare(String a, String b, String c, String d) {
        addEdge(a, b);
        addEdge(b, c);
        addEdge(c, d);
        addEdge(d, a);
    }

    private void makeStar(String hub, String... leaves) {
        for (String leaf : leaves) {
            addEdge(hub, leaf);
        }
    }

    // =======================================
    // 1. Constructor
    // =======================================

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNullGraph() {
        new MotifAnalyzer(null);
    }

    @Test(expected = IllegalStateException.class)
    public void testAccessBeforeCompute() {
        new MotifAnalyzer(graph).getTriangleCount();
    }

    // =======================================
    // 2. Empty and Trivial Graphs
    // =======================================

    @Test
    public void testEmptyGraph() {
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertEquals(0, ma.getTriangleCount());
        assertEquals(0, ma.getSquareCount());
        assertEquals(0, ma.getWedgeCount());
        assertEquals(0, ma.getStar3Count());
    }

    @Test
    public void testSingleVertex() {
        graph.addVertex("A");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertEquals(0, ma.getTriangleCount());
        assertEquals(0, ma.getWedgeCount());
    }

    @Test
    public void testSingleEdge() {
        addEdge("A", "B");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertEquals(0, ma.getTriangleCount());
        assertEquals(0, ma.getWedgeCount());
        assertEquals(0, ma.getStar3Count());
    }

    // =======================================
    // 3. Triangle Detection
    // =======================================

    @Test
    public void testSingleTriangle() {
        makeTriangle("A", "B", "C");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertEquals(1, ma.getTriangleCount());
        assertEquals(3, ma.getWedgeCount());
        assertEquals(0, ma.getPathCount());
    }

    @Test
    public void testTwoDisjointTriangles() {
        makeTriangle("A", "B", "C");
        makeTriangle("D", "E", "F");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertEquals(2, ma.getTriangleCount());
    }

    @Test
    public void testTriangleSharingEdge() {
        // A-B-C and A-B-D
        makeTriangle("A", "B", "C");
        addEdge("A", "D");
        addEdge("B", "D");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertEquals(2, ma.getTriangleCount());
    }

    @Test
    public void testCompleteK4HasFourTriangles() {
        // K4 has C(4,3) = 4 triangles
        addEdge("A", "B");
        addEdge("A", "C");
        addEdge("A", "D");
        addEdge("B", "C");
        addEdge("B", "D");
        addEdge("C", "D");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertEquals(4, ma.getTriangleCount());
    }

    @Test
    public void testCompleteK5HasTenTriangles() {
        String[] verts = {"A", "B", "C", "D", "E"};
        for (int i = 0; i < verts.length; i++) {
            for (int j = i + 1; j < verts.length; j++) {
                addEdge(verts[i], verts[j]);
            }
        }
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertEquals(10, ma.getTriangleCount());
    }

    @Test
    public void testTriangleDensityComplete() {
        makeTriangle("A", "B", "C");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertEquals(1.0, ma.getTriangleDensity(), 0.001);
    }

    @Test
    public void testTriangleDensityNone() {
        addEdge("A", "B");
        addEdge("B", "C");
        // No triangle
        graph.addVertex("D");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertEquals(0.0, ma.getTriangleDensity(), 0.001);
    }

    // =======================================
    // 4. Wedge / Path Detection
    // =======================================

    @Test
    public void testOpenPath() {
        addEdge("A", "B");
        addEdge("B", "C");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertEquals(0, ma.getTriangleCount());
        assertEquals(1, ma.getWedgeCount());
        assertEquals(1, ma.getPathCount());
    }

    @Test
    public void testStarOf3HasWedges() {
        makeStar("H", "A", "B", "C");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        // C(3,2) = 3 wedges centered on H
        assertEquals(3, ma.getWedgeCount());
        assertEquals(0, ma.getTriangleCount());
    }

    // =======================================
    // 5. Square (4-cycle) Detection
    // =======================================

    @Test
    public void testSingleSquare() {
        makeSquare("A", "B", "C", "D");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertEquals(1, ma.getSquareCount());
        assertEquals(0, ma.getTriangleCount());
    }

    @Test
    public void testSquareWithDiagonal() {
        // A-B-C-D plus diagonal A-C → two triangles, no pure square
        makeSquare("A", "B", "C", "D");
        addEdge("A", "C");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertEquals(2, ma.getTriangleCount());
        // Square count should still detect cycles
    }

    @Test
    public void testNoSquareInTriangle() {
        makeTriangle("A", "B", "C");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertEquals(0, ma.getSquareCount());
    }

    // =======================================
    // 6. Star Detection
    // =======================================

    @Test
    public void testStar3() {
        makeStar("H", "A", "B", "C");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertEquals(1, ma.getStar3Count());
        assertEquals(0, ma.getStar4Count());
        assertEquals(0, ma.getStar5Count());
    }

    @Test
    public void testStar4() {
        makeStar("H", "A", "B", "C", "D");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertEquals(4, ma.getStar3Count()); // C(4,3) = 4
        assertEquals(1, ma.getStar4Count()); // C(4,4) = 1
    }

    @Test
    public void testStar5() {
        makeStar("H", "A", "B", "C", "D", "E");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertEquals(10, ma.getStar3Count()); // C(5,3)
        assertEquals(5, ma.getStar4Count());  // C(5,4)
        assertEquals(1, ma.getStar5Count());  // C(5,5)
    }

    @Test
    public void testStarParticipation() {
        makeStar("H", "A", "B", "C");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        Map<String, Integer> hp = ma.getParticipation("H");
        assertNotNull(hp);
        assertEquals(1, (int) hp.get("star3"));
        // Leaves should not be star hubs
        Map<String, Integer> ap = ma.getParticipation("A");
        assertNotNull(ap);
        assertEquals(0, (int) ap.get("star3"));
    }

    // =======================================
    // 7. Clustering Coefficient
    // =======================================

    @Test
    public void testClusteringCoefficientComplete() {
        makeTriangle("A", "B", "C");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertEquals(1.0, ma.getClusteringCoefficient(), 0.001);
    }

    @Test
    public void testClusteringCoefficientStar() {
        makeStar("H", "A", "B", "C");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertEquals(0.0, ma.getClusteringCoefficient(), 0.001);
    }

    @Test
    public void testTransitivityEqualsClusteringForSimple() {
        makeTriangle("A", "B", "C");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertEquals(ma.getClusteringCoefficient(), ma.getTransitivity(), 0.001);
    }

    // =======================================
    // 8. Motif Fingerprint
    // =======================================

    @Test
    public void testFingerprintSumsToOne() {
        makeTriangle("A", "B", "C");
        addEdge("C", "D");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        Map<String, Double> fp = ma.getMotifFingerprint();
        double sum = 0;
        for (double v : fp.values()) sum += v;
        assertEquals(1.0, sum, 0.001);
    }

    @Test
    public void testFingerprintKeys() {
        makeTriangle("A", "B", "C");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        Map<String, Double> fp = ma.getMotifFingerprint();
        assertTrue(fp.containsKey("triangle"));
        assertTrue(fp.containsKey("square"));
        assertTrue(fp.containsKey("star3"));
        assertTrue(fp.containsKey("star4"));
        assertTrue(fp.containsKey("star5"));
        assertTrue(fp.containsKey("wedge"));
    }

    @Test
    public void testFingerprintAllTriangles() {
        makeTriangle("A", "B", "C");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        Map<String, Double> fp = ma.getMotifFingerprint();
        // Only triangles and wedges exist, both contribute
        assertTrue(fp.get("triangle") > 0);
        assertTrue(fp.get("wedge") > 0);
        assertEquals(0.0, fp.get("square"), 0.001);
    }

    // =======================================
    // 9. Top Participants
    // =======================================

    @Test
    public void testTopParticipantsTriangle() {
        // Hub node in two triangles
        makeTriangle("A", "B", "C");
        makeTriangle("A", "D", "E");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        List<Map.Entry<String, Integer>> top = ma.getTopParticipants("triangle", 1);
        assertFalse(top.isEmpty());
        // A participates in 2 triangles
        assertEquals("A", top.get(0).getKey());
        assertEquals(2, (int) top.get(0).getValue());
    }

    @Test
    public void testTopParticipantsLimit() {
        addEdge("A", "B");
        addEdge("A", "C");
        addEdge("A", "D");
        addEdge("A", "E");
        addEdge("B", "C");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        List<Map.Entry<String, Integer>> top = ma.getTopParticipants("wedge", 2);
        assertTrue(top.size() <= 2);
    }

    // =======================================
    // 10. Vertex Participation
    // =======================================

    @Test
    public void testParticipationNonexistentVertex() {
        graph.addVertex("A");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertNull(ma.getParticipation("Z"));
    }

    @Test
    public void testParticipationUnmodifiable() {
        makeTriangle("A", "B", "C");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        Map<String, Integer> p = ma.getParticipation("A");
        try {
            p.put("triangle", 999);
            fail("Should be unmodifiable");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    // =======================================
    // 11. Idempotency
    // =======================================

    @Test
    public void testComputeIdempotent() {
        makeTriangle("A", "B", "C");
        MotifAnalyzer ma = new MotifAnalyzer(graph);
        ma.compute();
        int t1 = ma.getTriangleCount();
        ma.compute(); // second call
        int t2 = ma.getTriangleCount();
        assertEquals(t1, t2);
    }

    // =======================================
    // 12. Summary
    // =======================================

    @Test
    public void testSummaryContainsMotifTypes() {
        makeTriangle("A", "B", "C");
        makeStar("H", "X", "Y", "Z");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        String summary = ma.getSummary();
        assertTrue(summary.contains("Triangles"));
        assertTrue(summary.contains("Squares"));
        assertTrue(summary.contains("3-Stars"));
        assertTrue(summary.contains("Clustering"));
        assertTrue(summary.contains("Fingerprint"));
    }

    @Test
    public void testSummaryNotEmpty() {
        graph.addVertex("A");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        String summary = ma.getSummary();
        assertFalse(summary.isEmpty());
    }

    // =======================================
    // 13. Complex Graphs
    // =======================================

    @Test
    public void testPetersenGraphTriangles() {
        // Petersen graph has no triangles (girth = 5)
        // Build outer 5-cycle
        addEdge("O0", "O1"); addEdge("O1", "O2"); addEdge("O2", "O3");
        addEdge("O3", "O4"); addEdge("O4", "O0");
        // Build inner pentagram
        addEdge("I0", "I2"); addEdge("I2", "I4"); addEdge("I4", "I1");
        addEdge("I1", "I3"); addEdge("I3", "I0");
        // Connect outer to inner
        addEdge("O0", "I0"); addEdge("O1", "I1"); addEdge("O2", "I2");
        addEdge("O3", "I3"); addEdge("O4", "I4");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertEquals(0, ma.getTriangleCount());
        assertEquals(0.0, ma.getClusteringCoefficient(), 0.001);
    }

    @Test
    public void testLinearChainNoTriangles() {
        // A-B-C-D-E
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "E");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertEquals(0, ma.getTriangleCount());
        assertEquals(3, ma.getWedgeCount()); // B-centered, C-centered, D-centered
        assertEquals(0, ma.getSquareCount());
    }

    @Test
    public void testCycle6() {
        // 6-cycle has no triangles and no 4-cycles (girth = 6)
        addEdge("A", "B"); addEdge("B", "C"); addEdge("C", "D");
        addEdge("D", "E"); addEdge("E", "F"); addEdge("F", "A");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertEquals(0, ma.getTriangleCount());
        assertEquals(0, ma.getSquareCount());
        assertEquals(6, ma.getWedgeCount());
    }

    @Test
    public void testDisconnectedComponents() {
        makeTriangle("A", "B", "C");
        makeStar("H", "X", "Y", "Z");
        // No edges between components
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertEquals(1, ma.getTriangleCount());
        assertEquals(1, ma.getStar3Count());
    }

    // =======================================
    // 14. Edge Cases
    // =======================================

    @Test
    public void testIsolatedVertices() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        assertEquals(0, ma.getTriangleCount());
        assertEquals(0, ma.getWedgeCount());
        assertEquals(0.0, ma.getClusteringCoefficient(), 0.001);
    }

    @Test
    public void testFingerprintEmptyGraph() {
        graph.addVertex("A");
        MotifAnalyzer ma = new MotifAnalyzer(graph).compute();
        Map<String, Double> fp = ma.getMotifFingerprint();
        // All zeros is fine — with no motifs, each entry is 0/1 = 0
        for (double v : fp.values()) {
            assertEquals(0.0, v, 0.001);
        }
    }

    @Test
    public void testComputeReturnsSelf() {
        MotifAnalyzer ma = new MotifAnalyzer(graph);
        assertSame(ma, ma.compute());
    }
}

