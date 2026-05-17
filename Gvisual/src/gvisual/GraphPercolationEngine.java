package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GraphPercolationEngine — autonomous percolation analysis engine that simulates
 * bond and site percolation processes, estimates percolation thresholds, tracks
 * giant component evolution, detects phase transitions, forecasts fragmentation,
 * and generates interactive HTML dashboards.
 *
 * <h3>Seven Analysis Engines:</h3>
 * <ol>
 *   <li><b>Bond Percolation Simulator</b> — randomly removes edges with
 *       probability (1-p) for each occupation probability p in [0,1], tracks
 *       largest connected component size</li>
 *   <li><b>Site Percolation Simulator</b> — randomly removes vertices (and
 *       their edges) with probability (1-p), tracks largest connected component</li>
 *   <li><b>Percolation Threshold Estimator</b> — runs Monte Carlo trials at
 *       varying p values, finds critical probability p_c where giant component
 *       emerges via steepest-slope detection</li>
 *   <li><b>Giant Component Tracker</b> — tracks fraction of nodes in largest
 *       connected component as a function of p, detects emergence point</li>
 *   <li><b>Phase Transition Detector</b> — identifies critical regime by
 *       analyzing the derivative of the giant component curve, classifies
 *       transition sharpness (Sharp/Gradual/None)</li>
 *   <li><b>Fragmentation Forecaster</b> — predicts how many random failures
 *       needed to fragment the network (autonomous agentic capability)</li>
 *   <li><b>Interactive HTML Dashboard</b> — self-contained HTML with charts
 *       for percolation curves, threshold markers, phase transition
 *       visualization, and autonomous insights</li>
 * </ol>
 *
 * <p>Usage:</p>
 * <pre>
 *   GraphPercolationEngine engine = new GraphPercolationEngine();
 *   PercolationReport report = engine.analyze(graph);
 *   System.out.println(engine.toText(report));
 *   String html = engine.exportHtml(report);
 * </pre>
 *
 * @author sauravbhattacharya001
 */
public class GraphPercolationEngine {

    // -- Configuration --------------------------------------------------------
    private int monteCarloTrials = 50;
    private int probabilitySteps = 20;
    private Random rng = new Random(42);

    // -- Builder-style setters ------------------------------------------------

    public GraphPercolationEngine setMonteCarloTrials(int n) {
        this.monteCarloTrials = n; return this;
    }

    public GraphPercolationEngine setProbabilitySteps(int n) {
        this.probabilitySteps = n; return this;
    }

    public GraphPercolationEngine setRandomSeed(long seed) {
        this.rng = new Random(seed); return this;
    }

    public GraphPercolationEngine setRng(Random rng) {
        this.rng = rng; return this;
    }

    // ==================================================================
    // Inner classes
    // ==================================================================

    /** Percolation type. */
    public enum PercolationType { BOND, SITE }

    /** Phase transition sharpness classification. */
    public enum TransitionSharpness { SHARP, GRADUAL, NONE }

    /** Full percolation analysis report. */
    public static class PercolationReport {
        public final Map<Double, Double> bondPercolationCurve;
        public final Map<Double, Double> sitePercolationCurve;
        public final double bondThreshold;
        public final double siteThreshold;
        public final TransitionSharpness phaseTransitionSharpness;
        public final double fragmentationTolerance;
        public final int fragmentationForecast;
        public final double healthScore;
        public final List<String> insights;
        public final int nodeCount;
        public final int edgeCount;

        public PercolationReport(Map<Double, Double> bondPercolationCurve,
                                 Map<Double, Double> sitePercolationCurve,
                                 double bondThreshold,
                                 double siteThreshold,
                                 TransitionSharpness phaseTransitionSharpness,
                                 double fragmentationTolerance,
                                 int fragmentationForecast,
                                 double healthScore,
                                 List<String> insights,
                                 int nodeCount, int edgeCount) {
            this.bondPercolationCurve = Collections.unmodifiableMap(new LinkedHashMap<>(bondPercolationCurve));
            this.sitePercolationCurve = Collections.unmodifiableMap(new LinkedHashMap<>(sitePercolationCurve));
            this.bondThreshold = bondThreshold;
            this.siteThreshold = siteThreshold;
            this.phaseTransitionSharpness = phaseTransitionSharpness;
            this.fragmentationTolerance = fragmentationTolerance;
            this.fragmentationForecast = fragmentationForecast;
            this.healthScore = healthScore;
            this.insights = Collections.unmodifiableList(new ArrayList<>(insights));
            this.nodeCount = nodeCount;
            this.edgeCount = edgeCount;
        }
    }

