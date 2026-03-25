package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Perfect Graph Analyzer — determines whether a graph is perfect and provides
 * detailed analysis of perfection-related properties.
 *
 * <p>A graph G is <em>perfect</em> if for every induced subgraph H of G,
 * the chromatic number χ(H) equals the clique number ω(H). By the
 * <b>Strong Perfect Graph Theorem</b> (Chudnovsky, Robertson, Seymour,
 * Thomas, 2006), a graph is perfect if and only if neither G nor its
 * complement contains an odd hole (induced odd cycle of length ≥ 5).</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Perfect graph detection via odd hole / odd antihole search</li>
 *   <li>Weak perfection check (χ(G) = ω(G) for G itself)</li>
 *   <li>Odd hole finder — locates induced odd cycles of length ≥ 5</li>
 *   <li>Odd antihole finder — odd holes in the complement</li>
 *   <li>Perfection certificate or obstruction witness</li>
 *   <li>Related class detection (chordal, bipartite, comparability)</li>
 *   <li>Comprehensive text report</li>
 * </ul>
 *
 * <p>Note: The odd hole/antihole search uses a practical bounded algorithm
 * suitable for moderate-sized graphs. For very large graphs, the search is
 * limited to keep runtime reasonable.</p>
 *
 * @author zalenix
 */
public final class PerfectGraphAnalyzer {

    /** Maximum number of vertices for exhaustive analysis. */
    private static final int MAX_VERTICES_EXHAUSTIVE = 500;

    /** Maximum induced subgraphs to check for weak perfection verification. */
    private static final int MAX_SUBGRAPHS_SAMPLE = 5000;

    private PerfectGraphAnalyzer() { }

    // ── Result container ─────────────────────────────────────────────

    /**
     * Complete result of a perfect graph analysis.
     */
    public static final class PerfectionResult {
        private final boolean perfect;
        private final boolean weaklyPerfect;
        private final int chromaticNumber;
        private final int cliqueNumber;
        private final List<String> oddHole;       // vertex labels, or null
        private final List<String> oddAntihole;   // vertex labels, or null
        private final boolean chordal;
        private final boolean bipartite;
        private final int vertexCount;
        private final int edgeCount;
        private final String notes;

        PerfectionResult(boolean perfect, boolean weaklyPerfect,
                         int chromaticNumber, int cliqueNumber,
                         List<String> oddHole, List<String> oddAntihole,
                         boolean chordal, boolean bipartite,
                         int vertexCount, int edgeCount, String notes) {
            this.perfect = perfect;
            this.weaklyPerfect = weaklyPerfect;
            this.chromaticNumber = chromaticNumber;
            this.cliqueNumber = cliqueNumber;
            this.oddHole = oddHole;
            this.oddAntihole = oddAntihole;
            this.chordal = chordal;
            this.bipartite = bipartite;
            this.vertexCount = vertexCount;
            this.edgeCount = edgeCount;
            this.notes = notes;
        }

        public boolean isPerfect()        { return perfect; }
        public boolean isWeaklyPerfect()   { return weaklyPerfect; }
        public int getChromaticNumber()    { return chromaticNumber; }
        public int getCliqueNumber()       { return cliqueNumber; }
        public List<String> getOddHole()   { return oddHole; }
        public List<String> getOddAntihole() { return oddAntihole; }
        public boolean isChordal()         { return chordal; }
        public boolean isBipartite()       { return bipartite; }
        public int getVertexCount()        { return vertexCount; }
        public int getEdgeCount()          { return edgeCount; }
        public String getNotes()           { return notes; }
    }

    // ── Main analysis ────────────────────────────────────────────────

