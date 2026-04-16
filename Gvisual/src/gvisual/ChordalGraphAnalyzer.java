package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Chordal graph analyzer — determines whether a graph is chordal (triangulated),
 * computes perfect elimination orderings (PEO), optimal coloring, maximum clique,
 * minimum clique cover, fill-in edges for chordal completion, and clique tree
 * decomposition.
 *
 * <p>A graph is <em>chordal</em> if every cycle of length ≥ 4 has a chord
 * (an Edge joining two non-adjacent vertices in the cycle). Chordal graphs
 * admit a <em>perfect elimination ordering</em> (PEO) — an ordering of
 * vertices such that, for each vertex, its later neighbors form a clique.</p>
 *
 * <h3>Key results for chordal graphs:</h3>
 * <ul>
 *   <li>Optimal coloring in O(V+E) via greedy on reverse PEO</li>
 *   <li>Maximum clique in O(V+E) from PEO neighborhoods</li>
 *   <li>Minimum clique cover equals chromatic number (perfect graph)</li>
 *   <li>Maximum independent set equals clique cover number</li>
 *   <li>Clique tree (junction tree) decomposition</li>
 * </ul>
 *
 * <p>Uses Maximum Cardinality Search (MCS) for PEO computation.</p>
 *
 * @author zalenix
 */
public final class ChordalGraphAnalyzer {

    private ChordalGraphAnalyzer() { /* utility class */ }

    // ── Data classes ──────────────────────────────────────────────────────

    /** Result of chordality analysis. */
    public static class ChordalityResult {
        private final boolean chordal;
        private final List<String> peo;
        private final List<String> chordalesseCycle; // null if chordal

        public ChordalityResult(boolean chordal, List<String> peo, List<String> cycle) {
            this.chordal = chordal;
            this.peo = Collections.unmodifiableList(peo);
            this.chordalesseCycle = cycle != null ? Collections.unmodifiableList(cycle) : null;
        }

        public boolean isChordal() { return chordal; }
        public List<String> getPeo() { return peo; }
        /** Returns a chordless cycle of length ≥ 4, or null if graph is chordal. */
        public List<String> getChordlessCycle() { return chordalesseCycle; }
    }

    /** Coloring result with color assignment and chromatic number. */
    public static class ColoringResult {
        private final Map<String, Integer> colors;
        private final int chromaticNumber;

        public ColoringResult(Map<String, Integer> colors, int chromaticNumber) {
            this.colors = Collections.unmodifiableMap(colors);
            this.chromaticNumber = chromaticNumber;
        }

        public Map<String, Integer> getColors() { return colors; }
        public int getChromaticNumber() { return chromaticNumber; }
    }

    /** Clique tree node. */
    public static class CliqueTreeNode {
        private final int id;
        private final Set<String> vertices;
        private final Set<Integer> neighbors; // ids of adjacent clique nodes

        public CliqueTreeNode(int id, Set<String> vertices) {
            this.id = id;
            this.vertices = Collections.unmodifiableSet(vertices);
            this.neighbors = new LinkedHashSet<>();
        }

        public int getId() { return id; }
        public Set<String> getVertices() { return vertices; }
        public Set<Integer> getNeighbors() { return neighbors; }
    }

    /** Fill-in result for making a graph chordal. */
    public static class FillInResult {
        private final List<String[]> fillEdges;
        private final int fillCount;

        public FillInResult(List<String[]> fillEdges) {
            this.fillEdges = Collections.unmodifiableList(fillEdges);
            this.fillCount = fillEdges.size();
        }

        public List<String[]> getFillEdges() { return fillEdges; }
        public int getFillCount() { return fillCount; }
    }

    /** Comprehensive chordal analysis report. */
    public static class ChordalReport {
        private final ChordalityResult chordality;
        private final ColoringResult coloring;
        private final Set<String> maxClique;
        private final List<Set<String>> allMaximalCliques;
        private final List<CliqueTreeNode> cliqueTree;
        private final FillInResult fillIn;
        private final int vertexCount;
        private final int edgeCount;

