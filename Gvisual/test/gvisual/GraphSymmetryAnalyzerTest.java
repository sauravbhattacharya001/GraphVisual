package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link GraphSymmetryAnalyzer}.
 */
public class GraphSymmetryAnalyzerTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
    }

    private edge addEdge(String v1, String v2) {
        edge e = new Edge("f", v1, v2);
        e.setWeight(1.0f);
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        graph.addEdge(e, v1, v2);
        return e;
    }

    /** Builds a complete graph K_n on vertices "0", "1", ..., "n-1". */
    private void buildComplete(int n) {
        for (int i = 0; i < n; i++) graph.addVertex(String.valueOf(i));
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                addEdge(String.valueOf(i), String.valueOf(j));
    }

    /** Builds a cycle C_n on vertices "0", "1", ..., "n-1". */
    private void buildCycle(int n) {
        for (int i = 0; i < n; i++) graph.addVertex(String.valueOf(i));
        for (int i = 0; i < n; i++)
            addEdge(String.valueOf(i), String.valueOf((i + 1) % n));
    }

    /** Builds a path P_n: 0—1—2—…—(n-1). */
    private void buildPath(int n) {
        for (int i = 0; i < n; i++) graph.addVertex(String.valueOf(i));
        for (int i = 0; i < n - 1; i++)
            addEdge(String.valueOf(i), String.valueOf(i + 1));
    }

    /** Builds a star K_{1,n}: centre "c" with n leaves "0"…"n-1". */
    private void buildStar(int n) {
        graph.addVertex("c");
        for (int i = 0; i < n; i++) {
            graph.addVertex(String.valueOf(i));
            addEdge("c", String.valueOf(i));
        }
    }

    // ═══════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════

    @Test(expected = IllegalArgumentException.class)
    public void nullGraphThrows() {
        new GraphSymmetryAnalyzer(null);
    }

    // ═══════════════════════════════════════
    // Empty graph
    // ═══════════════════════════════════════

    @Test
    public void emptyGraph_vertexTransitive() {
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertTrue(a.isVertexTransitive());
        assertTrue(a.isEdgeTransitive());
        assertTrue(a.isSymmetric());
        assertTrue(a.isAsymmetric());
        assertEquals(1.0, a.getSymmetryFactor(), 0.0001);
        assertEquals(0.0, a.getSymmetricVertexFraction(), 0.0001);
    }

    @Test
    public void emptyGraph_noOrbits() {
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertEquals(0, a.getVertexOrbits().size());
        assertEquals(0, a.getEdgeOrbits().size());
        assertEquals(0, a.getFixedPoints().size());
    }

    // ═══════════════════════════════════════
    // Single vertex
    // ═══════════════════════════════════════

    @Test
    public void singleVertex_singleOrbit() {
        graph.addVertex("a");
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertEquals(1, a.getVertexOrbitCount());
        assertTrue(a.isVertexTransitive());
        assertTrue(a.isAsymmetric()); // only one vertex → trivially asymmetric
        assertEquals(1.0, a.getSymmetryFactor(), 0.0001);
    }

    // ═══════════════════════════════════════
    // Complete graph K_n (vertex-transitive, edge-transitive)
    // ═══════════════════════════════════════

    @Test
    public void completeK4_vertexTransitive() {
        buildComplete(4);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertTrue(a.isVertexTransitive());
        assertEquals(1, a.getVertexOrbitCount());
        assertEquals(4, a.getLargestOrbitSize());
    }

    @Test
    public void completeK4_edgeTransitive() {
        buildComplete(4);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertTrue(a.isEdgeTransitive());
        assertEquals(1, a.getEdgeOrbitCount());
    }

    @Test
    public void completeK4_symmetric() {
        buildComplete(4);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertTrue(a.isSymmetric());
        assertFalse(a.isAsymmetric());
        assertEquals(1.0, a.getSymmetryFactor(), 0.0001);
        assertEquals(1.0, a.getSymmetricVertexFraction(), 0.0001);
    }

    @Test
    public void completeK5_noFixedPoints() {
        buildComplete(5);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertEquals(0, a.getFixedPoints().size());
        assertEquals(0, a.getAsymmetricVertices().size());
    }

    // ═══════════════════════════════════════
    // Cycle C_n (vertex-transitive, edge-transitive)
    // ═══════════════════════════════════════

    @Test
    public void cycleC5_vertexTransitive() {
        buildCycle(5);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertTrue(a.isVertexTransitive());
        assertEquals(1, a.getVertexOrbitCount());
    }

    @Test
    public void cycleC5_edgeTransitive() {
        buildCycle(5);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertTrue(a.isEdgeTransitive());
    }

    @Test
    public void cycleC6_symmetric() {
        buildCycle(6);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertTrue(a.isSymmetric());
    }

    // ═══════════════════════════════════════
    // Path P_n (not transitive — endpoints differ)
    // ═══════════════════════════════════════

    @Test
    public void pathP5_notVertexTransitive() {
        buildPath(5);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertFalse(a.isVertexTransitive());
        // Path 0—1—2—3—4 has orbits: {0,4}, {1,3}, {2}
        assertEquals(3, a.getVertexOrbitCount());
    }

    @Test
    public void pathP5_centreIsFixedPoint() {
        buildPath(5);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        List<String> fixed = a.getFixedPoints();
        assertEquals(1, fixed.size());
        assertEquals("2", fixed.get(0));
    }

    @Test
    public void pathP5_endpointsSameOrbit() {
        buildPath(5);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        Set<String> orbit0 = a.getOrbitOf("0");
        assertTrue(orbit0.contains("4"));
        assertEquals(2, orbit0.size());
    }

    @Test
    public void pathP4_twoOrbits() {
        buildPath(4);
        // Path 0—1—2—3: orbits {0,3} and {1,2}
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertEquals(2, a.getVertexOrbitCount());
        assertFalse(a.isVertexTransitive());
        assertEquals(0, a.getFixedPoints().size());
    }

    // ═══════════════════════════════════════
    // Star K_{1,n} (not vertex-transitive, edge-transitive)
    // ═══════════════════════════════════════

    @Test
    public void starK1_4_notVertexTransitive() {
        buildStar(4);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertFalse(a.isVertexTransitive());
        // 2 orbits: {c} and {0,1,2,3}
        assertEquals(2, a.getVertexOrbitCount());
    }

    @Test
    public void starK1_4_centreIsFixedPoint() {
        buildStar(4);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertTrue(a.getFixedPoints().contains("c"));
        assertEquals(1, a.getFixedPoints().size());
    }

    @Test
    public void starK1_4_edgeTransitive() {
        buildStar(4);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertTrue(a.isEdgeTransitive());
        assertEquals(1, a.getEdgeOrbitCount());
    }

    @Test
    public void starK1_4_symmetryFactor() {
        buildStar(4);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertEquals(0.5, a.getSymmetryFactor(), 0.0001); // 1/2 orbits
    }

    // ═══════════════════════════════════════
    // Petersen graph (vertex-transitive, edge-transitive)
    // ═══════════════════════════════════════

    private void buildPetersen() {
        // Outer cycle: 0-1-2-3-4-0
        // Inner pentagram: 5-7-9-6-8-5
        for (int i = 0; i < 10; i++) graph.addVertex(String.valueOf(i));
        // Outer ring
        addEdge("0", "1"); addEdge("1", "2"); addEdge("2", "3");
        addEdge("3", "4"); addEdge("4", "0");
        // Inner pentagram
        addEdge("5", "7"); addEdge("7", "9"); addEdge("9", "6");
        addEdge("6", "8"); addEdge("8", "5");
        // Spokes
        addEdge("0", "5"); addEdge("1", "6"); addEdge("2", "7");
        addEdge("3", "8"); addEdge("4", "9");
    }

    @Test
    public void petersen_vertexTransitive() {
        buildPetersen();
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertTrue(a.isVertexTransitive());
        assertEquals(1, a.getVertexOrbitCount());
        assertEquals(10, a.getLargestOrbitSize());
    }

    @Test
    public void petersen_edgeTransitive() {
        buildPetersen();
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertTrue(a.isEdgeTransitive());
    }

    @Test
    public void petersen_symmetric() {
        buildPetersen();
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertTrue(a.isSymmetric());
    }

    // ═══════════════════════════════════════
    // Asymmetric graph
    // ═══════════════════════════════════════

    @Test
    public void asymmetricGraph_manyFixedPoints() {
        // Build a graph with enough structural asymmetry that colour
        // refinement finds many fixed points.
        // 0—1—2—3—4, plus 1—5, 5—6
        // After WL:
        //   0: deg1, nbr=[deg2] 
        //   4: deg1, nbr=[deg2]  → 0 and 4 same orbit
        //   1: deg3, nbrs=[deg1,deg2,deg2]
        //   2: deg2, nbrs=[deg2,deg3]
        //   3: deg2, nbrs=[deg1,deg2]
        //   5: deg2, nbrs=[deg1,deg3]
        //   6: deg1, nbr=[deg2]  → same as 0,4
        // At least 5 orbits, several vertices unique.
        addEdge("0", "1");
        addEdge("1", "2");
        addEdge("2", "3");
        addEdge("3", "4");
        addEdge("1", "5");
        addEdge("5", "6");
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        // Should not be vertex-transitive
        assertFalse(a.isVertexTransitive());
        // Multiple orbits expected
        assertTrue(a.getVertexOrbitCount() >= 4);
        // Fixed points exist (unique vertices like 1, 2)
        assertTrue(a.getFixedPoints().size() >= 2);
    }

    // ═══════════════════════════════════════
    // Disconnected graph — isolated vertices
    // ═══════════════════════════════════════

    @Test
    public void isolatedVertices_sameOrbit() {
        graph.addVertex("a");
        graph.addVertex("b");
        graph.addVertex("c");
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertTrue(a.isVertexTransitive());
        assertEquals(1, a.getVertexOrbitCount());
        assertEquals(3, a.getLargestOrbitSize());
    }

    @Test
    public void mixedIsolatedAndConnected() {
        // Edge a—b, plus isolated c
        addEdge("a", "b");
        graph.addVertex("c");
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertFalse(a.isVertexTransitive());
        // {a,b} (degree 1) and {c} (degree 0)
        assertEquals(2, a.getVertexOrbitCount());
    }

    // ═══════════════════════════════════════
    // Orbit size distribution
    // ═══════════════════════════════════════

    @Test
    public void pathP5_orbitDistribution() {
        buildPath(5);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        Map<Integer, Integer> dist = a.getOrbitSizeDistribution();
        // {0,4} size 2, {1,3} size 2, {2} size 1
        assertEquals(Integer.valueOf(1), dist.get(1)); // 1 orbit of size 1
        assertEquals(Integer.valueOf(2), dist.get(2)); // 2 orbits of size 2
    }

    // ═══════════════════════════════════════
    // Distinguishing number lower bound
    // ═══════════════════════════════════════

    @Test
    public void completeK4_distinguishingBound() {
        buildComplete(4);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertEquals(4, a.getDistinguishingNumberLowerBound());
    }

    @Test
    public void pathP5_distinguishingBound() {
        buildPath(5);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertEquals(2, a.getDistinguishingNumberLowerBound());
    }

    // ═══════════════════════════════════════
    // Symmetric vertex fraction
    // ═══════════════════════════════════════

    @Test
    public void pathP5_symmetricFraction() {
        buildPath(5);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        // 4 of 5 vertices are in non-singleton orbits
        assertEquals(0.8, a.getSymmetricVertexFraction(), 0.0001);
    }

    @Test
    public void completeK3_symmetricFractionOne() {
        buildComplete(3);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertEquals(1.0, a.getSymmetricVertexFraction(), 0.0001);
    }

    // ═══════════════════════════════════════
    // getOrbitOf
    // ═══════════════════════════════════════

    @Test(expected = IllegalArgumentException.class)
    public void getOrbitOf_invalidVertex_throws() {
        buildComplete(3);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        a.getOrbitOf("zzz");
    }

    @Test(expected = IllegalArgumentException.class)
    public void getOrbitOf_null_throws() {
        buildComplete(3);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        a.getOrbitOf(null);
    }

    @Test
    public void getOrbitOf_returnsCorrectSet() {
        buildPath(5);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        Set<String> orbit = a.getOrbitOf("0");
        assertTrue(orbit.contains("0"));
        assertTrue(orbit.contains("4"));
        assertEquals(2, orbit.size());
    }

    // ═══════════════════════════════════════
    // Edge orbits — specific tests
    // ═══════════════════════════════════════

    @Test
    public void pathP5_edgeOrbits() {
        buildPath(5);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        // Edges: 0-1, 1-2, 2-3, 3-4
        // Orbit pairs: {0,4}-{1,3} and {1,3}-{2}
        // So 0-1 ↔ 3-4 (orbit pair {leaf,inner1}) and 1-2 ↔ 2-3 (orbit pair {inner1,centre})
        assertEquals(2, a.getEdgeOrbitCount());
    }

    @Test
    public void singleEdge_oneEdgeOrbit() {
        addEdge("a", "b");
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertEquals(1, a.getEdgeOrbitCount());
    }

    // ═══════════════════════════════════════
    // Report
    // ═══════════════════════════════════════

    @Test
    public void report_containsKeyInfo() {
        buildComplete(3);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        String report = a.generateReport();
        assertTrue(report.contains("Vertex orbits: 1"));
        assertTrue(report.contains("Vertex-transitive: true"));
        assertTrue(report.contains("Edge-transitive: true"));
        assertTrue(report.contains("Symmetric (arc-transitive): true"));
        assertTrue(report.contains("Symmetry factor:"));
    }

    @Test
    public void report_showsFixedPoints() {
        buildPath(5);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        String report = a.generateReport();
        assertTrue(report.contains("Fixed points (asymmetric vertices):"));
        assertTrue(report.contains("2"));
    }

    @Test
    public void report_emptyGraph() {
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        String report = a.generateReport();
        assertTrue(report.contains("Vertices: 0"));
    }

    // ═══════════════════════════════════════
    // Vertex orbit map
    // ═══════════════════════════════════════

    @Test
    public void vertexOrbitMap_completeGraph() {
        buildComplete(3);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        Map<String, Integer> map = a.getVertexOrbitMap();
        assertEquals(3, map.size());
        // All same orbit id
        int first = map.values().iterator().next();
        for (int v : map.values()) {
            assertEquals(first, v);
        }
    }

    @Test
    public void vertexOrbitMap_unmodifiable() {
        buildComplete(3);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        try {
            a.getVertexOrbitMap().put("x", 99);
            fail("Should be unmodifiable");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    // ═══════════════════════════════════════
    // Bipartite K_{3,3} (vertex-transitive, edge-transitive)
    // ═══════════════════════════════════════

    @Test
    public void bipartiteK33_vertexTransitive() {
        // K_{3,3}: all vertices have degree 3
        for (int i = 0; i < 3; i++)
            for (int j = 3; j < 6; j++)
                addEdge(String.valueOf(i), String.valueOf(j));
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertTrue(a.isVertexTransitive());
    }

    // ═══════════════════════════════════════
    // Cube Q3 (vertex-transitive, edge-transitive)
    // ═══════════════════════════════════════

    @Test
    public void cubeQ3_symmetric() {
        // 3-cube: 8 vertices, 12 edges, 3-regular
        for (int i = 0; i < 8; i++) graph.addVertex(String.valueOf(i));
        for (int i = 0; i < 8; i++)
            for (int j = i + 1; j < 8; j++)
                if (Integer.bitCount(i ^ j) == 1)
                    addEdge(String.valueOf(i), String.valueOf(j));
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertTrue(a.isVertexTransitive());
        assertTrue(a.isEdgeTransitive());
        assertTrue(a.isSymmetric());
    }

    // ═══════════════════════════════════════
    // Smallest / largest orbit size
    // ═══════════════════════════════════════

    @Test
    public void emptyGraph_orbitSizes() {
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertEquals(0, a.getLargestOrbitSize());
        assertEquals(0, a.getSmallestOrbitSize());
    }

    @Test
    public void star_orbitSizes() {
        buildStar(5);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        assertEquals(5, a.getLargestOrbitSize());  // leaves
        assertEquals(1, a.getSmallestOrbitSize()); // centre
    }

    // ═══════════════════════════════════════
    // Lazy computation — multiple calls same result
    // ═══════════════════════════════════════

    @Test
    public void lazyComputation_consistentResults() {
        buildCycle(6);
        GraphSymmetryAnalyzer a = new GraphSymmetryAnalyzer(graph);
        // Call methods multiple times — should return same results
        assertEquals(a.getVertexOrbitCount(), a.getVertexOrbitCount());
        assertEquals(a.getEdgeOrbitCount(), a.getEdgeOrbitCount());
        assertEquals(a.isVertexTransitive(), a.isVertexTransitive());
        assertEquals(a.getSymmetryFactor(), a.getSymmetryFactor(), 0.0);
    }
}
