package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Treewidth analysis and tree decomposition of graphs.
 *
 * <p>Treewidth is a fundamental graph parameter measuring how "tree-like" a graph is.
 * Trees have treewidth 1, cycles have treewidth 2, and complete graphs K_n have
 * treewidth n-1. Many NP-hard problems become polynomial on graphs of bounded treewidth.</p>
 *
 * <h3>Features</h3>
 * <ul>
 *   <li><b>Upper bounds:</b> Greedy degree, min-fill, and min-width elimination orderings</li>
 *   <li><b>Lower bounds:</b> Degeneracy, min-max degree (MMD), contraction degeneracy</li>
 *   <li><b>Exact treewidth:</b> For small graphs (&le;15 vertices) via optimal elimination search</li>
 *   <li><b>Tree decomposition:</b> Bag-based decomposition with validation</li>
 *   <li><b>Nice tree decomposition:</b> Introduce/forget/join node conversion</li>
 *   <li><b>Pathwidth:</b> Upper bound via DFS elimination ordering</li>
 *   <li><b>Classification:</b> Identifies bounded-treewidth graph families</li>
 *   <li><b>Reports:</b> Comprehensive text report generation</li>
 * </ul>
 *
 * @author zalenix
 */
public class TreewidthAnalyzer {

    private final Graph<String, edge> graph;

    /**
     * A bag in a tree decomposition.
     */
    public static class Bag {
        private final int id;
        private final Set<String> vertices;
        private final List<Integer> children;
        private int parent;
        private String type; // "leaf", "introduce", "forget", "join", "root", "normal"

        public Bag(int id, Set<String> vertices) {
            this.id = id;
            this.vertices = new LinkedHashSet<>(vertices);
            this.children = new ArrayList<>();
            this.parent = -1;
            this.type = "normal";
        }

        public int getId() { return id; }
        public Set<String> getVertices() { return Collections.unmodifiableSet(vertices); }
        public List<Integer> getChildren() { return Collections.unmodifiableList(children); }
        public int getParent() { return parent; }
        public String getType() { return type; }
        public int size() { return vertices.size(); }

        public void setParent(int parent) { this.parent = parent; }
        public void setType(String type) { this.type = type; }
        public void addChild(int child) { this.children.add(child); }

        @Override
        public String toString() {
            return "Bag" + id + vertices;
        }
    }

    /**
     * Result of a tree decomposition.
     */
    public static class TreeDecomposition {
        private final List<Bag> bags;
        private final int width;
        private final String method;

        public TreeDecomposition(List<Bag> bags, int width, String method) {
            this.bags = bags;
            this.width = width;
            this.method = method;
        }

        public List<Bag> getBags() { return Collections.unmodifiableList(bags); }
        public int getWidth() { return width; }
        public String getMethod() { return method; }
        public int getNumBags() { return bags.size(); }

        public int getMaxBagSize() {
            return bags.stream().mapToInt(Bag::size).max().orElse(0);
        }

        public double getAvgBagSize() {
            return bags.stream().mapToInt(Bag::size).average().orElse(0.0);
        }
    }

    /**
     * Comprehensive treewidth analysis result.
     */
    public static class TreewidthReport {
        public int lowerBound;
        public int upperBound;
        public int exactTreewidth; // -1 if not computed
        public int degeneracy;
        public int mmdLowerBound;
        public int pathwidthUpperBound;
        public TreeDecomposition bestDecomposition;
        public TreeDecomposition niceDecomposition;
        public String classification;
        public Map<String, Integer> heuristicResults;

        public TreewidthReport() {
            this.exactTreewidth = -1;
            this.heuristicResults = new LinkedHashMap<>();
        }
    }

    public TreewidthAnalyzer(Graph<String, edge> graph) {
        if (graph == null) throw new IllegalArgumentException("Graph cannot be null");
        this.graph = graph;
    }

    // ---- Upper bounds via elimination orderings ----

    /**
     * Computes upper bound using greedy degree ordering (eliminate min-degree vertex first).
     */
    public TreeDecomposition greedyDegreeDecomposition() {
        return eliminationDecomposition("greedy-degree", this::pickMinDegree);
    }

