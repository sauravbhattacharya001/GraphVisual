package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Graph Isomorphism Checker — determines whether two graphs are structurally
 * identical (isomorphic) using a backtracking algorithm with degree-based
 * pruning inspired by the VF2 approach.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Isomorphism test</b> — checks if two graphs have the same
 *       structure (same vertex/edge count, degree sequence, and a valid
 *       bijective vertex mapping that preserves adjacency).</li>
 *   <li><b>Vertex mapping</b> — when isomorphic, returns one valid mapping
 *       from vertices in graph A to vertices in graph B.</li>
 *   <li><b>Quick rejection</b> — fast-fails on vertex count, edge count,
 *       and degree sequence mismatches before attempting the expensive
 *       backtracking search.</li>
 *   <li><b>Neighbour-degree signature pruning</b> — during backtracking,
 *       candidate vertices must have matching sorted neighbour-degree
 *       profiles, dramatically reducing the search space.</li>
 *   <li><b>Text report</b> — formatted summary with result and mapping.</li>
 * </ul>
 *
 * <p>Designed for small-to-medium graphs (hundreds of vertices). The worst
 * case is exponential, but degree-based pruning makes it practical for
 * real-world networks.</p>
 *
 * @author zalenix
 */
public class GraphIsomorphismChecker {

    private final Graph<String, Edge> graphA;
    private final Graph<String, Edge> graphB;
    private Map<String, String> mapping;
    private boolean computed;
    private boolean isomorphic;
    private String rejectionReason;

    // Internal indexed representations
    private int nA, nB;
    private List<String> verticesA, verticesB;
    private Map<String, Integer> idxA, idxB;
    private int[][] adjA, adjB;
    private int[] degA, degB;
    /** Reverse mapping: mapBtoA[bIdx] = aIdx, or -1 if unmapped.
     *  Eliminates O(V) linear scan in findMappedA(). */
    private int[] mapBtoA;

    /**
     * Creates a checker for the given pair of graphs.
     *
     * @param graphA first graph
     * @param graphB second graph
     * @throws IllegalArgumentException if either graph is null
     */
    public GraphIsomorphismChecker(Graph<String, Edge> graphA,
                                   Graph<String, Edge> graphB) {
        if (graphA == null || graphB == null) {
            throw new IllegalArgumentException("Graphs must not be null");
        }
        this.graphA = graphA;
        this.graphB = graphB;
        this.computed = false;
        this.isomorphic = false;
    }

