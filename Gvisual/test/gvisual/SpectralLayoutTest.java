package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link SpectralLayout}.
 *
 * <p>The layout is computed by inverse iteration on a shifted Laplacian, so
 * exact eigenvector entries are not stable across runs — but a number of
 * properties <em>are</em>:</p>
 * <ul>
 *   <li>All vertices receive coordinates inside [padding, canvas - padding].</li>
 *   <li>The result is deterministic for a fixed seed and disabled jitter.</li>
 *   <li>Trivial cases (n=0, 1, 2) follow documented fallbacks.</li>
 *   <li>For a clearly bipartite or two-community graph, the Fiedler axis
 *       separates the two communities (so we can assert their projected
 *       centroids are distinct).</li>
 *   <li>{@link SpectralLayout#qualityMetrics(Graph)} returns the documented
 *       set of keys and self-consistent values (stress &gt;= 0, etc.).</li>
 *   <li>SVG output contains one {@code &lt;circle&gt;} per vertex and one
 *       {@code &lt;line&gt;} per edge, escapes special characters in labels,
 *       and uses the configured canvas size.</li>
 * </ul>
 */
public class SpectralLayoutTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
    }

    private void addEdge(String a, String b) {
        if (!graph.containsVertex(a)) graph.addVertex(a);
        if (!graph.containsVertex(b)) graph.addVertex(b);
        graph.addEdge(new Edge("f", a, b), a, b);
    }

    // ── Trivial cases ──────────────────────────────────────────────────

    @Test
    public void emptyGraph_producesNoPositions() {
        SpectralLayout layout = new SpectralLayout().compute(graph);
        assertTrue(layout.getXPositions().isEmpty());
        assertTrue(layout.getYPositions().isEmpty());
    }

    @Test
    public void singleVertex_placedAtCanvasCentre() {
        graph.addVertex("only");
        SpectralLayout layout = new SpectralLayout()
                .canvasWidth(400).canvasHeight(300)
                .compute(graph);

        assertEquals(200.0, layout.getX("only"), 1e-9);
        assertEquals(150.0, layout.getY("only"), 1e-9);
    }

    @Test
    public void twoVertices_placedOnHorizontalAxis() {
        graph.addVertex("a");
        graph.addVertex("b");
        SpectralLayout layout = new SpectralLayout()
                .canvasWidth(400).canvasHeight(300).padding(20)
                .compute(graph);

        // The two-node case is documented to place them at the left/right
        // padding on the horizontal midline.
        assertEquals(20.0,  layout.getX("a"), 1e-9);
        assertEquals(380.0, layout.getX("b"), 1e-9);
        assertEquals(150.0, layout.getY("a"), 1e-9);
        assertEquals(150.0, layout.getY("b"), 1e-9);
    }

    @Test
    public void unknownVertex_returnsZero() {
        graph.addVertex("only");
        SpectralLayout layout = new SpectralLayout().compute(graph);
        assertEquals(0.0, layout.getX("ghost"), 0.0);
        assertEquals(0.0, layout.getY("ghost"), 0.0);
    }

    // ── Bounds and determinism ─────────────────────────────────────────

    @Test
    public void allCoordinatesFallWithinPaddedCanvas() {
        // Path graph of 6 nodes — non-degenerate, exercises full pipeline.
        for (int i = 0; i < 5; i++) addEdge("v" + i, "v" + (i + 1));

        double w = 500, h = 400, pad = 30;
        SpectralLayout layout = new SpectralLayout()
                .canvasWidth(w).canvasHeight(h).padding(pad)
                .jitter(false)
                .compute(graph);

        assertEquals(6, layout.getXPositions().size());
        for (String v : layout.getXPositions().keySet()) {
            double x = layout.getX(v);
            double y = layout.getY(v);
            assertTrue("x out of bounds for " + v + ": " + x,
                    x >= pad - 1e-6 && x <= w - pad + 1e-6);
            assertTrue("y out of bounds for " + v + ": " + y,
                    y >= pad - 1e-6 && y <= h - pad + 1e-6);
        }
    }

    @Test
    public void sameSeedAndJitterOff_isDeterministic() {
        // Small cycle to avoid degenerate eigenvalues.
        addEdge("a", "b"); addEdge("b", "c"); addEdge("c", "d"); addEdge("d", "a");

        SpectralLayout l1 = new SpectralLayout().jitter(false).seed(7).compute(graph);
        SpectralLayout l2 = new SpectralLayout().jitter(false).seed(7).compute(graph);

        for (String v : l1.getXPositions().keySet()) {
            assertEquals("X mismatch for " + v, l1.getX(v), l2.getX(v), 1e-9);
            assertEquals("Y mismatch for " + v, l1.getY(v), l2.getY(v), 1e-9);
        }
    }

    // ── Structural: two communities ────────────────────────────────────

    @Test
    public void twoCliquesJoinedByOneEdge_separateAlongFiedlerAxis() {
        // Two K_3 cliques bridged by a single edge.  Spectral layout's
        // 2nd eigenvector should put one clique on each side of the bridge.
        addEdge("a", "b"); addEdge("b", "c"); addEdge("a", "c");
        addEdge("x", "y"); addEdge("y", "z"); addEdge("x", "z");
        addEdge("c", "x");   // bridge

        SpectralLayout layout = new SpectralLayout()
                .canvasWidth(1000).canvasHeight(1000)
                .padding(50)
                .jitter(false)
                .seed(1)
                .compute(graph);

        double leftCentroid  = (layout.getX("a") + layout.getX("b") + layout.getX("c")) / 3.0;
        double rightCentroid = (layout.getX("x") + layout.getX("y") + layout.getX("z")) / 3.0;

        // The two cliques should land in distinguishable positions on the
        // primary axis; we don't care which side is which.
        assertTrue("Centroids should separate the two communities along X, got "
                        + leftCentroid + " vs " + rightCentroid,
                Math.abs(leftCentroid - rightCentroid) > 50.0);
    }

    // ── Quality metrics ────────────────────────────────────────────────

    @Test
    public void qualityMetrics_returnsExpectedKeysAndSanityValues() {
        for (int i = 0; i < 4; i++) addEdge("v" + i, "v" + (i + 1));

        SpectralLayout layout = new SpectralLayout().jitter(false).compute(graph);
        Map<String, Double> metrics = layout.qualityMetrics(graph);

        assertTrue("missing key stress",            metrics.containsKey("stress"));
        assertTrue("missing key edgeCount",         metrics.containsKey("edgeCount"));
        assertTrue("missing key edgeLengthMean",    metrics.containsKey("edgeLengthMean"));
        assertTrue("missing key edgeLengthStdDev",  metrics.containsKey("edgeLengthStdDev"));
        assertTrue("missing key edgeLengthUniformity",
                metrics.containsKey("edgeLengthUniformity"));

        assertEquals(graph.getEdgeCount(), metrics.get("edgeCount").intValue());
        assertTrue("stress should be non-negative", metrics.get("stress") >= 0);
        assertTrue("mean edge length should be > 0", metrics.get("edgeLengthMean") > 0);
        assertTrue("stddev should be non-negative",  metrics.get("edgeLengthStdDev") >= 0);
        // Uniformity is 1 - cv. For non-zero mean it must be <= 1.
        assertTrue("uniformity must be <= 1",        metrics.get("edgeLengthUniformity") <= 1.0 + 1e-9);
    }

    @Test
    public void qualityMetrics_onEmptyGraphReportsZeroEdges() {
        // Two isolated vertices — no edges.
        graph.addVertex("a"); graph.addVertex("b"); graph.addVertex("c");
        SpectralLayout layout = new SpectralLayout().jitter(false).compute(graph);
        Map<String, Double> metrics = layout.qualityMetrics(graph);

        assertEquals(0.0, metrics.get("stress"), 1e-12);
        assertEquals(0, metrics.get("edgeCount").intValue());
        assertFalse("no edge-length stats when there are no edges",
                metrics.containsKey("edgeLengthMean"));
    }

    // ── SVG export ─────────────────────────────────────────────────────

    @Test
    public void svgExport_containsOneCirclePerNodeAndOneLinePerEdge() {
        addEdge("a", "b"); addEdge("b", "c");

        SpectralLayout layout = new SpectralLayout()
                .canvasWidth(640).canvasHeight(480)
                .jitter(false)
                .compute(graph);

        String svg = layout.toSvg(graph);
        assertNotNull(svg);
        assertTrue("missing <svg> root", svg.startsWith("<svg"));
        assertTrue("missing canvas width",  svg.contains("width=\"640\""));
        assertTrue("missing canvas height", svg.contains("height=\"480\""));

        assertEquals("one <circle> per vertex",
                graph.getVertexCount(), countOccurrences(svg, "<circle"));
        assertEquals("one <line> per edge",
                graph.getEdgeCount(), countOccurrences(svg, "<line"));
        assertTrue("missing </svg> close", svg.trim().endsWith("</svg>"));
    }

    @Test
    public void svgExport_escapesSpecialCharactersInLabels() {
        graph.addVertex("a&b<c>");
        graph.addVertex("plain");
        graph.addEdge(new Edge("f", "a&b<c>", "plain"), "a&b<c>", "plain");

        SpectralLayout layout = new SpectralLayout().jitter(false).compute(graph);
        String svg = layout.toSvg(graph);

        assertFalse("raw '<' from label must be escaped",
                svg.contains(">a&b<c><"));
        assertTrue("expected XML-escaped label",
                svg.contains("a&amp;b&lt;c&gt;"));
    }

    @Test
    public void toString_listsEveryVertex() {
        addEdge("a", "b"); addEdge("b", "c");
        SpectralLayout layout = new SpectralLayout().jitter(false).compute(graph);
        String s = layout.toString();
        assertTrue(s.contains("a"));
        assertTrue(s.contains("b"));
        assertTrue(s.contains("c"));
        assertTrue(s.startsWith("SpectralLayout"));
    }

    // ── Builder-style setters round-trip ───────────────────────────────

    @Test
    public void buildersReturnSameInstanceForChaining() {
        SpectralLayout layout = new SpectralLayout();
        assertSame(layout, layout.canvasWidth(123));
        assertSame(layout, layout.canvasHeight(456));
        assertSame(layout, layout.padding(7));
        assertSame(layout, layout.jitter(false));
        assertSame(layout, layout.seed(99));
    }

    @Test
    public void positionMapsAreUnmodifiable() {
        graph.addVertex("only");
        SpectralLayout layout = new SpectralLayout().compute(graph);
        try {
            layout.getXPositions().put("evil", 0.0);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) { /* OK */ }
        try {
            layout.getYPositions().put("evil", 0.0);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) { /* OK */ }
    }

    // ── helpers ────────────────────────────────────────────────────────

    private static int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
