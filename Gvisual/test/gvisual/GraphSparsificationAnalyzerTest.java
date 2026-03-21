package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class GraphSparsificationAnalyzerTest {

    private Graph<String, Edge> triangle, path, star, complete5, disconnected, single, empty, weighted;

    @Before
    public void setUp() {
        triangle = new UndirectedSparseGraph<String, Edge>();
        triangle.addVertex("A"); triangle.addVertex("B"); triangle.addVertex("C");
        triangle.addEdge(new Edge("f", "A", "B"), "A", "B");
        triangle.addEdge(new Edge("f", "B", "C"), "B", "C");
        triangle.addEdge(new Edge("f", "A", "C"), "A", "C");

        path = new UndirectedSparseGraph<String, Edge>();
        for (String v : new String[]{"A","B","C","D"}) path.addVertex(v);
        path.addEdge(new Edge("f","A","B"),"A","B");
        path.addEdge(new Edge("f","B","C"),"B","C");
        path.addEdge(new Edge("f","C","D"),"C","D");

        star = new UndirectedSparseGraph<String, Edge>();
        for (String v : new String[]{"A","B","C","D","E"}) star.addVertex(v);
        star.addEdge(new Edge("f","A","B"),"A","B");
        star.addEdge(new Edge("f","A","C"),"A","C");
        star.addEdge(new Edge("f","A","D"),"A","D");
        star.addEdge(new Edge("f","A","E"),"A","E");

        complete5 = new UndirectedSparseGraph<String, Edge>();
        String[] k5 = {"A","B","C","D","E"};
        for (String v : k5) complete5.addVertex(v);
        for (int i = 0; i < k5.length; i++)
            for (int j = i+1; j < k5.length; j++) {
                Edge e = new Edge("f", k5[i], k5[j]); e.setWeight((float)(i+j));
                complete5.addEdge(e, k5[i], k5[j]);
            }

        disconnected = new UndirectedSparseGraph<String, Edge>();
        for (String v : new String[]{"A","B","C","D"}) disconnected.addVertex(v);
        disconnected.addEdge(new Edge("f","A","B"),"A","B");
        disconnected.addEdge(new Edge("f","C","D"),"C","D");

        single = new UndirectedSparseGraph<String, Edge>();
        single.addVertex("A");

        empty = new UndirectedSparseGraph<String, Edge>();

        weighted = new UndirectedSparseGraph<String, Edge>();
        for (String v : new String[]{"A","B","C","D"}) weighted.addVertex(v);
        edge e1=new Edge("f","A","B"); e1.setWeight(1f);
        edge e2=new Edge("f","B","C"); e2.setWeight(5f);
        edge e3=new Edge("f","C","D"); e3.setWeight(3f);
        edge e4=new Edge("f","A","D"); e4.setWeight(2f);
        edge e5=new Edge("f","A","C"); e5.setWeight(4f);
        weighted.addEdge(e1,"A","B"); weighted.addEdge(e2,"B","C");
        weighted.addEdge(e3,"C","D"); weighted.addEdge(e4,"A","D");
        weighted.addEdge(e5,"A","C");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraph() { new GraphSparsificationAnalyzer(null); }

    @Test public void testEmptyGraphOk() { assertNotNull(new GraphSparsificationAnalyzer(empty)); }

    // Bridges
    @Test public void testBridgesPath() { assertEquals(3, new GraphSparsificationAnalyzer(path).findBridges().size()); }
    @Test public void testBridgesTriangle() { assertEquals(0, new GraphSparsificationAnalyzer(triangle).findBridges().size()); }
    @Test public void testBridgesStar() { assertEquals(4, new GraphSparsificationAnalyzer(star).findBridges().size()); }
    @Test public void testBridgesEmpty() { assertEquals(0, new GraphSparsificationAnalyzer(empty).findBridges().size()); }

    // Edge Importance
    @Test public void testImportancePath() {
        Map<edge,Double> s = new GraphSparsificationAnalyzer(path).scoreEdgeImportance();
        assertEquals(3, s.size());
        for (double v : s.values()) { assertTrue(v >= 0); assertTrue(v <= 1.0); }
    }
    @Test public void testImportanceBridgesHigh() {
        for (double s : new GraphSparsificationAnalyzer(path).scoreEdgeImportance().values()) assertTrue(s >= 0.5);
    }
    @Test public void testImportanceEmpty() { assertEquals(0, new GraphSparsificationAnalyzer(empty).scoreEdgeImportance().size()); }
    @Test public void testImportanceK5() { assertEquals(10, new GraphSparsificationAnalyzer(complete5).scoreEdgeImportance().size()); }

    // Spanning Tree
    @Test public void testSTTriangle() { Graph<String, Edge> st = new GraphSparsificationAnalyzer(triangle).spanningTreeSparsify(); assertEquals(3, st.getVertexCount()); assertEquals(2, st.getEdgeCount()); }
    @Test public void testSTK5() { assertEquals(4, new GraphSparsificationAnalyzer(complete5).spanningTreeSparsify().getEdgeCount()); }
    @Test public void testSTDisconnected() { assertEquals(2, new GraphSparsificationAnalyzer(disconnected).spanningTreeSparsify().getEdgeCount()); }
    @Test public void testSTSingle() { assertEquals(0, new GraphSparsificationAnalyzer(single).spanningTreeSparsify().getEdgeCount()); }

    // Random
    @Test public void testRandAll() { assertEquals(10, new GraphSparsificationAnalyzer(complete5).randomSparsify(1.0, 42).getEdgeCount()); }
    @Test public void testRandNone() { Graph<String, Edge> s = new GraphSparsificationAnalyzer(complete5).randomSparsify(0.0, 42); assertEquals(0, s.getEdgeCount()); assertEquals(5, s.getVertexCount()); }
    @Test public void testRandDeterministic() { GraphSparsificationAnalyzer a = new GraphSparsificationAnalyzer(complete5); assertEquals(a.randomSparsify(0.5,42).getEdgeCount(), a.randomSparsify(0.5,42).getEdgeCount()); }
    @Test(expected = IllegalArgumentException.class) public void testRandInvalid() { new GraphSparsificationAnalyzer(complete5).randomSparsify(1.5, 42); }

    // Threshold
    @Test public void testThreshold3() { assertEquals(3, new GraphSparsificationAnalyzer(weighted).thresholdSparsify(3f).getEdgeCount()); }
    @Test public void testThresholdHigh() { assertEquals(0, new GraphSparsificationAnalyzer(weighted).thresholdSparsify(100f).getEdgeCount()); }
    @Test public void testThresholdZero() { assertEquals(5, new GraphSparsificationAnalyzer(weighted).thresholdSparsify(0f).getEdgeCount()); }

    // Local
    @Test public void testLocalK1() { Graph<String, Edge> s = new GraphSparsificationAnalyzer(weighted).localSparsify(1); assertTrue(s.getEdgeCount() >= 1 && s.getEdgeCount() <= 4); }
    @Test public void testLocalHighK() { assertEquals(5, new GraphSparsificationAnalyzer(weighted).localSparsify(100).getEdgeCount()); }
    @Test(expected = IllegalArgumentException.class) public void testLocalInvalid() { new GraphSparsificationAnalyzer(weighted).localSparsify(0); }

    // Importance Sparsify
    @Test public void testImpAll() { assertEquals(10, new GraphSparsificationAnalyzer(complete5).importanceSparsify(1.0).getEdgeCount()); }
    @Test public void testImpHalf() { assertEquals(5, new GraphSparsificationAnalyzer(complete5).importanceSparsify(0.5).getEdgeCount()); }
    @Test(expected = IllegalArgumentException.class) public void testImpInvalid() { new GraphSparsificationAnalyzer(complete5).importanceSparsify(-0.1); }

    // Quality
    @Test public void testQualityST() {
        GraphSparsificationAnalyzer a = new GraphSparsificationAnalyzer(complete5);
        GraphSparsificationAnalyzer.SparsificationQuality q = a.evaluateQuality(a.spanningTreeSparsify());
        assertEquals(5, q.originalVertices); assertEquals(10, q.originalEdges); assertEquals(4, q.sparseEdges);
        assertTrue(q.connectivityPreserved); assertTrue(q.edgeReduction > 0.5);
    }
    @Test public void testQualityBroken() {
        GraphSparsificationAnalyzer a = new GraphSparsificationAnalyzer(path);
        Graph<String, Edge> b = new UndirectedSparseGraph<String, Edge>();
        for (String v : path.getVertices()) b.addVertex(v);
        b.addEdge(new Edge("f","A","B"),"A","B"); b.addEdge(new Edge("f","C","D"),"C","D");
        assertFalse(a.evaluateQuality(b).connectivityPreserved);
    }
    @Test(expected = IllegalArgumentException.class) public void testQualityNull() { new GraphSparsificationAnalyzer(triangle).evaluateQuality(null); }
    @Test public void testQualityGrade() { GraphSparsificationAnalyzer a = new GraphSparsificationAnalyzer(complete5); assertEquals(1, a.evaluateQuality(a.spanningTreeSparsify()).getGrade().length()); }

    // Sparseness
    @Test public void testSparsenessK5() { GraphSparsificationAnalyzer.SparsenessMetrics sm = new GraphSparsificationAnalyzer(complete5).analyzeSparseness(); assertEquals(5, sm.vertices); assertEquals(10, sm.edges); assertEquals(1.0, sm.density, 0.001); assertFalse(sm.isSparse); assertEquals("Very Dense", sm.classification); }
    @Test public void testSparsenessPath() { GraphSparsificationAnalyzer.SparsenessMetrics sm = new GraphSparsificationAnalyzer(path).analyzeSparseness(); assertTrue(sm.isTree); assertEquals("Tree/Forest", sm.classification); assertEquals(3, sm.bridgeCount); }
    @Test public void testSparsnessStar() { GraphSparsificationAnalyzer.SparsenessMetrics sm = new GraphSparsificationAnalyzer(star).analyzeSparseness(); assertTrue(sm.isTree); assertEquals(4, sm.maxDegree); assertEquals(1, sm.minDegree); }
    @Test public void testSparsenessEmpty() { GraphSparsificationAnalyzer.SparsenessMetrics sm = new GraphSparsificationAnalyzer(empty).analyzeSparseness(); assertEquals(0, sm.vertices); assertEquals(0, sm.density, 0.001); }
    @Test public void testSparsnessSingle() { GraphSparsificationAnalyzer.SparsenessMetrics sm = new GraphSparsificationAnalyzer(single).analyzeSparseness(); assertEquals(1, sm.vertices); assertEquals(0, sm.edges); }
    @Test public void testSparsenessDisconn() { assertTrue(new GraphSparsificationAnalyzer(disconnected).analyzeSparseness().isTree); }

    // Compare
    @Test public void testCompareAll() { Map<String,GraphSparsificationAnalyzer.SparsificationQuality> r = new GraphSparsificationAnalyzer(complete5).compareAllMethods(); assertEquals(6, r.size()); assertTrue(r.containsKey("Spanning Tree")); }
    @Test public void testCompareSTConn() { assertTrue(new GraphSparsificationAnalyzer(complete5).compareAllMethods().get("Spanning Tree").connectivityPreserved); }

    // Report
    @Test public void testReport() { String r = new GraphSparsificationAnalyzer(complete5).generateReport(); assertTrue(r.contains("SPARSIFICATION")); assertTrue(r.contains("Spanning Tree")); }
    @Test public void testReportEmpty() { assertNotNull(new GraphSparsificationAnalyzer(empty).generateReport()); }
    @Test public void testReportPath() { assertTrue(new GraphSparsificationAnalyzer(path).generateReport().contains("Tree/Forest")); }
    @Test public void testReportBridges() { assertTrue(new GraphSparsificationAnalyzer(path).generateReport().contains("Bridge Edges")); }

    // Edge cases
    @Test public void testSingleEdge() { Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>(); g.addVertex("A"); g.addVertex("B"); g.addEdge(new Edge("f","A","B"),"A","B"); GraphSparsificationAnalyzer a = new GraphSparsificationAnalyzer(g); assertEquals(1, a.findBridges().size()); assertEquals(1, a.spanningTreeSparsify().getEdgeCount()); }
    @Test public void testWeightedST() { Graph<String, Edge> st = new GraphSparsificationAnalyzer(weighted).spanningTreeSparsify(); assertEquals(3, st.getEdgeCount()); assertEquals(4, st.getVertexCount()); }
    @Test public void testLocalPreservesV() { assertEquals(5, new GraphSparsificationAnalyzer(complete5).localSparsify(2).getVertexCount()); }
    @Test public void testRandPreservesV() { assertEquals(5, new GraphSparsificationAnalyzer(complete5).randomSparsify(0.3, 123).getVertexCount()); }
    @Test public void testQualityDensity() { GraphSparsificationAnalyzer a = new GraphSparsificationAnalyzer(complete5); GraphSparsificationAnalyzer.SparsificationQuality q = a.evaluateQuality(a.spanningTreeSparsify()); assertEquals(1.0, q.originalDensity, 0.001); assertTrue(q.sparseDensity < q.originalDensity); }
    @Test public void testImpZero() { assertEquals(0, new GraphSparsificationAnalyzer(complete5).importanceSparsify(0.0).getEdgeCount()); }
}
