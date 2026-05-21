package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GraphFairnessAuditEngine — autonomous network fairness and equity auditor.
 *
 * <p>Measures how equitably resources, influence, and access are distributed
 * across a network. Uses Gini coefficients, Lorenz curves, and BFS-based
 * proximity analysis to quantify structural inequality and recommend
 * interventions.</p>
 *
 * <h3>Seven Analysis Engines:</h3>
 * <ol>
 *   <li><b>Degree Equity Analyzer</b> — Gini coefficient on degree distribution,
 *       Lorenz curve, identifies degree-starved vs degree-privileged nodes</li>
 *   <li><b>Centrality Equity Analyzer</b> — betweenness centrality Gini via
 *       BFS shortest-path counting, centrality inequality measurement</li>
 *   <li><b>Access Equity Measurer</b> — average shortest-path distance per node,
 *       identifies access-disadvantaged and access-privileged nodes</li>
 *   <li><b>Community Representation Checker</b> — label-propagation community
 *       detection, Gini on community sizes to detect representation imbalance</li>
 *   <li><b>Resource Proximity Analyzer</b> — designates top-k degree nodes as
 *       resources, finds nodes in "resource deserts" (distance &gt; 2× median)</li>
 *   <li><b>Fairness Intervention Planner</b> — greedy edge-addition suggestions
 *       ranked by estimated fairness improvement (autonomous agentic capability)</li>
 *   <li><b>Insight Generator</b> — produces ranked, actionable fairness insights</li>
 * </ol>
 *
 * <p>Usage:</p>
 * <pre>
 *   GraphFairnessAuditEngine engine = new GraphFairnessAuditEngine();
 *   FairnessReport report = engine.analyze(graph);
 *   System.out.println(engine.toText(report));
 *   String html = engine.exportHtml(report);
 * </pre>
 *
 * @author sauravbhattacharya001
 */
public class GraphFairnessAuditEngine {

    // -- Configuration --------------------------------------------------------
    private int resourceCount = 3;
    private int maxInterventions = 5;
    private int communityIterations = 20;
    private Random rng = new Random(42);

    // -- Builder-style setters ------------------------------------------------

    public GraphFairnessAuditEngine setResourceCount(int n) {
        this.resourceCount = Math.max(1, n);
        return this;
    }

    public GraphFairnessAuditEngine setMaxInterventions(int n) {
        this.maxInterventions = Math.max(0, n);
        return this;
    }

    public GraphFairnessAuditEngine setCommunityIterations(int n) {
        this.communityIterations = Math.max(1, n);
        return this;
    }

    public GraphFairnessAuditEngine setRandomSeed(long seed) {
        this.rng = new Random(seed);
        return this;
    }

    // ==================================================================
    // Inner classes
    // ==================================================================

    /** Fairness tier classification. */
    public enum FairnessTier {
        EXEMPLARY, FAIR, MODERATE, INEQUITABLE, CRITICAL;

        public static FairnessTier fromScore(double score) {
            if (score >= 80) return EXEMPLARY;
            if (score >= 60) return FAIR;
            if (score >= 40) return MODERATE;
            if (score >= 20) return INEQUITABLE;
            return CRITICAL;
        }
    }

    /** A suggested edge addition to improve fairness. */
    public static class Intervention implements Comparable<Intervention> {
        public final String source;
        public final String target;
        public final double expectedImprovement;

        public Intervention(String source, String target, double expectedImprovement) {
            this.source = source;
            this.target = target;
            this.expectedImprovement = expectedImprovement;
        }

        @Override
        public int compareTo(Intervention other) {
            return Double.compare(other.expectedImprovement, this.expectedImprovement);
        }

        @Override
        public String toString() {
            return String.format("%s — %s (improvement: %.4f)", source, target, expectedImprovement);
        }
    }

    /** Full fairness audit report. */
    public static class FairnessReport {
        public final double degreeGini;
        public final double centralityGini;
        public final double accessGini;
        public final double communityGini;
        public final int resourceDesertCount;
        public final List<double[]> lorenzCurve;
        public final List<String> accessDisadvantaged;
        public final List<String> accessPrivileged;
        public final List<String> degreeStarved;
        public final List<String> degreePrivileged;
        public final List<Intervention> interventions;
        public final double healthScore;
        public final FairnessTier fairnessTier;
        public final List<String> insights;
        public final int nodeCount;
        public final int edgeCount;
        public final int communityCount;
        public final Map<String, Integer> communityAssignment;

