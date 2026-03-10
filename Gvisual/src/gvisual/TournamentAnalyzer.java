package gvisual;

import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tournament graph analyzer for directed complete graphs.
 *
 * <p>A <b>tournament</b> is a directed graph obtained by assigning a direction
 * to every edge in a complete graph. Equivalently, for every pair of distinct
 * vertices u and v, exactly one of (u→v) or (v→u) exists. Tournaments model
 * round-robin competitions, paired comparisons, and social choice.</p>
 *
 * <h3>Features</h3>
 * <ul>
 *   <li><b>Tournament validation:</b> checks if graph is a valid tournament</li>
 *   <li><b>Score sequence:</b> sorted out-degree sequence with validity check</li>
 *   <li><b>Copeland ranking:</b> vertices ordered by wins (out-degree)</li>
 *   <li><b>Condorcet winner/loser:</b> vertex beating/losing to all others</li>
 *   <li><b>King vertices:</b> vertices that reach everyone in ≤ 2 steps</li>
 *   <li><b>Hamiltonian path:</b> constructive O(n²) algorithm (every tournament has one)</li>
 *   <li><b>Dominance matrix:</b> reachability-2 matrix (direct + via intermediary)</li>
 *   <li><b>Landau's theorem check:</b> Hamiltonian path existence (always true)</li>
 *   <li><b>Strong connectivity test:</b> with condensation into SCC components</li>
 *   <li><b>Upset detection:</b> finds edges going from lower- to higher-ranked vertex</li>
 *   <li><b>Transitivity check:</b> (u beats v and v beats w) ⟹ (u beats w)?</li>
 *   <li><b>Slater ranking:</b> linear ordering minimizing disagreements (upsets)</li>
 *   <li><b>Report generation:</b> full text report of all analysis</li>
 * </ul>
 *
 * <h3>Theory</h3>
 * <ul>
 *   <li>Every tournament has a Hamiltonian path (Rédei 1934).</li>
 *   <li>A tournament is strongly connected iff it has a Hamiltonian cycle
 *       (Moon 1968).</li>
 *   <li>Every tournament has at least one king vertex (Maurer 1980).</li>
 *   <li>The Slater ranking problem is NP-hard in general, but feasible for
 *       small tournaments.</li>
 * </ul>
 *
 * @author zalenix
 */
public class TournamentAnalyzer {

    private final Graph<String, edge> graph;
    private final List<String> vertices;
    private final Map<String, Set<String>> beats;  // u → set of v where u beats v

    /**
     * Constructs a TournamentAnalyzer for the given directed graph.
     *
     * @param graph the JUNG graph to analyze (must not be null)
     * @throws IllegalArgumentException if graph is null
     */
    public TournamentAnalyzer(Graph<String, edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.vertices = new ArrayList<String>(graph.getVertices());
        Collections.sort(this.vertices);
        this.beats = buildAdjacency();
    }

    /**
     * Builds the adjacency map from edge directions.
     */
    private Map<String, Set<String>> buildAdjacency() {
        Map<String, Set<String>> adj = new HashMap<String, Set<String>>();
        for (String v : vertices) {
            adj.put(v, new HashSet<String>());
        }
        for (edge e : graph.getEdges()) {
            String from = e.getVertex1();
            String to = e.getVertex2();
            if (from != null && to != null && adj.containsKey(from)) {
                adj.get(from).add(to);
            }
        }
        return adj;
    }

    // ── Validation ──────────────────────────────────────────────

