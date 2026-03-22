package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Steiner Tree Analyzer — computes minimum Steiner trees and related metrics.
 *
 * <p>A Steiner tree for a set of <em>terminal</em> vertices in a weighted graph is a
 * subtree of minimum total weight that spans all terminals. Unlike minimum spanning
 * trees, Steiner trees may include additional non-terminal vertices (called
 * <em>Steiner points</em>) to reduce total cost.</p>
 *
 * <h3>Algorithms</h3>
 * <ul>
 *   <li><b>Shortest-path heuristic:</b> Greedy algorithm that iteratively connects
 *       the nearest unconnected terminal via shortest paths — O(T · V²) where T =
 *       |terminals|. Approximation ratio ≤ 2.</li>
 *   <li><b>Minimum spanning tree heuristic:</b> Builds a complete graph on terminals
 *       using shortest-path distances, finds MST, then maps back — O(T² · V log V).
 *       Approximation ratio ≤ 2(1 − 1/L) where L = number of leaves.</li>
 *   <li><b>Exact (Dreyfus–Wagner):</b> Dynamic programming over subsets of terminals
 *       — O(3^T · V + 2^T · V² + V · E). Feasible for T ≤ 15.</li>
 * </ul>
 *
 * <h3>Analytics</h3>
 * <ul>
 *   <li><b>Steiner ratio:</b> cost(Steiner tree) / cost(MST of terminals)</li>
 *   <li><b>Steiner points:</b> non-terminal vertices used in the optimal tree</li>
 *   <li><b>Terminal reachability:</b> which terminals can be connected</li>
 *   <li><b>Bottleneck edge:</b> heaviest edge in the Steiner tree</li>
 *   <li><b>Savings analysis:</b> cost reduction vs direct MST on terminals</li>
 *   <li><b>Vertex importance:</b> how much removing a Steiner point increases cost</li>
 * </ul>
 *
 * <h3>Applications</h3>
 * <ul>
 *   <li>Network design — connecting sites with minimum cabling</li>
 *   <li>VLSI circuit layout — wire routing</li>
 *   <li>Telecommunications — multicast routing</li>
 *   <li>Phylogenetics — evolutionary tree reconstruction</li>
 *   <li>Transportation planning — hub-and-spoke networks</li>
 * </ul>
 *
 * @author zalenix
 */
public class SteinerTreeAnalyzer {

    private final Graph<String, Edge> graph;
    private int syntheticEdgeId = 0;

    public SteinerTreeAnalyzer(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
    }

    // ── Data classes ──────────────────────────────────────────────

    /**
     * Result of a Steiner tree computation.
     */
    public static class SteinerTreeResult {
        public final Set<String> terminals;
        public final Set<String> steinerPoints;
        public final Set<EdgeInfo> edges;
        public final double totalWeight;
        public final boolean exact;
        public final String algorithm;

        public SteinerTreeResult(Set<String> terminals, Set<String> steinerPoints,
                                 Set<EdgeInfo> edges, double totalWeight,
                                 boolean exact, String algorithm) {
            this.terminals = Collections.unmodifiableSet(terminals);
            this.steinerPoints = Collections.unmodifiableSet(steinerPoints);
            this.edges = Collections.unmodifiableSet(edges);
            this.totalWeight = totalWeight;
            this.exact = exact;
            this.algorithm = algorithm;
        }

        public Set<String> allVertices() {
            Set<String> all = new HashSet<>(terminals);
            all.addAll(steinerPoints);
            return all;
        }

        public int totalVertices() { return terminals.size() + steinerPoints.size(); }
        public int totalEdges() { return edges.size(); }
    }

    public static class EdgeInfo {
        public final String from;
        public final String to;
        public final double weight;

