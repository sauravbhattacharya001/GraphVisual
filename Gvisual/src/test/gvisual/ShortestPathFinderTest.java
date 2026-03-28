package gvisual;

import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link ShortestPathFinder} — BFS shortest path, Dijkstra
 * weighted shortest path, reachability, and connectivity.
 */
public class ShortestPathFinderTest {

    // ── Helpers ─────────────────────────────────────────────────────

    private static edge addEdge(Graph<String, edge> g, String v1, String v2) {
        g.addVertex(v1);
        g.addVertex(v2);
        edge e = new edge("f", v1, v2);
        g.addEdge(e, v1, v2);
        return e;
    }

    private static edge addWeightedEdge(Graph<String, edge> g, String v1, String v2, float w) {
        g.addVertex(v1);
        g.addVertex(v2);
        edge e = new edge("f", v1, v2);
        e.setWeight(w);
        g.addEdge(e, v1, v2);
        return e;
    }

    // ── Constructor ─────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullGraph_throws() {
        new ShortestPathFinder(null);
    }

    // ── findShortestByHops ──────────────────────────────────────────

    @Test
    public void byHops_sameVertex_returnsZeroHopPath() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        ShortestPathFinder spf = new ShortestPathFinder(g);

        ShortestPathFinder.PathResult result = spf.findShortestByHops("A", "A");
        assertNotNull(result);
        assertEquals(1, result.getVertices().size());
        assertEquals("A", result.getVertices().get(0));
        assertEquals(0, result.getHopCount());
        assertEquals(0.0, result.getTotalWeight(), 1e-9);
    }

    @Test
    public void byHops_directNeighbor_returns1Hop() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        ShortestPathFinder spf = new ShortestPathFinder(g);

        ShortestPathFinder.PathResult result = spf.findShortestByHops("A", "B");
        assertNotNull(result);
        assertEquals(Arrays.asList("A", "B"), result.getVertices());
        assertEquals(1, result.getHopCount());
    }

    @Test
    public void byHops_choosesFewestHops_notLowestWeight() {
        // A --1-- B --1-- C (2 hops)
        // A --10-- C              (1 hop, heavier)
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        addWeightedEdge(g, "A", "B", 1f);
        addWeightedEdge(g, "B", "C", 1f);
        addWeightedEdge(g, "A", "C", 10f);
        ShortestPathFinder spf = new ShortestPathFinder(g);

        ShortestPathFinder.PathResult result = spf.findShortestByHops("A", "C");
        assertNotNull(result);
        assertEquals(1, result.getHopCount()); // direct edge = 1 hop
        assertEquals(Arrays.asList("A", "C"), result.getVertices());
    }

    @Test
    public void byHops_disconnectedVertices_returnsNull() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        g.addVertex("B");
        ShortestPathFinder spf = new ShortestPathFinder(g);

        assertNull(spf.findShortestByHops("A", "B"));
    }

    @Test
    public void byHops_linearChain_returnsCorrectPath() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        ShortestPathFinder spf = new ShortestPathFinder(g);

        ShortestPathFinder.PathResult result = spf.findShortestByHops("A", "D");
        assertNotNull(result);
        assertEquals(3, result.getHopCount());
        assertEquals(Arrays.asList("A", "B", "C", "D"), result.getVertices());
        assertEquals(3, result.getEdges().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void byHops_nullSource_throws() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        new ShortestPathFinder(g).findShortestByHops(null, "A");
    }

    @Test(expected = IllegalArgumentException.class)
    public void byHops_vertexNotInGraph_throws() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        new ShortestPathFinder(g).findShortestByHops("A", "Z");
    }

    // ── findShortestByWeight (Dijkstra) ─────────────────────────────

    @Test
    public void byWeight_sameVertex_returnsZeroWeight() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("X");
        ShortestPathFinder spf = new ShortestPathFinder(g);

        ShortestPathFinder.PathResult result = spf.findShortestByWeight("X", "X");
        assertNotNull(result);
        assertEquals(0, result.getHopCount());
        assertEquals(0.0, result.getTotalWeight(), 1e-9);
    }

    @Test
    public void byWeight_prefersLighterPath() {
        // A --1-- B --1-- C (weight 2, 2 hops)
        // A --10-- C          (weight 10, 1 hop)
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        addWeightedEdge(g, "A", "B", 1f);
        addWeightedEdge(g, "B", "C", 1f);
        addWeightedEdge(g, "A", "C", 10f);
        ShortestPathFinder spf = new ShortestPathFinder(g);

        ShortestPathFinder.PathResult result = spf.findShortestByWeight("A", "C");
        assertNotNull(result);
        assertEquals(2.0, result.getTotalWeight(), 1e-9);
        assertEquals(Arrays.asList("A", "B", "C"), result.getVertices());
    }

    @Test
    public void byWeight_zeroWeightEdges_treatedAsWeight1() {
        // Zero-weight edges should be normalized to 1.0
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B"); // weight 0 (default) -> treated as 1.0
        addEdge(g, "B", "C"); // weight 0 -> treated as 1.0
        ShortestPathFinder spf = new ShortestPathFinder(g);

        ShortestPathFinder.PathResult result = spf.findShortestByWeight("A", "C");
        assertNotNull(result);
        assertEquals(2.0, result.getTotalWeight(), 1e-9);
    }

    @Test
    public void byWeight_disconnected_returnsNull() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        g.addVertex("B");
        ShortestPathFinder spf = new ShortestPathFinder(g);

        assertNull(spf.findShortestByWeight("A", "B"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void byWeight_negativeEdge_throws() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        addWeightedEdge(g, "A", "B", -5f);
        new ShortestPathFinder(g).findShortestByWeight("A", "B");
    }

    // ── getReachableVertices ────────────────────────────────────────

    @Test
    public void reachable_isolatedVertex_returnsSelf() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        ShortestPathFinder spf = new ShortestPathFinder(g);

        Set<String> reachable = spf.getReachableVertices("A");
        assertEquals(Collections.singleton("A"), reachable);
    }

    @Test
    public void reachable_connectedComponent_returnsAll() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        g.addVertex("D"); // isolated
        ShortestPathFinder spf = new ShortestPathFinder(g);

        Set<String> reachable = spf.getReachableVertices("A");
        assertEquals(new HashSet<>(Arrays.asList("A", "B", "C")), reachable);
        assertFalse(reachable.contains("D"));
    }

    // ── areConnected ────────────────────────────────────────────────

    @Test
    public void areConnected_sameVertex_returnsTrue() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        assertTrue(new ShortestPathFinder(g).areConnected("A", "A"));
    }

    @Test
    public void areConnected_directNeighbors_returnsTrue() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        assertTrue(new ShortestPathFinder(g).areConnected("A", "B"));
    }

    @Test
    public void areConnected_disconnected_returnsFalse() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        g.addVertex("B");
        assertFalse(new ShortestPathFinder(g).areConnected("A", "B"));
    }

    @Test
    public void areConnected_transitivelyConnected_returnsTrue() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        assertTrue(new ShortestPathFinder(g).areConnected("A", "D"));
    }

    // ── PathResult.toString ─────────────────────────────────────────

    @Test
    public void pathResult_toString_formatsCorrectly() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        addWeightedEdge(g, "A", "B", 3f);
        addWeightedEdge(g, "B", "C", 4f);
        ShortestPathFinder spf = new ShortestPathFinder(g);

        ShortestPathFinder.PathResult result = spf.findShortestByHops("A", "C");
        assertNotNull(result);
        String str = result.toString();
        assertTrue(str.contains("A"));
        assertTrue(str.contains("→"));
        assertTrue(str.contains("C"));
        assertTrue(str.contains("2 hops"));
    }

    // ── Directed graph support ──────────────────────────────────────

    @Test
    public void byHops_directedGraph_respectsEdgeDirection() {
        Graph<String, edge> g = new DirectedSparseGraph<>();
        addEdge(g, "A", "B"); // A -> B only
        addEdge(g, "B", "C"); // B -> C only
        ShortestPathFinder spf = new ShortestPathFinder(g);

        // Forward direction should work
        ShortestPathFinder.PathResult forward = spf.findShortestByHops("A", "C");
        assertNotNull(forward);
        assertEquals(2, forward.getHopCount());

        // Reverse direction should fail (no path C -> A in directed graph)
        ShortestPathFinder.PathResult reverse = spf.findShortestByHops("C", "A");
        assertNull(reverse);
    }

    // ── Larger graph: diamond/grid ──────────────────────────────────

    @Test
    public void byWeight_diamondGraph_findsOptimalPath() {
        //     B
        //    / \
        //   1   5
        //  /     \
        // A       D
        //  \     /
        //   2   1
        //    \ /
        //     C
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        addWeightedEdge(g, "A", "B", 1f);
        addWeightedEdge(g, "A", "C", 2f);
        addWeightedEdge(g, "B", "D", 5f);
        addWeightedEdge(g, "C", "D", 1f);
        ShortestPathFinder spf = new ShortestPathFinder(g);

        ShortestPathFinder.PathResult result = spf.findShortestByWeight("A", "D");
        assertNotNull(result);
        // A->C->D = 3.0 is cheaper than A->B->D = 6.0
        assertEquals(3.0, result.getTotalWeight(), 1e-9);
        assertEquals(Arrays.asList("A", "C", "D"), result.getVertices());
    }
}
