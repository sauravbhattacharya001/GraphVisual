package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for CircularLayout — verifies all ordering strategies, dual-ring mode,
 * SVG export, edge crossing counts, and edge cases.
 */
public class CircularLayoutTest {

    private Graph<String, Edge> triangle;
    private Graph<String, Edge> star;
    private Graph<String, Edge> empty;

    @Before
    public void setUp() {
        // Triangle: A-B-C-A
        triangle = new UndirectedSparseGraph<>();
        triangle.addVertex("A");
        triangle.addVertex("B");
        triangle.addVertex("C");
        triangle.addEdge(makeEdge("A", "B"), "A", "B");
        triangle.addEdge(makeEdge("B", "C"), "B", "C");
        triangle.addEdge(makeEdge("C", "A"), "C", "A");

        // Star: center connected to 5 leaves
        star = new UndirectedSparseGraph<>();
        star.addVertex("center");
        for (int i = 1; i <= 5; i++) {
            String leaf = "leaf" + i;
            star.addVertex(leaf);
            star.addEdge(makeEdge("center", leaf), "center", leaf);
        }

        empty = new UndirectedSparseGraph<>();
    }

    private edge makeEdge(String v1, String v2) {
        edge e = new Edge("f", v1, v2);
        e.setWeight(1.0f);
        e.setLabel(v1 + "-" + v2);
        return e;
    }

    @Test
    public void testEmptyGraph() {
        CircularLayout layout = new CircularLayout.Builder(empty)
            .build();
        layout.compute();
        assertTrue(layout.getPositions().isEmpty());
        assertEquals(0, layout.getEdgeCrossings());
        assertEquals(0, layout.getOrderedVertices().size());
    }

    @Test
    public void testTriangleAlphabetical() {
        CircularLayout layout = new CircularLayout.Builder(triangle)
            .ordering(CircularLayout.Ordering.ALPHABETICAL)
            .build();
        layout.compute();
        assertEquals(3, layout.getPositions().size());
        List<String> order = layout.getOrderedVertices();
        assertEquals("A", order.get(0));
        assertEquals("B", order.get(1));
        assertEquals("C", order.get(2));
    }

    @Test
    public void testTriangleHasNoEdgeCrossings() {
        CircularLayout layout = new CircularLayout.Builder(triangle)
            .ordering(CircularLayout.Ordering.ALPHABETICAL)
            .build();
        layout.compute();
        assertEquals(0, layout.getEdgeCrossings());
    }

    @Test
    public void testDegreeOrdering() {
        CircularLayout layout = new CircularLayout.Builder(star)
            .ordering(CircularLayout.Ordering.DEGREE)
            .build();
        layout.compute();
        // Center has degree 5, should be first
        assertEquals("center", layout.getOrderedVertices().get(0));
    }

    @Test
    public void testBfsOrdering() {
        CircularLayout layout = new CircularLayout.Builder(star)
            .ordering(CircularLayout.Ordering.BFS)
            .build();
        layout.compute();
        assertEquals(6, layout.getOrderedVertices().size());
        // BFS from highest-degree node = center
        assertEquals("center", layout.getOrderedVertices().get(0));
    }

    @Test
    public void testCommunityOrdering() {
        // Two disconnected components
        Graph<String, Edge> disconnected = new UndirectedSparseGraph<>();
        disconnected.addVertex("A1");
        disconnected.addVertex("A2");
        disconnected.addEdge(makeEdge("A1", "A2"), "A1", "A2");
        disconnected.addVertex("B1");
        disconnected.addVertex("B2");
        disconnected.addEdge(makeEdge("B1", "B2"), "B1", "B2");

        CircularLayout layout = new CircularLayout.Builder(disconnected)
            .ordering(CircularLayout.Ordering.COMMUNITY)
            .build();
        layout.compute();

        List<String> order = layout.getOrderedVertices();
        // Same-component nodes should be adjacent
        int a1Idx = order.indexOf("A1");
        int a2Idx = order.indexOf("A2");
        assertEquals(1, Math.abs(a1Idx - a2Idx));
    }

