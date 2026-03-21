package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for ChordalGraphAnalyzer.
 */
public class ChordalGraphAnalyzerTest {

    private Graph<String, Edge> emptyGraph;
    private Graph<String, Edge> singleVertex;
    private Graph<String, Edge> singleEdge;
    private Graph<String, Edge> triangle;   // K3 — chordal
    private Graph<String, Edge> path4;      // A-B-C-D — chordal
    private Graph<String, Edge> cycle4;     // A-B-C-D-A — NOT chordal
    private Graph<String, Edge> cycle5;     // 5-cycle — NOT chordal
    private Graph<String, Edge> complete4;  // K4 — chordal
    private Graph<String, Edge> diamond;    // K4 minus one edge — chordal
    private Graph<String, Edge> star5;      // star — chordal
    private Graph<String, Edge> fan;        // fan graph — chordal

    private int edgeCounter;

    @Before
    public void setUp() {
        edgeCounter = 0;

        emptyGraph = new UndirectedSparseGraph<>();

        singleVertex = new UndirectedSparseGraph<>();
        singleVertex.addVertex("A");

        singleEdge = new UndirectedSparseGraph<>();
        addEdge(singleEdge, "A", "B");

        triangle = new UndirectedSparseGraph<>();
        addEdge(triangle, "A", "B");
        addEdge(triangle, "B", "C");
        addEdge(triangle, "A", "C");

        path4 = new UndirectedSparseGraph<>();
        addEdge(path4, "A", "B");
        addEdge(path4, "B", "C");
        addEdge(path4, "C", "D");

        cycle4 = new UndirectedSparseGraph<>();
        addEdge(cycle4, "A", "B");
        addEdge(cycle4, "B", "C");
        addEdge(cycle4, "C", "D");
        addEdge(cycle4, "D", "A");

        cycle5 = new UndirectedSparseGraph<>();
        addEdge(cycle5, "A", "B");
        addEdge(cycle5, "B", "C");
        addEdge(cycle5, "C", "D");
        addEdge(cycle5, "D", "E");
        addEdge(cycle5, "E", "A");

        complete4 = new UndirectedSparseGraph<>();
        addEdge(complete4, "A", "B");
        addEdge(complete4, "A", "C");
        addEdge(complete4, "A", "D");
        addEdge(complete4, "B", "C");
        addEdge(complete4, "B", "D");
        addEdge(complete4, "C", "D");

        // Diamond: K4 minus B-D edge → A-B, A-C, A-D, B-C, C-D (chordal)
        diamond = new UndirectedSparseGraph<>();
        addEdge(diamond, "A", "B");
        addEdge(diamond, "A", "C");
        addEdge(diamond, "A", "D");
        addEdge(diamond, "B", "C");
        addEdge(diamond, "C", "D");

        // Star: center X, leaves A,B,C,D
        star5 = new UndirectedSparseGraph<>();
        addEdge(star5, "X", "A");
        addEdge(star5, "X", "B");
        addEdge(star5, "X", "C");
        addEdge(star5, "X", "D");

        // Fan: path A-B-C-D plus center X connected to all
        fan = new UndirectedSparseGraph<>();
        addEdge(fan, "A", "B");
        addEdge(fan, "B", "C");
        addEdge(fan, "C", "D");
        addEdge(fan, "X", "A");
        addEdge(fan, "X", "B");
        addEdge(fan, "X", "C");
        addEdge(fan, "X", "D");
    }

    private void addEdge(Graph<String, Edge> g, String u, String v) {
        if (!g.containsVertex(u)) g.addVertex(u);
        if (!g.containsVertex(v)) g.addVertex(v);
        g.addEdge(new Edge("e" + (++edgeCounter), u, v), u, v);
    }

    // ── MCS ordering ────────────────────────────────────────────────────

    @Test
    public void testMCSEmptyGraph() {
        List<String> order = ChordalGraphAnalyzer.maximumCardinalitySearch(emptyGraph);
        assertTrue(order.isEmpty());
    }

    @Test
    public void testMCSNullGraph() {
        List<String> order = ChordalGraphAnalyzer.maximumCardinalitySearch(null);
        assertTrue(order.isEmpty());
    }

    @Test
    public void testMCSSingleVertex() {
        List<String> order = ChordalGraphAnalyzer.maximumCardinalitySearch(singleVertex);
        assertEquals(1, order.size());
        assertEquals("A", order.get(0));
    }

