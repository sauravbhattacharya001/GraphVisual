package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.*;

/**
 * Line Graph Analyzer — constructs and analyzes the <b>line graph</b> L(G)
 * of an undirected graph G.
 *
 * <p>The <b>line graph</b> L(G) is a graph where:</p>
 * <ul>
 *   <li>Each edge of G becomes a vertex in L(G)</li>
 *   <li>Two vertices in L(G) are adjacent if and only if their corresponding
 *       edges in G share a common endpoint</li>
 * </ul>
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Line graph construction</b> — builds L(G) with labeled vertices</li>
 *   <li><b>Whitney's theorem</b> — checks uniqueness of root graph recovery</li>
 *   <li><b>Edge coloring bounds</b> — Vizing's theorem: χ'(G) ∈ {Δ, Δ+1}</li>
 *   <li><b>Line graph properties</b> — order, size, degree sequence, regularity</li>
 *   <li><b>Edge adjacency analysis</b> — neighborhoods, central edges</li>
 *   <li><b>Iterated line graphs</b> — L²(G), L³(G) with convergence analysis</li>
 *   <li><b>Matching via independent set</b> — maximal matching in G through L(G)</li>
 *   <li><b>Vertex edge-cliques</b> — clique decomposition of L(G)</li>
 * </ul>
 *
 * @author zalenix
 */
public class LineGraphAnalyzer {

    private final Graph<String, edge> graph;
    private Graph<String, edge> lineGraph;
    private Map<String, edge> vertexToEdge;
    private Map<String, String> edgeToVertex;
    private boolean computed;

    public LineGraphAnalyzer(Graph<String, edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.computed = false;
    }

    private void ensureComputed() {
        if (!computed) {
            buildLineGraph();
            computed = true;
        }
    }

    private void buildLineGraph() {
        lineGraph = new UndirectedSparseGraph<String, edge>();
        vertexToEdge = new LinkedHashMap<String, edge>();
        edgeToVertex = new HashMap<String, String>();

        List<edge> edges = new ArrayList<edge>(graph.getEdges());
        for (int i = 0; i < edges.size(); i++) {
            edge e = edges.get(i);
            String label = edgeLabel(e);
            lineGraph.addVertex(label);
            vertexToEdge.put(label, e);
            edgeToVertex.put(edgeKey(e), label);
        }

        for (int i = 0; i < edges.size(); i++) {
            for (int j = i + 1; j < edges.size(); j++) {
                edge e1 = edges.get(i);
                edge e2 = edges.get(j);
                if (sharesEndpoint(e1, e2)) {
                    String v1 = edgeToVertex.get(edgeKey(e1));
                    String v2 = edgeToVertex.get(edgeKey(e2));
                    edge lgEdge = new edge("lg", v1, v2);
                    lgEdge.setLabel(v1 + "-" + v2);
                    lineGraph.addEdge(lgEdge, v1, v2);
                }
            }
        }
    }

    private String edgeLabel(edge e) {
        String v1 = null, v2 = null;
        Collection<String> endpoints = graph.getEndpoints(e);
        if (endpoints != null && endpoints.size() == 2) {
            Iterator<String> it = endpoints.iterator();
            v1 = it.next();
            v2 = it.next();
        }
        if (v1 == null) v1 = e.getVertex1();
        if (v2 == null) v2 = e.getVertex2();
        if (v1 != null && v2 != null) {
            if (v1.compareTo(v2) <= 0) return v1 + "-" + v2;
            return v2 + "-" + v1;
        }
        return "e" + System.identityHashCode(e);
    }

    private String edgeKey(edge e) {
        return edgeLabel(e);
    }

    private boolean sharesEndpoint(edge e1, edge e2) {
        String[] ep1 = getEndpoints(e1);
        String[] ep2 = getEndpoints(e2);
        return ep1[0].equals(ep2[0]) || ep1[0].equals(ep2[1])
            || ep1[1].equals(ep2[0]) || ep1[1].equals(ep2[1]);
    }

