package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Community detection using the Louvain modularity optimization algorithm.
 *
 * <p>The Louvain method is a multi-level, greedy optimization algorithm that
 * maximizes modularity Q. It proceeds in two phases that are repeated:</p>
 * <ol>
 *   <li><b>Phase 1 (Local moves):</b> Each node is moved to the neighboring
 *       community that yields the greatest increase in modularity.</li>
 *   <li><b>Phase 2 (Aggregation):</b> Communities are collapsed into
 *       super-nodes, building a new weighted graph.</li>
 * </ol>
 * <p>A resolution parameter γ controls granularity (γ &gt; 1 = smaller
 * communities, γ &lt; 1 = larger ones).</p>
 *
 * @author zalenix
 */
public class LouvainCommunityDetector {

    private final Graph<String, edge> graph;
    private final double resolution;

    public LouvainCommunityDetector(Graph<String, edge> graph) {
        this(graph, 1.0);
    }

    public LouvainCommunityDetector(Graph<String, edge> graph, double resolution) {
        if (graph == null) throw new IllegalArgumentException("Graph must not be null");
        if (resolution <= 0) throw new IllegalArgumentException("Resolution must be positive");
        this.graph = graph;
        this.resolution = resolution;
    }

    // ─── Result classes ──────────────────────────────────────────────

    public static class Community implements Comparable<Community> {
        private int id;
        private final Set<String> members;
        private int internalEdges;
        private int externalEdges;
        private double internalWeight;

        public Community(int id) {
            this.id = id;
            this.members = new LinkedHashSet<String>();
        }

        public int getId() { return id; }
        void setId(int id) { this.id = id; }
        public Set<String> getMembers() { return Collections.unmodifiableSet(members); }
        public int getSize() { return members.size(); }
        public int getInternalEdges() { return internalEdges; }
        public int getExternalEdges() { return externalEdges; }
        public double getInternalWeight() { return internalWeight; }

        public double getIntraDensity() {
            int n = members.size();
            if (n < 2) return 0.0;
            return (2.0 * internalEdges) / ((long) n * (n - 1));
        }

        public double getInterIntraRatio() {
            int total = internalEdges + externalEdges;
            if (total == 0) return 0.0;
            return (double) externalEdges / total;
        }

        public int compareTo(Community other) {
            return Integer.compare(other.getSize(), this.getSize());
        }

        @Override
        public String toString() {
            return String.format("Community %d: %d members, %d internal edges, density=%.4f",
                    id, members.size(), internalEdges, getIntraDensity());
        }
    }

    public static class HierarchyLevel {
        private final int level;
        private final int communityCount;
        private final double modularity;
        private final Map<String, Integer> nodeAssignments;

        public HierarchyLevel(int level, int communityCount, double modularity,
                              Map<String, Integer> nodeAssignments) {
            this.level = level;
            this.communityCount = communityCount;
            this.modularity = modularity;
            this.nodeAssignments = Collections.unmodifiableMap(nodeAssignments);
        }

        public int getLevel() { return level; }
        public int getCommunityCount() { return communityCount; }
        public double getModularity() { return modularity; }
        public Map<String, Integer> getNodeAssignments() { return nodeAssignments; }
    }

    public static class LouvainResult {
        private final List<Community> communities;
        private final Map<String, Integer> nodeToCommunity;
        private final List<HierarchyLevel> hierarchy;
        private final double modularity;

        public LouvainResult(List<Community> communities, Map<String, Integer> nodeToCommunity,
                             List<HierarchyLevel> hierarchy, double modularity) {
            this.communities = Collections.unmodifiableList(communities);
            this.nodeToCommunity = Collections.unmodifiableMap(nodeToCommunity);
            this.hierarchy = Collections.unmodifiableList(hierarchy);
            this.modularity = modularity;
        }

        public List<Community> getCommunities() { return communities; }
        public Map<String, Integer> getNodeToCommunity() { return nodeToCommunity; }
        public List<HierarchyLevel> getHierarchy() { return hierarchy; }
        public double getModularity() { return modularity; }
        public int getCommunityCount() { return communities.size(); }

        public Community getCommunityOf(String node) {
            Integer id = nodeToCommunity.get(node);
            if (id == null || id < 0 || id >= communities.size()) return null;
            return communities.get(id);
        }

