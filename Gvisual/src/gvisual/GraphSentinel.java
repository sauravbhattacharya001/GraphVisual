package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GraphSentinel -- autonomous structural drift detector that compares two
 * graph snapshots and performs deep structural analysis to surface meaningful
 * changes: community migrations, hub dynamics, centrality shifts, role
 * transitions, stability scoring, and proactive early-warning alerts.
 *
 * <p>Goes far beyond simple set-level diffs (added/removed nodes). Detects
 * the <em>meaning</em> behind structural changes, answers "what happened
 * to the network?" and recommends actions.</p>
 *
 * <p>Usage:
 * <pre>
 *   GraphSentinel sentinel = new GraphSentinel(beforeGraph, afterGraph);
 *   GraphSentinel.DriftReport report = sentinel.analyze();
 *   System.out.println(sentinel.toText(report));
 *   // or HTML:
 *   String html = sentinel.exportHtml(report);
 * </pre>
 *
 * <h3>Six analysis engines:</h3>
 * <ol>
 *   <li><b>Community Migration Detector</b> -- splits, merges, births, deaths,
 *       individual node migrations</li>
 *   <li><b>Hub Dynamics Analyzer</b> -- emergence, decline, strengthening,
 *       weakening of high-degree nodes</li>
 *   <li><b>Centrality Shift Detector</b> -- rising influencers, declining
 *       gatekeepers, new/lost bridge nodes</li>
 *   <li><b>Structural Role Transition Detector</b> -- Hub/Bridge/Core/
 *       Peripheral/Isolate transitions per node</li>
 *   <li><b>Stability Scorer</b> -- composite 0-100 score across 5 dimensions</li>
 *   <li><b>Early Warning System</b> -- prioritized CRITICAL/WARNING/INFO
 *       alerts with recommendations</li>
 * </ol>
 *
 * @author zalenix
 */
public class GraphSentinel {

    private final Graph<String, Edge> before;
    private final Graph<String, Edge> after;

    /**
     * Create a sentinel comparing two graph snapshots.
     *
     * @param before the baseline graph snapshot
     * @param after  the later graph snapshot
     * @throws IllegalArgumentException if either graph is null
     */
    public GraphSentinel(Graph<String, Edge> before, Graph<String, Edge> after) {
        if (before == null) throw new IllegalArgumentException("before graph must not be null");
        if (after == null) throw new IllegalArgumentException("after graph must not be null");
        this.before = before;
        this.after = after;
    }

    // -- Data classes ------------------------------------------------------

    /** Complete drift analysis report. */
    public static class DriftReport {
        public int stabilityScore;
        public String stabilityGrade;
        public List<CommunityEvent> communityEvents = new ArrayList<>();
        public List<HubEvent> hubEvents = new ArrayList<>();
        public List<CentralityShift> centralityShifts = new ArrayList<>();
        public List<RoleTransition> roleTransitions = new ArrayList<>();
        public List<Alert> alerts = new ArrayList<>();
        public int nodesBefore, nodesAfter, edgesBefore, edgesAfter;
        public long timestamp;
    }

    /** A detected community-level change. */
    public static class CommunityEvent {
        public String type;
        public List<String> affectedNodes;
        public String description;

        public CommunityEvent(String type, List<String> affectedNodes, String description) {
            this.type = type;
            this.affectedNodes = affectedNodes;
            this.description = description;
        }
    }

    /** A detected hub-level change. */
    public static class HubEvent {
        public String node;
        public String type;
        public int oldDegree;
        public int newDegree;

        public HubEvent(String node, String type, int oldDegree, int newDegree) {
            this.node = node;
            this.type = type;
            this.oldDegree = oldDegree;
            this.newDegree = newDegree;
        }
    }

    /** A detected betweenness-centrality shift. */
    public static class CentralityShift {
        public String node;
        public double oldCentrality;
        public double newCentrality;
        public String classification;

        public CentralityShift(String node, double oldCentrality, double newCentrality, String classification) {
            this.node = node;
            this.oldCentrality = oldCentrality;
            this.newCentrality = newCentrality;
            this.classification = classification;
        }
    }

    /** A structural role transition for a single node. */
    public static class RoleTransition {
        public String node;
        public String oldRole;
        public String newRole;

        public RoleTransition(String node, String oldRole, String newRole) {
            this.node = node;
            this.oldRole = oldRole;
            this.newRole = newRole;
        }
    }

    /** A prioritized alert with recommendation. */
    public static class Alert {
        public String severity; // CRITICAL, WARNING, INFO
        public String category;
        public String description;
        public List<String> affectedNodes;
        public String recommendation;

        public Alert(String severity, String category, String description,
                     List<String> affectedNodes, String recommendation) {
            this.severity = severity;
            this.category = category;
            this.description = description;
            this.affectedNodes = affectedNodes;
            this.recommendation = recommendation;
        }
    }

    // -- Main analysis -----------------------------------------------------

