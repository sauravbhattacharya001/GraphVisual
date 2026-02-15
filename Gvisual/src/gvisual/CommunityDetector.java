package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Detects communities in a JUNG graph using connected-component analysis
 * and provides metrics for each community. Communities are ranked by size
 * (largest first) and each node is assigned a community ID.
 *
 * <p>This enables researchers to identify social clusters, study their
 * composition (relationship types), and track how communities evolve
 * over the timeline.</p>
 *
 * @author zalenix
 */
public class CommunityDetector {

    private final Graph<String, edge> graph;

    /**
     * Creates a new CommunityDetector for the given graph.
     *
     * @param graph the JUNG graph to analyze
     * @throws IllegalArgumentException if graph is null
     */
    public CommunityDetector(Graph<String, edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
    }

    /**
     * Represents a single detected community with its members and metrics.
     */
    public static class Community implements Comparable<Community> {
        private final int id;
        private final Set<String> members;
        private final Map<String, Integer> edgeTypeCounts;
        private int internalEdges;
        private double totalWeight;

        public Community(int id) {
            this.id = id;
            this.members = new LinkedHashSet<String>();
            this.edgeTypeCounts = new LinkedHashMap<String, Integer>();
            this.internalEdges = 0;
            this.totalWeight = 0.0;
        }

        /** Unique community identifier (0-based, ranked by size). */
        public int getId() { return id; }

        /** Set of vertex IDs belonging to this community. */
        public Set<String> getMembers() { return Collections.unmodifiableSet(members); }

        /** Number of members in this community. */
        public int getSize() { return members.size(); }

        /** Number of edges internal to this community. */
        public int getInternalEdges() { return internalEdges; }

        /** Total weight of internal edges. */
        public double getTotalWeight() { return totalWeight; }

        /** Counts of each edge type within this community. */
        public Map<String, Integer> getEdgeTypeCounts() {
            return Collections.unmodifiableMap(edgeTypeCounts);
        }

        /**
         * Internal density: ratio of actual internal edges to maximum possible.
         * For undirected graph: 2*|E| / (|V| * (|V|-1)).
         * Returns 0 if fewer than 2 members.
         */
        public double getDensity() {
            int n = members.size();
            if (n < 2) return 0.0;
            return (2.0 * internalEdges) / (n * (n - 1));
        }

        /**
         * Average edge weight within this community.
         */
        public double getAverageWeight() {
            if (internalEdges == 0) return 0.0;
            return totalWeight / internalEdges;
        }

        /**
         * Returns the dominant (most common) edge type in this community.
         */
        public String getDominantType() {
            String dominant = "none";
            int maxCount = 0;
            for (Map.Entry<String, Integer> entry : edgeTypeCounts.entrySet()) {
                if (entry.getValue() > maxCount) {
                    maxCount = entry.getValue();
                    dominant = entry.getKey();
                }
            }
            return dominant;
        }

        /** Sort communities largest-first. */
        public int compareTo(Community other) {
            return Integer.compare(other.getSize(), this.getSize());
        }

        @Override
        public String toString() {
            return String.format("Community %d: %d members, %d edges, density=%.3f, dominant=%s",
                    id, members.size(), internalEdges, getDensity(), getDominantType());
        }
    }

    /**
     * Result of community detection containing all communities and a
     * node-to-community mapping.
     */
    public static class DetectionResult {
        private final List<Community> communities;
        private final Map<String, Integer> nodeToCommunity;

        public DetectionResult(List<Community> communities, Map<String, Integer> nodeToCommunity) {
            this.communities = Collections.unmodifiableList(communities);
            this.nodeToCommunity = Collections.unmodifiableMap(nodeToCommunity);
        }

        /** All communities, sorted largest-first. */
        public List<Community> getCommunities() { return communities; }

        /** Maps each vertex ID to its community ID. */
        public Map<String, Integer> getNodeToCommunity() { return nodeToCommunity; }

        /** Total number of communities detected. */
        public int getCommunityCount() { return communities.size(); }

