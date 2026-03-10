package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.*;

/**
 * Classic network science benchmark graphs for testing and comparison.
 *
 * @author zalenix
 */
public class GraphBenchmarkSuite {

    public static class BenchmarkGraph {
        private final String name;
        private final String description;
        private final Graph<String, edge> graph;
        private final int expectedNodes;
        private final int expectedEdges;
        private final Map<String, String> properties;

        public BenchmarkGraph(String name, String description,
                              Graph<String, edge> graph,
                              int expectedNodes, int expectedEdges,
                              Map<String, String> properties) {
            this.name = name;
            this.description = description;
            this.graph = graph;
            this.expectedNodes = expectedNodes;
            this.expectedEdges = expectedEdges;
            this.properties = Collections.unmodifiableMap(properties);
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public Graph<String, edge> getGraph() { return graph; }
        public int getExpectedNodes() { return expectedNodes; }
        public int getExpectedEdges() { return expectedEdges; }
        public Map<String, String> getProperties() { return properties; }

        public boolean verify() {
            return graph.getVertexCount() == expectedNodes
                && graph.getEdgeCount() == expectedEdges;
        }

        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== %s ===%n", name));
            sb.append(String.format("  %s%n", description));
            sb.append(String.format("  Nodes: %d (expected %d)%n", graph.getVertexCount(), expectedNodes));
            sb.append(String.format("  Edges: %d (expected %d)%n", graph.getEdgeCount(), expectedEdges));
            sb.append(String.format("  Verified: %s%n", verify()));
            if (!properties.isEmpty()) {
                sb.append("  Properties:\n");
                for (Map.Entry<String, String> e : properties.entrySet())
                    sb.append(String.format("    %s: %s%n", e.getKey(), e.getValue()));
            }
            return sb.toString();
        }
    }

    private static void addEdge(Graph<String, edge> g, String v1, String v2) {
        if (!g.containsVertex(v1)) g.addVertex(v1);
        if (!g.containsVertex(v2)) g.addVertex(v2);
        edge e = new edge("f", v1, v2);
        e.setLabel(v1 + "-" + v2);
        g.addEdge(e, v1, v2);
    }

