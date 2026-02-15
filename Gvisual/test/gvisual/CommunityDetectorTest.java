package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for CommunityDetector — connected component detection,
 * community metrics, and modularity scoring.
 */
public class CommunityDetectorTest {

    private Graph<String, edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, edge>();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new CommunityDetector(null);
    }

    @Test
    public void testEmptyGraph() {
        CommunityDetector detector = new CommunityDetector(graph);
        CommunityDetector.DetectionResult result = detector.detect();
        assertEquals(0, result.getCommunityCount());
        assertTrue(result.getCommunities().isEmpty());
    }

    @Test
    public void testSingleNode() {
        graph.addVertex("A");
        CommunityDetector detector = new CommunityDetector(graph);
        CommunityDetector.DetectionResult result = detector.detect();
        assertEquals(1, result.getCommunityCount());
        assertEquals(1, result.getCommunities().get(0).getSize());
        assertTrue(result.getCommunities().get(0).getMembers().contains("A"));
    }

    @Test
    public void testTwoDisconnectedNodes() {
        graph.addVertex("A");
        graph.addVertex("B");
        CommunityDetector detector = new CommunityDetector(graph);
        CommunityDetector.DetectionResult result = detector.detect();
        assertEquals(2, result.getCommunityCount());
        // Both communities should have 1 member each
        assertEquals(1, result.getCommunities().get(0).getSize());
        assertEquals(1, result.getCommunities().get(1).getSize());
    }

    @Test
    public void testTwoConnectedNodes() {
        graph.addVertex("A");
        graph.addVertex("B");
        edge e = new edge("f", "A", "B");
        e.setWeight(5.0f);
        graph.addEdge(e, "A", "B");

        CommunityDetector detector = new CommunityDetector(graph);
        CommunityDetector.DetectionResult result = detector.detect();
        assertEquals(1, result.getCommunityCount());
        assertEquals(2, result.getCommunities().get(0).getSize());
        assertEquals(1, result.getCommunities().get(0).getInternalEdges());
    }

    @Test
    public void testTwoCommunities() {
        // Community 1: A-B-C
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        edge e1 = new edge("f", "A", "B");
        e1.setWeight(3.0f);
        edge e2 = new edge("f", "B", "C");
        e2.setWeight(4.0f);
        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");

        // Community 2: D-E
        graph.addVertex("D");
        graph.addVertex("E");
        edge e3 = new edge("c", "D", "E");
        e3.setWeight(6.0f);
        graph.addEdge(e3, "D", "E");

        CommunityDetector detector = new CommunityDetector(graph);
        CommunityDetector.DetectionResult result = detector.detect();
        assertEquals(2, result.getCommunityCount());

        // Largest community first
        assertEquals(3, result.getCommunities().get(0).getSize());
        assertEquals(2, result.getCommunities().get(1).getSize());
    }

    @Test
    public void testNodeToCommunityMapping() {
        graph.addVertex("A");
        graph.addVertex("B");
        edge e1 = new edge("f", "A", "B");
        e1.setWeight(1.0f);
        graph.addEdge(e1, "A", "B");

        graph.addVertex("C");

        CommunityDetector detector = new CommunityDetector(graph);
        CommunityDetector.DetectionResult result = detector.detect();

        // A and B should be in same community
        assertEquals(result.getNodeToCommunity().get("A"),
                     result.getNodeToCommunity().get("B"));
        // C should be in a different community
        assertNotEquals(result.getNodeToCommunity().get("A"),
                        result.getNodeToCommunity().get("C"));
    }

    @Test
    public void testCommunityDensity() {
        // Complete graph of 3 nodes: density should be 1.0
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        edge e1 = new edge("f", "A", "B"); e1.setWeight(1.0f);
        edge e2 = new edge("f", "B", "C"); e2.setWeight(1.0f);
        edge e3 = new edge("f", "A", "C"); e3.setWeight(1.0f);
        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");
        graph.addEdge(e3, "A", "C");

        CommunityDetector detector = new CommunityDetector(graph);
        CommunityDetector.DetectionResult result = detector.detect();
        assertEquals(1.0, result.getCommunities().get(0).getDensity(), 0.001);
    }

    @Test
    public void testCommunityDensitySingleNode() {
        graph.addVertex("A");
        CommunityDetector detector = new CommunityDetector(graph);
        CommunityDetector.DetectionResult result = detector.detect();
        assertEquals(0.0, result.getCommunities().get(0).getDensity(), 0.001);
    }

    @Test
    public void testEdgeTypeCounts() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        edge e1 = new edge("f", "A", "B"); e1.setWeight(1.0f);
        edge e2 = new edge("c", "B", "C"); e2.setWeight(2.0f);
        edge e3 = new edge("f", "A", "C"); e3.setWeight(3.0f);
        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");
        graph.addEdge(e3, "A", "C");

        CommunityDetector detector = new CommunityDetector(graph);
        CommunityDetector.DetectionResult result = detector.detect();
        CommunityDetector.Community c = result.getCommunities().get(0);

        assertEquals(2, c.getEdgeTypeCounts().get("f").intValue());
        assertEquals(1, c.getEdgeTypeCounts().get("c").intValue());
        assertEquals("f", c.getDominantType());
    }

    @Test
    public void testAverageWeight() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        edge e1 = new edge("f", "A", "B"); e1.setWeight(10.0f);
        edge e2 = new edge("f", "B", "C"); e2.setWeight(20.0f);
        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");

        CommunityDetector detector = new CommunityDetector(graph);
        CommunityDetector.DetectionResult result = detector.detect();
        assertEquals(15.0, result.getCommunities().get(0).getAverageWeight(), 0.001);
    }

    @Test
    public void testSignificantCommunities() {
        // 3 communities: one with 3 nodes, one with 2, one with 1
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        edge e1 = new edge("f", "A", "B"); e1.setWeight(1.0f);
        edge e2 = new edge("f", "B", "C"); e2.setWeight(1.0f);
        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");

        graph.addVertex("D");
        graph.addVertex("E");
        edge e3 = new edge("c", "D", "E"); e3.setWeight(1.0f);
        graph.addEdge(e3, "D", "E");

        graph.addVertex("F"); // isolated

        CommunityDetector detector = new CommunityDetector(graph);
        CommunityDetector.DetectionResult result = detector.detect();

        assertEquals(3, result.getCommunityCount());
        List<CommunityDetector.Community> significant = result.getSignificantCommunities(2);
        assertEquals(2, significant.size());
        List<CommunityDetector.Community> large = result.getSignificantCommunities(3);
        assertEquals(1, large.size());
    }

    @Test
    public void testGetCommunityOf() {
        graph.addVertex("A");
        graph.addVertex("B");
        edge e1 = new edge("f", "A", "B"); e1.setWeight(1.0f);
        graph.addEdge(e1, "A", "B");

        CommunityDetector detector = new CommunityDetector(graph);
        CommunityDetector.DetectionResult result = detector.detect();

        CommunityDetector.Community ca = result.getCommunityOf("A");
        CommunityDetector.Community cb = result.getCommunityOf("B");
        assertNotNull(ca);
        assertNotNull(cb);
        assertEquals(ca.getId(), cb.getId());
        assertNull(result.getCommunityOf("Z")); // non-existent
    }

    @Test
    public void testModularity() {
        // Two separate cliques → should have positive modularity
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        edge e1 = new edge("f", "A", "B"); e1.setWeight(1.0f);
        edge e2 = new edge("f", "B", "C"); e2.setWeight(1.0f);
        edge e3 = new edge("f", "A", "C"); e3.setWeight(1.0f);
        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");
        graph.addEdge(e3, "A", "C");

        graph.addVertex("D");
        graph.addVertex("E");
        graph.addVertex("F");
        edge e4 = new edge("c", "D", "E"); e4.setWeight(1.0f);
        edge e5 = new edge("c", "E", "F"); e5.setWeight(1.0f);
        edge e6 = new edge("c", "D", "F"); e6.setWeight(1.0f);
        graph.addEdge(e4, "D", "E");
        graph.addEdge(e5, "E", "F");
        graph.addEdge(e6, "D", "F");

        CommunityDetector detector = new CommunityDetector(graph);
        CommunityDetector.DetectionResult result = detector.detect();

        double modularity = result.getModularity(graph);
        assertTrue("Modularity should be positive for disconnected cliques",
                   modularity > 0.0);
        assertTrue("Modularity should be at most 1.0", modularity <= 1.0);
    }

    @Test
    public void testModularityEmptyGraph() {
        CommunityDetector detector = new CommunityDetector(graph);
        CommunityDetector.DetectionResult result = detector.detect();
        assertEquals(0.0, result.getModularity(graph), 0.001);
    }

    @Test
    public void testModularitySingleComponent() {
        // All in one component → modularity should be 0
        graph.addVertex("A");
        graph.addVertex("B");
        edge e1 = new edge("f", "A", "B"); e1.setWeight(1.0f);
        graph.addEdge(e1, "A", "B");

        CommunityDetector detector = new CommunityDetector(graph);
        CommunityDetector.DetectionResult result = detector.detect();
        double mod = result.getModularity(graph);
        // Single component should have modularity close to 0
        assertTrue("Single component modularity near 0", Math.abs(mod) < 0.5);
    }

    @Test
    public void testCommunityToString() {
        graph.addVertex("A");
        graph.addVertex("B");
        edge e1 = new edge("f", "A", "B"); e1.setWeight(5.0f);
        graph.addEdge(e1, "A", "B");

        CommunityDetector detector = new CommunityDetector(graph);
        CommunityDetector.DetectionResult result = detector.detect();
        String str = result.getCommunities().get(0).toString();
        assertTrue(str.contains("Community 0"));
        assertTrue(str.contains("2 members"));
        assertTrue(str.contains("1 edges"));
    }

    @Test
    public void testDominantTypeNone() {
        graph.addVertex("A"); // isolated node
        CommunityDetector detector = new CommunityDetector(graph);
        CommunityDetector.DetectionResult result = detector.detect();
        assertEquals("none", result.getCommunities().get(0).getDominantType());
    }

    @Test
    public void testTotalWeight() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        edge e1 = new edge("f", "A", "B"); e1.setWeight(7.5f);
        edge e2 = new edge("f", "B", "C"); e2.setWeight(2.5f);
        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");

        CommunityDetector detector = new CommunityDetector(graph);
        CommunityDetector.DetectionResult result = detector.detect();
        assertEquals(10.0, result.getCommunities().get(0).getTotalWeight(), 0.01);
    }

    @Test
    public void testLargerGraph() {
        // Star graph: center A connected to B,C,D,E + isolated F,G
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");
        graph.addVertex("E");
        graph.addVertex("F");
        graph.addVertex("G");

        edge e1 = new edge("f", "A", "B"); e1.setWeight(1.0f);
        edge e2 = new edge("c", "A", "C"); e2.setWeight(2.0f);
        edge e3 = new edge("s", "A", "D"); e3.setWeight(3.0f);
        edge e4 = new edge("sg", "A", "E"); e4.setWeight(4.0f);
        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "A", "C");
        graph.addEdge(e3, "A", "D");
        graph.addEdge(e4, "A", "E");

        CommunityDetector detector = new CommunityDetector(graph);
        CommunityDetector.DetectionResult result = detector.detect();

        // Should be 3 communities: {A,B,C,D,E}, {F}, {G}
        assertEquals(3, result.getCommunityCount());
        assertEquals(5, result.getCommunities().get(0).getSize());
        assertEquals(4, result.getCommunities().get(0).getInternalEdges());

        // Verify significant filtering
        assertEquals(1, result.getSignificantCommunities(2).size());
    }

    @Test
    public void testMultipleEdgeTypes() {
        // Community with mixed edge types
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        edge e1 = new edge("f", "A", "B"); e1.setWeight(1.0f);
        edge e2 = new edge("fs", "B", "C"); e2.setWeight(1.0f);
        edge e3 = new edge("sg", "A", "C"); e3.setWeight(1.0f);
        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");
        graph.addEdge(e3, "A", "C");

        CommunityDetector detector = new CommunityDetector(graph);
        CommunityDetector.DetectionResult result = detector.detect();
        CommunityDetector.Community c = result.getCommunities().get(0);

        assertEquals(3, c.getEdgeTypeCounts().size());
        assertEquals(1, c.getEdgeTypeCounts().get("f").intValue());
        assertEquals(1, c.getEdgeTypeCounts().get("fs").intValue());
        assertEquals(1, c.getEdgeTypeCounts().get("sg").intValue());
    }
}
