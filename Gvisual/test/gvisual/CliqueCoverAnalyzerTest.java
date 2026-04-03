package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link CliqueCoverAnalyzer}.
 *
 * Covers greedy clique cover, exact minimum clique cover, cover validation,
 * quality metrics, bounds, and the full report.
 */
public class CliqueCoverAnalyzerTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
    }

    // --- Helpers ---

    private void addEdge(String v1, String v2) {
        Edge e = new Edge("f", v1, v2);
        e.setWeight(1.0f);
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        graph.addEdge(e, v1, v2);
    }

    private void makeComplete(String... vertices) {
        for (int i = 0; i < vertices.length; i++) {
            if (!graph.containsVertex(vertices[i])) graph.addVertex(vertices[i]);
            for (int j = i + 1; j < vertices.length; j++) {
                addEdge(vertices[i], vertices[j]);
            }
        }
    }

    private void assertValidCover(List<Set<String>> cover) {
        CliqueCoverAnalyzer analyzer = new CliqueCoverAnalyzer(graph);
        assertTrue("Cover should be valid", analyzer.isValidCliqueCover(cover));
    }

    // ── Empty graph ─────────────────────────────────────────────────

    @Test
    public void testEmptyGraph() {
        CliqueCoverAnalyzer analyzer = new CliqueCoverAnalyzer(graph);
        List<Set<String>> cover = analyzer.greedyCliqueCover();
        assertTrue("Empty graph should have empty cover", cover.isEmpty());
        assertTrue(analyzer.isValidCliqueCover(cover));
    }

    // ── Single vertex ───────────────────────────────────────────────

    @Test
    public void testSingleVertex() {
        graph.addVertex("A");
        CliqueCoverAnalyzer analyzer = new CliqueCoverAnalyzer(graph);
        List<Set<String>> cover = analyzer.greedyCliqueCover();
        assertEquals(1, cover.size());
        assertTrue(cover.get(0).contains("A"));
        assertValidCover(cover);
    }

    // ── Complete graph is a single clique ───────────────────────────

    @Test
    public void testCompleteGraphSingleClique() {
        makeComplete("A", "B", "C", "D");
        CliqueCoverAnalyzer analyzer = new CliqueCoverAnalyzer(graph);
        List<Set<String>> cover = analyzer.greedyCliqueCover();
        assertEquals("Complete graph should be covered by 1 clique", 1, cover.size());
        assertEquals(4, cover.get(0).size());
        assertValidCover(cover);
    }

    // ── Independent set: each vertex is its own clique ──────────────

    @Test
    public void testIndependentSetEachVertexSeparateClique() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        CliqueCoverAnalyzer analyzer = new CliqueCoverAnalyzer(graph);
        List<Set<String>> cover = analyzer.greedyCliqueCover();
        assertEquals("Independent set needs n cliques", 3, cover.size());
        assertValidCover(cover);
    }

    // ── Path graph P3: A-B-C ────────────────────────────────────────

    @Test
    public void testPathGraphP3() {
        addEdge("A", "B");
        addEdge("B", "C");
        CliqueCoverAnalyzer analyzer = new CliqueCoverAnalyzer(graph);
        List<Set<String>> cover = analyzer.greedyCliqueCover();
        // P3 needs at least 2 cliques (e.g. {A,B} and {C})
        assertTrue("Path P3 needs >= 2 cliques", cover.size() >= 2);
        assertValidCover(cover);
    }

    // ── Two disjoint triangles ──────────────────────────────────────

    @Test
    public void testTwoDisjointTriangles() {
        makeComplete("A", "B", "C");
        makeComplete("D", "E", "F");
        CliqueCoverAnalyzer analyzer = new CliqueCoverAnalyzer(graph);
        List<Set<String>> cover = analyzer.greedyCliqueCover();
        assertEquals("Two triangles need exactly 2 cliques", 2, cover.size());
        assertValidCover(cover);
    }

    // ── Exact minimum on small graph ────────────────────────────────

    @Test
    public void testExactMinimumOnSmallGraph() {
        // Square: A-B-C-D-A → minimum cover is 2 ({A,B},{C,D}) or ({A,D},{B,C})
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "A");
        CliqueCoverAnalyzer analyzer = new CliqueCoverAnalyzer(graph);
        List<Set<String>> exact = analyzer.exactMinimumCliqueCover();
        assertEquals("Square graph needs exactly 2 cliques", 2, exact.size());
        assertTrue(analyzer.isValidCliqueCover(exact));
    }

    @Test
    public void testExactMatchesGreedyOnCompleteGraph() {
        makeComplete("A", "B", "C", "D", "E");
        CliqueCoverAnalyzer analyzer = new CliqueCoverAnalyzer(graph);
        List<Set<String>> exact = analyzer.exactMinimumCliqueCover();
        assertEquals(1, exact.size());
    }

    // ── Cover validation ────────────────────────────────────────────

    @Test
    public void testValidCoverAccepted() {
        makeComplete("A", "B", "C");
        graph.addVertex("D");
        CliqueCoverAnalyzer analyzer = new CliqueCoverAnalyzer(graph);
        List<Set<String>> cover = new ArrayList<>();
        cover.add(new HashSet<>(Arrays.asList("A", "B", "C")));
        cover.add(new HashSet<>(Collections.singletonList("D")));
        assertTrue(analyzer.isValidCliqueCover(cover));
    }

    @Test
    public void testInvalidCoverNotAClique() {
        addEdge("A", "B");
        graph.addVertex("C"); // C not connected to A or B
        CliqueCoverAnalyzer analyzer = new CliqueCoverAnalyzer(graph);
        List<Set<String>> cover = new ArrayList<>();
        cover.add(new HashSet<>(Arrays.asList("A", "B", "C"))); // not a clique
        assertFalse(analyzer.isValidCliqueCover(cover));
    }

    @Test
    public void testInvalidCoverMissingVertex() {
        addEdge("A", "B");
        graph.addVertex("C");
        CliqueCoverAnalyzer analyzer = new CliqueCoverAnalyzer(graph);
        List<Set<String>> cover = new ArrayList<>();
        cover.add(new HashSet<>(Arrays.asList("A", "B")));
        // Missing C
        assertFalse(analyzer.isValidCliqueCover(cover));
    }

    @Test
    public void testInvalidCoverDuplicateVertex() {
        addEdge("A", "B");
        CliqueCoverAnalyzer analyzer = new CliqueCoverAnalyzer(graph);
        List<Set<String>> cover = new ArrayList<>();
        cover.add(new HashSet<>(Arrays.asList("A", "B")));
        cover.add(new HashSet<>(Collections.singletonList("A"))); // duplicate A
        assertFalse(analyzer.isValidCliqueCover(cover));
    }

    // ── Cover metrics ───────────────────────────────────────────────

    @Test
    public void testCoverMetricsOnBalancedCover() {
        makeComplete("A", "B", "C");
        makeComplete("D", "E", "F");
        CliqueCoverAnalyzer analyzer = new CliqueCoverAnalyzer(graph);
        List<Set<String>> cover = analyzer.greedyCliqueCover();
        Map<String, Object> metrics = analyzer.coverMetrics(cover);

        assertEquals(2, metrics.get("numCliques"));
        assertEquals(true, metrics.get("valid"));
        assertEquals(3, metrics.get("minCliqueSize"));
        assertEquals(3, metrics.get("maxCliqueSize"));
        assertEquals(1.0, (double) metrics.get("balance"), 0.01);
    }

    @Test
    public void testCoverMetricsOnEmptyCover() {
        CliqueCoverAnalyzer analyzer = new CliqueCoverAnalyzer(graph);
        Map<String, Object> metrics = analyzer.coverMetrics(new ArrayList<>());
        assertEquals(0, metrics.get("numCliques"));
        assertEquals(0, metrics.get("minCliqueSize"));
        assertEquals(0, metrics.get("maxCliqueSize"));
    }

    // ── Bounds ──────────────────────────────────────────────────────

    @Test
    public void testCliqueCoverBounds() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        CliqueCoverAnalyzer analyzer = new CliqueCoverAnalyzer(graph);
        Map<String, Integer> bounds = analyzer.cliqueCoverBounds();

        assertTrue(bounds.get("lowerBound") >= 1);
        assertTrue(bounds.get("upperBound") >= bounds.get("lowerBound"));
    }

    @Test
    public void testBoundsOnCompleteGraph() {
        makeComplete("A", "B", "C", "D");
        CliqueCoverAnalyzer analyzer = new CliqueCoverAnalyzer(graph);
        Map<String, Integer> bounds = analyzer.cliqueCoverBounds();
        assertEquals(Integer.valueOf(1), bounds.get("upperBound"));
    }

    // ── Full report ─────────────────────────────────────────────────

    @Test
    public void testFullReportContainsKeyInfo() {
        makeComplete("A", "B", "C");
        graph.addVertex("D");
        addEdge("D", "A");
        CliqueCoverAnalyzer analyzer = new CliqueCoverAnalyzer(graph);
        String report = analyzer.fullReport();

        assertTrue(report.contains("Clique Cover Analysis"));
        assertTrue(report.contains("4 vertices"));
        assertTrue(report.contains("Greedy clique cover"));
        assertTrue(report.contains("Quality metrics"));
        assertTrue(report.contains("Exact minimum clique cover"));
    }

    @Test
    public void testFullReportSkipsExactForLargeGraph() {
        // Create graph with > 20 vertices
        for (int i = 0; i < 25; i++) {
            graph.addVertex("V" + i);
        }
        for (int i = 0; i < 24; i++) {
            addEdge("V" + i, "V" + (i + 1));
        }
        CliqueCoverAnalyzer analyzer = new CliqueCoverAnalyzer(graph);
        String report = analyzer.fullReport();
        assertTrue(report.contains("Exact solver skipped"));
    }

    // ── Null graph argument ─────────────────────────────────────────

    @Test(expected = NullPointerException.class)
    public void testNullGraphThrows() {
        new CliqueCoverAnalyzer(null);
    }
}
