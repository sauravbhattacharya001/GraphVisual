package gvisual;

import edu.uci.ics.jung.graph.DirectedGraph;
import edu.uci.ics.jung.graph.Graph;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GraphKnowledgeExtractor — autonomous knowledge extraction and link prediction
 * engine that treats a graph as a knowledge structure, discovers implied/missing
 * relationships, and predicts new connections with confidence scoring.
 *
 * <h3>Six Analysis Engines:</h3>
 * <ol>
 *   <li><b>Link Prediction Engine</b> — predicts missing edges using 6 heuristics:
 *       Common Neighbors, Jaccard, Adamic-Adar, Preferential Attachment,
 *       Resource Allocation, truncated Katz index</li>
 *   <li><b>Transitivity Inference Engine</b> — finds triadic closure opportunities
 *       (A-B, B-C implies A-C likely)</li>
 *   <li><b>Reciprocity Analyzer</b> — in directed graphs, finds one-way relationships
 *       likely to become bidirectional</li>
 *   <li><b>Structural Hole Detector</b> — finds knowledge gaps where disconnected
 *       subsets share structural similarity</li>
 *   <li><b>Pattern-Based Reasoner</b> — identifies motifs (triangles, stars, chains)
 *       and infers connection rules</li>
 *   <li><b>Knowledge Completeness Scorer</b> — rates graph completeness 0-100</li>
 * </ol>
 *
 * <p>Usage:</p>
 * <pre>
 *   GraphKnowledgeExtractor extractor = new GraphKnowledgeExtractor(graph);
 *   GraphKnowledgeExtractor.ExtractorReport report = extractor.analyze();
 *   System.out.println(extractor.toText(report));
 *   String html = extractor.exportHtml(report);
 * </pre>
 *
 * @author zalenix
 */
public class GraphKnowledgeExtractor {

    // ── Inner Classes ────────────────────────────────────────────────

    /** A single link prediction with evidence. */
    public static class Prediction implements Comparable<Prediction> {
        private final String source;
        private final String target;
        private final double score;
        private final Map<String, Double> heuristicScores;
        private final String explanation;

        public Prediction(String source, String target, double score,
                          Map<String, Double> heuristicScores, String explanation) {
            this.source = source;
            this.target = target;
            this.score = score;
            this.heuristicScores = Collections.unmodifiableMap(new LinkedHashMap<>(heuristicScores));
            this.explanation = explanation;
        }

        public String getSource() { return source; }
        public String getTarget() { return target; }
        public double getScore() { return score; }
        public Map<String, Double> getHeuristicScores() { return heuristicScores; }
        public String getExplanation() { return explanation; }
        public int getAgreeingHeuristics() {
            return (int) heuristicScores.values().stream().filter(v -> v > 0).count();
        }

        @Override
        public int compareTo(Prediction o) {
            return Double.compare(o.score, this.score); // descending
        }
    }

    /** Triadic closure opportunity. */
    public static class TriadicClosure {
        private final String nodeA;
        private final String nodeC;
        private final String bridge; // shared neighbor B
        private final double likelihood;

        public TriadicClosure(String nodeA, String nodeC, String bridge, double likelihood) {
            this.nodeA = nodeA;
            this.nodeC = nodeC;
            this.bridge = bridge;
            this.likelihood = likelihood;
        }

        public String getNodeA() { return nodeA; }
        public String getNodeC() { return nodeC; }
        public String getBridge() { return bridge; }
        public double getLikelihood() { return likelihood; }
    }

    /** A non-reciprocated edge likely to become bidirectional. */
    public static class ReciprocityCandidate {
        private final String from;
        private final String to;
        private final double probability;
        private final String evidence;

        public ReciprocityCandidate(String from, String to, double probability, String evidence) {
            this.from = from;
            this.to = to;
            this.probability = probability;
            this.evidence = evidence;
        }

        public String getFrom() { return from; }
        public String getTo() { return to; }
        public double getProbability() { return probability; }
        public String getEvidence() { return evidence; }
    }

    /** A structural hole between graph regions. */
    public static class StructuralHole {
        private final Set<String> groupA;
        private final Set<String> groupB;
        private final double similarity;
        private final String bridgeCandidate;

        public StructuralHole(Set<String> groupA, Set<String> groupB,
                              double similarity, String bridgeCandidate) {
            this.groupA = Collections.unmodifiableSet(new LinkedHashSet<>(groupA));
            this.groupB = Collections.unmodifiableSet(new LinkedHashSet<>(groupB));
            this.similarity = similarity;
            this.bridgeCandidate = bridgeCandidate;
        }

        public Set<String> getGroupA() { return groupA; }
        public Set<String> getGroupB() { return groupB; }
        public double getSimilarity() { return similarity; }
        public String getBridgeCandidate() { return bridgeCandidate; }
    }

    /** Structural motif pattern. */
    public static class MotifPattern {
        private final String type; // "triangle", "star", "chain"
        private final int count;
        private final List<String> impliedConnections;

        public MotifPattern(String type, int count, List<String> impliedConnections) {
            this.type = type;
            this.count = count;
            this.impliedConnections = Collections.unmodifiableList(new ArrayList<>(impliedConnections));
        }

        public String getType() { return type; }
        public int getCount() { return count; }
        public List<String> getImpliedConnections() { return impliedConnections; }
    }

