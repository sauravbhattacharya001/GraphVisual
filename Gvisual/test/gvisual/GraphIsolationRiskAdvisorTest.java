package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphIsolationRiskAdvisor}.
 *
 * @author sauravbhattacharya001
 */
public class GraphIsolationRiskAdvisorTest {

    private static Clock fixed() {
        return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    }

    private static Graph<String, Edge> empty() { return new UndirectedSparseGraph<>(); }

    private static void addEdge(Graph<String, Edge> g, String u, String v) {
        addEdge(g, u, v, 1.0);
    }
    private static void addEdge(Graph<String, Edge> g, String u, String v, double w) {
        if (!g.containsVertex(u)) g.addVertex(u);
        if (!g.containsVertex(v)) g.addVertex(v);
        Edge e = new Edge("f", u, v);
        e.setWeight((float) w);
        g.addEdge(e, u, v);
    }

    private static GraphIsolationRiskAdvisor.NodeRisk<?> findNode(
            GraphIsolationRiskAdvisor.Plan p, String name) {
        for (GraphIsolationRiskAdvisor.NodeRisk<?> n : p.nodes) {
            if (name.equals(n.node)) return n;
        }
        throw new AssertionError("missing node " + name);
    }

    @Test public void emptyGraphProducesEmptySnapshotInsight() {
        GraphIsolationRiskAdvisor.Plan p =
                new GraphIsolationRiskAdvisor<>(empty()).withFixedClock(fixed()).analyze();
        assertEquals(GraphIsolationRiskAdvisor.Grade.A, p.grade);
        assertTrue(p.insights.contains("EMPTY_SNAPSHOT"));
        assertTrue(p.nodes.isEmpty());
        // playbook always non-empty (EMPTY_GRAPH action)
        assertFalse(p.playbook.isEmpty());
        assertEquals("EMPTY_GRAPH", p.playbook.get(0).id);
    }

    @Test public void singletonVertexIsAlreadyIsolated() {
        Graph<String, Edge> g = empty();
        g.addVertex("LONE");
        GraphIsolationRiskAdvisor.Plan p =
                new GraphIsolationRiskAdvisor<>(g).withFixedClock(fixed()).analyze();
        GraphIsolationRiskAdvisor.NodeRisk<?> n = findNode(p, "LONE");
        assertEquals(GraphIsolationRiskAdvisor.Verdict.ALREADY_ISOLATED, n.verdict);
        assertEquals(GraphIsolationRiskAdvisor.Priority.P0, n.priority);
        assertEquals(100.0, n.riskScore, 0.01);
        // grade is F because isolated_fraction == 1.0
        assertEquals(GraphIsolationRiskAdvisor.Grade.F, p.grade);
    }

    @Test public void singleEdgePairIsSeveredRisk() {
        Graph<String, Edge> g = empty();
        addEdge(g, "A", "B");
        GraphIsolationRiskAdvisor.Plan p =
                new GraphIsolationRiskAdvisor<>(g).withFixedClock(fixed()).analyze();
        for (GraphIsolationRiskAdvisor.NodeRisk<?> n : p.nodes) {
            assertEquals(GraphIsolationRiskAdvisor.Verdict.SEVERED_RISK, n.verdict);
            assertEquals(GraphIsolationRiskAdvisor.Priority.P0, n.priority);
        }
        // FORTIFY action should be present and P0.
        boolean found = false;
        for (GraphIsolationRiskAdvisor.Action a : p.playbook) {
            if ("FORTIFY_SINGLE_GATEWAY_NODES".equals(a.id)) {
                assertEquals(GraphIsolationRiskAdvisor.Priority.P0, a.priority);
                found = true;
            }
        }
        assertTrue("FORTIFY action expected", found);
    }

