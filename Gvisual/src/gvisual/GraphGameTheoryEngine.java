package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GraphGameTheoryEngine - autonomous game-theoretic analysis engine that models
 * networks as cooperative and non-cooperative games, computing Shapley values,
 * Nash equilibria, coalition structures, bargaining power, and strategic
 * position scores.
 *
 * <h3>Six Analysis Engines:</h3>
 * <ol>
 *   <li><b>Shapley Value Calculator</b> - Monte Carlo approximation of each node's
 *       marginal contribution to network connectivity (connected-pairs value
 *       function)</li>
 *   <li><b>Nash Equilibrium Detector</b> - network coordination game solved via
 *       best-response dynamics with convergence detection</li>
 *   <li><b>Coalition Structure Analyzer</b> - greedy merge optimization using
 *       density * size^2 coalition value</li>
 *   <li><b>Bargaining Power Analyzer</b> - sampling-based Banzhaf-style power
 *       index measuring critical membership in winning coalitions</li>
 *   <li><b>Strategic Position Scorer</b> - composite 0-100 score blending all
 *       four game-theoretic metrics</li>
 *   <li><b>Game Theory Dashboard</b> - interactive HTML export with bar charts,
 *       leaderboards, and autonomous insights</li>
 * </ol>
 *
 * <p>Usage:</p>
 * <pre>
 *   GraphGameTheoryEngine engine = new GraphGameTheoryEngine();
 *   GameTheoryReport report = engine.analyze(graph);
 *   System.out.println(engine.toText(report));
 *   String html = engine.exportHtml(report);
 * </pre>
 *
 * @author zalenix
 */
public class GraphGameTheoryEngine {

    // -- Configuration ----------------------------------------------------
    private int shapleyPermutations = 200;
    private int bargainingSamples = 500;
    private int nashMaxIterations = 100;
    private double nashCost = 0.3;
    private Random rng = new Random(42);

    // -- Builder-style setters ---------------------------------------------

    public GraphGameTheoryEngine setShapleyPermutations(int n) {
        this.shapleyPermutations = n; return this;
    }

    public GraphGameTheoryEngine setBargainingSamples(int n) {
        this.bargainingSamples = n; return this;
    }

    public GraphGameTheoryEngine setNashMaxIterations(int n) {
        this.nashMaxIterations = n; return this;
    }

    public GraphGameTheoryEngine setNashCost(double c) {
        this.nashCost = c; return this;
    }

    public GraphGameTheoryEngine setRng(Random rng) {
        this.rng = rng; return this;
    }

    // ==================================================================
    // Inner classes
    // ==================================================================

    /** A pure-strategy Nash equilibrium. */
    public static class NashEquilibrium {
        public final Map<String, Integer> strategies;
        public final double totalPayoff;
        public final int convergenceSteps;

        public NashEquilibrium(Map<String, Integer> strategies, double totalPayoff,
                               int convergenceSteps) {
            this.strategies = Collections.unmodifiableMap(new LinkedHashMap<>(strategies));
            this.totalPayoff = totalPayoff;
            this.convergenceSteps = convergenceSteps;
        }
    }

    /** A coalition of nodes with a computed value. */
    public static class Coalition {
        public final Set<String> members;
        public final double value;
        public final int rank;

        public Coalition(Set<String> members, double value, int rank) {
            this.members = Collections.unmodifiableSet(new LinkedHashSet<>(members));
            this.value = value;
            this.rank = rank;
        }
    }

    /** Full game-theory analysis report. */
    public static class GameTheoryReport {
        public final Map<String, Double> shapleyValues;
        public final Map<String, Double> bargainingPower;
        public final List<NashEquilibrium> nashEquilibria;
        public final List<Coalition> coalitions;
        public final Map<String, Double> strategicPositionScores;
        public final int nodeCount;
        public final int edgeCount;
        public final double gameTheoryHealthScore;
        public final List<String> insights;

