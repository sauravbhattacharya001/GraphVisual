package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for GraphSummarizer — narrative summary generation.
 */
public class GraphSummarizerTest {

    private Graph<String, Edge> graph;
    private List<Edge> friends, fs, classmates, strangers, studyG;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, Edge>();
        friends = new ArrayList<Edge>();
        fs = new ArrayList<Edge>();
        classmates = new ArrayList<Edge>();
        strangers = new ArrayList<Edge>();
        studyG = new ArrayList<Edge>();
    }

    private GraphSummarizer makeSummarizer() {
        return new GraphSummarizer(graph, friends, fs, classmates, strangers, studyG);
    }

    private edge addEdge(String v1, String v2, String typeCode) {
        graph.addVertex(v1);
        graph.addVertex(v2);
        edge e = new Edge(typeCode, v1, v2);
        e.setWeight(50.0f);
        graph.addEdge(e, v1, v2);
        return e;
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new GraphSummarizer(null, friends, fs, classmates, strangers, studyG);
    }

    @Test
    public void testEmptyGraphSummary() {
        GraphSummarizer s = makeSummarizer();
        String summary = s.generateSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("empty"));
        assertTrue(summary.contains("0 nodes"));
    }

    @Test
    public void testEmptyGraphOneLiner() {
        GraphSummarizer s = makeSummarizer();
        String line = s.getOneLiner();
        assertTrue(line.contains("0 nodes"));
        assertTrue(line.contains("0 edges"));
    }

    @Test
    public void testSmallNetwork() {
        edge e1 = addEdge("A", "B", "f");
        friends.add(e1);
        GraphSummarizer s = makeSummarizer();
        String overview = s.getOverviewSection();
        assertTrue(overview.contains("small"));
        assertTrue(overview.contains("2 nodes"));
    }

    @Test
    public void testMediumNetwork() {
        // Create 20 nodes
        for (int i = 0; i < 20; i++) {
            graph.addVertex("N" + i);
        }
        for (int i = 0; i < 19; i++) {
            edge e = addEdge("N" + i, "N" + (i + 1), "c");
            classmates.add(e);
        }
        GraphSummarizer s = makeSummarizer();
        assertTrue(s.getOverviewSection().contains("medium"));
    }

    @Test
    public void testDensitySection() {
        edge e1 = addEdge("A", "B", "f");
        edge e2 = addEdge("B", "C", "f");
        edge e3 = addEdge("A", "C", "f");
        friends.add(e1);
        friends.add(e2);
        friends.add(e3);
        GraphSummarizer s = makeSummarizer();
        String density = s.getDensitySection();
        assertTrue(density.contains("density"));
        // Complete graph of 3 → density 1.0 → "very dense"
        assertTrue(density.contains("very dense"));
    }

    @Test
    public void testConnectivitySingleComponent() {
        edge e1 = addEdge("A", "B", "f");
        friends.add(e1);
        GraphSummarizer s = makeSummarizer();
        String conn = s.getConnectivitySection();
        assertTrue(conn.contains("fully connected"));
    }

    @Test
    public void testConnectivityMultipleComponents() {
        edge e1 = addEdge("A", "B", "f");
        friends.add(e1);
        graph.addVertex("C"); // isolated
        GraphSummarizer s = makeSummarizer();
        String conn = s.getConnectivitySection();
        assertTrue(conn.contains("2 connected components"));
        assertTrue(conn.contains("isolated"));
    }

    @Test
    public void testCompositionAllFriends() {
        edge e1 = addEdge("A", "B", "f");
        friends.add(e1);
        GraphSummarizer s = makeSummarizer();
        String comp = s.getCompositionSection();
        assertTrue(comp.contains("friends"));
        assertTrue(comp.contains("friendship-based"));
    }

    @Test
    public void testCompositionMixed() {
        edge e1 = addEdge("A", "B", "f");
        friends.add(e1);
        edge e2 = addEdge("B", "C", "s");
        strangers.add(e2);
        GraphSummarizer s = makeSummarizer();
        String comp = s.getCompositionSection();
        assertTrue(comp.contains("friends"));
        assertTrue(comp.contains("strangers"));
    }

    @Test
    public void testCompositionEmpty() {
        graph.addVertex("A");
        GraphSummarizer s = makeSummarizer();
        String comp = s.getCompositionSection();
        assertTrue(comp.contains("No relationship data"));
    }

    @Test
    public void testHubSection() {
        edge e1 = addEdge("Hub", "A", "f");
        edge e2 = addEdge("Hub", "B", "f");
        edge e3 = addEdge("Hub", "C", "f");
        friends.add(e1);
        friends.add(e2);
        friends.add(e3);
        GraphSummarizer s = makeSummarizer();
        String hubs = s.getHubSection();
        assertTrue(hubs.contains("Hub"));
        assertTrue(hubs.contains("hub nodes"));
    }

    @Test
    public void testHubSectionEmpty() {
        GraphSummarizer s = makeSummarizer();
        String hubs = s.getHubSection();
        assertTrue(hubs.contains("empty graph"));
    }

    @Test
    public void testStructuralObservationsTree() {
        // 4-node tree: A-B, B-C, C-D (3 edges, 4 nodes, connected)
        edge e1 = addEdge("A", "B", "f");
        edge e2 = addEdge("B", "C", "f");
        edge e3 = addEdge("C", "D", "f");
        friends.add(e1);
        friends.add(e2);
        friends.add(e3);
        GraphSummarizer s = makeSummarizer();
        String obs = s.getStructuralObservations();
        assertTrue(obs.contains("tree structure"));
    }

    @Test
    public void testStructuralObservationsNearComplete() {
        // 4-node near-complete: 5 out of 6 edges → density 0.833
        edge e1 = addEdge("A", "B", "f");
        edge e2 = addEdge("A", "C", "f");
        edge e3 = addEdge("A", "D", "f");
        edge e4 = addEdge("B", "C", "f");
        edge e5 = addEdge("B", "D", "f");
        friends.add(e1); friends.add(e2); friends.add(e3);
        friends.add(e4); friends.add(e5);
        GraphSummarizer s = makeSummarizer();
        String obs = s.getStructuralObservations();
        assertTrue(obs.contains("near-complete"));
    }

    @Test
    public void testFullSummaryContainsAllSections() {
        edge e1 = addEdge("A", "B", "f");
        friends.add(e1);
        GraphSummarizer s = makeSummarizer();
        String full = s.generateSummary();
        assertTrue(full.contains("=== Graph Summary ==="));
        assertTrue(full.contains("density"));
        assertTrue(full.contains("hub nodes"));
    }

    @Test
    public void testNullEdgeListsHandledGracefully() {
        graph.addVertex("A");
        GraphSummarizer s = new GraphSummarizer(graph, null, null, null, null, null);
        String summary = s.generateSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("1 nodes"));
    }

    @Test
    public void testOneLinerFormat() {
        edge e1 = addEdge("A", "B", "f");
        friends.add(e1);
        GraphSummarizer s = makeSummarizer();
        String line = s.getOneLiner();
        assertTrue(line.contains("2 nodes"));
        assertTrue(line.contains("1 edges"));
        assertTrue(line.contains("1 component"));
        assertFalse(line.contains("\n"));
    }

    @Test
    public void testAverageWeightObservation() {
        edge e1 = addEdge("A", "B", "f");
        friends.add(e1);
        GraphSummarizer s = makeSummarizer();
        String obs = s.getStructuralObservations();
        assertTrue(obs.contains("Average edge weight"));
    }
}
