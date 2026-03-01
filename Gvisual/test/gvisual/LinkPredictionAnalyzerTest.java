package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for LinkPredictionAnalyzer — predicts missing edges using
 * common neighbors, Jaccard, Adamic-Adar, and preferential attachment.
 */
public class LinkPredictionAnalyzerTest {

    private Graph<String, edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, edge>();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new LinkPredictionAnalyzer(null);
    }

    @Test
    public void testEmptyGraph() {
        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.COMMON_NEIGHBORS, 10);
        assertEquals(0, result.getTotalVertices());
        assertEquals(0, result.getPredictions().size());
    }

    @Test
    public void testTrianglePrediction() {
        // A--B, A--C, B--C missing → should predict B--C
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge(new edge("f", "A", "B"), "A", "B");
        graph.addEdge(new edge("f", "A", "C"), "A", "C");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.COMMON_NEIGHBORS, 10);

        assertEquals(1, result.getPredictions().size());
        LinkPredictionAnalyzer.PredictedLink link = result.getPredictions().get(0);
        assertTrue(link.getCommonNeighbors().contains("A"));
        assertEquals(1.0, link.getScore(), 0.001);
    }

    @Test
    public void testJaccardScoring() {
        // A--B, A--C → Jaccard(B,C) = |{A}| / |{A}| = 1.0
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge(new edge("f", "A", "B"), "A", "B");
        graph.addEdge(new edge("f", "A", "C"), "A", "C");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.JACCARD, 10);

        assertEquals(1, result.getPredictions().size());
        assertEquals(1.0, result.getPredictions().get(0).getScore(), 0.001);
    }

    @Test
    public void testPreferentialAttachment() {
        // A(degree 3) and E(degree 2) should score high
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");
        graph.addVertex("E");
        graph.addEdge(new edge("f", "A", "B"), "A", "B");
        graph.addEdge(new edge("f", "A", "C"), "A", "C");
        graph.addEdge(new edge("f", "A", "D"), "A", "D");
        graph.addEdge(new edge("f", "D", "E"), "D", "E");
        graph.addEdge(new edge("f", "C", "E"), "C", "E");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.PREFERENTIAL_ATTACHMENT, 5);

        // A-E should be predicted (3*2=6)
        assertTrue(result.getPredictions().size() > 0);
        // Highest PA score pair should involve high-degree nodes
        LinkPredictionAnalyzer.PredictedLink top = result.getPredictions().get(0);
        assertTrue(top.getScore() > 0);
    }

    @Test
    public void testAdamicAdar() {
        // A--B, A--C, B--D, C--D → predict B-C with common neighbor A
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");
        graph.addEdge(new edge("f", "A", "B"), "A", "B");
        graph.addEdge(new edge("f", "A", "C"), "A", "C");
        graph.addEdge(new edge("f", "B", "D"), "B", "D");
        graph.addEdge(new edge("f", "C", "D"), "C", "D");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.ADAMIC_ADAR, 10);

        assertTrue(result.getPredictions().size() > 0);
        // AA score for common neighbor with degree 2 = 1/log(2) ≈ 1.443
        for (LinkPredictionAnalyzer.PredictedLink link : result.getPredictions()) {
            assertTrue(link.getScore() > 0);
        }
    }

    @Test
    public void testEnsemblePrediction() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge(new edge("f", "A", "B"), "A", "B");
        graph.addEdge(new edge("f", "A", "C"), "A", "C");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predictEnsemble(10);

        assertTrue(result.getPredictions().size() > 0);
    }

    @Test
    public void testCompleteGraphNoPredictions() {
        // Complete graph K3 — no missing edges
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge(new edge("f", "A", "B"), "A", "B");
        graph.addEdge(new edge("f", "A", "C"), "A", "C");
        graph.addEdge(new edge("f", "B", "C"), "B", "C");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.COMMON_NEIGHBORS, 10);

        assertEquals(0, result.getPredictions().size());
        assertEquals(3, result.getExistingEdges());
    }

    @Test
    public void testDensityCalculation() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge(new edge("f", "A", "B"), "A", "B");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.COMMON_NEIGHBORS, 10);

        // 1 edge out of 3 possible = 33.3%
        assertEquals(1.0 / 3.0, result.getDensity(), 0.001);
    }

    @Test
    public void testSummaryNotNull() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addEdge(new edge("f", "A", "B"), "A", "B");
        graph.addVertex("C");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.JACCARD, 5);

        String summary = result.getSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("Link Prediction"));
    }
}