    /** Complete extraction report. */
    public static class ExtractorReport {
        private final List<Prediction> predictions;
        private final List<TriadicClosure> triadicClosures;
        private final List<ReciprocityCandidate> reciprocityCandidates;
        private final List<StructuralHole> structuralHoles;
        private final List<MotifPattern> motifPatterns;
        private final double completenessScore;
        private final Map<String, Double> completenessBreakdown;
        private final int totalNodes;
        private final int totalEdges;

        public ExtractorReport(List<Prediction> predictions,
                               List<TriadicClosure> triadicClosures,
                               List<ReciprocityCandidate> reciprocityCandidates,
                               List<StructuralHole> structuralHoles,
                               List<MotifPattern> motifPatterns,
                               double completenessScore,
                               Map<String, Double> completenessBreakdown,
                               int totalNodes, int totalEdges) {
            this.predictions = Collections.unmodifiableList(new ArrayList<>(predictions));
            this.triadicClosures = Collections.unmodifiableList(new ArrayList<>(triadicClosures));
            this.reciprocityCandidates = Collections.unmodifiableList(new ArrayList<>(reciprocityCandidates));
            this.structuralHoles = Collections.unmodifiableList(new ArrayList<>(structuralHoles));
            this.motifPatterns = Collections.unmodifiableList(new ArrayList<>(motifPatterns));
            this.completenessScore = completenessScore;
            this.completenessBreakdown = Collections.unmodifiableMap(new LinkedHashMap<>(completenessBreakdown));
            this.totalNodes = totalNodes;
            this.totalEdges = totalEdges;
        }

        public List<Prediction> getPredictions() { return predictions; }
        public List<TriadicClosure> getTriadicClosures() { return triadicClosures; }
        public List<ReciprocityCandidate> getReciprocityCandidates() { return reciprocityCandidates; }
        public List<StructuralHole> getStructuralHoles() { return structuralHoles; }
        public List<MotifPattern> getMotifPatterns() { return motifPatterns; }
        public double getCompletenessScore() { return completenessScore; }
        public Map<String, Double> getCompletenessBreakdown() { return completenessBreakdown; }
        public int getTotalNodes() { return totalNodes; }
        public int getTotalEdges() { return totalEdges; }
    }

    // ── Fields ───────────────────────────────────────────────────────

    private final Graph<String, Edge> graph;
    private final boolean directed;
    private final Map<String, Set<String>> neighborMap;
    private int topK = 20;

    // ── Constructor ──────────────────────────────────────────────────

    public GraphKnowledgeExtractor(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.directed = graph instanceof DirectedGraph;
        this.neighborMap = buildNeighborMap();
    }

    public void setTopK(int topK) {
        if (topK < 1) throw new IllegalArgumentException("topK must be >= 1");
        this.topK = topK;
    }

    public int getTopK() { return topK; }

    // ── Main Analysis ────────────────────────────────────────────────

    /**
     * Runs all six extraction engines and produces a complete report.
     */
    public ExtractorReport analyze() {
        List<Prediction> predictions = runLinkPrediction();
        List<TriadicClosure> closures = runTransitivityInference();
        List<ReciprocityCandidate> reciprocity = runReciprocityAnalysis();
        List<StructuralHole> holes = runStructuralHoleDetection();
        List<MotifPattern> motifs = runPatternReasoner();
        Map<String, Double> breakdown = new LinkedHashMap<>();
        double completeness = computeCompleteness(breakdown);

        return new ExtractorReport(predictions, closures, reciprocity,
                holes, motifs, completeness, breakdown,
                graph.getVertexCount(), graph.getEdgeCount());
    }

    // ── Engine 1: Link Prediction ────────────────────────────────────