    // ==================================================================
    // Main analysis
    // ==================================================================

    /**
     * Run full percolation analysis on the given graph.
     *
     * @param graph the network to analyze
     * @return comprehensive percolation report
     */
    public PercolationReport analyze(Graph<String, Edge> graph) {
        List<String> vertices = new ArrayList<>(graph.getVertices());
        int n = vertices.size();
        int e = graph.getEdgeCount();

        if (n == 0) {
            return new PercolationReport(
                    Collections.emptyMap(), Collections.emptyMap(),
                    0.0, 0.0, TransitionSharpness.NONE, 0.0, 0,
                    0.0, Collections.singletonList("Empty graph — no percolation analysis possible."),
                    0, 0);
        }

        if (n == 1) {
            Map<Double, Double> flat = new LinkedHashMap<>();
            for (int s = 0; s <= probabilitySteps; s++) {
                double p = (double) s / probabilitySteps;
                flat.put(round(p), 1.0);
            }
            return new PercolationReport(
                    flat, flat, 0.0, 0.0, TransitionSharpness.NONE,
                    1.0, 1, 100.0,
                    Collections.singletonList("Single-node graph — trivially percolating at all thresholds."),
                    1, 0);
        }

        // -- Build edge list --------------------------------------------------
        List<Edge> edgeList = new ArrayList<>(graph.getEdges());
        Map<Edge, String[]> edgeEndpoints = new LinkedHashMap<>();
        for (Edge edge : edgeList) {
            String v1 = graph.getEndpoints(edge).getFirst();
            String v2 = graph.getEndpoints(edge).getSecond();
            edgeEndpoints.put(edge, new String[]{v1, v2});
        }

        // -- Bond percolation curve -------------------------------------------
        Map<Double, Double> bondCurve = new LinkedHashMap<>();
        for (int s = 0; s <= probabilitySteps; s++) {
            double p = (double) s / probabilitySteps;
            double avgFraction = 0.0;
            for (int t = 0; t < monteCarloTrials; t++) {
                avgFraction += simulateBondPercolation(vertices, edgeList, edgeEndpoints, p);
            }
            bondCurve.put(round(p), round(avgFraction / monteCarloTrials));
        }

        // -- Site percolation curve -------------------------------------------
        Map<Double, Double> siteCurve = new LinkedHashMap<>();
        for (int s = 0; s <= probabilitySteps; s++) {
            double p = (double) s / probabilitySteps;
            double avgFraction = 0.0;
            for (int t = 0; t < monteCarloTrials; t++) {
                avgFraction += simulateSitePercolation(vertices, edgeList, edgeEndpoints, p);
            }
            siteCurve.put(round(p), round(avgFraction / monteCarloTrials));
        }

        // -- Threshold estimation (steepest slope) ----------------------------
        double bondThreshold = estimateThreshold(bondCurve);
        double siteThreshold = estimateThreshold(siteCurve);

        // -- Phase transition detection ---------------------------------------
        TransitionSharpness sharpness = detectPhaseTransition(bondCurve);

        // -- Fragmentation forecast -------------------------------------------
        double fragTolerance = computeFragmentationTolerance(vertices, edgeList, edgeEndpoints);
        int fragForecast = (int) Math.ceil(fragTolerance * n);

        // -- Health score (higher threshold = more robust) --------------------
        double avgThreshold = (bondThreshold + siteThreshold) / 2.0;
        // Invert: lower threshold means network is more robust (percolates easier)
        // Actually: higher threshold means you need more edges/nodes to percolate,
        // meaning network is harder to fragment. So healthScore ~ threshold * 100
        // But for robustness: low threshold = percolation at low p = robust.
        // Let's define: health = (1 - avgThreshold) * fragTolerance * 100, capped at 100
        double healthRaw = (1.0 - avgThreshold * 0.5) * fragTolerance * 100.0;
        double healthScore = Math.min(100.0, Math.max(0.0, round(healthRaw)));

        // -- Insights ---------------------------------------------------------
        List<String> insights = generateInsights(bondThreshold, siteThreshold,
                sharpness, fragTolerance, fragForecast, n, e, bondCurve, siteCurve);

        return new PercolationReport(bondCurve, siteCurve, bondThreshold, siteThreshold,
                sharpness, round(fragTolerance), fragForecast, healthScore, insights, n, e);
    }

