package gvisual;

import edu.uci.ics.jung.graph.DirectedGraph;
import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GraphInfluenceCampaignPlanner — autonomous strategic influence campaign
 * planning engine that optimizes seed selection, budgets, wave timing,
 * and competitive positioning for network influence maximization.
 *
 * <h3>Seven Analysis Engines:</h3>
 * <ol>
 *   <li><b>Budget-Constrained Seed Selector</b> — CELF-inspired greedy
 *       seed selection with per-node costs and budget limits, maximizing
 *       influence-per-dollar through marginal-gain-to-cost ratios</li>
 *   <li><b>Multi-Wave Campaign Designer</b> — plans sequential seeding
 *       waves with timing optimization, allowing later waves to target
 *       regions unreached by earlier ones</li>
 *   <li><b>Competitive Influence Analyzer</b> — simulates head-to-head
 *       influence campaigns with two competing seed sets, measuring
 *       territory control and contested zones</li>
 *   <li><b>ROI Optimizer</b> — evaluates cost-effectiveness of each seed
 *       node using marginal-gain-per-cost ratios, identifies diminishing
 *       returns breakpoints, and recommends optimal budget allocation</li>
 *   <li><b>Influence Sustainability Analyzer</b> — measures how durable
 *       influence is over time: decay rate, half-life estimation, and
 *       re-seeding recommendations for sustained coverage</li>
 *   <li><b>Insight Generator</b> — autonomous strategic recommendations
 *       including optimal budget, wave count, competitive positioning,
 *       and risk assessment</li>
 *   <li><b>Interactive HTML Dashboard</b> — self-contained HTML export
 *       with seed rankings, wave timeline, competitive map, ROI curve,
 *       sustainability chart, and strategic insights</li>
 * </ol>
 *
 * <p>Usage:</p>
 * <pre>
 *   GraphInfluenceCampaignPlanner planner = new GraphInfluenceCampaignPlanner();
 *   CampaignReport report = planner.analyze(graph);
 *   System.out.println(planner.toText(report));
 *   String html = planner.exportHtml(report);
 * </pre>
 *
 * @author zalenix
 */
public class GraphInfluenceCampaignPlanner {

    // -- Configuration --------------------------------------------------------
    private int monteCarloTrials = 50;
    private double defaultProbability = 0.1;
    private int maxWaves = 3;
    private double totalBudget = Double.MAX_VALUE;
    private int maxSeedsPerWave = 5;
    private long randomSeed = -1;
    private Random rng;

    // -- Data structures ------------------------------------------------------

    /** Seed selection strategy. */
    public enum Strategy {
        GREEDY_MARGINAL,   // Standard greedy marginal gain
        COST_EFFECTIVE,    // Marginal gain / cost ratio
        DEGREE_HEURISTIC,  // Fast degree-based heuristic
        HYBRID             // Combines degree-based filtering with marginal gain
    }

    /** Campaign wave phase. */
    public enum WavePhase {
        INITIAL_PUSH,      // First wave: maximize early reach
        EXPANSION,         // Middle waves: fill gaps
        CONSOLIDATION      // Final wave: reinforce weak areas
    }

    /** Competitive territory status. */
    public enum TerritoryStatus {
        CAMPAIGN_A,   // Controlled by campaign A
        CAMPAIGN_B,   // Controlled by campaign B
        CONTESTED,    // Reached by both campaigns
        UNREACHED     // Not reached by either
    }

    /** A seed node candidate with scoring details. */
    public static class SeedCandidate {
        public String node;
        public double marginalGain;
        public double cost;
        public double efficiency; // gain / cost
        public int rank;
        public int wave;

        public SeedCandidate(String node, double marginalGain, double cost,
                             double efficiency, int rank, int wave) {
            this.node = node;
            this.marginalGain = marginalGain;
            this.cost = cost;
            this.efficiency = efficiency;
            this.rank = rank;
            this.wave = wave;
        }
    }

    /** A campaign wave with seeds and expected outcomes. */
    public static class CampaignWave {
        public int waveNumber;
        public WavePhase phase;
        public List<SeedCandidate> seeds = new ArrayList<>();
        public double expectedReach;
        public double cumulativeReach;
        public double waveCost;
        public double cumulativeCost;
    }

    /** Competitive analysis result. */
    public static class CompetitiveResult {
        public Set<String> campaignASeeds;
        public Set<String> campaignBSeeds;
        public Map<String, TerritoryStatus> territoryMap = new LinkedHashMap<>();
        public int campaignATerritory;
        public int campaignBTerritory;
        public int contested;
        public int unreached;
        public double campaignADominance; // 0-1
        public double campaignBDominance;
    }

    /** ROI analysis for a seed set. */
    public static class ROIAnalysis {
        public List<double[]> roiCurve = new ArrayList<>(); // [seeds, cost, reach, marginalROI]
        public int diminishingReturnsBreakpoint; // seed index where ROI drops sharply
        public double optimalBudget;
        public double peakROI;
        public double overallROI; // total reach / total cost
    }

    /** Influence sustainability metrics. */
    public static class SustainabilityAnalysis {
        public List<double[]> decayCurve = new ArrayList<>(); // [round, activeNodes]
        public double halfLife; // rounds until influence halves
        public double decayRate; // fraction lost per round
        public double sustainabilityScore; // 0-100
        public List<String> reseedTargets = new ArrayList<>(); // nodes to re-seed
    }

    /** Full campaign report. */
    public static class CampaignReport {
        public List<CampaignWave> waves = new ArrayList<>();
        public List<SeedCandidate> allSeeds = new ArrayList<>();
        public CompetitiveResult competitive;
        public ROIAnalysis roi;
        public SustainabilityAnalysis sustainability;
        public List<String> insights = new ArrayList<>();
        public double healthScore; // 0-100
        public int nodesAnalyzed;
        public int edgesAnalyzed;
        public double totalCost;
        public double totalReach;
        public double reachPercentage;
    }

