package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Set;

/**
 * Tests for ShortestPathFinder — BFS and Dijkstra path finding
 * on JUNG UndirectedSparseGraph.
 *
 * @author zalenix
 */
public class ShortestPathFinderTest {

    private Graph<String, edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, edge>();
    }

    private edge makeEdge(String type, String v1, String v2, float weight) {
        edge e = new edge(type, v1, v2);
        e.setWeight(weight);
        return e;
    }

    // --- Constructor tests ---

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new ShortestPathFinder(null);
    }

    @Test
    public void testEmptyGraph() {
        ShortestPathFinder finder = new ShortestPathFinder(graph);
        assertNotNull(finder);
    }

    // --- findShortestByHops tests ---

    @Test
    public void testSameSourceAndTarget() {
        graph.addVertex("A");
        ShortestPathFinder finder = new ShortestPathFinder(graph);
        ShortestPathFinder.PathResult result = finder.findShortestByHops("A", "A");
        assertNotNull(result);
        assertEquals(0, result.getHopCount());
        assertEquals(1, result.getVertices().size());
        assertEquals("A", result.getVertices().get(0));
        assertEquals(0.0, result.getTotalWeight(), 0.001);
    }

    @Test
    public void testDirectConnection() {
        graph.addVertex("A");
        graph.addVertex("B");
        edge e = makeEdge("f", "A", "B", 5.0f);
        graph.addEdge(e, "A", "B");

        ShortestPathFinder finder = new ShortestPathFinder(graph);
        ShortestPathFinder.PathResult result = finder.findShortestByHops("A", "B");

        assertNotNull(result);
        assertEquals(1, result.getHopCount());
        assertEquals(2, result.getVertices().size());
        assertEquals("A", result.getVertices().get(0));
        assertEquals("B", result.getVertices().get(1));
        assertEquals(5.0, result.getTotalWeight(), 0.001);
    }

    @Test
    public void testMultiHopPath() {
        // A -- B -- C -- D
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");

        graph.addEdge(makeEdge("f", "A", "B", 1.0f), "A", "B");
        graph.addEdge(makeEdge("f", "B", "C", 2.0f), "B", "C");
        graph.addEdge(makeEdge("f", "C", "D", 3.0f), "C", "D");

        ShortestPathFinder finder = new ShortestPathFinder(graph);
        ShortestPathFinder.PathResult result = finder.findShortestByHops("A", "D");

        assertNotNull(result);
        assertEquals(3, result.getHopCount());
        assertEquals(4, result.getVertices().size());
        assertEquals("A", result.getVertices().get(0));
        assertEquals("D", result.getVertices().get(3));
        assertEquals(6.0, result.getTotalWeight(), 0.001);
    }

    @Test
    public void testShortestHopsNotWeightOptimal() {
        // A--B direct (weight 100), A--C--B (weight 1+1=2)
        // BFS should find A--B (1 hop) even though it's heavier
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");

        graph.addEdge(makeEdge("f", "A", "B", 100.0f), "A", "B");
        graph.addEdge(makeEdge("f", "A", "C", 1.0f), "A", "C");
        graph.addEdge(makeEdge("f", "C", "B", 1.0f), "C", "B");

        ShortestPathFinder finder = new ShortestPathFinder(graph);
        ShortestPathFinder.PathResult result = finder.findShortestByHops("A", "B");

        assertNotNull(result);
        assertEquals(1, result.getHopCount()); // Direct is fewest hops
    }

    @Test
    public void testNoPathExists() {
        graph.addVertex("A");
        graph.addVertex("B");
        // No edge between them

        ShortestPathFinder finder = new ShortestPathFinder(graph);
        ShortestPathFinder.PathResult result = finder.findShortestByHops("A", "B");
        assertNull(result);
    }

    @Test
    public void testDisconnectedComponents() {
        // Component 1: A--B
        // Component 2: C--D
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");

        graph.addEdge(makeEdge("f", "A", "B", 1.0f), "A", "B");
        graph.addEdge(makeEdge("f", "C", "D", 1.0f), "C", "D");

        ShortestPathFinder finder = new ShortestPathFinder(graph);
        assertNull(finder.findShortestByHops("A", "C"));
        assertNull(finder.findShortestByHops("B", "D"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullSourceThrows() {
        graph.addVertex("A");
        ShortestPathFinder finder = new ShortestPathFinder(graph);
        finder.findShortestByHops(null, "A");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVertexNotInGraphThrows() {
        graph.addVertex("A");
        ShortestPathFinder finder = new ShortestPathFinder(graph);
        finder.findShortestByHops("A", "Z");
    }

    // --- findShortestByWeight tests ---

    @Test
    public void testWeightOptimalDifferentFromHopOptimal() {
        // A--B (weight 100) vs A--C--B (weight 1+1=2)
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");

        graph.addEdge(makeEdge("f", "A", "B", 100.0f), "A", "B");
        graph.addEdge(makeEdge("f", "A", "C", 1.0f), "A", "C");
        graph.addEdge(makeEdge("f", "C", "B", 1.0f), "C", "B");

        ShortestPathFinder finder = new ShortestPathFinder(graph);
        ShortestPathFinder.PathResult result = finder.findShortestByWeight("A", "B");

        assertNotNull(result);
        assertEquals(2, result.getHopCount()); // 2 hops via C
        assertEquals(2.0, result.getTotalWeight(), 0.001);
    }

    @Test
    public void testWeightSameSourceTarget() {
        graph.addVertex("A");
        ShortestPathFinder finder = new ShortestPathFinder(graph);
        ShortestPathFinder.PathResult result = finder.findShortestByWeight("A", "A");
        assertNotNull(result);
        assertEquals(0, result.getHopCount());
    }

    @Test
    public void testWeightNoPath() {
        graph.addVertex("A");
        graph.addVertex("B");
        ShortestPathFinder finder = new ShortestPathFinder(graph);
        assertNull(finder.findShortestByWeight("A", "B"));
    }

    @Test
    public void testWeightDiamond() {
        //     B (weight 2+2=4)
        //    / \
        //   A   D
        //    \ /
        //     C (weight 1+1=2)
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");

        graph.addEdge(makeEdge("f", "A", "B", 2.0f), "A", "B");
        graph.addEdge(makeEdge("f", "B", "D", 2.0f), "B", "D");
        graph.addEdge(makeEdge("f", "A", "C", 1.0f), "A", "C");
        graph.addEdge(makeEdge("f", "C", "D", 1.0f), "C", "D");

        ShortestPathFinder finder = new ShortestPathFinder(graph);
        ShortestPathFinder.PathResult result = finder.findShortestByWeight("A", "D");

        assertNotNull(result);
        assertEquals(2.0, result.getTotalWeight(), 0.001);
        assertTrue(result.getVertices().contains("C")); // Goes through C
    }

    // --- getReachableVertices tests ---

    @Test
    public void testReachableFromIsolated() {
        graph.addVertex("A");
        ShortestPathFinder finder = new ShortestPathFinder(graph);
        Set<String> reachable = finder.getReachableVertices("A");
        assertEquals(1, reachable.size());
        assertTrue(reachable.contains("A"));
    }

    @Test
    public void testReachableInComponent() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D"); // isolated

        graph.addEdge(makeEdge("f", "A", "B", 1.0f), "A", "B");
        graph.addEdge(makeEdge("f", "B", "C", 1.0f), "B", "C");

        ShortestPathFinder finder = new ShortestPathFinder(graph);
        Set<String> reachable = finder.getReachableVertices("A");

        assertEquals(3, reachable.size());
        assertTrue(reachable.contains("A"));
        assertTrue(reachable.contains("B"));
        assertTrue(reachable.contains("C"));
        assertFalse(reachable.contains("D"));
    }

    // --- areConnected tests ---

    @Test
    public void testAreConnectedTrue() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addEdge(makeEdge("f", "A", "B", 1.0f), "A", "B");

        ShortestPathFinder finder = new ShortestPathFinder(graph);
        assertTrue(finder.areConnected("A", "B"));
    }

    @Test
    public void testAreConnectedFalse() {
        graph.addVertex("A");
        graph.addVertex("B");

        ShortestPathFinder finder = new ShortestPathFinder(graph);
        assertFalse(finder.areConnected("A", "B"));
    }

    @Test
    public void testAreConnectedSelf() {
        graph.addVertex("A");
        ShortestPathFinder finder = new ShortestPathFinder(graph);
        assertTrue(finder.areConnected("A", "A"));
    }

    // --- PathResult tests ---

    @Test
    public void testPathResultToString() {
        graph.addVertex("A");
        graph.addVertex("B");
        edge e = makeEdge("f", "A", "B", 5.0f);
        graph.addEdge(e, "A", "B");

        ShortestPathFinder finder = new ShortestPathFinder(graph);
        ShortestPathFinder.PathResult result = finder.findShortestByHops("A", "B");

        String str = result.toString();
        assertTrue(str.contains("A"));
        assertTrue(str.contains("B"));
        assertTrue(str.contains("→"));
        assertTrue(str.contains("1 hop"));
        assertTrue(str.contains("5.0"));
    }

    @Test
    public void testPathResultImmutable() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addEdge(makeEdge("f", "A", "B", 1.0f), "A", "B");

        ShortestPathFinder finder = new ShortestPathFinder(graph);
        ShortestPathFinder.PathResult result = finder.findShortestByHops("A", "B");

        try {
            result.getVertices().add("C");
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }

        try {
            result.getEdges().clear();
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    // --- Edge type diversity ---

    @Test
    public void testPathAcrossEdgeTypes() {
        // A --(friend)-- B --(classmate)-- C --(stranger)-- D
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");

        graph.addEdge(makeEdge("f", "A", "B", 3.0f), "A", "B");
        graph.addEdge(makeEdge("c", "B", "C", 5.0f), "B", "C");
        graph.addEdge(makeEdge("s", "C", "D", 2.0f), "C", "D");

        ShortestPathFinder finder = new ShortestPathFinder(graph);
        ShortestPathFinder.PathResult result = finder.findShortestByHops("A", "D");

        assertNotNull(result);
        assertEquals(3, result.getHopCount());
        assertEquals(10.0, result.getTotalWeight(), 0.001);

        // Check edge types
        assertEquals("f", result.getEdges().get(0).getType());
        assertEquals("c", result.getEdges().get(1).getType());
        assertEquals("s", result.getEdges().get(2).getType());
    }

    // --- Bidirectional (undirected) ---

    @Test
    public void testBidirectionalPath() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addEdge(makeEdge("f", "A", "B", 1.0f), "A", "B");

        ShortestPathFinder finder = new ShortestPathFinder(graph);

        // A to B
        ShortestPathFinder.PathResult ab = finder.findShortestByHops("A", "B");
        assertNotNull(ab);
        assertEquals(1, ab.getHopCount());

        // B to A (should also work — undirected)
        ShortestPathFinder.PathResult ba = finder.findShortestByHops("B", "A");
        assertNotNull(ba);
        assertEquals(1, ba.getHopCount());
    }

    // --- Star topology ---

    @Test
    public void testStarTopology() {
        // Center: H. Spokes: A,B,C,D,E,F,G
        graph.addVertex("H");
        String[] spokes = {"A", "B", "C", "D", "E", "F", "G"};
        for (String s : spokes) {
            graph.addVertex(s);
            graph.addEdge(makeEdge("f", "H", s, 1.0f), "H", s);
        }

        ShortestPathFinder finder = new ShortestPathFinder(graph);

        // Any spoke to any other spoke should be 2 hops via H
        ShortestPathFinder.PathResult result = finder.findShortestByHops("A", "G");
        assertNotNull(result);
        assertEquals(2, result.getHopCount());
        assertEquals("H", result.getVertices().get(1));
    }
}
