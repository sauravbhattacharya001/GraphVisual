package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Treewidth Analyzer — computes upper and lower bounds on treewidth and
 * produces tree decompositions for undirected graphs.
 *
 * <h3>Features</h3>
 * <ul>
 *   <li><b>Upper bound</b> — greedy min-degree and min-fill-in elimination
 *       orderings produce tree decompositions whose width upper-bounds
 *       treewidth</li>
 *   <li><b>Lower bound</b> — degeneracy (max k-core) and minor-min-width
 *       heuristics give lower bounds</li>
 *   <li><b>Tree decomposition</b> — bags and tree edges from the elimination
 *       ordering</li>
 *   <li><b>Report</b> — comprehensive summary with bounds, bag statistics,
 *       and graph classification hints</li>
 * </ul>
 *
 * <p>Treewidth is a fundamental graph parameter that measures how "tree-like"
 * a graph is. Graphs of bounded treewidth admit efficient algorithms for
 * many NP-hard problems.</p>
 *
 * @author zalenix
 */
public final class TreewidthAnalyzer {

    private TreewidthAnalyzer() { /* utility class */ }

    // ===============================================================
    //  Result classes
    // ===============================================================

    /** A single bag in a tree decomposition. */
    public static final class Bag {
        private final int id;
        private final Set<String> vertices;

        public Bag(int id, Set<String> vertices) {
            this.id = id;
            this.vertices = Collections.unmodifiableSet(vertices);
        }

        public int getId()              { return id; }
        public Set<String> getVertices() { return vertices; }
        public int width()              { return vertices.size() - 1; }

        @Override
        public String toString() {
            return "Bag " + id + ": " + vertices;
        }
    }

    /** Edge between two bags in the tree decomposition. */
    public static final class TreeEdge {
        private final int bag1;
        private final int bag2;

        public TreeEdge(int bag1, int bag2) {
            this.bag1 = bag1;
            this.bag2 = bag2;
        }

        public int getBag1() { return bag1; }
        public int getBag2() { return bag2; }

        @Override
        public String toString() {
            return bag1 + " -- " + bag2;
        }
    }

    /** Complete tree decomposition with bags and tree edges. */
    public static final class TreeDecomposition {
        private final List<Bag> bags;
        private final List<TreeEdge> treeEdges;
        private final int width;

        public TreeDecomposition(List<Bag> bags, List<TreeEdge> treeEdges,
                                 int width) {
            this.bags = Collections.unmodifiableList(bags);
            this.treeEdges = Collections.unmodifiableList(treeEdges);
            this.width = width;
        }

        public List<Bag> getBags()           { return bags; }
        public List<TreeEdge> getTreeEdges() { return treeEdges; }
        public int getWidth()                { return width; }
    }

    /** Full treewidth analysis result. */
    public static final class TreewidthResult {
        private final int lowerBound;
        private final int upperBound;
        private final String lowerMethod;
        private final String upperMethod;
        private final TreeDecomposition decomposition;
        private final int vertices;
        private final int edges;

        public TreewidthResult(int lowerBound, int upperBound,
                               String lowerMethod, String upperMethod,
                               TreeDecomposition decomposition,
                               int vertices, int edges) {
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
            this.lowerMethod = lowerMethod;
            this.upperMethod = upperMethod;
            this.decomposition = decomposition;
            this.vertices = vertices;
            this.edges = edges;
        }

        public int getLowerBound()       { return lowerBound; }
        public int getUpperBound()       { return upperBound; }
        public String getLowerMethod()   { return lowerMethod; }
        public String getUpperMethod()   { return upperMethod; }
        public TreeDecomposition getDecomposition() { return decomposition; }
        public int getVertices()         { return vertices; }
        public int getEdges()            { return edges; }

        public boolean isExact() {
            return lowerBound == upperBound;
        }
    }

    // ===============================================================
    //  Public API
    // ===============================================================

