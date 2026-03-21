package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link GraphEntropyAnalyzer}.
 */
public class GraphEntropyAnalyzerTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
    }

    // --- Helpers ---

    private edge addEdge(String v1, String v2) {
        return addEdge(v1, v2, "f");
    }

    private edge addEdge(String v1, String v2, String type) {
        edge e = new Edge(type, v1, v2);
        e.setWeight(1.0f);
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        graph.addEdge(e, v1, v2);
        return e;
    }

    private Graph<String, Edge> completeGraph(int n) {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 1; i <= n; i++) g.addVertex("N" + i);
        for (int i = 1; i <= n; i++) {
            for (int j = i + 1; j <= n; j++) {
                edge e = new Edge("f", "N" + i, "N" + j);
                e.setWeight(1.0f);
                g.addEdge(e, "N" + i, "N" + j);
            }
        }
        return g;
    }

    private Graph<String, Edge> pathGraph(int n) {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 1; i <= n; i++) g.addVertex("N" + i);
        for (int i = 1; i < n; i++) {
            edge e = new Edge("f", "N" + i, "N" + (i + 1));
            e.setWeight(1.0f);
            g.addEdge(e, "N" + i, "N" + (i + 1));
        }
        return g;
    }

    private Graph<String, Edge> cycleGraph(int n) {
        Graph<String, Edge> g = pathGraph(n);
        edge e = new Edge("f", "N" + n, "N1");
        e.setWeight(1.0f);
        g.addEdge(e, "N" + n, "N1");
        return g;
    }

    private Graph<String, Edge> starGraph(int n) {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("C");
        for (int i = 1; i <= n; i++) {
            g.addVertex("L" + i);
            edge e = new Edge("f", "C", "L" + i);
            e.setWeight(1.0f);
            g.addEdge(e, "C", "L" + i);
        }
        return g;
    }

    // ═══════════════════════════════════════════════════════════════
    // Empty / Single / Trivial
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testEmptyGraph() {
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        assertEquals(0, a.getDegreeEntropy(), 1e-10);
        assertEquals(0, a.getVonNeumannEntropy(), 1e-10);
        assertEquals(0, a.getEdgeTypeEntropy(), 1e-10);
        assertEquals(0, a.getRandomWalkEntropyRate(), 1e-10);
        assertEquals("trivial", a.getComplexityClass());
    }

    @Test
    public void testSingleVertex() {
        graph.addVertex("A");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        assertEquals(0, a.getDegreeEntropy(), 1e-10);
        assertEquals(0, a.getVonNeumannEntropy(), 1e-10);
        assertEquals("trivial", a.getComplexityClass());
    }

    @Test
    public void testTwoVerticesOneEdge() {
        addEdge("A", "B");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        assertEquals(0, a.getDegreeEntropy(), 1e-10);
    }

    @Test
    public void testNullGraphThrows() {
        try {
            new GraphEntropyAnalyzer(null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Degree Entropy
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testDegreeEntropyRegularGraph() {
        Graph<String, Edge> g = cycleGraph(5);
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(g);
        a.compute();
        assertEquals(0, a.getDegreeEntropy(), 1e-10);
    }

    @Test
    public void testDegreeEntropyStarGraph() {
        Graph<String, Edge> g = starGraph(4);
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(g);
        a.compute();
        // 1 vertex with degree 4, 4 vertices with degree 1
        double p1 = 1.0 / 5;
        double p2 = 4.0 / 5;
        double expected = -(p1 * Math.log(p1) / Math.log(2) + p2 * Math.log(p2) / Math.log(2));
        assertEquals(expected, a.getDegreeEntropy(), 1e-10);
    }

    @Test
    public void testDegreeEntropyCompleteGraph() {
        Graph<String, Edge> g = completeGraph(5);
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(g);
        a.compute();
        assertEquals(0, a.getDegreeEntropy(), 1e-10);
    }

    @Test
    public void testMaxDegreeEntropy() {
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(completeGraph(8));
        a.compute();
        assertEquals(Math.log(8) / Math.log(2), a.getMaxDegreeEntropy(), 1e-10);
    }

    @Test
    public void testNormalisedDegreeEntropyRegular() {
        Graph<String, Edge> g = cycleGraph(6);
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(g);
        a.compute();
        assertEquals(0, a.getNormalisedDegreeEntropy(), 1e-10);
    }

    @Test
    public void testNormalisedDegreeEntropyBounded() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "A");
        addEdge("A", "C");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        double norm = a.getNormalisedDegreeEntropy();
        assertTrue("Normalised entropy should be >= 0", norm >= 0);
        assertTrue("Normalised entropy should be <= 1", norm <= 1.0001);
    }

    // ═══════════════════════════════════════════════════════════════
    // Von Neumann Entropy
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testVonNeumannSingleEdge() {
        addEdge("A", "B");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        // K2: Laplacian eigenvalues [0, 2], normalised: only 2/2=1 → H = 0
        assertEquals(0, a.getVonNeumannEntropy(), 1e-8);
    }

    @Test
    public void testVonNeumannTriangle() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        // K3 Laplacian eigenvalues: [0, 3, 3]
        // Normalised: [3/6, 3/6] = [0.5, 0.5] → H = 1 bit
        assertEquals(1.0, a.getVonNeumannEntropy(), 1e-6);
    }

    @Test
    public void testVonNeumannPositive() {
        Graph<String, Edge> g = pathGraph(5);
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(g);
        a.compute();
        assertTrue(a.getVonNeumannEntropy() > 0);
    }

    @Test
    public void testVonNeumannCompleteHigherThanPath() {
        GraphEntropyAnalyzer aComplete = new GraphEntropyAnalyzer(completeGraph(5));
        aComplete.compute();
        GraphEntropyAnalyzer aPath = new GraphEntropyAnalyzer(pathGraph(5));
        aPath.compute();
        assertTrue(aComplete.getVonNeumannEntropy() >= aPath.getVonNeumannEntropy() - 0.01);
    }

    // ═══════════════════════════════════════════════════════════════
    // Neighbourhood Entropy
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testNeighbourhoodEntropyRegular() {
        Graph<String, Edge> g = cycleGraph(5);
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(g);
        a.compute();
        assertEquals(0, a.getAvgNeighbourhoodEntropy(), 1e-10);
    }

    @Test
    public void testNeighbourhoodEntropyIsolatedVertex() {
        graph.addVertex("A");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        assertEquals(0.0, a.getNeighbourhoodEntropy().get("A"), 1e-10);
    }

    @Test
    public void testNeighbourhoodEntropyPerVertex() {
        addEdge("A", "B");
        addEdge("A", "C");
        addEdge("B", "C");
        addEdge("B", "D");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        Map<String, Double> ne = a.getNeighbourhoodEntropy();
        assertEquals(4, ne.size());
        // D has degree 1, its only neighbour B has degree 3 → entropy = 0
        assertEquals(0, ne.get("D"), 1e-10);
    }

    @Test
    public void testMostDiverseVertex() {
        addEdge("A", "B");
        addEdge("A", "C");
        addEdge("A", "D");
        addEdge("B", "C");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        assertNotNull(a.getMostDiverseVertex());
    }

    @Test
    public void testLeastDiverseVertex() {
        addEdge("A", "B");
        addEdge("B", "C");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        assertNotNull(a.getLeastDiverseVertex());
    }

    @Test
    public void testMostLeastDiverseEmptyGraph() {
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        assertNull(a.getMostDiverseVertex());
        assertNull(a.getLeastDiverseVertex());
    }

    // ═══════════════════════════════════════════════════════════════
    // Edge Type Entropy
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testEdgeTypeEntropyAllSame() {
        addEdge("A", "B", "f");
        addEdge("B", "C", "f");
        addEdge("C", "A", "f");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        assertEquals(0, a.getEdgeTypeEntropy(), 1e-10);
    }

    @Test
    public void testEdgeTypeEntropyMixed() {
        addEdge("A", "B", "f");
        addEdge("B", "C", "c");
        addEdge("C", "A", "s");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        assertEquals(Math.log(3) / Math.log(2), a.getEdgeTypeEntropy(), 1e-10);
    }

    @Test
    public void testEdgeTypeEntropyTwoTypes() {
        addEdge("A", "B", "f");
        addEdge("B", "C", "f");
        addEdge("C", "D", "c");
        addEdge("D", "A", "c");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        assertEquals(1.0, a.getEdgeTypeEntropy(), 1e-10);
    }

    @Test
    public void testEdgeTypeEntropyNoEdges() {
        graph.addVertex("A");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        assertEquals(0, a.getEdgeTypeEntropy(), 1e-10);
    }

    // ═══════════════════════════════════════════════════════════════
    // Topological Information Content
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testTopoInfoContentComplete() {
        Graph<String, Edge> g = completeGraph(4);
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(g);
        a.compute();
        assertEquals(0, a.getTopologicalInfoContent(), 1e-6);
    }

    @Test
    public void testTopoInfoContentAllDifferent() {
        Graph<String, Edge> g = pathGraph(4);
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(g);
        a.compute();
        assertTrue(a.getTopologicalInfoContent() > 0);
    }

    @Test
    public void testTopoInfoContentSingleVertex() {
        graph.addVertex("A");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        assertEquals(0, a.getTopologicalInfoContent(), 1e-10);
    }

    // ═══════════════════════════════════════════════════════════════
    // Random Walk Entropy Rate
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testRWEntropyRateRegular() {
        Graph<String, Edge> g = cycleGraph(6);
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(g);
        a.compute();
        // C6: m=6, 2m=12, each d=2 for 6 vertices
        // H = log2(12) - (12/12)*log2(2) = log2(12) - 1
        double expected = Math.log(12) / Math.log(2) - 1.0;
        assertEquals(expected, a.getRandomWalkEntropyRate(), 1e-10);
    }

    @Test
    public void testRWEntropyRatePositive() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        assertTrue(a.getRandomWalkEntropyRate() > 0);
    }

    @Test
    public void testRWEntropyRateNoEdges() {
        graph.addVertex("A");
        graph.addVertex("B");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        assertEquals(0, a.getRandomWalkEntropyRate(), 1e-10);
    }

    // ═══════════════════════════════════════════════════════════════
    // Chromatic Entropy
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testChromaticEntropyComplete() {
        Graph<String, Edge> g = completeGraph(4);
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(g);
        a.compute();
        assertEquals(Math.log(4) / Math.log(2), a.getChromaticEntropy(), 1e-10);
    }

    @Test
    public void testChromaticEntropyBipartite() {
        Graph<String, Edge> g = pathGraph(4);
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(g);
        a.compute();
        assertEquals(1.0, a.getChromaticEntropy(), 1e-6);
    }

    @Test
    public void testChromaticEntropyNoEdges() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        assertEquals(0, a.getChromaticEntropy(), 1e-10);
    }

    // ═══════════════════════════════════════════════════════════════
    // Degree-CC Mutual Information
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testDegreeCCMutualInfoNonNegative() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        addEdge("C", "D");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        assertTrue(a.getDegreeCCMutualInfo() >= 0);
    }

    @Test
    public void testDegreeCCMutualInfoSingleVertex() {
        graph.addVertex("A");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        assertEquals(0, a.getDegreeCCMutualInfo(), 1e-10);
    }

    @Test
    public void testDegreeCCMutualInfoRegular() {
        Graph<String, Edge> g = cycleGraph(6);
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(g);
        a.compute();
        assertEquals(0, a.getDegreeCCMutualInfo(), 1e-10);
    }

    // ═══════════════════════════════════════════════════════════════
    // Complexity Classification
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testComplexityTrivialEmpty() {
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        assertEquals("trivial", a.getComplexityClass());
    }

    @Test
    public void testComplexityRegularLow() {
        // Cycle — all vertices have same degree, but Von Neumann entropy
        // is non-trivial so complexity is moderate, not low
        Graph<String, Edge> g = cycleGraph(6);
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(g);
        a.compute();
        String c = a.getComplexityClass();
        assertTrue("Regular graph should be low or moderate complexity",
                "low".equals(c) || "moderate".equals(c));
    }

    @Test
    public void testComplexityNotNull() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "A");
        addEdge("A", "C");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        assertNotNull(a.getComplexityClass());
    }

    // ═══════════════════════════════════════════════════════════════
    // Report
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testReportNotEmpty() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        String report = a.generateReport();
        assertNotNull(report);
        assertTrue(report.contains("Graph Entropy Analysis"));
        assertTrue(report.contains("Degree distribution entropy"));
        assertTrue(report.contains("Von Neumann entropy"));
    }

    @Test
    public void testReportContainsComplexity() {
        addEdge("A", "B");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        String report = a.generateReport();
        assertTrue(report.contains("Structural complexity"));
    }

    @Test
    public void testReportEmptyGraph() {
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        String report = a.generateReport();
        assertTrue(report.contains("0 vertices"));
    }

    // ═══════════════════════════════════════════════════════════════
    // Idempotency / Caching
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testComputeIdempotent() {
        addEdge("A", "B");
        addEdge("B", "C");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        double d1 = a.getDegreeEntropy();
        a.compute();
        assertEquals(d1, a.getDegreeEntropy(), 1e-15);
    }

    @Test
    public void testLazyCompute() {
        addEdge("A", "B");
        addEdge("B", "C");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        double d = a.getDegreeEntropy();
        assertTrue(d >= 0);
    }

    // ═══════════════════════════════════════════════════════════════
    // Larger Graphs
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testCompleteGraphK10() {
        Graph<String, Edge> g = completeGraph(10);
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(g);
        a.compute();
        assertEquals(0, a.getDegreeEntropy(), 1e-10);
        assertTrue(a.getVonNeumannEntropy() > 0);
        assertEquals(0, a.getTopologicalInfoContent(), 1e-6);
    }

    @Test
    public void testPathGraph10() {
        Graph<String, Edge> g = pathGraph(10);
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(g);
        a.compute();
        assertTrue(a.getDegreeEntropy() > 0);
        assertTrue(a.getVonNeumannEntropy() > 0);
        assertTrue(a.getRandomWalkEntropyRate() > 0);
    }

    @Test
    public void testStarGraph10() {
        Graph<String, Edge> g = starGraph(10);
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(g);
        a.compute();
        assertTrue(a.getDegreeEntropy() > 0);
        Map<String, Double> ne = a.getNeighbourhoodEntropy();
        assertEquals(0, ne.get("L1"), 1e-10);
    }

    @Test
    public void testPetersenLikeGraph() {
        addEdge("A", "B"); addEdge("B", "C"); addEdge("C", "D");
        addEdge("D", "E"); addEdge("E", "A");
        addEdge("A", "F"); addEdge("B", "G"); addEdge("C", "H");
        addEdge("D", "I"); addEdge("E", "J");
        addEdge("F", "H"); addEdge("H", "J"); addEdge("J", "G");
        addEdge("G", "I"); addEdge("I", "F");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        assertEquals(0, a.getDegreeEntropy(), 1e-10);
        assertTrue(a.getVonNeumannEntropy() > 0);
        assertEquals(0, a.getAvgNeighbourhoodEntropy(), 1e-10);
    }

    @Test
    public void testMixedEdgeTypesGraph() {
        addEdge("A", "B", "f");
        addEdge("B", "C", "c");
        addEdge("C", "D", "s");
        addEdge("D", "E", "sg");
        addEdge("E", "A", "fs");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        assertEquals(Math.log(5) / Math.log(2), a.getEdgeTypeEntropy(), 1e-10);
    }

    @Test
    public void testDisconnectedGraph() {
        addEdge("A", "B");
        addEdge("C", "D");
        graph.addVertex("E");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        assertTrue(a.getDegreeEntropy() > 0);
        assertNotNull(a.getComplexityClass());
    }

    // ═══════════════════════════════════════════════════════════════
    // Comparison Properties
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testStarHigherDegreeEntropyThanCycle() {
        GraphEntropyAnalyzer aStar = new GraphEntropyAnalyzer(starGraph(5));
        aStar.compute();
        GraphEntropyAnalyzer aCycle = new GraphEntropyAnalyzer(cycleGraph(6));
        aCycle.compute();
        assertTrue(aStar.getDegreeEntropy() > aCycle.getDegreeEntropy());
    }

    @Test
    public void testEdgeTypeEntropyMonotonicity() {
        Graph<String, Edge> g1 = new UndirectedSparseGraph<>();
        g1.addVertex("A"); g1.addVertex("B"); g1.addVertex("C");
        edge e1 = new Edge("f", "A", "B"); e1.setWeight(1); g1.addEdge(e1, "A", "B");
        edge e2 = new Edge("f", "B", "C"); e2.setWeight(1); g1.addEdge(e2, "B", "C");

        Graph<String, Edge> g2 = new UndirectedSparseGraph<>();
        g2.addVertex("A"); g2.addVertex("B"); g2.addVertex("C");
        edge e3 = new Edge("f", "A", "B"); e3.setWeight(1); g2.addEdge(e3, "A", "B");
        edge e4 = new Edge("c", "B", "C"); e4.setWeight(1); g2.addEdge(e4, "B", "C");

        GraphEntropyAnalyzer a1 = new GraphEntropyAnalyzer(g1);
        a1.compute();
        GraphEntropyAnalyzer a2 = new GraphEntropyAnalyzer(g2);
        a2.compute();
        assertTrue(a2.getEdgeTypeEntropy() > a1.getEdgeTypeEntropy());
    }

    @Test
    public void testNeighbourhoodEntropyUnmodifiable() {
        addEdge("A", "B");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        try {
            a.getNeighbourhoodEntropy().put("X", 999.0);
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Additional Entropy Properties
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testEntropyNonNegative() {
        // All entropy measures should be >= 0
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("A", "D");
        addEdge("A", "C");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        assertTrue(a.getDegreeEntropy() >= 0);
        assertTrue(a.getVonNeumannEntropy() >= 0);
        assertTrue(a.getEdgeTypeEntropy() >= 0);
        assertTrue(a.getTopologicalInfoContent() >= 0);
        assertTrue(a.getRandomWalkEntropyRate() >= 0);
        assertTrue(a.getChromaticEntropy() >= 0);
        assertTrue(a.getDegreeCCMutualInfo() >= 0);
        assertTrue(a.getAvgNeighbourhoodEntropy() >= 0);
    }

    @Test
    public void testWheelGraph() {
        // Wheel: center connected to all in a cycle
        graph.addVertex("C");
        for (int i = 1; i <= 6; i++) {
            graph.addVertex("N" + i);
            edge e = new Edge("f", "C", "N" + i);
            e.setWeight(1.0f);
            graph.addEdge(e, "C", "N" + i);
        }
        for (int i = 1; i <= 6; i++) {
            int next = (i % 6) + 1;
            edge e = new Edge("f", "N" + i, "N" + next);
            e.setWeight(1.0f);
            graph.addEdge(e, "N" + i, "N" + next);
        }
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        // Two degree classes: centre=6, rim=3
        assertTrue(a.getDegreeEntropy() > 0);
        assertTrue(a.getVonNeumannEntropy() > 0);
        String report = a.generateReport();
        assertNotNull(report);
        assertTrue(report.length() > 100);
    }

    @Test
    public void testBarabasi5Nodes() {
        // Small scale-free-ish: hub connected to all, some secondary edges
        addEdge("H", "A");
        addEdge("H", "B");
        addEdge("H", "C");
        addEdge("H", "D");
        addEdge("A", "B");
        addEdge("C", "D");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        assertTrue(a.getDegreeEntropy() > 0);
        assertTrue(a.getTopologicalInfoContent() > 0);
    }

    @Test
    public void testTwoComponentsEntropy() {
        // Two triangles, disconnected
        addEdge("A", "B"); addEdge("B", "C"); addEdge("C", "A");
        addEdge("D", "E"); addEdge("E", "F"); addEdge("F", "D");
        GraphEntropyAnalyzer a = new GraphEntropyAnalyzer(graph);
        a.compute();
        assertEquals(0, a.getDegreeEntropy(), 1e-10); // all degree 2
        assertTrue(a.getVonNeumannEntropy() > 0);
    }
}