        public GameTheoryReport(Map<String, Double> shapleyValues,
                                Map<String, Double> bargainingPower,
                                List<NashEquilibrium> nashEquilibria,
                                List<Coalition> coalitions,
                                Map<String, Double> strategicPositionScores,
                                int nodeCount, int edgeCount,
                                double gameTheoryHealthScore,
                                List<String> insights) {
            this.shapleyValues = Collections.unmodifiableMap(new LinkedHashMap<>(shapleyValues));
            this.bargainingPower = Collections.unmodifiableMap(new LinkedHashMap<>(bargainingPower));
            this.nashEquilibria = Collections.unmodifiableList(new ArrayList<>(nashEquilibria));
            this.coalitions = Collections.unmodifiableList(new ArrayList<>(coalitions));
            this.strategicPositionScores = Collections.unmodifiableMap(new LinkedHashMap<>(strategicPositionScores));
            this.nodeCount = nodeCount;
            this.edgeCount = edgeCount;
            this.gameTheoryHealthScore = gameTheoryHealthScore;
            this.insights = Collections.unmodifiableList(new ArrayList<>(insights));
        }
    }

    // ==================================================================
    // Main analysis
    // ==================================================================

    /**
     * Run full game-theoretic analysis on the given graph.
     *
     * @param graph the network to analyze
     * @return comprehensive game theory report
     */
    public GameTheoryReport analyze(Graph<String, Edge> graph) {
        List<String> vertices = new ArrayList<>(graph.getVertices());
        int n = vertices.size();
        int e = graph.getEdgeCount();

        if (n == 0) {
            return new GameTheoryReport(
                    Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyMap(), 0, 0, 0.0,
                    Collections.singletonList("Empty graph — no game-theoretic analysis possible."));
        }

        // Engine 1: Shapley values
        Map<String, Double> shapley = computeShapleyValues(graph, vertices);

        // Engine 2: Nash equilibria
        List<NashEquilibrium> nash = findNashEquilibria(graph, vertices);

        // Engine 3: Coalition structures
        List<Coalition> coalitions = analyzeCoalitions(graph, vertices);

        // Engine 4: Bargaining power
        Map<String, Double> bargaining = computeBargainingPower(graph, vertices);

        // Engine 5: Strategic position scores
        Map<String, Double> strategic = computeStrategicPositions(
                shapley, bargaining, nash, coalitions, vertices);

        // Health score
        double health = computeHealthScore(shapley, nash, coalitions, n);

        // Autonomous insights
        List<String> insights = generateInsights(shapley, bargaining, nash, coalitions,
                strategic, n, e);

        return new GameTheoryReport(shapley, bargaining, nash, coalitions,
                strategic, n, e, health, insights);
    }

    // ==================================================================
    // Engine 1: Shapley Value Calculator
    // ==================================================================

    private Map<String, Double> computeShapleyValues(Graph<String, Edge> graph,
                                                     List<String> vertices) {
        int n = vertices.size();
        Map<String, Double> shapley = new LinkedHashMap<>();
        for (String v : vertices) shapley.put(v, 0.0);

        if (n == 1) {
            shapley.put(vertices.get(0), 0.0);
            return shapley;
        }

        int perms = Math.min(shapleyPermutations, factorial(n));

        for (int p = 0; p < perms; p++) {
            List<String> perm = new ArrayList<>(vertices);
            Collections.shuffle(perm, rng);

            Set<String> coalition = new LinkedHashSet<>();
            long prevValue = 0;

            for (String v : perm) {
                coalition.add(v);
                long newValue = coalitionConnectedPairs(graph, coalition);
                double marginal = newValue - prevValue;
                shapley.put(v, shapley.get(v) + marginal);
                prevValue = newValue;
            }
        }

        // Average
        for (String v : vertices) {
            shapley.put(v, shapley.get(v) / perms);
        }

        return shapley;
    }

