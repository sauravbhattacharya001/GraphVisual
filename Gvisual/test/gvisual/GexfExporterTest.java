package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for GexfExporter.
 */
public class GexfExporterTest {

    private Graph<String, Edge> graph;
    private List<Edge> edges;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
        graph.addVertex("Alice");
        graph.addVertex("Bob");
        graph.addVertex("Carol");

        edge e1 = new Edge("f", "Alice", "Bob");
        e1.setWeight(3.5f);
        e1.setLabel("friends");
        graph.addEdge(e1, "Alice", "Bob");

        edge e2 = new Edge("c", "Bob", "Carol");
        e2.setWeight(1.0f);
        graph.addEdge(e2, "Bob", "Carol");

        edges = new ArrayList<>();
        edges.add(e1);
        edges.add(e2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new GexfExporter(null, null);
    }

    @Test
    public void testExportToStringContainsGexfStructure() {
        GexfExporter exporter = new GexfExporter(graph, edges);
        String xml = exporter.exportToString();

        assertTrue(xml.contains("<?xml"));
        assertTrue(xml.contains("<gexf"));
        assertTrue(xml.contains("<nodes>"));
        assertTrue(xml.contains("<edges>"));
        assertTrue(xml.contains("</gexf>"));
        assertTrue(xml.contains("Alice"));
        assertTrue(xml.contains("Bob"));
        assertTrue(xml.contains("Carol"));
    }

    @Test
    public void testEdgeTypeAttribute() {
        GexfExporter exporter = new GexfExporter(graph, edges);
        String xml = exporter.exportToString();

        // Edge type 'f' should appear as an attribute value
        assertTrue(xml.contains("value=\"f\""));
        assertTrue(xml.contains("value=\"c\""));
    }

    @Test
    public void testVizColorPresent() {
        GexfExporter exporter = new GexfExporter(graph, edges);
        String xml = exporter.exportToString();
        assertTrue(xml.contains("viz:color"));
        assertTrue(xml.contains("viz:size"));
    }

    @Test
    public void testVizDataCanBeDisabled() {
        GexfExporter exporter = new GexfExporter(graph, edges);
        exporter.setIncludeVizData(false);
        String xml = exporter.exportToString();
        assertFalse(xml.contains("viz:color"));
        assertFalse(xml.contains("viz:size"));
    }

    @Test
    public void testTemporalEdgesEnableDynamicMode() {
        edge e1 = new Edge("f", "Alice", "Bob");
        e1.setWeight(1.0f);
        e1.setTimestamp(1000L);
        e1.setEndTimestamp(5000L);

        Graph<String, Edge> tGraph = new UndirectedSparseGraph<>();
        tGraph.addVertex("Alice");
        tGraph.addVertex("Bob");
        tGraph.addEdge(e1, "Alice", "Bob");

        GexfExporter exporter = new GexfExporter(tGraph, null);
        String xml = exporter.exportToString();

        assertTrue(xml.contains("mode=\"dynamic\""));
        assertTrue(xml.contains("<spells>"));
        assertTrue(xml.contains("start=\"1000.0\""));
        assertTrue(xml.contains("end=\"5000.0\""));
    }

    @Test
    public void testExportToFile() throws IOException {
        File tmpFile = File.createTempFile("gexf_test_", ".gexf");
        tmpFile.deleteOnExit();

        GexfExporter exporter = new GexfExporter(graph, edges);
        exporter.setCreator("TestSuite");
        exporter.setDescription("Unit test graph");
        exporter.export(tmpFile);

        assertTrue(tmpFile.exists());
        String content = new String(Files.readAllBytes(tmpFile.toPath()));
        assertTrue(content.contains("<gexf"));
        assertTrue(content.contains("TestSuite"));
        assertTrue(content.contains("Unit test graph"));
    }

    @Test
    public void testXmlEscaping() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A&B");
        g.addVertex("C<D>");

        edge e = new Edge("f", "A&B", "C<D>");
        e.setWeight(1.0f);
        g.addEdge(e, "A&B", "C<D>");

        GexfExporter exporter = new GexfExporter(g, null);
        String xml = exporter.exportToString();

        assertTrue(xml.contains("A&amp;B"));
        assertTrue(xml.contains("C&lt;D&gt;"));
        // Should not contain unescaped special chars in attribute values
        assertFalse(xml.contains("id=\"A&B\""));
    }

    @Test
    public void testEmptyGraph() {
        Graph<String, Edge> empty = new UndirectedSparseGraph<>();
        GexfExporter exporter = new GexfExporter(empty, new ArrayList<>());
        String xml = exporter.exportToString();

        assertTrue(xml.contains("<nodes>"));
        assertTrue(xml.contains("</nodes>"));
        assertTrue(xml.contains("<edges>"));
        assertTrue(xml.contains("</edges>"));
    }

    @Test
    public void testMetadata() {
        GexfExporter exporter = new GexfExporter(graph, edges);
        exporter.setCreator("MyApp");
        exporter.setDescription("Test description");
        String xml = exporter.exportToString();

        assertTrue(xml.contains("<creator>MyApp</creator>"));
        assertTrue(xml.contains("<description>Test description</description>"));
    }
}