    @Test
    public void testMCSReturnsAllVertices() {
        List<String> order = ChordalGraphAnalyzer.maximumCardinalitySearch(complete4);
        assertEquals(4, order.size());
        assertEquals(new HashSet<>(Arrays.asList("A", "B", "C", "D")), new HashSet<>(order));
    }

    // ── Chordality tests ────────────────────────────────────────────────

    @Test
    public void testEmptyGraphIsChordal() {
        assertTrue(ChordalGraphAnalyzer.testChordality(emptyGraph).isChordal());
    }

    @Test
    public void testNullGraphIsChordal() {
        assertTrue(ChordalGraphAnalyzer.testChordality(null).isChordal());
    }

    @Test
    public void testSingleVertexIsChordal() {
        assertTrue(ChordalGraphAnalyzer.testChordality(singleVertex).isChordal());
    }

    @Test
    public void testSingleEdgeIsChordal() {
        assertTrue(ChordalGraphAnalyzer.testChordality(singleEdge).isChordal());
    }

    @Test
    public void testTriangleIsChordal() {
        ChordalGraphAnalyzer.ChordalityResult r = ChordalGraphAnalyzer.testChordality(triangle);
        assertTrue(r.isChordal());
        assertNull(r.getChordlessCycle());
        assertEquals(3, r.getPeo().size());
    }

    @Test
    public void testPathIsChordal() {
        assertTrue(ChordalGraphAnalyzer.testChordality(path4).isChordal());
    }

    @Test
    public void testComplete4IsChordal() {
        assertTrue(ChordalGraphAnalyzer.testChordality(complete4).isChordal());
    }

    @Test
    public void testDiamondIsChordal() {
        assertTrue(ChordalGraphAnalyzer.testChordality(diamond).isChordal());
    }

    @Test
    public void testStarIsChordal() {
        assertTrue(ChordalGraphAnalyzer.testChordality(star5).isChordal());
    }

    @Test
    public void testFanIsChordal() {
        assertTrue(ChordalGraphAnalyzer.testChordality(fan).isChordal());
    }

    @Test
    public void testCycle4IsNotChordal() {
        ChordalGraphAnalyzer.ChordalityResult r = ChordalGraphAnalyzer.testChordality(cycle4);
        assertFalse(r.isChordal());
        assertNotNull(r.getChordlessCycle());
        assertTrue(r.getChordlessCycle().size() >= 3);
    }

    @Test
    public void testCycle5IsNotChordal() {
        ChordalGraphAnalyzer.ChordalityResult r = ChordalGraphAnalyzer.testChordality(cycle5);
        assertFalse(r.isChordal());
    }

    // ── Coloring ────────────────────────────────────────────────────────

    @Test
    public void testColoringEmpty() {
        ChordalGraphAnalyzer.ColoringResult c = ChordalGraphAnalyzer.optimalColoring(emptyGraph);
        assertEquals(0, c.getChromaticNumber());
    }

    @Test
    public void testColoringSingleVertex() {
        ChordalGraphAnalyzer.ColoringResult c = ChordalGraphAnalyzer.optimalColoring(singleVertex);
        assertEquals(1, c.getChromaticNumber());
    }

    @Test
    public void testColoringTriangle() {
        ChordalGraphAnalyzer.ColoringResult c = ChordalGraphAnalyzer.optimalColoring(triangle);
        assertEquals(3, c.getChromaticNumber());
        // Verify valid coloring
        Map<String, Integer> colors = c.getColors();
        assertNotEquals(colors.get("A"), colors.get("B"));
        assertNotEquals(colors.get("B"), colors.get("C"));
        assertNotEquals(colors.get("A"), colors.get("C"));
    }

    @Test
    public void testColoringPath4() {
        ChordalGraphAnalyzer.ColoringResult c = ChordalGraphAnalyzer.optimalColoring(path4);
        assertEquals(2, c.getChromaticNumber());
    }

    @Test
    public void testColoringComplete4() {
        ChordalGraphAnalyzer.ColoringResult c = ChordalGraphAnalyzer.optimalColoring(complete4);
        assertEquals(4, c.getChromaticNumber());
    }

    @Test
    public void testColoringStar() {
        ChordalGraphAnalyzer.ColoringResult c = ChordalGraphAnalyzer.optimalColoring(star5);
        assertEquals(2, c.getChromaticNumber());
    }

