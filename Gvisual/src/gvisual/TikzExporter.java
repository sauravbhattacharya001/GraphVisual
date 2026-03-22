package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Exports a JUNG graph to TikZ/LaTeX format for direct inclusion in
 * academic papers, theses, and presentations.
 *
 * <p>The output is a standalone {@code .tex} file that compiles with
 * {@code pdflatex} or {@code lualatex}. It uses the {@code tikz} package
 * with force-directed layout computed internally (no TeX-side layout
 * engine required).</p>
 *
 * <h3>Features</h3>
 * <ul>
 *   <li>Force-directed automatic layout</li>
 *   <li>Edge type → color mapping (uses xcolor named colors)</li>
 *   <li>Edge weight → line width scaling</li>
 *   <li>Node degree → radius scaling</li>
 *   <li>Node labels</li>
 *   <li>Optional legend</li>
 *   <li>Standalone or includable (with/without preamble)</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 *   TikzExporter exporter = new TikzExporter(graph);
 *   exporter.setTitle("Student Network");
 *   exporter.export(new File("network.tex"));
 * </pre>
 *
 * @author zalenix
 */
public class TikzExporter {

    private final Graph<String, Edge> graph;
    private double canvasWidth = 12.0;  // cm
    private double canvasHeight = 9.0;  // cm
    private String title;
    private boolean standalone = true;
    private boolean showLegend = true;
    private boolean showLabels = true;
    private boolean scaleNodesByDegree = true;
    private boolean scaleEdgesByWeight = true;
    private int layoutIterations = 300;

    private static final Map<String, String> TYPE_COLORS = new LinkedHashMap<>();
    static {
        TYPE_COLORS.put("f",  "green!70!black");
        TYPE_COLORS.put("fs", "blue!70!black");
        TYPE_COLORS.put("c",  "orange!80!black");
        TYPE_COLORS.put("s",  "red!70!black");
        TYPE_COLORS.put("sg", "violet!70!black");
    }

    private static final Map<String, String> TYPE_NAMES = new LinkedHashMap<>();
    static {
        TYPE_NAMES.put("f",  "Friend");
        TYPE_NAMES.put("fs", "Familiar Stranger");
        TYPE_NAMES.put("c",  "Classmate");
        TYPE_NAMES.put("s",  "Stranger");
        TYPE_NAMES.put("sg", "Study Group");
    }

    public TikzExporter(Graph<String, Edge> graph) {
        if (graph == null) throw new IllegalArgumentException("Graph must not be null");
        this.graph = graph;
    }

    public void setCanvasWidth(double cm) { this.canvasWidth = Math.max(4, cm); }
    public void setCanvasHeight(double cm) { this.canvasHeight = Math.max(3, cm); }
    public void setTitle(String title) { this.title = title; }
    public void setStandalone(boolean standalone) { this.standalone = standalone; }
    public void setShowLegend(boolean show) { this.showLegend = show; }
    public void setShowLabels(boolean show) { this.showLabels = show; }
    public void setScaleNodesByDegree(boolean scale) { this.scaleNodesByDegree = scale; }
    public void setScaleEdgesByWeight(boolean scale) { this.scaleEdgesByWeight = scale; }
    public void setLayoutIterations(int n) { this.layoutIterations = Math.max(10, n); }

