package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link SubgraphExtractor}.
 *
 * Tests the fluent builder API for subgraph extraction including
 * edge type filtering, weight filtering, degree filtering, k-hop
 * neighborhoods, node whitelisting, time windows, connected-only
 * mode, CSV export, and result statistics.
 */
public class SubgraphExtractorTest {

    private Graph<String, Edge> graph;
    private List<Edge> edges;

    /** Helper to create an edge with type, vertices, weight, and optional timestamps. */
    private Edge makeEdge(String type, String v1, String v2, float weight) {
        Edge e  new Edge(type, v1, v2);
        e.setWeight(weight);
        return e;
    }

    private Edge makeTimedEdge(String type, String v1, String v2, float weight,
                               long start, long end) {
        Edge e  makeEdge(type, v1, v2, weight);
        e.setTimestamp(start);
        e.setEndTimestamp(end);
        return e;
    }

    /**
     * Build a small test graph:
     *   A --f(3.0)-- B --c(1.0)-- C --f(5.0)-- D --s(2.0)-- E
     *   A --c(4.0)-- C
     *   B --f(2.0)-- D
     */
    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
        edges = new ArrayList<>();

        Edge e1  makeEdge("f", "A", "B", 3.0f);
        Edge e2  makeEdge("c", "B", "C", 1.0f);
        Edge e3  makeEdge("f", "C", "D", 5.0f);
        Edge e4  makeEdge("s", "D", "E", 2.0f);
        Edge e5  makeEdge("c", "A", "C", 4.0f);
        Edge e6  makeEdge("f", "B", "D", 2.0f);

