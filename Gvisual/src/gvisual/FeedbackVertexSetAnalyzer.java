package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.DirectedSparseGraph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Feedback Vertex Set (FVS) analysis for graphs.
 *
 * <p>A feedback vertex set is a set of vertices whose removal makes the graph acyclic
 * (a forest for undirected graphs, a DAG for directed graphs). The minimum FVS problem
 * is NP-hard, so this analyzer provides both exact and heuristic algorithms.</p>
 *
 * <h3>Algorithms</h3>
 * <ul>
 *   <li><b>Greedy FVS:</b> Max-degree heuristic with iterative cycle breaking — O(V·E)</li>
 *   <li><b>Exact minimum FVS:</b> Backtracking with pruning — O(2^V)</li>
 *   <li><b>Reduction rules:</b> Degree-0/1 removal, self-loop, degree-2 bypass</li>
 *   <li><b>Feedback Edge Set:</b> Complement approach via spanning tree</li>
 *   <li><b>Bounds:</b> Lower bound from cycle packing, upper bound from greedy</li>
 *   <li><b>Vertex criticality:</b> Per-vertex impact on cycle structure</li>
 * </ul>
 *
 * <h3>Applications</h3>
 * <ul>
 *   <li>Deadlock resolution in operating systems</li>
 *   <li>Circuit testing (breaking feedback loops)</li>
 *   <li>Dependency cycle breaking in build systems</li>
 *   <li>Bayesian network construction from undirected models</li>
 *   <li>Program analysis (loop identification)</li>
 * </ul>
 *
 * @author zalenix
 */
public class FeedbackVertexSetAnalyzer {

    private final Graph<String, edge> graph;
    private final boolean directed;

    public FeedbackVertexSetAnalyzer(Graph<String, edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.directed = graph instanceof DirectedSparseGraph;
    }

    // ── Core verification ─────────────────────────────────────────

    public boolean isValidFVS(Set<String> fvs) {
        if (fvs == null) return false;
        Set<String> remaining = new HashSet<>(graph.getVertices());
        remaining.removeAll(fvs);
        return !GraphUtils.hasCycleInSubgraph(graph, remaining, directed);
    }

    // ── Greedy FVS ────────────────────────────────────────────────

    public Set<String> greedyFVS() {
        Set<String> fvs = new LinkedHashSet<>();
        Set<String> remaining = new HashSet<>(graph.getVertices());
        applyReductions(remaining, fvs);

        while (GraphUtils.hasCycleInSubgraph(graph, remaining, directed)) {
            String best = null;
            int bestDeg = -1;
            for (String v : remaining) {
                int deg = countDegreeInSubgraph(v, remaining);
                if (deg > bestDeg) { bestDeg = deg; best = v; }
            }
            if (best == null) break;
            fvs.add(best);
            remaining.remove(best);
        }

        // Minimality pass
        List<String> ordered = new ArrayList<>(fvs);
        for (int i = ordered.size() - 1; i >= 0; i--) {
            String v = ordered.get(i);
            fvs.remove(v);
            remaining.add(v);
            if (GraphUtils.hasCycleInSubgraph(graph, remaining, directed)) {
                remaining.remove(v);
                fvs.add(v);
            }
        }
        return fvs;
    }

    private void applyReductions(Set<String> remaining, Set<String> fvs) {
        boolean changed = true;
        while (changed) {
            changed = false;
            Iterator<String> it = remaining.iterator();
            while (it.hasNext()) {
                String v = it.next();
                if (countDegreeInSubgraph(v, remaining) <= 1) {
                    it.remove();
                    changed = true;
                }
            }
        }
    }

    private int countDegreeInSubgraph(String v, Set<String> vertices) {
        if (directed) {
            return getSuccessorsInSubgraph(v, vertices).size() + getPredecessorsInSubgraph(v, vertices).size();
        }
        return getNeighborsInSubgraph(v, vertices).size();
    }

    private List<String> getNeighborsInSubgraph(String v, Set<String> vertices) {
        List<String> result = new ArrayList<>();
        for (String n : graph.getNeighbors(v)) {
            if (vertices.contains(n)) result.add(n);
        }
        return result;
    }

    private List<String> getSuccessorsInSubgraph(String v, Set<String> vertices) {
        List<String> result = new ArrayList<>();
        for (String n : graph.getSuccessors(v)) {
            if (vertices.contains(n)) result.add(n);
        }
        return result;
    }

    private List<String> getPredecessorsInSubgraph(String v, Set<String> vertices) {
        List<String> result = new ArrayList<>();
        for (String n : graph.getPredecessors(v)) {
            if (vertices.contains(n)) result.add(n);
        }
        return result;
    }

    // ── Exact minimum FVS ─────────────────────────────────────────

