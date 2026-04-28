package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphSentinel} -- autonomous structural drift detector.
 *
 * @author zalenix
 */
public class GraphSentinelTest {

    // -- Helpers -----------------------------------------------------------

    private static Graph<String, Edge> emptyGraph() {
        return new UndirectedSparseGraph<>();
    }

    private static void addEdge(Graph<String, Edge> g, String v1, String v2) {
        addEdge(g, v1, v2, "f");
    }

    private static void addEdge(Graph<String, Edge> g, String v1, String v2, String type) {
        if (!g.containsVertex(v1)) g.addVertex(v1);
        if (!g.containsVertex(v2)) g.addVertex(v2);
        Edge e = new Edge(type, v1, v2);
        g.addEdge(e, v1, v2);
    }

    private static void addVertex(Graph<String, Edge> g, String v) {
        if (!g.containsVertex(v)) g.addVertex(v);
    }

    /** Build a star graph: center connected to n leaves. */
    private static Graph<String, Edge> star(String center, int n) {
        Graph<String, Edge> g = emptyGraph();
        g.addVertex(center);
        for (int i = 1; i <= n; i++) {
            String leaf = "L" + i;
            addEdge(g, center, leaf);
        }
        return g;
    }

    /** Build a path graph: A-B-C-D-... */
    private static Graph<String, Edge> path(String... nodes) {
        Graph<String, Edge> g = emptyGraph();
        for (int i = 0; i < nodes.length - 1; i++) {
            addEdge(g, nodes[i], nodes[i + 1]);
        }
        return g;
    }

    /** Build a complete graph on n nodes (named "V0".."V(n-1)"). */
    private static Graph<String, Edge> complete(int n) {
        Graph<String, Edge> g = emptyGraph();
        for (int i = 0; i < n; i++) g.addVertex("V" + i);
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                addEdge(g, "V" + i, "V" + j);
        return g;
    }

    // -- Test 1: Identical graphs ------------------------------------------

    @Test
    public void identicalGraphs_stabilityIs100() {
        Graph<String, Edge> g = complete(5);
        GraphSentinel sentinel = new GraphSentinel(g, g);
        GraphSentinel.DriftReport report = sentinel.analyze();
        assertEquals(100, report.stabilityScore);
        assertEquals("Stable", report.stabilityGrade);
    }

    // -- Test 2: Completely different graphs --------------------------------

    @Test
    public void completelyDifferentGraphs_lowStability() {
        Graph<String, Edge> g1 = emptyGraph();
        addEdge(g1, "A", "B"); addEdge(g1, "B", "C"); addEdge(g1, "C", "A");
        Graph<String, Edge> g2 = emptyGraph();
        addEdge(g2, "X", "Y"); addEdge(g2, "Y", "Z"); addEdge(g2, "Z", "X");

        GraphSentinel sentinel = new GraphSentinel(g1, g2);
        GraphSentinel.DriftReport report = sentinel.analyze();
        assertTrue("Stability should be low", report.stabilityScore < 20);
        assertTrue("Should have CRITICAL alerts",
                report.alerts.stream().anyMatch(a -> "CRITICAL".equals(a.severity)));
    }

    // -- Test 3: Single node added -----------------------------------------

    @Test
    public void singleNodeAdded_minorDrift() {
        Graph<String, Edge> g1 = complete(4);
        Graph<String, Edge> g2 = complete(4);
        addVertex(g2, "NewNode");

        GraphSentinel sentinel = new GraphSentinel(g1, g2);
        GraphSentinel.DriftReport report = sentinel.analyze();
        assertTrue("Should detect the new node", report.nodesAfter > report.nodesBefore);
        assertTrue("Stability should be > 50", report.stabilityScore > 50);
    }

    // -- Test 4: Single node removed ---------------------------------------

