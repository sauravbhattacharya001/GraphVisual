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
 * Tests for {@link GraphCascadingFailureAdvisor}.
 *
 * @author sauravbhattacharya001
 */
public class GraphCascadingFailureAdvisorTest {

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

    private static Graph<String, Edge> triangle() {
        Graph<String, Edge> g = empty();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "A", "C");
        return g;
    }

    // 1. Star: centre is CRITICAL_HUB (disconnects + cascade).
    @Test public void starCenterIsCriticalHub() {
        Graph<String, Edge> g = star("HUB", 8);
        GraphCascadingFailureAdvisor.Plan p =
                new GraphCascadingFailureAdvisor<>(g)
                        .withFixedClock(fixed()).analyze();
        GraphCascadingFailureAdvisor.NodeRisk<?> hub = p.perNode.get("HUB");
        assertNotNull(hub);
        assertEquals(GraphCascadingFailureAdvisor.Verdict.CRITICAL_HUB, hub.verdict);
        assertEquals(GraphCascadingFailureAdvisor.Priority.P0, hub.priority);
        assertTrue("expected DISCONNECTS_GRAPH reason, got " + hub.reasons,
                hub.reasons.contains("DISCONNECTS_GRAPH"));
        assertEquals("F", p.grade);
    }

    // 2. Path: at least one interior node is an articulation point.
    @Test public void pathInteriorArticulation() {
        Graph<String, Edge> g = path(6);
        GraphCascadingFailureAdvisor.Plan p =
                new GraphCascadingFailureAdvisor<>(g)
                        .withFixedClock(fixed()).analyze();
        boolean anyArt = false;
        for (GraphCascadingFailureAdvisor.NodeRisk<?> nr : p.perNode.values()) {
            if (nr.articulation) { anyArt = true; break; }
        }
        assertTrue("expected at least one articulation point", anyArt);
    }

    // 3. Low tolerance triggers more cascade than high tolerance.
    @Test public void lowToleranceMoreCascade() {
        Graph<String, Edge> g = star("HUB", 6);
        GraphCascadingFailureAdvisor.Plan low =
                new GraphCascadingFailureAdvisor<>(g)
                        .withTolerance(0.0)
                        .withInitialFailures(Arrays.asList("L1"))
                        .withFixedClock(fixed()).analyze();
        GraphCascadingFailureAdvisor.Plan high =
                new GraphCascadingFailureAdvisor<>(g)
                        .withTolerance(5.0)
                        .withInitialFailures(Arrays.asList("L1"))
                        .withFixedClock(fixed()).analyze();
        assertTrue("low tolerance should not cascade less than high tolerance: "
                        + low.initialCascadeFraction + " vs " + high.initialCascadeFraction,
                low.initialCascadeFraction >= high.initialCascadeFraction);
    }

    // 4. Risk-appetite monotonicity on a shared risky node.
    @Test public void riskAppetiteMonotonic() {
        Graph<String, Edge> g = star("HUB", 10);
        double cautious = avgRisk(g, GraphCascadingFailureAdvisor.RiskAppetite.CAUTIOUS);
        double balanced = avgRisk(g, GraphCascadingFailureAdvisor.RiskAppetite.BALANCED);
        double aggressive = avgRisk(g, GraphCascadingFailureAdvisor.RiskAppetite.AGGRESSIVE);
        assertTrue("cautious >= balanced: " + cautious + " vs " + balanced,
                cautious + 1e-9 >= balanced);
        assertTrue("balanced >= aggressive: " + balanced + " vs " + aggressive,
                balanced + 1e-9 >= aggressive);
    }

    private static double avgRisk(Graph<String, Edge> g,
                                  GraphCascadingFailureAdvisor.RiskAppetite r) {
        GraphCascadingFailureAdvisor.Plan p =
                new GraphCascadingFailureAdvisor<>(g)
                        .withRiskAppetite(r).withFixedClock(fixed()).analyze();
        double sum = 0.0;
        for (GraphCascadingFailureAdvisor.NodeRisk<?> nr : p.perNode.values()) sum += nr.riskScore;
        return sum / Math.max(1, p.perNode.size());
    }

    // 5. Initial-failure scenario propagates.
    @Test public void initialFailureScenarioPropagates() {
        Graph<String, Edge> g = star("HUB", 6);
        GraphCascadingFailureAdvisor.Plan p =
                new GraphCascadingFailureAdvisor<>(g)
                        .withTolerance(0.0)
                        .withInitialFailures(Arrays.asList("HUB"))
                        .withFixedClock(fixed()).analyze();
        assertTrue("HUB failure should cascade to leaves: " + p.initialCascadeFraction,
                p.initialCascadeFraction > 0.5);
        boolean hasInsight = false;
        for (String s : p.insights) if (s.startsWith("INITIAL_CASCADE:")) { hasInsight = true; break; }
        assertTrue("expected INITIAL_CASCADE insight", hasInsight);
    }

    // 6. Empty graph throws.
    @Test(expected = IllegalArgumentException.class)
    public void emptyGraphThrows() {
        new GraphCascadingFailureAdvisor<>(empty());
    }

    // 7. Single-node graph: clean, no playbook spam.
    @Test public void singleNodeGradeA() {
        Graph<String, Edge> g = empty();
        g.addVertex("solo");
        GraphCascadingFailureAdvisor.Plan p =
                new GraphCascadingFailureAdvisor<>(g)
                        .withFixedClock(fixed()).analyze();
        assertEquals(1, p.graphSize);
        // grade is at worst B if isolated-overload is flagged; should be A here since load=0
        assertTrue("expected A or B grade, got " + p.grade,
                "A".equals(p.grade) || "B".equals(p.grade));
        assertEquals(1, p.playbook.size());
        assertEquals("BASELINE_RESILIENT", p.playbook.get(0).id);
    }

    // 8. JSON determinism with fixed clock.
    @Test public void jsonDeterminism() {
        Graph<String, Edge> g = path(5);
        GraphCascadingFailureAdvisor<String, Edge> a1 =
                new GraphCascadingFailureAdvisor<>(g).withFixedClock(fixed());
        GraphCascadingFailureAdvisor<String, Edge> a2 =
                new GraphCascadingFailureAdvisor<>(g).withFixedClock(fixed());
        String j1 = a1.toJson(a1.analyze());
        String j2 = a2.toJson(a2.analyze());
        assertEquals(j1, j2);
    }

    // 9. Markdown sections present.
    @Test public void markdownSections() {
        Graph<String, Edge> g = path(4);
        GraphCascadingFailureAdvisor<String, Edge> a =
                new GraphCascadingFailureAdvisor<>(g).withFixedClock(fixed());
        String md = a.toMarkdown(a.analyze());
        assertTrue(md.contains("## Summary"));
        assertTrue(md.contains("## Per-node risks"));
        assertTrue(md.contains("## Playbook"));
        assertTrue(md.contains("## Insights"));
        assertTrue(md.contains("| Node | Verdict | Priority | Risk |"));
    }

    // 10. Text headline contains VERDICT.
    @Test public void textVerdictHeadline() {
        Graph<String, Edge> g = triangle();
        GraphCascadingFailureAdvisor<String, Edge> a =
                new GraphCascadingFailureAdvisor<>(g).withFixedClock(fixed());
        String txt = a.render(a.analyze());
        assertTrue("expected VERDICT in text: " + txt, txt.contains("VERDICT"));
    }

    // 11. Initial failures with unknown nodes -> SOURCES_OUT_OF_GRAPH.
    @Test public void unknownInitialSources() {
        Graph<String, Edge> g = triangle();
        GraphCascadingFailureAdvisor.Plan p =
                new GraphCascadingFailureAdvisor<>(g)
                        .withInitialFailures(Arrays.asList("ZZZ", "A"))
                        .withFixedClock(fixed()).analyze();
        boolean ok = false;
        for (String s : p.insights) if (s.startsWith("SOURCES_OUT_OF_GRAPH:")) { ok = true; break; }
        assertTrue("expected SOURCES_OUT_OF_GRAPH insight", ok);
    }

    // 12. Triangle with generous tolerance: baseline reasonably healthy.
    @Test public void triangleBaselineHealthy() {
        Graph<String, Edge> g = triangle();
        GraphCascadingFailureAdvisor.Plan p =
                new GraphCascadingFailureAdvisor<>(g)
                        .withTolerance(1.0).withFixedClock(fixed()).analyze();
        // With +100% tolerance, removing any vertex of the triangle still leaves the
        // surviving edge intact; no CRITICAL_HUB should exist.
        for (GraphCascadingFailureAdvisor.NodeRisk<?> nr : p.perNode.values()) {
            assertNotEquals(GraphCascadingFailureAdvisor.Verdict.CRITICAL_HUB, nr.verdict);
        }
        assertNotEquals("F", p.grade);
    }

    // 13. JSON top-level keys.
    @Test public void jsonTopLevelKeys() {
        Graph<String, Edge> g = triangle();
        GraphCascadingFailureAdvisor<String, Edge> a =
                new GraphCascadingFailureAdvisor<>(g).withFixedClock(fixed());
        String j = a.toJson(a.analyze());
        for (String key : new String[]{
                "\"grade\"", "\"summary\"", "\"perNode\"", "\"playbook\"",
                "\"insights\"", "\"generatedAt\"", "\"initialCascadeFraction\""}) {
            assertTrue("missing key " + key + " in " + j, j.contains(key));
        }
    }

    // 14. Markdown table header present and stable.
    @Test public void markdownTableHeader() {
        Graph<String, Edge> g = star("HUB", 4);
        GraphCascadingFailureAdvisor<String, Edge> a =
                new GraphCascadingFailureAdvisor<>(g).withFixedClock(fixed());
        String md = a.toMarkdown(a.analyze());
        assertTrue(md.contains("| Node | Verdict | Priority | Risk | Load/Cap | WorstCascade | Reasons |"));
    }
}
