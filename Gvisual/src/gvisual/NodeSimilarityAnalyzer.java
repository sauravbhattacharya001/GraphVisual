package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.util.*;

/**
 * Computes pairwise node similarity within a single graph using
 * multiple structural metrics.
 *
 * <p>Supported similarity measures:</p>
 * <ul>
 *   <li><b>Jaccard</b> — |N(u) ∩ N(v)| / |N(u) ∪ N(v)|</li>
 *   <li><b>Overlap (Szymkiewicz–Simpson)</b> — |N(u) ∩ N(v)| / min(|N(u)|, |N(v)|)</li>
 *   <li><b>Adamic-Adar</b> — Σ_{w ∈ N(u) ∩ N(v)} 1/log(|N(w)|)</li>
 *   <li><b>Cosine</b> — dot(deg_u, deg_v) / (||deg_u|| × ||deg_v||) on neighbor-degree vectors</li>
 *   <li><b>Structural equivalence</b> — fraction of all other nodes to which u and v
 *       have identical connectivity (both connected or both disconnected)</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 *   NodeSimilarityAnalyzer nsa = new NodeSimilarityAnalyzer(graph);
 *   double j = nsa.jaccard("A", "B");
 *   List&lt;ScoredPair&gt; top = nsa.mostSimilar(Metric.JACCARD, 10);
 *   Map&lt;String, Double&gt; nearest = nsa.kNearestNeighbors("A", Metric.ADAMIC_ADAR, 5);
 * </pre>
 *
 * @author zalenix
 */
public class NodeSimilarityAnalyzer {

    /** Available similarity metrics. */
    public enum Metric {
        JACCARD,
        OVERLAP,
        ADAMIC_ADAR,
        COSINE,
        STRUCTURAL_EQUIVALENCE
    }

    /** A scored pair of nodes. */
    public static class ScoredPair implements Comparable<ScoredPair> {
        private final String nodeA;
        private final String nodeB;
        private final double score;

        public ScoredPair(String nodeA, String nodeB, double score) {
            this.nodeA = nodeA;
            this.nodeB = nodeB;
            this.score = score;
        }

        public String getNodeA() { return nodeA; }
        public String getNodeB() { return nodeB; }
        public double getScore() { return score; }

        @Override
        public int compareTo(ScoredPair o) {
            return Double.compare(o.score, this.score); // descending
        }

        @Override
        public String toString() {
            return nodeA + " <-> " + nodeB + " = " + String.format("%.4f", score);
        }
    }

    private final Graph<String, Edge> graph;
    // Cached neighbor sets for performance
    private final Map<String, Set<String>> neighborCache;

    /**
     * Creates a new NodeSimilarityAnalyzer.
     *
     * @param graph the JUNG graph to analyze
     * @throws IllegalArgumentException if graph is null
     */
    public NodeSimilarityAnalyzer(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.neighborCache = GraphUtils.buildAdjacencyMap(graph);
    }

    // ── Single-pair metrics ─────────────────────────────────────

    /**
     * Jaccard similarity: |N(u) ∩ N(v)| / |N(u) ∪ N(v)|.
     * Returns 1.0 if both nodes are isolated (identical empty neighborhoods).
     *
     * @param u first node
     * @param v second node
     * @return Jaccard coefficient in [0, 1]
     */
    public double jaccard(String u, String v) {
        validateNode(u);
        validateNode(v);
        Set<String> nu = neighbors(u);
        Set<String> nv = neighbors(v);
        if (nu.isEmpty() && nv.isEmpty()) return 1.0;
        int intersection = intersectionSize(nu, nv);
        int union = nu.size() + nv.size() - intersection;
        return (double) intersection / union;
    }

    /**
     * Overlap coefficient (Szymkiewicz-Simpson):
     * |N(u) ∩ N(v)| / min(|N(u)|, |N(v)|).
     * Returns 1.0 if both isolated, 0.0 if exactly one is isolated.
     *
     * @param u first node
     * @param v second node
     * @return overlap coefficient in [0, 1]
     */
    public double overlap(String u, String v) {
        validateNode(u);
        validateNode(v);
        Set<String> nu = neighbors(u);
        Set<String> nv = neighbors(v);
        if (nu.isEmpty() && nv.isEmpty()) return 1.0;
        if (nu.isEmpty() || nv.isEmpty()) return 0.0;
        int intersection = intersectionSize(nu, nv);
        return (double) intersection / Math.min(nu.size(), nv.size());
    }