    public Set<String> exactMinimumFVS() {
        List<String> verts = new ArrayList<>(graph.getVertices());
        if (verts.size() > 25) return null;

        Set<String> allVerts = new HashSet<>(verts);
        if (!GraphUtils.hasCycleInSubgraph(graph, allVerts, directed)) return new HashSet<>();

        Set<String> reduced = new HashSet<>(verts);
        Set<String> forced = new LinkedHashSet<>();
        applyReductions(reduced, forced);

        if (!GraphUtils.hasCycleInSubgraph(graph, reduced, directed)) return forced;

        List<String> candidates = new ArrayList<>(reduced);
        Set<String> bestFVS = new HashSet<>(reduced);
        Set<String> currentFVS = new LinkedHashSet<>();
        backtrack(candidates, 0, new HashSet<>(reduced), currentFVS, bestFVS);

        bestFVS.addAll(forced);
        return bestFVS;
    }

    private void backtrack(List<String> candidates, int idx, Set<String> remaining,
                           Set<String> current, Set<String> best) {
        if (!GraphUtils.hasCycleInSubgraph(graph, remaining, directed)) {
            if (current.size() < best.size()) { best.clear(); best.addAll(current); }
            return;
        }
        if (current.size() >= best.size() - 1) return;
        if (idx >= candidates.size()) return;

        String v = candidates.get(idx);
        current.add(v); remaining.remove(v);
        backtrack(candidates, idx + 1, remaining, current, best);
        current.remove(v); remaining.add(v);
        backtrack(candidates, idx + 1, remaining, current, best);
    }

    // ── Feedback Edge Set ─────────────────────────────────────────

    public Set<edge> feedbackEdgeSet() {
        Set<String> vertices = new HashSet<>(graph.getVertices());
        if (vertices.isEmpty()) return new HashSet<>();

        Set<edge> treeEdges = new HashSet<>();
        Set<String> visited = new HashSet<>();

        for (String start : vertices) {
            if (visited.contains(start)) continue;
            Queue<String> queue = new LinkedList<>();
            queue.add(start); visited.add(start);
            while (!queue.isEmpty()) {
                String v = queue.poll();
                Collection<edge> incident = graph.getIncidentEdges(v);
                if (incident == null) continue;
                for (edge e : incident) {
                    String other = GraphUtils.getOtherEnd(e, v);
                    if (other != null && !visited.contains(other)) {
                        visited.add(other); treeEdges.add(e); queue.add(other);
                    }
                }
            }
        }

        Set<edge> feedback = new HashSet<>(graph.getEdges());
        feedback.removeAll(treeEdges);
        return feedback;
    }

    // ── Cycle rank ────────────────────────────────────────────────

    public int cycleRank() {
        return graph.getEdgeCount() - graph.getVertexCount() + countComponents();
    }

    private int countComponents() {
        return GraphUtils.findComponents(graph).size();
    }

    // ── Bounds ────────────────────────────────────────────────────

    public int lowerBound() {
        int cr = cycleRank();
        if (cr == 0) return 0;
        int maxDeg = 0;
        for (String v : graph.getVertices()) {
            int deg = graph.getNeighborCount(v);
            if (deg > maxDeg) maxDeg = deg;
        }
        if (maxDeg <= 1) return 0;
        return Math.max(1, (int) Math.ceil((double) cr / (maxDeg - 1)));
    }

    public int upperBound() { return greedyFVS().size(); }

    // ── Vertex criticality ────────────────────────────────────────

