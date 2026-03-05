package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for GraphSimilarityAnalyzer.
 */
public class GraphSimilarityAnalyzerTest {

    private Graph<String, edge> createTriangle() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addEdge(new edge("friend", "A", "B"), "A", "B");
        g.addEdge(new edge("friend", "B", "C"), "B", "C");
        g.addEdge(new edge("friend", "A", "C"), "A", "C");
        return g;
    }

    private Graph<String, edge> createPath() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addEdge(new edge("friend", "A", "B"), "A", "B");
        g.addEdge(new edge("friend", "B", "C"), "B", "C");
        return g;
    }

    private Graph<String, edge> createStar5() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < 5; i++) g.addVertex("V" + i);
        for (int i = 1; i < 5; i++) {
            g.addEdge(new edge("friend", "V0", "V" + i), "V0", "V" + i);
        }
        return g;
    }

    @Test
    public void testIdenticalGraphs() {
        Graph<String, edge> g1 = createTriangle();
        Graph<String, edge> g2 = createTriangle();

        GraphSimilarityAnalyzer sim = new GraphSimilarityAnalyzer(g1, g2);
        sim.compute();

        assertEquals(0.0, sim.getJensenShannonDivergence(), 1e-10);
        assertEquals(0.0, sim.getVonNeumannDivergence(), 1e-6);
        assertEquals(0.0, sim.getEntropyProfileDistance(), 1e-6);
        assertEquals(1.0, sim.getSimilarityScore(), 1e-6);
    }

    @Test
    public void testDifferentGraphs() {
        Graph<String, edge> g1 = createTriangle();
        Graph<String, edge> g2 = createStar5();

        GraphSimilarityAnalyzer sim = new GraphSimilarityAnalyzer(g1, g2);
        sim.compute();

        // Should have nonzero divergences
        assertTrue("JSD should be > 0", sim.getJensenShannonDivergence() > 0);
        assertTrue("Similarity should be < 1", sim.getSimilarityScore() < 1.0);
        assertTrue("Similarity should be > 0", sim.getSimilarityScore() > 0.0);
    }

    @Test
    public void testJSDBounded() {
        Graph<String, edge> g1 = createTriangle();
        Graph<String, edge> g2 = createPath();

        GraphSimilarityAnalyzer sim = new GraphSimilarityAnalyzer(g1, g2);
        sim.compute();

        assertTrue("JSD should be >= 0", sim.getJensenShannonDivergence() >= 0);
        assertTrue("JSD should be <= 1", sim.getJensenShannonDivergence() <= 1);
    }

    @Test
    public void testVonNeumannNonNegative() {
        Graph<String, edge> g1 = createPath();
        Graph<String, edge> g2 = createStar5();

        GraphSimilarityAnalyzer sim = new GraphSimilarityAnalyzer(g1, g2);
        sim.compute();

        assertTrue("VN divergence should be >= 0", sim.getVonNeumannDivergence() >= 0);
    }

    @Test
    public void testEntropyProfiles() {
        Graph<String, edge> g1 = createTriangle();
        Graph<String, edge> g2 = createStar5();

        GraphSimilarityAnalyzer sim = new GraphSimilarityAnalyzer(g1, g2);
        sim.compute();

        double[] p1 = sim.getProfile1();
        double[] p2 = sim.getProfile2();

        assertEquals(6, p1.length);
        assertEquals(6, p2.length);
    }

    @Test
    public void testEmptyGraphs() {
        Graph<String, edge> g1 = new UndirectedSparseGraph<>();
        Graph<String, edge> g2 = new UndirectedSparseGraph<>();

        GraphSimilarityAnalyzer sim = new GraphSimilarityAnalyzer(g1, g2);
        sim.compute();

        assertEquals(0.0, sim.getJensenShannonDivergence(), 1e-10);
        assertEquals(1.0, sim.getSimilarityScore(), 1e-6);
    }

    @Test
    public void testReport() {
        Graph<String, edge> g1 = createTriangle();
        Graph<String, edge> g2 = createStar5();

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
        Graph<String, edge> g1 = createTriangle();
        Graph<String, edge> g2 = createStar5();

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
}