    /**
     * Adamic-Adar index: Σ_{w ∈ N(u) ∩ N(v)} 1/log(|N(w)|).
     * Higher values indicate more similarity through low-degree common neighbors.
     * Returns 0 if no common neighbors.
     *
     * @param u first node
     * @param v second node
     * @return Adamic-Adar score (unbounded, ≥ 0)
     */
    public double adamicAdar(String u, String v) {
        validateNode(u);
        validateNode(v);
        Set<String> nu = neighbors(u);
        Set<String> nv = neighbors(v);
        double score = 0.0;
        // iterate over smaller set for efficiency
        Set<String> smaller = nu.size() <= nv.size() ? nu : nv;
        Set<String> larger  = nu.size() <= nv.size() ? nv : nu;
        for (String w : smaller) {
            if (larger.contains(w)) {
                int degW = neighbors(w).size();
                if (degW > 1) {
                    score += 1.0 / Math.log(degW);
                }
                // degW <= 1: log(1)=0 or log(0)=-inf, skip (node contributes nothing meaningful)
            }
        }
        return score;
    }

    /**
     * Cosine similarity of neighborhood degree vectors.
     * For each node in the graph, the vector entry is the degree of that node
     * if it's a neighbor, 0 otherwise. Compares the "profile" of each node's
     * neighborhood.
     * Returns 1.0 if both isolated, 0.0 if exactly one is isolated.
     *
     * @param u first node
     * @param v second node
     * @return cosine similarity in [0, 1]
     */
    public double cosine(String u, String v) {
        validateNode(u);
        validateNode(v);
        Set<String> nu = neighbors(u);
        Set<String> nv = neighbors(v);
        if (nu.isEmpty() && nv.isEmpty()) return 1.0;
        if (nu.isEmpty() || nv.isEmpty()) return 0.0;

        // Sparse dot product: only iterate over nodes in either neighborhood
        double dot = 0.0, normU = 0.0, normV = 0.0;

        Set<String> allNeighbors = new HashSet<String>(nu);
        allNeighbors.addAll(nv);

        for (String w : allNeighbors) {
            int degW = neighbors(w).size();
            double du = nu.contains(w) ? degW : 0;
            double dv = nv.contains(w) ? degW : 0;
            dot += du * dv;
            normU += du * du;
            normV += dv * dv;
        }

        double denom = Math.sqrt(normU) * Math.sqrt(normV);
        return denom == 0 ? 0.0 : dot / denom;
    }

    /**
     * Structural equivalence: fraction of all other nodes to which u and v
     * have identical connectivity (both connected or both disconnected).
     * Returns 1.0 for identical connection patterns, 0.0 for completely opposite.
     *
     * @param u first node
     * @param v second node
     * @return structural equivalence in [0, 1]
     */
    public double structuralEquivalence(String u, String v) {
        validateNode(u);
        validateNode(v);
        Set<String> nu = neighbors(u);
        Set<String> nv = neighbors(v);
        Collection<String> allVertices = graph.getVertices();
        int total = 0;
        int match = 0;
        for (String w : allVertices) {
            if (w.equals(u) || w.equals(v)) continue;
            total++;
            boolean uHas = nu.contains(w);
            boolean vHas = nv.contains(w);
            if (uHas == vHas) match++;
        }
        return total == 0 ? 1.0 : (double) match / total;
    }

    /**
     * Compute similarity using the specified metric.
     *
     * @param u first node
     * @param v second node
     * @param metric the similarity metric to use
     * @return similarity score
     */
    public double similarity(String u, String v, Metric metric) {
        switch (metric) {
            case JACCARD:
                return jaccard(u, v);
            case OVERLAP:
                return overlap(u, v);
            case ADAMIC_ADAR:
                return adamicAdar(u, v);
            case COSINE:
                return cosine(u, v);
            case STRUCTURAL_EQUIVALENCE:
                return structuralEquivalence(u, v);
            default:
                throw new IllegalArgumentException("Unknown metric: " + metric);
        }
    }

    // ── Bulk operations ─────────────────────────────────────────

    /**
     * Find the top-k most similar node pairs in the entire graph.
     * Uses a min-heap to efficiently track the top-k pairs without
     * storing all O(n²) scores.
     *
     * @param metric the similarity metric to use
     * @param k      maximum number of pairs to return
     * @return list of scored pairs, sorted by score descending
     */
    public List<ScoredPair> mostSimilar(Metric metric, int k) {
        if (k <= 0) return Collections.emptyList();

        List<String> vertices = new ArrayList<String>(graph.getVertices());
        Collections.sort(vertices); // deterministic ordering
        PriorityQueue<ScoredPair> minHeap = new PriorityQueue<ScoredPair>(k + 1,
            (ScoredPair a, ScoredPair b) -> {
                    return Double.compare(a.score, b.score); // min-heap by score
                }
        );

        for (int i = 0; i < vertices.size(); i++) {
            for (int j = i + 1; j < vertices.size(); j++) {
                double score = similarity(vertices.get(i), vertices.get(j), metric);
                ScoredPair sp = new ScoredPair(vertices.get(i), vertices.get(j), score);
                minHeap.offer(sp);
                if (minHeap.size() > k) {
                    minHeap.poll();
                }
            }
        }

        List<ScoredPair> result = new ArrayList<ScoredPair>(minHeap);
        Collections.sort(result); // descending by score
        return result;
    }

