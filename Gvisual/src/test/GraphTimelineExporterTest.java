package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphTimelineExporter}.
 */
public class GraphTimelineExporterTest {

    private TemporalGraph temporalGraph;

    @Before
    public void setUp() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        g.addVertex("B");
        g.addVertex("C");

        Edge e1 = new Edge("f", "A", "B");
        e1.setTimestamp(1000L);
        g.addEdge(e1, "A", "B");

        Edge e2 = new Edge("c", "B", "C");
        e2.setTimestamp(2000L);
        g.addEdge(e2, "B", "C");

        Edge e3 = new Edge("s", "A", "C");
        e3.setTimestamp(3000L);
        g.addEdge(e3, "A", "C");

        temporalGraph = new TemporalGraph(g);
    }

    @Test
    public void testExportToString() {
        GraphTimelineExporter exporter = new GraphTimelineExporter(temporalGraph);
        exporter.setTitle("Test Timeline");
        String html = exporter.exportToString();

        assertNotNull(html);
        assertTrue("Should contain HTML doctype", html.contains("<!DOCTYPE html>"));
        assertTrue("Should contain title", html.contains("Test Timeline"));
        assertTrue("Should contain SNAPSHOTS data", html.contains("SNAPSHOTS"));
        assertTrue("Should contain CUMULATIVE data", html.contains("CUMULATIVE"));
        assertTrue("Should contain play button", html.contains("btnPlay"));
        assertTrue("Should contain scrubber", html.contains("scrubber"));
        assertTrue("Should contain node A", html.contains("\"A\""));
        assertTrue("Should contain node B", html.contains("\"B\""));
    }

    @Test
    public void testExportToFile() throws Exception {
        GraphTimelineExporter exporter = new GraphTimelineExporter(temporalGraph);
        File tmp = File.createTempFile("timeline-test", ".html");
        tmp.deleteOnExit();
        exporter.export(tmp);
        assertTrue("File should exist", tmp.exists());
        assertTrue("File should not be empty", tmp.length() > 1000);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraph() {
        new GraphTimelineExporter(null);
    }

    @Test
    public void testDefaultTitle() {
        GraphTimelineExporter exporter = new GraphTimelineExporter(temporalGraph);
        String html = exporter.exportToString();
        assertTrue("Should have default title", html.contains("Graph Timeline"));
    }

    @Test
    public void testSetNullTitle() {
        GraphTimelineExporter exporter = new GraphTimelineExporter(temporalGraph);
        exporter.setTitle(null);
        String html = exporter.exportToString();
        assertTrue("Null title should fallback", html.contains("Graph Timeline"));
    }
}
