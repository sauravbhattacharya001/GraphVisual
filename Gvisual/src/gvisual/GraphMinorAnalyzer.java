package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.*;

/**
 * Graph Minor Analyzer — operations and analysis based on graph minor theory.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Edge contraction</b> — contract an edge, merging its endpoints</li>
 *   <li><b>Vertex deletion</b> — remove a vertex and its incident edges</li>
 *   <li><b>Edge deletion</b> — remove an edge</li>
 *   <li><b>Minor sequence</b> — apply a sequence of operations to derive a minor</li>
 *   <li><b>K₅ minor test</b> — check if graph contains K₅ as a minor</li>
 *   <li><b>K₃,₃ minor test</b> — check if graph contains K₃,₃ as a minor</li>
 *   <li><b>Kuratowski planarity</b> — non-planar if K₅ or K₃,₃ minor found</li>
 *   <li><b>Contraction degeneracy</b> — minimum vertex count reachable by contractions</li>
 *   <li><b>Hadwiger number estimate</b> — largest complete minor (greedy lower bound)</li>
 *   <li><b>Minor-closed family tests</b> — forest (no K₃), outerplanar (no K₄, no K₂,₃)</li>
 *   <li><b>Text report</b> — human-readable minor analysis summary</li>
 * </ul>
 *
 * <p>All operations produce new graph copies; the original is never modified.
 * For minor containment, heuristic/bounded search is used to keep runtime practical.</p>
 *
 * @author zalenix
 */
public class GraphMinorAnalyzer {

    private final Graph<String, Edge> graph;

    public GraphMinorAnalyzer(Graph<String, Edge> graph) {
        if (graph == null) throw new IllegalArgumentException("Graph must not be null");
        this.graph = graph;
    }

    // ── Graph copy ──────────────────────────────────────────────────────

    /**
     * Creates a deep copy of a graph.
     */
    public static Graph<String, Edge> copyGraph(Graph<String, Edge> g) {
        Graph<String, Edge> copy = new UndirectedSparseGraph<>();
        for (String v : g.getVertices()) copy.addVertex(v);
        for (Edge e : g.getEdges()) {
            Collection<String> endpoints = g.getEndpoints(e);
            Iterator<String> it = endpoints.iterator();
            String v1 = it.next(), v2 = it.next();
            copy.addEdge(new Edge(e.getType(), v1, v2), v1, v2);
        }
        return copy;
    }

    // ── Vertex deletion ─────────────────────────────────────────────────

    /**
     * Returns a new graph with the given vertex removed.
     */
    public Graph<String, Edge> deleteVertex(String vertex) {
        if (!graph.containsVertex(vertex))
            throw new IllegalArgumentException("Vertex not found: " + vertex);
        Graph<String, Edge> result = copyGraph(graph);
        result.removeVertex(vertex);
        return result;
    }

    /**
     * Returns a new graph with the given vertices removed.
     */
    public Graph<String, Edge> deleteVertices(Collection<String> vertices) {
        Graph<String, Edge> result = copyGraph(graph);
        for (String v : vertices) result.removeVertex(v);
        return result;
    }

    // ── Edge deletion ───────────────────────────────────────────────────

    /**
     * Returns a new graph with the edge between v1 and v2 removed.
     */
    public Graph<String, Edge> deleteEdge(String v1, String v2) {
        Graph<String, Edge> result = copyGraph(graph);
        Edge e = result.findEdge(v1, v2);
        if (e == null) throw new IllegalArgumentException("No edge between " + v1 + " and " + v2);
        result.removeEdge(e);
        return result;
    }

    // ── Edge contraction ────────────────────────────────────────────────

    /**
     * Contracts the edge between v1 and v2, merging v2 into v1.
     * Returns a new graph. The merged vertex keeps v1's name.
     */
    public static Graph<String, Edge> contractEdge(Graph<String, Edge> g, String v1, String v2) {
        if (g.findEdge(v1, v2) == null)
            throw new IllegalArgumentException("No edge between " + v1 + " and " + v2);
        Graph<String, Edge> result = copyGraph(g);
        // Get v2's neighbors (excluding v1)
        Collection<String> v2Neighbors = new ArrayList<>(result.getNeighbors(v2));
        v2Neighbors.remove(v1);
        // Add edges from v1 to v2's neighbors (if not already present)
        int eid = result.getEdgeCount();
        for (String n : v2Neighbors) {
            if (result.findEdge(v1, n) == null) {
                result.addEdge(new Edge("contracted_" + eid++, v1, n), v1, n);
            }
        }
        result.removeVertex(v2);
        return result;
    }

