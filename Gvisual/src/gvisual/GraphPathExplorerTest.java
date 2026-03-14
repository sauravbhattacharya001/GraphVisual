package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.*;

/**
 * Test suite for GraphPathExplorer — 45 tests covering all 7 capabilities.
 *
 * <p>Run with: {@code java gvisual.GraphPathExplorerTest}</p>
 *
 * @author zalenix
 */
public class GraphPathExplorerTest {

    private static int passed = 0;
    private static int failed = 0;

    // ── Test graphs ────────────────────────────────────────────────

    /**
     * Linear: A - B - C - D - E
     */
    private static Graph<String, edge> linearGraph() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addVertex("D"); g.addVertex("E");
        addEdge(g, "A", "B", 1); addEdge(g, "B", "C", 1);
        addEdge(g, "C", "D", 1); addEdge(g, "D", "E", 1);
        return g;
    }

    /**
     * Diamond:    A
     *            / \
     *           B   C
     *            \ /
     *             D
     */
    private static Graph<String, edge> diamondGraph() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B");
        g.addVertex("C"); g.addVertex("D");
        addEdge(g, "A", "B", 1); addEdge(g, "A", "C", 1);
        addEdge(g, "B", "D", 1); addEdge(g, "C", "D", 1);
        return g;
    }

    /**
     * Grid (3x3):
     *   A - B - C
     *   |   |   |
     *   D - E - F
     *   |   |   |
     *   G - H - I
     */
    private static Graph<String, edge> gridGraph() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        String[] nodes = {"A","B","C","D","E","F","G","H","I"};
        for (String n : nodes) g.addVertex(n);
        // Horizontal
        addEdge(g, "A", "B", 1); addEdge(g, "B", "C", 1);
        addEdge(g, "D", "E", 1); addEdge(g, "E", "F", 1);
        addEdge(g, "G", "H", 1); addEdge(g, "H", "I", 1);
        // Vertical
        addEdge(g, "A", "D", 1); addEdge(g, "B", "E", 1);
        addEdge(g, "C", "F", 1); addEdge(g, "D", "G", 1);
        addEdge(g, "E", "H", 1); addEdge(g, "F", "I", 1);
        return g;
    }

    /**
     * Weighted: A -1- B -2- C
     *                  |     |
     *                  5     1
     *                  |     |
     *                  D -1- E
     */
    private static Graph<String, edge> weightedGraph() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addVertex("D"); g.addVertex("E");
        addEdge(g, "A", "B", 1); addEdge(g, "B", "C", 2);
        addEdge(g, "B", "D", 5); addEdge(g, "C", "E", 1);
        addEdge(g, "D", "E", 1);
        return g;
    }

    /**
     * Disconnected: A - B - C    D - E
     */
    private static Graph<String, edge> disconnectedGraph() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addVertex("D"); g.addVertex("E");
        addEdge(g, "A", "B", 1); addEdge(g, "B", "C", 1);
        addEdge(g, "D", "E", 1);
        return g;
    }

    /**
     * Complete K4: A, B, C, D — all connected
     */
    private static Graph<String, edge> completeK4() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B");
        g.addVertex("C"); g.addVertex("D");
        addEdge(g, "A", "B", 1); addEdge(g, "A", "C", 1);
        addEdge(g, "A", "D", 1); addEdge(g, "B", "C", 1);
        addEdge(g, "B", "D", 1); addEdge(g, "C", "D", 1);
        return g;
    }

    /**
     * Bottleneck graph: A - B - X - C - D
     *                           |
     *                   E - F - X (same X)
     * X is the only path between {A,B} and {C,D,E,F}
     */
    private static Graph<String, edge> bottleneckGraph() {
        Graph<String, edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("X");
        g.addVertex("C"); g.addVertex("D");
        addEdge(g, "A", "B", 1); addEdge(g, "B", "X", 1);
        addEdge(g, "X", "C", 1); addEdge(g, "C", "D", 1);
        return g;
    }

    private static int edgeCounter = 0;
    private static void addEdge(Graph<String, edge> g, String v1, String v2, float w) {
        edge e = new edge("e", v1, v2);
        e.setWeight(w);
        e.setLabel("e" + (edgeCounter++));
        g.addEdge(e, v1, v2);
    }

    // ── Test helpers ──────────────────────────────────────────────

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  PASS: " + name);
        } else {
            failed++;
            System.out.println("  FAIL: " + name);
        }
    }

    private static void checkThrows(String name, Runnable fn, Class<?> exType) {
        try {
            fn.run();
            failed++;
            System.out.println("  FAIL: " + name + " (no exception thrown)");
        } catch (Exception e) {
            if (exType.isInstance(e)) {
                passed++;
                System.out.println("  PASS: " + name);
            } else {
                failed++;
                System.out.println("  FAIL: " + name + " (wrong exception: " + e.getClass().getSimpleName() + ")");
            }
        }
    }

    // ── Tests ─────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("\n=== GraphPathExplorer Tests ===\n");

        testConstructor();
        testAllSimplePaths();
        testKShortestPaths();
        testConstrainedPaths();
        testAvoidanceRouting();
        testPathStats();
        testVertexParticipation();
        testBottleneckVertices();
        testEdgeKey();
        testReport();
        testPathClass();

        System.out.println("\n================================");
        System.out.printf("Results: %d passed, %d failed, %d total%n",
            passed, failed, passed + failed);
        if (failed > 0) System.exit(1);
    }

    private static void testConstructor() {
        System.out.println("\n-- Constructor --");
        checkThrows("null graph", () -> new GraphPathExplorer(null),
            IllegalArgumentException.class);
        checkThrows("empty graph",
            () -> new GraphPathExplorer(new UndirectedSparseGraph<>()),
            IllegalArgumentException.class);
        Graph<String, edge> g = linearGraph();
        GraphPathExplorer ex = new GraphPathExplorer(g);
        check("valid construction", ex != null);
    }

    private static void testAllSimplePaths() {
        System.out.println("\n-- All Simple Paths --");

        // Linear: only one path A->E
        GraphPathExplorer lin = new GraphPathExplorer(linearGraph());
        List<GraphPathExplorer.Path> paths = lin.findAllSimplePaths("A", "E");
        check("linear: exactly 1 path", paths.size() == 1);
        check("linear: 4 hops", paths.get(0).getHopCount() == 4);

        // Diamond: 2 paths A->D
        GraphPathExplorer dia = new GraphPathExplorer(diamondGraph());
        paths = dia.findAllSimplePaths("A", "D");
        check("diamond: 2 paths A->D", paths.size() == 2);
        check("diamond: both 2 hops",
            paths.get(0).getHopCount() == 2 && paths.get(1).getHopCount() == 2);

        // Complete K4: A->D has multiple paths
        GraphPathExplorer k4 = new GraphPathExplorer(completeK4());
        paths = k4.findAllSimplePaths("A", "D");
        check("K4: >=3 paths A->D", paths.size() >= 3);
        check("K4: shortest is 1 hop", paths.get(0).getHopCount() == 1);

        // Grid: many paths A->I
        GraphPathExplorer grid = new GraphPathExplorer(gridGraph());
        paths = grid.findAllSimplePaths("A", "I");
        check("grid: many paths A->I", paths.size() > 2);
        check("grid: shortest is 4 hops", paths.get(0).getHopCount() == 4);

        // Disconnected: no paths A->D
        GraphPathExplorer disc = new GraphPathExplorer(disconnectedGraph());
        paths = disc.findAllSimplePaths("A", "D");
        check("disconnected: 0 paths A->D", paths.isEmpty());

        // Max depth limit
        grid = new GraphPathExplorer(gridGraph());
        List<GraphPathExplorer.Path> limited = grid.findAllSimplePaths("A", "I", 4, 1000);
        List<GraphPathExplorer.Path> unlimited = grid.findAllSimplePaths("A", "I", 20, 1000);
        check("maxDepth limits paths", limited.size() <= unlimited.size());

        // Max paths limit
        List<GraphPathExplorer.Path> capped = grid.findAllSimplePaths("A", "I", 20, 2);
        check("maxPaths caps results", capped.size() <= 2);

        // Invalid vertices
        checkThrows("invalid source",
            () -> lin.findAllSimplePaths("Z", "A"),
            IllegalArgumentException.class);
        checkThrows("invalid target",
            () -> lin.findAllSimplePaths("A", "Z"),
            IllegalArgumentException.class);
    }

    private static void testKShortestPaths() {
        System.out.println("\n-- K Shortest Paths --");

        // Diamond: 2 shortest A->D
        GraphPathExplorer dia = new GraphPathExplorer(diamondGraph());
        List<GraphPathExplorer.Path> ks = dia.findKShortestPaths("A", "D", 5);
        check("diamond: 2 k-shortest", ks.size() == 2);
        check("diamond: first is shortest",
            ks.get(0).getTotalWeight() <= ks.get(1).getTotalWeight());

        // K4: at least 3 shortest
        GraphPathExplorer k4 = new GraphPathExplorer(completeK4());
        ks = k4.findKShortestPaths("A", "D", 3);
        check("K4: 3 k-shortest", ks.size() == 3);
        check("K4: ordered by weight",
            ks.get(0).getTotalWeight() <= ks.get(1).getTotalWeight() &&
            ks.get(1).getTotalWeight() <= ks.get(2).getTotalWeight());

        // Disconnected: no paths
        GraphPathExplorer disc = new GraphPathExplorer(disconnectedGraph());
        ks = disc.findKShortestPaths("A", "D", 3);
        check("disconnected: 0 k-shortest", ks.isEmpty());

        // Weighted: respects weights
        GraphPathExplorer wt = new GraphPathExplorer(weightedGraph());
        ks = wt.findKShortestPaths("A", "E", 2);
        check("weighted: >=1 path", ks.size() >= 1);
        // A->B->C->E = 1+2+1 = 4 should be shorter than A->B->D->E = 1+5+1 = 7
        check("weighted: shortest <= 5", ks.get(0).getTotalWeight() <= 5);

        // k=1 gives single shortest
        ks = dia.findKShortestPaths("A", "D", 1);
        check("k=1: single result", ks.size() == 1);

        // Invalid k
        checkThrows("k < 1",
            () -> dia.findKShortestPaths("A", "D", 0),
            IllegalArgumentException.class);
    }

    private static void testConstrainedPaths() {
        System.out.println("\n-- Constrained Paths --");

        // Grid: A->I through E
        GraphPathExplorer grid = new GraphPathExplorer(gridGraph());
        GraphPathExplorer.Path p = grid.findConstrainedPath("A", "I",
            Arrays.asList("E"));
        check("grid: constrained through E exists", p != null);
        check("grid: path contains E", p.getVertices().contains("E"));
        check("grid: starts at A", p.getSource().equals("A"));
        check("grid: ends at I", p.getTarget().equals("I"));

        // Multiple waypoints: A -> B -> E -> H -> I
        p = grid.findConstrainedPath("A", "I",
            Arrays.asList("B", "H"));
        check("grid: multi-waypoint exists", p != null);
        int bIdx = p.getVertices().indexOf("B");
        int hIdx = p.getVertices().indexOf("H");
        check("grid: B before H in path", bIdx < hIdx);

        // Empty waypoints = shortest path
        p = grid.findConstrainedPath("A", "I", Collections.emptyList());
        check("grid: empty waypoints works", p != null);

        // Null waypoints = shortest path
        p = grid.findConstrainedPath("A", "I", null);
        check("grid: null waypoints works", p != null);

        // Disconnected: constrained fails
        GraphPathExplorer disc = new GraphPathExplorer(disconnectedGraph());
        p = disc.findConstrainedPath("A", "D", Arrays.asList("B"));
        check("disconnected: constrained returns null", p == null);
    }

    private static void testAvoidanceRouting() {
        System.out.println("\n-- Avoidance Routing --");

        // Diamond: avoid B forces through C
        GraphPathExplorer dia = new GraphPathExplorer(diamondGraph());
        Set<String> avoid = new HashSet<>(Arrays.asList("B"));
        GraphPathExplorer.Path p = dia.findPathAvoiding("A", "D", avoid, null);
        check("diamond: avoid B exists", p != null);
        check("diamond: path doesn't contain B",
            !p.getVertices().contains("B"));
        check("diamond: goes through C", p.getVertices().contains("C"));

        // Avoid both B and C: no path
        avoid = new HashSet<>(Arrays.asList("B", "C"));
        p = dia.findPathAvoiding("A", "D", avoid, null);
        check("diamond: avoid B+C = null", p == null);

        // Edge avoidance
        Set<String> avoidEdges = new HashSet<>();
        avoidEdges.add(GraphPathExplorer.edgeKey("A", "B"));
        p = dia.findPathAvoiding("A", "D", null, avoidEdges);
        check("diamond: avoid A-B edge exists", p != null);
        check("diamond: goes through C (edge avoided)",
            p.getVertices().contains("C"));

        // Avoid source returns null
        avoid = new HashSet<>(Arrays.asList("A"));
        p = dia.findPathAvoiding("A", "D", avoid, null);
        check("avoid source returns null", p == null);

        // Avoid target returns null
        avoid = new HashSet<>(Arrays.asList("D"));
        p = dia.findPathAvoiding("A", "D", avoid, null);
        check("avoid target returns null", p == null);
    }

    private static void testPathStats() {
        System.out.println("\n-- Path Statistics --");

        // Grid stats
        GraphPathExplorer grid = new GraphPathExplorer(gridGraph());
        GraphPathExplorer.PathStats stats = grid.computePathStats("A", "I", 10);
        check("grid stats: pathCount > 0", stats.getPathCount() > 0);
        check("grid stats: shortest 4 hops", stats.getShortestHops() == 4);
        check("grid stats: longest >= 4 hops", stats.getLongestHops() >= 4);
        check("grid stats: avg hops > 0", stats.getAvgHops() > 0);
        check("grid stats: diversity in [0,1]",
            stats.getPathDiversity() >= 0 && stats.getPathDiversity() <= 1);
        check("grid stats: intermediate vertices > 0",
            stats.getIntermediateVertices().size() > 0);
        check("grid stats: length dist not empty",
            !stats.getLengthDistribution().isEmpty());

        // Empty paths stats
        GraphPathExplorer disc = new GraphPathExplorer(disconnectedGraph());
        stats = disc.computePathStats("A", "D", 10);
        check("disc stats: 0 paths", stats.getPathCount() == 0);
        check("disc stats: diversity = 1.0", stats.getPathDiversity() == 1.0);

        // Path length distribution
        Map<Integer, Integer> dist = grid.pathLengthDistribution("A", "I", 10);
        check("grid dist: has length 4", dist.containsKey(4));
        check("grid dist: all values > 0",
            dist.values().stream().allMatch(v -> v > 0));

        // toString doesn't crash
        String s = stats.toString();
        check("stats toString works", s != null && !s.isEmpty());
    }

    private static void testVertexParticipation() {
        System.out.println("\n-- Vertex Participation --");

        // Grid: E should have high participation between A and I
        GraphPathExplorer grid = new GraphPathExplorer(gridGraph());
        Map<String, Integer> part = grid.vertexPathParticipation("A", "I", 10);
        check("grid: E participates", part.containsKey("E"));
        check("grid: source A not in participation", !part.containsKey("A"));
        check("grid: target I not in participation", !part.containsKey("I"));

        // Linear: all intermediates participate in all paths
        GraphPathExplorer lin = new GraphPathExplorer(linearGraph());
        part = lin.vertexPathParticipation("A", "E", 10);
        check("linear: B participates 1 time", part.getOrDefault("B", 0) == 1);
        check("linear: C participates 1 time", part.getOrDefault("C", 0) == 1);
        check("linear: D participates 1 time", part.getOrDefault("D", 0) == 1);
    }

    private static void testBottleneckVertices() {
        System.out.println("\n-- Bottleneck Vertices --");

        // Bottleneck graph: X is the only bottleneck
        GraphPathExplorer bn = new GraphPathExplorer(bottleneckGraph());
        Set<String> bottlenecks = bn.findBottleneckVertices("A", "D", 10);
        check("bottleneck: X is a bottleneck", bottlenecks.contains("X"));
        check("bottleneck: B is a bottleneck (linear path)",
            bottlenecks.contains("B"));
        check("bottleneck: C is a bottleneck (linear path)",
            bottlenecks.contains("C"));

        // Diamond: no bottleneck between A and D (two disjoint paths)
        GraphPathExplorer dia = new GraphPathExplorer(diamondGraph());
        bottlenecks = dia.findBottleneckVertices("A", "D", 10);
        check("diamond: no bottlenecks", bottlenecks.isEmpty());

        // Disconnected: no paths = no bottlenecks
        GraphPathExplorer disc = new GraphPathExplorer(disconnectedGraph());
        bottlenecks = disc.findBottleneckVertices("A", "D", 10);
        check("disconnected: no bottlenecks", bottlenecks.isEmpty());
    }

    private static void testEdgeKey() {
        System.out.println("\n-- Edge Key --");
        check("edgeKey A|B", GraphPathExplorer.edgeKey("A", "B").equals("A|B"));
        check("edgeKey B|A = A|B",
            GraphPathExplorer.edgeKey("B", "A").equals("A|B"));
        check("edgeKey same = same",
            GraphPathExplorer.edgeKey("X", "X").equals("X|X"));
    }

    private static void testReport() {
        System.out.println("\n-- Report --");

        GraphPathExplorer grid = new GraphPathExplorer(gridGraph());
        String report = grid.generateReport("A", "I");
        check("report: not null", report != null);
        check("report: contains Source", report.contains("Source: A"));
        check("report: contains Target", report.contains("Target: I"));
        check("report: contains Summary", report.contains("Summary"));
        check("report: contains Total simple paths",
            report.contains("Total simple paths"));

        // Disconnected report
        GraphPathExplorer disc = new GraphPathExplorer(disconnectedGraph());
        report = disc.generateReport("A", "D");
        check("disc report: no paths message",
            report.contains("No paths found"));
    }

    private static void testPathClass() {
        System.out.println("\n-- Path Class --");

        GraphPathExplorer.Path p1 = new GraphPathExplorer.Path(
            Arrays.asList("A", "B", "C"), 3.0);
        GraphPathExplorer.Path p2 = new GraphPathExplorer.Path(
            Arrays.asList("A", "B", "C"), 3.0);
        GraphPathExplorer.Path p3 = new GraphPathExplorer.Path(
            Arrays.asList("A", "D", "C"), 5.0);

        check("path equals same vertices", p1.equals(p2));
        check("path not equals different vertices", !p1.equals(p3));
        check("path hashCode consistent", p1.hashCode() == p2.hashCode());
        check("path compareTo: lower weight first", p1.compareTo(p3) < 0);
        check("path source", p1.getSource().equals("A"));
        check("path target", p1.getTarget().equals("C"));
        check("path hopCount", p1.getHopCount() == 2);
        check("path toString", p1.toString().contains("A"));
    }
}
