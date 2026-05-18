package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;

/**
 * GraphInfluenceSeedAdvisor &mdash; agentic seed-selection advisor for
 * information spread / outbreak containment over undirected graphs.
 *
 * <p>Two modes:</p>
 * <ul>
 *   <li>{@link Mode#SPREAD} (default): pick up to K seeds that maximise
 *       expected influence under an Independent Cascade (IC) diffusion model
 *       with default per-edge activation probability {@code p = 0.10}.</li>
 *   <li>{@link Mode#CONTAINMENT}: given a set of <em>source</em> nodes
 *       (an outbreak), pick up to K immunisation nodes whose removal
 *       most reduces the expected spread.</li>
 * </ul>
 *
 * <p>Greedy selection with marginal-gain estimation via deterministic
 * seeded Monte Carlo. Single file, pure JDK + JUNG; never mutates inputs.</p>
 *
 * <pre>
 *   GraphInfluenceSeedAdvisor&lt;String, Edge&gt; a =
 *           new GraphInfluenceSeedAdvisor&lt;&gt;(g).withBudget(3);
 *   GraphInfluenceSeedAdvisor.Plan plan = a.analyze();
 *   System.out.println(a.toMarkdown(plan));
 * </pre>
 *
 * @author sauravbhattacharya001
 */
public final class GraphInfluenceSeedAdvisor<V, E> {

    // -- Public types ------------------------------------------------------

    public enum Mode { SPREAD, CONTAINMENT }
    public enum Verdict { SEED_NOW, SEED_SECONDARY, SKIP_REDUNDANT }
    public enum Priority { P0, P1, P2, P3 }
    public enum RiskAppetite { CAUTIOUS, BALANCED, AGGRESSIVE }

    public static final class SeedDecision<V> {
        public final V node;
        public final Verdict verdict;
        public final Priority priority;
        public final double marginalGain;
        public final double coverageEstimate;
        public final List<String> reasons;
        public SeedDecision(V node, Verdict verdict, Priority priority,
                            double marginalGain, double coverageEstimate,
                            List<String> reasons) {
            this.node = node;
            this.verdict = verdict;
            this.priority = priority;
            this.marginalGain = marginalGain;
            this.coverageEstimate = coverageEstimate;
            this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
        }
    }

    public static final class Action {
        public final String id;
        public final Priority priority;
        public final String label;
        public final String owner;
        public final int blastRadius;
        public final String reversibility;
        public final String reason;
        public Action(String id, Priority priority, String label, String owner,
                      int blastRadius, String reversibility, String reason) {
            this.id = id;
            this.priority = priority;
            this.label = label;
            this.owner = owner;
            this.blastRadius = blastRadius;
            this.reversibility = reversibility;
            this.reason = reason;
        }
    }

    public static final class Plan {
        public final Mode mode;
        public final int budget;
        public final int graphSize;
        public final List<Object> seeds;          // V order = selection order
        public final List<SeedDecision<?>> decisions;
        public final List<SeedDecision<?>> alternates; // SKIP_REDUNDANT
        public final double baselineSpread;       // SPREAD: sigma(empty)=0;
                                                  // CONTAINMENT: sigma(sources)
        public final double expectedCoverage;     // SPREAD: sigma(S_final);
                                                  // CONTAINMENT: sigma(sources \ blocked by S)
        public final double expectedReduction;    // CONTAINMENT only (else 0)
        public final double coverageFraction;     // covered / N
        public final double reductionFraction;    // CONTAINMENT only
        public final String grade;                // A..F
        public final List<Action> playbook;
        public final List<String> insights;
        public final int simulationsUsed;
        public final boolean closeMarginRound;
        public final String generatedAt;

        public Plan(Mode mode, int budget, int graphSize,
                    List<Object> seeds, List<SeedDecision<?>> decisions,
                    List<SeedDecision<?>> alternates,
                    double baselineSpread, double expectedCoverage,
                    double expectedReduction, double coverageFraction,
                    double reductionFraction, String grade,
                    List<Action> playbook, List<String> insights,
                    int simulationsUsed, boolean closeMarginRound,
                    String generatedAt) {
            this.mode = mode;
            this.budget = budget;
            this.graphSize = graphSize;
            this.seeds = Collections.unmodifiableList(new ArrayList<>(seeds));
            this.decisions = Collections.unmodifiableList(new ArrayList<>(decisions));
            this.alternates = Collections.unmodifiableList(new ArrayList<>(alternates));
            this.baselineSpread = baselineSpread;
            this.expectedCoverage = expectedCoverage;
            this.expectedReduction = expectedReduction;
            this.coverageFraction = coverageFraction;
            this.reductionFraction = reductionFraction;
            this.grade = grade;
            this.playbook = Collections.unmodifiableList(new ArrayList<>(playbook));
            this.insights = Collections.unmodifiableList(new ArrayList<>(insights));
            this.simulationsUsed = simulationsUsed;
            this.closeMarginRound = closeMarginRound;
            this.generatedAt = generatedAt;
        }
    }