    /**
     * Runs the isomorphism check. Must be called before querying results.
     */
    public void compute() {
        mapping = new LinkedHashMap<String, String>();
        nA = graphA.getVertexCount();
        nB = graphB.getVertexCount();

        // Quick reject: vertex count
        if (nA != nB) {
            isomorphic = false;
            rejectionReason = String.format(
                "Vertex count mismatch: %d vs %d", nA, nB);
            computed = true;
            return;
        }

        // Quick reject: edge count
        int eA = graphA.getEdgeCount();
        int eB = graphB.getEdgeCount();
        if (eA != eB) {
            isomorphic = false;
            rejectionReason = String.format(
                "Edge count mismatch: %d vs %d", eA, eB);
            computed = true;
            return;
        }

        // Trivial: both empty
        if (nA == 0) {
            isomorphic = true;
            rejectionReason = null;
            computed = true;
            return;
        }

        // Build indexed structures
        verticesA = new ArrayList<String>(graphA.getVertices());
        verticesB = new ArrayList<String>(graphB.getVertices());
        idxA = buildIndexMap(verticesA);
        idxB = buildIndexMap(verticesB);
        adjA = buildAdjacency(graphA, verticesA, idxA);
        adjB = buildAdjacency(graphB, verticesB, idxB);
        degA = new int[nA];
        degB = new int[nB];
        for (int i = 0; i < nA; i++) degA[i] = adjA[i].length;
        for (int i = 0; i < nB; i++) degB[i] = adjB[i].length;

        // Quick reject: degree sequence
        int[] sortedDegA = Arrays.copyOf(degA, nA);
        int[] sortedDegB = Arrays.copyOf(degB, nB);
        Arrays.sort(sortedDegA);
        Arrays.sort(sortedDegB);
        if (!Arrays.equals(sortedDegA, sortedDegB)) {
            isomorphic = false;
            rejectionReason = "Degree sequence mismatch";
            computed = true;
            return;
        }

        // Partition vertices by degree for candidate generation
        // Sort A vertices by degree (ascending) to try constrained vertices first
        Integer[] orderA = new Integer[nA];
        for (int i = 0; i < nA; i++) orderA[i] = i;
        Arrays.sort(orderA, new Comparator<Integer>() {
            public int compare(Integer a, Integer b) {
                return Integer.compare(degA[a], degA[b]);
            }
        });

        // Group B vertices by degree
        Map<Integer, List<Integer>> degGroupB = new HashMap<Integer, List<Integer>>();
        for (int i = 0; i < nB; i++) {
            int d = degB[i];
            List<Integer> group = degGroupB.get(d);
            if (group == null) {
                group = new ArrayList<Integer>();
                degGroupB.put(d, group);
            }
            group.add(i);
        }

        // Compute neighbour-degree signatures for pruning
        int[][] sigA = new int[nA][];
        int[][] sigB = new int[nB][];
        for (int i = 0; i < nA; i++) {
            sigA[i] = neighbourSignature(adjA, degA, i);
        }
        for (int i = 0; i < nB; i++) {
            sigB[i] = neighbourSignature(adjB, degB, i);
        }

        // Sort adjacency lists for O(log V) binary-search adjacency checks
        for (int i = 0; i < nA; i++) Arrays.sort(adjA[i]);
        for (int i = 0; i < nB; i++) Arrays.sort(adjB[i]);

        // Backtracking search
        int[] mapAtoB = new int[nA];
        boolean[] usedB = new boolean[nB];
        Arrays.fill(mapAtoB, -1);
        mapBtoA = new int[nB];
        Arrays.fill(mapBtoA, -1);

        isomorphic = backtrack(0, orderA, mapAtoB, usedB,
                               degGroupB, sigA, sigB);

        if (isomorphic) {
            rejectionReason = null;
            for (int i = 0; i < nA; i++) {
                mapping.put(verticesA.get(i), verticesB.get(mapAtoB[i]));
            }
        } else {
            rejectionReason = "No valid mapping found";
        }

        computed = true;
    }

    /** Returns true if the two graphs are isomorphic. */
    public boolean isIsomorphic() {
        ensureComputed();
        return isomorphic;
    }

    /**
     * Returns the vertex mapping from graph A to graph B, or an empty map
     * if the graphs are not isomorphic.
     */
    public Map<String, String> getMapping() {
        ensureComputed();
        return Collections.unmodifiableMap(mapping);
    }

    /** Returns the reason for rejection, or null if isomorphic. */
    public String getRejectionReason() {
        ensureComputed();
        return rejectionReason;
    }

    /**
     * Returns a human-readable summary.
     */
    public String getSummary() {
        ensureComputed();
        StringBuilder sb = new StringBuilder();
        sb.append("=== Graph Isomorphism Check ===\n");
        sb.append(String.format("Graph A: %d vertices, %d edges\n",
                graphA.getVertexCount(), graphA.getEdgeCount()));
        sb.append(String.format("Graph B: %d vertices, %d edges\n",
                graphB.getVertexCount(), graphB.getEdgeCount()));
        sb.append(String.format("Isomorphic: %s\n", isomorphic ? "YES" : "NO"));
        if (!isomorphic && rejectionReason != null) {
            sb.append(String.format("Reason: %s\n", rejectionReason));
        }
        if (isomorphic && !mapping.isEmpty()) {
            sb.append("Mapping (A → B):\n");
            for (Map.Entry<String, String> e : mapping.entrySet()) {
                sb.append(String.format("  %s → %s\n", e.getKey(), e.getValue()));
            }
        }
        return sb.toString();
    }

    // --- Private helpers ---

