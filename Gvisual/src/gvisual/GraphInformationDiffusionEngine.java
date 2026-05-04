package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GraphInformationDiffusionEngine - autonomous information cascade simulation
 * and analysis engine. Models how information, innovations, or behaviours spread
 * through a network using classic diffusion models and derives actionable
 * intelligence about spreading dynamics.
 *
 * <h3>Seven Analysis Engines</h3>
 * <ol>
 *   <li><b>Independent Cascade (IC) Simulator</b> — each active node activates
 *       each inactive neighbour with probability p. Monte Carlo averaged.</li>
 *   <li><b>Linear Threshold (LT) Simulator</b> - each node activates when the
 *       fraction of active neighbours exceeds its random threshold.</li>
 *   <li><b>Superspreader Identifier</b> - composite scoring (degree centrality +
 *       k-shell + single-seed IC cascade) with tier classification.</li>
 *   <li><b>Cascade Phase Detector</b> - identifies Ignition / Early Spread /
 *       Exponential Growth / Peak / Saturation / Extinction phases from
 *       activation velocity and acceleration.</li>
 *   <li><b>Tipping Point Estimator</b> - sweeps seed-set sizes to find the
 *       critical mass for &gt;50% network activation.</li>
 *   <li><b>Viral Potential Scorer</b> - composite 0-100 network-level score
 *       based on degree, clustering, diameter, heterogeneity, and
 *       giant-component fraction.</li>
 *   <li><b>Insight Generator</b> — autonomous insights about spreading dynamics,
 *       superspreader concentration, bottlenecks, and containment.</li>
 * </ol>
 *
 * <p>Usage:</p>
 * <pre>
 *   GraphInformationDiffusionEngine engine = new GraphInformationDiffusionEngine();
 *   DiffusionReport report = engine.analyze(graph);
 *   System.out.println(engine.toText(report));
 *   String html = engine.exportHtml(report);
 * </pre>
 *
 * @author zalenix
 */
public class GraphInformationDiffusionEngine {

    // -- Configuration --------------------------------------------------------
    private double spreadProbability = 0.1;
    private int monteCarloTrials = 50;
    private Random rng = new Random(42);
    private Set<String> seedNodes = null; // null → auto-select highest-degree

    // -- Builder-style setters ------------------------------------------------

    public GraphInformationDiffusionEngine setSpreadProbability(double p) {
        this.spreadProbability = p; return this;
    }

    public GraphInformationDiffusionEngine setMonteCarloTrials(int n) {
        this.monteCarloTrials = n; return this;
    }

    public GraphInformationDiffusionEngine setRandomSeed(long seed) {
        this.rng = new Random(seed); return this;
    }

    public GraphInformationDiffusionEngine setRng(Random rng) {
        this.rng = rng; return this;
    }

    public GraphInformationDiffusionEngine setSeedNodes(Set<String> seeds) {
        this.seedNodes = seeds == null ? null : new LinkedHashSet<>(seeds); return this;
    }

    // ==================================================================
    // Inner classes
    // ==================================================================

    /** Spreading tier classification. */
    public enum SpreaderTier { SUPER, HIGH, MEDIUM, LOW, MINIMAL }

    /** Full diffusion analysis report. */
    public static class DiffusionReport {
        public final double cascadeSizeIC;
        public final double cascadeSizeLT;
        public final Map<String, Double> nodeActivationProbIC;
        public final Map<String, Double> nodeActivationProbLT;
        public final Map<String, Double> superspreaderScores;
        public final Map<String, String> superspreaderTiers;
        public final List<String> topSpreaders;
        public final List<String> cascadePhases;
        public final Map<String, Integer> phaseTransitions;
        public final double tippingPointPct;
        public final double tippingPointConfidence;
        public final double viralScore;
        public final String viralTier;
        public final double healthScore;
        public final List<String> insights;
        public final int nodeCount;
        public final int edgeCount;

        public DiffusionReport(double cascadeSizeIC, double cascadeSizeLT,
                               Map<String, Double> nodeActivationProbIC,
                               Map<String, Double> nodeActivationProbLT,
                               Map<String, Double> superspreaderScores,
                               Map<String, String> superspreaderTiers,
                               List<String> topSpreaders,
                               List<String> cascadePhases,
                               Map<String, Integer> phaseTransitions,
                               double tippingPointPct, double tippingPointConfidence,
                               double viralScore, String viralTier,
                               double healthScore, List<String> insights,
                               int nodeCount, int edgeCount) {
            this.cascadeSizeIC = cascadeSizeIC;
            this.cascadeSizeLT = cascadeSizeLT;
            this.nodeActivationProbIC = Collections.unmodifiableMap(new LinkedHashMap<>(nodeActivationProbIC));
            this.nodeActivationProbLT = Collections.unmodifiableMap(new LinkedHashMap<>(nodeActivationProbLT));
            this.superspreaderScores = Collections.unmodifiableMap(new LinkedHashMap<>(superspreaderScores));
            this.superspreaderTiers = Collections.unmodifiableMap(new LinkedHashMap<>(superspreaderTiers));
            this.topSpreaders = Collections.unmodifiableList(new ArrayList<>(topSpreaders));
            this.cascadePhases = Collections.unmodifiableList(new ArrayList<>(cascadePhases));
            this.phaseTransitions = Collections.unmodifiableMap(new LinkedHashMap<>(phaseTransitions));
            this.tippingPointPct = tippingPointPct;
            this.tippingPointConfidence = tippingPointConfidence;
            this.viralScore = viralScore;
            this.viralTier = viralTier;
            this.healthScore = healthScore;
            this.insights = Collections.unmodifiableList(new ArrayList<>(insights));
            this.nodeCount = nodeCount;
            this.edgeCount = edgeCount;
        }
    }