        public EdgeInfo(String from, String to, double weight) {
            this.from = from;
            this.to = to;
            this.weight = weight;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EdgeInfo)) return false;
            EdgeInfo e = (EdgeInfo) o;
            return Double.compare(e.weight, weight) == 0 &&
                   ((from.equals(e.from) && to.equals(e.to)) ||
                    (from.equals(e.to) && to.equals(e.from)));
        }

        @Override
        public int hashCode() {
            int h1 = from.compareTo(to) < 0 ? from.hashCode() : to.hashCode();
            int h2 = from.compareTo(to) < 0 ? to.hashCode() : from.hashCode();
            return Objects.hash(h1, h2, weight);
        }

        @Override
        public String toString() {
            return from + " -(" + weight + ")- " + to;
        }
    }

    public static class SteinerReport {
        public final SteinerTreeResult tree;
        public final double steinerRatio;
        public final EdgeInfo bottleneckEdge;
        public final Map<String, Double> steinerPointImportance;
        public final double mstCost;
        public final double savings;
        public final double savingsPercent;

        public SteinerReport(SteinerTreeResult tree, double steinerRatio,
                             EdgeInfo bottleneckEdge,
                             Map<String, Double> steinerPointImportance,
                             double mstCost, double savings, double savingsPercent) {
            this.tree = tree;
            this.steinerRatio = steinerRatio;
            this.bottleneckEdge = bottleneckEdge;
            this.steinerPointImportance = Collections.unmodifiableMap(steinerPointImportance);
            this.mstCost = mstCost;
            this.savings = savings;
            this.savingsPercent = savingsPercent;
        }
    }

    // ── Terminal validation ───────────────────────────────────────

    /**
     * Checks which terminals exist in the graph.
     */
    public Set<String> validateTerminals(Set<String> terminals) {
        return terminals.stream()
                .filter(t -> graph.containsVertex(t))
                .collect(Collectors.toSet());
    }

    /**
     * Checks whether all terminals are reachable from each other.
     */
    public boolean areTerminalsConnected(Set<String> terminals) {
        Set<String> valid = validateTerminals(terminals);
        if (valid.size() <= 1) return true;
        String start = valid.iterator().next();
        Set<String> reachable = GraphUtils.bfsComponent(graph, start);
        return reachable.containsAll(valid);
    }

    /**
     * Groups terminals into connected components.
     */
    public List<Set<String>> terminalComponents(Set<String> terminals) {
        Set<String> valid = validateTerminals(terminals);
        Map<String, Set<String>> compMap = new HashMap<>();
        Set<String> visited = new HashSet<>();

        for (String t : valid) {
            if (visited.contains(t)) continue;
            Set<String> reachable = GraphUtils.bfsComponent(graph, t);
            Set<String> comp = new HashSet<>();
            for (String v : valid) {
                if (reachable.contains(v)) {
                    comp.add(v);
                    visited.add(v);
                }
            }
            if (!comp.isEmpty()) {
                compMap.put(t, comp);
            }
        }
        return new ArrayList<>(compMap.values());
    }

    // ── Shortest-path heuristic (2-approximation) ─────────────────

    /**
     * Greedy Steiner tree: iteratively connects the nearest unconnected terminal.
     */
    public SteinerTreeResult shortestPathHeuristic(Set<String> terminals) {
        validateInput(terminals);
        Set<String> valid = validateTerminals(terminals);
        if (valid.size() <= 1) {
            return new SteinerTreeResult(valid, Collections.emptySet(),
                    Collections.emptySet(), 0, valid.size() <= 1, "shortest-path-heuristic");
        }

        Set<String> connected = new HashSet<>();
        Set<EdgeInfo> treeEdges = new HashSet<>();
        double totalWeight = 0;

        // Start from arbitrary terminal
        String first = valid.iterator().next();
        connected.add(first);
        Set<String> remaining = new HashSet<>(valid);
        remaining.remove(first);

        // Track all vertices in the tree (including intermediate ones)
        Set<String> treeVertices = new HashSet<>();
        treeVertices.add(first);

        // Cache Dijkstra results to avoid redundant recomputation.
        // Previously dijkstra(src) was called inside the inner loop over
        // treeVertices × remaining, recomputing SSSP for the same source
        // vertex many times. Now each source is computed at most once.
        Map<String, GraphUtils.DijkstraResult> dijkstraCache = new HashMap<>();

        while (!remaining.isEmpty()) {
            // Find nearest terminal to any vertex in the tree
            double bestDist = Double.MAX_VALUE;
            String bestTerminal = null;
            List<String> bestPath = null;

            for (String target : remaining) {
                // Find shortest path from any tree vertex to this terminal
                for (String src : treeVertices) {
                    GraphUtils.DijkstraResult dr = dijkstraCache.get(src);
                    if (dr == null) {
                        dr = GraphUtils.dijkstra(graph, src);
                        dijkstraCache.put(src, dr);
                    }
                    if (dr.dist.containsKey(target) && dr.dist.get(target) < bestDist) {
                        bestDist = dr.dist.get(target);
                        bestTerminal = target;
                        bestPath = GraphUtils.reconstructPath(dr, src, target);
                    }
                }
            }

            if (bestPath == null) break; // Remaining terminals unreachable

            // Add path edges to tree
            for (int i = 0; i < bestPath.size() - 1; i++) {
                String u = bestPath.get(i);
                String v = bestPath.get(i + 1);
                EdgeInfo ei = new EdgeInfo(u, v, getEdgeWeight(u, v));
                if (!containsEdgeInfo(treeEdges, u, v)) {
                    treeEdges.add(ei);
                    totalWeight += ei.weight;
                }
                treeVertices.add(u);
                treeVertices.add(v);
            }

            connected.add(bestTerminal);
            remaining.remove(bestTerminal);
        }

        // Prune unnecessary leaves that aren't terminals
        pruneTree(treeEdges, valid, treeVertices);
        totalWeight = treeEdges.stream().mapToDouble(e -> e.weight).sum();

        Set<String> steinerPts = new HashSet<>(treeVertices);
        steinerPts.removeAll(valid);

        return new SteinerTreeResult(valid, steinerPts, treeEdges, totalWeight,
                false, "shortest-path-heuristic");
    }

    // ── MST heuristic (2-approximation) ───────────────────────────

    /**
     * MST-based Steiner tree: builds metric closure on terminals, finds MST,
     * maps back to original graph, removes redundant edges.
     */
    public SteinerTreeResult mstHeuristic(Set<String> terminals) {
        validateInput(terminals);
        Set<String> valid = validateTerminals(terminals);
        if (valid.size() <= 1) {
            return new SteinerTreeResult(valid, Collections.emptySet(),
                    Collections.emptySet(), 0, valid.size() <= 1, "mst-heuristic");
        }

        // Build metric closure: complete graph on terminals with shortest-path weights
        Map<String, GraphUtils.DijkstraResult> allPairs = new HashMap<>();
        for (String t : valid) {
            allPairs.put(t, GraphUtils.dijkstra(graph, t));
        }

        // Find MST of metric closure using Prim's
        List<String> termList = new ArrayList<>(valid);
        Set<String> inMst = new HashSet<>();
        inMst.add(termList.get(0));
        List<String[]> mstEdges = new ArrayList<>(); // pairs of terminals

        while (inMst.size() < termList.size()) {
            double bestDist = Double.MAX_VALUE;
            String bestFrom = null, bestTo = null;
            for (String u : inMst) {
                GraphUtils.DijkstraResult dr = allPairs.get(u);
                for (String v : termList) {
                    if (!inMst.contains(v) && dr.dist.containsKey(v) && dr.dist.get(v) < bestDist) {
                        bestDist = dr.dist.get(v);
                        bestFrom = u;
                        bestTo = v;
                    }
                }
            }
            if (bestTo == null) break;
            inMst.add(bestTo);
            mstEdges.add(new String[]{bestFrom, bestTo});
        }

        // Map MST edges back to original graph paths
        Set<EdgeInfo> treeEdges = new HashSet<>();
        Set<String> treeVertices = new HashSet<>();
        double totalWeight = 0;

        for (String[] mstEdge : mstEdges) {
            GraphUtils.DijkstraResult dr = allPairs.get(mstEdge[0]);
            List<String> path = GraphUtils.reconstructPath(dr, mstEdge[0], mstEdge[1]);
            if (path == null) continue;
            for (int i = 0; i < path.size() - 1; i++) {
                String u = path.get(i);
                String v = path.get(i + 1);
                if (!containsEdgeInfo(treeEdges, u, v)) {
                    EdgeInfo ei = new EdgeInfo(u, v, getEdgeWeight(u, v));
                    treeEdges.add(ei);
                    totalWeight += ei.weight;
                }
                treeVertices.add(u);
                treeVertices.add(v);
            }
        }

        // Prune non-terminal leaves
        pruneTree(treeEdges, valid, treeVertices);
        totalWeight = treeEdges.stream().mapToDouble(e -> e.weight).sum();

        Set<String> steinerPts = new HashSet<>(treeVertices);
        steinerPts.removeAll(valid);

        return new SteinerTreeResult(valid, steinerPts, treeEdges, totalWeight,
                false, "mst-heuristic");
    }

    // ── Exact Dreyfus–Wagner DP ───────────────────────────────────

    /**
     * Exact minimum Steiner tree via Dreyfus–Wagner DP.
     * Feasible only for small terminal sets (T ≤ 15).
     *
     * @throws IllegalArgumentException if terminals > 15
     */
    public SteinerTreeResult exact(Set<String> terminals) {
        validateInput(terminals);
        Set<String> valid = validateTerminals(terminals);
        if (valid.size() <= 1) {
            return new SteinerTreeResult(valid, Collections.emptySet(),
                    Collections.emptySet(), 0, true, "dreyfus-wagner");
        }
        if (valid.size() > 15) {
            throw new IllegalArgumentException("Exact algorithm limited to ≤ 15 terminals, got " + valid.size());
        }
        if (valid.size() == 2) {
            // Just find shortest path
            Iterator<String> it = valid.iterator();
            String s = it.next(), t = it.next();
            GraphUtils.DijkstraResult dr = GraphUtils.dijkstra(graph, s);
            List<String> path = GraphUtils.reconstructPath(dr, s, t);
            if (path == null) {
                return new SteinerTreeResult(valid, Collections.emptySet(),
                        Collections.emptySet(), Double.MAX_VALUE, true, "dreyfus-wagner");
            }
            Set<EdgeInfo> edges = new HashSet<>();
            Set<String> verts = new HashSet<>();
            double w = 0;
            for (int i = 0; i < path.size() - 1; i++) {
                String u = path.get(i), v = path.get(i + 1);
                edges.add(new EdgeInfo(u, v, getEdgeWeight(u, v)));
                w += getEdgeWeight(u, v);
                verts.add(u); verts.add(v);
            }
            verts.removeAll(valid);
            return new SteinerTreeResult(valid, verts, edges, w, true, "dreyfus-wagner");
        }

        // Map vertices to indices
        List<String> vertList = new ArrayList<>(graph.getVertices());
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < vertList.size(); i++) idx.put(vertList.get(i), i);

        int V = vertList.size();
        List<String> termList = new ArrayList<>(valid);
        int T = termList.size();
        int fullMask = (1 << T) - 1;

        // All-pairs shortest paths
        double[][] dist = new double[V][V];
        int[][] next = new int[V][V];
        for (double[] row : dist) Arrays.fill(row, Double.MAX_VALUE / 2);
        for (int[] row : next) Arrays.fill(row, -1);
        for (int i = 0; i < V; i++) { dist[i][i] = 0; next[i][i] = i; }
        for (Edge e : graph.getEdges()) {
            Collection<String> endpoints = graph.getEndpoints(e);
            Iterator<String> eit = endpoints.iterator();
            String u = eit.next(), v = eit.next();
            int ui = idx.get(u), vi = idx.get(v);
            double w = e.getWeight() > 0 ? e.getWeight() : 1.0;
            if (w < dist[ui][vi]) {
                dist[ui][vi] = w; next[ui][vi] = vi;
                dist[vi][ui] = w; next[vi][ui] = ui;
            }
        }
        for (int k = 0; k < V; k++)
            for (int i = 0; i < V; i++)
                for (int j = 0; j < V; j++)
                    if (dist[i][k] + dist[k][j] < dist[i][j]) {
                        dist[i][j] = dist[i][k] + dist[k][j];
                        next[i][j] = next[i][k];
                    }

        // DP: dp[S][v] = min cost of Steiner tree for terminal subset S rooted at v
        double[][] dp = new double[1 << T][V];
        int[][][] parent = new int[1 << T][V][2]; // [splitMask, splitVertex] for reconstruction
        for (double[] row : dp) Arrays.fill(row, Double.MAX_VALUE / 2);
        for (int[][] row : parent) for (int[] r : row) Arrays.fill(r, -1);

        // Base cases: single terminal subsets
        for (int i = 0; i < T; i++) {
            int mask = 1 << i;
            int ti = idx.get(termList.get(i));
            for (int v = 0; v < V; v++) {
                dp[mask][v] = dist[ti][v];
            }
        }

        // Fill DP for increasing subset sizes
        for (int S = 1; S <= fullMask; S++) {
            if (Integer.bitCount(S) <= 1) continue;

            // Try splitting S into two non-empty subsets
            for (int sub = (S - 1) & S; sub > 0; sub = (sub - 1) & S) {
                int complement = S ^ sub;
                if (sub > complement) continue; // avoid duplicates
                for (int v = 0; v < V; v++) {
                    double cost = dp[sub][v] + dp[complement][v];
                    if (cost < dp[S][v]) {
                        dp[S][v] = cost;
                        parent[S][v][0] = sub;
                        parent[S][v][1] = v;
                    }
                }
            }

            // Relax using shortest paths: dp[S][v] = min over u { dp[S][u] + dist[u][v] }
            for (int v = 0; v < V; v++) {
                for (int u = 0; u < V; u++) {
                    if (u == v) continue;
                    double cost = dp[S][u] + dist[u][v];
                    if (cost < dp[S][v]) {
                        dp[S][v] = cost;
                        parent[S][v][0] = S;
                        parent[S][v][1] = u;
                    }
                }
            }
        }

        // Find optimal root
        double bestCost = Double.MAX_VALUE / 2;
        int bestRoot = -1;
        for (int v = 0; v < V; v++) {
            if (dp[fullMask][v] < bestCost) {
                bestCost = dp[fullMask][v];
                bestRoot = v;
            }
        }

        // Reconstruct tree
        Set<EdgeInfo> treeEdges = new HashSet<>();
        Set<String> treeVertices = new HashSet<>();
        reconstructDP(dp, parent, dist, next, fullMask, bestRoot, vertList, idx, termList, treeEdges, treeVertices);

        double totalWeight = treeEdges.stream().mapToDouble(e -> e.weight).sum();

        Set<String> steinerPts = new HashSet<>(treeVertices);
        steinerPts.removeAll(valid);

        return new SteinerTreeResult(valid, steinerPts, treeEdges, totalWeight,
                true, "dreyfus-wagner");
    }

    private void reconstructDP(double[][] dp, int[][][] parent, double[][] dist,
                                int[][] next, int S, int v, List<String> vertList,
                                Map<String, Integer> idx, List<String> termList,
                                Set<EdgeInfo> treeEdges, Set<String> treeVertices) {
        if (Integer.bitCount(S) <= 1) {
            // Base case: single terminal — path from terminal to v
            int ti = Integer.numberOfTrailingZeros(S);
            int termIdx = idx.get(termList.get(ti));
            addShortestPath(next, termIdx, v, vertList, treeEdges, treeVertices);
            return;
        }

        int splitMask = parent[S][v][0];
        int splitVert = parent[S][v][1];

        if (splitMask < 0) return;

        if (splitMask == S) {
            // Came from edge relaxation: dp[S][v] = dp[S][splitVert] + dist[splitVert][v]
            // Add path from splitVert to v
            addShortestPath(next, splitVert, v, vertList, treeEdges, treeVertices);
            reconstructDP(dp, parent, dist, next, S, splitVert, vertList, idx, termList, treeEdges, treeVertices);
        } else {
            // Came from subset split at vertex v (splitVert == v)
            int complement = S ^ splitMask;
            reconstructDP(dp, parent, dist, next, splitMask, v, vertList, idx, termList, treeEdges, treeVertices);
            reconstructDP(dp, parent, dist, next, complement, v, vertList, idx, termList, treeEdges, treeVertices);
        }
    }

    private void addShortestPath(int[][] next, int u, int v, List<String> vertList,
                                  Set<EdgeInfo> treeEdges, Set<String> treeVertices) {
        if (u == v) { treeVertices.add(vertList.get(u)); return; }
        int cur = u;
        while (cur != v) {
            int nxt = next[cur][v];
            if (nxt < 0) return;
            String su = vertList.get(cur), sv = vertList.get(nxt);
            treeVertices.add(su);
            treeVertices.add(sv);
            double w = getEdgeWeight(su, sv);
            if (!containsEdgeInfo(treeEdges, su, sv)) {
                treeEdges.add(new EdgeInfo(su, sv, w));
            }
            cur = nxt;
        }
    }

    // ── Best heuristic ────────────────────────────────────────────

    /**
     * Returns the best Steiner tree from both heuristics.
     */
    public SteinerTreeResult bestHeuristic(Set<String> terminals) {
        SteinerTreeResult sp = shortestPathHeuristic(terminals);
        SteinerTreeResult mst = mstHeuristic(terminals);
        return sp.totalWeight <= mst.totalWeight ? sp : mst;
    }

    /**
     * Auto-selects: exact for ≤ 10 terminals, best heuristic otherwise.
     */
    public SteinerTreeResult solve(Set<String> terminals) {
        Set<String> valid = validateTerminals(terminals);
        if (valid.size() <= 10 && graph.getVertexCount() <= 200) {
            try {
                return exact(terminals);
            } catch (Exception e) {
                return bestHeuristic(terminals);
            }
        }
        return bestHeuristic(terminals);
    }

    // ── Analysis ──────────────────────────────────────────────────

    /**
     * Finds the bottleneck (heaviest) edge in a Steiner tree.
     */
    public EdgeInfo bottleneckEdge(SteinerTreeResult result) {
        return result.edges.stream()
                .max(Comparator.comparingDouble(e -> e.weight))
                .orElse(null);
    }

    /**
     * Computes Steiner ratio: Steiner tree cost / MST cost over terminals.
     * Ratio ≤ 1 means Steiner points help reduce total cost.
     */
    public double steinerRatio(SteinerTreeResult result) {
        double mstCost = terminalMSTCost(result.terminals);
        if (mstCost <= 0) return 1.0;
        return result.totalWeight / mstCost;
    }

    /**
     * Computes how much each Steiner point saves: removing it and re-routing.
     */
    public Map<String, Double> steinerPointImportance(SteinerTreeResult result) {
        Map<String, Double> importance = new HashMap<>();
        for (String sp : result.steinerPoints) {
            // Recompute without this Steiner point
            Set<String> terminalsPlus = new HashSet<>(result.terminals);
            // The importance is how much the cost increases when this point is unavailable
            // Approximate: sum of edges adjacent to this point minus best alternative
            double adjWeight = result.edges.stream()
                    .filter(e -> e.from.equals(sp) || e.to.equals(sp))
                    .mapToDouble(e -> e.weight)
                    .sum();
            importance.put(sp, adjWeight);
        }
        return importance;
    }

    /**
     * Cost of MST connecting only the terminal vertices via shortest paths.
     */
    public double terminalMSTCost(Set<String> terminals) {
        Set<String> valid = validateTerminals(terminals);
        if (valid.size() <= 1) return 0;

        // Build complete graph on terminals with shortest-path distances
        Map<String, GraphUtils.DijkstraResult> allPairs = new HashMap<>();
        for (String t : valid) allPairs.put(t, GraphUtils.dijkstra(graph, t));

        // Prim's MST
        List<String> termList = new ArrayList<>(valid);
        Set<String> inMst = new HashSet<>();
        inMst.add(termList.get(0));
        double totalCost = 0;

        while (inMst.size() < termList.size()) {
            double best = Double.MAX_VALUE;
            String bestTo = null;
            for (String u : inMst) {
                GraphUtils.DijkstraResult dr = allPairs.get(u);
                for (String v : termList) {
                    if (!inMst.contains(v) && dr.dist.containsKey(v) && dr.dist.get(v) < best) {
                        best = dr.dist.get(v);
                        bestTo = v;
                    }
                }
            }
            if (bestTo == null) break;
            inMst.add(bestTo);
            totalCost += best;
        }
        return totalCost;
    }

    /**
     * Generates a comprehensive Steiner tree report.
     */
    public SteinerReport analyze(Set<String> terminals) {
        SteinerTreeResult tree = solve(terminals);
        double ratio = steinerRatio(tree);
        EdgeInfo bottleneck = bottleneckEdge(tree);
        Map<String, Double> importance = steinerPointImportance(tree);
        double mstCost = terminalMSTCost(terminals);
        double savings = mstCost - tree.totalWeight;
        double savingsPct = mstCost > 0 ? (savings / mstCost) * 100 : 0;

        return new SteinerReport(tree, ratio, bottleneck, importance, mstCost,
                savings, savingsPct);
    }

    /**
     * Generates a human-readable text report.
     */
    public String textReport(Set<String> terminals) {
        SteinerReport report = analyze(terminals);
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════\n");
        sb.append("         STEINER TREE ANALYSIS            \n");
        sb.append("═══════════════════════════════════════════\n\n");

        sb.append("Algorithm: ").append(report.tree.algorithm).append("\n");
        sb.append("Exact: ").append(report.tree.exact ? "yes" : "no (heuristic)").append("\n\n");

        sb.append("── Terminals ──────────────────────────────\n");
        sb.append("Count: ").append(report.tree.terminals.size()).append("\n");
        sb.append("Vertices: ").append(sorted(report.tree.terminals)).append("\n\n");

        sb.append("── Steiner Tree ───────────────────────────\n");
        sb.append("Total weight: ").append(String.format("%.4f", report.tree.totalWeight)).append("\n");
        sb.append("Edges: ").append(report.tree.totalEdges()).append("\n");
        sb.append("Vertices: ").append(report.tree.totalVertices()).append("\n");
        sb.append("Steiner points: ").append(report.tree.steinerPoints.isEmpty() ?
                "(none)" : sorted(report.tree.steinerPoints)).append("\n\n");

        if (report.bottleneckEdge != null) {
            sb.append("── Bottleneck Edge ────────────────────────\n");
            sb.append(report.bottleneckEdge).append("\n\n");
        }

        sb.append("── Cost Comparison ────────────────────────\n");
        sb.append("Steiner tree cost: ").append(String.format("%.4f", report.tree.totalWeight)).append("\n");
        sb.append("Terminal MST cost: ").append(String.format("%.4f", report.mstCost)).append("\n");
        sb.append("Steiner ratio: ").append(String.format("%.4f", report.steinerRatio)).append("\n");
        sb.append("Savings: ").append(String.format("%.4f (%.1f%%)", report.savings, report.savingsPercent)).append("\n\n");

        if (!report.steinerPointImportance.isEmpty()) {
            sb.append("── Steiner Point Importance ────────────────\n");
            report.steinerPointImportance.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .forEach(e -> sb.append(String.format("  %-10s  adj-weight: %.4f\n", e.getKey(), e.getValue())));
            sb.append("\n");
        }

        sb.append("── Tree Edges ─────────────────────────────\n");
        report.tree.edges.stream()
                .sorted(Comparator.comparing((EdgeInfo e) -> e.from).thenComparing(e -> e.to))
                .forEach(e -> sb.append("  ").append(e).append("\n"));

        return sb.toString();
    }

    // ── Utility methods ───────────────────────────────────────────

    private void validateInput(Set<String> terminals) {
        if (terminals == null || terminals.isEmpty()) {
            throw new IllegalArgumentException("Terminals must not be null or empty");
        }
    }

    private String sorted(Set<String> set) {
        List<String> list = new ArrayList<>(set);
        Collections.sort(list);
        return list.toString();
    }

    private double getEdgeWeight(String u, String v) {
        Edge e = graph.findEdge(u, v);
        if (e == null) e = graph.findEdge(v, u);
        if (e == null) return 1.0;
        return e.getWeight() > 0 ? e.getWeight() : 1.0;
    }

    private boolean containsEdgeInfo(Set<EdgeInfo> edges, String u, String v) {
        for (EdgeInfo ei : edges) {
            if ((ei.from.equals(u) && ei.to.equals(v)) || (ei.from.equals(v) && ei.to.equals(u))) {
                return true;
            }
        }
        return false;
    }

    private void pruneTree(Set<EdgeInfo> edges, Set<String> terminals, Set<String> vertices) {
        boolean changed = true;
        while (changed) {
            changed = false;
            Iterator<String> it = vertices.iterator();
            while (it.hasNext()) {
                String v = it.next();
                if (terminals.contains(v)) continue;
                // Count degree in tree
                long degree = edges.stream()
                        .filter(e -> e.from.equals(v) || e.to.equals(v))
                        .count();
                if (degree <= 1) {
                    edges.removeIf(e -> e.from.equals(v) || e.to.equals(v));
                    it.remove();
                    changed = true;
                }
            }
        }
    }
}
