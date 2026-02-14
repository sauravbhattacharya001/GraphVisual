package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

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
    public void testSingleEdge() {
        edge e = new edge("f", "A", "B");
        e.setWeight(50.0f);
        friendEdges.add(e);
        graph.addEdge(e, "A", "B");

        GraphStats stats = createStats();
        assertEquals(2, stats.getNodeCount());
        assertEquals(1, stats.getVisibleEdgeCount());
        assertEquals(1, stats.getTotalEdgeCount());
        assertEquals(1, stats.getFriendCount());
        assertEquals(0, stats.getClassmateCount());
    }

    @Test
    public void testDensityTriangle() {
        // 3 nodes, 3 edges = complete graph, density = 1.0
        edge e1 = new edge("f", "A", "B");
        edge e2 = new edge("f", "B", "C");
        edge e3 = new edge("f", "A", "C");
        e1.setWeight(10); e2.setWeight(10); e3.setWeight(10);
        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");
        graph.addEdge(e3, "A", "C");
        friendEdges.add(e1); friendEdges.add(e2); friendEdges.add(e3);

        GraphStats stats = createStats();
        assertEquals(1.0, stats.getDensity(), 0.001);
    }

    @Test
    public void testAverageDegree() {
        // 3 nodes, 2 edges: degrees are 2, 1, 1 -> avg = 4/3
        edge e1 = new edge("f", "A", "B");
        edge e2 = new edge("c", "A", "C");
        e1.setWeight(10); e2.setWeight(20);
        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "A", "C");
        friendEdges.add(e1);
        classmateEdges.add(e2);

        GraphStats stats = createStats();
        assertEquals(4.0 / 3.0, stats.getAverageDegree(), 0.001);
        assertEquals(2, stats.getMaxDegree());
    }

    @Test
    public void testTopNodes() {
        edge e1 = new edge("f", "A", "B");
        edge e2 = new edge("f", "A", "C");
        edge e3 = new edge("f", "A", "D");
        e1.setWeight(10); e2.setWeight(10); e3.setWeight(10);
        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "A", "C");
        graph.addEdge(e3, "A", "D");
        friendEdges.add(e1); friendEdges.add(e2); friendEdges.add(e3);

        GraphStats stats = createStats();
        List<String> top = stats.getTopNodes(2);
        assertEquals(2, top.size());
        // A has degree 3, should be first
        assertTrue(top.get(0).contains("A"));
        assertTrue(top.get(0).contains("3"));
    }

    @Test
    public void testIsolatedNodes() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        // no edges

        GraphStats stats = createStats();
        assertEquals(3, stats.getIsolatedNodeCount());
    }

    @Test
    public void testAverageWeight() {
        edge e1 = new edge("f", "A", "B");
        edge e2 = new edge("s", "C", "D");
        e1.setWeight(30.0f);
        e2.setWeight(10.0f);
        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "C", "D");
        friendEdges.add(e1);
        strangerEdges.add(e2);

        GraphStats stats = createStats();
        assertEquals(20.0, stats.getAverageWeight(), 0.001);
    }

    @Test
    public void testCategoryCountsIndependent() {
        edge e1 = new edge("f", "A", "B"); e1.setWeight(10);
        edge e2 = new edge("fs", "C", "D"); e2.setWeight(5);
        edge e3 = new edge("c", "E", "F"); e3.setWeight(20);
        edge e4 = new edge("s", "G", "H"); e4.setWeight(2);
        edge e5 = new edge("sg", "I", "J"); e5.setWeight(15);

        friendEdges.add(e1);
        fsEdges.add(e2);
        classmateEdges.add(e3);
        strangerEdges.add(e4);
        studyGEdges.add(e5);

        // Only add some to graph (simulating filtered)
        graph.addEdge(e1, "A", "B");
        graph.addEdge(e3, "E", "F");

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
    public void testDensitySingleNode() {
        graph.addVertex("A");
        GraphStats stats = createStats();
        assertEquals(0.0, stats.getDensity(), 0.001);
    }
}
