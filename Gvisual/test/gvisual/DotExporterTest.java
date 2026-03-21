package gvisual;

import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * Tests for DotExporter.
 */
public class DotExporterTest {

    private Graph<String, Edge> undirectedGraph;
    private Graph<String, Edge> directedGraph;

    @Before
    public void setUp() {
        // Undirected graph: A--B--C
        undirectedGraph = new UndirectedSparseGraph<>();
        undirectedGraph.addVertex("A");
        undirectedGraph.addVertex("B");
        undirectedGraph.addVertex("C");
        Edge e1 = new Edge("f", "A", "B");
        e1.setWeight(2.0f);
        e1.setLabel("friendship");
        undirectedGraph.addEdge(e1, "A", "B");
        Edge e2 = new Edge("c", "B", "C");
        e2.setWeight(1.0f);
        undirectedGraph.addEdge(e2, "B", "C");

        // Directed graph: X->Y->Z
        directedGraph = new DirectedSparseGraph<>();
        directedGraph.addVertex("X");
        directedGraph.addVertex("Y");
        directedGraph.addVertex("Z");
        Edge d1 = new Edge("s", "X", "Y");
        d1.setWeight(3.0f);
        directedGraph.addEdge(d1, "X", "Y");
        Edge d2 = new Edge("sg", "Y", "Z");
        d2.setWeight(5.0f);
        directedGraph.addEdge(d2, "Y", "Z");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new DotExporter(null);
    }

    @Test
    public void testUndirectedGraphOutput() {
        DotExporter exporter = new DotExporter(undirectedGraph);
        String dot = exporter.exportToString();
        assertTrue("Should start with graph keyword", dot.contains("graph G {"));
        assertTrue("Should use -- connector", dot.contains(" -- "));
        assertFalse("Should not use -> connector", dot.contains(" -> "));
        assertTrue("Should contain node A", dot.contains("\"A\""));
        assertTrue("Should contain node B", dot.contains("\"B\""));
        assertTrue("Should contain node C", dot.contains("\"C\""));
    }

    @Test
    public void testDirectedGraphOutput() {
        DotExporter exporter = new DotExporter(directedGraph);
        exporter.setDirected(true);
        String dot = exporter.exportToString();
        assertTrue("Should start with digraph keyword", dot.contains("digraph G {"));
        assertTrue("Should use -> connector", dot.contains(" -> "));
    }

    @Test
    public void testGraphNameSanitization() {
        DotExporter exporter = new DotExporter(undirectedGraph);
        exporter.setGraphName("My Graph!@#");
        String dot = exporter.exportToString();
        // Special chars should be replaced with underscores
        assertTrue(dot.contains("graph My_Graph___"));
        assertFalse(dot.contains("My Graph!@#"));
    }

    @Test
    public void testNullGraphNameDefaultsToG() {
        DotExporter exporter = new DotExporter(undirectedGraph);
        exporter.setGraphName(null);
        String dot = exporter.exportToString();
        assertTrue(dot.contains("graph G {"));
    }

    @Test
    public void testTimestampAndDescription() {
        DotExporter exporter = new DotExporter(undirectedGraph);
        exporter.setTimestamp("2026-03-15");
        exporter.setDescription("Test export");
        String dot = exporter.exportToString();
        assertTrue(dot.contains("// Timestamp: 2026-03-15"));
        assertTrue(dot.contains("// Test export"));
    }

    @Test
    public void testEdgeTypeColoring() {
        DotExporter exporter = new DotExporter(undirectedGraph);
        exporter.setColorByEdgeType(true);
        String dot = exporter.exportToString();
        // Should have legend subgraph with Edge types
        assertTrue("Should contain legend", dot.contains("cluster_legend"));
        // Should contain the type colors
        assertTrue("Should contain Friend legend", dot.contains("Friend"));
    }

    @Test
    public void testNoEdgeTypeColoring() {
        DotExporter exporter = new DotExporter(undirectedGraph);
        exporter.setColorByEdgeType(false);
        String dot = exporter.exportToString();
        assertFalse("Should not contain legend", dot.contains("cluster_legend"));
    }

