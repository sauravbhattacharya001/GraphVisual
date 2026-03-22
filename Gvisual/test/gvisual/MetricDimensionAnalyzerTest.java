package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/**
 * Tests for MetricDimensionAnalyzer — 55 tests covering exact and greedy
 * metric dimension, distance matrices, twin detection, resolving sets,
 * metric representation, bounds, and edge cases.
 */
public class MetricDimensionAnalyzerTest {

    // ── Graph builders ──────────────────────────────────────────────

    private Graph<String, Edge> makeGraph(String[][] edges) {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        for (String[] e : edges) {
            g.addVertex(e[0]);
            g.addVertex(e[1]);
            Edge ed = new Edge("e", e[0], e[1]);
            ed.setLabel(e[0] + "-" + e[1]);
            g.addEdge(ed, e[0], e[1]);
        }
        return g;
    }

    private Graph<String, Edge> emptyGraph() {
        return new UndirectedSparseGraph<String, Edge>();
    }

    private Graph<String, Edge> singleVertex() {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        g.addVertex("A");
        return g;
    }

    private Graph<String, Edge> path(String... nodes) {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        for (String v : nodes) g.addVertex(v);
        for (int i = 0; i < nodes.length - 1; i++) {
            Edge e = new Edge("e", nodes[i], nodes[i + 1]);
            e.setLabel(nodes[i] + "-" + nodes[i + 1]);
            g.addEdge(e, nodes[i], nodes[i + 1]);
        }
        return g;
    }

    private Graph<String, Edge> cycle(int n) {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        String[] v = new String[n];
        for (int i = 0; i < n; i++) {
            v[i] = String.valueOf((char) ('A' + i));
            g.addVertex(v[i]);
        }
        for (int i = 0; i < n; i++) {
            String a = v[i], b = v[(i + 1) % n];
            Edge e = new Edge("e", a, b);
            e.setLabel(a + "-" + b);
            g.addEdge(e, a, b);
        }
        return g;
    }

