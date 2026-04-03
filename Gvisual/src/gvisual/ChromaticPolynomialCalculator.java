package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Chromatic Polynomial Calculator — computes the chromatic polynomial P(G, k)
 * of an undirected graph using the deletion-contraction algorithm.
 *
 * <p>The chromatic polynomial P(G, k) counts the number of proper k-colorings
 * of graph G. It encodes deep structural information: its roots, coefficients,
 * and evaluation at specific values reveal properties like the chromatic number,
 * acyclicity, and connectivity.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Exact chromatic polynomial via deletion-contraction (graphs ≤ 20 vertices)</li>
 *   <li>Evaluate P(G, k) for any k — how many proper k-colorings exist?</li>
 *   <li>Chromatic number extraction from the polynomial</li>
 *   <li>Coefficient analysis and polynomial properties</li>
 *   <li>Special-case detection (trees, complete graphs, cycles, empty graphs)</li>
 *   <li>Polynomial factoring over connected components</li>
 *   <li>Comprehensive text report</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 * ChromaticPolynomialCalculator calc = new ChromaticPolynomialCalculator(graph);
 * ChromaticPolynomialCalculator.PolynomialResult result = calc.compute();
 * System.out.println(result.getPolynomialString());
 * long colorings = result.evaluate(4);  // number of proper 4-colorings
 * </pre>
 *
 * @author zalenix
 */
public final class ChromaticPolynomialCalculator {

    /** Maximum vertices for exact computation (deletion-contraction is exponential). */
    private static final int MAX_VERTICES_EXACT = 20;

    private final Graph<String, String> graph;

    public ChromaticPolynomialCalculator(Graph<String, String> graph) {
        this.graph = Objects.requireNonNull(graph, "graph must not be null");
    }

    // ── Result container ─────────────────────────────────────────────

    /**
     * Result of chromatic polynomial computation.
     */
    public static final class PolynomialResult {
        private final long[] coefficients; // coefficients[i] = coeff of k^i
        private final int degree;
        private final int chromaticNumber;
        private final int vertexCount;
        private final int edgeCount;
        private final String specialType; // null if not a recognized special graph
        private final boolean exact;
        private final List<Map.Entry<Integer, Long>> evaluations;

        PolynomialResult(long[] coefficients, int chromaticNumber,
                         int vertexCount, int edgeCount, String specialType,
                         boolean exact, List<Map.Entry<Integer, Long>> evaluations) {
            this.coefficients = coefficients.clone();
            this.degree = coefficients.length - 1;
            this.chromaticNumber = chromaticNumber;
            this.vertexCount = vertexCount;
            this.edgeCount = edgeCount;
            this.specialType = specialType;
            this.exact = exact;
            this.evaluations = Collections.unmodifiableList(evaluations);
        }

        /** Polynomial coefficients; index i holds the coefficient of k^i. */
        public long[] getCoefficients() { return coefficients.clone(); }

        /** Degree of the polynomial (= number of vertices). */
        public int getDegree() { return degree; }

        /** Chromatic number χ(G) — smallest k with P(G,k) &gt; 0. */
        public int getChromaticNumber() { return chromaticNumber; }

        public int getVertexCount() { return vertexCount; }
        public int getEdgeCount() { return edgeCount; }

        /** If the graph matches a known family (tree, cycle, complete, empty), its name. */
        public String getSpecialType() { return specialType; }

        /** Whether the polynomial is exact (true) or an approximation. */
        public boolean isExact() { return exact; }

        /** Sample evaluations P(G, k) for small k values. */
        public List<Map.Entry<Integer, Long>> getEvaluations() { return evaluations; }

        /**
         * Evaluate the polynomial at a given k.
         */
        public long evaluate(int k) {
            return evaluatePolynomial(coefficients, k);
        }

        /**
         * Human-readable polynomial string, e.g. "k^3 - 3k^2 + 2k".
         */
        public String getPolynomialString() {
            return formatPolynomial(coefficients);
        }
    }

    // ── Main computation ─────────────────────────────────────────────