    // -- Configuration -----------------------------------------------------

    private final Graph<V, E> graph;
    private int budget = 5;
    private Mode mode = Mode.SPREAD;
    private Set<V> sources = new LinkedHashSet<>();
    private double defaultP = 0.10;
    private Function<E, Double> edgeProb = null;
    private int simulations = 200;
    private long randomSeed = 0xC0FFEEL;
    private double minMarginalGain = 0.5;
    private RiskAppetite risk = RiskAppetite.BALANCED;
    private Clock clock = Clock.systemUTC();

    public GraphInfluenceSeedAdvisor(Graph<V, E> graph) {
        if (graph == null) throw new IllegalArgumentException("graph is null");
        if (graph.getVertexCount() == 0) throw new IllegalArgumentException("graph is empty");
        this.graph = graph;
    }

    public GraphInfluenceSeedAdvisor<V, E> withBudget(int k) { this.budget = k; return this; }
    public GraphInfluenceSeedAdvisor<V, E> withMode(Mode m) { this.mode = m; return this; }
    public GraphInfluenceSeedAdvisor<V, E> withSources(Collection<V> srcs) {
        this.sources = (srcs == null) ? new LinkedHashSet<V>() : new LinkedHashSet<>(srcs);
        return this;
    }
    public GraphInfluenceSeedAdvisor<V, E> withEdgeProbability(double p) {
        this.defaultP = clamp01(p); this.edgeProb = null; return this;
    }
    public GraphInfluenceSeedAdvisor<V, E> withEdgeProbability(Function<E, Double> f) {
        this.edgeProb = f; return this;
    }
    public GraphInfluenceSeedAdvisor<V, E> withSimulations(int r) { this.simulations = Math.max(1, r); return this; }
    public GraphInfluenceSeedAdvisor<V, E> withRandomSeed(long seed) { this.randomSeed = seed; return this; }
    public GraphInfluenceSeedAdvisor<V, E> withMinMarginalGain(double g) { this.minMarginalGain = Math.max(0.0, g); return this; }
    public GraphInfluenceSeedAdvisor<V, E> withRiskAppetite(RiskAppetite r) { this.risk = r; return this; }
    public GraphInfluenceSeedAdvisor<V, E> withFixedClock(Clock c) { this.clock = c; return this; }

    // -- Analysis ----------------------------------------------------------

    public Plan analyze() {
        // 1. Build adjacency map.
        List<V> vertices = new ArrayList<>(graph.getVertices());
        // Stable sort by toString for determinism.
        Collections.sort(vertices, new Comparator<V>() {
            public int compare(V a, V b) { return String.valueOf(a).compareTo(String.valueOf(b)); }
        });
        int n = vertices.size();
        Map<V, Integer> idx = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) idx.put(vertices.get(i), i);

        // adjacency: list of {neighborIdx, probability}
        List<int[]> adjN = new ArrayList<>(n);
        List<double[]> adjP = new ArrayList<>(n);
        for (int i = 0; i < n; i++) { adjN.add(new int[0]); adjP.add(new double[0]); }

        // edge collection (dedupe)
        Map<Long, Double> edgePMap = new HashMap<>();
        for (E e : graph.getEdges()) {
            Collection<V> ends = graph.getIncidentVertices(e);
            if (ends == null || ends.size() < 2) continue;
            Iterator<V> it = ends.iterator();
            V u = it.next(); V v = it.next();
            if (u.equals(v)) continue;
            int ui = idx.get(u); int vi = idx.get(v);
            int a = Math.min(ui, vi); int b = Math.max(ui, vi);
            long key = ((long) a) * 1_000_003L + (long) b;
            double p = edgeProb != null ? clamp01(edgeProb.apply(e)) : defaultP;
            Double cur = edgePMap.get(key);
            // dedupe parallel edges: keep max p
            if (cur == null || p > cur) edgePMap.put(key, p);
        }
        // build neighbor arrays
        Map<Integer, List<int[]>> tmp = new HashMap<>();
        for (Map.Entry<Long, Double> entry : edgePMap.entrySet()) {
            long key = entry.getKey();
            int b = (int) (key % 1_000_003L);
            int a = (int) (key / 1_000_003L);
            int pInt = Double.doubleToLongBits(entry.getValue()) == 0 ? 0 : 1;
            // build per-side lists
            tmp.computeIfAbsent(a, new java.util.function.Function<Integer, List<int[]>>() {
                public List<int[]> apply(Integer k) { return new ArrayList<>(); }
            }).add(new int[]{b, pInt});
            tmp.computeIfAbsent(b, new java.util.function.Function<Integer, List<int[]>>() {
                public List<int[]> apply(Integer k) { return new ArrayList<>(); }
            }).add(new int[]{a, pInt});
        }
        // Replace tmp-built ints to actual neighbour/prob arrays in sorted neighbour order.
        // We need probabilities as doubles, so redo with map of (a,b) -> p.
        for (int i = 0; i < n; i++) {
            List<int[]> lst = tmp.get(i);
            if (lst == null) continue;
            // sort by neighbor idx for deterministic order
            lst.sort(new Comparator<int[]>() {
                public int compare(int[] x, int[] y) { return Integer.compare(x[0], y[0]); }
            });
            int[] ns = new int[lst.size()];
            double[] ps = new double[lst.size()];
            for (int k = 0; k < lst.size(); k++) {
                ns[k] = lst.get(k)[0];
                int a = Math.min(i, ns[k]); int b = Math.max(i, ns[k]);
                long key = ((long) a) * 1_000_003L + (long) b;
                ps[k] = edgePMap.get(key);
            }
            adjN.set(i, ns);
            adjP.set(i, ps);
        }

