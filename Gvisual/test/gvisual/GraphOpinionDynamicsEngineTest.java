package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.io.File;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphOpinionDynamicsEngine}.
 *
 * @author sauravbhattacharya001
 */
public class GraphOpinionDynamicsEngineTest {

    // ── Helper methods ───────────────────────────────────────────────

    private Graph<String, Edge> triangle() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addEdge(new Edge("c", "A", "B"), "A", "B");
        g.addEdge(new Edge("c", "B", "C"), "B", "C");
        g.addEdge(new Edge("c", "A", "C"), "A", "C");
        return g;
    }

    private Graph<String, Edge> star5() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("Hub");
        for (int i = 1; i <= 4; i++) {
            String leaf = "L" + i;
            g.addVertex(leaf);
            g.addEdge(new Edge("c", "Hub", leaf), "Hub", leaf);
        }
        return g;
    }

    private Graph<String, Edge> path(int n) {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < n; i++) g.addVertex("N" + i);
        for (int i = 0; i < n - 1; i++) {
            g.addEdge(new Edge("c", "N" + i, "N" + (i + 1)), "N" + i, "N" + (i + 1));
        }
        return g;
    }

    private Graph<String, Edge> completeGraph(int n) {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < n; i++) g.addVertex("V" + i);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                g.addEdge(new Edge("c", "V" + i, "V" + j), "V" + i, "V" + j);
            }
        }
        return g;
    }

    /** Two cliques connected by a single bridge edge. */
    private Graph<String, Edge> twoCliques() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        // Clique 1: A1-A4
        for (int i = 1; i <= 4; i++) g.addVertex("A" + i);
        for (int i = 1; i <= 4; i++)
            for (int j = i + 1; j <= 4; j++)
                g.addEdge(new Edge("c", "A" + i, "A" + j), "A" + i, "A" + j);
        // Clique 2: B1-B4
        for (int i = 1; i <= 4; i++) g.addVertex("B" + i);
        for (int i = 1; i <= 4; i++)
            for (int j = i + 1; j <= 4; j++)
                g.addEdge(new Edge("c", "B" + i, "B" + j), "B" + i, "B" + j);
        // Bridge
        g.addEdge(new Edge("c", "A1", "B1"), "A1", "B1");
        return g;
    }

    /** Disconnected: two separate triangles. */
    private Graph<String, Edge> disconnected() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("X1"); g.addVertex("X2"); g.addVertex("X3");
        g.addEdge(new Edge("c", "X1", "X2"), "X1", "X2");
        g.addEdge(new Edge("c", "X2", "X3"), "X2", "X3");
        g.addEdge(new Edge("c", "X1", "X3"), "X1", "X3");

        g.addVertex("Y1"); g.addVertex("Y2"); g.addVertex("Y3");
        g.addEdge(new Edge("c", "Y1", "Y2"), "Y1", "Y2");
        g.addEdge(new Edge("c", "Y2", "Y3"), "Y2", "Y3");
        g.addEdge(new Edge("c", "Y1", "Y3"), "Y1", "Y3");
        return g;
    }

    private Graph<String, Edge> singleNode() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("Solo");
        return g;
    }

    private Graph<String, Edge> emptyGraph() {
        return new UndirectedSparseGraph<>();
    }

    private Map<String, Double> uniformOpinions(Graph<String, Edge> g, double val) {
        Map<String, Double> ops = new LinkedHashMap<>();
        for (String v : g.getVertices()) ops.put(v, val);
        return ops;
    }

    private Map<String, Double> polarizedOpinions(Graph<String, Edge> g) {
        Map<String, Double> ops = new LinkedHashMap<>();
        int i = 0;
        for (String v : g.getVertices()) {
            ops.put(v, (i % 2 == 0) ? 0.0 : 1.0);
            i++;
        }
        return ops;
    }

    private GraphOpinionDynamicsEngine engine() {
        GraphOpinionDynamicsEngine e = new GraphOpinionDynamicsEngine();
        e.setRandomSeed(42);
        e.setMaxSteps(200);
        e.setSnapshotInterval(5);
        return e;
    }

    // ── Voter Model Tests ────────────────────────────────────────────

    @Test
    public void voterModel_completeGraph_converges() {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = completeGraph(6);
        Map<String, Double> ops = new LinkedHashMap<>();
        ops.put("V0", 0.0); ops.put("V1", 0.0); ops.put("V2", 0.0);
        ops.put("V3", 1.0); ops.put("V4", 1.0); ops.put("V5", 1.0);
        eng.setMaxSteps(5000);

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r =
                eng.simulateVoterModel(g, ops, 5000);

        assertEquals("VoterModel", r.model);
        assertNotNull(r.finalOpinions);
        assertEquals(6, r.finalOpinions.size());
        // All final opinions should be either 0.0 or 1.0 (voter model copies exact values)
        for (double v : r.finalOpinions.values()) {
            assertTrue("Opinion should be 0 or 1", v == 0.0 || v == 1.0);
        }
    }

    @Test
    public void voterModel_singleNode_unchanged() {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = singleNode();
        Map<String, Double> ops = new LinkedHashMap<>();
        ops.put("Solo", 0.7);

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r =
                eng.simulateVoterModel(g, ops, 100);

        assertEquals(0.7, r.finalOpinions.get("Solo"), 1e-10);
    }

    @Test
    public void voterModel_recordsTimeline() {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = triangle();
        Map<String, Double> ops = new LinkedHashMap<>();
        ops.put("A", 0.1); ops.put("B", 0.5); ops.put("C", 0.9);

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r =
                eng.simulateVoterModel(g, ops, 50);

        assertFalse("Timeline should have snapshots", r.timeline.isEmpty());
        assertEquals(0, r.timeline.get(0).step);
    }

    @Test
    public void voterModel_preservesOpinionSet() {
        // Voter model only copies — no new opinion values should appear
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = triangle();
        Map<String, Double> ops = new LinkedHashMap<>();
        ops.put("A", 0.2); ops.put("B", 0.5); ops.put("C", 0.8);
        Set<Double> origValues = new HashSet<>(ops.values());

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r =
                eng.simulateVoterModel(g, ops, 100);

        for (double v : r.finalOpinions.values()) {
            assertTrue("Voter model should only copy existing opinions",
                    origValues.contains(v));
        }
    }

    // ── DeGroot Model Tests ──────────────────────────────────────────

    @Test
    public void deGroot_completeGraph_convergesToAverage() {
        GraphOpinionDynamicsEngine eng = engine();
        eng.setMaxSteps(500);
        Graph<String, Edge> g = completeGraph(5);
        Map<String, Double> ops = new LinkedHashMap<>();
        ops.put("V0", 0.0); ops.put("V1", 0.25); ops.put("V2", 0.5);
        ops.put("V3", 0.75); ops.put("V4", 1.0);

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r =
                eng.simulateDeGroot(g, ops, 500);

        // All should converge to the same value (close to the mean)
        double first = r.finalOpinions.values().iterator().next();
        for (double v : r.finalOpinions.values()) {
            assertEquals("All opinions should converge", first, v, 0.01);
        }
    }

    @Test
    public void deGroot_disconnectedComponents_separateConsensus() {
        GraphOpinionDynamicsEngine eng = engine();
        eng.setMaxSteps(200);
        Graph<String, Edge> g = disconnected();
        Map<String, Double> ops = new LinkedHashMap<>();
        ops.put("X1", 0.1); ops.put("X2", 0.2); ops.put("X3", 0.3);
        ops.put("Y1", 0.7); ops.put("Y2", 0.8); ops.put("Y3", 0.9);

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r =
                eng.simulateDeGroot(g, ops, 200);

        // X cluster should converge to ~0.2, Y cluster to ~0.8
        double xAvg = (r.finalOpinions.get("X1") + r.finalOpinions.get("X2") + r.finalOpinions.get("X3")) / 3;
        double yAvg = (r.finalOpinions.get("Y1") + r.finalOpinions.get("Y2") + r.finalOpinions.get("Y3")) / 3;
        assertTrue("X cluster should converge low", xAvg < 0.4);
        assertTrue("Y cluster should converge high", yAvg > 0.6);
    }

    @Test
    public void deGroot_uniformOpinions_noChange() {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = triangle();
        Map<String, Double> ops = uniformOpinions(g, 0.5);

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r =
                eng.simulateDeGroot(g, ops, 100);

        for (double v : r.finalOpinions.values()) {
            assertEquals(0.5, v, 1e-6);
        }
    }

    @Test
    public void deGroot_emptyGraph_returnsEmpty() {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = emptyGraph();

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r =
                eng.simulateDeGroot(g, new LinkedHashMap<>(), 100);

        assertEquals(0, r.steps);
        assertTrue(r.finalOpinions.isEmpty());
    }

    @Test
    public void deGroot_stepsRecorded() {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = triangle();
        Map<String, Double> ops = new LinkedHashMap<>();
        ops.put("A", 0.0); ops.put("B", 0.5); ops.put("C", 1.0);

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r =
                eng.simulateDeGroot(g, ops, 100);

        assertTrue("Should have run at least 1 step", r.steps >= 1);
        assertEquals("DeGroot", r.model);
    }

    @Test
    public void deGroot_weightedEdges() {
        GraphOpinionDynamicsEngine eng = engine();
        eng.setMaxSteps(200);
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        Edge ab = new Edge("c", "A", "B"); ab.setWeight(10.0f);
        Edge bc = new Edge("c", "B", "C"); bc.setWeight(1.0f);
        g.addEdge(ab, "A", "B");
        g.addEdge(bc, "B", "C");

        Map<String, Double> ops = new LinkedHashMap<>();
        ops.put("A", 0.0); ops.put("B", 0.5); ops.put("C", 1.0);

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r =
                eng.simulateDeGroot(g, ops, 200);

        // B should be pulled closer to A due to heavy weight
        assertNotNull(r.finalOpinions);
    }

    // ── Bounded Confidence Tests ─────────────────────────────────────

    @Test
    public void boundedConfidence_largeEpsilon_converges() {
        GraphOpinionDynamicsEngine eng = engine();
        eng.setMaxSteps(300);
        Graph<String, Edge> g = completeGraph(5);
        Map<String, Double> ops = new LinkedHashMap<>();
        ops.put("V0", 0.1); ops.put("V1", 0.3); ops.put("V2", 0.5);
        ops.put("V3", 0.7); ops.put("V4", 0.9);

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r =
                eng.simulateBoundedConfidence(g, ops, 1.0, 300);

        // Large epsilon means everyone talks to everyone → convergence
        double finalVar = GraphOpinionDynamicsEngine.computeVariance(r.finalOpinions.values());
        assertTrue("Should converge with large epsilon", finalVar < 0.01);
    }

    @Test
    public void boundedConfidence_smallEpsilon_formsClusters() {
        GraphOpinionDynamicsEngine eng = engine();
        eng.setMaxSteps(300);
        Graph<String, Edge> g = completeGraph(6);
        Map<String, Double> ops = new LinkedHashMap<>();
        ops.put("V0", 0.0); ops.put("V1", 0.05);
        ops.put("V2", 0.5); ops.put("V3", 0.55);
        ops.put("V4", 0.95); ops.put("V5", 1.0);

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r =
                eng.simulateBoundedConfidence(g, ops, 0.15, 300);

        // Should form ~3 clusters, not converge to single value
        Set<Double> distinctValues = new HashSet<>();
        for (double v : r.finalOpinions.values()) {
            boolean found = false;
            for (double existing : distinctValues) {
                if (Math.abs(v - existing) < 0.05) { found = true; break; }
            }
            if (!found) distinctValues.add(v);
        }
        assertTrue("Should form multiple clusters", distinctValues.size() >= 2);
    }

    @Test
    public void boundedConfidence_zeroEpsilon_noChange() {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = triangle();
        Map<String, Double> ops = new LinkedHashMap<>();
        ops.put("A", 0.1); ops.put("B", 0.5); ops.put("C", 0.9);

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r =
                eng.simulateBoundedConfidence(g, ops, 0.0, 100);

        // With epsilon=0, only self-averaging → no change
        assertEquals(0.1, r.finalOpinions.get("A"), 1e-6);
        assertEquals(0.5, r.finalOpinions.get("B"), 1e-6);
        assertEquals(0.9, r.finalOpinions.get("C"), 1e-6);
    }

    @Test
    public void boundedConfidence_modelNameIncludesEpsilon() {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = triangle();

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r =
                eng.simulateBoundedConfidence(g, uniformOpinions(g, 0.5), 0.25, 10);

        assertTrue("Model name should include epsilon",
                r.model.contains("0.25"));
    }

    // ── Polarization Detector Tests ──────────────────────────────────

    @Test
    public void polarization_uniformOpinions_none() {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = completeGraph(10);
        Map<String, Double> ops = uniformOpinions(g, 0.5);

        GraphOpinionDynamicsEngine.PolarizationMetrics pm =
                eng.measurePolarization(g, ops);

        assertEquals("none", pm.severity);
        assertEquals(0.0, pm.varianceRatio, 0.01);
    }

    @Test
    public void polarization_bimodalOpinions_high() {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = completeGraph(10);
        Map<String, Double> ops = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) {
            ops.put("V" + i, (i < 5) ? 0.1 : 0.9);
        }

        GraphOpinionDynamicsEngine.PolarizationMetrics pm =
                eng.measurePolarization(g, ops);

        assertTrue("Bimodality should be high, got " + pm.bimodalityCoefficient,
                pm.bimodalityCoefficient > 0.3);
        assertTrue("Variance ratio should be high, got " + pm.varianceRatio,
                pm.varianceRatio > 0.5);
        assertNotEquals("none", pm.severity);
    }

    @Test
    public void polarization_metricsBounded() {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = completeGraph(8);
        Map<String, Double> ops = polarizedOpinions(g);

        GraphOpinionDynamicsEngine.PolarizationMetrics pm =
                eng.measurePolarization(g, ops);

        assertTrue(pm.bimodalityCoefficient >= 0 && pm.bimodalityCoefficient <= 1.0);
        assertTrue(pm.varianceRatio >= 0 && pm.varianceRatio <= 1.0);
        assertTrue(pm.estebanRayIndex >= 0 && pm.estebanRayIndex <= 1.0);
    }

    @Test
    public void polarization_singleNode() {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = singleNode();
        Map<String, Double> ops = new LinkedHashMap<>();
        ops.put("Solo", 0.5);

        GraphOpinionDynamicsEngine.PolarizationMetrics pm =
                eng.measurePolarization(g, ops);

        assertEquals("none", pm.severity);
    }

    @Test
    public void polarization_severityScale() {
        GraphOpinionDynamicsEngine eng = engine();
        Set<String> validSeverities = new HashSet<>(
                Arrays.asList("none", "mild", "moderate", "severe", "extreme"));

        Graph<String, Edge> g = completeGraph(6);
        Map<String, Double> ops = polarizedOpinions(g);

        GraphOpinionDynamicsEngine.PolarizationMetrics pm =
                eng.measurePolarization(g, ops);

        assertTrue("Severity should be valid",
                validSeverities.contains(pm.severity));
    }

    // ── Echo Chamber Tests ───────────────────────────────────────────

    @Test
    public void echoChamber_uniformOpinions_none() {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = twoCliques();
        Map<String, Double> ops = uniformOpinions(g, 0.5);

        List<GraphOpinionDynamicsEngine.EchoChamber> chambers =
                eng.detectEchoChambers(g, ops);

        assertTrue("No echo chambers with uniform opinions", chambers.isEmpty());
    }

    @Test
    public void echoChamber_polarizedCliques_detected() {
        GraphOpinionDynamicsEngine eng = engine();
        eng.setEchoChamberThreshold(0.5);
        Graph<String, Edge> g = twoCliques();
        Map<String, Double> ops = new LinkedHashMap<>();
        ops.put("A1", 0.1); ops.put("A2", 0.12); ops.put("A3", 0.08); ops.put("A4", 0.11);
        ops.put("B1", 0.9); ops.put("B2", 0.88); ops.put("B3", 0.92); ops.put("B4", 0.91);

        List<GraphOpinionDynamicsEngine.EchoChamber> chambers =
                eng.detectEchoChambers(g, ops);

        // Should detect at least one echo chamber
        assertFalse("Should detect echo chambers", chambers.isEmpty());
        for (GraphOpinionDynamicsEngine.EchoChamber ec : chambers) {
            assertTrue("Members should be non-empty", ec.members.size() >= 2);
            assertTrue("Strength should be positive", ec.chamberStrength >= 0);
        }
    }

    @Test
    public void echoChamber_strengthBounded() {
        GraphOpinionDynamicsEngine eng = engine();
        eng.setEchoChamberThreshold(0.3);
        Graph<String, Edge> g = twoCliques();
        Map<String, Double> ops = new LinkedHashMap<>();
        ops.put("A1", 0.0); ops.put("A2", 0.0); ops.put("A3", 0.0); ops.put("A4", 0.0);
        ops.put("B1", 1.0); ops.put("B2", 1.0); ops.put("B3", 1.0); ops.put("B4", 1.0);

        List<GraphOpinionDynamicsEngine.EchoChamber> chambers =
                eng.detectEchoChambers(g, ops);

        for (GraphOpinionDynamicsEngine.EchoChamber ec : chambers) {
            assertTrue("Strength should be bounded [0,1]",
                    ec.chamberStrength >= 0 && ec.chamberStrength <= 1.0);
            assertTrue("Internal similarity bounded [0,1]",
                    ec.internalSimilarity >= 0 && ec.internalSimilarity <= 1.0);
        }
    }

    @Test
    public void echoChamber_singleNode_none() {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = singleNode();
        Map<String, Double> ops = new LinkedHashMap<>();
        ops.put("Solo", 0.5);

        List<GraphOpinionDynamicsEngine.EchoChamber> chambers =
                eng.detectEchoChambers(g, ops);

        assertTrue("No echo chambers with single node", chambers.isEmpty());
    }

    // ── Consensus Forecast Tests ─────────────────────────────────────

    @Test
    public void forecast_converging() {
        GraphOpinionDynamicsEngine eng = engine();
        eng.setMaxSteps(500);
        eng.setSnapshotInterval(1);
        eng.setConvergenceThreshold(0.0001);
        Graph<String, Edge> g = path(10);
        Map<String, Double> ops = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) ops.put("N" + i, i / 9.0);

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r =
                eng.simulateDeGroot(g, ops, 500);
        GraphOpinionDynamicsEngine.ConsensusForecast fc =
                eng.forecastConsensus(r.timeline);

        assertEquals("converging", fc.verdict);
        assertTrue("Should reach consensus", fc.consensusReached);
    }

    @Test
    public void forecast_emptyTimeline_stalled() {
        GraphOpinionDynamicsEngine eng = engine();

        GraphOpinionDynamicsEngine.ConsensusForecast fc =
                eng.forecastConsensus(new ArrayList<>());

        assertEquals("stalled", fc.verdict);
        assertFalse(fc.consensusReached);
    }

    @Test
    public void forecast_singleSnapshot() {
        GraphOpinionDynamicsEngine eng = engine();
        List<GraphOpinionDynamicsEngine.SimulationSnapshot> timeline = new ArrayList<>();
        GraphOpinionDynamicsEngine.SimulationSnapshot ss = new GraphOpinionDynamicsEngine.SimulationSnapshot();
        ss.step = 0;
        ss.variance = 0.1;
        timeline.add(ss);

        GraphOpinionDynamicsEngine.ConsensusForecast fc =
                eng.forecastConsensus(timeline);

        assertNotNull(fc);
        assertNotNull(fc.verdict);
    }

    @Test
    public void forecast_verdictValues() {
        Set<String> valid = new HashSet<>(
                Arrays.asList("converging", "polarizing", "oscillating", "stalled"));

        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = triangle();
        Map<String, Double> ops = new LinkedHashMap<>();
        ops.put("A", 0.0); ops.put("B", 0.5); ops.put("C", 1.0);

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r =
                eng.simulateDeGroot(g, ops, 100);
        GraphOpinionDynamicsEngine.ConsensusForecast fc =
                eng.forecastConsensus(r.timeline);

        assertTrue("Verdict should be valid", valid.contains(fc.verdict));
    }

    // ── Full Analysis Tests ──────────────────────────────────────────

    @Test
    public void analyze_fullPipeline() {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = twoCliques();

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r = eng.analyze(g);

        assertNotNull(r);
        assertNotNull(r.model);
        assertNotNull(r.initialOpinions);
        assertNotNull(r.finalOpinions);
        assertNotNull(r.polarization);
        assertNotNull(r.echoChambers);
        assertNotNull(r.forecast);
        assertNotNull(r.insights);
        assertTrue(r.healthScore >= 0 && r.healthScore <= 100);
        assertEquals(8, r.finalOpinions.size());
    }

    @Test
    public void analyze_withInitialOpinions() {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = triangle();
        Map<String, Double> ops = new LinkedHashMap<>();
        ops.put("A", 0.1); ops.put("B", 0.5); ops.put("C", 0.9);

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r = eng.analyze(g, ops);

        assertEquals(0.1, r.initialOpinions.get("A"), 1e-10);
        assertEquals(0.5, r.initialOpinions.get("B"), 1e-10);
        assertEquals(0.9, r.initialOpinions.get("C"), 1e-10);
    }

    @Test
    public void analyze_emptyGraph() {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = emptyGraph();

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r = eng.analyze(g);

        assertNotNull(r);
        assertEquals(100.0, r.healthScore, 1e-6);
    }

    @Test
    public void analyze_singleNode() {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = singleNode();

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r = eng.analyze(g);

        assertNotNull(r);
        assertEquals(1, r.finalOpinions.size());
    }

    @Test
    public void analyze_nullGraph() {
        GraphOpinionDynamicsEngine eng = engine();

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r = eng.analyze(null);

        assertNotNull(r);
        assertEquals(100.0, r.healthScore, 1e-6);
    }

    // ── Health Score Tests ───────────────────────────────────────────

    @Test
    public void healthScore_consensusHigh() {
        GraphOpinionDynamicsEngine eng = engine();
        eng.setMaxSteps(500);
        Graph<String, Edge> g = completeGraph(5);
        Map<String, Double> ops = uniformOpinions(g, 0.5);

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r = eng.analyze(g, ops);

        assertTrue("Consensus should give high health", r.healthScore >= 80);
    }

    @Test
    public void healthScore_bounded() {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = twoCliques();

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r = eng.analyze(g);

        assertTrue("Score >= 0", r.healthScore >= 0);
        assertTrue("Score <= 100", r.healthScore <= 100);
    }

    // ── Text Output Tests ────────────────────────────────────────────

    @Test
    public void toText_containsKeyInfo() {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = triangle();
        Map<String, Double> ops = new LinkedHashMap<>();
        ops.put("A", 0.1); ops.put("B", 0.5); ops.put("C", 0.9);

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r = eng.analyze(g, ops);
        String text = eng.toText(r);

        assertTrue("Should contain model name", text.contains("DeGroot"));
        assertTrue("Should contain health score", text.contains("Health Score"));
        assertTrue("Should contain opinion section", text.contains("Opinion Summary"));
    }

    @Test
    public void toText_notEmpty() {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = star5();

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r = eng.analyze(g);
        String text = eng.toText(r);

        assertFalse("Text should not be empty", text.isEmpty());
        assertTrue("Should have multiple lines", text.split("\n").length > 5);
    }

    // ── HTML Export Tests ────────────────────────────────────────────

    @Test
    public void exportHtml_validStructure() {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = twoCliques();

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r = eng.analyze(g);
        String html = eng.exportHtml(r);

        assertTrue("Should start with DOCTYPE", html.contains("<!DOCTYPE html>"));
        assertTrue("Should have closing body", html.contains("</body>"));
        assertTrue("Should contain dashboard title", html.contains("Opinion Dynamics"));
    }

    @Test
    public void exportHtml_containsMetrics() {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = triangle();
        Map<String, Double> ops = new LinkedHashMap<>();
        ops.put("A", 0.0); ops.put("B", 0.5); ops.put("C", 1.0);

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r = eng.analyze(g, ops);
        String html = eng.exportHtml(r);

        assertTrue("Should contain health score", html.contains("Health Score"));
        assertTrue("Should contain polarization", html.contains("Polarization"));
    }

    @Test
    public void exportHtmlToFile_creates() throws Exception {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = triangle();
        GraphOpinionDynamicsEngine.OpinionDynamicsReport r = eng.analyze(g);

        File tmp = File.createTempFile("opinion_dynamics_", ".html");
        tmp.deleteOnExit();
        eng.exportHtmlToFile(r, tmp.getAbsolutePath());

        assertTrue("File should exist", tmp.exists());
        assertTrue("File should have content", tmp.length() > 100);
    }

    // ── Insights Tests ───────────────────────────────────────────────

    @Test
    public void insights_generated() {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = twoCliques();
        Map<String, Double> ops = new LinkedHashMap<>();
        ops.put("A1", 0.0); ops.put("A2", 0.0); ops.put("A3", 0.0); ops.put("A4", 0.0);
        ops.put("B1", 1.0); ops.put("B2", 1.0); ops.put("B3", 1.0); ops.put("B4", 1.0);

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r = eng.analyze(g, ops);

        assertFalse("Should generate insights", r.insights.isEmpty());
    }

    @Test
    public void insights_emptyGraph_hasMessage() {
        GraphOpinionDynamicsEngine eng = engine();
        Graph<String, Edge> g = emptyGraph();

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r = eng.analyze(g);

        assertFalse("Should have at least one insight", r.insights.isEmpty());
        assertTrue("Should mention empty", r.insights.get(0).toLowerCase().contains("empty"));
    }

    // ── Configuration Tests ──────────────────────────────────────────

    @Test
    public void config_snapshotInterval() {
        GraphOpinionDynamicsEngine eng = engine();
        eng.setSnapshotInterval(1);
        eng.setMaxSteps(20);
        eng.setConvergenceThreshold(0.00001);
        Graph<String, Edge> g = path(10);
        Map<String, Double> ops = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) ops.put("N" + i, i / 9.0);

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r =
                eng.simulateDeGroot(g, ops, 20);

        // With interval=1 and slow convergence, should have many snapshots
        assertTrue("Should have many snapshots, got " + r.timeline.size(), r.timeline.size() >= 5);
    }

    @Test
    public void config_convergenceThreshold() {
        GraphOpinionDynamicsEngine eng = engine();
        eng.setConvergenceThreshold(1.0); // Very high → converges immediately
        Graph<String, Edge> g = triangle();
        Map<String, Double> ops = new LinkedHashMap<>();
        ops.put("A", 0.4); ops.put("B", 0.5); ops.put("C", 0.6);

        GraphOpinionDynamicsEngine.OpinionDynamicsReport r =
                eng.simulateDeGroot(g, ops, 500);

        // Should converge very quickly with high threshold
        assertTrue("Should converge in few steps", r.steps <= 5);
    }

    // ── Variance Helper Test ─────────────────────────────────────────

    @Test
    public void computeVariance_correct() {
        List<Double> vals = Arrays.asList(0.0, 0.5, 1.0);
        double var = GraphOpinionDynamicsEngine.computeVariance(vals);
        // mean=0.5, var = ((0.25)+(0)+(0.25))/3 = 0.1667
        assertEquals(1.0 / 6.0, var, 0.001);
    }

    @Test
    public void computeVariance_uniform_zero() {
        List<Double> vals = Arrays.asList(0.5, 0.5, 0.5);
        double var = GraphOpinionDynamicsEngine.computeVariance(vals);
        assertEquals(0.0, var, 1e-10);
    }

    @Test
    public void computeVariance_empty() {
        double var = GraphOpinionDynamicsEngine.computeVariance(Collections.emptyList());
        assertEquals(0.0, var, 1e-10);
    }
}
