package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.*;

/**
 * Graph Sparsification Analyzer — reduces Edge count while preserving
 * key structural properties of the graph.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Spanning tree sparsification</b> — minimum edges preserving connectivity</li>
 *   <li><b>Edge importance scoring</b> — betweenness, bridge detection, redundancy</li>
 *   <li><b>Random sparsification</b> — probabilistic Edge sampling with target ratio</li>
 *   <li><b>Threshold sparsification</b> — weight-based Edge filtering</li>
 *   <li><b>Local sparsification</b> — preserve top-k edges per vertex by weight</li>
 *   <li><b>Importance-based sparsification</b> — keep most structurally important edges</li>
 *   <li><b>Quality metrics</b> — connectivity, density, degree correlation grading</li>
 *   <li><b>Reports</b> — comprehensive text analysis of sparsification results</li>
 * </ul>
 *
 * @author zalenix
 */
public class GraphSparsificationAnalyzer {

    private final Graph<String, Edge> graph;

    public GraphSparsificationAnalyzer(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
    }

    /** Scores each Edge by importance (0.0-1.0). Higher = more important. */
    public Map<Edge, Double> scoreEdgeImportance() {
        Map<Edge, Double> scores = new LinkedHashMap<Edge, Double>();
        if (graph.getEdgeCount() == 0) return scores;

        Map<Edge, Double> betweenness = computeEdgeBetweenness();
        Set<Edge> bridges = findBridges();

        double maxBet = 0;
        for (double val : betweenness.values()) {
            if (val > maxBet) maxBet = val;
        }

        int maxDeg = 0;
        for (String vtx : graph.getVertices()) {
            if (graph.degree(vtx) > maxDeg) maxDeg = graph.degree(vtx);
        }

        for (Edge e : graph.getEdges()) {
            double score = 0;
            if (bridges.contains(e)) score += 0.5;
            double bet = betweenness.containsKey(e) ? betweenness.get(e) : 0;
            if (maxBet > 0) score += 0.3 * (bet / maxBet);
            String v1 = e.getVertex1(), v2 = e.getVertex2();
            if (v1 != null && v2 != null && graph.containsVertex(v1) && graph.containsVertex(v2) && maxDeg > 0) {
                double avgDeg = (graph.degree(v1) + graph.degree(v2)) / 2.0;
                score += 0.2 * (1.0 - avgDeg / maxDeg);
            }
            scores.put(e, Math.min(1.0, score));
        }
        return scores;
    }

    /** Finds bridge edges whose removal disconnects the graph. */
    public Set<Edge> findBridges() {
        Set<Edge> bridges = new LinkedHashSet<Edge>();
        if (graph.getVertexCount() == 0) return bridges;
        Map<String, Integer> disc = new HashMap<String, Integer>();
        Map<String, Integer> low = new HashMap<String, Integer>();
        Map<String, String> parent = new HashMap<String, String>();
        int[] timer = {0};
        for (String vtx : graph.getVertices()) {
            if (!disc.containsKey(vtx)) bridgeDFS(vtx, disc, low, parent, timer, bridges);
        }
        return bridges;
    }

    private void bridgeDFS(String u, Map<String, Integer> disc, Map<String, Integer> low,
                           Map<String, String> parent, int[] timer, Set<Edge> bridges) {
        disc.put(u, timer[0]);
        low.put(u, timer[0]);
        timer[0]++;
        for (String nb : graph.getNeighbors(u)) {
            if (!disc.containsKey(nb)) {
                parent.put(nb, u);
                bridgeDFS(nb, disc, low, parent, timer, bridges);
                low.put(u, Math.min(low.get(u), low.get(nb)));
                if (low.get(nb) > disc.get(u)) {
                    Edge be = graph.findEdge(u, nb);
                    if (be != null) bridges.add(be);
                }
            } else if (!nb.equals(parent.get(u))) {
                low.put(u, Math.min(low.get(u), disc.get(nb)));
            }
        }
    }