        public ChordalReport(ChordalityResult chordality, ColoringResult coloring,
                             Set<String> maxClique, List<Set<String>> allMaximalCliques,
                             List<CliqueTreeNode> cliqueTree, FillInResult fillIn,
                             int vertexCount, int edgeCount) {
            this.chordality = chordality;
            this.coloring = coloring;
            this.maxClique = maxClique != null ? Collections.unmodifiableSet(maxClique) : null;
            this.allMaximalCliques = allMaximalCliques;
            this.cliqueTree = cliqueTree;
            this.fillIn = fillIn;
            this.vertexCount = vertexCount;
            this.edgeCount = edgeCount;
        }

        public ChordalityResult getChordality() { return chordality; }
        public ColoringResult getColoring() { return coloring; }
        public Set<String> getMaxClique() { return maxClique; }
        public List<Set<String>> getAllMaximalCliques() { return allMaximalCliques; }
        public List<CliqueTreeNode> getCliqueTree() { return cliqueTree; }
        public FillInResult getFillIn() { return fillIn; }
        public int getVertexCount() { return vertexCount; }
        public int getEdgeCount() { return edgeCount; }

        public String toTextReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Chordal Graph Analysis Report ===\n\n");
            sb.append("Vertices: ").append(vertexCount).append("\n");
            sb.append("Edges: ").append(edgeCount).append("\n\n");

            sb.append("Chordal: ").append(chordality.isChordal() ? "YES" : "NO").append("\n");
            if (!chordality.isChordal() && chordality.getChordlessCycle() != null) {
                sb.append("Chordless cycle: ").append(chordality.getChordlessCycle()).append("\n");
            }
            if (chordality.isChordal()) {
                sb.append("Perfect Elimination Ordering: ").append(chordality.getPeo()).append("\n");
            }
            sb.append("\n");

            if (coloring != null) {
                sb.append("Chromatic number: ").append(coloring.getChromaticNumber()).append("\n");
                sb.append("Coloring: ").append(coloring.getColors()).append("\n\n");
            }

            if (maxClique != null) {
                sb.append("Maximum clique size: ").append(maxClique.size()).append("\n");
                sb.append("Maximum clique: ").append(maxClique).append("\n\n");
            }

            if (allMaximalCliques != null) {
                sb.append("Maximal cliques: ").append(allMaximalCliques.size()).append("\n");
                for (int i = 0; i < allMaximalCliques.size(); i++) {
                    sb.append("  Clique ").append(i).append(": ").append(allMaximalCliques.get(i)).append("\n");
                }
                sb.append("\n");
            }

            if (cliqueTree != null) {
                sb.append("Clique tree nodes: ").append(cliqueTree.size()).append("\n");
                for (CliqueTreeNode node : cliqueTree) {
                    sb.append("  Node ").append(node.getId()).append(": ").append(node.getVertices());
                    sb.append(" → neighbors: ").append(node.getNeighbors()).append("\n");
                }
                sb.append("\n");
            }

            if (fillIn != null && fillIn.getFillCount() > 0) {
                sb.append("Fill-in edges needed: ").append(fillIn.getFillCount()).append("\n");
                for (String[] fe : fillIn.getFillEdges()) {
                    sb.append("  ").append(fe[0]).append(" — ").append(fe[1]).append("\n");
                }
            }