    // ==================================================================
    // Main analysis
    // ==================================================================

    public DiffusionReport analyze(Graph<String, Edge> graph) {
        List<String> vertices = new ArrayList<>(graph.getVertices());
        int n = vertices.size();
        int e = graph.getEdgeCount();

        if (n == 0) {
            return emptyReport(0, 0);
        }
        if (n == 1) {
            String v = vertices.get(0);
            Map<String, Double> single = new LinkedHashMap<>();
            single.put(v, 1.0);
            Map<String, Double> scores = new LinkedHashMap<>();
            scores.put(v, 50.0);
            Map<String, String> tiers = new LinkedHashMap<>();
            tiers.put(v, "MEDIUM");
            return new DiffusionReport(
                    1.0, 1.0, single, single, scores, tiers,
                    Collections.singletonList(v),
                    Collections.singletonList("Ignition"),
                    singlePhaseMap("Ignition", 0),
                    100.0, 1.0, 0.0, "Immune", 0.0,
                    Collections.singletonList("Single-node graph — trivial diffusion."),
                    1, e);
        }

        // Build adjacency
        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(graph);

        // Select seeds
        Set<String> seeds = resolveSeedNodes(vertices, adj);

        // Engine 1: IC simulation
        ICResult icResult = runIC(vertices, adj, seeds);

        // Engine 2: LT simulation
        LTResult ltResult = runLT(vertices, adj, seeds);

        // Engine 3: Superspreader identification
        Map<String, Double> ssScores = computeSuperspreaderScores(vertices, adj);
        Map<String, String> ssTiers = new LinkedHashMap<>();
        List<String> topSpreaders = new ArrayList<>();
        List<Map.Entry<String, Double>> sorted = ssScores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .collect(Collectors.toList());
        for (Map.Entry<String, Double> en : sorted) {
            ssTiers.put(en.getKey(), tierForScore(en.getValue()));
        }
        for (int i = 0; i < Math.min(10, sorted.size()); i++) {
            topSpreaders.add(sorted.get(i).getKey());
        }

        // Engine 4: Cascade phase detection (use IC step-by-step data)
        PhaseResult phaseResult = detectPhases(vertices, adj, seeds);

        // Engine 5: Tipping point estimation
        TippingResult tipping = estimateTippingPoint(vertices, adj);

        // Engine 6: Viral potential score
        double viralScore = computeViralScore(vertices, adj, graph);
        String viralTier = viralTier(viralScore);

        // Engine 7: Insights
        List<String> insights = generateInsights(icResult, ltResult, ssScores,
                phaseResult, tipping, viralScore, viralTier, n, e, seeds);

        return new DiffusionReport(
                icResult.avgCascadeSize, ltResult.avgCascadeSize,
                icResult.activationProb, ltResult.activationProb,
                ssScores, ssTiers, topSpreaders,
                phaseResult.phases, phaseResult.transitions,
                tipping.tippingPct, tipping.confidence,
                viralScore, viralTier, viralScore, insights, n, e);
    }

    // ==================================================================
    // Engine 1: Independent Cascade
    // ==================================================================

    private static class ICResult {
        double avgCascadeSize;
        Map<String, Double> activationProb;
    }

    private ICResult runIC(List<String> vertices, Map<String, Set<String>> adj, Set<String> seeds) {
        int n = vertices.size();
        Map<String, int[]> activationCount = new LinkedHashMap<>();
        for (String v : vertices) activationCount.put(v, new int[]{0});
        double totalSize = 0;

        for (int trial = 0; trial < monteCarloTrials; trial++) {
            Set<String> active = new LinkedHashSet<>(seeds);
            Queue<String> frontier = new LinkedList<>(seeds);
            while (!frontier.isEmpty()) {
                String u = frontier.poll();
                Set<String> neighbors = adj.getOrDefault(u, Collections.emptySet());
                for (String v : neighbors) {
                    if (!active.contains(v) && rng.nextDouble() < spreadProbability) {
                        active.add(v);
                        frontier.add(v);
                    }
                }
            }
            totalSize += active.size();
            for (String v : active) {
                activationCount.get(v)[0]++;
            }
        }

        ICResult result = new ICResult();
        result.avgCascadeSize = totalSize / monteCarloTrials;
        result.activationProb = new LinkedHashMap<>();
        for (String v : vertices) {
            result.activationProb.put(v, (double) activationCount.get(v)[0] / monteCarloTrials);
        }
        return result;
    }

    // ==================================================================
    // Engine 2: Linear Threshold
    // ==================================================================

    private static class LTResult {
        double avgCascadeSize;
        Map<String, Double> activationProb;
    }