    // -- Configuration setters ------------------------------------------------

    public GraphInfluenceCampaignPlanner setMonteCarloTrials(int trials) {
        this.monteCarloTrials = Math.max(1, trials);
        return this;
    }

    public GraphInfluenceCampaignPlanner setDefaultProbability(double p) {
        this.defaultProbability = Math.max(0.01, Math.min(1.0, p));
        return this;
    }

    public GraphInfluenceCampaignPlanner setMaxWaves(int waves) {
        this.maxWaves = Math.max(1, Math.min(10, waves));
        return this;
    }

    public GraphInfluenceCampaignPlanner setTotalBudget(double budget) {
        this.totalBudget = Math.max(0, budget);
        return this;
    }

    public GraphInfluenceCampaignPlanner setMaxSeedsPerWave(int max) {
        this.maxSeedsPerWave = Math.max(1, Math.min(50, max));
        return this;
    }

    public GraphInfluenceCampaignPlanner setRandomSeed(long seed) {
        this.randomSeed = seed;
        return this;
    }

    // -- Main analysis --------------------------------------------------------

    /**
     * Performs full autonomous campaign planning analysis.
     *
     * @param graph the network graph to plan campaigns for
     * @return a complete campaign report
     */
    public CampaignReport analyze(Graph<String, Edge> graph) {
        if (graph == null) throw new IllegalArgumentException("Graph must not be null");
        rng = (randomSeed >= 0) ? new Random(randomSeed) : new Random();

        CampaignReport report = new CampaignReport();
        List<String> vertices = new ArrayList<>(graph.getVertices());
        report.nodesAnalyzed = vertices.size();
        report.edgesAnalyzed = graph.getEdgeCount();

        if (vertices.isEmpty()) {
            report.healthScore = 0.0;
            report.insights.add("Empty graph — no campaign possible.");
            return report;
        }

        // Pre-cache adjacency for performance
        Map<String, List<String>> neighborCache = buildNeighborCache(graph);

        // Assign node costs (degree-weighted: higher-degree nodes cost more)
        Map<String, Double> nodeCosts = computeNodeCosts(graph, vertices);

        // Engine 1: Budget-Constrained Seed Selection
        List<SeedCandidate> allSeeds = selectSeedsBudgetConstrained(
                graph, vertices, neighborCache, nodeCosts);
        report.allSeeds = allSeeds;

        // Engine 2: Multi-Wave Campaign Design
        designWaves(graph, neighborCache, allSeeds, report);

        // Engine 3: Competitive Influence Analysis
        report.competitive = analyzeCompetition(graph, vertices, neighborCache, allSeeds);

        // Engine 4: ROI Optimization
        report.roi = analyzeROI(graph, neighborCache, allSeeds, nodeCosts);

        // Engine 5: Influence Sustainability Analysis
        Set<String> seedSet = allSeeds.stream().map(s -> s.node).collect(Collectors.toSet());
        report.sustainability = analyzeSustainability(graph, vertices, neighborCache, seedSet);

        // Compute totals
        report.totalCost = allSeeds.stream().mapToDouble(s -> s.cost).sum();
        report.totalReach = estimateSpread(graph, neighborCache,
                allSeeds.stream().map(s -> s.node).collect(Collectors.toSet()));
        report.reachPercentage = vertices.isEmpty() ? 0.0 :
                (report.totalReach / vertices.size()) * 100.0;

        // Health score
        report.healthScore = computeHealthScore(report);

        // Engine 6: Insight Generator
        generateInsights(report, graph, vertices);

        return report;
    }

    // -- Engine 1: Budget-Constrained Seed Selection --------------------------

    /**
     * CELF-inspired greedy seed selection with budget constraints.
     * Selects seeds by best marginal-gain-per-cost ratio until budget runs out.
     */
    private List<SeedCandidate> selectSeedsBudgetConstrained(
            Graph<String, Edge> graph, List<String> vertices,
            Map<String, List<String>> neighborCache,
            Map<String, Double> nodeCosts) {

        List<SeedCandidate> selected = new ArrayList<>();
        Set<String> seedSet = new LinkedHashSet<>();
        double remainingBudget = totalBudget;
        int maxTotal = maxSeedsPerWave * maxWaves;
        int rank = 0;

        // Pre-filter: only consider top nodes by degree as candidates
        // to keep O(candidates * trials) manageable
        int candidateLimit = Math.min(vertices.size(), Math.max(50, maxTotal * 5));
        List<String> candidates = vertices.stream()
                .sorted((a, b) -> Integer.compare(graph.degree(b), graph.degree(a)))
                .limit(candidateLimit)
                .collect(Collectors.toList());

        while (selected.size() < maxTotal && !candidates.isEmpty()) {
            String bestNode = null;
            double bestEfficiency = -1;
            double bestGain = 0;

            // Compute baseline spread
            double baseSpread = seedSet.isEmpty() ? 0 :
                    estimateSpread(graph, neighborCache, seedSet);

            Iterator<String> it = candidates.iterator();
            while (it.hasNext()) {
                String candidate = it.next();
                double cost = nodeCosts.getOrDefault(candidate, 1.0);
                if (cost > remainingBudget) {
                    it.remove();
                    continue;
                }

                Set<String> trial = new LinkedHashSet<>(seedSet);
                trial.add(candidate);
                double gain = estimateSpread(graph, neighborCache, trial) - baseSpread;
                double efficiency = cost > 0 ? gain / cost : gain;

                if (efficiency > bestEfficiency) {
                    bestEfficiency = efficiency;
                    bestGain = gain;
                    bestNode = candidate;
                }
            }

            if (bestNode == null || bestGain < 0.01) break;

            rank++;
            double cost = nodeCosts.getOrDefault(bestNode, 1.0);
            selected.add(new SeedCandidate(bestNode, bestGain, cost,
                    bestEfficiency, rank, 1)); // wave assigned later
            seedSet.add(bestNode);
            remainingBudget -= cost;
            candidates.remove(bestNode);
        }

        return selected;
    }

