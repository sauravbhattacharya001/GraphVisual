package gvisual;

import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * Tests for {@link MermaidExporter}.
 *
 * @author sauravbhattacharya001
 */
public class MermaidExporterTest {

    private Graph<String, Edge> undirected;
    private Graph<String, Edge> directed;

    @Before
    public void setUp() {
        undirected = new UndirectedSparseGraph<>();
        undirected.addVertex("Alice");
        undirected.addVertex("Bob");
        undirected.addVertex("Carol");
        Edge e1 = new Edge("f", "Alice", "Bob");
        e1.setWeight(2.0f);
        e1.setLabel("best friends");
        undirected.addEdge(e1, "Alice", "Bob");
        Edge e2 = new Edge("c", "Bob", "Carol");
        e2.setWeight(1.0f);
        undirected.addEdge(e2, "Bob", "Carol");

        directed = new DirectedSparseGraph<>();
        directed.addVertex("X");
        directed.addVertex("Y");
        directed.addVertex("Z");
        Edge d1 = new Edge("s", "X", "Y");
        d1.setWeight(3.0f);
        directed.addEdge(d1, "X", "Y");
        Edge d2 = new Edge("sg", "Y", "Z");
        d2.setWeight(5.0f);
        directed.addEdge(d2, "Y", "Z");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new MermaidExporter(null);
    }

    @Test
    public void testDefaultHeaderIsFlowchartTD() {
        MermaidExporter exporter = new MermaidExporter(undirected);
        String out = exporter.exportToString();
        assertTrue("Should declare a flowchart", out.contains("flowchart TD"));
        // No directed arrows in default undirected mode
        assertFalse("Should not use directed arrows", out.contains(" --> "));
        assertTrue("Should use undirected connector",
                out.contains(" --- ") || out.contains(" ---| ") || out.contains("---"));
    }

    @Test
    public void testLegacyGraphKeyword() {
        MermaidExporter exporter = new MermaidExporter(undirected);
        exporter.setUseFlowchartKeyword(false);
        String out = exporter.exportToString();
        assertTrue("Should fall back to graph keyword", out.contains("graph TD"));
        assertFalse("Must not also emit flowchart", out.contains("flowchart TD"));
    }

    @Test
    public void testDirectedArrows() {
        MermaidExporter exporter = new MermaidExporter(directed);
        exporter.setDirected(true);
        String out = exporter.exportToString();
        assertTrue("Should contain --> arrows", out.contains("-->"));
    }