    @Test
    public void testColoringIsValid() {
        ChordalGraphAnalyzer.ColoringResult c = ChordalGraphAnalyzer.optimalColoring(fan);
        Map<String, Integer> colors = c.getColors();
        for (Edge e : fan.getEdges()) {
            assertNotEquals("Adjacent vertices must have different colors: " + e.getVertex1() + "-" + e.getVertex2(),
                colors.get(e.getVertex1()), colors.get(e.getVertex2()));
        }
    }

    // ── Maximum clique ──────────────────────────────────────────────────

    @Test
    public void testMaxCliqueEmpty() {
        assertTrue(ChordalGraphAnalyzer.maximumClique(emptyGraph).isEmpty());
    }

    @Test
    public void testMaxCliqueSingle() {
        assertEquals(1, ChordalGraphAnalyzer.maximumClique(singleVertex).size());
    }

    @Test
    public void testMaxCliqueTriangle() {
        assertEquals(3, ChordalGraphAnalyzer.maximumClique(triangle).size());
    }

    @Test
    public void testMaxCliqueComplete4() {
        assertEquals(4, ChordalGraphAnalyzer.maximumClique(complete4).size());
    }

    @Test
    public void testMaxCliquePath() {
        assertEquals(2, ChordalGraphAnalyzer.maximumClique(path4).size());
    }

    @Test
    public void testMaxCliqueStar() {
        assertEquals(2, ChordalGraphAnalyzer.maximumClique(star5).size());
    }

    @Test
    public void testMaxCliqueDiamond() {
        Set<String> mc = ChordalGraphAnalyzer.maximumClique(diamond);
        assertEquals(3, mc.size());
    }

    // ── Maximal cliques enumeration ─────────────────────────────────────

    @Test
    public void testAllMaximalCliquesEmpty() {
        assertTrue(ChordalGraphAnalyzer.allMaximalCliques(emptyGraph).isEmpty());
    }

    @Test
    public void testAllMaximalCliquesSingle() {
        List<Set<String>> cliques = ChordalGraphAnalyzer.allMaximalCliques(singleVertex);
        assertEquals(1, cliques.size());
        assertTrue(cliques.get(0).contains("A"));
    }

    @Test
    public void testAllMaximalCliquesTriangle() {
        List<Set<String>> cliques = ChordalGraphAnalyzer.allMaximalCliques(triangle);
        assertEquals(1, cliques.size());
        assertEquals(3, cliques.get(0).size());
    }

    @Test
    public void testAllMaximalCliquesPath() {
        List<Set<String>> cliques = ChordalGraphAnalyzer.allMaximalCliques(path4);
        assertEquals(3, cliques.size()); // {A,B}, {B,C}, {C,D}
        for (Set<String> c : cliques) assertEquals(2, c.size());
    }

    @Test
    public void testAllMaximalCliquesComplete4() {
        List<Set<String>> cliques = ChordalGraphAnalyzer.allMaximalCliques(complete4);
        assertEquals(1, cliques.size());
        assertEquals(4, cliques.get(0).size());
    }

    @Test
    public void testMaximalCliquesAreMaximal() {
        List<Set<String>> cliques = ChordalGraphAnalyzer.allMaximalCliques(diamond);
        for (Set<String> c : cliques) {
            assertTrue("Clique must have >= 2 vertices in non-trivial graph", c.size() >= 2);
        }
    }

    // ── Clique tree ─────────────────────────────────────────────────────

    @Test
    public void testCliqueTreeEmpty() {
        assertTrue(ChordalGraphAnalyzer.buildCliqueTree(emptyGraph).isEmpty());
    }

    @Test
    public void testCliqueTreeSingle() {
        List<ChordalGraphAnalyzer.CliqueTreeNode> tree = ChordalGraphAnalyzer.buildCliqueTree(singleVertex);
        assertEquals(1, tree.size());
    }

    @Test
    public void testCliqueTreePath() {
        List<ChordalGraphAnalyzer.CliqueTreeNode> tree = ChordalGraphAnalyzer.buildCliqueTree(path4);
        assertEquals(3, tree.size());
        // Tree has n-1 edges for n nodes
        int totalEdges = 0;
        for (ChordalGraphAnalyzer.CliqueTreeNode node : tree) {
            totalEdges += node.getNeighbors().size();
        }
        assertEquals(4, totalEdges); // 2 edges × 2 directions
    }