    /** Spanning tree (Kruskal's MST). */
    public Graph<String, Edge> spanningTreeSparsify() {
        Graph<String, Edge> sparse = new UndirectedSparseGraph<String, Edge>();
        for (String vtx : graph.getVertices()) sparse.addVertex(vtx);
        if (graph.getVertexCount() <= 1) return sparse;

        List<Edge> edges = new ArrayList<Edge>(graph.getEdges());
        Collections.sort(edges, (Edge a, Edge b) -> { return Float.compare(a.getWeight(), b.getWeight()); });
        Map<String, String> par = new HashMap<String, String>();
        Map<String, Integer> rnk = new HashMap<String, Integer>();
        for (String vtx : graph.getVertices()) { par.put(vtx, vtx); rnk.put(vtx, 0); }

        for (Edge e : edges) {
            String u = e.getVertex1(), vt = e.getVertex2();
            if (u == null || vt == null) continue;
            String ru = ufFind(par, u), rv = ufFind(par, vt);
            if (!ru.equals(rv)) {
                Edge ne = new Edge(e.getType(), u, vt);
                ne.setWeight(e.getWeight()); ne.setLabel(e.getLabel());
                sparse.addEdge(ne, u, vt);
                ufUnion(par, rnk, ru, rv);
            }
        }
        return sparse;
    }

    private String ufFind(Map<String, String> p, String x) {
        while (!p.get(x).equals(x)) { p.put(x, p.get(p.get(x))); x = p.get(x); }
        return x;
    }
    private void ufUnion(Map<String, String> p, Map<String, Integer> r, String a, String b) {
        if (r.get(a) < r.get(b)) p.put(a, b);
        else if (r.get(a) > r.get(b)) p.put(b, a);
        else { p.put(b, a); r.put(a, r.get(a) + 1); }
    }

    /** Random sparsification — keeps each Edge with given probability. */
    public Graph<String, Edge> randomSparsify(double keepProb, long seed) {
        if (keepProb < 0 || keepProb > 1) throw new IllegalArgumentException("keepProbability must be between 0 and 1");
        Graph<String, Edge> sparse = new UndirectedSparseGraph<String, Edge>();
        for (String vtx : graph.getVertices()) sparse.addVertex(vtx);
        Random rng = new Random(seed);
        for (Edge e : graph.getEdges()) {
            if (rng.nextDouble() < keepProb) {
                String u = e.getVertex1(), vt = e.getVertex2();
                if (u != null && vt != null) {
                    Edge ne = new Edge(e.getType(), u, vt);
                    ne.setWeight(e.getWeight()); ne.setLabel(e.getLabel());
                    sparse.addEdge(ne, u, vt);
                }
            }
        }
        return sparse;
    }

    /** Threshold sparsification — keeps edges with weight >= threshold. */
    public Graph<String, Edge> thresholdSparsify(float threshold) {
        Graph<String, Edge> sparse = new UndirectedSparseGraph<String, Edge>();
        for (String vtx : graph.getVertices()) sparse.addVertex(vtx);
        for (Edge e : graph.getEdges()) {
            if (e.getWeight() >= threshold) {
                String u = e.getVertex1(), vt = e.getVertex2();
                if (u != null && vt != null) {
                    Edge ne = new Edge(e.getType(), u, vt);
                    ne.setWeight(e.getWeight()); ne.setLabel(e.getLabel());
                    sparse.addEdge(ne, u, vt);
                }
            }
        }
        return sparse;
    }

    /** Local sparsification — keep top-k edges per vertex by weight. */
    public Graph<String, Edge> localSparsify(int k) {
        if (k < 1) throw new IllegalArgumentException("k must be at least 1");
        Graph<String, Edge> sparse = new UndirectedSparseGraph<String, Edge>();
        for (String vtx : graph.getVertices()) sparse.addVertex(vtx);
        Set<String> added = new HashSet<String>();

        for (String vertex : graph.getVertices()) {
            List<Edge> inc = new ArrayList<Edge>(graph.getIncidentEdges(vertex));
            Collections.sort(inc, (Edge a, Edge b) -> { return Float.compare(b.getWeight(), a.getWeight()); });
            int cnt = 0;
            for (Edge e : inc) {
                if (cnt >= k) break;
                String u = e.getVertex1(), vt = e.getVertex2();
                if (u == null || vt == null) continue;
                String key = u.compareTo(vt) < 0 ? u + "|" + vt : vt + "|" + u;
                if (!added.contains(key)) {
                    Edge ne = new Edge(e.getType(), u, vt);
                    ne.setWeight(e.getWeight()); ne.setLabel(e.getLabel());
                    sparse.addEdge(ne, u, vt);
                    added.add(key);
                }
                cnt++;
            }
        }
        return sparse;
    }