    /**
     * Run all six analysis engines and return a comprehensive drift report.
     *
     * @return the drift analysis report
     */
    public DriftReport analyze() {
        DriftReport report = new DriftReport();
        report.timestamp = System.currentTimeMillis();
        report.nodesBefore = before.getVertexCount();
        report.nodesAfter = after.getVertexCount();
        report.edgesBefore = before.getEdgeCount();
        report.edgesAfter = after.getEdgeCount();

        Map<String, Set<String>> adjBefore = buildAdjacencyMap(before);
        Map<String, Set<String>> adjAfter = buildAdjacencyMap(after);

        // 1. Community migration
        Map<String, Integer> commBefore = detectCommunities(before);
        Map<String, Integer> commAfter = detectCommunities(after);
        report.communityEvents = analyzeCommunityMigration(commBefore, commAfter);

        // 2. Hub dynamics
        report.hubEvents = analyzeHubDynamics(before, after);

        // 3. Centrality shifts
        Map<String, Double> cenBefore = computeBetweenness(before, adjBefore);
        Map<String, Double> cenAfter = computeBetweenness(after, adjAfter);
        report.centralityShifts = analyzeCentralityShifts(cenBefore, cenAfter);

        // 4. Role transitions
        report.roleTransitions = analyzeRoleTransitions(before, after, adjBefore, adjAfter,
                cenBefore, cenAfter, commBefore, commAfter);

        // 5. Stability score
        report.stabilityScore = computeStabilityScore(before, after, commBefore, commAfter);
        report.stabilityGrade = gradeStability(report.stabilityScore);

        // 6. Early warnings
        report.alerts = generateAlerts(report);
        report.alerts.sort((a, b) -> severityRank(a.severity) - severityRank(b.severity));

        return report;
    }

    // -- Community detection (BFS connected components) --------------------

    private Map<String, Integer> detectCommunities(Graph<String, Edge> g) {
        Map<String, Integer> membership = new HashMap<>();
        Set<String> visited = new HashSet<>();
        int communityId = 0;
        for (String v : g.getVertices()) {
            if (!visited.contains(v)) {
                Deque<String> queue = new ArrayDeque<>();
                queue.add(v);
                visited.add(v);
                while (!queue.isEmpty()) {
                    String cur = queue.poll();
                    membership.put(cur, communityId);
                    for (String nb : g.getNeighbors(cur)) {
                        if (visited.add(nb)) {
                            queue.add(nb);
                        }
                    }
                }
                communityId++;
            }
        }
        return membership;
    }

    private List<CommunityEvent> analyzeCommunityMigration(
            Map<String, Integer> commBefore, Map<String, Integer> commAfter) {
        List<CommunityEvent> events = new ArrayList<>();

        // Group nodes by community
        Map<Integer, Set<String>> beforeGroups = groupByCommunity(commBefore);
        Map<Integer, Set<String>> afterGroups = groupByCommunity(commAfter);

        // Match before->after communities by best Jaccard
        Map<Integer, Integer> bestMatch = new HashMap<>();
        Map<Integer, Double> bestScore = new HashMap<>();
        for (Map.Entry<Integer, Set<String>> be : beforeGroups.entrySet()) {
            double best = 0;
            int bestId = -1;
            for (Map.Entry<Integer, Set<String>> ae : afterGroups.entrySet()) {
                double j = jaccard(be.getValue(), ae.getValue());
                if (j > best) {
                    best = j;
                    bestId = ae.getKey();
                }
            }
            if (best > 0) {
                bestMatch.put(be.getKey(), bestId);
                bestScore.put(be.getKey(), best);
            }
        }

        // Detect splits: multiple before-communities map to different after-communities
        // but some before-community members ended up in different after-communities
        for (Map.Entry<Integer, Set<String>> be : beforeGroups.entrySet()) {
            Set<Integer> afterComms = new HashSet<>();
            for (String node : be.getValue()) {
                if (commAfter.containsKey(node)) {
                    afterComms.add(commAfter.get(node));
                }
            }
            if (afterComms.size() > 1) {
                events.add(new CommunityEvent("SPLIT",
                        new ArrayList<>(be.getValue()),
                        "Community " + be.getKey() + " split into " + afterComms.size() + " groups"));
            }
        }

        // Detect merges: multiple before-communities map to same after-community
        Map<Integer, List<Integer>> reverseMatch = new HashMap<>();
        for (Map.Entry<Integer, Integer> m : bestMatch.entrySet()) {
            reverseMatch.computeIfAbsent(m.getValue(), k -> new ArrayList<>()).add(m.getKey());
        }
        for (Map.Entry<Integer, List<Integer>> rm : reverseMatch.entrySet()) {
            if (rm.getValue().size() > 1) {
                Set<String> affected = new HashSet<>();
                for (int bid : rm.getValue()) {
                    affected.addAll(beforeGroups.getOrDefault(bid, Collections.emptySet()));
                }
                events.add(new CommunityEvent("MERGE",
                        new ArrayList<>(affected),
                        rm.getValue().size() + " communities merged into community " + rm.getKey()));
            }
        }

        // Detect births (after-communities with no good match to before)
        Set<Integer> matchedAfter = new HashSet<>(bestMatch.values());
        for (Map.Entry<Integer, Set<String>> ae : afterGroups.entrySet()) {
            if (!matchedAfter.contains(ae.getKey())) {
                events.add(new CommunityEvent("BIRTH",
                        new ArrayList<>(ae.getValue()),
                        "New community emerged with " + ae.getValue().size() + " members"));
            }
        }

        // Detect deaths (before-communities with no members surviving)
        for (Map.Entry<Integer, Set<String>> be : beforeGroups.entrySet()) {
            boolean anyRemain = be.getValue().stream().anyMatch(commAfter::containsKey);
            if (!anyRemain) {
                events.add(new CommunityEvent("DEATH",
                        new ArrayList<>(be.getValue()),
                        "Community " + be.getKey() + " dissolved (" + be.getValue().size() + " members lost)"));
            }
        }

        // Detect individual migrations
        for (Map.Entry<String, Integer> e : commBefore.entrySet()) {
            String node = e.getKey();
            if (commAfter.containsKey(node)) {
                int beforeComm = e.getValue();
                int afterComm = commAfter.get(node);
                // Check if this node's after-community is the best match for its before-community
                Integer expectedAfter = bestMatch.get(beforeComm);
                if (expectedAfter != null && expectedAfter != afterComm) {
                    events.add(new CommunityEvent("MIGRATION",
                            Collections.singletonList(node),
                            "Node " + node + " migrated from community " + beforeComm + " to " + afterComm));
                }
            }
        }

        return events;
    }

