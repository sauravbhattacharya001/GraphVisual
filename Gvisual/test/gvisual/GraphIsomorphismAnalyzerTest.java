package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/**
 * Tests for GraphIsomorphismAnalyzer.
 *
 * @author zalenix
 */
public class GraphIsomorphismAnalyzerTest {

    // ── Helper methods ──────────────────────────────────────────

    /**
     * Build an undirected graph from edge pairs.
     * Each edge is [vertex1, vertex2].
     */
    private Graph<String, Edge> buildGraph(String[][] edges) {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        int edgeId = 0;
        for (String[] e : edges) {
            if (!g.containsVertex(e[0])) g.addVertex(e[0]);
            if (!g.containsVertex(e[1])) g.addVertex(e[1]);
            edge ed = new Edge("c", e[0], e[1]);
            ed.setLabel("e" + edgeId++);
            g.addEdge(ed, e[0], e[1]);
        }
        return g;
    }

    /**
     * Build a graph with only isolated vertices.
     */
    private Graph<String, Edge> buildIsolated(String... vertices) {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        for (String v : vertices) g.addVertex(v);
        return g;
    }

    // ── Constructor ─────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullGraph1_throws() {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        new GraphIsomorphismAnalyzer(null, g);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullGraph2_throws() {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        new GraphIsomorphismAnalyzer(g, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_bothNull_throws() {
        new GraphIsomorphismAnalyzer(null, null);
    }

    // ── Empty graphs ────────────────────────────────────────────

    @Test
    public void analyze_twoEmptyGraphs_isomorphic() {
        Graph<String, Edge> g1 = new UndirectedSparseGraph<String, Edge>();
        Graph<String, Edge> g2 = new UndirectedSparseGraph<String, Edge>();
        GraphIsomorphismAnalyzer analyzer =
                new GraphIsomorphismAnalyzer(g1, g2);
        GraphIsomorphismAnalyzer.IsomorphismResult result = analyzer.analyze();

        assertTrue(result.isIsomorphic());
        assertTrue(result.getMapping().isEmpty());
        assertNull(result.getRejectionReason());
    }

    // ── Single vertex ───────────────────────────────────────────

    @Test
    public void analyze_singleVertexEach_isomorphic() {
        Graph<String, Edge> g1 = buildIsolated("A");
        Graph<String, Edge> g2 = buildIsolated("X");

        GraphIsomorphismAnalyzer.IsomorphismResult result =
                new GraphIsomorphismAnalyzer(g1, g2).analyze();

        assertTrue(result.isIsomorphic());
        assertEquals(1, result.getMapping().size());
        assertEquals("X", result.getMapping().get("A"));
    }

    // ── Identical graphs ────────────────────────────────────────

    @Test
    public void analyze_identicalTriangles_isomorphic() {
        Graph<String, Edge> g1 = buildGraph(new String[][]{
                {"A", "B"}, {"B", "C"}, {"C", "A"}
        });
        Graph<String, Edge> g2 = buildGraph(new String[][]{
                {"X", "Y"}, {"Y", "Z"}, {"Z", "X"}
        });

        GraphIsomorphismAnalyzer.IsomorphismResult result =
                new GraphIsomorphismAnalyzer(g1, g2).analyze();

        assertTrue(result.isIsomorphic());
        assertEquals(3, result.getMapping().size());
        // Verify mapping preserves edges
        verifyMapping(g1, g2, result.getMapping());
    }

    @Test
    public void analyze_identicalPaths_isomorphic() {
        // Path: A-B-C-D
        Graph<String, Edge> g1 = buildGraph(new String[][]{
                {"A", "B"}, {"B", "C"}, {"C", "D"}
        });
        // Path: W-X-Y-Z
        Graph<String, Edge> g2 = buildGraph(new String[][]{
                {"W", "X"}, {"X", "Y"}, {"Y", "Z"}
        });

        GraphIsomorphismAnalyzer.IsomorphismResult result =
                new GraphIsomorphismAnalyzer(g1, g2).analyze();

        assertTrue(result.isIsomorphic());
        verifyMapping(g1, g2, result.getMapping());
    }

    @Test
    public void analyze_identicalStars_isomorphic() {
        // Star: center A connected to B, C, D, E
        Graph<String, Edge> g1 = buildGraph(new String[][]{
                {"A", "B"}, {"A", "C"}, {"A", "D"}, {"A", "E"}
        });
        // Star: center M connected to N, O, P, Q
        Graph<String, Edge> g2 = buildGraph(new String[][]{
                {"M", "N"}, {"M", "O"}, {"M", "P"}, {"M", "Q"}
        });

        GraphIsomorphismAnalyzer.IsomorphismResult result =
                new GraphIsomorphismAnalyzer(g1, g2).analyze();

        assertTrue(result.isIsomorphic());
        verifyMapping(g1, g2, result.getMapping());
    }

    @Test
    public void analyze_completeK4_isomorphic() {
        Graph<String, Edge> g1 = buildGraph(new String[][]{
                {"A", "B"}, {"A", "C"}, {"A", "D"},
                {"B", "C"}, {"B", "D"}, {"C", "D"}
        });
        Graph<String, Edge> g2 = buildGraph(new String[][]{
                {"W", "X"}, {"W", "Y"}, {"W", "Z"},
                {"X", "Y"}, {"X", "Z"}, {"Y", "Z"}
        });

        GraphIsomorphismAnalyzer.IsomorphismResult result =
                new GraphIsomorphismAnalyzer(g1, g2).analyze();

        assertTrue(result.isIsomorphic());
        verifyMapping(g1, g2, result.getMapping());
    }

    // ── Non-isomorphic graphs ───────────────────────────────────

    @Test
    public void analyze_differentVertexCount_notIsomorphic() {
        Graph<String, Edge> g1 = buildGraph(new String[][]{
                {"A", "B"}, {"B", "C"}
        });
        Graph<String, Edge> g2 = buildGraph(new String[][]{
                {"X", "Y"}, {"Y", "Z"}, {"Z", "W"}
        });

        GraphIsomorphismAnalyzer.IsomorphismResult result =
                new GraphIsomorphismAnalyzer(g1, g2).analyze();

        assertFalse(result.isIsomorphic());
        assertTrue(result.getRejectionReason().contains("vertex count"));
    }

    @Test
    public void analyze_differentEdgeCount_notIsomorphic() {
        // Triangle vs path of 3
        Graph<String, Edge> g1 = buildGraph(new String[][]{
                {"A", "B"}, {"B", "C"}, {"C", "A"}
        });
        Graph<String, Edge> g2 = buildGraph(new String[][]{
                {"X", "Y"}, {"Y", "Z"}
        });
        g2.addVertex("W"); // add isolated vertex to match vertex count

        // g1: 3 vertices 3 edges, g2: 4 vertices 2 edges → vertex count diff
        // Let's make same vertex count
        Graph<String, Edge> g2b = buildGraph(new String[][]{
                {"X", "Y"}, {"Y", "Z"}
        });
        // g1: 3v 3e, g2b: 3v 2e

        GraphIsomorphismAnalyzer.IsomorphismResult result =
                new GraphIsomorphismAnalyzer(g1, g2b).analyze();

        assertFalse(result.isIsomorphic());
        assertTrue(result.getRejectionReason().contains("edge count"));
    }

    @Test
    public void analyze_differentDegreeSequence_notIsomorphic() {
        // Star: center A → B, C, D (degrees: 3,1,1,1)
        Graph<String, Edge> g1 = buildGraph(new String[][]{
                {"A", "B"}, {"A", "C"}, {"A", "D"}
        });
        // Path: X-Y-Z-W (degrees: 1,2,2,1)
        Graph<String, Edge> g2 = buildGraph(new String[][]{
                {"X", "Y"}, {"Y", "Z"}, {"Z", "W"}
        });

        GraphIsomorphismAnalyzer.IsomorphismResult result =
                new GraphIsomorphismAnalyzer(g1, g2).analyze();

        assertFalse(result.isIsomorphic());
        assertTrue(result.getRejectionReason().contains("degree sequence"));
    }

    @Test
    public void analyze_sameDegreeSequence_butNotIsomorphic() {
        // Two graphs with degree sequence [2,2,2,2,2,2] but different structure
        // Cycle C6: A-B-C-D-E-F-A
        Graph<String, Edge> g1 = buildGraph(new String[][]{
                {"A", "B"}, {"B", "C"}, {"C", "D"},
                {"D", "E"}, {"E", "F"}, {"F", "A"}
        });
        // Two triangles: X-Y-Z-X and P-Q-R-P
        Graph<String, Edge> g2 = buildGraph(new String[][]{
                {"X", "Y"}, {"Y", "Z"}, {"Z", "X"},
                {"P", "Q"}, {"Q", "R"}, {"R", "P"}
        });

        GraphIsomorphismAnalyzer.IsomorphismResult result =
                new GraphIsomorphismAnalyzer(g1, g2).analyze();

        assertFalse(result.isIsomorphic());
        // Both have same degree sequence but different connectivity
        assertTrue(result.getRejectionReason().contains("structural mismatch"));
    }

    // ── Isolated vertices ───────────────────────────────────────

    @Test
    public void analyze_isolatedVertices_isomorphic() {
        Graph<String, Edge> g1 = buildIsolated("A", "B", "C");
        Graph<String, Edge> g2 = buildIsolated("X", "Y", "Z");

        GraphIsomorphismAnalyzer.IsomorphismResult result =
                new GraphIsomorphismAnalyzer(g1, g2).analyze();

        assertTrue(result.isIsomorphic());
        assertEquals(3, result.getMapping().size());
    }

    @Test
    public void analyze_differentIsolatedCount_notIsomorphic() {
        Graph<String, Edge> g1 = buildIsolated("A", "B");
        Graph<String, Edge> g2 = buildIsolated("X", "Y", "Z");

        GraphIsomorphismAnalyzer.IsomorphismResult result =
                new GraphIsomorphismAnalyzer(g1, g2).analyze();

        assertFalse(result.isIsomorphic());
    }

    // ── areIsomorphic shortcut ──────────────────────────────────

    @Test
    public void areIsomorphic_returns_true_for_matching_graphs() {
        Graph<String, Edge> g1 = buildGraph(new String[][]{
                {"A", "B"}, {"B", "C"}
        });
        Graph<String, Edge> g2 = buildGraph(new String[][]{
                {"X", "Y"}, {"Y", "Z"}
        });

        assertTrue(new GraphIsomorphismAnalyzer(g1, g2).areIsomorphic());
    }

    @Test
    public void areIsomorphic_returns_false_for_nonMatching() {
        Graph<String, Edge> g1 = buildGraph(new String[][]{
                {"A", "B"}, {"B", "C"}, {"C", "A"}
        });
        Graph<String, Edge> g2 = buildGraph(new String[][]{
                {"X", "Y"}, {"Y", "Z"}
        });
        g2.addVertex("W");

        assertFalse(new GraphIsomorphismAnalyzer(g1, g2).areIsomorphic());
    }

    // ── Degree sequences ────────────────────────────────────────

    @Test
    public void analyze_degreeSequencesReturned() {
        Graph<String, Edge> g1 = buildGraph(new String[][]{
                {"A", "B"}, {"A", "C"}
        });
        Graph<String, Edge> g2 = buildGraph(new String[][]{
                {"X", "Y"}, {"X", "Z"}
        });

        GraphIsomorphismAnalyzer.IsomorphismResult result =
                new GraphIsomorphismAnalyzer(g1, g2).analyze();

        assertEquals(Arrays.asList(1, 1, 2), result.getDegreeSequence1());
        assertEquals(Arrays.asList(1, 1, 2), result.getDegreeSequence2());
    }

    // ── Complex graphs ──────────────────────────────────────────

    @Test
    public void analyze_petersenLike_isomorphic() {
        // Build two copies of the Petersen graph with different labels
        // Outer cycle: 0-1-2-3-4-0, inner star: 5-7-9-6-8-5
        Graph<String, Edge> g1 = buildGraph(new String[][]{
                {"0", "1"}, {"1", "2"}, {"2", "3"}, {"3", "4"}, {"4", "0"},
                {"0", "5"}, {"1", "6"}, {"2", "7"}, {"3", "8"}, {"4", "9"},
                {"5", "7"}, {"7", "9"}, {"9", "6"}, {"6", "8"}, {"8", "5"}
        });
        Graph<String, Edge> g2 = buildGraph(new String[][]{
                {"A", "B"}, {"B", "C"}, {"C", "D"}, {"D", "E"}, {"E", "A"},
                {"A", "F"}, {"B", "G"}, {"C", "H"}, {"D", "I"}, {"E", "J"},
                {"F", "H"}, {"H", "J"}, {"J", "G"}, {"G", "I"}, {"I", "F"}
        });

        GraphIsomorphismAnalyzer.IsomorphismResult result =
                new GraphIsomorphismAnalyzer(g1, g2).analyze();

        assertTrue("Petersen graphs should be isomorphic",
                result.isIsomorphic());
        verifyMapping(g1, g2, result.getMapping());
    }

    @Test
    public void analyze_bipartiteK23_isomorphic() {
        // K2,3: {A,B} fully connected to {C,D,E}
        Graph<String, Edge> g1 = buildGraph(new String[][]{
                {"A", "C"}, {"A", "D"}, {"A", "E"},
                {"B", "C"}, {"B", "D"}, {"B", "E"}
        });
        // Same with different labels
        Graph<String, Edge> g2 = buildGraph(new String[][]{
                {"P", "R"}, {"P", "S"}, {"P", "T"},
                {"Q", "R"}, {"Q", "S"}, {"Q", "T"}
        });

        assertTrue(new GraphIsomorphismAnalyzer(g1, g2).areIsomorphic());
    }

    // ── toString ────────────────────────────────────────────────

    @Test
    public void toString_isomorphic_showsMapping() {
        Graph<String, Edge> g1 = buildIsolated("A");
        Graph<String, Edge> g2 = buildIsolated("X");
        GraphIsomorphismAnalyzer.IsomorphismResult result =
                new GraphIsomorphismAnalyzer(g1, g2).analyze();

        String str = result.toString();
        assertTrue(str.contains("Isomorphic"));
        assertTrue(str.contains("mapping"));
    }

    @Test
    public void toString_notIsomorphic_showsReason() {
        Graph<String, Edge> g1 = buildIsolated("A", "B");
        Graph<String, Edge> g2 = buildIsolated("X");
        GraphIsomorphismAnalyzer.IsomorphismResult result =
                new GraphIsomorphismAnalyzer(g1, g2).analyze();

        String str = result.toString();
        assertTrue(str.contains("Not isomorphic"));
    }

    // ── Graph with mixed connectivity ───────────────────────────

    @Test
    public void analyze_graphWithIsolatedAndConnected_isomorphic() {
        // Graph: A-B, C isolated
        Graph<String, Edge> g1 = buildGraph(new String[][]{{"A", "B"}});
        g1.addVertex("C");

        Graph<String, Edge> g2 = buildGraph(new String[][]{{"X", "Y"}});
        g2.addVertex("Z");

        assertTrue(new GraphIsomorphismAnalyzer(g1, g2).areIsomorphic());
    }

    @Test
    public void analyze_selfIsomorphic() {
        Graph<String, Edge> g = buildGraph(new String[][]{
                {"A", "B"}, {"B", "C"}, {"C", "D"}, {"D", "A"}
        });

        assertTrue(new GraphIsomorphismAnalyzer(g, g).areIsomorphic());
    }

    // ── Mapping immutability ────────────────────────────────────

    @Test(expected = UnsupportedOperationException.class)
    public void mapping_isUnmodifiable() {
        Graph<String, Edge> g1 = buildIsolated("A");
        Graph<String, Edge> g2 = buildIsolated("X");
        GraphIsomorphismAnalyzer.IsomorphismResult result =
                new GraphIsomorphismAnalyzer(g1, g2).analyze();
        result.getMapping().put("B", "Y");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void degreeSequence_isUnmodifiable() {
        Graph<String, Edge> g1 = buildIsolated("A");
        Graph<String, Edge> g2 = buildIsolated("X");
        GraphIsomorphismAnalyzer.IsomorphismResult result =
                new GraphIsomorphismAnalyzer(g1, g2).analyze();
        result.getDegreeSequence1().add(42);
    }

    // ── Verify mapping helper ───────────────────────────────────

    /**
     * Verify that a mapping preserves all edges:
     * for every edge (u, v) in g1, (mapping[u], mapping[v]) must be
     * an edge in g2.
     */
    private void verifyMapping(Graph<String, Edge> g1,
                                Graph<String, Edge> g2,
                                Map<String, String> mapping) {
        for (Edge e : g1.getEdges()) {
            String u1 = e.getVertex1();
            String v1 = e.getVertex2();
            // JUNG undirected edges might have endpoints in either order
            Collection<String> endpoints = g1.getEndpoints(e);
            Iterator<String> it = endpoints.iterator();
            u1 = it.next();
            v1 = it.next();

            String u2 = mapping.get(u1);
            String v2 = mapping.get(v1);
            assertNotNull("Mapping missing for " + u1, u2);
            assertNotNull("Mapping missing for " + v1, v2);
            assertTrue("Edge (" + u2 + "," + v2 + ") missing in g2",
                    g2.isNeighbor(u2, v2));
        }
    }
}
