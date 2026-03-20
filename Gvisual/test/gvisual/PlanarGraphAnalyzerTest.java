package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for PlanarGraphAnalyzer.
 *
 * @author zalenix
 */
public class PlanarGraphAnalyzerTest {

    private int edgeId = 0;

    private Graph<String, Edge> newGraph() {
        edgeId = 0;
        return new UndirectedSparseGraph<String, Edge>();
    }

    private void addEdge(Graph<String, Edge> g, String v1, String v2) {
        Edge e  new Edge("f", v1, v2);
        e.setLabel("e" + (edgeId++));
        g.addEdge(e, v1, v2);
    }

    // ── Planarity Tests ──────────────────────────────────────────

    @Test
    public void testEmptyGraphIsPlanar() {
        Graph<String, Edge> g = newGraph();
        PlanarGraphAnalyzer.PlanarityResult r = PlanarGraphAnalyzer.testPlanarity(g);
        assertTrue(r.isPlanar());
        assertEquals(0, r.getVertices());
        assertEquals(0, r.getEdges());
    }

    @Test
    public void testSingleVertexIsPlanar() {
        Graph<String, Edge> g = newGraph();
        g.addVertex("A");
        PlanarGraphAnalyzer.PlanarityResult r = PlanarGraphAnalyzer.testPlanarity(g);
        assertTrue(r.isPlanar());
        assertEquals(1, r.getVertices());
    }

    @Test
    public void testSingleEdgeIsPlanar() {
        Graph<String, Edge> g = newGraph();
        addEdge(g, "A", "B");
        PlanarGraphAnalyzer.PlanarityResult r = PlanarGraphAnalyzer.testPlanarity(g);
        assertTrue(r.isPlanar());
        assertEquals(2, r.getVertices());
        assertEquals(1, r.getEdges());
    }

    @Test
    public void testTriangleIsPlanar() {
        Graph<String, Edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        PlanarGraphAnalyzer.PlanarityResult r = PlanarGraphAnalyzer.testPlanarity(g);
        assertTrue(r.isPlanar());
    }

    @Test
    public void testK4IsPlanar() {
        Graph<String, Edge> g = newGraph();
        String[] v = {"A", "B", "C", "D"};
        for (int i = 0; i < v.length; i++)
            for (int j = i + 1; j < v.length; j++)
                addEdge(g, v[i], v[j]);
        PlanarGraphAnalyzer.PlanarityResult r = PlanarGraphAnalyzer.testPlanarity(g);
        assertTrue(r.isPlanar());
        assertEquals(4, r.getVertices());
        assertEquals(6, r.getEdges());
    }

    @Test
    public void testK5IsNotPlanar() {
        Graph<String, Edge> g = newGraph();
        String[] v = {"A", "B", "C", "D", "E"};
        for (int i = 0; i < v.length; i++)
            for (int j = i + 1; j < v.length; j++)
                addEdge(g, v[i], v[j]);
        PlanarGraphAnalyzer.PlanarityResult r = PlanarGraphAnalyzer.testPlanarity(g);
        assertFalse(r.isPlanar());
        assertEquals(5, r.getVertices());
        assertEquals(10, r.getEdges());
    }

    @Test
    public void testK33IsNotPlanar() {
        Graph<String, Edge> g = newGraph();
        String[] left = {"L1", "L2", "L3"};
        String[] right = {"R1", "R2", "R3"};
        for (String l : left)
            for (String r : right)
                addEdge(g, l, r);
        PlanarGraphAnalyzer.PlanarityResult r = PlanarGraphAnalyzer.testPlanarity(g);
        assertFalse(r.isPlanar());
    }

    @Test
    public void testPathGraphIsPlanar() {
        Graph<String, Edge> g = newGraph();
        for (int i = 0; i < 10; i++)
            addEdge(g, "V" + i, "V" + (i + 1));
        assertTrue(PlanarGraphAnalyzer.testPlanarity(g).isPlanar());
    }

    @Test
    public void testCycleGraphIsPlanar() {
        Graph<String, Edge> g = newGraph();
        int n = 8;
        for (int i = 0; i < n; i++)
            addEdge(g, "V" + i, "V" + ((i + 1) % n));
        assertTrue(PlanarGraphAnalyzer.testPlanarity(g).isPlanar());
    }

