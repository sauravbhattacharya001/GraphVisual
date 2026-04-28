package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Clique Cover Analyzer — partitions graph vertices into the fewest cliques.
 *
 * <blockquote>
 * A <b>clique cover</b> (or <b>clique partition</b>) of a graph G is a
 * collection of cliques {C₁, C₂, …, Cₖ} such that every vertex belongs
 * to exactly one clique. The <b>clique cover number</b> θ(G) is the
 * minimum k over all such partitions.
 * </blockquote>
 *
 * <h3>Relationship to other invariants</h3>
 * <ul>
 *   <li>θ(G) = χ(Ḡ) — the clique cover number equals the chromatic number
 *       of the complement graph.</li>
 *   <li>θ(G) ≥ maximum independent set size (each clique can contain at
 *       most one vertex from any independent set).</li>
 *   <li>For perfect graphs, θ(G) equals the independence number α(G).</li>
 * </ul>
 *
 * <h3>Algorithms</h3>
 * <ul>
 *   <li><b>Greedy clique cover:</b> Iteratively finds a maximal clique among
 *       uncovered vertices and assigns them. O(V²·E) heuristic.</li>
 *   <li><b>Exact minimum clique cover:</b> Backtracking search for small
 *       graphs (≤ 20 vertices) with pruning.</li>
 *   <li><b>Cover verification:</b> Check whether a given partition is valid.</li>
 *   <li><b>Cover quality metrics:</b> Balance, average/min/max clique size.</li>
 *   <li><b>Full report:</b> Consolidated analysis with all metrics.</li>
 * </ul>
 *
 * <h3>Applications</h3>
 * <ul>
 *   <li>Register allocation (grouping non-conflicting variables)</li>
 *   <li>Scheduling (grouping compatible tasks into time slots)</li>
 *   <li>Data clustering (partitioning into dense subgroups)</li>
 *   <li>Social network analysis (community decomposition)</li>
 * </ul>
 *
 * @author zalenix
 */
public class CliqueCoverAnalyzer {

    private final Graph<String, Edge> graph;
    private final Map<String, Set<String>> adj;

    /**
     * Creates a new CliqueCoverAnalyzer.
     *
     * @param graph the JUNG graph to analyse
     */
    public CliqueCoverAnalyzer(Graph<String, Edge> graph) {
        this.graph = Objects.requireNonNull(graph, "graph must not be null");
        this.adj = new HashMap<>();
        for (String v : graph.getVertices()) {
            Set<String> neighbors = new HashSet<>(graph.getNeighbors(v));
            neighbors.remove(v);
            adj.put(v, neighbors);
        }
    }

    // ── Greedy clique cover ────────────────────────────────────────────

    /**
     * Finds a clique cover using a greedy heuristic.
     *
     * <p>Strategy: repeatedly find the largest maximal clique among the
     * remaining uncovered vertices, assign those vertices, and repeat
     * until all vertices are covered.</p>
     *
     * @return list of cliques (each clique is a set of vertex labels)
     */
    public List<Set<String>> greedyCliqueCover() {
        Set<String> uncovered = new HashSet<>(graph.getVertices());
        List<Set<String>> cover = new ArrayList<>();

        while (!uncovered.isEmpty()) {
            Set<String> clique = findGreedyMaximalClique(uncovered);
            cover.add(clique);
            uncovered.removeAll(clique);
        }

        return cover;
    }

    /**
     * Finds a maximal clique in the subgraph induced by the given vertices
     * using a greedy max-degree-first approach.
     */
    private Set<String> findGreedyMaximalClique(Set<String> candidates) {
        if (candidates.isEmpty()) return Collections.emptySet();

        // Start with the vertex having the most neighbors in candidates
        String seed = candidates.stream()
                .max(Comparator.comparingInt(v -> countNeighborsIn(v, candidates)))
                .orElse(candidates.iterator().next());

        Set<String> clique = new LinkedHashSet<>();
        clique.add(seed);

        // Candidates that are adjacent to all current clique members
        Set<String> potential = new HashSet<>(candidates);
        potential.retainAll(adj.getOrDefault(seed, Collections.emptySet()));

        // Greedily add vertices that are adjacent to all clique members
        while (!potential.isEmpty()) {
            String best = potential.stream()
                    .max(Comparator.comparingInt(v -> countNeighborsIn(v, potential)))
                    .orElse(potential.iterator().next());

            clique.add(best);
            potential.retainAll(adj.getOrDefault(best, Collections.emptySet()));
        }

        return clique;
    }

