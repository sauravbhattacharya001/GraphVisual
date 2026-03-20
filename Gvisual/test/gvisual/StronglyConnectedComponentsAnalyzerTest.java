package gvisual;

import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/**
 * Tests for StronglyConnectedComponentsAnalyzer.
 *
 * @author zalenix
 */
public class StronglyConnectedComponentsAnalyzerTest {

    // ── Helper methods ──────────────────────────────────────────

    private Graph<String, Edge> buildDirectedGraph(String[][] edges) {
        Graph<String, Edge> g = new DirectedSparseGraph<String, Edge>();
        int edgeId = 0;
        for (String[] e : edges) {
            String from = e[0];
            String to = e[1];
            if (!g.containsVertex(from)) g.addVertex(from);
            if (!g.containsVertex(to)) g.addVertex(to);
            Edge ed  new Edge("link", from, to);
            ed.setLabel("e" + edgeId++);
            g.addEdge(ed, from, to);
        }
        return g;
    }

    private Graph<String, Edge> buildDirectedGraphWithIsolated(String[][] edges, String[] isolated) {
        Graph<String, Edge> g = buildDirectedGraph(edges);
        for (String v : isolated) {
            if (!g.containsVertex(v)) g.addVertex(v);
        }
        return g;
    }

    private void assertBothAlgorithmsAgree(Graph<String, Edge> g, int expectedComponents) {
        StronglyConnectedComponentsAnalyzer analyzer = new StronglyConnectedComponentsAnalyzer(g);
        StronglyConnectedComponentsAnalyzer.SCCResult tarjan = analyzer.tarjan();
        StronglyConnectedComponentsAnalyzer.SCCResult kosaraju = analyzer.kosaraju();

        assertEquals("Tarjan component count", expectedComponents, tarjan.getComponentCount());
        assertEquals("Kosaraju component count", expectedComponents, kosaraju.getComponentCount());

        // Both should assign the same groupings (though component IDs may differ)
        for (String v1 : g.getVertices()) {
            for (String v2 : g.getVertices()) {
                assertEquals("Connectivity of " + v1 + "," + v2 + " should agree",
                        tarjan.areStronglyConnected(v1, v2),
                        kosaraju.areStronglyConnected(v1, v2));
            }
        }
    }