    /**
     * Computes upper bound using min-fill ordering (eliminate vertex causing fewest fill edges).
     */
    public TreeDecomposition minFillDecomposition() {
        return eliminationDecomposition("min-fill", this::pickMinFill);
    }

    /**
     * Computes upper bound using min-width ordering (eliminate vertex with minimum degree in remaining graph).
     */
    public TreeDecomposition minWidthDecomposition() {
        return eliminationDecomposition("min-width", this::pickMinDegree); // same as greedy degree
    }

    private TreeDecomposition eliminationDecomposition(String method, VertexPicker picker) {
        if (graph.getVertexCount() == 0) {
            return new TreeDecomposition(new ArrayList<>(), 0, method);
        }

        // Build adjacency
        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(graph);
        // Use LinkedHashSet for O(1) contains/remove instead of ArrayList O(V).
        // ArrayList.remove() and .contains() are O(V) per call, making the
        // elimination loop O(V²) for removes alone.  LinkedHashSet preserves
        // insertion order while providing O(1) set operations.
        Set<String> remaining = new LinkedHashSet<>(adj.keySet());
        List<Bag> bags = new ArrayList<>();
        int width = 0;
        int bagId = 0;

        List<String> order = new ArrayList<>();

        while (!remaining.isEmpty()) {
            String v = picker.pick(remaining, adj);
            remaining.remove(v);
            order.add(v);

            Set<String> neighbors = new LinkedHashSet<>(adj.getOrDefault(v, Collections.emptySet()));
            neighbors.retainAll(remaining);

            // Bag = {v} ∪ neighbors in remaining graph
            Set<String> bagVertices = new LinkedHashSet<>();
            bagVertices.add(v);
            bagVertices.addAll(neighbors);

            Bag bag = new Bag(bagId++, bagVertices);
            bags.add(bag);
            width = Math.max(width, bagVertices.size() - 1);

            // Add fill edges (make neighbors a clique)
            List<String> nList = new ArrayList<>(neighbors);
            for (int i = 0; i < nList.size(); i++) {
                for (int j = i + 1; j < nList.size(); j++) {
                    adj.computeIfAbsent(nList.get(i), k -> new LinkedHashSet<>()).add(nList.get(j));
                    adj.computeIfAbsent(nList.get(j), k -> new LinkedHashSet<>()).add(nList.get(i));
                }
            }

            // Remove v from adjacency
            adj.remove(v);
            for (Set<String> s : adj.values()) {
                s.remove(v);
            }
        }

        // Build tree structure: connect bags
        connectBags(bags);

        return new TreeDecomposition(bags, width, method);
    }

    private void connectBags(List<Bag> bags) {
        if (bags.size() <= 1) return;
        // Connect each bag to the next bag that shares vertices
        for (int i = 0; i < bags.size() - 1; i++) {
            // Find closest later bag sharing a vertex
            Bag current = bags.get(i);
            for (int j = i + 1; j < bags.size(); j++) {
                Bag candidate = bags.get(j);
                Set<String> intersection = new HashSet<>(current.getVertices());
                intersection.retainAll(candidate.getVertices());
                if (!intersection.isEmpty()) {
                    current.addChild(j);
                    candidate.setParent(i);
                    break;
                }
            }
        }
    }

    @FunctionalInterface
    private interface VertexPicker {
        String pick(Set<String> remaining, Map<String, Set<String>> adj);
    }

    private String pickMinDegree(Set<String> remaining, Map<String, Set<String>> adj) {
        String best = remaining.iterator().next();
        int bestDeg = Integer.MAX_VALUE;
        // remaining is already a Set — no need to create a HashSet copy.
        // Previously created new HashSet<>(remaining) on every call, which
        // was O(V) per call × V calls = O(V²) allocation overhead.
        for (String v : remaining) {
            int deg = 0;
            Set<String> neighbors = adj.getOrDefault(v, Collections.emptySet());
            for (String n : neighbors) {
                if (remaining.contains(n)) deg++;
            }
            if (deg < bestDeg) {
                bestDeg = deg;
                best = v;
            }
        }
        return best;
    }

