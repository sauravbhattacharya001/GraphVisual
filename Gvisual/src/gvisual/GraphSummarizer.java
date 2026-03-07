package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Generates a human-readable narrative summary of a graph's structure,
 * combining key metrics into a concise text report. Useful for quick
 * understanding, embedding in presentations, or exporting as a text digest.
 *
 * <p>The summary covers: size classification, density, connectivity,
 * degree distribution, relationship composition, hub nodes, and
 * structural observations.</p>
 *
 * <p>Usage:
 * <pre>
 *   GraphSummarizer summarizer = new GraphSummarizer(graph, friendEdges, fsEdges,
 *       classmateEdges, strangerEdges, studyGEdges);
 *   String summary = summarizer.generateSummary();
 *   // or individual sections:
 *   String overview = summarizer.getOverviewSection();
 * </pre></p>
 *
 * @author zalenix
 */
public class GraphSummarizer {

    private final Graph<String, edge> graph;
    private final GraphStats stats;
    private final CommunityDetector communities;
    private final List<edge> friendEdges;
    private final List<edge> fsEdges;
    private final List<edge> classmateEdges;
    private final List<edge> strangerEdges;
    private final List<edge> studyGEdges;

    /**
     * Creates a new GraphSummarizer.
     *
     * @param graph          the JUNG graph to summarize
     * @param friendEdges    friend edges
     * @param fsEdges        familiar stranger edges
     * @param classmateEdges classmate edges
     * @param strangerEdges  stranger edges
     * @param studyGEdges    study group edges
     * @throws IllegalArgumentException if graph is null
     */
    public GraphSummarizer(Graph<String, edge> graph,
                           List<edge> friendEdges,
                           List<edge> fsEdges,
                           List<edge> classmateEdges,
                           List<edge> strangerEdges,
                           List<edge> studyGEdges) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.friendEdges = friendEdges != null ? friendEdges : new ArrayList<edge>();
        this.fsEdges = fsEdges != null ? fsEdges : new ArrayList<edge>();
        this.classmateEdges = classmateEdges != null ? classmateEdges : new ArrayList<edge>();
        this.strangerEdges = strangerEdges != null ? strangerEdges : new ArrayList<edge>();
        this.studyGEdges = studyGEdges != null ? studyGEdges : new ArrayList<edge>();
        this.stats = new GraphStats(graph, this.friendEdges, this.fsEdges,
                this.classmateEdges, this.strangerEdges, this.studyGEdges);
        this.communities = new CommunityDetector(graph);
    }

    /**
     * Generates the full narrative summary.
     *
     * @return multi-paragraph human-readable summary
     */
    public String generateSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Graph Summary ===\n\n");
        sb.append(getOverviewSection()).append("\n\n");
        sb.append(getDensitySection()).append("\n\n");
        sb.append(getConnectivitySection()).append("\n\n");
        sb.append(getCompositionSection()).append("\n\n");
        sb.append(getHubSection()).append("\n\n");
        sb.append(getStructuralObservations());
        return sb.toString();
    }

    /**
     * Size and scale overview.
     */
    public String getOverviewSection() {
        int nodes = stats.getNodeCount();
        int edges = stats.getVisibleEdgeCount();
        String sizeClass = classifySize(nodes);

        return String.format("This is a %s network with %d nodes and %d edges. %s",
                sizeClass, nodes, edges, getSizeContext(nodes, edges));
    }

    /**
     * Density and degree analysis.
     */
    public String getDensitySection() {
        double density = stats.getDensity();
        double avgDeg = stats.getAverageDegree();
        int maxDeg = stats.getMaxDegree();
        String densityDesc = classifyDensity(density);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("The network is %s (density: %.4f). ", densityDesc, density));
        sb.append(String.format("On average, each node connects to %.1f others, ", avgDeg));
        sb.append(String.format("with the most connected node having %d connections.", maxDeg));

        if (maxDeg > 0 && avgDeg > 0 && maxDeg > avgDeg * 5) {
            sb.append(" There is a significant degree disparity, suggesting hub-dominated topology.");
        }

        return sb.toString();
    }

    /**
     * Connectivity and component analysis.
     */
    public String getConnectivitySection() {
        List<CommunityDetector.Community> comms = communities.getCommunities();
        int isolated = stats.getIsolatedNodeCount();
        int nodes = stats.getNodeCount();

        StringBuilder sb = new StringBuilder();
        if (comms.size() == 1) {
            sb.append("The network is fully connected — all nodes can reach each other.");
        } else {
            sb.append(String.format("The network has %d connected components. ", comms.size()));
            if (!comms.isEmpty()) {
                sb.append(String.format("The largest contains %d nodes (%.0f%% of the network).",
                        comms.get(0).getSize(),
                        nodes > 0 ? (100.0 * comms.get(0).getSize() / nodes) : 0));
            }
        }

        if (isolated > 0) {
            sb.append(String.format(" %d node%s %s isolated (no connections).",
                    isolated, isolated == 1 ? "" : "s", isolated == 1 ? "is" : "are"));
        }

        return sb.toString();
    }

    /**
     * Relationship type composition breakdown.
     */
    public String getCompositionSection() {
        int total = stats.getTotalEdgeCount();
        if (total == 0) {
            return "No relationship data is available for composition analysis.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Relationship composition: ");

        List<String[]> parts = new ArrayList<String[]>();
        addIfNonZero(parts, "friends", stats.getFriendCount(), total);
        addIfNonZero(parts, "familiar strangers", stats.getFsCount(), total);
        addIfNonZero(parts, "classmates", stats.getClassmateCount(), total);
        addIfNonZero(parts, "strangers", stats.getStrangerCount(), total);
        addIfNonZero(parts, "study group", stats.getStudyGroupCount(), total);

        for (int i = 0; i < parts.size(); i++) {
            if (i > 0 && i == parts.size() - 1) {
                sb.append(", and ");
            } else if (i > 0) {
                sb.append(", ");
            }
            sb.append(parts.get(i)[0]);
        }
        sb.append(".");

        // Identify dominant type
        String dominant = findDominant(total);
        if (dominant != null) {
            sb.append(String.format(" The network is predominantly %s-based.", dominant));
        }

        return sb.toString();
    }

    /**
     * Hub nodes section.
     */
    public String getHubSection() {
        List<String> topNodes = stats.getTopNodes(5);
        if (topNodes.isEmpty()) {
            return "No hub nodes identified (empty graph).";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Key hub nodes (most connected): ");
        for (int i = 0; i < topNodes.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(topNodes.get(i));
        }
        sb.append(".");
        return sb.toString();
    }

    /**
     * High-level structural observations and insights.
     */
    public String getStructuralObservations() {
        int nodes = stats.getNodeCount();
        int edges = stats.getVisibleEdgeCount();
        double density = stats.getDensity();
        double avgDeg = stats.getAverageDegree();
        int maxDeg = stats.getMaxDegree();
        List<CommunityDetector.Community> comms = communities.getCommunities();

        List<String> observations = new ArrayList<String>();

        // Tree-like?
        if (nodes > 1 && edges == nodes - 1 && comms.size() == 1) {
            observations.add("The network forms a tree structure (minimally connected).");
        }

        // Near-complete?
        if (density > 0.8 && nodes > 3) {
            observations.add("The network is near-complete — most nodes know each other.");
        }

        // Power-law hint
        if (maxDeg > 0 && avgDeg > 0 && maxDeg > avgDeg * 10) {
            observations.add("Extreme degree inequality suggests a scale-free or power-law topology.");
        }

        // Small components
        int tinyComms = 0;
        for (CommunityDetector.Community c : comms) {
            if (c.getSize() <= 2) tinyComms++;
        }
        if (tinyComms > comms.size() / 2 && comms.size() > 3) {
            observations.add("Many small isolated clusters suggest fragmented social structure.");
        }

        // Average weight
        double avgWeight = stats.getAverageWeight();
        if (avgWeight > 0) {
            observations.add(String.format("Average edge weight is %.2f, indicating %s interactions.",
                    avgWeight, avgWeight > 100 ? "strong" : avgWeight > 30 ? "moderate" : "light"));
        }

        if (observations.isEmpty()) {
            return "No notable structural patterns detected.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Structural observations:\n");
        for (String obs : observations) {
            sb.append("  • ").append(obs).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Returns a compact one-line summary (for tooltips, status bars, etc.).
     *
     * @return single-line summary string
     */
    public String getOneLiner() {
        int n = stats.getNodeCount();
        int e = stats.getVisibleEdgeCount();
        double d = stats.getDensity();
        List<CommunityDetector.Community> comms = communities.getCommunities();
        return String.format("%d nodes, %d edges, density %.4f, %d component%s",
                n, e, d, comms.size(), comms.size() == 1 ? "" : "s");
    }

    // ---- Helper methods ----

    private String classifySize(int nodes) {
        if (nodes == 0) return "empty";
        if (nodes <= 10) return "small";
        if (nodes <= 50) return "medium";
        if (nodes <= 200) return "large";
        return "very large";
    }

    private String getSizeContext(int nodes, int edges) {
        if (nodes == 0) return "The graph contains no data.";
        if (edges == 0) return "No connections exist between nodes.";
        double ratio = (double) edges / nodes;
        if (ratio < 1) return "The network is sparsely connected.";
        if (ratio < 3) return "The network has moderate connectivity.";
        return "The network is richly interconnected.";
    }

    private String classifyDensity(double density) {
        if (density < 0.05) return "very sparse";
        if (density < 0.15) return "sparse";
        if (density < 0.35) return "moderately dense";
        if (density < 0.65) return "dense";
        return "very dense";
    }

    private void addIfNonZero(List<String[]> parts, String label, int count, int total) {
        if (count > 0) {
            double pct = 100.0 * count / total;
            parts.add(new String[]{String.format("%s %.0f%% (%d)", label, pct, count)});
        }
    }

    private String findDominant(int total) {
        int threshold = total / 2; // >50% is dominant
        if (stats.getFriendCount() > threshold) return "friendship";
        if (stats.getFsCount() > threshold) return "familiar stranger";
        if (stats.getClassmateCount() > threshold) return "classmate";
        if (stats.getStrangerCount() > threshold) return "stranger";
        if (stats.getStudyGroupCount() > threshold) return "study group";
        return null;
    }
}