    /**
     * Compute the chromatic polynomial.
     *
     * @return the polynomial result
     */
    public PolynomialResult compute() {
        int n = graph.getVertexCount();
        int m = graph.getEdgeCount();

        // Detect special graph types first
        String specialType = detectSpecialType(n, m);

        long[] poly;
        boolean exact;

        if (n == 0) {
            poly = new long[]{1}; // P(empty,k) = 1 (vacuously true)
            exact = true;
        } else if (m == 0) {
            // Independent set: P = k^n
            poly = new long[n + 1];
            poly[n] = 1;
            exact = true;
        } else if (specialType != null && specialType.equals("Complete K" + n)) {
            poly = completeGraphPolynomial(n);
            exact = true;
        } else if (specialType != null && specialType.startsWith("Tree")) {
            poly = treePolynomial(n);
            exact = true;
        } else if (specialType != null && specialType.startsWith("Cycle")) {
            poly = cyclePolynomial(n);
            exact = true;
        } else if (n <= MAX_VERTICES_EXACT) {
            // Factor over connected components
            poly = computeViaComponents();
            exact = true;
        } else {
            // Too large: compute approximate via deletion-contraction on a
            // simplified version, or just return the tree/edge bound
            poly = approximatePolynomial(n, m);
            exact = false;
        }

        // Find chromatic number from polynomial
        int chi = findChromaticNumber(poly);

        // Sample evaluations
        List<Map.Entry<Integer, Long>> evals = new ArrayList<>();
        for (int k = 0; k <= Math.min(n + 2, 10); k++) {
            long val = evaluatePolynomial(poly, k);
            evals.add(new AbstractMap.SimpleEntry<>(k, val));
        }

        return new PolynomialResult(poly, chi, n, m, specialType, exact, evals);
    }

    /**
     * Generate a comprehensive text report.
     */
    public String generateReport() {
        PolynomialResult result = compute();
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════╗\n");
        sb.append("║          CHROMATIC POLYNOMIAL ANALYSIS                  ║\n");
        sb.append("╚══════════════════════════════════════════════════════════╝\n\n");

        sb.append(String.format("  Vertices:          %d%n", result.getVertexCount()));
        sb.append(String.format("  Edges:             %d%n", result.getEdgeCount()));
        if (result.getSpecialType() != null) {
            sb.append(String.format("  Graph type:        %s%n", result.getSpecialType()));
        }
        sb.append(String.format("  Exact:             %s%n", result.isExact() ? "Yes" : "No (approximation)"));
        sb.append("\n");

        sb.append("── Chromatic Polynomial P(G, k) ────────────────────────────\n\n");
        sb.append(String.format("  P(G, k) = %s%n%n", result.getPolynomialString()));
        sb.append(String.format("  Degree:            %d%n", result.getDegree()));
        sb.append(String.format("  Chromatic number:  χ(G) = %d%n%n", result.getChromaticNumber()));

        // Coefficient breakdown
        sb.append("── Coefficients ────────────────────────────────────────────\n\n");
        long[] coeffs = result.getCoefficients();
        for (int i = coeffs.length - 1; i >= 0; i--) {
            if (coeffs[i] != 0) {
                sb.append(String.format("  k^%d : %d%n", i, coeffs[i]));
            }
        }
        sb.append("\n");

        // Properties from coefficients
        sb.append("── Polynomial Properties ───────────────────────────────────\n\n");
        if (coeffs.length > 1) {
            long leadingCoeff = coeffs[coeffs.length - 1];
            sb.append(String.format("  Leading coefficient:   %d (always 1 for simple graphs)%n", leadingCoeff));
            if (coeffs.length > 2) {
                long secondCoeff = coeffs[coeffs.length - 2];
                sb.append(String.format("  Second coefficient:    %d (= -|E|, edge count = %d)%n",
                        secondCoeff, result.getEdgeCount()));
            }
        }
        boolean alternating = isAlternatingSign(coeffs);
        sb.append(String.format("  Alternating signs:     %s%n", alternating ? "Yes" : "No"));
        sb.append(String.format("  P(G, 0) = %d (always 0 for non-empty graphs)%n",
                evaluatePolynomial(coeffs, 0)));
        sb.append(String.format("  P(G, 1) = %d (>0 iff graph has no edges)%n",
                evaluatePolynomial(coeffs, 1)));
        sb.append("\n");

        // Evaluation table
        sb.append("── Evaluation Table P(G, k) ────────────────────────────────\n\n");
        sb.append("   k  │  P(G, k)       │ Meaning\n");
        sb.append("  ────┼────────────────┼────────────────────────────\n");
        for (Map.Entry<Integer, Long> e : result.getEvaluations()) {
            int k = e.getKey();
            long val = e.getValue();
            String meaning = "";
            if (val == 0 && k > 0) {
                meaning = "Not " + k + "-colorable";
            } else if (val > 0 && k == result.getChromaticNumber()) {
                meaning = val + " proper colorings (χ = " + k + ")";
            } else if (val > 0) {
                meaning = val + " proper colorings";
            } else if (k == 0) {
                meaning = "Trivial (no colors)";
            }
            sb.append(String.format("  %3d │ %14d │ %s%n", k, val, meaning));
        }
        sb.append("\n");

        return sb.toString();
    }

