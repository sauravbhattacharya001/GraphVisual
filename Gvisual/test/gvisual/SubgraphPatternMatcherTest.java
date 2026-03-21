package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for SubgraphPatternMatcher — VF2-style subgraph pattern finding.
 */
public class SubgraphPatternMatcherTest {

    // ── Helpers ─────────────────────────────────────────────────

    private Graph<String, Edge> emptyGraph() {
        return new UndirectedSparseGraph<>();
    }

    private void addEdge(Graph<String, Edge> g, String v1, String v2) {
        if (!g.containsVertex(v1)) g.addVertex(v1);
        if (!g.containsVertex(v2)) g.addVertex(v2);
        g.addEdge(new Edge(null, v1, v2), v1, v2);
    }

    private void addTypedEdge(Graph<String, Edge> g, String v1, String v2,
                              String type) {
        if (!g.containsVertex(v1)) g.addVertex(v1);
        if (!g.containsVertex(v2)) g.addVertex(v2);
        g.addEdge(new Edge(type, v1, v2), v1, v2);
    }

    private Graph<String, Edge> makeTriangle(String a, String b, String c) {
        Graph<String, Edge> g = emptyGraph();
        addEdge(g, a, b);
        addEdge(g, b, c);
        addEdge(g, a, c);
        return g;
    }

    private Graph<String, Edge> makeK4() {
        Graph<String, Edge> g = emptyGraph();
        String[] v = {"A", "B", "C", "D"};
        for (int i = 0; i < 4; i++) {
            for (int j = i + 1; j < 4; j++) {
                addEdge(g, v[i], v[j]);
            }
        }
        return g;
    }

    private Graph<String, Edge> makePath(String... nodes) {
        Graph<String, Edge> g = emptyGraph();
        for (int i = 0; i < nodes.length - 1; i++) {
            addEdge(g, nodes[i], nodes[i + 1]);
        }
        return g;
    }

    // ── Builder validation ──────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullTarget() {
        new SubgraphPatternMatcher.Builder(null,
                SubgraphPatternMatcher.trianglePattern()).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullPattern() {
        new SubgraphPatternMatcher.Builder(emptyGraph(), null).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPatternTooSmall() {
        Graph<String, Edge> tiny = emptyGraph();
        tiny.addVertex("X");
        new SubgraphPatternMatcher.Builder(emptyGraph(), tiny).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMaxMatchesZero() {
        new SubgraphPatternMatcher.Builder(emptyGraph(),
                SubgraphPatternMatcher.trianglePattern())
                .maxMatches(0).build();
    }

    // ── No matches ──────────────────────────────────────────────

    @Test
    public void testNoMatchInEmptyTarget() {
        Graph<String, Edge> target = emptyGraph();
        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, SubgraphPatternMatcher.trianglePattern()).build();
        SubgraphPatternMatcher.MatchResult r = m.findMatches();
        assertEquals(0, r.getMatchCount());
        assertFalse(r.isHitLimit());
    }

    @Test
    public void testNoMatchPatternLargerThanTarget() {
        Graph<String, Edge> target = makePath("A", "B");
        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, SubgraphPatternMatcher.squarePattern()).build();
        assertEquals(0, m.findMatches().getMatchCount());
    }

