package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for GraphPartitioner — BFS, Kernighan-Lin, and spectral
 * partitioning strategies with balance, edge cut, and conductance metrics.
 */
public class GraphPartitionerTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
    }

    // ---- Helpers ----

    private void addEdge(String u, String v) {
        edge e = new Edge("f", u, v);
        graph.addEdge(e, u, v);
    }

    private void buildTriangle() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
    }

    private void buildPath(int n) {
        for (int i = 0; i < n; i++) {
            graph.addVertex("N" + i);
        }
        for (int i = 0; i < n - 1; i++) {
            addEdge("N" + i, "N" + (i + 1));
        }
    }

    private void buildTwoCliques(int size1, int size2, int bridges) {
        for (int i = 0; i < size1; i++) {
            graph.addVertex("A" + i);
            for (int j = i + 1; j < size1; j++) {
                addEdge("A" + i, "A" + j);
            }
        }
        for (int i = 0; i < size2; i++) {
            graph.addVertex("B" + i);
            for (int j = i + 1; j < size2; j++) {
                addEdge("B" + i, "B" + j);
            }
        }
        for (int i = 0; i < bridges; i++) {
            addEdge("A" + (i % size1), "B" + (i % size2));
        }
    }

    private void assertValidPartition(GraphPartitioner.PartitionResult result, int k) {
        assertEquals(k, result.getK());
        // Every vertex assigned to a valid partition
        for (Map.Entry<String, Integer> e : result.getAssignment().entrySet()) {
            assertTrue("Partition ID out of range: " + e.getValue(),
                e.getValue() >= 0 && e.getValue() < k);
        }
        // All vertices assigned
        assertEquals(graph.getVertexCount(), result.getAssignment().size());
        // Edge cuts non-negative
        assertTrue(result.getEdgeCuts() >= 0);
        // Imbalance >= 1.0
        assertTrue(result.getImbalanceRatio() >= 1.0 - 1e-9);
        // Cut ratio in [0, 1]
        assertTrue(result.getCutRatio() >= 0.0);
        assertTrue(result.getCutRatio() <= 1.0);
    }

    // =========================================================================
    // Constructor
    // =========================================================================

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new GraphPartitioner(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testZeroKThrows() {
        graph.addVertex("A");
        new GraphPartitioner(graph).partition(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testKExceedsVertexCount() {
        graph.addVertex("A");
        new GraphPartitioner(graph).partition(2);
    }

    // =========================================================================
    // Empty and trivial graphs
    // =========================================================================

    @Test
    public void testEmptyGraph() {
        GraphPartitioner p = new GraphPartitioner(graph);
        GraphPartitioner.PartitionResult result = p.partition(1);
        assertEquals(0, result.getEdgeCuts());
        assertEquals(1, result.getK());
        assertTrue(result.getAssignment().isEmpty());
    }

    @Test
    public void testSingleVertex() {
        graph.addVertex("A");
        GraphPartitioner p = new GraphPartitioner(graph);
        GraphPartitioner.PartitionResult result = p.partition(1);
        assertEquals(0, result.getPartition("A"));
        assertEquals(0, result.getEdgeCuts());
    }

    @Test
    public void testKEqualsOne() {
        addEdge("A", "B");
        addEdge("B", "C");
        GraphPartitioner.PartitionResult result = new GraphPartitioner(graph).partition(1);
        assertEquals(0, result.getEdgeCuts());
        for (String v : graph.getVertices()) {
            assertEquals(0, result.getPartition(v));
        }
    }

    @Test
    public void testKEqualsN() {
        addEdge("A", "B");
        addEdge("B", "C");
        GraphPartitioner.PartitionResult result = new GraphPartitioner(graph).partition(3);
        assertValidPartition(result, 3);
        // Each vertex in its own partition
        Set<Integer> partitions = new HashSet<>(result.getAssignment().values());
        assertEquals(3, partitions.size());
    }

    // =========================================================================
    // BFS strategy
    // =========================================================================

    @Test
    public void testBFSTriangleTwoParts() {
        buildTriangle();
        GraphPartitioner.PartitionResult result =
            new GraphPartitioner(graph).partition(2, GraphPartitioner.Strategy.BFS);
        assertValidPartition(result, 2);
        // At least 1 edge must be cut (triangle can't be split without cutting)
        assertTrue(result.getEdgeCuts() >= 1);
    }

    @Test
    public void testBFSPathEvenSplit() {
        buildPath(6);
        GraphPartitioner.PartitionResult result =
            new GraphPartitioner(graph).partition(2, GraphPartitioner.Strategy.BFS);
        assertValidPartition(result, 2);
        // Perfect balance should be achievable for a 6-node path split into 2
        assertTrue(result.getImbalanceRatio() <= 1.5);
    }

    @Test
    public void testBFSTwoCliques() {
        buildTwoCliques(5, 5, 1);
        GraphPartitioner.PartitionResult result =
            new GraphPartitioner(graph).partition(2, GraphPartitioner.Strategy.BFS);
        assertValidPartition(result, 2);
    }

    @Test
    public void testBFSThreePartitions() {
        buildPath(9);
        GraphPartitioner.PartitionResult result =
            new GraphPartitioner(graph).partition(3, GraphPartitioner.Strategy.BFS);
        assertValidPartition(result, 3);
        // Each partition should have 3 nodes (balanced)
        for (GraphPartitioner.PartitionInfo pi : result.getPartitions()) {
            assertTrue("Partition " + pi.getId() + " size " + pi.getSize(),
                pi.getSize() >= 2 && pi.getSize() <= 4);
        }
    }

    // =========================================================================
    // Kernighan-Lin strategy
    // =========================================================================

    @Test
    public void testKLBasicPartition() {
        buildTwoCliques(4, 4, 1);
        GraphPartitioner.PartitionResult result =
            new GraphPartitioner(graph).partition(2, GraphPartitioner.Strategy.KERNIGHAN_LIN);
        assertValidPartition(result, 2);
    }

    @Test
    public void testKLImprovesBFS() {
        // Two cliques connected by a single bridge — KL should find the natural cut
        buildTwoCliques(5, 5, 1);
        GraphPartitioner p = new GraphPartitioner(graph);
        GraphPartitioner.PartitionResult bfs = p.partition(2, GraphPartitioner.Strategy.BFS);
        GraphPartitioner.PartitionResult kl = p.partition(2, GraphPartitioner.Strategy.KERNIGHAN_LIN);
        // KL should have same or fewer edge cuts than BFS
        assertTrue("KL cuts " + kl.getEdgeCuts() + " >= BFS cuts " + bfs.getEdgeCuts(),
            kl.getEdgeCuts() <= bfs.getEdgeCuts());
    }

    @Test
    public void testKLThreeWay() {
        buildPath(12);
        GraphPartitioner.PartitionResult result =
            new GraphPartitioner(graph).partition(3, GraphPartitioner.Strategy.KERNIGHAN_LIN);
        assertValidPartition(result, 3);
    }

    // =========================================================================
    // Spectral strategy
    // =========================================================================

    @Test
    public void testSpectralBasicPartition() {
        buildTwoCliques(4, 4, 1);
        GraphPartitioner.PartitionResult result =
            new GraphPartitioner(graph).partition(2, GraphPartitioner.Strategy.SPECTRAL);
        assertValidPartition(result, 2);
    }

    @Test
    public void testSpectralFindsNaturalCut() {
        // Two cliques with single bridge — spectral should find the natural 2-way cut
        buildTwoCliques(5, 5, 1);
        GraphPartitioner.PartitionResult result =
            new GraphPartitioner(graph).partition(2, GraphPartitioner.Strategy.SPECTRAL);
        assertValidPartition(result, 2);
        // Should cut at most the bridge (and maybe a few more due to balance)
        assertTrue("Spectral cuts = " + result.getEdgeCuts(),
            result.getEdgeCuts() <= 5);
    }

    @Test
    public void testSpectralThreeWay() {
        buildPath(9);
        GraphPartitioner.PartitionResult result =
            new GraphPartitioner(graph).partition(3, GraphPartitioner.Strategy.SPECTRAL);
        assertValidPartition(result, 3);
    }

    // =========================================================================
    // PartitionResult metrics
    // =========================================================================

    @Test
    public void testEdgeCutsAccuracy() {
        // Path A-B-C-D split into {A,B} and {C,D} should have exactly 1 cut
        buildPath(4);
        GraphPartitioner.PartitionResult result = new GraphPartitioner(graph).partition(2);
        // Can't assert exact cuts since BFS is heuristic, but should be >=1
        assertTrue(result.getEdgeCuts() >= 1);
    }

    @Test
    public void testImbalancePerfectSplit() {
        buildPath(4);
        // With 4 nodes and k=2, perfect balance = 1.0
        GraphPartitioner.PartitionResult result = new GraphPartitioner(graph).partition(2);
        assertTrue("Imbalance " + result.getImbalanceRatio(), result.getImbalanceRatio() <= 1.5);
    }

    @Test
    public void testCutRatioRange() {
        buildTriangle();
        GraphPartitioner.PartitionResult result = new GraphPartitioner(graph).partition(2);
        assertTrue(result.getCutRatio() >= 0.0 && result.getCutRatio() <= 1.0);
    }

    @Test
    public void testPartitionInfoList() {
        buildPath(6);
        GraphPartitioner.PartitionResult result = new GraphPartitioner(graph).partition(3);
        assertEquals(3, result.getPartitions().size());
        int totalMembers = 0;
        for (GraphPartitioner.PartitionInfo pi : result.getPartitions()) {
            totalMembers += pi.getSize();
            assertTrue(pi.getConductance() >= 0.0 && pi.getConductance() <= 1.0);
        }
        assertEquals(6, totalMembers);
    }

    @Test
    public void testPartitionInfoById() {
        buildPath(4);
        GraphPartitioner.PartitionResult result = new GraphPartitioner(graph).partition(2);
        GraphPartitioner.PartitionInfo p0 = result.getPartitionInfo(0);
        assertNotNull(p0);
        assertEquals(0, p0.getId());
        assertTrue(p0.getSize() > 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPartitionInfoInvalidId() {
        buildPath(4);
        GraphPartitioner.PartitionResult result = new GraphPartitioner(graph).partition(2);
        result.getPartitionInfo(5);
    }

    @Test
    public void testGetPartitionVertex() {
        buildTriangle();
        GraphPartitioner.PartitionResult result = new GraphPartitioner(graph).partition(2);
        // Should not throw
        int p = result.getPartition("A");
        assertTrue(p >= 0 && p < 2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetPartitionUnknownVertex() {
        buildTriangle();
        GraphPartitioner.PartitionResult result = new GraphPartitioner(graph).partition(2);
        result.getPartition("Z");
    }

    @Test
    public void testToString() {
        buildPath(4);
        GraphPartitioner.PartitionResult result = new GraphPartitioner(graph).partition(2);
        String str = result.toString();
        assertTrue(str.contains("GraphPartition"));
        assertTrue(str.contains("edgeCuts="));
        assertTrue(str.contains("Partition 0"));
    }

    // =========================================================================
    // Disconnected graphs
    // =========================================================================

    @Test
    public void testDisconnectedGraph() {
        // Two disconnected components
        addEdge("A", "B");
        addEdge("C", "D");
        GraphPartitioner.PartitionResult result = new GraphPartitioner(graph).partition(2);
        assertValidPartition(result, 2);
    }

    @Test
    public void testDisconnectedThreeComponents() {
        graph.addVertex("X");
        addEdge("A", "B");
        addEdge("C", "D");
        GraphPartitioner.PartitionResult result = new GraphPartitioner(graph).partition(3);
        assertValidPartition(result, 3);
    }

    // =========================================================================
    // Larger graphs
    // =========================================================================

    @Test
    public void testLargerPath() {
        buildPath(20);
        for (GraphPartitioner.Strategy s : GraphPartitioner.Strategy.values()) {
            GraphPartitioner.PartitionResult result =
                new GraphPartitioner(graph).partition(4, s);
            assertValidPartition(result, 4);
            // Should be reasonably balanced
            assertTrue("Strategy " + s + " imbalance " + result.getImbalanceRatio(),
                result.getImbalanceRatio() <= 2.0);
        }
    }

    @Test
    public void testLargerTwoCliques() {
        buildTwoCliques(8, 8, 2);
        for (GraphPartitioner.Strategy s : GraphPartitioner.Strategy.values()) {
            GraphPartitioner.PartitionResult result =
                new GraphPartitioner(graph).partition(2, s);
            assertValidPartition(result, 2);
        }
    }

    @Test
    public void testFivePartitions() {
        buildPath(25);
        GraphPartitioner.PartitionResult result = new GraphPartitioner(graph).partition(5);
        assertValidPartition(result, 5);
        assertEquals(5, result.getPartitions().size());
    }

    // =========================================================================
    // Default strategy
    // =========================================================================

    @Test
    public void testDefaultStrategyIsBFS() {
        buildPath(6);
        GraphPartitioner p = new GraphPartitioner(graph);
        GraphPartitioner.PartitionResult r1 = p.partition(2);
        GraphPartitioner.PartitionResult r2 = p.partition(2, GraphPartitioner.Strategy.BFS);
        // Same edge cuts (deterministic)
        assertEquals(r1.getEdgeCuts(), r2.getEdgeCuts());
    }

    // =========================================================================
    // Conductance
    // =========================================================================

    @Test
    public void testConductanceSinglePartition() {
        buildTriangle();
        GraphPartitioner.PartitionResult result = new GraphPartitioner(graph).partition(1);
        // Single partition: no external edges → conductance = 0
        assertEquals(0.0, result.getPartitions().get(0).getConductance(), 1e-9);
    }

    @Test
    public void testConductanceAllCut() {
        // Star graph: center + leaves. Split center from leaves.
        graph.addVertex("center");
        for (int i = 0; i < 4; i++) {
            addEdge("center", "leaf" + i);
        }
        GraphPartitioner.PartitionResult result = new GraphPartitioner(graph).partition(2);
        // At least one partition should have non-zero conductance
        boolean anyNonZero = false;
        for (GraphPartitioner.PartitionInfo pi : result.getPartitions()) {
            if (pi.getConductance() > 0) anyNonZero = true;
        }
        assertTrue(anyNonZero);
    }

    // =========================================================================
    // Members list
    // =========================================================================

    @Test
    public void testMembersListComplete() {
        buildPath(6);
        GraphPartitioner.PartitionResult result = new GraphPartitioner(graph).partition(2);
        Set<String> allMembers = new HashSet<>();
        for (GraphPartitioner.PartitionInfo pi : result.getPartitions()) {
            allMembers.addAll(pi.getMembers());
        }
        assertEquals(new HashSet<>(graph.getVertices()), allMembers);
    }

    @Test
    public void testMembersListNoOverlap() {
        buildPath(6);
        GraphPartitioner.PartitionResult result = new GraphPartitioner(graph).partition(3);
        Set<String> seen = new HashSet<>();
        for (GraphPartitioner.PartitionInfo pi : result.getPartitions()) {
            for (String m : pi.getMembers()) {
                assertTrue("Duplicate member: " + m, seen.add(m));
            }
        }
    }
}
