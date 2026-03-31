package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link KCoreDecomposition} — coreness computation, shells,
 * density profiles, cohesion, and classification.
 */
public class KCoreDecompositionTest {

    // ── Helper ──────────────────────────────────────────────────────

    private static Edge addEdge(Graph<String, Edge> g, String v1, String v2) {
        g.addVertex(v1);
        g.addVertex(v2);
        Edge e = new Edge("f", v1, v2);
        g.addEdge(e, v1, v2);
        return e;
    }

    // ── Constructor ─────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new KCoreDecomposition(null);
    }

    // ── Empty graph ─────────────────────────────────────────────────

    @Test
    public void testEmptyGraph() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        KCoreDecomposition kcd = new KCoreDecomposition(g).compute();
        assertEquals(0, kcd.getDegeneracy());
        assertTrue(kcd.getCoreness().isEmpty());
        assertEquals(0.0, kcd.getCohesionScore(), 0.001);
        assertEquals(0.0, kcd.getAverageCoreness(), 0.001);
        assertEquals("Empty", kcd.classifyCoreStructure());
    }

    // ── Isolated vertices ───────────────────────────────────────────

    @Test
    public void testIsolatedVertices() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        g.addVertex("B");
        g.addVertex("C");
        KCoreDecomposition kcd = new KCoreDecomposition(g).compute();
        assertEquals(0, kcd.getDegeneracy());
        for (String v : new String[]{"A", "B", "C"}) {
            assertEquals(0, kcd.getCoreness(v));
        }
        assertEquals("Disconnected (all isolated vertices)", kcd.classifyCoreStructure());
    }

    // ── Simple path (tree-like) ─────────────────────────────────────

    @Test
    public void testPathGraph() {
        // A-B-C-D: all vertices have coreness 1
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        KCoreDecomposition kcd = new KCoreDecomposition(g).compute();
        assertEquals(1, kcd.getDegeneracy());
        for (String v : new String[]{"A", "B", "C", "D"}) {
            assertEquals(1, kcd.getCoreness(v));
        }
        assertEquals("Tree-like (no dense subgraphs)", kcd.classifyCoreStructure());
    }

    // ── Triangle ────────────────────────────────────────────────────

    @Test
    public void testTriangle() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        KCoreDecomposition kcd = new KCoreDecomposition(g).compute();
        assertEquals(2, kcd.getDegeneracy());
        for (String v : new String[]{"A", "B", "C"}) {
            assertEquals(2, kcd.getCoreness(v));
        }
        assertEquals(100.0, kcd.getCohesionScore(), 0.001);
    }

    // ── K4 (complete graph on 4 vertices) ───────────────────────────

    @Test
    public void testK4() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        String[] verts = {"A", "B", "C", "D"};
        for (int i = 0; i < verts.length; i++)
            for (int j = i + 1; j < verts.length; j++)
                addEdge(g, verts[i], verts[j]);

        KCoreDecomposition kcd = new KCoreDecomposition(g).compute();
        assertEquals(3, kcd.getDegeneracy());
        for (String v : verts) {
            assertEquals(3, kcd.getCoreness(v));
        }
        assertEquals(3.0, kcd.getAverageCoreness(), 0.001);
    }

    // ── Core-periphery structure ────────────────────────────────────

    @Test
    public void testCorePeriphery() {
        // Triangle (2-core) with pendant vertices
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        addEdge(g, "A", "X");  // pendant
        addEdge(g, "B", "Y");  // pendant

        KCoreDecomposition kcd = new KCoreDecomposition(g).compute();
        assertEquals(2, kcd.getDegeneracy());
        assertEquals(2, kcd.getCoreness("A"));
        assertEquals(2, kcd.getCoreness("B"));
        assertEquals(2, kcd.getCoreness("C"));
        assertEquals(1, kcd.getCoreness("X"));
        assertEquals(1, kcd.getCoreness("Y"));
    }

    // ── getKCore ────────────────────────────────────────────────────

    @Test
    public void testGetKCore() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        addEdge(g, "A", "X");

        KCoreDecomposition kcd = new KCoreDecomposition(g).compute();
        List<String> core2 = kcd.getKCore(2);
        assertEquals(3, core2.size());
        assertTrue(core2.containsAll(Arrays.asList("A", "B", "C")));
        assertFalse(core2.contains("X"));

        List<String> core1 = kcd.getKCore(1);
        assertEquals(4, core1.size());

        List<String> core0 = kcd.getKCore(0);
        assertEquals(4, core0.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetKCoreNegativeThrows() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        new KCoreDecomposition(g).getKCore(-1);
    }

    // ── Core shells ─────────────────────────────────────────────────

    @Test
    public void testCoreShells() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        addEdge(g, "A", "X");

        KCoreDecomposition kcd = new KCoreDecomposition(g).compute();
        SortedMap<Integer, List<String>> shells = kcd.getCoreShells();
        assertEquals(2, shells.size());
        assertTrue(shells.containsKey(1));
        assertTrue(shells.containsKey(2));
        assertEquals(1, shells.get(1).size());
        assertEquals(3, shells.get(2).size());
    }

    // ── Coreness distribution ───────────────────────────────────────

    @Test
    public void testCorenessDistribution() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        addEdge(g, "A", "X");

        SortedMap<Integer, Integer> dist = new KCoreDecomposition(g).compute().getCorenessDistribution();
        assertEquals(Integer.valueOf(1), dist.get(1));
        assertEquals(Integer.valueOf(3), dist.get(2));
    }

    // ── Core density profile ────────────────────────────────────────

    @Test
    public void testCoreDensityProfile() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");

        List<KCoreDecomposition.CoreDensity> profile =
                new KCoreDecomposition(g).compute().getCoreDensityProfile();
        assertFalse(profile.isEmpty());

        // The innermost core (2-core) is a triangle → density = 1.0
        KCoreDecomposition.CoreDensity innermost = profile.get(profile.size() - 1);
        assertEquals(2, innermost.getK());
        assertEquals(1.0, innermost.getDensity(), 0.001);
    }

    // ── Unknown vertex ──────────────────────────────────────────────

    @Test
    public void testUnknownVertexReturnsMinusOne() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        assertEquals(-1, new KCoreDecomposition(g).compute().getCoreness("MISSING"));
    }

    // ── Idempotency ─────────────────────────────────────────────────

    @Test
    public void testComputeIdempotent() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        KCoreDecomposition kcd = new KCoreDecomposition(g);
        KCoreDecomposition same = kcd.compute().compute().compute();
        assertSame(kcd, same);
    }

    // ── getResult ───────────────────────────────────────────────────

    @Test
    public void testGetResult() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");

        KCoreDecomposition.KCoreResult result = new KCoreDecomposition(g).getResult();
        assertEquals(3, result.getVertexCount());
        assertEquals(3, result.getEdgeCount());
        assertEquals(2, result.getDegeneracy());
        assertEquals(1, result.getNumberOfShells());
        assertNotNull(result.getStructureClassification());
        assertNotNull(result.getCoreness());
        assertNotNull(result.getShells());
        assertNotNull(result.getDistribution());
        assertNotNull(result.getDensityProfile());
    }

    // ── getSummary ──────────────────────────────────────────────────

    @Test
    public void testGetSummaryContainsKey() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");

        String summary = new KCoreDecomposition(g).compute().getSummary();
        assertTrue(summary.contains("K-Core Decomposition"));
        assertTrue(summary.contains("Degeneracy: 2"));
        assertTrue(summary.contains("density"));
    }
}
