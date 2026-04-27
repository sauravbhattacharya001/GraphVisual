package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Graph Intelligence Advisor — proactively analyzes a graph's structural
 * properties and recommends which analysis algorithms would yield the most
 * interesting insights, ranked by confidence and expected value.
 *
 * <p>Instead of requiring users to know which of the 40+ available analyzers
 * to run, the advisor scans the graph's topology (size, density, degree
 * distribution, connectivity, edge types) and generates a prioritized list
 * of recommended analyses with reasoning.</p>
 *
 * <h3>How it works:</h3>
 * <ol>
 *   <li>Compute lightweight structural fingerprint (O(V+E))</li>
 *   <li>Match fingerprint against analysis preconditions and value heuristics</li>
 *   <li>Rank recommendations by expected insight value</li>
 *   <li>Generate actionable report with reasoning for each recommendation</li>
 * </ol>
 *
 * <h3>Agentic behavior:</h3>
 * <ul>
 *   <li><b>Proactive</b> — suggests without being asked</li>
 *   <li><b>Context-aware</b> — recommendations change based on graph structure</li>
 *   <li><b>Explanatory</b> — provides reasoning, not just suggestions</li>
 *   <li><b>Prioritized</b> — ranks by expected insight value</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 *   GraphIntelligenceAdvisor advisor = new GraphIntelligenceAdvisor(graph);
 *   advisor.analyze();
 *   String report = advisor.formatTextReport();
 *   advisor.exportHtml(new File("advisor-report.html"));
 *   List&lt;Recommendation&gt; top = advisor.getTopRecommendations(5);
 * </pre>
 *
 * @author zalenix
 */
public class GraphIntelligenceAdvisor {

    /** A single analysis recommendation with reasoning and priority. */
    public static class Recommendation implements Comparable<Recommendation> {
        private final String analysisName;
        private final String category;
        private final double confidence;  // 0.0–1.0
        private final String reasoning;
        private final String expectedInsight;
        private final String difficulty;  // "quick", "moderate", "intensive"

        public Recommendation(String analysisName, String category,
                              double confidence, String reasoning,
                              String expectedInsight, String difficulty) {
            this.analysisName = analysisName;
            this.category = category;
            this.confidence = Math.max(0.0, Math.min(1.0, confidence));
            this.reasoning = reasoning;
            this.expectedInsight = expectedInsight;
            this.difficulty = difficulty;
        }

        public String getAnalysisName() { return analysisName; }
        public String getCategory() { return category; }
        public double getConfidence() { return confidence; }
        public String getReasoning() { return reasoning; }
        public String getExpectedInsight() { return expectedInsight; }
        public String getDifficulty() { return difficulty; }

        @Override
        public int compareTo(Recommendation o) {
            return Double.compare(o.confidence, this.confidence); // descending
        }
    }

    /** Structural fingerprint of the graph. */
    public static class GraphFingerprint {
        int nodeCount;
        int edgeCount;
        double density;
        double avgDegree;
        int maxDegree;
        int minDegree;
        double degreeVariance;
        int componentCount;
        int largestComponentSize;
        boolean hasIsolatedNodes;
        boolean hasHighDegreeHubs;
        int edgeTypeCount;
        Map<String, Integer> edgeTypeCounts = new LinkedHashMap<>();
        boolean hasSelfLoops;
        boolean hasWeightedEdges;
        boolean hasTemporalEdges;
        double clusteringEstimate;
    }

    private final Graph<String, Edge> graph;
    private GraphFingerprint fingerprint;
    private final List<Recommendation> recommendations = new ArrayList<>();

    public GraphIntelligenceAdvisor(Graph<String, Edge> graph) {
        this.graph = Objects.requireNonNull(graph, "graph must not be null");
    }

    /** Run the full analysis pipeline: fingerprint → match → rank. */
    public void analyze() {
        this.fingerprint = computeFingerprint();
        recommendations.clear();
        generateRecommendations();
        Collections.sort(recommendations);
    }

    /** Get the structural fingerprint (available after analyze()). */
    public GraphFingerprint getFingerprint() {
        return fingerprint;
    }

    /** Get all recommendations sorted by confidence (descending). */
    public List<Recommendation> getRecommendations() {
        return Collections.unmodifiableList(recommendations);
    }

