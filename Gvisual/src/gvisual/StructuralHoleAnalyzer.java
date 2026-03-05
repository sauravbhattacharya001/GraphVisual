package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Structural Hole Analyzer — identifies brokerage opportunities and
 * information control positions in a network.
 *
 * <p>Based on Ronald Burt's structural holes theory, which argues that
 * individuals who bridge otherwise disconnected groups gain competitive
 * advantages through information access, timing, and referral control.</p>
 *
 * <h3>Metrics computed per vertex:</h3>
 * <ul>
 *   <li><b>Effective size</b> — number of alters minus redundancy
 *       (non-redundant contacts provide unique information)</li>
 *   <li><b>Efficiency</b> — effective size / degree (proportion of
 *       non-redundant contacts)</li>
 *   <li><b>Constraint</b> — how much ego's network is concentrated
 *       in a single cluster (low constraint = more structural holes)</li>
 *   <li><b>Hierarchy</b> — how concentrated the constraint is across
 *       contacts (high = one contact dominates)</li>
 *   <li><b>Brokerage score</b> — composite 0–100 score combining
 *       efficiency, inverse constraint, and bridging position</li>
 *   <li><b>Bridge score</b> — fraction of ego's edges that are bridges
 *       (connecting otherwise disconnected components)</li>
 * </ul>
 *
 * <p>Applications in student community analysis:</p>
 * <ul>
 *   <li>Finding students who bridge different social circles</li>
 *   <li>Identifying information brokers and gatekeepers</li>
 *   <li>Measuring network redundancy and closure</li>
 *   <li>Detecting brokerage opportunities for collaboration</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 * StructuralHoleAnalyzer analyzer = new StructuralHoleAnalyzer(graph);
 * VertexMetrics vm = analyzer.analyze("Alice");
 * List&lt;VertexMetrics&gt; all = analyzer.analyzeAll();
 * List&lt;VertexMetrics&gt; top = analyzer.topBrokers(5);
 * BrokerageReport report = analyzer.generateReport();
 * String text = analyzer.formatReport(report);
 * </pre>
 *
 * @author zalenix
 */
public class StructuralHoleAnalyzer {

    private final Graph<String, edge> graph;
    private Map<String, Set<String>> neighborCache;

    /**
     * Creates a new StructuralHoleAnalyzer for the given graph.
     *
     * @param graph the JUNG graph to analyze (must not be null)
     * @throws IllegalArgumentException if graph is null
     */
    public StructuralHoleAnalyzer(Graph<String, edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        buildNeighborCache();
    }

    private void buildNeighborCache() {
        neighborCache = new HashMap<>();
        for (String v : graph.getVertices()) {
            Set<String> nbrs = new LinkedHashSet<>();
            for (String n : graph.getNeighbors(v)) {
                nbrs.add(n);
            }
            neighborCache.put(v, nbrs);
        }
    }

    // ── Result classes ──────────────────────────────────────────

    /**
     * Structural hole metrics for a single vertex.
     */
    public static class VertexMetrics {
        private final String vertex;
        private final int degree;
        private final double effectiveSize;
        private final double efficiency;
        private final double constraint;
        private final double hierarchy;
        private final double brokerageScore;
        private final double bridgeScore;
        private final Map<String, Double> pairwiseConstraint;

        public VertexMetrics(String vertex, int degree,
                             double effectiveSize, double efficiency,
                             double constraint, double hierarchy,
                             double brokerageScore, double bridgeScore,
                             Map<String, Double> pairwiseConstraint) {
            this.vertex = vertex;
            this.degree = degree;
            this.effectiveSize = effectiveSize;
            this.efficiency = efficiency;
            this.constraint = constraint;
            this.hierarchy = hierarchy;
            this.brokerageScore = brokerageScore;
            this.bridgeScore = bridgeScore;
            this.pairwiseConstraint = Collections.unmodifiableMap(pairwiseConstraint);
        }

        public String getVertex() { return vertex; }
        public int getDegree() { return degree; }
        public double getEffectiveSize() { return effectiveSize; }
        public double getEfficiency() { return efficiency; }
        public double getConstraint() { return constraint; }
        public double getHierarchy() { return hierarchy; }
        public double getBrokerageScore() { return brokerageScore; }
        public double getBridgeScore() { return bridgeScore; }
        public Map<String, Double> getPairwiseConstraint() { return pairwiseConstraint; }

