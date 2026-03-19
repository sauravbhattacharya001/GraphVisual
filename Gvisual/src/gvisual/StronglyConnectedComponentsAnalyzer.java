package gvisual;

import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;

import java.util.*;

/**
 * Strongly Connected Components (SCC) analysis for directed graphs.
 *
 * <p>A <b>strongly connected component</b> is a maximal set of vertices
 * such that there is a directed path from every vertex to every other
 * vertex in the set.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li><b>Tarjan's algorithm</b> — single-pass DFS with lowlink values, O(V+E)</li>
 *   <li><b>Kosaraju's algorithm</b> — two-pass DFS using transpose graph, O(V+E)</li>
 *   <li><b>Condensation DAG</b> — collapse each SCC into a single super-node</li>
 *   <li><b>Component classification</b> — source/sink/intermediate SCCs</li>
 *   <li><b>Vertex lookup</b> — which SCC does a given vertex belong to?</li>
 *   <li><b>Connectivity queries</b> — are two vertices in the same SCC?</li>
 *   <li><b>Bridge edges</b> — edges between different SCCs</li>
 *   <li><b>Summary report</b> — text overview of SCC structure</li>
 * </ul>
 *
 * <p>Use cases:</p>
 * <ul>
 *   <li>Circular dependency detection in software modules</li>
 *   <li>Web page cluster analysis (link structure)</li>
 *   <li>Social network community detection</li>
 *   <li>2-SAT satisfiability via implication graphs</li>
 *   <li>Deadlock detection in resource allocation</li>
 * </ul>
 *
 * @author zalenix
 */
public class StronglyConnectedComponentsAnalyzer {

    private final Graph<String, Edge> graph;

    /**
     * Create a new SCC analyzer for the given graph.
     *
     * @param graph the JUNG graph to analyze (must not be null)
     * @throws IllegalArgumentException if graph is null
     */
    public StronglyConnectedComponentsAnalyzer(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
    }

    // ── Result classes ──────────────────────────────────────────

    /**
     * Represents a single strongly connected component.
     */
    public static class Component {
        private final int id;
        private final Set<String> vertices;
        private String classification; // "source", "sink", "intermediate", "isolated"

        public Component(int id, Set<String> vertices) {
            this.id = id;
            this.vertices = Collections.unmodifiableSet(vertices);
        }

        public int getId() { return id; }
        public Set<String> getVertices() { return vertices; }
        public int size() { return vertices.size(); }
        public boolean isTrivial() { return vertices.size() == 1; }
        public String getClassification() { return classification; }
        void setClassification(String classification) { this.classification = classification; }

        @Override
        public String toString() {
            return "SCC-" + id + vertices + "(" + classification + ")";
        }
    }

    /**
     * Full SCC analysis result.
     */
    public static class SCCResult {
        private final List<Component> components;
        private final Map<String, Integer> vertexToComponent;
        private final Graph<String, Edge> condensation;
        private final List<Edge> bridgeEdges;
        private final String algorithm;

        public SCCResult(List<Component> components, Map<String, Integer> vertexToComponent,
                         Graph<String, Edge> condensation, List<Edge> bridgeEdges, String algorithm) {
            this.components = Collections.unmodifiableList(components);
            this.vertexToComponent = Collections.unmodifiableMap(vertexToComponent);
            this.condensation = condensation;
            this.bridgeEdges = Collections.unmodifiableList(bridgeEdges);
            this.algorithm = algorithm;
        }

        public List<Component> getComponents() { return components; }
        public int getComponentCount() { return components.size(); }
        public Map<String, Integer> getVertexToComponent() { return vertexToComponent; }
        public Graph<String, Edge> getCondensation() { return condensation; }
        public List<Edge> getBridgeEdges() { return bridgeEdges; }
        public String getAlgorithm() { return algorithm; }

        /** Get the component containing a specific vertex. */
        public Component getComponentOf(String vertex) {
            Integer idx = vertexToComponent.get(vertex);
            if (idx == null) return null;
            return components.get(idx);
        }