    private LTResult runLT(List<String> vertices, Map<String, Set<String>> adj, Set<String> seeds) {
        int n = vertices.size();
        Map<String, int[]> activationCount = new LinkedHashMap<>();
        for (String v : vertices) activationCount.put(v, new int[]{0});
        double totalSize = 0;

        // Pre-compute neighbor sizes (immutable per trial)
        Map<String, Integer> neighborSize = new LinkedHashMap<>(n * 2);
        for (String v : vertices) {
            neighborSize.put(v, adj.getOrDefault(v, Collections.emptySet()).size());
        }

        for (int trial = 0; trial < monteCarloTrials; trial++) {
            // Random thresholds
            Map<String, Double> thresholds = new LinkedHashMap<>(n * 2);
            for (String v : vertices) {
                thresholds.put(v, rng.nextDouble());
            }

            Set<String> active = new LinkedHashSet<>(seeds);

            // Incremental active-neighbor counts: only recompute on activation
            Map<String, Integer> activeNbCount = new LinkedHashMap<>(n * 2);
            for (String v : vertices) activeNbCount.put(v, 0);

            // Initialize counts from seed nodes
            for (String s : seeds) {
                for (String nb : adj.getOrDefault(s, Collections.emptySet())) {
                    if (!active.contains(nb)) {
                        activeNbCount.put(nb, activeNbCount.get(nb) + 1);
                    }
                }
            }

            // Candidate set: inactive nodes with at least one active neighbor
            Set<String> candidates = new LinkedHashSet<>();
            for (String v : vertices) {
                if (!active.contains(v) && activeNbCount.get(v) > 0) {
                    candidates.add(v);
                }
            }

            boolean changed = true;
            while (changed) {
                changed = false;
                List<String> newlyActivated = new ArrayList<>();
                Iterator<String> it = candidates.iterator();
                while (it.hasNext()) {
                    String v = it.next();
                    int nSize = neighborSize.get(v);
                    if (nSize == 0) { it.remove(); continue; }
                    double fraction = (double) activeNbCount.get(v) / nSize;
                    if (fraction >= thresholds.get(v)) {
                        active.add(v);
                        it.remove();
                        newlyActivated.add(v);
                        changed = true;
                    }
                }
                // Update counts and candidates for newly activated nodes
                for (String v : newlyActivated) {
                    for (String nb : adj.getOrDefault(v, Collections.emptySet())) {
                        if (!active.contains(nb)) {
                            activeNbCount.put(nb, activeNbCount.get(nb) + 1);
                            candidates.add(nb);
                        }
                    }
                }
            }
            totalSize += active.size();
            for (String v : active) {
                activationCount.get(v)[0]++;
            }
        }

        LTResult result = new LTResult();
        result.avgCascadeSize = totalSize / monteCarloTrials;
        result.activationProb = new LinkedHashMap<>();
        for (String v : vertices) {
            result.activationProb.put(v, (double) activationCount.get(v)[0] / monteCarloTrials);
        }
        return result;
    }

    // ==================================================================
    // Engine 3: Superspreader Identification
    // ==================================================================

    private Map<String, Double> computeSuperspreaderScores(List<String> vertices,
                                                            Map<String, Set<String>> adj) {
        int n = vertices.size();
        // a) Degree centrality (normalized)
        Map<String, Double> degreeCent = new LinkedHashMap<>();
        double maxDeg = 0;
        for (String v : vertices) {
            double deg = adj.getOrDefault(v, Collections.emptySet()).size();
            degreeCent.put(v, deg);
            if (deg > maxDeg) maxDeg = deg;
        }
        if (maxDeg > 0) {
            for (String v : vertices) degreeCent.put(v, degreeCent.get(v) / maxDeg);
        }

        // b) K-shell decomposition
        Map<String, Integer> kShell = computeKShell(vertices, adj);
        int maxShell = 0;
        for (int s : kShell.values()) if (s > maxShell) maxShell = s;
        Map<String, Double> kShellNorm = new LinkedHashMap<>();
        for (String v : vertices) {
            kShellNorm.put(v, maxShell > 0 ? (double) kShell.get(v) / maxShell : 0.0);
        }

        // c) Single-seed IC cascade size (fast, fewer trials)
        int ssTrials = Math.max(10, monteCarloTrials / 5);
        Map<String, Double> icSpread = new LinkedHashMap<>();
        double maxSpread = 0;
        for (String v : vertices) {
            double total = 0;
            Set<String> seed = Collections.singleton(v);
            for (int t = 0; t < ssTrials; t++) {
                total += runSingleIC(adj, seed);
            }
            double avg = total / ssTrials;
            icSpread.put(v, avg);
            if (avg > maxSpread) maxSpread = avg;
        }
        if (maxSpread > 0) {
            for (String v : vertices) icSpread.put(v, icSpread.get(v) / maxSpread);
        }

        // Composite: 30% degree + 30% k-shell + 40% IC spread
        Map<String, Double> scores = new LinkedHashMap<>();
        for (String v : vertices) {
            double score = (0.3 * degreeCent.get(v) + 0.3 * kShellNorm.get(v)
                    + 0.4 * icSpread.get(v)) * 100.0;
            scores.put(v, Math.min(100.0, Math.max(0.0, score)));
        }
        return scores;
    }

