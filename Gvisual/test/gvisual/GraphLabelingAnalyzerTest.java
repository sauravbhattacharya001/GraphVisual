package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;
import static org.junit.Assert.*;

public class GraphLabelingAnalyzerTest {

    private Graph<String, Edge> makeTriangle() {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addEdge(new Edge("A", "B", "e1"), "A", "B");
        g.addEdge(new Edge("B", "C", "e2"), "B", "C");
        g.addEdge(new Edge("A", "C", "e3"), "A", "C");
        return g;
    }

    private Graph<String, Edge> makePath3() {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addEdge(new Edge("A", "B", "e1"), "A", "B");
        g.addEdge(new Edge("B", "C", "e2"), "B", "C");
        return g;
    }

    private Graph<String, Edge> makeEmpty() {
        return new UndirectedSparseGraph<String, Edge>();
    }

    private Graph<String, Edge> makeSingleVertex() {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        g.addVertex("A");
        return g;
    }

    private Graph<String, Edge> makeBipartiteK23() {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        g.addVertex("A1"); g.addVertex("A2");
        g.addVertex("B1"); g.addVertex("B2"); g.addVertex("B3");
        int ec = 0;
        for (String a : new String[]{"A1", "A2"}) {
            for (String b : new String[]{"B1", "B2", "B3"}) {
                g.addEdge(new Edge(a, b, "e" + (++ec)), a, b);
            }
        }
        return g;
    }

    @Test
    public void testEmptyGraph() {
        GraphLabelingAnalyzer a = new GraphLabelingAnalyzer(makeEmpty());
        a.compute();
        assertEquals(0, a.getMaxDegree());
        assertTrue(a.isGracefulExists());
        assertEquals(0, a.getBandwidth());
    }

    @Test
    public void testSingleVertex() {
        GraphLabelingAnalyzer a = new GraphLabelingAnalyzer(makeSingleVertex());
        a.compute();
        assertEquals(0, a.getMaxDegree());
        assertEquals(0, a.getBandwidth());
    }

    @Test
    public void testPathIsGraceful() {
        // Path P3: A-B-C, 2 edges. Graceful: A=0, B=2, C=1 → edges |0-2|=2, |2-1|=1
        GraphLabelingAnalyzer a = new GraphLabelingAnalyzer(makePath3());
        a.compute();
        assertTrue(a.isGracefulExists());
        assertNotNull(a.getGracefulLabeling());
        assertEquals(2, a.getMaxDegree()); // B has degree 2
    }

    @Test
    public void testTriangleVizing() {
        GraphLabelingAnalyzer a = new GraphLabelingAnalyzer(makeTriangle());
        a.compute();
        // K3 is odd complete graph → Class 2
        assertEquals(2, a.getMaxDegree());
        assertTrue(a.getVizingClass().contains("Class 2"));
    }

    @Test
    public void testBipartiteIsClass1() {
        GraphLabelingAnalyzer a = new GraphLabelingAnalyzer(makeBipartiteK23());
        a.compute();
        assertTrue(a.getVizingClass().contains("Class 1"));
        assertEquals(3, a.getMaxDegree());
    }

    @Test
    public void testBandwidth() {
        GraphLabelingAnalyzer a = new GraphLabelingAnalyzer(makePath3());
        a.compute();
        // Path of 3 vertices: optimal bandwidth = 1
        assertEquals(1, a.getBandwidth());
        assertTrue(a.isBandwidthExact());
    }

    @Test
    public void testSummaryNotEmpty() {
        GraphLabelingAnalyzer a = new GraphLabelingAnalyzer(makeTriangle());
        a.compute();
        String summary = a.getSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("Graph Labeling Analysis"));
        assertTrue(summary.contains("Vizing"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraph() {
        new GraphLabelingAnalyzer(null);
    }

    @Test(expected = IllegalStateException.class)
    public void testQueryBeforeCompute() {
        GraphLabelingAnalyzer a = new GraphLabelingAnalyzer(makeTriangle());
        a.getMaxDegree();
    }
}
