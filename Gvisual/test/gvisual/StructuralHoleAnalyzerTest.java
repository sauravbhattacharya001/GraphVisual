package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for StructuralHoleAnalyzer.
 */
public class StructuralHoleAnalyzerTest {

    // ── Graph builders ──────────────────────────────────────────

    private Graph<String, edge> emptyGraph() {
        return new UndirectedSparseGraph<>();
    }

    private Graph<String, edge> singleVertex() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        return g;
    }

    private Graph<String, edge> singleEdge() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        g.addVertex("B");
        g.addEdge(new edge("f", "A", "B"), "A", "B");
        return g;
    }

    /** Triangle: A-B, B-C, A-C — fully connected, no structural holes */
    private Graph<String, edge> triangle() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addEdge(new edge("f", "A", "B"), "A", "B");
        g.addEdge(new edge("f", "B", "C"), "B", "C");
        g.addEdge(new edge("f", "A", "C"), "A", "C");
        return g;
    }

    /** Star: center connected to A, B, C, D — spokes not connected */
    private Graph<String, edge> star() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("center"); g.addVertex("A");
        g.addVertex("B"); g.addVertex("C"); g.addVertex("D");
        g.addEdge(new edge("f", "center", "A"), "center", "A");
        g.addEdge(new edge("f", "center", "B"), "center", "B");
        g.addEdge(new edge("f", "center", "C"), "center", "C");
        g.addEdge(new edge("f", "center", "D"), "center", "D");
        return g;
    }

    /**
     * Bow-tie: two triangles connected by a single bridge node.
     * A-B-C triangle, C-D-E triangle, C is the broker.
     */
    private Graph<String, edge> bowTie() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        for (String v : new String[]{"A", "B", "C", "D", "E"}) g.addVertex(v);
        g.addEdge(new edge("f", "A", "B"), "A", "B");
        g.addEdge(new edge("f", "A", "C"), "A", "C");
        g.addEdge(new edge("f", "B", "C"), "B", "C");
        g.addEdge(new edge("f", "C", "D"), "C", "D");
        g.addEdge(new edge("f", "C", "E"), "C", "E");
        g.addEdge(new edge("f", "D", "E"), "D", "E");
        return g;
    }

    /** Path: A-B-C-D — B and C are brokers between endpoints */
    private Graph<String, edge> path() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        for (String v : new String[]{"A", "B", "C", "D"}) g.addVertex(v);
        g.addEdge(new edge("f", "A", "B"), "A", "B");
        g.addEdge(new edge("f", "B", "C"), "B", "C");
        g.addEdge(new edge("f", "C", "D"), "C", "D");
        return g;
    }

    /** Complete K4 — fully connected, maximum redundancy */
    private Graph<String, edge> complete4() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        String[] vs = {"A", "B", "C", "D"};
        for (String v : vs) g.addVertex(v);
        for (int i = 0; i < vs.length; i++) {
            for (int j = i + 1; j < vs.length; j++) {
                g.addEdge(new edge("f", vs[i], vs[j]), vs[i], vs[j]);
            }
        }
        return g;
    }

    /** Two cliques connected by a single bridge */
    private Graph<String, edge> twoClusters() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        // Cluster 1: A, B, C (triangle)
        for (String v : new String[]{"A", "B", "C"}) g.addVertex(v);
        g.addEdge(new edge("f", "A", "B"), "A", "B");
        g.addEdge(new edge("f", "A", "C"), "A", "C");
        g.addEdge(new edge("f", "B", "C"), "B", "C");
        // Cluster 2: D, E, F (triangle)
        for (String v : new String[]{"D", "E", "F"}) g.addVertex(v);
        g.addEdge(new edge("f", "D", "E"), "D", "E");
        g.addEdge(new edge("f", "D", "F"), "D", "F");
        g.addEdge(new edge("f", "E", "F"), "E", "F");
        // Bridge: C -- D
        g.addEdge(new edge("f", "C", "D"), "C", "D");
        return g;
    }

    // ── Constructor tests ───────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraph() {
        new StructuralHoleAnalyzer(null);
    }

    @Test
    public void testEmptyGraph() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(emptyGraph());
        List<StructuralHoleAnalyzer.VertexMetrics> all = a.analyzeAll();
        assertTrue(all.isEmpty());
    }

    // ── Analyze single vertex ───────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testAnalyzeNullVertex() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(singleVertex());
        a.analyze(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAnalyzeNonexistentVertex() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(singleVertex());
        a.analyze("Z");
    }

    @Test
    public void testIsolatedVertex() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(singleVertex());
        StructuralHoleAnalyzer.VertexMetrics m = a.analyze("A");
        assertEquals(0, m.getDegree());
        assertEquals(0.0, m.getEffectiveSize(), 0.001);
        assertEquals(0.0, m.getEfficiency(), 0.001);
        assertEquals(0.0, m.getConstraint(), 0.001);
        assertEquals(0.0, m.getBrokerageScore(), 0.001);
        assertEquals("Isolate", m.getRole());
    }

    @Test
    public void testSingleEdge() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(singleEdge());
        StructuralHoleAnalyzer.VertexMetrics m = a.analyze("A");
        assertEquals(1, m.getDegree());
        assertEquals(1.0, m.getEffectiveSize(), 0.001);
        assertEquals(1.0, m.getEfficiency(), 0.001);
        assertEquals("Peripheral", m.getRole());
    }

    // ── Effective size ──────────────────────────────────────────

    @Test
    public void testEffectiveSizeTriangle() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(triangle());
        StructuralHoleAnalyzer.VertexMetrics m = a.analyze("A");
        assertTrue("Effective size in triangle should be < degree (2)",
                m.getEffectiveSize() < 2.0);
    }

    @Test
    public void testEffectiveSizeStar() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(star());
        StructuralHoleAnalyzer.VertexMetrics m = a.analyze("center");
        assertEquals(4.0, m.getEffectiveSize(), 0.001);
        assertEquals(1.0, m.getEfficiency(), 0.001);
    }

    @Test
    public void testEffectiveSizeComplete() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(complete4());
        StructuralHoleAnalyzer.VertexMetrics m = a.analyze("A");
        assertTrue("Effective size in K4 should be < degree (3)",
                m.getEffectiveSize() < 3.0);
    }

    // ── Constraint ──────────────────────────────────────────────

    @Test
    public void testConstraintStarCenter() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(star());
        StructuralHoleAnalyzer.VertexMetrics m = a.analyze("center");
        assertTrue("Star center constraint should be low", m.getConstraint() < 0.3);
    }

    @Test
    public void testConstraintTriangleHigher() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(triangle());
        StructuralHoleAnalyzer.VertexMetrics m = a.analyze("A");
        assertTrue("Triangle constraint should be higher than star center",
                m.getConstraint() > 0.3);
    }

    @Test
    public void testConstraintComplete() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(complete4());
        StructuralHoleAnalyzer.VertexMetrics m = a.analyze("A");
        assertTrue("K4 constraint should be > 0.3", m.getConstraint() > 0.3);
    }

    @Test
    public void testPairwiseConstraintSums() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(bowTie());
        StructuralHoleAnalyzer.VertexMetrics m = a.analyze("C");
        double sum = m.getPairwiseConstraint().values().stream()
                .mapToDouble(Double::doubleValue).sum();
        assertEquals(m.getConstraint(), sum, 0.001);
    }

    // ── Brokerage score ─────────────────────────────────────────

    @Test
    public void testStarCenterHighBrokerage() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(star());
        StructuralHoleAnalyzer.VertexMetrics center = a.analyze("center");
        StructuralHoleAnalyzer.VertexMetrics spoke = a.analyze("A");
        assertTrue("Star center should have higher brokerage than spoke",
                center.getBrokerageScore() > spoke.getBrokerageScore());
    }

    @Test
    public void testBowTieBroker() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(bowTie());
        StructuralHoleAnalyzer.VertexMetrics mC = a.analyze("C");
        StructuralHoleAnalyzer.VertexMetrics mA = a.analyze("A");
        assertTrue("Bow-tie bridge (C) should have higher brokerage than leaf (A)",
                mC.getBrokerageScore() > mA.getBrokerageScore());
    }

    @Test
    public void testBrokerageScoreRange() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(bowTie());
        for (StructuralHoleAnalyzer.VertexMetrics vm : a.analyzeAll()) {
            assertTrue(vm.getBrokerageScore() >= 0);
            assertTrue(vm.getBrokerageScore() <= 100);
        }
    }

    @Test
    public void testPathMiddleBrokerage() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(path());
        StructuralHoleAnalyzer.VertexMetrics mB = a.analyze("B");
        StructuralHoleAnalyzer.VertexMetrics mA = a.analyze("A");
        assertTrue("Path middle node should have higher brokerage than endpoint",
                mB.getBrokerageScore() > mA.getBrokerageScore());
    }

    // ── Bridge score ────────────────────────────────────────────

    @Test
    public void testBridgeScoreStarCenter() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(star());
        StructuralHoleAnalyzer.VertexMetrics m = a.analyze("center");
        assertEquals(1.0, m.getBridgeScore(), 0.001);
    }

    @Test
    public void testBridgeScoreTriangle() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(triangle());
        StructuralHoleAnalyzer.VertexMetrics m = a.analyze("A");
        assertEquals(0.0, m.getBridgeScore(), 0.001);
    }

    @Test
    public void testBridgeScoreIsolated() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(singleVertex());
        StructuralHoleAnalyzer.VertexMetrics m = a.analyze("A");
        assertEquals(0.0, m.getBridgeScore(), 0.001);
    }

    // ── Hierarchy ───────────────────────────────────────────────

    @Test
    public void testHierarchySymmetric() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(star());
        StructuralHoleAnalyzer.VertexMetrics m = a.analyze("center");
        assertEquals(0.0, m.getHierarchy(), 0.001);
    }

    @Test
    public void testHierarchyNonnegative() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(bowTie());
        for (StructuralHoleAnalyzer.VertexMetrics vm : a.analyzeAll()) {
            assertTrue(vm.getHierarchy() >= 0);
        }
    }

    // ── Roles ───────────────────────────────────────────────────

    @Test
    public void testRoleIsolate() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(singleVertex());
        assertEquals("Isolate", a.analyze("A").getRole());
    }

    @Test
    public void testRolePeripheral() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(singleEdge());
        assertEquals("Peripheral", a.analyze("A").getRole());
    }

    @Test
    public void testRoleBrokerInStar() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(star());
        String role = a.analyze("center").getRole();
        assertTrue("Star center should be Key Broker or Broker",
                role.contains("Broker"));
    }

    // ── analyzeAll ──────────────────────────────────────────────

    @Test
    public void testAnalyzeAllReturnsAllVertices() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(bowTie());
        assertEquals(5, a.analyzeAll().size());
    }

    @Test
    public void testAnalyzeAllSortedByBrokerage() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(bowTie());
        List<StructuralHoleAnalyzer.VertexMetrics> all = a.analyzeAll();
        for (int i = 1; i < all.size(); i++) {
            assertTrue(all.get(i - 1).getBrokerageScore() >= all.get(i).getBrokerageScore());
        }
    }

    // ── topBrokers ──────────────────────────────────────────────

    @Test
    public void testTopBrokers() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(bowTie());
        List<StructuralHoleAnalyzer.VertexMetrics> top = a.topBrokers(2);
        assertEquals(2, top.size());
        assertEquals("C", top.get(0).getVertex());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTopBrokersInvalid() {
        new StructuralHoleAnalyzer(bowTie()).topBrokers(0);
    }

    @Test
    public void testTopBrokersExceedsSize() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(singleEdge());
        assertEquals(2, a.topBrokers(10).size());
    }

    // ── mostConstrained ─────────────────────────────────────────

    @Test
    public void testMostConstrained() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(bowTie());
        List<StructuralHoleAnalyzer.VertexMetrics> mc = a.mostConstrained(2);
        assertEquals(2, mc.size());
        assertTrue(mc.get(0).getConstraint() >= mc.get(1).getConstraint());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMostConstrainedInvalid() {
        new StructuralHoleAnalyzer(bowTie()).mostConstrained(0);
    }

    // ── findBridgingEdges ───────────────────────────────────────

    @Test
    public void testBridgingEdgesTriangle() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(triangle());
        List<StructuralHoleAnalyzer.BridgingEdge> bridges = a.findBridgingEdges();
        assertTrue("Triangle should have no bridging edges", bridges.isEmpty());
    }

    @Test
    public void testBridgingEdgesTwoClusters() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(twoClusters());
        List<StructuralHoleAnalyzer.BridgingEdge> bridges = a.findBridgingEdges();
        assertFalse(bridges.isEmpty());
        boolean foundCD = bridges.stream().anyMatch(be ->
                (be.getVertex1().equals("C") && be.getVertex2().equals("D")) ||
                (be.getVertex1().equals("D") && be.getVertex2().equals("C")));
        assertTrue("C-D bridge should be detected", foundCD);
    }

    @Test
    public void testBridgingEdgeStar() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(star());
        List<StructuralHoleAnalyzer.BridgingEdge> bridges = a.findBridgingEdges();
        assertEquals(4, bridges.size());
    }

    @Test
    public void testBridgingEdgeSortedByStrength() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(twoClusters());
        List<StructuralHoleAnalyzer.BridgingEdge> bridges = a.findBridgingEdges();
        for (int i = 1; i < bridges.size(); i++) {
            assertTrue(bridges.get(i - 1).getBridgeStrength() >=
                    bridges.get(i).getBridgeStrength());
        }
    }

    // ── identifyHoles ───────────────────────────────────────────

    @Test
    public void testIdentifyHolesTwoClusters() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(twoClusters());
        List<StructuralHoleAnalyzer.BridgingEdge> holes = a.identifyHoles();
        assertFalse(holes.isEmpty());
        for (StructuralHoleAnalyzer.BridgingEdge h : holes) {
            assertTrue(h.getBridgeStrength() >= 0.75);
        }
    }

    @Test
    public void testIdentifyHolesTriangle() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(triangle());
        assertTrue(a.identifyHoles().isEmpty());
    }

    // ── compareVertices ─────────────────────────────────────────

    @Test
    public void testCompareVertices() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(bowTie());
        String cmp = a.compareVertices("C", "A");
        assertTrue(cmp.contains("C"));
        assertTrue(cmp.contains("A"));
        assertTrue(cmp.contains("Brokerage Score"));
        assertTrue(cmp.contains("stronger brokerage position"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCompareVerticesNull() {
        new StructuralHoleAnalyzer(bowTie()).compareVertices(null, "A");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCompareVerticesNotInGraph() {
        new StructuralHoleAnalyzer(bowTie()).compareVertices("A", "Z");
    }

    // ── generateReport ──────────────────────────────────────────

    @Test
    public void testReportBowTie() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(bowTie());
        StructuralHoleAnalyzer.BrokerageReport r = a.generateReport();
        assertEquals(5, r.getVertexCount());
        assertEquals(6, r.getEdgeCount());
        assertEquals(5, r.getAllMetrics().size());
        assertFalse(r.getTopBrokers().isEmpty());
    }

    @Test
    public void testReportEmptyGraph() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(emptyGraph());
        StructuralHoleAnalyzer.BrokerageReport r = a.generateReport();
        assertEquals(0, r.getVertexCount());
        assertEquals(0, r.getAllMetrics().size());
        assertEquals(0.0, r.getAvgConstraint(), 0.001);
    }

    @Test
    public void testReportAverages() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(star());
        StructuralHoleAnalyzer.BrokerageReport r = a.generateReport();
        assertTrue(r.getAvgBrokerage() > 0);
        assertTrue(r.getAvgEfficiency() > 0);
    }

    @Test
    public void testReportNetworkClosure() {
        StructuralHoleAnalyzer a1 = new StructuralHoleAnalyzer(complete4());
        double closureComplete = a1.generateReport().getNetworkClosure();
        StructuralHoleAnalyzer a2 = new StructuralHoleAnalyzer(star());
        double closureStar = a2.generateReport().getNetworkClosure();
        assertTrue("Complete graph should have higher closure than star",
                closureComplete > closureStar);
    }

    // ── formatReport ────────────────────────────────────────────

    @Test
    public void testFormatReportContainsSections() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(bowTie());
        String text = a.formatReport(a.generateReport());
        assertTrue(text.contains("Structural Hole Analysis Report"));
        assertTrue(text.contains("Top Brokers"));
        assertTrue(text.contains("Most Constrained"));
        assertTrue(text.contains("Role Distribution"));
        assertTrue(text.contains("Interpretation"));
    }

    @Test
    public void testFormatReportEmpty() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(emptyGraph());
        String text = a.formatReport(a.generateReport());
        assertTrue(text.contains("0 vertices"));
    }

    // ── toString ────────────────────────────────────────────────

    @Test
    public void testVertexMetricsToString() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(star());
        String s = a.analyze("center").toString();
        assertTrue(s.contains("center"));
        assertTrue(s.contains("effSize"));
    }

    @Test
    public void testBridgingEdgeToString() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(star());
        List<StructuralHoleAnalyzer.BridgingEdge> bridges = a.findBridgingEdges();
        assertFalse(bridges.isEmpty());
        String s = bridges.get(0).toString();
        assertTrue(s.contains("strength"));
    }

    // ── Two clusters analysis ───────────────────────────────────

    @Test
    public void testTwoClustersBridgeNodes() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(twoClusters());
        StructuralHoleAnalyzer.VertexMetrics mC = a.analyze("C");
        StructuralHoleAnalyzer.VertexMetrics mA = a.analyze("A");
        assertTrue("Bridge node C should have higher brokerage than interior A",
                mC.getBrokerageScore() > mA.getBrokerageScore());
    }

    @Test
    public void testTwoClustersEfficiency() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(twoClusters());
        StructuralHoleAnalyzer.VertexMetrics mC = a.analyze("C");
        StructuralHoleAnalyzer.VertexMetrics mA = a.analyze("A");
        assertTrue(mC.getEfficiency() >= mA.getEfficiency());
    }

    // ── Edge cases ──────────────────────────────────────────────

    @Test
    public void testSingleVertexReport() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(singleVertex());
        StructuralHoleAnalyzer.BrokerageReport r = a.generateReport();
        assertEquals(1, r.getVertexCount());
        assertEquals(0, r.getEdgeCount());
    }

    @Test
    public void testPathEfficiency() {
        StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(path());
        assertEquals(1.0, a.analyze("B").getEfficiency(), 0.001);
        assertEquals(1.0, a.analyze("C").getEfficiency(), 0.001);
    }

    @Test
    public void testConstraintNonnegative() {
        for (Graph<String, edge> g : Arrays.asList(star(), triangle(), bowTie(), path(), complete4())) {
            StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(g);
            for (StructuralHoleAnalyzer.VertexMetrics vm : a.analyzeAll()) {
                assertTrue("Constraint should be non-negative for " + vm.getVertex(),
                        vm.getConstraint() >= 0);
            }
        }
    }

    @Test
    public void testEfficiencyBetweenZeroAndOne() {
        for (Graph<String, edge> g : Arrays.asList(star(), triangle(), bowTie(), path(), complete4())) {
            StructuralHoleAnalyzer a = new StructuralHoleAnalyzer(g);
            for (StructuralHoleAnalyzer.VertexMetrics vm : a.analyzeAll()) {
                if (vm.getDegree() > 0) {
                    assertTrue("Efficiency should be >= 0", vm.getEfficiency() >= 0);
                    assertTrue("Efficiency should be <= 1", vm.getEfficiency() <= 1.001);
                }
            }
        }
    }
}
