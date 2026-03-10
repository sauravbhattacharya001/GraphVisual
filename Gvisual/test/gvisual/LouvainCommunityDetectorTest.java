package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/**
 * Comprehensive tests for LouvainCommunityDetector.
 */
public class LouvainCommunityDetectorTest {

    private Graph<String, edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, edge>();
    }

    private void addEdge(String type, String v1, String v2, float weight) {
        graph.addVertex(v1);
        graph.addVertex(v2);
        edge e = new edge(type, v1, v2);
        e.setWeight(weight);
        graph.addEdge(e, v1, v2);
    }

    // ─── Constructor ─────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() { new LouvainCommunityDetector(null); }

    @Test(expected = IllegalArgumentException.class)
    public void testZeroResolutionThrows() { new LouvainCommunityDetector(graph, 0.0); }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeResolutionThrows() { new LouvainCommunityDetector(graph, -1.0); }

    // ─── Trivial graphs ─────────────────────────────────────────────

    @Test
    public void testEmptyGraph() {
        LouvainCommunityDetector.LouvainResult r = new LouvainCommunityDetector(graph).detect();
        assertEquals(0, r.getCommunityCount());
        assertEquals(0.0, r.getModularity(), 0.001);
        assertTrue(r.getHierarchy().isEmpty());
    }

    @Test
    public void testSingleNode() {
        graph.addVertex("A");
        LouvainCommunityDetector.LouvainResult r = new LouvainCommunityDetector(graph).detect();
        assertEquals(1, r.getCommunityCount());
        assertTrue(r.getCommunities().get(0).getMembers().contains("A"));
    }

    @Test
    public void testTwoConnectedNodes() {
        addEdge("f", "A", "B", 1.0f);
        LouvainCommunityDetector.LouvainResult r = new LouvainCommunityDetector(graph).detect();
        assertEquals(r.getNodeToCommunity().get("A"), r.getNodeToCommunity().get("B"));
    }

    @Test
    public void testTwoDisconnectedNodes() {
        graph.addVertex("A");
        graph.addVertex("B");
        LouvainCommunityDetector.LouvainResult r = new LouvainCommunityDetector(graph).detect();
        assertEquals(0.0, r.getModularity(), 0.001);
    }

    // ─── Two clear communities ───────────────────────────────────────

    @Test
    public void testTwoClearCommunities() {
        addEdge("f", "A", "B", 1.0f);
        addEdge("f", "B", "C", 1.0f);
        addEdge("f", "A", "C", 1.0f);
        addEdge("f", "D", "E", 1.0f);
        addEdge("f", "E", "F", 1.0f);
        addEdge("f", "D", "F", 1.0f);
        addEdge("f", "C", "D", 0.1f);

        LouvainCommunityDetector.LouvainResult r = new LouvainCommunityDetector(graph).detect();
        assertEquals(2, r.getCommunityCount());
        assertEquals(r.getNodeToCommunity().get("A"), r.getNodeToCommunity().get("B"));
        assertEquals(r.getNodeToCommunity().get("A"), r.getNodeToCommunity().get("C"));
        assertEquals(r.getNodeToCommunity().get("D"), r.getNodeToCommunity().get("E"));
        assertEquals(r.getNodeToCommunity().get("D"), r.getNodeToCommunity().get("F"));
        assertNotEquals(r.getNodeToCommunity().get("A"), r.getNodeToCommunity().get("D"));
        assertTrue(r.getModularity() > 0.0);
    }

    // ─── Modularity ─────────────────────────────────────────────────

    @Test
    public void testModularityPositive() {
        addEdge("f", "A", "B", 1.0f); addEdge("f", "B", "C", 1.0f); addEdge("f", "A", "C", 1.0f);
        addEdge("f", "D", "E", 1.0f); addEdge("f", "E", "F", 1.0f); addEdge("f", "D", "F", 1.0f);
        addEdge("f", "C", "D", 0.5f);
        LouvainCommunityDetector.LouvainResult r = new LouvainCommunityDetector(graph).detect();
        assertTrue(r.getModularity() > 0.0);
        assertTrue(r.getModularity() <= 1.0);
    }

    @Test
    public void testModularityBounded() {
        for (int i = 0; i < 20; i++)
            for (int j = i + 1; j < 20; j++)
                if (i / 5 == j / 5 || (i + j) % 7 == 0)
                    addEdge("f", "N" + i, "N" + j, 1.0f);
        LouvainCommunityDetector.LouvainResult r = new LouvainCommunityDetector(graph).detect();
        assertTrue(r.getModularity() >= -0.5);
        assertTrue(r.getModularity() <= 1.0);
    }

    // ─── Hierarchy ──────────────────────────────────────────────────

    @Test
    public void testHierarchyNotEmpty() {
        addEdge("f", "A", "B", 1.0f); addEdge("f", "B", "C", 1.0f);
        LouvainCommunityDetector.LouvainResult r = new LouvainCommunityDetector(graph).detect();
        assertFalse(r.getHierarchy().isEmpty());
        assertEquals(0, r.getHierarchy().get(0).getLevel());
    }

    @Test
    public void testHierarchyFinalModularity() {
        addEdge("f", "A", "B", 1.0f); addEdge("f", "B", "C", 1.0f); addEdge("f", "A", "C", 1.0f);
        addEdge("f", "D", "E", 1.0f); addEdge("f", "E", "F", 1.0f); addEdge("f", "D", "F", 1.0f);
        addEdge("f", "C", "D", 0.1f);
        LouvainCommunityDetector.LouvainResult r = new LouvainCommunityDetector(graph).detect();
        List<LouvainCommunityDetector.HierarchyLevel> levels = r.getHierarchy();
        assertEquals(r.getModularity(), levels.get(levels.size() - 1).getModularity(), 0.001);
    }

    @Test
    public void testHierarchyLevelAssignments() {
        addEdge("f", "A", "B", 1.0f); addEdge("f", "C", "D", 1.0f);
        LouvainCommunityDetector.LouvainResult r = new LouvainCommunityDetector(graph).detect();
        for (LouvainCommunityDetector.HierarchyLevel level : r.getHierarchy()) {
            assertTrue(level.getNodeAssignments().containsKey("A"));
            assertTrue(level.getNodeAssignments().containsKey("B"));
            assertTrue(level.getNodeAssignments().containsKey("C"));
            assertTrue(level.getNodeAssignments().containsKey("D"));
        }
    }

    // ─── Resolution ──────────────────────────────────────────────────

    @Test
    public void testHigherResolutionMoreCommunities() {
        addEdge("f", "A", "B", 1.0f); addEdge("f", "B", "C", 1.0f); addEdge("f", "A", "C", 1.0f);
        addEdge("f", "D", "E", 1.0f); addEdge("f", "E", "F", 1.0f); addEdge("f", "D", "F", 1.0f);
        addEdge("f", "C", "D", 0.5f);
        int lowC = new LouvainCommunityDetector(graph, 0.5).detect().getCommunityCount();
        int highC = new LouvainCommunityDetector(graph, 2.0).detect().getCommunityCount();
        assertTrue(highC >= lowC);
    }

    // ─── Statistics ──────────────────────────────────────────────────

    @Test
    public void testInternalEdges() {
        addEdge("f", "A", "B", 1.0f); addEdge("f", "B", "C", 1.0f); addEdge("f", "A", "C", 1.0f);
        LouvainCommunityDetector.LouvainResult r = new LouvainCommunityDetector(graph).detect();
        if (r.getCommunityCount() == 1) assertEquals(3, r.getCommunities().get(0).getInternalEdges());
    }

    @Test
    public void testExternalEdges() {
        addEdge("f", "A", "B", 1.0f); addEdge("f", "C", "D", 1.0f); addEdge("f", "B", "C", 0.01f);
        LouvainCommunityDetector.LouvainResult r = new LouvainCommunityDetector(graph, 2.0).detect();
        if (r.getCommunityCount() == 2) {
            int ext = 0;
            for (LouvainCommunityDetector.Community c : r.getCommunities()) ext += c.getExternalEdges();
            assertEquals(2, ext);
        }
    }

    @Test
    public void testIntraDensityComplete() {
        addEdge("f", "A", "B", 1.0f); addEdge("f", "B", "C", 1.0f); addEdge("f", "A", "C", 1.0f);
        LouvainCommunityDetector.LouvainResult r = new LouvainCommunityDetector(graph).detect();
        if (r.getCommunityCount() == 1)
            assertEquals(1.0, r.getCommunities().get(0).getIntraDensity(), 0.001);
    }

    @Test
    public void testIntraDensitySingle() {
        graph.addVertex("A");
        LouvainCommunityDetector.LouvainResult r = new LouvainCommunityDetector(graph).detect();
        assertEquals(0.0, r.getCommunities().get(0).getIntraDensity(), 0.001);
    }

    @Test
    public void testInterIntraRatioZero() {
        addEdge("f", "A", "B", 1.0f);
        LouvainCommunityDetector.LouvainResult r = new LouvainCommunityDetector(graph).detect();
        if (r.getCommunityCount() == 1)
            assertEquals(0.0, r.getCommunities().get(0).getInterIntraRatio(), 0.001);
    }

    // ─── getCommunityOf ──────────────────────────────────────────────

    @Test
    public void testGetCommunityOfValid() {
        addEdge("f", "A", "B", 1.0f);
        LouvainCommunityDetector.LouvainResult r = new LouvainCommunityDetector(graph).detect();
        assertNotNull(r.getCommunityOf("A"));
        assertNotNull(r.getCommunityOf("B"));
    }

    @Test
    public void testGetCommunityOfMissing() {
        addEdge("f", "A", "B", 1.0f);
        LouvainCommunityDetector.LouvainResult r = new LouvainCommunityDetector(graph).detect();
        assertNull(r.getCommunityOf("Z"));
        assertNull(r.getCommunityOf(null));
    }

    // ─── Significant communities ─────────────────────────────────────

    @Test
    public void testSignificant() {
        addEdge("f", "A", "B", 1.0f); addEdge("f", "B", "C", 1.0f); addEdge("f", "A", "C", 1.0f);
        graph.addVertex("D");
        LouvainCommunityDetector.LouvainResult r = new LouvainCommunityDetector(graph).detect();
        for (LouvainCommunityDetector.Community c : r.getSignificantCommunities(2))
            assertTrue(c.getSize() >= 2);
    }

    // ─── Export ──────────────────────────────────────────────────────

    @Test
    public void testExportCsv() {
        addEdge("f", "A", "B", 1.0f); addEdge("f", "C", "D", 1.0f);
        String csv = new LouvainCommunityDetector(graph).detect().exportCsv();
        assertTrue(csv.startsWith("node,community_id,community_size\n"));
        assertTrue(csv.contains("A,")); assertTrue(csv.contains("D,"));
        assertEquals(5, csv.split("\n").length);
    }

    @Test
    public void testExportJson() {
        addEdge("f", "A", "B", 1.0f); addEdge("f", "C", "D", 1.0f);
        String json = new LouvainCommunityDetector(graph).detect().exportJson();
        assertTrue(json.contains("\"modularity\"")); assertTrue(json.contains("\"communityCount\""));
        assertTrue(json.contains("\"communities\"")); assertTrue(json.contains("\"hierarchy\""));
    }

    @Test
    public void testGenerateReport() {
        addEdge("f", "A", "B", 1.0f); addEdge("f", "B", "C", 1.0f); addEdge("f", "A", "C", 1.0f);
        addEdge("f", "D", "E", 1.0f); addEdge("f", "E", "F", 1.0f); addEdge("f", "D", "F", 1.0f);
        addEdge("f", "C", "D", 0.1f);
        String rpt = new LouvainCommunityDetector(graph).detect().generateReport();
        assertTrue(rpt.contains("Louvain Community Detection Report"));
        assertTrue(rpt.contains("Final modularity")); assertTrue(rpt.contains("Community size statistics"));
    }

    // ─── toString ────────────────────────────────────────────────────

    @Test
    public void testCommunityToString() {
        addEdge("f", "A", "B", 1.0f);
        String s = new LouvainCommunityDetector(graph).detect().getCommunities().get(0).toString();
        assertTrue(s.contains("Community")); assertTrue(s.contains("members"));
    }

    // ─── Larger graphs ───────────────────────────────────────────────

    @Test
    public void testFourCliques() {
        String[][] cliques = {{"A1","A2","A3","A4"},{"B1","B2","B3","B4"},
                              {"C1","C2","C3","C4"},{"D1","D2","D3","D4"}};
        for (String[] cl : cliques)
            for (int i = 0; i < cl.length; i++)
                for (int j = i+1; j < cl.length; j++)
                    addEdge("f", cl[i], cl[j], 5.0f);
        addEdge("f", "A4", "B1", 0.1f);
        addEdge("f", "B4", "C1", 0.1f);
        addEdge("f", "C4", "D1", 0.1f);
        LouvainCommunityDetector.LouvainResult r = new LouvainCommunityDetector(graph).detect();
        assertTrue(r.getCommunityCount() >= 2);
        assertTrue(r.getModularity() > 0.0);
        assertEquals(r.getNodeToCommunity().get("A1"), r.getNodeToCommunity().get("A2"));
    }

    @Test
    public void testCompleteGraph() {
        for (int i = 0; i < 5; i++)
            for (int j = i+1; j < 5; j++)
                addEdge("f", "V"+i, "V"+j, 1.0f);
        assertNotNull(new LouvainCommunityDetector(graph).detect());
    }

    @Test
    public void testStarGraph() {
        for (int i = 1; i <= 6; i++) addEdge("f", "Hub", "S"+i, 1.0f);
        assertTrue(new LouvainCommunityDetector(graph).detect().getCommunityCount() >= 1);
    }

    @Test
    public void testDeterministic() {
        addEdge("f", "A", "B", 1.0f); addEdge("f", "B", "C", 1.0f); addEdge("f", "A", "C", 1.0f);
        addEdge("f", "D", "E", 1.0f); addEdge("f", "E", "F", 1.0f); addEdge("f", "D", "F", 1.0f);
        addEdge("f", "C", "D", 0.1f);
        LouvainCommunityDetector det = new LouvainCommunityDetector(graph);
        LouvainCommunityDetector.LouvainResult r1 = det.detect(), r2 = det.detect();
        assertEquals(r1.getCommunityCount(), r2.getCommunityCount());
        assertEquals(r1.getModularity(), r2.getModularity(), 0.001);
    }

    @Test
    public void testZeroWeightEdge() {
        graph.addVertex("A"); graph.addVertex("B");
        edge e = new edge("f", "A", "B"); e.setWeight(0.0f);
        graph.addEdge(e, "A", "B");
        LouvainCommunityDetector.LouvainResult r = new LouvainCommunityDetector(graph).detect();
        assertEquals(r.getNodeToCommunity().get("A"), r.getNodeToCommunity().get("B"));
    }

    @Test
    public void testAllNodesAssigned() {
        addEdge("f", "A", "B", 1.0f); addEdge("f", "C", "D", 1.0f); graph.addVertex("E");
        LouvainCommunityDetector.LouvainResult r = new LouvainCommunityDetector(graph).detect();
        assertEquals(5, r.getNodeToCommunity().size());
        for (String n : new String[]{"A","B","C","D","E"})
            assertTrue(r.getNodeToCommunity().containsKey(n));
    }

    @Test
    public void testCommunityIdsMatchIndex() {
        addEdge("f", "A", "B", 1.0f); addEdge("f", "C", "D", 1.0f);
        LouvainCommunityDetector.LouvainResult r = new LouvainCommunityDetector(graph).detect();
        for (int i = 0; i < r.getCommunities().size(); i++)
            assertEquals(i, r.getCommunities().get(i).getId());
    }
}
