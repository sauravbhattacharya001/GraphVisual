package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphMentorMatchAdvisor}.
 *
 * @author sauravbhattacharya001
 */
public class GraphMentorMatchAdvisorTest {

    private static Clock fixed() {
        return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    }

    private static Graph<String, Edge> empty() { return new UndirectedSparseGraph<>(); }

    private static void addEdge(Graph<String, Edge> g, String u, String v) {
        if (!g.containsVertex(u)) g.addVertex(u);
        if (!g.containsVertex(v)) g.addVertex(v);
        g.addEdge(new Edge("f", u, v), u, v);
    }

    /** A vertex that is a "hub" with the given number of leaves; returns the hub name. */
    private static void hubWithLeaves(Graph<String, Edge> g, String hub, int leaves, String prefix) {
        if (!g.containsVertex(hub)) g.addVertex(hub);
        for (int i = 0; i < leaves; i++) {
            addEdge(g, hub, prefix + i);
        }
    }

    // 1. empty graph -> EMPTY_SNAPSHOT, grade A.
    @Test
    public void emptyGraphYieldsEmptySnapshot() {
        Graph<String, Edge> g = empty();
        GraphMentorMatchAdvisor.Plan p =
                new GraphMentorMatchAdvisor<>(g).withFixedClock(fixed()).analyze();
        assertEquals(GraphMentorMatchAdvisor.Grade.A, p.grade);
        assertTrue(p.insights.contains("EMPTY_SNAPSHOT"));
        assertTrue(p.mentees.isEmpty());
    }

    // 2. all nodes degree <= 1 -> NO_MENTORS_AVAILABLE.
    @Test
    public void noMentorsAvailable() {
        Graph<String, Edge> g = empty();
        addEdge(g, "a", "b");
        addEdge(g, "c", "d");
        GraphMentorMatchAdvisor.Plan p =
                new GraphMentorMatchAdvisor<>(g).withFixedClock(fixed()).analyze();
        assertTrue("expected NO_MENTORS_AVAILABLE in " + p.insights,
                p.insights.contains("NO_MENTORS_AVAILABLE"));
        assertEquals(GraphMentorMatchAdvisor.Grade.F, p.grade);
    }

    // 3. isolated mentee gets matched to well-connected mentor.
    @Test
    public void isolatedMenteeMatchesMentor() {
        Graph<String, Edge> g = empty();
        // mentor: degree 5
        hubWithLeaves(g, "mentor", 5, "f");
        // mentee: connected to one of the mentor's friends (shared neighbour)
        addEdge(g, "mentee", "f0");
        GraphMentorMatchAdvisor.Plan p =
                new GraphMentorMatchAdvisor<>(g).withFixedClock(fixed()).analyze();
        // Find mentee record
        boolean found = false;
        for (GraphMentorMatchAdvisor.MenteeMatch<?> m : p.mentees) {
            if ("mentee".equals(m.mentee)) {
                found = true;
                assertEquals(GraphMentorMatchAdvisor.MenteeVerdict.MATCHED, m.verdict);
                assertFalse(m.mentors.isEmpty());
                assertEquals("mentor", m.mentors.get(0).mentor);
                break;
            }
        }
        assertTrue("mentee record not found", found);
    }

    // 4. mentor cap enforced -> OVERLOADED appears.
    @Test
    public void mentorCapEnforced() {
        Graph<String, Edge> g = empty();
        // Single mentor, lots of low-degree mentees.
        hubWithLeaves(g, "M", 6, "x"); // mentor with degree 6
        // Add many isolated mentees that each share a neighbour with M via x0
        for (int i = 0; i < 8; i++) {
            addEdge(g, "mentee" + i, "x0");
        }
        GraphMentorMatchAdvisor.Plan p = new GraphMentorMatchAdvisor<>(g)
                .withFixedClock(fixed())
                .withMaxMentorsPerMentee(1)
                .analyze();
        boolean overloaded = false;
        for (GraphMentorMatchAdvisor.MentorProfile<?> mp : p.mentors) {
            if (mp.verdict == GraphMentorMatchAdvisor.MentorVerdict.OVERLOADED) {
                overloaded = true; break;
            }
        }
        assertTrue("expected at least one OVERLOADED mentor", overloaded);
    }

