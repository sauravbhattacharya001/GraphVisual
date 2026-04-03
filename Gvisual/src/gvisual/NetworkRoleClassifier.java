package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Network Role Classifier — assigns structural role archetypes to each vertex
 * based on combined centrality and local topology metrics.
 *
 * <p>Inspired by the Role Equivalence / Positional Analysis literature
 * (Lorrain &amp; White 1971, Burt 1976), this analyzer classifies each node
 * into one of six structural archetypes:</p>
 *
 * <ul>
 *   <li><b>Hub</b> — high degree + high closeness; well-connected core nodes</li>
 *   <li><b>Bridge</b> — high betweenness but moderate/low degree; spans communities</li>
 *   <li><b>Local Hub</b> — high degree + high clustering; leaders of tight clusters</li>
 *   <li><b>Connector</b> — moderate degree + moderate betweenness; links sub-groups</li>
 *   <li><b>Peripheral</b> — low degree + low centrality; Edge of the network</li>
 *   <li><b>Isolate</b> — degree 0; disconnected from the network</li>
 * </ul>
 *
 * <p>The classifier computes four metrics per node (degree centrality,
 * betweenness centrality, closeness centrality, clustering coefficient),
 * normalises them to [0,1], then applies threshold-based rules to assign
 * roles. Thresholds are adaptive — computed from the network's own
 * distribution (percentile-based) rather than hard-coded.</p>
 *
 * <h3>Usage:</h3>
 * <pre>
 * NetworkRoleClassifier classifier = new NetworkRoleClassifier(graph);
 * classifier.classify();
 *
 * // Single node
 * NodeRole role = classifier.getRole("Alice");
 *
 * // All roles
 * Map&lt;String, NodeRole&gt; roles = classifier.getAllRoles();
 *
 * // Nodes in a specific role
 * List&lt;String&gt; hubs = classifier.getNodesByRole(StructuralRole.HUB);
 *
 * // Distribution summary
 * RoleDistribution dist = classifier.getRoleDistribution();
 *
 * // Full report
 * String report = classifier.generateReport();
 * </pre>
 *
 * @author zalenix
 */
public class NetworkRoleClassifier {

    /** Structural role archetypes. */
    public enum StructuralRole {
        HUB("Hub", "High degree + high closeness; well-connected core node"),
        BRIDGE("Bridge", "High betweenness but moderate degree; spans communities"),
        LOCAL_HUB("Local Hub", "High degree + high clustering; cluster leader"),
        CONNECTOR("Connector", "Moderate degree + moderate betweenness; links sub-groups"),
        PERIPHERAL("Peripheral", "Low degree + low centrality; network Edge"),
        ISOLATE("Isolate", "Degree 0; disconnected from the network");

        private final String label;
        private final String description;

        StructuralRole(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String getLabel() { return label; }
        public String getDescription() { return description; }
    }

    /** Holds all computed metrics and the assigned role for a single node. */
    public static class NodeRole {
        private final String nodeId;
        private final int degree;
        private final double degreeCentrality;
        private final double betweennessCentrality;
        private final double closenessCentrality;
        private final double clusteringCoefficient;
        private final StructuralRole role;

        public NodeRole(String nodeId, int degree,
                        double degreeCentrality, double betweennessCentrality,
                        double closenessCentrality, double clusteringCoefficient,
                        StructuralRole role) {
            this.nodeId = nodeId;
            this.degree = degree;
            this.degreeCentrality = degreeCentrality;
            this.betweennessCentrality = betweennessCentrality;
            this.closenessCentrality = closenessCentrality;
            this.clusteringCoefficient = clusteringCoefficient;
            this.role = role;
        }

        public String getNodeId() { return nodeId; }
        public int getDegree() { return degree; }
        public double getDegreeCentrality() { return degreeCentrality; }
        public double getBetweennessCentrality() { return betweennessCentrality; }
        public double getClosenessCentrality() { return closenessCentrality; }
        public double getClusteringCoefficient() { return clusteringCoefficient; }
        public StructuralRole getRole() { return role; }