        /** Check if two vertices are in the same SCC. */
        public boolean areStronglyConnected(String v1, String v2) {
            Integer c1 = vertexToComponent.get(v1);
            Integer c2 = vertexToComponent.get(v2);
            return c1 != null && c2 != null && c1.equals(c2);
        }

        /** Get all non-trivial (size > 1) components. */
        public List<Component> getNonTrivialComponents() {
            List<Component> result = new ArrayList<Component>();
            for (Component c : components) {
                if (!c.isTrivial()) result.add(c);
            }
            return result;
        }

        /** Get the largest component. */
        public Component getLargestComponent() {
            Component largest = null;
            for (Component c : components) {
                if (largest == null || c.size() > largest.size()) {
                    largest = c;
                }
            }
            return largest;
        }

        /** Is the entire graph strongly connected? */
        public boolean isStronglyConnected() {
            return components.size() == 1;
        }

        /** Get source SCCs (no incoming edges in condensation). */
        public List<Component> getSourceComponents() {
            return getByClassification("source");
        }

        /** Get sink SCCs (no outgoing edges in condensation). */
        public List<Component> getSinkComponents() {
            return getByClassification("sink");
        }

        private List<Component> getByClassification(String cls) {
            List<Component> result = new ArrayList<Component>();
            for (Component c : components) {
                if (cls.equals(c.getClassification())) result.add(c);
            }
            return result;
        }
    }

    // ── Tarjan's Algorithm ──────────────────────────────────────

    /**
     * Find SCCs using Tarjan's algorithm (single DFS pass).
     *
     * @return SCC analysis result
     */
    public SCCResult tarjan() {
        Map<String, Integer> index = new HashMap<String, Integer>();
        Map<String, Integer> lowlink = new HashMap<String, Integer>();
        Map<String, Boolean> onStack = new HashMap<String, Boolean>();
        Deque<String> stack = new ArrayDeque<String>();
        List<Set<String>> rawComponents = new ArrayList<Set<String>>();
        int[] counter = {0};

        for (String v : graph.getVertices()) {
            if (!index.containsKey(v)) {
                tarjanDFS(v, index, lowlink, onStack, stack, rawComponents, counter);
            }
        }

        return buildResult(rawComponents, "tarjan");
    }

    private void tarjanDFS(String v, Map<String, Integer> index, Map<String, Integer> lowlink,
                           Map<String, Boolean> onStack, Deque<String> stack,
                           List<Set<String>> rawComponents, int[] counter) {
        // Iterative Tarjan to avoid stack overflow on large graphs
        Deque<TarjanFrame> callStack = new ArrayDeque<TarjanFrame>();

        index.put(v, counter[0]);
        lowlink.put(v, counter[0]);
        counter[0]++;
        stack.push(v);
        onStack.put(v, true);

        callStack.push(new TarjanFrame(v, getSuccessors(v)));

        while (!callStack.isEmpty()) {
            TarjanFrame frame = callStack.peek();

            if (frame.neighborIndex < frame.neighbors.size()) {
                String w = frame.neighbors.get(frame.neighborIndex);
                frame.neighborIndex++;

                if (!index.containsKey(w)) {
                    index.put(w, counter[0]);
                    lowlink.put(w, counter[0]);
                    counter[0]++;
                    stack.push(w);
                    onStack.put(w, true);
                    callStack.push(new TarjanFrame(w, getSuccessors(w)));
                } else if (Boolean.TRUE.equals(onStack.get(w))) {
                    lowlink.put(frame.vertex, Math.min(lowlink.get(frame.vertex), index.get(w)));
                }
            } else {
                // All neighbors processed
                if (lowlink.get(frame.vertex).equals(index.get(frame.vertex))) {
                    Set<String> component = new LinkedHashSet<String>();
                    String w;
                    do {
                        w = stack.pop();
                        onStack.put(w, false);
                        component.add(w);
                    } while (!w.equals(frame.vertex));
                    rawComponents.add(component);
                }

                callStack.pop();
                if (!callStack.isEmpty()) {
                    TarjanFrame parent = callStack.peek();
                    lowlink.put(parent.vertex,
                            Math.min(lowlink.get(parent.vertex), lowlink.get(frame.vertex)));
                }
            }
        }
    }

    private static class TarjanFrame {
        final String vertex;
        final List<String> neighbors;
        int neighborIndex;