    /**
     * Analyzes treewidth of the graph, computing bounds and a tree
     * decomposition.
     *
     * @param graph an undirected JUNG graph
     * @return TreewidthResult with bounds and decomposition
     */
    public static TreewidthResult analyze(Graph<String, Edge> graph) {
        int V = graph.getVertexCount();
        int E = graph.getEdgeCount();

        if (V == 0) {
            return new TreewidthResult(0, 0, "empty", "empty",
                    new TreeDecomposition(
                            Collections.<Bag>emptyList(),
                            Collections.<TreeEdge>emptyList(), 0),
                    0, 0);
        }

        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(graph);

        // Compute upper bounds via two heuristics, take the best
        TreeDecomposition minDegreeDecomp = eliminationDecomposition(adj, "min-degree");
        TreeDecomposition minFillDecomp = eliminationDecomposition(adj, "min-fill");

        TreeDecomposition bestDecomp;
        String bestMethod;
        if (minDegreeDecomp.getWidth() <= minFillDecomp.getWidth()) {
            bestDecomp = minDegreeDecomp;
            bestMethod = "min-degree elimination";
        } else {
            bestDecomp = minFillDecomp;
            bestMethod = "min-fill elimination";
        }

        // Compute lower bounds
        int degeneracy = computeDegeneracy(adj);
        int minorMinWidth = computeMinorMinWidth(adj);
        int lowerBound;
        String lowerMethod;
        if (degeneracy >= minorMinWidth) {
            lowerBound = degeneracy;
            lowerMethod = "degeneracy";
        } else {
            lowerBound = minorMinWidth;
            lowerMethod = "minor-min-width";
        }

        // Lower bound can't exceed upper bound
        if (lowerBound > bestDecomp.getWidth()) {
            lowerBound = bestDecomp.getWidth();
        }

        return new TreewidthResult(lowerBound, bestDecomp.getWidth(),
                lowerMethod, bestMethod, bestDecomp, V, E);
    }

    /**
     * Generates a human-readable treewidth report.
     *
     * @param graph an undirected JUNG graph
     * @return formatted report string
     */
    public static String report(Graph<String, Edge> graph) {
        TreewidthResult result = analyze(graph);
        StringBuilder sb = new StringBuilder();

        sb.append("=== Treewidth Analysis Report ===\n\n");
        sb.append(String.format("Vertices: %d    Edges: %d\n\n",
                result.getVertices(), result.getEdges()));

        sb.append("--- Bounds ---\n");
        if (result.isExact()) {
            sb.append(String.format("Treewidth = %d (exact)\n",
                    result.getUpperBound()));
        } else {
            sb.append(String.format("Lower bound: %d (%s)\n",
                    result.getLowerBound(), result.getLowerMethod()));
            sb.append(String.format("Upper bound: %d (%s)\n",
                    result.getUpperBound(), result.getUpperMethod()));
        }

        sb.append("\n--- Classification ---\n");
        int tw = result.getUpperBound();
        if (tw == 0) {
            sb.append("The graph is an independent set (treewidth 0).\n");
        } else if (tw == 1) {
            sb.append("The graph is a forest (treewidth ≤ 1).\n");
        } else if (tw == 2) {
            sb.append("The graph is series-parallel (treewidth ≤ 2).\n");
        } else if (tw == 3) {
            sb.append("Treewidth ≤ 3: many NP-hard problems solvable in linear time.\n");
        } else {
            sb.append(String.format("Treewidth ≤ %d: dynamic programming on tree " +
                    "decomposition may still be practical.\n", tw));
        }

        TreeDecomposition decomp = result.getDecomposition();
        sb.append(String.format("\n--- Tree Decomposition (%d bags, %d tree edges) ---\n",
                decomp.getBags().size(), decomp.getTreeEdges().size()));

        // Bag statistics
        int minBag = Integer.MAX_VALUE, maxBag = 0;
        double sumBag = 0;
        for (Bag bag : decomp.getBags()) {
            int sz = bag.getVertices().size();
            if (sz < minBag) minBag = sz;
            if (sz > maxBag) maxBag = sz;
            sumBag += sz;
        }
        if (!decomp.getBags().isEmpty()) {
            sb.append(String.format("Bag sizes: min=%d, max=%d, avg=%.1f\n",
                    minBag, maxBag, sumBag / decomp.getBags().size()));
        }

        // Show first few bags
        int showCount = Math.min(10, decomp.getBags().size());
        sb.append(String.format("\nFirst %d bags:\n", showCount));
        for (int i = 0; i < showCount; i++) {
            sb.append("  ").append(decomp.getBags().get(i)).append('\n');
        }
        if (decomp.getBags().size() > showCount) {
            sb.append(String.format("  ... and %d more bags\n",
                    decomp.getBags().size() - showCount));
        }

        return sb.toString();
    }

