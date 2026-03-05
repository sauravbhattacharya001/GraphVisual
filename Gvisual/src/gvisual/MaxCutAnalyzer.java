package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Maximum Cut (MaxCut) analyzer for undirected graphs.
 *
 * <p>The <b>Maximum Cut</b> problem partitions vertices into two disjoint sets
 * S and T such that the number (or total weight) of edges crossing the
 * partition is maximized. MaxCut is NP-hard in general, so this analyzer
 * provides multiple algorithms:</p>
 *
 * <ul>
 *   <li><b>Greedy</b> — O(V·E) heuristic that assigns each vertex to the
 *       side that maximizes the current cut</li>
 *   <li><b>Local search</b> — iterative improvement by flipping vertices
 *       until no single flip increases the cut (guaranteed ≥ |E|/2)</li>
 *   <li><b>Random + local search</b> — multiple random restarts with local
 *       search refinement for better solutions</li>
 *   <li><b>Exact (brute-force)</b> — exhaustive enumeration for small graphs
 *       (≤ 20 vertices), guaranteeing the optimal solution</li>
 * </ul>
 *
 * <p>Supports both unweighted and weighted graphs.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * MaxCutAnalyzer analyzer = new MaxCutAnalyzer(graph);
 * MaxCutAnalyzer.CutResult greedy = analyzer.computeGreedy();
 * MaxCutAnalyzer.CutResult local = analyzer.computeLocalSearch();
 * MaxCutAnalyzer.CutResult best = analyzer.computeBest();
 * MaxCutAnalyzer.CutResult exact = analyzer.computeExact(); // small graphs
 * String report = analyzer.generateReport();
 * </pre>
 *
 * @author zalenix
 */
public class MaxCutAnalyzer {

    private final Graph<String, edge> graph;
    private static final int EXACT_LIMIT = 20;
    private static final int RANDOM_RESTARTS = 25;

    public MaxCutAnalyzer(Graph<String, edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
    }

    public static class CutResult {
        private final Set<String> setS;
        private final Set<String> setT;
        private final double cutValue;
        private final int cutEdgeCount;
        private final List<edge> cutEdges;
        private final String algorithm;
        private final int totalEdges;
        private final double cutRatio;

        public CutResult(Set<String> setS, Set<String> setT,
                          double cutValue, int cutEdgeCount,
                          List<edge> cutEdges, String algorithm,
                          int totalEdges) {
            this.setS = Collections.unmodifiableSet(new LinkedHashSet<String>(setS));
            this.setT = Collections.unmodifiableSet(new LinkedHashSet<String>(setT));
            this.cutValue = cutValue;
            this.cutEdgeCount = cutEdgeCount;
            this.cutEdges = Collections.unmodifiableList(new ArrayList<edge>(cutEdges));
            this.algorithm = algorithm;
            this.totalEdges = totalEdges;
            this.cutRatio = totalEdges > 0 ? (double) cutEdgeCount / totalEdges : 0.0;
        }

        public Set<String> getSetS() { return setS; }
        public Set<String> getSetT() { return setT; }
        public double getCutValue() { return cutValue; }
        public int getCutEdgeCount() { return cutEdgeCount; }
        public List<edge> getCutEdges() { return cutEdges; }
        public String getAlgorithm() { return algorithm; }
        public int getTotalEdges() { return totalEdges; }
        public double getCutRatio() { return cutRatio; }
    }

    public CutResult computeGreedy() {
        Collection<String> vertices = graph.getVertices();
        if (vertices.isEmpty()) return emptyCut("Greedy");

        List<String> sorted = new ArrayList<String>(vertices);
        Collections.sort(sorted, new Comparator<String>() {
            public int compare(String a, String b) {
                return graph.degree(b) - graph.degree(a);
            }
        });

        Set<String> setS = new LinkedHashSet<String>();
        Set<String> setT = new LinkedHashSet<String>();

        for (String v : sorted) {
            int toS = countNeighborsIn(v, setS);
            int toT = countNeighborsIn(v, setT);
            if (toS >= toT) {
                setT.add(v);
            } else {
                setS.add(v);
            }
        }
        return buildResult(setS, setT, "Greedy");
    }