    // ── Deletion-Contraction ─────────────────────────────────────────

    /**
     * Compute chromatic polynomial by factoring over connected components,
     * then using deletion-contraction on each component.
     */
    private long[] computeViaComponents() {
        List<Set<String>> components = findConnectedComponents();
        long[] result = new long[]{1}; // multiplicative identity

        for (Set<String> component : components) {
            Graph<String, String> subgraph = induceSubgraph(graph, component);
            long[] componentPoly = deletionContraction(subgraph, new HashMap<>());
            result = multiplyPolynomials(result, componentPoly);
        }
        return result;
    }

    /**
     * Deletion-contraction with memoization.
     * P(G, k) = P(G-e, k) - P(G/e, k)
     */
    private long[] deletionContraction(Graph<String, String> g, Map<String, long[]> memo) {
        int n = g.getVertexCount();
        int m = g.getEdgeCount();

        // Base cases
        if (n == 0) return new long[]{1};
        if (m == 0) {
            long[] poly = new long[n + 1];
            poly[n] = 1;
            return poly;
        }

        // Check memo
        String key = canonicalKey(g);
        if (memo.containsKey(key)) {
            return memo.get(key).clone();
        }

        // Pick an edge incident to the minimum-degree vertex.
        // This heuristic reduces the branching factor of the recursion
        // tree: contracting a low-degree vertex produces a smaller graph
        // more quickly, and the deletion side keeps the graph sparser.
        String minVertex = null;
        int minDeg = Integer.MAX_VALUE;
        for (String vertex : g.getVertices()) {
            int deg = g.degree(vertex);
            if (deg > 0 && deg < minDeg) {
                minDeg = deg;
                minVertex = vertex;
                if (deg == 1) break; // can't do better than degree 1
            }
        }

        String edge = g.getIncidentEdges(minVertex).iterator().next();
        String u = g.getEndpoints(edge).getFirst();
        String v = g.getEndpoints(edge).getSecond();

        // G - e (deletion)
        Graph<String, String> gMinusE = copyGraph(g);
        gMinusE.removeEdge(edge);
        long[] pDeletion = deletionContraction(gMinusE, memo);

        // G / e (contraction): merge v into u
        Graph<String, String> gContracted = contractEdge(g, edge, u, v);
        long[] pContraction = deletionContraction(gContracted, memo);

        // P(G) = P(G-e) - P(G/e)
        long[] result = subtractPolynomials(pDeletion, pContraction);
        memo.put(key, result.clone());
        return result;
    }

    // ── Special graph polynomials ────────────────────────────────────

    /** P(K_n, k) = k(k-1)(k-2)...(k-n+1) = falling factorial. */
    private static long[] completeGraphPolynomial(int n) {
        long[] poly = new long[]{1};
        for (int i = 0; i < n; i++) {
            // multiply by (k - i)
            poly = multiplyPolynomials(poly, new long[]{-i, 1});
        }
        return poly;
    }

    /** P(T_n, k) = k(k-1)^(n-1) for any tree on n vertices. */
    private static long[] treePolynomial(int n) {
        if (n == 1) return new long[]{0, 1}; // k
        // k * (k-1)^(n-1)
        long[] base = new long[]{-1, 1}; // (k-1)
        long[] power = new long[]{1};
        for (int i = 0; i < n - 1; i++) {
            power = multiplyPolynomials(power, base);
        }
        // multiply by k
        return multiplyPolynomials(power, new long[]{0, 1});
    }

    /** P(C_n, k) = (k-1)^n + (-1)^n * (k-1) for cycle on n vertices. */
    private static long[] cyclePolynomial(int n) {
        // (k-1)^n
        long[] base = new long[]{-1, 1};
        long[] power = new long[]{1};
        for (int i = 0; i < n; i++) {
            power = multiplyPolynomials(power, base);
        }
        // + (-1)^n * (k-1)
        long sign = (n % 2 == 0) ? 1 : -1;
        long[] term = new long[]{-sign, sign}; // (-1)^n * (k-1)
        return addPolynomials(power, term);
    }