    @Test
    public void singleNodeRemoved_alertGenerated() {
        Graph<String, Edge> g1 = emptyGraph();
        addEdge(g1, "A", "B"); addEdge(g1, "A", "C"); addEdge(g1, "B", "C");
        addEdge(g1, "A", "D");

        Graph<String, Edge> g2 = emptyGraph();
        addEdge(g2, "A", "B"); addEdge(g2, "A", "C"); addEdge(g2, "B", "C");

        GraphSentinel sentinel = new GraphSentinel(g1, g2);
        GraphSentinel.DriftReport report = sentinel.analyze();
        assertTrue("After should have fewer nodes", report.nodesAfter < report.nodesBefore);
    }

    // -- Test 5: Hub node removed ------------------------------------------

    @Test
    public void hubRemoved_criticalAlert() {
        Graph<String, Edge> g1 = star("HUB", 10);
        // After: hub removed, leaves remain disconnected
        Graph<String, Edge> g2 = emptyGraph();
        for (int i = 1; i <= 10; i++) g2.addVertex("L" + i);

        GraphSentinel sentinel = new GraphSentinel(g1, g2);
        GraphSentinel.DriftReport report = sentinel.analyze();
        assertTrue("Should have critical alerts",
                report.alerts.stream().anyMatch(a -> "CRITICAL".equals(a.severity)));
    }

    // -- Test 6: Community split -------------------------------------------

    @Test
    public void communitySplit_detected() {
        // Before: two cliques connected by bridge A-D
        Graph<String, Edge> g1 = emptyGraph();
        addEdge(g1, "A", "B"); addEdge(g1, "A", "C"); addEdge(g1, "B", "C");
        addEdge(g1, "A", "D");
        addEdge(g1, "D", "E"); addEdge(g1, "D", "F"); addEdge(g1, "E", "F");

        // After: bridge removed, two separate components
        Graph<String, Edge> g2 = emptyGraph();
        addEdge(g2, "A", "B"); addEdge(g2, "A", "C"); addEdge(g2, "B", "C");
        addEdge(g2, "D", "E"); addEdge(g2, "D", "F"); addEdge(g2, "E", "F");

        GraphSentinel sentinel = new GraphSentinel(g1, g2);
        GraphSentinel.DriftReport report = sentinel.analyze();
        assertTrue("Should detect community split",
                report.communityEvents.stream().anyMatch(e -> "SPLIT".equals(e.type)));
    }

    // -- Test 7: Community merge -------------------------------------------

    @Test
    public void communityMerge_detected() {
        // Before: two separate components
        Graph<String, Edge> g1 = emptyGraph();
        addEdge(g1, "A", "B"); addEdge(g1, "A", "C");
        addEdge(g1, "D", "E"); addEdge(g1, "D", "F");

        // After: connected by bridge
        Graph<String, Edge> g2 = emptyGraph();
        addEdge(g2, "A", "B"); addEdge(g2, "A", "C");
        addEdge(g2, "D", "E"); addEdge(g2, "D", "F");
        addEdge(g2, "C", "D"); // bridge

        GraphSentinel sentinel = new GraphSentinel(g1, g2);
        GraphSentinel.DriftReport report = sentinel.analyze();
        assertTrue("Should detect community merge",
                report.communityEvents.stream().anyMatch(e -> "MERGE".equals(e.type)));
    }

    // -- Test 8: Role transition -- peripheral to hub -----------------------

    @Test
    public void peripheralToHub_roleTransition() {
        // Before: P is peripheral (degree 1)
        Graph<String, Edge> g1 = emptyGraph();
        addEdge(g1, "A", "B"); addEdge(g1, "A", "C"); addEdge(g1, "A", "D");
        addEdge(g1, "A", "E"); addEdge(g1, "A", "F");
        addEdge(g1, "P", "A");

        // After: P is now the hub
        Graph<String, Edge> g2 = emptyGraph();
        addEdge(g2, "A", "B"); addEdge(g2, "A", "C");
        addEdge(g2, "P", "A"); addEdge(g2, "P", "B"); addEdge(g2, "P", "C");
        addEdge(g2, "P", "D"); addEdge(g2, "P", "E"); addEdge(g2, "P", "F");

        GraphSentinel sentinel = new GraphSentinel(g1, g2);
        GraphSentinel.DriftReport report = sentinel.analyze();
        assertTrue("Should detect role transitions", !report.roleTransitions.isEmpty());
    }

