package gvisual;

import edu.uci.ics.jung.graph.Graph;
import org.junit.Test;

import java.util.LinkedHashMap;

import static org.junit.Assert.*;

/**
 * Tests for {@link FamousGraphLibrary} — verifies structural properties
 * of each famous named graph.
 */
public class FamousGraphLibraryTest {

    // ── Petersen Graph ──────────────────────────────────────

    @Test
    public void petersen_has_10_vertices_15_edges() {
        Graph<String, Edge> g = FamousGraphLibrary.petersen();
        assertEquals(10, g.getVertexCount());
        assertEquals(15, g.getEdgeCount());
        // Petersen graph is 3-regular
        for (String v : g.getVertices()) {
            assertEquals("Petersen is 3-regular", 3, g.degree(v));
        }
    }

    // ── Zachary's Karate Club ───────────────────────────────

    @Test
    public void karate_has_34_vertices_78_edges() {
        Graph<String, Edge> g = FamousGraphLibrary.karateClub();
        assertEquals(34, g.getVertexCount());
        assertEquals(78, g.getEdgeCount());
    }

    // ── Königsberg Bridges ──────────────────────────────────

    @Test
    public void konigsberg_has_4_vertices_7_edges() {
        Graph<String, Edge> g = FamousGraphLibrary.konigsberg();
        assertEquals(4, g.getVertexCount());
        assertEquals(7, g.getEdgeCount());
    }

    // ── K₃₃ ────────────────────────────────────────────────

    @Test
    public void k33_has_6_vertices_9_edges() {
        Graph<String, Edge> g = FamousGraphLibrary.k33();
        assertEquals(6, g.getVertexCount());
        assertEquals(9, g.getEdgeCount());
    }

    // ── Frucht Graph ────────────────────────────────────────

    @Test
    public void frucht_has_12_vertices_18_edges() {
        Graph<String, Edge> g = FamousGraphLibrary.frucht();
        assertEquals(12, g.getVertexCount());
        assertEquals(18, g.getEdgeCount());
        // Frucht is 3-regular (cubic)
        for (String v : g.getVertices()) {
            assertEquals("Frucht is cubic", 3, g.degree(v));
        }
    }

    // ── Heawood Graph ───────────────────────────────────────

    @Test
    public void heawood_has_14_vertices_21_edges() {
        Graph<String, Edge> g = FamousGraphLibrary.heawood();
        assertEquals(14, g.getVertexCount());
        assertEquals(21, g.getEdgeCount());
        // Heawood is 3-regular
        for (String v : g.getVertices()) {
            assertEquals("Heawood is cubic", 3, g.degree(v));
        }
    }

    // ── Dodecahedron ────────────────────────────────────────

    @Test
    public void dodecahedron_has_20_vertices_30_edges() {
        Graph<String, Edge> g = FamousGraphLibrary.dodecahedron();
        assertEquals(20, g.getVertexCount());
        assertEquals(30, g.getEdgeCount());
        // Dodecahedron is 3-regular
        for (String v : g.getVertices()) {
            assertEquals("Dodecahedron is cubic", 3, g.degree(v));
        }
    }

    // ── Tutte Graph ─────────────────────────────────────────

    @Test
    public void tutte_has_46_vertices_69_edges() {
        Graph<String, Edge> g = FamousGraphLibrary.tutte();
        assertEquals(46, g.getVertexCount());
        assertEquals(69, g.getEdgeCount());
        // Tutte graph is 3-regular
        for (String v : g.getVertices()) {
            assertEquals("Tutte is cubic", 3, g.degree(v));
        }
    }

    // ── Florentine Families ─────────────────────────────────

    @Test
    public void florentine_has_15_vertices() {
        Graph<String, Edge> g = FamousGraphLibrary.florentine();
        assertEquals(15, g.getVertexCount());
        assertTrue(g.getEdgeCount() > 0);
        // Medici should be a high-degree hub
        assertTrue("Medici is a hub", g.degree("Medici") >= 5);
    }

    // ── Diamond Graph ───────────────────────────────────────

    @Test
    public void diamond_has_4_vertices_5_edges() {
        Graph<String, Edge> g = FamousGraphLibrary.diamond();
        assertEquals(4, g.getVertexCount());
        assertEquals(5, g.getEdgeCount());
    }

    // ── Bull Graph ──────────────────────────────────────────

    @Test
    public void bull_has_5_vertices_5_edges() {
        Graph<String, Edge> g = FamousGraphLibrary.bull();
        assertEquals(5, g.getVertexCount());
        assertEquals(5, g.getEdgeCount());
    }

    // ── Butterfly Graph ─────────────────────────────────────

    @Test
    public void butterfly_has_5_vertices_6_edges() {
        Graph<String, Edge> g = FamousGraphLibrary.butterfly();
        assertEquals(5, g.getVertexCount());
        assertEquals(6, g.getEdgeCount());
    }

    // ── Catalog ─────────────────────────────────────────────

    @Test
    public void catalog_contains_all_12_graphs() {
        LinkedHashMap<String, String> cat = FamousGraphLibrary.catalog();
        assertEquals(12, cat.size());
        assertTrue(cat.containsKey("petersen"));
        assertTrue(cat.containsKey("karate"));
        assertTrue(cat.containsKey("butterfly"));
    }

    // ── byName lookup ───────────────────────────────────────

    @Test
    public void byName_case_insensitive() {
        assertNotNull(FamousGraphLibrary.byName("PETERSEN"));
        assertNotNull(FamousGraphLibrary.byName("Petersen"));
        assertNotNull(FamousGraphLibrary.byName("petersen"));
    }

    @Test
    public void byName_unknown_returns_null() {
        assertNull(FamousGraphLibrary.byName("nonexistent"));
    }

    @Test
    public void byName_null_returns_null() {
        assertNull(FamousGraphLibrary.byName(null));
    }

    @Test
    public void byName_all_catalog_entries_resolve() {
        for (String name : FamousGraphLibrary.catalog().keySet()) {
            Graph<String, Edge> g = FamousGraphLibrary.byName(name);
            assertNotNull("byName should resolve: " + name, g);
            assertTrue("Graph should have vertices: " + name, g.getVertexCount() > 0);
        }
    }
}
