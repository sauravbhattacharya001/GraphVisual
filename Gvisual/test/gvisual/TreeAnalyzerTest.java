package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link TreeAnalyzer}.
 */
public class TreeAnalyzerTest {

    private Graph<String, Edge> graph;
    private int edgeId;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
        edgeId = 0;
    }

    private edge addEdge(String v1, String v2) {
        edge e = new Edge("f", v1, v2);
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

    private void buildStar(String center, String... leaves) {
        for (String leaf : leaves) {
            addEdge(center, leaf);
        }
    }

    // ═══════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullGraph_throws() {
        new TreeAnalyzer(null);
    }

    @Test
    public void constructor_validGraph_succeeds() {
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        assertNotNull(ta);
    }

    // ═══════════════════════════════════════
    // Tree Detection
    // ═══════════════════════════════════════

    @Test
    public void checkTree_emptyGraph_isForest() {
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.TreeCheck check = ta.checkTree();
        assertFalse(check.isTree);
        assertTrue(check.isForest);
    }

    @Test
    public void checkTree_singleVertex_isTree() {
        graph.addVertex("A");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.TreeCheck check = ta.checkTree();
        assertTrue(check.isTree);
        assertEquals(1, check.vertexCount);
        assertEquals(0, check.edgeCount);
    }

    @Test
    public void checkTree_singleEdge_isTree() {
        addEdge("A", "B");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.TreeCheck check = ta.checkTree();
        assertTrue(check.isTree);
        assertEquals(2, check.vertexCount);
        assertEquals(1, check.edgeCount);
    }

    @Test
    public void checkTree_path_isTree() {
        buildPath("A", "B", "C", "D", "E");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        assertTrue(ta.checkTree().isTree);
    }

    @Test
    public void checkTree_star_isTree() {
        buildStar("C", "A", "B", "D", "E");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        assertTrue(ta.checkTree().isTree);
    }

    @Test
    public void checkTree_cycle_notTree() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.TreeCheck check = ta.checkTree();
        assertFalse(check.isTree);
        assertFalse(check.isForest);
    }

    @Test
    public void checkTree_forest_notTreeButIsForest() {
        addEdge("A", "B");
        addEdge("C", "D");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.TreeCheck check = ta.checkTree();
        assertFalse(check.isTree);
        assertTrue(check.isForest);
        assertEquals(2, check.componentCount);
    }

    @Test
    public void checkTree_disconnectedWithCycle_notForest() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        graph.addVertex("D");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.TreeCheck check = ta.checkTree();
        assertFalse(check.isTree);
        assertFalse(check.isForest);
    }

    // ═══════════════════════════════════════
    // Center
    // ═══════════════════════════════════════

    @Test
    public void findCenter_singleVertex_isSelf() {
        graph.addVertex("X");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.CenterResult center = ta.findCenter();
        assertEquals(1, center.centerVertices.size());
        assertEquals("X", center.centerVertices.get(0));
        assertEquals(0, center.radius);
    }

    @Test
    public void findCenter_path3_middleVertex() {
        buildPath("A", "B", "C");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.CenterResult center = ta.findCenter();
        assertEquals(1, center.centerVertices.size());
        assertEquals("B", center.centerVertices.get(0));
    }

    @Test
    public void findCenter_path4_twoMiddleVertices() {
        buildPath("A", "B", "C", "D");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.CenterResult center = ta.findCenter();
        assertEquals(2, center.centerVertices.size());
        assertTrue(center.centerVertices.contains("B"));
        assertTrue(center.centerVertices.contains("C"));
    }

    @Test
    public void findCenter_path5_middleVertex() {
        buildPath("A", "B", "C", "D", "E");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.CenterResult center = ta.findCenter();
        assertEquals(1, center.centerVertices.size());
        assertEquals("C", center.centerVertices.get(0));
        assertEquals(2, center.radius);
    }

    @Test
    public void findCenter_star_centerIsHub() {
        buildStar("H", "A", "B", "C", "D");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.CenterResult center = ta.findCenter();
        assertEquals(1, center.centerVertices.size());
        assertEquals("H", center.centerVertices.get(0));
        assertEquals(1, center.radius);
    }

    @Test(expected = IllegalStateException.class)
    public void findCenter_notTree_throws() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        new TreeAnalyzer(graph).findCenter();
    }

    // ═══════════════════════════════════════
    // Centroid
    // ═══════════════════════════════════════

    @Test
    public void findCentroid_singleVertex() {
        graph.addVertex("X");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.CentroidResult result = ta.findCentroid();
        assertEquals(1, result.centroidVertices.size());
        assertEquals("X", result.centroidVertices.get(0));
    }

    @Test
    public void findCentroid_path5_middleVertex() {
        buildPath("A", "B", "C", "D", "E");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.CentroidResult result = ta.findCentroid();
        assertEquals(1, result.centroidVertices.size());
        assertEquals("C", result.centroidVertices.get(0));
        assertEquals(2, result.maxSubtreeSize);
    }

    @Test
    public void findCentroid_star_centerIsHub() {
        buildStar("H", "A", "B", "C", "D", "E");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.CentroidResult result = ta.findCentroid();
        assertTrue(result.centroidVertices.contains("H"));
    }

    @Test
    public void findCentroid_path4_twoCentroids() {
        buildPath("A", "B", "C", "D");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.CentroidResult result = ta.findCentroid();
        assertEquals(2, result.centroidVertices.size());
    }

    // ═══════════════════════════════════════
    // Diameter
    // ═══════════════════════════════════════

    @Test
    public void findDiameter_singleVertex_zero() {
        graph.addVertex("X");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.DiameterResult result = ta.findDiameter();
        assertEquals(0, result.diameter);
    }

    @Test
    public void findDiameter_path5_is4() {
        buildPath("A", "B", "C", "D", "E");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.DiameterResult result = ta.findDiameter();
        assertEquals(4, result.diameter);
        assertEquals(5, result.path.size());
    }

    @Test
    public void findDiameter_star_is2() {
        buildStar("H", "A", "B", "C");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.DiameterResult result = ta.findDiameter();
        assertEquals(2, result.diameter);
    }

    @Test
    public void findDiameter_pathHasCorrectEndpoints() {
        buildPath("A", "B", "C", "D", "E");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.DiameterResult result = ta.findDiameter();
        assertEquals("A", result.path.get(0));
        assertEquals("E", result.path.get(result.path.size() - 1));
    }

    // ═══════════════════════════════════════
    // Rooted tree
    // ═══════════════════════════════════════

    @Test
    public void rootAt_path_correctDepths() {
        buildPath("A", "B", "C", "D");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.RootedTreeInfo info = ta.rootAt("A");
        assertEquals("A", info.root);
        assertEquals(0, (int) info.depth.get("A"));
        assertEquals(1, (int) info.depth.get("B"));
        assertEquals(2, (int) info.depth.get("C"));
        assertEquals(3, (int) info.depth.get("D"));
        assertEquals(3, info.height);
    }

    @Test
    public void rootAt_correctSubtreeSizes() {
        buildPath("A", "B", "C");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.RootedTreeInfo info = ta.rootAt("A");
        assertEquals(3, (int) info.subtreeSize.get("A"));
        assertEquals(2, (int) info.subtreeSize.get("B"));
        assertEquals(1, (int) info.subtreeSize.get("C"));
    }

    @Test
    public void rootAt_correctLeaves() {
        buildStar("H", "A", "B", "C");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.RootedTreeInfo info = ta.rootAt("H");
        assertEquals(3, info.leaves.size());
        assertTrue(info.leaves.contains("A"));
        assertTrue(info.leaves.contains("B"));
        assertTrue(info.leaves.contains("C"));
    }

    @Test
    public void rootAt_correctParents() {
        buildPath("A", "B", "C");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.RootedTreeInfo info = ta.rootAt("A");
        assertNull(info.parent.get("A"));
        assertEquals("A", info.parent.get("B"));
        assertEquals("B", info.parent.get("C"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rootAt_invalidVertex_throws() {
        addEdge("A", "B");
        new TreeAnalyzer(graph).rootAt("Z");
    }

    // ═══════════════════════════════════════
    // Prüfer sequence
    // ═══════════════════════════════════════

    @Test
    public void encodePrufer_path3() {
        buildPath("1", "2", "3");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        List<String> prufer = ta.encodePrufer();
        assertEquals(1, prufer.size());
        assertEquals("2", prufer.get(0));
    }

    @Test
    public void encodePrufer_star() {
        buildStar("3", "1", "2", "4", "5");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        List<String> prufer = ta.encodePrufer();
        assertEquals(3, prufer.size());
        // All entries should be "3" (the hub)
        for (String s : prufer) {
            assertEquals("3", s);
        }
    }

    @Test
    public void encodePrufer_twoVertices_empty() {
        addEdge("1", "2");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        List<String> prufer = ta.encodePrufer();
        assertTrue(prufer.isEmpty());
    }

    @Test
    public void decodePrufer_roundTrip() {
        // Build a tree: 1-2, 2-3, 3-4, 3-5
        addEdge("1", "2");
        addEdge("2", "3");
        addEdge("3", "4");
        addEdge("3", "5");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        List<String> prufer = ta.encodePrufer();
        List<String> vertices = Arrays.asList("1", "2", "3", "4", "5");
        List<String[]> edges = TreeAnalyzer.decodePrufer(prufer, vertices);
        assertEquals(4, edges.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void decodePrufer_wrongLength_throws() {
        TreeAnalyzer.decodePrufer(Arrays.asList("A"), Arrays.asList("A", "B", "C", "D"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void decodePrufer_tooFewVertices_throws() {
        TreeAnalyzer.decodePrufer(Collections.emptyList(), Collections.singletonList("A"));
    }

    // ═══════════════════════════════════════
    // LCA
    // ═══════════════════════════════════════

    @Test
    public void buildLCA_path_queriesCorrect() {
        buildPath("A", "B", "C", "D", "E");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.LCAEngine lca = ta.buildLCA("A");
        assertEquals("A", lca.query("A", "E"));
        assertEquals("B", lca.query("B", "E"));
        assertEquals("C", lca.query("C", "D"));
    }

    @Test
    public void buildLCA_star_queriesCorrect() {
        buildStar("H", "A", "B", "C");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.LCAEngine lca = ta.buildLCA("H");
        assertEquals("H", lca.query("A", "B"));
        assertEquals("H", lca.query("A", "C"));
        assertEquals("A", lca.query("A", "A"));
    }

    @Test
    public void buildLCA_distance_correct() {
        buildPath("A", "B", "C", "D", "E");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.LCAEngine lca = ta.buildLCA("A");
        assertEquals(4, lca.distance("A", "E"));
        assertEquals(2, lca.distance("B", "D"));
        assertEquals(0, lca.distance("C", "C"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildLCA_invalidVertex_throws() {
        addEdge("A", "B");
        new TreeAnalyzer(graph).buildLCA("Z");
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildLCA_queryInvalidVertex_throws() {
        addEdge("A", "B");
        TreeAnalyzer.LCAEngine lca = new TreeAnalyzer(graph).buildLCA("A");
        lca.query("A", "Z");
    }

    // ═══════════════════════════════════════
    // Tree isomorphism
    // ═══════════════════════════════════════

    @Test
    public void canonicalForm_sameTree_sameForm() {
        buildPath("A", "B", "C");
        TreeAnalyzer ta1 = new TreeAnalyzer(graph);
        String form1 = ta1.canonicalForm();

        Graph<String, Edge> graph2 = new UndirectedSparseGraph<>();
        edge e1 = new Edge("f", "X", "Y"); e1.setWeight(1); e1.setLabel("e0");
        edge e2 = new Edge("f", "Y", "Z"); e2.setWeight(1); e2.setLabel("e1");
        graph2.addEdge(e1, "X", "Y");
        graph2.addEdge(e2, "Y", "Z");
        TreeAnalyzer ta2 = new TreeAnalyzer(graph2);
        String form2 = ta2.canonicalForm();

        assertEquals(form1, form2);
    }

    @Test
    public void canonicalForm_differentStructure_differentForm() {
        // Path of 4
        buildPath("A", "B", "C", "D");
        TreeAnalyzer ta1 = new TreeAnalyzer(graph);

        // Star of 4
        Graph<String, Edge> graph2 = new UndirectedSparseGraph<>();
        edge e1 = new Edge("f", "H", "X"); e1.setWeight(1); e1.setLabel("e0");
        edge e2 = new Edge("f", "H", "Y"); e2.setWeight(1); e2.setLabel("e1");
        edge e3 = new Edge("f", "H", "Z"); e3.setWeight(1); e3.setLabel("e2");
        graph2.addEdge(e1, "H", "X");
        graph2.addEdge(e2, "H", "Y");
        graph2.addEdge(e3, "H", "Z");
        TreeAnalyzer ta2 = new TreeAnalyzer(graph2);

        assertNotEquals(ta1.canonicalForm(), ta2.canonicalForm());
    }

    @Test
    public void isIsomorphicTo_samePath_true() {
        buildPath("A", "B", "C", "D");
        TreeAnalyzer ta1 = new TreeAnalyzer(graph);

        Graph<String, Edge> graph2 = new UndirectedSparseGraph<>();
        edge e1 = new Edge("f", "1", "2"); e1.setWeight(1); e1.setLabel("e0");
        edge e2 = new Edge("f", "2", "3"); e2.setWeight(1); e2.setLabel("e1");
        edge e3 = new Edge("f", "3", "4"); e3.setWeight(1); e3.setLabel("e2");
        graph2.addEdge(e1, "1", "2");
        graph2.addEdge(e2, "2", "3");
        graph2.addEdge(e3, "3", "4");
        TreeAnalyzer ta2 = new TreeAnalyzer(graph2);

        assertTrue(ta1.isIsomorphicTo(ta2));
    }

    @Test
    public void isIsomorphicTo_null_false() {
        graph.addVertex("A");
        assertFalse(new TreeAnalyzer(graph).isIsomorphicTo(null));
    }

    @Test
    public void isIsomorphicTo_differentSize_false() {
        buildPath("A", "B", "C");
        TreeAnalyzer ta1 = new TreeAnalyzer(graph);

        Graph<String, Edge> graph2 = new UndirectedSparseGraph<>();
        edge e1 = new Edge("f", "X", "Y"); e1.setWeight(1); e1.setLabel("e0");
        graph2.addEdge(e1, "X", "Y");
        TreeAnalyzer ta2 = new TreeAnalyzer(graph2);

        assertFalse(ta1.isIsomorphicTo(ta2));
    }

    // ═══════════════════════════════════════
    // Degree distribution
    // ═══════════════════════════════════════

    @Test
    public void vertexDegrees_star() {
        buildStar("H", "A", "B", "C");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        Map<String, Integer> degrees = ta.vertexDegrees();
        assertEquals(3, (int) degrees.get("H"));
        assertEquals(1, (int) degrees.get("A"));
    }

    @Test
    public void degreeDistribution_path() {
        buildPath("A", "B", "C", "D", "E");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        Map<Integer, Integer> dist = ta.degreeDistribution();
        assertEquals(2, (int) dist.get(1)); // leaves
        assertEquals(3, (int) dist.get(2)); // internal
    }

    // ═══════════════════════════════════════
    // Full report
    // ═══════════════════════════════════════

    @Test
    public void analyze_tree_fullReport() {
        buildPath("A", "B", "C", "D", "E");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.TreeReport report = ta.analyze();
        assertTrue(report.treeCheck.isTree);
        assertNotNull(report.center);
        assertNotNull(report.centroid);
        assertNotNull(report.diameter);
        assertNotNull(report.rootedInfo);
        assertEquals(4, report.diameter.diameter);
    }

    @Test
    public void analyze_nonTree_partialReport() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.TreeReport report = ta.analyze();
        assertFalse(report.treeCheck.isTree);
        assertNull(report.center);
        assertNull(report.diameter);
    }

    @Test
    public void analyze_toText_notEmpty() {
        buildPath("A", "B", "C", "D", "E");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        String text = ta.analyze().toText();
        assertNotNull(text);
        assertTrue(text.contains("Tree Analysis Report"));
        assertTrue(text.contains("Is tree: true"));
        assertTrue(text.contains("Diameter: 4"));
    }

    // ═══════════════════════════════════════
    // Complex tree shapes
    // ═══════════════════════════════════════

    @Test
    public void complexTree_caterpillar() {
        // Spine: A-B-C-D, pendants off each
        buildPath("A", "B", "C", "D");
        addEdge("A", "A1");
        addEdge("B", "B1");
        addEdge("B", "B2");
        addEdge("C", "C1");
        addEdge("D", "D1");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        assertTrue(ta.checkTree().isTree);
        // Diameter is still through the spine + one pendant at each end
        assertTrue(ta.findDiameter().diameter >= 4);
    }

    @Test
    public void complexTree_binaryTree() {
        //       R
        //      / \
        //     L   R1
        //    / \
        //   LL  LR
        addEdge("R", "L");
        addEdge("R", "R1");
        addEdge("L", "LL");
        addEdge("L", "LR");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        assertTrue(ta.checkTree().isTree);
        TreeAnalyzer.RootedTreeInfo info = ta.rootAt("R");
        assertEquals(2, info.height);
        assertEquals(3, info.leaves.size());
    }

    @Test
    public void lca_complexTree() {
        addEdge("R", "A");
        addEdge("R", "B");
        addEdge("A", "C");
        addEdge("A", "D");
        addEdge("B", "E");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.LCAEngine lca = ta.buildLCA("R");
        assertEquals("A", lca.query("C", "D"));
        assertEquals("R", lca.query("C", "E"));
        assertEquals("R", lca.query("D", "E"));
        assertEquals("A", lca.query("A", "C"));
    }

    @Test
    public void lca_distance_complexTree() {
        addEdge("R", "A");
        addEdge("R", "B");
        addEdge("A", "C");
        addEdge("A", "D");
        addEdge("B", "E");
        TreeAnalyzer ta = new TreeAnalyzer(graph);
        TreeAnalyzer.LCAEngine lca = ta.buildLCA("R");
        assertEquals(2, lca.distance("C", "D"));
        assertEquals(4, lca.distance("C", "E"));
        assertEquals(1, lca.distance("R", "A"));
    }
}