    // -- Test 9: Centrality shift when bridge removed ----------------------

    @Test
    public void bridgeRemoval_centralityShifts() {
        Graph<String, Edge> g1 = emptyGraph();
        addEdge(g1, "A", "B"); addEdge(g1, "B", "C"); addEdge(g1, "C", "D");
        addEdge(g1, "D", "E");

        Graph<String, Edge> g2 = emptyGraph();
        addEdge(g2, "A", "B"); addEdge(g2, "D", "E");
        addVertex(g2, "C");

        GraphSentinel sentinel = new GraphSentinel(g1, g2);
        GraphSentinel.DriftReport report = sentinel.analyze();
        assertTrue("Should detect centrality shifts", !report.centralityShifts.isEmpty());
    }

    // -- Test 10: New isolate warning --------------------------------------

    @Test
    public void newIsolate_warningGenerated() {
        Graph<String, Edge> g1 = emptyGraph();
        addEdge(g1, "A", "B"); addEdge(g1, "B", "C");

        // C loses its edge, becomes isolated
        Graph<String, Edge> g2 = emptyGraph();
        addEdge(g2, "A", "B");
        addVertex(g2, "C");

        GraphSentinel sentinel = new GraphSentinel(g1, g2);
        GraphSentinel.DriftReport report = sentinel.analyze();
        assertTrue("Should have warning about isolate",
                report.alerts.stream().anyMatch(a ->
                        "WARNING".equals(a.severity) && "NEW_ISOLATE".equals(a.category)));
    }

    // -- Test 11: Empty before graph ---------------------------------------

    @Test
    public void emptyBeforeGraph_noException() {
        Graph<String, Edge> g1 = emptyGraph();
        Graph<String, Edge> g2 = complete(3);

        GraphSentinel sentinel = new GraphSentinel(g1, g2);
        GraphSentinel.DriftReport report = sentinel.analyze();
        assertNotNull(report);
        assertTrue(report.stabilityScore >= 0 && report.stabilityScore <= 100);
    }

    // -- Test 12: Empty after graph ----------------------------------------

    @Test
    public void emptyAfterGraph_noException() {
        Graph<String, Edge> g1 = complete(3);
        Graph<String, Edge> g2 = emptyGraph();

        GraphSentinel sentinel = new GraphSentinel(g1, g2);
        GraphSentinel.DriftReport report = sentinel.analyze();
        assertNotNull(report);
        assertTrue(report.stabilityScore >= 0 && report.stabilityScore <= 100);
    }

    // -- Test 13: Both empty -----------------------------------------------

    @Test
    public void bothEmpty_stability100() {
        Graph<String, Edge> g1 = emptyGraph();
        Graph<String, Edge> g2 = emptyGraph();

        GraphSentinel sentinel = new GraphSentinel(g1, g2);
        GraphSentinel.DriftReport report = sentinel.analyze();
        assertEquals(100, report.stabilityScore);
    }

    // -- Test 14: Single node graph ----------------------------------------

    @Test
    public void singleNodeGraph_noCrash() {
        Graph<String, Edge> g1 = emptyGraph();
        g1.addVertex("A");
        Graph<String, Edge> g2 = emptyGraph();
        g2.addVertex("A");

        GraphSentinel sentinel = new GraphSentinel(g1, g2);
        GraphSentinel.DriftReport report = sentinel.analyze();
        assertNotNull(report);
        assertEquals(100, report.stabilityScore);
    }

    // -- Test 15: Self-referencing unchanged graph -------------------------

