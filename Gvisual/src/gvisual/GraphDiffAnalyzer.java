package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.util.*;

/**
 * Compares two graphs and computes structural differences: added, removed,
 * and common nodes and edges, plus similarity metrics. Useful for analyzing
 * how a network evolves over time or comparing alternative network models.
 *
 * <h3>Use Cases</h3>
 * <ul>
 *   <li>Track network evolution across snapshots (before/after)</li>
 *   <li>Compare observed vs. predicted network structures</li>
 *   <li>Detect topology changes in communication networks</li>
 *   <li>Measure similarity between two social/IMEI graphs</li>
 * </ul>
 *
 * <h3>Metrics</h3>
 * <ul>
 *   <li><b>Jaccard Similarity (nodes):</b> |intersection| / |union|</li>
 *   <li><b>Jaccard Similarity (edges):</b> same, based on endpoint pairs</li>
 *   <li><b>Edit Distance:</b> total additions + removals to transform A into B</li>
 * </ul>
 *
 * @author zalenix
 */
public class GraphDiffAnalyzer {

    private final Graph<String, Edge> graphA;
    private final Graph<String, Edge> graphB;

    /**
     * Create a diff analyzer comparing graphA (baseline) to graphB (target).
     *
     * @param graphA the baseline graph
     * @param graphB the target graph to compare against
     * @throws IllegalArgumentException if either graph is null
     */
    public GraphDiffAnalyzer(Graph<String, Edge> graphA, Graph<String, Edge> graphB) {
        if (graphA == null || graphB == null) {
            throw new IllegalArgumentException("Both graphs must not be null");
        }
        this.graphA = graphA;
        this.graphB = graphB;
    }

    // ── Result class ────────────────────────────────────────────

    /**
     * Holds the complete diff result between two graphs.
     * <p>
     * Includes node/Edge differences, similarity metrics, edit distance,
     * and degree changes — all computed in a single pass by
     * {@link GraphDiffAnalyzer#computeDiff()}.
     */
    public static class DiffResult {
        private final Set<String> addedNodes;
        private final Set<String> removedNodes;
        private final Set<String> commonNodes;
        private final List<EdgeDiff> addedEdges;
        private final List<EdgeDiff> removedEdges;
        private final List<EdgeDiff> commonEdges;
        private final double nodeJaccard;
        private final double edgeJaccard;
        private final int editDistance;
        private final Map<String, int[]> degreeChanges;

        public DiffResult(Set<String> addedNodes, Set<String> removedNodes,
                          Set<String> commonNodes, List<EdgeDiff> addedEdges,
                          List<EdgeDiff> removedEdges, List<EdgeDiff> commonEdges,
                          double nodeJaccard, double edgeJaccard,
                          int editDistance, Map<String, int[]> degreeChanges) {
            this.addedNodes = Collections.unmodifiableSet(addedNodes);
            this.removedNodes = Collections.unmodifiableSet(removedNodes);
            this.commonNodes = Collections.unmodifiableSet(commonNodes);
            this.addedEdges = Collections.unmodifiableList(addedEdges);
            this.removedEdges = Collections.unmodifiableList(removedEdges);
            this.commonEdges = Collections.unmodifiableList(commonEdges);
            this.nodeJaccard = nodeJaccard;
            this.edgeJaccard = edgeJaccard;
            this.editDistance = editDistance;
            this.degreeChanges = Collections.unmodifiableMap(degreeChanges);
        }

        /** Nodes present in B but not in A. */
        public Set<String> getAddedNodes() { return addedNodes; }

        /** Nodes present in A but not in B. */
        public Set<String> getRemovedNodes() { return removedNodes; }

        /** Nodes present in both A and B. */
        public Set<String> getCommonNodes() { return commonNodes; }

        /** Edges present in B but not in A. */
        public List<EdgeDiff> getAddedEdges() { return addedEdges; }

        /** Edges present in A but not in B. */
        public List<EdgeDiff> getRemovedEdges() { return removedEdges; }

        /** Edges present in both A and B. */
        public List<EdgeDiff> getCommonEdges() { return commonEdges; }

        /** Jaccard similarity of node sets: |A∩B| / |A∪B|. */
        public double getNodeJaccard() { return nodeJaccard; }

        /** Jaccard similarity of edge sets: |A∩B| / |A∪B|. */
        public double getEdgeJaccard() { return edgeJaccard; }

        /**
         * Graph edit distance: total additions + removals (nodes and edges)
         * needed to transform A into B.
         */
        public int getEditDistance() { return editDistance; }

        /**
         * Nodes whose degree changed between graphs A and B.
         * Keys are node IDs; values are {@code [degreeInA, degreeInB]}.
         * Only includes nodes present in both graphs.
         */
        public Map<String, int[]> getDegreeChanges() { return degreeChanges; }

        /** True if the two graphs are structurally identical. */
        public boolean isIdentical() {
            return addedNodes.isEmpty() && removedNodes.isEmpty()
                    && addedEdges.isEmpty() && removedEdges.isEmpty();
        }

