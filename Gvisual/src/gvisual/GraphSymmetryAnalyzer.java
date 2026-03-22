package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Analyses the symmetry structure of a graph by computing automorphism orbits,
 * transitivity properties, and symmetry metrics.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Color refinement (1-WL)</b> — Weisfeiler–Leman vertex classification
 *       producing equivalence classes that are a superset of true orbits</li>
 *   <li><b>Vertex orbits</b> — groups of vertices that can be mapped to each
 *       other by an automorphism (approximated via refinement)</li>
 *   <li><b>Edge orbits</b> — groups of edges whose endpoint orbit-pairs match</li>
 *   <li><b>Vertex-transitivity test</b> — whether all vertices share one orbit</li>
 *   <li><b>Edge-transitivity test</b> — whether all edges share one orbit</li>
 *   <li><b>Symmetry factor</b> — 1/|orbits|, ranges from 1 (fully symmetric)
 *       to 1/n (fully asymmetric)</li>
 *   <li><b>Asymmetric vertices</b> — vertices in singleton orbits (fixed by
 *       every automorphism)</li>
 *   <li><b>Orbit size distribution</b> — histogram of orbit sizes</li>
 *   <li><b>Distinguishing number</b> — minimum colours to break all symmetry
 *       (lower bound: max orbit size)</li>
 *   <li><b>Text report</b> — human-readable symmetry summary</li>
 * </ul>
 *
 * <p>The implementation uses iterative colour refinement (1-dimensional
 * Weisfeiler–Leman algorithm).  Vertices start with the same colour; each
 * round replaces a vertex's colour with a hash of its current colour and the
 * sorted multiset of its neighbours' colours.  Iteration stops when the
 * partition stabilises.  The resulting equivalence classes are a conservative
 * approximation of automorphism orbits — they never merge truly
 * non-equivalent vertices, but may fail to distinguish some equivalent
 * vertices in highly regular graphs.</p>
 *
 * @author zalenix
 */
public class GraphSymmetryAnalyzer {

    private final Graph<String, Edge> graph;

    /** Lazily computed colour-refinement partition (vertex → orbit id). */
    private Map<String, Integer> vertexOrbitMap;
    /** Lazily computed orbit groups. */
    private List<Set<String>> orbits;
    /** Lazily computed Edge orbits. */
    private List<Set<Edge>> edgeOrbits;

    public GraphSymmetryAnalyzer(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
    }

    // ------------------------------------------------------------------
    //  Core: Colour Refinement (1-WL)
    // ------------------------------------------------------------------

    /**
     * Runs the Weisfeiler–Leman colour refinement algorithm.
     * Returns a map from vertex to its stable colour (orbit id).
     */
    private Map<String, Integer> colourRefinement() {
        if (graph.getVertexCount() == 0) {
            return Collections.emptyMap();
        }

        // Initial colouring: all vertices same colour based on degree
        Map<String, Integer> colours = new HashMap<>();
        for (String v : graph.getVertices()) {
            colours.put(v, graph.degree(v));
        }

        boolean changed = true;
        int maxIterations = graph.getVertexCount() + 1; // guaranteed to converge
        int iteration = 0;

        while (changed && iteration < maxIterations) {
            changed = false;
            iteration++;

            // Build new colour from (current colour, sorted neighbour colour multiset)
            Map<String, List<Integer>> signatures = new HashMap<>();
            for (String v : graph.getVertices()) {
                List<Integer> sig = new ArrayList<>();
                sig.add(colours.get(v));
                List<Integer> neighbourColours = new ArrayList<>();
                for (String n : graph.getNeighbors(v)) {
                    neighbourColours.add(colours.get(n));
                }
                Collections.sort(neighbourColours);
                sig.addAll(neighbourColours);
                signatures.put(v, sig);
            }

            // Assign new colours by unique signature
            Map<List<Integer>, Integer> signatureToColour = new HashMap<>();
            Map<String, Integer> newColours = new HashMap<>();
            int nextColour = 0;
            // Sort vertices for deterministic colouring
            List<String> sortedVertices = new ArrayList<>(graph.getVertices());
            Collections.sort(sortedVertices);
            for (String v : sortedVertices) {
                List<Integer> sig = signatures.get(v);
                if (!signatureToColour.containsKey(sig)) {
                    signatureToColour.put(sig, nextColour++);
                }
                newColours.put(v, signatureToColour.get(sig));
            }

            // Check if partition changed
            if (!newColours.equals(colours)) {
                changed = true;
            }
            colours = newColours;
        }

        // Normalise colours to 0-based contiguous ids
        Map<Integer, Integer> remap = new HashMap<>();
        Map<String, Integer> result = new LinkedHashMap<>();
        int id = 0;
        List<String> sorted = new ArrayList<>(colours.keySet());
        Collections.sort(sorted);
        for (String v : sorted) {
            int c = colours.get(v);
            if (!remap.containsKey(c)) {
                remap.put(c, id++);
            }
            result.put(v, remap.get(c));
        }
        return result;
    }

