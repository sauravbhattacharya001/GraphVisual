package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link GraphAsciiRenderer}.
 * Covers: empty graphs, single-node, multi-node rendering, Unicode mode,
 * degree display, adjacency list, degree histogram, highlight features,
 * configuration validation, and file export.
 */
public class GraphAsciiRendererTest {

    private Graph<String, edge> emptyGraph;
    private Graph<String, edge> singleNodeGraph;
    private Graph<String, edge> triangleGraph;
    private Graph<String, edge> starGraph;

    @Before
    public void setUp() {
        emptyGraph = new UndirectedSparseGraph<>();

        singleNodeGraph = new UndirectedSparseGraph<>();
        singleNodeGraph.addVertex("A");

        // Triangle: A-B-C-A
        triangleGraph = new UndirectedSparseGraph<>();
        triangleGraph.addVertex("A");
        triangleGraph.addVertex("B");
        triangleGraph.addVertex("C");
        edge e1 = new edge("f", "A", "B");
        e1.setWeight(1.0f);
        edge e2 = new edge("c", "B", "C");
        e2.setWeight(2.0f);
        edge e3 = new edge("f", "C", "A");
        e3.setWeight(1.5f);
        triangleGraph.addEdge(e1, "A", "B");
        triangleGraph.addEdge(e2, "B", "C");
        triangleGraph.addEdge(e3, "C", "A");

        // Star: center connected to 4 leaves
        starGraph = new UndirectedSparseGraph<>();
        starGraph.addVertex("Center");
        for (int i = 1; i <= 4; i++) {
            String leaf = "Leaf" + i;
            starGraph.addVertex(leaf);
            edge e = new edge("f", "Center", leaf);
            e.setWeight(1.0f);
            starGraph.addEdge(e, "Center", leaf);
        }
    }

    // --- Constructor validation ---

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsNullGraph() {
        new GraphAsciiRenderer(null);
    }

    // --- Empty graph ---

    @Test
    public void renderEmptyGraphReturnsPlaceholder() {
        GraphAsciiRenderer renderer = new GraphAsciiRenderer(emptyGraph);
        assertEquals("(empty graph)", renderer.render());
    }

    @Test
    public void renderEmptyGraphHistogramReturnsPlaceholder() {
        GraphAsciiRenderer renderer = new GraphAsciiRenderer(emptyGraph);
        assertEquals("(empty graph)", renderer.renderDegreeHistogram());
    }

    // --- Single node ---

    @Test
    public void renderSingleNodeContainsLabel() {
        GraphAsciiRenderer renderer = new GraphAsciiRenderer(singleNodeGraph);
        renderer.setWidth(40);
        renderer.setHeight(15);
        String output = renderer.render();
        assertTrue("Should contain vertex label 'A'", output.contains("A"));
        assertTrue("Should contain node count", output.contains("1 nodes"));
        assertTrue("Should contain edge count", output.contains("0 edges"));
    }

    // --- Triangle graph basic render ---

    @Test
    public void renderTriangleContainsAllVertices() {
        GraphAsciiRenderer renderer = new GraphAsciiRenderer(triangleGraph);
        renderer.setWidth(60);
        renderer.setHeight(20);
        String output = renderer.render();
        assertTrue(output.contains("A"));
        assertTrue(output.contains("B"));
        assertTrue(output.contains("C"));
        assertTrue(output.contains("3 nodes"));
        assertTrue(output.contains("3 edges"));
    }

    // --- Unicode mode ---

    @Test
    public void unicodeModeUsesUnicodeBorders() {
        GraphAsciiRenderer renderer = new GraphAsciiRenderer(triangleGraph);
        renderer.setWidth(60);
        renderer.setHeight(20);
        renderer.setUnicode(true);
        String output = renderer.render();
        assertTrue("Unicode mode should use ═ borders", output.contains("═"));
        assertTrue("Unicode mode should use ║ borders", output.contains("║"));
    }

