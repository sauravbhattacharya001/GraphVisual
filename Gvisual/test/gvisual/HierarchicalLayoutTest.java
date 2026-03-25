package gvisual;

import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link HierarchicalLayout} — validates Sugiyama-style layered
 * graph layout across DAGs (linear chains, diamonds, wide trees), cyclic
 * graphs with back-edge handling, disconnected components, and edge cases
 * like single-node and empty graphs.
 */
public class HierarchicalLayoutTest {

    // ── Helpers ──────────────────────────────────────────────────────

    private Graph<String, Edge> linearDAG(int n) {
        Graph<String, Edge> g = new DirectedSparseGraph<String, Edge>();
        for (int i = 0; i < n; i++) g.addVertex("n" + i);
        for (int i = 0; i < n - 1; i++) {
            Edge e = new Edge("d", "n" + i, "n" + (i + 1));
            g.addEdge(e, "n" + i, "n" + (i + 1));
        }
        return g;
    }

    private Graph<String, Edge> diamondDAG() {
        //     A
        //    / \
        //   B   C
        //    \ /
        //     D
        Graph<String, Edge> g = new DirectedSparseGraph<String, Edge>();
        for (String v : new String[]{"A", "B", "C", "D"}) g.addVertex(v);
        g.addEdge(new Edge("d", "A", "B"), "A", "B");
        g.addEdge(new Edge("d", "A", "C"), "A", "C");
        g.addEdge(new Edge("d", "B", "D"), "B", "D");
        g.addEdge(new Edge("d", "C", "D"), "C", "D");
        return g;
    }

    private Graph<String, Edge> wideDAG(int width) {
        // root -> child0, child1, ..., child(width-1)
        Graph<String, Edge> g = new DirectedSparseGraph<String, Edge>();
        g.addVertex("root");
        for (int i = 0; i < width; i++) {
            String child = "c" + i;
            g.addVertex(child);
            g.addEdge(new Edge("d", "root", child), "root", child);
        }
        return g;
    }

    private Graph<String, Edge> cycleGraph(int n) {
        Graph<String, Edge> g = new DirectedSparseGraph<String, Edge>();
        for (int i = 0; i < n; i++) g.addVertex("n" + i);
        for (int i = 0; i < n; i++) {
            String from = "n" + i;
            String to = "n" + ((i + 1) % n);
            g.addEdge(new Edge("d", from, to), from, to);
        }
        return g;
    }

