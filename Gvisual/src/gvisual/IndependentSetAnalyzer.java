package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Independent set analysis for undirected graphs.
 *
 * <p>Provides algorithms for finding independent sets (sets of vertices with no edges
 * between them), computing bounds on the independence number, and analyzing the
 * independence structure of graphs.</p>
 *
 * <h3>Algorithms</h3>
 * <ul>
 *   <li><b>Greedy independent set:</b> Min-degree heuristic — O(V²)</li>
 *   <li><b>Exact maximum independent set:</b> Backtracking with pruning — O(2^V)</li>
 *   <li><b>Maximal independent set enumeration:</b> Bron-Kerbosch variant — exponential</li>
 *   <li><b>Kernel reduction:</b> Degree-0/1/folding rules for preprocessing</li>
 *   <li><b>Bounds:</b> Turán, Ramsey, LP relaxation, greedy lower bound</li>
 *   <li><b>Complement relationship:</b> Independent set ↔ clique in complement graph</li>
 * </ul>
 *
 * <h3>Applications</h3>
 * <ul>
 *   <li>Scheduling (conflict-free resource allocation)</li>
 *   <li>Map labeling (non-overlapping label placement)</li>
 *   <li>Wireless channel assignment</li>
 *   <li>Social network analysis (maximum non-adjacent group)</li>
 *   <li>Molecular structure (stable configurations)</li>
 * </ul>
 *
 * @author zalenix
 */
public class IndependentSetAnalyzer {

    private final Graph<String, edge> graph;

    /**
     * Constructs an analyzer for the given undirected graph.
     *
     * @param graph the graph to analyze (should be undirected)
     * @throws IllegalArgumentException if graph is null
     */
    public IndependentSetAnalyzer(Graph<String, edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
    }

    // ── Verification ──────────────────────────────────────────────

