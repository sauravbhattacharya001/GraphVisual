package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link GraphStats}.
 */
public class GraphStatsTest {

    private Graph<String, edge> graph;
    private Vector<edge> friendEdges;
    private Vector<edge> fsEdges;
    private Vector<edge> classmateEdges;
    private Vector<edge> strangerEdges;
    private Vector<edge> studyGEdges;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, edge>();
        friendEdges = new Vector<edge>();
        fsEdges = new Vector<edge>();
        classmateEdges = new Vector<edge>();
        strangerEdges = new Vector<edge>();
        studyGEdges = new Vector<edge>();
    }

    private GraphStats createStats() {
        return new GraphStats(graph, friendEdges, fsEdges,
                classmateEdges, strangerEdges, studyGEdges);
    }

    // ── Helper ──────────────────────────────────────────────────

    private edge makeEdge(String type, String v1, String v2, float weight) {
        edge e = new edge(type, v1, v2);
        e.setWeight(weight);
        return e;
    }

    private void addToGraph(edge e) {
        graph.addEdge(e, e.getVertex1(), e.getVertex2());
    }

    // ── Empty graph ─────────────────────────────────────────────

    @Test
    public void testEmptyGraph() {
        GraphStats stats = createStats();
        assertEquals(0, stats.getNodeCount());
        assertEquals(0, stats.getVisibleEdgeCount());
        assertEquals(0, stats.getTotalEdgeCount());
        assertEquals(0.0, stats.getDensity(), 0.001);
        assertEquals(0.0, stats.getAverageDegree(), 0.001);
        assertEquals(0, stats.getMaxDegree());
        assertEquals(0, stats.getIsolatedNodeCount());
        assertEquals(0.0, stats.getAverageWeight(), 0.001);
    }

    @Test
    public void testEmptyGraph_topNodes_empty() {
        GraphStats stats = createStats();
        assertEquals(0, stats.getTopNodes(5).size());
    }

    @Test
    public void testEmptyGraph_totalEdgeCount_withFilteredEdges() {
        // Edges exist in category lists but graph is empty (all filtered)
        friendEdges.add(makeEdge("f", "A", "B", 10));
        strangerEdges.add(makeEdge("s", "C", "D", 5));
        GraphStats stats = createStats();
        assertEquals(0, stats.getVisibleEdgeCount());
        assertEquals(2, stats.getTotalEdgeCount());
    }

    // ── Single vertex ───────────────────────────────────────────

    @Test
    public void testSingleVertex_isolated() {
        graph.addVertex("A");
        GraphStats stats = createStats();
        assertEquals(1, stats.getNodeCount());
        assertEquals(0, stats.getVisibleEdgeCount());
        assertEquals(0.0, stats.getDensity(), 0.001);
        assertEquals(0.0, stats.getAverageDegree(), 0.001);
        assertEquals(0, stats.getMaxDegree());
        assertEquals(1, stats.getIsolatedNodeCount());
    }

    @Test
    public void testDensitySingleNode() {
        graph.addVertex("A");
        GraphStats stats = createStats();
        assertEquals(0.0, stats.getDensity(), 0.001);
    }

    // ── Single edge ─────────────────────────────────────────────

    @Test
    public void testSingleEdge() {
        edge e = makeEdge("f", "A", "B", 50.0f);
        friendEdges.add(e);
        addToGraph(e);

        GraphStats stats = createStats();
        assertEquals(2, stats.getNodeCount());
        assertEquals(1, stats.getVisibleEdgeCount());
        assertEquals(1, stats.getTotalEdgeCount());
        assertEquals(1, stats.getFriendCount());
        assertEquals(0, stats.getClassmateCount());
    }

    @Test
    public void testSingleEdge_density() {
        // 2 nodes, 1 edge: density = 2*1 / (2*1) = 1.0
        edge e = makeEdge("f", "A", "B", 10);
        friendEdges.add(e);
        addToGraph(e);
        GraphStats stats = createStats();
        assertEquals(1.0, stats.getDensity(), 0.001);
    }

    @Test
    public void testSingleEdge_averageDegree() {
        // 2 nodes, 1 edge: avg = 2*1/2 = 1.0
        edge e = makeEdge("f", "A", "B", 10);
        friendEdges.add(e);
        addToGraph(e);
        GraphStats stats = createStats();
        assertEquals(1.0, stats.getAverageDegree(), 0.001);
    }

    // ── Triangle (complete K3) ──────────────────────────────────

    @Test
    public void testDensityTriangle() {
        edge e1 = makeEdge("f", "A", "B", 10);
        edge e2 = makeEdge("f", "B", "C", 10);
        edge e3 = makeEdge("f", "A", "C", 10);
        addToGraph(e1); addToGraph(e2); addToGraph(e3);
        friendEdges.add(e1); friendEdges.add(e2); friendEdges.add(e3);

        GraphStats stats = createStats();
        assertEquals(1.0, stats.getDensity(), 0.001);
    }

    @Test
    public void testTriangle_averageDegree() {
        // 3 nodes, 3 edges: avg = 2*3/3 = 2.0
        edge e1 = makeEdge("f", "A", "B", 10);
        edge e2 = makeEdge("f", "B", "C", 10);
        edge e3 = makeEdge("f", "A", "C", 10);
        addToGraph(e1); addToGraph(e2); addToGraph(e3);
        friendEdges.add(e1); friendEdges.add(e2); friendEdges.add(e3);

        GraphStats stats = createStats();
        assertEquals(2.0, stats.getAverageDegree(), 0.001);
    }

    @Test
    public void testTriangle_maxDegree() {
        edge e1 = makeEdge("f", "A", "B", 10);
        edge e2 = makeEdge("f", "B", "C", 10);
        edge e3 = makeEdge("f", "A", "C", 10);
        addToGraph(e1); addToGraph(e2); addToGraph(e3);
        friendEdges.add(e1); friendEdges.add(e2); friendEdges.add(e3);

        GraphStats stats = createStats();
        assertEquals(2, stats.getMaxDegree());
    }

    @Test
    public void testTriangle_noIsolatedNodes() {
        edge e1 = makeEdge("f", "A", "B", 10);
        edge e2 = makeEdge("f", "B", "C", 10);
        edge e3 = makeEdge("f", "A", "C", 10);
        addToGraph(e1); addToGraph(e2); addToGraph(e3);
        friendEdges.add(e1); friendEdges.add(e2); friendEdges.add(e3);

        GraphStats stats = createStats();
        assertEquals(0, stats.getIsolatedNodeCount());
    }

    // ── Average degree ──────────────────────────────────────────

    @Test
    public void testAverageDegree() {
        // 3 nodes, 2 edges: degrees are 2, 1, 1 -> avg = 4/3
        edge e1 = makeEdge("f", "A", "B", 10);
        edge e2 = makeEdge("c", "A", "C", 20);
        addToGraph(e1); addToGraph(e2);
        friendEdges.add(e1); classmateEdges.add(e2);

        GraphStats stats = createStats();
        assertEquals(4.0 / 3.0, stats.getAverageDegree(), 0.001);
        assertEquals(2, stats.getMaxDegree());
    }

    @Test
    public void testAverageDegree_fourNodesPath() {
        // A-B-C-D (path): 3 edges, 4 nodes, degrees: 1,2,2,1 avg = 6/4 = 1.5
        edge e1 = makeEdge("f", "A", "B", 10);
        edge e2 = makeEdge("f", "B", "C", 10);
        edge e3 = makeEdge("f", "C", "D", 10);
        addToGraph(e1); addToGraph(e2); addToGraph(e3);
        friendEdges.add(e1); friendEdges.add(e2); friendEdges.add(e3);

        GraphStats stats = createStats();
        assertEquals(1.5, stats.getAverageDegree(), 0.001);
    }

    // ── Top nodes ───────────────────────────────────────────────

    @Test
    public void testTopNodes() {
        edge e1 = makeEdge("f", "A", "B", 10);
        edge e2 = makeEdge("f", "A", "C", 10);
        edge e3 = makeEdge("f", "A", "D", 10);
        addToGraph(e1); addToGraph(e2); addToGraph(e3);
        friendEdges.add(e1); friendEdges.add(e2); friendEdges.add(e3);

        GraphStats stats = createStats();
        List<String> top = stats.getTopNodes(2);
        assertEquals(2, top.size());
        assertTrue(top.get(0).contains("A"));
        assertTrue(top.get(0).contains("3"));
    }

    @Test
    public void testTopNodes_requestMoreThanExist() {
        edge e1 = makeEdge("f", "A", "B", 10);
        addToGraph(e1);
        friendEdges.add(e1);

        GraphStats stats = createStats();
        List<String> top = stats.getTopNodes(10);
        assertEquals(2, top.size()); // only 2 vertices exist
    }

    @Test
    public void testTopNodes_requestZero() {
        edge e1 = makeEdge("f", "A", "B", 10);
        addToGraph(e1);
        friendEdges.add(e1);

        GraphStats stats = createStats();
        List<String> top = stats.getTopNodes(0);
        assertEquals(0, top.size());
    }

    @Test
    public void testTopNodes_requestNegative() {
        edge e1 = makeEdge("f", "A", "B", 10);
        addToGraph(e1);
        friendEdges.add(e1);

        GraphStats stats = createStats();
        List<String> top = stats.getTopNodes(-1);
        assertEquals(0, top.size());
    }

    @Test
    public void testTopNodes_requestOne() {
        edge e1 = makeEdge("f", "A", "B", 10);
        edge e2 = makeEdge("f", "A", "C", 10);
        addToGraph(e1); addToGraph(e2);
        friendEdges.add(e1); friendEdges.add(e2);

        GraphStats stats = createStats();
        List<String> top = stats.getTopNodes(1);
        assertEquals(1, top.size());
        assertTrue(top.get(0).contains("A"));
        assertTrue(top.get(0).contains("2"));
    }

    @Test
    public void testTopNodes_allSameDegree() {
        // Triangle: all have degree 2
        edge e1 = makeEdge("f", "A", "B", 10);
        edge e2 = makeEdge("f", "B", "C", 10);
        edge e3 = makeEdge("f", "A", "C", 10);
        addToGraph(e1); addToGraph(e2); addToGraph(e3);
        friendEdges.add(e1); friendEdges.add(e2); friendEdges.add(e3);

        GraphStats stats = createStats();
        List<String> top = stats.getTopNodes(2);
        assertEquals(2, top.size());
        // All have degree 2
        for (String s : top) {
            assertTrue(s.contains("2"));
        }
    }

    @Test
    public void testTopNodes_onlyIsolatedNodes() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");

        GraphStats stats = createStats();
        List<String> top = stats.getTopNodes(2);
        assertEquals(2, top.size());
        for (String s : top) {
            assertTrue(s.contains("0")); // degree 0
        }
    }

    @Test
    public void testTopNodes_descending() {
        // Hub A connects to 4, B connects to 2, C connects to 1
        edge e1 = makeEdge("f", "A", "X", 10);
        edge e2 = makeEdge("f", "A", "Y", 10);
        edge e3 = makeEdge("f", "A", "Z", 10);
        edge e4 = makeEdge("f", "A", "W", 10);
        edge e5 = makeEdge("f", "B", "X", 10);
        edge e6 = makeEdge("f", "B", "Y", 10);
        addToGraph(e1); addToGraph(e2); addToGraph(e3);
        addToGraph(e4); addToGraph(e5); addToGraph(e6);
        friendEdges.add(e1); friendEdges.add(e2); friendEdges.add(e3);
        friendEdges.add(e4); friendEdges.add(e5); friendEdges.add(e6);

        GraphStats stats = createStats();
        List<String> top = stats.getTopNodes(3);
        assertEquals(3, top.size());
        // First entry should be A with degree 4
        assertTrue(top.get(0).contains("A"));
        assertTrue(top.get(0).contains("4"));
    }

    // ── Isolated nodes ──────────────────────────────────────────

    @Test
    public void testIsolatedNodes() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");

        GraphStats stats = createStats();
        assertEquals(3, stats.getIsolatedNodeCount());
    }

    @Test
    public void testIsolatedNodes_mixedConnectivity() {
        // A-B connected, C and D isolated
        edge e = makeEdge("f", "A", "B", 10);
        addToGraph(e);
        friendEdges.add(e);
        graph.addVertex("C");
        graph.addVertex("D");

        GraphStats stats = createStats();
        assertEquals(2, stats.getIsolatedNodeCount());
    }

    @Test
    public void testIsolatedNodes_noneIsolated() {
        edge e1 = makeEdge("f", "A", "B", 10);
        edge e2 = makeEdge("f", "B", "C", 10);
        addToGraph(e1); addToGraph(e2);
        friendEdges.add(e1); friendEdges.add(e2);

        GraphStats stats = createStats();
        assertEquals(0, stats.getIsolatedNodeCount());
    }

    // ── Average weight ──────────────────────────────────────────

    @Test
    public void testAverageWeight() {
        edge e1 = makeEdge("f", "A", "B", 30.0f);
        edge e2 = makeEdge("s", "C", "D", 10.0f);
        addToGraph(e1); addToGraph(e2);
        friendEdges.add(e1); strangerEdges.add(e2);

        GraphStats stats = createStats();
        assertEquals(20.0, stats.getAverageWeight(), 0.001);
    }

    @Test
    public void testAverageWeight_singleEdge() {
        edge e = makeEdge("f", "A", "B", 42.0f);
        addToGraph(e);
        friendEdges.add(e);

        GraphStats stats = createStats();
        assertEquals(42.0, stats.getAverageWeight(), 0.001);
    }

    @Test
    public void testAverageWeight_zeroWeight() {
        edge e1 = makeEdge("f", "A", "B", 0.0f);
        edge e2 = makeEdge("f", "C", "D", 0.0f);
        addToGraph(e1); addToGraph(e2);
        friendEdges.add(e1); friendEdges.add(e2);

        GraphStats stats = createStats();
        assertEquals(0.0, stats.getAverageWeight(), 0.001);
    }

    @Test
    public void testAverageWeight_noEdges() {
        graph.addVertex("A");
        GraphStats stats = createStats();
        assertEquals(0.0, stats.getAverageWeight(), 0.001);
    }

    @Test
    public void testAverageWeight_multipleEdges() {
        edge e1 = makeEdge("f", "A", "B", 10.0f);
        edge e2 = makeEdge("f", "B", "C", 20.0f);
        edge e3 = makeEdge("f", "C", "D", 30.0f);
        edge e4 = makeEdge("f", "D", "E", 40.0f);
        addToGraph(e1); addToGraph(e2); addToGraph(e3); addToGraph(e4);
        friendEdges.add(e1); friendEdges.add(e2); friendEdges.add(e3); friendEdges.add(e4);

        GraphStats stats = createStats();
        assertEquals(25.0, stats.getAverageWeight(), 0.001); // (10+20+30+40)/4
    }

    // ── Category counts ─────────────────────────────────────────

    @Test
    public void testCategoryCountsIndependent() {
        edge e1 = makeEdge("f", "A", "B", 10);
        edge e2 = makeEdge("fs", "C", "D", 5);
        edge e3 = makeEdge("c", "E", "F", 20);
        edge e4 = makeEdge("s", "G", "H", 2);
        edge e5 = makeEdge("sg", "I", "J", 15);

        friendEdges.add(e1);
        fsEdges.add(e2);
        classmateEdges.add(e3);
        strangerEdges.add(e4);
        studyGEdges.add(e5);

        // Only add some to graph (simulating filtered)
        addToGraph(e1);
        addToGraph(e3);

        GraphStats stats = createStats();
        assertEquals(2, stats.getVisibleEdgeCount());
        assertEquals(5, stats.getTotalEdgeCount());
        assertEquals(1, stats.getFriendCount());
        assertEquals(1, stats.getFsCount());
        assertEquals(1, stats.getClassmateCount());
        assertEquals(1, stats.getStrangerCount());
        assertEquals(1, stats.getStudyGroupCount());
    }

    @Test
    public void testCategoryCount_multipleFriends() {
        edge e1 = makeEdge("f", "A", "B", 10);
        edge e2 = makeEdge("f", "C", "D", 20);
        edge e3 = makeEdge("f", "E", "F", 30);
        friendEdges.add(e1); friendEdges.add(e2); friendEdges.add(e3);
        addToGraph(e1); addToGraph(e2); addToGraph(e3);

        GraphStats stats = createStats();
        assertEquals(3, stats.getFriendCount());
        assertEquals(0, stats.getFsCount());
        assertEquals(0, stats.getClassmateCount());
        assertEquals(0, stats.getStrangerCount());
        assertEquals(0, stats.getStudyGroupCount());
        assertEquals(3, stats.getTotalEdgeCount());
    }

    @Test
    public void testCategoryCount_allZero() {
        GraphStats stats = createStats();
        assertEquals(0, stats.getFriendCount());
        assertEquals(0, stats.getFsCount());
        assertEquals(0, stats.getClassmateCount());
        assertEquals(0, stats.getStrangerCount());
        assertEquals(0, stats.getStudyGroupCount());
        assertEquals(0, stats.getTotalEdgeCount());
    }

    // ── Density edge cases ──────────────────────────────────────

    @Test
    public void testDensity_sparseGraph() {
        // 4 nodes, 1 edge: density = 2*1 / (4*3) = 1/6
        edge e = makeEdge("f", "A", "B", 10);
        addToGraph(e);
        friendEdges.add(e);
        graph.addVertex("C");
        graph.addVertex("D");

        GraphStats stats = createStats();
        assertEquals(1.0 / 6.0, stats.getDensity(), 0.001);
    }

    @Test
    public void testDensity_completeK4() {
        // 4 nodes, 6 edges: density = 2*6 / (4*3) = 1.0
        String[] nodes = {"A", "B", "C", "D"};
        int id = 0;
        for (int i = 0; i < nodes.length; i++) {
            for (int j = i + 1; j < nodes.length; j++) {
                edge e = makeEdge("f", nodes[i], nodes[j], 10);
                e.setLabel("e" + id++);
                addToGraph(e);
                friendEdges.add(e);
            }
        }

        GraphStats stats = createStats();
        assertEquals(1.0, stats.getDensity(), 0.001);
    }

    @Test
    public void testDensity_starGraph() {
        // Center connects to 5 others: 6 nodes, 5 edges
        // density = 2*5 / (6*5) = 10/30 = 1/3
        for (int i = 1; i <= 5; i++) {
            edge e = makeEdge("f", "center", "n" + i, 10);
            e.setLabel("e" + i);
            addToGraph(e);
            friendEdges.add(e);
        }

        GraphStats stats = createStats();
        assertEquals(1.0 / 3.0, stats.getDensity(), 0.001);
    }

    // ── Max degree edge cases ───────────────────────────────────

    @Test
    public void testMaxDegree_starGraph() {
        // Center has degree 5
        for (int i = 1; i <= 5; i++) {
            edge e = makeEdge("f", "hub", "leaf" + i, 10);
            e.setLabel("e" + i);
            addToGraph(e);
            friendEdges.add(e);
        }

        GraphStats stats = createStats();
        assertEquals(5, stats.getMaxDegree());
    }

    @Test
    public void testMaxDegree_emptyGraph() {
        GraphStats stats = createStats();
        assertEquals(0, stats.getMaxDegree());
    }

    @Test
    public void testMaxDegree_onlyIsolated() {
        graph.addVertex("A");
        graph.addVertex("B");
        GraphStats stats = createStats();
        assertEquals(0, stats.getMaxDegree());
    }

    // ── Caching behavior ────────────────────────────────────────

    @Test
    public void testCaching_multipleCallsReturnSameResult() {
        edge e1 = makeEdge("f", "A", "B", 10);
        edge e2 = makeEdge("f", "A", "C", 20);
        addToGraph(e1); addToGraph(e2);
        friendEdges.add(e1); friendEdges.add(e2);

        GraphStats stats = createStats();

        // Call multiple times — should use cache
        assertEquals(2, stats.getMaxDegree());
        assertEquals(2, stats.getMaxDegree());
        assertEquals(0, stats.getIsolatedNodeCount());
        assertEquals(0, stats.getIsolatedNodeCount());

        List<String> top1 = stats.getTopNodes(2);
        List<String> top2 = stats.getTopNodes(2);
        assertEquals(top1.size(), top2.size());
    }

    @Test
    public void testCaching_weightMultipleCalls() {
        edge e1 = makeEdge("f", "A", "B", 15.0f);
        edge e2 = makeEdge("f", "C", "D", 25.0f);
        addToGraph(e1); addToGraph(e2);
        friendEdges.add(e1); friendEdges.add(e2);

        GraphStats stats = createStats();
        assertEquals(20.0, stats.getAverageWeight(), 0.001);
        assertEquals(20.0, stats.getAverageWeight(), 0.001);
    }

    // ── Filtered vs visible edges ───────────────────────────────

    @Test
    public void testFiltered_noEdgesVisible() {
        // All edges in category lists but none in graph
        friendEdges.add(makeEdge("f", "A", "B", 10));
        fsEdges.add(makeEdge("fs", "C", "D", 5));
        classmateEdges.add(makeEdge("c", "E", "F", 20));

        GraphStats stats = createStats();
        assertEquals(0, stats.getVisibleEdgeCount());
        assertEquals(3, stats.getTotalEdgeCount());
        assertEquals(0.0, stats.getAverageDegree(), 0.001);
        assertEquals(0.0, stats.getAverageWeight(), 0.001);
    }

    @Test
    public void testFiltered_partialVisible() {
        edge e1 = makeEdge("f", "A", "B", 30);
        edge e2 = makeEdge("f", "C", "D", 10);
        edge e3 = makeEdge("f", "E", "F", 50); // not added to graph

        friendEdges.add(e1); friendEdges.add(e2); friendEdges.add(e3);
        addToGraph(e1); addToGraph(e2);

        GraphStats stats = createStats();
        assertEquals(2, stats.getVisibleEdgeCount());
        assertEquals(3, stats.getTotalEdgeCount());
        assertEquals(20.0, stats.getAverageWeight(), 0.001); // only visible: (30+10)/2
    }

    // ── Larger graph ────────────────────────────────────────────

    @Test
    public void testLargerGraph_tenNodes() {
        // Build a star: node0 connects to nodes 1-9
        for (int i = 1; i <= 9; i++) {
            edge e = makeEdge("f", "n0", "n" + i, i * 10.0f);
            e.setLabel("e" + i);
            addToGraph(e);
            friendEdges.add(e);
        }

        GraphStats stats = createStats();
        assertEquals(10, stats.getNodeCount());
        assertEquals(9, stats.getVisibleEdgeCount());
        assertEquals(9, stats.getMaxDegree()); // n0 degree
        assertEquals(0, stats.getIsolatedNodeCount());

        // avg degree: 2*9/10 = 1.8
        assertEquals(1.8, stats.getAverageDegree(), 0.001);

        // avg weight: (10+20+...+90)/9 = 450/9 = 50
        assertEquals(50.0, stats.getAverageWeight(), 0.001);

        // density: 2*9 / (10*9) = 18/90 = 0.2
        assertEquals(0.2, stats.getDensity(), 0.001);

        List<String> top = stats.getTopNodes(1);
        assertEquals(1, top.size());
        assertTrue(top.get(0).contains("n0"));
        assertTrue(top.get(0).contains("9"));
    }

    @Test
    public void testLargerGraph_chainOf10() {
        // Linear chain: n0-n1-n2-...-n9
        for (int i = 0; i < 9; i++) {
            edge e = makeEdge("f", "n" + i, "n" + (i + 1), 10);
            e.setLabel("e" + i);
            addToGraph(e);
            friendEdges.add(e);
        }

        GraphStats stats = createStats();
        assertEquals(10, stats.getNodeCount());
        assertEquals(9, stats.getVisibleEdgeCount());
        assertEquals(2, stats.getMaxDegree()); // internal nodes have degree 2
        assertEquals(0, stats.getIsolatedNodeCount());
    }

    // ── Node and edge count accuracy ────────────────────────────

    @Test
    public void testNodeCount_verticesFromEdgesOnly() {
        // Vertices only added implicitly through edges
        edge e = makeEdge("f", "X", "Y", 5);
        addToGraph(e);
        friendEdges.add(e);

        GraphStats stats = createStats();
        assertEquals(2, stats.getNodeCount());
    }

    @Test
    public void testNodeCount_mixedExplicitAndEdge() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        edge e = makeEdge("f", "A", "B", 10);
        addToGraph(e);
        friendEdges.add(e);

        GraphStats stats = createStats();
        assertEquals(3, stats.getNodeCount());
        assertEquals(1, stats.getVisibleEdgeCount());
        assertEquals(1, stats.getIsolatedNodeCount()); // C is isolated
    }

    // ── Mixed categories ────────────────────────────────────────

    @Test
    public void testMixedCategories_allInGraph() {
        edge e1 = makeEdge("f", "A", "B", 10);
        edge e2 = makeEdge("fs", "C", "D", 20);
        edge e3 = makeEdge("c", "E", "F", 30);
        edge e4 = makeEdge("s", "G", "H", 40);
        edge e5 = makeEdge("sg", "I", "J", 50);
        e1.setLabel("e1"); e2.setLabel("e2"); e3.setLabel("e3"); e4.setLabel("e4"); e5.setLabel("e5");

        addToGraph(e1); addToGraph(e2); addToGraph(e3);
        addToGraph(e4); addToGraph(e5);
        friendEdges.add(e1); fsEdges.add(e2); classmateEdges.add(e3);
        strangerEdges.add(e4); studyGEdges.add(e5);

        GraphStats stats = createStats();
        assertEquals(10, stats.getNodeCount());
        assertEquals(5, stats.getVisibleEdgeCount());
        assertEquals(5, stats.getTotalEdgeCount());
        // avg weight: (10+20+30+40+50)/5 = 30
        assertEquals(30.0, stats.getAverageWeight(), 0.001);
    }
}
