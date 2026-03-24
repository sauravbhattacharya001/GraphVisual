package gvisual;

import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

/**
 * Tests for {@link GraphMatrixExporter}.
 */
public class GraphMatrixExporterTest {

    private UndirectedSparseGraph<String, Edge> graph;
    private Collection<Edge> edges;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");

        Edge e1 = new Edge("A", "B", EdgeType.blue);
        Edge e2 = new Edge("B", "C", EdgeType.blue);
        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");
        edges = Arrays.asList(e1, e2);
    }

    @Test
    public void testAdjacencyMatrix() {
        GraphMatrixExporter exporter = new GraphMatrixExporter(graph, edges);
        int[][] adj = exporter.buildAdjacencyMatrix();
        // A-B-C path: A(0)-B(1), B(1)-C(2)
        assertEquals(3, adj.length);
        assertEquals(1, adj[0][1]); // A-B
        assertEquals(1, adj[1][0]); // B-A
        assertEquals(1, adj[1][2]); // B-C
        assertEquals(0, adj[0][2]); // A-C not connected
    }

    @Test
    public void testLaplacianMatrix() {
        GraphMatrixExporter exporter = new GraphMatrixExporter(graph, edges);
        int[][] lap = exporter.buildLaplacianMatrix();
        // Degree: A=1, B=2, C=1
        assertEquals(1, lap[0][0]);  // deg(A)
        assertEquals(2, lap[1][1]);  // deg(B)
        assertEquals(1, lap[2][2]);  // deg(C)
        assertEquals(-1, lap[0][1]); // -A(A,B)
        assertEquals(0, lap[0][2]);  // no edge A-C
    }

    @Test
    public void testIncidenceMatrix() {
        GraphMatrixExporter exporter = new GraphMatrixExporter(graph, edges);
        int[][] inc = exporter.buildIncidenceMatrix();
        assertEquals(3, inc.length);    // 3 nodes
        assertEquals(2, inc[0].length); // 2 edges
        // Each edge column should have exactly 2 ones
        for (int j = 0; j < 2; j++) {
            int sum = 0;
            for (int i = 0; i < 3; i++) sum += inc[i][j];
            assertEquals(2, sum);
        }
    }

    @Test
    public void testCsvOutput() {
        GraphMatrixExporter exporter = new GraphMatrixExporter(graph, edges);
        String csv = exporter.adjacencyCsvToString();
        assertNotNull(csv);
        assertTrue(csv.contains("A"));
        assertTrue(csv.contains("B"));
        assertTrue(csv.contains("C"));
        // Header + 3 rows
        String[] lines = csv.trim().split("\n");
        assertEquals(4, lines.length);
    }

    @Test
    public void testLatexOutput() {
        GraphMatrixExporter exporter = new GraphMatrixExporter(graph, edges);
        String latex = exporter.adjacencyLatexToString();
        assertTrue(latex.contains("\\begin{bmatrix}"));
        assertTrue(latex.contains("\\end{bmatrix}"));
        assertTrue(latex.contains("\\\\"));
    }

    @Test
    public void testFileExport() throws Exception {
        GraphMatrixExporter exporter = new GraphMatrixExporter(graph, edges);
        File tmp = File.createTempFile("adj_test", ".csv");
        tmp.deleteOnExit();
        exporter.exportAdjacencyCsv(tmp);
        assertTrue(tmp.length() > 0);
    }
}