        // 2. Filter & resolve sources.
        List<String> insights = new ArrayList<>();
        Set<Integer> sourceIdx = new LinkedHashSet<>();
        int sourceFilteredCount = 0;
        if (mode == Mode.CONTAINMENT) {
            if (sources.isEmpty()) throw new IllegalArgumentException("CONTAINMENT requires non-empty sources");
            for (V s : sources) {
                Integer i = idx.get(s);
                if (i != null) sourceIdx.add(i);
                else sourceFilteredCount++;
            }
            if (sourceFilteredCount > 0) insights.add("SOURCES_OUT_OF_GRAPH:" + sourceFilteredCount);
            if (sourceIdx.isEmpty()) throw new IllegalArgumentException("CONTAINMENT: all sources missing from graph");
        }

        // 3. Determine simulationsUsed (risk-modulated).
        int sims = simulations;
        if (risk == RiskAppetite.CAUTIOUS) sims = simulations * 2;
        else if (risk == RiskAppetite.AGGRESSIVE) sims = Math.max(50, simulations / 2);

        // 4. Articulation points (iterative Tarjan).
        boolean[] isArt = articulationPoints(adjN, n);

        // 5. Degrees.
        int[] degree = new int[n];
        for (int i = 0; i < n; i++) degree[i] = adjN.get(i).length;

        // 6. Candidate pool (top by degree).
        int poolSize = Math.max(3 * Math.max(budget, 1), 25);
        poolSize = Math.min(poolSize, n);
        List<Integer> allByDegree = new ArrayList<>();
        for (int i = 0; i < n; i++) allByDegree.add(i);
        final int[] degRef = degree;
        final List<V> vRef = vertices;
        allByDegree.sort(new Comparator<Integer>() {
            public int compare(Integer a, Integer b) {
                int c = Integer.compare(degRef[b], degRef[a]);
                if (c != 0) return c;
                return String.valueOf(vRef.get(a)).compareTo(String.valueOf(vRef.get(b)));
            }
        });
        // pool excludes sources in CONTAINMENT
        List<Integer> candidatePool = new ArrayList<>();
        for (Integer i : allByDegree) {
            if (mode == Mode.CONTAINMENT && sourceIdx.contains(i)) continue;
            candidatePool.add(i);
            if (candidatePool.size() >= poolSize) break;
        }

        // 7. Run greedy.
        int effectiveBudget = Math.max(0, Math.min(budget, n));
        Set<Integer> chosen = new LinkedHashSet<>();
        List<SeedDecision<?>> decisions = new ArrayList<>();
        List<SeedDecision<?>> alternates = new ArrayList<>();
        boolean closeMargin = false;

        // baseline depending on mode
        double baseline;
        Set<Integer> baselineActivated = new HashSet<>();
        if (mode == Mode.SPREAD) {
            baseline = 0.0;
        } else {
            // sigma(sources)
            double[] sigOut = simulate(adjN, adjP, sourceIdx, Collections.<Integer>emptySet(), sims, randomSeed, baselineActivated);
            baseline = sigOut[0];
        }

        double prevCoverage = (mode == Mode.SPREAD) ? 0.0 : baseline;
        double firstGain = -1.0;