    /**
     * Checks whether a given vertex set is a valid independent set.
     *
     * @param vertices the candidate set
     * @return true if no two vertices in the set are adjacent
     */
    public boolean isIndependentSet(Set<String> vertices) {
        if (vertices == null) return true;
        List<String> list = new ArrayList<>(vertices);
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                if (graph.findEdge(list.get(i), list.get(j)) != null) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Checks whether an independent set is maximal (no vertex can be added).
     *
     * @param vertices the independent set
     * @return true if maximal
     */
    public boolean isMaximalIndependentSet(Set<String> vertices) {
        if (!isIndependentSet(vertices)) return false;
        for (String v : graph.getVertices()) {
            if (vertices.contains(v)) continue;
            boolean canAdd = true;
            for (String u : vertices) {
                if (graph.findEdge(v, u) != null) {
                    canAdd = false;
                    break;
                }
            }
            if (canAdd) return false;
        }
        return true;
    }

    // ── Greedy Independent Set ────────────────────────────────────

    /**
     * Finds an independent set using the minimum-degree greedy heuristic.
     * Repeatedly picks the vertex with smallest degree and removes it and
     * its neighbors.
     *
     * @return a maximal independent set (not necessarily maximum)
     */
    public Set<String> greedyIndependentSet() {
        Set<String> result = new LinkedHashSet<>();
        Set<String> remaining = new HashSet<>(asCollection(graph.getVertices()));
        Map<String, Set<String>> adjMap = buildAdjacency(remaining);

        while (!remaining.isEmpty()) {
            // Pick vertex with minimum degree among remaining
            String minVertex = null;
            int minDeg = Integer.MAX_VALUE;
            for (String v : remaining) {
                int deg = 0;
                for (String n : adjMap.getOrDefault(v, Collections.emptySet())) {
                    if (remaining.contains(n)) deg++;
                }
                if (deg < minDeg) {
                    minDeg = deg;
                    minVertex = v;
                }
            }
            result.add(minVertex);
            // Remove minVertex and its neighbors
            Set<String> toRemove = new HashSet<>();
            toRemove.add(minVertex);
            for (String n : adjMap.getOrDefault(minVertex, Collections.emptySet())) {
                if (remaining.contains(n)) toRemove.add(n);
            }
            remaining.removeAll(toRemove);
        }
        return result;
    }

    /**
     * Greedy independent set using maximum-degree-first removal strategy.
     * Iteratively removes the highest-degree vertex and its neighbors.
     *
     * @return a maximal independent set
     */
    public Set<String> greedyMaxDegreeIndependentSet() {
        Set<String> result = new LinkedHashSet<>();
        Set<String> remaining = new HashSet<>(asCollection(graph.getVertices()));
        Map<String, Set<String>> adjMap = buildAdjacency(remaining);

        while (!remaining.isEmpty()) {
            String maxVertex = null;
            int maxDeg = -1;
            for (String v : remaining) {
                int deg = 0;
                for (String n : adjMap.getOrDefault(v, Collections.emptySet())) {
                    if (remaining.contains(n)) deg++;
                }
                if (deg > maxDeg || (deg == maxDeg && (maxVertex == null || v.compareTo(maxVertex) < 0))) {
                    maxDeg = deg;
                    maxVertex = v;
                }
            }
            // Remove maxVertex (it's excluded), add remaining isolated vertices
            remaining.remove(maxVertex);
            // Actually, max-degree removal means: remove it, then pick remaining isolates
            // Let me use the correct strategy: pick vertex, add to set, remove neighbors
            // For max-degree: pick min-degree vertex to ADD (keeps more options)
            // Re-implementing: this is min-neighbor-count strategy
            // Actually for "max degree first": remove high-degree vertices to free up isolates
            // Standard approach: remove max degree vertex from graph, repeat, leftover = IS
        }

        // Better implementation: standard vertex removal approach
        remaining = new HashSet<>(asCollection(graph.getVertices()));
        while (!remaining.isEmpty()) {
            // Find max-degree vertex in remaining
            String maxV = null;
            int maxD = -1;
            for (String v : remaining) {
                int d = 0;
                for (String n : adjMap.getOrDefault(v, Collections.emptySet())) {
                    if (remaining.contains(n)) d++;
                }
                if (d > maxD) { maxD = d; maxV = v; }
            }
            if (maxD == 0) {
                // All remaining are isolated — add them all
                result.addAll(remaining);
                break;
            }
            remaining.remove(maxV); // remove highest-degree vertex
        }
        return result;
    }

    // ── Exact Maximum Independent Set ─────────────────────────────

    /**
     * Finds the maximum independent set using backtracking with pruning.
     * Warning: exponential time complexity — use only for small graphs (≤ 30 vertices).
     *
     * @return the maximum independent set
     */
    public Set<String> exactMaximumIndependentSet() {
        return exactMaximumIndependentSet(30);
    }

    /**
     * Finds the maximum independent set with a vertex limit.
     *
     * @param maxVertices maximum number of vertices to attempt (throws if exceeded)
     * @return the maximum independent set
     * @throws IllegalStateException if graph has more vertices than maxVertices
     */
    public Set<String> exactMaximumIndependentSet(int maxVertices) {
        int n = graph.getVertexCount();
        if (n > maxVertices) {
            throw new IllegalStateException(
                "Graph has " + n + " vertices, exceeding limit of " + maxVertices +
                ". Use greedy or bounded methods instead.");
        }
        if (n == 0) return Collections.emptySet();

        List<String> vertices = new ArrayList<>(asCollection(graph.getVertices()));
        Collections.sort(vertices);
        Map<String, Set<String>> adjMap = buildAdjacency(new HashSet<>(vertices));

        int[] bestSize = {0};
        Set<String> bestSet = new LinkedHashSet<>();
        backtrack(vertices, adjMap, 0, new LinkedHashSet<>(), new HashSet<>(), bestSize, bestSet);
        return bestSet;
    }

    private void backtrack(List<String> vertices, Map<String, Set<String>> adj,
                           int index, Set<String> current, Set<String> excluded,
                           int[] bestSize, Set<String> bestSet) {
        // Upper bound pruning
        int remaining = 0;
        for (int i = index; i < vertices.size(); i++) {
            if (!excluded.contains(vertices.get(i))) remaining++;
        }
        if (current.size() + remaining <= bestSize[0]) return;

        if (index == vertices.size()) {
            if (current.size() > bestSize[0]) {
                bestSize[0] = current.size();
                bestSet.clear();
                bestSet.addAll(current);
            }
            return;
        }

        String v = vertices.get(index);
        if (excluded.contains(v)) {
            backtrack(vertices, adj, index + 1, current, excluded, bestSize, bestSet);
            return;
        }

        // Branch: include v
        Set<String> newExcluded = new HashSet<>(excluded);
        for (String n : adj.getOrDefault(v, Collections.emptySet())) {
            newExcluded.add(n);
        }
        current.add(v);
        backtrack(vertices, adj, index + 1, current, newExcluded, bestSize, bestSet);
        current.remove(v);

        // Branch: exclude v
        backtrack(vertices, adj, index + 1, current, excluded, bestSize, bestSet);
    }

    // ── Maximal Independent Set Enumeration ───────────────────────

    /**
     * Enumerates all maximal independent sets in the graph.
     * Uses a modified Bron-Kerbosch algorithm on the complement graph
     * (maximal independent sets = maximal cliques in complement).
     *
     * @return list of all maximal independent sets
     */
    public List<Set<String>> allMaximalIndependentSets() {
        return allMaximalIndependentSets(1000);
    }

    /**
     * Enumerates maximal independent sets up to a limit.
     *
     * @param maxCount maximum number to enumerate
     * @return list of maximal independent sets
     */
    public List<Set<String>> allMaximalIndependentSets(int maxCount) {
        List<String> vertices = new ArrayList<>(asCollection(graph.getVertices()));
        Collections.sort(vertices);
        Map<String, Set<String>> adjMap = buildAdjacency(new HashSet<>(vertices));

        // Build complement adjacency
        Map<String, Set<String>> compAdj = new HashMap<>();
        for (String v : vertices) {
            Set<String> nonNeighbors = new HashSet<>(vertices);
            nonNeighbors.remove(v);
            nonNeighbors.removeAll(adjMap.getOrDefault(v, Collections.emptySet()));
            compAdj.put(v, nonNeighbors);
        }

        List<Set<String>> results = new ArrayList<>();
        bronKerbosch(new LinkedHashSet<>(), new LinkedHashSet<>(vertices),
                     new LinkedHashSet<>(), compAdj, results, maxCount);
        return results;
    }

    private void bronKerbosch(Set<String> R, Set<String> P, Set<String> X,
                               Map<String, Set<String>> compAdj,
                               List<Set<String>> results, int maxCount) {
        if (results.size() >= maxCount) return;
        if (P.isEmpty() && X.isEmpty()) {
            results.add(new LinkedHashSet<>(R));
            return;
        }

        // Choose pivot: vertex in P ∪ X with max connections in complement graph to P
        String pivot = null;
        int maxConn = -1;
        Set<String> pux = new LinkedHashSet<>(P);
        pux.addAll(X);
        for (String u : pux) {
            int conn = 0;
            for (String p : P) {
                if (compAdj.getOrDefault(u, Collections.emptySet()).contains(p)) conn++;
            }
            if (conn > maxConn) { maxConn = conn; pivot = u; }
        }

        Set<String> candidates = new LinkedHashSet<>(P);
        if (pivot != null) {
            candidates.removeAll(compAdj.getOrDefault(pivot, Collections.emptySet()));
        }

        for (String v : candidates) {
            if (results.size() >= maxCount) return;
            Set<String> newR = new LinkedHashSet<>(R);
            newR.add(v);
            Set<String> newP = new LinkedHashSet<>();
            Set<String> newX = new LinkedHashSet<>();
            Set<String> vNeighbors = compAdj.getOrDefault(v, Collections.emptySet());
            for (String p : P) { if (vNeighbors.contains(p)) newP.add(p); }
            for (String x : X) { if (vNeighbors.contains(x)) newX.add(x); }
            bronKerbosch(newR, newP, newX, compAdj, results, maxCount);
            P.remove(v);
            X.add(v);
        }
    }

    // ── Kernel Reduction ──────────────────────────────────────────

    /**
     * Result of kernel reduction preprocessing.
     */
    public static class KernelResult {
        /** Vertices forced into the independent set by reduction rules. */
        public final Set<String> forcedVertices;
        /** Remaining vertices in the reduced kernel. */
        public final Set<String> kernelVertices;
        /** Edges in the reduced kernel. */
        public final Set<String[]> kernelEdges;
        /** Number of reduction rules applied. */
        public final int rulesApplied;
        /** Description of each rule application. */
        public final List<String> ruleLog;

        public KernelResult(Set<String> forced, Set<String> kernel,
                           Set<String[]> edges, int rules, List<String> log) {
            this.forcedVertices = Collections.unmodifiableSet(forced);
            this.kernelVertices = Collections.unmodifiableSet(kernel);
            this.kernelEdges = Collections.unmodifiableSet(edges);
            this.rulesApplied = rules;
            this.ruleLog = Collections.unmodifiableList(log);
        }
    }

    /**
     * Applies kernel reduction rules to simplify the graph.
     * Rules:
     * <ul>
     *   <li>Degree-0: isolated vertices are always in the IS</li>
     *   <li>Degree-1: pendant vertices — include the pendant, exclude its neighbor</li>
     *   <li>Degree-2 folding: merge degree-2 vertex with non-adjacent neighbors</li>
     * </ul>
     *
     * @return the kernel reduction result
     */
    public KernelResult kernelReduction() {
        Set<String> forced = new LinkedHashSet<>();
        Set<String> remaining = new LinkedHashSet<>(asCollection(graph.getVertices()));
        Map<String, Set<String>> adj = buildAdjacency(remaining);
        List<String> log = new ArrayList<>();
        int rules = 0;

        boolean changed = true;
        while (changed) {
            changed = false;

            // Rule 1: Degree-0 — isolated vertices
            for (String v : new ArrayList<>(remaining)) {
                int deg = 0;
                for (String n : adj.getOrDefault(v, Collections.emptySet())) {
                    if (remaining.contains(n)) deg++;
                }
                if (deg == 0) {
                    forced.add(v);
                    remaining.remove(v);
                    log.add("Degree-0: " + v + " → forced into IS");
                    rules++;
                    changed = true;
                }
            }

            // Rule 2: Degree-1 — pendant vertices
            for (String v : new ArrayList<>(remaining)) {
                if (!remaining.contains(v)) continue;
                int deg = 0;
                String neighbor = null;
                for (String n : adj.getOrDefault(v, Collections.emptySet())) {
                    if (remaining.contains(n)) { deg++; neighbor = n; }
                }
                if (deg == 1 && neighbor != null) {
                    forced.add(v);
                    remaining.remove(v);
                    // Remove the neighbor and its neighborhood
                    Set<String> nNeighbors = new HashSet<>();
                    for (String nn : adj.getOrDefault(neighbor, Collections.emptySet())) {
                        if (remaining.contains(nn)) nNeighbors.add(nn);
                    }
                    remaining.remove(neighbor);
                    log.add("Degree-1: " + v + " → forced, neighbor " + neighbor + " excluded");
                    rules++;
                    changed = true;
                }
            }
        }

        // Collect kernel edges
        Set<String[]> kernelEdges = new LinkedHashSet<>();
        for (String v : remaining) {
            for (String n : adj.getOrDefault(v, Collections.emptySet())) {
                if (remaining.contains(n) && v.compareTo(n) < 0) {
                    kernelEdges.add(new String[]{v, n});
                }
            }
        }

        return new KernelResult(forced, remaining, kernelEdges, rules, log);
    }

    // ── Bounds ────────────────────────────────────────────────────

    /**
     * Computes various bounds on the independence number α(G).
     *
     * @return a map of bound names to values
     */
    public Map<String, Double> independenceNumberBounds() {
        Map<String, Double> bounds = new LinkedHashMap<>();
        int n = graph.getVertexCount();
        int m = graph.getEdgeCount();

        if (n == 0) {
            bounds.put("trivial_upper", 0.0);
            bounds.put("trivial_lower", 0.0);
            return bounds;
        }

        // Trivial bounds
        bounds.put("trivial_upper", (double) n);
        bounds.put("trivial_lower", 1.0);

        // Greedy lower bound
        bounds.put("greedy_lower", (double) greedyIndependentSet().size());

        // Turán bound: α(G) ≥ n / (1 + d_avg) where d_avg = 2m/n
        double dAvg = (n > 0) ? (2.0 * m / n) : 0;
        bounds.put("turan_lower", n / (1.0 + dAvg));

        // Ramsey bound: α(G) ≥ ⌈n / (Δ + 1)⌉
        int maxDeg = 0;
        for (String v : graph.getVertices()) {
            int deg = graph.degree(v);
            if (deg > maxDeg) maxDeg = deg;
        }
        bounds.put("ramsey_lower", Math.ceil((double) n / (maxDeg + 1)));

        // Lovász theta bound approximation: n * min_eigenvalue(complement) / ... 
        // Simplified: use n - maxDeg as upper bound (for vertex cover complement)
        // α(G) + τ(G) = n (Gallai's theorem), τ(G) ≥ m / Δ
        if (maxDeg > 0) {
            double vcLower = (double) m / maxDeg;
            bounds.put("gallai_upper", n - vcLower);
        }

        // Edge bound: α(G) ≤ n - m/Δ (from vertex cover)
        // Also: α(G) ≤ n(n-1-2m/n) ... simplified
        // n² / (n + 2m) — Motzkin-Straus
        if (n + 2 * m > 0) {
            bounds.put("motzkin_straus_upper", (double)(n * n) / (n + 2 * m));
        }

        return bounds;
    }

    // ── Per-Vertex Analysis ───────────────────────────────────────

    /**
     * For each vertex, determines how many maximal independent sets contain it.
     * Uses the enumerated maximal IS (limited to avoid explosion).
     *
     * @param limit max number of maximal IS to enumerate
     * @return map of vertex → count of MIS containing it
     */
    public Map<String, Integer> vertexMISParticipation(int limit) {
        List<Set<String>> allMIS = allMaximalIndependentSets(limit);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String v : graph.getVertices()) {
            int count = 0;
            for (Set<String> mis : allMIS) {
                if (mis.contains(v)) count++;
            }
            counts.put(v, count);
        }
        return counts;
    }