    @Test
    public void testMinimizeCrossings() {
        CircularLayout layout = new CircularLayout.Builder(star)
            .ordering(CircularLayout.Ordering.MINIMIZE_CROSSINGS)
            .build();
        layout.compute();
        assertEquals(6, layout.getPositions().size());
        // Just verify it completes and positions are set
        for (String v : star.getVertices()) {
            assertNotNull(layout.getPositions().get(v));
        }
    }

    @Test
    public void testDualRing() {
        CircularLayout layout = new CircularLayout.Builder(star)
            .dualRing(true)
            .hubThreshold(0.7)
            .ordering(CircularLayout.Ordering.DEGREE)
            .build();
        layout.compute();
        // Center should be a hub
        assertTrue(layout.getHubNodes().contains("center"));
        // Hubs should be on inner ring (closer to center)
        double cx = 400, cy = 400; // default width/height = 800
        double[] hubPos = layout.getPositions().get("center");
        double hubDist = Math.sqrt(Math.pow(hubPos[0] - cx, 2) + Math.pow(hubPos[1] - cy, 2));
        double[] leafPos = layout.getPositions().get("leaf1");
        double leafDist = Math.sqrt(Math.pow(leafPos[0] - cx, 2) + Math.pow(leafPos[1] - cy, 2));
        assertTrue("Hub should be closer to center", hubDist < leafDist);
    }

    @Test
    public void testPositionsOnCircle() {
        CircularLayout layout = new CircularLayout.Builder(triangle)
            .width(600).height(600).padding(50)
            .ordering(CircularLayout.Ordering.ALPHABETICAL)
            .build();
        layout.compute();

        double cx = 300, cy = 300, r = 250;
        for (double[] pos : layout.getPositions().values()) {
            double dist = Math.sqrt(Math.pow(pos[0] - cx, 2) + Math.pow(pos[1] - cy, 2));
            assertEquals(r, dist, 0.1);
        }
    }

    @Test
    public void testReport() {
        CircularLayout layout = new CircularLayout.Builder(triangle)
            .ordering(CircularLayout.Ordering.ALPHABETICAL)
            .build();
        layout.compute();
        String report = layout.getReport();
        assertTrue(report.contains("Vertices: 3"));
        assertTrue(report.contains("Edges: 3"));
        assertTrue(report.contains("Ordering: ALPHABETICAL"));
        assertTrue(report.contains("Edge crossings:"));
    }

    @Test
    public void testSvgExport() {
        CircularLayout layout = new CircularLayout.Builder(triangle)
            .ordering(CircularLayout.Ordering.ALPHABETICAL)
            .build();
        layout.compute();
        String svg = layout.toSvg();
        assertTrue(svg.startsWith("<svg"));
        assertTrue(svg.contains("</svg>"));
        assertTrue(svg.contains("<circle"));
        assertTrue(svg.contains("<line"));
        assertTrue(svg.contains(">A</text>"));
        assertTrue(svg.contains(">B</text>"));
    }

    @Test
    public void testSvgHidesLabelsForLargeGraphs() {
        // Create graph with 100 nodes
        Graph<String, Edge> large = new UndirectedSparseGraph<>();
        for (int i = 0; i < 100; i++) {
            large.addVertex("n" + i);
        }
        for (int i = 0; i < 99; i++) {
            large.addEdge(makeEdge("n" + i, "n" + (i + 1)), "n" + i, "n" + (i + 1));
        }

        CircularLayout layout = new CircularLayout.Builder(large)
            .ordering(CircularLayout.Ordering.ALPHABETICAL)
            .build();
        layout.compute();
        String svg = layout.toSvg();
        // Labels hidden when > 60 nodes
        assertFalse(svg.contains("<text"));
    }

    @Test(expected = IllegalStateException.class)
    public void testAccessBeforeCompute() {
        CircularLayout layout = new CircularLayout.Builder(triangle).build();
        layout.getPositions(); // should throw
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraph() {
        new CircularLayout.Builder(null);
    }

    @Test
    public void testSingleNode() {
        Graph<String, Edge> single = new UndirectedSparseGraph<>();
        single.addVertex("alone");
        CircularLayout layout = new CircularLayout.Builder(single)
            .ordering(CircularLayout.Ordering.ALPHABETICAL)
            .build();
        layout.compute();
        assertEquals(1, layout.getPositions().size());
        assertNotNull(layout.getPositions().get("alone"));
        assertEquals(0, layout.getEdgeCrossings());
    }
}
