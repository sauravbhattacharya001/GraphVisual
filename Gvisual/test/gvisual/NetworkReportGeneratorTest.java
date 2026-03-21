package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for NetworkReportGenerator.
 */
public class NetworkReportGeneratorTest {

    private Graph<String, Edge> graph;
    private List<Edge> friendEdges;
    private List<Edge> fsEdges;
    private List<Edge> classmateEdges;
    private List<Edge> strangerEdges;
    private List<Edge> studyGEdges;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, Edge>();
        friendEdges = new ArrayList<Edge>();
        fsEdges = new ArrayList<Edge>();
        classmateEdges = new ArrayList<Edge>();
        strangerEdges = new ArrayList<Edge>();
        studyGEdges = new ArrayList<Edge>();
    }

    private edge addEdge(String v1, String v2, String type) {
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        edge e = new Edge(type, v1, v2);
        e.setWeight(1.0f);
        graph.addEdge(e, v1, v2);
        return e;
    }

    @Test
    public void testGenerateEmptyGraph() {
        NetworkReportGenerator gen = new NetworkReportGenerator(graph, friendEdges, fsEdges, classmateEdges, strangerEdges, studyGEdges);
        String html = gen.generate();
        assertNotNull(html);
        assertTrue(html.contains("Network Analysis Report"));
        assertTrue(html.contains("<!DOCTYPE html>"));
    }

    @Test
    public void testGenerateWithNodes() {
        friendEdges.add(addEdge("A", "B", "f"));
        friendEdges.add(addEdge("B", "C", "f"));
        classmateEdges.add(addEdge("A", "C", "c"));
        graph.addVertex("D"); // isolated

        NetworkReportGenerator gen = new NetworkReportGenerator(graph, friendEdges, fsEdges, classmateEdges, strangerEdges, studyGEdges);
        gen.setTitle("Test Network");
        String html = gen.generate();

        assertTrue(html.contains("Test Network"));
        assertTrue(html.contains("4")); // node count
        assertTrue(html.contains("3")); // edge count
        assertTrue(html.contains("Friend"));
        assertTrue(html.contains("Classmate"));
    }

    @Test
    public void testContainsSvgCharts() {
        friendEdges.add(addEdge("1", "2", "f"));
        friendEdges.add(addEdge("2", "3", "f"));
        friendEdges.add(addEdge("3", "4", "f"));

        NetworkReportGenerator gen = new NetworkReportGenerator(graph, friendEdges, fsEdges, classmateEdges, strangerEdges, studyGEdges);
        String html = gen.generate();

        assertTrue("Should contain SVG elements", html.contains("<svg"));
        assertTrue("Should contain degree distribution", html.contains("Degree Distribution"));
        assertTrue("Should contain edge type breakdown", html.contains("Edge Type Breakdown"));
        assertTrue("Should contain top nodes", html.contains("Top 10"));
    }

    @Test
    public void testHealthScore() {
        // Well-connected graph
        friendEdges.add(addEdge("A", "B", "f"));
        friendEdges.add(addEdge("B", "C", "f"));
        friendEdges.add(addEdge("A", "C", "f"));

        NetworkReportGenerator gen = new NetworkReportGenerator(graph, friendEdges, fsEdges, classmateEdges, strangerEdges, studyGEdges);
        String html = gen.generate();

        assertTrue("Should contain health score", html.contains("Network Health"));
        assertTrue("Should contain score value", html.contains("score-value"));
    }

    @Test
    public void testExportToFile() throws Exception {
        friendEdges.add(addEdge("X", "Y", "f"));
        NetworkReportGenerator gen = new NetworkReportGenerator(graph, friendEdges, fsEdges, classmateEdges, strangerEdges, studyGEdges);

        File tmpFile = File.createTempFile("report-test", ".html");
        tmpFile.deleteOnExit();
        gen.export(tmpFile);

        assertTrue("File should exist", tmpFile.exists());
        assertTrue("File should have content", tmpFile.length() > 100);
    }

    @Test
    public void testPrintStyles() {
        NetworkReportGenerator gen = new NetworkReportGenerator(graph, friendEdges, fsEdges, classmateEdges, strangerEdges, studyGEdges);
        String html = gen.generate();
        assertTrue("Should contain print media query", html.contains("@media print"));
    }

    @Test
    public void testResponsiveStyles() {
        NetworkReportGenerator gen = new NetworkReportGenerator(graph, friendEdges, fsEdges, classmateEdges, strangerEdges, studyGEdges);
        String html = gen.generate();
        assertTrue("Should contain responsive breakpoint", html.contains("max-width: 700px"));
    }

    @Test
    public void testDegreeFrequencyTable() {
        friendEdges.add(addEdge("A", "B", "f"));
        friendEdges.add(addEdge("B", "C", "f"));

        NetworkReportGenerator gen = new NetworkReportGenerator(graph, friendEdges, fsEdges, classmateEdges, strangerEdges, studyGEdges);
        String html = gen.generate();
        assertTrue("Should contain degree table", html.contains("Degree Frequency Table"));
        assertTrue("Should contain table element", html.contains("<table"));
    }

    @Test
    public void testNullEdgeLists() {
        // Should handle null edge lists gracefully
        NetworkReportGenerator gen = new NetworkReportGenerator(graph, null, null, null, null, null);
        String html = gen.generate();
        assertNotNull(html);
        assertTrue(html.contains("<!DOCTYPE html>"));
    }

    @Test
    public void testAllEdgeTypes() {
        friendEdges.add(addEdge("1", "2", "f"));
        fsEdges.add(addEdge("2", "3", "fs"));
        classmateEdges.add(addEdge("3", "4", "c"));
        strangerEdges.add(addEdge("4", "5", "s"));
        studyGEdges.add(addEdge("5", "1", "sg"));

        NetworkReportGenerator gen = new NetworkReportGenerator(graph, friendEdges, fsEdges, classmateEdges, strangerEdges, studyGEdges);
        String html = gen.generate();

        assertTrue(html.contains("Friend"));
        assertTrue(html.contains("Familiar Stranger"));
        assertTrue(html.contains("Classmate"));
        assertTrue(html.contains("Stranger"));
        assertTrue(html.contains("Study Group"));
    }

    @Test
    public void testMultipleComponents() {
        friendEdges.add(addEdge("A", "B", "f"));
        friendEdges.add(addEdge("C", "D", "f")); // separate component

        NetworkReportGenerator gen = new NetworkReportGenerator(graph, friendEdges, fsEdges, classmateEdges, strangerEdges, studyGEdges);
        String html = gen.generate();
        assertTrue("Should show 2 components", html.contains("2 Components") || html.contains(">2<"));
    }

    @Test
    public void testHtmlEscaping() {
        graph.addVertex("<script>alert('xss')</script>");
        NetworkReportGenerator gen = new NetworkReportGenerator(graph, friendEdges, fsEdges, classmateEdges, strangerEdges, studyGEdges);
        gen.setTitle("<b>Injected</b>");
        String html = gen.generate();
        assertFalse("Should escape HTML in title", html.contains("<b>Injected</b>"));
        assertTrue("Should have escaped title", html.contains("&lt;b&gt;Injected&lt;/b&gt;"));
    }
}