    private int runSingleIC(Map<String, Set<String>> adj, Set<String> seeds) {
        Set<String> active = new LinkedHashSet<>(seeds);
        Queue<String> frontier = new LinkedList<>(seeds);
        while (!frontier.isEmpty()) {
            String u = frontier.poll();
            Set<String> neighbors = adj.getOrDefault(u, Collections.emptySet());
            for (String v : neighbors) {
                if (!active.contains(v) && rng.nextDouble() < spreadProbability) {
                    active.add(v);
                    frontier.add(v);
                }
            }
        }
        return active.size();
    }

    private Map<String, Integer> computeKShell(List<String> vertices, Map<String, Set<String>> adj) {
        Map<String, Integer> shell = new LinkedHashMap<>();
        Map<String, Integer> degree = new LinkedHashMap<>();
        Set<String> remaining = new LinkedHashSet<>(vertices);

        for (String v : vertices) {
            int deg = 0;
            for (String nb : adj.getOrDefault(v, Collections.emptySet())) {
                if (remaining.contains(nb)) deg++;
            }
            degree.put(v, deg);
        }

        int k = 0;
        while (!remaining.isEmpty()) {
            boolean removed = true;
            while (removed) {
                removed = false;
                List<String> toRemove = new ArrayList<>();
                for (String v : remaining) {
                    if (degree.get(v) <= k) {
                        toRemove.add(v);
                    }
                }
                for (String v : toRemove) {
                    remaining.remove(v);
                    shell.put(v, k);
                    for (String nb : adj.getOrDefault(v, Collections.emptySet())) {
                        if (remaining.contains(nb)) {
                            degree.put(nb, degree.get(nb) - 1);
                        }
                    }
                    removed = true;
                }
            }
            k++;
        }
        return shell;
    }

    // ==================================================================
    // Engine 4: Cascade Phase Detection
    // ==================================================================

    private static class PhaseResult {
        List<String> phases;
        Map<String, Integer> transitions;
    }

    private PhaseResult detectPhases(List<String> vertices, Map<String, Set<String>> adj,
                                     Set<String> seeds) {
        // Run a single representative IC cascade recording time steps
        List<Integer> activationsPerStep = new ArrayList<>();
        Set<String> active = new LinkedHashSet<>(seeds);
        activationsPerStep.add(seeds.size()); // step 0: ignition

        Set<String> frontier = new LinkedHashSet<>(seeds);
        int step = 0;
        while (!frontier.isEmpty() && step < vertices.size()) {
            step++;
            Set<String> newActive = new LinkedHashSet<>();
            for (String u : frontier) {
                for (String v : adj.getOrDefault(u, Collections.emptySet())) {
                    if (!active.contains(v) && rng.nextDouble() < spreadProbability) {
                        newActive.add(v);
                    }
                }
            }
            active.addAll(newActive);
            activationsPerStep.add(newActive.size());
            frontier = newActive;
        }

        // Classify phases based on velocity and acceleration
        List<String> phases = new ArrayList<>();
        Map<String, Integer> transitions = new LinkedHashMap<>();

        phases.add("Ignition");
        transitions.put("Ignition", 0);

        if (activationsPerStep.size() <= 1) {
            PhaseResult r = new PhaseResult();
            r.phases = phases;
            r.transitions = transitions;
            return r;
        }

        int n = vertices.size();
        boolean hitEarly = false, hitExponential = false, hitPeak = false;
        boolean hitSaturation = false, hitExtinction = false;

        int peakStep = 0;
        int peakVal = 0;
        for (int i = 1; i < activationsPerStep.size(); i++) {
            if (activationsPerStep.get(i) > peakVal) {
                peakVal = activationsPerStep.get(i);
                peakStep = i;
            }
        }

        for (int i = 1; i < activationsPerStep.size(); i++) {
            int cur = activationsPerStep.get(i);
            int prev = (i > 1) ? activationsPerStep.get(i - 1) : activationsPerStep.get(0);

            if (!hitEarly && cur > 0) {
                hitEarly = true;
                if (i > 1) { // don't mark if step 1 is already exponential
                    phases.add("Early Spread");
                    transitions.put("Early Spread", i);
                }
            }

            if (!hitExponential && cur > prev && cur >= 2) {
                hitExponential = true;
                phases.add("Exponential Growth");
                transitions.put("Exponential Growth", i);
            }

            if (!hitPeak && i == peakStep && peakVal > 0) {
                hitPeak = true;
                phases.add("Peak");
                transitions.put("Peak", i);
            }

            if (hitPeak && !hitSaturation && cur < peakVal && cur > 0) {
                hitSaturation = true;
                phases.add("Saturation");
                transitions.put("Saturation", i);
            }

            if (hitPeak && !hitExtinction && cur == 0) {
                hitExtinction = true;
                phases.add("Extinction");
                transitions.put("Extinction", i);
            }
        }

        // If cascade died immediately
        if (!hitEarly) {
            phases.add("Extinction");
            transitions.put("Extinction", 1);
        }

        PhaseResult r = new PhaseResult();
        r.phases = phases;
        r.transitions = transitions;
        return r;
    }

    // ==================================================================
    // Engine 5: Tipping Point Estimation
    // ==================================================================