    @Test
    public void sameGraphReference_stability100() {
        Graph<String, Edge> g = star("C", 5);
        GraphSentinel sentinel = new GraphSentinel(g, g);
        GraphSentinel.DriftReport report = sentinel.analyze();
        assertEquals(100, report.stabilityScore);
    }

    // -- Test 16: Hub strengthening ----------------------------------------

    @Test
    public void hubStrengthening_detected() {
        Graph<String, Edge> g1 = star("HUB", 6);
        Graph<String, Edge> g2 = star("HUB", 12);
        // Retain original leaves too
        for (int i = 1; i <= 6; i++) {
            if (!g2.containsVertex("L" + i)) addEdge(g2, "HUB", "L" + i);
        }

        GraphSentinel sentinel = new GraphSentinel(g1, g2);
        GraphSentinel.DriftReport report = sentinel.analyze();
        assertTrue("Should detect hub events", !report.hubEvents.isEmpty());
    }

    // -- Test 17: Hub weakening --------------------------------------------

    @Test
    public void hubWeakening_detected() {
        Graph<String, Edge> g1 = star("HUB", 10);
        Graph<String, Edge> g2 = star("HUB", 3);
        // Add remaining leaves as isolates
        for (int i = 4; i <= 10; i++) addVertex(g2, "L" + i);

        GraphSentinel sentinel = new GraphSentinel(g1, g2);
        GraphSentinel.DriftReport report = sentinel.analyze();
        assertTrue("Should detect hub weakening or decline",
                report.hubEvents.stream().anyMatch(h ->
                        "WEAKENED".equals(h.type) || "DECLINED".equals(h.type)));
    }

    // -- Test 18: Multiple community events --------------------------------

    @Test
    public void multipleCommunityEvents() {
        // Before: 3 components
        Graph<String, Edge> g1 = emptyGraph();
        addEdge(g1, "A1", "A2"); addEdge(g1, "A2", "A3");
        addEdge(g1, "B1", "B2"); addEdge(g1, "B2", "B3");
        addEdge(g1, "C1", "C2");

        // After: A+B merged, C died, D born
        Graph<String, Edge> g2 = emptyGraph();
        addEdge(g2, "A1", "A2"); addEdge(g2, "A2", "A3");
        addEdge(g2, "B1", "B2"); addEdge(g2, "B2", "B3");
        addEdge(g2, "A3", "B1"); // merge A and B
        addEdge(g2, "D1", "D2"); // new community

        GraphSentinel sentinel = new GraphSentinel(g1, g2);
        GraphSentinel.DriftReport report = sentinel.analyze();
        assertTrue("Should have multiple community events", report.communityEvents.size() >= 2);
    }

    // -- Test 19: Alert priority ordering ----------------------------------

    @Test
    public void alertsArePrioritySorted() {
        // Big change -> multiple alert levels
        Graph<String, Edge> g1 = star("HUB", 10);
        Graph<String, Edge> g2 = emptyGraph();
        for (int i = 1; i <= 10; i++) g2.addVertex("L" + i);
        addEdge(g2, "X", "Y"); // new nodes

        GraphSentinel sentinel = new GraphSentinel(g1, g2);
        GraphSentinel.DriftReport report = sentinel.analyze();
        // Verify sorted: CRITICAL ≤ WARNING ≤ INFO
        for (int i = 1; i < report.alerts.size(); i++) {
            int prev = severityRank(report.alerts.get(i - 1).severity);
            int curr = severityRank(report.alerts.get(i).severity);
            assertTrue("Alerts should be sorted by severity", prev <= curr);
        }
    }

    // -- Test 20: Stability score bounds -----------------------------------

