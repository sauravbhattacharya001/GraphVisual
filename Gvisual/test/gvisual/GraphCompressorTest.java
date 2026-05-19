package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for {@link GraphCompressor} - exercises every compression strategy
 * (structural equivalence, neighborhood similarity, degree binning, exact
 * degree, attribute, k-hop locality) plus the {@link
 * GraphCompressor.CompressionResult} accessors, the compressibility report,
 * and CSV export.
 */
public class GraphCompressorTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, Edge>();
    }

    private void addEdge(String a, String b) {
        graph.addVertex(a);
        graph.addVertex(b);
        Edge e = new Edge("f", a, b);
        e.setWeight(1.0f);
        graph.addEdge(e, a, b);
    }

    private void addEdge(String a, String b, float weight) {
        graph.addVertex(a);
        graph.addVertex(b);
        Edge e = new Edge("f", a, b);
        e.setWeight(weight);
        graph.addEdge(e, a, b);
    }

    // ── Constructor / argument validation ────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void nullGraphRejected() {
        new GraphCompressor(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void thresholdBelowZeroRejected() {
        new GraphCompressor(graph).byNeighborhoodSimilarity(-0.1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void thresholdAboveOneRejected() {
        new GraphCompressor(graph).byNeighborhoodSimilarity(1.1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void binSizeZeroRejected() {
        new GraphCompressor(graph).byDegree(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullAttributeFunctionRejected() {
        new GraphCompressor(graph).byAttribute(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptySeedsRejected() {
        new GraphCompressor(graph).byKHopLocality(Collections.<String>emptyList(), 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullSeedsRejected() {
        new GraphCompressor(graph).byKHopLocality(null, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void kHopZeroRejected() {
        new GraphCompressor(graph).byKHopLocality(Arrays.asList("A"), 0);
    }

    // ── Empty / trivial graphs ───────────────────────────────────────

    @Test
    public void emptyGraphProducesEmptyResult() {
        GraphCompressor.CompressionResult r =
                new GraphCompressor(graph).byStructuralEquivalence();
        assertEquals(0, r.getOriginalNodeCount());
        assertEquals(0, r.getCompressedNodeCount());
        assertEquals(0, r.getCompressedEdgeCount());
        assertEquals(1.0, r.getCompressionRatio(), 1e-9);
        assertEquals(0.0, r.getNodeReductionPercent(), 1e-9);
        assertEquals(0.0, r.getEdgeReductionPercent(), 1e-9);
        assertEquals(0, r.getMergedGroupCount());
        assertEquals(0, r.getLargestSupernodeSize());
        assertEquals(0.0, r.getAverageSupernodeSize(), 1e-9);
    }

    @Test
    public void singletonNodeIsItsOwnSupernode() {
        graph.addVertex("A");
        GraphCompressor.CompressionResult r =
                new GraphCompressor(graph).byStructuralEquivalence();
        assertEquals(1, r.getCompressedNodeCount());
        assertEquals("A", r.getSupernodeOf("A"));
        assertEquals(Arrays.asList("A"), r.getMembersOf("A"));
        // No edges in or out
        assertEquals(0, r.getCompressedEdgeCount());
    }

    // ── Structural equivalence ───────────────────────────────────────

    /**
     * In K_{2,3} with parts {A,B} and {X,Y,Z}, A and B share neighbors
     * {X,Y,Z}; X, Y, Z share neighbors {A,B}. Structural equivalence
     * should collapse to exactly 2 supernodes joined by 1 edge.
     */
    @Test
    public void structuralEquivalenceMergesK23() {
        for (String left : new String[]{"A", "B"}) {
            for (String right : new String[]{"X", "Y", "Z"}) {
                addEdge(left, right);
            }
        }
        GraphCompressor.CompressionResult r =
                new GraphCompressor(graph).byStructuralEquivalence();

        assertEquals(5, r.getOriginalNodeCount());
        assertEquals(6, r.getOriginalEdgeCount());
        assertEquals(2, r.getCompressedNodeCount());
        assertEquals(1, r.getCompressedEdgeCount());
        assertEquals(2, r.getMergedGroupCount());
        assertEquals(3, r.getLargestSupernodeSize());
        // Both A and B map to the same supernode
        assertEquals(r.getSupernodeOf("A"), r.getSupernodeOf("B"));
        assertNotEquals(r.getSupernodeOf("A"), r.getSupernodeOf("X"));
        // X, Y, Z all together
        String rightSuper = r.getSupernodeOf("X");
        assertEquals(rightSuper, r.getSupernodeOf("Y"));
        assertEquals(rightSuper, r.getSupernodeOf("Z"));
        // Compression ratio = 2/5
        assertEquals(0.4, r.getCompressionRatio(), 1e-9);
        assertEquals(60.0, r.getNodeReductionPercent(), 1e-9);
        // Edge reduction 1/6 remaining → 83.33%
        assertEquals((1.0 - 1.0/6.0) * 100.0, r.getEdgeReductionPercent(), 1e-9);

        // Aggregated superedge weight equals sum of original edge weights
        Graph<String, Edge> q = r.getCompressedGraph();
        Edge se = q.getEdges().iterator().next();
        assertEquals(6.0f, se.getWeight(), 1e-6f);
        assertTrue(r.getStrategy().contains("structural_equivalence"));
    }

    @Test
    public void structurallyDistinctNodesNotMerged() {
        // Path A-B-C-D: every node has a distinct neighbor set
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        GraphCompressor.CompressionResult r =
                new GraphCompressor(graph).byStructuralEquivalence();
        assertEquals(4, r.getCompressedNodeCount());
        assertEquals(3, r.getCompressedEdgeCount());
        assertEquals(0, r.getMergedGroupCount());
        assertEquals(1, r.getLargestSupernodeSize());
    }

    // ── Neighborhood similarity ──────────────────────────────────────

    @Test
    public void neighborhoodSimilarityAtZeroMergesEverythingNonEmpty() {
        addEdge("A", "X");
        addEdge("B", "Y");
        addEdge("C", "Z");
        // Threshold 0 ⇒ any non-disjoint pair merges; greedy from low degree
        // up. All vertices have degree 1; the algorithm sweeps them into one
        // group (all jaccard=0 against the first vertex, threshold 0 ⇒ merge).
        GraphCompressor.CompressionResult r =
                new GraphCompressor(graph).byNeighborhoodSimilarity(0.0);
        // Everyone collapses since every Jaccard >= 0.
        assertEquals(1, r.getCompressedNodeCount());
        // No superedges (all originals are between merged-into-same group)
        assertEquals(0, r.getCompressedEdgeCount());
    }

    @Test
    public void neighborhoodSimilarityAtOneEqualsStructuralForIdenticalPairs() {
        // Two pairs with identical neighbor sets
        addEdge("A", "X");
        addEdge("A", "Y");
        addEdge("B", "X");
        addEdge("B", "Y");
        GraphCompressor.CompressionResult r =
                new GraphCompressor(graph).byNeighborhoodSimilarity(1.0);
        // A and B should merge (identical neighbors); X and Y should merge
        assertEquals(r.getSupernodeOf("A"), r.getSupernodeOf("B"));
        assertEquals(r.getSupernodeOf("X"), r.getSupernodeOf("Y"));
        assertEquals(2, r.getCompressedNodeCount());
        assertTrue(r.getStrategy().contains("threshold=1.0"));
    }

    @Test
    public void neighborhoodSimilarityHighThresholdDoesNotForceMerge() {
        addEdge("A", "X");
        addEdge("B", "Y"); // disjoint neighbors with A
        GraphCompressor.CompressionResult r =
                new GraphCompressor(graph).byNeighborhoodSimilarity(0.5);
        assertEquals(4, r.getCompressedNodeCount());
    }

    // ── Degree binning ───────────────────────────────────────────────

    @Test
    public void byDegreeBinsByEqualDegree() {
        // Star: center C has degree 3, leaves L1/L2/L3 have degree 1
        addEdge("C", "L1");
        addEdge("C", "L2");
        addEdge("C", "L3");
        GraphCompressor.CompressionResult r =
                new GraphCompressor(graph).byExactDegree();
        // Two bins: degree-1 (L1/L2/L3) and degree-3 (C)
        assertEquals(2, r.getCompressedNodeCount());
        assertEquals(r.getSupernodeOf("L1"), r.getSupernodeOf("L2"));
        assertEquals(r.getSupernodeOf("L1"), r.getSupernodeOf("L3"));
        assertNotEquals(r.getSupernodeOf("C"), r.getSupernodeOf("L1"));
        assertEquals(1, r.getCompressedEdgeCount());
        assertTrue(r.getStrategy().contains("binSize=1"));
    }

    @Test
    public void byDegreeBinSizeCoarsensCorrectly() {
        // Degrees 1, 2, 3, 4, 5 spread across 6 nodes via a small graph
        addEdge("A", "B"); // A:1, B:1
        addEdge("B", "C"); // B:2, C:1
        addEdge("B", "D"); // B:3, D:1
        addEdge("C", "D"); // C:2, D:2
        // Final: A=1, B=3, C=2, D=2
        GraphCompressor.CompressionResult r =
                new GraphCompressor(graph).byDegree(2);
        // bin 0 = degree {0,1} (A); bin 1 = degree {2,3} (B,C,D)
        assertEquals(2, r.getCompressedNodeCount());
        assertEquals(r.getSupernodeOf("B"), r.getSupernodeOf("C"));
        assertEquals(r.getSupernodeOf("B"), r.getSupernodeOf("D"));
        assertNotEquals(r.getSupernodeOf("A"), r.getSupernodeOf("B"));
    }

    // ── Attribute compression ────────────────────────────────────────

    @Test
    public void attributeCompressionGroupsByLabel() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "A");
        final Map<String, String> labels = new HashMap<String, String>();
        labels.put("A", "red");
        labels.put("B", "blue");
        labels.put("C", "red");
        labels.put("D", "blue");

        GraphCompressor.CompressionResult r =
                new GraphCompressor(graph).byAttribute(new java.util.function.Function<String, String>() {
            @Override public String apply(String v) { return labels.get(v); }
        });

        assertEquals(2, r.getCompressedNodeCount());
        assertEquals(r.getSupernodeOf("A"), r.getSupernodeOf("C"));
        assertEquals(r.getSupernodeOf("B"), r.getSupernodeOf("D"));
        // All 4 edges are cross-color, so a single superedge with weight 4
        assertEquals(1, r.getCompressedEdgeCount());
        Edge se = r.getCompressedGraph().getEdges().iterator().next();
        assertEquals(4.0f, se.getWeight(), 1e-6f);
    }

    @Test
    public void attributeNullsBucketedInUnassigned() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        addEdge("A", "B");
        GraphCompressor.CompressionResult r =
                new GraphCompressor(graph).byAttribute(new java.util.function.Function<String, String>() {
            @Override public String apply(String v) { return v.equals("A") ? "x" : null; }
        });
        // A on its own; B+C in __unassigned__
        assertEquals(2, r.getCompressedNodeCount());
        assertEquals(r.getSupernodeOf("B"), r.getSupernodeOf("C"));
    }

    // ── k-hop locality ───────────────────────────────────────────────

    @Test
    public void kHopLocalityCollectsNeighborhood() {
        // Line A-B-C-D-E, seed=B, k=1 ⇒ {A,B,C} merge; D,E remain singletons
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "E");
        GraphCompressor.CompressionResult r =
                new GraphCompressor(graph).byKHopLocality(Arrays.asList("B"), 1);
        assertEquals(r.getSupernodeOf("A"), r.getSupernodeOf("B"));
        assertEquals(r.getSupernodeOf("A"), r.getSupernodeOf("C"));
        assertNotEquals(r.getSupernodeOf("A"), r.getSupernodeOf("D"));
        assertNotEquals(r.getSupernodeOf("D"), r.getSupernodeOf("E"));
        // 1 supernode + 2 singletons = 3 nodes
        assertEquals(3, r.getCompressedNodeCount());
    }

    @Test
    public void kHopLocalitySkipsAlreadyClaimed() {
        // Two seeds whose 1-hop neighborhoods overlap: claim order wins
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        GraphCompressor.CompressionResult r =
                new GraphCompressor(graph).byKHopLocality(Arrays.asList("A", "D"), 1);
        // From A,k=1: {A,B}. From D,k=1: D's neighbor C is unclaimed ⇒ {C,D}.
        assertEquals(r.getSupernodeOf("A"), r.getSupernodeOf("B"));
        assertEquals(r.getSupernodeOf("C"), r.getSupernodeOf("D"));
        assertNotEquals(r.getSupernodeOf("A"), r.getSupernodeOf("C"));
    }

    @Test
    public void kHopLocalityIgnoresUnknownSeed() {
        addEdge("A", "B");
        GraphCompressor.CompressionResult r =
                new GraphCompressor(graph).byKHopLocality(Arrays.asList("Z"), 1);
        // Z is not in graph; A,B become singletons.
        assertEquals(2, r.getCompressedNodeCount());
    }

    // ── Compressibility report ───────────────────────────────────────

    @Test
    public void compressibilityReportContainsAllStrategies() {
        addEdge("A", "X");
        addEdge("B", "X");
        addEdge("A", "Y");
        addEdge("B", "Y");
        String report = new GraphCompressor(graph).compressibilityReport();
        assertTrue(report.contains("Graph Compressibility Report"));
        assertTrue(report.contains("Structural Equivalence"));
        assertTrue(report.contains("Neighborhood Sim (t=0.9)"));
        assertTrue(report.contains("Neighborhood Sim (t=0.3)"));
        assertTrue(report.contains("Exact Degree"));
        assertTrue(report.contains("Degree (bin=2)"));
        assertTrue(report.contains("Degree (bin=5)"));
        assertTrue(report.contains("Degree (bin=10)"));
    }

    // ── Result accessors / serialisation ─────────────────────────────

    @Test
    public void summaryContainsKeyFields() {
        addEdge("A", "X");
        addEdge("B", "X");
        GraphCompressor.CompressionResult r =
                new GraphCompressor(graph).byStructuralEquivalence();
        String s = r.getSummary();
        assertTrue(s.contains("Strategy:"));
        assertTrue(s.contains("Original:"));
        assertTrue(s.contains("Compressed:"));
        assertTrue(s.contains("Compression ratio:"));
        assertTrue(s.contains("Merged groups:"));
    }

    @Test
    public void csvExportListsNodesSorted() {
        addEdge("A", "X");
        addEdge("B", "X");
        GraphCompressor.CompressionResult r =
                new GraphCompressor(graph).byStructuralEquivalence();
        String csv = r.toCSV();
        String[] lines = csv.split("\n");
        assertEquals("original_node,supernode,group_size", lines[0]);
        assertEquals(4, lines.length); // header + 3 nodes
        // Sorted order: A, B, X
        assertTrue(lines[1].startsWith("A,"));
        assertTrue(lines[2].startsWith("B,"));
        assertTrue(lines[3].startsWith("X,"));
    }

    @Test
    public void getMembersOfReturnsEmptyForUnknown() {
        GraphCompressor.CompressionResult r =
                new GraphCompressor(graph).byStructuralEquivalence();
        assertTrue(r.getMembersOf("not-there").isEmpty());
        assertNull(r.getSupernodeOf("not-there"));
    }

    @Test
    public void supernodeMembersMapIsUnmodifiable() {
        graph.addVertex("A");
        GraphCompressor.CompressionResult r =
                new GraphCompressor(graph).byStructuralEquivalence();
        try {
            r.getSupernodeMembers().put("X", Arrays.asList("Y"));
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
        try {
            r.getNodeToSupernode().put("X", "Y");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }

    // ── Edge weight aggregation ──────────────────────────────────────

    @Test
    public void superedgeWeightsSumOriginals() {
        // Two parallel-ish groups: merge via attribute
        addEdge("A", "C", 2.0f);
        addEdge("A", "D", 3.0f);
        addEdge("B", "C", 4.0f);
        addEdge("B", "D", 5.0f);
        final Map<String, String> labels = new HashMap<String, String>();
        labels.put("A", "L"); labels.put("B", "L");
        labels.put("C", "R"); labels.put("D", "R");
        GraphCompressor.CompressionResult r =
                new GraphCompressor(graph).byAttribute(new java.util.function.Function<String, String>() {
            @Override public String apply(String v) { return labels.get(v); }
        });
        assertEquals(1, r.getCompressedEdgeCount());
        Edge se = r.getCompressedGraph().getEdges().iterator().next();
        assertEquals(14.0f, se.getWeight(), 1e-6f);
    }

    @Test
    public void intraGroupEdgesDroppedInQuotient() {
        // Triangle A-B-C all same label ⇒ all merged, no superedges remain
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        final Map<String, String> labels = new HashMap<String, String>();
        labels.put("A", "g"); labels.put("B", "g"); labels.put("C", "g");
        GraphCompressor.CompressionResult r =
                new GraphCompressor(graph).byAttribute(new java.util.function.Function<String, String>() {
            @Override public String apply(String v) { return labels.get(v); }
        });
        assertEquals(1, r.getCompressedNodeCount());
        assertEquals(0, r.getCompressedEdgeCount());
        assertEquals(100.0, r.getEdgeReductionPercent(), 1e-9);
    }
}
