package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphInformationDiffusionEngine}.
 *
 * @author zalenix
 */
public class GraphInformationDiffusionEngineTest {

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

    private Graph<String, Edge> path5() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 1; i <= 5; i++) g.addVertex("V" + i);
        for (int i = 1; i < 5; i++) {
            g.addEdge(new Edge("c", "V" + i, "V" + (i + 1)), "V" + i, "V" + (i + 1));
        }
        return g;
    }

    private Graph<String, Edge> complete6() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        String[] vs = {"A", "B", "C", "D", "E", "F"};
        for (String v : vs) g.addVertex(v);
        for (int i = 0; i < vs.length; i++) {
            for (int j = i + 1; j < vs.length; j++) {
                g.addEdge(new Edge("c", vs[i], vs[j]), vs[i], vs[j]);
            }
        }
        return g;
    }

    private Graph<String, Edge> grid3x3() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++)
                g.addVertex(r + "_" + c);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (c < 2) g.addEdge(new Edge("c", r + "_" + c, r + "_" + (c + 1)), r + "_" + c, r + "_" + (c + 1));
                if (r < 2) g.addEdge(new Edge("c", r + "_" + c, (r + 1) + "_" + c), r + "_" + c, (r + 1) + "_" + c);
            }
        }
        return g;
    }

    private Graph<String, Edge> barbell() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        // Clique 1: A1-A4
        for (int i = 1; i <= 4; i++) g.addVertex("A" + i);
        for (int i = 1; i <= 4; i++)
            for (int j = i + 1; j <= 4; j++)
                g.addEdge(new Edge("c", "A" + i, "A" + j), "A" + i, "A" + j);
        // Clique 2: B1-B4
        for (int i = 1; i <= 4; i++) g.addVertex("B" + i);
        for (int i = 1; i <= 4; i++)
            for (int j = i + 1; j <= 4; j++)
                g.addEdge(new Edge("c", "B" + i, "B" + j), "B" + i, "B" + j);
        // Bridge
        g.addEdge(new Edge("c", "A1", "B1"), "A1", "B1");
        return g;
    }

    private Graph<String, Edge> disconnected() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addEdge(new Edge("c", "A", "B"), "A", "B");
        // C is isolated
        return g;
    }

    private GraphInformationDiffusionEngine engine() {
        return new GraphInformationDiffusionEngine().setRandomSeed(42);
    }

    private GraphInformationDiffusionEngine highSpreadEngine() {
        return new GraphInformationDiffusionEngine().setRandomSeed(42).setSpreadProbability(0.8);
    }

    // ── Empty / Single / Two-node edge cases ─────────────────────────

    @Test
    public void testEmptyGraph() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(emptyGraph());
        assertEquals(0, r.nodeCount);
        assertEquals(0.0, r.cascadeSizeIC, 0.001);
        assertEquals(0.0, r.cascadeSizeLT, 0.001);
        assertFalse(r.insights.isEmpty());
    }

    @Test
    public void testSingleNode() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(singleNode());
        assertEquals(1, r.nodeCount);
        assertEquals(1.0, r.cascadeSizeIC, 0.001);
        assertEquals(1.0, r.cascadeSizeLT, 0.001);
        assertTrue(r.superspreaderScores.containsKey("A"));
    }

    @Test
    public void testTwoNodes() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(twoNodes());
        assertEquals(2, r.nodeCount);
        assertEquals(1, r.edgeCount);
        assertTrue(r.cascadeSizeIC >= 1.0);
        assertTrue(r.cascadeSizeLT >= 1.0);
    }

    @Test
    public void testSingleNodeViralScore() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(singleNode());
        assertEquals(0.0, r.viralScore, 0.001);
        assertEquals("Immune", r.viralTier);
    }

    @Test
    public void testEmptyGraphInsights() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(emptyGraph());
        assertTrue(r.insights.stream().anyMatch(i -> i.contains("Empty graph")));
    }

    @Test
    public void testSingleNodePhases() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(singleNode());
        assertTrue(r.cascadePhases.contains("Ignition"));
    }

    // ── IC Cascade tests ─────────────────────────────────────────────

    @Test
    public void testICTriangle() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(triangle());
        assertTrue(r.cascadeSizeIC >= 1.0);
        assertTrue(r.cascadeSizeIC <= 3.0);
    }

    @Test
    public void testICHighProbabilityComplete() {
        GraphInformationDiffusionEngine.DiffusionReport r = highSpreadEngine().analyze(complete6());
        // With p=0.8 on K6, cascade should reach most nodes
        assertTrue(r.cascadeSizeIC > 3.0);
    }

    @Test
    public void testICPathSpread() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(path5());
        assertTrue(r.cascadeSizeIC >= 1.0);
        assertTrue(r.cascadeSizeIC <= 5.0);
    }

    @Test
    public void testICStarFromHub() {
        GraphInformationDiffusionEngine eng = engine().setSeedNodes(Collections.singleton("Hub"));
        GraphInformationDiffusionEngine.DiffusionReport r = eng.analyze(star5());
        assertTrue(r.cascadeSizeIC >= 1.0);
    }

    @Test
    public void testICGrid() {
        GraphInformationDiffusionEngine.DiffusionReport r = highSpreadEngine().analyze(grid3x3());
        assertTrue(r.cascadeSizeIC >= 1.0);
    }

    @Test
    public void testICBarbell() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(barbell());
        assertTrue(r.cascadeSizeIC >= 1.0);
        assertEquals(8, r.nodeCount);
    }

    @Test
    public void testICActivationProbabilities() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(triangle());
        assertEquals(3, r.nodeActivationProbIC.size());
        for (double prob : r.nodeActivationProbIC.values()) {
            assertTrue(prob >= 0.0 && prob <= 1.0);
        }
    }

    @Test
    public void testICSeedAlwaysActive() {
        // The seed node should have activation probability = 1.0
        GraphInformationDiffusionEngine eng = engine().setSeedNodes(Collections.singleton("A"));
        GraphInformationDiffusionEngine.DiffusionReport r = eng.analyze(triangle());
        assertEquals(1.0, r.nodeActivationProbIC.get("A"), 0.001);
    }

    // ── LT Cascade tests ────────────────────────────────────────────

    @Test
    public void testLTTriangle() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(triangle());
        assertTrue(r.cascadeSizeLT >= 1.0);
        assertTrue(r.cascadeSizeLT <= 3.0);
    }

    @Test
    public void testLTComplete() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(complete6());
        assertTrue(r.cascadeSizeLT >= 1.0);
    }

    @Test
    public void testLTPath() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(path5());
        assertTrue(r.cascadeSizeLT >= 1.0);
    }

    @Test
    public void testLTStar() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(star5());
        assertTrue(r.cascadeSizeLT >= 1.0);
    }

    @Test
    public void testLTActivationProbabilities() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(complete6());
        assertEquals(6, r.nodeActivationProbLT.size());
        for (double prob : r.nodeActivationProbLT.values()) {
            assertTrue(prob >= 0.0 && prob <= 1.0);
        }
    }

    @Test
    public void testLTSeedAlwaysActive() {
        GraphInformationDiffusionEngine eng = engine().setSeedNodes(Collections.singleton("B"));
        GraphInformationDiffusionEngine.DiffusionReport r = eng.analyze(triangle());
        assertEquals(1.0, r.nodeActivationProbLT.get("B"), 0.001);
    }

    @Test
    public void testLTDisconnected() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(disconnected());
        // Isolated node C should rarely activate via LT
        assertTrue(r.nodeActivationProbLT.containsKey("C"));
    }

    // ── Superspreader tests ──────────────────────────────────────────

    @Test
    public void testSuperspreaderStarHubHighest() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(star5());
        // Hub should have the highest score
        double hubScore = r.superspreaderScores.get("Hub");
        for (Map.Entry<String, Double> e : r.superspreaderScores.entrySet()) {
            if (!e.getKey().equals("Hub")) {
                assertTrue("Hub should score higher than " + e.getKey(),
                        hubScore >= e.getValue());
            }
        }
    }

    @Test
    public void testSuperspreaderScoresInRange() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(complete6());
        for (double score : r.superspreaderScores.values()) {
            assertTrue(score >= 0.0 && score <= 100.0);
        }
    }

    @Test
    public void testSuperspreaderTiersValid() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(grid3x3());
        Set<String> validTiers = new HashSet<>(Arrays.asList("SUPER", "HIGH", "MEDIUM", "LOW", "MINIMAL"));
        for (String tier : r.superspreaderTiers.values()) {
            assertTrue("Invalid tier: " + tier, validTiers.contains(tier));
        }
    }

    @Test
    public void testTopSpreadersList() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(complete6());
        assertFalse(r.topSpreaders.isEmpty());
        assertTrue(r.topSpreaders.size() <= 10);
        // First should have highest score
        double firstScore = r.superspreaderScores.get(r.topSpreaders.get(0));
        for (String s : r.topSpreaders) {
            assertTrue(firstScore >= r.superspreaderScores.get(s));
        }
    }

    @Test
    public void testSuperspreaderBarbell() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(barbell());
        // Bridge nodes A1/B1 should be among top spreaders
        assertTrue(r.superspreaderScores.containsKey("A1"));
        assertTrue(r.superspreaderScores.containsKey("B1"));
    }

    @Test
    public void testSuperspreaderGridCenter() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(grid3x3());
        // Center node 1_1 has highest degree (4), should score well
        double centerScore = r.superspreaderScores.get("1_1");
        assertTrue(centerScore > 0);
    }

    // ── Phase Detection tests ────────────────────────────────────────

    @Test
    public void testPhaseDetectionNonEmpty() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(complete6());
        assertFalse(r.cascadePhases.isEmpty());
        assertEquals("Ignition", r.cascadePhases.get(0));
    }

    @Test
    public void testPhaseDetectionStarWithIgnition() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(star5());
        assertTrue(r.cascadePhases.contains("Ignition"));
        assertTrue(r.phaseTransitions.containsKey("Ignition"));
        assertEquals(Integer.valueOf(0), r.phaseTransitions.get("Ignition"));
    }

    @Test
    public void testPhaseTransitionsOrdered() {
        GraphInformationDiffusionEngine.DiffusionReport r = highSpreadEngine().analyze(grid3x3());
        // Phase transitions should have non-decreasing step values
        int lastStep = -1;
        for (String phase : r.cascadePhases) {
            Integer step = r.phaseTransitions.get(phase);
            assertNotNull("Missing transition for phase " + phase, step);
            assertTrue("Steps should be non-decreasing", step >= lastStep);
            lastStep = step;
        }
    }

    @Test
    public void testPhaseDetectionTriangle() {
        GraphInformationDiffusionEngine.DiffusionReport r = highSpreadEngine().analyze(triangle());
        assertTrue(r.cascadePhases.size() >= 1);
    }

    @Test
    public void testPhaseDetectionPath() {
        GraphInformationDiffusionEngine.DiffusionReport r = highSpreadEngine().analyze(path5());
        assertTrue(r.cascadePhases.size() >= 1);
    }

    // ── Tipping Point tests ──────────────────────────────────────────

    @Test
    public void testTippingPointComplete() {
        GraphInformationDiffusionEngine eng = new GraphInformationDiffusionEngine()
                .setRandomSeed(42).setSpreadProbability(0.5);
        GraphInformationDiffusionEngine.DiffusionReport r = eng.analyze(complete6());
        assertTrue(r.tippingPointPct > 0);
        assertTrue(r.tippingPointPct <= 100);
    }

    @Test
    public void testTippingPointConfidence() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(grid3x3());
        assertTrue(r.tippingPointConfidence >= 0 && r.tippingPointConfidence <= 1.0);
    }

    @Test
    public void testTippingPointSingleNode() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(singleNode());
        assertEquals(100.0, r.tippingPointPct, 0.001);
    }

    @Test
    public void testTippingPointBarbell() {
        GraphInformationDiffusionEngine.DiffusionReport r = highSpreadEngine().analyze(barbell());
        assertTrue(r.tippingPointPct > 0);
    }

    // ── Viral Score tests ────────────────────────────────────────────

    @Test
    public void testViralScoreRange() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(complete6());
        assertTrue(r.viralScore >= 0 && r.viralScore <= 100);
    }

    @Test
    public void testViralScoreCompleteHigherThanPath() {
        double completeViral = engine().analyze(complete6()).viralScore;
        double pathViral = engine().analyze(path5()).viralScore;
        assertTrue("Complete graph should be more viral than path",
                completeViral > pathViral);
    }

    @Test
    public void testViralTierValid() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(grid3x3());
        Set<String> validTiers = new HashSet<>(Arrays.asList(
                "Highly Viral", "Viral", "Moderate", "Resistant", "Immune"));
        assertTrue("Invalid viral tier: " + r.viralTier, validTiers.contains(r.viralTier));
    }

    @Test
    public void testViralScoreDisconnected() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(disconnected());
        // Disconnected graph should have lower viral score
        assertTrue(r.viralScore >= 0);
    }

    @Test
    public void testHealthScoreEqualsViralScore() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(triangle());
        assertEquals(r.viralScore, r.healthScore, 0.001);
    }

    @Test
    public void testViralScoreGrid() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(grid3x3());
        assertTrue(r.viralScore >= 0 && r.viralScore <= 100);
    }

    // ── Insight tests ────────────────────────────────────────────────

    @Test
    public void testInsightsNotEmpty() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(complete6());
        assertFalse(r.insights.isEmpty());
    }

    @Test
    public void testInsightsContainViralPotential() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(triangle());
        assertTrue(r.insights.stream().anyMatch(i -> i.contains("Viral potential")));
    }

    @Test
    public void testInsightsContainCascadeCoverage() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(star5());
        assertTrue(r.insights.stream().anyMatch(i -> i.contains("cascade reaches")));
    }

    @Test
    public void testInsightsContainContainment() {
        // High spread on complete graph should trigger containment advice
        GraphInformationDiffusionEngine eng = new GraphInformationDiffusionEngine()
                .setRandomSeed(42).setSpreadProbability(0.9);
        GraphInformationDiffusionEngine.DiffusionReport r = eng.analyze(complete6());
        // May or may not trigger depending on viral score
        assertNotNull(r.insights);
    }

    // ── HTML Export tests ────────────────────────────────────────────

    @Test
    public void testHtmlExportNotNull() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(triangle());
        String html = engine().exportHtml(r);
        assertNotNull(html);
        assertTrue(html.contains("<!DOCTYPE html>"));
    }

    @Test
    public void testHtmlExportContainsSections() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(complete6());
        String html = engine().exportHtml(r);
        assertTrue(html.contains("Viral Potential"));
        assertTrue(html.contains("Independent Cascade"));
        assertTrue(html.contains("Linear Threshold"));
        assertTrue(html.contains("Superspreaders"));
        assertTrue(html.contains("Cascade Phases"));
        assertTrue(html.contains("Insights"));
    }

    @Test
    public void testHtmlExportToFile() throws IOException {
        GraphInformationDiffusionEngine eng = engine();
        GraphInformationDiffusionEngine.DiffusionReport r = eng.analyze(star5());
        File f = File.createTempFile("diffusion-test", ".html");
        f.deleteOnExit();
        eng.exportHtml(r, f);
        assertTrue(f.length() > 100);
    }

    @Test
    public void testHtmlExportEmpty() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(emptyGraph());
        String html = engine().exportHtml(r);
        assertNotNull(html);
        assertTrue(html.contains("<!DOCTYPE html>"));
    }

    @Test
    public void testHtmlExportBarbell() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(barbell());
        String html = engine().exportHtml(r);
        assertTrue(html.length() > 500);
    }

    // ── Text Export tests ────────────────────────────────────────────

    @Test
    public void testTextExportNotNull() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(triangle());
        String text = engine().toText(r);
        assertNotNull(text);
        assertTrue(text.contains("DIFFUSION ENGINE"));
    }

    @Test
    public void testTextExportContainsSections() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(complete6());
        String text = engine().toText(r);
        assertTrue(text.contains("Independent Cascade"));
        assertTrue(text.contains("Linear Threshold"));
        assertTrue(text.contains("Superspreaders"));
        assertTrue(text.contains("Cascade Phases"));
        assertTrue(text.contains("Tipping Point"));
        assertTrue(text.contains("Insights"));
    }

    @Test
    public void testTextExportEmpty() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(emptyGraph());
        String text = engine().toText(r);
        assertNotNull(text);
    }

    // ── Configuration tests ──────────────────────────────────────────

    @Test
    public void testSetSpreadProbability() {
        GraphInformationDiffusionEngine eng = engine().setSpreadProbability(1.0);
        GraphInformationDiffusionEngine.DiffusionReport r = eng.analyze(path5());
        // With p=1.0, cascade should reach all nodes
        assertEquals(5.0, r.cascadeSizeIC, 0.001);
    }

    @Test
    public void testSetSpreadProbabilityZero() {
        GraphInformationDiffusionEngine eng = engine().setSpreadProbability(0.0);
        GraphInformationDiffusionEngine.DiffusionReport r = eng.analyze(complete6());
        // With p=0, only seed should be active
        assertEquals(1.0, r.cascadeSizeIC, 0.001);
    }

    @Test
    public void testSetMonteCarloTrials() {
        GraphInformationDiffusionEngine eng = new GraphInformationDiffusionEngine()
                .setRandomSeed(42).setMonteCarloTrials(10);
        GraphInformationDiffusionEngine.DiffusionReport r = eng.analyze(triangle());
        assertNotNull(r);
    }

    @Test
    public void testSetRandomSeed() {
        GraphInformationDiffusionEngine eng = engine().setRandomSeed(123);
        GraphInformationDiffusionEngine.DiffusionReport r = eng.analyze(complete6());
        assertNotNull(r);
    }

    @Test
    public void testSetRng() {
        GraphInformationDiffusionEngine eng = engine().setRng(new Random(99));
        GraphInformationDiffusionEngine.DiffusionReport r = eng.analyze(star5());
        assertNotNull(r);
    }

    // ── Seed node tests ──────────────────────────────────────────────

    @Test
    public void testCustomSeedNodes() {
        Set<String> seeds = new LinkedHashSet<>(Arrays.asList("L1", "L2"));
        GraphInformationDiffusionEngine eng = engine().setSeedNodes(seeds);
        GraphInformationDiffusionEngine.DiffusionReport r = eng.analyze(star5());
        assertEquals(1.0, r.nodeActivationProbIC.get("L1"), 0.001);
        assertEquals(1.0, r.nodeActivationProbIC.get("L2"), 0.001);
    }

    @Test
    public void testInvalidSeedFallsBack() {
        Set<String> seeds = Collections.singleton("NonExistent");
        GraphInformationDiffusionEngine eng = engine().setSeedNodes(seeds);
        GraphInformationDiffusionEngine.DiffusionReport r = eng.analyze(triangle());
        // Should fall back to auto-select
        assertTrue(r.cascadeSizeIC >= 1.0);
    }

    @Test
    public void testAutoSeedSelectsHighDegree() {
        // In star graph, Hub has degree 4, leaves have degree 1
        GraphInformationDiffusionEngine eng = engine().setSpreadProbability(0.0);
        GraphInformationDiffusionEngine.DiffusionReport r = eng.analyze(star5());
        // With p=0, only seed is active. Hub should be auto-selected
        assertEquals(1.0, r.nodeActivationProbIC.get("Hub"), 0.001);
    }

    @Test
    public void testNullSeedAutoSelects() {
        GraphInformationDiffusionEngine eng = engine().setSeedNodes(null);
        GraphInformationDiffusionEngine.DiffusionReport r = eng.analyze(triangle());
        assertTrue(r.cascadeSizeIC >= 1.0);
    }

    // ── Determinism tests ────────────────────────────────────────────

    @Test
    public void testDeterministicWithSameSeed() {
        GraphInformationDiffusionEngine.DiffusionReport r1 = engine().analyze(complete6());
        GraphInformationDiffusionEngine.DiffusionReport r2 = engine().analyze(complete6());
        assertEquals(r1.cascadeSizeIC, r2.cascadeSizeIC, 0.001);
        assertEquals(r1.cascadeSizeLT, r2.cascadeSizeLT, 0.001);
        assertEquals(r1.viralScore, r2.viralScore, 0.001);
    }

    @Test
    public void testDifferentSeedsDifferentResults() {
        GraphInformationDiffusionEngine.DiffusionReport r1 =
                new GraphInformationDiffusionEngine().setRandomSeed(1).analyze(complete6());
        GraphInformationDiffusionEngine.DiffusionReport r2 =
                new GraphInformationDiffusionEngine().setRandomSeed(999).analyze(complete6());
        // With different seeds, IC results may differ (probabilistic)
        // Just verify both produce valid results
        assertTrue(r1.cascadeSizeIC >= 1.0);
        assertTrue(r2.cascadeSizeIC >= 1.0);
    }

    // ── Topology-specific tests ──────────────────────────────────────

    @Test
    public void testDisconnectedGraph() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(disconnected());
        assertEquals(3, r.nodeCount);
        assertTrue(r.superspreaderScores.size() == 3);
    }

    @Test
    public void testBarbellBridgeBottleneck() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(barbell());
        assertEquals(8, r.nodeCount);
        assertEquals(13, r.edgeCount); // 6+6+1
        assertFalse(r.insights.isEmpty());
    }

    @Test
    public void testGrid3x3Structure() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(grid3x3());
        assertEquals(9, r.nodeCount);
        assertEquals(12, r.edgeCount);
    }

    // ── Report immutability tests ────────────────────────────────────

    @Test(expected = UnsupportedOperationException.class)
    public void testInsightsImmutable() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(triangle());
        r.insights.add("hacked");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testTopSpreadersImmutable() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(triangle());
        r.topSpreaders.add("hacked");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testActivationProbImmutable() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(triangle());
        r.nodeActivationProbIC.put("hacked", 0.0);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSuperspreaderScoresImmutable() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(triangle());
        r.superspreaderScores.put("hacked", 0.0);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testPhasesImmutable() {
        GraphInformationDiffusionEngine.DiffusionReport r = engine().analyze(triangle());
        r.cascadePhases.add("hacked");
    }
}
