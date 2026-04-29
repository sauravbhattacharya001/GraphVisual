package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * NetworkImmunizationPlanner — autonomous epidemic containment engine that
 * simulates SIR (Susceptible-Infected-Recovered) epidemics on graphs, tests
 * multiple immunization strategies, and recommends optimal vaccination plans.
 *
 * <h3>Immunization Strategies:</h3>
 * <ul>
 *   <li><b>Degree-Based</b> — immunize highest-degree nodes first (hub targeting)</li>
 *   <li><b>Betweenness-Based</b> — immunize nodes with highest betweenness centrality</li>
 *   <li><b>PageRank-Based</b> — immunize nodes ranked by PageRank importance</li>
 *   <li><b>Acquaintance</b> — random node's random neighbor (scalable, no global knowledge)</li>
 *   <li><b>K-Shell</b> — immunize from highest k-shell core outward</li>
 *   <li><b>Random</b> — baseline: random node selection</li>
 * </ul>
 *
 * <h3>SIR Epidemic Model:</h3>
 * <ul>
 *   <li>β (beta) — transmission probability per contact per time step</li>
 *   <li>γ (gamma) — recovery probability per time step</li>
 *   <li>Configurable initial infection seeds</li>
 *   <li>Monte Carlo averaging over multiple runs</li>
 * </ul>
 *
 * <h3>Agentic Behavior:</h3>
 * <ul>
 *   <li><b>Autonomous planning</b> — evaluates all strategies and recommends optimal</li>
 *   <li><b>Budget optimization</b> — finds minimum immunization needed to contain outbreak</li>
 *   <li><b>Critical threshold detection</b> — identifies epidemic tipping point</li>
 *   <li><b>Comparative analysis</b> — ranks strategies by effectiveness</li>
 *   <li><b>Interactive dashboard</b> — HTML report with epidemic curves and strategy comparison</li>
 * </ul>
 */
public class NetworkImmunizationPlanner {

    // --- Configuration ---
    private double beta = 0.3;       // transmission probability
    private double gamma = 0.1;      // recovery probability
    private int timeSteps = 50;      // simulation duration
    private int monteCarloRuns = 20; // averaging runs
    private int initialInfected = 3; // seed infections
    private Random rng = new Random(42);

    // --- Epidemic states ---
    private enum State { SUSCEPTIBLE, INFECTED, RECOVERED, IMMUNIZED }

    // --- Strategy enum ---
    public enum Strategy {
        DEGREE("Degree-Based", "Immunize highest-degree hubs"),
        BETWEENNESS("Betweenness-Based", "Immunize highest betweenness centrality nodes"),
        PAGERANK("PageRank-Based", "Immunize highest PageRank nodes"),
        ACQUAINTANCE("Acquaintance", "Random neighbor of random node (no global info)"),
        KSHELL("K-Shell", "Immunize from highest k-shell core"),
        RANDOM("Random", "Baseline random immunization");

        final String displayName;
        final String description;

