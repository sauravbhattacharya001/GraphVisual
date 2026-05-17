package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link GraphProductCalculator}.
 *
 * <p>The tests use small hand-verifiable examples and the closed-form edge
 * counts for each product type:</p>
 *
 * <ul>
 *   <li>Cartesian:    |E_G|\u00b7|V_H| + |V_G|\u00b7|E_H|</li>
 *   <li>Tensor:       2\u00b7|E_G|\u00b7|E_H|</li>
 *   <li>Strong:       Cartesian + Tensor</li>
 *   <li>Lexicographic:|E_G|\u00b7|V_H|\u00b2 + |V_G|\u00b7|E_H|</li>
 * </ul>
 */
public class GraphProductCalculatorTest {

    private static int edgeId = 0;

    private static Graph<String, Edge> build(String[]... edges) {
        Graph<String, Edge> g = new UndirectedSparseGraph<String, Edge>();
        for (String[] e : edges) {
            if (!g.containsVertex(e[0])) g.addVertex(e[0]);
            if (!g.containsVertex(e[1])) g.addVertex(e[1]);
            Edge ed = new Edge("link", e[0], e[1]);
            ed.setLabel("e" + (edgeId++));
            g.addEdge(ed, e[0], e[1]);
        }
        return g;
    }

    // ─── Constructor ────────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void nullFirstGraphRejected() {
        new GraphProductCalculator(null,
                build(new String[]{"a", "b"}));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullSecondGraphRejected() {
        new GraphProductCalculator(
                build(new String[]{"a", "b"}), null);
    }

    // ─── Cartesian product ──────────────────────────────────────────────

    @Test
    public void cartesianProductOfTwoEdgesIsC4() {
        // K2 □ K2 = C4 (a square)
        Graph<String, Edge> g = build(new String[]{"a", "b"});
        Graph<String, Edge> h = build(new String[]{"x", "y"});
        GraphProductCalculator calc = new GraphProductCalculator(g, h);

        Graph<String, Edge> p = calc.cartesianProduct();
        // 2 * 2 = 4 product vertices
        assertEquals(4, p.getVertexCount());
        // |E_G|*|V_H| + |V_G|*|E_H| = 1*2 + 2*1 = 4
        assertEquals(4, p.getEdgeCount());
    }

    @Test
    public void cartesianProductIsCached() {
        Graph<String, Edge> g = build(new String[]{"a", "b"});
        Graph<String, Edge> h = build(new String[]{"x", "y"});
        GraphProductCalculator calc = new GraphProductCalculator(g, h);
        assertSame(calc.cartesianProduct(), calc.cartesianProduct());
    }

    // ─── Tensor product ─────────────────────────────────────────────────

    @Test
    public void tensorProductOfTwoEdgesHasTwoEdges() {
        // K2 × K2 = 2K2 (two disjoint edges across the diagonals)
        Graph<String, Edge> g = build(new String[]{"a", "b"});
        Graph<String, Edge> h = build(new String[]{"x", "y"});
        GraphProductCalculator calc = new GraphProductCalculator(g, h);

        Graph<String, Edge> p = calc.tensorProduct();
        assertEquals(4, p.getVertexCount());
        // 2 * |E_G| * |E_H| = 2 * 1 * 1 = 2
        assertEquals(2, p.getEdgeCount());
    }

    // ─── Strong product (the refactored method) ─────────────────────────

    @Test
    public void strongProductOfTwoEdgesEqualsCartesianPlusTensor() {
        Graph<String, Edge> g = build(new String[]{"a", "b"});
        Graph<String, Edge> h = build(new String[]{"x", "y"});
        GraphProductCalculator calc = new GraphProductCalculator(g, h);

        Graph<String, Edge> p = calc.strongProduct();
        assertEquals(4, p.getVertexCount());
        // 4 Cartesian + 2 tensor = 6
        assertEquals(6, p.getEdgeCount());

        // All four product vertices should be mutually adjacent in K2⊠K2 = K4
        String[] verts = {"(a,x)", "(a,y)", "(b,x)", "(b,y)"};
        for (String v : verts) assertTrue("missing " + v, p.containsVertex(v));
        for (int i = 0; i < verts.length; i++) {
            for (int j = i + 1; j < verts.length; j++) {
                assertNotNull(verts[i] + "-" + verts[j],
                        p.findEdge(verts[i], verts[j]));
            }
        }
    }

