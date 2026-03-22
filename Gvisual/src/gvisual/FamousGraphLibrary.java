package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.*;

/**
 * Library of famous named graphs from graph theory and network science.
 *
 * <p>Provides instant construction of well-known graphs that are frequently
 * used in textbooks, algorithm demonstrations, and research papers. Each graph
 * is built programmatically with correct structure and meaningful vertex labels.</p>
 *
 * <h3>Available graphs:</h3>
 * <ul>
 *   <li><b>Petersen Graph</b> — the most famous counter-example in graph theory (10 vertices, 15 edges)</li>
 *   <li><b>Zachary's Karate Club</b> — classic social network dataset (34 members, 78 edges)</li>
 *   <li><b>Königsberg Bridges</b> — Euler's original problem (4 landmasses, 7 bridges)</li>
 *   <li><b>Complete Bipartite K₃₃</b> — utility graph, key in planarity testing</li>
 *   <li><b>Frucht Graph</b> — smallest cubic graph with no non-trivial automorphisms</li>
 *   <li><b>Heawood Graph</b> — incidence graph of the Fano plane (14 vertices)</li>
 *   <li><b>Dodecahedron</b> — skeleton of the regular dodecahedron (20 vertices)</li>
 *   <li><b>Tutte Graph</b> — smallest counterexample to Tait's conjecture (46 vertices)</li>
 *   <li><b>Florentine Families</b> — Renaissance marriage network (15 families)</li>
 *   <li><b>Diamond Graph</b> — K₄ minus one edge (4 vertices, 5 edges)</li>
 *   <li><b>Bull Graph</b> — the bull graph (5 vertices, 5 edges)</li>
 *   <li><b>Butterfly Graph</b> — two triangles sharing a vertex (5 vertices, 6 edges)</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 *   // Get the Petersen graph
 *   Graph<String, Edge> petersen = FamousGraphLibrary.petersen();
 *
 *   // List all available graphs
 *   Map<String, String> catalog = FamousGraphLibrary.catalog();
 *
 *   // Get a graph by name
 *   Graph<String, Edge> g = FamousGraphLibrary.byName("petersen");
 * }</pre>
 */
public final class FamousGraphLibrary {

    private FamousGraphLibrary() { /* utility class */ }

    // ---- catalog --------------------------------------------------------

    /** Ordered map of graph-name → one-line description. */
    public static LinkedHashMap<String, String> catalog() {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("petersen",           "Petersen Graph — 10 vertices, 15 edges, classic counter-example");
        m.put("karate",            "Zachary's Karate Club — 34 members social network");
        m.put("konigsberg",        "Königsberg Bridges — Euler's 4 landmasses, 7 bridges");
        m.put("k33",               "Complete Bipartite K₃₃ — utility graph");
        m.put("frucht",            "Frucht Graph — smallest asymmetric cubic graph");
        m.put("heawood",           "Heawood Graph — Fano plane incidence graph, 14 vertices");
        m.put("dodecahedron",      "Dodecahedron — 20 vertices, 30 edges");
        m.put("tutte",             "Tutte Graph — 46 vertices, Tait's conjecture counter-example");
        m.put("florentine",        "Florentine Families — Renaissance marriage network");
        m.put("diamond",           "Diamond Graph — K₄ minus one edge");
        m.put("bull",              "Bull Graph — 5 vertices, 5 edges");
        m.put("butterfly",         "Butterfly (Bowtie) — two triangles sharing a vertex");
        return m;
    }

    /** Look up a famous graph by name (case-insensitive). Returns null if unknown. */
    public static Graph<String, Edge> byName(String name) {
        if (name == null) return null;
        switch (name.toLowerCase().trim()) {
            case "petersen":      return petersen();
            case "karate":        return karateClub();
            case "konigsberg":    return konigsberg();
            case "k33":           return k33();
            case "frucht":        return frucht();
            case "heawood":       return heawood();
            case "dodecahedron":  return dodecahedron();
            case "tutte":         return tutte();
            case "florentine":    return florentine();
            case "diamond":       return diamond();
            case "bull":          return bull();
            case "butterfly":     return butterfly();
            default:              return null;
        }
    }

    // ---- helpers --------------------------------------------------------

    private static int edgeId = 0;

    private static void link(Graph<String, Edge> g, String a, String b) {
        Edge e = new Edge("f", a, b);
        e.setLabel(a + "-" + b);
        g.addEdge(e, a, b);
    }

    // ---- individual graphs ----------------------------------------------