    private String pickMinFill(Set<String> remaining, Map<String, Set<String>> adj) {
        String best = remaining.iterator().next();
        int bestFill = Integer.MAX_VALUE;
        // remaining is already a Set — O(1) contains, no copy needed.
        for (String v : remaining) {
            Set<String> neighbors = adj.getOrDefault(v, Collections.emptySet());
            List<String> nList = new ArrayList<>();
            for (String n : neighbors) {
                if (remaining.contains(n)) nList.add(n);
            }
            int fill = 0;
            for (int i = 0; i < nList.size(); i++) {
                for (int j = i + 1; j < nList.size(); j++) {
                    Set<String> ni = adj.getOrDefault(nList.get(i), Collections.emptySet());
                    if (!ni.contains(nList.get(j))) fill++;
                }
            }
            if (fill < bestFill) {
                bestFill = fill;
                best = v;
            }
        }
        return best;
    }

    // ---- Lower bounds ----

    /**
     * Computes the degeneracy (k-core number) as a lower bound on treewidth.
     */
    public int computeDegeneracy() {
        if (graph.getVertexCount() == 0) return 0;
        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(graph);
        int degeneracy = 0;
        Set<String> remaining = new LinkedHashSet<>(adj.keySet());

        while (!remaining.isEmpty()) {
            // Find min degree vertex
            String minV = null;
            int minDeg = Integer.MAX_VALUE;
            for (String v : remaining) {
                int deg = 0;
                for (String n : adj.getOrDefault(v, Collections.emptySet())) {
                    if (remaining.contains(n)) deg++;
                }
                if (deg < minDeg) {
                    minDeg = deg;
                    minV = v;
                }
            }
            degeneracy = Math.max(degeneracy, minDeg);
            remaining.remove(minV);
        }
        return degeneracy;
    }

    /**
     * Computes MMD (minimum maximum degree) lower bound.
     * Iteratively contracts the edge with minimum degree endpoint.
     */
    public int computeMMDLowerBound() {
        if (graph.getVertexCount() == 0) return 0;
        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(graph);
        int mmd = 0;

        while (adj.size() > 1) {
            // Find min degree
            int minDeg = Integer.MAX_VALUE;
            for (Map.Entry<String, Set<String>> e : adj.entrySet()) {
                int deg = e.getValue().size();
                if (deg < minDeg) minDeg = deg;
            }
            mmd = Math.max(mmd, minDeg);

            // Find min degree vertex and contract with a neighbor
            String minV = null;
            for (Map.Entry<String, Set<String>> e : adj.entrySet()) {
                if (e.getValue().size() == minDeg) {
                    minV = e.getKey();
                    break;
                }
            }

            Set<String> neighbors = adj.get(minV);
            if (neighbors == null || neighbors.isEmpty()) {
                adj.remove(minV);
                continue;
            }

            // Contract minV into first neighbor
            String target = neighbors.iterator().next();
            Set<String> minNeighbors = new LinkedHashSet<>(neighbors);
            minNeighbors.remove(target);

            // Merge minV's neighbors into target
            Set<String> targetNeighbors = adj.get(target);
            targetNeighbors.remove(minV);
            targetNeighbors.addAll(minNeighbors);

            // Update other vertices
            for (String n : minNeighbors) {
                Set<String> nNeighbors = adj.get(n);
                if (nNeighbors != null) {
                    nNeighbors.remove(minV);
                    nNeighbors.add(target);
                }
            }

            adj.remove(minV);
        }

        return mmd;
    }

    // ---- Exact treewidth ----

    /**
     * Computes exact treewidth for small graphs (&le;15 vertices).
     * Returns -1 if graph is too large.
     */
    public int computeExactTreewidth() {
        int n = graph.getVertexCount();
        if (n == 0) return 0;
        if (n == 1) return 0;
        if (n > 15) return -1;

        // Try elimination orderings with binary search on width
        int lower = computeDegeneracy();
        int upper = getBestUpperBound();

        if (lower == upper) return lower;

        // Binary search: can we achieve width k?
        int lo = lower, hi = upper;
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (canAchieveWidth(mid)) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return lo;
    }

