package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for GraphQueryEngine — node and edge query builders.
 */
public class GraphQueryEngineTest {

    private Graph<String, Edge> graph;
    private GraphQueryEngine engine;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
        // Build a small test graph:
        //   A --f-- B --c-- C --f-- D
        //           |               |
        //           +------s--------+
        //   E (isolated)
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");
        graph.addVertex("E");

        Edge e1 = new Edge("f", "A", "B"); e1.setWeight(1.0f);
        Edge e2 = new Edge("c", "B", "C"); e2.setWeight(2.5f);
        Edge e3 = new Edge("f", "C", "D"); e3.setWeight(0.5f);
        Edge e4 = new Edge("s", "B", "D"); e4.setWeight(3.0f);

        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");
        graph.addEdge(e3, "C", "D");
        graph.addEdge(e4, "B", "D");

        engine = new GraphQueryEngine(graph);
    }

    @Test
    public void testNodeMinDegree() {
        // B has degree 3, C has degree 2, D has degree 2
        Set<String> result = engine.nodes().withMinDegree(2).results();
        assertTrue(result.contains("B"));
        assertTrue(result.contains("C"));
        assertTrue(result.contains("D"));
        assertFalse(result.contains("A")); // degree 1
        assertFalse(result.contains("E")); // degree 0
    }

    @Test
    public void testNodeIsolated() {
        Set<String> result = engine.nodes().isolated().results();
        assertEquals(1, result.size());
        assertTrue(result.contains("E"));
    }

    @Test
    public void testNodeLeaves() {
        Set<String> result = engine.nodes().leaves().results();
        assertEquals(1, result.size());
        assertTrue(result.contains("A"));
    }

    @Test
    public void testNodeContaining() {
        Set<String> result = engine.nodes().containing("b").results();
        assertEquals(1, result.size());
        assertTrue(result.contains("B"));
    }

    @Test
    public void testNodeConnectedByType() {
        Set<String> result = engine.nodes().connectedByType("s").results();
        assertTrue(result.contains("B"));
        assertTrue(result.contains("D"));
        assertEquals(2, result.size());
    }

    @Test
    public void testNodeNeighborsOf() {
        Set<String> result = engine.nodes().neighborsOf("B").results();
        assertTrue(result.contains("A"));
        assertTrue(result.contains("C"));
        assertTrue(result.contains("D"));
        assertEquals(3, result.size());
    }

    @Test
    public void testNodeSortedByDegree() {
        List<String> sorted = engine.nodes().sortedByDegree();
        assertEquals("B", sorted.get(0)); // highest degree
    }

    @Test
    public void testEdgeOfType() {
        assertEquals(2, engine.edges().ofType("f").count());
        assertEquals(1, engine.edges().ofType("c").count());
        assertEquals(1, engine.edges().ofType("s").count());
    }

    @Test
    public void testEdgeMinWeight() {
        Set<Edge> result = engine.edges().withMinWeight(2.0f).results();
        assertEquals(2, result.size()); // c=2.5, s=3.0
    }

    @Test
    public void testEdgeBetweenNodes() {
        Set<String> subset = new HashSet<>(Arrays.asList("B", "C", "D"));
        Set<Edge> result = engine.edges().betweenNodes(subset).results();
        assertEquals(3, result.size()); // B-C, C-D, B-D
    }

    @Test
    public void testEdgeIncidentTo() {
        Set<Edge> result = engine.edges().incidentTo("A").results();
        assertEquals(1, result.size());
    }

    @Test
    public void testEdgeSortedByWeight() {
        List<Edge> sorted = engine.edges().sortedByWeight();
        assertEquals(3.0f, sorted.get(0).getWeight(), 0.001f);
    }

    @Test
    public void testEdgeTypeBreakdown() {
        Map<String, Integer> breakdown = engine.edges().typeBreakdown();
        assertEquals(Integer.valueOf(2), breakdown.get("f"));
        assertEquals(Integer.valueOf(1), breakdown.get("c"));
        assertEquals(Integer.valueOf(1), breakdown.get("s"));
    }

    @Test
    public void testChainedFilters() {
        // Edges of type "f" with weight >= 1.0
        Set<Edge> result = engine.edges().ofType("f").withMinWeight(1.0f).results();
        assertEquals(1, result.size()); // only A-B (w=1.0), C-D has w=0.5
    }

    @Test
    public void testStats() {
        GraphQueryEngine.QueryStats stats = engine.stats();
        assertEquals(5, stats.nodeCount);
        assertEquals(4, stats.edgeCount);
        assertEquals(0, stats.minDegree);
        assertEquals(3, stats.maxDegree);
    }

    @Test
    public void testNodeSummary() {
        String summary = engine.nodes().isolated().summary();
        assertTrue(summary.contains("1 node"));
        assertTrue(summary.contains("E"));
    }

    @Test
    public void testEdgeSummary() {
        String summary = engine.edges().ofType("s").summary();
        assertTrue(summary.contains("1 edge"));
    }

    @Test
    public void testEmptyResults() {
        assertEquals("No matching nodes.", engine.nodes().withMinDegree(100).summary());
        assertEquals("No matching edges.", engine.edges().ofType("nonexistent").summary());
    }

    @Test
    public void testTemporalEdgeQuery() {
        Edge te = new Edge("f", "A", "E");
        te.setTimestamp(1000L);
        te.setEndTimestamp(2000L);
        graph.addEdge(te, "A", "E");

        assertEquals(1, engine.edges().temporal().count());
        assertEquals(1, engine.edges().activeAt(1500L).count()); // only temporal edge
        assertEquals(0, engine.edges().temporal().activeAt(3000L).count());
    }
}
