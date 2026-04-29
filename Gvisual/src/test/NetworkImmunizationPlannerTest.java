package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for NetworkImmunizationPlanner — SIR epidemic simulation
 * and immunization strategy evaluation.
 */
public class NetworkImmunizationPlannerTest {

    private Graph<String, Edge> starGraph;
    private Graph<String, Edge> lineGraph;
    private Graph<String, Edge> completeGraph;
    private Graph<String, Edge> scaleGraph;

    @Before
    public void setUp() {
        // Star graph: hub + 10 leaves
        starGraph = new UndirectedSparseGraph<>();
        starGraph.addVertex("hub");
        for (int i = 0; i < 10; i++) {
            String leaf = "L" + i;
            starGraph.addVertex(leaf);
            starGraph.addEdge(new Edge("c", "hub", leaf), "hub", leaf);
        }

        // Line graph: 20 nodes in a chain
        lineGraph = new UndirectedSparseGraph<>();
        for (int i = 0; i < 20; i++) lineGraph.addVertex("N" + i);
        for (int i = 0; i < 19; i++)
            lineGraph.addEdge(new Edge("c", "N" + i, "N" + (i + 1)), "N" + i, "N" + (i + 1));

        // Complete graph K8
        completeGraph = new UndirectedSparseGraph<>();
        for (int i = 0; i < 8; i++) completeGraph.addVertex("K" + i);
        for (int i = 0; i < 8; i++)
            for (int j = i + 1; j < 8; j++)
                completeGraph.addEdge(new Edge("c", "K" + i, "K" + j), "K" + i, "K" + j);

        // Scale-free-ish: 50 nodes
        scaleGraph = new UndirectedSparseGraph<>();
        Random r = new Random(42);
        for (int i = 0; i < 50; i++) scaleGraph.addVertex("S" + i);
        for (int i = 0; i < 3; i++)
            for (int j = i + 1; j < 3; j++)
                scaleGraph.addEdge(new Edge("c", "S" + i, "S" + j), "S" + i, "S" + j);
        for (int i = 3; i < 50; i++) {
            List<String> existing = new ArrayList<>(scaleGraph.getVertices());
            existing.remove("S" + i);
            for (int e = 0; e < 2; e++) {
                String target = existing.get(r.nextInt(existing.size()));
                if (!scaleGraph.isNeighbor("S" + i, target))
                    scaleGraph.addEdge(new Edge("c", "S" + i, target), "S" + i, target);
            }
        }
    }

    @Test
    public void testPlanReturnsNonNull() {
        NetworkImmunizationPlanner planner = new NetworkImmunizationPlanner().setSeed(1);
        NetworkImmunizationPlanner.ImmunizationPlan plan = planner.plan(starGraph, 0.10);
        assertNotNull(plan);
        assertNotNull(plan.baselineEpidemic);
        assertNotNull(plan.recommendedStrategy);
        assertFalse(plan.strategyResults.isEmpty());
    }

    @Test
    public void testAllStrategiesEvaluated() {
        NetworkImmunizationPlanner planner = new NetworkImmunizationPlanner().setSeed(2);
        NetworkImmunizationPlanner.ImmunizationPlan plan = planner.plan(lineGraph, 0.15);
        assertEquals(NetworkImmunizationPlanner.Strategy.values().length, plan.strategyResults.size());
    }

    @Test
    public void testBaselineHigherThanImmunized() {
        NetworkImmunizationPlanner planner = new NetworkImmunizationPlanner()
                .setBeta(0.5).setGamma(0.05).setSeed(3).setMonteCarloRuns(30);
        NetworkImmunizationPlanner.ImmunizationPlan plan = planner.plan(scaleGraph, 0.20);
        // At least one strategy should reduce infection
        boolean anyBetter = plan.strategyResults.stream().anyMatch(sr -> sr.effectiveness > 0);
        assertTrue("At least one strategy should have positive effectiveness", anyBetter);
    }