    // -- Engine 2: Multi-Wave Campaign Design ---------------------------------

    /**
     * Distributes selected seeds across waves based on strategic phases.
     */
    private void designWaves(Graph<String, Edge> graph,
                             Map<String, List<String>> neighborCache,
                             List<SeedCandidate> allSeeds,
                             CampaignReport report) {
        if (allSeeds.isEmpty()) return;

        int numWaves = Math.min(maxWaves,
                (int) Math.ceil((double) allSeeds.size() / maxSeedsPerWave));
        numWaves = Math.max(1, numWaves);

        int seedsPerWave = (int) Math.ceil((double) allSeeds.size() / numWaves);
        Set<String> cumulativeSeeds = new LinkedHashSet<>();
        double cumulativeCost = 0;

        for (int w = 0; w < numWaves; w++) {
            CampaignWave wave = new CampaignWave();
            wave.waveNumber = w + 1;

            if (w == 0) wave.phase = WavePhase.INITIAL_PUSH;
            else if (w == numWaves - 1) wave.phase = WavePhase.CONSOLIDATION;
            else wave.phase = WavePhase.EXPANSION;

            int start = w * seedsPerWave;
            int end = Math.min(start + seedsPerWave, allSeeds.size());

            for (int i = start; i < end; i++) {
                SeedCandidate seed = allSeeds.get(i);
                seed.wave = wave.waveNumber;
                wave.seeds.add(seed);
                cumulativeSeeds.add(seed.node);
                wave.waveCost += seed.cost;
            }

            cumulativeCost += wave.waveCost;
            wave.cumulativeCost = cumulativeCost;

            // Estimate reach for this wave
            wave.cumulativeReach = estimateSpread(graph, neighborCache, cumulativeSeeds);
            double prevReach = w > 0 ? report.waves.get(w - 1).cumulativeReach : 0;
            wave.expectedReach = wave.cumulativeReach - prevReach;

            report.waves.add(wave);
        }
    }

    // -- Engine 3: Competitive Influence Analysis -----------------------------

    /**
     * Simulates two competing campaigns: A uses the planned seeds,
     * B uses a degree-based heuristic counter-strategy.
     */
    private CompetitiveResult analyzeCompetition(
            Graph<String, Edge> graph, List<String> vertices,
            Map<String, List<String>> neighborCache,
            List<SeedCandidate> campaignASeeds) {

        CompetitiveResult result = new CompetitiveResult();

        // Campaign A: our planned seeds
        Set<String> aSeeds = campaignASeeds.stream()
                .map(s -> s.node).collect(Collectors.toCollection(LinkedHashSet::new));
        result.campaignASeeds = aSeeds;

        // Campaign B: degree-based competitor (picks highest degree nodes not in A)
        Set<String> bSeeds = vertices.stream()
                .filter(v -> !aSeeds.contains(v))
                .sorted((a, b) -> Integer.compare(graph.degree(b), graph.degree(a)))
                .limit(aSeeds.size())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        result.campaignBSeeds = bSeeds;

        // Run Monte Carlo for both campaigns simultaneously
        Map<String, int[]> reachCounts = new LinkedHashMap<>();
        for (String v : vertices) reachCounts.put(v, new int[2]); // [reachedByA, reachedByB]

        for (int trial = 0; trial < monteCarloTrials; trial++) {
            Set<String> reachedA = simulateIC(graph, neighborCache, aSeeds);
            Set<String> reachedB = simulateIC(graph, neighborCache, bSeeds);

            for (String v : vertices) {
                int[] counts = reachCounts.get(v);
                if (reachedA.contains(v)) counts[0]++;
                if (reachedB.contains(v)) counts[1]++;
            }
        }

        // Classify territory
        double threshold = monteCarloTrials * 0.3; // reached in 30%+ of trials
        for (String v : vertices) {
            int[] counts = reachCounts.get(v);
            boolean byA = counts[0] > threshold;
            boolean byB = counts[1] > threshold;

            TerritoryStatus status;
            if (byA && byB) status = TerritoryStatus.CONTESTED;
            else if (byA) status = TerritoryStatus.CAMPAIGN_A;
            else if (byB) status = TerritoryStatus.CAMPAIGN_B;
            else status = TerritoryStatus.UNREACHED;

            result.territoryMap.put(v, status);
        }

        result.campaignATerritory = (int) result.territoryMap.values().stream()
                .filter(s -> s == TerritoryStatus.CAMPAIGN_A).count();
        result.campaignBTerritory = (int) result.territoryMap.values().stream()
                .filter(s -> s == TerritoryStatus.CAMPAIGN_B).count();
        result.contested = (int) result.territoryMap.values().stream()
                .filter(s -> s == TerritoryStatus.CONTESTED).count();
        result.unreached = (int) result.territoryMap.values().stream()
                .filter(s -> s == TerritoryStatus.UNREACHED).count();

        int total = vertices.size();
        result.campaignADominance = total > 0 ?
                (double) (result.campaignATerritory + result.contested) / total : 0;
        result.campaignBDominance = total > 0 ?
                (double) (result.campaignBTerritory + result.contested) / total : 0;

        return result;
    }

    // -- Engine 4: ROI Optimization -------------------------------------------