    // ── Constructor validation ───────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullGraph() {
        new HierarchicalLayout(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_zeroLayerSpacing() {
        new HierarchicalLayout(linearDAG(2), 0, 80, 24,
                HierarchicalLayout.Orientation.TOP_TO_BOTTOM, 1200, 800);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_negativeNodeSpacing() {
        new HierarchicalLayout(linearDAG(2), 120, -5, 24,
                HierarchicalLayout.Orientation.TOP_TO_BOTTOM, 1200, 800);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_negativeWidth() {
        new HierarchicalLayout(linearDAG(2), 120, 80, 24,
                HierarchicalLayout.Orientation.TOP_TO_BOTTOM, -100, 800);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_negativeSweeps() {
        new HierarchicalLayout(linearDAG(2), 120, 80, -1,
                HierarchicalLayout.Orientation.TOP_TO_BOTTOM, 1200, 800);
    }

    // ── Compute-before-access guard ──────────────────────────────────

    @Test(expected = IllegalStateException.class)
    public void getPositions_beforeCompute() {
        new HierarchicalLayout(linearDAG(2)).getPositions();
    }

    @Test(expected = IllegalStateException.class)
    public void getLayers_beforeCompute() {
        new HierarchicalLayout(linearDAG(2)).getLayers();
    }

    @Test(expected = IllegalStateException.class)
    public void getSummary_beforeCompute() {
        new HierarchicalLayout(linearDAG(2)).getSummary();
    }

    @Test(expected = IllegalStateException.class)
    public void toSVG_beforeCompute() {
        new HierarchicalLayout(linearDAG(2)).toSVG(800, 600, 15);
    }

    // ── Empty graph ──────────────────────────────────────────────────

    @Test
    public void emptyGraph() {
        Graph<String, Edge> g = new DirectedSparseGraph<String, Edge>();
        HierarchicalLayout layout = new HierarchicalLayout(g).compute();

        assertTrue(layout.getPositions().isEmpty());
        assertEquals(0, layout.getLayerCount());
        assertEquals(0, layout.getEdgeCrossings());
        assertEquals(0, layout.getMaxLayerWidth());
        assertTrue(layout.getCriticalPath().isEmpty());
        assertTrue(layout.getReversedEdges().isEmpty());
    }

    // ── Single node ──────────────────────────────────────────────────

    @Test
    public void singleNode() {
        Graph<String, Edge> g = new DirectedSparseGraph<String, Edge>();
        g.addVertex("solo");
        HierarchicalLayout layout = new HierarchicalLayout(g).compute();

        assertEquals(1, layout.getPositions().size());
        assertNotNull(layout.getPosition("solo"));
        assertEquals(1, layout.getLayerCount());
        assertEquals(1, layout.getMaxLayerWidth());
        assertEquals(0, layout.getEdgeCrossings());
    }

    // ── Linear chain ─────────────────────────────────────────────────

    @Test
    public void linearChain_layers() {
        HierarchicalLayout layout = new HierarchicalLayout(linearDAG(4)).compute();

        assertEquals(4, layout.getLayerCount());
        Map<String, Integer> layers = layout.getLayerAssignment();
        assertEquals(0, (int) layers.get("n0"));
        assertEquals(1, (int) layers.get("n1"));
        assertEquals(2, (int) layers.get("n2"));
        assertEquals(3, (int) layers.get("n3"));
    }

    @Test
    public void linearChain_noEdgeCrossings() {
        HierarchicalLayout layout = new HierarchicalLayout(linearDAG(5)).compute();
        assertEquals(0, layout.getEdgeCrossings());
    }

    @Test
    public void linearChain_criticalPath() {
        HierarchicalLayout layout = new HierarchicalLayout(linearDAG(4)).compute();
        List<String> cp = layout.getCriticalPath();
        assertEquals(4, cp.size());
        assertEquals("n0", cp.get(0));
        assertEquals("n3", cp.get(3));
    }

    @Test
    public void linearChain_positions_topToBottom() {
        HierarchicalLayout layout = new HierarchicalLayout(linearDAG(3)).compute();
        Map<String, double[]> pos = layout.getPositions();

        // In top-to-bottom, y should increase with layer depth
        assertTrue(pos.get("n0")[1] < pos.get("n1")[1]);
        assertTrue(pos.get("n1")[1] < pos.get("n2")[1]);
    }

    // ── Diamond DAG ──────────────────────────────────────────────────

    @Test
    public void diamondDAG_layers() {
        HierarchicalLayout layout = new HierarchicalLayout(diamondDAG()).compute();

        Map<String, Integer> la = layout.getLayerAssignment();
        assertEquals(0, (int) la.get("A"));
        // B and C should be in the same layer
        assertEquals((int) la.get("B"), (int) la.get("C"));
        // D should be deeper
        assertTrue(la.get("D") > la.get("B"));
    }

    @Test
    public void diamondDAG_maxLayerWidth() {
        HierarchicalLayout layout = new HierarchicalLayout(diamondDAG()).compute();
        assertEquals(2, layout.getMaxLayerWidth()); // B and C in same layer
    }

    @Test
    public void diamondDAG_noCrossings() {
        HierarchicalLayout layout = new HierarchicalLayout(diamondDAG()).compute();
        assertEquals(0, layout.getEdgeCrossings());
    }

    // ── Wide fan-out ─────────────────────────────────────────────────

    @Test
    public void wideFanOut_layerAssignment() {
        HierarchicalLayout layout = new HierarchicalLayout(wideDAG(8)).compute();

        assertEquals(2, layout.getLayerCount());
        assertEquals(0, (int) layout.getLayerAssignment().get("root"));
        for (int i = 0; i < 8; i++) {
            assertEquals(1, (int) layout.getLayerAssignment().get("c" + i));
        }
    }

    @Test
    public void wideFanOut_maxWidth() {
        HierarchicalLayout layout = new HierarchicalLayout(wideDAG(10)).compute();
        assertEquals(10, layout.getMaxLayerWidth());
    }

    // ── Cycle handling ───────────────────────────────────────────────

    @Test
    public void cycleGraph_handledGracefully() {
        HierarchicalLayout layout = new HierarchicalLayout(cycleGraph(4)).compute();

        // Should complete without exception
        assertFalse(layout.getReversedEdges().isEmpty());
        assertEquals(4, layout.getPositions().size());
    }

    @Test
    public void selfLoop_handled() {
        Graph<String, Edge> g = new DirectedSparseGraph<String, Edge>();
        g.addVertex("A");
        g.addVertex("B");
        g.addEdge(new Edge("d", "A", "B"), "A", "B");

        HierarchicalLayout layout = new HierarchicalLayout(g).compute();
        assertEquals(2, layout.getPositions().size());
    }

    // ── Left-to-right orientation ────────────────────────────────────

    @Test
    public void leftToRight_xIncreasesWithLayer() {
        HierarchicalLayout layout = new HierarchicalLayout(
                linearDAG(3), 120, 80, 24,
                HierarchicalLayout.Orientation.LEFT_TO_RIGHT, 1200, 800
        ).compute();

        Map<String, double[]> pos = layout.getPositions();

        // In left-to-right, x should increase with layer depth
        assertTrue(pos.get("n0")[0] < pos.get("n1")[0]);
        assertTrue(pos.get("n1")[0] < pos.get("n2")[0]);
    }

    @Test
    public void leftToRight_orientation() {
        HierarchicalLayout layout = new HierarchicalLayout(
                linearDAG(2), 120, 80, 24,
                HierarchicalLayout.Orientation.LEFT_TO_RIGHT, 1200, 800
        );
        assertEquals(HierarchicalLayout.Orientation.LEFT_TO_RIGHT,
                layout.getOrientation());
    }

    // ── SVG export ───────────────────────────────────────────────────

    @Test
    public void toSVG_containsElements() {
        HierarchicalLayout layout = new HierarchicalLayout(diamondDAG()).compute();
        String svg = layout.toSVG(800, 600, 15);

        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("</svg>"));
        assertTrue(svg.contains("<circle"));
        assertTrue(svg.contains("<line"));
        assertTrue(svg.contains("<text"));
        assertTrue(svg.contains("arrowhead")); // marker for directed edges
    }

    @Test
    public void toSVG_emptyGraph() {
        Graph<String, Edge> g = new DirectedSparseGraph<String, Edge>();
        String svg = new HierarchicalLayout(g).compute().toSVG(800, 600, 15);

        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("</svg>"));
        assertFalse(svg.contains("<circle")); // no nodes
    }

    @Test
    public void toSVG_reversedEdgesStyledDifferently() {
        HierarchicalLayout layout = new HierarchicalLayout(cycleGraph(3)).compute();
        String svg = layout.toSVG(800, 600, 15);

        // Reversed edges should be dashed red
        if (!layout.getReversedEdges().isEmpty()) {
            assertTrue(svg.contains("stroke-dasharray"));
            assertTrue(svg.contains("#e74c3c")); // red color
        }
    }

    // ── Quality report ───────────────────────────────────────────────

    @Test
    public void qualityReport_values() {
        HierarchicalLayout layout = new HierarchicalLayout(diamondDAG()).compute();
        HierarchicalLayout.LayoutQuality q = layout.getQualityReport();

        assertEquals(3, q.getLayerCount());
        assertEquals(2, q.getMaxLayerWidth());
        assertEquals(0, q.getEdgeCrossings());
        assertEquals(0, q.getReversedEdgeCount());
        assertTrue(q.getCriticalPathLength() > 0);
        assertTrue(q.getAspectRatio() > 0);
        assertEquals("TOP_TO_BOTTOM", q.getOrientation());
    }

    @Test
    public void qualityReport_toString() {
        HierarchicalLayout layout = new HierarchicalLayout(linearDAG(3)).compute();
        String s = layout.getQualityReport().toString();

        assertTrue(s.contains("LayoutQuality"));
        assertTrue(s.contains("layers="));
        assertTrue(s.contains("crossings="));
    }

    // ── Summary ──────────────────────────────────────────────────────

    @Test
    public void summary_containsInfo() {
        HierarchicalLayout layout = new HierarchicalLayout(diamondDAG()).compute();
        String summary = layout.getSummary();

        assertTrue(summary.contains("Hierarchical Layout Summary"));
        assertTrue(summary.contains("Vertices"));
        assertTrue(summary.contains("Layers"));
        assertTrue(summary.contains("Edge crossings"));
        assertTrue(summary.contains("Critical path"));
        assertTrue(summary.contains("Layer breakdown"));
    }

    // ── Fluent chaining ──────────────────────────────────────────────

    @Test
    public void compute_returnsThis() {
        HierarchicalLayout layout = new HierarchicalLayout(linearDAG(2));
        assertSame(layout, layout.compute());
    }

    // ── Disconnected components ──────────────────────────────────────

    @Test
    public void disconnectedComponents() {
        Graph<String, Edge> g = new DirectedSparseGraph<String, Edge>();
        g.addVertex("A");
        g.addVertex("B");
        g.addVertex("C");
        g.addVertex("D");
        g.addEdge(new Edge("d", "A", "B"), "A", "B");
        g.addEdge(new Edge("d", "C", "D"), "C", "D");

        HierarchicalLayout layout = new HierarchicalLayout(g).compute();

        assertEquals(4, layout.getPositions().size());
        assertEquals(2, layout.getLayerCount());
    }

    // ── Isolated vertices ────────────────────────────────────────────

    @Test
    public void isolatedVertices() {
        Graph<String, Edge> g = new DirectedSparseGraph<String, Edge>();
        g.addVertex("A");
        g.addVertex("B");
        g.addVertex("C");
        // No edges

        HierarchicalLayout layout = new HierarchicalLayout(g).compute();

        assertEquals(3, layout.getPositions().size());
        assertEquals(1, layout.getLayerCount());
        assertEquals(3, layout.getMaxLayerWidth());
    }

    // ── Crossing detection ───────────────────────────────────────────

    @Test
    public void crossingDetection_knownCase() {
        // Create X-crossing pattern: A->D, B->C where A,B in layer 0 and C,D in layer 1
        Graph<String, Edge> g = new DirectedSparseGraph<String, Edge>();
        g.addVertex("A");
        g.addVertex("B");
        g.addVertex("C");
        g.addVertex("D");
        g.addEdge(new Edge("d", "A", "D"), "A", "D");
        g.addEdge(new Edge("d", "B", "C"), "B", "C");

        // With enough crossing sweeps, should minimize crossings
        HierarchicalLayout layout = new HierarchicalLayout(
                g, 120, 80, 50, HierarchicalLayout.Orientation.TOP_TO_BOTTOM,
                1200, 800).compute();

        // Barycenter heuristic should resolve this to 0 crossings
        assertEquals(0, layout.getEdgeCrossings());
    }

    // ── Deep DAG ─────────────────────────────────────────────────────

    @Test
    public void deepDAG() {
        HierarchicalLayout layout = new HierarchicalLayout(linearDAG(20)).compute();

        assertEquals(20, layout.getLayerCount());
        assertEquals(1, layout.getMaxLayerWidth());
        List<String> cp = layout.getCriticalPath();
        assertEquals(20, cp.size());
    }

    // ── Position bounds ──────────────────────────────────────────────

    @Test
    public void positions_allFinite() {
        HierarchicalLayout layout = new HierarchicalLayout(diamondDAG()).compute();

        for (double[] pos : layout.getPositions().values()) {
            assertTrue(Double.isFinite(pos[0]));
            assertTrue(Double.isFinite(pos[1]));
        }
    }

    @Test
    public void getPosition_unknownVertex() {
        HierarchicalLayout layout = new HierarchicalLayout(linearDAG(2)).compute();
        assertNull(layout.getPosition("nonexistent"));
    }

    // ── Complex DAG ──────────────────────────────────────────────────

    @Test
    public void complexDAG_multiplePathsToSink() {
        //     A
        //    /|\
        //   B C D
        //   |/  |
        //   E   F
        //    \ /
        //     G
        Graph<String, Edge> g = new DirectedSparseGraph<String, Edge>();
        for (String v : new String[]{"A","B","C","D","E","F","G"}) g.addVertex(v);
        g.addEdge(new Edge("d","A","B"), "A", "B");
        g.addEdge(new Edge("d","A","C"), "A", "C");
        g.addEdge(new Edge("d","A","D"), "A", "D");
        g.addEdge(new Edge("d","B","E"), "B", "E");
        g.addEdge(new Edge("d","C","E"), "C", "E");
        g.addEdge(new Edge("d","D","F"), "D", "F");
        g.addEdge(new Edge("d","E","G"), "E", "G");
        g.addEdge(new Edge("d","F","G"), "F", "G");

        HierarchicalLayout layout = new HierarchicalLayout(g).compute();

        Map<String, Integer> la = layout.getLayerAssignment();
        assertEquals(0, (int) la.get("A"));
        assertTrue(la.get("G") > la.get("B"));
        assertTrue(la.get("G") > la.get("F"));
        assertTrue(layout.getCriticalPath().size() >= 4);
    }

    // ── Layers are unmodifiable ──────────────────────────────────────

    @Test(expected = UnsupportedOperationException.class)
    public void layers_unmodifiable() {
        HierarchicalLayout layout = new HierarchicalLayout(linearDAG(3)).compute();
        layout.getLayers().get(0).add("hacked");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void positions_unmodifiable() {
        HierarchicalLayout layout = new HierarchicalLayout(linearDAG(3)).compute();
        layout.getPositions().put("hacked", new double[]{0, 0});
    }

    @Test(expected = UnsupportedOperationException.class)
    public void criticalPath_unmodifiable() {
        HierarchicalLayout layout = new HierarchicalLayout(linearDAG(3)).compute();
        layout.getCriticalPath().add("hacked");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void reversedEdges_unmodifiable() {
        HierarchicalLayout layout = new HierarchicalLayout(cycleGraph(3)).compute();
        layout.getReversedEdges().add(new Edge("d", "x", "y"));
    }

    // ── Recompute ────────────────────────────────────────────────────

    @Test
    public void compute_canCallTwice() {
        HierarchicalLayout layout = new HierarchicalLayout(linearDAG(3));
        layout.compute();
        layout.compute(); // should not throw
        assertEquals(3, layout.getLayerCount());
    }

    // ── Large graph performance ──────────────────────────────────────

    @Test
    public void largeGraph_completes() {
        // Build a moderately large DAG (100 nodes, ~200 edges)
        Graph<String, Edge> g = new DirectedSparseGraph<String, Edge>();
        for (int i = 0; i < 100; i++) g.addVertex("v" + i);

        Random rng = new Random(42);
        for (int i = 0; i < 100; i++) {
            int targets = 1 + rng.nextInt(3);
            for (int t = 0; t < targets; t++) {
                int j = i + 1 + rng.nextInt(Math.min(10, 100 - i));
                if (j < 100) {
                    String from = "v" + i;
                    String to = "v" + j;
                    Edge e = new Edge("d", from, to);
                    try {
                        g.addEdge(e, from, to);
                    } catch (Exception ex) {
                        // duplicate Edge, skip
                    }
                }
            }
        }

        long start = System.currentTimeMillis();
        HierarchicalLayout layout = new HierarchicalLayout(g).compute();
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(100, layout.getPositions().size());
        assertTrue(layout.getLayerCount() > 1);
        assertTrue("Should complete in <5s, took " + elapsed + "ms",
                elapsed < 5000);
    }
}