    /** Petersen Graph: 10 vertices, 15 edges. Outer 5-cycle + inner pentagram. */
    public static Graph<String, Edge> petersen() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        String[] outer = {"P0", "P1", "P2", "P3", "P4"};
        String[] inner = {"P5", "P6", "P7", "P8", "P9"};
        for (String v : outer) g.addVertex(v);
        for (String v : inner) g.addVertex(v);
        // Outer cycle
        for (int i = 0; i < 5; i++) link(g, outer[i], outer[(i + 1) % 5]);
        // Inner pentagram
        for (int i = 0; i < 5; i++) link(g, inner[i], inner[(i + 2) % 5]);
        // Spokes
        for (int i = 0; i < 5; i++) link(g, outer[i], inner[i]);
        return g;
    }

    /**
     * Zachary's Karate Club: 34 members, 78 edges.
     * Classic social network from Wayne Zachary's 1977 paper.
     */
    public static Graph<String, Edge> karateClub() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 1; i <= 34; i++) g.addVertex("M" + i);
        int[][] edges = {
            {1,2},{1,3},{1,4},{1,5},{1,6},{1,7},{1,8},{1,9},{1,11},{1,12},{1,13},{1,14},{1,18},{1,20},{1,22},{1,32},
            {2,3},{2,4},{2,8},{2,14},{2,18},{2,20},{2,22},{2,31},
            {3,4},{3,8},{3,9},{3,10},{3,14},{3,28},{3,29},{3,33},
            {4,8},{4,13},{4,14},
            {5,7},{5,11},
            {6,7},{6,11},{6,17},
            {7,17},
            {9,31},{9,33},{9,34},
            {10,34},
            {14,34},
            {15,33},{15,34},
            {16,33},{16,34},
            {19,33},{19,34},
            {20,34},
            {21,33},{21,34},
            {23,33},{23,34},
            {24,26},{24,28},{24,30},{24,33},{24,34},
            {25,26},{25,28},{25,32},
            {26,32},
            {27,30},{27,34},
            {28,34},
            {29,32},{29,34},
            {30,33},{30,34},
            {31,33},{31,34},
            {32,33},{32,34},
            {33,34}
        };
        for (int[] pair : edges) link(g, "M" + pair[0], "M" + pair[1]);
        return g;
    }

    /** Königsberg Bridges: 4 landmasses connected by 7 bridges (multigraph approximated). */
    public static Graph<String, Edge> konigsberg() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        // Landmasses: North, South, East, Island(Kneiphof)
        for (String v : new String[]{"North", "South", "East", "Island"}) g.addVertex(v);
        // 7 bridges — since UndirectedSparseGraph doesn't support parallel edges,
        // we model as weighted or add intermediate bridge nodes
        String[] bridges = {"B1","B2","B3","B4","B5","B6","B7"};
        for (String b : bridges) g.addVertex(b);
        // North-Island: 2 bridges
        link(g, "North", "B1"); link(g, "B1", "Island");
        link(g, "North", "B2"); link(g, "B2", "Island");
        // South-Island: 2 bridges
        link(g, "South", "B3"); link(g, "B3", "Island");
        link(g, "South", "B4"); link(g, "B4", "Island");
        // North-East: 1 bridge
        link(g, "North", "B5"); link(g, "B5", "East");
        // South-East: 1 bridge
        link(g, "South", "B6"); link(g, "B6", "East");
        // Island-East: 1 bridge
        link(g, "Island", "B7"); link(g, "B7", "East");
        return g;
    }

    /** Complete Bipartite K₃₃ — the utility graph. */
    public static Graph<String, Edge> k33() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        String[] left  = {"U1", "U2", "U3"};
        String[] right = {"V1", "V2", "V3"};
        for (String v : left)  g.addVertex(v);
        for (String v : right) g.addVertex(v);
        for (String u : left)
            for (String v : right)
                link(g, u, v);
        return g;
    }

    /** Frucht Graph: 12 vertices, 18 edges. Smallest cubic graph with trivial automorphism group. */
    public static Graph<String, Edge> frucht() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < 12; i++) g.addVertex("F" + i);
        int[][] edges = {
            {0,1},{1,2},{2,3},{3,4},{4,5},{5,6},{6,0},  // outer 7-cycle
            {0,7},{1,7},{2,8},{3,8},{4,9},{5,9},{6,10},
            {7,11},{8,11},{9,10},{10,11}
        };
        for (int[] e : edges) link(g, "F" + e[0], "F" + e[1]);
        return g;
    }

    /** Heawood Graph: 14 vertices, 21 edges. Incidence graph of the Fano plane. */
    public static Graph<String, Edge> heawood() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < 14; i++) g.addVertex("H" + i);
        // Heawood graph adjacency: vertex i connects to i±1 mod 14 and i±5 mod 14
        int[][] edges = {
            {0,1},{1,2},{2,3},{3,4},{4,5},{5,6},{6,7},{7,8},{8,9},{9,10},{10,11},{11,12},{12,13},{13,0},
            {0,5},{1,6},{2,7},{3,8},{4,9},{5,10},{6,11}
        };
        for (int[] e : edges) link(g, "H" + e[0], "H" + e[1]);
        return g;
    }

    /** Dodecahedron: 20 vertices, 30 edges. Skeleton of the regular dodecahedron. */
    public static Graph<String, Edge> dodecahedron() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < 20; i++) g.addVertex("D" + i);
        int[][] edges = {
            {0,1},{1,2},{2,3},{3,4},{4,0},           // top pentagon
            {0,5},{1,6},{2,7},{3,8},{4,9},            // top spokes
            {5,10},{6,10},{6,11},{7,11},{7,12},{8,12},{8,13},{9,13},{9,14},{5,14}, // middle band
            {10,15},{11,16},{12,17},{13,18},{14,19},   // bottom spokes
            {15,16},{16,17},{17,18},{18,19},{19,15}    // bottom pentagon
        };
        for (int[] e : edges) link(g, "D" + e[0], "D" + e[1]);
        return g;
    }

    /**
     * Tutte Graph: 46 vertices, 69 edges.
     * Smallest known counterexample to Tait's conjecture that every 3-connected
     * cubic planar graph is Hamiltonian.
     */
    public static Graph<String, Edge> tutte() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (int i = 0; i < 46; i++) g.addVertex("T" + i);
        // Tutte graph edges (standard numbering)
        int[][] edges = {
            {0,1},{0,2},{0,3},
            {1,4},{1,26},
            {2,10},{2,16},
            {3,18},{3,24},
            {4,5},{4,33},
            {5,6},{5,29},
            {6,7},{6,27},
            {7,8},{7,14},
            {8,9},{8,38},
            {9,10},{9,37},
            {10,11},
            {11,12},{11,39},
            {12,13},{12,35},
            {13,14},{13,15},
            {14,34},
            {15,16},{15,22},
            {16,17},
            {17,18},{17,44},
            {18,19},
            {19,20},{19,43},
            {20,21},{20,41},
            {21,22},{21,23},
            {22,40},
            {23,24},{23,30},
            {24,25},
            {25,26},{25,45},
            {26,27},
            {27,28},
            {28,29},{28,32},
            {29,30},
            {30,31},
            {31,32},{31,42},
            {32,33},
            {33,34},
            {34,35},
            {35,36},
            {36,37},{36,39},
            {37,38},
            {38,45},
            {39,40},
            {40,41},
            {41,42},
            {42,43},
            {43,44},
            {44,45}
        };
        for (int[] e : edges) link(g, "T" + e[0], "T" + e[1]);
        return g;
    }

    /**
     * Florentine Families marriage network.
     * 15 Renaissance Florentine families connected by marriage ties.
     * Classic dataset from Padgett & Ansell (1993).
     */
    public static Graph<String, Edge> florentine() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        String[] families = {
            "Acciaiuoli","Albizzi","Barbadori","Bischeri","Castellani",
            "Ginori","Guadagni","Lamberteschi","Medici","Pazzi",
            "Peruzzi","Ridolfi","Salviati","Strozzi","Tornabuoni"
        };
        for (String f : families) g.addVertex(f);
        String[][] marriages = {
            {"Medici","Acciaiuoli"},{"Medici","Barbadori"},{"Medici","Ridolfi"},
            {"Medici","Tornabuoni"},{"Medici","Albizzi"},{"Medici","Salviati"},
            {"Albizzi","Ginori"},{"Albizzi","Guadagni"},
            {"Guadagni","Lamberteschi"},{"Guadagni","Tornabuoni"},{"Guadagni","Bischeri"},
            {"Bischeri","Strozzi"},{"Bischeri","Peruzzi"},
            {"Castellani","Peruzzi"},{"Castellani","Strozzi"},{"Castellani","Barbadori"},
            {"Strozzi","Ridolfi"},
            {"Peruzzi","Strozzi"},
            {"Ridolfi","Tornabuoni"},
            {"Salviati","Pazzi"}
        };
        for (String[] pair : marriages) link(g, pair[0], pair[1]);
        return g;
    }

    /** Diamond Graph: K₄ minus one edge. 4 vertices, 5 edges. */
    public static Graph<String, Edge> diamond() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (String v : new String[]{"A","B","C","D"}) g.addVertex(v);
        link(g, "A", "B"); link(g, "A", "C"); link(g, "B", "C");
        link(g, "B", "D"); link(g, "C", "D");
        return g;
    }

    /** Bull Graph: 5 vertices, 5 edges. Triangle with two pendant edges. */
    public static Graph<String, Edge> bull() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (String v : new String[]{"A","B","C","D","E"}) g.addVertex(v);
        link(g, "A", "B"); link(g, "B", "C"); link(g, "A", "C");
        link(g, "A", "D"); link(g, "B", "E");
        return g;
    }

    /** Butterfly (Bowtie) Graph: 5 vertices, 6 edges. Two triangles sharing a vertex. */
    public static Graph<String, Edge> butterfly() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (String v : new String[]{"A","B","C","D","E"}) g.addVertex(v);
        // C is the shared vertex
        link(g, "A", "B"); link(g, "B", "C"); link(g, "A", "C");
        link(g, "C", "D"); link(g, "D", "E"); link(g, "C", "E");
        return g;
    }
}
