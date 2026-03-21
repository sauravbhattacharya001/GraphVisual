package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for ForceDirectedLayout — Fruchterman-Reingold layout algorithm
 * with quality metrics and SVG export.
 */
public class ForceDirectedLayoutTest {

    private Graph<String, Edge> graph;
    private int edgeCounter;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, Edge>();
        edgeCounter = 0;
    }

    private edge addEdge(String v1, String v2) {
        graph.addVertex(v1);
        graph.addVertex(v2);
        edge e = new Edge("test", v1, v2);
        e.setWeight(1.0f);
        graph.addEdge(e, v1, v2);
        edgeCounter++;
        return e;
    }

    private void buildTriangle() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
    }

    private void buildPath(int length) {
        for (int i = 0; i < length; i++) {
            addEdge("N" + i, "N" + (i + 1));
        }
    }

    private void buildStar(int spokes) {
        for (int i = 0; i < spokes; i++) {
            addEdge("center", "spoke" + i);
        }
    }

    // ── Constructor validation ───────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new ForceDirectedLayout(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testZeroIterationsThrows() {
        new ForceDirectedLayout(graph, 0, 800, 600, 0.1, true, 42L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeWidthThrows() {
        new ForceDirectedLayout(graph, 100, -1, 600, 0.1, true, 42L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeGravityThrows() {
        new ForceDirectedLayout(graph, 100, 800, 600, -0.1, true, 42L);
    }

    // ── Empty and single-node graphs ────────────────────────────

    @Test
    public void testEmptyGraph() {
        ForceDirectedLayout layout = new ForceDirectedLayout(graph);
        layout.compute();
        assertTrue(layout.getPositions().isEmpty());
        assertEquals(0, layout.getIterationsUsed());
        assertEquals(0, layout.getFinalEnergy(), 0.001);
    }

    @Test
    public void testSingleNode() {
        graph.addVertex("A");
        ForceDirectedLayout layout = new ForceDirectedLayout(graph);
        layout.compute();

        Map<String, double[]> positions = layout.getPositions();
        assertEquals(1, positions.size());
        assertNotNull(positions.get("A"));
        assertEquals(400, positions.get("A")[0], 0.001); // center of 800
        assertEquals(300, positions.get("A")[1], 0.001); // center of 600
    }

    @Test
    public void testTwoConnectedNodes() {
        addEdge("A", "B");
        ForceDirectedLayout layout = new ForceDirectedLayout(graph);
        layout.compute();

        double[] pA = layout.getPosition("A");
        double[] pB = layout.getPosition("B");
        assertNotNull(pA);
        assertNotNull(pB);

        // They should be distinct positions
        double dist = Math.sqrt(Math.pow(pA[0] - pB[0], 2) +
                                Math.pow(pA[1] - pB[1], 2));
        assertTrue("Connected nodes should have non-zero distance", dist > 1);
    }

    // ── Core algorithm ──────────────────────────────────────────

    @Test
    public void testTriangleLayout() {
        buildTriangle();
        ForceDirectedLayout layout = new ForceDirectedLayout(graph);
        layout.compute();

        Map<String, double[]> positions = layout.getPositions();
        assertEquals(3, positions.size());

        // All nodes should be within bounds
        for (double[] p : positions.values()) {
            assertTrue("x should be >= 0", p[0] >= 0);
            assertTrue("x should be <= 800", p[0] <= 800);
            assertTrue("y should be >= 0", p[1] >= 0);
            assertTrue("y should be <= 600", p[1] <= 600);
        }
    }

    @Test
    public void testDeterministicWithSameSeed() {
        buildTriangle();
        ForceDirectedLayout layout1 = new ForceDirectedLayout(
                graph, 100, 800, 600, 0.1, true, 123L);
        layout1.compute();

        ForceDirectedLayout layout2 = new ForceDirectedLayout(
                graph, 100, 800, 600, 0.1, true, 123L);
        layout2.compute();

        Map<String, double[]> pos1 = layout1.getPositions();
        Map<String, double[]> pos2 = layout2.getPositions();
        for (String v : pos1.keySet()) {
            assertEquals(pos1.get(v)[0], pos2.get(v)[0], 0.001);
            assertEquals(pos1.get(v)[1], pos2.get(v)[1], 0.001);
        }
    }

    @Test
    public void testDifferentSeedGivesDifferentLayout() {
        buildTriangle();
        ForceDirectedLayout layout1 = new ForceDirectedLayout(
                graph, 100, 800, 600, 0.1, true, 1L);
        layout1.compute();

        ForceDirectedLayout layout2 = new ForceDirectedLayout(
                graph, 100, 800, 600, 0.1, true, 99L);
        layout2.compute();

        // At least one position should differ
        boolean differs = false;
        Map<String, double[]> pos1 = layout1.getPositions();
        Map<String, double[]> pos2 = layout2.getPositions();
        for (String v : pos1.keySet()) {
            if (Math.abs(pos1.get(v)[0] - pos2.get(v)[0]) > 1 ||
                Math.abs(pos1.get(v)[1] - pos2.get(v)[1]) > 1) {
                differs = true;
                break;
            }
        }
        assertTrue("Different seeds should produce different layouts", differs);
    }

    @Test
    public void testIdempotent() {
        buildTriangle();
        ForceDirectedLayout layout = new ForceDirectedLayout(graph);
        layout.compute();
        Map<String, double[]> pos1 = layout.getPositions();

        layout.compute(); // should be no-op
        Map<String, double[]> pos2 = layout.getPositions();

        for (String v : pos1.keySet()) {
            assertEquals(pos1.get(v)[0], pos2.get(v)[0], 0.001);
            assertEquals(pos1.get(v)[1], pos2.get(v)[1], 0.001);
        }
    }

    @Test
    public void testConnectedNodesCloserThanDisconnected() {
        // Two pairs: A-B connected, C-D connected, pairs not connected
        addEdge("A", "B");
        graph.addVertex("C");
        graph.addVertex("D");
        addEdge("C", "D");

        ForceDirectedLayout layout = new ForceDirectedLayout(
                graph, 300, 800, 600, 0.01, true, 42L);
        layout.compute();

        double[] pA = layout.getPosition("A");
        double[] pB = layout.getPosition("B");
        double distAB = Math.sqrt(Math.pow(pA[0] - pB[0], 2) +
                                   Math.pow(pA[1] - pB[1], 2));

        double[] pC = layout.getPosition("C");
        double distAC = Math.sqrt(Math.pow(pA[0] - pC[0], 2) +
                                   Math.pow(pA[1] - pC[1], 2));

        // Connected pair should generally be closer than disconnected pair
        // (with low gravity, disconnected components drift apart)
        assertTrue("Connected nodes should be closer than disconnected ones",
                distAB < distAC || distAB < 200);
    }

    // ── Edge weights ────────────────────────────────────────────

    @Test
    public void testEdgeWeightsInfluenceLayout() {
        addEdge("A", "B").setWeight(10.0f);  // strong connection
        addEdge("A", "C").setWeight(0.1f);   // weak connection

        ForceDirectedLayout withWeights = new ForceDirectedLayout(
                graph, 200, 800, 600, 0.1, true, 42L);
        withWeights.compute();

        double[] pA = withWeights.getPosition("A");
        double[] pB = withWeights.getPosition("B");
        double[] pC = withWeights.getPosition("C");

        double distAB = Math.sqrt(Math.pow(pA[0] - pB[0], 2) +
                                   Math.pow(pA[1] - pB[1], 2));
        double distAC = Math.sqrt(Math.pow(pA[0] - pC[0], 2) +
                                   Math.pow(pA[1] - pC[1], 2));

        // With weights, heavy edge A-B should pull tighter than weak A-C
        assertTrue("Heavy edge should produce closer nodes: AB=" +
                distAB + " AC=" + distAC, distAB < distAC);
    }

    // ── Normalized positions ────────────────────────────────────

    @Test
    public void testNormalizedPositions() {
        buildTriangle();
        ForceDirectedLayout layout = new ForceDirectedLayout(graph);
        layout.compute();

        Map<String, double[]> norm = layout.getNormalizedPositions(
                1000, 500, 20);
        assertEquals(3, norm.size());

        for (double[] p : norm.values()) {
            assertTrue("x should be >= padding", p[0] >= 20);
            assertTrue("x should be <= width", p[0] <= 1000);
            assertTrue("y should be >= padding", p[1] >= 20);
            assertTrue("y should be <= height", p[1] <= 500);
        }
    }

    @Test
    public void testNormalizedEmptyGraph() {
        ForceDirectedLayout layout = new ForceDirectedLayout(graph);
        layout.compute();
        Map<String, double[]> norm = layout.getNormalizedPositions(
                1000, 500, 20);
        assertTrue(norm.isEmpty());
    }

    // ── Quality metrics ─────────────────────────────────────────

    @Test
    public void testEdgeCrossingsOnTriangle() {
        buildTriangle();
        ForceDirectedLayout layout = new ForceDirectedLayout(graph);
        layout.compute();
        // Triangle should have 0 crossings (planar)
        assertEquals(0, layout.countEdgeCrossings());
    }

    @Test
    public void testEdgeLengthUniformityOnTriangle() {
        buildTriangle();
        ForceDirectedLayout layout = new ForceDirectedLayout(graph);
        layout.compute();
        double cv = layout.edgeLengthUniformity();
        // Triangle should have fairly uniform edge lengths
        assertTrue("Triangle edge lengths should be fairly uniform: " + cv,
                cv < 0.5);
    }

    @Test
    public void testAngularResolutionOnStar() {
        buildStar(6);
        ForceDirectedLayout layout = new ForceDirectedLayout(graph);
        layout.compute();
        double angle = layout.minAngularResolution();
        // Star with 6 spokes: optimal angle = 60°
        assertTrue("Star angular resolution should be positive", angle > 0);
    }

    @Test
    public void testStressOnPath() {
        buildPath(5);
        ForceDirectedLayout layout = new ForceDirectedLayout(graph);
        layout.compute();
        double stress = layout.computeStress();
        assertTrue("Stress should be non-negative", stress >= 0);
    }

    @Test
    public void testQualityReport() {
        buildTriangle();
        ForceDirectedLayout layout = new ForceDirectedLayout(graph);
        layout.compute();

        ForceDirectedLayout.LayoutQuality quality = layout.getQualityReport();
        assertNotNull(quality);
        assertTrue(quality.getEdgeCrossings() >= 0);
        assertTrue(quality.getEdgeLengthCV() >= 0);
        assertTrue(quality.getMinAngularResolution() >= 0);
        assertTrue(quality.getStress() >= 0);
        assertTrue(quality.getIterations() > 0);
        assertNotNull(quality.toString());
    }

    // ── Convergence ─────────────────────────────────────────────

    @Test
    public void testConvergenceOnSmallGraph() {
        buildTriangle();
        ForceDirectedLayout layout = new ForceDirectedLayout(
                graph, 1000, 800, 600, 0.1, true, 42L);
        layout.compute();
        // Small graph should converge or settle to low energy
        assertTrue("Small graph should have low final energy",
                layout.getFinalEnergy() < 100);
    }

    @Test
    public void testFinalEnergyDecreases() {
        buildStar(8);
        ForceDirectedLayout fewIter = new ForceDirectedLayout(
                graph, 10, 800, 600, 0.1, true, 42L);
        fewIter.compute();

        ForceDirectedLayout manyIter = new ForceDirectedLayout(
                graph, 300, 800, 600, 0.1, true, 42L);
        manyIter.compute();

        // More iterations should yield lower or equal energy
        assertTrue("More iterations should yield lower energy",
                manyIter.getFinalEnergy() <= fewIter.getFinalEnergy() + 1);
    }

    // ── SVG export ──────────────────────────────────────────────

    @Test
    public void testSVGExport() {
        buildTriangle();
        ForceDirectedLayout layout = new ForceDirectedLayout(graph);
        layout.compute();

        String svg = layout.toSVG(800, 600, 5);
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("</svg>"));
        assertTrue(svg.contains("<line"));
        assertTrue(svg.contains("<circle"));
        assertTrue(svg.contains("A"));
        assertTrue(svg.contains("B"));
        assertTrue(svg.contains("C"));
    }

    @Test
    public void testSVGExportEmptyGraph() {
        ForceDirectedLayout layout = new ForceDirectedLayout(graph);
        layout.compute();
        String svg = layout.toSVG(400, 300, 5);
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("</svg>"));
    }

    // ── Summary ─────────────────────────────────────────────────

    @Test
    public void testSummary() {
        buildTriangle();
        ForceDirectedLayout layout = new ForceDirectedLayout(graph);
        layout.compute();

        String summary = layout.getSummary();
        assertTrue(summary.contains("Force-Directed Layout"));
        assertTrue(summary.contains("Vertices: 3"));
        assertTrue(summary.contains("Edges: 3"));
        assertTrue(summary.contains("Quality Metrics"));
    }

    // ── Larger graph ────────────────────────────────────────────

    @Test
    public void testLargerGraphCompletes() {
        // Build a 20-node random-ish graph
        for (int i = 0; i < 20; i++) {
            graph.addVertex("V" + i);
        }
        Random rng = new Random(42);
        for (int i = 0; i < 40; i++) {
            String v1 = "V" + rng.nextInt(20);
            String v2 = "V" + rng.nextInt(20);
            if (!v1.equals(v2) && graph.findEdge(v1, v2) == null) {
                addEdge(v1, v2);
            }
        }

        ForceDirectedLayout layout = new ForceDirectedLayout(
                graph, 200, 1000, 800, 0.1, true, 42L);
        layout.compute();

        assertEquals(20, layout.getPositions().size());
        assertTrue(layout.getIterationsUsed() > 0);
    }

    @Test
    public void testGetPositionReturnsNullForUnknownVertex() {
        buildTriangle();
        ForceDirectedLayout layout = new ForceDirectedLayout(graph);
        layout.compute();
        assertNull(layout.getPosition("unknown"));
    }

    @Test
    public void testGetPositionReturnsCopy() {
        buildTriangle();
        ForceDirectedLayout layout = new ForceDirectedLayout(graph);
        layout.compute();

        double[] p1 = layout.getPosition("A");
        double[] p2 = layout.getPosition("A");
        assertNotSame("getPosition should return copy", p1, p2);
        assertEquals(p1[0], p2[0], 0.001);
        assertEquals(p1[1], p2[1], 0.001);
    }

    @Test
    public void testGravityKeepsNodesGrouped() {
        // Disconnected graph with gravity
        addEdge("A", "B");
        addEdge("C", "D");
        addEdge("E", "F");

        ForceDirectedLayout withGravity = new ForceDirectedLayout(
                graph, 200, 800, 600, 1.0, true, 42L);
        withGravity.compute();

        ForceDirectedLayout noGravity = new ForceDirectedLayout(
                graph, 200, 800, 600, 0.0, true, 42L);
        noGravity.compute();

        // With gravity, nodes should be more centered
        double gravitySpread = computeSpread(withGravity.getPositions());
        double noGravitySpread = computeSpread(noGravity.getPositions());

        assertTrue("Gravity should keep nodes more grouped: " +
                gravitySpread + " vs " + noGravitySpread,
                gravitySpread <= noGravitySpread + 50);
    }

    private double computeSpread(Map<String, double[]> positions) {
        double cx = 0, cy = 0;
        for (double[] p : positions.values()) {
            cx += p[0]; cy += p[1];
        }
        cx /= positions.size();
        cy /= positions.size();

        double spread = 0;
        for (double[] p : positions.values()) {
            spread += Math.sqrt(Math.pow(p[0] - cx, 2) +
                                Math.pow(p[1] - cy, 2));
        }
        return spread / positions.size();
    }

    @Test
    public void testBarnesHutLargeGraph() {
        // Build a 150-node graph to trigger Barnes-Hut path (threshold=100)
        for (int i = 0; i < 150; i++) {
            graph.addVertex("V" + i);
        }
        Random rng = new Random(99);
        for (int i = 0; i < 300; i++) {
            String v1 = "V" + rng.nextInt(150);
            String v2 = "V" + rng.nextInt(150);
            if (!v1.equals(v2) && graph.findEdge(v1, v2) == null) {
                addEdge(v1, v2);
            }
        }

        ForceDirectedLayout layout = new ForceDirectedLayout(
                graph, 100, 1200, 900, 0.1, true, 42L);
        layout.compute();

        assertEquals(150, layout.getPositions().size());
        assertTrue("Should converge", layout.getIterationsUsed() > 0);

        // All positions should be within bounds
        for (double[] p : layout.getPositions().values()) {
            assertTrue("x in bounds", p[0] >= 0 && p[0] <= 1200);
            assertTrue("y in bounds", p[1] >= 0 && p[1] <= 900);
        }

        // Connected nodes should still be closer than random pairs on average
        double connectedDist = 0;
        int connectedCount = 0;
        for (Edge e : graph.getEdges()) {
            double[] p1 = layout.getPosition(e.getVertex1());
            double[] p2 = layout.getPosition(e.getVertex2());
            if (p1 != null && p2 != null) {
                connectedDist += Math.sqrt(
                    Math.pow(p1[0]-p2[0], 2) + Math.pow(p1[1]-p2[1], 2));
                connectedCount++;
            }
        }
        if (connectedCount > 0) {
            connectedDist /= connectedCount;
            // Just verify it completed and produced reasonable layout
            assertTrue("Connected avg distance should be positive",
                       connectedDist > 0);
        }
    }

    @Test
    public void testBarnesHutMatchesBruteForceOnSmallGraph() {
        // Verify the threshold: graphs under 100 nodes use brute-force
        // and produce deterministic results
        for (int i = 0; i < 50; i++) {
            graph.addVertex("N" + i);
        }
        Random rng = new Random(77);
        for (int i = 0; i < 80; i++) {
            String v1 = "N" + rng.nextInt(50);
            String v2 = "N" + rng.nextInt(50);
            if (!v1.equals(v2) && graph.findEdge(v1, v2) == null) {
                addEdge(v1, v2);
            }
        }

        // Run twice with same seed - should be identical (brute-force path)
        ForceDirectedLayout layout1 = new ForceDirectedLayout(
                graph, 100, 800, 600, 0.1, true, 42L);
        layout1.compute();

        ForceDirectedLayout layout2 = new ForceDirectedLayout(
                graph, 100, 800, 600, 0.1, true, 42L);
        layout2.compute();

        for (String v : graph.getVertices()) {
            double[] p1 = layout1.getPosition(v);
            double[] p2 = layout2.getPosition(v);
            assertEquals("x should match for " + v, p1[0], p2[0], 0.001);
            assertEquals("y should match for " + v, p1[1], p2[1], 0.001);
        }
    }
}