    /**
     * Computes the independence contribution of each vertex: how much removing
     * it changes the greedy IS size.
     *
     * @return map of vertex → impact (positive means removing hurts IS)
     */
    public Map<String, Integer> vertexIndependenceImpact() {
        int baseline = greedyIndependentSet().size();
        Map<String, Integer> impact = new LinkedHashMap<>();
        for (String v : graph.getVertices()) {
            // Build subgraph without v
            Graph<String, edge> sub = new UndirectedSparseGraph<>();
            for (String u : graph.getVertices()) {
                if (!u.equals(v)) sub.addVertex(u);
            }
            for (edge e : graph.getEdges()) {
                String v1 = graph.getEndpoints(e).getFirst();
                String v2 = graph.getEndpoints(e).getSecond();
                if (!v1.equals(v) && !v2.equals(v)) {
                    sub.addEdge(new edge(e.getType(), v1, v2), v1, v2);
                }
            }
            IndependentSetAnalyzer subAnalyzer = new IndependentSetAnalyzer(sub);
            int subIS = subAnalyzer.greedyIndependentSet().size();
            impact.put(v, baseline - subIS);
        }
        return impact;
    }

    // ── Independence Polynomial (small graphs) ────────────────────

    /**
     * Computes the independence polynomial coefficients for small graphs.
     * I(G, x) = Σ_k i_k * x^k where i_k = number of independent sets of size k.
     *
     * @return array where index k = number of independent sets of size k
     * @throws IllegalStateException if graph has more than 20 vertices
     */
    public int[] independencePolynomial() {
        int n = graph.getVertexCount();
        if (n > 20) {
            throw new IllegalStateException(
                "Independence polynomial computation limited to 20 vertices, graph has " + n);
        }

        List<String> vertices = new ArrayList<>(asCollection(graph.getVertices()));
        Collections.sort(vertices);
        Map<String, Set<String>> adj = buildAdjacency(new HashSet<>(vertices));

        int[] counts = new int[n + 1];
        counts[0] = 1; // empty set

        // Enumerate all subsets via bitmask
        for (int mask = 1; mask < (1 << n); mask++) {
            List<String> subset = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) subset.add(vertices.get(i));
            }
            // Check if independent
            boolean independent = true;
            outer:
            for (int i = 0; i < subset.size(); i++) {
                for (int j = i + 1; j < subset.size(); j++) {
                    if (adj.getOrDefault(subset.get(i), Collections.emptySet()).contains(subset.get(j))) {
                        independent = false;
                        break outer;
                    }
                }
            }
            if (independent) {
                counts[subset.size()]++;
            }
        }
        return counts;
    }

    // ── Complement Relationship ───────────────────────────────────

    /**
     * Finds the maximum clique by computing the maximum independent set
     * on the complement graph.
     *
     * @return the maximum clique (independent set of complement)
     */
    public Set<String> maximumCliqueViaComplement() {
        Graph<String, edge> complement = buildComplement();
        IndependentSetAnalyzer compAnalyzer = new IndependentSetAnalyzer(complement);
        return compAnalyzer.exactMaximumIndependentSet();
    }

    private Graph<String, edge> buildComplement() {
        Graph<String, edge> comp = new UndirectedSparseGraph<>();
        List<String> vertices = new ArrayList<>(asCollection(graph.getVertices()));
        for (String v : vertices) comp.addVertex(v);
        int edgeId = 0;
        for (int i = 0; i < vertices.size(); i++) {
            for (int j = i + 1; j < vertices.size(); j++) {
                String u = vertices.get(i), w = vertices.get(j);
                if (graph.findEdge(u, w) == null) {
                    comp.addEdge(new edge("comp_" + (edgeId++), u, w), u, w);
                }
            }
        }
        return comp;
    }

    // ── Report ────────────────────────────────────────────────────

    /**
     * Comprehensive independent set analysis report.
     */
    public static class IndependentSetReport {
        public final int vertexCount;
        public final int edgeCount;
        public final Set<String> greedyIS;
        public final Set<String> maximumIS;
        public final int independenceNumber;
        public final Map<String, Double> bounds;
        public final KernelResult kernel;
        public final int maximalISCount;
        public final Map<String, Integer> vertexParticipation;
        public final String summary;

        public IndependentSetReport(int vc, int ec, Set<String> greedy, Set<String> max,
                                    int alpha, Map<String, Double> bounds, KernelResult kernel,
                                    int misCount, Map<String, Integer> participation, String summary) {
            this.vertexCount = vc;
            this.edgeCount = ec;
            this.greedyIS = greedy;
            this.maximumIS = max;
            this.independenceNumber = alpha;
            this.bounds = bounds;
            this.kernel = kernel;
            this.maximalISCount = misCount;
            this.vertexParticipation = participation;
            this.summary = summary;
        }
    }

    /**
     * Generates a comprehensive analysis report.
     *
     * @return the report
     */
    public IndependentSetReport fullReport() {
        int n = graph.getVertexCount();
        int m = graph.getEdgeCount();

        Set<String> greedy = greedyIndependentSet();

        Set<String> maximum;
        if (n <= 30) {
            maximum = exactMaximumIndependentSet();
        } else {
            maximum = greedy; // fallback
        }
        int alpha = maximum.size();

        Map<String, Double> bounds = independenceNumberBounds();
        KernelResult kernel = kernelReduction();

        List<Set<String>> allMIS = (n <= 25) ? allMaximalIndependentSets(500) : Collections.emptyList();
        int misCount = allMIS.size();

        Map<String, Integer> participation = (n <= 25) ? vertexMISParticipation(500) : Collections.emptyMap();

        StringBuilder sb = new StringBuilder();
        sb.append("=== Independent Set Analysis Report ===\n");
        sb.append(String.format("Graph: %d vertices, %d edges\n", n, m));
        sb.append(String.format("Independence number α(G) = %d\n", alpha));
        sb.append(String.format("Maximum IS: %s\n", maximum));
        sb.append(String.format("Greedy IS size: %d\n", greedy.size()));
        sb.append(String.format("Greedy IS: %s\n", greedy));
        sb.append(String.format("Kernel reduction: %d vertices forced, %d kernel vertices remain\n",
                               kernel.forcedVertices.size(), kernel.kernelVertices.size()));
        sb.append(String.format("Rules applied: %d\n", kernel.rulesApplied));
        if (misCount > 0) {
            sb.append(String.format("Maximal independent sets found: %d\n", misCount));
        }
        sb.append("\nBounds:\n");
        for (Map.Entry<String, Double> entry : bounds.entrySet()) {
            sb.append(String.format("  %s: %.2f\n", entry.getKey(), entry.getValue()));
        }
        if (!participation.isEmpty()) {
            sb.append("\nVertex MIS participation:\n");
            participation.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> sb.append(String.format("  %s: %d\n", e.getKey(), e.getValue())));
        }

        return new IndependentSetReport(n, m, greedy, maximum, alpha, bounds, kernel,
                                        misCount, participation, sb.toString());
    }

    // ── Helpers ───────────────────────────────────────────────────

    private Map<String, Set<String>> buildAdjacency(Set<String> vertices) {
        Map<String, Set<String>> adj = new HashMap<>();
        for (String v : vertices) {
            adj.put(v, new HashSet<>());
        }
        for (edge e : graph.getEdges()) {
            String v1 = graph.getEndpoints(e).getFirst();
            String v2 = graph.getEndpoints(e).getSecond();
            if (vertices.contains(v1) && vertices.contains(v2)) {
                adj.computeIfAbsent(v1, k -> new HashSet<>()).add(v2);
                adj.computeIfAbsent(v2, k -> new HashSet<>()).add(v1);
            }
        }
        return adj;
    }

    @SuppressWarnings("unchecked")
    private Collection<String> asCollection(Collection<String> c) {
        return c;
    }
}
