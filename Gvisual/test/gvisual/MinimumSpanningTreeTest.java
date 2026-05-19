package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for MinimumSpanningTree — Kruskal's algorithm with Union-Find,
 * MST computation, component analysis, and Edge type distribution.
 */
public class MinimumSpanningTreeTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, Edge>();
    }

    // ==========================================
    //  Constructor tests
    // ==========================================

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new MinimumSpanningTree(null);
    }

    @Test
    public void testConstructorAcceptsValidGraph() {
        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        assertNotNull(mst);
    }

    // ==========================================
    //  Empty / trivial graphs
    // ==========================================

    @Test
    public void testEmptyGraph() {
        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertEquals(0, result.getEdgeCount());
        assertEquals(0.0f, result.getTotalWeight(), 0.001f);
        assertEquals(0, result.getVertexCount());
        assertTrue(result.getEdges().isEmpty());
        assertTrue(result.getComponents().isEmpty());
    }

    @Test
    public void testSingleVertex() {
        graph.addVertex("A");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertEquals(0, result.getEdgeCount());
        assertEquals(1, result.getVertexCount());
        assertEquals(1, result.getComponentCount());
        assertTrue(result.getEdges().isEmpty());
        assertEquals(1, result.getComponents().size());
        assertEquals(1, result.getComponents().get(0).getSize());
    }

    @Test
    public void testTwoDisconnectedVertices() {
        graph.addVertex("A");
        graph.addVertex("B");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertEquals(0, result.getEdgeCount());
        assertEquals(2, result.getVertexCount());
        assertEquals(2, result.getComponentCount());
        assertFalse(result.isConnected());
    }

    @Test
    public void testSingleEdge() {
        Edge e1 = new Edge("f", "A", "B");
        e1.setWeight(5.0f);
        graph.addEdge(e1, "A", "B");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertEquals(1, result.getEdgeCount());
        assertEquals(5.0f, result.getTotalWeight(), 0.001f);
        assertEquals(2, result.getVertexCount());
        assertEquals(1, result.getComponentCount());
        assertTrue(result.isConnected());
        assertSame(e1, result.getEdges().get(0));
    }

    // ==========================================
    //  Basic MST computation
    // ==========================================

    @Test
    public void testTrianglePicksLightestTwoEdges() {
        // Triangle: A-B(1), B-C(2), A-C(3) → MST = A-B(1) + B-C(2) = weight 3
        Edge e1 = new Edge("f", "A", "B");
        e1.setWeight(1.0f);
        Edge e2 = new Edge("f", "B", "C");
        e2.setWeight(2.0f);
        Edge e3 = new Edge("f", "A", "C");
        e3.setWeight(3.0f);

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");
        graph.addEdge(e3, "A", "C");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertEquals(2, result.getEdgeCount());
        assertEquals(3.0f, result.getTotalWeight(), 0.001f);
        assertTrue(result.isConnected());
        // Must include the two lightest edges
        assertTrue(result.getEdges().contains(e1));
        assertTrue(result.getEdges().contains(e2));
        assertFalse(result.getEdges().contains(e3));
    }

    @Test
    public void testSquareGraphSelectsCorrectEdges() {
        // Square: A-B(1), B-C(4), C-D(2), A-D(3)
        // Also diagonal: A-C(5)
        // MST = A-B(1) + C-D(2) + A-D(3) = weight 6
        Edge e1 = new Edge("f", "A", "B"); e1.setWeight(1.0f);
        Edge e2 = new Edge("c", "B", "C"); e2.setWeight(4.0f);
        Edge e3 = new Edge("f", "C", "D"); e3.setWeight(2.0f);
        Edge e4 = new Edge("s", "A", "D"); e4.setWeight(3.0f);
        Edge e5 = new Edge("fs", "A", "C"); e5.setWeight(5.0f);

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");
        graph.addEdge(e3, "C", "D");
        graph.addEdge(e4, "A", "D");
        graph.addEdge(e5, "A", "C");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertEquals(3, result.getEdgeCount()); // N-1 = 4-1 = 3
        assertEquals(6.0f, result.getTotalWeight(), 0.001f);
        assertTrue(result.isConnected());
    }

    @Test
    public void testMstHasNMinus1Edges() {
        // Complete graph K5 — MST should have exactly 4 edges
        String[] nodes = {"A", "B", "C", "D", "E"};
        float w = 1.0f;
        for (int i = 0; i < nodes.length; i++) {
            for (int j = i + 1; j < nodes.length; j++) {
                Edge e = new Edge("f", nodes[i], nodes[j]);
                e.setWeight(w);
                graph.addEdge(e, nodes[i], nodes[j]);
                w += 1.0f;
            }
        }

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertEquals(4, result.getEdgeCount()); // 5-1 = 4
        assertTrue(result.isConnected());
    }

    // ==========================================
    //  Disconnected graphs (forest)
    // ==========================================

    @Test
    public void testDisconnectedGraphProducesForest() {
        // Component 1: A-B(1), B-C(2)
        Edge e1 = new Edge("f", "A", "B"); e1.setWeight(1.0f);
        Edge e2 = new Edge("f", "B", "C"); e2.setWeight(2.0f);
        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");

        // Component 2: D-E(3)
        Edge e3 = new Edge("c", "D", "E"); e3.setWeight(3.0f);
        graph.addEdge(e3, "D", "E");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertEquals(3, result.getEdgeCount());
        assertEquals(6.0f, result.getTotalWeight(), 0.001f);
        assertEquals(2, result.getComponentCount());
        assertFalse(result.isConnected());
    }

    @Test
    public void testForestComponentBreakdown() {
        // Component 1: A-B(1), B-C(2)
        Edge e1 = new Edge("f", "A", "B"); e1.setWeight(1.0f);
        Edge e2 = new Edge("f", "B", "C"); e2.setWeight(2.0f);
        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");

        // Component 2: D-E(5)
        Edge e3 = new Edge("c", "D", "E"); e3.setWeight(5.0f);
        graph.addEdge(e3, "D", "E");

        // Isolated vertex
        graph.addVertex("F");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertEquals(3, result.getComponentCount());
        assertEquals(6, result.getVertexCount());

        // Components sorted by size descending
        assertEquals(3, result.getComponents().get(0).getSize()); // A,B,C
        assertEquals(2, result.getComponents().get(1).getSize()); // D,E
        assertEquals(1, result.getComponents().get(2).getSize()); // F
    }

    @Test
    public void testIsolatedVerticesAsSingletonComponents() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertEquals(3, result.getComponentCount());
        assertEquals(0, result.getEdgeCount());
        for (MinimumSpanningTree.MSTComponent comp : result.getComponents()) {
            assertEquals(1, comp.getSize());
            assertEquals(0, comp.getEdges().size());
        }
    }

    // ==========================================
    //  Edge weight / ordering tests
    // ==========================================

    @Test
    public void testEqualWeightsProducesValidMst() {
        // All edges weight 1 — any spanning tree is MST
        Edge e1 = new Edge("f", "A", "B"); e1.setWeight(1.0f);
        Edge e2 = new Edge("f", "B", "C"); e2.setWeight(1.0f);
        Edge e3 = new Edge("f", "A", "C"); e3.setWeight(1.0f);

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");
        graph.addEdge(e3, "A", "C");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertEquals(2, result.getEdgeCount());
        assertEquals(2.0f, result.getTotalWeight(), 0.001f);
        assertTrue(result.isConnected());
    }

    @Test
    public void testZeroWeightEdges() {
        Edge e1 = new Edge("f", "A", "B"); e1.setWeight(0.0f);
        Edge e2 = new Edge("f", "B", "C"); e2.setWeight(0.0f);

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertEquals(2, result.getEdgeCount());
        assertEquals(0.0f, result.getTotalWeight(), 0.001f);
    }

    @Test
    public void testLargeWeights() {
        Edge e1 = new Edge("f", "A", "B"); e1.setWeight(1000000.0f);
        Edge e2 = new Edge("f", "B", "C"); e2.setWeight(999999.0f);

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertEquals(2, result.getEdgeCount());
        assertEquals(1999999.0f, result.getTotalWeight(), 1.0f);
    }

    // ==========================================
    //  Edge type distribution
    // ==========================================

    @Test
    public void testEdgeTypeDistribution() {
        Edge e1 = new Edge("f", "A", "B"); e1.setWeight(1.0f);
        Edge e2 = new Edge("c", "B", "C"); e2.setWeight(2.0f);
        Edge e3 = new Edge("f", "C", "D"); e3.setWeight(3.0f);
        Edge e4 = new Edge("s", "D", "E"); e4.setWeight(4.0f);

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");
        graph.addEdge(e3, "C", "D");
        graph.addEdge(e4, "D", "E");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        Map<String, Integer> dist = result.getEdgeTypeDistribution();
        assertEquals(Integer.valueOf(2), dist.get("f"));
        assertEquals(Integer.valueOf(1), dist.get("c"));
        assertEquals(Integer.valueOf(1), dist.get("s"));
    }

    @Test
    public void testEdgeTypeDistributionEmpty() {
        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertTrue(result.getEdgeTypeDistribution().isEmpty());
    }

    @Test
    public void testEdgeTypeDistributionAllSameType() {
        Edge e1 = new Edge("sg", "A", "B"); e1.setWeight(1.0f);
        Edge e2 = new Edge("sg", "B", "C"); e2.setWeight(2.0f);

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        Map<String, Integer> dist = result.getEdgeTypeDistribution();
        assertEquals(1, dist.size());
        assertEquals(Integer.valueOf(2), dist.get("sg"));
    }

    // ==========================================
    //  Stats methods
    // ==========================================

    @Test
    public void testHeaviestEdge() {
        Edge e1 = new Edge("f", "A", "B"); e1.setWeight(1.0f);
        Edge e2 = new Edge("f", "B", "C"); e2.setWeight(5.0f);
        Edge e3 = new Edge("f", "C", "D"); e3.setWeight(3.0f);

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");
        graph.addEdge(e3, "C", "D");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertNotNull(result.getHeaviestEdge());
        assertEquals(5.0f, result.getHeaviestEdge().getWeight(), 0.001f);
    }

    @Test
    public void testHeaviestEdgeEmpty() {
        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertNull(result.getHeaviestEdge());
    }

    @Test
    public void testLightestEdge() {
        Edge e1 = new Edge("f", "A", "B"); e1.setWeight(3.0f);
        Edge e2 = new Edge("f", "B", "C"); e2.setWeight(1.0f);
        Edge e3 = new Edge("f", "C", "D"); e3.setWeight(5.0f);

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");
        graph.addEdge(e3, "C", "D");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertNotNull(result.getLightestEdge());
        assertEquals(1.0f, result.getLightestEdge().getWeight(), 0.001f);
    }

    @Test
    public void testLightestEdgeEmpty() {
        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertNull(result.getLightestEdge());
    }

    @Test
    public void testAverageWeight() {
        Edge e1 = new Edge("f", "A", "B"); e1.setWeight(2.0f);
        Edge e2 = new Edge("f", "B", "C"); e2.setWeight(4.0f);
        Edge e3 = new Edge("f", "C", "D"); e3.setWeight(6.0f);

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");
        graph.addEdge(e3, "C", "D");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertEquals(4.0f, result.getAverageWeight(), 0.001f);
    }

    @Test
    public void testAverageWeightEmpty() {
        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertEquals(0.0f, result.getAverageWeight(), 0.001f);
    }

    @Test
    public void testGetSummaryConnected() {
        Edge e1 = new Edge("f", "A", "B"); e1.setWeight(1.0f);
        Edge e2 = new Edge("f", "B", "C"); e2.setWeight(2.0f);

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        String summary = result.getSummary();
        assertTrue(summary.contains("2 edges"));
        assertTrue(summary.contains("3.0"));
        assertTrue(summary.contains("connected"));
        assertFalse(summary.contains("forest"));
    }

    @Test
    public void testGetSummaryForest() {
        Edge e1 = new Edge("f", "A", "B"); e1.setWeight(1.0f);
        graph.addEdge(e1, "A", "B");
        graph.addVertex("C");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        String summary = result.getSummary();
        assertTrue(summary.contains("forest"));
        assertTrue(summary.contains("2 components"));
    }

    // ==========================================
    //  Component analysis
    // ==========================================

    @Test
    public void testComponentWeights() {
        // Comp1: A-B(1) B-C(2) → weight 3
        Edge e1 = new Edge("f", "A", "B"); e1.setWeight(1.0f);
        Edge e2 = new Edge("f", "B", "C"); e2.setWeight(2.0f);
        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");

        // Comp2: D-E(7)
        Edge e3 = new Edge("c", "D", "E"); e3.setWeight(7.0f);
        graph.addEdge(e3, "D", "E");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertEquals(2, result.getComponents().size());
        MinimumSpanningTree.MSTComponent comp1 = result.getComponents().get(0); // larger
        MinimumSpanningTree.MSTComponent comp2 = result.getComponents().get(1);

        assertEquals(3, comp1.getSize());
        assertEquals(3.0f, comp1.getTotalWeight(), 0.001f);
        assertEquals(2, comp1.getEdges().size());

        assertEquals(2, comp2.getSize());
        assertEquals(7.0f, comp2.getTotalWeight(), 0.001f);
        assertEquals(1, comp2.getEdges().size());
    }

    @Test
    public void testComponentDominantType() {
        Edge e1 = new Edge("f", "A", "B"); e1.setWeight(1.0f);
        Edge e2 = new Edge("f", "B", "C"); e2.setWeight(2.0f);
        Edge e3 = new Edge("c", "C", "D"); e3.setWeight(3.0f);

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");
        graph.addEdge(e3, "C", "D");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertEquals(1, result.getComponentCount());
        assertEquals("f", result.getComponents().get(0).getDominantType());
    }

    @Test
    public void testComponentDominantTypeNull() {
        graph.addVertex("A");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertNull(result.getComponents().get(0).getDominantType());
    }

    @Test
    public void testComponentIds() {
        Edge e1 = new Edge("f", "A", "B"); e1.setWeight(1.0f);
        graph.addEdge(e1, "A", "B");
        graph.addVertex("C");
        graph.addVertex("D");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        Set<Integer> ids = new HashSet<Integer>();
        for (MinimumSpanningTree.MSTComponent comp : result.getComponents()) {
            ids.add(comp.getId());
        }
        assertEquals(3, ids.size());
        assertTrue(ids.contains(0));
        assertTrue(ids.contains(1));
        assertTrue(ids.contains(2));
    }

    // ==========================================
    //  Larger / complex graphs
    // ==========================================

    @Test
    public void testLargerGraph() {
        // Classic example: 6-node graph
        //   A--1--B--6--C
        //   |    /|    /
        //   4   3 5   2
        //   |  /  |  /
        //   D--7--E--8--F
        Edge[] edges = {
            makeEdge("f", "A", "B", 1), makeEdge("c", "A", "D", 4),
            makeEdge("f", "B", "D", 3), makeEdge("s", "B", "E", 5),
            makeEdge("f", "B", "C", 6), makeEdge("c", "C", "E", 2),
            makeEdge("sg", "D", "E", 7), makeEdge("f", "E", "F", 8)
        };
        for (Edge e : edges) {
            graph.addEdge(e, e.getVertex1(), e.getVertex2());
        }

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertEquals(5, result.getEdgeCount()); // 6-1 = 5
        assertTrue(result.isConnected());
        // MST edges should be: A-B(1), C-E(2), B-D(3), B-E(5), E-F(8) = 19
        assertEquals(19.0f, result.getTotalWeight(), 0.001f);
    }

    @Test
    public void testStarGraph() {
        // Hub (A) connected to 5 leaves
        for (int i = 0; i < 5; i++) {
            Edge e = new Edge("f", "A", "L" + i);
            e.setWeight(i + 1.0f);
            graph.addEdge(e, "A", "L" + i);
        }

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertEquals(5, result.getEdgeCount());
        assertEquals(15.0f, result.getTotalWeight(), 0.001f); // 1+2+3+4+5
        assertTrue(result.isConnected());
    }

    @Test
    public void testLinearChain() {
        // A-B-C-D-E (chain)
        for (int i = 0; i < 4; i++) {
            Edge e = new Edge("f", "N" + i, "N" + (i + 1));
            e.setWeight((i + 1) * 2.0f);
            graph.addEdge(e, "N" + i, "N" + (i + 1));
        }

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertEquals(4, result.getEdgeCount());
        assertEquals(20.0f, result.getTotalWeight(), 0.001f); // 2+4+6+8
        assertTrue(result.isConnected());
    }

    @Test
    public void testCompleteGraphK4() {
        // K4 with distinct weights
        String[] nodes = {"A", "B", "C", "D"};
        float[][] weights = {
            {0, 1, 3, 5},
            {1, 0, 4, 2},
            {3, 4, 0, 6},
            {5, 2, 6, 0}
        };
        for (int i = 0; i < 4; i++) {
            for (int j = i + 1; j < 4; j++) {
                Edge e = new Edge("f", nodes[i], nodes[j]);
                e.setWeight(weights[i][j]);
                graph.addEdge(e, nodes[i], nodes[j]);
            }
        }

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertEquals(3, result.getEdgeCount()); // 4-1
        assertTrue(result.isConnected());
        // MST: A-B(1) + B-D(2) + A-C(3) = 6
        assertEquals(6.0f, result.getTotalWeight(), 0.001f);
    }

    // ==========================================
    //  Determinism / idempotency
    // ==========================================

    @Test
    public void testMultipleComputesSameResult() {
        Edge e1 = new Edge("f", "A", "B"); e1.setWeight(1.0f);
        Edge e2 = new Edge("f", "B", "C"); e2.setWeight(2.0f);
        Edge e3 = new Edge("f", "A", "C"); e3.setWeight(3.0f);

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");
        graph.addEdge(e3, "A", "C");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);

        MinimumSpanningTree.MSTResult r1 = mst.compute();
        MinimumSpanningTree.MSTResult r2 = mst.compute();

        assertEquals(r1.getEdgeCount(), r2.getEdgeCount());
        assertEquals(r1.getTotalWeight(), r2.getTotalWeight(), 0.001f);
        assertEquals(r1.getComponentCount(), r2.getComponentCount());
    }

    @Test
    public void testResultIsUnmodifiable() {
        Edge e1 = new Edge("f", "A", "B"); e1.setWeight(1.0f);
        graph.addEdge(e1, "A", "B");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        try {
            result.getEdges().add(new Edge("f", "X", "Y"));
            fail("Should not be able to modify edges list");
        } catch (UnsupportedOperationException expected) {
            // pass
        }
    }

    @Test
    public void testComponentsUnmodifiable() {
        Edge e1 = new Edge("f", "A", "B"); e1.setWeight(1.0f);
        graph.addEdge(e1, "A", "B");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        try {
            result.getComponents().clear();
            fail("Should not be able to modify components list");
        } catch (UnsupportedOperationException expected) {
            // pass
        }
    }

    // ==========================================
    //  Mixed Edge types
    // ==========================================

    @Test
    public void testMixedEdgeTypes() {
        Edge e1 = new Edge("f", "A", "B"); e1.setWeight(1.0f);
        Edge e2 = new Edge("c", "B", "C"); e2.setWeight(2.0f);
        Edge e3 = new Edge("s", "C", "D"); e3.setWeight(3.0f);
        Edge e4 = new Edge("fs", "D", "E"); e4.setWeight(4.0f);
        Edge e5 = new Edge("sg", "A", "E"); e5.setWeight(10.0f);

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");
        graph.addEdge(e3, "C", "D");
        graph.addEdge(e4, "D", "E");
        graph.addEdge(e5, "A", "E");

        MinimumSpanningTree mst = new MinimumSpanningTree(graph);
        MinimumSpanningTree.MSTResult result = mst.compute();

        assertEquals(4, result.getEdgeCount());
        assertEquals(10.0f, result.getTotalWeight(), 0.001f); // 1+2+3+4
        // sg Edge (weight 10) should NOT be in MST
        assertFalse(result.getEdges().contains(e5));

        Map<String, Integer> dist = result.getEdgeTypeDistribution();
        assertEquals(4, dist.size()); // f, c, s, fs
    }

    // ==========================================
    //  Union-Find specific tests
    // ==========================================

    // ------------------------------------------------------------
    //  UnionFind switched from String-keyed maps to int[] arrays in
    //  v2.63.0 for ~3x faster Kruskal merge on large graphs. The
    //  tests below were updated accordingly: vertices A,B,C,D,E are
    //  mapped to ids 0,1,2,3,4.
    // ------------------------------------------------------------

    @Test
    public void testUnionFindBasic() {
        // 4 elements: A=0, B=1, C=2, D=3
        MinimumSpanningTree.UnionFind uf = new MinimumSpanningTree.UnionFind(4);

        // Initially all separate
        assertNotEquals(uf.find(0), uf.find(1));
        assertNotEquals(uf.find(2), uf.find(3));

        assertTrue("A and B were distinct", uf.union(0, 1));
        assertEquals(uf.find(0), uf.find(1));
        assertNotEquals(uf.find(0), uf.find(2));

        assertTrue("C and D were distinct", uf.union(2, 3));
        assertEquals(uf.find(2), uf.find(3));

        assertTrue("AB-cluster and CD-cluster were distinct", uf.union(0, 2));
        assertEquals(uf.find(0), uf.find(3));

        // Re-unioning members of the same component is a no-op and
        // must return false so callers (Kruskal) can skip them.
        assertFalse("already-merged union must report no-op", uf.union(1, 3));
    }

    @Test
    public void testUnionFindPathCompression() {
        // 5 elements: A=0..E=4
        MinimumSpanningTree.UnionFind uf = new MinimumSpanningTree.UnionFind(5);

        uf.union(0, 1);
        uf.union(1, 2);
        uf.union(2, 3);
        uf.union(3, 4);

        // All should share same root after path compression
        int root = uf.find(4);
        assertEquals(root, uf.find(0));
        assertEquals(root, uf.find(1));
        assertEquals(root, uf.find(2));
        assertEquals(root, uf.find(3));
    }

    @Test
    public void testUnionFindSelfUnion() {
        // 2 elements: A=0, B=1
        MinimumSpanningTree.UnionFind uf = new MinimumSpanningTree.UnionFind(2);

        // Self-union is a no-op; must report false (not a merge).
        assertFalse("self-union is not a merge", uf.union(0, 0));
        assertEquals(uf.find(0), uf.find(0));
        assertNotEquals(uf.find(0), uf.find(1));
    }

    // ==========================================
    //  Helper
    // ==========================================

    private Edge makeEdge(String type, String v1, String v2, float weight) {
        Edge e = new Edge(type, v1, v2);
        e.setWeight(weight);
        return e;
    }
}