        /**
         * Classifies the brokerage role based on metrics.
         *
         * @return role classification string
         */
        public String getRole() {
            if (degree == 0) return "Isolate";
            if (degree == 1) return "Peripheral";
            if (brokerageScore >= 80) return "Key Broker";
            if (brokerageScore >= 60) return "Broker";
            if (brokerageScore >= 40) return "Moderate Broker";
            if (constraint > 0.5) return "Constrained";
            return "Embedded";
        }

        @Override
        public String toString() {
            return String.format("%s: effSize=%.2f eff=%.3f constraint=%.3f " +
                            "hierarchy=%.3f brokerage=%.1f bridge=%.3f [%s]",
                    vertex, effectiveSize, efficiency, constraint,
                    hierarchy, brokerageScore, bridgeScore, getRole());
        }
    }

    /**
     * Overall brokerage report for the network.
     */
    public static class BrokerageReport {
        private final int vertexCount;
        private final int edgeCount;
        private final List<VertexMetrics> allMetrics;
        private final double avgConstraint;
        private final double avgEfficiency;
        private final double avgBrokerage;
        private final double networkClosure;
        private final List<VertexMetrics> topBrokers;
        private final List<VertexMetrics> mostConstrained;
        private final List<BridgingEdge> bridgingEdges;

        public BrokerageReport(int vertexCount, int edgeCount,
                               List<VertexMetrics> allMetrics,
                               double avgConstraint, double avgEfficiency,
                               double avgBrokerage, double networkClosure,
                               List<VertexMetrics> topBrokers,
                               List<VertexMetrics> mostConstrained,
                               List<BridgingEdge> bridgingEdges) {
            this.vertexCount = vertexCount;
            this.edgeCount = edgeCount;
            this.allMetrics = Collections.unmodifiableList(allMetrics);
            this.avgConstraint = avgConstraint;
            this.avgEfficiency = avgEfficiency;
            this.avgBrokerage = avgBrokerage;
            this.networkClosure = networkClosure;
            this.topBrokers = Collections.unmodifiableList(topBrokers);
            this.mostConstrained = Collections.unmodifiableList(mostConstrained);
            this.bridgingEdges = Collections.unmodifiableList(bridgingEdges);
        }

        public int getVertexCount() { return vertexCount; }
        public int getEdgeCount() { return edgeCount; }
        public List<VertexMetrics> getAllMetrics() { return allMetrics; }
        public double getAvgConstraint() { return avgConstraint; }
        public double getAvgEfficiency() { return avgEfficiency; }
        public double getAvgBrokerage() { return avgBrokerage; }
        public double getNetworkClosure() { return networkClosure; }
        public List<VertexMetrics> getTopBrokers() { return topBrokers; }
        public List<VertexMetrics> getMostConstrained() { return mostConstrained; }
        public List<BridgingEdge> getBridgingEdges() { return bridgingEdges; }
    }

    /**
     * An edge that bridges structural holes.
     */
    public static class BridgingEdge {
        private final String vertex1;
        private final String vertex2;
        private final double bridgeStrength;
        private final int commonNeighbors;

        public BridgingEdge(String vertex1, String vertex2,
                            double bridgeStrength, int commonNeighbors) {
            this.vertex1 = vertex1;
            this.vertex2 = vertex2;
            this.bridgeStrength = bridgeStrength;
            this.commonNeighbors = commonNeighbors;
        }

        public String getVertex1() { return vertex1; }
        public String getVertex2() { return vertex2; }
        public double getBridgeStrength() { return bridgeStrength; }
        public int getCommonNeighbors() { return commonNeighbors; }

        @Override
        public String toString() {
            return String.format("%s -- %s  (strength=%.3f, common=%d)",
                    vertex1, vertex2, bridgeStrength, commonNeighbors);
        }
    }

    // ── Analysis methods ────────────────────────────────────────

    /**
     * Compute structural hole metrics for a single vertex.
     *
     * @param vertex the vertex to analyze
     * @return metrics for the vertex
     * @throws IllegalArgumentException if vertex is null or not in graph
     */
    public VertexMetrics analyze(String vertex) {
        validateVertex(vertex);

        Set<String> neighbors = neighborCache.getOrDefault(vertex, Collections.emptySet());
        int degree = neighbors.size();

        if (degree == 0) {
            return new VertexMetrics(vertex, 0, 0, 0, 0, 0, 0, 0, Collections.emptyMap());
        }

        double effectiveSize = computeEffectiveSize(vertex, neighbors);
        double efficiency = effectiveSize / degree;
        Map<String, Double> pairwise = computePairwiseConstraint(vertex, neighbors);
        double constraint = pairwise.values().stream().mapToDouble(Double::doubleValue).sum();
        double hierarchy = computeHierarchy(constraint, pairwise);
        double bridgeScore = computeBridgeScore(vertex, neighbors);
        double brokerageScore = computeBrokerageScore(efficiency, constraint, bridgeScore);

        return new VertexMetrics(vertex, degree, effectiveSize, efficiency,
                constraint, hierarchy, brokerageScore, bridgeScore, pairwise);
    }