    @Test
    public void testEdgeLabelInOutput() {
        DotExporter exporter = new DotExporter(undirectedGraph);
        String dot = exporter.exportToString();
        assertTrue("Should contain Edge label", dot.contains("\"friendship\""));
    }

    @Test
    public void testNodeCountEdgeCountComment() {
        DotExporter exporter = new DotExporter(undirectedGraph);
        String dot = exporter.exportToString();
        assertTrue(dot.contains("Nodes: 3"));
        assertTrue(dot.contains("Edges: 2"));
    }

    @Test
    public void testLayoutEngineComment() {
        DotExporter exporter = new DotExporter(undirectedGraph);
        exporter.setLayoutEngine("fdp");
        String dot = exporter.exportToString();
        assertTrue(dot.contains("// Suggested layout engine: fdp"));
    }

    @Test
    public void testRankDir() {
        DotExporter exporter = new DotExporter(directedGraph);
        exporter.setDirected(true);
        exporter.setRankDir("LR");
        String dot = exporter.exportToString();
        assertTrue(dot.contains("rankdir=LR"));
    }

    @Test
    public void testCustomGraphAttribute() {
        DotExporter exporter = new DotExporter(undirectedGraph);
        exporter.setGraphAttribute("nodesep", "0.5");
        String dot = exporter.exportToString();
        assertTrue(dot.contains("nodesep=\"0.5\""));
    }

    @Test
    public void testCustomTypeColor() {
        DotExporter exporter = new DotExporter(undirectedGraph);
        exporter.setTypeColor("f", "#FF0000");
        String dot = exporter.exportToString();
        assertTrue(dot.contains("#FF0000"));
    }

    @Test
    public void testEmptyGraph() {
        Graph<String, Edge> empty = new UndirectedSparseGraph<>();
        DotExporter exporter = new DotExporter(empty);
        String dot = exporter.exportToString();
        assertTrue(dot.contains("graph G {"));
        assertTrue(dot.contains("Nodes: 0"));
        assertTrue(dot.contains("Edges: 0"));
        assertTrue(dot.endsWith("}\n"));
    }

    @Test
    public void testExportToFile() throws IOException {
        File tempFile = File.createTempFile("dotexporter_test", ".dot");
        tempFile.deleteOnExit();
        DotExporter exporter = new DotExporter(undirectedGraph);
        exporter.export(tempFile);

        String content = new String(Files.readAllBytes(tempFile.toPath()));
        assertTrue(content.contains("graph G {"));
        assertTrue(content.contains("\"A\""));
    }

    @Test
    public void testQuoteEscapesSpecialChars() {
        // Add a vertex with special characters
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("Node\"With\"Quotes");
        g.addVertex("Normal");
        Edge e = new Edge("f", "Node\"With\"Quotes", "Normal");
        g.addEdge(e, "Node\"With\"Quotes", "Normal");

        DotExporter exporter = new DotExporter(g);
        String dot = exporter.exportToString();
        // Quotes inside vertex names should be escaped
        assertTrue(dot.contains("\\\""));
        // Should still be valid DOT (no unescaped quotes breaking structure)
        assertFalse(dot.contains("Node\"With"));
    }

    @Test
    public void testScaleNodesByDegreeDisabled() {
        DotExporter exporter = new DotExporter(undirectedGraph);
        exporter.setScaleNodesByDegree(false);
        String dot = exporter.exportToString();
        // Should not contain fontsize attributes on nodes (only tooltip)
        assertFalse(dot.contains("fontsize=1") && dot.contains("width=0."));
    }

    @Test
    public void testScaleEdgesByWeightDisabled() {
        DotExporter exporter = new DotExporter(undirectedGraph);
        exporter.setScaleEdgesByWeight(false);
        String dot = exporter.exportToString();
        // penwidth should not appear on edges
        assertFalse(dot.contains("penwidth="));
    }

    @Test
    public void testSingleVertexGraph() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("Solo");
        DotExporter exporter = new DotExporter(g);
        String dot = exporter.exportToString();
        assertTrue(dot.contains("\"Solo\""));
        assertTrue(dot.contains("Nodes: 1"));
        assertTrue(dot.contains("Edges: 0"));
    }
}