    /**
     * Builds an ROI curve showing incremental return for each additional seed.
     */
    private ROIAnalysis analyzeROI(Graph<String, Edge> graph,
                                   Map<String, List<String>> neighborCache,
                                   List<SeedCandidate> seeds,
                                   Map<String, Double> nodeCosts) {
        ROIAnalysis roi = new ROIAnalysis();
        if (seeds.isEmpty()) {
            roi.diminishingReturnsBreakpoint = 0;
            roi.optimalBudget = 0;
            roi.peakROI = 0;
            roi.overallROI = 0;
            return roi;
        }

        Set<String> seedSet = new LinkedHashSet<>();
        double cumCost = 0;
        double prevReach = 0;
        double peakMarginalROI = 0;
        int breakpoint = seeds.size();
        boolean foundBreakpoint = false;

        for (int i = 0; i < seeds.size(); i++) {
            SeedCandidate seed = seeds.get(i);
            seedSet.add(seed.node);
            cumCost += seed.cost;

            double reach = estimateSpread(graph, neighborCache, seedSet);
            double marginalReach = reach - prevReach;
            double marginalROI = seed.cost > 0 ? marginalReach / seed.cost : marginalReach;

            roi.roiCurve.add(new double[]{i + 1, cumCost, reach, marginalROI});

            if (marginalROI > peakMarginalROI) {
                peakMarginalROI = marginalROI;
            }

            // Detect diminishing returns: when ROI drops below 30% of peak
            if (!foundBreakpoint && i > 0 && peakMarginalROI > 0 &&
                    marginalROI < peakMarginalROI * 0.3) {
                breakpoint = i;
                foundBreakpoint = true;
            }

            prevReach = reach;
        }

        roi.diminishingReturnsBreakpoint = breakpoint;
        roi.peakROI = peakMarginalROI;

        // Optimal budget = cost up to breakpoint
        double optBudget = 0;
        for (int i = 0; i < breakpoint && i < seeds.size(); i++) {
            optBudget += seeds.get(i).cost;
        }
        roi.optimalBudget = optBudget;
        roi.overallROI = cumCost > 0 ? prevReach / cumCost : 0;

        return roi;
    }

    // -- Engine 5: Influence Sustainability Analysis --------------------------

    /**
     * Measures how durable influence is by simulating multi-round spread
     * with decay (nodes can recover and become susceptible again).
     */
    private SustainabilityAnalysis analyzeSustainability(
            Graph<String, Edge> graph, List<String> vertices,
            Map<String, List<String>> neighborCache,
            Set<String> seeds) {
        SustainabilityAnalysis result = new SustainabilityAnalysis();
        if (seeds.isEmpty() || vertices.isEmpty()) {
            result.halfLife = 0;
            result.decayRate = 1.0;
            result.sustainabilityScore = 0;
            return result;
        }

        int maxRounds = 20;
        double recoveryRate = 0.15; // 15% chance of recovery per round

        // Run multiple trials and average
        double[] avgActive = new double[maxRounds + 1];
        int trials = Math.max(10, monteCarloTrials / 2);

        for (int trial = 0; trial < trials; trial++) {
            Map<String, Boolean> active = new LinkedHashMap<>();
            for (String v : vertices) active.put(v, false);
            for (String s : seeds) if (active.containsKey(s)) active.put(s, true);

            Set<String> currentlyActive = new LinkedHashSet<>(seeds);
            currentlyActive.retainAll(active.keySet());
            avgActive[0] += currentlyActive.size();

            for (int round = 1; round <= maxRounds; round++) {
                Set<String> toActivate = new LinkedHashSet<>();
                Set<String> toDeactivate = new LinkedHashSet<>();

                // Spread
                for (String node : currentlyActive) {
                    List<String> neighbors = neighborCache.getOrDefault(node,
                            Collections.emptyList());
                    for (String neighbor : neighbors) {
                        if (!active.get(neighbor) && rng.nextDouble() < defaultProbability) {
                            toActivate.add(neighbor);
                        }
                    }
                }

                // Recovery/decay (non-seed nodes can lose influence)
                for (String node : currentlyActive) {
                    if (!seeds.contains(node) && rng.nextDouble() < recoveryRate) {
                        toDeactivate.add(node);
                    }
                }

                for (String n : toActivate) {
                    active.put(n, true);
                    currentlyActive.add(n);
                }
                for (String n : toDeactivate) {
                    active.put(n, false);
                    currentlyActive.remove(n);
                }

                avgActive[round] += currentlyActive.size();
            }
        }

        // Average across trials
        for (int r = 0; r <= maxRounds; r++) {
            avgActive[r] /= trials;
            result.decayCurve.add(new double[]{r, avgActive[r]});
        }

        // Compute half-life
        double peakActive = 0;
        int peakRound = 0;
        for (int r = 0; r <= maxRounds; r++) {
            if (avgActive[r] > peakActive) {
                peakActive = avgActive[r];
                peakRound = r;
            }
        }

        double halfTarget = peakActive / 2.0;
        result.halfLife = maxRounds; // default if never halves
        for (int r = peakRound + 1; r <= maxRounds; r++) {
            if (avgActive[r] <= halfTarget) {
                result.halfLife = r - peakRound;
                break;
            }
        }

        // Decay rate: average per-round loss after peak
        if (peakRound < maxRounds && peakActive > 0) {
            double endActive = avgActive[maxRounds];
            int span = maxRounds - peakRound;
            result.decayRate = span > 0 ? 1.0 - Math.pow(endActive / peakActive, 1.0 / span) : 0;
        } else {
            result.decayRate = 0;
        }

        // Sustainability score: higher half-life and lower decay = better
        double halfLifeScore = Math.min(result.halfLife / 10.0, 1.0) * 50;
        double decayScore = (1.0 - Math.min(result.decayRate, 1.0)) * 50;
        result.sustainabilityScore = halfLifeScore + decayScore;

        // Identify nodes for re-seeding: high-degree nodes that weren't reached well
        Set<String> reachedSet = simulateIC(graph, neighborCache, seeds);
        result.reseedTargets = vertices.stream()
                .filter(v -> !reachedSet.contains(v))
                .sorted((a, b) -> Integer.compare(graph.degree(b), graph.degree(a)))
                .limit(5)
                .collect(Collectors.toList());

        return result;
    }

    // -- Simulation helpers ---------------------------------------------------

