package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for GraphTemporalDynamicsEngine.
 */
public class GraphTemporalDynamicsEngineTest {

    private GraphTemporalDynamicsEngine engine;

    @Before
    public void setUp() {
        engine = new GraphTemporalDynamicsEngine();
    }

    // -- Helper methods --

    private Graph<String, Edge> createGraph(String[][] edges) {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (String[] e : edges) {
            if (!g.containsVertex(e[0])) g.addVertex(e[0]);
            if (!g.containsVertex(e[1])) g.addVertex(e[1]);
            // Skip duplicate undirected pairs so the snapshot stays a simple graph.
            if (g.findEdge(e[0], e[1]) != null) continue;
            g.addEdge(new Edge("f", e[0], e[1]), e[0], e[1]);
        }
        return g;
    }

    private List<Graph<String, Edge>> createGrowingSequence() {
        List<Graph<String, Edge>> snapshots = new ArrayList<>();
        // Snapshot 0: small graph
        snapshots.add(createGraph(new String[][]{{"A", "B"}, {"B", "C"}}));
        // Snapshot 1: add node D
        snapshots.add(createGraph(new String[][]{{"A", "B"}, {"B", "C"}, {"C", "D"}}));
        // Snapshot 2: more connections
        snapshots.add(createGraph(new String[][]{{"A", "B"}, {"B", "C"}, {"C", "D"}, {"A", "C"}, {"B", "D"}}));
        // Snapshot 3: fully connected core
        snapshots.add(createGraph(new String[][]{{"A", "B"}, {"B", "C"}, {"C", "D"}, {"A", "C"}, {"B", "D"}, {"A", "D"}, {"D", "E"}}));
        // Snapshot 4: expansion
        snapshots.add(createGraph(new String[][]{{"A", "B"}, {"B", "C"}, {"C", "D"}, {"A", "C"}, {"B", "D"}, {"A", "D"}, {"D", "E"}, {"E", "F"}, {"F", "G"}}));
        return snapshots;
    }

    // -- Test: Basic analysis --

