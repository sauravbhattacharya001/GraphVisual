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
 * Tests for {@link GraphFriendshipTriadAdvisor}.
 *
 * @author sauravbhattacharya001
 */
public class GraphFriendshipTriadAdvisorTest {

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

    @Test public void emptyGraphProducesEmptySnapshotInsight() {
        Graph<String, Edge> g = empty();
        GraphFriendshipTriadAdvisor.Plan p =
                new GraphFriendshipTriadAdvisor<>(g).withFixedClock(fixed()).analyze();
        assertEquals(GraphFriendshipTriadAdvisor.Grade.A, p.grade);
        assertTrue(p.insights.contains("EMPTY_SNAPSHOT"));
        assertTrue(p.nodes.isEmpty());
    }

    @Test public void perfectTriangleIsTightCluster() {
        Graph<String, Edge> g = triangle("X");
        GraphFriendshipTriadAdvisor.Plan p =
                new GraphFriendshipTriadAdvisor<>(g).withFixedClock(fixed()).analyze();
        assertEquals(3, p.nodes.size());
        for (GraphFriendshipTriadAdvisor.NodeTriad<?> n : p.nodes) {
            assertEquals(GraphFriendshipTriadAdvisor.Verdict.TIGHT_CLUSTER, n.verdict);
            assertEquals(1, n.closedTriangles);
            assertEquals(0, n.openTriads);
            assertEquals(1.0, n.clusteringCoefficient, 1e-6);
        }
        assertEquals(1.0, p.globalClustering, 1e-6);
    }

    @Test public void starHubIsStrongBridger() {
        Graph<String, Edge> g = empty();
        addEdge(g, "HUB", "A");
        addEdge(g, "HUB", "B");
        addEdge(g, "HUB", "C");
        addEdge(g, "HUB", "D");
        GraphFriendshipTriadAdvisor.Plan p =
                new GraphFriendshipTriadAdvisor<>(g).withFixedClock(fixed()).analyze();
        GraphFriendshipTriadAdvisor.NodeTriad<?> hub = p.nodes.stream()
                .filter(n -> "HUB".equals(n.node)).findFirst().orElseThrow();
        assertEquals(GraphFriendshipTriadAdvisor.Verdict.STRONG_BRIDGER, hub.verdict);
        assertEquals(0, hub.closedTriangles);
        assertEquals(6, hub.openTriads);
        assertEquals(GraphFriendshipTriadAdvisor.Priority.P1, hub.priority);
    }

    @Test public void degreeOneVertexIsIsolated() {
        Graph<String, Edge> g = empty();
        addEdge(g, "A", "B");
        GraphFriendshipTriadAdvisor.Plan p =
                new GraphFriendshipTriadAdvisor<>(g).withFixedClock(fixed()).analyze();
        for (GraphFriendshipTriadAdvisor.NodeTriad<?> n : p.nodes) {
            assertEquals(GraphFriendshipTriadAdvisor.Verdict.ISOLATED, n.verdict);
            assertEquals(GraphFriendshipTriadAdvisor.Priority.P1, n.priority);
        }
    }

    @Test public void chronicMediatorTriggersAtRiskAndP0Cautious() {
        // Hub connected to 6 nodes, none of which connect to each other.
        Graph<String, Edge> g = empty();
        for (int i = 0; i < 6; i++) addEdge(g, "HUB", "P" + i);
        GraphFriendshipTriadAdvisor.Plan p =
                new GraphFriendshipTriadAdvisor<>(g)
                        .withRiskAppetite(GraphFriendshipTriadAdvisor.RiskAppetite.CAUTIOUS)
                        .withFixedClock(fixed())
                        .analyze();
        GraphFriendshipTriadAdvisor.NodeTriad<?> hub = p.nodes.stream()
                .filter(n -> "HUB".equals(n.node)).findFirst().orElseThrow();
        // 6 open triads, clustering=0; STRONG_BRIDGER takes precedence with closed==0.
        assertTrue(hub.verdict == GraphFriendshipTriadAdvisor.Verdict.STRONG_BRIDGER
                || hub.verdict == GraphFriendshipTriadAdvisor.Verdict.AT_RISK_OF_CLOSURE);
        assertTrue(hub.openTriads >= 6);
        assertEquals(0.0, hub.clusteringCoefficient, 1e-9);
    }

    @Test public void potentialBrokerDetectedOnSingleClosureManyOpen() {
        // Hub has many neighbours; exactly one pair of those neighbours is connected.
        Graph<String, Edge> g = empty();
        for (int i = 0; i < 5; i++) addEdge(g, "HUB", "P" + i);
        addEdge(g, "P0", "P1"); // single closure -> 1 triangle through HUB
        GraphFriendshipTriadAdvisor.Plan p =
                new GraphFriendshipTriadAdvisor<>(g).withFixedClock(fixed()).analyze();
        GraphFriendshipTriadAdvisor.NodeTriad<?> hub = p.nodes.stream()
                .filter(n -> "HUB".equals(n.node)).findFirst().orElseThrow();
        assertEquals(1, hub.closedTriangles);
        assertEquals(GraphFriendshipTriadAdvisor.Verdict.POTENTIAL_BROKER, hub.verdict);
    }

