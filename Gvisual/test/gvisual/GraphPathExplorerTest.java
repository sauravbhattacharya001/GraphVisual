package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for GraphPathExplorer — all-paths enumeration, K-shortest paths,
 * constrained routing, avoidance routing, path statistics, and bottleneck detection.
 *
 * @author zalenix
 */
public class GraphPathExplorerTest {

    private Graph<String, edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
    }

    private edge makeEdge(String type, String v1, String v2, float weight) {
        edge e = new edge(type, v1, v2);
        e.setWeight(weight);
        return e;
    }

    private void buildDiamondGraph() {
        // A--B--D
        // |     |
        // +--C--+
        // A-B: 1, B-D: 1, A-C: 2, C-D: 2
        for (String v : new String[]{"A", "B", "C", "D"}) graph.addVertex(v);
        graph.addEdge(makeEdge("FR", "A", "B", 1.0f), "A", "B");
        graph.addEdge(makeEdge("FR", "B", "D", 1.0f), "B", "D");
        graph.addEdge(makeEdge("FR", "A", "C", 2.0f), "A", "C");
        graph.addEdge(makeEdge("FR", "C", "D", 2.0f), "C", "D");
    }

    private void buildLinearGraph() {
        // A--B--C--D
        for (String v : new String[]{"A", "B", "C", "D"}) graph.addVertex(v);
        graph.addEdge(makeEdge("FR", "A", "B", 1.0f), "A", "B");
        graph.addEdge(makeEdge("FR", "B", "C", 1.0f), "B", "C");
        graph.addEdge(makeEdge("FR", "C", "D", 1.0f), "C", "D");
    }

    private void buildCompleteK4() {
        // Complete graph on 4 vertices
        String[] verts = {"A", "B", "C", "D"};
        for (String v : verts) graph.addVertex(v);
        int id = 0;
        for (int i = 0; i < verts.length; i++) {
            for (int j = i + 1; j < verts.length; j++) {
                graph.addEdge(makeEdge("FR", verts[i], verts[j], 1.0f),
                              verts[i], verts[j]);
            }
        }
    }

    // ── Constructor tests ──────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new GraphPathExplorer(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyGraphThrows() {
        new GraphPathExplorer(graph);
    }

    @Test
    public void testSingleVertexGraph() {
        graph.addVertex("A");
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        assertNotNull(explorer);
    }

    // ── findAllSimplePaths tests ───────────────────────────────────

    @Test
    public void testAllSimplePathsDiamond() {
        buildDiamondGraph();
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        List<GraphPathExplorer.Path> paths = explorer.findAllSimplePaths("A", "D");
        assertEquals("Diamond graph should have 2 paths A->D", 2, paths.size());
        // Sorted by weight: A-B-D (weight 2) before A-C-D (weight 4)
        assertEquals(2.0, paths.get(0).getTotalWeight(), 0.001);
        assertEquals(4.0, paths.get(1).getTotalWeight(), 0.001);
    }

    @Test
    public void testAllSimplePathsLinear() {
        buildLinearGraph();
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        List<GraphPathExplorer.Path> paths = explorer.findAllSimplePaths("A", "D");
        assertEquals("Linear graph should have exactly 1 path A->D", 1, paths.size());
        assertEquals(3, paths.get(0).getHopCount());
    }

    @Test
    public void testAllSimplePathsComplete() {
        buildCompleteK4();
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        List<GraphPathExplorer.Path> paths = explorer.findAllSimplePaths("A", "D");
        // K4: A-D, A-B-D, A-C-D, A-B-C-D, A-C-B-D = 5 paths
        assertEquals(5, paths.size());
    }

    @Test
    public void testAllSimplePathsNoPath() {
        graph.addVertex("A");
        graph.addVertex("B");
        // No edge between them
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        List<GraphPathExplorer.Path> paths = explorer.findAllSimplePaths("A", "B");
        assertTrue("Disconnected vertices should yield no paths", paths.isEmpty());
    }

    @Test
    public void testAllSimplePathsMaxDepthLimit() {
        buildLinearGraph();
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        // maxDepth=2 should prevent finding the 3-hop path A-B-C-D
        List<GraphPathExplorer.Path> paths = explorer.findAllSimplePaths("A", "D", 2, 1000);
        assertTrue("Path A->D requires 3 hops, maxDepth=2 should find none", paths.isEmpty());
    }

    @Test
    public void testAllSimplePathsMaxPathsLimit() {
        buildCompleteK4();
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        List<GraphPathExplorer.Path> paths = explorer.findAllSimplePaths("A", "D", 20, 2);
        assertTrue("Should return at most 2 paths", paths.size() <= 2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAllSimplePathsInvalidSource() {
        buildDiamondGraph();
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        explorer.findAllSimplePaths("X", "D");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAllSimplePathsInvalidTarget() {
        buildDiamondGraph();
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        explorer.findAllSimplePaths("A", "X");
    }

    // ── Path inner class tests ─────────────────────────────────────

    @Test
    public void testPathProperties() {
        GraphPathExplorer.Path p = new GraphPathExplorer.Path(
            Arrays.asList("A", "B", "C"), 3.5);
        assertEquals("A", p.getSource());
        assertEquals("C", p.getTarget());
        assertEquals(2, p.getHopCount());
        assertEquals(3.5, p.getTotalWeight(), 0.001);
        assertEquals(Arrays.asList("A", "B", "C"), p.getVertices());
    }

    @Test
    public void testPathEquality() {
        GraphPathExplorer.Path p1 = new GraphPathExplorer.Path(
            Arrays.asList("A", "B"), 1.0);
        GraphPathExplorer.Path p2 = new GraphPathExplorer.Path(
            Arrays.asList("A", "B"), 2.0);
        GraphPathExplorer.Path p3 = new GraphPathExplorer.Path(
            Arrays.asList("A", "C"), 1.0);
        assertEquals("Same vertices = equal regardless of weight", p1, p2);
        assertNotEquals("Different vertices = not equal", p1, p3);
    }

    @Test
    public void testPathComparable() {
        GraphPathExplorer.Path light = new GraphPathExplorer.Path(
            Arrays.asList("A", "B"), 1.0);
        GraphPathExplorer.Path heavy = new GraphPathExplorer.Path(
            Arrays.asList("A", "C"), 5.0);
        assertTrue("Lighter path should sort before heavier", light.compareTo(heavy) < 0);
    }

    @Test
    public void testPathToString() {
        GraphPathExplorer.Path p = new GraphPathExplorer.Path(
            Arrays.asList("A", "B", "C"), 2.5);
        String s = p.toString();
        assertTrue(s.contains("A -> B -> C"));
        assertTrue(s.contains("hops=2"));
    }

    // ── K shortest paths tests ─────────────────────────────────────

    @Test
    public void testKShortestPathsDiamond() {
        buildDiamondGraph();
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        List<GraphPathExplorer.Path> paths = explorer.findKShortestPaths("A", "D", 3);
        assertEquals(2, paths.size());
        // First should be shortest (A-B-D, weight 2)
        assertEquals(2.0, paths.get(0).getTotalWeight(), 0.001);
    }

    @Test
    public void testKShortestPathsK1() {
        buildDiamondGraph();
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        List<GraphPathExplorer.Path> paths = explorer.findKShortestPaths("A", "D", 1);
        assertEquals(1, paths.size());
    }

    @Test
    public void testKShortestPathsNoPath() {
        graph.addVertex("A");
        graph.addVertex("B");
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        List<GraphPathExplorer.Path> paths = explorer.findKShortestPaths("A", "B", 5);
        assertTrue(paths.isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testKShortestPathsInvalidK() {
        buildDiamondGraph();
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        explorer.findKShortestPaths("A", "D", 0);
    }

    // ── Constrained path tests ─────────────────────────────────────

    @Test
    public void testConstrainedPathWithWaypoint() {
        buildDiamondGraph();
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        // Force path through C: A->C->D
        GraphPathExplorer.Path p = explorer.findConstrainedPath(
            "A", "D", Arrays.asList("C"));
        assertNotNull(p);
        assertTrue("Path must contain C", p.getVertices().contains("C"));
        assertEquals(4.0, p.getTotalWeight(), 0.001);
    }

    @Test
    public void testConstrainedPathNoWaypoints() {
        buildDiamondGraph();
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        GraphPathExplorer.Path p = explorer.findConstrainedPath(
            "A", "D", Collections.emptyList());
        assertNotNull(p);
        // Should find shortest path A-B-D
        assertEquals(2.0, p.getTotalWeight(), 0.001);
    }

    @Test
    public void testConstrainedPathUnreachableWaypoint() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge(makeEdge("FR", "A", "B", 1.0f), "A", "B");
        // C is disconnected
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        GraphPathExplorer.Path p = explorer.findConstrainedPath(
            "A", "B", Arrays.asList("C"));
        assertNull("Should return null if waypoint is unreachable", p);
    }

    // ── Avoidance routing tests ────────────────────────────────────

    @Test
    public void testAvoidNode() {
        buildDiamondGraph();
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        // Avoid B → must go A-C-D
        GraphPathExplorer.Path p = explorer.findPathAvoiding(
            "A", "D",
            new HashSet<>(Arrays.asList("B")),
            null);
        assertNotNull(p);
        assertFalse("Path should not contain B", p.getVertices().contains("B"));
        assertEquals(4.0, p.getTotalWeight(), 0.001);
    }

    @Test
    public void testAvoidEdge() {
        buildDiamondGraph();
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        // Avoid edge A-B → must go A-C-D
        Set<String> avoidEdges = new HashSet<>();
        avoidEdges.add(GraphPathExplorer.edgeKey("A", "B"));
        GraphPathExplorer.Path p = explorer.findPathAvoiding(
            "A", "D", null, avoidEdges);
        assertNotNull(p);
        assertEquals(4.0, p.getTotalWeight(), 0.001);
    }

    @Test
    public void testAvoidSourceReturnsNull() {
        buildDiamondGraph();
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        GraphPathExplorer.Path p = explorer.findPathAvoiding(
            "A", "D",
            new HashSet<>(Arrays.asList("A")),
            null);
        assertNull("Avoiding source should return null", p);
    }

    @Test
    public void testAvoidAllPathsReturnsNull() {
        buildDiamondGraph();
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        // Avoid both B and C → no path from A to D
        GraphPathExplorer.Path p = explorer.findPathAvoiding(
            "A", "D",
            new HashSet<>(Arrays.asList("B", "C")),
            null);
        assertNull(p);
    }

    // ── Edge key tests ─────────────────────────────────────────────

    @Test
    public void testEdgeKeyCanonical() {
        assertEquals(GraphPathExplorer.edgeKey("A", "B"),
                     GraphPathExplorer.edgeKey("B", "A"));
        assertEquals("A|B", GraphPathExplorer.edgeKey("A", "B"));
    }

    // ── Path statistics tests ──────────────────────────────────────

    @Test
    public void testPathLengthDistribution() {
        buildDiamondGraph();
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        Map<Integer, Integer> dist = explorer.pathLengthDistribution("A", "D", 20);
        // Both paths are 2 hops
        assertEquals(1, dist.size());
        assertEquals(Integer.valueOf(2), dist.get(2));
    }

    @Test
    public void testPathStats() {
        buildDiamondGraph();
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        GraphPathExplorer.PathStats stats = explorer.computePathStats("A", "D", 20);
        assertEquals(2, stats.getPathCount());
        assertEquals(2, stats.getShortestHops());
        assertEquals(2, stats.getLongestHops());
        assertEquals(2.0, stats.getMinWeight(), 0.001);
        assertEquals(4.0, stats.getMaxWeight(), 0.001);
        assertEquals("A", stats.getSource());
        assertEquals("D", stats.getTarget());
    }

    @Test
    public void testPathStatsEmpty() {
        graph.addVertex("A");
        graph.addVertex("B");
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        GraphPathExplorer.PathStats stats = explorer.computePathStats("A", "B", 20);
        assertEquals(0, stats.getPathCount());
        assertEquals(0, stats.getShortestHops());
    }

    @Test
    public void testPathStatsToString() {
        buildDiamondGraph();
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        GraphPathExplorer.PathStats stats = explorer.computePathStats("A", "D", 20);
        String s = stats.toString();
        assertTrue(s.contains("A->D"));
        assertTrue(s.contains("2 paths"));
    }

    @Test
    public void testPathDiversity() {
        buildCompleteK4();
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        GraphPathExplorer.PathStats stats = explorer.computePathStats("A", "D", 20);
        double diversity = stats.getPathDiversity();
        assertTrue("Diversity should be between 0 and 1", diversity >= 0 && diversity <= 1);
    }

    // ── Vertex participation and bottleneck tests ──────────────────

    @Test
    public void testVertexPathParticipation() {
        buildDiamondGraph();
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        Map<String, Integer> participation = explorer.vertexPathParticipation("A", "D", 20);
        // B appears in path A-B-D, C appears in path A-C-D
        assertEquals(Integer.valueOf(1), participation.get("B"));
        assertEquals(Integer.valueOf(1), participation.get("C"));
    }

    @Test
    public void testBottleneckVerticesLinear() {
        buildLinearGraph();
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        Set<String> bottlenecks = explorer.findBottleneckVertices("A", "D", 20);
        // B and C are bottlenecks in linear A-B-C-D
        assertTrue(bottlenecks.contains("B"));
        assertTrue(bottlenecks.contains("C"));
    }

    @Test
    public void testBottleneckVerticesDiamond() {
        buildDiamondGraph();
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        Set<String> bottlenecks = explorer.findBottleneckVertices("A", "D", 20);
        // No bottleneck — two independent paths through B and C
        assertTrue("Diamond should have no bottlenecks", bottlenecks.isEmpty());
    }

    @Test
    public void testBottleneckVerticesNoPath() {
        graph.addVertex("A");
        graph.addVertex("B");
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        Set<String> bottlenecks = explorer.findBottleneckVertices("A", "B", 20);
        assertTrue(bottlenecks.isEmpty());
    }

    // ── Report generation tests ────────────────────────────────────

    @Test
    public void testGenerateReport() {
        buildDiamondGraph();
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        String report = explorer.generateReport("A", "D");
        assertNotNull(report);
        assertTrue(report.contains("Source: A"));
        assertTrue(report.contains("Target: D"));
        assertTrue(report.contains("Total simple paths: 2"));
        assertTrue(report.contains("Path Length Distribution"));
    }

    @Test
    public void testGenerateReportNoPath() {
        graph.addVertex("A");
        graph.addVertex("B");
        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        String report = explorer.generateReport("A", "B");
        assertTrue(report.contains("No paths found"));
    }

    // ── Weight handling tests ──────────────────────────────────────

    @Test
    public void testWeightedKShortest() {
        // Graph where weight matters: A-B (10), A-C (1), C-B (1)
        for (String v : new String[]{"A", "B", "C"}) graph.addVertex(v);
        graph.addEdge(makeEdge("FR", "A", "B", 10.0f), "A", "B");
        graph.addEdge(makeEdge("FR", "A", "C", 1.0f), "A", "C");
        graph.addEdge(makeEdge("FR", "C", "B", 1.0f), "C", "B");

        GraphPathExplorer explorer = new GraphPathExplorer(graph);
        List<GraphPathExplorer.Path> paths = explorer.findKShortestPaths("A", "B", 2);
        assertEquals(2, paths.size());
        // Shortest by weight: A-C-B (weight 2)
        assertEquals(2.0, paths.get(0).getTotalWeight(), 0.001);
        // Second: A-B (weight 10)
        assertEquals(10.0, paths.get(1).getTotalWeight(), 0.001);
    }
}
