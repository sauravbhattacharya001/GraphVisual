package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Tests for DimacsExporter — DIMACS format output for JUNG graphs.
 */
public class DimacsExporterTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Graph<String, Edge> triangleGraph;
    private Graph<String, Edge> emptyGraph;
    private Graph<String, Edge> singleVertexGraph;

    @Before
    public void setUp() {
        // Triangle: A--B--C--A
        triangleGraph = new UndirectedSparseGraph<>();
        triangleGraph.addVertex("A");
        triangleGraph.addVertex("B");
        triangleGraph.addVertex("C");
        triangleGraph.addEdge(new Edge("e1", "A", "B"), "A", "B");
        triangleGraph.addEdge(new Edge("e2", "B", "C"), "B", "C");
        triangleGraph.addEdge(new Edge("e3", "A", "C"), "A", "C");

        // Empty graph (no vertices)
        emptyGraph = new UndirectedSparseGraph<>();

        // Single vertex, no edges
        singleVertexGraph = new UndirectedSparseGraph<>();
        singleVertexGraph.addVertex("Solo");
    }

    @Test
    public void testVertexAndEdgeCount() {
        DimacsExporter exporter = new DimacsExporter(triangleGraph);
        assertEquals(3, exporter.getVertexCount());
        assertEquals(3, exporter.getEdgeCount());
    }

    @Test
    public void testExportTriangle() throws IOException {
        DimacsExporter exporter = new DimacsExporter(triangleGraph);
        File outFile = tempFolder.newFile("triangle.col");
        exporter.export(outFile);

        List<String> lines = Files.readAllLines(outFile.toPath());

        // Find problem line
        String problemLine = lines.stream()
                .filter(l -> l.startsWith("p "))
                .findFirst()
                .orElse(null);
        assertNotNull("Must contain a problem line", problemLine);
        assertEquals("p edge 3 3", problemLine);

        // Count edge lines
        long edgeLines = lines.stream().filter(l -> l.startsWith("e ")).count();
        assertEquals(3, edgeLines);

        // All edge lines should have 1-based vertex IDs
        List<String> edges = lines.stream()
                .filter(l -> l.startsWith("e "))
                .collect(Collectors.toList());
        for (String edge : edges) {
            String[] parts = edge.split(" ");
            assertEquals(3, parts.length);
            int u = Integer.parseInt(parts[1]);
            int v = Integer.parseInt(parts[2]);
            assertTrue("Vertex IDs must be 1-based", u >= 1 && u <= 3);
            assertTrue("Vertex IDs must be 1-based", v >= 1 && v <= 3);
            assertTrue("Edge endpoints must differ", u != v);
        }
    }

    @Test
    public void testExportContainsComments() throws IOException {
        DimacsExporter exporter = new DimacsExporter(triangleGraph);
        exporter.setDescription("Test triangle");
        File outFile = tempFolder.newFile("commented.col");
        exporter.export(outFile);

        String content = new String(Files.readAllBytes(outFile.toPath()));
        assertTrue("Should contain header comment", content.contains("c DIMACS graph exported by GraphVisual"));
        assertTrue("Should contain description", content.contains("c Description: Test triangle"));
    }

    @Test
    public void testExportWithTimestamp() throws IOException {
        DimacsExporter exporter = new DimacsExporter(triangleGraph);
        exporter.setTimestamp("2026-04-03T08:00:00Z");
        File outFile = tempFolder.newFile("timestamped.col");
        exporter.export(outFile);

        String content = new String(Files.readAllBytes(outFile.toPath()));
        assertTrue("Should contain timestamp", content.contains("c Timestamp: 2026-04-03T08:00:00Z"));
    }

    @Test
    public void testExportEmptyGraph() throws IOException {
        DimacsExporter exporter = new DimacsExporter(emptyGraph);
        File outFile = tempFolder.newFile("empty.col");
        exporter.export(outFile);

        List<String> lines = Files.readAllLines(outFile.toPath());
        String problemLine = lines.stream()
                .filter(l -> l.startsWith("p "))
                .findFirst()
                .orElse(null);
        assertNotNull(problemLine);
        assertEquals("p edge 0 0", problemLine);

        long edgeLines = lines.stream().filter(l -> l.startsWith("e ")).count();
        assertEquals(0, edgeLines);
    }

    @Test
    public void testExportSingleVertex() throws IOException {
        DimacsExporter exporter = new DimacsExporter(singleVertexGraph);
        File outFile = tempFolder.newFile("single.col");
        exporter.export(outFile);

        List<String> lines = Files.readAllLines(outFile.toPath());
        String problemLine = lines.stream()
                .filter(l -> l.startsWith("p "))
                .findFirst()
                .orElse(null);
        assertEquals("p edge 1 0", problemLine);

        // Vertex mapping should list the single vertex
        assertTrue(lines.stream().anyMatch(l -> l.contains("Solo")));
    }

    @Test
    public void testExportWithSummary() throws IOException {
        DimacsExporter exporter = new DimacsExporter(triangleGraph);
        File outFile = tempFolder.newFile("summary.col");
        String summary = exporter.exportWithSummary(outFile);

        assertNotNull(summary);
        assertTrue(summary.contains("DIMACS exported successfully"));
        assertTrue(summary.contains("Nodes: 3"));
    }

    @Test(expected = NullPointerException.class)
    public void testNullGraphThrows() {
        new DimacsExporter(null);
    }

    @Test
    public void testSetDescriptionNull() throws IOException {
        DimacsExporter exporter = new DimacsExporter(triangleGraph);
        exporter.setDescription(null);
        // Should not throw — null treated as empty
        File outFile = tempFolder.newFile("nulldesc.col");
        exporter.export(outFile);

        String content = new String(Files.readAllBytes(outFile.toPath()));
        assertFalse("Null description should not produce description line",
                content.contains("c Description:"));
    }

    @Test
    public void testVertexMappingInComments() throws IOException {
        DimacsExporter exporter = new DimacsExporter(triangleGraph);
        File outFile = tempFolder.newFile("mapping.col");
        exporter.export(outFile);

        List<String> mappingLines = Files.readAllLines(outFile.toPath()).stream()
                .filter(l -> l.startsWith("c   "))
                .collect(Collectors.toList());
        assertEquals("Should have 3 vertex mapping lines", 3, mappingLines.size());
        // Sorted order: A=1, B=2, C=3
        assertTrue(mappingLines.get(0).contains("1 = A"));
        assertTrue(mappingLines.get(1).contains("2 = B"));
        assertTrue(mappingLines.get(2).contains("3 = C"));
    }

    @Test
    public void testNoDuplicateEdges() throws IOException {
        // Add the same logical edge twice via different Edge objects
        Graph<String, Edge> graph = new UndirectedSparseGraph<>();
        graph.addVertex("X");
        graph.addVertex("Y");
        graph.addEdge(new Edge("e1", "X", "Y"), "X", "Y");

        DimacsExporter exporter = new DimacsExporter(graph);
        File outFile = tempFolder.newFile("nodup.col");
        exporter.export(outFile);

        long edgeLines = Files.readAllLines(outFile.toPath()).stream()
                .filter(l -> l.startsWith("e "))
                .count();
        assertEquals("Should have exactly 1 edge line", 1, edgeLines);
    }
}
