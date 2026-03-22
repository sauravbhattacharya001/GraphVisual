package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for SteinerTreeAnalyzer.
 */
public class SteinerTreeAnalyzerTest {

    private int edgeId = 0;

    private Edge addEdge(Graph<String, Edge> g, String u, String v, float w) {
        Edge e = new Edge("e", u, v);
        e.setWeight(w);
        e.setLabel("e" + (edgeId++));
        g.addEdge(e, u, v);
        return e;
    }

    private Edge addEdge(Graph<String, Edge> g, String u, String v) {
        return addEdge(g, u, v, 1.0f);
    }

    // ── Simple graphs ─────────────────────────────────────────────

    private Graph<String, Edge> makePath(String... vertices) {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (String v : vertices) g.addVertex(v);
        for (int i = 0; i < vertices.length - 1; i++) addEdge(g, vertices[i], vertices[i + 1]);
        return g;
    }

    private Graph<String, Edge> makeStar(String center, String... leaves) {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex(center);
        for (String l : leaves) { g.addVertex(l); addEdge(g, center, l); }
        return g;
    }

    // ── Constructor ───────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraph() {
        new SteinerTreeAnalyzer(null);
    }

    // ── Validate terminals ────────────────────────────────────────

    @Test
    public void testValidateTerminals() {
        Graph<String, Edge> g = makePath("A", "B", "C");
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        Set<String> valid = sa.validateTerminals(new HashSet<>(Arrays.asList("A", "C", "X")));
        assertEquals(new HashSet<>(Arrays.asList("A", "C")), valid);
    }

    @Test
    public void testValidateTerminalsAllInvalid() {
        Graph<String, Edge> g = makePath("A", "B");
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        assertTrue(sa.validateTerminals(new HashSet<>(Arrays.asList("X", "Y"))).isEmpty());
    }

    // ── Terminal connectivity ─────────────────────────────────────

    @Test
    public void testTerminalsConnected() {
        Graph<String, Edge> g = makePath("A", "B", "C", "D");
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        assertTrue(sa.areTerminalsConnected(new HashSet<>(Arrays.asList("A", "D"))));
    }

    @Test
    public void testTerminalsDisconnected() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C"); g.addVertex("D");
        addEdge(g, "A", "B");
        addEdge(g, "C", "D");
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        assertFalse(sa.areTerminalsConnected(new HashSet<>(Arrays.asList("A", "C"))));
    }

    @Test
    public void testTerminalComponents() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C"); g.addVertex("D");
        addEdge(g, "A", "B");
        addEdge(g, "C", "D");
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        List<Set<String>> comps = sa.terminalComponents(new HashSet<>(Arrays.asList("A", "B", "C", "D")));
        assertEquals(2, comps.size());
    }

    @Test
    public void testSingleTerminalAlwaysConnected() {
        Graph<String, Edge> g = makePath("A", "B");
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        assertTrue(sa.areTerminalsConnected(Collections.singleton("A")));
    }

    // ── Shortest-path heuristic ───────────────────────────────────

    @Test
    public void testSPHeuristicSimplePath() {
        Graph<String, Edge> g = makePath("A", "B", "C");
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerTreeResult r = sa.shortestPathHeuristic(
                new HashSet<>(Arrays.asList("A", "C")));
        assertEquals(2.0, r.totalWeight, 0.001);
        assertTrue(r.steinerPoints.contains("B"));
        assertTrue(r.terminals.containsAll(Arrays.asList("A", "C")));
    }

    @Test
    public void testSPHeuristicStar() {
        Graph<String, Edge> g = makeStar("C", "A", "B", "D");
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerTreeResult r = sa.shortestPathHeuristic(
                new HashSet<>(Arrays.asList("A", "B", "D")));
        assertTrue(r.steinerPoints.contains("C"));
        assertEquals(3.0, r.totalWeight, 0.001);
    }

    @Test
    public void testSPHeuristicSingleTerminal() {
        Graph<String, Edge> g = makePath("A", "B");
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerTreeResult r = sa.shortestPathHeuristic(Collections.singleton("A"));
        assertEquals(0, r.totalWeight, 0.001);
        assertTrue(r.steinerPoints.isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSPHeuristicNullTerminals() {
        Graph<String, Edge> g = makePath("A", "B");
        new SteinerTreeAnalyzer(g).shortestPathHeuristic(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSPHeuristicEmptyTerminals() {
        Graph<String, Edge> g = makePath("A", "B");
        new SteinerTreeAnalyzer(g).shortestPathHeuristic(Collections.emptySet());
    }

    @Test
    public void testSPHeuristicWeightedGraph() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (String v : Arrays.asList("A", "B", "C", "D")) g.addVertex(v);
        addEdge(g, "A", "B", 1);
        addEdge(g, "B", "C", 1);
        addEdge(g, "A", "C", 10);
        addEdge(g, "B", "D", 1);
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerTreeResult r = sa.shortestPathHeuristic(
                new HashSet<>(Arrays.asList("A", "C", "D")));
        assertTrue(r.totalWeight < 10); // Should go through B
    }

    // ── MST heuristic ─────────────────────────────────────────────

    @Test
    public void testMSTHeuristicSimplePath() {
        Graph<String, Edge> g = makePath("A", "B", "C");
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerTreeResult r = sa.mstHeuristic(
                new HashSet<>(Arrays.asList("A", "C")));
        assertEquals(2.0, r.totalWeight, 0.001);
    }

    @Test
    public void testMSTHeuristicStar() {
        Graph<String, Edge> g = makeStar("C", "A", "B", "D");
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerTreeResult r = sa.mstHeuristic(
                new HashSet<>(Arrays.asList("A", "B", "D")));
        assertEquals(3.0, r.totalWeight, 0.001);
    }

    @Test
    public void testMSTHeuristicSingleTerminal() {
        Graph<String, Edge> g = makePath("A", "B");
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerTreeResult r = sa.mstHeuristic(Collections.singleton("A"));
        assertEquals(0, r.totalWeight, 0.001);
    }

    // ── Exact (Dreyfus-Wagner) ────────────────────────────────────

    @Test
    public void testExactSimplePath() {
        Graph<String, Edge> g = makePath("A", "B", "C");
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerTreeResult r = sa.exact(
                new HashSet<>(Arrays.asList("A", "C")));
        assertTrue(r.exact);
        assertEquals("dreyfus-wagner", r.algorithm);
        assertEquals(2.0, r.totalWeight, 0.001);
    }

    @Test
    public void testExactStar() {
        Graph<String, Edge> g = makeStar("C", "A", "B", "D");
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerTreeResult r = sa.exact(
                new HashSet<>(Arrays.asList("A", "B", "D")));
        assertTrue(r.exact);
        assertEquals(3.0, r.totalWeight, 0.001);
    }

    @Test
    public void testExactTwoTerminals() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (String v : Arrays.asList("A", "B", "C")) g.addVertex(v);
        addEdge(g, "A", "B", 5);
        addEdge(g, "A", "C", 2);
        addEdge(g, "C", "B", 2);
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerTreeResult r = sa.exact(
                new HashSet<>(Arrays.asList("A", "B")));
        assertEquals(4.0, r.totalWeight, 0.001); // A-C-B cheaper than A-B
    }

    @Test
    public void testExactSingleTerminal() {
        Graph<String, Edge> g = makePath("A", "B");
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerTreeResult r = sa.exact(Collections.singleton("A"));
        assertEquals(0, r.totalWeight, 0.001);
        assertTrue(r.exact);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExactTooManyTerminals() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        Set<String> terms = new HashSet<>();
        for (int i = 0; i < 16; i++) {
            String v = "V" + i;
            g.addVertex(v);
            terms.add(v);
        }
        new SteinerTreeAnalyzer(g).exact(terms);
    }

    @Test
    public void testExactWeightedDiamond() {
        // Diamond: A-B(1), A-C(1), B-D(1), C-D(1), A-D(10)
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (String v : Arrays.asList("A", "B", "C", "D")) g.addVertex(v);
        addEdge(g, "A", "B", 1);
        addEdge(g, "A", "C", 1);
        addEdge(g, "B", "D", 1);
        addEdge(g, "C", "D", 1);
        addEdge(g, "A", "D", 10);
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerTreeResult r = sa.exact(
                new HashSet<>(Arrays.asList("A", "D")));
        assertEquals(2.0, r.totalWeight, 0.001); // A-B-D or A-C-D
    }

    // ── Best heuristic ────────────────────────────────────────────

    @Test
    public void testBestHeuristic() {
        Graph<String, Edge> g = makeStar("C", "A", "B", "D");
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerTreeResult r = sa.bestHeuristic(
                new HashSet<>(Arrays.asList("A", "B", "D")));
        assertEquals(3.0, r.totalWeight, 0.001);
    }

    // ── Solve (auto-select) ───────────────────────────────────────

    @Test
    public void testSolveSmallUsesExact() {
        Graph<String, Edge> g = makePath("A", "B", "C");
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerTreeResult r = sa.solve(
                new HashSet<>(Arrays.asList("A", "C")));
        assertTrue(r.exact);
    }

    // ── Bottleneck edge ───────────────────────────────────────────

    @Test
    public void testBottleneckEdge() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (String v : Arrays.asList("A", "B", "C")) g.addVertex(v);
        addEdge(g, "A", "B", 1);
        addEdge(g, "B", "C", 5);
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerTreeResult r = sa.shortestPathHeuristic(
                new HashSet<>(Arrays.asList("A", "C")));
        SteinerTreeAnalyzer.EdgeInfo bn = sa.bottleneckEdge(r);
        assertNotNull(bn);
        assertEquals(5.0, bn.weight, 0.001);
    }

    @Test
    public void testBottleneckEdgeEmpty() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerTreeResult r = sa.shortestPathHeuristic(Collections.singleton("A"));
        assertNull(sa.bottleneckEdge(r));
    }

    // ── Steiner ratio ─────────────────────────────────────────────

    @Test
    public void testSteinerRatioNoSteinerPoints() {
        // When all terminals are adjacent, ratio should be ~1
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (String v : Arrays.asList("A", "B", "C")) g.addVertex(v);
        addEdge(g, "A", "B", 1);
        addEdge(g, "B", "C", 1);
        addEdge(g, "A", "C", 1);
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerTreeResult r = sa.shortestPathHeuristic(
                new HashSet<>(Arrays.asList("A", "B", "C")));
        double ratio = sa.steinerRatio(r);
        assertTrue(ratio > 0 && ratio <= 1.01);
    }

    @Test
    public void testSteinerRatioWithSteinerPoints() {
        // Star topology: terminals at leaves, center is Steiner point
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (String v : Arrays.asList("A", "B", "C", "D", "center")) g.addVertex(v);
        addEdge(g, "center", "A", 1);
        addEdge(g, "center", "B", 1);
        addEdge(g, "center", "C", 1);
        addEdge(g, "center", "D", 1);
        addEdge(g, "A", "B", 10);
        addEdge(g, "B", "C", 10);
        addEdge(g, "C", "D", 10);
        addEdge(g, "A", "D", 10);
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerTreeResult r = sa.solve(
                new HashSet<>(Arrays.asList("A", "B", "C", "D")));
        double ratio = sa.steinerRatio(r);
        assertTrue(ratio < 1.0); // Using center saves cost
    }

    // ── Steiner point importance ──────────────────────────────────

    @Test
    public void testSteinerPointImportance() {
        Graph<String, Edge> g = makeStar("C", "A", "B", "D");
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerTreeResult r = sa.shortestPathHeuristic(
                new HashSet<>(Arrays.asList("A", "B", "D")));
        Map<String, Double> imp = sa.steinerPointImportance(r);
        assertTrue(imp.containsKey("C"));
        assertTrue(imp.get("C") > 0);
    }

    @Test
    public void testSteinerPointImportanceNoSteinerPoints() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B");
        addEdge(g, "A", "B");
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerTreeResult r = sa.shortestPathHeuristic(
                new HashSet<>(Arrays.asList("A", "B")));
        assertTrue(sa.steinerPointImportance(r).isEmpty());
    }

    // ── Terminal MST cost ─────────────────────────────────────────

    @Test
    public void testTerminalMSTCost() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (String v : Arrays.asList("A", "B", "C")) g.addVertex(v);
        addEdge(g, "A", "B", 3);
        addEdge(g, "B", "C", 4);
        addEdge(g, "A", "C", 10);
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        assertEquals(7.0, sa.terminalMSTCost(new HashSet<>(Arrays.asList("A", "B", "C"))), 0.001);
    }

    @Test
    public void testTerminalMSTCostSingle() {
        Graph<String, Edge> g = makePath("A", "B");
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        assertEquals(0, sa.terminalMSTCost(Collections.singleton("A")), 0.001);
    }

    // ── Analyze / Report ──────────────────────────────────────────

    @Test
    public void testAnalyze() {
        Graph<String, Edge> g = makeStar("C", "A", "B", "D");
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerReport report = sa.analyze(
                new HashSet<>(Arrays.asList("A", "B", "D")));
        assertNotNull(report.tree);
        assertTrue(report.mstCost >= report.tree.totalWeight - 0.001);
    }

    @Test
    public void testTextReport() {
        Graph<String, Edge> g = makeStar("C", "A", "B", "D");
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        String report = sa.textReport(new HashSet<>(Arrays.asList("A", "B", "D")));
        assertTrue(report.contains("STEINER TREE ANALYSIS"));
        assertTrue(report.contains("Total weight"));
        assertTrue(report.contains("Steiner ratio"));
    }

    // ── EdgeInfo ──────────────────────────────────────────────────

    @Test
    public void testEdgeInfoEquality() {
        SteinerTreeAnalyzer.EdgeInfo e1 = new SteinerTreeAnalyzer.EdgeInfo("A", "B", 1.0);
        SteinerTreeAnalyzer.EdgeInfo e2 = new SteinerTreeAnalyzer.EdgeInfo("B", "A", 1.0);
        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
    }

    @Test
    public void testEdgeInfoInequality() {
        SteinerTreeAnalyzer.EdgeInfo e1 = new SteinerTreeAnalyzer.EdgeInfo("A", "B", 1.0);
        SteinerTreeAnalyzer.EdgeInfo e2 = new SteinerTreeAnalyzer.EdgeInfo("A", "B", 2.0);
        assertNotEquals(e1, e2);
    }

    @Test
    public void testEdgeInfoToString() {
        SteinerTreeAnalyzer.EdgeInfo e = new SteinerTreeAnalyzer.EdgeInfo("A", "B", 3.5);
        assertEquals("A -(3.5)- B", e.toString());
    }

    // ── SteinerTreeResult ─────────────────────────────────────────

    @Test
    public void testResultAllVertices() {
        SteinerTreeAnalyzer.SteinerTreeResult r = new SteinerTreeAnalyzer.SteinerTreeResult(
                new HashSet<>(Arrays.asList("A", "B")),
                new HashSet<>(Collections.singletonList("C")),
                Collections.emptySet(), 0, false, "test");
        assertEquals(new HashSet<>(Arrays.asList("A", "B", "C")), r.allVertices());
        assertEquals(3, r.totalVertices());
        assertEquals(0, r.totalEdges());
    }

    // ── Larger graph ──────────────────────────────────────────────

    @Test
    public void testGridGraph() {
        // 3x3 grid with unit weights
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++)
                g.addVertex(r + "," + c);
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++) {
                if (c + 1 < 3) addEdge(g, r + "," + c, r + "," + (c + 1));
                if (r + 1 < 3) addEdge(g, r + "," + c, (r + 1) + "," + c);
            }
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        // Terminals at corners
        Set<String> terms = new HashSet<>(Arrays.asList("0,0", "0,2", "2,0", "2,2"));
        SteinerTreeAnalyzer.SteinerTreeResult r = sa.solve(terms);
        assertNotNull(r);
        assertTrue(r.totalWeight > 0);
        assertTrue(r.allVertices().containsAll(terms));
    }

    @Test
    public void testWeightedGraphPrefersCheaperPaths() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (String v : Arrays.asList("A", "B", "C", "D", "E")) g.addVertex(v);
        addEdge(g, "A", "B", 10);
        addEdge(g, "A", "D", 1);
        addEdge(g, "D", "E", 1);
        addEdge(g, "E", "B", 1);
        addEdge(g, "B", "C", 1);
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerTreeResult r = sa.solve(
                new HashSet<>(Arrays.asList("A", "B", "C")));
        assertTrue(r.totalWeight <= 5); // A-D-E-B-C = 4, not A-B(10)+B-C(1) = 11
    }

    @Test
    public void testTwoAdjacentTerminals() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B");
        addEdge(g, "A", "B", 3);
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerTreeResult r = sa.solve(
                new HashSet<>(Arrays.asList("A", "B")));
        assertEquals(3.0, r.totalWeight, 0.001);
        assertTrue(r.steinerPoints.isEmpty());
    }

    @Test
    public void testAllVerticesAreTerminals() {
        Graph<String, Edge> g = makePath("A", "B", "C");
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerTreeResult r = sa.solve(
                new HashSet<>(Arrays.asList("A", "B", "C")));
        assertTrue(r.steinerPoints.isEmpty());
        assertEquals(2.0, r.totalWeight, 0.001);
    }

    @Test
    public void testSavingsAnalysis() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (String v : Arrays.asList("A", "B", "C", "S")) g.addVertex(v);
        addEdge(g, "S", "A", 1);
        addEdge(g, "S", "B", 1);
        addEdge(g, "S", "C", 1);
        addEdge(g, "A", "B", 10);
        addEdge(g, "B", "C", 10);
        addEdge(g, "A", "C", 10);
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerReport report = sa.analyze(
                new HashSet<>(Arrays.asList("A", "B", "C")));
        assertTrue(report.savings > 0);
        assertTrue(report.savingsPercent > 0);
    }

    @Test
    public void testDisconnectedTerminals() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B");
        // No edges
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        assertFalse(sa.areTerminalsConnected(new HashSet<>(Arrays.asList("A", "B"))));
    }

    @Test
    public void testCompleteGraph() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (String v : Arrays.asList("A", "B", "C", "D")) g.addVertex(v);
        addEdge(g, "A", "B", 1);
        addEdge(g, "A", "C", 1);
        addEdge(g, "A", "D", 1);
        addEdge(g, "B", "C", 1);
        addEdge(g, "B", "D", 1);
        addEdge(g, "C", "D", 1);
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        SteinerTreeAnalyzer.SteinerTreeResult r = sa.solve(
                new HashSet<>(Arrays.asList("A", "B", "C", "D")));
        assertEquals(3.0, r.totalWeight, 0.001); // MST of K4 = 3 edges
    }

    @Test
    public void testHeuristicsVsExact() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (String v : Arrays.asList("A", "B", "C", "D", "E")) g.addVertex(v);
        addEdge(g, "A", "D", 1);
        addEdge(g, "D", "B", 1);
        addEdge(g, "D", "E", 1);
        addEdge(g, "E", "C", 1);
        addEdge(g, "A", "B", 5);
        addEdge(g, "B", "C", 5);
        SteinerTreeAnalyzer sa = new SteinerTreeAnalyzer(g);
        Set<String> terms = new HashSet<>(Arrays.asList("A", "B", "C"));
        SteinerTreeAnalyzer.SteinerTreeResult exact = sa.exact(terms);
        SteinerTreeAnalyzer.SteinerTreeResult sp = sa.shortestPathHeuristic(terms);
        SteinerTreeAnalyzer.SteinerTreeResult mst = sa.mstHeuristic(terms);
        // Heuristics should be at most 2x optimal
        assertTrue(sp.totalWeight <= 2 * exact.totalWeight + 0.001);
        assertTrue(mst.totalWeight <= 2 * exact.totalWeight + 0.001);
    }
}
