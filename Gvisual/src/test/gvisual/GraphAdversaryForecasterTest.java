package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphAdversaryForecaster}.
 *
 * @author sauravbhattacharya001
 */
public class GraphAdversaryForecasterTest {

    private static Graph<String, Edge> empty() { return new UndirectedSparseGraph<>(); }

    private static void addEdge(Graph<String, Edge> g, String u, String v) {
        if (!g.containsVertex(u)) g.addVertex(u);
        if (!g.containsVertex(v)) g.addVertex(v);
        g.addEdge(new Edge("f", u, v), u, v);
    }

    private static Graph<String, Edge> star(String center, int n) {
        Graph<String, Edge> g = empty();
        g.addVertex(center);
        for (int i = 1; i <= n; i++) addEdge(g, center, "L" + i);
        return g;
    }

    /** Path: P1 - P2 - P3 - P4 - P5. */
    private static Graph<String, Edge> path(int n) {
        Graph<String, Edge> g = empty();
        for (int i = 1; i <= n; i++) g.addVertex("P" + i);
        for (int i = 1; i < n; i++) addEdge(g, "P" + i, "P" + (i + 1));
        return g;
    }

    /** Two K4 cliques joined by a single bridge edge between their hubs. */
    private static Graph<String, Edge> dumbbell() {
        Graph<String, Edge> g = empty();
        String[] a = {"A1", "A2", "A3", "A4"};
        String[] b = {"B1", "B2", "B3", "B4"};
        for (int i = 0; i < a.length; i++)
            for (int j = i + 1; j < a.length; j++) addEdge(g, a[i], a[j]);
        for (int i = 0; i < b.length; i++)
            for (int j = i + 1; j < b.length; j++) addEdge(g, b[i], b[j]);
        addEdge(g, "A1", "B1"); // bridge
        return g;
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullGraph() {
        new GraphAdversaryForecaster(null, Collections.emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullTrace() {
        new GraphAdversaryForecaster(empty(), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBadTopK() {
        new GraphAdversaryForecaster(empty(), Collections.emptyList()).withTopK(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBadTemperature() {
        new GraphAdversaryForecaster(empty(), Collections.emptyList()).withTemperature(0.0);
    }

    @Test
    public void emptyTraceProducesNonEmptyForecast() {
        Graph<String, Edge> g = star("H", 4);
        GraphAdversaryForecaster.Forecast f =
                new GraphAdversaryForecaster(g, Collections.emptyList()).withTopK(3).analyze();
        assertEquals(5, f.liveNodeCount);
        assertEquals(0, f.removedCount);
        assertFalse("forecast should fall back to a sensible default", f.nextTargets.isEmpty());
        assertEquals(3, f.nextTargets.size());
        // Primary should be the default fallback.
        assertEquals(GraphAdversaryForecaster.Strategy.DEGREE_TARGETING, f.primary.strategy);
        assertEquals(0.0, f.primary.confidence, 1e-9);
    }

    @Test
    public void hubRemovalImpliesDegreeOrBridgeTargeting() {
        // Big star: removing the center is unambiguously high-degree AND articulation point.
        Graph<String, Edge> g = star("H", 6);
        GraphAdversaryForecaster.Forecast f =
                new GraphAdversaryForecaster(g, Collections.singletonList("H")).withTopK(3).analyze();
        assertTrue("primary confidence should be high after picking the obvious hub",
                f.primary.confidence > 0.5);
        GraphAdversaryForecaster.Strategy s = f.primary.strategy;
        assertTrue("expected hub-seeking strategy, got " + s,
                s == GraphAdversaryForecaster.Strategy.DEGREE_TARGETING
                        || s == GraphAdversaryForecaster.Strategy.BRIDGE_TARGETING
                        || s == GraphAdversaryForecaster.Strategy.BETWEENNESS_TARGETING
                        || s == GraphAdversaryForecaster.Strategy.COMMUNITY_CUT);
        assertEquals(6, f.liveNodeCount);
        assertEquals(1, f.removedCount);
    }

    @Test
    public void middlePathRemovalImpliesBetweennessOrBridge() {
        // P1-P2-P3-P4-P5; removing P3 is the textbook betweenness/bridge attack.
        Graph<String, Edge> g = path(5);
        GraphAdversaryForecaster.Forecast f =
                new GraphAdversaryForecaster(g, Collections.singletonList("P3")).withTopK(2).analyze();
        GraphAdversaryForecaster.Strategy s = f.primary.strategy;
        assertTrue("expected centrality-seeking strategy, got " + s,
                s == GraphAdversaryForecaster.Strategy.BETWEENNESS_TARGETING
                        || s == GraphAdversaryForecaster.Strategy.BRIDGE_TARGETING
                        || s == GraphAdversaryForecaster.Strategy.COMMUNITY_CUT);
        assertTrue("centrality confidence should be very high", f.primary.confidence > 0.7);
    }

    @Test
    public void dumbbellBridgeAttackInferred() {
        Graph<String, Edge> g = dumbbell();
        // Attack the two bridge endpoints in order.
        GraphAdversaryForecaster.Forecast f =
                new GraphAdversaryForecaster(g, Arrays.asList("A1", "B1")).withTopK(4).analyze();
        GraphAdversaryForecaster.Strategy s = f.primary.strategy;
        assertTrue("expected structural-cut or hub strategy, got " + s,
                s == GraphAdversaryForecaster.Strategy.BRIDGE_TARGETING
                        || s == GraphAdversaryForecaster.Strategy.COMMUNITY_CUT
                        || s == GraphAdversaryForecaster.Strategy.BETWEENNESS_TARGETING
                        || s == GraphAdversaryForecaster.Strategy.DEGREE_TARGETING);
        assertNotEquals(GraphAdversaryForecaster.Strategy.RANDOM, s);
        assertNotEquals(GraphAdversaryForecaster.Strategy.PERIPHERAL, s);
        assertEquals(2, f.removedCount);
        assertEquals(6, f.liveNodeCount);
    }

    @Test
    public void forecastProbabilitiesSumToOne() {
        Graph<String, Edge> g = dumbbell();
        GraphAdversaryForecaster.Forecast f =
                new GraphAdversaryForecaster(g, Collections.singletonList("A1"))
                        .withTopK(3).withTemperature(1.5).analyze();
        assertEquals(3, f.nextTargets.size());
        double sum = 0.0;
        for (GraphAdversaryForecaster.TargetForecast t : f.nextTargets) {
            assertTrue("prob must be in [0,1]: " + t.probability,
                    t.probability >= 0.0 && t.probability <= 1.0 + 1e-9);
            sum += t.probability;
        }
        assertEquals(1.0, sum, 1e-6);
    }

    @Test
    public void topKClampedToLiveNodes() {
        Graph<String, Edge> g = path(3);
        // Remove all but one node — only 1 node will remain alive after replay.
        GraphAdversaryForecaster.Forecast f =
                new GraphAdversaryForecaster(g, Arrays.asList("P1", "P2"))
                        .withTopK(10).analyze();
        assertEquals(1, f.liveNodeCount);
        assertEquals(1, f.nextTargets.size());
        assertEquals(1.0, f.nextTargets.get(0).probability, 1e-6);
    }

    @Test
    public void ignoredTraceEntriesCounted() {
        Graph<String, Edge> g = star("H", 3);
        GraphAdversaryForecaster.Forecast f =
                new GraphAdversaryForecaster(g, Arrays.asList("ghost1", null, "H", "ghost2"))
                        .withTopK(2).analyze();
        assertEquals(3, f.ignoredTraceCount);
        assertEquals(1, f.removedCount);
    }

    @Test
    public void exportsAreNonEmptyAndContainPrimaryStrategy() {
        Graph<String, Edge> g = star("H", 5);
        GraphAdversaryForecaster fc = new GraphAdversaryForecaster(g, Collections.singletonList("H"));
        GraphAdversaryForecaster.Forecast f = fc.analyze();
        String txt = fc.toText(f);
        String md  = fc.toMarkdown(f);
        String js  = fc.toJson(f);
        assertNotNull(txt); assertNotNull(md); assertNotNull(js);
        assertTrue(txt.length() > 50);
        assertTrue(md.length() > 50);
        assertTrue(js.length() > 50);
        String primary = f.primary.strategy.name();
        assertTrue("text export should mention primary strategy", txt.contains(primary));
        assertTrue("markdown export should mention primary strategy", md.contains(primary));
        assertTrue("json export should mention primary strategy", js.contains(primary));
    }

    @Test
    public void jsonExportIsWellFormedish() {
        Graph<String, Edge> g = dumbbell();
        GraphAdversaryForecaster fc = new GraphAdversaryForecaster(g, Arrays.asList("A1", "B1"));
        String js = fc.toJson(fc.analyze());
        // Balanced braces and brackets.
        int braces = 0, brackets = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < js.length(); i++) {
            char c = js.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\') { escape = true; continue; }
            if (c == '"')  { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') braces++;
            else if (c == '}') braces--;
            else if (c == '[') brackets++;
            else if (c == ']') brackets--;
            assertTrue("unbalanced at index " + i, braces >= 0 && brackets >= 0);
        }
        assertEquals(0, braces);
        assertEquals(0, brackets);
        assertTrue(js.startsWith("{") && js.endsWith("}"));
        assertTrue(js.contains("\"strategy_ranking\""));
        assertTrue(js.contains("\"next_targets\""));
        assertTrue(js.contains("\"defenses\""));
    }
}