    // ===============================================================
    //  Elimination-based tree decomposition
    // ===============================================================

    /**
     * Builds a tree decomposition using a greedy elimination ordering.
     *
     * @param origAdj the adjacency map
     * @param strategy "min-degree" or "min-fill"
     * @return tree decomposition
     */
    private static TreeDecomposition eliminationDecomposition(
            Map<String, Set<String>> origAdj, String strategy) {

        // Deep-copy adjacency
        Map<String, Set<String>> adj = new HashMap<String, Set<String>>();
        for (Map.Entry<String, Set<String>> entry : origAdj.entrySet()) {
            adj.put(entry.getKey(), new HashSet<String>(entry.getValue()));
        }

        List<Bag> bags = new ArrayList<Bag>();
        List<TreeEdge> treeEdges = new ArrayList<TreeEdge>();
        int width = 0;

        // Track which bag each vertex was last seen in (for tree edges)
        Map<String, Integer> lastBag = new HashMap<String, Integer>();

        int bagId = 0;
        Set<String> remaining = new HashSet<String>(adj.keySet());

        while (!remaining.isEmpty()) {
            // Pick vertex to eliminate
            String v = pickVertex(adj, remaining, strategy);
            remaining.remove(v);

            // Build bag: v + its current neighbors (that are still remaining)
            Set<String> neighbors = new HashSet<String>();
            if (adj.containsKey(v)) {
                for (String n : adj.get(v)) {
                    if (remaining.contains(n)) {
                        neighbors.add(n);
                    }
                }
            }

            Set<String> bagVertices = new HashSet<String>(neighbors);
            bagVertices.add(v);

            Bag bag = new Bag(bagId, bagVertices);
            bags.add(bag);

            if (bag.width() > width) {
                width = bag.width();
            }

            // Create tree edges to bags of neighbors
            Set<Integer> connected = new HashSet<Integer>();
            for (String n : neighbors) {
                if (lastBag.containsKey(n)) {
                    int prevBag = lastBag.get(n);
                    if (!connected.contains(prevBag) && prevBag != bagId) {
                        treeEdges.add(new TreeEdge(prevBag, bagId));
                        connected.add(prevBag);
                    }
                }
            }

            // Update lastBag for all vertices in this bag
            for (String u : bagVertices) {
                lastBag.put(u, bagId);
            }

            // Make neighbors into a clique (fill edges)
            List<String> nList = new ArrayList<String>(neighbors);
            for (int i = 0; i < nList.size(); i++) {
                for (int j = i + 1; j < nList.size(); j++) {
                    String a = nList.get(i);
                    String b = nList.get(j);
                    if (adj.containsKey(a)) adj.get(a).add(b);
                    if (adj.containsKey(b)) adj.get(b).add(a);
                }
            }

            // Remove v from adjacency
            if (adj.containsKey(v)) {
                for (String n : adj.get(v)) {
                    if (adj.containsKey(n)) {
                        adj.get(n).remove(v);
                    }
                }
                adj.remove(v);
            }

            bagId++;
        }

        return new TreeDecomposition(bags, treeEdges, width);
    }

    private static String pickVertex(Map<String, Set<String>> adj,
                                     Set<String> remaining, String strategy) {
        String best = null;
        int bestScore = Integer.MAX_VALUE;

        for (String v : remaining) {
            int score;
            Set<String> neighbors = activeNeighbors(adj, v, remaining);
            if ("min-fill".equals(strategy)) {
                score = countFillEdges(adj, neighbors, remaining);
            } else {
                score = neighbors.size();
            }
            if (score < bestScore) {
                bestScore = score;
                best = v;
            }
        }
        return best;
    }

    private static Set<String> activeNeighbors(Map<String, Set<String>> adj,
                                                String v, Set<String> remaining) {
        Set<String> result = new HashSet<String>();
        if (adj.containsKey(v)) {
            for (String n : adj.get(v)) {
                if (remaining.contains(n)) {
                    result.add(n);
                }
            }
        }
        return result;
    }

    private static int countFillEdges(Map<String, Set<String>> adj,
                                       Set<String> neighbors,
                                       Set<String> remaining) {
        int fill = 0;
        List<String> nList = new ArrayList<String>(neighbors);
        for (int i = 0; i < nList.size(); i++) {
            for (int j = i + 1; j < nList.size(); j++) {
                String a = nList.get(i);
                String b = nList.get(j);
                boolean connected = adj.containsKey(a) && adj.get(a).contains(b);
                if (!connected) fill++;
            }
        }
        return fill;
    }