    // ------------------------------------------------------------------
    //  Lazy initialisation
    // ------------------------------------------------------------------

    private void ensureComputed() {
        if (vertexOrbitMap != null) return;
        vertexOrbitMap = colourRefinement();

        // Build orbit groups
        Map<Integer, Set<String>> groups = new TreeMap<>();
        for (Map.Entry<String, Integer> e : vertexOrbitMap.entrySet()) {
            groups.computeIfAbsent(e.getValue(), k -> new LinkedHashSet<>()).add(e.getKey());
        }
        orbits = new ArrayList<>(groups.values());

        // Build Edge orbits: two edges are equivalent if their endpoint
        // orbit-pair (sorted) is identical
        Map<List<Integer>, Set<Edge>> edgeGroups = new LinkedHashMap<>();
        for (Edge e : graph.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            int o1 = vertexOrbitMap.getOrDefault(v1, -1);
            int o2 = vertexOrbitMap.getOrDefault(v2, -1);
            List<Integer> key = o1 <= o2
                    ? Arrays.asList(o1, o2)
                    : Arrays.asList(o2, o1);
            edgeGroups.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(e);
        }
        edgeOrbits = new ArrayList<>(edgeGroups.values());
    }

    // ------------------------------------------------------------------
    //  Vertex orbits
    // ------------------------------------------------------------------

    /**
     * Returns a mapping from each vertex to its orbit index (0-based).
     */
    public Map<String, Integer> getVertexOrbitMap() {
        ensureComputed();
        return Collections.unmodifiableMap(vertexOrbitMap);
    }

    /**
     * Returns the list of vertex orbits. Each set contains vertices that
     * are structurally equivalent under colour refinement.
     */
    public List<Set<String>> getVertexOrbits() {
        ensureComputed();
        return Collections.unmodifiableList(orbits);
    }

    /**
     * Returns the number of distinct vertex orbits.
     */
    public int getVertexOrbitCount() {
        ensureComputed();
        return orbits.size();
    }

    /**
     * Returns the orbit (set of vertices) that contains the given vertex.
     *
     * @throws IllegalArgumentException if vertex is not in the graph
     */
    public Set<String> getOrbitOf(String vertex) {
        if (vertex == null || !graph.containsVertex(vertex)) {
            throw new IllegalArgumentException("Vertex not in graph: " + vertex);
        }
        ensureComputed();
        int orbitId = vertexOrbitMap.get(vertex);
        return Collections.unmodifiableSet(orbits.get(orbitId));
    }

    // ------------------------------------------------------------------
    //  Edge orbits
    // ------------------------------------------------------------------

    /**
     * Returns the list of Edge orbits. Edges whose endpoint orbit-pairs
     * match are grouped together.
     */
    public List<Set<Edge>> getEdgeOrbits() {
        ensureComputed();
        return Collections.unmodifiableList(edgeOrbits);
    }

    /**
     * Returns the number of distinct Edge orbits.
     */
    public int getEdgeOrbitCount() {
        ensureComputed();
        return edgeOrbits.size();
    }

    // ------------------------------------------------------------------
    //  Transitivity
    // ------------------------------------------------------------------

    /**
     * A graph is vertex-transitive if all vertices belong to a single orbit.
     */
    public boolean isVertexTransitive() {
        if (graph.getVertexCount() == 0) return true;
        ensureComputed();
        return orbits.size() == 1;
    }

    /**
     * A graph is Edge-transitive if all edges belong to a single orbit.
     */
    public boolean isEdgeTransitive() {
        if (graph.getEdgeCount() == 0) return true;
        ensureComputed();
        return edgeOrbits.size() == 1;
    }

    /**
     * A graph is symmetric (arc-transitive) if it is both vertex-transitive
     * and Edge-transitive. This is a necessary (not sufficient) condition —
     * colour refinement may over-approximate.
     */
    public boolean isSymmetric() {
        return isVertexTransitive() && isEdgeTransitive();
    }

    // ------------------------------------------------------------------
    //  Symmetry metrics
    // ------------------------------------------------------------------

    /**
     * Symmetry factor: 1 / |vertex orbits|.
     * Ranges from 1.0 (single orbit, fully symmetric) to 1/n (every vertex
     * unique, fully asymmetric). Returns 1.0 for empty graphs.
     */
    public double getSymmetryFactor() {
        if (graph.getVertexCount() == 0) return 1.0;
        ensureComputed();
        return 1.0 / orbits.size();
    }

    /**
     * Fraction of vertices that are in non-singleton orbits.
     * 1.0 = every vertex has a symmetric partner; 0.0 = all vertices are
     * unique (or graph is empty).
     */
    public double getSymmetricVertexFraction() {
        if (graph.getVertexCount() == 0) return 0.0;
        ensureComputed();
        long symmetric = orbits.stream()
                .filter(o -> o.size() > 1)
                .mapToLong(Set::size)
                .sum();
        return (double) symmetric / graph.getVertexCount();
    }

