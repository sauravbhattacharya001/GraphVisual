package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link CliqueAnalyzer}.
 */
public class CliqueAnalyzerTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, Edge>();
    }

    // --- Helpers ---

    private Edge addEdge(String v1, String v2) {
        Edge e = new Edge("f", v1, v2);
        e.setWeight(1.0f);
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        graph.addEdge(e, v1, v2);
        return e;
    }

    private void makeComplete(String... vertices) {
        for (int i = 0; i < vertices.length; i++) {
            if (!graph.containsVertex(vertices[i])) graph.addVertex(vertices[i]);
            for (int j = i + 1; j < vertices.length; j++) {
                addEdge(vertices[i], vertices[j]);
            }
        }
    }

    private boolean cliquesContainExact(List<Set<String>> cliques, String... members) {
        Set<String> target = new HashSet<String>(Arrays.asList(members));
        for (Set<String> clique : cliques) {
            if (clique.equals(target)) return true;
        }
        return false;
    }

    // ═══════════════════════════════════════
    // 1. Constructor
    // ═══════════════════════════════════════

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNullGraph() {
        new CliqueAnalyzer(null);
    }

    @Test
    public void testConstructorValid() {
        CliqueAnalyzer ca = new CliqueAnalyzer(graph);
        assertNotNull(ca);
    }

    // ═══════════════════════════════════════
    // 2. Empty graph
    // ═══════════════════════════════════════

    @Test
    public void testEmptyGraphZeroCliques() {
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(0, ca.getCliqueCount());
    }

    @Test
    public void testEmptyGraphCliqueNumberZero() {
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(0, ca.getCliqueNumber());
    }

    @Test
    public void testEmptyGraphEmptyDistribution() {
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertTrue(ca.getSizeDistribution().isEmpty());
    }

    // ═══════════════════════════════════════
    // 3. Single vertex
    // ═══════════════════════════════════════

    @Test
    public void testSingleVertexOneClique() {
        graph.addVertex("A");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(1, ca.getCliqueCount());
    }

    @Test
    public void testSingleVertexCliqueSize1() {
        graph.addVertex("A");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(1, ca.getCliqueNumber());
        assertTrue(ca.getLargestClique().contains("A"));
    }

    // ═══════════════════════════════════════
    // 4. Single Edge
    // ═══════════════════════════════════════

    @Test
    public void testSingleEdgeOneClique() {
        addEdge("A", "B");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(1, ca.getCliqueCount());
    }

    @Test
    public void testSingleEdgeCliqueSize2() {
        addEdge("A", "B");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(2, ca.getCliqueNumber());
        assertTrue(ca.getLargestClique().contains("A"));
        assertTrue(ca.getLargestClique().contains("B"));
    }

    // ═══════════════════════════════════════
    // 5. Triangle (K3)
    // ═══════════════════════════════════════

    @Test
    public void testTriangleOneClique() {
        makeComplete("A", "B", "C");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(1, ca.getCliqueCount());
    }

    @Test
    public void testTriangleCliqueSize3() {
        makeComplete("A", "B", "C");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(3, ca.getCliqueNumber());
    }

    @Test
    public void testTriangleCoverage() {
        makeComplete("A", "B", "C");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(1.0, ca.getCoverage(), 0.001);
    }

    @Test
    public void testTriangleParticipation() {
        makeComplete("A", "B", "C");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        Map<String, Integer> part = ca.getNodeParticipation();
        assertEquals(Integer.valueOf(1), part.get("A"));
        assertEquals(Integer.valueOf(1), part.get("B"));
        assertEquals(Integer.valueOf(1), part.get("C"));
    }

    // ═══════════════════════════════════════
    // 6. K4
    // ═══════════════════════════════════════

    @Test
    public void testK4OneClique() {
        makeComplete("A", "B", "C", "D");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(1, ca.getCliqueCount());
    }

    @Test
    public void testK4CliqueNumber4() {
        makeComplete("A", "B", "C", "D");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(4, ca.getCliqueNumber());
    }

    @Test
    public void testK4ContainsAllVertices() {
        makeComplete("A", "B", "C", "D");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        Set<String> largest = ca.getLargestClique();
        assertTrue(largest.contains("A"));
        assertTrue(largest.contains("B"));
        assertTrue(largest.contains("C"));
        assertTrue(largest.contains("D"));
    }

    @Test
    public void testK4Distribution() {
        makeComplete("A", "B", "C", "D");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        SortedMap<Integer, Integer> dist = ca.getSizeDistribution();
        assertEquals(1, dist.size());
        assertEquals(Integer.valueOf(1), dist.get(4));
    }

    // ═══════════════════════════════════════
    // 7. Path graph (A-B-C-D)
    // ═══════════════════════════════════════

    @Test
    public void testPathGraphCliqueCount() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(3, ca.getCliqueCount());
    }

    @Test
    public void testPathGraphNoLargeClique() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(2, ca.getCliqueNumber());
    }

    @Test
    public void testPathGraphAllCliquesSize2() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        for (Set<String> clique : ca.getCliques()) {
            assertEquals(2, clique.size());
        }
    }

    // ═══════════════════════════════════════
    // 8. Star graph
    // ═══════════════════════════════════════

    @Test
    public void testStarGraphCliqueCount() {
        // Hub = "H", leaves = L1..L4
        addEdge("H", "L1");
        addEdge("H", "L2");
        addEdge("H", "L3");
        addEdge("H", "L4");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(4, ca.getCliqueCount()); // 4 cliques of size 2
    }

    @Test
    public void testStarGraphMaxCliqueSize2() {
        addEdge("H", "L1");
        addEdge("H", "L2");
        addEdge("H", "L3");
        addEdge("H", "L4");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(2, ca.getCliqueNumber());
    }

    @Test
    public void testStarGraphHubHighestParticipation() {
        addEdge("H", "L1");
        addEdge("H", "L2");
        addEdge("H", "L3");
        addEdge("H", "L4");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        Map<String, Integer> part = ca.getNodeParticipation();
        assertEquals(Integer.valueOf(4), part.get("H"));
        assertEquals(Integer.valueOf(1), part.get("L1"));
    }

    // ═══════════════════════════════════════
    // 9. Bowtie (two triangles sharing vertex)
    // ═══════════════════════════════════════

    @Test
    public void testBowtieCliqueCount() {
        // Triangle 1: A-B-C, Triangle 2: C-D-E
        makeComplete("A", "B", "C");
        addEdge("C", "D");
        addEdge("C", "E");
        addEdge("D", "E");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(2, ca.getCliqueCount());
    }

    @Test
    public void testBowtieCliqueNumber3() {
        makeComplete("A", "B", "C");
        addEdge("C", "D");
        addEdge("C", "E");
        addEdge("D", "E");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(3, ca.getCliqueNumber());
    }

    @Test
    public void testBowtieSharedVertex() {
        makeComplete("A", "B", "C");
        addEdge("C", "D");
        addEdge("C", "E");
        addEdge("D", "E");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        Map<String, Integer> part = ca.getNodeParticipation();
        assertEquals(Integer.valueOf(2), part.get("C")); // C in both cliques
    }

    @Test
    public void testBowtieContainsExpectedCliques() {
        makeComplete("A", "B", "C");
        addEdge("C", "D");
        addEdge("C", "E");
        addEdge("D", "E");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertTrue(cliquesContainExact(ca.getCliques(), "A", "B", "C"));
        assertTrue(cliquesContainExact(ca.getCliques(), "C", "D", "E"));
    }

    // ═══════════════════════════════════════
    // 10. Petersen-like (multiple overlapping cliques)
    // ═══════════════════════════════════════

    @Test
    public void testOverlappingCliquesMultiple() {
        // Two K3 sharing an Edge: A-B-C and A-B-D
        makeComplete("A", "B", "C");
        addEdge("A", "D");
        addEdge("B", "D");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(2, ca.getCliqueCount());
        assertEquals(3, ca.getCliqueNumber());
    }

    @Test
    public void testOverlappingCliquesCorrectContent() {
        makeComplete("A", "B", "C");
        addEdge("A", "D");
        addEdge("B", "D");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertTrue(cliquesContainExact(ca.getCliques(), "A", "B", "C"));
        assertTrue(cliquesContainExact(ca.getCliques(), "A", "B", "D"));
    }

    // ═══════════════════════════════════════
    // 11. Complete graph K5
    // ═══════════════════════════════════════

    @Test
    public void testK5SingleClique() {
        makeComplete("A", "B", "C", "D", "E");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(1, ca.getCliqueCount());
    }

    @Test
    public void testK5CliqueNumber5() {
        makeComplete("A", "B", "C", "D", "E");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(5, ca.getCliqueNumber());
    }

    @Test
    public void testK5Distribution() {
        makeComplete("A", "B", "C", "D", "E");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        SortedMap<Integer, Integer> dist = ca.getSizeDistribution();
        assertEquals(Integer.valueOf(1), dist.get(5));
        assertEquals(1, dist.size());
    }

    // ═══════════════════════════════════════
    // 12. Disconnected graph
    // ═══════════════════════════════════════

    @Test
    public void testDisconnectedCliqueCount() {
        // Component 1: K3 (A,B,C), Component 2: K2 (D,E), Isolated: F
        makeComplete("A", "B", "C");
        addEdge("D", "E");
        graph.addVertex("F");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(3, ca.getCliqueCount()); // {A,B,C}, {D,E}, {F}
    }

    @Test
    public void testDisconnectedCliqueNumber() {
        makeComplete("A", "B", "C");
        addEdge("D", "E");
        graph.addVertex("F");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(3, ca.getCliqueNumber());
    }

    @Test
    public void testDisconnectedContainsIsolated() {
        makeComplete("A", "B", "C");
        addEdge("D", "E");
        graph.addVertex("F");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertTrue(cliquesContainExact(ca.getCliques(), "F"));
    }

    // ═══════════════════════════════════════
    // 13. getCliquesOfSize
    // ═══════════════════════════════════════

    @Test
    public void testGetCliquesOfSizeBasic() {
        makeComplete("A", "B", "C");
        addEdge("D", "E");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(1, ca.getCliquesOfSize(3).size());
        assertEquals(1, ca.getCliquesOfSize(2).size());
    }

    @Test
    public void testGetCliquesOfSizeZeroResult() {
        makeComplete("A", "B", "C");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(0, ca.getCliquesOfSize(5).size());
    }

    @Test
    public void testGetCliquesOfSizeMultiple() {
        // Two K3 sharing Edge A-B
        makeComplete("A", "B", "C");
        addEdge("A", "D");
        addEdge("B", "D");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(2, ca.getCliquesOfSize(3).size());
    }

    // ═══════════════════════════════════════
    // 14. getCliquesContaining
    // ═══════════════════════════════════════

    @Test
    public void testGetCliquesContainingBasic() {
        makeComplete("A", "B", "C");
        addEdge("A", "D");
        addEdge("B", "D");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        // A is in both {A,B,C} and {A,B,D}
        assertEquals(2, ca.getCliquesContaining("A").size());
    }

    @Test
    public void testGetCliquesContainingLeafVertex() {
        addEdge("A", "B");
        addEdge("A", "C");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        // B is only in {A,B}
        assertEquals(1, ca.getCliquesContaining("B").size());
    }

    @Test
    public void testGetCliquesContainingNonexistentVertex() {
        addEdge("A", "B");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(0, ca.getCliquesContaining("Z").size());
    }

    // ═══════════════════════════════════════
    // 15. getNodeParticipation
    // ═══════════════════════════════════════

    @Test
    public void testNodeParticipationAllVerticesPresent() {
        makeComplete("A", "B", "C");
        addEdge("D", "E");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        Map<String, Integer> part = ca.getNodeParticipation();
        assertEquals(5, part.size());
    }

    @Test
    public void testNodeParticipationCorrectCounts() {
        // A is in both {A,B,C} and {A,D}
        makeComplete("A", "B", "C");
        addEdge("A", "D");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        Map<String, Integer> part = ca.getNodeParticipation();
        assertEquals(Integer.valueOf(2), part.get("A")); // in {A,B,C} and {A,D}
        assertEquals(Integer.valueOf(1), part.get("B"));
        assertEquals(Integer.valueOf(1), part.get("D"));
    }

    // ═══════════════════════════════════════
    // 16. getTopParticipants
    // ═══════════════════════════════════════

    @Test
    public void testTopParticipantsOrdering() {
        addEdge("H", "L1");
        addEdge("H", "L2");
        addEdge("H", "L3");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        List<Map.Entry<String, Integer>> top = ca.getTopParticipants(2);
        assertEquals(2, top.size());
        assertEquals("H", top.get(0).getKey());
        assertEquals(Integer.valueOf(3), top.get(0).getValue());
    }

    @Test
    public void testTopParticipantsNLargerThanVertices() {
        addEdge("A", "B");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        List<Map.Entry<String, Integer>> top = ca.getTopParticipants(100);
        assertEquals(2, top.size()); // only 2 vertices
    }

    @Test
    public void testTopParticipantsEmptyGraph() {
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        List<Map.Entry<String, Integer>> top = ca.getTopParticipants(5);
        assertEquals(0, top.size());
    }

    // ═══════════════════════════════════════
    // 17. getOverlaps
    // ═══════════════════════════════════════

    @Test
    public void testOverlapsFindsSharedVertices() {
        // Two K3 sharing Edge A-B: {A,B,C} and {A,B,D}
        makeComplete("A", "B", "C");
        addEdge("A", "D");
        addEdge("B", "D");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        List<CliqueAnalyzer.CliqueOverlap> overlaps = ca.getOverlaps(10);
        assertEquals(1, overlaps.size());
        assertTrue(overlaps.get(0).sharedVertices.contains("A"));
        assertTrue(overlaps.get(0).sharedVertices.contains("B"));
        assertEquals(2, overlaps.get(0).overlapSize);
    }

    @Test
    public void testOverlapsNoOverlap() {
        // Two disconnected K3
        makeComplete("A", "B", "C");
        // Second triangle
        graph.addVertex("D");
        graph.addVertex("E");
        graph.addVertex("F");
        addEdge("D", "E");
        addEdge("D", "F");
        addEdge("E", "F");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        List<CliqueAnalyzer.CliqueOverlap> overlaps = ca.getOverlaps(10);
        assertEquals(0, overlaps.size());
    }

    @Test
    public void testOverlapsLimited() {
        // Star with many cliques all sharing hub
        addEdge("H", "L1");
        addEdge("H", "L2");
        addEdge("H", "L3");
        addEdge("H", "L4");
        addEdge("H", "L5");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        // C(5,2)=10 overlap pairs, limit to 3
        List<CliqueAnalyzer.CliqueOverlap> overlaps = ca.getOverlaps(3);
        assertEquals(3, overlaps.size());
    }

    // ═══════════════════════════════════════
    // 18. getCoverage
    // ═══════════════════════════════════════

    @Test
    public void testCoverageEmptyGraph() {
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(0.0, ca.getCoverage(), 0.001);
    }

    @Test
    public void testCoverageFraction() {
        // K3 + isolated vertex: coverage = 3/4 = 0.75
        makeComplete("A", "B", "C");
        graph.addVertex("D");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(0.75, ca.getCoverage(), 0.001);
    }

    // ═══════════════════════════════════════
    // 19. getCliqueGraph
    // ═══════════════════════════════════════

    @Test
    public void testCliqueGraphBasic() {
        // Two K3 sharing Edge A-B → overlap=2
        makeComplete("A", "B", "C");
        addEdge("A", "D");
        addEdge("B", "D");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        Map<Integer, Set<Integer>> cg = ca.getCliqueGraph(1);
        // Both cliques should be connected
        assertTrue(cg.get(0).contains(1));
        assertTrue(cg.get(1).contains(0));
    }

    @Test
    public void testCliqueGraphHighThreshold() {
        // Two K3 sharing Edge → overlap=2, threshold=3 → no connection
        makeComplete("A", "B", "C");
        addEdge("A", "D");
        addEdge("B", "D");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        Map<Integer, Set<Integer>> cg = ca.getCliqueGraph(3);
        assertTrue(cg.get(0).isEmpty());
        assertTrue(cg.get(1).isEmpty());
    }

    @Test
    public void testCliqueGraphDisconnected() {
        // Two separate K3 → no overlap → no edges in clique graph
        makeComplete("A", "B", "C");
        graph.addVertex("D");
        graph.addVertex("E");
        graph.addVertex("F");
        addEdge("D", "E");
        addEdge("D", "F");
        addEdge("E", "F");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        Map<Integer, Set<Integer>> cg = ca.getCliqueGraph(1);
        assertTrue(cg.get(0).isEmpty());
        assertTrue(cg.get(1).isEmpty());
    }

    // ═══════════════════════════════════════
    // 20. getResult
    // ═══════════════════════════════════════

    @Test
    public void testResultAllFieldsPopulated() {
        makeComplete("A", "B", "C");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        CliqueAnalyzer.CliqueResult result = ca.getResult();
        assertNotNull(result.cliques);
        assertEquals(3, result.cliqueNumber);
        assertEquals(1, result.cliqueCount);
        assertEquals(3.0, result.averageSize, 0.001);
        assertEquals(1.0, result.coverage, 0.001);
        assertNotNull(result.sizeDistribution);
        assertNotNull(result.nodeParticipation);
    }

    @Test
    public void testResultToString() {
        makeComplete("A", "B", "C");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        CliqueAnalyzer.CliqueResult result = ca.getResult();
        String s = result.toString();
        assertNotNull(s);
        assertTrue(s.contains("cliqueNumber=3"));
    }

    // ═══════════════════════════════════════
    // 21. formatSummary
    // ═══════════════════════════════════════

    @Test
    public void testFormatSummaryNonEmpty() {
        makeComplete("A", "B", "C", "D");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        String summary = ca.formatSummary();
        assertNotNull(summary);
        assertFalse(summary.isEmpty());
    }

    @Test
    public void testFormatSummaryContainsKeyInfo() {
        makeComplete("A", "B", "C", "D");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        String summary = ca.formatSummary();
        assertTrue(summary.contains("Clique Analysis"));
        assertTrue(summary.contains("4")); // clique number
        assertTrue(summary.contains("Largest clique"));
    }

    // ═══════════════════════════════════════
    // Additional Edge cases & coverage
    // ═══════════════════════════════════════

    @Test
    public void testLazyComputation() {
        // getCliques() should trigger compute() automatically
        makeComplete("A", "B", "C");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph);
        List<Set<String>> cliques = ca.getCliques();
        assertEquals(1, cliques.size());
    }

    @Test
    public void testIdempotentCompute() {
        makeComplete("A", "B", "C");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph);
        ca.compute();
        ca.compute(); // second call should be no-op
        assertEquals(1, ca.getCliqueCount());
    }

    @Test
    public void testCliquesSortedBySizeDescending() {
        // K3 and a separate Edge → cliques: {A,B,C} (size 3), {D,E} (size 2)
        makeComplete("A", "B", "C");
        addEdge("D", "E");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        List<Set<String>> cliques = ca.getCliques();
        assertTrue(cliques.get(0).size() >= cliques.get(1).size());
    }

    @Test
    public void testAverageCliqueSize() {
        // K3 + separate Edge: avg = (3+2)/2 = 2.5
        makeComplete("A", "B", "C");
        addEdge("D", "E");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(2.5, ca.getAverageCliqueSize(), 0.001);
    }

    @Test
    public void testAverageCliqueSizeEmptyGraph() {
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(0.0, ca.getAverageCliqueSize(), 0.001);
    }

    @Test
    public void testOverlapToString() {
        Set<String> shared = new HashSet<String>(Arrays.asList("A", "B"));
        CliqueAnalyzer.CliqueOverlap ov = new CliqueAnalyzer.CliqueOverlap(0, 1, shared);
        String s = ov.toString();
        assertTrue(s.contains("0"));
        assertTrue(s.contains("1"));
    }

    @Test
    public void testCoverageOnlyCountsNontrivial() {
        // Only edges (size 2 cliques): coverage should be 0.0
        addEdge("A", "B");
        addEdge("C", "D");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(0.0, ca.getCoverage(), 0.001);
    }

    @Test
    public void testFormatSummaryEmptyGraph() {
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        String summary = ca.formatSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("Clique Analysis"));
    }

    @Test
    public void testMultipleIsolatedVertices() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(3, ca.getCliqueCount());
        assertEquals(1, ca.getCliqueNumber());
    }

    @Test
    public void testLargestCliqueEmptyGraph() {
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertTrue(ca.getLargestClique().isEmpty());
    }

    @Test
    public void testGetCliquesOfSizeOne() {
        graph.addVertex("A");
        graph.addVertex("B");
        addEdge("C", "D");
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        assertEquals(2, ca.getCliquesOfSize(1).size());
    }

    @Test
    public void testCliqueGraphEmptyGraph() {
        CliqueAnalyzer ca = new CliqueAnalyzer(graph).compute();
        Map<Integer, Set<Integer>> cg = ca.getCliqueGraph(1);
        assertTrue(cg.isEmpty());
    }
}
