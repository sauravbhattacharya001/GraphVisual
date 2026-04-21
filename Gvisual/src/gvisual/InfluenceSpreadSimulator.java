package gvisual;

import edu.uci.ics.jung.graph.DirectedGraph;
import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Simulates how influence, information, or contagion spreads through a
 * network. Supports three propagation models commonly used in network
 * science and computational social science:
 *
 * <ul>
 *   <li><strong>Independent Cascade (IC)</strong> — each infected node gets
 *       one chance to infect each neighbor with probability p</li>
 *   <li><strong>Linear Threshold (LT)</strong> — a node activates when the
 *       fraction of its active neighbors exceeds its threshold</li>
 *   <li><strong>SIR (Susceptible-Infected-Recovered)</strong> — classic
 *       epidemiological model with infection rate β and recovery rate γ</li>
 * </ul>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Step-by-step simulation with per-round state snapshots</li>
 *   <li>Monte Carlo mode: run N simulations, aggregate statistics</li>
 *   <li>Influence maximization: find top-k seed nodes by expected spread</li>
 *   <li>Vaccination strategy: find optimal nodes to immunize</li>
 *   <li>Cascade timeline: full trace of who infected whom and when</li>
 * </ul>
 *
 * @author zalenix
 */
public class InfluenceSpreadSimulator {

    /** Propagation model to use. */
    public enum Model {
        INDEPENDENT_CASCADE,
        LINEAR_THRESHOLD,
        SIR
    }

    /** Node state in a simulation. */
    public enum NodeState {
        SUSCEPTIBLE,
        INFECTED,
        RECOVERED
    }

    private final Graph<String, Edge> graph;
    private final Random random;

    /**
     * Pre-cached adjacency: node → list of neighbor IDs.
     * Built once at construction, eliminating repeated JUNG API calls
     * during Monte Carlo simulations.
     */
    private final Map<String, List<String>> neighborCache;

    /**
     * Pre-cached Edge weights: "from\0to" → weight.
     * Only entries with Edge weight in (0, 1] are stored; missing keys
     * mean "use default probability". Avoids O(degree) findEdge() lookups
     * that dominated Monte Carlo hot loops.
     */
    private final Map<String, Double> edgeWeightCache;

    /**
     * Pre-cached predecessor lists for directed graphs: node → list of
     * predecessor (incoming) vertex IDs. For undirected graphs, this is
     * the same as neighborCache. Used by Linear Threshold simulation
     * which needs incoming influence, not outgoing.
     */
    private final Map<String, List<String>> predecessorCache;

    public InfluenceSpreadSimulator(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.random = new Random();
        this.neighborCache = buildNeighborCache();
        this.edgeWeightCache = buildEdgeWeightCache();
        this.predecessorCache = buildPredecessorCache();
    }

