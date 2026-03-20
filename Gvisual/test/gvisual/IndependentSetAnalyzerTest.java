package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for IndependentSetAnalyzer.
 */
public class IndependentSetAnalyzerTest {

    private Graph<String, Edge> emptyGraph;
    private Graph<String, Edge> singleVertex;
    private Graph<String, Edge> singleEdge;
    private Graph<String, Edge> triangle;
    private Graph<String, Edge> path4;    // A-B-C-D
    private Graph<String, Edge> star5;    // center connected to 4 leaves
    private Graph<String, Edge> cycle5;   // 5-cycle
    private Graph<String, Edge> complete4;
    private Graph<String, Edge> bipartite; // K_{2,3}
    private Graph<String, Edge> petersen;  // Petersen graph approx

    @Before
    public void setUp() {
        // Empty graph
        emptyGraph = new UndirectedSparseGraph<>();

        // Single vertex
        singleVertex = new UndirectedSparseGraph<>();
        singleVertex.addVertex("A");

        // Single Edge singleEdge  new UndirectedSparseGraph<>();
        singleEdge.addVertex("A");
        singleEdge.addVertex("B");
        singleEdge.addEdge(new Edge("e1", "A", "B"), "A", "B");

        // Triangle
        triangle = new UndirectedSparseGraph<>();
        for (String v : new String[]{"A", "B", "C"}) triangle.addVertex(v);
        triangle.addEdge(new Edge("e1", "A", "B"), "A", "B");
        triangle.addEdge(new Edge("e2", "B", "C"), "B", "C");
        triangle.addEdge(new Edge("e3", "A", "C"), "A", "C");

        // Path: A-B-C-D
        path4 = new UndirectedSparseGraph<>();
        for (String v : new String[]{"A", "B", "C", "D"}) path4.addVertex(v);
        path4.addEdge(new Edge("e1", "A", "B"), "A", "B");
        path4.addEdge(new Edge("e2", "B", "C"), "B", "C");
        path4.addEdge(new Edge("e3", "C", "D"), "C", "D");

        // Star: center=X, leaves=A,B,C,D
        star5 = new UndirectedSparseGraph<>();
        for (String v : new String[]{"X", "A", "B", "C", "D"}) star5.addVertex(v);
        star5.addEdge(new Edge("e1", "X", "A"), "X", "A");
        star5.addEdge(new Edge("e2", "X", "B"), "X", "B");
        star5.addEdge(new Edge("e3", "X", "C"), "X", "C");
        star5.addEdge(new Edge("e4", "X", "D"), "X", "D");

        // 5-cycle: A-B-C-D-E-A
        cycle5 = new UndirectedSparseGraph<>();
        for (String v : new String[]{"A", "B", "C", "D", "E"}) cycle5.addVertex(v);
        cycle5.addEdge(new Edge("e1", "A", "B"), "A", "B");
        cycle5.addEdge(new Edge("e2", "B", "C"), "B", "C");
        cycle5.addEdge(new Edge("e3", "C", "D"), "C", "D");
        cycle5.addEdge(new Edge("e4", "D", "E"), "D", "E");
        cycle5.addEdge(new Edge("e5", "E", "A"), "E", "A");

        // K4
        complete4 = new UndirectedSparseGraph<>();
        for (String v : new String[]{"A", "B", "C", "D"}) complete4.addVertex(v);
        complete4.addEdge(new Edge("e1", "A", "B"), "A", "B");
        complete4.addEdge(new Edge("e2", "A", "C"), "A", "C");
        complete4.addEdge(new Edge("e3", "A", "D"), "A", "D");
        complete4.addEdge(new Edge("e4", "B", "C"), "B", "C");
        complete4.addEdge(new Edge("e5", "B", "D"), "B", "D");
        complete4.addEdge(new Edge("e6", "C", "D"), "C", "D");

        // K_{2,3}: {A,B} x {C,D,E}
        bipartite = new UndirectedSparseGraph<>();
        for (String v : new String[]{"A", "B", "C", "D", "E"}) bipartite.addVertex(v);
        int eid = 0;
        for (String l : new String[]{"A", "B"}) {
            for (String r : new String[]{"C", "D", "E"}) {
                bipartite.addEdge(new Edge("e" + (eid++), l, r), l, r);
            }
        }
    }