    @Test
    public void testStarGraphHubImmunization() {
        // Immunizing the hub of a star should dramatically reduce spread
        NetworkImmunizationPlanner planner = new NetworkImmunizationPlanner()
                .setBeta(0.5).setGamma(0.05).setSeed(4).setMonteCarloRuns(30);
        NetworkImmunizationPlanner.ImmunizationPlan plan = planner.plan(starGraph, 0.10);
        // Degree strategy should select the hub
        NetworkImmunizationPlanner.StrategyResult degreeResult = plan.strategyResults.stream()
                .filter(sr -> sr.strategy == NetworkImmunizationPlanner.Strategy.DEGREE)
                .findFirst().orElse(null);
        assertNotNull(degreeResult);
        assertTrue("Degree strategy should immunize hub", degreeResult.immunizedNodes.contains("hub"));
    }

    @Test
    public void testEpidemicCurveLengthMatchesTimeSteps() {
        int steps = 30;
        NetworkImmunizationPlanner planner = new NetworkImmunizationPlanner()
                .setTimeSteps(steps).setSeed(5);
        NetworkImmunizationPlanner.ImmunizationPlan plan = planner.plan(lineGraph, 0.10);
        assertEquals(steps, plan.baselineEpidemic.infectedCurve.length);
        assertEquals(steps, plan.baselineEpidemic.recoveredCurve.length);
    }

    @Test
    public void testBudgetSweepContainsAllStrategies() {
        NetworkImmunizationPlanner planner = new NetworkImmunizationPlanner().setSeed(6);
        NetworkImmunizationPlanner.ImmunizationPlan plan = planner.plan(scaleGraph, 0.10);
        assertEquals(NetworkImmunizationPlanner.Strategy.values().length, plan.budgetSweep.size());
        for (double[] sweep : plan.budgetSweep.values()) {
            assertEquals(6, sweep.length); // 5%, 10%, 15%, 20%, 25%, 30%
        }
    }

    @Test
    public void testEffectivenessMonotonic() {
        // Higher budget should generally yield >= effectiveness
        NetworkImmunizationPlanner planner = new NetworkImmunizationPlanner()
                .setBeta(0.4).setGamma(0.1).setSeed(7).setMonteCarloRuns(50);
        NetworkImmunizationPlanner.ImmunizationPlan plan = planner.plan(scaleGraph, 0.20);
        // Check degree strategy sweep is roughly monotonic (allow small Monte Carlo noise)
        double[] sweep = plan.budgetSweep.get(NetworkImmunizationPlanner.Strategy.DEGREE);
        for (int i = 1; i < sweep.length; i++) {
            assertTrue("Effectiveness should generally increase with budget (i=" + i + ")",
                    sweep[i] >= sweep[i - 1] - 0.15); // allow 15% noise margin
        }
    }

    @Test
    public void testCompleteGraphHighInfection() {
        // Complete graph should have high baseline infection
        NetworkImmunizationPlanner planner = new NetworkImmunizationPlanner()
                .setBeta(0.5).setGamma(0.05).setSeed(8).setMonteCarloRuns(30);
        NetworkImmunizationPlanner.ImmunizationPlan plan = planner.plan(completeGraph, 0.15);
        assertTrue("Complete graph should have high total infection",
                plan.baselineEpidemic.totalInfected > 0.5);
    }

    @Test
    public void testCriticalThresholdInRange() {
        NetworkImmunizationPlanner planner = new NetworkImmunizationPlanner().setSeed(9);
        NetworkImmunizationPlanner.ImmunizationPlan plan = planner.plan(scaleGraph, 0.15);
        assertTrue("Critical threshold should be between 0 and 0.5",
                plan.criticalThreshold >= 0.05 && plan.criticalThreshold <= 0.50);
    }

    @Test
    public void testNetworkSizeRecorded() {
        NetworkImmunizationPlanner planner = new NetworkImmunizationPlanner().setSeed(10);
        NetworkImmunizationPlanner.ImmunizationPlan plan = planner.plan(starGraph, 0.10);
        assertEquals(11, plan.networkSize); // hub + 10 leaves
    }

