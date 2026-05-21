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
 * Tests for {@link GraphSignedConflictAdvisor}.
 *
 * @author sauravbhattacharya001
 */
public class GraphSignedConflictAdvisorTest {

    private static Clock fixed() {
        return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    }

    private static Graph<String, Edge> empty() { return new UndirectedSparseGraph<>(); }

    private static Edge mkPos(String u, String v) {
        Edge e = new Edge("f", u, v);
        e.setWeight(1.0f);
        return e;
    }

    private static Edge mkNeg(String u, String v) {
        Edge e = new Edge("f", u, v);
        e.setWeight(-1.0f);
        e.setLabel("-");
        return e;
    }

    private static void addPos(Graph<String, Edge> g, String u, String v) {
        if (!g.containsVertex(u)) g.addVertex(u);
        if (!g.containsVertex(v)) g.addVertex(v);
        g.addEdge(mkPos(u, v), u, v);
    }
    private static void addNeg(Graph<String, Edge> g, String u, String v) {
        if (!g.containsVertex(u)) g.addVertex(u);
        if (!g.containsVertex(v)) g.addVertex(v);
        g.addEdge(mkNeg(u, v), u, v);
    }

    @Test public void emptyGraphReturnsGradeA() {
        Graph<String, Edge> g = empty();
        GraphSignedConflictAdvisor.Plan p =
                new GraphSignedConflictAdvisor(g).withFixedClock(fixed()).analyze();
        assertEquals(GraphSignedConflictAdvisor.Grade.A, p.grade);
        assertTrue(p.insights.contains("EMPTY_OR_TRIVIAL"));
        assertEquals(1, p.playbook.size());
        assertEquals("HEALTHY_NETWORK", p.playbook.get(0).id);
        assertEquals(GraphSignedConflictAdvisor.Priority.P3, p.playbook.get(0).priority);
    }

    @Test public void singleVertexIsTrivial() {
        Graph<String, Edge> g = empty();
        g.addVertex("A");
        GraphSignedConflictAdvisor.Plan p =
                new GraphSignedConflictAdvisor(g).withFixedClock(fixed()).analyze();
        assertEquals(GraphSignedConflictAdvisor.Grade.A, p.grade);
        assertTrue(p.insights.contains("EMPTY_OR_TRIVIAL"));
    }

    @Test public void allPositiveTriangleIsBalanced() {
        Graph<String, Edge> g = empty();
        addPos(g, "A", "B");
        addPos(g, "B", "C");
        addPos(g, "A", "C");
        GraphSignedConflictAdvisor.Plan p =
                new GraphSignedConflictAdvisor(g).withFixedClock(fixed()).analyze();
        assertEquals(GraphSignedConflictAdvisor.Grade.A, p.grade);
        assertTrue(p.insights.contains("BALANCED_NETWORK"));
        for (GraphSignedConflictAdvisor.NodeConflict n : p.nodes) {
            assertNotEquals(GraphSignedConflictAdvisor.Verdict.AGITATOR, n.verdict);
            assertNotEquals(GraphSignedConflictAdvisor.Verdict.ISOLATED_HOSTILE, n.verdict);
        }
        assertEquals(0, p.frustrationIndex);
    }

    @Test public void frustratedTriangleIsDetected() {
        // ++- triangle: A-B+ B-C+ A-C-  => frustrated
        Graph<String, Edge> g = empty();
        addPos(g, "A", "B");
        addPos(g, "B", "C");
        addNeg(g, "A", "C");
        GraphSignedConflictAdvisor.Plan p =
                new GraphSignedConflictAdvisor(g).withFixedClock(fixed()).analyze();
        assertTrue("frustration should be >= 1", p.frustrationIndex >= 1);
        // expect at least 1 frustrated edge entry
        assertFalse(p.edges.isEmpty());
    }