    private boolean backtrack(int depth, Integer[] orderA, int[] mapAtoB,
                              boolean[] usedB,
                              Map<Integer, List<Integer>> degGroupB,
                              int[][] sigA, int[][] sigB) {
        if (depth == nA) return true;

        int aIdx = orderA[depth];
        int aDeg = degA[aIdx];
        List<Integer> candidates = degGroupB.get(aDeg);
        if (candidates == null) return false;

        for (int bIdx : candidates) {
            if (usedB[bIdx]) continue;

            // Neighbour-degree signature check
            if (!Arrays.equals(sigA[aIdx], sigB[bIdx])) continue;

            // Check adjacency consistency with already-mapped vertices
            if (!isConsistent(aIdx, bIdx, mapAtoB)) continue;

            mapAtoB[aIdx] = bIdx;
            usedB[bIdx] = true;
            mapBtoA[bIdx] = aIdx;

            if (backtrack(depth + 1, orderA, mapAtoB, usedB,
                          degGroupB, sigA, sigB)) {
                return true;
            }

            mapAtoB[aIdx] = -1;
            usedB[bIdx] = false;
            mapBtoA[bIdx] = -1;
        }

        return false;
    }

    private boolean isConsistent(int aIdx, int bIdx, int[] mapAtoB) {
        // For each neighbour of aIdx already mapped, verify the mapped
        // vertex is a neighbour of bIdx
        for (int aNbr : adjA[aIdx]) {
            int bMapped = mapAtoB[aNbr];
            if (bMapped >= 0) {
                // aNbr is mapped to bMapped; bMapped must be adjacent to bIdx
                if (!isAdjacent(adjB, bIdx, bMapped)) return false;
            }
        }
        // Reverse check: for each neighbour of bIdx already used in mapping,
        // the corresponding A vertex must be a neighbour of aIdx
        for (int bNbr : adjB[bIdx]) {
            int aCorr = mapBtoA[bNbr];  // O(1) reverse lookup
            if (aCorr >= 0) {
                if (!isAdjacent(adjA, aIdx, aCorr)) return false;
            }
        }
        return true;
    }

    /**
     * O(1) reverse lookup replaced findMappedA — see mapBtoA field.
     * Kept as documentation: findMappedA was O(V) linear scan, now eliminated.
     */

    private boolean isAdjacent(int[][] adj, int u, int v) {
        return Arrays.binarySearch(adj[u], v) >= 0;
    }

    private int[] neighbourSignature(int[][] adj, int[] deg, int v) {
        int[] sig = new int[adj[v].length];
        for (int i = 0; i < sig.length; i++) {
            sig[i] = deg[adj[v][i]];
        }
        Arrays.sort(sig);
        return sig;
    }

    private Map<String, Integer> buildIndexMap(List<String> vertices) {
        Map<String, Integer> map = new HashMap<String, Integer>(vertices.size() * 2);
        for (int i = 0; i < vertices.size(); i++) {
            map.put(vertices.get(i), i);
        }
        return map;
    }

    private int[][] buildAdjacency(Graph<String, Edge> g,
                                    List<String> vertices,
                                    Map<String, Integer> idxMap) {
        int n = vertices.size();
        @SuppressWarnings("unchecked")
        List<Integer>[] tmp = new List[n];
        for (int i = 0; i < n; i++) tmp[i] = new ArrayList<Integer>();
        for (Edge e : g.getEdges()) {
            Integer ui = idxMap.get(e.getVertex1());
            Integer vi = idxMap.get(e.getVertex2());
            if (ui != null && vi != null && !ui.equals(vi)) {
                tmp[ui].add(vi);
                tmp[vi].add(ui);
            }
        }
        int[][] adj = new int[n][];
        for (int i = 0; i < n; i++) {
            List<Integer> nb = tmp[i];
            adj[i] = new int[nb.size()];
            for (int j = 0; j < nb.size(); j++) adj[i][j] = nb.get(j);
        }
        return adj;
    }

    private void ensureComputed() {
        if (!computed) {
            throw new IllegalStateException("Call compute() before querying results");
        }
    }
}
