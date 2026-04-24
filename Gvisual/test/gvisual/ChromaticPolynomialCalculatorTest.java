package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link ChromaticPolynomialCalculator}.
 */
public class ChromaticPolynomialCalculatorTest {

    private Graph<String, String> graph;
    private int edgeId;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
        edgeId = 0;
    }

    private void addEdge(String v1, String v2) {
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        graph.addEdge("e" + edgeId++, v1, v2);
    }

    // --- Empty / trivial graphs ---

    @Test
    public void emptyGraph_polynomialIsOne() {
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(graph);
        ChromaticPolynomialCalculator.PolynomialResult r = calc.compute();
        assertEquals(1, r.evaluate(0));
        assertEquals(1, r.evaluate(5));
        assertTrue(r.isExact());
    }

    @Test
    public void singleVertex_polynomialIsK() {
        graph.addVertex("A");
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(graph);
        ChromaticPolynomialCalculator.PolynomialResult r = calc.compute();
        // P(G,k) = k
        assertEquals(0, r.evaluate(0));
        assertEquals(1, r.evaluate(1));
        assertEquals(5, r.evaluate(5));
        assertEquals(1, r.getChromaticNumber());
        assertTrue(r.isExact());
    }

    @Test
    public void independentSet_polynomialIsKToN() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(graph);
        ChromaticPolynomialCalculator.PolynomialResult r = calc.compute();
        // P = k^3
        assertEquals(0, r.evaluate(0));
        assertEquals(1, r.evaluate(1));
        assertEquals(8, r.evaluate(2));
        assertEquals(27, r.evaluate(3));
        assertEquals(1, r.getChromaticNumber());
    }

    @Test(expected = NullPointerException.class)
    public void nullGraph_throws() {
        new ChromaticPolynomialCalculator(null);
    }

    // --- Single edge (K2) ---

    @Test
    public void singleEdge_polynomial() {
        addEdge("A", "B");
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(graph);
        ChromaticPolynomialCalculator.PolynomialResult r = calc.compute();
        // P(K2, k) = k(k-1)
        assertEquals(0, r.evaluate(0));
        assertEquals(0, r.evaluate(1));
        assertEquals(2, r.evaluate(2));
        assertEquals(6, r.evaluate(3));
        assertEquals(2, r.getChromaticNumber());
    }

    // --- Complete graphs ---

    @Test
    public void completeK3_polynomial() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(graph);
        ChromaticPolynomialCalculator.PolynomialResult r = calc.compute();
        // P(K3, k) = k(k-1)(k-2)
        assertEquals(0, r.evaluate(0));
        assertEquals(0, r.evaluate(1));
        assertEquals(0, r.evaluate(2));
        assertEquals(6, r.evaluate(3));
        assertEquals(24, r.evaluate(4));
        assertEquals(3, r.getChromaticNumber());
        assertEquals("Complete K3", r.getSpecialType());
    }

    @Test
    public void completeK4_polynomial() {
        String[] vs = {"A", "B", "C", "D"};
        for (int i = 0; i < vs.length; i++)
            for (int j = i + 1; j < vs.length; j++)
                addEdge(vs[i], vs[j]);
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(graph);
        ChromaticPolynomialCalculator.PolynomialResult r = calc.compute();
        // P(K4, k) = k(k-1)(k-2)(k-3)
        assertEquals(0, r.evaluate(3));
        assertEquals(24, r.evaluate(4));
        assertEquals(120, r.evaluate(5));
        assertEquals(4, r.getChromaticNumber());
    }

    // --- Trees ---

    @Test
    public void pathGraph_isTree() {
        // A-B-C-D: tree on 4 vertices
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(graph);
        ChromaticPolynomialCalculator.PolynomialResult r = calc.compute();
        // P(T4, k) = k(k-1)^3
        assertEquals(0, r.evaluate(0));
        assertEquals(0, r.evaluate(1));
        assertEquals(2, r.evaluate(2)); // 2*1^3 = 2
        assertEquals(24, r.evaluate(3)); // 3*2^3 = 24
        assertEquals(2, r.getChromaticNumber());
        assertNotNull(r.getSpecialType());
        assertTrue(r.getSpecialType().startsWith("Tree"));
    }

    @Test
    public void starGraph_isTree() {
        // Hub "H" connected to A,B,C,D
        addEdge("H", "A");
        addEdge("H", "B");
        addEdge("H", "C");
        addEdge("H", "D");
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(graph);
        ChromaticPolynomialCalculator.PolynomialResult r = calc.compute();
        // P(T5, k) = k(k-1)^4
        assertEquals(0, r.evaluate(1));
        assertEquals(2, r.evaluate(2)); // 2*1 = 2
        assertEquals(48, r.evaluate(3)); // 3*16 = 48
        assertEquals(2, r.getChromaticNumber());
    }

    // --- Cycles ---

    @Test
    public void cycle3_isTriangle() {
        // Same as K3, but test via cycle detection path
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(graph);
        ChromaticPolynomialCalculator.PolynomialResult r = calc.compute();
        // K3 is detected as Complete before Cycle, but polynomial is same
        assertEquals(6, r.evaluate(3));
        assertEquals(3, r.getChromaticNumber());
    }

    @Test
    public void cycle4_polynomial() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "A");
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(graph);
        ChromaticPolynomialCalculator.PolynomialResult r = calc.compute();
        // P(C4, k) = (k-1)^4 + (k-1)
        // k=2: 1 + 1 = 2
        assertEquals(2, r.evaluate(2));
        // k=3: 16 + 2 = 18
        assertEquals(18, r.evaluate(3));
        assertEquals(2, r.getChromaticNumber());
        assertEquals("Cycle C4", r.getSpecialType());
    }

    @Test
    public void cycle5_oddCycle() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "E");
        addEdge("E", "A");
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(graph);
        ChromaticPolynomialCalculator.PolynomialResult r = calc.compute();
        // P(C5, k) = (k-1)^5 - (k-1)
        // k=2: 1 - 1 = 0  (odd cycle is not 2-colorable)
        assertEquals(0, r.evaluate(2));
        // k=3: 32 - 2 = 30
        assertEquals(30, r.evaluate(3));
        assertEquals(3, r.getChromaticNumber());
    }

    // --- Disconnected components ---

    @Test
    public void disconnectedGraph_productsPolynomials() {
        // K2 + isolated vertex = P(K2,k) * P(v,k) = k(k-1) * k = k^2(k-1)
        addEdge("A", "B");
        graph.addVertex("C");
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(graph);
        ChromaticPolynomialCalculator.PolynomialResult r = calc.compute();
        assertEquals(0, r.evaluate(0));
        assertEquals(0, r.evaluate(1));
        // k^2*(k-1) at k=2 = 4*1 = 4
        assertEquals(4, r.evaluate(2));
        assertEquals(18, r.evaluate(3)); // 9*2 = 18
    }

    @Test
    public void twoDisconnectedEdges() {
        // K2 + K2 = [k(k-1)]^2
        addEdge("A", "B");
        addEdge("C", "D");
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(graph);
        ChromaticPolynomialCalculator.PolynomialResult r = calc.compute();
        // k=2: [2*1]^2 = 4
        assertEquals(4, r.evaluate(2));
        // k=3: [3*2]^2 = 36
        assertEquals(36, r.evaluate(3));
    }

    // --- Deletion-contraction (general graph) ---

    @Test
    public void diamondGraph_deletionContraction() {
        // Diamond: K4 minus one edge
        addEdge("A", "B");
        addEdge("A", "C");
        addEdge("A", "D");
        addEdge("B", "C");
        addEdge("C", "D");
        // Missing B-D
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(graph);
        ChromaticPolynomialCalculator.PolynomialResult r = calc.compute();
        // Chromatic number should be 3 (triangle A-B-C exists)
        assertEquals(3, r.getChromaticNumber());
        assertEquals(0, r.evaluate(2));
        assertTrue(r.evaluate(3) > 0);
        assertTrue(r.isExact());
    }

    @Test
    public void petersenLikeSmall_exact() {
        // A small non-trivial graph: house graph (square + triangle on top)
        // A-B-C-D-A plus B-E and C-E
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "A");
        addEdge("B", "E");
        addEdge("C", "E");
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(graph);
        ChromaticPolynomialCalculator.PolynomialResult r = calc.compute();
        assertTrue(r.isExact());
        assertEquals(3, r.getChromaticNumber());
        // P(G,0) must always be 0 for non-empty graphs
        assertEquals(0, r.evaluate(0));
    }

    // --- PolynomialResult API ---

    @Test
    public void resultDegreeEqualsVertexCount() {
        addEdge("A", "B");
        addEdge("B", "C");
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(graph);
        ChromaticPolynomialCalculator.PolynomialResult r = calc.compute();
        assertEquals(r.getVertexCount(), r.getDegree());
    }

    @Test
    public void polynomialStringNotEmpty() {
        addEdge("A", "B");
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(graph);
        ChromaticPolynomialCalculator.PolynomialResult r = calc.compute();
        assertFalse(r.getPolynomialString().isEmpty());
    }

    @Test
    public void evaluationsListPopulated() {
        addEdge("A", "B");
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(graph);
        ChromaticPolynomialCalculator.PolynomialResult r = calc.compute();
        assertFalse(r.getEvaluations().isEmpty());
        // First entry k=0 should always be 0 for non-empty graph
        assertEquals(Integer.valueOf(0), r.getEvaluations().get(0).getKey());
        assertEquals(Long.valueOf(0), r.getEvaluations().get(0).getValue());
    }

    @Test
    public void coefficientSecondTermEqualsNegativeEdgeCount() {
        // For any simple graph, coefficient of k^(n-1) = -|E|
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(graph);
        ChromaticPolynomialCalculator.PolynomialResult r = calc.compute();
        long[] coeffs = r.getCoefficients();
        // Leading: coeffs[n] = 1, second: coeffs[n-1] = -|E|
        assertEquals(1, coeffs[coeffs.length - 1]);
        assertEquals(-r.getEdgeCount(), coeffs[coeffs.length - 2]);
    }

    // --- Report generation ---

    @Test
    public void reportNotEmpty() {
        addEdge("A", "B");
        addEdge("B", "C");
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(graph);
        String report = calc.generateReport();
        assertNotNull(report);
        assertTrue(report.contains("CHROMATIC POLYNOMIAL"));
        assertTrue(report.contains("Chromatic number"));
    }

    @Test
    public void reportIncludesSpecialType() {
        // K3
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(graph);
        String report = calc.generateReport();
        assertTrue(report.contains("Complete K3"));
    }
}
