package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link CsvReportExporter}.
 */
public class CsvReportExporterTest {

    // --- Construction ---

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new CsvReportExporter(null, null);
    }

    @Test
    public void testNullEdgeListAccepted() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        CsvReportExporter exporter = new CsvReportExporter(g, null);
        assertNotNull(exporter.exportToString());
    }

    // --- Empty graph ---

    @Test
    public void testEmptyGraphProducesHeaderOnly() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        CsvReportExporter exporter = new CsvReportExporter(g, new ArrayList<edge>());
        exporter.setTimestamp("2026-01-01 00:00:00");
        String csv = exporter.exportToString();

        assertTrue(csv.contains("# GraphVisual CSV Report"));
        assertTrue(csv.contains("Nodes: 0"));
        assertTrue(csv.contains("Node,Degree,"));
        // No data rows
        String[] lines = csv.split("\n");
        assertEquals("Should have 3 lines (2 comments + header)", 3, lines.length);
    }

    // --- Single node, no edges ---

    @Test
    public void testSingleNodeNoEdges() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("Alice");
        CsvReportExporter exporter = new CsvReportExporter(g, new ArrayList<edge>());
        String csv = exporter.exportToString();

        assertTrue(csv.contains("Nodes: 1"));
        assertTrue(csv.contains("Alice,0,0,0,0,0,0,"));
        assertTrue(csv.contains("false")); // not an articulation point
    }

    // --- Triangle graph ---

    @Test
    public void testTriangleGraphMetrics() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        List<edge> edges = new ArrayList<>();

        edge e1 = new edge("f", "A", "B");
        edge e2 = new edge("f", "B", "C");
        edge e3 = new edge("f", "A", "C");
        edges.add(e1);
        edges.add(e2);
        edges.add(e3);
        g.addEdge(e1, "A", "B");
        g.addEdge(e2, "B", "C");
        g.addEdge(e3, "A", "C");

        CsvReportExporter exporter = new CsvReportExporter(g, edges);
        String csv = exporter.exportToString();

        // 3 nodes, 3 edges
        assertTrue(csv.contains("Nodes: 3"));
        assertTrue(csv.contains("Edges: 3"));

        // Each node has degree 2, friend edge count 2
        // Clustering coefficient of a triangle node = 1.0
        String[] lines = csv.split("\n");
        // 2 comment + 1 header + 3 data = 6 lines
        assertEquals(6, lines.length);

        // Nodes sorted: A, B, C
        assertTrue(lines[3].startsWith("A,2,2,0,0,0,0,"));
        assertTrue(lines[3].endsWith(",1.000000,false"));

        // No articulation points in a triangle
        assertFalse(csv.contains("true"));
    }

    // --- Bridge graph (articulation point) ---

    @Test
    public void testBridgeGraphHasArticulationPoint() {
        // A-B-C where B is an articulation point
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        List<edge> edges = new ArrayList<>();

        edge e1 = new edge("f", "A", "B");
        edge e2 = new edge("c", "B", "C");
        edges.add(e1);
        edges.add(e2);
        g.addEdge(e1, "A", "B");
        g.addEdge(e2, "B", "C");

        CsvReportExporter exporter = new CsvReportExporter(g, edges);
        String csv = exporter.exportToString();

        // B should be an articulation point
        String[] lines = csv.split("\n");
        // Find line for B
        String bLine = null;
        for (String line : lines) {
            if (line.startsWith("B,")) {
                bLine = line;
                break;
            }
        }
        assertNotNull("Node B row should exist", bLine);
        assertTrue("B should be articulation point", bLine.endsWith("true"));

        // A and C should NOT be articulation points
        for (String line : lines) {
            if (line.startsWith("A,") || line.startsWith("C,")) {
                assertTrue(line.endsWith("false"));
            }
        }
    }

    // --- Edge type counts ---

    @Test
    public void testEdgeTypeCounts() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        List<edge> edges = new ArrayList<>();

        edge e1 = new edge("f", "X", "Y");   // friend
        edge e2 = new edge("fs", "X", "Z");  // familiar stranger
        edge e3 = new edge("sg", "Y", "Z");  // study group
        edges.add(e1);
        edges.add(e2);
        edges.add(e3);
        g.addEdge(e1, "X", "Y");
        g.addEdge(e2, "X", "Z");
        g.addEdge(e3, "Y", "Z");

        CsvReportExporter exporter = new CsvReportExporter(g, edges);
        String csv = exporter.exportToString();

        // X has: 1 friend, 1 fs, 0 classmate, 0 stranger, 0 sg
        String[] lines = csv.split("\n");
        String xLine = null;
        for (String line : lines) {
            if (line.startsWith("X,")) { xLine = line; break; }
        }
        assertNotNull(xLine);
        // X,2,1,1,0,0,0,...
        assertTrue("X should have 1 friend and 1 fs edge",
                   xLine.startsWith("X,2,1,1,0,0,0,"));
    }

    // --- CSV escaping ---

    @Test
    public void testNodeNameWithCommaIsEscaped() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("Last, First");
        CsvReportExporter exporter = new CsvReportExporter(g, new ArrayList<edge>());
        String csv = exporter.exportToString();
        assertTrue("Comma in node name should be quoted",
                   csv.contains("\"Last, First\""));
    }

    // --- File export ---

    @Test
    public void testExportToFile() throws IOException {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("Solo");
        CsvReportExporter exporter = new CsvReportExporter(g, new ArrayList<edge>());

        File tempFile = File.createTempFile("graph-report-", ".csv");
        tempFile.deleteOnExit();
        exporter.export(tempFile);

        String content = new String(Files.readAllBytes(tempFile.toPath()), "UTF-8");
        assertTrue(content.contains("Solo,0,"));
        assertTrue(content.contains("# GraphVisual CSV Report"));
    }

    // --- setTimestamp ---

    @Test
    public void testSetTimestamp() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        CsvReportExporter exporter = new CsvReportExporter(g, new ArrayList<edge>());
        exporter.setTimestamp("2099-12-31 23:59:59");
        String csv = exporter.exportToString();
        assertTrue(csv.contains("2099-12-31 23:59:59"));
    }

    // --- Deterministic ordering ---

    @Test
    public void testNodeOutputIsSorted() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("Charlie");
        g.addVertex("Alice");
        g.addVertex("Bob");
        CsvReportExporter exporter = new CsvReportExporter(g, new ArrayList<edge>());
        String csv = exporter.exportToString();

        int idxA = csv.indexOf("Alice,");
        int idxB = csv.indexOf("Bob,");
        int idxC = csv.indexOf("Charlie,");
        assertTrue("Alice before Bob", idxA < idxB);
        assertTrue("Bob before Charlie", idxB < idxC);
    }

    // --- CSV formula injection prevention ---

    @Test
    public void testFormulaInjectionNodeEquals() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("=CMD()");
        CsvReportExporter exporter = new CsvReportExporter(g, new ArrayList<edge>());
        String csv = exporter.exportToString();
        assertTrue("Formula prefix defused", csv.contains("\"'=CMD()\""));
        assertFalse("Raw formula not present", csv.contains(",=CMD(),"));
    }

    @Test
    public void testFormulaInjectionNodePlus() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("+1234");
        CsvReportExporter exporter = new CsvReportExporter(g, new ArrayList<edge>());
        String csv = exporter.exportToString();
        assertTrue("Plus-prefix defused", csv.contains("\"'+1234\""));
    }

    @Test
    public void testFormulaInjectionNodeMinus() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("-DROP");
        CsvReportExporter exporter = new CsvReportExporter(g, new ArrayList<edge>());
        String csv = exporter.exportToString();
        assertTrue("Minus-prefix defused", csv.contains("\"'-DROP\""));
    }

    @Test
    public void testFormulaInjectionNodeAt() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("@SUM(A1:A10)");
        CsvReportExporter exporter = new CsvReportExporter(g, new ArrayList<edge>());
        String csv = exporter.exportToString();
        assertTrue("At-prefix defused", csv.contains("\"'@SUM(A1:A10)\""));
    }

    @Test
    public void testSafeNodeNotPrefixed() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("Alice");
        CsvReportExporter exporter = new CsvReportExporter(g, new ArrayList<edge>());
        String csv = exporter.exportToString();
        assertTrue("Safe name present", csv.contains("Alice,"));
        assertFalse("No spurious quoting", csv.contains("\"'Alice\""));
    }
}
