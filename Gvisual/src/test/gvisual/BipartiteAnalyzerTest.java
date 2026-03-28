package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link BipartiteAnalyzer} — bipartiteness detection, 2-coloring,
 * Hopcroft–Karp maximum matching, König's minimum vertex cover, maximum
 * independent set, and odd cycle detection.
 */
public class BipartiteAnalyzerTest {

    // ── Helpers ─────────────────────────────────────────────────────

    private static Edge addEdge(Graph<String, Edge> g, String v1, String v2) {
        g.addVertex(v1);
        g.addVertex(v2);
        Edge e = new Edge("f", v1, v2);
        g.addEdge(e, v1, v2);
        return e;
    }

    private static Graph<String, Edge> bipartiteGraph() {
        // K_{3,3}: complete bipartite with left={A,B,C}, right={X,Y,Z}
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        for (String l : new String[]{"A", "B", "C"}) {
            for (String r : new String[]{"X", "Y", "Z"}) {
                addEdge(g, l, r);
            }
        }
        return g;
    }

    private static Graph<String, Edge> oddCycleGraph() {
        // Triangle: A-B-C-A (odd cycle, not bipartite)
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        return g;
    }

    // ── Constructor ─────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsNull() {
        new BipartiteAnalyzer(null);
    }

    // ── Empty graph ─────────────────────────────────────────────────

    @Test
    public void emptyGraphIsBipartite() {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        BipartiteAnalyzer ba = new BipartiteAnalyzer(g).compute();

        assertTrue(ba.isBipartite());
        assertTrue(ba.getLeftPartition().isEmpty());
        assertTrue(ba.getRightPartition().isEmpty());
        assertNull(ba.getOddCycle());
    }

    // ── Single vertex ───────────────────────────────────────────────

    @Test
    public void singleVertexIsBipartite() {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        g.addVertex("A");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(g).compute();

        assertTrue(ba.isBipartite());
        // Single vertex in one partition
        assertEquals(1, ba.getLeftPartition().size() + ba.getRightPartition().size());
    }

    // ── Single edge (trivially bipartite) ───────────────────────────

    @Test
    public void singleEdgeIsBipartite() {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        addEdge(g, "A", "B");
        BipartiteAnalyzer ba = new BipartiteAnalyzer(g).compute();

        assertTrue(ba.isBipartite());
        assertNull(ba.getOddCycle());

        // Both vertices accounted for across partitions
        Set<String> all = new HashSet<String>(ba.getLeftPartition());
        all.addAll(ba.getRightPartition());
        assertEquals(new HashSet<String>(Arrays.asList("A", "B")), all);
    }

    // ── K_{3,3} bipartite ───────────────────────────────────────────

    @Test
    public void completeBipartiteGraphDetected() {
        BipartiteAnalyzer ba = new BipartiteAnalyzer(bipartiteGraph()).compute();
        assertTrue(ba.isBipartite());

        // Partitions should cover all 6 vertices with 3 in each
        assertEquals(6, ba.getLeftPartition().size() + ba.getRightPartition().size());
    }

    @Test
    public void coloringIsValid() {
        BipartiteAnalyzer ba = new BipartiteAnalyzer(bipartiteGraph()).compute();
        Map<String, Integer> coloring = ba.getColoring();

        // No edge connects same-color vertices
        Graph<String, Edge> g = bipartiteGraph();
        for (Edge e : g.getEdges()) {
            String v1 = g.getEndpoints(e).getFirst();
            String v2 = g.getEndpoints(e).getSecond();
            assertNotEquals("Adjacent vertices must have different colors",
                    coloring.get(v1), coloring.get(v2));
        }
    }

    // ── Odd cycle (non-bipartite) ───────────────────────────────────

    @Test
    public void triangleIsNotBipartite() {
        BipartiteAnalyzer ba = new BipartiteAnalyzer(oddCycleGraph()).compute();

        assertFalse(ba.isBipartite());
        assertNotNull(ba.getOddCycle());
        // Odd cycle should have odd length
        assertTrue(ba.getOddCycle().size() % 2 == 1);
    }

    @Test
    public void pentagonIsNotBipartite() {
        // 5-cycle: A-B-C-D-E-A
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        addEdge(g, "D", "E");
        addEdge(g, "E", "A");

        BipartiteAnalyzer ba = new BipartiteAnalyzer(g).compute();
        assertFalse(ba.isBipartite());
        assertNotNull(ba.getOddCycle());
    }

    // ── Even cycle (bipartite) ──────────────────────────────────────

    @Test
    public void evenCycleIsBipartite() {
        // 4-cycle: A-B-C-D-A
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        addEdge(g, "D", "A");

        BipartiteAnalyzer ba = new BipartiteAnalyzer(g).compute();
        assertTrue(ba.isBipartite());
        assertEquals(2, ba.getLeftPartition().size());
        assertEquals(2, ba.getRightPartition().size());
    }

    // ── Disconnected components ─────────────────────────────────────

    @Test
    public void disconnectedBipartiteComponents() {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        // Component 1: edge A-B
        addEdge(g, "A", "B");
        // Component 2: edge C-D
        addEdge(g, "C", "D");
        // Isolated vertex
        g.addVertex("E");

        BipartiteAnalyzer ba = new BipartiteAnalyzer(g).compute();
        assertTrue(ba.isBipartite());
        assertEquals(5, ba.getColoring().size());
    }

    @Test
    public void disconnectedWithOneOddCycle() {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        // Component 1: bipartite edge
        addEdge(g, "A", "B");
        // Component 2: triangle (non-bipartite)
        addEdge(g, "X", "Y");
        addEdge(g, "Y", "Z");
        addEdge(g, "Z", "X");

        BipartiteAnalyzer ba = new BipartiteAnalyzer(g).compute();
        assertFalse(ba.isBipartite());
    }

    // ── Idempotent compute ──────────────────────────────────────────

    @Test
    public void computeIsIdempotent() {
        BipartiteAnalyzer ba = new BipartiteAnalyzer(bipartiteGraph());
        ba.compute();
        boolean first = ba.isBipartite();
        ba.compute(); // should be no-op
        assertEquals(first, ba.isBipartite());
    }

    // ── Maximum Matching (Hopcroft-Karp) ────────────────────────────

    @Test
    public void maximumMatchingOnK33() {
        BipartiteAnalyzer ba = new BipartiteAnalyzer(bipartiteGraph()).compute();

        List<BipartiteAnalyzer.MatchingEdge> matching = ba.getMaximumMatching();
        // K_{3,3} has perfect matching of size 3
        assertEquals(3, matching.size());

        // All matched vertices should be distinct
        Set<String> matchedLeft = new HashSet<String>();
        Set<String> matchedRight = new HashSet<String>();
        for (BipartiteAnalyzer.MatchingEdge me : matching) {
            assertTrue("Duplicate left vertex in matching", matchedLeft.add(me.getLeft()));
            assertTrue("Duplicate right vertex in matching", matchedRight.add(me.getRight()));
        }
    }

    @Test
    public void matchingSizeOnPath() {
        // Path: A-B-C-D (bipartite, max matching = 2)
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");

        BipartiteAnalyzer ba = new BipartiteAnalyzer(g).compute();
        assertEquals(2, ba.getMatchingSize());
    }

    @Test(expected = IllegalStateException.class)
    public void matchingThrowsOnNonBipartite() {
        new BipartiteAnalyzer(oddCycleGraph()).compute().getMaximumMatching();
    }

    // ── Minimum Vertex Cover (König's theorem) ──────────────────────

    @Test
    public void minimumVertexCoverOnK33() {
        BipartiteAnalyzer ba = new BipartiteAnalyzer(bipartiteGraph()).compute();

        List<String> cover = ba.getMinimumVertexCover();
        // König: |min vertex cover| = |max matching| = 3
        assertEquals(3, cover.size());

        // Verify cover: every edge has at least one endpoint in cover
        Set<String> coverSet = new HashSet<String>(cover);
        Graph<String, Edge> g = bipartiteGraph();
        for (Edge e : g.getEdges()) {
            String v1 = g.getEndpoints(e).getFirst();
            String v2 = g.getEndpoints(e).getSecond();
            assertTrue("Edge " + v1 + "-" + v2 + " not covered",
                    coverSet.contains(v1) || coverSet.contains(v2));
        }
    }

    @Test(expected = IllegalStateException.class)
    public void vertexCoverThrowsOnNonBipartite() {
        new BipartiteAnalyzer(oddCycleGraph()).compute().getMinimumVertexCover();
    }

    // ── Maximum Independent Set ─────────────────────────────────────

    @Test
    public void maxIndependentSetOnK33() {
        BipartiteAnalyzer ba = new BipartiteAnalyzer(bipartiteGraph()).compute();

        List<String> indep = ba.getMaximumIndependentSet();
        // |max independent set| = |V| - |min vertex cover| = 6 - 3 = 3
        assertEquals(3, indep.size());

        // No two vertices in independent set should be adjacent
        Set<String> indepSet = new HashSet<String>(indep);
        Graph<String, Edge> g = bipartiteGraph();
        for (String v : indep) {
            for (String n : g.getNeighbors(v)) {
                assertFalse("Independent set contains adjacent vertices " + v + " and " + n,
                        indepSet.contains(n));
            }
        }
    }

    // ── Star graph ──────────────────────────────────────────────────

    @Test
    public void starGraphIsBipartite() {
        // Star: center connected to 5 leaves
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        for (int i = 1; i <= 5; i++) {
            addEdge(g, "center", "leaf" + i);
        }

        BipartiteAnalyzer ba = new BipartiteAnalyzer(g).compute();
        assertTrue(ba.isBipartite());
        // Max matching = 5 (one per leaf), but limited by min(left, right)
        // center is alone in one partition, 5 leaves in the other
        // so max matching = 1 (center can only match one leaf)
        assertEquals(1, ba.getMatchingSize());
        // Min vertex cover = 1 (just the center covers all edges)
        assertEquals(1, ba.getMinimumVertexCover().size());
        assertTrue(ba.getMinimumVertexCover().contains("center"));
    }

    // ── MatchingEdge equals/hashCode ────────────────────────────────

    @Test
    public void matchingEdgeEquality() {
        BipartiteAnalyzer.MatchingEdge e1 = new BipartiteAnalyzer.MatchingEdge("A", "X");
        BipartiteAnalyzer.MatchingEdge e2 = new BipartiteAnalyzer.MatchingEdge("A", "X");
        BipartiteAnalyzer.MatchingEdge e3 = new BipartiteAnalyzer.MatchingEdge("A", "Y");

        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
        assertNotEquals(e1, e3);
        assertNotEquals(e1, "not an edge");
        assertEquals("A — X", e1.toString());
    }
}
