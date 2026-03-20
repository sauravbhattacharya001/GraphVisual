package gvisual;

import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for CycleAnalyzer.
 *
 * @author zalenix
 */
public class CycleAnalyzerTest {

    // ── Helper methods ──────────────────────────────────────────

    private Graph<String, Edge> buildUndirected(String[][] edges) {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        int id = 0;
        for (String[] e : edges) {
            if (!g.containsVertex(e[0])) g.addVertex(e[0]);
            if (!g.containsVertex(e[1])) g.addVertex(e[1]);
            Edge ed  new Edge("link", e[0], e[1]);
            ed.setLabel("e" + id++);
            g.addEdge(ed, e[0], e[1]);
        }
        return g;
    }

    private Graph<String, Edge> buildDirected(String[][] edges) {
        Graph<String, Edge> g = new DirectedSparseGraph<String, Edge>();
        int id = 0;
        for (String[] e : edges) {
            if (!g.containsVertex(e[0])) g.addVertex(e[0]);
            if (!g.containsVertex(e[1])) g.addVertex(e[1]);
            Edge ed  new Edge("link", e[0], e[1]);
            ed.setLabel("e" + id++);
            g.addEdge(ed, e[0], e[1]);
        }
        return g;
    }

    private Graph<String, Edge> emptyUndirected() {
        return new UndirectedSparseGraph<String, Edge>();
    }

    private Graph<String, Edge> emptyDirected() {
        return new DirectedSparseGraph<String, Edge>();
    }

    private Graph<String, Edge> singleVertex(boolean directed) {
        Graph<String, Edge> g = directed
                ? new DirectedSparseGraph<String, Edge>()
                : new UndirectedSparseGraph<String, Edge>();
        g.addVertex("A");
        return g;
    }

