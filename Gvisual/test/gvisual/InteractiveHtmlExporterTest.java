package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Tests for InteractiveHtmlExporter.
 */
public class InteractiveHtmlExporterTest {

    private Graph<String, edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
        graph.addVertex("Alice");
        graph.addVertex("Bob");
        graph.addVertex("Carol");
        graph.addVertex("Dave");

        edge e1 = new edge("f", "Alice", "Bob");
        e1.setWeight(1.0f);
        graph.addEdge(e1, "Alice", "Bob");

        edge e2 = new edge("c", "Bob", "Carol");
        e2.setWeight(0.5f);
        graph.addEdge(e2, "Bob", "Carol");

        edge e3 = new edge("sg", "Alice", "Carol");
        e3.setWeight(2.0f);
        graph.addEdge(e3, "Alice", "Carol");

        edge e4 = new edge("fs", "Carol", "Dave");
        e4.setWeight(1.0f);
        graph.addEdge(e4, "Carol", "Dave");
    }

    @Test
    public void testExportToStringContainsHtml() {
        InteractiveHtmlExporter exporter = new InteractiveHtmlExporter(graph);
        String html = exporter.exportToString();
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("</html>"));
        assertTrue(html.contains("d3.v7.min.js"));
    }

    @Test
    public void testContainsAllNodes() {
        InteractiveHtmlExporter exporter = new InteractiveHtmlExporter(graph);
        String html = exporter.exportToString();
        assertTrue(html.contains("\"Alice\""));
        assertTrue(html.contains("\"Bob\""));
        assertTrue(html.contains("\"Carol\""));
        assertTrue(html.contains("\"Dave\""));
    }

    @Test
    public void testContainsEdgeTypes() {
        InteractiveHtmlExporter exporter = new InteractiveHtmlExporter(graph);
        String html = exporter.exportToString();
        assertTrue(html.contains("type:\"f\""));
        assertTrue(html.contains("type:\"c\""));
        assertTrue(html.contains("type:\"sg\""));
        assertTrue(html.contains("type:\"fs\""));
    }

    @Test
    public void testCustomTitle() {
        InteractiveHtmlExporter exporter = new InteractiveHtmlExporter(graph);
        exporter.setTitle("My Network");
        String html = exporter.exportToString();
        assertTrue(html.contains("My Network"));
    }

    @Test
    public void testCustomDescription() {
        InteractiveHtmlExporter exporter = new InteractiveHtmlExporter(graph);
        exporter.setDescription("Test graph for unit tests");
        String html = exporter.exportToString();
        assertTrue(html.contains("Test graph for unit tests"));
    }

    @Test
    public void testDarkMode() {
        InteractiveHtmlExporter exporter = new InteractiveHtmlExporter(graph);
        exporter.setDarkMode(true);
        String html = exporter.exportToString();
        assertTrue(html.contains("class=\"dark\""));
    }

    @Test
    public void testLightModeDefault() {
        InteractiveHtmlExporter exporter = new InteractiveHtmlExporter(graph);
        String html = exporter.exportToString();
        assertFalse(html.contains("class=\"dark\""));
    }

    @Test
    public void testStatsPresent() {
        InteractiveHtmlExporter exporter = new InteractiveHtmlExporter(graph);
        String html = exporter.exportToString();
        assertTrue(html.contains("Nodes"));
        assertTrue(html.contains("Edges"));
        assertTrue(html.contains("Density"));
    }

    @Test
    public void testStatsHidden() {
        InteractiveHtmlExporter exporter = new InteractiveHtmlExporter(graph);
        exporter.setShowStats(false);
        String html = exporter.exportToString();
        assertFalse(html.contains("id=\"sidebar\""));
    }

    @Test
    public void testSearchPresent() {
        InteractiveHtmlExporter exporter = new InteractiveHtmlExporter(graph);
        String html = exporter.exportToString();
        assertTrue(html.contains("id=\"search\""));
    }

    @Test
    public void testSearchHidden() {
        InteractiveHtmlExporter exporter = new InteractiveHtmlExporter(graph);
        exporter.setShowSearch(false);
        String html = exporter.exportToString();
        assertFalse(html.contains("id=\"search\""));
    }

    @Test
    public void testLegendShowsEdgeTypeCounts() {
        InteractiveHtmlExporter exporter = new InteractiveHtmlExporter(graph);
        String html = exporter.exportToString();
        assertTrue(html.contains("Friend"));
        assertTrue(html.contains("Classmate"));
        assertTrue(html.contains("Study Group"));
        assertTrue(html.contains("Facebook"));
    }

    @Test
    public void testContainsD3ForceSimulation() {
        InteractiveHtmlExporter exporter = new InteractiveHtmlExporter(graph);
        String html = exporter.exportToString();
        assertTrue(html.contains("d3.forceSimulation"));
        assertTrue(html.contains("d3.forceLink"));
        assertTrue(html.contains("d3.forceManyBody"));
    }

    @Test
    public void testContainsInteractiveFeatures() {
        InteractiveHtmlExporter exporter = new InteractiveHtmlExporter(graph);
        String html = exporter.exportToString();
        assertTrue(html.contains("zoom"));
        assertTrue(html.contains("drag"));
        assertTrue(html.contains("tooltip"));
        assertTrue(html.contains("theme-toggle"));
    }

    @Test
    public void testExportToFile() throws Exception {
        File tmp = File.createTempFile("graph-test", ".html");
        tmp.deleteOnExit();
        InteractiveHtmlExporter exporter = new InteractiveHtmlExporter(graph);
        exporter.export(tmp);
        assertTrue(tmp.exists());
        assertTrue(tmp.length() > 1000);
    }

    @Test
    public void testEmptyGraph() {
        Graph<String, edge> empty = new UndirectedSparseGraph<>();
        InteractiveHtmlExporter exporter = new InteractiveHtmlExporter(empty);
        String html = exporter.exportToString();
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("nodes: ["));
        assertTrue(html.contains("links: ["));
    }

    @Test
    public void testSingleNodeGraph() {
        Graph<String, edge> single = new UndirectedSparseGraph<>();
        single.addVertex("Lonely");
        InteractiveHtmlExporter exporter = new InteractiveHtmlExporter(single);
        String html = exporter.exportToString();
        assertTrue(html.contains("\"Lonely\""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new InteractiveHtmlExporter(null);
    }

    @Test
    public void testHtmlEscaping() {
        InteractiveHtmlExporter exporter = new InteractiveHtmlExporter(graph);
        exporter.setTitle("Title <b>bold</b>");
        String html = exporter.exportToString();
        assertTrue(html.contains("Title &lt;b&gt;bold&lt;/b&gt;"));
        assertFalse(html.contains("<b>bold</b>"));
    }

    @Test
    public void testNodeDegreeInData() {
        InteractiveHtmlExporter exporter = new InteractiveHtmlExporter(graph);
        String html = exporter.exportToString();
        // Carol has degree 3 (connected to Alice, Bob, Dave)
        assertTrue(html.contains("id:\"Carol\",deg:3"));
    }

    @Test
    public void testEdgeWeightInData() {
        InteractiveHtmlExporter exporter = new InteractiveHtmlExporter(graph);
        String html = exporter.exportToString();
        assertTrue(html.contains("weight:2.0"));
        assertTrue(html.contains("weight:0.5"));
    }
}
