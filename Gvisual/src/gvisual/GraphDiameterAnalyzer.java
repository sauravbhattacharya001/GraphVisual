package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Analyzes graph diameter, radius, eccentricity, center, and periphery.
 *
 * <p>For each vertex, computes the <b>eccentricity</b> — the maximum shortest-path
 * distance (hop count) to any other reachable vertex. From eccentricities:</p>
 * <ul>
 *   <li><b>Diameter</b> — maximum eccentricity (longest shortest path in the graph)</li>
 *   <li><b>Radius</b> — minimum eccentricity</li>
 *   <li><b>Center</b> — set of vertices whose eccentricity equals the radius</li>
 *   <li><b>Periphery</b> — set of vertices whose eccentricity equals the diameter</li>
 * </ul>
 *
 * <p>Operates on the largest connected component when the graph is disconnected.
 * Useful for understanding the overall spread and identifying structurally
 * important nodes in social/IMEI networks.</p>
 *
 * @author zalenix
 */
public class GraphDiameterAnalyzer {

    private final Graph<String, edge> graph;
    private Map<String, Integer> eccentricities;
    private int diameter;
    private int radius;
    private Set<String> centerVertices;
    private Set<String> peripheryVertices;
    private Set<String> largestComponent;
    private boolean computed;

    /**
     * Creates a new GraphDiameterAnalyzer for the given graph.
     *
     * @param graph the JUNG graph to analyze
     * @throws IllegalArgumentException if graph is null
     */
    public GraphDiameterAnalyzer(Graph<String, edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
        this.computed = false;
    }

    /**
     * Runs the analysis. Must be called before querying results.
     */
    public void analyze() {
        eccentricities = new LinkedHashMap<String, Integer>();
        centerVertices = new LinkedHashSet<String>();
        peripheryVertices = new LinkedHashSet<String>();
        diameter = 0;
        radius = Integer.MAX_VALUE;

        if (graph.getVertexCount() == 0) {
            radius = 0;
            largestComponent = Collections.emptySet();
            computed = true;
            return;
        }

        // Find the largest connected component
        largestComponent = findLargestComponent();

        if (largestComponent.size() <= 1) {
            for (String v : largestComponent) {
                eccentricities.put(v, 0);
                centerVertices.add(v);
                peripheryVertices.add(v);
            }
            diameter = 0;
            radius = 0;
            computed = true;
            return;
        }

        // Compute eccentricity for each vertex in the largest component
        for (String vertex : largestComponent) {
            int ecc = computeEccentricity(vertex, largestComponent);
            eccentricities.put(vertex, ecc);
            if (ecc > diameter) diameter = ecc;
            if (ecc < radius) radius = ecc;
        }

        // Identify center and periphery
        for (Map.Entry<String, Integer> entry : eccentricities.entrySet()) {
            if (entry.getValue() == radius) {
                centerVertices.add(entry.getKey());
            }
            if (entry.getValue() == diameter) {
                peripheryVertices.add(entry.getKey());
            }
        }

        computed = true;
    }

    /**
     * Returns the diameter (max eccentricity) of the graph.
     */
    public int getDiameter() {
        ensureComputed();
        return diameter;
    }

    /**
     * Returns the radius (min eccentricity) of the graph.
     */
    public int getRadius() {
        ensureComputed();
        return radius;
    }

    /**
     * Returns the eccentricity map for all vertices in the largest component.
     */
    public Map<String, Integer> getEccentricities() {
        ensureComputed();
        return Collections.unmodifiableMap(eccentricities);
    }

    /**
     * Returns the eccentricity of a specific vertex.
     *
     * @param vertex the vertex ID
     * @return eccentricity value, or -1 if vertex is not in the largest component
     */
    public int getEccentricity(String vertex) {
        ensureComputed();
        Integer ecc = eccentricities.get(vertex);
        return ecc != null ? ecc : -1;
    }

    /**
     * Returns the center vertices (eccentricity == radius).
     */
    public Set<String> getCenterVertices() {
        ensureComputed();
        return Collections.unmodifiableSet(centerVertices);
    }

    /**
     * Returns the periphery vertices (eccentricity == diameter).
     */
    public Set<String> getPeripheryVertices() {
        ensureComputed();
        return Collections.unmodifiableSet(peripheryVertices);
    }

    /**
     * Returns the size of the largest connected component analyzed.
     */
    public int getLargestComponentSize() {
        ensureComputed();
        return largestComponent.size();
    }

    /**
     * Returns the vertices in the largest connected component.
     */
    public Set<String> getLargestComponent() {
        ensureComputed();
        return Collections.unmodifiableSet(largestComponent);
    }

    /**
     * Returns vertices sorted by eccentricity (ascending — most central first).
     */
    public List<Map.Entry<String, Integer>> getRankedByEccentricity() {
        ensureComputed();
        List<Map.Entry<String, Integer>> entries =
                new ArrayList<Map.Entry<String, Integer>>(eccentricities.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return Integer.compare(a.getValue(), b.getValue());
            }
        });
        return entries;
    }

    /**
     * Returns a human-readable summary of the diameter analysis.
     */
    public String getSummary() {
        ensureComputed();
        StringBuilder sb = new StringBuilder();
        sb.append("=== Graph Diameter Analysis ===\n");
        sb.append(String.format("Largest component: %d vertices (of %d total)\n",
                largestComponent.size(), graph.getVertexCount()));
        sb.append(String.format("Diameter: %d\n", diameter));
        sb.append(String.format("Radius: %d\n", radius));
        sb.append(String.format("Center vertices (%d): %s\n",
                centerVertices.size(), formatSet(centerVertices, 10)));
        sb.append(String.format("Periphery vertices (%d): %s\n",
                peripheryVertices.size(), formatSet(peripheryVertices, 10)));
        return sb.toString();
    }

    // --- Private helpers ---

    private int computeEccentricity(String source, Set<String> component) {
        Map<String, Integer> distances = GraphUtils.bfsDistances(graph, source);
        int maxDist = 0;
        for (String v : component) {
            Integer d = distances.get(v);
            if (d != null && d > maxDist) {
                maxDist = d;
            }
        }
        return maxDist;
    }

    private Set<String> findLargestComponent() {
        return GraphUtils.findLargestComponent(graph);
    }

    private String getOtherEnd(edge e, String current) {
        return GraphUtils.getOtherEnd(e, current);
    }

    private String formatSet(Set<String> set, int max) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String s : set) {
            if (count > 0) sb.append(", ");
            if (count >= max) {
                sb.append("... (").append(set.size() - max).append(" more)");
                break;
            }
            sb.append(s);
            count++;
        }
        return sb.toString();
    }

    private void ensureComputed() {
        if (!computed) {
            throw new IllegalStateException("Call analyze() before querying results");
        }
    }
}
