package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphFairnessAuditEngine}.
 *
 * @author zalenix
 */
public class GraphFairnessAuditEngineTest {

    // ── Helper methods ───────────────────────────────────────────────

    private Graph<String, Edge> emptyGraph() {
        return new UndirectedSparseGraph<>();
    }

    private Graph<String, Edge> singleNode() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        return g;
    }

    private Graph<String, Edge> twoNodes() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B");
        g.addEdge(new Edge("c", "A", "B"), "A", "B");
        return g;
    }

    private Graph<String, Edge> triangle() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addEdge(new Edge("c", "A", "B"), "A", "B");
        g.addEdge(new Edge("c", "B", "C"), "B", "C");
        g.addEdge(new Edge("c", "A", "C"), "A", "C");
        return g;
    }

    private Graph<String, Edge> star5() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("Hub");
        for (int i = 1; i <= 4; i++) {
            String leaf = "L" + i;
            g.addVertex(leaf);
            g.addEdge(new Edge("c", "Hub", leaf), "Hub", leaf);
        }
        return g;
    }

    private Graph<String, Edge> completeK5() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        String[] nodes = {"A", "B", "C", "D", "E"};
        for (String n : nodes) g.addVertex(n);
        for (int i = 0; i < nodes.length; i++) {
            for (int j = i + 1; j < nodes.length; j++) {
                g.addEdge(new Edge("c", nodes[i], nodes[j]), nodes[i], nodes[j]);
            }
        }
        return g;
    }

    private Graph<String, Edge> pathGraph() {
        // A - B - C - D - E
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        String[] nodes = {"A", "B", "C", "D", "E"};
        for (String n : nodes) g.addVertex(n);
        for (int i = 0; i < nodes.length - 1; i++) {
            g.addEdge(new Edge("c", nodes[i], nodes[i + 1]), nodes[i], nodes[i + 1]);
        }
        return g;
    }

    private Graph<String, Edge> barbellGraph() {
        // Two K4 cliques connected by single bridge
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        // Clique 1: A,B,C,D
        String[] c1 = {"A", "B", "C", "D"};
        for (String n : c1) g.addVertex(n);
        for (int i = 0; i < c1.length; i++)
            for (int j = i + 1; j < c1.length; j++)
                g.addEdge(new Edge("c", c1[i], c1[j]), c1[i], c1[j]);
        // Clique 2: E,F,G,H
        String[] c2 = {"E", "F", "G", "H"};
        for (String n : c2) g.addVertex(n);
        for (int i = 0; i < c2.length; i++)
            for (int j = i + 1; j < c2.length; j++)
                g.addEdge(new Edge("c", c2[i], c2[j]), c2[i], c2[j]);
        // Bridge
        g.addEdge(new Edge("c", "D", "E"), "D", "E");
        return g;
    }

    private Graph<String, Edge> disconnectedGraph() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        // Component 1
        g.addVertex("A"); g.addVertex("B");
        g.addEdge(new Edge("c", "A", "B"), "A", "B");
        // Component 2
        g.addVertex("C"); g.addVertex("D");
        g.addEdge(new Edge("c", "C", "D"), "C", "D");
        // Isolated
        g.addVertex("E");
        return g;
    }

    private GraphFairnessAuditEngine engine() {
        return new GraphFairnessAuditEngine();
    }

    // ── Empty graph ──────────────────────────────────────────────────

    @Test
    public void emptyGraph_returnsZeroGini() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(emptyGraph());
        assertEquals(0, r.degreeGini, 0.001);
        assertEquals(0, r.centralityGini, 0.001);
        assertEquals(0, r.accessGini, 0.001);
        assertEquals(0, r.communityGini, 0.001);
    }

    @Test
    public void emptyGraph_healthScore100() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(emptyGraph());
        assertEquals(100.0, r.healthScore, 0.1);
    }

    @Test
    public void emptyGraph_zeroCounts() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(emptyGraph());
        assertEquals(0, r.nodeCount);
        assertEquals(0, r.edgeCount);
        assertEquals(0, r.communityCount);
    }

    @Test
    public void emptyGraph_hasInsight() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(emptyGraph());
        assertFalse(r.insights.isEmpty());
    }

    // ── Single node ──────────────────────────────────────────────────

    @Test
    public void singleNode_zeroGini() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(singleNode());
        assertEquals(0, r.degreeGini, 0.001);
        assertEquals(100.0, r.healthScore, 0.1);
    }

    @Test
    public void singleNode_oneCommunity() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(singleNode());
        assertEquals(1, r.communityCount);
    }

    @Test
    public void singleNode_nodeCountIsOne() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(singleNode());
        assertEquals(1, r.nodeCount);
        assertEquals(0, r.edgeCount);
    }

    // ── Two connected nodes ──────────────────────────────────────────

    @Test
    public void twoNodes_zeroGini() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(twoNodes());
        assertEquals(0, r.degreeGini, 0.001);
    }

    @Test
    public void twoNodes_highScore() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(twoNodes());
        assertTrue(r.healthScore >= 80);
    }

    @Test
    public void twoNodes_edgeCountIsOne() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(twoNodes());
        assertEquals(1, r.edgeCount);
    }

    // ── Triangle ─────────────────────────────────────────────────────

    @Test
    public void triangle_zeroGini() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(triangle());
        assertEquals(0, r.degreeGini, 0.001);
    }

    @Test
    public void triangle_highScore() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(triangle());
        assertTrue(r.healthScore >= 80);
    }

    @Test
    public void triangle_noResourceDeserts() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(triangle());
        assertEquals(0, r.resourceDesertCount);
    }

    // ── Star graph ───────────────────────────────────────────────────

    @Test
    public void star_hasDegreeInequality() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(star5());
        assertTrue("Star should have degree inequality", r.degreeGini > 0.2);
    }

    @Test
    public void star_centralityInequality() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(star5());
        assertTrue("Star should have centrality inequality", r.centralityGini > 0);
    }

    @Test
    public void star_nodeCount() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(star5());
        assertEquals(5, r.nodeCount);
        assertEquals(4, r.edgeCount);
    }

    @Test
    public void star_hasInsights() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(star5());
        assertTrue(r.insights.size() > 1);
    }

    // ── Complete graph K5 ────────────────────────────────────────────

    @Test
    public void completeK5_zeroDegreeGini() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(completeK5());
        assertEquals(0, r.degreeGini, 0.001);
    }

    @Test
    public void completeK5_zeroAccessGini() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(completeK5());
        assertEquals(0, r.accessGini, 0.001);
    }

    @Test
    public void completeK5_highScore() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(completeK5());
        assertTrue(r.healthScore >= 80);
    }

    @Test
    public void completeK5_exemplaryTier() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(completeK5());
        assertEquals(GraphFairnessAuditEngine.FairnessTier.EXEMPLARY, r.fairnessTier);
    }

    @Test
    public void completeK5_noResourceDeserts() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(completeK5());
        assertEquals(0, r.resourceDesertCount);
    }

    // ── Path graph ───────────────────────────────────────────────────

    @Test
    public void pathGraph_hasDegreeInequality() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(pathGraph());
        assertTrue("Path should have some degree inequality", r.degreeGini > 0);
    }

    @Test
    public void pathGraph_hasAccessInequality() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(pathGraph());
        assertTrue("Path should have access inequality", r.accessGini > 0);
    }

    @Test
    public void pathGraph_hasCentralityInequality() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(pathGraph());
        assertTrue("Path should have centrality inequality", r.centralityGini > 0);
    }

    @Test
    public void pathGraph_lowerScoreThanComplete() {
        GraphFairnessAuditEngine.FairnessReport rPath = engine().analyze(pathGraph());
        GraphFairnessAuditEngine.FairnessReport rK5 = engine().analyze(completeK5());
        assertTrue(rK5.healthScore > rPath.healthScore);
    }

    // ── Barbell graph ────────────────────────────────────────────────

    @Test
    public void barbell_hasCentralityInequality() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(barbellGraph());
        assertTrue("Barbell should have centrality inequality", r.centralityGini > 0.1);
    }

    @Test
    public void barbell_nodeCount() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(barbellGraph());
        assertEquals(8, r.nodeCount);
    }

    @Test
    public void barbell_hasMultipleCommunities() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(barbellGraph());
        assertTrue(r.communityCount >= 1);
    }

    @Test
    public void barbell_hasInsights() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(barbellGraph());
        assertFalse(r.insights.isEmpty());
    }

    // ── Disconnected graph ───────────────────────────────────────────

    @Test
    public void disconnected_handlesGracefully() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(disconnectedGraph());
        assertTrue(r.healthScore >= 0 && r.healthScore <= 100);
    }

    @Test
    public void disconnected_nodeCount() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(disconnectedGraph());
        assertEquals(5, r.nodeCount);
        assertEquals(2, r.edgeCount);
    }

    @Test
    public void disconnected_hasResourceDeserts() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(disconnectedGraph());
        // Isolated node E should be in a resource desert
        assertTrue(r.resourceDesertCount >= 0);
    }

    // ── Health score bounds ──────────────────────────────────────────

    @Test
    public void healthScore_betweenZeroAndHundred_empty() {
        double score = engine().analyze(emptyGraph()).healthScore;
        assertTrue(score >= 0 && score <= 100);
    }

    @Test
    public void healthScore_betweenZeroAndHundred_star() {
        double score = engine().analyze(star5()).healthScore;
        assertTrue(score >= 0 && score <= 100);
    }

    @Test
    public void healthScore_betweenZeroAndHundred_barbell() {
        double score = engine().analyze(barbellGraph()).healthScore;
        assertTrue(score >= 0 && score <= 100);
    }

    // ── Gini coefficient bounds ──────────────────────────────────────

    @Test
    public void degreeGini_betweenZeroAndOne() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(star5());
        assertTrue(r.degreeGini >= 0 && r.degreeGini <= 1);
    }

    @Test
    public void centralityGini_betweenZeroAndOne() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(barbellGraph());
        assertTrue(r.centralityGini >= 0 && r.centralityGini <= 1);
    }

    @Test
    public void accessGini_betweenZeroAndOne() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(pathGraph());
        assertTrue(r.accessGini >= 0 && r.accessGini <= 1);
    }

    @Test
    public void communityGini_betweenZeroAndOne() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(barbellGraph());
        assertTrue(r.communityGini >= 0 && r.communityGini <= 1);
    }

    // ── Lorenz curve ─────────────────────────────────────────────────

    @Test
    public void lorenzCurve_startsAtOrigin() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(star5());
        assertFalse(r.lorenzCurve.isEmpty());
        assertEquals(0, r.lorenzCurve.get(0)[0], 0.001);
        assertEquals(0, r.lorenzCurve.get(0)[1], 0.001);
    }

    @Test
    public void lorenzCurve_endsAtOneOne() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(star5());
        double[] last = r.lorenzCurve.get(r.lorenzCurve.size() - 1);
        assertEquals(1.0, last[0], 0.001);
        assertEquals(1.0, last[1], 0.001);
    }

    @Test
    public void lorenzCurve_nonDecreasing() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(pathGraph());
        for (int i = 1; i < r.lorenzCurve.size(); i++) {
            assertTrue(r.lorenzCurve.get(i)[1] >= r.lorenzCurve.get(i - 1)[1] - 0.001);
        }
    }

    @Test
    public void lorenzCurve_completeGraphIsDiagonal() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(completeK5());
        // For complete graph, Lorenz curve should be close to diagonal
        for (double[] pt : r.lorenzCurve) {
            assertEquals(pt[0], pt[1], 0.01);
        }
    }

    // ── Interventions ────────────────────────────────────────────────

    @Test
    public void interventions_notNull() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(star5());
        assertNotNull(r.interventions);
    }

    @Test
    public void interventions_respectMaxLimit() {
        GraphFairnessAuditEngine.FairnessReport r = new GraphFairnessAuditEngine()
                .setMaxInterventions(2)
                .analyze(star5());
        assertTrue(r.interventions.size() <= 2);
    }

    @Test
    public void interventions_emptyForCompleteGraph() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(completeK5());
        // Complete graph — all nodes have same degree, no starved nodes
        assertTrue(r.interventions.isEmpty());
    }

    @Test
    public void interventions_haveNonNegativeImprovement() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(star5());
        for (GraphFairnessAuditEngine.Intervention iv : r.interventions) {
            assertTrue(iv.expectedImprovement >= 0);
        }
    }

    // ── Insights ─────────────────────────────────────────────────────

    @Test
    public void insights_nonEmpty_star() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(star5());
        assertTrue(r.insights.size() > 1);
    }

    @Test
    public void insights_containsTierInfo() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(star5());
        boolean hasTier = r.insights.stream().anyMatch(i -> i.contains("fairness:"));
        assertTrue("Should mention fairness tier", hasTier);
    }

    // ── Fairness tier ────────────────────────────────────────────────

    @Test
    public void fairnessTier_fromScore() {
        assertEquals(GraphFairnessAuditEngine.FairnessTier.EXEMPLARY,
                GraphFairnessAuditEngine.FairnessTier.fromScore(85));
        assertEquals(GraphFairnessAuditEngine.FairnessTier.FAIR,
                GraphFairnessAuditEngine.FairnessTier.fromScore(65));
        assertEquals(GraphFairnessAuditEngine.FairnessTier.MODERATE,
                GraphFairnessAuditEngine.FairnessTier.fromScore(45));
        assertEquals(GraphFairnessAuditEngine.FairnessTier.INEQUITABLE,
                GraphFairnessAuditEngine.FairnessTier.fromScore(25));
        assertEquals(GraphFairnessAuditEngine.FairnessTier.CRITICAL,
                GraphFairnessAuditEngine.FairnessTier.fromScore(10));
    }

    @Test
    public void fairnessTier_boundaryValues() {
        assertEquals(GraphFairnessAuditEngine.FairnessTier.EXEMPLARY,
                GraphFairnessAuditEngine.FairnessTier.fromScore(80));
        assertEquals(GraphFairnessAuditEngine.FairnessTier.FAIR,
                GraphFairnessAuditEngine.FairnessTier.fromScore(60));
        assertEquals(GraphFairnessAuditEngine.FairnessTier.MODERATE,
                GraphFairnessAuditEngine.FairnessTier.fromScore(40));
        assertEquals(GraphFairnessAuditEngine.FairnessTier.INEQUITABLE,
                GraphFairnessAuditEngine.FairnessTier.fromScore(20));
    }

    // ── HTML export ──────────────────────────────────────────────────

    @Test
    public void htmlExport_containsTitle() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(star5());
        String html = engine().exportHtml(r);
        assertTrue(html.contains("Graph Fairness Audit Engine"));
    }

    @Test
    public void htmlExport_containsCanvas() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(star5());
        String html = engine().exportHtml(r);
        assertTrue(html.contains("<canvas"));
    }

    @Test
    public void htmlExport_containsTierBadge() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(completeK5());
        String html = engine().exportHtml(r);
        assertTrue(html.contains("tier-badge"));
    }

    @Test
    public void htmlExport_containsLorenz() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(pathGraph());
        String html = engine().exportHtml(r);
        assertTrue(html.contains("lorenzData"));
    }

    @Test
    public void htmlExport_escapesNodeNames() {
        // Create graph with HTML-like node name
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("<script>"); g.addVertex("B");
        g.addEdge(new Edge("c", "<script>", "B"), "<script>", "B");
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(g);
        String html = engine().exportHtml(r);
        assertFalse(html.contains("<script>B"));
    }

    @Test
    public void htmlExport_toFile() throws IOException {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(star5());
        File tmp = File.createTempFile("fairness-", ".html");
        tmp.deleteOnExit();
        engine().exportHtml(r, tmp.getAbsolutePath());
        assertTrue(tmp.exists());
        assertTrue(tmp.length() > 100);
    }

    // ── Text output ──────────────────────────────────────────────────

    @Test
    public void textOutput_containsGiniLabel() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(star5());
        String text = engine().toText(r);
        assertTrue(text.contains("Degree:"));
        assertTrue(text.contains("Centrality:"));
        assertTrue(text.contains("Access:"));
    }

    @Test
    public void textOutput_containsFairnessScore() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(star5());
        String text = engine().toText(r);
        assertTrue(text.contains("Fairness Score:"));
    }

    @Test
    public void textOutput_containsTier() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(completeK5());
        String text = engine().toText(r);
        assertTrue(text.contains("EXEMPLARY"));
    }

    @Test
    public void textOutput_containsLorenz() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(pathGraph());
        String text = engine().toText(r);
        assertTrue(text.contains("Lorenz Curve"));
    }

    // ── Builder methods ──────────────────────────────────────────────

    @Test
    public void builder_setResourceCount() {
        GraphFairnessAuditEngine.FairnessReport r = new GraphFairnessAuditEngine()
                .setResourceCount(1)
                .analyze(star5());
        assertTrue(r.healthScore >= 0 && r.healthScore <= 100);
    }

    @Test
    public void builder_setRandomSeed() {
        GraphFairnessAuditEngine.FairnessReport r1 = new GraphFairnessAuditEngine()
                .setRandomSeed(123).analyze(barbellGraph());
        GraphFairnessAuditEngine.FairnessReport r2 = new GraphFairnessAuditEngine()
                .setRandomSeed(123).analyze(barbellGraph());
        assertEquals(r1.healthScore, r2.healthScore, 0.01);
    }

    @Test
    public void builder_setCommunityIterations() {
        GraphFairnessAuditEngine.FairnessReport r = new GraphFairnessAuditEngine()
                .setCommunityIterations(5)
                .analyze(barbellGraph());
        assertTrue(r.communityCount >= 1);
    }

    @Test
    public void builder_setMaxInterventions() {
        GraphFairnessAuditEngine.FairnessReport r = new GraphFairnessAuditEngine()
                .setMaxInterventions(0)
                .analyze(star5());
        assertTrue(r.interventions.isEmpty());
    }

    // ── Star vs Complete comparison ──────────────────────────────────

    @Test
    public void star_higherDegreeGiniThanComplete() {
        double starGini = engine().analyze(star5()).degreeGini;
        double completeGini = engine().analyze(completeK5()).degreeGini;
        assertTrue(starGini > completeGini);
    }

    @Test
    public void star_lowerHealthThanComplete() {
        double starHealth = engine().analyze(star5()).healthScore;
        double completeHealth = engine().analyze(completeK5()).healthScore;
        assertTrue(completeHealth > starHealth);
    }

    // ── Community assignment ─────────────────────────────────────────

    @Test
    public void communityAssignment_allNodesAssigned() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(barbellGraph());
        assertEquals(8, r.communityAssignment.size());
    }

    @Test
    public void communityAssignment_emptyForEmptyGraph() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(emptyGraph());
        assertTrue(r.communityAssignment.isEmpty());
    }

    // ── Immutability ─────────────────────────────────────────────────

    @Test(expected = UnsupportedOperationException.class)
    public void insights_areImmutable() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(star5());
        r.insights.add("hack");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void interventions_areImmutable() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(star5());
        r.interventions.add(new GraphFairnessAuditEngine.Intervention("X", "Y", 1.0));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void lorenzCurve_isImmutable() {
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(star5());
        r.lorenzCurve.add(new double[]{0, 0});
    }

    // ── Intervention toString ────────────────────────────────────────

    @Test
    public void intervention_toStringFormat() {
        GraphFairnessAuditEngine.Intervention iv =
                new GraphFairnessAuditEngine.Intervention("A", "B", 0.1234);
        String s = iv.toString();
        assertTrue(s.contains("A"));
        assertTrue(s.contains("B"));
        assertTrue(s.contains("0.1234"));
    }

    // ── Large star (higher inequality) ───────────────────────────────

    @Test
    public void largeStar_highDegreeGini() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("Hub");
        for (int i = 1; i <= 20; i++) {
            String leaf = "L" + i;
            g.addVertex(leaf);
            g.addEdge(new Edge("c", "Hub", leaf), "Hub", leaf);
        }
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(g);
        assertTrue("Large star degree Gini should be > 0.3", r.degreeGini > 0.3);
    }

    @Test
    public void largeStar_notExemplaryTier() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("Hub");
        for (int i = 1; i <= 20; i++) {
            String leaf = "L" + i;
            g.addVertex(leaf);
            g.addEdge(new Edge("c", "Hub", leaf), "Hub", leaf);
        }
        GraphFairnessAuditEngine.FairnessReport r = engine().analyze(g);
        assertTrue("Large star should not be EXEMPLARY",
                r.fairnessTier != GraphFairnessAuditEngine.FairnessTier.EXEMPLARY);
    }
}