    // -- Hub dynamics ------------------------------------------------------

    private List<HubEvent> analyzeHubDynamics(Graph<String, Edge> g1, Graph<String, Edge> g2) {
        List<HubEvent> events = new ArrayList<>();
        Map<String, Integer> deg1 = computeDegrees(g1);
        Map<String, Integer> deg2 = computeDegrees(g2);

        Set<String> hubs1 = identifyHubs(deg1);
        Set<String> hubs2 = identifyHubs(deg2);

        // New hubs emerged
        for (String h : hubs2) {
            if (!hubs1.contains(h)) {
                events.add(new HubEvent(h, "EMERGED",
                        deg1.getOrDefault(h, 0), deg2.getOrDefault(h, 0)));
            }
        }

        // Hubs declined (left top tier)
        for (String h : hubs1) {
            if (!hubs2.contains(h)) {
                events.add(new HubEvent(h, "DECLINED",
                        deg1.getOrDefault(h, 0), deg2.getOrDefault(h, 0)));
            }
        }

        // Hubs that strengthened or weakened
        for (String h : hubs1) {
            if (hubs2.contains(h)) {
                int d1 = deg1.getOrDefault(h, 0);
                int d2 = deg2.getOrDefault(h, 0);
                if (d1 > 0 && d2 > d1 * 1.5) {
                    events.add(new HubEvent(h, "STRENGTHENED", d1, d2));
                } else if (d1 > 0 && d2 < d1 * 0.7) {
                    events.add(new HubEvent(h, "WEAKENED", d1, d2));
                }
            }
        }

        return events;
    }

