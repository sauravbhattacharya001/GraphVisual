package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for {@link GraphUtils} — shared graph traversal, adjacency,
 * BFS, component finding, betweenness centrality, global efficiency,
 * Dijkstra shortest paths, and subgraph utilities.
 */
public class GraphUtilsTest {

    private Graph<String, edge> graph;

    @Before
    public void setUp() {
        // Build a small test graph:
        //   A -- B -- C -- D
        //   |         |
        //   E ------- F
        //
        // Plus isolated node G
        graph = new UndirectedSparseGraph<>();
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");
        graph.addVertex("E");
        graph.addVertex("F");
        graph.addVertex("G");

        addEdge("A", "B", "f", 1.0f);
        addEdge("B", "C", "f", 2.0f);
        addEdge("C", "D", "c", 3.0f);
        addEdge("A", "E", "s", 1.0f);
        addEdge("E", "F", "f", 1.0f);
        addEdge("C", "F", "fs", 4.0f);
    }

    private int edgeCounter = 0;

    private void addEdge(String v1, String v2, String type, float weight) {
        edge e = new edge(type, v1, v2);
        e.setWeight(weight);
        e.setLabel(v1 + "-" + v2);
        graph.addEdge(e, v1, v2);
        edgeCounter++;
    }

    // ── getOtherEnd ─────────────────────────────────────────────

    @Test
    public void getOtherEnd_fromVertex1_returnsVertex2() {
        edge e = new edge("f", "X", "Y");
        assertEquals("Y", GraphUtils.getOtherEnd(e, "X"));
    }

    @Test
    public void getOtherEnd_fromVertex2_returnsVertex1() {
        edge e = new edge("f", "X", "Y");
        assertEquals("X", GraphUtils.getOtherEnd(e, "Y"));
    }

    @Test
    public void getOtherEnd_notEndpoint_returnsNull() {
        edge e = new edge("f", "X", "Y");
        assertNull(GraphUtils.getOtherEnd(e, "Z"));
    }

    // ── buildAdjacencyMap ───────────────────────────────────────

    @Test
    public void buildAdjacencyMap_allVertices() {
        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(graph);
        assertEquals(7, adj.size());

        assertTrue(adj.get("A").contains("B"));
        assertTrue(adj.get("A").contains("E"));
        assertEquals(2, adj.get("A").size());

        assertTrue(adj.get("B").contains("A"));
        assertTrue(adj.get("B").contains("C"));

        assertTrue(adj.get("C").contains("B"));
        assertTrue(adj.get("C").contains("D"));
        assertTrue(adj.get("C").contains("F"));
        assertEquals(3, adj.get("C").size());

        // G is isolated
        assertTrue(adj.get("G").isEmpty());
    }

    @Test
    public void buildAdjacencyMap_subset() {
        Set<String> subset = new HashSet<>(Arrays.asList("A", "B", "C"));
        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(graph, subset);

        assertEquals(3, adj.size());
        assertTrue(adj.get("A").contains("B"));
        assertFalse(adj.get("A").contains("E")); // E not in subset
        assertTrue(adj.get("B").contains("A"));
        assertTrue(adj.get("B").contains("C"));
    }

    @Test
    public void buildAdjacencyMap_emptyGraph() {
        Graph<String, edge> empty = new UndirectedSparseGraph<>();
        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(empty);
        assertTrue(adj.isEmpty());
    }

    // ── bfsDistances ────────────────────────────────────────────

    @Test
    public void bfsDistances_fromA() {
        Map<String, Integer> dist = GraphUtils.bfsDistances(graph, "A");
        assertEquals(0, (int) dist.get("A"));
        assertEquals(1, (int) dist.get("B"));
        assertEquals(2, (int) dist.get("C"));
        assertEquals(3, (int) dist.get("D"));
        assertEquals(1, (int) dist.get("E"));
        assertEquals(2, (int) dist.get("F"));
        assertFalse(dist.containsKey("G")); // isolated
    }

    @Test
    public void bfsDistances_fromIsolated() {
        Map<String, Integer> dist = GraphUtils.bfsDistances(graph, "G");
        assertEquals(1, dist.size());
        assertEquals(0, (int) dist.get("G"));
    }

    // ── bfsComponent ────────────────────────────────────────────