    private boolean canAchieveWidth(int targetWidth) {
        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(graph);
        return canAchieveWidthDFS(adj, new HashSet<>(adj.keySet()), targetWidth);
    }

    private boolean canAchieveWidthDFS(Map<String, Set<String>> adj, Set<String> remaining, int targetWidth) {
        if (remaining.size() <= 1) return true;

        for (String v : new ArrayList<>(remaining)) {
            Set<String> neighbors = new LinkedHashSet<>();
            for (String n : adj.getOrDefault(v, Collections.emptySet())) {
                if (remaining.contains(n)) neighbors.add(n);
            }

            if (neighbors.size() > targetWidth) continue;

            // Check fill edges needed
            List<String> nList = new ArrayList<>(neighbors);
            int bagSize = 1 + nList.size();
            if (bagSize - 1 > targetWidth) continue;

            // Eliminate v: add fill edges
            Map<String, Set<String>> newAdj = deepCopyAdj(adj);
            for (int i = 0; i < nList.size(); i++) {
                for (int j = i + 1; j < nList.size(); j++) {
                    newAdj.computeIfAbsent(nList.get(i), k -> new LinkedHashSet<>()).add(nList.get(j));
                    newAdj.computeIfAbsent(nList.get(j), k -> new LinkedHashSet<>()).add(nList.get(i));
                }
            }
            newAdj.remove(v);
            for (Set<String> s : newAdj.values()) s.remove(v);

            Set<String> newRemaining = new LinkedHashSet<>(remaining);
            newRemaining.remove(v);

            if (canAchieveWidthDFS(newAdj, newRemaining, targetWidth)) return true;
        }
        return false;
    }