    /** Count connected pairs in the induced subgraph of the coalition. */
    long coalitionConnectedPairs(Graph<String, Edge> graph, Set<String> coalition) {
        if (coalition.size() <= 1) return 0;

        // BFS to find connected components within coalition
        Set<String> visited = new HashSet<>();
        long totalPairs = 0;

        for (String start : coalition) {
            if (visited.contains(start)) continue;
            int componentSize = 0;
            Queue<String> queue = new LinkedList<>();
            queue.add(start);
            visited.add(start);
            while (!queue.isEmpty()) {
                String current = queue.poll();
                componentSize++;
                for (String neighbor : graph.getNeighbors(current)) {
                    if (coalition.contains(neighbor) && !visited.contains(neighbor)) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
            totalPairs += (long) componentSize * (componentSize - 1) / 2;
        }

        return totalPairs;
    }

    // ==================================================================
    // Engine 2: Nash Equilibrium Detector
    // ==================================================================

    private List<NashEquilibrium> findNashEquilibria(Graph<String, Edge> graph,
                                                     List<String> vertices) {
        List<NashEquilibrium> results = new ArrayList<>();

        // Try multiple random initial strategy profiles
        int attempts = Math.min(5, vertices.size());
        Set<String> seenProfiles = new HashSet<>();

        for (int attempt = 0; attempt < attempts; attempt++) {
            Map<String, Integer> strategies = new LinkedHashMap<>();
            for (String v : vertices) {
                strategies.put(v, rng.nextBoolean() ? 1 : 0);
            }

            boolean converged = false;
            int steps = 0;

            for (int iter = 0; iter < nashMaxIterations; iter++) {
                steps++;
                boolean changed = false;

                for (String v : vertices) {
                    int currentStrat = strategies.get(v);
                    double currentPayoff = nashPayoff(graph, v, currentStrat, strategies);
                    int altStrat = 1 - currentStrat;
                    double altPayoff = nashPayoff(graph, v, altStrat, strategies);

                    if (altPayoff > currentPayoff) {
                        strategies.put(v, altStrat);
                        changed = true;
                    }
                }

                if (!changed) {
                    converged = true;
                    break;
                }
            }

            if (converged) {
                String profile = strategies.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> e.getValue().toString())
                        .collect(Collectors.joining(","));

                if (!seenProfiles.contains(profile)) {
                    seenProfiles.add(profile);
                    double totalPayoff = 0;
                    for (String v : vertices) {
                        totalPayoff += nashPayoff(graph, v, strategies.get(v), strategies);
                    }
                    results.add(new NashEquilibrium(strategies, totalPayoff, steps));
                }
            }
        }

        return results;
    }

    private double nashPayoff(Graph<String, Edge> graph, String node,
                              int strategy, Map<String, Integer> strategies) {
        double payoff = 0;
        Collection<String> neighbors = graph.getNeighbors(node);
        if (neighbors != null) {
            for (String neighbor : neighbors) {
                Integer ns = strategies.get(neighbor);
                if (ns != null && ns == strategy) {
                    payoff += 1.0;
                }
            }
        }
        return payoff - nashCost;
    }

    // ==================================================================
    // Engine 3: Coalition Structure Analyzer
    // ==================================================================

    private List<Coalition> analyzeCoalitions(Graph<String, Edge> graph,
                                              List<String> vertices) {
        // Start with singleton coalitions and precompute their values
        List<Set<String>> coalitions = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (String v : vertices) {
            Set<String> s = new LinkedHashSet<>();
            s.add(v);
            coalitions.add(s);
            values.add(0.0); // singleton value is always 0
        }

        // Greedy merge — cache coalition values to avoid O(C²) recomputation
        boolean merged = true;
        while (merged && coalitions.size() > 1) {
            merged = false;
            double bestGain = 0;
            int bestI = -1, bestJ = -1;
            double bestMergedVal = 0;

            for (int i = 0; i < coalitions.size(); i++) {
                double valI = values.get(i);
                for (int j = i + 1; j < coalitions.size(); j++) {
                    Set<String> merged_ = new LinkedHashSet<>(coalitions.get(i));
                    merged_.addAll(coalitions.get(j));
                    double mergedVal = coalitionValue(graph, merged_);
                    double gain = mergedVal - valI - values.get(j);
                    if (gain > bestGain) {
                        bestGain = gain;
                        bestI = i;
                        bestJ = j;
                        bestMergedVal = mergedVal;
                    }
                }
            }

            if (bestI >= 0 && bestGain > 0) {
                Set<String> newCoalition = new LinkedHashSet<>(coalitions.get(bestI));
                newCoalition.addAll(coalitions.get(bestJ));
                // Remove higher index first to preserve lower index
                coalitions.remove(bestJ);
                values.remove(bestJ);
                coalitions.remove(bestI);
                values.remove(bestI);
                coalitions.add(newCoalition);
                values.add(bestMergedVal);
                merged = true;
            }
        }

        // Sort by cached value descending and assign ranks
        Integer[] indices = new Integer[coalitions.size()];
        for (int i = 0; i < indices.length; i++) indices[i] = i;
        Arrays.sort(indices, (a, b) -> Double.compare(values.get(b), values.get(a)));

        List<Coalition> result = new ArrayList<>();
        for (int rank = 0; rank < indices.length; rank++) {
            int idx = indices[rank];
            result.add(new Coalition(coalitions.get(idx), values.get(idx), rank + 1));
        }

        return result;
    }

    double coalitionValue(Graph<String, Edge> graph, Set<String> members) {
        if (members.size() <= 1) return 0;
        int edgeCount = 0;
        for (String v : members) {
            Collection<String> neighbors = graph.getNeighbors(v);
            if (neighbors != null) {
                for (String n : neighbors) {
                    if (members.contains(n)) edgeCount++;
                }
            }
        }
        edgeCount /= 2; // undirected
        int sz = members.size();
        double maxEdges = (double) sz * (sz - 1) / 2;
        double density = maxEdges > 0 ? edgeCount / maxEdges : 0;
        return density * sz * sz;
    }

    // ==================================================================
    // Engine 4: Bargaining Power Analyzer
    // ==================================================================

    private Map<String, Double> computeBargainingPower(Graph<String, Edge> graph,
                                                       List<String> vertices) {
        int n = vertices.size();
        Map<String, Integer> criticalCount = new LinkedHashMap<>();
        for (String v : vertices) criticalCount.put(v, 0);

        double threshold = n / 2.0;

        for (int s = 0; s < bargainingSamples; s++) {
            Set<String> coalition = new LinkedHashSet<>();
            for (String v : vertices) {
                if (rng.nextBoolean()) coalition.add(v);
            }

            if (coalition.size() <= threshold) continue;
            if (!isCoalitionConnected(graph, coalition)) continue;

            // This coalition wins — check who is critical
            for (String v : new ArrayList<>(coalition)) {
                coalition.remove(v);
                boolean stillWins = coalition.size() > threshold
                        && isCoalitionConnected(graph, coalition);
                if (!stillWins) {
                    criticalCount.put(v, criticalCount.get(v) + 1);
                }
                coalition.add(v);
            }
        }

        // Normalize to 0-1
        int maxCritical = criticalCount.values().stream()
                .mapToInt(Integer::intValue).max().orElse(1);
        if (maxCritical == 0) maxCritical = 1;

        Map<String, Double> power = new LinkedHashMap<>();
        for (String v : vertices) {
            power.put(v, (double) criticalCount.get(v) / maxCritical);
        }
        return power;
    }

    private boolean isCoalitionConnected(Graph<String, Edge> graph, Set<String> coalition) {
        if (coalition.isEmpty()) return false;
        if (coalition.size() == 1) return true;

        String start = coalition.iterator().next();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            Collection<String> neighbors = graph.getNeighbors(current);
            if (neighbors != null) {
                for (String neighbor : neighbors) {
                    if (coalition.contains(neighbor) && !visited.contains(neighbor)) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
        }

        return visited.size() == coalition.size();
    }

    // ==================================================================
    // Engine 5: Strategic Position Scorer
    // ==================================================================

    private Map<String, Double> computeStrategicPositions(
            Map<String, Double> shapley, Map<String, Double> bargaining,
            List<NashEquilibrium> nash, List<Coalition> coalitions,
            List<String> vertices) {

        // Normalize Shapley to 0-1
        double maxShapley = shapley.values().stream()
                .mapToDouble(Double::doubleValue).max().orElse(1);
        if (maxShapley == 0) maxShapley = 1;

        // Nash frequency: how often node is in majority strategy
        Map<String, Double> nashFreq = new LinkedHashMap<>();
        for (String v : vertices) nashFreq.put(v, 0.5); // default
        if (!nash.isEmpty()) {
            for (String v : vertices) {
                int majorityCount = 0;
                for (NashEquilibrium eq : nash) {
                    int strat = eq.strategies.getOrDefault(v, 0);
                    long count0 = eq.strategies.values().stream().filter(s -> s == 0).count();
                    long count1 = eq.strategies.values().stream().filter(s -> s == 1).count();
                    if ((strat == 0 && count0 >= count1) || (strat == 1 && count1 >= count0)) {
                        majorityCount++;
                    }
                }
                nashFreq.put(v, (double) majorityCount / nash.size());
            }
        }

        // Coalition membership value: max coalition value where node is a member
        Map<String, Double> coalVal = new LinkedHashMap<>();
        double maxCoalVal = 1;
        for (String v : vertices) {
            double best = 0;
            for (Coalition c : coalitions) {
                if (c.members.contains(v) && c.value > best) {
                    best = c.value;
                }
            }
            coalVal.put(v, best);
            if (best > maxCoalVal) maxCoalVal = best;
        }

        // Composite score
        Map<String, Double> scores = new LinkedHashMap<>();
        for (String v : vertices) {
            double s = (shapley.get(v) / maxShapley) * 0.30
                    + bargaining.get(v) * 0.30
                    + nashFreq.get(v) * 0.20
                    + (coalVal.get(v) / maxCoalVal) * 0.20;
            scores.put(v, Math.min(100.0, s * 100.0));
        }

        return scores;
    }

    // ==================================================================
    // Health score & Insights
    // ==================================================================

    private double computeHealthScore(Map<String, Double> shapley,
                                      List<NashEquilibrium> nash,
                                      List<Coalition> coalitions, int n) {
        double score = 50; // base

        // Shapley diversity: high variance = interesting structure
        if (n > 1) {
            double mean = shapley.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double variance = shapley.values().stream()
                    .mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0);
            double cv = mean > 0 ? Math.sqrt(variance) / mean : 0;
            score += Math.min(20, cv * 20);
        }

        // Nash convergence bonus
        if (!nash.isEmpty()) score += 15;

        // Coalition structure complexity
        if (coalitions.size() > 1 && coalitions.size() < n) score += 15;

        return Math.min(100, Math.max(0, score));
    }

    private List<String> generateInsights(Map<String, Double> shapley,
                                          Map<String, Double> bargaining,
                                          List<NashEquilibrium> nash,
                                          List<Coalition> coalitions,
                                          Map<String, Double> strategic,
                                          int n, int edges) {
        List<String> insights = new ArrayList<>();

        // Top Shapley node
        shapley.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(e ->
                insights.add(String.format("Node '%s' has highest Shapley value (%.2f), " +
                        "making it the most critical for network connectivity.", e.getKey(), e.getValue())));

        // Bargaining power concentration
        double maxPower = bargaining.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        long highPower = bargaining.values().stream().filter(v -> v > 0.7).count();
        if (highPower <= 2 && n > 3) {
            insights.add(String.format("Bargaining power is concentrated: only %d node(s) " +
                    "hold dominant negotiating position.", highPower));
        }

        // Nash equilibria
        if (nash.isEmpty()) {
            insights.add("No pure-strategy Nash equilibrium found — the network coordination " +
                    "game may require mixed strategies.");
        } else if (nash.size() > 1) {
            insights.add(String.format("%d distinct Nash equilibria found — the network supports " +
                    "multiple stable coordination outcomes.", nash.size()));
        }

        // Coalition structure
        if (coalitions.size() == 1 && n > 1) {
            insights.add("All nodes merged into a single grand coalition — the network is " +
                    "highly cohesive with strong collaborative incentives.");
        } else if (coalitions.size() > 3) {
            insights.add(String.format("Network fragmented into %d coalitions — potential " +
                    "competing interest groups.", coalitions.size()));
        }

        // Strategic position outliers
        double meanStrat = strategic.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
        strategic.entrySet().stream()
                .filter(e -> e.getValue() > meanStrat * 1.5 && e.getValue() > 60)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(1)
                .forEach(e -> insights.add(String.format("Node '%s' is a strategic powerhouse " +
                        "(score %.1f) — high across multiple game-theoretic dimensions.", e.getKey(), e.getValue())));

        return insights;
    }

    // ==================================================================
    // Text report
    // ==================================================================

    /**
     * Generate a human-readable text report.
     */
    public String toText(GameTheoryReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("==========================================================\n");
        sb.append("  GRAPH GAME THEORY ENGINE - Analysis Report\n");
        sb.append("==========================================================\n\n");

        sb.append(String.format("Network: %d nodes, %d edges%n", report.nodeCount, report.edgeCount));
        sb.append(String.format("Game Theory Health Score: %.1f / 100%n%n", report.gameTheoryHealthScore));

        // Shapley values (top 10)
        sb.append("-- Shapley Values (Top 10) --------------------------------\n");
        report.shapleyValues.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> sb.append(String.format("  %-15s  %.4f%n", e.getKey(), e.getValue())));

        // Bargaining power (top 10)
        sb.append("\n-- Bargaining Power (Top 10) -------------------------------\n");
        report.bargainingPower.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> sb.append(String.format("  %-15s  %.4f%n", e.getKey(), e.getValue())));