    // ── Approximation for large graphs ───────────────────────────────

    private long[] approximatePolynomial(int n, int m) {
        // Use Whitney's formula first two terms: k^n - m*k^(n-1) + ...
        // Only first two terms as approximation
        long[] poly = new long[n + 1];
        poly[n] = 1;
        if (n > 0) {
            poly[n - 1] = -m;
        }
        return poly;
    }

    // ── Graph operations ─────────────────────────────────────────────

    private List<Set<String>> findConnectedComponents() {
        Set<String> visited = new HashSet<>();
        List<Set<String>> components = new ArrayList<>();
        for (String v : graph.getVertices()) {
            if (!visited.contains(v)) {
                Set<String> component = new HashSet<>();
                Queue<String> queue = new ArrayDeque<>();
                queue.add(v);
                visited.add(v);
                while (!queue.isEmpty()) {
                    String curr = queue.poll();
                    component.add(curr);
                    for (String neighbor : graph.getNeighbors(curr)) {
                        if (visited.add(neighbor)) {
                            queue.add(neighbor);
                        }
                    }
                }
                components.add(component);
            }
        }
        return components;
    }

    private static Graph<String, String> induceSubgraph(Graph<String, String> g, Set<String> vertices) {
        Graph<String, String> sub = new UndirectedSparseGraph<>();
        for (String v : vertices) sub.addVertex(v);
        for (String e : g.getEdges()) {
            String u = g.getEndpoints(e).getFirst();
            String v = g.getEndpoints(e).getSecond();
            if (vertices.contains(u) && vertices.contains(v)) {
                sub.addEdge(e, u, v);
            }
        }
        return sub;
    }

    private static Graph<String, String> copyGraph(Graph<String, String> g) {
        Graph<String, String> copy = new UndirectedSparseGraph<>();
        for (String v : g.getVertices()) copy.addVertex(v);
        for (String e : g.getEdges()) {
            String u = g.getEndpoints(e).getFirst();
            String v = g.getEndpoints(e).getSecond();
            copy.addEdge(e, u, v);
        }
        return copy;
    }

    private static Graph<String, String> contractEdge(Graph<String, String> g,
                                                       String edge, String u, String v) {
        Graph<String, String> result = new UndirectedSparseGraph<>();
        // Add all vertices except v
        for (String vertex : g.getVertices()) {
            if (!vertex.equals(v)) {
                result.addVertex(vertex);
            }
        }
        // Add edges, redirecting v's edges to u
        int edgeCounter = 0;
        Set<String> addedPairs = new HashSet<>();
        for (String e : g.getEdges()) {
            if (e.equals(edge)) continue;
            String a = g.getEndpoints(e).getFirst();
            String b = g.getEndpoints(e).getSecond();
            // Replace v with u
            if (a.equals(v)) a = u;
            if (b.equals(v)) b = u;
            // Skip self-loops
            if (a.equals(b)) continue;
            // Skip multi-edges
            String pairKey = a.compareTo(b) < 0 ? a + "~" + b : b + "~" + a;
            if (addedPairs.add(pairKey)) {
                result.addEdge("ce_" + edgeCounter++, a, b);
            }
        }
        return result;
    }

    /**
     * Canonical key for memoization — sorted adjacency representation.
     * Uses packed long encoding to avoid per-edge String allocation:
     * each edge (a,b) with a&lt;b is packed as (a &lt;&lt; 16) | b, then the
     * sorted long array is converted to a single String via Arrays.toString.
     */
    private static String canonicalKey(Graph<String, String> g) {
        List<String> vertices = new ArrayList<>(g.getVertices());
        Collections.sort(vertices);
        Map<String, Integer> index = new HashMap<>(vertices.size() * 2);
        for (int i = 0; i < vertices.size(); i++) index.put(vertices.get(i), i);

        int edgeCount = g.getEdgeCount();
        long[] edgeCodes = new long[edgeCount];
        int idx = 0;
        for (String e : g.getEdges()) {
            int a = index.get(g.getEndpoints(e).getFirst());
            int b = index.get(g.getEndpoints(e).getSecond());
            if (a > b) { int t = a; a = b; b = t; }
            edgeCodes[idx++] = ((long) a << 16) | b;
        }
        Arrays.sort(edgeCodes);
        return vertices.size() + ":" + Arrays.toString(edgeCodes);
    }

