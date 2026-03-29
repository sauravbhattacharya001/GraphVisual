package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Map;

/**
 * Tests for {@link GraphIsomorphismChecker}.
 */
public class GraphIsomorphismCheckerTest {

    private Graph<String, Edge> buildGraph(String[][] edges) {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        for (String[] e : edges) {
            if (!g.containsVertex(e[0])) g.addVertex(e[0]);
            if (!g.containsVertex(e[1])) g.addVertex(e[1]);
            g.addEdge(new Edge(e[0], e[1]), e[0], e[1]);
        }
        return g;
    }

    @Test
    public void testIdenticalGraphs() {
        Graph<String, Edge> a = buildGraph(new String[][]{
            {"A", "B"}, {"B", "C"}, {"C", "A"}
        });
        Graph<String, Edge> b = buildGraph(new String[][]{
            {"X", "Y"}, {"Y", "Z"}, {"Z", "X"}
        });
        GraphIsomorphismChecker checker = new GraphIsomorphismChecker(a, b);
        checker.compute();
        assertTrue(checker.isIsomorphic());
        Map<String, String> mapping = checker.getMapping();
        assertEquals(3, mapping.size());
    }

    @Test
    public void testDifferentVertexCount() {
        Graph<String, Edge> a = buildGraph(new String[][]{
            {"A", "B"}, {"B", "C"}
        });
        Graph<String, Edge> b = buildGraph(new String[][]{
            {"X", "Y"}, {"Y", "Z"}, {"Z", "W"}
        });
        // b has 4 vertices, a has 3
        GraphIsomorphismChecker checker = new GraphIsomorphismChecker(a, b);
        checker.compute();
        assertFalse(checker.isIsomorphic());
        assertTrue(checker.getRejectionReason().contains("Vertex count"));
    }

    @Test
    public void testDifferentEdgeCount() {
        Graph<String, Edge> a = buildGraph(new String[][]{
            {"A", "B"}, {"B", "C"}, {"C", "A"}
        });
        Graph<String, Edge> b = new UndirectedSparseGraph<String, Edge>();
        b.addVertex("X"); b.addVertex("Y"); b.addVertex("Z");
        b.addEdge(new Edge("X", "Y"), "X", "Y");
        b.addEdge(new Edge("Y", "Z"), "Y", "Z");
        // Same vertex count (3), different edge count (3 vs 2)
        GraphIsomorphismChecker checker = new GraphIsomorphismChecker(a, b);
        checker.compute();
        assertFalse(checker.isIsomorphic());
    }

    @Test
    public void testEmptyGraphs() {
        Graph<String, Edge> a = new UndirectedSparseGraph<String, Edge>();
        Graph<String, Edge> b = new UndirectedSparseGraph<String, Edge>();
        GraphIsomorphismChecker checker = new GraphIsomorphismChecker(a, b);
        checker.compute();
        assertTrue(checker.isIsomorphic());
    }

    @Test
    public void testNonIsomorphicSameDegreeSequence() {
        // C4 (cycle of 4) vs K1,3 + isolated vertex — both have 4 vertices
        // but different structures (though we need same edge count too)
        // Better: C6 vs K3,3 — both 6 vertices, 9 edges... no.
        // Simple: two different 4-vertex 4-edge graphs
        // Graph A: cycle C4 (A-B-C-D-A)
        Graph<String, Edge> a = buildGraph(new String[][]{
            {"A", "B"}, {"B", "C"}, {"C", "D"}, {"D", "A"}
        });
        // Graph B: path + extra edge (A-B-C-D, A-C) — "diamond minus one"
        // 4 vertices, 4 edges, degree seq [1,2,2,3] vs [2,2,2,2]
        // Different degree sequences — will be caught early
        // Let's use something with same degree seq but non-isomorphic:
        // Both 6 vertices, 6 edges, all degree 2:
        // A: two disjoint triangles
        // B: single hexagon (C6)
        Graph<String, Edge> ga = buildGraph(new String[][]{
            {"A", "B"}, {"B", "C"}, {"C", "A"},
            {"D", "E"}, {"E", "F"}, {"F", "D"}
        });
        Graph<String, Edge> gb = buildGraph(new String[][]{
            {"X", "Y"}, {"Y", "Z"}, {"Z", "W"},
            {"W", "V"}, {"V", "U"}, {"U", "X"}
        });
        GraphIsomorphismChecker checker = new GraphIsomorphismChecker(ga, gb);
        checker.compute();
        assertFalse(checker.isIsomorphic());
    }

    @Test
    public void testSummaryContainsResult() {
        Graph<String, Edge> a = buildGraph(new String[][]{
            {"A", "B"}
        });
        Graph<String, Edge> b = buildGraph(new String[][]{
            {"X", "Y"}
        });
        GraphIsomorphismChecker checker = new GraphIsomorphismChecker(a, b);
        checker.compute();
        String summary = checker.getSummary();
        assertTrue(summary.contains("YES"));
        assertTrue(summary.contains("Mapping"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraph() {
        new GraphIsomorphismChecker(null, null);
    }

    @Test(expected = IllegalStateException.class)
    public void testQueryBeforeCompute() {
        Graph<String, Edge> a = new UndirectedSparseGraph<String, Edge>();
        Graph<String, Edge> b = new UndirectedSparseGraph<String, Edge>();
        GraphIsomorphismChecker checker = new GraphIsomorphismChecker(a, b);
        checker.isIsomorphic(); // should throw
    }
}