    /**
     * Estimates average spread via Monte Carlo IC simulations.
     */
    private double estimateSpread(Graph<String, Edge> graph,
                                  Map<String, List<String>> neighborCache,
                                  Set<String> seeds) {
        if (seeds.isEmpty()) return 0;
        long total = 0;
        for (int trial = 0; trial < monteCarloTrials; trial++) {
            total += simulateIC(graph, neighborCache, seeds).size();
        }
        return (double) total / monteCarloTrials;
    }

    /**
     * Single Independent Cascade simulation returning set of reached nodes.
     */
    private Set<String> simulateIC(Graph<String, Edge> graph,
                                   Map<String, List<String>> neighborCache,
                                   Set<String> seeds) {
        Set<String> active = new LinkedHashSet<>();
        for (String s : seeds) {
            if (graph.containsVertex(s)) active.add(s);
        }
        Set<String> frontier = new LinkedHashSet<>(active);

        while (!frontier.isEmpty()) {
            Set<String> nextFrontier = new LinkedHashSet<>();
            for (String node : frontier) {
                List<String> neighbors = neighborCache.getOrDefault(node,
                        Collections.emptyList());
                for (String neighbor : neighbors) {
                    if (!active.contains(neighbor)) {
                        double prob = getEdgeProbability(graph, node, neighbor);
                        if (rng.nextDouble() < prob) {
                            active.add(neighbor);
                            nextFrontier.add(neighbor);
                        }
                    }
                }
            }
            frontier = nextFrontier;
        }
        return active;
    }

    private double getEdgeProbability(Graph<String, Edge> graph,
                                      String from, String to) {
        Edge e = graph.findEdge(from, to);
        if (e != null) {
            double w = e.getWeight();
            if (w > 0 && w <= 1.0) return w;
        }
        return defaultProbability;
    }

    private Map<String, List<String>> buildNeighborCache(Graph<String, Edge> graph) {
        boolean directed = graph instanceof DirectedGraph;
        Map<String, List<String>> cache = new HashMap<>();
        for (String node : graph.getVertices()) {
            Collection<String> nbrs = directed
                    ? graph.getSuccessors(node)
                    : graph.getNeighbors(node);
            cache.put(node, nbrs != null
                    ? new ArrayList<>(nbrs)
                    : Collections.emptyList());
        }
        return cache;
    }

    private Map<String, Double> computeNodeCosts(Graph<String, Edge> graph,
                                                  List<String> vertices) {
        Map<String, Double> costs = new LinkedHashMap<>();
        if (vertices.isEmpty()) return costs;

        int maxDegree = vertices.stream()
                .mapToInt(graph::degree).max().orElse(1);
        maxDegree = Math.max(maxDegree, 1);

        for (String v : vertices) {
            // Cost scales with degree: higher-degree nodes are more "expensive"
            double normalizedDegree = (double) graph.degree(v) / maxDegree;
            costs.put(v, 1.0 + normalizedDegree * 4.0); // cost range: 1.0 to 5.0
        }
        return costs;
    }

    // -- Health score ---------------------------------------------------------

    private double computeHealthScore(CampaignReport report) {
        double score = 0;

        // Reach component (0-40): how much of the network can we reach
        score += Math.min(report.reachPercentage / 100.0, 1.0) * 40;

        // ROI component (0-20): efficiency of our seed selection
        if (report.roi != null && report.roi.peakROI > 0) {
            score += Math.min(report.roi.overallROI / report.roi.peakROI, 1.0) * 20;
        }

        // Sustainability component (0-20)
        if (report.sustainability != null) {
            score += report.sustainability.sustainabilityScore / 100.0 * 20;
        }

        // Competitive component (0-20): dominance over competitor
        if (report.competitive != null) {
            score += report.competitive.campaignADominance * 20;
        }

        return Math.min(100, Math.max(0, score));
    }

    // -- Engine 6: Insight Generator ------------------------------------------

    private void generateInsights(CampaignReport report,
                                  Graph<String, Edge> graph,
                                  List<String> vertices) {
        if (vertices.isEmpty()) return;

        // Reach insight
        if (report.reachPercentage > 80) {
            report.insights.add("🎯 Excellent reach: campaign covers " +
                    String.format("%.1f%%", report.reachPercentage) +
                    " of the network with " + report.allSeeds.size() + " seeds.");
        } else if (report.reachPercentage > 50) {
            report.insights.add("📊 Good reach at " +
                    String.format("%.1f%%", report.reachPercentage) +
                    ". Consider adding " + (report.sustainability != null ?
                    report.sustainability.reseedTargets.size() : "more") +
                    " seeds to fill gaps.");
        } else {
            report.insights.add("⚠️ Limited reach (" +
                    String.format("%.1f%%", report.reachPercentage) +
                    "). Network may have isolated communities requiring separate seeding.");
        }

        // ROI insight
        if (report.roi != null && report.roi.diminishingReturnsBreakpoint < report.allSeeds.size()) {
            report.insights.add("💰 Diminishing returns detected after seed #" +
                    report.roi.diminishingReturnsBreakpoint +
                    ". Optimal budget: " + String.format("%.1f", report.roi.optimalBudget) +
                    " (saves " + String.format("%.1f",
                    report.totalCost - report.roi.optimalBudget) + " vs full plan).");
        }

        // Sustainability insight
        if (report.sustainability != null) {
            if (report.sustainability.halfLife <= 3) {
                report.insights.add("🔋 Short influence half-life (" +
                        String.format("%.1f", report.sustainability.halfLife) +
                        " rounds). Consider periodic re-seeding or choosing " +
                        "higher-centrality seed nodes for better persistence.");
            } else if (report.sustainability.halfLife >= 10) {
                report.insights.add("✅ Excellent sustainability: influence persists for " +
                        String.format("%.1f", report.sustainability.halfLife) +
                        "+ rounds — campaign is self-sustaining.");
            }
        }

        // Competitive insight
        if (report.competitive != null) {
            if (report.competitive.campaignADominance > report.competitive.campaignBDominance) {
                report.insights.add("🏆 Campaign outperforms degree-based competitor (" +
                        String.format("%.0f%%", report.competitive.campaignADominance * 100) +
                        " vs " +
                        String.format("%.0f%%", report.competitive.campaignBDominance * 100) +
                        " network influence).");
            } else {
                report.insights.add("⚔️ Competitor's degree-based strategy is stronger. " +
                        "Consider prioritizing hub nodes to counter.");
            }

            if (report.competitive.contested > 0) {
                report.insights.add("🤝 " + report.competitive.contested +
                        " nodes are contested territory — first-mover advantage is crucial.");
            }
        }

        // Wave strategy insight
        if (report.waves.size() > 1) {
            CampaignWave firstWave = report.waves.get(0);
            double firstWaveReachPct = vertices.size() > 0 ?
                    (firstWave.cumulativeReach / vertices.size()) * 100 : 0;
            report.insights.add("📢 First wave (" + firstWave.seeds.size() +
                    " seeds) achieves " + String.format("%.1f%%", firstWaveReachPct) +
                    " reach. " + (report.waves.size() - 1) + " follow-up waves fill gaps.");
        }

        // Structural insight
        double avgDegree = vertices.size() > 0 ?
                (double) graph.getEdgeCount() * 2 / vertices.size() : 0;
        if (avgDegree < 2) {
            report.insights.add("🕸️ Sparse network (avg degree " +
                    String.format("%.1f", avgDegree) +
                    ") — influence propagation is inherently limited. " +
                    "Focus seeds on bridge nodes connecting components.");
        }
    }