        for (int round = 0; round < effectiveBudget; round++) {
            int bestNode = -1;
            double bestGain = -Double.MAX_VALUE;
            double bestCoverage = 0.0;
            double secondGain = -Double.MAX_VALUE;
            for (Integer cand : candidatePool) {
                if (chosen.contains(cand)) continue;
                double coverage;
                double gain;
                if (mode == Mode.SPREAD) {
                    Set<Integer> S = new HashSet<>(chosen); S.add(cand);
                    coverage = simulate(adjN, adjP, S, Collections.<Integer>emptySet(), sims, randomSeed + round, null)[0];
                    gain = coverage - prevCoverage;
                } else {
                    Set<Integer> block = new HashSet<>(chosen); block.add(cand);
                    double remaining = simulate(adjN, adjP, sourceIdx, block, sims, randomSeed + round, null)[0];
                    coverage = remaining;
                    // gain = previous remaining - new remaining (i.e., extra reduction)
                    gain = prevCoverage - remaining;
                }
                // CAUTIOUS diversity penalty
                if (risk == RiskAppetite.CAUTIOUS && !chosen.isEmpty()) {
                    Set<Integer> candNbrs = new HashSet<>();
                    for (int nb : adjN.get(cand)) candNbrs.add(nb);
                    for (Integer s : chosen) {
                        Set<Integer> sNbrs = new HashSet<>();
                        for (int nb : adjN.get(s)) sNbrs.add(nb);
                        double jac = jaccard(candNbrs, sNbrs);
                        if (jac >= 0.5) { gain -= 0.10 * Math.abs(coverage); break; }
                    }
                }
                if (gain > bestGain) {
                    secondGain = bestGain;
                    bestGain = gain;
                    bestNode = cand;
                    bestCoverage = coverage;
                } else if (gain > secondGain) {
                    secondGain = gain;
                }
            }
            if (bestNode < 0) break;

            // close-margin tracking
            if (secondGain > -Double.MAX_VALUE && bestGain > 0
                    && (bestGain - secondGain) / Math.max(1e-9, bestGain) < 0.05) {
                closeMargin = true;
            }

            if (firstGain < 0) firstGain = bestGain;

            // SKIP if below min
            if (bestGain < minMarginalGain) {
                // record as alternate
                List<String> rsn = new ArrayList<>();
                rsn.add("LOW_MARGINAL_GAIN");
                alternates.add(new SeedDecision<>(vertices.get(bestNode), Verdict.SKIP_REDUNDANT,
                        Priority.P2, bestGain, bestCoverage, rsn));
                // Stop adding more — gains will only get smaller (submodularity heuristic).
                break;
            }

            chosen.add(bestNode);
            List<String> reasons = new ArrayList<>();
            if (degree[bestNode] >= Math.max(3, (int) Math.ceil(0.20 * n))) reasons.add("HIGH_DEGREE_HUB");
            if (isArt[bestNode]) reasons.add("BRIDGE_NODE");
            // baseline activated overlap (only meaningful for SPREAD when chosen.size()>1)
            if (mode == Mode.SPREAD && !baselineActivated.isEmpty()) {
                Set<Integer> nbrs = new HashSet<>();
                for (int nb : adjN.get(bestNode)) nbrs.add(nb);
                if (!nbrs.isEmpty()) {
                    int overlap = 0;
                    for (Integer x : nbrs) if (baselineActivated.contains(x)) overlap++;
                    if ((double) overlap / nbrs.size() >= 0.5) reasons.add("OVERLAPS_PRIOR_SEED");
                }
            }
            if (mode == Mode.CONTAINMENT) {
                boolean gateway = false;
                for (int nb : adjN.get(bestNode)) if (sourceIdx.contains(nb)) { gateway = true; break; }
                if (gateway) reasons.add("OUTBREAK_GATEWAY");
                if (gateway && isArt[bestNode]) reasons.add("CHOKE_POINT");
            }
            // priority decided after we know mean gain — placeholder for now
            decisions.add(new SeedDecision<>(vertices.get(bestNode), Verdict.SEED_NOW, Priority.P0,
                    bestGain, bestCoverage, reasons));

            // update baseline activated snapshot for SPREAD
            if (mode == Mode.SPREAD) {
                baselineActivated.clear();
                simulate(adjN, adjP, chosen, Collections.<Integer>emptySet(), Math.max(5, sims / 4),
                        randomSeed + 9999L + round, baselineActivated);
            }
            prevCoverage = bestCoverage;
        }

        // 8. Assign verdicts/priorities based on mean gain.
        double meanGain = 0.0;
        if (!decisions.isEmpty()) {
            double s = 0; for (SeedDecision<?> d : decisions) s += d.marginalGain;
            meanGain = s / decisions.size();
        }
        List<SeedDecision<?>> finalDecisions = new ArrayList<>();
        for (SeedDecision<?> d : decisions) {
            Verdict v = d.marginalGain >= meanGain ? Verdict.SEED_NOW : Verdict.SEED_SECONDARY;
            Priority p = v == Verdict.SEED_NOW ? Priority.P0 : Priority.P1;
            List<String> r = new ArrayList<>(d.reasons);
            if (d.marginalGain >= meanGain * 1.25 && !r.contains("HIGH_MARGINAL_GAIN")) r.add("HIGH_MARGINAL_GAIN");
            @SuppressWarnings("unchecked")
            SeedDecision<V> casted = (SeedDecision<V>) d;
            finalDecisions.add(new SeedDecision<>(casted.node, v, p, d.marginalGain, d.coverageEstimate, r));
        }