    public Map<String, Integer> vertexCriticality() {
        Map<String, Integer> criticality = new LinkedHashMap<>();
        int baseCR = cycleRank();
        for (String v : graph.getVertices()) {
            Set<String> without = new HashSet<>(graph.getVertices());
            without.remove(v);
            criticality.put(v, baseCR - GraphUtils.cycleRankOfSubgraph(graph, without));
        }
        return criticality.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    // ── Disjoint cycle packing ────────────────────────────────────

    public List<List<String>> disjointCyclePacking() {
        List<List<String>> packing = new ArrayList<>();
        Set<String> remaining = new HashSet<>(graph.getVertices());
        while (true) {
            List<String> cycle = findCycleInSubgraph(remaining);
            if (cycle == null) break;
            packing.add(cycle);
            remaining.removeAll(cycle);
        }
        return packing;
    }

    private List<String> findCycleInSubgraph(Set<String> vertices) {
        if (vertices.size() < 3) return null;
        Set<String> visited = new HashSet<>();
        Map<String, String> parent = new HashMap<>();
        for (String start : vertices) {
            if (visited.contains(start)) continue;
            Queue<String> queue = new LinkedList<>();
            queue.add(start); visited.add(start); parent.put(start, null);
            while (!queue.isEmpty()) {
                String v = queue.poll();
                for (String n : getNeighborsInSubgraph(v, vertices)) {
                    if (!visited.contains(n)) {
                        visited.add(n); parent.put(n, v); queue.add(n);
                    } else if (!n.equals(parent.get(v))) {
                        return reconstructCycle(v, n, parent);
                    }
                }
            }
        }
        return null;
    }

    private List<String> reconstructCycle(String u, String v, Map<String, String> parent) {
        List<String> pathU = new ArrayList<>(), pathV = new ArrayList<>();
        String curr = u;
        while (curr != null) { pathU.add(curr); curr = parent.get(curr); }
        curr = v;
        while (curr != null) { pathV.add(curr); curr = parent.get(curr); }

        Set<String> ancestorsU = new HashSet<>(pathU);
        String lca = null;
        for (String a : pathV) { if (ancestorsU.contains(a)) { lca = a; break; } }

        List<String> cycle = new ArrayList<>();
        for (String a : pathU) { cycle.add(a); if (a.equals(lca)) break; }
        List<String> vToLca = new ArrayList<>();
        for (String a : pathV) { if (a.equals(lca)) break; vToLca.add(a); }
        Collections.reverse(vToLca);
        cycle.addAll(vToLca);
        return cycle;
    }

    // ── Approximation ratio ───────────────────────────────────────

    public double approximationRatio() {
        int lb = lowerBound();
        if (lb == 0) return GraphUtils.hasCycleInSubgraph(graph, new HashSet<>(graph.getVertices()), directed) ? -1 : 1.0;
        return (double) upperBound() / lb;
    }

    // ── Report ────────────────────────────────────────────────────

    public static class FVSReport {
        public final int vertexCount, edgeCount, cycleRank, lowerBound, upperBound;
        public final boolean isAcyclic;
        public final Set<String> greedyFVS, exactFVS;
        public final Set<edge> feedbackEdgeSet;
        public final Map<String, Integer> criticality;
        public final List<List<String>> cyclePacking;
        public final double approximationRatio;

        public FVSReport(int vertexCount, int edgeCount, int cycleRank, boolean isAcyclic,
                         Set<String> greedyFVS, Set<String> exactFVS, Set<edge> feedbackEdgeSet,
                         int lowerBound, int upperBound, Map<String, Integer> criticality,
                         List<List<String>> cyclePacking, double approximationRatio) {
            this.vertexCount = vertexCount; this.edgeCount = edgeCount;
            this.cycleRank = cycleRank; this.isAcyclic = isAcyclic;
            this.greedyFVS = greedyFVS; this.exactFVS = exactFVS;
            this.feedbackEdgeSet = feedbackEdgeSet;
            this.lowerBound = lowerBound; this.upperBound = upperBound;
            this.criticality = criticality; this.cyclePacking = cyclePacking;
            this.approximationRatio = approximationRatio;
        }
    }

    public FVSReport generateReport() {
        Set<String> allVerts = new HashSet<>(graph.getVertices());
        boolean acyclic = !GraphUtils.hasCycleInSubgraph(graph, allVerts, directed);
        Set<String> greedy = greedyFVS();
        return new FVSReport(graph.getVertexCount(), graph.getEdgeCount(), cycleRank(), acyclic,
                greedy, exactMinimumFVS(), feedbackEdgeSet(), lowerBound(), greedy.size(),
                acyclic ? new LinkedHashMap<>() : vertexCriticality(),
                disjointCyclePacking(), approximationRatio());
    }

    public String textReport() {
        FVSReport r = generateReport();
        StringBuilder sb = new StringBuilder();
        sb.append("═══ Feedback Vertex Set Analysis ═══\n\n");
        sb.append(String.format("Graph: %d vertices, %d edges\n", r.vertexCount, r.edgeCount));
        sb.append(String.format("Cycle rank: %d\nAcyclic: %s\n\n", r.cycleRank, r.isAcyclic));
        if (r.isAcyclic) { sb.append("Graph is acyclic — no feedback vertex set needed.\n"); return sb.toString(); }

        sb.append("── Feedback Vertex Set ──\n");
        sb.append(String.format("  Greedy FVS (%d): %s\n", r.greedyFVS.size(), r.greedyFVS));
        if (r.exactFVS != null) sb.append(String.format("  Exact FVS  (%d): %s\n", r.exactFVS.size(), r.exactFVS));
        else sb.append("  Exact FVS: (graph too large)\n");
        sb.append(String.format("  Bounds: [%d, %d]\n", r.lowerBound, r.upperBound));
        if (r.approximationRatio > 0) sb.append(String.format("  Approx ratio: %.2f\n", r.approximationRatio));

        sb.append(String.format("\n── Feedback Edge Set (%d edges) ──\n", r.feedbackEdgeSet.size()));
        sb.append(String.format("\n── Cycle Packing (%d disjoint cycles) ──\n", r.cyclePacking.size()));
        for (int i = 0; i < r.cyclePacking.size(); i++)
            sb.append(String.format("  Cycle %d: %s\n", i + 1, r.cyclePacking.get(i)));

        sb.append("\n── Vertex Criticality (top 10) ──\n");
        int shown = 0;
        for (Map.Entry<String, Integer> entry : r.criticality.entrySet()) {
            if (shown++ >= 10) break;
            sb.append(String.format("  %s: %d cycle(s) broken\n", entry.getKey(), entry.getValue()));
        }
        return sb.toString();
    }
}

