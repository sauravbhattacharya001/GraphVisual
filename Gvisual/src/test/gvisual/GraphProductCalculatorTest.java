package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

/**
 * Tests for GraphProductCalculator — Cartesian, tensor, strong, and
 * lexicographic graph products.
 *
 * @author zalenix
 */
public class GraphProductCalculatorTest {

    private Graph<String, Edge> triangle;   // K3: 3 vertices, 3 edges
    private Graph<String, Edge> path2;      // P2: 2 vertices, 1 edge
    private Graph<String, Edge> single;     // K1: 1 vertex, 0 edges
    private Graph<String, Edge> k2;         // K2: 2 vertices, 1 edge
    private Graph<String, Edge> empty3;     // 3 isolated vertices

    @Before
    public void setUp() {
        // Triangle K3: a-b, b-c, a-c
        triangle = new UndirectedSparseGraph<String, Edge>();
        triangle.addVertex("a");
        triangle.addVertex("b");
        triangle.addVertex("c");
        triangle.addEdge(new Edge("e", "a", "b"), "a", "b");
        triangle.addEdge(new Edge("e", "b", "c"), "b", "c");
        triangle.addEdge(new Edge("e", "a", "c"), "a", "c");

        // Path P2: x-y
        path2 = new UndirectedSparseGraph<String, Edge>();
        path2.addVertex("x");
        path2.addVertex("y");
        path2.addEdge(new Edge("e", "x", "y"), "x", "y");

        // Single vertex
        single = new UndirectedSparseGraph<String, Edge>();
        single.addVertex("s");

        // K2: p-q
        k2 = new UndirectedSparseGraph<String, Edge>();
        k2.addVertex("p");
        k2.addVertex("q");
        k2.addEdge(new Edge("e", "p", "q"), "p", "q");

        // Empty graph with 3 vertices
        empty3 = new UndirectedSparseGraph<String, Edge>();
        empty3.addVertex("1");
        empty3.addVertex("2");
        empty3.addVertex("3");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphG() {
        new GraphProductCalculator(null, path2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphH() {
        new GraphProductCalculator(triangle, null);
    }

    // --- Vertex count tests ---

    @Test
    public void testCartesianVertexCount() {
        GraphProductCalculator calc = new GraphProductCalculator(triangle, path2);
        Graph<String, Edge> result = calc.cartesianProduct();
        assertEquals(6, result.getVertexCount()); // 3 * 2
    }

    @Test
    public void testTensorVertexCount() {
        GraphProductCalculator calc = new GraphProductCalculator(triangle, path2);
        assertEquals(6, calc.tensorProduct().getVertexCount());
    }

    @Test
    public void testStrongVertexCount() {
        GraphProductCalculator calc = new GraphProductCalculator(triangle, path2);
        assertEquals(6, calc.strongProduct().getVertexCount());
    }

    @Test
    public void testLexicographicVertexCount() {
        GraphProductCalculator calc = new GraphProductCalculator(triangle, path2);
        assertEquals(6, calc.lexicographicProduct().getVertexCount());
    }

    // --- Cartesian product edge counts ---

    @Test
    public void testCartesianK3xP2Edges() {
        // |E_G|*|V_H| + |V_G|*|E_H| = 3*2 + 3*1 = 9
        GraphProductCalculator calc = new GraphProductCalculator(triangle, path2);
        assertEquals(9, calc.cartesianProduct().getEdgeCount());
    }

    @Test
    public void testCartesianK2xK2Edges() {
        // 1*2 + 2*1 = 4 (this is C4, the 4-cycle)
        GraphProductCalculator calc = new GraphProductCalculator(k2, k2);
        assertEquals(4, calc.cartesianProduct().getEdgeCount());
    }

    @Test
    public void testCartesianWithSingleVertex() {
        // K3 □ K1 = K3 (3 edges)
        GraphProductCalculator calc = new GraphProductCalculator(triangle, single);
        assertEquals(3, calc.cartesianProduct().getEdgeCount());
        assertEquals(3, calc.cartesianProduct().getVertexCount());
    }

    // --- Tensor product edge counts ---

    @Test
    public void testTensorK3xP2Edges() {
        // 2 * |E_G| * |E_H| = 2 * 3 * 1 = 6
        GraphProductCalculator calc = new GraphProductCalculator(triangle, path2);
        assertEquals(6, calc.tensorProduct().getEdgeCount());
    }

    @Test
    public void testTensorK2xK2Edges() {
        // 2 * 1 * 1 = 2
        GraphProductCalculator calc = new GraphProductCalculator(k2, k2);
        assertEquals(2, calc.tensorProduct().getEdgeCount());
    }

    @Test
    public void testTensorWithEmptyGraph() {
        // No edges in H → no tensor edges
        GraphProductCalculator calc = new GraphProductCalculator(triangle, empty3);
        assertEquals(0, calc.tensorProduct().getEdgeCount());
        assertEquals(9, calc.tensorProduct().getVertexCount());
    }

    // --- Strong product edge counts ---

    @Test
    public void testStrongK3xP2Edges() {
        // Cartesian + Tensor = 9 + 6 = 15
        GraphProductCalculator calc = new GraphProductCalculator(triangle, path2);
        assertEquals(15, calc.strongProduct().getEdgeCount());
    }

    @Test
    public void testStrongK2xK2Edges() {
        // 4 + 2 = 6 → K4 (complete graph on 4 vertices)
        GraphProductCalculator calc = new GraphProductCalculator(k2, k2);
        assertEquals(6, calc.strongProduct().getEdgeCount());
    }

    // --- Lexicographic product edge counts ---

    @Test
    public void testLexicographicK3xP2Edges() {
        // |E_G|*|V_H|^2 + |V_G|*|E_H| = 3*4 + 3*1 = 15
        GraphProductCalculator calc = new GraphProductCalculator(triangle, path2);
        assertEquals(15, calc.lexicographicProduct().getEdgeCount());
    }

    @Test
    public void testLexicographicK2xK2Edges() {
        // 1*4 + 2*1 = 6
        GraphProductCalculator calc = new GraphProductCalculator(k2, k2);
        assertEquals(6, calc.lexicographicProduct().getEdgeCount());
    }

    // --- Single vertex products ---

    @Test
    public void testAllProductsWithK1() {
        GraphProductCalculator calc = new GraphProductCalculator(single, single);
        assertEquals(1, calc.cartesianProduct().getVertexCount());
        assertEquals(0, calc.cartesianProduct().getEdgeCount());
        assertEquals(0, calc.tensorProduct().getEdgeCount());
        assertEquals(0, calc.strongProduct().getEdgeCount());
        assertEquals(0, calc.lexicographicProduct().getEdgeCount());
    }

    // --- ProductInfo tests ---

    @Test
    public void testProductInfo() {
        GraphProductCalculator calc = new GraphProductCalculator(triangle, path2);
        GraphProductCalculator.ProductInfo info = calc.getProductInfo(
                GraphProductCalculator.ProductType.CARTESIAN);
        assertEquals(6, info.getVertexCount());
        assertEquals(9, info.getEdgeCount());
        assertEquals(3, info.getSourceGVertices());
        assertEquals(3, info.getSourceGEdges());
        assertEquals(2, info.getSourceHVertices());
        assertEquals(1, info.getSourceHEdges());
        assertTrue(info.getDensity() > 0);
        assertTrue(info.getComputeTimeMs() >= 0);
        assertNotNull(info.toString());
    }

    // --- Report test ---

    @Test
    public void testReport() {
        GraphProductCalculator calc = new GraphProductCalculator(triangle, path2);
        String report = calc.getReport();
        assertNotNull(report);
        assertTrue(report.contains("Graph Product Report"));
        assertTrue(report.contains("CARTESIAN"));
        assertTrue(report.contains("TENSOR"));
        assertTrue(report.contains("STRONG"));
        assertTrue(report.contains("LEXICOGRAPHIC"));
        assertTrue(report.contains("Theoretical Edge Counts"));
    }

    // --- Degree sequence tests ---

    @Test
    public void testDegreeSequence() {
        GraphProductCalculator calc = new GraphProductCalculator(k2, k2);
        List<Integer> degrees = calc.getDegreeSequence(
                GraphProductCalculator.ProductType.CARTESIAN);
        assertEquals(4, degrees.size());
        // C4: all vertices have degree 2
        for (int d : degrees) {
            assertEquals(2, d);
        }
    }

    // --- Caching test ---

    @Test
    public void testCachingReturnsSameInstance() {
        GraphProductCalculator calc = new GraphProductCalculator(triangle, path2);
        Graph<String, Edge> first = calc.cartesianProduct();
        Graph<String, Edge> second = calc.cartesianProduct();
        assertSame(first, second);
    }

    // --- Vertex naming test ---

    @Test
    public void testVertexNaming() {
        GraphProductCalculator calc = new GraphProductCalculator(k2, k2);
        Graph<String, Edge> product = calc.cartesianProduct();
        assertTrue(product.containsVertex("(p,p)"));
        assertTrue(product.containsVertex("(p,q)"));
        assertTrue(product.containsVertex("(q,p)"));
        assertTrue(product.containsVertex("(q,q)"));
    }

    // --- Empty graph products ---

    @Test
    public void testCartesianWithEmptyGraph() {
        GraphProductCalculator calc = new GraphProductCalculator(triangle, empty3);
        Graph<String, Edge> result = calc.cartesianProduct();
        assertEquals(9, result.getVertexCount());
        // |E_G|*|V_H| + |V_G|*|E_H| = 3*3 + 3*0 = 9
        assertEquals(9, result.getEdgeCount());
    }

    @Test
    public void testLexicographicWithEmptyH() {
        // |E_G|*|V_H|^2 + |V_G|*|E_H| = 3*9 + 3*0 = 27
        GraphProductCalculator calc = new GraphProductCalculator(triangle, empty3);
        assertEquals(27, calc.lexicographicProduct().getEdgeCount());
    }
}