    private Map<String, Set<String>> deepCopyAdj(Map<String, Set<String>> adj) {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> e : adj.entrySet()) {
            copy.put(e.getKey(), new LinkedHashSet<>(e.getValue()));
        }
        return copy;
    }

    // ---- Pathwidth ----

    /**
     * Computes an upper bound on pathwidth using DFS ordering.
     */
    public int computePathwidthUpperBound() {
        if (graph.getVertexCount() == 0) return 0;
        if (graph.getVertexCount() == 1) return 0;

        // Use DFS ordering as elimination for pathwidth
        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(graph);
        Set<String> visited = new LinkedHashSet<>();
        List<String> order = new ArrayList<>();

        // Start from min-degree vertex
        String start = null;
        int minDeg = Integer.MAX_VALUE;
        for (Map.Entry<String, Set<String>> e : adj.entrySet()) {
            if (e.getValue().size() < minDeg) {
                minDeg = e.getValue().size();
                start = e.getKey();
            }
        }

        dfsOrder(start, adj, visited, order);
        // Handle disconnected components
        for (String v : adj.keySet()) {
            if (!visited.contains(v)) {
                dfsOrder(v, adj, visited, order);
            }
        }

        // Compute width from this ordering
        return computeWidthFromOrdering(order);
    }

    private void dfsOrder(String v, Map<String, Set<String>> adj, Set<String> visited, List<String> order) {
        visited.add(v);
        order.add(v);
        for (String n : adj.getOrDefault(v, Collections.emptySet())) {
            if (!visited.contains(n)) {
                dfsOrder(n, adj, visited, order);
            }
        }
    }

    private int computeWidthFromOrdering(List<String> order) {
        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(graph);
        Map<String, Set<String>> workAdj = deepCopyAdj(adj);
        int width = 0;

        for (String v : order) {
            Set<String> neighbors = workAdj.getOrDefault(v, Collections.emptySet());
            Set<String> activeNeighbors = new LinkedHashSet<>();
            Set<String> processed = new LinkedHashSet<>(order.subList(0, order.indexOf(v)));
            for (String n : neighbors) {
                if (!processed.contains(n) || neighbors.contains(n)) {
                    activeNeighbors.add(n);
                }
            }
            width = Math.max(width, activeNeighbors.size());

            // Add fill edges
            List<String> nList = new ArrayList<>(activeNeighbors);
            for (int i = 0; i < nList.size(); i++) {
                for (int j = i + 1; j < nList.size(); j++) {
                    workAdj.computeIfAbsent(nList.get(i), k -> new LinkedHashSet<>()).add(nList.get(j));
                    workAdj.computeIfAbsent(nList.get(j), k -> new LinkedHashSet<>()).add(nList.get(i));
                }
            }
            workAdj.remove(v);
            for (Set<String> s : workAdj.values()) s.remove(v);
        }
        return width;
    }

    // ---- Nice tree decomposition ----

    /**
     * Converts a tree decomposition into a nice tree decomposition.
     * A nice tree decomposition has four types of nodes:
     * - Leaf: bag with one vertex, no children
     * - Introduce: adds one vertex compared to its single child
     * - Forget: removes one vertex compared to its single child
     * - Join: two children with identical bags
     */
    public TreeDecomposition toNiceDecomposition(TreeDecomposition td) {
        if (td.getBags().isEmpty()) {
            return new TreeDecomposition(new ArrayList<>(), 0, "nice");
        }

        List<Bag> niceBags = new ArrayList<>();
        int[] nextId = {0};

        // Find root (bag with no parent)
        int rootIdx = 0;
        for (int i = 0; i < td.getBags().size(); i++) {
            if (td.getBags().get(i).getParent() == -1) {
                rootIdx = i;
                break;
            }
        }

        buildNiceTree(td, rootIdx, niceBags, nextId);

        // Add forget nodes to root down to empty
        if (!niceBags.isEmpty()) {
            Bag topBag = niceBags.get(niceBags.size() - 1);
            int currentIdx = niceBags.size() - 1;
            List<String> toForget = new ArrayList<>(topBag.getVertices());
            for (String v : toForget) {
                Set<String> newVerts = new LinkedHashSet<>(niceBags.get(currentIdx).getVertices());
                newVerts.remove(v);
                if (newVerts.isEmpty() && toForget.indexOf(v) == toForget.size() - 1) break;
                Bag forgetBag = new Bag(nextId[0]++, newVerts);
                forgetBag.setType("forget");
                forgetBag.addChild(currentIdx);
                niceBags.get(currentIdx).setParent(niceBags.size());
                niceBags.add(forgetBag);
                currentIdx = niceBags.size() - 1;
            }
            // Mark the actual root
            niceBags.get(niceBags.size() - 1).setType("root");
        }

        int width = niceBags.stream().mapToInt(b -> b.size() - 1).max().orElse(0);
        return new TreeDecomposition(niceBags, Math.max(0, width), "nice");
    }

    private int buildNiceTree(TreeDecomposition td, int bagIdx, List<Bag> niceBags, int[] nextId) {
        Bag original = td.getBags().get(bagIdx);
        List<Integer> children = original.getChildren();

        if (children.isEmpty()) {
            // Create leaf: introduce vertices one by one
            List<String> verts = new ArrayList<>(original.getVertices());
            if (verts.isEmpty()) {
                Bag leaf = new Bag(nextId[0]++, Collections.emptySet());
                leaf.setType("leaf");
                niceBags.add(leaf);
                return niceBags.size() - 1;
            }

            // Start with single vertex leaf
            Set<String> leafSet = new LinkedHashSet<>();
            leafSet.add(verts.get(0));
            Bag leaf = new Bag(nextId[0]++, leafSet);
            leaf.setType("leaf");
            niceBags.add(leaf);
            int currentIdx = niceBags.size() - 1;

            // Introduce remaining vertices
            for (int i = 1; i < verts.size(); i++) {
                Set<String> introSet = new LinkedHashSet<>(niceBags.get(currentIdx).getVertices());
                introSet.add(verts.get(i));
                Bag intro = new Bag(nextId[0]++, introSet);
                intro.setType("introduce");
                intro.addChild(currentIdx);
                niceBags.get(currentIdx).setParent(niceBags.size());
                niceBags.add(intro);
                currentIdx = niceBags.size() - 1;
            }
            return currentIdx;
        }

        // Process children
        List<Integer> childIndices = new ArrayList<>();
        for (int childIdx : children) {
            int niceChildIdx = buildNiceTree(td, childIdx, niceBags, nextId);
            childIndices.add(niceChildIdx);
        }

        // If multiple children, create join nodes pairwise
        int currentIdx = childIndices.get(0);
        // Adjust child to match current bag vertices
        currentIdx = adjustBag(niceBags, currentIdx, original.getVertices(), nextId);

        for (int i = 1; i < childIndices.size(); i++) {
            int otherIdx = childIndices.get(i);
            otherIdx = adjustBag(niceBags, otherIdx, original.getVertices(), nextId);

            // Create join node
            Bag join = new Bag(nextId[0]++, new LinkedHashSet<>(original.getVertices()));
            join.setType("join");
            join.addChild(currentIdx);
            join.addChild(otherIdx);
            niceBags.get(currentIdx).setParent(niceBags.size());
            niceBags.get(otherIdx).setParent(niceBags.size());
            niceBags.add(join);
            currentIdx = niceBags.size() - 1;
        }

        return currentIdx;
    }

    private int adjustBag(List<Bag> niceBags, int childIdx, Set<String> targetVertices, int[] nextId) {
        Set<String> childVerts = niceBags.get(childIdx).getVertices();
        int currentIdx = childIdx;

        // Forget vertices not in target
        for (String v : new ArrayList<>(childVerts)) {
            if (!targetVertices.contains(v)) {
                Set<String> newVerts = new LinkedHashSet<>(niceBags.get(currentIdx).getVertices());
                newVerts.remove(v);
                Bag forget = new Bag(nextId[0]++, newVerts);
                forget.setType("forget");
                forget.addChild(currentIdx);
                niceBags.get(currentIdx).setParent(niceBags.size());
                niceBags.add(forget);
                currentIdx = niceBags.size() - 1;
            }
        }

        // Introduce vertices in target not in child
        for (String v : targetVertices) {
            if (!niceBags.get(currentIdx).getVertices().contains(v)) {
                Set<String> newVerts = new LinkedHashSet<>(niceBags.get(currentIdx).getVertices());
                newVerts.add(v);
                Bag intro = new Bag(nextId[0]++, newVerts);
                intro.setType("introduce");
                intro.addChild(currentIdx);
                niceBags.get(currentIdx).setParent(niceBags.size());
                niceBags.add(intro);
                currentIdx = niceBags.size() - 1;
            }
        }

        return currentIdx;
    }

    // ---- Validation ----

    /**
     * Validates a tree decomposition.
     * Checks: (1) every vertex in some bag, (2) every edge covered,
     * (3) bags containing each vertex form a connected subtree.
     */
    public boolean validateDecomposition(TreeDecomposition td) {
        if (graph.getVertexCount() == 0) return td.getBags().isEmpty() || td.getWidth() == 0;

        // Check every vertex appears in some bag
        Set<String> coveredVertices = new HashSet<>();
        for (Bag bag : td.getBags()) {
            coveredVertices.addAll(bag.getVertices());
        }
        for (String v : graph.getVertices()) {
            if (!coveredVertices.contains(v)) return false;
        }

        // Check every edge is covered by some bag
        for (edge e : graph.getEdges()) {
            Collection<String> endpoints = graph.getEndpoints(e);
            List<String> epList = new ArrayList<>(endpoints);
            if (epList.size() != 2) continue;
            boolean found = false;
            for (Bag bag : td.getBags()) {
                if (bag.getVertices().contains(epList.get(0)) && bag.getVertices().contains(epList.get(1))) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }

        // Check connected subtree property
        for (String v : graph.getVertices()) {
            List<Integer> bagIndices = new ArrayList<>();
            for (int i = 0; i < td.getBags().size(); i++) {
                if (td.getBags().get(i).getVertices().contains(v)) {
                    bagIndices.add(i);
                }
            }
            if (!isConnectedSubtree(td, bagIndices)) return false;
        }

        return true;
    }

    private boolean isConnectedSubtree(TreeDecomposition td, List<Integer> indices) {
        if (indices.size() <= 1) return true;
        Set<Integer> indexSet = new HashSet<>(indices);

        // BFS/DFS on tree restricted to these bags
        Set<Integer> visited = new HashSet<>();
        Queue<Integer> queue = new LinkedList<>();
        queue.add(indices.get(0));
        visited.add(indices.get(0));

        while (!queue.isEmpty()) {
            int current = queue.poll();
            Bag bag = td.getBags().get(current);
            // Check parent
            if (bag.getParent() >= 0 && indexSet.contains(bag.getParent()) && visited.add(bag.getParent())) {
                queue.add(bag.getParent());
            }
            // Check children
            for (int child : bag.getChildren()) {
                if (indexSet.contains(child) && visited.add(child)) {
                    queue.add(child);
                }
            }
        }
        return visited.size() == indices.size();
    }

    // ---- Classification ----

    /**
     * Classifies the graph based on its treewidth.
     */
    public String classifyByTreewidth() {
        int n = graph.getVertexCount();
        if (n == 0) return "empty graph (tw=0)";
        if (n == 1) return "single vertex (tw=0)";

        int tw = getBestUpperBound();

        // Check if it's a tree/forest (tw=1)
        int edgeCount = graph.getEdgeCount();
        if (edgeCount == n - 1 && isConnected()) {
            return "tree (tw=1)";
        }
        if (edgeCount < n && tw <= 1) {
            return "forest (tw≤1)";
        }

        // Complete graph
        if (edgeCount == (long) n * (n - 1) / 2) {
            return "complete graph K" + n + " (tw=" + (n - 1) + ")";
        }

        if (tw <= 1) return "partial tree (tw≤1)";
        if (tw <= 2) return "series-parallel / outerplanar family (tw≤2)";
        if (tw <= 3) return "bounded treewidth (tw≤3)";
        if (tw <= 4) return "bounded treewidth (tw≤4)";
        if (tw == n - 1) return "dense graph (tw=" + tw + ")";
        return "general graph (tw≤" + tw + ")";
    }

    private boolean isConnected() {
        if (graph.getVertexCount() <= 1) return true;
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        String start = graph.getVertices().iterator().next();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            String v = queue.poll();
            for (String n : graph.getNeighbors(v)) {
                if (visited.add(n)) queue.add(n);
            }
        }
        return visited.size() == graph.getVertexCount();
    }

    // ---- Best bounds ----

    /**
     * Returns the best (lowest) upper bound from all heuristics.
     */
    public int getBestUpperBound() {
        if (graph.getVertexCount() <= 1) return 0;
        int gd = greedyDegreeDecomposition().getWidth();
        int mf = minFillDecomposition().getWidth();
        return Math.min(gd, mf);
    }

    /**
     * Returns the best (highest) lower bound.
     */
    public int getBestLowerBound() {
        if (graph.getVertexCount() <= 1) return 0;
        int deg = computeDegeneracy();
        int mmd = computeMMDLowerBound();
        return Math.max(deg, mmd);
    }

    // ---- Bag analysis ----

    /**
     * Computes bag intersection sizes for the decomposition.
     */
    public Map<String, Integer> analyzeBagIntersections(TreeDecomposition td) {
        Map<String, Integer> result = new LinkedHashMap<>();
        List<Bag> bags = td.getBags();
        int totalIntersection = 0;
        int edgeCount = 0;

        for (Bag bag : bags) {
            for (int childIdx : bag.getChildren()) {
                Set<String> intersection = new HashSet<>(bag.getVertices());
                intersection.retainAll(bags.get(childIdx).getVertices());
                totalIntersection += intersection.size();
                edgeCount++;
            }
        }

        result.put("numBags", bags.size());
        result.put("maxBagSize", td.getMaxBagSize());
        result.put("treeEdges", edgeCount);
        result.put("totalIntersection", totalIntersection);
        result.put("avgIntersection", edgeCount > 0 ? totalIntersection / edgeCount : 0);
        return result;
    }

    // ---- Width profile ----

    /**
     * Returns a histogram of bag sizes in the decomposition.
     */
    public Map<Integer, Integer> getWidthProfile(TreeDecomposition td) {
        Map<Integer, Integer> profile = new TreeMap<>();
        for (Bag bag : td.getBags()) {
            profile.merge(bag.size(), 1, Integer::sum);
        }
        return profile;
    }

    // ---- Full report ----

    /**
     * Generates a comprehensive treewidth analysis report.
     */
    public TreewidthReport generateReport() {
        TreewidthReport report = new TreewidthReport();

        // Compute bounds
        report.degeneracy = computeDegeneracy();
        report.mmdLowerBound = computeMMDLowerBound();
        report.lowerBound = Math.max(report.degeneracy, report.mmdLowerBound);

        // Heuristic decompositions
        TreeDecomposition gd = greedyDegreeDecomposition();
        TreeDecomposition mf = minFillDecomposition();

        report.heuristicResults.put("greedy-degree", gd.getWidth());
        report.heuristicResults.put("min-fill", mf.getWidth());

        report.upperBound = Math.min(gd.getWidth(), mf.getWidth());
        report.bestDecomposition = gd.getWidth() <= mf.getWidth() ? gd : mf;

        // Exact treewidth for small graphs
        if (graph.getVertexCount() <= 15) {
            report.exactTreewidth = computeExactTreewidth();
        }

        // Nice decomposition
        report.niceDecomposition = toNiceDecomposition(report.bestDecomposition);

        // Pathwidth
        report.pathwidthUpperBound = computePathwidthUpperBound();

        // Classification
        report.classification = classifyByTreewidth();

        return report;
    }

    /**
     * Generates a human-readable text report.
     */
    public String generateTextReport() {
        TreewidthReport report = generateReport();
        StringBuilder sb = new StringBuilder();

        sb.append("=== Treewidth Analysis Report ===\n\n");
        sb.append("Graph: ").append(graph.getVertexCount()).append(" vertices, ")
          .append(graph.getEdgeCount()).append(" edges\n");
        sb.append("Classification: ").append(report.classification).append("\n\n");

        sb.append("--- Bounds ---\n");
        sb.append("Lower bound: ").append(report.lowerBound).append("\n");
        sb.append("  Degeneracy: ").append(report.degeneracy).append("\n");
        sb.append("  MMD: ").append(report.mmdLowerBound).append("\n");
        sb.append("Upper bound: ").append(report.upperBound).append("\n");
        for (Map.Entry<String, Integer> e : report.heuristicResults.entrySet()) {
            sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
        }
        if (report.exactTreewidth >= 0) {
            sb.append("Exact treewidth: ").append(report.exactTreewidth).append("\n");
        }
        sb.append("Pathwidth upper bound: ").append(report.pathwidthUpperBound).append("\n\n");

        sb.append("--- Best Decomposition (").append(report.bestDecomposition.getMethod()).append(") ---\n");
        sb.append("Width: ").append(report.bestDecomposition.getWidth()).append("\n");
        sb.append("Bags: ").append(report.bestDecomposition.getNumBags()).append("\n");
        sb.append("Max bag size: ").append(report.bestDecomposition.getMaxBagSize()).append("\n");
        sb.append(String.format("Avg bag size: %.1f\n", report.bestDecomposition.getAvgBagSize()));

        Map<Integer, Integer> profile = getWidthProfile(report.bestDecomposition);
        sb.append("Width profile: ").append(profile).append("\n\n");

        if (report.niceDecomposition != null) {
            sb.append("--- Nice Decomposition ---\n");
            sb.append("Bags: ").append(report.niceDecomposition.getNumBags()).append("\n");
            Map<String, Long> typeCounts = report.niceDecomposition.getBags().stream()
                .collect(Collectors.groupingBy(Bag::getType, Collectors.counting()));
            sb.append("Node types: ").append(typeCounts).append("\n");
        }

        return sb.toString();
    }
}