    @Test
    public void asciiModeUsesAsciiBorders() {
        GraphAsciiRenderer renderer = new GraphAsciiRenderer(triangleGraph);
        renderer.setWidth(60);
        renderer.setHeight(20);
        renderer.setUnicode(false);
        String output = renderer.render();
        assertTrue("ASCII mode should use = borders", output.contains("="));
        assertTrue("ASCII mode should use | borders", output.contains("|"));
    }

    // --- Show degree ---

    @Test
    public void showDegreeIncludesDegreeAnnotations() {
        GraphAsciiRenderer renderer = new GraphAsciiRenderer(triangleGraph);
        renderer.setWidth(80);
        renderer.setHeight(25);
        renderer.setShowDegree(true);
        String output = renderer.render();
        // Each node in triangle has degree 2
        assertTrue("Should show degree annotation (2)", output.contains("(2)"));
    }

    // --- Legend ---

    @Test
    public void legendShowsDegreeStats() {
        GraphAsciiRenderer renderer = new GraphAsciiRenderer(starGraph);
        renderer.setShowLegend(true);
        String output = renderer.render();
        assertTrue("Legend should show min degree", output.contains("min="));
        assertTrue("Legend should show max degree", output.contains("max="));
        assertTrue("Legend should show avg degree", output.contains("avg="));
    }

    @Test
    public void legendCanBeDisabled() {
        GraphAsciiRenderer renderer = new GraphAsciiRenderer(triangleGraph);
        renderer.setShowLegend(false);
        String output = renderer.render();
        assertFalse("No legend expected", output.contains("Graph:"));
        assertFalse("No degree stats expected", output.contains("Degree:"));
    }

    // --- Highlight ---

    @Test
    public void highlightNodeByName() {
        GraphAsciiRenderer renderer = new GraphAsciiRenderer(triangleGraph);
        renderer.setWidth(60);
        renderer.setHeight(20);
        renderer.addHighlightNode("A");
        String output = renderer.render();
        // With highlight nodes, legend should mention highlighted
        assertTrue(output.contains("highlighted"));
    }

    @Test
    public void highlightByDegreeThreshold() {
        GraphAsciiRenderer renderer = new GraphAsciiRenderer(starGraph);
        renderer.setWidth(80);
        renderer.setHeight(25);
        renderer.setHighlightDegreeThreshold(3);
        String output = renderer.render();
        // Center has degree 4, should be highlighted
        assertTrue(output.contains("highlighted"));
    }

    // --- Configuration validation ---

