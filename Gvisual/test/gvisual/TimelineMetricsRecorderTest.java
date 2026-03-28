package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.*;
import java.util.*;

/**
 * Tests for TimelineMetricsRecorder — snapshot recording, CSV export,
 * trend analysis, phase transitions, and edge cases.
 */
public class TimelineMetricsRecorderTest {

    private TimelineMetricsRecorder recorder;

    @Before
    public void setUp() {
        recorder = new TimelineMetricsRecorder();
    }

    private edge makeEdge(String v1, String v2) {
        return makeEdge(v1, v2, "f", 1.0f);
    }

    private edge makeEdge(String v1, String v2, String type, float weight) {
        edge e = new edge(type, v1, v2);
        e.setWeight(weight);
        return e;
    }

    private List<edge> emptyList() {
        return new ArrayList<>();
    }

    // --- Basic recording ---

    @Test
    public void testEmptyRecorder() {
        assertEquals(0, recorder.getStepCount());
        assertTrue(recorder.getSnapshots().isEmpty());
    }

    @Test
    public void testRecordSingleStep() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B");
        edge e = makeEdge("A", "B");
        g.addEdge(e, "A", "B");

        recorder.recordStep("step0", g, Arrays.asList(e), emptyList(), emptyList(), emptyList(), emptyList());