    private String[] getEndpoints(edge e) {
        Collection<String> endpoints = graph.getEndpoints(e);
        if (endpoints != null && endpoints.size() == 2) {
            Iterator<String> it = endpoints.iterator();
            return new String[]{it.next(), it.next()};
        }
        String v1 = e.getVertex1();
        String v2 = e.getVertex2();
        return new String[]{v1 != null ? v1 : "", v2 != null ? v2 : ""};
    }

    // ── Accessors ───────────────────────────────────────────────────

    public Graph<String, edge> getLineGraph() {
        ensureComputed();
        return lineGraph;
    }

    public Map<String, edge> getVertexToEdgeMapping() {
        ensureComputed();
        return Collections.unmodifiableMap(vertexToEdge);
    }

    public int lineGraphOrder() {
        ensureComputed();
        return lineGraph.getVertexCount();
    }

    public int lineGraphSize() {
        ensureComputed();
        return lineGraph.getEdgeCount();
    }

    // ── Degree Analysis ─────────────────────────────────────────────

    public List<Integer> lineGraphDegreeSequence() {
        ensureComputed();
        List<Integer> degrees = new ArrayList<Integer>();
        for (String v : lineGraph.getVertices()) {
            degrees.add(lineGraph.degree(v));
        }
        Collections.sort(degrees, Collections.reverseOrder());
        return degrees;
    }

    public Map<String, Integer> lineGraphDegrees() {
        ensureComputed();
        Map<String, Integer> degrees = new LinkedHashMap<String, Integer>();
        for (String v : lineGraph.getVertices()) {
            degrees.put(v, lineGraph.degree(v));
        }
        return degrees;
    }

    public int maxDegreeOfG() {
        int maxDeg = 0;
        for (String v : graph.getVertices()) {
            int d = graph.degree(v);
            if (d > maxDeg) maxDeg = d;
        }
        return maxDeg;
    }

    public boolean isLineGraphRegular() {
        ensureComputed();
        if (lineGraph.getVertexCount() <= 1) return true;
        int first = -1;
        for (String v : lineGraph.getVertices()) {
            int d = lineGraph.degree(v);
            if (first == -1) first = d;
            else if (d != first) return false;
        }
        return true;
    }

    // ── Vizing's Theorem ────────────────────────────────────────────

    public static class VizingResult {
        private final int maxDegree;
        private final int lowerBound;
        private final int upperBound;
        private final String classification;

        public VizingResult(int maxDegree, int lowerBound, int upperBound,
                            String classification) {
            this.maxDegree = maxDegree;
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
            this.classification = classification;
        }

        public int getMaxDegree() { return maxDegree; }
        public int getLowerBound() { return lowerBound; }
        public int getUpperBound() { return upperBound; }
        public String getClassification() { return classification; }

        @Override
        public String toString() {
            return String.format("Vizing: delta=%d, chi'(G) in [%d, %d], Class %s",
                    maxDegree, lowerBound, upperBound, classification);
        }
    }

    public VizingResult vizingAnalysis() {
        int delta = maxDegreeOfG();
        if (graph.getEdgeCount() == 0) {
            return new VizingResult(delta, 0, 0, "trivial");
        }

        int n = graph.getVertexCount();
        int m = graph.getEdgeCount();
        String classification;

        if (isBipartite()) {
            classification = "1 (bipartite)";
        } else if (isOddCycle(n, m, delta)) {
            classification = "2 (odd cycle)";
        } else if (m == n * (n - 1) / 2 && n > 0) {
            classification = n % 2 == 0 ? "1 (complete, even order)" :
                    "2 (complete, odd order)";
        } else if (2 * m > delta * (n - 1)) {
            classification = "2 (overfull)";
        } else {
            classification = "undetermined (bounds apply)";
        }

        return new VizingResult(delta, delta, delta + 1, classification);
    }