    private static class TippingResult {
        double tippingPct;
        double confidence;
    }

    private TippingResult estimateTippingPoint(List<String> vertices,
                                                Map<String, Set<String>> adj) {
        int n = vertices.size();
        if (n <= 1) {
            TippingResult r = new TippingResult();
            r.tippingPct = 100.0;
            r.confidence = 1.0;
            return r;
        }

        // Sort vertices by degree descending for greedy seed selection
        List<String> byDegree = new ArrayList<>(vertices);
        byDegree.sort((a, b) -> Integer.compare(
                adj.getOrDefault(b, Collections.emptySet()).size(),
                adj.getOrDefault(a, Collections.emptySet()).size()));

        int tippingSeedCount = -1;
        int maxSeeds = Math.max(1, n / 2);
        int stepSize = Math.max(1, maxSeeds / 20); // test ~20 points
        int trials = Math.max(5, monteCarloTrials / 5);

        double[] coverages = new double[maxSeeds + 1];

        for (int seedCount = 1; seedCount <= maxSeeds; seedCount += stepSize) {
            Set<String> seeds = new LinkedHashSet<>();
            for (int i = 0; i < Math.min(seedCount, byDegree.size()); i++) {
                seeds.add(byDegree.get(i));
            }

            double totalCoverage = 0;
            for (int t = 0; t < trials; t++) {
                int cascadeSize = runSingleIC(adj, seeds);
                totalCoverage += (double) cascadeSize / n;
            }
            double avgCoverage = totalCoverage / trials;
            coverages[seedCount] = avgCoverage;

            if (tippingSeedCount < 0 && avgCoverage > 0.5) {
                tippingSeedCount = seedCount;
            }
        }

        TippingResult r = new TippingResult();
        if (tippingSeedCount > 0) {
            r.tippingPct = (double) tippingSeedCount / n * 100.0;
            r.confidence = 0.8; // moderate confidence from Monte Carlo
        } else {
            r.tippingPct = 100.0; // couldn't reach 50% even with half the nodes
            r.confidence = 0.5;
        }
        return r;
    }

    // ==================================================================
    // Engine 6: Viral Potential Score
    // ==================================================================

    private double computeViralScore(List<String> vertices, Map<String, Set<String>> adj,
                                     Graph<String, Edge> graph) {
        int n = vertices.size();
        if (n <= 1) return 0.0;

        // Factor 1: Average degree (normalized, cap at 20)
        double totalDeg = 0;
        double[] degrees = new double[n];
        for (int i = 0; i < n; i++) {
            degrees[i] = adj.getOrDefault(vertices.get(i), Collections.emptySet()).size();
            totalDeg += degrees[i];
        }
        double avgDeg = totalDeg / n;
        double degScore = Math.min(1.0, avgDeg / 20.0);

        // Factor 2: Clustering coefficient
        double totalCC = 0;
        int ccCount = 0;
        for (String v : vertices) {
            Set<String> neighbors = adj.getOrDefault(v, Collections.emptySet());
            int k = neighbors.size();
            if (k < 2) continue;
            int triangles = 0;
            List<String> nbList = new ArrayList<>(neighbors);
            for (int i = 0; i < nbList.size(); i++) {
                for (int j = i + 1; j < nbList.size(); j++) {
                    if (adj.getOrDefault(nbList.get(i), Collections.emptySet()).contains(nbList.get(j))) {
                        triangles++;
                    }
                }
            }
            totalCC += (2.0 * triangles) / (k * (k - 1));
            ccCount++;
        }
        double avgCC = ccCount > 0 ? totalCC / ccCount : 0.0;

        // Factor 3: Diameter estimate (BFS from random node)
        int diameter = estimateDiameter(vertices, adj);
        // Smaller diameter → more viral. Score: 1.0 for diameter ≤ 3, 0 for ≥ 20
        double diamScore = Math.max(0, 1.0 - (diameter - 3.0) / 17.0);

        // Factor 4: Degree heterogeneity (Gini coefficient)
        Arrays.sort(degrees);
        double gini = computeGini(degrees);

        // Factor 5: Giant component fraction
        double giantFraction = giantComponentFraction(vertices, adj);

        // Weighted composite: avg_degree 25%, clustering 15%, diameter 20%, heterogeneity 15%, giant 25%
        double score = (0.25 * degScore + 0.15 * avgCC + 0.20 * diamScore
                + 0.15 * gini + 0.25 * giantFraction) * 100.0;
        return Math.min(100.0, Math.max(0.0, score));
    }

    private int estimateDiameter(List<String> vertices, Map<String, Set<String>> adj) {
        if (vertices.isEmpty()) return 0;
        int maxDist = 0;
        // BFS from a few nodes
        int probes = Math.min(5, vertices.size());
        List<String> probeNodes = new ArrayList<>(vertices);
        Collections.shuffle(probeNodes, rng);
        for (int p = 0; p < probes; p++) {
            Map<String, Integer> dist = GraphUtils.bfsDistancesFromAdj(probeNodes.get(p), adj);
            for (int d : dist.values()) {
                if (d > maxDist) maxDist = d;
            }
        }
        return maxDist;
    }

    // bfs removed — use GraphUtils.bfsDistancesFromAdj(source, adj)