    // -- Text output ----------------------------------------------------------

    /**
     * Generates a plain-text summary of the campaign report.
     */
    public String toText(CampaignReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║         GRAPH INFLUENCE CAMPAIGN PLANNER                   ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════╝\n\n");

        sb.append(String.format("Network: %d nodes, %d edges\n",
                report.nodesAnalyzed, report.edgesAnalyzed));
        sb.append(String.format("Campaign Health Score: %.0f/100\n", report.healthScore));
        sb.append(String.format("Total Seeds: %d | Total Cost: %.1f | Reach: %.1f%%\n\n",
                report.allSeeds.size(), report.totalCost, report.reachPercentage));

        // Seed rankings
        sb.append("── SEED RANKINGS ──────────────────────────────────────────\n");
        for (SeedCandidate seed : report.allSeeds) {
            sb.append(String.format("  #%d: %-12s  gain=%.2f  cost=%.1f  efficiency=%.2f  wave=%d\n",
                    seed.rank, seed.node, seed.marginalGain,
                    seed.cost, seed.efficiency, seed.wave));
        }
        sb.append("\n");

        // Waves
        if (!report.waves.isEmpty()) {
            sb.append("── CAMPAIGN WAVES ─────────────────────────────────────────\n");
            for (CampaignWave wave : report.waves) {
                sb.append(String.format("  Wave %d [%s]: %d seeds, cost=%.1f, " +
                                "reach=%.1f (cumulative=%.1f)\n",
                        wave.waveNumber, wave.phase, wave.seeds.size(),
                        wave.waveCost, wave.expectedReach, wave.cumulativeReach));
            }
            sb.append("\n");
        }

        // Competitive analysis
        if (report.competitive != null) {
            CompetitiveResult c = report.competitive;
            sb.append("── COMPETITIVE ANALYSIS ───────────────────────────────────\n");
            sb.append(String.format("  Campaign A territory: %d nodes (%.0f%% dominance)\n",
                    c.campaignATerritory, c.campaignADominance * 100));
            sb.append(String.format("  Campaign B territory: %d nodes (%.0f%% dominance)\n",
                    c.campaignBTerritory, c.campaignBDominance * 100));
            sb.append(String.format("  Contested: %d | Unreached: %d\n",
                    c.contested, c.unreached));
            sb.append("\n");
        }

        // ROI
        if (report.roi != null) {
            ROIAnalysis r = report.roi;
            sb.append("── ROI ANALYSIS ───────────────────────────────────────────\n");
            sb.append(String.format("  Peak ROI: %.2f | Overall ROI: %.2f\n",
                    r.peakROI, r.overallROI));
            sb.append(String.format("  Diminishing returns at seed #%d\n",
                    r.diminishingReturnsBreakpoint));
            sb.append(String.format("  Optimal budget: %.1f\n", r.optimalBudget));
            sb.append("\n");
        }

        // Sustainability
        if (report.sustainability != null) {
            SustainabilityAnalysis s = report.sustainability;
            sb.append("── SUSTAINABILITY ─────────────────────────────────────────\n");
            sb.append(String.format("  Half-life: %.1f rounds | Decay rate: %.3f/round\n",
                    s.halfLife, s.decayRate));
            sb.append(String.format("  Sustainability score: %.0f/100\n", s.sustainabilityScore));
            if (!s.reseedTargets.isEmpty()) {
                sb.append("  Re-seed targets: " + String.join(", ", s.reseedTargets) + "\n");
            }
            sb.append("\n");
        }

        // Insights
        if (!report.insights.isEmpty()) {
            sb.append("── AUTONOMOUS INSIGHTS ────────────────────────────────────\n");
            for (String insight : report.insights) {
                sb.append("  " + insight + "\n");
            }
        }

        return sb.toString();
    }

    // -- HTML Dashboard -------------------------------------------------------

