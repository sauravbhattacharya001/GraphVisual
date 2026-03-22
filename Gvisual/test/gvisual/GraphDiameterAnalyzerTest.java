package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests for GraphDiameterAnalyzer.
 */
public class GraphDiameterAnalyzerTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, Edge>();
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullGraphThrows() {
        new GraphDiameterAnalyzer(null);
    }

    @Test(expected = IllegalStateException.class)
    public void queryBeforeAnalyzeThrows() {
        GraphDiameterAnalyzer analyzer = new GraphDiameterAnalyzer(graph);
        analyzer.getDiameter();
    }

    @Test
    public void emptyGraph() {
        GraphDiameterAnalyzer analyzer = new GraphDiameterAnalyzer(graph);
        analyzer.analyze();
        assertEquals(0, analyzer.getDiameter());
        assertEquals(0, analyzer.getRadius());
        assertEquals(0, analyzer.getLargestComponentSize());
    }

    @Test
    public void singleVertex() {
        graph.addVertex("A");
        GraphDiameterAnalyzer analyzer = new GraphDiameterAnalyzer(graph);
        analyzer.analyze();
        assertEquals(0, analyzer.getDiameter());
        assertEquals(0, analyzer.getRadius());
        assertEquals(1, analyzer.getLargestComponentSize());
        assertTrue(analyzer.getCenterVertices().contains("A"));
    }

    @Test
    public void linearGraph() {
        // A - B - C - D (path graph, diameter = 3)
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("D");
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "B", "C"), "B", "C");
        graph.addEdge(new Edge("f", "C", "D"), "C", "D");

        GraphDiameterAnalyzer analyzer = new GraphDiameterAnalyzer(graph);
        analyzer.analyze();

        assertEquals(3, analyzer.getDiameter());
        assertEquals(2, analyzer.getRadius());
        // Center should be B and C (eccentricity 2)
        assertTrue(analyzer.getCenterVertices().contains("B"));
        assertTrue(analyzer.getCenterVertices().contains("C"));
        // Periphery should be A and D (eccentricity 3)
        assertTrue(analyzer.getPeripheryVertices().contains("A"));
        assertTrue(analyzer.getPeripheryVertices().contains("D"));
    }

    @Test
    public void completeGraph() {
        // K4: all connected, diameter = 1, radius = 1
        String[] verts = {"A", "B", "C", "D"};
        for (String v : verts) graph.addVertex(v);
        int edgeId = 0;
        for (int i = 0; i < verts.length; i++) {
            for (int j = i + 1; j < verts.length; j++) {
                graph.addEdge(new Edge("f", verts[i], verts[j]), verts[i], verts[j]);
            }
        }

        GraphDiameterAnalyzer analyzer = new GraphDiameterAnalyzer(graph);
        analyzer.analyze();

        assertEquals(1, analyzer.getDiameter());
        assertEquals(1, analyzer.getRadius());
        assertEquals(4, analyzer.getCenterVertices().size());
        assertEquals(4, analyzer.getPeripheryVertices().size());
    }

    @Test
    public void starGraph() {
        // Center hub connected to 4 leaves: diameter=2, radius=1
        graph.addVertex("H");
        for (int i = 1; i <= 4; i++) {
            String leaf = "L" + i;
            graph.addVertex(leaf);
            graph.addEdge(new Edge("f", "H", leaf), "H", leaf);
        }

        GraphDiameterAnalyzer analyzer = new GraphDiameterAnalyzer(graph);
        analyzer.analyze();

        assertEquals(2, analyzer.getDiameter());
        assertEquals(1, analyzer.getRadius());
        assertEquals(1, analyzer.getCenterVertices().size());
        assertTrue(analyzer.getCenterVertices().contains("H"));
        assertEquals(4, analyzer.getPeripheryVertices().size());
    }

    @Test
    public void disconnectedGraphUsesLargestComponent() {
        // Component 1: A-B-C (3 vertices)
        // Component 2: X-Y (2 vertices)
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addVertex("X");
        graph.addVertex("Y");
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "B", "C"), "B", "C");
        graph.addEdge(new Edge("f", "X", "Y"), "X", "Y");

        GraphDiameterAnalyzer analyzer = new GraphDiameterAnalyzer(graph);
        analyzer.analyze();

        assertEquals(3, analyzer.getLargestComponentSize());
        assertEquals(2, analyzer.getDiameter());
        assertEquals(1, analyzer.getRadius());
        // X and Y should not be in the eccentricity map
        assertEquals(-1, analyzer.getEccentricity("X"));
    }

    @Test
    public void eccentricityValues() {
        // A-B-C: eccentricities A=2, B=1, C=2
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "B", "C"), "B", "C");

        GraphDiameterAnalyzer analyzer = new GraphDiameterAnalyzer(graph);
        analyzer.analyze();

        assertEquals(2, analyzer.getEccentricity("A"));
        assertEquals(1, analyzer.getEccentricity("B"));
        assertEquals(2, analyzer.getEccentricity("C"));
    }

    @Test
    public void summaryContainsKey() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");

        GraphDiameterAnalyzer analyzer = new GraphDiameterAnalyzer(graph);
        analyzer.analyze();

        String summary = analyzer.getSummary();
        assertTrue(summary.contains("Diameter:"));
        assertTrue(summary.contains("Radius:"));
        assertTrue(summary.contains("Center"));
        assertTrue(summary.contains("Periphery"));
    }

    @Test
    public void rankedByEccentricityIsSorted() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");
        graph.addEdge(new Edge("f", "B", "C"), "B", "C");

        GraphDiameterAnalyzer analyzer = new GraphDiameterAnalyzer(graph);
        analyzer.analyze();

        java.util.List<Map.Entry<String, Integer>> ranked = analyzer.getRankedByEccentricity();
        // First should have lowest eccentricity
        assertEquals("B", ranked.get(0).getKey());
        assertEquals(1, (int) ranked.get(0).getValue());
    }
}