    /**
     * Checks whether the graph is a valid tournament.
     * A tournament requires: for every pair (u,v), exactly one of
     * u→v or v→u exists.
     *
     * @return true if graph is a valid tournament
     */
    public boolean isTournament() {
        int n = vertices.size();
        if (n <= 1) return true;

        // A tournament on n vertices has exactly n*(n-1)/2 edges
        if (graph.getEdgeCount() != (long) n * (n - 1) / 2) return false;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                String u = vertices.get(i);
                String v = vertices.get(j);
                boolean uBeatsV = beats.get(u).contains(v);
                boolean vBeatsU = beats.get(v).contains(u);
                if (uBeatsV == vBeatsU) return false;  // both or neither
            }
        }
        return true;
    }

    /**
     * Returns validation errors, or empty list if valid tournament.
     *
     * @return list of validation error messages
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<String>();
        int n = vertices.size();
        if (n <= 1) return errors;

        long expected = (long) n * (n - 1) / 2;
        if (graph.getEdgeCount() != expected) {
            errors.add("Expected " + expected + " edges for " + n +
                       " vertices, found " + graph.getEdgeCount());
        }

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                String u = vertices.get(i);
                String v = vertices.get(j);
                boolean uv = beats.get(u).contains(v);
                boolean vu = beats.get(v).contains(u);
                if (!uv && !vu) {
                    errors.add("Missing edge between " + u + " and " + v);
                } else if (uv && vu) {
                    errors.add("Duplicate edge between " + u + " and " + v);
                }
            }
        }
        return errors;
    }

    // ── Score Sequence ──────────────────────────────────────────

    /**
     * Result of score sequence analysis.
     */
    public static class ScoreSequence {
        private final List<Integer> scores;
        private final Map<String, Integer> vertexScores;
        private final boolean isValid;

        public ScoreSequence(List<Integer> scores, Map<String, Integer> vertexScores,
                             boolean isValid) {
            this.scores = Collections.unmodifiableList(scores);
            this.vertexScores = Collections.unmodifiableMap(vertexScores);
            this.isValid = isValid;
        }

        /** Sorted (non-decreasing) out-degree sequence. */
        public List<Integer> getScores() { return scores; }
        /** Map from vertex to its score (out-degree / wins). */
        public Map<String, Integer> getVertexScores() { return vertexScores; }
        /** Whether this is a valid tournament score sequence (Landau's criterion). */
        public boolean isValid() { return isValid; }
    }

    /**
     * Computes the score sequence (sorted out-degrees).
     * Checks validity via Landau's criterion: a non-decreasing sequence
     * s_0 ≤ s_1 ≤ ... ≤ s_{n-1} is a tournament score sequence iff
     * for each k = 1..n: sum(s_0..s_{k-1}) ≥ C(k,2) = k(k-1)/2,
     * with equality when k = n.
     *
     * @return the score sequence analysis
     */
    public ScoreSequence computeScoreSequence() {
        Map<String, Integer> vertexScores = new HashMap<String, Integer>();
        for (String v : vertices) {
            vertexScores.put(v, beats.get(v).size());
        }

        List<Integer> sorted = new ArrayList<Integer>(vertexScores.values());
        Collections.sort(sorted);

        boolean valid = checkLandauCriterion(sorted);
        return new ScoreSequence(sorted, vertexScores, valid);
    }

    /**
     * Checks Landau's criterion for a sorted score sequence.
     */
    private boolean checkLandauCriterion(List<Integer> sorted) {
        int n = sorted.size();
        if (n == 0) return true;

        long cumSum = 0;
        for (int k = 1; k <= n; k++) {
            cumSum += sorted.get(k - 1);
            long required = (long) k * (k - 1) / 2;
            if (cumSum < required) return false;
        }
        // Total must equal n*(n-1)/2
        long total = (long) n * (n - 1) / 2;
        return cumSum == total;
    }

    // ── Copeland Ranking ────────────────────────────────────────

    /**
     * Entry in the Copeland ranking.
     */
    public static class RankEntry implements Comparable<RankEntry> {
        private final String vertex;
        private final int wins;
        private final int losses;
        private final int rank;

        public RankEntry(String vertex, int wins, int losses, int rank) {
            this.vertex = vertex;
            this.wins = wins;
            this.losses = losses;
            this.rank = rank;
        }

        public String getVertex() { return vertex; }
        public int getWins() { return wins; }
        public int getLosses() { return losses; }
        public int getRank() { return rank; }

        @Override
        public int compareTo(RankEntry other) {
            if (this.wins != other.wins) return Integer.compare(other.wins, this.wins);
            return this.vertex.compareTo(other.vertex);
        }
    }

    /**
     * Computes the Copeland ranking: vertices sorted by number of wins
     * (out-degree). Higher wins = higher rank.
     *
     * @return list of rank entries sorted by rank (1 = best)
     */
    public List<RankEntry> computeCopelandRanking() {
        int n = vertices.size();
        List<RankEntry> entries = new ArrayList<RankEntry>();
        for (String v : vertices) {
            int wins = beats.get(v).size();
            int losses = n - 1 - wins;
            entries.add(new RankEntry(v, wins, losses, 0));
        }
        Collections.sort(entries);

        // Assign ranks (tied wins get same rank)
        List<RankEntry> ranked = new ArrayList<RankEntry>();
        int rank = 1;
        for (int i = 0; i < entries.size(); i++) {
            RankEntry e = entries.get(i);
            if (i > 0 && e.wins < entries.get(i - 1).wins) {
                rank = i + 1;
            }
            ranked.add(new RankEntry(e.vertex, e.wins, e.losses, rank));
        }
        return ranked;
    }

    // ── Condorcet Winner / Loser ────────────────────────────────

    /**
     * Finds the Condorcet winner: a vertex that beats every other vertex.
     * May not exist (returns null).
     *
     * @return the Condorcet winner vertex, or null if none exists
     */
    public String findCondorcetWinner() {
        int n = vertices.size();
        if (n == 0) return null;

        for (String v : vertices) {
            if (beats.get(v).size() == n - 1) {
                return v;
            }
        }
        return null;
    }

    /**
     * Finds the Condorcet loser: a vertex that loses to every other vertex.
     * May not exist (returns null).
     *
     * @return the Condorcet loser vertex, or null if none exists
     */
    public String findCondorcetLoser() {
        for (String v : vertices) {
            if (beats.get(v).isEmpty()) {
                return v;
            }
        }
        return null;
    }

    // ── King Vertices ───────────────────────────────────────────

    /**
     * Finds all king vertices. A vertex v is a king if every other vertex
     * is reachable from v in at most 2 steps (v beats them directly or
     * v beats someone who beats them).
     *
     * <p>Every tournament has at least one king (Maurer 1980). Any vertex
     * with maximum out-degree is always a king.</p>
     *
     * @return set of king vertices
     */
    public Set<String> findKingVertices() {
        Set<String> kings = new LinkedHashSet<String>();
        int n = vertices.size();

        for (String v : vertices) {
            Set<String> reach = new HashSet<String>();
            reach.add(v);
            // Direct wins
            Set<String> directWins = beats.get(v);
            reach.addAll(directWins);
            // 2-step: through direct wins
            for (String w : directWins) {
                reach.addAll(beats.get(w));
            }
            if (reach.size() >= n) {
                kings.add(v);
            }
        }
        return kings;
    }

    // ── Hamiltonian Path ────────────────────────────────────────

    /**
     * Constructs a Hamiltonian path using the insertion algorithm.
     * Every tournament has a Hamiltonian path (Rédei's theorem, 1934).
     *
     * <p>Algorithm: Start with one vertex. For each new vertex, insert it
     * into the existing path by binary-search-like placement: find the
     * first position where the new vertex beats the vertex at that position.</p>
     *
     * <p>Time complexity: O(n²) worst case, O(n log n) with binary search.</p>
     *
     * @return the Hamiltonian path as an ordered list of vertices
     */
    public List<String> findHamiltonianPath() {
        if (vertices.isEmpty()) return new ArrayList<String>();

        LinkedList<String> path = new LinkedList<String>();
        path.add(vertices.get(0));

        for (int i = 1; i < vertices.size(); i++) {
            String v = vertices.get(i);
            insertIntoPath(path, v);
        }

        return new ArrayList<String>(path);
    }

    /**
     * Inserts vertex v into the sorted path. Finds the first position
     * where v beats the existing vertex at that position.
     */
    private void insertIntoPath(LinkedList<String> path, String v) {
        // If v beats the first vertex, prepend
        if (beats.get(v).contains(path.getFirst())) {
            path.addFirst(v);
            return;
        }

        // Find the first position where v beats the next vertex
        ListIterator<String> it = path.listIterator();
        while (it.hasNext()) {
            String current = it.next();
            if (!it.hasNext()) {
                // v loses to everyone, append at end
                it.add(v);
                return;
            }
            String next = path.get(it.nextIndex());
            if (beats.get(current).contains(v) && beats.get(v).contains(next)) {
                it.add(v);
                return;
            }
        }
        // Should never reach here for a valid tournament, but append as fallback
        path.addLast(v);
    }

    // ── Transitivity ────────────────────────────────────────────

    /**
     * Result of transitivity analysis.
     */
    public static class TransitivityResult {
        private final boolean isTransitive;
        private final int transitiveTriples;
        private final int intransitiveTriples;
        private final double transitivityRatio;
        private final List<String[]> intransitiveExamples;

        public TransitivityResult(boolean isTransitive, int transitiveTriples,
                                  int intransitiveTriples, double transitivityRatio,
                                  List<String[]> intransitiveExamples) {
            this.isTransitive = isTransitive;
            this.transitiveTriples = transitiveTriples;
            this.intransitiveTriples = intransitiveTriples;
            this.transitivityRatio = transitivityRatio;
            this.intransitiveExamples = intransitiveExamples;
        }

        /** True if tournament is fully transitive (isomorphic to a total order). */
        public boolean isTransitive() { return isTransitive; }
        /** Number of transitive triples (a→b, b→c, a→c). */
        public int getTransitiveTriples() { return transitiveTriples; }
        /** Number of intransitive triples (a→b, b→c, c→a — 3-cycles). */
        public int getIntransitiveTriples() { return intransitiveTriples; }
        /** Ratio of transitive triples to total triples. */
        public double getTransitivityRatio() { return transitivityRatio; }
        /** Up to 10 example intransitive triples [a, b, c]. */
        public List<String[]> getIntransitiveExamples() { return intransitiveExamples; }
    }

    /**
     * Analyzes transitivity of the tournament.
     * A transitive tournament is one where (u beats v) and (v beats w)
     * always implies (u beats w). This means there are no 3-cycles.
     *
     * @return transitivity analysis result
     */
    public TransitivityResult analyzeTransitivity() {
        int transitive = 0;
        int intransitive = 0;
        List<String[]> examples = new ArrayList<String[]>();
        int n = vertices.size();

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                String a = vertices.get(i);
                String b = vertices.get(j);
                if (!beats.get(a).contains(b)) continue;

                for (int k = 0; k < n; k++) {
                    if (k == i || k == j) continue;
                    String c = vertices.get(k);
                    if (!beats.get(b).contains(c)) continue;

                    // a beats b, b beats c
                    if (beats.get(a).contains(c)) {
                        transitive++;
                    } else {
                        intransitive++;
                        if (examples.size() < 10) {
                            examples.add(new String[]{a, b, c});
                        }
                    }
                }
            }
        }

        int total = transitive + intransitive;
        double ratio = total > 0 ? (double) transitive / total : 1.0;
        return new TransitivityResult(intransitive == 0, transitive, intransitive,
                                      ratio, examples);
    }

    // ── Dominance Matrix ────────────────────────────────────────

    /**
     * Computes the 2-step dominance matrix.
     * Entry D[u][v] = number of ways u reaches v in at most 2 steps
     * (1 if direct win, plus number of intermediaries w where u→w→v).
     *
     * @return map of vertex → (map of vertex → dominance count)
     */
    public Map<String, Map<String, Integer>> computeDominanceMatrix() {
        Map<String, Map<String, Integer>> matrix = new LinkedHashMap<String, Map<String, Integer>>();

        for (String u : vertices) {
            Map<String, Integer> row = new LinkedHashMap<String, Integer>();
            for (String v : vertices) {
                if (u.equals(v)) {
                    row.put(v, 0);
                    continue;
                }
                int count = 0;
                // Direct win
                if (beats.get(u).contains(v)) count++;
                // 2-step: u→w→v
                for (String w : beats.get(u)) {
                    if (!w.equals(v) && beats.get(w).contains(v)) {
                        count++;
                    }
                }
                row.put(v, count);
            }
            matrix.put(u, row);
        }
        return matrix;
    }

    /**
     * Computes the total dominance score for each vertex (sum of 2-step dominance).
     * This provides a more nuanced ranking than simple win count.
     *
     * @return map from vertex to total dominance score, sorted descending
     */
    public Map<String, Integer> computeDominanceScores() {
        Map<String, Map<String, Integer>> matrix = computeDominanceMatrix();
        Map<String, Integer> scores = new HashMap<String, Integer>();
        for (String u : vertices) {
            int total = 0;
            for (Map.Entry<String, Integer> entry : matrix.get(u).entrySet()) {
                total += entry.getValue();
            }
            scores.put(u, total);
        }
        // Sort by score descending
        LinkedHashMap<String, Integer> sorted = new LinkedHashMap<String, Integer>();
        scores.entrySet().stream()
            .sorted((a, b) -> {
                int cmp = Integer.compare(b.getValue(), a.getValue());
                return cmp != 0 ? cmp : a.getKey().compareTo(b.getKey());
            })
            .forEachOrdered(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    // ── Strong Connectivity ─────────────────────────────────────

    /**
     * Result of strong connectivity analysis.
     */
    public static class ConnectivityResult {
        private final boolean isStronglyConnected;
        private final int componentCount;
        private final List<Set<String>> components;
        private final List<String> condensationOrder;

        public ConnectivityResult(boolean isStronglyConnected, int componentCount,
                                  List<Set<String>> components,
                                  List<String> condensationOrder) {
            this.isStronglyConnected = isStronglyConnected;
            this.componentCount = componentCount;
            this.components = components;
            this.condensationOrder = condensationOrder;
        }

        /** True if the tournament is strongly connected. */
        public boolean isStronglyConnected() { return isStronglyConnected; }
        /** Number of strongly connected components. */
        public int getComponentCount() { return componentCount; }
        /** The SCCs as sets of vertices. */
        public List<Set<String>> getComponents() { return components; }
        /** Topological order of condensation DAG (component labels). */
        public List<String> getCondensationOrder() { return condensationOrder; }
    }

    /**
     * Analyzes strong connectivity using Kosaraju's algorithm.
     * A strongly connected tournament has a Hamiltonian cycle (Moon 1968).
     *
     * @return connectivity analysis result
     */
    public ConnectivityResult analyzeConnectivity() {
        if (vertices.isEmpty()) {
            return new ConnectivityResult(true, 0,
                new ArrayList<Set<String>>(), new ArrayList<String>());
        }

        // Step 1: get finish order via DFS
        List<String> finishOrder = new ArrayList<String>();
        Set<String> visited = new HashSet<String>();
        for (String v : vertices) {
            if (!visited.contains(v)) {
                dfsForward(v, visited, finishOrder);
            }
        }

        // Step 2: Build reverse adjacency
        Map<String, Set<String>> reverseAdj = new HashMap<String, Set<String>>();
        for (String v : vertices) {
            reverseAdj.put(v, new HashSet<String>());
        }
        for (String u : vertices) {
            for (String v : beats.get(u)) {
                reverseAdj.get(v).add(u);
            }
        }

        // Step 3: DFS on reverse in reverse finish order
        visited.clear();
        List<Set<String>> components = new ArrayList<Set<String>>();
        for (int i = finishOrder.size() - 1; i >= 0; i--) {
            String v = finishOrder.get(i);
            if (!visited.contains(v)) {
                Set<String> component = new LinkedHashSet<String>();
                dfsReverse(v, visited, component, reverseAdj);
                components.add(component);
            }
        }

        // Build condensation labels
        List<String> condensationOrder = new ArrayList<String>();
        for (int i = 0; i < components.size(); i++) {
            Set<String> comp = components.get(i);
            String label = "SCC" + (i + 1) + "(" + comp.size() + ")";
            condensationOrder.add(label);
        }

        boolean stronglyConnected = components.size() == 1;
        return new ConnectivityResult(stronglyConnected, components.size(),
                                      components, condensationOrder);
    }

    private void dfsForward(String v, Set<String> visited, List<String> finishOrder) {
        visited.add(v);
        for (String w : beats.get(v)) {
            if (!visited.contains(w)) {
                dfsForward(w, visited, finishOrder);
            }
        }
        finishOrder.add(v);
    }

    private void dfsReverse(String v, Set<String> visited, Set<String> component,
                            Map<String, Set<String>> reverseAdj) {
        visited.add(v);
        component.add(v);
        for (String w : reverseAdj.get(v)) {
            if (!visited.contains(w)) {
                dfsReverse(w, visited, component, reverseAdj);
            }
        }
    }

    // ── Upset Detection ─────────────────────────────────────────

    /**
     * Represents an upset: an edge from a lower-ranked to a higher-ranked vertex
     * (based on Copeland ranking).
     */
    public static class Upset {
        private final String winner;
        private final String loser;
        private final int winnerRank;
        private final int loserRank;

        public Upset(String winner, String loser, int winnerRank, int loserRank) {
            this.winner = winner;
            this.loser = loser;
            this.winnerRank = winnerRank;
            this.loserRank = loserRank;
        }

        /** The vertex that won (lower-seeded / worse rank). */
        public String getWinner() { return winner; }
        /** The vertex that lost (higher-seeded / better rank). */
        public String getLoser() { return loser; }
        /** Winner's Copeland rank (higher number = worse). */
        public int getWinnerRank() { return winnerRank; }
        /** Loser's Copeland rank (lower number = better). */
        public int getLoserRank() { return loserRank; }
        /** Magnitude of the upset (rank difference). */
        public int getMagnitude() { return winnerRank - loserRank; }
    }

    /**
     * Finds all upsets: matches where a lower-ranked vertex beats a
     * higher-ranked vertex (by Copeland ranking).
     *
     * @return list of upsets sorted by magnitude (biggest first)
     */
    public List<Upset> findUpsets() {
        List<RankEntry> ranking = computeCopelandRanking();
        Map<String, Integer> rankMap = new HashMap<String, Integer>();
        for (RankEntry e : ranking) {
            rankMap.put(e.getVertex(), e.getRank());
        }

        List<Upset> upsets = new ArrayList<Upset>();
        for (String u : vertices) {
            for (String v : beats.get(u)) {
                int uRank = rankMap.get(u);
                int vRank = rankMap.get(v);
                if (uRank > vRank) {
                    // u (worse rank) beat v (better rank) — upset!
                    upsets.add(new Upset(u, v, uRank, vRank));
                }
            }
        }

        // Sort by magnitude descending
        Collections.sort(upsets, (Upset a, Upset b) -> {
                int cmp = Integer.compare(b.getMagnitude(), a.getMagnitude());
                if (cmp != 0) return cmp;
                return a.getWinner().compareTo(b.getWinner());
            });
        return upsets;
    }

    // ── Slater Ranking ──────────────────────────────────────────

    /**
     * Result of Slater ranking computation.
     */
    public static class SlaterResult {
        private final List<String> ranking;
        private final int disagreements;

        public SlaterResult(List<String> ranking, int disagreements) {
            this.ranking = Collections.unmodifiableList(ranking);
            this.disagreements = disagreements;
        }

        /** The Slater ordering (best to worst). */
        public List<String> getRanking() { return ranking; }
        /** Number of disagreements (upsets) in this ordering. */
        public int getDisagreements() { return disagreements; }
    }

    /**
     * Computes the Slater ranking: the linear ordering that minimizes
     * the number of disagreements with the tournament edges.
     *
     * <p>For small tournaments (≤ 10 vertices), uses exact brute-force
     * permutation search. For larger tournaments, uses a greedy heuristic
     * starting from the Copeland ranking.</p>
     *
     * @return Slater ranking result
     */
    public SlaterResult computeSlaterRanking() {
        int n = vertices.size();
        if (n == 0) return new SlaterResult(new ArrayList<String>(), 0);
        if (n == 1) return new SlaterResult(new ArrayList<String>(vertices), 0);

        if (n <= 10) {
            return slaterExact();
        } else {
            return slaterGreedy();
        }
    }

    /**
     * Exact Slater ranking via permutation enumeration.
     */
    private SlaterResult slaterExact() {
        List<String> best = null;
        int bestDisagreements = Integer.MAX_VALUE;

        List<List<String>> perms = generatePermutations(new ArrayList<String>(vertices));
        for (List<String> perm : perms) {
            int d = countDisagreements(perm);
            if (d < bestDisagreements) {
                bestDisagreements = d;
                best = new ArrayList<String>(perm);
            }
        }
        return new SlaterResult(best, bestDisagreements);
    }

    /**
     * Greedy Slater ranking: start from Copeland order, then do local
     * swaps to reduce disagreements.
     */
    private SlaterResult slaterGreedy() {
        // Start with Copeland ranking
        List<RankEntry> copeland = computeCopelandRanking();
        List<String> order = new ArrayList<String>();
        for (RankEntry e : copeland) {
            order.add(e.getVertex());
        }

        // Local improvement: swap adjacent pairs if it reduces disagreements
        boolean improved = true;
        int maxIter = vertices.size() * vertices.size();
        int iter = 0;
        while (improved && iter < maxIter) {
            improved = false;
            iter++;
            for (int i = 0; i < order.size() - 1; i++) {
                String a = order.get(i);
                String b = order.get(i + 1);
                // If b beats a, swapping reduces disagreements by 1
                if (beats.get(b).contains(a)) {
                    order.set(i, b);
                    order.set(i + 1, a);
                    improved = true;
                }
            }
        }

        int disagreements = countDisagreements(order);
        return new SlaterResult(order, disagreements);
    }

    /**
     * Counts disagreements in a linear ordering: number of edges (u→v)
     * where u appears after v in the ordering.
     */
    private int countDisagreements(List<String> order) {
        Map<String, Integer> pos = new HashMap<String, Integer>();
        for (int i = 0; i < order.size(); i++) {
            pos.put(order.get(i), i);
        }
        int count = 0;
        for (String u : vertices) {
            for (String v : beats.get(u)) {
                if (pos.containsKey(u) && pos.containsKey(v) &&
                    pos.get(u) > pos.get(v)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Generates all permutations of a list (for small n only).
     */
    private List<List<String>> generatePermutations(List<String> items) {
        List<List<String>> result = new ArrayList<List<String>>();
        if (items.size() <= 1) {
            result.add(new ArrayList<String>(items));
            return result;
        }
        for (int i = 0; i < items.size(); i++) {
            String first = items.get(i);
            List<String> rest = new ArrayList<String>(items);
            rest.remove(i);
            for (List<String> perm : generatePermutations(rest)) {
                List<String> full = new ArrayList<String>();
                full.add(first);
                full.addAll(perm);
                result.add(full);
            }
        }
        return result;
    }

    // ── Report Generation ───────────────────────────────────────

    /**
     * Generates a comprehensive text report of the tournament analysis.
     *
     * @return formatted multi-line report string
     */
    public String generateReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══ Tournament Analysis Report ═══\n\n");

        // Basic info
        sb.append("Vertices: ").append(vertices.size()).append("\n");
        sb.append("Edges: ").append(graph.getEdgeCount()).append("\n");
        sb.append("Valid tournament: ").append(isTournament() ? "Yes" : "No").append("\n");

        List<String> errors = validate();
        if (!errors.isEmpty()) {
            sb.append("Validation errors:\n");
            for (String err : errors) {
                sb.append("  - ").append(err).append("\n");
            }
        }
        sb.append("\n");

        // Score sequence
        ScoreSequence scoreSeq = computeScoreSequence();
        sb.append("── Score Sequence ──\n");
        sb.append("Scores: ").append(scoreSeq.getScores()).append("\n");
        sb.append("Valid score sequence (Landau): ").append(scoreSeq.isValid()).append("\n\n");

        // Copeland ranking
        List<RankEntry> ranking = computeCopelandRanking();
        sb.append("── Copeland Ranking ──\n");
        for (RankEntry e : ranking) {
            sb.append(String.format("  #%d %s (W:%d L:%d)\n",
                e.getRank(), e.getVertex(), e.getWins(), e.getLosses()));
        }
        sb.append("\n");

        // Condorcet
        String winner = findCondorcetWinner();
        String loser = findCondorcetLoser();
        sb.append("── Condorcet ──\n");
        sb.append("Condorcet winner: ").append(winner != null ? winner : "None").append("\n");
        sb.append("Condorcet loser: ").append(loser != null ? loser : "None").append("\n\n");

        // Kings
        Set<String> kings = findKingVertices();
        sb.append("── King Vertices ──\n");
        sb.append("Kings: ").append(kings).append("\n");
        sb.append("Count: ").append(kings.size()).append("\n\n");

        // Transitivity
        TransitivityResult trans = analyzeTransitivity();
        sb.append("── Transitivity ──\n");
        sb.append("Transitive: ").append(trans.isTransitive()).append("\n");
        sb.append("Transitive triples: ").append(trans.getTransitiveTriples()).append("\n");
        sb.append("Intransitive triples: ").append(trans.getIntransitiveTriples()).append("\n");
        sb.append(String.format("Transitivity ratio: %.3f\n", trans.getTransitivityRatio()));
        if (!trans.getIntransitiveExamples().isEmpty()) {
            sb.append("Examples of intransitivity:\n");
            int shown = Math.min(5, trans.getIntransitiveExamples().size());
            for (int i = 0; i < shown; i++) {
                String[] t = trans.getIntransitiveExamples().get(i);
                sb.append(String.format("  %s→%s→%s but %s→%s\n", t[0], t[1], t[2], t[2], t[0]));
            }
        }
        sb.append("\n");

        // Hamiltonian path
        List<String> hamPath = findHamiltonianPath();
        sb.append("── Hamiltonian Path ──\n");
        sb.append("Path: ").append(String.join(" → ", hamPath)).append("\n\n");

        // Connectivity
        ConnectivityResult conn = analyzeConnectivity();
        sb.append("── Strong Connectivity ──\n");
        sb.append("Strongly connected: ").append(conn.isStronglyConnected()).append("\n");
        sb.append("SCC count: ").append(conn.getComponentCount()).append("\n");
        if (!conn.isStronglyConnected()) {
            sb.append("Condensation: ").append(conn.getCondensationOrder()).append("\n");
        }
        sb.append("\n");

        // Dominance scores
        Map<String, Integer> domScores = computeDominanceScores();
        sb.append("── Dominance Scores (2-step) ──\n");
        for (Map.Entry<String, Integer> e : domScores.entrySet()) {
            sb.append(String.format("  %s: %d\n", e.getKey(), e.getValue()));
        }
        sb.append("\n");

        // Upsets
        List<Upset> upsets = findUpsets();
        sb.append("── Upsets ──\n");
        sb.append("Total upsets: ").append(upsets.size()).append("\n");
        int shown = Math.min(5, upsets.size());
        for (int i = 0; i < shown; i++) {
            Upset u = upsets.get(i);
            sb.append(String.format("  %s (rank %d) beat %s (rank %d) — magnitude %d\n",
                u.getWinner(), u.getWinnerRank(), u.getLoser(), u.getLoserRank(),
                u.getMagnitude()));
        }
        sb.append("\n");

        // Slater
        SlaterResult slater = computeSlaterRanking();
        sb.append("── Slater Ranking ──\n");
        sb.append("Ranking: ").append(String.join(" > ", slater.getRanking())).append("\n");
        sb.append("Disagreements: ").append(slater.getDisagreements()).append("\n");

        return sb.toString();
    }
}
