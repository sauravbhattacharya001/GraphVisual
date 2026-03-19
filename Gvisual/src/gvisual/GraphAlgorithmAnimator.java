package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.util.*;

/**
 * GraphAlgorithmAnimator — generates step-by-step SVG animation frames
 * showing graph algorithms executing visually.
 *
 * <p>Each frame is a self-contained SVG string showing the graph with nodes
 * and edges colored to reflect the algorithm's current state (visited,
 * frontier, unvisited, current, shortest path, etc.).</p>
 *
 * <p>Supported algorithms:</p>
 * <ul>
 *   <li><b>BFS</b> — breadth-first search wave propagation</li>
 *   <li><b>DFS</b> — depth-first search with backtracking</li>
 *   <li><b>Dijkstra</b> — shortest path discovery with distance labels</li>
 *   <li><b>Kruskal</b> — minimum spanning tree construction</li>
 *   <li><b>PageRank</b> — iterative rank convergence</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 *   Graph&lt;String, edge&gt; g = ...;
 *   GraphAlgorithmAnimator animator = new GraphAlgorithmAnimator(g);
 *   List&lt;AnimationFrame&gt; frames = animator.animateBFS("A");
 *   for (AnimationFrame f : frames) {
 *       System.out.println(f.toSVG(800, 600));
 *   }
 *   // Or get a full HTML player:
 *   String html = animator.toHtmlPlayer(frames, 800, 600);
 * </pre>
 *
 * @author Zalenix (automated builder)
 */
public class GraphAlgorithmAnimator {

    private final Graph<String, Edge> graph;
    private final Map<String, double[]> positions;
    private final int defaultWidth;
    private final int defaultHeight;
    private final int nodeRadius;

    // ── Color palette ─────────────────────────────────────────────

    /** Node/edge in default unvisited state. */
    private static final String COLOR_UNVISITED = "#cbd5e1";
    /** Node currently being processed. */
    private static final String COLOR_CURRENT = "#ef4444";
    /** Node in the frontier (queued / discovered but not yet processed). */
    private static final String COLOR_FRONTIER = "#f59e0b";
    /** Node fully visited / finalized. */
    private static final String COLOR_VISITED = "#22c55e";
    /** Edge on the result path / tree. */
    private static final String COLOR_TREE_EDGE = "#3b82f6";
    /** Edge being examined this step. */
    private static final String COLOR_ACTIVE_EDGE = "#ef4444";
    /** Background. */
    private static final String COLOR_BG = "#1e293b";
    /** Text color. */
    private static final String COLOR_TEXT = "#f8fafc";

    // ── Animation frame ────────────────────────────────────────────

    /**
     * A single frame in an algorithm animation.
     */
    public static class AnimationFrame {
        private final int stepNumber;
        private final String algorithmName;
        private final String description;
        private final Map<String, String> nodeColors;
        private final Map<String, String> edgeColors;
        private final Map<String, String> nodeLabels;
        private final Map<String, String> edgeLabelOverrides;

        public AnimationFrame(int stepNumber, String algorithmName,
                              String description,
                              Map<String, String> nodeColors,
                              Map<String, String> edgeColors,
                              Map<String, String> nodeLabels,
                              Map<String, String> edgeLabelOverrides) {
            this.stepNumber = stepNumber;
            this.algorithmName = algorithmName;
            this.description = description;
            this.nodeColors = new LinkedHashMap<>(nodeColors);
            this.edgeColors = new LinkedHashMap<>(edgeColors);
            this.nodeLabels = new LinkedHashMap<>(nodeLabels);
            this.edgeLabelOverrides = edgeLabelOverrides != null
                    ? new LinkedHashMap<>(edgeLabelOverrides)
                    : new LinkedHashMap<>();
        }

        public int getStepNumber() { return stepNumber; }
        public String getAlgorithmName() { return algorithmName; }
        public String getDescription() { return description; }
        public Map<String, String> getNodeColors() { return Collections.unmodifiableMap(nodeColors); }
        public Map<String, String> getEdgeColors() { return Collections.unmodifiableMap(edgeColors); }
        public Map<String, String> getNodeLabels() { return Collections.unmodifiableMap(nodeLabels); }
    }

    // ── Constructor ────────────────────────────────────────────────

