package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for NodeSimilarityAnalyzer — pairwise node similarity metrics.
 */
public class NodeSimilarityAnalyzerTest {

    private Graph<String, edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, edge>();
    }

    private void addEdge(String v1, String v2, String type) {
        graph.addVertex(v1);
        graph.addVertex(v2);
        edge e = new edge(type, v1, v2);
        graph.addEdge(e, v1, v2);
    }

    /**
     * Diamond graph: A-B, A-C, B-D, C-D (plus B-C for extra connectivity).
     *
     *     A
     *    / \
     *   B - C
     *    \ /
     *     D
     */
    private void buildDiamond() {
        addEdge("A", "B", "f");
        addEdge("A", "C", "f");
        addEdge("B", "C", "f");
        addEdge("B", "D", "f");
        addEdge("C", "D", "f");
    }

    /**
     * Two cliques {A,B,C} and {D,E,F} with bridge B-D.
     */
    private void buildTwoCliques() {
        addEdge("A", "B", "f");
        addEdge("A", "C", "f");
        addEdge("B", "C", "f");
        addEdge("D", "E", "f");
        addEdge("D", "F", "f");
        addEdge("E", "F", "f");
        addEdge("B", "D", "f");
    }

    // ── Constructor ─────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new NodeSimilarityAnalyzer(null);
    }

    // ── Jaccard ─────────────────────────────────────────────────

    @Test
    public void testJaccardIdenticalNeighborhoods() {
        // B and C both connected to A and D (and each other)
        buildDiamond();
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        // N(B) = {A, C, D}, N(C) = {A, B, D}
        // intersection = {A, D} = 2, union = {A, B, C, D} = 4
        double j = nsa.jaccard("B", "C");
        assertEquals(0.5, j, 0.001);
    }

    @Test
    public void testJaccardNoCommonNeighbors() {
        // A-B, C-D — A and D share no neighbors
        addEdge("A", "B", "f");
        addEdge("C", "D", "f");
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        assertEquals(0.0, nsa.jaccard("A", "D"), 0.001);
    }

    @Test
    public void testJaccardBothIsolated() {
        graph.addVertex("X");
        graph.addVertex("Y");
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        assertEquals(1.0, nsa.jaccard("X", "Y"), 0.001);
    }

    @Test
    public void testJaccardOneIsolated() {
        addEdge("A", "B", "f");
        graph.addVertex("X");
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        assertEquals(0.0, nsa.jaccard("A", "X"), 0.001);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testJaccardInvalidNode() {
        addEdge("A", "B", "f");
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        nsa.jaccard("A", "MISSING");
    }

    // ── Overlap ─────────────────────────────────────────────────

    @Test
    public void testOverlapSubset() {
        // A-B, A-C, B-C, A-D
        // N(B) = {A, C}, N(A) = {B, C, D}
        // intersection = {C} wait — B is in N(A)? Yes.
        // N(B) = {A, C}, N(A) = {B, C, D}
        // intersection of N(B) and N(A) = {C} (A not in its own neighborhood, B not neighbor of itself)
        // Actually: N(A) = {B, C, D}, N(B) = {A, C}
        // intersection = {C}, min = 2
        // overlap = 1/2 = 0.5
        addEdge("A", "B", "f");
        addEdge("A", "C", "f");
        addEdge("B", "C", "f");
        addEdge("A", "D", "f");
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        assertEquals(0.5, nsa.overlap("A", "B"), 0.001);
    }

    @Test
    public void testOverlapBothIsolated() {
        graph.addVertex("X");
        graph.addVertex("Y");
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        assertEquals(1.0, nsa.overlap("X", "Y"), 0.001);
    }

    @Test
    public void testOverlapOneIsolated() {
        addEdge("A", "B", "f");
        graph.addVertex("X");
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        assertEquals(0.0, nsa.overlap("A", "X"), 0.001);
    }

    // ── Adamic-Adar ─────────────────────────────────────────────

    @Test
    public void testAdamicAdarCommonNeighbors() {
        buildDiamond();
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        // N(A) = {B, C}, N(D) = {B, C}
        // Common = {B, C}. |N(B)| = 3, |N(C)| = 3
        // AA = 1/log(3) + 1/log(3) = 2/log(3)
        double expected = 2.0 / Math.log(3);
        assertEquals(expected, nsa.adamicAdar("A", "D"), 0.001);
    }

    @Test
    public void testAdamicAdarNoCommon() {
        addEdge("A", "B", "f");
        addEdge("C", "D", "f");
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        assertEquals(0.0, nsa.adamicAdar("A", "D"), 0.001);
    }

    @Test
    public void testAdamicAdarDegreeOneCommon() {
        // A-C, B-C where C has degree 1? No, C has degree 2.
        // For degree-1 common neighbor: A-X, B-X, X has only A and B as neighbors => deg=2
        // We need a node with deg=1: only possible if X connects to one node.
        // A-X only => X has deg 1, but then X isn't neighbor of B.
        // Actually, Adamic-Adar skips degree-1 nodes (log(1) = 0). Let's verify.
        addEdge("A", "B", "f");
        addEdge("A", "C", "f");
        // N(B)={A}, N(C)={A}. Common={A}. deg(A)=2. AA=1/log(2)
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        double expected = 1.0 / Math.log(2);
        assertEquals(expected, nsa.adamicAdar("B", "C"), 0.001);
    }

    // ── Cosine ──────────────────────────────────────────────────

    @Test
    public void testCosineIdenticalNeighborhoods() {
        // Star: A-B, A-C, A-D. Then add E-B, E-C, E-D.
        // N(A) = {B,C,D}, N(E) = {B,C,D} — identical neighborhoods
        addEdge("A", "B", "f");
        addEdge("A", "C", "f");
        addEdge("A", "D", "f");
        addEdge("E", "B", "f");
        addEdge("E", "C", "f");
        addEdge("E", "D", "f");
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        assertEquals(1.0, nsa.cosine("A", "E"), 0.001);
    }

    @Test
    public void testCosineBothIsolated() {
        graph.addVertex("X");
        graph.addVertex("Y");
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        assertEquals(1.0, nsa.cosine("X", "Y"), 0.001);
    }

    @Test
    public void testCosineOneIsolated() {
        addEdge("A", "B", "f");
        graph.addVertex("X");
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        assertEquals(0.0, nsa.cosine("A", "X"), 0.001);
    }

    // ── Structural Equivalence ──────────────────────────────────

    @Test
    public void testStructuralEquivalencePerfect() {
        // A-C, A-D, B-C, B-D. A and B connect to exactly the same nodes.
        addEdge("A", "C", "f");
        addEdge("A", "D", "f");
        addEdge("B", "C", "f");
        addEdge("B", "D", "f");
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        // Other nodes: C, D. A connects to both, B connects to both => match=2/2=1.0
        assertEquals(1.0, nsa.structuralEquivalence("A", "B"), 0.001);
    }

    @Test
    public void testStructuralEquivalenceZero() {
        // A connects to B only, C connects to D only, check A vs C
        // Others: B, D. A->B yes, C->B no (mismatch). A->D no, C->D yes (mismatch).
        addEdge("A", "B", "f");
        addEdge("C", "D", "f");
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        assertEquals(0.0, nsa.structuralEquivalence("A", "C"), 0.001);
    }

    @Test
    public void testStructuralEquivalenceSingleNode() {
        // Only two nodes, no "other" nodes to compare
        addEdge("A", "B", "f");
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        assertEquals(1.0, nsa.structuralEquivalence("A", "B"), 0.001);
    }

    // ── Generic similarity() method ─────────────────────────────

    @Test
    public void testSimilarityDispatch() {
        buildDiamond();
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        assertEquals(nsa.jaccard("A", "B"), nsa.similarity("A", "B", NodeSimilarityAnalyzer.Metric.JACCARD), 0.0001);
        assertEquals(nsa.adamicAdar("A", "B"), nsa.similarity("A", "B", NodeSimilarityAnalyzer.Metric.ADAMIC_ADAR), 0.0001);
        assertEquals(nsa.overlap("A", "B"), nsa.similarity("A", "B", NodeSimilarityAnalyzer.Metric.OVERLAP), 0.0001);
        assertEquals(nsa.cosine("A", "B"), nsa.similarity("A", "B", NodeSimilarityAnalyzer.Metric.COSINE), 0.0001);
        assertEquals(nsa.structuralEquivalence("A", "B"), nsa.similarity("A", "B", NodeSimilarityAnalyzer.Metric.STRUCTURAL_EQUIVALENCE), 0.0001);
    }

    // ── mostSimilar ─────────────────────────────────────────────

    @Test
    public void testMostSimilarReturnsTopK() {
        buildTwoCliques();
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        List<NodeSimilarityAnalyzer.ScoredPair> top = nsa.mostSimilar(NodeSimilarityAnalyzer.Metric.JACCARD, 3);
        assertEquals(3, top.size());
        // Scores should be descending
        assertTrue(top.get(0).getScore() >= top.get(1).getScore());
        assertTrue(top.get(1).getScore() >= top.get(2).getScore());
    }

    @Test
    public void testMostSimilarZeroK() {
        buildDiamond();
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        assertTrue(nsa.mostSimilar(NodeSimilarityAnalyzer.Metric.JACCARD, 0).isEmpty());
    }

    @Test
    public void testMostSimilarTopPairIsSymmetric() {
        buildTwoCliques();
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        // A and C should be structurally equivalent in clique 1 (minus bridge node B)
        // E and F should be structurally equivalent in clique 2
        List<NodeSimilarityAnalyzer.ScoredPair> top = nsa.mostSimilar(NodeSimilarityAnalyzer.Metric.STRUCTURAL_EQUIVALENCE, 1);
        assertEquals(1, top.size());
        assertTrue(top.get(0).getScore() > 0.5); // should be high
    }

    // ── kNearestNeighbors ───────────────────────────────────────

    @Test
    public void testKNearestNeighbors() {
        buildDiamond();
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        LinkedHashMap<String, Double> knn = nsa.kNearestNeighbors("A", NodeSimilarityAnalyzer.Metric.JACCARD, 2);
        assertEquals(2, knn.size());
        // Values should be in descending order
        Double[] scores = knn.values().toArray(new Double[0]);
        assertTrue(scores[0] >= scores[1]);
    }

    @Test
    public void testKNearestNeighborsZeroK() {
        buildDiamond();
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        assertTrue(nsa.kNearestNeighbors("A", NodeSimilarityAnalyzer.Metric.JACCARD, 0).isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testKNearestNeighborsInvalidNode() {
        buildDiamond();
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        nsa.kNearestNeighbors("MISSING", NodeSimilarityAnalyzer.Metric.JACCARD, 3);
    }

    // ── similarityMatrix ────────────────────────────────────────

    @Test
    public void testSimilarityMatrixSize() {
        buildDiamond();
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        Map<String, Double> matrix = nsa.similarityMatrix(NodeSimilarityAnalyzer.Metric.JACCARD);
        // 4 nodes => C(4,2) = 6 pairs
        assertEquals(6, matrix.size());
    }

    @Test
    public void testSimilarityMatrixKeyFormat() {
        addEdge("A", "B", "f");
        addEdge("B", "C", "f");
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        Map<String, Double> matrix = nsa.similarityMatrix(NodeSimilarityAnalyzer.Metric.JACCARD);
        assertTrue(matrix.containsKey("A|B"));
        assertTrue(matrix.containsKey("A|C"));
        assertTrue(matrix.containsKey("B|C"));
    }

    // ── similarPairsAboveThreshold ──────────────────────────────

    @Test
    public void testSimilarPairsAboveThreshold() {
        buildDiamond();
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        List<NodeSimilarityAnalyzer.ScoredPair> pairs = nsa.similarPairsAboveThreshold(NodeSimilarityAnalyzer.Metric.JACCARD, 0.4);
        for (NodeSimilarityAnalyzer.ScoredPair sp : pairs) {
            assertTrue(sp.getScore() >= 0.4);
        }
    }

    @Test
    public void testSimilarPairsAboveThresholdHighThreshold() {
        addEdge("A", "B", "f");
        addEdge("C", "D", "f");
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        // No pair should have Jaccard > 0.99 in a disconnected graph
        List<NodeSimilarityAnalyzer.ScoredPair> pairs = nsa.similarPairsAboveThreshold(NodeSimilarityAnalyzer.Metric.JACCARD, 0.99);
        assertTrue(pairs.isEmpty());
    }

    // ── report ──────────────────────────────────────────────────

    @Test
    public void testReportContainsAllMetrics() {
        buildDiamond();
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        String report = nsa.report("A", 3);
        assertTrue(report.contains("JACCARD"));
        assertTrue(report.contains("OVERLAP"));
        assertTrue(report.contains("ADAMIC_ADAR"));
        assertTrue(report.contains("COSINE"));
        assertTrue(report.contains("STRUCTURAL_EQUIVALENCE"));
    }

    @Test
    public void testReportContainsTarget() {
        buildDiamond();
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        String report = nsa.report("A", 3);
        assertTrue(report.contains("Node Similarity Report: A"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReportInvalidNode() {
        buildDiamond();
        NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
        nsa.report("MISSING", 3);
    }

    // ── ScoredPair ──────────────────────────────────────────────

    @Test
    public void testScoredPairToString() {
        NodeSimilarityAnalyzer.ScoredPair sp = new NodeSimilarityAnalyzer.ScoredPair("A", "B", 0.75);
        String s = sp.toString();
        assertTrue(s.contains("A"));
        assertTrue(s.contains("B"));
        assertTrue(s.contains("0.7500"));
    }

    @Test
    public void testScoredPairComparableDescending() {
        NodeSimilarityAnalyzer.ScoredPair a = new NodeSimilarityAnalyzer.ScoredPair("A", "B", 0.9);
        NodeSimilarityAnalyzer.ScoredPair b = new NodeSimilarityAnalyzer.ScoredPair("C", "D", 0.3);
        assertTrue(a.compareTo(b) < 0); // a should come first (higher score)
    }
}