        public FairnessReport(double degreeGini, double centralityGini,
                              double accessGini, double communityGini,
                              int resourceDesertCount,
                              List<double[]> lorenzCurve,
                              List<String> accessDisadvantaged,
                              List<String> accessPrivileged,
                              List<String> degreeStarved,
                              List<String> degreePrivileged,
                              List<Intervention> interventions,
                              double healthScore,
                              List<String> insights,
                              int nodeCount, int edgeCount,
                              int communityCount,
                              Map<String, Integer> communityAssignment) {
            this.degreeGini = degreeGini;
            this.centralityGini = centralityGini;
            this.accessGini = accessGini;
            this.communityGini = communityGini;
            this.resourceDesertCount = resourceDesertCount;
            this.lorenzCurve = Collections.unmodifiableList(new ArrayList<>(lorenzCurve));
            this.accessDisadvantaged = Collections.unmodifiableList(new ArrayList<>(accessDisadvantaged));
            this.accessPrivileged = Collections.unmodifiableList(new ArrayList<>(accessPrivileged));
            this.degreeStarved = Collections.unmodifiableList(new ArrayList<>(degreeStarved));
            this.degreePrivileged = Collections.unmodifiableList(new ArrayList<>(degreePrivileged));
            this.interventions = Collections.unmodifiableList(new ArrayList<>(interventions));
            this.healthScore = healthScore;
            this.fairnessTier = FairnessTier.fromScore(healthScore);
            this.insights = Collections.unmodifiableList(new ArrayList<>(insights));
            this.nodeCount = nodeCount;
            this.edgeCount = edgeCount;
            this.communityCount = communityCount;
            this.communityAssignment = Collections.unmodifiableMap(new HashMap<>(communityAssignment));
        }
    }

    // ==================================================================
    // Main analysis
    // ==================================================================

    /**
     * Run full fairness audit on the given graph.
     *
     * @param graph the network to audit
     * @return comprehensive fairness report
     */
    public FairnessReport analyze(Graph<String, Edge> graph) {
        List<String> vertices = new ArrayList<>(graph.getVertices());
        int n = vertices.size();
        int e = graph.getEdgeCount();

        if (n == 0) {
            return new FairnessReport(0, 0, 0, 0, 0,
                    Arrays.asList(new double[]{0, 0}, new double[]{1, 1}),
                    Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), 100.0,
                    Collections.singletonList("Empty graph — no fairness issues possible."),
                    0, 0, 0, Collections.emptyMap());
        }

