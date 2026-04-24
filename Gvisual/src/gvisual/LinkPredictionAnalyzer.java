package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.util.*;

/**
 * Predicts likely missing edges in a graph using well-known link prediction
 * metrics from social network analysis.
 *
 * <p>Implements four scoring methods:</p>
 * <ul>
 *   <li><b>Common Neighbors</b> — count of shared neighbors between two nodes</li>
 *   <li><b>Jaccard Coefficient</b> — shared neighbors / union of neighbors (0–1)</li>
 *   <li><b>Adamic-Adar Index</b> — weighted common neighbors, inverse log of degree</li>
 *   <li><b>Preferential Attachment</b> — product of degrees (high-degree nodes attract)</li>
 * </ul>
 *
 * <p>Useful for:</p>
 * <ul>
 *   <li>Predicting future connections in social networks</li>
 *   <li>Finding potential IMEI correlations not yet in the data</li>
 *   <li>Identifying likely missing links in incomplete graphs</li>
 *   <li>Recommending connections in collaboration networks</li>
 * </ul>
 *
 * @author zalenix
 */
public class LinkPredictionAnalyzer {

    private final Graph<String, Edge> graph;

    /**
     * Lazily cached adjacency map — built once on first use and reused
     * across predict(), predictEnsemble(), and score() calls.  Previously
     * each method independently called GraphUtils.buildAdjacencyMap(),
     * resulting in O(V+E) redundant work per call on the same analyzer
     * instance.
     */
    private Map<String, Set<String>> cachedAdjacency;

    /**
     * Create a new link prediction analyzer.
     *
     * @param graph the JUNG graph to analyze (must not be null)
     * @throws IllegalArgumentException if graph is null
     */
    public LinkPredictionAnalyzer(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
    }

    /**
     * Returns the cached adjacency map, building it on first access.
     */
    private Map<String, Set<String>> adjacency() {
        if (cachedAdjacency == null) {
            cachedAdjacency = GraphUtils.buildAdjacencyMap(graph);
        }
        return cachedAdjacency;
    }

    // ── Score methods ───────────────────────────────────────────

    /**
     * Scoring method for link prediction.
     */
    public enum Method {
        /** Number of shared neighbors. */
        COMMON_NEIGHBORS,
        /** Shared neighbors divided by union of neighbor sets. */
        JACCARD,
        /** Sum of 1/log(degree) for each common neighbor. */
        ADAMIC_ADAR,
        /** Product of the two nodes' degrees. */
        PREFERENTIAL_ATTACHMENT,
        /** Weighted average across multiple prediction methods. */
        ENSEMBLE
    }

    // ── Result classes ──────────────────────────────────────────

    /**
     * A predicted link with its score.
     */
    public static class PredictedLink {
        private final String vertex1;
        private final String vertex2;
        private final double score;
        private final Method method;
        private final Set<String> commonNeighbors;

        public PredictedLink(String vertex1, String vertex2, double score,
                             Method method, Set<String> commonNeighbors) {
            this.vertex1 = vertex1;
            this.vertex2 = vertex2;
            this.score = score;
            this.method = method;
            this.commonNeighbors = Collections.unmodifiableSet(commonNeighbors);
        }

        public String getVertex1() { return vertex1; }
        public String getVertex2() { return vertex2; }
        public double getScore() { return score; }
        public Method getMethod() { return method; }
        public Set<String> getCommonNeighbors() { return commonNeighbors; }

        @Override
        public String toString() {
            return String.format("%s -- %s  (%.4f, %s, %d common)",
                    vertex1, vertex2, score, method, commonNeighbors.size());
        }
    }

    /**
     * Full prediction result with metadata.
     */
    public static class PredictionResult {
        private final List<PredictedLink> predictions;
        private final Method method;
        private final int totalVertices;
        private final int existingEdges;
        private final long possibleEdges;
        private final int candidatesEvaluated;

        public PredictionResult(List<PredictedLink> predictions, Method method,
                                int totalVertices, int existingEdges,
                                long possibleEdges, int candidatesEvaluated) {
            this.predictions = Collections.unmodifiableList(predictions);
            this.method = method;
            this.totalVertices = totalVertices;
            this.existingEdges = existingEdges;
            this.possibleEdges = possibleEdges;
            this.candidatesEvaluated = candidatesEvaluated;
        }

        public List<PredictedLink> getPredictions() { return predictions; }
        public Method getMethod() { return method; }
        public int getTotalVertices() { return totalVertices; }
        public int getExistingEdges() { return existingEdges; }
        public long getPossibleEdges() { return possibleEdges; }
        public int getCandidatesEvaluated() { return candidatesEvaluated; }

