package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link CommunityDetector} — connected-component community
 * detection, community metrics, modularity, and result accessors.
 */
public class CommunityDetectorTest {

    // ── Helper ──────────────────────────────────────────────────────

    private static Edge addEdge(Graph<String, Edge> g, String v1, String v2) {
        g.addVertex(v1);
        g.addVertex(v2);
        Edge e = new Edge("f", v1, v2);
        g.addEdge(e, v1, v2);
        return e;
    }

    private static Edge addTypedEdge(Graph<String, Edge> g, String v1, String v2, String type) {
        g.addVertex(v1);
        g.addVertex(v2);
        Edge e = new Edge(type, v1, v2);
        g.addEdge(e, v1, v2);
        return e;
    }

    // ── Constructor ─────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new CommunityDetector(null);
    }

    // ── Empty graph ─────────────────────────────────────────────────

    @Test
    public void testEmptyGraph() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        CommunityDetector.DetectionResult result = new CommunityDetector(g).detect();
        assertEquals(0, result.getCommunityCount());
        assertTrue(result.getCommunities().isEmpty());
    }

    // ── Single vertex ───────────────────────────────────────────────

    @Test
    public void testSingleVertex() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        CommunityDetector.DetectionResult result = new CommunityDetector(g).detect();
        assertEquals(1, result.getCommunityCount());
        assertEquals(1, result.getCommunities().get(0).getSize());
        assertTrue(result.getCommunities().get(0).getMembers().contains("A"));
    }

    // ── Single connected component ──────────────────────────────────

    @Test
    public void testFullyConnected() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");

        CommunityDetector.DetectionResult result = new CommunityDetector(g).detect();
        assertEquals(1, result.getCommunityCount());
        assertEquals(3, result.getCommunities().get(0).getSize());
        assertEquals(3, result.getCommunities().get(0).getInternalEdges());
    }

    // ── Two disconnected components ─────────────────────────────────

    @Test
    public void testTwoComponents() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        // second component
        addEdge(g, "X", "Y");

        CommunityDetector.DetectionResult result = new CommunityDetector(g).detect();
        assertEquals(2, result.getCommunityCount());

        // Largest first
        assertEquals(3, result.getCommunities().get(0).getSize());
        assertEquals(2, result.getCommunities().get(1).getSize());
    }

    // ── Node-to-community mapping ───────────────────────────────────

    @Test
    public void testNodeToCommunityMapping() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "X", "Y");

        CommunityDetector.DetectionResult result = new CommunityDetector(g).detect();
        Map<String, Integer> map = result.getNodeToCommunity();

        // A and B should share a community
        assertEquals(map.get("A"), map.get("B"));
        // X and Y should share a community
        assertEquals(map.get("X"), map.get("Y"));
        // But the two groups should differ
        assertNotEquals(map.get("A"), map.get("X"));
    }

    // ── getCommunityOf ──────────────────────────────────────────────

    @Test
    public void testGetCommunityOf() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        CommunityDetector.DetectionResult result = new CommunityDetector(g).detect();
        assertNotNull(result.getCommunityOf("A"));
        assertTrue(result.getCommunityOf("A").getMembers().contains("B"));
        assertNull(result.getCommunityOf("MISSING"));
    }

    // ── Significant communities ─────────────────────────────────────

    @Test
    public void testSignificantCommunities() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        g.addVertex("X"); // isolated

        CommunityDetector.DetectionResult result = new CommunityDetector(g).detect();
        List<CommunityDetector.Community> significant = result.getSignificantCommunities(2);
        assertEquals(1, significant.size());
        assertEquals(3, significant.get(0).getSize());
    }

    // ── Edge type counts ────────────────────────────────────────────

    @Test
    public void testEdgeTypeCounts() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addTypedEdge(g, "A", "B", "friend");
        addTypedEdge(g, "B", "C", "friend");
        addTypedEdge(g, "C", "A", "colleague");

        CommunityDetector.DetectionResult result = new CommunityDetector(g).detect();
        CommunityDetector.Community c = result.getCommunities().get(0);
        assertEquals(Integer.valueOf(2), c.getEdgeTypeCounts().get("friend"));
        assertEquals(Integer.valueOf(1), c.getEdgeTypeCounts().get("colleague"));
        assertEquals("friend", c.getDominantType());
    }

    // ── Density ─────────────────────────────────────────────────────

    @Test
    public void testTriangleDensity() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");

        CommunityDetector.Community c = new CommunityDetector(g).detect().getCommunities().get(0);
        // Triangle: 3 edges, 3 vertices → density = 2*3 / (3*2) = 1.0
        assertEquals(1.0, c.getDensity(), 0.001);
    }

    @Test
    public void testPathDensity() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");

        CommunityDetector.Community c = new CommunityDetector(g).detect().getCommunities().get(0);
        // Path: 2 edges, 3 vertices → density = 2*2 / (3*2) ≈ 0.667
        assertEquals(0.667, c.getDensity(), 0.01);
    }

    @Test
    public void testSingleNodeDensityZero() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        CommunityDetector.Community c = new CommunityDetector(g).detect().getCommunities().get(0);
        assertEquals(0.0, c.getDensity(), 0.001);
    }

    // ── Modularity ──────────────────────────────────────────────────

    @Test
    public void testModularitySingleComponent() {
        // Single component → modularity should be ≤ 0 (no good partition)
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");

        CommunityDetector.DetectionResult result = new CommunityDetector(g).detect();
        // With one community containing all edges, modularity ≈ (1 - something)
        double mod = result.getModularity(g);
        assertTrue("Modularity should be finite", Double.isFinite(mod));
    }

    @Test
    public void testModularityTwoDisjointCliques() {
        // Two disjoint triangles → good partition → positive modularity
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        addEdge(g, "X", "Y");
        addEdge(g, "Y", "Z");
        addEdge(g, "Z", "X");

        CommunityDetector.DetectionResult result = new CommunityDetector(g).detect();
        double mod = result.getModularity(g);
        assertTrue("Modularity should be positive for disjoint cliques", mod > 0);
    }

    @Test
    public void testModularityEmptyGraphZero() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        CommunityDetector.DetectionResult result = new CommunityDetector(g).detect();
        assertEquals(0.0, result.getModularity(g), 0.001);
    }

    // ── getCommunities convenience method ───────────────────────────

    @Test
    public void testGetCommunitiesConvenience() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        g.addVertex("X");

        List<CommunityDetector.Community> communities = new CommunityDetector(g).getCommunities();
        assertEquals(2, communities.size());
    }

    // ── Community toString ──────────────────────────────────────────

    @Test
    public void testCommunityToString() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        String str = new CommunityDetector(g).detect().getCommunities().get(0).toString();
        assertTrue(str.contains("Community"));
        assertTrue(str.contains("2 members"));
    }

    // ── Sorting: largest first ──────────────────────────────────────

    @Test
    public void testCommunitiesSortedBySize() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        // Small component first
        g.addVertex("X");
        // Larger component
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");

        CommunityDetector.DetectionResult result = new CommunityDetector(g).detect();
        List<CommunityDetector.Community> cs = result.getCommunities();
        assertTrue(cs.get(0).getSize() >= cs.get(1).getSize());
    }
}