    private int countNeighborsIn(String v, Set<String> subset) {
        Set<String> nbrs = adj.getOrDefault(v, Collections.emptySet());
        int count = 0;
        for (String u : subset) {
            if (!u.equals(v) && nbrs.contains(u)) count++;
        }
        return count;
    }

    // ── Exact minimum clique cover ─────────────────────────────────────

    /**
     * Finds the minimum clique cover by exact backtracking search.
     *
     * <p><b>Warning:</b> Only practical for small graphs (≤ 20 vertices).
     * For larger graphs, use {@link #greedyCliqueCover()}.</p>
     *
     * @return the minimum clique cover, or the greedy result if the graph
     *         is too large for exact computation
     */
    public List<Set<String>> exactMinimumCliqueCover() {
        List<String> vertices = new ArrayList<>(graph.getVertices());
        if (vertices.size() > 20) {
            return greedyCliqueCover();
        }

        List<Set<String>> greedySolution = greedyCliqueCover();
        int[] bestSize = {greedySolution.size()};
        List<List<Set<String>>> bestCover = new ArrayList<>();
        bestCover.add(new ArrayList<>(greedySolution));

        backtrack(vertices, 0, new ArrayList<>(), bestSize, bestCover);

        return bestCover.get(0);
    }

    private void backtrack(List<String> vertices, int idx,
                           List<Set<String>> current,
                           int[] bestSize, List<List<Set<String>>> bestCover) {
        if (idx == vertices.size()) {
            if (current.size() < bestSize[0]) {
                bestSize[0] = current.size();
                bestCover.set(0, deepCopy(current));
            }
            return;
        }

        // Pruning: can't beat best if current is already as large
        if (current.size() >= bestSize[0]) return;

        String v = vertices.get(idx);

        // Try adding v to each existing clique (if compatible)
        for (int i = 0; i < current.size(); i++) {
            if (canAddToClique(v, current.get(i))) {
                current.get(i).add(v);
                backtrack(vertices, idx + 1, current, bestSize, bestCover);
                current.get(i).remove(v);
            }
        }

        // Try starting a new clique with v (only if it might improve)
        if (current.size() + 1 < bestSize[0]) {
            Set<String> newClique = new LinkedHashSet<>();
            newClique.add(v);
            current.add(newClique);
            backtrack(vertices, idx + 1, current, bestSize, bestCover);
            current.remove(current.size() - 1);
        }
    }

    private boolean canAddToClique(String v, Set<String> clique) {
        Set<String> nbrs = adj.getOrDefault(v, Collections.emptySet());
        for (String u : clique) {
            if (!nbrs.contains(u)) return false;
        }
        return true;
    }

    private List<Set<String>> deepCopy(List<Set<String>> cover) {
        List<Set<String>> copy = new ArrayList<>();
        for (Set<String> clique : cover) {
            copy.add(new LinkedHashSet<>(clique));
        }
        return copy;
    }

    // ── Verification ───────────────────────────────────────────────────

    /**
     * Verifies whether a given partition is a valid clique cover.
     *
     * @param cover the partition to check
     * @return true if every set is a clique and they partition all vertices
     */
    public boolean isValidCliqueCover(List<Set<String>> cover) {
        Set<String> allVertices = new HashSet<>(graph.getVertices());
        Set<String> covered = new HashSet<>();

        for (Set<String> clique : cover) {
            // Check that it's actually a clique
            for (String u : clique) {
                for (String v : clique) {
                    if (!u.equals(v) && !adj.getOrDefault(u, Collections.emptySet()).contains(v)) {
                        return false;
                    }
                }
            }
            // Check for overlaps
            for (String v : clique) {
                if (!covered.add(v)) return false; // duplicate vertex
            }
        }

        return covered.equals(allVertices);
    }

    // ── Quality metrics ────────────────────────────────────────────────