    /**
     * Find the k most similar nodes to a given target node.
     *
     * @param target the target node
     * @param metric the similarity metric to use
     * @param k      maximum number of neighbors to return
     * @return map of node ID → similarity score, sorted by score descending
     */
    public LinkedHashMap<String, Double> kNearestNeighbors(String target, Metric metric, int k) {
        validateNode(target);
        if (k <= 0) return new LinkedHashMap<String, Double>();

        List<ScoredPair> scores = new ArrayList<ScoredPair>();
        for (String v : graph.getVertices()) {
            if (v.equals(target)) continue;
            double s = similarity(target, v, metric);
            scores.add(new ScoredPair(target, v, s));
        }
        Collections.sort(scores); // descending by score

        LinkedHashMap<String, Double> result = new LinkedHashMap<String, Double>();
        int count = 0;
        for (ScoredPair sp : scores) {
            if (count >= k) break;
            result.put(sp.getNodeB(), sp.getScore());
            count++;
        }
        return result;
    }

    /**
     * Compute the full pairwise similarity matrix for all nodes.
     * Returns a map of "nodeA|nodeB" → score (only upper triangle, a &lt; b).
     *
     * @param metric the similarity metric to use
     * @return pairwise similarity map
     */
    public Map<String, Double> similarityMatrix(Metric metric) {
        List<String> vertices = new ArrayList<String>(graph.getVertices());
        Collections.sort(vertices);
        Map<String, Double> matrix = new LinkedHashMap<String, Double>();
        for (int i = 0; i < vertices.size(); i++) {
            for (int j = i + 1; j < vertices.size(); j++) {
                String key = vertices.get(i) + "|" + vertices.get(j);
                matrix.put(key, similarity(vertices.get(i), vertices.get(j), metric));
            }
        }
        return matrix;
    }

    /**
     * Find all node pairs with similarity above a threshold.
     *
     * @param metric    the similarity metric to use
     * @param threshold minimum score (inclusive)
     * @return list of scored pairs above threshold, sorted descending
     */
    public List<ScoredPair> similarPairsAboveThreshold(Metric metric, double threshold) {
        List<String> vertices = new ArrayList<String>(graph.getVertices());
        Collections.sort(vertices);
        List<ScoredPair> result = new ArrayList<ScoredPair>();

        for (int i = 0; i < vertices.size(); i++) {
            for (int j = i + 1; j < vertices.size(); j++) {
                double score = similarity(vertices.get(i), vertices.get(j), metric);
                if (score >= threshold) {
                    result.add(new ScoredPair(vertices.get(i), vertices.get(j), score));
                }
            }
        }
        Collections.sort(result); // descending
        return result;
    }

    /**
     * Generate a human-readable similarity report for a node,
     * showing its top-k most similar neighbors across all metrics.
     *
     * @param target the target node
     * @param k      number of similar nodes to show per metric
     * @return formatted report string
     */
    public String report(String target, int k) {
        validateNode(target);
        StringBuilder sb = new StringBuilder();
        sb.append("=== Node Similarity Report: ").append(target).append(" ===\n");
        sb.append("Degree: ").append(neighbors(target).size()).append("\n\n");

        for (Metric m : Metric.values()) {
            sb.append("--- ").append(m.name()).append(" ---\n");
            LinkedHashMap<String, Double> knn = kNearestNeighbors(target, m, k);
            if (knn.isEmpty()) {
                sb.append("  (no other nodes)\n");
            }
            for (Map.Entry<String, Double> e : knn.entrySet()) {
                sb.append(String.format("  %-20s %.4f%n", e.getKey(), e.getValue()));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ── Internal helpers ────────────────────────────────────────

    private Set<String> neighbors(String node) {
        Set<String> cached = neighborCache.get(node);
        if (cached != null) return cached;
        Collection<String> raw = graph.getNeighbors(node);
        Set<String> set = raw == null ? Collections.<String>emptySet()
                                      : new HashSet<String>(raw);
        neighborCache.put(node, set);
        return set;
    }

    private int intersectionSize(Set<String> a, Set<String> b) {
        Set<String> smaller = a.size() <= b.size() ? a : b;
        Set<String> larger  = a.size() <= b.size() ? b : a;
        int count = 0;
        for (String s : smaller) {
            if (larger.contains(s)) count++;
        }
        return count;
    }

    private void validateNode(String node) {
        if (node == null || !graph.containsVertex(node)) {
            throw new IllegalArgumentException("Node not in graph: " + node);
        }
    }
}
