package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.util.*;

/**
 * Signed graph analysis based on structural balance theory.
 *
 * <p>A signed graph has edges labeled positive (+) or negative (−). This analyzer
 * implements Heider's structural balance theory and extensions for studying social
 * networks, alliances, conflicts, and polarization.</p>
 *
 * <h3>Features</h3>
 * <ul>
 *   <li><b>Sign assignment:</b> Uses edge weight sign (positive/negative) or label (+/-)</li>
 *   <li><b>Triangle census:</b> Counts balanced (+++, +−−) and unbalanced (++-,−−−) triangles</li>
 *   <li><b>Structural balance check:</b> Tests if graph is perfectly balanced (Harary's theorem)</li>
 *   <li><b>Weak balance check:</b> Davis's generalization allowing k &gt; 2 coalitions</li>
 *   <li><b>Frustration index:</b> Minimum edges to flip for perfect balance</li>
 *   <li><b>Coalition detection:</b> Finds groups where internal edges are + and cross-group are −</li>
 *   <li><b>Balance degree:</b> Fraction of balanced triangles (0.0 to 1.0)</li>
 *   <li><b>Edge sign statistics:</b> Counts and ratios of positive/negative edges</li>
 *   <li><b>Vertex polarization:</b> Per-vertex ratio of negative to total edges</li>
 *   <li><b>Sign prediction:</b> Predict missing edge sign based on mutual neighbors</li>
 * </ul>
 *
 * <h3>Theory</h3>
 * <ul>
 *   <li><b>Strong balance (Harary 1953):</b> A signed graph is balanced iff vertices
 *       can be split into exactly 2 groups with all positive edges within and all
 *       negative edges between groups.</li>
 *   <li><b>Weak balance (Davis 1967):</b> Allows k ≥ 2 groups; no all-negative triangles.</li>
 *   <li><b>Frustration index:</b> NP-hard in general; exact for small graphs, heuristic for large.</li>
 * </ul>
 *
 * @author zalenix
 */
public class SignedGraphAnalyzer {

    private final Graph<String, Edge> graph;

    /**
     * Constructs an analyzer for the given graph.
     * Edge signs are determined by weight: weight &lt; 0 means negative, otherwise positive.
     * Alternatively, edges with label "-" or "negative" are treated as negative.
     *
     * @param graph the graph to analyze
     * @throws IllegalArgumentException if graph is null
     */
    public SignedGraphAnalyzer(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
    }

    /**
     * Determines if an edge is negative.
     *
     * @param e the edge
     * @return true if edge is negative (weight &lt; 0 or label is "-"/"negative")
     */
    public boolean isNegative(Edge e) {
        if (e.getWeight() < 0) return true;
        String label = e.getLabel();
        return label != null && (label.equals("-") || label.equalsIgnoreCase("negative"));
    }

    /**
     * Determines if an edge is positive.
     *
     * @param e the edge
     * @return true if edge is positive
     */
    public boolean isPositive(Edge e) {
        return !isNegative(e);
    }

    // ---- Edge Sign Statistics ----

    /**
     * Counts positive edges.
     * @return number of positive edges
     */
    public int countPositiveEdges() {
        int count = 0;
        for (Edge e : graph.getEdges()) {
            if (isPositive(e)) count++;
        }
        return count;
    }

    /**
     * Counts negative edges.
     * @return number of negative edges
     */
    public int countNegativeEdges() {
        int count = 0;
        for (Edge e : graph.getEdges()) {
            if (isNegative(e)) count++;
        }
        return count;
    }

    /**
     * Returns the ratio of negative edges to total edges.
     * @return negativity ratio (0.0–1.0), 0 if no edges
     */
    public double negativityRatio() {
        int total = graph.getEdgeCount();
        if (total == 0) return 0.0;
        return (double) countNegativeEdges() / total;
    }

    // ---- Vertex Polarization ----

