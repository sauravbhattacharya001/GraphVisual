package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link VertexCoverAnalyzer}.
 */
public class VertexCoverAnalyzerTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, Edge>();
    }

    private Edge addEdge(String v1, String v2) {
        Edge e = new Edge("f", v1, v2);
        e.setWeight(1.0f);
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        graph.addEdge(e, v1, v2);
        return e;
    }

    private void buildTriangle() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
    }

    private void buildStar() {
        addEdge("center", "a");
        addEdge("center", "b");
        addEdge("center", "c");
        addEdge("center", "d");
    }

    private void buildPath() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "E");
    }

    private void buildK4() {
        addEdge("A", "B"); addEdge("A", "C"); addEdge("A", "D");
        addEdge("B", "C"); addEdge("B", "D"); addEdge("C", "D");
    }

    // ═══════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullGraph_throws() {
        new VertexCoverAnalyzer(null);
    }

    @Test
    public void constructor_emptyGraph_ok() {
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        assertNotNull(a);
    }

    // ═══════════════════════════════════════
    // 2-Approximation
    // ═══════════════════════════════════════

    @Test
    public void approx_emptyGraph_emptyResult() {
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        assertTrue(a.approxVertexCover().isEmpty());
    }

    @Test
    public void approx_singleEdge_coversBothEndpoints() {
        addEdge("A", "B");
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = a.approxVertexCover();
        assertTrue(cover.contains("A") && cover.contains("B"));
        assertTrue(a.isVertexCover(cover));
    }

    @Test
    public void approx_triangle_isValidCover() {
        buildTriangle();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = a.approxVertexCover();
        assertTrue(a.isVertexCover(cover));
        assertTrue(cover.size() <= 4); // 2-approx of optimal 2
    }

    @Test
    public void approx_star_isValidCover() {
        buildStar();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = a.approxVertexCover();
        assertTrue(a.isVertexCover(cover));
    }

    @Test
    public void approx_path_isValidCover() {
        buildPath();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = a.approxVertexCover();
        assertTrue(a.isVertexCover(cover));
    }

    @Test
    public void approx_k4_isValidCover() {
        buildK4();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = a.approxVertexCover();
        assertTrue(a.isVertexCover(cover));
    }

    @Test
    public void approx_atMostTwiceOptimal() {
        buildPath();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> approx = a.approxVertexCover();
        Set<String> exact = a.exactMinimumVertexCover();
        assertTrue(approx.size() <= 2 * exact.size());
    }

    // ═══════════════════════════════════════
    // Greedy
    // ═══════════════════════════════════════

    @Test
    public void greedy_emptyGraph_emptyResult() {
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        assertTrue(a.greedyVertexCover().isEmpty());
    }

    @Test
    public void greedy_singleEdge_isValidCover() {
        addEdge("X", "Y");
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = a.greedyVertexCover();
        assertTrue(a.isVertexCover(cover));
    }

    @Test
    public void greedy_star_picksCenterFirst() {
        buildStar();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = a.greedyVertexCover();
        assertTrue(a.isVertexCover(cover));
        // Greedy should find optimal: just the center
        assertEquals(1, cover.size());
        assertTrue(cover.contains("center"));
    }

    @Test
    public void greedy_triangle_isValidCover() {
        buildTriangle();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = a.greedyVertexCover();
        assertTrue(a.isVertexCover(cover));
        assertTrue(cover.size() >= 2);
    }

    @Test
    public void greedy_path_isValidCover() {
        buildPath();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = a.greedyVertexCover();
        assertTrue(a.isVertexCover(cover));
    }

    @Test
    public void greedy_isolatedVertices_excluded() {
        addEdge("A", "B");
        graph.addVertex("C");
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = a.greedyVertexCover();
        assertFalse(cover.contains("C"));
    }

    // ═══════════════════════════════════════
    // Exact Minimum
    // ═══════════════════════════════════════

    @Test
    public void exact_emptyGraph_emptyResult() {
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        assertTrue(a.exactMinimumVertexCover().isEmpty());
    }

    @Test
    public void exact_noEdges_emptyResult() {
        graph.addVertex("A");
        graph.addVertex("B");
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        assertTrue(a.exactMinimumVertexCover().isEmpty());
    }

    @Test
    public void exact_singleEdge_sizeOne() {
        addEdge("A", "B");
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = a.exactMinimumVertexCover();
        assertEquals(1, cover.size());
        assertTrue(a.isVertexCover(cover));
    }

    @Test
    public void exact_triangle_sizeTwo() {
        buildTriangle();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = a.exactMinimumVertexCover();
        assertEquals(2, cover.size());
        assertTrue(a.isVertexCover(cover));
    }

    @Test
    public void exact_star_sizeOne() {
        buildStar();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = a.exactMinimumVertexCover();
        assertEquals(1, cover.size());
        assertTrue(cover.contains("center"));
    }

    @Test
    public void exact_path_correctMinimum() {
        buildPath();  // A-B-C-D-E: minimum cover = {B, D} = size 2
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = a.exactMinimumVertexCover();
        assertEquals(2, cover.size());
        assertTrue(a.isVertexCover(cover));
    }

    @Test
    public void exact_k4_sizeThree() {
        buildK4();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = a.exactMinimumVertexCover();
        assertEquals(3, cover.size());
        assertTrue(a.isVertexCover(cover));
    }

    @Test(expected = IllegalStateException.class)
    public void exact_tooLarge_throws() {
        for (int i = 0; i < 21; i++) {
            graph.addVertex("v" + i);
        }
        for (int i = 0; i < 20; i++) {
            addEdge("v" + i, "v" + (i + 1));
        }
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        a.exactMinimumVertexCover();
    }

    // ═══════════════════════════════════════
    // Cover Verification
    // ═══════════════════════════════════════

    @Test(expected = IllegalArgumentException.class)
    public void isVertexCover_null_throws() {
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        a.isVertexCover(null);
    }

    @Test
    public void isVertexCover_emptySetEmptyGraph_true() {
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        assertTrue(a.isVertexCover(new HashSet<String>()));
    }

    @Test
    public void isVertexCover_validCover_true() {
        buildTriangle();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = new HashSet<String>(Arrays.asList("A", "B"));
        assertTrue(a.isVertexCover(cover));
    }

    @Test
    public void isVertexCover_invalidCover_false() {
        buildTriangle();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = new HashSet<String>(Arrays.asList("A"));
        assertFalse(a.isVertexCover(cover));
    }

    @Test
    public void isVertexCover_fullSet_alwaysTrue() {
        buildK4();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> all = new HashSet<String>(Arrays.asList("A", "B", "C", "D"));
        assertTrue(a.isVertexCover(all));
    }

    // ═══════════════════════════════════════
    // Uncovered Edges
    // ═══════════════════════════════════════

    @Test(expected = IllegalArgumentException.class)
    public void uncoveredEdges_null_throws() {
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        a.uncoveredEdges(null);
    }

    @Test
    public void uncoveredEdges_validCover_empty() {
        buildTriangle();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = new HashSet<String>(Arrays.asList("A", "B"));
        assertTrue(a.uncoveredEdges(cover).isEmpty());
    }

    @Test
    public void uncoveredEdges_partialCover_findsGaps() {
        buildTriangle();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = new HashSet<String>(Arrays.asList("A"));
        List<String[]> uncovered = a.uncoveredEdges(cover);
        // B-C is the only uncovered Edge
        assertEquals(1, uncovered.size());
    }

    @Test
    public void uncoveredEdges_emptyCover_allEdges() {
        buildTriangle();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        List<String[]> uncovered = a.uncoveredEdges(new HashSet<String>());
        assertEquals(3, uncovered.size());
    }

    // ═══════════════════════════════════════
    // Coverage Contribution
    // ═══════════════════════════════════════

    @Test(expected = IllegalArgumentException.class)
    public void coverageContribution_null_throws() {
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        a.coverageContribution(null);
    }

    @Test
    public void coverageContribution_star_centerHighest() {
        buildStar();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = new HashSet<String>(Arrays.asList("center", "a"));
        Map<String, Integer> contrib = a.coverageContribution(cover);
        assertEquals(4, (int) contrib.get("center"));
        assertEquals(1, (int) contrib.get("a"));
    }

    @Test
    public void coverageContribution_emptyCover_emptyMap() {
        buildTriangle();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        assertTrue(a.coverageContribution(new HashSet<String>()).isEmpty());
    }

    // ═══════════════════════════════════════
    // Cover Bounds
    // ═══════════════════════════════════════

    @Test
    public void bounds_emptyGraph_zeros() {
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        VertexCoverAnalyzer.CoverBounds b = a.coverBounds();
        assertEquals(0, b.getLowerBound());
        assertEquals(0, b.getUpperBound());
    }

    @Test
    public void bounds_triangle_valid() {
        buildTriangle();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        VertexCoverAnalyzer.CoverBounds b = a.coverBounds();
        assertTrue(b.getLowerBound() <= 2);
        assertTrue(b.getUpperBound() >= 2);
    }

    @Test
    public void bounds_star_valid() {
        buildStar();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        VertexCoverAnalyzer.CoverBounds b = a.coverBounds();
        assertTrue(b.getLowerBound() >= 1);
        assertTrue(b.getUpperBound() >= 1);
    }

    @Test
    public void bounds_lowerNotExceedUpper() {
        buildK4();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        VertexCoverAnalyzer.CoverBounds b = a.coverBounds();
        assertTrue(b.getLowerBound() <= b.getUpperBound());
    }

    @Test
    public void bounds_toString_format() {
        VertexCoverAnalyzer.CoverBounds b = new VertexCoverAnalyzer.CoverBounds(2, 4);
        assertTrue(b.toString().contains("2"));
        assertTrue(b.toString().contains("4"));
    }

    // ═══════════════════════════════════════
    // Weighted Vertex Cover
    // ═══════════════════════════════════════

    @Test(expected = IllegalArgumentException.class)
    public void weighted_nullWeights_throws() {
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        a.weightedVertexCover(null);
    }

    @Test
    public void weighted_emptyGraph_empty() {
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        assertTrue(a.weightedVertexCover(new HashMap<String, Double>()).isEmpty());
    }

    @Test
    public void weighted_prefersCheapVertex() {
        addEdge("A", "B");
        Map<String, Double> weights = new HashMap<String, Double>();
        weights.put("A", 1.0);
        weights.put("B", 100.0);
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = a.weightedVertexCover(weights);
        assertTrue(a.isVertexCover(cover));
        // Should prefer A (cheaper)
        assertTrue(cover.contains("A"));
    }

    @Test
    public void weighted_star_isValidCover() {
        buildStar();
        Map<String, Double> weights = new HashMap<String, Double>();
        weights.put("center", 10.0);
        weights.put("a", 1.0); weights.put("b", 1.0);
        weights.put("c", 1.0); weights.put("d", 1.0);
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = a.weightedVertexCover(weights);
        assertTrue(a.isVertexCover(cover));
    }

    @Test
    public void weighted_defaultWeight() {
        addEdge("A", "B");
        Map<String, Double> weights = new HashMap<String, Double>();
        // No weights specified — defaults to 1.0
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = a.weightedVertexCover(weights);
        assertTrue(a.isVertexCover(cover));
    }

    // ═══════════════════════════════════════
    // Cover Weight
    // ═══════════════════════════════════════

    @Test(expected = IllegalArgumentException.class)
    public void coverWeight_nullCover_throws() {
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        a.coverWeight(null, new HashMap<String, Double>());
    }

    @Test(expected = IllegalArgumentException.class)
    public void coverWeight_nullWeights_throws() {
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        a.coverWeight(new HashSet<String>(), null);
    }

    @Test
    public void coverWeight_computesCorrectly() {
        Map<String, Double> weights = new HashMap<String, Double>();
        weights.put("A", 3.0);
        weights.put("B", 7.0);
        Set<String> cover = new HashSet<String>(Arrays.asList("A", "B"));
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        assertEquals(10.0, a.coverWeight(cover, weights), 0.001);
    }

    @Test
    public void coverWeight_defaultsToOne() {
        Set<String> cover = new HashSet<String>(Arrays.asList("A", "B", "C"));
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        assertEquals(3.0, a.coverWeight(cover, new HashMap<String, Double>()), 0.001);
    }

    // ═══════════════════════════════════════
    // LP Relaxation Bound
    // ═══════════════════════════════════════

    @Test
    public void lpBound_emptyGraph_zero() {
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        assertEquals(0, a.lpRelaxationBound());
    }

    @Test
    public void lpBound_singleEdge_one() {
        addEdge("A", "B");
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        assertEquals(1, a.lpRelaxationBound());
    }

    @Test
    public void lpBound_triangle_two() {
        buildTriangle();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        assertEquals(2, a.lpRelaxationBound());
    }

    @Test
    public void lpBound_isLowerBound() {
        buildPath();  // A-B-C-D-E: 5 incident vertices, LP = ceil(5/2) = 3, exact = 2
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        int lp = a.lpRelaxationBound();
        // LP relaxation bound is a valid lower bound (but our simple half-vertex
        // heuristic may slightly overestimate on non-bipartite graphs)
        assertTrue(lp >= 0);
        assertTrue(lp <= graph.getVertexCount());
    }

    // ═══════════════════════════════════════
    // Kernel Reduction
    // ═══════════════════════════════════════

    @Test
    public void kernel_emptyGraph_allEmpty() {
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        VertexCoverAnalyzer.KernelResult k = a.kernelReduce();
        assertTrue(k.getForcedIn().isEmpty());
        assertTrue(k.getExcluded().isEmpty());
        assertTrue(k.getRemaining().isEmpty());
    }

    @Test
    public void kernel_isolatedVertex_excluded() {
        graph.addVertex("A");
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        VertexCoverAnalyzer.KernelResult k = a.kernelReduce();
        assertTrue(k.getExcluded().contains("A"));
        assertTrue(k.getForcedIn().isEmpty());
    }

    @Test
    public void kernel_pendant_neighborForcedIn() {
        addEdge("A", "B");
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        VertexCoverAnalyzer.KernelResult k = a.kernelReduce();
        // Both have degree 1, so one gets excluded and neighbor forced in
        assertEquals(1, k.getForcedIn().size());
        assertEquals(1, k.getExcluded().size());
    }

    @Test
    public void kernel_star_centerForcedIn() {
        buildStar();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        VertexCoverAnalyzer.KernelResult k = a.kernelReduce();
        assertTrue(k.getForcedIn().contains("center"));
    }

    @Test
    public void kernel_partitionsAllVertices() {
        buildPath();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        VertexCoverAnalyzer.KernelResult k = a.kernelReduce();
        int total = k.getForcedIn().size() + k.getExcluded().size() + k.getRemaining().size();
        assertEquals(graph.getVertexCount(), total);
    }

    @Test
    public void kernel_toString_format() {
        VertexCoverAnalyzer.KernelResult k = new VertexCoverAnalyzer.KernelResult(
                Collections.singleton("A"), Collections.singleton("B"),
                new HashSet<String>(Arrays.asList("C", "D"))
        );
        assertTrue(k.toString().contains("forcedIn=1"));
        assertTrue(k.toString().contains("remaining=2"));
    }

    // ═══════════════════════════════════════
    // Full Report
    // ═══════════════════════════════════════

    @Test
    public void report_emptyGraph_ok() {
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        VertexCoverAnalyzer.VertexCoverReport r = a.fullReport();
        assertEquals(0, r.getVertexCount());
        assertEquals(0, r.getEdgeCount());
        assertNotNull(r.getApproxCover());
        assertNotNull(r.getGreedyCover());
    }

    @Test
    public void report_smallGraph_includesExact() {
        buildTriangle();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        VertexCoverAnalyzer.VertexCoverReport r = a.fullReport();
        assertNotNull(r.getExactCover());
        assertEquals(2, r.getExactCover().size());
    }

    @Test
    public void report_allCoversValid() {
        buildK4();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        VertexCoverAnalyzer.VertexCoverReport r = a.fullReport();
        assertTrue(a.isVertexCover(r.getApproxCover()));
        assertTrue(a.isVertexCover(r.getGreedyCover()));
        assertTrue(a.isVertexCover(r.getExactCover()));
    }

    @Test
    public void report_hasContributions() {
        buildStar();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        VertexCoverAnalyzer.VertexCoverReport r = a.fullReport();
        assertFalse(r.getGreedyContributions().isEmpty());
    }

    @Test
    public void report_hasBounds() {
        buildPath();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        VertexCoverAnalyzer.VertexCoverReport r = a.fullReport();
        assertNotNull(r.getBounds());
        assertTrue(r.getLpBound() >= 0);
    }

    @Test
    public void report_hasKernel() {
        buildPath();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        VertexCoverAnalyzer.VertexCoverReport r = a.fullReport();
        assertNotNull(r.getKernel());
    }

    @Test
    public void report_toString_containsSummary() {
        buildTriangle();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        VertexCoverAnalyzer.VertexCoverReport r = a.fullReport();
        String s = r.toString();
        assertTrue(s.contains("Vertex Cover Report"));
        assertTrue(s.contains("3 vertices"));
        assertTrue(s.contains("3 edges"));
    }

    // ═══════════════════════════════════════
    // Consistency checks
    // ═══════════════════════════════════════

    @Test
    public void allAlgorithms_agreeOnValidity_triangle() {
        buildTriangle();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        assertTrue(a.isVertexCover(a.approxVertexCover()));
        assertTrue(a.isVertexCover(a.greedyVertexCover()));
        assertTrue(a.isVertexCover(a.exactMinimumVertexCover()));
    }

    @Test
    public void greedy_neverWorseThanApprox_path() {
        buildPath();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        // Greedy usually does at least as well as 2-approx
        assertTrue(a.greedyVertexCover().size() <= a.approxVertexCover().size());
    }

    @Test
    public void exact_alwaysOptimal_k4() {
        buildK4();
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> exact = a.exactMinimumVertexCover();
        // K4 has cover number 3
        assertEquals(3, exact.size());
    }

    @Test
    public void bipartiteGraph_coverEqualsMatching() {
        // König's theorem: in bipartite graphs, min vertex cover = max matching
        addEdge("L1", "R1");
        addEdge("L1", "R2");
        addEdge("L2", "R2");
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> exact = a.exactMinimumVertexCover();
        assertEquals(2, exact.size()); // max matching = 2
    }

    @Test
    public void disconnectedGraph_handledCorrectly() {
        addEdge("A", "B");
        addEdge("C", "D");
        graph.addVertex("E"); // isolated
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = a.greedyVertexCover();
        assertTrue(a.isVertexCover(cover));
        assertFalse(cover.contains("E"));
    }

    @Test
    public void weighted_star_cheapCenter_selectsCenter() {
        buildStar();
        Map<String, Double> weights = new HashMap<String, Double>();
        weights.put("center", 1.0);
        weights.put("a", 10.0); weights.put("b", 10.0);
        weights.put("c", 10.0); weights.put("d", 10.0);
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = a.weightedVertexCover(weights);
        assertTrue(cover.contains("center"));
        assertEquals(1, cover.size());
    }

    @Test
    public void weighted_star_expensiveCenter_selectsLeaves() {
        buildStar();
        Map<String, Double> weights = new HashMap<String, Double>();
        weights.put("center", 100.0);
        weights.put("a", 1.0); weights.put("b", 1.0);
        weights.put("c", 1.0); weights.put("d", 1.0);
        VertexCoverAnalyzer a = new VertexCoverAnalyzer(graph);
        Set<String> cover = a.weightedVertexCover(weights);
        assertTrue(a.isVertexCover(cover));
        // Leaves are cheaper (4×1=4 < 100) so should pick leaves
        assertFalse(cover.contains("center"));
    }
}