    // 5. shared-neighbour bridge boosts pairing AND triggers warm-introduce action.
    @Test
    public void sharedNeighbourBridgeFiresWarmIntroduce() {
        Graph<String, Edge> g = empty();
        hubWithLeaves(g, "mentor", 5, "n");
        addEdge(g, "mentee", "n0"); // shared neighbour exists
        GraphMentorMatchAdvisor.Plan p =
                new GraphMentorMatchAdvisor<>(g).withFixedClock(fixed()).analyze();
        boolean warm = false;
        for (GraphMentorMatchAdvisor.Action a : p.playbook) {
            if ("WARM_INTRODUCE_VIA_SHARED_FRIEND".equals(a.id)) { warm = true; break; }
        }
        assertTrue("expected WARM_INTRODUCE_VIA_SHARED_FRIEND in playbook", warm);
        // The single proposed pairing should be a bridge.
        for (GraphMentorMatchAdvisor.MenteeMatch<?> m : p.mentees) {
            if ("mentee".equals(m.mentee) && !m.mentors.isEmpty()) {
                assertTrue("expected bridge match", m.mentors.get(0).bridge);
            }
        }
    }

    // 6. mentor already directly connected to mentee penalised -> still acceptable but
    //    score reduced; we verify the alreadyConnected flag is honoured.
    @Test
    public void directlyConnectedMentorPenalised() {
        Graph<String, Edge> g = empty();
        // Build a strong mentor with degree 5, ONE of whose neighbours is our mentee.
        hubWithLeaves(g, "mentor", 4, "q");
        addEdge(g, "mentor", "mentee"); // mentee is one of mentor's neighbours -> directly connected
        GraphMentorMatchAdvisor.Plan p = new GraphMentorMatchAdvisor<>(g)
                .withFixedClock(fixed())
                .withMenteeDegreeThreshold(1)
                .analyze();
        for (GraphMentorMatchAdvisor.MenteeMatch<?> m : p.mentees) {
            if ("mentee".equals(m.mentee)) {
                for (GraphMentorMatchAdvisor.MentorRec<?> r : m.mentors) {
                    if ("mentor".equals(r.mentor)) {
                        assertTrue("mentor should be flagged alreadyConnected",
                                r.alreadyConnected);
                    }
                }
            }
        }
    }

    // 7. PAIR_UP_ISOLATED_MENTEES fires when isolated NO_MATCH mentees exist.
    @Test
    public void pairUpIsolatedMenteesFires() {
        Graph<String, Edge> g = empty();
        // Several isolated vertices that share no neighbours with any mentor pool member.
        g.addVertex("iso1"); g.addVertex("iso2"); g.addVertex("iso3");
        // A small disconnected cluster of low-degree non-mentors (no real mentor exists).
        addEdge(g, "a", "b");
        addEdge(g, "b", "c");
        GraphMentorMatchAdvisor.Plan p =
                new GraphMentorMatchAdvisor<>(g).withFixedClock(fixed()).analyze();
        boolean pair = false;
        for (GraphMentorMatchAdvisor.Action a : p.playbook) {
            if ("PAIR_UP_ISOLATED_MENTEES".equals(a.id)) { pair = true; break; }
        }
        assertTrue("expected PAIR_UP_ISOLATED_MENTEES in playbook", pair);
    }

    // 8a. CAUTIOUS appetite adds SCHEDULE_FOLLOWUP_AUDIT when not grade A.
    @Test
    public void cautiousAddsFollowupAudit() {
        Graph<String, Edge> g = empty();
        // Make it grade != A: isolated mentees + few mentors.
        g.addVertex("iso");
        addEdge(g, "a", "b"); addEdge(g, "b", "c"); addEdge(g, "c", "d");
        addEdge(g, "d", "a");
        GraphMentorMatchAdvisor.Plan p = new GraphMentorMatchAdvisor<>(g)
                .withFixedClock(fixed())
                .withRiskAppetite(GraphMentorMatchAdvisor.RiskAppetite.CAUTIOUS)
                .analyze();
        boolean fa = false;
        for (GraphMentorMatchAdvisor.Action a : p.playbook) {
            if ("SCHEDULE_FOLLOWUP_AUDIT".equals(a.id)) { fa = true; break; }
        }
        assertTrue("expected SCHEDULE_FOLLOWUP_AUDIT under CAUTIOUS appetite", fa);
    }