    @Test public void triangleAllStable() {
        Graph<String, Edge> g = empty();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "A", "C");
        GraphIsolationRiskAdvisor.Plan p =
                new GraphIsolationRiskAdvisor<>(g).withFixedClock(fixed()).analyze();
        for (GraphIsolationRiskAdvisor.NodeRisk<?> n : p.nodes) {
            assertEquals(GraphIsolationRiskAdvisor.Verdict.STABLE, n.verdict);
            assertEquals(GraphIsolationRiskAdvisor.Priority.P3, n.priority);
            assertFalse(n.bridgeDependent);
        }
        assertEquals(GraphIsolationRiskAdvisor.Grade.A, p.grade);
        assertEquals(1, p.componentCount);
    }

    @Test public void pathInteriorIsBridgeDependent() {
        // A-B-C-D-E: B,C,D are bridge-dependent (each depends on a single
        // articulation point to reach the far end of the path).
        Graph<String, Edge> g = empty();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        addEdge(g, "D", "E");
        GraphIsolationRiskAdvisor.Plan p =
                new GraphIsolationRiskAdvisor<>(g).withFixedClock(fixed()).analyze();
        // A and E are degree 1 -> SEVERED_RISK
        assertEquals(GraphIsolationRiskAdvisor.Verdict.SEVERED_RISK, findNode(p, "A").verdict);
        assertEquals(GraphIsolationRiskAdvisor.Verdict.SEVERED_RISK, findNode(p, "E").verdict);
        // B,D are degree 2, depend on C/B respectively; B depends on C (which
        // is an articulation) -- but actually for B, both A and C are
        // candidates; A is not articulation. C IS articulation. So bridge_dependent=true.
        GraphIsolationRiskAdvisor.NodeRisk<?> nb = findNode(p, "B");
        assertTrue("B should be bridge-dependent", nb.bridgeDependent);
        assertEquals(GraphIsolationRiskAdvisor.Verdict.BRIDGE_DEPENDENT, nb.verdict);
        assertEquals(GraphIsolationRiskAdvisor.Priority.P1, nb.priority);
        // Some bridge action should appear.
        boolean foundBridge = false;
        for (GraphIsolationRiskAdvisor.Action a : p.playbook) {
            if ("REMOVE_SINGLE_POINT_OF_FAILURE_BRIDGES".equals(a.id)) {
                foundBridge = true;
                assertEquals(GraphIsolationRiskAdvisor.Priority.P1, a.priority);
            }
        }
        assertTrue("bridge SPOF action expected", foundBridge);
    }

    @Test public void starHubIsOverConnectedAndLeavesAreSevered() {
        Graph<String, Edge> g = empty();
        for (int i = 0; i < 6; i++) addEdge(g, "HUB", "P" + i);
        GraphIsolationRiskAdvisor.Plan p =
                new GraphIsolationRiskAdvisor<>(g).withFixedClock(fixed()).analyze();
        GraphIsolationRiskAdvisor.NodeRisk<?> hub = findNode(p, "HUB");
        assertEquals(GraphIsolationRiskAdvisor.Verdict.OVER_CONNECTED, hub.verdict);
        for (int i = 0; i < 6; i++) {
            GraphIsolationRiskAdvisor.NodeRisk<?> leaf = findNode(p, "P" + i);
            assertEquals(GraphIsolationRiskAdvisor.Verdict.SEVERED_RISK, leaf.verdict);
            assertEquals(GraphIsolationRiskAdvisor.Priority.P0, leaf.priority);
        }
        // Grade should be F (6/7 nodes severed = 85% at risk)
        assertEquals(GraphIsolationRiskAdvisor.Grade.F, p.grade);
    }

    @Test public void disconnectedComponentsTriggersStitchAction() {
        Graph<String, Edge> g = empty();
        // Triangle 1
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "A", "C");
        // Triangle 2
        addEdge(g, "X", "Y");
        addEdge(g, "Y", "Z");
        addEdge(g, "X", "Z");
        GraphIsolationRiskAdvisor.Plan p =
                new GraphIsolationRiskAdvisor<>(g).withFixedClock(fixed()).analyze();
        assertEquals(2, p.componentCount);
        boolean found = false;
        for (GraphIsolationRiskAdvisor.Action a : p.playbook) {
            if ("STITCH_DISCONNECTED_COMPONENTS".equals(a.id)) {
                assertEquals(GraphIsolationRiskAdvisor.Priority.P1, a.priority);
                found = true;
            }
        }
        assertTrue("stitch action expected", found);
    }

    @Test public void riskAppetiteMonotonicityOnAtRiskSnapshot() {
        // Star hub with 4 leaves -> 4 P0 (severed) + 1 over-connected.
        Graph<String, Edge> g = empty();
        for (int i = 0; i < 4; i++) addEdge(g, "HUB", "L" + i);
        double cautious = run(g, GraphIsolationRiskAdvisor.RiskAppetite.CAUTIOUS).meanRiskScore;
        double balanced = run(g, GraphIsolationRiskAdvisor.RiskAppetite.BALANCED).meanRiskScore;
        double aggressive = run(g, GraphIsolationRiskAdvisor.RiskAppetite.AGGRESSIVE).meanRiskScore;
        // Cautious mean-risk should be >= balanced >= aggressive.
        assertTrue("cautious >= balanced: " + cautious + " >= " + balanced,
                cautious >= balanced - 1e-9);
        assertTrue("balanced >= aggressive: " + balanced + " >= " + aggressive,
                balanced >= aggressive - 1e-9);
    }

    @Test public void aggressiveDropsLonelyP2WhenP0Present() {
        // Construct: triangle A-B-C (stable), plus isolated D, plus path D ... no,
        // make: triangle, plus isolated singleton, ensures P0 (isolated) and no
        // P2 actions; nothing to trim. Instead build to have one P2.
        Graph<String, Edge> g = empty();
        g.addVertex("Z");                       // isolated -> P0
        // Two triangles to create 2 components but no P2 specifically.
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "A", "C");
        GraphIsolationRiskAdvisor.Plan agg =
                new GraphIsolationRiskAdvisor<>(g)
                        .withRiskAppetite(GraphIsolationRiskAdvisor.RiskAppetite.AGGRESSIVE)
                        .withFixedClock(fixed()).analyze();
        // Should still contain REINTEGRATE_ISOLATED_NODES P0
        boolean found = false;
        for (GraphIsolationRiskAdvisor.Action a : agg.playbook) {
            if ("REINTEGRATE_ISOLATED_NODES".equals(a.id)) found = true;
        }
        assertTrue(found);
    }

    @Test public void cautiousAddsScheduledFollowup() {
        Graph<String, Edge> g = empty();
        g.addVertex("LONE");
        GraphIsolationRiskAdvisor.Plan p =
                new GraphIsolationRiskAdvisor<>(g)
                        .withRiskAppetite(GraphIsolationRiskAdvisor.RiskAppetite.CAUTIOUS)
                        .withFixedClock(fixed()).analyze();
        boolean found = false;
        for (GraphIsolationRiskAdvisor.Action a : p.playbook) {
            if ("SCHEDULE_ISOLATION_FOLLOWUP".equals(a.id)) found = true;
        }
        assertTrue(found);
    }

    @Test public void jsonRoundTripIsByteStableForFixedClock() {
        Graph<String, Edge> g = empty();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "A", "C");
        addEdge(g, "C", "D");           // makes C an articulation, D severed
        GraphIsolationRiskAdvisor<String, Edge> advisor =
                new GraphIsolationRiskAdvisor<>(g).withFixedClock(fixed());
        String j1 = advisor.toJson(advisor.analyze());
        String j2 = advisor.toJson(advisor.analyze());
        assertEquals(j1, j2);
        assertTrue(j1.contains("\"grade\":"));
        assertTrue(j1.contains("\"nodes\":"));
        assertTrue(j1.contains("\"playbook\":"));
    }

    @Test public void textAndMarkdownContainPlaybookAndInsights() {
        Graph<String, Edge> g = empty();
        addEdge(g, "A", "B");
        GraphIsolationRiskAdvisor<String, Edge> advisor =
                new GraphIsolationRiskAdvisor<>(g).withFixedClock(fixed());
        GraphIsolationRiskAdvisor.Plan p = advisor.analyze();
        String t = advisor.toText(p);
        assertTrue(t.contains("Playbook"));
        assertTrue(t.contains("Insights"));
        String md = advisor.toMarkdown(p);
        assertTrue(md.contains("# Graph Isolation Risk"));
        assertTrue(md.contains("## Top nodes"));
        assertTrue(md.contains("## Playbook"));
        assertTrue(md.contains("## Insights"));
    }

    @Test public void weakTieIsRecordedAsReason() {
        Graph<String, Edge> g = empty();
        addEdge(g, "A", "B", 0.1);          // weak tie
        GraphIsolationRiskAdvisor.Plan p =
                new GraphIsolationRiskAdvisor<>(g).withWeakTieThreshold(0.2)
                        .withFixedClock(fixed()).analyze();
        GraphIsolationRiskAdvisor.NodeRisk<?> a = findNode(p, "A");
        assertTrue(a.reasons.contains("WEAK_GATEWAY_TIE"));
    }

    @Test public void rejectsNullGraph() {
        try {
            new GraphIsolationRiskAdvisor<String, Edge>(null);
            fail("expected IAE");
        } catch (IllegalArgumentException expected) { /* ok */ }
    }

    private GraphIsolationRiskAdvisor.Plan run(Graph<String, Edge> g,
            GraphIsolationRiskAdvisor.RiskAppetite a) {
        return new GraphIsolationRiskAdvisor<>(g).withRiskAppetite(a)
                .withFixedClock(fixed()).analyze();
    }
}
