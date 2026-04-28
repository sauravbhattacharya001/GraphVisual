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
 * Tests for AdjacencyListExporter.
 */
public class AdjacencyListExporterTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge(new Edge("e1", "A", "B"), "A", "B");
        graph.addEdge(new Edge("e2", "B", "C"), "B", "C");
        graph.addEdge(new Edge("e3", "A", "C"), "A", "C");
    }

    @Test
    public void nodeAndEdgeCount() {
        AdjacencyListExporter exporter = new AdjacencyListExporter(graph);
        assertEquals(3, exporter.getNodeCount());
        assertEquals(3, exporter.getEdgeCount());
    }

    @Test
    public void exportPlainText() throws IOException {
        File tmp = File.createTempFile("adjlist", ".txt");
        tmp.deleteOnExit();

        AdjacencyListExporter exporter = new AdjacencyListExporter(graph);
        exporter.exportPlainText(tmp);

        assertTrue("File should exist", tmp.exists());
        String content = new String(Files.readAllBytes(tmp.toPath()));
        assertTrue("Should contain vertex A", content.contains("A"));
        assertTrue("Should contain vertex B", content.contains("B"));
        assertTrue("Should contain vertex C", content.contains("C"));
    }

    @Test
    public void exportPython() throws IOException {
        File tmp = File.createTempFile("adjlist", ".py");
        tmp.deleteOnExit();

        AdjacencyListExporter exporter = new AdjacencyListExporter(graph);
        exporter.exportPython(tmp);

        String content = new String(Files.readAllBytes(tmp.toPath()));
        assertTrue("Should be Python-style", content.contains("{") || content.contains("graph"));
    }

    @Test
    public void exportMatlab() throws IOException {
        File tmp = File.createTempFile("adjlist", ".m");
        tmp.deleteOnExit();

        AdjacencyListExporter exporter = new AdjacencyListExporter(graph);
        exporter.exportMatlab(tmp);

        String content = new String(Files.readAllBytes(tmp.toPath()));
        assertFalse("Matlab output should not be empty", content.trim().isEmpty());
    }

    @Test
    public void exportMathematica() throws IOException {
        File tmp = File.createTempFile("adjlist", ".wl");
        tmp.deleteOnExit();

        AdjacencyListExporter exporter = new AdjacencyListExporter(graph);
        exporter.exportMathematica(tmp);

        String content = new String(Files.readAllBytes(tmp.toPath()));
        assertFalse("Mathematica output should not be empty", content.trim().isEmpty());
    }

    @Test
    public void exportAllCreatesSummary() throws IOException {
        File baseFile = File.createTempFile("adjlist_all", ".txt");
        baseFile.deleteOnExit();

        AdjacencyListExporter exporter = new AdjacencyListExporter(graph);
        String summary = exporter.exportAll(baseFile);

        assertNotNull("exportAll should return a summary", summary);
        assertFalse("Summary should not be empty", summary.isEmpty());
    }

    @Test
    public void directedGraphExport() throws IOException {
        Graph<String, Edge> dg = new DirectedSparseGraph<>();
        dg.addVertex("X");
        dg.addVertex("Y");
        dg.addVertex("Z");
        dg.addEdge(new Edge("d1", "X", "Y"), "X", "Y");
        dg.addEdge(new Edge("d2", "Y", "Z"), "Y", "Z");

        File tmp = File.createTempFile("adjlist_dir", ".txt");
        tmp.deleteOnExit();

        AdjacencyListExporter exporter = new AdjacencyListExporter(dg);
        assertEquals(3, exporter.getNodeCount());
        assertEquals(2, exporter.getEdgeCount());

        exporter.exportPlainText(tmp);
        String content = new String(Files.readAllBytes(tmp.toPath()));
        assertTrue("Should contain vertices", content.contains("X"));
    }

    @Test
    public void emptyGraphExport() throws IOException {
        Graph<String, Edge> empty = new UndirectedSparseGraph<>();

        File tmp = File.createTempFile("adjlist_empty", ".txt");
        tmp.deleteOnExit();

        AdjacencyListExporter exporter = new AdjacencyListExporter(empty);
        assertEquals(0, exporter.getNodeCount());
        assertEquals(0, exporter.getEdgeCount());

        exporter.exportPlainText(tmp);
        assertTrue("File should exist even for empty graph", tmp.exists());
    }
}
