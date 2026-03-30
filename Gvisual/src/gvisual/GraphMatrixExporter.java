package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Exports graph matrices (adjacency, incidence, Laplacian) to CSV and LaTeX formats.
 *
 * <p>Supports three matrix types:</p>
 * <ul>
 *   <li><b>Adjacency Matrix</b> — N×N binary/weighted matrix of node connections</li>
 *   <li><b>Incidence Matrix</b> — N×M matrix mapping nodes to edges</li>
 *   <li><b>Laplacian Matrix</b> — L = D − A, useful for spectral analysis</li>
 * </ul>
 *
 * <p>Each matrix can be exported to CSV (for spreadsheets, pandas, R) or
 * LaTeX (for academic papers using the {@code amsmath} or {@code array} environments).</p>
 *
 * <p>Usage:</p>
 * <pre>
 *   GraphMatrixExporter exporter = new GraphMatrixExporter(graph, edges);
 *   exporter.exportAdjacencyCsv(new File("adj.csv"));
 *   exporter.exportAdjacencyLatex(new File("adj.tex"));
 *   exporter.exportIncidenceCsv(new File("inc.csv"));
 *   exporter.exportLaplacianLatex(new File("lap.tex"));
 * </pre>
 *
 * @author zalenix
 */
public class GraphMatrixExporter {

    private final Graph<String, Edge> graph;
    private final List<Edge> edges;
    private final List<String> nodes;

    /**
     * Creates a new matrix exporter.
     *
     * @param graph the JUNG graph
     * @param edges all edges in the graph
     */
    public GraphMatrixExporter(Graph<String, Edge> graph, Collection<Edge> edges) {
        this.graph = Objects.requireNonNull(graph, "graph must not be null");
        this.edges = new ArrayList<>(edges);
        List<String> nodeList = new ArrayList<>(graph.getVertices());
        Collections.sort(nodeList);
        this.nodes = Collections.unmodifiableList(nodeList);
    }

    // ── Adjacency Matrix ─────────────────────────────────────────────

    /**
     * Builds the adjacency matrix as a 2D int array.
     * Entry [i][j] = 1 if nodes i and j are connected, 0 otherwise.
     */
    public int[][] buildAdjacencyMatrix() {
        int n = nodes.size();
        int[][] matrix = new int[n][n];
        Map<String, Integer> index = buildIndex();

        for (Edge e : edges) {
            String src = graph.getSource(e) != null ? graph.getSource(e) : graph.getEndpoints(e).getFirst();
            String dst = graph.getDest(e) != null ? graph.getDest(e) : graph.getEndpoints(e).getSecond();
            if (src == null || dst == null) continue;
            Integer si = index.get(src);
            Integer di = index.get(dst);
            if (si == null || di == null) continue;
            matrix[si][di] = 1;
            matrix[di][si] = 1; // undirected
        }
        return matrix;
    }

    /**
     * Builds the Laplacian matrix (L = D − A).
     */
    public int[][] buildLaplacianMatrix() {
        int[][] adj = buildAdjacencyMatrix();
        int n = adj.length;
        int[][] lap = new int[n][n];
        for (int i = 0; i < n; i++) {
            int degree = 0;
            for (int j = 0; j < n; j++) {
                degree += adj[i][j];
                lap[i][j] = -adj[i][j];
            }
            lap[i][i] = degree;
        }
        return lap;
    }

    /**
     * Builds the incidence matrix. Rows = nodes, columns = edges.
     * Entry [i][e] = 1 if node i is an endpoint of edge e.
     */
    public int[][] buildIncidenceMatrix() {
        int n = nodes.size();
        int m = edges.size();
        int[][] matrix = new int[n][m];
        Map<String, Integer> index = buildIndex();

        for (int e = 0; e < m; e++) {
            Edge edge = edges.get(e);
            String src = graph.getSource(edge) != null ? graph.getSource(edge) : graph.getEndpoints(edge).getFirst();
            String dst = graph.getDest(edge) != null ? graph.getDest(edge) : graph.getEndpoints(edge).getSecond();
            if (src != null && index.containsKey(src)) matrix[index.get(src)][e] = 1;
            if (dst != null && index.containsKey(dst)) matrix[index.get(dst)][e] = 1;
        }
        return matrix;
    }

    // ── CSV Export ───────────────────────────────────────────────────

    /**
     * Exports the adjacency matrix to CSV.
     */
    public void exportAdjacencyCsv(File file) throws IOException {
        writeSquareMatrixCsv(file, buildAdjacencyMatrix(), nodes);
    }

    /**
     * Exports the Laplacian matrix to CSV.
     */
    public void exportLaplacianCsv(File file) throws IOException {
        writeSquareMatrixCsv(file, buildLaplacianMatrix(), nodes);
    }

    /**
     * Exports the incidence matrix to CSV.
     */
    public void exportIncidenceCsv(File file) throws IOException {
        int[][] matrix = buildIncidenceMatrix();
        List<String> colHeaders = new ArrayList<>();
        for (int i = 0; i < edges.size(); i++) {
            Edge e = edges.get(i);
            colHeaders.add(e.toString() != null ? e.toString() : "e" + i);
        }
        writeMatrixCsv(file, matrix, nodes, colHeaders);
    }

