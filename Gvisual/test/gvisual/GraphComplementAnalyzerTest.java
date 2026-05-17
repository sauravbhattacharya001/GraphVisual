package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import edu.uci.ics.jung.graph.util.Pair;
import org.junit.Test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphComplementAnalyzer}.
 *
 * <p>The complement G' of G has the same vertex set and contains exactly
 * the edges that are missing from G. These tests cover edge cases (empty,
 * single vertex), well-known graphs (complete, empty, path, cycle), and
 * the invariant {@code |E(G)| + |E(G')| = n*(n-1)/2}.</p>
 */
public class GraphComplementAnalyzerTest {

    // ── builders ──────────────────────────────────────────────

    private static Graph<String, Edge> empty(int n) {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        for (int i = 0; i < n; i++) g.addVertex("v" + i);
        return g;
    }

    private static Graph<String, Edge> complete(int n) {
        Graph<String, Edge> g = empty(n);
        int id = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                g.addEdge(new Edge("c", "v" + i, "v" + j), "v" + i, "v" + j);
                id++;
            }
        }
        return g;
    }

    private static Graph<String, Edge> path(int n) {
        Graph<String, Edge> g = empty(n);
        for (int i = 0; i < n - 1; i++) {
            g.addEdge(new Edge("p", "v" + i, "v" + (i + 1)), "v" + i, "v" + (i + 1));
        }
        return g;
    }

    private static Graph<String, Edge> cycle(int n) {
        Graph<String, Edge> g = path(n);
        if (n >= 3) {
            g.addEdge(new Edge("p", "v" + (n - 1), "v0"), "v" + (n - 1), "v0");
        }
        return g;
    }

    private static String key(String a, String b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    /**
     * Builds the set of unordered endpoint pairs for all edges in g.
     * Reads endpoints via the graph (not via Edge fields) so the test does
     * not depend on how callers populated the Edge's vertex1/vertex2 fields.
     */
    private static Set<String> edgeKeys(Graph<String, Edge> g) {
        Set<String> s = new HashSet<String>();
        for (Edge e : g.getEdges()) {
            Pair<String> ends = g.getEndpoints(e);
            s.add(key(ends.getFirst(), ends.getSecond()));
        }
        return s;
    }

    // ── tests ─────────────────────────────────────────────────

    @Test
    public void complementOfEmptyGraphIsEmpty() {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        Graph<String, Edge> c = GraphComplementAnalyzer.buildComplement(g);
        assertEquals(0, c.getVertexCount());
        assertEquals(0, c.getEdgeCount());
    }

    @Test
    public void complementOfSingleVertexHasNoEdges() {
        Graph<String, Edge> g = empty(1);
        Graph<String, Edge> c = GraphComplementAnalyzer.buildComplement(g);
        assertEquals(1, c.getVertexCount());
        assertEquals(0, c.getEdgeCount());
    }

    @Test
    public void complementOfCompleteGraphIsEdgeless() {
        Graph<String, Edge> kn = complete(5);
        Graph<String, Edge> c = GraphComplementAnalyzer.buildComplement(kn);
        assertEquals(5, c.getVertexCount());
        assertEquals(0, c.getEdgeCount());
        for (String v : c.getVertices()) {
            assertEquals(0, c.degree(v));
        }
    }

    @Test
    public void complementOfEdgelessGraphIsComplete() {
        Graph<String, Edge> g = empty(4);
        Graph<String, Edge> c = GraphComplementAnalyzer.buildComplement(g);
        // K4 has 4*3/2 = 6 edges
        assertEquals(6, c.getEdgeCount());
        // every vertex has degree n-1 = 3
        for (String v : c.getVertices()) {
            assertEquals(3, c.degree(v));
        }
    }

    @Test
    public void complementOfPathHasCorrectEdgeCount() {
        // P4: 0-1-2-3 → 3 edges
        // K4 has 6 edges → complement should have 3
        Graph<String, Edge> p = path(4);
        Graph<String, Edge> c = GraphComplementAnalyzer.buildComplement(p);
        assertEquals(3, c.getEdgeCount());

        // Specifically: edges NOT in path are {0-2, 0-3, 1-3}
        Set<String> compKeys = edgeKeys(c);
        assertTrue(compKeys.contains(key("v0", "v2")));
        assertTrue(compKeys.contains(key("v0", "v3")));
        assertTrue(compKeys.contains(key("v1", "v3")));
        assertFalse(compKeys.contains(key("v0", "v1")));
        assertFalse(compKeys.contains(key("v1", "v2")));
        assertFalse(compKeys.contains(key("v2", "v3")));
    }

    @Test
    public void edgeCountInvariantHolds() {
        // |E(G)| + |E(G')| = n*(n-1)/2 for any graph
        for (int n = 0; n <= 8; n++) {
            Graph<String, Edge> p = path(n);
            Graph<String, Edge> c = GraphComplementAnalyzer.buildComplement(p);
            int maxEdges = n * (n - 1) / 2;
            assertEquals("n=" + n, maxEdges, p.getEdgeCount() + c.getEdgeCount());
        }
    }

    @Test
    public void complementIsInvolutive() {
        // (G')' should equal G (same edge set)
        Graph<String, Edge> g = cycle(6); // C6
        Graph<String, Edge> cc = GraphComplementAnalyzer.buildComplement(
                GraphComplementAnalyzer.buildComplement(g));
        assertEquals(g.getVertexCount(), cc.getVertexCount());
        assertEquals(g.getEdgeCount(), cc.getEdgeCount());
        assertEquals(edgeKeys(g), edgeKeys(cc));
    }

    @Test
    public void complementDoesNotIncludeSelfLoops() {
        Graph<String, Edge> g = empty(4);
        Graph<String, Edge> c = GraphComplementAnalyzer.buildComplement(g);
        for (Edge e : c.getEdges()) {
            Pair<String> ends = c.getEndpoints(e);
            assertNotEquals("self-loop", ends.getFirst(), ends.getSecond());
        }
    }

    @Test
    public void complementEdgeListMatchesBuiltComplement() {
        Graph<String, Edge> p = path(5);
        Graph<String, Edge> c = GraphComplementAnalyzer.buildComplement(p);

        List<String[]> edgeList = GraphComplementAnalyzer.getComplementEdgeList(p);
        assertEquals(c.getEdgeCount(), edgeList.size());

        Set<String> listKeys = new HashSet<String>();
        for (String[] pair : edgeList) {
            assertEquals(2, pair.length);
            listKeys.add(key(pair[0], pair[1]));
        }
        // The complement edge list, taken pair-by-pair, must cover the same
        // unordered vertex pairs as the complement graph's edges.
        assertEquals(edgeKeys(c), listKeys);
        // Silence unused-import warning on Iterator when present.
        Iterator<Edge> ignored = c.getEdges().iterator();
        assertNotNull(ignored);
    }

    @Test
    public void analyzeReportContainsKeySections() {
        Graph<String, Edge> g = path(4);
        String report = GraphComplementAnalyzer.analyze(g);
        assertNotNull(report);
        assertTrue("title", report.contains("GRAPH COMPLEMENT ANALYSIS"));
        assertTrue("orig", report.contains("Original Graph"));
        assertTrue("compl", report.contains("Complement Graph"));
        assertTrue("validation", report.contains("Validation"));
        assertTrue("isolated", report.contains("Isolated Vertices"));
    }

    @Test
    public void analyzeFlagsSelfComplementaryCandidate() {
        // P4 has 3 edges, K4 has 6 edges → P4 has exactly half the edges, so
        // the edge-count test for self-complementarity should report PASS
        // (P4 is in fact self-complementary).
        Graph<String, Edge> p4 = path(4);
        String report = GraphComplementAnalyzer.analyze(p4);
        assertTrue("P4 should pass edge-count self-comp test",
                report.contains("PASS"));
    }

    @Test
    public void analyzeMarksNonSelfComplementaryGraph() {
        // P3 has 2 edges, K3 has 3 edges (odd) → cannot be self-complementary
        Graph<String, Edge> p3 = path(3);
        String report = GraphComplementAnalyzer.analyze(p3);
        // The "Edge-count test:" line should say FAIL
        assertTrue(report.contains("Edge-count test:     FAIL"));
    }

    @Test
    public void analyzeIdentifiesUniversalVerticesAsIsolatedInComplement() {
        // Star graph K_{1,3}: center connected to 3 leaves.
        // In the complement, the 3 leaves form a triangle and the center
        // becomes isolated.
        Graph<String, Edge> star = empty(4);
        star.addEdge(new Edge("s", "v0", "v1"), "v0", "v1");
        star.addEdge(new Edge("s", "v0", "v2"), "v0", "v2");
        star.addEdge(new Edge("s", "v0", "v3"), "v0", "v3");

        Graph<String, Edge> c = GraphComplementAnalyzer.buildComplement(star);
        assertEquals(0, c.degree("v0"));     // center is isolated in complement
        assertEquals(2, c.degree("v1"));
        assertEquals(2, c.degree("v2"));
        assertEquals(2, c.degree("v3"));
        assertEquals(3, c.getEdgeCount());   // triangle on leaves
    }

    @Test
    public void buildComplementProducesUniqueEdges() {
        // No duplicate edges in the complement (undirected, each pair once)
        Graph<String, Edge> g = empty(5);
        Graph<String, Edge> c = GraphComplementAnalyzer.buildComplement(g);
        Set<String> keys = edgeKeys(c);
        assertEquals(c.getEdgeCount(), keys.size());
    }
}