    // ===============================================================
    //  Lower bound: degeneracy (k-core)
    // ===============================================================

    /**
     * Computes the degeneracy (maximum k-core number) of the graph.
     * This is a lower bound on treewidth.
     */
    private static int computeDegeneracy(Map<String, Set<String>> origAdj) {
        Map<String, Set<String>> adj = new HashMap<String, Set<String>>();
        for (Map.Entry<String, Set<String>> entry : origAdj.entrySet()) {
            adj.put(entry.getKey(), new HashSet<String>(entry.getValue()));
        }

        int degeneracy = 0;
        Set<String> remaining = new HashSet<String>(adj.keySet());

        while (!remaining.isEmpty()) {
            // Find vertex with minimum degree
            String minV = null;
            int minDeg = Integer.MAX_VALUE;
            for (String v : remaining) {
                int deg = 0;
                if (adj.containsKey(v)) {
                    for (String n : adj.get(v)) {
                        if (remaining.contains(n)) deg++;
                    }
                }
                if (deg < minDeg) {
                    minDeg = deg;
                    minV = v;
                }
            }

            if (minDeg > degeneracy) degeneracy = minDeg;

            remaining.remove(minV);
            if (adj.containsKey(minV)) {
                for (String n : adj.get(minV)) {
                    if (adj.containsKey(n)) {
                        adj.get(n).remove(minV);
                    }
                }
                adj.remove(minV);
            }
        }

        return degeneracy;
    }

    // ===============================================================
    //  Lower bound: minor-min-width
    // ===============================================================

    /**
     * Computes the minor-min-width lower bound on treewidth.
     * Repeatedly contracts the edge between the minimum-degree vertex
     * and its minimum-degree neighbor.
     */
    private static int computeMinorMinWidth(Map<String, Set<String>> origAdj) {
        Map<String, Set<String>> adj = new HashMap<String, Set<String>>();
        for (Map.Entry<String, Set<String>> entry : origAdj.entrySet()) {
            adj.put(entry.getKey(), new HashSet<String>(entry.getValue()));
        }

        int bound = 0;
        Set<String> remaining = new HashSet<String>(adj.keySet());

        while (remaining.size() > 1) {
            // Find vertex with minimum degree
            String minV = null;
            int minDeg = Integer.MAX_VALUE;
            for (String v : remaining) {
                int deg = 0;
                if (adj.containsKey(v)) {
                    for (String n : adj.get(v)) {
                        if (remaining.contains(n)) deg++;
                    }
                }
                if (deg < minDeg) {
                    minDeg = deg;
                    minV = v;
                }
            }

            if (minDeg > bound) bound = minDeg;

            if (minDeg == 0) {
                remaining.remove(minV);
                continue;
            }

            // Find minimum-degree neighbor
            String minNeighbor = null;
            int minNDeg = Integer.MAX_VALUE;
            if (adj.containsKey(minV)) {
                for (String n : adj.get(minV)) {
                    if (!remaining.contains(n)) continue;
                    int deg = 0;
                    if (adj.containsKey(n)) {
                        for (String nn : adj.get(n)) {
                            if (remaining.contains(nn)) deg++;
                        }
                    }
                    if (deg < minNDeg) {
                        minNDeg = deg;
                        minNeighbor = n;
                    }
                }
            }

            if (minNeighbor == null) {
                remaining.remove(minV);
                continue;
            }

            // Contract minV into minNeighbor
            Set<String> vNeighbors = adj.containsKey(minV) ?
                    new HashSet<String>(adj.get(minV)) : new HashSet<String>();
            vNeighbors.remove(minNeighbor);

            for (String n : vNeighbors) {
                if (!remaining.contains(n)) continue;
                // Add edge minNeighbor -- n
                if (adj.containsKey(minNeighbor)) adj.get(minNeighbor).add(n);
                if (adj.containsKey(n)) {
                    adj.get(n).remove(minV);
                    adj.get(n).add(minNeighbor);
                }
            }

            // Remove minV
            remaining.remove(minV);
            if (adj.containsKey(minNeighbor)) {
                adj.get(minNeighbor).remove(minV);
            }
            adj.remove(minV);
        }

        return bound;
    }
}
