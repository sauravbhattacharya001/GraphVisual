package gvisual;

import static org.junit.Assert.*;
import edu.uci.ics.jung.graph.Graph;
import org.junit.Before;
import org.junit.Test;
import java.util.*;

public class GraphBenchmarkSuiteTest {
    private GraphBenchmarkSuite suite;
    @Before public void setUp() { suite = new GraphBenchmarkSuite(); }

    @Test public void testZacharyCounts() {
        GraphBenchmarkSuite.BenchmarkGraph bg = suite.zacharyKarateClub();
        assertEquals(34, bg.getGraph().getVertexCount());
        assertEquals(78, bg.getGraph().getEdgeCount());
        assertTrue(bg.verify());
    }
    @Test public void testZacharyKeyNodes() {
        Graph<String, Edge> g = suite.zacharyKarateClub().getGraph();
        assertTrue(g.containsVertex("1"));
        assertTrue(g.containsVertex("34"));
        assertTrue(g.degree("1") >= 16);
    }
    @Test public void testZacharyProperties() {
        assertTrue(suite.zacharyKarateClub().getProperties().containsKey("communities"));
    }
    @Test public void testPetersenCounts() {
        GraphBenchmarkSuite.BenchmarkGraph bg = suite.petersenGraph();
        assertEquals(10, bg.getGraph().getVertexCount());
        assertEquals(15, bg.getGraph().getEdgeCount());
        assertTrue(bg.verify());
    }
    @Test public void testPetersenThreeRegular() {
        Graph<String, Edge> g = suite.petersenGraph().getGraph();
        for (String v : g.getVertices()) assertEquals(3, g.degree(v));
    }
    @Test public void testPetersenNotHamiltonian() {
        assertEquals("no", suite.petersenGraph().getProperties().get("hamiltonian"));
    }
    @Test public void testFlorentineCounts() {
        GraphBenchmarkSuite.BenchmarkGraph bg = suite.florentineFamilies();
        assertEquals(15, bg.getGraph().getVertexCount());
        assertEquals(20, bg.getGraph().getEdgeCount());
        assertTrue(bg.verify());
    }
    @Test public void testFlorentineMediciHighest() {
        Graph<String, Edge> g = suite.florentineFamilies().getGraph();
        int md = g.degree("Medici");
        for (String v : g.getVertices()) assertTrue(md >= g.degree(v));
    }
    @Test public void testFlorentineNames() {
        Graph<String, Edge> g = suite.florentineFamilies().getGraph();
        assertTrue(g.containsVertex("Strozzi"));
        assertTrue(g.containsVertex("Albizzi"));
    }
    @Test public void testCubeCounts() {
        GraphBenchmarkSuite.BenchmarkGraph bg = suite.cubeGraph();
        assertEquals(8, bg.getGraph().getVertexCount());
        assertEquals(12, bg.getGraph().getEdgeCount());
        assertTrue(bg.verify());
    }
    @Test public void testCubeThreeRegular() {
        for (String v : suite.cubeGraph().getGraph().getVertices())
            assertEquals(3, suite.cubeGraph().getGraph().degree(v));
    }
    @Test public void testDodecahedronCounts() {
        GraphBenchmarkSuite.BenchmarkGraph bg = suite.dodecahedron();
        assertEquals(20, bg.getGraph().getVertexCount());
        assertEquals(30, bg.getGraph().getEdgeCount());
        assertTrue(bg.verify());
    }
    @Test public void testDodecahedronThreeRegular() {
        for (String v : suite.dodecahedron().getGraph().getVertices())
            assertEquals(3, suite.dodecahedron().getGraph().degree(v));
    }
    @Test public void testTutteCounts() {
        GraphBenchmarkSuite.BenchmarkGraph bg = suite.tutteGraph();
        assertEquals(46, bg.getGraph().getVertexCount());
        assertEquals(69, bg.getGraph().getEdgeCount());
        assertTrue(bg.verify());
    }
    @Test public void testTutteCubic() {
        for (String v : suite.tutteGraph().getGraph().getVertices())
            assertEquals(3, suite.tutteGraph().getGraph().degree(v));
    }
    @Test public void testTutteProperties() {
        assertEquals("no", suite.tutteGraph().getProperties().get("hamiltonian"));
        assertEquals("yes", suite.tutteGraph().getProperties().get("planar"));
    }
    @Test public void testFriendshipF1() {
        GraphBenchmarkSuite.BenchmarkGraph bg = suite.friendshipGraph(1);
        assertEquals(3, bg.getGraph().getVertexCount());
        assertEquals(3, bg.getGraph().getEdgeCount());
        assertTrue(bg.verify());
    }
    @Test public void testFriendshipF5() {
        GraphBenchmarkSuite.BenchmarkGraph bg = suite.friendshipGraph(5);
        assertEquals(11, bg.getGraph().getVertexCount());
        assertEquals(15, bg.getGraph().getEdgeCount());
        assertTrue(bg.verify());
    }
    @Test public void testFriendshipHubDegree() {
        assertEquals(8, suite.friendshipGraph(4).getGraph().degree("0"));
    }
    @Test public void testFriendshipLeafDegree() {
        Graph<String, Edge> g = suite.friendshipGraph(3).getGraph();
        for (String v : g.getVertices())
            if (!v.equals("0")) assertEquals(2, g.degree(v));
    }
    @Test(expected = IllegalArgumentException.class)
    public void testFriendshipInvalidN() { suite.friendshipGraph(0); }
    @Test public void testAllBenchmarksCount() { assertEquals(7, suite.allBenchmarks().size()); }
    @Test public void testAllBenchmarksVerify() {
        for (GraphBenchmarkSuite.BenchmarkGraph bg : suite.allBenchmarks())
            assertTrue(bg.getName() + " verify", bg.verify());
    }
    @Test public void testAllBenchmarksMetadata() {
        for (GraphBenchmarkSuite.BenchmarkGraph bg : suite.allBenchmarks()) {
            assertNotNull(bg.getName()); assertFalse(bg.getName().isEmpty());
            assertNotNull(bg.getDescription()); assertNotNull(bg.getProperties());
        }
    }
    @Test public void testGetByNameVariants() {
        assertNotNull(suite.getByName("zachary"));
        assertNotNull(suite.getByName("karate-club"));
        assertNotNull(suite.getByName("petersen"));
        assertNotNull(suite.getByName("florentine"));
        assertNotNull(suite.getByName("cube"));
        assertNotNull(suite.getByName("dodecahedron"));
        assertNotNull(suite.getByName("tutte"));
    }
    @Test public void testGetByNameFriendship() {
        GraphBenchmarkSuite.BenchmarkGraph bg = suite.getByName("friendship(3)");
        assertNotNull(bg); assertEquals(7, bg.getGraph().getVertexCount());
    }
    @Test public void testGetByNameUnknown() { assertNull(suite.getByName("nonexistent")); }
    @Test public void testSummary() {
        String s = suite.petersenGraph().getSummary();
        assertTrue(s.contains("Petersen")); assertTrue(s.contains("10"));
    }
    @Test public void testAvailableList() {
        List<String> names = GraphBenchmarkSuite.availableBenchmarks();
        assertTrue(names.size() >= 7); assertTrue(names.contains("petersen"));
    }
}
