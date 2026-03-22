package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

/**
 * Tests for {@link GraphComplementAnalyzer}.
 */
public class GraphComplementAnalyzerTest {

    private Graph<String, edge> makeTriangle() {
        UndirectedSparseGraph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        edge e1 = new edge("A", "B", "e1");
        edge e2 = new edge("B", "C", "e2");
        g.addEdge(e1, "A", "B");
        g.addEdge(e2, "B", "C");
        return g;
    }

    @Test
    public void complementEdgeCountPlusOriginalEqualsComplete() {
        Graph<String, edge> g = makeTriangle();
        Graph<String, edge> comp = GraphComplementAnalyzer.buildComplement(g);
        int n = g.getVertexCount();
        int maxEdges = n * (n - 1) / 2;
        assertEquals(maxEdges, g.getEdgeCount() + comp.getEdgeCount());
    }

    @Test
    public void complementOfCompleteGraphIsEmpty() {
        UndirectedSparseGraph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addEdge(new edge("A", "B", "1"), "A", "B");
        g.addEdge(new edge("B", "C", "2"), "B", "C");
        g.addEdge(new edge("A", "C", "3"), "A", "C");

        Graph<String, edge> comp = GraphComplementAnalyzer.buildComplement(g);
        assertEquals(0, comp.getEdgeCount());
    }

    @Test
    public void complementPreservesVertices() {
        Graph<String, edge> g = makeTriangle();
        Graph<String, edge> comp = GraphComplementAnalyzer.buildComplement(g);
        assertEquals(g.getVertexCount(), comp.getVertexCount());
        for (String v : g.getVertices()) {
            assertTrue(comp.containsVertex(v));
        }
    }

    @Test
    public void analyzeProducesReport() {
        Graph<String, edge> g = makeTriangle();
        String report = GraphComplementAnalyzer.analyze(g);
        assertTrue(report.contains("GRAPH COMPLEMENT ANALYSIS"));
        assertTrue(report.contains("Density"));
    }

    @Test
    public void getComplementEdgeListWorks() {
        Graph<String, edge> g = makeTriangle();
        List<String[]> edges = GraphComplementAnalyzer.getComplementEdgeList(g);
        // Triangle with 2 edges, 3 vertices → max 3 edges → complement has 1 edge
        assertEquals(1, edges.size());
    }
}
