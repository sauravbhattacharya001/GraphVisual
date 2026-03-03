package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link BipartiteAnalyzer}.
 */
public class BipartiteAnalyzerTest {

    private Graph<String, edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, edge>();
    }

    private edge addEdge(String v1, String v2) {
        edge e = new edge("f", v1, v2);
        e.setWeight(1.0f);
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        graph.addEdge(e, v1, v2);
        return e;
    }

    // ═══════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════

    @Test(expected = IllegalArgumentException.class)
    public void nullGraphThrows() {
        new BipartiteAnalyzer(null);
    }

    // ═══════════════════════════════════════
    // Empty graph
    // ═══════════════════════════════════════

    @Test
    public void emptyGraphIsBipartite() {
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        assertTrue(ba.isBipartite());
    }

    @Test
    public void emptyGraphMatchingIsEmpty() {
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        assertEquals(0, ba.getMatchingSize());
    }

    // ═══════════════════════════════════════
    // Single vertex
    // ═══════════════════════════════════════

    @Test
    public void singleVertexIsBipartite() {
        graph.addVertex("A");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        assertTrue(ba.isBipartite());
        assertEquals(0, ba.getMatchingSize());
    }

    // ═══════════════════════════════════════
    // Single edge (trivial bipartite)
    // ═══════════════════════════════════════

    @Test
    public void singleEdgeIsBipartite() {
        addEdge("A", "B");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        assertTrue(ba.isBipartite());
    }

    @Test
    public void singleEdgeMatchingSize() {
        addEdge("A", "B");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        assertEquals(1, ba.getMatchingSize());
    }

    @Test
    public void singleEdgePerfectMatching() {
        addEdge("A", "B");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        assertTrue(ba.hasPerfectMatching());
    }

    @Test
    public void singleEdgePartitions() {
        addEdge("A", "B");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        assertEquals(1, ba.getLeftPartition().size());
        assertEquals(1, ba.getRightPartition().size());
    }

    // ═══════════════════════════════════════
    // Triangle (not bipartite)
    // ═══════════════════════════════════════

    @Test
    public void triangleIsNotBipartite() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        assertFalse(ba.isBipartite());
    }

    @Test
    public void triangleHasOddCycle() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        assertNotNull(ba.getOddCycle());
        assertTrue(ba.getOddCycle().size() >= 3);
    }

    @Test(expected = IllegalStateException.class)
    public void matchingOnNonBipartiteThrows() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        new BipartiteAnalyzer(graph).compute().getMaximumMatching();
    }

    @Test(expected = IllegalStateException.class)
    public void vertexCoverOnNonBipartiteThrows() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        new BipartiteAnalyzer(graph).compute().getMinimumVertexCover();
    }

    @Test(expected = IllegalStateException.class)
    public void independentSetOnNonBipartiteThrows() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        new BipartiteAnalyzer(graph).compute().getMaximumIndependentSet();
    }

    // ═══════════════════════════════════════
    // Even cycle (bipartite)
    // ═══════════════════════════════════════

    @Test
    public void evenCycleIsBipartite() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "A");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        assertTrue(ba.isBipartite());
    }

    @Test
    public void evenCyclePerfectMatching() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "A");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        assertTrue(ba.hasPerfectMatching());
        assertEquals(2, ba.getMatchingSize());
    }

    @Test
    public void evenCyclePartitionBalance() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "A");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        assertEquals(1.0, ba.getPartitionBalance(), 0.001);
    }

    // ═══════════════════════════════════════
    // Complete bipartite K(2,3)
    // ═══════════════════════════════════════

    @Test
    public void completeBipartiteK23() {
        // Left: L1, L2. Right: R1, R2, R3
        addEdge("L1", "R1"); addEdge("L1", "R2"); addEdge("L1", "R3");
        addEdge("L2", "R1"); addEdge("L2", "R2"); addEdge("L2", "R3");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        assertTrue(ba.isBipartite());
        assertEquals(2, ba.getMatchingSize()); // min(2,3)
    }

    @Test
    public void completeBipartiteK23NoPerfectMatching() {
        addEdge("L1", "R1"); addEdge("L1", "R2"); addEdge("L1", "R3");
        addEdge("L2", "R1"); addEdge("L2", "R2"); addEdge("L2", "R3");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        assertFalse(ba.hasPerfectMatching());
    }

    @Test
    public void completeBipartiteK23VertexCover() {
        addEdge("L1", "R1"); addEdge("L1", "R2"); addEdge("L1", "R3");
        addEdge("L2", "R1"); addEdge("L2", "R2"); addEdge("L2", "R3");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        List<String> cover = ba.getMinimumVertexCover();
        assertEquals(2, cover.size()); // König: |cover| = |matching|
    }

    @Test
    public void completeBipartiteK23IndependentSet() {
        addEdge("L1", "R1"); addEdge("L1", "R2"); addEdge("L1", "R3");
        addEdge("L2", "R1"); addEdge("L2", "R2"); addEdge("L2", "R3");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        List<String> indep = ba.getMaximumIndependentSet();
        assertEquals(3, indep.size()); // 5 - 2
    }

    @Test
    public void completeBipartiteK23Density() {
        addEdge("L1", "R1"); addEdge("L1", "R2"); addEdge("L1", "R3");
        addEdge("L2", "R1"); addEdge("L2", "R2"); addEdge("L2", "R3");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        assertEquals(1.0, ba.getBipartiteDensity(), 0.001);
    }

    // ═══════════════════════════════════════
    // Complete bipartite K(3,3) — perfect matching
    // ═══════════════════════════════════════

    @Test
    public void completeBipartiteK33PerfectMatching() {
        addEdge("L1", "R1"); addEdge("L1", "R2"); addEdge("L1", "R3");
        addEdge("L2", "R1"); addEdge("L2", "R2"); addEdge("L2", "R3");
        addEdge("L3", "R1"); addEdge("L3", "R2"); addEdge("L3", "R3");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        assertTrue(ba.hasPerfectMatching());
        assertEquals(3, ba.getMatchingSize());
    }

    @Test
    public void completeBipartiteK33MatchingCoverage() {
        addEdge("L1", "R1"); addEdge("L1", "R2"); addEdge("L1", "R3");
        addEdge("L2", "R1"); addEdge("L2", "R2"); addEdge("L2", "R3");
        addEdge("L3", "R1"); addEdge("L3", "R2"); addEdge("L3", "R3");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        assertEquals(1.0, ba.getMatchingCoverage(), 0.001);
    }

    // ═══════════════════════════════════════
    // Path graph (bipartite)
    // ═══════════════════════════════════════

    @Test
    public void pathGraphBipartite() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "E");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        assertTrue(ba.isBipartite());
        assertEquals(2, ba.getMatchingSize());
    }

    // ═══════════════════════════════════════
    // Star graph
    // ═══════════════════════════════════════

    @Test
    public void starGraphBipartite() {
        addEdge("center", "A");
        addEdge("center", "B");
        addEdge("center", "C");
        addEdge("center", "D");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        assertTrue(ba.isBipartite());
        assertEquals(1, ba.getMatchingSize());
    }

    @Test
    public void starGraphVertexCover() {
        addEdge("center", "A");
        addEdge("center", "B");
        addEdge("center", "C");
        addEdge("center", "D");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        List<String> cover = ba.getMinimumVertexCover();
        assertEquals(1, cover.size());
        assertEquals("center", cover.get(0));
    }

    // ═══════════════════════════════════════
    // Disconnected components (both bipartite)
    // ═══════════════════════════════════════

    @Test
    public void disconnectedBipartiteComponents() {
        addEdge("A", "B");
        addEdge("C", "D");
        addEdge("E", "F");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        assertTrue(ba.isBipartite());
        assertEquals(3, ba.getMatchingSize());
    }

    // ═══════════════════════════════════════
    // Disconnected with one non-bipartite component
    // ═══════════════════════════════════════

    @Test
    public void disconnectedOneNonBipartite() {
        addEdge("A", "B"); // bipartite component
        addEdge("C", "D");
        addEdge("D", "E");
        addEdge("E", "C"); // triangle
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        assertFalse(ba.isBipartite());
    }

    // ═══════════════════════════════════════
    // Isolated vertices
    // ═══════════════════════════════════════

    @Test
    public void isolatedVerticesAreBipartite() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        assertTrue(ba.isBipartite());
        assertEquals(0, ba.getMatchingSize());
    }

    // ═══════════════════════════════════════
    // Odd cycle K5
    // ═══════════════════════════════════════

    @Test
    public void pentagon_notBipartite() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "E");
        addEdge("E", "A");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        assertFalse(ba.isBipartite());
    }

    // ═══════════════════════════════════════
    // König's theorem verification
    // ═══════════════════════════════════════

    @Test
    public void konigTheoremHolds() {
        // Build a larger bipartite graph
        addEdge("L1", "R1"); addEdge("L1", "R2");
        addEdge("L2", "R2"); addEdge("L2", "R3");
        addEdge("L3", "R3");
        addEdge("L4", "R1"); addEdge("L4", "R4");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        assertTrue(ba.isBipartite());
        // König: |max matching| = |min vertex cover|
        assertEquals(ba.getMatchingSize(), ba.getMinimumVertexCover().size());
    }

    @Test
    public void vertexCoverPlusIndependentSetEqualsN() {
        addEdge("L1", "R1"); addEdge("L1", "R2");
        addEdge("L2", "R2"); addEdge("L2", "R3");
        addEdge("L3", "R3");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        int n = graph.getVertexCount();
        assertEquals(n, ba.getMinimumVertexCover().size() + ba.getMaximumIndependentSet().size());
    }

    // ═══════════════════════════════════════
    // Vertex cover validity
    // ═══════════════════════════════════════

    @Test
    public void vertexCoverCoversAllEdges() {
        addEdge("L1", "R1"); addEdge("L1", "R2");
        addEdge("L2", "R2"); addEdge("L2", "R3");
        addEdge("L3", "R1"); addEdge("L3", "R3");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        List<String> cover = ba.getMinimumVertexCover();
        java.util.Set<String> coverSet = new java.util.HashSet<String>(cover);
        // Every edge must have at least one endpoint in the cover
        for (edge e : graph.getEdges()) {
            assertTrue("Edge not covered: " + e.getVertex1() + "-" + e.getVertex2(),
                    coverSet.contains(e.getVertex1()) || coverSet.contains(e.getVertex2()));
        }
    }

    // ═══════════════════════════════════════
    // Independent set validity
    // ═══════════════════════════════════════

    @Test
    public void independentSetHasNoEdges() {
        addEdge("L1", "R1"); addEdge("L1", "R2");
        addEdge("L2", "R2"); addEdge("L2", "R3");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        List<String> indep = ba.getMaximumIndependentSet();
        java.util.Set<String> indepSet = new java.util.HashSet<String>(indep);
        // No two vertices in the independent set should be adjacent
        for (String v : indep) {
            for (String n : graph.getNeighbors(v)) {
                assertFalse("Independent set has adjacent vertices: " + v + "-" + n,
                        indepSet.contains(n));
            }
        }
    }

    // ═══════════════════════════════════════
    // Coloring consistency
    // ═══════════════════════════════════════

    @Test
    public void coloringIsValid() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        Map<String, Integer> coloring = ba.getColoring();
        for (edge e : graph.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            assertNotEquals("Adjacent vertices have same color: " + v1 + "-" + v2,
                    coloring.get(v1), coloring.get(v2));
        }
    }

    // ═══════════════════════════════════════
    // Idempotent compute
    // ═══════════════════════════════════════

    @Test
    public void computeIsIdempotent() {
        addEdge("A", "B");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph);
        ba.compute();
        ba.compute(); // second call should be no-op
        assertTrue(ba.isBipartite());
    }

    // ═══════════════════════════════════════
    // Result object
    // ═══════════════════════════════════════

    @Test
    public void resultObjectBipartite() {
        addEdge("L1", "R1"); addEdge("L1", "R2");
        addEdge("L2", "R2");
        BipartiteAnalyzer.BipartiteResult result =
                new BipartiteAnalyzer(graph).compute().getResult();
        assertTrue(result.isBipartite());
        assertEquals(4, result.getVertexCount());
        assertEquals(3, result.getEdgeCount());
        assertNotNull(result.getMatching());
        assertNotNull(result.getVertexCover());
        assertNotNull(result.getIndependentSet());
        assertNull(result.getOddCycle());
    }

    @Test
    public void resultObjectNonBipartite() {
        addEdge("A", "B"); addEdge("B", "C"); addEdge("C", "A");
        BipartiteAnalyzer.BipartiteResult result =
                new BipartiteAnalyzer(graph).compute().getResult();
        assertFalse(result.isBipartite());
        assertNotNull(result.getOddCycle());
        assertNull(result.getMatching());
    }

    // ═══════════════════════════════════════
    // Summary output
    // ═══════════════════════════════════════

    @Test
    public void summaryBipartite() {
        addEdge("A", "B");
        String summary = new BipartiteAnalyzer(graph).compute().getSummary();
        assertTrue(summary.contains("Bipartite: YES"));
        assertTrue(summary.contains("Maximum Matching"));
    }

    @Test
    public void summaryNonBipartite() {
        addEdge("A", "B"); addEdge("B", "C"); addEdge("C", "A");
        String summary = new BipartiteAnalyzer(graph).compute().getSummary();
        assertTrue(summary.contains("Bipartite: NO"));
    }

    // ═══════════════════════════════════════
    // Matching edge equals/hashCode
    // ═══════════════════════════════════════

    @Test
    public void matchingEdgeEquality() {
        BipartiteAnalyzer.MatchingEdge e1 = new BipartiteAnalyzer.MatchingEdge("A", "B");
        BipartiteAnalyzer.MatchingEdge e2 = new BipartiteAnalyzer.MatchingEdge("A", "B");
        BipartiteAnalyzer.MatchingEdge e3 = new BipartiteAnalyzer.MatchingEdge("A", "C");
        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
        assertNotEquals(e1, e3);
    }

    @Test
    public void matchingEdgeToString() {
        BipartiteAnalyzer.MatchingEdge e = new BipartiteAnalyzer.MatchingEdge("X", "Y");
        assertEquals("X — Y", e.toString());
    }

    // ═══════════════════════════════════════
    // Large bipartite graph
    // ═══════════════════════════════════════

    @Test
    public void largeBipartiteGraph() {
        // K(5,5) complete bipartite
        for (int i = 1; i <= 5; i++) {
            for (int j = 1; j <= 5; j++) {
                addEdge("L" + i, "R" + j);
            }
        }
        BipartiteAnalyzer ba = new BipartiteAnalyzer(graph).compute();
        assertTrue(ba.isBipartite());
        assertEquals(5, ba.getMatchingSize());
        assertTrue(ba.hasPerfectMatching());
        assertEquals(5, ba.getMinimumVertexCover().size());
        assertEquals(5, ba.getMaximumIndependentSet().size());
    }
}
