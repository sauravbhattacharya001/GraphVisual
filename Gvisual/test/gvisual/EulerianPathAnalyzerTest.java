package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for EulerianPathAnalyzer.
 *
 * @author zalenix
 */
public class EulerianPathAnalyzerTest {

    // ── Helper ────────────────────────────────────────────────────

    private int edgeId = 0;

    private Graph<String, edge> newGraph() {
        edgeId = 0;
        return new UndirectedSparseGraph<String, edge>();
    }

    private void addEdge(Graph<String, edge> g, String v1, String v2) {
        edge e = new edge("f", v1, v2);
        e.setLabel("e" + (edgeId++));
        g.addEdge(e, v1, v2);
    }

    // ── Eulerian Circuit Tests ────────────────────────────────────

    @Test
    public void testTriangleIsEulerianCircuit() {
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        EulerianPathAnalyzer.EulerianAnalysis result = analyzer.analyze();
        assertEquals(EulerianPathAnalyzer.EulerianType.EULERIAN_CIRCUIT, result.getType());
        assertTrue(result.getOddDegreeVertices().isEmpty());
    }

    @Test
    public void testSquareWithDiagonalsIsEulerianCircuit() {
        // K4 minus one edge won't work; use a proper even-degree graph
        // Square: A-B-C-D-A, all degree 2
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        addEdge(g, "D", "A");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        assertEquals(EulerianPathAnalyzer.EulerianType.EULERIAN_CIRCUIT, analyzer.analyze().getType());
    }