    /**
     * Create an animator with auto-computed layout positions.
     *
     * @param graph the graph to animate
     */
    public GraphAlgorithmAnimator(Graph<String, Edge> graph) {
        this(graph, 800, 600, 18);
    }

    /**
     * Create an animator with custom dimensions.
     *
     * @param graph  the graph to animate
     * @param width  SVG viewport width
     * @param height SVG viewport height
     * @param radius node circle radius
     */
    public GraphAlgorithmAnimator(Graph<String, Edge> graph,
                                   int width, int height, int radius) {
        if (graph == null) throw new IllegalArgumentException("Graph must not be null");
        this.graph = graph;
        this.defaultWidth = width;
        this.defaultHeight = height;
        this.nodeRadius = radius;
        this.positions = computeLayout(graph, width, height, radius);
    }

    /**
     * Create an animator with pre-computed positions.
     *
     * @param graph     the graph
     * @param positions map of vertex to {x, y} positions
     */
    public GraphAlgorithmAnimator(Graph<String, Edge> graph,
                                   Map<String, double[]> positions) {
        this(graph, positions, 800, 600, 18);
    }

    /**
     * Create an animator with pre-computed positions and custom dimensions.
     */
    public GraphAlgorithmAnimator(Graph<String, Edge> graph,
                                   Map<String, double[]> positions,
                                   int width, int height, int radius) {
        if (graph == null) throw new IllegalArgumentException("Graph must not be null");
        if (positions == null) throw new IllegalArgumentException("Positions must not be null");
        this.graph = graph;
        this.positions = new LinkedHashMap<>(positions);
        this.defaultWidth = width;
        this.defaultHeight = height;
        this.nodeRadius = radius;
    }

    // ── BFS animation ──────────────────────────────────────────────

    /**
     * Animate breadth-first search from a source vertex.
     *
     * @param source starting vertex
     * @return list of animation frames
     */
    public List<AnimationFrame> animateBFS(String source) {
        validateVertex(source);
        List<AnimationFrame> frames = new ArrayList<>();

        Map<String, String> nodeState = new LinkedHashMap<>();
        Map<String, String> edgeState = new LinkedHashMap<>();
        Map<String, String> nodeLabels = new LinkedHashMap<>();
        Map<String, Integer> distances = new LinkedHashMap<>();

        for (String v : graph.getVertices()) {
            nodeState.put(v, COLOR_UNVISITED);
            nodeLabels.put(v, v);
        }
        for (Edge e : graph.getEdges()) {
            edgeState.put(edgeKey(e), COLOR_UNVISITED);
        }

        // Initial frame
        nodeState.put(source, COLOR_CURRENT);
        distances.put(source, 0);
        nodeLabels.put(source, source + " (d=0)");
        frames.add(new AnimationFrame(0, "BFS",
                "Start BFS from " + source,
                nodeState, edgeState, nodeLabels, null));

        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new LinkedHashSet<>();
        queue.add(source);
        visited.add(source);
        int step = 1;

        while (!queue.isEmpty()) {
            String current = queue.poll();
            nodeState.put(current, COLOR_CURRENT);

            List<String> neighbors = new ArrayList<>(graph.getNeighbors(current));
            Collections.sort(neighbors);

            for (String neighbor : neighbors) {
                Edge e = findEdge(current, neighbor);
                if (e != null) {
                    edgeState.put(edgeKey(e), COLOR_ACTIVE_EDGE);
                }

                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                    int dist = distances.getOrDefault(current, 0) + 1;
                    distances.put(neighbor, dist);
                    nodeState.put(neighbor, COLOR_FRONTIER);
                    nodeLabels.put(neighbor, neighbor + " (d=" + dist + ")");
                    if (e != null) {
                        edgeState.put(edgeKey(e), COLOR_TREE_EDGE);
                    }
                }
            }

            frames.add(new AnimationFrame(step, "BFS",
                    "Process " + current + " — discovered " +
                            countColor(nodeState, COLOR_FRONTIER) + " frontier nodes",
                    nodeState, edgeState, nodeLabels, null));

            nodeState.put(current, COLOR_VISITED);
            step++;
        }