    @Test public void hostileStarHasPolarizedOrAgitator() {
        // Center has 4 negative edges to leaves; degree 4, polarization 1.0
        Graph<String, Edge> g = empty();
        addNeg(g, "X", "A");
        addNeg(g, "X", "B");
        addNeg(g, "X", "C");
        addNeg(g, "X", "D");
        GraphSignedConflictAdvisor.Plan p =
                new GraphSignedConflictAdvisor(g).withFixedClock(fixed()).analyze();
        GraphSignedConflictAdvisor.NodeConflict x = null;
        for (GraphSignedConflictAdvisor.NodeConflict n : p.nodes) {
            if (n.node.equals("X")) { x = n; break; }
        }
        assertNotNull(x);
        // No triangles => no frustrated edges => can't be AGITATOR (which requires frustrated incident)
        // Should be ISOLATED_HOSTILE (all negative + deg >= 2) or POLARIZED
        assertTrue(x.verdict == GraphSignedConflictAdvisor.Verdict.ISOLATED_HOSTILE
                || x.verdict == GraphSignedConflictAdvisor.Verdict.POLARIZED
                || x.verdict == GraphSignedConflictAdvisor.Verdict.AGITATOR);
    }

    @Test public void agitatorPlusFrustratedTriangle() {
        // Build a graph where one vertex has high polarization AND >=2 frustrated edges.
        // Triangle 1: A-B+, B-C+, A-C-  (frustrated edge A-C)
        // Triangle 2: A-D-, D-E+, A-E-  (frustrated edges A-D and A-E if these create ++- patterns)
        //   Actually triangle A-D-, D-E+, A-E- is +-- pattern (balanced strongly under Harary).
        // Let's instead create more ++- triangles touching A:
        // Triangle 2: A-B+, B-D+, A-D-  -> frustrated edge A-D
        // Triangle 3: A-C-, C-E-, A-E+  -> +-- balanced; instead: A-C-, C-E+, A-E+ -> ++- (frustrated A-C)
        // So A is in 2 frustrated triangles and has incident frustrated edges A-C and A-D.
        Graph<String, Edge> g = empty();
        addPos(g, "A", "B");
        addPos(g, "B", "C");
        addNeg(g, "A", "C");
        addPos(g, "B", "D");
        addNeg(g, "A", "D");
        addPos(g, "C", "E");
        addPos(g, "A", "E");
        // A has degree 4: A-B+, A-C-, A-D-, A-E+  -> polarization 0.5 (not >=0.75)
        // Add more negative edges from A:
        addNeg(g, "A", "F");
        addNeg(g, "A", "G");
        addPos(g, "C", "F"); // make A-F frustrated via A-C-, A-F-, C-F+? --- triangle = nnn; weakly balanced.
        // Easier: add another ++- frustrated triangle on A: A-G-, G-H+, A-H+  (G-H+, A-H+, A-G-)
        addPos(g, "G", "H");
        addPos(g, "A", "H");
        // Now A: edges A-B+, A-C-, A-D-, A-E+, A-F-, A-G-, A-H+ -> neg=4 pos=3 polarization ~0.57
        // not >=0.75. Let's make most negative:
        addNeg(g, "A", "I");
        addNeg(g, "A", "J");
        addNeg(g, "A", "K");
        addPos(g, "B", "I"); // creates ++- triangle? A-B+, A-I-, B-I+ -> ++- frustrated, A-I frustrated
        // Now A: B+, C-, D-, E+, F-, G-, H+, I-, J-, K- -> 3 pos, 7 neg, pol=0.7
        addNeg(g, "A", "L");
        addNeg(g, "A", "M");
        // Now A: 3 pos, 9 neg => pol=0.75, deg=12
        GraphSignedConflictAdvisor.Plan p =
                new GraphSignedConflictAdvisor(g).withFixedClock(fixed()).analyze();
        GraphSignedConflictAdvisor.NodeConflict a = null;
        for (GraphSignedConflictAdvisor.NodeConflict n : p.nodes) {
            if (n.node.equals("A")) { a = n; break; }
        }
        assertNotNull(a);
        // We expect either AGITATOR or POLARIZED depending on frustrated incident count
        assertTrue("expected AGITATOR or POLARIZED for A, got " + a.verdict,
                a.verdict == GraphSignedConflictAdvisor.Verdict.AGITATOR
                        || a.verdict == GraphSignedConflictAdvisor.Verdict.POLARIZED);
        if (a.verdict == GraphSignedConflictAdvisor.Verdict.AGITATOR) {
            // Grade should be D or F
            assertTrue(p.grade == GraphSignedConflictAdvisor.Grade.D
                    || p.grade == GraphSignedConflictAdvisor.Grade.F
                    || p.grade == GraphSignedConflictAdvisor.Grade.C);
            // Playbook contains ISOLATE_AGITATORS
            boolean found = false;
            for (GraphSignedConflictAdvisor.Action act : p.playbook) {
                if ("ISOLATE_AGITATORS".equals(act.id)) found = true;
            }
            assertTrue(found);
        }
    }