        assertEquals(1, recorder.getStepCount());
        TimelineMetricsRecorder.StepSnapshot snap = recorder.getSnapshots().get(0);
        assertEquals("step0", snap.getLabel());
        assertEquals(0, snap.getStepIndex());
        assertEquals(2, snap.getNodeCount());
        assertEquals(1, snap.getEdgeCount());
        assertEquals(1, snap.getFriendCount());
        assertEquals(2, snap.getNewNodes());
        assertEquals(0, snap.getLostNodes());
    }

    @Test
    public void testRecordMultipleSteps() {
        Graph<String, edge> g1 = new UndirectedSparseGraph<>();
        g1.addVertex("A"); g1.addVertex("B");
        edge e1 = makeEdge("A", "B");
        g1.addEdge(e1, "A", "B");
        recorder.recordStep("t1", g1, Arrays.asList(e1), emptyList(), emptyList(), emptyList(), emptyList());

        Graph<String, edge> g2 = new UndirectedSparseGraph<>();
        g2.addVertex("A"); g2.addVertex("B"); g2.addVertex("C");
        edge e2a = makeEdge("A", "B");
        edge e2b = makeEdge("B", "C");
        g2.addEdge(e2a, "A", "B"); g2.addEdge(e2b, "B", "C");
        recorder.recordStep("t2", g2, Arrays.asList(e2a, e2b), emptyList(), emptyList(), emptyList(), emptyList());

        assertEquals(2, recorder.getStepCount());
        TimelineMetricsRecorder.StepSnapshot snap2 = recorder.getSnapshots().get(1);
        assertEquals(3, snap2.getNodeCount());
        assertEquals(1, snap2.getNewNodes());
        assertEquals(0, snap2.getLostNodes());
    }

    @Test
    public void testNodeLoss() {
        Graph<String, edge> g1 = new UndirectedSparseGraph<>();
        g1.addVertex("A"); g1.addVertex("B"); g1.addVertex("C");
        recorder.recordStep("t1", g1, emptyList(), emptyList(), emptyList(), emptyList(), emptyList());

        Graph<String, edge> g2 = new UndirectedSparseGraph<>();
        g2.addVertex("A"); g2.addVertex("B");
        recorder.recordStep("t2", g2, emptyList(), emptyList(), emptyList(), emptyList(), emptyList());

        assertEquals(1, recorder.getSnapshots().get(1).getLostNodes());
    }

    @Test
    public void testDensityComputation() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        edge e1 = makeEdge("A", "B"); edge e2 = makeEdge("B", "C"); edge e3 = makeEdge("A", "C");
        g.addEdge(e1, "A", "B"); g.addEdge(e2, "B", "C"); g.addEdge(e3, "A", "C");

        recorder.recordStep("complete", g, Arrays.asList(e1, e2, e3),
            emptyList(), emptyList(), emptyList(), emptyList());

        TimelineMetricsRecorder.StepSnapshot snap = recorder.getSnapshots().get(0);
        assertEquals(1.0, snap.getDensity(), 0.001);
        assertEquals(2.0, snap.getAvgDegree(), 0.001);
        assertEquals(2, snap.getMaxDegree());
        assertEquals(0, snap.getIsolatedNodes());
    }

    @Test
    public void testIsolatedNodes() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        recorder.recordStep("isolated", g, emptyList(), emptyList(), emptyList(), emptyList(), emptyList());
        assertEquals(3, recorder.getSnapshots().get(0).getIsolatedNodes());
    }

    @Test
    public void testCategoryCounts() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C"); g.addVertex("D");
        edge f = makeEdge("A", "B", "f", 1.0f);
        edge c = makeEdge("B", "C", "c", 1.0f);
        edge s = makeEdge("C", "D", "s", 1.0f);
        g.addEdge(f, "A", "B"); g.addEdge(c, "B", "C"); g.addEdge(s, "C", "D");

        recorder.recordStep("cats", g, Arrays.asList(f), emptyList(), Arrays.asList(c), Arrays.asList(s), emptyList());

        TimelineMetricsRecorder.StepSnapshot snap = recorder.getSnapshots().get(0);
        assertEquals(1, snap.getFriendCount());
        assertEquals(1, snap.getClassmateCount());
        assertEquals(1, snap.getStrangerCount());
        assertEquals(0, snap.getFsCount());
        assertEquals(0, snap.getStudyGroupCount());
    }

    @Test
    public void testComponentCount() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C"); g.addVertex("D");
        edge e1 = makeEdge("A", "B");
        g.addEdge(e1, "A", "B");

        recorder.recordStep("multi", g, Arrays.asList(e1),
            emptyList(), emptyList(), emptyList(), emptyList());
        assertEquals(3, recorder.getSnapshots().get(0).getComponentCount());
    }

    @Test
    public void testCsvExport() throws IOException {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B");
        edge e = makeEdge("A", "B");
        g.addEdge(e, "A", "B");
        recorder.recordStep("s1", g, Arrays.asList(e), emptyList(), emptyList(), emptyList(), emptyList());

        String csv = recorder.exportCsvString();
        assertTrue(csv.startsWith("step,label,"));
        String[] lines = csv.trim().split("\n");
        assertEquals(2, lines.length);
        assertTrue(lines[1].startsWith("0,s1,"));
    }

    @Test
    public void testCsvFileExport() throws IOException {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("X");
        recorder.recordStep("x", g, emptyList(), emptyList(), emptyList(), emptyList(), emptyList());

        File tmp = File.createTempFile("metrics_test", ".csv");
        tmp.deleteOnExit();
        recorder.exportCsv(tmp);

        BufferedReader br = new BufferedReader(new FileReader(tmp));
        assertNotNull(br.readLine()); // header
        assertNotNull(br.readLine()); // data
        assertNull(br.readLine());    // no more
        br.close();
    }

    @Test
    public void testCsvEscaping() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        recorder.recordStep("label,with,commas", g, emptyList(), emptyList(), emptyList(), emptyList(), emptyList());
        assertTrue(recorder.exportCsvString().contains("\"label,with,commas\""));
    }

    @Test
    public void testGrowthRates() {
        Graph<String, edge> g1 = new UndirectedSparseGraph<>();
        g1.addVertex("A"); g1.addVertex("B");
        edge e1 = makeEdge("A", "B");
        g1.addEdge(e1, "A", "B");
        recorder.recordStep("t1", g1, Arrays.asList(e1), emptyList(), emptyList(), emptyList(), emptyList());

        Graph<String, edge> g2 = new UndirectedSparseGraph<>();
        g2.addVertex("A"); g2.addVertex("B"); g2.addVertex("C"); g2.addVertex("D");
        edge e2a = makeEdge("A", "B"); edge e2b = makeEdge("C", "D");
        g2.addEdge(e2a, "A", "B"); g2.addEdge(e2b, "C", "D");
        recorder.recordStep("t2", g2, Arrays.asList(e2a, e2b), emptyList(), emptyList(), emptyList(), emptyList());

        TimelineMetricsRecorder.StepSnapshot s1 = recorder.getSnapshots().get(0);
        TimelineMetricsRecorder.StepSnapshot s2 = recorder.getSnapshots().get(1);
        assertEquals(1.0, s2.getNodeGrowthRate(s1), 0.001);
        assertEquals(1.0, s2.getEdgeGrowthRate(s1), 0.001);
    }

    @Test
    public void testGrowthRateFirstStep() {
        TimelineMetricsRecorder.StepSnapshot snap = new TimelineMetricsRecorder.StepSnapshot(
            "first", 0, 5, 3, 1, 1, 1, 0, 0, 0.3, 1.2, 2, 1.0, 0, 1, 5, 0, 3, 0);
        assertEquals(0.0, snap.getNodeGrowthRate(null), 0.001);
    }

    @Test
    public void testTrendAnalysisEmpty() {
        TimelineMetricsRecorder.TrendReport report = recorder.analyzeTrends();
        assertEquals(0, report.getTotalSteps());
        assertTrue(report.getPhaseTransitions().isEmpty());
    }

    @Test
    public void testTrendAnalysisPeaks() {
        Graph<String, edge> g1 = new UndirectedSparseGraph<>();
        g1.addVertex("A"); g1.addVertex("B");
        edge e1 = makeEdge("A", "B");
        g1.addEdge(e1, "A", "B");
        recorder.recordStep("t1", g1, Arrays.asList(e1), emptyList(), emptyList(), emptyList(), emptyList());

        Graph<String, edge> g2 = new UndirectedSparseGraph<>();
        g2.addVertex("A"); g2.addVertex("B"); g2.addVertex("C"); g2.addVertex("D");
        edge e2a = makeEdge("A","B"); edge e2b = makeEdge("B","C");
        edge e2c = makeEdge("C","D"); edge e2d = makeEdge("A","C");
        g2.addEdge(e2a,"A","B"); g2.addEdge(e2b,"B","C");
        g2.addEdge(e2c,"C","D"); g2.addEdge(e2d,"A","C");
        recorder.recordStep("t2", g2, Arrays.asList(e2a,e2b,e2c,e2d),
            emptyList(), emptyList(), emptyList(), emptyList());

        Graph<String, edge> g3 = new UndirectedSparseGraph<>();
        g3.addVertex("X");
        recorder.recordStep("t3", g3, emptyList(), emptyList(), emptyList(), emptyList(), emptyList());

        TimelineMetricsRecorder.TrendReport report = recorder.analyzeTrends();
        assertEquals(3, report.getTotalSteps());
        assertEquals(4, report.getPeakNodeCount());
        assertEquals(1, report.getPeakNodeStep());
    }

    @Test
    public void testPhaseTransitions() {
        recorder.setPhaseThreshold(0.3);

        Graph<String, edge> g1 = new UndirectedSparseGraph<>();
        g1.addVertex("A"); g1.addVertex("B");
        edge e1 = makeEdge("A","B");
        g1.addEdge(e1,"A","B");
        recorder.recordStep("small", g1, Arrays.asList(e1), emptyList(), emptyList(), emptyList(), emptyList());

        Graph<String, edge> g2 = new UndirectedSparseGraph<>();
        for (String v : new String[]{"A","B","C","D","E","F"}) g2.addVertex(v);
        edge e2a = makeEdge("A","B"); edge e2b = makeEdge("C","D");
        edge e2c = makeEdge("E","F"); edge e2d = makeEdge("A","C"); edge e2e = makeEdge("B","D");
        g2.addEdge(e2a,"A","B"); g2.addEdge(e2b,"C","D"); g2.addEdge(e2c,"E","F");
        g2.addEdge(e2d,"A","C"); g2.addEdge(e2e,"B","D");
        recorder.recordStep("spike", g2, Arrays.asList(e2a,e2b,e2c,e2d,e2e),
            emptyList(), emptyList(), emptyList(), emptyList());

        TimelineMetricsRecorder.TrendReport report = recorder.analyzeTrends();
        assertFalse(report.getPhaseTransitions().isEmpty());
        assertEquals("growth_spike", report.getPhaseTransitions().get(0).type);
    }

    @Test
    public void testContractionTransition() {
        recorder.setPhaseThreshold(0.3);

        Graph<String, edge> g1 = new UndirectedSparseGraph<>();
        for (String v : new String[]{"A","B","C","D","E"}) g1.addVertex(v);
        edge e1 = makeEdge("A","B"); edge e2 = makeEdge("C","D"); edge e3 = makeEdge("D","E");
        g1.addEdge(e1,"A","B"); g1.addEdge(e2,"C","D"); g1.addEdge(e3,"D","E");
        recorder.recordStep("big", g1, Arrays.asList(e1,e2,e3), emptyList(), emptyList(), emptyList(), emptyList());

        Graph<String, edge> g2 = new UndirectedSparseGraph<>();
        g2.addVertex("A"); g2.addVertex("B");
        edge e4 = makeEdge("A","B");
        g2.addEdge(e4,"A","B");
        recorder.recordStep("small", g2, Arrays.asList(e4), emptyList(), emptyList(), emptyList(), emptyList());

        boolean hasContraction = false;
        for (TimelineMetricsRecorder.PhaseTransition pt : recorder.analyzeTrends().getPhaseTransitions()) {
            if ("contraction".equals(pt.type)) hasContraction = true;
        }
        assertTrue(hasContraction);
    }

    @Test
    public void testCategoryTrends() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B");
        edge f = makeEdge("A","B","f",1.0f);
        g.addEdge(f,"A","B");
        recorder.recordStep("s1", g, Arrays.asList(f), emptyList(), emptyList(), emptyList(), emptyList());

        double[] friendTrend = recorder.analyzeTrends().getCategoryTrends().get("friends");
        assertNotNull(friendTrend);
        assertEquals(1.0, friendTrend[0], 0.001);
    }

    @Test
    public void testClear() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        recorder.recordStep("s1", g, emptyList(), emptyList(), emptyList(), emptyList(), emptyList());
        recorder.clear();
        assertEquals(0, recorder.getStepCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraph() {
        recorder.recordStep("x", null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullFile() throws IOException {
        recorder.exportCsv(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidThreshold() {
        recorder.setPhaseThreshold(0.0);
    }

    @Test
    public void testPhaseThresholdGetSet() {
        recorder.setPhaseThreshold(0.5);
        assertEquals(0.5, recorder.getPhaseThreshold(), 0.001);
    }

    @Test
    public void testTrendReportToString() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B");
        edge e = makeEdge("A","B");
        g.addEdge(e,"A","B");
        recorder.recordStep("t1", g, Arrays.asList(e), emptyList(), emptyList(), emptyList(), emptyList());

        String str = recorder.analyzeTrends().toString();
        assertTrue(str.contains("Timeline Trend Report"));
        assertTrue(str.contains("Peak nodes"));
    }

    @Test
    public void testSnapshotsUnmodifiable() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        recorder.recordStep("s1", g, emptyList(), emptyList(), emptyList(), emptyList(), emptyList());
        try {
            recorder.getSnapshots().add(null);
            fail("Should throw");
        } catch (UnsupportedOperationException e) { /* expected */ }
    }

    @Test
    public void testAvgWeight() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        edge e1 = makeEdge("A","B","f",3.0f);
        edge e2 = makeEdge("B","C","f",5.0f);
        g.addEdge(e1,"A","B"); g.addEdge(e2,"B","C");
        recorder.recordStep("wt", g, Arrays.asList(e1,e2), emptyList(), emptyList(), emptyList(), emptyList());
        assertEquals(4.0, recorder.getSnapshots().get(0).getAvgWeight(), 0.001);
    }

    @Test
    public void testEmptyGraphStep() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        recorder.recordStep("empty", g, emptyList(), emptyList(), emptyList(), emptyList(), emptyList());
        TimelineMetricsRecorder.StepSnapshot snap = recorder.getSnapshots().get(0);
        assertEquals(0, snap.getNodeCount());
        assertEquals(0.0, snap.getDensity(), 0.001);
        assertEquals(0, snap.getComponentCount());
    }

    @Test
    public void testStdDevComputation() {
        Graph<String, edge> g1 = new UndirectedSparseGraph<>();
        g1.addVertex("A"); g1.addVertex("B");
        recorder.recordStep("t1", g1, emptyList(), emptyList(), emptyList(), emptyList(), emptyList());

        Graph<String, edge> g2 = new UndirectedSparseGraph<>();
        g2.addVertex("A"); g2.addVertex("B"); g2.addVertex("C"); g2.addVertex("D");
        recorder.recordStep("t2", g2, emptyList(), emptyList(), emptyList(), emptyList(), emptyList());

        TimelineMetricsRecorder.TrendReport report = recorder.analyzeTrends();
        assertEquals(3.0, report.getAvgNodeCount(), 0.001);
        assertEquals(1.0, report.getNodeCountStdDev(), 0.001);
    }
}