    /**
     * Compute structural hole metrics for all vertices.
     *
     * @return list of metrics sorted by brokerage score (descending)
     */
    public List<VertexMetrics> analyzeAll() {
        List<VertexMetrics> results = new ArrayList<>();
        for (String v : graph.getVertices()) {
            results.add(analyze(v));
        }
        results.sort((a, b) -> Double.compare(b.getBrokerageScore(), a.getBrokerageScore()));
        return results;
    }

    /**
     * Get the top N brokers in the network.
     *
     * @param n number of brokers to return
     * @return list of top brokers sorted by brokerage score
     * @throws IllegalArgumentException if n &lt; 1
     */
    public List<VertexMetrics> topBrokers(int n) {
        if (n < 1) throw new IllegalArgumentException("n must be >= 1");
        List<VertexMetrics> all = analyzeAll();
        return all.subList(0, Math.min(n, all.size()));
    }

    /**
     * Get the most constrained vertices (least brokerage opportunity).
     *
     * @param n number of vertices to return
     * @return list sorted by constraint (descending)
     * @throws IllegalArgumentException if n &lt; 1
     */
    public List<VertexMetrics> mostConstrained(int n) {
        if (n < 1) throw new IllegalArgumentException("n must be >= 1");
        List<VertexMetrics> all = analyzeAll();
        all.sort((a, b) -> Double.compare(b.getConstraint(), a.getConstraint()));
        return all.subList(0, Math.min(n, all.size()));
    }

    /**
     * Find edges that bridge structural holes (low overlap between endpoints).
     *
     * @return list of bridging edges sorted by bridge strength (descending)
     */
    public List<BridgingEdge> findBridgingEdges() {
        List<BridgingEdge> bridges = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (edge e : graph.getEdges()) {
            Collection<String> endpoints = graph.getEndpoints(e);
            if (endpoints == null || endpoints.size() != 2) continue;

            Iterator<String> it = endpoints.iterator();
            String v1 = it.next();
            String v2 = it.next();

            String key = v1.compareTo(v2) < 0 ? v1 + "|" + v2 : v2 + "|" + v1;
            if (!seen.add(key)) continue;

            Set<String> n1 = neighborCache.getOrDefault(v1, Collections.emptySet());
            Set<String> n2 = neighborCache.getOrDefault(v2, Collections.emptySet());

            // Common neighbors (excluding each other)
            Set<String> common = new HashSet<>(n1);
            common.retainAll(n2);
            common.remove(v1);
            common.remove(v2);

            // Bridge strength: inverse of neighborhood overlap
            Set<String> union = new HashSet<>(n1);
            union.addAll(n2);
            union.remove(v1);
            union.remove(v2);

            double overlap = union.isEmpty() ? 0.0 :
                    (double) common.size() / union.size();
            double strength = 1.0 - overlap;

            if (strength > 0.5) {
                bridges.add(new BridgingEdge(v1, v2, strength, common.size()));
            }
        }

        bridges.sort((a, b) -> Double.compare(b.getBridgeStrength(), a.getBridgeStrength()));
        return bridges;
    }

    /**
     * Generate a comprehensive brokerage report for the entire network.
     *
     * @return the brokerage report
     */
    public BrokerageReport generateReport() {
        List<VertexMetrics> all = analyzeAll();

        double avgConstraint = 0, avgEfficiency = 0, avgBrokerage = 0;
        if (!all.isEmpty()) {
            for (VertexMetrics vm : all) {
                avgConstraint += vm.getConstraint();
                avgEfficiency += vm.getEfficiency();
                avgBrokerage += vm.getBrokerageScore();
            }
            avgConstraint /= all.size();
            avgEfficiency /= all.size();
            avgBrokerage /= all.size();
        }

        // Network closure: average constraint across all vertices
        // High closure = dense, redundant network with few structural holes
        double networkClosure = avgConstraint;

        List<VertexMetrics> topBrokers = all.stream()
                .filter(vm -> vm.getDegree() > 0)
                .sorted((a, b) -> Double.compare(b.getBrokerageScore(), a.getBrokerageScore()))
                .limit(10)
                .collect(Collectors.toList());

        List<VertexMetrics> mostConstrained = all.stream()
                .filter(vm -> vm.getDegree() > 0)
                .sorted((a, b) -> Double.compare(b.getConstraint(), a.getConstraint()))
                .limit(10)
                .collect(Collectors.toList());

        List<BridgingEdge> bridgingEdges = findBridgingEdges();

        return new BrokerageReport(
                graph.getVertexCount(), graph.getEdgeCount(),
                all, avgConstraint, avgEfficiency, avgBrokerage,
                networkClosure, topBrokers, mostConstrained, bridgingEdges
        );
    }

