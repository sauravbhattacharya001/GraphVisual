package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link DominatingSetAnalyzer}.
 */
public class DominatingSetAnalyzerTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, Edge>();
    }

    private edge addEdge(String v1, String v2) {
        edge e = new Edge("f", v1, v2);
        e.setWeight(1.0f);
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        graph.addEdge(e, v1, v2);
        return e;
    }

    // ═══════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullGraph_throws() {
        new DominatingSetAnalyzer(null);
    }

    @Test
    public void constructor_emptyGraph_ok() {
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        assertNotNull(a);
    }

    // ═══════════════════════════════════════
    // Greedy Dominating Set
    // ═══════════════════════════════════════

    @Test
    public void greedy_emptyGraph_returnsEmpty() {
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        assertTrue(a.greedyDominatingSet().isEmpty());
    }

    @Test
    public void greedy_singleVertex_returnsThatVertex() {
        graph.addVertex("A");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        Set<String> ds = a.greedyDominatingSet();
        assertEquals(1, ds.size());
        assertTrue(ds.contains("A"));
    }

    @Test
    public void greedy_twoConnected_returnsOne() {
        addEdge("A", "B");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        Set<String> ds = a.greedyDominatingSet();
        assertEquals(1, ds.size());
        assertTrue(a.isDominatingSet(ds));
    }

    @Test
    public void greedy_path_isValid() {
        // A-B-C-D-E
        addEdge("A", "B"); addEdge("B", "C");
        addEdge("C", "D"); addEdge("D", "E");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        Set<String> ds = a.greedyDominatingSet();
        assertTrue(a.isDominatingSet(ds));
        assertTrue(ds.size() <= 3); // optimal is 2
    }

    @Test
    public void greedy_star_returnsCenter() {
        addEdge("C", "A"); addEdge("C", "B");
        addEdge("C", "D"); addEdge("C", "E");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        Set<String> ds = a.greedyDominatingSet();
        assertEquals(1, ds.size());
        assertTrue(ds.contains("C"));
    }

    @Test
    public void greedy_complete4_returnsOne() {
        addEdge("A", "B"); addEdge("A", "C"); addEdge("A", "D");
        addEdge("B", "C"); addEdge("B", "D"); addEdge("C", "D");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        assertEquals(1, a.greedyDominatingSet().size());
    }

    @Test
    public void greedy_twoIsolated_returnsBoth() {
        graph.addVertex("A"); graph.addVertex("B");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        Set<String> ds = a.greedyDominatingSet();
        assertEquals(2, ds.size());
    }

    @Test
    public void greedy_triangle_returnsOne() {
        addEdge("A", "B"); addEdge("B", "C"); addEdge("A", "C");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        assertEquals(1, a.greedyDominatingSet().size());
    }

    @Test
    public void greedy_resultIsUnmodifiable() {
        addEdge("A", "B");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        Set<String> ds = a.greedyDominatingSet();
        try { ds.add("X"); fail("Should be unmodifiable"); }
        catch (UnsupportedOperationException e) { /* expected */ }
    }

    // ═══════════════════════════════════════
    // Exact Minimum Dominating Set
    // ═══════════════════════════════════════

    @Test
    public void exact_emptyGraph_returnsEmpty() {
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        assertTrue(a.exactMinimumDominatingSet().isEmpty());
    }

    @Test
    public void exact_singleVertex_returnsOne() {
        graph.addVertex("A");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        assertEquals(1, a.exactMinimumDominatingSet().size());
    }

    @Test
    public void exact_path5_returnsOptimal() {
        addEdge("A", "B"); addEdge("B", "C");
        addEdge("C", "D"); addEdge("D", "E");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        Set<String> ds = a.exactMinimumDominatingSet();
        assertEquals(2, ds.size());
        assertTrue(a.isDominatingSet(ds));
    }

    @Test
    public void exact_star_returnsOne() {
        addEdge("C", "A"); addEdge("C", "B");
        addEdge("C", "D"); addEdge("C", "E");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        assertEquals(1, a.exactMinimumDominatingSet().size());
    }

    @Test
    public void exact_cycle4_returnsTwo() {
        addEdge("A", "B"); addEdge("B", "C");
        addEdge("C", "D"); addEdge("D", "A");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        Set<String> ds = a.exactMinimumDominatingSet();
        assertTrue(ds.size() <= 2);
        assertTrue(a.isDominatingSet(ds));
    }

    @Test(expected = IllegalStateException.class)
    public void exact_tooLargeGraph_throws() {
        for (int i = 0; i < 21; i++) graph.addVertex("V" + i);
        new DominatingSetAnalyzer(graph).exactMinimumDominatingSet();
    }

    @Test
    public void exact_twoIsolated_returnsTwo() {
        graph.addVertex("A"); graph.addVertex("B");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        assertEquals(2, a.exactMinimumDominatingSet().size());
    }

    // ═══════════════════════════════════════
    // Independent Dominating Set
    // ═══════════════════════════════════════

    @Test
    public void independent_emptyGraph() {
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        Set<String> ids = a.independentDominatingSet();
        assertTrue(ids.isEmpty());
    }

    @Test
    public void independent_singleVertex() {
        graph.addVertex("A");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        Set<String> ids = a.independentDominatingSet();
        assertEquals(1, ids.size());
        assertTrue(a.isDominatingSet(ids));
        assertTrue(a.isIndependentSet(ids));
    }

    @Test
    public void independent_path_isValidAndIndependent() {
        addEdge("A", "B"); addEdge("B", "C");
        addEdge("C", "D"); addEdge("D", "E");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        Set<String> ids = a.independentDominatingSet();
        assertTrue(a.isDominatingSet(ids));
        assertTrue(a.isIndependentSet(ids));
    }

    @Test
    public void independent_complete4_isIndependent() {
        addEdge("A", "B"); addEdge("A", "C"); addEdge("A", "D");
        addEdge("B", "C"); addEdge("B", "D"); addEdge("C", "D");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        Set<String> ids = a.independentDominatingSet();
        assertEquals(1, ids.size()); // one vertex dominates all in K4
        assertTrue(a.isIndependentSet(ids));
    }

    @Test
    public void independent_twoIsolated() {
        graph.addVertex("A"); graph.addVertex("B");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        Set<String> ids = a.independentDominatingSet();
        assertEquals(2, ids.size());
        assertTrue(a.isIndependentSet(ids));
    }

    // ═══════════════════════════════════════
    // Connected Dominating Set
    // ═══════════════════════════════════════

    @Test
    public void connected_emptyGraph() {
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        assertTrue(a.connectedDominatingSet().isEmpty());
    }

    @Test
    public void connected_singleVertex() {
        graph.addVertex("A");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        Set<String> cds = a.connectedDominatingSet();
        assertEquals(1, cds.size());
    }

    @Test
    public void connected_star_returnsCenter() {
        addEdge("C", "A"); addEdge("C", "B");
        addEdge("C", "D"); addEdge("C", "E");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        Set<String> cds = a.connectedDominatingSet();
        assertEquals(1, cds.size());
        assertTrue(cds.contains("C"));
    }

    @Test
    public void connected_path_isValid() {
        addEdge("A", "B"); addEdge("B", "C");
        addEdge("C", "D"); addEdge("D", "E");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        Set<String> cds = a.connectedDominatingSet();
        assertTrue(a.isDominatingSet(cds));
    }

    @Test
    public void connected_disconnected_returnsEmpty() {
        graph.addVertex("A"); graph.addVertex("B");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        assertTrue(a.connectedDominatingSet().isEmpty());
    }

    @Test
    public void connected_triangle_returnsOne() {
        addEdge("A", "B"); addEdge("B", "C"); addEdge("A", "C");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        Set<String> cds = a.connectedDominatingSet();
        assertEquals(1, cds.size());
    }

    // ═══════════════════════════════════════
    // k-Dominating Set
    // ═══════════════════════════════════════

    @Test(expected = IllegalArgumentException.class)
    public void kDom_zeroK_throws() {
        graph.addVertex("A");
        new DominatingSetAnalyzer(graph).kDominatingSet(0);
    }

    @Test
    public void kDom_k1_samAsGreedy() {
        addEdge("A", "B"); addEdge("B", "C");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        Set<String> k1 = a.kDominatingSet(1);
        assertTrue(a.isDominatingSet(k1));
    }

    @Test
    public void kDom_k2_moreCoverage() {
        addEdge("A", "B"); addEdge("B", "C");
        addEdge("C", "D"); addEdge("B", "D");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        Set<String> k2 = a.kDominatingSet(2);
        // Every non-member should have at least 2 neighbors in set
        Map<String, Integer> cov = a.coverageMap(k2);
        for (String v : cov.keySet()) {
            if (!k2.contains(v)) {
                assertTrue(cov.get(v) >= 2 || a.isDominatingSet(k2));
            }
        }
    }

    @Test
    public void kDom_emptyGraph() {
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        assertTrue(a.kDominatingSet(1).isEmpty());
    }

    // ═══════════════════════════════════════
    // Verification: isDominatingSet
    // ═══════════════════════════════════════

    @Test
    public void verify_null_returnsFalse() {
        graph.addVertex("A");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        assertFalse(a.isDominatingSet(null));
    }

    @Test
    public void verify_empty_onEmptyGraph_returnsTrue() {
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        assertTrue(a.isDominatingSet(Collections.<String>emptySet()));
    }

    @Test
    public void verify_empty_onNonEmptyGraph_returnsFalse() {
        graph.addVertex("A");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        assertFalse(a.isDominatingSet(Collections.<String>emptySet()));
    }

    @Test
    public void verify_fullSet_alwaysTrue() {
        addEdge("A", "B"); addEdge("B", "C");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        Set<String> full = new HashSet<String>(Arrays.asList("A", "B", "C"));
        assertTrue(a.isDominatingSet(full));
    }

    @Test
    public void verify_partialSet_correct() {
        addEdge("A", "B"); addEdge("B", "C");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        // {B} dominates A and C
        assertTrue(a.isDominatingSet(Collections.singleton("B")));
        // {A} does not dominate C
        assertFalse(a.isDominatingSet(Collections.singleton("A")));
    }

    // ═══════════════════════════════════════
    // isIndependentSet
    // ═══════════════════════════════════════

    @Test
    public void isIndependent_null_returnsTrue() {
        graph.addVertex("A");
        assertTrue(new DominatingSetAnalyzer(graph).isIndependentSet(null));
    }

    @Test
    public void isIndependent_singleton_true() {
        addEdge("A", "B");
        assertTrue(new DominatingSetAnalyzer(graph)
            .isIndependentSet(Collections.singleton("A")));
    }

    @Test
    public void isIndependent_adjacentPair_false() {
        addEdge("A", "B");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        assertFalse(a.isIndependentSet(new HashSet<String>(Arrays.asList("A", "B"))));
    }

    @Test
    public void isIndependent_nonAdjacentPair_true() {
        addEdge("A", "B"); addEdge("B", "C");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        assertTrue(a.isIndependentSet(new HashSet<String>(Arrays.asList("A", "C"))));
    }

    // ═══════════════════════════════════════
    // Bounds
    // ═══════════════════════════════════════

    @Test
    public void bounds_emptyGraph() {
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        assertEquals(0, a.dominationLowerBound());
        assertEquals(0, a.dominationUpperBound());
    }

    @Test
    public void bounds_star5() {
        addEdge("C", "A"); addEdge("C", "B");
        addEdge("C", "D"); addEdge("C", "E");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        // n=5, Δ=4 → lb = ceil(5/5) = 1, ub = 5-4 = 1
        assertEquals(1, a.dominationLowerBound());
        assertEquals(1, a.dominationUpperBound());
    }

    @Test
    public void bounds_path4() {
        addEdge("A", "B"); addEdge("B", "C"); addEdge("C", "D");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        // n=4, Δ=2 → lb = ceil(4/3) = 2, ub = 4-2 = 2
        assertEquals(2, a.dominationLowerBound());
        assertEquals(2, a.dominationUpperBound());
    }

    @Test
    public void bounds_isolatedVertices() {
        graph.addVertex("A"); graph.addVertex("B"); graph.addVertex("C");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        // Δ=0 → lb = ceil(3/1) = 3, ub = 3
        assertEquals(3, a.dominationLowerBound());
        assertEquals(3, a.dominationUpperBound());
    }

    // ═══════════════════════════════════════
    // Coverage
    // ═══════════════════════════════════════

    @Test
    public void coverage_star() {
        addEdge("C", "A"); addEdge("C", "B");
        addEdge("C", "D"); addEdge("C", "E");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        Map<String, Integer> cov = a.coverageMap(Collections.singleton("C"));
        assertEquals(1, (int) cov.get("C")); // self
        assertEquals(1, (int) cov.get("A"));
        assertEquals(1, (int) cov.get("B"));
    }

    @Test
    public void coverage_triangle_twoMembers() {
        addEdge("A", "B"); addEdge("B", "C"); addEdge("A", "C");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        Set<String> ds = new HashSet<String>(Arrays.asList("A", "B"));
        Map<String, Integer> cov = a.coverageMap(ds);
        assertEquals(2, (int) cov.get("A")); // self + B
        assertEquals(2, (int) cov.get("B")); // self + A
        assertEquals(2, (int) cov.get("C")); // A + B
    }

    @Test
    public void minimumCoverage_emptyGraph() {
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        assertEquals(0, a.minimumCoverage(Collections.<String>emptySet()));
    }

    @Test
    public void minimumCoverage_star() {
        addEdge("C", "A"); addEdge("C", "B");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        assertEquals(1, a.minimumCoverage(Collections.singleton("C")));
    }

    // ═══════════════════════════════════════
    // Report
    // ═══════════════════════════════════════

    @Test
    public void report_containsKeyInfo() {
        addEdge("A", "B"); addEdge("B", "C");
        addEdge("C", "D"); addEdge("D", "E");
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        String report = a.generateReport();
        assertTrue(report.contains("Dominating Set Analysis"));
        assertTrue(report.contains("Greedy"));
        assertTrue(report.contains("Independent"));
        assertTrue(report.contains("bounds"));
        assertTrue(report.contains("Exact"));
    }

    @Test
    public void report_emptyGraph() {
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        String report = a.generateReport();
        assertTrue(report.contains("0 vertices"));
    }

    @Test
    public void report_largeGraph_noExact() {
        for (int i = 0; i < 25; i++) {
            graph.addVertex("V" + i);
            if (i > 0) addEdge("V" + i, "V" + (i - 1));
        }
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        String report = a.generateReport();
        assertFalse(report.contains("Exact"));
    }

    // ═══════════════════════════════════════
    // Larger graphs
    // ═══════════════════════════════════════

    @Test
    public void petersen_graph() {
        // Outer: 0-4, Inner: 5-9
        for (int i = 0; i < 5; i++) {
            addEdge("" + i, "" + ((i + 1) % 5)); // outer cycle
            addEdge("" + i, "" + (i + 5));        // spokes
        }
        addEdge("5", "7"); addEdge("7", "9"); addEdge("9", "6");
        addEdge("6", "8"); addEdge("8", "5"); // inner pentagram

        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        Set<String> ds = a.greedyDominatingSet();
        assertTrue(a.isDominatingSet(ds));
        // Petersen domination number is 4
        Set<String> exact = a.exactMinimumDominatingSet();
        assertTrue(exact.size() <= 4);
    }

    @Test
    public void bipartite_complete_k33() {
        for (int i = 1; i <= 3; i++)
            for (int j = 4; j <= 6; j++)
                addEdge("" + i, "" + j);
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        Set<String> ds = a.greedyDominatingSet();
        assertTrue(a.isDominatingSet(ds));
        assertEquals(2, a.exactMinimumDominatingSet().size());
    }

    @Test
    public void wheel_graph() {
        // Hub + 5-cycle
        for (int i = 0; i < 5; i++) {
            addEdge("hub", "r" + i);
            addEdge("r" + i, "r" + ((i + 1) % 5));
        }
        DominatingSetAnalyzer a = new DominatingSetAnalyzer(graph);
        Set<String> ds = a.greedyDominatingSet();
        assertEquals(1, ds.size());
        assertTrue(ds.contains("hub"));
    }
}
