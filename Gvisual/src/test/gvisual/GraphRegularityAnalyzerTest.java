package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for GraphRegularityAnalyzer.
 */
public class GraphRegularityAnalyzerTest {

    private Graph<String, Edge> makeTriangle() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addEdge(new Edge("A", "B", EdgeType.UNDIRECTED), "A", "B");
        g.addEdge(new Edge("B", "C", EdgeType.UNDIRECTED), "B", "C");
        g.addEdge(new Edge("A", "C", EdgeType.UNDIRECTED), "A", "C");
        return g;
    }

    private Graph<String, Edge> makePath() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addEdge(new Edge("A", "B", EdgeType.UNDIRECTED), "A", "B");
        g.addEdge(new Edge("B", "C", EdgeType.UNDIRECTED), "B", "C");
        return g;
    }

    @Test
    public void testRegularGraph() {
        GraphRegularityAnalyzer a = new GraphRegularityAnalyzer(makeTriangle());
        a.analyze();
        assertTrue(a.isRegular());
        assertEquals(2, a.getRegularityDegree());
        assertEquals(0, a.getAlbertsonIndex());
        assertEquals(0.0, a.getDegreeVariance(), 0.001);
        assertTrue(a.getDeviantVertices().isEmpty());
    }

    @Test
    public void testIrregularGraph() {
        GraphRegularityAnalyzer a = new GraphRegularityAnalyzer(makePath());
        a.analyze();
        assertFalse(a.isRegular());
        assertEquals(-1, a.getRegularityDegree());
        assertEquals(1, a.getMinDegree());
        assertEquals(2, a.getMaxDegree());
        assertTrue(a.getAlbertsonIndex() > 0);
        assertFalse(a.getDeviantVertices().isEmpty());
    }

    @Test
    public void testEmptyGraph() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        GraphRegularityAnalyzer a = new GraphRegularityAnalyzer(g);
        a.analyze();
        assertTrue(a.isRegular());
        assertEquals(0, a.getRegularityDegree());
    }

    @Test
    public void testReport() {
        GraphRegularityAnalyzer a = new GraphRegularityAnalyzer(makeTriangle());
        a.analyze();
        String report = a.generateReport();
        assertTrue(report.contains("2-regular"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraph() {
        new GraphRegularityAnalyzer(null);
    }

    @Test(expected = IllegalStateException.class)
    public void testNotComputed() {
        GraphRegularityAnalyzer a = new GraphRegularityAnalyzer(makeTriangle());
        a.isRegular(); // should throw
    }
}
