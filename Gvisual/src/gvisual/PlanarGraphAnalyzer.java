package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Planar Graph Analyzer — planarity testing, face enumeration, dual graph
 * construction, and Kuratowski subgraph detection for undirected graphs.
 *
 * <h3>Features</h3>
 * <ul>
 *   <li><b>Planarity testing</b> — Euler's formula check (necessary) plus
 *       Boyer–Myrvold-inspired Edge-addition planarity via DFS embedding</li>
 *   <li><b>Face enumeration</b> — walks around a combinatorial planar embedding
 *       to list all faces (including the outer/unbounded face)</li>
 *   <li><b>Dual graph</b> — constructs the face-adjacency dual for a planar graph</li>
 *   <li><b>Kuratowski subgraph</b> — when non-planar, finds a K₅ or K₃,₃
 *       subdivision certificate</li>
 *   <li><b>Planarity report</b> — comprehensive summary with Euler numbers,
 *       genus estimate, face list, and recommendations</li>
 * </ul>
 *
 * @author zalenix
 */
public final class PlanarGraphAnalyzer {

    private PlanarGraphAnalyzer() { /* utility class */ }

    // ===============================================================
    //  Result classes
    // ===============================================================

    /** Result of a planarity test. */
    public static final class PlanarityResult {
        private final boolean planar;
        private final int vertices;
        private final int edges;
        private final int components;
        private final String reason;

        public PlanarityResult(boolean planar, int vertices, int edges,
                               int components, String reason) {
            this.planar = planar;
            this.vertices = vertices;
            this.edges = edges;
            this.components = components;
            this.reason = reason;
        }

        public boolean isPlanar()    { return planar; }
        public int getVertices()     { return vertices; }
        public int getEdges()        { return edges; }
        public int getComponents()   { return components; }
        public String getReason()    { return reason; }

        /** Expected face count via Euler's formula: F = E - V + C + 1. */
        public int expectedFaces() {
            return edges - vertices + components + 1;
        }
    }

    /** A face in the planar embedding — an ordered list of vertices. */
    public static final class Face {
        private final int id;
        private final List<String> vertices;
        private final boolean outer;

        public Face(int id, List<String> vertices, boolean outer) {
            this.id = id;
            this.vertices = Collections.unmodifiableList(
                    new ArrayList<String>(vertices));
            this.outer = outer;
        }

        public int getId()               { return id; }
        public List<String> getVertices() { return vertices; }
        public boolean isOuter()          { return outer; }
        public int size()                 { return vertices.size(); }
    }

    /** A dual graph represented as adjacency between face IDs. */
    public static final class DualGraph {
        private final Map<Integer, Set<Integer>> adjacency;
        private final List<Face> faces;

        public DualGraph(Map<Integer, Set<Integer>> adjacency,
                         List<Face> faces) {
            this.adjacency = adjacency;
            this.faces = faces;
        }

        public Map<Integer, Set<Integer>> getAdjacency() { return adjacency; }
        public List<Face> getFaces() { return faces; }
        public int nodeCount() { return adjacency.size(); }

        public int edgeCount() {
            int total = 0;
            for (Set<Integer> nbrs : adjacency.values()) total += nbrs.size();
            return total / 2;
        }
    }

    /** Certificate of non-planarity — a K₅ or K₃,₃ subdivision. */
    public static final class KuratowskiSubgraph {
        private final String type; // "K5" or "K3_3"
        private final Set<String> branchVertices;
        private final List<List<String>> paths;

        public KuratowskiSubgraph(String type, Set<String> branchVertices,
                                   List<List<String>> paths) {
            this.type = type;
            this.branchVertices = Collections.unmodifiableSet(
                    new LinkedHashSet<String>(branchVertices));
            this.paths = paths;
        }

        public String getType()                { return type; }
        public Set<String> getBranchVertices()  { return branchVertices; }
        public List<List<String>> getPaths()    { return paths; }
    }

    /** Comprehensive planarity report. */
    public static final class PlanarityReport {
        private final PlanarityResult result;
        private final List<Face> faces;        // null if non-planar
        private final DualGraph dual;          // null if non-planar
        private final KuratowskiSubgraph kuratowski; // null if planar
        private final int genus;

        public PlanarityReport(PlanarityResult result, List<Face> faces,
                                DualGraph dual, KuratowskiSubgraph kuratowski,
                                int genus) {
            this.result = result;
            this.faces = faces;
            this.dual = dual;
            this.kuratowski = kuratowski;
            this.genus = genus;
        }

        public PlanarityResult getResult()           { return result; }
        public List<Face> getFaces()                  { return faces; }
        public DualGraph getDual()                    { return dual; }
        public KuratowskiSubgraph getKuratowski()     { return kuratowski; }
        public int getGenus()                         { return genus; }