    private Set<String> identifyHubs(Map<String, Integer> degrees) {
        if (degrees.isEmpty()) return Collections.emptySet();
        List<Integer> sorted = new ArrayList<>(degrees.values());
        Collections.sort(sorted, Collections.reverseOrder());
        int threshold = sorted.get(Math.max(0, sorted.size() / 10));
        if (threshold == 0) threshold = 1;
        final int thr = threshold;
        return degrees.entrySet().stream()
                .filter(e -> e.getValue() >= thr)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    // -- Centrality shifts -------------------------------------------------

    private Map<String, Double> computeBetweenness(Graph<String, Edge> g,
                                                    Map<String, Set<String>> adj) {
        Map<String, Double> centrality = new HashMap<>();
        Collection<String> vertices = g.getVertices();
        for (String v : vertices) centrality.put(v, 0.0);

        for (String s : vertices) {
            Deque<String> stack = new ArrayDeque<>();
            Map<String, List<String>> predecessors = new HashMap<>();
            Map<String, Integer> sigma = new HashMap<>();
            Map<String, Integer> dist = new HashMap<>();
            for (String v : vertices) {
                predecessors.put(v, new ArrayList<>());
                sigma.put(v, 0);
                dist.put(v, -1);
            }
            sigma.put(s, 1);
            dist.put(s, 0);

            Deque<String> queue = new ArrayDeque<>();
            queue.add(s);
            while (!queue.isEmpty()) {
                String v = queue.poll();
                stack.push(v);
                Set<String> neighbors = adj.getOrDefault(v, Collections.emptySet());
                for (String w : neighbors) {
                    if (dist.get(w) < 0) {
                        queue.add(w);
                        dist.put(w, dist.get(v) + 1);
                    }
                    if (dist.get(w) == dist.get(v) + 1) {
                        sigma.put(w, sigma.get(w) + sigma.get(v));
                        predecessors.get(w).add(v);
                    }
                }
            }

            Map<String, Double> delta = new HashMap<>();
            for (String v : vertices) delta.put(v, 0.0);
            while (!stack.isEmpty()) {
                String w = stack.pop();
                for (String v : predecessors.get(w)) {
                    int sw = sigma.get(w);
                    double d = sw > 0 ? (double) sigma.get(v) / sw * (1.0 + delta.get(w)) : 0.0;
                    delta.put(v, delta.get(v) + d);
                }
                if (!w.equals(s)) {
                    centrality.put(w, centrality.get(w) + delta.get(w));
                }
            }
        }

        // Normalize
        int n = vertices.size();
        double norm = (n > 2) ? (n - 1.0) * (n - 2.0) : 1.0;
        for (String v : vertices) {
            centrality.put(v, centrality.get(v) / norm);
        }
        return centrality;
    }

    private List<CentralityShift> analyzeCentralityShifts(
            Map<String, Double> cenBefore, Map<String, Double> cenAfter) {
        Set<String> allNodes = new HashSet<>();
        allNodes.addAll(cenBefore.keySet());
        allNodes.addAll(cenAfter.keySet());

        List<CentralityShift> shifts = new ArrayList<>();
        for (String node : allNodes) {
            double cb = cenBefore.getOrDefault(node, 0.0);
            double ca = cenAfter.getOrDefault(node, 0.0);
            double diff = ca - cb;
            shifts.add(new CentralityShift(node, cb, ca, classifyCentralityChange(cb, ca, diff)));
        }

        // Sort by absolute change descending, return top 10
        shifts.sort((a, b) -> Double.compare(
                Math.abs(b.newCentrality - b.oldCentrality),
                Math.abs(a.newCentrality - a.oldCentrality)));

        return shifts.size() > 10 ? shifts.subList(0, 10) : shifts;
    }

    private String classifyCentralityChange(double old, double now, double diff) {
        if (old == 0 && now > 0.01) return "NEW_BRIDGE";
        if (now == 0 && old > 0.01) return "LOST_BRIDGE";
        if (diff > 0.05) return "RISING_INFLUENCER";
        if (diff < -0.05) return "DECLINING_GATEKEEPER";
        return "STABLE";
    }

    // -- Role transitions --------------------------------------------------

    private List<RoleTransition> analyzeRoleTransitions(
            Graph<String, Edge> g1, Graph<String, Edge> g2,
            Map<String, Set<String>> adj1, Map<String, Set<String>> adj2,
            Map<String, Double> cen1, Map<String, Double> cen2,
            Map<String, Integer> comm1, Map<String, Integer> comm2) {

        Set<String> allNodes = new HashSet<>();
        allNodes.addAll(g1.getVertices());
        allNodes.addAll(g2.getVertices());

        Map<String, String> roles1 = new HashMap<>();
        Map<String, String> roles2 = new HashMap<>();

        for (String n : g1.getVertices()) {
            roles1.put(n, classifyRole(n, g1, adj1, cen1));
        }
        for (String n : g2.getVertices()) {
            roles2.put(n, classifyRole(n, g2, adj2, cen2));
        }

        List<RoleTransition> transitions = new ArrayList<>();
        for (String n : allNodes) {
            String r1 = roles1.getOrDefault(n, "ABSENT");
            String r2 = roles2.getOrDefault(n, "ABSENT");
            if (!r1.equals(r2)) {
                transitions.add(new RoleTransition(n, r1, r2));
            }
        }
        return transitions;
    }

    private String classifyRole(String node, Graph<String, Edge> g,
                                 Map<String, Set<String>> adj, Map<String, Double> centrality) {
        int degree = g.getNeighborCount(node);
        if (degree == 0) return "ISOLATE";

        double cen = centrality.getOrDefault(node, 0.0);
        double clustering = localClustering(node, adj);

        // Hubs: top degree (>= 2x avg)
        double avgDeg = g.getVertexCount() > 0 ?
                (2.0 * g.getEdgeCount()) / g.getVertexCount() : 0;
        if (degree >= avgDeg * 2 && avgDeg > 0) return "HUB";

        // Bridge: high centrality, low clustering
        if (cen > 0.1 && clustering < 0.3) return "BRIDGE";

        // Core: high clustering
        if (clustering > 0.5 && degree >= 2) return "CORE";

        // Peripheral
        if (degree <= 2) return "PERIPHERAL";

        return "MEMBER";
    }

    private double localClustering(String node, Map<String, Set<String>> adj) {
        Set<String> neighbors = adj.getOrDefault(node, Collections.emptySet());
        int k = neighbors.size();
        if (k < 2) return 0.0;

        int links = 0;
        List<String> nbList = new ArrayList<>(neighbors);
        for (int i = 0; i < nbList.size(); i++) {
            Set<String> ni = adj.getOrDefault(nbList.get(i), Collections.emptySet());
            for (int j = i + 1; j < nbList.size(); j++) {
                if (ni.contains(nbList.get(j))) links++;
            }
        }
        return (2.0 * links) / (k * (k - 1.0));
    }

    // -- Stability scorer --------------------------------------------------

    private int computeStabilityScore(Graph<String, Edge> g1, Graph<String, Edge> g2,
                                       Map<String, Integer> comm1, Map<String, Integer> comm2) {
        // Node stability (20%)
        Set<String> n1 = new HashSet<>(g1.getVertices());
        Set<String> n2 = new HashSet<>(g2.getVertices());
        double nodeStability = jaccard(n1, n2);

        // Edge stability (20%)
        Set<String> e1 = edgeKeys(g1);
        Set<String> e2 = edgeKeys(g2);
        double edgeStability = jaccard(e1, e2);

        // Community stability (25%)
        Map<Integer, Set<String>> bg = groupByCommunity(comm1);
        Map<Integer, Set<String>> ag = groupByCommunity(comm2);
        double commStability = communityMatchScore(bg, ag);

        // Hub stability (15%)
        Set<String> hubs1 = identifyHubs(computeDegrees(g1));
        Set<String> hubs2 = identifyHubs(computeDegrees(g2));
        double hubStability = hubs1.isEmpty() && hubs2.isEmpty() ? 1.0 : jaccard(hubs1, hubs2);

        // Degree distribution stability (20%)
        double degreeStability = degreeDistributionSimilarity(g1, g2);

        double score = nodeStability * 20 + edgeStability * 20 + commStability * 25
                + hubStability * 15 + degreeStability * 20;

        return Math.max(0, Math.min(100, (int) Math.round(score)));
    }

    private double communityMatchScore(Map<Integer, Set<String>> bg, Map<Integer, Set<String>> ag) {
        if (bg.isEmpty() && ag.isEmpty()) return 1.0;
        if (bg.isEmpty() || ag.isEmpty()) return 0.0;
        double total = 0;
        for (Set<String> bc : bg.values()) {
            double best = 0;
            for (Set<String> ac : ag.values()) {
                best = Math.max(best, jaccard(bc, ac));
            }
            total += best;
        }
        return total / bg.size();
    }

    private double degreeDistributionSimilarity(Graph<String, Edge> g1, Graph<String, Edge> g2) {
        Map<Integer, Double> dist1 = degreeDistribution(g1);
        Map<Integer, Double> dist2 = degreeDistribution(g2);
        if (dist1.isEmpty() && dist2.isEmpty()) return 1.0;

        Set<Integer> allDegrees = new HashSet<>();
        allDegrees.addAll(dist1.keySet());
        allDegrees.addAll(dist2.keySet());

        // Use 1 - Jensen-Shannon divergence approximation (Bhattacharyya coefficient)
        double bc = 0;
        for (int d : allDegrees) {
            double p = dist1.getOrDefault(d, 0.0);
            double q = dist2.getOrDefault(d, 0.0);
            bc += Math.sqrt(p * q);
        }
        return Math.max(0, Math.min(1, bc));
    }

    private Map<Integer, Double> degreeDistribution(Graph<String, Edge> g) {
        Map<Integer, Integer> counts = new HashMap<>();
        int total = g.getVertexCount();
        if (total == 0) return Collections.emptyMap();
        for (String v : g.getVertices()) {
            int d = g.getNeighborCount(v);
            counts.merge(d, 1, Integer::sum);
        }
        Map<Integer, Double> dist = new HashMap<>();
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            dist.put(e.getKey(), (double) e.getValue() / total);
        }
        return dist;
    }