    /**
     * Returns the set of asymmetric vertices — those in singleton orbits,
     * meaning they are fixed by every automorphism.
     */
    public Set<String> getAsymmetricVertices() {
        ensureComputed();
        Set<String> result = new LinkedHashSet<>();
        for (Set<String> orbit : orbits) {
            if (orbit.size() == 1) {
                result.addAll(orbit);
            }
        }
        return result;
    }

    /**
     * Orbit size distribution: maps orbit size → count of orbits with that size.
     */
    public Map<Integer, Integer> getOrbitSizeDistribution() {
        ensureComputed();
        Map<Integer, Integer> dist = new TreeMap<>();
        for (Set<String> orbit : orbits) {
            dist.merge(orbit.size(), 1, Integer::sum);
        }
        return dist;
    }

    /**
     * Lower bound on the distinguishing number — the minimum number of
     * colours needed so that the only colour-preserving automorphism is
     * the identity. The maximum orbit size is a lower bound.
     */
    public int getDistinguishingNumberLowerBound() {
        ensureComputed();
        return orbits.stream()
                .mapToInt(Set::size)
                .max()
                .orElse(1);
    }

    /**
     * Returns the size of the largest vertex orbit.
     */
    public int getLargestOrbitSize() {
        ensureComputed();
        return orbits.stream()
                .mapToInt(Set::size)
                .max()
                .orElse(0);
    }

    /**
     * Returns the size of the smallest vertex orbit.
     */
    public int getSmallestOrbitSize() {
        ensureComputed();
        return orbits.stream()
                .mapToInt(Set::size)
                .min()
                .orElse(0);
    }

    // ------------------------------------------------------------------
    //  Fixed-point detection
    // ------------------------------------------------------------------

    /**
     * Returns vertices that are in singleton orbits — they are structurally
     * unique and must be fixed by every automorphism.
     */
    public List<String> getFixedPoints() {
        ensureComputed();
        List<String> fixed = new ArrayList<>();
        for (Set<String> orbit : orbits) {
            if (orbit.size() == 1) {
                fixed.addAll(orbit);
            }
        }
        Collections.sort(fixed);
        return fixed;
    }

    /**
     * Returns true if the graph is asymmetric — every vertex is in its own
     * orbit, meaning the only automorphism is the identity.
     */
    public boolean isAsymmetric() {
        if (graph.getVertexCount() == 0) return true;
        ensureComputed();
        return orbits.size() == graph.getVertexCount();
    }

    // ------------------------------------------------------------------
    //  Report
    // ------------------------------------------------------------------

    /**
     * Generates a human-readable symmetry report.
     */
    public String generateReport() {
        ensureComputed();
        StringBuilder sb = new StringBuilder();
        sb.append("=== Graph Symmetry Report ===\n\n");
        sb.append(String.format("Vertices: %d%n", graph.getVertexCount()));
        sb.append(String.format("Edges: %d%n", graph.getEdgeCount()));
        sb.append(String.format("Vertex orbits: %d%n", orbits.size()));
        sb.append(String.format("Edge orbits: %d%n", edgeOrbits.size()));
        sb.append(String.format("Symmetry factor: %.4f%n", getSymmetryFactor()));
        sb.append(String.format("Symmetric vertex fraction: %.4f%n",
                getSymmetricVertexFraction()));
        sb.append(String.format("Vertex-transitive: %s%n", isVertexTransitive()));
        sb.append(String.format("Edge-transitive: %s%n", isEdgeTransitive()));
        sb.append(String.format("Symmetric (arc-transitive): %s%n", isSymmetric()));
        sb.append(String.format("Asymmetric: %s%n", isAsymmetric()));
        sb.append(String.format("Largest orbit: %d%n", getLargestOrbitSize()));
        sb.append(String.format("Smallest orbit: %d%n", getSmallestOrbitSize()));
        sb.append(String.format("Fixed points: %d%n", getFixedPoints().size()));
        sb.append(String.format("Distinguishing number (lower bound): %d%n",
                getDistinguishingNumberLowerBound()));

        sb.append("\nOrbit size distribution:\n");
        for (Map.Entry<Integer, Integer> e : getOrbitSizeDistribution().entrySet()) {
            sb.append(String.format("  Size %d: %d orbit(s)%n", e.getKey(), e.getValue()));
        }

        sb.append("\nVertex orbits:\n");
        for (int i = 0; i < orbits.size(); i++) {
            Set<String> orbit = orbits.get(i);
            List<String> sorted = new ArrayList<>(orbit);
            Collections.sort(sorted);
            sb.append(String.format("  Orbit %d (size %d): %s%n",
                    i, orbit.size(), sorted));
        }

        if (!getFixedPoints().isEmpty()) {
            sb.append("\nFixed points (asymmetric vertices): ");
            sb.append(getFixedPoints());
            sb.append("\n");
        }

        return sb.toString();
    }
}