    @Test
    public void bfsComponent_mainComponent() {
        Set<String> comp = GraphUtils.bfsComponent(graph, "A");
        assertEquals(6, comp.size());
        assertTrue(comp.containsAll(Arrays.asList("A", "B", "C", "D", "E", "F")));
        assertFalse(comp.contains("G"));
    }

    @Test
    public void bfsComponent_isolatedNode() {
        Set<String> comp = GraphUtils.bfsComponent(graph, "G");
        assertEquals(1, comp.size());
        assertTrue(comp.contains("G"));
    }

    // ── findComponents ──────────────────────────────────────────

    @Test
    public void findComponents_twoComponents() {
        List<Set<String>> comps = GraphUtils.findComponents(graph);
        assertEquals(2, comps.size());
        // Sorted largest-first
        assertEquals(6, comps.get(0).size());
        assertEquals(1, comps.get(1).size());
        assertTrue(comps.get(1).contains("G"));
    }

    @Test
    public void findComponents_emptyGraph() {
        Graph<String, edge> empty = new UndirectedSparseGraph<>();
        List<Set<String>> comps = GraphUtils.findComponents(empty);
        assertTrue(comps.isEmpty());
    }

    // ── findLargestComponent ────────────────────────────────────

    @Test
    public void findLargestComponent_returnsLargest() {
        Set<String> largest = GraphUtils.findLargestComponent(graph);
        assertEquals(6, largest.size());
        assertFalse(largest.contains("G"));
    }

    @Test
    public void findLargestComponent_emptyGraph() {
        Graph<String, edge> empty = new UndirectedSparseGraph<>();
        Set<String> largest = GraphUtils.findLargestComponent(empty);
        assertTrue(largest.isEmpty());
    }

    // ── getCommonNeighbors ──────────────────────────────────────

    @Test
    public void getCommonNeighbors_existingCommon() {
        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(graph);
        // A and C share neighbor B? No: A-B, B-C, but A not neighbor of C
        // B and F share neighbor C
        Set<String> common = GraphUtils.getCommonNeighbors(adj, "B", "F");
        assertTrue(common.contains("C"));
        assertEquals(1, common.size());
    }

    @Test
    public void getCommonNeighbors_noCommon() {
        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(graph);
        Set<String> common = GraphUtils.getCommonNeighbors(adj, "D", "E");
        assertTrue(common.isEmpty());
    }

    // ── hasCycleInSubgraph ──────────────────────────────────────

    @Test
    public void hasCycle_undirected_withCycle() {
        // A-B-C-F-E-A forms a cycle
        Set<String> vertices = new HashSet<>(Arrays.asList("A", "B", "C", "E", "F"));
        assertTrue(GraphUtils.hasCycleInSubgraph(graph, vertices, false));
    }

    @Test
    public void hasCycle_undirected_noCycle() {
        // A-B-C-D is a path (no cycle)
        Set<String> vertices = new HashSet<>(Arrays.asList("A", "B", "C", "D"));
        assertFalse(GraphUtils.hasCycleInSubgraph(graph, vertices, false));
    }

    @Test
    public void hasCycle_singleVertex() {
        Set<String> single = new HashSet<>(Arrays.asList("A"));
        assertFalse(GraphUtils.hasCycleInSubgraph(graph, single, false));
    }

    // ── countEdgesInSubgraph ────────────────────────────────────

    @Test
    public void countEdgesInSubgraph_fullGraph() {
        Set<String> all = new HashSet<>(graph.getVertices());
        assertEquals(6, GraphUtils.countEdgesInSubgraph(graph, all));
    }

    @Test
    public void countEdgesInSubgraph_subset() {
        // A-B, B-C = 2 edges in {A,B,C}
        Set<String> sub = new HashSet<>(Arrays.asList("A", "B", "C"));
        assertEquals(2, GraphUtils.countEdgesInSubgraph(graph, sub));
    }

    @Test
    public void countEdgesInSubgraph_isolatedNode() {
        Set<String> sub = new HashSet<>(Arrays.asList("G"));
        assertEquals(0, GraphUtils.countEdgesInSubgraph(graph, sub));
    }

    // ── countComponentsInSubgraph ───────────────────────────────

    @Test
    public void countComponentsInSubgraph_connected() {
        Set<String> connected = new HashSet<>(Arrays.asList("A", "B", "C"));
        assertEquals(1, GraphUtils.countComponentsInSubgraph(graph, connected));
    }

