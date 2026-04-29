package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link DominatingSetAnalyzer} — greedy dominating set, exact
 * minimum dominating set, independent dominating set, connected dominating
 * set, k-domination, verification, bounds, coverage, and report generation.
 *
 * <p>Covers empty graphs, single vertices, complete graphs, paths, stars,
 * cycles, trees, disconnected graphs, and petersen-style topologies.</p>
 */
public class DominatingSetAnalyzerTest {

    // ── Helpers ─────────────────────────────────────────────────────

    private static void addEdge(Graph<String, Edge> g, String v1, String v2) {
        g.addVertex(v1);
        g.addVertex(v2);
        Edge e = new Edge("f", v1, v2);
        g.addEdge(e, v1, v2);
    }

    /** Empty graph with no vertices. */
    private static Graph<String, Edge> emptyGraph() {
        return new UndirectedSparseGraph<String, Edge>();
    }

    /** Single isolated vertex. */
    private static Graph<String, Edge> singleVertex() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        return g;
    }

    /** Two isolated vertices (no edges). */
    private static Graph<String, Edge> twoIsolated() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        g.addVertex("B");
        return g;
    }

    /** Simple edge: A-B. */
    private static Graph<String, Edge> singleEdge() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        return g;
    }

    /** Path graph: A-B-C-D-E. */
    private static Graph<String, Edge> pathGraph() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        addEdge(g, "D", "E");
        return g;
    }

    /** Star graph: center=H, spokes to A,B,C,D,E. */
    private static Graph<String, Edge> starGraph() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (String s : new String[]{"A", "B", "C", "D", "E"}) {
            addEdge(g, "H", s);
        }
        return g;
    }

    /** Complete graph K5: A,B,C,D,E all pairwise connected. */
    private static Graph<String, Edge> completeK5() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        String[] vs = {"A", "B", "C", "D", "E"};
        for (int i = 0; i < vs.length; i++) {
            for (int j = i + 1; j < vs.length; j++) {
                addEdge(g, vs[i], vs[j]);
            }
        }
        return g;
    }

    /** Cycle C6: A-B-C-D-E-F-A. */
    private static Graph<String, Edge> cycleC6() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        String[] vs = {"A", "B", "C", "D", "E", "F"};
        for (int i = 0; i < vs.length; i++) {
            addEdge(g, vs[i], vs[(i + 1) % vs.length]);
        }
        return g;
    }

    /** Triangle: A-B-C-A. */
    private static Graph<String, Edge> triangle() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        return g;
    }

    /** Binary tree:      R
     *                   / \
     *                  L   R1
     *                 / \
     *                LL  LR
     */
    private static Graph<String, Edge> binaryTree() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "R", "L");
        addEdge(g, "R", "R1");
        addEdge(g, "L", "LL");
        addEdge(g, "L", "LR");
        return g;
    }

    /** Disconnected graph: A-B-C and D-E (two components). */
    private static Graph<String, Edge> disconnected() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "D", "E");
        return g;
    }

    /** Verifies a set is a valid dominating set by checking all vertices. */
    private static void assertDominates(Graph<String, Edge> g,
            Map<String, Set<String>> adj, Set<String> ds) {
        for (String v : g.getVertices()) {
            if (ds.contains(v)) continue;
            boolean covered = false;
            for (String d : ds) {
                if (adj.containsKey(d) && adj.get(d).contains(v)) {
                    covered = true;
                    break;
                }
            }
            assertTrue("Vertex " + v + " is not dominated", covered);
        }
    }

    // ── Constructor ─────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullGraph_throws() {
        new DominatingSetAnalyzer(null);
    }

    // ── Greedy dominating set ───────────────────────────────────────

    @Test
    public void greedy_emptyGraph_returnsEmpty() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(emptyGraph());
        assertTrue(dsa.greedyDominatingSet().isEmpty());
    }

    @Test
    public void greedy_singleVertex_returnsThatVertex() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(singleVertex());
        Set<String> ds = dsa.greedyDominatingSet();
        assertEquals(1, ds.size());
        assertTrue(ds.contains("A"));
    }

    @Test
    public void greedy_singleEdge_returnsOneVertex() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(singleEdge());
        Set<String> ds = dsa.greedyDominatingSet();
        assertEquals(1, ds.size());
        assertTrue(ds.contains("A") || ds.contains("B"));
    }

    @Test
    public void greedy_twoIsolated_returnsBoth() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(twoIsolated());
        Set<String> ds = dsa.greedyDominatingSet();
        assertEquals(2, ds.size());
        assertTrue(ds.contains("A"));
        assertTrue(ds.contains("B"));
    }

    @Test
    public void greedy_starGraph_returnsCenter() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(starGraph());
        Set<String> ds = dsa.greedyDominatingSet();
        assertEquals(1, ds.size());
        assertTrue(ds.contains("H"));
    }

    @Test
    public void greedy_completeK5_returnsOneVertex() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(completeK5());
        Set<String> ds = dsa.greedyDominatingSet();
        assertEquals(1, ds.size());
    }

    @Test
    public void greedy_pathGraph_isValid() {
        Graph<String, Edge> g = pathGraph();
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(g);
        Set<String> ds = dsa.greedyDominatingSet();
        assertTrue(dsa.isDominatingSet(ds));
        // Optimal for P5 is 2 (e.g. B,D)
        assertTrue("Greedy should find at most 3 for P5", ds.size() <= 3);
    }

    @Test
    public void greedy_cycleC6_isValid() {
        Graph<String, Edge> g = cycleC6();
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(g);
        Set<String> ds = dsa.greedyDominatingSet();
        assertTrue(dsa.isDominatingSet(ds));
        // Optimal for C6 is 2
        assertTrue("Greedy should find at most 3 for C6", ds.size() <= 3);
    }

    @Test
    public void greedy_resultIsUnmodifiable() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(starGraph());
        Set<String> ds = dsa.greedyDominatingSet();
        try {
            ds.add("Z");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    // ── Exact minimum dominating set ────────────────────────────────

    @Test
    public void exact_emptyGraph_returnsEmpty() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(emptyGraph());
        assertTrue(dsa.exactMinimumDominatingSet().isEmpty());
    }

    @Test
    public void exact_singleVertex_returnsThatVertex() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(singleVertex());
        Set<String> ds = dsa.exactMinimumDominatingSet();
        assertEquals(1, ds.size());
        assertTrue(ds.contains("A"));
    }

    @Test
    public void exact_starGraph_returnsCenter() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(starGraph());
        Set<String> ds = dsa.exactMinimumDominatingSet();
        assertEquals(1, ds.size());
        assertTrue(ds.contains("H"));
    }

    @Test
    public void exact_completeK5_returnsSingleVertex() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(completeK5());
        Set<String> ds = dsa.exactMinimumDominatingSet();
        assertEquals(1, ds.size());
    }

    @Test
    public void exact_pathGraph_returnsOptimal() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(pathGraph());
        Set<String> ds = dsa.exactMinimumDominatingSet();
        assertEquals(2, ds.size());
        assertTrue(dsa.isDominatingSet(ds));
    }

    @Test
    public void exact_cycleC6_returnsOptimal() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(cycleC6());
        Set<String> ds = dsa.exactMinimumDominatingSet();
        assertEquals(2, ds.size());
        assertTrue(dsa.isDominatingSet(ds));
    }

    @Test
    public void exact_triangle_returnsOne() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(triangle());
        Set<String> ds = dsa.exactMinimumDominatingSet();
        assertEquals(1, ds.size());
        assertTrue(dsa.isDominatingSet(ds));
    }

    @Test
    public void exact_twoIsolated_returnsBoth() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(twoIsolated());
        Set<String> ds = dsa.exactMinimumDominatingSet();
        assertEquals(2, ds.size());
    }

    @Test
    public void exact_binaryTree_returnsOptimal() {
        // Binary tree with 5 nodes: {R, L} dominate all (R covers R1, L covers LL, LR)
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(binaryTree());
        Set<String> ds = dsa.exactMinimumDominatingSet();
        assertEquals(2, ds.size());
        assertTrue(dsa.isDominatingSet(ds));
    }

    @Test(expected = IllegalStateException.class)
    public void exact_tooManyVertices_throws() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < 21; i++) g.addVertex("V" + i);
        new DominatingSetAnalyzer(g).exactMinimumDominatingSet();
    }

    @Test
    public void exact_at20Vertices_succeeds() {
        // Create a path of 20 vertices — should not throw
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < 20; i++) g.addVertex("V" + i);
        for (int i = 0; i < 19; i++) addEdge(g, "V" + i, "V" + (i + 1));
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(g);
        Set<String> ds = dsa.exactMinimumDominatingSet();
        assertTrue(dsa.isDominatingSet(ds));
        // P20 domination number is ceil(20/3) = 7
        assertEquals(7, ds.size());
    }

    // ── Independent dominating set ──────────────────────────────────

    @Test
    public void independent_emptyGraph() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(emptyGraph());
        Set<String> ids = dsa.independentDominatingSet();
        assertTrue(ids.isEmpty());
    }

    @Test
    public void independent_singleVertex() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(singleVertex());
        Set<String> ids = dsa.independentDominatingSet();
        assertEquals(1, ids.size());
    }

    @Test
    public void independent_completeK5_returnsOne() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(completeK5());
        Set<String> ids = dsa.independentDominatingSet();
        // In K5, any single vertex dominates all — and a single vertex is trivially independent
        assertEquals(1, ids.size());
    }

    @Test
    public void independent_pathGraph_isIndependentAndDominating() {
        Graph<String, Edge> g = pathGraph();
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(g);
        Set<String> ids = dsa.independentDominatingSet();
        assertTrue("Must be a dominating set", dsa.isDominatingSet(ids));
        assertTrue("Must be an independent set", dsa.isIndependentSet(ids));
    }

    @Test
    public void independent_cycleC6_isIndependentAndDominating() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(cycleC6());
        Set<String> ids = dsa.independentDominatingSet();
        assertTrue(dsa.isDominatingSet(ids));
        assertTrue(dsa.isIndependentSet(ids));
    }

    @Test
    public void independent_twoIsolated_returnsBoth() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(twoIsolated());
        Set<String> ids = dsa.independentDominatingSet();
        assertEquals(2, ids.size());
        assertTrue(dsa.isIndependentSet(ids));
    }

    // ── Connected dominating set ────────────────────────────────────

    @Test
    public void connected_emptyGraph_returnsEmpty() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(emptyGraph());
        assertTrue(dsa.connectedDominatingSet().isEmpty());
    }

    @Test
    public void connected_singleVertex_returnsThatVertex() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(singleVertex());
        Set<String> cds = dsa.connectedDominatingSet();
        assertEquals(1, cds.size());
    }

    @Test
    public void connected_starGraph_returnsCenter() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(starGraph());
        Set<String> cds = dsa.connectedDominatingSet();
        assertEquals(1, cds.size());
        assertTrue(cds.contains("H"));
    }

    @Test
    public void connected_completeK5_returnsOne() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(completeK5());
        Set<String> cds = dsa.connectedDominatingSet();
        assertEquals(1, cds.size());
    }

    @Test
    public void connected_pathGraph_isValidAndConnected() {
        Graph<String, Edge> g = pathGraph();
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(g);
        Set<String> cds = dsa.connectedDominatingSet();
        assertTrue(dsa.isDominatingSet(cds));
        // CDS should form a connected subgraph — verify path connectivity
        // in the induced subgraph via BFS
        assertConnected(g, cds);
    }

    @Test
    public void connected_disconnectedGraph_returnsEmpty() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(disconnected());
        Set<String> cds = dsa.connectedDominatingSet();
        // Can't form a connected dominating set for a disconnected graph
        assertTrue(cds.isEmpty());
    }

    /** BFS connectivity check for the induced subgraph of {@code subset}. */
    private static void assertConnected(Graph<String, Edge> g, Set<String> subset) {
        if (subset.size() <= 1) return;
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        String start = subset.iterator().next();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            String v = queue.poll();
            for (String n : g.getNeighbors(v)) {
                if (subset.contains(n) && visited.add(n)) {
                    queue.add(n);
                }
            }
        }
        assertEquals("CDS induced subgraph must be connected",
                subset.size(), visited.size());
    }

    // ── k-domination ────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void kDominating_kZero_throws() {
        new DominatingSetAnalyzer(starGraph()).kDominatingSet(0);
    }

    @Test
    public void kDominating_emptyGraph_returnsEmpty() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(emptyGraph());
        assertTrue(dsa.kDominatingSet(1).isEmpty());
    }

    @Test
    public void kDominating_k1_isStandardDomination() {
        Graph<String, Edge> g = pathGraph();
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(g);
        Set<String> k1 = dsa.kDominatingSet(1);
        assertTrue(dsa.isDominatingSet(k1));
    }

    @Test
    public void kDominating_k2_pathGraph() {
        Graph<String, Edge> g = pathGraph();
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(g);
        Set<String> k2 = dsa.kDominatingSet(2);
        // Every non-member needs at least 2 adjacent members
        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(g);
        for (String v : g.getVertices()) {
            if (k2.contains(v)) continue;
            int count = 0;
            for (String m : k2) {
                if (adj.get(v).contains(m)) count++;
            }
            assertTrue("Vertex " + v + " has only " + count + " dominators, need 2",
                    count >= 2);
        }
    }

    @Test
    public void kDominating_k1_completeGraph_returnsSingle() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(completeK5());
        Set<String> k1 = dsa.kDominatingSet(1);
        assertEquals(1, k1.size());
    }

    @Test
    public void kDominating_k4_completeK5() {
        // In K5, each non-member has 4 neighbors. k=4 means every non-member
        // needs 4 adjacent members, so we need at least 4 members.
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(completeK5());
        Set<String> k4 = dsa.kDominatingSet(4);
        assertTrue(k4.size() >= 4);
    }

    // ── Verification ────────────────────────────────────────────────

    @Test
    public void isDominating_null_returnsFalse() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(starGraph());
        assertFalse(dsa.isDominatingSet(null));
    }

    @Test
    public void isDominating_emptySetOnNonEmptyGraph_returnsFalse() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(starGraph());
        assertFalse(dsa.isDominatingSet(Collections.emptySet()));
    }

    @Test
    public void isDominating_fullVertexSet_returnsTrue() {
        Graph<String, Edge> g = pathGraph();
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(g);
        assertTrue(dsa.isDominatingSet(new HashSet<>(g.getVertices())));
    }

    @Test
    public void isDominating_singleCenterOfStar_returnsTrue() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(starGraph());
        assertTrue(dsa.isDominatingSet(Collections.singleton("H")));
    }

    @Test
    public void isDominating_leafOfStar_returnsFalse() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(starGraph());
        // A single leaf doesn't dominate the other leaves
        assertFalse(dsa.isDominatingSet(Collections.singleton("A")));
    }

    // ── Independent set verification ────────────────────────────────

    @Test
    public void isIndependent_null_returnsTrue() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(starGraph());
        assertTrue(dsa.isIndependentSet(null));
    }

    @Test
    public void isIndependent_singleVertex_returnsTrue() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(starGraph());
        assertTrue(dsa.isIndependentSet(Collections.singleton("A")));
    }

    @Test
    public void isIndependent_adjacentPair_returnsFalse() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(singleEdge());
        assertFalse(dsa.isIndependentSet(new HashSet<>(Arrays.asList("A", "B"))));
    }

    @Test
    public void isIndependent_nonAdjacentPair_returnsTrue() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(pathGraph());
        // A and C are not adjacent in A-B-C-D-E
        assertTrue(dsa.isIndependentSet(new HashSet<>(Arrays.asList("A", "C"))));
    }

    // ── Bounds ──────────────────────────────────────────────────────

    @Test
    public void bounds_emptyGraph() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(emptyGraph());
        assertEquals(0, dsa.dominationLowerBound());
        assertEquals(0, dsa.dominationUpperBound());
    }

    @Test
    public void bounds_singleVertex() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(singleVertex());
        assertEquals(1, dsa.dominationLowerBound());
        assertEquals(1, dsa.dominationUpperBound());
    }

    @Test
    public void bounds_starGraph() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(starGraph());
        // n=6, Δ=5: LB=ceil(6/6)=1, UB=6-5=1
        assertEquals(1, dsa.dominationLowerBound());
        assertEquals(1, dsa.dominationUpperBound());
    }

    @Test
    public void bounds_pathGraph() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(pathGraph());
        // n=5, Δ=2: LB=ceil(5/3)=2, UB=5-2=3
        assertEquals(2, dsa.dominationLowerBound());
        assertEquals(3, dsa.dominationUpperBound());
    }

    @Test
    public void bounds_bracketExactDominationNumber() {
        // For all small graphs: LB <= exact domination number <= UB
        for (Graph<String, Edge> g : Arrays.asList(
                singleVertex(), singleEdge(), triangle(), pathGraph(),
                starGraph(), completeK5(), cycleC6(), binaryTree())) {
            if (g.getVertexCount() == 0) continue;
            DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(g);
            int exact = dsa.exactMinimumDominatingSet().size();
            assertTrue("LB <= exact for graph with " + g.getVertexCount() + " vertices",
                    dsa.dominationLowerBound() <= exact);
            assertTrue("exact <= UB for graph with " + g.getVertexCount() + " vertices",
                    exact <= dsa.dominationUpperBound());
        }
    }

    // ── Coverage map ────────────────────────────────────────────────

    @Test
    public void coverage_starCenter_allCoveredOnce() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(starGraph());
        Map<String, Integer> cov = dsa.coverageMap(Collections.singleton("H"));
        // H itself: covered 1 (self). Each leaf: covered 1 (by H).
        for (int c : cov.values()) {
            assertEquals(1, c);
        }
    }

    @Test
    public void coverage_completeK5_allVertices_highCoverage() {
        Graph<String, Edge> g = completeK5();
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(g);
        Set<String> all = new HashSet<>(g.getVertices());
        Map<String, Integer> cov = dsa.coverageMap(all);
        // Each vertex is covered by itself + 4 neighbors = 5
        for (int c : cov.values()) {
            assertEquals(5, c);
        }
    }

    @Test
    public void minimumCoverage_starCenter() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(starGraph());
        assertEquals(1, dsa.minimumCoverage(Collections.singleton("H")));
    }

    @Test
    public void minimumCoverage_emptyDominatingSet() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(starGraph());
        assertEquals(0, dsa.minimumCoverage(Collections.emptySet()));
    }

    // ── Report ──────────────────────────────────────────────────────

    @Test
    public void report_containsKeyInfo() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(pathGraph());
        String report = dsa.generateReport();
        assertTrue(report.contains("Dominating Set Analysis"));
        assertTrue(report.contains("5 vertices"));
        assertTrue(report.contains("4 edges"));
        assertTrue(report.contains("Greedy dominating set"));
        assertTrue(report.contains("Independent dominating set"));
        assertTrue(report.contains("Exact minimum dominating set"));
        assertTrue(report.contains("Domination number bounds"));
    }

    @Test
    public void report_emptyGraph_noErrors() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(emptyGraph());
        String report = dsa.generateReport();
        assertNotNull(report);
        assertTrue(report.contains("0 vertices"));
    }

    @Test
    public void report_disconnected_showsNoConnectedSet() {
        DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(disconnected());
        String report = dsa.generateReport();
        assertTrue(report.contains("N/A") || report.contains("disconnected"));
    }

    // ── Greedy vs Exact consistency ─────────────────────────────────

    @Test
    public void greedy_neverSmallerThanExact() {
        // Greedy is a heuristic — it should find a set >= exact size
        for (Graph<String, Edge> g : Arrays.asList(
                singleVertex(), singleEdge(), triangle(), pathGraph(),
                starGraph(), completeK5(), cycleC6(), binaryTree())) {
            if (g.getVertexCount() == 0) continue;
            DominatingSetAnalyzer dsa = new DominatingSetAnalyzer(g);
            int exactSize = dsa.exactMinimumDominatingSet().size();
            int greedySize = dsa.greedyDominatingSet().size();
            assertTrue("Greedy (" + greedySize + ") >= exact (" + exactSize + ")",
                    greedySize >= exactSize);
        }
    }
}
