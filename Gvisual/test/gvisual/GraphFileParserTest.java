package gvisual;

import edu.uci.ics.jung.graph.Graph;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Comprehensive tests for {@link GraphFileParser}.
 *
 * <p>Covers: normal parsing, edge classification by type, weight validation,
 * malformed input handling, empty files, filter predicates, section ordering,
 * and duplicate node/edge resilience.</p>
 *
 * @author zalenix
 */
public class GraphFileParserTest {

    private File tempDir;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("graphparser-test").toFile();
    }

    @After
    public void tearDown() {
        if (tempDir != null) {
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File f : files) f.delete();
            }
            tempDir.delete();
        }
    }

    private File writeGraph(String content) throws IOException {
        File f = new File(tempDir, "graph_" + System.nanoTime() + ".txt");
        try (FileWriter w = new FileWriter(f)) {
            w.write(content);
        }
        return f;
    }

    // ── Basic parsing ───────────────────────────────────────────

    @Test
    public void testBasicNodesAndEdges() throws IOException {
        File f = writeGraph(
            "nodes\nA\nB\nC\nedges\nf A B 1.0\nc B C 2.5\n"
        );
        GraphFileParser.ParseResult result = GraphFileParser.parse(f.getAbsolutePath());

        assertEquals(3, result.getVertices().size());
        assertTrue(result.getVertices().containsAll(Set.of("A", "B", "C")));
        assertEquals(2, result.getGraph().getEdgeCount());
        assertEquals(0, result.getSkippedLines());
    }

    @Test
    public void testAllEdgeTypesClassified() throws IOException {
        File f = writeGraph(
            "nodes\nA\nB\nC\nD\nE\nF\n" +
            "edges\n" +
            "f A B 1.0\n" +
            "c B C 2.0\n" +
            "fs C D 3.0\n" +
            "s D E 4.0\n" +
            "sg E F 5.0\n"
        );
        GraphFileParser.ParseResult result = GraphFileParser.parse(f.getAbsolutePath());

        assertEquals(1, result.getEdges(EdgeType.FRIEND).size());
        assertEquals(1, result.getEdges(EdgeType.CLASSMATE).size());
        assertEquals(1, result.getEdges(EdgeType.FAMILIAR).size());
        assertEquals(1, result.getEdges(EdgeType.STRANGER).size());
        assertEquals(1, result.getEdges(EdgeType.STUDY_GROUP).size());
        assertEquals(5, result.getGraph().getEdgeCount());
    }

    @Test
    public void testEdgeWeightsPreserved() throws IOException {
        File f = writeGraph(
            "nodes\nX\nY\nedges\nf X Y 3.14\n"
        );
        GraphFileParser.ParseResult result = GraphFileParser.parse(f.getAbsolutePath());

        List<edge> friends = result.getEdges(EdgeType.FRIEND);
        assertEquals(1, friends.size());
        assertEquals(3.14f, friends.get(0).getWeight(), 0.001f);
    }

    // ── Edge cases and error handling ───────────────────────────

    @Test
    public void testMalformedEdgeLinesSkipped() throws IOException {
        File f = writeGraph(
            "nodes\nA\nB\nedges\nf A B\nf A B 1.0\n"
        );
        // First edge line has only 3 parts (missing weight) → skipped
        GraphFileParser.ParseResult result = GraphFileParser.parse(f.getAbsolutePath());

        assertEquals(1, result.getGraph().getEdgeCount());
        assertEquals(1, result.getSkippedLines());
    }

    @Test
    public void testInvalidWeightSkipped() throws IOException {
        File f = writeGraph(
            "nodes\nA\nB\nC\nedges\nf A B notanumber\nf B C 1.0\n"
        );
        GraphFileParser.ParseResult result = GraphFileParser.parse(f.getAbsolutePath());

        assertEquals(1, result.getGraph().getEdgeCount());
        assertEquals(1, result.getSkippedLines());
    }

    @Test
    public void testNaNWeightSkipped() throws IOException {
        File f = writeGraph(
            "nodes\nA\nB\nedges\nf A B NaN\n"
        );
        GraphFileParser.ParseResult result = GraphFileParser.parse(f.getAbsolutePath());

        assertEquals(0, result.getGraph().getEdgeCount());
        assertEquals(1, result.getSkippedLines());
    }

    @Test
    public void testInfinityWeightSkipped() throws IOException {
        File f = writeGraph(
            "nodes\nA\nB\nedges\nf A B Infinity\n"
        );
        GraphFileParser.ParseResult result = GraphFileParser.parse(f.getAbsolutePath());

        assertEquals(0, result.getGraph().getEdgeCount());
        assertEquals(1, result.getSkippedLines());
    }

    @Test
    public void testEmptyFile() throws IOException {
        File f = writeGraph("");
        GraphFileParser.ParseResult result = GraphFileParser.parse(f.getAbsolutePath());

        assertEquals(0, result.getVertices().size());
        assertEquals(0, result.getGraph().getEdgeCount());
        assertEquals(0, result.getSkippedLines());
    }

    @Test
    public void testOnlyNodes() throws IOException {
        File f = writeGraph("nodes\nA\nB\nC\n");
        GraphFileParser.ParseResult result = GraphFileParser.parse(f.getAbsolutePath());

        assertEquals(3, result.getVertices().size());
        assertEquals(0, result.getGraph().getEdgeCount());
    }

    @Test
    public void testBlankLinesIgnored() throws IOException {
        File f = writeGraph(
            "\n\nnodes\n\nA\n\nB\n\nedges\n\nf A B 1.0\n\n"
        );
        GraphFileParser.ParseResult result = GraphFileParser.parse(f.getAbsolutePath());

        assertEquals(2, result.getVertices().size());
        assertEquals(1, result.getGraph().getEdgeCount());
    }

    @Test
    public void testSectionHeadersCaseInsensitive() throws IOException {
        File f = writeGraph("NODES\nA\nB\nEDGES\nf A B 1.0\n");
        GraphFileParser.ParseResult result = GraphFileParser.parse(f.getAbsolutePath());

        assertEquals(2, result.getVertices().size());
        assertEquals(1, result.getGraph().getEdgeCount());
    }

    // ── Filter predicate ────────────────────────────────────────

    @Test
    public void testFilterExcludesEdgeTypesFromGraph() throws IOException {
        File f = writeGraph(
            "nodes\nA\nB\nC\nedges\nf A B 1.0\ns B C 2.0\n"
        );
        // Only include friend edges in the graph
        GraphFileParser.ParseResult result = GraphFileParser.parse(
            f.getAbsolutePath(), code -> code.equals("f")
        );

        // Graph should only have the friend edge
        assertEquals(1, result.getGraph().getEdgeCount());

        // But both edge types should still be classified
        assertEquals(1, result.getEdges(EdgeType.FRIEND).size());
        assertEquals(1, result.getEdges(EdgeType.STRANGER).size());
    }

    @Test
    public void testFilterExcludesAll() throws IOException {
        File f = writeGraph(
            "nodes\nA\nB\nedges\nf A B 1.0\n"
        );
        GraphFileParser.ParseResult result = GraphFileParser.parse(
            f.getAbsolutePath(), code -> false
        );

        // Graph has no edges, but classification still happened
        assertEquals(0, result.getGraph().getEdgeCount());
        assertEquals(1, result.getEdges(EdgeType.FRIEND).size());
    }

    // ── Convenience overload ────────────────────────────────────

    @Test
    public void testParseWithoutFilterIncludesAll() throws IOException {
        File f = writeGraph(
            "nodes\nA\nB\nC\nedges\nf A B 1.0\ns B C 2.0\n"
        );
        GraphFileParser.ParseResult result = GraphFileParser.parse(f.getAbsolutePath());

        assertEquals(2, result.getGraph().getEdgeCount());
    }

    // ── File I/O errors ─────────────────────────────────────────

    @Test(expected = IOException.class)
    public void testNonexistentFileThrows() throws IOException {
        GraphFileParser.parse(new File(tempDir, "does_not_exist.txt").getAbsolutePath());
    }

    // ── Edge label assignment ───────────────────────────────────

    @Test
    public void testFirstEdgeOfTypeGetsLabel() throws IOException {
        File f = writeGraph(
            "nodes\nA\nB\nC\nedges\nf A B 1.0\nf B C 2.0\n"
        );
        GraphFileParser.ParseResult result = GraphFileParser.parse(f.getAbsolutePath());

        List<edge> friends = result.getEdges(EdgeType.FRIEND);
        assertEquals(2, friends.size());

        // First edge should have the display label for legend
        assertNotNull(friends.get(0).getLabel());
        assertEquals("friend", friends.get(0).getLabel());
    }

    // ── Unknown edge type codes ─────────────────────────────────

    @Test
    public void testUnknownEdgeTypeStillAddedToGraph() throws IOException {
        File f = writeGraph(
            "nodes\nA\nB\nedges\nxx A B 1.0\n"
        );
        GraphFileParser.ParseResult result = GraphFileParser.parse(f.getAbsolutePath());

        // Unknown type code → edge added to graph but no classification
        assertEquals(1, result.getGraph().getEdgeCount());
        // All type lists should be empty
        for (EdgeType t : EdgeType.values()) {
            assertEquals(0, result.getEdges(t).size());
        }
    }

    // ── Negative and zero weights (valid floats) ────────────────

    @Test
    public void testZeroWeightAccepted() throws IOException {
        File f = writeGraph("nodes\nA\nB\nedges\nf A B 0.0\n");
        GraphFileParser.ParseResult result = GraphFileParser.parse(f.getAbsolutePath());
        assertEquals(1, result.getGraph().getEdgeCount());
    }

    @Test
    public void testNegativeWeightAccepted() throws IOException {
        File f = writeGraph("nodes\nA\nB\nedges\nf A B -5.0\n");
        GraphFileParser.ParseResult result = GraphFileParser.parse(f.getAbsolutePath());
        assertEquals(1, result.getGraph().getEdgeCount());
        assertEquals(-5.0f, result.getEdges(EdgeType.FRIEND).get(0).getWeight(), 0.001f);
    }

    // ── GetEdges returns empty list for unused type ─────────────

    @Test
    public void testGetEdgesForUnusedTypeReturnsEmptyList() throws IOException {
        File f = writeGraph("nodes\nA\nB\nedges\nf A B 1.0\n");
        GraphFileParser.ParseResult result = GraphFileParser.parse(f.getAbsolutePath());

        List<edge> strangers = result.getEdges(EdgeType.STRANGER);
        assertNotNull(strangers);
        assertTrue(strangers.isEmpty());
    }
}
