package gvisual;

import edu.uci.ics.jung.graph.Graph;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link RandomGraphGenerator}.
 *
 * Exercises each generator with deterministic seeds and asserts the
 * structural invariants documented in the class Javadoc.
 */
public class RandomGraphGeneratorTest {

    // ----------------------------------------------------------------
    // Catalog
    // ----------------------------------------------------------------

    @Test
    public void testCatalogContainsAllModels() {
        Map<String, String> cat = RandomGraphGenerator.catalog();
        assertNotNull(cat);
        assertTrue(cat.containsKey("erdos-renyi"));
        assertTrue(cat.containsKey("barabasi-albert"));
        assertTrue(cat.containsKey("watts-strogatz"));
        assertTrue(cat.containsKey("random-regular"));
        assertTrue(cat.containsKey("grid"));
        assertTrue(cat.containsKey("random-tree"));
        assertTrue(cat.containsKey("complete"));
        assertTrue(cat.containsKey("star"));
        assertEquals(8, cat.size());
    }

    // ----------------------------------------------------------------
    // Erdos-Renyi
    // ----------------------------------------------------------------

    @Test
    public void testErdosRenyiP1IsComplete() {
        // p = 1 -> every edge included -> K_n
        Graph<String, Edge> g = RandomGraphGenerator.erdosRenyi(8, 1.0, new Random(1));
        assertEquals(8, g.getVertexCount());
        assertEquals(8 * 7 / 2, g.getEdgeCount());
    }

    @Test
    public void testErdosRenyiP0HasNoEdges() {
        Graph<String, Edge> g = RandomGraphGenerator.erdosRenyi(10, 0.0, new Random(2));
        assertEquals(10, g.getVertexCount());
        assertEquals(0, g.getEdgeCount());
    }

    @Test
    public void testErdosRenyiHasNoSelfLoopsOrMultiEdges() {
        Graph<String, Edge> g = RandomGraphGenerator.erdosRenyi(30, 0.4, new Random(42));
        assertEquals(30, g.getVertexCount());
        assertNoSelfLoopsOrParallel(g);
    }

    @Test
    public void testErdosRenyiDefaultRngWorks() {
        // Just exercise the no-rng overload; structure must still be valid.
        Graph<String, Edge> g = RandomGraphGenerator.erdosRenyi(10, 0.2);
        assertEquals(10, g.getVertexCount());
        assertNoSelfLoopsOrParallel(g);
    }

    // ----------------------------------------------------------------
    // Barabasi-Albert
    // ----------------------------------------------------------------

    @Test
    public void testBarabasiAlbertVertexCount() {
        Graph<String, Edge> g = RandomGraphGenerator.barabasiAlbert(20, 2, new Random(7));
        assertEquals(20, g.getVertexCount());
        // Initial K_{m+1} = K_3 has 3 edges; each new vertex (20 - 3 = 17 of them) adds m = 2 edges.
        // Total = 3 + 17 * 2 = 37.
        assertEquals(37, g.getEdgeCount());
        assertNoSelfLoopsOrParallel(g);
    }

    @Test
    public void testBarabasiAlbertClampsSmallParameters() {
        // m forced to 1, n forced to m+1=2 -> single edge.
        Graph<String, Edge> g = RandomGraphGenerator.barabasiAlbert(1, 0, new Random(3));
        assertEquals(2, g.getVertexCount());
        assertEquals(1, g.getEdgeCount());
    }

    @Test
    public void testBarabasiAlbertDefaultRngWorks() {
        Graph<String, Edge> g = RandomGraphGenerator.barabasiAlbert(10, 2);
        assertEquals(10, g.getVertexCount());
        assertNoSelfLoopsOrParallel(g);
    }

    // ----------------------------------------------------------------
    // Watts-Strogatz
    // ----------------------------------------------------------------

    @Test
    public void testWattsStrogatzBetaZeroIsRingLattice() {
        int n = 12, k = 4;
        Graph<String, Edge> g = RandomGraphGenerator.wattsStrogatz(n, k, 0.0, new Random(11));
        assertEquals(n, g.getVertexCount());
        // Ring lattice with k=4 has n*k/2 edges and is k-regular.
        assertEquals(n * k / 2, g.getEdgeCount());
        for (String v : g.getVertices()) {
            assertEquals("vertex " + v + " should have degree k", k, g.degree(v));
        }
        assertNoSelfLoopsOrParallel(g);
    }