    // ==================================================================
    // Engine 1: Bond percolation simulation
    // ==================================================================

    private double simulateBondPercolation(List<String> vertices,
                                           List<Edge> edges,
                                           Map<Edge, String[]> endpoints,
                                           double p) {
        // Keep each edge with probability p
        Set<String> activeVertices = new LinkedHashSet<>(vertices);
        List<String[]> activeEdges = new ArrayList<>();
        for (Edge edge : edges) {
            if (rng.nextDouble() < p) {
                activeEdges.add(endpoints.get(edge));
            }
        }
        return giantComponentFraction(activeVertices, activeEdges);
    }

    // ==================================================================
    // Engine 2: Site percolation simulation
    // ==================================================================

    private double simulateSitePercolation(List<String> vertices,
                                           List<Edge> edges,
                                           Map<Edge, String[]> endpoints,
                                           double p) {
        // Keep each vertex with probability p
        Set<String> activeVertices = new LinkedHashSet<>();
        for (String v : vertices) {
            if (rng.nextDouble() < p) {
                activeVertices.add(v);
            }
        }
        if (activeVertices.isEmpty()) return 0.0;

        List<String[]> activeEdges = new ArrayList<>();
        for (Edge edge : edges) {
            String[] ep = endpoints.get(edge);
            if (activeVertices.contains(ep[0]) && activeVertices.contains(ep[1])) {
                activeEdges.add(ep);
            }
        }
        return giantComponentFraction(activeVertices, activeEdges);
    }

    // ==================================================================
    // Engine 3: Threshold estimation
    // ==================================================================

    private double estimateThreshold(Map<Double, Double> curve) {
        List<Map.Entry<Double, Double>> entries = new ArrayList<>(curve.entrySet());
        if (entries.size() < 2) return 0.5;

        double maxSlope = 0.0;
        double thresholdP = 0.5;

        for (int i = 1; i < entries.size(); i++) {
            double dp = entries.get(i).getKey() - entries.get(i - 1).getKey();
            if (dp <= 0) continue;
            double slope = (entries.get(i).getValue() - entries.get(i - 1).getValue()) / dp;
            if (slope > maxSlope) {
                maxSlope = slope;
                thresholdP = (entries.get(i).getKey() + entries.get(i - 1).getKey()) / 2.0;
            }
        }
        return round(thresholdP);
    }

    // ==================================================================
    // Engine 4: Giant component tracker (used in simulation)
    // ==================================================================

    private double giantComponentFraction(Set<String> vertices, List<String[]> edges) {
        if (vertices.isEmpty()) return 0.0;

        // Build adjacency list
        Map<String, List<String>> adj = new HashMap<>();
        for (String v : vertices) {
            adj.put(v, new ArrayList<>());
        }
        for (String[] ep : edges) {
            if (adj.containsKey(ep[0]) && adj.containsKey(ep[1])) {
                adj.get(ep[0]).add(ep[1]);
                adj.get(ep[1]).add(ep[0]);
            }
        }

        // BFS to find largest component
        Set<String> visited = new HashSet<>();
        int largest = 0;
        for (String v : vertices) {
            if (visited.contains(v)) continue;
            int size = bfsComponentSize(v, adj, visited);
            if (size > largest) largest = size;
        }
        return (double) largest / vertices.size();
    }

    private int bfsComponentSize(String start, Map<String, List<String>> adj,
                                 Set<String> visited) {
        Queue<String> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start);
        int size = 0;
        while (!queue.isEmpty()) {
            String v = queue.poll();
            size++;
            for (String neighbor : adj.getOrDefault(v, Collections.emptyList())) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        return size;
    }