        // 9. Compute plan totals.
        double expectedCoverage;
        double expectedReduction = 0.0;
        double coverageFraction;
        double reductionFraction = 0.0;
        if (mode == Mode.SPREAD) {
            expectedCoverage = finalDecisions.isEmpty() ? 0.0 : finalDecisions.get(finalDecisions.size() - 1).coverageEstimate;
            coverageFraction = (double) expectedCoverage / n;
        } else {
            double remaining = finalDecisions.isEmpty() ? baseline : finalDecisions.get(finalDecisions.size() - 1).coverageEstimate;
            expectedCoverage = remaining;
            expectedReduction = baseline - remaining;
            coverageFraction = (double) baseline / n;
            reductionFraction = baseline > 0 ? expectedReduction / baseline : 0.0;
        }

        // 10. Grade.
        String grade;
        if (mode == Mode.SPREAD) {
            if (effectiveBudget == 0) grade = "A";
            else if (coverageFraction < 0.05) grade = "F";
            else if (coverageFraction >= 0.60) grade = "A";
            else if (coverageFraction >= 0.40) grade = "B";
            else if (coverageFraction >= 0.25) grade = "C";
            else if (coverageFraction >= 0.10) grade = "D";
            else grade = "F";
        } else {
            if (effectiveBudget == 0) grade = "A";
            else if (reductionFraction >= 0.70) grade = "A";
            else if (reductionFraction >= 0.50) grade = "B";
            else if (reductionFraction >= 0.30) grade = "C";
            else if (reductionFraction >= 0.10) grade = "D";
            else grade = "F";
        }

        // 11. Build playbook.
        List<Action> playbook = new ArrayList<>();
        boolean anySeedNow = false; boolean anyBridge = false; boolean anyChoke = false;
        for (SeedDecision<?> d : finalDecisions) {
            if (d.verdict == Verdict.SEED_NOW) anySeedNow = true;
            if (d.reasons.contains("BRIDGE_NODE")) anyBridge = true;
            if (d.reasons.contains("CHOKE_POINT")) anyChoke = true;
        }
        if (mode == Mode.SPREAD && anySeedNow) {
            playbook.add(new Action("LAUNCH_SEED_CAMPAIGN", Priority.P0,
                    "Launch the seeding campaign on the SEED_NOW set", "campaign", 4, "medium",
                    "primary seeds dominate marginal-gain budget"));
        }
        if (mode == Mode.CONTAINMENT && anyChoke) {
            playbook.add(new Action("IMMUNIZE_CHOKE_POINTS", Priority.P0,
                    "Immunise/isolate the identified choke points", "security", 4, "low",
                    "CHOKE_POINT nodes sit on the only path between the outbreak and the rest of the graph"));
        }
        if (!alternates.isEmpty()) {
            playbook.add(new Action("MONITOR_FRINGE", Priority.P1,
                    "Monitor the next-best alternates that were dropped", "ops", 2, "high",
                    "alternate seeds fell below minMarginalGain=" + minMarginalGain));
        }
        if (anyBridge) {
            // suggest a redundant path: find a bridge seed's first two non-adjacent 2-hop neighbours
            String suggestion = suggestRedundantEdge(finalDecisions, adjN, vertices);
            playbook.add(new Action("ADD_REDUNDANT_PATH", Priority.P1,
                    "Add a redundant edge to reduce single-point-of-failure risk" +
                            (suggestion != null ? " (suggested: " + suggestion + ")" : ""),
                    "network", 3, "medium",
                    "at least one picked seed is a bridge / articulation node"));
        }
        if (mode == Mode.SPREAD && effectiveBudget > 0 && coverageFraction < 0.25 && effectiveBudget < n / 4) {
            playbook.add(new Action("INCREASE_BUDGET", Priority.P2,
                    "Raise the seed budget K", "campaign", 2, "high",
                    "coverage fraction " + fmt(coverageFraction) + " below 25% with budget room"));
        }
        if (closeMargin) {
            playbook.add(new Action("RAISE_SIMULATIONS", Priority.P2,
                    "Run more Monte Carlo simulations to disambiguate close calls", "ops", 1, "high",
                    "top-2 candidates within 5% in at least one selection round"));
        }
        if (playbook.isEmpty()) {
            playbook.add(new Action("BASELINE_OK", Priority.P3,
                    "No interventions required", "ops", 1, "high",
                    "no advisor signals triggered"));
        }