        Strategy(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    // --- Results ---
    public static class EpidemicResult {
        public final double peakInfected;       // max fraction infected at any step
        public final double totalInfected;      // total fraction ever infected
        public final int peakTime;              // time step of peak
        public final double[] infectedCurve;    // fraction infected at each step
        public final double[] recoveredCurve;   // fraction recovered at each step
        public final int epidemicDuration;      // steps until no infected remain

        public EpidemicResult(double peakInfected, double totalInfected, int peakTime,
                              double[] infectedCurve, double[] recoveredCurve, int epidemicDuration) {
            this.peakInfected = peakInfected;
            this.totalInfected = totalInfected;
            this.peakTime = peakTime;
            this.infectedCurve = infectedCurve;
            this.recoveredCurve = recoveredCurve;
            this.epidemicDuration = epidemicDuration;
        }
    }

    public static class StrategyResult {
        public final Strategy strategy;
        public final double budgetFraction;     // fraction of nodes immunized
        public final EpidemicResult epidemic;
        public final List<String> immunizedNodes;
        public final double effectiveness;      // reduction in total infected vs no immunization

        public StrategyResult(Strategy strategy, double budgetFraction, EpidemicResult epidemic,
                              List<String> immunizedNodes, double effectiveness) {
            this.strategy = strategy;
            this.budgetFraction = budgetFraction;
            this.epidemic = epidemic;
            this.immunizedNodes = immunizedNodes;
            this.effectiveness = effectiveness;
        }
    }

    public static class ImmunizationPlan {
        public final List<StrategyResult> strategyResults;
        public final Strategy recommendedStrategy;
        public final double criticalThreshold;  // min budget fraction to contain epidemic
        public final EpidemicResult baselineEpidemic;
        public final int networkSize;
        public final String timestamp;
        public final Map<Strategy, double[]> budgetSweep; // effectiveness at different budgets

        public ImmunizationPlan(List<StrategyResult> strategyResults, Strategy recommendedStrategy,
                                double criticalThreshold, EpidemicResult baselineEpidemic,
                                int networkSize, Map<Strategy, double[]> budgetSweep) {
            this.strategyResults = strategyResults;
            this.recommendedStrategy = recommendedStrategy;
            this.criticalThreshold = criticalThreshold;
            this.baselineEpidemic = baselineEpidemic;
            this.networkSize = networkSize;
            this.budgetSweep = budgetSweep;
            this.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        }
    }

    // --- Configuration setters ---
    public NetworkImmunizationPlanner setBeta(double beta) { this.beta = beta; return this; }
    public NetworkImmunizationPlanner setGamma(double gamma) { this.gamma = gamma; return this; }
    public NetworkImmunizationPlanner setTimeSteps(int steps) { this.timeSteps = steps; return this; }
    public NetworkImmunizationPlanner setMonteCarloRuns(int runs) { this.monteCarloRuns = runs; return this; }
    public NetworkImmunizationPlanner setInitialInfected(int n) { this.initialInfected = n; return this; }
    public NetworkImmunizationPlanner setSeed(long seed) { this.rng = new Random(seed); return this; }

    /**
     * Run the full autonomous immunization planning pipeline.
     *
     * <p>Performance: precomputes centrality rankings (betweenness, PageRank, K-Shell)
     * once and reuses them across all budget levels and threshold searches, eliminating
     * redundant O(V²+VE) recomputations that previously occurred ~49× per plan() call.</p>
     */
    public ImmunizationPlan plan(Graph<String, Edge> graph, double budgetFraction) {
        int n = graph.getVertexCount();
        if (n == 0) throw new IllegalArgumentException("Graph is empty");

        // Precompute centrality rankings once — avoids redundant O(V²+VE) recomputation
        // across budget sweep (6 levels × 6 strategies) + threshold search (~10 iterations).
        List<String> degreeRanked = computeDegreeRanking(graph);
        List<String> betweennessRanked = computeBetweennessRanking(graph);
        List<String> pageRankRanked = computePageRankRanking(graph, 0.85, 30);
        List<String> kShellRanked = computeKShellRanking(graph);
        Map<Strategy, List<String>> rankings = new EnumMap<>(Strategy.class);
        rankings.put(Strategy.DEGREE, degreeRanked);
        rankings.put(Strategy.BETWEENNESS, betweennessRanked);
        rankings.put(Strategy.PAGERANK, pageRankRanked);
        rankings.put(Strategy.KSHELL, kShellRanked);

        // 1. Run baseline epidemic (no immunization)
        EpidemicResult baseline = runEpidemic(graph, Collections.emptySet());

        // 2. Evaluate each strategy at the given budget
        List<StrategyResult> results = new ArrayList<>();
        for (Strategy strategy : Strategy.values()) {
            List<String> immunized = selectNodesFromRankings(graph, strategy, budgetFraction, rankings);
            Set<String> immunizedSet = new HashSet<>(immunized);
            EpidemicResult epidemic = runEpidemic(graph, immunizedSet);
            double effectiveness = baseline.totalInfected > 0
                    ? 1.0 - (epidemic.totalInfected / baseline.totalInfected) : 0.0;
            results.add(new StrategyResult(strategy, budgetFraction, epidemic, immunized, effectiveness));
        }

        // 3. Rank strategies and recommend
        results.sort((a, b) -> Double.compare(b.effectiveness, a.effectiveness));
        Strategy recommended = results.get(0).strategy;

        // 4. Budget sweep for each strategy (5%, 10%, 15%, 20%, 25%, 30%)
        double[] budgetLevels = {0.05, 0.10, 0.15, 0.20, 0.25, 0.30};
        Map<Strategy, double[]> budgetSweep = new LinkedHashMap<>();
        for (Strategy strategy : Strategy.values()) {
            double[] effectivenessArr = new double[budgetLevels.length];
            for (int i = 0; i < budgetLevels.length; i++) {
                List<String> immunized = selectNodesFromRankings(graph, strategy, budgetLevels[i], rankings);
                EpidemicResult ep = runEpidemic(graph, new HashSet<>(immunized));
                effectivenessArr[i] = baseline.totalInfected > 0
                        ? 1.0 - (ep.totalInfected / baseline.totalInfected) : 0.0;
            }
            budgetSweep.put(strategy, effectivenessArr);
        }

        // 5. Find critical threshold for recommended strategy
        double criticalThreshold = findCriticalThresholdFromRankings(graph, recommended, baseline, rankings);

        return new ImmunizationPlan(results, recommended, criticalThreshold, baseline, n, budgetSweep);
    }

    /**
     * Simulate SIR epidemic with Monte Carlo averaging.
     */
    private EpidemicResult runEpidemic(Graph<String, Edge> graph, Set<String> immunized) {
        int n = graph.getVertexCount();
        List<String> vertices = new ArrayList<>(graph.getVertices());
        vertices.removeAll(immunized);

        double[] avgInfected = new double[timeSteps];
        double[] avgRecovered = new double[timeSteps];
        double avgTotalInfected = 0;
        double avgPeakInfected = 0;
        int avgPeakTime = 0;
        int avgDuration = 0;

        for (int run = 0; run < monteCarloRuns; run++) {
            Map<String, State> states = new HashMap<>();
            for (String v : graph.getVertices()) {
                states.put(v, immunized.contains(v) ? State.IMMUNIZED : State.SUSCEPTIBLE);
            }

            // Seed initial infections
            List<String> susceptible = new ArrayList<>(vertices);
            Collections.shuffle(susceptible, rng);
            int seeds = Math.min(initialInfected, susceptible.size());
            for (int i = 0; i < seeds; i++) {
                states.put(susceptible.get(i), State.INFECTED);
            }

            double peakInf = 0;
            int peakT = 0;
            int duration = timeSteps;
            Set<String> everInfected = new HashSet<>();
            for (int i = 0; i < seeds; i++) everInfected.add(susceptible.get(i));

            for (int t = 0; t < timeSteps; t++) {
                int infCount = 0, recCount = 0;
                for (State s : states.values()) {
                    if (s == State.INFECTED) infCount++;
                    else if (s == State.RECOVERED) recCount++;
                }
                double fracInf = (double) infCount / n;
                double fracRec = (double) recCount / n;
                avgInfected[t] += fracInf;
                avgRecovered[t] += fracRec;

                if (fracInf > peakInf) { peakInf = fracInf; peakT = t; }
                if (infCount == 0 && t > 0) { duration = t; break; }

                // SIR transitions
                Map<String, State> next = new HashMap<>(states);
                for (String v : graph.getVertices()) {
                    if (states.get(v) == State.INFECTED) {
                        // Try to infect neighbors
                        for (String neighbor : graph.getNeighbors(v)) {
                            if (states.get(neighbor) == State.SUSCEPTIBLE && rng.nextDouble() < beta) {
                                next.put(neighbor, State.INFECTED);
                                everInfected.add(neighbor);
                            }
                        }
                        // Try to recover
                        if (rng.nextDouble() < gamma) {
                            next.put(v, State.RECOVERED);
                        }
                    }
                }
                states = next;
            }

            avgTotalInfected += (double) everInfected.size() / n;
            avgPeakInfected += peakInf;
            avgPeakTime += peakT;
            avgDuration += duration;
        }

        // Average
        for (int t = 0; t < timeSteps; t++) {
            avgInfected[t] /= monteCarloRuns;
            avgRecovered[t] /= monteCarloRuns;
        }

        return new EpidemicResult(
                avgPeakInfected / monteCarloRuns,
                avgTotalInfected / monteCarloRuns,
                avgPeakTime / monteCarloRuns,
                avgInfected, avgRecovered,
                avgDuration / monteCarloRuns);
    }

    /**
     * Select nodes to immunize using precomputed rankings.
     * Rankings are computed once per plan() call, eliminating redundant centrality computations.
     */
    private List<String> selectNodesFromRankings(Graph<String, Edge> graph, Strategy strategy,
                                                  double budgetFraction, Map<Strategy, List<String>> rankings) {
        int n = graph.getVertexCount();
        int budget = Math.max(1, (int) (n * budgetFraction));

        switch (strategy) {
            case DEGREE:
            case BETWEENNESS:
            case PAGERANK:
            case KSHELL:
                // All deterministic strategies use precomputed ranking — O(1) subList
                List<String> ranked = rankings.get(strategy);
                return new ArrayList<>(ranked.subList(0, Math.min(budget, ranked.size())));
            case ACQUAINTANCE:
                return selectByAcquaintance(graph, budget);
            case RANDOM:
                List<String> vertices = new ArrayList<>(graph.getVertices());
                Collections.shuffle(vertices, rng);
                return vertices.subList(0, Math.min(budget, vertices.size()));
            default:
                return Collections.emptyList();
        }
    }

    // --- Precomputed ranking builders (called once per plan()) ---

    private List<String> computeDegreeRanking(Graph<String, Edge> graph) {
        List<String> vertices = new ArrayList<>(graph.getVertices());
        vertices.sort((a, b) -> Integer.compare(graph.degree(b), graph.degree(a)));
        return vertices;
    }

    private List<String> computeBetweennessRanking(Graph<String, Edge> graph) {
        Map<String, Double> betweenness = computeBetweenness(graph);
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(betweenness.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return sorted.stream().map(Map.Entry::getKey).collect(Collectors.toList());
    }

    private List<String> computePageRankRanking(Graph<String, Edge> graph, double damping, int iterations) {
        Map<String, Double> pr = computePageRank(graph, damping, iterations);
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(pr.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return sorted.stream().map(Map.Entry::getKey).collect(Collectors.toList());
    }

    private List<String> computeKShellRanking(Graph<String, Edge> graph) {
        Map<String, Integer> kshell = computeKShell(graph);
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(kshell.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return sorted.stream().map(Map.Entry::getKey).collect(Collectors.toList());
    }

    private List<String> selectByAcquaintance(Graph<String, Edge> graph, int budget) {
        List<String> vertices = new ArrayList<>(graph.getVertices());
        Set<String> selected = new HashSet<>();
        int attempts = 0;
        while (selected.size() < budget && attempts < budget * 10) {
            String randomNode = vertices.get(rng.nextInt(vertices.size()));
            Collection<String> neighbors = graph.getNeighbors(randomNode);
            if (!neighbors.isEmpty()) {
                List<String> neighborList = new ArrayList<>(neighbors);
                selected.add(neighborList.get(rng.nextInt(neighborList.size())));
            }
            attempts++;
        }
        return new ArrayList<>(selected);
    }

    /**
     * Find the minimum budget fraction needed to contain the epidemic (total infected < 10%).
     * Uses precomputed rankings to avoid recomputing centrality metrics.
     */
    private double findCriticalThresholdFromRankings(Graph<String, Edge> graph, Strategy strategy,
                                                     EpidemicResult baseline, Map<Strategy, List<String>> rankings) {
        double threshold = 0.30; // default if not found
        for (double budget = 0.05; budget <= 0.50; budget += 0.05) {
            List<String> immunized = selectNodesFromRankings(graph, strategy, budget, rankings);
            EpidemicResult ep = runEpidemic(graph, new HashSet<>(immunized));
            if (ep.totalInfected < 0.10) {
                threshold = budget;
                break;
            }
        }
        return threshold;
    }

    // --- Centrality computations ---

    private Map<String, Double> computeBetweenness(Graph<String, Edge> graph) {
        Map<String, Double> betweenness = new HashMap<>();
        for (String v : graph.getVertices()) betweenness.put(v, 0.0);

        for (String s : graph.getVertices()) {
            // BFS from s
            Map<String, Integer> dist = new HashMap<>();
            Map<String, Double> sigma = new HashMap<>();
            Map<String, List<String>> pred = new HashMap<>();
            Queue<String> queue = new LinkedList<>();
            Deque<String> stack = new ArrayDeque<>();

            for (String v : graph.getVertices()) {
                dist.put(v, -1);
                sigma.put(v, 0.0);
                pred.put(v, new ArrayList<>());
            }
            dist.put(s, 0);
            sigma.put(s, 1.0);
            queue.add(s);

            while (!queue.isEmpty()) {
                String v = queue.poll();
                stack.push(v);
                for (String w : graph.getNeighbors(v)) {
                    if (dist.get(w) == -1) {
                        dist.put(w, dist.get(v) + 1);
                        queue.add(w);
                    }
                    if (dist.get(w) == dist.get(v) + 1) {
                        sigma.put(w, sigma.get(w) + sigma.get(v));
                        pred.get(w).add(v);
                    }
                }
            }

            Map<String, Double> delta = new HashMap<>();
            for (String v : graph.getVertices()) delta.put(v, 0.0);
            while (!stack.isEmpty()) {
                String w = stack.pop();
                for (String v : pred.get(w)) {
                    delta.put(v, delta.get(v) + (sigma.get(v) / sigma.get(w)) * (1.0 + delta.get(w)));
                }
                if (!w.equals(s)) {
                    betweenness.put(w, betweenness.get(w) + delta.get(w));
                }
            }
        }
        // Normalize for undirected graph
        for (String v : graph.getVertices()) {
            betweenness.put(v, betweenness.get(v) / 2.0);
        }
        return betweenness;
    }

    private Map<String, Double> computePageRank(Graph<String, Edge> graph, double damping, int iterations) {
        Map<String, Double> pr = new HashMap<>();
        int n = graph.getVertexCount();
        double init = 1.0 / n;
        for (String v : graph.getVertices()) pr.put(v, init);

        for (int iter = 0; iter < iterations; iter++) {
            Map<String, Double> newPr = new HashMap<>();
            double danglingSum = 0;
            for (String v : graph.getVertices()) {
                if (graph.degree(v) == 0) danglingSum += pr.get(v);
            }
            for (String v : graph.getVertices()) {
                double sum = 0;
                for (String neighbor : graph.getNeighbors(v)) {
                    sum += pr.get(neighbor) / graph.degree(neighbor);
                }
                newPr.put(v, (1 - damping) / n + damping * (sum + danglingSum / n));
            }
            pr = newPr;
        }
        return pr;
    }

    private Map<String, Integer> computeKShell(Graph<String, Edge> graph) {
        Map<String, Integer> kshell = new HashMap<>();
        Map<String, Integer> degrees = new HashMap<>();
        Set<String> remaining = new HashSet<>(graph.getVertices());

        for (String v : graph.getVertices()) {
            int deg = 0;
            for (String n : graph.getNeighbors(v)) {
                if (remaining.contains(n)) deg++;
            }
            degrees.put(v, deg);
        }

        int k = 0;
        while (!remaining.isEmpty()) {
            boolean found = true;
            while (found) {
                found = false;
                Iterator<String> it = remaining.iterator();
                List<String> toRemove = new ArrayList<>();
                while (it.hasNext()) {
                    String v = it.next();
                    if (degrees.get(v) <= k) {
                        toRemove.add(v);
                        found = true;
                    }
                }
                for (String v : toRemove) {
                    remaining.remove(v);
                    kshell.put(v, k);
                    for (String n : graph.getNeighbors(v)) {
                        if (remaining.contains(n)) {
                            degrees.put(n, degrees.get(n) - 1);
                        }
                    }
                }
            }
            k++;
        }
        return kshell;
    }

    // --- HTML Dashboard Export ---

    /**
     * Export an interactive HTML dashboard showing epidemic curves, strategy comparison,
     * and budget optimization results.
     */
    public String exportHtml(ImmunizationPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html><head><meta charset='UTF-8'>\n");
        sb.append("<title>Network Immunization Planner — Dashboard</title>\n");
        sb.append("<style>\n");
        sb.append("* { margin: 0; padding: 0; box-sizing: border-box; }\n");
        sb.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; ");
        sb.append("background: #0f172a; color: #e2e8f0; padding: 24px; }\n");
        sb.append("h1 { font-size: 1.8rem; margin-bottom: 8px; color: #f8fafc; }\n");
        sb.append("h2 { font-size: 1.3rem; margin: 16px 0 8px; color: #94a3b8; }\n");
        sb.append(".meta { color: #64748b; font-size: 0.85rem; margin-bottom: 24px; }\n");
        sb.append(".grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 16px; margin: 16px 0; }\n");
        sb.append(".card { background: #1e293b; border-radius: 12px; padding: 20px; border: 1px solid #334155; }\n");
        sb.append(".card h3 { color: #f1f5f9; font-size: 1rem; margin-bottom: 12px; }\n");
        sb.append(".metric { font-size: 2rem; font-weight: 700; }\n");
        sb.append(".metric-label { font-size: 0.8rem; color: #64748b; text-transform: uppercase; }\n");
        sb.append(".recommend { background: linear-gradient(135deg, #059669, #047857); padding: 16px 20px; ");
        sb.append("border-radius: 10px; margin: 16px 0; font-weight: 600; }\n");
        sb.append("table { width: 100%; border-collapse: collapse; margin: 12px 0; }\n");
        sb.append("th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid #334155; }\n");
        sb.append("th { color: #94a3b8; font-size: 0.8rem; text-transform: uppercase; }\n");
        sb.append("td { font-size: 0.9rem; }\n");
        sb.append(".bar { height: 8px; background: #334155; border-radius: 4px; overflow: hidden; }\n");
        sb.append(".bar-fill { height: 100%; border-radius: 4px; }\n");
        sb.append(".eff-high { background: #10b981; }\n");
        sb.append(".eff-med { background: #f59e0b; }\n");
        sb.append(".eff-low { background: #ef4444; }\n");
        sb.append("canvas { width: 100%; height: 250px; }\n");
        sb.append("</style>\n</head><body>\n");

        sb.append("<h1>🛡️ Network Immunization Planner</h1>\n");
        sb.append("<p class='meta'>Generated: ").append(plan.timestamp)
          .append(" | Network: ").append(plan.networkSize).append(" nodes")
          .append(" | β=").append(String.format("%.2f", beta))
          .append(" γ=").append(String.format("%.2f", gamma))
          .append("</p>\n");

        // Recommendation banner
        sb.append("<div class='recommend'>✅ Recommended Strategy: <b>")
          .append(plan.recommendedStrategy.displayName).append("</b>")
          .append(" — Critical threshold: ").append(String.format("%.0f%%", plan.criticalThreshold * 100))
          .append(" of nodes need immunization to contain outbreak (&lt;10% infected)</div>\n");

        // Summary cards
        sb.append("<div class='grid'>\n");
        appendCard(sb, "Baseline Peak", String.format("%.1f%%", plan.baselineEpidemic.peakInfected * 100), "#ef4444");
        appendCard(sb, "Baseline Total Infected", String.format("%.1f%%", plan.baselineEpidemic.totalInfected * 100), "#f59e0b");
        appendCard(sb, "Best Strategy Effectiveness",
                String.format("%.1f%%", plan.strategyResults.get(0).effectiveness * 100), "#10b981");
        appendCard(sb, "Epidemic Duration (no vacc.)",
                plan.baselineEpidemic.epidemicDuration + " steps", "#8b5cf6");
        sb.append("</div>\n");

        // Strategy comparison table
        sb.append("<h2>Strategy Comparison (Budget: ").append(String.format("%.0f%%", plan.strategyResults.get(0).budgetFraction * 100)).append(")</h2>\n");
        sb.append("<div class='card'><table>\n");
        sb.append("<tr><th>Rank</th><th>Strategy</th><th>Effectiveness</th><th>Peak Infected</th><th>Total Infected</th><th>Duration</th></tr>\n");
        for (int i = 0; i < plan.strategyResults.size(); i++) {
            StrategyResult sr = plan.strategyResults.get(i);
            String effClass = sr.effectiveness > 0.6 ? "eff-high" : sr.effectiveness > 0.3 ? "eff-med" : "eff-low";
            sb.append("<tr><td>#").append(i + 1).append("</td>");
            sb.append("<td><b>").append(sr.strategy.displayName).append("</b><br><small>").append(sr.strategy.description).append("</small></td>");
            sb.append("<td><div class='bar'><div class='bar-fill ").append(effClass)
              .append("' style='width:").append(String.format("%.0f", sr.effectiveness * 100)).append("%'></div></div>")
              .append(String.format("%.1f%%", sr.effectiveness * 100)).append("</td>");
            sb.append("<td>").append(String.format("%.1f%%", sr.epidemic.peakInfected * 100)).append("</td>");
            sb.append("<td>").append(String.format("%.1f%%", sr.epidemic.totalInfected * 100)).append("</td>");
            sb.append("<td>").append(sr.epidemic.epidemicDuration).append(" steps</td></tr>\n");
        }
        sb.append("</table></div>\n");

        // Budget sweep table
        sb.append("<h2>Budget Optimization (Effectiveness at Different Budgets)</h2>\n");
        sb.append("<div class='card'><table>\n");
        sb.append("<tr><th>Strategy</th><th>5%</th><th>10%</th><th>15%</th><th>20%</th><th>25%</th><th>30%</th></tr>\n");
        for (Map.Entry<Strategy, double[]> entry : plan.budgetSweep.entrySet()) {
            sb.append("<tr><td>").append(entry.getKey().displayName).append("</td>");
            for (double eff : entry.getValue()) {
                String color = eff > 0.6 ? "#10b981" : eff > 0.3 ? "#f59e0b" : "#ef4444";
                sb.append("<td style='color:").append(color).append("'>").append(String.format("%.0f%%", eff * 100)).append("</td>");
            }
            sb.append("</tr>\n");
        }
        sb.append("</table></div>\n");

        // Epidemic curve data (inline chart via canvas)
        sb.append("<h2>Epidemic Curves (Baseline vs Best Strategy)</h2>\n");
        sb.append("<div class='card'><canvas id='epiChart'></canvas></div>\n");
        sb.append("<script>\n");
        sb.append("const canvas = document.getElementById('epiChart');\n");
        sb.append("canvas.width = canvas.parentElement.clientWidth - 40;\n");
        sb.append("canvas.height = 250;\n");
        sb.append("const ctx = canvas.getContext('2d');\n");
        sb.append("const baseInf = ").append(arrayToJs(plan.baselineEpidemic.infectedCurve)).append(";\n");
        sb.append("const bestInf = ").append(arrayToJs(plan.strategyResults.get(0).epidemic.infectedCurve)).append(";\n");
        sb.append("const w = canvas.width, h = canvas.height;\n");
        sb.append("const maxVal = Math.max(...baseInf, ...bestInf, 0.01);\n");
        sb.append("function drawCurve(data, color) {\n");
        sb.append("  ctx.beginPath(); ctx.strokeStyle = color; ctx.lineWidth = 2;\n");
        sb.append("  for(let i=0;i<data.length;i++){const x=i/(data.length-1)*w;const y=h-data[i]/maxVal*h*0.9;if(i===0)ctx.moveTo(x,y);else ctx.lineTo(x,y);}\n");
        sb.append("  ctx.stroke();\n}\n");
        sb.append("ctx.fillStyle='#1e293b';ctx.fillRect(0,0,w,h);\n");
        sb.append("drawCurve(baseInf,'#ef4444');\n");
        sb.append("drawCurve(bestInf,'#10b981');\n");
        sb.append("ctx.font='12px sans-serif';ctx.fillStyle='#ef4444';ctx.fillText('Baseline (no vaccination)',10,20);\n");
        sb.append("ctx.fillStyle='#10b981';ctx.fillText('").append(plan.strategyResults.get(0).strategy.displayName).append("',10,38);\n");
        sb.append("</script>\n");

        // Immunized nodes for top strategy
        StrategyResult best = plan.strategyResults.get(0);
        sb.append("<h2>Priority Immunization Targets (").append(best.strategy.displayName).append(")</h2>\n");
        sb.append("<div class='card'><p style='font-size:0.85rem;color:#94a3b8;'>Top ")
          .append(Math.min(20, best.immunizedNodes.size())).append(" nodes to immunize first:</p><p>");
        for (int i = 0; i < Math.min(20, best.immunizedNodes.size()); i++) {
            if (i > 0) sb.append(", ");
            sb.append("<b>").append(best.immunizedNodes.get(i)).append("</b>");
        }
        sb.append("</p></div>\n");

        sb.append("</body></html>");
        return sb.toString();
    }

    private void appendCard(StringBuilder sb, String label, String value, String color) {
        sb.append("<div class='card'><p class='metric-label'>").append(label).append("</p>");
        sb.append("<p class='metric' style='color:").append(color).append("'>").append(value).append("</p></div>\n");
    }

    private String arrayToJs(double[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format("%.4f", arr[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Save the HTML dashboard to a file.
     */
    public void exportToFile(ImmunizationPlan plan, String filePath) throws IOException {
        String html = exportHtml(plan);
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            writer.write(html);
        }
    }

    // --- Test support ---
    public static void main(String[] args) {
        // Demo: Build a scale-free-ish test network
        Graph<String, Edge> graph = new UndirectedSparseGraph<>();
        Random r = new Random(123);

        // Create 100-node preferential attachment graph
        int n = 100;
        for (int i = 0; i < n; i++) graph.addVertex("N" + i);

        // Seed clique
        for (int i = 0; i < 5; i++)
            for (int j = i + 1; j < 5; j++)
                graph.addEdge(new Edge("c", "N" + i, "N" + j), "N" + i, "N" + j);

        // Preferential attachment
        for (int i = 5; i < n; i++) {
            int edges = 2 + r.nextInt(2);
            List<String> targets = new ArrayList<>(graph.getVertices());
            for (int e = 0; e < edges && !targets.isEmpty(); e++) {
                // Degree-weighted selection
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

        System.out.println("=== Network Immunization Planner Demo ===");
        System.out.println("Network: " + graph.getVertexCount() + " nodes, " + graph.getEdgeCount() + " edges");

        NetworkImmunizationPlanner planner = new NetworkImmunizationPlanner()
                .setBeta(0.3).setGamma(0.1).setTimeSteps(50).setMonteCarloRuns(30);

        ImmunizationPlan plan = planner.plan(graph, 0.15);

        System.out.println("\n--- Baseline Epidemic (No Immunization) ---");
        System.out.printf("  Peak infected: %.1f%% at step %d%n",
                plan.baselineEpidemic.peakInfected * 100, plan.baselineEpidemic.peakTime);
        System.out.printf("  Total infected: %.1f%%%n", plan.baselineEpidemic.totalInfected * 100);
        System.out.printf("  Duration: %d steps%n", plan.baselineEpidemic.epidemicDuration);

        System.out.println("\n--- Strategy Rankings (15% budget) ---");
        for (int i = 0; i < plan.strategyResults.size(); i++) {
            StrategyResult sr = plan.strategyResults.get(i);
            System.out.printf("  #%d %s — effectiveness: %.1f%%, total infected: %.1f%%%n",
                    i + 1, sr.strategy.displayName, sr.effectiveness * 100, sr.epidemic.totalInfected * 100);
        }

        System.out.println("\n✅ Recommended: " + plan.recommendedStrategy.displayName);
        System.out.printf("   Critical threshold: %.0f%% immunization to contain outbreak%n",
                plan.criticalThreshold * 100);

        // Export dashboard
        try {
            planner.exportToFile(plan, "immunization_dashboard.html");
            System.out.println("\n📊 Dashboard exported to immunization_dashboard.html");
        } catch (IOException e) {
            System.err.println("Failed to export: " + e.getMessage());
        }
    }
}
