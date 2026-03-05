package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link RandomWalkAnalyzer}.
 */
public class RandomWalkAnalyzerTest {

    private Graph<String, edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<>();
    }

    private edge addEdge(String v1, String v2) {
        edge e = new edge("f", v1, v2);
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        graph.addEdge(e, v1, v2);
        return e;
    }

    // ── Constructor ────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_zeroSimulations() {
        new RandomWalkAnalyzer(0, new Random(42));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_nullRng() {
        new RandomWalkAnalyzer(100, null);
    }

    @Test
    public void testConstructor_defaultNoThrow() {
        // Default constructor should not throw
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer();
        assertNotNull(rwa);
    }

    // ── Hitting Time ───────────────────────────────────────────────

    @Test
    public void testHittingTime_sameNode() {
        addEdge("A", "B");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer(1000, new Random(42));
        assertEquals(0.0, rwa.hittingTime(graph, "A", "A"), 0.001);
    }

    @Test
    public void testHittingTime_twoNodes() {
        addEdge("A", "B");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer(5000, new Random(42));
        double ht = rwa.hittingTime(graph, "A", "B");
        // Direct neighbor => hitting time should be exactly 1.0
        assertEquals(1.0, ht, 0.01);
    }

    @Test
    public void testHittingTime_pathGraph() {
        // A -- B -- C: hitting A->C should be ~2
        addEdge("A", "B");
        addEdge("B", "C");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer(10000, new Random(42));
        double ht = rwa.hittingTime(graph, "A", "C");
        // For path A-B-C: from A, must go to B (1 step), then from B with
        // equal probability go to A or C, so expected is > 2
        assertTrue(ht > 1.5);
        assertTrue(ht < 10.0);
    }

    @Test
    public void testHittingTime_disconnected() {
        graph.addVertex("A");
        graph.addVertex("B");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer(100, new Random(42));
        double ht = rwa.hittingTime(graph, "A", "B");
        assertEquals(Double.POSITIVE_INFINITY, ht, 0.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHittingTime_nullGraph() {
        new RandomWalkAnalyzer().hittingTime(null, "A", "B");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHittingTime_nodeNotInGraph() {
        addEdge("A", "B");
        new RandomWalkAnalyzer().hittingTime(graph, "A", "Z");
    }

    // ── Hitting Times From (batch) ─────────────────────────────────

    @Test
    public void testHittingTimesFrom_selfIsZero() {
        addEdge("A", "B");
        addEdge("B", "C");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer(1000, new Random(42));
        Map<String, Double> htMap = rwa.hittingTimesFrom(graph, "A");
        assertEquals(0.0, htMap.get("A"), 0.001);
    }

    @Test
    public void testHittingTimesFrom_coversAllVertices() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer(1000, new Random(42));
        Map<String, Double> htMap = rwa.hittingTimesFrom(graph, "A");
        assertEquals(4, htMap.size());
        for (String v : Arrays.asList("A", "B", "C", "D")) {
            assertTrue(htMap.containsKey(v));
        }
    }

    @Test
    public void testHittingTimesFrom_directNeighborIsOne() {
        addEdge("A", "B");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer(5000, new Random(42));
        Map<String, Double> htMap = rwa.hittingTimesFrom(graph, "A");
        // Only neighbor => exactly 1 step
        assertEquals(1.0, htMap.get("B"), 0.05);
    }

    @Test
    public void testHittingTimesFrom_disconnectedIsInfinity() {
        addEdge("A", "B");
        graph.addVertex("C");  // isolated
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer(500, new Random(42));
        Map<String, Double> htMap = rwa.hittingTimesFrom(graph, "A");
        assertEquals(Double.POSITIVE_INFINITY, htMap.get("C"), 0.0);
    }

    // ── Commute Distance ───────────────────────────────────────────

    @Test
    public void testCommuteDistance_positive() {
        addEdge("A", "B");
        addEdge("B", "C");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer(5000, new Random(42));
        double cd = rwa.commuteDistance(graph, "A", "B");
        // Commute = H(A,B) + H(B,A); H(A,B) = 1, H(B,A) > 1 on a path
        assertTrue(cd > 1.5);
        assertTrue(cd < 20.0);
    }

    // ── Cover Time ─────────────────────────────────────────────────

    @Test
    public void testCoverTime_singleNode() {
        graph.addVertex("A");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer(100, new Random(42));
        double ct = rwa.coverTime(graph, "A");
        assertEquals(0.0, ct, 0.001);
    }

    @Test
    public void testCoverTime_completeGraph() {
        // K4: cover time should be moderate
        addEdge("A", "B"); addEdge("A", "C"); addEdge("A", "D");
        addEdge("B", "C"); addEdge("B", "D"); addEdge("C", "D");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer(5000, new Random(42));
        double ct = rwa.coverTime(graph, "A");
        assertTrue(ct > 0);
        assertTrue(ct < 50);  // K4 cover time is about 8.33
    }

    @Test
    public void testCoverTime_pathGraph() {
        // Path A-B-C: cover time from A is higher than K3
        addEdge("A", "B");
        addEdge("B", "C");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer(5000, new Random(42));
        double ct = rwa.coverTime(graph, "A");
        assertTrue(ct > 2.0);
    }

    // ── Return Time ────────────────────────────────────────────────

    @Test
    public void testReturnTime_isolatedNode() {
        graph.addVertex("A");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer(100, new Random(42));
        double rt = rwa.returnTime(graph, "A");
        assertEquals(Double.POSITIVE_INFINITY, rt, 0.0);
    }

    @Test
    public void testReturnTime_twoNodes() {
        addEdge("A", "B");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer(5000, new Random(42));
        double rt = rwa.returnTime(graph, "A");
        // Implementation: step to neighbor then walk back; finite and > 0
        assertTrue(rt > 0);
        assertTrue(rt < 10.0);
    }

    @Test
    public void testReturnTime_starCenter() {
        // Center of star has high degree, should return quickly
        addEdge("C", "A"); addEdge("C", "B"); addEdge("C", "D"); addEdge("C", "E");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer(5000, new Random(42));
        double rt = rwa.returnTime(graph, "C");
        // Leaves have degree 1 so always bounce back to center
        assertTrue(rt > 0);
        assertTrue(rt < 10.0);
    }

    // ── Stationary Distribution ────────────────────────────────────

    @Test
    public void testStationaryDistribution_sumsToOne() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer();
        Map<String, Double> sd = rwa.stationaryDistribution(graph);
        double sum = 0;
        for (double v : sd.values()) sum += v;
        assertEquals(1.0, sum, 1e-10);
    }

    @Test
    public void testStationaryDistribution_regularGraphUniform() {
        // Triangle: all degree 2 => uniform distribution
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer();
        Map<String, Double> sd = rwa.stationaryDistribution(graph);
        for (double v : sd.values()) {
            assertEquals(1.0 / 3.0, v, 1e-10);
        }
    }

    @Test
    public void testStationaryDistribution_higherDegreeMoreVisited() {
        // Star: center has degree 3, leaves have degree 1
        addEdge("C", "L1"); addEdge("C", "L2"); addEdge("C", "L3");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer();
        Map<String, Double> sd = rwa.stationaryDistribution(graph);
        assertTrue(sd.get("C") > sd.get("L1"));
    }

    @Test
    public void testStationaryDistribution_isolatedNodesUniform() {
        graph.addVertex("A");
        graph.addVertex("B");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer();
        Map<String, Double> sd = rwa.stationaryDistribution(graph);
        assertEquals(0.5, sd.get("A"), 1e-10);
        assertEquals(0.5, sd.get("B"), 1e-10);
    }

    // ── Mixing Time ────────────────────────────────────────────────

    @Test
    public void testMixingTime_emptyGraph() {
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer();
        assertEquals(0, rwa.mixingTime(graph, 0.1));
    }

    @Test
    public void testMixingTime_completeGraphFast() {
        addEdge("A", "B"); addEdge("A", "C"); addEdge("A", "D");
        addEdge("B", "C"); addEdge("B", "D"); addEdge("C", "D");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer();
        int mt = rwa.mixingTime(graph, 0.1);
        // Complete graph mixes in very few steps
        assertTrue(mt <= 10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMixingTime_invalidEpsilon() {
        addEdge("A", "B");
        new RandomWalkAnalyzer().mixingTime(graph, 0.0);
    }

    // ── Walk Trace ─────────────────────────────────────────────────

    @Test
    public void testWalkTrace_startsAtSource() {
        addEdge("A", "B");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer(100, new Random(42));
        List<String> trace = rwa.walkTrace(graph, "A", 5);
        assertEquals("A", trace.get(0));
    }

    @Test
    public void testWalkTrace_lengthIsStepsPlusOne() {
        addEdge("A", "B"); addEdge("B", "C");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer(100, new Random(42));
        List<String> trace = rwa.walkTrace(graph, "A", 10);
        assertEquals(11, trace.size());
    }

    @Test
    public void testWalkTrace_zeroSteps() {
        addEdge("A", "B");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer(100, new Random(42));
        List<String> trace = rwa.walkTrace(graph, "A", 0);
        assertEquals(1, trace.size());
        assertEquals("A", trace.get(0));
    }

    @Test
    public void testWalkTrace_isolatedNodeStopsImmediately() {
        graph.addVertex("A");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer(100, new Random(42));
        List<String> trace = rwa.walkTrace(graph, "A", 10);
        assertEquals(1, trace.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWalkTrace_negativeSteps() {
        addEdge("A", "B");
        new RandomWalkAnalyzer().walkTrace(graph, "A", -1);
    }

    // ── Visit Frequency ────────────────────────────────────────────

    @Test
    public void testVisitFrequency_sumsToOne() {
        addEdge("A", "B"); addEdge("B", "C"); addEdge("C", "A");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer(100, new Random(42));
        Map<String, Double> freq = rwa.visitFrequency(graph, "A", 1000);
        double sum = 0;
        for (double v : freq.values()) sum += v;
        assertEquals(1.0, sum, 1e-10);
    }

    @Test
    public void testVisitFrequency_coversAllVertices() {
        addEdge("A", "B"); addEdge("B", "C"); addEdge("C", "A");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer(100, new Random(42));
        Map<String, Double> freq = rwa.visitFrequency(graph, "A", 100);
        assertEquals(3, freq.size());
    }

    // ── Summarize ──────────────────────────────────────────────────

    @Test
    public void testSummarize_triangle() {
        addEdge("A", "B"); addEdge("B", "C"); addEdge("C", "A");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer(1000, new Random(42));
        RandomWalkAnalyzer.WalkSummary<String> summary = rwa.summarize(graph);
        assertEquals(3, summary.getNodeCount());
        assertEquals(3, summary.getEdgeCount());
        assertNotNull(summary.getMostVisitedNode());
        assertNotNull(summary.getLeastVisitedNode());
        assertTrue(summary.getCoverTimeFromBest() > 0);
    }

    @Test
    public void testSummarize_toString() {
        addEdge("A", "B"); addEdge("B", "C"); addEdge("C", "A");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer(1000, new Random(42));
        RandomWalkAnalyzer.WalkSummary<String> summary = rwa.summarize(graph);
        String s = summary.toString();
        assertTrue(s.contains("WalkSummary"));
        assertTrue(s.contains("nodes=3"));
    }

    @Test
    public void testSummarize_stationaryDistribution() {
        addEdge("A", "B"); addEdge("B", "C"); addEdge("C", "A");
        RandomWalkAnalyzer rwa = new RandomWalkAnalyzer(1000, new Random(42));
        RandomWalkAnalyzer.WalkSummary<String> summary = rwa.summarize(graph);
        Map<String, Double> sd = summary.getStationaryDistribution();
        assertEquals(3, sd.size());
    }
}