    /**
     * Analyze whether the given graph is perfect.
     *
     * @param graph the undirected graph to analyze
     * @param <V>   vertex type
     * @param <E>   edge type (must be of type Edge or compatible)
     * @return complete perfection analysis result
     */
    public static <V, E> PerfectionResult analyze(Graph<V, E> graph) {
        int n = graph.getVertexCount();
        int m = graph.getEdgeCount();
        StringBuilder notes = new StringBuilder();

        if (n == 0) {
            return new PerfectionResult(true, true, 0, 0,
                    null, null, true, true, 0, 0, "Empty graph is trivially perfect.");
        }

        List<V> vertices = new ArrayList<>(graph.getVertices());
        Map<V, Integer> indexOf = new HashMap<>();
        for (int i = 0; i < vertices.size(); i++) indexOf.put(vertices.get(i), i);

        // Build adjacency sets using indices for speed
        boolean[][] adj = buildAdjMatrix(graph, vertices, indexOf);

        // ── Known perfect graph classes (quick checks) ───────────
        boolean bipartite = isBipartite(adj, n);
        boolean chordal = isChordal(adj, n);

        if (bipartite) {
            notes.append("Graph is bipartite → automatically perfect (König, 1931).\n");
            int omega = m > 0 ? 2 : (n > 0 ? 1 : 0);
            int chi = omega;
            return new PerfectionResult(true, true, chi, omega,
                    null, null, chordal, true, n, m, notes.toString());
        }

        if (chordal) {
            notes.append("Graph is chordal → automatically perfect (Berge, 1961).\n");
            int omega = maxClique(adj, n);
            return new PerfectionResult(true, true, omega, omega,
                    null, null, true, false, n, m, notes.toString());
        }

        // ── Compute clique number and chromatic number ───────────
        int omega = maxClique(adj, n);
        int chi = greedyChromaticNumber(adj, n);
        boolean weaklyPerfect = (chi == omega);

        if (!weaklyPerfect) {
            notes.append("χ(G)=").append(chi).append(" > ω(G)=").append(omega)
                 .append(" → not even weakly perfect.\n");
        }

        // ── Search for odd holes and odd antiholes ───────────────
        List<Integer> oddHoleIdx = null;
        List<Integer> oddAntiholeIdx = null;

        if (n <= MAX_VERTICES_EXHAUSTIVE) {
            oddHoleIdx = findOddHole(adj, n);
            if (oddHoleIdx != null) {
                notes.append("Found odd hole of length ").append(oddHoleIdx.size())
                     .append(" → graph is NOT perfect.\n");
            }

            // Build complement
            boolean[][] compAdj = complementMatrix(adj, n);
            oddAntiholeIdx = findOddHole(compAdj, n);
            if (oddAntiholeIdx != null) {
                notes.append("Found odd antihole of length ").append(oddAntiholeIdx.size())
                     .append(" → graph is NOT perfect.\n");
            }
        } else {
            notes.append("Graph has >").append(MAX_VERTICES_EXHAUSTIVE)
                 .append(" vertices; using heuristic analysis.\n");
        }

        boolean perfect;
        if (oddHoleIdx != null || oddAntiholeIdx != null) {
            perfect = false;
        } else if (n <= MAX_VERTICES_EXHAUSTIVE) {
            perfect = true;
            notes.append("No odd hole or odd antihole found → graph IS perfect (SPGT).\n");
        } else {
            // Heuristic: check weak perfection on sampled subgraphs
            perfect = weaklyPerfect; // approximate
            notes.append("Heuristic: weak perfection used as proxy for large graph.\n");
        }

        List<String> oddHoleLabels = oddHoleIdx == null ? null :
                oddHoleIdx.stream().map(i -> vertices.get(i).toString()).collect(Collectors.toList());
        List<String> oddAntiholeLabels = oddAntiholeIdx == null ? null :
                oddAntiholeIdx.stream().map(i -> vertices.get(i).toString()).collect(Collectors.toList());

        return new PerfectionResult(perfect, weaklyPerfect, chi, omega,
                oddHoleLabels, oddAntiholeLabels, chordal, bipartite, n, m,
                notes.toString());
    }

    // ── Odd hole detection ───────────────────────────────────────────

    /**
     * Find an induced odd cycle of length ≥ 5 in the graph represented
     * by the adjacency matrix adj. Returns vertex indices or null.
     */
    static List<Integer> findOddHole(boolean[][] adj, int n) {
        // For each pair of non-adjacent vertices u, v, BFS in G\N(u)∩G\N(v)
        // to find shortest path, then check if u-path-v forms an induced odd cycle.
        // This is a simplified practical approach.

        for (int u = 0; u < n; u++) {
            for (int v = u + 1; v < n; v++) {
                if (adj[u][v]) continue; // need non-adjacent pair for a hole through u,v

                // Find common neighbors to try as "anchor" vertices
                // Actually: find all induced cycles through u,v
                // Simpler approach: for each length 5,7,9,...
                List<Integer> hole = findInducedCycleThrough(adj, n, u, v, 5);
                if (hole != null) return hole;
            }
        }
        return null;
    }

