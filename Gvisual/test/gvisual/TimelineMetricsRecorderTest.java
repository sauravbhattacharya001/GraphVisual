package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link TimelineMetricsRecorder}.
 *
 * <p>Covers per-step snapshot capture, churn (new/lost nodes/edges)
 * accounting, CSV export (file + string), trend analysis (peaks,
 * averages, std dev, category min/max/avg), phase-transition
 * detection with the configurable threshold, the empty-recorder
 * edge cases, {@link TimelineMetricsRecorder#clear()}, and input
 * validation.</p>
 */
public class TimelineMetricsRecorderTest {

    // ---------- helpers ----------

    /** Builds an undirected graph containing the given edges (and their endpoints). */
    private static Graph<String, Edge> graphOf(Edge... edges) {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        for (Edge e : edges) {
            g.addVertex(e.getVertex1());
            g.addVertex(e.getVertex2());
            g.addEdge(e, e.getVertex1(), e.getVertex2());
        }
        return g;
    }

    /** Builds a friend-edge with a configurable weight. */
    private static Edge friend(String a, String b, float w) {
        Edge e = new Edge("f", a, b);
        e.setWeight(w);
        return e;
    }

    private static Edge edge(String type, String a, String b) {
        return new Edge(type, a, b);
    }

    private static List<Edge> list(Edge... es) {
        List<Edge> out = new ArrayList<Edge>();
        Collections.addAll(out, es);
        return out;
    }

    private static List<Edge> empty() {
        return Collections.<Edge>emptyList();
    }

    // ---------- basic recording ----------

    @Test
    public void newRecorderHasZeroSteps() {
        TimelineMetricsRecorder r = new TimelineMetricsRecorder();
        assertEquals(0, r.getStepCount());
        assertTrue(r.getSnapshots().isEmpty());
    }

    @Test
    public void firstStepHasAllNewAndZeroLost() {
        TimelineMetricsRecorder r = new TimelineMetricsRecorder();
        Edge ab = friend("A", "B", 1.0f);
        Edge bc = friend("B", "C", 2.0f);
        Graph<String, Edge> g = graphOf(ab, bc);

        r.recordStep("t0", g, list(ab, bc), empty(), empty(), empty(), empty());

        assertEquals(1, r.getStepCount());
        TimelineMetricsRecorder.StepSnapshot s = r.getSnapshots().get(0);
        assertEquals("t0", s.getLabel());
        assertEquals(0, s.getStepIndex());
        assertEquals(3, s.getNodeCount());
        assertEquals(2, s.getEdgeCount());
        assertEquals(2, s.getFriendCount());
        assertEquals(0, s.getClassmateCount());
        // density = 2E / (N*(N-1)) = 4 / 6
        assertEquals(2.0 / 3.0, s.getDensity(), 1e-9);
        // avg degree = 2E/N = 4/3
        assertEquals(4.0 / 3.0, s.getAvgDegree(), 1e-9);
        assertEquals(2, s.getMaxDegree());        // B has 2 neighbours
        assertEquals(1.5, s.getAvgWeight(), 1e-6);
        assertEquals(0, s.getIsolatedNodes());
        assertEquals(1, s.getComponentCount());

        // First step: everything is new, nothing lost.
        assertEquals(3, s.getNewNodes());
        assertEquals(0, s.getLostNodes());
        assertEquals(2, s.getNewEdges());
        assertEquals(0, s.getLostEdges());
    }

    @Test
    public void componentsAndIsolatesAreCounted() {
        TimelineMetricsRecorder r = new TimelineMetricsRecorder();
        Edge ab = friend("A", "B", 1.0f);
        Edge cd = friend("C", "D", 1.0f);
        Graph<String, Edge> g = graphOf(ab, cd);
        g.addVertex("E"); // isolated

        r.recordStep("t0", g, list(ab, cd), empty(), empty(), empty(), empty());

        TimelineMetricsRecorder.StepSnapshot s = r.getSnapshots().get(0);
        assertEquals(5, s.getNodeCount());
        assertEquals(1, s.getIsolatedNodes());
        assertEquals(3, s.getComponentCount()); // {A,B}, {C,D}, {E}
    }

    @Test
    public void categoryCountsOnlyTallyEdgesPresentInGraph() {
        TimelineMetricsRecorder r = new TimelineMetricsRecorder();
        Edge ab = friend("A", "B", 1.0f);
        Edge bc = edge("c", "B", "C");
        Edge cd = edge("fs", "C", "D");
        // graph contains ab and bc, but cd is in the "candidate" classmate list
        // even though it isn't in the graph -> should NOT be counted.
        Graph<String, Edge> g = graphOf(ab, bc);

        r.recordStep("t0", g,
            list(ab),                  // friends in graph
            list(cd),                  // familiar strangers — NOT in graph
            list(bc),                  // classmates in graph
            empty(), empty());

        TimelineMetricsRecorder.StepSnapshot s = r.getSnapshots().get(0);
        assertEquals(1, s.getFriendCount());
        assertEquals(0, s.getFsCount());
        assertEquals(1, s.getClassmateCount());
    }

    // ---------- churn accounting ----------

    @Test
    public void secondStepReportsCorrectChurn() {
        TimelineMetricsRecorder r = new TimelineMetricsRecorder();

        Edge ab = friend("A", "B", 1.0f);
        Edge bc = friend("B", "C", 1.0f);
        Graph<String, Edge> g0 = graphOf(ab, bc);
        r.recordStep("t0", g0, list(ab, bc), empty(), empty(), empty(), empty());

        // Step 1: drop AB, add CD (new node D, new edge CD; lose edge AB but A still present via no edge -> A still present? actually only if vertex stays)
        Edge cd = friend("C", "D", 1.0f);
        Graph<String, Edge> g1 = graphOf(bc, cd); // contains B,C,D; A is gone
        r.recordStep("t1", g1, list(bc, cd), empty(), empty(), empty(), empty());

        TimelineMetricsRecorder.StepSnapshot s = r.getSnapshots().get(1);
        assertEquals(1, s.getNewNodes());  // D
        assertEquals(1, s.getLostNodes()); // A
        assertEquals(1, s.getNewEdges());  // CD
        assertEquals(1, s.getLostEdges()); // AB
        assertEquals(1, s.getStepIndex());
    }

    // ---------- export ----------

    @Test
    public void exportCsvStringHasHeaderAndOneRowPerStep() {
        TimelineMetricsRecorder r = new TimelineMetricsRecorder();
        Edge ab = friend("A", "B", 1.0f);
        Graph<String, Edge> g = graphOf(ab);
        r.recordStep("t0", g, list(ab), empty(), empty(), empty(), empty());
        r.recordStep("t1", g, list(ab), empty(), empty(), empty(), empty());

        String csv = r.exportCsvString();
        BufferedReader br = new BufferedReader(new StringReader(csv));
        List<String> lines = new ArrayList<String>();
        try {
            String ln;
            while ((ln = br.readLine()) != null) lines.add(ln);
        } catch (IOException ioe) {
            fail("CSV reader threw: " + ioe.getMessage());
        }

        assertEquals(3, lines.size());
        assertEquals(TimelineMetricsRecorder.StepSnapshot.csvHeader(), lines.get(0));
        // step index is the first column
        assertTrue("step 0 row starts with 0,", lines.get(1).startsWith("0,"));
        assertTrue("step 1 row starts with 1,", lines.get(2).startsWith("1,"));
    }

    @Test
    public void exportCsvWritesFile() throws Exception {
        TimelineMetricsRecorder r = new TimelineMetricsRecorder();
        Edge ab = friend("A", "B", 1.0f);
        r.recordStep("t0", graphOf(ab), list(ab), empty(), empty(), empty(), empty());

        File f = File.createTempFile("timeline-metrics-test", ".csv");
        f.deleteOnExit();
        try {
            r.exportCsv(f);
            String content = new String(Files.readAllBytes(f.toPath()));
            assertTrue("CSV file must include the header",
                content.contains(TimelineMetricsRecorder.StepSnapshot.csvHeader()));
            assertTrue("CSV file must include the t0 row",
                content.contains(",t0,"));
        } finally {
            f.delete();
        }
    }

    @Test
    public void csvRowEscapesLabelsWithCommasAndQuotes() {
        TimelineMetricsRecorder r = new TimelineMetricsRecorder();
        Edge ab = friend("A", "B", 1.0f);
        // label contains a comma and a quote — must be RFC-4180-style escaped
        r.recordStep("hello, \"world\"", graphOf(ab),
            list(ab), empty(), empty(), empty(), empty());

        String csv = r.exportCsvString();
        // The escaped form should be: "hello, ""world"""
        assertTrue("label must be wrapped in quotes and embedded quotes doubled, got:\n" + csv,
            csv.contains("\"hello, \"\"world\"\"\""));
    }

    // ---------- trend analysis ----------

    @Test
    public void analyzeTrendsOnEmptyRecorderReturnsZeroes() {
        TimelineMetricsRecorder.TrendReport rep =
            new TimelineMetricsRecorder().analyzeTrends();
        assertEquals(0, rep.getTotalSteps());
        assertTrue(rep.getPhaseTransitions().isEmpty());
        assertTrue(rep.getCategoryTrends().isEmpty());
        assertEquals(0.0, rep.getMaxDensity(), 0.0);
    }

    @Test
    public void analyzeTrendsComputesPeaksAndAverages() {
        TimelineMetricsRecorder r = new TimelineMetricsRecorder();

        // step 0: 2 nodes, 1 edge
        Edge ab = friend("A", "B", 1.0f);
        r.recordStep("s0", graphOf(ab), list(ab), empty(), empty(), empty(), empty());

        // step 1: 4 nodes, 3 edges (the peak)
        Edge bc = friend("B", "C", 1.0f);
        Edge cd = friend("C", "D", 1.0f);
        r.recordStep("s1", graphOf(ab, bc, cd),
            list(ab, bc, cd), empty(), empty(), empty(), empty());

        // step 2: 3 nodes, 2 edges
        r.recordStep("s2", graphOf(ab, bc),
            list(ab, bc), empty(), empty(), empty(), empty());

        TimelineMetricsRecorder.TrendReport rep = r.analyzeTrends();
        assertEquals(3, rep.getTotalSteps());
        assertEquals(4, rep.getPeakNodeCount());
        assertEquals(1, rep.getPeakNodeStep());
        assertEquals(3, rep.getPeakEdgeCount());
        assertEquals(1, rep.getPeakEdgeStep());

        // averages — straightforward arithmetic over the 3 snapshots
        assertEquals((2 + 4 + 3) / 3.0, rep.getAvgNodeCount(), 1e-9);
        assertEquals((1 + 3 + 2) / 3.0, rep.getAvgEdgeCount(), 1e-9);

        // std dev: population std dev (divides by n)
        double mN = (2 + 4 + 3) / 3.0;
        double expectedSd = Math.sqrt(((2 - mN) * (2 - mN)
                                      + (4 - mN) * (4 - mN)
                                      + (3 - mN) * (3 - mN)) / 3.0);
        assertEquals(expectedSd, rep.getNodeCountStdDev(), 1e-9);

        // category trends are present and the friends min/max/avg make sense.
        double[] friends = rep.getCategoryTrends().get("friends");
        assertNotNull(friends);
        assertEquals(1.0, friends[0], 1e-9);                   // min
        assertEquals(3.0, friends[1], 1e-9);                   // max
        assertEquals((1 + 3 + 2) / 3.0, friends[2], 1e-9);     // avg
    }

    @Test
    public void phaseTransitionsDetectGrowthAndContraction() {
        TimelineMetricsRecorder r = new TimelineMetricsRecorder();
        r.setPhaseThreshold(0.3); // explicit; matches default

        // step 0: 10 nodes, 5 edges via a path
        Graph<String, Edge> g0 = new UndirectedSparseGraph<String, Edge>();
        List<Edge> friends0 = new ArrayList<Edge>();
        for (int i = 0; i < 10; i++) g0.addVertex("v" + i);
        for (int i = 0; i < 5; i++) {
            Edge e = friend("v" + i, "v" + (i + 1), 1.0f);
            g0.addEdge(e, e.getVertex1(), e.getVertex2());
            friends0.add(e);
        }
        r.recordStep("s0", g0, friends0, empty(), empty(), empty(), empty());

        // step 1: 20 nodes, 15 edges — a >100% growth in both, should be growth_spike
        Graph<String, Edge> g1 = new UndirectedSparseGraph<String, Edge>();
        List<Edge> friends1 = new ArrayList<Edge>();
        for (int i = 0; i < 20; i++) g1.addVertex("w" + i);
        for (int i = 0; i < 15; i++) {
            Edge e = friend("w" + i, "w" + (i + 1), 1.0f);
            g1.addEdge(e, e.getVertex1(), e.getVertex2());
            friends1.add(e);
        }
        r.recordStep("s1", g1, friends1, empty(), empty(), empty(), empty());

        // step 2: 5 nodes, 2 edges — sharp contraction
        Graph<String, Edge> g2 = new UndirectedSparseGraph<String, Edge>();
        List<Edge> friends2 = new ArrayList<Edge>();
        for (int i = 0; i < 5; i++) g2.addVertex("x" + i);
        for (int i = 0; i < 2; i++) {
            Edge e = friend("x" + i, "x" + (i + 1), 1.0f);
            g2.addEdge(e, e.getVertex1(), e.getVertex2());
            friends2.add(e);
        }
        r.recordStep("s2", g2, friends2, empty(), empty(), empty(), empty());

        TimelineMetricsRecorder.TrendReport rep = r.analyzeTrends();
        List<TimelineMetricsRecorder.PhaseTransition> ts = rep.getPhaseTransitions();
        assertEquals(2, ts.size());
        assertEquals("growth_spike",  ts.get(0).type);
        assertEquals(0, ts.get(0).fromStep);
        assertEquals(1, ts.get(0).toStep);
        assertEquals("contraction",   ts.get(1).type);
        assertEquals(1, ts.get(1).fromStep);
        assertEquals(2, ts.get(1).toStep);
    }

    @Test
    public void phaseTransitionsRespectThreshold() {
        TimelineMetricsRecorder r = new TimelineMetricsRecorder();
        r.setPhaseThreshold(0.9); // only > 90% changes should fire

        // Build two graphs that differ by ~10% in nodes and edges —
        // well below the 90% threshold so no transition should fire.
        Graph<String, Edge> g0 = new UndirectedSparseGraph<String, Edge>();
        List<Edge> friends0 = new ArrayList<Edge>();
        for (int i = 0; i < 10; i++) g0.addVertex("v" + i);
        for (int i = 0; i < 10; i++) {
            Edge e = friend("v" + i, "v" + ((i + 1) % 10), 1.0f);
            g0.addEdge(e, e.getVertex1(), e.getVertex2());
            friends0.add(e);
        }
        r.recordStep("s0", g0, friends0, empty(), empty(), empty(), empty());

        Graph<String, Edge> g1 = new UndirectedSparseGraph<String, Edge>();
        List<Edge> friends1 = new ArrayList<Edge>();
        for (int i = 0; i < 11; i++) g1.addVertex("v" + i);
        for (int i = 0; i < 11; i++) {
            Edge e = friend("v" + i, "v" + ((i + 1) % 11), 1.0f);
            g1.addEdge(e, e.getVertex1(), e.getVertex2());
            friends1.add(e);
        }
        r.recordStep("s1", g1, friends1, empty(), empty(), empty(), empty());

        TimelineMetricsRecorder.TrendReport rep = r.analyzeTrends();
        assertTrue("no phase transition expected when growth < threshold",
            rep.getPhaseTransitions().isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setPhaseThresholdRejectsZero() {
        new TimelineMetricsRecorder().setPhaseThreshold(0.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setPhaseThresholdRejectsAboveOne() {
        new TimelineMetricsRecorder().setPhaseThreshold(1.5);
    }

    @Test
    public void setPhaseThresholdAcceptsBoundary() {
        TimelineMetricsRecorder r = new TimelineMetricsRecorder();
        r.setPhaseThreshold(1.0);
        assertEquals(1.0, r.getPhaseThreshold(), 0.0);
    }

    // ---------- validation / lifecycle ----------

    @Test(expected = IllegalArgumentException.class)
    public void recordStepRejectsNullGraph() {
        new TimelineMetricsRecorder().recordStep(
            "t", null, empty(), empty(), empty(), empty(), empty());
    }

    @Test
    public void clearResetsStepIndexAndChurnBaseline() {
        TimelineMetricsRecorder r = new TimelineMetricsRecorder();
        Edge ab = friend("A", "B", 1.0f);
        r.recordStep("s0", graphOf(ab), list(ab), empty(), empty(), empty(), empty());
        r.recordStep("s1", graphOf(ab), list(ab), empty(), empty(), empty(), empty());
        assertEquals(2, r.getStepCount());

        r.clear();
        assertEquals(0, r.getStepCount());
        assertTrue(r.getSnapshots().isEmpty());

        // After clear, the very next recordStep behaves like "first step":
        // every node/edge counts as new, none as lost.
        r.recordStep("s0", graphOf(ab), list(ab), empty(), empty(), empty(), empty());
        TimelineMetricsRecorder.StepSnapshot s = r.getSnapshots().get(0);
        assertEquals(0, s.getStepIndex());
        assertEquals(2, s.getNewNodes());
        assertEquals(0, s.getLostNodes());
        assertEquals(1, s.getNewEdges());
        assertEquals(0, s.getLostEdges());
    }

    @Test
    public void snapshotsListIsUnmodifiable() {
        TimelineMetricsRecorder r = new TimelineMetricsRecorder();
        try {
            r.getSnapshots().add(null);
            fail("Expected UnsupportedOperationException — snapshots list must be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }
}