    @Test
    public void testCliqueTreeComplete4() {
        List<ChordalGraphAnalyzer.CliqueTreeNode> tree = ChordalGraphAnalyzer.buildCliqueTree(complete4);
        assertEquals(1, tree.size());
    }

    @Test
    public void testCliqueTreeRunningIntersection() {
        // Verify running intersection property: for each vertex,
        // the clique tree nodes containing it form a connected subtree
        List<ChordalGraphAnalyzer.CliqueTreeNode> tree = ChordalGraphAnalyzer.buildCliqueTree(fan);
        Map<String, Set<Integer>> vertexNodes = new HashMap<>();
        for (ChordalGraphAnalyzer.CliqueTreeNode node : tree) {
            for (String v : node.getVertices()) {
                if (!vertexNodes.containsKey(v)) vertexNodes.put(v, new HashSet<Integer>());
                vertexNodes.get(v).add(node.getId());
            }
        }
        // Each set of nodes containing a vertex should form a connected subtree
        for (Map.Entry<String, Set<Integer>> entry : vertexNodes.entrySet()) {
            Set<Integer> nodes = entry.getValue();
            if (nodes.size() > 1) {
                // BFS within the subtree
                Iterator<Integer> it = nodes.iterator();
                int start = it.next();
                Set<Integer> reached = new HashSet<>();
                Queue<Integer> q = new LinkedList<>();
                q.add(start);
                reached.add(start);
                while (!q.isEmpty()) {
                    int cur = q.poll();
                    for (int nb : tree.get(cur).getNeighbors()) {
                        if (nodes.contains(nb) && !reached.contains(nb)) {
                            reached.add(nb);
                            q.add(nb);
                        }
                    }
                }
                assertEquals("Vertex " + entry.getKey() + " subtree should be connected",
                    nodes.size(), reached.size());
            }
        }
    }

    // ── Fill-in edges ───────────────────────────────────────────────────

    @Test
    public void testFillInChordalGraphHasNone() {
        ChordalGraphAnalyzer.FillInResult r = ChordalGraphAnalyzer.computeFillIn(triangle);
        assertEquals(0, r.getFillCount());
    }

    @Test
    public void testFillInComplete4HasNone() {
        assertEquals(0, ChordalGraphAnalyzer.computeFillIn(complete4).getFillCount());
    }

    @Test
    public void testFillInCycle4() {
        ChordalGraphAnalyzer.FillInResult r = ChordalGraphAnalyzer.computeFillIn(cycle4);
        assertTrue(r.getFillCount() >= 1);
    }

    @Test
    public void testFillInCycle5() {
        ChordalGraphAnalyzer.FillInResult r = ChordalGraphAnalyzer.computeFillIn(cycle5);
        assertTrue(r.getFillCount() >= 2);
    }

    @Test
    public void testFillInEdgesAreValid() {
        ChordalGraphAnalyzer.FillInResult r = ChordalGraphAnalyzer.computeFillIn(cycle4);
        Set<String> vertices = new HashSet<>(Arrays.asList("A", "B", "C", "D"));
        for (String[] fe : r.getFillEdges()) {
            assertEquals(2, fe.length);
            assertTrue(vertices.contains(fe[0]));
            assertTrue(vertices.contains(fe[1]));
            assertTrue("Fill edge endpoints must be ordered", fe[0].compareTo(fe[1]) < 0);
        }
    }

    // ── Minimal separators ──────────────────────────────────────────────

    @Test
    public void testSeparatorsTriangle() {
        List<Set<String>> seps = ChordalGraphAnalyzer.minimalSeparators(triangle);
        assertTrue(seps.isEmpty()); // only 1 maximal clique
    }

    @Test
    public void testSeparatorsPath() {
        List<Set<String>> seps = ChordalGraphAnalyzer.minimalSeparators(path4);
        // Path A-B-C-D has cliques {A,B},{B,C},{C,D} → separators {B},{C}
        assertEquals(2, seps.size());
    }

    // ── Treewidth ───────────────────────────────────────────────────────

    @Test
    public void testTreewidthEmpty() {
        assertEquals(-1, ChordalGraphAnalyzer.treewidth(emptyGraph));
    }

    @Test
    public void testTreewidthSingle() {
        assertEquals(0, ChordalGraphAnalyzer.treewidth(singleVertex));
    }