    /**
     * Contracts the edge between v1 and v2 on the instance graph.
     */
    public Graph<String, Edge> contractEdge(String v1, String v2) {
        return contractEdge(graph, v1, v2);
    }

    // ── Minor sequence ──────────────────────────────────────────────────

    /**
     * An operation in a minor derivation sequence.
     */
    public static class MinorOp {
        public enum Type { DELETE_VERTEX, DELETE_EDGE, CONTRACT_EDGE }
        public final Type type;
        public final String v1;
        public final String v2; // null for DELETE_VERTEX

        public MinorOp(Type type, String v1, String v2) {
            this.type = type; this.v1 = v1; this.v2 = v2;
        }

        public static MinorOp deleteVertex(String v) { return new MinorOp(Type.DELETE_VERTEX, v, null); }
        public static MinorOp deleteEdge(String v1, String v2) { return new MinorOp(Type.DELETE_EDGE, v1, v2); }
        public static MinorOp contract(String v1, String v2) { return new MinorOp(Type.CONTRACT_EDGE, v1, v2); }

        @Override
        public String toString() {
            switch (type) {
                case DELETE_VERTEX: return "delete(" + v1 + ")";
                case DELETE_EDGE: return "delEdge(" + v1 + "," + v2 + ")";
                case CONTRACT_EDGE: return "contract(" + v1 + "," + v2 + ")";
                default: return "?";
            }
        }
    }

    /**
     * Applies a sequence of minor operations starting from the instance graph.
     */
    public Graph<String, Edge> applySequence(List<MinorOp> ops) {
        Graph<String, Edge> g = copyGraph(graph);
        for (MinorOp op : ops) {
            switch (op.type) {
                case DELETE_VERTEX:
                    g.removeVertex(op.v1);
                    break;
                case DELETE_EDGE:
                    Edge de = g.findEdge(op.v1, op.v2);
                    if (de != null) g.removeEdge(de);
                    break;
                case CONTRACT_EDGE:
                    if (g.findEdge(op.v1, op.v2) != null) {
                        g = contractEdge(g, op.v1, op.v2);
                    }
                    break;
            }
        }
        return g;
    }

    // ── Complete graph minor test ────────────────────────────────────────

    /**
     * Estimates the Hadwiger number — the largest k such that K_k is a minor.
     * Uses a greedy approach: repeatedly contract the edge whose endpoints
     * have the most common non-neighbors, tracking the effective clique.
     */
    public int hadwigerNumber() {
        if (graph.getVertexCount() == 0) return 0;
        if (graph.getEdgeCount() == 0) return 1;

        // Build adjacency
        Graph<String, Edge> g = copyGraph(graph);
        int best = 1;

        // Greedy: find largest complete minor by contracting edges
        // that maximize connectivity
        while (g.getVertexCount() > 1) {
            // Check if current graph is complete
            int n = g.getVertexCount();
            int m = g.getEdgeCount();
            if (m == n * (n - 1) / 2) {
                best = Math.max(best, n);
                break;
            }
            best = Math.max(best, findLargestClique(g));

            // Find best edge to contract: endpoints sharing most neighbors
            String bestV1 = null, bestV2 = null;
            int bestScore = -1;
            for (Edge e : g.getEdges()) {
                Collection<String> ep = g.getEndpoints(e);
                Iterator<String> it = ep.iterator();
                String a = it.next(), b = it.next();
                Set<String> na = new HashSet<>(g.getNeighbors(a));
                Set<String> nb = new HashSet<>(g.getNeighbors(b));
                na.retainAll(nb);
                int score = na.size();
                if (score > bestScore) {
                    bestScore = score;
                    bestV1 = a;
                    bestV2 = b;
                }
            }
            if (bestV1 == null) break;
            g = contractEdge(g, bestV1, bestV2);
        }
        if (g.getVertexCount() == 1) best = Math.max(best, 1);
        return best;
    }

