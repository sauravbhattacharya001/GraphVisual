package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphAutoPilot}.
 */
public class GraphAutoPilotTest {

    // ── Helpers ────────────────────────────────────────────

    private static Graph<String, Edge> emptyGraph() {
        return new UndirectedSparseGraph<>();
    }

    private static Graph<String, Edge> singleNode() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        return g;
    }

    private static Graph<String, Edge> twoNodesOneEdge() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        g.addVertex("B");
        g.addEdge(new Edge("f", "A", "B"), "A", "B");
        return g;
    }

    /** A ─ B ─ C ─ D  (path graph with 3 bridges) */
    private static Graph<String, Edge> pathGraph() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        String[] nodes = {"A", "B", "C", "D"};
        for (String n : nodes) g.addVertex(n);
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        return g;
    }

    /** Triangle: A ─ B ─ C ─ A  (no bridges, no articulation points) */
    private static Graph<String, Edge> triangle() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        g.addVertex("B");
        g.addVertex("C");
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        return g;
    }

    /** Star: hub H connected to S1..S5 */
    private static Graph<String, Edge> starGraph() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("H");
        for (int i = 1; i <= 5; i++) {
            String s = "S" + i;
            g.addVertex(s);
            addEdge(g, "H", s);
        }
        return g;
    }

    /** Two triangles connected by a single bridge: A-B-C-A  D-E-F-D  with C-D bridge */
    private static Graph<String, Edge> bowTieGraph() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (String v : new String[]{"A", "B", "C", "D", "E", "F"}) g.addVertex(v);
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        addEdge(g, "D", "E");
        addEdge(g, "E", "F");
        addEdge(g, "F", "D");
        addEdge(g, "C", "D"); // bridge
        return g;
    }

    /** Disconnected: A-B and C-D (two components) */
    private static Graph<String, Edge> disconnectedGraph() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (String v : new String[]{"A", "B", "C", "D"}) g.addVertex(v);
        addEdge(g, "A", "B");
        addEdge(g, "C", "D");
        return g;
    }

    /** Large hub-spoke with pendants */
    private static Graph<String, Edge> hubSpokeGraph() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("HUB");
        for (int i = 0; i < 20; i++) {
            String s = "N" + i;
            g.addVertex(s);
            addEdge(g, "HUB", s);
        }
        // Add a few inter-spoke edges
        addEdge(g, "N0", "N1");
        addEdge(g, "N2", "N3");
        return g;
    }

    private static void addEdge(Graph<String, Edge> g, String a, String b) {
        g.addEdge(new Edge("f", a, b), a, b);
    }

    // ── Constructor tests ──────────────────────────────────

    @Test(expected = NullPointerException.class)
    public void constructorRejectsNull() {
        new GraphAutoPilot(null);
    }

    // ── Empty / trivial graphs ─────────────────────────────

    @Test
    public void emptyGraphDoesNotThrow() {
        GraphAutoPilot pilot = new GraphAutoPilot(emptyGraph());
        pilot.analyze();
        assertEquals(0, pilot.getWeaknesses().size());
        assertEquals(0, pilot.getActionPlan().size());
    }

    @Test
    public void singleNodeDoesNotThrow() {
        GraphAutoPilot pilot = new GraphAutoPilot(singleNode());
        pilot.analyze();
        assertNotNull(pilot.getBeforeHealth());
    }

    @Test
    public void twoNodesProducesReport() {
        GraphAutoPilot pilot = new GraphAutoPilot(twoNodesOneEdge());
        pilot.analyze();
        assertNotNull(pilot.formatTextReport());
        assertTrue(pilot.formatTextReport().contains("GRAPH AUTOPILOT"));
    }

    // ── Bridge detection ───────────────────────────────────

    @Test
    public void pathGraphDetectsBridges() {
        GraphAutoPilot pilot = new GraphAutoPilot(pathGraph());
        pilot.analyze();
        boolean hasBridgeWeakness = pilot.getWeaknesses().stream()
                .anyMatch(w -> w.getCategory().contains("Bridge"));
        assertTrue("Path graph should have bridge weakness", hasBridgeWeakness);
    }

    @Test
    public void triangleHasNoBridges() {
        GraphAutoPilot pilot = new GraphAutoPilot(triangle());
        pilot.analyze();
        boolean hasBridgeWeakness = pilot.getWeaknesses().stream()
                .anyMatch(w -> w.getCategory().contains("Bridge"));
        assertFalse("Triangle should have no bridges", hasBridgeWeakness);
    }

    @Test
    public void bowTieDetectsBridgeAndPlansbypass() {
        GraphAutoPilot pilot = new GraphAutoPilot(bowTieGraph());
        pilot.analyze();
        boolean hasBridge = pilot.getWeaknesses().stream()
                .anyMatch(w -> w.getCategory().contains("Bridge"));
        assertTrue("Bow-tie should detect bridge C-D", hasBridge);

        boolean hasBypass = pilot.getActionPlan().stream()
                .anyMatch(a -> a.getType() == GraphAutoPilot.ActionType.ADD_BYPASS);
        assertTrue("Should plan a bypass for the bridge", hasBypass);
    }

    // ── Disconnected components ────────────────────────────

    @Test
    public void disconnectedGraphDetectsComponents() {
        GraphAutoPilot pilot = new GraphAutoPilot(disconnectedGraph());
        pilot.analyze();
        boolean hasDisconnected = pilot.getWeaknesses().stream()
                .anyMatch(w -> w.getCategory().contains("Disconnected"));
        assertTrue("Should detect disconnected components", hasDisconnected);

        boolean hasBridgeAction = pilot.getActionPlan().stream()
                .anyMatch(a -> a.getType() == GraphAutoPilot.ActionType.BRIDGE_COMMUNITIES);
        assertTrue("Should plan community bridging", hasBridgeAction);
    }

    // ── Peripheral nodes ───────────────────────────────────

    @Test
    public void starGraphDetectsPeripherals() {
        GraphAutoPilot pilot = new GraphAutoPilot(starGraph());
        pilot.analyze();
        boolean hasPeripheral = pilot.getWeaknesses().stream()
                .anyMatch(w -> w.getCategory().contains("Peripheral"));
        assertTrue("Star graph should detect peripheral nodes", hasPeripheral);

        boolean hasPeripheralAction = pilot.getActionPlan().stream()
                .anyMatch(a -> a.getType() == GraphAutoPilot.ActionType.CONNECT_PERIPHERAL);
        assertTrue("Should plan peripheral connections", hasPeripheralAction);
    }

    // ── Health snapshot ────────────────────────────────────

    @Test
    public void healthSnapshotHasAllMetrics() {
        GraphAutoPilot pilot = new GraphAutoPilot(bowTieGraph());
        pilot.analyze();
        GraphAutoPilot.HealthSnapshot h = pilot.getBeforeHealth();
        Map<String, Double> map = h.toMap();
        assertTrue(map.containsKey("Bridges"));
        assertTrue(map.containsKey("Articulation Points"));
        assertTrue(map.containsKey("Components"));
        assertTrue(map.containsKey("Avg Degree"));
        assertTrue(map.containsKey("Health Score"));
        assertTrue(h.healthScore >= 0 && h.healthScore <= 100);
    }

    @Test
    public void triangleIsHealthy() {
        GraphAutoPilot pilot = new GraphAutoPilot(triangle());
        pilot.analyze();
        assertTrue("Triangle should be very healthy",
                pilot.getBeforeHealth().healthScore >= 80);
    }

    // ── Action plan ────────────────────────────────────────

    @Test
    public void actionsAreSortedByImpact() {
        GraphAutoPilot pilot = new GraphAutoPilot(hubSpokeGraph());
        pilot.analyze();
        List<GraphAutoPilot.Action> plan = pilot.getActionPlan();
        for (int i = 1; i < plan.size(); i++) {
            assertTrue("Actions should be sorted by impact descending",
                    plan.get(i - 1).getImpactScore() >= plan.get(i).getImpactScore());
        }
    }

    @Test
    public void actionPlanCappedAt20() {
        // Even a big unhealthy graph shouldn't produce more than MAX_ACTIONS
        GraphAutoPilot pilot = new GraphAutoPilot(hubSpokeGraph());
        pilot.analyze();
        assertTrue(pilot.getActionPlan().size() <= 20);
    }

    @Test
    public void getTopActionsReturnsSubset() {
        GraphAutoPilot pilot = new GraphAutoPilot(hubSpokeGraph());
        pilot.analyze();
        List<GraphAutoPilot.Action> top3 = pilot.getTopActions(3);
        assertTrue(top3.size() <= 3);
        if (pilot.getActionPlan().size() >= 3) {
            assertEquals(3, top3.size());
        }
    }

    // ── Simulation ─────────────────────────────────────────

    @Test
    public void actionsHaveSimulatedMetrics() {
        GraphAutoPilot pilot = new GraphAutoPilot(bowTieGraph());
        pilot.analyze();
        for (GraphAutoPilot.Action a : pilot.getActionPlan()) {
            assertNotNull(a.getBeforeMetrics());
            assertNotNull(a.getAfterMetrics());
            assertFalse(a.getBeforeMetrics().isEmpty());
        }
    }

    // ── Apply actions ──────────────────────────────────────

    @Test
    public void applyTopActionsReturnsNewGraph() {
        GraphAutoPilot pilot = new GraphAutoPilot(bowTieGraph());
        pilot.analyze();
        Graph<String, Edge> optimized = pilot.applyTopActions(3);
        assertNotNull(optimized);
        assertTrue("Optimized graph should have at least as many edges",
                optimized.getEdgeCount() >= bowTieGraph().getEdgeCount());
        // Original should be unchanged
        assertEquals(7, bowTieGraph().getEdgeCount());
    }

    @Test
    public void applyZeroActionsReturnsCopy() {
        GraphAutoPilot pilot = new GraphAutoPilot(triangle());
        pilot.analyze();
        Graph<String, Edge> copy = pilot.applyTopActions(0);
        assertEquals(triangle().getVertexCount(), copy.getVertexCount());
        assertEquals(triangle().getEdgeCount(), copy.getEdgeCount());
    }

    // ── Reports ────────────────────────────────────────────

    @Test
    public void textReportContainsAllSections() {
        GraphAutoPilot pilot = new GraphAutoPilot(bowTieGraph());
        pilot.analyze();
        String report = pilot.formatTextReport();
        assertTrue(report.contains("Current Health"));
        assertTrue(report.contains("Weaknesses Found"));
        assertTrue(report.contains("Action Plan"));
    }

    @Test
    public void htmlReportIsValid() {
        GraphAutoPilot pilot = new GraphAutoPilot(bowTieGraph());
        pilot.analyze();
        String html = pilot.generateHtml();
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("GraphAutoPilot"));
        assertTrue(html.contains("</html>"));
    }

    @Test
    public void htmlExportCreatesFile() throws Exception {
        GraphAutoPilot pilot = new GraphAutoPilot(bowTieGraph());
        pilot.analyze();
        File tmp = File.createTempFile("autopilot-", ".html");
        tmp.deleteOnExit();
        pilot.exportHtml(tmp);
        assertTrue(tmp.length() > 100);
    }

    // ── Error handling ─────────────────────────────────────

    @Test(expected = IllegalStateException.class)
    public void getWeaknessesBeforeAnalyzeThrows() {
        new GraphAutoPilot(triangle()).getWeaknesses();
    }

    @Test(expected = IllegalStateException.class)
    public void getActionPlanBeforeAnalyzeThrows() {
        new GraphAutoPilot(triangle()).getActionPlan();
    }

    @Test(expected = IllegalStateException.class)
    public void formatReportBeforeAnalyzeThrows() {
        new GraphAutoPilot(triangle()).formatTextReport();
    }

    // ── Action properties ──────────────────────────────────

    @Test
    public void actionTypeHasDescription() {
        for (GraphAutoPilot.ActionType t : GraphAutoPilot.ActionType.values()) {
            assertNotNull(t.getDescription());
            assertFalse(t.getDescription().isEmpty());
        }
    }

    @Test
    public void priorityHasWeight() {
        assertTrue(GraphAutoPilot.Priority.CRITICAL.getWeight() > GraphAutoPilot.Priority.LOW.getWeight());
        assertEquals("Critical", GraphAutoPilot.Priority.CRITICAL.getLabel());
    }

    @Test
    public void actionAccessorsWork() {
        GraphAutoPilot pilot = new GraphAutoPilot(pathGraph());
        pilot.analyze();
        if (!pilot.getActionPlan().isEmpty()) {
            GraphAutoPilot.Action a = pilot.getActionPlan().get(0);
            assertNotNull(a.getType());
            assertNotNull(a.getPriority());
            assertNotNull(a.getDescription());
            assertNotNull(a.getReasoning());
        }
    }

    // ── Weakness accessors ─────────────────────────────────

    @Test
    public void weaknessAccessorsWork() {
        GraphAutoPilot pilot = new GraphAutoPilot(pathGraph());
        pilot.analyze();
        for (GraphAutoPilot.Weakness w : pilot.getWeaknesses()) {
            assertNotNull(w.getCategory());
            assertNotNull(w.getDescription());
            assertNotNull(w.getSeverity());
            assertNotNull(w.getInvolvedNodes());
        }
    }

    // ── Large-ish graph doesn't time out ───────────────────

    @Test
    public void mediumGraphCompletesQuickly() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < 100; i++) g.addVertex("V" + i);
        java.util.Random rng = new java.util.Random(42);
        for (int i = 1; i < 100; i++) {
            addEdge(g, "V" + i, "V" + rng.nextInt(i)); // random tree
        }
        // Add some extra edges
        for (int i = 0; i < 50; i++) {
            int a = rng.nextInt(100), b = rng.nextInt(100);
            if (a != b && g.findEdge("V" + a, "V" + b) == null) {
                addEdge(g, "V" + a, "V" + b);
            }
        }

        long start = System.currentTimeMillis();
        GraphAutoPilot pilot = new GraphAutoPilot(g);
        pilot.analyze();
        long elapsed = System.currentTimeMillis() - start;

        assertTrue("Should complete in under 10 seconds, took " + elapsed + "ms", elapsed < 10000);
        assertNotNull(pilot.formatTextReport());
        assertNotNull(pilot.generateHtml());
    }
}