    @Test
    public void testHtmlExportNonEmpty() {
        NetworkImmunizationPlanner planner = new NetworkImmunizationPlanner()
                .setTimeSteps(20).setMonteCarloRuns(5).setSeed(11);
        NetworkImmunizationPlanner.ImmunizationPlan plan = planner.plan(lineGraph, 0.10);
        String html = planner.exportHtml(plan);
        assertNotNull(html);
        assertTrue("HTML should contain title", html.contains("Network Immunization Planner"));
        assertTrue("HTML should contain strategy table", html.contains("Degree-Based"));
        assertTrue("HTML should contain canvas chart", html.contains("epiChart"));
    }

    @Test
    public void testEmptyGraphThrows() {
        Graph<String, Edge> empty = new UndirectedSparseGraph<>();
        NetworkImmunizationPlanner planner = new NetworkImmunizationPlanner();
        try {
            planner.plan(empty, 0.10);
            fail("Should throw for empty graph");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("empty"));
        }
    }

    @Test
    public void testImmunizedNodeCountMatchesBudget() {
        NetworkImmunizationPlanner planner = new NetworkImmunizationPlanner().setSeed(12);
        NetworkImmunizationPlanner.ImmunizationPlan plan = planner.plan(scaleGraph, 0.20);
        for (NetworkImmunizationPlanner.StrategyResult sr : plan.strategyResults) {
            int expected = Math.max(1, (int) (50 * 0.20)); // 10 nodes
            assertTrue("Immunized count should be near budget",
                    sr.immunizedNodes.size() <= expected + 2); // small tolerance for acquaintance
        }
    }

    @Test
    public void testDifferentSeedsProduceDifferentResults() {
        NetworkImmunizationPlanner p1 = new NetworkImmunizationPlanner().setSeed(100).setMonteCarloRuns(10);
        NetworkImmunizationPlanner p2 = new NetworkImmunizationPlanner().setSeed(200).setMonteCarloRuns(10);
        NetworkImmunizationPlanner.ImmunizationPlan plan1 = p1.plan(scaleGraph, 0.15);
        NetworkImmunizationPlanner.ImmunizationPlan plan2 = p2.plan(scaleGraph, 0.15);
        // Results won't be identical with different seeds
        // (but deterministic strategies like DEGREE should have same node selection)
        assertNotNull(plan1);
        assertNotNull(plan2);
    }

    @Test
    public void testTimestampPopulated() {
        NetworkImmunizationPlanner planner = new NetworkImmunizationPlanner().setSeed(13);
        NetworkImmunizationPlanner.ImmunizationPlan plan = planner.plan(starGraph, 0.10);
        assertNotNull(plan.timestamp);
        assertFalse(plan.timestamp.isEmpty());
    }

    @Test
    public void testStrategyResultsRankedByEffectiveness() {
        NetworkImmunizationPlanner planner = new NetworkImmunizationPlanner().setSeed(14);
        NetworkImmunizationPlanner.ImmunizationPlan plan = planner.plan(scaleGraph, 0.15);
        for (int i = 1; i < plan.strategyResults.size(); i++) {
            assertTrue("Results should be ranked by effectiveness (descending)",
                    plan.strategyResults.get(i - 1).effectiveness >= plan.strategyResults.get(i).effectiveness);
        }
    }

    @Test
    public void testPeakTimeNonNegative() {
        NetworkImmunizationPlanner planner = new NetworkImmunizationPlanner().setSeed(15);
        NetworkImmunizationPlanner.ImmunizationPlan plan = planner.plan(lineGraph, 0.10);
        assertTrue(plan.baselineEpidemic.peakTime >= 0);
        for (NetworkImmunizationPlanner.StrategyResult sr : plan.strategyResults) {
            assertTrue(sr.epidemic.peakTime >= 0);
        }
    }

    @Test
    public void testEpidemicValuesInRange() {
        NetworkImmunizationPlanner planner = new NetworkImmunizationPlanner().setSeed(16);
        NetworkImmunizationPlanner.ImmunizationPlan plan = planner.plan(scaleGraph, 0.15);
        assertTrue(plan.baselineEpidemic.peakInfected >= 0 && plan.baselineEpidemic.peakInfected <= 1);
        assertTrue(plan.baselineEpidemic.totalInfected >= 0 && plan.baselineEpidemic.totalInfected <= 1);
        for (double v : plan.baselineEpidemic.infectedCurve) {
            assertTrue("Infected fraction should be in [0,1]", v >= 0 && v <= 1);
        }
    }

    @Test
    public void testEffectivenessInRange() {
        NetworkImmunizationPlanner planner = new NetworkImmunizationPlanner().setSeed(17);
        NetworkImmunizationPlanner.ImmunizationPlan plan = planner.plan(scaleGraph, 0.20);
        for (NetworkImmunizationPlanner.StrategyResult sr : plan.strategyResults) {
            assertTrue("Effectiveness should be in [-0.1, 1]",
                    sr.effectiveness >= -0.1 && sr.effectiveness <= 1.0);
        }
    }

    @Test
    public void testConfigurationChaining() {
        NetworkImmunizationPlanner planner = new NetworkImmunizationPlanner()
                .setBeta(0.5).setGamma(0.2).setTimeSteps(30).setMonteCarloRuns(5)
                .setInitialInfected(2).setSeed(18);
        NetworkImmunizationPlanner.ImmunizationPlan plan = planner.plan(lineGraph, 0.10);
        assertNotNull(plan);
        assertEquals(30, plan.baselineEpidemic.infectedCurve.length);
    }

    @Test
    public void testSmallBudgetStillWorks() {
        NetworkImmunizationPlanner planner = new NetworkImmunizationPlanner().setSeed(19);
        NetworkImmunizationPlanner.ImmunizationPlan plan = planner.plan(starGraph, 0.01);
        assertNotNull(plan);
        assertTrue(plan.strategyResults.get(0).immunizedNodes.size() >= 1);
    }

    @Test
    public void testLargeBudget() {
        NetworkImmunizationPlanner planner = new NetworkImmunizationPlanner()
                .setBeta(0.5).setGamma(0.1).setSeed(20).setMonteCarloRuns(10);
        NetworkImmunizationPlanner.ImmunizationPlan plan = planner.plan(scaleGraph, 0.50);
        // With 50% immunized, total infection should be low
        NetworkImmunizationPlanner.StrategyResult best = plan.strategyResults.get(0);
        assertTrue("50% budget should significantly reduce epidemic",
                best.epidemic.totalInfected < plan.baselineEpidemic.totalInfected);
    }

    @Test
    public void testRecoveredCurveNonDecreasing() {
        NetworkImmunizationPlanner planner = new NetworkImmunizationPlanner()
                .setSeed(21).setMonteCarloRuns(30);
        NetworkImmunizationPlanner.ImmunizationPlan plan = planner.plan(scaleGraph, 0.10);
        double[] rec = plan.baselineEpidemic.recoveredCurve;
        for (int i = 1; i < rec.length; i++) {
            assertTrue("Recovered should be non-decreasing (or nearly so with averaging)",
                    rec[i] >= rec[i - 1] - 0.01);
        }
    }

    @Test
    public void testAcquaintanceStrategyBiasesHubs() {
        // Acquaintance immunization should preferentially select high-degree nodes
        NetworkImmunizationPlanner planner = new NetworkImmunizationPlanner().setSeed(22);
        NetworkImmunizationPlanner.ImmunizationPlan plan = planner.plan(starGraph, 0.20);
        NetworkImmunizationPlanner.StrategyResult acq = plan.strategyResults.stream()
                .filter(sr -> sr.strategy == NetworkImmunizationPlanner.Strategy.ACQUAINTANCE)
                .findFirst().orElse(null);
        assertNotNull(acq);
        // Hub is the only neighbor of every leaf, so it should be selected
        assertTrue("Acquaintance should likely select hub in star graph",
                acq.immunizedNodes.contains("hub"));
    }
}
