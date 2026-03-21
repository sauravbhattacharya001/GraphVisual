package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link KTrussAnalyzer}.
 */
public class KTrussAnalyzerTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
    }

    private Edge addEdge(String v1, String v2) {
        Edge e = new Edge("f", v1, v2);
        e.setWeight(1.0f);
        graph.addEdge(e, v1, v2);
        return e;
    }

    @Test
    public void testEmptyGraph() {
        KTrussAnalyzer analyzer = new KTrussAnalyzer(graph);
        assertEquals(0, analyzer.getMaxTrussNumber());
        assertTrue(analyzer.getTrussDistribution().isEmpty());
    }

    @Test
    public void testSingleEdge_noTriangle() {
        addEdge("A", "B");
        KTrussAnalyzer analyzer = new KTrussAnalyzer(graph);
        // An Edge with no triangles has truss number 2
        assertEquals(2, analyzer.getMaxTrussNumber());
    }

    @Test
    public void testTriangle() {
        Edge ab = addEdge("A", "B");
        Edge bc = addEdge("B", "C");
        Edge ac = addEdge("A", "C");

        KTrussAnalyzer analyzer = new KTrussAnalyzer(graph);
        // A triangle: each Edge has 1 triangle support → 3-truss
        assertTrue(analyzer.getMaxTrussNumber() >= 2);
        assertEquals(1, analyzer.getTriangleSupport(ab));
        assertEquals(1, analyzer.getTriangleSupport(bc));
        assertEquals(1, analyzer.getTriangleSupport(ac));
    }

    @Test
    public void testClique4() {
        // K4: each Edge participates in 2 triangles → 4-truss
        addEdge("A", "B");
        addEdge("A", "C");
        addEdge("A", "D");
        addEdge("B", "C");
        addEdge("B", "D");
        addEdge("C", "D");

        KTrussAnalyzer analyzer = new KTrussAnalyzer(graph);
        assertTrue(analyzer.getMaxTrussNumber() >= 3);
    }

    @Test
    public void testGetKTruss() {
        // Build a graph with a triangle plus an appendage
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        addEdge("C", "D"); // no triangle

        KTrussAnalyzer analyzer = new KTrussAnalyzer(graph);
        Graph<String, Edge> truss3 = analyzer.getKTruss(3);

        // The 3-truss should contain only the triangle edges
        assertTrue(truss3.getEdgeCount() <= 3);
        assertFalse(truss3.containsVertex("D") && truss3.getNeighborCount("D") > 0);
    }

    @Test
    public void testTrussDistribution() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");

        KTrussAnalyzer analyzer = new KTrussAnalyzer(graph);
        Map<Integer, Integer> dist = analyzer.getTrussDistribution();
        assertFalse(dist.isEmpty());
    }

    @Test
    public void testTrussHierarchy() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        addEdge("C", "D");

        KTrussAnalyzer analyzer = new KTrussAnalyzer(graph);
        Map<Integer, List<Edge>> hierarchy = analyzer.getTrussHierarchy();
        assertFalse(hierarchy.isEmpty());
    }

    @Test
    public void testCompareTrussVsCore() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
        addEdge("C", "D");
        addEdge("D", "E");

        KTrussAnalyzer analyzer = new KTrussAnalyzer(graph);
        List<String> report = analyzer.compareTrussVsCore();
        assertFalse(report.isEmpty());
        assertTrue(report.get(0).contains("K-Truss vs K-Core"));
    }

    @Test
    public void testSummary() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");

        KTrussAnalyzer analyzer = new KTrussAnalyzer(graph);
        String summary = analyzer.getSummary();
        assertTrue(summary.contains("K-Truss Decomposition"));
        assertTrue(summary.contains("Max truss number"));
    }
}