        TarjanFrame(String vertex, List<String> neighbors) {
            this.vertex = vertex;
            this.neighbors = neighbors;
            this.neighborIndex = 0;
        }
    }

    // ── Kosaraju's Algorithm ────────────────────────────────────

    /**
     * Find SCCs using Kosaraju's algorithm (two DFS passes).
     *
     * @return SCC analysis result
     */
    public SCCResult kosaraju() {
        // Pass 1: compute finish order
        Set<String> visited = new HashSet<String>();
        Deque<String> finishOrder = new ArrayDeque<String>();
        for (String v : graph.getVertices()) {
            if (!visited.contains(v)) {
                kosarajuDFS1(v, visited, finishOrder);
            }
        }

        // Build transpose adjacency
        Map<String, List<String>> transpose = new HashMap<String, List<String>>();
        for (String v : graph.getVertices()) {
            transpose.put(v, new ArrayList<String>());
        }
        for (Edge e : graph.getEdges()) {
            String src = getSource(e);
            String dst = getDest(e);
            if (src != null && dst != null) {
                transpose.get(dst).add(src);
            }
        }

        // Pass 2: collect components in reverse finish order
        visited.clear();
        List<Set<String>> rawComponents = new ArrayList<Set<String>>();
        while (!finishOrder.isEmpty()) {
            String v = finishOrder.pop();
            if (!visited.contains(v)) {
                Set<String> component = new LinkedHashSet<String>();
                kosarajuDFS2(v, visited, transpose, component);
                rawComponents.add(component);
            }
        }

        return buildResult(rawComponents, "kosaraju");
    }

    private void kosarajuDFS1(String v, Set<String> visited, Deque<String> finishOrder) {
        Deque<String[]> stack = new ArrayDeque<String[]>();
        stack.push(new String[]{v, "enter"});

        while (!stack.isEmpty()) {
            String[] frame = stack.pop();
            String vertex = frame[0];
            String phase = frame[1];

            if ("exit".equals(phase)) {
                finishOrder.push(vertex);
                continue;
            }

            if (visited.contains(vertex)) continue;
            visited.add(vertex);
            stack.push(new String[]{vertex, "exit"});

            for (String w : getSuccessors(vertex)) {
                if (!visited.contains(w)) {
                    stack.push(new String[]{w, "enter"});
                }
            }
        }
    }

    private void kosarajuDFS2(String v, Set<String> visited,
                              Map<String, List<String>> transpose, Set<String> component) {
        Deque<String> stack = new ArrayDeque<String>();
        stack.push(v);

        while (!stack.isEmpty()) {
            String vertex = stack.pop();
            if (visited.contains(vertex)) continue;
            visited.add(vertex);
            component.add(vertex);

            List<String> neighbors = transpose.get(vertex);
            if (neighbors != null) {
                for (String w : neighbors) {
                    if (!visited.contains(w)) {
                        stack.push(w);
                    }
                }
            }
        }
    }

    // ── Result building ─────────────────────────────────────────

    private SCCResult buildResult(List<Set<String>> rawComponents, String algorithm) {
        // Build components and vertex mapping
        List<Component> components = new ArrayList<Component>();
        Map<String, Integer> vertexToComponent = new HashMap<String, Integer>();

        for (int i = 0; i < rawComponents.size(); i++) {
            Component comp = new Component(i, rawComponents.get(i));
            components.add(comp);
            for (String v : rawComponents.get(i)) {
                vertexToComponent.put(v, i);
            }
        }

        // Build condensation DAG
        Graph<String, Edge> condensation = new DirectedSparseGraph<String, Edge>();
        for (Component c : components) {
            condensation.addVertex("SCC-" + c.getId());
        }

        List<Edge> bridgeEdges = new ArrayList<Edge>();
        Set<String> condensationEdgeSet = new HashSet<String>();
        int edgeId = 0;

        for (Edge e : graph.getEdges()) {
            String src = getSource(e);
            String dst = getDest(e);
            if (src == null || dst == null) continue;

            int srcComp = vertexToComponent.get(src);
            int dstComp = vertexToComponent.get(dst);

            if (srcComp != dstComp) {
                bridgeEdges.add(e);
                String key = srcComp + "->" + dstComp;
                if (!condensationEdgeSet.contains(key)) {
                    condensationEdgeSet.add(key);
                    Edge ce = new Edge("bridge", "SCC-" + srcComp, "SCC-" + dstComp);
                    ce.setLabel("ce" + edgeId++);
                    condensation.addEdge(ce, "SCC-" + srcComp, "SCC-" + dstComp);
                }
            }
        }

        // Classify components
        for (Component c : components) {
            String node = "SCC-" + c.getId();
            int inDeg = condensation.inDegree(node);
            int outDeg = condensation.outDegree(node);

            if (inDeg == 0 && outDeg == 0) {
                c.setClassification("isolated");
            } else if (inDeg == 0) {
                c.setClassification("source");
            } else if (outDeg == 0) {
                c.setClassification("sink");
            } else {
                c.setClassification("intermediate");
            }
        }

        return new SCCResult(components, vertexToComponent, condensation, bridgeEdges, algorithm);
    }

