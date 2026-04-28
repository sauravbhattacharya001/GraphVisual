package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * Tests for DimacsExporter.
 */
public class DimacsExporterTest {

    private Graph<String, Edge> undirectedGraph;
    private Graph<String, Edge> directedGraph;

    @Before
    public void setUp() {
        undirectedGraph = new UndirectedSparseGraph<>();
        undirectedGraph.addVertex("A");
        undirectedGraph.addVertex("B");
        undirectedGraph.addVertex("C");
        undirectedGraph.addEdge(new Edge("e1", "A", "B"), "A", "B");
        undirectedGraph.addEdge(new Edge("e2", "B", "C"), "B", "C");
        undirectedGraph.addEdge(new Edge("e3", "A", "C"), "A", "C");

        directedGraph = new DirectedSparseGraph<>();
        directedGraph.addVertex("X");
        directedGraph.addVertex("Y");
        directedGraph.addEdge(new Edge("d1", "X", "Y"), "X", "Y");
    }

    @Test
    public void vertexAndEdgeCount() {
        DimacsExporter exporter = new DimacsExporter(undirectedGraph);
        assertEquals(3, exporter.getVertexCount());
        assertEquals(3, exporter.getEdgeCount());
    }

    @Test
    public void exportCreatesFile() throws IOException {
        File tmp = File.createTempFile("dimacs", ".col");
        tmp.deleteOnExit();

        DimacsExporter exporter = new DimacsExporter(undirectedGraph);
        exporter.export(tmp);

        assertTrue("File should exist", tmp.exists());
        String content = new String(Files.readAllBytes(tmp.toPath()));
        assertTrue("Should contain problem line", content.contains("p edge 3 3"));
        assertTrue("Should contain edge lines", content.contains("e "));
    }

    @Test
    public void exportWithDescription() throws IOException {
        File tmp = File.createTempFile("dimacs", ".col");
        tmp.deleteOnExit();

        DimacsExporter exporter = new DimacsExporter(undirectedGraph);
        exporter.setDescription("Test graph");
        exporter.export(tmp);

        String content = new String(Files.readAllBytes(tmp.toPath()));
        assertTrue("Should contain description comment", content.contains("Test graph"));
    }

    @Test
    public void exportWithTimestamp() throws IOException {
        File tmp = File.createTempFile("dimacs", ".col");
        tmp.deleteOnExit();

        DimacsExporter exporter = new DimacsExporter(undirectedGraph);
        exporter.setTimestamp("2026-04-28");
        exporter.export(tmp);

        String content = new String(Files.readAllBytes(tmp.toPath()));
        assertTrue("Should contain timestamp", content.contains("2026-04-28"));
    }

    @Test
    public void exportWithSummaryReturnsStats() throws IOException {
        File tmp = File.createTempFile("dimacs", ".col");
        tmp.deleteOnExit();

        DimacsExporter exporter = new DimacsExporter(undirectedGraph);
        String summary = exporter.exportWithSummary(tmp);

        assertNotNull(summary);
        assertFalse("Summary should not be empty", summary.isEmpty());
    }

    @Test
    public void directedGraphExport() throws IOException {
        File tmp = File.createTempFile("dimacs_dir", ".col");
        tmp.deleteOnExit();

        DimacsExporter exporter = new DimacsExporter(directedGraph);
        assertEquals(2, exporter.getVertexCount());
        assertEquals(1, exporter.getEdgeCount());

        exporter.export(tmp);
        String content = new String(Files.readAllBytes(tmp.toPath()));
        assertTrue("Should have problem line", content.contains("p edge 2 1"));
    }

    @Test
    public void singleNodeGraph() throws IOException {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("Solo");

        File tmp = File.createTempFile("dimacs_solo", ".col");
        tmp.deleteOnExit();

        DimacsExporter exporter = new DimacsExporter(g);
        assertEquals(1, exporter.getVertexCount());
        assertEquals(0, exporter.getEdgeCount());
        exporter.export(tmp);

        String content = new String(Files.readAllBytes(tmp.toPath()));
        assertTrue("Problem line reflects single node", content.contains("p edge 1 0"));
    }
}
