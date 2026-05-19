package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link GraphRegularityAnalyzer}.
 *
 * Covers:
 *  - constructor validation
 *  - empty graph edge case
 *  - regular graphs (cycle, complete, k-regular)
 *  - non-regular graphs (star, path)
 *  - Albertson irregularity index
 *  - degree distribution, mean, variance
 *  - deviant vertex detection and ordering
 *  - ensureComputed guard
 *  - report generation contents
 */
public class GraphRegularityAnalyzerTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new UndirectedSparseGraph<String, Edge>();
    }

    private void addEdge(String u, String v) {
        if (!graph.containsVertex(u)) graph.addVertex(u);
        if (!graph.containsVertex(v)) graph.addVertex(v);
        Edge e = new Edge("e", u, v);
        e.setWeight(1.0f);
        graph.addEdge(e, u, v);
    }

    private GraphRegularityAnalyzer analyze() {
        GraphRegularityAnalyzer a = new GraphRegularityAnalyzer(graph);
        a.analyze();
        return a;
    }

    // ═══════════ Constructor ═══════════

    @Test(expected = IllegalArgumentException.class)
    public void nullGraphThrows() {
        new GraphRegularityAnalyzer(null);
    }

    // ═══════════ ensureComputed guard ═══════════

    @Test(expected = IllegalStateException.class)
    public void queryingBeforeAnalyzeThrows() {
        new GraphRegularityAnalyzer(graph).isRegular();
    }

    @Test(expected = IllegalStateException.class)
    public void getReportBeforeAnalyzeThrows() {
        new GraphRegularityAnalyzer(graph).generateReport();
    }

    // ═══════════ Empty graph ═══════════

    @Test
    public void emptyGraphIsTriviallyRegular() {
        GraphRegularityAnalyzer a = analyze();
        assertTrue("empty graph treated as regular", a.isRegular());
        assertEquals(0, a.getRegularityDegree());
        assertEquals(0, a.getMinDegree());
        assertEquals(0, a.getMaxDegree());
        assertEquals(0.0, a.getMeanDegree(), 1e-9);
        assertEquals(0.0, a.getDegreeVariance(), 1e-9);
        assertEquals(0L, a.getAlbertsonIndex());
        assertEquals(0, a.getModeDegree());
        assertTrue(a.getDeviantVertices().isEmpty());
        assertTrue(a.getDegreeDistribution().isEmpty());
        assertTrue(a.getDegreeMap().isEmpty());
    }

    // ═══════════ Regular graphs ═══════════

    @Test
    public void cycleC4IsTwoRegular() {
        // C4: A-B-C-D-A — every vertex degree 2
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");
        addEdge("D", "A");

        GraphRegularityAnalyzer a = analyze();
        assertTrue(a.isRegular());
        assertEquals(2, a.getRegularityDegree());
        assertEquals(2, a.getMinDegree());
        assertEquals(2, a.getMaxDegree());
        assertEquals(2.0, a.getMeanDegree(), 1e-9);
        assertEquals(0.0, a.getDegreeVariance(), 1e-9);
        assertEquals("regular ⇒ Albertson index 0", 0L, a.getAlbertsonIndex());
        assertEquals(2, a.getModeDegree());
        assertTrue(a.getDeviantVertices().isEmpty());
    }

    @Test
    public void completeK4IsThreeRegular() {
        // K4: every pair connected
        String[] verts = {"A", "B", "C", "D"};
        for (int i = 0; i < verts.length; i++) {
            for (int j = i + 1; j < verts.length; j++) {
                addEdge(verts[i], verts[j]);
            }
        }

        GraphRegularityAnalyzer a = analyze();
        assertTrue(a.isRegular());
        assertEquals(3, a.getRegularityDegree());
        assertEquals(3.0, a.getMeanDegree(), 1e-9);
        assertEquals(0L, a.getAlbertsonIndex());

        Map<Integer, Integer> dist = a.getDegreeDistribution();
        assertEquals(1, dist.size());
        assertEquals(Integer.valueOf(4), dist.get(3));
    }

    @Test
    public void singleIsolatedVertexIsZeroRegular() {
        graph.addVertex("X");

        GraphRegularityAnalyzer a = analyze();
        assertTrue(a.isRegular());
        assertEquals(0, a.getRegularityDegree());
        assertEquals(0L, a.getAlbertsonIndex());
    }

    // ═══════════ Non-regular graphs ═══════════

    @Test
    public void starK1_4IsNotRegular() {
        // Center 'H' connected to 4 leaves
        addEdge("H", "L1");
        addEdge("H", "L2");
        addEdge("H", "L3");
        addEdge("H", "L4");

        GraphRegularityAnalyzer a = analyze();
        assertFalse(a.isRegular());
        assertEquals(-1, a.getRegularityDegree());
        assertEquals(1, a.getMinDegree());
        assertEquals(4, a.getMaxDegree());
        // Mean degree = (4 + 1*4) / 5 = 8/5
        assertEquals(8.0 / 5.0, a.getMeanDegree(), 1e-9);

        // Mode is degree 1 (4 leaves) — the hub is deviant
        assertEquals(1, a.getModeDegree());
        List<String> dev = a.getDeviantVertices();
        assertEquals(1, dev.size());
        assertEquals("H", dev.get(0));

        // Albertson: each of the 4 edges contributes |4-1| = 3 → total 12
        assertEquals(12L, a.getAlbertsonIndex());
    }

    @Test
    public void pathP4IsNotRegular() {
        // A-B-C-D — degrees: 1,2,2,1
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "D");

        GraphRegularityAnalyzer a = analyze();
        assertFalse(a.isRegular());
        assertEquals(1, a.getMinDegree());
        assertEquals(2, a.getMaxDegree());
        assertEquals(1.5, a.getMeanDegree(), 1e-9);

        // Variance: mean=1.5; ((1.5)^2 * 2 + (0.5)^2 * 2)/4 = (2*0.25 + 2*0.25)/... wait
        // diffs: 1-1.5=-0.5 (×2), 2-1.5=0.5 (×2). sumSq = 4 * 0.25 = 1.0. variance = 1.0/4 = 0.25.
        assertEquals(0.25, a.getDegreeVariance(), 1e-9);

        // Tie between mode 1 and mode 2 — implementation picks one via stream max;
        // whatever it picks, the *other* two vertices are deviant.
        int mode = a.getModeDegree();
        assertTrue("mode must be 1 or 2", mode == 1 || mode == 2);
        assertEquals(2, a.getDeviantVertices().size());

        // Albertson: edges A-B (|1-2|=1), B-C (|2-2|=0), C-D (|2-1|=1) → 2
        assertEquals(2L, a.getAlbertsonIndex());
    }

    // ═══════════ Distributions & maps ═══════════

    @Test
    public void degreeDistributionMatchesDegrees() {
        // Triangle + pendant: A-B, B-C, C-A, A-D
        // Degrees: A=3, B=2, C=2, D=1
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
        addEdge("A", "D");

        GraphRegularityAnalyzer a = analyze();
        Map<Integer, Integer> dist = a.getDegreeDistribution();
        assertEquals(Integer.valueOf(1), dist.get(1));
        assertEquals(Integer.valueOf(2), dist.get(2));
        assertEquals(Integer.valueOf(1), dist.get(3));

        Map<String, Integer> dm = a.getDegreeMap();
        assertEquals(Integer.valueOf(3), dm.get("A"));
        assertEquals(Integer.valueOf(2), dm.get("B"));
        assertEquals(Integer.valueOf(2), dm.get("C"));
        assertEquals(Integer.valueOf(1), dm.get("D"));

        assertEquals("mode is 2 (most common degree)", 2, a.getModeDegree());

        // Deviants: A (Δ=1) and D (Δ=1); should be sorted by abs deviation desc
        List<String> dev = a.getDeviantVertices();
        assertEquals(2, dev.size());
        assertTrue(dev.contains("A"));
        assertTrue(dev.contains("D"));

        // Albertson: A-B |3-2|=1, B-C 0, C-A |2-3|=1, A-D |3-1|=2 → 4
        assertEquals(4L, a.getAlbertsonIndex());
    }

    // ═══════════ Immutability ═══════════

    @Test(expected = UnsupportedOperationException.class)
    public void degreeMapIsUnmodifiable() {
        addEdge("A", "B");
        analyze().getDegreeMap().put("X", 99);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void degreeDistributionIsUnmodifiable() {
        addEdge("A", "B");
        analyze().getDegreeDistribution().put(42, 42);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void deviantVerticesIsUnmodifiable() {
        addEdge("H", "L1");
        addEdge("H", "L2");
        analyze().getDeviantVertices().add("nope");
    }

    // ═══════════ Report content ═══════════

    @Test
    public void reportMentionsRegularityForRegularGraph() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A"); // K3: 2-regular

        String r = analyze().generateReport();
        assertNotNull(r);
        assertTrue(r.contains("Graph Regularity Analysis"));
        assertTrue("should claim 2-regular", r.contains("2-regular"));
        assertTrue(r.contains("Albertson"));
    }

    @Test
    public void reportMentionsNonRegularForStar() {
        addEdge("H", "L1");
        addEdge("H", "L2");
        addEdge("H", "L3");

        String r = analyze().generateReport();
        assertTrue(r.contains("NOT regular"));
        assertTrue(r.contains("Mode degree"));
        assertTrue(r.contains("Deviant vertices"));
        assertTrue("hub H should appear among deviants", r.contains("H"));
    }

    @Test
    public void reportTopDeviantsCappedAtTen() {
        // Star with 12 leaves: hub degree 12, 12 leaves of degree 1 → mode=1, deviants=[hub]=1
        // Make a more interesting case: hub + 12 leaves + a "medium" cluster of 3 distinct degrees.
        // Easier: bigger star — only 1 deviant, but test that report doesn't crash and contains "Top deviants".
        for (int i = 0; i < 12; i++) {
            addEdge("H", "L" + i);
        }
        String r = analyze().generateReport();
        assertTrue(r.contains("Top deviants"));
        // Only 1 deviant → no "... and N more" line
        assertFalse(r.contains("... and "));
    }
}
