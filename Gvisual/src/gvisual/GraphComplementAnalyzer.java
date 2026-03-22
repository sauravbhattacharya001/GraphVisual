package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.*;

/**
 * Computes the <b>complement graph</b> of a given graph and provides comparative
 * analysis between the original and its complement.
 *
 * <p>The complement G' of a graph G has the same vertices, but an Edge exists in G'
 * if and only if it does <em>not</em> exist in G. This is useful for understanding
 * graph density, identifying missing connections, and studying structural properties
 * that become apparent when relationships are inverted.</p>
 *
 * <h3>Features</h3>
 * <ul>
 *   <li>Build the complement graph as a new JUNG UndirectedSparseGraph</li>
 *   <li>Compare Edge counts, density, and degree distributions</li>
 *   <li>Identify vertices whose degree changes most dramatically</li>
 *   <li>Check self-complementarity (isomorphism with complement)</li>
 *   <li>Export a textual comparison report</li>
 * </ul>
 *
 * @author zalenix
 */
public final class GraphComplementAnalyzer {

    private GraphComplementAnalyzer() { /* utility class */ }

    /**
     * Builds the complement of the given graph.
     *
     * @param graph the original graph
     * @return a new graph containing all edges not present in the original
     */
    public static Graph<String, Edge> buildComplement(Graph<String, Edge> graph) {
        UndirectedSparseGraph<String, Edge> complement = new UndirectedSparseGraph<>();
        List<String> vertices = new ArrayList<>(graph.getVertices());

        for (String v : vertices) {
            complement.addVertex(v);
        }

        Set<String> existingEdges = new HashSet<>();
        for (Edge e : graph.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            existingEdges.add(edgeKey(v1, v2));
        }

        int edgeId = 0;
        for (int i = 0; i < vertices.size(); i++) {
            for (int j = i + 1; j < vertices.size(); j++) {
                String v1 = vertices.get(i);
                String v2 = vertices.get(j);
                if (!existingEdges.contains(edgeKey(v1, v2))) {
                    Edge e = new Edge(v1, v2, "complement_" + edgeId++);
                    complement.addEdge(e, v1, v2);
                }
            }
        }

        return complement;
    }