    // ── Constructor ─────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new IndependentSetAnalyzer(null);
    }

    // ── isIndependentSet ────────────────────────────────────────

    @Test
    public void testNullSetIsIndependent() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(triangle);
        assertTrue(a.isIndependentSet(null));
    }

    @Test
    public void testEmptySetIsIndependent() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(triangle);
        assertTrue(a.isIndependentSet(Collections.emptySet()));
    }

    @Test
    public void testSingletonIsIndependent() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(triangle);
        assertTrue(a.isIndependentSet(new HashSet<>(Arrays.asList("A"))));
    }

    @Test
    public void testAdjacentPairNotIndependent() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(triangle);
        assertFalse(a.isIndependentSet(new HashSet<>(Arrays.asList("A", "B"))));
    }

    @Test
    public void testNonAdjacentPairIsIndependent() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(path4);
        assertTrue(a.isIndependentSet(new HashSet<>(Arrays.asList("A", "C"))));
    }

    @Test
    public void testPathMaxISValid() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(path4);
        assertTrue(a.isIndependentSet(new HashSet<>(Arrays.asList("A", "D"))));
        assertTrue(a.isIndependentSet(new HashSet<>(Arrays.asList("A", "C"))));
        assertTrue(a.isIndependentSet(new HashSet<>(Arrays.asList("B", "D"))));
    }

    // ── isMaximalIndependentSet ──────────────────────────────────

    @Test
    public void testMaximalISOnPath() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(path4);
        // {A, C} is maximal (can't add B or D)
        assertTrue(a.isMaximalIndependentSet(new HashSet<>(Arrays.asList("A", "C"))));
    }

    @Test
    public void testNonMaximalIS() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(path4);
        // {A} is not maximal — can add C or D
        assertFalse(a.isMaximalIndependentSet(new HashSet<>(Arrays.asList("A"))));
    }

    @Test
    public void testNonISIsNotMaximal() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(triangle);
        assertFalse(a.isMaximalIndependentSet(new HashSet<>(Arrays.asList("A", "B"))));
    }

    // ── greedyIndependentSet ────────────────────────────────────

    @Test
    public void testGreedyOnEmptyGraph() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(emptyGraph);
        Set<String> is = a.greedyIndependentSet();
        assertTrue(is.isEmpty());
    }

    @Test
    public void testGreedyOnSingleVertex() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(singleVertex);
        Set<String> is = a.greedyIndependentSet();
        assertEquals(1, is.size());
        assertTrue(is.contains("A"));
    }

    @Test
    public void testGreedyOnSingleEdge() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(singleEdge);
        Set<String> is = a.greedyIndependentSet();
        assertEquals(1, is.size());
        assertTrue(a.isIndependentSet(is));
    }

    @Test
    public void testGreedyOnTriangle() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(triangle);
        Set<String> is = a.greedyIndependentSet();
        assertEquals(1, is.size());
        assertTrue(a.isIndependentSet(is));
    }

    @Test
    public void testGreedyOnPath4() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(path4);
        Set<String> is = a.greedyIndependentSet();
        assertTrue(is.size() >= 2);
        assertTrue(a.isIndependentSet(is));
        assertTrue(a.isMaximalIndependentSet(is));
    }

    @Test
    public void testGreedyOnStar() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(star5);
        Set<String> is = a.greedyIndependentSet();
        assertTrue(a.isIndependentSet(is));
        assertTrue(a.isMaximalIndependentSet(is));
    }

    @Test
    public void testGreedyOnComplete4() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(complete4);
        Set<String> is = a.greedyIndependentSet();
        assertEquals(1, is.size());
    }

    @Test
    public void testGreedyOnBipartite() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(bipartite);
        Set<String> is = a.greedyIndependentSet();
        assertTrue(a.isIndependentSet(is));
        assertTrue(is.size() >= 2);
    }

    // ── greedyMaxDegreeIndependentSet ───────────────────────────

    @Test
    public void testMaxDegreeGreedyOnPath() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(path4);
        Set<String> is = a.greedyMaxDegreeIndependentSet();
        assertTrue(a.isIndependentSet(is));
    }

    @Test
    public void testMaxDegreeGreedyOnEmpty() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(emptyGraph);
        Set<String> is = a.greedyMaxDegreeIndependentSet();
        assertTrue(is.isEmpty());
    }

    // ── exactMaximumIndependentSet ──────────────────────────────

    @Test
    public void testExactOnEmptyGraph() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(emptyGraph);
        assertEquals(0, a.exactMaximumIndependentSet().size());
    }

    @Test
    public void testExactOnSingleVertex() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(singleVertex);
        assertEquals(1, a.exactMaximumIndependentSet().size());
    }

    @Test
    public void testExactOnSingleEdge() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(singleEdge);
        assertEquals(1, a.exactMaximumIndependentSet().size());
    }

    @Test
    public void testExactOnTriangle() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(triangle);
        assertEquals(1, a.exactMaximumIndependentSet().size());
    }

    @Test
    public void testExactOnPath4() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(path4);
        Set<String> mis = a.exactMaximumIndependentSet();
        assertEquals(2, mis.size());
        assertTrue(a.isIndependentSet(mis));
    }

    @Test
    public void testExactOnStar5() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(star5);
        Set<String> mis = a.exactMaximumIndependentSet();
        assertEquals(4, mis.size()); // all leaves
        assertFalse(mis.contains("X"));
    }

    @Test
    public void testExactOnCycle5() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(cycle5);
        Set<String> mis = a.exactMaximumIndependentSet();
        assertEquals(2, mis.size());
        assertTrue(a.isIndependentSet(mis));
    }

    @Test
    public void testExactOnComplete4() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(complete4);
        assertEquals(1, a.exactMaximumIndependentSet().size());
    }

    @Test
    public void testExactOnBipartite() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(bipartite);
        Set<String> mis = a.exactMaximumIndependentSet();
        assertEquals(3, mis.size()); // larger partition {C,D,E}
        assertTrue(a.isIndependentSet(mis));
    }

    @Test(expected = IllegalStateException.class)
    public void testExactVertexLimitThrows() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(path4);
        a.exactMaximumIndependentSet(2); // limit=2 but graph has 4
    }

    // ── allMaximalIndependentSets ───────────────────────────────

    @Test
    public void testAllMISOnEmpty() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(emptyGraph);
        List<Set<String>> all = a.allMaximalIndependentSets();
        // Empty set is the only maximal IS of empty graph
        assertEquals(1, all.size());
    }

    @Test
    public void testAllMISOnSingleVertex() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(singleVertex);
        List<Set<String>> all = a.allMaximalIndependentSets();
        assertEquals(1, all.size());
        assertTrue(all.get(0).contains("A"));
    }

    @Test
    public void testAllMISOnSingleEdge() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(singleEdge);
        List<Set<String>> all = a.allMaximalIndependentSets();
        assertEquals(2, all.size()); // {A} and {B}
        for (Set<String> s : all) {
            assertTrue(a.isMaximalIndependentSet(s));
        }
    }

    @Test
    public void testAllMISOnTriangle() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(triangle);
        List<Set<String>> all = a.allMaximalIndependentSets();
        assertEquals(3, all.size()); // {A}, {B}, {C}
    }

    @Test
    public void testAllMISOnPath4() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(path4);
        List<Set<String>> all = a.allMaximalIndependentSets();
        // Path on 4: maximal IS are {A,C}, {A,D}, {B,D}
        assertTrue(all.size() >= 3);
        for (Set<String> s : all) {
            assertTrue(a.isMaximalIndependentSet(s));
        }
    }

    @Test
    public void testAllMISLimit() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(path4);
        List<Set<String>> limited = a.allMaximalIndependentSets(1);
        assertEquals(1, limited.size());
    }

    @Test
    public void testAllMISAllValid() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(cycle5);
        List<Set<String>> all = a.allMaximalIndependentSets();
        for (Set<String> s : all) {
            assertTrue(a.isIndependentSet(s));
            assertTrue(a.isMaximalIndependentSet(s));
        }
    }

    // ── kernelReduction ─────────────────────────────────────────

    @Test
    public void testKernelOnEmptyGraph() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(emptyGraph);
        IndependentSetAnalyzer.KernelResult kr = a.kernelReduction();
        assertTrue(kr.forcedVertices.isEmpty());
        assertTrue(kr.kernelVertices.isEmpty());
    }

    @Test
    public void testKernelOnIsolatedVertices() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(g);
        IndependentSetAnalyzer.KernelResult kr = a.kernelReduction();
        assertEquals(3, kr.forcedVertices.size());
        assertTrue(kr.kernelVertices.isEmpty());
    }

    @Test
    public void testKernelOnPath4() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(path4);
        IndependentSetAnalyzer.KernelResult kr = a.kernelReduction();
        // A and D are degree-1. After forcing A and removing B, C becomes degree-1.
        // So forced = {A, C} (not {A, D}).
        assertTrue(kr.forcedVertices.contains("A"));
        assertTrue(kr.forcedVertices.contains("C"));
        assertTrue(kr.rulesApplied > 0);
    }

    @Test
    public void testKernelOnStar() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(star5);
        IndependentSetAnalyzer.KernelResult kr = a.kernelReduction();
        // All leaves are degree-1, forced in
        assertTrue(kr.rulesApplied > 0);
        assertTrue(kr.forcedVertices.size() >= 1);
    }

    @Test
    public void testKernelOnComplete4() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(complete4);
        IndependentSetAnalyzer.KernelResult kr = a.kernelReduction();
        // No reduction possible (all deg 3)
        assertEquals(0, kr.rulesApplied);
        assertEquals(4, kr.kernelVertices.size());
    }

    @Test
    public void testKernelLogNotEmpty() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(path4);
        IndependentSetAnalyzer.KernelResult kr = a.kernelReduction();
        assertFalse(kr.ruleLog.isEmpty());
    }

    // ── independenceNumberBounds ─────────────────────────────────

    @Test
    public void testBoundsOnEmptyGraph() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(emptyGraph);
        Map<String, Double> b = a.independenceNumberBounds();
        assertEquals(0.0, b.get("trivial_upper"), 0.01);
    }

    @Test
    public void testBoundsOnPath4() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(path4);
        Map<String, Double> b = a.independenceNumberBounds();
        // α(P4) = 2
        assertTrue(b.get("greedy_lower") >= 2.0);
        assertTrue(b.get("trivial_upper") >= 2.0);
    }

    @Test
    public void testBoundsContainExpectedKeys() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(triangle);
        Map<String, Double> b = a.independenceNumberBounds();
        assertTrue(b.containsKey("trivial_upper"));
        assertTrue(b.containsKey("trivial_lower"));
        assertTrue(b.containsKey("greedy_lower"));
        assertTrue(b.containsKey("turan_lower"));
        assertTrue(b.containsKey("ramsey_lower"));
    }

    @Test
    public void testTuranBoundOnComplete() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(complete4);
        Map<String, Double> b = a.independenceNumberBounds();
        // Turán: 4/(1+3) = 1.0
        assertEquals(1.0, b.get("turan_lower"), 0.01);
    }

    // ── vertexMISParticipation ───────────────────────────────────

    @Test
    public void testParticipationOnTriangle() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(triangle);
        Map<String, Integer> p = a.vertexMISParticipation(100);
        // Each vertex in exactly 1 maximal IS
        assertEquals(1, (int) p.get("A"));
        assertEquals(1, (int) p.get("B"));
        assertEquals(1, (int) p.get("C"));
    }

    @Test
    public void testParticipationOnSingleEdge() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(singleEdge);
        Map<String, Integer> p = a.vertexMISParticipation(100);
        assertEquals(1, (int) p.get("A"));
        assertEquals(1, (int) p.get("B"));
    }

    // ── vertexIndependenceImpact ────────────────────────────────

    @Test
    public void testImpactOnPath4() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(path4);
        Map<String, Integer> impact = a.vertexIndependenceImpact();
        assertNotNull(impact);
        assertEquals(4, impact.size());
    }

    @Test
    public void testImpactOnSingleVertex() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(singleVertex);
        Map<String, Integer> impact = a.vertexIndependenceImpact();
        assertEquals(1, (int) impact.get("A")); // removing it drops IS from 1 to 0
    }

    // ── independencePolynomial ───────────────────────────────────

    @Test
    public void testPolynomialOnEmpty() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(emptyGraph);
        int[] poly = a.independencePolynomial();
        assertEquals(1, poly.length);
        assertEquals(1, poly[0]); // just empty set
    }

    @Test
    public void testPolynomialOnSingleVertex() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(singleVertex);
        int[] poly = a.independencePolynomial();
        assertEquals(2, poly.length);
        assertEquals(1, poly[0]); // empty set
        assertEquals(1, poly[1]); // {A}
    }

    @Test
    public void testPolynomialOnSingleEdge() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(singleEdge);
        int[] poly = a.independencePolynomial();
        assertEquals(1, poly[0]); // empty
        assertEquals(2, poly[1]); // {A}, {B}
        assertEquals(0, poly[2]); // no IS of size 2
    }

    @Test
    public void testPolynomialOnTriangle() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(triangle);
        int[] poly = a.independencePolynomial();
        assertEquals(1, poly[0]);
        assertEquals(3, poly[1]); // 3 singletons
        assertEquals(0, poly[2]); // no IS of size 2
        assertEquals(0, poly[3]); // no IS of size 3
    }

    @Test
    public void testPolynomialOnPath4() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(path4);
        int[] poly = a.independencePolynomial();
        assertEquals(1, poly[0]); // empty
        assertEquals(4, poly[1]); // 4 singletons
        // IS of size 2: {A,C}, {A,D}, {B,D} = 3
        assertEquals(3, poly[2]);
    }

    @Test(expected = IllegalStateException.class)
    public void testPolynomialTooLargeThrows() {
        Graph<String, Edge> big = new UndirectedSparseGraph<>();
        for (int i = 0; i < 25; i++) big.addVertex("V" + i);
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(big);
        a.independencePolynomial(); // > 20 vertices
    }

    // ── maximumCliqueViaComplement ──────────────────────────────

    @Test
    public void testMaxCliqueOnTriangle() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(triangle);
        Set<String> clique = a.maximumCliqueViaComplement();
        assertEquals(3, clique.size()); // triangle is a 3-clique
    }

    @Test
    public void testMaxCliqueOnPath4() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(path4);
        Set<String> clique = a.maximumCliqueViaComplement();
        assertEquals(2, clique.size()); // max clique in P4 = any edge
    }

    @Test
    public void testMaxCliqueOnIndependentVertices() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(g);
        Set<String> clique = a.maximumCliqueViaComplement();
        assertEquals(1, clique.size()); // complement is K3, but IS of K3 = 1... wait
        // complement of 3 isolated vertices = K3. IS of K3 = 1. So max clique of original = 1.
        // That's correct: 3 isolated vertices have no edges so max clique = 1
    }

    // ── fullReport ──────────────────────────────────────────────

    @Test
    public void testReportOnPath4() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(path4);
        IndependentSetAnalyzer.IndependentSetReport r = a.fullReport();
        assertEquals(4, r.vertexCount);
        assertEquals(3, r.edgeCount);
        assertEquals(2, r.independenceNumber);
        assertTrue(a.isIndependentSet(r.maximumIS));
        assertTrue(a.isIndependentSet(r.greedyIS));
        assertNotNull(r.summary);
        assertTrue(r.summary.contains("Independence number"));
    }

    @Test
    public void testReportOnStar() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(star5);
        IndependentSetAnalyzer.IndependentSetReport r = a.fullReport();
        assertEquals(4, r.independenceNumber);
    }

    @Test
    public void testReportOnComplete4() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(complete4);
        IndependentSetAnalyzer.IndependentSetReport r = a.fullReport();
        assertEquals(1, r.independenceNumber);
    }

    @Test
    public void testReportOnEmpty() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(emptyGraph);
        IndependentSetAnalyzer.IndependentSetReport r = a.fullReport();
        assertEquals(0, r.vertexCount);
        assertEquals(0, r.independenceNumber);
    }

    @Test
    public void testReportOnBipartite() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(bipartite);
        IndependentSetAnalyzer.IndependentSetReport r = a.fullReport();
        assertEquals(3, r.independenceNumber); // larger partition
    }

    @Test
    public void testReportBoundsNotEmpty() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(cycle5);
        IndependentSetAnalyzer.IndependentSetReport r = a.fullReport();
        assertFalse(r.bounds.isEmpty());
    }

    @Test
    public void testReportKernelNotNull() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(path4);
        IndependentSetAnalyzer.IndependentSetReport r = a.fullReport();
        assertNotNull(r.kernel);
    }

    @Test
    public void testReportSummaryContainsVertexCount() {
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(cycle5);
        IndependentSetAnalyzer.IndependentSetReport r = a.fullReport();
        assertTrue(r.summary.contains("5 vertices"));
    }

    // ── Disconnected graph ──────────────────────────────────────

    @Test
    public void testDisconnectedGraph() {
        // Two components: triangle + isolated vertex
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C"); g.addVertex("D");
        g.addEdge(new Edge("e1", "A", "B"), "A", "B");
        g.addEdge(new Edge("e2", "B", "C"), "B", "C");
        g.addEdge(new Edge("e3", "A", "C"), "A", "C");
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(g);
        Set<String> mis = a.exactMaximumIndependentSet();
        assertEquals(2, mis.size()); // 1 from triangle + D
        assertTrue(mis.contains("D"));
    }

    @Test
    public void testTwoDisconnectedEdges() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C"); g.addVertex("D");
        g.addEdge(new Edge("e1", "A", "B"), "A", "B");
        g.addEdge(new Edge("e2", "C", "D"), "C", "D");
        IndependentSetAnalyzer a = new IndependentSetAnalyzer(g);
        assertEquals(2, a.exactMaximumIndependentSet().size());
    }
}