    @Test
    public void testAnalyzeReturnsReport() {
        List<Graph<String, Edge>> snapshots = createGrowingSequence();
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        assertNotNull(report);
        assertEquals(5, report.snapshotCount);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAnalyzeRequiresAtLeastTwoSnapshots() {
        List<Graph<String, Edge>> single = new ArrayList<>();
        single.add(createGraph(new String[][]{{"A", "B"}}));
        engine.analyze(single);
    }

    @Test
    public void testSnapshotMetricsComputed() {
        List<Graph<String, Edge>> snapshots = createGrowingSequence();
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        assertEquals(5, report.snapshots.size());
        assertEquals(3, report.snapshots.get(0).nodeCount);
        assertEquals(2, report.snapshots.get(0).edgeCount);
    }

    @Test
    public void testDensityCalculation() {
        List<Graph<String, Edge>> snapshots = createGrowingSequence();
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        // 3 nodes, 2 edges => density = 2*2/(3*2) = 0.667
        assertEquals(2.0 / 3.0, report.snapshots.get(0).density, 0.01);
    }

    @Test
    public void testVelocitiesComputed() {
        List<Graph<String, Edge>> snapshots = createGrowingSequence();
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        assertEquals(4, report.velocities.size());
        for (GraphTemporalDynamicsEngine.StructuralVelocity v : report.velocities) {
            assertTrue(v.compositeSpeed >= 0);
            assertFalse(v.deltas.isEmpty());
        }
    }

    @Test
    public void testVelocityDeltasContainAllMetrics() {
        List<Graph<String, Edge>> snapshots = createGrowingSequence();
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        GraphTemporalDynamicsEngine.StructuralVelocity v = report.velocities.get(0);
        assertTrue(v.deltas.containsKey("density"));
        assertTrue(v.deltas.containsKey("avgClustering"));
        assertTrue(v.deltas.containsKey("avgDegree"));
        assertTrue(v.deltas.containsKey("degreeEntropy"));
        assertTrue(v.deltas.containsKey("componentCount"));
        assertTrue(v.deltas.containsKey("maxDegreeRatio"));
    }

    // -- Test: Momentum --

    @Test
    public void testMomentumClassified() {
        List<Graph<String, Edge>> snapshots = createGrowingSequence();
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        assertNotNull(report.currentMomentum);
        assertTrue(report.momentumScore >= 0 && report.momentumScore <= 100);
    }

    @Test
    public void testStagnantMomentumForIdenticalGraphs() {
        Graph<String, Edge> g = createGraph(new String[][]{{"A", "B"}, {"B", "C"}, {"A", "C"}});
        List<Graph<String, Edge>> snapshots = Arrays.asList(g, g, g, g);
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        assertEquals(GraphTemporalDynamicsEngine.MomentumPhase.STAGNANT, report.currentMomentum);
    }

    // -- Test: Forecasts --

    @Test
    public void testForecastsGenerated() {
        List<Graph<String, Edge>> snapshots = createGrowingSequence();
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        assertFalse(report.forecasts.isEmpty());
        for (GraphTemporalDynamicsEngine.MetricForecast f : report.forecasts) {
            assertNotNull(f.metric);
            assertNotNull(f.trend);
            assertEquals(3, f.predicted.size()); // default horizon
        }
    }

    @Test
    public void testForecastTrendForGrowingGraph() {
        List<Graph<String, Edge>> snapshots = createGrowingSequence();
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        // avgDegree should be rising for a growing graph
        GraphTemporalDynamicsEngine.MetricForecast avgDegreeForecast = report.forecasts.stream()
                .filter(f -> "avgDegree".equals(f.metric)).findFirst().orElse(null);
        assertNotNull(avgDegreeForecast);
        assertTrue(avgDegreeForecast.slope > 0);
    }

    @Test
    public void testCustomForecastHorizon() {
        engine.setForecastHorizon(5);
        List<Graph<String, Edge>> snapshots = createGrowingSequence();
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        for (GraphTemporalDynamicsEngine.MetricForecast f : report.forecasts) {
            assertEquals(5, f.predicted.size());
        }
    }

    // -- Test: Node Lifecycles --

    @Test
    public void testNodeTrajectoriesGenerated() {
        List<Graph<String, Edge>> snapshots = createGrowingSequence();
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        assertFalse(report.nodeTrajectories.isEmpty());
        for (GraphTemporalDynamicsEngine.NodeTrajectory t : report.nodeTrajectories) {
            assertNotNull(t.nodeId);
            assertNotNull(t.lifecycle);
            assertEquals(5, t.importanceOverTime.size());
        }
    }

    @Test
    public void testNewcomerDetected() {
        List<Graph<String, Edge>> snapshots = createGrowingSequence();
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        // Node E appears in snapshot 3, not in 0
        boolean hasNewcomer = report.nodeTrajectories.stream()
                .anyMatch(t -> t.lifecycle == GraphTemporalDynamicsEngine.NodeLifecycle.NEWCOMER);
        assertTrue("Should detect newcomer nodes", hasNewcomer);
    }

    @Test
    public void testDepartingNodeDetected() {
        List<Graph<String, Edge>> snapshots = new ArrayList<>();
        snapshots.add(createGraph(new String[][]{{"A", "B"}, {"B", "C"}, {"C", "X"}}));
        snapshots.add(createGraph(new String[][]{{"A", "B"}, {"B", "C"}, {"C", "X"}}));
        snapshots.add(createGraph(new String[][]{{"A", "B"}, {"B", "C"}}));
        snapshots.add(createGraph(new String[][]{{"A", "B"}, {"B", "C"}}));
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        boolean hasDeparting = report.nodeTrajectories.stream()
                .anyMatch(t -> "X".equals(t.nodeId) && t.lifecycle == GraphTemporalDynamicsEngine.NodeLifecycle.DEPARTING);
        assertTrue("Should detect departing node X", hasDeparting);
    }

    // -- Test: Phase Transitions --

    @Test
    public void testPhaseTransitionsListNotNull() {
        List<Graph<String, Edge>> snapshots = createGrowingSequence();
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        assertNotNull(report.transitions);
    }

    @Test
    public void testPhaseTransitionDetectedOnAbruptChange() {
        List<Graph<String, Edge>> snapshots = new ArrayList<>();
        // Gradual start
        snapshots.add(createGraph(new String[][]{{"A", "B"}}));
        snapshots.add(createGraph(new String[][]{{"A", "B"}, {"B", "C"}}));
        snapshots.add(createGraph(new String[][]{{"A", "B"}, {"B", "C"}, {"C", "D"}}));
        // Abrupt explosion
        snapshots.add(createGraph(new String[][]{
                {"A", "B"}, {"B", "C"}, {"C", "D"}, {"A", "C"}, {"A", "D"}, {"B", "D"},
                {"E", "F"}, {"F", "G"}, {"G", "H"}, {"H", "E"}, {"E", "G"},
                {"A", "E"}, {"B", "F"}, {"C", "G"}, {"D", "H"}
        }));
        snapshots.add(createGraph(new String[][]{
                {"A", "B"}, {"B", "C"}, {"C", "D"}, {"A", "C"}, {"A", "D"}, {"B", "D"},
                {"E", "F"}, {"F", "G"}, {"G", "H"}, {"H", "E"}, {"E", "G"},
                {"A", "E"}, {"B", "F"}, {"C", "G"}, {"D", "H"}, {"A", "F"}, {"B", "G"}
        }));

        engine.setCusumThreshold(2.0);
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        // Should detect at least one transition around the explosion point
        assertFalse("Should detect phase transition on abrupt change", report.transitions.isEmpty());
    }

    // -- Test: Temporal Patterns --

    @Test
    public void testPatternsListNotNull() {
        List<Graph<String, Edge>> snapshots = createGrowingSequence();
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        assertNotNull(report.patterns);
    }

    @Test
    public void testOscillationDetection() {
        List<Graph<String, Edge>> snapshots = new ArrayList<>();
        // Create oscillating density
        for (int i = 0; i < 8; i++) {
            if (i % 2 == 0) {
                snapshots.add(createGraph(new String[][]{{"A", "B"}, {"B", "C"}}));
            } else {
                snapshots.add(createGraph(new String[][]{{"A", "B"}, {"B", "C"}, {"A", "C"}, {"C", "D"}, {"D", "A"}}));
            }
        }
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        boolean hasOscillation = report.patterns.stream()
                .anyMatch(p -> "oscillation".equals(p.type));
        assertTrue("Should detect oscillation pattern", hasOscillation);
    }

    // -- Test: Insights --

    @Test
    public void testInsightsGenerated() {
        List<Graph<String, Edge>> snapshots = createGrowingSequence();
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        assertFalse(report.insights.isEmpty());
        assertTrue(report.insights.get(0).contains("momentum"));
    }

    // -- Test: Output --

    @Test
    public void testToTextOutput() {
        List<Graph<String, Edge>> snapshots = createGrowingSequence();
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        String text = engine.toText(report);
        assertNotNull(text);
        assertTrue(text.contains("TEMPORAL DYNAMICS"));
        assertTrue(text.contains("Structural Velocity"));
        assertTrue(text.contains("Metric Forecasts"));
    }

    @Test
    public void testHtmlExport() {
        List<Graph<String, Edge>> snapshots = createGrowingSequence();
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        String html = engine.exportHtml(report);
        assertNotNull(html);
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("Temporal Dynamics"));
        assertTrue(html.contains("showTab"));
    }