        // Final frame
        frames.add(new AnimationFrame(step, "BFS",
                "BFS complete — visited " + visited.size() + " / " +
                        graph.getVertexCount() + " vertices",
                nodeState, edgeState, nodeLabels, null));

        return frames;
    }

    // ── DFS animation ──────────────────────────────────────────────

    /**
     * Animate depth-first search from a source vertex.
     *
     * @param source starting vertex
     * @return list of animation frames
     */
    public List<AnimationFrame> animateDFS(String source) {
        validateVertex(source);
        List<AnimationFrame> frames = new ArrayList<>();

        Map<String, String> nodeState = new LinkedHashMap<>();
        Map<String, String> edgeState = new LinkedHashMap<>();
        Map<String, String> nodeLabels = new LinkedHashMap<>();

        for (String v : graph.getVertices()) {
            nodeState.put(v, COLOR_UNVISITED);
            nodeLabels.put(v, v);
        }
        for (Edge e : graph.getEdges()) {
            edgeState.put(edgeKey(e), COLOR_UNVISITED);
        }

        Set<String> visited = new LinkedHashSet<>();
        int[] step = {0};

        // Initial frame
        frames.add(new AnimationFrame(0, "DFS",
                "Start DFS from " + source,
                nodeState, edgeState, nodeLabels, null));

        dfsRecurse(source, visited, nodeState, edgeState, nodeLabels,
                frames, step);

        // Final frame
        frames.add(new AnimationFrame(step[0] + 1, "DFS",
                "DFS complete — visited " + visited.size() + " / " +
                        graph.getVertexCount() + " vertices",
                nodeState, edgeState, nodeLabels, null));

        return frames;
    }

    private void dfsRecurse(String current, Set<String> visited,
                            Map<String, String> nodeState,
                            Map<String, String> edgeState,
                            Map<String, String> nodeLabels,
                            List<AnimationFrame> frames, int[] step) {
        visited.add(current);
        step[0]++;
        nodeState.put(current, COLOR_CURRENT);
        nodeLabels.put(current, current + " [" + step[0] + "]");

        frames.add(new AnimationFrame(step[0], "DFS",
                "Enter " + current + " (discovery #" + step[0] + ")",
                nodeState, edgeState, nodeLabels, null));

        List<String> neighbors = new ArrayList<>(graph.getNeighbors(current));
        Collections.sort(neighbors);

        for (String neighbor : neighbors) {
            if (!visited.contains(neighbor)) {
                Edge e = findEdge(current, neighbor);
                if (e != null) {
                    edgeState.put(edgeKey(e), COLOR_TREE_EDGE);
                }
                dfsRecurse(neighbor, visited, nodeState, edgeState,
                        nodeLabels, frames, step);
            }
        }

        nodeState.put(current, COLOR_VISITED);
        step[0]++;
        frames.add(new AnimationFrame(step[0], "DFS",
                "Backtrack from " + current,
                nodeState, edgeState, nodeLabels, null));
    }

    // ── Dijkstra animation ─────────────────────────────────────────

    /**
     * Animate Dijkstra's shortest path algorithm from a source.
     *
     * @param source starting vertex
     * @return list of animation frames
     */
    public List<AnimationFrame> animateDijkstra(String source) {
        validateVertex(source);
        List<AnimationFrame> frames = new ArrayList<>();

        Map<String, String> nodeState = new LinkedHashMap<>();
        Map<String, String> edgeState = new LinkedHashMap<>();
        Map<String, String> nodeLabels = new LinkedHashMap<>();
        Map<String, Double> dist = new LinkedHashMap<>();
        Map<String, String> prev = new LinkedHashMap<>();

        for (String v : graph.getVertices()) {
            nodeState.put(v, COLOR_UNVISITED);
            dist.put(v, Double.MAX_VALUE);
            nodeLabels.put(v, v + " (\u221e)");
        }
        for (Edge e : graph.getEdges()) {
            edgeState.put(edgeKey(e), COLOR_UNVISITED);
        }

        dist.put(source, 0.0);
        nodeState.put(source, COLOR_FRONTIER);
        nodeLabels.put(source, source + " (0)");

        frames.add(new AnimationFrame(0, "Dijkstra",
                "Initialize — source = " + source,
                nodeState, edgeState, nodeLabels, null));

        Set<String> finalized = new LinkedHashSet<>();
        int step = 1;

        while (finalized.size() < graph.getVertexCount()) {
            // Pick unfinalized vertex with smallest distance
            String u = null;
            double minDist = Double.MAX_VALUE;
            for (Map.Entry<String, Double> entry : dist.entrySet()) {
                if (!finalized.contains(entry.getKey()) &&
                        entry.getValue() < minDist) {
                    minDist = entry.getValue();
                    u = entry.getKey();
                }
            }
            if (u == null) break;

            finalized.add(u);
            nodeState.put(u, COLOR_CURRENT);

            // Highlight the tree edge to this node
            if (prev.containsKey(u)) {
                Edge pe = findEdge(prev.get(u), u);
                if (pe != null) edgeState.put(EdgeKey(pe), COLOR_TREE_EDGE);
            }

            // Relax neighbors
            List<String> neighbors = new ArrayList<>(graph.getNeighbors(u));
            Collections.sort(neighbors);
            int relaxed = 0;

            for (String v : neighbors) {
                if (finalized.contains(v)) continue;

                Edge e = findEdge(u, v);
                double weight = (e != null) ? Math.max(e.getWeight(), 0.001) : 1.0;
                double alt = dist.get(u) + weight;

                if (e != null) {
                    edgeState.put(edgeKey(e), COLOR_ACTIVE_EDGE);
                }

                if (alt < dist.get(v)) {
                    dist.put(v, alt);
                    prev.put(v, u);
                    nodeState.put(v, COLOR_FRONTIER);
                    nodeLabels.put(v, v + " (" + String.format("%.1f", alt) + ")");
                    relaxed++;
                }
            }

            frames.add(new AnimationFrame(step, "Dijkstra",
                    "Finalize " + u + " (d=" + String.format("%.1f", minDist) +
                            "), relaxed " + relaxed + " edges",
                    nodeState, edgeState, nodeLabels, null));

            nodeState.put(u, COLOR_VISITED);
            step++;
        }

        frames.add(new AnimationFrame(step, "Dijkstra",
                "Dijkstra complete — " + finalized.size() + " vertices reached",
                nodeState, EdgeState, nodeLabels, null));

        return frames;
    }

    // ── Kruskal MST animation ──────────────────────────────────────

    /**
     * Animate Kruskal's minimum spanning tree construction.
     *
     * @return list of animation frames
     */
    public List<AnimationFrame> animateKruskal() {
        List<AnimationFrame> frames = new ArrayList<>();

        Map<String, String> nodeState = new LinkedHashMap<>();
        Map<String, String> edgeState = new LinkedHashMap<>();
        Map<String, String> nodeLabels = new LinkedHashMap<>();
        Map<String, String> edgeLabelOverrides = new LinkedHashMap<>();

        for (String v : graph.getVertices()) {
            nodeState.put(v, COLOR_UNVISITED);
            nodeLabels.put(v, v);
        }
        for (Edge e : graph.getEdges()) {
            edgeState.put(edgeKey(e), COLOR_UNVISITED);
        }

        // Sort edges by weight
        List<Edge> sortedEdges = new ArrayList<>(graph.getEdges());
        sortedEdges.sort(Comparator.comparingDouble(edge::getWeight));

        // Union-Find
        Map<String, String> parent = new LinkedHashMap<>();
        Map<String, Integer> rank = new LinkedHashMap<>();
        for (String v : graph.getVertices()) {
            parent.put(v, v);
            rank.put(v, 0);
        }

        frames.add(new AnimationFrame(0, "Kruskal",
                "Start Kruskal MST — " + sortedEdges.size() +
                        " edges sorted by weight",
                nodeState, edgeState, nodeLabels, edgeLabelOverrides));

        int step = 1;
        int treeEdges = 0;
        double totalWeight = 0;

        for (Edge e : sortedEdges) {
            String u = e.getVertex1();
            String v = e.getVertex2();
            String ek = edgeKey(e);

            edgeState.put(ek, COLOR_ACTIVE_EDGE);
            edgeLabelOverrides.put(ek,
                    String.format("%.1f", e.getWeight()));

            String ru = find(parent, u);
            String rv = find(parent, v);

            if (!ru.equals(rv)) {
                union(parent, rank, ru, rv);
                edgeState.put(ek, COLOR_TREE_EDGE);
                nodeState.put(u, COLOR_VISITED);
                nodeState.put(v, COLOR_VISITED);
                treeEdges++;
                totalWeight += e.getWeight();

                frames.add(new AnimationFrame(step, "Kruskal",
                        "Add edge " + u + "—" + v +
                                " (w=" + String.format("%.1f", e.getWeight()) +
                                ") — " + treeEdges + " tree edges",
                        nodeState, edgeState, nodeLabels, edgeLabelOverrides));
            } else {
                frames.add(new AnimationFrame(step, "Kruskal",
                        "Skip edge " + u + "—" + v +
                                " (would create cycle)",
                        nodeState, edgeState, nodeLabels, edgeLabelOverrides));
                edgeState.put(ek, COLOR_UNVISITED);
            }

            step++;
            if (treeEdges >= graph.getVertexCount() - 1) break;
        }

        frames.add(new AnimationFrame(step, "Kruskal",
                "MST complete — " + treeEdges + " edges, total weight " +
                        String.format("%.1f", totalWeight),
                nodeState, edgeState, nodeLabels, edgeLabelOverrides));

        return frames;
    }

    // ── PageRank animation ──────────────────────────────────────────

    /**
     * Animate PageRank iterations showing rank convergence.
     *
     * @param dampingFactor damping factor (typically 0.85)
     * @param maxIterations maximum iterations
     * @return list of animation frames
     */
    public List<AnimationFrame> animatePageRank(double dampingFactor,
                                                  int maxIterations) {
        List<AnimationFrame> frames = new ArrayList<>();
        int n = graph.getVertexCount();
        if (n == 0) return frames;

        Map<String, Double> ranks = new LinkedHashMap<>();
        double initial = 1.0 / n;
        for (String v : graph.getVertices()) {
            ranks.put(v, initial);
        }

        Map<String, String> nodeState = new LinkedHashMap<>();
        Map<String, String> edgeState = new LinkedHashMap<>();
        Map<String, String> nodeLabels = new LinkedHashMap<>();

        for (Edge e : graph.getEdges()) {
            edgeState.put(edgeKey(e), COLOR_UNVISITED);
        }

        updatePageRankColors(ranks, nodeState, nodeLabels, n);
        frames.add(new AnimationFrame(0, "PageRank",
                "Initialize — all nodes rank = " +
                        String.format("%.3f", initial),
                nodeState, edgeState, nodeLabels, null));

        for (int iter = 1; iter <= maxIterations; iter++) {
            Map<String, Double> newRanks = new LinkedHashMap<>();
            double sinkShare = 0;

            // Collect sink node contributions
            for (String v : graph.getVertices()) {
                if (graph.getOutEdges(v).isEmpty()) {
                    sinkShare += ranks.get(v);
                }
            }
            sinkShare = dampingFactor * sinkShare / n;

            for (String v : graph.getVertices()) {
                double sum = 0;
                for (String u : graph.getPredecessors(v)) {
                    int outDeg = graph.getOutEdges(u).size();
                    if (outDeg > 0) {
                        sum += ranks.get(u) / outDeg;
                    }
                }
                newRanks.put(v, (1 - dampingFactor) / n +
                        dampingFactor * sum + sinkShare);
            }

            // Check convergence
            double maxDiff = 0;
            for (String v : graph.getVertices()) {
                maxDiff = Math.max(maxDiff,
                        Math.abs(newRanks.get(v) - ranks.get(v)));
            }

            ranks = newRanks;
            updatePageRankColors(ranks, nodeState, nodeLabels, n);

            frames.add(new AnimationFrame(iter, "PageRank",
                    "Iteration " + iter + " — max \u0394 = " +
                            String.format("%.6f", maxDiff),
                    nodeState, edgeState, nodeLabels, null));

            if (maxDiff < 1e-6) break;
        }

        return frames;
    }

    private void updatePageRankColors(Map<String, Double> ranks,
                                       Map<String, String> nodeState,
                                       Map<String, String> nodeLabels,
                                       int n) {
        double maxRank = 0;
        for (double r : ranks.values()) maxRank = Math.max(maxRank, r);
        if (maxRank == 0) maxRank = 1;

        for (Map.Entry<String, Double> entry : ranks.entrySet()) {
            String v = entry.getKey();
            double r = entry.getValue();
            double intensity = r / maxRank;
            nodeState.put(v, interpolateColor(intensity));
            nodeLabels.put(v, v + " (" +
                    String.format("%.3f", r) + ")");
        }
    }

    // ── SVG rendering ──────────────────────────────────────────────

    /**
     * Render a single animation frame to SVG.
     *
     * @param frame  the frame to render
     * @param width  SVG width
     * @param height SVG height
     * @return SVG string
     */
    public String toSVG(AnimationFrame frame, int width, int height) {
        StringBuilder sb = new StringBuilder();
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" ")
          .append("width=\"").append(width)
          .append("\" height=\"").append(height).append("\">\n");
        sb.append("<rect width=\"100%\" height=\"100%\" fill=\"")
          .append(COLOR_BG).append("\"/>\n");

        // Title bar
        sb.append("<text x=\"10\" y=\"25\" fill=\"").append(COLOR_TEXT)
          .append("\" font-family=\"sans-serif\" font-size=\"14\" font-weight=\"bold\">")
          .append(escapeXml(frame.algorithmName))
          .append(" — Step ").append(frame.stepNumber).append("</text>\n");
        sb.append("<text x=\"10\" y=\"45\" fill=\"").append(COLOR_TEXT)
          .append("\" font-family=\"sans-serif\" font-size=\"12\" opacity=\"0.8\">")
          .append(escapeXml(frame.description)).append("</text>\n");

        int offsetY = 60;

        // Draw edges
        for (Edge e : graph.getEdges()) {
            String ek = edgeKey(e);
            String color = frame.edgeColors.getOrDefault(ek, COLOR_UNVISITED);
            double[] p1 = positions.get(e.getVertex1());
            double[] p2 = positions.get(e.getVertex2());
            if (p1 == null || p2 == null) continue;

            float strokeWidth = color.equals(COLOR_UNVISITED) ? 1.0f : 2.5f;
            float opacity = color.equals(COLOR_UNVISITED) ? 0.3f : 0.9f;

            sb.append("<line x1=\"").append(fmt(p1[0]))
              .append("\" y1=\"").append(fmt(p1[1] + offsetY))
              .append("\" x2=\"").append(fmt(p2[0]))
              .append("\" y2=\"").append(fmt(p2[1] + offsetY))
              .append("\" stroke=\"").append(color)
              .append("\" stroke-width=\"").append(strokeWidth)
              .append("\" opacity=\"").append(opacity).append("\"/>\n");

            // Edge label
            String lbl = frame.edgeLabelOverrides.containsKey(ek)
                    ? frame.edgeLabelOverrides.get(ek) : null;
            if (lbl != null) {
                double mx = (p1[0] + p2[0]) / 2;
                double my = (p1[1] + p2[1]) / 2 + offsetY;
                sb.append("<text x=\"").append(fmt(mx))
                  .append("\" y=\"").append(fmt(my - 5))
                  .append("\" fill=\"").append(COLOR_TEXT)
                  .append("\" font-family=\"sans-serif\" font-size=\"9\" ")
                  .append("text-anchor=\"middle\" opacity=\"0.7\">")
                  .append(escapeXml(lbl)).append("</text>\n");
            }
        }

        // Draw nodes
        for (String v : graph.getVertices()) {
            double[] pos = positions.get(v);
            if (pos == null) continue;
            String color = frame.nodeColors.getOrDefault(v, COLOR_UNVISITED);
            String label = frame.nodeLabels.getOrDefault(v, v);

            sb.append("<circle cx=\"").append(fmt(pos[0]))
              .append("\" cy=\"").append(fmt(pos[1] + offsetY))
              .append("\" r=\"").append(nodeRadius)
              .append("\" fill=\"").append(color)
              .append("\" stroke=\"").append(COLOR_TEXT)
              .append("\" stroke-width=\"1.5\"/>\n");

            sb.append("<text x=\"").append(fmt(pos[0]))
              .append("\" y=\"").append(fmt(pos[1] + offsetY + nodeRadius + 14))
              .append("\" fill=\"").append(COLOR_TEXT)
              .append("\" font-family=\"sans-serif\" font-size=\"10\" ")
              .append("text-anchor=\"middle\">")
              .append(escapeXml(label)).append("</text>\n");
        }

        sb.append("</svg>");
        return sb.toString();
    }

    // ── HTML player ────────────────────────────────────────────────

    /**
     * Generate a self-contained HTML page with an interactive frame player.
     * Includes play/pause, step forward/backward, speed control, and a
     * progress bar.
     *
     * @param frames list of animation frames
     * @param width  player width
     * @param height player height
     * @return complete HTML string
     */
    public String toHtmlPlayer(List<AnimationFrame> frames,
                                int width, int height) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html><head>\n");
        sb.append("<meta charset=\"utf-8\">\n");
        sb.append("<title>").append(frames.isEmpty() ? "Graph Animation"
                : escapeXml(frames.get(0).algorithmName) + " Animation")
          .append("</title>\n");
        sb.append("<style>\n");
        sb.append("body{margin:0;background:#0f172a;color:#f8fafc;");
        sb.append("font-family:system-ui,sans-serif;display:flex;");
        sb.append("flex-direction:column;align-items:center;padding:1rem}\n");
        sb.append(".controls{display:flex;gap:8px;margin:12px 0;align-items:center}\n");
        sb.append("button{background:#334155;color:#f8fafc;border:none;");
        sb.append("padding:6px 14px;border-radius:6px;cursor:pointer;font-size:13px}\n");
        sb.append("button:hover{background:#475569}\n");
        sb.append("input[type=range]{width:120px}\n");
        sb.append("#info{font-size:13px;opacity:0.8;margin:4px 0}\n");
        sb.append("#progress{width:").append(width).append("px;height:4px;");
        sb.append("background:#334155;border-radius:2px;margin:8px 0}\n");
        sb.append("#bar{height:100%;background:#3b82f6;border-radius:2px;");
        sb.append("transition:width 0.2s}\n");
        sb.append("</style>\n</head><body>\n");

        sb.append("<div id=\"canvas\"></div>\n");
        sb.append("<div id=\"progress\"><div id=\"bar\" style=\"width:0%\"></div></div>\n");
        sb.append("<div id=\"info\"></div>\n");
        sb.append("<div class=\"controls\">\n");
        sb.append("  <button onclick=\"stepBack()\">&#x23EE; Prev</button>\n");
        sb.append("  <button id=\"playBtn\" onclick=\"togglePlay()\">&#x25B6; Play</button>\n");
        sb.append("  <button onclick=\"stepFwd()\">Next &#x23ED;</button>\n");
        sb.append("  <label>Speed: <input type=\"range\" id=\"speed\" ");
        sb.append("min=\"1\" max=\"10\" value=\"3\" oninput=\"updateSpeed()\"></label>\n");
        sb.append("</div>\n");

        // Embed frames as JSON
        sb.append("<script>\n");
        sb.append("var svgs = [\n");
        for (int i = 0; i < frames.size(); i++) {
            String svg = toSVG(frames.get(i), width, height);
            sb.append("  ").append(escapeJS(svg));
            if (i < frames.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("];\n");

        sb.append("var idx=0, playing=false, timer=null, delay=800;\n");
        sb.append("function show(i){idx=Math.max(0,Math.min(i,svgs.length-1));\n");
        sb.append("  document.getElementById('canvas').innerHTML=svgs[idx];\n");
        sb.append("  document.getElementById('bar').style.width=");
        sb.append("(idx/(svgs.length-1)*100)+'%';\n");
        sb.append("  document.getElementById('info').textContent=");
        sb.append("'Frame '+(idx+1)+' / '+svgs.length;}\n");
        sb.append("function stepFwd(){show(idx+1)}\n");
        sb.append("function stepBack(){show(idx-1)}\n");
        sb.append("function togglePlay(){playing=!playing;\n");
        sb.append("  document.getElementById('playBtn').innerHTML=");
        sb.append("playing?'&#x23F8; Pause':'&#x25B6; Play';\n");
        sb.append("  if(playing) tick(); else clearTimeout(timer);}\n");
        sb.append("function tick(){if(!playing)return;stepFwd();\n");
        sb.append("  if(idx<svgs.length-1) timer=setTimeout(tick,delay);");
        sb.append("  else{playing=false;document.getElementById('playBtn')");
        sb.append(".innerHTML='&#x25B6; Play';}}\n");
        sb.append("function updateSpeed(){delay=1200-");
        sb.append("document.getElementById('speed').value*100;}\n");
        sb.append("show(0);\n");
        sb.append("</script>\n</body></html>");

        return sb.toString();
    }

    // ── Summary ────────────────────────────────────────────────────

    /**
     * Get a text summary of available animations for this graph.
     *
     * @return summary string
     */
    public String getSummary() {
        int v = graph.getVertexCount();
        int e = graph.getEdgeCount();
        StringBuilder sb = new StringBuilder();
        sb.append("GraphAlgorithmAnimator\n");
        sb.append("  Graph: ").append(v).append(" vertices, ")
          .append(e).append(" edges\n");
        sb.append("  Available animations:\n");
        sb.append("    - BFS (breadth-first search)\n");
        sb.append("    - DFS (depth-first search)\n");
        sb.append("    - Dijkstra (shortest paths)\n");
        sb.append("    - Kruskal (minimum spanning tree)\n");
        sb.append("    - PageRank (iterative convergence)\n");
        sb.append("  Output: SVG frames or self-contained HTML player\n");
        return sb.toString();
    }

    // ── Utility methods ────────────────────────────────────────────

    private void validateVertex(String v) {
        if (!graph.containsVertex(v)) {
            throw new IllegalArgumentException(
                    "Vertex '" + v + "' not found in graph");
        }
    }

    private String edgeKey(Edge e) {
        if (e == null) return "";
        String a = e.getVertex1();
        String b = e.getVertex2();
        return (a.compareTo(b) <= 0) ? a + "~~" + b : b + "~~" + a;
    }

    private Edge findEdge(String u, String v) {
        Edge e = graph.findEdge(u, v);
        if (e == null) e = graph.findEdge(v, u);
        return e;
    }

    private int countColor(Map<String, String> map, String color) {
        int count = 0;
        for (String c : map.values()) {
            if (c.equals(color)) count++;
        }
        return count;
    }

    private String interpolateColor(double t) {
        // Interpolate from blue (#3b82f6) to red (#ef4444)
        t = Math.max(0, Math.min(1, t));
        int r = (int) (0x3b + t * (0xef - 0x3b));
        int g = (int) (0x82 + t * (0x44 - 0x82));
        int b = (int) (0xf6 + t * (0x44 - 0xf6));
        return String.format("#%02x%02x%02x", r, g, b);
    }

    private static String fmt(double v) {
        return String.format("%.1f", v);
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String escapeJS(String s) {
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                       .replace("\r", "") + "\"";
    }

    // Union-Find for Kruskal
    private String find(Map<String, String> parent, String x) {
        while (!parent.get(x).equals(x)) {
            parent.put(x, parent.get(parent.get(x)));
            x = parent.get(x);
        }
        return x;
    }

    private void union(Map<String, String> parent,
                       Map<String, Integer> rank, String a, String b) {
        if (rank.get(a) < rank.get(b)) { String t = a; a = b; b = t; }
        parent.put(b, a);
        if (rank.get(a).equals(rank.get(b))) {
            rank.put(a, rank.get(a) + 1);
        }
    }

    /**
     * Compute a simple circular layout for the graph.
     */
    private static Map<String, double[]> computeLayout(
            Graph<String, Edge> graph, int width, int height, int radius) {
        Map<String, double[]> pos = new LinkedHashMap<>();
        List<String> vertices = new ArrayList<>(graph.getVertices());
        Collections.sort(vertices);
        int n = vertices.size();
        int padding = radius + 40;
        double cx = width / 2.0;
        double cy = (height - 60) / 2.0;
        double rx = cx - padding;
        double ry = cy - padding;

        for (int i = 0; i < n; i++) {
            double angle = 2 * Math.PI * i / n - Math.PI / 2;
            pos.put(vertices.get(i), new double[]{
                    cx + rx * Math.cos(angle),
                    cy + ry * Math.sin(angle)
            });
        }
        return pos;
    }
}
