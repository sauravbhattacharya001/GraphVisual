package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphCompressor}.
 */
public class GraphCompressorTest {

    private Graph<String, edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
    }

    // ── Construction ────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraph() {
        new GraphCompressor(null);
    }

    @Test
    public void testEmptyGraph() {
        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byStructuralEquivalence();
        assertEquals(0, result.getCompressedNodeCount());
        assertEquals(0, result.getCompressedEdgeCount());
    }

    @Test
    public void testSingleNode() {
        graph.addVertex("A");
        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byStructuralEquivalence();
        assertEquals(1, result.getCompressedNodeCount());
        assertEquals(0, result.getCompressedEdgeCount());
    }

    // ── Structural Equivalence ──────────────────────────────────────

    @Test
    public void testStructuralEquivalence_identicalNeighbors() {
        // B and C both connect only to A → structurally equivalent
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        addEdge("A", "B", "e1");
        addEdge("A", "C", "e2");

        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byStructuralEquivalence();

        // B and C should merge (same neighbor set {A})
        assertTrue(result.getCompressedNodeCount() < graph.getVertexCount());
        assertEquals(result.getSupernodeOf("B"), result.getSupernodeOf("C"));
        assertNotEquals(result.getSupernodeOf("A"), result.getSupernodeOf("B"));
    }

    @Test
    public void testStructuralEquivalence_noEquivalent() {
        // A-B, B-C, C-D: A neighbors={B}, B neighbors={A,C}, C neighbors={B,D}, D neighbors={C}
        addEdge("A", "B", "e1");
        addEdge("B", "C", "e2");
        addEdge("C", "D", "e3");

        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byStructuralEquivalence();

        // A has {B}, D has {C} — different; B has {A,C}, C has {B,D} — different
        assertEquals(4, result.getCompressedNodeCount());
    }

    @Test
    public void testStructuralEquivalence_allEquivalent() {
        // Star: A is center, B/C/D/E are leaves → B/C/D/E equivalent
        for (String leaf : new String[]{"B", "C", "D", "E"}) {
            addEdge("A", leaf, "e_" + leaf);
        }

        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byStructuralEquivalence();

        assertEquals(2, result.getCompressedNodeCount());
        String snB = result.getSupernodeOf("B");
        assertEquals(snB, result.getSupernodeOf("C"));
        assertEquals(snB, result.getSupernodeOf("D"));
        assertEquals(snB, result.getSupernodeOf("E"));
    }

    // ── Neighborhood Similarity ─────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNeighborhoodSimilarity_invalidThresholdLow() {
        new GraphCompressor(graph).byNeighborhoodSimilarity(-0.1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNeighborhoodSimilarity_invalidThresholdHigh() {
        new GraphCompressor(graph).byNeighborhoodSimilarity(1.1);
    }

    @Test
    public void testNeighborhoodSimilarity_lowThreshold_mergesMore() {
        // Triangle + pendant
        addEdge("A", "B", "e1");
        addEdge("B", "C", "e2");
        addEdge("A", "C", "e3");
        addEdge("C", "D", "e4");

        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult strict = compressor.byNeighborhoodSimilarity(0.9);
        GraphCompressor.CompressionResult relaxed = compressor.byNeighborhoodSimilarity(0.3);

        assertTrue(relaxed.getCompressedNodeCount() <= strict.getCompressedNodeCount());
    }

    @Test
    public void testNeighborhoodSimilarity_zeroThreshold_mergesAll() {
        addEdge("A", "B", "e1");
        addEdge("C", "D", "e2");

        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byNeighborhoodSimilarity(0.0);

        // With threshold 0, all pairs have similarity >= 0, so greedy merge
        assertTrue(result.getCompressedNodeCount() < 4);
    }

    // ── Degree-Based ────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testDegree_invalidBinSize() {
        new GraphCompressor(graph).byDegree(0);
    }

    @Test
    public void testExactDegree() {
        // Star: center has degree 3, leaves have degree 1
        addEdge("A", "B", "e1");
        addEdge("A", "C", "e2");
        addEdge("A", "D", "e3");

        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byExactDegree();

        // Two groups: degree-1 {B,C,D} and degree-3 {A}
        assertEquals(2, result.getCompressedNodeCount());
    }

    @Test
    public void testDegreeBinning() {
        // Various degrees
        addEdge("A", "B", "e1");
        addEdge("A", "C", "e2");
        addEdge("A", "D", "e3");
        addEdge("A", "E", "e4");
        addEdge("B", "C", "e5");

        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byDegree(2);

        // Degrees: A=4, B=2, C=2, D=1, E=1
        // Bins: [0,2)={D,E}, [2,4)={B,C}, [4,6)={A}
        assertEquals(3, result.getCompressedNodeCount());
    }

    // ── Attribute-Based ─────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testAttribute_nullFunction() {
        new GraphCompressor(graph).byAttribute(null);
    }

    @Test
    public void testAttribute_groupsByLabel() {
        addEdge("A", "B", "e1");
        addEdge("B", "C", "e2");
        addEdge("C", "D", "e3");

        Map<String, String> labels = new HashMap<>();
        labels.put("A", "group1");
        labels.put("B", "group1");
        labels.put("C", "group2");
        labels.put("D", "group2");

        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byAttribute(labels::get);

        assertEquals(2, result.getCompressedNodeCount());
        assertEquals(result.getSupernodeOf("A"), result.getSupernodeOf("B"));
        assertEquals(result.getSupernodeOf("C"), result.getSupernodeOf("D"));
    }

    @Test
    public void testAttribute_nullValues_groupedTogether() {
        graph.addVertex("A");
        graph.addVertex("B");

        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byAttribute(v -> null);

        assertEquals(1, result.getCompressedNodeCount());
    }

    // ── K-Hop Locality ──────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testKHop_nullSeeds() {
        new GraphCompressor(graph).byKHopLocality(null, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testKHop_emptySeeds() {
        new GraphCompressor(graph).byKHopLocality(Collections.emptyList(), 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testKHop_invalidK() {
        graph.addVertex("A");
        new GraphCompressor(graph).byKHopLocality(List.of("A"), 0);
    }

    @Test
    public void testKHop_absorbs1Hop() {
        // Path A-B-C-D-E
        addEdge("A", "B", "e1");
        addEdge("B", "C", "e2");
        addEdge("C", "D", "e3");
        addEdge("D", "E", "e4");

        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byKHopLocality(List.of("A", "E"), 1);

        // A absorbs B (1-hop), E absorbs D (1-hop), C is unclaimed singleton
        String snA = result.getSupernodeOf("A");
        String snB = result.getSupernodeOf("B");
        assertEquals(snA, snB);

        String snE = result.getSupernodeOf("E");
        String snD = result.getSupernodeOf("D");
        assertEquals(snE, snD);
    }

    @Test
    public void testKHop_seedNotInGraph_skipped() {
        graph.addVertex("A");

        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byKHopLocality(List.of("X", "A"), 1);

        assertEquals(1, result.getCompressedNodeCount());
    }

    // ── Result Methods ──────────────────────────────────────────────

    @Test
    public void testCompressionRatio() {
        for (String leaf : new String[]{"B", "C", "D", "E"}) {
            addEdge("A", leaf, "e_" + leaf);
        }

        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byStructuralEquivalence();

        double ratio = result.getCompressionRatio();
        assertTrue(ratio > 0 && ratio <= 1.0);
        assertEquals(2.0 / 5.0, ratio, 0.001);
    }

    @Test
    public void testNodeReductionPercent() {
        for (String leaf : new String[]{"B", "C", "D", "E"}) {
            addEdge("A", leaf, "e_" + leaf);
        }

        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byStructuralEquivalence();

        assertEquals(60.0, result.getNodeReductionPercent(), 0.1);
    }

    @Test
    public void testGetMergedGroupCount() {
        addEdge("A", "B", "e1");
        addEdge("A", "C", "e2");
        addEdge("D", "E", "e3");
        addEdge("D", "F", "e4");

        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byStructuralEquivalence();

        assertTrue(result.getMergedGroupCount() > 0);
    }

    @Test
    public void testLargestSupernodeSize() {
        for (String leaf : new String[]{"B", "C", "D", "E"}) {
            addEdge("A", leaf, "e_" + leaf);
        }

        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byStructuralEquivalence();

        assertEquals(4, result.getLargestSupernodeSize());
    }

    @Test
    public void testAverageSupernodeSize() {
        for (String leaf : new String[]{"B", "C", "D", "E"}) {
            addEdge("A", leaf, "e_" + leaf);
        }

        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byStructuralEquivalence();

        // 5 nodes → 2 supernodes → avg 2.5
        assertEquals(2.5, result.getAverageSupernodeSize(), 0.001);
    }

    @Test
    public void testGetMembersOf() {
        addEdge("A", "B", "e1");
        addEdge("A", "C", "e2");

        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byStructuralEquivalence();

        String snB = result.getSupernodeOf("B");
        List<String> members = result.getMembersOf(snB);
        assertTrue(members.contains("B"));
        assertTrue(members.contains("C"));
    }

    @Test
    public void testGetMembersOf_unknown() {
        graph.addVertex("A");
        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byStructuralEquivalence();

        assertTrue(result.getMembersOf("nonexistent").isEmpty());
    }

    @Test
    public void testGetSupernodeOf_unknown() {
        graph.addVertex("A");
        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byStructuralEquivalence();

        assertNull(result.getSupernodeOf("nonexistent"));
    }

    @Test
    public void testGetSummary() {
        addEdge("A", "B", "e1");
        addEdge("A", "C", "e2");

        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byStructuralEquivalence();

        String summary = result.getSummary();
        assertTrue(summary.contains("Strategy"));
        assertTrue(summary.contains("Original"));
        assertTrue(summary.contains("Compressed"));
        assertTrue(summary.contains("Node reduction"));
    }

    @Test
    public void testToCSV() {
        addEdge("A", "B", "e1");
        addEdge("A", "C", "e2");

        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byStructuralEquivalence();

        String csv = result.toCSV();
        assertTrue(csv.startsWith("original_node,supernode,group_size\n"));
        assertTrue(csv.contains("A,"));
        assertTrue(csv.contains("B,"));
        assertTrue(csv.contains("C,"));
    }

    @Test
    public void testGetStrategy() {
        graph.addVertex("A");
        GraphCompressor compressor = new GraphCompressor(graph);

        assertEquals("structural_equivalence",
                compressor.byStructuralEquivalence().getStrategy());
        assertTrue(compressor.byNeighborhoodSimilarity(0.5).getStrategy()
                .contains("neighborhood_similarity"));
        assertTrue(compressor.byDegree(3).getStrategy().contains("degree"));
        assertEquals("attribute",
                compressor.byAttribute(v -> "x").getStrategy());
    }

    // ── Compressibility Report ──────────────────────────────────────

    @Test
    public void testCompressibilityReport() {
        addEdge("A", "B", "e1");
        addEdge("A", "C", "e2");
        addEdge("B", "C", "e3");
        addEdge("C", "D", "e4");

        GraphCompressor compressor = new GraphCompressor(graph);
        String report = compressor.compressibilityReport();

        assertTrue(report.contains("Compressibility Report"));
        assertTrue(report.contains("Structural Equivalence"));
        assertTrue(report.contains("Neighborhood Sim"));
        assertTrue(report.contains("Exact Degree"));
    }

    // ── Edge Cases ──────────────────────────────────────────────────

    @Test
    public void testDisconnectedGraph() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        // No edges, all isolated

        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byStructuralEquivalence();

        // All have empty neighbor sets → all equivalent → 1 supernode
        assertEquals(1, result.getCompressedNodeCount());
    }

    @Test
    public void testCompleteGraph() {
        String[] nodes = {"A", "B", "C", "D"};
        int edgeIdx = 0;
        for (int i = 0; i < nodes.length; i++) {
            for (int j = i + 1; j < nodes.length; j++) {
                addEdge(nodes[i], nodes[j], "e" + edgeIdx++);
            }
        }

        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byExactDegree();

        // All have degree 3 → 1 supernode
        assertEquals(1, result.getCompressedNodeCount());
    }

    @Test
    public void testSelfLoopIgnored() {
        addEdge("A", "B", "e1");
        // Self-loops not typically supported in UndirectedSparseGraph
        // but the compressor should handle whatever the graph gives it

        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byStructuralEquivalence();
        assertNotNull(result);
    }

    @Test
    public void testLargeStarGraph() {
        for (int i = 0; i < 100; i++) {
            addEdge("center", "leaf" + i, "e" + i);
        }

        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byStructuralEquivalence();

        // All leaves equivalent → 2 supernodes
        assertEquals(2, result.getCompressedNodeCount());
        assertEquals(99.0, result.getNodeReductionPercent(), 1.0);
    }

    @Test
    public void testQuotientGraphHasEdges() {
        // Two groups connected by edges
        addEdge("A", "B", "e1");
        addEdge("A", "C", "e2");
        addEdge("D", "B", "e3");
        addEdge("D", "C", "e4");

        Map<String, String> labels = new HashMap<>();
        labels.put("A", "g1");
        labels.put("D", "g1");
        labels.put("B", "g2");
        labels.put("C", "g2");

        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byAttribute(labels::get);

        assertEquals(2, result.getCompressedNodeCount());
        assertEquals(1, result.getCompressedEdgeCount());
    }

    @Test
    public void testEdgeReductionPercent_noEdges() {
        graph.addVertex("A");
        GraphCompressor compressor = new GraphCompressor(graph);
        GraphCompressor.CompressionResult result = compressor.byStructuralEquivalence();
        assertEquals(0.0, result.getEdgeReductionPercent(), 0.001);
    }

    // ── Helper ──────────────────────────────────────────────────────

    private void addEdge(String v1, String v2, String id) {
        edge e = new edge("test", v1, v2);
        e.setLabel(id);
        graph.addEdge(e, v1, v2);
    }
}
