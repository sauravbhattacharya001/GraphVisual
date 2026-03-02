package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link SpectralAnalyzer}.
 */
public class SpectralAnalyzerTest {

    private Graph<String, edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, edge>();
    }

    // --- Helpers ---

    private edge addEdge(String v1, String v2) {
        edge e = new edge("f", v1, v2);
        e.setWeight(1.0f);
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        graph.addEdge(e, v1, v2);
        return e;
    }

    private Graph<String, edge> completeGraph(int n) {
        Graph<String, edge> g = new UndirectedSparseGraph<String, edge>();
        for (int i = 1; i <= n; i++) g.addVertex("N" + i);
        int edgeId = 0;
        for (int i = 1; i <= n; i++) {
            for (int j = i + 1; j <= n; j++) {
                edge e = new edge("f", "N" + i, "N" + j);
                e.setWeight(1.0f);
                g.addEdge(e, "N" + i, "N" + j);
                edgeId++;
            }
        }
        return g;
    }

    private Graph<String, edge> pathGraph(int n) {
        Graph<String, edge> g = new UndirectedSparseGraph<String, edge>();
        for (int i = 1; i <= n; i++) g.addVertex("N" + i);
        for (int i = 1; i < n; i++) {
            edge e = new edge("f", "N" + i, "N" + (i + 1));
            e.setWeight(1.0f);
            g.addEdge(e, "N" + i, "N" + (i + 1));
        }
        return g;
    }

    private Graph<String, edge> cycleGraph(int n) {
        Graph<String, edge> g = pathGraph(n);
        edge e = new edge("f", "N" + n, "N1");
        e.setWeight(1.0f);
        g.addEdge(e, "N" + n, "N1");
        return g;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Null / empty / trivial
    // ═══════════════════════════════════════════════════════════════

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new SpectralAnalyzer(null);
    }

    @Test
    public void testEmptyGraph() {
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();
        assertEquals(0, sa.getAdjacencyEigenvalues().length);
        assertEquals(0, sa.getLaplacianEigenvalues().length);
        assertEquals(0.0, sa.getSpectralRadius(), 1e-6);
        assertEquals(0.0, sa.getAlgebraicConnectivity(), 1e-6);
        assertEquals(0.0, sa.getEnergy(), 1e-6);
        assertEquals("Empty", sa.getClassification());
        assertFalse(sa.isConnectedSpectrally());
    }

    @Test
    public void testSingleVertex() {
        graph.addVertex("A");
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();
        assertEquals(1, sa.getAdjacencyEigenvalues().length);
        assertEquals(0.0, sa.getAdjacencyEigenvalues()[0], 1e-6);
        assertEquals(0.0, sa.getSpectralRadius(), 1e-6);
        assertEquals("Trivial", sa.getClassification());
    }

    // ═══════════════════════════════════════════════════════════════
    //  Compute-before-access guard
    // ═══════════════════════════════════════════════════════════════

    @Test(expected = IllegalStateException.class)
    public void testAccessBeforeComputeThrows() {
        addEdge("A", "B");
        new SpectralAnalyzer(graph).getSpectralRadius();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Idempotency
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testIdempotent() {
        addEdge("A", "B");
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();
        double r1 = sa.getSpectralRadius();
        sa.compute(); // second call
        assertEquals(r1, sa.getSpectralRadius(), 1e-10);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Single edge (K2)
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testSingleEdge() {
        addEdge("A", "B");
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();

        // K2 adjacency eigenvalues: {1, -1}
        double[] adjEv = sa.getAdjacencyEigenvalues();
        assertEquals(2, adjEv.length);
        assertEquals(1.0, adjEv[0], 1e-6);
        assertEquals(-1.0, adjEv[1], 1e-6);

        // Spectral radius = 1
        assertEquals(1.0, sa.getSpectralRadius(), 1e-6);

        // Spectral gap = 1 - (-1) = 2
        assertEquals(2.0, sa.getSpectralGap(), 1e-6);

        // Laplacian eigenvalues: {0, 2}
        double[] lapEv = sa.getLaplacianEigenvalues();
        assertEquals(0.0, lapEv[0], 1e-6);
        assertEquals(2.0, lapEv[1], 1e-6);

        // Connected
        assertTrue(sa.isConnectedSpectrally());
        assertEquals(2.0, sa.getAlgebraicConnectivity(), 1e-6);

        // Bipartite
        assertTrue(sa.isBipartiteLikely());

        // Energy = |1| + |-1| = 2
        assertEquals(2.0, sa.getEnergy(), 1e-6);

        // Spanning trees of K2 = 1
        assertEquals(1.0, sa.getSpanningTreeCount(), 1e-6);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Complete graphs
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testCompleteK3() {
        graph = completeGraph(3);
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();

        // K3 adjacency eigenvalues: {2, -1, -1}
        assertEquals(2.0, sa.getSpectralRadius(), 1e-4);
        assertEquals(3.0, sa.getSpectralGap(), 1e-4); // 2 - (-1) = 3

        // K3 Laplacian eigenvalues: {0, 3, 3}
        assertEquals(3.0, sa.getAlgebraicConnectivity(), 1e-4);

        assertTrue(sa.isConnectedSpectrally());

        // Spanning trees of K3 = 3
        assertEquals(3.0, sa.getSpanningTreeCount(), 1.0);
    }

    @Test
    public void testCompleteK4() {
        graph = completeGraph(4);
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();

        // K4: spectral radius = 3
        assertEquals(3.0, sa.getSpectralRadius(), 1e-4);

        // K4: algebraic connectivity = 4
        assertEquals(4.0, sa.getAlgebraicConnectivity(), 1e-4);

        assertTrue(sa.isConnectedSpectrally());

        // K4: 16 spanning trees
        assertEquals(16.0, sa.getSpanningTreeCount(), 1.0);
    }

    @Test
    public void testCompleteK5() {
        graph = completeGraph(5);
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();

        // K5: spectral radius = 4
        assertEquals(4.0, sa.getSpectralRadius(), 1e-4);

        // Kn spanning trees = n^(n-2)  => 5^3 = 125
        assertEquals(125.0, sa.getSpanningTreeCount(), 2.0);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Path graphs
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testPathP3() {
        graph = pathGraph(3);
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();

        // P3 adjacency eigenvalues: {√2, 0, -√2}
        assertEquals(Math.sqrt(2), sa.getSpectralRadius(), 1e-4);

        // P3 is bipartite (it's a tree)
        assertTrue(sa.isBipartiteLikely());

        // Connected
        assertTrue(sa.isConnectedSpectrally());

        // Algebraic connectivity > 0
        assertTrue(sa.getAlgebraicConnectivity() > 0);
    }

    @Test
    public void testPathP5Connected() {
        graph = pathGraph(5);
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();
        assertTrue(sa.isConnectedSpectrally());
        assertTrue(sa.isBipartiteLikely());

        // P5 has 1 spanning tree (it IS a tree)
        assertEquals(1.0, sa.getSpanningTreeCount(), 1e-4);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Cycle graphs
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testCycleC4() {
        graph = cycleGraph(4);
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();

        // C4: spectral radius = 2 (regular with degree 2)
        assertEquals(2.0, sa.getSpectralRadius(), 1e-4);

        // C4 is bipartite
        assertTrue(sa.isBipartiteLikely());

        assertTrue(sa.isConnectedSpectrally());

        // C4: 4 spanning trees
        assertEquals(4.0, sa.getSpanningTreeCount(), 1.0);
    }

    @Test
    public void testCycleC5() {
        graph = cycleGraph(5);
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();

        // C5 (odd cycle) is NOT bipartite
        assertFalse(sa.isBipartiteLikely());

        // C5: 5 spanning trees
        assertEquals(5.0, sa.getSpanningTreeCount(), 1.0);
    }

    @Test
    public void testCycleC6() {
        graph = cycleGraph(6);
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();

        // C6 (even) IS bipartite
        assertTrue(sa.isBipartiteLikely());

        // C6: 6 spanning trees
        assertEquals(6.0, sa.getSpanningTreeCount(), 1.0);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Disconnected graph
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testDisconnectedGraph() {
        addEdge("A", "B");
        graph.addVertex("C");  // isolated vertex

        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();

        // Not connected — algebraic connectivity = 0
        assertFalse(sa.isConnectedSpectrally());
        assertEquals(0.0, sa.getAlgebraicConnectivity(), 1e-6);

        // Two zero Laplacian eigenvalues (2 components)
        double[] lapEv = sa.getLaplacianEigenvalues();
        assertEquals(0.0, lapEv[0], 1e-6);
        assertEquals(0.0, lapEv[1], 1e-6);

        // No spanning trees for disconnected graph
        assertEquals(0.0, sa.getSpanningTreeCount(), 1e-6);

        assertTrue(sa.getClassification().contains("Disconnected"));
    }

    @Test
    public void testTwoDisconnectedEdges() {
        addEdge("A", "B");
        addEdge("C", "D");

        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();
        assertFalse(sa.isConnectedSpectrally());

        // Two components → 2 zero eigenvalues
        double[] lapEv = sa.getLaplacianEigenvalues();
        int zeros = 0;
        for (double v : lapEv) if (Math.abs(v) < 1e-6) zeros++;
        assertEquals(2, zeros);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Star graph
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testStarS4() {
        // Star with center "C" and 4 leaves
        addEdge("C", "L1");
        addEdge("C", "L2");
        addEdge("C", "L3");
        addEdge("C", "L4");

        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();

        // Star S_n has spectral radius = √n
        assertEquals(2.0, sa.getSpectralRadius(), 1e-4);

        assertTrue(sa.isConnectedSpectrally());
        assertTrue(sa.isBipartiteLikely());  // star is bipartite

        // Energy of star S_n = 2√n
        assertEquals(4.0, sa.getEnergy(), 1e-4);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Spectral partitioning
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testPartitionCoversAllVertices() {
        graph = completeGraph(6);
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();

        List<String> a = sa.getPartitionA();
        List<String> b = sa.getPartitionB();

        // All vertices accounted for
        assertEquals(6, a.size() + b.size());

        // No overlap
        for (String v : a) assertFalse(b.contains(v));
    }

    @Test
    public void testPartitionOfBarbell() {
        // Two triangles connected by a bridge
        addEdge("A1", "A2");
        addEdge("A2", "A3");
        addEdge("A1", "A3");
        addEdge("B1", "B2");
        addEdge("B2", "B3");
        addEdge("B1", "B3");
        addEdge("A3", "B1"); // bridge

        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();

        assertTrue(sa.isConnectedSpectrally());
        // Algebraic connectivity should be relatively small (bridge)
        assertTrue(sa.getAlgebraicConnectivity() < 2.0);

        // Both partitions should be non-empty for a barbell
        List<String> a = sa.getPartitionA();
        List<String> b = sa.getPartitionB();
        assertEquals(6, a.size() + b.size());
    }

    // ═══════════════════════════════════════════════════════════════
    //  Fiedler vector
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testFiedlerVectorLength() {
        graph = pathGraph(5);
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();
        assertEquals(5, sa.getFiedlerVector().length);
    }

    @Test
    public void testFiedlerVectorEmptyGraphEmpty() {
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();
        assertEquals(0, sa.getFiedlerVector().length);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Energy
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testEnergyComplete() {
        // K_n energy = 2(n-1)
        graph = completeGraph(4);
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();
        assertEquals(6.0, sa.getEnergy(), 1e-4); // 2*3 = 6
    }

    @Test
    public void testEnergyNonNegative() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();
        assertTrue(sa.getEnergy() >= 0.0);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Summary & toMap
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testSummaryNotNull() {
        graph = completeGraph(4);
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();
        String summary = sa.getSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("Spectral Analysis"));
        assertTrue(summary.contains("Spectral radius"));
        assertTrue(summary.contains("Algebraic connectivity"));
    }

    @Test
    public void testToMapContainsAllKeys() {
        graph = pathGraph(4);
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();
        Map<String, Object> map = sa.toMap();

        assertTrue(map.containsKey("spectralRadius"));
        assertTrue(map.containsKey("spectralGap"));
        assertTrue(map.containsKey("algebraicConnectivity"));
        assertTrue(map.containsKey("energy"));
        assertTrue(map.containsKey("spanningTreeCount"));
        assertTrue(map.containsKey("connectedSpectrally"));
        assertTrue(map.containsKey("bipartiteLikely"));
        assertTrue(map.containsKey("classification"));
        assertTrue(map.containsKey("partitionA"));
        assertTrue(map.containsKey("partitionB"));
        assertTrue(map.containsKey("adjacencyEigenvalues"));
        assertTrue(map.containsKey("laplacianEigenvalues"));
    }

    // ═══════════════════════════════════════════════════════════════
    //  Vertex ordering
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testVertexOrderSorted() {
        addEdge("Z", "A");
        addEdge("A", "M");
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();
        List<String> order = sa.getVertexOrder();
        assertEquals("A", order.get(0));
        assertEquals("M", order.get(1));
        assertEquals("Z", order.get(2));
    }

    // ═══════════════════════════════════════════════════════════════
    //  Classification string
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testClassificationConnectedGraph() {
        addEdge("A", "B");
        addEdge("B", "C");
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();
        assertTrue(sa.getClassification().contains("Connected"));
    }

    @Test
    public void testClassificationRegularGraph() {
        // C4 is 2-regular
        graph = cycleGraph(4);
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();
        assertTrue(sa.getClassification().contains("Regular"));
    }

    // ═══════════════════════════════════════════════════════════════
    //  Eigenvalue count
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testEigenvalueCountMatchesVertices() {
        graph = completeGraph(7);
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();
        assertEquals(7, sa.getAdjacencyEigenvalues().length);
        assertEquals(7, sa.getLaplacianEigenvalues().length);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Laplacian eigenvalue properties
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testLaplacianEigenvaluesNonNegative() {
        graph = completeGraph(5);
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();
        for (double ev : sa.getLaplacianEigenvalues()) {
            assertTrue("Laplacian eigenvalue should be >= 0: " + ev,
                    ev >= -1e-6);
        }
    }

    @Test
    public void testLaplacianSmallestIsZero() {
        graph = pathGraph(4);
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();
        assertEquals(0.0, sa.getLaplacianEigenvalues()[0], 1e-6);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Adjacency eigenvalue sum = 0 (trace of A)
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testAdjacencyEigenvaluesSumToZero() {
        graph = completeGraph(5);
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();
        double sum = 0;
        for (double ev : sa.getAdjacencyEigenvalues()) sum += ev;
        assertEquals(0.0, sum, 1e-4);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Defensive copy
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testGettersReturnDefensiveCopies() {
        addEdge("A", "B");
        SpectralAnalyzer sa = new SpectralAnalyzer(graph).compute();

        double[] adj = sa.getAdjacencyEigenvalues();
        adj[0] = 999.0;
        assertNotEquals(999.0, sa.getAdjacencyEigenvalues()[0], 1e-6);

        double[] fv = sa.getFiedlerVector();
        fv[0] = 999.0;
        assertNotEquals(999.0, sa.getFiedlerVector()[0], 1e-6);
    }
}