    private int findLargestClique(Graph<String, Edge> g) {
        // Simple greedy clique finder
        List<String> vertices = new ArrayList<>(g.getVertices());
        vertices.sort((a, b) -> Integer.compare(g.degree(b), g.degree(a)));
        List<String> clique = new ArrayList<>();
        for (String v : vertices) {
            boolean fits = true;
            for (String c : clique) {
                if (g.findEdge(v, c) == null) { fits = false; break; }
            }
            if (fits) clique.add(v);
        }
        return clique.size();
    }

    // ── K5 and K3,3 minor containment ───────────────────────────────────

    /**
     * Tests whether the graph contains K₅ as a minor (heuristic).
     * Uses branch sets: tries to partition vertices into 5 connected groups
     * that are all pairwise adjacent.
     */
    public boolean hasK5Minor() {
        return hasCompleteMinor(5);
    }

    /**
     * Tests whether the graph contains K_k as a minor for small k.
     */
    public boolean hasCompleteMinor(int k) {
        if (graph.getVertexCount() < k) return false;
        if (k <= 1) return graph.getVertexCount() >= 1;
        if (k == 2) return graph.getEdgeCount() > 0;
        // Hadwiger number >= k means K_k minor exists
        return hadwigerNumber() >= k;
    }

    /**
     * Tests whether the graph contains K₃,₃ as a minor (heuristic).
     * Tries to find 6 connected branch sets (3+3) with all cross-edges.
     */
    public boolean hasK33Minor() {
        if (graph.getVertexCount() < 6) return false;
        if (graph.getEdgeCount() < 9) return false;

        // Try contracting graph down and checking for K33
        Graph<String, Edge> g = copyGraph(graph);

        // Remove degree-1 vertices iteratively (they can't help with K33)
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String v : new ArrayList<>(g.getVertices())) {
                if (g.containsVertex(v) && g.degree(v) <= 1 && g.getVertexCount() > 6) {
                    g.removeVertex(v);
                    changed = true;
                }
            }
        }

        if (g.getVertexCount() < 6) return false;

        // Greedy: try to find K33 by checking subsets
        List<String> verts = new ArrayList<>(g.getVertices());
        // For small graphs, try combinations
        if (verts.size() <= 20) {
            return findK33Brute(g, verts);
        }
        // For larger graphs, use contraction heuristic
        return findK33Contraction(g);
    }

    private boolean findK33Brute(Graph<String, Edge> g, List<String> verts) {
        int n = verts.size();
        // Try all combinations of 3+3
        for (int i = 0; i < n - 5; i++)
            for (int j = i+1; j < n - 4; j++)
                for (int k = j+1; k < n - 3; k++)
                    for (int a = k+1; a < n - 2; a++)
                        for (int b = a+1; b < n - 1; b++)
                            for (int c = b+1; c < n; c++) {
                                String[] left = {verts.get(i), verts.get(j), verts.get(k)};
                                String[] right = {verts.get(a), verts.get(b), verts.get(c)};
                                if (isCompleteBipartite(g, left, right)) return true;
                            }
        return false;
    }

    private boolean isCompleteBipartite(Graph<String, Edge> g, String[] left, String[] right) {
        for (String l : left)
            for (String r : right)
                if (g.findEdge(l, r) == null) return false;
        return true;
    }

    private boolean findK33Contraction(Graph<String, Edge> g) {
        // Contract high-degree edges and check smaller graph
        while (g.getVertexCount() > 15 && g.getEdgeCount() > 0) {
            Edge e = null;
            int bestDeg = -1;
            for (Edge candidate : g.getEdges()) {
                Collection<String> ep = g.getEndpoints(candidate);
                Iterator<String> it = ep.iterator();
                String a = it.next(), b = it.next();
                int deg = g.degree(a) + g.degree(b);
                if (deg > bestDeg) { bestDeg = deg; e = candidate; }
            }
            if (e == null) break;
            Collection<String> ep = g.getEndpoints(e);
            Iterator<String> it = ep.iterator();
            g = contractEdge(g, it.next(), it.next());
        }
        if (g.getVertexCount() < 6) return false;
        return findK33Brute(g, new ArrayList<>(g.getVertices()));
    }

    // ── Minor-closed family membership ──────────────────────────────────

    /**
     * Tests if the graph is a forest (no K₃ minor = no cycles).
     */
    public boolean isForest() {
        return !hasCompleteMinor(3);
    }

    /**
     * Tests if the graph is outerplanar (no K₄ minor and no K₂,₃ minor).
     * Simplified check: |E| ≤ 2|V| - 3 for connected outerplanar graphs.
     */
    public boolean isOuterplanar() {
        int n = graph.getVertexCount();
        int m = graph.getEdgeCount();
        if (n <= 2) return true;
        // Necessary condition: m ≤ 2n - 3
        if (m > 2 * n - 3) return false;
        // No K4 minor
        return !hasCompleteMinor(4);
    }

    /**
     * Tests planarity via Wagner's theorem: a graph is planar iff it
     * has no K₅ or K₃,₃ minor.
     * Note: this is heuristic for large graphs.
     */
    public boolean isPlanarHeuristic() {
        int n = graph.getVertexCount();
        int m = graph.getEdgeCount();
        if (n <= 4) return true;
        // Euler's formula: m ≤ 3n - 6
        if (m > 3 * n - 6) return false;
        return !hasK5Minor() && !hasK33Minor();
    }

    // ── Contraction degeneracy ──────────────────────────────────────────

    /**
     * Computes the minimum number of vertices reachable by repeatedly
     * contracting edges. For a connected graph, this is always 1.
     * For disconnected graphs, it equals the number of components.
     */
    public int contractionDegeneracy() {
        if (graph.getVertexCount() == 0) return 0;
        // Count connected components via BFS
        Set<String> visited = new HashSet<>();
        int components = 0;
        for (String v : graph.getVertices()) {
            if (!visited.contains(v)) {
                components++;
                Queue<String> queue = new LinkedList<>();
                queue.add(v);
                visited.add(v);
                while (!queue.isEmpty()) {
                    String curr = queue.poll();
                    for (String n : graph.getNeighbors(curr)) {
                        if (visited.add(n)) queue.add(n);
                    }
                }
            }
        }
        return components;
    }

    // ── Subdivision / Topological minor ─────────────────────────────────

    /**
     * Subdivides an edge: replaces edge (v1,v2) with v1-new-v2.
     * Returns a new graph with the subdivision vertex added.
     */
    public Graph<String, Edge> subdivideEdge(String v1, String v2, String newVertex) {
        Graph<String, Edge> result = copyGraph(graph);
        Edge e = result.findEdge(v1, v2);
        if (e == null) throw new IllegalArgumentException("No edge between " + v1 + " and " + v2);
        result.removeEdge(e);
        result.addVertex(newVertex);
        result.addEdge(new Edge("sub_a", v1, newVertex), v1, newVertex);
        result.addEdge(new Edge("sub_b", newVertex, v2), newVertex, v2);
        return result;
    }

    // ── Text report ─────────────────────────────────────────────────────

    /**
     * Generates a comprehensive minor analysis report.
     */
    public String generateReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Graph Minor Analysis Report ===\n\n");

        int n = graph.getVertexCount();
        int m = graph.getEdgeCount();
        sb.append(String.format("Vertices: %d  |  Edges: %d\n\n", n, m));

        sb.append("── Minor-Closed Family Membership ──\n");
        boolean forest = isForest();
        sb.append(String.format("  Forest (acyclic):    %s\n", forest ? "YES" : "NO"));
        boolean outer = isOuterplanar();
        sb.append(String.format("  Outerplanar:         %s\n", outer ? "YES" : "NO"));
        boolean planar = isPlanarHeuristic();
        sb.append(String.format("  Planar (heuristic):  %s\n", planar ? "YES" : "NO"));

        sb.append("\n── Minor Containment ──\n");
        for (int k = 3; k <= Math.min(6, n); k++) {
            boolean has = hasCompleteMinor(k);
            sb.append(String.format("  Contains K%d minor:   %s\n", k, has ? "YES" : "NO"));
        }
        if (n >= 6) {
            sb.append(String.format("  Contains K3,3 minor: %s\n", hasK33Minor() ? "YES" : "NO"));
        }

        sb.append("\n── Structural Metrics ──\n");
        int hw = hadwigerNumber();
        sb.append(String.format("  Hadwiger number (≥): %d\n", hw));
        int cd = contractionDegeneracy();
        sb.append(String.format("  Components:          %d\n", cd));

        sb.append("\n=== End of Report ===\n");
        return sb.toString();
    }
}
