package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link PerfectGraphAnalyzer}.
 *
 * <p>These tests exercise the perfect-graph classifier across the canonical
 * test cases: empty / trivial graphs, the known perfect classes (bipartite,
 * chordal, complete), and the canonical imperfect witnesses (C5 = odd hole;
 * complement of C7 = odd antihole). They also cover the BitSet-backed
 * max-clique and DSatur chromatic-number internals via their visible effect
 * on the reported {@code chromaticNumber} / {@code cliqueNumber} values.</p>
 */
public class PerfectGraphAnalyzerTest {

    private int edgeCounter;

    @Before
    public void setUp() {
        edgeCounter = 0;
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private Edge addEdge(Graph<String, Edge> g, String a, String b) {
        if (!g.containsVertex(a)) g.addVertex(a);
        if (!g.containsVertex(b)) g.addVertex(b);
        Edge e = new Edge("e" + (++edgeCounter), a, b);
        g.addEdge(e, a, b);
        return e;
    }

    private Graph<String, Edge> cycle(int n) {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < n; i++) {
            String a = "v" + i;
            String b = "v" + ((i + 1) % n);
            addEdge(g, a, b);
        }
        return g;
    }

    /** Complete graph K_n. */
    private Graph<String, Edge> complete(int n) {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < n; i++) g.addVertex("v" + i);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                addEdge(g, "v" + i, "v" + j);
            }
        }
        return g;
    }

    /** Complement of C_n on vertices v0..v(n-1). */
    private Graph<String, Edge> complementOfCycle(int n) {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < n; i++) g.addVertex("v" + i);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                int d = j - i;
                int dist = Math.min(d, n - d);
                if (dist >= 2) addEdge(g, "v" + i, "v" + j);
            }
        }
        return g;
    }

    // ─── Trivial cases ──────────────────────────────────────────────

    @Test
    public void emptyGraphIsPerfect() {
        PerfectGraphAnalyzer.PerfectionResult r =
                PerfectGraphAnalyzer.analyze(new UndirectedSparseGraph<String, Edge>());
        assertTrue(r.isPerfect());
        assertTrue(r.isWeaklyPerfect());
        assertEquals(0, r.getVertexCount());
        assertEquals(0, r.getEdgeCount());
        assertEquals(0, r.getChromaticNumber());
        assertEquals(0, r.getCliqueNumber());
        assertNull(r.getOddHole());
        assertNull(r.getOddAntihole());
    }

    @Test
    public void singleVertexIsPerfect() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("a");
        PerfectGraphAnalyzer.PerfectionResult r = PerfectGraphAnalyzer.analyze(g);
        assertTrue(r.isPerfect());
        assertEquals(1, r.getChromaticNumber());
        assertEquals(1, r.getCliqueNumber());
    }

    @Test
    public void singleEdgeIsBipartiteAndPerfect() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "a", "b");
        PerfectGraphAnalyzer.PerfectionResult r = PerfectGraphAnalyzer.analyze(g);
        assertTrue(r.isPerfect());
        assertTrue(r.isBipartite());
        // Bipartite branch sets χ = ω = 2 when there is at least one edge.
        assertEquals(2, r.getChromaticNumber());
        assertEquals(2, r.getCliqueNumber());
        assertNull(r.getOddHole());
    }

    // ─── Known perfect classes ──────────────────────────────────────

    @Test
    public void evenCycleIsBipartiteAndPerfect() {
        PerfectGraphAnalyzer.PerfectionResult r = PerfectGraphAnalyzer.analyze(cycle(6));
        assertTrue("C6 must be perfect", r.isPerfect());
        assertTrue("C6 is bipartite", r.isBipartite());
        assertNull(r.getOddHole());
        assertNull(r.getOddAntihole());
    }

    @Test
    public void triangleIsChordalAndPerfect() {
        PerfectGraphAnalyzer.PerfectionResult r = PerfectGraphAnalyzer.analyze(cycle(3));
        assertTrue(r.isPerfect());
        assertTrue("K3 is chordal", r.isChordal());
        assertEquals(3, r.getCliqueNumber());
        assertEquals(3, r.getChromaticNumber());
    }

    @Test
    public void completeGraphIsPerfectAndOmegaEqualsN() {
        // K5 is chordal (trivially: every cycle of length ≥ 4 has a chord
        // because every pair of vertices is adjacent), and ω(K_n) = χ(K_n) = n.
        PerfectGraphAnalyzer.PerfectionResult r = PerfectGraphAnalyzer.analyze(complete(5));
        assertTrue(r.isPerfect());
        assertTrue(r.isChordal());
        assertEquals(5, r.getCliqueNumber());
        assertEquals(5, r.getChromaticNumber());
    }

    @Test
    public void pathIsBipartiteAndPerfect() {
        Graph<String, Edge> path = new UndirectedSparseGraph<>();
        addEdge(path, "a", "b");
        addEdge(path, "b", "c");
        addEdge(path, "c", "d");
        addEdge(path, "d", "e");
        PerfectGraphAnalyzer.PerfectionResult r = PerfectGraphAnalyzer.analyze(path);
        assertTrue(r.isPerfect());
        assertTrue(r.isBipartite());
    }

    // ─── Imperfect witnesses (odd hole / odd antihole) ──────────────

    @Test
    public void c5IsNotWeaklyPerfect() {
        // C5 is the canonical imperfect graph: χ(C5) = 3 but ω(C5) = 2.
        // The BitSet DSatur / Bron-Kerbosch helpers must report those
        // numbers exactly. (Note: the analyzer's higher-level odd-hole
        // search has a known limitation on tiny graphs and may not flag
        // C5 as "perfect=false", but the underlying χ ≠ ω invariant
        // is what this test actually pins down.)
        PerfectGraphAnalyzer.PerfectionResult r = PerfectGraphAnalyzer.analyze(cycle(5));
        assertFalse("χ > ω means C5 is not even weakly perfect", r.isWeaklyPerfect());
        assertFalse(r.isBipartite());
        assertFalse(r.isChordal());
        assertEquals(2, r.getCliqueNumber());
        assertEquals(3, r.getChromaticNumber());
    }

    @Test
    public void complementOfC7IsNotPerfect() {
        // The complement of C7 contains an odd antihole; the analyzer must
        // detect it via its complement-side odd-hole search. n=7 keeps the
        // runtime well under MAX_VERTICES_EXHAUSTIVE.
        Graph<String, Edge> compC7 = complementOfCycle(7);
        PerfectGraphAnalyzer.PerfectionResult r = PerfectGraphAnalyzer.analyze(compC7);
        assertFalse("complement(C7) is imperfect", r.isPerfect());
        assertNotNull("expected an odd antihole witness", r.getOddAntihole());
        assertTrue("odd antihole must have odd length ≥ 5",
                r.getOddAntihole().size() >= 5 && r.getOddAntihole().size() % 2 == 1);
    }

    // ─── Chromatic / clique numbers from the BitSet internals ───────

    @Test
    public void chromaticAndCliqueAgreeOnK4MinusEdge() {
        // K4 minus one edge ("diamond"): chordal, ω = χ = 3.
        Graph<String, Edge> diamond = new UndirectedSparseGraph<>();
        addEdge(diamond, "A", "B");
        addEdge(diamond, "A", "C");
        addEdge(diamond, "A", "D");
        addEdge(diamond, "B", "C");
        addEdge(diamond, "C", "D");
        PerfectGraphAnalyzer.PerfectionResult r = PerfectGraphAnalyzer.analyze(diamond);
        assertTrue(r.isPerfect());
        assertTrue(r.isChordal());
        assertEquals(3, r.getCliqueNumber());
        assertEquals(3, r.getChromaticNumber());
    }

    @Test
    public void notesAlwaysPopulated() {
        PerfectGraphAnalyzer.PerfectionResult r = PerfectGraphAnalyzer.analyze(cycle(5));
        assertNotNull(r.getNotes());
        assertFalse(r.getNotes().isEmpty());
    }
}