    private List<Prediction> runLinkPrediction() {
        List<String> vertices = new ArrayList<>(graph.getVertices());
        if (vertices.size() < 2) return Collections.emptyList();

        // For large graphs, sample node pairs
        int n = vertices.size();
        long maxPairs = (long) n * (n - 1) / 2;
        boolean sample = maxPairs > 10000;

        List<Prediction> results = new ArrayList<>();
        Set<String> existingEdges = buildEdgeSet();
        Random rng = new Random(42);

        if (sample) {
            // Sample up to 5000 non-edge pairs
            Set<String> checked = new HashSet<>();
            int attempts = 0;
            while (results.size() < topK * 5 && attempts < 50000) {
                attempts++;
                String u = vertices.get(rng.nextInt(n));
                String v = vertices.get(rng.nextInt(n));
                if (u.equals(v)) continue;
                String key = pairKey(u, v);
                if (checked.contains(key) || existingEdges.contains(key)) continue;
                checked.add(key);
                Prediction p = scorePair(u, v);
                if (p.getScore() > 0) results.add(p);
            }
        } else {
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    String u = vertices.get(i);
                    String v = vertices.get(j);
                    if (existingEdges.contains(pairKey(u, v))) continue;
                    Prediction p = scorePair(u, v);
                    if (p.getScore() > 0) results.add(p);
                }
            }
        }

        Collections.sort(results);
        return results.size() > topK ? results.subList(0, topK) : results;
    }

    private Prediction scorePair(String u, String v) {
        Map<String, Double> scores = new LinkedHashMap<>();
        Set<String> uNeighbors = neighborMap.getOrDefault(u, Collections.emptySet());
        Set<String> vNeighbors = neighborMap.getOrDefault(v, Collections.emptySet());

        // Common Neighbors
        Set<String> common = new HashSet<>(uNeighbors);
        common.retainAll(vNeighbors);
        double cn = common.size();
        scores.put("CommonNeighbors", cn);

        // Jaccard
        Set<String> union = new HashSet<>(uNeighbors);
        union.addAll(vNeighbors);
        double jaccard = union.isEmpty() ? 0 : cn / union.size();
        scores.put("Jaccard", jaccard);

        // Adamic-Adar
        double adamicAdar = 0;
        for (String w : common) {
            int deg = neighborMap.getOrDefault(w, Collections.emptySet()).size();
            if (deg > 1) adamicAdar += 1.0 / Math.log(deg);
        }
        scores.put("AdamicAdar", adamicAdar);

        // Preferential Attachment
        double pa = (double) uNeighbors.size() * vNeighbors.size();
        scores.put("PreferentialAttachment", pa);

        // Resource Allocation
        double ra = 0;
        for (String w : common) {
            int deg = neighborMap.getOrDefault(w, Collections.emptySet()).size();
            if (deg > 0) ra += 1.0 / deg;
        }
        scores.put("ResourceAllocation", ra);

        // Katz (truncated to paths of length 2 and 3)
        double beta = 0.01;
        double katz = beta * cn; // length-2 paths
        // length-3 paths
        double l3 = 0;
        for (String w : uNeighbors) {
            if (w.equals(v)) continue;
            Set<String> wNeighbors = neighborMap.getOrDefault(w, Collections.emptySet());
            for (String x : wNeighbors) {
                if (x.equals(u) || x.equals(w)) continue;
                if (vNeighbors.contains(x)) l3++;
            }
        }
        katz += beta * beta * l3;
        scores.put("Katz", katz);

        // Composite score: normalize and average
        double composite = computeCompositeScore(scores);
        int agreeing = (int) scores.values().stream().filter(s -> s > 0).count();
        String explanation = String.format("%d/6 heuristics agree, CN=%d, Jaccard=%.3f",
                agreeing, (int) cn, jaccard);

        return new Prediction(u, v, composite, scores, explanation);
    }

    private double computeCompositeScore(Map<String, Double> scores) {
        // Normalize each to [0,1] relative to theoretical max, then average
        double cn = Math.min(scores.get("CommonNeighbors") / Math.max(1, graph.getVertexCount()), 1.0);
        double jac = scores.get("Jaccard");
        double aa = Math.min(scores.get("AdamicAdar") / 10.0, 1.0);
        double pa = Math.min(scores.get("PreferentialAttachment") /
                Math.max(1, Math.pow(graph.getVertexCount(), 2) * 0.01), 1.0);
        double ra = Math.min(scores.get("ResourceAllocation") / 5.0, 1.0);
        double katz = Math.min(scores.get("Katz") / 0.5, 1.0);
        return (cn + jac + aa + pa + ra + katz) / 6.0;
    }

    // ── Engine 2: Transitivity Inference ─────────────────────────────

    private List<TriadicClosure> runTransitivityInference() {
        List<TriadicClosure> closures = new ArrayList<>();
        Set<String> existingEdges = buildEdgeSet();
        Set<String> seen = new HashSet<>();

        for (String b : graph.getVertices()) {
            Set<String> bNeighbors = neighborMap.getOrDefault(b, Collections.emptySet());
            List<String> nbrList = new ArrayList<>(bNeighbors);
            for (int i = 0; i < nbrList.size(); i++) {
                for (int j = i + 1; j < nbrList.size(); j++) {
                    String a = nbrList.get(i);
                    String c = nbrList.get(j);
                    String key = pairKey(a, c);
                    if (existingEdges.contains(key) || seen.contains(key)) continue;
                    seen.add(key);
                    // Likelihood based on shared neighbors of a and c
                    Set<String> aNeighbors = neighborMap.getOrDefault(a, Collections.emptySet());
                    Set<String> cNeighbors = neighborMap.getOrDefault(c, Collections.emptySet());
                    Set<String> shared = new HashSet<>(aNeighbors);
                    shared.retainAll(cNeighbors);
                    double likelihood = Math.min(1.0, shared.size() / 3.0);
                    closures.add(new TriadicClosure(a, c, b, likelihood));
                }
            }
        }

        closures.sort((x, y) -> Double.compare(y.getLikelihood(), x.getLikelihood()));
        return closures.size() > topK ? closures.subList(0, topK) : closures;
    }

    // ── Engine 3: Reciprocity Analysis ───────────────────────────────

    private List<ReciprocityCandidate> runReciprocityAnalysis() {
        if (!directed) return Collections.emptyList();

        DirectedGraph<String, Edge> dg = (DirectedGraph<String, Edge>) graph;
        List<ReciprocityCandidate> candidates = new ArrayList<>();

        // Calculate global reciprocity rate
        int reciprocated = 0;
        int totalDirected = 0;
        for (Edge e : dg.getEdges()) {
            totalDirected++;
            String src = null, dst = null;
            // Get source and dest
            src = dg.getSource(e);
            dst = dg.getDest(e);
            if (src != null && dst != null && dg.findEdge(dst, src) != null) {
                reciprocated++;
            }
        }
        double globalRate = totalDirected > 0 ? (double) reciprocated / totalDirected : 0;

        // Find non-reciprocated edges
        for (Edge e : dg.getEdges()) {
            String src = dg.getSource(e);
            String dst = dg.getDest(e);
            if (src == null || dst == null) continue;
            if (dg.findEdge(dst, src) != null) continue; // already reciprocated

            // Score probability based on: mutual neighbors, degree similarity
            Set<String> srcN = neighborMap.getOrDefault(src, Collections.emptySet());
            Set<String> dstN = neighborMap.getOrDefault(dst, Collections.emptySet());
            Set<String> mutual = new HashSet<>(srcN);
            mutual.retainAll(dstN);
            double mutualFactor = Math.min(1.0, mutual.size() / 3.0);
            double degreeSim = 1.0 - Math.abs(srcN.size() - dstN.size()) /
                    (double) Math.max(1, Math.max(srcN.size(), dstN.size()));
            double prob = (globalRate + mutualFactor + degreeSim) / 3.0;

            String evidence = String.format("globalReciprocity=%.2f, mutualNeighbors=%d, degreeSim=%.2f",
                    globalRate, mutual.size(), degreeSim);
            candidates.add(new ReciprocityCandidate(dst, src, prob, evidence));
        }

        candidates.sort((a, b) -> Double.compare(b.getProbability(), a.getProbability()));
        return candidates.size() > topK ? candidates.subList(0, topK) : candidates;
    }

    // ── Engine 4: Structural Hole Detection ──────────────────────────

    private List<StructuralHole> runStructuralHoleDetection() {
        // Find connected components, then compare disconnected ones
        List<Set<String>> components = findComponents();
        if (components.size() < 2) {
            // For connected graphs, find loosely connected regions using BFS layering
            return findInternalHoles();
        }

        List<StructuralHole> holes = new ArrayList<>();
        for (int i = 0; i < components.size() && i < 10; i++) {
            for (int j = i + 1; j < components.size() && j < 10; j++) {
                Set<String> a = components.get(i);
                Set<String> b = components.get(j);
                double sim = computeGroupSimilarity(a, b);
                if (sim > 0.1) {
                    String bridge = findBestBridge(a, b);
                    holes.add(new StructuralHole(a, b, sim, bridge));
                }
            }
        }

        holes.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
        return holes.size() > topK ? holes.subList(0, topK) : holes;
    }

    private List<StructuralHole> findInternalHoles() {
        // Find node pairs with high structural similarity but no direct edge
        List<String> vertices = new ArrayList<>(graph.getVertices());
        List<StructuralHole> holes = new ArrayList<>();
        Set<String> existingEdges = buildEdgeSet();

        for (int i = 0; i < Math.min(vertices.size(), 50); i++) {
            for (int j = i + 1; j < Math.min(vertices.size(), 50); j++) {
                String u = vertices.get(i);
                String v = vertices.get(j);
                if (existingEdges.contains(pairKey(u, v))) continue;
                Set<String> uN = neighborMap.getOrDefault(u, Collections.emptySet());
                Set<String> vN = neighborMap.getOrDefault(v, Collections.emptySet());
                if (uN.isEmpty() || vN.isEmpty()) continue;

                // Structural equivalence: how similar are their neighbor sets?
                Set<String> intersection = new HashSet<>(uN);
                intersection.retainAll(vN);
                Set<String> union = new HashSet<>(uN);
                union.addAll(vN);
                double sim = (double) intersection.size() / union.size();
                if (sim > 0.3) {
                    Set<String> groupA = new LinkedHashSet<>();
                    groupA.add(u);
                    groupA.addAll(uN);
                    Set<String> groupB = new LinkedHashSet<>();
                    groupB.add(v);
                    groupB.addAll(vN);
                    holes.add(new StructuralHole(groupA, groupB, sim, u + "-" + v));
                }
            }
        }

        holes.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
        return holes.size() > 5 ? holes.subList(0, 5) : holes;
    }

    private double computeGroupSimilarity(Set<String> a, Set<String> b) {
        // Compare degree distributions
        double avgDegA = a.stream().mapToInt(n -> neighborMap.getOrDefault(n, Collections.emptySet()).size()).average().orElse(0);
        double avgDegB = b.stream().mapToInt(n -> neighborMap.getOrDefault(n, Collections.emptySet()).size()).average().orElse(0);
        double degreeSim = 1.0 - Math.abs(avgDegA - avgDegB) / Math.max(1, Math.max(avgDegA, avgDegB));

        // Compare sizes
        double sizeSim = (double) Math.min(a.size(), b.size()) / Math.max(1, Math.max(a.size(), b.size()));

        return (degreeSim + sizeSim) / 2.0;
    }

    private String findBestBridge(Set<String> a, Set<String> b) {
        // Highest degree node in smaller set bridges to other
        Set<String> smaller = a.size() <= b.size() ? a : b;
        return smaller.stream()
                .max(Comparator.comparingInt(n -> neighborMap.getOrDefault(n, Collections.emptySet()).size()))
                .orElse(smaller.iterator().next());
    }

    // ── Engine 5: Pattern-Based Reasoner ─────────────────────────────

    private List<MotifPattern> runPatternReasoner() {
        List<MotifPattern> patterns = new ArrayList<>();

        // Count triangles
        int triangleCount = countTriangles();
        if (triangleCount > 0) {
            List<String> implied = findTriangleImpliedConnections();
            patterns.add(new MotifPattern("triangle", triangleCount, implied));
        }

        // Count stars (nodes with degree >= 4)
        List<String> starImplied = new ArrayList<>();
        int starCount = 0;
        for (String v : graph.getVertices()) {
            Set<String> nbrs = neighborMap.getOrDefault(v, Collections.emptySet());
            if (nbrs.size() >= 4) {
                starCount++;
                // Star pattern implies leaves might connect to each other
                List<String> leaves = new ArrayList<>(nbrs);
                for (int i = 0; i < Math.min(leaves.size(), 3); i++) {
                    for (int j = i + 1; j < Math.min(leaves.size(), 3); j++) {
                        if (!buildEdgeSet().contains(pairKey(leaves.get(i), leaves.get(j)))) {
                            starImplied.add(leaves.get(i) + " -> " + leaves.get(j));
                        }
                    }
                }
            }
        }
        if (starCount > 0) {
            patterns.add(new MotifPattern("star", starCount,
                    starImplied.size() > 10 ? starImplied.subList(0, 10) : starImplied));
        }

        // Count chains (paths of length >= 3 without branching)
        int chainCount = 0;
        List<String> chainImplied = new ArrayList<>();
        for (String v : graph.getVertices()) {
            Set<String> nbrs = neighborMap.getOrDefault(v, Collections.emptySet());
            if (nbrs.size() == 2) {
                chainCount++;
                // Chain endpoints might benefit from shortcut
                List<String> ends = new ArrayList<>(nbrs);
                String a = ends.get(0);
                String b = ends.get(1);
                if (!buildEdgeSet().contains(pairKey(a, b))) {
                    chainImplied.add(a + " -> " + b + " (via " + v + ")");
                }
            }
        }
        if (chainCount > 0) {
            patterns.add(new MotifPattern("chain", chainCount,
                    chainImplied.size() > 10 ? chainImplied.subList(0, 10) : chainImplied));
        }

        return patterns;
    }

    private int countTriangles() {
        int count = 0;
        List<String> vertices = new ArrayList<>(graph.getVertices());
        for (String v : vertices) {
            Set<String> vNbrs = neighborMap.getOrDefault(v, Collections.emptySet());
            for (String u : vNbrs) {
                if (u.compareTo(v) <= 0) continue;
                Set<String> uNbrs = neighborMap.getOrDefault(u, Collections.emptySet());
                for (String w : uNbrs) {
                    if (w.compareTo(u) <= 0) continue;
                    if (vNbrs.contains(w)) count++;
                }
            }
        }
        return count;
    }

    private List<String> findTriangleImpliedConnections() {
        // Near-triangles: two edges of three present
        Set<String> existingEdges = buildEdgeSet();
        List<String> implied = new ArrayList<>();
        List<String> vertices = new ArrayList<>(graph.getVertices());

        for (String b : vertices) {
            Set<String> bNbrs = neighborMap.getOrDefault(b, Collections.emptySet());
            List<String> nbrList = new ArrayList<>(bNbrs);
            for (int i = 0; i < nbrList.size() && implied.size() < 10; i++) {
                for (int j = i + 1; j < nbrList.size() && implied.size() < 10; j++) {
                    String a = nbrList.get(i);
                    String c = nbrList.get(j);
                    if (!existingEdges.contains(pairKey(a, c))) {
                        implied.add(a + " -> " + c + " (close triangle via " + b + ")");
                    }
                }
            }
            if (implied.size() >= 10) break;
        }
        return implied;
    }

    // ── Engine 6: Knowledge Completeness ─────────────────────────────

    private double computeCompleteness(Map<String, Double> breakdown) {
        int n = graph.getVertexCount();
        int m = graph.getEdgeCount();
        if (n < 2) {
            breakdown.put("density", n == 0 ? 0.0 : 100.0);
            breakdown.put("clustering", 100.0);
            breakdown.put("closureRate", 100.0);
            breakdown.put("connectivity", n == 0 ? 0.0 : 100.0);
            return n == 0 ? 0.0 : 100.0;
        }

        // 1. Density relative to expected (not full density, but expected from degree dist)
        double maxEdges = (double) n * (n - 1) / (directed ? 1 : 2);
        double density = m / maxEdges;
        // Score: use log scale since most real graphs are sparse
        double densityScore = Math.min(100, density * 500); // generous scaling
        breakdown.put("density", densityScore);

        // 2. Clustering coefficient (higher = more complete local structure)
        double avgClustering = computeAverageClusteringCoefficient();
        double clusteringScore = avgClustering * 100;
        breakdown.put("clustering", clusteringScore);

        // 3. Triadic closure rate
        int openTriads = 0;
        int closedTriads = 0;
        Set<String> edges = buildEdgeSet();
        for (String v : graph.getVertices()) {
            Set<String> nbrs = neighborMap.getOrDefault(v, Collections.emptySet());
            List<String> nbrList = new ArrayList<>(nbrs);
            for (int i = 0; i < nbrList.size(); i++) {
                for (int j = i + 1; j < nbrList.size(); j++) {
                    if (edges.contains(pairKey(nbrList.get(i), nbrList.get(j)))) {
                        closedTriads++;
                    } else {
                        openTriads++;
                    }
                }
            }
        }
        double closureRate = (openTriads + closedTriads) == 0 ? 100 :
                (double) closedTriads / (openTriads + closedTriads) * 100;
        breakdown.put("closureRate", closureRate);

        // 4. Connectivity (fraction of nodes reachable from any node)
        List<Set<String>> comps = findComponents();
        double largestComp = comps.stream().mapToInt(Set::size).max().orElse(0);
        double connectivityScore = (largestComp / n) * 100;
        breakdown.put("connectivity", connectivityScore);

        // Weighted average
        return densityScore * 0.2 + clusteringScore * 0.3 + closureRate * 0.3 + connectivityScore * 0.2;
    }

    private double computeAverageClusteringCoefficient() {
        double sum = 0;
        int count = 0;
        for (String v : graph.getVertices()) {
            Set<String> nbrs = neighborMap.getOrDefault(v, Collections.emptySet());
            int k = nbrs.size();
            if (k < 2) continue;
            int links = 0;
            List<String> nbrList = new ArrayList<>(nbrs);
            Set<String> edges = buildEdgeSet();
            for (int i = 0; i < nbrList.size(); i++) {
                for (int j = i + 1; j < nbrList.size(); j++) {
                    if (edges.contains(pairKey(nbrList.get(i), nbrList.get(j)))) links++;
                }
            }
            double maxLinks = (double) k * (k - 1) / 2;
            sum += links / maxLinks;
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    // ── Utilities ────────────────────────────────────────────────────

    private Map<String, Set<String>> buildNeighborMap() {
        Map<String, Set<String>> map = new HashMap<>();
        for (String v : graph.getVertices()) {
            Collection<String> nbrs = graph.getNeighbors(v);
            map.put(v, nbrs != null ? new HashSet<>(nbrs) : new HashSet<>());
        }
        return map;
    }

    private Set<String> buildEdgeSet() {
        Set<String> set = new HashSet<>();
        for (Edge e : graph.getEdges()) {
            Collection<String> endpoints = graph.getEndpoints(e);
            if (endpoints != null && endpoints.size() == 2) {
                Iterator<String> it = endpoints.iterator();
                String u = it.next();
                String v = it.next();
                set.add(pairKey(u, v));
                if (!directed) set.add(pairKey(v, u));
            }
        }
        return set;
    }

    private String pairKey(String u, String v) {
        if (!directed && u.compareTo(v) > 0) {
            return v + "\0" + u;
        }
        return u + "\0" + v;
    }

    private List<Set<String>> findComponents() {
        Set<String> visited = new HashSet<>();
        List<Set<String>> components = new ArrayList<>();
        for (String v : graph.getVertices()) {
            if (visited.contains(v)) continue;
            Set<String> comp = new LinkedHashSet<>();
            Queue<String> queue = new LinkedList<>();
            queue.add(v);
            visited.add(v);
            while (!queue.isEmpty()) {
                String cur = queue.poll();
                comp.add(cur);
                for (String nbr : neighborMap.getOrDefault(cur, Collections.emptySet())) {
                    if (!visited.contains(nbr)) {
                        visited.add(nbr);
                        queue.add(nbr);
                    }
                }
            }
            components.add(comp);
        }
        components.sort((a, b) -> Integer.compare(b.size(), a.size()));
        return components;
    }

    // ── Text Export ──────────────────────────────────────────────────

    public String toText(ExtractorReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("===================================================================\n");
        sb.append("  GRAPH KNOWLEDGE EXTRACTOR - Autonomous Analysis Report\n");
        sb.append("===================================================================\n\n");
        sb.append(String.format("  Graph: %d nodes, %d edges (%s)\n",
                report.getTotalNodes(), report.getTotalEdges(),
                directed ? "directed" : "undirected"));
        sb.append(String.format("  Knowledge Completeness: %.1f / 100\n\n", report.getCompletenessScore()));

        // Completeness breakdown
        sb.append("-- Completeness Breakdown ------------------------------------------\n");
        for (Map.Entry<String, Double> entry : report.getCompletenessBreakdown().entrySet()) {
            sb.append(String.format("  %-15s : %5.1f%%\n", entry.getKey(), entry.getValue()));
        }
        sb.append("\n");

        // Link Predictions
        sb.append("-- Top Link Predictions --------------------------------------------\n");
        if (report.getPredictions().isEmpty()) {
            sb.append("  No predictions (graph may be complete)\n");
        } else {
            sb.append(String.format("  %-10s %-10s %8s %8s  %s\n",
                    "Source", "Target", "Score", "Agree", "Explanation"));
            for (Prediction p : report.getPredictions()) {
                sb.append(String.format("  %-10s %-10s %8.4f %6d/6  %s\n",
                        truncate(p.getSource(), 10), truncate(p.getTarget(), 10),
                        p.getScore(), p.getAgreeingHeuristics(), p.getExplanation()));
            }
        }
        sb.append("\n");

        // Triadic Closures
        sb.append("-- Triadic Closure Opportunities -----------------------------------\n");
        if (report.getTriadicClosures().isEmpty()) {
            sb.append("  No open triads found\n");
        } else {
            for (TriadicClosure tc : report.getTriadicClosures()) {
                sb.append(String.format("  %s - %s (via %s, likelihood=%.2f)\n",
                        tc.getNodeA(), tc.getNodeC(), tc.getBridge(), tc.getLikelihood()));
            }
        }
        sb.append("\n");

        // Reciprocity
        if (!report.getReciprocityCandidates().isEmpty()) {
            sb.append("-- Reciprocity Candidates ------------------------------------------\n");
            for (ReciprocityCandidate rc : report.getReciprocityCandidates()) {
                sb.append(String.format("  %s -> %s (prob=%.2f, %s)\n",
                        rc.getFrom(), rc.getTo(), rc.getProbability(), rc.getEvidence()));
            }
            sb.append("\n");
        }

        // Structural Holes
        sb.append("-- Structural Holes ------------------------------------------------\n");
        if (report.getStructuralHoles().isEmpty()) {
            sb.append("  No significant structural holes detected\n");
        } else {
            for (StructuralHole sh : report.getStructuralHoles()) {
                sb.append(String.format("  Groups (size %d, %d) similarity=%.2f bridge=%s\n",
                        sh.getGroupA().size(), sh.getGroupB().size(),
                        sh.getSimilarity(), sh.getBridgeCandidate()));
            }
        }
        sb.append("\n");

        // Motif Patterns
        sb.append("-- Motif Patterns --------------------------------------------------\n");
        for (MotifPattern mp : report.getMotifPatterns()) {
            sb.append(String.format("  %s: %d found, %d implied connections\n",
                    mp.getType(), mp.getCount(), mp.getImpliedConnections().size()));
        }
        sb.append("\n");

        sb.append("===================================================================\n");
        return sb.toString();
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + ".." : s;
    }

    // ── HTML Export ──────────────────────────────────────────────────

    public String exportHtml(ExtractorReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">\n");
        sb.append("<title>Graph Knowledge Extractor Report</title>\n");
        sb.append("<style>\n");
        sb.append("body{font-family:'Segoe UI',sans-serif;margin:0;padding:20px;background:#1a1a2e;color:#e0e0e0}\n");
        sb.append(".header{text-align:center;padding:20px;background:linear-gradient(135deg,#16213e,#0f3460);border-radius:12px;margin-bottom:20px}\n");
        sb.append(".header h1{margin:0;color:#4fc3f7;font-size:1.8em}\n");
        sb.append(".gauge{width:200px;height:100px;margin:20px auto;position:relative}\n");
        sb.append(".gauge-fill{width:200px;height:100px;border-radius:100px 100px 0 0;background:conic-gradient(from 0.75turn,#4caf50,#ffeb3b,#f44336);position:absolute;clip-path:polygon(0 100%,0 0,100% 0,100% 100%)}\n");
        sb.append(".gauge-value{position:absolute;bottom:0;left:50%;transform:translateX(-50%);font-size:2em;font-weight:bold;color:#4fc3f7}\n");
        sb.append(".tabs{display:flex;gap:4px;margin-bottom:0;flex-wrap:wrap}\n");
        sb.append(".tab{padding:10px 16px;cursor:pointer;background:#16213e;border-radius:8px 8px 0 0;color:#aaa;border:1px solid #333;border-bottom:none}\n");
        sb.append(".tab.active{background:#0f3460;color:#4fc3f7;font-weight:bold}\n");
        sb.append(".panel{display:none;padding:20px;background:#0f3460;border-radius:0 8px 8px 8px;border:1px solid #333}\n");
        sb.append(".panel.active{display:block}\n");
        sb.append("table{width:100%;border-collapse:collapse;margin:10px 0}\n");
        sb.append("th,td{padding:8px 12px;text-align:left;border-bottom:1px solid #333}\n");
        sb.append("th{background:#16213e;color:#4fc3f7;cursor:pointer}\n");
        sb.append("tr:hover{background:#16213e}\n");
        sb.append(".badge{display:inline-block;padding:2px 8px;border-radius:12px;font-size:0.8em}\n");
        sb.append(".badge-high{background:#4caf50;color:#fff}\n");
        sb.append(".badge-mid{background:#ff9800;color:#fff}\n");
        sb.append(".badge-low{background:#f44336;color:#fff}\n");
        sb.append(".metric{display:inline-block;margin:8px;padding:12px;background:#16213e;border-radius:8px;min-width:120px;text-align:center}\n");
        sb.append(".metric-value{font-size:1.5em;font-weight:bold;color:#4fc3f7}\n");
        sb.append(".metric-label{font-size:0.85em;color:#aaa}\n");
        sb.append("</style></head><body>\n");

        // Header
        sb.append("<div class=\"header\">\n");
        sb.append("<h1>🧠 Graph Knowledge Extractor</h1>\n");
        sb.append(String.format("<p>%d nodes • %d edges • %s</p>\n",
                report.getTotalNodes(), report.getTotalEdges(),
                directed ? "directed" : "undirected"));
        sb.append(String.format("<div class=\"gauge-value\" style=\"font-size:2.5em;color:%s\">%.1f</div>\n",
                report.getCompletenessScore() >= 70 ? "#4caf50" :
                        report.getCompletenessScore() >= 40 ? "#ff9800" : "#f44336",
                report.getCompletenessScore()));
        sb.append("<p style=\"color:#aaa\">Knowledge Completeness Score</p>\n");
        sb.append("</div>\n");

        // Metrics row
        sb.append("<div style=\"text-align:center;margin-bottom:20px\">\n");
        for (Map.Entry<String, Double> entry : report.getCompletenessBreakdown().entrySet()) {
            sb.append(String.format("<div class=\"metric\"><div class=\"metric-value\">%.0f%%</div><div class=\"metric-label\">%s</div></div>\n",
                    entry.getValue(), entry.getKey()));
        }
        sb.append("</div>\n");

        // Tabs
        sb.append("<div class=\"tabs\">\n");
        sb.append("<div class=\"tab active\" onclick=\"showTab(0)\">Link Predictions</div>\n");
        sb.append("<div class=\"tab\" onclick=\"showTab(1)\">Triadic Closures</div>\n");
        sb.append("<div class=\"tab\" onclick=\"showTab(2)\">Reciprocity</div>\n");
        sb.append("<div class=\"tab\" onclick=\"showTab(3)\">Structural Holes</div>\n");
        sb.append("<div class=\"tab\" onclick=\"showTab(4)\">Motif Patterns</div>\n");
        sb.append("</div>\n");

        // Panel 0: Link Predictions
        sb.append("<div class=\"panel active\">\n");
        sb.append("<h3>Top Link Predictions</h3>\n");
        sb.append("<table><thead><tr><th>Source</th><th>Target</th><th>Score</th><th>Agreeing</th><th>Explanation</th></tr></thead><tbody>\n");
        for (Prediction p : report.getPredictions()) {
            String badge = p.getScore() >= 0.5 ? "badge-high" : p.getScore() >= 0.2 ? "badge-mid" : "badge-low";
            sb.append(String.format("<tr><td>%s</td><td>%s</td><td><span class=\"badge %s\">%.4f</span></td><td>%d/6</td><td>%s</td></tr>\n",
                    esc(p.getSource()), esc(p.getTarget()), badge, p.getScore(),
                    p.getAgreeingHeuristics(), esc(p.getExplanation())));
        }
        sb.append("</tbody></table></div>\n");

        // Panel 1: Triadic Closures
        sb.append("<div class=\"panel\">\n");
        sb.append("<h3>Triadic Closure Opportunities</h3>\n");
        sb.append("<table><thead><tr><th>Node A</th><th>Node C</th><th>Bridge</th><th>Likelihood</th></tr></thead><tbody>\n");
        for (TriadicClosure tc : report.getTriadicClosures()) {
            sb.append(String.format("<tr><td>%s</td><td>%s</td><td>%s</td><td>%.2f</td></tr>\n",
                    esc(tc.getNodeA()), esc(tc.getNodeC()), esc(tc.getBridge()), tc.getLikelihood()));
        }
        sb.append("</tbody></table></div>\n");

        // Panel 2: Reciprocity
        sb.append("<div class=\"panel\">\n");
        sb.append("<h3>Reciprocity Candidates</h3>\n");
        if (report.getReciprocityCandidates().isEmpty()) {
            sb.append("<p>Not applicable (undirected graph) or no candidates found.</p>\n");
        } else {
            sb.append("<table><thead><tr><th>From</th><th>To</th><th>Probability</th><th>Evidence</th></tr></thead><tbody>\n");
            for (ReciprocityCandidate rc : report.getReciprocityCandidates()) {
                sb.append(String.format("<tr><td>%s</td><td>%s</td><td>%.2f</td><td>%s</td></tr>\n",
                        esc(rc.getFrom()), esc(rc.getTo()), rc.getProbability(), esc(rc.getEvidence())));
            }
            sb.append("</tbody></table>\n");
        }
        sb.append("</div>\n");

        // Panel 3: Structural Holes
        sb.append("<div class=\"panel\">\n");
        sb.append("<h3>Structural Holes</h3>\n");
        if (report.getStructuralHoles().isEmpty()) {
            sb.append("<p>No significant structural holes detected.</p>\n");
        } else {
            sb.append("<table><thead><tr><th>Group A</th><th>Group B</th><th>Similarity</th><th>Bridge</th></tr></thead><tbody>\n");
            for (StructuralHole sh : report.getStructuralHoles()) {
                sb.append(String.format("<tr><td>%d nodes</td><td>%d nodes</td><td>%.2f</td><td>%s</td></tr>\n",
                        sh.getGroupA().size(), sh.getGroupB().size(),
                        sh.getSimilarity(), esc(sh.getBridgeCandidate())));
            }
            sb.append("</tbody></table>\n");
        }
        sb.append("</div>\n");

        // Panel 4: Motifs
        sb.append("<div class=\"panel\">\n");
        sb.append("<h3>Motif Patterns & Implied Connections</h3>\n");
        for (MotifPattern mp : report.getMotifPatterns()) {
            sb.append(String.format("<h4>%s — %d found</h4>\n", esc(mp.getType()), mp.getCount()));
            if (!mp.getImpliedConnections().isEmpty()) {
                sb.append("<ul>\n");
                for (String ic : mp.getImpliedConnections()) {
                    sb.append(String.format("<li>%s</li>\n", esc(ic)));
                }
                sb.append("</ul>\n");
            }
        }
        sb.append("</div>\n");

        // JavaScript
        sb.append("<script>\n");
        sb.append("function showTab(idx){document.querySelectorAll('.tab').forEach((t,i)=>{t.classList.toggle('active',i===idx)});");
        sb.append("document.querySelectorAll('.panel').forEach((p,i)=>{p.classList.toggle('active',i===idx)})}\n");
        sb.append("document.querySelectorAll('th').forEach(th=>{th.addEventListener('click',function(){");
        sb.append("const table=this.closest('table'),idx=[...this.parentNode.children].indexOf(this),");
        sb.append("rows=[...table.tBodies[0].rows],asc=this.dataset.asc!=='true';");
        sb.append("rows.sort((a,b)=>{let av=a.cells[idx].textContent,bv=b.cells[idx].textContent;");
        sb.append("let an=parseFloat(av),bn=parseFloat(bv);if(!isNaN(an)&&!isNaN(bn))return asc?an-bn:bn-an;");
        sb.append("return asc?av.localeCompare(bv):bv.localeCompare(av)});");
        sb.append("rows.forEach(r=>table.tBodies[0].appendChild(r));this.dataset.asc=String(asc)})})\n");
        sb.append("</script>\n");
        sb.append("</body></html>");

        return sb.toString();
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
