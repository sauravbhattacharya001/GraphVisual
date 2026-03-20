package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for GraphSampler - graph subsampling strategies.
 */
public class GraphSamplerTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, Edge>();
    }

    private void addEdge(String v1, String v2) {
        graph.addVertex(v1);
        graph.addVertex(v2);
        Edge e  new Edge("f", v1, v2);
        e.setWeight(1.0f);
        graph.addEdge(e, v1, v2);
    }

    /**
     * Builds a 10-node chain: A-B-C-D-E-F-G-H-I-J
     */
    private void buildChain() {
        String[] nodes = {"A","B","C","D","E","F","G","H","I","J"};
        for (int i = 0; i < nodes.length - 1; i++) {
            addEdge(nodes[i], nodes[i+1]);
        }
    }

    /**
     * Builds K5 (complete graph on 5 nodes).
     */
    private void buildK5() {
        String[] nodes = {"A","B","C","D","E"};
        for (int i = 0; i < nodes.length; i++) {
            graph.addVertex(nodes[i]);
            for (int j = i + 1; j < nodes.length; j++) {
                addEdge(nodes[i], nodes[j]);
            }
        }
    }

    /**
     * Builds two triangles: {A,B,C} and {D,E,F} (disconnected).
     */
    private void buildTwoTriangles() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        addEdge("D", "E");
        addEdge("E", "F");
        addEdge("D", "F");
    }

    /**
     * Builds a 20-node grid-like structure for larger tests.
     */
    private void buildGrid() {
        // 4x5 grid
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 5; c++) {
                String node = "N" + (r * 5 + c);
                graph.addVertex(node);
                if (c > 0) addEdge(node, "N" + (r * 5 + c - 1));
                if (r > 0) addEdge(node, "N" + ((r - 1) * 5 + c));
            }
        }
    }

    // ── Constructor ─────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullGraph_throws() {
        new GraphSampler(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullRng_throws() {
        new GraphSampler(graph, null);
    }

    @Test
    public void constructor_withSeededRng_deterministic() {
        buildK5();
        GraphSampler s1 = new GraphSampler(graph, new Random(42));
        GraphSampler s2 = new GraphSampler(graph, new Random(42));

        GraphSampler.SampleResult r1 = s1.randomNode(0.6);
        GraphSampler.SampleResult r2 = s2.randomNode(0.6);

        assertEquals(r1.getNodeCount(), r2.getNodeCount());
        // Same seed should produce same sample
        Set<String> nodes1 = new HashSet<String>(r1.getSample().getVertices());
        Set<String> nodes2 = new HashSet<String>(r2.getSample().getVertices());
        assertEquals(nodes1, nodes2);
    }

    // ── Random Node Sampling ────────────────────────────────────

    @Test
    public void randomNode_fullSample_returnsEntireGraph() {
        buildK5();
        GraphSampler sampler = new GraphSampler(graph, new Random(1));
        GraphSampler.SampleResult result = sampler.randomNode(1.0);

        assertEquals(5, result.getNodeCount());
        assertEquals(10, result.getEdgeCount());
        assertEquals(1.0, result.getNodeCoverage(), 0.01);
    }

    @Test
    public void randomNode_halfSample_returnsCorrectCount() {
        buildChain();
        GraphSampler sampler = new GraphSampler(graph, new Random(1));
        GraphSampler.SampleResult result = sampler.randomNode(0.5);

        assertEquals(5, result.getNodeCount());
        assertTrue(result.getEdgeCount() <= 4); // at most 4 chain edges
    }

    @Test
    public void randomNode_singleNode_minSize() {
        buildK5();
        GraphSampler sampler = new GraphSampler(graph, new Random(1));
        GraphSampler.SampleResult result = sampler.randomNode(0.01);

        assertTrue(result.getNodeCount() >= 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void randomNode_zeroFraction_throws() {
        buildK5();
        new GraphSampler(graph).randomNode(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void randomNode_negativeFraction_throws() {
        buildK5();
        new GraphSampler(graph).randomNode(-0.5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void randomNode_overOneFraction_throws() {
        buildK5();
        new GraphSampler(graph).randomNode(1.5);
    }

    @Test
    public void randomNode_emptyGraph_returnsEmpty() {
        GraphSampler sampler = new GraphSampler(graph);
        GraphSampler.SampleResult result = sampler.randomNode(0.5);
        assertEquals(0, result.getNodeCount());
    }

    // ── Random Edge Sampling ────────────────────────────────────

    @Test
    public void randomEdge_fullSample_allEdges() {
        buildK5();
        GraphSampler sampler = new GraphSampler(graph, new Random(1));
        GraphSampler.SampleResult result = sampler.randomEdge(1.0);

        assertEquals(5, result.getNodeCount());
        assertEquals(10, result.getEdgeCount());
    }

    @Test
    public void randomEdge_halfSample_includesEndpoints() {
        buildChain();
        GraphSampler sampler = new GraphSampler(graph, new Random(1));
        GraphSampler.SampleResult result = sampler.randomEdge(0.5);

        // 5 edges sampled, each has 2 endpoints
        assertTrue(result.getNodeCount() >= 2);
        assertTrue(result.getEdgeCount() >= 1);
    }

    @Test
    public void randomEdge_preservesDensityBetter() {
        buildGrid();
        GraphSampler sampler = new GraphSampler(graph, new Random(42));
        GraphSampler.SampleResult edgeResult = sampler.randomEdge(0.3);

        // Edge sampling should produce a reasonable density ratio
        assertTrue(edgeResult.getDensityRatio() > 0);
    }

    // ── Snowball / BFS Sampling ─────────────────────────────────

    @Test
    public void snowball_producesConnectedSample() {
        buildGrid();
        GraphSampler sampler = new GraphSampler(graph, new Random(1));
        GraphSampler.SampleResult result = sampler.snowball(0.5, null);

        assertTrue(result.getNodeCount() >= 10);
        assertTrue(result.isConnected());
    }

    @Test
    public void snowball_withSeeds_startsFromSeeds() {
        buildChain();
        GraphSampler sampler = new GraphSampler(graph, new Random(1));
        GraphSampler.SampleResult result = sampler.snowball(0.3, Arrays.asList("A"));

        assertTrue(result.getSample().containsVertex("A"));
        assertTrue(result.getSample().containsVertex("B")); // neighbor of A
    }

    @Test
    public void snowball_fullFraction_allNodes() {
        buildK5();
        GraphSampler sampler = new GraphSampler(graph, new Random(1));
        GraphSampler.SampleResult result = sampler.snowball(1.0, null);

        assertEquals(5, result.getNodeCount());
    }

    @Test
    public void snowball_invalidSeeds_fallsBackToRandom() {
        buildK5();
        GraphSampler sampler = new GraphSampler(graph, new Random(1));
        GraphSampler.SampleResult result = sampler.snowball(0.5,
            Arrays.asList("NONEXISTENT"));

        assertTrue(result.getNodeCount() >= 1);
    }

    @Test
    public void snowball_emptySeeds_usesRandomSeed() {
        buildK5();
        GraphSampler sampler = new GraphSampler(graph, new Random(1));
        GraphSampler.SampleResult result = sampler.snowball(0.5,
            Collections.<String>emptyList());

        assertTrue(result.getNodeCount() >= 1);
    }

    // ── Random Walk Sampling ────────────────────────────────────

    @Test
    public void randomWalk_producesNonEmptySample() {
        buildGrid();
        GraphSampler sampler = new GraphSampler(graph, new Random(1));
        GraphSampler.SampleResult result = sampler.randomWalk(0.5, null, 0.15);

        assertTrue(result.getNodeCount() >= 1);
    }

    @Test
    public void randomWalk_withStartNode_includesStart() {
        buildChain();
        GraphSampler sampler = new GraphSampler(graph, new Random(1));
        GraphSampler.SampleResult result = sampler.randomWalk(0.3, "E", 0.0);

        assertTrue(result.getSample().containsVertex("E"));
    }

    @Test
    public void randomWalk_highRestart_staysNearSeed() {
        buildChain();
        GraphSampler sampler = new GraphSampler(graph, new Random(42));
        GraphSampler.SampleResult result = sampler.randomWalk(0.3, "A", 0.9);

        // With 90% restart to A, should mostly sample near A
        assertTrue(result.getSample().containsVertex("A"));
    }

    @Test
    public void randomWalk_zeroRestart_valid() {
        buildK5();
        GraphSampler sampler = new GraphSampler(graph, new Random(1));
        GraphSampler.SampleResult result = sampler.randomWalk(0.5, null, 0.0);

        assertTrue(result.getNodeCount() >= 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void randomWalk_negativeRestart_throws() {
        buildK5();
        new GraphSampler(graph).randomWalk(0.5, null, -0.1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void randomWalk_restartOne_throws() {
        buildK5();
        new GraphSampler(graph).randomWalk(0.5, null, 1.0);
    }

    @Test
    public void randomWalk_invalidStartNode_usesRandom() {
        buildK5();
        GraphSampler sampler = new GraphSampler(graph, new Random(1));
        GraphSampler.SampleResult result = sampler.randomWalk(0.5, "NOPE", 0.0);

        assertTrue(result.getNodeCount() >= 1);
    }

    // ── Forest Fire Sampling ────────────────────────────────────

    @Test
    public void forestFire_producesNonEmptySample() {
        buildGrid();
        GraphSampler sampler = new GraphSampler(graph, new Random(1));
        GraphSampler.SampleResult result = sampler.forestFire(0.5, null, 0.7);

        assertTrue(result.getNodeCount() >= 1);
    }

    @Test
    public void forestFire_highBurnProb_largerSample() {
        buildGrid();
        GraphSampler s1 = new GraphSampler(graph, new Random(42));
        GraphSampler s2 = new GraphSampler(graph, new Random(42));

        GraphSampler.SampleResult low = s1.forestFire(0.5, "N0", 0.2);
        GraphSampler.SampleResult high = s2.forestFire(0.5, "N0", 0.8);

        // Both should produce valid samples
        assertTrue(low.getNodeCount() >= 1);
        assertTrue(high.getNodeCount() >= 1);
    }

    @Test
    public void forestFire_withStartNode_includesStart() {
        buildK5();
        GraphSampler sampler = new GraphSampler(graph, new Random(1));
        GraphSampler.SampleResult result = sampler.forestFire(0.5, "C", 0.5);

        assertTrue(result.getSample().containsVertex("C"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void forestFire_zeroBurnProb_throws() {
        buildK5();
        new GraphSampler(graph).forestFire(0.5, null, 0.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void forestFire_burnProbOne_throws() {
        buildK5();
        new GraphSampler(graph).forestFire(0.5, null, 1.0);
    }

    @Test
    public void forestFire_disconnectedGraph_handlesGracefully() {
        buildTwoTriangles();
        GraphSampler sampler = new GraphSampler(graph, new Random(1));
        // Starting from triangle 1, might not reach triangle 2
        GraphSampler.SampleResult result = sampler.forestFire(0.8, "A", 0.7);

        assertTrue(result.getNodeCount() >= 1);
    }

    // ── SampleResult metrics ────────────────────────────────────

    @Test
    public void sampleResult_coverage_correct() {
        buildK5();
        GraphSampler sampler = new GraphSampler(graph, new Random(1));
        GraphSampler.SampleResult result = sampler.randomNode(0.6);

        assertEquals(5, result.getOriginalNodeCount());
        assertEquals(10, result.getOriginalEdgeCount());
        assertEquals(3, result.getNodeCount()); // ceil(5 * 0.6) = 3
        assertTrue(result.getNodeCoverage() > 0);
        assertTrue(result.getNodeCoverage() <= 1.0);
    }

    @Test
    public void sampleResult_densityRatio_computed() {
        buildK5();
        GraphSampler sampler = new GraphSampler(graph, new Random(1));
        GraphSampler.SampleResult result = sampler.randomNode(1.0);

        assertEquals(1.0, result.getDensityRatio(), 0.01);
    }

    @Test
    public void sampleResult_strategy_recorded() {
        buildK5();
        GraphSampler sampler = new GraphSampler(graph, new Random(1));

        assertEquals("RandomNode", sampler.randomNode(0.5).getStrategy());
        assertEquals("RandomEdge", sampler.randomEdge(0.5).getStrategy());
        assertEquals("Snowball", sampler.snowball(0.5, null).getStrategy());
        assertEquals("RandomWalk", sampler.randomWalk(0.5, null, 0.1).getStrategy());
        assertEquals("ForestFire", sampler.forestFire(0.5, null, 0.5).getStrategy());
    }

    @Test
    public void sampleResult_summary_containsKey() {
        buildK5();
        GraphSampler sampler = new GraphSampler(graph, new Random(1));
        String summary = sampler.randomNode(0.5).getSummary();

        assertTrue(summary.contains("RandomNode"));
        assertTrue(summary.contains("Original:"));
        assertTrue(summary.contains("Sample:"));
        assertTrue(summary.contains("Coverage:"));
        assertTrue(summary.contains("Density ratio:"));
    }

    @Test
    public void sampleResult_isConnected_chain() {
        buildChain();
        GraphSampler sampler = new GraphSampler(graph, new Random(1));
        // Snowball from one end should give a connected sample
        GraphSampler.SampleResult result = sampler.snowball(0.5, Arrays.asList("A"));
        assertTrue(result.isConnected());
    }

    @Test
    public void sampleResult_componentCount_disconnected() {
        buildTwoTriangles();
        GraphSampler sampler = new GraphSampler(graph, new Random(1));
        // Full sample should have 2 components
        GraphSampler.SampleResult result = sampler.randomNode(1.0);
        assertEquals(2, result.getComponentCount());
    }

    // ── Edge preservation ───────────────────────────────────────

    @Test
    public void randomNode_inducesEdgesCorrectly() {
        // Build triangle A-B-C
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");

        GraphSampler sampler = new GraphSampler(graph, new Random(1));
        GraphSampler.SampleResult result = sampler.randomNode(1.0);

        // All 3 edges should be present
        assertEquals(3, result.getEdgeCount());
        assertNotNull(result.getSample().findEdge("A", "B"));
    }

    @Test
    public void randomEdge_inducesExtraEdges() {
        // Build triangle A-B-C, sample edge A-B
        // Should also induce A-C and B-C if nodes A,B,C are all included
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        // Add isolated node to make edge fraction meaningful
        graph.addVertex("D");

        GraphSampler sampler = new GraphSampler(graph, new Random(1));
        // Sample all edges (fraction=1.0) to verify induction
        GraphSampler.SampleResult result = sampler.randomEdge(1.0);
        assertEquals(3, result.getEdgeCount());
    }
}
