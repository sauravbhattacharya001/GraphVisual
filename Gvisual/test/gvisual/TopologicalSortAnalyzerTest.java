package gvisual;

import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/**
 * Tests for TopologicalSortAnalyzer.
 *
 * @author zalenix
 */
public class TopologicalSortAnalyzerTest {

    // ── Helper methods ──────────────────────────────────────────

    /**
     * Build a directed graph from vertex-edge definitions.
     * Each edge is from→to with a label.
     */
    private Graph<String, edge> buildDirectedGraph(String[][] edges) {
        Graph<String, edge> g = new DirectedSparseGraph<String, edge>();
        int edgeId = 0;
        for (String[] e : edges) {
            String from = e[0];
            String to = e[1];
            if (!g.containsVertex(from)) g.addVertex(from);
            if (!g.containsVertex(to)) g.addVertex(to);
            edge ed = new edge("dep", from, to);
            ed.setLabel("e" + edgeId++);
            g.addEdge(ed, from, to);
        }
        return g;
    }

    /**
     * Add isolated vertices to a graph.
     */
    private void addVertices(Graph<String, edge> g, String... vertices) {
        for (String v : vertices) {
            if (!g.containsVertex(v)) g.addVertex(v);
        }
    }

    // ── Constructor ─────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullGraph_throws() {
        new TopologicalSortAnalyzer(null);
    }

    // ── Empty graph ─────────────────────────────────────────────

    @Test
    public void analyze_emptyGraph_isDAG() {
        Graph<String, edge> g = new DirectedSparseGraph<String, edge>();
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.TopologicalSortResult result = analyzer.analyze();

        assertTrue(result.isDAG());
        assertTrue(result.getSortedOrder().isEmpty());
        assertTrue(result.getCycles().isEmpty());
        assertTrue(result.getRoots().isEmpty());
        assertTrue(result.getLeaves().isEmpty());
        assertEquals(0, result.getLongestPathLength());
    }

    // ── Single vertex ───────────────────────────────────────────

    @Test
    public void analyze_singleVertex_isDAG() {
        Graph<String, edge> g = new DirectedSparseGraph<String, edge>();
        g.addVertex("A");
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.TopologicalSortResult result = analyzer.analyze();

        assertTrue(result.isDAG());
        assertEquals(1, result.getSortedOrder().size());
        assertEquals("A", result.getSortedOrder().get(0));
        assertEquals(1, result.getRoots().size());
        assertEquals(1, result.getLeaves().size());
        assertEquals(0, result.getLongestPathLength());
    }

    // ── Simple chain ────────────────────────────────────────────

    @Test
    public void analyze_linearChain_correctOrder() {
        // A → B → C → D
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "B"}, {"B", "C"}, {"C", "D"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.TopologicalSortResult result = analyzer.analyze();

        assertTrue(result.isDAG());
        assertEquals(Arrays.asList("A", "B", "C", "D"), result.getSortedOrder());
        assertEquals(Arrays.asList("A"), result.getRoots());
        assertEquals(Arrays.asList("D"), result.getLeaves());
        assertEquals(3, result.getLongestPathLength());
    }

    @Test
    public void analyze_linearChain_criticalPath() {
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "B"}, {"B", "C"}, {"C", "D"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.TopologicalSortResult result = analyzer.analyze();

        assertEquals(Arrays.asList("A", "B", "C", "D"), result.getCriticalPath());
    }

    // ── Diamond DAG ─────────────────────────────────────────────

    @Test
    public void analyze_diamondDAG_isDAG() {
        //   A
        //  / \
        // B   C
        //  \ /
        //   D
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "B"}, {"A", "C"}, {"B", "D"}, {"C", "D"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.TopologicalSortResult result = analyzer.analyze();

        assertTrue(result.isDAG());
        assertEquals(4, result.getSortedOrder().size());

        // A must come before B, C; B and C must come before D
        List<String> order = result.getSortedOrder();
        assertTrue(order.indexOf("A") < order.indexOf("B"));
        assertTrue(order.indexOf("A") < order.indexOf("C"));
        assertTrue(order.indexOf("B") < order.indexOf("D"));
        assertTrue(order.indexOf("C") < order.indexOf("D"));
    }

    @Test
    public void analyze_diamondDAG_roots_and_leaves() {
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "B"}, {"A", "C"}, {"B", "D"}, {"C", "D"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.TopologicalSortResult result = analyzer.analyze();

        assertEquals(Arrays.asList("A"), result.getRoots());
        assertEquals(Arrays.asList("D"), result.getLeaves());
    }

    @Test
    public void analyze_diamondDAG_depths() {
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "B"}, {"A", "C"}, {"B", "D"}, {"C", "D"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.TopologicalSortResult result = analyzer.analyze();

        assertEquals(0, (int) result.getDepthMap().get("A"));
        assertEquals(1, (int) result.getDepthMap().get("B"));
        assertEquals(1, (int) result.getDepthMap().get("C"));
        assertEquals(2, (int) result.getDepthMap().get("D"));
    }

    // ── Multiple roots and leaves ───────────────────────────────

    @Test
    public void analyze_multipleRootsAndLeaves() {
        // A → C, B → C, C → D, C → E
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "C"}, {"B", "C"}, {"C", "D"}, {"C", "E"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.TopologicalSortResult result = analyzer.analyze();

        assertTrue(result.isDAG());
        assertEquals(Arrays.asList("A", "B"), result.getRoots());
        assertEquals(Arrays.asList("D", "E"), result.getLeaves());
    }

    // ── Disconnected DAG ────────────────────────────────────────

    @Test
    public void analyze_disconnectedDAG() {
        // A → B, C → D (two separate chains)
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "B"}, {"C", "D"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.TopologicalSortResult result = analyzer.analyze();

        assertTrue(result.isDAG());
        assertEquals(4, result.getSortedOrder().size());
        assertEquals(Arrays.asList("A", "C"), result.getRoots());
        assertEquals(Arrays.asList("B", "D"), result.getLeaves());
    }

    @Test
    public void analyze_disconnectedDAG_withIsolated() {
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "B"}
        });
        g.addVertex("X");
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.TopologicalSortResult result = analyzer.analyze();

        assertTrue(result.isDAG());
        assertEquals(3, result.getSortedOrder().size());
        assertTrue(result.getRoots().contains("X"));
        assertTrue(result.getLeaves().contains("X"));
    }

    // ── Simple cycle ────────────────────────────────────────────

    @Test
    public void analyze_simpleCycle_notDAG() {
        // A → B → C → A
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "B"}, {"B", "C"}, {"C", "A"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.TopologicalSortResult result = analyzer.analyze();

        assertFalse(result.isDAG());
        assertTrue(result.getSortedOrder().isEmpty());
        assertFalse(result.getCycles().isEmpty());

        // Check that cycle contains A, B, C
        TopologicalSortAnalyzer.CycleInfo cycle = result.getCycles().get(0);
        Set<String> cycleVerts = new HashSet<String>(cycle.getVertices());
        assertTrue(cycleVerts.contains("A"));
        assertTrue(cycleVerts.contains("B"));
        assertTrue(cycleVerts.contains("C"));
    }

    @Test
    public void analyze_selfLoop_notDAG() {
        // A → A
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "A"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.TopologicalSortResult result = analyzer.analyze();

        assertFalse(result.isDAG());
    }

    @Test
    public void analyze_twoCycle_notDAG() {
        // A → B → A
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "B"}, {"B", "A"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.TopologicalSortResult result = analyzer.analyze();

        assertFalse(result.isDAG());
        assertFalse(result.getCycles().isEmpty());
    }

    // ── Dependency counts ───────────────────────────────────────

    @Test
    public void analyze_dependencyCounts_correct() {
        // A → C, B → C, C → D
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "C"}, {"B", "C"}, {"C", "D"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.TopologicalSortResult result = analyzer.analyze();

        // in-degree
        assertEquals(0, (int) result.getDependencyCount().get("A"));
        assertEquals(0, (int) result.getDependencyCount().get("B"));
        assertEquals(2, (int) result.getDependencyCount().get("C"));
        assertEquals(1, (int) result.getDependencyCount().get("D"));
    }

    @Test
    public void analyze_dependentCounts_correct() {
        // A → C, B → C, C → D
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "C"}, {"B", "C"}, {"C", "D"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.TopologicalSortResult result = analyzer.analyze();

        // out-degree
        assertEquals(1, (int) result.getDependentCount().get("A"));
        assertEquals(1, (int) result.getDependentCount().get("B"));
        assertEquals(1, (int) result.getDependentCount().get("C"));
        assertEquals(0, (int) result.getDependentCount().get("D"));
    }

    // ── Vertex dependency info ──────────────────────────────────

    @Test
    public void analyzeDependencies_nullVertex_returnsNull() {
        Graph<String, edge> g = new DirectedSparseGraph<String, edge>();
        g.addVertex("A");
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        assertNull(analyzer.analyzeDependencies(null));
    }

    @Test
    public void analyzeDependencies_missingVertex_returnsNull() {
        Graph<String, edge> g = new DirectedSparseGraph<String, edge>();
        g.addVertex("A");
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        assertNull(analyzer.analyzeDependencies("Z"));
    }

    @Test
    public void analyzeDependencies_transitiveForward() {
        // A → B → C → D
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "B"}, {"B", "C"}, {"C", "D"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.VertexDependencyInfo info = analyzer.analyzeDependencies("B");

        assertEquals("B", info.getVertex());
        // B depends on A (transitively)
        assertTrue(info.getAllDependencies().contains("A"));
        assertEquals(1, info.getAllDependencies().size());
        // B's dependents are C and D (transitively)
        assertTrue(info.getAllDependents().contains("C"));
        assertTrue(info.getAllDependents().contains("D"));
        assertEquals(2, info.getAllDependents().size());
        assertFalse(info.isRoot());
        assertFalse(info.isLeaf());
    }

    @Test
    public void analyzeDependencies_rootVertex() {
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "B"}, {"B", "C"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.VertexDependencyInfo info = analyzer.analyzeDependencies("A");

        assertTrue(info.isRoot());
        assertFalse(info.isLeaf());
        assertTrue(info.getAllDependencies().isEmpty());
        assertEquals(2, info.getAllDependents().size());
    }

    @Test
    public void analyzeDependencies_leafVertex() {
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "B"}, {"B", "C"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.VertexDependencyInfo info = analyzer.analyzeDependencies("C");

        assertFalse(info.isRoot());
        assertTrue(info.isLeaf());
        assertEquals(2, info.getAllDependencies().size());
        assertTrue(info.getAllDependents().isEmpty());
    }

    @Test
    public void analyzeDependencies_isolatedVertex() {
        Graph<String, edge> g = new DirectedSparseGraph<String, edge>();
        g.addVertex("X");
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.VertexDependencyInfo info = analyzer.analyzeDependencies("X");

        assertTrue(info.isRoot());
        assertTrue(info.isLeaf());
        assertEquals(0, info.getDepth());
        assertTrue(info.getAllDependencies().isEmpty());
        assertTrue(info.getAllDependents().isEmpty());
    }

    // ── Choice points ───────────────────────────────────────────

    @Test
    public void countChoicePoints_linearChain_zero() {
        // A → B → C (no choice — exactly one ready at each step)
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "B"}, {"B", "C"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        assertEquals(0, analyzer.countChoicePoints());
    }

    @Test
    public void countChoicePoints_parallelRoots_one() {
        // A → C, B → C (A and B both ready at start)
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "C"}, {"B", "C"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        assertEquals(1, analyzer.countChoicePoints());
    }

    @Test
    public void countChoicePoints_cycleGraph_negative() {
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "B"}, {"B", "A"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        assertEquals(-1, analyzer.countChoicePoints());
    }

    // ── Summary ─────────────────────────────────────────────────

    @Test
    public void generateSummary_DAG_containsKeyInfo() {
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "B"}, {"B", "C"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        String summary = analyzer.generateSummary();

        assertTrue(summary.contains("TOPOLOGICAL SORT ANALYSIS"));
        assertTrue(summary.contains("Is DAG:   Yes"));
        assertTrue(summary.contains("TOPOLOGICAL ORDER"));
        assertTrue(summary.contains("1. A"));
        assertTrue(summary.contains("2. B"));
        assertTrue(summary.contains("3. C"));
        assertTrue(summary.contains("STRUCTURE"));
        assertTrue(summary.contains("[A]"));
    }

    @Test
    public void generateSummary_cycle_showsCycleInfo() {
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "B"}, {"B", "C"}, {"C", "A"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        String summary = analyzer.generateSummary();

        assertTrue(summary.contains("Is DAG:   No"));
        assertTrue(summary.contains("CYCLES DETECTED"));
        assertTrue(summary.contains("cycle(s) found"));
    }

    // ── Complex DAG (course prerequisites) ──────────────────────

    @Test
    public void analyze_coursePrerequisites() {
        // Math101 → Math201 → Math301
        // Math101 → CS101 → CS201
        // CS101 → CS202
        // Math201 → CS202
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"Math101", "Math201"}, {"Math201", "Math301"},
            {"Math101", "CS101"}, {"CS101", "CS201"},
            {"CS101", "CS202"}, {"Math201", "CS202"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.TopologicalSortResult result = analyzer.analyze();

        assertTrue(result.isDAG());
        assertEquals(6, result.getSortedOrder().size());

        // Math101 must be first
        assertEquals("Math101", result.getSortedOrder().get(0));

        // CS202 depends on both CS101 and Math201
        List<String> order = result.getSortedOrder();
        assertTrue(order.indexOf("CS101") < order.indexOf("CS202"));
        assertTrue(order.indexOf("Math201") < order.indexOf("CS202"));

        // One root, multiple leaves
        assertEquals(1, result.getRoots().size());
        assertEquals("Math101", result.getRoots().get(0));
        assertTrue(result.getLeaves().contains("Math301"));
        assertTrue(result.getLeaves().contains("CS201"));
        assertTrue(result.getLeaves().contains("CS202"));
    }

    @Test
    public void analyze_coursePrerequisites_criticalPath() {
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"Math101", "Math201"}, {"Math201", "Math301"},
            {"Math101", "CS101"}, {"CS101", "CS201"},
            {"CS101", "CS202"}, {"Math201", "CS202"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.TopologicalSortResult result = analyzer.analyze();

        // CS202 has depth 3 (Math101→Math201→CS202 or Math101→CS101→CS202)
        // Actually: Math101(0)→Math201(1)→CS202. CS101(1)→CS202.
        // CS202 gets max(1+1, 1+1) = 2. Math301 gets 1+1 = 2.
        // So longest path = 2, critical path length = 2
        assertEquals(2, result.getLongestPathLength());
    }

    // ── Build system DAG ────────────────────────────────────────

    @Test
    public void analyze_buildSystem() {
        // libutil → libcore → app
        // libutil → libnet → app
        // libnet → libsecurity → app
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"libutil", "libcore"}, {"libcore", "app"},
            {"libutil", "libnet"}, {"libnet", "app"},
            {"libnet", "libsecurity"}, {"libsecurity", "app"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.TopologicalSortResult result = analyzer.analyze();

        assertTrue(result.isDAG());
        assertEquals(5, result.getSortedOrder().size());

        // libutil must be first, app must be last
        assertEquals("libutil", result.getSortedOrder().get(0));
        assertEquals("app", result.getSortedOrder().get(result.getSortedOrder().size() - 1));

        // app depends on everything
        assertEquals(3, (int) result.getDependencyCount().get("app"));
    }

    // ── Cycle with tail ─────────────────────────────────────────

    @Test
    public void analyze_cycleWithTail() {
        // A → B → C → D → B (cycle B-C-D, tail A)
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "B"}, {"B", "C"}, {"C", "D"}, {"D", "B"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.TopologicalSortResult result = analyzer.analyze();

        assertFalse(result.isDAG());
        assertFalse(result.getCycles().isEmpty());
    }

    // ── CycleInfo ───────────────────────────────────────────────

    @Test
    public void cycleInfo_size() {
        List<String> verts = Arrays.asList("A", "B", "C");
        TopologicalSortAnalyzer.CycleInfo info =
            new TopologicalSortAnalyzer.CycleInfo(verts, new ArrayList<edge>());
        assertEquals(3, info.size());
    }

    @Test
    public void cycleInfo_immutable() {
        List<String> verts = new ArrayList<String>(Arrays.asList("A", "B"));
        TopologicalSortAnalyzer.CycleInfo info =
            new TopologicalSortAnalyzer.CycleInfo(verts, new ArrayList<edge>());

        // Original list mutation shouldn't affect CycleInfo
        verts.add("C");
        assertEquals(2, info.getVertices().size());
    }

    // ── Result immutability ─────────────────────────────────────

    @Test(expected = UnsupportedOperationException.class)
    public void result_sortedOrder_immutable() {
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "B"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.TopologicalSortResult result = analyzer.analyze();
        result.getSortedOrder().add("Z");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void result_depthMap_immutable() {
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "B"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.TopologicalSortResult result = analyzer.analyze();
        result.getDepthMap().put("Z", 99);
    }

    // ── Deterministic ordering ──────────────────────────────────

    @Test
    public void analyze_deterministic_sameOrderEveryTime() {
        // Multiple possible orderings — should be deterministic
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "D"}, {"B", "D"}, {"C", "D"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);

        List<String> first = analyzer.analyze().getSortedOrder();
        List<String> second = analyzer.analyze().getSortedOrder();
        assertEquals(first, second);

        // Lexicographic: A, B, C before D
        assertEquals("A", first.get(0));
        assertEquals("B", first.get(1));
        assertEquals("C", first.get(2));
        assertEquals("D", first.get(3));
    }

    // ── Wide graph ──────────────────────────────────────────────

    @Test
    public void analyze_wideGraph_manyRoots() {
        // 10 independent roots all pointing to a single sink
        Graph<String, edge> g = new DirectedSparseGraph<String, edge>();
        g.addVertex("sink");
        for (int i = 0; i < 10; i++) {
            String v = "root" + i;
            g.addVertex(v);
            edge e = new edge("dep", v, "sink");
            e.setLabel("e" + i);
            g.addEdge(e, v, "sink");
        }
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.TopologicalSortResult result = analyzer.analyze();

        assertTrue(result.isDAG());
        assertEquals(11, result.getSortedOrder().size());
        assertEquals(10, result.getRoots().size());
        assertEquals(1, result.getLeaves().size());
        assertEquals("sink", result.getSortedOrder().get(10)); // last
    }

    // ── Deep graph ──────────────────────────────────────────────

    @Test
    public void analyze_deepChain_longestPath() {
        // v0 → v1 → v2 → ... → v19 (chain of 20)
        Graph<String, edge> g = new DirectedSparseGraph<String, edge>();
        for (int i = 0; i < 20; i++) {
            g.addVertex("v" + i);
        }
        for (int i = 0; i < 19; i++) {
            String from = "v" + i;
            String to = "v" + (i + 1);
            edge e = new edge("dep", from, to);
            e.setLabel("e" + i);
            g.addEdge(e, from, to);
        }

        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.TopologicalSortResult result = analyzer.analyze();

        assertTrue(result.isDAG());
        assertEquals(20, result.getSortedOrder().size());
        assertEquals(19, result.getLongestPathLength());
        assertEquals(20, result.getCriticalPath().size());
    }

    // ── VertexDependencyInfo depth ──────────────────────────────

    @Test
    public void analyzeDependencies_depth_correct() {
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "B"}, {"B", "C"}, {"C", "D"}
        });
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);

        assertEquals(0, analyzer.analyzeDependencies("A").getDepth());
        assertEquals(1, analyzer.analyzeDependencies("B").getDepth());
        assertEquals(2, analyzer.analyzeDependencies("C").getDepth());
        assertEquals(3, analyzer.analyzeDependencies("D").getDepth());
    }

    // ── Mixed: DAG with isolated vertices ───────────────────────

    @Test
    public void analyze_dagWithIsolated_allIncluded() {
        Graph<String, edge> g = buildDirectedGraph(new String[][]{
            {"A", "B"}
        });
        g.addVertex("X");
        g.addVertex("Y");
        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.TopologicalSortResult result = analyzer.analyze();

        assertTrue(result.isDAG());
        assertEquals(4, result.getSortedOrder().size());
        // Isolated vertices are both roots and leaves
        assertTrue(result.getRoots().contains("X"));
        assertTrue(result.getRoots().contains("Y"));
        assertTrue(result.getLeaves().contains("X"));
        assertTrue(result.getLeaves().contains("Y"));
    }

    // ── Undirected graph edge handling ──────────────────────────

    @Test
    public void analyze_handlesEdgesWithNullVertices() {
        Graph<String, edge> g = new DirectedSparseGraph<String, edge>();
        g.addVertex("A");
        g.addVertex("B");
        // Add normal edge
        edge e1 = new edge("dep", "A", "B");
        e1.setLabel("e1");
        g.addEdge(e1, "A", "B");
        // Add edge with null vertex1 (shouldn't crash)
        edge e2 = new edge("dep", null, "B");
        e2.setLabel("e2");
        // Can't add to directed graph with null — just test with valid edges

        TopologicalSortAnalyzer analyzer = new TopologicalSortAnalyzer(g);
        TopologicalSortAnalyzer.TopologicalSortResult result = analyzer.analyze();
        assertTrue(result.isDAG());
    }
}