    // ── Utility methods ─────────────────────────────────────────

    /**
     * Generate a text summary report of the SCC analysis.
     */
    public String generateReport(SCCResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("===========================================\n");
        sb.append("  Strongly Connected Components Analysis\n");
        sb.append("===========================================\n\n");

        sb.append("Algorithm: ").append(result.getAlgorithm()).append("\n");
        sb.append("Vertices: ").append(graph.getVertexCount()).append("\n");
        sb.append("Edges: ").append(graph.getEdgeCount()).append("\n");
        sb.append("Components: ").append(result.getComponentCount()).append("\n");
        sb.append("Non-trivial: ").append(result.getNonTrivialComponents().size()).append("\n");
        sb.append("Strongly connected: ").append(result.isStronglyConnected() ? "YES" : "NO").append("\n");
        sb.append("Bridge edges: ").append(result.getBridgeEdges().size()).append("\n\n");

        sb.append("-- Components ----------------------------------\n\n");
        for (Component c : result.getComponents()) {
            sb.append("  SCC-").append(c.getId());
            sb.append(" [").append(c.getClassification()).append("]");
            sb.append(" size=").append(c.size());
            sb.append(" ").append(c.getVertices()).append("\n");
        }

        sb.append("\n-- Condensation DAG ----------------------------\n\n");
        sb.append("  Nodes: ").append(result.getCondensation().getVertexCount()).append("\n");
        sb.append("  Edges: ").append(result.getCondensation().getEdgeCount()).append("\n");

        List<Component> sources = result.getSourceComponents();
        List<Component> sinks = result.getSinkComponents();
        sb.append("  Sources: ").append(sources.size()).append("\n");
        sb.append("  Sinks: ").append(sinks.size()).append("\n");

        return sb.toString();
    }

    /**
     * Minimum edges to add to make entire graph strongly connected.
     * Answer is max(#sources, #sinks) in condensation, or 0 if already connected.
     */
    public int minEdgesToStronglyConnect(SCCResult result) {
        if (result.isStronglyConnected()) return 0;

        int sources = 0, sinks = 0;
        for (Component c : result.getComponents()) {
            if ("source".equals(c.getClassification()) || "isolated".equals(c.getClassification())) {
                sources++;
            }
            if ("sink".equals(c.getClassification()) || "isolated".equals(c.getClassification())) {
                sinks++;
            }
        }
        return Math.max(sources, sinks);
    }

    // ── Graph helpers ───────────────────────────────────────────

    private List<String> getSuccessors(String v) {
        List<String> result = new ArrayList<String>();
        Collection<Edge> outEdges = graph.getOutEdges(v);
        if (outEdges != null) {
            for (Edge e : outEdges) {
                String dest = getDest(e);
                if (dest != null && !dest.equals(v)) {
                    result.add(dest);
                } else if (dest != null && dest.equals(v)) {
                    // self-loop: successor is v itself
                    result.add(dest);
                }
            }
        }
        return result;
    }

    private String getSource(Edge e) {
        return e.getVertex1();
    }

    private String getDest(Edge e) {
        return e.getVertex2();
    }
}
