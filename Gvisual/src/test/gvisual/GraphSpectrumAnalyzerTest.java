package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphSpectrumAnalyzer}.
 *
 * <p>Verifies the fix for issue #169: the previous hand-rolled QR routine
 * threw {@code ArrayIndexOutOfBoundsException} on every non-trivial graph
 * and returned mathematically wrong eigenvalues even when it did not crash.
 * After delegating to Colt's {@code EigenvalueDecomposition}, the analyzer
 * matches closed-form spectra for canonical graph families.</p>
 */
public class GraphSpectrumAnalyzerTest {

    private static final double TOL = 1e-6;

    private static Edge addEdge(Graph<String, Edge> g, String u, String v) {
        if (!g.containsVertex(u)) g.addVertex(u);
        if (!g.containsVertex(v)) g.addVertex(v);
        Edge e = new Edge("f", u, v);
        e.setWeight(1f);
        g.addEdge(e, u, v);
        return e;
    }

    private static Graph<String, Edge> emptyGraph() {
        return new UndirectedSparseGraph<String, Edge>();
    }

    private static Graph<String, Edge> completeGraph(int n) {
        Graph<String, Edge> g = emptyGraph();
        for (int i = 0; i < n; i++) g.addVertex("v" + i);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                addEdge(g, "v" + i, "v" + j);
            }
        }
        return g;
    }

    /** Path graph on n vertices: v0 - v1 - ... - v(n-1). */
    private static Graph<String, Edge> pathGraph(int n) {
        Graph<String, Edge> g = emptyGraph();
        for (int i = 0; i < n; i++) g.addVertex("v" + i);
        for (int i = 0; i + 1 < n; i++) addEdge(g, "v" + i, "v" + (i + 1));
        return g;
    }

    /** Star graph K_{1, n-1}: hub v0 connected to v1..v(n-1). */
    private static Graph<String, Edge> starGraph(int n) {
        Graph<String, Edge> g = emptyGraph();
        g.addVertex("hub");
        for (int i = 1; i < n; i++) addEdge(g, "hub", "leaf" + i);
        return g;
    }

    // ─── Issue #169 reproductions: these used to throw ──────────────

    /** Triangle K_3. Old code: ArrayIndexOutOfBoundsException at line 342. */
    @Test
    public void triangleSpectrumDoesNotCrash() {
        GraphSpectrumAnalyzer a = new GraphSpectrumAnalyzer(completeGraph(3));
        a.compute();
        double[] eig = a.getAdjacencyEigenvalues();
        assertEquals(3, eig.length);
        // K_3 adjacency spectrum: {2, -1, -1} (descending).
        assertEquals(2.0, eig[0], TOL);
        assertEquals(-1.0, eig[1], TOL);
        assertEquals(-1.0, eig[2], TOL);
    }

    @Test
    public void completeK4Spectrum() {
        GraphSpectrumAnalyzer a = new GraphSpectrumAnalyzer(completeGraph(4));
        a.compute();
        double[] eig = a.getAdjacencyEigenvalues();
        // K_n adjacency spectrum: {n-1, -1, -1, ..., -1}.
        assertEquals(4, eig.length);
        assertEquals(3.0, eig[0], TOL);
        for (int i = 1; i < 4; i++) assertEquals(-1.0, eig[i], TOL);
    }

    @Test
    public void path3Spectrum() {
        GraphSpectrumAnalyzer a = new GraphSpectrumAnalyzer(pathGraph(3));
        a.compute();
        double[] eig = a.getAdjacencyEigenvalues();
        // P_3 spectrum: { sqrt(2), 0, -sqrt(2) }.
        assertEquals(3, eig.length);
        assertEquals(Math.sqrt(2.0), eig[0], TOL);
        assertEquals(0.0, eig[1], TOL);
        assertEquals(-Math.sqrt(2.0), eig[2], TOL);
    }

    @Test
    public void star4Spectrum() {
        GraphSpectrumAnalyzer a = new GraphSpectrumAnalyzer(starGraph(4));
        a.compute();
        double[] eig = a.getAdjacencyEigenvalues();
        // K_{1,3} spectrum: { sqrt(3), 0, 0, -sqrt(3) }.
        assertEquals(4, eig.length);
        assertEquals(Math.sqrt(3.0), eig[0], TOL);
        assertEquals(0.0, eig[1], TOL);
        assertEquals(0.0, eig[2], TOL);
        assertEquals(-Math.sqrt(3.0), eig[3], TOL);
    }

    // ─── Derived metrics ────────────────────────────────────────────

    @Test
    public void spectralRadiusMatchesLargestEigenvalue() {
        GraphSpectrumAnalyzer a = new GraphSpectrumAnalyzer(completeGraph(5));
        a.compute();
        // K_5 spectral radius = 4.
        assertEquals(4.0, a.getSpectralRadius(), TOL);
    }

    @Test
    public void algebraicConnectivityOfDisconnectedGraphIsZero() {
        // Two disjoint edges: {a-b, c-d}.
        Graph<String, Edge> g = emptyGraph();
        addEdge(g, "a", "b");
        addEdge(g, "c", "d");
        GraphSpectrumAnalyzer a = new GraphSpectrumAnalyzer(g);
        a.compute();
        // Fiedler value (second-smallest Laplacian eigenvalue) is 0 for a
        // disconnected graph.
        assertEquals(0.0, a.getAlgebraicConnectivity(), TOL);
        assertEquals(2, a.getComponentCount());
    }

    @Test
    public void algebraicConnectivityOfTriangleIsThree() {
        GraphSpectrumAnalyzer a = new GraphSpectrumAnalyzer(completeGraph(3));
        a.compute();
        // L(K_3) spectrum = {0, 3, 3}. Fiedler value = 3.
        assertEquals(3.0, a.getAlgebraicConnectivity(), TOL);
        assertEquals(1, a.getComponentCount());
    }

    @Test
    public void graphEnergyOfTriangle() {
        GraphSpectrumAnalyzer a = new GraphSpectrumAnalyzer(completeGraph(3));
        a.compute();
        // Energy(K_3) = |2| + |-1| + |-1| = 4.
        assertEquals(4.0, a.getGraphEnergy(), TOL);
    }

    // ─── Edge cases ─────────────────────────────────────────────────

    @Test
    public void emptyGraphIsHandled() {
        GraphSpectrumAnalyzer a = new GraphSpectrumAnalyzer(emptyGraph());
        a.compute();
        assertEquals(0, a.getAdjacencyEigenvalues().length);
        assertEquals(0, a.getLaplacianEigenvalues().length);
        assertEquals(0.0, a.getSpectralRadius(), TOL);
        assertEquals(0, a.getComponentCount());
    }

    @Test
    public void singleVertexGraphIsHandled() {
        Graph<String, Edge> g = emptyGraph();
        g.addVertex("only");
        GraphSpectrumAnalyzer a = new GraphSpectrumAnalyzer(g);
        a.compute();
        // Single vertex: adjacency = [0], Laplacian = [0].
        assertArrayEquals(new double[]{0.0}, a.getAdjacencyEigenvalues(), TOL);
        assertArrayEquals(new double[]{0.0}, a.getLaplacianEigenvalues(), TOL);
        assertEquals(1, a.getComponentCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullGraphRejected() {
        new GraphSpectrumAnalyzer(null);
    }

    @Test(expected = IllegalStateException.class)
    public void queryBeforeComputeRejected() {
        new GraphSpectrumAnalyzer(completeGraph(3)).getSpectralRadius();
    }
}