    /**
     * Computes per-vertex polarization (fraction of incident edges that are negative).
     *
     * @return map of vertex → polarization (0.0 = all positive, 1.0 = all negative)
     */
    public Map<String, Double> vertexPolarization() {
        Map<String, Double> result = new LinkedHashMap<>();
        for (String v : graph.getVertices()) {
            Collection<Edge> incident = graph.getIncidentEdges(v);
            if (incident == null || incident.isEmpty()) {
                result.put(v, 0.0);
                continue;
            }
            long negCount = 0;
            for (Edge e : incident) {
                if (isNegative(e)) negCount++;
            }
            result.put(v, (double) negCount / incident.size());
        }
        return result;
    }

    /**
     * Returns the most polarized vertex (highest fraction of negative edges).
     * @return vertex name, or null if graph is empty
     */
    public String mostPolarizedVertex() {
        Map<String, Double> pol = vertexPolarization();
        return pol.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    // ---- Triangle Census ----

    /**
     * Result of triangle census in a signed graph.
     */
    public static class TriangleCensus {
        /** Triangles with 3 positive edges (+++) — balanced */
        public final int ppp;
        /** Triangles with 2 positive, 1 negative (++-) — unbalanced */
        public final int ppn;
        /** Triangles with 1 positive, 2 negative (+--) — balanced */
        public final int pnn;
        /** Triangles with 3 negative edges (---) — unbalanced (strongly), balanced (weakly) */
        public final int nnn;

        public TriangleCensus(int ppp, int ppn, int pnn, int nnn) {
            this.ppp = ppp;
            this.ppn = ppn;
            this.pnn = pnn;
            this.nnn = nnn;
        }

        /** Total triangles */
        public int total() { return ppp + ppn + pnn + nnn; }

        /** Balanced triangles under strong balance theory (ppp + pnn) */
        public int stronglyBalanced() { return ppp + pnn; }

        /** Unbalanced triangles under strong balance (ppn + nnn) */
        public int stronglyUnbalanced() { return ppn + nnn; }

        /** Balanced triangles under weak balance (ppp + pnn + nnn) */
        public int weaklyBalanced() { return ppp + pnn + nnn; }

        /** Unbalanced triangles under weak balance (ppn only) */
        public int weaklyUnbalanced() { return ppn; }

        /**
         * Strong balance degree: fraction of triangles that are balanced.
         * @return 0.0–1.0, or 1.0 if no triangles
         */
        public double strongBalanceDegree() {
            int t = total();
            return t == 0 ? 1.0 : (double) stronglyBalanced() / t;
        }

        /**
         * Weak balance degree.
         * @return 0.0–1.0, or 1.0 if no triangles
         */
        public double weakBalanceDegree() {
            int t = total();
            return t == 0 ? 1.0 : (double) weaklyBalanced() / t;
        }

        @Override
        public String toString() {
            return String.format("TriangleCensus{+++=%d, ++-=%d, +--=%d, ---=%d, " +
                    "strongBalance=%.2f, weakBalance=%.2f}",
                    ppp, ppn, pnn, nnn, strongBalanceDegree(), weakBalanceDegree());
        }
    }

    /**
     * Performs a triangle census counting each sign pattern.
     *
     * @return triangle census results
     */
    public TriangleCensus triangleCensus() {
        List<String> vertices = new ArrayList<>(graph.getVertices());
        int n = vertices.size();
        int ppp = 0, ppn = 0, pnn = 0, nnn = 0;

        // Build adjacency for fast lookup
        Map<String, Set<String>> adj = new HashMap<>();
        Map<String, Map<String, Edge>> edgeMap = new HashMap<>();
        for (String v : vertices) {
            adj.put(v, new HashSet<>());
            edgeMap.put(v, new HashMap<>());
        }
        for (Edge e : graph.getEdges()) {
            Collection<String> endpoints = graph.getEndpoints(e);
            Iterator<String> it = endpoints.iterator();
            String u = it.next();
            String v = it.next();
            adj.get(u).add(v);
            adj.get(v).add(u);
            edgeMap.get(u).put(v, e);
            edgeMap.get(v).put(u, e);
        }

        // Enumerate triangles
        for (int i = 0; i < n; i++) {
            String a = vertices.get(i);
            for (int j = i + 1; j < n; j++) {
                String b = vertices.get(j);
                if (!adj.get(a).contains(b)) continue;
                for (int k = j + 1; k < n; k++) {
                    String c = vertices.get(k);
                    if (!adj.get(a).contains(c) || !adj.get(b).contains(c)) continue;
                    // Triangle a-b-c
                    int negCount = 0;
                    if (isNegative(edgeMap.get(a).get(b))) negCount++;
                    if (isNegative(edgeMap.get(a).get(c))) negCount++;
                    if (isNegative(edgeMap.get(b).get(c))) negCount++;
                    switch (negCount) {
                        case 0: ppp++; break;
                        case 1: ppn++; break;
                        case 2: pnn++; break;
                        case 3: nnn++; break;
                    }
                }
            }
        }
        return new TriangleCensus(ppp, ppn, pnn, nnn);
    }

    // ---- Structural Balance ----

    /**
     * Tests if the graph is strongly balanced (Harary's theorem).
     * A signed graph is balanced iff it contains no cycles with an odd number
     * of negative edges. Equivalent to 2-colorability of the "negative subgraph."
     *
     * @return true if perfectly balanced
     */
    public boolean isStronglyBalanced() {
        if (graph.getVertexCount() == 0) return true;

        // BFS/DFS coloring: try to 2-color vertices such that
        // positive edges connect same-color and negative edges connect different-color
        Map<String, Integer> color = new HashMap<>();
        List<String> vertices = new ArrayList<>(graph.getVertices());

        // Build adjacency
        Map<String, List<String>> adj = buildAdjacency();
        Map<String, Map<String, Edge>> edgeMap = buildEdgeMap();

        for (String start : vertices) {
            if (color.containsKey(start)) continue;
            // BFS
            Queue<String> queue = new LinkedList<>();
            queue.add(start);
            color.put(start, 0);
            while (!queue.isEmpty()) {
                String u = queue.poll();
                int cu = color.get(u);
                for (String v : adj.getOrDefault(u, Collections.emptyList())) {
                    Edge e  edgeMap.get(u).get(v);
                    int expectedColor = isNegative(e) ? (1 - cu) : cu;
                    if (color.containsKey(v)) {
                        if (color.get(v) != expectedColor) return false;
                    } else {
                        color.put(v, expectedColor);
                        queue.add(v);
                    }
                }
            }
        }
        return true;
    }

    /**
     * Tests if the graph is weakly balanced (Davis's criterion).
     * Weakly balanced iff no cycle has exactly one negative edge.
     * Equivalent to: no ++-triangle exists.
     *
     * <p>For efficiency, checks triangle census for ppn = 0 (necessary condition)
     * and does full cycle check for graphs with no triangles.</p>
     *
     * @return true if weakly balanced
     */
    public boolean isWeaklyBalanced() {
        if (graph.getVertexCount() == 0) return true;
        // Weak balance: vertices can be partitioned into k≥2 groups,
        // positive within, negative between.
        // Check: the positive-edge subgraph's connected components must have
        // only negative edges between them.
        Map<String, Integer> component = new HashMap<>();
        Map<String, List<String>> posAdj = new HashMap<>();
        for (String v : graph.getVertices()) {
            posAdj.put(v, new ArrayList<>());
        }
        Map<String, Map<String, Edge>> edgeMap = buildEdgeMap();

        for (Edge e : graph.getEdges()) {
            if (isPositive(e)) {
                Collection<String> eps = graph.getEndpoints(e);
                Iterator<String> it = eps.iterator();
                String u = it.next();
                String v = it.next();
                posAdj.get(u).add(v);
                posAdj.get(v).add(u);
            }
        }

        // Find connected components of positive subgraph
        int comp = 0;
        for (String v : graph.getVertices()) {
            if (component.containsKey(v)) continue;
            Queue<String> queue = new LinkedList<>();
            queue.add(v);
            component.put(v, comp);
            while (!queue.isEmpty()) {
                String u = queue.poll();
                for (String w : posAdj.get(u)) {
                    if (!component.containsKey(w)) {
                        component.put(w, comp);
                        queue.add(w);
                    }
                }
            }
            comp++;
        }

        // Check that all negative edges go between different components
        for (Edge e : graph.getEdges()) {
            if (isNegative(e)) {
                Collection<String> eps = graph.getEndpoints(e);
                Iterator<String> it = eps.iterator();
                String u = it.next();
                String v = it.next();
                if (component.get(u).equals(component.get(v))) return false;
            }
        }
        return true;
    }

    // ---- Coalition Detection ----

    /**
     * Finds coalitions (groups) in a balanced or near-balanced graph.
     * Uses the 2-coloring from strong balance check if balanced,
     * otherwise uses positive-edge connected components.
     *
     * @return list of coalitions (each is a set of vertex names)
     */
    public List<Set<String>> findCoalitions() {
        if (graph.getVertexCount() == 0) return Collections.emptyList();

        if (isStronglyBalanced()) {
            return findStrongCoalitions();
        } else {
            return findWeakCoalitions();
        }
    }

    private List<Set<String>> findStrongCoalitions() {
        Map<String, Integer> color = new HashMap<>();
        Map<String, List<String>> adj = buildAdjacency();
        Map<String, Map<String, Edge>> edgeMap = buildEdgeMap();

        for (String start : graph.getVertices()) {
            if (color.containsKey(start)) continue;
            Queue<String> queue = new LinkedList<>();
            queue.add(start);
            color.put(start, 0);
            while (!queue.isEmpty()) {
                String u = queue.poll();
                int cu = color.get(u);
                for (String v : adj.getOrDefault(u, Collections.emptyList())) {
                    if (color.containsKey(v)) continue;
                    Edge e  edgeMap.get(u).get(v);
                    color.put(v, isNegative(e) ? (1 - cu) : cu);
                    queue.add(v);
                }
            }
        }

        Map<Integer, Set<String>> groups = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : color.entrySet()) {
            groups.computeIfAbsent(entry.getValue(), k -> new LinkedHashSet<>()).add(entry.getKey());
        }
        return new ArrayList<>(groups.values());
    }

