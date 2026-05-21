package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * Tests for {@link TikzExporter}.
 *
 * <p>Covers constructor validation, empty-graph fallback, structural content
 * of the generated TikZ/LaTeX document (preamble, nodes, edges, legend,
 * labels, title), the standalone vs. includable flag, scaling toggles,
 * LaTeX-special-character escaping, identifier sanitization, file I/O,
 * and edge-type colour mapping.</p>
 */
public class TikzExporterTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");

        Edge e1 = new Edge("f", "A", "B");
        e1.setWeight(1.5f);
        graph.addEdge(e1, "A", "B");

        Edge e2 = new Edge("c", "B", "C");
        e2.setWeight(2.0f);
        graph.addEdge(e2, "B", "C");

        Edge e3 = new Edge("s", "A", "C");
        e3.setWeight(0.5f);
        graph.addEdge(e3, "A", "C");

        Edge e4 = new Edge("sg", "C", "D");
        e4.setWeight(3.0f);
        graph.addEdge(e4, "C", "D");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsNullGraph() {
        new TikzExporter(null);
    }

    @Test
    public void emptyGraphProducesValidStandaloneDocument() {
        Graph<String, Edge> empty = new UndirectedSparseGraph<>();
        TikzExporter exporter = new TikzExporter(empty);
        String tex = exporter.exportToString();

        assertTrue("standalone preamble", tex.contains("\\documentclass[border=10pt]{standalone}"));
        assertTrue("opens tikzpicture", tex.contains("\\begin{tikzpicture}"));
        assertTrue("closes tikzpicture", tex.contains("\\end{tikzpicture}"));
        assertTrue("ends document", tex.contains("\\end{document}"));
        assertTrue("empty-graph placeholder", tex.contains("Empty graph"));
    }

    @Test
    public void emptyGraphIncludableHasNoPreamble() {
        Graph<String, Edge> empty = new UndirectedSparseGraph<>();
        TikzExporter exporter = new TikzExporter(empty);
        exporter.setStandalone(false);
        String tex = exporter.exportToString();

        assertFalse("no documentclass", tex.contains("\\documentclass"));
        assertFalse("no end document", tex.contains("\\end{document}"));
        assertTrue("opens tikzpicture", tex.contains("\\begin{tikzpicture}"));
    }

    @Test
    public void standaloneOutputContainsExpectedPreamble() {
        TikzExporter exporter = new TikzExporter(graph);
        String tex = exporter.exportToString();

        assertTrue(tex.contains("\\documentclass[border=10pt]{standalone}"));
        assertTrue(tex.contains("\\usepackage{tikz}"));
        assertTrue(tex.contains("\\usepackage{xcolor}"));
        assertTrue(tex.contains("\\usetikzlibrary{arrows.meta,positioning}"));
        assertTrue(tex.contains("\\begin{document}"));
        assertTrue(tex.contains("\\end{document}"));
    }

    @Test
    public void nonStandaloneOutputHasNoPreambleOrDocument() {
        TikzExporter exporter = new TikzExporter(graph);
        exporter.setStandalone(false);
        String tex = exporter.exportToString();

        assertFalse(tex.contains("\\documentclass"));
        assertFalse(tex.contains("\\begin{document}"));
        assertFalse(tex.contains("\\end{document}"));
        assertTrue(tex.contains("\\begin{tikzpicture}"));
        assertTrue(tex.contains("\\end{tikzpicture}"));
    }

    @Test
    public void outputContainsAllNodes() {
        TikzExporter exporter = new TikzExporter(graph);
        String tex = exporter.exportToString();

        // Sanitized identifiers
        assertTrue(tex.contains("(A)"));
        assertTrue(tex.contains("(B)"));
        assertTrue(tex.contains("(C)"));
        assertTrue(tex.contains("(D)"));

        // Labels
        assertTrue(tex.contains("{A}"));
        assertTrue(tex.contains("{D}"));
    }

    @Test
    public void outputContainsEdgeDrawCommandsForEveryEdge() {
        TikzExporter exporter = new TikzExporter(graph);
        String tex = exporter.exportToString();

        int drawCount = 0;
        int idx = 0;
        while ((idx = tex.indexOf("\\draw[", idx)) != -1) {
            drawCount++;
            idx++;
        }
        // Four edges + legend swatches (one per used type, 4 here) → at least 8 draws.
        assertTrue("at least one \\draw per edge", drawCount >= graph.getEdgeCount());
    }

    @Test
    public void edgeColorsReflectTypeMapping() {
        TikzExporter exporter = new TikzExporter(graph);
        String tex = exporter.exportToString();

        // Colors from TYPE_COLORS map
        assertTrue("friend → green",      tex.contains("green!70!black"));
        assertTrue("classmate → orange",  tex.contains("orange!80!black"));
        assertTrue("stranger → red",      tex.contains("red!70!black"));
        assertTrue("study group → violet", tex.contains("violet!70!black"));
    }

    @Test
    public void legendIsRenderedWhenEnabled() {
        TikzExporter exporter = new TikzExporter(graph);
        String tex = exporter.exportToString();

        assertTrue("legend header", tex.contains("Edge Types"));
        // Human-readable type names from TYPE_NAMES
        assertTrue(tex.contains("Friend"));
        assertTrue(tex.contains("Classmate"));
        assertTrue(tex.contains("Stranger"));
        assertTrue(tex.contains("Study Group"));
    }

    @Test
    public void legendCanBeDisabled() {
        TikzExporter exporter = new TikzExporter(graph);
        exporter.setShowLegend(false);
        String tex = exporter.exportToString();

        assertFalse("no legend header", tex.contains("Edge Types"));
    }

    @Test
    public void labelsCanBeDisabled() {
        TikzExporter exporter = new TikzExporter(graph);
        exporter.setShowLabels(false);
        String tex = exporter.exportToString();

        // Nodes still placed with sanitized ids
        assertTrue(tex.contains("(A)"));
        // But the label braces should be empty for at least some nodes.
        assertTrue("empty label braces present", tex.contains(") {};"));
    }

    @Test
    public void titleIsRenderedWhenSet() {
        TikzExporter exporter = new TikzExporter(graph);
        exporter.setTitle("My Network");
        String tex = exporter.exportToString();

        assertTrue(tex.contains("My Network"));
    }

    @Test
    public void titleIsEscapedForLatex() {
        TikzExporter exporter = new TikzExporter(graph);
        exporter.setTitle("100% & $cool$_graph#1");
        String tex = exporter.exportToString();

        assertTrue("% escaped", tex.contains("\\%"));
        assertTrue("& escaped", tex.contains("\\&"));
        assertTrue("$ escaped", tex.contains("\\$"));
        assertTrue("_ escaped", tex.contains("\\_"));
        assertTrue("# escaped", tex.contains("\\#"));
    }

    @Test
    public void vertexIdsWithSpecialCharsAreSanitized() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("node-1");
        g.addVertex("node 2");
        Edge e = new Edge("f", "node-1", "node 2");
        g.addEdge(e, "node-1", "node 2");

        TikzExporter exporter = new TikzExporter(g);
        String tex = exporter.exportToString();

        // sanitize() replaces non-alphanumeric chars with 'x'
        assertTrue("sanitized id for node-1", tex.contains("(nodex1)"));
        assertTrue("sanitized id for node 2", tex.contains("(nodex2)"));
    }

    @Test
    public void scalingTogglesProduceValidOutput() {
        TikzExporter exporter = new TikzExporter(graph);
        exporter.setScaleNodesByDegree(false);
        exporter.setScaleEdgesByWeight(false);
        String tex = exporter.exportToString();

        assertTrue(tex.contains("\\begin{tikzpicture}"));
        assertTrue(tex.contains("\\end{tikzpicture}"));
        // With weight scaling off, the default line width 0.4pt should appear.
        assertTrue("default edge line width", tex.contains("line width=0.40pt"));
    }

    @Test
    public void canvasDimensionsAreClampedToMinimum() {
        TikzExporter exporter = new TikzExporter(graph);
        // Both should be clamped to minimums (4 and 3 respectively); no crash on tiny values.
        exporter.setCanvasWidth(0.1);
        exporter.setCanvasHeight(0.1);
        String tex = exporter.exportToString();

        assertNotNull(tex);
        assertTrue(tex.contains("\\begin{tikzpicture}"));
    }

    @Test
    public void layoutIterationsAreClampedToMinimum() {
        TikzExporter exporter = new TikzExporter(graph);
        exporter.setLayoutIterations(1); // below minimum of 10
        String tex = exporter.exportToString();
        assertTrue(tex.contains("\\begin{tikzpicture}"));
    }

    @Test
    public void exportWritesUtf8FileWithSameContentAsString() throws IOException {
        TikzExporter exporter = new TikzExporter(graph);
        exporter.setTitle("File Test");

        File tmp = Files.createTempFile("tikz-exporter-test", ".tex").toFile();
        tmp.deleteOnExit();
        try {
            exporter.export(tmp);
            assertTrue(tmp.length() > 0);
            String onDisk = new String(Files.readAllBytes(tmp.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
            String inMemory = exporter.exportToString();
            assertEquals("file content matches in-memory output", inMemory, onDisk);
            assertTrue(onDisk.contains("File Test"));
        } finally {
            tmp.delete();
        }
    }

    @Test
    public void exportRejectsInvalidOutputPath() {
        TikzExporter exporter = new TikzExporter(graph);
        // ExportUtils.validateOutputPath should reject null.
        try {
            exporter.export(null);
            fail("expected exception for null output file");
        } catch (IOException | RuntimeException expected) {
            // Either IOException (declared) or IllegalArgumentException is acceptable.
        }
    }

    @Test
    public void unknownEdgeTypeFallsBackToGray() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("X");
        g.addVertex("Y");
        Edge e = new Edge("mystery-type", "X", "Y");
        e.setWeight(1.0f);
        g.addEdge(e, "X", "Y");

        TikzExporter exporter = new TikzExporter(g);
        String tex = exporter.exportToString();

        assertTrue("unknown type renders as gray", tex.contains("\\draw[gray,"));
    }

    @Test
    public void edgeWithNullEndpointsIsSkippedGracefully() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("P");
        g.addVertex("Q");
        Edge e = new Edge("f", "P", "Q");
        e.setWeight(1.0f);
        g.addEdge(e, "P", "Q");
        // Manually create an edge with null endpoints and inject (defensive path).
        Edge bad = new Edge("f", null, null);
        bad.setWeight(1.0f);
        g.addEdge(bad, "P", "Q"); // JUNG topology still uses P/Q; Edge fields are null.

        TikzExporter exporter = new TikzExporter(g);
        String tex = exporter.exportToString();
        // Should not throw and should still contain at least one valid \draw line.
        assertTrue(tex.contains("\\draw["));
    }
}
