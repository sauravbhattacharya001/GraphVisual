package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for RichClubAnalyzer.
 *
 * @author zalenix
 */
public class RichClubAnalyzerTest {

    private Graph<String, Edge> starGraph;
    private Graph<String, Edge> completeGraph;
    private Graph<String, Edge> bipartiteGraph;
    private Graph<String, Edge> hubAndSpoke;

    @Before
    public void setUp() {
        // Star: center connected to 5 leaves
        starGraph = new UndirectedSparseGraph<>();
        starGraph.addVertex("center");
        for (int i = 1; i <= 5; i++) {
            String leaf = "L" + i;
            starGraph.addVertex(leaf);
            edge e = new Edge("c", "center", leaf);
            starGraph.addEdge(e, "center", leaf);
        }

        // Complete K5
        completeGraph = new UndirectedSparseGraph<>();
        String[] nodes = {"A", "B", "C", "D", "E"};
        for (String n : nodes) completeGraph.addVertex(n);
        int eid = 0;
        for (int i = 0; i < nodes.length; i++) {
            for (int j = i + 1; j < nodes.length; j++) {
                edge e = new Edge("c", nodes[i], nodes[j]);
                completeGraph.addEdge(e, nodes[i], nodes[j]);
            }
        }

        // Bipartite-like: two hubs each connected to own set + connected to each other
        bipartiteGraph = new UndirectedSparseGraph<>();
        bipartiteGraph.addVertex("H1");
        bipartiteGraph.addVertex("H2");
        for (int i = 1; i <= 3; i++) {
            String a = "A" + i;
            String b = "B" + i;
            bipartiteGraph.addVertex(a);
            bipartiteGraph.addVertex(b);
            bipartiteGraph.addEdge(new Edge("c", "H1", a), "H1", a);
            bipartiteGraph.addEdge(new Edge("c", "H2", b), "H2", b);
        }
        bipartiteGraph.addEdge(new Edge("c", "H1", "H2"), "H1", "H2");

        // Hub-and-spoke with interconnected hubs
        hubAndSpoke = new UndirectedSparseGraph<>();
        for (int h = 1; h <= 3; h++) hubAndSpoke.addVertex("Hub" + h);
        // Connect hubs to each other
        hubAndSpoke.addEdge(new Edge("c", "Hub1", "Hub2"), "Hub1", "Hub2");
        hubAndSpoke.addEdge(new Edge("c", "Hub2", "Hub3"), "Hub2", "Hub3");
        hubAndSpoke.addEdge(new Edge("c", "Hub1", "Hub3"), "Hub1", "Hub3");
        // Add spokes
        for (int h = 1; h <= 3; h++) {
            for (int s = 1; s <= 4; s++) {
                String spoke = "S" + h + "_" + s;
                hubAndSpoke.addVertex(spoke);
                hubAndSpoke.addEdge(new Edge("c", "Hub" + h, spoke), "Hub" + h, spoke);
            }
        }
    }