    public CutResult computeLocalSearch() {
        Collection<String> vertices = graph.getVertices();
        if (vertices.isEmpty()) return emptyCut("LocalSearch");

        CutResult initial = computeGreedy();
        Set<String> setS = new LinkedHashSet<String>(initial.getSetS());
        Set<String> setT = new LinkedHashSet<String>(initial.getSetT());
        return refineByFlipping(setS, setT, "LocalSearch");
    }

    public CutResult computeRandomLocalSearch() {
        return computeRandomLocalSearch(RANDOM_RESTARTS);
    }

    public CutResult computeRandomLocalSearch(int restarts) {
        if (restarts < 1) throw new IllegalArgumentException("Restarts must be >= 1");
        Collection<String> vertices = graph.getVertices();
        if (vertices.isEmpty()) return emptyCut("RandomLocalSearch");

        List<String> vertexList = new ArrayList<String>(vertices);
        Random rng = new Random(42);
        CutResult best = null;

        for (int r = 0; r < restarts; r++) {
            Set<String> setS = new LinkedHashSet<String>();
            Set<String> setT = new LinkedHashSet<String>();
            for (String v : vertexList) {
                if (rng.nextBoolean()) setS.add(v); else setT.add(v);
            }
            if (setS.isEmpty() && !setT.isEmpty()) {
                String m = setT.iterator().next(); setT.remove(m); setS.add(m);
            } else if (setT.isEmpty() && !setS.isEmpty()) {
                String m = setS.iterator().next(); setS.remove(m); setT.add(m);
            }
            CutResult refined = refineByFlipping(setS, setT, "RandomLocalSearch");
            if (best == null || refined.getCutValue() > best.getCutValue()) best = refined;
        }
        return best;
    }

    public CutResult computeExact() {
        Collection<String> vertices = graph.getVertices();
        if (vertices.isEmpty()) return emptyCut("Exact");
        if (vertices.size() > EXACT_LIMIT) {
            throw new IllegalStateException(
                "Exact MaxCut only supported for graphs with <= " + EXACT_LIMIT
                + " vertices (got " + vertices.size() + ")");
        }

        List<String> vertexList = new ArrayList<String>(vertices);
        int n = vertexList.size();
        long totalMasks = 1L << n;
        double bestValue = -1;
        long bestMask = 0;

        for (long mask = 0; mask < totalMasks; mask++) {
            double value = evaluateCutByMask(vertexList, mask);
            if (value > bestValue) { bestValue = value; bestMask = mask; }
        }

        Set<String> setS = new LinkedHashSet<String>();
        Set<String> setT = new LinkedHashSet<String>();
        for (int i = 0; i < n; i++) {
            if ((bestMask & (1L << i)) != 0) setS.add(vertexList.get(i));
            else setT.add(vertexList.get(i));
        }
        return buildResult(setS, setT, "Exact");
    }

    public CutResult computeBest() {
        if (graph.getVertexCount() <= EXACT_LIMIT) return computeExact();
        return computeRandomLocalSearch();
    }

    public double computeUpperBound() {
        int edgeCount = graph.getEdgeCount();
        int n = graph.getVertexCount();
        if (n <= 1 || edgeCount == 0) return 0.0;
        double totalWeight = 0;
        for (edge e : graph.getEdges()) totalWeight += Math.max(e.getWeight(), 1.0f);
        double edwardsBound = (double) edgeCount / 2.0 + (double) (n - 1) / 4.0;
        return Math.min(totalWeight, edwardsBound);
    }

    public double computeLowerBound() {
        int edgeCount = graph.getEdgeCount();
        if (edgeCount == 0) return 0.0;
        return (double) edgeCount / 2.0;
    }

    public Map<String, Integer> computeVertexContributions(CutResult result) {
        Map<String, Integer> contributions = new LinkedHashMap<String, Integer>();
        for (String v : graph.getVertices()) contributions.put(v, 0);
        for (edge e : result.getCutEdges()) {
            String v1 = e.getVertex1(), v2 = e.getVertex2();
            if (contributions.containsKey(v1)) contributions.put(v1, contributions.get(v1) + 1);
            if (contributions.containsKey(v2)) contributions.put(v2, contributions.get(v2) + 1);
        }
        return contributions;
    }