    @Test
    public void testOrientationLR() {
        MermaidExporter exporter = new MermaidExporter(undirected);
        exporter.setOrientation("LR");
        assertTrue(exporter.exportToString().contains("flowchart LR"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidOrientationRejected() {
        new MermaidExporter(undirected).setOrientation("DIAGONAL");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullOrientationRejected() {
        new MermaidExporter(undirected).setOrientation(null);
    }

    @Test
    public void testEdgeLabelIsRendered() {
        MermaidExporter exporter = new MermaidExporter(undirected);
        String out = exporter.exportToString();
        assertTrue("Should contain piped edge label",
                out.contains("|best friends|"));
    }

    @Test
    public void testEdgeLabelsCanBeDisabled() {
        MermaidExporter exporter = new MermaidExporter(undirected);
        exporter.setShowEdgeLabels(false);
        String out = exporter.exportToString();
        assertFalse("Edge labels should be suppressed", out.contains("best friends"));
    }

    @Test
    public void testColorPaletteEmitsClassDefs() {
        MermaidExporter exporter = new MermaidExporter(undirected);
        String out = exporter.exportToString();
        assertTrue("classDef should be present", out.contains("classDef edge_f"));
        assertTrue("linkStyle should be emitted", out.contains("linkStyle 0"));
    }

    @Test
    public void testColorByEdgeTypeOffSuppressesStyles() {
        MermaidExporter exporter = new MermaidExporter(undirected);
        exporter.setColorByEdgeType(false);
        String out = exporter.exportToString();
        assertFalse(out.contains("classDef edge_"));
        assertFalse(out.contains("linkStyle"));
    }

    @Test
    public void testVertexLabelsEscapeSpecialCharacters() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A\"B");
        g.addVertex("Pipe|Node");
        g.addEdge(new Edge("f", "A\"B", "Pipe|Node"), "A\"B", "Pipe|Node");
        MermaidExporter exporter = new MermaidExporter(g);
        String out = exporter.exportToString();
        assertFalse("Raw quote must not appear in label", out.contains("\"A\"B\""));
        assertTrue("Quote should be escaped", out.contains("&quot;"));
        assertTrue("Pipe should be escaped", out.contains("&#124;"));
    }

    @Test
    public void testVertexIdSanitization() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("hello world");
        g.addVertex("a/b");
        g.addEdge(new Edge("f", "hello world", "a/b"), "hello world", "a/b");
        MermaidExporter exporter = new MermaidExporter(g);
        String out = exporter.exportToString();
        // Sanitized ids start with 'n' and contain underscores in place of spaces / slashes.
        assertTrue("Should contain sanitized id for 'hello world'",
                out.contains("nhello_world"));
        assertTrue("Should contain sanitized id for 'a/b'",
                out.contains("na_b"));
    }

    @Test
    public void testDeterministicOutput() {
        MermaidExporter a = new MermaidExporter(undirected);
        MermaidExporter b = new MermaidExporter(undirected);
        assertEquals("Same graph should yield byte-identical Mermaid",
                a.exportToString(), b.exportToString());
    }

    @Test
    public void testMarkdownBlockWrapper() {
        MermaidExporter exporter = new MermaidExporter(undirected);
        String md = exporter.exportToMarkdownBlock();
        assertTrue(md.startsWith("```mermaid\n"));
        assertTrue(md.endsWith("```\n"));
        assertTrue(md.contains("flowchart"));
    }

    @Test
    public void testTitleFrontmatter() {
        MermaidExporter exporter = new MermaidExporter(undirected);
        exporter.setTitle("Friendship Graph");
        String out = exporter.exportToString();
        assertTrue(out.startsWith("---\n"));
        assertTrue(out.contains("title: Friendship Graph"));
    }

    @Test
    public void testEmptyGraphProducesValidMermaid() {
        Graph<String, Edge> empty = new UndirectedSparseGraph<>();
        MermaidExporter exporter = new MermaidExporter(empty);
        String out = exporter.exportToString();
        assertTrue(out.contains("flowchart TD"));
        assertTrue(out.contains("Nodes: 0, Edges: 0"));
    }

    @Test
    public void testFileExport() throws Exception {
        File tmp = File.createTempFile("mermaid_export_", ".mmd");
        try {
            new MermaidExporter(undirected).export(tmp);
            String contents = new String(Files.readAllBytes(tmp.toPath()));
            assertTrue(contents.contains("flowchart TD"));
            assertTrue(contents.contains("nAlice"));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    @Test
    public void testSingleUndirectedEdgeProducesOneConnectorLine() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        g.addVertex("B");
        g.addEdge(new Edge("f", "A", "B"), "A", "B");
        String out = new MermaidExporter(g).exportToString();
        int count = 0;
        int idx = 0;
        while ((idx = out.indexOf(" --- ", idx)) != -1) { count++; idx += 5; }
        assertEquals(1, count);
    }

    @Test
    public void testCustomTypeColorOverridesPalette() {
        MermaidExporter exporter = new MermaidExporter(undirected);
        exporter.setTypeColor("f", "#000000");
        String out = exporter.exportToString();
        assertTrue("Custom color should appear in classDef",
                out.contains("classDef edge_f stroke:#000000"));
    }

    @Test
    public void testEscapeMermaidLabelHelper() {
        assertEquals("", MermaidExporter.escapeMermaidLabel(null));
        assertEquals("a&quot;b", MermaidExporter.escapeMermaidLabel("a\"b"));
        assertEquals("a&#124;b", MermaidExporter.escapeMermaidLabel("a|b"));
        assertEquals("&lt;tag&gt;", MermaidExporter.escapeMermaidLabel("<tag>"));
        assertEquals("a b c", MermaidExporter.escapeMermaidLabel("a\nb\tc"));
    }
}
