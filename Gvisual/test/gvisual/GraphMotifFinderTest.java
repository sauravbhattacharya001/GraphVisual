package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link GraphMotifFinder}.
 *
 * <p>Covers triangle, star, open-path (P3), and square (C4) detection,
 * the motif census, global and local clustering coefficients, top
 * triangle participants, and degenerate-graph behaviour.</p>
 */
public class GraphMotifFinderTest {

    // ─── Builders ──────────────────────────────────────────────────────

    private static int edgeId = 0;

    private static Graph<String, Edge> build(String[]... edges) {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        for (String[] e : edges) {
            if (!g.containsVertex(e[0])) g.addVertex(e[0]);
            if (!g.containsVertex(e[1])) g.addVertex(e[1]);
            Edge ed = new Edge("link", e[0], e[1]);
            ed.setLabel("e" + (edgeId++));
            g.addEdge(ed, e[0], e[1]);
        }
        return g;
    }

    private static Graph<String, Edge> empty() {
        return new UndirectedSparseGraph<String, Edge>();
    }

    private static Set<Set<String>> asUnorderedSets(List<List<String>> in) {
        Set<Set<String>> out = new HashSet<Set<String>>();
        for (List<String> sub : in) out.add(new HashSet<String>(sub));
        return out;
    }

    // ─── Construction ──────────────────────────────────────────────────

    @Test(expected = NullPointerException.class)
    public void constructorRejectsNullGraph() {
        new GraphMotifFinder(null);
    }

    // ─── Triangles ─────────────────────────────────────────────────────

    @Test
    public void singleTriangleIsDetectedExactlyOnce() {
        Graph<String, Edge> g = build(
                new String[]{"A", "B"},
                new String[]{"B", "C"},
                new String[]{"A", "C"});
        GraphMotifFinder f = new GraphMotifFinder(g);
        f.analyze();

        assertEquals(1, f.getTriangles().size());
        assertEquals(new HashSet<String>(Arrays.asList("A", "B", "C")),
                new HashSet<String>(f.getTriangles().get(0)));
    }

    @Test
    public void k4HasFourTriangles() {
        // Complete graph on 4 vertices => C(4,3) = 4 triangles
        Graph<String, Edge> g = build(
                new String[]{"A", "B"}, new String[]{"A", "C"}, new String[]{"A", "D"},
                new String[]{"B", "C"}, new String[]{"B", "D"}, new String[]{"C", "D"});
        GraphMotifFinder f = new GraphMotifFinder(g);
        f.analyze();
        assertEquals(4, f.getTriangles().size());

        // Each triangle should be sorted (no duplicates as permutations)
        Set<Set<String>> unique = asUnorderedSets(f.getTriangles());
        assertEquals(4, unique.size());
    }

    @Test
    public void pathGraphHasNoTrianglesOrSquares() {
        // A-B-C-D-E (path, no cycles)
        Graph<String, Edge> g = build(
                new String[]{"A", "B"}, new String[]{"B", "C"},
                new String[]{"C", "D"}, new String[]{"D", "E"});
        GraphMotifFinder f = new GraphMotifFinder(g);
        f.analyze();
        assertTrue(f.getTriangles().isEmpty());
        assertTrue(f.getSquares().isEmpty());
    }

    // ─── Stars ─────────────────────────────────────────────────────────

    @Test
    public void starHubWithThreeLeavesIsDetected() {
        Graph<String, Edge> g = build(
                new String[]{"H", "A"}, new String[]{"H", "B"}, new String[]{"H", "C"});
        GraphMotifFinder f = new GraphMotifFinder(g);
        f.analyze();

        assertEquals(1, f.getStars().size());
        assertTrue(f.getStars().containsKey("H"));
        assertEquals(Arrays.asList("A", "B", "C"), f.getStars().get("H"));
    }

    @Test
    public void degreeTwoHubIsNotStar() {
        // Min star degree is 3
        Graph<String, Edge> g = build(
                new String[]{"H", "A"}, new String[]{"H", "B"});
        GraphMotifFinder f = new GraphMotifFinder(g);
        f.analyze();
        assertTrue(f.getStars().isEmpty());
    }

    // ─── Open paths (P3) ───────────────────────────────────────────────

