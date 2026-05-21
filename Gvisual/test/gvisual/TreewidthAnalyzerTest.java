package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests for {@link TreewidthAnalyzer}.
 *
 * <p>These tests pin down the analyzer on canonical graph families where the
 * treewidth is known exactly:
 * <ul>
 *   <li>Empty graph and singleton: treewidth 0</li>
 *   <li>Forests (paths, trees): treewidth 1</li>
 *   <li>Cycle C_n (n &ge; 3): treewidth 2</li>
 *   <li>Complete graph K_n: treewidth n-1 (both bounds tight, exact)</li>
 *   <li>n-by-m grid: treewidth = min(n, m) — but the heuristics here only
 *       give bounds, so we assert the bounds bracket the true value</li>
 * </ul>
 *
 * <p>Beyond exact-value pinning, these tests guard the structural
 * invariants of the produced tree decomposition: every graph vertex
 * appears in at least one bag, every graph edge has both endpoints in at
 * least one common bag, and the reported width equals max(|bag|) - 1.
 * These three properties together with bag-connectedness are the
 * definition of a tree decomposition; CI breakage on any of them flags a
 * real algorithmic regression, not a cosmetic one.
 */
public class TreewidthAnalyzerTest {

    private int edgeCounter;

    @Before
    public void setUp() {
        edgeCounter = 0;
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private Edge addEdge(Graph<String, Edge> g, String a, String b) {
        if (!g.containsVertex(a)) g.addVertex(a);
        if (!g.containsVertex(b)) g.addVertex(b);
        Edge e = new Edge("e" + (++edgeCounter), a, b);
        g.addEdge(e, a, b);
        return e;
    }

    private Graph<String, Edge> path(int n) {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        for (int i = 0; i < n; i++) g.addVertex("v" + i);
        for (int i = 0; i < n - 1; i++) addEdge(g, "v" + i, "v" + (i + 1));
        return g;
    }

    private Graph<String, Edge> cycle(int n) {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        for (int i = 0; i < n; i++) {
            addEdge(g, "v" + i, "v" + ((i + 1) % n));
        }
        return g;
    }

    private Graph<String, Edge> complete(int n) {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        for (int i = 0; i < n; i++) g.addVertex("v" + i);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                addEdge(g, "v" + i, "v" + j);
            }
        }
        return g;
    }

    /** Caterpillar tree: a path with one leaf hanging off each interior node. */
    private Graph<String, Edge> caterpillar(int spineLength) {
        Graph<String, Edge> g = path(spineLength);
        for (int i = 0; i < spineLength; i++) {
            addEdge(g, "v" + i, "leaf" + i);
        }
        return g;
    }

    private Graph<String, Edge> grid(int rows, int cols) {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                g.addVertex(r + "," + c);
            }
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (c + 1 < cols) addEdge(g, r + "," + c, r + "," + (c + 1));
                if (r + 1 < rows) addEdge(g, r + "," + c, (r + 1) + "," + c);
            }
        }
        return g;
    }

    /**
     * Verifies the two combinatorial invariants of a tree decomposition
     * that don't depend on the connectivity of the bag-tree:
     * vertex coverage and edge coverage. (Bag-connectedness — the third
     * defining property — is harder to assert without reconstructing the
     * tree, and is exercised indirectly by the elimination algorithm's
     * construction.)
     */
    private void assertCoversGraph(Graph<String, Edge> g,
                                   TreewidthAnalyzer.TreewidthResult r) {
        TreewidthAnalyzer.TreeDecomposition d = r.getDecomposition();

        // Vertex coverage
        Set<String> covered = new HashSet<String>();
        for (TreewidthAnalyzer.Bag bag : d.getBags()) {
            covered.addAll(bag.getVertices());
        }
        for (String v : g.getVertices()) {
            assertTrue("vertex " + v + " not covered by any bag",
                    covered.contains(v));
        }

        // Edge coverage: each graph edge has both endpoints in some shared bag
        for (Edge e : g.getEdges()) {
            edu.uci.ics.jung.graph.util.Pair<String> ends = g.getEndpoints(e);
            String a = ends.getFirst();
            String b = ends.getSecond();
            boolean found = false;
            for (TreewidthAnalyzer.Bag bag : d.getBags()) {
                if (bag.getVertices().contains(a) && bag.getVertices().contains(b)) {
                    found = true;
                    break;
                }
            }
            assertTrue("edge " + a + "--" + b + " not in any single bag", found);
        }

        // Reported width matches max-bag-minus-one
        int maxBag = 0;
        for (TreewidthAnalyzer.Bag bag : d.getBags()) {
            maxBag = Math.max(maxBag, bag.getVertices().size());
        }
        if (d.getBags().isEmpty()) {
            assertEquals("empty decomposition has width 0", 0, d.getWidth());
        } else {
            assertEquals("reported width should equal max(|bag|)-1",
                    maxBag - 1, d.getWidth());
        }
    }

    // ─── Trivial cases ──────────────────────────────────────────────

    @Test
    public void emptyGraphHasTreewidthZero() {
        TreewidthAnalyzer.TreewidthResult r =
                TreewidthAnalyzer.analyze(new UndirectedSparseGraph<String, Edge>());
        assertEquals(0, r.getLowerBound());
        assertEquals(0, r.getUpperBound());
        assertEquals(0, r.getVertices());
        assertEquals(0, r.getEdges());
        assertTrue(r.isExact());
        assertEquals(0, r.getDecomposition().getBags().size());
        assertEquals(0, r.getDecomposition().getTreeEdges().size());
    }

    @Test
    public void singletonHasTreewidthZero() {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        g.addVertex("solo");
        TreewidthAnalyzer.TreewidthResult r = TreewidthAnalyzer.analyze(g);
        assertEquals(0, r.getUpperBound());
        assertEquals(0, r.getLowerBound());
        assertTrue(r.isExact());
        assertCoversGraph(g, r);
    }

    @Test
    public void singleEdgeHasTreewidthOne() {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        addEdge(g, "a", "b");
        TreewidthAnalyzer.TreewidthResult r = TreewidthAnalyzer.analyze(g);
        assertEquals(1, r.getUpperBound());
        assertTrue("lower bound should be <= upper bound",
                r.getLowerBound() <= r.getUpperBound());
        assertCoversGraph(g, r);
    }

    // ─── Forests / trees ────────────────────────────────────────────

    @Test
    public void pathHasTreewidthOne() {
        Graph<String, Edge> g = path(7);
        TreewidthAnalyzer.TreewidthResult r = TreewidthAnalyzer.analyze(g);
        assertEquals("path P_7 should have treewidth 1", 1, r.getUpperBound());
        assertTrue(r.getLowerBound() <= 1);
        assertCoversGraph(g, r);
    }

    @Test
    public void caterpillarHasTreewidthOne() {
        Graph<String, Edge> g = caterpillar(5);
        TreewidthAnalyzer.TreewidthResult r = TreewidthAnalyzer.analyze(g);
        assertEquals("trees have treewidth 1", 1, r.getUpperBound());
        assertCoversGraph(g, r);
    }

    // ─── Cycles ─────────────────────────────────────────────────────

    @Test
    public void triangleHasTreewidthTwo() {
        // K3 == C3
        Graph<String, Edge> g = complete(3);
        TreewidthAnalyzer.TreewidthResult r = TreewidthAnalyzer.analyze(g);
        assertEquals(2, r.getUpperBound());
        assertEquals(2, r.getLowerBound());
        assertTrue(r.isExact());
        assertCoversGraph(g, r);
    }

    @Test
    public void cycleC5HasTreewidthTwo() {
        Graph<String, Edge> g = cycle(5);
        TreewidthAnalyzer.TreewidthResult r = TreewidthAnalyzer.analyze(g);
        assertEquals("cycle C_n (n>=3) has treewidth 2", 2, r.getUpperBound());
        assertCoversGraph(g, r);
    }

    @Test
    public void cycleC8HasTreewidthTwo() {
        Graph<String, Edge> g = cycle(8);
        TreewidthAnalyzer.TreewidthResult r = TreewidthAnalyzer.analyze(g);
        assertEquals(2, r.getUpperBound());
        assertCoversGraph(g, r);
    }

    // ─── Complete graphs ────────────────────────────────────────────

    @Test
    public void k4HasTreewidthThree() {
        Graph<String, Edge> g = complete(4);
        TreewidthAnalyzer.TreewidthResult r = TreewidthAnalyzer.analyze(g);
        assertEquals(3, r.getUpperBound());
        assertEquals(3, r.getLowerBound());
        assertTrue("K_n bounds should be exact", r.isExact());
        assertCoversGraph(g, r);
    }

    @Test
    public void k6HasTreewidthFive() {
        Graph<String, Edge> g = complete(6);
        TreewidthAnalyzer.TreewidthResult r = TreewidthAnalyzer.analyze(g);
        assertEquals(5, r.getUpperBound());
        assertEquals(5, r.getLowerBound());
        assertTrue(r.isExact());
        assertCoversGraph(g, r);
    }

    // ─── Grids ──────────────────────────────────────────────────────

    @Test
    public void smallGridBoundsBracketTrueTreewidth() {
        // 3x4 grid has true treewidth 3 (= min(3,4))
        Graph<String, Edge> g = grid(3, 4);
        TreewidthAnalyzer.TreewidthResult r = TreewidthAnalyzer.analyze(g);
        assertTrue("lower bound should be at least 2 for any 3-by-N grid",
                r.getLowerBound() >= 2);
        assertTrue("upper bound should not exceed n-1 = 11",
                r.getUpperBound() <= 11);
        assertTrue(r.getLowerBound() <= r.getUpperBound());
        assertCoversGraph(g, r);
    }

    // ─── Disconnected graphs ────────────────────────────────────────

    @Test
    public void independentSetHasTreewidthZero() {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        for (int i = 0; i < 5; i++) g.addVertex("v" + i);
        TreewidthAnalyzer.TreewidthResult r = TreewidthAnalyzer.analyze(g);
        assertEquals(0, r.getUpperBound());
        assertEquals(0, r.getLowerBound());
        assertCoversGraph(g, r);
    }

    @Test
    public void twoDisconnectedTrianglesHasTreewidthTwo() {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        // triangle 1
        addEdge(g, "a", "b");
        addEdge(g, "b", "c");
        addEdge(g, "c", "a");
        // triangle 2
        addEdge(g, "x", "y");
        addEdge(g, "y", "z");
        addEdge(g, "z", "x");
        TreewidthAnalyzer.TreewidthResult r = TreewidthAnalyzer.analyze(g);
        assertEquals(2, r.getUpperBound());
        assertCoversGraph(g, r);
    }

    // ─── Bounds ordering invariant ──────────────────────────────────

    @Test
    public void lowerBoundNeverExceedsUpperBoundOnRandomDenseGraph() {
        // Build a moderately dense graph and check the bound ordering.
        // Uses a deterministic seed so the test is repeatable.
        java.util.Random rng = new java.util.Random(42L);
        int n = 12;
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        for (int i = 0; i < n; i++) g.addVertex("v" + i);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (rng.nextDouble() < 0.4) addEdge(g, "v" + i, "v" + j);
            }
        }
        TreewidthAnalyzer.TreewidthResult r = TreewidthAnalyzer.analyze(g);
        assertTrue("lower <= upper", r.getLowerBound() <= r.getUpperBound());
        assertTrue("upper <= n-1", r.getUpperBound() <= n - 1);
        assertCoversGraph(g, r);
    }

    // ─── Report ─────────────────────────────────────────────────────

    @Test
    public void reportMentionsBoundsAndDecomposition() {
        String txt = TreewidthAnalyzer.report(complete(4));
        assertNotNull(txt);
        assertTrue("report should mention 'Treewidth'", txt.contains("Treewidth"));
        assertTrue("report should mention 'Bounds'", txt.contains("Bounds"));
        assertTrue("report should mention 'Tree Decomposition'",
                txt.contains("Tree Decomposition"));
    }

    @Test
    public void reportOnEmptyGraphIsWellFormed() {
        String txt = TreewidthAnalyzer.report(new UndirectedSparseGraph<String, Edge>());
        assertNotNull(txt);
        assertTrue(txt.contains("Vertices: 0"));
    }

    // ─── Bag / TreeEdge value semantics ─────────────────────────────

    @Test
    public void bagWidthIsSizeMinusOne() {
        Set<String> vs = new HashSet<String>();
        vs.add("a"); vs.add("b"); vs.add("c");
        TreewidthAnalyzer.Bag bag = new TreewidthAnalyzer.Bag(7, vs);
        assertEquals(7, bag.getId());
        assertEquals(3, bag.getVertices().size());
        assertEquals(2, bag.width());
        assertTrue(bag.toString().contains("Bag 7"));
    }

    @Test
    public void treeEdgeRetainsEndpoints() {
        TreewidthAnalyzer.TreeEdge e = new TreewidthAnalyzer.TreeEdge(3, 9);
        assertEquals(3, e.getBag1());
        assertEquals(9, e.getBag2());
        assertEquals("3 -- 9", e.toString());
    }

    @Test
    public void bagVerticesAreUnmodifiable() {
        Set<String> vs = new HashSet<String>();
        vs.add("a");
        TreewidthAnalyzer.Bag bag = new TreewidthAnalyzer.Bag(0, vs);
        try {
            bag.getVertices().add("hacker");
            fail("Bag.getVertices() must return an unmodifiable view");
        } catch (UnsupportedOperationException expected) {
            // good
        }
    }
}
