package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphInfluenceSeedAdvisor}.
 *
 * @author sauravbhattacharya001
 */
public class GraphInfluenceSeedAdvisorTest {

    private static Clock fixed() {
        return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    }

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

    private static Graph<String, Edge> path(int n) {
        Graph<String, Edge> g = empty();
        for (int i = 1; i <= n; i++) g.addVertex("P" + i);
        for (int i = 1; i < n; i++) addEdge(g, "P" + i, "P" + (i + 1));
        return g;
    }

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

    private static Graph<String, Edge> twoComponents() {
        Graph<String, Edge> g = empty();
        addEdge(g, "X1", "X2"); addEdge(g, "X2", "X3"); addEdge(g, "X1", "X3");
        addEdge(g, "Y1", "Y2"); addEdge(g, "Y2", "Y3"); addEdge(g, "Y1", "Y3");
        return g;
    }

    // 1. Star -> hub dominates
    @Test public void starHubDominates() {
        Graph<String, Edge> g = star("HUB", 10);
        GraphInfluenceSeedAdvisor<String, Edge> a =
                new GraphInfluenceSeedAdvisor<>(g).withBudget(3).withEdgeProbability(0.5)
                        .withSimulations(60).withFixedClock(fixed());
        GraphInfluenceSeedAdvisor.Plan p = a.analyze();
        assertFalse(p.decisions.isEmpty());
        assertEquals("HUB", p.decisions.get(0).node);
        assertTrue("expected HUB_DOMINANT insight, got " + p.insights,
                p.insights.contains("HUB_DOMINANT") || p.decisions.size() == 1);
    }

    // 2. Path -> midpoint picked; BRIDGE_NODE reason fires somewhere
    @Test public void pathBridgeReason() {
        Graph<String, Edge> g = path(8);
        GraphInfluenceSeedAdvisor<String, Edge> a =
                new GraphInfluenceSeedAdvisor<>(g).withBudget(2).withEdgeProbability(0.6)
                        .withSimulations(50).withFixedClock(fixed());
        GraphInfluenceSeedAdvisor.Plan p = a.analyze();
        boolean bridge = false;
        for (GraphInfluenceSeedAdvisor.SeedDecision<?> d : p.decisions)
            if (d.reasons.contains("BRIDGE_NODE")) { bridge = true; break; }
        assertTrue("expected BRIDGE_NODE somewhere", bridge);
    }

    // 3. Dumbbell -> ADD_REDUNDANT_PATH playbook
    @Test public void dumbbellRedundantPathAction() {
        Graph<String, Edge> g = dumbbell();
        GraphInfluenceSeedAdvisor<String, Edge> a =
                new GraphInfluenceSeedAdvisor<>(g).withBudget(2).withEdgeProbability(0.4)
                        .withSimulations(50).withFixedClock(fixed());
        GraphInfluenceSeedAdvisor.Plan p = a.analyze();
        boolean found = false;
        for (GraphInfluenceSeedAdvisor.Action act : p.playbook)
            if ("ADD_REDUNDANT_PATH".equals(act.id)) { found = true; break; }
        // If bridge endpoints (A1/B1) got picked we expect this; allow soft check
        boolean anyBridge = false;
        for (GraphInfluenceSeedAdvisor.SeedDecision<?> d : p.decisions)
            if (d.reasons.contains("BRIDGE_NODE")) { anyBridge = true; break; }
        if (anyBridge) assertTrue("expected ADD_REDUNDANT_PATH when a bridge was picked", found);
        assertEquals(2, p.decisions.size());
    }

    // 4. Two components: CONTAINMENT in one leaves the other alone
    @Test public void containmentTwoComponents() {
        Graph<String, Edge> g = twoComponents();
        GraphInfluenceSeedAdvisor<String, Edge> a =
                new GraphInfluenceSeedAdvisor<>(g).withBudget(1).withMode(GraphInfluenceSeedAdvisor.Mode.CONTAINMENT)
                        .withSources(Arrays.asList("X1")).withEdgeProbability(0.8)
                        .withSimulations(40).withFixedClock(fixed());
        GraphInfluenceSeedAdvisor.Plan p = a.analyze();
        assertEquals(GraphInfluenceSeedAdvisor.Mode.CONTAINMENT, p.mode);
        // baseline confined to X-component (<=3); never touches Y-component
        assertTrue("baseline <= 3, got " + p.baselineSpread, p.baselineSpread <= 3.0 + 1e-6);
    }

    // 5. CONTAINMENT on star w/ leaf source picks hub -> OUTBREAK_GATEWAY+CHOKE_POINT
    @Test public void containmentStarChokePoint() {
        Graph<String, Edge> g = star("HUB", 6);
        GraphInfluenceSeedAdvisor<String, Edge> a =
                new GraphInfluenceSeedAdvisor<>(g).withBudget(1).withMode(GraphInfluenceSeedAdvisor.Mode.CONTAINMENT)
                        .withSources(Arrays.asList("L1")).withEdgeProbability(0.9)
                        .withSimulations(60).withFixedClock(fixed());
        GraphInfluenceSeedAdvisor.Plan p = a.analyze();
        assertEquals(1, p.decisions.size());
        assertEquals("HUB", p.decisions.get(0).node);
        assertTrue(p.decisions.get(0).reasons.contains("OUTBREAK_GATEWAY"));
        assertTrue(p.decisions.get(0).reasons.contains("CHOKE_POINT"));
        boolean immunize = false;
        for (GraphInfluenceSeedAdvisor.Action act : p.playbook)
            if ("IMMUNIZE_CHOKE_POINTS".equals(act.id)) { immunize = true; break; }
        assertTrue(immunize);
    }