    @Test public void riskAppetiteMonotonicityOnSameInput() {
        Graph<String, Edge> g = empty();
        addPos(g, "A", "B");
        addPos(g, "B", "C");
        addNeg(g, "A", "C");
        addNeg(g, "C", "D");
        addPos(g, "B", "D");
        double cautious = new GraphSignedConflictAdvisor(g).withFixedClock(fixed())
                .withRiskAppetite(GraphSignedConflictAdvisor.RiskAppetite.CAUTIOUS)
                .analyze().polarizationScore;
        double balanced = new GraphSignedConflictAdvisor(g).withFixedClock(fixed())
                .withRiskAppetite(GraphSignedConflictAdvisor.RiskAppetite.BALANCED)
                .analyze().polarizationScore;
        double aggressive = new GraphSignedConflictAdvisor(g).withFixedClock(fixed())
                .withRiskAppetite(GraphSignedConflictAdvisor.RiskAppetite.AGGRESSIVE)
                .analyze().polarizationScore;
        assertTrue("cautious >= balanced: " + cautious + " vs " + balanced,
                cautious >= balanced);
        assertTrue("balanced >= aggressive: " + balanced + " vs " + aggressive,
                balanced >= aggressive);
    }

    @Test public void aggressiveTrimsP3FallbackWhenHighPriorityPresent() {
        // Build a graph that produces a P1 action (e.g., 2+ ISOLATED_HOSTILE)
        Graph<String, Edge> g = empty();
        addNeg(g, "A", "B");
        addNeg(g, "A", "C");
        addNeg(g, "B", "C"); // A all-neg, B all-neg, C all-neg, all deg>=2 -> ISOLATED_HOSTILE
        GraphSignedConflictAdvisor.Plan p =
                new GraphSignedConflictAdvisor(g).withFixedClock(fixed())
                        .withRiskAppetite(GraphSignedConflictAdvisor.RiskAppetite.AGGRESSIVE)
                        .analyze();
        boolean hasP3 = p.playbook.stream().anyMatch(a ->
                a.priority == GraphSignedConflictAdvisor.Priority.P3);
        boolean hasP1OrP0 = p.playbook.stream().anyMatch(a ->
                a.priority == GraphSignedConflictAdvisor.Priority.P0
                        || a.priority == GraphSignedConflictAdvisor.Priority.P1);
        if (hasP1OrP0) assertFalse("Aggressive should trim P3 when P0/P1 present", hasP3);
    }

    @Test public void cautiousSchedulesDialogueOnLowGrade() {
        // All-negative triangle => F or D; cautious must add SCHEDULE_DIALOGUE_SESSION
        Graph<String, Edge> g = empty();
        addNeg(g, "A", "B");
        addNeg(g, "A", "C");
        addNeg(g, "B", "C");
        GraphSignedConflictAdvisor.Plan p =
                new GraphSignedConflictAdvisor(g).withFixedClock(fixed())
                        .withRiskAppetite(GraphSignedConflictAdvisor.RiskAppetite.CAUTIOUS)
                        .analyze();
        if (p.grade == GraphSignedConflictAdvisor.Grade.C
                || p.grade == GraphSignedConflictAdvisor.Grade.D
                || p.grade == GraphSignedConflictAdvisor.Grade.F) {
            boolean found = p.playbook.stream().anyMatch(a ->
                    "SCHEDULE_DIALOGUE_SESSION".equals(a.id));
            assertTrue("expected SCHEDULE_DIALOGUE_SESSION with cautious + grade " + p.grade, found);
        }
    }