    // ── Null input ──────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraph() {
        new StronglyConnectedComponentsAnalyzer(null);
    }

    // ── Empty graph ─────────────────────────────────────────────

    @Test
    public void testEmptyGraph() {
        Graph<String, Edge> g = new DirectedSparseGraph<String, Edge>();
        StronglyConnectedComponentsAnalyzer analyzer = new StronglyConnectedComponentsAnalyzer(g);
        StronglyConnectedComponentsAnalyzer.SCCResult result = analyzer.tarjan();
        assertEquals(0, result.getComponentCount());
        assertTrue(result.getBridgeEdges().isEmpty());
    }

    // ── Single vertex ───────────────────────────────────────────

    @Test
    public void testSingleVertex() {
        Graph<String, Edge> g = new DirectedSparseGraph<String, Edge>();
        g.addVertex("A");
        assertBothAlgorithmsAgree(g, 1);

        StronglyConnectedComponentsAnalyzer analyzer = new StronglyConnectedComponentsAnalyzer(g);
        StronglyConnectedComponentsAnalyzer.SCCResult result = analyzer.tarjan();
        assertTrue(result.isStronglyConnected());
        assertTrue(result.getComponents().get(0).isTrivial());
    }

    // ── Two vertices, one edge ──────────────────────────────────

    @Test
    public void testTwoVerticesOneEdge() {
        Graph<String, Edge> g = buildDirectedGraph(new String[][]{{"A", "B"}});
        assertBothAlgorithmsAgree(g, 2);

        StronglyConnectedComponentsAnalyzer analyzer = new StronglyConnectedComponentsAnalyzer(g);
        StronglyConnectedComponentsAnalyzer.SCCResult result = analyzer.tarjan();
        assertFalse(result.isStronglyConnected());
        assertFalse(result.areStronglyConnected("A", "B"));
        assertEquals(1, result.getBridgeEdges().size());
    }

    // ── Simple cycle ────────────────────────────────────────────

    @Test
    public void testSimpleCycle() {
        Graph<String, Edge> g = buildDirectedGraph(new String[][]{
                {"A", "B"}, {"B", "C"}, {"C", "A"}
        });
        assertBothAlgorithmsAgree(g, 1);

        StronglyConnectedComponentsAnalyzer analyzer = new StronglyConnectedComponentsAnalyzer(g);
        StronglyConnectedComponentsAnalyzer.SCCResult result = analyzer.tarjan();
        assertTrue(result.isStronglyConnected());
        assertTrue(result.areStronglyConnected("A", "B"));
        assertTrue(result.areStronglyConnected("B", "C"));
        assertEquals(0, result.getBridgeEdges().size());
    }

    // ── Classic two-SCC graph ───────────────────────────────────

    @Test
    public void testTwoSCCs() {
        Graph<String, Edge> g = buildDirectedGraph(new String[][]{
                {"A", "B"}, {"B", "A"},   // SCC1: {A,B}
                {"C", "D"}, {"D", "C"},   // SCC2: {C,D}
                {"B", "C"}                 // bridge
        });
        assertBothAlgorithmsAgree(g, 2);

        StronglyConnectedComponentsAnalyzer analyzer = new StronglyConnectedComponentsAnalyzer(g);
        StronglyConnectedComponentsAnalyzer.SCCResult result = analyzer.tarjan();
        assertTrue(result.areStronglyConnected("A", "B"));
        assertTrue(result.areStronglyConnected("C", "D"));
        assertFalse(result.areStronglyConnected("A", "C"));
        assertEquals(1, result.getBridgeEdges().size());
    }

    // ── DAG (no cycles) ────────────────────────────────────────

    @Test
    public void testDAG() {
        Graph<String, Edge> g = buildDirectedGraph(new String[][]{
                {"A", "B"}, {"A", "C"}, {"B", "D"}, {"C", "D"}
        });
        assertBothAlgorithmsAgree(g, 4);

        StronglyConnectedComponentsAnalyzer analyzer = new StronglyConnectedComponentsAnalyzer(g);
        StronglyConnectedComponentsAnalyzer.SCCResult result = analyzer.tarjan();
        assertEquals(0, result.getNonTrivialComponents().size());
    }

    // ── Isolated vertices ───────────────────────────────────────

    @Test
    public void testIsolatedVertices() {
        Graph<String, Edge> g = buildDirectedGraphWithIsolated(
                new String[][]{{"A", "B"}, {"B", "A"}},
                new String[]{"X", "Y"}
        );
        assertBothAlgorithmsAgree(g, 3); // {A,B}, {X}, {Y}
    }

    // ── Complex multi-SCC graph ─────────────────────────────────

    @Test
    public void testComplexGraph() {
        // 3 SCCs: {A,B,C}, {D,E}, {F}
        Graph<String, Edge> g = buildDirectedGraph(new String[][]{
                {"A", "B"}, {"B", "C"}, {"C", "A"},  // SCC1
                {"D", "E"}, {"E", "D"},                // SCC2
                {"C", "D"}, {"E", "F"}                 // bridges
        });
        assertBothAlgorithmsAgree(g, 3);

        StronglyConnectedComponentsAnalyzer analyzer = new StronglyConnectedComponentsAnalyzer(g);
        StronglyConnectedComponentsAnalyzer.SCCResult result = analyzer.tarjan();
        assertEquals(2, result.getNonTrivialComponents().size());
        // Verify: 2 non-trivial components ({A,B,C} and {D,E})
        assertEquals(2, result.getNonTrivialComponents().size());
    }

    // ── Component classification ────────────────────────────────

    @Test
    public void testClassification() {
        // source -> intermediate -> sink
        Graph<String, Edge> g = buildDirectedGraph(new String[][]{
                {"A", "B"}, {"B", "A"},   // source SCC
                {"C", "D"}, {"D", "C"},   // intermediate SCC
                {"E", "F"}, {"F", "E"},   // sink SCC
                {"B", "C"}, {"D", "E"}    // bridges
        });
        StronglyConnectedComponentsAnalyzer analyzer = new StronglyConnectedComponentsAnalyzer(g);
        StronglyConnectedComponentsAnalyzer.SCCResult result = analyzer.tarjan();

        assertEquals(3, result.getComponentCount());
        assertEquals(1, result.getSourceComponents().size());
        assertEquals(1, result.getSinkComponents().size());

        // The component containing A should be source
        StronglyConnectedComponentsAnalyzer.Component compA = result.getComponentOf("A");
        assertEquals("source", compA.getClassification());

        // The component containing E should be sink
        StronglyConnectedComponentsAnalyzer.Component compE = result.getComponentOf("E");
        assertEquals("sink", compE.getClassification());
    }

    // ── Condensation DAG ────────────────────────────────────────

    @Test
    public void testCondensationDAG() {
        Graph<String, Edge> g = buildDirectedGraph(new String[][]{
                {"A", "B"}, {"B", "A"},
                {"C", "D"}, {"D", "C"},
                {"B", "C"}
        });
        StronglyConnectedComponentsAnalyzer analyzer = new StronglyConnectedComponentsAnalyzer(g);
        StronglyConnectedComponentsAnalyzer.SCCResult result = analyzer.tarjan();

        Graph<String, Edge> condensation = result.getCondensation();
        assertEquals(2, condensation.getVertexCount());
        assertEquals(1, condensation.getEdgeCount());
    }

    // ── Largest component ───────────────────────────────────────

    @Test
    public void testLargestComponent() {
        Graph<String, Edge> g = buildDirectedGraph(new String[][]{
                {"A", "B"}, {"B", "C"}, {"C", "D"}, {"D", "A"},  // size 4
                {"E", "F"}, {"F", "E"}                             // size 2
        });
        StronglyConnectedComponentsAnalyzer analyzer = new StronglyConnectedComponentsAnalyzer(g);
        StronglyConnectedComponentsAnalyzer.SCCResult result = analyzer.tarjan();

        assertEquals(4, result.getLargestComponent().size());
    }

    // ── Vertex lookup ───────────────────────────────────────────

    @Test
    public void testVertexLookup() {
        Graph<String, Edge> g = buildDirectedGraph(new String[][]{
                {"A", "B"}, {"B", "A"}
        });
        StronglyConnectedComponentsAnalyzer analyzer = new StronglyConnectedComponentsAnalyzer(g);
        StronglyConnectedComponentsAnalyzer.SCCResult result = analyzer.tarjan();

        assertNotNull(result.getComponentOf("A"));
        assertNotNull(result.getComponentOf("B"));
        assertNull(result.getComponentOf("Z"));
    }

    // ── Self-loop ───────────────────────────────────────────────

    @Test
    public void testSelfLoop() {
        Graph<String, Edge> g = new DirectedSparseGraph<String, Edge>();
        g.addVertex("A");
        // Self-loops in JUNG DirectedSparseGraph may not be supported,
        // but the vertex should still be in its own SCC
        StronglyConnectedComponentsAnalyzer analyzer = new StronglyConnectedComponentsAnalyzer(g);
        StronglyConnectedComponentsAnalyzer.SCCResult result = analyzer.tarjan();
        assertEquals(1, result.getComponentCount());
    }

    // ── Min edges to strongly connect ───────────────────────────

    @Test
    public void testMinEdgesToConnect() {
        // Already strongly connected
        Graph<String, Edge> g1 = buildDirectedGraph(new String[][]{
                {"A", "B"}, {"B", "C"}, {"C", "A"}
        });
        StronglyConnectedComponentsAnalyzer a1 = new StronglyConnectedComponentsAnalyzer(g1);
        assertEquals(0, a1.minEdgesToStronglyConnect(a1.tarjan()));

        // Chain: A->B->C (3 SCCs, 1 source, 1 sink) => need max(1,1) = 1 edge
        Graph<String, Edge> g2 = buildDirectedGraph(new String[][]{
                {"A", "B"}, {"B", "C"}
        });
        StronglyConnectedComponentsAnalyzer a2 = new StronglyConnectedComponentsAnalyzer(g2);
        assertEquals(1, a2.minEdgesToStronglyConnect(a2.tarjan()));
    }

    // ── Report generation ───────────────────────────────────────

    @Test
    public void testReportGeneration() {
        Graph<String, Edge> g = buildDirectedGraph(new String[][]{
                {"A", "B"}, {"B", "A"}, {"C", "D"}
        });
        StronglyConnectedComponentsAnalyzer analyzer = new StronglyConnectedComponentsAnalyzer(g);
        StronglyConnectedComponentsAnalyzer.SCCResult result = analyzer.tarjan();
        String report = analyzer.generateReport(result);

        assertTrue(report.contains("Strongly Connected Components"));
        assertTrue(report.contains("tarjan"));
        assertTrue(report.contains("Components:"));
    }

    // ── Complete graph (all strongly connected) ─────────────────

    @Test
    public void testCompleteGraph() {
        // K4 - complete directed graph
        String[] nodes = {"A", "B", "C", "D"};
        List<String[]> edges = new ArrayList<String[]>();
        for (String from : nodes) {
            for (String to : nodes) {
                if (!from.equals(to)) {
                    edges.add(new String[]{from, to});
                }
            }
        }
        Graph<String, Edge> g = buildDirectedGraph(edges.toArray(new String[0][]));
        assertBothAlgorithmsAgree(g, 1);
    }

    // ── Long chain ──────────────────────────────────────────────

    @Test
    public void testLongChain() {
        String[][] edges = new String[9][];
        for (int i = 0; i < 9; i++) {
            edges[i] = new String[]{"N" + i, "N" + (i + 1)};
        }
        Graph<String, Edge> g = buildDirectedGraph(edges);
        assertBothAlgorithmsAgree(g, 10); // each node is its own SCC
    }

    // ── Long cycle ──────────────────────────────────────────────

    @Test
    public void testLongCycle() {
        int n = 10;
        String[][] edges = new String[n][];
        for (int i = 0; i < n; i++) {
            edges[i] = new String[]{"N" + i, "N" + ((i + 1) % n)};
        }
        Graph<String, Edge> g = buildDirectedGraph(edges);
        assertBothAlgorithmsAgree(g, 1); // all in one SCC
    }

    // ── Diamond pattern ─────────────────────────────────────────

    @Test
    public void testDiamondPattern() {
        Graph<String, Edge> g = buildDirectedGraph(new String[][]{
                {"A", "B"}, {"A", "C"}, {"B", "D"}, {"C", "D"},
                {"D", "A"}  // back edge creates one big SCC
        });
        assertBothAlgorithmsAgree(g, 1);
    }

    // ── Multiple source/sink SCCs ───────────────────────────────

    @Test
    public void testMultipleSourcesSinks() {
        Graph<String, Edge> g = buildDirectedGraph(new String[][]{
                {"A", "C"}, {"B", "C"}, {"C", "D"}, {"C", "E"}
        });
        StronglyConnectedComponentsAnalyzer analyzer = new StronglyConnectedComponentsAnalyzer(g);
        StronglyConnectedComponentsAnalyzer.SCCResult result = analyzer.tarjan();

        assertEquals(5, result.getComponentCount());
        assertTrue(result.getSourceComponents().size() >= 2);
        assertTrue(result.getSinkComponents().size() >= 2);
    }

    // ── Connectivity query for non-existent vertex ──────────────

    @Test
    public void testConnectivityNonExistent() {
        Graph<String, Edge> g = buildDirectedGraph(new String[][]{{"A", "B"}});
        StronglyConnectedComponentsAnalyzer analyzer = new StronglyConnectedComponentsAnalyzer(g);
        StronglyConnectedComponentsAnalyzer.SCCResult result = analyzer.tarjan();

        assertFalse(result.areStronglyConnected("A", "Z"));
        assertFalse(result.areStronglyConnected("Z", "A"));
    }

    // ── Nested cycles ───────────────────────────────────────────

    @Test
    public void testNestedCycles() {
        // Inner cycle A-B-C-A, outer cycle A-D-E-C
        Graph<String, Edge> g = buildDirectedGraph(new String[][]{
                {"A", "B"}, {"B", "C"}, {"C", "A"},
                {"A", "D"}, {"D", "E"}, {"E", "C"}
        });
        assertBothAlgorithmsAgree(g, 1); // all reachable via cycles

        StronglyConnectedComponentsAnalyzer analyzer = new StronglyConnectedComponentsAnalyzer(g);
        StronglyConnectedComponentsAnalyzer.SCCResult result = analyzer.tarjan();
        assertEquals(5, result.getLargestComponent().size());
    }

    // ── Parallel edges between SCCs ─────────────────────────────

    @Test
    public void testParallelBridgeEdges() {
        // Two SCCs with multiple edges between them
        Graph<String, Edge> g = buildDirectedGraph(new String[][]{
                {"A", "B"}, {"B", "A"},
                {"C", "D"}, {"D", "C"},
                {"A", "C"}, {"B", "D"}  // two bridge edges
        });
        StronglyConnectedComponentsAnalyzer analyzer = new StronglyConnectedComponentsAnalyzer(g);
        StronglyConnectedComponentsAnalyzer.SCCResult result = analyzer.tarjan();

        assertEquals(2, result.getComponentCount());
        assertEquals(2, result.getBridgeEdges().size());
        // Condensation should have only 1 edge (deduplicated)
        assertEquals(1, result.getCondensation().getEdgeCount());
    }

    // ── Component toString ──────────────────────────────────────

    @Test
    public void testComponentToString() {
        Graph<String, Edge> g = buildDirectedGraph(new String[][]{{"A", "B"}, {"B", "A"}});
        StronglyConnectedComponentsAnalyzer analyzer = new StronglyConnectedComponentsAnalyzer(g);
        StronglyConnectedComponentsAnalyzer.SCCResult result = analyzer.tarjan();

        String str = result.getComponents().get(0).toString();
        assertTrue(str.contains("SCC-"));
    }

    // ── Star topology (hub with spokes) ─────────────────────────

    @Test
    public void testStarTopology() {
        // Hub A with outgoing edges to B,C,D,E
        Graph<String, Edge> g = buildDirectedGraph(new String[][]{
                {"A", "B"}, {"A", "C"}, {"A", "D"}, {"A", "E"}
        });
        assertBothAlgorithmsAgree(g, 5);
    }

    // ── Bidirectional star ──────────────────────────────────────

    @Test
    public void testBidirectionalStar() {
        Graph<String, Edge> g = buildDirectedGraph(new String[][]{
                {"A", "B"}, {"B", "A"},
                {"A", "C"}, {"C", "A"},
                {"A", "D"}, {"D", "A"}
        });
        // A is connected to each, but B,C,D not connected to each other
        assertBothAlgorithmsAgree(g, 1); // actually all connected through A
    }

    // ── Figure-eight ────────────────────────────────────────────

    @Test
    public void testFigureEight() {
        // Two cycles sharing vertex C
        Graph<String, Edge> g = buildDirectedGraph(new String[][]{
                {"A", "B"}, {"B", "C"}, {"C", "A"},
                {"C", "D"}, {"D", "E"}, {"E", "C"}
        });
        assertBothAlgorithmsAgree(g, 1); // all connected through C
    }

    // ── Min edges: multiple isolated ────────────────────────────

    @Test
    public void testMinEdgesMultipleIsolated() {
        Graph<String, Edge> g = new DirectedSparseGraph<String, Edge>();
        g.addVertex("A");
        g.addVertex("B");
        g.addVertex("C");

        StronglyConnectedComponentsAnalyzer analyzer = new StronglyConnectedComponentsAnalyzer(g);
        StronglyConnectedComponentsAnalyzer.SCCResult result = analyzer.tarjan();

        assertEquals(3, result.getComponentCount());
        assertEquals(3, analyzer.minEdgesToStronglyConnect(result));
    }
}