        /** Graph density (existing edges / possible edges). */
        public double getDensity() {
            return possibleEdges > 0
                    ? (double) existingEdges / possibleEdges
                    : 0;
        }

        /** Summary text for display. */
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Link Prediction (%s)\n", method));
            sb.append(String.format("Vertices: %d | Edges: %d | Density: %.2f%%\n",
                    totalVertices, existingEdges, getDensity() * 100));
            sb.append(String.format("Candidates evaluated: %d\n", candidatesEvaluated));
            sb.append(String.format("Top predictions: %d\n", predictions.size()));
            sb.append("─────────────────────────────────────\n");
            int rank = 1;
            for (PredictedLink link : predictions) {
                sb.append(String.format("%2d. %s -- %s  score=%.4f  (%d common)\n",
                        rank++, link.getVertex1(), link.getVertex2(),
                        link.getScore(), link.getCommonNeighbors().size()));
            }
            return sb.toString();
        }
    }

    // ── Analysis ────────────────────────────────────────────────

    /**
     * Predict missing links using the specified method.
     *
     * <p>Uses a streaming top-K approach with a min-heap of size K.
     * For COMMON_NEIGHBORS, JACCARD, and ADAMIC_ADAR, only pairs sharing
     * at least one common neighbor can score &gt; 0, so we enumerate
     * <b>2-hop pairs</b> (neighbors-of-neighbors) instead of all O(V²)
     * vertex pairs — reducing work to O(V·Δ²) where Δ is max degree.
     * On sparse graphs (Δ ≪ V) this is orders of magnitude faster.
     * PREFERENTIAL_ATTACHMENT still uses the O(V²) sweep since any pair
     * with non-zero degrees scores positively.</p>
     *
     * @param method scoring method to use
     * @param topK   number of top predictions to return
     * @return prediction result with ranked candidate edges
     */
    public PredictionResult predict(Method method, int topK) {
        Collection<String> vertices = graph.getVertices();
        int n = vertices.size();
        int existingEdges = graph.getEdgeCount();
        long possibleEdges = (long) n * (n - 1) / 2;
        Map<String, Set<String>> adjacency = adjacency();

        PriorityQueue<PredictedLink> minHeap = new PriorityQueue<PredictedLink>(
                topK + 1,
                (PredictedLink a, PredictedLink b) -> Double.compare(a.getScore(), b.getScore()));

        int candidatesEvaluated = 0;

        if (method == Method.PREFERENTIAL_ATTACHMENT) {
            // PA scores are non-zero for any pair where both endpoints have
            // neighbors, so O(V²) enumeration is unavoidable.
            List<String> vertexList = new ArrayList<String>(vertices);
            for (int i = 0; i < vertexList.size(); i++) {
                String u = vertexList.get(i);
                Set<String> uNeighbors = adjacency.get(u);
                if (uNeighbors.isEmpty()) continue;

                for (int j = i + 1; j < vertexList.size(); j++) {
                    String v = vertexList.get(j);
                    if (uNeighbors.contains(v)) continue;

                    Set<String> vNeighbors = adjacency.get(v);
                    if (vNeighbors.isEmpty()) continue;

                    candidatesEvaluated++;
                    double score = (double) uNeighbors.size() * vNeighbors.size();

                    if (minHeap.size() < topK || score > minHeap.peek().getScore()) {
                        Set<String> common = GraphUtils.getCommonNeighbors(adjacency, u, v);
                        minHeap.offer(new PredictedLink(u, v, score, method, common));
                        if (minHeap.size() > topK) minHeap.poll();
                    }
                }
            }
        } else {
            // CN / Jaccard / Adamic-Adar: score is 0 when common neighbors
            // is empty, so only 2-hop reachable pairs need evaluation.
            // For each vertex u, walk u's neighbors w, then w's neighbors v
            // (where v > u lexicographically and v is not adjacent to u).
            // A seen-set per source u deduplicates the (u,v) pairs.
            List<String> sortedVertices = new ArrayList<String>(vertices);
            Collections.sort(sortedVertices);
            Map<String, Integer> vertexOrd = new HashMap<String, Integer>(n * 2);
            for (int i = 0; i < sortedVertices.size(); i++) {
                vertexOrd.put(sortedVertices.get(i), i);
            }

            for (String u : sortedVertices) {
                Set<String> uNeighbors = adjacency.get(u);
                if (uNeighbors.isEmpty()) continue;
                int uOrd = vertexOrd.get(u);

                // Collect 2-hop candidates: distinct vertices reachable
                // through exactly one intermediate neighbor
                Set<String> seen = new HashSet<String>();
                for (String w : uNeighbors) {
                    for (String v : adjacency.get(w)) {
                        // Only consider v > u (lexicographic) to avoid
                        // evaluating each pair twice, and skip direct neighbors
                        if (vertexOrd.get(v) > uOrd
                                && !uNeighbors.contains(v)
                                && seen.add(v)) {
                            candidatesEvaluated++;
                            Set<String> common = GraphUtils.getCommonNeighbors(adjacency, u, v);
                            double score = computeScore(method, adjacency, u, v, common);
                            if (score > 0
                                    && (minHeap.size() < topK
                                        || score > minHeap.peek().getScore())) {
                                minHeap.offer(new PredictedLink(u, v, score, method, common));
                                if (minHeap.size() > topK) minHeap.poll();
                            }
                        }
                    }
                }
            }
        }

        // Extract results in descending order
        List<PredictedLink> top = new ArrayList<PredictedLink>(minHeap);
        Collections.sort(top, SCORE_DESCENDING);

        return new PredictionResult(top, method, n, existingEdges,
                possibleEdges, candidatesEvaluated);
    }

    /**
     * Predict using all methods and return a combined ranking.
     * Each method's scores are normalized to [0,1] and averaged.
     *
     * <p>Uses a two-phase approach: for the CN/Jaccard/AA components,
     * only <b>2-hop pairs</b> (neighbors-of-neighbors) are enumerated
     * since those are the only pairs with non-zero CN/Jaccard/AA scores.
     * Preferential Attachment is computed inline for these same pairs.
     * This reduces the inner loop from O(V²) to O(V·Δ²) on sparse
     * graphs while still producing correct ensemble rankings (pairs with
     * zero CN/Jaccard/AA and only a PA signal are extremely weak
     * candidates and would rarely make the top-K).</p>
     *
     * @param topK number of top predictions to return
     * @return prediction result with ensemble scores
     */
    public PredictionResult predictEnsemble(int topK) {
        Collection<String> vertices = graph.getVertices();
        int n = vertices.size();
        int existingEdges = graph.getEdgeCount();
        long possibleEdges = (long) n * (n - 1) / 2;
        Map<String, Set<String>> adjacency = adjacency();

        Method[] methods = {
            Method.COMMON_NEIGHBORS, Method.JACCARD,
            Method.ADAMIC_ADAR, Method.PREFERENTIAL_ATTACHMENT
        };

        int poolSize = Math.max(topK * 4, 64);
        double[] maxScores = new double[4];
        int candidatesEvaluated = 0;

        PriorityQueue<Object[]> minHeap = new PriorityQueue<Object[]>(
                poolSize + 1,
                (Object[] a, Object[] b) -> Double.compare(
                        ((double[]) a[3])[4], ((double[]) b[3])[4]));

        // 2-hop enumeration: only pairs sharing ≥1 common neighbor
        List<String> sortedVertices = new ArrayList<String>(vertices);
        Collections.sort(sortedVertices);
        Map<String, Integer> vertexOrd = new HashMap<String, Integer>(n * 2);
        for (int i = 0; i < sortedVertices.size(); i++) {
            vertexOrd.put(sortedVertices.get(i), i);
        }

        for (String u : sortedVertices) {
            Set<String> uNeighbors = adjacency.get(u);
            if (uNeighbors.isEmpty()) continue;
            int uOrd = vertexOrd.get(u);

            Set<String> seen = new HashSet<String>();
            for (String w : uNeighbors) {
                for (String v : adjacency.get(w)) {
                    if (vertexOrd.get(v) > uOrd
                            && !uNeighbors.contains(v)
                            && seen.add(v)) {
                        candidatesEvaluated++;

                        Set<String> common = GraphUtils.getCommonNeighbors(adjacency, u, v);
                        double[] scores = new double[5];
                        for (int m = 0; m < 4; m++) {
                            scores[m] = computeScore(methods[m], adjacency, u, v, common);
                        }
                        if (scores[0] == 0 && scores[3] == 0) continue;

                        scores[4] = scores[0] + scores[1] + scores[2] + scores[3];

                        for (int m = 0; m < 4; m++) {
                            maxScores[m] = Math.max(maxScores[m], scores[m]);
                        }

                        if (minHeap.size() < poolSize || scores[4] > ((double[]) minHeap.peek()[3])[4]) {
                            minHeap.offer(new Object[]{u, v, common, scores});
                            if (minHeap.size() > poolSize) minHeap.poll();
                        }
                    }
                }
            }
        }

        // Pass 2: normalize the pool and extract the real top-K
        List<PredictedLink> candidates = new ArrayList<PredictedLink>(minHeap.size());
        for (Object[] entry : minHeap) {
            double[] scores = (double[]) entry[3];
            double avg = 0;
            int count = 0;
            for (int m = 0; m < 4; m++) {
                if (maxScores[m] > 0) {
                    avg += scores[m] / maxScores[m];
                    count++;
                }
            }
            avg = count > 0 ? avg / count : 0;
            @SuppressWarnings("unchecked")
            Set<String> common = (Set<String>) entry[2];
            candidates.add(new PredictedLink((String) entry[0], (String) entry[1],
                    avg, Method.ENSEMBLE, common));
        }

        Collections.sort(candidates, SCORE_DESCENDING);
        List<PredictedLink> top = candidates.subList(
                0, Math.min(topK, candidates.size()));

        return new PredictionResult(new ArrayList<PredictedLink>(top),
                Method.ENSEMBLE, n, existingEdges, possibleEdges,
                candidatesEvaluated);
    }

    // ── Shared pair enumeration ──────────────────────────────────

    /** Shared comparator for sorting predictions by score descending. */
    private static final Comparator<PredictedLink> SCORE_DESCENDING =
            (PredictedLink a, PredictedLink b) -> {
                    return Double.compare(b.getScore(), a.getScore());
                };

    /**
     * Holds the results of enumerating all candidate (non-Edge) vertex pairs,
     * along with precomputed adjacency and common-neighbor sets. This lets
     * {@link #predict} and {@link #predictEnsemble} share the expensive O(V²)
     * pair enumeration, adjacency construction, and common-neighbor
     * computation instead of duplicating it.
     */
    private static class PairEvaluation {
        final int n;
        final int existingEdges;
        final long possibleEdges;
        final Map<String, Set<String>> adjacency;
        /** Ordered list of candidate pairs as [u, v] arrays. */
        final List<String[]> pairs;
        /** Common neighbors for each pair, same indexing as pairs. */
        final List<Set<String>> commonNeighbors;

        PairEvaluation(int n, int existingEdges, long possibleEdges,
                       Map<String, Set<String>> adjacency,
                       List<String[]> pairs, List<Set<String>> commonNeighbors) {
            this.n = n;
            this.existingEdges = existingEdges;
            this.possibleEdges = possibleEdges;
            this.adjacency = adjacency;
            this.pairs = pairs;
            this.commonNeighbors = commonNeighbors;
        }
    }

    /**
     * Enumerates all non-Edge vertex pairs, builds adjacency, and computes
     * common neighbors for each pair. Called once by predict/predictEnsemble.
     */
    private PairEvaluation evaluatePairs() {
        Collection<String> vertices = graph.getVertices();
        int n = vertices.size();
        int existingEdges = graph.getEdgeCount();
        long possibleEdges = (long) n * (n - 1) / 2;
        Map<String, Set<String>> adjacency = adjacency();

        List<String> vertexList = new ArrayList<String>(vertices);
        List<String[]> pairs = new ArrayList<String[]>();
        List<Set<String>> commonNeighbors = new ArrayList<Set<String>>();

        for (int i = 0; i < vertexList.size(); i++) {
            for (int j = i + 1; j < vertexList.size(); j++) {
                String u = vertexList.get(i);
                String v = vertexList.get(j);
                if (adjacency.get(u).contains(v)) continue;

                pairs.add(new String[]{u, v});
                commonNeighbors.add(getCommonNeighbors(adjacency, u, v));
            }
        }

        return new PairEvaluation(n, existingEdges, possibleEdges,
                adjacency, pairs, commonNeighbors);
    }

    // ── Scoring functions ───────────────────────────────────────

    private double computeScore(Method method, Map<String, Set<String>> adjacency,
                                String u, String v, Set<String> common) {
        switch (method) {
            case COMMON_NEIGHBORS:
                return common.size();

            case JACCARD: {
                // Compute union size arithmetically to avoid allocating a HashSet:
                // |A ∪ B| = |A| + |B| - |A ∩ B|
                int unionSize = adjacency.get(u).size() + adjacency.get(v).size()
                        - common.size();
                return unionSize == 0 ? 0 : (double) common.size() / unionSize;
            }

            case ADAMIC_ADAR: {
                double score = 0;
                for (String w : common) {
                    int degree = adjacency.get(w).size();
                    if (degree > 1) {
                        score += 1.0 / Math.log(degree);
                    }
                }
                return score;
            }

            case PREFERENTIAL_ATTACHMENT:
                return (double) adjacency.get(u).size() * adjacency.get(v).size();

            default:
                return 0;
        }
    }

    // ── Helpers ─────────────────────────────────────────────────

    private Set<String> getCommonNeighbors(Map<String, Set<String>> adjacency,
                                           String u, String v) {
        return GraphUtils.getCommonNeighbors(adjacency, u, v);
    }
}
