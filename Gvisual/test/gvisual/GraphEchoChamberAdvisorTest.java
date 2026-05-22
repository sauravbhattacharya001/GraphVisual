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
 * Tests for {@link GraphEchoChamberAdvisor}.
 *
 * @author sauravbhattacharya001
 */
public class GraphEchoChamberAdvisorTest {

    private static Clock fixed() {
        return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    }

    private static Graph<String, Edge> empty() { return new UndirectedSparseGraph<>(); }

    private static void addEdge(Graph<String, Edge> g, String u, String v) {
        if (!g.containsVertex(u)) g.addVertex(u);
        if (!g.containsVertex(v)) g.addVertex(v);
        g.addEdge(new Edge("f", u, v), u, v);
    }

    // ---------- helpers --------------------------------------------------

    private static GraphEchoChamberAdvisor.NodeEcho<?> find(
            GraphEchoChamberAdvisor.Plan plan, String id) {
        for (GraphEchoChamberAdvisor.NodeEcho<?> n : plan.nodes) {
            if (id.equals(Objects.toString(n.node))) return n;
        }
        throw new AssertionError("node not found: " + id);
    }

    private static boolean hasAction(GraphEchoChamberAdvisor.Plan plan, String id) {
        for (GraphEchoChamberAdvisor.Action a : plan.playbook) {
            if (id.equals(a.id)) return true;
        }
        return false;
    }

    // ---------- tests ----------------------------------------------------

    @Test public void emptyGraphProducesGradeAAndEmptyInsight() {
        Graph<String, Edge> g = empty();
        GraphEchoChamberAdvisor.Plan p =
                new GraphEchoChamberAdvisor<>(g).withFixedClock(fixed()).analyze();
        assertEquals(GraphEchoChamberAdvisor.Grade.A, p.grade);
        assertTrue(p.insights.contains("EMPTY_SNAPSHOT"));
        assertTrue(p.nodes.isEmpty());
        assertEquals(0, p.labelCount);
    }

    @Test public void graphWithoutLabelsTreatsEveryNodeUnlabeled() {
        Graph<String, Edge> g = empty();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        GraphEchoChamberAdvisor.Plan p =
                new GraphEchoChamberAdvisor<>(g).withFixedClock(fixed()).analyze();
        assertTrue(p.insights.contains("NO_LABELS_PROVIDED"));
        for (GraphEchoChamberAdvisor.NodeEcho<?> n : p.nodes) {
            assertEquals(GraphEchoChamberAdvisor.Verdict.UNLABELED, n.verdict);
        }
    }

