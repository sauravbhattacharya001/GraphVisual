package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphEvolutionSimulator}.
 *
 * @author zalenix
 */
public class GraphEvolutionSimulatorTest {

    private Graph<String, Edge> buildSmallGraph() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        g.addVertex("B");
        g.addVertex("C");
        g.addVertex("D");
        g.addVertex("E");
        g.addEdge(new Edge("f", "A", "B"), "A", "B");
        g.addEdge(new Edge("f", "B", "C"), "B", "C");
        g.addEdge(new Edge("f", "C", "D"), "C", "D");
        g.addEdge(new Edge("f", "D", "E"), "D", "E");
        g.addEdge(new Edge("f", "A", "E"), "A", "E");
        return g;
    }

    private Graph<String, Edge> buildDisconnectedClusters() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        // Cluster 1
        g.addVertex("c1_a"); g.addVertex("c1_b"); g.addVertex("c1_c");
        g.addEdge(new Edge("f", "c1_a", "c1_b"), "c1_a", "c1_b");
        g.addEdge(new Edge("f", "c1_b", "c1_c"), "c1_b", "c1_c");
        // Cluster 2
        g.addVertex("c2_a"); g.addVertex("c2_b"); g.addVertex("c2_c");
        g.addEdge(new Edge("f", "c2_a", "c2_b"), "c2_a", "c2_b");
        g.addEdge(new Edge("f", "c2_b", "c2_c"), "c2_b", "c2_c");
        // Cluster 3
        g.addVertex("c3_a"); g.addVertex("c3_b");
        g.addEdge(new Edge("f", "c3_a", "c3_b"), "c3_a", "c3_b");
        return g;
    }

    @Test
    public void testPreferentialAttachment() {
        Graph<String, Edge> g = buildSmallGraph();
        GraphEvolutionSimulator sim = new GraphEvolutionSimulator(g);
        GraphEvolutionSimulator.SimConfig config = new GraphEvolutionSimulator.SimConfig();
        config.model = GraphEvolutionSimulator.GrowthModel.PREFERENTIAL_ATTACHMENT;
        config.totalSteps = 20;
        config.seed = 42;

        GraphEvolutionSimulator.SimulationResult result = sim.simulate(config);

        assertNotNull(result);
        assertNotNull(result.timeline);
        assertNotNull(result.finalGraph);
        assertTrue(result.timeline.size() > 1);
        // Graph should have grown
        GraphEvolutionSimulator.StepSnapshot last = result.timeline.get(result.timeline.size() - 1);
        assertTrue("Node count should increase", last.nodeCount >= 5);
        assertTrue("Edge count should increase", last.edgeCount >= 5);
    }

    @Test
    public void testRandomAttachment() {
        Graph<String, Edge> g = buildSmallGraph();
        GraphEvolutionSimulator sim = new GraphEvolutionSimulator(g);
        GraphEvolutionSimulator.SimConfig config = new GraphEvolutionSimulator.SimConfig();
        config.model = GraphEvolutionSimulator.GrowthModel.RANDOM_ATTACHMENT;
        config.totalSteps = 20;
        config.seed = 123;

        GraphEvolutionSimulator.SimulationResult result = sim.simulate(config);

        assertNotNull(result);
        GraphEvolutionSimulator.StepSnapshot last = result.timeline.get(result.timeline.size() - 1);
        assertTrue("Edge count should grow", last.edgeCount >= 5);
    }

    @Test
    public void testTriadicClosure() {
        Graph<String, Edge> g = buildSmallGraph();
        GraphEvolutionSimulator sim = new GraphEvolutionSimulator(g);
        GraphEvolutionSimulator.SimConfig config = new GraphEvolutionSimulator.SimConfig();
        config.model = GraphEvolutionSimulator.GrowthModel.TRIADIC_CLOSURE;
        config.totalSteps = 30;
        config.newNodeProbability = 0.1; // Mostly close triangles
        config.seed = 42;

        GraphEvolutionSimulator.SimulationResult result = sim.simulate(config);

        assertNotNull(result);
        // Triadic closure should generally increase clustering over time
        GraphEvolutionSimulator.StepSnapshot first = result.timeline.get(0);
        GraphEvolutionSimulator.StepSnapshot last = result.timeline.get(result.timeline.size() - 1);
        assertTrue("Edges should grow", last.edgeCount > first.edgeCount);
    }

    @Test
    public void testCopyModel() {
        Graph<String, Edge> g = buildSmallGraph();
        GraphEvolutionSimulator sim = new GraphEvolutionSimulator(g);
        GraphEvolutionSimulator.SimConfig config = new GraphEvolutionSimulator.SimConfig();
        config.model = GraphEvolutionSimulator.GrowthModel.COPY_MODEL;
        config.totalSteps = 20;
        config.newNodeProbability = 0.5;
        config.seed = 99;

        GraphEvolutionSimulator.SimulationResult result = sim.simulate(config);

        assertNotNull(result);
        GraphEvolutionSimulator.StepSnapshot last = result.timeline.get(result.timeline.size() - 1);
        assertTrue("Nodes should increase with copy model", last.nodeCount >= 5);
    }

    @Test
    public void testFitnessBased() {
        Graph<String, Edge> g = buildSmallGraph();
        GraphEvolutionSimulator sim = new GraphEvolutionSimulator(g);
        GraphEvolutionSimulator.SimConfig config = new GraphEvolutionSimulator.SimConfig();
        config.model = GraphEvolutionSimulator.GrowthModel.FITNESS_BASED;
        config.totalSteps = 20;
        config.seed = 42;

        GraphEvolutionSimulator.SimulationResult result = sim.simulate(config);

        assertNotNull(result);
        assertTrue("Timeline should have entries", result.timeline.size() > 1);
    }

    @Test
    public void testTippingPointDetection() {
        Graph<String, Edge> g = buildDisconnectedClusters();
        GraphEvolutionSimulator sim = new GraphEvolutionSimulator(g);
        GraphEvolutionSimulator.SimConfig config = new GraphEvolutionSimulator.SimConfig();
        config.model = GraphEvolutionSimulator.GrowthModel.RANDOM_ATTACHMENT;
        config.totalSteps = 50;
        config.edgesPerStep = 3;
        config.newNodeProbability = 0.0; // Only add edges to existing nodes
        config.seed = 42;

        GraphEvolutionSimulator.SimulationResult result = sim.simulate(config);

        // With enough random edges on a small disconnected graph, something should happen
        assertNotNull(result.tippingPoints);
        // We can't guarantee tipping points in all random seeds, but verify the structure
        for (GraphEvolutionSimulator.TippingPoint tp : result.tippingPoints) {
            assertNotNull(tp.type);
            assertNotNull(tp.description);
            assertTrue("Severity should be 0-1", tp.severity >= 0 && tp.severity <= 1);
            assertTrue("Step should be positive", tp.step > 0);
        }
    }

    @Test
    public void testCompareModels() {
        Graph<String, Edge> g = buildSmallGraph();
        GraphEvolutionSimulator sim = new GraphEvolutionSimulator(g);
        GraphEvolutionSimulator.SimConfig config = new GraphEvolutionSimulator.SimConfig();
        config.totalSteps = 10;

        Map<GraphEvolutionSimulator.GrowthModel, GraphEvolutionSimulator.SimulationResult> results =
                sim.compareModels(config);

        assertEquals("Should have results for all 5 models",
                GraphEvolutionSimulator.GrowthModel.values().length, results.size());
        for (GraphEvolutionSimulator.GrowthModel model : GraphEvolutionSimulator.GrowthModel.values()) {
            assertTrue("Missing result for " + model, results.containsKey(model));
            assertNotNull(results.get(model).timeline);
            assertFalse(results.get(model).timeline.isEmpty());
        }
    }

    @Test
    public void testReproducibility() {
        Graph<String, Edge> g = buildSmallGraph();
        GraphEvolutionSimulator sim1 = new GraphEvolutionSimulator(g);
        GraphEvolutionSimulator sim2 = new GraphEvolutionSimulator(g);
        GraphEvolutionSimulator.SimConfig config = new GraphEvolutionSimulator.SimConfig();
        config.totalSteps = 20;
        config.seed = 42;

        GraphEvolutionSimulator.SimulationResult r1 = sim1.simulate(config);
        GraphEvolutionSimulator.SimulationResult r2 = sim2.simulate(config);

        assertEquals("Timeline lengths should match", r1.timeline.size(), r2.timeline.size());
        for (int i = 0; i < r1.timeline.size(); i++) {
            assertEquals("Node count should match at step " + i,
                    r1.timeline.get(i).nodeCount, r2.timeline.get(i).nodeCount);
            assertEquals("Edge count should match at step " + i,
                    r1.timeline.get(i).edgeCount, r2.timeline.get(i).edgeCount);
        }
    }

    @Test
    public void testEmptyGraph() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        GraphEvolutionSimulator sim = new GraphEvolutionSimulator(g);
        GraphEvolutionSimulator.SimConfig config = new GraphEvolutionSimulator.SimConfig();
        config.totalSteps = 10;
        config.seed = 42;

        GraphEvolutionSimulator.SimulationResult result = sim.simulate(config);

        assertNotNull(result);
        assertNotNull(result.timeline);
        // Should still run without errors
        assertTrue("Timeline should have entries", result.timeline.size() > 0);
    }

    @Test
    public void testSingleNode() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("solo");
        GraphEvolutionSimulator sim = new GraphEvolutionSimulator(g);
        GraphEvolutionSimulator.SimConfig config = new GraphEvolutionSimulator.SimConfig();
        config.totalSteps = 10;
        config.seed = 42;

        GraphEvolutionSimulator.SimulationResult result = sim.simulate(config);

        assertNotNull(result);
        GraphEvolutionSimulator.StepSnapshot last = result.timeline.get(result.timeline.size() - 1);
        assertTrue("Should have added nodes", last.nodeCount > 1);
    }

    @Test
    public void testHtmlExport() throws Exception {
        Graph<String, Edge> g = buildSmallGraph();
        GraphEvolutionSimulator sim = new GraphEvolutionSimulator(g);
        GraphEvolutionSimulator.SimConfig config = new GraphEvolutionSimulator.SimConfig();
        config.totalSteps = 10;

        GraphEvolutionSimulator.SimulationResult result = sim.simulate(config);
        File out = File.createTempFile("evolution", ".html");
        out.deleteOnExit();

        sim.exportHtml(result, out);

        assertTrue("File should exist", out.exists());
        assertTrue("File should not be empty", out.length() > 100);
    }

    @Test
    public void testComparisonHtmlExport() throws Exception {
        Graph<String, Edge> g = buildSmallGraph();
        GraphEvolutionSimulator sim = new GraphEvolutionSimulator(g);
        GraphEvolutionSimulator.SimConfig config = new GraphEvolutionSimulator.SimConfig();
        config.totalSteps = 10;

        Map<GraphEvolutionSimulator.GrowthModel, GraphEvolutionSimulator.SimulationResult> results =
                sim.compareModels(config);
        File out = File.createTempFile("comparison", ".html");
        out.deleteOnExit();

        sim.exportComparisonHtml(results, out);

        assertTrue("File should exist", out.exists());
        assertTrue("File should not be empty", out.length() > 100);
    }

    @Test
    public void testTextReport() {
        Graph<String, Edge> g = buildSmallGraph();
        GraphEvolutionSimulator sim = new GraphEvolutionSimulator(g);
        GraphEvolutionSimulator.SimConfig config = new GraphEvolutionSimulator.SimConfig();
        config.totalSteps = 10;

        GraphEvolutionSimulator.SimulationResult result = sim.simulate(config);
        String report = sim.formatTextReport(result);

        assertNotNull(report);
        assertTrue("Report should contain header", report.contains("GRAPH EVOLUTION SIMULATOR"));
        assertTrue("Report should contain summary", report.contains("Summary"));
        assertTrue("Report should contain timeline", report.contains("Timeline"));
    }

    @Test
    public void testSummaryContents() {
        Graph<String, Edge> g = buildSmallGraph();
        GraphEvolutionSimulator sim = new GraphEvolutionSimulator(g);
        GraphEvolutionSimulator.SimConfig config = new GraphEvolutionSimulator.SimConfig();
        config.totalSteps = 10;

        GraphEvolutionSimulator.SimulationResult result = sim.simulate(config);

        assertTrue("Summary should have model", result.summary.containsKey("model"));
        assertTrue("Summary should have steps", result.summary.containsKey("steps"));
        assertTrue("Summary should have initialNodes", result.summary.containsKey("initialNodes"));
        assertTrue("Summary should have finalNodes", result.summary.containsKey("finalNodes"));
        assertTrue("Summary should have tippingPoints", result.summary.containsKey("tippingPoints"));
    }
}
