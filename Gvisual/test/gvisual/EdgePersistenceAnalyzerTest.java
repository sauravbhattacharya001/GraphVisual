package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link EdgePersistenceAnalyzer}.
 */
public class EdgePersistenceAnalyzerTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
    }

    // ── Constructor validation ─────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_nullTemporalGraph() {
        new EdgePersistenceAnalyzer(null, 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_zeroWindows() {
        TemporalGraph tg = new TemporalGraph(graph);
        new EdgePersistenceAnalyzer(tg, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_negativeWindows() {
        TemporalGraph tg = new TemporalGraph(graph);
        new EdgePersistenceAnalyzer(tg, -1);
    }

    // ── Classification ─────────────────────────────────────────────

    @Test
    public void testClassify_allPersistent() {
        // Edge spans entire time range -> persistent in all windows
        Edge e1 = new Edge("f", "A", "B");
        e1.setTimestamp(0L);
        e1.setEndTimestamp(1000L);
        graph.addEdge(e1, "A", "B");

        TemporalGraph tg = new TemporalGraph(graph);
        EdgePersistenceAnalyzer epa = new EdgePersistenceAnalyzer(tg, 10);
        Map<Edge, String> result = epa.classify();
        assertEquals(EdgePersistenceAnalyzer.PERSISTENT, result.get(e1));
    }

    @Test
    public void testClassify_transientEdge() {
        // Edge active only in a tiny window of a long timeline
        Edge e1 = new Edge("f", "A", "B");
        e1.setTimestamp(0L);
        e1.setEndTimestamp(1000L);

        Edge e2 = new Edge("f", "C", "D");
        e2.setTimestamp(0L);
        e2.setEndTimestamp(10L);  // only first ~1% of range

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "C", "D");

        TemporalGraph tg = new TemporalGraph(graph);
        EdgePersistenceAnalyzer epa = new EdgePersistenceAnalyzer(tg, 20);
        Map<Edge, String> result = epa.classify();
        assertEquals(EdgePersistenceAnalyzer.TRANSIENT, result.get(e2));
    }

    @Test
    public void testClassify_periodicEdge() {
        // Edge active in about half the windows
        Edge e1 = new Edge("f", "A", "B");
        e1.setTimestamp(0L);
        e1.setEndTimestamp(1000L);  // full range marker

        Edge e2 = new Edge("f", "C", "D");
        e2.setTimestamp(0L);
        e2.setEndTimestamp(500L);  // half the range

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "C", "D");

        TemporalGraph tg = new TemporalGraph(graph);
        EdgePersistenceAnalyzer epa = new EdgePersistenceAnalyzer(tg, 10);
        Map<Edge, String> result = epa.classify();
        // e2 should be periodic (present in ~50% of windows)
        String c2 = result.get(e2);
        assertTrue("Half-range Edge should be periodic",
                   EdgePersistenceAnalyzer.PERIODIC.equals(c2) ||
                   EdgePersistenceAnalyzer.PERSISTENT.equals(c2));
    }

    @Test(expected = IllegalStateException.class)
    public void testClassify_emptyGraph() {
        TemporalGraph tg = new TemporalGraph(graph);
        EdgePersistenceAnalyzer epa = new EdgePersistenceAnalyzer(tg, 5);
        epa.classify(); // should throw — no timestamped edges
    }

    @Test
    public void testClassify_singleWindow() {
        Edge e1 = new Edge("f", "A", "B");
        e1.setTimestamp(100L);
        e1.setEndTimestamp(200L);
        graph.addEdge(e1, "A", "B");

        TemporalGraph tg = new TemporalGraph(graph);
        EdgePersistenceAnalyzer epa = new EdgePersistenceAnalyzer(tg, 1);
        Map<Edge, String> result = epa.classify();
        // With 1 window, ratio is 1.0 => persistent
        assertEquals(EdgePersistenceAnalyzer.PERSISTENT, result.get(e1));
    }

    // ── Summary ────────────────────────────────────────────────────

    @Test
    public void testSummary_countsMatchClassify() {
        Edge e1 = new Edge("f", "A", "B");
        e1.setTimestamp(0L); e1.setEndTimestamp(1000L);
        Edge e2 = new Edge("f", "C", "D");
        e2.setTimestamp(0L); e2.setEndTimestamp(10L);

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "C", "D");

        TemporalGraph tg = new TemporalGraph(graph);
        EdgePersistenceAnalyzer epa = new EdgePersistenceAnalyzer(tg, 20);

        Map<String, Integer> summary = epa.summary();
        int total = summary.get(EdgePersistenceAnalyzer.PERSISTENT)
                  + summary.get(EdgePersistenceAnalyzer.PERIODIC)
                  + summary.get(EdgePersistenceAnalyzer.TRANSIENT);
        assertEquals(2, total);
    }

    @Test
    public void testSummary_allKeysPresent() {
        Edge e1 = new Edge("f", "A", "B");
        e1.setTimestamp(0L); e1.setEndTimestamp(100L);
        graph.addEdge(e1, "A", "B");

        TemporalGraph tg = new TemporalGraph(graph);
        EdgePersistenceAnalyzer epa = new EdgePersistenceAnalyzer(tg, 5);
        Map<String, Integer> summary = epa.summary();
        assertTrue(summary.containsKey(EdgePersistenceAnalyzer.PERSISTENT));
        assertTrue(summary.containsKey(EdgePersistenceAnalyzer.PERIODIC));
        assertTrue(summary.containsKey(EdgePersistenceAnalyzer.TRANSIENT));
    }

    // ── getEdgesByClassification ───────────────────────────────────

    @Test
    public void testGetEdgesByClassification_persistent() {
        Edge e1 = new Edge("f", "A", "B");
        e1.setTimestamp(0L); e1.setEndTimestamp(1000L);
        graph.addEdge(e1, "A", "B");

        TemporalGraph tg = new TemporalGraph(graph);
        EdgePersistenceAnalyzer epa = new EdgePersistenceAnalyzer(tg, 5);
        Set<Edge> persistent = epa.getEdgesByClassification(EdgePersistenceAnalyzer.PERSISTENT);
        assertTrue(persistent.contains(e1));
    }

    @Test
    public void testGetEdgesByClassification_noMatch() {
        Edge e1 = new Edge("f", "A", "B");
        e1.setTimestamp(0L); e1.setEndTimestamp(1000L);
        graph.addEdge(e1, "A", "B");

        TemporalGraph tg = new TemporalGraph(graph);
        EdgePersistenceAnalyzer epa = new EdgePersistenceAnalyzer(tg, 5);
        Set<Edge> transient_ = epa.getEdgesByClassification(EdgePersistenceAnalyzer.TRANSIENT);
        assertFalse(transient_.contains(e1));
    }

    // ── Memoization & validation (issue #168) ─────────────────────

    @Test
    public void testClassify_isMemoizedAcrossCalls() {
        // Same inputs -> classify() must return the SAME map instance the
        // second time (proves we are not re-scanning windows/edges).
        Edge e1 = new Edge("f", "A", "B");
        e1.setTimestamp(0L); e1.setEndTimestamp(1000L);
        Edge e2 = new Edge("f", "C", "D");
        e2.setTimestamp(0L); e2.setEndTimestamp(10L);
        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "C", "D");

        TemporalGraph tg = new TemporalGraph(graph);
        EdgePersistenceAnalyzer epa = new EdgePersistenceAnalyzer(tg, 20);

        Map<Edge, String> first = epa.classify();
        Map<Edge, String> second = epa.classify();
        assertSame("classify() must memoize and return the same instance",
                   first, second);

        // Semantics still match the original (transient + persistent).
        assertEquals(EdgePersistenceAnalyzer.PERSISTENT, first.get(e1));
        assertEquals(EdgePersistenceAnalyzer.TRANSIENT, first.get(e2));
    }

    @Test
    public void testSummaryAndBucketsUseCachedClassification() {
        Edge e1 = new Edge("f", "A", "B");
        e1.setTimestamp(0L); e1.setEndTimestamp(1000L);
        graph.addEdge(e1, "A", "B");

        TemporalGraph tg = new TemporalGraph(graph);
        EdgePersistenceAnalyzer epa = new EdgePersistenceAnalyzer(tg, 10);

        // Drive summary() and getEdgesByClassification() and re-check
        // classify() identity to confirm nothing rebuilt the cache.
        Map<Edge, String> first = epa.classify();
        epa.summary();
        epa.getEdgesByClassification(EdgePersistenceAnalyzer.PERSISTENT);
        epa.getEdgesByClassification(EdgePersistenceAnalyzer.PERIODIC);
        epa.getEdgesByClassification(EdgePersistenceAnalyzer.TRANSIENT);
        Map<Edge, String> later = epa.classify();
        assertSame("summary/getEdgesByClassification must not invalidate cache",
                   first, later);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetEdgesByClassification_unknownStringRejected() {
        Edge e1 = new Edge("f", "A", "B");
        e1.setTimestamp(0L); e1.setEndTimestamp(1000L);
        graph.addEdge(e1, "A", "B");
        TemporalGraph tg = new TemporalGraph(graph);
        EdgePersistenceAnalyzer epa = new EdgePersistenceAnalyzer(tg, 5);
        // Common typo — must throw, not silently return empty set.
        epa.getEdgesByClassification("persistant");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetEdgesByClassification_wrongCaseRejected() {
        Edge e1 = new Edge("f", "A", "B");
        e1.setTimestamp(0L); e1.setEndTimestamp(1000L);
        graph.addEdge(e1, "A", "B");
        TemporalGraph tg = new TemporalGraph(graph);
        EdgePersistenceAnalyzer epa = new EdgePersistenceAnalyzer(tg, 5);
        epa.getEdgesByClassification("PERSISTENT");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetEdgesByClassification_nullRejected() {
        Edge e1 = new Edge("f", "A", "B");
        e1.setTimestamp(0L); e1.setEndTimestamp(1000L);
        graph.addEdge(e1, "A", "B");
        TemporalGraph tg = new TemporalGraph(graph);
        EdgePersistenceAnalyzer epa = new EdgePersistenceAnalyzer(tg, 5);
        epa.getEdgesByClassification(null);
    }

    // ── Constants ──────────────────────────────────────────────────

    @Test
    public void testConstants() {
        assertEquals("persistent", EdgePersistenceAnalyzer.PERSISTENT);
        assertEquals("periodic", EdgePersistenceAnalyzer.PERIODIC);
        assertEquals("transient", EdgePersistenceAnalyzer.TRANSIENT);
    }
}
