package gvisual;

import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

/**
 * Tests for {@link GraphDistanceDistribution}.
 */
public class GraphDistanceDistributionTest {

    // --- helpers ---

    private Graph<String, Edge> makePath(int n) {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < n; i++) g.addVertex("v" + i);
        for (int i = 0; i < n - 1; i++) {
            g.addEdge(new Edge("e", "v" + i, "v" + (i + 1)), "v" + i, "v" + (i + 1));
        }
        return g;
    }

    private Graph<String, Edge> makeComplete(int n) {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < n; i++) g.addVertex("v" + i);
        int id = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                g.addEdge(new Edge("e" + id++, "v" + i, "v" + j), "v" + i, "v" + j);
            }
        }
        return g;
    }

    private Graph<String, Edge> makeStar(int leaves) {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("center");
        for (int i = 0; i < leaves; i++) {
            String leaf = "leaf" + i;
            g.addVertex(leaf);
            g.addEdge(new Edge("e" + i, "center", leaf), "center", leaf);
        }
        return g;
    }

    // --- constructor ---

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraph() {
        new GraphDistanceDistribution(null);
    }

    @Test(expected = IllegalStateException.class)
    public void testNotComputed() {
        GraphDistanceDistribution dd = new GraphDistanceDistribution(makePath(3));
        dd.getAveragePathLength();
    }

    // --- empty / single vertex ---

    @Test
    public void testEmptyGraph() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        GraphDistanceDistribution dd = new GraphDistanceDistribution(g);
        dd.compute();
        assertEquals(0.0, dd.getAveragePathLength(), 0.001);
        assertEquals(0, dd.getWienerIndex());
        assertTrue(dd.getDistanceHistogram().isEmpty());
        assertEquals(0.0, dd.getSeparationRatio(), 0.001);
    }

    @Test
    public void testSingleVertex() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("a");
        GraphDistanceDistribution dd = new GraphDistanceDistribution(g);
        dd.compute();
        assertEquals(0.0, dd.getAveragePathLength(), 0.001);
        assertEquals(0, dd.getWienerIndex());
        assertEquals(0.0, dd.getSeparationRatio(), 0.001);
    }

    // --- path graph ---

    @Test
    public void testPathDistances() {
        // Path: v0 - v1 - v2 - v3
        GraphDistanceDistribution dd = new GraphDistanceDistribution(makePath(4));
        dd.compute();
        assertEquals(0, dd.getDistance("v0", "v0"));
        assertEquals(1, dd.getDistance("v0", "v1"));
        assertEquals(2, dd.getDistance("v0", "v2"));
        assertEquals(3, dd.getDistance("v0", "v3"));
        assertEquals(1, dd.getDistance("v2", "v3"));
    }

    @Test
    public void testPathHistogram() {
        GraphDistanceDistribution dd = new GraphDistanceDistribution(makePath(4));
        dd.compute();
        Map<Integer, Integer> hist = dd.getDistanceHistogram();
        // d=1: 3 pairs, d=2: 2 pairs, d=3: 1 pair
        assertEquals(3, (int) hist.get(1));
        assertEquals(2, (int) hist.get(2));
        assertEquals(1, (int) hist.get(3));
    }

    @Test
    public void testPathAverageLength() {
        GraphDistanceDistribution dd = new GraphDistanceDistribution(makePath(4));
        dd.compute();
        // (1+1+1+2+2+3) / 6 = 10/6 ≈ 1.6667
        assertEquals(10.0 / 6.0, dd.getAveragePathLength(), 0.001);
    }

    @Test
    public void testPathWienerIndex() {
        GraphDistanceDistribution dd = new GraphDistanceDistribution(makePath(4));
        dd.compute();
        assertEquals(10, dd.getWienerIndex());
    }

    // --- complete graph ---

    @Test
    public void testCompleteGraphDistances() {
        GraphDistanceDistribution dd = new GraphDistanceDistribution(makeComplete(5));
        dd.compute();
        // All pairs at distance 1
        assertEquals(1.0, dd.getAveragePathLength(), 0.001);
        // C(5,2) = 10 pairs, each distance 1
        assertEquals(10, dd.getWienerIndex());
    }

    @Test
    public void testCompleteGraphHistogram() {
        GraphDistanceDistribution dd = new GraphDistanceDistribution(makeComplete(4));
        dd.compute();
        Map<Integer, Integer> hist = dd.getDistanceHistogram();
        assertEquals(1, hist.size());
        assertEquals(6, (int) hist.get(1));
    }

    // --- star graph ---

    @Test
    public void testStarRemoteness() {
        GraphDistanceDistribution dd = new GraphDistanceDistribution(makeStar(4));
        dd.compute();
        // Center has distance 1 to all leaves → remoteness = 1.0
        assertEquals(1.0, dd.getVertexRemoteness("center"), 0.001);
        // Leaf has distance 1 to center, 2 to other 3 leaves → (1+2+2+2)/4 = 7/4
        assertEquals(7.0 / 4.0, dd.getVertexRemoteness("leaf0"), 0.001);
    }

    @Test
    public void testStarRemotenessRanking() {
        GraphDistanceDistribution dd = new GraphDistanceDistribution(makeStar(4));
        dd.compute();
        List<Map.Entry<String, Double>> ranked = dd.getAllRemotenessRanked();
        assertEquals("center", ranked.get(0).getKey());
    }

    // --- separation ratio ---

    @Test
    public void testConnectedSeparation() {
        GraphDistanceDistribution dd = new GraphDistanceDistribution(makePath(4));
        dd.compute();
        assertEquals(0.0, dd.getSeparationRatio(), 0.001);
    }

    @Test
    public void testDisconnectedSeparation() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("a");
        g.addVertex("b");
        g.addVertex("c");
        g.addEdge(new Edge("e1", "a", "b"), "a", "b");
        // c is isolated → 2 out of 3 pairs unreachable
        GraphDistanceDistribution dd = new GraphDistanceDistribution(g);
        dd.compute();
        // Reachable: (a,b). Unreachable: (a,c), (b,c). Ratio = 2/3
        assertEquals(2.0 / 3.0, dd.getSeparationRatio(), 0.001);
    }

    // --- harmonic mean ---

    @Test
    public void testHarmonicMeanComplete() {
        GraphDistanceDistribution dd = new GraphDistanceDistribution(makeComplete(4));
        dd.compute();
        // All distances = 1 → harmonic = 1.0
        assertEquals(1.0, dd.getHarmonicMeanDistance(), 0.001);
    }

    @Test
    public void testHarmonicMeanDisconnected() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("a");
        g.addVertex("b");
        // No edges → infinite harmonic mean
        GraphDistanceDistribution dd = new GraphDistanceDistribution(g);
        dd.compute();
        assertTrue(Double.isInfinite(dd.getHarmonicMeanDistance()));
    }

    // --- percentiles ---

    @Test
    public void testMedianDistance() {
        GraphDistanceDistribution dd = new GraphDistanceDistribution(makePath(5));
        dd.compute();
        // Distances: 1,1,1,1,2,2,2,3,3,4 → sorted, median at index 4 = 2
        assertEquals(2, dd.getMedianDistance());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPercentile() {
        GraphDistanceDistribution dd = new GraphDistanceDistribution(makePath(3));
        dd.compute();
        dd.getDistancePercentile(101);
    }

    // --- distinct distances ---

    @Test
    public void testDistinctDistances() {
        GraphDistanceDistribution dd = new GraphDistanceDistribution(makePath(5));
        dd.compute();
        assertEquals(4, dd.getDistinctDistanceCount());
    }

    @Test
    public void testDistinctDistancesComplete() {
        GraphDistanceDistribution dd = new GraphDistanceDistribution(makeComplete(5));
        dd.compute();
        assertEquals(1, dd.getDistinctDistanceCount());
    }

    // --- unreachable distance ---

    @Test
    public void testUnreachableDistance() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("a");
        g.addVertex("b");
        GraphDistanceDistribution dd = new GraphDistanceDistribution(g);
        dd.compute();
        assertEquals(-1, dd.getDistance("a", "b"));
    }

    @Test
    public void testNonexistentVertex() {
        GraphDistanceDistribution dd = new GraphDistanceDistribution(makePath(3));
        dd.compute();
        assertEquals(-1, dd.getDistance("nonexistent", "v0"));
    }

    // --- remoteness edge cases ---

    @Test
    public void testRemotenessIsolated() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("a");
        GraphDistanceDistribution dd = new GraphDistanceDistribution(g);
        dd.compute();
        assertEquals(-1.0, dd.getVertexRemoteness("a"), 0.001);
    }

    @Test
    public void testRemotenessNonexistent() {
        GraphDistanceDistribution dd = new GraphDistanceDistribution(makePath(3));
        dd.compute();
        assertEquals(-1.0, dd.getVertexRemoteness("nope"), 0.001);
    }

    // --- exports ---

    @Test
    public void testReport() {
        GraphDistanceDistribution dd = new GraphDistanceDistribution(makePath(4));
        dd.compute();
        String report = dd.generateReport();
        assertTrue(report.contains("Distance Distribution Report"));
        assertTrue(report.contains("Average path length"));
        assertTrue(report.contains("Wiener index: 10"));
        assertTrue(report.contains("Distance Histogram"));
    }

    @Test
    public void testCsvExport() {
        GraphDistanceDistribution dd = new GraphDistanceDistribution(makePath(3));
        dd.compute();
        String csv = dd.exportCsv();
        assertTrue(csv.contains("v0"));
        assertTrue(csv.contains("v1"));
        assertTrue(csv.contains("v2"));
        // Should have header row + 3 data rows
        String[] lines = csv.split("\n");
        assertEquals(4, lines.length);
    }

    @Test
    public void testHistogramJson() {
        GraphDistanceDistribution dd = new GraphDistanceDistribution(makeComplete(3));
        dd.compute();
        String json = dd.exportHistogramJson();
        assertTrue(json.contains("\"1\":3"));
    }

    // --- directed graph ---

    @Test
    public void testDirectedGraph() {
        Graph<String, Edge> g = new DirectedSparseGraph<>();
        g.addVertex("a");
        g.addVertex("b");
        g.addVertex("c");
        g.addEdge(new Edge("e1", "a", "b"), "a", "b");
        g.addEdge(new Edge("e2", "b", "c"), "b", "c");
        // a→b→c but no reverse
        GraphDistanceDistribution dd = new GraphDistanceDistribution(g);
        dd.compute();
        assertEquals(1, dd.getDistance("a", "b"));
        assertEquals(2, dd.getDistance("a", "c"));
        assertEquals(-1, dd.getDistance("c", "a"));
    }

    // --- distance matrix ---

    @Test
    public void testDistanceMatrixSize() {
        GraphDistanceDistribution dd = new GraphDistanceDistribution(makePath(4));
        dd.compute();
        Map<String, Map<String, Integer>> matrix = dd.getDistanceMatrix();
        assertEquals(4, matrix.size());
    }

    // --- recompute ---

    @Test
    public void testRecompute() {
        GraphDistanceDistribution dd = new GraphDistanceDistribution(makePath(3));
        dd.compute();
        long w1 = dd.getWienerIndex();
        dd.compute(); // recompute should be safe
        assertEquals(w1, dd.getWienerIndex());
    }

    // --- two components ---

    @Test
    public void testTwoComponents() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("a");
        g.addVertex("b");
        g.addEdge(new Edge("e1", "a", "b"), "a", "b");
        g.addVertex("c");
        g.addVertex("d");
        g.addEdge(new Edge("e2", "c", "d"), "c", "d");
        GraphDistanceDistribution dd = new GraphDistanceDistribution(g);
        dd.compute();
        assertEquals(1, dd.getDistance("a", "b"));
        assertEquals(1, dd.getDistance("c", "d"));
        assertEquals(-1, dd.getDistance("a", "c"));
        // Wiener = 1 + 1 = 2 (only reachable pairs)
        assertEquals(2, dd.getWienerIndex());
        // 2 reachable out of 6 pairs → separation = 4/6
        assertEquals(4.0 / 6.0, dd.getSeparationRatio(), 0.001);
    }

    // --- percentile on empty ---

    @Test
    public void testPercentileEmpty() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("a");
        GraphDistanceDistribution dd = new GraphDistanceDistribution(g);
        dd.compute();
        assertEquals(-1, dd.getDistancePercentile(50));
    }

    // --- cycle graph ---

    @Test
    public void testCycleGraph() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < 6; i++) g.addVertex("v" + i);
        for (int i = 0; i < 6; i++) {
            int j = (i + 1) % 6;
            g.addEdge(new Edge("e" + i, "v" + i, "v" + j), "v" + i, "v" + j);
        }
        GraphDistanceDistribution dd = new GraphDistanceDistribution(g);
        dd.compute();
        // Cycle of 6: max distance = 3
        assertEquals(3, dd.getDistance("v0", "v3"));
        assertEquals(2, dd.getDistance("v0", "v2"));
        assertEquals(1, dd.getDistance("v0", "v1"));
        // Average: (6*1 + 6*2 + 3*3) / 15 = 27/15 = 1.8
        assertEquals(27.0 / 15.0, dd.getAveragePathLength(), 0.001);
    }
}