    // 8b. AGGRESSIVE trims lone P2 when urgent items exist.
    @Test
    public void aggressiveTrimsLoneP2() {
        Graph<String, Edge> g = empty();
        // Build scenario likely to produce a lone P2 (EXPAND_MENTOR_TRAINING) alongside
        // P0/P1 actions. Several isolated mentees + one heavily-utilised mentor.
        hubWithLeaves(g, "M", 5, "y");
        for (int i = 0; i < 6; i++) addEdge(g, "men" + i, "y0");
        GraphMentorMatchAdvisor.Plan aggressive = new GraphMentorMatchAdvisor<>(g)
                .withFixedClock(fixed())
                .withRiskAppetite(GraphMentorMatchAdvisor.RiskAppetite.AGGRESSIVE)
                .withMaxMentorsPerMentee(1)
                .analyze();
        // We don't assert a specific trimmed action; instead verify invariant: when
        // there's any P0/P1, there is NOT exactly one P2 action.
        int p2 = 0, urgent = 0;
        for (GraphMentorMatchAdvisor.Action a : aggressive.playbook) {
            if (a.priority == GraphMentorMatchAdvisor.Priority.P2) p2++;
            if (a.priority == GraphMentorMatchAdvisor.Priority.P0
                    || a.priority == GraphMentorMatchAdvisor.Priority.P1) urgent++;
        }
        if (urgent > 0) {
            assertNotEquals("AGGRESSIVE should not leave exactly one lone P2", 1, p2);
        }
    }

    // 9. High unmatched fraction -> grade F.
    @Test
    public void highUnmatchedFractionGradesF() {
        Graph<String, Edge> g = empty();
        // No mentors at all + many mentees => grade F (noMentors == true).
        for (int i = 0; i < 5; i++) g.addVertex("z" + i);
        GraphMentorMatchAdvisor.Plan p =
                new GraphMentorMatchAdvisor<>(g).withFixedClock(fixed()).analyze();
        assertEquals(GraphMentorMatchAdvisor.Grade.F, p.grade);
    }

    // 10. Healthy pipeline -> grade A, no P0 actions.
    @Test
    public void healthyPipelineGradesA() {
        Graph<String, Edge> g = empty();
        // Two strong mentors, no mentees (raise threshold to -1 to suppress).
        hubWithLeaves(g, "m1", 5, "p");
        hubWithLeaves(g, "m2", 5, "q");
        addEdge(g, "m1", "m2");
        GraphMentorMatchAdvisor.Plan p = new GraphMentorMatchAdvisor<>(g)
                .withFixedClock(fixed())
                .withMenteeDegreeThreshold(0) // no leaves -> no mentees
                .analyze();
        assertEquals(GraphMentorMatchAdvisor.Grade.A, p.grade);
        for (GraphMentorMatchAdvisor.Action a : p.playbook) {
            assertNotEquals("unexpected P0 action on healthy pipeline: " + a.id,
                    GraphMentorMatchAdvisor.Priority.P0, a.priority);
        }
    }

    // 11. toJson is deterministic and key-sorted (top-level keys appear alphabetically).
    @Test
    public void jsonIsDeterministicAndSorted() {
        Graph<String, Edge> g = empty();
        hubWithLeaves(g, "mentor", 5, "k");
        addEdge(g, "mentee", "k0");
        GraphMentorMatchAdvisor<String, Edge> advisor =
                new GraphMentorMatchAdvisor<>(g).withFixedClock(fixed());
        String j1 = advisor.toJson(advisor.analyze());
        String j2 = advisor.toJson(advisor.analyze());
        assertEquals("toJson must be deterministic", j1, j2);
        // Check expected top-level keys are present in alpha-ish documented order.
        String[] expected = {
                "bridge_match_fraction", "generated_at", "grade", "headline",
                "matched_fraction", "mentor_utilization", "risk_appetite",
                "unmatched_fraction", "weak_fraction", "mentees", "mentors",
                "playbook", "insights"
        };
        int last = -1;
        for (String key : expected) {
            int idx = j1.indexOf("\"" + key + "\"");
            assertTrue("missing key " + key, idx >= 0);
            assertTrue("key " + key + " out of order at " + idx + " (prev " + last + ")",
                    idx > last);
            last = idx;
        }
    }

    // 12. analyze() must NOT mutate the input graph.
    @Test
    public void analyzeDoesNotMutateGraph() {
        Graph<String, Edge> g = empty();
        hubWithLeaves(g, "mentor", 5, "u");
        addEdge(g, "mentee", "u0");
        int vBefore = g.getVertexCount();
        int eBefore = g.getEdgeCount();
        Set<String> vs = new HashSet<>(g.getVertices());
        new GraphMentorMatchAdvisor<>(g).withFixedClock(fixed()).analyze();
        assertEquals("vertex count must not change", vBefore, g.getVertexCount());
        assertEquals("edge count must not change", eBefore, g.getEdgeCount());
        assertEquals("vertex set must not change", vs, new HashSet<>(g.getVertices()));
    }
}