    @Test(expected = IllegalArgumentException.class)
    public void setWidthRejectsTooSmall() {
        GraphAsciiRenderer renderer = new GraphAsciiRenderer(triangleGraph);
        renderer.setWidth(19);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setHeightRejectsTooSmall() {
        GraphAsciiRenderer renderer = new GraphAsciiRenderer(triangleGraph);
        renderer.setHeight(9);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setLayoutIterationsRejectsZero() {
        GraphAsciiRenderer renderer = new GraphAsciiRenderer(triangleGraph);
        renderer.setLayoutIterations(0);
    }

    @Test
    public void setWidthAcceptsBoundary() {
        GraphAsciiRenderer renderer = new GraphAsciiRenderer(triangleGraph);
        renderer.setWidth(20);
        renderer.setHeight(10);
        // Should not throw, and render should work
        String output = renderer.render();
        assertNotNull(output);
    }

    // --- Adjacency list ---

    @Test
    public void adjacencyListContainsAllVertices() {
        GraphAsciiRenderer renderer = new GraphAsciiRenderer(triangleGraph);
        String output = renderer.renderAdjacencyList();
        assertTrue(output.contains("A"));
        assertTrue(output.contains("B"));
        assertTrue(output.contains("C"));
        assertTrue(output.contains("3 nodes"));
        assertTrue(output.contains("3 edges"));
    }

    @Test
    public void adjacencyListShowsDegreeWhenEnabled() {
        GraphAsciiRenderer renderer = new GraphAsciiRenderer(triangleGraph);
        renderer.setShowDegree(true);
        String output = renderer.renderAdjacencyList();
        assertTrue("Should show degree [2]", output.contains("[2]"));
    }

    @Test
    public void adjacencyListUnicodeMode() {
        GraphAsciiRenderer renderer = new GraphAsciiRenderer(triangleGraph);
        renderer.setUnicode(true);
        String output = renderer.renderAdjacencyList();
        assertTrue("Unicode mode uses ●", output.contains("●"));
        assertTrue("Unicode mode uses ├──", output.contains("├──"));
    }

    // --- Degree histogram ---

    @Test
    public void degreeHistogramTriangle() {
        GraphAsciiRenderer renderer = new GraphAsciiRenderer(triangleGraph);
        String output = renderer.renderDegreeHistogram();
        assertTrue(output.contains("Degree Distribution"));
        // All 3 nodes have degree 2, so should show degree 2 with count 3
        assertTrue(output.contains("3"));
    }

    @Test
    public void degreeHistogramStarShowsMultipleDegrees() {
        GraphAsciiRenderer renderer = new GraphAsciiRenderer(starGraph);
        String output = renderer.renderDegreeHistogram();
        // Center has degree 4, leaves have degree 1
        assertTrue("Should show degree 1 count", output.contains("4"));  // 4 leaves
        assertTrue("Should show degree 4 count", output.contains("1"));  // 1 center
    }

    @Test
    public void degreeHistogramUnicodeUsesBlockChars() {
        GraphAsciiRenderer renderer = new GraphAsciiRenderer(triangleGraph);
        renderer.setUnicode(true);
        String output = renderer.renderDegreeHistogram();
        assertTrue("Unicode histogram uses █", output.contains("█"));
        assertTrue("Unicode histogram uses ═", output.contains("═"));
    }

    // --- File export ---

    @Test
    public void exportToFileCreatesReadableFile() throws IOException {
        GraphAsciiRenderer renderer = new GraphAsciiRenderer(triangleGraph);
        renderer.setWidth(60);
        renderer.setHeight(20);
        File tempFile = File.createTempFile("graph-ascii-test", ".txt");
        tempFile.deleteOnExit();
        try {
            renderer.exportToFile(tempFile.getAbsolutePath());
            String content = new String(Files.readAllBytes(tempFile.toPath()), "UTF-8");
            assertTrue(content.contains("A"));
            assertTrue(content.contains("3 nodes"));
            assertTrue(content.length() > 100);
        } finally {
            tempFile.delete();
        }
    }

    // --- Edge type distribution in legend ---

    @Test
    public void legendShowsEdgeTypeDistribution() {
        GraphAsciiRenderer renderer = new GraphAsciiRenderer(triangleGraph);
        renderer.setShowLegend(true);
        String output = renderer.render();
        // Triangle has f and c edge types
        assertTrue("Legend should show edge types", output.contains("Edge types:"));
        assertTrue("Should show 'f' type", output.contains("f="));
        assertTrue("Should show 'c' type", output.contains("c="));
    }

    // --- Layout iterations ---

    @Test
    public void minimalLayoutIterationsStillRenders() {
        GraphAsciiRenderer renderer = new GraphAsciiRenderer(triangleGraph);
        renderer.setLayoutIterations(1);
        renderer.setWidth(40);
        renderer.setHeight(15);
        String output = renderer.render();
        assertNotNull(output);
        assertTrue(output.contains("A"));
    }

    // --- Large graph doesn't crash ---

    @Test
    public void largeGraphRendersWithoutError() {
        Graph<String, edge> large = new UndirectedSparseGraph<>();
        for (int i = 0; i < 50; i++) {
            large.addVertex("N" + i);
        }
        for (int i = 0; i < 49; i++) {
            edge e = new edge("f", "N" + i, "N" + (i + 1));
            e.setWeight(1.0f);
            large.addEdge(e, "N" + i, "N" + (i + 1));
        }
        GraphAsciiRenderer renderer = new GraphAsciiRenderer(large);
        renderer.setLayoutIterations(10); // fast
        String output = renderer.render();
        assertNotNull(output);
        assertTrue(output.contains("50 nodes"));
        assertTrue(output.contains("49 edges"));
    }
}