    @Test
    public void testWattsStrogatzClampsBadInputs() {
        // n=2 < 3 should be bumped to 3; k=1 (odd, <2) bumped to 2.
        Graph<String, Edge> g = RandomGraphGenerator.wattsStrogatz(2, 1, 0.5, new Random(1));
        assertTrue(g.getVertexCount() >= 3);
        assertNoSelfLoopsOrParallel(g);
    }

    @Test
    public void testWattsStrogatzWithRewiringIsValid() {
        Graph<String, Edge> g = RandomGraphGenerator.wattsStrogatz(20, 4, 0.5, new Random(99));
        assertEquals(20, g.getVertexCount());
        // Rewiring preserves edge count.
        assertEquals(20 * 4 / 2, g.getEdgeCount());
        assertNoSelfLoopsOrParallel(g);
    }

    @Test
    public void testWattsStrogatzDefaultRngWorks() {
        Graph<String, Edge> g = RandomGraphGenerator.wattsStrogatz(10, 4, 0.1);
        assertEquals(10, g.getVertexCount());
    }

    // ----------------------------------------------------------------
    // Random regular
    // ----------------------------------------------------------------

    @Test
    public void testRandomRegularIsKRegular() {
        int n = 10, k = 3;
        // n*k must be even -> 10*3=30 even, ok.
        Graph<String, Edge> g = RandomGraphGenerator.randomRegular(n, k, new Random(5));
        assertEquals(n, g.getVertexCount());
        for (String v : g.getVertices()) {
            assertEquals("vertex " + v + " should be " + k + "-regular", k, g.degree(v));
        }
        assertNoSelfLoopsOrParallel(g);
    }

    @Test
    public void testRandomRegularOddNxKIsAdjusted() {
        // n*k=5*3=15 odd -> adjusts n to 6.
        Graph<String, Edge> g = RandomGraphGenerator.randomRegular(5, 3, new Random(13));
        assertEquals(6, g.getVertexCount());
    }

    @Test
    public void testRandomRegularDefaultRngWorks() {
        Graph<String, Edge> g = RandomGraphGenerator.randomRegular(8, 2);
        assertEquals(8, g.getVertexCount());
    }

    // ----------------------------------------------------------------
    // Grid
    // ----------------------------------------------------------------

    @Test
    public void testGridVertexAndEdgeCount() {
        int rows = 4, cols = 5;
        Graph<String, Edge> g = RandomGraphGenerator.grid(rows, cols);
        assertEquals(rows * cols, g.getVertexCount());
        // Grid edges = rows*(cols-1) horizontal + (rows-1)*cols vertical
        int expected = rows * (cols - 1) + (rows - 1) * cols;
        assertEquals(expected, g.getEdgeCount());
        assertNoSelfLoopsOrParallel(g);
    }

    @Test
    public void testGrid1x1HasNoEdges() {
        Graph<String, Edge> g = RandomGraphGenerator.grid(1, 1);
        assertEquals(1, g.getVertexCount());
        assertEquals(0, g.getEdgeCount());
    }

    // ----------------------------------------------------------------
    // Random tree
    // ----------------------------------------------------------------

    @Test
    public void testRandomTreeHasNMinusOneEdges() {
        for (int n : new int[]{2, 3, 7, 15, 30}) {
            Graph<String, Edge> g = RandomGraphGenerator.randomTree(n, new Random(n));
            assertEquals("n=" + n + " vertex count", n, g.getVertexCount());
            assertEquals("n=" + n + " tree should have n-1 edges", n - 1, g.getEdgeCount());
            assertNoSelfLoopsOrParallel(g);
        }
    }

    @Test
    public void testRandomTreeClampsTooSmallN() {
        Graph<String, Edge> g = RandomGraphGenerator.randomTree(1);
        assertEquals(2, g.getVertexCount());
        assertEquals(1, g.getEdgeCount());
    }