        for (String v : Arrays.asList("A", "B", "C", "D", "E")) {
            graph.addVertex(v);
        }
        for (Edge e : Arrays.asList(e1, e2, e3, e4, e5, e6)) {
            graph.addEdge(e, e.getVertex1(), e.getVertex2());
            edges.add(e);
        }
    }

    // --- Constructor validation ---

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new SubgraphExtractor(null, edges);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullEdgeListThrows() {
        new SubgraphExtractor(graph, null);
    }

    // --- No filters: full extraction ---

    @Test
    public void testExtractWithNoFiltersReturnsFullGraph() {
        SubgraphExtractor.Result result = new SubgraphExtractor(graph, edges).extract();
        assertEquals(5, result.getNodeCount());
        assertEquals(6, result.getEdgeCount());
        assertEquals(1.0, result.getNodeRetention(), 0.001);
        assertEquals(1.0, result.getEdgeRetention(), 0.001);
    }

    // --- Edge type filtering ---

    @Test
    public void testFilterByEdgeTypeKeepsOnlyMatchingEdges() {
        SubgraphExtractor.Result result = new SubgraphExtractor(graph, edges)
                .filterByEdgeType("f")
                .extract();
        // 3 "f" edges: A-B, C-D, B-D
        assertEquals(3, result.getEdgeCount());
        // Nodes involved: A, B, C, D (all have at least one "f" edge)
        // E is included as vertex (not connectedOnly) but has no f-edge
        assertTrue(result.getGraph().containsVertex("A"));
        assertTrue(result.getGraph().containsVertex("B"));
    }

    @Test
    public void testFilterByMultipleEdgeTypes() {
        SubgraphExtractor.Result result = new SubgraphExtractor(graph, edges)
                .filterByEdgeType("f", "s")
                .extract();
        // 3 "f" + 1 "s" = 4 edges
        assertEquals(4, result.getEdgeCount());
    }

    // --- Weight filtering ---

    @Test
    public void testFilterByMinWeight() {
        SubgraphExtractor.Result result = new SubgraphExtractor(graph, edges)
                .filterByMinWeight(3.0f)
                .extract();
        // Edges with weight >= 3: A-B(3), C-D(5), A-C(4) = 3
        assertEquals(3, result.getEdgeCount());
    }

    @Test
    public void testFilterByMaxWeight() {
        SubgraphExtractor.Result result = new SubgraphExtractor(graph, edges)
                .filterByMaxWeight(2.0f)
                .extract();
        // Edges with weight <= 2: B-C(1), D-E(2), B-D(2) = 3
        assertEquals(3, result.getEdgeCount());
    }

    @Test
    public void testFilterByWeightRange() {
        SubgraphExtractor.Result result = new SubgraphExtractor(graph, edges)
                .filterByMinWeight(2.0f)
                .filterByMaxWeight(4.0f)
                .extract();
        // Edges: A-B(3), D-E(2), A-C(4), B-D(2) = 4
        assertEquals(4, result.getEdgeCount());
    }

    // --- Degree filtering ---

    @Test
    public void testFilterByDegreeRangeRemovesLowDegreeNodes() {
        SubgraphExtractor.Result result = new SubgraphExtractor(graph, edges)
                .filterByDegreeRange(2, null)
                .extract();
        // In full graph: A=3, B=3, C=3, D=3, E=1 → E removed
        assertFalse(result.getGraph().containsVertex("E"));
        assertTrue(result.getGraph().containsVertex("A"));
    }

    @Test
    public void testFilterByMaxDegreeRemovesHighDegreeNodes() {
        SubgraphExtractor.Result result = new SubgraphExtractor(graph, edges)
                .filterByDegreeRange(null, 1)
                .extract();
        // Only E has degree 1 in full graph, all others have 3
        assertEquals(1, result.getNodeCount());
        assertTrue(result.getGraph().containsVertex("E"));
    }

    // --- K-hop neighborhood ---

    @Test
    public void testOneHopNeighborhood() {
        SubgraphExtractor.Result result = new SubgraphExtractor(graph, edges)
                .filterByNeighborhood("A", 1)
                .extract();
        // A's neighbors: B, C. So candidate = {A, B, C}
        assertTrue(result.getGraph().containsVertex("A"));
        assertTrue(result.getGraph().containsVertex("B"));
        assertTrue(result.getGraph().containsVertex("C"));
        // D, E not in 1-hop from A
        assertFalse(result.getGraph().containsVertex("D"));
        assertFalse(result.getGraph().containsVertex("E"));
    }

    @Test
    public void testTwoHopNeighborhood() {
        SubgraphExtractor.Result result = new SubgraphExtractor(graph, edges)
                .filterByNeighborhood("A", 2)
                .extract();
        // 1-hop: B, C. 2-hop adds D. E is 3 hops from A.
        assertTrue(result.getGraph().containsVertex("D"));
        assertFalse(result.getGraph().containsVertex("E"));
    }

    @Test
    public void testNeighborhoodOfNonExistentSeedReturnsEmpty() {
        SubgraphExtractor.Result result = new SubgraphExtractor(graph, edges)
                .filterByNeighborhood("NONEXISTENT", 2)
                .extract();
        assertEquals(0, result.getNodeCount());
        assertEquals(0, result.getEdgeCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNeighborhoodNullSeedThrows() {
        new SubgraphExtractor(graph, edges).filterByNeighborhood(null, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNeighborhoodZeroHopsThrows() {
        new SubgraphExtractor(graph, edges).filterByNeighborhood("A", 0);
    }

    // --- Node whitelist ---

    @Test
    public void testFilterByNodesKeepsOnlyWhitelistedNodesAndMutualEdges() {
        SubgraphExtractor.Result result = new SubgraphExtractor(graph, edges)
                .filterByNodes("A", "B", "C")
                .extract();
        assertEquals(3, result.getNodeCount());
        // Edges among {A,B,C}: A-B(f), B-C(c), A-C(c) = 3
        assertEquals(3, result.getEdgeCount());
    }

    @Test
    public void testFilterByNodesWithNonExistentNodesIgnoresThem() {
        SubgraphExtractor.Result result = new SubgraphExtractor(graph, edges)
                .filterByNodes("A", "FAKE")
                .extract();
        // Only A exists, no edges with just 1 node
        assertEquals(1, result.getNodeCount());
        assertEquals(0, result.getEdgeCount());
    }

    // --- Time window filtering ---

    @Test
    public void testFilterByTimeWindow() {
        // Replace edges with timed ones
        List<Edge> timedEdges = new ArrayList<>();
        timedEdges.add(makeTimedEdge("f", "A", "B", 3.0f, 100, 200));
        timedEdges.add(makeTimedEdge("c", "B", "C", 1.0f, 300, 400));
        timedEdges.add(makeTimedEdge("f", "C", "D", 5.0f, 150, 250));

        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (String v : Arrays.asList("A", "B", "C", "D")) g.addVertex(v);
        for (Edge e : timedEdges) g.addEdge(e, e.getVertex1(), e.getVertex2());

        SubgraphExtractor.Result result = new SubgraphExtractor(g, timedEdges)
                .filterByTimeWindow(100, 200)
                .extract();
        // A-B active [100,200] overlaps [100,200] ✓
        // B-C active [300,400] does NOT overlap [100,200] ✗
        // C-D active [150,250] overlaps [100,200] ✓
        assertEquals(2, result.getEdgeCount());
    }

    // --- Connected-only mode ---

    @Test
    public void testConnectedOnlyRemovesIsolatedNodes() {
        SubgraphExtractor.Result result = new SubgraphExtractor(graph, edges)
                .filterByEdgeType("f")
                .connectedOnly(true)
                .extract();
        // f-edges: A-B, C-D, B-D → nodes with edges: A,B,C,D
        // E has no f-edge → removed
        assertFalse(result.getGraph().containsVertex("E"));
        assertEquals(4, result.getNodeCount());
    }

    // --- Combined filters ---

    @Test
    public void testCombinedEdgeTypeAndWeight() {
        SubgraphExtractor.Result result = new SubgraphExtractor(graph, edges)
                .filterByEdgeType("f")
                .filterByMinWeight(3.0f)
                .extract();
        // f-edges: A-B(3), C-D(5), B-D(2). Weight >= 3: A-B(3), C-D(5)
        assertEquals(2, result.getEdgeCount());
    }

    @Test
    public void testNeighborhoodPlusEdgeTypeFilter() {
        SubgraphExtractor.Result result = new SubgraphExtractor(graph, edges)
                .filterByNeighborhood("A", 1)
                .filterByEdgeType("c")
                .extract();
        // 1-hop from A: {A,B,C}. c-edges among them: B-C(c), A-C(c) = 2
        assertEquals(2, result.getEdgeCount());
    }

    // --- Result statistics ---

    @Test
    public void testDensityCalculation() {
        SubgraphExtractor.Result result = new SubgraphExtractor(graph, edges).extract();
        // density = 2*6 / (5*4) = 0.6
        assertEquals(0.6, result.getDensity(), 0.001);
    }

    @Test
    public void testDensityWithSingleNode() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("X");
        SubgraphExtractor.Result result = new SubgraphExtractor(g, Collections.emptyList())
                .extract();
        assertEquals(0.0, result.getDensity(), 0.001);
    }

    @Test
    public void testRetentionOnEmptySourceGraph() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("X");
        SubgraphExtractor.Result result = new SubgraphExtractor(g, Collections.emptyList())
                .extract();
        // 1 node retained out of 1
        assertEquals(1.0, result.getNodeRetention(), 0.001);
        assertEquals(0.0, result.getEdgeRetention(), 0.001);
    }

    @Test
    public void testEdgeTypeBreakdown() {
        SubgraphExtractor.Result result = new SubgraphExtractor(graph, edges).extract();
        Map<String, Integer> breakdown = result.getEdgeTypeBreakdown();
        assertFalse(breakdown.isEmpty());
    }

    @Test
    public void testSummaryContainsKeyInfo() {
        SubgraphExtractor.Result result = new SubgraphExtractor(graph, edges).extract();
        String summary = result.getSummary();
        assertTrue(summary.contains("Subgraph Extraction Summary"));
        assertTrue(summary.contains("Nodes:"));
        assertTrue(summary.contains("Edges:"));
        assertTrue(summary.contains("Density:"));
    }

    // --- CSV export ---

    @Test
    public void testExportEdgeListToString() {
        SubgraphExtractor.Result result = new SubgraphExtractor(graph, edges)
                .filterByNodes("A", "B")
                .extract();
        String csv = result.exportEdgeListToString();
        assertTrue(csv.startsWith("source,target,type,weight,label"));
        assertTrue(csv.contains("A"));
        assertTrue(csv.contains("B"));
    }

    @Test
    public void testExportEdgeListToFile() throws IOException {
        SubgraphExtractor.Result result = new SubgraphExtractor(graph, edges).extract();
        File tmp = File.createTempFile("subgraph_test_", ".csv");
        tmp.deleteOnExit();
        result.exportEdgeList(tmp);
        List<String> lines = Files.readAllLines(tmp.toPath());
        assertEquals("Header + 6 data lines", 7, lines.size());
        assertTrue(lines.get(0).contains("source,target,type,weight,label"));
    }
}