        public List<Community> getSignificantCommunities(int minSize) {
            List<Community> result = new ArrayList<Community>();
            for (Community c : communities) {
                if (c.getSize() >= minSize) result.add(c);
            }
            return result;
        }

        /** Exports community assignments as CSV. */
        public String exportCsv() {
            StringBuilder sb = new StringBuilder();
            sb.append("node,community_id,community_size\n");
            List<String> nodes = new ArrayList<String>(nodeToCommunity.keySet());
            Collections.sort(nodes);
            for (String node : nodes) {
                int cid = nodeToCommunity.get(node);
                sb.append(node).append(',').append(cid).append(',')
                  .append(communities.get(cid).getSize()).append('\n');
            }
            return sb.toString();
        }

        /** Exports results as JSON. */
        public String exportJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"modularity\": ").append(String.format("%.6f", modularity)).append(",\n");
            sb.append("  \"communityCount\": ").append(communities.size()).append(",\n");
            sb.append("  \"hierarchyLevels\": ").append(hierarchy.size()).append(",\n");
            sb.append("  \"communities\": [\n");
            for (int i = 0; i < communities.size(); i++) {
                Community c = communities.get(i);
                sb.append("    {\"id\": ").append(c.getId());
                sb.append(", \"size\": ").append(c.getSize());
                sb.append(", \"internalEdges\": ").append(c.getInternalEdges());
                sb.append(", \"externalEdges\": ").append(c.getExternalEdges());
                sb.append(", \"density\": ").append(String.format("%.6f", c.getIntraDensity()));
                sb.append(", \"members\": [");
                List<String> mems = new ArrayList<String>(c.getMembers());
                Collections.sort(mems);
                for (int j = 0; j < mems.size(); j++) {
                    if (j > 0) sb.append(", ");
                    sb.append("\"").append(mems.get(j)).append("\"");
                }
                sb.append("]}");
                if (i < communities.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ],\n");
            sb.append("  \"hierarchy\": [\n");
            for (int i = 0; i < hierarchy.size(); i++) {
                HierarchyLevel h = hierarchy.get(i);
                sb.append("    {\"level\": ").append(h.getLevel());
                sb.append(", \"communities\": ").append(h.getCommunityCount());
                sb.append(", \"modularity\": ").append(String.format("%.6f", h.getModularity()));
                sb.append("}");
                if (i < hierarchy.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ]\n}");
            return sb.toString();
        }