    /**
     * Format a brokerage report as human-readable text.
     *
     * @param report the report to format
     * @return formatted text report
     */
    public String formatReport(BrokerageReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║           Structural Hole Analysis Report                  ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════╝\n\n");

        sb.append(String.format("Network: %d vertices, %d edges\n",
                report.getVertexCount(), report.getEdgeCount()));
        sb.append(String.format("Average Constraint:  %.4f\n", report.getAvgConstraint()));
        sb.append(String.format("Average Efficiency:  %.4f\n", report.getAvgEfficiency()));
        sb.append(String.format("Average Brokerage:   %.1f\n", report.getAvgBrokerage()));
        sb.append(String.format("Network Closure:     %.4f  %s\n\n",
                report.getNetworkClosure(), closureLabel(report.getNetworkClosure())));

        // Top brokers
        if (!report.getTopBrokers().isEmpty()) {
            sb.append("── Top Brokers ──────────────────────────────────────────\n");
            sb.append(String.format("  %-12s %6s %8s %10s %10s %6s  %s\n",
                    "Vertex", "Degree", "EffSize", "Efficiency", "Constraint", "Score", "Role"));
            for (VertexMetrics vm : report.getTopBrokers()) {
                sb.append(String.format("  %-12s %6d %8.2f %10.4f %10.4f %6.1f  %s\n",
                        truncate(vm.getVertex(), 12), vm.getDegree(),
                        vm.getEffectiveSize(), vm.getEfficiency(),
                        vm.getConstraint(), vm.getBrokerageScore(), vm.getRole()));
            }
            sb.append("\n");
        }

        // Most constrained
        if (!report.getMostConstrained().isEmpty()) {
            sb.append("── Most Constrained ─────────────────────────────────────\n");
            sb.append(String.format("  %-12s %6s %10s %10s  %s\n",
                    "Vertex", "Degree", "Constraint", "Hierarchy", "Role"));
            for (VertexMetrics vm : report.getMostConstrained()) {
                sb.append(String.format("  %-12s %6d %10.4f %10.4f  %s\n",
                        truncate(vm.getVertex(), 12), vm.getDegree(),
                        vm.getConstraint(), vm.getHierarchy(), vm.getRole()));
            }
            sb.append("\n");
        }

        // Bridging edges
        if (!report.getBridgingEdges().isEmpty()) {
            sb.append("── Bridging Edges (strength > 0.5) ──────────────────────\n");
            int count = Math.min(report.getBridgingEdges().size(), 15);
            for (int i = 0; i < count; i++) {
                BridgingEdge be = report.getBridgingEdges().get(i);
                sb.append(String.format("  %s -- %s  (strength=%.3f, common neighbors=%d)\n",
                        be.getVertex1(), be.getVertex2(),
                        be.getBridgeStrength(), be.getCommonNeighbors()));
            }
            if (report.getBridgingEdges().size() > 15) {
                sb.append(String.format("  ... and %d more bridging edges\n",
                        report.getBridgingEdges().size() - 15));
            }
            sb.append("\n");
        }

        // Role distribution
        Map<String, Integer> roleCounts = new LinkedHashMap<>();
        for (VertexMetrics vm : report.getAllMetrics()) {
            roleCounts.merge(vm.getRole(), 1, Integer::sum);
        }
        sb.append("── Role Distribution ────────────────────────────────────\n");
        roleCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .forEach(e -> sb.append(String.format("  %-20s %d\n", e.getKey(), e.getValue())));
        sb.append("\n");

        // Interpretation
        sb.append("── Interpretation ───────────────────────────────────────\n");
        if (report.getNetworkClosure() > 0.4) {
            sb.append("  High network closure: most contacts are well-connected\n");
            sb.append("  to each other. Few structural holes — information flows\n");
            sb.append("  freely but innovation may be limited.\n");
        } else if (report.getNetworkClosure() > 0.2) {
            sb.append("  Moderate closure: mix of redundant and bridging ties.\n");
            sb.append("  Some brokerage opportunities exist.\n");
        } else {
            sb.append("  Low closure: many structural holes. Brokers have high\n");
            sb.append("  information advantages. Network is loosely connected.\n");
        }

        return sb.toString();
    }