    @Test
    public void countComponentsInSubgraph_disconnected() {
        // D and G are not connected to each other
        Set<String> disconnected = new HashSet<>(Arrays.asList("D", "G"));
        assertEquals(2, GraphUtils.countComponentsInSubgraph(graph, disconnected));
    }

    // ── cycleRankOfSubgraph ─────────────────────────────────────

    @Test
    public void cycleRank_tree() {
        // A-B-C-D is a path (tree): edges=3, vertices=4, components=1 → rank=0
        Set<String> path = new HashSet<>(Arrays.asList("A", "B", "C", "D"));
        assertEquals(0, GraphUtils.cycleRankOfSubgraph(graph, path));
    }

    @Test
    public void cycleRank_withCycle() {
        // A-B-C-F-E-A: edges=5, vertices=5, components=1 → rank=1
        Set<String> cyclic = new HashSet<>(Arrays.asList("A", "B", "C", "E", "F"));
        assertEquals(1, GraphUtils.cycleRankOfSubgraph(graph, cyclic));
    }

    // ── copyGraph ───────────────────────────────────────────────

    @Test
    public void copyGraph_preservesStructure() {
        Graph<String, edge> copy = GraphUtils.copyGraph(graph);
        assertEquals(graph.getVertexCount(), copy.getVertexCount());
        assertEquals(graph.getEdgeCount(), copy.getEdgeCount());

        for (String v : graph.getVertices()) {
            assertTrue(copy.containsVertex(v));
        }
    }

    @Test
    public void copyGraph_independent() {
        Graph<String, edge> copy = GraphUtils.copyGraph(graph);
        copy.removeVertex("A");
        // Original should be unaffected
        assertTrue(graph.containsVertex("A"));
        assertEquals(7, graph.getVertexCount());
        assertEquals(6, copy.getVertexCount());
    }

    @Test
    public void copyGraph_preservesEdgeProperties() {
        Graph<String, edge> copy = GraphUtils.copyGraph(graph);
        for (edge e : copy.getEdges()) {
            assertNotNull(e.getType());
            assertNotNull(e.getVertex1());
            assertNotNull(e.getVertex2());
        }
    }

    // ── computeBetweenness ──────────────────────────────────────

    @Test
    public void computeBetweenness_nonEmpty() {
        Map<String, Double> bc = GraphUtils.computeBetweenness(graph);
        assertEquals(7, bc.size());

        // B and C are on many shortest paths, should have higher betweenness
        assertTrue(bc.get("B") > 0);
        assertTrue(bc.get("C") > 0);

        // G is isolated, betweenness = 0
        assertEquals(0.0, bc.get("G"), 0.001);

        // D is a leaf, betweenness = 0
        assertEquals(0.0, bc.get("D"), 0.001);
    }

    @Test
    public void computeBetweenness_emptyGraph() {
        Graph<String, edge> empty = new UndirectedSparseGraph<>();
        Map<String, Double> bc = GraphUtils.computeBetweenness(empty);
        assertTrue(bc.isEmpty());
    }

    @Test
    public void computeBetweenness_singleNode() {
        Graph<String, edge> single = new UndirectedSparseGraph<>();
        single.addVertex("X");
        Map<String, Double> bc = GraphUtils.computeBetweenness(single);
        assertEquals(1, bc.size());
        assertEquals(0.0, bc.get("X"), 0.001);
    }

    @Test
    public void computeBetweenness_lineGraph() {
        // A -- B -- C: B has betweenness = 1 (on path A-C)
        Graph<String, edge> line = new UndirectedSparseGraph<>();
        line.addVertex("A");
        line.addVertex("B");
        line.addVertex("C");
        edge e1 = new edge("f", "A", "B");
        edge e2 = new edge("f", "B", "C");
        line.addEdge(e1, "A", "B");
        line.addEdge(e2, "B", "C");

        Map<String, Double> bc = GraphUtils.computeBetweenness(line);
        assertEquals(1.0, bc.get("B"), 0.001);
        assertEquals(0.0, bc.get("A"), 0.001);
        assertEquals(0.0, bc.get("C"), 0.001);
    }

    // ── globalEfficiency ────────────────────────────────────────