    private static Map<String, String> props(String... pairs) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < pairs.length; i += 2) m.put(pairs[i], pairs[i + 1]);
        return m;
    }

    public BenchmarkGraph zacharyKarateClub() {
        Graph<String, edge> g = new UndirectedSparseGraph<String, edge>();
        int[][] edges = {
            {1,2},{1,3},{1,4},{1,5},{1,6},{1,7},{1,8},{1,9},{1,11},{1,12},
            {1,13},{1,14},{1,18},{1,20},{1,22},{1,32},
            {2,3},{2,4},{2,8},{2,14},{2,18},{2,20},{2,22},{2,31},
            {3,4},{3,8},{3,9},{3,10},{3,14},{3,28},{3,29},{3,33},
            {4,8},{4,13},{4,14},{5,7},{5,11},{6,7},{6,11},{6,17},{7,17},
            {9,31},{9,33},{9,34},{10,34},{14,34},
            {15,33},{15,34},{16,33},{16,34},{19,33},{19,34},{20,34},
            {21,33},{21,34},{23,33},{23,34},
            {24,26},{24,28},{24,30},{24,33},{24,34},
            {25,26},{25,28},{25,32},{26,32},{27,30},{27,34},{28,34},
            {29,32},{29,34},{30,33},{30,34},{31,33},{31,34},{32,33},{32,34},{33,34},
        };
        for (int[] p : edges) addEdge(g, String.valueOf(p[0]), String.valueOf(p[1]));
        return new BenchmarkGraph("Zachary Karate Club",
            "Social network of 34 karate club members (Zachary 1977). Two known factions.",
            g, 34, 78, props("communities","2","diameter","5","density","0.139","type","undirected"));
    }

    public BenchmarkGraph petersenGraph() {
        Graph<String, edge> g = new UndirectedSparseGraph<String, edge>();
        int[][] edges = {{0,1},{1,2},{2,3},{3,4},{4,0},{5,7},{7,9},{9,6},{6,8},{8,5},{0,5},{1,6},{2,7},{3,8},{4,9}};
        for (int[] p : edges) addEdge(g, String.valueOf(p[0]), String.valueOf(p[1]));
        return new BenchmarkGraph("Petersen Graph",
            "Classic 3-regular graph. Counterexample for many conjectures.",
            g, 10, 15, props("regular","3","diameter","2","girth","5","hamiltonian","no","type","undirected"));
    }

    public BenchmarkGraph florentineFamilies() {
        Graph<String, edge> g = new UndirectedSparseGraph<String, edge>();
        String[][] edges = {
            {"Medici","Barbadori"},{"Medici","Ridolfi"},{"Medici","Tornabuoni"},
            {"Medici","Albizzi"},{"Medici","Salviati"},{"Medici","Acciaiuoli"},
            {"Ridolfi","Strozzi"},{"Ridolfi","Tornabuoni"},{"Tornabuoni","Guadagni"},
            {"Albizzi","Ginori"},{"Albizzi","Guadagni"},{"Salviati","Pazzi"},
            {"Guadagni","Lamberteschi"},{"Guadagni","Bischeri"},
            {"Strozzi","Castellani"},{"Strozzi","Bischeri"},
            {"Castellani","Peruzzi"},{"Castellani","Barbadori"},
            {"Peruzzi","Bischeri"},{"Peruzzi","Barbadori"},
        };
        for (String[] p : edges) addEdge(g, p[0], p[1]);
        return new BenchmarkGraph("Florentine Families",
            "Marriage alliances among 15 Florentine families (Padgett & Ansell 1993).",
            g, 15, 20, props("highest_betweenness","Medici","type","undirected"));
    }

    public BenchmarkGraph cubeGraph() {
        Graph<String, edge> g = new UndirectedSparseGraph<String, edge>();
        int[][] edges = {{0,1},{0,2},{0,4},{1,3},{1,5},{2,3},{2,6},{3,7},{4,5},{4,6},{5,7},{6,7}};
        for (int[] p : edges) {
            String v1 = String.format("%03d", Integer.parseInt(Integer.toBinaryString(p[0])));
            String v2 = String.format("%03d", Integer.parseInt(Integer.toBinaryString(p[1])));
            addEdge(g, v1, v2);
        }
        return new BenchmarkGraph("Cube Graph (Q3)",
            "3-dimensional hypercube. Vertices are 3-bit binary strings.",
            g, 8, 12, props("regular","3","bipartite","yes","hamiltonian","yes","diameter","3","type","undirected"));
    }

    public BenchmarkGraph dodecahedron() {
        Graph<String, edge> g = new UndirectedSparseGraph<String, edge>();
        int[][] edges = {
            {1,2},{2,3},{3,4},{4,5},{5,1},{1,6},{2,7},{3,8},{4,9},{5,10},
            {6,11},{6,15},{7,11},{7,12},{8,12},{8,13},{9,13},{9,14},{10,14},{10,15},
            {11,16},{12,17},{13,18},{14,19},{15,20},{16,17},{17,18},{18,19},{19,20},{20,16},
        };
        for (int[] p : edges) addEdge(g, String.valueOf(p[0]), String.valueOf(p[1]));
        return new BenchmarkGraph("Dodecahedron",
            "Skeleton of a regular dodecahedron. Hamilton's Icosian game (1857).",
            g, 20, 30, props("regular","3","planar","yes","hamiltonian","yes","diameter","5","girth","5","type","undirected"));
    }

    public BenchmarkGraph tutteGraph() {
        Graph<String, edge> g = new UndirectedSparseGraph<String, edge>();
        int[][] edges = {
            {1,2},{1,4},{1,26},
            {2,3},{2,5},{3,4},{3,8},{4,29},
            {5,6},{5,7},{6,8},{6,9},{7,10},{7,11},{8,12},
            {9,10},{9,13},{10,14},{11,12},{11,15},{12,16},
            {13,14},{13,17},{14,18},{15,16},{15,19},{16,20},
            {17,18},{17,21},{18,22},{19,20},{19,23},{20,24},
            {21,22},{21,25},{22,26},{23,24},{23,27},{24,28},
            {25,26},{25,29},{27,28},{27,30},{28,31},{29,32},
            {30,31},{30,33},{31,34},{32,33},{32,35},{33,36},
            {34,35},{34,37},{35,38},{36,37},{36,39},{37,40},
            {38,39},{38,41},{39,42},{40,41},{40,43},{41,44},
            {42,43},{42,45},{43,46},{44,45},{44,46},{45,46},
        };
        for (int[] p : edges) addEdge(g, String.valueOf(p[0]), String.valueOf(p[1]));
        return new BenchmarkGraph("Tutte Graph",
            "Smallest 3-connected cubic planar non-Hamiltonian graph (Tutte 1946).",
            g, 46, 69, props("regular","3 (cubic)","planar","yes","hamiltonian","no","3-connected","yes","type","undirected"));
    }

    public BenchmarkGraph friendshipGraph(int n) {
        if (n < 1) throw new IllegalArgumentException("n must be >= 1");
        Graph<String, edge> g = new UndirectedSparseGraph<String, edge>();
        g.addVertex("0");
        for (int i = 0; i < n; i++) {
            String a = String.valueOf(2 * i + 1), b = String.valueOf(2 * i + 2);
            addEdge(g, "0", a); addEdge(g, "0", b); addEdge(g, a, b);
        }
        return new BenchmarkGraph("Friendship Graph F(" + n + ")",
            n + " triangles sharing a hub (Friendship Theorem, Erdos-Renyi-Sos 1966).",
            g, 2*n+1, 3*n, props("hub_degree",String.valueOf(2*n),"leaf_degree","2","diameter","2","type","undirected"));
    }

    public List<BenchmarkGraph> allBenchmarks() {
        return Arrays.asList(zacharyKarateClub(), petersenGraph(), florentineFamilies(),
            cubeGraph(), dodecahedron(), tutteGraph(), friendshipGraph(5));
    }

    public static List<String> availableBenchmarks() {
        return Arrays.asList("zachary-karate-club","petersen","florentine-families",
            "cube-q3","dodecahedron","tutte","friendship(n)");
    }

    public BenchmarkGraph getByName(String name) {
        switch (name.toLowerCase().replace(" ","-").replace("_","-")) {
            case "zachary": case "zachary-karate-club": case "karate": case "karate-club": return zacharyKarateClub();
            case "petersen": return petersenGraph();
            case "florentine": case "florentine-families": return florentineFamilies();
            case "cube": case "cube-q3": case "hypercube": case "q3": return cubeGraph();
            case "dodecahedron": return dodecahedron();
            case "tutte": return tutteGraph();
            default:
                if (name.toLowerCase().startsWith("friendship")) {
                    String num = name.replaceAll("[^0-9]","");
                    return friendshipGraph(num.isEmpty() ? 5 : Integer.parseInt(num));
                }
                return null;
        }
    }
}