    // ── Core computations ───────────────────────────────────────

    /**
     * Effective size: the number of alters minus redundancy.
     * Redundancy for each alter j = sum over other alters q of p(i,q) * m(j,q)
     * where p(i,q) = proportion of i's relations invested in q
     * and m(j,q) = marginal strength of j's connection to q.
     *
     * Simplified: effectiveSize = degree - (sum of pairwise redundancy)
     */
    double computeEffectiveSize(String vertex, Set<String> neighbors) {
        if (neighbors.size() <= 1) return neighbors.size();

        double redundancy = 0.0;
        double proportionEach = 1.0 / neighbors.size();

        for (String j : neighbors) {
            // For each alter j, count how many of ego's other alters
            // are also connected to j (redundancy with j)
            Set<String> jNeighbors = neighborCache.getOrDefault(j, Collections.emptySet());
            double jRedundancy = 0.0;
            for (String q : neighbors) {
                if (q.equals(j)) continue;
                if (jNeighbors.contains(q)) {
                    // q is connected to both ego and j — redundant path
                    jRedundancy += proportionEach;
                }
            }
            redundancy += jRedundancy;
        }

        return Math.max(0, neighbors.size() - redundancy);
    }

    /**
     * Pairwise constraint: c(i,j) = (p(i,j) + sum_q p(i,q)*p(q,j))^2
     * where p(i,j) is the proportion of i's network energy invested in j.
     *
     * For unweighted graphs, p(i,j) = 1/degree(i) for each neighbor j.
     */
    Map<String, Double> computePairwiseConstraint(String vertex, Set<String> neighbors) {
        Map<String, Double> pairwise = new LinkedHashMap<>();
        if (neighbors.isEmpty()) return pairwise;

        double pij = 1.0 / neighbors.size();

        for (String j : neighbors) {
            // Direct proportion
            double directAndIndirect = pij;

            // Indirect paths through mutual contacts q
            Set<String> jNeighbors = neighborCache.getOrDefault(j, Collections.emptySet());
            for (String q : neighbors) {
                if (q.equals(j)) continue;
                if (jNeighbors.contains(q)) {
                    // Path i -> q -> j exists
                    // p(i,q) * p(q,j)
                    Set<String> qNeighbors = neighborCache.getOrDefault(q, Collections.emptySet());
                    double pqj = qNeighbors.isEmpty() ? 0 : 1.0 / qNeighbors.size();
                    directAndIndirect += pij * pqj;
                }
            }

            pairwise.put(j, directAndIndirect * directAndIndirect);
        }

        return pairwise;
    }

    /**
     * Hierarchy: Coleman's concentration of constraint.
     * H = (sum_j (c_j / C)^2 * N_j) / (N * (C/N)^2)
     * Simplified: coefficient of variation of pairwise constraints.
     */
    double computeHierarchy(double totalConstraint, Map<String, Double> pairwise) {
        if (pairwise.size() <= 1 || totalConstraint <= 0) return 0.0;

        double mean = totalConstraint / pairwise.size();
        double variance = 0.0;
        for (double c : pairwise.values()) {
            variance += (c - mean) * (c - mean);
        }
        variance /= pairwise.size();
        double stdDev = Math.sqrt(variance);

        // Coefficient of variation as hierarchy measure
        return mean > 0 ? stdDev / mean : 0.0;
    }

    /**
     * Bridge score: fraction of ego's edges where removing the edge
     * would increase the number of connected components in ego's
     * neighborhood subgraph.
     */
    double computeBridgeScore(String vertex, Set<String> neighbors) {
        if (neighbors.size() <= 1) return neighbors.isEmpty() ? 0.0 : 1.0;

        // Count neighbor pairs that are NOT connected
        // (edges to them are bridging)
        int bridgingCount = 0;
        List<String> nbrList = new ArrayList<>(neighbors);

        for (String nbr : nbrList) {
            // Is this neighbor connected to any OTHER neighbor of ego?
            Set<String> nbrNeighbors = neighborCache.getOrDefault(nbr, Collections.emptySet());
            boolean hasOtherEgoNeighbor = false;
            for (String other : nbrList) {
                if (!other.equals(nbr) && nbrNeighbors.contains(other)) {
                    hasOtherEgoNeighbor = true;
                    break;
                }
            }
            if (!hasOtherEgoNeighbor) {
                bridgingCount++;
            }
        }

        return (double) bridgingCount / neighbors.size();
    }