        if (n == 1) {
            Map<String, Integer> comm = new HashMap<>();
            comm.put(vertices.get(0), 0);
            return new FairnessReport(0, 0, 0, 0, 0,
                    Arrays.asList(new double[]{0, 0}, new double[]{1, 1}),
                    Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), 100.0,
                    Collections.singletonList("Single-node graph — trivially fair."),
                    1, 0, 1, comm);
        }

        // -- Build adjacency --------------------------------------------------
        Map<String, Set<String>> adj = buildAdjacency(graph, vertices);

        // -- Engine 1: Degree Equity ------------------------------------------
        int[] degrees = new int[n];
        for (int i = 0; i < n; i++) {
            degrees[i] = adj.get(vertices.get(i)).size();
        }
        double degreeGini = computeGini(degrees);
        List<double[]> lorenz = computeLorenzCurve(degrees);

        // Identify degree-starved (bottom 20%) and degree-privileged (top 20%)
        int[] sortedDeg = Arrays.copyOf(degrees, n);
        Arrays.sort(sortedDeg);
        double threshold20 = sortedDeg[Math.max(0, (int)(n * 0.2) - 1)];
        double threshold80 = sortedDeg[Math.min(n - 1, (int)(n * 0.8))];
        List<String> degreeStarved = new ArrayList<>();
        List<String> degreePrivileged = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (degrees[i] <= threshold20) degreeStarved.add(vertices.get(i));
            if (degrees[i] >= threshold80 && degrees[i] > 0) degreePrivileged.add(vertices.get(i));
        }

        // -- Engine 2: Centrality Equity --------------------------------------
        double[] centrality = computeBetweennessCentrality(vertices, adj, n);
        int[] centralityInt = new int[n];
        double maxCent = 0;
        for (double c : centrality) maxCent = Math.max(maxCent, c);
        if (maxCent > 0) {
            for (int i = 0; i < n; i++) {
                centralityInt[i] = (int) Math.round(centrality[i] * 10000 / maxCent);
            }
        }
        double centralityGini = computeGini(centralityInt);

        // -- Engine 3: Access Equity ------------------------------------------
        double[] avgDistances = computeAverageDistances(vertices, adj, n);
        int[] distInt = new int[n];
        for (int i = 0; i < n; i++) {
            distInt[i] = (int) Math.round(avgDistances[i] * 1000);
        }
        double accessGini = computeGini(distInt);

        // Identify access-disadvantaged (top 20% distance) and privileged (bottom 20%)
        double[] sortedDist = Arrays.copyOf(avgDistances, n);
        Arrays.sort(sortedDist);
        double distThreshHigh = sortedDist[Math.min(n - 1, (int)(n * 0.8))];
        double distThreshLow = sortedDist[Math.max(0, (int)(n * 0.2) - 1)];
        List<String> accessDisadvantaged = new ArrayList<>();
        List<String> accessPrivileged = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (avgDistances[i] >= distThreshHigh && avgDistances[i] > 0) {
                accessDisadvantaged.add(vertices.get(i));
            }
            if (avgDistances[i] <= distThreshLow) {
                accessPrivileged.add(vertices.get(i));
            }
        }

        // -- Engine 4: Community Representation -------------------------------
        Map<String, Integer> communityAssignment = detectCommunities(vertices, adj, n);
        Map<Integer, Integer> commSizes = new HashMap<>();
        for (int label : communityAssignment.values()) {
            commSizes.merge(label, 1, Integer::sum);
        }
        int communityCount = commSizes.size();
        int[] commSizeArr = commSizes.values().stream().mapToInt(Integer::intValue).toArray();
        double communityGini = commSizeArr.length <= 1 ? 0 : computeGini(commSizeArr);

        // -- Engine 5: Resource Proximity -------------------------------------
        int rCount = Math.min(resourceCount, n);
        List<String> resources = identifyResources(vertices, degrees, rCount);
        int resourceDesertCount = countResourceDeserts(vertices, adj, resources, n);

        // -- Engine 6: Fairness Intervention Planner --------------------------
        List<Intervention> interventions = planInterventions(graph, vertices, adj, degrees, n);

        // -- Health Score (inverted Gini average) -----------------------------
        double avgGini = (degreeGini + centralityGini + accessGini + communityGini) / 4.0;
        double healthScore = Math.max(0, Math.min(100, (1.0 - avgGini) * 100));

        // -- Engine 7: Insight Generator --------------------------------------
        List<String> insights = generateInsights(degreeGini, centralityGini,
                accessGini, communityGini, resourceDesertCount,
                degreeStarved, degreePrivileged, accessDisadvantaged,
                communityCount, n, interventions, healthScore);

        return new FairnessReport(degreeGini, centralityGini, accessGini,
                communityGini, resourceDesertCount, lorenz,
                accessDisadvantaged, accessPrivileged,
                degreeStarved, degreePrivileged,
                interventions, healthScore, insights,
                n, e, communityCount, communityAssignment);
    }

    // ==================================================================
    // Engine implementations
    // ==================================================================

    private Map<String, Set<String>> buildAdjacency(Graph<String, Edge> graph,
                                                     List<String> vertices) {
        Map<String, Set<String>> adj = new HashMap<>();
        for (String v : vertices) adj.put(v, new HashSet<>());
        for (Edge edge : graph.getEdges()) {
            Collection<String> eps = graph.getEndpoints(edge);
            Iterator<String> it = eps.iterator();
            String u = it.next();
            String v = it.hasNext() ? it.next() : u;
            if (!u.equals(v)) {
                adj.get(u).add(v);
                adj.get(v).add(u);
            }
        }
        return adj;
    }

    /** Compute Gini coefficient for an array of non-negative values. */
    private double computeGini(int[] values) {
        int n = values.length;
        if (n <= 1) return 0;
        long sum = 0;
        for (int v : values) sum += v;
        if (sum == 0) return 0;
        int[] sorted = Arrays.copyOf(values, n);
        Arrays.sort(sorted);
        double numerator = 0;
        for (int i = 0; i < n; i++) {
            numerator += (2.0 * (i + 1) - n - 1) * sorted[i];
        }
        return numerator / (n * (double) sum);
    }

    /** Compute Lorenz curve points from degree values. */
    private List<double[]> computeLorenzCurve(int[] values) {
        int n = values.length;
        int[] sorted = Arrays.copyOf(values, n);
        Arrays.sort(sorted);
        long total = 0;
        for (int v : sorted) total += v;
        List<double[]> curve = new ArrayList<>();
        curve.add(new double[]{0, 0});
        if (total == 0) {
            curve.add(new double[]{1, 1});
            return curve;
        }
        long cumulative = 0;
        for (int i = 0; i < n; i++) {
            cumulative += sorted[i];
            curve.add(new double[]{
                    (double)(i + 1) / n,
                    (double) cumulative / total
            });
        }
        return curve;
    }

    /** Approximate betweenness centrality via BFS from each node. */
    private double[] computeBetweennessCentrality(List<String> vertices,
                                                   Map<String, Set<String>> adj, int n) {
        double[] centrality = new double[n];
        Map<String, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < n; i++) indexMap.put(vertices.get(i), i);

        for (int s = 0; s < n; s++) {
            // BFS from s
            int[] dist = new int[n];
            long[] paths = new long[n];
            Arrays.fill(dist, -1);
            dist[s] = 0;
            paths[s] = 1;
            List<List<Integer>> preds = new ArrayList<>();
            for (int i = 0; i < n; i++) preds.add(new ArrayList<>());
            Queue<Integer> queue = new LinkedList<>();
            queue.add(s);
            List<Integer> order = new ArrayList<>();

            while (!queue.isEmpty()) {
                int u = queue.poll();
                order.add(u);
                for (String nbr : adj.get(vertices.get(u))) {
                    int v = indexMap.get(nbr);
                    if (dist[v] < 0) {
                        dist[v] = dist[u] + 1;
                        queue.add(v);
                    }
                    if (dist[v] == dist[u] + 1) {
                        paths[v] += paths[u];
                        preds.get(v).add(u);
                    }
                }
            }

            // Back-propagation
            double[] delta = new double[n];
            for (int i = order.size() - 1; i >= 0; i--) {
                int w = order.get(i);
                for (int pred : preds.get(w)) {
                    double frac = (double) paths[pred] / paths[w];
                    delta[pred] += frac * (1 + delta[w]);
                }
                if (w != s) {
                    centrality[w] += delta[w];
                }
            }
        }
        // Normalize for undirected graph (each pair counted twice)
        for (int i = 0; i < n; i++) centrality[i] /= 2.0;
        return centrality;
    }

    /** Compute average shortest path distance from each node to all reachable nodes. */
    private double[] computeAverageDistances(List<String> vertices,
                                              Map<String, Set<String>> adj, int n) {
        Map<String, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < n; i++) indexMap.put(vertices.get(i), i);
        double[] avgDist = new double[n];

        for (int s = 0; s < n; s++) {
            int[] dist = new int[n];
            Arrays.fill(dist, -1);
            dist[s] = 0;
            Queue<Integer> queue = new LinkedList<>();
            queue.add(s);
            long totalDist = 0;
            int reachable = 0;

            while (!queue.isEmpty()) {
                int u = queue.poll();
                for (String nbr : adj.get(vertices.get(u))) {
                    int v = indexMap.get(nbr);
                    if (dist[v] < 0) {
                        dist[v] = dist[u] + 1;
                        totalDist += dist[v];
                        reachable++;
                        queue.add(v);
                    }
                }
            }
            avgDist[s] = reachable > 0 ? (double) totalDist / reachable : 0;
        }
        return avgDist;
    }

    /** Simple label propagation community detection. */
    private Map<String, Integer> detectCommunities(List<String> vertices,
                                                    Map<String, Set<String>> adj, int n) {
        Map<String, Integer> labels = new HashMap<>();
        for (int i = 0; i < n; i++) labels.put(vertices.get(i), i);

        List<String> shuffled = new ArrayList<>(vertices);
        for (int iter = 0; iter < communityIterations; iter++) {
            boolean changed = false;
            Collections.shuffle(shuffled, rng);
            for (String v : shuffled) {
                Set<String> neighbors = adj.get(v);
                if (neighbors.isEmpty()) continue;
                // Majority label among neighbors
                Map<Integer, Integer> labelCounts = new HashMap<>();
                for (String nbr : neighbors) {
                    labelCounts.merge(labels.get(nbr), 1, Integer::sum);
                }
                int bestLabel = labels.get(v);
                int bestCount = 0;
                for (Map.Entry<Integer, Integer> entry : labelCounts.entrySet()) {
                    if (entry.getValue() > bestCount) {
                        bestCount = entry.getValue();
                        bestLabel = entry.getKey();
                    }
                }
                if (bestLabel != labels.get(v)) {
                    labels.put(v, bestLabel);
                    changed = true;
                }
            }
            if (!changed) break;
        }
        return labels;
    }

    /** Identify top-k degree nodes as resources. */
    private List<String> identifyResources(List<String> vertices, int[] degrees, int k) {
        Integer[] indices = new Integer[vertices.size()];
        for (int i = 0; i < indices.length; i++) indices[i] = i;
        Arrays.sort(indices, (a, b) -> Integer.compare(degrees[b], degrees[a]));
        List<String> resources = new ArrayList<>();
        for (int i = 0; i < Math.min(k, indices.length); i++) {
            resources.add(vertices.get(indices[i]));
        }
        return resources;
    }

    /** Count nodes in resource deserts (min distance to resource > 2 * median). */
    private int countResourceDeserts(List<String> vertices, Map<String, Set<String>> adj,
                                     List<String> resources, int n) {
        if (resources.isEmpty()) return n;
        Map<String, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < n; i++) indexMap.put(vertices.get(i), i);

        // Multi-source BFS from all resources
        int[] dist = new int[n];
        Arrays.fill(dist, Integer.MAX_VALUE);
        Queue<Integer> queue = new LinkedList<>();
        for (String r : resources) {
            int ri = indexMap.get(r);
            dist[ri] = 0;
            queue.add(ri);
        }
        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (String nbr : adj.get(vertices.get(u))) {
                int v = indexMap.get(nbr);
                if (dist[v] > dist[u] + 1) {
                    dist[v] = dist[u] + 1;
                    queue.add(v);
                }
            }
        }

        // Compute median distance
        int[] finite = Arrays.stream(dist).filter(d -> d < Integer.MAX_VALUE).toArray();
        if (finite.length == 0) return n;
        Arrays.sort(finite);
        double median = finite.length % 2 == 0
                ? (finite[finite.length / 2 - 1] + finite[finite.length / 2]) / 2.0
                : finite[finite.length / 2];
        double threshold = Math.max(2, 2 * median);

        int desertCount = 0;
        for (int d : dist) {
            if (d > threshold || d == Integer.MAX_VALUE) desertCount++;
        }
        return desertCount;
    }

    /** Greedy edge-addition intervention planner. */
    private List<Intervention> planInterventions(Graph<String, Edge> graph,
                                                  List<String> vertices,
                                                  Map<String, Set<String>> adj,
                                                  int[] degrees, int n) {
        if (n <= 2) return Collections.emptyList();

        // Find degree-starved nodes (below average)
        double avgDeg = 0;
        for (int d : degrees) avgDeg += d;
        avgDeg /= n;

        List<Integer> starved = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (degrees[i] < avgDeg) starved.add(i);
        }
        if (starved.isEmpty()) return Collections.emptyList();

        // For each starved node, find best unconnected partner.
        // The improvement estimate factors in both the starved node's deficit
        // and the candidate partner's degree — connecting a low-degree node to
        // a high-degree hub reduces the Gini coefficient more than connecting
        // to another low-degree node.  Previous code used a flat formula that
        // scored all candidates with degrees[j] > avgDeg identically (only a
        // binary 1.5× multiplier), making partner selection arbitrary.
        List<Intervention> candidates = new ArrayList<>();
        double maxDeg = 1;
        for (int d : degrees) maxDeg = Math.max(maxDeg, d);
        for (int si : starved) {
            String sv = vertices.get(si);
            Set<String> neighbors = adj.get(sv);
            double bestImprovement = 0;
            String bestTarget = null;
            double deficit = avgDeg - degrees[si]; // always > 0 for starved
            for (int j = 0; j < n; j++) {
                if (j == si) continue;
                String tv = vertices.get(j);
                if (neighbors.contains(tv)) continue;
                // Base improvement from closing the starved node's deficit.
                // Scale by partner degree so higher-degree partners rank higher.
                double partnerFactor = 1.0 + (degrees[j] / maxDeg);
                double improvement = deficit * partnerFactor / (n * avgDeg + 1);
                if (improvement > bestImprovement) {
                    bestImprovement = improvement;
                    bestTarget = tv;
                }
            }
            if (bestTarget != null) {
                candidates.add(new Intervention(sv, bestTarget,
                        Math.round(bestImprovement * 10000.0) / 10000.0));
            }
        }

        Collections.sort(candidates);
        return candidates.subList(0, Math.min(maxInterventions, candidates.size()));
    }

    /** Generate ranked insights. */
    private List<String> generateInsights(double degreeGini, double centralityGini,
                                           double accessGini, double communityGini,
                                           int resourceDesertCount,
                                           List<String> degreeStarved,
                                           List<String> degreePrivileged,
                                           List<String> accessDisadvantaged,
                                           int communityCount, int nodeCount,
                                           List<Intervention> interventions,
                                           double healthScore) {
        List<String> insights = new ArrayList<>();

        // Overall assessment
        FairnessTier tier = FairnessTier.fromScore(healthScore);
        insights.add(String.format("Overall fairness: %s (score %.1f/100) — %s",
                tier, healthScore, tierDescription(tier)));

        // Degree inequality
        if (degreeGini > 0.5) {
            insights.add(String.format("⚠ CRITICAL: Severe degree inequality (Gini=%.3f). " +
                    "A small number of hubs dominate connectivity while %d nodes are degree-starved.",
                    degreeGini, degreeStarved.size()));
        } else if (degreeGini > 0.3) {
            insights.add(String.format("⚠ WARNING: Moderate degree inequality (Gini=%.3f). " +
                    "%d nodes have significantly fewer connections.", degreeGini, degreeStarved.size()));
        } else {
            insights.add(String.format("✓ Degree distribution is relatively equitable (Gini=%.3f).", degreeGini));
        }

        // Centrality inequality
        if (centralityGini > 0.5) {
            insights.add(String.format("⚠ CRITICAL: Centrality concentrated in few nodes (Gini=%.3f). " +
                    "Network routing depends heavily on a small set of bottleneck nodes.", centralityGini));
        } else if (centralityGini > 0.3) {
            insights.add(String.format("⚠ WARNING: Some centrality concentration (Gini=%.3f).", centralityGini));
        }

        // Access inequality
        if (accessGini > 0.3) {
            insights.add(String.format("⚠ WARNING: Access inequality detected (Gini=%.3f). " +
                    "%d nodes are access-disadvantaged with longer average paths.", accessGini,
                    accessDisadvantaged.size()));
        }

        // Community balance
        if (communityGini > 0.4) {
            insights.add(String.format("⚠ WARNING: Community sizes are imbalanced (Gini=%.3f, %d communities). " +
                    "Some communities may be underrepresented.", communityGini, communityCount));
        }

        // Resource deserts
        if (resourceDesertCount > 0) {
            double pct = 100.0 * resourceDesertCount / Math.max(1, nodeCount);
            insights.add(String.format("⚠ %d nodes (%.0f%%) are in resource deserts — " +
                    "far from high-degree hub nodes.", resourceDesertCount, pct));
        }

        // Interventions
        if (!interventions.isEmpty()) {
            insights.add(String.format("RECOMMENDATION: %d edge additions identified that would improve equity. " +
                    "Top: connect %s to %s.", interventions.size(),
                    interventions.get(0).source, interventions.get(0).target));
        }

        return insights;
    }

    private String tierDescription(FairnessTier tier) {
        switch (tier) {
            case EXEMPLARY: return "resources and access are well-distributed";
            case FAIR: return "minor inequalities present but acceptable";
            case MODERATE: return "noticeable structural inequalities";
            case INEQUITABLE: return "significant structural inequality requiring attention";
            case CRITICAL: return "severe inequality — network structure heavily favors few nodes";
            default: return "";
        }
    }

    // ==================================================================
    // Text output
    // ==================================================================

    /**
     * Generate a human-readable text report.
     *
     * @param report the fairness report
     * @return formatted text string
     */
    public String toText(FairnessReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════════\n");
        sb.append("  GRAPH FAIRNESS AUDIT ENGINE — Analysis Report\n");
        sb.append("═══════════════════════════════════════════════════════\n\n");

        sb.append(String.format("  Nodes: %d    Edges: %d    Communities: %d\n",
                report.nodeCount, report.edgeCount, report.communityCount));
        sb.append(String.format("  Fairness Score: %.1f / 100  [%s]\n\n",
                report.healthScore, report.fairnessTier));

        sb.append("── Gini Coefficients ───────────────────────────────\n");
        sb.append(String.format("  Degree:      %.4f  %s\n", report.degreeGini, giniBar(report.degreeGini)));
        sb.append(String.format("  Centrality:  %.4f  %s\n", report.centralityGini, giniBar(report.centralityGini)));
        sb.append(String.format("  Access:      %.4f  %s\n", report.accessGini, giniBar(report.accessGini)));
        sb.append(String.format("  Community:   %.4f  %s\n\n", report.communityGini, giniBar(report.communityGini)));

        sb.append("── Degree Equity ───────────────────────────────────\n");
        sb.append(String.format("  Degree-starved nodes (%d): %s\n",
                report.degreeStarved.size(), limitList(report.degreeStarved, 10)));
        sb.append(String.format("  Degree-privileged nodes (%d): %s\n\n",
                report.degreePrivileged.size(), limitList(report.degreePrivileged, 10)));

        sb.append("── Access Equity ───────────────────────────────────\n");
        sb.append(String.format("  Access-disadvantaged nodes (%d): %s\n",
                report.accessDisadvantaged.size(), limitList(report.accessDisadvantaged, 10)));
        sb.append(String.format("  Access-privileged nodes (%d): %s\n\n",
                report.accessPrivileged.size(), limitList(report.accessPrivileged, 10)));

        sb.append("── Resource Proximity ──────────────────────────────\n");
        sb.append(String.format("  Nodes in resource deserts: %d\n\n", report.resourceDesertCount));

        if (!report.interventions.isEmpty()) {
            sb.append("── Suggested Interventions ─────────────────────────\n");
            for (int i = 0; i < report.interventions.size(); i++) {
                sb.append(String.format("  %d. %s\n", i + 1, report.interventions.get(i)));
            }
            sb.append("\n");
        }

        sb.append("── Lorenz Curve ────────────────────────────────────\n");
        for (double[] point : report.lorenzCurve) {
            sb.append(String.format("  Pop=%.2f  Degree=%.4f\n", point[0], point[1]));
        }
        sb.append("\n");

        sb.append("── Autonomous Insights ─────────────────────────────\n");
        for (int i = 0; i < report.insights.size(); i++) {
            sb.append(String.format("  %d. %s\n", i + 1, report.insights.get(i)));
        }
        sb.append("\n═══════════════════════════════════════════════════════\n");
        return sb.toString();
    }

    private String giniBar(double gini) {
        int filled = (int)(gini * 20);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < filled; i++) sb.append("█");
        for (int i = filled; i < 20; i++) sb.append("░");
        return sb.toString();
    }

    private String limitList(List<String> list, int max) {
        if (list.isEmpty()) return "(none)";
        if (list.size() <= max) return String.join(", ", list);
        return String.join(", ", list.subList(0, max)) + " (+" + (list.size() - max) + " more)";
    }

    // ==================================================================
    // HTML export
    // ==================================================================

    /**
     * Export the fairness report as a self-contained HTML dashboard.
     *
     * @param report the fairness report
     * @return HTML string
     */
    public String exportHtml(FairnessReport report) {
        StringBuilder html = new StringBuilder();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("<title>Graph Fairness Audit Engine — Dashboard</title>\n");
        html.append("<style>\n");
        html.append("* { margin: 0; padding: 0; box-sizing: border-box; }\n");
        html.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; ");
        html.append("background: #0f0f23; color: #e0e0e0; padding: 20px; }\n");
        html.append("h1 { color: #00d4ff; font-size: 1.8em; margin-bottom: 5px; }\n");
        html.append("h2 { color: #7fdbca; font-size: 1.2em; margin: 20px 0 10px; border-bottom: 1px solid #333; padding-bottom: 5px; }\n");
        html.append(".subtitle { color: #888; font-size: 0.9em; margin-bottom: 20px; }\n");
        html.append(".grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; margin-bottom: 20px; }\n");
        html.append(".card { background: #1a1a2e; border-radius: 8px; padding: 15px; border: 1px solid #333; }\n");
        html.append(".card .label { color: #888; font-size: 0.85em; }\n");
        html.append(".card .value { color: #00d4ff; font-size: 1.6em; font-weight: bold; margin-top: 5px; }\n");
        html.append(".card .value.warn { color: #ff6b6b; }\n");
        html.append(".card .value.ok { color: #51cf66; }\n");
        html.append(".tier-badge { display: inline-block; padding: 8px 20px; border-radius: 20px; font-weight: bold; font-size: 1.1em; margin: 10px 0; }\n");
        html.append(".tier-EXEMPLARY { background: #2d6a4f; color: #b7e4c7; }\n");
        html.append(".tier-FAIR { background: #3a5a40; color: #d8f3dc; }\n");
        html.append(".tier-MODERATE { background: #5c4d1e; color: #ffd43b; }\n");
        html.append(".tier-INEQUITABLE { background: #6a2c2c; color: #ffb4a2; }\n");
        html.append(".tier-CRITICAL { background: #7a1c1c; color: #ff6b6b; }\n");
        html.append(".chart-container { background: #1a1a2e; border-radius: 8px; padding: 15px; border: 1px solid #333; margin-bottom: 15px; }\n");
        html.append("canvas { width: 100% !important; height: 300px !important; }\n");
        html.append(".insight { background: #1a1a2e; border-left: 3px solid #00d4ff; padding: 10px 15px; margin: 8px 0; border-radius: 0 6px 6px 0; }\n");
        html.append(".insight.warning { border-left-color: #ff6b6b; }\n");
        html.append(".insight.recommendation { border-left-color: #ffd43b; }\n");
        html.append("table { width: 100%; border-collapse: collapse; margin: 10px 0; }\n");
        html.append("th { background: #16213e; color: #7fdbca; padding: 8px; text-align: left; font-size: 0.85em; }\n");
        html.append("td { padding: 8px; border-bottom: 1px solid #222; font-size: 0.85em; }\n");
        html.append("tr:hover { background: #16213e; }\n");
        html.append(".bar { display: inline-block; height: 12px; background: linear-gradient(90deg, #00d4ff, #7fdbca); border-radius: 3px; }\n");
        html.append("</style>\n</head>\n<body>\n");

        html.append("<h1>⚖ Graph Fairness Audit Engine</h1>\n");
        html.append("<div class=\"subtitle\">Autonomous fairness analysis — ")
                .append(timestamp).append("</div>\n");

        // Tier badge
        html.append(String.format("<div class=\"tier-badge tier-%s\">%s — %.1f/100</div>\n",
                report.fairnessTier, report.fairnessTier, report.healthScore));

        // Summary cards
        html.append("<div class=\"grid\">\n");
        appendCard(html, "Nodes", String.valueOf(report.nodeCount), "");
        appendCard(html, "Edges", String.valueOf(report.edgeCount), "");
        appendCard(html, "Communities", String.valueOf(report.communityCount), "");
        String healthClass = report.healthScore >= 60 ? "ok" : report.healthScore >= 30 ? "" : "warn";
        appendCard(html, "Fairness Score", String.format("%.1f", report.healthScore), healthClass);
        appendCard(html, "Degree Gini", String.format("%.4f", report.degreeGini),
                report.degreeGini > 0.5 ? "warn" : "");
        appendCard(html, "Centrality Gini", String.format("%.4f", report.centralityGini),
                report.centralityGini > 0.5 ? "warn" : "");
        appendCard(html, "Access Gini", String.format("%.4f", report.accessGini),
                report.accessGini > 0.5 ? "warn" : "");
        appendCard(html, "Community Gini", String.format("%.4f", report.communityGini),
                report.communityGini > 0.4 ? "warn" : "");
        appendCard(html, "Resource Deserts", String.valueOf(report.resourceDesertCount),
                report.resourceDesertCount > 0 ? "warn" : "ok");
        html.append("</div>\n");

        // Lorenz curve chart
        html.append("<div class=\"chart-container\">\n");
        html.append("<h2>Lorenz Curve (Degree Distribution)</h2>\n");
        html.append("<canvas id=\"lorenzChart\"></canvas>\n");
        html.append("</div>\n");

        // Interventions table
        if (!report.interventions.isEmpty()) {
            html.append("<h2>Suggested Interventions</h2>\n");
            html.append("<table><tr><th>#</th><th>Source</th><th>Target</th><th>Expected Improvement</th></tr>\n");
            for (int i = 0; i < report.interventions.size(); i++) {
                Intervention iv = report.interventions.get(i);
                html.append(String.format("<tr><td>%d</td><td>%s</td><td>%s</td><td>%.4f</td></tr>\n",
                        i + 1, escapeHtml(iv.source), escapeHtml(iv.target), iv.expectedImprovement));
            }
            html.append("</table>\n");
        }

        // Insights
        html.append("<h2>Autonomous Insights</h2>\n");
        for (String insight : report.insights) {
            String cls = "insight";
            if (insight.contains("⚠") || insight.contains("CRITICAL")) {
                cls = "insight warning";
            } else if (insight.contains("RECOMMENDATION")) {
                cls = "insight recommendation";
            }
            html.append("<div class=\"").append(cls).append("\">")
                    .append(escapeHtml(insight)).append("</div>\n");
        }

        // JavaScript for Lorenz curve
        html.append("<script>\n");
        html.append("var canvas = document.getElementById('lorenzChart');\n");
        html.append("var ctx = canvas.getContext('2d');\n");
        html.append("canvas.width = canvas.offsetWidth * 2; canvas.height = 600;\n");
        html.append("var w = canvas.width, h = canvas.height;\n");
        html.append("var pad = {top: 30, right: 30, bottom: 50, left: 60};\n");
        html.append("var pw = w - pad.left - pad.right, ph = h - pad.top - pad.bottom;\n");
        html.append("ctx.fillStyle = '#1a1a2e'; ctx.fillRect(0, 0, w, h);\n");

        // Grid
        html.append("ctx.strokeStyle = '#333'; ctx.lineWidth = 1;\n");
        html.append("for (var i = 0; i <= 10; i++) {\n");
        html.append("  var y = pad.top + (ph * i / 10);\n");
        html.append("  ctx.beginPath(); ctx.moveTo(pad.left, y); ctx.lineTo(w - pad.right, y); ctx.stroke();\n");
        html.append("}\n");

        // Line of equality (diagonal)
        html.append("ctx.strokeStyle = '#555'; ctx.lineWidth = 2; ctx.setLineDash([5,5]);\n");
        html.append("ctx.beginPath(); ctx.moveTo(pad.left, h - pad.bottom); ctx.lineTo(w - pad.right, pad.top); ctx.stroke();\n");
        html.append("ctx.setLineDash([]);\n");

        // Lorenz curve data
        html.append("var lorenzData = [");
        boolean first = true;
        for (double[] pt : report.lorenzCurve) {
            if (!first) html.append(",");
            html.append("[").append(pt[0]).append(",").append(pt[1]).append("]");
            first = false;
        }
        html.append("];\n");

        // Draw Lorenz curve
        html.append("ctx.strokeStyle = '#00d4ff'; ctx.lineWidth = 3; ctx.beginPath();\n");
        html.append("for (var j = 0; j < lorenzData.length; j++) {\n");
        html.append("  var x = pad.left + lorenzData[j][0] * pw;\n");
        html.append("  var y = pad.top + (1 - lorenzData[j][1]) * ph;\n");
        html.append("  if (j === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);\n");
        html.append("}\nctx.stroke();\n");

        // Fill area between equality line and Lorenz curve
        html.append("ctx.globalAlpha = 0.15; ctx.fillStyle = '#ff6b6b'; ctx.beginPath();\n");
        html.append("ctx.moveTo(pad.left, h - pad.bottom);\n");
        html.append("for (var j = 0; j < lorenzData.length; j++) {\n");
        html.append("  ctx.lineTo(pad.left + lorenzData[j][0] * pw, pad.top + (1 - lorenzData[j][1]) * ph);\n");
        html.append("}\n");
        html.append("ctx.lineTo(w - pad.right, pad.top);\n");
        html.append("ctx.closePath(); ctx.fill(); ctx.globalAlpha = 1;\n");

        // Points on Lorenz curve
        html.append("ctx.fillStyle = '#00d4ff';\n");
        html.append("for (var j = 0; j < lorenzData.length; j++) {\n");
        html.append("  var x = pad.left + lorenzData[j][0] * pw;\n");
        html.append("  var y = pad.top + (1 - lorenzData[j][1]) * ph;\n");
        html.append("  ctx.beginPath(); ctx.arc(x, y, 4, 0, 2*Math.PI); ctx.fill();\n");
        html.append("}\n");

        // Axes labels
        html.append("ctx.fillStyle = '#888'; ctx.font = '18px sans-serif';\n");
        html.append("ctx.fillText('Population fraction (cumulative)', w/2 - 120, h - 10);\n");
        html.append("ctx.save(); ctx.translate(15, h/2); ctx.rotate(-Math.PI/2);\n");
        html.append("ctx.fillText('Degree share (cumulative)', -80, 0); ctx.restore();\n");

        // Gini annotation
        html.append(String.format("ctx.fillStyle = '#ff6b6b'; ctx.font = 'bold 20px sans-serif';\n"));
        html.append(String.format("ctx.fillText('Gini = %.4f', pad.left + pw * 0.6, pad.top + ph * 0.3);\n",
                report.degreeGini));

        html.append("</script>\n");
        html.append("</body>\n</html>\n");
        return html.toString();
    }

    /**
     * Export the fairness report as HTML and write to a file.
     *
     * @param report   the fairness report
     * @param filePath path to write the HTML file
     * @throws IOException if writing fails
     */
    public void exportHtml(FairnessReport report, String filePath) throws IOException {
        String html = exportHtml(report);
        try (Writer writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            writer.write(html);
        }
    }

    // -- HTML helpers ---------------------------------------------------------

    private void appendCard(StringBuilder html, String label, String value, String cssClass) {
        html.append("<div class=\"card\"><div class=\"label\">").append(escapeHtml(label))
                .append("</div><div class=\"value");
        if (!cssClass.isEmpty()) html.append(" ").append(cssClass);
        html.append("\">").append(escapeHtml(value)).append("</div></div>\n");
    }

    /** Delegates to {@link ExportUtils#escapeHtml(String)} for consistent HTML escaping. */
    private String escapeHtml(String text) {
        return ExportUtils.escapeHtml(text);
    }
}