    // ── Null input ──────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullGraph_throws() {
        new CycleAnalyzer(null);
    }

    // ── hasCycles: undirected ───────────────────────────────────

    @Test
    public void hasCycles_emptyUndirected_false() {
        assertFalse(new CycleAnalyzer(emptyUndirected()).hasCycles());
    }

    @Test
    public void hasCycles_singleVertexUndirected_false() {
        assertFalse(new CycleAnalyzer(singleVertex(false)).hasCycles());
    }

    @Test
    public void hasCycles_treeUndirected_false() {
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"B", "C"}, {"B", "D"}
        });
        assertFalse(new CycleAnalyzer(g).hasCycles());
    }

    @Test
    public void hasCycles_triangleUndirected_true() {
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"B", "C"}, {"C", "A"}
        });
        assertTrue(new CycleAnalyzer(g).hasCycles());
    }

    @Test
    public void hasCycles_squareUndirected_true() {
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"B", "C"}, {"C", "D"}, {"D", "A"}
        });
        assertTrue(new CycleAnalyzer(g).hasCycles());
    }

    @Test
    public void hasCycles_disconnectedWithOneCyclic_true() {
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"B", "C"}, {"C", "A"},  // triangle
                {"D", "E"}                            // separate tree edge
        });
        assertTrue(new CycleAnalyzer(g).hasCycles());
    }

    // ── hasCycles: directed ────────────────────────────────────

    @Test
    public void hasCycles_emptyDirected_false() {
        assertFalse(new CycleAnalyzer(emptyDirected()).hasCycles());
    }

    @Test
    public void hasCycles_singleVertexDirected_false() {
        assertFalse(new CycleAnalyzer(singleVertex(true)).hasCycles());
    }

    @Test
    public void hasCycles_dagDirected_false() {
        Graph<String, Edge> g = buildDirected(new String[][] {
                {"A", "B"}, {"A", "C"}, {"B", "D"}, {"C", "D"}
        });
        assertFalse(new CycleAnalyzer(g).hasCycles());
    }

    @Test
    public void hasCycles_directedTriangle_true() {
        Graph<String, Edge> g = buildDirected(new String[][] {
                {"A", "B"}, {"B", "C"}, {"C", "A"}
        });
        assertTrue(new CycleAnalyzer(g).hasCycles());
    }

    @Test
    public void hasCycles_selfLoop_directed_true() {
        Graph<String, Edge> g = buildDirected(new String[][] {
                {"A", "A"}
        });
        assertTrue(new CycleAnalyzer(g).hasCycles());
    }

    @Test
    public void hasCycles_twoNodeCycleDirected_true() {
        Graph<String, Edge> g = buildDirected(new String[][] {
                {"A", "B"}, {"B", "A"}
        });
        assertTrue(new CycleAnalyzer(g).hasCycles());
    }

    // ── Girth ──────────────────────────────────────────────────

    @Test
    public void girth_emptyGraph_negative1() {
        assertEquals(-1, new CycleAnalyzer(emptyUndirected()).girth());
    }

    @Test
    public void girth_tree_negative1() {
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"B", "C"}, {"C", "D"}
        });
        assertEquals(-1, new CycleAnalyzer(g).girth());
    }

    @Test
    public void girth_triangle_3() {
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"B", "C"}, {"C", "A"}
        });
        assertEquals(3, new CycleAnalyzer(g).girth());
    }

    @Test
    public void girth_square_4() {
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"B", "C"}, {"C", "D"}, {"D", "A"}
        });
        assertEquals(4, new CycleAnalyzer(g).girth());
    }

    @Test
    public void girth_squareWithDiagonal_3() {
        // Square ABCD with diagonal AC creates two triangles
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"B", "C"}, {"C", "D"}, {"D", "A"}, {"A", "C"}
        });
        assertEquals(3, new CycleAnalyzer(g).girth());
    }

    @Test
    public void girth_directed_triangle_3() {
        Graph<String, Edge> g = buildDirected(new String[][] {
                {"A", "B"}, {"B", "C"}, {"C", "A"}
        });
        assertEquals(3, new CycleAnalyzer(g).girth());
    }

    @Test
    public void girth_directed_selfLoop_1() {
        Graph<String, Edge> g = buildDirected(new String[][] {
                {"A", "A"}
        });
        assertEquals(1, new CycleAnalyzer(g).girth());
    }

    @Test
    public void girth_directed_twoNodeCycle_2() {
        Graph<String, Edge> g = buildDirected(new String[][] {
                {"A", "B"}, {"B", "A"}
        });
        assertEquals(2, new CycleAnalyzer(g).girth());
    }

    // ── Fundamental Cycle Basis ────────────────────────────────

    @Test
    public void fundamentalBasis_tree_empty() {
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"B", "C"}
        });
        List<CycleAnalyzer.Cycle> basis = new CycleAnalyzer(g).fundamentalCycleBasis();
        assertTrue(basis.isEmpty());
    }

    @Test
    public void fundamentalBasis_triangle_oneCycle() {
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"B", "C"}, {"C", "A"}
        });
        List<CycleAnalyzer.Cycle> basis = new CycleAnalyzer(g).fundamentalCycleBasis();
        assertEquals(1, basis.size());
        assertEquals(3, basis.get(0).length());
    }

    @Test
    public void fundamentalBasis_squareWithDiagonal_twoCycles() {
        // 4 vertices, 5 edges, 1 component → cyclomatic number = 5-4+1 = 2
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"B", "C"}, {"C", "D"}, {"D", "A"}, {"A", "C"}
        });
        List<CycleAnalyzer.Cycle> basis = new CycleAnalyzer(g).fundamentalCycleBasis();
        assertEquals(2, basis.size());
    }

    @Test
    public void fundamentalBasis_K4_threeCycles() {
        // K4: 4 vertices, 6 edges, 1 component → 6-4+1 = 3
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"A", "C"}, {"A", "D"},
                {"B", "C"}, {"B", "D"}, {"C", "D"}
        });
        List<CycleAnalyzer.Cycle> basis = new CycleAnalyzer(g).fundamentalCycleBasis();
        assertEquals(3, basis.size());
    }

    @Test
    public void fundamentalBasis_disconnected_correctCount() {
        // Two triangles: 6V, 6E, 2 components → 6-6+2 = 2
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"B", "C"}, {"C", "A"},
                {"D", "E"}, {"E", "F"}, {"F", "D"}
        });
        List<CycleAnalyzer.Cycle> basis = new CycleAnalyzer(g).fundamentalCycleBasis();
        assertEquals(2, basis.size());
    }

    @Test
    public void fundamentalBasis_emptyGraph_empty() {
        List<CycleAnalyzer.Cycle> basis = new CycleAnalyzer(emptyUndirected())
                .fundamentalCycleBasis();
        assertTrue(basis.isEmpty());
    }

    // ── All Simple Cycles ──────────────────────────────────────

    @Test
    public void allCycles_tree_none() {
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"B", "C"}
        });
        CycleAnalyzer.CycleEnumerationResult result =
                new CycleAnalyzer(g).findAllSimpleCycles();
        assertEquals(0, result.count());
        assertTrue(result.isComplete());
    }

    @Test
    public void allCycles_triangle_oneCycle() {
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"B", "C"}, {"C", "A"}
        });
        CycleAnalyzer.CycleEnumerationResult result =
                new CycleAnalyzer(g).findAllSimpleCycles();
        assertEquals(1, result.count());
        assertEquals(3, result.getCycles().get(0).length());
    }

    @Test
    public void allCycles_K4_sevenCycles() {
        // K4 has 7 simple cycles: 4 triangles + 3 four-cycles
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"A", "C"}, {"A", "D"},
                {"B", "C"}, {"B", "D"}, {"C", "D"}
        });
        CycleAnalyzer.CycleEnumerationResult result =
                new CycleAnalyzer(g).findAllSimpleCycles();
        assertEquals(7, result.count());
        assertTrue(result.isComplete());
    }

    @Test
    public void allCycles_directedTriangle_oneCycle() {
        Graph<String, Edge> g = buildDirected(new String[][] {
                {"A", "B"}, {"B", "C"}, {"C", "A"}
        });
        CycleAnalyzer.CycleEnumerationResult result =
                new CycleAnalyzer(g).findAllSimpleCycles();
        assertEquals(1, result.count());
    }

    @Test
    public void allCycles_directedTwoNodeCycle() {
        Graph<String, Edge> g = buildDirected(new String[][] {
                {"A", "B"}, {"B", "A"}
        });
        CycleAnalyzer.CycleEnumerationResult result =
                new CycleAnalyzer(g).findAllSimpleCycles();
        assertEquals(1, result.count());
        assertEquals(2, result.getCycles().get(0).length());
    }

    @Test
    public void allCycles_limit_respectsBound() {
        // K4 has 7 cycles; limit to 3
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"A", "C"}, {"A", "D"},
                {"B", "C"}, {"B", "D"}, {"C", "D"}
        });
        CycleAnalyzer.CycleEnumerationResult result =
                new CycleAnalyzer(g).findAllSimpleCycles(3);
        assertEquals(3, result.count());
        assertFalse(result.isComplete());
    }

    @Test(expected = IllegalArgumentException.class)
    public void allCycles_negativeLimit_throws() {
        new CycleAnalyzer(emptyUndirected()).findAllSimpleCycles(-1);
    }

    @Test
    public void allCycles_noDuplicates() {
        // Square ABCD: should find exactly 1 cycle
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"B", "C"}, {"C", "D"}, {"D", "A"}
        });
        CycleAnalyzer.CycleEnumerationResult result =
                new CycleAnalyzer(g).findAllSimpleCycles();
        assertEquals(1, result.count());
    }

    @Test
    public void allCycles_squareWithDiagonal() {
        // Square + diagonal: 3 cycles (2 triangles + 1 square)
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"B", "C"}, {"C", "D"}, {"D", "A"}, {"A", "C"}
        });
        CycleAnalyzer.CycleEnumerationResult result =
                new CycleAnalyzer(g).findAllSimpleCycles();
        assertEquals(3, result.count());
    }

    // ── Cycle class ────────────────────────────────────────────

    @Test
    public void cycle_length() {
        CycleAnalyzer.Cycle c = new CycleAnalyzer.Cycle(
                Arrays.asList("A", "B", "C"));
        assertEquals(3, c.length());
    }

    @Test
    public void cycle_toString() {
        CycleAnalyzer.Cycle c = new CycleAnalyzer.Cycle(
                Arrays.asList("A", "B", "C"));
        assertEquals("A \u2192 B \u2192 C \u2192 A", c.toString());
    }

    @Test
    public void cycle_equals_rotation() {
        CycleAnalyzer.Cycle c1 = new CycleAnalyzer.Cycle(
                Arrays.asList("A", "B", "C"));
        CycleAnalyzer.Cycle c2 = new CycleAnalyzer.Cycle(
                Arrays.asList("B", "C", "A"));
        assertEquals(c1, c2);
    }

    @Test
    public void cycle_notEqual_differentVertices() {
        CycleAnalyzer.Cycle c1 = new CycleAnalyzer.Cycle(
                Arrays.asList("A", "B", "C"));
        CycleAnalyzer.Cycle c2 = new CycleAnalyzer.Cycle(
                Arrays.asList("A", "B", "D"));
        assertNotEquals(c1, c2);
    }

    @Test
    public void cycle_totalWeight() {
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"B", "C"}, {"C", "A"}
        });
        // Set weights
        for (Edge e : g.getEdges()) {
            e.setWeight(2.0f);
        }
        CycleAnalyzer.Cycle c = new CycleAnalyzer.Cycle(
                Arrays.asList("A", "B", "C"));
        assertEquals(6.0f, c.totalWeight(g), 0.001f);
    }

    @Test
    public void cycle_immutableVertices() {
        CycleAnalyzer.Cycle c = new CycleAnalyzer.Cycle(
                Arrays.asList("A", "B", "C"));
        try {
            c.getVertices().add("D");
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    // ── Full Report ────────────────────────────────────────────

    @Test
    public void analyze_acyclicGraph() {
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"B", "C"}
        });
        CycleAnalyzer.CycleReport report = new CycleAnalyzer(g).analyze();

        assertFalse(report.hasCycles());
        assertEquals(-1, report.getGirth());
        assertEquals(-1, report.getCircumference());
        assertTrue(report.getFundamentalBasis().isEmpty());
        assertTrue(report.getAllCycles().isEmpty());
        assertTrue(report.getVertexParticipation().isEmpty());
        assertEquals(0, report.getCyclomaticNumber());
        assertEquals(0.0, report.getAverageCycleLength(), 0.01);
        assertNull(report.getMostCyclicVertex());
    }

    @Test
    public void analyze_triangle() {
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"B", "C"}, {"C", "A"}
        });
        CycleAnalyzer.CycleReport report = new CycleAnalyzer(g).analyze();

        assertTrue(report.hasCycles());
        assertEquals(3, report.getGirth());
        assertEquals(3, report.getCircumference());
        assertEquals(1, report.getCyclomaticNumber());
        assertEquals(1, report.getAllCycles().size());
        assertTrue(report.isAllCyclesComplete());
        assertEquals(3.0, report.getAverageCycleLength(), 0.01);
    }

    @Test
    public void analyze_K4_vertexParticipation() {
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"A", "C"}, {"A", "D"},
                {"B", "C"}, {"B", "D"}, {"C", "D"}
        });
        CycleAnalyzer.CycleReport report = new CycleAnalyzer(g).analyze();

        assertTrue(report.hasCycles());
        assertEquals(7, report.getAllCycles().size());

        // Every vertex in K4 should participate in cycles
        Map<String, Integer> participation = report.getVertexParticipation();
        assertTrue(participation.containsKey("A"));
        assertTrue(participation.containsKey("B"));
        assertTrue(participation.containsKey("C"));
        assertTrue(participation.containsKey("D"));

        // Each vertex in K4 is in exactly 6 of the 7 simple cycles
        // (excluded from the 1 cycle of the other 3 vertices)
        for (int count : participation.values()) {
            assertEquals(6, count);
        }
    }

    @Test
    public void analyze_summary_containsKey_info() {
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"B", "C"}, {"C", "A"}
        });
        String summary = new CycleAnalyzer(g).analyze().getSummary();

        assertTrue(summary.contains("Cyclic"));
        assertTrue(summary.contains("Girth"));
        assertTrue(summary.contains("Cyclomatic number"));
    }

    @Test
    public void analyze_summary_acyclic() {
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}
        });
        String summary = new CycleAnalyzer(g).analyze().getSummary();

        assertTrue(summary.contains("Acyclic"));
        assertTrue(summary.contains("No cycles"));
    }

    @Test
    public void analyze_directed_dag() {
        Graph<String, Edge> g = buildDirected(new String[][] {
                {"A", "B"}, {"A", "C"}, {"B", "D"}, {"C", "D"}
        });
        CycleAnalyzer.CycleReport report = new CycleAnalyzer(g).analyze();

        assertFalse(report.hasCycles());
        assertEquals(-1, report.getGirth());
    }

    @Test
    public void analyze_directed_withCycle() {
        Graph<String, Edge> g = buildDirected(new String[][] {
                {"A", "B"}, {"B", "C"}, {"C", "A"}, {"C", "D"}
        });
        CycleAnalyzer.CycleReport report = new CycleAnalyzer(g).analyze();

        assertTrue(report.hasCycles());
        assertEquals(3, report.getGirth());
        assertNotNull(report.getMostCyclicVertex());
    }

    @Test
    public void analyze_circumference_longestCycle() {
        // Pentagon: single 5-cycle
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"B", "C"}, {"C", "D"}, {"D", "E"}, {"E", "A"}
        });
        CycleAnalyzer.CycleReport report = new CycleAnalyzer(g).analyze();

        assertEquals(5, report.getCircumference());
        assertEquals(5, report.getGirth());
    }

    @Test
    public void analyze_withLimit_reportsIncomplete() {
        // K4 has 7 cycles, limit to 2
        Graph<String, Edge> g = buildUndirected(new String[][] {
                {"A", "B"}, {"A", "C"}, {"A", "D"},
                {"B", "C"}, {"B", "D"}, {"C", "D"}
        });
        CycleAnalyzer.CycleReport report = new CycleAnalyzer(g).analyze(2);

        assertTrue(report.hasCycles());
        assertEquals(2, report.getAllCycles().size());
        assertFalse(report.isAllCyclesComplete());
    }
}