    @Test
    public void strongProductOfPathsMatchesFormula() {
        // P3 ⊠ P3
        // G: a-b-c (2 edges, 3 verts)
        // H: x-y-z (2 edges, 3 verts)
        // Strong = |E_G|·|V_H| + |V_G|·|E_H| + 2·|E_G|·|E_H|
        //        = 2·3 + 3·2 + 2·2·2 = 6 + 6 + 8 = 20
        Graph<String, Edge> g = build(
                new String[]{"a", "b"}, new String[]{"b", "c"});
        Graph<String, Edge> h = build(
                new String[]{"x", "y"}, new String[]{"y", "z"});
        GraphProductCalculator calc = new GraphProductCalculator(g, h);

        Graph<String, Edge> p = calc.strongProduct();
        assertEquals(9, p.getVertexCount());
        assertEquals(20, p.getEdgeCount());
    }

    @Test
    public void strongProductIsCached() {
        Graph<String, Edge> g = build(new String[]{"a", "b"});
        Graph<String, Edge> h = build(new String[]{"x", "y"});
        GraphProductCalculator calc = new GraphProductCalculator(g, h);
        assertSame(calc.strongProduct(), calc.strongProduct());
    }

    // ─── Lexicographic product ──────────────────────────────────────────

    @Test
    public void lexicographicProductEdgeCountMatchesFormula() {
        // |E_G|·|V_H|² + |V_G|·|E_H|
        Graph<String, Edge> g = build(new String[]{"a", "b"});            // 2v 1e
        Graph<String, Edge> h = build(new String[]{"x", "y"});            // 2v 1e
        GraphProductCalculator calc = new GraphProductCalculator(g, h);

        Graph<String, Edge> p = calc.lexicographicProduct();
        // 1*4 + 2*1 = 6
        assertEquals(6, p.getEdgeCount());
    }

    // ─── Product info / report ──────────────────────────────────────────

    @Test
    public void productInfoExposesCounts() {
        Graph<String, Edge> g = build(new String[]{"a", "b"});
        Graph<String, Edge> h = build(new String[]{"x", "y"});
        GraphProductCalculator calc = new GraphProductCalculator(g, h);

        GraphProductCalculator.ProductInfo info =
                calc.getProductInfo(GraphProductCalculator.ProductType.STRONG);
        assertEquals(GraphProductCalculator.ProductType.STRONG, info.getType());
        assertEquals(4, info.getVertexCount());
        assertEquals(6, info.getEdgeCount());
        assertTrue(info.getDensity() > 0.0);
        assertTrue(info.toString().contains("STRONG"));
    }

    @Test
    public void reportMentionsAllFourProductTypes() {
        Graph<String, Edge> g = build(new String[]{"a", "b"});
        Graph<String, Edge> h = build(new String[]{"x", "y"});
        String rep = new GraphProductCalculator(g, h).getReport();
        assertTrue(rep.contains("CARTESIAN"));
        assertTrue(rep.contains("TENSOR"));
        assertTrue(rep.contains("STRONG"));
        assertTrue(rep.contains("LEXICOGRAPHIC"));
    }

    @Test
    public void degreeSequenceForCartesianK2BoxK2IsAllTwos() {
        // C4 is 2-regular
        Graph<String, Edge> g = build(new String[]{"a", "b"});
        Graph<String, Edge> h = build(new String[]{"x", "y"});
        GraphProductCalculator calc = new GraphProductCalculator(g, h);
        java.util.List<Integer> deg = calc.getDegreeSequence(
                GraphProductCalculator.ProductType.CARTESIAN);
        assertEquals(4, deg.size());
        for (Integer d : deg) assertEquals(Integer.valueOf(2), d);
    }
}