        /**
         * Returns only non-trivial communities (size >= minSize).
         */
        public List<Community> getSignificantCommunities(int minSize) {
            List<Community> result = new ArrayList<Community>();
            for (Community c : communities) {
                if (c.getSize() >= minSize) {
                    result.add(c);
                }
            }
            return result;
        }

        /**
         * Returns the community a given node belongs to, or null if not found.
         */
        public Community getCommunityOf(String node) {
            Integer id = nodeToCommunity.get(node);
            if (id == null) return null;
            for (Community c : communities) {
                if (c.getId() == id) return c;
            }
            return null;
        }

        /**
         * Modularity score: measures how well the graph is partitioned.
         * Higher values (closer to 1) indicate stronger community structure.
         * Q = sum_c [ (e_c / m) - (d_c / 2m)^2 ]
         * where e_c = internal edges, m = total edges, d_c = sum of degrees.
         */
        public double getModularity(Graph<String, edge> graph) {
            int m = graph.getEdgeCount();
            if (m == 0) return 0.0;

            double q = 0.0;
            for (Community c : communities) {
                double ec = (double) c.getInternalEdges() / m;
                double dc = 0;
                for (String node : c.getMembers()) {
                    dc += graph.degree(node);
                }
                double ac = dc / (2.0 * m);
                q += ec - (ac * ac);
            }
            return q;
        }
    }

    /**
     * Detects communities using connected component analysis (BFS-based).
     * Each connected component is treated as a community. Communities are
     * ranked by size (largest first) and enriched with edge-type breakdowns.
     *
     * @return detection result with communities and node mappings
     */
    public DetectionResult detect() {
        Set<String> visited = new HashSet<String>();
        List<Community> communities = new ArrayList<Community>();
        Map<String, Integer> nodeToCommunity = new HashMap<String, Integer>();
        int communityId = 0;

        // Find connected components via BFS
        for (String vertex : graph.getVertices()) {
            if (visited.contains(vertex)) continue;

            Community community = new Community(communityId);
            Queue<String> queue = new LinkedList<String>();
            queue.add(vertex);
            visited.add(vertex);

            while (!queue.isEmpty()) {
                String current = queue.poll();
                community.members.add(current);
                nodeToCommunity.put(current, communityId);

                for (edge e : graph.getIncidentEdges(current)) {
                    String neighbor = getOtherEnd(e, current);
                    if (neighbor != null && !visited.contains(neighbor)) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }

            // Compute internal edge metrics
            Set<edge> counted = new HashSet<edge>();
            for (String member : community.members) {
                for (edge e : graph.getIncidentEdges(member)) {
                    if (counted.contains(e)) continue;
                    String other = getOtherEnd(e, member);
                    if (other != null && community.members.contains(other)) {
                        counted.add(e);
                        community.internalEdges++;
                        community.totalWeight += e.getWeight();
                        String type = e.getType();
                        Integer count = community.edgeTypeCounts.get(type);
                        community.edgeTypeCounts.put(type, count == null ? 1 : count + 1);
                    }
                }
            }

            communities.add(community);
            communityId++;
        }

        // Sort by size (largest first) and reassign IDs
        Collections.sort(communities);
        List<Community> sorted = new ArrayList<Community>();
        Map<String, Integer> updatedMapping = new HashMap<String, Integer>();
        for (int i = 0; i < communities.size(); i++) {
            Community old = communities.get(i);
            Community reindexed = new Community(i);
            reindexed.members.addAll(old.members);
            reindexed.internalEdges = old.internalEdges;
            reindexed.totalWeight = old.totalWeight;
            reindexed.edgeTypeCounts.putAll(old.edgeTypeCounts);
            sorted.add(reindexed);
            for (String member : reindexed.members) {
                updatedMapping.put(member, i);
            }
        }

        return new DetectionResult(sorted, updatedMapping);
    }

    private String getOtherEnd(edge e, String current) {
        String v1 = e.getVertex1();
        String v2 = e.getVertex2();
        if (current.equals(v1)) return v2;
        if (current.equals(v2)) return v1;
        return null;
    }
}