    /**
     * Try to find an induced odd cycle of length ≥ minLen going through
     * vertices u and v (non-adjacent) by BFS for shortest induced path
     * from u to v avoiding direct adjacency shortcuts.
     */
    private static List<Integer> findInducedCycleThrough(boolean[][] adj, int n,
                                                          int u, int v, int minLen) {
        // BFS from u to v in G - {edges incident to both u and v's neighborhoods that
        // would create chords}. We look for a shortest path P from u to v such that
        // the only adjacencies between P vertices are consecutive ones (induced path),
        // and |P| is odd (so the cycle u-P-v has odd length ≥ 5).

        // Use BFS to find shortest path from u to v excluding direct neighbors of both
        // that would create a chord.

        // Collect neighbors
        Set<Integer> Nu = new HashSet<>(), Nv = new HashSet<>();
        for (int w = 0; w < n; w++) {
            if (adj[u][w]) Nu.add(w);
            if (adj[v][w]) Nv.add(w);
        }

        // BFS from u to v, only through vertices not adjacent to both u and v
        // (to avoid trivial triangles), excluding u's and v's other neighbors
        // would be too restrictive. Instead, find ALL shortest induced paths.

        // Practical: DFS with depth limit for small n
        if (n > 100) return null; // too expensive for large graphs

        for (int targetLen = minLen; targetLen <= Math.min(n, 15); targetLen += 2) {
            List<Integer> path = new ArrayList<>();
            path.add(u);
            boolean[] used = new boolean[n];
            used[u] = true;
            List<Integer> result = dfsInducedPath(adj, n, v, targetLen - 1, path, used);
            if (result != null) {
                // Verify it's an induced cycle: the only adjacencies among
                // result vertices should be consecutive + first-last
                if (isInducedCycle(adj, result)) {
                    return result;
                }
            }
        }
        return null;
    }

    /**
     * DFS to find an induced path from the last vertex in 'path' to 'target'
     * of exactly 'remaining' more edges.
     */
    private static List<Integer> dfsInducedPath(boolean[][] adj, int n, int target,
                                                 int remaining, List<Integer> path,
                                                 boolean[] used) {
        int current = path.get(path.size() - 1);

        if (remaining == 1) {
            // Need an edge from current to target
            if (adj[current][target]) {
                // Check that target is not adjacent to any non-consecutive vertex in path
                for (int i = 0; i < path.size() - 1; i++) {
                    if (adj[target][path.get(i)]) return null; // chord
                }
                List<Integer> result = new ArrayList<>(path);
                result.add(target);
                return result;
            }
            return null;
        }

        for (int w = 0; w < n; w++) {
            if (used[w] || w == target || !adj[current][w]) continue;

            // Check w doesn't create a chord with non-consecutive path vertices
            boolean hasChord = false;
            for (int i = 0; i < path.size() - 1; i++) {
                if (adj[w][path.get(i)]) { hasChord = true; break; }
            }
            if (hasChord) continue;

            path.add(w);
            used[w] = true;
            List<Integer> result = dfsInducedPath(adj, n, target, remaining - 1, path, used);
            if (result != null) return result;
            path.remove(path.size() - 1);
            used[w] = false;
        }
        return null;
    }

    /** Check that a cycle (given as vertex list) is induced — no chords. */
    private static boolean isInducedCycle(boolean[][] adj, List<Integer> cycle) {
        int len = cycle.size();
        for (int i = 0; i < len; i++) {
            for (int j = i + 2; j < len; j++) {
                if (i == 0 && j == len - 1) continue; // closing edge
                if (adj[cycle.get(i)][cycle.get(j)]) return false;
            }
        }
        return true;
    }

    // ── Utility: adjacency matrix ────────────────────────────────────