    private double computeGini(double[] sorted) {
        int n = sorted.length;
        if (n == 0) return 0;
        double sum = 0, weightedSum = 0;
        for (int i = 0; i < n; i++) {
            sum += sorted[i];
            weightedSum += (i + 1) * sorted[i];
        }
        if (sum == 0) return 0;
        return (2.0 * weightedSum) / (n * sum) - (n + 1.0) / n;
    }

    private double giantComponentFraction(List<String> vertices, Map<String, Set<String>> adj) {
        if (vertices.isEmpty()) return 0;
        Set<String> visited = new LinkedHashSet<>();
        int maxComp = 0;
        for (String v : vertices) {
            if (visited.contains(v)) continue;
            Map<String, Integer> comp = GraphUtils.bfsDistancesFromAdj(v, adj);
            visited.addAll(comp.keySet());
            if (comp.size() > maxComp) maxComp = comp.size();
        }
        return (double) maxComp / vertices.size();
    }

    // ==================================================================
    // Engine 7: Insight Generator
    // ==================================================================

    private List<String> generateInsights(ICResult ic, LTResult lt,
                                          Map<String, Double> ssScores,
                                          PhaseResult phases, TippingResult tipping,
                                          double viralScore, String viralTier,
                                          int n, int e, Set<String> seeds) {
        List<String> insights = new ArrayList<>();

        // IC vs LT comparison
        if (ic.avgCascadeSize > lt.avgCascadeSize * 1.5) {
            insights.add("IC model spreads significantly more than LT — network is probabilistically vulnerable but threshold-resilient.");
        } else if (lt.avgCascadeSize > ic.avgCascadeSize * 1.5) {
            insights.add("LT model spreads more than IC — network has strong peer-pressure dynamics.");
        }

        // Superspreader concentration
        long superCount = ssScores.values().stream().filter(s -> s >= 80).count();
        if (superCount > 0 && superCount <= n * 0.1) {
            insights.add(String.format("Superspreader concentration: %d nodes (%.1f%%) control most spreading power — immunizing them could halt cascades.",
                    superCount, 100.0 * superCount / n));
        }

        // Tipping point
        if (tipping.tippingPct < 10) {
            insights.add(String.format("Very low tipping point (%.1f%%) — even a small seed set can trigger network-wide cascades.", tipping.tippingPct));
        } else if (tipping.tippingPct > 50) {
            insights.add("High tipping point — network is resistant to viral spread without large initial adoption.");
        }

        // Viral score
        insights.add(String.format("Viral potential: %s (score %.1f/100) — %s",
                viralTier, viralScore, viralExplanation(viralTier)));

        // Phase sharpness
        if (phases.phases.contains("Exponential Growth") && phases.phases.contains("Peak")) {
            Integer expStep = phases.transitions.get("Exponential Growth");
            Integer peakStep = phases.transitions.get("Peak");
            if (expStep != null && peakStep != null) {
                int duration = peakStep - expStep;
                if (duration <= 2) {
                    insights.add("Sharp phase transition — cascade reaches peak within 2 steps of exponential growth onset. High outbreak risk.");
                }
            }
        }

        // Cascade coverage
        double icCoverage = ic.avgCascadeSize / n * 100;
        insights.add(String.format("Average IC cascade reaches %.1f%% of nodes from seed set of %d.", icCoverage, seeds.size()));

        // Containment recommendations
        if (viralScore > 60) {
            insights.add("CONTAINMENT: Consider immunizing top superspreaders to reduce viral potential. Target nodes in the SUPER tier first.");
        }
        if (viralScore > 80) {
            insights.add("WARNING: Network is highly susceptible to information cascades. Monitor for misinformation spread.");
        }

        return insights;
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    // buildAdjacency removed — use GraphUtils.buildAdjacencyMap(graph)

    private Set<String> resolveSeedNodes(List<String> vertices, Map<String, Set<String>> adj) {
        if (seedNodes != null && !seedNodes.isEmpty()) {
            Set<String> valid = new LinkedHashSet<>();
            for (String s : seedNodes) {
                if (adj.containsKey(s)) valid.add(s);
            }
            if (!valid.isEmpty()) return valid;
        }
        // Auto-select: highest degree node
        String best = vertices.get(0);
        int bestDeg = adj.getOrDefault(best, Collections.emptySet()).size();
        for (String v : vertices) {
            int d = adj.getOrDefault(v, Collections.emptySet()).size();
            if (d > bestDeg) { best = v; bestDeg = d; }
        }
        return Collections.singleton(best);
    }

    private static String tierForScore(double score) {
        if (score >= 80) return "SUPER";
        if (score >= 60) return "HIGH";
        if (score >= 40) return "MEDIUM";
        if (score >= 20) return "LOW";
        return "MINIMAL";
    }

    private static String viralTier(double score) {
        if (score >= 80) return "Highly Viral";
        if (score >= 60) return "Viral";
        if (score >= 40) return "Moderate";
        if (score >= 20) return "Resistant";
        return "Immune";
    }

    private static String viralExplanation(String tier) {
        switch (tier) {
            case "Highly Viral": return "Information spreads rapidly with minimal seeding.";
            case "Viral": return "Network supports efficient cascade propagation.";
            case "Moderate": return "Spread is possible but requires strategic seeding.";
            case "Resistant": return "Network structure limits cascade propagation.";
            default: return "Network is effectively immune to viral spread.";
        }
    }

    private DiffusionReport emptyReport(int n, int e) {
        return new DiffusionReport(
                0, 0,
                Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyMap(),
                0, 0, 0, "Immune", 0,
                Collections.singletonList("Empty graph — no diffusion analysis possible."),
                n, e);
    }

    private Map<String, Integer> singlePhaseMap(String phase, int step) {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put(phase, step);
        return m;
    }

    // ==================================================================
    // Text export
    // ==================================================================

    public String toText(DiffusionReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("+=========================================================+\n");
        sb.append("|     GRAPH INFORMATION DIFFUSION ENGINE -- REPORT         |\n");
        sb.append("+=========================================================+\n\n");

        sb.append(String.format("  Network: %d nodes, %d edges\n", report.nodeCount, report.edgeCount));
        sb.append(String.format("  Viral Score: %.1f / 100 (%s)\n\n", report.viralScore, report.viralTier));

        sb.append("-- Independent Cascade ------------------------------------\n");
        sb.append(String.format("  Average cascade size: %.2f (%.1f%% of network)\n",
                report.cascadeSizeIC, report.nodeCount > 0 ? report.cascadeSizeIC / report.nodeCount * 100 : 0));

        sb.append("\n-- Linear Threshold --------------------------------------\n");
        sb.append(String.format("  Average cascade size: %.2f (%.1f%% of network)\n",
                report.cascadeSizeLT, report.nodeCount > 0 ? report.cascadeSizeLT / report.nodeCount * 100 : 0));

        sb.append("\n-- Top Superspreaders ------------------------------------\n");
        for (int i = 0; i < report.topSpreaders.size(); i++) {
            String node = report.topSpreaders.get(i);
            sb.append(String.format("  %2d. %-15s Score: %5.1f  Tier: %s\n",
                    i + 1, node, report.superspreaderScores.getOrDefault(node, 0.0),
                    report.superspreaderTiers.getOrDefault(node, "?")));
        }

        sb.append("\n-- Cascade Phases ----------------------------------------\n");
        for (String phase : report.cascadePhases) {
            Integer step = report.phaseTransitions.get(phase);
            sb.append(String.format("  Step %2d: %s\n", step != null ? step : -1, phase));
        }

        sb.append("\n-- Tipping Point -----------------------------------------\n");
        sb.append(String.format("  Critical mass: %.1f%% of nodes (confidence: %.0f%%)\n",
                report.tippingPointPct, report.tippingPointConfidence * 100));

        sb.append("\n-- Insights ----------------------------------------------\n");
        for (String insight : report.insights) {
            sb.append("  • ").append(insight).append("\n");
        }

        return sb.toString();
    }

    // ==================================================================
    // HTML export
    // ==================================================================

    public String exportHtml(DiffusionReport report) {
        StringBuilder h = new StringBuilder();
        h.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">\n");
        h.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n");
        h.append("<title>Information Diffusion Analysis</title>\n");
        h.append("<style>\n");
        h.append("*{margin:0;padding:0;box-sizing:border-box}\n");
        h.append("body{background:#0f172a;color:#e2e8f0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;padding:24px}\n");
        h.append("h1{font-size:1.8em;margin-bottom:8px;color:#f8fafc}\n");
        h.append(".subtitle{color:#94a3b8;margin-bottom:24px}\n");
        h.append(".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:16px;margin-bottom:24px}\n");
        h.append(".card{background:#1e293b;border-radius:12px;padding:20px;border:1px solid #334155}\n");
        h.append("h2{font-size:1.1em;margin-bottom:12px;color:#f1f5f9}\n");
        h.append(".stat{font-size:2.2em;font-weight:700;color:#38bdf8}\n");
        h.append(".stat-label{font-size:.85em;color:#94a3b8}\n");
        h.append("table{width:100%;border-collapse:collapse;margin-top:8px}\n");
        h.append("th,td{text-align:left;padding:6px 10px;border-bottom:1px solid #334155}\n");
        h.append("th{color:#94a3b8;font-weight:600;font-size:.85em}\n");
        h.append(".bar-container{display:flex;align-items:center;margin:4px 0}\n");
        h.append(".bar-label{width:100px;font-size:.85em;color:#cbd5e1;flex-shrink:0}\n");
        h.append(".bar{height:18px;border-radius:4px;min-width:2px;transition:width .3s}\n");
        h.append(".bar-val{margin-left:8px;font-size:.8em;color:#94a3b8}\n");
        h.append(".tier-super{color:#f97316;font-weight:700}\n");
        h.append(".tier-high{color:#eab308}\n");
        h.append(".tier-medium{color:#38bdf8}\n");
        h.append(".tier-low{color:#94a3b8}\n");
        h.append(".tier-minimal{color:#64748b}\n");
        h.append(".insight{background:#1a2332;border-left:3px solid #38bdf8;padding:8px 12px;margin:6px 0;border-radius:0 6px 6px 0;font-size:.9em}\n");
        h.append(".phase{display:inline-block;padding:4px 12px;border-radius:12px;margin:3px;font-size:.85em;background:#334155}\n");
        h.append(".gauge{width:100%;height:24px;background:#0f172a;border-radius:12px;overflow:hidden;margin-top:8px}\n");
        h.append(".gauge-fill{height:100%;border-radius:12px;transition:width .5s}\n");
        h.append("</style></head><body>\n");

        h.append("<h1>&#x1F4E1; Information Diffusion Analysis</h1>\n");
        h.append("<p class=\"subtitle\">Autonomous cascade simulation &amp; spreading dynamics</p>\n");

        // Summary cards
        h.append("<div class=\"grid\">\n");

        // Viral Score gauge
        h.append("<div class=\"card\">");
        h.append("<h2>&#x1F525; Viral Potential</h2>");
        h.append(String.format("<div class=\"stat\">%.1f</div>", report.viralScore));
        h.append(String.format("<div class=\"stat-label\">%s</div>", report.viralTier));
        String gaugeColor = report.viralScore >= 80 ? "#ef4444" : report.viralScore >= 60 ? "#f97316"
                : report.viralScore >= 40 ? "#eab308" : report.viralScore >= 20 ? "#38bdf8" : "#64748b";
        h.append(String.format("<div class=\"gauge\"><div class=\"gauge-fill\" style=\"width:%.1f%%;background:%s\"></div></div>",
                report.viralScore, gaugeColor));
        h.append("</div>\n");

        // IC cascade
        h.append("<div class=\"card\">");
        h.append("<h2>&#x1F4A5; Independent Cascade</h2>");
        h.append(String.format("<div class=\"stat\">%.1f</div>", report.cascadeSizeIC));
        h.append(String.format("<div class=\"stat-label\">avg nodes reached (%.1f%%)</div>",
                report.nodeCount > 0 ? report.cascadeSizeIC / report.nodeCount * 100 : 0));
        h.append("</div>\n");

        // LT cascade
        h.append("<div class=\"card\">");
        h.append("<h2>&#x1F3AF; Linear Threshold</h2>");
        h.append(String.format("<div class=\"stat\">%.1f</div>", report.cascadeSizeLT));
        h.append(String.format("<div class=\"stat-label\">avg nodes reached (%.1f%%)</div>",
                report.nodeCount > 0 ? report.cascadeSizeLT / report.nodeCount * 100 : 0));
        h.append("</div>\n");

        // Tipping point
        h.append("<div class=\"card\">");
        h.append("<h2>&#x26A1; Tipping Point</h2>");
        h.append(String.format("<div class=\"stat\">%.1f%%</div>", report.tippingPointPct));
        h.append(String.format("<div class=\"stat-label\">critical mass (%.0f%% confidence)</div>",
                report.tippingPointConfidence * 100));
        h.append("</div>\n");

        h.append("</div>\n"); // end grid

        // Superspreaders
        h.append("<div class=\"card\" style=\"margin-bottom:16px\">\n");
        h.append("<h2>&#x1F451; Top Superspreaders</h2>\n");
        if (!report.topSpreaders.isEmpty()) {
            double maxScore = report.superspreaderScores.getOrDefault(report.topSpreaders.get(0), 100.0);
            if (maxScore == 0) maxScore = 1;
            for (String node : report.topSpreaders) {
                double score = report.superspreaderScores.getOrDefault(node, 0.0);
                String tier = report.superspreaderTiers.getOrDefault(node, "MINIMAL");
                double pct = (score / maxScore) * 100;
                String barColor = tier.equals("SUPER") ? "#f97316" : tier.equals("HIGH") ? "#eab308"
                        : tier.equals("MEDIUM") ? "#38bdf8" : "#64748b";
                h.append("<div class=\"bar-container\">");
                h.append("<span class=\"bar-label\">").append(escHtml(node)).append("</span>");
                h.append(String.format("<div class=\"bar\" style=\"width:%.1f%%;background:%s\"></div>", pct, barColor));
                h.append(String.format("<span class=\"bar-val\">%.1f (%s)</span>", score, tier));
                h.append("</div>\n");
            }
        }
        h.append("</div>\n");

        // Cascade phases
        h.append("<div class=\"card\" style=\"margin-bottom:16px\">\n");
        h.append("<h2>&#x1F4C8; Cascade Phases</h2>\n");
        for (String phase : report.cascadePhases) {
            Integer step = report.phaseTransitions.get(phase);
            h.append(String.format("<span class=\"phase\">Step %d: %s</span>\n",
                    step != null ? step : 0, escHtml(phase)));
        }
        h.append("</div>\n");

        // Insights
        h.append("<div class=\"card\">\n");
        h.append("<h2>&#x1F4A1; Autonomous Insights</h2>\n");
        for (String insight : report.insights) {
            h.append("<div class=\"insight\">").append(escHtml(insight)).append("</div>\n");
        }
        h.append("</div>\n");

        // Footer
        h.append("<p style=\"margin-top:24px;color:#475569;font-size:.8em;text-align:center\">");
        h.append("Generated by GraphInformationDiffusionEngine -- ");
        h.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(new Date()));
        h.append("</p>\n");

        h.append("</body></html>\n");
        return h.toString();
    }

    public void exportHtml(DiffusionReport report, File file) throws IOException {
        try (Writer w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            w.write(exportHtml(report));
        }
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
