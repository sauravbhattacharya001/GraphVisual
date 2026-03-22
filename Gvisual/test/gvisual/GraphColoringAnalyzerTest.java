package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for GraphColoringAnalyzer -- greedy coloring with multiple orderings,
 * DSatur, chromatic bounds, k-colorability, coloring verification, color
 * class analysis, Edge chromatic bounds, and report generation.
 */
public class GraphColoringAnalyzerTest {

    private Graph<String, Edge> graph;
    private int edgeCounter;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, Edge>();
        edgeCounter = 0;
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
        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

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

        assertEquals(1, result.getChromaticBound());
        assertTrue(result.isValid());
    }

    @Test
    public void testTwoConnectedVertices() {
        graph.addVertex("A");
        graph.addVertex("B");
        addEdge("A", "B");

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
        buildTriangle();

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        assertEquals(3, result.getChromaticBound());
        assertTrue(result.isValid());
    }

    @Test
    public void testPathGraphNeedsTwoColors() {
        buildPath("A", "B", "C", "D");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        assertEquals(2, result.getChromaticBound());
        assertTrue(result.isValid());
    }

    @Test
    public void testCompleteGraphK4() {
        buildComplete("A", "B", "C", "D");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        assertEquals(4, result.getChromaticBound());
        assertTrue(result.isValid());
    }

    @Test
    public void testStarGraphNeedsTwoColors() {
        graph.addVertex("center");
        for (int i = 1; i <= 5; i++) {
            String leaf = "leaf" + i;
            graph.addVertex(leaf);
            addEdge("center", leaf);
        }

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        assertEquals(2, result.getChromaticBound());
        assertTrue(result.isValid());
    }

    @Test
    public void testBipartiteGraphK33() {
        String[] left = {"A", "B", "C"};
        String[] right = {"X", "Y", "Z"};
        for (String s : left) graph.addVertex(s);
        for (String s : right) graph.addVertex(s);
        for (String l : left)
            for (String r : right)
                addEdge(l, r);

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        assertEquals(2, result.getChromaticBound());
        assertTrue(result.isValid());
    }

    @Test
    public void testPetersenGraph() {
        buildPetersenGraph();

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        assertEquals(10, result.getVertexCount());
        assertTrue(result.isValid());
        assertTrue(result.getChromaticBound() >= 3);
        assertTrue(result.getChromaticBound() <= 4);
    }

    // ==========================================
    //  DSatur coloring
    // ==========================================

    @Test
    public void testDSaturEmptyGraph() {
        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).computeDSatur();

        assertEquals(0, result.getChromaticBound());
        assertTrue(result.isValid());
    }

    @Test
    public void testDSaturSingleVertex() {
        graph.addVertex("A");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).computeDSatur();

        assertEquals(1, result.getChromaticBound());
        assertTrue(result.isValid());
    }

    @Test
    public void testDSaturTriangle() {
        buildTriangle();

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).computeDSatur();

        assertEquals(3, result.getChromaticBound());
        assertTrue(result.isValid());
    }

    @Test
    public void testDSaturBipartite() {
        buildPath("A", "B", "C", "D", "E");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).computeDSatur();

        assertEquals(2, result.getChromaticBound());
        assertTrue(result.isValid());
    }

    @Test
    public void testDSaturOddCycle() {
        // 5-cycle: needs 3 colors
        buildCycle("A", "B", "C", "D", "E");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).computeDSatur();

        assertEquals(3, result.getChromaticBound());
        assertTrue(result.isValid());
    }

    @Test
    public void testDSaturCompleteK5() {
        buildComplete("A", "B", "C", "D", "E");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).computeDSatur();

        assertEquals(5, result.getChromaticBound());
        assertTrue(result.isValid());
    }

    @Test
    public void testDSaturPetersen() {
        buildPetersenGraph();

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).computeDSatur();

        assertTrue(result.isValid());
        // DSatur should find 3 colors for Petersen graph
        assertTrue(result.getChromaticBound() >= 3);
        assertTrue(result.getChromaticBound() <= 4);
    }

    // ==========================================
    //  Vertex ordering strategies
    // ==========================================

    @Test
    public void testNaturalOrdering() {
        buildTriangle();

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph)
                .computeWithOrdering(GraphColoringAnalyzer.VertexOrdering.NATURAL);

        assertEquals(3, result.getChromaticBound());
        assertTrue(result.isValid());
    }

    @Test
    public void testLargestFirstOrdering() {
        buildMediumGraph();

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph)
                .computeWithOrdering(GraphColoringAnalyzer.VertexOrdering.LARGEST_FIRST);

        assertTrue(result.isValid());
        assertTrue(result.getChromaticBound() >= 2);
    }

    @Test
    public void testSmallestLastOrdering() {
        buildMediumGraph();

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph)
                .computeWithOrdering(GraphColoringAnalyzer.VertexOrdering.SMALLEST_LAST);

        assertTrue(result.isValid());
        assertTrue(result.getChromaticBound() >= 2);
    }

    @Test
    public void testDSaturOrdering() {
        buildMediumGraph();

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph)
                .computeWithOrdering(GraphColoringAnalyzer.VertexOrdering.DSATUR);

        assertTrue(result.isValid());
        assertTrue(result.getChromaticBound() >= 2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullOrderingThrows() {
        new GraphColoringAnalyzer(graph).computeWithOrdering(null);
    }

    @Test
    public void testAllOrderingsProduceValidColorings() {
        buildMediumGraph();
        GraphColoringAnalyzer analyzer = new GraphColoringAnalyzer(graph);

        for (GraphColoringAnalyzer.VertexOrdering ordering :
                GraphColoringAnalyzer.VertexOrdering.values()) {
            GraphColoringAnalyzer.ColoringResult result =
                analyzer.computeWithOrdering(ordering);
            assertTrue("Ordering " + ordering + " produced invalid coloring",
                result.isValid());
        }
    }

    // ==========================================
    //  computeWithOrder (custom)
    // ==========================================

    @Test
    public void testCustomOrderProducesValidColoring() {
        buildTriangle();

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph)
                .computeWithOrder(Arrays.asList("A", "B", "C"));

        assertTrue(result.isValid());
        assertEquals(3, result.getChromaticBound());
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
        addEdge("A", "B");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph)
                .computeWithOrder(Arrays.asList("A", "B"));

        assertEquals(2, result.getVertexCount());
        assertTrue(result.isValid());
    }

    // ==========================================
    //  Chromatic number bounds
    // ==========================================

    @Test
    public void testChromaticLowerBoundEmpty() {
        assertEquals(0, new GraphColoringAnalyzer(graph).chromaticLowerBound());
    }

    @Test
    public void testChromaticLowerBoundTriangle() {
        buildTriangle();
        assertEquals(3, new GraphColoringAnalyzer(graph).chromaticLowerBound());
    }

    @Test
    public void testChromaticLowerBoundK4() {
        buildComplete("A", "B", "C", "D");
        assertEquals(4, new GraphColoringAnalyzer(graph).chromaticLowerBound());
    }

    @Test
    public void testChromaticUpperBound() {
        buildTriangle();
        assertEquals(3, new GraphColoringAnalyzer(graph).chromaticUpperBound());
    }

    @Test
    public void testChromaticBoundsConsistency() {
        buildMediumGraph();
        int[] bounds = new GraphColoringAnalyzer(graph).chromaticBounds();
        assertTrue(bounds[0] <= bounds[1]);
        assertTrue(bounds[0] >= 1);
    }

    @Test
    public void testChromaticBoundsCompleteGraph() {
        buildComplete("A", "B", "C", "D");
        int[] bounds = new GraphColoringAnalyzer(graph).chromaticBounds();
        // For complete graph, bounds should be tight
        assertEquals(4, bounds[0]);
        assertEquals(4, bounds[1]);
    }

    // ==========================================
    //  k-colorability
    // ==========================================

    @Test
    public void testKColorableEmptyGraph() {
        assertTrue(new GraphColoringAnalyzer(graph).isKColorable(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testKColorableNegativeThrows() {
        new GraphColoringAnalyzer(graph).isKColorable(-1);
    }

    @Test
    public void testKColorableZeroWithVertices() {
        graph.addVertex("A");
        assertFalse(new GraphColoringAnalyzer(graph).isKColorable(0));
    }

    @Test
    public void testKColorableTriangleWith2() {
        buildTriangle();
        assertFalse(new GraphColoringAnalyzer(graph).isKColorable(2));
    }

    @Test
    public void testKColorableTriangleWith3() {
        buildTriangle();
        assertTrue(new GraphColoringAnalyzer(graph).isKColorable(3));
    }

    @Test
    public void testKColorableK4With3() {
        buildComplete("A", "B", "C", "D");
        assertFalse(new GraphColoringAnalyzer(graph).isKColorable(3));
    }

    @Test
    public void testKColorableK4With4() {
        buildComplete("A", "B", "C", "D");
        assertTrue(new GraphColoringAnalyzer(graph).isKColorable(4));
    }

    @Test
    public void testKColorableBipartiteWith2() {
        buildPath("A", "B", "C", "D");
        assertTrue(new GraphColoringAnalyzer(graph).isKColorable(2));
    }

    // ==========================================
    //  Coloring verification
    // ==========================================

    @Test
    public void testVerifyValidColoring() {
        buildTriangle();
        Map<String, Integer> assignment = new HashMap<>();
        assignment.put("A", 0);
        assignment.put("B", 1);
        assignment.put("C", 2);

        assertTrue(new GraphColoringAnalyzer(graph).verifyColoring(assignment));
    }

    @Test
    public void testVerifyInvalidColoring() {
        buildTriangle();
        Map<String, Integer> assignment = new HashMap<>();
        assignment.put("A", 0);
        assignment.put("B", 0);
        assignment.put("C", 1);

        assertFalse(new GraphColoringAnalyzer(graph).verifyColoring(assignment));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVerifyNullThrows() {
        new GraphColoringAnalyzer(graph).verifyColoring(null);
    }

    @Test
    public void testFindConflictsNone() {
        buildTriangle();
        Map<String, Integer> assignment = new HashMap<>();
        assignment.put("A", 0);
        assignment.put("B", 1);
        assignment.put("C", 2);

        List<String[]> conflicts =
            new GraphColoringAnalyzer(graph).findConflicts(assignment);
        assertTrue(conflicts.isEmpty());
    }

    @Test
    public void testFindConflictsPresent() {
        buildTriangle();
        Map<String, Integer> assignment = new HashMap<>();
        assignment.put("A", 0);
        assignment.put("B", 0);
        assignment.put("C", 1);

        List<String[]> conflicts =
            new GraphColoringAnalyzer(graph).findConflicts(assignment);
        assertFalse(conflicts.isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindConflictsNullThrows() {
        new GraphColoringAnalyzer(graph).findConflicts(null);
    }

    // ==========================================
    //  Color class analysis
    // ==========================================

    @Test
    public void testAnalyzeColorClassesTriangle() {
        buildTriangle();
        GraphColoringAnalyzer analyzer = new GraphColoringAnalyzer(graph);
        GraphColoringAnalyzer.ColoringResult result = analyzer.compute();
        Map<String, Object> analysis = analyzer.analyzeColorClasses(result);

        assertEquals(3, analysis.get("numColors"));
        assertEquals(3, analysis.get("numVertices"));
        assertEquals(1, analysis.get("largestClass"));
        assertEquals(1, analysis.get("smallestClass"));
        assertEquals(1.0, (Double) analysis.get("balanceRatio"), 0.001);
        assertEquals(true, analysis.get("allIndependent"));
    }

    @Test
    public void testAnalyzeColorClassesStar() {
        graph.addVertex("center");
        for (int i = 1; i <= 5; i++) {
            graph.addVertex("leaf" + i);
            addEdge("center", "leaf" + i);
        }

        GraphColoringAnalyzer analyzer = new GraphColoringAnalyzer(graph);
        GraphColoringAnalyzer.ColoringResult result = analyzer.compute();
        Map<String, Object> analysis = analyzer.analyzeColorClasses(result);

        assertEquals(2, analysis.get("numColors"));
        assertEquals(5, analysis.get("largestClass"));
        assertEquals(1, analysis.get("smallestClass"));
        assertTrue(analysis.containsKey("classSizes"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAnalyzeColorClassesNullThrows() {
        new GraphColoringAnalyzer(graph).analyzeColorClasses(null);
    }

    @Test
    public void testAnalyzeColorClassesEmpty() {
        GraphColoringAnalyzer analyzer = new GraphColoringAnalyzer(graph);
        GraphColoringAnalyzer.ColoringResult result = analyzer.compute();
        Map<String, Object> analysis = analyzer.analyzeColorClasses(result);

        assertEquals(0, analysis.get("numColors"));
        assertEquals(1.0, (Double) analysis.get("balanceRatio"), 0.001);
        assertEquals(true, analysis.get("allIndependent"));
    }

    // ==========================================
    //  Edge chromatic number (Vizing)
    // ==========================================

    @Test
    public void testEdgeChromaticBoundsEmpty() {
        int[] bounds = new GraphColoringAnalyzer(graph).edgeChromaticBounds();
        assertEquals(0, bounds[0]);
        assertEquals(0, bounds[1]);
    }

    @Test
    public void testEdgeChromaticBoundsTriangle() {
        buildTriangle();
        int[] bounds = new GraphColoringAnalyzer(graph).edgeChromaticBounds();
        // Δ = 2, so bounds = [2, 3]
        assertEquals(2, bounds[0]);
        assertEquals(3, bounds[1]);
    }

    @Test
    public void testEdgeChromaticBoundsK4() {
        buildComplete("A", "B", "C", "D");
        int[] bounds = new GraphColoringAnalyzer(graph).edgeChromaticBounds();
        // Δ = 3, so bounds = [3, 4]
        assertEquals(3, bounds[0]);
        assertEquals(4, bounds[1]);
    }

    @Test
    public void testEdgeChromaticBoundsStar() {
        graph.addVertex("center");
        for (int i = 1; i <= 5; i++) {
            graph.addVertex("leaf" + i);
            addEdge("center", "leaf" + i);
        }
        int[] bounds = new GraphColoringAnalyzer(graph).edgeChromaticBounds();
        assertEquals(5, bounds[0]);
        assertEquals(6, bounds[1]);
    }

    @Test
    public void testMaxDegree() {
        graph.addVertex("center");
        for (int i = 1; i <= 4; i++) {
            graph.addVertex("v" + i);
            addEdge("center", "v" + i);
        }
        assertEquals(4, new GraphColoringAnalyzer(graph).maxDegree());
    }

    @Test
    public void testMaxDegreeEmpty() {
        assertEquals(0, new GraphColoringAnalyzer(graph).maxDegree());
    }

    // ==========================================
    //  Color class metrics from ColoringResult
    // ==========================================

    @Test
    public void testGetVerticesWithColor() {
        graph.addVertex("A");
        graph.addVertex("B");
        addEdge("A", "B");

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

    @Test
    public void testLargestClassSize() {
        graph.addVertex("center");
        for (int i = 1; i <= 5; i++) {
            graph.addVertex("leaf" + i);
            addEdge("center", "leaf" + i);
        }
        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();
        assertEquals(5, result.getLargestClassSize());
    }

    @Test
    public void testSmallestClassSize() {
        graph.addVertex("center");
        for (int i = 1; i <= 5; i++) {
            graph.addVertex("leaf" + i);
            addEdge("center", "leaf" + i);
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

    @Test
    public void testColorClassesPartitionVertices() {
        buildMediumGraph();
        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        Set<String> allFromClasses = new HashSet<>();
        for (List<String> cls : result.getColorClasses().values()) {
            for (String v : cls) {
                assertTrue(allFromClasses.add(v));
            }
        }
        assertEquals(graph.getVertexCount(), allFromClasses.size());
    }

    // ==========================================
    //  Summary and toString
    // ==========================================

    @Test
    public void testSummaryContainsExpectedKeys() {
        graph.addVertex("A");
        graph.addVertex("B");
        addEdge("A", "B");

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
    public void testToStringContainsInfo() {
        graph.addVertex("A");
        graph.addVertex("B");
        addEdge("A", "B");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        String str = result.toString();
        assertTrue(str.contains("Graph Coloring Result"));
        assertTrue(str.contains("Vertices: 2"));
        assertTrue(str.contains("chromatic bound"));
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
    //  Report generation
    // ==========================================

    @Test
    public void testGenerateReport() {
        buildMediumGraph();
        String report = new GraphColoringAnalyzer(graph).generateReport();

        assertNotNull(report);
        assertTrue(report.contains("Graph Coloring Analysis Report"));
        assertTrue(report.contains("Greedy Coloring Results"));
        assertTrue(report.contains("Chromatic Number Bounds"));
        assertTrue(report.contains("Edge Chromatic Number"));
        assertTrue(report.contains("DSatur"));
    }

    @Test
    public void testGenerateReportEmpty() {
        String report = new GraphColoringAnalyzer(graph).generateReport();
        assertNotNull(report);
        assertTrue(report.contains("0 vertices"));
    }

    // ==========================================
    //  Disconnected components
    // ==========================================

    @Test
    public void testDisconnectedComponentsSameColors() {
        graph.addVertex("A");
        graph.addVertex("B");
        addEdge("A", "B");
        graph.addVertex("C");
        graph.addVertex("D");
        addEdge("C", "D");

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        assertEquals(2, result.getChromaticBound());
        assertTrue(result.isValid());
    }

    @Test
    public void testDisconnectedIsolatedVertices() {
        for (int i = 0; i < 5; i++) graph.addVertex("v" + i);

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        assertEquals(1, result.getChromaticBound());
        assertTrue(result.isValid());
    }

    // ==========================================
    //  Welsh-Powell behavior
    // ==========================================

    @Test
    public void testWelshPowellPrioritizesHighDegree() {
        graph.addVertex("hub");
        for (int i = 1; i <= 4; i++) {
            graph.addVertex("s" + i);
            addEdge("hub", "s" + i);
        }

        GraphColoringAnalyzer.ColoringResult result =
            new GraphColoringAnalyzer(graph).compute();

        assertEquals(0, result.getColor("hub"));
        assertTrue(result.isValid());
    }

    // ==========================================
    //  Helper methods
    // ==========================================

    private void addEdge(String v1, String v2) {
        Edge e = new Edge("f", v1, v2);
        e.setLabel("e" + (edgeCounter++));
        graph.addEdge(e, v1, v2);
    }

    private void buildTriangle() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
    }

    private void buildPath(String... vertices) {
        for (String v : vertices) graph.addVertex(v);
        for (int i = 0; i < vertices.length - 1; i++) {
            addEdge(vertices[i], vertices[i + 1]);
        }
    }

    private void buildCycle(String... vertices) {
        for (String v : vertices) graph.addVertex(v);
        for (int i = 0; i < vertices.length; i++) {
            addEdge(vertices[i], vertices[(i + 1) % vertices.length]);
        }
    }

    private void buildComplete(String... vertices) {
        for (String v : vertices) graph.addVertex(v);
        for (int i = 0; i < vertices.length; i++) {
            for (int j = i + 1; j < vertices.length; j++) {
                addEdge(vertices[i], vertices[j]);
            }
        }
    }

    private void buildPetersenGraph() {
        String[] outer = {"o0", "o1", "o2", "o3", "o4"};
        String[] inner = {"i0", "i1", "i2", "i3", "i4"};
        for (String v : outer) graph.addVertex(v);
        for (String v : inner) graph.addVertex(v);
        for (int i = 0; i < 5; i++) {
            addEdge(outer[i], outer[(i + 1) % 5]);
        }
        for (int i = 0; i < 5; i++) {
            addEdge(inner[i], inner[(i + 2) % 5]);
        }
        for (int i = 0; i < 5; i++) {
            addEdge(outer[i], inner[i]);
        }
    }

    private void buildMediumGraph() {
        for (int i = 1; i <= 8; i++) graph.addVertex("v" + i);
        addEdge("v1", "v2");
        addEdge("v1", "v3");
        addEdge("v2", "v3");
        addEdge("v2", "v4");
        addEdge("v3", "v5");
        addEdge("v4", "v5");
        addEdge("v4", "v6");
        addEdge("v5", "v7");
        addEdge("v6", "v7");
        addEdge("v6", "v8");
        addEdge("v7", "v8");
    }
}