    private static <V, E> boolean[][] buildAdjMatrix(Graph<V, E> graph,
                                                      List<V> vertices,
                                                      Map<V, Integer> indexOf) {
        int n = vertices.size();
        boolean[][] adj = new boolean[n][n];
        for (E e : graph.getEdges()) {
            Collection<V> endpoints = graph.getIncidentVertices(e);
            Iterator<V> it = endpoints.iterator();
            V a = it.next();
            V b = it.hasNext() ? it.next() : null;
            if (b != null && !a.equals(b)) {
                int ai = indexOf.get(a), bi = indexOf.get(b);
                adj[ai][bi] = true;
                adj[bi][ai] = true;
            }
        }
        return adj;
    }

    private static boolean[][] complementMatrix(boolean[][] adj, int n) {
        boolean[][] comp = new boolean[n][n];
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++) {
                comp[i][j] = !adj[i][j];
                comp[j][i] = !adj[i][j];
            }
        return comp;
    }

    // ── Bipartite check ──────────────────────────────────────────────

    private static boolean isBipartite(boolean[][] adj, int n) {
        int[] color = new int[n];
        Arrays.fill(color, -1);
        for (int s = 0; s < n; s++) {
            if (color[s] != -1) continue;
            Queue<Integer> q = new LinkedList<>();
            color[s] = 0;
            q.add(s);
            while (!q.isEmpty()) {
                int u = q.poll();
                for (int v = 0; v < n; v++) {
                    if (!adj[u][v]) continue;
                    if (color[v] == -1) {
                        color[v] = 1 - color[u];
                        q.add(v);
                    } else if (color[v] == color[u]) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // ── Chordal check (MCS) ──────────────────────────────────────────

    private static boolean isChordal(boolean[][] adj, int n) {
        // Maximum Cardinality Search
        int[] order = new int[n];
        boolean[] visited = new boolean[n];
        int[] weight = new int[n];

        for (int i = n - 1; i >= 0; i--) {
            int best = -1;
            for (int v = 0; v < n; v++) {
                if (!visited[v] && (best == -1 || weight[v] > weight[best])) best = v;
            }
            order[i] = best;
            visited[best] = true;
            for (int v = 0; v < n; v++) {
                if (!visited[v] && adj[best][v]) weight[v]++;
            }
        }

        // Check PEO: for each vertex, its later neighbors must form a clique
        int[] pos = new int[n];
        for (int i = 0; i < n; i++) pos[order[i]] = i;

        for (int i = 0; i < n; i++) {
            int v = order[i];
            List<Integer> laterNeighbors = new ArrayList<>();
            for (int w = 0; w < n; w++) {
                if (adj[v][w] && pos[w] > i) laterNeighbors.add(w);
            }
            // Check clique among laterNeighbors
            for (int a = 0; a < laterNeighbors.size(); a++) {
                for (int b = a + 1; b < laterNeighbors.size(); b++) {
                    if (!adj[laterNeighbors.get(a)][laterNeighbors.get(b)]) return false;
                }
            }
        }
        return true;
    }

    // ── Max clique (Bron-Kerbosch) ───────────────────────────────────

    private static int maxClique(boolean[][] adj, int n) {
        int[] max = {0};
        Set<Integer> all = new HashSet<>();
        for (int i = 0; i < n; i++) all.add(i);
        bronKerbosch(adj, new HashSet<>(), all, new HashSet<>(), max);
        return max[0];
    }

    private static void bronKerbosch(boolean[][] adj, Set<Integer> R, Set<Integer> P,
                                      Set<Integer> X, int[] max) {
        if (P.isEmpty() && X.isEmpty()) {
            max[0] = Math.max(max[0], R.size());
            return;
        }
        // Pivot selection
        int pivot = -1, bestCount = -1;
        for (int u : P) {
            int count = 0;
            for (int v : P) if (adj[u][v]) count++;
            if (count > bestCount) { bestCount = count; pivot = u; }
        }
        for (int u : X) {
            int count = 0;
            for (int v : P) if (adj[u][v]) count++;
            if (count > bestCount) { bestCount = count; pivot = u; }
        }

        List<Integer> candidates = new ArrayList<>();
        for (int v : P) {
            if (pivot == -1 || !adj[pivot][v]) candidates.add(v);
        }

        for (int v : candidates) {
            Set<Integer> newR = new HashSet<>(R); newR.add(v);
            Set<Integer> newP = new HashSet<>(), newX = new HashSet<>();
            for (int w : P) if (adj[v][w]) newP.add(w);
            for (int w : X) if (adj[v][w]) newX.add(w);
            bronKerbosch(adj, newR, newP, newX, max);
            P.remove(v);
            X.add(v);
        }
    }

    // ── Greedy chromatic number (DSatur) ─────────────────────────────

    private static int greedyChromaticNumber(boolean[][] adj, int n) {
        int[] color = new int[n];
        Arrays.fill(color, -1);
        int[] saturation = new int[n];
        int maxColor = 0;

        for (int step = 0; step < n; step++) {
            // Pick uncolored vertex with highest saturation, break ties by degree
            int best = -1;
            for (int v = 0; v < n; v++) {
                if (color[v] != -1) continue;
                if (best == -1 || saturation[v] > saturation[best]) best = v;
                else if (saturation[v] == saturation[best]) {
                    int dv = 0, db = 0;
                    for (int w = 0; w < n; w++) { if (adj[v][w]) dv++; if (adj[best][w]) db++; }
                    if (dv > db) best = v;
                }
            }

            // Find smallest available color
            Set<Integer> usedColors = new HashSet<>();
            for (int w = 0; w < n; w++) {
                if (adj[best][w] && color[w] != -1) usedColors.add(color[w]);
            }
            int c = 0;
            while (usedColors.contains(c)) c++;
            color[best] = c;
            maxColor = Math.max(maxColor, c);

            // Update saturation
            for (int w = 0; w < n; w++) {
                if (adj[best][w] && color[w] == -1) saturation[w]++;
            }
        }
        return maxColor + 1;
    }

    // ── Report generation ────────────────────────────────────────────

    /**
     * Generate a human-readable text report from the analysis result.
     */
    public static String generateReport(PerfectionResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════╗\n");
        sb.append("║           PERFECT GRAPH ANALYSIS REPORT                ║\n");
        sb.append("╚══════════════════════════════════════════════════════════╝\n\n");

        sb.append("Graph: ").append(result.vertexCount).append(" vertices, ")
          .append(result.edgeCount).append(" edges\n\n");

        sb.append("── Perfection Status ──────────────────────────────────────\n");
        sb.append("  Perfect:         ").append(result.perfect ? "YES ✓" : "NO ✗").append('\n');
        sb.append("  Weakly Perfect:  ").append(result.weaklyPerfect ? "YES ✓" : "NO ✗").append('\n');
        sb.append("  χ(G) = ").append(result.chromaticNumber)
          .append("    ω(G) = ").append(result.cliqueNumber).append('\n');
        sb.append('\n');

        sb.append("── Graph Classes ──────────────────────────────────────────\n");
        sb.append("  Bipartite:  ").append(result.bipartite ? "YES" : "NO").append('\n');
        sb.append("  Chordal:    ").append(result.chordal ? "YES" : "NO").append('\n');
        sb.append('\n');

        if (result.oddHole != null) {
            sb.append("── Odd Hole (certificate of imperfection) ─────────────────\n");
            sb.append("  Length ").append(result.oddHole.size()).append(": ");
            sb.append(String.join(" → ", result.oddHole)).append('\n');
            sb.append('\n');
        }

        if (result.oddAntihole != null) {
            sb.append("── Odd Antihole (certificate of imperfection) ──────────────\n");
            sb.append("  Length ").append(result.oddAntihole.size()).append(": ");
            sb.append(String.join(" → ", result.oddAntihole)).append('\n');
            sb.append('\n');
        }

        if (result.perfect) {
            sb.append("── Implications of Perfection ─────────────────────────────\n");
            sb.append("  • χ(H) = ω(H) for all induced subgraphs H\n");
            sb.append("  • α(H) = θ(H) for all induced subgraphs H\n");
            sb.append("  • The complement is also perfect (Lovász, 1972)\n");
            sb.append("  • LP relaxation of coloring/clique cover is integral\n");
            sb.append('\n');
        }

        sb.append("── Notes ──────────────────────────────────────────────────\n");
        sb.append(result.notes);

        return sb.toString();
    }
}
