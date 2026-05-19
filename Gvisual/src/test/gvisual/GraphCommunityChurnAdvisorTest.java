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
 * Tests for {@link GraphCommunityChurnAdvisor}.
 *
 * @author sauravbhattacharya001
 */
public class GraphCommunityChurnAdvisorTest {

    private static Clock fixed() {
        return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    }

    private static Graph<String, Edge> empty() { return new UndirectedSparseGraph<>(); }

    private static void addEdge(Graph<String, Edge> g, String u, String v) {
        if (!g.containsVertex(u)) g.addVertex(u);
        if (!g.containsVertex(v)) g.addVertex(v);
        g.addEdge(new Edge("f", u, v), u, v);
    }

    private static Graph<String, Edge> triangle(String prefix) {
        Graph<String, Edge> g = empty();
        addEdge(g, prefix + "A", prefix + "B");
        addEdge(g, prefix + "B", prefix + "C");
        addEdge(g, prefix + "A", prefix + "C");
        return g;
    }

    @Test public void identicalGraphsAreAllStableGradeA() {
        Graph<String, Edge> g1 = triangle("X");
        Graph<String, Edge> g2 = triangle("X");
        GraphCommunityChurnAdvisor.Plan p =
                new GraphCommunityChurnAdvisor<>(g1, g2)
                        .withFixedClock(fixed())
                        .analyze();
        assertEquals(GraphCommunityChurnAdvisor.Grade.A, p.grade);
        assertEquals(3, p.retainedNodes);
        assertEquals(0, p.newArrivals);
        assertEquals(0, p.departures);
        assertTrue(p.portfolioChurnScore < 30.0);
        for (GraphCommunityChurnAdvisor.NodeChurn<?> n : p.nodes) {
            assertEquals(GraphCommunityChurnAdvisor.Verdict.STABLE, n.verdict);
        }
    }

    @Test public void newArrivalsAndDeparturesAreClassified() {
        Graph<String, Edge> g1 = triangle("X");
        Graph<String, Edge> g2 = empty();
        addEdge(g2, "XA", "XB");
        addEdge(g2, "XB", "XC");
        addEdge(g2, "XA", "XC");
        addEdge(g2, "XA", "NEW1");
        g2.addVertex("NEW2");
        GraphCommunityChurnAdvisor.Plan p =
                new GraphCommunityChurnAdvisor<>(g1, g2).withFixedClock(fixed()).analyze();
        assertEquals(2, p.newArrivals);
        Map<String, GraphCommunityChurnAdvisor.Verdict> byNode = new HashMap<>();
        for (GraphCommunityChurnAdvisor.NodeChurn<?> n : p.nodes) {
            byNode.put((String) n.node, n.verdict);
        }
        assertEquals(GraphCommunityChurnAdvisor.Verdict.NEW_ARRIVAL, byNode.get("NEW1"));
        assertEquals(GraphCommunityChurnAdvisor.Verdict.NEW_ARRIVAL, byNode.get("NEW2"));
    }

    @Test public void departedNodeIsClassified() {
        Graph<String, Edge> g1 = empty();
        addEdge(g1, "A", "B");
        addEdge(g1, "B", "C");
        addEdge(g1, "C", "D");
        addEdge(g1, "D", "E");
        addEdge(g1, "E", "A");
        Graph<String, Edge> g2 = empty();
        addEdge(g2, "B", "C");
        addEdge(g2, "C", "D");
        addEdge(g2, "D", "E");
        GraphCommunityChurnAdvisor.Plan p =
                new GraphCommunityChurnAdvisor<>(g1, g2).withFixedClock(fixed()).analyze();
        assertEquals(1, p.departures);
        GraphCommunityChurnAdvisor.NodeChurn<?> aNode = p.nodes.stream()
                .filter(n -> "A".equals(n.node)).findFirst().orElseThrow();
        assertEquals(GraphCommunityChurnAdvisor.Verdict.DEPARTED, aNode.verdict);
    }

    @Test public void isolatingNodeProducesP0() {
        Graph<String, Edge> g1 = empty();
        addEdge(g1, "A", "B");
        addEdge(g1, "A", "C");
        addEdge(g1, "A", "D");
        addEdge(g1, "A", "E");
        addEdge(g1, "A", "F");
        Graph<String, Edge> g2 = empty();
        g2.addVertex("A");
        g2.addVertex("B"); g2.addVertex("C"); g2.addVertex("D");
        g2.addVertex("E"); g2.addVertex("F");
        addEdge(g2, "B", "C"); addEdge(g2, "D", "E");
        GraphCommunityChurnAdvisor.Plan p =
                new GraphCommunityChurnAdvisor<>(g1, g2).withFixedClock(fixed()).analyze();
        GraphCommunityChurnAdvisor.NodeChurn<?> aNode = p.nodes.stream()
                .filter(n -> "A".equals(n.node)).findFirst().orElseThrow();
        assertEquals(GraphCommunityChurnAdvisor.Verdict.ISOLATING, aNode.verdict);
        assertEquals(GraphCommunityChurnAdvisor.Priority.P0, aNode.priority);
    }

