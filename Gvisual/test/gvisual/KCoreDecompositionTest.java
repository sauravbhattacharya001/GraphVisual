package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link KCoreDecomposition}.
 */
public class KCoreDecompositionTest {

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

    // ═══════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNullGraph() {
        new KCoreDecomposition(null);
    }

    @Test
    public void testConstructorValid() {
        KCoreDecomposition kcore = new KCoreDecomposition(graph);
        assertNotNull(kcore);
    }

    // ═══════════════════════════════════════
    // Empty graph
    // ═══════════════════════════════════════

    @Test
    public void testEmptyGraph() {
        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        assertEquals(0, kcore.getDegeneracy());
        assertTrue(kcore.getCoreness().isEmpty());
        assertEquals(0.0, kcore.getCohesionScore(), 1e-10);
        assertEquals(0.0, kcore.getAverageCoreness(), 1e-10);
        assertEquals("Empty", kcore.classifyCoreStructure());
    }

    // ═══════════════════════════════════════
    // Single vertex
    // ═══════════════════════════════════════

    @Test
    public void testSingleVertex() {
        graph.addVertex("A");
        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        assertEquals(0, kcore.getDegeneracy());
        assertEquals(0, kcore.getCoreness("A"));
        assertEquals(100.0, kcore.getCohesionScore(), 1e-10);
    }

    // ═══════════════════════════════════════
    // Isolated vertices
    // ═══════════════════════════════════════

    @Test
    public void testAllIsolatedVertices() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        assertEquals(0, kcore.getDegeneracy());
        assertEquals(0, kcore.getCoreness("A"));
        assertEquals(0, kcore.getCoreness("B"));
        assertEquals(0, kcore.getCoreness("C"));
        assertEquals("Disconnected (all isolated vertices)", kcore.classifyCoreStructure());
    }

    // ═══════════════════════════════════════
    // Simple edge (2 vertices)
    // ═══════════════════════════════════════

    @Test
    public void testSingleEdge() {
        addEdge("A", "B");
        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        assertEquals(1, kcore.getDegeneracy());
        assertEquals(1, kcore.getCoreness("A"));
        assertEquals(1, kcore.getCoreness("B"));
    }

    // ═══════════════════════════════════════
    // Path graph (A-B-C-D)
    // ═══════════════════════════════════════

    @Test
    public void testPathGraph() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        // All vertices in a path have coreness 1
        assertEquals(1, kcore.getDegeneracy());
        assertEquals(1, kcore.getCoreness("A"));
        assertEquals(1, kcore.getCoreness("B"));
        assertEquals(1, kcore.getCoreness("C"));
        assertEquals(1, kcore.getCoreness("D"));
        assertEquals("Tree-like (no dense subgraphs)", kcore.classifyCoreStructure());
    }

    // ═══════════════════════════════════════
    // Star graph (hub + 4 leaves)
    // ═══════════════════════════════════════

    @Test
    public void testStarGraph() {
        addEdge("H", "A");
        addEdge("H", "B");
        addEdge("H", "C");
        addEdge("H", "D");
        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        // Star: hub and leaves all have coreness 1
        assertEquals(1, kcore.getDegeneracy());
        for (String v : graph.getVertices()) {
            assertEquals(1, kcore.getCoreness(v));
        }
    }

    // ═══════════════════════════════════════
    // Triangle (K3) — 2-core
    // ═══════════════════════════════════════

    @Test
    public void testTriangle() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        assertEquals(2, kcore.getDegeneracy());
        assertEquals(2, kcore.getCoreness("A"));
        assertEquals(2, kcore.getCoreness("B"));
        assertEquals(2, kcore.getCoreness("C"));
    }

    // ═══════════════════════════════════════
    // Complete graph K4 — 3-core
    // ═══════════════════════════════════════

    @Test
    public void testCompleteK4() {
        addEdge("A", "B");
        addEdge("A", "C");
        addEdge("A", "D");
        addEdge("B", "C");
        addEdge("B", "D");
        addEdge("C", "D");
        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        assertEquals(3, kcore.getDegeneracy());
        for (String v : graph.getVertices()) {
            assertEquals(3, kcore.getCoreness(v));
        }
        // All in the innermost core → 100% cohesion
        assertEquals(100.0, kcore.getCohesionScore(), 1e-10);
    }

    // ═══════════════════════════════════════
    // Complete graph K5 — 4-core
    // ═══════════════════════════════════════

    @Test
    public void testCompleteK5() {
        String[] nodes = {"A", "B", "C", "D", "E"};
        for (int i = 0; i < nodes.length; i++) {
            for (int j = i + 1; j < nodes.length; j++) {
                addEdge(nodes[i], nodes[j]);
            }
        }
        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        assertEquals(4, kcore.getDegeneracy());
    }

    // ═══════════════════════════════════════
    // Triangle with pendant (A-B-C triangle + D hanging off A)
    // ═══════════════════════════════════════

    @Test
    public void testTriangleWithPendant() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        addEdge("A", "D");  // pendant vertex
        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        assertEquals(2, kcore.getDegeneracy());
        assertEquals(2, kcore.getCoreness("A"));
        assertEquals(2, kcore.getCoreness("B"));
        assertEquals(2, kcore.getCoreness("C"));
        assertEquals(1, kcore.getCoreness("D"));  // pendant is 1-core
    }

    // ═══════════════════════════════════════
    // Two triangles connected by a bridge
    // ═══════════════════════════════════════

    @Test
    public void testTwoTrianglesWithBridge() {
        // Triangle 1: A-B-C
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        // Bridge
        addEdge("C", "D");
        // Triangle 2: D-E-F
        addEdge("D", "E");
        addEdge("E", "F");
        addEdge("D", "F");

        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        assertEquals(2, kcore.getDegeneracy());
        // Bridge vertices still get coreness 2 since they're in triangles
        assertEquals(2, kcore.getCoreness("A"));
        assertEquals(2, kcore.getCoreness("B"));
        assertEquals(2, kcore.getCoreness("C"));
        assertEquals(2, kcore.getCoreness("D"));
        assertEquals(2, kcore.getCoreness("E"));
        assertEquals(2, kcore.getCoreness("F"));
    }

    // ═══════════════════════════════════════
    // Core-periphery structure
    // ═══════════════════════════════════════

    @Test
    public void testCorePeriphery() {
        // Dense core: A-B-C-D (K4)
        addEdge("A", "B");
        addEdge("A", "C");
        addEdge("A", "D");
        addEdge("B", "C");
        addEdge("B", "D");
        addEdge("C", "D");
        // Peripheral nodes connected to one core node
        addEdge("A", "P1");
        addEdge("B", "P2");
        addEdge("C", "P3");

        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        assertEquals(3, kcore.getDegeneracy());
        // Core nodes
        assertEquals(3, kcore.getCoreness("A"));
        assertEquals(3, kcore.getCoreness("B"));
        assertEquals(3, kcore.getCoreness("C"));
        assertEquals(3, kcore.getCoreness("D"));
        // Peripheral nodes
        assertEquals(1, kcore.getCoreness("P1"));
        assertEquals(1, kcore.getCoreness("P2"));
        assertEquals(1, kcore.getCoreness("P3"));
    }

    // ═══════════════════════════════════════
    // Shell grouping
    // ═══════════════════════════════════════

    @Test
    public void testCoreShells() {
        // K3 + pendant
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        addEdge("A", "D");

        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        SortedMap<Integer, List<String>> shells = kcore.getCoreShells();

        assertEquals(2, shells.size());
        assertTrue(shells.containsKey(1));
        assertTrue(shells.containsKey(2));
        assertEquals(1, shells.get(1).size());
        assertTrue(shells.get(1).contains("D"));
        assertEquals(3, shells.get(2).size());
    }

    @Test
    public void testGetKCore() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        addEdge("A", "D");

        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();

        // 0-core = all vertices
        assertEquals(4, kcore.getKCore(0).size());
        // 1-core = all vertices (all have coreness >= 1)
        assertEquals(4, kcore.getKCore(1).size());
        // 2-core = triangle only
        assertEquals(3, kcore.getKCore(2).size());
        assertFalse(kcore.getKCore(2).contains("D"));
        // 3-core = empty
        assertEquals(0, kcore.getKCore(3).size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetKCoreNegative() {
        new KCoreDecomposition(graph).compute().getKCore(-1);
    }

    // ═══════════════════════════════════════
    // Density profile
    // ═══════════════════════════════════════

    @Test
    public void testCoreDensityProfile() {
        // K3 + pendant
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        addEdge("A", "D");

        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        List<KCoreDecomposition.CoreDensity> profile = kcore.getCoreDensityProfile();

        // Should have profiles for 0-core, 1-core, 2-core
        assertTrue(profile.size() >= 2);

        // 2-core (triangle) should have density 1.0 (complete subgraph)
        KCoreDecomposition.CoreDensity innermost = profile.get(profile.size() - 1);
        assertEquals(2, innermost.getK());
        assertEquals(3, innermost.getVertices());
        assertEquals(3, innermost.getEdges());
        assertEquals(1.0, innermost.getDensity(), 1e-10);
    }

    @Test
    public void testDensityIncreasesWithK() {
        // Build graph with clear layered structure
        // Core: A-B-C (triangle, 2-core)
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        // Peripheral: D connected to A only
        addEdge("A", "D");
        // Extra peripheral
        addEdge("D", "E");

        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        List<KCoreDecomposition.CoreDensity> profile = kcore.getCoreDensityProfile();

        // Density should be non-decreasing (higher cores are denser)
        for (int i = 1; i < profile.size(); i++) {
            assertTrue("Density should increase with k",
                    profile.get(i).getDensity() >= profile.get(i - 1).getDensity());
        }
    }

    // ═══════════════════════════════════════
    // Analytics
    // ═══════════════════════════════════════

    @Test
    public void testCohesionScoreComplete() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        // All vertices have same coreness → 100%
        assertEquals(100.0, kcore.getCohesionScore(), 1e-10);
    }

    @Test
    public void testCohesionScoreLow() {
        // Star: 1 hub, 10 leaves — all have coreness 1 → 100%
        // Actually all same coreness. Use different structure.
        // K4 core + 6 pendant leaves
        addEdge("A", "B");
        addEdge("A", "C");
        addEdge("A", "D");
        addEdge("B", "C");
        addEdge("B", "D");
        addEdge("C", "D");
        addEdge("A", "P1");
        addEdge("A", "P2");
        addEdge("B", "P3");
        addEdge("B", "P4");
        addEdge("C", "P5");
        addEdge("D", "P6");

        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        // 4 core nodes / 10 total = 40%
        assertEquals(40.0, kcore.getCohesionScore(), 1e-10);
    }

    @Test
    public void testAverageCoreness() {
        // Triangle + pendant
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        addEdge("A", "D");

        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        // Coreness: A=2, B=2, C=2, D=1 → avg = 7/4 = 1.75
        assertEquals(1.75, kcore.getAverageCoreness(), 1e-10);
    }

    @Test
    public void testCorenessDistribution() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        addEdge("A", "D");

        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        SortedMap<Integer, Integer> dist = kcore.getCorenessDistribution();

        assertEquals(2, dist.size());
        assertEquals(Integer.valueOf(1), dist.get(1));  // D
        assertEquals(Integer.valueOf(3), dist.get(2));  // A, B, C
    }

    // ═══════════════════════════════════════
    // Structure classification
    // ═══════════════════════════════════════

    @Test
    public void testClassifyEmpty() {
        assertEquals("Empty", new KCoreDecomposition(graph).compute().classifyCoreStructure());
    }

    @Test
    public void testClassifyDisconnected() {
        graph.addVertex("A");
        graph.addVertex("B");
        assertEquals("Disconnected (all isolated vertices)",
                new KCoreDecomposition(graph).compute().classifyCoreStructure());
    }

    @Test
    public void testClassifyTreeLike() {
        addEdge("A", "B");
        addEdge("B", "C");
        assertEquals("Tree-like (no dense subgraphs)",
                new KCoreDecomposition(graph).compute().classifyCoreStructure());
    }

    @Test
    public void testClassifyHighlyCohesive() {
        // K4: all vertices have coreness 3, cohesion = 100%
        addEdge("A", "B");
        addEdge("A", "C");
        addEdge("A", "D");
        addEdge("B", "C");
        addEdge("B", "D");
        addEdge("C", "D");
        assertEquals("Highly cohesive (single dense core)",
                new KCoreDecomposition(graph).compute().classifyCoreStructure());
    }

    // ═══════════════════════════════════════
    // Result object
    // ═══════════════════════════════════════

    @Test
    public void testResultObject() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        addEdge("A", "D");

        KCoreDecomposition.KCoreResult result =
                new KCoreDecomposition(graph).compute().getResult();

        assertEquals(4, result.getVertexCount());
        assertEquals(4, result.getEdgeCount());
        assertEquals(2, result.getDegeneracy());
        assertEquals(2, result.getNumberOfShells());
        assertEquals(1.75, result.getAverageCoreness(), 1e-10);
        assertNotNull(result.getCoreness());
        assertNotNull(result.getShells());
        assertNotNull(result.getDistribution());
        assertNotNull(result.getDensityProfile());
        assertNotNull(result.getStructureClassification());
    }

    // ═══════════════════════════════════════
    // Non-existent vertex
    // ═══════════════════════════════════════

    @Test
    public void testCorenessNonExistentVertex() {
        addEdge("A", "B");
        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        assertEquals(-1, kcore.getCoreness("Z"));
    }

    // ═══════════════════════════════════════
    // Idempotent compute
    // ═══════════════════════════════════════

    @Test
    public void testComputeIdempotent() {
        addEdge("A", "B");
        KCoreDecomposition kcore = new KCoreDecomposition(graph);
        kcore.compute();
        Map<String, Integer> first = kcore.getCoreness();
        kcore.compute();  // second call
        Map<String, Integer> second = kcore.getCoreness();
        assertEquals(first, second);
    }

    // ═══════════════════════════════════════
    // Auto-compute on accessor
    // ═══════════════════════════════════════

    @Test
    public void testAutoCompute() {
        addEdge("A", "B");
        KCoreDecomposition kcore = new KCoreDecomposition(graph);
        // Calling accessor without explicit compute() should auto-compute
        assertEquals(1, kcore.getDegeneracy());
    }

    // ═══════════════════════════════════════
    // Cycle graph (C5) — all vertices 2-core
    // ═══════════════════════════════════════

    @Test
    public void testCycle5() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "E");
        addEdge("E", "A");
        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        assertEquals(2, kcore.getDegeneracy());
        for (String v : graph.getVertices()) {
            assertEquals(2, kcore.getCoreness(v));
        }
    }

    // ═══════════════════════════════════════
    // Petersen graph (3-regular) — 3-core
    // ═══════════════════════════════════════

    @Test
    public void testPetersenGraph() {
        // Outer ring
        addEdge("0", "1");
        addEdge("1", "2");
        addEdge("2", "3");
        addEdge("3", "4");
        addEdge("4", "0");
        // Inner pentagram
        addEdge("5", "7");
        addEdge("7", "9");
        addEdge("9", "6");
        addEdge("6", "8");
        addEdge("8", "5");
        // Spokes
        addEdge("0", "5");
        addEdge("1", "6");
        addEdge("2", "7");
        addEdge("3", "8");
        addEdge("4", "9");

        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        // Petersen graph is 3-regular, so all vertices have coreness 3
        assertEquals(3, kcore.getDegeneracy());
        for (String v : graph.getVertices()) {
            assertEquals(3, kcore.getCoreness(v));
        }
    }

    // ═══════════════════════════════════════
    // Bipartite graph K3,3 — 3-core
    // ═══════════════════════════════════════

    @Test
    public void testBipartiteK33() {
        String[] left = {"L1", "L2", "L3"};
        String[] right = {"R1", "R2", "R3"};
        for (String l : left) {
            for (String r : right) {
                addEdge(l, r);
            }
        }
        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        assertEquals(3, kcore.getDegeneracy());
    }

    // ═══════════════════════════════════════
    // Wheel graph W5 (hub + C4) — 3-core
    // ═══════════════════════════════════════

    @Test
    public void testWheelGraph() {
        // Outer cycle
        addEdge("1", "2");
        addEdge("2", "3");
        addEdge("3", "4");
        addEdge("4", "1");
        // Hub
        addEdge("H", "1");
        addEdge("H", "2");
        addEdge("H", "3");
        addEdge("H", "4");

        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        assertEquals(3, kcore.getDegeneracy());
    }

    // ═══════════════════════════════════════
    // Summary
    // ═══════════════════════════════════════

    @Test
    public void testSummaryNotEmpty() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");

        String summary = new KCoreDecomposition(graph).compute().getSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("K-Core Decomposition"));
        assertTrue(summary.contains("Degeneracy: 2"));
        assertTrue(summary.contains("Shell distribution"));
        assertTrue(summary.contains("Core density profile"));
    }

    @Test
    public void testSummaryEmpty() {
        String summary = new KCoreDecomposition(graph).compute().getSummary();
        assertTrue(summary.contains("Degeneracy: 0"));
    }

    // ═══════════════════════════════════════
    // Number of shells
    // ═══════════════════════════════════════

    @Test
    public void testNumberOfShells() {
        // K4 + pendant = shells {1, 3}
        addEdge("A", "B");
        addEdge("A", "C");
        addEdge("A", "D");
        addEdge("B", "C");
        addEdge("B", "D");
        addEdge("C", "D");
        addEdge("A", "P");

        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        assertEquals(2, kcore.getNumberOfShells()); // shells at k=1 and k=3
    }

    // ═══════════════════════════════════════
    // CoreDensity toString
    // ═══════════════════════════════════════

    @Test
    public void testCoreDensityToString() {
        KCoreDecomposition.CoreDensity cd =
                new KCoreDecomposition.CoreDensity(3, 4, 6, 1.0);
        String s = cd.toString();
        assertTrue(s.contains("3-core"));
        assertTrue(s.contains("4 vertices"));
        assertTrue(s.contains("6 edges"));
        assertTrue(s.contains("1.0000"));
    }

    // ═══════════════════════════════════════
    // Large layered graph
    // ═══════════════════════════════════════

    @Test
    public void testLayeredGraph() {
        // Inner core: K4 (A,B,C,D) — coreness 3
        addEdge("A", "B");
        addEdge("A", "C");
        addEdge("A", "D");
        addEdge("B", "C");
        addEdge("B", "D");
        addEdge("C", "D");

        // Middle layer: E,F connected to two core nodes each + to each other
        addEdge("E", "A");
        addEdge("E", "B");
        addEdge("E", "F");
        addEdge("F", "C");
        addEdge("F", "D");

        // Outer: G just connects to E
        addEdge("G", "E");

        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();

        // Core nodes should have highest coreness
        assertTrue(kcore.getCoreness("A") >= kcore.getCoreness("E"));
        assertTrue(kcore.getCoreness("E") >= kcore.getCoreness("G"));

        // G should be in the outermost shell (lowest coreness among connected)
        assertEquals(1, kcore.getCoreness("G"));

        // Should have multiple shells
        assertTrue(kcore.getNumberOfShells() >= 2);
    }

    // ═══════════════════════════════════════
    // Mixed isolated + connected
    // ═══════════════════════════════════════

    @Test
    public void testMixedIsolatedAndConnected() {
        graph.addVertex("ISO1");
        graph.addVertex("ISO2");
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");

        KCoreDecomposition kcore = new KCoreDecomposition(graph).compute();
        assertEquals(0, kcore.getCoreness("ISO1"));
        assertEquals(0, kcore.getCoreness("ISO2"));
        assertEquals(2, kcore.getCoreness("A"));
        assertEquals(2, kcore.getDegeneracy());
    }

    // ═══════════════════════════════════════
    // Shell sorted alphabetically
    // ═══════════════════════════════════════

    @Test
    public void testShellsSortedAlphabetically() {
        addEdge("C", "A");
        addEdge("A", "B");
        addEdge("B", "C");

        SortedMap<Integer, List<String>> shells =
                new KCoreDecomposition(graph).compute().getCoreShells();
        List<String> shell2 = shells.get(2);
        assertEquals("A", shell2.get(0));
        assertEquals("B", shell2.get(1));
        assertEquals("C", shell2.get(2));
    }

    // ═══════════════════════════════════════
    // getKCore returns sorted list
    // ═══════════════════════════════════════

    @Test
    public void testGetKCoreSorted() {
        addEdge("C", "A");
        addEdge("A", "B");
        addEdge("B", "C");

        List<String> core = new KCoreDecomposition(graph).compute().getKCore(2);
        assertEquals("A", core.get(0));
        assertEquals("B", core.get(1));
        assertEquals("C", core.get(2));
    }

    // ═══════════════════════════════════════
    // KCoreResult accessors
    // ═══════════════════════════════════════

    @Test
    public void testKCoreResultAccessors() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");

        KCoreDecomposition.KCoreResult r =
                new KCoreDecomposition(graph).compute().getResult();

        assertEquals(3, r.getVertexCount());
        assertEquals(3, r.getEdgeCount());
        assertEquals(2, r.getDegeneracy());
        assertEquals(1, r.getNumberOfShells());
        assertEquals(100.0, r.getCohesionScore(), 1e-10);
        assertEquals(2.0, r.getAverageCoreness(), 1e-10);
        assertTrue(r.getStructureClassification().contains("cohesive"));
        assertEquals(3, r.getCoreness().size());
        assertEquals(1, r.getShells().size());
        assertEquals(1, r.getDistribution().size());
        assertFalse(r.getDensityProfile().isEmpty());
    }
}
