package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Parses a graph definition file (nodes + edges) into a JUNG graph and
 * classified edge lists.
 *
 * <p>Extracted from {@link Main#addGraph()} to separate file I/O and
 * parsing logic from Swing UI construction. This makes the parsing
 * independently testable and reusable (e.g. for headless analysis or
 * batch processing).
 *
 * <h3>File format</h3>
 * <pre>
 * nodes
 * A
 * B
 * C
 * edges
 * FR A B 1.5
 * CL B C 2.0
 * </pre>
 *
 * Each edge line: {@code <type_code> <vertex1> <vertex2> <weight>}
 */
public class GraphFileParser {

    private static final Logger LOGGER = Logger.getLogger(GraphFileParser.class.getName());

    /**
     * Result of parsing a graph file. Holds the graph, classified edge
     * lists, and the set of all vertices found.
     */
    public static class ParseResult {
        private final Graph<String, Edge> graph;
        private final Map<EdgeType, List<Edge>> edgesByType;
        private final Set<String> vertices;
        private final int skippedLines;

        ParseResult(Graph<String, Edge> graph,
                    Map<EdgeType, List<Edge>> edgesByType,
                    Set<String> vertices,
                    int skippedLines) {
            this.graph = graph;
            this.edgesByType = Collections.unmodifiableMap(edgesByType);
            this.vertices = Collections.unmodifiableSet(vertices);
            this.skippedLines = skippedLines;
        }

        /** The parsed JUNG graph (undirected, sparse). */
        public Graph<String, Edge> getGraph() { return graph; }

        /** Edges grouped by {@link EdgeType}. */
        public Map<EdgeType, List<Edge>> getEdgesByType() { return edgesByType; }

        /** Convenience accessor for a single edge type's list (never null). */
        public List<Edge> getEdges(EdgeType type) {
            return edgesByType.getOrDefault(type, Collections.emptyList());
        }

        /** All vertex identifiers found in the file. */
        public Set<String> getVertices() { return vertices; }

        /** Number of lines skipped due to parse errors. */
        public int getSkippedLines() { return skippedLines; }
    }

    /**
     * Parse a graph file into a {@link ParseResult}.
     *
     * @param filePath       path to the graph definition file
     * @param visibleFilter  predicate that returns {@code true} for edge type
     *                       codes that should be added to the graph (not just
     *                       classified). Pass {@code code -> true} to include all.
     * @return parsed result containing graph, edge lists, and vertices
     * @throws IOException if the file cannot be read
     */
    public static ParseResult parse(String filePath, Predicate<String> visibleFilter)
            throws IOException {

        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        Map<EdgeType, List<Edge>> edgesByType = new EnumMap<>(EdgeType.class);
        for (EdgeType t : EdgeType.values()) {
            edgesByType.put(t, new ArrayList<>());
        }
        Set<String> vertices = new LinkedHashSet<>();
        int skipped = 0;

        File database = new File(filePath);
        LineIterator lineIterator = null;

        try {
            lineIterator = FileUtils.lineIterator(database);
            int section = -1; // 0 = nodes, 1 = edges

            while (lineIterator.hasNext()) {
                String line = lineIterator.nextLine().trim();
                if (line.isEmpty()) continue;

                if (line.equalsIgnoreCase("nodes")) {
                    section = 0;
                    continue;
                }
                if (line.equalsIgnoreCase("edges")) {
                    section = 1;
                    continue;
                }

                if (section == 0) {
                    // Node line
                    String[] parts = line.split("\\s+");
                    if (parts.length < 1 || parts[0].isEmpty()) {
                        skipped++;
                        continue;
                    }
                    String vertex = parts[0];
                    g.addVertex(vertex);
                    vertices.add(vertex);

                } else if (section == 1) {
                    // Edge line: <type> <v1> <v2> <weight>
                    String[] parts = line.split("\\s+");
                    if (parts.length < 4) {
                        LOGGER.warning("Skipping malformed edge line: " + line);
                        skipped++;
                        continue;
                    }

                    float weight;
                    try {
                        weight = Float.parseFloat(parts[3]);
                    } catch (NumberFormatException e) {
                        LOGGER.warning("Skipping edge with invalid weight: " + line);
                        skipped++;
                        continue;
                    }
                    if (Float.isNaN(weight) || Float.isInfinite(weight)) {
                        LOGGER.warning("Skipping edge with non-finite weight: " + line);
                        skipped++;
                        continue;
                    }

                    edge curEdge = new Edge(parts[0], parts[1], parts[2]);
                    curEdge.setWeight(weight);

                    // Classify by type
                    EdgeType edgeType = EdgeType.fromCode(parts[0]);
                    if (edgeType != null) {
                        List<Edge> typeList = edgesByType.get(edgeType);
                        // Set label on first edge of each type for the legend
                        if (typeList.stream().noneMatch(e -> e.getLabel() != null)) {
                            curEdge.setLabel(edgeType.getDisplayLabel());
                        }
                        typeList.add(curEdge);
                    }

                    // Only add to graph if this type is visible
                    if (visibleFilter.test(parts[0])) {
                        g.addEdge(curEdge, parts[1], parts[2]);
                    }
                }
            }
        } finally {
            LineIterator.closeQuietly(lineIterator);
        }

        return new ParseResult(g, edgesByType, vertices, skipped);
    }

    /**
     * Convenience overload that includes all edge types.
     */
    public static ParseResult parse(String filePath) throws IOException {
        return parse(filePath, code -> true);
    }
}