    private Graph<String, Edge> complete(int n) {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        String[] v = new String[n];
        for (int i = 0; i < n; i++) {
            v[i] = String.valueOf((char) ('A' + i));
            g.addVertex(v[i]);
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Edge e = new Edge("e", v[i], v[j]);
                e.setLabel(v[i] + "-" + v[j]);
                g.addEdge(e, v[i], v[j]);
            }
        }
        return g;
    }

    private Graph<String, Edge> star(int leaves) {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        g.addVertex("C");
        for (int i = 0; i < leaves; i++) {
            String v = "L" + i;
            g.addVertex(v);
            Edge e = new Edge("e", "C", v);
            e.setLabel("C-" + v);
            g.addEdge(e, "C", v);
        }
        return g;
    }

    private Graph<String, Edge> petersen() {
        // Petersen graph: outer cycle 0-4, inner pentagram 5-9
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        for (int i = 0; i < 10; i++) g.addVertex("V" + i);
        // Outer cycle: 0-1-2-3-4-0
        int[][] outer = {{0,1},{1,2},{2,3},{3,4},{4,0}};
        for (int[] e : outer) {
            Edge ed = new Edge("e", "V" + e[0], "V" + e[1]);
            ed.setLabel("V" + e[0] + "-V" + e[1]);
            g.addEdge(ed, "V" + e[0], "V" + e[1]);
        }
        // Inner pentagram: 5-7, 7-9, 9-6, 6-8, 8-5
        int[][] inner = {{5,7},{7,9},{9,6},{6,8},{8,5}};
        for (int[] e : inner) {
            Edge ed = new Edge("e", "V" + e[0], "V" + e[1]);
            ed.setLabel("V" + e[0] + "-V" + e[1]);
            g.addEdge(ed, "V" + e[0], "V" + e[1]);
        }
        // Spokes: 0-5, 1-6, 2-7, 3-8, 4-9
        int[][] spokes = {{0,5},{1,6},{2,7},{3,8},{4,9}};
        for (int[] e : spokes) {
            Edge ed = new Edge("e", "V" + e[0], "V" + e[1]);
            ed.setLabel("V" + e[0] + "-V" + e[1]);
            g.addEdge(ed, "V" + e[0], "V" + e[1]);
        }
        return g;
    }

    // ── Empty and trivial graphs ────────────────────────────────────

    @Test
    public void testEmptyGraph() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(emptyGraph());
        assertEquals(0, a.getMetricDimension());
        assertTrue(a.getOptimalResolvingSet().isEmpty());
        assertTrue(a.getGreedyResolvingSet().isEmpty());
    }

    @Test
    public void testSingleVertex() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(singleVertex());
        assertEquals(0, a.getMetricDimension());
        assertTrue(a.getOptimalResolvingSet().isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraph() {
        new MetricDimensionAnalyzer(null);
    }

    // ── Paths: β(Pn) = 1 for n ≥ 2 ────────────────────────────────

    @Test
    public void testPath2() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(
            path("A", "B"));
        assertEquals(1, a.getMetricDimension());
    }

    @Test
    public void testPath3() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(
            path("A", "B", "C"));
        assertEquals(1, a.getMetricDimension());
    }

    @Test
    public void testPath5() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(
            path("A", "B", "C", "D", "E"));
        assertEquals(1, a.getMetricDimension());
    }

    // ── Cycles: β(Cn) = 2 for n ≥ 3 ───────────────────────────────

    @Test
    public void testCycle3() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(cycle(3));
        assertEquals(2, a.getMetricDimension());
    }

    @Test
    public void testCycle4() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(cycle(4));
        assertEquals(2, a.getMetricDimension());
    }

    @Test
    public void testCycle5() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(cycle(5));
        assertEquals(2, a.getMetricDimension());
    }

    @Test
    public void testCycle6() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(cycle(6));
        assertEquals(2, a.getMetricDimension());
    }

    // ── Complete graphs: β(Kn) = n − 1 ────────────────────────────

    @Test
    public void testK2() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(complete(2));
        assertEquals(1, a.getMetricDimension());
    }

    @Test
    public void testK3() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(complete(3));
        assertEquals(2, a.getMetricDimension());
    }

    @Test
    public void testK4() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(complete(4));
        assertEquals(3, a.getMetricDimension());
    }

    @Test
    public void testK5() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(complete(5));
        assertEquals(4, a.getMetricDimension());
    }

    // ── Stars: β(K1,n) = n − 1 ────────────────────────────────────

    @Test
    public void testStar3() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(star(3));
        assertEquals(2, a.getMetricDimension());
    }

    @Test
    public void testStar4() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(star(4));
        assertEquals(3, a.getMetricDimension());
    }

    // ── Petersen graph: β = 3 ──────────────────────────────────────

    @Test
    public void testPetersen() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(petersen());
        assertEquals(3, a.getMetricDimension());
    }

    // ── Distance matrix ────────────────────────────────────────────

    @Test
    public void testDistanceMatrixPath() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(
            path("A", "B", "C"));
        int[][] d = a.getDistanceMatrix();
        List<String> order = a.getVertexOrder();
        int ai = order.indexOf("A"), bi = order.indexOf("B"), ci = order.indexOf("C");
        assertEquals(0, d[ai][ai]);
        assertEquals(1, d[ai][bi]);
        assertEquals(2, d[ai][ci]);
        assertEquals(1, d[bi][ci]);
    }

    @Test
    public void testDistanceMatrixDisconnected() {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        g.addVertex("A");
        g.addVertex("B");
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(g);
        int[][] d = a.getDistanceMatrix();
        List<String> order = a.getVertexOrder();
        int ai = order.indexOf("A"), bi = order.indexOf("B");
        assertEquals(-1, d[ai][bi]);
    }

    @Test
    public void testDistanceMatrixSymmetric() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(cycle(5));
        int[][] d = a.getDistanceMatrix();
        for (int i = 0; i < d.length; i++) {
            for (int j = 0; j < d.length; j++) {
                assertEquals(d[i][j], d[j][i]);
            }
        }
    }

    // ── Resolving set validation ───────────────────────────────────

    @Test
    public void testIsResolvingSetValid() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(
            path("A", "B", "C", "D"));
        // An endpoint resolves all pairs on a path
        assertTrue(a.isResolvingSet(Arrays.asList("A")));
        assertTrue(a.isResolvingSet(Arrays.asList("D")));
    }

    @Test
    public void testIsResolvingSetInvalid() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(complete(4));
        // A single vertex can't resolve K4 (all non-chosen vertices
        // are at distance 1)
        assertFalse(a.isResolvingSet(Arrays.asList("A")));
    }

    @Test
    public void testIsResolvingSetEmpty() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(
            path("A", "B"));
        assertFalse(a.isResolvingSet(Collections.<String>emptyList()));
    }

    @Test
    public void testIsResolvingSetOptimal() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(cycle(5));
        List<String> opt = a.getOptimalResolvingSet();
        assertTrue(a.isResolvingSet(opt));
    }

    @Test
    public void testIsResolvingSetBadVertex() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(
            path("A", "B"));
        assertFalse(a.isResolvingSet(Arrays.asList("Z")));
    }

    // ── Metric representation ──────────────────────────────────────

    @Test
    public void testMetricRepresentation() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(
            path("A", "B", "C", "D"));
        List<String> rs = Arrays.asList("A");
        Map<String, int[]> repr = a.getMetricRepresentation(rs);
        assertArrayEquals(new int[]{0}, repr.get("A"));
        assertArrayEquals(new int[]{1}, repr.get("B"));
        assertArrayEquals(new int[]{2}, repr.get("C"));
        assertArrayEquals(new int[]{3}, repr.get("D"));
    }

    @Test
    public void testMetricRepresentationUnique() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(cycle(5));
        List<String> opt = a.getOptimalResolvingSet();
        Map<String, int[]> repr = a.getMetricRepresentation(opt);
        // All vectors should be unique
        Set<String> seen = new HashSet<String>();
        for (int[] vec : repr.values()) {
            assertTrue(seen.add(Arrays.toString(vec)));
        }
    }

    // ── Vertex identification ──────────────────────────────────────

    @Test
    public void testIdentifyVertex() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(
            path("A", "B", "C", "D"));
        List<String> rs = Arrays.asList("A");
        assertEquals("C", a.identifyVertex(rs, new int[]{2}));
        assertEquals("A", a.identifyVertex(rs, new int[]{0}));
        assertNull(a.identifyVertex(rs, new int[]{99}));
    }

    // ── Twin detection ─────────────────────────────────────────────

    @Test
    public void testTwinsInComplete() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(complete(4));
        List<Set<String>> twins = a.getTwinClasses();
        // In K4, all vertices are closed twins (same closed nbr)
        assertEquals(1, twins.size());
        assertEquals(4, twins.get(0).size());
    }

    @Test
    public void testTwinsInPath() {
        // Path A-B-C: A and C are open twins (both have N={B}, not adjacent)
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(
            path("A", "B", "C"));
        List<Set<String>> twins = a.getTwinClasses();
        assertEquals(1, twins.size());
        assertTrue(twins.get(0).contains("A"));
        assertTrue(twins.get(0).contains("C"));
    }

    @Test
    public void testTwinsInStar() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(star(3));
        // Leaves of a star are open twins
        List<Set<String>> twins = a.getTwinClasses();
        assertEquals(1, twins.size());
        assertEquals(3, twins.get(0).size());
        assertFalse(twins.get(0).contains("C")); // center not a twin
    }

    @Test
    public void testAreTwins() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(star(3));
        assertTrue(a.areTwins("L0", "L1"));
        assertTrue(a.areTwins("L1", "L2"));
        assertFalse(a.areTwins("C", "L0"));
    }

    // ── Bounds ─────────────────────────────────────────────────────

    @Test
    public void testLowerBound() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(cycle(5));
        int lb = a.getLowerBound();
        assertTrue(lb >= 1);
        assertTrue(lb <= a.getMetricDimension());
    }

    @Test
    public void testUpperBound() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(cycle(5));
        int ub = a.getUpperBound();
        assertTrue(ub >= a.getMetricDimension());
    }

    @Test
    public void testBoundsConsistent() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(complete(5));
        assertTrue(a.getLowerBound() <= a.getMetricDimension());
        assertTrue(a.getUpperBound() >= a.getMetricDimension());
    }

    // ── Greedy resolving set ───────────────────────────────────────

    @Test
    public void testGreedyIsResolving() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(petersen());
        List<String> greedy = a.getGreedyResolvingSet();
        assertTrue(a.isResolvingSet(greedy));
    }

    @Test
    public void testGreedySizeBound() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(cycle(6));
        List<String> greedy = a.getGreedyResolvingSet();
        assertTrue(greedy.size() >= a.getMetricDimension());
    }

    @Test
    public void testGreedyDeterministic() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(
            path("A", "B", "C"));
        List<String> g1 = a.getGreedyResolvingSet();
        List<String> g2 = a.getGreedyResolvingSet();
        assertEquals(g1, g2);
    }

    // ── Resolving power ────────────────────────────────────────────

    @Test
    public void testResolvingPower() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(
            path("A", "B", "C"));
        Map<String, Integer> power = a.getResolvingPower();
        assertEquals(3, power.size());
        // Endpoints of a path resolve all pairs
        assertTrue(power.get("A") > 0);
        assertTrue(power.get("C") > 0);
    }

    @Test
    public void testResolvingPowerSymmetry() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(cycle(4));
        Map<String, Integer> power = a.getResolvingPower();
        // All vertices in a cycle have equal resolving power
        int first = power.values().iterator().next();
        for (int p : power.values()) {
            assertEquals(first, p);
        }
    }

    // ── Resolving pairs ────────────────────────────────────────────

    @Test
    public void testResolvingPairs() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(
            path("A", "B", "C"));
        List<String[]> pairs = a.getResolvingPairs("A");
        assertTrue(pairs.size() > 0);
        // A resolves (B,C) since d(B,A)=1 ≠ d(C,A)=2
        boolean found = false;
        for (String[] p : pairs) {
            if ((p[0].equals("B") && p[1].equals("C")) ||
                (p[0].equals("C") && p[1].equals("B"))) {
                found = true;
            }
        }
        assertTrue(found);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testResolvingPairsInvalidVertex() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(
            path("A", "B"));
        a.getResolvingPairs("Z");
    }

    // ── Summary ────────────────────────────────────────────────────

    @Test
    public void testSummaryNotEmpty() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(cycle(5));
        String summary = a.getSummary();
        assertFalse(summary.isEmpty());
        assertTrue(summary.contains("Metric Dimension"));
        assertTrue(summary.contains("Lower bound"));
        assertTrue(summary.contains("Upper bound"));
    }

    @Test
    public void testSummaryIncludesExact() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(
            path("A", "B", "C"));
        String summary = a.getSummary();
        assertTrue(summary.contains("exact"));
    }

    @Test
    public void testSummaryEmptyGraph() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(emptyGraph());
        String summary = a.getSummary();
        assertTrue(summary.contains("Empty graph"));
    }

    // ── Edge cases ─────────────────────────────────────────────────

    @Test
    public void testDisconnectedGraph() {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        g.addVertex("A");
        g.addVertex("B");
        g.addVertex("C");
        Edge e = new Edge("e", "A", "B");
        e.setLabel("A-B");
        g.addEdge(e, "A", "B");
        // C is isolated — disconnected graph
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(g);
        // Should still compute greedy
        List<String> greedy = a.getGreedyResolvingSet();
        assertTrue(a.isResolvingSet(greedy));
    }

    @Test
    public void testTwoIsolatedVertices() {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        g.addVertex("A");
        g.addVertex("B");
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(g);
        // Two isolated vertices are open twins (both have empty nbrs),
        // but d(A,B)=-1 so they have distinct distance vectors to each other
        List<String> greedy = a.getGreedyResolvingSet();
        assertTrue(a.isResolvingSet(greedy));
    }

    @Test
    public void testVertexOrder() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(
            path("C", "A", "B"));
        List<String> order = a.getVertexOrder();
        // Sorted alphabetically
        assertEquals(Arrays.asList("A", "B", "C"), order);
    }

    @Test
    public void testOptimalEqualsGreedyOnPath() {
        MetricDimensionAnalyzer a = new MetricDimensionAnalyzer(
            path("A", "B", "C", "D", "E"));
        // Both should find a set of size 1
        assertEquals(a.getMetricDimension(), a.getGreedyResolvingSet().size());
    }
}