    /**
     * Composite brokerage score (0–100).
     * Combines efficiency (40%), inverse constraint (40%), and bridge score (20%).
     */
    double computeBrokerageScore(double efficiency, double constraint, double bridgeScore) {
        double effComponent = efficiency * 40.0;
        double constraintComponent = (1.0 - Math.min(1.0, constraint)) * 40.0;
        double bridgeComponent = bridgeScore * 20.0;
        return Math.min(100.0, Math.max(0.0, effComponent + constraintComponent + bridgeComponent));
    }

    /**
     * Compare two vertices and explain who has better brokerage position.
     *
     * @param v1 first vertex
     * @param v2 second vertex
     * @return comparison text
     */
    public String compareVertices(String v1, String v2) {
        validateVertex(v1);
        validateVertex(v2);

        VertexMetrics m1 = analyze(v1);
        VertexMetrics m2 = analyze(v2);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Comparison: %s vs %s\n\n", v1, v2));
        sb.append(String.format("  %-20s %12s %12s\n", "Metric", v1, v2));
        sb.append(String.format("  %-20s %12d %12d\n", "Degree", m1.getDegree(), m2.getDegree()));
        sb.append(String.format("  %-20s %12.2f %12.2f\n", "Effective Size", m1.getEffectiveSize(), m2.getEffectiveSize()));
        sb.append(String.format("  %-20s %12.4f %12.4f\n", "Efficiency", m1.getEfficiency(), m2.getEfficiency()));
        sb.append(String.format("  %-20s %12.4f %12.4f\n", "Constraint", m1.getConstraint(), m2.getConstraint()));
        sb.append(String.format("  %-20s %12.4f %12.4f\n", "Hierarchy", m1.getHierarchy(), m2.getHierarchy()));
        sb.append(String.format("  %-20s %12.1f %12.1f\n", "Brokerage Score", m1.getBrokerageScore(), m2.getBrokerageScore()));
        sb.append(String.format("  %-20s %12.4f %12.4f\n", "Bridge Score", m1.getBridgeScore(), m2.getBridgeScore()));
        sb.append(String.format("  %-20s %12s %12s\n", "Role", m1.getRole(), m2.getRole()));

        sb.append("\n  ");
        if (m1.getBrokerageScore() > m2.getBrokerageScore()) {
            sb.append(String.format("%s has stronger brokerage position (%.1f vs %.1f).",
                    v1, m1.getBrokerageScore(), m2.getBrokerageScore()));
        } else if (m2.getBrokerageScore() > m1.getBrokerageScore()) {
            sb.append(String.format("%s has stronger brokerage position (%.1f vs %.1f).",
                    v2, m2.getBrokerageScore(), m1.getBrokerageScore()));
        } else {
            sb.append("Both vertices have equal brokerage positions.");
        }

        return sb.toString();
    }

    /**
     * Identify structural holes in the network — pairs of communities
     * with few connections between them.
     *
     * @return list of vertex pairs representing the strongest holes
     */
    public List<BridgingEdge> identifyHoles() {
        // A structural hole exists between two clusters with minimal overlap.
        // We identify these as edges with maximum bridge strength.
        List<BridgingEdge> allBridges = findBridgingEdges();
        // Only return the strongest holes (strength >= 0.75)
        return allBridges.stream()
                .filter(be -> be.getBridgeStrength() >= 0.75)
                .collect(Collectors.toList());
    }

    // ── Utilities ───────────────────────────────────────────────

    private void validateVertex(String vertex) {
        if (vertex == null) {
            throw new IllegalArgumentException("Vertex must not be null");
        }
        if (!graph.containsVertex(vertex)) {
            throw new IllegalArgumentException("Vertex not in graph: " + vertex);
        }
    }

    private String closureLabel(double closure) {
        if (closure > 0.4) return "(High — dense, redundant)";
        if (closure > 0.2) return "(Moderate — mixed)";
        return "(Low — many structural holes)";
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 2) + "..";
    }
}
