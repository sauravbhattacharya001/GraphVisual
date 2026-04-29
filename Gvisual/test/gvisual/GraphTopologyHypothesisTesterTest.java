package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for GraphTopologyHypothesisTester.
 */
public class GraphTopologyHypothesisTesterTest {

    private Graph<String, Edge> starGraph;
    private Graph<String, Edge> completeGraph;
    private Graph<String, Edge> ringGraph;
    private Graph<String, Edge> randomLikeGraph;

    @Before
    public void setUp() {
        starGraph = buildStarGraph(20);
        completeGraph = buildCompleteGraph(10);
        ringGraph = buildRingWithShortcuts(30, 3);
        randomLikeGraph = buildRandomLikeGraph(25, 0.3);
    }

    @Test
    public void testBasicConstruction() {
        GraphTopologyHypothesisTester tester = new GraphTopologyHypothesisTester(starGraph);
        assertNotNull(tester);
    }

    @Test(expected = IllegalStateException.class)
    public void testAccessBeforeAnalysis() {
        GraphTopologyHypothesisTester tester = new GraphTopologyHypothesisTester(starGraph);
        tester.getRankedResults();
    }

    @Test(expected = IllegalStateException.class)
    public void testSmallGraphThrows() {
        Graph<String, Edge> tiny = new UndirectedSparseGraph<>();
        tiny.addVertex("A");
        tiny.addVertex("B");
        tiny.addEdge(new Edge("A", "B", "test"), "A", "B");
        GraphTopologyHypothesisTester tester = new GraphTopologyHypothesisTester(tiny);
        tester.runAllTests();
    }

    @Test
    public void testStarGraphDetectsScaleFreeOrCorePeriphery() {
        GraphTopologyHypothesisTester tester = new GraphTopologyHypothesisTester(starGraph, 50);
        tester.runAllTests();
        List<GraphTopologyHypothesisTester.HypothesisResult> results = tester.getRankedResults();
        assertNotNull(results);
        assertEquals(5, results.size());
        // Star should score well on core-periphery or scale-free
        GraphTopologyHypothesisTester.TopologyType best = tester.getBestFitTopology();
        assertTrue(best == GraphTopologyHypothesisTester.TopologyType.CORE_PERIPHERY ||
                   best == GraphTopologyHypothesisTester.TopologyType.SCALE_FREE);
    }

    @Test
    public void testCompleteGraphDetectsRandom() {
        GraphTopologyHypothesisTester tester = new GraphTopologyHypothesisTester(completeGraph, 50);
        tester.runAllTests();
        // Complete graph has uniform degree; should detect random or not scale-free
        double sfConf = tester.getConfidence(GraphTopologyHypothesisTester.TopologyType.SCALE_FREE);
        double randomConf = tester.getConfidence(GraphTopologyHypothesisTester.TopologyType.RANDOM);
        // Scale-free should not be the strongest for a complete graph
        assertTrue("Complete graph shouldn't strongly match scale-free", sfConf < 0.9);
    }

    @Test
    public void testRingWithShortcutsDetectsSmallWorld() {
        GraphTopologyHypothesisTester tester = new GraphTopologyHypothesisTester(ringGraph, 50);
        tester.runAllTests();
        double swConf = tester.getConfidence(GraphTopologyHypothesisTester.TopologyType.SMALL_WORLD);
        // Ring with shortcuts should show at least moderate small-world signal
        assertTrue("Ring with shortcuts should show some small-world signal", swConf > 0.1);
    }

    @Test
    public void testRandomLikeGraphDetectsRandom() {
        GraphTopologyHypothesisTester tester = new GraphTopologyHypothesisTester(randomLikeGraph, 50);
        tester.runAllTests();
        double randomConf = tester.getConfidence(GraphTopologyHypothesisTester.TopologyType.RANDOM);
        // Random graph should have decent random confidence
        assertTrue("Random-like graph should score on random hypothesis", randomConf > 0.2);
    }

    @Test
    public void testAllResultsHaveEvidence() {
        GraphTopologyHypothesisTester tester = new GraphTopologyHypothesisTester(starGraph, 50);
        tester.runAllTests();
        for (GraphTopologyHypothesisTester.HypothesisResult r : tester.getRankedResults()) {
            assertNotNull(r.getEvidence());
            assertFalse("Each result should have evidence", r.getEvidence().isEmpty());
            assertNotNull(r.getMetrics());
            assertFalse("Each result should have metrics", r.getMetrics().isEmpty());
            assertNotNull(r.getVerdict());
            assertTrue(r.getConfidence() >= 0 && r.getConfidence() <= 1);
        }
    }

