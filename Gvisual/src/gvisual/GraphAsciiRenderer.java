package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Renders a JUNG graph as ASCII/Unicode art for terminal display.
 * Uses a simple force-directed layout mapped to a character grid.
 *
 * <h3>Features</h3>
 * <ul>
 *   <li>Force-directed node placement on a text grid</li>
 *   <li>ASCII and Unicode box-drawing edge styles</li>
 *   <li>Node labels with degree annotations</li>
 *   <li>Configurable grid size (width × height in characters)</li>
 *   <li>Edge weight display</li>
 *   <li>Highlight nodes by name or degree threshold</li>
 *   <li>Legend with graph statistics</li>
 *   <li>Export to file or return as String</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 *   GraphAsciiRenderer renderer = new GraphAsciiRenderer(graph);
 *   renderer.setWidth(120);
 *   renderer.setHeight(40);
 *   renderer.setUnicode(true);
 *   renderer.setShowDegree(true);
 *   System.out.println(renderer.render());
 *
 *   // Or export to file
 *   renderer.exportToFile("graph-ascii.txt");
 * </pre>
 *
 * @author zalenix
 */
public class GraphAsciiRenderer {

    private final Graph<String, edge> graph;
    private int width = 100;
    private int height = 35;
    private boolean unicode = false;
    private boolean showDegree = false;
    private boolean showWeight = false;
    private boolean showLegend = true;
    private Set<String> highlightNodes = new HashSet<>();
    private int highlightDegreeThreshold = -1;
    private int layoutIterations = 200;

    // Layout positions (0.0 - 1.0)
    private Map<String, double[]> positions;

    // Character constants
    private static final char SPACE = ' ';
    private static final char DOT_ASCII = '*';
    private static final char DOT_UNICODE = '●';
    private static final char HIGHLIGHT_ASCII = '@';
    private static final char HIGHLIGHT_UNICODE = '◆';
    private static final char EDGE_H_ASCII = '-';
    private static final char EDGE_V_ASCII = '|';
    private static final char EDGE_D1_ASCII = '/';
    private static final char EDGE_D2_ASCII = '\\';
    private static final char EDGE_H_UNICODE = '─';
    private static final char EDGE_V_UNICODE = '│';
    private static final char EDGE_D1_UNICODE = '╱';
    private static final char EDGE_D2_UNICODE = '╲';

    /**
     * Create an ASCII renderer for the given graph.
     *
     * @param graph the JUNG graph to render
     * @throws IllegalArgumentException if graph is null
     */
    public GraphAsciiRenderer(Graph<String, edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
    }

    /** Set grid width in characters. Default: 100. */
    public void setWidth(int width) {
        if (width < 20) throw new IllegalArgumentException("Width must be >= 20");
        this.width = width;
    }

    /** Set grid height in characters. Default: 35. */
    public void setHeight(int height) {
        if (height < 10) throw new IllegalArgumentException("Height must be >= 10");
        this.height = height;
    }

    /** Use Unicode box-drawing characters instead of ASCII. */
    public void setUnicode(boolean unicode) {
        this.unicode = unicode;
    }

    /** Show node degree next to label. */
    public void setShowDegree(boolean showDegree) {
        this.showDegree = showDegree;
    }

    /** Show edge weight along edges. */
    public void setShowWeight(boolean showWeight) {
        this.showWeight = showWeight;
    }

    /** Show legend with graph statistics. Default: true. */
    public void setShowLegend(boolean showLegend) {
        this.showLegend = showLegend;
    }

    /** Highlight specific nodes by name. */
    public void addHighlightNode(String node) {
        highlightNodes.add(node);
    }

    /** Highlight all nodes with degree >= threshold. */
    public void setHighlightDegreeThreshold(int threshold) {
        this.highlightDegreeThreshold = threshold;
    }

    /** Set number of force-directed layout iterations. Default: 200. */
    public void setLayoutIterations(int iterations) {
        if (iterations < 1) throw new IllegalArgumentException("Iterations must be >= 1");
        this.layoutIterations = iterations;
    }