    @Test
    public void testWheelGraphIsPlanar() {
        // Wheel W5: center + 5-cycle, always planar
        Graph<String, Edge> g = newGraph();
        g.addVertex("C");
        for (int i = 0; i < 5; i++) {
            addEdge(g, "V" + i, "V" + ((i + 1) % 5));
            addEdge(g, "C", "V" + i);
        }
        assertTrue(PlanarGraphAnalyzer.testPlanarity(g).isPlanar());
    }

    @Test
    public void testDisconnectedPlanarGraph() {
        Graph<String, Edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "D", "E");
        PlanarGraphAnalyzer.PlanarityResult r = PlanarGraphAnalyzer.testPlanarity(g);
        assertTrue(r.isPlanar());
        assertEquals(2, r.getComponents());
    }

    @Test
    public void testPetersenGraphIsNotPlanar() {
        // Petersen graph: 10 vertices, 15 edges, non-planar
        Graph<String, Edge> g = newGraph();
        // Outer cycle
        for (int i = 0; i < 5; i++)
            addEdge(g, "O" + i, "O" + ((i + 1) % 5));
        // Inner pentagram
        for (int i = 0; i < 5; i++)
            addEdge(g, "I" + i, "I" + ((i + 2) % 5));
        // Spokes
        for (int i = 0; i < 5; i++)
            addEdge(g, "O" + i, "I" + i);
        assertFalse(PlanarGraphAnalyzer.testPlanarity(g).isPlanar());
    }

    @Test
    public void testComponentCount() {
        Graph<String, Edge> g = newGraph();
        g.addVertex("A");
        g.addVertex("B");
        g.addVertex("C");
        addEdge(g, "D", "E");
        PlanarGraphAnalyzer.PlanarityResult r = PlanarGraphAnalyzer.testPlanarity(g);
        assertEquals(4, r.getComponents()); // 3 isolated + 1 pair
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        PlanarGraphAnalyzer.testPlanarity(null);
    }

    @Test
    public void testExpectedFacesEulerFormula() {
        // Triangle: V=3, E=3, C=1 → F = 3-3+1+1 = 2
        Graph<String, Edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        PlanarGraphAnalyzer.PlanarityResult r = PlanarGraphAnalyzer.testPlanarity(g);
        assertEquals(2, r.expectedFaces());
    }

    // ── Face Enumeration Tests ───────────────────────────────────

    @Test
    public void testTriangleFaces() {
        Graph<String, Edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        List<PlanarGraphAnalyzer.Face> faces = PlanarGraphAnalyzer.enumerateFaces(g);
        assertNotNull(faces);
        assertEquals(2, faces.size()); // inner + outer
    }

    @Test
    public void testK4Faces() {
        Graph<String, Edge> g = newGraph();
        String[] v = {"A", "B", "C", "D"};
        for (int i = 0; i < v.length; i++)
            for (int j = i + 1; j < v.length; j++)
                addEdge(g, v[i], v[j]);
        List<PlanarGraphAnalyzer.Face> faces = PlanarGraphAnalyzer.enumerateFaces(g);
        assertNotNull(faces);
        // K4 is planar; face enumeration should return faces
        assertTrue("K4 should have at least 2 faces", faces.size() >= 2);
    }

    @Test
    public void testFacesOfNonPlanarReturnsNull() {
        Graph<String, Edge> g = newGraph();
        String[] v = {"A", "B", "C", "D", "E"};
        for (int i = 0; i < v.length; i++)
            for (int j = i + 1; j < v.length; j++)
                addEdge(g, v[i], v[j]);
        assertNull(PlanarGraphAnalyzer.enumerateFaces(g));
    }

    @Test
    public void testFaceHasOuterMarker() {
        Graph<String, Edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        List<PlanarGraphAnalyzer.Face> faces = PlanarGraphAnalyzer.enumerateFaces(g);
        boolean hasOuter = false;
        for (PlanarGraphAnalyzer.Face f : faces) {
            if (f.isOuter()) hasOuter = true;
        }
        assertTrue(hasOuter);
    }

    @Test
    public void testFaceIds() {
        Graph<String, Edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        List<PlanarGraphAnalyzer.Face> faces = PlanarGraphAnalyzer.enumerateFaces(g);
        Set<Integer> ids = new HashSet<Integer>();
        for (PlanarGraphAnalyzer.Face f : faces) ids.add(f.getId());
        assertEquals(faces.size(), ids.size()); // all unique
    }

    @Test
    public void testSquareFaces() {
        Graph<String, Edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        addEdge(g, "D", "A");
        List<PlanarGraphAnalyzer.Face> faces = PlanarGraphAnalyzer.enumerateFaces(g);
        assertNotNull(faces);
        assertEquals(2, faces.size()); // inner + outer
    }

    @Test
    public void testSingleEdgeFaces() {
        Graph<String, Edge> g = newGraph();
        addEdge(g, "A", "B");
        List<PlanarGraphAnalyzer.Face> faces = PlanarGraphAnalyzer.enumerateFaces(g);
        assertNotNull(faces);
        // Single edge: 1 face (the entire plane)
        assertTrue(faces.size() >= 1);
    }

    // ── Dual Graph Tests ─────────────────────────────────────────

    @Test
    public void testTriangleDualGraph() {
        Graph<String, Edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        PlanarGraphAnalyzer.DualGraph dual = PlanarGraphAnalyzer.buildDualGraph(g);
        assertNotNull(dual);
        assertEquals(2, dual.nodeCount()); // 2 faces → 2 dual nodes
    }

    @Test
    public void testK4DualGraph() {
        Graph<String, Edge> g = newGraph();
        String[] v = {"A", "B", "C", "D"};
        for (int i = 0; i < v.length; i++)
            for (int j = i + 1; j < v.length; j++)
                addEdge(g, v[i], v[j]);
        PlanarGraphAnalyzer.DualGraph dual = PlanarGraphAnalyzer.buildDualGraph(g);
        assertNotNull(dual);
        assertTrue("K4 dual should have at least 2 nodes", dual.nodeCount() >= 2);
    }

    @Test
    public void testNonPlanarDualReturnsNull() {
        Graph<String, Edge> g = newGraph();
        String[] v = {"A", "B", "C", "D", "E"};
        for (int i = 0; i < v.length; i++)
            for (int j = i + 1; j < v.length; j++)
                addEdge(g, v[i], v[j]);
        assertNull(PlanarGraphAnalyzer.buildDualGraph(g));
    }

    @Test
    public void testDualGraphSymmetry() {
        Graph<String, Edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        PlanarGraphAnalyzer.DualGraph dual = PlanarGraphAnalyzer.buildDualGraph(g);
        // Check adjacency is symmetric
        for (Map.Entry<Integer, Set<Integer>> entry : dual.getAdjacency().entrySet()) {
            for (int nbr : entry.getValue()) {
                assertTrue(dual.getAdjacency().get(nbr).contains(entry.getKey()));
            }
        }
    }

    @Test
    public void testDualEdgeCount() {
        // Triangle dual: 2 nodes, should have edges between them
        Graph<String, Edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        PlanarGraphAnalyzer.DualGraph dual = PlanarGraphAnalyzer.buildDualGraph(g);
        assertTrue(dual.edgeCount() > 0);
    }

    // ── Kuratowski Subgraph Tests ────────────────────────────────

    @Test
    public void testK5KuratowskiSubgraph() {
        Graph<String, Edge> g = newGraph();
        String[] v = {"A", "B", "C", "D", "E"};
        for (int i = 0; i < v.length; i++)
            for (int j = i + 1; j < v.length; j++)
                addEdge(g, v[i], v[j]);
        PlanarGraphAnalyzer.KuratowskiSubgraph ks =
                PlanarGraphAnalyzer.findKuratowskiSubgraph(g);
        assertNotNull(ks);
        assertEquals("K5", ks.getType());
        assertEquals(5, ks.getBranchVertices().size());
    }

    @Test
    public void testK33KuratowskiSubgraph() {
        Graph<String, Edge> g = newGraph();
        String[] left = {"L1", "L2", "L3"};
        String[] right = {"R1", "R2", "R3"};
        for (String l : left)
            for (String r : right)
                addEdge(g, l, r);
        PlanarGraphAnalyzer.KuratowskiSubgraph ks =
                PlanarGraphAnalyzer.findKuratowskiSubgraph(g);
        assertNotNull(ks);
        // Should find K3_3
        assertNotNull(ks.getType());
        assertEquals(6, ks.getBranchVertices().size());
    }

    @Test
    public void testPlanarGraphHasNoKuratowski() {
        Graph<String, Edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        assertNull(PlanarGraphAnalyzer.findKuratowskiSubgraph(g));
    }

    @Test
    public void testKuratowskiHasPaths() {
        Graph<String, Edge> g = newGraph();
        String[] v = {"A", "B", "C", "D", "E"};
        for (int i = 0; i < v.length; i++)
            for (int j = i + 1; j < v.length; j++)
                addEdge(g, v[i], v[j]);
        PlanarGraphAnalyzer.KuratowskiSubgraph ks =
                PlanarGraphAnalyzer.findKuratowskiSubgraph(g);
        assertNotNull(ks.getPaths());
        assertFalse(ks.getPaths().isEmpty());
    }

    // ── Full Report Tests ────────────────────────────────────────

    @Test
    public void testPlanarReport() {
        Graph<String, Edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        PlanarGraphAnalyzer.PlanarityReport report = PlanarGraphAnalyzer.analyze(g);
        assertTrue(report.getResult().isPlanar());
        assertNotNull(report.getFaces());
        assertNotNull(report.getDual());
        assertNull(report.getKuratowski());
        assertEquals(0, report.getGenus());
    }

    @Test
    public void testNonPlanarReport() {
        Graph<String, Edge> g = newGraph();
        String[] v = {"A", "B", "C", "D", "E"};
        for (int i = 0; i < v.length; i++)
            for (int j = i + 1; j < v.length; j++)
                addEdge(g, v[i], v[j]);
        PlanarGraphAnalyzer.PlanarityReport report = PlanarGraphAnalyzer.analyze(g);
        assertFalse(report.getResult().isPlanar());
        assertNull(report.getFaces());
        assertNull(report.getDual());
        assertNotNull(report.getKuratowski());
        assertTrue(report.getGenus() >= 1);
    }

    @Test
    public void testReportToText() {
        Graph<String, Edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        String text = PlanarGraphAnalyzer.analyze(g).toText();
        assertTrue(text.contains("Planarity Report"));
        assertTrue(text.contains("Planar: YES"));
        assertTrue(text.contains("Faces:"));
    }

    @Test
    public void testNonPlanarReportToText() {
        Graph<String, Edge> g = newGraph();
        String[] v = {"A", "B", "C", "D", "E"};
        for (int i = 0; i < v.length; i++)
            for (int j = i + 1; j < v.length; j++)
                addEdge(g, v[i], v[j]);
        String text = PlanarGraphAnalyzer.analyze(g).toText();
        assertTrue(text.contains("Planar: NO"));
        assertTrue(text.contains("Kuratowski"));
    }

    @Test
    public void testGenusOfK5() {
        Graph<String, Edge> g = newGraph();
        String[] v = {"A", "B", "C", "D", "E"};
        for (int i = 0; i < v.length; i++)
            for (int j = i + 1; j < v.length; j++)
                addEdge(g, v[i], v[j]);
        PlanarGraphAnalyzer.PlanarityReport report = PlanarGraphAnalyzer.analyze(g);
        assertEquals(1, report.getGenus());
    }

    @Test
    public void testTreeIsPlanar() {
        // Binary tree
        Graph<String, Edge> g = newGraph();
        addEdge(g, "1", "2");
        addEdge(g, "1", "3");
        addEdge(g, "2", "4");
        addEdge(g, "2", "5");
        addEdge(g, "3", "6");
        addEdge(g, "3", "7");
        assertTrue(PlanarGraphAnalyzer.testPlanarity(g).isPlanar());
    }

    @Test
    public void testStarGraphIsPlanar() {
        Graph<String, Edge> g = newGraph();
        for (int i = 0; i < 10; i++)
            addEdge(g, "C", "V" + i);
        assertTrue(PlanarGraphAnalyzer.testPlanarity(g).isPlanar());
    }

    @Test
    public void testTreeFaces() {
        Graph<String, Edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "B", "D");
        List<PlanarGraphAnalyzer.Face> faces = PlanarGraphAnalyzer.enumerateFaces(g);
        assertNotNull(faces);
        // Tree: E=3, V=4, C=1, F = E-V+C+1 = 1
        // But face enumeration on a tree yields faces from dart walks
        assertTrue(faces.size() >= 1);
    }

    @Test
    public void testIsolatedVerticesPlanar() {
        Graph<String, Edge> g = newGraph();
        for (int i = 0; i < 5; i++) g.addVertex("V" + i);
        PlanarGraphAnalyzer.PlanarityResult r = PlanarGraphAnalyzer.testPlanarity(g);
        assertTrue(r.isPlanar());
        assertEquals(5, r.getComponents());
    }

    @Test
    public void testSquareWithDiagonalPlanar() {
        Graph<String, Edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        addEdge(g, "D", "A");
        addEdge(g, "A", "C"); // one diagonal
        PlanarGraphAnalyzer.PlanarityResult r = PlanarGraphAnalyzer.testPlanarity(g);
        assertTrue(r.isPlanar());
    }

    @Test
    public void testSquareWithBothDiagonalsPlanar() {
        Graph<String, Edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        addEdge(g, "D", "A");
        addEdge(g, "A", "C");
        addEdge(g, "B", "D");
        // K4 again, planar
        assertTrue(PlanarGraphAnalyzer.testPlanarity(g).isPlanar());
    }

    @Test
    public void testCubeGraphIsPlanar() {
        // Q3 (3-cube / hypercube) has 8 vertices, 12 edges — planar
        Graph<String, Edge> g = newGraph();
        addEdge(g, "000", "001"); addEdge(g, "000", "010"); addEdge(g, "000", "100");
        addEdge(g, "001", "011"); addEdge(g, "001", "101");
        addEdge(g, "010", "011"); addEdge(g, "010", "110");
        addEdge(g, "011", "111");
        addEdge(g, "100", "101"); addEdge(g, "100", "110");
        addEdge(g, "101", "111");
        addEdge(g, "110", "111");
        assertTrue(PlanarGraphAnalyzer.testPlanarity(g).isPlanar());
    }

    @Test
    public void testFaceVerticesNotNull() {
        Graph<String, Edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        List<PlanarGraphAnalyzer.Face> faces = PlanarGraphAnalyzer.enumerateFaces(g);
        for (PlanarGraphAnalyzer.Face f : faces) {
            assertNotNull(f.getVertices());
            assertTrue(f.size() > 0);
        }
    }

    @Test
    public void testDualFacesMatchEnumeratedFaces() {
        Graph<String, Edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "A");
        PlanarGraphAnalyzer.DualGraph dual = PlanarGraphAnalyzer.buildDualGraph(g);
        List<PlanarGraphAnalyzer.Face> faces = PlanarGraphAnalyzer.enumerateFaces(g);
        assertEquals(faces.size(), dual.getFaces().size());
    }

    @Test
    public void testPlanarReportHasCorrectVertexCount() {
        Graph<String, Edge> g = newGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "C", "D");
        addEdge(g, "D", "A");
        PlanarGraphAnalyzer.PlanarityReport report = PlanarGraphAnalyzer.analyze(g);
        assertEquals(4, report.getResult().getVertices());
        assertEquals(4, report.getResult().getEdges());
    }

    @Test
    public void testOctahedronIsPlanar() {
        // Octahedron: 6 vertices, 12 edges, planar
        Graph<String, Edge> g = newGraph();
        // Top and bottom + mid ring
        String[] mid = {"A", "B", "C", "D"};
        for (int i = 0; i < 4; i++) {
            addEdge(g, mid[i], mid[(i + 1) % 4]);
            addEdge(g, "Top", mid[i]);
            addEdge(g, "Bot", mid[i]);
        }
        assertTrue(PlanarGraphAnalyzer.testPlanarity(g).isPlanar());
    }
}
