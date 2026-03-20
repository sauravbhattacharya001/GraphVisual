package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for GraphSimilarityAnalyzer.
 */
public class GraphSimilarityAnalyzerTest {

    private Graph<String, Edge> createTriangle() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addEdge(new Edge("friend", "A", "B"), "A", "B");
        g.addEdge(new Edge("friend", "B", "C"), "B", "C");
        g.addEdge(new Edge("friend", "A", "C"), "A", "C");
        return g;
    }

    private Graph<String, Edge> createPath() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addEdge(new Edge("friend", "A", "B"), "A", "B");
        g.addEdge(new Edge("friend", "B", "C"), "B", "C");
        return g;
    }

    private Graph<String, Edge> createStar5() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < 5; i++) g.addVertex("V" + i);
        for (int i = 1; i < 5; i++) {
            g.addEdge(new Edge("friend", "V0", "V" + i), "V0", "V" + i);
        }
        return g;
    }

    @Test
    public void testIdenticalGraphs() {
        Graph<String, Edge> g1 = createTriangle();
        Graph<String, Edge> g2 = createTriangle();

        GraphSimilarityAnalyzer sim = new GraphSimilarityAnalyzer(g1, g2);
        sim.compute();

        assertEquals(0.0, sim.getJensenShannonDivergence(), 1e-10);
        assertEquals(0.0, sim.getVonNeumannDivergence(), 1e-6);
        assertEquals(0.0, sim.getEntropyProfileDistance(), 1e-6);
        assertEquals(1.0, sim.getSimilarityScore(), 1e-6);
    }

    @Test
    public void testDifferentGraphs() {
        Graph<String, Edge> g1 = createTriangle();
        Graph<String, Edge> g2 = createStar5();

        GraphSimilarityAnalyzer sim = new GraphSimilarityAnalyzer(g1, g2);
        sim.compute();

        // Should have nonzero divergences
        assertTrue("JSD should be > 0", sim.getJensenShannonDivergence() > 0);
        assertTrue("Similarity should be < 1", sim.getSimilarityScore() < 1.0);
        assertTrue("Similarity should be > 0", sim.getSimilarityScore() > 0.0);
    }

    @Test
    public void testJSDBounded() {
        Graph<String, Edge> g1 = createTriangle();
        Graph<String, Edge> g2 = createPath();

        GraphSimilarityAnalyzer sim = new GraphSimilarityAnalyzer(g1, g2);
        sim.compute();

        assertTrue("JSD should be >= 0", sim.getJensenShannonDivergence() >= 0);
        assertTrue("JSD should be <= 1", sim.getJensenShannonDivergence() <= 1);
    }

    @Test
    public void testVonNeumannNonNegative() {
        Graph<String, Edge> g1 = createPath();
        Graph<String, Edge> g2 = createStar5();

        GraphSimilarityAnalyzer sim = new GraphSimilarityAnalyzer(g1, g2);
        sim.compute();

        assertTrue("VN divergence should be >= 0", sim.getVonNeumannDivergence() >= 0);
    }

    @Test
    public void testEntropyProfiles() {
        Graph<String, Edge> g1 = createTriangle();
        Graph<String, Edge> g2 = createStar5();

        GraphSimilarityAnalyzer sim = new GraphSimilarityAnalyzer(g1, g2);
        sim.compute();

        double[] p1 = sim.getProfile1();
        double[] p2 = sim.getProfile2();

        assertEquals(6, p1.length);
        assertEquals(6, p2.length);
    }

    @Test
    public void testEmptyGraphs() {
        Graph<String, Edge> g1 = new UndirectedSparseGraph<>();
        Graph<String, Edge> g2 = new UndirectedSparseGraph<>();

        GraphSimilarityAnalyzer sim = new GraphSimilarityAnalyzer(g1, g2);
        sim.compute();

        assertEquals(0.0, sim.getJensenShannonDivergence(), 1e-10);
        assertEquals(1.0, sim.getSimilarityScore(), 1e-6);
    }

    @Test
    public void testReport() {
        Graph<String, Edge> g1 = createTriangle();
        Graph<String, Edge> g2 = createStar5();

        GraphSimilarityAnalyzer sim = new GraphSimilarityAnalyzer(g1, g2);
        sim.compute();

        String report = sim.generateReport();
        assertTrue("Report should contain 'Similarity'", report.contains("Similarity"));
        assertTrue("Report should contain 'Jensen-Shannon'", report.contains("Jensen-Shannon"));
        assertTrue("Report should contain 'Von Neumann'", report.contains("Von Neumann"));
    }

    @Test(expected = NullPointerException.class)
    public void testNullGraph1() {
        new GraphSimilarityAnalyzer(null, createTriangle());
    }

    @Test(expected = NullPointerException.class)
    public void testNullGraph2() {
        new GraphSimilarityAnalyzer(createTriangle(), null);
    }

    @Test
    public void testSymmetry() {
        Graph<String, Edge> g1 = createTriangle();
        Graph<String, Edge> g2 = createStar5();

        GraphSimilarityAnalyzer sim12 = new GraphSimilarityAnalyzer(g1, g2);
        sim12.compute();
        GraphSimilarityAnalyzer sim21 = new GraphSimilarityAnalyzer(g2, g1);
        sim21.compute();

        assertEquals("JSD should be symmetric",
                sim12.getJensenShannonDivergence(),
                sim21.getJensenShannonDivergence(), 1e-10);
        assertEquals("Similarity should be symmetric",
                sim12.getSimilarityScore(),
                sim21.getSimilarityScore(), 1e-6);
    }

    // ── Additional tests ────────────────────────────────────────────

    @Test
    public void testSingleVertexGraphs() {
        Graph<String, Edge> g1 = new UndirectedSparseGraph<>();
        g1.addVertex("A");
        Graph<String, Edge> g2 = new UndirectedSparseGraph<>();
        g2.addVertex("X");

        GraphSimilarityAnalyzer sim = new GraphSimilarityAnalyzer(g1, g2);
        sim.compute();

        // Single vertices with no edges should be identical structurally
        assertEquals(0.0, sim.getJensenShannonDivergence(), 1e-10);
        assertEquals(1.0, sim.getSimilarityScore(), 1e-6);
    }

    @Test
    public void testSingleVertexVsEdge() {
        Graph<String, Edge> g1 = new UndirectedSparseGraph<>();
        g1.addVertex("A");

        Graph<String, Edge> g2 = new UndirectedSparseGraph<>();
        g2.addVertex("A"); g2.addVertex("B");
        g2.addEdge(new Edge("friend", "A", "B"), "A", "B");

        GraphSimilarityAnalyzer sim = new GraphSimilarityAnalyzer(g1, g2);
        sim.compute();

        assertTrue("Should detect structural difference",
                sim.getSimilarityScore() < 1.0);
    }

    @Test
    public void testDisconnectedGraph() {
        // Two isolated components vs a connected graph
        Graph<String, Edge> disconnected = new UndirectedSparseGraph<>();
        disconnected.addVertex("A"); disconnected.addVertex("B");
        disconnected.addVertex("C"); disconnected.addVertex("D");
        disconnected.addEdge(new Edge("friend", "A", "B"), "A", "B");
        disconnected.addEdge(new Edge("friend", "C", "D"), "C", "D");

        Graph<String, Edge> connected = new UndirectedSparseGraph<>();
        connected.addVertex("A"); connected.addVertex("B");
        connected.addVertex("C"); connected.addVertex("D");
        connected.addEdge(new Edge("friend", "A", "B"), "A", "B");
        connected.addEdge(new Edge("friend", "B", "C"), "B", "C");
        connected.addEdge(new Edge("friend", "C", "D"), "C", "D");

        GraphSimilarityAnalyzer sim = new GraphSimilarityAnalyzer(disconnected, connected);
        sim.compute();

        assertTrue("Disconnected vs connected should differ",
                sim.getSimilarityScore() < 1.0);
        assertTrue("Similarity should be > 0", sim.getSimilarityScore() > 0.0);
    }

    @Test
    public void testDifferentSizedGraphs() {
        // Tests spectral padding: triangle (3 vertices) vs star5 (5 vertices)
        Graph<String, Edge> small = createTriangle();
        Graph<String, Edge> large = createStar5();

        GraphSimilarityAnalyzer sim = new GraphSimilarityAnalyzer(small, large);
        sim.compute();

        // All measures should be valid despite size mismatch
        assertTrue(sim.getJensenShannonDivergence() >= 0);
        assertTrue(sim.getJensenShannonDivergence() <= 1);
        assertTrue(sim.getVonNeumannDivergence() >= 0);
        assertTrue(sim.getEntropyProfileDistance() >= 0);
        assertTrue(sim.getSimilarityScore() >= 0);
        assertTrue(sim.getSimilarityScore() <= 1);
    }

    @Test
    public void testLazyCompute() {
        // Calling getters should trigger compute() automatically
        Graph<String, Edge> g1 = createTriangle();
        Graph<String, Edge> g2 = createPath();

        GraphSimilarityAnalyzer sim = new GraphSimilarityAnalyzer(g1, g2);
        // DO NOT call compute() — ensureComputed should handle it
        double score = sim.getSimilarityScore();
        assertTrue("Lazy compute should produce valid score", score >= 0 && score <= 1);

        double jsd = sim.getJensenShannonDivergence();
        assertTrue("JSD should be computed lazily", jsd >= 0 && jsd <= 1);
    }

    @Test
    public void testComputeIdempotency() {
        Graph<String, Edge> g1 = createTriangle();
        Graph<String, Edge> g2 = createStar5();

        GraphSimilarityAnalyzer sim = new GraphSimilarityAnalyzer(g1, g2);
        sim.compute();
        double score1 = sim.getSimilarityScore();
        double jsd1 = sim.getJensenShannonDivergence();

        // Second compute() should be a no-op (early return)
        sim.compute();
        assertEquals("Score should not change on recompute", score1, sim.getSimilarityScore(), 0);
        assertEquals("JSD should not change on recompute", jsd1, sim.getJensenShannonDivergence(), 0);
    }

    @Test
    public void testProfilesCloned() {
        // getProfile1/getProfile2 should return defensive copies
        Graph<String, Edge> g1 = createTriangle();
        Graph<String, Edge> g2 = createStar5();

        GraphSimilarityAnalyzer sim = new GraphSimilarityAnalyzer(g1, g2);
        sim.compute();

        double[] p1a = sim.getProfile1();
        double[] p1b = sim.getProfile1();

        assertNotSame("Profiles should be different array instances", p1a, p1b);
        assertArrayEquals("Profile values should be identical", p1a, p1b, 1e-15);

        // Mutating the returned array should not affect the analyzer
        p1a[0] = -999.0;
        double[] p1c = sim.getProfile1();
        assertNotEquals(-999.0, p1c[0], 1e-10);
    }

    @Test
    public void testSimilarityScoreBounds() {
        // Test across multiple graph pairs that similarity is always in [0, 1]
        Graph<String, Edge>[] graphs = new Graph[]{
                createTriangle(), createPath(), createStar5(),
                new UndirectedSparseGraph<>()
        };
        // Add a vertex to the empty graph so it's not truly empty
        graphs[3].addVertex("lone");

        for (int i = 0; i < graphs.length; i++) {
            for (int j = 0; j < graphs.length; j++) {
                GraphSimilarityAnalyzer sim =
                        new GraphSimilarityAnalyzer(graphs[i], graphs[j]);
                sim.compute();
                assertTrue("Similarity must be >= 0 for pair (" + i + "," + j + ")",
                        sim.getSimilarityScore() >= 0);
                assertTrue("Similarity must be <= 1 for pair (" + i + "," + j + ")",
                        sim.getSimilarityScore() <= 1.0 + 1e-10);
            }
        }
    }

    @Test
    public void testEntropyProfileDistanceTriangleInequality() {
        // For a metric: d(A,C) <= d(A,B) + d(B,C)
        Graph<String, Edge> a = createTriangle();
        Graph<String, Edge> b = createPath();
        Graph<String, Edge> c = createStar5();

        GraphSimilarityAnalyzer ab = new GraphSimilarityAnalyzer(a, b);
        ab.compute();
        GraphSimilarityAnalyzer bc = new GraphSimilarityAnalyzer(b, c);
        bc.compute();
        GraphSimilarityAnalyzer ac = new GraphSimilarityAnalyzer(a, c);
        ac.compute();

        assertTrue("Entropy profile distance should satisfy triangle inequality",
                ac.getEntropyProfileDistance() <=
                        ab.getEntropyProfileDistance() + bc.getEntropyProfileDistance() + 1e-10);
    }

    @Test
    public void testReportContainsGraphSizes() {
        Graph<String, Edge> g1 = createTriangle();
        Graph<String, Edge> g2 = createStar5();

        GraphSimilarityAnalyzer sim = new GraphSimilarityAnalyzer(g1, g2);
        sim.compute();

        String report = sim.generateReport();
        assertTrue("Report should contain 'Graph 1: 3 vertices'",
                report.contains("Graph 1: 3 vertices"));
        assertTrue("Report should contain 'Graph 2: 5 vertices'",
                report.contains("Graph 2: 5 vertices"));
    }

    @Test
    public void testReportContainsInterpretation() {
        Graph<String, Edge> g1 = createTriangle();
        Graph<String, Edge> g2 = createTriangle();

        GraphSimilarityAnalyzer sim = new GraphSimilarityAnalyzer(g1, g2);
        sim.compute();

        String report = sim.generateReport();
        // Identical graphs → high similarity → "very similar" interpretation
        assertTrue("Report should contain interpretation",
                report.contains("Interpretation"));
        assertTrue("Identical graphs should get 'very similar' label",
                report.contains("very similar"));
    }

    @Test
    public void testReportContainsEntropyProfileTable() {
        Graph<String, Edge> g1 = createTriangle();
        Graph<String, Edge> g2 = createPath();

        GraphSimilarityAnalyzer sim = new GraphSimilarityAnalyzer(g1, g2);
        sim.compute();

        String report = sim.generateReport();
        assertTrue("Report should contain 'Degree Entropy'",
                report.contains("Degree Entropy"));
        assertTrue("Report should contain 'Random Walk Entropy Rate'",
                report.contains("Random Walk Entropy Rate"));
        assertTrue("Report should contain column headers",
                report.contains("Graph 1") && report.contains("Graph 2"));
    }

    @Test
    public void testCompleteGraphVsEmpty() {
        // Complete graph K4 vs graph with only isolated vertices
        Graph<String, Edge> complete = new UndirectedSparseGraph<>();
        for (int i = 0; i < 4; i++) complete.addVertex("V" + i);
        for (int i = 0; i < 4; i++) {
            for (int j = i + 1; j < 4; j++) {
                complete.addEdge(new Edge("friend", "V" + i, "V" + j), "V" + i, "V" + j);
            }
        }

        Graph<String, Edge> isolated = new UndirectedSparseGraph<>();
        for (int i = 0; i < 4; i++) isolated.addVertex("V" + i);

        GraphSimilarityAnalyzer sim = new GraphSimilarityAnalyzer(complete, isolated);
        sim.compute();

        // These should be very different
        assertTrue("Complete vs isolated should have high JSD",
                sim.getJensenShannonDivergence() > 0);
        assertTrue("Similarity should be < 1",
                sim.getSimilarityScore() < 1.0);
    }

    @Test
    public void testIsomorphicGraphsDifferentLabels() {
        // Two triangles with different vertex labels should be identical
        Graph<String, Edge> g1 = new UndirectedSparseGraph<>();
        g1.addVertex("A"); g1.addVertex("B"); g1.addVertex("C");
        g1.addEdge(new Edge("friend", "A", "B"), "A", "B");
        g1.addEdge(new Edge("friend", "B", "C"), "B", "C");
        g1.addEdge(new Edge("friend", "A", "C"), "A", "C");

        Graph<String, Edge> g2 = new UndirectedSparseGraph<>();
        g2.addVertex("X"); g2.addVertex("Y"); g2.addVertex("Z");
        g2.addEdge(new Edge("friend", "X", "Y"), "X", "Y");
        g2.addEdge(new Edge("friend", "Y", "Z"), "Y", "Z");
        g2.addEdge(new Edge("friend", "X", "Z"), "X", "Z");

        GraphSimilarityAnalyzer sim = new GraphSimilarityAnalyzer(g1, g2);
        sim.compute();

        assertEquals("Isomorphic graphs should have JSD = 0",
                0.0, sim.getJensenShannonDivergence(), 1e-10);
        assertEquals("Isomorphic graphs should have similarity = 1",
                1.0, sim.getSimilarityScore(), 1e-6);
    }

    @Test
    public void testVonNeumannSymmetry() {
        Graph<String, Edge> g1 = createPath();
        Graph<String, Edge> g2 = createStar5();

        GraphSimilarityAnalyzer sim12 = new GraphSimilarityAnalyzer(g1, g2);
        sim12.compute();
        GraphSimilarityAnalyzer sim21 = new GraphSimilarityAnalyzer(g2, g1);
        sim21.compute();

        assertEquals("Von Neumann divergence should be symmetric",
                sim12.getVonNeumannDivergence(),
                sim21.getVonNeumannDivergence(), 1e-6);
    }

    @Test
    public void testEntropyProfileDistanceSymmetry() {
        Graph<String, Edge> g1 = createTriangle();
        Graph<String, Edge> g2 = createStar5();

        GraphSimilarityAnalyzer sim12 = new GraphSimilarityAnalyzer(g1, g2);
        sim12.compute();
        GraphSimilarityAnalyzer sim21 = new GraphSimilarityAnalyzer(g2, g1);
        sim21.compute();

        assertEquals("Entropy profile distance should be symmetric",
                sim12.getEntropyProfileDistance(),
                sim21.getEntropyProfileDistance(), 1e-6);
    }
}