    @Test
    public void globalEfficiency_completeTriangle() {
        Graph<String, edge> tri = new UndirectedSparseGraph<>();
        tri.addVertex("A");
        tri.addVertex("B");
        tri.addVertex("C");
        tri.addEdge(new edge("f", "A", "B"), "A", "B");
        tri.addEdge(new edge("f", "B", "C"), "B", "C");
        tri.addEdge(new edge("f", "A", "C"), "A", "C");

        double eff = GraphUtils.globalEfficiency(tri);
        // Complete graph: all distances = 1, efficiency = 1.0
        assertEquals(1.0, eff, 0.001);
    }

    @Test
    public void globalEfficiency_singleNode() {
        Graph<String, edge> single = new UndirectedSparseGraph<>();
        single.addVertex("X");
        assertEquals(0.0, GraphUtils.globalEfficiency(single), 0.001);
    }

    @Test
    public void globalEfficiency_disconnected() {
        // Two isolated nodes: distance infinite, sum = 0
        Graph<String, edge> disc = new UndirectedSparseGraph<>();
        disc.addVertex("A");
        disc.addVertex("B");
        assertEquals(0.0, GraphUtils.globalEfficiency(disc), 0.001);
    }

    @Test
    public void globalEfficiency_lineGraph() {
        // A-B-C: d(A,B)=1, d(A,C)=2, d(B,C)=1
        // sum = 1/1 + 1/2 + 1/1 = 2.5; E = 2*2.5/(3*2) = 5/6 ≈ 0.833
        Graph<String, edge> line = new UndirectedSparseGraph<>();
        line.addVertex("A");
        line.addVertex("B");
        line.addVertex("C");
        line.addEdge(new edge("f", "A", "B"), "A", "B");
        line.addEdge(new edge("f", "B", "C"), "B", "C");

        double eff = GraphUtils.globalEfficiency(line);
        assertEquals(5.0 / 6.0, eff, 0.001);
    }

    // ── dijkstra ────────────────────────────────────────────────

    @Test
    public void dijkstra_shortestDistances() {
        GraphUtils.DijkstraResult dr = GraphUtils.dijkstra(graph, "A");
        assertEquals(0.0, dr.dist.get("A"), 0.001);
        assertEquals(1.0, dr.dist.get("B"), 0.001); // A-B weight 1
        assertEquals(1.0, dr.dist.get("E"), 0.001); // A-E weight 1
        assertEquals(2.0, dr.dist.get("F"), 0.001); // A-E-F weight 1+1
        // A→C: via A-B-C = 1+2=3, or A-E-F-C = 1+1+4=6 → shortest = 3
        assertEquals(3.0, dr.dist.get("C"), 0.001);
        // A→D: A-B-C-D = 1+2+3=6
        assertEquals(6.0, dr.dist.get("D"), 0.001);

        // G unreachable
        assertFalse(dr.dist.containsKey("G"));
    }

    @Test
    public void dijkstra_isolatedSource() {
        GraphUtils.DijkstraResult dr = GraphUtils.dijkstra(graph, "G");
        assertEquals(1, dr.dist.size());
        assertEquals(0.0, dr.dist.get("G"), 0.001);
    }

    // ── reconstructPath ─────────────────────────────────────────

    @Test
    public void reconstructPath_existingPath() {
        GraphUtils.DijkstraResult dr = GraphUtils.dijkstra(graph, "A");
        List<String> path = GraphUtils.reconstructPath(dr, "A", "D");
        assertNotNull(path);
        assertEquals("A", path.get(0));
        assertEquals("D", path.get(path.size() - 1));
        assertTrue(path.size() >= 2);
    }

    @Test
    public void reconstructPath_sameSourceTarget() {
        GraphUtils.DijkstraResult dr = GraphUtils.dijkstra(graph, "A");
        List<String> path = GraphUtils.reconstructPath(dr, "A", "A");
        assertNotNull(path);
        assertEquals(1, path.size());
        assertEquals("A", path.get(0));
    }

    @Test
    public void reconstructPath_unreachable() {
        GraphUtils.DijkstraResult dr = GraphUtils.dijkstra(graph, "A");
        List<String> path = GraphUtils.reconstructPath(dr, "A", "G");
        assertNull(path);
    }

    @Test
    public void reconstructPath_adjacent() {
        GraphUtils.DijkstraResult dr = GraphUtils.dijkstra(graph, "A");
        List<String> path = GraphUtils.reconstructPath(dr, "A", "B");
        assertNotNull(path);
        assertEquals(2, path.size());
        assertEquals("A", path.get(0));
        assertEquals("B", path.get(1));
    }
}