    private String gradeStability(int score) {
        if (score >= 90) return "Stable";
        if (score >= 70) return "Minor Drift";
        if (score >= 50) return "Significant Drift";
        if (score >= 30) return "Major Restructuring";
        return "Critical Transformation";
    }

    // -- Early warning system ----------------------------------------------

    private List<Alert> generateAlerts(DriftReport report) {
        List<Alert> alerts = new ArrayList<>();

        // CRITICAL: hub lost >50% connections
        for (HubEvent h : report.hubEvents) {
            if ("DECLINED".equals(h.type) && h.oldDegree > 0 && h.newDegree < h.oldDegree * 0.5) {
                alerts.add(new Alert("CRITICAL", "HUB_LOSS",
                        "Hub " + h.node + " lost >50% connections (was " + h.oldDegree + ", now " + h.newDegree + ")",
                        Collections.singletonList(h.node),
                        "Investigate cause of hub degradation; consider reinforcing alternative paths"));
            }
        }

        // CRITICAL: community fragmentation
        for (CommunityEvent ce : report.communityEvents) {
            if ("SPLIT".equals(ce.type) && ce.description.contains("3") || "SPLIT".equals(ce.type) && ce.affectedNodes.size() > 10) {
                alerts.add(new Alert("CRITICAL", "COMMUNITY_FRAGMENTATION",
                        ce.description,
                        ce.affectedNodes,
                        "Major community fragmentation detected; review recent edge removals"));
            }
        }

        // CRITICAL: stability < 30
        if (report.stabilityScore < 30) {
            alerts.add(new Alert("CRITICAL", "LOW_STABILITY",
                    "Stability score " + report.stabilityScore + "/100 -- Critical Transformation",
                    Collections.emptyList(),
                    "Network has undergone fundamental restructuring; full topology review recommended"));
        }

        // WARNING: new isolates
        for (RoleTransition rt : report.roleTransitions) {
            if ("ISOLATE".equals(rt.newRole) && !"ISOLATE".equals(rt.oldRole) && !"ABSENT".equals(rt.oldRole)) {
                alerts.add(new Alert("WARNING", "NEW_ISOLATE",
                        "Node " + rt.node + " became isolated (was " + rt.oldRole + ")",
                        Collections.singletonList(rt.node),
                        "Node lost all connections; check if this is expected"));
            }
        }

        // WARNING: bridge node lost
        for (CentralityShift cs : report.centralityShifts) {
            if ("LOST_BRIDGE".equals(cs.classification)) {
                alerts.add(new Alert("WARNING", "BRIDGE_LOST",
                        "Node " + cs.node + " lost its bridge role",
                        Collections.singletonList(cs.node),
                        "Bridge loss may disconnect communities; verify alternate paths exist"));
            }
        }

        // WARNING: hub weakening
        for (HubEvent h : report.hubEvents) {
            if ("WEAKENED".equals(h.type)) {
                alerts.add(new Alert("WARNING", "HUB_WEAKENING",
                        "Hub " + h.node + " weakened from degree " + h.oldDegree + " to " + h.newDegree,
                        Collections.singletonList(h.node),
                        "Monitor hub for further decline; ensure redundancy in critical connections"));
            }
        }

        // WARNING: stability < 60
        if (report.stabilityScore < 60 && report.stabilityScore >= 30) {
            alerts.add(new Alert("WARNING", "MODERATE_INSTABILITY",
                    "Stability score " + report.stabilityScore + "/100 -- significant drift detected",
                    Collections.emptyList(),
                    "Network is changing substantially; review recent modifications"));
        }

        // INFO: minor community changes
        for (CommunityEvent ce : report.communityEvents) {
            if ("MIGRATION".equals(ce.type)) {
                alerts.add(new Alert("INFO", "NODE_MIGRATION",
                        ce.description,
                        ce.affectedNodes,
                        "Node changed community affiliation; may indicate evolving relationships"));
            }
        }

        // INFO: new peripheral nodes
        for (RoleTransition rt : report.roleTransitions) {
            if ("ABSENT".equals(rt.oldRole) && "PERIPHERAL".equals(rt.newRole)) {
                alerts.add(new Alert("INFO", "NEW_NODE",
                        "New peripheral node " + rt.node + " appeared",
                        Collections.singletonList(rt.node),
                        "New participant joined the network at the periphery"));
            }
        }

        // INFO: rising influencer
        for (CentralityShift cs : report.centralityShifts) {
            if ("RISING_INFLUENCER".equals(cs.classification)) {
                alerts.add(new Alert("INFO", "RISING_INFLUENCE",
                        String.format("Node %s influence rising (%.3f -> %.3f)", cs.node, cs.oldCentrality, cs.newCentrality),
                        Collections.singletonList(cs.node),
                        "Node is becoming more central in the network; potential new leader emerging"));
            }
        }

        return alerts;
    }