    @Test
    public void testGetResultForSpecificType() {
        GraphTopologyHypothesisTester tester = new GraphTopologyHypothesisTester(starGraph, 50);
        tester.runAllTests();
        for (GraphTopologyHypothesisTester.TopologyType type : GraphTopologyHypothesisTester.TopologyType.values()) {
            GraphTopologyHypothesisTester.HypothesisResult r = tester.getResult(type);
            assertNotNull(r);
            assertEquals(type, r.getTopology());
        }
    }

    @Test
    public void testVerdictThresholds() {
        assertEquals(GraphTopologyHypothesisTester.Verdict.STRONG_MATCH,
                GraphTopologyHypothesisTester.Verdict.fromConfidence(0.9));
        assertEquals(GraphTopologyHypothesisTester.Verdict.MODERATE_MATCH,
                GraphTopologyHypothesisTester.Verdict.fromConfidence(0.7));
        assertEquals(GraphTopologyHypothesisTester.Verdict.WEAK_MATCH,
                GraphTopologyHypothesisTester.Verdict.fromConfidence(0.5));
        assertEquals(GraphTopologyHypothesisTester.Verdict.UNLIKELY,
                GraphTopologyHypothesisTester.Verdict.fromConfidence(0.3));
        assertEquals(GraphTopologyHypothesisTester.Verdict.REJECTED,
                GraphTopologyHypothesisTester.Verdict.fromConfidence(0.1));
    }

    @Test
    public void testReportGeneration() {
        GraphTopologyHypothesisTester tester = new GraphTopologyHypothesisTester(starGraph, 50);
        tester.runAllTests();
        String report = tester.generateReport();
        assertNotNull(report);
        assertTrue(report.contains("GRAPH TOPOLOGY HYPOTHESIS TEST REPORT"));
        assertTrue(report.contains("RANKED RESULTS"));
        assertTrue(report.contains("VERDICT"));
        assertTrue(report.contains("Confidence:"));
    }

    @Test
    public void testCustomTrialCount() {
        GraphTopologyHypothesisTester tester = new GraphTopologyHypothesisTester(starGraph, 20);
        tester.runAllTests();
        assertNotNull(tester.getRankedResults());
    }

    @Test
    public void testResultsAreSortedByConfidence() {
        GraphTopologyHypothesisTester tester = new GraphTopologyHypothesisTester(ringGraph, 50);
        tester.runAllTests();
        List<GraphTopologyHypothesisTester.HypothesisResult> results = tester.getRankedResults();
        for (int i = 0; i < results.size() - 1; i++) {
            assertTrue(results.get(i).getConfidence() >= results.get(i + 1).getConfidence());
        }
    }

    @Test
    public void testScaleFreeNetwork() {
        // Build a preferential-attachment-like graph
        Graph<String, Edge> sfGraph = buildPreferentialAttachment(40);
        GraphTopologyHypothesisTester tester = new GraphTopologyHypothesisTester(sfGraph, 50);
        tester.runAllTests();
        double sfConf = tester.getConfidence(GraphTopologyHypothesisTester.TopologyType.SCALE_FREE);
        // Should detect some scale-free signal
        assertTrue("Preferential attachment graph should show scale-free signal", sfConf > 0.2);
    }

    @Test
    public void testCorePeripheryNetwork() {
        Graph<String, Edge> cpGraph = buildCorePeripheryGraph(5, 20);
        GraphTopologyHypothesisTester tester = new GraphTopologyHypothesisTester(cpGraph, 50);
        tester.runAllTests();
        double cpConf = tester.getConfidence(GraphTopologyHypothesisTester.TopologyType.CORE_PERIPHERY);
        assertTrue("Explicit CP graph should detect core-periphery", cpConf > 0.3);
    }

    @Test
    public void testPValueRanges() {
        GraphTopologyHypothesisTester tester = new GraphTopologyHypothesisTester(starGraph, 50);
        tester.runAllTests();
        for (GraphTopologyHypothesisTester.HypothesisResult r : tester.getRankedResults()) {
            assertTrue("p-value should be in [0,1]", r.getPValue() >= 0 && r.getPValue() <= 1);
        }
    }

    @Test
    public void testTopologyTypeLabels() {
        for (GraphTopologyHypothesisTester.TopologyType type : GraphTopologyHypothesisTester.TopologyType.values()) {
            assertNotNull(type.getLabel());
            assertNotNull(type.getDescription());
            assertFalse(type.getLabel().isEmpty());
            assertFalse(type.getDescription().isEmpty());
        }
    }

    // ─── Graph Builders ─────────────────────────────────────────────

    private Graph<String, Edge> buildStarGraph(int leaves) {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("hub");
        for (int i = 0; i < leaves; i++) {
            String leaf = "leaf" + i;
            g.addVertex(leaf);
            g.addEdge(new Edge("hub", leaf, "star"), "hub", leaf);
        }
        return g;
    }

