package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Comprehensive tests for GrowthRateAnalyzer.
 * Covers: constructor validation, analyze() snapshots, edgeGrowthRate()
 * linear regression, density, clustering coefficient, edge cases.
 */
public class GrowthRateAnalyzerTest {

    private Graph<String, edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, edge>();
    }

    // ─── Helper methods ───

    private edge makeEdge(String type, String v1, String v2, long start, long end) {
        edge e = new edge(type, v1, v2);
        e.setTimestamp(start);
        e.setEndTimestamp(end);
        return e;
    }

    private edge makeEdge(String type, String v1, String v2, long timestamp) {
        edge e = new edge(type, v1, v2);
        e.setTimestamp(timestamp);
        return e;
    }

    private void addEdge(Graph<String, edge> g, edge e) {
        String v1 = e.getVertex1();
        String v2 = e.getVertex2();
        if (!g.containsVertex(v1)) g.addVertex(v1);
        if (!g.containsVertex(v2)) g.addVertex(v2);
        g.addEdge(e, v1, v2);
    }

    // ─── Constructor validation ───

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_nullTemporalGraph() {
        new GrowthRateAnalyzer(null, 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_zeroWindows() {
        TemporalGraph tg = new TemporalGraph(graph);
        new GrowthRateAnalyzer(tg, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_negativeWindows() {
        TemporalGraph tg = new TemporalGraph(graph);
        new GrowthRateAnalyzer(tg, -1);
    }

    @Test
    public void testConstructor_validArguments() {
        edge e = makeEdge("f", "A", "B", 100L, 200L);
        addEdge(graph, e);
        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 3);
        assertNotNull(analyzer);
    }

    @Test
    public void testConstructor_singleWindow() {
        edge e = makeEdge("f", "A", "B", 100L, 200L);
        addEdge(graph, e);
        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 1);
        assertNotNull(analyzer);
    }

    // ─── analyze() ───

    @Test
    public void testAnalyze_singleWindowSingleEdge() {
        edge e = makeEdge("f", "A", "B", 100L, 200L);
        addEdge(graph, e);
        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 1);

        List<GrowthRateAnalyzer.MetricSnapshot> snapshots = analyzer.analyze();
        assertEquals(1, snapshots.size());
        assertEquals(2, snapshots.get(0).nodeCount);
        assertEquals(1, snapshots.get(0).edgeCount);
    }

    @Test
    public void testAnalyze_multipleWindows() {
        // Build a growing graph: edges appear at different times
        edge e1 = makeEdge("f", "A", "B", 100L, 500L);
        edge e2 = makeEdge("f", "B", "C", 200L, 500L);
        edge e3 = makeEdge("f", "C", "D", 300L, 500L);
        addEdge(graph, e1);
        addEdge(graph, e2);
        addEdge(graph, e3);

        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 3);

        List<GrowthRateAnalyzer.MetricSnapshot> snapshots = analyzer.analyze();
        assertEquals(3, snapshots.size());

        // Each window should have non-negative node/edge counts
        for (GrowthRateAnalyzer.MetricSnapshot snap : snapshots) {
            assertTrue(snap.nodeCount >= 0);
            assertTrue(snap.edgeCount >= 0);
        }
    }

    @Test
    public void testAnalyze_snapshotsAreOrdered() {
        edge e1 = makeEdge("f", "A", "B", 100L, 400L);
        edge e2 = makeEdge("f", "C", "D", 200L, 400L);
        addEdge(graph, e1);
        addEdge(graph, e2);

        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 4);

        List<GrowthRateAnalyzer.MetricSnapshot> snapshots = analyzer.analyze();
        for (int i = 1; i < snapshots.size(); i++) {
            assertTrue(snapshots.get(i).windowStart >= snapshots.get(i - 1).windowStart);
        }
    }

    @Test
    public void testAnalyze_growingNetwork() {
        // Phase 1: 1 edge (t=0..1000)
        edge e1 = makeEdge("f", "A", "B", 0L, 1000L);
        addEdge(graph, e1);
        // Phase 2: 2 more edges (t=500..1000)
        edge e2 = makeEdge("f", "B", "C", 500L, 1000L);
        edge e3 = makeEdge("f", "C", "A", 500L, 1000L);
        addEdge(graph, e2);
        addEdge(graph, e3);

        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 2);

        List<GrowthRateAnalyzer.MetricSnapshot> snapshots = analyzer.analyze();
        assertEquals(2, snapshots.size());
        // Second window should have more edges
        assertTrue(snapshots.get(1).edgeCount >= snapshots.get(0).edgeCount);
    }

    // ─── Density calculations ───

    @Test
    public void testAnalyze_densityOfCompleteTriangle() {
        // Triangle: 3 nodes, 3 edges → density = 3/3 = 1.0
        edge e1 = makeEdge("f", "A", "B", 100L, 200L);
        edge e2 = makeEdge("f", "B", "C", 100L, 200L);
        edge e3 = makeEdge("f", "A", "C", 100L, 200L);
        addEdge(graph, e1);
        addEdge(graph, e2);
        addEdge(graph, e3);

        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 1);

        List<GrowthRateAnalyzer.MetricSnapshot> snapshots = analyzer.analyze();
        assertEquals(1.0, snapshots.get(0).density, 0.001);
    }

    @Test
    public void testAnalyze_densityOfPath() {
        // Path A-B-C: 3 nodes, 2 edges → density = 2/3 ≈ 0.667
        edge e1 = makeEdge("f", "A", "B", 100L, 200L);
        edge e2 = makeEdge("f", "B", "C", 100L, 200L);
        addEdge(graph, e1);
        addEdge(graph, e2);

        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 1);

        List<GrowthRateAnalyzer.MetricSnapshot> snapshots = analyzer.analyze();
        assertEquals(2.0 / 3.0, snapshots.get(0).density, 0.001);
    }

    @Test
    public void testAnalyze_densityOfSingleEdge() {
        // 2 nodes, 1 edge → density = 1/1 = 1.0
        edge e1 = makeEdge("f", "A", "B", 100L, 200L);
        addEdge(graph, e1);

        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 1);

        List<GrowthRateAnalyzer.MetricSnapshot> snapshots = analyzer.analyze();
        assertEquals(1.0, snapshots.get(0).density, 0.001);
    }

    // ─── Clustering coefficient ───

    @Test
    public void testAnalyze_clusteringOfTriangle() {
        // Complete triangle: all neighbors are connected → clustering = 1.0
        edge e1 = makeEdge("f", "A", "B", 100L, 200L);
        edge e2 = makeEdge("f", "B", "C", 100L, 200L);
        edge e3 = makeEdge("f", "A", "C", 100L, 200L);
        addEdge(graph, e1);
        addEdge(graph, e2);
        addEdge(graph, e3);

        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 1);

        List<GrowthRateAnalyzer.MetricSnapshot> snapshots = analyzer.analyze();
        assertEquals(1.0, snapshots.get(0).avgClusteringCoefficient, 0.001);
    }

    @Test
    public void testAnalyze_clusteringOfPath() {
        // Path A-B-C: B has 2 neighbors not connected → clustering(B) = 0
        // A and C each have 1 neighbor → clustering = 0
        // Average = 0
        edge e1 = makeEdge("f", "A", "B", 100L, 200L);
        edge e2 = makeEdge("f", "B", "C", 100L, 200L);
        addEdge(graph, e1);
        addEdge(graph, e2);

        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 1);

        List<GrowthRateAnalyzer.MetricSnapshot> snapshots = analyzer.analyze();
        assertEquals(0.0, snapshots.get(0).avgClusteringCoefficient, 0.001);
    }

    @Test
    public void testAnalyze_clusteringOfStar() {
        // Star: A connected to B,C,D,E but no connections among B,C,D,E
        // clustering(A) = 0 (no triangles), others have degree 1 (skip)
        edge e1 = makeEdge("f", "A", "B", 100L, 200L);
        edge e2 = makeEdge("f", "A", "C", 100L, 200L);
        edge e3 = makeEdge("f", "A", "D", 100L, 200L);
        edge e4 = makeEdge("f", "A", "E", 100L, 200L);
        addEdge(graph, e1);
        addEdge(graph, e2);
        addEdge(graph, e3);
        addEdge(graph, e4);

        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 1);

        List<GrowthRateAnalyzer.MetricSnapshot> snapshots = analyzer.analyze();
        assertEquals(0.0, snapshots.get(0).avgClusteringCoefficient, 0.001);
    }

    // ─── edgeGrowthRate() ───

    @Test
    public void testEdgeGrowthRate_growingNetwork() {
        // Edges appearing over time: should have positive growth rate
        edge e1 = makeEdge("f", "A", "B", 0L, 1000L);
        edge e2 = makeEdge("f", "B", "C", 250L, 1000L);
        edge e3 = makeEdge("f", "C", "D", 500L, 1000L);
        edge e4 = makeEdge("f", "D", "E", 750L, 1000L);
        addEdge(graph, e1);
        addEdge(graph, e2);
        addEdge(graph, e3);
        addEdge(graph, e4);

        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 4);

        double rate = analyzer.edgeGrowthRate();
        assertTrue("Growth rate should be positive for growing network", rate > 0);
    }

    @Test
    public void testEdgeGrowthRate_shrinkingNetwork() {
        // Edges disappearing over time: should have negative growth rate
        edge e1 = makeEdge("f", "A", "B", 0L, 1000L);
        edge e2 = makeEdge("f", "B", "C", 0L, 250L);
        edge e3 = makeEdge("f", "C", "D", 0L, 500L);
        addEdge(graph, e1);
        addEdge(graph, e2);
        addEdge(graph, e3);

        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 4);

        double rate = analyzer.edgeGrowthRate();
        assertTrue("Growth rate should be negative for shrinking network", rate < 0);
    }

    @Test
    public void testEdgeGrowthRate_stableNetwork() {
        // All edges active for entire duration: constant count → rate ≈ 0
        edge e1 = makeEdge("f", "A", "B", 0L, 1000L);
        edge e2 = makeEdge("f", "B", "C", 0L, 1000L);
        edge e3 = makeEdge("f", "C", "A", 0L, 1000L);
        addEdge(graph, e1);
        addEdge(graph, e2);
        addEdge(graph, e3);

        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 5);

        double rate = analyzer.edgeGrowthRate();
        assertEquals(0.0, rate, 0.001);
    }

    @Test
    public void testEdgeGrowthRate_singleWindow() {
        // With only 1 window, can't compute regression → returns 0
        edge e = makeEdge("f", "A", "B", 100L, 200L);
        addEdge(graph, e);

        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 1);

        double rate = analyzer.edgeGrowthRate();
        assertEquals(0.0, rate, 0.001);
    }

    // ─── MetricSnapshot ───

    @Test
    public void testMetricSnapshot_toString() {
        GrowthRateAnalyzer.MetricSnapshot snap =
            new GrowthRateAnalyzer.MetricSnapshot(1000L, 5, 8, 0.5, 0.75);
        String s = snap.toString();
        assertTrue(s.contains("t=1000"));
        assertTrue(s.contains("nodes=5"));
        assertTrue(s.contains("edges=8"));
        assertTrue(s.contains("0.5000"));
        assertTrue(s.contains("0.7500"));
    }

    @Test
    public void testMetricSnapshot_fields() {
        GrowthRateAnalyzer.MetricSnapshot snap =
            new GrowthRateAnalyzer.MetricSnapshot(500L, 3, 2, 0.6667, 0.0);
        assertEquals(500L, snap.windowStart);
        assertEquals(3, snap.nodeCount);
        assertEquals(2, snap.edgeCount);
        assertEquals(0.6667, snap.density, 0.001);
        assertEquals(0.0, snap.avgClusteringCoefficient, 0.001);
    }

    // ─── Complex scenarios ───

    @Test
    public void testAnalyze_disconnectedComponents() {
        // Two disconnected edges at same time
        edge e1 = makeEdge("f", "A", "B", 100L, 200L);
        edge e2 = makeEdge("f", "C", "D", 100L, 200L);
        addEdge(graph, e1);
        addEdge(graph, e2);

        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 1);

        List<GrowthRateAnalyzer.MetricSnapshot> snapshots = analyzer.analyze();
        assertEquals(4, snapshots.get(0).nodeCount);
        assertEquals(2, snapshots.get(0).edgeCount);
        // Density: 2 / C(4,2) = 2/6 ≈ 0.333
        assertEquals(2.0 / 6.0, snapshots.get(0).density, 0.001);
    }

    @Test
    public void testAnalyze_edgesWithDifferentTypes() {
        // Multiple edge types shouldn't affect metric computation
        edge e1 = makeEdge("friendship", "A", "B", 100L, 200L);
        edge e2 = makeEdge("work", "B", "C", 100L, 200L);
        edge e3 = makeEdge("family", "C", "A", 100L, 200L);
        addEdge(graph, e1);
        addEdge(graph, e2);
        addEdge(graph, e3);

        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 1);

        List<GrowthRateAnalyzer.MetricSnapshot> snapshots = analyzer.analyze();
        assertEquals(3, snapshots.get(0).nodeCount);
        assertEquals(3, snapshots.get(0).edgeCount);
        assertEquals(1.0, snapshots.get(0).density, 0.001);
    }

    @Test
    public void testAnalyze_windowWithNoEdges() {
        // Edge only active in first half of time range
        // Last windows should have 0 edges
        edge e1 = makeEdge("f", "A", "B", 0L, 100L);
        addEdge(graph, e1);
        // Add another edge later to extend time range
        edge e2 = makeEdge("f", "C", "D", 900L, 1000L);
        addEdge(graph, e2);

        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 5);

        List<GrowthRateAnalyzer.MetricSnapshot> snapshots = analyzer.analyze();
        assertEquals(5, snapshots.size());
        // Middle windows should have 0 edges
        boolean foundEmpty = false;
        for (GrowthRateAnalyzer.MetricSnapshot snap : snapshots) {
            if (snap.edgeCount == 0) {
                foundEmpty = true;
                assertEquals(0, snap.nodeCount);
                assertEquals(0.0, snap.density, 0.001);
            }
        }
        assertTrue("Should have at least one empty window", foundEmpty);
    }

    @Test
    public void testAnalyze_k4CompleteGraph() {
        // K4: 4 nodes, 6 edges → density = 1.0
        String[] verts = {"A", "B", "C", "D"};
        int id = 0;
        for (int i = 0; i < 4; i++) {
            for (int j = i + 1; j < 4; j++) {
                edge e = makeEdge("f", verts[i], verts[j], 100L, 200L);
                addEdge(graph, e);
                id++;
            }
        }

        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 1);

        List<GrowthRateAnalyzer.MetricSnapshot> snapshots = analyzer.analyze();
        assertEquals(4, snapshots.get(0).nodeCount);
        assertEquals(6, snapshots.get(0).edgeCount);
        assertEquals(1.0, snapshots.get(0).density, 0.001);
        // K4 has perfect clustering
        assertEquals(1.0, snapshots.get(0).avgClusteringCoefficient, 0.001);
    }

    @Test
    public void testAnalyze_manyWindows() {
        // Test with many windows (10)
        edge e1 = makeEdge("f", "A", "B", 0L, 1000L);
        edge e2 = makeEdge("f", "B", "C", 500L, 1000L);
        addEdge(graph, e1);
        addEdge(graph, e2);

        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 10);

        List<GrowthRateAnalyzer.MetricSnapshot> snapshots = analyzer.analyze();
        assertEquals(10, snapshots.size());
    }

    @Test
    public void testEdgeGrowthRate_linearGrowth() {
        // Build a network where edges appear linearly
        for (int i = 0; i < 10; i++) {
            String v1 = "V" + i;
            String v2 = "V" + (i + 1);
            long start = i * 100L;
            edge e = makeEdge("f", v1, v2, start, 1000L);
            addEdge(graph, e);
        }

        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 10);

        double rate = analyzer.edgeGrowthRate();
        assertTrue("Linear growth should have positive rate", rate > 0);
    }

    @Test
    public void testAnalyze_snapshotCountMatchesWindowCount() {
        edge e = makeEdge("f", "A", "B", 0L, 100L);
        addEdge(graph, e);
        TemporalGraph tg = new TemporalGraph(graph);

        for (int windows = 1; windows <= 5; windows++) {
            GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, windows);
            List<GrowthRateAnalyzer.MetricSnapshot> snapshots = analyzer.analyze();
            assertEquals("Window count " + windows, windows, snapshots.size());
        }
    }

    @Test
    public void testEdgeGrowthRate_twoWindows() {
        // 2 windows: window 1 has 1 edge, window 2 has 3 edges
        // slope = (3-1)/1 = 2.0
        edge e1 = makeEdge("f", "A", "B", 0L, 200L);
        edge e2 = makeEdge("f", "B", "C", 100L, 200L);
        edge e3 = makeEdge("f", "C", "D", 100L, 200L);
        addEdge(graph, e1);
        addEdge(graph, e2);
        addEdge(graph, e3);

        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 2);

        double rate = analyzer.edgeGrowthRate();
        assertTrue("Should have positive growth", rate > 0);
    }

    @Test
    public void testAnalyze_densityWithEmptyWindow() {
        // Windows with 0 or 1 nodes should have density 0
        edge e1 = makeEdge("f", "A", "B", 0L, 50L);
        edge e2 = makeEdge("f", "C", "D", 950L, 1000L);
        addEdge(graph, e1);
        addEdge(graph, e2);

        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 5);

        List<GrowthRateAnalyzer.MetricSnapshot> snapshots = analyzer.analyze();
        for (GrowthRateAnalyzer.MetricSnapshot snap : snapshots) {
            if (snap.nodeCount < 2) {
                assertEquals(0.0, snap.density, 0.001);
            }
        }
    }

    @Test
    public void testAnalyze_clusteringWithPartialTriangles() {
        // Square: A-B-C-D-A (4 nodes, 4 edges, no diagonals)
        // Each node has degree 2, neighbors not connected → clustering = 0
        edge e1 = makeEdge("f", "A", "B", 100L, 200L);
        edge e2 = makeEdge("f", "B", "C", 100L, 200L);
        edge e3 = makeEdge("f", "C", "D", 100L, 200L);
        edge e4 = makeEdge("f", "D", "A", 100L, 200L);
        addEdge(graph, e1);
        addEdge(graph, e2);
        addEdge(graph, e3);
        addEdge(graph, e4);

        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 1);

        List<GrowthRateAnalyzer.MetricSnapshot> snapshots = analyzer.analyze();
        assertEquals(0.0, snapshots.get(0).avgClusteringCoefficient, 0.001);
    }

    @Test
    public void testAnalyze_clusteringWithOneDiagonal() {
        // Square with one diagonal: A-B-C-D-A + A-C
        // A: neighbors {B,C,D}. B-C? yes. B-D? yes(via D-A? no, D is neighbor of A not B). 
        // Let me think: A has neighbors B,C,D; pairs: (B,C)=yes, (B,D)=no, (C,D)=yes → 2/3
        // B: neighbors A,C; pair (A,C)=yes → 1/1 = 1.0
        // C: neighbors A,B,D; pairs: (A,B)=yes, (A,D)=yes, (B,D)=no → 2/3
        // D: neighbors A,C; pair (A,C)=yes → 1/1 = 1.0
        // Average = (2/3 + 1 + 2/3 + 1) / 4 = (10/3) / 4 ≈ 0.833
        edge e1 = makeEdge("f", "A", "B", 100L, 200L);
        edge e2 = makeEdge("f", "B", "C", 100L, 200L);
        edge e3 = makeEdge("f", "C", "D", 100L, 200L);
        edge e4 = makeEdge("f", "D", "A", 100L, 200L);
        edge e5 = makeEdge("f", "A", "C", 100L, 200L);
        addEdge(graph, e1);
        addEdge(graph, e2);
        addEdge(graph, e3);
        addEdge(graph, e4);
        addEdge(graph, e5);

        TemporalGraph tg = new TemporalGraph(graph);
        GrowthRateAnalyzer analyzer = new GrowthRateAnalyzer(tg, 1);

        List<GrowthRateAnalyzer.MetricSnapshot> snapshots = analyzer.analyze();
        double expected = (2.0/3.0 + 1.0 + 2.0/3.0 + 1.0) / 4.0;
        assertEquals(expected, snapshots.get(0).avgClusteringCoefficient, 0.01);
    }
}
