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

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, Edge>();
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
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "A", "C"), "A", "C");

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
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "A", "C"), "A", "C");

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
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "A", "C"), "A", "C");
        graph.addEdge(new Edge("f", "A", "D"), "A", "D");
        graph.addEdge(new Edge("f", "D", "E"), "D", "E");
        graph.addEdge(new Edge("f", "C", "E"), "C", "E");

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
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "A", "C"), "A", "C");
        graph.addEdge(new Edge("f", "B", "D"), "B", "D");
        graph.addEdge(new Edge("f", "C", "D"), "C", "D");

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
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "A", "C"), "A", "C");

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
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "A", "C"), "A", "C");
        graph.addEdge(new Edge("f", "B", "C"), "B", "C");

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
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");

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
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addVertex("C");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.JACCARD, 5);

        String summary = result.getSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("Link Prediction"));
    }

    // ── Isolated & single-vertex cases ─────────────────────────

    @Test
    public void testSingleVertex() {
        graph.addVertex("A");
        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.COMMON_NEIGHBORS, 5);
        assertEquals(1, result.getTotalVertices());
        assertEquals(0, result.getExistingEdges());
        assertEquals(0, result.getPossibleEdges());
        assertEquals(0, result.getPredictions().size());
    }

    @Test
    public void testTwoVerticesNoEdge() {
        graph.addVertex("A");
        graph.addVertex("B");
        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);

        // Common neighbors: 0 (no shared neighbor) → no prediction with score > 0
        LinkPredictionAnalyzer.PredictionResult cnResult =
                analyzer.predict(LinkPredictionAnalyzer.Method.COMMON_NEIGHBORS, 5);
        assertEquals(0, cnResult.getPredictions().size());
        assertEquals(1, cnResult.getCandidatesEvaluated());

        // Preferential attachment: 0 * 0 = 0 → no prediction
        LinkPredictionAnalyzer.PredictionResult paResult =
                analyzer.predict(LinkPredictionAnalyzer.Method.PREFERENTIAL_ATTACHMENT, 5);
        assertEquals(0, paResult.getPredictions().size());
    }

    @Test
    public void testIsolatedVerticesInLargerGraph() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("isolated");
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "A", "C"), "A", "C");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.COMMON_NEIGHBORS, 10);

        // isolated has no neighbors → no common neighbors with anyone
        // Only B-C should be predicted (common neighbor A)
        assertEquals(1, result.getPredictions().size());
        assertEquals(4, result.getTotalVertices());
    }

    // ── Disconnected graph ─────────────────────────────────────

    @Test
    public void testDisconnectedComponents() {
        // Component 1: A-B-C triangle missing B-C
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "A", "C"), "A", "C");
        // Component 2: D-E
        graph.addVertex("D");
        graph.addVertex("E");
        graph.addEdge(new Edge("f", "D", "E"), "D", "E");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.COMMON_NEIGHBORS, 10);

        // B-C has 1 common neighbor (A), cross-component pairs have 0
        assertEquals(1, result.getPredictions().size());
        LinkPredictionAnalyzer.PredictedLink link = result.getPredictions().get(0);
        assertTrue(
                (link.getVertex1().equals("B") && link.getVertex2().equals("C")) ||
                (link.getVertex1().equals("C") && link.getVertex2().equals("B")));
    }

    // ── topK boundary ──────────────────────────────────────────

    @Test
    public void testTopKLimitsResults() {
        // Star graph: hub A connected to B,C,D,E → many pairs to predict
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");
        graph.addVertex("E");
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "A", "C"), "A", "C");
        graph.addEdge(new Edge("f", "A", "D"), "A", "D");
        graph.addEdge(new Edge("f", "A", "E"), "A", "E");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.COMMON_NEIGHBORS, 2);

        assertTrue(result.getPredictions().size() <= 2);
        assertTrue(result.getCandidatesEvaluated() > 2);
    }

    @Test
    public void testTopKZero() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "A", "C"), "A", "C");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.COMMON_NEIGHBORS, 0);
        assertEquals(0, result.getPredictions().size());
    }

    // ── Score ordering ─────────────────────────────────────────

    @Test
    public void testResultsSortedByScoreDescending() {
        // Create graph where some pairs have more common neighbors than others
        // Hub A connected to B,C,D,E; also B-C connected
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");
        graph.addVertex("E");
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "A", "C"), "A", "C");
        graph.addEdge(new Edge("f", "A", "D"), "A", "D");
        graph.addEdge(new Edge("f", "A", "E"), "A", "E");
        graph.addEdge(new Edge("f", "B", "C"), "B", "C");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.COMMON_NEIGHBORS, 10);

        List<LinkPredictionAnalyzer.PredictedLink> preds = result.getPredictions();
        for (int i = 0; i < preds.size() - 1; i++) {
            assertTrue("Results should be sorted by score descending",
                    preds.get(i).getScore() >= preds.get(i + 1).getScore());
        }
    }

    // ── Jaccard edge cases ─────────────────────────────────────

    @Test
    public void testJaccardWithDifferentSizedNeighborhoods() {
        // A connected to B,C,D,E; F connected to B only
        // Jaccard(A,F) = |{B}| / |{B,C,D,E,F}| ... but F is not neighbor of A
        // Actually: neighbors(A)={B,C,D,E}, neighbors(F)={B}
        // Jaccard(A,F) = |{B}| / |{B,C,D,E}| = 0.25
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");
        graph.addVertex("E");
        graph.addVertex("F");
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "A", "C"), "A", "C");
        graph.addEdge(new Edge("f", "A", "D"), "A", "D");
        graph.addEdge(new Edge("f", "A", "E"), "A", "E");
        graph.addEdge(new Edge("f", "B", "F"), "B", "F");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.JACCARD, 20);

        // Find A-F prediction
        boolean found = false;
        for (LinkPredictionAnalyzer.PredictedLink link : result.getPredictions()) {
            if ((link.getVertex1().equals("A") && link.getVertex2().equals("F")) ||
                (link.getVertex1().equals("F") && link.getVertex2().equals("A"))) {
                assertEquals(0.25, link.getScore(), 0.001);
                found = true;
            }
        }
        assertTrue("A-F prediction should exist", found);
    }

    // ── Adamic-Adar scoring detail ─────────────────────────────

    @Test
    public void testAdamicAdarScoreValue() {
        // A--B, A--C, where degree(A) = 2
        // AA(B,C) = 1/log(2) ≈ 1.4427
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "A", "C"), "A", "C");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.ADAMIC_ADAR, 10);

        assertEquals(1, result.getPredictions().size());
        double expected = 1.0 / Math.log(2.0);
        assertEquals(expected, result.getPredictions().get(0).getScore(), 0.001);
    }

    @Test
    public void testAdamicAdarMultipleCommonNeighbors() {
        // B--A--C, B--D--C → AA(B,C) = 1/log(deg A) + 1/log(deg D)
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "A", "C"), "A", "C");
        graph.addEdge(new Edge("f", "D", "B"), "D", "B");
        graph.addEdge(new Edge("f", "D", "C"), "D", "C");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.ADAMIC_ADAR, 10);

        // Find B-C prediction
        for (LinkPredictionAnalyzer.PredictedLink link : result.getPredictions()) {
            if ((link.getVertex1().equals("B") && link.getVertex2().equals("C")) ||
                (link.getVertex1().equals("C") && link.getVertex2().equals("B"))) {
                // Common neighbors: A (deg 2) and D (deg 2)
                double expected = 2.0 / Math.log(2.0);
                assertEquals(expected, link.getScore(), 0.001);
                assertEquals(2, link.getCommonNeighbors().size());
            }
        }
    }

    // ── Preferential attachment detail ──────────────────────────

    @Test
    public void testPreferentialAttachmentScoreValue() {
        // A: degree 3, E: degree 2 → PA(A,E) = 3*2 = 6
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");
        graph.addVertex("E");
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "A", "C"), "A", "C");
        graph.addEdge(new Edge("f", "A", "D"), "A", "D");
        graph.addEdge(new Edge("f", "D", "E"), "D", "E");
        graph.addEdge(new Edge("f", "C", "E"), "C", "E");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.PREFERENTIAL_ATTACHMENT, 10);

        // Find A-E prediction: PA = deg(A) * deg(E) = 3 * 2 = 6
        boolean found = false;
        for (LinkPredictionAnalyzer.PredictedLink link : result.getPredictions()) {
            if ((link.getVertex1().equals("A") && link.getVertex2().equals("E")) ||
                (link.getVertex1().equals("E") && link.getVertex2().equals("A"))) {
                assertEquals(6.0, link.getScore(), 0.001);
                found = true;
            }
        }
        assertTrue("A-E should be predicted", found);
    }

    // ── PredictionResult metadata ──────────────────────────────

    @Test
    public void testPredictionResultMetadata() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "B", "C"), "B", "C");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.COMMON_NEIGHBORS, 10);

        assertEquals(4, result.getTotalVertices());
        assertEquals(2, result.getExistingEdges());
        assertEquals(6, result.getPossibleEdges()); // 4*3/2
        assertEquals(LinkPredictionAnalyzer.Method.COMMON_NEIGHBORS, result.getMethod());
        assertEquals(2.0 / 6.0, result.getDensity(), 0.001);
    }

    @Test
    public void testDensityZeroForNoEdges() {
        graph.addVertex("A");
        graph.addVertex("B");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.COMMON_NEIGHBORS, 5);

        assertEquals(0.0, result.getDensity(), 0.001);
    }

    @Test
    public void testDensityOneForCompleteGraph() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "A", "C"), "A", "C");
        graph.addEdge(new Edge("f", "B", "C"), "B", "C");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.COMMON_NEIGHBORS, 5);

        assertEquals(1.0, result.getDensity(), 0.001);
    }

    // ── PredictedLink toString ──────────────────────────────────

    @Test
    public void testPredictedLinkToString() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "A", "C"), "A", "C");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.COMMON_NEIGHBORS, 5);

        String str = result.getPredictions().get(0).toString();
        assertNotNull(str);
        assertTrue(str.contains("COMMON_NEIGHBORS"));
        assertTrue(str.contains("common"));
    }

    // ── Ensemble details ───────────────────────────────────────

    @Test
    public void testEnsembleReturnsNormalizedScores() {
        // Star: A connected to B,C,D; predict B-C, B-D, C-D
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "A", "C"), "A", "C");
        graph.addEdge(new Edge("f", "A", "D"), "A", "D");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predictEnsemble(10);

        // All missing pairs have same structure → equal ensemble scores
        List<LinkPredictionAnalyzer.PredictedLink> preds = result.getPredictions();
        assertEquals(3, preds.size());
        // All scores should be in [0, 1] since they're normalized averages
        for (LinkPredictionAnalyzer.PredictedLink link : preds) {
            assertTrue(link.getScore() >= 0.0 && link.getScore() <= 1.0);
        }
    }

    @Test
    public void testEnsembleEmptyGraph() {
        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predictEnsemble(5);
        assertEquals(0, result.getPredictions().size());
    }

    // ── Summary format ─────────────────────────────────────────

    @Test
    public void testSummaryContainsKey() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "A", "C"), "A", "C");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.ADAMIC_ADAR, 5);

        String summary = result.getSummary();
        assertTrue(summary.contains("ADAMIC_ADAR"));
        assertTrue(summary.contains("Vertices: 3"));
        assertTrue(summary.contains("Edges: 2"));
        assertTrue(summary.contains("Candidates evaluated:"));
        assertTrue(summary.contains("Top predictions:"));
    }

    // ── Common neighbors accessor ──────────────────────────────

    @Test
    public void testCommonNeighborsCorrect() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");
        // A--B, A--C, D--B, D--C → predict A-D, common = {B, C}
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "A", "C"), "A", "C");
        graph.addEdge(new Edge("f", "D", "B"), "D", "B");
        graph.addEdge(new Edge("f", "D", "C"), "D", "C");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.COMMON_NEIGHBORS, 10);

        // Find A-D prediction
        for (LinkPredictionAnalyzer.PredictedLink link : result.getPredictions()) {
            if ((link.getVertex1().equals("A") && link.getVertex2().equals("D")) ||
                (link.getVertex1().equals("D") && link.getVertex2().equals("A"))) {
                Set<String> common = link.getCommonNeighbors();
                assertEquals(2, common.size());
                assertTrue(common.contains("B"));
                assertTrue(common.contains("C"));
            }
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCommonNeighborsUnmodifiable() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "A", "C"), "A", "C");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.COMMON_NEIGHBORS, 5);

        result.getPredictions().get(0).getCommonNeighbors().add("X");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testPredictionsListUnmodifiable() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "A", "C"), "A", "C");

        LinkPredictionAnalyzer analyzer = new LinkPredictionAnalyzer(graph);
        LinkPredictionAnalyzer.PredictionResult result =
                analyzer.predict(LinkPredictionAnalyzer.Method.COMMON_NEIGHBORS, 5);

        result.getPredictions().clear();
    }
}