    /** Importance-based sparsification — keeps the most important edges. */
    public Graph<String, Edge> importanceSparsify(double keepRatio) {
        if (keepRatio < 0 || keepRatio > 1) throw new IllegalArgumentException("keepRatio must be between 0 and 1");
        Graph<String, Edge> sparse = new UndirectedSparseGraph<String, Edge>();
        for (String vtx : graph.getVertices()) sparse.addVertex(vtx);

        Map<Edge, Double> scores = scoreEdgeImportance();
        List<Map.Entry<Edge, Double>> sorted = new ArrayList<Map.Entry<Edge, Double>>(scores.entrySet());
        Collections.sort(sorted, (Map.Entry<Edge, Double> a, Map.Entry<Edge, Double> b) -> {
                return Double.compare(b.getValue(), a.getValue());
            });
        int keep = (int) Math.ceil(graph.getEdgeCount() * keepRatio);
        int cnt = 0;
        for (Map.Entry<Edge, Double> entry : sorted) {
            if (cnt >= keep) break;
            Edge e = entry.getKey();
            String u = e.getVertex1(), vt = e.getVertex2();
            if (u != null && vt != null) {
                Edge ne = new Edge(e.getType(), u, vt);
                ne.setWeight(e.getWeight()); ne.setLabel(e.getLabel());
                sparse.addEdge(ne, u, vt);
            }
            cnt++;
        }
        return sparse;
    }

    // ── Quality ──────────────────────────────────────────────────────

    public static class SparsificationQuality {
        public final int originalVertices, originalEdges, sparseVertices, sparseEdges;
        public final double edgeReduction;
        public final boolean connectivityPreserved;
        public final int originalComponents, sparseComponents;
        public final double originalDensity, sparseDensity, avgDegreeOriginal, avgDegreeSparse, degreeCorrelation;

        public SparsificationQuality(int ov, int oe, int sv, int se, double er, boolean cp,
                                      int oc, int sc, double od, double sd, double ado, double ads, double dc) {
            this.originalVertices = ov; this.originalEdges = oe;
            this.sparseVertices = sv; this.sparseEdges = se;
            this.edgeReduction = er; this.connectivityPreserved = cp;
            this.originalComponents = oc; this.sparseComponents = sc;
            this.originalDensity = od; this.sparseDensity = sd;
            this.avgDegreeOriginal = ado; this.avgDegreeSparse = ads;
            this.degreeCorrelation = dc;
        }

        public String getGrade() {
            double score = (connectivityPreserved ? 40 : 0) + 30 * edgeReduction + 30 * Math.max(0, degreeCorrelation);
            if (score >= 90) return "A";
            if (score >= 80) return "B";
            if (score >= 70) return "C";
            if (score >= 60) return "D";
            return "F";
        }
    }

    public SparsificationQuality evaluateQuality(Graph<String, Edge> sparse) {
        if (sparse == null) throw new IllegalArgumentException("Sparse graph must not be null");
        int ov = graph.getVertexCount(), oe = graph.getEdgeCount();
        int sv = sparse.getVertexCount(), se = sparse.getEdgeCount();
        double er = oe > 0 ? 1.0 - (double) se / oe : 0;
        int oc = countComponents(graph), sc = countComponents(sparse);
        double od = ov > 1 ? 2.0 * oe / (ov * (ov - 1.0)) : 0;
        double sd = sv > 1 ? 2.0 * se / (sv * (sv - 1.0)) : 0;
        double ado = ov > 0 ? 2.0 * oe / ov : 0;
        double ads = sv > 0 ? 2.0 * se / sv : 0;
        return new SparsificationQuality(ov, oe, sv, se, er, oc == sc, oc, sc, od, sd, ado, ads, degreeCorr(graph, sparse));
    }

    private int countComponents(Graph<String, Edge> g) {
        return GraphUtils.findComponents(g).size();
    }

    private double degreeCorr(Graph<String, Edge> g1, Graph<String, Edge> g2) {
        List<String> common = new ArrayList<String>();
        for (String vtx : g1.getVertices()) { if (g2.containsVertex(vtx)) common.add(vtx); }
        if (common.size() < 2) return 0;
        double[] x = new double[common.size()], y = new double[common.size()];
        for (int i = 0; i < common.size(); i++) { x[i] = g1.degree(common.get(i)); y[i] = g2.degree(common.get(i)); }
        return pearson(x, y);
    }