    @Test
    public void testNoTriangleInTree() {
        Graph<String, Edge> tree = emptyGraph();
        addEdge(tree, "A", "B");
        addEdge(tree, "A", "C");
        addEdge(tree, "B", "D");
        addEdge(tree, "B", "E");
        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                tree, SubgraphPatternMatcher.trianglePattern()).build();
        assertEquals(0, m.findMatches().getMatchCount());
    }

    // ── Triangle matching ───────────────────────────────────────

    @Test
    public void testSingleTriangleFound() {
        Graph<String, Edge> target = makeTriangle("A", "B", "C");
        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, SubgraphPatternMatcher.trianglePattern()).build();
        SubgraphPatternMatcher.MatchResult r = m.findMatches();
        assertEquals(1, r.getMatchCount());

        SubgraphPatternMatcher.Match match = r.getMatches().get(0);
        assertEquals(3, match.size());
        Set<String> nodes = match.getTargetNodes();
        assertTrue(nodes.contains("A"));
        assertTrue(nodes.contains("B"));
        assertTrue(nodes.contains("C"));
    }

    @Test
    public void testTwoDisjointTriangles() {
        Graph<String, Edge> target = emptyGraph();
        addEdge(target, "A", "B");
        addEdge(target, "B", "C");
        addEdge(target, "A", "C");
        addEdge(target, "D", "E");
        addEdge(target, "E", "F");
        addEdge(target, "D", "F");

        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, SubgraphPatternMatcher.trianglePattern()).build();
        assertEquals(2, m.findMatches().getMatchCount());
    }

    @Test
    public void testTrianglesInK4() {
        Graph<String, Edge> target = makeK4();
        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, SubgraphPatternMatcher.trianglePattern()).build();
        // K4 contains C(4,3) = 4 triangles
        assertEquals(4, m.findMatches().getMatchCount());
    }

    // ── Path matching ───────────────────────────────────────────

    @Test
    public void testPathInChain() {
        Graph<String, Edge> target = makePath("A", "B", "C", "D", "E");
        Graph<String, Edge> pattern = SubgraphPatternMatcher.pathPattern(2);
        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, pattern).build();
        SubgraphPatternMatcher.MatchResult r = m.findMatches();
        // Path of length 2 (3 nodes): ABC, BCD, CDE = 3 matches
        assertEquals(3, r.getMatchCount());
    }

    @Test
    public void testPathLength1() {
        Graph<String, Edge> target = makePath("A", "B", "C");
        Graph<String, Edge> pattern = SubgraphPatternMatcher.pathPattern(1);
        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, pattern).build();
        // Two edges: A-B and B-C
        assertEquals(2, m.findMatches().getMatchCount());
    }

    // ── Star matching ───────────────────────────────────────────

    @Test
    public void testStarInHub() {
        Graph<String, Edge> target = emptyGraph();
        addEdge(target, "hub", "A");
        addEdge(target, "hub", "B");
        addEdge(target, "hub", "C");
        addEdge(target, "hub", "D");

        Graph<String, Edge> pattern = SubgraphPatternMatcher.starPattern(3);
        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, pattern).build();
        // 4 leaves choose 3 = 4 star-3 patterns
        assertTrue(m.findMatches().getMatchCount() >= 4);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testStarPatternMinLeaves() {
        SubgraphPatternMatcher.starPattern(1);
    }

    // ── Square matching ─────────────────────────────────────────

    @Test
    public void testSquareFound() {
        Graph<String, Edge> target = emptyGraph();
        addEdge(target, "A", "B");
        addEdge(target, "B", "C");
        addEdge(target, "C", "D");
        addEdge(target, "D", "A");

        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, SubgraphPatternMatcher.squarePattern()).build();
        assertEquals(1, m.findMatches().getMatchCount());
    }

    @Test
    public void testNoSquareInTriangle() {
        Graph<String, Edge> target = makeTriangle("A", "B", "C");
        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, SubgraphPatternMatcher.squarePattern()).build();
        assertEquals(0, m.findMatches().getMatchCount());
    }

    // ── Diamond matching ────────────────────────────────────────

    @Test
    public void testDiamondInK4() {
        // K4 has 4 nodes, 6 edges. Diamond has 4 nodes, 5 edges.
        // Each diamond is K4 minus one edge. 6 possible removals = 6 diamonds.
        // But dedup by node set: all cover {A,B,C,D} = only 1 unique set.
        // Actually no — diamond is a specific subgraph structure, not just
        // a node set. With dedup by node set, K4 yields 1 match.
        Graph<String, Edge> target = makeK4();
        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, SubgraphPatternMatcher.diamondPattern()).build();
        // All 4 nodes form a single set → 1 unique match
        assertEquals(1, m.findMatches().getMatchCount());
    }

    @Test
    public void testDiamondPattern() {
        Graph<String, Edge> p = SubgraphPatternMatcher.diamondPattern();
        assertEquals(4, p.getVertexCount());
        assertEquals(5, p.getEdgeCount());
    }

    // ── Bowtie matching ─────────────────────────────────────────

    @Test
    public void testBowtiePattern() {
        Graph<String, Edge> p = SubgraphPatternMatcher.bowtiePattern();
        assertEquals(5, p.getVertexCount());
        assertEquals(6, p.getEdgeCount());
    }

    @Test
    public void testBowtieFound() {
        Graph<String, Edge> target = emptyGraph();
        // Two triangles sharing node C
        addEdge(target, "A", "B");
        addEdge(target, "B", "C");
        addEdge(target, "A", "C");
        addEdge(target, "C", "D");
        addEdge(target, "D", "E");
        addEdge(target, "C", "E");

        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, SubgraphPatternMatcher.bowtiePattern()).build();
        SubgraphPatternMatcher.MatchResult r = m.findMatches();
        assertTrue(r.getMatchCount() >= 1);
        assertEquals(5, r.getMatches().get(0).size());
    }

    // ── Complete pattern ────────────────────────────────────────

    @Test
    public void testCompletePattern() {
        Graph<String, Edge> p = SubgraphPatternMatcher.completePattern(4);
        assertEquals(4, p.getVertexCount());
        assertEquals(6, p.getEdgeCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCompletePatternMinNodes() {
        SubgraphPatternMatcher.completePattern(1);
    }

    @Test
    public void testK4InK5() {
        Graph<String, Edge> k5 = emptyGraph();
        String[] v = {"A", "B", "C", "D", "E"};
        for (int i = 0; i < 5; i++) {
            for (int j = i + 1; j < 5; j++) {
                addEdge(k5, v[i], v[j]);
            }
        }
        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                k5, SubgraphPatternMatcher.completePattern(4)).build();
        // C(5,4) = 5 complete subgraphs of size 4
        assertEquals(5, m.findMatches().getMatchCount());
    }

    // ── House pattern ───────────────────────────────────────────

    @Test
    public void testHousePattern() {
        Graph<String, Edge> p = SubgraphPatternMatcher.housePattern();
        assertEquals(5, p.getVertexCount());
        assertEquals(6, p.getEdgeCount());
    }

    @Test
    public void testHouseFound() {
        Graph<String, Edge> target = emptyGraph();
        addEdge(target, "A", "B");
        addEdge(target, "B", "C");
        addEdge(target, "C", "D");
        addEdge(target, "D", "A");
        addEdge(target, "C", "E");
        addEdge(target, "D", "E");

        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, SubgraphPatternMatcher.housePattern()).build();
        assertTrue(m.findMatches().getMatchCount() >= 1);
    }

    // ── MaxMatches limit ────────────────────────────────────────

    @Test
    public void testMaxMatchesLimit() {
        Graph<String, Edge> target = makeK4();
        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, SubgraphPatternMatcher.trianglePattern())
                .maxMatches(2).build();
        SubgraphPatternMatcher.MatchResult r = m.findMatches();
        assertEquals(2, r.getMatchCount());
        assertTrue(r.isHitLimit());
    }

    // ── Degree-constrained matching ─────────────────────────────

    @Test
    public void testDegreeConstrainedReducesMatches() {
        // Star hub has degree 4, pattern hub has degree 3
        // With degree constraint, hub must have degree >= 3
        Graph<String, Edge> target = emptyGraph();
        addEdge(target, "H", "A");
        addEdge(target, "H", "B");
        addEdge(target, "H", "C");
        addEdge(target, "H", "D");

        Graph<String, Edge> pattern = SubgraphPatternMatcher.starPattern(3);

        // Without degree constraint
        SubgraphPatternMatcher m1 = new SubgraphPatternMatcher.Builder(
                target, pattern).degreeConstrained(false).build();
        int normalCount = m1.findMatches().getMatchCount();

        // With degree constraint
        SubgraphPatternMatcher m2 = new SubgraphPatternMatcher.Builder(
                target, pattern).degreeConstrained(true).build();
        int constrainedCount = m2.findMatches().getMatchCount();

        // Constrained should be <= normal (hub must match H which has degree 4)
        assertTrue(constrainedCount <= normalCount);
    }

    // ── Edge type filtering ─────────────────────────────────────

    @Test
    public void testEdgeTypeFilter() {
        Graph<String, Edge> target = emptyGraph();
        addTypedEdge(target, "A", "B", "friend");
        addTypedEdge(target, "B", "C", "friend");
        addTypedEdge(target, "A", "C", "colleague");

        // Triangle using only "friend" edges → no triangle (A-C is colleague)
        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, SubgraphPatternMatcher.trianglePattern())
                .edgeTypeFilter("friend").build();
        assertEquals(0, m.findMatches().getMatchCount());
    }

    @Test
    public void testEdgeTypeFilterFindsMatch() {
        Graph<String, Edge> target = emptyGraph();
        addTypedEdge(target, "A", "B", "friend");
        addTypedEdge(target, "B", "C", "friend");
        addTypedEdge(target, "A", "C", "friend");
        addTypedEdge(target, "C", "D", "colleague");

        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, SubgraphPatternMatcher.trianglePattern())
                .edgeTypeFilter("friend").build();
        assertEquals(1, m.findMatches().getMatchCount());
    }

    // ── Match class ─────────────────────────────────────────────

    @Test
    public void testMatchMapping() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("P0", "A");
        map.put("P1", "B");
        map.put("P2", "C");
        SubgraphPatternMatcher.Match match = new SubgraphPatternMatcher.Match(map);
        assertEquals(3, match.size());
        assertEquals("A", match.getMapping().get("P0"));
        Set<String> nodes = match.getTargetNodes();
        assertEquals(3, nodes.size());
        assertTrue(nodes.containsAll(Arrays.asList("A", "B", "C")));
    }

    @Test
    public void testMatchOverlap() {
        Map<String, String> m1 = new LinkedHashMap<>();
        m1.put("P0", "A");
        m1.put("P1", "B");
        m1.put("P2", "C");
        Map<String, String> m2 = new LinkedHashMap<>();
        m2.put("P0", "B");
        m2.put("P1", "C");
        m2.put("P2", "D");
        SubgraphPatternMatcher.Match match1 = new SubgraphPatternMatcher.Match(m1);
        SubgraphPatternMatcher.Match match2 = new SubgraphPatternMatcher.Match(m2);
        assertEquals(2, match1.overlapWith(match2)); // B and C shared
    }

    @Test
    public void testMatchEquality() {
        Map<String, String> m1 = new LinkedHashMap<>();
        m1.put("P0", "A");
        m1.put("P1", "B");
        Map<String, String> m2 = new LinkedHashMap<>();
        m2.put("P0", "B");
        m2.put("P1", "A");
        SubgraphPatternMatcher.Match match1 = new SubgraphPatternMatcher.Match(m1);
        SubgraphPatternMatcher.Match match2 = new SubgraphPatternMatcher.Match(m2);
        // Same target node set → equal
        assertEquals(match1, match2);
        assertEquals(match1.hashCode(), match2.hashCode());
    }

    @Test
    public void testMatchToString() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("P0", "X");
        m.put("P1", "Y");
        SubgraphPatternMatcher.Match match = new SubgraphPatternMatcher.Match(m);
        String s = match.toString();
        assertTrue(s.contains("P0→X"));
        assertTrue(s.contains("P1→Y"));
    }

    // ── MatchResult statistics ──────────────────────────────────

    @Test
    public void testCoverage() {
        Graph<String, Edge> target = emptyGraph();
        addEdge(target, "A", "B");
        addEdge(target, "B", "C");
        addEdge(target, "A", "C");
        target.addVertex("D"); // isolated
        target.addVertex("E"); // isolated

        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, SubgraphPatternMatcher.trianglePattern()).build();
        SubgraphPatternMatcher.MatchResult r = m.findMatches();
        // 3 out of 5 nodes covered = 60%
        assertEquals(0.6, r.getCoverage(), 0.01);
    }

    @Test
    public void testNodeParticipation() {
        Graph<String, Edge> target = makeK4();
        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, SubgraphPatternMatcher.trianglePattern()).build();
        SubgraphPatternMatcher.MatchResult r = m.findMatches();
        Map<String, Integer> participation = r.getNodeParticipation();
        // Each node in K4 participates in 3 triangles
        for (int count : participation.values()) {
            assertEquals(3, count);
        }
    }

    @Test
    public void testTopParticipants() {
        Graph<String, Edge> target = makeK4();
        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, SubgraphPatternMatcher.trianglePattern()).build();
        List<Map.Entry<String, Integer>> top =
                m.findMatches().getTopParticipants(2);
        assertEquals(2, top.size());
        assertEquals(3, top.get(0).getValue().intValue());
    }

    @Test
    public void testAverageOverlapNoMatches() {
        Graph<String, Edge> target = makePath("A", "B", "C");
        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, SubgraphPatternMatcher.trianglePattern()).build();
        assertEquals(0, m.findMatches().getAverageOverlap(), 0.001);
    }

    @Test
    public void testAverageOverlapOneMatch() {
        Graph<String, Edge> target = makeTriangle("A", "B", "C");
        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, SubgraphPatternMatcher.trianglePattern()).build();
        assertEquals(0, m.findMatches().getAverageOverlap(), 0.001);
    }

    @Test
    public void testAverageOverlapMultipleMatches() {
        Graph<String, Edge> target = makeK4();
        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, SubgraphPatternMatcher.trianglePattern()).build();
        SubgraphPatternMatcher.MatchResult r = m.findMatches();
        // 4 triangles in K4, each pair shares 2 nodes
        assertTrue(r.getAverageOverlap() > 0);
    }

    // ── Report ──────────────────────────────────────────────────

    @Test
    public void testReport() {
        Graph<String, Edge> target = makeK4();
        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, SubgraphPatternMatcher.trianglePattern()).build();
        String report = m.findMatches().generateReport();
        assertTrue(report.contains("Pattern: 3 nodes, 3 edges"));
        assertTrue(report.contains("Target:  4 nodes, 6 edges"));
        assertTrue(report.contains("Matches: 4"));
        assertTrue(report.contains("Coverage:"));
        assertTrue(report.contains("Top participants"));
    }

    @Test
    public void testReportEmpty() {
        Graph<String, Edge> target = makePath("A", "B");
        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, SubgraphPatternMatcher.trianglePattern()).build();
        String report = m.findMatches().generateReport();
        assertTrue(report.contains("Matches: 0"));
    }

    // ── Custom pattern ──────────────────────────────────────────

    @Test
    public void testCustomPattern() {
        // Custom pattern: 4-clique with one pendant (tail)
        Graph<String, Edge> pattern = emptyGraph();
        addEdge(pattern, "A", "B");
        addEdge(pattern, "A", "C");
        addEdge(pattern, "B", "C");
        addEdge(pattern, "C", "D"); // pendant

        // Target: triangle + pendant
        Graph<String, Edge> target = emptyGraph();
        addEdge(target, "X", "Y");
        addEdge(target, "Y", "Z");
        addEdge(target, "X", "Z");
        addEdge(target, "Z", "W");

        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, pattern).build();
        SubgraphPatternMatcher.MatchResult r = m.findMatches();
        assertTrue(r.getMatchCount() >= 1);
    }

    // ── Deduplication ───────────────────────────────────────────

    @Test
    public void testDeduplicated() {
        // Path P0-P1 can match A-B in two orientations but should dedup
        Graph<String, Edge> target = emptyGraph();
        addEdge(target, "A", "B");
        Graph<String, Edge> pattern = SubgraphPatternMatcher.pathPattern(1);
        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, pattern).build();
        assertEquals(1, m.findMatches().getMatchCount());
    }

    // ── Isolated nodes in target ────────────────────────────────

    @Test
    public void testIsolatedNodesIgnored() {
        Graph<String, Edge> target = makeTriangle("A", "B", "C");
        target.addVertex("D");
        target.addVertex("E");
        SubgraphPatternMatcher m = new SubgraphPatternMatcher.Builder(
                target, SubgraphPatternMatcher.trianglePattern()).build();
        assertEquals(1, m.findMatches().getMatchCount());
    }

    // ── Path pattern validation ─────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testPathPatternMinLength() {
        SubgraphPatternMatcher.pathPattern(0);
    }
}
