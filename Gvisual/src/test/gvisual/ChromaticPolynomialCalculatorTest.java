package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for ChromaticPolynomialCalculator.
 */
public class ChromaticPolynomialCalculatorTest {

    private Graph<String, String> makeTriangle() {
        Graph<String, String> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addEdge("e1", "A", "B");
        g.addEdge("e2", "B", "C");
        g.addEdge("e3", "A", "C");
        return g;
    }

    private Graph<String, String> makePath3() {
        // A-B-C (tree on 3 vertices)
        Graph<String, String> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addEdge("e1", "A", "B");
        g.addEdge("e2", "B", "C");
        return g;
    }

    private Graph<String, String> makeK4() {
        Graph<String, String> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C"); g.addVertex("D");
        g.addEdge("e1", "A", "B"); g.addEdge("e2", "A", "C");
        g.addEdge("e3", "A", "D"); g.addEdge("e4", "B", "C");
        g.addEdge("e5", "B", "D"); g.addEdge("e6", "C", "D");
        return g;
    }

    @Test
    public void testTriangleIsK3() {
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(makeTriangle());
        ChromaticPolynomialCalculator.PolynomialResult result = calc.compute();

        assertTrue(result.isExact());
        assertEquals(3, result.getChromaticNumber());
        // P(K3, k) = k(k-1)(k-2) = k^3 - 3k^2 + 2k
        assertEquals(0, result.evaluate(0));
        assertEquals(0, result.evaluate(1));
        assertEquals(0, result.evaluate(2));
        assertEquals(6, result.evaluate(3));   // 3! = 6
        assertEquals(24, result.evaluate(4));  // 4*3*2 = 24
    }

    @Test
    public void testPathIsTree() {
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(makePath3());
        ChromaticPolynomialCalculator.PolynomialResult result = calc.compute();

        assertTrue(result.isExact());
        assertEquals(2, result.getChromaticNumber());
        // P(tree on 3, k) = k(k-1)^2 = k^3 - 2k^2 + k
        assertEquals(0, result.evaluate(0));
        assertEquals(0, result.evaluate(1));
        assertEquals(2, result.evaluate(2));
        assertEquals(12, result.evaluate(3));
    }

    @Test
    public void testK4() {
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(makeK4());
        ChromaticPolynomialCalculator.PolynomialResult result = calc.compute();

        assertTrue(result.isExact());
        assertEquals(4, result.getChromaticNumber());
        // P(K4, k) = k(k-1)(k-2)(k-3)
        assertEquals(24, result.evaluate(4));  // 4!
        assertEquals(0, result.evaluate(3));
    }

    @Test
    public void testEmptyGraph() {
        Graph<String, String> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(g);
        ChromaticPolynomialCalculator.PolynomialResult result = calc.compute();

        assertTrue(result.isExact());
        assertEquals(1, result.getChromaticNumber());
        // P(empty 3, k) = k^3
        assertEquals(8, result.evaluate(2));
        assertEquals(27, result.evaluate(3));
    }

    @Test
    public void testReportNotEmpty() {
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(makeTriangle());
        String report = calc.generateReport();
        assertNotNull(report);
        assertTrue(report.contains("CHROMATIC POLYNOMIAL"));
        assertTrue(report.contains("χ(G)"));
    }

    @Test
    public void testPolynomialString() {
        ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(makeTriangle());
        ChromaticPolynomialCalculator.PolynomialResult result = calc.compute();
        String polyStr = result.getPolynomialString();
        assertNotNull(polyStr);
        assertFalse(polyStr.isEmpty());
    }
}