    /**
     * Returns the adjacency matrix as a CSV string.
     */
    public String adjacencyCsvToString() {
        return squareMatrixCsvToString(buildAdjacencyMatrix(), nodes);
    }

    /**
     * Returns the Laplacian matrix as a CSV string.
     */
    public String laplacianCsvToString() {
        return squareMatrixCsvToString(buildLaplacianMatrix(), nodes);
    }

    // ── LaTeX Export ────────────────────────────────────────────────

    /**
     * Exports the adjacency matrix as a LaTeX bmatrix.
     */
    public void exportAdjacencyLatex(File file) throws IOException {
        writeLatex(file, buildAdjacencyMatrix(), nodes, "Adjacency Matrix $A$");
    }

    /**
     * Exports the Laplacian matrix as a LaTeX bmatrix.
     */
    public void exportLaplacianLatex(File file) throws IOException {
        writeLatex(file, buildLaplacianMatrix(), nodes, "Laplacian Matrix $L = D - A$");
    }

    /**
     * Exports the incidence matrix as a LaTeX bmatrix.
     */
    public void exportIncidenceLatex(File file) throws IOException {
        int[][] matrix = buildIncidenceMatrix();
        List<String> colLabels = new ArrayList<>();
        for (int i = 0; i < edges.size(); i++) {
            colLabels.add("e_{" + i + "}");
        }
        writeRectLatex(file, matrix, nodes, colLabels, "Incidence Matrix $B$");
    }

    /**
     * Returns the adjacency matrix as a LaTeX string.
     */
    public String adjacencyLatexToString() {
        return squareMatrixLatexToString(buildAdjacencyMatrix(), nodes, "Adjacency Matrix $A$");
    }

    /**
     * Returns the Laplacian matrix as a LaTeX string.
     */
    public String laplacianLatexToString() {
        return squareMatrixLatexToString(buildLaplacianMatrix(), nodes, "Laplacian Matrix $L = D - A$");
    }

    // ── Internal helpers ────────────────────────────────────────────

    private Map<String, Integer> buildIndex() {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            index.put(nodes.get(i), i);
        }
        return index;
    }

    private void writeSquareMatrixCsv(File file, int[][] matrix, List<String> labels) throws IOException {
        writeMatrixCsv(file, matrix, labels, labels);
    }

    private void writeMatrixCsv(File file, int[][] matrix, List<String> rowLabels, List<String> colLabels) throws IOException {
        ExportUtils.validateOutputPath(file);
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            pw.print(",");
            pw.println(String.join(",", colLabels));
            for (int i = 0; i < matrix.length; i++) {
                StringBuilder sb = new StringBuilder();
                sb.append(escapeCsv(rowLabels.get(i)));
                for (int j = 0; j < matrix[i].length; j++) {
                    sb.append(',').append(matrix[i][j]);
                }
                pw.println(sb);
            }
        }
    }

    private String squareMatrixCsvToString(int[][] matrix, List<String> labels) {
        StringBuilder sb = new StringBuilder();
        sb.append(',').append(String.join(",", labels)).append('\n');
        for (int i = 0; i < matrix.length; i++) {
            sb.append(escapeCsv(labels.get(i)));
            for (int j = 0; j < matrix[i].length; j++) {
                sb.append(',').append(matrix[i][j]);
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void writeLatex(File file, int[][] matrix, List<String> labels, String caption) throws IOException {
        ExportUtils.validateOutputPath(file);
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            pw.println(squareMatrixLatexToString(matrix, labels, caption));
        }
    }

    private String squareMatrixLatexToString(int[][] matrix, List<String> labels, String caption) {
        StringBuilder sb = new StringBuilder();
        sb.append("% ").append(caption).append('\n');
        sb.append("% Nodes: ").append(String.join(", ", labels)).append('\n');
        sb.append("\\[").append('\n');
        sb.append("\\begin{bmatrix}").append('\n');
        for (int i = 0; i < matrix.length; i++) {
            StringBuilder row = new StringBuilder();
            for (int j = 0; j < matrix[i].length; j++) {
                if (j > 0) row.append(" & ");
                row.append(matrix[i][j]);
            }
            row.append(" \\\\");
            sb.append(row).append('\n');
        }
        sb.append("\\end{bmatrix}").append('\n');
        sb.append("\\]").append('\n');
        return sb.toString();
    }

    private void writeRectLatex(File file, int[][] matrix, List<String> rowLabels,
                                 List<String> colLabels, String caption) throws IOException {
        ExportUtils.validateOutputPath(file);
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            pw.println("% " + caption);
            pw.println("% Rows (nodes): " + String.join(", ", rowLabels));
            pw.println("% Columns (edges): " + String.join(", ", colLabels));
            pw.println("\\[");
            pw.println("\\begin{bmatrix}");
            for (int i = 0; i < matrix.length; i++) {
                StringBuilder row = new StringBuilder();
                for (int j = 0; j < matrix[i].length; j++) {
                    if (j > 0) row.append(" & ");
                    row.append(matrix[i][j]);
                }
                row.append(" \\\\");
                pw.println(row);
            }
            pw.println("\\end{bmatrix}");
            pw.println("\\]");
        }
    }
}
