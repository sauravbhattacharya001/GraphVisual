package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphVoronoiPartitioner}.
 *
 * <p>Covers construction validation, multi-source BFS partitioning,
 * tie-breaking semantics, boundary edge detection, unreachable vertex
 * handling, cell statistics, the dual-graph adjacency map, and the
 * text/HTML export paths.</p>
 *
 * @author sauravbhattacharya001
 */
public class GraphVoronoiPartitionerTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
    }

    // ---------- helpers ----------

    private void addEdge(String v1, String v2) {
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        Edge e = new Edge("f", v1, v2);
        e.setWeight(1.0f);
        graph.addEdge(e, v1, v2);
    }

    private void buildLine(String... ids) {
        for (int i = 0; i + 1 < ids.length; i++) addEdge(ids[i], ids[i + 1]);
    }

    // ---------- constructor validation ----------

    @Test(expected = NullPointerException.class)
    public void constructorRejectsNullGraph() {
        new GraphVoronoiPartitioner(null, Set.of("A"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsNullSeeds() {
        graph.addVertex("A");
        new GraphVoronoiPartitioner(graph, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsEmptySeeds() {
        graph.addVertex("A");
        new GraphVoronoiPartitioner(graph, new HashSet<>());
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsUnknownSeed() {
        graph.addVertex("A");
        new GraphVoronoiPartitioner(graph, Set.of("ghost"));
    }

    // ---------- core partitioning ----------

    @Test
    public void singleSeedAbsorbsAllReachableVertices() {
        // A - B - C - D
        buildLine("A", "B", "C", "D");
        GraphVoronoiPartitioner p = new GraphVoronoiPartitioner(graph, Set.of("A"));
        GraphVoronoiPartitioner.VoronoiResult r = p.partition();

        assertEquals(1, r.cells.size());
        Set<String> cell = r.cells.get("A");
        assertEquals(new LinkedHashSet<>(java.util.Arrays.asList("A", "B", "C", "D")), cell);
        assertTrue(r.unreachable.isEmpty());
        assertTrue("single-cell partition has no boundary edges",
                r.boundaryEdges.isEmpty());

        // Distances should be 0,1,2,3 along the line
        assertEquals(Integer.valueOf(0), r.distances.get("A"));
        assertEquals(Integer.valueOf(1), r.distances.get("B"));
        assertEquals(Integer.valueOf(2), r.distances.get("C"));
        assertEquals(Integer.valueOf(3), r.distances.get("D"));
    }

    @Test
    public void twoSeedsSplitLineAtMidpoint() {
        // A - B - C - D - E
        // Seeds A and E should each claim 2 neighbors; C is equidistant (dist=2).
        // Tie-break: alphabetically smaller seed (A) wins C.
        buildLine("A", "B", "C", "D", "E");
        GraphVoronoiPartitioner p = new GraphVoronoiPartitioner(graph, Set.of("A", "E"));
        GraphVoronoiPartitioner.VoronoiResult r = p.partition();

        assertEquals("A", r.assignment.get("A"));
        assertEquals("A", r.assignment.get("B"));
        assertEquals("A", r.assignment.get("C")); // tie-break to A
        assertEquals("E", r.assignment.get("D"));
        assertEquals("E", r.assignment.get("E"));

        // Exactly one boundary edge: C-D
        assertEquals(1, r.boundaryEdges.size());
        Edge be = r.boundaryEdges.get(0);
        String v1 = be.getVertex1(), v2 = be.getVertex2();
        assertTrue((v1.equals("C") && v2.equals("D")) || (v1.equals("D") && v2.equals("C")));
    }

    @Test
    public void unreachableVerticesAreReported() {
        // Component 1: A - B   Component 2: X - Y
        addEdge("A", "B");
        addEdge("X", "Y");
        GraphVoronoiPartitioner p = new GraphVoronoiPartitioner(graph, Set.of("A"));
        GraphVoronoiPartitioner.VoronoiResult r = p.partition();

        assertEquals(Set.of("X", "Y"), r.unreachable);
        assertFalse(r.assignment.containsKey("X"));
        assertFalse(r.assignment.containsKey("Y"));
        assertEquals(2, r.cells.get("A").size());
    }

    @Test
    public void cellStatsComputeSizeAndInternalEdges() {
        // Triangle A-B-C plus a pendant D off A
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        addEdge("A", "D");
        // X-Y reachable only from X
        addEdge("X", "Y");

        GraphVoronoiPartitioner p = new GraphVoronoiPartitioner(graph, Set.of("A", "X"));
        GraphVoronoiPartitioner.VoronoiResult r = p.partition();

        GraphVoronoiPartitioner.CellStats a = r.cellStats.get("A");
        assertEquals(4, a.size);                 // A, B, C, D
        // AB, BC, AC, AD => all internal to cell A. Count is 4.
        assertEquals(4, a.internalEdges);
        assertEquals(0, a.boundaryEdgeCount);
        assertEquals(0, a.boundaryVertices);
        // density = 2 * 4 / (4 * 3) = 8/12
        assertEquals(8.0 / 12.0, a.density, 1e-9);

        GraphVoronoiPartitioner.CellStats x = r.cellStats.get("X");
        assertEquals(2, x.size);
        assertEquals(1, x.internalEdges);
        assertEquals(1.0, x.density, 1e-9);
    }

    @Test
    public void dualGraphAdjacencyMatchesBoundaryEdges() {
        // A - B - C with three seeds
        buildLine("A", "B", "C");
        GraphVoronoiPartitioner p = new GraphVoronoiPartitioner(graph, Set.of("A", "B", "C"));
        GraphVoronoiPartitioner.VoronoiResult r = p.partition();

        // Each vertex is its own seed
        assertEquals("A", r.assignment.get("A"));
        assertEquals("B", r.assignment.get("B"));
        assertEquals("C", r.assignment.get("C"));

        // Adjacency: A<->B, B<->C
        assertTrue(r.adjacency.get("A").contains("B"));
        assertTrue(r.adjacency.get("B").contains("A"));
        assertTrue(r.adjacency.get("B").contains("C"));
        assertTrue(r.adjacency.get("C").contains("B"));
        assertFalse(r.adjacency.get("A").contains("C"));

        assertEquals(2, r.boundaryEdges.size());
    }

    @Test
    public void resultCollectionsAreUnmodifiable() {
        addEdge("A", "B");
        GraphVoronoiPartitioner p = new GraphVoronoiPartitioner(graph, Set.of("A"));
        GraphVoronoiPartitioner.VoronoiResult r = p.partition();

        try { r.assignment.put("Q", "A"); fail("assignment must be unmodifiable"); }
        catch (UnsupportedOperationException expected) { }
        try { r.cells.remove("A"); fail("cells must be unmodifiable"); }
        catch (UnsupportedOperationException expected) { }
        try { r.unreachable.add("Q"); fail("unreachable must be unmodifiable"); }
        catch (UnsupportedOperationException expected) { }
        try { r.boundaryEdges.clear(); fail("boundaryEdges must be unmodifiable"); }
        catch (UnsupportedOperationException expected) { }
    }

    // ---------- text export ----------

    @Test
    public void toTextContainsAllSeedHeaders() {
        buildLine("A", "B", "C", "D", "E");
        GraphVoronoiPartitioner p = new GraphVoronoiPartitioner(graph, Set.of("A", "E"));
        GraphVoronoiPartitioner.VoronoiResult r = p.partition();

        String text = r.toText();
        assertNotNull(text);
        assertTrue(text.contains("Seeds: 2"));
        assertTrue(text.contains("Assigned vertices: 5"));
        assertTrue("text report must mention seed A", text.contains("A"));
        assertTrue("text report must mention seed E", text.contains("E"));
        assertTrue(text.contains("Boundary edges:"));
        assertTrue(text.contains("Density:"));
    }

    @Test
    public void toTextReportsUnreachableCount() {
        addEdge("A", "B");
        addEdge("X", "Y"); // disconnected component
        GraphVoronoiPartitioner p = new GraphVoronoiPartitioner(graph, Set.of("A"));
        String text = p.partition().toText();
        assertTrue("expected 'Unreachable vertices: 2' in:\n" + text,
                text.contains("Unreachable vertices: 2"));
    }

    // ---------- html export ----------

    @Test
    public void exportHtmlWritesNonEmptyFileWithLegend() throws Exception {
        buildLine("A", "B", "C", "D", "E");
        GraphVoronoiPartitioner p = new GraphVoronoiPartitioner(graph, Set.of("A", "E"));
        GraphVoronoiPartitioner.VoronoiResult r = p.partition();

        File tmp = File.createTempFile("voronoi-", ".html");
        tmp.deleteOnExit();
        try {
            p.exportHtml(r, tmp.getAbsolutePath());
            String html = Files.readString(tmp.toPath());

            assertTrue(html.startsWith("<!DOCTYPE html>"));
            assertTrue(html.contains("Graph Voronoi Partition"));
            assertTrue(html.contains("Legend"));
            assertTrue(html.contains("Cell Details"));
            assertTrue(html.contains("Cell Adjacency (Dual Graph)"));
            // Seeds appear by id
            assertTrue(html.contains(">A<") || html.contains("A</h3>") || html.contains("A&nbsp"));
            assertTrue(html.contains(">E<") || html.contains("E</h3>") || html.contains("E&nbsp"));
            // Boundary edges section reflects single boundary
            assertTrue(html.contains("Boundary Edges (1)"));
        } finally {
            if (tmp.exists() && !tmp.delete()) tmp.deleteOnExit();
        }
    }
}