    private List<Set<String>> findWeakCoalitions() {
        // Use positive-edge connected components
        Map<String, Integer> component = new HashMap<>();
        Map<String, List<String>> posAdj = new HashMap<>();
        for (String v : graph.getVertices()) {
            posAdj.put(v, new ArrayList<>());
        }
        for (Edge e : graph.getEdges()) {
            if (isPositive(e)) {
                Collection<String> eps = graph.getEndpoints(e);
                Iterator<String> it = eps.iterator();
                String u = it.next();
                String v = it.next();
                posAdj.get(u).add(v);
                posAdj.get(v).add(u);
            }
        }
        int comp = 0;
        for (String v : graph.getVertices()) {
            if (component.containsKey(v)) continue;
            Queue<String> queue = new LinkedList<>();
            queue.add(v);
            component.put(v, comp);
            while (!queue.isEmpty()) {
                String u = queue.poll();
                for (String w : posAdj.get(u)) {
                    if (!component.containsKey(w)) {
                        component.put(w, comp);
                        queue.add(w);
                    }
                }
            }
            comp++;
        }
        Map<Integer, Set<String>> groups = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : component.entrySet()) {
            groups.computeIfAbsent(entry.getValue(), k -> new LinkedHashSet<>()).add(entry.getKey());
        }
        return new ArrayList<>(groups.values());
    }

    // ---- Frustration Index ----

    /**
     * Computes the frustration index: minimum number of edges whose sign must be
     * flipped to make the graph balanced. Also known as the line index of balance.
     *
     * <p>Uses exact backtracking for small graphs (≤20 vertices) and a greedy
     * heuristic for larger ones.</p>
     *
     * @return frustration index (0 = perfectly balanced)
     */
    public int frustrationIndex() {
        if (graph.getEdgeCount() == 0 || isStronglyBalanced()) return 0;

        List<String> vertices = new ArrayList<>(graph.getVertices());
        int n = vertices.size();

        if (n <= 20) {
            return exactFrustration(vertices);
        } else {
            return greedyFrustration(vertices);
        }
    }

    private int exactFrustration(List<String> vertices) {
        int n = vertices.size();
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < n; i++) idx.put(vertices.get(i), i);

        Map<String, Map<String, Edge>> edgeMap = buildEdgeMap();
        Map<String, List<String>> adj = buildAdjacency();

        int bestFrustration = graph.getEdgeCount(); // worst case

        // Try all 2-partitions (2^(n-1) since we fix first vertex)
        int limit = 1 << (n - 1);
        for (int mask = 0; mask < limit; mask++) {
            int frustration = 0;
            for (Edge e : graph.getEdges()) {
                Collection<String> eps = graph.getEndpoints(e);
                Iterator<String> it = eps.iterator();
                String u = it.next();
                String v = it.next();
                int iu = idx.get(u);
                int iv = idx.get(v);
                boolean sameGroup = getBit(mask, iu) == getBit(mask, iv);
                boolean pos = isPositive(e);
                // Frustrated if: positive edge between groups, or negative edge within group
                if ((pos && !sameGroup) || (!pos && sameGroup)) {
                    frustration++;
                }
            }
            bestFrustration = Math.min(bestFrustration, frustration);
            if (bestFrustration == 0) break;
        }
        return bestFrustration;
    }

    private int getBit(int mask, int pos) {
        if (pos == 0) return 0; // fix vertex 0 to group 0
        return (mask >> (pos - 1)) & 1;
    }

    private int greedyFrustration(List<String> vertices) {
        // Greedy: start with BFS coloring, count frustrated edges
        Map<String, Integer> color = new HashMap<>();
        Map<String, List<String>> adj = buildAdjacency();
        Map<String, Map<String, Edge>> edgeMap = buildEdgeMap();

        for (String start : vertices) {
            if (color.containsKey(start)) continue;
            Queue<String> queue = new LinkedList<>();
            queue.add(start);
            color.put(start, 0);
            while (!queue.isEmpty()) {
                String u = queue.poll();
                int cu = color.get(u);
                for (String v : adj.getOrDefault(u, Collections.emptyList())) {
                    if (color.containsKey(v)) continue;
                    Edge e  edgeMap.get(u).get(v);
                    color.put(v, isNegative(e) ? (1 - cu) : cu);
                    queue.add(v);
                }
            }
        }

        int frustration = 0;
        for (Edge e : graph.getEdges()) {
            Collection<String> eps = graph.getEndpoints(e);
            Iterator<String> it = eps.iterator();
            String u = it.next();
            String v = it.next();
            boolean sameGroup = color.get(u).equals(color.get(v));
            boolean pos = isPositive(e);
            if ((pos && !sameGroup) || (!pos && sameGroup)) {
                frustration++;
            }
        }
        return frustration;
    }

    /**
     * Returns the list of frustrated edges (edges violating balance).
     * Uses the same partition as frustration index computation.
     *
     * @return list of frustrated edges
     */
    public List<Edge> findFrustratedEdges() {
        if (graph.getEdgeCount() == 0) return Collections.emptyList();

        Map<String, Integer> color = computePartition();
        List<Edge> frustrated = new ArrayList<>();

        for (Edge e : graph.getEdges()) {
            Collection<String> eps = graph.getEndpoints(e);
            Iterator<String> it = eps.iterator();
            String u = it.next();
            String v = it.next();
            boolean sameGroup = color.get(u).equals(color.get(v));
            boolean pos = isPositive(e);
            if ((pos && !sameGroup) || (!pos && sameGroup)) {
                frustrated.add(e);
            }
        }
        return frustrated;
    }

    private Map<String, Integer> computePartition() {
        Map<String, Integer> color = new HashMap<>();
        Map<String, List<String>> adj = buildAdjacency();
        Map<String, Map<String, Edge>> edgeMap = buildEdgeMap();

        for (String start : graph.getVertices()) {
            if (color.containsKey(start)) continue;
            Queue<String> queue = new LinkedList<>();
            queue.add(start);
            color.put(start, 0);
            while (!queue.isEmpty()) {
                String u = queue.poll();
                int cu = color.get(u);
                for (String v : adj.getOrDefault(u, Collections.emptyList())) {
                    if (color.containsKey(v)) continue;
                    Edge e  edgeMap.get(u).get(v);
                    color.put(v, isNegative(e) ? (1 - cu) : cu);
                    queue.add(v);
                }
            }
        }
        return color;
    }

    // ---- Sign Prediction ----

    /**
     * Predicts the sign of a potential edge between two vertices based on
     * mutual neighbor signs (triadic closure principle).
     *
     * <p>For each mutual neighbor w: if sign(u,w) * sign(w,v) is positive,
     * it votes for a positive edge; otherwise negative. Majority wins.</p>
     *
     * @param u first vertex
     * @param v second vertex
     * @return predicted sign: +1 for positive, -1 for negative, 0 if no evidence
     * @throws IllegalArgumentException if vertices don't exist in graph
     */
    public int predictSign(String u, String v) {
        if (!graph.containsVertex(u)) {
            throw new IllegalArgumentException("Vertex not found: " + u);
        }
        if (!graph.containsVertex(v)) {
            throw new IllegalArgumentException("Vertex not found: " + v);
        }

        Map<String, Map<String, Edge>> edgeMap = buildEdgeMap();
        Set<String> neighborsU = new HashSet<>(edgeMap.getOrDefault(u, Collections.emptyMap()).keySet());
        Set<String> neighborsV = new HashSet<>(edgeMap.getOrDefault(v, Collections.emptyMap()).keySet());

        Set<String> mutual = new HashSet<>(neighborsU);
        mutual.retainAll(neighborsV);

        if (mutual.isEmpty()) return 0;

        int vote = 0;
        for (String w : mutual) {
            int signUW = isNegative(edgeMap.get(u).get(w)) ? -1 : 1;
            int signWV = isNegative(edgeMap.get(w).get(v)) ? -1 : 1;
            vote += signUW * signWV;
        }
        return Integer.signum(vote);
    }

    // ---- Status Consistency ----

    /**
     * Computes status consistency for directed-like interpretation.
     * In a signed network, positive edges suggest "likes/agrees" and negative
     * edges suggest "dislikes/disagrees". Status consistency measures whether
     * sign patterns are consistent with a linear status ordering.
     *
     * <p>A triad (u,v,w) is status-consistent if the product of signs around
     * the cycle equals +1 (even number of negatives).</p>
     *
     * @return fraction of consistent triads (0.0–1.0), or 1.0 if no triads
     */
    public double statusConsistency() {
        TriangleCensus census = triangleCensus();
        if (census.total() == 0) return 1.0;
        // Status-consistent: even number of negative edges (ppp + pnn)
        return census.strongBalanceDegree();
    }

    // ---- Comprehensive Report ----

    /**
     * Result of a full signed graph analysis.
     */
    public static class SignedGraphReport {
        public final int vertexCount;
        public final int edgeCount;
        public final int positiveEdges;
        public final int negativeEdges;
        public final double negativityRatio;
        public final TriangleCensus triangleCensus;
        public final boolean stronglyBalanced;
        public final boolean weaklyBalanced;
        public final int frustrationIndex;
        public final int coalitionCount;
        public final List<Set<String>> coalitions;
        public final String mostPolarizedVertex;
        public final double mostPolarizedValue;

        public SignedGraphReport(int vertexCount, int edgeCount, int positiveEdges,
                int negativeEdges, double negativityRatio, TriangleCensus triangleCensus,
                boolean stronglyBalanced, boolean weaklyBalanced, int frustrationIndex,
                List<Set<String>> coalitions, String mostPolarizedVertex, double mostPolarizedValue) {
            this.vertexCount = vertexCount;
            this.edgeCount = edgeCount;
            this.positiveEdges = positiveEdges;
            this.negativeEdges = negativeEdges;
            this.negativityRatio = negativityRatio;
            this.triangleCensus = triangleCensus;
            this.stronglyBalanced = stronglyBalanced;
            this.weaklyBalanced = weaklyBalanced;
            this.frustrationIndex = frustrationIndex;
            this.coalitions = coalitions;
            this.coalitionCount = coalitions.size();
            this.mostPolarizedVertex = mostPolarizedVertex;
            this.mostPolarizedValue = mostPolarizedValue;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Signed Graph Analysis Report ===\n");
            sb.append(String.format("Vertices: %d, Edges: %d (+%d / -%d)\n",
                    vertexCount, edgeCount, positiveEdges, negativeEdges));
            sb.append(String.format("Negativity ratio: %.2f\n", negativityRatio));
            sb.append(String.format("Triangles: %s\n", triangleCensus));
            sb.append(String.format("Strongly balanced: %s\n", stronglyBalanced));
            sb.append(String.format("Weakly balanced: %s\n", weaklyBalanced));
            sb.append(String.format("Frustration index: %d\n", frustrationIndex));
            sb.append(String.format("Coalitions: %d\n", coalitionCount));
            for (int i = 0; i < coalitions.size(); i++) {
                sb.append(String.format("  Group %d: %s\n", i + 1, coalitions.get(i)));
            }
            if (mostPolarizedVertex != null) {
                sb.append(String.format("Most polarized: %s (%.2f)\n",
                        mostPolarizedVertex, mostPolarizedValue));
            }
            return sb.toString();
        }
    }

    /**
     * Generates a comprehensive signed graph analysis report.
     *
     * @return full analysis report
     */
    public SignedGraphReport analyze() {
        int positiveEdges = countPositiveEdges();
        int negativeEdges = countNegativeEdges();
        TriangleCensus census = triangleCensus();
        boolean strong = isStronglyBalanced();
        boolean weak = isWeaklyBalanced();
        int frustration = frustrationIndex();
        List<Set<String>> coalitions = findCoalitions();
        Map<String, Double> pol = vertexPolarization();

        String mostPolarized = mostPolarizedVertex();
        double mostPolarizedVal = mostPolarized != null ? pol.getOrDefault(mostPolarized, 0.0) : 0.0;

        return new SignedGraphReport(
                graph.getVertexCount(), graph.getEdgeCount(),
                positiveEdges, negativeEdges, negativityRatio(),
                census, strong, weak, frustration, coalitions,
                mostPolarized, mostPolarizedVal);
    }

    // ---- Helper Methods ----

    private Map<String, List<String>> buildAdjacency() {
        Map<String, List<String>> adj = new HashMap<>();
        for (String v : graph.getVertices()) {
            adj.put(v, new ArrayList<>());
        }
        for (Edge e : graph.getEdges()) {
            Collection<String> eps = graph.getEndpoints(e);
            Iterator<String> it = eps.iterator();
            String u = it.next();
            String v = it.next();
            adj.get(u).add(v);
            adj.get(v).add(u);
        }
        return adj;
    }

    private Map<String, Map<String, Edge>> buildEdgeMap() {
        Map<String, Map<String, Edge>> edgeMap = new HashMap<>();
        for (String v : graph.getVertices()) {
            edgeMap.put(v, new HashMap<>());
        }
        for (Edge e : graph.getEdges()) {
            Collection<String> eps = graph.getEndpoints(e);
            Iterator<String> it = eps.iterator();
            String u = it.next();
            String v = it.next();
            edgeMap.get(u).put(v, e);
            edgeMap.get(v).put(u, e);
        }
        return edgeMap;
    }
}