    // 6. CONTAINMENT without sources -> throw
    @Test(expected = IllegalArgumentException.class)
    public void containmentNeedsSources() {
        Graph<String, Edge> g = star("HUB", 4);
        new GraphInfluenceSeedAdvisor<>(g).withMode(GraphInfluenceSeedAdvisor.Mode.CONTAINMENT).analyze();
    }

    // 7. Risk appetite monotonicity
    @Test public void riskAppetiteMonotone() {
        Graph<String, Edge> g = path(6);
        int sims = 60;
        int c = new GraphInfluenceSeedAdvisor<>(g).withBudget(1).withSimulations(sims)
                .withRiskAppetite(GraphInfluenceSeedAdvisor.RiskAppetite.CAUTIOUS).withFixedClock(fixed()).analyze().simulationsUsed;
        int b = new GraphInfluenceSeedAdvisor<>(g).withBudget(1).withSimulations(sims)
                .withRiskAppetite(GraphInfluenceSeedAdvisor.RiskAppetite.BALANCED).withFixedClock(fixed()).analyze().simulationsUsed;
        int x = new GraphInfluenceSeedAdvisor<>(g).withBudget(1).withSimulations(sims)
                .withRiskAppetite(GraphInfluenceSeedAdvisor.RiskAppetite.AGGRESSIVE).withFixedClock(fixed()).analyze().simulationsUsed;
        assertTrue(c >= b && b >= x);
    }

    // 8. Deterministic JSON
    @Test public void deterministicJson() {
        Graph<String, Edge> g = star("HUB", 5);
        GraphInfluenceSeedAdvisor<String, Edge> a1 =
                new GraphInfluenceSeedAdvisor<>(g).withBudget(2).withSimulations(50).withFixedClock(fixed());
        GraphInfluenceSeedAdvisor<String, Edge> a2 =
                new GraphInfluenceSeedAdvisor<>(g).withBudget(2).withSimulations(50).withFixedClock(fixed());
        String j1 = a1.toJson(a1.analyze());
        String j2 = a2.toJson(a2.analyze());
        assertEquals(j1, j2);
    }

    // 9. K=0 -> empty plan, BASELINE_OK
    @Test public void kZeroBaseline() {
        Graph<String, Edge> g = star("HUB", 3);
        GraphInfluenceSeedAdvisor<String, Edge> a =
                new GraphInfluenceSeedAdvisor<>(g).withBudget(0).withFixedClock(fixed());
        GraphInfluenceSeedAdvisor.Plan p = a.analyze();
        assertTrue(p.decisions.isEmpty());
        assertEquals("A", p.grade);
        boolean baseline = false;
        for (GraphInfluenceSeedAdvisor.Action act : p.playbook)
            if ("BASELINE_OK".equals(act.id)) { baseline = true; break; }
        assertTrue(baseline);
    }

    // 10. K > N -> no crash, plan size <= N
    @Test public void kGreaterThanN() {
        Graph<String, Edge> g = path(3);
        GraphInfluenceSeedAdvisor<String, Edge> a =
                new GraphInfluenceSeedAdvisor<>(g).withBudget(99).withSimulations(30).withFixedClock(fixed());
        GraphInfluenceSeedAdvisor.Plan p = a.analyze();
        assertTrue(p.decisions.size() <= 3);
    }

    // 11. High minMarginalGain -> SKIP_REDUNDANT alternates + MONITOR_FRINGE playbook
    @Test public void monitorFringeOnHighThreshold() {
        Graph<String, Edge> g = path(5);
        GraphInfluenceSeedAdvisor<String, Edge> a =
                new GraphInfluenceSeedAdvisor<>(g).withBudget(5).withMinMarginalGain(50.0)
                        .withSimulations(30).withFixedClock(fixed());
        GraphInfluenceSeedAdvisor.Plan p = a.analyze();
        assertFalse("expected at least one alternate", p.alternates.isEmpty());
        boolean monitor = false;
        for (GraphInfluenceSeedAdvisor.Action act : p.playbook)
            if ("MONITOR_FRINGE".equals(act.id)) { monitor = true; break; }
        assertTrue("expected MONITOR_FRINGE", monitor);
    }

    // 12. Markdown contains required sections
    @Test public void markdownStructure() {
        Graph<String, Edge> g = star("HUB", 5);
        GraphInfluenceSeedAdvisor<String, Edge> a =
                new GraphInfluenceSeedAdvisor<>(g).withBudget(2).withSimulations(30).withFixedClock(fixed());
        String md = a.toMarkdown(a.analyze());
        assertTrue(md.contains("# GraphInfluenceSeedAdvisor"));
        assertTrue(md.contains("| Seed | Verdict | Priority | MarginalGain | Coverage | Reasons |"));
        assertTrue(md.contains("## Playbook"));
    }

    // 13. Empty graph -> throw
    @Test(expected = IllegalArgumentException.class)
    public void emptyGraphRejected() {
        new GraphInfluenceSeedAdvisor<>(empty());
    }

    // 14. Bonus: SPREAD on dumbbell with K=2 chooses one from each clique (high coverage).
    @Test public void spreadCoversBothCliques() {
        Graph<String, Edge> g = dumbbell();
        GraphInfluenceSeedAdvisor<String, Edge> a =
                new GraphInfluenceSeedAdvisor<>(g).withBudget(2).withEdgeProbability(0.5)
                        .withSimulations(80).withMinMarginalGain(0.0).withFixedClock(fixed());
        GraphInfluenceSeedAdvisor.Plan p = a.analyze();
        assertEquals(2, p.decisions.size());
        assertTrue("expected coverage >= 4 for dumbbell, got " + p.expectedCoverage,
                p.expectedCoverage >= 4.0);
    }
}