    public String findBestFlipCandidate(CutResult result) {
        if (graph.getVertexCount() == 0) return null;
        String bestVertex = null;
        double bestGain = Double.NEGATIVE_INFINITY;
        for (String v : graph.getVertices()) {
            double gain = computeFlipGain(v, result.getSetS(), result.getSetT());
            if (gain > bestGain) { bestGain = gain; bestVertex = v; }
        }
        return bestVertex;
    }

    public CutResult computeBalanced(int tolerance) {
        if (tolerance < 0) throw new IllegalArgumentException("Tolerance must be >= 0");
        Collection<String> vertices = graph.getVertices();
        if (vertices.isEmpty()) return emptyCut("Balanced");

        List<String> sorted = new ArrayList<String>(vertices);
        Collections.sort(sorted, new Comparator<String>() {
            public int compare(String a, String b) { return graph.degree(b) - graph.degree(a); }
        });

        int n = sorted.size(), half = n / 2;
        Set<String> setS = new LinkedHashSet<String>();
        Set<String> setT = new LinkedHashSet<String>();

        for (String v : sorted) {
            int toS = countNeighborsIn(v, setS), toT = countNeighborsIn(v, setT);
            boolean preferT = toS >= toT;
            if (preferT && setT.size() < half + tolerance + 1) setT.add(v);
            else if (!preferT && setS.size() < half + tolerance + 1) setS.add(v);
            else if (setS.size() < setT.size()) setS.add(v);
            else setT.add(v);
        }

        boolean improved = true;
        while (improved) {
            improved = false;
            for (String v : new ArrayList<String>(graph.getVertices())) {
                boolean inS = setS.contains(v);
                Set<String> from = inS ? setS : setT, to = inS ? setT : setS;
                if (Math.abs((from.size() - 1) - (to.size() + 1)) > tolerance) continue;
                double gain = computeFlipGain(v, setS, setT);
                if (gain > 0) { from.remove(v); to.add(v); improved = true; }
            }
        }
        return buildResult(setS, setT, "Balanced");
    }

    public List<CutResult> compareAlgorithms() {
        List<CutResult> results = new ArrayList<CutResult>();
        results.add(computeGreedy());
        results.add(computeLocalSearch());
        results.add(computeRandomLocalSearch());
        if (graph.getVertexCount() <= EXACT_LIMIT) results.add(computeExact());
        Collections.sort(results, new Comparator<CutResult>() {
            public int compare(CutResult a, CutResult b) {
                return Double.compare(b.getCutValue(), a.getCutValue());
            }
        });
        return results;
    }

    public String generateReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== MaxCut Analysis Report ===\n\n");
        int n = graph.getVertexCount(), m = graph.getEdgeCount();
        sb.append("Graph: ").append(n).append(" vertices, ").append(m).append(" edges\n\n");

        if (n == 0) { sb.append("Empty graph — no cut possible.\n"); return sb.toString(); }

        sb.append("── Bounds ──\n");
        sb.append(String.format("  Lower bound (|E|/2): %.1f\n", computeLowerBound()));
        sb.append(String.format("  Upper bound: %.1f\n\n", computeUpperBound()));

        List<CutResult> results = compareAlgorithms();
        sb.append("── Algorithm Comparison ──\n");
        for (CutResult r : results) {
            sb.append(String.format("  %-20s  cut=%.1f  edges=%d/%d (%.1f%%)\n",
                r.getAlgorithm(), r.getCutValue(), r.getCutEdgeCount(),
                r.getTotalEdges(), r.getCutRatio() * 100));
        }
        sb.append("\n");

        CutResult best = results.get(0);
        sb.append("── Best Cut (").append(best.getAlgorithm()).append(") ──\n");
        sb.append("  Set S (").append(best.getSetS().size()).append("): ").append(best.getSetS()).append("\n");
        sb.append("  Set T (").append(best.getSetT().size()).append("): ").append(best.getSetT()).append("\n");
        sb.append(String.format("  Cut value: %.1f\n", best.getCutValue()));
        sb.append(String.format("  Cut ratio: %.1f%%\n", best.getCutRatio() * 100));

