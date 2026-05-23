package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * Tests for {@link CentralityRadarExporter}.
 *
 * <p>Verifies construction validation, the generated HTML structure, custom
 * titles, JSON node payload integrity, XSS escaping in titles, and that
 * the file export path writes a byte-equal copy of {@link #exportToString()}.</p>
 *
 * @author sauravbhattacharya001
 */
public class CentralityRadarExporterTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
        // Small graph: A-B-C-D with an A-C chord. 4 vertices, 4 edges.
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("A", "C");
    }

    private void addEdge(String v1, String v2) {
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        Edge e = new Edge("f", v1, v2);
        e.setWeight(1.0f);
        graph.addEdge(e, v1, v2);
    }

    // ---------- constructor ----------

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsNullGraph() {
        new CentralityRadarExporter(null);
    }

    @Test
    public void constructorAcceptsEmptyGraph() {
        Graph<String, Edge> empty = new UndirectedSparseGraph<>();
        CentralityRadarExporter exporter = new CentralityRadarExporter(empty);
        String html = exporter.exportToString();
        assertNotNull(html);
        // Empty graph -> "All (0)" in the topN select
        assertTrue(html.contains("All (0)"));
        // Empty DATA array
        assertTrue(html.contains("const DATA=[]"));
    }

    // ---------- HTML structure ----------

    @Test
    public void htmlContainsRequiredStructure() {
        CentralityRadarExporter exporter = new CentralityRadarExporter(graph);
        String html = exporter.exportToString();

        assertTrue("must start with DOCTYPE", html.startsWith("<!DOCTYPE html>"));
        assertTrue(html.contains("<html lang=\"en\">"));
        assertTrue(html.contains("<meta charset=\"UTF-8\">"));
        assertTrue(html.contains("<meta name=\"viewport\""));
        assertTrue(html.contains("<canvas id=\"radar\""));
        assertTrue(html.contains("id=\"rankTable\""));
        assertTrue(html.contains("id=\"search\""));
        assertTrue(html.contains("id=\"topN\""));
        assertTrue(html.contains("id=\"sortBy\""));
        assertTrue(html.contains("id=\"themeBtn\""));
        assertTrue(html.contains("id=\"stats\""));
        assertTrue("must end with closing html", html.trim().endsWith("</html>"));
    }

    @Test
    public void htmlContainsAllNodeIdsInData() {
        CentralityRadarExporter exporter = new CentralityRadarExporter(graph);
        String html = exporter.exportToString();
        // Each node id should appear inside the DATA JSON literal.
        // The exporter emits {"id":"A",...}
        for (String id : new String[]{"A", "B", "C", "D"}) {
            assertTrue("DATA payload missing node " + id,
                    html.contains("\"id\":\"" + id + "\""));
        }
        // Total node count is rendered in the "All (N)" option
        assertTrue(html.contains("All (4)"));
    }

    // ---------- title customization ----------

    @Test
    public void defaultTitleIsUsedWhenNotSet() {
        CentralityRadarExporter exporter = new CentralityRadarExporter(graph);
        String html = exporter.exportToString();
        assertTrue(html.contains("<title>Centrality Radar Chart</title>"));
    }

    @Test
    public void customTitleIsHonoured() {
        CentralityRadarExporter exporter = new CentralityRadarExporter(graph);
        exporter.setTitle("My Network");
        String html = exporter.exportToString();
        assertTrue(html.contains("<title>My Network</title>"));
    }

    @Test
    public void titleIsXmlEscapedToPreventInjection() {
        CentralityRadarExporter exporter = new CentralityRadarExporter(graph);
        exporter.setTitle("<script>alert(1)</script>");
        String html = exporter.exportToString();
        // The literal <script> tag must not appear unescaped in the title slot.
        assertFalse("raw <script> must not be injected via title",
                html.contains("<title><script>alert(1)</script></title>"));
        // It should appear in escaped form (&lt; / &gt; or similar).
        assertTrue(html.contains("&lt;script&gt;") || html.contains("&lt;script"));
    }

    // ---------- file export ----------

    @Test
    public void exportWritesFileMatchingExportToString() throws Exception {
        CentralityRadarExporter exporter = new CentralityRadarExporter(graph);
        exporter.setTitle("File Export Test");
        String expected = exporter.exportToString();

        File tmp = File.createTempFile("radar-", ".html");
        try {
            exporter.export(tmp);
            String onDisk = Files.readString(tmp.toPath());
            assertEquals("file contents must match exportToString output", expected, onDisk);
            assertTrue("file should be non-trivial in size", tmp.length() > 1000);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    @Test(expected = NullPointerException.class)
    public void exportRejectsNullFile() throws Exception {
        new CentralityRadarExporter(graph).export(null);
    }
}