        // Nash equilibria
        sb.append("\n-- Nash Equilibria -----------------------------------------\n");
        if (report.nashEquilibria.isEmpty()) {
            sb.append("  No pure-strategy Nash equilibrium found.\n");
        } else {
            for (int i = 0; i < report.nashEquilibria.size(); i++) {
                NashEquilibrium eq = report.nashEquilibria.get(i);
                long s0 = eq.strategies.values().stream().filter(s -> s == 0).count();
                long s1 = eq.strategies.values().stream().filter(s -> s == 1).count();
                sb.append(String.format("  Equilibrium #%d: Strategy 0=%d, Strategy 1=%d  " +
                        "| Payoff=%.2f | Steps=%d%n", i + 1, s0, s1, eq.totalPayoff, eq.convergenceSteps));
            }
        }

        // Coalitions
        sb.append("\n-- Coalition Structure -------------------------------------\n");
        for (Coalition c : report.coalitions) {
            sb.append(String.format("  Rank #%d: [%s] - value=%.2f, size=%d%n",
                    c.rank, String.join(", ", c.members), c.value, c.members.size()));
        }

        // Strategic positions (top 10)
        sb.append("\n-- Strategic Position Scores (Top 10) ----------------------\n");
        report.strategicPositionScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> sb.append(String.format("  %-15s  %.1f%n", e.getKey(), e.getValue())));

        // Insights
        sb.append("\n-- Autonomous Insights -------------------------------------\n");
        for (String insight : report.insights) {
            sb.append("  * ").append(insight).append("\n");
        }

        return sb.toString();
    }

    // ==================================================================
    // HTML Dashboard
    // ==================================================================

    /**
     * Export an interactive HTML dashboard.
     */
    public String exportHtml(GameTheoryReport report) {
        StringBuilder h = new StringBuilder();
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        h.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>\n");
        h.append("<title>Graph Game Theory Engine — Dashboard</title>\n");
        h.append("<style>\n");
        h.append("*{margin:0;padding:0;box-sizing:border-box}\n");
        h.append("body{font-family:'Segoe UI',system-ui,sans-serif;background:#0d1117;color:#c9d1d9;padding:24px}\n");
        h.append("h1{color:#58a6ff;font-size:1.8em;margin-bottom:4px}\n");
        h.append("h2{color:#58a6ff;font-size:1.2em;margin:24px 0 12px;border-bottom:1px solid #21262d;padding-bottom:6px}\n");
        h.append(".subtitle{color:#8b949e;font-size:0.9em;margin-bottom:20px}\n");
        h.append(".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:16px;margin-bottom:20px}\n");
        h.append(".card{background:#161b22;border:1px solid #30363d;border-radius:8px;padding:16px}\n");
        h.append(".metric{font-size:2em;font-weight:700;color:#58a6ff}\n");
        h.append(".label{color:#8b949e;font-size:0.85em;margin-top:4px}\n");
        h.append("table{width:100%;border-collapse:collapse;margin:8px 0}\n");
        h.append("th,td{text-align:left;padding:8px 12px;border-bottom:1px solid #21262d}\n");
        h.append("th{color:#8b949e;font-size:0.85em;text-transform:uppercase}\n");
        h.append("td{color:#c9d1d9}\n");
        h.append(".bar-bg{background:#21262d;border-radius:4px;height:18px;position:relative}\n");
        h.append(".bar-fill{height:100%;border-radius:4px;transition:width 0.3s}\n");
        h.append(".insight{background:#161b22;border-left:3px solid #f0883e;padding:10px 14px;margin:8px 0;border-radius:0 6px 6px 0}\n");
        h.append(".score-gauge{display:inline-block;width:80px;height:80px;border-radius:50%;");
        h.append("border:6px solid #30363d;position:relative;text-align:center;line-height:68px;font-size:1.4em;font-weight:700}\n");
        h.append("</style></head><body>\n");

        h.append("<h1>Graph Game Theory Engine</h1>\n");
        h.append("<div class='subtitle'>Autonomous game-theoretic analysis - ").append(ts).append("</div>\n");

        // Summary cards
        h.append("<div class='grid'>\n");
        appendCard(h, String.valueOf(report.nodeCount), "Nodes");
        appendCard(h, String.valueOf(report.edgeCount), "Edges");
        appendCard(h, String.format("%.1f", report.gameTheoryHealthScore), "Health Score");
        appendCard(h, String.valueOf(report.nashEquilibria.size()), "Nash Equilibria");
        appendCard(h, String.valueOf(report.coalitions.size()), "Coalitions");
        h.append("</div>\n");

        // Shapley values bar chart
        h.append("<h2>Shapley Values — Node Connectivity Contribution</h2>\n");
        h.append("<div class='card'><table><tr><th>Node</th><th>Value</th><th style='width:50%'>Distribution</th></tr>\n");
        double maxS = report.shapleyValues.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);
        if (maxS == 0) maxS = 1;
        final double maxShap = maxS;
        report.shapleyValues.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> {
                    double pct = (e.getValue() / maxShap) * 100;
                    h.append(String.format("<tr><td>%s</td><td>%.4f</td><td><div class='bar-bg'>" +
                                    "<div class='bar-fill' style='width:%.1f%%;background:#58a6ff'></div></div></td></tr>\n",
                            esc(e.getKey()), e.getValue(), pct));
                });
        h.append("</table></div>\n");

        // Bargaining power
        h.append("<h2>Bargaining Power Index</h2>\n");
        h.append("<div class='card'><table><tr><th>Node</th><th>Power</th><th style='width:50%'>Strength</th></tr>\n");
        report.bargainingPower.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> {
                    double pct = e.getValue() * 100;
                    h.append(String.format("<tr><td>%s</td><td>%.4f</td><td><div class='bar-bg'>" +
                                    "<div class='bar-fill' style='width:%.1f%%;background:#f0883e'></div></div></td></tr>\n",
                            esc(e.getKey()), e.getValue(), pct));
                });
        h.append("</table></div>\n");

        // Nash equilibria
        h.append("<h2>Nash Equilibria</h2>\n");
        h.append("<div class='card'>\n");
        if (report.nashEquilibria.isEmpty()) {
            h.append("<p style='color:#8b949e'>No pure-strategy Nash equilibrium found.</p>\n");
        } else {
            h.append("<table><tr><th>#</th><th>Strategy 0</th><th>Strategy 1</th><th>Total Payoff</th><th>Steps</th></tr>\n");
            for (int i = 0; i < report.nashEquilibria.size(); i++) {
                NashEquilibrium eq = report.nashEquilibria.get(i);
                long s0 = eq.strategies.values().stream().filter(s -> s == 0).count();
                long s1 = eq.strategies.values().stream().filter(s -> s == 1).count();
                h.append(String.format("<tr><td>%d</td><td>%d</td><td>%d</td><td>%.2f</td><td>%d</td></tr>\n",
                        i + 1, s0, s1, eq.totalPayoff, eq.convergenceSteps));
            }
            h.append("</table>\n");
        }
        h.append("</div>\n");

        // Coalition structure
        h.append("<h2>Coalition Structure</h2>\n");
        h.append("<div class='card'><table><tr><th>Rank</th><th>Members</th><th>Size</th><th>Value</th></tr>\n");
        for (Coalition c : report.coalitions) {
            String members = c.members.size() > 8
                    ? c.members.stream().limit(8).collect(Collectors.joining(", ")) + "..."
                    : String.join(", ", c.members);
            h.append(String.format("<tr><td>#%d</td><td>%s</td><td>%d</td><td>%.2f</td></tr>\n",
                    c.rank, esc(members), c.members.size(), c.value));
        }
        h.append("</table></div>\n");

        // Strategic position leaderboard
        h.append("<h2>Strategic Position Leaderboard</h2>\n");
        h.append("<div class='card'><table><tr><th>Rank</th><th>Node</th><th>Score</th><th style='width:50%'>Power</th></tr>\n");
        List<Map.Entry<String, Double>> sorted = report.strategicPositionScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toList());
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Double> e = sorted.get(i);
            String color = e.getValue() > 70 ? "#3fb950" : e.getValue() > 40 ? "#f0883e" : "#8b949e";
            h.append(String.format("<tr><td>#%d</td><td>%s</td><td>%.1f</td><td><div class='bar-bg'>" +
                            "<div class='bar-fill' style='width:%.1f%%;background:%s'></div></div></td></tr>\n",
                    i + 1, esc(e.getKey()), e.getValue(), e.getValue(), color));
        }
        h.append("</table></div>\n");

        // Insights
        h.append("<h2>Autonomous Insights</h2>\n");
        for (String insight : report.insights) {
            h.append("<div class='insight'>").append(esc(insight)).append("</div>\n");
        }

        h.append("</body></html>");
        return h.toString();
    }

    private void appendCard(StringBuilder h, String value, String label) {
        h.append("<div class='card'><div class='metric'>").append(value)
                .append("</div><div class='label'>").append(label).append("</div></div>\n");
    }

    private String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * Export HTML dashboard to a file.
     */
    public void exportToFile(GameTheoryReport report, String path) throws IOException {
        String html = exportHtml(report);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8)) {
            w.write(html);
        }
    }

    // -- Utility ----------------------------------------------------------

    private int factorial(int n) {
        if (n <= 1) return 1;
        int result = 1;
        for (int i = 2; i <= Math.min(n, 12); i++) result *= i;
        return result;
    }

    // ==================================================================
    // Demo main
    // ==================================================================

    public static void main(String[] args) {
        UndirectedSparseGraph<String, Edge> graph = new UndirectedSparseGraph<>();
        Random r = new Random(42);
        int n = 20;

        for (int i = 0; i < n; i++) graph.addVertex("N" + i);

        // Core clique
        for (int i = 0; i < 5; i++)
            for (int j = i + 1; j < 5; j++)
                graph.addEdge(new Edge("c", "N" + i, "N" + j), "N" + i, "N" + j);

        // Preferential attachment
        for (int i = 5; i < n; i++) {
            int edges = 2 + r.nextInt(2);
            List<String> targets = new ArrayList<>(graph.getVertices());
            for (int e = 0; e < edges && !targets.isEmpty(); e++) {
                double totalDeg = targets.stream().mapToInt(v -> Math.max(1, graph.degree(v))).sum();
                double pick = r.nextDouble() * totalDeg;
                double cumulative = 0;
                String target = targets.get(0);
                for (String v : targets) {
                    cumulative += Math.max(1, graph.degree(v));
                    if (cumulative >= pick) { target = v; break; }
                }
                if (!graph.isNeighbor("N" + i, target)) {
                    graph.addEdge(new Edge("c", "N" + i, target), "N" + i, target);
                }
                targets.remove(target);
            }
        }

        System.out.println("=== Graph Game Theory Engine Demo ===");
        System.out.println("Network: " + graph.getVertexCount() + " nodes, " + graph.getEdgeCount() + " edges");

        GraphGameTheoryEngine engine = new GraphGameTheoryEngine();
        GameTheoryReport report = engine.analyze(graph);

        System.out.println(engine.toText(report));

        try {
            engine.exportToFile(report, "game_theory_dashboard.html");
            System.out.println("\nDashboard exported to game_theory_dashboard.html");
        } catch (IOException e) {
            System.err.println("Failed to export: " + e.getMessage());
        }
    }
}