    public void export(File outputFile) throws IOException {
        ExportUtils.validateOutputPath(outputFile);
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
            writer.write(exportToString());
        }
    }

    public String exportToString() {
        if (graph.getVertexCount() == 0) {
            return emptyDocument();
        }

        Map<String, double[]> positions = computeLayout();
        StringBuilder sb = new StringBuilder();

        int maxDegree = 1;
        for (String v : graph.getVertices()) {
            maxDegree = Math.max(maxDegree, graph.degree(v));
        }
        float minWeight = Float.MAX_VALUE, maxWeight = Float.MIN_VALUE;
        for (Edge e : graph.getEdges()) {
            minWeight = Math.min(minWeight, e.getWeight());
            maxWeight = Math.max(maxWeight, e.getWeight());
        }
        if (minWeight == Float.MAX_VALUE) { minWeight = 0; maxWeight = 1; }
        if (maxWeight <= minWeight) maxWeight = minWeight + 1;

        Set<String> usedTypes = new LinkedHashSet<>();
        for (Edge e : graph.getEdges()) {
            if (e.getType() != null) usedTypes.add(e.getType());
        }

        // Preamble
        if (standalone) {
            sb.append("\\documentclass[border=10pt]{standalone}\n");
            sb.append("\\usepackage[utf8]{inputenc}\n");
            sb.append("\\usepackage{tikz}\n");
            sb.append("\\usepackage{xcolor}\n");
            sb.append("\\usetikzlibrary{arrows.meta,positioning}\n\n");

            // Define colors
            for (String type : usedTypes) {
                String color = TYPE_COLORS.getOrDefault(type, "gray");
                sb.append("\\definecolor{Edge").append(sanitize(type)).append("}{named}{")
                  .append(color.contains("!") ? color.split("!")[0] : color).append("}\n");
            }
            // Actually use xcolor mixing syntax
            sb.setLength(0);
            sb.append("\\documentclass[border=10pt]{standalone}\n");
            sb.append("\\usepackage[utf8]{inputenc}\n");
            sb.append("\\usepackage{tikz}\n");
            sb.append("\\usepackage{xcolor}\n");
            sb.append("\\usetikzlibrary{arrows.meta,positioning}\n\n");

            sb.append("\\begin{document}\n");
        }

        sb.append("\\begin{tikzpicture}[\n");
        sb.append("  every node/.style={circle, draw, inner sep=1pt, font=\\tiny},\n");
        sb.append("  every Edge/.style={-}\n");
        sb.append("]\n\n");

        // Title
        if (title != null && !title.isEmpty()) {
            sb.append("\\node[draw=none, font=\\bfseries\\small] at (")
              .append(fmt(canvasWidth / 2)).append(",").append(fmt(canvasHeight + 0.5))
              .append(") {").append(escapeLatex(title)).append("};\n\n");
        }

        // Nodes
        for (String v : graph.getVertices()) {
            double[] pos = positions.get(v);
            double radius = scaleNodesByDegree
                    ? 3.0 + 7.0 * ((double) graph.degree(v) / maxDegree)
                    : 5.0;
            double tikzRadius = radius / 20.0; // convert to cm-ish

            sb.append("\\node[minimum size=").append(fmt(tikzRadius)).append("cm, fill=blue!20] (")
              .append(sanitize(v)).append(") at (")
              .append(fmt(pos[0])).append(",").append(fmt(pos[1])).append(")");

            if (showLabels) {
                sb.append(" {").append(escapeLatex(v)).append("}");
            } else {
                sb.append(" {}");
            }
            sb.append(";\n");
        }

        sb.append("\n");

        // Edges
        for (Edge e : graph.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            if (v1 == null || v2 == null) continue;

            String color = "gray";
            if (e.getType() != null) {
                color = TYPE_COLORS.getOrDefault(e.getType(), "gray");
            }

            double lineWidth = 0.4;
            if (scaleEdgesByWeight) {
                float w = e.getWeight();
                float norm = (w - minWeight) / (maxWeight - minWeight);
                lineWidth = 0.2 + 1.0 * norm; // pt
            }

            sb.append("\\draw[").append(color).append(", line width=")
              .append(fmt(lineWidth)).append("pt] (")
              .append(sanitize(v1)).append(") -- (").append(sanitize(v2)).append(");\n");
        }

        // Legend
        if (showLegend && !usedTypes.isEmpty()) {
            sb.append("\n% Legend\n");
            double legendX = canvasWidth + 0.5;
            double legendY = canvasHeight;
            sb.append("\\node[draw=none, font=\\bfseries\\footnotesize, anchor=west] at (")
              .append(fmt(legendX)).append(",").append(fmt(legendY)).append(") {Edge Types};\n");

            int i = 0;
            for (String type : usedTypes) {
                String color = TYPE_COLORS.getOrDefault(type, "gray");
                String name = TYPE_NAMES.getOrDefault(type, type);
                double y = legendY - 0.5 - i * 0.4;

                sb.append("\\draw[").append(color).append(", line width=1.5pt] (")
                  .append(fmt(legendX)).append(",").append(fmt(y)).append(") -- ++(0.5,0) ")
                  .append("node[draw=none, right, font=\\tiny] {").append(escapeLatex(name)).append("};\n");
                i++;
            }
        }

        sb.append("\n\\end{tikzpicture}\n");

        if (standalone) {
            sb.append("\\end{document}\n");
        }

        return sb.toString();
    }

    // ---- Layout (same force-directed approach as SvgExporter) ----

    private Map<String, double[]> computeLayout() {
        List<String> vertices = new ArrayList<>(graph.getVertices());
        int n = vertices.size();
        Map<String, double[]> positions = new HashMap<>();
        Random rand = new Random(42);

        for (String v : vertices) {
            positions.put(v, new double[]{
                    0.5 + rand.nextDouble() * (canvasWidth - 1),
                    0.5 + rand.nextDouble() * (canvasHeight - 1)
            });
        }

        double k = Math.sqrt((canvasWidth * canvasHeight) / (double) n);
        double temp = Math.min(canvasWidth, canvasHeight) * 0.1;

        for (int iter = 0; iter < layoutIterations; iter++) {
            Map<String, double[]> disp = new HashMap<>();
            for (String v : vertices) disp.put(v, new double[]{0, 0});

            // Repulsive forces
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    String vi = vertices.get(i), vj = vertices.get(j);
                    double[] pi = positions.get(vi), pj = positions.get(vj);
                    double dx = pi[0] - pj[0], dy = pi[1] - pj[1];
                    double dist = Math.max(Math.sqrt(dx * dx + dy * dy), 0.01);
                    double force = (k * k) / dist;
                    double fx = (dx / dist) * force, fy = (dy / dist) * force;
                    disp.get(vi)[0] += fx; disp.get(vi)[1] += fy;
                    disp.get(vj)[0] -= fx; disp.get(vj)[1] -= fy;
                }
            }

            // Attractive forces
            for (Edge e : graph.getEdges()) {
                String v1 = e.getVertex1(), v2 = e.getVertex2();
                if (v1 == null || v2 == null) continue;
                double[] p1 = positions.get(v1), p2 = positions.get(v2);
                if (p1 == null || p2 == null) continue;
                double dx = p1[0] - p2[0], dy = p1[1] - p2[1];
                double dist = Math.max(Math.sqrt(dx * dx + dy * dy), 0.01);
                double force = (dist * dist) / k;
                double fx = (dx / dist) * force, fy = (dy / dist) * force;
                disp.get(v1)[0] -= fx; disp.get(v1)[1] -= fy;
                disp.get(v2)[0] += fx; disp.get(v2)[1] += fy;
            }

            // Apply with temperature
            for (String v : vertices) {
                double[] d = disp.get(v), p = positions.get(v);
                double dist = Math.max(Math.sqrt(d[0] * d[0] + d[1] * d[1]), 0.01);
                double scale = Math.min(dist, temp) / dist;
                p[0] = Math.max(0.3, Math.min(canvasWidth - 0.3, p[0] + d[0] * scale));
                p[1] = Math.max(0.3, Math.min(canvasHeight - 0.3, p[1] + d[1] * scale));
            }
            temp *= 0.95;
        }

        return positions;
    }

    private String emptyDocument() {
        StringBuilder sb = new StringBuilder();
        if (standalone) {
            sb.append("\\documentclass[border=10pt]{standalone}\n");
            sb.append("\\usepackage{tikz}\n");
            sb.append("\\begin{document}\n");
        }
        sb.append("\\begin{tikzpicture}\n");
        sb.append("\\node[font=\\large\\itshape] {Empty graph};\n");
        sb.append("\\end{tikzpicture}\n");
        if (standalone) {
            sb.append("\\end{document}\n");
        }
        return sb.toString();
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9]", "x");
    }

    private static String escapeLatex(String s) {
        return s.replace("\\", "\\textbackslash{}")
                .replace("{", "\\{").replace("}", "\\}")
                .replace("&", "\\&").replace("%", "\\%")
                .replace("$", "\\$").replace("#", "\\#")
                .replace("_", "\\_").replace("~", "\\textasciitilde{}")
                .replace("^", "\\textasciicircum{}");
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.2f", v);
    }
}