    /**
     * Render the graph to an ASCII/Unicode string.
     *
     * @return the rendered graph as a multi-line string
     */
    public String render() {
        if (graph.getVertexCount() == 0) {
            return "(empty graph)";
        }

        computeLayout();

        // Map positions to grid coordinates (leave margin for labels)
        int marginX = 3;
        int marginY = 2;
        int gridW = width - 2 * marginX;
        int gridH = height - 2 * marginY;

        Map<String, int[]> gridPos = new HashMap<>();
        for (Map.Entry<String, double[]> entry : positions.entrySet()) {
            int gx = marginX + (int) (entry.getValue()[0] * (gridW - 1));
            int gy = marginY + (int) (entry.getValue()[1] * (gridH - 1));
            gx = Math.max(marginX, Math.min(width - marginX - 1, gx));
            gy = Math.max(marginY, Math.min(height - marginY - 1, gy));
            gridPos.put(entry.getKey(), new int[]{gx, gy});
        }

        // Initialize grid
        char[][] grid = new char[height][width];
        for (char[] row : grid) Arrays.fill(row, SPACE);

        // Draw edges
        for (edge e : graph.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            if (v1 == null || v2 == null) continue;
            int[] p1 = gridPos.get(v1);
            int[] p2 = gridPos.get(v2);
            if (p1 == null || p2 == null) continue;
            drawEdge(grid, p1[0], p1[1], p2[0], p2[1]);
        }

        // Draw nodes (overwrite edge chars at node positions)
        for (String vertex : graph.getVertices()) {
            int[] pos = gridPos.get(vertex);
            if (pos == null) continue;
            boolean highlighted = highlightNodes.contains(vertex)
                    || (highlightDegreeThreshold >= 0 && graph.degree(vertex) >= highlightDegreeThreshold);
            char nodeChar;
            if (highlighted) {
                nodeChar = unicode ? HIGHLIGHT_UNICODE : HIGHLIGHT_ASCII;
            } else {
                nodeChar = unicode ? DOT_UNICODE : DOT_ASCII;
            }
            if (pos[1] >= 0 && pos[1] < height && pos[0] >= 0 && pos[0] < width) {
                grid[pos[1]][pos[0]] = nodeChar;
            }

            // Draw label to the right of node
            String label = truncateLabel(vertex, 8);
            if (showDegree) {
                label += "(" + graph.degree(vertex) + ")";
            }
            int labelStart = pos[0] + 2;
            if (labelStart + label.length() >= width) {
                labelStart = pos[0] - label.length() - 1;
            }
            if (labelStart >= 0 && pos[1] >= 0 && pos[1] < height) {
                for (int i = 0; i < label.length() && labelStart + i < width; i++) {
                    if (grid[pos[1]][labelStart + i] == SPACE) {
                        grid[pos[1]][labelStart + i] = label.charAt(i);
                    }
                }
            }
        }

        // Build output
        StringBuilder sb = new StringBuilder();

        // Top border
        String hBorder = unicode ? "═" : "=";
        String corner = unicode ? "╔" : "+";
        String cornerR = unicode ? "╗" : "+";
        String cornerBL = unicode ? "╚" : "+";
        String cornerBR = unicode ? "╝" : "+";
        String vBorder = unicode ? "║" : "|";

        sb.append(corner);
        for (int i = 0; i < width; i++) sb.append(hBorder);
        sb.append(cornerR).append('\n');

        // Grid rows
        for (int y = 0; y < height; y++) {
            sb.append(vBorder);
            sb.append(new String(grid[y]));
            sb.append(vBorder).append('\n');
        }

        // Bottom border
        sb.append(cornerBL);
        for (int i = 0; i < width; i++) sb.append(hBorder);
        sb.append(cornerBR).append('\n');

        // Legend
        if (showLegend) {
            sb.append('\n');
            sb.append("  Graph: ").append(graph.getVertexCount()).append(" nodes, ")
              .append(graph.getEdgeCount()).append(" edges\n");

            if (!highlightNodes.isEmpty() || highlightDegreeThreshold >= 0) {
                char hChar = unicode ? HIGHLIGHT_UNICODE : HIGHLIGHT_ASCII;
                char nChar = unicode ? DOT_UNICODE : DOT_ASCII;
                sb.append("  ").append(nChar).append(" = node, ")
                  .append(hChar).append(" = highlighted\n");
            }

            // Degree stats
            int minDeg = Integer.MAX_VALUE, maxDeg = 0;
            double sumDeg = 0;
            for (String v : graph.getVertices()) {
                int d = graph.degree(v);
                minDeg = Math.min(minDeg, d);
                maxDeg = Math.max(maxDeg, d);
                sumDeg += d;
            }
            double avgDeg = graph.getVertexCount() > 0 ? sumDeg / graph.getVertexCount() : 0;
            sb.append(String.format("  Degree: min=%d, max=%d, avg=%.1f%n", minDeg, maxDeg, avgDeg));

            // Edge type distribution
            Map<String, Integer> typeCounts = new TreeMap<>();
            for (edge e : graph.getEdges()) {
                String type = e.getType() != null ? e.getType() : "unknown";
                typeCounts.merge(type, 1, Integer::sum);
            }
            if (!typeCounts.isEmpty()) {
                sb.append("  Edge types: ");
                boolean first = true;
                for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
                    if (!first) sb.append(", ");
                    sb.append(entry.getKey()).append("=").append(entry.getValue());
                    first = false;
                }
                sb.append('\n');
            }
        }