    private boolean isBipartite() {
        if (graph.getVertexCount() == 0) return true;
        Map<String, Integer> color = new HashMap<String, Integer>();
        for (String start : graph.getVertices()) {
            if (color.containsKey(start)) continue;
            Queue<String> queue = new LinkedList<String>();
            queue.add(start);
            color.put(start, 0);
            while (!queue.isEmpty()) {
                String v = queue.poll();
                int c = color.get(v);
                for (String nbr : graph.getNeighbors(v)) {
                    if (!color.containsKey(nbr)) {
                        color.put(nbr, 1 - c);
                        queue.add(nbr);
                    } else if (color.get(nbr) == c) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean isOddCycle(int n, int m, int delta) {
        return n >= 3 && m == n && delta == 2 && n % 2 == 1;
    }

    // ── Iterated Line Graphs ────────────────────────────────────────

    public static class IteratedResult {
        private final List<int[]> orderSizeSequence;
        private final String convergence;

        public IteratedResult(List<int[]> orderSizeSequence, String convergence) {
            this.orderSizeSequence = orderSizeSequence;
            this.convergence = convergence;
        }

        public List<int[]> getOrderSizeSequence() { return orderSizeSequence; }
        public String getConvergence() { return convergence; }
    }

    public IteratedResult iteratedLineGraphs(int iterations) {
        iterations = Math.min(iterations, 5);
        List<int[]> seq = new ArrayList<int[]>();
        seq.add(new int[]{graph.getVertexCount(), graph.getEdgeCount()});

        Graph<String, edge> current = graph;
        String convergence = "growing";

        for (int i = 0; i < iterations; i++) {
            if (current.getEdgeCount() == 0) {
                convergence = "collapsed (no edges)";
                break;
            }
            if (current.getEdgeCount() > 10000) {
                convergence = "truncated (too large)";
                break;
            }

            LineGraphAnalyzer sub = new LineGraphAnalyzer(current);
            current = sub.getLineGraph();
            int order = current.getVertexCount();
            int size = current.getEdgeCount();
            seq.add(new int[]{order, size});

            if (seq.size() >= 2) {
                int[] prev = seq.get(seq.size() - 2);
                if (order == prev[0] && size == prev[1]) {
                    convergence = "fixed point at iteration " + (i + 1);
                    break;
                }
            }
        }

        if (convergence.equals("growing") && seq.size() >= 3) {
            boolean allDecreasing = true;
            boolean allIncreasing = true;
            for (int i = 1; i < seq.size(); i++) {
                if (seq.get(i)[0] >= seq.get(i - 1)[0]) allDecreasing = false;
                if (seq.get(i)[0] <= seq.get(i - 1)[0]) allIncreasing = false;
            }
            if (allDecreasing) convergence = "shrinking";
            else if (allIncreasing) convergence = "growing";
            else convergence = "oscillating";
        }

        return new IteratedResult(seq, convergence);
    }

    // ── Edge Neighborhood ───────────────────────────────────────────

    public Map<String, Set<String>> edgeNeighborhoods() {
        ensureComputed();
        Map<String, Set<String>> neighborhoods = new LinkedHashMap<String, Set<String>>();
        for (String v : lineGraph.getVertices()) {
            Set<String> nbrs = new TreeSet<String>();
            for (String nbr : lineGraph.getNeighbors(v)) {
                nbrs.add(nbr);
            }
            neighborhoods.put(v, nbrs);
        }
        return neighborhoods;
    }

    public String mostCentralEdge() {
        ensureComputed();
        String best = null;
        int bestDeg = -1;
        for (String v : lineGraph.getVertices()) {
            int d = lineGraph.degree(v);
            if (d > bestDeg) {
                bestDeg = d;
                best = v;
            }
        }
        return best;
    }

    // ── Whitney's Theorem ───────────────────────────────────────────

    public String whitneyTheoremCheck() {
        int n = graph.getVertexCount();
        int m = graph.getEdgeCount();

        if (n == 0) return "Trivial graph - Whitney's theorem does not apply";

        if (!isConnected()) {
            return "Graph is disconnected - Whitney's theorem applies component-wise for components with > 4 vertices";
        }

        if (n == 3 && m == 3) {
            return "Graph is K3 - this is the Whitney exception: L(K3) = L(K_{1,3}) = K3";
        }

        if (n == 4 && m == 3 && maxDegreeOfG() == 3) {
            return "Graph is K_{1,3} - this is the Whitney exception: L(K_{1,3}) = L(K3) = K3";
        }

        if (n > 4) {
            return "Whitney's theorem holds: the line graph uniquely determines this graph";
        }

        return "Whitney's theorem applies with possible small-graph caveats (n=" + n + ")";
    }

    private boolean isConnected() {
        if (graph.getVertexCount() <= 1) return true;
        Set<String> visited = new HashSet<String>();
        Queue<String> queue = new LinkedList<String>();
        String start = graph.getVertices().iterator().next();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            String v = queue.poll();
            for (String nbr : graph.getNeighbors(v)) {
                if (visited.add(nbr)) {
                    queue.add(nbr);
                }
            }
        }
        return visited.size() == graph.getVertexCount();
    }

    // ── Matching via L(G) Independent Set ───────────────────────────

    public Set<String> maximalMatchingViaLineGraph() {
        ensureComputed();
        Set<String> matching = new LinkedHashSet<String>();
        Set<String> excluded = new HashSet<String>();

        List<String> vertices = new ArrayList<String>(lineGraph.getVertices());
        Collections.sort(vertices, new Comparator<String>() {
            public int compare(String a, String b) {
                return Integer.compare(lineGraph.degree(b), lineGraph.degree(a));
            }
        });

        for (String v : vertices) {
            if (!excluded.contains(v)) {
                matching.add(v);
                excluded.add(v);
                for (String nbr : lineGraph.getNeighbors(v)) {
                    excluded.add(nbr);
                }
            }
        }

        return matching;
    }

    public int matchingSize() {
        return maximalMatchingViaLineGraph().size();
    }

    // ── Statistics ──────────────────────────────────────────────────

    public static class LineGraphStats {
        private final int originalOrder;
        private final int originalSize;
        private final int lineOrder;
        private final int lineSize;
        private final int lineMaxDegree;
        private final int lineMinDegree;
        private final double lineAvgDegree;
        private final boolean lineRegular;
        private final int matchingSize;
        private final VizingResult vizing;

        public LineGraphStats(int originalOrder, int originalSize,
                              int lineOrder, int lineSize,
                              int lineMaxDegree, int lineMinDegree,
                              double lineAvgDegree, boolean lineRegular,
                              int matchingSize, VizingResult vizing) {
            this.originalOrder = originalOrder;
            this.originalSize = originalSize;
            this.lineOrder = lineOrder;
            this.lineSize = lineSize;
            this.lineMaxDegree = lineMaxDegree;
            this.lineMinDegree = lineMinDegree;
            this.lineAvgDegree = lineAvgDegree;
            this.lineRegular = lineRegular;
            this.matchingSize = matchingSize;
            this.vizing = vizing;
        }

        public int getOriginalOrder() { return originalOrder; }
        public int getOriginalSize() { return originalSize; }
        public int getLineOrder() { return lineOrder; }
        public int getLineSize() { return lineSize; }
        public int getLineMaxDegree() { return lineMaxDegree; }
        public int getLineMinDegree() { return lineMinDegree; }
        public double getLineAvgDegree() { return lineAvgDegree; }
        public boolean isLineRegular() { return lineRegular; }
        public int getMatchingSize() { return matchingSize; }
        public VizingResult getVizing() { return vizing; }
    }

    public LineGraphStats computeStats() {
        ensureComputed();

        int lineMaxDeg = 0;
        int lineMinDeg = Integer.MAX_VALUE;
        int degSum = 0;

        if (lineGraph.getVertexCount() == 0) {
            lineMinDeg = 0;
        }

        for (String v : lineGraph.getVertices()) {
            int d = lineGraph.degree(v);
            if (d > lineMaxDeg) lineMaxDeg = d;
            if (d < lineMinDeg) lineMinDeg = d;
            degSum += d;
        }

        double avgDeg = lineGraph.getVertexCount() > 0
                ? (double) degSum / lineGraph.getVertexCount() : 0.0;

        return new LineGraphStats(
                graph.getVertexCount(), graph.getEdgeCount(),
                lineGraphOrder(), lineGraphSize(),
                lineMaxDeg, lineMinDeg,
                avgDeg, isLineGraphRegular(),
                matchingSize(), vizingAnalysis()
        );
    }

    // ── Vertex Edge-Cliques ─────────────────────────────────────────

    public Map<String, Set<String>> vertexEdgeCliques() {
        ensureComputed();
        Map<String, Set<String>> cliques = new LinkedHashMap<String, Set<String>>();

        for (String v : graph.getVertices()) {
            Set<String> clique = new TreeSet<String>();
            for (edge e : graph.getIncidentEdges(v)) {
                String label = edgeToVertex.get(edgeKey(e));
                if (label != null) clique.add(label);
            }
            cliques.put(v, clique);
        }

        return cliques;
    }

    // ── Report ──────────────────────────────────────────────────────

    public String generateReport() {
        LineGraphStats stats = computeStats();
        IteratedResult iterated = iteratedLineGraphs(3);
        String whitney = whitneyTheoremCheck();

        StringBuilder sb = new StringBuilder();
        sb.append("===================================================\n");
        sb.append("         LINE GRAPH ANALYSIS REPORT\n");
        sb.append("===================================================\n\n");

        sb.append("-- Original Graph G --\n");
        sb.append(String.format("  Vertices (n):    %d\n", stats.getOriginalOrder()));
        sb.append(String.format("  Edges (m):       %d\n", stats.getOriginalSize()));
        sb.append(String.format("  Max degree (D):  %d\n", maxDegreeOfG()));
        sb.append("\n");

        sb.append("-- Line Graph L(G) --\n");
        sb.append(String.format("  Vertices:        %d  (= edges of G)\n", stats.getLineOrder()));
        sb.append(String.format("  Edges:           %d\n", stats.getLineSize()));
        sb.append(String.format("  Max degree:      %d\n", stats.getLineMaxDegree()));
        sb.append(String.format("  Min degree:      %d\n", stats.getLineMinDegree()));
        sb.append(String.format("  Avg degree:      %.2f\n", stats.getLineAvgDegree()));
        sb.append(String.format("  Regular:         %s\n", stats.isLineRegular() ? "yes" : "no"));
        sb.append("\n");

        sb.append("-- Vizing's Theorem (Edge Coloring) --\n");
        sb.append(String.format("  %s\n", stats.getVizing().toString()));
        sb.append("\n");

        sb.append("-- Whitney's Theorem --\n");
        sb.append(String.format("  %s\n", whitney));
        sb.append("\n");

        sb.append("-- Matching (via L(G) independent set) --\n");
        sb.append(String.format("  Maximal matching size: %d\n", stats.getMatchingSize()));
        Set<String> matching = maximalMatchingViaLineGraph();
        if (!matching.isEmpty()) {
            sb.append("  Matching edges: ");
            sb.append(matching.toString());
            sb.append("\n");
        }
        sb.append("\n");

        sb.append("-- Iterated Line Graphs --\n");
        List<int[]> seq = iterated.getOrderSizeSequence();
        for (int i = 0; i < seq.size(); i++) {
            String name = i == 0 ? "G       " : "L^" + i + "(G)  ";
            sb.append(String.format("  %s  |V|=%d, |E|=%d\n",
                    name, seq.get(i)[0], seq.get(i)[1]));
        }
        sb.append(String.format("  Convergence: %s\n", iterated.getConvergence()));
        sb.append("\n");

        Map<String, Set<String>> cliques = vertexEdgeCliques();
        sb.append("-- Vertex Edge-Cliques in L(G) --\n");
        for (Map.Entry<String, Set<String>> entry : cliques.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                sb.append(String.format("  %s -> %s (size %d)\n",
                        entry.getKey(), entry.getValue(), entry.getValue().size()));
            }
        }
        sb.append("\n");

        List<Integer> degSeq = lineGraphDegreeSequence();
        sb.append("-- L(G) Degree Sequence --\n");
        sb.append("  ");
        int show = Math.min(degSeq.size(), 20);
        for (int i = 0; i < show; i++) {
            if (i > 0) sb.append(", ");
            sb.append(degSeq.get(i));
        }
        if (degSeq.size() > show) sb.append(", ...");
        sb.append("\n\n");

        sb.append("===================================================\n");

        return sb.toString();
    }
}
