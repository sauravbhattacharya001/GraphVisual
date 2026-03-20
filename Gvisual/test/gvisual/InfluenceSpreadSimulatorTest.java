package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for InfluenceSpreadSimulator — IC, LT, SIR models,
 * Monte Carlo, influence maximization, vaccination strategy.
 */
public class InfluenceSpreadSimulatorTest {

    private Graph<String, Edge> graph;
    private InfluenceSpreadSimulator simulator;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
        graph.addEdge(new Edge("c", "A", "B"), "A", "B");
        graph.addEdge(new Edge("c", "A", "C"), "A", "C");
        graph.addEdge(new Edge("c", "A", "D"), "A", "D");
        graph.addEdge(new Edge("c", "A", "E"), "A", "E");
        simulator = new InfluenceSpreadSimulator(graph, 42L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new InfluenceSpreadSimulator(null);
    }

    @Test
    public void testSeededConstructor() {
        InfluenceSpreadSimulator s = new InfluenceSpreadSimulator(graph, 42L);
        assertNotNull(s);
    }

    // ─── Independent Cascade ────────────────────────────────────

    @Test
    public void testICFromCenterNode() {
        InfluenceSpreadSimulator.SimulationResult result =
                simulator.simulateIC(Arrays.asList("A"), 1.0, 0);
        assertEquals(5, result.getTotalInfected());
        assertEquals(1.0, result.getSpreadRatio(), 0.001);
    }

    @Test
    public void testICWithZeroProbability() {
        InfluenceSpreadSimulator.SimulationResult result =
                simulator.simulateIC(Arrays.asList("A"), 0.0, 0);
        assertEquals(1, result.getTotalInfected());
    }

    @Test
    public void testICMaxRoundsLimit() {
        InfluenceSpreadSimulator.SimulationResult result =
                simulator.simulateIC(Arrays.asList("A"), 1.0, 1);
        assertTrue(result.getRoundCount() <= 2);
    }

    @Test
    public void testICTimeline() {
        InfluenceSpreadSimulator.SimulationResult result =
                simulator.simulateIC(Arrays.asList("A"), 1.0, 0);
        assertFalse(result.getTimeline().isEmpty());
        for (InfluenceSpreadSimulator.InfectionEvent event : result.getTimeline()) {
            assertEquals("A", event.getSource());
            assertEquals(1, event.getRound());
        }
    }

    @Test
    public void testICSnapshots() {
        InfluenceSpreadSimulator.SimulationResult result =
                simulator.simulateIC(Arrays.asList("A"), 1.0, 0);
        assertTrue(result.getSnapshots().size() >= 2);
        InfluenceSpreadSimulator.RoundSnapshot initial = result.getSnapshots().get(0);
        assertEquals(0, initial.getRound());
        assertEquals(1, initial.getInfected());
        assertEquals(4, initial.getSusceptible());
    }

    @Test
    public void testICFromLeafNode() {
        InfluenceSpreadSimulator.SimulationResult result =
                simulator.simulateIC(Arrays.asList("B"), 1.0, 0);
        assertEquals(5, result.getTotalInfected());
    }

    @Test
    public void testICMultipleSeeds() {
        InfluenceSpreadSimulator.SimulationResult result =
                simulator.simulateIC(Arrays.asList("B", "C"), 1.0, 0);
        assertEquals(5, result.getTotalInfected());
    }