    // ----------------------------------------------------------------
    // Complete
    // ----------------------------------------------------------------

    @Test
    public void testCompleteHasAllEdges() {
        for (int n : new int[]{1, 2, 5, 8}) {
            Graph<String, Edge> g = RandomGraphGenerator.complete(n);
            assertEquals(n, g.getVertexCount());
            assertEquals(n * (n - 1) / 2, g.getEdgeCount());
            assertNoSelfLoopsOrParallel(g);
        }
    }

    // ----------------------------------------------------------------
    // Star
    // ----------------------------------------------------------------

    @Test
    public void testStarHasHubAndLeaves() {
        Graph<String, Edge> g = RandomGraphGenerator.star(7);
        assertEquals(7, g.getVertexCount());
        assertEquals(6, g.getEdgeCount());
        assertTrue("hub vertex must exist", g.containsVertex("hub"));
        assertEquals("hub must connect to all leaves", 6, g.degree("hub"));
        for (String v : g.getVertices()) {
            if (!v.equals("hub")) {
                assertEquals("leaf " + v + " has degree 1", 1, g.degree(v));
            }
        }
    }

    @Test
    public void testStarClampsTooSmallN() {
        Graph<String, Edge> g = RandomGraphGenerator.star(1);
        assertEquals(2, g.getVertexCount());
        assertEquals(1, g.getEdgeCount());
    }

    // ----------------------------------------------------------------
    // byName dispatcher
    // ----------------------------------------------------------------

    @Test
    public void testByNameKnownModels() {
        Map<String, Number> p = new HashMap<>();
        p.put("n", 10);
        p.put("p", 1.0);
        Graph<String, Edge> er = RandomGraphGenerator.byName("erdos-renyi", p);
        assertNotNull(er);
        assertEquals(10, er.getVertexCount());
        assertEquals(45, er.getEdgeCount());

        Map<String, Number> p2 = new HashMap<>();
        p2.put("n", 8);
        assertNotNull(RandomGraphGenerator.byName("barabasi-albert", p2));
        assertNotNull(RandomGraphGenerator.byName("watts-strogatz", p2));
        assertNotNull(RandomGraphGenerator.byName("random-regular", p2));
        assertNotNull(RandomGraphGenerator.byName("random-tree", p2));
        assertNotNull(RandomGraphGenerator.byName("complete", p2));
        assertNotNull(RandomGraphGenerator.byName("star", p2));

        Map<String, Number> grid = new HashMap<>();
        grid.put("rows", 3);
        grid.put("cols", 4);
        Graph<String, Edge> g = RandomGraphGenerator.byName("grid", grid);
        assertNotNull(g);
        assertEquals(12, g.getVertexCount());
    }

    @Test
    public void testByNameWithDefaultsUsesEmptyMap() {
        // All defaults apply: n=20.
        Map<String, Number> empty = new HashMap<>();
        Graph<String, Edge> g = RandomGraphGenerator.byName("complete", empty);
        assertNotNull(g);
        assertEquals(20, g.getVertexCount());
    }

    @Test
    public void testByNameUnknownReturnsNull() {
        assertNull(RandomGraphGenerator.byName("not-a-real-model", new HashMap<>()));
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private static void assertNoSelfLoopsOrParallel(Graph<String, Edge> g) {
        Set<String> pairs = new HashSet<>();
        for (Edge e : g.getEdges()) {
            // JUNG's UndirectedSparseGraph does not allow parallel edges so
            // duplicates would already have been silently dropped, but the
            // pair-collection sanity check still validates self-loop absence.
            java.util.Collection<String> endpoints = g.getEndpoints(e);
            assertEquals("each edge should have exactly two endpoints", 2, endpoints.size());
            String[] arr = endpoints.toArray(new String[0]);
            assertNotEquals("self-loop detected on " + arr[0], arr[0], arr[1]);
            String key = arr[0].compareTo(arr[1]) < 0 ? arr[0] + "|" + arr[1] : arr[1] + "|" + arr[0];
            assertTrue("parallel edge detected: " + key, pairs.add(key));
        }
    }
}