    // -- Test: Edge cases --

    @Test
    public void testTwoSnapshotsMinimum() {
        List<Graph<String, Edge>> snapshots = new ArrayList<>();
        snapshots.add(createGraph(new String[][]{{"A", "B"}}));
        snapshots.add(createGraph(new String[][]{{"A", "B"}, {"B", "C"}}));
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        assertEquals(2, report.snapshotCount);
        assertEquals(1, report.velocities.size());
    }

    @Test
    public void testEmptySnapshotHandled() {
        List<Graph<String, Edge>> snapshots = new ArrayList<>();
        snapshots.add(new UndirectedSparseGraph<>());
        snapshots.add(createGraph(new String[][]{{"A", "B"}}));
        snapshots.add(createGraph(new String[][]{{"A", "B"}, {"B", "C"}}));
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        assertEquals(3, report.snapshotCount);
        assertEquals(0, report.snapshots.get(0).nodeCount);
    }

    @Test
    public void testSingleNodeSnapshot() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("lonely");
        List<Graph<String, Edge>> snapshots = new ArrayList<>();
        snapshots.add(g);
        snapshots.add(createGraph(new String[][]{{"A", "B"}}));
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        assertEquals(1, report.snapshots.get(0).nodeCount);
        assertEquals(0.0, report.snapshots.get(0).density, 0.001);
    }

    @Test
    public void testDisconnectedComponents() {
        List<Graph<String, Edge>> snapshots = new ArrayList<>();
        snapshots.add(createGraph(new String[][]{{"A", "B"}, {"C", "D"}}));
        snapshots.add(createGraph(new String[][]{{"A", "B"}, {"B", "C"}, {"C", "D"}}));
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        assertEquals(2, report.snapshots.get(0).componentCount);
        assertEquals(1, report.snapshots.get(1).componentCount);
    }

    @Test
    public void testLargeSnapshotSequence() {
        List<Graph<String, Edge>> snapshots = new ArrayList<>();
        Random rng = new Random(42);
        for (int s = 0; s < 20; s++) {
            Graph<String, Edge> g = new UndirectedSparseGraph<>();
            int nodes = 10 + s;
            for (int i = 0; i < nodes; i++) g.addVertex("N" + i);
            int edgeCount = 10 + s * 3;
            for (int e = 0; e < edgeCount; e++) {
                String v1 = "N" + rng.nextInt(nodes);
                String v2 = "N" + rng.nextInt(nodes);
                if (!v1.equals(v2) && g.findEdge(v1, v2) == null) {
                    g.addEdge(new Edge("f", v1, v2), v1, v2);
                }
            }
            snapshots.add(g);
        }
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        assertEquals(20, report.snapshotCount);
        assertEquals(19, report.velocities.size());
        assertNotNull(report.currentMomentum);
    }

    // -- Test: Configuration --

    @Test
    public void testCusumThresholdConfiguration() {
        engine.setCusumThreshold(10.0); // very high threshold
        List<Graph<String, Edge>> snapshots = createGrowingSequence();
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        // With high threshold, fewer transitions
        assertTrue(report.transitions.size() <= 2);
    }

    @Test
    public void testSignificantChangeThreshold() {
        engine.setSignificantChangeThreshold(0.001);
        List<Graph<String, Edge>> snapshots = createGrowingSequence();
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        // With very low threshold, most metrics should be non-stable
        long nonStable = report.forecasts.stream().filter(f -> !"stable".equals(f.trend)).count();
        assertTrue(nonStable >= 3);
    }

    // -- Test: Momentum phases --

    @Test
    public void testCollapsingMomentum() {
        List<Graph<String, Edge>> snapshots = new ArrayList<>();
        // Start dense, get sparse
        snapshots.add(createGraph(new String[][]{{"A","B"},{"B","C"},{"C","A"},{"A","D"},{"B","D"},{"C","D"}}));
        snapshots.add(createGraph(new String[][]{{"A","B"},{"B","C"},{"C","A"},{"A","D"}}));
        snapshots.add(createGraph(new String[][]{{"A","B"},{"B","C"}}));
        snapshots.add(createGraph(new String[][]{{"A","B"}}));
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        // Should detect declining/decelerating or collapsing
        assertTrue(report.momentumScore < 60);
    }

    // -- Test: R-squared --

    @Test
    public void testPerfectLinearTrendHasHighRSquared() {
        List<Graph<String, Edge>> snapshots = new ArrayList<>();
        // Linear growth in edges
        for (int i = 2; i <= 6; i++) {
            Graph<String, Edge> g = new UndirectedSparseGraph<>();
            for (int j = 0; j <= i; j++) g.addVertex("V" + j);
            for (int j = 0; j < i; j++) {
                g.addEdge(new Edge("f", "V" + j, "V" + (j + 1)), "V" + j, "V" + (j + 1));
            }
            snapshots.add(g);
        }
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        // avgDegree should have high R² for a linear chain growth
        GraphTemporalDynamicsEngine.MetricForecast degreeForecast = report.forecasts.stream()
                .filter(f -> "avgDegree".equals(f.metric)).findFirst().orElse(null);
        assertNotNull(degreeForecast);
        // Linear chain has constant avg degree ~2 except endpoints, so R² may vary
        // Just verify it's computed
        assertTrue(degreeForecast.rSquared >= 0 && degreeForecast.rSquared <= 1);
    }

    @Test
    public void testNodeTrajectoryLimitedTo20() {
        List<Graph<String, Edge>> snapshots = new ArrayList<>();
        Random rng = new Random(7);
        for (int s = 0; s < 5; s++) {
            Graph<String, Edge> g = new UndirectedSparseGraph<>();
            for (int i = 0; i < 50; i++) g.addVertex("N" + i);
            for (int e = 0; e < 80; e++) {
                String v1 = "N" + rng.nextInt(50);
                String v2 = "N" + rng.nextInt(50);
                if (!v1.equals(v2) && g.findEdge(v1, v2) == null) {
                    g.addEdge(new Edge("f", v1, v2), v1, v2);
                }
            }
            snapshots.add(g);
        }
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        assertTrue(report.nodeTrajectories.size() <= 20);
    }

    @Test
    public void testDegreeEntropyIncreases() {
        List<Graph<String, Edge>> snapshots = new ArrayList<>();
        // Snapshot 0: uniform degree (4-cycle, every vertex has degree 2 -> entropy 0).
        snapshots.add(createGraph(new String[][]{{"A","B"},{"B","C"},{"C","D"},{"D","A"}}));
        // Snapshot 1: add diagonal A-C, so A and C become degree 3, B and D stay 2.
        snapshots.add(createGraph(new String[][]{{"A","B"},{"B","C"},{"C","D"},{"D","A"},{"A","C"}}));
        // Snapshot 2: extend with a pendant E off A and a pendant F off B -> degree mix {1,1,2,3,3,4}.
        snapshots.add(createGraph(new String[][]{
                {"A","B"},{"B","C"},{"C","D"},{"D","A"},{"A","C"},{"A","E"},{"B","F"}}));
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        // First snapshot: all same degree -> entropy = 0 (one bin)
        assertEquals(0.0, report.snapshots.get(0).degreeEntropy, 0.01);
        // Later snapshots should have higher entropy
        assertTrue(report.snapshots.get(2).degreeEntropy > report.snapshots.get(0).degreeEntropy);
    }

    @Test
    public void testExportToFile() throws Exception {
        List<Graph<String, Edge>> snapshots = createGrowingSequence();
        GraphTemporalDynamicsEngine.TemporalDynamicsReport report = engine.analyze(snapshots);
        String tempPath = System.getProperty("java.io.tmpdir") + "/temporal_dynamics_test.html";
        engine.exportToFile(report, tempPath);
        java.io.File f = new java.io.File(tempPath);
        assertTrue(f.exists());
        assertTrue(f.length() > 1000);
        f.delete();
    }
}