    @Test
    public void testTreewidthPath() {
        assertEquals(1, ChordalGraphAnalyzer.treewidth(path4));
    }

    @Test
    public void testTreewidthTriangle() {
        assertEquals(2, ChordalGraphAnalyzer.treewidth(triangle));
    }

    @Test
    public void testTreewidthComplete4() {
        assertEquals(3, ChordalGraphAnalyzer.treewidth(complete4));
    }

    // ── Elimination cliques ─────────────────────────────────────────────

    @Test
    public void testEliminationCliquesPath() {
        List<String> order = ChordalGraphAnalyzer.maximumCardinalitySearch(path4);
        List<Set<String>> cliques = ChordalGraphAnalyzer.eliminationCliques(path4, order);
        assertEquals(4, cliques.size());
    }

    @Test
    public void testEliminationCliquesNull() {
        assertTrue(ChordalGraphAnalyzer.eliminationCliques(null, null).isEmpty());
    }

    // ── Full analysis ───────────────────────────────────────────────────

    @Test
    public void testAnalyzeChordalGraph() {
        ChordalGraphAnalyzer.ChordalReport report = ChordalGraphAnalyzer.analyze(complete4);
        assertTrue(report.getChordality().isChordal());
        assertNotNull(report.getColoring());
        assertNotNull(report.getMaxClique());
        assertNotNull(report.getAllMaximalCliques());
        assertNotNull(report.getCliqueTree());
        assertNull(report.getFillIn());
        assertEquals(4, report.getVertexCount());
        assertEquals(6, report.getEdgeCount());
    }

    @Test
    public void testAnalyzeNonChordalGraph() {
        ChordalGraphAnalyzer.ChordalReport report = ChordalGraphAnalyzer.analyze(cycle4);
        assertFalse(report.getChordality().isChordal());
        assertNotNull(report.getFillIn());
        assertTrue(report.getFillIn().getFillCount() > 0);
    }

    @Test
    public void testAnalyzeTextReport() {
        ChordalGraphAnalyzer.ChordalReport report = ChordalGraphAnalyzer.analyze(diamond);
        String text = report.toTextReport();
        assertTrue(text.contains("Chordal: YES"));
        assertTrue(text.contains("Vertices: 4"));
        assertTrue(text.contains("Chromatic number:"));
        assertTrue(text.contains("Maximum clique size:"));
    }

    @Test
    public void testAnalyzeNonChordalTextReport() {
        ChordalGraphAnalyzer.ChordalReport report = ChordalGraphAnalyzer.analyze(cycle5);
        String text = report.toTextReport();
        assertTrue(text.contains("Chordal: NO"));
    }

    // ── Perfect graph property: χ = ω for chordal ───────────────────────

    @Test
    public void testPerfectGraphPropertyTriangle() {
        int chi = ChordalGraphAnalyzer.optimalColoring(triangle).getChromaticNumber();
        int omega = ChordalGraphAnalyzer.maximumClique(triangle).size();
        assertEquals(chi, omega);
    }

    @Test
    public void testPerfectGraphPropertyComplete4() {
        int chi = ChordalGraphAnalyzer.optimalColoring(complete4).getChromaticNumber();
        int omega = ChordalGraphAnalyzer.maximumClique(complete4).size();
        assertEquals(chi, omega);
    }

    @Test
    public void testPerfectGraphPropertyPath() {
        int chi = ChordalGraphAnalyzer.optimalColoring(path4).getChromaticNumber();
        int omega = ChordalGraphAnalyzer.maximumClique(path4).size();
        assertEquals(chi, omega);
    }

    @Test
    public void testPerfectGraphPropertyDiamond() {
        int chi = ChordalGraphAnalyzer.optimalColoring(diamond).getChromaticNumber();
        int omega = ChordalGraphAnalyzer.maximumClique(diamond).size();
        assertEquals(chi, omega);
    }

    @Test
    public void testPerfectGraphPropertyStar() {
        int chi = ChordalGraphAnalyzer.optimalColoring(star5).getChromaticNumber();
        int omega = ChordalGraphAnalyzer.maximumClique(star5).size();
        assertEquals(chi, omega);
    }

    @Test
    public void testPerfectGraphPropertyFan() {
        int chi = ChordalGraphAnalyzer.optimalColoring(fan).getChromaticNumber();
        int omega = ChordalGraphAnalyzer.maximumClique(fan).size();
        assertEquals(chi, omega);
    }
}