    // ==================================================================
    // Engine 5: Phase transition detection
    // ==================================================================

    private TransitionSharpness detectPhaseTransition(Map<Double, Double> curve) {
        List<Map.Entry<Double, Double>> entries = new ArrayList<>(curve.entrySet());
        if (entries.size() < 3) return TransitionSharpness.NONE;

        // Compute first derivatives (slopes)
        List<Double> slopes = new ArrayList<>();
        for (int i = 1; i < entries.size(); i++) {
            double dp = entries.get(i).getKey() - entries.get(i - 1).getKey();
            if (dp <= 0) {
                slopes.add(0.0);
                continue;
            }
            slopes.add((entries.get(i).getValue() - entries.get(i - 1).getValue()) / dp);
        }

        double maxSlope = slopes.stream().mapToDouble(Double::doubleValue).max().orElse(0);

        // Count how many slope segments are > 50% of max
        long steepCount = slopes.stream().filter(s -> s > maxSlope * 0.5).count();
        double steepFraction = (double) steepCount / slopes.size();

        if (maxSlope < 0.3) return TransitionSharpness.NONE;
        if (steepFraction < 0.25) return TransitionSharpness.SHARP;
        return TransitionSharpness.GRADUAL;
    }

    // ==================================================================
    // Engine 6: Fragmentation forecaster
    // ==================================================================

    private double computeFragmentationTolerance(List<String> vertices,
                                                  List<Edge> edges,
                                                  Map<Edge, String[]> endpoints) {
        int n = vertices.size();
        if (n <= 1) return 1.0;

        double totalFraction = 0.0;
        int trials = Math.min(monteCarloTrials, 30);
        for (int t = 0; t < trials; t++) {
            List<String> shuffled = new ArrayList<>(vertices);
            Collections.shuffle(shuffled, rng);

            Set<String> active = new LinkedHashSet<>(vertices);
            int removals = 0;

            for (String victim : shuffled) {
                active.remove(victim);
                removals++;

                if (active.isEmpty()) break;

                // Check if giant component < 50% of remaining
                List<String[]> activeEdges = new ArrayList<>();
                for (Edge edge : edges) {
                    String[] ep = endpoints.get(edge);
                    if (active.contains(ep[0]) && active.contains(ep[1])) {
                        activeEdges.add(ep);
                    }
                }
                double gcf = giantComponentFraction(active, activeEdges);
                if (gcf < 0.5) {
                    break;
                }
            }
            totalFraction += (double) removals / n;
        }
        return totalFraction / trials;
    }

    // ==================================================================
    // Insight generation
    // ==================================================================

