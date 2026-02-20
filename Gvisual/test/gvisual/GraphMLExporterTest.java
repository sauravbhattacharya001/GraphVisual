package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link GraphMLExporter}.
 */
public class GraphMLExporterTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Graph<String, edge> graph;
    private List<edge> allEdges;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, edge>();
        allEdges = new ArrayList<edge>();
    }

    // --- Constructor tests ---

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrowsException() {
        new GraphMLExporter(null, allEdges);
    }

    @Test
    public void testNullEdgeListDefaults() {
        GraphMLExporter exporter = new GraphMLExporter(graph, null);
        assertEquals(0, exporter.getEdgeCount());
    }

    // --- Empty graph ---

    @Test
    public void testEmptyGraphExport() {
        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);
        String xml = exporter.exportToString();

        assertTrue(xml.contains("<?xml version=\"1.0\""));
        assertTrue(xml.contains("<graphml"));
        assertTrue(xml.contains("edgedefault=\"undirected\""));
        assertTrue(xml.contains("</graphml>"));
        assertFalse(xml.contains("<node"));
        assertFalse(xml.contains("<edge"));
    }

    // --- Vertex export ---

    @Test
    public void testVerticesExported() {
        graph.addVertex("Alice");
        graph.addVertex("Bob");

        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);
        String xml = exporter.exportToString();

        assertTrue(xml.contains("<node id=\"Alice\">"));
        assertTrue(xml.contains("<node id=\"Bob\">"));
        assertTrue(xml.contains("<data key=\"d0\">Alice</data>"));
        assertTrue(xml.contains("<data key=\"d0\">Bob</data>"));
    }

    @Test
    public void testVerticesSortedDeterministic() {
        graph.addVertex("Charlie");
        graph.addVertex("Alice");
        graph.addVertex("Bob");

        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);
        String xml = exporter.exportToString();

        int alicePos = xml.indexOf("\"Alice\"");
        int bobPos = xml.indexOf("\"Bob\"");
        int charliePos = xml.indexOf("\"Charlie\"");

        assertTrue(alicePos < bobPos);
        assertTrue(bobPos < charliePos);
    }

    // --- Edge export ---

    @Test
    public void testEdgesExported() {
        edge e = new edge("f", "A", "B");
        e.setWeight(42.5f);
        allEdges.add(e);
        graph.addEdge(e, "A", "B");

        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);
        String xml = exporter.exportToString();

        assertTrue(xml.contains("<edge id=\"e0\" source=\"A\" target=\"B\">"));
        assertTrue(xml.contains("<data key=\"d1\">f</data>"));
        assertTrue(xml.contains("<data key=\"d2\">Friend</data>"));
        assertTrue(xml.contains("<data key=\"d3\">42.5</data>"));
    }

    @Test
    public void testEdgeLabelExported() {
        edge e = new edge("c", "X", "Y");
        e.setWeight(10f);
        e.setLabel("Classmate");
        allEdges.add(e);
        graph.addEdge(e, "X", "Y");

        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);
        String xml = exporter.exportToString();

        assertTrue(xml.contains("<data key=\"d4\">Classmate</data>"));
    }

    @Test
    public void testEdgeWithoutLabelOmitted() {
        edge e = new edge("s", "X", "Y");
        e.setWeight(5f);
        allEdges.add(e);
        graph.addEdge(e, "X", "Y");

        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);
        String xml = exporter.exportToString();

        assertFalse(xml.contains("<data key=\"d4\">"));
    }

    // --- All edge types ---

    @Test
    public void testAllEdgeTypeLabels() {
        assertEquals("Friend", GraphMLExporter.getTypeLabel("f"));
        assertEquals("Familiar Stranger", GraphMLExporter.getTypeLabel("fs"));
        assertEquals("Classmate", GraphMLExporter.getTypeLabel("c"));
        assertEquals("Stranger", GraphMLExporter.getTypeLabel("s"));
        assertEquals("Study Group", GraphMLExporter.getTypeLabel("sg"));
        assertEquals("Unknown", GraphMLExporter.getTypeLabel(null));
        assertEquals("xyz", GraphMLExporter.getTypeLabel("xyz"));
    }

    @Test
    public void testMultipleEdgeTypesExported() {
        edge e1 = new edge("f", "A", "B"); e1.setWeight(10f);
        edge e2 = new edge("fs", "B", "C"); e2.setWeight(5f);
        edge e3 = new edge("c", "C", "D"); e3.setWeight(20f);
        edge e4 = new edge("s", "D", "E"); e4.setWeight(3f);
        edge e5 = new edge("sg", "E", "A"); e5.setWeight(15f);
        allEdges.add(e1); allEdges.add(e2); allEdges.add(e3);
        allEdges.add(e4); allEdges.add(e5);
        graph.addEdge(e1, "A", "B");
        graph.addEdge(e2, "B", "C");
        graph.addEdge(e3, "C", "D");
        graph.addEdge(e4, "D", "E");
        graph.addEdge(e5, "E", "A");

        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);
        String xml = exporter.exportToString();

        assertTrue(xml.contains("Friend"));
        assertTrue(xml.contains("Familiar Stranger"));
        assertTrue(xml.contains("Classmate"));
        assertTrue(xml.contains("Stranger"));
        assertTrue(xml.contains("Study Group"));
        assertEquals(5, exporter.getEdgeCount());
    }

    // --- Metadata ---

    @Test
    public void testTimestampMetadata() {
        graph.addVertex("A");
        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);
        exporter.setTimestamp("2011-03-15");

        String xml = exporter.exportToString();
        assertTrue(xml.contains("Timestamp: 2011-03-15"));
    }

    @Test
    public void testDescriptionMetadata() {
        graph.addVertex("A");
        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);
        exporter.setDescription("Test network");

        String xml = exporter.exportToString();
        assertTrue(xml.contains("Test network"));
    }

    @Test
    public void testTimestampAndDescriptionCombined() {
        graph.addVertex("A");
        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);
        exporter.setTimestamp("2011-04-01");
        exporter.setDescription("Spring semester");

        String xml = exporter.exportToString();
        assertTrue(xml.contains("Spring semester"));
        assertTrue(xml.contains("Timestamp: 2011-04-01"));
        assertTrue(xml.contains(" | "));
    }

    @Test
    public void testNoMetadataOmitsDesc() {
        graph.addVertex("A");
        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);

        String xml = exporter.exportToString();
        assertFalse(xml.contains("<desc>"));
    }

    @Test
    public void testGettersSetters() {
        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);
        exporter.setTimestamp("2011-05-01");
        assertEquals("2011-05-01", exporter.getTimestamp());

        exporter.setDescription("My graph");
        assertEquals("My graph", exporter.getDescription());

        exporter.setTimestamp(null);
        assertEquals("", exporter.getTimestamp());

        exporter.setDescription(null);
        assertEquals("", exporter.getDescription());
    }

    // --- XML escaping ---

    @Test
    public void testXmlEscaping() {
        assertEquals("&amp;", GraphMLExporter.escapeXml("&"));
        assertEquals("&lt;", GraphMLExporter.escapeXml("<"));
        assertEquals("&gt;", GraphMLExporter.escapeXml(">"));
        assertEquals("&quot;", GraphMLExporter.escapeXml("\""));
        assertEquals("&apos;", GraphMLExporter.escapeXml("'"));
        assertEquals("hello &amp; world", GraphMLExporter.escapeXml("hello & world"));
        assertEquals("", GraphMLExporter.escapeXml(null));
        assertEquals("", GraphMLExporter.escapeXml(""));
    }

    @Test
    public void testVertexWithSpecialCharsEscaped() {
        graph.addVertex("A&B");
        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);
        String xml = exporter.exportToString();

        assertTrue(xml.contains("A&amp;B"));
        assertFalse(xml.contains("\"A&B\"")); // raw ampersand must be escaped
    }

    // --- File export ---

    @Test
    public void testExportToFile() throws IOException {
        edge e = new edge("f", "X", "Y");
        e.setWeight(99f);
        allEdges.add(e);
        graph.addEdge(e, "X", "Y");

        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);
        exporter.setTimestamp("2011-03-15");

        File outFile = tempFolder.newFile("test-graph.graphml");
        exporter.export(outFile);

        assertTrue(outFile.exists());
        assertTrue(outFile.length() > 0);

        String content = new String(Files.readAllBytes(outFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(content.contains("<?xml"));
        assertTrue(content.contains("<node id=\"X\">"));
        assertTrue(content.contains("<node id=\"Y\">"));
        assertTrue(content.contains("<edge id=\"e0\""));
        assertTrue(content.contains("Timestamp: 2011-03-15"));
    }

    // --- Counts ---

    @Test
    public void testVertexCount() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");

        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);
        assertEquals(3, exporter.getVertexCount());
    }

    @Test
    public void testEdgeCountUsesAllEdges() {
        edge e1 = new edge("f", "A", "B"); e1.setWeight(10f);
        edge e2 = new edge("c", "C", "D"); e2.setWeight(20f);
        allEdges.add(e1);
        allEdges.add(e2);
        // Only add one to graph (simulating filter)
        graph.addEdge(e1, "A", "B");
        graph.addVertex("C");
        graph.addVertex("D");

        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);
        assertEquals(2, exporter.getEdgeCount());
    }

    @Test
    public void testEdgeCountFallsBackToGraphEdges() {
        edge e1 = new edge("f", "A", "B"); e1.setWeight(10f);
        graph.addEdge(e1, "A", "B");

        GraphMLExporter exporter = new GraphMLExporter(graph, new ArrayList<edge>());
        assertEquals(1, exporter.getEdgeCount());
    }

    // --- GraphML key definitions ---

    @Test
    public void testKeyDefinitionsPresent() {
        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);
        String xml = exporter.exportToString();

        assertTrue(xml.contains("key id=\"d0\""));
        assertTrue(xml.contains("key id=\"d1\""));
        assertTrue(xml.contains("key id=\"d2\""));
        assertTrue(xml.contains("key id=\"d3\""));
        assertTrue(xml.contains("key id=\"d4\""));
        assertTrue(xml.contains("attr.name=\"type\""));
        assertTrue(xml.contains("attr.name=\"weight\""));
        assertTrue(xml.contains("attr.name=\"type_label\""));
    }

    // --- Export visible only ---

    @Test
    public void testExportVisibleOnly() {
        edge e1 = new edge("f", "A", "B"); e1.setWeight(10f);
        edge e2 = new edge("c", "C", "D"); e2.setWeight(20f);
        allEdges.add(e1);
        allEdges.add(e2);
        // Only e1 in graph
        graph.addEdge(e1, "A", "B");
        graph.addVertex("C");
        graph.addVertex("D");

        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);

        // Full export should have 2 edges
        String fullXml = exporter.exportToString();
        assertTrue(fullXml.contains("e0"));
        assertTrue(fullXml.contains("e1"));

        // Visible-only export should have 1 edge
        String visibleXml = exporter.exportVisibleToString();
        assertTrue(visibleXml.contains("e0"));
        assertFalse(visibleXml.contains("e1"));

        // allEdges should be restored after visible export
        assertEquals(2, exporter.getEdgeCount());
    }

    // --- Large graph ---

    @Test
    public void testLargeGraphExport() {
        for (int i = 0; i < 100; i++) {
            graph.addVertex("N" + i);
        }
        for (int i = 0; i < 99; i++) {
            edge e = new edge("f", "N" + i, "N" + (i + 1));
            e.setWeight(i * 1.5f);
            allEdges.add(e);
            graph.addEdge(e, "N" + i, "N" + (i + 1));
        }

        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);
        assertEquals(100, exporter.getVertexCount());
        assertEquals(99, exporter.getEdgeCount());

        String xml = exporter.exportToString();
        assertTrue(xml.contains("<node id=\"N0\">"));
        assertTrue(xml.contains("<node id=\"N99\">"));
        assertTrue(xml.contains("<edge id=\"e0\""));
        assertTrue(xml.contains("<edge id=\"e98\""));
    }

    // --- Edge weight formatting ---

    @Test
    public void testEdgeWeightFormatting() {
        edge e = new edge("f", "A", "B");
        e.setWeight(3.14159f);
        allEdges.add(e);
        graph.addEdge(e, "A", "B");

        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);
        String xml = exporter.exportToString();

        // Weight should be formatted to 1 decimal place
        assertTrue(xml.contains("<data key=\"d3\">3.1</data>"));
    }

    @Test
    public void testZeroWeightEdge() {
        edge e = new edge("s", "A", "B");
        e.setWeight(0f);
        allEdges.add(e);
        graph.addEdge(e, "A", "B");

        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);
        String xml = exporter.exportToString();

        assertTrue(xml.contains("<data key=\"d3\">0.0</data>"));
    }

    // --- GraphML schema ---

    @Test
    public void testGraphMLNamespace() {
        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);
        String xml = exporter.exportToString();

        assertTrue(xml.contains("xmlns=\"http://graphml.graphstruct.org/xmlns\""));
        assertTrue(xml.contains("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""));
    }

    @Test
    public void testGraphIdAndEdgeDefault() {
        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);
        String xml = exporter.exportToString();

        assertTrue(xml.contains("id=\"G\""));
        assertTrue(xml.contains("edgedefault=\"undirected\""));
    }

    // --- Edge with empty label ---

    @Test
    public void testEdgeWithEmptyLabelOmitted() {
        edge e = new edge("f", "A", "B");
        e.setWeight(10f);
        e.setLabel("");
        allEdges.add(e);
        graph.addEdge(e, "A", "B");

        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);
        String xml = exporter.exportToString();

        assertFalse(xml.contains("<data key=\"d4\">"));
    }

    // --- Isolated vertices with edges ---

    @Test
    public void testIsolatedVerticesExported() {
        graph.addVertex("Lonely");
        edge e = new edge("f", "A", "B");
        e.setWeight(10f);
        allEdges.add(e);
        graph.addEdge(e, "A", "B");

        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);
        String xml = exporter.exportToString();

        assertEquals(3, exporter.getVertexCount());
        assertTrue(xml.contains("<node id=\"Lonely\">"));
    }

    // --- Multiple exports produce same output ---

    @Test
    public void testDeterministicOutput() {
        edge e = new edge("f", "A", "B");
        e.setWeight(10f);
        allEdges.add(e);
        graph.addEdge(e, "A", "B");

        GraphMLExporter exporter = new GraphMLExporter(graph, allEdges);
        exporter.setTimestamp("2011-03-15");

        String xml1 = exporter.exportToString();
        String xml2 = exporter.exportToString();

        assertEquals(xml1, xml2);
    }
}