    /**
     * Generates a comparative analysis report between the original graph
     * and its complement.
     *
     * @param graph the original graph
     * @return a formatted analysis report string
     */
    public static String analyze(Graph<String, Edge> graph) {
        Graph<String, Edge> complement = buildComplement(graph);
        int n = graph.getVertexCount();
        int origEdges = graph.getEdgeCount();
        int compEdges = complement.getEdgeCount();
        int maxEdges = n * (n - 1) / 2;

        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════\n");
        sb.append("       GRAPH COMPLEMENT ANALYSIS\n");
        sb.append("═══════════════════════════════════════════\n\n");

        sb.append("Vertices:              ").append(n).append("\n");
        sb.append("Max possible edges:    ").append(maxEdges).append("\n\n");

        sb.append("── Original Graph ─────────────────────────\n");
        sb.append("  Edges:               ").append(origEdges).append("\n");
        sb.append(String.format("  Density:             %.4f%n", density(origEdges, n)));
        sb.append(String.format("  Avg degree:          %.2f%n", avgDegree(graph)));
        sb.append("\n");

        sb.append("── Complement Graph ───────────────────────\n");
        sb.append("  Edges:               ").append(compEdges).append("\n");
        sb.append(String.format("  Density:             %.4f%n", density(compEdges, n)));
        sb.append(String.format("  Avg degree:          %.2f%n", avgDegree(complement)));
        sb.append("\n");

        // Verify Edge counts sum correctly
        sb.append("── Validation ─────────────────────────────\n");
        sb.append("  Orig + Complement:   ").append(origEdges + compEdges).append("\n");
        sb.append("  Expected (n*(n-1)/2):").append(maxEdges).append("\n");
        sb.append("  Valid:               ").append(origEdges + compEdges == maxEdges ? "✓" : "✗").append("\n\n");

        // Self-complementary check (quick heuristic: Edge count must equal n*(n-1)/4)
        boolean couldBeSelfComplementary = (maxEdges % 2 == 0) && (origEdges == maxEdges / 2);
        sb.append("── Self-Complementary ─────────────────────\n");
        sb.append("  Edge-count test:     ").append(couldBeSelfComplementary ? "PASS (possible)" : "FAIL").append("\n");
        if (couldBeSelfComplementary) {
            sb.append("  (Full isomorphism check not performed — Edge count is necessary but not sufficient)\n");
        }
        sb.append("\n");

        // Top degree changes
        sb.append("── Largest Degree Changes ─────────────────\n");
        List<DegreeChange> changes = computeDegreeChanges(graph, complement);
        changes.sort((a, b) -> Integer.compare(b.absDelta, a.absDelta));
        int show = Math.min(10, changes.size());
        sb.append(String.format("  %-20s %8s %8s %8s%n", "Vertex", "Original", "Compl.", "Delta"));
        sb.append("  ").append("-".repeat(48)).append("\n");
        for (int i = 0; i < show; i++) {
            DegreeChange dc = changes.get(i);
            sb.append(String.format("  %-20s %8d %8d %+8d%n",
                    truncate(dc.vertex, 20), dc.origDeg, dc.compDeg, dc.compDeg - dc.origDeg));
        }
        sb.append("\n");

        // Isolated vertices analysis
        long origIsolated = graph.getVertices().stream().filter(v -> graph.degree(v) == 0).count();
        long compIsolated = complement.getVertices().stream().filter(v -> complement.degree(v) == 0).count();
        sb.append("── Isolated Vertices ──────────────────────\n");
        sb.append("  In original:         ").append(origIsolated).append("\n");
        sb.append("  In complement:       ").append(compIsolated).append("\n");
        sb.append("  (Isolated in complement = universal vertices in original)\n");

        return sb.toString();
    }

    /**
     * Returns the complement graph's Edge list as a list of string pairs.
     *
     * @param graph the original graph
     * @return list of [vertex1, vertex2] arrays representing complement edges
     */
    public static List<String[]> getComplementEdgeList(Graph<String, Edge> graph) {
        Graph<String, Edge> complement = buildComplement(graph);
        List<String[]> result = new ArrayList<>();
        for (Edge e : complement.getEdges()) {
            result.add(new String[]{e.getVertex1(), e.getVertex2()});
        }
        return result;
    }

    // ── Internal helpers ──────────────────────────────────────

    private static String edgeKey(String v1, String v2) {
        return v1.compareTo(v2) < 0 ? v1 + "|" + v2 : v2 + "|" + v1;
    }

    private static double density(int edges, int vertices) {
        if (vertices < 2) return 0.0;
        return (2.0 * edges) / (vertices * (vertices - 1));
    }

    private static double avgDegree(Graph<String, Edge> g) {
        if (g.getVertexCount() == 0) return 0.0;
        double sum = 0;
        for (String v : g.getVertices()) {
            sum += g.degree(v);
        }
        return sum / g.getVertexCount();
    }

    private static List<DegreeChange> computeDegreeChanges(
            Graph<String, Edge> orig, Graph<String, Edge> comp) {
        List<DegreeChange> list = new ArrayList<>();
        for (String v : orig.getVertices()) {
            int od = orig.degree(v);
            int cd = comp.degree(v);
            list.add(new DegreeChange(v, od, cd));
        }
        return list;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static final class DegreeChange {
        final String vertex;
        final int origDeg;
        final int compDeg;
        final int absDelta;

        DegreeChange(String vertex, int origDeg, int compDeg) {
            this.vertex = vertex;
            this.origDeg = origDeg;
            this.compDeg = compDeg;
            this.absDelta = Math.abs(compDeg - origDeg);
        }
    }
}