    private List<String> generateInsights(double bondT, double siteT,
                                          TransitionSharpness sharpness,
                                          double fragTol, int fragForecast,
                                          int n, int e,
                                          Map<Double, Double> bondCurve,
                                          Map<Double, Double> siteCurve) {
        List<String> insights = new ArrayList<>();

        // Robustness insight
        double avgT = (bondT + siteT) / 2.0;
        if (avgT < 0.3) {
            insights.add("Network is highly robust: percolation persists even at low occupation probabilities (avg threshold = "
                    + String.format("%.2f", avgT) + ").");
        } else if (avgT > 0.7) {
            insights.add("Network is fragile: percolation requires high occupation probabilities (avg threshold = "
                    + String.format("%.2f", avgT) + "). Consider adding redundant connections.");
        } else {
            insights.add("Network has moderate percolation robustness (avg threshold = "
                    + String.format("%.2f", avgT) + ").");
        }

        // Bond vs site comparison
        double diff = Math.abs(bondT - siteT);
        if (diff > 0.15) {
            if (bondT < siteT) {
                insights.add("Bond percolation threshold (" + String.format("%.2f", bondT)
                        + ") is significantly lower than site threshold ("
                        + String.format("%.2f", siteT)
                        + ") — the network is more resilient to edge failures than node failures.");
            } else {
                insights.add("Site percolation threshold (" + String.format("%.2f", siteT)
                        + ") is lower than bond threshold ("
                        + String.format("%.2f", bondT)
                        + ") — the network tolerates node removals better than edge removals.");
            }
        }

        // Phase transition
        if (sharpness == TransitionSharpness.SHARP) {
            insights.add("Phase transition is SHARP — the network transitions abruptly from connected to fragmented, "
                    + "characteristic of highly structured topologies.");
        } else if (sharpness == TransitionSharpness.GRADUAL) {
            insights.add("Phase transition is GRADUAL — the network degrades progressively, "
                    + "suggesting heterogeneous connectivity with no single critical point.");
        } else {
            insights.add("No clear phase transition detected — the giant component fraction "
                    + "changes smoothly without a distinct critical regime.");
        }

        // Fragmentation forecast
        if (fragForecast <= 1) {
            insights.add("⚠ CRITICAL: Network can be fragmented by removing just "
                    + fragForecast + " node(s). Single points of failure detected.");
        } else if (fragTol < 0.2) {
            insights.add("⚠ WARNING: Network fragments after removing only "
                    + String.format("%.0f%%", fragTol * 100) + " of nodes ("
                    + fragForecast + " nodes). High fragmentation risk.");
        } else if (fragTol > 0.5) {
            insights.add("Network is resilient to random failures — tolerates removal of "
                    + String.format("%.0f%%", fragTol * 100) + " of nodes ("
                    + fragForecast + " nodes) before fragmenting.");
        }

        // Density insight
        double maxEdges = (double) n * (n - 1) / 2;
        double density = maxEdges > 0 ? e / maxEdges : 0;
        if (density > 0.5) {
            insights.add("High graph density (" + String.format("%.1f%%", density * 100)
                    + ") contributes to percolation robustness through path redundancy.");
        } else if (density < 0.1 && n > 3) {
            insights.add("Low graph density (" + String.format("%.1f%%", density * 100)
                    + ") makes the network vulnerable to percolation failure.");
        }

        // Autonomous recommendation
        if (avgT > 0.5 && fragTol < 0.3) {
            insights.add("RECOMMENDATION: Add cross-cutting edges between peripheral nodes "
                    + "to lower the percolation threshold and improve fragmentation tolerance.");
        }

        if (insights.isEmpty()) {
            insights.add("Percolation analysis complete for " + n + " nodes and " + e + " edges.");
        }

        return insights;
    }

    // ==================================================================
    // Text output
    // ==================================================================

    /**
     * Format the percolation report as human-readable text.
     *
     * @param report the percolation report
     * @return formatted text
     */
    public String toText(PercolationReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════════\n");
        sb.append("  GRAPH PERCOLATION ENGINE — Analysis Report\n");
        sb.append("═══════════════════════════════════════════════════════\n\n");

        sb.append(String.format("  Nodes: %d    Edges: %d\n", report.nodeCount, report.edgeCount));
        sb.append(String.format("  Health Score: %.1f / 100\n\n", report.healthScore));

        sb.append("── Bond Percolation Threshold ──────────────────────\n");
        sb.append(String.format("  p_c (bond) = %.4f\n\n", report.bondThreshold));

        sb.append("── Site Percolation Threshold ──────────────────────\n");
        sb.append(String.format("  p_c (site) = %.4f\n\n", report.siteThreshold));

        sb.append("── Phase Transition ────────────────────────────────\n");
        sb.append(String.format("  Sharpness: %s\n\n", report.phaseTransitionSharpness));

        sb.append("── Fragmentation Forecast ──────────────────────────\n");
        sb.append(String.format("  Tolerance: %.2f (%.0f%% of nodes)\n",
                report.fragmentationTolerance, report.fragmentationTolerance * 100));
        sb.append(String.format("  Removals to fragment: %d nodes\n\n", report.fragmentationForecast));

        sb.append("── Bond Percolation Curve ──────────────────────────\n");
        for (Map.Entry<Double, Double> entry : report.bondPercolationCurve.entrySet()) {
            sb.append(String.format("  p=%.2f  →  GC=%.4f  %s\n",
                    entry.getKey(), entry.getValue(), sparkBar(entry.getValue())));
        }
        sb.append("\n");

        sb.append("── Site Percolation Curve ──────────────────────────\n");
        for (Map.Entry<Double, Double> entry : report.sitePercolationCurve.entrySet()) {
            sb.append(String.format("  p=%.2f  →  GC=%.4f  %s\n",
                    entry.getKey(), entry.getValue(), sparkBar(entry.getValue())));
        }
        sb.append("\n");

        sb.append("── Autonomous Insights ─────────────────────────────\n");
        for (int i = 0; i < report.insights.size(); i++) {
            sb.append(String.format("  %d. %s\n", i + 1, report.insights.get(i)));
        }
        sb.append("\n═══════════════════════════════════════════════════════\n");
        return sb.toString();
    }