            return sb.toString();
        }
    }

    // ── Maximum Cardinality Search ────────────────────────────────────────

    /**
     * Computes a vertex ordering via Maximum Cardinality Search (MCS).
     * If the graph is chordal, this produces a perfect elimination ordering.
     *
     * @param graph the graph
     * @return MCS ordering (last eliminated first)
     */
    public static List<String> maximumCardinalitySearch(Graph<String, Edge> graph) {
        return maximumCardinalitySearch(graph, GraphUtils.buildAdjacencyMap(graph));
    }

    /**
     * MCS using a pre-computed adjacency map, avoiding redundant graph traversals
     * when the caller already has the adjacency structure.
     *
     * <p>Uses a bucket-based priority queue for O(V+E) total time instead of
     * the naive O(V²) linear-scan approach. Each vertex is placed in a bucket
     * corresponding to its current weight (number of already-selected
     * neighbors). Selecting the max-weight vertex is O(1) amortized by
     * tracking the current maximum bucket, and weight increments are O(1)
     * bucket moves.</p>
     */
    @SuppressWarnings("unchecked")
    static List<String> maximumCardinalitySearch(Graph<String, Edge> graph,
                                                  Map<String, Set<String>> adj) {
        if (graph == null) return Collections.emptyList();
        Collection<String> vertices = graph.getVertices();
        if (vertices == null || vertices.isEmpty()) return Collections.emptyList();

        int n = vertices.size();

        // Bucket-queue: buckets[w] holds vertices with current weight w.
        // Max possible weight is n-1 (a vertex adjacent to all others).
        List<LinkedHashSet<String>> buckets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            buckets.add(new LinkedHashSet<>());
        }

        Map<String, Integer> weight = new HashMap<>(n * 2);
        Set<String> selected = new HashSet<>(n * 2);
        for (String v : vertices) {
            weight.put(v, 0);
            buckets.get(0).add(v);
        }

        int maxBucket = 0;
        List<String> ordering = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            // Find the highest non-empty bucket
            while (maxBucket > 0 && buckets.get(maxBucket).isEmpty()) {
                maxBucket--;
            }
            // Pop one vertex from that bucket
            Iterator<String> it = buckets.get(maxBucket).iterator();
            String best = it.next();
            it.remove();
            selected.add(best);
            ordering.add(best);

            // Increment weights of unselected neighbors (O(deg) bucket moves)
            Set<String> neighbors = adj.get(best);
            if (neighbors != null) {
                for (String nb : neighbors) {
                    if (!selected.contains(nb)) {
                        int oldW = weight.get(nb);
                        int newW = oldW + 1;
                        buckets.get(oldW).remove(nb);
                        buckets.get(newW).add(nb);
                        weight.put(nb, newW);
                        if (newW > maxBucket) maxBucket = newW;
                    }
                }
            }
        }

        // MCS gives ordering where last picked = best to eliminate first
        // Reverse so index 0 = first to eliminate
        Collections.reverse(ordering);
        return ordering;
    }

    // ── Chordality test ──────────────────────────────────────────────────

    /**
     * Tests if the graph is chordal by verifying the MCS ordering is a PEO.
     *
     * @param graph the graph
     * @return ChordalityResult with PEO if chordal, or a chordless cycle if not
     */
    public static ChordalityResult testChordality(Graph<String, Edge> graph) {
        if (graph == null || graph.getVertexCount() == 0) {
            return new ChordalityResult(true, Collections.<String>emptyList(), null);
        }

        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(graph);
        List<String> mcsOrder = maximumCardinalitySearch(graph, adj);
        Map<String, Integer> pos = buildPositionMap(mcsOrder);
        return verifyChordalityFromMCS(mcsOrder, adj, pos);
    }

    /**
     * Finds a chordless cycle through v, u, w where u-w is the missing Edge.
     */
    private static List<String> findChordlessCycle(Map<String, Set<String>> adj,
                                                    String v, String u, String w) {
        // Simple approach: BFS from u to w avoiding v and direct u-w Edge
        // to find shortest path, then cycle is v → u → path → w → v
        Queue<String> queue = new ArrayDeque<>();
        Map<String, String> parent = new HashMap<>();
        Set<String> visited = new HashSet<>();
        visited.add(v);
        visited.add(u);
        queue.add(u);
        parent.put(u, null);

        while (!queue.isEmpty()) {
            String curr = queue.poll();
            Set<String> nbrs = adj.get(curr);
            if (nbrs == null) continue;
            for (String nb : nbrs) {
                if (visited.contains(nb)) continue;
                if (nb.equals(w)) {
                    // Found path from u to w
                    List<String> cycle = new ArrayList<>();
                    cycle.add(v);
                    // Reconstruct u → ... → curr
                    List<String> path = new ArrayList<>();
                    String p = curr;
                    while (p != null) {
                        path.add(p);
                        p = parent.get(p);
                    }
                    Collections.reverse(path);
                    cycle.addAll(path);
                    cycle.add(w);
                    return cycle;
                }
                visited.add(nb);
                parent.put(nb, curr);
                queue.add(nb);
            }
        }

        // Fallback: return simple cycle v-u-w (shouldn't reach here for connected)
        return Arrays.asList(v, u, w);
    }

    // ── Optimal coloring (chordal) ───────────────────────────────────────

    /**
     * Computes optimal (minimum) graph coloring for a chordal graph
     * by greedy coloring on the reverse PEO.
     *
     * @param graph the graph
     * @return coloring result
     */
    public static ColoringResult optimalColoring(Graph<String, Edge> graph) {
        if (graph == null || graph.getVertexCount() == 0) {
            return new ColoringResult(Collections.<String, Integer>emptyMap(), 0);
        }

        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(graph);
        List<String> peo = maximumCardinalitySearch(graph, adj);
        return colorFromPEO(peo, adj);
    }

    // ── Maximum clique (chordal) ─────────────────────────────────────────

    /**
     * Finds a maximum clique in O(V+E) using the PEO.
     * For each vertex in the PEO, the vertex plus its later neighbors
     * that form a clique give a candidate; the largest is returned.
     *
     * @param graph the graph
     * @return vertices of a maximum clique
     */
    public static Set<String> maximumClique(Graph<String, Edge> graph) {
        if (graph == null || graph.getVertexCount() == 0) {
            return Collections.emptySet();
        }

        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(graph);
        List<String> mcsOrder = maximumCardinalitySearch(graph, adj);
        Map<String, Integer> pos = buildPositionMap(mcsOrder);
        ChordalityResult cr = verifyChordalityFromMCS(mcsOrder, adj, pos);

        if (!cr.isChordal()) {
            return findMaxCliqueGreedy(graph, adj);
        }
        return maxCliqueFromPEO(mcsOrder, pos, adj);
    }

    private static Set<String> findMaxCliqueGreedy(Graph<String, Edge> graph) {
        return findMaxCliqueGreedy(graph, GraphUtils.buildAdjacencyMap(graph));
    }

    private static Set<String> findMaxCliqueGreedy(Graph<String, Edge> graph,
                                                    Map<String, Set<String>> adj) {
        // Sort vertices by degree descending
        List<String> sorted = new ArrayList<>(graph.getVertices());
        Collections.sort(sorted, (String a, String b) -> {
                return Integer.compare(
                    adj.get(b) != null ? adj.get(b).size() : 0,
                    adj.get(a) != null ? adj.get(a).size() : 0
                );
            });

        Set<String> clique = new LinkedHashSet<>();
        for (String v : sorted) {
            boolean canAdd = true;
            Set<String> vNbrs = adj.get(v);
            for (String c : clique) {
                if (vNbrs == null || !vNbrs.contains(c)) {
                    canAdd = false;
                    break;
                }
            }
            if (canAdd) clique.add(v);
        }
        return clique;
    }

    // ── Maximal cliques enumeration (PEO-based) ─────────────────────────

    /**
     * Enumerates all maximal cliques of a chordal graph in O(V+E).
     *
     * @param graph the graph
     * @return list of maximal cliques
     */
    public static List<Set<String>> allMaximalCliques(Graph<String, Edge> graph) {
        if (graph == null || graph.getVertexCount() == 0) {
            return Collections.emptyList();
        }

        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(graph);
        List<String> peo = maximumCardinalitySearch(graph, adj);
        Map<String, Integer> pos = buildPositionMap(peo);
        return maximalCliquesFromPEO(peo, pos, adj);
    }

    // ── Clique tree (junction tree) ──────────────────────────────────────

    /**
     * Builds a clique tree (junction tree) from the maximal cliques of a chordal graph.
     * Connects cliques by maximum-weight edges where weight = intersection size.
     *
     * @param graph the graph
     * @return list of clique tree nodes with neighbor relationships
     */
    public static List<CliqueTreeNode> buildCliqueTree(Graph<String, Edge> graph) {
        List<Set<String>> cliques = allMaximalCliques(graph);
        if (cliques.isEmpty()) return Collections.emptyList();

        List<CliqueTreeNode> nodes = new ArrayList<>();
        for (int i = 0; i < cliques.size(); i++) {
            nodes.add(new CliqueTreeNode(i, cliques.get(i)));
        }

        if (cliques.size() <= 1) return nodes;

        // Build maximum spanning tree of clique intersection graph (Prim's)
        int n = cliques.size();
        boolean[] inTree = new boolean[n];
        int[] maxWeight = new int[n];
        int[] parent = new int[n];
        Arrays.fill(maxWeight, -1);
        Arrays.fill(parent, -1);
        maxWeight[0] = 0;

        for (int iter = 0; iter < n; iter++) {
            // Find vertex not in tree with max weight
            int best = -1;
            for (int i = 0; i < n; i++) {
                if (!inTree[i] && (best == -1 || maxWeight[i] > maxWeight[best])) {
                    best = i;
                }
            }
            inTree[best] = true;

            if (parent[best] >= 0) {
                nodes.get(best).neighbors.add(parent[best]);
                nodes.get(parent[best]).neighbors.add(best);
            }

            // Update weights for remaining vertices
            Set<String> bestSet = cliques.get(best);
            for (int i = 0; i < n; i++) {
                if (!inTree[i]) {
                    int intersection = intersectionSize(bestSet, cliques.get(i));
                    if (intersection > maxWeight[i]) {
                        maxWeight[i] = intersection;
                        parent[i] = best;
                    }
                }
            }
        }

        return nodes;
    }

    private static int intersectionSize(Set<String> a, Set<String> b) {
        int count = 0;
        for (String v : a) {
            if (b.contains(v)) count++;
        }
        return count;
    }

    // ── Fill-in edges ────────────────────────────────────────────────────

    /**
     * Computes fill-in edges needed to make a non-chordal graph chordal,
     * based on the MCS ordering. For each vertex, adds edges between
     * non-adjacent later neighbors.
     *
     * @param graph the graph
     * @return fill-in result with list of edges to add
     */
    public static FillInResult computeFillIn(Graph<String, Edge> graph) {
        if (graph == null || graph.getVertexCount() == 0) {
            return new FillInResult(Collections.<String[]>emptyList());
        }

        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(graph);
        List<String> mcsOrder = maximumCardinalitySearch(graph, adj);
        Map<String, Integer> pos = buildPositionMap(mcsOrder);
        return fillInFromMCS(mcsOrder, pos, adj);
    }

    // ── Minimum separators ───────────────────────────────────────────────

    /**
     * Finds all minimal vertex separators of a chordal graph.
     * These are the intersections of adjacent maximal cliques in the clique tree.
     *
     * @param graph the graph
     * @return list of minimal separators
     */
    public static List<Set<String>> minimalSeparators(Graph<String, Edge> graph) {
        List<Set<String>> cliques = allMaximalCliques(graph);
        List<CliqueTreeNode> tree = buildCliqueTreeFromCliques(cliques);
        if (tree.size() <= 1) return Collections.emptyList();

        Set<Set<String>> separators = new LinkedHashSet<>();
        for (CliqueTreeNode node : tree) {
            for (int nbId : node.getNeighbors()) {
                if (nbId > node.getId()) { // avoid duplicates
                    Set<String> sep = new TreeSet<>(cliques.get(node.getId()));
                    sep.retainAll(cliques.get(nbId));
                    if (!sep.isEmpty()) {
                        separators.add(sep);
                    }
                }
            }
        }
        return new ArrayList<>(separators);
    }

    // ── Treewidth ────────────────────────────────────────────────────────

    /**
     * Computes the treewidth of a chordal graph (= max clique size - 1).
     *
     * @param graph the graph
     * @return treewidth, or -1 for empty graph
     */
    public static int treewidth(Graph<String, Edge> graph) {
        Set<String> mc = maximumClique(graph);
        return mc.isEmpty() ? -1 : mc.size() - 1;
    }

    // ── Vertex elimination game ──────────────────────────────────────────

    /**
     * Simulates vertex elimination, returning the cliques formed at each step.
     *
     * @param graph the graph
     * @param order elimination order
     * @return list of cliques formed at each elimination step
     */
    public static List<Set<String>> eliminationCliques(Graph<String, Edge> graph,
                                                        List<String> order) {
        if (graph == null || order == null) return Collections.emptyList();

        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(graph);  // needed: mutable copy below
        // Mutable copy
        Map<String, Set<String>> remaining = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : adj.entrySet()) {
            remaining.put(e.getKey(), new HashSet<>(e.getValue()));
        }

        List<Set<String>> cliques = new ArrayList<>();

        for (String v : order) {
            if (!remaining.containsKey(v)) continue;
            Set<String> nbrs = remaining.get(v);
            Set<String> activeNbrs = new TreeSet<>();
            for (String nb : nbrs) {
                if (remaining.containsKey(nb)) {
                    activeNbrs.add(nb);
                }
            }

            Set<String> clique = new TreeSet<>();
            clique.add(v);
            clique.addAll(activeNbrs);
            cliques.add(clique);

            // Remove v from all neighbor lists
            for (String nb : activeNbrs) {
                Set<String> nbNbrs = remaining.get(nb);
                if (nbNbrs != null) nbNbrs.remove(v);
            }
            remaining.remove(v);
        }

        return cliques;
    }

    // ── Full analysis ────────────────────────────────────────────────────

    /**
     * Runs comprehensive chordal analysis on the graph.
     *
     * <p>Internally reuses the MCS ordering, adjacency map, and chordality
     * result across all sub-analyses. Before this optimization each public
     * method (optimalColoring, maximumClique, allMaximalCliques,
     * buildCliqueTree) independently re-ran MCS and rebuilt the adjacency
     * map, resulting in 5× redundant MCS traversals and ~10× redundant
     * adjacency-map constructions.</p>
     *
     * @param graph the graph
     * @return full analysis report
     */
    public static ChordalReport analyze(Graph<String, Edge> graph) {
        int vc = graph != null ? graph.getVertexCount() : 0;
        int ec = graph != null ? graph.getEdgeCount() : 0;

        if (graph == null || vc == 0) {
            ChordalityResult cr = new ChordalityResult(true, Collections.<String>emptyList(), null);
            return new ChordalReport(cr,
                    new ColoringResult(Collections.<String, Integer>emptyMap(), 0),
                    Collections.emptySet(), null, null, null, 0, 0);
        }

        // Compute expensive structures once
        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(graph);
        List<String> mcsOrder = maximumCardinalitySearch(graph, adj);
        Map<String, Integer> pos = buildPositionMap(mcsOrder);

        // Test chordality using pre-computed data
        ChordalityResult chordality = verifyChordalityFromMCS(mcsOrder, adj, pos);

        ColoringResult coloring = null;
        Set<String> maxClique = null;
        List<Set<String>> maximalCliques = null;
        List<CliqueTreeNode> cliqueTree = null;
        FillInResult fillIn = null;

        if (chordality.isChordal()) {
            coloring = colorFromPEO(mcsOrder, adj);
            maxClique = maxCliqueFromPEO(mcsOrder, pos, adj);
            maximalCliques = maximalCliquesFromPEO(mcsOrder, pos, adj);
            cliqueTree = buildCliqueTreeFromCliques(maximalCliques);
        } else {
            fillIn = fillInFromMCS(mcsOrder, pos, adj);
            coloring = colorFromPEO(mcsOrder, adj);
            maxClique = findMaxCliqueGreedy(graph);
        }

        return new ChordalReport(chordality, coloring, maxClique, maximalCliques,
                                 cliqueTree, fillIn, vc, ec);
    }

    // ── Internal methods that reuse pre-computed structures ──────────────

    private static Map<String, Integer> buildPositionMap(List<String> ordering) {
        Map<String, Integer> pos = new HashMap<>(ordering.size() * 2);
        for (int i = 0; i < ordering.size(); i++) {
            pos.put(ordering.get(i), i);
        }
        return pos;
    }

    /**
     * Verifies chordality from pre-computed MCS ordering and adjacency map.
     * Equivalent to testChordality but avoids recomputing MCS and adj.
     */
    private static ChordalityResult verifyChordalityFromMCS(
            List<String> mcsOrder, Map<String, Set<String>> adj,
            Map<String, Integer> pos) {
        for (int i = 0; i < mcsOrder.size(); i++) {
            String v = mcsOrder.get(i);
            Set<String> nbrs = adj.get(v);
            if (nbrs == null) continue;

            List<String> laterNeighbors = new ArrayList<>();
            for (String nb : nbrs) {
                if (pos.get(nb) > i) {
                    laterNeighbors.add(nb);
                }
            }

            for (int a = 0; a < laterNeighbors.size(); a++) {
                for (int b = a + 1; b < laterNeighbors.size(); b++) {
                    String u = laterNeighbors.get(a);
                    String w = laterNeighbors.get(b);
                    Set<String> uNbrs = adj.get(u);
                    if (uNbrs == null || !uNbrs.contains(w)) {
                        List<String> cycle = findChordlessCycle(adj, v, u, w);
                        return new ChordalityResult(false, mcsOrder, cycle);
                    }
                }
            }
        }
        return new ChordalityResult(true, mcsOrder, null);
    }

    /**
     * Greedy coloring on reverse PEO using pre-computed adjacency map.
     */
    private static ColoringResult colorFromPEO(List<String> peo,
                                                Map<String, Set<String>> adj) {
        List<String> reversed = new ArrayList<>(peo);
        Collections.reverse(reversed);

        Map<String, Integer> colors = new LinkedHashMap<>();
        int maxColor = 0;

        for (String v : reversed) {
            Set<Integer> usedColors = new HashSet<>();
            Set<String> nbrs = adj.get(v);
            if (nbrs != null) {
                for (String nb : nbrs) {
                    Integer c = colors.get(nb);
                    if (c != null) usedColors.add(c);
                }
            }
            int c = 0;
            while (usedColors.contains(c)) c++;
            colors.put(v, c);
            if (c > maxColor) maxColor = c;
        }

        return new ColoringResult(colors, maxColor + 1);
    }

    /**
     * Maximum clique from PEO using pre-computed adjacency map and positions.
     */
    private static Set<String> maxCliqueFromPEO(List<String> peo,
                                                 Map<String, Integer> pos,
                                                 Map<String, Set<String>> adj) {
        Set<String> best = new LinkedHashSet<>();
        for (int i = 0; i < peo.size(); i++) {
            String v = peo.get(i);
            Set<String> clique = new LinkedHashSet<>();
            clique.add(v);
            Set<String> nbrs = adj.get(v);
            if (nbrs != null) {
                for (String nb : nbrs) {
                    if (pos.get(nb) > i) clique.add(nb);
                }
            }
            if (clique.size() > best.size()) best = clique;
        }
        return best;
    }

    /**
     * All maximal cliques from PEO using pre-computed adjacency map and positions.
     */
    private static List<Set<String>> maximalCliquesFromPEO(List<String> peo,
                                                            Map<String, Integer> pos,
                                                            Map<String, Set<String>> adj) {
        List<Set<String>> cliques = new ArrayList<>();
        Set<Set<String>> seen = new HashSet<>();

        for (int i = 0; i < peo.size(); i++) {
            String v = peo.get(i);
            Set<String> clique = new TreeSet<>();
            clique.add(v);
            Set<String> nbrs = adj.get(v);
            if (nbrs != null) {
                for (String nb : nbrs) {
                    if (pos.get(nb) > i) clique.add(nb);
                }
            }
            if (!seen.contains(clique)) {
                boolean maximal = true;
                for (Set<String> existing : cliques) {
                    if (existing.containsAll(clique)) {
                        maximal = false;
                        break;
                    }
                }
                if (maximal) {
                    Iterator<Set<String>> it = cliques.iterator();
                    while (it.hasNext()) {
                        if (clique.containsAll(it.next())) it.remove();
                    }
                    cliques.add(clique);
                    seen.add(clique);
                }
            }
        }
        return cliques;
    }

    /**
     * Build clique tree from pre-computed maximal cliques list.
     */
    private static List<CliqueTreeNode> buildCliqueTreeFromCliques(List<Set<String>> cliques) {
        if (cliques == null || cliques.isEmpty()) return Collections.emptyList();

        List<CliqueTreeNode> nodes = new ArrayList<>();
        for (int i = 0; i < cliques.size(); i++) {
            nodes.add(new CliqueTreeNode(i, cliques.get(i)));
        }

        if (cliques.size() <= 1) return nodes;

        int n = cliques.size();
        boolean[] inTree = new boolean[n];
        int[] maxWeight = new int[n];
        int[] parent = new int[n];
        Arrays.fill(maxWeight, -1);
        Arrays.fill(parent, -1);
        maxWeight[0] = 0;

        for (int iter = 0; iter < n; iter++) {
            int best = -1;
            for (int i = 0; i < n; i++) {
                if (!inTree[i] && (best == -1 || maxWeight[i] > maxWeight[best])) {
                    best = i;
                }
            }
            inTree[best] = true;

            if (parent[best] >= 0) {
                nodes.get(best).neighbors.add(parent[best]);
                nodes.get(parent[best]).neighbors.add(best);
            }

            Set<String> bestSet = cliques.get(best);
            for (int i = 0; i < n; i++) {
                if (!inTree[i]) {
                    int intersection = intersectionSize(bestSet, cliques.get(i));
                    if (intersection > maxWeight[i]) {
                        maxWeight[i] = intersection;
                        parent[i] = best;
                    }
                }
            }
        }

        return nodes;
    }

    /**
     * Compute fill-in edges using pre-computed MCS ordering and adjacency map.
     */
    private static FillInResult fillInFromMCS(List<String> mcsOrder,
                                               Map<String, Integer> pos,
                                               Map<String, Set<String>> adj) {
        Map<String, Set<String>> augmented = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : adj.entrySet()) {
            augmented.put(e.getKey(), new HashSet<>(e.getValue()));
        }

        List<String[]> fillEdges = new ArrayList<>();

        for (int i = 0; i < mcsOrder.size(); i++) {
            String v = mcsOrder.get(i);
            Set<String> nbrs = augmented.get(v);
            if (nbrs == null) continue;

            List<String> laterNeighbors = new ArrayList<>();
            for (String nb : nbrs) {
                if (pos.get(nb) > i) laterNeighbors.add(nb);
            }

            for (int a = 0; a < laterNeighbors.size(); a++) {
                for (int b = a + 1; b < laterNeighbors.size(); b++) {
                    String u = laterNeighbors.get(a);
                    String w = laterNeighbors.get(b);
                    if (!augmented.get(u).contains(w)) {
                        augmented.get(u).add(w);
                        augmented.get(w).add(u);
                        String first = u.compareTo(w) < 0 ? u : w;
                        String second = u.compareTo(w) < 0 ? w : u;
                        fillEdges.add(new String[]{first, second});
                    }
                }
            }
        }

        return new FillInResult(fillEdges);
    }
}