    @Test public void migrantDetectedWhenCommunityChanges() {
        Graph<String, Edge> g1 = empty();
        addEdge(g1, "A", "B"); addEdge(g1, "B", "C"); addEdge(g1, "A", "C");
        addEdge(g1, "D", "E"); addEdge(g1, "E", "F"); addEdge(g1, "D", "F");
        Graph<String, Edge> g2 = empty();
        addEdge(g2, "A", "B"); addEdge(g2, "B", "C");
        addEdge(g2, "D", "E"); addEdge(g2, "E", "F"); addEdge(g2, "D", "F");
        Map<String, Object> bc = new HashMap<>();
        bc.put("A", "X"); bc.put("B", "X"); bc.put("C", "X");
        bc.put("D", "Y"); bc.put("E", "Y"); bc.put("F", "Y");
        Map<String, Object> cc = new HashMap<>();
        cc.put("A", "Y"); cc.put("B", "X"); cc.put("C", "X");
        cc.put("D", "Y"); cc.put("E", "Y"); cc.put("F", "Y");
        GraphCommunityChurnAdvisor.Plan p =
                new GraphCommunityChurnAdvisor<>(g1, g2)
                        .withCommunities(bc, cc)
                        .withFixedClock(fixed())
                        .analyze();
        GraphCommunityChurnAdvisor.NodeChurn<?> aNode = p.nodes.stream()
                .filter(n -> "A".equals(n.node)).findFirst().orElseThrow();
        assertEquals(GraphCommunityChurnAdvisor.Verdict.MIGRANT, aNode.verdict);
    }

    @Test public void dissolvedCommunityForcesGradeDOrWorseAndPlaybook() {
        Graph<String, Edge> g1 = triangle("X");
        Graph<String, Edge> g2 = empty();
        addEdge(g2, "YA", "YB");
        GraphCommunityChurnAdvisor.Plan p =
                new GraphCommunityChurnAdvisor<>(g1, g2).withFixedClock(fixed()).analyze();
        assertTrue("Expected at least one DISSOLVED",
                p.communities.stream().anyMatch(e -> "DISSOLVED".equals(e.pattern)));
        boolean hasInvestigate = p.playbook.stream()
                .anyMatch(a -> "INVESTIGATE_DISSOLVED_GROUPS".equals(a.id));
        assertTrue("Playbook should include INVESTIGATE_DISSOLVED_GROUPS", hasInvestigate);
        assertTrue("Grade should be D or F",
                p.grade == GraphCommunityChurnAdvisor.Grade.D
                        || p.grade == GraphCommunityChurnAdvisor.Grade.F);
    }

    @Test public void riskAppetiteIsMonotonic() {
        Graph<String, Edge> g1 = empty();
        for (int i = 0; i < 6; i++) addEdge(g1, "A" + i, "A" + ((i + 1) % 6));
        Graph<String, Edge> g2 = empty();
        for (int i = 0; i < 6; i++) g2.addVertex("A" + i);
        addEdge(g2, "A0", "A1"); addEdge(g2, "A3", "A4");
        double cautious = new GraphCommunityChurnAdvisor<>(g1, g2)
                .withRiskAppetite(GraphCommunityChurnAdvisor.RiskAppetite.CAUTIOUS)
                .withFixedClock(fixed()).analyze().portfolioChurnScore;
        double balanced = new GraphCommunityChurnAdvisor<>(g1, g2)
                .withRiskAppetite(GraphCommunityChurnAdvisor.RiskAppetite.BALANCED)
                .withFixedClock(fixed()).analyze().portfolioChurnScore;
        double aggressive = new GraphCommunityChurnAdvisor<>(g1, g2)
                .withRiskAppetite(GraphCommunityChurnAdvisor.RiskAppetite.AGGRESSIVE)
                .withFixedClock(fixed()).analyze().portfolioChurnScore;
        assertTrue("cautious >= balanced", cautious >= balanced);
        assertTrue("balanced >= aggressive", balanced >= aggressive);
    }

    @Test public void rendersTextMarkdownAndJson() {
        Graph<String, Edge> g1 = triangle("X");
        Graph<String, Edge> g2 = empty();
        addEdge(g2, "XA", "XB");
        addEdge(g2, "XA", "NEW1");
        GraphCommunityChurnAdvisor<String, Edge> adv =
                new GraphCommunityChurnAdvisor<>(g1, g2).withFixedClock(fixed());
        GraphCommunityChurnAdvisor.Plan p = adv.analyze();
        String text = adv.toText(p);
        String md = adv.toMarkdown(p);
        String json = adv.toJson(p);
        assertTrue(text.contains("VERDICT"));
        assertTrue(md.contains("# Graph Community Churn"));
        assertTrue(md.contains("## Playbook"));
        assertTrue(json.startsWith("{"));
        assertTrue(json.contains("\"grade\""));
        assertTrue(json.contains("\"nodes\""));
        assertTrue(json.contains("\"playbook\""));
        assertTrue(json.contains("\"insights\""));
    }

    @Test public void deterministicJsonForFixedClock() {
        Graph<String, Edge> g1 = triangle("X");
        Graph<String, Edge> g2 = triangle("X");
        GraphCommunityChurnAdvisor<String, Edge> a =
                new GraphCommunityChurnAdvisor<>(g1, g2).withFixedClock(fixed());
        GraphCommunityChurnAdvisor<String, Edge> b =
                new GraphCommunityChurnAdvisor<>(g1, g2).withFixedClock(fixed());
        assertEquals(a.toJson(a.analyze()), b.toJson(b.analyze()));
    }

    @Test public void emptyGraphsProduceEmptySnapshotInsight() {
        Graph<String, Edge> g1 = empty();
        Graph<String, Edge> g2 = empty();
        GraphCommunityChurnAdvisor.Plan p =
                new GraphCommunityChurnAdvisor<>(g1, g2).withFixedClock(fixed()).analyze();
        assertEquals(GraphCommunityChurnAdvisor.Grade.A, p.grade);
        assertTrue(p.insights.contains("EMPTY_SNAPSHOT"));
    }
}