        /**
         * Summary string describing the diff.
         */
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Graph Diff Summary ===\n");
            sb.append(String.format("Nodes: %d added, %d removed, %d common (Jaccard: %.4f)%n",
                    addedNodes.size(), removedNodes.size(), commonNodes.size(), nodeJaccard));
            sb.append(String.format("Edges: %d added, %d removed, %d common (Jaccard: %.4f)%n",
                    addedEdges.size(), removedEdges.size(), commonEdges.size(), edgeJaccard));
            sb.append(String.format("Edit distance: %d%n", editDistance));
            if (!degreeChanges.isEmpty()) {
                sb.append(String.format("Degree changes: %d nodes%n", degreeChanges.size()));
            }
            if (isIdentical()) {
                sb.append("Graphs are structurally identical.\n");
            }
            return sb.toString();
        }
    }

    /**
     * Represents an edge for diff purposes (endpoint pair, normalized order
     * for undirected comparison).
     */
    public static class EdgeDiff {
        private final String vertex1;
        private final String vertex2;
        private final String edgeKey;

        public EdgeDiff(String v1, String v2) {
            if (v1.compareTo(v2) <= 0) {
                this.vertex1 = v1;
                this.vertex2 = v2;
            } else {
                this.vertex1 = v2;
                this.vertex2 = v1;
            }
            this.edgeKey = this.vertex1 + "-" + this.vertex2;
        }

        public String getVertex1() { return vertex1; }
        public String getVertex2() { return vertex2; }
        public String getEdgeKey() { return edgeKey; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EdgeDiff that = (EdgeDiff) o;
            return edgeKey.equals(that.edgeKey);
        }

        @Override
        public int hashCode() {
            return edgeKey.hashCode();
        }

        @Override
        public String toString() {
            return vertex1 + " <-> " + vertex2;
        }
    }

    // ── Analysis ────────────────────────────────────────────────

    /**
     * Compute the full diff between graphA and graphB.
     * <p>
     * This single call computes all node/Edge differences, Jaccard
     * similarity, edit distance, and degree changes. Use the returned
     * {@link DiffResult} to access everything — there is no need to call
     * {@link #findDegreeChanges()} or {@link #computeEditDistance()}
     * separately unless you specifically want to avoid the full diff.
     *
     * @return a DiffResult with all differences and similarity scores
     */
    public DiffResult computeDiff() {
        Set<String> nodesA = new HashSet<>(graphA.getVertices());
        Set<String> nodesB = new HashSet<>(graphB.getVertices());

        Set<String> addedNodes = new TreeSet<>(nodesB);
        addedNodes.removeAll(nodesA);

        Set<String> removedNodes = new TreeSet<>(nodesA);
        removedNodes.removeAll(nodesB);

        Set<String> commonNodes = new TreeSet<>(nodesA);
        commonNodes.retainAll(nodesB);

        Set<EdgeDiff> edgesA = extractEdges(graphA);
        Set<EdgeDiff> edgesB = extractEdges(graphB);

        Set<EdgeDiff> addedEdgeSet = new HashSet<>(edgesB);
        addedEdgeSet.removeAll(edgesA);

        Set<EdgeDiff> removedEdgeSet = new HashSet<>(edgesA);
        removedEdgeSet.removeAll(edgesB);

        Set<EdgeDiff> commonEdgeSet = new HashSet<>(edgesA);
        commonEdgeSet.retainAll(edgesB);

        double nodeJaccard = jaccard(nodesA.size(), nodesB.size(), commonNodes.size());
        double edgeJaccard = jaccard(edgesA.size(), edgesB.size(), commonEdgeSet.size());

        // Edit distance: total structural changes to transform A into B
        int editDistance = addedNodes.size() + removedNodes.size()
                + addedEdgeSet.size() + removedEdgeSet.size();

        // Degree changes for common nodes
        Map<String, int[]> degreeChanges = new TreeMap<>();
        for (String node : commonNodes) {
            int degA = graphA.degree(node);
            int degB = graphB.degree(node);
            if (degA != degB) {
                degreeChanges.put(node, new int[]{degA, degB});
            }
        }

        return new DiffResult(
                addedNodes, removedNodes, commonNodes,
                new ArrayList<>(addedEdgeSet),
                new ArrayList<>(removedEdgeSet),
                new ArrayList<>(commonEdgeSet),
                nodeJaccard, edgeJaccard,
                editDistance, degreeChanges
        );
    }

    /**
     * Find nodes that exist in both graphs but whose degree changed.
     * <p>
     * Convenience method — delegates to {@link #computeDiff()} internally.
     * Prefer calling {@code computeDiff().getDegreeChanges()} if you also
     * need other diff information, to avoid redundant work.
     *
     * @return map of node to [degreeInA, degreeInB] for changed nodes
     */
    public Map<String, int[]> findDegreeChanges() {
        return computeDiff().getDegreeChanges();
    }

    /**
     * Compute the edit distance: total node/edge additions + removals
     * needed to transform A into B.
     * <p>
     * Convenience method — delegates to {@link #computeDiff()} internally.
     * Prefer calling {@code computeDiff().getEditDistance()} if you also
     * need other diff information, to avoid redundant work.
     *
     * @return the graph edit distance
     */
    public int computeEditDistance() {
        return computeDiff().getEditDistance();
    }

    // ── Helpers ─────────────────────────────────────────────────

    private Set<EdgeDiff> extractEdges(Graph<String, Edge> g) {
        Set<EdgeDiff> edges = new HashSet<>();
        for (Edge e : g.getEdges()) {
            Collection<String> endpoints = g.getEndpoints(e);
            if (endpoints != null && endpoints.size() == 2) {
                Iterator<String> it = endpoints.iterator();
                edges.add(new EdgeDiff(it.next(), it.next()));
            }
        }
        return edges;
    }

    private double jaccard(int sizeA, int sizeB, int intersection) {
        int union = sizeA + sizeB - intersection;
        if (union == 0) return 1.0;
        return (double) intersection / union;
    }
}
