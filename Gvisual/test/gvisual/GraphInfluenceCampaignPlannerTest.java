package gvisual;

import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.util.*;

/**
 * Tests for GraphInfluenceCampaignPlanner — autonomous influence campaign
 * planning engine with budget-constrained seed selection, multi-wave
 * campaigns, competitive analysis, ROI optimization, and sustainability.
 */
public class GraphInfluenceCampaignPlannerTest {

    private GraphInfluenceCampaignPlanner planner;

    @Before
    public void setUp() {
        planner = new GraphInfluenceCampaignPlanner()
                .setRandomSeed(42)
                .setMonteCarloTrials(20);
    }

    // ── Helper: build undirected graph ──────────────────────────

    private Graph<String, Edge> buildStarGraph(int spokes) {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("hub");
        for (int i = 1; i <= spokes; i++) {
            String v = "n" + i;
            g.addVertex(v);
            g.addEdge(new Edge("e", "hub", v), "hub", v);
        }
        return g;
    }

    private Graph<String, Edge> buildChainGraph(int length) {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < length; i++) g.addVertex("n" + i);
        for (int i = 0; i < length - 1; i++) {
            g.addEdge(new Edge("e", "n" + i, "n" + (i + 1)), "n" + i, "n" + (i + 1));
        }
        return g;
    }

    private Graph<String, Edge> buildCompleteGraph(int n) {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < n; i++) g.addVertex("n" + i);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                g.addEdge(new Edge("e", "n" + i, "n" + j), "n" + i, "n" + j);
            }
        }
        return g;
    }

    private Graph<String, Edge> buildTwoClusterGraph() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        // Cluster A
        for (int i = 0; i < 5; i++) g.addVertex("a" + i);
        for (int i = 0; i < 5; i++)
            for (int j = i + 1; j < 5; j++)
                g.addEdge(new Edge("e", "a" + i, "a" + j), "a" + i, "a" + j);
        // Cluster B
        for (int i = 0; i < 5; i++) g.addVertex("b" + i);
        for (int i = 0; i < 5; i++)
            for (int j = i + 1; j < 5; j++)
                g.addEdge(new Edge("e", "b" + i, "b" + j), "b" + i, "b" + j);
        // Bridge
        g.addEdge(new Edge("e", "a4", "b0"), "a4", "b0");
        return g;
    }

    // ── Basic sanity ────────────────────────────────────────────

    @Test
    public void testAnalyzeNotNull() {
        Graph<String, Edge> g = buildStarGraph(5);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertNotNull(r);
    }

    @Test
    public void testHealthScoreRange() {
        Graph<String, Edge> g = buildCompleteGraph(8);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertTrue(r.healthScore >= 0 && r.healthScore <= 100);
    }

    @Test
    public void testNodeAndEdgeCounts() {
        Graph<String, Edge> g = buildStarGraph(6);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertEquals(7, r.nodesAnalyzed);
        assertEquals(6, r.edgesAnalyzed);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        planner.analyze(null);
    }

    @Test
    public void testEmptyGraph() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertEquals(0, r.nodesAnalyzed);
        assertEquals(0, r.allSeeds.size());
        assertTrue(r.insights.size() > 0);
    }

    // ── Seed Selection ──────────────────────────────────────────

    @Test
    public void testSeedsSelected() {
        Graph<String, Edge> g = buildStarGraph(10);
        planner.setMaxSeedsPerWave(3).setMaxWaves(2);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertTrue(r.allSeeds.size() > 0);
        assertTrue(r.allSeeds.size() <= 6); // 3 * 2
    }

    @Test
    public void testHubSelectedFirst() {
        Graph<String, Edge> g = buildStarGraph(10);
        planner.setMaxSeedsPerWave(1).setMaxWaves(1);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertEquals(1, r.allSeeds.size());
        assertEquals("hub", r.allSeeds.get(0).node);
    }

    @Test
    public void testSeedRanksAscending() {
        Graph<String, Edge> g = buildCompleteGraph(8);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        for (int i = 0; i < r.allSeeds.size(); i++) {
            assertEquals(i + 1, r.allSeeds.get(i).rank);
        }
    }

    @Test
    public void testSeedEfficiencyPositive() {
        Graph<String, Edge> g = buildTwoClusterGraph();
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        for (GraphInfluenceCampaignPlanner.SeedCandidate s : r.allSeeds) {
            assertTrue(s.efficiency >= 0);
        }
    }

    @Test
    public void testSeedCostPositive() {
        Graph<String, Edge> g = buildStarGraph(5);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        for (GraphInfluenceCampaignPlanner.SeedCandidate s : r.allSeeds) {
            assertTrue(s.cost > 0);
        }
    }

    @Test
    public void testBudgetConstraint() {
        Graph<String, Edge> g = buildCompleteGraph(10);
        planner.setTotalBudget(3.0).setMaxSeedsPerWave(5).setMaxWaves(3);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertTrue(r.totalCost <= 3.0 + 0.01); // small float tolerance
    }

    @Test
    public void testNoDuplicateSeeds() {
        Graph<String, Edge> g = buildTwoClusterGraph();
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        Set<String> seen = new HashSet<>();
        for (GraphInfluenceCampaignPlanner.SeedCandidate s : r.allSeeds) {
            assertTrue("Duplicate seed: " + s.node, seen.add(s.node));
        }
    }

    // ── Multi-Wave Campaign ─────────────────────────────────────

    @Test
    public void testWavesCreated() {
        Graph<String, Edge> g = buildTwoClusterGraph();
        planner.setMaxWaves(3).setMaxSeedsPerWave(2);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertTrue(r.waves.size() >= 1);
        assertTrue(r.waves.size() <= 3);
    }

    @Test
    public void testWaveNumbersSequential() {
        Graph<String, Edge> g = buildCompleteGraph(8);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        for (int i = 0; i < r.waves.size(); i++) {
            assertEquals(i + 1, r.waves.get(i).waveNumber);
        }
    }

    @Test
    public void testCumulativeReachNonDecreasing() {
        Graph<String, Edge> g = buildTwoClusterGraph();
        planner.setMaxWaves(3).setMaxSeedsPerWave(3);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        double prev = 0;
        for (GraphInfluenceCampaignPlanner.CampaignWave w : r.waves) {
            assertTrue(w.cumulativeReach >= prev - 0.01);
            prev = w.cumulativeReach;
        }
    }

    @Test
    public void testFirstWaveIsInitialPush() {
        Graph<String, Edge> g = buildStarGraph(8);
        planner.setMaxWaves(2);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        if (!r.waves.isEmpty()) {
            assertEquals(GraphInfluenceCampaignPlanner.WavePhase.INITIAL_PUSH,
                    r.waves.get(0).phase);
        }
    }

    @Test
    public void testWaveSeedsNonEmpty() {
        Graph<String, Edge> g = buildCompleteGraph(6);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        for (GraphInfluenceCampaignPlanner.CampaignWave w : r.waves) {
            assertTrue(w.seeds.size() > 0);
        }
    }

    @Test
    public void testCumulativeCostNonDecreasing() {
        Graph<String, Edge> g = buildTwoClusterGraph();
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        double prev = 0;
        for (GraphInfluenceCampaignPlanner.CampaignWave w : r.waves) {
            assertTrue(w.cumulativeCost >= prev - 0.01);
            prev = w.cumulativeCost;
        }
    }

    // ── Competitive Analysis ────────────────────────────────────

    @Test
    public void testCompetitiveNotNull() {
        Graph<String, Edge> g = buildStarGraph(6);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertNotNull(r.competitive);
    }

    @Test
    public void testCompetitiveTerritoryCoversAll() {
        Graph<String, Edge> g = buildCompleteGraph(6);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        GraphInfluenceCampaignPlanner.CompetitiveResult c = r.competitive;
        assertEquals(g.getVertexCount(),
                c.campaignATerritory + c.campaignBTerritory + c.contested + c.unreached);
    }

    @Test
    public void testCompetitiveDominanceRange() {
        Graph<String, Edge> g = buildTwoClusterGraph();
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertTrue(r.competitive.campaignADominance >= 0 && r.competitive.campaignADominance <= 1);
        assertTrue(r.competitive.campaignBDominance >= 0 && r.competitive.campaignBDominance <= 1);
    }

    @Test
    public void testCompetitiveSeedSetsDisjoint() {
        Graph<String, Edge> g = buildTwoClusterGraph();
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        Set<String> intersection = new HashSet<>(r.competitive.campaignASeeds);
        intersection.retainAll(r.competitive.campaignBSeeds);
        assertTrue(intersection.isEmpty());
    }

    @Test
    public void testCompetitiveTerritoryMapSize() {
        Graph<String, Edge> g = buildStarGraph(5);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertEquals(g.getVertexCount(), r.competitive.territoryMap.size());
    }

    // ── ROI Analysis ────────────────────────────────────────────

    @Test
    public void testROINotNull() {
        Graph<String, Edge> g = buildStarGraph(6);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertNotNull(r.roi);
    }

    @Test
    public void testROICurveLength() {
        Graph<String, Edge> g = buildCompleteGraph(6);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertEquals(r.allSeeds.size(), r.roi.roiCurve.size());
    }

    @Test
    public void testROIPeakPositive() {
        Graph<String, Edge> g = buildStarGraph(10);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertTrue(r.roi.peakROI >= 0);
    }

    @Test
    public void testROIOverallPositive() {
        Graph<String, Edge> g = buildCompleteGraph(8);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertTrue(r.roi.overallROI >= 0);
    }

    @Test
    public void testROIBreakpointRange() {
        Graph<String, Edge> g = buildTwoClusterGraph();
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertTrue(r.roi.diminishingReturnsBreakpoint >= 0);
        assertTrue(r.roi.diminishingReturnsBreakpoint <= r.allSeeds.size());
    }

    @Test
    public void testROIOptimalBudgetNonNegative() {
        Graph<String, Edge> g = buildStarGraph(5);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertTrue(r.roi.optimalBudget >= 0);
    }

    // ── Sustainability Analysis ─────────────────────────────────

    @Test
    public void testSustainabilityNotNull() {
        Graph<String, Edge> g = buildStarGraph(6);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertNotNull(r.sustainability);
    }

    @Test
    public void testSustainabilityHalfLifePositive() {
        Graph<String, Edge> g = buildCompleteGraph(8);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertTrue(r.sustainability.halfLife >= 0);
    }

    @Test
    public void testSustainabilityDecayRateRange() {
        Graph<String, Edge> g = buildStarGraph(10);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertTrue(r.sustainability.decayRate >= 0 && r.sustainability.decayRate <= 1.0);
    }

    @Test
    public void testSustainabilityScoreRange() {
        Graph<String, Edge> g = buildTwoClusterGraph();
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertTrue(r.sustainability.sustainabilityScore >= 0);
        assertTrue(r.sustainability.sustainabilityScore <= 100);
    }

    @Test
    public void testSustainabilityDecayCurve() {
        Graph<String, Edge> g = buildCompleteGraph(6);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertTrue(r.sustainability.decayCurve.size() > 0);
        assertEquals(0.0, r.sustainability.decayCurve.get(0)[0], 0.01); // starts at round 0
    }

    // ── Report Totals ───────────────────────────────────────────

    @Test
    public void testTotalCostPositive() {
        Graph<String, Edge> g = buildStarGraph(8);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        if (r.allSeeds.size() > 0) {
            assertTrue(r.totalCost > 0);
        }
    }

    @Test
    public void testReachPercentageRange() {
        Graph<String, Edge> g = buildCompleteGraph(6);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertTrue(r.reachPercentage >= 0 && r.reachPercentage <= 100);
    }

    @Test
    public void testTotalReachNonNegative() {
        Graph<String, Edge> g = buildTwoClusterGraph();
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertTrue(r.totalReach >= 0);
    }

    // ── Insights ────────────────────────────────────────────────

    @Test
    public void testInsightsGenerated() {
        Graph<String, Edge> g = buildTwoClusterGraph();
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertTrue(r.insights.size() > 0);
    }

    @Test
    public void testInsightsNonEmpty() {
        Graph<String, Edge> g = buildStarGraph(6);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        for (String insight : r.insights) {
            assertNotNull(insight);
            assertFalse(insight.trim().isEmpty());
        }
    }

    // ── Text Output ─────────────────────────────────────────────

    @Test
    public void testToTextNotNull() {
        Graph<String, Edge> g = buildStarGraph(5);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        String text = planner.toText(r);
        assertNotNull(text);
        assertTrue(text.contains("CAMPAIGN PLANNER"));
    }

    @Test
    public void testToTextContainsSeedRankings() {
        Graph<String, Edge> g = buildCompleteGraph(6);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        String text = planner.toText(r);
        assertTrue(text.contains("SEED RANKINGS"));
    }

    @Test
    public void testToTextContainsWaves() {
        Graph<String, Edge> g = buildTwoClusterGraph();
        planner.setMaxWaves(2);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        String text = planner.toText(r);
        assertTrue(text.contains("CAMPAIGN WAVES"));
    }

    // ── HTML Export ─────────────────────────────────────────────

    @Test
    public void testExportHtml() {
        Graph<String, Edge> g = buildStarGraph(6);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        String html = planner.exportHtml(r);
        assertNotNull(html);
        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertTrue(html.contains("Campaign Planner"));
    }

    @Test
    public void testExportHtmlContainsSections() {
        Graph<String, Edge> g = buildTwoClusterGraph();
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        String html = planner.exportHtml(r);
        assertTrue(html.contains("Seed Rankings"));
        assertTrue(html.contains("Competitive Analysis"));
        assertTrue(html.contains("ROI Analysis"));
        assertTrue(html.contains("Sustainability"));
    }

    @Test
    public void testExportHtmlToFile() throws Exception {
        Graph<String, Edge> g = buildCompleteGraph(6);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        File tmp = File.createTempFile("campaign_test_", ".html");
        try {
            planner.exportHtmlToFile(r, tmp.getAbsolutePath());
            assertTrue(tmp.exists());
            assertTrue(tmp.length() > 100);
        } finally {
            tmp.delete();
        }
    }

    // ── Directed graph support ──────────────────────────────────

    @Test
    public void testDirectedGraph() {
        DirectedSparseGraph<String, Edge> g = new DirectedSparseGraph<>();
        g.addVertex("a"); g.addVertex("b"); g.addVertex("c"); g.addVertex("d");
        g.addEdge(new Edge("e", "a", "b"), "a", "b");
        g.addEdge(new Edge("e", "a", "c"), "a", "c");
        g.addEdge(new Edge("e", "b", "d"), "b", "d");
        g.addEdge(new Edge("e", "c", "d"), "c", "d");
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertNotNull(r);
        assertTrue(r.healthScore >= 0);
    }

    // ── Single node graph ───────────────────────────────────────

    @Test
    public void testSingleNode() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("alone");
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertNotNull(r);
        assertEquals(1, r.nodesAnalyzed);
    }

    // ── Disconnected components ─────────────────────────────────

    @Test
    public void testDisconnectedGraph() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        // Component 1
        g.addVertex("a1"); g.addVertex("a2");
        g.addEdge(new Edge("e", "a1", "a2"), "a1", "a2");
        // Component 2
        g.addVertex("b1"); g.addVertex("b2");
        g.addEdge(new Edge("e", "b1", "b2"), "b1", "b2");
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertNotNull(r);
        assertEquals(4, r.nodesAnalyzed);
    }

    // ── Configuration ───────────────────────────────────────────

    @Test
    public void testCustomProbability() {
        Graph<String, Edge> g = buildStarGraph(5);
        planner.setDefaultProbability(0.5);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertNotNull(r);
        // Higher probability should generally yield higher reach
        assertTrue(r.reachPercentage >= 0);
    }

    @Test
    public void testCustomMaxWaves() {
        Graph<String, Edge> g = buildCompleteGraph(10);
        planner.setMaxWaves(5).setMaxSeedsPerWave(2);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertTrue(r.waves.size() <= 5);
    }

    @Test
    public void testChainMethodReturnsThis() {
        GraphInfluenceCampaignPlanner p = new GraphInfluenceCampaignPlanner();
        assertSame(p, p.setMonteCarloTrials(10));
        assertSame(p, p.setDefaultProbability(0.2));
        assertSame(p, p.setMaxWaves(2));
        assertSame(p, p.setTotalBudget(100));
        assertSame(p, p.setMaxSeedsPerWave(3));
        assertSame(p, p.setRandomSeed(99));
    }

    // ── Edge weight support ─────────────────────────────────────

    @Test
    public void testWeightedEdges() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("a"); g.addVertex("b"); g.addVertex("c");
        Edge ab = new Edge("e", "a", "b"); ab.setWeight(0.9f);
        Edge bc = new Edge("e", "b", "c"); bc.setWeight(0.1f);
        g.addEdge(ab, "a", "b");
        g.addEdge(bc, "b", "c");
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertNotNull(r);
        assertTrue(r.reachPercentage >= 0);
    }

    // ── Large graph ─────────────────────────────────────────────

    @Test
    public void testLargerGraph() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < 50; i++) g.addVertex("v" + i);
        Random rand = new Random(123);
        for (int i = 0; i < 50; i++) {
            for (int j = i + 1; j < 50; j++) {
                if (rand.nextDouble() < 0.15) {
                    g.addEdge(new Edge("e", "v" + i, "v" + j), "v" + i, "v" + j);
                }
            }
        }
        planner.setMonteCarloTrials(10);
        GraphInfluenceCampaignPlanner.CampaignReport r = planner.analyze(g);
        assertNotNull(r);
        assertEquals(50, r.nodesAnalyzed);
        assertTrue(r.healthScore >= 0 && r.healthScore <= 100);
    }
}
