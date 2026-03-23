package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link BandwidthMinimizer}.
 */
public class BandwidthMinimizerTest {

    private Graph<String, String> buildPathGraph(int n) {
        Graph<String, String> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < n; i++) g.addVertex("v" + i);
        for (int i = 0; i < n - 1; i++) {
            g.addEdge("e" + i, "v" + i, "v" + (i + 1));
        }
        return g;
    }

    private Graph<String, String> buildGridGraph(int rows, int cols) {
        Graph<String, String> g = new UndirectedSparseGraph<>();
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                g.addVertex(r + "_" + c);
        int eid = 0;
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                if (c + 1 < cols) g.addEdge("e" + eid++, r + "_" + c, r + "_" + (c + 1));
                if (r + 1 < rows) g.addEdge("e" + eid++, r + "_" + c, (r + 1) + "_" + c);
            }
        return g;
    }

    @Test
    public void testEmptyGraph() {
        Graph<String, String> g = new UndirectedSparseGraph<>();
        BandwidthMinimizer.BandwidthResult r = BandwidthMinimizer.cuthillMcKee(g);
        assertEquals(0, r.getBandwidth());
        assertEquals(0, r.getProfile());
        assertTrue(r.getOrdering().isEmpty());
    }

    @Test
    public void testSingleVertex() {
        Graph<String, String> g = new UndirectedSparseGraph<>();
        g.addVertex("a");
        BandwidthMinimizer.BandwidthResult r = BandwidthMinimizer.cuthillMcKee(g);
        assertEquals(0, r.getBandwidth());
        assertEquals(1, r.getOrdering().size());
    }

    @Test
    public void testPathGraphBandwidth() {
        Graph<String, String> g = buildPathGraph(5);
        // Natural path ordering should have bandwidth 1
        List<String> natural = Arrays.asList("v0", "v1", "v2", "v3", "v4");
        BandwidthMinimizer.BandwidthResult r =
                BandwidthMinimizer.computeBandwidth(g, natural);
        assertEquals(1, r.getBandwidth());
    }

    @Test
    public void testCuthillMcKeeReducesBandwidth() {
        // Grid graph with bad natural ordering
        Graph<String, String> g = buildGridGraph(3, 3);
        BandwidthMinimizer.ComparisonReport report = BandwidthMinimizer.compare(g);
        assertNotNull(report.getOriginal());
        assertNotNull(report.getCuthillMcKee());
        assertNotNull(report.getReverseCuthillMcKee());
        assertNotNull(report.getBest());
        // CM/RCM should not be worse than some reasonable bound
        assertTrue(report.getBest().getBandwidth() <= report.getOriginal().getBandwidth());
    }

    @Test
    public void testReverseCuthillMcKee() {
        Graph<String, String> g = buildGridGraph(4, 4);
        BandwidthMinimizer.BandwidthResult rcm = BandwidthMinimizer.reverseCuthillMcKee(g);
        assertTrue(rcm.getBandwidth() > 0);
        assertEquals(16, rcm.getOrdering().size());
    }

    @Test
    public void testDisconnectedGraph() {
        Graph<String, String> g = new UndirectedSparseGraph<>();
        g.addVertex("a"); g.addVertex("b"); g.addEdge("e1", "a", "b");
        g.addVertex("x"); g.addVertex("y"); g.addEdge("e2", "x", "y");
        BandwidthMinimizer.BandwidthResult r = BandwidthMinimizer.cuthillMcKee(g);
        assertEquals(4, r.getOrdering().size());
        // bandwidth >= 1 (within each component)
        assertTrue(r.getBandwidth() >= 1);
    }

    @Test
    public void testComparisonReportToString() {
        Graph<String, String> g = buildGridGraph(3, 3);
        BandwidthMinimizer.ComparisonReport report = BandwidthMinimizer.compare(g);
        String s = report.toString();
        assertTrue(s.contains("Original"));
        assertTrue(s.contains("Cuthill-McKee"));
        assertTrue(s.contains("Reverse CM"));
    }

    @Test
    public void testProfileComputation() {
        Graph<String, String> g = buildPathGraph(4);
        List<String> ordering = Arrays.asList("v0", "v1", "v2", "v3");
        BandwidthMinimizer.BandwidthResult r =
                BandwidthMinimizer.computeBandwidth(g, ordering);
        // Each vertex has row bandwidth 1 (except endpoints which connect to 1 neighbor)
        // Profile = sum of row bandwidths = 1+1+1+1 = 4 (bidirectional edges)
        assertEquals(4, r.getProfile());
    }
}
