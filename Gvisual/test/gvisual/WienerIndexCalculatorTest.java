package gvisual;

import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for {@link WienerIndexCalculator}.
 */
public class WienerIndexCalculatorTest {

    private Graph<String, Edge> makePath(int n) {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        for (int i = 0; i < n; i++) g.addVertex("v" + i);
        for (int i = 0; i < n - 1; i++) {
            Edge e = new Edge("v" + i, "v" + (i + 1));
            g.addEdge(e, "v" + i, "v" + (i + 1));
        }
        return g;
    }

    @Test
    public void testEmptyGraph() {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        WienerIndexCalculator calc = new WienerIndexCalculator(g);
        calc.compute();
        assertEquals(0, calc.getWienerIndex());
        assertEquals(0, calc.getComponentSize());
    }

    @Test
    public void testSingleVertex() {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        g.addVertex("A");
        WienerIndexCalculator calc = new WienerIndexCalculator(g);
        calc.compute();
        assertEquals(0, calc.getWienerIndex());
        assertEquals(0, calc.getPairCount());
    }

    @Test
    public void testPathOfThree() {
        // Path: v0 - v1 - v2
        // Distances: d(0,1)=1, d(0,2)=2, d(1,2)=1 → W = 4
        Graph<String, Edge> g = makePath(3);
        WienerIndexCalculator calc = new WienerIndexCalculator(g);
        calc.compute();
        assertEquals(4, calc.getWienerIndex());
        assertEquals(3, calc.getPairCount());
        assertEquals(4.0 / 3, calc.getAveragePathLength(), 1e-9);
    }

    @Test
    public void testTriangle() {
        // Complete graph K3: all distances = 1, W = 3
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addEdge(new Edge("A", "B"), "A", "B");
        g.addEdge(new Edge("A", "C"), "A", "C");
        g.addEdge(new Edge("B", "C"), "B", "C");
        WienerIndexCalculator calc = new WienerIndexCalculator(g);
        calc.compute();
        assertEquals(3, calc.getWienerIndex());
        assertEquals(1.0, calc.getAveragePathLength(), 1e-9);
        // Harary: 3 pairs each contributing 1/1 = 3.0
        assertEquals(3.0, calc.getHararyIndex(), 1e-9);
    }

    @Test
    public void testPathOfFour() {
        // Path: 0-1-2-3
        // d(0,1)=1, d(0,2)=2, d(0,3)=3, d(1,2)=1, d(1,3)=2, d(2,3)=1
        // W = 1+2+3+1+2+1 = 10
        Graph<String, Edge> g = makePath(4);
        WienerIndexCalculator calc = new WienerIndexCalculator(g);
        calc.compute();
        assertEquals(10, calc.getWienerIndex());
        assertEquals(6, calc.getPairCount());
    }

    @Test
    public void testHyperWiener() {
        // K3: all d=1, WW = ½ * 3 * (1 + 1) = 3
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addEdge(new Edge("A", "B"), "A", "B");
        g.addEdge(new Edge("A", "C"), "A", "C");
        g.addEdge(new Edge("B", "C"), "B", "C");
        WienerIndexCalculator calc = new WienerIndexCalculator(g);
        calc.compute();
        assertEquals(3, calc.getHyperWienerIndex());
    }

    @Test
    public void testSummaryNotNull() {
        Graph<String, Edge> g = makePath(3);
        WienerIndexCalculator calc = new WienerIndexCalculator(g);
        calc.compute();
        String summary = calc.getSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("Wiener Index"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraph() {
        new WienerIndexCalculator(null);
    }

    @Test(expected = IllegalStateException.class)
    public void testQueryBeforeCompute() {
        Graph<String, Edge> g = makePath(3);
        WienerIndexCalculator calc = new WienerIndexCalculator(g);
        calc.getWienerIndex();
    }
}