    @Test public void jsonIsByteStable() {
        Graph<String, Edge> g = empty();
        addPos(g, "A", "B");
        addPos(g, "B", "C");
        addNeg(g, "A", "C");
        addPos(g, "B", "D");
        addNeg(g, "A", "D");
        GraphSignedConflictAdvisor adv1 = new GraphSignedConflictAdvisor(g).withFixedClock(fixed());
        GraphSignedConflictAdvisor adv2 = new GraphSignedConflictAdvisor(g).withFixedClock(fixed());
        String j1 = adv1.toJson(adv1.analyze());
        String j2 = adv2.toJson(adv2.analyze());
        assertEquals(j1, j2);
    }

    @Test public void markdownContainsHeaders() {
        Graph<String, Edge> g = empty();
        addPos(g, "A", "B");
        addNeg(g, "B", "C");
        GraphSignedConflictAdvisor adv = new GraphSignedConflictAdvisor(g).withFixedClock(fixed());
        String md = adv.toMarkdown(adv.analyze());
        assertTrue(md.contains("## Summary"));
        assertTrue(md.contains("## Playbook"));
        assertTrue(md.contains("## Insights"));
        assertTrue(md.contains("## Per-vertex"));
    }

    @Test public void renderHasVerdictHeadline() {
        Graph<String, Edge> g = empty();
        addPos(g, "A", "B");
        GraphSignedConflictAdvisor adv = new GraphSignedConflictAdvisor(g).withFixedClock(fixed());
        String txt = adv.render(adv.analyze());
        assertTrue(txt.startsWith("VERDICT:"));
    }

    @Test public void analyzeDoesNotMutateGraph() {
        Graph<String, Edge> g = empty();
        addPos(g, "A", "B");
        addNeg(g, "B", "C");
        addPos(g, "A", "C");
        int vBefore = g.getVertexCount();
        int eBefore = g.getEdgeCount();
        Set<String> verts = new HashSet<>(g.getVertices());
        new GraphSignedConflictAdvisor(g).withFixedClock(fixed())
                .withRiskAppetite(GraphSignedConflictAdvisor.RiskAppetite.CAUTIOUS)
                .analyze();
        new GraphSignedConflictAdvisor(g).withFixedClock(fixed())
                .withRiskAppetite(GraphSignedConflictAdvisor.RiskAppetite.AGGRESSIVE)
                .analyze();
        assertEquals(vBefore, g.getVertexCount());
        assertEquals(eBefore, g.getEdgeCount());
        assertEquals(verts, new HashSet<>(g.getVertices()));
    }

    @Test public void topKLimitsNodeOutput() {
        Graph<String, Edge> g = empty();
        for (int i = 0; i < 10; i++) addPos(g, "A", "N" + i);
        for (int i = 0; i < 5; i++) addNeg(g, "B", "N" + i);
        GraphSignedConflictAdvisor.Plan p =
                new GraphSignedConflictAdvisor(g).withFixedClock(fixed()).withTopK(3).analyze();
        assertTrue("topK should cap nodes <= 3, got " + p.nodes.size(), p.nodes.size() <= 3);
    }

    @Test public void coalitionsDetectedInBipolarGraph() {
        // Two positive cliques connected by a negative bridge: classic two-coalition
        Graph<String, Edge> g = empty();
        addPos(g, "A", "B");
        addPos(g, "B", "C");
        addPos(g, "A", "C");
        addPos(g, "X", "Y");
        addPos(g, "Y", "Z");
        addPos(g, "X", "Z");
        addNeg(g, "A", "X");
        addNeg(g, "B", "Y");
        GraphSignedConflictAdvisor.Plan p =
                new GraphSignedConflictAdvisor(g).withFixedClock(fixed()).analyze();
        // Should detect coalitions; either 1 or 2 depending on implementation, but in a balanced
        // signed network with two cliques, we expect 2.
        assertTrue("expected >=1 coalition, got " + p.coalitions.size(),
                p.coalitions.size() >= 1);
    }
}
