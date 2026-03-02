package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link GraphDiffAnalyzer}.
 */
public class GraphDiffAnalyzerTest {

    private Graph<String, edge> graphA;
    private Graph<String, edge> graphB;

    @Before
    public void setUp() {
        graphA = new UndirectedSparseGraph<>();
        graphB = new UndirectedSparseGraph<>();
    }

    private edge addEdge(Graph<String, edge> g, String v1, String v2, String type) {
        if (!g.containsVertex(v1)) g.addVertex(v1);
        if (!g.containsVertex(v2)) g.addVertex(v2);
        edge e = new edge(type, v1, v2);
        g.addEdge(e, v1, v2);
        return e;
    }

    @Test
    public void testIdenticalGraphs() {
        addEdge(graphA, "A", "B", "f");
        addEdge(graphA, "B", "C", "f");
        addEdge(graphB, "A", "B", "f");
        addEdge(graphB, "B", "C", "f");

        GraphDiffAnalyzer analyzer = new GraphDiffAnalyzer(graphA, graphB);
        GraphDiffAnalyzer.DiffResult result = analyzer.computeDiff();

        assertTrue(result.isIdentical());
        assertEquals(3, result.getCommonNodes().size());
        assertEquals(2, result.getCommonEdges().size());
        assertTrue(result.getAddedNodes().isEmpty());
        assertTrue(result.getRemovedNodes().isEmpty());
        assertEquals(1.0, result.getNodeJaccard(), 0.001);
        assertEquals(1.0, result.getEdgeJaccard(), 0.001);
    }

    @Test
    public void testCompletelyDifferentGraphs() {
        addEdge(graphA, "A", "B", "f");
        addEdge(graphB, "C", "D", "f");

        GraphDiffAnalyzer analyzer = new GraphDiffAnalyzer(graphA, graphB);
        GraphDiffAnalyzer.DiffResult result = analyzer.computeDiff();

        assertFalse(result.isIdentical());
        assertEquals(2, result.getAddedNodes().size());
        assertEquals(2, result.getRemovedNodes().size());
        assertTrue(result.getCommonNodes().isEmpty());
        assertEquals(0.0, result.getNodeJaccard(), 0.001);
    }

    @Test
    public void testAddedNodesAndEdges() {
        addEdge(graphA, "A", "B", "f");
        addEdge(graphB, "A", "B", "f");
        addEdge(graphB, "B", "C", "f");

        GraphDiffAnalyzer analyzer = new GraphDiffAnalyzer(graphA, graphB);
        GraphDiffAnalyzer.DiffResult result = analyzer.computeDiff();

        assertEquals(1, result.getAddedNodes().size());
        assertTrue(result.getAddedNodes().contains("C"));
        assertTrue(result.getRemovedNodes().isEmpty());
        assertEquals(1, result.getAddedEdges().size());
    }

    @Test
    public void testRemovedNodesAndEdges() {
        addEdge(graphA, "A", "B", "f");
        addEdge(graphA, "B", "C", "f");
        addEdge(graphB, "A", "B", "f");

        GraphDiffAnalyzer analyzer = new GraphDiffAnalyzer(graphA, graphB);
        GraphDiffAnalyzer.DiffResult result = analyzer.computeDiff();

        assertEquals(1, result.getRemovedNodes().size());
        assertTrue(result.getRemovedNodes().contains("C"));
        assertEquals(1, result.getRemovedEdges().size());
    }

    @Test
    public void testEditDistance() {
        addEdge(graphA, "A", "B", "f");
        addEdge(graphA, "B", "C", "f");
        addEdge(graphB, "A", "B", "f");
        addEdge(graphB, "B", "D", "f");

        GraphDiffAnalyzer analyzer = new GraphDiffAnalyzer(graphA, graphB);
        // removed: C, edge B-C; added: D, edge B-D = 4
        assertEquals(4, analyzer.computeEditDistance());
    }

    @Test
    public void testDegreeChanges() {
        addEdge(graphA, "A", "B", "f");
        addEdge(graphB, "A", "B", "f");
        addEdge(graphB, "A", "C", "f");

        GraphDiffAnalyzer analyzer = new GraphDiffAnalyzer(graphA, graphB);
        Map<String, int[]> changes = analyzer.findDegreeChanges();

        assertTrue(changes.containsKey("A"));
        assertEquals(1, changes.get("A")[0]); // degree in A
        assertEquals(2, changes.get("A")[1]); // degree in B
        assertFalse(changes.containsKey("B")); // B has degree 1 in both
    }

    @Test
    public void testEmptyGraphs() {
        GraphDiffAnalyzer analyzer = new GraphDiffAnalyzer(graphA, graphB);
        GraphDiffAnalyzer.DiffResult result = analyzer.computeDiff();

        assertTrue(result.isIdentical());
        assertEquals(1.0, result.getNodeJaccard(), 0.001);
        assertEquals(1.0, result.getEdgeJaccard(), 0.001);
    }

    @Test
    public void testSummaryOutput() {
        addEdge(graphA, "A", "B", "f");
        addEdge(graphB, "A", "C", "f");

        GraphDiffAnalyzer analyzer = new GraphDiffAnalyzer(graphA, graphB);
        GraphDiffAnalyzer.DiffResult result = analyzer.computeDiff();
        String summary = result.getSummary();

        assertTrue(summary.contains("Graph Diff Summary"));
        assertTrue(summary.contains("added"));
        assertTrue(summary.contains("Jaccard"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphA() {
        new GraphDiffAnalyzer(null, graphB);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphB() {
        new GraphDiffAnalyzer(graphA, null);
    }
}