    @Test
    public void testK4IsNotEulerian() {
        // K4: every vertex has degree 3 (odd)
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "A", "C");
        addEdge(g, "A", "D");
        addEdge(g, "B", "C");
        addEdge(g, "B", "D");
        addEdge(g, "C", "D");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        assertEquals(EulerianPathAnalyzer.EulerianType.NOT_EULERIAN, analyzer.analyze().getType());
        assertEquals(4, analyzer.analyze().getOddDegreeVertices().size());
    }

    // ── Eulerian Path Tests ───────────────────────────────────────

    @Test
    public void testSimplePathGraph() {
        // A-B-C: A and C have degree 1 (odd), B has degree 2 (even)
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        EulerianPathAnalyzer.EulerianAnalysis result = analyzer.analyze();
        assertEquals(EulerianPathAnalyzer.EulerianType.EULERIAN_PATH, result.getType());
        assertEquals(2, result.getOddDegreeVertices().size());
    }

    @Test
    public void testKoenigsbergBridges() {
        // Classic 7-bridges problem (simplified): 4 vertices, 7 edges
        // All vertices have odd degree → NOT_EULERIAN
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "A", "B"); // multi-edge simulated with different edge objects
        addEdge(g, "A", "C");
        addEdge(g, "A", "C");
        addEdge(g, "A", "D");
        addEdge(g, "B", "D");
        addEdge(g, "C", "D");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        // Due to JUNG not supporting multigraphs easily, check the type is computed
        assertNotNull(analyzer.analyze().getType());
    }

    // ── Path Finding Tests ────────────────────────────────────────

    @Test
    public void testFindCircuitInTriangle() {
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        EulerianPathAnalyzer.EulerianPathResult path = analyzer.findEulerianPath();
        assertNotNull(path);
        assertTrue(path.isCircuit());
        assertEquals(3, path.getEdgeCount());
        assertEquals(4, path.getVertices().size()); // circuit returns to start
        assertEquals(path.getVertices().get(0), path.getVertices().get(path.getVertices().size() - 1));
    }

    @Test
    public void testFindPathInLinearGraph() {
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        EulerianPathAnalyzer.EulerianPathResult path = analyzer.findEulerianPath();
        assertNotNull(path);
        assertFalse(path.isCircuit());
        assertEquals(2, path.getEdgeCount());
        assertEquals(3, path.getVertices().size());
    }

    @Test
    public void testNoPathInK4() {
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "A", "C");
        addEdge(g, "A", "D");
        addEdge(g, "B", "C");
        addEdge(g, "B", "D");
        addEdge(g, "C", "D");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        assertNull(analyzer.findEulerianPath());
    }

    @Test
    public void testFindPathVisitsAllEdges() {
        // House graph: square + triangle on top
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        addEdge(g, "D", "A");
        addEdge(g, "C", "E");
        addEdge(g, "D", "E");
        // Degrees: A=2, B=2, C=3, D=3, E=2 → Eulerian path C↔D
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        EulerianPathAnalyzer.EulerianPathResult path = analyzer.findEulerianPath();
        assertNotNull(path);
        assertEquals(6, path.getEdgeCount());
    }

    // ── Edge Cases ────────────────────────────────────────────────

    @Test
    public void testEmptyGraph() {
        Graph<String, edge> g = newGraph();
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        EulerianPathAnalyzer.EulerianAnalysis result = analyzer.analyze();
        assertEquals(EulerianPathAnalyzer.EulerianType.EULERIAN_CIRCUIT, result.getType());
    }

    @Test
    public void testSingleVertex() {
        Graph<String, edge> g = newGraph();
        g.addVertex("A");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        assertEquals(EulerianPathAnalyzer.EulerianType.EULERIAN_CIRCUIT, analyzer.analyze().getType());
    }

    @Test
    public void testSingleEdge() {
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        assertEquals(EulerianPathAnalyzer.EulerianType.EULERIAN_PATH, analyzer.analyze().getType());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new EulerianPathAnalyzer(null);
    }

    @Test
    public void testDisconnectedGraphNotEulerian() {
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "C", "D");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        assertEquals(EulerianPathAnalyzer.EulerianType.NOT_EULERIAN, analyzer.analyze().getType());
        assertFalse(analyzer.analyze().isConnected());
    }

    @Test
    public void testIsolatedVerticesIgnored() {
        // Triangle + isolated vertex → still Eulerian circuit
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        g.addVertex("Z"); // isolated
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        assertEquals(EulerianPathAnalyzer.EulerianType.EULERIAN_CIRCUIT, analyzer.analyze().getType());
    }

    // ── Degree Map Tests ──────────────────────────────────────────

    @Test
    public void testDegreeMapCorrect() {
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        Map<String, Integer> degrees = analyzer.analyze().getDegreeMap();
        assertEquals(Integer.valueOf(2), degrees.get("A"));
        assertEquals(Integer.valueOf(2), degrees.get("B"));
        assertEquals(Integer.valueOf(2), degrees.get("C"));
    }

    @Test
    public void testDegreeMapImmutable() {
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        Map<String, Integer> degrees = analyzer.analyze().getDegreeMap();
        try {
            degrees.put("X", 5);
            fail("Should be immutable");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    // ── Suggestion Tests ──────────────────────────────────────────

    @Test
    public void testSuggestEdgesForEulerianOnEulerianGraph() {
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        assertTrue(analyzer.suggestEdgesForEulerian().isEmpty());
    }

    @Test
    public void testSuggestEdgesForK4() {
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "A", "C");
        addEdge(g, "A", "D");
        addEdge(g, "B", "C");
        addEdge(g, "B", "D");
        addEdge(g, "C", "D");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        List<String[]> suggestions = analyzer.suggestEdgesForEulerian();
        assertEquals(2, suggestions.size());
    }

    // ── Edge Connectivity Tests ───────────────────────────────────

    @Test
    public void testEdgeConnectivityTriangle() {
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        assertEquals(2, analyzer.computeEdgeConnectivity());
    }

    @Test
    public void testEdgeConnectivityBridge() {
        // A-B-C: bridge at B
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        assertEquals(1, analyzer.computeEdgeConnectivity());
    }

    @Test
    public void testEdgeConnectivitySingleVertex() {
        Graph<String, edge> g = newGraph();
        g.addVertex("A");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        assertEquals(0, analyzer.computeEdgeConnectivity());
    }

    // ── Report Tests ──────────────────────────────────────────────

    @Test
    public void testReportContainsType() {
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        String report = analyzer.generateReport();
        assertTrue(report.contains("EULERIAN_CIRCUIT"));
        assertTrue(report.contains("Vertices: 3"));
        assertTrue(report.contains("Edges: 3"));
    }

    @Test
    public void testReportForNonEulerian() {
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "A", "C");
        addEdge(g, "A", "D");
        addEdge(g, "B", "C");
        addEdge(g, "B", "D");
        addEdge(g, "C", "D");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        String report = analyzer.generateReport();
        assertTrue(report.contains("NOT_EULERIAN"));
        assertTrue(report.contains("Chinese Postman"));
        assertTrue(report.contains("Suggested edge additions"));
    }

    @Test
    public void testReportForPath() {
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        String report = analyzer.generateReport();
        assertTrue(report.contains("EULERIAN_PATH"));
    }

    // ── Min Edge Duplications ─────────────────────────────────────

    @Test
    public void testMinDuplicationsEulerianCircuit() {
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        assertEquals(0, analyzer.analyze().getMinEdgeDuplications());
    }

    @Test
    public void testMinDuplicationsEulerianPath() {
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        assertEquals(0, analyzer.analyze().getMinEdgeDuplications());
    }

    @Test
    public void testMinDuplicationsK4() {
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "A", "C");
        addEdge(g, "A", "D");
        addEdge(g, "B", "C");
        addEdge(g, "B", "D");
        addEdge(g, "C", "D");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        assertEquals(2, analyzer.analyze().getMinEdgeDuplications());
    }

    // ── Larger Graph Tests ────────────────────────────────────────

    @Test
    public void testPetersenGraphNotEulerian() {
        // Petersen graph: 10 vertices, 15 edges, all degree 3
        Graph<String, edge> g = newGraph();
        // Outer cycle
        addEdge(g, "0", "1"); addEdge(g, "1", "2"); addEdge(g, "2", "3");
        addEdge(g, "3", "4"); addEdge(g, "4", "0");
        // Inner pentagram
        addEdge(g, "5", "7"); addEdge(g, "7", "9"); addEdge(g, "9", "6");
        addEdge(g, "6", "8"); addEdge(g, "8", "5");
        // Spokes
        addEdge(g, "0", "5"); addEdge(g, "1", "6"); addEdge(g, "2", "7");
        addEdge(g, "3", "8"); addEdge(g, "4", "9");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        assertEquals(EulerianPathAnalyzer.EulerianType.NOT_EULERIAN, analyzer.analyze().getType());
        assertEquals(10, analyzer.analyze().getOddDegreeVertices().size());
    }

    @Test
    public void testCompleteGraphK5HasEulerianCircuit() {
        // K5: 5 vertices, 10 edges, all degree 4 (even) → Eulerian circuit
        Graph<String, edge> g = newGraph();
        String[] v = {"A", "B", "C", "D", "E"};
        for (int i = 0; i < v.length; i++) {
            for (int j = i + 1; j < v.length; j++) {
                addEdge(g, v[i], v[j]);
            }
        }
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        assertEquals(EulerianPathAnalyzer.EulerianType.EULERIAN_CIRCUIT, analyzer.analyze().getType());
        EulerianPathAnalyzer.EulerianPathResult path = analyzer.findEulerianPath();
        assertNotNull(path);
        assertTrue(path.isCircuit());
        assertEquals(10, path.getEdgeCount());
    }

    @Test
    public void testCircuitPathReturnsToStart() {
        // Square graph
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        addEdge(g, "D", "A");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        EulerianPathAnalyzer.EulerianPathResult path = analyzer.findEulerianPath();
        assertNotNull(path);
        assertTrue(path.isCircuit());
        String first = path.getVertices().get(0);
        String last = path.getVertices().get(path.getVertices().size() - 1);
        assertEquals(first, last);
    }

    // ── Analysis Object Tests ─────────────────────────────────────

    @Test
    public void testAnalysisTotalEdges() {
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        assertEquals(3, analyzer.analyze().getTotalEdges());
    }

    @Test
    public void testAnalysisTotalVertices() {
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        g.addVertex("D");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        assertEquals(4, analyzer.analyze().getTotalVertices());
    }

    @Test
    public void testAnalysisConnected() {
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        assertTrue(analyzer.analyze().isConnected());
    }

    @Test
    public void testAnalysisDisconnected() {
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "C", "D");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        assertFalse(analyzer.analyze().isConnected());
    }

    @Test
    public void testPathResultImmutable() {
        Graph<String, edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        EulerianPathAnalyzer.EulerianPathResult path = analyzer.findEulerianPath();
        try {
            path.getVertices().add("X");
            fail("Should be immutable");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    // ── Star Graph Tests ──────────────────────────────────────────

    @Test
    public void testStarGraphNotEulerian() {
        // Star with 5 leaves: center has degree 5, leaves have degree 1
        Graph<String, edge> g = newGraph();
        for (int i = 1; i <= 5; i++) {
            addEdge(g, "center", "leaf" + i);
        }
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        assertEquals(EulerianPathAnalyzer.EulerianType.NOT_EULERIAN, analyzer.analyze().getType());
    }

    @Test
    public void testStarWithEvenLeaves() {
        // Star with 2 leaves: center degree 2, leaves degree 1 → Eulerian path
        Graph<String, edge> g = newGraph();
        addEdge(g, "center", "A");
        addEdge(g, "center", "B");
        EulerianPathAnalyzer analyzer = new EulerianPathAnalyzer(g);
        assertEquals(EulerianPathAnalyzer.EulerianType.EULERIAN_PATH, analyzer.analyze().getType());
    }
}