    @Test
    public void singleOpenPathDetected() {
        // A-B-C; A and C not adjacent => one P3 with middle B
        Graph<String, Edge> g = build(
                new String[]{"A", "B"}, new String[]{"B", "C"});
        GraphMotifFinder f = new GraphMotifFinder(g);
        f.analyze();

        assertEquals(1, f.getOpenPaths().size());
        List<String> p = f.getOpenPaths().get(0);
        assertEquals("B", p.get(1)); // middle
        assertEquals(new HashSet<String>(Arrays.asList("A", "C")),
                new HashSet<String>(Arrays.asList(p.get(0), p.get(2))));
    }

    @Test
    public void triangleHasNoOpenPaths() {
        Graph<String, Edge> g = build(
                new String[]{"A", "B"}, new String[]{"B", "C"}, new String[]{"A", "C"});
        GraphMotifFinder f = new GraphMotifFinder(g);
        f.analyze();
        assertTrue("triangle has no open P3", f.getOpenPaths().isEmpty());
    }

    @Test
    public void starProducesC_k_2_OpenPaths() {
        // Star with 4 leaves: middle hub yields C(4,2) = 6 open P3 paths
        Graph<String, Edge> g = build(
                new String[]{"H", "A"}, new String[]{"H", "B"},
                new String[]{"H", "C"}, new String[]{"H", "D"});
        GraphMotifFinder f = new GraphMotifFinder(g);
        f.analyze();
        assertEquals(6, f.getOpenPaths().size());
    }

    // ─── Squares (C4) ──────────────────────────────────────────────────

    @Test
    public void singleSquareDetected() {
        // A-B-C-D-A, no diagonals
        Graph<String, Edge> g = build(
                new String[]{"A", "B"}, new String[]{"B", "C"},
                new String[]{"C", "D"}, new String[]{"D", "A"});
        GraphMotifFinder f = new GraphMotifFinder(g);
        f.analyze();

        assertEquals(1, f.getSquares().size());
        assertEquals(new HashSet<String>(Arrays.asList("A", "B", "C", "D")),
                new HashSet<String>(f.getSquares().get(0)));
    }

    @Test
    public void squareWithDiagonalIsNotCountedAsSquare() {
        // A-B-C-D-A plus diagonal A-C => triangles, but C4 with chord
        // should not be counted (algorithm excludes chorded paths via
        // vNbrs.contains(w) check).
        Graph<String, Edge> g = build(
                new String[]{"A", "B"}, new String[]{"B", "C"},
                new String[]{"C", "D"}, new String[]{"D", "A"},
                new String[]{"A", "C"});
        GraphMotifFinder f = new GraphMotifFinder(g);
        f.analyze();
        // The algorithm explicitly excludes triangles when forming squares,
        // so adding chord A-C should remove the C4 made of {A,B,C,D}.
        // (See findSquares: `if (vNbrs.contains(w)) continue;`)
        assertTrue("chorded C4 should be excluded", f.getSquares().isEmpty());
    }

    // ─── Census ────────────────────────────────────────────────────────

    @Test
    public void censusContainsAllFourMotifCategories() {
        Graph<String, Edge> g = build(
                new String[]{"A", "B"}, new String[]{"B", "C"}, new String[]{"A", "C"});
        GraphMotifFinder f = new GraphMotifFinder(g);
        f.analyze();
        Map<String, Integer> census = f.getCensus();
        assertEquals(4, census.size());
        assertEquals(Integer.valueOf(1), census.get("Triangles"));
        // Star key uses Unicode "≥" - check by prefix match instead
        boolean hasStarKey = false;
        for (String k : census.keySet()) {
            if (k.startsWith("Stars")) { hasStarKey = true; break; }
        }
        assertTrue(hasStarKey);
        assertTrue(census.containsKey("Open Paths (P3)"));
        assertTrue(census.containsKey("Squares (C4)"));
    }

    // ─── Clustering coefficients ───────────────────────────────────────

    @Test
    public void clusteringCoefficientOfTriangleIsOne() {
        Graph<String, Edge> g = build(
                new String[]{"A", "B"}, new String[]{"B", "C"}, new String[]{"A", "C"});
        GraphMotifFinder f = new GraphMotifFinder(g);
        f.analyze();
        assertEquals(1.0, f.getClusteringCoefficient(), 1e-9);
    }

    @Test
    public void clusteringCoefficientOfPathP3IsZero() {
        Graph<String, Edge> g = build(
                new String[]{"A", "B"}, new String[]{"B", "C"});
        GraphMotifFinder f = new GraphMotifFinder(g);
        f.analyze();
        assertEquals(0.0, f.getClusteringCoefficient(), 1e-9);
    }

    @Test
    public void clusteringCoefficientOfEmptyGraphIsZero() {
        GraphMotifFinder f = new GraphMotifFinder(empty());
        f.analyze();
        assertEquals(0.0, f.getClusteringCoefficient(), 1e-9);
    }