    private double pearson(double[] x, double[] y) {
        int n = x.length; double mx = 0, my = 0;
        for (int i = 0; i < n; i++) { mx += x[i]; my += y[i]; }
        mx /= n; my /= n;
        double num = 0, dx = 0, dy = 0;
        for (int i = 0; i < n; i++) { num += (x[i]-mx)*(y[i]-my); dx += (x[i]-mx)*(x[i]-mx); dy += (y[i]-my)*(y[i]-my); }
        return (dx == 0 || dy == 0) ? 0 : num / Math.sqrt(dx * dy);
    }

    private Map<Edge, Double> computeEdgeBetweenness() {
        Map<Edge, Double> bet = new LinkedHashMap<Edge, Double>();
        for (Edge e : graph.getEdges()) bet.put(e, 0.0);
        for (String s : graph.getVertices()) {
            Stack<String> stack = new Stack<String>();
            Map<String, List<String>> pred = new HashMap<String, List<String>>();
            Map<String, Integer> sigma = new HashMap<String, Integer>();
            Map<String, Integer> dist = new HashMap<String, Integer>();
            for (String vtx : graph.getVertices()) { pred.put(vtx, new ArrayList<String>()); sigma.put(vtx, 0); dist.put(vtx, -1); }
            sigma.put(s, 1); dist.put(s, 0);
            Queue<String> q = new ArrayDeque<String>(); q.add(s);
            while (!q.isEmpty()) {
                String vtx = q.poll(); stack.push(vtx);
                for (String w : graph.getNeighbors(vtx)) {
                    if (dist.get(w) < 0) { q.add(w); dist.put(w, dist.get(vtx) + 1); }
                    if (dist.get(w) == dist.get(vtx) + 1) { sigma.put(w, sigma.get(w) + sigma.get(vtx)); pred.get(w).add(vtx); }
                }
            }
            Map<String, Double> delta = new HashMap<String, Double>();
            for (String vtx : graph.getVertices()) delta.put(vtx, 0.0);
            while (!stack.isEmpty()) {
                String w = stack.pop();
                for (String vtx : pred.get(w)) {
                    double c = (double) sigma.get(vtx) / sigma.get(w) * (1.0 + delta.get(w));
                    Edge e = graph.findEdge(vtx, w);
                    if (e != null) bet.put(e, bet.get(e) + c);
                    delta.put(vtx, delta.get(vtx) + c);
                }
            }
        }
        for (Edge e : bet.keySet()) bet.put(e, bet.get(e) / 2.0);
        return bet;
    }

    /** Compares multiple sparsification methods. */
    public Map<String, SparsificationQuality> compareAllMethods() {
        Map<String, SparsificationQuality> r = new LinkedHashMap<String, SparsificationQuality>();
        r.put("Spanning Tree", evaluateQuality(spanningTreeSparsify()));
        r.put("Random (50%)", evaluateQuality(randomSparsify(0.5, 42)));
        r.put("Random (30%)", evaluateQuality(randomSparsify(0.3, 42)));
        r.put("Importance (50%)", evaluateQuality(importanceSparsify(0.5)));
        r.put("Importance (30%)", evaluateQuality(importanceSparsify(0.3)));
        r.put("Local (k=2)", evaluateQuality(localSparsify(2)));
        return r;
    }

    // ── Sparseness ───────────────────────────────────────────────────

    public static class SparsenessMetrics {
        public final int vertices, edges, maxDegree, minDegree, bridgeCount;
        public final double density, avgDegree, edgeToVertexRatio, bridgeFraction;
        public final boolean isSparse, isTree;
        public final String classification;

        public SparsenessMetrics(int v, int e, double d, double ad, int maxD, int minD,
                                  double evr, boolean sp, boolean tree, int bc, double bf, String cl) {
            this.vertices = v; this.edges = e; this.density = d; this.avgDegree = ad;
            this.maxDegree = maxD; this.minDegree = minD; this.edgeToVertexRatio = evr;
            this.isSparse = sp; this.isTree = tree; this.bridgeCount = bc;
            this.bridgeFraction = bf; this.classification = cl;
        }
    }