        /** Generates a human-readable text report. */
        public String generateReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Louvain Community Detection Report ===\n\n");
            sb.append(String.format("Final modularity: %.6f\n", modularity));
            sb.append(String.format("Communities detected: %d\n", communities.size()));
            sb.append(String.format("Hierarchy levels: %d\n\n", hierarchy.size()));
            if (!communities.isEmpty()) {
                int minS = Integer.MAX_VALUE, maxS = 0, totalS = 0;
                for (Community c : communities) {
                    minS = Math.min(minS, c.getSize());
                    maxS = Math.max(maxS, c.getSize());
                    totalS += c.getSize();
                }
                sb.append("Community size statistics:\n");
                sb.append(String.format("  Min: %d, Max: %d, Avg: %.1f\n\n",
                        minS, maxS, (double) totalS / communities.size()));
            }
            sb.append("Communities (sorted by size):\n");
            for (Community c : communities) {
                sb.append(String.format("  #%d: %d members, %d internal edges, %d external edges, density=%.4f\n",
                        c.getId(), c.getSize(), c.getInternalEdges(), c.getExternalEdges(), c.getIntraDensity()));
            }
            sb.append("\nHierarchy:\n");
            for (HierarchyLevel h : hierarchy) {
                sb.append(String.format("  Level %d: %d communities, modularity=%.6f\n",
                        h.getLevel(), h.getCommunityCount(), h.getModularity()));
            }
            return sb.toString();
        }
    }

    // ─── Louvain algorithm ───────────────────────────────────────────

    public LouvainResult detect() {
        Collection<String> vertices = graph.getVertices();
        if (vertices.isEmpty()) {
            return new LouvainResult(
                    Collections.<Community>emptyList(),
                    Collections.<String, Integer>emptyMap(),
                    Collections.<HierarchyLevel>emptyList(), 0.0);
        }

        List<String> nodeList = new ArrayList<String>(vertices);
        Map<String, Integer> nodeIndex = new HashMap<String, Integer>();
        for (int i = 0; i < nodeList.size(); i++) nodeIndex.put(nodeList.get(i), i);
        int n = nodeList.size();

        List<Map<Integer, Double>> adjWeight = buildAdjacency(nodeIndex, n);
        double[] degree = new double[n];
        double m2 = 0.0;
        for (int i = 0; i < n; i++) {
            double d = 0.0;
            for (double w : adjWeight.get(i).values()) d += w;
            degree[i] = d;
            m2 += d;
        }

        int[] community = new int[n];
        for (int i = 0; i < n; i++) community[i] = i;

        List<Set<Integer>> superToOriginal = new ArrayList<Set<Integer>>();
        for (int i = 0; i < n; i++) {
            Set<Integer> s = new HashSet<Integer>();
            s.add(i);
            superToOriginal.add(s);
        }

        List<HierarchyLevel> hierarchy = new ArrayList<HierarchyLevel>();
        int levelCount = 0;
        boolean improved = true;

        while (improved) {
            improved = phase1(adjWeight, degree, community, m2, n);
            double mod = computeModularity(adjWeight, degree, community, m2, n);

            Map<Integer, Integer> remap = normalizeIds(community, n);
            Map<String, Integer> origAssign = new HashMap<String, Integer>();
            for (int i = 0; i < n; i++) {
                int nc = remap.get(community[i]);
                for (int origIdx : superToOriginal.get(i))
                    origAssign.put(nodeList.get(origIdx), nc);
            }
            hierarchy.add(new HierarchyLevel(levelCount, remap.size(), mod, origAssign));

            if (!improved) break;

            Map<Integer, Integer> normRemap = normalizeIds(community, n);
            int newN = normRemap.size();
            if (newN == n) break;

            List<Map<Integer, Double>> newAdj = new ArrayList<Map<Integer, Double>>();
            for (int i = 0; i < newN; i++) newAdj.add(new HashMap<Integer, Double>());
            List<Set<Integer>> newSuper = new ArrayList<Set<Integer>>();
            for (int i = 0; i < newN; i++) newSuper.add(new HashSet<Integer>());

            for (int i = 0; i < n; i++) {
                int ci = normRemap.get(community[i]);
                newSuper.get(ci).addAll(superToOriginal.get(i));
                for (Map.Entry<Integer, Double> e : adjWeight.get(i).entrySet()) {
                    int cj = normRemap.get(community[e.getKey()]);
                    Double ex = newAdj.get(ci).get(cj);
                    newAdj.get(ci).put(cj, ex == null ? e.getValue() : ex + e.getValue());
                }
            }

            double[] newDeg = new double[newN];
            for (int i = 0; i < newN; i++) {
                double d = 0.0;
                for (double w : newAdj.get(i).values()) d += w;
                newDeg[i] = d;
            }

            adjWeight = newAdj;
            degree = newDeg;
            n = newN;
            community = new int[n];
            for (int i = 0; i < n; i++) community[i] = i;
            superToOriginal = newSuper;
            levelCount++;
            improved = true;
        }

        // Build final result
        HierarchyLevel finalLevel = hierarchy.get(hierarchy.size() - 1);
        Map<String, Integer> finalAssign = finalLevel.getNodeAssignments();
        double finalMod = finalLevel.getModularity();

        Map<Integer, Community> cmap = new LinkedHashMap<Integer, Community>();
        for (Map.Entry<String, Integer> e : finalAssign.entrySet()) {
            int cid = e.getValue();
            if (!cmap.containsKey(cid)) cmap.put(cid, new Community(cid));
            cmap.get(cid).members.add(e.getKey());
        }

        Set<edge> counted = new HashSet<edge>();
        for (edge e : graph.getEdges()) {
            if (counted.contains(e)) continue;
            counted.add(e);
            Integer c1 = finalAssign.get(e.getVertex1());
            Integer c2 = finalAssign.get(e.getVertex2());
            if (c1 == null || c2 == null) continue;
            if (c1.equals(c2)) {
                Community c = cmap.get(c1);
                c.internalEdges++;
                c.internalWeight += e.getWeight();
            } else {
                cmap.get(c1).externalEdges++;
                cmap.get(c2).externalEdges++;
            }
        }

        List<Community> sorted = new ArrayList<Community>(cmap.values());
        Collections.sort(sorted);
        Map<Integer, Integer> idRemap = new HashMap<Integer, Integer>();
        for (int i = 0; i < sorted.size(); i++) {
            idRemap.put(sorted.get(i).getId(), i);
            sorted.get(i).setId(i);
        }
        Map<String, Integer> updatedAssign = new HashMap<String, Integer>();
        for (Map.Entry<String, Integer> e : finalAssign.entrySet())
            updatedAssign.put(e.getKey(), idRemap.get(e.getValue()));

        return new LouvainResult(sorted, updatedAssign, hierarchy, finalMod);
    }

    // ─── Internal ────────────────────────────────────────────────────

    private List<Map<Integer, Double>> buildAdjacency(Map<String, Integer> nodeIndex, int n) {
        List<Map<Integer, Double>> adj = new ArrayList<Map<Integer, Double>>();
        for (int i = 0; i < n; i++) adj.add(new HashMap<Integer, Double>());
        for (edge e : graph.getEdges()) {
            Integer i = nodeIndex.get(e.getVertex1());
            Integer j = nodeIndex.get(e.getVertex2());
            if (i == null || j == null) continue;
            double w = e.getWeight();
            if (w == 0.0) w = 1.0;
            Double ex = adj.get(i).get(j);
            adj.get(i).put(j, ex == null ? w : ex + w);
            ex = adj.get(j).get(i);
            adj.get(j).put(i, ex == null ? w : ex + w);
        }
        return adj;
    }

    private Map<Integer, Integer> normalizeIds(int[] community, int n) {
        Map<Integer, Integer> remap = new HashMap<Integer, Integer>();
        int next = 0;
        for (int i = 0; i < n; i++)
            if (!remap.containsKey(community[i])) remap.put(community[i], next++);
        return remap;
    }

    private boolean phase1(List<Map<Integer, Double>> adj, double[] degree,
                           int[] community, double m2, int n) {
        if (m2 == 0.0) return false;
        boolean anyMoved = false;
        boolean changed = true;

        // Use a large enough array for community degree sums
        // Find max community id
        int maxC = 0;
        for (int i = 0; i < n; i++) if (community[i] > maxC) maxC = community[i];
        double[] cDegSum = new double[maxC + n + 1];
        for (int i = 0; i < n; i++) cDegSum[community[i]] += degree[i];

        while (changed) {
            changed = false;
            for (int i = 0; i < n; i++) {
                int curC = community[i];
                double ki = degree[i];

                Map<Integer, Double> nw = new HashMap<Integer, Double>();
                for (Map.Entry<Integer, Double> e : adj.get(i).entrySet()) {
                    int cj = community[e.getKey()];
                    Double ex = nw.get(cj);
                    nw.put(cj, ex == null ? e.getValue() : ex + e.getValue());
                }

                cDegSum[curC] -= ki;

                Double kiCur = nw.get(curC);
                if (kiCur == null) kiCur = 0.0;

                int bestC = curC;
                double bestDQ = 0.0;

                for (Map.Entry<Integer, Double> e : nw.entrySet()) {
                    int tc = e.getKey();
                    double kiT = e.getValue();
                    double dq = (kiT - kiCur) / (m2 / 2.0)
                            - resolution * ki * (cDegSum[tc] - cDegSum[curC]) / (m2 * m2 / 2.0);
                    if (dq > bestDQ) { bestDQ = dq; bestC = tc; }
                }

                community[i] = bestC;
                cDegSum[bestC] += ki;
                if (bestC != curC) { changed = true; anyMoved = true; }
            }
        }
        return anyMoved;
    }

    private double computeModularity(List<Map<Integer, Double>> adj, double[] degree,
                                     int[] community, double m2, int n) {
        if (m2 == 0.0) return 0.0;
        double q = 0.0;
        for (int i = 0; i < n; i++) {
            for (Map.Entry<Integer, Double> e : adj.get(i).entrySet()) {
                int j = e.getKey();
                if (community[i] == community[j])
                    q += e.getValue() - resolution * degree[i] * degree[j] / m2;
            }
        }
        return q / m2;
    }
}
