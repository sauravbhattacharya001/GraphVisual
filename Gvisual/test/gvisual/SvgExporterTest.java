package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link SvgExporter}.
 */
public class SvgExporterTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");

        Edge e1  new Edge("f", "A", "B");
        e1.setWeight(1.5f);
        graph.addEdge(e1, "A", "B");

        Edge e2  new Edge("c", "B", "C");
        e2.setWeight(2.0f);
        graph.addEdge(e2, "B", "C");

        Edge e3  new Edge("s", "A", "C");
        e3.setWeight(0.5f);
        graph.addEdge(e3, "A", "C");

        Edge e4  new Edge("sg", "C", "D");
        e4.setWeight(3.0f);
        graph.addEdge(e4, "C", "D");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsNull() {
        new SvgExporter(null);
    }

    @Test
    public void exportToStringProducesValidSvg() {
        SvgExporter exporter = new SvgExporter(graph);
        String svg = exporter.exportToString();
        assertTrue(svg.startsWith("<?xml"));
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("</svg>"));
    }

    @Test
    public void svgContainsAllNodes() {
        SvgExporter exporter = new SvgExporter(graph);
        String svg = exporter.exportToString();
        assertTrue(svg.contains(">A<"));
        assertTrue(svg.contains(">B<"));
        assertTrue(svg.contains(">C<"));
        assertTrue(svg.contains(">D<"));
    }

    @Test
    public void svgContainsEdgeLines() {
        SvgExporter exporter = new SvgExporter(graph);
        String svg = exporter.exportToString();
        // Should contain line elements for edges
        assertTrue(svg.contains("<line"));
        // Should reference edge types in tooltips
        assertTrue(svg.contains("Friend"));
        assertTrue(svg.contains("Classmate"));
        assertTrue(svg.contains("Stranger"));
        assertTrue(svg.contains("Study Group"));
    }

    @Test
    public void svgContainsNodeCount() {
        SvgExporter exporter = new SvgExporter(graph);
        String svg = exporter.exportToString();
        assertTrue(svg.contains("4 nodes"));
        assertTrue(svg.contains("4 edges"));
    }

    @Test
    public void svgIncludesLegend() {
        SvgExporter exporter = new SvgExporter(graph);
        String svg = exporter.exportToString();
        assertTrue(svg.contains("Edge Types"));
        assertTrue(svg.contains("id=\"legend\""));
    }

    @Test
    public void legendCanBeDisabled() {
        SvgExporter exporter = new SvgExporter(graph);
        exporter.setShowLegend(false);
        String svg = exporter.exportToString();
        assertFalse(svg.contains("id=\"legend\""));
    }

    @Test
    public void labelsCanBeDisabled() {
        SvgExporter exporter = new SvgExporter(graph);
        exporter.setShowLabels(false);
        String svg = exporter.exportToString();
        assertFalse(svg.contains("class=\"node-label\""));
    }

    @Test
    public void titleAppearsInOutput() {
        SvgExporter exporter = new SvgExporter(graph);
        exporter.setTitle("Test Network");
        String svg = exporter.exportToString();
        assertTrue(svg.contains("Test Network"));
        assertTrue(svg.contains("class=\"title-text\""));
    }

    @Test
    public void descriptionAppearsAsDesc() {
        SvgExporter exporter = new SvgExporter(graph);
        exporter.setDescription("A test graph");
        String svg = exporter.exportToString();
        assertTrue(svg.contains("<desc>A test graph</desc>"));
    }

    @Test
    public void lightThemeUsesDifferentBackground() {
        SvgExporter exporter = new SvgExporter(graph);
        exporter.setDarkTheme(false);
        String svg = exporter.exportToString();
        assertTrue(svg.contains("#ffffff"));
        assertFalse(svg.contains("#1a1a2e"));
    }

    @Test
    public void darkThemeIsDefault() {
        SvgExporter exporter = new SvgExporter(graph);
        String svg = exporter.exportToString();
        assertTrue(svg.contains("#1a1a2e"));
    }

    @Test
    public void emptyGraphProducesSvg() {
        Graph<String, Edge> empty = new UndirectedSparseGraph<>();
        SvgExporter exporter = new SvgExporter(empty);
        String svg = exporter.exportToString();
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("Empty graph"));
    }

    @Test
    public void singleNodeGraphWorks() {
        Graph<String, Edge> single = new UndirectedSparseGraph<>();
        single.addVertex("X");
        SvgExporter exporter = new SvgExporter(single);
        String svg = exporter.exportToString();
        assertTrue(svg.contains(">X<"));
        assertTrue(svg.contains("1 nodes"));
    }

    @Test
    public void customCanvasSize() {
        SvgExporter exporter = new SvgExporter(graph);
        exporter.setWidth(1200);
        exporter.setHeight(900);
        String svg = exporter.exportToString();
        assertTrue(svg.contains("width=\"1200\""));
        assertTrue(svg.contains("height=\"900\""));
    }

    @Test
    public void customTypeColor() {
        SvgExporter exporter = new SvgExporter(graph);
        exporter.setTypeColor("f", "#00FF00");
        String svg = exporter.exportToString();
        assertTrue(svg.contains("#00FF00"));
    }

    @Test
    public void xmlSpecialCharsEscaped() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("<script>");
        g.addVertex("B&C");
        Edge e  new Edge("f", "<script>", "B&C");
        e.setWeight(1.0f);
        g.addEdge(e, "<script>", "B&C");

        SvgExporter exporter = new SvgExporter(g);
        String svg = exporter.exportToString();
        // Should not contain unescaped special chars
        assertFalse(svg.contains("<script>") && !svg.contains("&lt;script&gt;"));
        assertTrue(svg.contains("&amp;"));
    }

    @Test
    public void viewBoxMatchesDimensions() {
        SvgExporter exporter = new SvgExporter(graph);
        exporter.setWidth(1000);
        exporter.setHeight(800);
        String svg = exporter.exportToString();
        assertTrue(svg.contains("viewBox=\"0 0 1000 800\""));
    }
}