    // ── Polynomial arithmetic ────────────────────────────────────────

    private static long[] addPolynomials(long[] a, long[] b) {
        int maxLen = Math.max(a.length, b.length);
        long[] result = new long[maxLen];
        for (int i = 0; i < a.length; i++) result[i] += a[i];
        for (int i = 0; i < b.length; i++) result[i] += b[i];
        return result;
    }

    private static long[] subtractPolynomials(long[] a, long[] b) {
        int maxLen = Math.max(a.length, b.length);
        long[] result = new long[maxLen];
        for (int i = 0; i < a.length; i++) result[i] += a[i];
        for (int i = 0; i < b.length; i++) result[i] -= b[i];
        return result;
    }

    private static long[] multiplyPolynomials(long[] a, long[] b) {
        if (a.length == 0 || b.length == 0) return new long[]{0};
        long[] result = new long[a.length + b.length - 1];
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < b.length; j++) {
                result[i + j] += a[i] * b[j];
            }
        }
        return result;
    }

    /**
     * Evaluate polynomial using Horner's method — O(n) multiplications
     * instead of O(n) multiplications + O(n) power accumulations,
     * with better numerical stability for large coefficients.
     */
    private static long evaluatePolynomial(long[] coeffs, int k) {
        if (coeffs.length == 0) return 0;
        long result = coeffs[coeffs.length - 1];
        for (int i = coeffs.length - 2; i >= 0; i--) {
            result = result * k + coeffs[i];
        }
        return result;
    }

    // ── Formatting ───────────────────────────────────────────────────

    private static String formatPolynomial(long[] coeffs) {
        if (coeffs.length == 0) return "0";

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int i = coeffs.length - 1; i >= 0; i--) {
            long c = coeffs[i];
            if (c == 0) continue;

            if (!first) {
                sb.append(c > 0 ? " + " : " - ");
                c = Math.abs(c);
            } else if (c < 0) {
                sb.append("-");
                c = Math.abs(c);
            }

            if (i == 0) {
                sb.append(c);
            } else if (i == 1) {
                if (c == 1) sb.append("k");
                else sb.append(c).append("k");
            } else {
                if (c == 1) sb.append("k^").append(i);
                else sb.append(c).append("k^").append(i);
            }
            first = false;
        }
        return first ? "0" : sb.toString();
    }

    // ── Analysis helpers ─────────────────────────────────────────────

    private int findChromaticNumber(long[] poly) {
        for (int k = 0; k <= poly.length + 1; k++) {
            if (evaluatePolynomial(poly, k) > 0) return k;
        }
        return -1; // shouldn't happen for valid graphs
    }

    private static boolean isAlternatingSign(long[] coeffs) {
        // Check if non-zero coefficients alternate in sign
        int lastSign = 0;
        for (int i = coeffs.length - 1; i >= 0; i--) {
            if (coeffs[i] == 0) continue;
            int sign = coeffs[i] > 0 ? 1 : -1;
            if (lastSign != 0 && sign == lastSign) return false;
            lastSign = sign;
        }
        return true;
    }

    private String detectSpecialType(int n, int m) {
        if (n == 0) return "Empty graph";
        if (m == 0) return n == 1 ? "Single vertex" : "Independent set (" + n + " vertices)";

        // Complete graph: m = n*(n-1)/2
        if (m == (long) n * (n - 1) / 2) return "Complete K" + n;

        // Tree: connected, m = n-1
        if (m == n - 1 && isConnected()) return "Tree (" + n + " vertices)";

        // Cycle: all degree 2, connected
        if (m == n && graph.getVertices().stream()
                .allMatch(v -> graph.degree(v) == 2) && isConnected()) {
            return "Cycle C" + n;
        }

        return null;
    }

    private boolean isConnected() {
        if (graph.getVertexCount() == 0) return true;
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        String start = graph.getVertices().iterator().next();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            String curr = queue.poll();
            for (String neighbor : graph.getNeighbors(curr)) {
                if (visited.add(neighbor)) queue.add(neighbor);
            }
        }
        return visited.size() == graph.getVertexCount();
    }
}