        sb.append("\n── Vertex Contributions ──\n");
        Map<String, Integer> contributions = computeVertexContributions(best);
        List<Map.Entry<String, Integer>> sortedEntries =
            new ArrayList<Map.Entry<String, Integer>>(contributions.entrySet());
        Collections.sort(sortedEntries, new Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue() - a.getValue();
            }
        });
        int shown = 0;
        for (Map.Entry<String, Integer> entry : sortedEntries) {
            if (shown >= 10) { sb.append("  ... (").append(sortedEntries.size() - 10).append(" more)\n"); break; }
            String side = best.getSetS().contains(entry.getKey()) ? "S" : "T";
            sb.append(String.format("  %-10s [%s]  contribution=%d\n", entry.getKey(), side, entry.getValue()));
            shown++;
        }

        if (graph.getVertexCount() <= EXACT_LIMIT) {
            CutResult exact = null;
            for (CutResult r : results) { if ("Exact".equals(r.getAlgorithm())) { exact = r; break; } }
            if (exact != null && exact.getCutValue() > 0) {
                sb.append("\n── Approximation Quality ──\n");
                for (CutResult r : results) {
                    if (!"Exact".equals(r.getAlgorithm())) {
                        sb.append(String.format("  %s: %.1f%% of optimal\n",
                            r.getAlgorithm(), r.getCutValue() / exact.getCutValue() * 100));
                    }
                }
            }
        }
        return sb.toString();
    }

    // ── Private helpers ─────────────────────────────────────────────

    private int countNeighborsIn(String v, Set<String> set) {
        int count = 0;
        Collection<String> neighbors = graph.getNeighbors(v);
        if (neighbors != null) { for (String nb : neighbors) { if (set.contains(nb)) count++; } }
        return count;
    }

    private double weightedNeighborsIn(String v, Set<String> set) {
        double total = 0;
        Collection<edge> incidentEdges = graph.getIncidentEdges(v);
        if (incidentEdges != null) {
            for (edge e : incidentEdges) {
                String other = GraphUtils.getOtherEnd(e, v);
                if (other != null && set.contains(other)) total += Math.max(e.getWeight(), 1.0f);
            }
        }
        return total;
    }

    private double computeFlipGain(String v, Set<String> setS, Set<String> setT) {
        boolean inS = setS.contains(v);
        return weightedNeighborsIn(v, inS ? setS : setT) - weightedNeighborsIn(v, inS ? setT : setS);
    }

    private CutResult refineByFlipping(Set<String> setS, Set<String> setT, String algorithm) {
        boolean improved = true;
        while (improved) {
            improved = false;
            for (String v : new ArrayList<String>(graph.getVertices())) {
                double gain = computeFlipGain(v, setS, setT);
                if (gain > 1e-9) {
                    if (setS.contains(v)) { setS.remove(v); setT.add(v); }
                    else { setT.remove(v); setS.add(v); }
                    improved = true;
                }
            }
        }
        return buildResult(setS, setT, algorithm);
    }

    private double evaluateCutByMask(List<String> vertexList, long mask) {
        double cutValue = 0;
        for (edge e : graph.getEdges()) {
            int i1 = vertexList.indexOf(e.getVertex1()), i2 = vertexList.indexOf(e.getVertex2());
            if (i1 < 0 || i2 < 0) continue;
            if (((mask & (1L << i1)) != 0) != ((mask & (1L << i2)) != 0))
                cutValue += Math.max(e.getWeight(), 1.0f);
        }
        return cutValue;
    }

    private CutResult buildResult(Set<String> setS, Set<String> setT, String algorithm) {
        double cutValue = 0; int cutEdgeCount = 0;
        List<edge> cutEdges = new ArrayList<edge>();
        for (edge e : graph.getEdges()) {
            if (setS.contains(e.getVertex1()) != setS.contains(e.getVertex2())) {
                cutEdges.add(e); cutEdgeCount++;
                cutValue += Math.max(e.getWeight(), 1.0f);
            }
        }
        return new CutResult(setS, setT, cutValue, cutEdgeCount, cutEdges, algorithm, graph.getEdgeCount());
    }

    private CutResult emptyCut(String algorithm) {
        return new CutResult(new LinkedHashSet<String>(), new LinkedHashSet<String>(),
            0.0, 0, new ArrayList<edge>(), algorithm, 0);
    }
}
