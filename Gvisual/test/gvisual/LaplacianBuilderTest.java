package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link LaplacianBuilder}.
 */
public class LaplacianBuilderTest {

    private Graph<String, edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
    }

    private void addEdge(String v1, String v2) {
        edge e = new edge("f", v1, v2);
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        graph.addEdge(e, v1, v2);
    }

    // ── Adjacency Matrix ───────────────────────────────────────────

    @Test
    public void testAdjacencyMatrix_singleEdge() {
        addEdge("A", "B");
        List<String> verts = Arrays.asList("A", "B");
        double[][] A = LaplacianBuilder.buildAdjacencyMatrix(graph, verts);
        assertEquals(0.0, A[0][0], 1e-10); // no self-loop
        assertEquals(1.0, A[0][1], 1e-10); // A-B
        assertEquals(1.0, A[1][0], 1e-10); // B-A (symmetric)
        assertEquals(0.0, A[1][1], 1e-10);
    }

    @Test
    public void testAdjacencyMatrix_triangle() {
        addEdge("A", "B"); addEdge("B", "C"); addEdge("C", "A");
        List<String> verts = Arrays.asList("A", "B", "C");
        double[][] A = LaplacianBuilder.buildAdjacencyMatrix(graph, verts);
        // All pairs connected
        for (int i = 0; i < 3; i++) {
            assertEquals(0.0, A[i][i], 1e-10);
            for (int j = 0; j < 3; j++) {
                if (i != j) assertEquals(1.0, A[i][j], 1e-10);
            }
        }
    }

    @Test
    public void testAdjacencyMatrix_disconnected() {
        graph.addVertex("A");
        graph.addVertex("B");
        List<String> verts = Arrays.asList("A", "B");
        double[][] A = LaplacianBuilder.buildAdjacencyMatrix(graph, verts);
        assertEquals(0.0, A[0][1], 1e-10);
        assertEquals(0.0, A[1][0], 1e-10);
    }

    // ── Degree Vector ──────────────────────────────────────────────

    @Test
    public void testDegreeVector_triangle() {
        addEdge("A", "B"); addEdge("B", "C"); addEdge("C", "A");
        List<String> verts = Arrays.asList("A", "B", "C");
        double[][] A = LaplacianBuilder.buildAdjacencyMatrix(graph, verts);
        double[] deg = LaplacianBuilder.buildDegreeVector(A, 3);
        for (int i = 0; i < 3; i++) {
            assertEquals(2.0, deg[i], 1e-10);
        }
    }

    @Test
    public void testDegreeVector_star() {
        addEdge("C", "L1"); addEdge("C", "L2"); addEdge("C", "L3");
        List<String> verts = Arrays.asList("C", "L1", "L2", "L3");
        double[][] A = LaplacianBuilder.buildAdjacencyMatrix(graph, verts);
        double[] deg = LaplacianBuilder.buildDegreeVector(A, 4);
        assertEquals(3.0, deg[0], 1e-10); // center
        assertEquals(1.0, deg[1], 1e-10); // leaf
    }

    // ── Standard Laplacian ─────────────────────────────────────────

    @Test
    public void testLaplacian_rowSumsZero() {
        addEdge("A", "B"); addEdge("B", "C"); addEdge("C", "A");
        List<String> verts = Arrays.asList("A", "B", "C");
        double[][] L = LaplacianBuilder.buildLaplacian(graph, verts);
        for (int i = 0; i < 3; i++) {
            double rowSum = 0;
            for (int j = 0; j < 3; j++) rowSum += L[i][j];
            assertEquals(0.0, rowSum, 1e-10);
        }
    }

    @Test
    public void testLaplacian_symmetric() {
        addEdge("A", "B"); addEdge("B", "C");
        List<String> verts = Arrays.asList("A", "B", "C");
        double[][] L = LaplacianBuilder.buildLaplacian(graph, verts);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals(L[i][j], L[j][i], 1e-10);
            }
        }
    }

    @Test
    public void testLaplacian_diagonalIsDegree() {
        addEdge("A", "B"); addEdge("B", "C"); addEdge("B", "D");
        List<String> verts = Arrays.asList("A", "B", "C", "D");
        double[][] L = LaplacianBuilder.buildLaplacian(graph, verts);
        assertEquals(1.0, L[0][0], 1e-10); // A: degree 1
        assertEquals(3.0, L[1][1], 1e-10); // B: degree 3
        assertEquals(1.0, L[2][2], 1e-10); // C: degree 1
        assertEquals(1.0, L[3][3], 1e-10); // D: degree 1
    }

    @Test
    public void testLaplacian_offDiagonalIsNegativeAdj() {
        addEdge("A", "B");
        List<String> verts = Arrays.asList("A", "B");
        double[][] L = LaplacianBuilder.buildLaplacian(graph, verts);
        assertEquals(-1.0, L[0][1], 1e-10);
        assertEquals(-1.0, L[1][0], 1e-10);
    }

    @Test
    public void testLaplacian_fromAdjMatrix() {
        double[][] A = { {0, 1}, {1, 0} };
        double[][] L = LaplacianBuilder.buildLaplacian(A, 2);
        assertEquals(1.0, L[0][0], 1e-10);
        assertEquals(-1.0, L[0][1], 1e-10);
    }

    // ── Subgraph Laplacian ─────────────────────────────────────────

    @Test
    public void testSubgraphLaplacian_subset() {
        addEdge("A", "B"); addEdge("B", "C"); addEdge("C", "D");
        // Subgraph on {B, C}: only the B-C edge counts
        List<String> sub = Arrays.asList("B", "C");
        double[][] L = LaplacianBuilder.buildSubgraphLaplacian(graph, sub);
        assertEquals(1.0, L[0][0], 1e-10);
        assertEquals(-1.0, L[0][1], 1e-10);
        assertEquals(-1.0, L[1][0], 1e-10);
        assertEquals(1.0, L[1][1], 1e-10);
    }

    @Test
    public void testSubgraphLaplacian_rowSumsZero() {
        addEdge("A", "B"); addEdge("B", "C"); addEdge("C", "A"); addEdge("C", "D");
        List<String> sub = Arrays.asList("A", "B", "C");
        double[][] L = LaplacianBuilder.buildSubgraphLaplacian(graph, sub);
        for (int i = 0; i < 3; i++) {
            double s = 0;
            for (int j = 0; j < 3; j++) s += L[i][j];
            assertEquals(0.0, s, 1e-10);
        }
    }

    // ── Normalized Laplacian ───────────────────────────────────────

    @Test
    public void testNormalizedLaplacian_diagonalIsOneOrZero() {
        addEdge("A", "B"); addEdge("B", "C");
        graph.addVertex("D"); // isolated
        List<String> verts = Arrays.asList("A", "B", "C", "D");
        double[][] Ln = LaplacianBuilder.buildNormalizedLaplacian(graph, verts);
        assertEquals(1.0, Ln[0][0], 1e-10); // A has edges
        assertEquals(1.0, Ln[1][1], 1e-10);
        assertEquals(1.0, Ln[2][2], 1e-10);
        assertEquals(0.0, Ln[3][3], 1e-10); // D isolated
    }

    @Test
    public void testNormalizedLaplacian_regularGraphValues() {
        // Triangle: all degree 2
        addEdge("A", "B"); addEdge("B", "C"); addEdge("C", "A");
        List<String> verts = Arrays.asList("A", "B", "C");
        double[][] Ln = LaplacianBuilder.buildNormalizedLaplacian(graph, verts);
        // Off-diagonal: -1/(sqrt(2)*sqrt(2)) = -0.5
        assertEquals(-0.5, Ln[0][1], 1e-10);
        assertEquals(-0.5, Ln[1][2], 1e-10);
    }

    @Test
    public void testNormalizedLaplacian_symmetric() {
        addEdge("A", "B"); addEdge("B", "C");
        List<String> verts = Arrays.asList("A", "B", "C");
        double[][] Ln = LaplacianBuilder.buildNormalizedLaplacian(graph, verts);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals(Ln[i][j], Ln[j][i], 1e-10);
            }
        }
    }

    // ── Random Walk Laplacian ──────────────────────────────────────

    @Test
    public void testRandomWalkLaplacian_diagonalIsOneOrZero() {
        addEdge("A", "B");
        graph.addVertex("C"); // isolated
        List<String> verts = Arrays.asList("A", "B", "C");
        double[][] Lrw = LaplacianBuilder.buildRandomWalkLaplacian(graph, verts);
        assertEquals(1.0, Lrw[0][0], 1e-10);
        assertEquals(1.0, Lrw[1][1], 1e-10);
        assertEquals(0.0, Lrw[2][2], 1e-10); // isolated
    }

    @Test
    public void testRandomWalkLaplacian_offDiagonalIsDivByDegree() {
        addEdge("A", "B"); addEdge("A", "C");
        List<String> verts = Arrays.asList("A", "B", "C");
        double[][] Lrw = LaplacianBuilder.buildRandomWalkLaplacian(graph, verts);
        // A has degree 2, so off-diag = -1/2
        assertEquals(-0.5, Lrw[0][1], 1e-10);
        assertEquals(-0.5, Lrw[0][2], 1e-10);
    }

    @Test
    public void testRandomWalkLaplacian_rowSumsZero() {
        addEdge("A", "B"); addEdge("B", "C"); addEdge("C", "A");
        List<String> verts = Arrays.asList("A", "B", "C");
        double[][] Lrw = LaplacianBuilder.buildRandomWalkLaplacian(graph, verts);
        for (int i = 0; i < 3; i++) {
            double s = 0;
            for (int j = 0; j < 3; j++) s += Lrw[i][j];
            assertEquals(0.0, s, 1e-10);
        }
    }

    @Test
    public void testRandomWalkLaplacian_fromAdjMatrix() {
        double[][] A = { {0, 1, 1}, {1, 0, 0}, {1, 0, 0} };
        double[][] Lrw = LaplacianBuilder.buildRandomWalkLaplacian(A, 3);
        assertEquals(1.0, Lrw[0][0], 1e-10);
        assertEquals(-0.5, Lrw[0][1], 1e-10);
        assertEquals(-0.5, Lrw[0][2], 1e-10);
    }
}