    /** Get the top N recommendations. */
    public List<Recommendation> getTopRecommendations(int n) {
        return recommendations.subList(0, Math.min(n, recommendations.size()));
    }

    // ── Fingerprint computation ──────────────────────────────────────

    private GraphFingerprint computeFingerprint() {
        GraphFingerprint fp = new GraphFingerprint();
        Collection<String> vertices = graph.getVertices();
        Collection<Edge> edges = graph.getEdges();

        fp.nodeCount = vertices.size();
        fp.edgeCount = edges.size();

        if (fp.nodeCount <= 1) {
            fp.density = 0;
            fp.avgDegree = 0;
            fp.componentCount = fp.nodeCount;
            fp.largestComponentSize = fp.nodeCount;
            return fp;
        }

        // Density
        long maxEdges = (long) fp.nodeCount * (fp.nodeCount - 1) / 2;
        fp.density = maxEdges > 0 ? (double) fp.edgeCount / maxEdges : 0;

        // Degree stats
        int[] degrees = new int[fp.nodeCount];
        int i = 0;
        int totalDegree = 0;
        fp.maxDegree = 0;
        fp.minDegree = Integer.MAX_VALUE;
        for (String v : vertices) {
            int d = graph.degree(v);
            degrees[i++] = d;
            totalDegree += d;
            if (d > fp.maxDegree) fp.maxDegree = d;
            if (d < fp.minDegree) fp.minDegree = d;
            if (d == 0) fp.hasIsolatedNodes = true;
        }
        fp.avgDegree = (double) totalDegree / fp.nodeCount;

        // Degree variance
        double sumSqDiff = 0;
        for (int d : degrees) {
            double diff = d - fp.avgDegree;
            sumSqDiff += diff * diff;
        }
        fp.degreeVariance = sumSqDiff / fp.nodeCount;

        // Hub detection: nodes with degree > 2 * average
        fp.hasHighDegreeHubs = fp.maxDegree > 2 * fp.avgDegree && fp.avgDegree > 1;

        // Edge type analysis
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        boolean hasSelfLoops = false;
        boolean hasWeighted = false;
        boolean hasTemporal = false;
        for (Edge e : edges) {
            String type = e.getType() != null ? e.getType() : "unknown";
            typeCounts.merge(type, 1, Integer::sum);
            if (e.getVertex1() != null && e.getVertex1().equals(e.getVertex2())) {
                hasSelfLoops = true;
            }
            if (e.getWeight() != 0) hasWeighted = true;
            if (e.getTimestamp() != null) hasTemporal = true;
        }
        fp.edgeTypeCounts = typeCounts;
        fp.edgeTypeCount = typeCounts.size();
        fp.hasSelfLoops = hasSelfLoops;
        fp.hasWeightedEdges = hasWeighted;
        fp.hasTemporalEdges = hasTemporal;

        // Component analysis (BFS)
        Set<String> visited = new HashSet<>();
        fp.componentCount = 0;
        fp.largestComponentSize = 0;
        for (String v : vertices) {
            if (!visited.contains(v)) {
                fp.componentCount++;
                int compSize = bfsSize(v, visited);
                if (compSize > fp.largestComponentSize) {
                    fp.largestComponentSize = compSize;
                }
            }
        }

        // Clustering coefficient estimate (sample up to 200 nodes)
        fp.clusteringEstimate = estimateClustering(vertices, 200);

        return fp;
    }