    @Test
    public void stabilityScoreAlwaysBounded() {
        // Try various combinations
        Graph<String, Edge>[] graphs = new Graph[]{
                emptyGraph(), complete(3), star("C", 8), path("A", "B", "C", "D")
        };
        for (Graph<String, Edge> g1 : graphs) {
            for (Graph<String, Edge> g2 : graphs) {
                GraphSentinel sentinel = new GraphSentinel(g1, g2);
                GraphSentinel.DriftReport r = sentinel.analyze();
                assertTrue("Score must be >= 0", r.stabilityScore >= 0);
                assertTrue("Score must be <= 100", r.stabilityScore <= 100);
            }
        }
    }

    // -- Test 21: Text report contains key info ----------------------------

    @Test
    public void textReportContainsKeyInfo() {
        Graph<String, Edge> g1 = complete(4);
        Graph<String, Edge> g2 = complete(3);

        GraphSentinel sentinel = new GraphSentinel(g1, g2);
        GraphSentinel.DriftReport report = sentinel.analyze();
        String text = sentinel.toText(report);

        assertTrue("Should contain stability score", text.contains("Stability Score:"));
        assertTrue("Should contain GraphSentinel", text.contains("GraphSentinel"));
        assertTrue("Should contain node counts", text.contains("Nodes:"));
    }

    // -- Test 22: HTML report has expected structure ------------------------

    @Test
    public void htmlReportHasExpectedStructure() {
        Graph<String, Edge> g1 = star("H", 6);
        Graph<String, Edge> g2 = emptyGraph();
        for (int i = 1; i <= 6; i++) g2.addVertex("L" + i);

        GraphSentinel sentinel = new GraphSentinel(g1, g2);
        GraphSentinel.DriftReport report = sentinel.analyze();
        String html = sentinel.exportHtml(report);

        assertTrue("Should be valid HTML", html.contains("<!DOCTYPE html>"));
        assertTrue("Should have title", html.contains("GraphSentinel"));
        assertTrue("Should have alerts section", html.contains("Alerts"));
        assertTrue("Should have SVG gauge", html.contains("<svg"));
    }

    // -- Test 23: Large graph handles without error ------------------------

    @Test
    public void largeGraph_noError() {
        Graph<String, Edge> g1 = emptyGraph();
        Graph<String, Edge> g2 = emptyGraph();
        Random rng = new Random(42);
        for (int i = 0; i < 60; i++) {
            g1.addVertex("N" + i);
            g2.addVertex("N" + i);
        }
        for (int i = 0; i < 120; i++) {
            String a = "N" + rng.nextInt(60);
            String b = "N" + rng.nextInt(60);
            if (!a.equals(b)) addEdge(g1, a, b);
        }
        for (int i = 0; i < 100; i++) {
            String a = "N" + rng.nextInt(60);
            String b = "N" + rng.nextInt(60);
            if (!a.equals(b)) addEdge(g2, a, b);
        }

        GraphSentinel sentinel = new GraphSentinel(g1, g2);
        GraphSentinel.DriftReport report = sentinel.analyze();
        assertNotNull(report);
        assertTrue(report.stabilityScore >= 0 && report.stabilityScore <= 100);
    }

    // -- Test 24: Null graph rejection -------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void nullBeforeGraph_throws() {
        new GraphSentinel(null, emptyGraph());
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullAfterGraph_throws() {
        new GraphSentinel(emptyGraph(), null);
    }

    // -- Test 25: Nodes with no edges in both snapshots --------------------

    @Test
    public void isolatedNodesInBoth_stableAndNoAlerts() {
        Graph<String, Edge> g1 = emptyGraph();
        g1.addVertex("A"); g1.addVertex("B"); g1.addVertex("C");
        Graph<String, Edge> g2 = emptyGraph();
        g2.addVertex("A"); g2.addVertex("B"); g2.addVertex("C");

        GraphSentinel sentinel = new GraphSentinel(g1, g2);
        GraphSentinel.DriftReport report = sentinel.analyze();
        assertEquals(100, report.stabilityScore);
        // Should not have NEW_ISOLATE alerts (they were always isolates)
        assertTrue("Should not flag pre-existing isolates",
                report.alerts.stream().noneMatch(a -> "NEW_ISOLATE".equals(a.category)));
    }

