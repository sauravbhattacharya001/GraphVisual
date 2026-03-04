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

    private final Graph<String, edge> graph;

    /**
     * Create a new link prediction analyzer.
     *
     * @param graph the JUNG graph to analyze (must not be null)
     * @throws IllegalArgumentException if graph is null
     */
    public LinkPredictionAnalyzer(Graph<String, edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
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
        private final int possibleEdges;
        private final int candidatesEvaluated;

        public PredictionResult(List<PredictedLink> predictions, Method method,
                                int totalVertices, int existingEdges,
                                int possibleEdges, int candidatesEvaluated) {
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
        public int getPossibleEdges() { return possibleEdges; }
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
     * @param method scoring method to use
     * @param topK   number of top predictions to return
     * @return prediction result with ranked candidate edges
     */
    public PredictionResult predict(Method method, int topK) {
        PairEvaluation eval = evaluatePairs();
        List<PredictedLink> candidates = new ArrayList<PredictedLink>();

        for (int idx = 0; idx < eval.pairs.size(); idx++) {
            String[] pair = eval.pairs.get(idx);
            Set<String> common = eval.commonNeighbors.get(idx);
            double score = computeScore(method, eval.adjacency, pair[0], pair[1], common);

            if (score > 0) {
                candidates.add(new PredictedLink(pair[0], pair[1], score, method, common));
            }
        }

        Collections.sort(candidates, SCORE_DESCENDING);
        List<PredictedLink> top = candidates.subList(
                0, Math.min(topK, candidates.size()));

        return new PredictionResult(new ArrayList<PredictedLink>(top),
                method, eval.n, eval.existingEdges, eval.possibleEdges, eval.pairs.size());
    }

    /**
     * Predict using all methods and return a combined ranking.
     * Each method's scores are normalized to [0,1] and averaged.
     *
     * @param topK number of top predictions to return
     * @return prediction result with ensemble scores
     */
    public PredictionResult predictEnsemble(int topK) {
        PairEvaluation eval = evaluatePairs();
        Method[] methods = {
            Method.COMMON_NEIGHBORS, Method.JACCARD,
            Method.ADAMIC_ADAR, Method.PREFERENTIAL_ATTACHMENT
        };

        // Score every pair with all four methods
        double[][] allScores = new double[eval.pairs.size()][4];
        double[] maxScores = new double[4];

        for (int idx = 0; idx < eval.pairs.size(); idx++) {
            String[] pair = eval.pairs.get(idx);
            Set<String> common = eval.commonNeighbors.get(idx);
            for (int m = 0; m < 4; m++) {
                double s = computeScore(methods[m], eval.adjacency, pair[0], pair[1], common);
                allScores[idx][m] = s;
                maxScores[m] = Math.max(maxScores[m], s);
            }
        }

        // Normalize and average
        List<PredictedLink> candidates = new ArrayList<PredictedLink>();
        for (int idx = 0; idx < eval.pairs.size(); idx++) {
            // Skip pairs with no signal
            if (allScores[idx][0] == 0 && allScores[idx][3] == 0) continue;

            double avg = 0;
            int count = 0;
            for (int m = 0; m < 4; m++) {
                if (maxScores[m] > 0) {
                    avg += allScores[idx][m] / maxScores[m];
                    count++;
                }
            }
            avg = count > 0 ? avg / count : 0;

            String[] pair = eval.pairs.get(idx);
            candidates.add(new PredictedLink(pair[0], pair[1], avg,
                    Method.ENSEMBLE, eval.commonNeighbors.get(idx)));
        }

        Collections.sort(candidates, SCORE_DESCENDING);
        List<PredictedLink> top = candidates.subList(
                0, Math.min(topK, candidates.size()));

        return new PredictionResult(new ArrayList<PredictedLink>(top),
                Method.ENSEMBLE, eval.n, eval.existingEdges, eval.possibleEdges,
                eval.pairs.size());
    }

    // ── Shared pair enumeration ──────────────────────────────────

    /** Shared comparator for sorting predictions by score descending. */
    private static final Comparator<PredictedLink> SCORE_DESCENDING =
            new Comparator<PredictedLink>() {
                @Override
                public int compare(PredictedLink a, PredictedLink b) {
                    return Double.compare(b.getScore(), a.getScore());
                }
            };

    /**
     * Holds the results of enumerating all candidate (non-edge) vertex pairs,
     * along with precomputed adjacency and common-neighbor sets. This lets
     * {@link #predict} and {@link #predictEnsemble} share the expensive O(V²)
     * pair enumeration, adjacency construction, and common-neighbor
     * computation instead of duplicating it.
     */
    private static class PairEvaluation {
        final int n;
        final int existingEdges;
        final int possibleEdges;
        final Map<String, Set<String>> adjacency;
        /** Ordered list of candidate pairs as [u, v] arrays. */
        final List<String[]> pairs;
        /** Common neighbors for each pair, same indexing as pairs. */
        final List<Set<String>> commonNeighbors;

        PairEvaluation(int n, int existingEdges, int possibleEdges,
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
     * Enumerates all non-edge vertex pairs, builds adjacency, and computes
     * common neighbors for each pair. Called once by predict/predictEnsemble.
     */
    private PairEvaluation evaluatePairs() {
        Collection<String> vertices = graph.getVertices();
        int n = vertices.size();
        int existingEdges = graph.getEdgeCount();
        int possibleEdges = n * (n - 1) / 2;
        Map<String, Set<String>> adjacency = buildAdjacency(vertices);

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
                Set<String> union = new HashSet<String>(adjacency.get(u));
                union.addAll(adjacency.get(v));
                return union.isEmpty() ? 0 : (double) common.size() / union.size();
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

    private Map<String, Set<String>> buildAdjacency(Collection<String> vertices) {
        return GraphUtils.buildAdjacencyMap(graph);
    }

    private Set<String> getCommonNeighbors(Map<String, Set<String>> adjacency,
                                           String u, String v) {
        return GraphUtils.getCommonNeighbors(adjacency, u, v);
    }
}
