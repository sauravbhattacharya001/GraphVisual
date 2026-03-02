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
        PREFERENTIAL_ATTACHMENT
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
        Collection<String> vertices = graph.getVertices();
        int n = vertices.size();
        int existingEdges = graph.getEdgeCount();
        int possibleEdges = n * (n - 1) / 2;

        // Build adjacency sets for fast lookup
        Map<String, Set<String>> adjacency = buildAdjacency(vertices);

        // Evaluate all non-existing vertex pairs
        List<PredictedLink> candidates = new ArrayList<PredictedLink>();
        List<String> vertexList = new ArrayList<String>(vertices);
        int evaluated = 0;

        for (int i = 0; i < vertexList.size(); i++) {
            for (int j = i + 1; j < vertexList.size(); j++) {
                String u = vertexList.get(i);
                String v = vertexList.get(j);

                // Skip if edge already exists
                if (adjacency.get(u).contains(v)) continue;

                evaluated++;
                Set<String> common = getCommonNeighbors(adjacency, u, v);
                double score = computeScore(method, adjacency, u, v, common);

                if (score > 0) {
                    candidates.add(new PredictedLink(u, v, score, method, common));
                }
            }
        }

        // Sort by score descending
        Collections.sort(candidates, new Comparator<PredictedLink>() {
            @Override
            public int compare(PredictedLink a, PredictedLink b) {
                return Double.compare(b.getScore(), a.getScore());
            }
        });

        // Take top K
        List<PredictedLink> top = candidates.subList(
                0, Math.min(topK, candidates.size()));

        return new PredictionResult(new ArrayList<PredictedLink>(top),
                method, n, existingEdges, possibleEdges, evaluated);
    }

    /**
     * Predict using all methods and return a combined ranking.
     * Each method's scores are normalized to [0,1] and averaged.
     *
     * @param topK number of top predictions to return
     * @return prediction result with ensemble scores
     */
    public PredictionResult predictEnsemble(int topK) {
        Collection<String> vertices = graph.getVertices();
        int n = vertices.size();
        int existingEdges = graph.getEdgeCount();
        int possibleEdges = n * (n - 1) / 2;

        Map<String, Set<String>> adjacency = buildAdjacency(vertices);
        List<String> vertexList = new ArrayList<String>(vertices);

        // Compute scores for all methods
        Map<String, double[]> pairScores = new LinkedHashMap<String, double[]>();
        Map<String, Set<String>> pairCommon = new HashMap<String, Set<String>>();
        Map<String, String[]> pairVertices = new HashMap<String, String[]>();
        int evaluated = 0;

        for (int i = 0; i < vertexList.size(); i++) {
            for (int j = i + 1; j < vertexList.size(); j++) {
                String u = vertexList.get(i);
                String v = vertexList.get(j);
                if (adjacency.get(u).contains(v)) continue;

                evaluated++;
                String key = u + "|" + v;
                Set<String> common = getCommonNeighbors(adjacency, u, v);

                double[] scores = new double[4];
                scores[0] = computeScore(Method.COMMON_NEIGHBORS, adjacency, u, v, common);
                scores[1] = computeScore(Method.JACCARD, adjacency, u, v, common);
                scores[2] = computeScore(Method.ADAMIC_ADAR, adjacency, u, v, common);
                scores[3] = computeScore(Method.PREFERENTIAL_ATTACHMENT, adjacency, u, v, common);

                if (scores[0] > 0 || scores[3] > 0) {
                    pairScores.put(key, scores);
                    pairCommon.put(key, common);
                    pairVertices.put(key, new String[]{u, v});
                }
            }
        }

        // Normalize each method's scores to [0,1]
        double[] maxScores = new double[4];
        for (double[] scores : pairScores.values()) {
            for (int m = 0; m < 4; m++) {
                maxScores[m] = Math.max(maxScores[m], scores[m]);
            }
        }

        // Average normalized scores
        List<PredictedLink> candidates = new ArrayList<PredictedLink>();
        for (Map.Entry<String, double[]> entry : pairScores.entrySet()) {
            double[] scores = entry.getValue();
            double avg = 0;
            int count = 0;
            for (int m = 0; m < 4; m++) {
                if (maxScores[m] > 0) {
                    avg += scores[m] / maxScores[m];
                    count++;
                }
            }
            avg = count > 0 ? avg / count : 0;

            String[] verts = pairVertices.get(entry.getKey());
            candidates.add(new PredictedLink(verts[0], verts[1], avg,
                    Method.COMMON_NEIGHBORS, pairCommon.get(entry.getKey())));
        }

        Collections.sort(candidates, new Comparator<PredictedLink>() {
            @Override
            public int compare(PredictedLink a, PredictedLink b) {
                return Double.compare(b.getScore(), a.getScore());
            }
        });

        List<PredictedLink> top = candidates.subList(
                0, Math.min(topK, candidates.size()));

        return new PredictionResult(new ArrayList<PredictedLink>(top),
                Method.COMMON_NEIGHBORS, n, existingEdges, possibleEdges, evaluated);
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
