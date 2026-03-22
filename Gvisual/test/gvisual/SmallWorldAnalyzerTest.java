package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link SmallWorldAnalyzer}.
 */
public class SmallWorldAnalyzerTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
    }

    private void addEdge(String v1, String v2) {
        Edge e = new Edge("f", v1, v2);
        e.setWeight(1.0f);
        graph.addEdge(e, v1, v2);
    }

    // ── Null / Empty / Trivial ──────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraph() {
        new SmallWorldAnalyzer(null);
    }

    @Test
    public void testEmptyGraph() {
        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        assertEquals("Too Small", swa.getClassification());
        assertEquals(0, swa.getAvgClustering(), 1e-10);
        assertEquals(0, swa.getAvgPathLength(), 1e-10);
        assertEquals(0, swa.getSigma(), 1e-10);
    }

    @Test
    public void testSingleVertex() {
        graph.addVertex("A");
        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        assertEquals("Too Small", swa.getClassification());
        assertEquals(1, swa.getLargestComponentSize());
    }

    @Test
    public void testTwoVertices() {
        addEdge("A", "B");
        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        assertEquals("Too Small", swa.getClassification());
        assertEquals(2, swa.getLargestComponentSize());
    }

    // ── Triangle ────────────────────────────────────────────────────

    @Test
    public void testTriangle_perfectClustering() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");

        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        // Every vertex has CC = 1.0 in a triangle
        assertEquals(1.0, swa.getAvgClustering(), 1e-10);
        assertEquals(1.0, swa.getGlobalClustering(), 1e-10);

        // Each local CC should be 1.0
        Map<String, Double> cc = swa.getLocalClustering();
        for (double v : cc.values()) {
            assertEquals(1.0, v, 1e-10);
        }
    }

    @Test
    public void testTriangle_pathLength() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");

        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        // All pairs at distance 1 → avg = 1.0
        assertEquals(1.0, swa.getAvgPathLength(), 1e-10);
    }

    // ── Path graph (no triangles) ───────────────────────────────────

    @Test
    public void testPath_zeroClustering() {
        // A-B-C-D: no triangles
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");

        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        assertEquals(0.0, swa.getAvgClustering(), 1e-10);
        assertEquals(0.0, swa.getGlobalClustering(), 1e-10);
    }

    @Test
    public void testCycle4_avgPathLength() {
        // A-B-C-D-A: 4 vertices, 4 edges, mean degree = 2
        // Distances: AB=1, AC=2, AD=1, BC=1, BD=2, CD=1
        // Sum = 2*(1+2+1+1+2+1) = 16, pairs = 12 → avg = 16/12 = 4/3
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "A");

        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        double expected = 4.0 / 3.0;
        assertEquals(expected, swa.getAvgPathLength(), 1e-10);
    }

    // ── Star graph ──────────────────────────────────────────────────

    @Test
    public void testStar_zeroClustering() {
        // Hub connected to 5 leaves — no triangles
        for (int i = 1; i <= 5; i++) {
            addEdge("Hub", "L" + i);
        }

        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        assertEquals(0.0, swa.getAvgClustering(), 1e-10);
        assertEquals(6, swa.getLargestComponentSize());
    }

    // ── Complete graph ──────────────────────────────────────────────

    @Test
    public void testComplete5_perfectClustering() {
        String[] vs = {"A", "B", "C", "D", "E"};
        for (int i = 0; i < vs.length; i++) {
            for (int j = i + 1; j < vs.length; j++) {
                addEdge(vs[i], vs[j]);
            }
        }

        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        assertEquals(1.0, swa.getAvgClustering(), 1e-10);
        assertEquals(1.0, swa.getGlobalClustering(), 1e-10);
        assertEquals(1.0, swa.getAvgPathLength(), 1e-10);
        assertEquals(5, swa.getLargestComponentSize());
    }

    // ── Ring lattice (high clustering, long paths) ──────────────────

    @Test
    public void testRing_highClusteringLongPaths() {
        // 10-vertex ring with k=4 (each connected to 2 nearest on each side)
        int n = 10;
        String[] vs = new String[n];
        for (int i = 0; i < n; i++) vs[i] = "V" + i;

        for (int i = 0; i < n; i++) {
            addEdge(vs[i], vs[(i + 1) % n]);
            addEdge(vs[i], vs[(i + 2) % n]);
        }

        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);

        // Ring lattice should have high clustering
        assertTrue("Ring lattice should have high clustering",
                swa.getAvgClustering() > 0.3);

        // Avg path length should be moderate
        assertTrue("Ring lattice path length should be > 1",
                swa.getAvgPathLength() > 1.0);

        assertEquals(n, swa.getLargestComponentSize());
    }

    // ── Small-world graph (ring + shortcuts) ────────────────────────

    @Test
    public void testSmallWorld_ringPlusShortcuts() {
        // Ring lattice with shortcuts → should exhibit small-world
        int n = 20;
        String[] vs = new String[n];
        for (int i = 0; i < n; i++) vs[i] = "V" + i;

        // Ring with k=4
        for (int i = 0; i < n; i++) {
            addEdge(vs[i], vs[(i + 1) % n]);
            addEdge(vs[i], vs[(i + 2) % n]);
        }

        // Add a few long-range shortcuts (Watts-Strogatz style)
        addEdge(vs[0], vs[10]);
        addEdge(vs[5], vs[15]);
        addEdge(vs[3], vs[17]);

        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);

        // Should have decent clustering (from ring) and short paths (from shortcuts)
        assertTrue("Should have high clustering", swa.getAvgClustering() > 0.2);
        assertTrue("Should have short avg path", swa.getAvgPathLength() < 4.0);

        // Sigma should be > 1 for a small-world
        assertTrue("Sigma should indicate small-world", swa.getSigma() > 1.0);
    }

    // ── Random baselines ────────────────────────────────────────────

    @Test
    public void testRandomBaselines_positive() {
        // Build a graph with known properties
        int n = 10;
        for (int i = 0; i < n; i++) {
            addEdge("V" + i, "V" + ((i + 1) % n));
        }

        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        assertTrue("C_rand should be positive", swa.getRandomClustering() > 0);
        assertTrue("L_rand should be positive", swa.getRandomPathLength() > 0);
    }

    @Test
    public void testRandomClustering_formula() {
        // C_rand = <k> / n
        int n = 10;
        for (int i = 0; i < n; i++) {
            addEdge("V" + i, "V" + ((i + 1) % n));
        }
        // edges = 10, mean degree = 2.0, C_rand = 2.0/10 = 0.2
        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        assertEquals(0.2, swa.getRandomClustering(), 1e-10);
    }

    // ── Lattice clustering baseline ─────────────────────────────────

    @Test
    public void testLatticeClustering_formula() {
        // C_lattice = 3(k-2) / 4(k-1). For k=4: 3*2/(4*3) = 0.5
        int n = 10;
        String[] vs = new String[n];
        for (int i = 0; i < n; i++) vs[i] = "V" + i;

        for (int i = 0; i < n; i++) {
            addEdge(vs[i], vs[(i + 1) % n]);
            addEdge(vs[i], vs[(i + 2) % n]);
        }
        // mean degree = 4
        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        assertEquals(0.5, swa.getLatticeClustering(), 1e-10);
    }

    // ── Disconnected graph ──────────────────────────────────────────

    @Test
    public void testDisconnected_classification() {
        // 3 separate triangles: 9 nodes, 9 edges, mean degree = 2
        // Largest component = 3/9 = 33% → "Disconnected"
        addEdge("A1", "A2");
        addEdge("A2", "A3");
        addEdge("A1", "A3");

        addEdge("B1", "B2");
        addEdge("B2", "B3");
        addEdge("B1", "B3");

        addEdge("C1", "C2");
        addEdge("C2", "C3");
        addEdge("C1", "C3");

        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        assertEquals("Disconnected", swa.getClassification());
    }

    // ── Too Sparse ──────────────────────────────────────────────────

    @Test
    public void testSparse_classification() {
        // Tree: n=5, edges=4, mean degree < 2
        addEdge("A", "B");
        addEdge("A", "C");
        addEdge("A", "D");
        addEdge("A", "E");

        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        assertEquals("Too Sparse", swa.getClassification());
    }

    // ── Top clustered ───────────────────────────────────────────────

    @Test
    public void testTopClustered() {
        // Triangle + pendant: D connected only to A
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        addEdge("A", "D");

        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        List<Map.Entry<String, Double>> top = swa.getTopClustered(2);
        assertEquals(2, top.size());

        // B and C should have CC=1.0 (both neighbours connected)
        assertTrue("Top vertex should have CC >= 0.5",
                top.get(0).getValue() >= 0.5);
    }

    @Test
    public void testTopClustered_requestMoreThanExist() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");

        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        List<Map.Entry<String, Double>> top = swa.getTopClustered(100);
        assertEquals(3, top.size()); // only 3 vertices exist
    }

    // ── Omega range ─────────────────────────────────────────────────

    @Test
    public void testOmega_bounded() {
        // Build a ring lattice
        int n = 12;
        String[] vs = new String[n];
        for (int i = 0; i < n; i++) vs[i] = "V" + i;
        for (int i = 0; i < n; i++) {
            addEdge(vs[i], vs[(i + 1) % n]);
            addEdge(vs[i], vs[(i + 2) % n]);
        }

        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        assertTrue("Omega should be >= -1.5", swa.getOmega() >= -1.5);
        assertTrue("Omega should be <= 1.5", swa.getOmega() <= 1.5);
    }

    // ── Summary format ──────────────────────────────────────────────

    @Test
    public void testSummary_notEmpty() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");

        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        String summary = swa.getSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("Small-World Analysis"));
        assertTrue(summary.contains("Clustering"));
        assertTrue(summary.contains("Path Length"));
        assertTrue(summary.contains("Sigma"));
        assertTrue(summary.contains("Omega"));
        assertTrue(summary.contains("Classification"));
    }

    @Test
    public void testSummary_containsTopClustered() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");

        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        String summary = swa.getSummary();
        assertTrue(summary.contains("Top Clustered"));
    }

    // ── Global clustering (transitivity) ────────────────────────────

    @Test
    public void testGlobalClustering_triangle() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        assertEquals(1.0, swa.getGlobalClustering(), 1e-10);
    }

    @Test
    public void testGlobalClustering_openTriple() {
        // A-B-C-D: no closed triples
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");

        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        assertEquals(0.0, swa.getGlobalClustering(), 1e-10);
    }

    // ── Lazy computation ────────────────────────────────────────────

    @Test
    public void testLazyComputation_multipleCallsSameResult() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");

        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        double cc1 = swa.getAvgClustering();
        double cc2 = swa.getAvgClustering();
        double L1 = swa.getAvgPathLength();
        double L2 = swa.getAvgPathLength();
        assertEquals(cc1, cc2, 1e-15);
        assertEquals(L1, L2, 1e-15);
    }

    // ── Larger ring with shortcuts ──────────────────────────────────

    @Test
    public void testLargerGraph_sigmaPositive() {
        int n = 30;
        String[] vs = new String[n];
        for (int i = 0; i < n; i++) vs[i] = "V" + i;

        for (int i = 0; i < n; i++) {
            addEdge(vs[i], vs[(i + 1) % n]);
            addEdge(vs[i], vs[(i + 2) % n]);
        }
        addEdge(vs[0], vs[15]);
        addEdge(vs[7], vs[22]);
        addEdge(vs[3], vs[27]);
        addEdge(vs[10], vs[25]);

        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        assertTrue("Sigma should be positive", swa.getSigma() > 0);
        assertEquals(n, swa.getLargestComponentSize());
    }

    // ── Two components, largest > 50% ───────────────────────────────

    @Test
    public void testTwoComponents_largestDominant() {
        // 7-node ring + 3-node triangle = largest is 7/10 = 70%
        for (int i = 0; i < 7; i++) {
            addEdge("R" + i, "R" + ((i + 1) % 7));
        }
        addEdge("T0", "T1");
        addEdge("T1", "T2");
        addEdge("T0", "T2");

        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        assertEquals(7, swa.getLargestComponentSize());
        assertNotEquals("Disconnected", swa.getClassification());
    }

    // ── Isolated vertices handled ───────────────────────────────────

    @Test
    public void testIsolatedVertices_handledGracefully() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        graph.addVertex("D"); // isolated

        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        assertEquals(0.0, swa.getLocalClustering().get("D"), 1e-10);
        // A,B,C → CC=1.0 each, D → CC=0 → avg = 0.75
        assertEquals(0.75, swa.getAvgClustering(), 1e-10);
    }

    // ── Complete bipartite (no triangles) ────────────────────────────

    @Test
    public void testCompleteBipartite_zeroClustering() {
        String[] left = {"L1", "L2", "L3"};
        String[] right = {"R1", "R2", "R3"};
        for (String l : left) {
            for (String r : right) {
                addEdge(l, r);
            }
        }

        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        assertEquals(0.0, swa.getAvgClustering(), 1e-10);
        assertEquals(0.0, swa.getGlobalClustering(), 1e-10);
    }

    // ── Getter consistency ──────────────────────────────────────────

    @Test
    public void testGetters_returnConsistentValues() {
        int n = 10;
        String[] vs = new String[n];
        for (int i = 0; i < n; i++) vs[i] = "V" + i;
        for (int i = 0; i < n; i++) {
            addEdge(vs[i], vs[(i + 1) % n]);
            addEdge(vs[i], vs[(i + 2) % n]);
        }

        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        assertTrue(swa.getAvgClustering() >= 0 && swa.getAvgClustering() <= 1);
        assertTrue(swa.getGlobalClustering() >= 0 && swa.getGlobalClustering() <= 1);
        assertTrue(swa.getAvgPathLength() >= 0);
        assertTrue(swa.getLargestComponentSize() > 0);
        assertTrue(swa.getRandomClustering() >= 0);
        assertTrue(swa.getRandomPathLength() >= 0);
        assertTrue(swa.getLatticeClustering() >= 0);
        assertNotNull(swa.getClassification());
        assertNotNull(swa.getLocalClustering());
    }

    // ── Unmodifiable map ────────────────────────────────────────────

    @Test(expected = UnsupportedOperationException.class)
    public void testLocalClustering_unmodifiable() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        swa.getLocalClustering().put("X", 0.5);
    }

    // ── Wheel graph ─────────────────────────────────────────────────

    @Test
    public void testWheelGraph() {
        // Hub connected to all rim nodes, rim also a cycle
        int rim = 8;
        for (int i = 0; i < rim; i++) {
            addEdge("Hub", "R" + i);
            addEdge("R" + i, "R" + ((i + 1) % rim));
        }

        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        assertTrue("Wheel should have clustering > 0",
                swa.getAvgClustering() > 0);
        assertEquals(rim + 1, swa.getLargestComponentSize());
        assertNotNull(swa.getClassification());
    }

    // ── Petersen graph (3-regular, no triangles) ────────────────────

    @Test
    public void testPetersenGraph_noTriangles() {
        // Outer 5-cycle
        for (int i = 0; i < 5; i++) {
            addEdge("O" + i, "O" + ((i + 1) % 5));
        }
        // Inner pentagram
        for (int i = 0; i < 5; i++) {
            addEdge("I" + i, "I" + ((i + 2) % 5));
        }
        // Spokes
        for (int i = 0; i < 5; i++) {
            addEdge("O" + i, "I" + i);
        }

        SmallWorldAnalyzer swa = new SmallWorldAnalyzer(graph);
        // Petersen graph is triangle-free
        assertEquals(0.0, swa.getAvgClustering(), 1e-10);
        assertEquals(10, swa.getLargestComponentSize());
    }
}