    // -- Utility methods ---------------------------------------------------

    private Map<String, Set<String>> buildAdjacencyMap(Graph<String, Edge> g) {
        Map<String, Set<String>> adj = new HashMap<>();
        for (String v : g.getVertices()) {
            adj.put(v, new HashSet<>(g.getNeighbors(v)));
        }
        return adj;
    }

    private Map<String, Integer> computeDegrees(Graph<String, Edge> g) {
        Map<String, Integer> deg = new HashMap<>();
        for (String v : g.getVertices()) {
            deg.put(v, g.getNeighborCount(v));
        }
        return deg;
    }

    private Map<Integer, Set<String>> groupByCommunity(Map<String, Integer> membership) {
        Map<Integer, Set<String>> groups = new HashMap<>();
        for (Map.Entry<String, Integer> e : membership.entrySet()) {
            groups.computeIfAbsent(e.getValue(), k -> new HashSet<>()).add(e.getKey());
        }
        return groups;
    }

    private Set<String> edgeKeys(Graph<String, Edge> g) {
        Set<String> keys = new HashSet<>();
        for (Edge e : g.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            if (v1 == null || v2 == null) continue;
            String key = v1.compareTo(v2) <= 0 ? v1 + "~~" + v2 : v2 + "~~" + v1;
            keys.add(key);
        }
        return keys;
    }

    private double jaccard(Set<?> a, Set<?> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        Set<Object> union = new HashSet<>(a);
        union.addAll(b);
        Set<Object> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        return union.isEmpty() ? 1.0 : (double) intersection.size() / union.size();
    }

    private int severityRank(String severity) {
        switch (severity) {
            case "CRITICAL": return 0;
            case "WARNING": return 1;
            case "INFO": return 2;
            default: return 3;
        }
    }

    // -- Text report -------------------------------------------------------

