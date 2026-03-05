package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for TemporalGraph, EdgePersistenceAnalyzer, and GrowthRateAnalyzer.
 */
public class TemporalGraphTest {

    private Graph<String, edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
    }

    // ─── edge temporal methods ───

    @Test
    public void testEdgeIsActiveAt_untimedAlwaysActive() {
        edge e = new edge("f", "A", "B");
        assertTrue(e.isActiveAt(1000L));
        assertTrue(e.isActiveAt(0L));
    }

    @Test
    public void testEdgeIsActiveAt_pointInTime() {
        edge e = new edge("f", "A", "B");
        e.setTimestamp(500L);
        assertTrue(e.isActiveAt(500L));
        assertFalse(e.isActiveAt(499L));
        assertFalse(e.isActiveAt(501L));
    }

    @Test
    public void testEdgeIsActiveAt_interval() {
        edge e = new edge("f", "A", "B");
        e.setTimestamp(100L);
        e.setEndTimestamp(200L);
        assertFalse(e.isActiveAt(99L));
        assertTrue(e.isActiveAt(100L));
        assertTrue(e.isActiveAt(150L));
        assertTrue(e.isActiveAt(200L));
        assertFalse(e.isActiveAt(201L));
    }

    @Test
    public void testEdgeIsActiveDuring_overlap() {
        edge e = new edge("f", "A", "B");
        e.setTimestamp(100L);
        e.setEndTimestamp(200L);
        assertTrue(e.isActiveDuring(50L, 150L));
        assertTrue(e.isActiveDuring(150L, 250L));
        assertTrue(e.isActiveDuring(100L, 200L));
        assertFalse(e.isActiveDuring(201L, 300L));
        assertFalse(e.isActiveDuring(0L, 99L));
    }

    @Test
    public void testEdgeIsActiveDuring_untimedAlwaysActive() {
        edge e = new edge("f", "A", "B");
        assertTrue(e.isActiveDuring(0L, 1000L));
    }

    // ─── TemporalGraph ───

    @Test(expected = IllegalArgumentException.class)
    public void testTemporalGraphNullThrows() {
        new TemporalGraph(null);
    }

    @Test
    public void testSnapshotAt_filtersCorrectly() {
        edge e1 = new edge("f", "A", "B");
        e1.setTimestamp(100L);
        e1.setEndTimestamp(200L);

        edge e2 = new edge("f", "B", "C");
        e2.setTimestamp(300L);
        e2.setEndTimestamp(400L);

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");

        TemporalGraph tg = new TemporalGraph(graph);

        Graph<String, edge> snap150 = tg.snapshotAt(150L);
        assertEquals(1, snap150.getEdgeCount());
        assertTrue(snap150.containsEdge(e1));

        Graph<String, edge> snap350 = tg.snapshotAt(350L);
        assertEquals(1, snap350.getEdgeCount());
        assertTrue(snap350.containsEdge(e2));

        Graph<String, edge> snap250 = tg.snapshotAt(250L);
        assertEquals(0, snap250.getEdgeCount());
    }

    @Test
    public void testWindowBetween_includesOverlappingEdges() {
        edge e1 = new edge("f", "A", "B");
        e1.setTimestamp(100L);
        e1.setEndTimestamp(200L);

        edge e2 = new edge("f", "C", "D");
        e2.setTimestamp(300L);
        e2.setEndTimestamp(400L);

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "C", "D");

        TemporalGraph tg = new TemporalGraph(graph);

        Graph<String, edge> window = tg.windowBetween(150L, 350L);
        assertEquals(2, window.getEdgeCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWindowBetween_startAfterEndThrows() {
        TemporalGraph tg = new TemporalGraph(graph);
        tg.windowBetween(200L, 100L);
    }

    @Test
    public void testGetTimePoints_sorted() {
        edge e1 = new edge("f", "A", "B");
        e1.setTimestamp(300L);
        edge e2 = new edge("f", "C", "D");
        e2.setTimestamp(100L);
        edge e3 = new edge("f", "E", "F");
        e3.setTimestamp(200L);

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "C", "D");
        graph.addEdge(e3, "E", "F");

        TemporalGraph tg = new TemporalGraph(graph);
        List<Long> times = tg.getTimePoints();
        assertEquals(Arrays.asList(100L, 200L, 300L), times);
    }

    @Test
    public void testGetTimePoints_excludesUntimed() {
        edge e1 = new edge("f", "A", "B");
        e1.setTimestamp(100L);
        edge e2 = new edge("f", "C", "D"); // no timestamp

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "C", "D");

        TemporalGraph tg = new TemporalGraph(graph);
        assertEquals(1, tg.getTimePoints().size());
    }

    @Test
    public void testGenerateWindows_correctCount() {
        edge e1 = new edge("f", "A", "B");
        e1.setTimestamp(0L);
        e1.setEndTimestamp(100L);
        edge e2 = new edge("f", "C", "D");
        e2.setTimestamp(50L);
        e2.setEndTimestamp(150L);

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "C", "D");

        TemporalGraph tg = new TemporalGraph(graph);
        List<Map.Entry<Long, Graph<String, edge>>> windows = tg.generateWindows(3);
        assertEquals(3, windows.size());
    }

    // ─── EdgePersistenceAnalyzer ───

    @Test
    public void testEdgePersistence_classification() {
        // e1: active across all windows (0-1000)
        edge e1 = new edge("f", "A", "B");
        e1.setTimestamp(0L);
        e1.setEndTimestamp(1000L);

        // e2: active in a small window only
        edge e2 = new edge("f", "C", "D");
        e2.setTimestamp(0L);
        e2.setEndTimestamp(100L);

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "C", "D");

        TemporalGraph tg = new TemporalGraph(graph);
        EdgePersistenceAnalyzer analyzer = new EdgePersistenceAnalyzer(tg, 10);
        Map<edge, String> result = analyzer.classify();

        assertEquals(EdgePersistenceAnalyzer.PERSISTENT, result.get(e1));
        assertEquals(EdgePersistenceAnalyzer.TRANSIENT, result.get(e2));
    }

    @Test
    public void testEdgePersistence_summary() {
        edge e1 = new edge("f", "A", "B");
        e1.setTimestamp(0L);
        e1.setEndTimestamp(1000L);
        edge e2 = new edge("f", "C", "D");
        e2.setTimestamp(0L);
        e2.setEndTimestamp(50L);

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "C", "D");

        TemporalGraph tg = new TemporalGraph(graph);
        EdgePersistenceAnalyzer analyzer = new EdgePersistenceAnalyzer(tg, 10);
        Map<String, Integer> summary = analyzer.summary();

        assertEquals(3, summary.size());
        assertTrue(summary.containsKey(EdgePersistenceAnalyzer.PERSISTENT));
        assertTrue(summary.containsKey(EdgePersistenceAnalyzer.PERIODIC));
        assertTrue(summary.containsKey(EdgePersistenceAnalyzer.TRANSIENT));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEdgePersistence_nullGraphThrows() {
        new EdgePersistenceAnalyzer(null, 5);
    }

    // ─── GrowthRateAnalyzer ───

    @Test
    public void testGrowthRate_growingNetwork() {
        // Network grows: more edges appear in later windows
        for (int i = 0; i < 10; i++) {
            edge e = new edge("f", "N" + i, "N" + (i + 1));
            e.setTimestamp((long) i * 100);
            e.setEndTimestamp(1000L); // stays active once created
            graph.addEdge(e, "N" + i, "N" + (i + 1));
        }

        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 5);
        double rate = analyzer.edgeGrowthRate();
        assertTrue("Growing network should have positive growth rate", rate > 0);
    }

    @Test
    public void testGrowthRate_analyzeReturnsCorrectWindows() {
        edge e = new edge("f", "A", "B");
        e.setTimestamp(0L);
        e.setEndTimestamp(100L);
        graph.addEdge(e, "A", "B");

        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 3);
        List<GrowthRateAnalyzer.MetricSnapshot> snapshots = analyzer.analyze();
        assertEquals(3, snapshots.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGrowthRate_nullGraphThrows() {
        new GrowthRateAnalyzer(null, 5);
    }
}