    @Test
    public void localClusteringInTriangleIsOneForEachVertex() {
        Graph<String, Edge> g = build(
                new String[]{"A", "B"}, new String[]{"B", "C"}, new String[]{"A", "C"});
        GraphMotifFinder f = new GraphMotifFinder(g);
        f.analyze();
        Map<String, Double> lc = f.getLocalClustering();
        for (String v : Arrays.asList("A", "B", "C")) {
            assertEquals("local clustering of " + v, 1.0, lc.get(v), 1e-9);
        }
    }

    @Test
    public void localClusteringForLowDegreeVerticesIsZero() {
        // A-B-C: A and C have degree 1 -> 0; B has degree 2, no edge between
        // its neighbours -> 0.
        Graph<String, Edge> g = build(
                new String[]{"A", "B"}, new String[]{"B", "C"});
        GraphMotifFinder f = new GraphMotifFinder(g);
        f.analyze();
        Map<String, Double> lc = f.getLocalClustering();
        assertEquals(0.0, lc.get("A"), 1e-9);
        assertEquals(0.0, lc.get("B"), 1e-9);
        assertEquals(0.0, lc.get("C"), 1e-9);
    }

    @Test
    public void localClusteringWorksWhenCalledBeforeAnalyze() {
        // Documented behaviour: builds its own neighbour cache when needed.
        Graph<String, Edge> g = build(
                new String[]{"A", "B"}, new String[]{"B", "C"}, new String[]{"A", "C"});
        GraphMotifFinder f = new GraphMotifFinder(g);
        Map<String, Double> lc = f.getLocalClustering();
        assertEquals(3, lc.size());
        for (Double v : lc.values()) assertEquals(1.0, v, 1e-9);
    }

    // ─── Top triangle participants ─────────────────────────────────────

    @Test
    public void topTriangleNodesRankedCorrectlyForK4() {
        // In K4 each vertex participates in C(3,2)=3 triangles
        Graph<String, Edge> g = build(
                new String[]{"A", "B"}, new String[]{"A", "C"}, new String[]{"A", "D"},
                new String[]{"B", "C"}, new String[]{"B", "D"}, new String[]{"C", "D"});
        GraphMotifFinder f = new GraphMotifFinder(g);
        f.analyze();
        List<Map.Entry<String, Integer>> top = f.getTopTriangleNodes(4);
        assertEquals(4, top.size());
        for (Map.Entry<String, Integer> e : top) {
            assertEquals(Integer.valueOf(3), e.getValue());
        }
    }

    @Test
    public void topTriangleNodesEmptyWhenNoTriangles() {
        Graph<String, Edge> g = build(
                new String[]{"A", "B"}, new String[]{"B", "C"});
        GraphMotifFinder f = new GraphMotifFinder(g);
        f.analyze();
        assertTrue(f.getTopTriangleNodes(5).isEmpty());
    }

    // ─── Empty / degenerate ────────────────────────────────────────────

    @Test
    public void emptyGraphAnalysisIsSafe() {
        GraphMotifFinder f = new GraphMotifFinder(empty());
        f.analyze();
        assertTrue(f.getTriangles().isEmpty());
        assertTrue(f.getStars().isEmpty());
        assertTrue(f.getOpenPaths().isEmpty());
        assertTrue(f.getSquares().isEmpty());
        assertNotNull(f.getCensus());
    }

    @Test
    public void isolatedVerticesProduceNoMotifs() {
        Graph<String, Edge> g = empty();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        GraphMotifFinder f = new GraphMotifFinder(g);
        f.analyze();
        assertTrue(f.getTriangles().isEmpty());
        assertTrue(f.getOpenPaths().isEmpty());
        assertTrue(f.getSquares().isEmpty());
        assertTrue(f.getStars().isEmpty());
    }

    // ─── Report ────────────────────────────────────────────────────────

    @Test
    public void generateReportContainsCensusSection() {
        Graph<String, Edge> g = build(
                new String[]{"A", "B"}, new String[]{"B", "C"}, new String[]{"A", "C"});
        GraphMotifFinder f = new GraphMotifFinder(g);
        f.analyze();
        String rep = f.generateReport();
        assertNotNull(rep);
        assertTrue(rep.contains("GRAPH MOTIF ANALYSIS REPORT"));
        assertTrue(rep.contains("Triangles"));
        assertTrue(rep.contains("Global Clustering Coefficient"));
    }
}