        return sb.toString();
    }

    /**
     * Export the rendered graph to a text file.
     *
     * @param filePath path to the output file
     * @throws IOException if writing fails
     */
    public void exportToFile(String filePath) throws IOException {
        String rendered = render();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8)) {
            writer.write(rendered);
        }
    }

    /**
     * Render a compact adjacency list view (alternative to spatial layout).
     *
     * @return adjacency list as a string
     */
    public String renderAdjacencyList() {
        StringBuilder sb = new StringBuilder();
        sb.append("Adjacency List (").append(graph.getVertexCount()).append(" nodes, ")
          .append(graph.getEdgeCount()).append(" edges)\n");
        sb.append(unicode ? "─────────────────────────────\n" : "-----------------------------\n");

        List<String> vertices = new ArrayList<>(graph.getVertices());
        Collections.sort(vertices);

        for (String v : vertices) {
            int deg = graph.degree(v);
            sb.append(unicode ? " ● " : " * ").append(v);
            if (showDegree) sb.append(" [").append(deg).append("]");
            sb.append('\n');

            Collection<String> neighbors = graph.getNeighbors(v);
            if (neighbors != null) {
                List<String> sorted = new ArrayList<>(neighbors);
                Collections.sort(sorted);
                for (String n : sorted) {
                    sb.append(unicode ? "   ├── " : "   |-- ").append(n).append('\n');
                }
            }
        }
        return sb.toString();
    }

    /**
     * Render a degree histogram as horizontal ASCII bar chart.
     *
     * @return degree histogram string
     */
    public String renderDegreeHistogram() {
        if (graph.getVertexCount() == 0) return "(empty graph)";

        Map<Integer, Integer> histogram = new TreeMap<>();
        int maxDeg = 0;
        for (String v : graph.getVertices()) {
            int d = graph.degree(v);
            histogram.merge(d, 1, Integer::sum);
            maxDeg = Math.max(maxDeg, d);
        }

        int maxCount = Collections.max(histogram.values());
        int barMaxLen = Math.min(50, width - 20);

        StringBuilder sb = new StringBuilder();
        sb.append("Degree Distribution\n");
        sb.append(unicode ? "═══════════════════\n" : "===================\n");

        for (Map.Entry<Integer, Integer> entry : histogram.entrySet()) {
            int deg = entry.getKey();
            int count = entry.getValue();
            int barLen = maxCount > 0 ? (int) ((double) count / maxCount * barMaxLen) : 0;
            barLen = Math.max(1, barLen);

            String bar;
            if (unicode) {
                bar = "█".repeat(barLen);
            } else {
                bar = "#".repeat(barLen);
            }
            sb.append(String.format("  %3d │ %s %d%n", deg, bar, count));
        }
        return sb.toString();
    }

    // --- Private layout methods ---

    private void computeLayout() {
        positions = new HashMap<>();
        List<String> vertices = new ArrayList<>(graph.getVertices());
        int n = vertices.size();

        if (n == 1) {
            positions.put(vertices.get(0), new double[]{0.5, 0.5});
            return;
        }

        // Initialize with circular layout
        Random rand = new Random(42);
        for (int i = 0; i < n; i++) {
            double angle = 2.0 * Math.PI * i / n;
            double x = 0.5 + 0.35 * Math.cos(angle);
            double y = 0.5 + 0.35 * Math.sin(angle);
            positions.put(vertices.get(i), new double[]{x, y});
        }

        // Build adjacency for quick lookup
        Set<String> edgeSet = new HashSet<>();
        for (edge e : graph.getEdges()) {
            if (e.getVertex1() != null && e.getVertex2() != null) {
                edgeSet.add(e.getVertex1() + "|" + e.getVertex2());
                edgeSet.add(e.getVertex2() + "|" + e.getVertex1());
            }
        }

        // Force-directed iterations
        double k = 1.0 / Math.sqrt(n); // ideal distance
        double temp = 0.1;

        for (int iter = 0; iter < layoutIterations; iter++) {
            Map<String, double[]> disp = new HashMap<>();
            for (String v : vertices) disp.put(v, new double[]{0, 0});

            // Repulsive forces between all pairs
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    String vi = vertices.get(i);
                    String vj = vertices.get(j);
                    double[] pi = positions.get(vi);
                    double[] pj = positions.get(vj);
                    double dx = pi[0] - pj[0];
                    double dy = pi[1] - pj[1];
                    double dist = Math.sqrt(dx * dx + dy * dy) + 0.0001;
                    double force = (k * k) / dist;
                    double fx = (dx / dist) * force;
                    double fy = (dy / dist) * force;
                    disp.get(vi)[0] += fx;
                    disp.get(vi)[1] += fy;
                    disp.get(vj)[0] -= fx;
                    disp.get(vj)[1] -= fy;
                }
            }

            // Attractive forces along edges
            for (edge e : graph.getEdges()) {
                String v1 = e.getVertex1();
                String v2 = e.getVertex2();
                if (v1 == null || v2 == null) continue;
                double[] p1 = positions.get(v1);
                double[] p2 = positions.get(v2);
                if (p1 == null || p2 == null) continue;
                double dx = p1[0] - p2[0];
                double dy = p1[1] - p2[1];
                double dist = Math.sqrt(dx * dx + dy * dy) + 0.0001;
                double force = (dist * dist) / k;
                double fx = (dx / dist) * force;
                double fy = (dy / dist) * force;
                disp.get(v1)[0] -= fx;
                disp.get(v1)[1] -= fy;
                disp.get(v2)[0] += fx;
                disp.get(v2)[1] += fy;
            }

            // Apply displacement with temperature cooling
            for (String v : vertices) {
                double[] d = disp.get(v);
                double dist = Math.sqrt(d[0] * d[0] + d[1] * d[1]) + 0.0001;
                double scale = Math.min(dist, temp) / dist;
                double[] p = positions.get(v);
                p[0] = Math.max(0.05, Math.min(0.95, p[0] + d[0] * scale));
                p[1] = Math.max(0.05, Math.min(0.95, p[1] + d[1] * scale));
            }

            temp *= 0.95; // cool down
        }
    }

    private void drawEdge(char[][] grid, int x1, int y1, int x2, int y2) {
        // Bresenham's line algorithm
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        int cx = x1, cy = y1;
        while (true) {
            if (cx == x1 && cy == y1) {
                // skip start node
            } else if (cx == x2 && cy == y2) {
                break; // skip end node
            } else if (cy >= 0 && cy < grid.length && cx >= 0 && cx < grid[0].length) {
                if (grid[cy][cx] == SPACE) {
                    grid[cy][cx] = pickEdgeChar(x1, y1, x2, y2);
                }
            }

            if (cx == x2 && cy == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; cx += sx; }
            if (e2 < dx)  { err += dx; cy += sy; }
        }
    }

    private char pickEdgeChar(int x1, int y1, int x2, int y2) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);

        if (dy == 0) return unicode ? EDGE_H_UNICODE : EDGE_H_ASCII;
        if (dx == 0) return unicode ? EDGE_V_UNICODE : EDGE_V_ASCII;

        // Diagonal
        boolean sameDirection = (x2 - x1 > 0) == (y2 - y1 > 0);
        if (sameDirection) return unicode ? EDGE_D2_UNICODE : EDGE_D2_ASCII;
        return unicode ? EDGE_D1_UNICODE : EDGE_D1_ASCII;
    }

    private String truncateLabel(String label, int maxLen) {
        if (label.length() <= maxLen) return label;
        return label.substring(0, maxLen - 1) + "~";
    }
}