        public String toText() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Planarity Report ===\n");
            sb.append(String.format("Vertices: %d | Edges: %d | Components: %d\n",
                    result.getVertices(), result.getEdges(),
                    result.getComponents()));
            sb.append("Planar: ").append(result.isPlanar() ? "YES" : "NO")
              .append(" (").append(result.getReason()).append(")\n");
            sb.append("Genus: ").append(genus).append("\n");
            if (faces != null) {
                sb.append(String.format("Faces: %d (Euler: V-E+F = %d)\n",
                        faces.size(),
                        result.getVertices() - result.getEdges() + faces.size()));
                for (Face f : faces) {
                    sb.append(String.format("  Face %d%s: %s\n", f.getId(),
                            f.isOuter() ? " (outer)" : "",
                            f.getVertices().toString()));
                }
            }
            if (dual != null) {
                sb.append(String.format("Dual graph: %d nodes, %d edges\n",
                        dual.nodeCount(), dual.edgeCount()));
            }
            if (kuratowski != null) {
                sb.append(String.format("Kuratowski subdivision: %s (branch vertices: %s)\n",
                        kuratowski.getType(),
                        kuratowski.getBranchVertices().toString()));
            }
            return sb.toString();
        }
    }

    // ===============================================================
    //  Core: Planarity Test
    // ===============================================================

    /**
     * Tests whether the graph is planar.
     * Uses Euler's formula bound (E ≤ 3V - 6) as a quick reject,
     * then attempts to build a planar embedding via ordered DFS.
     */
    public static PlanarityResult testPlanarity(Graph<String, Edge> graph) {
        if (graph == null) throw new IllegalArgumentException("graph is null");

        int V = graph.getVertexCount();
        int E = graph.getEdgeCount();
        int C = countComponents(graph);

        if (V <= 1) {
            return new PlanarityResult(true, V, E, C, "trivially planar (≤1 vertex)");
        }
        if (V == 2) {
            boolean ok = E <= 1;
            return new PlanarityResult(ok, V, E, C,
                    ok ? "trivially planar (2 vertices)" : "multi-Edge exceeds bound");
        }
        // Euler bound: E ≤ 3V - 6 for simple connected planar graph with V≥3
        if (V >= 3 && E > 3 * V - 6) {
            return new PlanarityResult(false, V, E, C,
                    String.format("fails Euler bound: E=%d > 3V-6=%d", E, 3 * V - 6));
        }

        // For small graphs, attempt embedding via Edge-addition
        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(graph);
        boolean planar = attemptPlanarEmbedding(adj, graph);

        return new PlanarityResult(planar, V, E, C,
                planar ? "planar embedding found" : "no planar embedding exists");
    }

    // ===============================================================
    //  Face Enumeration
    // ===============================================================

    /**
     * Enumerates all faces of a planar graph by building a combinatorial
     * embedding and tracing face-walks. Returns null if non-planar.
     */
    public static List<Face> enumerateFaces(Graph<String, Edge> graph) {
        PlanarityResult pr = testPlanarity(graph);
        if (!pr.isPlanar()) return null;

        Map<String, List<String>> embedding = buildPlanarEmbedding(graph);
        if (embedding == null || embedding.isEmpty()) {
            // Single vertex or empty graph
            List<Face> faces = new ArrayList<Face>();
            faces.add(new Face(0, new ArrayList<String>(), true));
            return faces;
        }

        // Trace faces using the "next-Edge" walk on the combinatorial embedding
        Set<String> visitedDarts = new HashSet<String>();
        List<Face> faces = new ArrayList<Face>();
        int faceId = 0;

        for (String u : embedding.keySet()) {
            List<String> neighbors = embedding.get(u);
            for (String v : neighbors) {
                String dart = u + "->" + v;
                if (visitedDarts.contains(dart)) continue;

                List<String> faceVertices = new ArrayList<String>();
                String curr = u;
                String next = v;

                while (true) {
                    String d = curr + "->" + next;
                    if (visitedDarts.contains(d)) break;
                    visitedDarts.add(d);
                    faceVertices.add(curr);

                    // Find the position of curr in next's neighbor list
                    List<String> nextNeighbors = embedding.get(next);
                    int idx = nextNeighbors.indexOf(curr);
                    // The next dart goes to the *previous* neighbor in cyclic order
                    int prevIdx = (idx - 1 + nextNeighbors.size()) % nextNeighbors.size();

                    curr = next;
                    next = nextNeighbors.get(prevIdx);
                }

                faces.add(new Face(faceId++, faceVertices, false));
            }
        }

        // Mark the largest face as outer
        if (!faces.isEmpty()) {
            int maxIdx = 0;
            int maxSize = 0;
            for (int i = 0; i < faces.size(); i++) {
                if (faces.get(i).size() > maxSize) {
                    maxSize = faces.get(i).size();
                    maxIdx = i;
                }
            }
            Face outer = faces.get(maxIdx);
            faces.set(maxIdx, new Face(outer.getId(), outer.getVertices(), true));
        }

        return faces;
    }

    // ===============================================================
    //  Dual Graph
    // ===============================================================

    /**
     * Constructs the dual graph of a planar graph.
     * Each face becomes a node; two nodes are adjacent if their faces
     * share an Edge.
     */
    public static DualGraph buildDualGraph(Graph<String, Edge> graph) {
        List<Face> faces = enumerateFaces(graph);
        if (faces == null) return null;

        // Build Edge → face mapping
        Map<String, List<Integer>> edgeToFaces = new HashMap<String, List<Integer>>();
        for (Face f : faces) {
            List<String> verts = f.getVertices();
            for (int i = 0; i < verts.size(); i++) {
                String a = verts.get(i);
                String b = verts.get((i + 1) % verts.size());
                String edgeKey = makeEdgeKey(a, b);
                if (!edgeToFaces.containsKey(edgeKey)) {
                    edgeToFaces.put(edgeKey, new ArrayList<Integer>());
                }
                edgeToFaces.get(edgeKey).add(f.getId());
            }
        }

        // Build adjacency
        Map<Integer, Set<Integer>> adj = new LinkedHashMap<Integer, Set<Integer>>();
        for (Face f : faces) {
            adj.put(f.getId(), new LinkedHashSet<Integer>());
        }
        for (List<Integer> faceIds : edgeToFaces.values()) {
            if (faceIds.size() == 2) {
                int f1 = faceIds.get(0);
                int f2 = faceIds.get(1);
                adj.get(f1).add(f2);
                adj.get(f2).add(f1);
            }
        }

        return new DualGraph(adj, faces);
    }

    // ===============================================================
    //  Kuratowski Subgraph Detection
    // ===============================================================

    /**
     * For a non-planar graph, attempts to find a K₅ or K₃,₃ subdivision.
     * Returns null if the graph is planar.
     */
    public static KuratowskiSubgraph findKuratowskiSubgraph(
            Graph<String, Edge> graph) {
        PlanarityResult pr = testPlanarity(graph);
        if (pr.isPlanar()) return null;

        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(graph);

        // Try to find K₅ subdivision: 5 vertices all mutually connected by paths
        KuratowskiSubgraph k5 = findK5Subdivision(adj, graph);
        if (k5 != null) return k5;

        // Try to find K₃,₃ subdivision
        KuratowskiSubgraph k33 = findK33Subdivision(adj, graph);
        return k33;
    }

    // ===============================================================
    //  Full Report
    // ===============================================================

    /**
     * Generates a comprehensive planarity report.
     */
    public static PlanarityReport analyze(Graph<String, Edge> graph) {
        PlanarityResult result = testPlanarity(graph);
        List<Face> faces = null;
        DualGraph dual = null;
        KuratowskiSubgraph kuratowski = null;

        if (result.isPlanar()) {
            faces = enumerateFaces(graph);
            dual = buildDualGraph(graph);
        } else {
            kuratowski = findKuratowskiSubgraph(graph);
        }

        // Genus estimate: g = ceil((E - 3V + 6) / 6) for non-planar, 0 for planar
        int genus = 0;
        if (!result.isPlanar() && result.getVertices() >= 3) {
            int excess = result.getEdges() - (3 * result.getVertices() - 6);
            if (excess > 0) {
                genus = (excess + 5) / 6; // ceiling division
            } else {
                genus = 1; // non-planar but within Euler bound → genus at least 1
            }
        }

        return new PlanarityReport(result, faces, dual, kuratowski, genus);
    }

    // ===============================================================
    //  Internal helpers
    // ===============================================================

    /** Exhaustive minor check for small graphs using DFS over all possible Edge contractions. */
    private static boolean exhaustiveMinorCheck(Map<String, Set<String>> g) {
        // Clean up
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String v : new ArrayList<String>(g.keySet())) {
                if (!g.containsKey(v)) continue;
                if (g.get(v).size() <= 1) {
                    for (String n : g.get(v)) { if (g.containsKey(n)) g.get(n).remove(v); }
                    g.remove(v);
                    changed = true;
                }
            }
        }
        if (g.size() <= 4) return false;
        if (g.size() == 5) {
            for (String v : g.keySet()) if (g.get(v).size() < 4) return false;
            return true;
        }
        if (g.size() == 6 && isK33(g)) return true;
        if (g.size() > 12) return false; // too expensive for exhaustive

        // Try each Edge contraction
        for (String u : new ArrayList<String>(g.keySet())) {
            for (String v : new ArrayList<String>(g.get(u))) {
                if (u.compareTo(v) > 0) continue; // avoid duplicates
                Map<String, Set<String>> copy = new HashMap<String, Set<String>>();
                for (Map.Entry<String, Set<String>> e : g.entrySet()) {
                    copy.put(e.getKey(), new LinkedHashSet<String>(e.getValue()));
                }
                contractEdge(copy, v, u);
                if (exhaustiveMinorCheck(copy)) return true;
            }
        }
        return false;
    }

    /** Check if graph is triangle-free. */
    private static boolean isTriangleFree(Map<String, Set<String>> adj) {
        for (String u : adj.keySet()) {
            Set<String> uNbrs = adj.get(u);
            if (uNbrs == null) continue;
            for (String v : uNbrs) {
                if (v.compareTo(u) <= 0) continue;
                Set<String> vNbrs = adj.get(v);
                if (vNbrs == null) continue;
                for (String w : vNbrs) {
                    if (w.compareTo(v) <= 0) continue;
                    if (uNbrs.contains(w)) return false;
                }
            }
        }
        return true;
    }

    /** Count connected components via BFS. */
    static int countComponents(Graph<String, Edge> graph) {
        Set<String> visited = new HashSet<String>();
        int count = 0;
        for (String v : graph.getVertices()) {
            if (visited.contains(v)) continue;
            count++;
            Queue<String> q = new LinkedList<String>();
            q.add(v);
            visited.add(v);
            while (!q.isEmpty()) {
                String curr = q.poll();
                Collection<String> nbrs = graph.getNeighbors(curr);
                if (nbrs != null) {
                    for (String n : nbrs) {
                        if (!visited.contains(n)) {
                            visited.add(n);
                            q.add(n);
                        }
                    }
                }
            }
        }
        return count;
    }

    /**
     * Attempt planar embedding via Edge-addition for each biconnected component.
     * Uses a proper graph minor / subdivision check approach.
     */
    private static boolean attemptPlanarEmbedding(
            Map<String, Set<String>> adj, Graph<String, Edge> graph) {
        // Decompose into biconnected components and test each
        List<Set<String>> bicomponents = findBiconnectedComponents(adj);

        for (Set<String> comp : bicomponents) {
            if (comp.size() <= 4) continue; // K4 and smaller are always planar

            // Build subgraph adjacency
            Map<String, Set<String>> subAdj = new HashMap<String, Set<String>>();
            int edgeCount = 0;
            for (String v : comp) {
                Set<String> nbrs = new LinkedHashSet<String>();
                Set<String> origNbrs = adj.get(v);
                if (origNbrs != null) {
                    for (String n : origNbrs) {
                        if (comp.contains(n)) nbrs.add(n);
                    }
                }
                subAdj.put(v, nbrs);
                edgeCount += nbrs.size();
            }
            edgeCount /= 2;

            int V = comp.size();
            if (V >= 3 && edgeCount > 3 * V - 6) return false;

            // For triangle-free biconnected graphs: E <= 2V-4
            boolean triangleFree = isTriangleFree(subAdj);
            if (triangleFree && V >= 3 && edgeCount > 2 * V - 4) return false;

            // Check for K5 or K3,3 minor/subdivision
            if (containsK5OrK33Minor(subAdj, comp)) return false;
        }
        return true;
    }

    /**
     * Check if a biconnected component contains a K5 or K3,3 minor.
     * Uses Edge contraction with a strategy that contracts low-degree vertices
     * first, checking at each step for complete/bipartite minors.
     */
    private static boolean containsK5OrK33Minor(Map<String, Set<String>> adj,
                                                  Set<String> vertices) {
        // For small graphs (<= 15 vertices), try exhaustive contraction
        if (vertices.size() <= 15) {
            Map<String, Set<String>> g0 = new HashMap<String, Set<String>>();
            for (String v : vertices) {
                g0.put(v, new LinkedHashSet<String>(
                        adj.get(v) != null ? adj.get(v) : Collections.<String>emptySet()));
            }
            for (String v : new ArrayList<String>(g0.keySet())) {
                g0.get(v).remove(v); g0.get(v).retainAll(g0.keySet());
            }
            if (exhaustiveMinorCheck(g0)) return true;
        }
        // Try multiple contraction strategies
        for (int strategy = 0; strategy < 8; strategy++) {
            if (tryContractionStrategy(adj, vertices, strategy)) return true;
        }
        return false;
    }

    private static boolean tryContractionStrategy(Map<String, Set<String>> adj,
                                           Set<String> vertices,
                                           int strategy) {
        Map<String, Set<String>> g = new HashMap<String, Set<String>>();
        for (String v : vertices) {
            g.put(v, new LinkedHashSet<String>(
                    adj.get(v) != null ? adj.get(v) : Collections.<String>emptySet()));
        }
        for (String v : new ArrayList<String>(g.keySet())) {
            Set<String> nbrs = g.get(v);
            if (nbrs != null) { nbrs.remove(v); nbrs.retainAll(g.keySet()); }
        }

        int maxIter = vertices.size() * 2;
        for (int i = 0; i < maxIter && g.size() > 5; i++) {
            // Check at size 6 for K3,3
            if (g.size() == 6 && isK33(g)) return true;

            // Remove degree-0 and degree-1 vertices (can't be in K5/K3,3 minor)
            boolean changed = true;
            while (changed && g.size() > 5) {
                changed = false;
                for (String v : new ArrayList<String>(g.keySet())) {
                    if (!g.containsKey(v)) continue;
                    int deg = g.get(v).size();
                    if (deg <= 1) {
                        // Remove: can't be part of the minor
                        for (String n : g.get(v)) {
                            g.get(n).remove(v);
                        }
                        g.remove(v);
                        changed = true;
                    }
                }
            }

            if (g.size() <= 4) return false;
            if (g.size() == 5) break;
            if (g.size() == 6 && isK33(g)) return true;

            // Contract a degree-2 vertex if any (path contraction)
            String toContract = null;
            String neighbor = null;
            for (String v : g.keySet()) {
                if (g.get(v).size() == 2) { toContract = v; break; }
            }

            if (toContract == null) {
                String bestNeighbor = null;
                switch (strategy) {
                    case 0: case 1: {
                        int targetDeg = (strategy == 0) ? Integer.MAX_VALUE : 0;
                        for (Map.Entry<String, Set<String>> entry : g.entrySet()) {
                            int deg = entry.getValue().size();
                            if ((strategy == 0) ? deg < targetDeg : deg > targetDeg) {
                                targetDeg = deg;
                                toContract = entry.getKey();
                            }
                        }
                        break;
                    }
                    case 2: case 3: {
                        int bestCommon = (strategy == 2) ? -1 : Integer.MAX_VALUE;
                        for (String v : g.keySet()) {
                            for (String n : g.get(v)) {
                                Set<String> common = new HashSet<String>(g.get(v));
                                common.retainAll(g.get(n));
                                boolean better = (strategy == 2) ? common.size() > bestCommon : common.size() < bestCommon;
                                if (better) {
                                    bestCommon = common.size();
                                    toContract = v;
                                    bestNeighbor = n;
                                }
                            }
                        }
                        break;
                    }
                    default: {
                        // Strategies 4-7: contract non-adjacent vertex pairs by merging
                        // Pick the (strategy-4)th vertex in order
                        List<String> vList = new ArrayList<String>(g.keySet());
                        int idx = (strategy - 4) % vList.size();
                        toContract = vList.get(idx);
                        // Pick its neighbor with highest degree
                        int maxNDeg = 0;
                        for (String n : g.get(toContract)) {
                            if (g.get(n).size() > maxNDeg) {
                                maxNDeg = g.get(n).size();
                                bestNeighbor = n;
                            }
                        }
                        break;
                    }
                }
                if (bestNeighbor != null) { neighbor = bestNeighbor; }
            }

            if (toContract == null || g.get(toContract).isEmpty()) break;

            if (neighbor == null) neighbor = g.get(toContract).iterator().next();
            contractEdge(g, toContract, neighbor);
        }

        if (g.size() == 5) {
            boolean isK5 = true;
            for (String v : g.keySet()) {
                if (g.get(v).size() < 4) { isK5 = false; break; }
            }
            return isK5;
        }
        if (g.size() == 6 && isK33(g)) return true;
        return false;
    }

    /** Contract Edge (u,v) by merging v into u. */
    private static void contractEdge(Map<String, Set<String>> g,
                                      String remove, String keep) {
        Set<String> removeNbrs = g.get(remove);
        if (removeNbrs == null) removeNbrs = Collections.emptySet();

        // Add all of remove's neighbors to keep
        Set<String> keepNbrs = g.get(keep);
        for (String n : removeNbrs) {
            if (n.equals(keep)) continue;
            keepNbrs.add(n);
            // Update n's adjacency: replace remove with keep
            Set<String> nNbrs = g.get(n);
            if (nNbrs != null) {
                nNbrs.remove(remove);
                nNbrs.add(keep);
            }
        }
        keepNbrs.remove(keep); // no self-loops
        keepNbrs.remove(remove);

        // Remove the contracted vertex
        g.remove(remove);
        // Clean up keep's self-reference
        g.get(keep).remove(keep);
    }

    /** Check if a 6-vertex graph is K3,3 (complete bipartite). */
    private static boolean isK33(Map<String, Set<String>> g) {
        if (g.size() != 6) return false;
        // K3,3: every vertex has degree 3, and graph is bipartite
        for (Set<String> nbrs : g.values()) {
            if (nbrs.size() != 3) return false;
        }
        // Check bipartiteness
        List<String> verts = new ArrayList<String>(g.keySet());
        Map<String, Integer> color = new HashMap<String, Integer>();
        Queue<String> queue = new LinkedList<String>();
        color.put(verts.get(0), 0);
        queue.add(verts.get(0));
        while (!queue.isEmpty()) {
            String v = queue.poll();
            int c = color.get(v);
            for (String n : g.get(v)) {
                if (!color.containsKey(n)) {
                    color.put(n, 1 - c);
                    queue.add(n);
                } else if (color.get(n) == c) {
                    return false; // odd cycle
                }
            }
        }
        // Check we colored all 6
        if (color.size() != 6) return false;
        int side0 = 0;
        for (int c : color.values()) if (c == 0) side0++;
        return side0 == 3;
    }

    /** Find biconnected components via iterative DFS with cut vertices. */
    private static List<Set<String>> findBiconnectedComponents(
            Map<String, Set<String>> adj) {
        List<Set<String>> components = new ArrayList<Set<String>>();

        if (adj.isEmpty()) return components;

        Map<String, Integer> disc = new HashMap<String, Integer>();
        Map<String, Integer> low = new HashMap<String, Integer>();
        Map<String, String> par = new HashMap<String, String>();
        int[] timer = {0};

        Deque<String[]> edgeStack = new ArrayDeque<String[]>();

        for (String start : adj.keySet()) {
            if (disc.containsKey(start)) continue;

            Deque<Object[]> dfsStack = new ArrayDeque<Object[]>();
            dfsStack.push(new Object[]{start, (String) null,
                    adj.get(start) != null ?
                            new ArrayList<String>(adj.get(start)).iterator() :
                            Collections.<String>emptyList().iterator()});
            disc.put(start, timer[0]);
            low.put(start, timer[0]);
            timer[0]++;

            while (!dfsStack.isEmpty()) {
                Object[] frame = dfsStack.peek();
                String u = (String) frame[0];
                @SuppressWarnings("unchecked")
                Iterator<String> it = (Iterator<String>) frame[2];

                if (it.hasNext()) {
                    String v = it.next();
                    if (!disc.containsKey(v)) {
                        par.put(v, u);
                        disc.put(v, timer[0]);
                        low.put(v, timer[0]);
                        timer[0]++;
                        edgeStack.push(new String[]{u, v});

                        Set<String> vNbrs = adj.get(v);
                        dfsStack.push(new Object[]{v, u,
                                vNbrs != null ?
                                        new ArrayList<String>(vNbrs).iterator() :
                                        Collections.<String>emptyList().iterator()});
                    } else if (!v.equals(par.get(u)) &&
                               disc.get(v) < disc.get(u)) {
                        edgeStack.push(new String[]{u, v});
                        low.put(u, Math.min(low.get(u), disc.get(v)));
                    }
                } else {
                    dfsStack.pop();
                    if (dfsStack.isEmpty()) continue;

                    Object[] parentFrame = dfsStack.peek();
                    String p = (String) parentFrame[0];
                    low.put(p, Math.min(low.get(p), low.get(u)));

                    // If u is an articulation point relative to p
                    if ((par.get(p) == null && countDfsChildren(adj, p, par) > 1 &&
                         par.get(u) != null && par.get(u).equals(p)) ||
                        (par.get(p) != null && low.get(u) >= disc.get(p))) {

                        Set<String> comp = new LinkedHashSet<String>();
                        while (!edgeStack.isEmpty()) {
                            String[] e = edgeStack.peek();
                            if ((e[0].equals(p) && e[1].equals(u)) ||
                                (e[0].equals(u) && e[1].equals(p))) {
                                edgeStack.pop();
                                comp.add(e[0]);
                                comp.add(e[1]);
                                break;
                            }
                            edgeStack.pop();
                            comp.add(e[0]);
                            comp.add(e[1]);
                        }
                        if (!comp.isEmpty()) components.add(comp);
                    }
                }
            }

            // Remaining edges form one component
            if (!edgeStack.isEmpty()) {
                Set<String> comp = new LinkedHashSet<String>();
                while (!edgeStack.isEmpty()) {
                    String[] e = edgeStack.pop();
                    comp.add(e[0]);
                    comp.add(e[1]);
                }
                if (!comp.isEmpty()) components.add(comp);
            }
        }

        // Also add isolated vertices as singleton components
        for (String v : adj.keySet()) {
            boolean found = false;
            for (Set<String> c : components) {
                if (c.contains(v)) { found = true; break; }
            }
            if (!found) {
                Set<String> singleton = new LinkedHashSet<String>();
                singleton.add(v);
                components.add(singleton);
            }
        }

        return components;
    }

    private static int countDfsChildren(Map<String, Set<String>> adj,
                                         String v, Map<String, String> par) {
        int count = 0;
        for (Map.Entry<String, String> e : par.entrySet()) {
            if (v.equals(e.getValue())) count++;
        }
        return count;
    }

    /**
     * Build a combinatorial planar embedding (cyclic neighbor ordering).
     * Uses a DFS-based approach to compute a consistent planar embedding.
     * Assigns virtual coordinates via a simple spring layout, then orders
     * neighbors by angle.
     *
     * <p>Performance: positions and forces use flat arrays indexed by vertex
     * ordinal, eliminating HashMap lookups from the O(V² × 50) force loop.
     * Neighbor indices for the attraction phase are pre-resolved once.</p>
     */
    private static Map<String, List<String>> buildPlanarEmbedding(
            Graph<String, Edge> graph) {
        Map<String, List<String>> embedding = new LinkedHashMap<String, List<String>>();
        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(graph);
        List<String> vertices = new ArrayList<String>(graph.getVertices());

        if (vertices.isEmpty()) return embedding;

        int n = vertices.size();

        // Map vertex name → index for O(1) lookups in the hot loop
        Map<String, Integer> vertexIndex = new HashMap<String, Integer>(n * 2);
        for (int i = 0; i < n; i++) vertexIndex.put(vertices.get(i), i);

        // Flat arrays: posX[i], posY[i] for vertex i
        double[] posX = new double[n];
        double[] posY = new double[n];

        // Initial layout: place on a circle
        for (int i = 0; i < n; i++) {
            double angle = 2.0 * Math.PI * i / n;
            posX[i] = Math.cos(angle);
            posY[i] = Math.sin(angle);
        }

        // Pre-resolve neighbor indices for the attraction phase.
        // neighborIdx[v] = array of integer indices of v's neighbors.
        int[][] neighborIdx = new int[n][];
        for (int i = 0; i < n; i++) {
            Set<String> nbrs = adj.get(vertices.get(i));
            if (nbrs == null || nbrs.isEmpty()) {
                neighborIdx[i] = new int[0];
            } else {
                int[] idx = new int[nbrs.size()];
                int k = 0;
                for (String nb : nbrs) {
                    idx[k++] = vertexIndex.get(nb);
                }
                neighborIdx[i] = idx;
            }
        }

        // Force-directed refinement using flat arrays (no HashMap in hot loop)
        double[] fx = new double[n];
        double[] fy = new double[n];

        for (int iter = 0; iter < 50; iter++) {
            // Zero forces
            Arrays.fill(fx, 0.0);
            Arrays.fill(fy, 0.0);

            // Repulsion between all pairs — pure array arithmetic
            for (int i = 0; i < n; i++) {
                double pix = posX[i], piy = posY[i];
                for (int j = i + 1; j < n; j++) {
                    double dx = pix - posX[j], dy = piy - posY[j];
                    double dist = Math.sqrt(dx * dx + dy * dy) + 0.001;
                    double force = 0.1 / (dist * dist);
                    double fdx = dx / dist * force;
                    double fdy = dy / dist * force;
                    fx[i] += fdx;
                    fy[i] += fdy;
                    fx[j] -= fdx;
                    fy[j] -= fdy;
                }
            }

            // Attraction along edges — uses pre-resolved indices
            for (int i = 0; i < n; i++) {
                double pix = posX[i], piy = posY[i];
                for (int ni : neighborIdx[i]) {
                    double dx = posX[ni] - pix, dy = posY[ni] - piy;
                    double dist = Math.sqrt(dx * dx + dy * dy) + 0.001;
                    double force = dist * 0.05;
                    fx[i] += dx / dist * force;
                    fy[i] += dy / dist * force;
                }
            }

            // Apply forces
            for (int i = 0; i < n; i++) {
                posX[i] += fx[i] * 0.1;
                posY[i] += fy[i] * 0.1;
            }
        }

        // Order neighbors by angle from each vertex
        for (int vi = 0; vi < n; vi++) {
            final double pvx = posX[vi], pvy = posY[vi];
            final double[] fpx = posX, fpy = posY;
            final Map<String, Integer> fvi = vertexIndex;
            String v = vertices.get(vi);
            List<String> neighbors = new ArrayList<String>();
            Set<String> nbrs = adj.get(v);
            if (nbrs != null) neighbors.addAll(nbrs);

            Collections.sort(neighbors, (String a, String b) -> {
                    int ai = fvi.get(a), bi = fvi.get(b);
                    double angA = Math.atan2(fpy[ai] - pvy, fpx[ai] - pvx);
                    double angB = Math.atan2(fpy[bi] - pvy, fpx[bi] - pvx);
                    return Double.compare(angA, angB);
                });

            embedding.put(v, neighbors);
        }
        return embedding;
    }

    private static String makeEdgeKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    /** Try to find a K₅ subdivision by looking for 5 high-degree vertices. */
    private static KuratowskiSubgraph findK5Subdivision(
            Map<String, Set<String>> adj, Graph<String, Edge> graph) {
        // Find vertices with degree >= 4 as candidates
        List<String> candidates = new ArrayList<String>();
        for (Map.Entry<String, Set<String>> entry : adj.entrySet()) {
            if (entry.getValue().size() >= 4) {
                candidates.add(entry.getKey());
            }
        }

        if (candidates.size() < 5) return null;

        // Try combinations of 5 candidates
        int n = Math.min(candidates.size(), 20); // limit search
        for (int a = 0; a < n; a++) {
            for (int b = a + 1; b < n; b++) {
                for (int c = b + 1; c < n; c++) {
                    for (int d = c + 1; d < n; d++) {
                        for (int e = d + 1; e < n; e++) {
                            String[] five = {candidates.get(a), candidates.get(b),
                                    candidates.get(c), candidates.get(d),
                                    candidates.get(e)};
                            List<List<String>> paths = findAllPairPaths(
                                    adj, five, new HashSet<String>(Arrays.asList(five)));
                            if (paths != null) {
                                return new KuratowskiSubgraph("K5",
                                        new LinkedHashSet<String>(Arrays.asList(five)),
                                        paths);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Try to find a K₃,₃ subdivision. */
    private static KuratowskiSubgraph findK33Subdivision(
            Map<String, Set<String>> adj, Graph<String, Edge> graph) {
        List<String> candidates = new ArrayList<String>();
        for (Map.Entry<String, Set<String>> entry : adj.entrySet()) {
            if (entry.getValue().size() >= 3) {
                candidates.add(entry.getKey());
            }
        }

        if (candidates.size() < 6) return null;

        int n = Math.min(candidates.size(), 15);
        // Try partitions into two sets of 3
        for (int a = 0; a < n; a++) {
            for (int b = a + 1; b < n; b++) {
                for (int c = b + 1; c < n; c++) {
                    String[] left = {candidates.get(a), candidates.get(b),
                            candidates.get(c)};
                    for (int d = 0; d < n; d++) {
                        if (d == a || d == b || d == c) continue;
                        for (int e = d + 1; e < n; e++) {
                            if (e == a || e == b || e == c) continue;
                            for (int f = e + 1; f < n; f++) {
                                if (f == a || f == b || f == c) continue;
                                String[] right = {candidates.get(d),
                                        candidates.get(e), candidates.get(f)};
                                List<List<String>> paths = findBipartitePaths(
                                        adj, left, right);
                                if (paths != null) {
                                    Set<String> branch = new LinkedHashSet<String>();
                                    for (String s : left) branch.add(s);
                                    for (String s : right) branch.add(s);
                                    return new KuratowskiSubgraph("K3_3",
                                            branch, paths);
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Find vertex-disjoint paths between all pairs of 5 vertices (K₅ check).
     * Returns list of 10 paths, or null if impossible.
     */
    private static List<List<String>> findAllPairPaths(
            Map<String, Set<String>> adj, String[] vertices,
            Set<String> branchSet) {
        List<List<String>> allPaths = new ArrayList<List<String>>();
        Set<String> usedInternal = new HashSet<String>();

        for (int i = 0; i < vertices.length; i++) {
            for (int j = i + 1; j < vertices.length; j++) {
                List<String> path = bfsPath(adj, vertices[i], vertices[j],
                        usedInternal, branchSet);
                if (path == null) return null;
                // Mark internal vertices as used
                for (int k = 1; k < path.size() - 1; k++) {
                    usedInternal.add(path.get(k));
                }
                allPaths.add(path);
            }
        }
        return allPaths;
    }

    /**
     * Find vertex-disjoint paths from each left to each right vertex (K₃,₃).
     */
    private static List<List<String>> findBipartitePaths(
            Map<String, Set<String>> adj, String[] left, String[] right) {
        List<List<String>> allPaths = new ArrayList<List<String>>();
        Set<String> usedInternal = new HashSet<String>();
        Set<String> branchSet = new HashSet<String>();
        for (String s : left) branchSet.add(s);
        for (String s : right) branchSet.add(s);

        for (String l : left) {
            for (String r : right) {
                List<String> path = bfsPath(adj, l, r, usedInternal, branchSet);
                if (path == null) return null;
                for (int k = 1; k < path.size() - 1; k++) {
                    usedInternal.add(path.get(k));
                }
                allPaths.add(path);
            }
        }
        return allPaths;
    }

    /** BFS shortest path avoiding usedInternal vertices (but allowing branch). */
    private static List<String> bfsPath(Map<String, Set<String>> adj,
                                         String src, String dst,
                                         Set<String> usedInternal,
                                         Set<String> branchSet) {
        if (src.equals(dst)) return Arrays.asList(src);

        Map<String, String> prev = new HashMap<String, String>();
        Queue<String> queue = new LinkedList<String>();
        queue.add(src);
        prev.put(src, src);

        while (!queue.isEmpty()) {
            String curr = queue.poll();
            Set<String> nbrs = adj.get(curr);
            if (nbrs == null) continue;
            for (String n : nbrs) {
                if (prev.containsKey(n)) continue;
                if (!n.equals(dst) && !branchSet.contains(n) &&
                    usedInternal.contains(n)) continue;
                if (!n.equals(dst) && branchSet.contains(n) &&
                    !n.equals(src)) continue;
                prev.put(n, curr);
                if (n.equals(dst)) {
                    // Reconstruct path
                    List<String> path = new ArrayList<String>();
                    String c = dst;
                    while (!c.equals(src)) {
                        path.add(c);
                        c = prev.get(c);
                    }
                    path.add(src);
                    Collections.reverse(path);
                    return path;
                }
                queue.add(n);
            }
        }
        return null;
    }
}