        // 12. Insights.
        if (!finalDecisions.isEmpty()) {
            double top = finalDecisions.get(0).marginalGain;
            double total = 0; for (SeedDecision<?> d : finalDecisions) total += d.marginalGain;
            if (total > 0 && top / total > 0.40) insights.add("HUB_DOMINANT");
            if (finalDecisions.size() >= 2) {
                double last = finalDecisions.get(finalDecisions.size() - 1).marginalGain;
                if (firstGain > 0 && last / firstGain < 0.30) insights.add("DIMINISHING_RETURNS");
            }
            int bridgeCount = 0;
            for (SeedDecision<?> d : finalDecisions) if (d.reasons.contains("BRIDGE_NODE")) bridgeCount++;
            if (bridgeCount >= 2) insights.add("BRIDGE_HEAVY");
        }
        if (mode == Mode.CONTAINMENT) {
            if (reductionFraction >= 0.70) insights.add("OUTBREAK_CONTAINED");
            else if (reductionFraction < 0.20) insights.add("OUTBREAK_RESILIENT");
        }

        // 13. Seeds list.
        List<Object> seedList = new ArrayList<>();
        for (SeedDecision<?> d : finalDecisions) seedList.add(d.node);

        String generatedAt = Instant.now(clock).atOffset(ZoneOffset.UTC).toString();
        return new Plan(mode, effectiveBudget, n, seedList, finalDecisions, alternates,
                baseline, expectedCoverage, expectedReduction,
                coverageFraction, reductionFraction, grade, playbook, insights,
                sims, closeMargin, generatedAt);
    }

    // -- Independent Cascade simulation ------------------------------------

    /**
     * Run R simulations of IC seeded from seedIdx (and blocked from blockedIdx,
     * meaning those nodes cannot be activated and cannot propagate).
     * Returns array {meanActivated, varianceUnused}. If {@code accumulator}
     * is non-null, populated with the union of activated nodes across sims.
     */
    private double[] simulate(List<int[]> adjN, List<double[]> adjP,
                              Set<Integer> seedIdx, Set<Integer> blockedIdx,
                              int sims, long baseSeed, Set<Integer> accumulator) {
        int n = adjN.size();
        if (seedIdx.isEmpty()) return new double[]{0.0, 0.0};
        long total = 0;
        boolean[] active = new boolean[n];
        boolean[] blocked = new boolean[n];
        for (Integer b : blockedIdx) blocked[b] = true;
        int[] queue = new int[n];
        for (int sim = 0; sim < sims; sim++) {
            Random rnd = new Random(baseSeed + sim * 1315423911L);
            for (int i = 0; i < n; i++) active[i] = false;
            int qHead = 0, qTail = 0;
            for (Integer s : seedIdx) {
                if (blocked[s]) continue;
                if (!active[s]) { active[s] = true; queue[qTail++] = s; }
            }
            while (qHead < qTail) {
                int u = queue[qHead++];
                int[] nbrs = adjN.get(u);
                double[] ps = adjP.get(u);
                for (int k = 0; k < nbrs.length; k++) {
                    int v = nbrs[k];
                    if (blocked[v] || active[v]) continue;
                    if (rnd.nextDouble() < ps[k]) {
                        active[v] = true;
                        queue[qTail++] = v;
                    }
                }
            }
            int cnt = 0;
            for (int i = 0; i < n; i++) if (active[i]) {
                cnt++;
                if (accumulator != null) accumulator.add(i);
            }
            total += cnt;
        }
        return new double[]{(double) total / sims, 0.0};
    }

    // -- Articulation points (iterative Tarjan) ----------------------------

    private boolean[] articulationPoints(List<int[]> adjN, int n) {
        boolean[] isArt = new boolean[n];
        int[] disc = new int[n];
        int[] low = new int[n];
        int[] parent = new int[n];
        int[] childCount = new int[n];
        Arrays.fill(disc, -1);
        Arrays.fill(parent, -1);
        int time = 0;
        for (int start = 0; start < n; start++) {
            if (disc[start] != -1) continue;
            // iterative DFS
            Deque<int[]> stack = new ArrayDeque<>();
            stack.push(new int[]{start, 0});
            disc[start] = low[start] = time++;
            int rootChildren = 0;
            while (!stack.isEmpty()) {
                int[] top = stack.peek();
                int u = top[0]; int i = top[1];
                int[] nbrs = adjN.get(u);
                if (i < nbrs.length) {
                    top[1] = i + 1;
                    int v = nbrs[i];
                    if (disc[v] == -1) {
                        parent[v] = u;
                        disc[v] = low[v] = time++;
                        if (u == start) rootChildren++;
                        stack.push(new int[]{v, 0});
                    } else if (v != parent[u]) {
                        low[u] = Math.min(low[u], disc[v]);
                    }
                } else {
                    stack.pop();
                    if (!stack.isEmpty()) {
                        int parentU = stack.peek()[0];
                        low[parentU] = Math.min(low[parentU], low[u]);
                        if (parentU != start && low[u] >= disc[parentU]) {
                            isArt[parentU] = true;
                        }
                    }
                }
            }
            if (rootChildren > 1) isArt[start] = true;
        }
        return isArt;
    }

    // -- Helpers -----------------------------------------------------------

    private static double clamp01(Double d) {
        if (d == null) return 0.0;
        if (d < 0) return 0.0;
        if (d > 1) return 1.0;
        return d;
    }

    private static double jaccard(Set<Integer> a, Set<Integer> b) {
        if (a.isEmpty() && b.isEmpty()) return 0.0;
        int inter = 0;
        Set<Integer> small = a.size() < b.size() ? a : b;
        Set<Integer> big = small == a ? b : a;
        for (Integer x : small) if (big.contains(x)) inter++;
        int union = a.size() + b.size() - inter;
        return union == 0 ? 0.0 : (double) inter / union;
    }

    private String suggestRedundantEdge(List<SeedDecision<?>> finals,
                                        List<int[]> adjN, List<V> vertices) {
        for (SeedDecision<?> d : finals) {
            if (!d.reasons.contains("BRIDGE_NODE")) continue;
            int seedIdx = vertices.indexOf(d.node);
            if (seedIdx < 0) continue;
            int[] nbrs = adjN.get(seedIdx);
            if (nbrs.length < 2) continue;
            // find two neighbours that are not themselves adjacent
            for (int i = 0; i < nbrs.length; i++) {
                for (int j = i + 1; j < nbrs.length; j++) {
                    int a = nbrs[i]; int b = nbrs[j];
                    int[] aN = adjN.get(a);
                    boolean adj = false;
                    for (int x : aN) if (x == b) { adj = true; break; }
                    if (!adj) {
                        return "(" + vertices.get(a) + "," + vertices.get(b) + ")";
                    }
                }
            }
        }
        return null;
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.3f", d);
    }

    // -- Renderers ---------------------------------------------------------

    public String render(Plan p) {
        StringBuilder sb = new StringBuilder();
        sb.append("GraphInfluenceSeedAdvisor [").append(p.mode).append("] grade=").append(p.grade)
                .append(" budget=").append(p.budget).append(" picked=").append(p.decisions.size())
                .append(" N=").append(p.graphSize).append('\n');
        if (p.mode == Mode.SPREAD) {
            sb.append("  expectedCoverage=").append(fmt(p.expectedCoverage))
                    .append(" coverageFraction=").append(fmt(p.coverageFraction)).append('\n');
        } else {
            sb.append("  baselineSpread=").append(fmt(p.baselineSpread))
                    .append(" expectedReduction=").append(fmt(p.expectedReduction))
                    .append(" reductionFraction=").append(fmt(p.reductionFraction)).append('\n');
        }
        sb.append("  simulationsUsed=").append(p.simulationsUsed)
                .append(" closeMarginRound=").append(p.closeMarginRound).append('\n');
        for (SeedDecision<?> d : p.decisions) {
            sb.append("  - ").append(d.node).append("  ").append(d.verdict).append('/').append(d.priority)
                    .append("  gain=").append(fmt(d.marginalGain))
                    .append("  cov=").append(fmt(d.coverageEstimate))
                    .append("  reasons=").append(d.reasons).append('\n');
        }
        if (!p.alternates.isEmpty()) {
            sb.append("  Alternates (skipped):\n");
            for (SeedDecision<?> d : p.alternates) {
                sb.append("    - ").append(d.node).append(" gain=").append(fmt(d.marginalGain)).append('\n');
            }
        }
        sb.append("  Playbook:\n");
        for (Action a : p.playbook) {
            sb.append("    [").append(a.priority).append("] ").append(a.id)
                    .append(" - ").append(a.label).append('\n');
        }
        if (!p.insights.isEmpty()) {
            sb.append("  Insights: ").append(p.insights).append('\n');
        }
        return sb.toString();
    }

    public String toMarkdown(Plan p) {
        StringBuilder sb = new StringBuilder();
        sb.append("# GraphInfluenceSeedAdvisor\n\n");
        sb.append("**Mode:** ").append(p.mode).append("  **Grade:** ").append(p.grade)
                .append("  **N:** ").append(p.graphSize).append("  **Budget:** ").append(p.budget)
                .append("  **Picked:** ").append(p.decisions.size()).append("\n\n");
        if (p.mode == Mode.SPREAD) {
            sb.append("Expected coverage: **").append(fmt(p.expectedCoverage))
                    .append("** (").append(fmt(p.coverageFraction * 100)).append("% of N)\n\n");
        } else {
            sb.append("Baseline spread: **").append(fmt(p.baselineSpread))
                    .append("**  Expected reduction: **").append(fmt(p.expectedReduction))
                    .append("** (").append(fmt(p.reductionFraction * 100)).append("% of baseline)\n\n");
        }
        sb.append("Simulations: ").append(p.simulationsUsed)
                .append("  Close margin round: ").append(p.closeMarginRound).append("\n\n");
        sb.append("## Seeds\n\n");
        sb.append("| Seed | Verdict | Priority | MarginalGain | Coverage | Reasons |\n");
        sb.append("|------|---------|----------|--------------|----------|---------|\n");
        for (SeedDecision<?> d : p.decisions) {
            sb.append("| ").append(d.node).append(" | ").append(d.verdict).append(" | ")
                    .append(d.priority).append(" | ").append(fmt(d.marginalGain))
                    .append(" | ").append(fmt(d.coverageEstimate))
                    .append(" | ").append(String.join(", ", d.reasons)).append(" |\n");
        }
        sb.append("\n## Playbook\n\n");
        for (Action a : p.playbook) {
            sb.append("- **[").append(a.priority).append("] ").append(a.id).append("** &mdash; ")
                    .append(a.label).append("  *(owner: ").append(a.owner)
                    .append(", blastRadius: ").append(a.blastRadius)
                    .append(", reversibility: ").append(a.reversibility).append(")*\n");
        }
        if (!p.insights.isEmpty()) {
            sb.append("\n## Insights\n\n");
            for (String s : p.insights) sb.append("- ").append(s).append('\n');
        }
        return sb.toString();
    }

    public String toJson(Plan p) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        kv(sb, "mode", q(p.mode.name())); sb.append(',');
        kv(sb, "grade", q(p.grade)); sb.append(',');
        kv(sb, "graphSize", String.valueOf(p.graphSize)); sb.append(',');
        kv(sb, "budget", String.valueOf(p.budget)); sb.append(',');
        kv(sb, "baselineSpread", num(p.baselineSpread)); sb.append(',');
        kv(sb, "expectedCoverage", num(p.expectedCoverage)); sb.append(',');
        kv(sb, "expectedReduction", num(p.expectedReduction)); sb.append(',');
        kv(sb, "coverageFraction", num(p.coverageFraction)); sb.append(',');
        kv(sb, "reductionFraction", num(p.reductionFraction)); sb.append(',');
        kv(sb, "simulationsUsed", String.valueOf(p.simulationsUsed)); sb.append(',');
        kv(sb, "closeMarginRound", String.valueOf(p.closeMarginRound)); sb.append(',');
        sb.append(q("decisions")).append(':').append('[');
        for (int i = 0; i < p.decisions.size(); i++) {
            SeedDecision<?> d = p.decisions.get(i);
            if (i > 0) sb.append(',');
            sb.append('{');
            kv(sb, "coverageEstimate", num(d.coverageEstimate)); sb.append(',');
            kv(sb, "marginalGain", num(d.marginalGain)); sb.append(',');
            kv(sb, "node", q(String.valueOf(d.node))); sb.append(',');
            kv(sb, "priority", q(d.priority.name())); sb.append(',');
            sb.append(q("reasons")).append(":[");
            List<String> rs = new ArrayList<>(d.reasons);
            Collections.sort(rs);
            for (int j = 0; j < rs.size(); j++) {
                if (j > 0) sb.append(',');
                sb.append(q(rs.get(j)));
            }
            sb.append("],");
            kv(sb, "verdict", q(d.verdict.name()));
            sb.append('}');
        }
        sb.append("],");
        sb.append(q("playbook")).append(':').append('[');
        for (int i = 0; i < p.playbook.size(); i++) {
            Action a = p.playbook.get(i);
            if (i > 0) sb.append(',');
            sb.append('{');
            kv(sb, "blastRadius", String.valueOf(a.blastRadius)); sb.append(',');
            kv(sb, "id", q(a.id)); sb.append(',');
            kv(sb, "label", q(a.label)); sb.append(',');
            kv(sb, "owner", q(a.owner)); sb.append(',');
            kv(sb, "priority", q(a.priority.name())); sb.append(',');
            kv(sb, "reason", q(a.reason)); sb.append(',');
            kv(sb, "reversibility", q(a.reversibility));
            sb.append('}');
        }
        sb.append("],");
        sb.append(q("insights")).append(":[");
        List<String> ins = new ArrayList<>(p.insights);
        Collections.sort(ins);
        for (int i = 0; i < ins.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(q(ins.get(i)));
        }
        sb.append("],");
        kv(sb, "generatedAt", q(p.generatedAt));
        sb.append('}');
        return sb.toString();
    }

    private static void kv(StringBuilder sb, String k, String v) {
        sb.append(q(k)).append(':').append(v);
    }

    private static String q(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': b.append("\\\\"); break;
                case '"': b.append("\\\""); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
        return b.toString();
    }

    private static String num(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return "0";
        return String.format(Locale.ROOT, "%.6f", d);
    }
}
