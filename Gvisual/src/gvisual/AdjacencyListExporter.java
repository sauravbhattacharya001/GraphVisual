package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Exports a JUNG graph as adjacency lists in multiple formats for use in
 * external tools and programming languages.
 *
 * <h3>Supported Formats</h3>
 * <ul>
 *   <li><b>Plain text</b> — simple {@code node: neighbor1 neighbor2 ...} format</li>
 *   <li><b>Python</b> — dict literal compatible with NetworkX's
 *       {@code nx.from_dict_of_lists()}</li>
 *   <li><b>MATLAB</b> — sparse adjacency matrix construction script</li>
 *   <li><b>Mathematica</b> — {@code Graph[{...}]} expression</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 *   AdjacencyListExporter exporter = new AdjacencyListExporter(graph);
 *   exporter.exportAll(new File("graph_adj"));
 *   // creates graph_adj.txt, graph_adj.py, graph_adj.m, graph_adj.wl
 * </pre></p>
 *
 * @author zalenix
 */
public class AdjacencyListExporter {

    private final Graph<String, Edge> graph;
    private final Map<String, List<String>> adjacencyMap;

    /**
     * Creates a new exporter for the given graph.
     *
     * @param graph the JUNG graph to export
     */
    public AdjacencyListExporter(Graph<String, Edge> graph) {
        this.graph = Objects.requireNonNull(graph, "graph must not be null");
        this.adjacencyMap = buildAdjacencyMap();
    }

    private Map<String, List<String>> buildAdjacencyMap() {
        Map<String, List<String>> map = new TreeMap<>();
        for (String vertex : graph.getVertices()) {
            List<String> neighbors = new ArrayList<>(graph.getNeighbors(vertex));
            Collections.sort(neighbors);
            map.put(vertex, neighbors);
        }
        return map;
    }

    /**
     * Exports all four formats to files sharing the given base name.
     *
     * @param baseFile file whose name (without extension) is used as the base
     * @return summary of exported files
     * @throws IOException if any write fails
     */
    public String exportAll(File baseFile) throws IOException {
        String base = baseFile.getAbsolutePath().replaceAll("\\.[^.]+$", "");

        File txtFile = new File(base + ".txt");
        File pyFile = new File(base + ".py");
        File mFile = new File(base + ".m");
        File wlFile = new File(base + ".wl");

        exportPlainText(txtFile);
        exportPython(pyFile);
        exportMatlab(mFile);
        exportMathematica(wlFile);

        return String.format(
                "Adjacency list exported in 4 formats:%n"
                        + "  Plain text: %s%n"
                        + "  Python:     %s%n"
                        + "  MATLAB:     %s%n"
                        + "  Mathematica:%s%n"
                        + "Nodes: %d, Edges: %d",
                txtFile.getName(), pyFile.getName(),
                mFile.getName(), wlFile.getName(),
                graph.getVertexCount(), graph.getEdgeCount());
    }

    /**
     * Exports as plain text adjacency list.
     * Format: {@code node: neighbor1 neighbor2 neighbor3}
     */
    public void exportPlainText(File outFile) throws IOException {
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))) {
            pw.println("# Adjacency List — GraphVisual Export");
            pw.println("# Nodes: " + graph.getVertexCount()
                    + "  Edges: " + graph.getEdgeCount());
            pw.println("#");
            for (Map.Entry<String, List<String>> entry : adjacencyMap.entrySet()) {
                pw.println(entry.getKey() + ": "
                        + String.join(" ", entry.getValue()));
            }
        }
    }

    /**
     * Exports as a Python dictionary literal.
     * The output can be loaded with {@code nx.from_dict_of_lists(eval(open(...).read()))}.
     */
    public void exportPython(File outFile) throws IOException {
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))) {
            pw.println("# Adjacency list — GraphVisual Export");
            pw.println("# Usage: import networkx as nx");
            pw.println("#        G = nx.from_dict_of_lists(adj)");
            pw.println();
            pw.println("adj = {");
            int i = 0;
            int size = adjacencyMap.size();
            for (Map.Entry<String, List<String>> entry : adjacencyMap.entrySet()) {
                String neighbors = entry.getValue().stream()
                        .map(n -> "\"" + escapeStr(n) + "\"")
                        .collect(Collectors.joining(", "));
                String comma = (++i < size) ? "," : "";
                pw.println("    \"" + escapeStr(entry.getKey()) + "\": ["
                        + neighbors + "]" + comma);
            }
            pw.println("}");
        }
    }

    /**
     * Exports as a MATLAB script that builds a sparse adjacency matrix.
     */
    public void exportMatlab(File outFile) throws IOException {
        // Map node names to 1-based indices
        List<String> nodes = new ArrayList<>(adjacencyMap.keySet());
        Map<String, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            indexMap.put(nodes.get(i), i + 1);
        }

        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))) {
            pw.println("% Adjacency matrix — GraphVisual Export");
            pw.println("% Nodes: " + nodes.size()
                    + "  Edges: " + graph.getEdgeCount());
            pw.println();

            // Node name mapping
            pw.println("% Node index mapping:");
            for (int i = 0; i < nodes.size(); i++) {
                pw.println("%   " + (i + 1) + " = " + nodes.get(i));
            }
            pw.println();

            int n = nodes.size();
            pw.println("n = " + n + ";");
            pw.println("A = sparse(n, n);");
            pw.println();

            for (Map.Entry<String, List<String>> entry : adjacencyMap.entrySet()) {
                int from = indexMap.get(entry.getKey());
                for (String neighbor : entry.getValue()) {
                    int to = indexMap.get(neighbor);
                    if (from <= to) { // avoid duplicate edges for undirected
                        pw.println("A(" + from + ", " + to + ") = 1; "
                                + "A(" + to + ", " + from + ") = 1;");
                    }
                }
            }
            pw.println();
            pw.println("% Visualize: spy(A); or G = graph(A); plot(G);");
        }
    }

    /**
     * Exports as a Mathematica Graph expression.
     */
    public void exportMathematica(File outFile) throws IOException {
        Set<String> seen = new HashSet<>();
        List<String> edges = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : adjacencyMap.entrySet()) {
            String from = entry.getKey();
            for (String to : entry.getValue()) {
                String key = from.compareTo(to) < 0
                        ? from + "|" + to : to + "|" + from;
                if (seen.add(key)) {
                    edges.add("\"" + escapeStr(from) + "\" \\[UndirectedEdge] \""
                            + escapeStr(to) + "\"");
                }
            }
        }

        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))) {
            pw.println("(* Adjacency list — GraphVisual Export *)");
            pw.println("(* Nodes: " + graph.getVertexCount()
                    + "  Edges: " + graph.getEdgeCount() + " *)");
            pw.println();
            pw.println("g = Graph[{");
            for (int i = 0; i < edges.size(); i++) {
                String comma = (i < edges.size() - 1) ? "," : "";
                pw.println("  " + edges.get(i) + comma);
            }
            pw.println("}, VertexLabels -> \"Name\"]");
        }
    }

    /**
     * Returns the number of nodes in the graph.
     */
    public int getNodeCount() {
        return graph.getVertexCount();
    }

    /**
     * Returns the number of edges in the graph.
     */
    public int getEdgeCount() {
        return graph.getEdgeCount();
    }

    private static String escapeStr(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
