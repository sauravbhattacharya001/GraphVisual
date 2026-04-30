package gvisual;

import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphKnowledgeExtractor}.
 */
public class GraphKnowledgeExtractorTest {

    private static Edge addEdge(Graph<String, Edge> g, String v1, String v2) {
        g.addVertex(v1);
        g.addVertex(v2);
        Edge e = new Edge("f", v1, v2);
        g.addEdge(e, v1, v2);
        return e;
    }

    private static Edge addDirectedEdge(Graph<String, Edge> g, String v1, String v2) {
        g.addVertex(v1);
        g.addVertex(v2);
        Edge e = new Edge("f", v1, v2);
        g.addEdge(e, v1, v2);
        return e;
    }

    // ── Constructor ─────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new GraphKnowledgeExtractor(null);
    }

    @Test
    public void testEmptyGraphAnalyzes() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(g);
        GraphKnowledgeExtractor.ExtractorReport report = ext.analyze();
        assertNotNull(report);
        assertEquals(0, report.getTotalNodes());
        assertEquals(0, report.getTotalEdges());
        assertTrue(report.getPredictions().isEmpty());
    }

    @Test
    public void testSingleNodeGraph() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(g);
        GraphKnowledgeExtractor.ExtractorReport report = ext.analyze();
        assertEquals(1, report.getTotalNodes());
        assertTrue(report.getPredictions().isEmpty());
    }

    // ── Complete Graph (no predictions) ─────────────────────────────

    @Test
    public void testCompleteGraphNoPredictions() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "A", "C");
        addEdge(g, "B", "C");
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(g);
        GraphKnowledgeExtractor.ExtractorReport report = ext.analyze();
        // All possible edges exist → no predictions
        assertTrue(report.getPredictions().isEmpty());
    }

    @Test
    public void testCompleteGraphHighCompleteness() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "A", "C");
        addEdge(g, "B", "C");
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(g);
        GraphKnowledgeExtractor.ExtractorReport report = ext.analyze();
        assertTrue(report.getCompletenessScore() > 50);
    }

    // ── Triangle Closure ────────────────────────────────────────────

    @Test
    public void testTriangleClosurePrediction() {
        // A-B, B-C but no A-C → should predict A-C
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(g);
        GraphKnowledgeExtractor.ExtractorReport report = ext.analyze();
        assertFalse(report.getPredictions().isEmpty());
        // A-C should be in predictions
        boolean found = report.getPredictions().stream()
                .anyMatch(p -> (p.getSource().equals("A") && p.getTarget().equals("C"))
                        || (p.getSource().equals("C") && p.getTarget().equals("A")));
        assertTrue(found);
    }

    @Test
    public void testTriadicClosureDetected() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(g);
        GraphKnowledgeExtractor.ExtractorReport report = ext.analyze();
        assertFalse(report.getTriadicClosures().isEmpty());
    }

    // ── Link Prediction Heuristics ──────────────────────────────────

    @Test
    public void testCommonNeighborsScore() {
        // A-B, A-C, B-D, C-D → predict B-C (share A, D as neighbors? actually A connects to B,C)
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "A", "C");
        addEdge(g, "B", "D");
        addEdge(g, "C", "D");
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(g);
        GraphKnowledgeExtractor.ExtractorReport report = ext.analyze();
        // B-C should have common neighbors A and D
        Optional<GraphKnowledgeExtractor.Prediction> bc = report.getPredictions().stream()
                .filter(p -> (p.getSource().equals("B") && p.getTarget().equals("C"))
                        || (p.getSource().equals("C") && p.getTarget().equals("B")))
                .findFirst();
        assertTrue(bc.isPresent());
        assertTrue(bc.get().getHeuristicScores().get("CommonNeighbors") >= 2.0);
    }

    @Test
    public void testPredictionScoreRange() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(g);
        GraphKnowledgeExtractor.ExtractorReport report = ext.analyze();
        for (GraphKnowledgeExtractor.Prediction p : report.getPredictions()) {
            assertTrue(p.getScore() >= 0 && p.getScore() <= 1.0);
        }
    }

    @Test
    public void testPredictionsOrdered() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "A", "C");
        addEdge(g, "B", "D");
        addEdge(g, "C", "D");
        addEdge(g, "D", "E");
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(g);
        GraphKnowledgeExtractor.ExtractorReport report = ext.analyze();
        List<GraphKnowledgeExtractor.Prediction> preds = report.getPredictions();
        for (int i = 1; i < preds.size(); i++) {
            assertTrue(preds.get(i - 1).getScore() >= preds.get(i).getScore());
        }
    }

    // ── Star Graph ──────────────────────────────────────────────────

    @Test
    public void testStarGraphPredictions() {
        // Hub A connected to B,C,D,E — predicts connections among leaves
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "A", "C");
        addEdge(g, "A", "D");
        addEdge(g, "A", "E");
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(g);
        GraphKnowledgeExtractor.ExtractorReport report = ext.analyze();
        assertFalse(report.getPredictions().isEmpty());
        // All predictions should be between leaves
        for (GraphKnowledgeExtractor.Prediction p : report.getPredictions()) {
            assertFalse(p.getSource().equals("A") || p.getTarget().equals("A"));
        }
    }

    @Test
    public void testStarMotifDetected() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "A", "C");
        addEdge(g, "A", "D");
        addEdge(g, "A", "E");
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(g);
        GraphKnowledgeExtractor.ExtractorReport report = ext.analyze();
        boolean hasStar = report.getMotifPatterns().stream()
                .anyMatch(m -> m.getType().equals("star"));
        assertTrue(hasStar);
    }

    // ── Chain Graph ─────────────────────────────────────────────────

    @Test
    public void testChainGraphPredictions() {
        // A-B-C-D-E linear chain
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        addEdge(g, "D", "E");
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(g);
        GraphKnowledgeExtractor.ExtractorReport report = ext.analyze();
        assertFalse(report.getPredictions().isEmpty());
    }

    @Test
    public void testChainMotifDetected() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        addEdge(g, "D", "E");
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(g);
        GraphKnowledgeExtractor.ExtractorReport report = ext.analyze();
        boolean hasChain = report.getMotifPatterns().stream()
                .anyMatch(m -> m.getType().equals("chain"));
        assertTrue(hasChain);
    }

    // ── Directed Graph / Reciprocity ────────────────────────────────

    @Test
    public void testDirectedGraphReciprocity() {
        Graph<String, Edge> g = new DirectedSparseGraph<>();
        addDirectedEdge(g, "A", "B");
        addDirectedEdge(g, "B", "C");
        addDirectedEdge(g, "C", "A"); // creates cycle, A→B not reciprocated
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(g);
        GraphKnowledgeExtractor.ExtractorReport report = ext.analyze();
        assertFalse(report.getReciprocityCandidates().isEmpty());
    }

    @Test
    public void testUndirectedGraphNoReciprocity() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(g);
        GraphKnowledgeExtractor.ExtractorReport report = ext.analyze();
        assertTrue(report.getReciprocityCandidates().isEmpty());
    }

    @Test
    public void testFullyReciprocatedNoCandidate() {
        Graph<String, Edge> g = new DirectedSparseGraph<>();
        addDirectedEdge(g, "A", "B");
        addDirectedEdge(g, "B", "A");
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(g);
        GraphKnowledgeExtractor.ExtractorReport report = ext.analyze();
        assertTrue(report.getReciprocityCandidates().isEmpty());
    }

    // ── Structural Holes ────────────────────────────────────────────

    @Test
    public void testDisconnectedGraphFindsHoles() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        // Two disconnected triangles
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "A", "C");
        addEdge(g, "D", "E");
        addEdge(g, "E", "F");
        addEdge(g, "D", "F");
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(g);
        GraphKnowledgeExtractor.ExtractorReport report = ext.analyze();
        assertFalse(report.getStructuralHoles().isEmpty());
    }

    // ── Completeness Score ──────────────────────────────────────────

    @Test
    public void testCompletenessScoreRange() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(g);
        GraphKnowledgeExtractor.ExtractorReport report = ext.analyze();
        assertTrue(report.getCompletenessScore() >= 0);
        assertTrue(report.getCompletenessScore() <= 100);
    }

    @Test
    public void testCompletenessBreakdownKeys() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(g);
        GraphKnowledgeExtractor.ExtractorReport report = ext.analyze();
        Map<String, Double> bd = report.getCompletenessBreakdown();
        assertTrue(bd.containsKey("density"));
        assertTrue(bd.containsKey("clustering"));
        assertTrue(bd.containsKey("closureRate"));
        assertTrue(bd.containsKey("connectivity"));
    }

    @Test
    public void testDenseGraphHigherCompleteness() {
        // Dense graph should score higher than sparse
        Graph<String, Edge> sparse = new UndirectedSparseGraph<>();
        addEdge(sparse, "A", "B");
        addEdge(sparse, "B", "C");
        addEdge(sparse, "C", "D");
        addEdge(sparse, "D", "E");

        Graph<String, Edge> dense = new UndirectedSparseGraph<>();
        addEdge(dense, "A", "B");
        addEdge(dense, "A", "C");
        addEdge(dense, "A", "D");
        addEdge(dense, "B", "C");
        addEdge(dense, "B", "D");
        addEdge(dense, "C", "D");

        double sparseScore = new GraphKnowledgeExtractor(sparse).analyze().getCompletenessScore();
        double denseScore = new GraphKnowledgeExtractor(dense).analyze().getCompletenessScore();
        assertTrue(denseScore > sparseScore);
    }

    // ── Text Export ─────────────────────────────────────────────────

    @Test
    public void testTextExportNotEmpty() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(g);
        GraphKnowledgeExtractor.ExtractorReport report = ext.analyze();
        String text = ext.toText(report);
        assertNotNull(text);
        assertTrue(text.contains("GRAPH KNOWLEDGE EXTRACTOR"));
        assertTrue(text.contains("Link Predictions"));
    }

    @Test
    public void testTextExportContainsNodeCount() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(g);
        GraphKnowledgeExtractor.ExtractorReport report = ext.analyze();
        String text = ext.toText(report);
        assertTrue(text.contains("4 nodes"));
    }

    // ── HTML Export ──────────────────────────────────────────────────

    @Test
    public void testHtmlExportNotEmpty() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(g);
        GraphKnowledgeExtractor.ExtractorReport report = ext.analyze();
        String html = ext.exportHtml(report);
        assertNotNull(html);
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("Knowledge Extractor"));
    }

    @Test
    public void testHtmlExportContainsTabs() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(g);
        GraphKnowledgeExtractor.ExtractorReport report = ext.analyze();
        String html = ext.exportHtml(report);
        assertTrue(html.contains("Link Predictions"));
        assertTrue(html.contains("Triadic Closures"));
        assertTrue(html.contains("Structural Holes"));
    }

    // ── TopK Configuration ──────────────────────────────────────────

    @Test
    public void testTopKSetting() {
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(new UndirectedSparseGraph<>());
        ext.setTopK(5);
        assertEquals(5, ext.getTopK());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTopKZeroThrows() {
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(new UndirectedSparseGraph<>());
        ext.setTopK(0);
    }

    @Test
    public void testTopKLimitsPredictions() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        // Create star with many leaves → many possible predictions
        for (int i = 0; i < 10; i++) {
            addEdge(g, "hub", "leaf" + i);
        }
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(g);
        ext.setTopK(3);
        GraphKnowledgeExtractor.ExtractorReport report = ext.analyze();
        assertTrue(report.getPredictions().size() <= 3);
    }

    // ── Bipartite Graph ─────────────────────────────────────────────

    @Test
    public void testBipartiteGraphPredictions() {
        // Complete bipartite K(2,3) — no predictions within partitions possible
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "L1", "R1");
        addEdge(g, "L1", "R2");
        addEdge(g, "L1", "R3");
        addEdge(g, "L2", "R1");
        addEdge(g, "L2", "R2");
        addEdge(g, "L2", "R3");
        GraphKnowledgeExtractor ext = new GraphKnowledgeExtractor(g);
        GraphKnowledgeExtractor.ExtractorReport report = ext.analyze();
        // L1-L2 should be predicted (both connect to all R's)
        boolean foundL = report.getPredictions().stream()
                .anyMatch(p -> (p.getSource().equals("L1") && p.getTarget().equals("L2"))
                        || (p.getSource().equals("L2") && p.getTarget().equals("L1")));
        assertTrue(foundL);
    }
}