    @Test public void monochromeTriangleIsLeaningNotEchoChamber() {
        Graph<String, Edge> g = empty();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "A", "C");
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("A", "L"); labels.put("B", "L"); labels.put("C", "L");
        GraphEchoChamberAdvisor.Plan p =
                new GraphEchoChamberAdvisor<>(g).withLabels(labels)
                        .withFixedClock(fixed()).analyze();
        for (GraphEchoChamberAdvisor.NodeEcho<?> n : p.nodes) {
            assertEquals("deg=2 should not trigger ECHO_CHAMBER (needs deg>=4)",
                    GraphEchoChamberAdvisor.Verdict.LEANING, n.verdict);
            assertEquals(1.0, n.homophily, 1e-9);
        }
    }

    @Test public void homogeneousStarHubIsExtremeEchoP1Balanced() {
        Graph<String, Edge> g = empty();
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("HUB", "A");
        for (int i = 0; i < 6; i++) {
            addEdge(g, "HUB", "L" + i);
            labels.put("L" + i, "A");
        }
        GraphEchoChamberAdvisor.Plan p =
                new GraphEchoChamberAdvisor<>(g).withLabels(labels)
                        .withFixedClock(fixed()).analyze();
        GraphEchoChamberAdvisor.NodeEcho<?> hub = find(p, "HUB");
        assertEquals(GraphEchoChamberAdvisor.Verdict.EXTREME_ECHO, hub.verdict);
        assertEquals(GraphEchoChamberAdvisor.Priority.P1, hub.priority);
        assertEquals(6, hub.degree);
    }

    @Test public void homogeneousStarHubIsP0UnderCautious() {
        Graph<String, Edge> g = empty();
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("HUB", "A");
        for (int i = 0; i < 6; i++) {
            addEdge(g, "HUB", "L" + i);
            labels.put("L" + i, "A");
        }
        GraphEchoChamberAdvisor.Plan p =
                new GraphEchoChamberAdvisor<>(g).withLabels(labels)
                        .withRiskAppetite(GraphEchoChamberAdvisor.RiskAppetite.CAUTIOUS)
                        .withFixedClock(fixed()).analyze();
        GraphEchoChamberAdvisor.NodeEcho<?> hub = find(p, "HUB");
        assertEquals(GraphEchoChamberAdvisor.Verdict.EXTREME_ECHO, hub.verdict);
        assertEquals(GraphEchoChamberAdvisor.Priority.P0, hub.priority);
    }

    @Test public void crossLabelStarHubIsBridgeNode() {
        Graph<String, Edge> g = empty();
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("HUB", "X");
        String[] cs = {"A","B","C","D","E","F"};
        for (int i = 0; i < cs.length; i++) {
            addEdge(g, "HUB", "L" + i);
            labels.put("L" + i, cs[i]);
        }
        GraphEchoChamberAdvisor.Plan p =
                new GraphEchoChamberAdvisor<>(g).withLabels(labels)
                        .withFixedClock(fixed()).analyze();
        GraphEchoChamberAdvisor.NodeEcho<?> hub = find(p, "HUB");
        assertEquals(GraphEchoChamberAdvisor.Verdict.BRIDGE_NODE, hub.verdict);
        assertEquals(GraphEchoChamberAdvisor.Priority.P1, hub.priority);
        assertTrue(p.bridgeFraction > 0);
    }

    @Test public void mixedNeighbourhoodAtDiversityMidRangeIsMixed() {
        // HUB label A, 10 neighbours: 7 A, 1 B, 1 C, 1 D.
        // homophily = 0.7 (not > 0.70 -> not LEANING from homophily branch)
        // diversity = H/log(4) = 0.941/1.386 ~= 0.679 (in [0.40, 0.70) -> MIXED)
        Graph<String, Edge> g = empty();
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("HUB", "A");
        for (int i = 0; i < 7; i++) { addEdge(g, "HUB", "A" + i); labels.put("A" + i, "A"); }
        addEdge(g, "HUB", "B0"); labels.put("B0", "B");
        addEdge(g, "HUB", "C0"); labels.put("C0", "C");
        addEdge(g, "HUB", "D0"); labels.put("D0", "D");
        GraphEchoChamberAdvisor.Plan p =
                new GraphEchoChamberAdvisor<>(g).withLabels(labels)
                        .withFixedClock(fixed()).analyze();
        GraphEchoChamberAdvisor.NodeEcho<?> hub = find(p, "HUB");
        assertEquals(GraphEchoChamberAdvisor.Verdict.MIXED, hub.verdict);
        assertEquals(0.7, hub.homophily, 1e-6);
    }

    @Test public void globalHomophilyOnK4SplitTwoTwo() {
        Graph<String, Edge> g = empty();
        String[] vs = {"a","b","c","d"};
        for (int i = 0; i < 4; i++)
            for (int j = i + 1; j < 4; j++)
                addEdge(g, vs[i], vs[j]);
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("a","X"); labels.put("b","X");
        labels.put("c","Y"); labels.put("d","Y");
        GraphEchoChamberAdvisor.Plan p =
                new GraphEchoChamberAdvisor<>(g).withLabels(labels)
                        .withFixedClock(fixed()).analyze();
        // 6 edges total, 2 same-label (a-b, c-d) -> 2/6 = 0.333...
        assertEquals(0.333, p.globalHomophily, 0.01);
    }

    @Test public void dominantLabelAndShareComputedCorrectly() {
        Graph<String, Edge> g = empty();
        Map<String, String> labels = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) {
            addEdge(g, "x" + i, "y" + i);
            labels.put("x" + i, "A");
            labels.put("y" + i, (i < 4) ? "B" : "A");
        }
        // 6 A's (x0..x5) + 2 A's (y4,y5) = 8 A's, 4 B's (y0..y3). total = 12.
        // dominant = A, share = 8/12 ~ 0.667.
        GraphEchoChamberAdvisor.Plan p =
                new GraphEchoChamberAdvisor<>(g).withLabels(labels)
                        .withFixedClock(fixed()).analyze();
        assertEquals("A", p.dominantLabel);
        assertEquals(0.667, p.dominantLabelShare, 0.01);
        assertEquals(2, p.labelCount);
    }

    @Test public void playbookHasBreakExtremeEchoChambersWhenExtremePresent() {
        Graph<String, Edge> g = empty();
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("HUB", "A");
        for (int i = 0; i < 6; i++) {
            addEdge(g, "HUB", "L" + i);
            labels.put("L" + i, "A");
        }
        GraphEchoChamberAdvisor.Plan p =
                new GraphEchoChamberAdvisor<>(g).withLabels(labels)
                        .withFixedClock(fixed()).analyze();
        assertTrue("expected BREAK_EXTREME_ECHO_CHAMBERS in playbook",
                hasAction(p, "BREAK_EXTREME_ECHO_CHAMBERS"));
    }

    @Test public void playbookHasEmpowerBridgeNodesWhenBridgePresent() {
        Graph<String, Edge> g = empty();
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("HUB", "X");
        String[] cs = {"A","B","C","D","E","F"};
        for (int i = 0; i < cs.length; i++) {
            addEdge(g, "HUB", "L" + i);
            labels.put("L" + i, cs[i]);
        }
        GraphEchoChamberAdvisor.Plan p =
                new GraphEchoChamberAdvisor<>(g).withLabels(labels)
                        .withFixedClock(fixed()).analyze();
        assertTrue("expected EMPOWER_BRIDGE_NODES in playbook",
                hasAction(p, "EMPOWER_BRIDGE_NODES"));
    }

    @Test public void cautiousAddsScheduleEchoAuditAtBadGrade() {
        Graph<String, Edge> g = empty();
        Map<String, String> labels = new LinkedHashMap<>();
        // Make echoChamberFraction >= 0.50 to force grade F.
        labels.put("HUB", "A");
        for (int i = 0; i < 6; i++) {
            addEdge(g, "HUB", "L" + i);
            labels.put("L" + i, "A");
        }
        // Add a second extreme hub.
        labels.put("HUB2", "B");
        for (int i = 0; i < 6; i++) {
            addEdge(g, "HUB2", "M" + i);
            labels.put("M" + i, "B");
        }
        GraphEchoChamberAdvisor.Plan p =
                new GraphEchoChamberAdvisor<>(g).withLabels(labels)
                        .withRiskAppetite(GraphEchoChamberAdvisor.RiskAppetite.CAUTIOUS)
                        .withFixedClock(fixed()).analyze();
        assertTrue("expected SCHEDULE_ECHO_AUDIT under cautious + bad grade",
                hasAction(p, "SCHEDULE_ECHO_AUDIT"));
    }

    @Test public void aggressiveOmitsMaintainDiverseNetworkWhenUrgentPresent() {
        Graph<String, Edge> g = empty();
        Map<String, String> labels = new LinkedHashMap<>();
        // Create a BRIDGE_NODE -> P1 EMPOWER_BRIDGE_NODES.
        labels.put("HUB", "X");
        String[] cs = {"A","B","C","D","E","F"};
        for (int i = 0; i < cs.length; i++) {
            addEdge(g, "HUB", "L" + i);
            labels.put("L" + i, cs[i]);
        }
        GraphEchoChamberAdvisor.Plan p =
                new GraphEchoChamberAdvisor<>(g).withLabels(labels)
                        .withRiskAppetite(GraphEchoChamberAdvisor.RiskAppetite.AGGRESSIVE)
                        .withFixedClock(fixed()).analyze();
        assertFalse("MAINTAIN_DIVERSE_NETWORK should not appear when P0/P1 present",
                hasAction(p, "MAINTAIN_DIVERSE_NETWORK"));
    }

    @Test public void isolatedDegreeZeroVertexClassifiedIsolated() {
        Graph<String, Edge> g = empty();
        g.addVertex("LONE");
        addEdge(g, "A", "B");
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("LONE", "X");
        labels.put("A", "X");
        labels.put("B", "X");
        GraphEchoChamberAdvisor.Plan p =
                new GraphEchoChamberAdvisor<>(g).withLabels(labels)
                        .withFixedClock(fixed()).analyze();
        GraphEchoChamberAdvisor.NodeEcho<?> lone = find(p, "LONE");
        assertEquals(GraphEchoChamberAdvisor.Verdict.ISOLATED, lone.verdict);
        assertEquals(0, lone.degree);
    }

    @Test public void unlabeledVertexWithEdgesClassifiedUnlabeled() {
        Graph<String, Edge> g = empty();
        addEdge(g, "A", "B");
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("A", "X"); // B has no label
        GraphEchoChamberAdvisor.Plan p =
                new GraphEchoChamberAdvisor<>(g).withLabels(labels)
                        .withFixedClock(fixed()).analyze();
        GraphEchoChamberAdvisor.NodeEcho<?> b = find(p, "B");
        assertEquals(GraphEchoChamberAdvisor.Verdict.UNLABELED, b.verdict);
    }

    @Test public void jsonOutputIsByteStableAcrossRuns() {
        Graph<String, Edge> g = empty();
        addEdge(g, "A", "B");
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("A", "L"); labels.put("B", "L");
        GraphEchoChamberAdvisor<String, Edge> adv =
                new GraphEchoChamberAdvisor<>(g).withLabels(labels).withFixedClock(fixed());
        GraphEchoChamberAdvisor.Plan p1 = adv.analyze();
        GraphEchoChamberAdvisor.Plan p2 = adv.analyze();
        String j1 = adv.toJson(p1);
        String j2 = adv.toJson(p2);
        assertEquals(j1, j2);
        // Confirm keys are sorted at top level: bridge_fraction precedes echo_chamber_fraction.
        int iBridge = j1.indexOf("\"bridge_fraction\"");
        int iEcho   = j1.indexOf("\"echo_chamber_fraction\"");
        assertTrue(iBridge >= 0 && iEcho > iBridge);
    }

    @Test public void analyzeIsIdempotent() {
        Graph<String, Edge> g = empty();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("A","X"); labels.put("B","Y"); labels.put("C","X"); labels.put("D","Y");
        GraphEchoChamberAdvisor<String, Edge> adv =
                new GraphEchoChamberAdvisor<>(g).withLabels(labels).withFixedClock(fixed());
        GraphEchoChamberAdvisor.Plan p1 = adv.analyze();
        GraphEchoChamberAdvisor.Plan p2 = adv.analyze();
        assertEquals(p1.headline, p2.headline);
        assertEquals(p1.nodes.size(), p2.nodes.size());
        for (int i = 0; i < p1.nodes.size(); i++) {
            assertEquals(p1.nodes.get(i).verdict, p2.nodes.get(i).verdict);
            assertEquals(p1.nodes.get(i).node, p2.nodes.get(i).node);
        }
    }

    @Test public void textAndMarkdownRenderersProduceExpectedSections() {
        Graph<String, Edge> g = empty();
        addEdge(g, "A", "B");
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("A","X"); labels.put("B","Y");
        GraphEchoChamberAdvisor<String, Edge> adv =
                new GraphEchoChamberAdvisor<>(g).withLabels(labels).withFixedClock(fixed());
        GraphEchoChamberAdvisor.Plan p = adv.analyze();
        String text = adv.toText(p);
        String md   = adv.toMarkdown(p);
        assertTrue(text.length() > 0);
        assertTrue(md.contains("## Summary"));
        assertTrue(md.contains("## Nodes"));
        assertTrue(md.contains("## Playbook"));
        assertTrue(md.contains("## Insights"));
    }

    @Test public void inputGraphAndLabelsAreNotMutated() {
        Graph<String, Edge> g = empty();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("A","X"); labels.put("B","Y"); labels.put("C","X");

        Set<String> verticesBefore = new LinkedHashSet<>(g.getVertices());
        int edgesBefore = g.getEdgeCount();
        Map<String, String> labelsBefore = new LinkedHashMap<>(labels);

        new GraphEchoChamberAdvisor<>(g).withLabels(labels)
                .withFixedClock(fixed()).analyze();

        assertEquals(verticesBefore, new LinkedHashSet<>(g.getVertices()));
        assertEquals(edgesBefore, g.getEdgeCount());
        assertEquals(labelsBefore, labels);
    }
}