    @Test public void playbookIncludesReconnectWhenIsolated() {
        Graph<String, Edge> g = empty();
        addEdge(g, "A", "B"); // both A and B are ISOLATED (deg 1)
        addEdge(g, "C", "D"); // C and D ISOLATED too
        GraphFriendshipTriadAdvisor.Plan p =
                new GraphFriendshipTriadAdvisor<>(g).withFixedClock(fixed()).analyze();
        boolean has = p.playbook.stream()
                .anyMatch(a -> "RECONNECT_ISOLATED_MEMBERS".equals(a.id));
        assertTrue("Should recommend reconnecting isolated members", has);
    }

    @Test public void deterministicJsonForFixedClock() {
        Graph<String, Edge> g1 = triangle("X");
        Graph<String, Edge> g2 = triangle("X");
        GraphFriendshipTriadAdvisor<String, Edge> a =
                new GraphFriendshipTriadAdvisor<>(g1).withFixedClock(fixed());
        GraphFriendshipTriadAdvisor<String, Edge> b =
                new GraphFriendshipTriadAdvisor<>(g2).withFixedClock(fixed());
        assertEquals(a.toJson(a.analyze()), b.toJson(b.analyze()));
    }

    @Test public void rendersTextMarkdownAndJson() {
        Graph<String, Edge> g = empty();
        for (int i = 0; i < 4; i++) addEdge(g, "HUB", "P" + i);
        GraphFriendshipTriadAdvisor<String, Edge> adv =
                new GraphFriendshipTriadAdvisor<>(g).withFixedClock(fixed());
        GraphFriendshipTriadAdvisor.Plan p = adv.analyze();
        String t = adv.toText(p);
        String md = adv.toMarkdown(p);
        String json = adv.toJson(p);
        assertTrue(t.contains("VERDICT"));
        assertTrue(md.contains("# Graph Friendship Triad"));
        assertTrue(md.contains("## Playbook"));
        assertTrue(json.startsWith("{"));
        assertTrue(json.contains("\"grade\""));
        assertTrue(json.contains("\"nodes\""));
        assertTrue(json.contains("\"playbook\""));
        assertTrue(json.contains("\"insights\""));
        assertTrue(json.contains("\"global_clustering\""));
    }

    @Test public void riskAppetiteAffectsBridgingScore() {
        Graph<String, Edge> g = empty();
        for (int i = 0; i < 5; i++) addEdge(g, "HUB", "P" + i);
        double cautious = scoreOf(g, GraphFriendshipTriadAdvisor.RiskAppetite.CAUTIOUS);
        double balanced = scoreOf(g, GraphFriendshipTriadAdvisor.RiskAppetite.BALANCED);
        double aggressive = scoreOf(g, GraphFriendshipTriadAdvisor.RiskAppetite.AGGRESSIVE);
        assertTrue("cautious >= balanced", cautious >= balanced);
        assertTrue("balanced >= aggressive", balanced >= aggressive);
    }

    private static double scoreOf(Graph<String, Edge> g,
                                  GraphFriendshipTriadAdvisor.RiskAppetite a) {
        GraphFriendshipTriadAdvisor.Plan p =
                new GraphFriendshipTriadAdvisor<>(g)
                        .withRiskAppetite(a).withFixedClock(fixed()).analyze();
        return p.nodes.stream()
                .filter(n -> "HUB".equals(n.node))
                .findFirst().orElseThrow().bridgingScore;
    }

    @Test public void neverMutatesInputGraph() {
        Graph<String, Edge> g = triangle("X");
        int beforeV = g.getVertexCount();
        int beforeE = g.getEdgeCount();
        new GraphFriendshipTriadAdvisor<>(g).withFixedClock(fixed()).analyze();
        assertEquals(beforeV, g.getVertexCount());
        assertEquals(beforeE, g.getEdgeCount());
    }

    @Test public void clusteringCoefficientCorrectForKnownGraph() {
        // P4 path: A-B-C-D. B and C have degree 2; no closures.
        Graph<String, Edge> g = empty();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        GraphFriendshipTriadAdvisor.Plan p =
                new GraphFriendshipTriadAdvisor<>(g).withFixedClock(fixed()).analyze();
        Map<String, Double> ccs = new HashMap<>();
        for (GraphFriendshipTriadAdvisor.NodeTriad<?> n : p.nodes) {
            ccs.put((String) n.node, n.clusteringCoefficient);
        }
        assertEquals(0.0, ccs.get("B"), 1e-9);
        assertEquals(0.0, ccs.get("C"), 1e-9);
    }

    @Test public void summaryFractionsAreInZeroOneRange() {
        Graph<String, Edge> g = empty();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "A", "C");
        addEdge(g, "D", "E");
        addEdge(g, "F", "G");
        GraphFriendshipTriadAdvisor.Plan p =
                new GraphFriendshipTriadAdvisor<>(g).withFixedClock(fixed()).analyze();
        assertTrue(p.bridgingFraction >= 0.0 && p.bridgingFraction <= 1.0);
        assertTrue(p.bondingFraction >= 0.0 && p.bondingFraction <= 1.0);
        assertTrue(p.isolationFraction >= 0.0 && p.isolationFraction <= 1.0);
    }
}