    private Graph<String, Edge> buildCompleteGraph(int n) {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        List<String> nodes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String v = "v" + i;
            nodes.add(v);
            g.addVertex(v);
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                g.addEdge(new Edge(nodes.get(i), nodes.get(j), "complete"),
                        nodes.get(i), nodes.get(j));
            }
        }
        return g;
    }

    private Graph<String, Edge> buildRingWithShortcuts(int n, int shortcuts) {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        List<String> nodes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String v = "r" + i;
            nodes.add(v);
            g.addVertex(v);
        }
        // Ring edges
        for (int i = 0; i < n; i++) {
            g.addEdge(new Edge(nodes.get(i), nodes.get((i + 1) % n), "ring"),
                    nodes.get(i), nodes.get((i + 1) % n));
        }
        // Extra neighbor connections (each node to +2)
        for (int i = 0; i < n; i++) {
            String target = nodes.get((i + 2) % n);
            if (g.findEdge(nodes.get(i), target) == null) {
                g.addEdge(new Edge(nodes.get(i), target, "near"), nodes.get(i), target);
            }
        }
        // Random shortcuts
        Random rng = new Random(99);
        for (int s = 0; s < shortcuts; s++) {
            int a = rng.nextInt(n);
            int b = rng.nextInt(n);
            if (a != b && g.findEdge(nodes.get(a), nodes.get(b)) == null) {
                g.addEdge(new Edge(nodes.get(a), nodes.get(b), "shortcut"),
                        nodes.get(a), nodes.get(b));
            }
        }
        return g;
    }

    private Graph<String, Edge> buildRandomLikeGraph(int n, double p) {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        List<String> nodes = new ArrayList<>();
        Random rng = new Random(123);
        for (int i = 0; i < n; i++) {
            String v = "n" + i;
            nodes.add(v);
            g.addVertex(v);
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (rng.nextDouble() < p) {
                    g.addEdge(new Edge(nodes.get(i), nodes.get(j), "random"),
                            nodes.get(i), nodes.get(j));
                }
            }
        }
        return g;
    }

    private Graph<String, Edge> buildPreferentialAttachment(int n) {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        List<String> nodes = new ArrayList<>();
        Random rng = new Random(77);
        // Start with 3-node complete
        for (int i = 0; i < 3; i++) {
            String v = "pa" + i;
            nodes.add(v);
            g.addVertex(v);
        }
        g.addEdge(new Edge("pa0", "pa1", "init"), "pa0", "pa1");
        g.addEdge(new Edge("pa0", "pa2", "init"), "pa0", "pa2");
        g.addEdge(new Edge("pa1", "pa2", "init"), "pa1", "pa2");

        // Add nodes with preferential attachment (m=2)
        for (int i = 3; i < n; i++) {
            String newNode = "pa" + i;
            g.addVertex(newNode);
            nodes.add(newNode);

            // Build degree-weighted selection
            List<String> targets = new ArrayList<>();
            for (String existing : nodes.subList(0, i)) {
                int deg = Math.max(1, g.degree(existing));
                for (int d = 0; d < deg; d++) targets.add(existing);
            }
            Set<String> connected = new HashSet<>();
            int m = Math.min(2, i);
            while (connected.size() < m && !targets.isEmpty()) {
                String target = targets.get(rng.nextInt(targets.size()));
                if (!connected.contains(target)) {
                    connected.add(target);
                    g.addEdge(new Edge(newNode, target, "pa"), newNode, target);
                }
            }
        }
        return g;
    }

    private Graph<String, Edge> buildCorePeripheryGraph(int coreSize, int periSize) {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        List<String> core = new ArrayList<>();
        List<String> peri = new ArrayList<>();
        Random rng = new Random(55);

        for (int i = 0; i < coreSize; i++) {
            String v = "core" + i;
            core.add(v);
            g.addVertex(v);
        }
        for (int i = 0; i < periSize; i++) {
            String v = "peri" + i;
            peri.add(v);
            g.addVertex(v);
        }
        // Dense core (complete)
        for (int i = 0; i < coreSize; i++) {
            for (int j = i + 1; j < coreSize; j++) {
                g.addEdge(new Edge(core.get(i), core.get(j), "core"), core.get(i), core.get(j));
            }
        }
        // Sparse periphery connections to core (each peri connects to 1-2 core nodes)
        for (String p : peri) {
            int connections = 1 + rng.nextInt(2);
            Set<String> connected = new HashSet<>();
            for (int c = 0; c < connections; c++) {
                String target = core.get(rng.nextInt(coreSize));
                if (!connected.contains(target)) {
                    connected.add(target);
                    g.addEdge(new Edge(p, target, "cp"), p, target);
                }
            }
        }
        return g;
    }
}