    @Test
    public void testICSeedNotInGraph() {
        InfluenceSpreadSimulator.SimulationResult result =
                simulator.simulateIC(Arrays.asList("Z"), 1.0, 0);
        assertEquals(0, result.getTotalInfected());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testICNullSeeds() {
        simulator.simulateIC(null, 0.5, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testICEmptySeeds() {
        simulator.simulateIC(Collections.emptyList(), 0.5, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testICInvalidProbability() {
        simulator.simulateIC(Arrays.asList("A"), 1.5, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testICNegativeProbability() {
        simulator.simulateIC(Arrays.asList("A"), -0.1, 0);
    }

    // ─── Linear Threshold ───────────────────────────────────────

    @Test
    public void testLTFromCenterNode() {
        InfluenceSpreadSimulator.SimulationResult result =
                simulator.simulateLT(Arrays.asList("A"), 0);
        assertTrue(result.getTotalInfected() >= 1);
    }

    @Test
    public void testLTMultipleSeeds() {
        InfluenceSpreadSimulator.SimulationResult result =
                simulator.simulateLT(Arrays.asList("B", "C", "D", "E"), 0);
        assertTrue(result.getTotalInfected() >= 4);
    }

    @Test
    public void testLTMaxRounds() {
        InfluenceSpreadSimulator.SimulationResult result =
                simulator.simulateLT(Arrays.asList("A"), 1);
        assertTrue(result.getRoundCount() <= 2);
    }

    @Test
    public void testLTSnapshots() {
        InfluenceSpreadSimulator.SimulationResult result =
                simulator.simulateLT(Arrays.asList("A"), 0);
        assertFalse(result.getSnapshots().isEmpty());
        assertEquals(0, result.getSnapshots().get(0).getRound());
    }

    // ─── SIR Model ──────────────────────────────────────────────

    @Test
    public void testSIRBasic() {
        InfluenceSpreadSimulator.SimulationResult result =
                simulator.simulateSIR(Arrays.asList("A"), 1.0, 1.0, 0);
        assertTrue(result.getTotalInfected() >= 1);
    }

    @Test
    public void testSIRNoInfection() {
        InfluenceSpreadSimulator.SimulationResult result =
                simulator.simulateSIR(Arrays.asList("A"), 0.0, 1.0, 0);
        assertEquals(1, result.getTotalInfected());
    }

    @Test
    public void testSIRNoRecovery() {
        InfluenceSpreadSimulator.SimulationResult result =
                simulator.simulateSIR(Arrays.asList("A"), 1.0, 0.0, 10);
        assertEquals(5, result.getTotalInfected());
    }

    @Test
    public void testSIRMaxRounds() {
        InfluenceSpreadSimulator.SimulationResult result =
                simulator.simulateSIR(Arrays.asList("A"), 0.5, 0.0, 1);
        assertTrue(result.getRoundCount() <= 2);
    }

    @Test
    public void testSIRPeakInfected() {
        InfluenceSpreadSimulator.SimulationResult result =
                simulator.simulateSIR(Arrays.asList("A"), 1.0, 0.0, 10);
        assertTrue(result.getPeakInfected() >= 1);
        assertTrue(result.getPeakRound() >= 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSIRInvalidBeta() {
        simulator.simulateSIR(Arrays.asList("A"), 1.5, 0.5, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSIRInvalidGamma() {
        simulator.simulateSIR(Arrays.asList("A"), 0.5, -0.1, 0);
    }

    // ─── SimulationResult ───────────────────────────────────────

    @Test
    public void testResultGetters() {
        InfluenceSpreadSimulator.SimulationResult result =
                simulator.simulateIC(Arrays.asList("A"), 1.0, 0);
        assertEquals(InfluenceSpreadSimulator.Model.INDEPENDENT_CASCADE,
                result.getModel());
        assertTrue(result.getSeeds().contains("A"));
        assertEquals(1, result.getSeeds().size());
        assertNotNull(result.getFinalState());
        assertNotNull(result.toString());
        assertTrue(result.toString().contains("INDEPENDENT_CASCADE"));
    }

    @Test
    public void testUninfectedNodes() {
        InfluenceSpreadSimulator.SimulationResult result =
                simulator.simulateIC(Arrays.asList("A"), 0.0, 0);
        Set<String> uninfected = result.getUninfectedNodes();
        assertEquals(4, uninfected.size());
        assertFalse(uninfected.contains("A"));
    }

    // ─── Monte Carlo ────────────────────────────────────────────

    @Test
    public void testMonteCarloIC() {
        InfluenceSpreadSimulator.MonteCarloResult mc =
                simulator.monteCarlo(Arrays.asList("A"),
                        InfluenceSpreadSimulator.Model.INDEPENDENT_CASCADE,
                        0.5, 0.0, 0, 10);
        assertEquals(10, mc.getNumTrials());
        assertEquals(10, mc.getSpreads().size());
        assertEquals(10, mc.getDurations().size());
        assertTrue(mc.getAverageSpread() >= 1.0);
    }

    @Test
    public void testMonteCarloLT() {
        InfluenceSpreadSimulator.MonteCarloResult mc =
                simulator.monteCarlo(Arrays.asList("A"),
                        InfluenceSpreadSimulator.Model.LINEAR_THRESHOLD,
                        0.5, 0.0, 0, 10);
        assertEquals(10, mc.getNumTrials());
        assertTrue(mc.getAverageSpread() >= 1.0);
    }

    @Test
    public void testMonteCarloSIR() {
        InfluenceSpreadSimulator.MonteCarloResult mc =
                simulator.monteCarlo(Arrays.asList("A"),
                        InfluenceSpreadSimulator.Model.SIR,
                        0.5, 0.3, 0, 10);
        assertEquals(10, mc.getNumTrials());
        assertTrue(mc.getAverageSpread() >= 1.0);
    }

    @Test
    public void testMonteCarloStatistics() {
        InfluenceSpreadSimulator.MonteCarloResult mc =
                simulator.monteCarlo(Arrays.asList("A"),
                        InfluenceSpreadSimulator.Model.INDEPENDENT_CASCADE,
                        1.0, 0.0, 0, 20);
        assertEquals(5.0, mc.getAverageSpread(), 0.001);
        assertEquals(0.0, mc.getSpreadStdDev(), 0.001);
        assertEquals(5, mc.getMaxSpread());
        assertEquals(5, mc.getMinSpread());
    }

    @Test
    public void testMonteCarloInfectionFrequency() {
        InfluenceSpreadSimulator.MonteCarloResult mc =
                simulator.monteCarlo(Arrays.asList("A"),
                        InfluenceSpreadSimulator.Model.INDEPENDENT_CASCADE,
                        1.0, 0.0, 0, 10);
        assertEquals(1.0, mc.getNodeInfectionProbability("B"), 0.001);
        assertEquals(0.0, mc.getNodeInfectionProbability("Z"), 0.001);
    }

    @Test
    public void testMonteCarloTopInfected() {
        InfluenceSpreadSimulator.MonteCarloResult mc =
                simulator.monteCarlo(Arrays.asList("A"),
                        InfluenceSpreadSimulator.Model.INDEPENDENT_CASCADE,
                        1.0, 0.0, 0, 10);
        List<Map.Entry<String, Integer>> top = mc.getTopInfected(3);
        assertTrue(top.size() <= 3);
        for (Map.Entry<String, Integer> entry : top) {
            assertEquals(10, entry.getValue().intValue());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMonteCarloZeroTrials() {
        simulator.monteCarlo(Arrays.asList("A"),
                InfluenceSpreadSimulator.Model.INDEPENDENT_CASCADE,
                0.5, 0.0, 0, 0);
    }

    @Test
    public void testMonteCarloToString() {
        InfluenceSpreadSimulator.MonteCarloResult mc =
                simulator.monteCarlo(Arrays.asList("A"),
                        InfluenceSpreadSimulator.Model.INDEPENDENT_CASCADE,
                        0.5, 0.0, 0, 5);
        assertNotNull(mc.toString());
        assertTrue(mc.toString().contains("trials=5"));
    }

    // ─── Influence Maximization ─────────────────────────────────

    @Test
    public void testFindTopKSeeds() {
        List<InfluenceSpreadSimulator.SeedCandidate> seeds =
                simulator.findTopKSeeds(2,
                        InfluenceSpreadSimulator.Model.INDEPENDENT_CASCADE,
                        1.0, 5);
        assertFalse(seeds.isEmpty());
        assertTrue(seeds.size() <= 2);
        assertEquals("A", seeds.get(0).getNode());
        assertEquals(1, seeds.get(0).getRank());
        assertTrue(seeds.get(0).getMarginalGain() > 0);
    }

    @Test
    public void testFindTopKSeedsEmptyGraph() {
        Graph<String, Edge> empty = new UndirectedSparseGraph<>();
        InfluenceSpreadSimulator sim = new InfluenceSpreadSimulator(empty, 42L);
        List<InfluenceSpreadSimulator.SeedCandidate> seeds =
                sim.findTopKSeeds(3,
                        InfluenceSpreadSimulator.Model.INDEPENDENT_CASCADE,
                        0.5, 5);
        assertTrue(seeds.isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindTopKSeedsZeroK() {
        simulator.findTopKSeeds(0,
                InfluenceSpreadSimulator.Model.INDEPENDENT_CASCADE,
                0.5, 5);
    }

    @Test
    public void testSeedCandidateToString() {
        InfluenceSpreadSimulator.SeedCandidate sc =
                new InfluenceSpreadSimulator.SeedCandidate("A", 4.5, 1);
        assertTrue(sc.toString().contains("#1"));
        assertTrue(sc.toString().contains("A"));
    }

    // ─── Vaccination Strategy ───────────────────────────────────

    @Test
    public void testVaccinationTargets() {
        InfluenceSpreadSimulator.VaccinationStrategy strategy =
                simulator.findVaccinationTargets(2);
        assertNotNull(strategy);
        assertEquals(2, strategy.getTargets().size());
        assertEquals("A", strategy.getTargets().get(0));
        assertTrue(strategy.getEdgesBlocked() > 0);
        assertTrue(strategy.getCoverageRatio() > 0.0);
        assertTrue(strategy.getCoverageRatio() <= 1.0);
    }

    @Test
    public void testVaccinationSingleTarget() {
        InfluenceSpreadSimulator.VaccinationStrategy strategy =
                simulator.findVaccinationTargets(1);
        assertEquals(1, strategy.getTargets().size());
        assertEquals("A", strategy.getTargets().get(0));
        assertEquals(4, strategy.getEdgesBlocked());
        assertEquals(1.0, strategy.getCoverageRatio(), 0.001);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVaccinationZeroK() {
        simulator.findVaccinationTargets(0);
    }

    @Test
    public void testVaccinationKExceedsNodes() {
        InfluenceSpreadSimulator.VaccinationStrategy strategy =
                simulator.findVaccinationTargets(100);
        assertEquals(5, strategy.getTargets().size());
    }

    @Test
    public void testVaccinationToString() {
        InfluenceSpreadSimulator.VaccinationStrategy strategy =
                simulator.findVaccinationTargets(2);
        assertNotNull(strategy.toString());
        assertTrue(strategy.toString().contains("targets=2"));
    }

    // ─── Edge Weight Support ────────────────────────────────────

    @Test
    public void testEdgeWeightAsInfluenceProbability() {
        Graph<String, Edge> wg = new UndirectedSparseGraph<>();
        Edge e1  new Edge("c", "X", "Y");
        e1.setWeight(1.0f);
        Edge e2  new Edge("c", "X", "Z");
        e2.setWeight(0.0f);
        wg.addEdge(e1, "X", "Y");
        wg.addEdge(e2, "X", "Z");

        InfluenceSpreadSimulator sim = new InfluenceSpreadSimulator(wg, 42L);
        InfluenceSpreadSimulator.SimulationResult result =
                sim.simulateIC(Arrays.asList("X"), 0.5, 0);
        assertTrue(result.getFinalState().get("Y") !=
                InfluenceSpreadSimulator.NodeState.SUSCEPTIBLE);
    }

    // ─── Directed Graph ─────────────────────────────────────────

    @Test
    public void testDirectedGraphIC() {
        Graph<String, Edge> dg = new DirectedSparseGraph<>();
        dg.addEdge(new Edge("c", "A", "B"), "A", "B");
        dg.addEdge(new Edge("c", "A", "C"), "A", "C");

        InfluenceSpreadSimulator sim = new InfluenceSpreadSimulator(dg, 42L);
        InfluenceSpreadSimulator.SimulationResult result =
                sim.simulateIC(Arrays.asList("A"), 1.0, 0);
        assertEquals(3, result.getTotalInfected());
    }

    @Test
    public void testDirectedGraphNoBackPropagation() {
        Graph<String, Edge> dg = new DirectedSparseGraph<>();
        dg.addEdge(new Edge("c", "A", "B"), "A", "B");

        InfluenceSpreadSimulator sim = new InfluenceSpreadSimulator(dg, 42L);
        InfluenceSpreadSimulator.SimulationResult result =
                sim.simulateIC(Arrays.asList("B"), 1.0, 0);
        assertEquals(1, result.getTotalInfected());
    }

    // ─── Chain Graph ────────────────────────────────────────────

    @Test
    public void testChainGraphPropagation() {
        Graph<String, Edge> chain = new UndirectedSparseGraph<>();
        chain.addEdge(new Edge("c", "1", "2"), "1", "2");
        chain.addEdge(new Edge("c", "2", "3"), "2", "3");
        chain.addEdge(new Edge("c", "3", "4"), "3", "4");
        chain.addEdge(new Edge("c", "4", "5"), "4", "5");

        InfluenceSpreadSimulator sim = new InfluenceSpreadSimulator(chain, 42L);
        InfluenceSpreadSimulator.SimulationResult result =
                sim.simulateIC(Arrays.asList("1"), 1.0, 0);
        assertEquals(5, result.getTotalInfected());
        assertTrue(result.getRoundCount() >= 2);
    }

    // ─── Disconnected Graph ─────────────────────────────────────

    @Test
    public void testDisconnectedGraphLimitsSpread() {
        Graph<String, Edge> dg = new UndirectedSparseGraph<>();
        dg.addEdge(new Edge("c", "A", "B"), "A", "B");
        dg.addVertex("C");

        InfluenceSpreadSimulator sim = new InfluenceSpreadSimulator(dg, 42L);
        InfluenceSpreadSimulator.SimulationResult result =
                sim.simulateIC(Arrays.asList("A"), 1.0, 0);
        assertEquals(2, result.getTotalInfected());
        assertTrue(result.getUninfectedNodes().contains("C"));
    }

    // ─── InfectionEvent ─────────────────────────────────────────

    @Test
    public void testInfectionEventToString() {
        InfluenceSpreadSimulator.InfectionEvent event =
                new InfluenceSpreadSimulator.InfectionEvent("A", "B", 1);
        assertEquals("A", event.getSource());
        assertEquals("B", event.getTarget());
        assertEquals(1, event.getRound());
        assertTrue(event.toString().contains("A"));
        assertTrue(event.toString().contains("B"));
    }

    // ─── RoundSnapshot ──────────────────────────────────────────

    @Test
    public void testRoundSnapshotToString() {
        InfluenceSpreadSimulator.SimulationResult result =
                simulator.simulateIC(Arrays.asList("A"), 1.0, 0);
        InfluenceSpreadSimulator.RoundSnapshot snap = result.getSnapshots().get(0);
        assertNotNull(snap.toString());
        assertTrue(snap.toString().contains("Round 0"));
        assertNotNull(snap.getNodeStates());
    }
}