    public InfluenceSpreadSimulator(Graph<String, Edge> graph, long seed) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.random = new Random(seed);
        this.neighborCache = buildNeighborCache();
        this.edgeWeightCache = buildEdgeWeightCache();
        this.predecessorCache = buildPredecessorCache();
    }

    /**
     * Builds a cached neighbor list for every vertex, respecting directed
     * vs undirected graph semantics.
     */
    private Map<String, List<String>> buildNeighborCache() {
        boolean directed = graph instanceof DirectedGraph;
        Map<String, List<String>> cache = new HashMap<String, List<String>>();
        for (String node : graph.getVertices()) {
            Collection<String> nbrs = directed
                    ? graph.getSuccessors(node)
                    : graph.getNeighbors(node);
            cache.put(node, nbrs != null
                    ? new ArrayList<String>(nbrs)
                    : Collections.<String>emptyList());
        }
        return cache;
    }

    /**
     * Pre-caches Edge weights that override the default probability.
     * Only stores edges where weight is in (0, 1] — the range that
     * getEdgeProbability would use instead of the default. Key format:
     * "from\0to" (null-char separator — never appears in vertex IDs).
     */
    private Map<String, Double> buildEdgeWeightCache() {
        Map<String, Double> cache = new HashMap<String, Double>();
        for (Edge e : graph.getEdges()) {
            double w = e.getWeight();
            if (w > 0 && w <= 1.0) {
                Collection<String> endpoints = graph.getEndpoints(e);
                if (endpoints != null && endpoints.size() == 2) {
                    Iterator<String> it = endpoints.iterator();
                    String u = it.next();
                    String v = it.next();
                    cache.put(u + "\0" + v, w);
                    // For undirected graphs, cache both directions
                    if (!(graph instanceof DirectedGraph)) {
                        cache.put(v + "\0" + u, w);
                    }
                }
            }
        }
        return cache;
    }

    /**
     * Builds a cached predecessor list for every vertex. For directed graphs,
     * predecessors are nodes with edges pointing TO this node (incoming).
     * For undirected graphs, predecessors == neighbors.
     *
     * <p>The Linear Threshold model requires incoming influence: a node
     * activates when enough of its <b>predecessors</b> are active. Using
     * successors (outgoing edges) would incorrectly measure influence from
     * nodes this vertex points TO, rather than nodes pointing AT it.</p>
     */
    private Map<String, List<String>> buildPredecessorCache() {
        if (!(graph instanceof DirectedGraph)) {
            // Undirected: predecessors == neighbors
            return neighborCache;
        }
        DirectedGraph<String, Edge> dg = (DirectedGraph<String, Edge>) graph;
        Map<String, List<String>> cache = new HashMap<String, List<String>>();
        for (String node : graph.getVertices()) {
            Collection<String> preds = dg.getPredecessors(node);
            cache.put(node, preds != null
                    ? new ArrayList<String>(preds)
                    : Collections.<String>emptyList());
        }
        return cache;
    }

    /**
     * Returns the predecessors (incoming neighbors) of a node.
     * For undirected graphs, same as getNeighbors.
     */
    private Collection<String> getPredecessors(String node) {
        List<String> cached = predecessorCache.get(node);
        return cached != null ? cached : Collections.<String>emptyList();
    }

    // ─── Independent Cascade ────────────────────────────────────

    public SimulationResult simulateIC(Collection<String> seeds,
                                       double probability, int maxRounds) {
        validateSeeds(seeds);
        validateProbability(probability);

        Map<String, NodeState> state = initState(seeds);
        List<RoundSnapshot> snapshots = new ArrayList<>();
        List<InfectionEvent> timeline = new ArrayList<>();

        // Only include seeds that are actually in the graph
        Set<String> newlyInfected = new LinkedHashSet<>();
        for (String seed : seeds) {
            if (graph.containsVertex(seed)) newlyInfected.add(seed);
        }
        int round = 0;
        snapshots.add(createSnapshot(round, state));

        while (!newlyInfected.isEmpty()) {
            round++;
            if (maxRounds > 0 && round > maxRounds) break;

            Set<String> nextInfected = new LinkedHashSet<>();
            for (String node : newlyInfected) {
                for (String neighbor : getNeighbors(node)) {
                    if (state.get(neighbor) == NodeState.SUSCEPTIBLE) {
                        double edgeProb = getEdgeProbability(node, neighbor, probability);
                        if (random.nextDouble() < edgeProb) {
                            state.put(neighbor, NodeState.INFECTED);
                            nextInfected.add(neighbor);
                            timeline.add(new InfectionEvent(node, neighbor, round));
                        }
                    }
                }
            }
            for (String node : newlyInfected) {
                state.put(node, NodeState.RECOVERED);
            }
            newlyInfected = nextInfected;
            snapshots.add(createSnapshot(round, state));
        }

        return new SimulationResult(Model.INDEPENDENT_CASCADE, seeds,
                snapshots, timeline, state);
    }

    // ─── Linear Threshold ───────────────────────────────────────

    /**
     * Linear Threshold simulation.
     *
     * <p>Uses synchronous activation: all nodes are evaluated against
     * the current round's state, and newly activated nodes only become
     * visible in the next round.  This prevents activation order within
     * a single round from affecting results.</p>
     */
    public SimulationResult simulateLT(Collection<String> seeds, int maxRounds) {
        validateSeeds(seeds);

        Map<String, NodeState> state = initState(seeds);
        List<RoundSnapshot> snapshots = new ArrayList<>();
        List<InfectionEvent> timeline = new ArrayList<>();

        Map<String, Double> thresholds = new HashMap<>();
        for (String node : graph.getVertices()) {
            thresholds.put(node, random.nextDouble());
        }

        int round = 0;
        snapshots.add(createSnapshot(round, state));
        boolean changed = true;

        while (changed) {
            round++;
            if (maxRounds > 0 && round > maxRounds) break;
            changed = false;

            // Collect all activations for this round before applying any
            List<String> toActivate = new ArrayList<>();
            Map<String, String> activatedBy = new LinkedHashMap<>();

            for (String node : graph.getVertices()) {
                if (state.get(node) != NodeState.SUSCEPTIBLE) continue;

                // LT uses predecessors (incoming edges): a node activates
                // when enough nodes pointing TO it are active.
                Collection<String> influencers = getPredecessors(node);
                if (influencers.isEmpty()) continue;

                int activeNeighbors = 0;
                String anActiveNeighbor = null;
                for (String neighbor : influencers) {
                    if (state.get(neighbor) == NodeState.INFECTED ||
                        state.get(neighbor) == NodeState.RECOVERED) {
                        activeNeighbors++;
                        if (anActiveNeighbor == null) {
                            anActiveNeighbor = neighbor;
                        }
                    }
                }

                double fraction = (double) activeNeighbors / influencers.size();
                if (fraction >= thresholds.get(node)) {
                    toActivate.add(node);
                    if (anActiveNeighbor != null) {
                        activatedBy.put(node, anActiveNeighbor);
                    }
                }
            }

            // Apply all activations simultaneously
            for (String node : toActivate) {
                state.put(node, NodeState.INFECTED);
                changed = true;
                String source = activatedBy.get(node);
                if (source != null) {
                    timeline.add(new InfectionEvent(source, node, round));
                }
            }

            snapshots.add(createSnapshot(round, state));
        }

        return new SimulationResult(Model.LINEAR_THRESHOLD, seeds,
                snapshots, timeline, state);
    }

    // ─── SIR Model ──────────────────────────────────────────────

    public SimulationResult simulateSIR(Collection<String> seeds,
                                         double infectionRate,
                                         double recoveryRate,
                                         int maxRounds) {
        validateSeeds(seeds);
        validateProbability(infectionRate);
        validateProbability(recoveryRate);

        Map<String, NodeState> state = initState(seeds);
        List<RoundSnapshot> snapshots = new ArrayList<>();
        List<InfectionEvent> timeline = new ArrayList<>();

        // Track currently infected nodes explicitly to avoid iterating
        // all V vertices each round just to find the infected subset.
        // For large graphs with low infection rates, this reduces per-round
        // cost from O(V) to O(|infected| * avg_degree).
        Set<String> currentlyInfected = new LinkedHashSet<>();
        for (String seed : seeds) {
            if (graph.containsVertex(seed)) currentlyInfected.add(seed);
        }

        int round = 0;
        snapshots.add(createSnapshot(round, state));

        while (!currentlyInfected.isEmpty()) {
            round++;
            if (maxRounds > 0 && round > maxRounds) break;

            Set<String> toRecover = new LinkedHashSet<>();
            Set<String> toInfect = new LinkedHashSet<>();
            Map<String, String> infectedBy = new LinkedHashMap<>();

            for (String node : currentlyInfected) {
                for (String neighbor : getNeighbors(node)) {
                    if (state.get(neighbor) == NodeState.SUSCEPTIBLE &&
                        !toInfect.contains(neighbor)) {
                        double edgeProb = getEdgeProbability(node, neighbor, infectionRate);
                        if (random.nextDouble() < edgeProb) {
                            toInfect.add(neighbor);
                            infectedBy.put(neighbor, node);
                        }
                    }
                }
            }

            for (String node : currentlyInfected) {
                if (random.nextDouble() < recoveryRate) {
                    toRecover.add(node);
                }
            }

            for (String node : toInfect) {
                state.put(node, NodeState.INFECTED);
                currentlyInfected.add(node);
                String source = infectedBy.get(node);
                if (source != null) {
                    timeline.add(new InfectionEvent(source, node, round));
                }
            }
            for (String node : toRecover) {
                state.put(node, NodeState.RECOVERED);
                currentlyInfected.remove(node);
            }

            snapshots.add(createSnapshot(round, state));
        }

        return new SimulationResult(Model.SIR, seeds,
                snapshots, timeline, state);
    }

    // ─── Monte Carlo ────────────────────────────────────────────

    public MonteCarloResult monteCarlo(Collection<String> seeds,
                                        Model model, double probability,
                                        double recoveryRate, int maxRounds,
                                        int numTrials) {
        if (numTrials < 1) {
            throw new IllegalArgumentException("Number of trials must be >= 1");
        }

        List<Integer> spreads = new ArrayList<>();
        List<Integer> durations = new ArrayList<>();
        Map<String, Integer> infectionFrequency = new LinkedHashMap<>();

        for (int i = 0; i < numTrials; i++) {
            // Use lightweight simulation (no snapshots/timeline) to avoid
            // O(rounds × V) deep-copy overhead per trial. Monte Carlo only
            // needs final state + round count, not per-round snapshots.
            LightweightResult lr;
            switch (model) {
                case INDEPENDENT_CASCADE:
                    lr = simulateICLightweight(seeds, probability, maxRounds); break;
                case LINEAR_THRESHOLD:
                    lr = simulateLTLightweight(seeds, maxRounds); break;
                case SIR:
                    lr = simulateSIRLightweight(seeds, probability, recoveryRate, maxRounds); break;
                default:
                    throw new IllegalArgumentException("Unknown model: " + model);
            }

            spreads.add(lr.totalInfected);
            durations.add(lr.rounds);

            for (Map.Entry<String, NodeState> entry : lr.finalState.entrySet()) {
                if (entry.getValue() == NodeState.INFECTED ||
                    entry.getValue() == NodeState.RECOVERED) {
                    infectionFrequency.merge(entry.getKey(), 1, Integer::sum);
                }
            }
        }

        return new MonteCarloResult(numTrials, spreads, durations, infectionFrequency);
    }

    // ─── Influence Maximization ─────────────────────────────────

    public List<SeedCandidate> findTopKSeeds(int k, Model model,
                                              double probability,
                                              int numTrials) {
        if (k < 1) throw new IllegalArgumentException("k must be >= 1");
        if (graph.getVertexCount() == 0) return Collections.emptyList();

        k = Math.min(k, graph.getVertexCount());
        Set<String> selected = new LinkedHashSet<>();
        List<SeedCandidate> result = new ArrayList<>();

        for (int i = 0; i < k; i++) {
            String bestNode = null;
            double bestGain = -1;

            // Hoist base spread computation outside the inner loop:
            // selected doesn't change while we evaluate candidates,
            // so recomputing it per-candidate wastes V * numTrials simulations.
            double baseSpread = 0;
            if (!selected.isEmpty()) {
                MonteCarloResult baseResult = monteCarlo(selected, model,
                        probability, 0.3, 0, numTrials);
                baseSpread = baseResult.getAverageSpread();
            }

            for (String candidate : graph.getVertices()) {
                if (selected.contains(candidate)) continue;

                Set<String> trial = new LinkedHashSet<>(selected);
                trial.add(candidate);

                MonteCarloResult mcResult = monteCarlo(trial, model,
                        probability, 0.3, 0, numTrials);
                double gain = mcResult.getAverageSpread() - baseSpread;

                if (gain > bestGain) {
                    bestGain = gain;
                    bestNode = candidate;
                }
            }

            if (bestNode != null) {
                selected.add(bestNode);
                result.add(new SeedCandidate(bestNode, bestGain, i + 1));
            }
        }

        return result;
    }

    // ─── Vaccination Strategy ───────────────────────────────────

    public VaccinationStrategy findVaccinationTargets(int k) {
        if (k < 1) throw new IllegalArgumentException("k must be >= 1");

        List<Map.Entry<String, Integer>> degrees = new ArrayList<>();
        for (String node : graph.getVertices()) {
            degrees.add(new AbstractMap.SimpleEntry<>(node, graph.degree(node)));
        }
        degrees.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        List<String> targets = new ArrayList<>();
        Set<String> targetSet = new HashSet<>();
        for (int i = 0; i < Math.min(k, degrees.size()); i++) {
            targets.add(degrees.get(i).getKey());
            targetSet.add(degrees.get(i).getKey());
        }

        // Count distinct edges blocked: an Edge is blocked if at least one
        // of its endpoints is a vaccination target. Counting by degree
        // double-counts edges where both endpoints are vaccinated.
        int totalEdgesBlocked = 0;
        for (Edge e : graph.getEdges()) {
            Collection<String> endpoints = graph.getEndpoints(e);
            boolean blocked = false;
            for (String ep : endpoints) {
                if (targetSet.contains(ep)) {
                    blocked = true;
                    break;
                }
            }
            if (blocked) {
                totalEdgesBlocked++;
            }
        }

        int totalEdges = graph.getEdgeCount();
        double coverageRatio = totalEdges > 0 ?
                (double) totalEdgesBlocked / totalEdges : 0.0;

        return new VaccinationStrategy(targets, totalEdgesBlocked, coverageRatio);
    }

    // ─── Lightweight simulation (Monte Carlo fast path) ───────────

    /**
     * Minimal result for Monte Carlo: final state + round count only.
     * Avoids the per-round LinkedHashMap deep-copies and InfectionEvent
     * timeline that {@link SimulationResult} carries.
     */
    private static class LightweightResult {
        final Map<String, NodeState> finalState;
        final int rounds;
        final int totalInfected;

        LightweightResult(Map<String, NodeState> finalState, int rounds) {
            this.finalState = finalState;
            this.rounds = rounds;
            int cnt = 0;
            for (NodeState s : finalState.values())
                if (s == NodeState.INFECTED || s == NodeState.RECOVERED) cnt++;
            this.totalInfected = cnt;
        }
    }

    private LightweightResult simulateICLightweight(Collection<String> seeds,
                                                     double probability, int maxRounds) {
        Map<String, NodeState> state = initState(seeds);
        Set<String> newlyInfected = new LinkedHashSet<>();
        for (String seed : seeds)
            if (graph.containsVertex(seed)) newlyInfected.add(seed);
        int round = 0;

        while (!newlyInfected.isEmpty()) {
            round++;
            if (maxRounds > 0 && round > maxRounds) break;
            Set<String> nextInfected = new LinkedHashSet<>();
            for (String node : newlyInfected) {
                for (String neighbor : getNeighbors(node)) {
                    if (state.get(neighbor) == NodeState.SUSCEPTIBLE) {
                        double edgeProb = getEdgeProbability(node, neighbor, probability);
                        if (random.nextDouble() < edgeProb) {
                            state.put(neighbor, NodeState.INFECTED);
                            nextInfected.add(neighbor);
                        }
                    }
                }
            }
            for (String node : newlyInfected) state.put(node, NodeState.RECOVERED);
            newlyInfected = nextInfected;
        }
        return new LightweightResult(state, round);
    }

    private LightweightResult simulateLTLightweight(Collection<String> seeds, int maxRounds) {
        Map<String, NodeState> state = initState(seeds);
        Map<String, Double> thresholds = new HashMap<>();
        for (String node : graph.getVertices()) thresholds.put(node, random.nextDouble());

        int round = 0;
        boolean changed = true;
        while (changed) {
            round++;
            if (maxRounds > 0 && round > maxRounds) break;
            changed = false;
            List<String> toActivate = new ArrayList<>();
            for (String node : graph.getVertices()) {
                if (state.get(node) != NodeState.SUSCEPTIBLE) continue;
                Collection<String> influencers = getPredecessors(node);
                if (influencers.isEmpty()) continue;
                int active = 0;
                for (String nb : influencers)
                    if (state.get(nb) == NodeState.INFECTED || state.get(nb) == NodeState.RECOVERED)
                        active++;
                if ((double) active / influencers.size() >= thresholds.get(node))
                    toActivate.add(node);
            }
            for (String node : toActivate) {
                state.put(node, NodeState.INFECTED);
                changed = true;
            }
        }
        return new LightweightResult(state, round);
    }

    private LightweightResult simulateSIRLightweight(Collection<String> seeds,
                                                      double infectionRate,
                                                      double recoveryRate, int maxRounds) {
        Map<String, NodeState> state = initState(seeds);
        Set<String> currentlyInfected = new LinkedHashSet<>();
        for (String seed : seeds)
            if (graph.containsVertex(seed)) currentlyInfected.add(seed);
        int round = 0;

        while (!currentlyInfected.isEmpty()) {
            round++;
            if (maxRounds > 0 && round > maxRounds) break;
            Set<String> toInfect = new LinkedHashSet<>();
            Set<String> toRecover = new LinkedHashSet<>();
            for (String node : currentlyInfected) {
                for (String neighbor : getNeighbors(node)) {
                    if (state.get(neighbor) == NodeState.SUSCEPTIBLE && !toInfect.contains(neighbor)) {
                        double edgeProb = getEdgeProbability(node, neighbor, infectionRate);
                        if (random.nextDouble() < edgeProb) toInfect.add(neighbor);
                    }
                }
            }
            for (String node : currentlyInfected)
                if (random.nextDouble() < recoveryRate) toRecover.add(node);
            for (String node : toInfect) {
                state.put(node, NodeState.INFECTED);
                currentlyInfected.add(node);
            }
            for (String node : toRecover) {
                state.put(node, NodeState.RECOVERED);
                currentlyInfected.remove(node);
            }
        }
        return new LightweightResult(state, round);
    }

    // ─── Helpers ────────────────────────────────────────────────

    private void validateSeeds(Collection<String> seeds) {
        if (seeds == null || seeds.isEmpty())
            throw new IllegalArgumentException("Seeds must not be null or empty");
    }

    private void validateProbability(double p) {
        if (p < 0.0 || p > 1.0)
            throw new IllegalArgumentException(
                    "Probability must be between 0.0 and 1.0, got: " + p);
    }

    private Map<String, NodeState> initState(Collection<String> seeds) {
        Map<String, NodeState> state = new LinkedHashMap<>();
        for (String node : graph.getVertices())
            state.put(node, NodeState.SUSCEPTIBLE);
        for (String seed : seeds)
            if (graph.containsVertex(seed))
                state.put(seed, NodeState.INFECTED);
        return state;
    }

    private Collection<String> getNeighbors(String node) {
        List<String> cached = neighborCache.get(node);
        return cached != null ? cached : Collections.<String>emptyList();
    }

    private double getEdgeProbability(String from, String to, double defaultProb) {
        Double cached = edgeWeightCache.get(from + "\0" + to);
        return cached != null ? cached : defaultProb;
    }

    private RoundSnapshot createSnapshot(int round, Map<String, NodeState> state) {
        int susceptible = 0, infected = 0, recovered = 0;
        for (NodeState s : state.values()) {
            switch (s) {
                case SUSCEPTIBLE: susceptible++; break;
                case INFECTED: infected++; break;
                case RECOVERED: recovered++; break;
            }
        }
        return new RoundSnapshot(round, susceptible, infected, recovered,
                new LinkedHashMap<>(state));
    }

    // ─── Result Classes ─────────────────────────────────────────

    public static class InfectionEvent {
        private final String source;
        private final String target;
        private final int round;

        public InfectionEvent(String source, String target, int round) {
            this.source = source; this.target = target; this.round = round;
        }

        public String getSource() { return source; }
        public String getTarget() { return target; }
        public int getRound() { return round; }

        @Override
        public String toString() {
            return String.format("Round %d: %s -> %s", round, source, target);
        }
    }

    public static class RoundSnapshot {
        private final int round;
        private final int susceptible;
        private final int infected;
        private final int recovered;
        private final Map<String, NodeState> nodeStates;

        public RoundSnapshot(int round, int susceptible, int infected,
                             int recovered, Map<String, NodeState> nodeStates) {
            this.round = round; this.susceptible = susceptible;
            this.infected = infected; this.recovered = recovered;
            this.nodeStates = Collections.unmodifiableMap(nodeStates);
        }

        public int getRound() { return round; }
        public int getSusceptible() { return susceptible; }
        public int getInfected() { return infected; }
        public int getRecovered() { return recovered; }
        public Map<String, NodeState> getNodeStates() { return nodeStates; }

        @Override
        public String toString() {
            return String.format("Round %d: S=%d I=%d R=%d",
                    round, susceptible, infected, recovered);
        }
    }

    public static class SimulationResult {
        private final Model model;
        private final Collection<String> seeds;
        private final List<RoundSnapshot> snapshots;
        private final List<InfectionEvent> timeline;
        private final Map<String, NodeState> finalState;

        public SimulationResult(Model model, Collection<String> seeds,
                                List<RoundSnapshot> snapshots,
                                List<InfectionEvent> timeline,
                                Map<String, NodeState> finalState) {
            this.model = model;
            this.seeds = Collections.unmodifiableCollection(new ArrayList<>(seeds));
            this.snapshots = Collections.unmodifiableList(snapshots);
            this.timeline = Collections.unmodifiableList(timeline);
            this.finalState = Collections.unmodifiableMap(finalState);
        }

        public Model getModel() { return model; }
        public Collection<String> getSeeds() { return seeds; }
        public List<RoundSnapshot> getSnapshots() { return snapshots; }
        public List<InfectionEvent> getTimeline() { return timeline; }
        public Map<String, NodeState> getFinalState() { return finalState; }

        public int getRoundCount() {
            return snapshots.isEmpty() ? 0 : snapshots.get(snapshots.size() - 1).getRound();
        }

        public int getTotalInfected() {
            int count = 0;
            for (NodeState s : finalState.values())
                if (s == NodeState.INFECTED || s == NodeState.RECOVERED) count++;
            return count;
        }

        public double getSpreadRatio() {
            return finalState.isEmpty() ? 0.0 :
                    (double) getTotalInfected() / finalState.size();
        }

        public int getPeakInfected() {
            int peak = 0;
            for (RoundSnapshot snap : snapshots)
                peak = Math.max(peak, snap.getInfected());
            return peak;
        }

        public int getPeakRound() {
            int peak = 0, peakRound = 0;
            for (RoundSnapshot snap : snapshots) {
                if (snap.getInfected() > peak) {
                    peak = snap.getInfected();
                    peakRound = snap.getRound();
                }
            }
            return peakRound;
        }

        public Set<String> getUninfectedNodes() {
            Set<String> uninfected = new LinkedHashSet<>();
            for (Map.Entry<String, NodeState> entry : finalState.entrySet())
                if (entry.getValue() == NodeState.SUSCEPTIBLE)
                    uninfected.add(entry.getKey());
            return uninfected;
        }

        @Override
        public String toString() {
            return String.format(
                    "SimulationResult[model=%s, seeds=%d, rounds=%d, " +
                    "infected=%d/%d (%.1f%%), peak=%d at round %d]",
                    model, seeds.size(), getRoundCount(),
                    getTotalInfected(), finalState.size(),
                    getSpreadRatio() * 100,
                    getPeakInfected(), getPeakRound());
        }
    }

    public static class MonteCarloResult {
        private final int numTrials;
        private final List<Integer> spreads;
        private final List<Integer> durations;
        private final Map<String, Integer> infectionFrequency;

        public MonteCarloResult(int numTrials, List<Integer> spreads,
                                List<Integer> durations,
                                Map<String, Integer> infectionFrequency) {
            this.numTrials = numTrials;
            this.spreads = Collections.unmodifiableList(spreads);
            this.durations = Collections.unmodifiableList(durations);
            this.infectionFrequency = Collections.unmodifiableMap(infectionFrequency);
        }

        public int getNumTrials() { return numTrials; }
        public List<Integer> getSpreads() { return spreads; }
        public List<Integer> getDurations() { return durations; }
        public Map<String, Integer> getInfectionFrequency() { return infectionFrequency; }

        public double getAverageSpread() {
            if (spreads.isEmpty()) return 0.0;
            long sum = 0;
            for (int s : spreads) sum += s;
            return (double) sum / spreads.size();
        }

        public double getAverageDuration() {
            if (durations.isEmpty()) return 0.0;
            long sum = 0;
            for (int d : durations) sum += d;
            return (double) sum / durations.size();
        }

        public int getMaxSpread() {
            int max = 0;
            for (int s : spreads) max = Math.max(max, s);
            return max;
        }

        public int getMinSpread() {
            if (spreads.isEmpty()) return 0;
            int min = Integer.MAX_VALUE;
            for (int s : spreads) min = Math.min(min, s);
            return min;
        }

        public double getSpreadStdDev() {
            if (spreads.size() < 2) return 0.0;
            double avg = getAverageSpread();
            double sumSq = 0;
            for (int s : spreads) { double diff = s - avg; sumSq += diff * diff; }
            return Math.sqrt(sumSq / spreads.size());
        }

        public double getNodeInfectionProbability(String node) {
            Integer freq = infectionFrequency.get(node);
            return freq == null ? 0.0 : (double) freq / numTrials;
        }

        public List<Map.Entry<String, Integer>> getTopInfected(int k) {
            List<Map.Entry<String, Integer>> sorted =
                    new ArrayList<>(infectionFrequency.entrySet());
            sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            return sorted.subList(0, Math.min(k, sorted.size()));
        }

        @Override
        public String toString() {
            return String.format(
                    "MonteCarloResult[trials=%d, avgSpread=%.1f, " +
                    "min=%d, max=%d, stdDev=%.1f, avgDuration=%.1f]",
                    numTrials, getAverageSpread(),
                    getMinSpread(), getMaxSpread(),
                    getSpreadStdDev(), getAverageDuration());
        }
    }

    public static class SeedCandidate {
        private final String node;
        private final double marginalGain;
        private final int rank;

        public SeedCandidate(String node, double marginalGain, int rank) {
            this.node = node; this.marginalGain = marginalGain; this.rank = rank;
        }

        public String getNode() { return node; }
        public double getMarginalGain() { return marginalGain; }
        public int getRank() { return rank; }

        @Override
        public String toString() {
            return String.format("#%d: %s (marginal gain: %.2f)",
                    rank, node, marginalGain);
        }
    }

    public static class VaccinationStrategy {
        private final List<String> targets;
        private final int edgesBlocked;
        private final double coverageRatio;

        public VaccinationStrategy(List<String> targets, int edgesBlocked,
                                    double coverageRatio) {
            this.targets = Collections.unmodifiableList(targets);
            this.edgesBlocked = edgesBlocked;
            this.coverageRatio = coverageRatio;
        }

        public List<String> getTargets() { return targets; }
        public int getEdgesBlocked() { return edgesBlocked; }
        public double getCoverageRatio() { return coverageRatio; }

        @Override
        public String toString() {
            return String.format(
                    "VaccinationStrategy[targets=%d, edgesBlocked=%d, coverage=%.1f%%]",
                    targets.size(), edgesBlocked, coverageRatio * 100);
        }
    }
}