    /**
     * Exports an interactive HTML dashboard.
     */
    public String exportHtml(CampaignReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
        sb.append("<title>Influence Campaign Planner</title>");
        sb.append("<style>");
        sb.append("*{margin:0;padding:0;box-sizing:border-box}");
        sb.append("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;");
        sb.append("background:#0d1117;color:#c9d1d9;padding:20px}");
        sb.append(".header{text-align:center;padding:30px;background:linear-gradient(135deg,#1a1b26,#24283b);");
        sb.append("border-radius:12px;margin-bottom:20px;border:1px solid #30363d}");
        sb.append(".header h1{font-size:24px;color:#58a6ff;margin-bottom:8px}");
        sb.append(".score{font-size:48px;font-weight:bold;margin:10px 0}");
        sb.append(".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(400px,1fr));gap:16px}");
        sb.append(".card{background:#161b22;border:1px solid #30363d;border-radius:10px;padding:20px}");
        sb.append(".card h2{color:#58a6ff;font-size:16px;margin-bottom:12px;border-bottom:1px solid #21262d;padding-bottom:8px}");
        sb.append("table{width:100%;border-collapse:collapse;font-size:13px}");
        sb.append("th{text-align:left;padding:6px 8px;color:#8b949e;border-bottom:1px solid #21262d}");
        sb.append("td{padding:6px 8px;border-bottom:1px solid #161b22}");
        sb.append("tr:hover{background:#1c2128}");
        sb.append(".bar{height:20px;border-radius:4px;margin:4px 0;position:relative;background:#21262d}");
        sb.append(".bar-fill{height:100%;border-radius:4px;transition:width .3s}");
        sb.append(".bar-label{position:absolute;right:6px;top:2px;font-size:11px;color:#c9d1d9}");
        sb.append(".insight{padding:10px 14px;margin:6px 0;background:#1c2128;border-radius:6px;");
        sb.append("border-left:3px solid #58a6ff;font-size:13px}");
        sb.append(".tag{display:inline-block;padding:2px 8px;border-radius:4px;font-size:11px;margin:2px}");
        sb.append(".tag-a{background:#1f3d2c;color:#3fb950}");
        sb.append(".tag-b{background:#3d1f1f;color:#f85149}");
        sb.append(".tag-contested{background:#3d361f;color:#d29922}");
        sb.append(".tag-unreached{background:#21262d;color:#8b949e}");
        sb.append(".wave-badge{display:inline-block;padding:2px 10px;border-radius:10px;font-size:11px;");
        sb.append("background:#1f2937;color:#93c5fd;border:1px solid #374151}");
        sb.append(".chart-bar{display:flex;align-items:center;margin:3px 0}");
        sb.append(".chart-label{width:60px;font-size:12px;text-align:right;padding-right:8px;color:#8b949e}");
        sb.append(".chart-track{flex:1;height:16px;background:#21262d;border-radius:3px;overflow:hidden}");
        sb.append(".chart-fill{height:100%;border-radius:3px}");
        sb.append(".chart-value{width:50px;font-size:12px;padding-left:8px;color:#c9d1d9}");
        sb.append("</style></head><body>");

        // Header with score
        String scoreColor = report.healthScore >= 70 ? "#3fb950" :
                report.healthScore >= 40 ? "#d29922" : "#f85149";
        sb.append("<div class=\"header\">");
        sb.append("<h1>🎯 Graph Influence Campaign Planner</h1>");
        sb.append("<div class=\"score\" style=\"color:").append(scoreColor).append("\">");
        sb.append(String.format("%.0f", report.healthScore)).append("<span style=\"font-size:20px;color:#8b949e\">/100</span></div>");
        sb.append("<div style=\"color:#8b949e\">").append(report.nodesAnalyzed).append(" nodes · ");
        sb.append(report.edgesAnalyzed).append(" edges · ");
        sb.append(report.allSeeds.size()).append(" seeds · ");
        sb.append(String.format("%.1f%% reach", report.reachPercentage)).append("</div>");
        sb.append("</div>");

        sb.append("<div class=\"grid\">");

        // Seed Rankings card
        sb.append("<div class=\"card\"><h2>🌱 Seed Rankings</h2><table>");
        sb.append("<tr><th>#</th><th>Node</th><th>Gain</th><th>Cost</th><th>Efficiency</th><th>Wave</th></tr>");
        for (SeedCandidate seed : report.allSeeds) {
            sb.append("<tr><td>").append(seed.rank).append("</td>");
            sb.append("<td><strong>").append(escHtml(seed.node)).append("</strong></td>");
            sb.append("<td>").append(String.format("%.2f", seed.marginalGain)).append("</td>");
            sb.append("<td>").append(String.format("%.1f", seed.cost)).append("</td>");
            sb.append("<td>").append(String.format("%.2f", seed.efficiency)).append("</td>");
            sb.append("<td><span class=\"wave-badge\">W").append(seed.wave).append("</span></td></tr>");
        }
        sb.append("</table></div>");

        // Campaign Waves card
        if (!report.waves.isEmpty()) {
            sb.append("<div class=\"card\"><h2>📢 Campaign Waves</h2>");
            double maxReach = report.waves.stream()
                    .mapToDouble(w -> w.cumulativeReach).max().orElse(1);
            for (CampaignWave wave : report.waves) {
                sb.append("<div style=\"margin-bottom:12px\">");
                sb.append("<div style=\"font-weight:bold;margin-bottom:4px\">Wave ")
                        .append(wave.waveNumber).append(" — ").append(wave.phase).append("</div>");
                sb.append("<div style=\"font-size:12px;color:#8b949e\">")
                        .append(wave.seeds.size()).append(" seeds · cost ")
                        .append(String.format("%.1f", wave.waveCost)).append("</div>");
                double pct = maxReach > 0 ? (wave.cumulativeReach / maxReach) * 100 : 0;
                sb.append("<div class=\"bar\"><div class=\"bar-fill\" style=\"width:")
                        .append(String.format("%.1f", pct))
                        .append("%;background:linear-gradient(90deg,#1f6feb,#58a6ff)\"></div>");
                sb.append("<div class=\"bar-label\">").append(String.format("%.1f", wave.cumulativeReach))
                        .append(" reach</div></div></div>");
            }
            sb.append("</div>");
        }

        // Competitive Analysis card
        if (report.competitive != null) {
            CompetitiveResult c = report.competitive;
            int total = report.nodesAnalyzed;
            sb.append("<div class=\"card\"><h2>⚔️ Competitive Analysis</h2>");
            sb.append("<div style=\"display:flex;gap:8px;margin-bottom:12px;flex-wrap:wrap\">");
            sb.append("<span class=\"tag tag-a\">Campaign A: ").append(c.campaignATerritory).append("</span>");
            sb.append("<span class=\"tag tag-b\">Competitor B: ").append(c.campaignBTerritory).append("</span>");
            sb.append("<span class=\"tag tag-contested\">Contested: ").append(c.contested).append("</span>");
            sb.append("<span class=\"tag tag-unreached\">Unreached: ").append(c.unreached).append("</span>");
            sb.append("</div>");

            // Dominance bars
            sb.append("<div class=\"chart-bar\"><div class=\"chart-label\">You</div>");
            sb.append("<div class=\"chart-track\"><div class=\"chart-fill\" style=\"width:")
                    .append(String.format("%.1f", c.campaignADominance * 100))
                    .append("%;background:#3fb950\"></div></div>");
            sb.append("<div class=\"chart-value\">").append(String.format("%.0f%%", c.campaignADominance * 100)).append("</div></div>");

            sb.append("<div class=\"chart-bar\"><div class=\"chart-label\">Rival</div>");
            sb.append("<div class=\"chart-track\"><div class=\"chart-fill\" style=\"width:")
                    .append(String.format("%.1f", c.campaignBDominance * 100))
                    .append("%;background:#f85149\"></div></div>");
            sb.append("<div class=\"chart-value\">").append(String.format("%.0f%%", c.campaignBDominance * 100)).append("</div></div>");
            sb.append("</div>");
        }

        // ROI Analysis card
        if (report.roi != null && !report.roi.roiCurve.isEmpty()) {
            ROIAnalysis r = report.roi;
            sb.append("<div class=\"card\"><h2>💰 ROI Analysis</h2>");
            sb.append("<div style=\"font-size:13px;margin-bottom:10px\">");
            sb.append("Peak ROI: <strong>").append(String.format("%.2f", r.peakROI)).append("</strong> · ");
            sb.append("Overall: <strong>").append(String.format("%.2f", r.overallROI)).append("</strong> · ");
            sb.append("Optimal budget: <strong>").append(String.format("%.1f", r.optimalBudget)).append("</strong></div>");

            // ROI curve as bars
            double maxROI = r.roiCurve.stream().mapToDouble(d -> d[3]).max().orElse(1);
            for (double[] point : r.roiCurve) {
                double pct = maxROI > 0 ? (point[3] / maxROI) * 100 : 0;
                String color = (int) point[0] <= r.diminishingReturnsBreakpoint ? "#3fb950" : "#f85149";
                sb.append("<div class=\"chart-bar\"><div class=\"chart-label\">S").append((int) point[0]).append("</div>");
                sb.append("<div class=\"chart-track\"><div class=\"chart-fill\" style=\"width:")
                        .append(String.format("%.1f", pct))
                        .append("%;background:").append(color).append("\"></div></div>");
                sb.append("<div class=\"chart-value\">").append(String.format("%.2f", point[3])).append("</div></div>");
            }
            sb.append("</div>");
        }

        // Sustainability card
        if (report.sustainability != null) {
            SustainabilityAnalysis s = report.sustainability;
            sb.append("<div class=\"card\"><h2>🔋 Influence Sustainability</h2>");
            sb.append("<div style=\"font-size:13px;margin-bottom:10px\">");
            sb.append("Half-life: <strong>").append(String.format("%.1f", s.halfLife)).append(" rounds</strong> · ");
            sb.append("Decay: <strong>").append(String.format("%.1f%%", s.decayRate * 100)).append("/round</strong> · ");
            sb.append("Score: <strong>").append(String.format("%.0f", s.sustainabilityScore)).append("/100</strong></div>");

            // Decay curve as bars
            if (!s.decayCurve.isEmpty()) {
                double maxActive = s.decayCurve.stream().mapToDouble(d -> d[1]).max().orElse(1);
                for (double[] point : s.decayCurve) {
                    double pct = maxActive > 0 ? (point[1] / maxActive) * 100 : 0;
                    String color = pct > 50 ? "#3fb950" : pct > 25 ? "#d29922" : "#f85149";
                    sb.append("<div class=\"chart-bar\"><div class=\"chart-label\">R").append((int) point[0]).append("</div>");
                    sb.append("<div class=\"chart-track\"><div class=\"chart-fill\" style=\"width:")
                            .append(String.format("%.1f", pct))
                            .append("%;background:").append(color).append("\"></div></div>");
                    sb.append("<div class=\"chart-value\">").append(String.format("%.1f", point[1])).append("</div></div>");
                }
            }

            if (!s.reseedTargets.isEmpty()) {
                sb.append("<div style=\"margin-top:10px;font-size:12px;color:#8b949e\">Re-seed targets: ");
                sb.append(String.join(", ", s.reseedTargets)).append("</div>");
            }
            sb.append("</div>");
        }

        // Insights card
        if (!report.insights.isEmpty()) {
            sb.append("<div class=\"card\" style=\"grid-column:1/-1\"><h2>🧠 Autonomous Insights</h2>");
            for (String insight : report.insights) {
                sb.append("<div class=\"insight\">").append(escHtml(insight)).append("</div>");
            }
            sb.append("</div>");
        }

        sb.append("</div>"); // grid

        // Footer
        sb.append("<div style=\"text-align:center;margin-top:20px;color:#484f58;font-size:12px\">");
        sb.append("Generated by GraphInfluenceCampaignPlanner · GraphVisual · ");
        sb.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        sb.append("</div>");

        sb.append("</body></html>");
        return sb.toString();
    }

    /**
     * Exports the HTML dashboard to a file.
     */
    public void exportHtmlToFile(CampaignReport report, String path) throws IOException {
        try (Writer w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(path), StandardCharsets.UTF_8))) {
            w.write(exportHtml(report));
        }
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