    /**
     * Computes quality metrics for a clique cover.
     *
     * @param cover the clique cover to evaluate
     * @return a map of metric names to values
     */
    public Map<String, Object> coverMetrics(List<Set<String>> cover) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("numCliques", cover.size());
        metrics.put("valid", isValidCliqueCover(cover));

        if (cover.isEmpty()) {
            metrics.put("minCliqueSize", 0);
            metrics.put("maxCliqueSize", 0);
            metrics.put("avgCliqueSize", 0.0);
            metrics.put("balance", 1.0);
            return metrics;
        }

        int min = cover.stream().mapToInt(Set::size).min().orElse(0);
        int max = cover.stream().mapToInt(Set::size).max().orElse(0);
        double avg = cover.stream().mapToInt(Set::size).average().orElse(0.0);

        metrics.put("minCliqueSize", min);
        metrics.put("maxCliqueSize", max);
        metrics.put("avgCliqueSize", Math.round(avg * 100.0) / 100.0);
        metrics.put("balance", max == 0 ? 1.0 : Math.round((double) min / max * 100.0) / 100.0);

        return metrics;
    }

    // ── Clique cover number bounds ─────────────────────────────────────

    /**
     * Computes bounds on the clique cover number θ(G).
     *
     * @return map with "lowerBound" and "upperBound"
     */
    public Map<String, Integer> cliqueCoverBounds() {
        Map<String, Integer> bounds = new LinkedHashMap<>();

        // Lower bound: max independent set size (greedy approximation)
        int lowerBound = greedyIndependenceNumber();
        bounds.put("lowerBound", lowerBound);

        // Upper bound: greedy clique cover size
        bounds.put("upperBound", greedyCliqueCover().size());

        return bounds;
    }

    /**
     * Greedy approximation of the independence number (lower bound for θ).
     */
    private int greedyIndependenceNumber() {
        Set<String> remaining = new HashSet<>(graph.getVertices());
        int count = 0;

        while (!remaining.isEmpty()) {
            // Pick vertex with fewest neighbors in remaining
            String v = remaining.stream()
                    .min(Comparator.comparingInt(u -> countNeighborsIn(u, remaining)))
                    .orElse(remaining.iterator().next());

            count++;
            remaining.remove(v);
            remaining.removeAll(adj.getOrDefault(v, Collections.emptySet()));
        }

        return count;
    }

    // ── Full report ────────────────────────────────────────────────────

    /**
     * Generates a full clique cover analysis report.
     *
     * @return multiline report string
     */
    public String fullReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══ Clique Cover Analysis ═══\n\n");

        int n = graph.getVertexCount();
        int e = graph.getEdgeCount();
        sb.append(String.format("Graph: %d vertices, %d edges%n%n", n, e));

        // Bounds
        Map<String, Integer> bounds = cliqueCoverBounds();
        sb.append(String.format("Clique cover number bounds: [%d, %d]%n%n",
                bounds.get("lowerBound"), bounds.get("upperBound")));

        // Greedy cover
        List<Set<String>> greedy = greedyCliqueCover();
        sb.append(String.format("Greedy clique cover: %d cliques%n", greedy.size()));
        for (int i = 0; i < greedy.size(); i++) {
            sb.append(String.format("  Clique %d: {%s}%n", i + 1,
                    String.join(", ", greedy.get(i))));
        }
        sb.append("\n");

        // Metrics
        Map<String, Object> metrics = coverMetrics(greedy);
        sb.append("Quality metrics:\n");
        for (Map.Entry<String, Object> entry : metrics.entrySet()) {
            sb.append(String.format("  %s: %s%n", entry.getKey(), entry.getValue()));
        }
        sb.append("\n");

        // Exact for small graphs
        if (n <= 20) {
            List<Set<String>> exact = exactMinimumCliqueCover();
            sb.append(String.format("Exact minimum clique cover: %d cliques%n", exact.size()));
            for (int i = 0; i < exact.size(); i++) {
                sb.append(String.format("  Clique %d: {%s}%n", i + 1,
                        String.join(", ", exact.get(i))));
            }
        } else {
            sb.append("(Exact solver skipped — graph has > 20 vertices)\n");
        }

        return sb.toString();
    }
}