        @Override
        public String toString() {
            return String.format("%s: %s (deg=%d, dc=%.3f, bc=%.3f, cc=%.3f, clust=%.3f)",
                    nodeId, role.getLabel(), degree,
                    degreeCentrality, betweennessCentrality,
                    closenessCentrality, clusteringCoefficient);
        }
    }

    /** Aggregate role distribution across the network. */
    public static class RoleDistribution {
        private final Map<StructuralRole, Integer> counts;
        private final Map<StructuralRole, Double> percentages;
        private final int totalNodes;

        public RoleDistribution(Map<StructuralRole, Integer> counts, int totalNodes) {
            this.counts = Collections.unmodifiableMap(new LinkedHashMap<>(counts));
            this.totalNodes = totalNodes;
            Map<StructuralRole, Double> pcts = new LinkedHashMap<>();
            for (Map.Entry<StructuralRole, Integer> entry : counts.entrySet()) {
                pcts.put(entry.getKey(),
                        totalNodes > 0 ? 100.0 * entry.getValue() / totalNodes : 0.0);
            }
            this.percentages = Collections.unmodifiableMap(pcts);
        }

        public int getCount(StructuralRole role) {
            return counts.getOrDefault(role, 0);
        }

        public double getPercentage(StructuralRole role) {
            return percentages.getOrDefault(role, 0.0);
        }

        public Map<StructuralRole, Integer> getCounts() { return counts; }
        public Map<StructuralRole, Double> getPercentages() { return percentages; }
        public int getTotalNodes() { return totalNodes; }
    }

    // ── Instance fields ─────────────────────────────────────────

    private final Graph<String, Edge> graph;
    private final Map<String, NodeRole> roles;
    private boolean classified;

    // Adaptive thresholds (computed from percentiles)
    private double highDegreeThreshold;
    private double highBetweennessThreshold;
    private double highClosenessThreshold;
    private double highClusteringThreshold;

    /**
     * Creates a new NetworkRoleClassifier for the given graph.
     *
     * @param graph the JUNG graph to analyze (must not be null)
     * @throws IllegalArgumentException if graph is null
     */
    public NetworkRoleClassifier(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.roles = new LinkedHashMap<>();
        this.classified = false;
    }

    // ── Public API ──────────────────────────────────────────────

    /**
     * Runs the classification pipeline: compute metrics, determine adaptive
     * thresholds, assign roles to every vertex.
     */
    public void classify() {
        roles.clear();
        Collection<String> vertices = graph.getVertices();
        int n = vertices.size();
        if (n == 0) {
            classified = true;
            return;
        }

        // 1. Compute raw metrics
        Map<String, Integer> degrees = new LinkedHashMap<>();
        Map<String, Double> degreeCent = new LinkedHashMap<>();
        Map<String, Double> betweenCent = new LinkedHashMap<>();
        Map<String, Double> closeCent = new LinkedHashMap<>();
        Map<String, Double> clustering = new LinkedHashMap<>();

        Map<String, Set<String>> adj = GraphUtils.buildAdjacencyMap(graph);

        for (String v : vertices) {
            int deg = graph.degree(v);
            degrees.put(v, deg);
            degreeCent.put(v, n > 1 ? (double) deg / (n - 1) : 0.0);
        }

        // Betweenness centrality (Brandes)
        computeBetweenness(vertices, adj, betweenCent);

        // Closeness centrality
        computeCloseness(vertices, closeCent, n);

        // Clustering coefficient
        computeClustering(vertices, adj, clustering);

        // 2. Compute adaptive thresholds (75th percentile)
        highDegreeThreshold = percentile(degreeCent.values(), 75);
        highBetweennessThreshold = percentile(betweenCent.values(), 75);
        highClosenessThreshold = percentile(closeCent.values(), 75);
        highClusteringThreshold = percentile(clustering.values(), 75);

        // 3. Assign roles
        for (String v : vertices) {
            int deg = degrees.get(v);
            double dc = degreeCent.get(v);
            double bc = betweenCent.get(v);
            double cc = closeCent.get(v);
            double cl = clustering.get(v);

            StructuralRole role = assignRole(deg, dc, bc, cc, cl);

            roles.put(v, new NodeRole(v, deg, dc, bc, cc, cl, role));
        }

        classified = true;
    }

