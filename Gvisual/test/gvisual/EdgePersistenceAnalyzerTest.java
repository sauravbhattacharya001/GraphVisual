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

    private Graph<String, edge> graph;

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
        edge e1 = new edge("f", "A", "B");
        e1.setTimestamp(0L);
        e1.setEndTimestamp(1000L);
        graph.addEdge(e1, "A", "B");

        TemporalGraph tg = new TemporalGraph(graph);
        EdgePersistenceAnalyzer epa = new EdgePersistenceAnalyzer(tg, 10);
        Map<edge, String> result = epa.classify();
        assertEquals(EdgePersistenceAnalyzer.PERSISTENT, result.get(e1));
    }

    @Test
    public void testClassify_transientEdge() {
        // Edge active only in a tiny window of a long timeline
        edge e1 = new edge("f", "A", "B");
        e1.setTimestamp(0L);
        e1.setEndTimestamp(1000L);

        edge e2 = new edge("f", "C", "D");
        e2.setTimestamp(0L);
        e2.setEndTimestamp(10L);  // only first ~1% of range

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "C", "D");

        TemporalGraph tg = new TemporalGraph(graph);
        EdgePersistenceAnalyzer epa = new EdgePersistenceAnalyzer(tg, 20);
        Map<edge, String> result = epa.classify();
        assertEquals(EdgePersistenceAnalyzer.TRANSIENT, result.get(e2));
    }

    @Test
    public void testClassify_periodicEdge() {
        // Edge active in about half the windows
        edge e1 = new edge("f", "A", "B");
        e1.setTimestamp(0L);
        e1.setEndTimestamp(1000L);  // full range marker

        edge e2 = new edge("f", "C", "D");
        e2.setTimestamp(0L);
        e2.setEndTimestamp(500L);  // half the range

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "C", "D");

        TemporalGraph tg = new TemporalGraph(graph);
        EdgePersistenceAnalyzer epa = new EdgePersistenceAnalyzer(tg, 10);
        Map<edge, String> result = epa.classify();
        // e2 should be periodic (present in ~50% of windows)
        String c2 = result.get(e2);
        assertTrue("Half-range edge should be periodic",
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
        edge e1 = new edge("f", "A", "B");
        e1.setTimestamp(100L);
        e1.setEndTimestamp(200L);
        graph.addEdge(e1, "A", "B");

        TemporalGraph tg = new TemporalGraph(graph);
        EdgePersistenceAnalyzer epa = new EdgePersistenceAnalyzer(tg, 1);
        Map<edge, String> result = epa.classify();
        // With 1 window, ratio is 1.0 => persistent
        assertEquals(EdgePersistenceAnalyzer.PERSISTENT, result.get(e1));
    }

    // ── Summary ────────────────────────────────────────────────────

    @Test
    public void testSummary_countsMatchClassify() {
        edge e1 = new edge("f", "A", "B");
        e1.setTimestamp(0L); e1.setEndTimestamp(1000L);
        edge e2 = new edge("f", "C", "D");
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
        edge e1 = new edge("f", "A", "B");
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
        edge e1 = new edge("f", "A", "B");
        e1.setTimestamp(0L); e1.setEndTimestamp(1000L);
        graph.addEdge(e1, "A", "B");

        TemporalGraph tg = new TemporalGraph(graph);
        EdgePersistenceAnalyzer epa = new EdgePersistenceAnalyzer(tg, 5);
        Set<edge> persistent = epa.getEdgesByClassification(EdgePersistenceAnalyzer.PERSISTENT);
        assertTrue(persistent.contains(e1));
    }

    @Test
    public void testGetEdgesByClassification_noMatch() {
        edge e1 = new edge("f", "A", "B");
        e1.setTimestamp(0L); e1.setEndTimestamp(1000L);
        graph.addEdge(e1, "A", "B");

        TemporalGraph tg = new TemporalGraph(graph);
        EdgePersistenceAnalyzer epa = new EdgePersistenceAnalyzer(tg, 5);
        Set<edge> transient_ = epa.getEdgesByClassification(EdgePersistenceAnalyzer.TRANSIENT);
        assertFalse(transient_.contains(e1));
    }

    // ── Constants ──────────────────────────────────────────────────

    @Test
    public void testConstants() {
        assertEquals("persistent", EdgePersistenceAnalyzer.PERSISTENT);
        assertEquals("periodic", EdgePersistenceAnalyzer.PERIODIC);
        assertEquals("transient", EdgePersistenceAnalyzer.TRANSIENT);
    }
}