    private String sparkBar(double fraction) {
        int bars = (int) (fraction * 20);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bars; i++) sb.append("█");
        for (int i = bars; i < 20; i++) sb.append("░");
        return sb.toString();
    }

    // ==================================================================
    // HTML export
    // ==================================================================

    /**
     * Export the percolation report as a self-contained HTML dashboard.
     *
     * @param report the percolation report
     * @return HTML string
     */
    public String exportHtml(PercolationReport report) {
        StringBuilder html = new StringBuilder();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("<title>Graph Percolation Engine — Dashboard</title>\n");
        html.append("<style>\n");
        html.append("* { margin: 0; padding: 0; box-sizing: border-box; }\n");
        html.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; ");
        html.append("background: #0f0f23; color: #e0e0e0; padding: 20px; }\n");
        html.append("h1 { color: #00d4ff; font-size: 1.8em; margin-bottom: 5px; }\n");
        html.append("h2 { color: #7fdbca; font-size: 1.2em; margin: 20px 0 10px; border-bottom: 1px solid #333; padding-bottom: 5px; }\n");
        html.append(".subtitle { color: #888; font-size: 0.9em; margin-bottom: 20px; }\n");
        html.append(".grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; margin-bottom: 20px; }\n");
        html.append(".card { background: #1a1a2e; border-radius: 8px; padding: 15px; border: 1px solid #333; }\n");
        html.append(".card .label { color: #888; font-size: 0.85em; }\n");
        html.append(".card .value { color: #00d4ff; font-size: 1.6em; font-weight: bold; margin-top: 5px; }\n");
        html.append(".card .value.warn { color: #ff6b6b; }\n");
        html.append(".card .value.ok { color: #51cf66; }\n");
        html.append(".chart-container { background: #1a1a2e; border-radius: 8px; padding: 15px; border: 1px solid #333; margin-bottom: 15px; }\n");
        html.append("canvas { width: 100% !important; height: 300px !important; }\n");
        html.append(".insight { background: #1a1a2e; border-left: 3px solid #00d4ff; padding: 10px 15px; margin: 8px 0; border-radius: 0 6px 6px 0; }\n");
        html.append(".insight.warning { border-left-color: #ff6b6b; }\n");
        html.append(".insight.recommendation { border-left-color: #ffd43b; }\n");
        html.append("table { width: 100%; border-collapse: collapse; margin: 10px 0; }\n");
        html.append("th { background: #16213e; color: #7fdbca; padding: 8px; text-align: left; font-size: 0.85em; }\n");
        html.append("td { padding: 8px; border-bottom: 1px solid #222; font-size: 0.85em; }\n");
        html.append("tr:hover { background: #16213e; }\n");
        html.append(".bar { display: inline-block; height: 12px; background: linear-gradient(90deg, #00d4ff, #7fdbca); border-radius: 3px; }\n");
        html.append("</style>\n</head>\n<body>\n");

        html.append("<h1>⚡ Graph Percolation Engine</h1>\n");
        html.append("<div class=\"subtitle\">Autonomous percolation analysis — ")
                .append(timestamp).append("</div>\n");

        // Summary cards
        html.append("<div class=\"grid\">\n");
        appendCard(html, "Nodes", String.valueOf(report.nodeCount), "");
        appendCard(html, "Edges", String.valueOf(report.edgeCount), "");
        appendCard(html, "Bond Threshold (p_c)", String.format("%.4f", report.bondThreshold), "");
        appendCard(html, "Site Threshold (p_c)", String.format("%.4f", report.siteThreshold), "");
        String healthClass = report.healthScore >= 60 ? "ok" : report.healthScore >= 30 ? "" : "warn";
        appendCard(html, "Health Score", String.format("%.1f", report.healthScore), healthClass);
        appendCard(html, "Phase Transition", report.phaseTransitionSharpness.toString(), "");
        appendCard(html, "Fragmentation Tolerance",
                String.format("%.0f%%", report.fragmentationTolerance * 100), "");
        appendCard(html, "Removals to Fragment", String.valueOf(report.fragmentationForecast), "");
        html.append("</div>\n");

        // Bond percolation curve chart
        html.append("<div class=\"chart-container\">\n");
        html.append("<h2>Bond Percolation Curve</h2>\n");
        html.append("<canvas id=\"bondChart\"></canvas>\n");
        html.append("</div>\n");

        // Site percolation curve chart
        html.append("<div class=\"chart-container\">\n");
        html.append("<h2>Site Percolation Curve</h2>\n");
        html.append("<canvas id=\"siteChart\"></canvas>\n");
        html.append("</div>\n");

        // Bond percolation table
        html.append("<h2>Bond Percolation Data</h2>\n");
        html.append("<table><tr><th>Probability (p)</th><th>Giant Component Fraction</th><th>Visual</th></tr>\n");
        for (Map.Entry<Double, Double> entry : report.bondPercolationCurve.entrySet()) {
            html.append(String.format("<tr><td>%.2f</td><td>%.4f</td><td><div class=\"bar\" style=\"width: %.0f%%\"></div></td></tr>\n",
                    entry.getKey(), entry.getValue(), entry.getValue() * 100));
        }
        html.append("</table>\n");

        // Site percolation table
        html.append("<h2>Site Percolation Data</h2>\n");
        html.append("<table><tr><th>Probability (p)</th><th>Giant Component Fraction</th><th>Visual</th></tr>\n");
        for (Map.Entry<Double, Double> entry : report.sitePercolationCurve.entrySet()) {
            html.append(String.format("<tr><td>%.2f</td><td>%.4f</td><td><div class=\"bar\" style=\"width: %.0f%%\"></div></td></tr>\n",
                    entry.getKey(), entry.getValue(), entry.getValue() * 100));
        }
        html.append("</table>\n");

        // Insights
        html.append("<h2>Autonomous Insights</h2>\n");
        for (String insight : report.insights) {
            String cls = "insight";
            if (insight.contains("⚠") || insight.contains("WARNING") || insight.contains("CRITICAL")) {
                cls = "insight warning";
            } else if (insight.contains("RECOMMENDATION")) {
                cls = "insight recommendation";
            }
            html.append("<div class=\"").append(cls).append("\">").append(escapeHtml(insight)).append("</div>\n");
        }

        // JavaScript for canvas charts
        html.append("<script>\n");
        html.append("function drawChart(canvasId, data, threshold, color) {\n");
        html.append("  var canvas = document.getElementById(canvasId);\n");
        html.append("  var ctx = canvas.getContext('2d');\n");
        html.append("  canvas.width = canvas.offsetWidth * 2; canvas.height = 600;\n");
        html.append("  var w = canvas.width, h = canvas.height;\n");
        html.append("  var pad = {top: 30, right: 30, bottom: 50, left: 60};\n");
        html.append("  var pw = w - pad.left - pad.right, ph = h - pad.top - pad.bottom;\n");
        html.append("  ctx.fillStyle = '#1a1a2e'; ctx.fillRect(0, 0, w, h);\n");
        // Grid
        html.append("  ctx.strokeStyle = '#333'; ctx.lineWidth = 1;\n");
        html.append("  for (var i = 0; i <= 10; i++) {\n");
        html.append("    var y = pad.top + (ph * i / 10);\n");
        html.append("    ctx.beginPath(); ctx.moveTo(pad.left, y); ctx.lineTo(w - pad.right, y); ctx.stroke();\n");
        html.append("  }\n");
        // Threshold line
        html.append("  var tx = pad.left + threshold * pw;\n");
        html.append("  ctx.strokeStyle = '#ff6b6b'; ctx.lineWidth = 2; ctx.setLineDash([5,5]);\n");
        html.append("  ctx.beginPath(); ctx.moveTo(tx, pad.top); ctx.lineTo(tx, h - pad.bottom); ctx.stroke();\n");
        html.append("  ctx.setLineDash([]);\n");
        html.append("  ctx.fillStyle = '#ff6b6b'; ctx.font = '20px sans-serif';\n");
        html.append("  ctx.fillText('p_c=' + threshold.toFixed(4), tx + 5, pad.top + 20);\n");
        // Data line
        html.append("  ctx.strokeStyle = color; ctx.lineWidth = 3; ctx.beginPath();\n");
        html.append("  for (var j = 0; j < data.length; j++) {\n");
        html.append("    var x = pad.left + data[j][0] * pw;\n");
        html.append("    var y = pad.top + (1 - data[j][1]) * ph;\n");
        html.append("    if (j === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);\n");
        html.append("  }\n");
        html.append("  ctx.stroke();\n");
        // Points
        html.append("  ctx.fillStyle = color;\n");
        html.append("  for (var j = 0; j < data.length; j++) {\n");
        html.append("    var x = pad.left + data[j][0] * pw;\n");
        html.append("    var y = pad.top + (1 - data[j][1]) * ph;\n");
        html.append("    ctx.beginPath(); ctx.arc(x, y, 4, 0, 2*Math.PI); ctx.fill();\n");
        html.append("  }\n");
        // Axes labels
        html.append("  ctx.fillStyle = '#888'; ctx.font = '18px sans-serif';\n");
        html.append("  ctx.fillText('Occupation Probability (p)', w/2 - 100, h - 10);\n");
        html.append("  ctx.save(); ctx.translate(15, h/2); ctx.rotate(-Math.PI/2);\n");
        html.append("  ctx.fillText('Giant Component Fraction', -80, 0); ctx.restore();\n");
        // Axis ticks
        html.append("  ctx.fillStyle = '#aaa'; ctx.font = '16px sans-serif';\n");
        html.append("  for (var i = 0; i <= 10; i++) {\n");
        html.append("    ctx.fillText((i/10).toFixed(1), pad.left - 40, pad.top + ph - (ph * i / 10) + 5);\n");
        html.append("    ctx.fillText((i/10).toFixed(1), pad.left + (pw * i / 10) - 10, h - pad.bottom + 25);\n");
        html.append("  }\n");
        html.append("}\n");

        // Data arrays
        html.append("var bondData = [");
        boolean first = true;
        for (Map.Entry<Double, Double> entry : report.bondPercolationCurve.entrySet()) {
            if (!first) html.append(",");
            html.append("[").append(entry.getKey()).append(",").append(entry.getValue()).append("]");
            first = false;
        }
        html.append("];\n");

        html.append("var siteData = [");
        first = true;
        for (Map.Entry<Double, Double> entry : report.sitePercolationCurve.entrySet()) {
            if (!first) html.append(",");
            html.append("[").append(entry.getKey()).append(",").append(entry.getValue()).append("]");
            first = false;
        }
        html.append("];\n");

        html.append(String.format("drawChart('bondChart', bondData, %.4f, '#00d4ff');\n", report.bondThreshold));
        html.append(String.format("drawChart('siteChart', siteData, %.4f, '#7fdbca');\n", report.siteThreshold));
        html.append("</script>\n");

        html.append("</body>\n</html>\n");
        return html.toString();
    }

    /**
     * Export the percolation report as HTML and write to a file.
     *
     * @param report   the percolation report
     * @param filePath path to write the HTML file
     * @throws IOException if writing fails
     */
    public void exportHtml(PercolationReport report, String filePath) throws IOException {
        String html = exportHtml(report);
        try (Writer writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            writer.write(html);
        }
    }

    // -- HTML helpers ---------------------------------------------------------

    private void appendCard(StringBuilder html, String label, String value, String cssClass) {
        html.append("<div class=\"card\"><div class=\"label\">").append(escapeHtml(label))
                .append("</div><div class=\"value");
        if (!cssClass.isEmpty()) html.append(" ").append(cssClass);
        html.append("\">").append(escapeHtml(value)).append("</div></div>\n");
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    // -- Utility --------------------------------------------------------------

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