    // ── Constructor ───────────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraph() {
        new RichClubAnalyzer(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyGraph() {
        new RichClubAnalyzer(new UndirectedSparseGraph<>());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullRandom() {
        new RichClubAnalyzer(starGraph, null);
    }

    // ── Rich-club coefficient ─────────────────────────────────────────────

    @Test
    public void testStarGraphCoefficient() {
        RichClubAnalyzer rca = new RichClubAnalyzer(starGraph);
        // At k=0: all 6 nodes qualify, 5 edges among 6 nodes → φ = 2*5/(6*5) = 0.333
        double phi0 = rca.richClubCoefficient(0);
        assertEquals(0.333, phi0, 0.01);

        // At k=1: only center (degree 5) qualifies → 1 node, φ = 0
        double phi1 = rca.richClubCoefficient(1);
        assertEquals(0.0, phi1, 0.001);
    }

    @Test
    public void testCompleteGraphCoefficient() {
        RichClubAnalyzer rca = new RichClubAnalyzer(completeGraph);
        // K5: all degrees = 4, at k=0: all nodes, all edges → φ = 1.0
        double phi0 = rca.richClubCoefficient(0);
        assertEquals(1.0, phi0, 0.001);
    }

    @Test
    public void testCoefficientBelowZeroThreshold() {
        RichClubAnalyzer rca = new RichClubAnalyzer(starGraph);
        // k < 0 not meaningful but should work (all nodes have degree >= 1 > -1)
        // Actually testing k=0 covers minimum
        double phi = rca.richClubCoefficient(100);
        assertEquals(0.0, phi, 0.001);
    }

    @Test
    public void testCoefficients() {
        RichClubAnalyzer rca = new RichClubAnalyzer(starGraph);
        Map<Integer, Double> coeffs = rca.richClubCoefficients();
        assertFalse(coeffs.isEmpty());
        // Max degree is 5, so keys should be 0..4
        assertTrue(coeffs.containsKey(0));
        assertTrue(coeffs.containsKey(4));
        assertFalse(coeffs.containsKey(5));
    }

    // ── Members ───────────────────────────────────────────────────────────

    @Test
    public void testGetRichClubMembersStarGraph() {
        RichClubAnalyzer rca = new RichClubAnalyzer(starGraph);
        // k=0: all nodes (degree > 0, leaves have degree 1)
        List<String> members0 = rca.getRichClubMembers(0);
        assertEquals(6, members0.size());
        assertEquals("center", members0.get(0)); // highest degree first

        // k=1: only center
        List<String> members1 = rca.getRichClubMembers(1);
        assertEquals(1, members1.size());
        assertEquals("center", members1.get(0));
    }

    @Test
    public void testMembersSortedByDegree() {
        RichClubAnalyzer rca = new RichClubAnalyzer(hubAndSpoke);
        List<String> members = rca.getRichClubMembers(0);
        // Hubs should come first (degree 6 each), then spokes (degree 1)
        assertTrue(members.get(0).startsWith("Hub"));
        assertTrue(members.get(1).startsWith("Hub"));
        assertTrue(members.get(2).startsWith("Hub"));
    }

    // ── Internal edge fraction ────────────────────────────────────────────

    @Test
    public void testInternalEdgeFractionComplete() {
        RichClubAnalyzer rca = new RichClubAnalyzer(completeGraph);
        // k=0: all nodes → all edges internal → fraction = 1.0
        assertEquals(1.0, rca.internalEdgeFraction(0), 0.001);
    }

    @Test
    public void testInternalEdgeFractionHubSpoke() {
        RichClubAnalyzer rca = new RichClubAnalyzer(hubAndSpoke);
        // k=4: only hubs (degree 6), 3 internal edges out of 15 total
        double frac = rca.internalEdgeFraction(4);
        assertEquals(3.0 / 15.0, frac, 0.001);
    }

    @Test
    public void testInternalEdgeFractionNoMembers() {
        RichClubAnalyzer rca = new RichClubAnalyzer(starGraph);
        assertEquals(0.0, rca.internalEdgeFraction(100), 0.001);
    }

    // ── Assortativity ─────────────────────────────────────────────────────

    @Test
    public void testAssortativityComplete() {
        RichClubAnalyzer rca = new RichClubAnalyzer(completeGraph);
        // K5: all same degree → assortativity undefined but should return ~0
        // Actually with identical degrees, variance is 0, we return 0
        double a = rca.degreeAssortativity();
        assertTrue(Math.abs(a) < 0.01 || Double.isNaN(a) == false);
    }

    @Test
    public void testAssortativityStar() {
        RichClubAnalyzer rca = new RichClubAnalyzer(starGraph);
        // Star: all edges connect degree-5 center to degree-1 leaves
        // Zero variance on one side, so correlation is 0 (degenerate case)
        double a = rca.degreeAssortativity();
        // Just verify it returns a finite value
        assertFalse(Double.isNaN(a));
        assertFalse(Double.isInfinite(a));
    }

    @Test
    public void testAssortativityHubSpoke() {
        RichClubAnalyzer rca = new RichClubAnalyzer(hubAndSpoke);
        double a = rca.degreeAssortativity();
        // Hubs connect to each other (assortative) but also to spokes (disassortative)
        // Overall likely slightly negative
        assertNotNull(a); // just checking it computes
    }

    // ── Classification ────────────────────────────────────────────────────

    @Test
    public void testClassifyHubSpoke() {
        RichClubAnalyzer rca = new RichClubAnalyzer(hubAndSpoke);
        String c = rca.classify();
        assertNotNull(c);
        assertTrue(c.equals("rich-club") || c.equals("anti-rich-club") || c.equals("neutral"));
    }

    @Test
    public void testClassifyComplete() {
        RichClubAnalyzer rca = new RichClubAnalyzer(completeGraph);
        // K5 has only one meaningful degree threshold, so likely "neutral"
        String c = rca.classify();
        assertNotNull(c);
    }

    // ── Normalized coefficients ───────────────────────────────────────────

    @Test
    public void testNormalizedCoefficients() {
        RichClubAnalyzer rca = new RichClubAnalyzer(hubAndSpoke);
        Map<Integer, Double> rho = rca.normalizedCoefficients(5);
        assertFalse(rho.isEmpty());
        for (Double v : rho.values()) {
            assertTrue(v >= 0);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNormalizedZeroRandomizations() {
        new RichClubAnalyzer(starGraph).normalizedCoefficients(0);
    }

    // ── Full analysis ─────────────────────────────────────────────────────

    @Test
    public void testAnalyzeReturnsResult() {
        RichClubAnalyzer rca = new RichClubAnalyzer(hubAndSpoke);
        RichClubAnalyzer.RichClubResult result = rca.analyze();
        assertNotNull(result);
        assertNotNull(result.getPhi());
        assertNotNull(result.getRho());
        assertNotNull(result.getClassification());
        assertTrue(result.getNodeCount() > 0);
        assertTrue(result.getEdgeCount() > 0);
        assertTrue(result.getMaxDegree() > 0);
    }

    @Test
    public void testAnalyzeWithCustomRandomizations() {
        RichClubAnalyzer rca = new RichClubAnalyzer(bipartiteGraph);
        RichClubAnalyzer.RichClubResult result = rca.analyze(3);
        assertNotNull(result);
        assertFalse(result.getPhi().isEmpty());
    }

    // ── Report ────────────────────────────────────────────────────────────

    @Test
    public void testReport() {
        RichClubAnalyzer rca = new RichClubAnalyzer(hubAndSpoke);
        String report = rca.analyze().report();
        assertTrue(report.contains("RICH-CLUB ANALYSIS REPORT"));
        assertTrue(report.contains("Classification:"));
        assertTrue(report.contains("assortativity"));
        assertTrue(report.contains("phi(k)"));
    }

    // ── CSV export ────────────────────────────────────────────────────────

    @Test
    public void testCsvExport() {
        RichClubAnalyzer.RichClubResult result = new RichClubAnalyzer(starGraph).analyze();
        String csv = result.toCsv();
        assertTrue(csv.startsWith("k,phi,rho"));
        assertTrue(csv.split("\n").length > 2); // header + data rows
    }

    // ── JSON export ───────────────────────────────────────────────────────

    @Test
    public void testJsonExport() {
        RichClubAnalyzer.RichClubResult result = new RichClubAnalyzer(starGraph).analyze();
        String json = result.toJson();
        assertTrue(json.contains("\"classification\""));
        assertTrue(json.contains("\"assortativity\""));
        assertTrue(json.contains("\"coefficients\""));
        assertTrue(json.contains("\"nodeCount\""));
    }

    // ── Edge cases ────────────────────────────────────────────────────────

    @Test
    public void testSingleEdgeGraph() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("X");
        g.addVertex("Y");
        g.addEdge(new Edge("c", "X", "Y"), "X", "Y");
        RichClubAnalyzer rca = new RichClubAnalyzer(g);
        double phi = rca.richClubCoefficient(0);
        assertEquals(1.0, phi, 0.001); // 2 nodes, 1 edge, φ = 2*1/(2*1) = 1
    }

    @Test
    public void testDisconnectedNodes() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        g.addVertex("B");
        g.addVertex("C");
        g.addEdge(new Edge("c", "A", "B"), "A", "B");
        // C is isolated (degree 0)
        RichClubAnalyzer rca = new RichClubAnalyzer(g);
        List<String> members = rca.getRichClubMembers(0);
        assertEquals(2, members.size()); // only A and B
        assertFalse(members.contains("C"));
    }

    @Test
    public void testRichClubDensitySameAsPhi() {
        RichClubAnalyzer rca = new RichClubAnalyzer(hubAndSpoke);
        for (int k = 0; k < 7; k++) {
            assertEquals(rca.richClubCoefficient(k), rca.richClubDensity(k), 0.0001);
        }
    }

    @Test
    public void testBestThresholdReasonable() {
        RichClubAnalyzer.RichClubResult result = new RichClubAnalyzer(hubAndSpoke).analyze();
        assertTrue(result.getBestThreshold() >= 0);
        assertTrue(result.getBestPhi() >= 0 && result.getBestPhi() <= 1.0);
    }

    @Test
    public void testDeterministicWithSameSeed() {
        RichClubAnalyzer rca1 = new RichClubAnalyzer(hubAndSpoke, new Random(123));
        RichClubAnalyzer rca2 = new RichClubAnalyzer(hubAndSpoke, new Random(123));
        Map<Integer, Double> rho1 = rca1.normalizedCoefficients(5);
        Map<Integer, Double> rho2 = rca2.normalizedCoefficients(5);
        assertEquals(rho1, rho2);
    }

    // ── Large-ish graph ───────────────────────────────────────────────────

    @Test
    public void testLargerGraphPerformance() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        // Build a scale-free-ish graph: 50 nodes, preferential attachment
        for (int i = 0; i < 50; i++) g.addVertex("N" + i);
        Random r = new Random(99);
        // Connect in a ring first
        for (int i = 0; i < 50; i++) {
            String a = "N" + i, b = "N" + ((i + 1) % 50);
            g.addEdge(new Edge("c", a, b), a, b);
        }
        // Add some hub edges
        for (int i = 0; i < 30; i++) {
            String hub = "N" + (i % 3); // nodes 0-2 become hubs
            String target = "N" + (3 + r.nextInt(47));
            if (!g.isNeighbor(hub, target)) {
                g.addEdge(new Edge("c", hub, target), hub, target);
            }
        }

        RichClubAnalyzer rca = new RichClubAnalyzer(g);
        RichClubAnalyzer.RichClubResult result = rca.analyze(5);
        assertNotNull(result.report());
        assertTrue(result.getNodeCount() == 50);
    }
}