    public SparsenessMetrics analyzeSparseness() {
        int v = graph.getVertexCount(), e = graph.getEdgeCount();
        double density = v > 1 ? 2.0 * e / (v * (v - 1.0)) : 0;
        double avgDeg = v > 0 ? 2.0 * e / v : 0;
        int maxDeg = 0, minDeg = v > 0 ? Integer.MAX_VALUE : 0;
        for (String vtx : graph.getVertices()) { int d = graph.degree(vtx); if (d > maxDeg) maxDeg = d; if (d < minDeg) minDeg = d; }
        double evr = v > 0 ? (double) e / v : 0;
        int comp = countComponents(graph);
        boolean isTree = v > 0 && e == v - comp;
        int bc = findBridges().size();
        String cl;
        if (isTree) cl = "Tree/Forest";
        else if (density < 0.1) cl = "Very Sparse";
        else if (density < 0.3) cl = "Sparse";
        else if (density < 0.6) cl = "Moderate";
        else if (density < 0.8) cl = "Dense";
        else cl = "Very Dense";
        return new SparsenessMetrics(v, e, density, avgDeg, maxDeg, minDeg, evr, density < 0.5, isTree, bc, e > 0 ? (double) bc / e : 0, cl);
    }

    /** Generates a comprehensive text report. */
    public String generateReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════\n");
        sb.append("       GRAPH SPARSIFICATION ANALYSIS REPORT       \n");
        sb.append("═══════════════════════════════════════════════════\n\n");
        SparsenessMetrics sm = analyzeSparseness();
        sb.append("── Graph Sparseness ──\n");
        sb.append(String.format("  Vertices:          %d\n", sm.vertices));
        sb.append(String.format("  Edges:             %d\n", sm.edges));
        sb.append(String.format("  Density:           %.4f\n", sm.density));
        sb.append(String.format("  Avg Degree:        %.2f\n", sm.avgDegree));
        sb.append(String.format("  Degree Range:      [%d, %d]\n", sm.minDegree, sm.maxDegree));
        sb.append(String.format("  Classification:    %s\n", sm.classification));
        sb.append(String.format("  Bridges:           %d (%.1f%%)\n", sm.bridgeCount, sm.bridgeFraction * 100));
        sb.append("\n");

        Set<Edge> bridges = findBridges();
        if (!bridges.isEmpty()) {
            sb.append("── Bridge Edges (critical) ──\n");
            int cnt = 0;
            for (Edge e : bridges) {
                if (cnt >= 10) { sb.append(String.format("  ... and %d more\n", bridges.size() - 10)); break; }
                sb.append(String.format("  %s — %s\n", e.getVertex1(), e.getVertex2()));
                cnt++;
            }
            sb.append("\n");
        }

        if (graph.getEdgeCount() > 0) {
            sb.append("── Method Comparison ──\n");
            sb.append(String.format("  %-20s %6s %8s %6s %6s\n", "Method", "Edges", "Reduced", "Conn?", "Grade"));
            sb.append("  --------------------------------------------------\n");
            Map<String, SparsificationQuality> comp = compareAllMethods();
            for (Map.Entry<String, SparsificationQuality> entry : comp.entrySet()) {
                SparsificationQuality q = entry.getValue();
                sb.append(String.format("  %-20s %6d %7.1f%% %6s %6s\n",
                    entry.getKey(), q.sparseEdges, q.edgeReduction * 100,
                    q.connectivityPreserved ? "Yes" : "No", q.getGrade()));
            }
            sb.append("\n");

            String best = null; double bestScore = -1;
            for (Map.Entry<String, SparsificationQuality> entry : comp.entrySet()) {
                SparsificationQuality q = entry.getValue();
                double score = (q.connectivityPreserved ? 50 : 0) + 30 * q.edgeReduction + 20 * Math.max(0, q.degreeCorrelation);
                if (score > bestScore) { bestScore = score; best = entry.getKey(); }
            }
            if (best != null) sb.append(String.format("  Recommended: %s\n\n", best));

            sb.append("── Top Important Edges ──\n");
            Map<Edge, Double> importance = scoreEdgeImportance();
            List<Map.Entry<Edge, Double>> sorted = new ArrayList<Map.Entry<Edge, Double>>(importance.entrySet());
            Collections.sort(sorted, (Map.Entry<Edge, Double> a, Map.Entry<Edge, Double> b) -> { return Double.compare(b.getValue(), a.getValue()); });
            for (int i = 0; i < Math.min(10, sorted.size()); i++) {
                Map.Entry<Edge, Double> entry = sorted.get(i);
                Edge e = entry.getKey();
                sb.append(String.format("  %s — %s  (importance: %.3f)\n", e.getVertex1(), e.getVertex2(), entry.getValue()));
            }
        }
        sb.append("\n═══════════════════════════════════════════════════\n");
        return sb.toString();
    }
}