    /**
     * Gets the classified role for a specific node.
     *
     * @param nodeId the vertex ID
     * @return the NodeRole, or null if not found
     * @throws IllegalStateException if classify() has not been called
     */
    public NodeRole getRole(String nodeId) {
        ensureClassified();
        return roles.get(nodeId);
    }

    /**
     * Gets all classified roles.
     *
     * @return unmodifiable map of vertex ID to NodeRole
     * @throws IllegalStateException if classify() has not been called
     */
    public Map<String, NodeRole> getAllRoles() {
        ensureClassified();
        return Collections.unmodifiableMap(roles);
    }

    /**
     * Gets all nodes assigned a specific structural role.
     *
     * @param role the role to filter by
     * @return list of vertex IDs with that role, sorted alphabetically
     * @throws IllegalStateException if classify() has not been called
     */
    public List<String> getNodesByRole(StructuralRole role) {
        ensureClassified();
        return roles.entrySet().stream()
                .filter(e -> e.getValue().getRole() == role)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Gets the aggregate role distribution.
     *
     * @return RoleDistribution with counts and percentages
     * @throws IllegalStateException if classify() has not been called
     */
    public RoleDistribution getRoleDistribution() {
        ensureClassified();
        Map<StructuralRole, Integer> counts = new LinkedHashMap<>();
        for (StructuralRole r : StructuralRole.values()) {
            counts.put(r, 0);
        }
        for (NodeRole nr : roles.values()) {
            counts.merge(nr.getRole(), 1, Integer::sum);
        }
        return new RoleDistribution(counts, roles.size());
    }

    /**
     * Gets the top N nodes ranked by a combined importance score
     * (weighted sum of normalised metrics).
     *
     * @param n number of nodes to return
     * @return list of NodeRole objects, highest importance first
     * @throws IllegalStateException    if classify() has not been called
     * @throws IllegalArgumentException if n is less than 1
     */
    public List<NodeRole> topByImportance(int n) {
        ensureClassified();
        if (n < 1) throw new IllegalArgumentException("n must be >= 1");
        return roles.values().stream()
                .sorted((a, b) -> {
                    double sa = importanceScore(a);
                    double sb = importanceScore(b);
                    return Double.compare(sb, sa); // descending
                })
                .limit(n)
                .collect(Collectors.toList());
    }

    /**
     * Generates a human-readable classification report.
     *
     * @return formatted text report
     * @throws IllegalStateException if classify() has not been called
     */
    public String generateReport() {
        ensureClassified();
        StringBuilder sb = new StringBuilder();
        sb.append("=== Network Role Classification Report ===\n\n");

        // Distribution summary
        RoleDistribution dist = getRoleDistribution();
        sb.append("Role Distribution:\n");
        for (StructuralRole role : StructuralRole.values()) {
            int count = dist.getCount(role);
            double pct = dist.getPercentage(role);
            sb.append(String.format("  %-12s %3d nodes (%5.1f%%)\n",
                    role.getLabel(), count, pct));
        }
        sb.append(String.format("  %-12s %3d nodes\n", "TOTAL", dist.getTotalNodes()));

        // Adaptive thresholds
        sb.append("\nAdaptive Thresholds (75th percentile):\n");
        sb.append(String.format("  Degree centrality:      %.4f\n", highDegreeThreshold));
        sb.append(String.format("  Betweenness centrality: %.4f\n", highBetweennessThreshold));
        sb.append(String.format("  Closeness centrality:   %.4f\n", highClosenessThreshold));
        sb.append(String.format("  Clustering coefficient: %.4f\n", highClusteringThreshold));

        // Per-node table
        sb.append("\nNode Classifications:\n");
        sb.append(String.format("  %-10s %-12s %4s %7s %7s %7s %7s\n",
                "Node", "Role", "Deg", "DC", "BC", "CC", "Clust"));
        sb.append("  " + repeat("-", 62) + "\n");

        List<NodeRole> sorted = roles.values().stream()
                .sorted((a, b) -> {
                    int cmp = a.getRole().compareTo(b.getRole());
                    if (cmp != 0) return cmp;
                    return Double.compare(importanceScore(b), importanceScore(a));
                })
                .collect(Collectors.toList());

        for (NodeRole nr : sorted) {
            sb.append(String.format("  %-10s %-12s %4d %7.4f %7.4f %7.4f %7.4f\n",
                    truncate(nr.getNodeId(), 10), nr.getRole().getLabel(),
                    nr.getDegree(), nr.getDegreeCentrality(),
                    nr.getBetweennessCentrality(), nr.getClosenessCentrality(),
                    nr.getClusteringCoefficient()));
        }

        return sb.toString();
    }

    // ── Role assignment logic ───────────────────────────────────

    /**
     * Assigns a structural role based on metric values and adaptive thresholds.
     * Rules are applied in priority order (most specific first).
     */
    StructuralRole assignRole(int degree, double dc, double bc,
                                        double cc, double clustering) {
        if (degree == 0) {
            return StructuralRole.ISOLATE;
        }

        boolean highDeg = dc >= highDegreeThreshold;
        boolean highBtw = bc >= highBetweennessThreshold;
        boolean highClose = cc >= highClosenessThreshold;
        boolean highClust = clustering >= highClusteringThreshold;

        // Hub: high degree AND high closeness (core of the network)
        if (highDeg && highClose) {
            return StructuralRole.HUB;
        }

        // Bridge: high betweenness but NOT high degree (spans communities)
        if (highBtw && !highDeg) {
            return StructuralRole.BRIDGE;
        }

        // Local Hub: high degree AND high clustering (cluster leader)
        if (highDeg && highClust) {
            return StructuralRole.LOCAL_HUB;
        }

        // Connector: moderate metrics — either high degree or betweenness but
        // doesn't meet the stricter Hub/Bridge thresholds
        if (highDeg || highBtw) {
            return StructuralRole.CONNECTOR;
        }

        // Everything else is peripheral
        return StructuralRole.PERIPHERAL;
    }

    // ── Metric computation ──────────────────────────────────────

    /**
     * Brandes' algorithm for betweenness centrality.
     */
    private void computeBetweenness(Collection<String> vertices,
                                     Map<String, Set<String>> adj,
                                     Map<String, Double> result) {
        for (String v : vertices) {
            result.put(v, 0.0);
        }

        int n = vertices.size();
        if (n < 3) return; // betweenness meaningless for < 3 nodes

        for (String s : vertices) {
            // BFS phase
            Stack<String> stack = new Stack<>();
            Map<String, List<String>> predecessors = new HashMap<>();
            Map<String, Integer> sigma = new HashMap<>();  // # shortest paths
            Map<String, Integer> dist = new HashMap<>();

            for (String v : vertices) {
                predecessors.put(v, new ArrayList<>());
                sigma.put(v, 0);
                dist.put(v, -1);
            }
            sigma.put(s, 1);
            dist.put(s, 0);

            Queue<String> queue = new ArrayDeque<>();
            queue.add(s);

            while (!queue.isEmpty()) {
                String v = queue.poll();
                stack.push(v);
                int dv = dist.get(v);
                Set<String> neighbors = adj.getOrDefault(v, Collections.emptySet());
                for (String w : neighbors) {
                    // First visit?
                    if (dist.get(w) < 0) {
                        dist.put(w, dv + 1);
                        queue.add(w);
                    }
                    // Shortest path via v?
                    if (dist.get(w) == dv + 1) {
                        sigma.put(w, sigma.get(w) + sigma.get(v));
                        predecessors.get(w).add(v);
                    }
                }
            }

            // Accumulation phase
            Map<String, Double> delta = new HashMap<>();
            for (String v : vertices) delta.put(v, 0.0);

            while (!stack.isEmpty()) {
                String w = stack.pop();
                for (String v : predecessors.get(w)) {
                    double frac = (double) sigma.get(v) / sigma.get(w);
                    delta.put(v, delta.get(v) + frac * (1.0 + delta.get(w)));
                }
                if (!w.equals(s)) {
                    result.put(w, result.get(w) + delta.get(w));
                }
            }
        }

        // Normalise: undirected graph -> divide by (n-1)(n-2)
        double norm = (n - 1.0) * (n - 2.0);
        if (norm > 0) {
            for (String v : vertices) {
                result.put(v, result.get(v) / norm);
            }
        }
    }

    /**
     * Closeness centrality via BFS from each vertex.
     */
    private void computeCloseness(Collection<String> vertices,
                                   Map<String, Double> result, int n) {
        for (String source : vertices) {
            Map<String, Integer> distances = GraphUtils.bfsDistances(graph, source);
            double totalDist = 0;
            int reachable = 0;
            for (Map.Entry<String, Integer> entry : distances.entrySet()) {
                if (!entry.getKey().equals(source) && entry.getValue() > 0) {
                    totalDist += entry.getValue();
                    reachable++;
                }
            }
            if (reachable > 0 && totalDist > 0) {
                // Wasserman-Faust normalisation for disconnected graphs
                double avgDist = totalDist / reachable;
                double normalisation = (double) reachable / (n - 1);
                result.put(source, normalisation / avgDist);
            } else {
                result.put(source, 0.0);
            }
        }
    }

    /**
     * Local clustering coefficient: fraction of a node's neighbours
     * that are connected to each other.
     */
    private void computeClustering(Collection<String> vertices,
                                    Map<String, Set<String>> adj,
                                    Map<String, Double> result) {
        for (String v : vertices) {
            Set<String> neighbors = adj.getOrDefault(v, Collections.emptySet());
            int k = neighbors.size();
            if (k < 2) {
                result.put(v, 0.0);
                continue;
            }
            int triangles = 0;
            List<String> nbrList = new ArrayList<>(neighbors);
            for (int i = 0; i < nbrList.size(); i++) {
                for (int j = i + 1; j < nbrList.size(); j++) {
                    Set<String> nbrsOfI = adj.getOrDefault(nbrList.get(i), Collections.emptySet());
                    if (nbrsOfI.contains(nbrList.get(j))) {
                        triangles++;
                    }
                }
            }
            double maxTriangles = k * (k - 1.0) / 2.0;
            result.put(v, triangles / maxTriangles);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private void ensureClassified() {
        if (!classified) {
            throw new IllegalStateException(
                    "classify() must be called before querying results");
        }
    }

    /**
     * Computes an importance score as a weighted sum of normalised metrics.
     */
    private double importanceScore(NodeRole nr) {
        return 0.3 * nr.getDegreeCentrality()
             + 0.3 * nr.getBetweennessCentrality()
             + 0.2 * nr.getClosenessCentrality()
             + 0.2 * nr.getClusteringCoefficient();
    }

    /**
     * Computes the p-th percentile of a collection of values.
     * Uses linear interpolation method.
     */
    static double percentile(Collection<Double> values, double p) {
        if (values.isEmpty()) return 0.0;
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        double rank = p / 100.0 * (sorted.size() - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        if (lower == upper) return sorted.get(lower);
        double frac = rank - lower;
        return sorted.get(lower) * (1 - frac) + sorted.get(upper) * frac;
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 1) + "\u2026";
    }

    private static String repeat(String s, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(s);
        return sb.toString();
    }
}
