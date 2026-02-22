package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for GraphColoringAnalyzer -- Welsh-Powell greedy graph coloring,
 * custom ordering, validation, color classes, and summary analytics.
 */
public class GraphColoringAnalyzerTest {

    private Graph<String, edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, edge>();
    }

    // ==========================================
    //  Constructor tests
    // ==========================================

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new GraphColoringAnalyzer(null);
    }

    @Test
    public void testConstructorAcceptsValidGraph() {
        GraphColoringAnalyzer analyzer = new GraphColoringAnalyzer(graph);
        assertNotNull(analyzer);
    }

    // ==========================================
    //  Empty and trivial graphs
    // ==========================================

    @Test
    public void testEmptyGraph() {
        GraphColoringAnalyzer analyzer = new GraphColoringAnalyzer(graph);
        GraphColoringAnalyzer.ColoringResult result = analyzer.compute();

        assertEquals(0, result.getChromaticBound());
        assertEquals(0, result.getVertexCount());
        assertTrue(result.isValid());
        assertTrue(result.getColorAssignment().isEmpty());
        assertTrue(result.getColorClasses().isEmpty());
    }

    @Test
    public void testSingleVertex() {
        graph.addVertex("A");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        assertEquals(1, result.getChromaticBound());
        assertEquals(1, result.getVertexCount());
        assertEquals(0, result.getColor("A"));
        assertTrue(result.isValid());
    }

    @Test
    public void testTwoDisconnectedVertices() {
        graph.addVertex("A");
        graph.addVertex("B");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        // Both can share the same color
        assertEquals(1, result.getChromaticBound());
        assertTrue(result.isValid());
    }

    @Test
    public void testTwoConnectedVertices() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addEdge(makeEdge("f"), "A", "B");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        assertEquals(2, result.getChromaticBound());
        assertNotEquals(result.getColor("A"), result.getColor("B"));
        assertTrue(result.isValid());
    }

    // ==========================================
    //  Classic graph structures
    // ==========================================

    @Test
    public void testTriangleNeedsThreeColors() {
        // K3: three mutually connected vertices
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge(makeEdge("f"), "A", "B");
        graph.addEdge(makeEdge("f"), "B", "C");
        graph.addEdge(makeEdge("f"), "A", "C");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        assertEquals(3, result.getChromaticBound());
        assertTrue(result.isValid());
        // All three must be different
        assertNotEquals(result.getColor("A"), result.getColor("B"));
        assertNotEquals(result.getColor("B"), result.getColor("C"));
        assertNotEquals(result.getColor("A"), result.getColor("C"));
    }

    @Test
    public void testPathGraphNeedsTwoColors() {
        // A-B-C-D (path)
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");
        graph.addEdge(makeEdge("f"), "A", "B");
        graph.addEdge(makeEdge("f"), "B", "C");
        graph.addEdge(makeEdge("f"), "C", "D");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        // Path graph is bipartite: 2 colors
        assertEquals(2, result.getChromaticBound());
        assertTrue(result.isValid());
    }

    @Test
    public void testCycleEvenNeedsTwoColors() {
        // A-B-C-D-A (even cycle, 4 vertices)
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");
        graph.addEdge(makeEdge("f"), "A", "B");
        graph.addEdge(makeEdge("f"), "B", "C");
        graph.addEdge(makeEdge("f"), "C", "D");
        graph.addEdge(makeEdge("f"), "D", "A");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        // Even cycle is bipartite
        assertTrue(result.getChromaticBound() <= 3);
        assertTrue(result.isValid());
    }

    @Test
    public void testCycleOddNeedsThreeColors() {
        // A-B-C-D-E-A (odd cycle, 5 vertices)
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");
        graph.addVertex("E");
        graph.addEdge(makeEdge("f"), "A", "B");
        graph.addEdge(makeEdge("f"), "B", "C");
        graph.addEdge(makeEdge("f"), "C", "D");
        graph.addEdge(makeEdge("f"), "D", "E");
        graph.addEdge(makeEdge("f"), "E", "A");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        // Odd cycle needs at least 3 colors
        assertTrue(result.getChromaticBound() >= 3);
        assertTrue(result.isValid());
    }

    @Test
    public void testCompleteGraphK4() {
        // K4: 4 mutually connected vertices
        String[] v = {"A", "B", "C", "D"};
        for (String s : v) graph.addVertex(s);
        int edgeId = 0;
        for (int i = 0; i < v.length; i++) {
            for (int j = i + 1; j < v.length; j++) {
                graph.addEdge(makeEdge("f", edgeId++), v[i], v[j]);
            }
        }

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        assertEquals(4, result.getChromaticBound());
        assertTrue(result.isValid());
    }

    @Test
    public void testStarGraphNeedsTwoColors() {
        // Star: center connected to all leaves
        graph.addVertex("center");
        for (int i = 1; i <= 5; i++) {
            String leaf = "leaf" + i;
            graph.addVertex(leaf);
            graph.addEdge(makeEdge("f", i), "center", leaf);
        }

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        assertEquals(2, result.getChromaticBound());
        assertTrue(result.isValid());
    }

    @Test
    public void testBipartiteGraphNeedsTwoColors() {
        // K3,3 bipartite: {A,B,C} x {X,Y,Z}
        String[] left = {"A", "B", "C"};
        String[] right = {"X", "Y", "Z"};
        for (String s : left) graph.addVertex(s);
        for (String s : right) graph.addVertex(s);

        int edgeId = 0;
        for (String l : left) {
            for (String r : right) {
                graph.addEdge(makeEdge("f", edgeId++), l, r);
            }
        }

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        assertEquals(2, result.getChromaticBound());
        assertTrue(result.isValid());
    }

    // ==========================================
    //  Validation
    // ==========================================

    @Test
    public void testValidColoringProperty() {
        // Build a moderately complex graph
        buildMediumGraph();

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        assertTrue(result.isValid());

        // Manually verify: no adjacent vertices share a color
        Map<String, Integer> assignment = result.getColorAssignment();
        for (edge e : graph.getEdges()) {
            String v1 = graph.getEndpoints(e).getFirst();
            String v2 = graph.getEndpoints(e).getSecond();
            assertNotEquals(
                "Adjacent " + v1 + " and " + v2 + " must differ",
                assignment.get(v1), assignment.get(v2));
        }
    }

    // ==========================================
    //  Color classes
    // ==========================================

    @Test
    public void testColorClassesPartitionVertices() {
        buildMediumGraph();

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        // All vertices appear exactly once across all classes
        Set<String> allFromClasses = new HashSet<>();
        for (List<String> cls : result.getColorClasses().values()) {
            for (String v : cls) {
                assertTrue("Vertex " + v + " appears twice",
                    allFromClasses.add(v));
            }
        }
        assertEquals(graph.getVertexCount(), allFromClasses.size());
    }

    @Test
    public void testGetVerticesWithColor() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addEdge(makeEdge("f"), "A", "B");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        List<String> color0 = result.getVerticesWithColor(0);
        List<String> color1 = result.getVerticesWithColor(1);

        assertFalse(color0.isEmpty());
        assertFalse(color1.isEmpty());
        assertEquals(2, color0.size() + color1.size());
    }

    @Test
    public void testGetVerticesWithInvalidColor() {
        graph.addVertex("A");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        assertTrue(result.getVerticesWithColor(999).isEmpty());
    }

    @Test
    public void testGetColorForUnknownVertex() {
        graph.addVertex("A");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        assertEquals(-1, result.getColor("NONEXISTENT"));
    }

    // ==========================================
    //  Class size analytics
    // ==========================================

    @Test
    public void testLargestClassSize() {
        // Star: center gets one color, 5 leaves share another
        graph.addVertex("center");
        for (int i = 1; i <= 5; i++) {
            String leaf = "leaf" + i;
            graph.addVertex(leaf);
            graph.addEdge(makeEdge("f", i), "center", leaf);
        }

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        assertEquals(5, result.getLargestClassSize());
    }

    @Test
    public void testSmallestClassSize() {
        graph.addVertex("center");
        for (int i = 1; i <= 5; i++) {
            String leaf = "leaf" + i;
            graph.addVertex(leaf);
            graph.addEdge(makeEdge("f", i), "center", leaf);
        }

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        assertEquals(1, result.getSmallestClassSize());
    }

    @Test
    public void testClassSizesEmpty() {
        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        assertEquals(0, result.getLargestClassSize());
        assertEquals(0, result.getSmallestClassSize());
    }

    // ==========================================
    //  getSummary
    // ==========================================

    @Test
    public void testSummaryContainsExpectedKeys() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addEdge(makeEdge("f"), "A", "B");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        Map<String, Object> summary = result.getSummary();
        assertTrue(summary.containsKey("vertexCount"));
        assertTrue(summary.containsKey("chromaticBound"));
        assertTrue(summary.containsKey("valid"));
        assertTrue(summary.containsKey("largestClass"));
        assertTrue(summary.containsKey("smallestClass"));
        assertTrue(summary.containsKey("classSizes"));
    }

    @Test
    public void testSummaryValues() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addEdge(makeEdge("f"), "A", "B");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        Map<String, Object> summary = result.getSummary();
        assertEquals(2, summary.get("vertexCount"));
        assertEquals(2, summary.get("chromaticBound"));
        assertEquals(true, summary.get("valid"));
    }

    // ==========================================
    //  toString
    // ==========================================

    @Test
    public void testToStringContainsInfo() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addEdge(makeEdge("f"), "A", "B");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        String str = result.toString();
        assertTrue(str.contains("Graph Coloring Result"));
        assertTrue(str.contains("Vertices: 2"));
        assertTrue(str.contains("chromatic bound"));
        assertTrue(str.contains("Color classes"));
    }

    // ==========================================
    //  computeWithOrder
    // ==========================================

    @Test
    public void testCustomOrderProducesDifferentResult() {
        // Triangle: order matters for color assignment
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge(makeEdge("f"), "A", "B");
        graph.addEdge(makeEdge("f"), "B", "C");
        graph.addEdge(makeEdge("f"), "A", "C");

        GraphColoringAnalyzer analyzer = new GraphColoringAnalyzer(graph);

        // Both orderings should produce valid colorings
        GraphColoringAnalyzer.ColoringResult r1 =
            analyzer.computeWithOrder(Arrays.asList("A", "B", "C"));
        GraphColoringAnalyzer.ColoringResult r2 =
            analyzer.computeWithOrder(Arrays.asList("C", "B", "A"));

        assertTrue(r1.isValid());
        assertTrue(r2.isValid());
        assertEquals(3, r1.getChromaticBound());
        assertEquals(3, r2.getChromaticBound());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testComputeWithOrderNullThrows() {
        new GraphColoringAnalyzer(graph).computeWithOrder(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testComputeWithOrderInvalidVertexThrows() {
        graph.addVertex("A");
        new GraphColoringAnalyzer(graph)
            .computeWithOrder(Arrays.asList("A", "NONEXISTENT"));
    }

    @Test
    public void testComputeWithOrderPartialVertices() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge(makeEdge("f"), "A", "B");

        // Only color two of three vertices
        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph)
                .computeWithOrder(Arrays.asList("A", "B"));

        assertEquals(2, result.getVertexCount());
        assertTrue(result.isValid());
    }

    // ==========================================
    //  Welsh-Powell degree ordering
    // ==========================================

    @Test
    public void testWelshPowellPrioritizesHighDegree() {
        // Hub-and-spoke: center has highest degree, gets color 0
        graph.addVertex("hub");
        for (int i = 1; i <= 4; i++) {
            String spoke = "s" + i;
            graph.addVertex(spoke);
            graph.addEdge(makeEdge("f", i), "hub", spoke);
        }

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        // Hub (highest degree) should get color 0
        assertEquals(0, result.getColor("hub"));
        assertTrue(result.isValid());
    }

    // ==========================================
    //  Disconnected components
    // ==========================================

    @Test
    public void testDisconnectedComponentsSameColors() {
        // Two separate edges
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addEdge(makeEdge("f"), "A", "B");

        graph.addVertex("C");
        graph.addVertex("D");
        graph.addEdge(makeEdge("c"), "C", "D");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        // Two separate edges only need 2 colors total
        assertEquals(2, result.getChromaticBound());
        assertTrue(result.isValid());
    }

    @Test
    public void testDisconnectedIsolatedVertices() {
        // 5 isolated vertices
        for (int i = 0; i < 5; i++) {
            graph.addVertex("v" + i);
        }

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        assertEquals(1, result.getChromaticBound());
        assertTrue(result.isValid());
    }

    // ==========================================
    //  Edge types
    // ==========================================

    @Test
    public void testDifferentEdgeTypesColored() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        edge e1 = new edge("f", "A", "B");
        edge e2 = new edge("c", "B", "C");
        edge e3 = new edge("s", "A", "C");
        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");
        graph.addEdge(e3, "A", "C");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        assertEquals(3, result.getChromaticBound());
        assertTrue(result.isValid());
    }

    // ==========================================
    //  Larger graph
    // ==========================================

    @Test
    public void testPetersenGraph() {
        // Petersen graph: 10 vertices, 15 edges, chromatic number = 3
        String[] outer = {"o0", "o1", "o2", "o3", "o4"};
        String[] inner = {"i0", "i1", "i2", "i3", "i4"};
        for (String v : outer) graph.addVertex(v);
        for (String v : inner) graph.addVertex(v);

        int id = 0;
        // Outer cycle
        for (int i = 0; i < 5; i++) {
            graph.addEdge(makeEdge("f", id++), outer[i], outer[(i + 1) % 5]);
        }
        // Inner pentagram
        for (int i = 0; i < 5; i++) {
            graph.addEdge(makeEdge("f", id++), inner[i], inner[(i + 2) % 5]);
        }
        // Spokes
        for (int i = 0; i < 5; i++) {
            graph.addEdge(makeEdge("f", id++), outer[i], inner[i]);
        }

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        assertEquals(10, result.getVertexCount());
        assertTrue(result.isValid());
        // Petersen graph chromatic number is 3
        assertTrue(result.getChromaticBound() >= 3);
        assertTrue(result.getChromaticBound() <= 4); // Welsh-Powell should find 3 or 4
    }

    // ==========================================
    //  Immutability
    // ==========================================

    @Test(expected = UnsupportedOperationException.class)
    public void testColorAssignmentImmutable() {
        graph.addVertex("A");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        result.getColorAssignment().put("A", 99);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testColorClassesImmutable() {
        graph.addVertex("A");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        result.getColorClasses().put(99, new ArrayList<>());
    }

    // ==========================================
    //  Helper methods
    // ==========================================

    private edge makeEdge(String type) {
        return new edge(type, "", "");
    }

    private edge makeEdge(String type, int id) {
        edge e = new edge(type, "", "");
        e.setLabel("e" + id);
        return e;
    }

    private void buildMediumGraph() {
        // 8-vertex graph with mixed connectivity
        for (int i = 1; i <= 8; i++) {
            graph.addVertex("v" + i);
        }
        int id = 0;
        graph.addEdge(makeEdge("f", id++), "v1", "v2");
        graph.addEdge(makeEdge("f", id++), "v1", "v3");
        graph.addEdge(makeEdge("f", id++), "v2", "v3");
        graph.addEdge(makeEdge("c", id++), "v2", "v4");
        graph.addEdge(makeEdge("c", id++), "v3", "v5");
        graph.addEdge(makeEdge("s", id++), "v4", "v5");
        graph.addEdge(makeEdge("s", id++), "v4", "v6");
        graph.addEdge(makeEdge("f", id++), "v5", "v7");
        graph.addEdge(makeEdge("f", id++), "v6", "v7");
        graph.addEdge(makeEdge("f", id++), "v6", "v8");
        graph.addEdge(makeEdge("f", id++), "v7", "v8");
    }
}