    /**
     * Render a human-readable text report.
     *
     * @param report the analysis result
     * @return multi-line text summary
     */
    public String toText(DriftReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("===========================================\n");
        sb.append("  GraphSentinel -- Structural Drift Report  \n");
        sb.append("===========================================\n\n");

        sb.append(String.format("Stability Score: %d/100 -- %s%n", report.stabilityScore, report.stabilityGrade));
        sb.append(String.format("Nodes: %d -> %d  |  Edges: %d -> %d%n%n",
                report.nodesBefore, report.nodesAfter, report.edgesBefore, report.edgesAfter));

        // Alerts
        if (!report.alerts.isEmpty()) {
            sb.append("-- Alerts --\n");
            for (Alert a : report.alerts) {
                sb.append(String.format("  [%s] %s: %s%n", a.severity, a.category, a.description));
                sb.append(String.format("    -> %s%n", a.recommendation));
            }
            sb.append("\n");
        }

        // Community events
        if (!report.communityEvents.isEmpty()) {
            sb.append("-- Community Events --\n");
            for (CommunityEvent ce : report.communityEvents) {
                sb.append(String.format("  [%s] %s (%d nodes affected)%n",
                        ce.type, ce.description, ce.affectedNodes.size()));
            }
            sb.append("\n");
        }

        // Hub events
        if (!report.hubEvents.isEmpty()) {
            sb.append("-- Hub Dynamics --\n");
            for (HubEvent he : report.hubEvents) {
                sb.append(String.format("  %s: %s (degree %d -> %d)%n",
                        he.type, he.node, he.oldDegree, he.newDegree));
            }
            sb.append("\n");
        }

        // Centrality shifts
        if (!report.centralityShifts.isEmpty()) {
            sb.append("-- Centrality Shifts (top 10) --\n");
            for (CentralityShift cs : report.centralityShifts) {
                sb.append(String.format("  %s: %s (%.4f -> %.4f)%n",
                        cs.classification, cs.node, cs.oldCentrality, cs.newCentrality));
            }
            sb.append("\n");
        }

        // Role transitions
        if (!report.roleTransitions.isEmpty()) {
            sb.append("-- Role Transitions --\n");
            for (RoleTransition rt : report.roleTransitions) {
                sb.append(String.format("  %s: %s -> %s%n", rt.node, rt.oldRole, rt.newRole));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    // -- HTML report -------------------------------------------------------

    /**
     * Generate a self-contained interactive HTML report with dark theme,
     * stability gauge, color-coded alerts, and expandable sections.
     *
     * @param report the analysis result
     * @return complete HTML document as a String
     */
    public String exportHtml(DriftReport report) {
        StringBuilder h = new StringBuilder();
        h.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">");
        h.append("<title>GraphSentinel -- Drift Report</title>");
        h.append("<style>");
        h.append("*{margin:0;padding:0;box-sizing:border-box}");
        h.append("body{font-family:'Segoe UI',system-ui,sans-serif;background:#0d1117;color:#c9d1d9;padding:2rem}");
        h.append("h1{color:#58a6ff;margin-bottom:.5rem}");
        h.append("h2{color:#79c0ff;margin:1.5rem 0 .5rem;cursor:pointer}");
        h.append("h2:hover{text-decoration:underline}");
        h.append(".card{background:#161b22;border:1px solid #30363d;border-radius:8px;padding:1.5rem;margin:1rem 0}");
        h.append(".badge{display:inline-block;padding:2px 10px;border-radius:12px;font-size:.85rem;font-weight:600;margin:2px}");
        h.append(".badge-critical{background:#da3633;color:#fff}");
        h.append(".badge-warning{background:#d29922;color:#000}");
        h.append(".badge-info{background:#388bfd;color:#fff}");
        h.append("table{width:100%;border-collapse:collapse;margin:.5rem 0}");
        h.append("th,td{text-align:left;padding:6px 12px;border-bottom:1px solid #21262d}");
        h.append("th{color:#8b949e;font-weight:600}");
        h.append(".section{display:block}");
        h.append(".gauge-container{text-align:center;margin:1rem 0}");
        h.append(".stats{display:flex;gap:2rem;flex-wrap:wrap;margin:.5rem 0}");
        h.append(".stat{text-align:center}.stat-val{font-size:2rem;font-weight:700;color:#58a6ff}.stat-lbl{color:#8b949e;font-size:.85rem}");
        h.append("</style></head><body>");

        // Title
        h.append("<h1>GraphSentinel -- Structural Drift Report</h1>");
        h.append("<p style=\"color:#8b949e\">Analyzed at ").append(new Date(report.timestamp)).append("</p>");

        // Stats overview
        h.append("<div class=\"card\"><div class=\"stats\">");
        h.append("<div class=\"stat\"><div class=\"stat-val\">").append(report.stabilityScore).append("</div><div class=\"stat-lbl\">Stability Score</div></div>");
        h.append("<div class=\"stat\"><div class=\"stat-val\">").append(report.nodesBefore).append(" -&gt; ").append(report.nodesAfter).append("</div><div class=\"stat-lbl\">Nodes</div></div>");
        h.append("<div class=\"stat\"><div class=\"stat-val\">").append(report.edgesBefore).append(" -&gt; ").append(report.edgesAfter).append("</div><div class=\"stat-lbl\">Edges</div></div>");
        h.append("<div class=\"stat\"><div class=\"stat-val\">").append(report.alerts.size()).append("</div><div class=\"stat-lbl\">Alerts</div></div>");
        h.append("</div></div>");

        // Stability gauge SVG
        String gaugeColor = report.stabilityScore > 70 ? "#3fb950" : report.stabilityScore > 40 ? "#d29922" : "#da3633";
        double angle = 180.0 * report.stabilityScore / 100.0;
        double rad = Math.toRadians(180 - angle);
        double ex = 100 + 70 * Math.cos(rad);
        double ey = 100 - 70 * Math.sin(rad);
        int largeArc = angle > 90 ? 1 : 0;
        h.append("<div class=\"gauge-container\"><svg width=\"200\" height=\"120\" viewBox=\"0 0 200 120\">");
        h.append("<path d=\"M30,100 A70,70 0 0,1 170,100\" fill=\"none\" stroke=\"#21262d\" stroke-width=\"12\" stroke-linecap=\"round\"/>");
        if (report.stabilityScore > 0) {
            h.append(String.format("<path d=\"M30,100 A70,70 0 %d,1 %.1f,%.1f\" fill=\"none\" stroke=\"%s\" stroke-width=\"12\" stroke-linecap=\"round\"/>",
                    largeArc, ex, ey, gaugeColor));
        }
        h.append(String.format("<text x=\"100\" y=\"95\" text-anchor=\"middle\" fill=\"%s\" font-size=\"28\" font-weight=\"700\">%d</text>",
                gaugeColor, report.stabilityScore));
        h.append("<text x=\"100\" y=\"115\" text-anchor=\"middle\" fill=\"#8b949e\" font-size=\"11\">").append(esc(report.stabilityGrade)).append("</text>");
        h.append("</svg></div>");

        // Alerts section
        if (!report.alerts.isEmpty()) {
            h.append("<div class=\"card\"><h2>Alerts</h2><div class=\"section\">");
            for (Alert a : report.alerts) {
                String badgeClass = "CRITICAL".equals(a.severity) ? "badge-critical"
                        : "WARNING".equals(a.severity) ? "badge-warning" : "badge-info";
                h.append("<div style=\"margin:.5rem 0\"><span class=\"badge ").append(badgeClass).append("\">");
                h.append(esc(a.severity)).append("</span> <b>").append(esc(a.category)).append("</b>: ");
                h.append(esc(a.description)).append("<br><i style=\"color:#8b949e\">-> ").append(esc(a.recommendation)).append("</i></div>");
            }
            h.append("</div></div>");
        }

        // Community events
        if (!report.communityEvents.isEmpty()) {
            h.append("<div class=\"card\"><h2>Community Events</h2><div class=\"section\">");
            h.append("<table><tr><th>Type</th><th>Description</th><th>Affected</th></tr>");
            for (CommunityEvent ce : report.communityEvents) {
                h.append("<tr><td>").append(esc(ce.type)).append("</td><td>").append(esc(ce.description));
                h.append("</td><td>").append(ce.affectedNodes.size()).append(" nodes</td></tr>");
            }
            h.append("</table></div></div>");
        }

        // Hub events
        if (!report.hubEvents.isEmpty()) {
            h.append("<div class=\"card\"><h2>Hub Dynamics</h2><div class=\"section\">");
            h.append("<table><tr><th>Node</th><th>Event</th><th>Degree Change</th></tr>");
            for (HubEvent he : report.hubEvents) {
                h.append("<tr><td>").append(esc(he.node)).append("</td><td>").append(esc(he.type));
                h.append("</td><td>").append(he.oldDegree).append(" -&gt; ").append(he.newDegree).append("</td></tr>");
            }
            h.append("</table></div></div>");
        }

        // Centrality shifts
        if (!report.centralityShifts.isEmpty()) {
            h.append("<div class=\"card\"><h2>Centrality Shifts</h2><div class=\"section\">");
            h.append("<table><tr><th>Node</th><th>Classification</th><th>Before</th><th>After</th></tr>");
            for (CentralityShift cs : report.centralityShifts) {
                h.append("<tr><td>").append(esc(cs.node)).append("</td><td>").append(esc(cs.classification));
                h.append("</td><td>").append(String.format("%.4f", cs.oldCentrality));
                h.append("</td><td>").append(String.format("%.4f", cs.newCentrality)).append("</td></tr>");
            }
            h.append("</table></div></div>");
        }

        // Role transitions
        if (!report.roleTransitions.isEmpty()) {
            h.append("<div class=\"card\"><h2>Role Transitions</h2><div class=\"section\">");
            h.append("<table><tr><th>Node</th><th>From</th><th>To</th></tr>");
            for (RoleTransition rt : report.roleTransitions) {
                h.append("<tr><td>").append(esc(rt.node)).append("</td><td>").append(esc(rt.oldRole));
                h.append("</td><td>").append(esc(rt.newRole)).append("</td></tr>");
            }
            h.append("</table></div></div>");
        }

        h.append("<p style=\"color:#484f58;text-align:center;margin-top:2rem\">Generated by GraphSentinel -- GraphVisual</p>");
        h.append("</body></html>");
        return h.toString();
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
