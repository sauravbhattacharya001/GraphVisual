package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link TreewidthAnalyzer}.
 */
public class TreewidthAnalyzerTest {

    private Graph<String, Edge> graph;
    private int edgeId;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
        edgeId = 0;
    }

    private Edge addEdge(String v1, String v2) {
        Edge e = new Edge("f", v1, v2);
        e.setWeight(1.0f);
        e.setLabel("e" + (edgeId++));
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        graph.addEdge(e, v1, v2);
        return e;
    }

    private void buildPath(String... vertices) {
        for (int i = 0; i < vertices.length - 1; i++) {
            addEdge(vertices[i], vertices[i + 1]);
        }
    }

    private void buildCycle(String... vertices) {
        buildPath(vertices);
        addEdge(vertices[vertices.length - 1], vertices[0]);
    }

    private void buildComplete(int n) {
        for (int i = 0; i < n; i++) {
            graph.addVertex("v" + i);
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                addEdge("v" + i, "v" + j);
            }
        }
    }

    // === Empty and trivial graphs ===

    @Test
    public void testEmptyGraph() {
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertEquals(0, analyzer.getBestUpperBound());
        assertEquals(0, analyzer.computeDegeneracy());
    }

    @Test
    public void testSingleVertex() {
        graph.addVertex("A");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertEquals(0, analyzer.getBestUpperBound());
        assertEquals(0, analyzer.computeExactTreewidth());
    }

    @Test
    public void testSingleEdge() {
        addEdge("A", "B");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertEquals(1, analyzer.getBestUpperBound());
    }

    @Test
    public void testNullGraph() {
        try {
            new TreewidthAnalyzer(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    // === Trees (treewidth = 1) ===

    @Test
    public void testPathTreewidth() {
        buildPath("A", "B", "C", "D", "E");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertEquals(1, analyzer.getBestUpperBound());
    }

    @Test
    public void testStarTreewidth() {
        for (int i = 0; i < 5; i++) addEdge("center", "leaf" + i);
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertEquals(1, analyzer.getBestUpperBound());
    }

    @Test
    public void testBinaryTreeTreewidth() {
        addEdge("A", "B"); addEdge("A", "C");
        addEdge("B", "D"); addEdge("B", "E");
        addEdge("C", "F"); addEdge("C", "G");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertEquals(1, analyzer.getBestUpperBound());
    }

    @Test
    public void testTreeExactTreewidth() {
        buildPath("A", "B", "C", "D");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertEquals(1, analyzer.computeExactTreewidth());
    }

    // === Cycles (treewidth = 2) ===

    @Test
    public void testTriangleTreewidth() {
        buildCycle("A", "B", "C");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertTrue(analyzer.getBestUpperBound() >= 2);
    }

    @Test
    public void testCycle4Treewidth() {
        buildCycle("A", "B", "C", "D");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertEquals(2, analyzer.computeExactTreewidth());
    }

    @Test
    public void testCycle5Treewidth() {
        buildCycle("A", "B", "C", "D", "E");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertEquals(2, analyzer.computeExactTreewidth());
    }

    // === Complete graphs (treewidth = n-1) ===

    @Test
    public void testK3Treewidth() {
        buildComplete(3);
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertEquals(2, analyzer.computeExactTreewidth());
    }

    @Test
    public void testK4Treewidth() {
        buildComplete(4);
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertEquals(3, analyzer.computeExactTreewidth());
    }

    @Test
    public void testK5Treewidth() {
        buildComplete(5);
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertEquals(4, analyzer.computeExactTreewidth());
    }

    @Test
    public void testK4UpperBound() {
        buildComplete(4);
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertEquals(3, analyzer.getBestUpperBound());
    }

    // === Degeneracy ===

    @Test
    public void testDegeneracyPath() {
        buildPath("A", "B", "C", "D", "E");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertEquals(1, analyzer.computeDegeneracy());
    }

    @Test
    public void testDegeneracyComplete() {
        buildComplete(4);
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertEquals(3, analyzer.computeDegeneracy());
    }

    @Test
    public void testDegeneracyCycle() {
        buildCycle("A", "B", "C", "D");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertEquals(2, analyzer.computeDegeneracy());
    }

    @Test
    public void testDegeneracyEmpty() {
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertEquals(0, analyzer.computeDegeneracy());
    }

    // === MMD Lower Bound ===

    @Test
    public void testMMDPath() {
        buildPath("A", "B", "C", "D");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertTrue(analyzer.computeMMDLowerBound() >= 1);
    }

    @Test
    public void testMMDComplete() {
        buildComplete(4);
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertTrue(analyzer.computeMMDLowerBound() >= 2);
    }

    @Test
    public void testMMDEmpty() {
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertEquals(0, analyzer.computeMMDLowerBound());
    }

    // === Decomposition methods ===

    @Test
    public void testGreedyDegreeDecomposition() {
        buildPath("A", "B", "C", "D");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        TreewidthAnalyzer.TreeDecomposition td = analyzer.greedyDegreeDecomposition();
        assertNotNull(td);
        assertEquals("greedy-degree", td.getMethod());
        assertTrue(td.getWidth() >= 1);
    }

    @Test
    public void testMinFillDecomposition() {
        buildPath("A", "B", "C", "D");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        TreewidthAnalyzer.TreeDecomposition td = analyzer.minFillDecomposition();
        assertNotNull(td);
        assertEquals("min-fill", td.getMethod());
        assertTrue(td.getWidth() >= 1);
    }

    @Test
    public void testMinWidthDecomposition() {
        buildCycle("A", "B", "C", "D");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        TreewidthAnalyzer.TreeDecomposition td = analyzer.minWidthDecomposition();
        assertNotNull(td);
        assertEquals("min-width", td.getMethod());
    }

    @Test
    public void testDecompositionNumBags() {
        buildPath("A", "B", "C", "D", "E");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        TreewidthAnalyzer.TreeDecomposition td = analyzer.greedyDegreeDecomposition();
        assertTrue(td.getNumBags() > 0);
    }

    @Test
    public void testDecompositionMaxBagSize() {
        buildComplete(4);
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        TreewidthAnalyzer.TreeDecomposition td = analyzer.greedyDegreeDecomposition();
        assertTrue(td.getMaxBagSize() >= 3);
    }

    @Test
    public void testDecompositionAvgBagSize() {
        buildPath("A", "B", "C");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        TreewidthAnalyzer.TreeDecomposition td = analyzer.greedyDegreeDecomposition();
        assertTrue(td.getAvgBagSize() > 0);
    }

    @Test
    public void testEmptyGraphDecomposition() {
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        TreewidthAnalyzer.TreeDecomposition td = analyzer.greedyDegreeDecomposition();
        assertEquals(0, td.getWidth());
        assertEquals(0, td.getNumBags());
    }

    // === Validation ===

    @Test
    public void testValidatePathDecomposition() {
        buildPath("A", "B", "C", "D");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        TreewidthAnalyzer.TreeDecomposition td = analyzer.greedyDegreeDecomposition();
        assertTrue(analyzer.validateDecomposition(td));
    }

    @Test
    public void testValidateCycleDecomposition() {
        buildCycle("A", "B", "C", "D");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        TreewidthAnalyzer.TreeDecomposition td = analyzer.minFillDecomposition();
        assertTrue(analyzer.validateDecomposition(td));
    }

    @Test
    public void testValidateCompleteDecomposition() {
        buildComplete(5);
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        TreewidthAnalyzer.TreeDecomposition td = analyzer.greedyDegreeDecomposition();
        assertTrue(analyzer.validateDecomposition(td));
    }

    @Test
    public void testValidateEmptyDecomposition() {
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        TreewidthAnalyzer.TreeDecomposition td = analyzer.greedyDegreeDecomposition();
        assertTrue(analyzer.validateDecomposition(td));
    }

    // === Nice Tree Decomposition ===

    @Test
    public void testNiceDecompositionPath() {
        buildPath("A", "B", "C");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        TreewidthAnalyzer.TreeDecomposition td = analyzer.greedyDegreeDecomposition();
        TreewidthAnalyzer.TreeDecomposition nice = analyzer.toNiceDecomposition(td);
        assertNotNull(nice);
        assertEquals("nice", nice.getMethod());
        assertTrue(nice.getNumBags() > 0);
    }

    @Test
    public void testNiceDecompositionHasCorrectTypes() {
        buildPath("A", "B", "C", "D");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        TreewidthAnalyzer.TreeDecomposition td = analyzer.greedyDegreeDecomposition();
        TreewidthAnalyzer.TreeDecomposition nice = analyzer.toNiceDecomposition(td);
        Set<String> types = new HashSet<>();
        for (TreewidthAnalyzer.Bag bag : nice.getBags()) {
            types.add(bag.getType());
        }
        assertTrue(types.contains("leaf") || types.contains("introduce") || types.contains("forget") || types.contains("root"));
    }

    @Test
    public void testNiceDecompositionEmpty() {
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        TreewidthAnalyzer.TreeDecomposition td = analyzer.greedyDegreeDecomposition();
        TreewidthAnalyzer.TreeDecomposition nice = analyzer.toNiceDecomposition(td);
        assertEquals(0, nice.getNumBags());
    }

    @Test
    public void testNiceDecompositionSingleVertex() {
        graph.addVertex("A");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        TreewidthAnalyzer.TreeDecomposition td = analyzer.greedyDegreeDecomposition();
        TreewidthAnalyzer.TreeDecomposition nice = analyzer.toNiceDecomposition(td);
        assertTrue(nice.getNumBags() >= 1);
    }

    // === Pathwidth ===

    @Test
    public void testPathwidthPath() {
        buildPath("A", "B", "C", "D", "E");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        int pw = analyzer.computePathwidthUpperBound();
        assertTrue(pw >= 1);
    }

    @Test
    public void testPathwidthCycle() {
        buildCycle("A", "B", "C", "D");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        int pw = analyzer.computePathwidthUpperBound();
        assertTrue(pw >= 2);
    }

    @Test
    public void testPathwidthComplete() {
        buildComplete(4);
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        int pw = analyzer.computePathwidthUpperBound();
        assertTrue(pw >= 3);
    }

    @Test
    public void testPathwidthEmpty() {
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertEquals(0, analyzer.computePathwidthUpperBound());
    }

    @Test
    public void testPathwidthSingleVertex() {
        graph.addVertex("A");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertEquals(0, analyzer.computePathwidthUpperBound());
    }

    @Test
    public void testPathwidthGeTreewidth() {
        buildCycle("A", "B", "C", "D", "E");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertTrue(analyzer.computePathwidthUpperBound() >= analyzer.getBestLowerBound());
    }

    // === Classification ===

    @Test
    public void testClassifyEmpty() {
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertTrue(analyzer.classifyByTreewidth().contains("empty"));
    }

    @Test
    public void testClassifySingleVertex() {
        graph.addVertex("A");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertTrue(analyzer.classifyByTreewidth().contains("single vertex"));
    }

    @Test
    public void testClassifyTree() {
        buildPath("A", "B", "C", "D");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        String cls = analyzer.classifyByTreewidth();
        assertTrue(cls.contains("tree") || cls.contains("tw=1") || cls.contains("tw≤1"));
    }

    @Test
    public void testClassifyCompleteGraph() {
        buildComplete(5);
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        String cls = analyzer.classifyByTreewidth();
        assertTrue(cls.contains("complete") || cls.contains("dense") || cls.contains("tw=4"));
    }

    // === Bag analysis ===

    @Test
    public void testBagIntersections() {
        buildPath("A", "B", "C", "D");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        TreewidthAnalyzer.TreeDecomposition td = analyzer.greedyDegreeDecomposition();
        Map<String, Integer> analysis = analyzer.analyzeBagIntersections(td);
        assertTrue(analysis.containsKey("numBags"));
        assertTrue(analysis.containsKey("maxBagSize"));
    }

    @Test
    public void testWidthProfile() {
        buildPath("A", "B", "C", "D");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        TreewidthAnalyzer.TreeDecomposition td = analyzer.greedyDegreeDecomposition();
        Map<Integer, Integer> profile = analyzer.getWidthProfile(td);
        assertFalse(profile.isEmpty());
        int totalBags = profile.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(td.getNumBags(), totalBags);
    }

    // === Bounds relationship ===

    @Test
    public void testLowerBoundLeUpperBound() {
        buildCycle("A", "B", "C", "D", "E");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertTrue(analyzer.getBestLowerBound() <= analyzer.getBestUpperBound());
    }

    @Test
    public void testLowerBoundLeUpperBoundComplete() {
        buildComplete(5);
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertTrue(analyzer.getBestLowerBound() <= analyzer.getBestUpperBound());
    }

    @Test
    public void testExactBetweenBounds() {
        buildCycle("A", "B", "C", "D");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        int exact = analyzer.computeExactTreewidth();
        assertTrue(exact >= analyzer.getBestLowerBound());
        assertTrue(exact <= analyzer.getBestUpperBound());
    }

    // === Grid graph ===

    @Test
    public void testGrid2x3Treewidth() {
        // 2x3 grid: tw = 2
        addEdge("00", "01"); addEdge("01", "02");
        addEdge("10", "11"); addEdge("11", "12");
        addEdge("00", "10"); addEdge("01", "11"); addEdge("02", "12");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertEquals(2, analyzer.computeExactTreewidth());
    }

    @Test
    public void testGrid3x3Treewidth() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                String v = i + "" + j;
                graph.addVertex(v);
                if (j > 0) addEdge(i + "" + (j - 1), v);
                if (i > 0) addEdge((i - 1) + "" + j, v);
            }
        }
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        int exact = analyzer.computeExactTreewidth();
        assertEquals(3, exact);
    }

    // === Report generation ===

    @Test
    public void testGenerateReport() {
        buildCycle("A", "B", "C", "D");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        TreewidthAnalyzer.TreewidthReport report = analyzer.generateReport();
        assertNotNull(report);
        assertTrue(report.lowerBound <= report.upperBound);
        assertNotNull(report.bestDecomposition);
        assertNotNull(report.classification);
    }

    @Test
    public void testGenerateReportSmallGraph() {
        buildPath("A", "B", "C");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        TreewidthAnalyzer.TreewidthReport report = analyzer.generateReport();
        assertTrue(report.exactTreewidth >= 0);
    }

    @Test
    public void testGenerateTextReport() {
        buildCycle("A", "B", "C", "D");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        String text = analyzer.generateTextReport();
        assertNotNull(text);
        assertTrue(text.contains("Treewidth Analysis Report"));
        assertTrue(text.contains("Lower bound"));
        assertTrue(text.contains("Upper bound"));
    }

    @Test
    public void testReportHeuristicResults() {
        buildComplete(4);
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        TreewidthAnalyzer.TreewidthReport report = analyzer.generateReport();
        assertTrue(report.heuristicResults.containsKey("greedy-degree"));
        assertTrue(report.heuristicResults.containsKey("min-fill"));
    }

    @Test
    public void testReportNiceDecomposition() {
        buildPath("A", "B", "C", "D");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        TreewidthAnalyzer.TreewidthReport report = analyzer.generateReport();
        assertNotNull(report.niceDecomposition);
        assertEquals("nice", report.niceDecomposition.getMethod());
    }

    // === Bag class tests ===

    @Test
    public void testBagConstruction() {
        Set<String> verts = new LinkedHashSet<>(Arrays.asList("A", "B", "C"));
        TreewidthAnalyzer.Bag bag = new TreewidthAnalyzer.Bag(0, verts);
        assertEquals(0, bag.getId());
        assertEquals(3, bag.size());
        assertTrue(bag.getVertices().contains("A"));
    }

    @Test
    public void testBagParentChild() {
        TreewidthAnalyzer.Bag bag = new TreewidthAnalyzer.Bag(0, Collections.singleton("A"));
        assertEquals(-1, bag.getParent());
        bag.setParent(5);
        assertEquals(5, bag.getParent());
        bag.addChild(1);
        bag.addChild(2);
        assertEquals(2, bag.getChildren().size());
    }

    @Test
    public void testBagType() {
        TreewidthAnalyzer.Bag bag = new TreewidthAnalyzer.Bag(0, Collections.singleton("A"));
        assertEquals("normal", bag.getType());
        bag.setType("leaf");
        assertEquals("leaf", bag.getType());
    }

    @Test
    public void testBagToString() {
        Set<String> verts = new LinkedHashSet<>(Arrays.asList("X", "Y"));
        TreewidthAnalyzer.Bag bag = new TreewidthAnalyzer.Bag(3, verts);
        String str = bag.toString();
        assertTrue(str.contains("Bag3"));
        assertTrue(str.contains("X"));
    }

    // === Disconnected graphs ===

    @Test
    public void testDisconnectedGraph() {
        addEdge("A", "B");
        addEdge("C", "D");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertEquals(1, analyzer.getBestUpperBound());
    }

    @Test
    public void testDisconnectedPathwidth() {
        addEdge("A", "B"); addEdge("B", "C");
        addEdge("X", "Y"); addEdge("Y", "Z");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        int pw = analyzer.computePathwidthUpperBound();
        assertTrue(pw >= 1);
    }

    // === Large graph (no exact) ===

    @Test
    public void testLargeGraphNoExact() {
        for (int i = 0; i < 20; i++) {
            graph.addVertex("v" + i);
            if (i > 0) addEdge("v" + (i - 1), "v" + i);
        }
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertEquals(-1, analyzer.computeExactTreewidth());
    }

    // === TreeDecomposition class ===

    @Test
    public void testTreeDecompositionGetters() {
        buildPath("A", "B", "C");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        TreewidthAnalyzer.TreeDecomposition td = analyzer.greedyDegreeDecomposition();
        assertTrue(td.getWidth() >= 0);
        assertTrue(td.getNumBags() > 0);
        assertTrue(td.getMaxBagSize() > 0);
        assertTrue(td.getAvgBagSize() > 0);
        assertNotNull(td.getBags());
        assertNotNull(td.getMethod());
    }

    // === Isolated vertices ===

    @Test
    public void testIsolatedVertices() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        assertEquals(0, analyzer.getBestUpperBound());
    }

    // === Petersen-like structure ===

    @Test
    public void testWheelGraph() {
        // Wheel W5: center + 5-cycle
        for (int i = 0; i < 5; i++) {
            addEdge("center", "v" + i);
            addEdge("v" + i, "v" + ((i + 1) % 5));
        }
        TreewidthAnalyzer analyzer = new TreewidthAnalyzer(graph);
        int tw = analyzer.computeExactTreewidth();
        assertEquals(3, tw);
    }
}
