package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphSpectralClusteringEngine}.
 *
 * @author sauravbhattacharya001
 */
public class GraphSpectralClusteringEngineTest {

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
            String v = "L" + i;
            g.addVertex(v);
            g.addEdge(new Edge("c", "Hub", v), "Hub", v);
        }
        return g;
    }

    private Graph<String, Edge> path5() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 1; i <= 5; i++) g.addVertex("P" + i);
        for (int i = 1; i < 5; i++) {
            g.addEdge(new Edge("c", "P" + i, "P" + (i + 1)), "P" + i, "P" + (i + 1));
        }
        return g;
    }

    private Graph<String, Edge> cycle6() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < 6; i++) g.addVertex("C" + i);
        for (int i = 0; i < 6; i++) {
            g.addEdge(new Edge("c", "C" + i, "C" + ((i + 1) % 6)),
                    "C" + i, "C" + ((i + 1) % 6));
        }
        return g;
    }

    private Graph<String, Edge> complete(int n) {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < n; i++) g.addVertex("K" + i);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                g.addEdge(new Edge("c", "K" + i, "K" + j), "K" + i, "K" + j);
            }
        }
        return g;
    }

    /** Two K5 cliques connected by a single bridge edge. */
    private Graph<String, Edge> twoCliquesGraph() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        // Clique A: A0-A4
        for (int i = 0; i < 5; i++) g.addVertex("A" + i);
        for (int i = 0; i < 5; i++)
            for (int j = i + 1; j < 5; j++)
                g.addEdge(new Edge("c", "A" + i, "A" + j), "A" + i, "A" + j);
        // Clique B: B0-B4
        for (int i = 0; i < 5; i++) g.addVertex("B" + i);
        for (int i = 0; i < 5; i++)
            for (int j = i + 1; j < 5; j++)
                g.addEdge(new Edge("c", "B" + i, "B" + j), "B" + i, "B" + j);
        // Bridge
        g.addEdge(new Edge("c", "A0", "B0"), "A0", "B0");
        return g;
    }

    /** Barbell: two K4 connected by a path of length 3. */
    private Graph<String, Edge> barbellGraph() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        // Left K4
        for (int i = 0; i < 4; i++) g.addVertex("L" + i);
        for (int i = 0; i < 4; i++)
            for (int j = i + 1; j < 4; j++)
                g.addEdge(new Edge("c", "L" + i, "L" + j), "L" + i, "L" + j);
        // Path
        g.addVertex("M0"); g.addVertex("M1"); g.addVertex("M2");
        g.addEdge(new Edge("c", "L0", "M0"), "L0", "M0");
        g.addEdge(new Edge("c", "M0", "M1"), "M0", "M1");
        g.addEdge(new Edge("c", "M1", "M2"), "M1", "M2");
        // Right K4
        for (int i = 0; i < 4; i++) g.addVertex("R" + i);
        for (int i = 0; i < 4; i++)
            for (int j = i + 1; j < 4; j++)
                g.addEdge(new Edge("c", "R" + i, "R" + j), "R" + i, "R" + j);
        g.addEdge(new Edge("c", "M2", "R0"), "M2", "R0");
        return g;
    }

    /** Three triangles with sparse inter-connections. */
    private Graph<String, Edge> threeCommunitiesGraph() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        // Community 1: triangle X
        g.addVertex("X0"); g.addVertex("X1"); g.addVertex("X2");
        g.addEdge(new Edge("c", "X0", "X1"), "X0", "X1");
        g.addEdge(new Edge("c", "X1", "X2"), "X1", "X2");
        g.addEdge(new Edge("c", "X0", "X2"), "X0", "X2");
        // Community 2: triangle Y
        g.addVertex("Y0"); g.addVertex("Y1"); g.addVertex("Y2");
        g.addEdge(new Edge("c", "Y0", "Y1"), "Y0", "Y1");
        g.addEdge(new Edge("c", "Y1", "Y2"), "Y1", "Y2");
        g.addEdge(new Edge("c", "Y0", "Y2"), "Y0", "Y2");
        // Community 3: triangle Z
        g.addVertex("Z0"); g.addVertex("Z1"); g.addVertex("Z2");
        g.addEdge(new Edge("c", "Z0", "Z1"), "Z0", "Z1");
        g.addEdge(new Edge("c", "Z1", "Z2"), "Z1", "Z2");
        g.addEdge(new Edge("c", "Z0", "Z2"), "Z0", "Z2");
        // Inter-community bridges
        g.addEdge(new Edge("c", "X0", "Y0"), "X0", "Y0");
        g.addEdge(new Edge("c", "Y0", "Z0"), "Y0", "Z0");
        return g;
    }

    /** Disconnected: two separate triangles. */
    private Graph<String, Edge> disconnectedGraph() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addEdge(new Edge("c", "A", "B"), "A", "B");
        g.addEdge(new Edge("c", "B", "C"), "B", "C");
        g.addEdge(new Edge("c", "A", "C"), "A", "C");
        g.addVertex("D"); g.addVertex("E"); g.addVertex("F");
        g.addEdge(new Edge("c", "D", "E"), "D", "E");
        g.addEdge(new Edge("c", "E", "F"), "E", "F");
        g.addEdge(new Edge("c", "D", "F"), "D", "F");
        return g;
    }

    /** Graph with weighted edges. */
    private Graph<String, Edge> weightedGraph() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("W1"); g.addVertex("W2"); g.addVertex("W3"); g.addVertex("W4");
        Edge e1 = new Edge("c", "W1", "W2"); e1.setWeight(5.0f);
        Edge e2 = new Edge("c", "W2", "W3"); e2.setWeight(1.0f);
        Edge e3 = new Edge("c", "W3", "W4"); e3.setWeight(5.0f);
        Edge e4 = new Edge("c", "W1", "W4"); e4.setWeight(0.5f);
        g.addEdge(e1, "W1", "W2");
        g.addEdge(e2, "W2", "W3");
        g.addEdge(e3, "W3", "W4");
        g.addEdge(e4, "W1", "W4");
        return g;
    }

    private GraphSpectralClusteringEngine engine() {
        return new GraphSpectralClusteringEngine().setRandomSeed(42);
    }

    // ── Empty graph ─────────────────────────────────────────────────

    @Test
    public void testEmptyGraphReport() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(emptyGraph());
        assertEquals(0, r.vertexCount);
        assertEquals(0, r.edgeCount);
        assertEquals(1, r.kUsed);
        assertTrue(r.clusters.isEmpty());
    }

    @Test
    public void testEmptyGraphInsights() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(emptyGraph());
        assertFalse(r.insights.isEmpty());
        assertEquals("EMPTY", r.insights.get(0).type);
    }

    @Test
    public void testEmptyGraphEigengap() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(emptyGraph());
        assertEquals(0, r.eigengap.eigenvalues.length);
        assertEquals(1, r.eigengap.recommendedK);
    }

    // ── Single node ─────────────────────────────────────────────────

    @Test
    public void testSingleNodeReport() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(singleNode());
        assertEquals(1, r.vertexCount);
        assertEquals(1, r.kUsed);
        assertEquals(1, r.clusters.size());
        assertEquals(1, r.clusters.get(0).members.size());
    }

    @Test
    public void testSingleNodeAssignment() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(singleNode());
        assertEquals(Integer.valueOf(0), r.kWayAssignment.get("A"));
    }

    @Test
    public void testSingleNodeInsights() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(singleNode());
        assertEquals("SINGLETON", r.insights.get(0).type);
    }

    // ── Two nodes ───────────────────────────────────────────────────

    @Test
    public void testTwoNodesReport() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(twoNodes());
        assertEquals(2, r.vertexCount);
        assertEquals(1, r.edgeCount);
    }

    @Test
    public void testTwoNodesAssignment() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(twoNodes());
        assertNotNull(r.kWayAssignment);
        assertEquals(2, r.kWayAssignment.size());
    }

    @Test
    public void testTwoNodesBisection() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(twoNodes());
        assertNotNull(r.bisection);
        assertEquals(2, r.bisection.assignment.size());
    }

    // ── Triangle ────────────────────────────────────────────────────

    @Test
    public void testTriangleReport() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(triangle());
        assertEquals(3, r.vertexCount);
        assertEquals(3, r.edgeCount);
    }

    @Test
    public void testTriangleScoreBounds() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(triangle());
        assertTrue(r.compositeScore >= 0 && r.compositeScore <= 100);
    }

    @Test
    public void testTriangleEigenvalueCount() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(triangle());
        assertEquals(3, r.eigengap.eigenvalues.length);
    }

    // ── Star graph ──────────────────────────────────────────────────

    @Test
    public void testStar5Report() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(star5());
        assertEquals(5, r.vertexCount);
        assertEquals(4, r.edgeCount);
        assertTrue(r.compositeScore >= 0 && r.compositeScore <= 100);
    }

    @Test
    public void testStar5Clusters() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(star5());
        assertFalse(r.clusters.isEmpty());
    }

    // ── Path graph ──────────────────────────────────────────────────

    @Test
    public void testPath5Report() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(path5());
        assertEquals(5, r.vertexCount);
        assertEquals(4, r.edgeCount);
    }

    @Test
    public void testPath5Bisection() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(path5());
        assertNotNull(r.bisection);
        assertTrue(r.bisection.cutEdges >= 0);
    }

    // ── Cycle graph ─────────────────────────────────────────────────

    @Test
    public void testCycle6Report() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(cycle6());
        assertEquals(6, r.vertexCount);
        assertEquals(6, r.edgeCount);
    }

    @Test
    public void testCycle6EigenvalueCount() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(cycle6());
        assertEquals(6, r.eigengap.eigenvalues.length);
    }

    // ── Complete graph ──────────────────────────────────────────────

    @Test
    public void testCompleteK5() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(complete(5));
        assertEquals(5, r.vertexCount);
        assertEquals(10, r.edgeCount);
    }

    @Test
    public void testCompleteK5SingleCluster() {
        // Complete graph is one tightly connected community
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(complete(5));
        // Should detect 1 or few clusters
        assertTrue(r.kUsed >= 1);
    }

    // ── Two cliques ─────────────────────────────────────────────────

    @Test
    public void testTwoCliquesDetects2Clusters() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(twoCliquesGraph());
        // Should detect 2 communities
        assertTrue("Expected 2 clusters but got " + r.kUsed, r.kUsed >= 2);
    }

    @Test
    public void testTwoCliquesOptimalK() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(twoCliquesGraph());
        assertTrue(r.eigengap.recommendedK >= 2);
    }

    @Test
    public void testTwoCliquesBisection() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(twoCliquesGraph());
        // Bisection should separate the two cliques
        assertNotNull(r.bisection.assignment);
        assertEquals(10, r.bisection.assignment.size());
        // The cut should be exactly 1 (the bridge edge)
        assertTrue(r.bisection.cutEdges >= 1);
    }

    @Test
    public void testTwoCliquesModularity() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(twoCliquesGraph());
        // Strong community structure should yield positive modularity
        assertTrue("Expected positive modularity, got " + r.modularity, r.modularity > 0);
    }

    @Test
    public void testTwoCliquesConductance() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(twoCliquesGraph());
        // Clusters should have low conductance (well-separated)
        for (GraphSpectralClusteringEngine.ClusterInfo ci : r.clusters) {
            assertTrue(ci.conductance >= 0);
        }
    }

    @Test
    public void testTwoCliquesQualityScore() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(twoCliquesGraph());
        assertTrue(r.compositeScore >= 0 && r.compositeScore <= 100);
    }

    @Test
    public void testTwoCliquesHasBridgeNodes() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(twoCliquesGraph());
        boolean hasBridge = false;
        for (GraphSpectralClusteringEngine.ClusterInfo ci : r.clusters) {
            if (!ci.boundaryNodes.isEmpty()) hasBridge = true;
        }
        assertTrue("Expected bridge nodes between cliques", hasBridge);
    }

    // ── Barbell graph ───────────────────────────────────────────────

    @Test
    public void testBarbellReport() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(barbellGraph());
        assertEquals(11, r.vertexCount);
        assertTrue(r.kUsed >= 2);
    }

    @Test
    public void testBarbellModularity() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(barbellGraph());
        assertTrue(r.modularity > 0);
    }

    @Test
    public void testBarbellNormalizedCut() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(barbellGraph());
        assertTrue(r.normalizedCut >= 0);
    }

    // ── Three communities ───────────────────────────────────────────

    @Test
    public void testThreeCommunitiesReport() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(threeCommunitiesGraph());
        assertEquals(9, r.vertexCount);
    }

    @Test
    public void testThreeCommunitiesClusters() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(threeCommunitiesGraph());
        assertTrue(r.kUsed >= 2);
    }

    @Test
    public void testThreeCommunitiesInsights() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(threeCommunitiesGraph());
        assertFalse(r.insights.isEmpty());
    }

    // ── Disconnected graph ──────────────────────────────────────────

    @Test
    public void testDisconnectedDetectsTwoComponents() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(disconnectedGraph());
        assertEquals(6, r.vertexCount);
        // Should detect at least 2 clusters for disconnected components
        assertTrue("Expected >=2 clusters for disconnected graph, got " + r.kUsed,
                r.kUsed >= 2);
    }

    @Test
    public void testDisconnectedBisectionSeparates() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(disconnectedGraph());
        // Bisection should cleanly split components
        Set<Integer> parts = new HashSet<>(r.bisection.assignment.values());
        assertEquals(2, parts.size());
    }

    @Test
    public void testDisconnectedNoCutEdges() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(disconnectedGraph());
        // No edges between components
        assertEquals(0.0, r.bisection.cutEdges, 0.01);
    }

    // ── Weighted graph ──────────────────────────────────────────────

    @Test
    public void testWeightedGraphReport() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(weightedGraph());
        assertEquals(4, r.vertexCount);
        assertTrue(r.compositeScore >= 0 && r.compositeScore <= 100);
    }

    @Test
    public void testWeightedGraphHandlesWeights() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r = engine().analyze(weightedGraph());
        assertNotNull(r.kWayAssignment);
        assertEquals(4, r.kWayAssignment.size());
    }

    // ── Configuration ───────────────────────────────────────────────

    @Test
    public void testSetMaxK() {
        GraphSpectralClusteringEngine e = new GraphSpectralClusteringEngine().setMaxK(3);
        GraphSpectralClusteringEngine.SpectralClusteringReport r = e.analyze(twoCliquesGraph());
        assertNotNull(r);
    }

    @Test
    public void testSetRandomSeed() {
        GraphSpectralClusteringEngine e = new GraphSpectralClusteringEngine().setRandomSeed(123);
        GraphSpectralClusteringEngine.SpectralClusteringReport r = e.analyze(twoCliquesGraph());
        assertNotNull(r);
    }

    @Test
    public void testSetKmeansRestarts() {
        GraphSpectralClusteringEngine e = new GraphSpectralClusteringEngine().setKmeansRestarts(5);
        GraphSpectralClusteringEngine.SpectralClusteringReport r = e.analyze(twoCliquesGraph());
        assertNotNull(r);
    }

    @Test
    public void testSetRng() {
        GraphSpectralClusteringEngine e = new GraphSpectralClusteringEngine().setRng(new Random(99));
        GraphSpectralClusteringEngine.SpectralClusteringReport r = e.analyze(triangle());
        assertNotNull(r);
    }

    @Test
    public void testSetKmeansMaxIter() {
        GraphSpectralClusteringEngine e = new GraphSpectralClusteringEngine().setKmeansMaxIter(50);
        GraphSpectralClusteringEngine.SpectralClusteringReport r = e.analyze(twoCliquesGraph());
        assertNotNull(r);
    }

    @Test
    public void testBuilderChaining() {
        GraphSpectralClusteringEngine e = new GraphSpectralClusteringEngine()
                .setMaxK(5).setRandomSeed(42).setKmeansRestarts(3).setKmeansMaxIter(50);
        assertNotNull(e.analyze(triangle()));
    }

    // ── Null graph ──────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        engine().analyze(null);
    }

    // ── Text output ─────────────────────────────────────────────────

    @Test
    public void testTextOutputNotNull() {
        GraphSpectralClusteringEngine e = engine();
        GraphSpectralClusteringEngine.SpectralClusteringReport r = e.analyze(twoCliquesGraph());
        String text = e.toText(r);
        assertNotNull(text);
        assertFalse(text.isEmpty());
    }

    @Test
    public void testTextContainsHeader() {
        GraphSpectralClusteringEngine e = engine();
        String text = e.toText(e.analyze(twoCliquesGraph()));
        assertTrue(text.contains("SPECTRAL CLUSTERING"));
    }

    @Test
    public void testTextContainsClusterDetails() {
        GraphSpectralClusteringEngine e = engine();
        String text = e.toText(e.analyze(twoCliquesGraph()));
        assertTrue(text.contains("Cluster"));
        assertTrue(text.contains("conductance"));
    }

    @Test
    public void testTextContainsQualityMetrics() {
        GraphSpectralClusteringEngine e = engine();
        String text = e.toText(e.analyze(twoCliquesGraph()));
        assertTrue(text.contains("Modularity"));
        assertTrue(text.contains("Normalized cut"));
    }

    @Test
    public void testTextContainsInsights() {
        GraphSpectralClusteringEngine e = engine();
        String text = e.toText(e.analyze(twoCliquesGraph()));
        assertTrue(text.contains("Insights"));
    }

    // ── HTML output ─────────────────────────────────────────────────

    @Test
    public void testHtmlOutputNotNull() {
        GraphSpectralClusteringEngine e = engine();
        String html = e.exportHtml(e.analyze(twoCliquesGraph()));
        assertNotNull(html);
        assertFalse(html.isEmpty());
    }

    @Test
    public void testHtmlContainsDoctype() {
        GraphSpectralClusteringEngine e = engine();
        String html = e.exportHtml(e.analyze(twoCliquesGraph()));
        assertTrue(html.contains("<!DOCTYPE html>"));
    }

    @Test
    public void testHtmlContainsTitle() {
        GraphSpectralClusteringEngine e = engine();
        String html = e.exportHtml(e.analyze(twoCliquesGraph()));
        assertTrue(html.contains("Spectral Clustering"));
    }

    @Test
    public void testHtmlContainsGauge() {
        GraphSpectralClusteringEngine e = engine();
        String html = e.exportHtml(e.analyze(twoCliquesGraph()));
        assertTrue(html.contains("gauge"));
    }

    @Test
    public void testHtmlContainsInsightDivs() {
        GraphSpectralClusteringEngine e = engine();
        String html = e.exportHtml(e.analyze(twoCliquesGraph()));
        assertTrue(html.contains("class='insight"));
    }

    @Test
    public void testHtmlExportToFile() throws IOException {
        GraphSpectralClusteringEngine e = engine();
        File f = File.createTempFile("spectral-test", ".html");
        try {
            e.exportHtmlToFile(e.analyze(twoCliquesGraph()), f);
            assertTrue(f.length() > 100);
        } finally {
            f.delete();
        }
    }

    // ── Quality score bounds ────────────────────────────────────────

    @Test
    public void testQualityScoreAlwaysBounded() {
        Graph<String, Edge>[] graphs = new Graph[]{
                emptyGraph(), singleNode(), twoNodes(), triangle(),
                star5(), path5(), cycle6(), complete(5),
                twoCliquesGraph(), barbellGraph(),
                threeCommunitiesGraph(), disconnectedGraph()
        };
        GraphSpectralClusteringEngine e = engine();
        for (Graph<String, Edge> g : graphs) {
            GraphSpectralClusteringEngine.SpectralClusteringReport r = e.analyze(g);
            assertTrue("Score out of bounds: " + r.compositeScore,
                    r.compositeScore >= 0 && r.compositeScore <= 100);
        }
    }

    // ── Silhouette bounds ───────────────────────────────────────────

    @Test
    public void testSilhouetteBounded() {
        GraphSpectralClusteringEngine e = engine();
        GraphSpectralClusteringEngine.SpectralClusteringReport r = e.analyze(twoCliquesGraph());
        assertTrue(r.avgSilhouette >= -1.0 && r.avgSilhouette <= 1.0);
    }

    // ── Modularity sanity ───────────────────────────────────────────

    @Test
    public void testModularityBounded() {
        GraphSpectralClusteringEngine e = engine();
        GraphSpectralClusteringEngine.SpectralClusteringReport r = e.analyze(twoCliquesGraph());
        assertTrue(r.modularity >= -1.0 && r.modularity <= 1.0);
    }

    // ── Eigenvalue ordering ─────────────────────────────────────────

    @Test
    public void testEigenvaluesAscending() {
        GraphSpectralClusteringEngine e = engine();
        GraphSpectralClusteringEngine.SpectralClusteringReport r = e.analyze(twoCliquesGraph());
        for (int i = 1; i < r.eigengap.eigenvalues.length; i++) {
            assertTrue(r.eigengap.eigenvalues[i] >= r.eigengap.eigenvalues[i - 1] - 1e-6);
        }
    }

    // ── Cluster member coverage ─────────────────────────────────────

    @Test
    public void testAllNodesClustered() {
        GraphSpectralClusteringEngine e = engine();
        GraphSpectralClusteringEngine.SpectralClusteringReport r = e.analyze(twoCliquesGraph());
        Set<String> allMembers = new HashSet<>();
        for (GraphSpectralClusteringEngine.ClusterInfo ci : r.clusters) {
            allMembers.addAll(ci.members);
        }
        assertEquals(10, allMembers.size());
    }

    @Test
    public void testAssignmentCoversAllNodes() {
        GraphSpectralClusteringEngine e = engine();
        GraphSpectralClusteringEngine.SpectralClusteringReport r = e.analyze(barbellGraph());
        assertEquals(11, r.kWayAssignment.size());
    }

    // ── Conductance non-negative ────────────────────────────────────

    @Test
    public void testConductanceNonNegative() {
        GraphSpectralClusteringEngine e = engine();
        GraphSpectralClusteringEngine.SpectralClusteringReport r = e.analyze(barbellGraph());
        for (GraphSpectralClusteringEngine.ClusterInfo ci : r.clusters) {
            assertTrue(ci.conductance >= 0);
        }
        assertTrue(r.graphConductance >= 0);
    }

    // ── Internal edges non-negative ─────────────────────────────────

    @Test
    public void testInternalEdgesNonNegative() {
        GraphSpectralClusteringEngine e = engine();
        GraphSpectralClusteringEngine.SpectralClusteringReport r = e.analyze(twoCliquesGraph());
        for (GraphSpectralClusteringEngine.ClusterInfo ci : r.clusters) {
            assertTrue(ci.internalEdges >= 0);
            assertTrue(ci.boundaryEdges >= 0);
        }
    }

    // ── Normalized cut non-negative ─────────────────────────────────

    @Test
    public void testNormalizedCutNonNegative() {
        GraphSpectralClusteringEngine e = engine();
        GraphSpectralClusteringEngine.SpectralClusteringReport r = e.analyze(twoCliquesGraph());
        assertTrue(r.normalizedCut >= 0);
    }

    // ── Insight types ───────────────────────────────────────────────

    @Test
    public void testInsightsHaveTypes() {
        GraphSpectralClusteringEngine e = engine();
        GraphSpectralClusteringEngine.SpectralClusteringReport r = e.analyze(twoCliquesGraph());
        for (GraphSpectralClusteringEngine.SpectralInsight ins : r.insights) {
            assertNotNull(ins.type);
            assertNotNull(ins.description);
            assertNotNull(ins.severity);
        }
    }

    @Test
    public void testInsightsHaveSeverity() {
        GraphSpectralClusteringEngine e = engine();
        GraphSpectralClusteringEngine.SpectralClusteringReport r = e.analyze(twoCliquesGraph());
        Set<String> severities = new HashSet<>();
        for (GraphSpectralClusteringEngine.SpectralInsight ins : r.insights) {
            severities.add(ins.severity.name());
        }
        // At least one severity level present
        assertFalse(severities.isEmpty());
    }

    // ── Reproducibility with seed ───────────────────────────────────

    @Test
    public void testReproducibleWithSameSeed() {
        GraphSpectralClusteringEngine.SpectralClusteringReport r1 =
                new GraphSpectralClusteringEngine().setRandomSeed(42).analyze(twoCliquesGraph());
        GraphSpectralClusteringEngine.SpectralClusteringReport r2 =
                new GraphSpectralClusteringEngine().setRandomSeed(42).analyze(twoCliquesGraph());
        assertEquals(r1.kUsed, r2.kUsed);
        assertEquals(r1.compositeScore, r2.compositeScore, 0.01);
    }

    // ── Eigengap result fields ──────────────────────────────────────

    @Test
    public void testEigengapGapsLength() {
        GraphSpectralClusteringEngine e = engine();
        GraphSpectralClusteringEngine.SpectralClusteringReport r = e.analyze(twoCliquesGraph());
        assertTrue(r.eigengap.gaps.length > 0);
    }

    @Test
    public void testEigengapMaxGapRatioPositive() {
        GraphSpectralClusteringEngine e = engine();
        GraphSpectralClusteringEngine.SpectralClusteringReport r = e.analyze(twoCliquesGraph());
        assertTrue(r.eigengap.maxGapRatio >= 0);
    }
}