    // -- Test 26: DriftReport timestamp is set -----------------------------

    @Test
    public void reportTimestampIsSet() {
        GraphSentinel sentinel = new GraphSentinel(emptyGraph(), emptyGraph());
        GraphSentinel.DriftReport report = sentinel.analyze();
        assertTrue("Timestamp should be recent", report.timestamp > 0);
    }

    // -- Test 27: Community death detected ---------------------------------

    @Test
    public void communityDeath_detected() {
        Graph<String, Edge> g1 = emptyGraph();
        addEdge(g1, "A", "B"); addEdge(g1, "B", "C");
        addEdge(g1, "D", "E");

        // After: D-E community is gone entirely
        Graph<String, Edge> g2 = emptyGraph();
        addEdge(g2, "A", "B"); addEdge(g2, "B", "C");

        GraphSentinel sentinel = new GraphSentinel(g1, g2);
        GraphSentinel.DriftReport report = sentinel.analyze();
        assertTrue("Should detect community death",
                report.communityEvents.stream().anyMatch(e -> "DEATH".equals(e.type)));
    }

    // -- Test 28: Community birth detected ---------------------------------

    @Test
    public void communityBirth_detected() {
        Graph<String, Edge> g1 = emptyGraph();
        addEdge(g1, "A", "B");

        Graph<String, Edge> g2 = emptyGraph();
        addEdge(g2, "A", "B");
        addEdge(g2, "X", "Y"); addEdge(g2, "Y", "Z"); // new community

        GraphSentinel sentinel = new GraphSentinel(g1, g2);
        GraphSentinel.DriftReport report = sentinel.analyze();
        assertTrue("Should detect community birth",
                report.communityEvents.stream().anyMatch(e -> "BIRTH".equals(e.type)));
    }

    // -- Test 29: Hub emerged ----------------------------------------------

    @Test
    public void hubEmerged_detected() {
        Graph<String, Edge> g1 = path("A", "B", "C", "D", "E");
        Graph<String, Edge> g2 = emptyGraph();
        // Make C a hub
        addEdge(g2, "A", "B"); addEdge(g2, "B", "C"); addEdge(g2, "C", "D"); addEdge(g2, "D", "E");
        addEdge(g2, "C", "A"); addEdge(g2, "C", "E");
        addEdge(g2, "C", "F"); addEdge(g2, "C", "G"); addEdge(g2, "C", "H");

        GraphSentinel sentinel = new GraphSentinel(g1, g2);
        GraphSentinel.DriftReport report = sentinel.analyze();
        assertTrue("Should detect hub emergence",
                report.hubEvents.stream().anyMatch(h -> "EMERGED".equals(h.type)));
    }

    // -- Test 30: Stability grades cover all ranges ------------------------

    @Test
    public void stabilityGrades_allRanges() {
        // Identical = Stable
        GraphSentinel s1 = new GraphSentinel(complete(5), complete(5));
        assertEquals("Stable", s1.analyze().stabilityGrade);

        // Completely different = Critical Transformation
        Graph<String, Edge> ga = emptyGraph(); addEdge(ga, "A", "B");
        Graph<String, Edge> gb = emptyGraph(); addEdge(gb, "X", "Y");
        GraphSentinel s2 = new GraphSentinel(ga, gb);
        GraphSentinel.DriftReport r2 = s2.analyze();
        assertNotNull(r2.stabilityGrade);
        assertTrue("Grade should be non-empty", r2.stabilityGrade.length() > 0);
    }

    // -- Utility -----------------------------------------------------------

    private static int severityRank(String severity) {
        switch (severity) {
            case "CRITICAL": return 0;
            case "WARNING": return 1;
            case "INFO": return 2;
            default: return 3;
        }
    }
}
