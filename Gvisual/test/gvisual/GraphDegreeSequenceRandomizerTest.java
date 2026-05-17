package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import gvisual.GraphDegreeSequenceRandomizer.NullModelSummary;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link GraphDegreeSequenceRandomizer}.
 *
 * <p>Covers:</p>
 * <ul>
 *   <li>Degree sequence preservation (the core invariant of edge-switching).</li>
 *   <li>Vertex and edge count preservation.</li>
 *   <li>Absence of self-loops and multi-edges in randomized output.</li>
 *   <li>Reproducibility with a fixed seed.</li>
 *   <li>Ensemble generation.</li>
 *   <li>Edge cases: empty graph, single edge, isolated vertices.</li>
 *   <li>Significance computation (mean / std / z / p / isSignificant).</li>
 *   <li>NullModelSummary getters + toString.</li>
 * </ul>
 */
public class GraphDegreeSequenceRandomizerTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, Edge>();
    }

    // ---- helpers -------------------------------------------------------

    private void addEdge(String a, String b) {
        if (!graph.containsVertex(a)) graph.addVertex(a);
        if (!graph.containsVertex(b)) graph.addVertex(b);
        Edge e = new Edge("f", a, b);
        e.setWeight(1.0f);
        graph.addEdge(e, a, b);
    }

    private static Map<String, Integer> degreeMap(Graph<String, Edge> g) {
        Map<String, Integer> deg = new TreeMap<String, Integer>();
        for (String v : g.getVertices()) {
            deg.put(v, g.getNeighborCount(v));
        }
        return deg;
    }

    private static List<Integer> degreeSequence(Graph<String, Edge> g) {
        List<Integer> degs = new ArrayList<Integer>();
        for (String v : g.getVertices()) {
            degs.add(g.getNeighborCount(v));
        }
        Collections.sort(degs);
        return degs;
    }

    // ---- core invariants ----------------------------------------------

    @Test
    public void testDegreeSequencePreservedOnTriangle() {
        addEdge("a", "b");
        addEdge("b", "c");
        addEdge("a", "c");

        GraphDegreeSequenceRandomizer rand = new GraphDegreeSequenceRandomizer(42L);
        Graph<String, Edge> shuffled = rand.randomize(graph, 10);

        assertEquals(degreeSequence(graph), degreeSequence(shuffled));
    }

    @Test
    public void testPerVertexDegreePreservedOnPath() {
        addEdge("a", "b");
        addEdge("b", "c");
        addEdge("c", "d");
        addEdge("d", "e");

        GraphDegreeSequenceRandomizer rand = new GraphDegreeSequenceRandomizer(1234L);
        Graph<String, Edge> shuffled = rand.randomize(graph, 20);

        assertEquals("per-vertex degree map must be preserved",
                degreeMap(graph), degreeMap(shuffled));
    }

    @Test
    public void testVertexAndEdgeCountsPreserved() {
        // Build a denser graph.
        String[] vs = {"a", "b", "c", "d", "e"};
        addEdge("a", "b");
        addEdge("a", "c");
        addEdge("a", "d");
        addEdge("b", "c");
        addEdge("b", "e");
        addEdge("c", "d");

        GraphDegreeSequenceRandomizer rand = new GraphDegreeSequenceRandomizer(99L);
        Graph<String, Edge> shuffled = rand.randomize(graph, 25);

        assertEquals(graph.getVertexCount(), shuffled.getVertexCount());
        assertEquals(graph.getEdgeCount(), shuffled.getEdgeCount());
        // Vertex set should be the same.
        assertEquals(new TreeSet<String>(graph.getVertices()),
                new TreeSet<String>(shuffled.getVertices()));

        // Use the helper variable to assert all original vertices survive.
        for (String v : vs) {
            assertTrue("vertex " + v + " missing", shuffled.containsVertex(v));
        }
    }

    @Test
    public void testNoSelfLoopsOrMultiEdges() {
        // K_5 minus one edge -> some swap targets are blocked by existing edges.
        String[] vs = {"a", "b", "c", "d", "e"};
        for (int i = 0; i < vs.length; i++) {
            for (int j = i + 1; j < vs.length; j++) {
                if (i == 0 && j == 4) continue;  // remove a-e
                addEdge(vs[i], vs[j]);
            }
        }

        GraphDegreeSequenceRandomizer rand = new GraphDegreeSequenceRandomizer(7L);
        Graph<String, Edge> shuffled = rand.randomize(graph, 30);

        // Verify no self-loops.
        for (Edge e : shuffled.getEdges()) {
            String u = shuffled.getEndpoints(e).getFirst();
            String v = shuffled.getEndpoints(e).getSecond();
            assertNotEquals("self-loop created: " + u, u, v);
        }

        // Verify no parallel edges (since UndirectedSparseGraph already
        // forbids them, this is a structural sanity check on edge count).
        Set<String> seenPairs = new HashSet<String>();
        for (Edge e : shuffled.getEdges()) {
            String u = shuffled.getEndpoints(e).getFirst();
            String v = shuffled.getEndpoints(e).getSecond();
            String key = u.compareTo(v) < 0 ? u + "|" + v : v + "|" + u;
            assertTrue("duplicate edge between " + key, seenPairs.add(key));
        }
    }

    @Test
    public void testReturnsCopyNotOriginal() {
        addEdge("a", "b");
        addEdge("b", "c");
        addEdge("a", "c");

        GraphDegreeSequenceRandomizer rand = new GraphDegreeSequenceRandomizer(1L);
        Graph<String, Edge> shuffled = rand.randomize(graph, 5);

        assertNotSame("randomize must produce a new graph instance",
                graph, shuffled);

        // Mutating the copy must not affect the original.
        int origEdgeCount = graph.getEdgeCount();
        for (Edge e : new ArrayList<Edge>(shuffled.getEdges())) {
            shuffled.removeEdge(e);
        }
        assertEquals(origEdgeCount, graph.getEdgeCount());
    }

    // ---- reproducibility ----------------------------------------------

    @Test
    public void testSameSeedProducesSameDegreeSequence() {
        // Same seed -> same random walk -> same final degree sequence.
        // (Cannot guarantee identical edge sets because graph-internal
        // ordering may differ, but the degree map per vertex must match.)
        addEdge("a", "b");
        addEdge("b", "c");
        addEdge("c", "d");
        addEdge("d", "e");
        addEdge("a", "e");

        GraphDegreeSequenceRandomizer r1 = new GraphDegreeSequenceRandomizer(123L);
        GraphDegreeSequenceRandomizer r2 = new GraphDegreeSequenceRandomizer(123L);
        Graph<String, Edge> g1 = r1.randomize(graph, 5);
        Graph<String, Edge> g2 = r2.randomize(graph, 5);

        assertEquals(degreeMap(g1), degreeMap(g2));
    }

    // ---- ensemble -----------------------------------------------------

    @Test
    public void testEnsembleProducesRequestedCount() {
        addEdge("a", "b");
        addEdge("b", "c");
        addEdge("c", "d");
        addEdge("a", "d");

        GraphDegreeSequenceRandomizer rand = new GraphDegreeSequenceRandomizer(42L);
        List<Graph<String, Edge>> ensemble = rand.ensemble(graph, 5, 4);

        assertEquals(5, ensemble.size());
        List<Integer> origSeq = degreeSequence(graph);
        for (Graph<String, Edge> g : ensemble) {
            assertEquals("each ensemble graph must preserve the degree sequence",
                    origSeq, degreeSequence(g));
            assertEquals(graph.getVertexCount(), g.getVertexCount());
            assertEquals(graph.getEdgeCount(), g.getEdgeCount());
        }
    }

    @Test
    public void testEnsembleZeroCount() {
        addEdge("a", "b");
        GraphDegreeSequenceRandomizer rand = new GraphDegreeSequenceRandomizer(0L);
        List<Graph<String, Edge>> ensemble = rand.ensemble(graph, 0, 5);
        assertNotNull(ensemble);
        assertEquals(0, ensemble.size());
    }

    // ---- edge cases ---------------------------------------------------

    @Test
    public void testEmptyGraphRandomizes() {
        GraphDegreeSequenceRandomizer rand = new GraphDegreeSequenceRandomizer(0L);
        Graph<String, Edge> shuffled = rand.randomize(graph, 10);
        assertEquals(0, shuffled.getVertexCount());
        assertEquals(0, shuffled.getEdgeCount());
    }

    @Test
    public void testSingleEdgeGraphUnchanged() {
        addEdge("a", "b");
        GraphDegreeSequenceRandomizer rand = new GraphDegreeSequenceRandomizer(0L);
        Graph<String, Edge> shuffled = rand.randomize(graph, 100);
        assertEquals(2, shuffled.getVertexCount());
        assertEquals(1, shuffled.getEdgeCount());
        assertEquals(degreeMap(graph), degreeMap(shuffled));
    }

    @Test
    public void testIsolatedVerticesPreserved() {
        addEdge("a", "b");
        graph.addVertex("c");  // isolated
        graph.addVertex("d");  // isolated
        GraphDegreeSequenceRandomizer rand = new GraphDegreeSequenceRandomizer(99L);
        Graph<String, Edge> shuffled = rand.randomize(graph, 5);
        assertTrue(shuffled.containsVertex("c"));
        assertTrue(shuffled.containsVertex("d"));
        assertEquals(0, shuffled.getNeighborCount("c"));
        assertEquals(0, shuffled.getNeighborCount("d"));
    }

    @Test
    public void testSwapFactorZeroOrNegativeStillRuns() {
        addEdge("a", "b");
        addEdge("b", "c");
        addEdge("a", "c");
        GraphDegreeSequenceRandomizer rand = new GraphDegreeSequenceRandomizer(1L);
        // swapFactor <= 0 is clamped to 1 internally; must not throw.
        Graph<String, Edge> g1 = rand.randomize(graph, 0);
        Graph<String, Edge> g2 = rand.randomize(graph, -5);
        assertEquals(degreeMap(graph), degreeMap(g1));
        assertEquals(degreeMap(graph), degreeMap(g2));
    }

    // ---- significance computation -------------------------------------

    @Test
    public void testComputeSignificanceBasicStats() {
        GraphDegreeSequenceRandomizer rand = new GraphDegreeSequenceRandomizer();
        double[] ensemble = {1.0, 2.0, 3.0, 4.0, 5.0};
        NullModelSummary s = rand.computeSignificance(2.5, ensemble);

        // Mean = 3.0, population variance = 2.0, std = sqrt(2) ≈ 1.4142
        assertEquals(2.5, s.getObservedValue(), 1e-9);
        assertEquals(3.0, s.getEnsembleMean(), 1e-9);
        assertEquals(Math.sqrt(2.0), s.getEnsembleStdDev(), 1e-9);
        assertEquals((2.5 - 3.0) / Math.sqrt(2.0), s.getZScore(), 1e-9);
        // p = fraction of ensemble values >= 2.5 = 3/5 = 0.6 (3, 4, 5 qualify)
        assertEquals(0.6, s.getPValue(), 1e-9);
        assertEquals(5, s.getEnsembleSize());
    }

    @Test
    public void testComputeSignificanceZeroStdDevGivesZeroZ() {
        GraphDegreeSequenceRandomizer rand = new GraphDegreeSequenceRandomizer();
        double[] ensemble = {2.0, 2.0, 2.0, 2.0};
        NullModelSummary s = rand.computeSignificance(5.0, ensemble);
        assertEquals(0.0, s.getEnsembleStdDev(), 1e-9);
        assertEquals(0.0, s.getZScore(), 1e-9);
        // p = 0/4 = 0 (all ensemble values < observed)
        assertEquals(0.0, s.getPValue(), 1e-9);
    }

    @Test
    public void testIsSignificantThresholds() {
        GraphDegreeSequenceRandomizer rand = new GraphDegreeSequenceRandomizer();
        NullModelSummary low = rand.computeSignificance(
                10.0, new double[]{1.0, 2.0, 3.0, 4.0});  // p = 0
        assertTrue("p=0 is significant at alpha=0.05", low.isSignificant(0.05));

        NullModelSummary high = rand.computeSignificance(
                0.0, new double[]{1.0, 2.0, 3.0, 4.0});  // p = 1.0
        assertFalse("p=1 is not significant at alpha=0.05", high.isSignificant(0.05));
    }

    @Test
    public void testNullModelSummaryToStringContainsKeyFields() {
        GraphDegreeSequenceRandomizer rand = new GraphDegreeSequenceRandomizer();
        NullModelSummary s = rand.computeSignificance(
                2.0, new double[]{1.0, 2.0, 3.0});
        String txt = s.toString();
        assertTrue(txt.contains("observed"));
        assertTrue(txt.contains("mean"));
        assertTrue(txt.contains("std"));
        assertTrue(txt.contains("z"));
        assertTrue(txt.contains("p"));
        assertTrue(txt.contains("n="));
    }
}
