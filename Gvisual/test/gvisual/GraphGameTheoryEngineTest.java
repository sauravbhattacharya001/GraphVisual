package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphGameTheoryEngine}.
 *
 * @author sauravbhattacharya001
 */
public class GraphGameTheoryEngineTest {

    // ── Helper methods ───────────────────────────────────────────────

    private Graph<String, Edge> triangle() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addEdge(new Edge("c", "A", "B"), "A", "B");
        g.addEdge(new Edge("c", "B", "C"), "B", "C");
        g.addEdge(new Edge("c", "A", "C"), "A", "C");
        return g;
    }

    private Graph<String, Edge> star5() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("Hub");
        for (int i = 1; i <= 4; i++) {
            String leaf = "L" + i;
            g.addVertex(leaf);
            g.addEdge(new Edge("c", "Hub", leaf), "Hub", leaf);
        }
        return g;
    }

    private Graph<String, Edge> path4() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C"); g.addVertex("D");
        g.addEdge(new Edge("c", "A", "B"), "A", "B");
        g.addEdge(new Edge("c", "B", "C"), "B", "C");
        g.addEdge(new Edge("c", "C", "D"), "C", "D");
        return g;
    }

    private Graph<String, Edge> completeK5() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        String[] nodes = {"A", "B", "C", "D", "E"};
        for (String n : nodes) g.addVertex(n);
        for (int i = 0; i < nodes.length; i++)
            for (int j = i + 1; j < nodes.length; j++)
                g.addEdge(new Edge("c", nodes[i], nodes[j]), nodes[i], nodes[j]);
        return g;
    }

    private Graph<String, Edge> singleNode() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("Solo");
        return g;
    }

    private Graph<String, Edge> disconnected() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C"); g.addVertex("D");
        g.addEdge(new Edge("c", "A", "B"), "A", "B");
        g.addEdge(new Edge("c", "C", "D"), "C", "D");
        return g;
    }

    private Graph<String, Edge> empty() {
        return new UndirectedSparseGraph<>();
    }

    // ── Empty graph tests ────────────────────────────────────────────

    @Test
    public void testEmptyGraph() {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine();
        GraphGameTheoryEngine.GameTheoryReport report = engine.analyze(empty());
        assertEquals(0, report.nodeCount);
        assertEquals(0, report.edgeCount);
        assertTrue(report.shapleyValues.isEmpty());
        assertTrue(report.insights.size() > 0);
    }

    // ── Single node tests ────────────────────────────────────────────

    @Test
    public void testSingleNode() {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine();
        GraphGameTheoryEngine.GameTheoryReport report = engine.analyze(singleNode());
        assertEquals(1, report.nodeCount);
        assertEquals(0, report.edgeCount);
        assertEquals(0.0, report.shapleyValues.get("Solo"), 0.001);
    }

    // ── Shapley value tests ──────────────────────────────────────────

    @Test
    public void testShapleyValuesTriangle() {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine().setRng(new Random(42));
        GraphGameTheoryEngine.GameTheoryReport report = engine.analyze(triangle());
        // In a triangle all nodes are symmetric, Shapley should be roughly equal
        double valA = report.shapleyValues.get("A");
        double valB = report.shapleyValues.get("B");
        double valC = report.shapleyValues.get("C");
        assertEquals(valA, valB, 0.5);
        assertEquals(valB, valC, 0.5);
    }

    @Test
    public void testShapleyValuesStarHubHighest() {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine().setRng(new Random(42));
        GraphGameTheoryEngine.GameTheoryReport report = engine.analyze(star5());
        double hubVal = report.shapleyValues.get("Hub");
        for (int i = 1; i <= 4; i++) {
            assertTrue("Hub should have highest Shapley value",
                    hubVal >= report.shapleyValues.get("L" + i));
        }
    }

    @Test
    public void testShapleyValuesComplete() {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine().setRng(new Random(42));
        GraphGameTheoryEngine.GameTheoryReport report = engine.analyze(completeK5());
        // All nodes symmetric in K5
        double first = report.shapleyValues.get("A");
        for (String n : new String[]{"B", "C", "D", "E"}) {
            assertEquals(first, report.shapleyValues.get(n), 1.0);
        }
    }

    @Test
    public void testShapleyNonNegative() {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine().setRng(new Random(42));
        GraphGameTheoryEngine.GameTheoryReport report = engine.analyze(path4());
        for (double v : report.shapleyValues.values()) {
            assertTrue("Shapley values should be non-negative", v >= 0);
        }
    }

    // ── Nash equilibrium tests ───────────────────────────────────────

    @Test
    public void testNashEquilibriumExists() {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine().setRng(new Random(42));
        GraphGameTheoryEngine.GameTheoryReport report = engine.analyze(triangle());
        // Small graph should have at least one equilibrium
        assertFalse("Triangle should have at least one Nash equilibrium",
                report.nashEquilibria.isEmpty());
    }

    @Test
    public void testNashEquilibriumStrategies() {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine().setRng(new Random(42));
        GraphGameTheoryEngine.GameTheoryReport report = engine.analyze(triangle());
        for (GraphGameTheoryEngine.NashEquilibrium eq : report.nashEquilibria) {
            for (int s : eq.strategies.values()) {
                assertTrue("Strategy must be 0 or 1", s == 0 || s == 1);
            }
            assertTrue("Convergence steps should be positive", eq.convergenceSteps > 0);
        }
    }

    @Test
    public void testNashEquilibriumComplete() {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine().setRng(new Random(42));
        GraphGameTheoryEngine.GameTheoryReport report = engine.analyze(completeK5());
        for (GraphGameTheoryEngine.NashEquilibrium eq : report.nashEquilibria) {
            assertEquals(5, eq.strategies.size());
        }
    }

    // ── Coalition tests ──────────────────────────────────────────────

    @Test
    public void testCoalitionStructureTriangle() {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine();
        GraphGameTheoryEngine.GameTheoryReport report = engine.analyze(triangle());
        assertFalse("Should have at least one coalition", report.coalitions.isEmpty());
    }

    @Test
    public void testCoalitionRanksOrdered() {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine();
        GraphGameTheoryEngine.GameTheoryReport report = engine.analyze(completeK5());
        for (int i = 0; i < report.coalitions.size(); i++) {
            assertEquals(i + 1, report.coalitions.get(i).rank);
        }
    }

    @Test
    public void testCoalitionValueNonNegative() {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine();
        GraphGameTheoryEngine.GameTheoryReport report = engine.analyze(star5());
        for (GraphGameTheoryEngine.Coalition c : report.coalitions) {
            assertTrue("Coalition value should be non-negative", c.value >= 0);
        }
    }

    @Test
    public void testCoalitionDisconnectedGraph() {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine();
        GraphGameTheoryEngine.GameTheoryReport report = engine.analyze(disconnected());
        // Should have at least 2 coalitions for disconnected graph
        assertTrue("Disconnected graph should have multiple coalitions",
                report.coalitions.size() >= 2);
    }

    @Test
    public void testCoalitionValueFunction() {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine();
        Graph<String, Edge> g = triangle();
        Set<String> full = new LinkedHashSet<>(Arrays.asList("A", "B", "C"));
        double val = engine.coalitionValue(g, full);
        // triangle: 3 edges, density=1.0, size=3, value = 1.0 * 9 = 9
        assertEquals(9.0, val, 0.001);
    }

    // ── Bargaining power tests ───────────────────────────────────────

    @Test
    public void testBargainingPowerRange() {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine().setRng(new Random(42));
        GraphGameTheoryEngine.GameTheoryReport report = engine.analyze(completeK5());
        for (double v : report.bargainingPower.values()) {
            assertTrue("Bargaining power should be 0-1", v >= 0 && v <= 1.0);
        }
    }

    @Test
    public void testBargainingPowerStarHub() {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine().setRng(new Random(42));
        GraphGameTheoryEngine.GameTheoryReport report = engine.analyze(star5());
        // Hub should have high bargaining power
        double hubPower = report.bargainingPower.get("Hub");
        assertTrue("Hub should have significant bargaining power", hubPower >= 0.0);
    }

    // ── Strategic position tests ─────────────────────────────────────

    @Test
    public void testStrategicPositionScoreRange() {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine().setRng(new Random(42));
        GraphGameTheoryEngine.GameTheoryReport report = engine.analyze(completeK5());
        for (double v : report.strategicPositionScores.values()) {
            assertTrue("Score should be 0-100", v >= 0 && v <= 100);
        }
    }

    @Test
    public void testStrategicPositionAllNodesPresent() {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine().setRng(new Random(42));
        GraphGameTheoryEngine.GameTheoryReport report = engine.analyze(path4());
        assertEquals(4, report.strategicPositionScores.size());
        assertTrue(report.strategicPositionScores.containsKey("A"));
        assertTrue(report.strategicPositionScores.containsKey("D"));
    }

    // ── Health score tests ───────────────────────────────────────────

    @Test
    public void testHealthScoreRange() {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine().setRng(new Random(42));
        GraphGameTheoryEngine.GameTheoryReport report = engine.analyze(completeK5());
        assertTrue("Health score should be 0-100",
                report.gameTheoryHealthScore >= 0 && report.gameTheoryHealthScore <= 100);
    }

    // ── Insights tests ───────────────────────────────────────────────

    @Test
    public void testInsightsGenerated() {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine().setRng(new Random(42));
        GraphGameTheoryEngine.GameTheoryReport report = engine.analyze(star5());
        assertFalse("Should generate insights", report.insights.isEmpty());
    }

    // ── Text report tests ────────────────────────────────────────────

    @Test
    public void testToTextNotEmpty() {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine().setRng(new Random(42));
        GraphGameTheoryEngine.GameTheoryReport report = engine.analyze(triangle());
        String text = engine.toText(report);
        assertTrue(text.contains("GRAPH GAME THEORY ENGINE"));
        assertTrue(text.contains("Shapley Values"));
        assertTrue(text.contains("Nash Equilibria"));
        assertTrue(text.contains("Coalition Structure"));
    }

    // ── HTML export tests ────────────────────────────────────────────

    @Test
    public void testExportHtmlNotEmpty() {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine().setRng(new Random(42));
        GraphGameTheoryEngine.GameTheoryReport report = engine.analyze(triangle());
        String html = engine.exportHtml(report);
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("Game Theory Engine"));
        assertTrue(html.contains("Shapley Values"));
    }

    @Test
    public void testExportToFile() throws IOException {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine().setRng(new Random(42));
        GraphGameTheoryEngine.GameTheoryReport report = engine.analyze(triangle());
        File tmp = File.createTempFile("game_theory_test", ".html");
        tmp.deleteOnExit();
        engine.exportToFile(report, tmp.getAbsolutePath());
        assertTrue("HTML file should be non-empty", tmp.length() > 100);
    }

    // ── Connected pairs utility test ─────────────────────────────────

    @Test
    public void testCoalitionConnectedPairs() {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine();
        Graph<String, Edge> g = triangle();
        Set<String> all = new LinkedHashSet<>(Arrays.asList("A", "B", "C"));
        long pairs = engine.coalitionConnectedPairs(g, all);
        assertEquals("Triangle has 3 connected pairs", 3, pairs);
    }

    @Test
    public void testCoalitionConnectedPairsDisconnected() {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine();
        Graph<String, Edge> g = disconnected();
        Set<String> all = new LinkedHashSet<>(Arrays.asList("A", "B", "C", "D"));
        long pairs = engine.coalitionConnectedPairs(g, all);
        // Two components: {A,B} and {C,D} -> 1 + 1 = 2
        assertEquals(2, pairs);
    }

    // ── Path graph: middle nodes more important ──────────────────────

    @Test
    public void testPathMiddleNodesHigherShapley() {
        GraphGameTheoryEngine engine = new GraphGameTheoryEngine()
                .setShapleyPermutations(500).setRng(new Random(42));
        GraphGameTheoryEngine.GameTheoryReport report = engine.analyze(path4());
        // B and C are middle nodes, should have >= Shapley than endpoints
        double valB = report.shapleyValues.get("B");
        double valC = report.shapleyValues.get("C");
        double valA = report.shapleyValues.get("A");
        double valD = report.shapleyValues.get("D");
        assertTrue("Middle nodes should have higher or equal Shapley",
                (valB + valC) >= (valA + valD) - 0.5);
    }
}