    private int bfsSize(String start, Set<String> visited) {
        Queue<String> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start);
        int size = 0;
        while (!queue.isEmpty()) {
            String v = queue.poll();
            size++;
            Collection<String> neighbors = graph.getNeighbors(v);
            if (neighbors != null) {
                for (String n : neighbors) {
                    if (visited.add(n)) {
                        queue.add(n);
                    }
                }
            }
        }
        return size;
    }

    private double estimateClustering(Collection<String> vertices, int sampleSize) {
        List<String> vList = new ArrayList<>(vertices);
        if (vList.size() > sampleSize) {
            Collections.shuffle(vList, new Random(42));
            vList = vList.subList(0, sampleSize);
        }
        double totalCC = 0;
        int counted = 0;
        for (String v : vList) {
            Collection<String> neighbors = graph.getNeighbors(v);
            if (neighbors == null || neighbors.size() < 2) continue;
            List<String> nList = new ArrayList<>(neighbors);
            int links = 0;
            for (int a = 0; a < nList.size(); a++) {
                for (int b = a + 1; b < nList.size(); b++) {
                    if (graph.isNeighbor(nList.get(a), nList.get(b))) {
                        links++;
                    }
                }
            }
            int possible = nList.size() * (nList.size() - 1) / 2;
            totalCC += (double) links / possible;
            counted++;
        }
        return counted > 0 ? totalCC / counted : 0;
    }

    // ── Recommendation generation ────────────────────────────────────

    private void generateRecommendations() {
        GraphFingerprint fp = this.fingerprint;
        if (fp.nodeCount == 0) return;

        // Community Detection
        if (fp.nodeCount >= 5 && fp.edgeCount >= fp.nodeCount) {
            double conf = 0.5;
            String reason = "Graph has " + fp.nodeCount + " nodes and density " +
                    String.format("%.4f", fp.density) + ". ";
            if (fp.componentCount > 1) {
                conf += 0.2;
                reason += "Multiple components (" + fp.componentCount + ") suggest natural groupings. ";
            }
            if (fp.clusteringEstimate > 0.3) {
                conf += 0.2;
                reason += "High clustering coefficient (" + String.format("%.2f", fp.clusteringEstimate) +
                          ") indicates tightly-knit groups. ";
            }
            if (fp.edgeTypeCount > 1) {
                conf += 0.1;
                reason += "Multiple edge types may reveal community structure along type boundaries.";
            }
            recommendations.add(new Recommendation(
                    "Community Detection (Louvain)", "Structure",
                    conf, reason.trim(),
                    "Discover hidden groups/clusters and understand how the network naturally partitions",
                    fp.nodeCount > 500 ? "moderate" : "quick"));
        }

        // Anomaly Detection
        if (fp.nodeCount >= 10 && fp.degreeVariance > 1) {
            double conf = 0.4;
            String reason = "";
            if (fp.hasHighDegreeHubs) {
                conf += 0.3;
                reason += "Hub nodes detected (max degree " + fp.maxDegree +
                          " vs avg " + String.format("%.1f", fp.avgDegree) + ") — likely structural outliers. ";
            }
            if (fp.degreeVariance > fp.avgDegree * 2) {
                conf += 0.2;
                reason += "High degree variance (" + String.format("%.1f", fp.degreeVariance) +
                          ") suggests heterogeneous connectivity patterns. ";
            }
            recommendations.add(new Recommendation(
                    "Anomaly Detection", "Intelligence",
                    conf, reason.trim(),
                    "Identify suspicious or unusual nodes that deviate from normal network patterns",
                    "quick"));
        }

        // PageRank / Centrality
        if (fp.nodeCount >= 3 && fp.hasHighDegreeHubs) {
            double conf = 0.7;
            String reason = "Network has hub nodes (max degree " + fp.maxDegree + "). ";
            reason += "PageRank will reveal which nodes are truly influential vs just well-connected.";
            recommendations.add(new Recommendation(
                    "PageRank & Centrality Analysis", "Influence",
                    conf, reason,
                    "Rank nodes by true influence — find leaders, bridges, and gatekeepers",
                    "quick"));
        } else if (fp.nodeCount >= 3) {
            recommendations.add(new Recommendation(
                    "Centrality Analysis", "Influence",
                    0.4, "Even without obvious hubs, centrality reveals hidden influencers " +
                         "and structural bottlenecks.",
                    "Identify which nodes control information flow through the network",
                    "quick"));
        }

        // Link Prediction
        if (fp.nodeCount >= 5 && fp.density > 0.01 && fp.density < 0.8) {
            double conf = 0.4;
            String reason = "Moderate density (" + String.format("%.4f", fp.density) +
                    ") — neither too sparse nor too dense for prediction. ";
            if (fp.clusteringEstimate > 0.2) {
                conf += 0.25;
                reason += "Clustering patterns provide strong signal for predicting missing links.";
            }
            recommendations.add(new Recommendation(
                    "Link Prediction", "Prediction",
                    conf, reason.trim(),
                    "Predict likely missing or future connections between nodes",
                    "moderate"));
        }

        // Resilience Analysis
        if (fp.nodeCount >= 5) {
            double conf = 0.3;
            String reason = "";
            if (fp.componentCount == 1 && fp.hasHighDegreeHubs) {
                conf = 0.8;
                reason = "Single connected component with hub nodes — perfect candidate for resilience testing. " +
                         "Removing hubs could fragment the network.";
            } else if (fp.componentCount == 1) {
                conf = 0.5;
                reason = "Connected graph — worth checking how robust it is to node/edge removal.";
            } else {
                reason = "Already fragmented into " + fp.componentCount + " components. " +
                         "Resilience analysis will show vulnerability of remaining connections.";
            }
            recommendations.add(new Recommendation(
                    "Resilience Analysis", "Robustness",
                    conf, reason,
                    "Understand how network survives targeted attacks and random failures",
                    fp.nodeCount > 200 ? "intensive" : "moderate"));
        }

        // Motif / Substructure Analysis
        if (fp.nodeCount >= 10 && fp.edgeCount >= 15) {
            double conf = 0.35;
            String reason = "Graph is large enough for meaningful motif analysis. ";
            if (fp.clusteringEstimate > 0.3) {
                conf += 0.25;
                reason += "High clustering suggests recurring local patterns (triangles, stars, cliques).";
            }
            recommendations.add(new Recommendation(
                    "Motif & Substructure Analysis", "Patterns",
                    conf, reason.trim(),
                    "Discover recurring structural patterns that reveal network organizing principles",
                    fp.nodeCount > 100 ? "intensive" : "moderate"));
        }

        // K-Core Decomposition
        if (fp.nodeCount >= 10 && fp.density > 0.01) {
            double conf = 0.45;
            String reason = "K-core decomposition reveals the hierarchical backbone of the network. ";
            if (fp.hasHighDegreeHubs) {
                conf += 0.15;
                reason += "Hub nodes likely form a dense core surrounded by peripheral shells.";
            }
            recommendations.add(new Recommendation(
                    "K-Core Decomposition", "Structure",
                    conf, reason.trim(),
                    "Find the densest core of the network and understand node importance layers",
                    "quick"));
        }

        // Graph Coloring
        if (fp.nodeCount >= 5 && fp.nodeCount <= 500) {
            double conf = 0.3;
            String reason = "Graph coloring reveals conflict-free groupings. ";
            if (fp.clusteringEstimate > 0.4) {
                conf += 0.15;
                reason += "High clustering may indicate a low chromatic number.";
            }
            recommendations.add(new Recommendation(
                    "Graph Coloring", "Combinatorial",
                    conf, reason.trim(),
                    "Find the minimum coloring — useful for scheduling, resource allocation, and conflict detection",
                    fp.nodeCount > 100 ? "intensive" : "moderate"));
        }

        // Temporal Analysis
        if (fp.hasTemporalEdges) {
            recommendations.add(new Recommendation(
                    "Community Evolution Tracking", "Temporal",
                    0.85, "Temporal edge data detected! This enables tracking how communities " +
                          "form, grow, merge, and dissolve over time.",
                    "Watch the network evolve — discover growth patterns, pivotal moments, and stability",
                    "moderate"));

            recommendations.add(new Recommendation(
                    "Growth Rate Analysis", "Temporal",
                    0.75, "Temporal edges enable measuring network growth velocity, " +
                          "acceleration, and predicting future size.",
                    "Forecast network growth and identify periods of rapid change",
                    "quick"));
        }

        // Edge Betweenness
        if (fp.nodeCount >= 5 && fp.componentCount == 1) {
            double conf = 0.5;
            String reason = "Connected graph — edge betweenness identifies critical bridges. ";
            if (fp.edgeCount > fp.nodeCount * 1.5) {
                conf += 0.15;
                reason += "Enough redundant paths to make bridge detection meaningful.";
            }
            recommendations.add(new Recommendation(
                    "Edge Betweenness Analysis", "Influence",
                    conf, reason.trim(),
                    "Find the most critical edges whose removal would most disrupt communication",
                    fp.nodeCount > 200 ? "intensive" : "moderate"));
        }

        // Health Check
        if (fp.hasSelfLoops || fp.hasIsolatedNodes || fp.componentCount > fp.nodeCount * 0.1) {
            double conf = 0.7;
            String reason = "Potential data quality issues detected: ";
            List<String> issues = new ArrayList<>();
            if (fp.hasSelfLoops) issues.add("self-loops present");
            if (fp.hasIsolatedNodes) issues.add("isolated nodes found");
            if (fp.componentCount > 3) issues.add(fp.componentCount + " disconnected components");
            reason += String.join(", ", issues) + ".";
            recommendations.add(new Recommendation(
                    "Graph Health Check", "Quality",
                    conf, reason,
                    "Diagnose data quality issues and get actionable cleanup suggestions",
                    "quick"));
        }

        // Small World Analysis
        if (fp.nodeCount >= 20 && fp.clusteringEstimate > 0.2 && fp.componentCount == 1) {
            recommendations.add(new Recommendation(
                    "Small World Analysis", "Topology",
                    0.55, "High clustering (" + String.format("%.2f", fp.clusteringEstimate) +
                          ") in a connected graph — classic precondition for small-world topology. " +
                          "If path lengths are short, this is a small-world network.",
                    "Determine if the network has small-world properties (high clustering + short paths)",
                    "moderate"));
        }

        // Spectral Analysis
        if (fp.nodeCount >= 10 && fp.nodeCount <= 2000) {
            double conf = 0.35;
            String reason = "Spectral analysis reveals deep structural properties invisible to simpler methods. ";
            if (fp.componentCount > 1) {
                conf += 0.15;
                reason += "Multiple components will appear as zero eigenvalues — useful for validation.";
            }
            recommendations.add(new Recommendation(
                    "Spectral Analysis", "Advanced",
                    conf, reason.trim(),
                    "Uncover hidden structure through eigenvalue analysis of the graph's Laplacian",
                    fp.nodeCount > 500 ? "intensive" : "moderate"));
        }

        // Influence Spread Simulation
        if (fp.nodeCount >= 10 && fp.componentCount == 1) {
            double conf = 0.4;
            String reason = "Connected network suitable for epidemic/influence modeling. ";
            if (fp.hasHighDegreeHubs) {
                conf += 0.2;
                reason += "Hub nodes could serve as superspreaders — seeding from hubs vs random nodes " +
                          "will show dramatically different spread patterns.";
            }
            recommendations.add(new Recommendation(
                    "Influence Spread Simulation", "Dynamics",
                    conf, reason.trim(),
                    "Simulate information/disease spread to find optimal seed nodes and vulnerable paths",
                    "moderate"));
        }
    }

    // ── Text report ──────────────────────────────────────────────────

    /** Format a plain-text report of the advisor's findings. */
    public String formatTextReport() {
        if (fingerprint == null) throw new IllegalStateException("Call analyze() first");
        StringBuilder sb = new StringBuilder();
        sb.append("===========================================\n");
        sb.append("  GRAPH INTELLIGENCE ADVISOR REPORT\n");
        sb.append("===========================================\n\n");

        // Fingerprint summary
        sb.append("-- Graph Fingerprint --\n");
        sb.append(String.format("  Nodes: %,d    Edges: %,d    Density: %.4f\n",
                fingerprint.nodeCount, fingerprint.edgeCount, fingerprint.density));
        sb.append(String.format("  Avg Degree: %.1f    Max: %d    Min: %d\n",
                fingerprint.avgDegree, fingerprint.maxDegree, fingerprint.minDegree));
        sb.append(String.format("  Components: %d    Largest: %,d nodes\n",
                fingerprint.componentCount, fingerprint.largestComponentSize));
        sb.append(String.format("  Clustering Coeff (est): %.3f\n", fingerprint.clusteringEstimate));
        sb.append(String.format("  Edge Types: %d    Weighted: %s    Temporal: %s\n\n",
                fingerprint.edgeTypeCount, fingerprint.hasWeightedEdges ? "yes" : "no",
                fingerprint.hasTemporalEdges ? "yes" : "no"));

        // Recommendations
        sb.append("-- Recommended Analyses --\n\n");
        int rank = 1;
        for (Recommendation r : recommendations) {
            sb.append(String.format("#%d  %s  [%s]  (confidence: %.0f%%,  %s)\n",
                    rank++, r.getAnalysisName(), r.getCategory(),
                    r.getConfidence() * 100, r.getDifficulty()));
            sb.append("    WHY: ").append(r.getReasoning()).append("\n");
            sb.append("    INSIGHT: ").append(r.getExpectedInsight()).append("\n\n");
        }

        if (recommendations.isEmpty()) {
            sb.append("  No specific recommendations — graph may be too small for meaningful analysis.\n");
        }

        return sb.toString();
    }

    // ── HTML export ──────────────────────────────────────────────────

    /** Export an interactive HTML report. */
    public void exportHtml(File outputFile) throws IOException {
        if (fingerprint == null) throw new IllegalStateException("Call analyze() first");
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("<title>Graph Intelligence Advisor Report</title>\n");
        html.append("<style>\n");
        html.append(getCSS());
        html.append("</style>\n</head>\n<body>\n");

        // Header
        html.append("<div class=\"header\">\n");
        html.append("  <h1>&#129504; Graph Intelligence Advisor</h1>\n");
        html.append("  <p class=\"subtitle\">Proactive analysis recommendations based on structural fingerprinting</p>\n");
        html.append("  <p class=\"meta\">Generated: ").append(escapeHtml(timestamp)).append(" &bull; ");
        html.append(fingerprint.nodeCount).append(" nodes &bull; ");
        html.append(fingerprint.edgeCount).append(" edges</p>\n");
        html.append("</div>\n\n");

        // Fingerprint card
        html.append("<div class=\"section\">\n");
        html.append("  <h2>&#128269; Structural Fingerprint</h2>\n");
        html.append("  <div class=\"fingerprint-grid\">\n");
        appendMetricCard(html, "Nodes", String.format("%,d", fingerprint.nodeCount), "graph-size");
        appendMetricCard(html, "Edges", String.format("%,d", fingerprint.edgeCount), "graph-size");
        appendMetricCard(html, "Density", String.format("%.4f", fingerprint.density),
                fingerprint.density > 0.5 ? "high" : fingerprint.density < 0.01 ? "low" : "medium");
        appendMetricCard(html, "Avg Degree", String.format("%.1f", fingerprint.avgDegree), "medium");
        appendMetricCard(html, "Max Degree", String.valueOf(fingerprint.maxDegree),
                fingerprint.hasHighDegreeHubs ? "high" : "medium");
        appendMetricCard(html, "Components", String.valueOf(fingerprint.componentCount),
                fingerprint.componentCount == 1 ? "good" : "warning");
        appendMetricCard(html, "Clustering", String.format("%.3f", fingerprint.clusteringEstimate),
                fingerprint.clusteringEstimate > 0.3 ? "high" : "low");
        appendMetricCard(html, "Edge Types", String.valueOf(fingerprint.edgeTypeCount), "medium");
        html.append("  </div>\n");

        // Structural tags
        html.append("  <div class=\"tags\">\n");
        if (fingerprint.hasHighDegreeHubs) html.append("    <span class=\"tag hub\">Hub Nodes</span>\n");
        if (fingerprint.hasIsolatedNodes) html.append("    <span class=\"tag isolated\">Isolated Nodes</span>\n");
        if (fingerprint.hasSelfLoops) html.append("    <span class=\"tag selfloop\">Self-Loops</span>\n");
        if (fingerprint.hasWeightedEdges) html.append("    <span class=\"tag weighted\">Weighted</span>\n");
        if (fingerprint.hasTemporalEdges) html.append("    <span class=\"tag temporal\">Temporal</span>\n");
        if (fingerprint.componentCount == 1) html.append("    <span class=\"tag connected\">Connected</span>\n");
        if (fingerprint.clusteringEstimate > 0.3) html.append("    <span class=\"tag clustered\">Clustered</span>\n");
        html.append("  </div>\n");
        html.append("</div>\n\n");

        // Edge type breakdown
        if (!fingerprint.edgeTypeCounts.isEmpty()) {
            html.append("<div class=\"section\">\n");
            html.append("  <h2>&#128279; Edge Type Breakdown</h2>\n");
            html.append("  <div class=\"edge-type-bars\">\n");
            int maxCount = fingerprint.edgeTypeCounts.values().stream()
                    .mapToInt(Integer::intValue).max().orElse(1);
            String[] barColors = {"#4f46e5", "#0891b2", "#059669", "#d97706", "#dc2626", "#7c3aed"};
            int ci = 0;
            for (Map.Entry<String, Integer> entry : fingerprint.edgeTypeCounts.entrySet()) {
                double pct = 100.0 * entry.getValue() / maxCount;
                String color = barColors[ci % barColors.length];
                html.append("    <div class=\"bar-row\">\n");
                html.append("      <span class=\"bar-label\">").append(escapeHtml(entry.getKey())).append("</span>\n");
                html.append("      <div class=\"bar-track\"><div class=\"bar-fill\" style=\"width:")
                        .append(String.format("%.1f", pct)).append("%;background:").append(color).append("\"></div></div>\n");
                html.append("      <span class=\"bar-count\">").append(entry.getValue()).append("</span>\n");
                html.append("    </div>\n");
                ci++;
            }
            html.append("  </div>\n</div>\n\n");
        }

        // Recommendations
        html.append("<div class=\"section\">\n");
        html.append("  <h2>&#127919; Recommended Analyses (").append(recommendations.size()).append(")</h2>\n");
        if (recommendations.isEmpty()) {
            html.append("  <p class=\"empty\">No recommendations — graph may be too small.</p>\n");
        } else {
            int rank = 1;
            for (Recommendation r : recommendations) {
                String confClass = r.getConfidence() >= 0.7 ? "high" :
                        r.getConfidence() >= 0.4 ? "medium" : "low";
                html.append("  <div class=\"rec-card\">\n");
                html.append("    <div class=\"rec-header\">\n");
                html.append("      <span class=\"rec-rank\">#").append(rank++).append("</span>\n");
                html.append("      <span class=\"rec-name\">").append(escapeHtml(r.getAnalysisName())).append("</span>\n");
                html.append("      <span class=\"rec-category\">").append(escapeHtml(r.getCategory())).append("</span>\n");
                html.append("      <span class=\"rec-conf ").append(confClass).append("\">")
                        .append(String.format("%.0f%%", r.getConfidence() * 100)).append("</span>\n");
                html.append("      <span class=\"rec-diff diff-").append(r.getDifficulty()).append("\">")
                        .append(escapeHtml(r.getDifficulty())).append("</span>\n");
                html.append("    </div>\n");
                html.append("    <div class=\"rec-body\">\n");
                html.append("      <p class=\"rec-reason\"><strong>Why:</strong> ")
                        .append(escapeHtml(r.getReasoning())).append("</p>\n");
                html.append("      <p class=\"rec-insight\"><strong>Expected insight:</strong> ")
                        .append(escapeHtml(r.getExpectedInsight())).append("</p>\n");
                html.append("    </div>\n");
                html.append("  </div>\n");
            }
        }
        html.append("</div>\n\n");

        // Footer
        html.append("<div class=\"footer\">Generated by GraphVisual Intelligence Advisor</div>\n");
        html.append("</body>\n</html>\n");

        try (Writer w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
            w.write(html.toString());
        }
    }

    private void appendMetricCard(StringBuilder html, String label, String value, String level) {
        html.append("    <div class=\"metric-card ").append(level).append("\">\n");
        html.append("      <div class=\"metric-value\">").append(escapeHtml(value)).append("</div>\n");
        html.append("      <div class=\"metric-label\">").append(escapeHtml(label)).append("</div>\n");
        html.append("    </div>\n");
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String getCSS() {
        return "* { margin: 0; padding: 0; box-sizing: border-box; }\n"
             + "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;\n"
             + "  background: #0f172a; color: #e2e8f0; padding: 2rem; line-height: 1.6; }\n"
             + ".header { text-align: center; margin-bottom: 2rem; }\n"
             + ".header h1 { font-size: 2rem; color: #f8fafc; margin-bottom: 0.25rem; }\n"
             + ".header .subtitle { color: #94a3b8; font-size: 1.1rem; }\n"
             + ".header .meta { color: #64748b; font-size: 0.85rem; margin-top: 0.5rem; }\n"
             + ".section { background: #1e293b; border-radius: 12px; padding: 1.5rem; margin-bottom: 1.5rem; }\n"
             + ".section h2 { color: #f1f5f9; font-size: 1.3rem; margin-bottom: 1rem; }\n"
             + ".fingerprint-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));\n"
             + "  gap: 0.75rem; }\n"
             + ".metric-card { background: #334155; border-radius: 8px; padding: 1rem; text-align: center; }\n"
             + ".metric-value { font-size: 1.5rem; font-weight: 700; color: #f8fafc; }\n"
             + ".metric-label { font-size: 0.8rem; color: #94a3b8; margin-top: 0.25rem; }\n"
             + ".metric-card.high .metric-value { color: #f59e0b; }\n"
             + ".metric-card.low .metric-value { color: #60a5fa; }\n"
             + ".metric-card.warning .metric-value { color: #ef4444; }\n"
             + ".metric-card.good .metric-value { color: #34d399; }\n"
             + ".tags { display: flex; flex-wrap: wrap; gap: 0.5rem; margin-top: 1rem; }\n"
             + ".tag { padding: 0.3rem 0.8rem; border-radius: 999px; font-size: 0.75rem; font-weight: 600; }\n"
             + ".tag.hub { background: #7c3aed33; color: #a78bfa; border: 1px solid #7c3aed55; }\n"
             + ".tag.isolated { background: #ef444433; color: #fca5a5; border: 1px solid #ef444455; }\n"
             + ".tag.selfloop { background: #d9770633; color: #fbbf24; border: 1px solid #d9770655; }\n"
             + ".tag.weighted { background: #0891b233; color: #67e8f9; border: 1px solid #0891b255; }\n"
             + ".tag.temporal { background: #059669; color: #a7f3d0; border: 1px solid #05966955; }\n"
             + ".tag.connected { background: #34d39933; color: #6ee7b7; border: 1px solid #34d39955; }\n"
             + ".tag.clustered { background: #4f46e533; color: #a5b4fc; border: 1px solid #4f46e555; }\n"
             + ".edge-type-bars { display: flex; flex-direction: column; gap: 0.5rem; }\n"
             + ".bar-row { display: flex; align-items: center; gap: 0.75rem; }\n"
             + ".bar-label { width: 80px; text-align: right; font-size: 0.85rem; color: #94a3b8; }\n"
             + ".bar-track { flex: 1; height: 24px; background: #334155; border-radius: 6px; overflow: hidden; }\n"
             + ".bar-fill { height: 100%; border-radius: 6px; transition: width 0.5s; }\n"
             + ".bar-count { width: 50px; text-align: right; font-size: 0.85rem; color: #e2e8f0; }\n"
             + ".rec-card { background: #334155; border-radius: 10px; padding: 1rem; margin-bottom: 0.75rem;\n"
             + "  border-left: 4px solid #4f46e5; }\n"
             + ".rec-header { display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap; margin-bottom: 0.5rem; }\n"
             + ".rec-rank { font-size: 1.1rem; font-weight: 700; color: #818cf8; min-width: 2rem; }\n"
             + ".rec-name { font-size: 1rem; font-weight: 600; color: #f1f5f9; }\n"
             + ".rec-category { padding: 0.15rem 0.6rem; border-radius: 999px; font-size: 0.7rem;\n"
             + "  background: #475569; color: #cbd5e1; }\n"
             + ".rec-conf { padding: 0.15rem 0.6rem; border-radius: 999px; font-size: 0.75rem; font-weight: 700; }\n"
             + ".rec-conf.high { background: #16a34a33; color: #4ade80; }\n"
             + ".rec-conf.medium { background: #d97706; color: #fbbf24; }\n"
             + ".rec-conf.low { background: #64748b33; color: #94a3b8; }\n"
             + ".rec-diff { padding: 0.15rem 0.6rem; border-radius: 999px; font-size: 0.7rem; }\n"
             + ".diff-quick { background: #34d39933; color: #6ee7b7; }\n"
             + ".diff-moderate { background: #d9770633; color: #fbbf24; }\n"
             + ".diff-intensive { background: #ef444433; color: #fca5a5; }\n"
             + ".rec-body { padding-left: 2.75rem; }\n"
             + ".rec-reason { font-size: 0.85rem; color: #cbd5e1; margin-bottom: 0.3rem; }\n"
             + ".rec-insight { font-size: 0.85rem; color: #94a3b8; font-style: italic; }\n"
             + ".empty { color: #64748b; font-style: italic; }\n"
             + ".footer { text-align: center; color: #475569; font-size: 0.8rem; padding: 2rem 0 1rem; }\n";
    }
}
