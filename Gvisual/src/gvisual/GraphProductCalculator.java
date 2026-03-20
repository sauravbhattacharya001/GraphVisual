package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import java.util.*;

/**
 * Computes graph products — fundamental binary operations that combine two
 * graphs into a new graph whose vertex set is the Cartesian product of the
 * input vertex sets.
 *
 * <h3>Supported products:</h3>
 * <ul>
 *   <li><b>Cartesian product</b> (G □ H) — vertices (u1,v1) and (u2,v2)
 *       are adjacent iff (u1=u2 and v1~v2) or (u1~u2 and v1=v2)</li>
 *   <li><b>Tensor product</b> (G × H, also called categorical/direct) —
 *       adjacent iff u1~u2 and v1~v2</li>
 *   <li><b>Strong product</b> (G ⊠ H) — union of Cartesian and tensor
 *       products; adjacent iff (u1=u2 and v1~v2) or (u1~u2 and v1=v2) or
 *       (u1~u2 and v1~v2)</li>
 *   <li><b>Lexicographic product</b> (G ∘ H, also called composition) —
 *       adjacent iff u1~u2 or (u1=u2 and v1~v2)</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * GraphProductCalculator calc = new GraphProductCalculator(graphG, graphH);
 * Graph<String, Edge> cartesian = calc.cartesianProduct();
 * Graph<String, Edge> tensor = calc.tensorProduct();
 * Graph<String, Edge> strong = calc.strongProduct();
 * Graph<String, Edge> lex = calc.lexicographicProduct();
 *
 * // Product metadata
 * ProductInfo info = calc.getProductInfo(ProductType.CARTESIAN);
 * System.out.println("Vertices: " + info.getVertexCount());
 * System.out.println("Edges: " + info.getEdgeCount());
 *
 * // Text report comparing all products
 * System.out.println(calc.getReport());
 * }</pre>
 *
 * <p>Product vertices are named "(u,v)" where u and v are original vertex
 * labels. Product edges carry type "product".</p>
 *
 * @author zalenix
 */
public class GraphProductCalculator {

    /** Enumeration of supported graph product types. */
    public enum ProductType {
        CARTESIAN, TENSOR, STRONG, LEXICOGRAPHIC
    }

    /** Metadata about a computed graph product. */
    public static class ProductInfo {
        private final ProductType type;
        private final int vertexCount;
        private final int edgeCount;
        private final int sourceGVertices;
        private final int sourceGEdges;
        private final int sourceHVertices;
        private final int sourceHEdges;
        private final double density;
        private final long computeTimeMs;

        ProductInfo(ProductType type, int vertexCount, int edgeCount,
                    int sourceGVertices, int sourceGEdges,
                    int sourceHVertices, int sourceHEdges, long computeTimeMs) {
            this.type = type;
            this.vertexCount = vertexCount;
            this.edgeCount = edgeCount;
            this.sourceGVertices = sourceGVertices;
            this.sourceGEdges = sourceGEdges;
            this.sourceHVertices = sourceHVertices;
            this.sourceHEdges = sourceHEdges;
            this.computeTimeMs = computeTimeMs;
            int maxEdges = vertexCount * (vertexCount - 1) / 2;
            this.density = maxEdges > 0 ? (double) edgeCount / maxEdges : 0.0;
        }

        public ProductType getType() { return type; }
        public int getVertexCount() { return vertexCount; }
        public int getEdgeCount() { return edgeCount; }
        public int getSourceGVertices() { return sourceGVertices; }
        public int getSourceGEdges() { return sourceGEdges; }
        public int getSourceHVertices() { return sourceHVertices; }
        public int getSourceHEdges() { return sourceHEdges; }
        public double getDensity() { return density; }
        public long getComputeTimeMs() { return computeTimeMs; }

        @Override
        public String toString() {
            return String.format("%s product: %d vertices, %d edges, density=%.4f (%dms)",
                    type, vertexCount, edgeCount, density, computeTimeMs);
        }
    }

    private final Graph<String, Edge> graphG;
    private final Graph<String, Edge> graphH;
    private final List<String> verticesG;
    private final List<String> verticesH;
    private final Map<ProductType, Graph<String, Edge>> cache;
    private final Map<ProductType, ProductInfo> infoCache;

    /**
     * Creates a new GraphProductCalculator for the given input graphs.
     *
     * @param graphG the first graph (G)
     * @param graphH the second graph (H)
     * @throws IllegalArgumentException if either graph is null
     */
    public GraphProductCalculator(Graph<String, Edge> graphG, Graph<String, Edge> graphH) {
        if (graphG == null || graphH == null) {
            throw new IllegalArgumentException("Both graphs must be non-null");
        }
        this.graphG = graphG;
        this.graphH = graphH;
        this.verticesG = new ArrayList<String>(graphG.getVertices());
        this.verticesH = new ArrayList<String>(graphH.getVertices());
        this.cache = new EnumMap<ProductType, Graph<String, Edge>>(ProductType.class);
        this.infoCache = new EnumMap<ProductType, ProductInfo>(ProductType.class);
    }

    /**
     * Creates the product vertex label for a pair of source vertices.
     */
    private String productVertex(String u, String v) {
        return "(" + u + "," + v + ")";
    }

    /**
     * Checks if two vertices are adjacent in the given graph.
     */
    private boolean areAdjacent(Graph<String, Edge> g, String v1, String v2) {
        return g.findEdge(v1, v2) != null;
    }

    private int edgeCounter = 0;

    /**
     * Creates a fresh product edge.
     */
    private Edge newProductEdge(String u1, String v1, String u2, String v2) {
        Edge e  new Edge("product",
                productVertex(u1, v1),
                productVertex(u2, v2));
        e.setLabel("e" + (edgeCounter++));
        return e;
    }

    /**
     * Computes the Cartesian product G □ H.
     *
     * <p>(u1,v1) ~ (u2,v2) iff [u1=u2 and v1~v2 in H] or [v1=v2 and u1~u2 in G]</p>
     *
     * @return the Cartesian product graph
     */
    public Graph<String, Edge> cartesianProduct() {
        if (cache.containsKey(ProductType.CARTESIAN)) {
            return cache.get(ProductType.CARTESIAN);
        }
        long start = System.currentTimeMillis();
        Graph<String, Edge> product = new UndirectedSparseGraph<String, Edge>();

        // Add all product vertices
        for (String u : verticesG) {
            for (String v : verticesH) {
                product.addVertex(productVertex(u, v));
            }
        }

        // Edges from G-factor: fix H-vertex, vary G-vertices
        for (String v : verticesH) {
            for (int i = 0; i < verticesG.size(); i++) {
                for (int j = i + 1; j < verticesG.size(); j++) {
                    String u1 = verticesG.get(i);
                    String u2 = verticesG.get(j);
                    if (areAdjacent(graphG, u1, u2)) {
                        product.addEdge(newProductEdge(u1, v, u2, v),
                                productVertex(u1, v), productVertex(u2, v));
                    }
                }
            }
        }

        // Edges from H-factor: fix G-vertex, vary H-vertices
        for (String u : verticesG) {
            for (int i = 0; i < verticesH.size(); i++) {
                for (int j = i + 1; j < verticesH.size(); j++) {
                    String v1 = verticesH.get(i);
                    String v2 = verticesH.get(j);
                    if (areAdjacent(graphH, v1, v2)) {
                        product.addEdge(newProductEdge(u, v1, u, v2),
                                productVertex(u, v1), productVertex(u, v2));
                    }
                }
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        cache.put(ProductType.CARTESIAN, product);
        infoCache.put(ProductType.CARTESIAN, new ProductInfo(ProductType.CARTESIAN,
                product.getVertexCount(), product.getEdgeCount(),
                graphG.getVertexCount(), graphG.getEdgeCount(),
                graphH.getVertexCount(), graphH.getEdgeCount(), elapsed));
        return product;
    }

    /**
     * Computes the tensor (categorical/direct) product G × H.
     *
     * <p>(u1,v1) ~ (u2,v2) iff u1~u2 in G and v1~v2 in H</p>
     *
     * @return the tensor product graph
     */
    public Graph<String, Edge> tensorProduct() {
        if (cache.containsKey(ProductType.TENSOR)) {
            return cache.get(ProductType.TENSOR);
        }
        long start = System.currentTimeMillis();
        Graph<String, Edge> product = new UndirectedSparseGraph<String, Edge>();

        for (String u : verticesG) {
            for (String v : verticesH) {
                product.addVertex(productVertex(u, v));
            }
        }

        for (int i = 0; i < verticesG.size(); i++) {
            for (int j = i + 1; j < verticesG.size(); j++) {
                String u1 = verticesG.get(i);
                String u2 = verticesG.get(j);
                if (!areAdjacent(graphG, u1, u2)) continue;
                for (int k = 0; k < verticesH.size(); k++) {
                    for (int l = k + 1; l < verticesH.size(); l++) {
                        String v1 = verticesH.get(k);
                        String v2 = verticesH.get(l);
                        if (!areAdjacent(graphH, v1, v2)) continue;
                        // Both (u1,v1)-(u2,v2) and (u1,v2)-(u2,v1)
                        product.addEdge(newProductEdge(u1, v1, u2, v2),
                                productVertex(u1, v1), productVertex(u2, v2));
                        product.addEdge(newProductEdge(u1, v2, u2, v1),
                                productVertex(u1, v2), productVertex(u2, v1));
                    }
                }
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        cache.put(ProductType.TENSOR, product);
        infoCache.put(ProductType.TENSOR, new ProductInfo(ProductType.TENSOR,
                product.getVertexCount(), product.getEdgeCount(),
                graphG.getVertexCount(), graphG.getEdgeCount(),
                graphH.getVertexCount(), graphH.getEdgeCount(), elapsed));
        return product;
    }

    /**
     * Computes the strong product G ⊠ H (union of Cartesian and tensor).
     *
     * <p>(u1,v1) ~ (u2,v2) iff any of:
     * [u1=u2 and v1~v2] or [v1=v2 and u1~u2] or [u1~u2 and v1~v2]</p>
     *
     * @return the strong product graph
     */
    public Graph<String, Edge> strongProduct() {
        if (cache.containsKey(ProductType.STRONG)) {
            return cache.get(ProductType.STRONG);
        }
        long start = System.currentTimeMillis();
        Graph<String, Edge> product = new UndirectedSparseGraph<String, Edge>();

        for (String u : verticesG) {
            for (String v : verticesH) {
                product.addVertex(productVertex(u, v));
            }
        }

        // Build adjacency sets for fast lookup
        Set<String> gEdgeSet = new HashSet<String>();
        for (String u1 : verticesG) {
            for (String u2 : verticesG) {
                if (!u1.equals(u2) && areAdjacent(graphG, u1, u2)) {
                    gEdgeSet.add(u1 + "\0" + u2);
                }
            }
        }
        Set<String> hEdgeSet = new HashSet<String>();
        for (String v1 : verticesH) {
            for (String v2 : verticesH) {
                if (!v1.equals(v2) && areAdjacent(graphH, v1, v2)) {
                    hEdgeSet.add(v1 + "\0" + v2);
                }
            }
        }

        // Iterate all pairs of product vertices
        List<String> allProductVertices = new ArrayList<String>(product.getVertices());
        for (int i = 0; i < allProductVertices.size(); i++) {
            for (int j = i + 1; j < allProductVertices.size(); j++) {
                String pv1 = allProductVertices.get(i);
                String pv2 = allProductVertices.get(j);

                // Parse back the components
                String u1 = parseFirst(pv1);
                String v1 = parseSecond(pv1);
                String u2 = parseFirst(pv2);
                String v2 = parseSecond(pv2);

                boolean uEqual = u1.equals(u2);
                boolean vEqual = v1.equals(v2);
                boolean uAdj = gEdgeSet.contains(u1 + "\0" + u2);
                boolean vAdj = hEdgeSet.contains(v1 + "\0" + v2);

                boolean connected = false;
                if (uEqual && vAdj) connected = true;       // Cartesian (H-factor)
                else if (vEqual && uAdj) connected = true;  // Cartesian (G-factor)
                else if (uAdj && vAdj) connected = true;    // Tensor

                if (connected) {
                    Edge e  new Edge("product", pv1, pv2);
                    e.setLabel("e" + (edgeCounter++));
                    product.addEdge(e, pv1, pv2);
                }
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        cache.put(ProductType.STRONG, product);
        infoCache.put(ProductType.STRONG, new ProductInfo(ProductType.STRONG,
                product.getVertexCount(), product.getEdgeCount(),
                graphG.getVertexCount(), graphG.getEdgeCount(),
                graphH.getVertexCount(), graphH.getEdgeCount(), elapsed));
        return product;
    }

    /**
     * Computes the lexicographic product G ∘ H (graph composition).
     *
     * <p>(u1,v1) ~ (u2,v2) iff u1~u2 in G, or (u1=u2 and v1~v2 in H)</p>
     *
     * @return the lexicographic product graph
     */
    public Graph<String, Edge> lexicographicProduct() {
        if (cache.containsKey(ProductType.LEXICOGRAPHIC)) {
            return cache.get(ProductType.LEXICOGRAPHIC);
        }
        long start = System.currentTimeMillis();
        Graph<String, Edge> product = new UndirectedSparseGraph<String, Edge>();

        for (String u : verticesG) {
            for (String v : verticesH) {
                product.addVertex(productVertex(u, v));
            }
        }

        // When u1~u2 in G: connect (u1,v1) to (u2,v2) for ALL v1, v2
        for (int i = 0; i < verticesG.size(); i++) {
            for (int j = i + 1; j < verticesG.size(); j++) {
                String u1 = verticesG.get(i);
                String u2 = verticesG.get(j);
                if (!areAdjacent(graphG, u1, u2)) continue;
                for (String v1 : verticesH) {
                    for (String v2 : verticesH) {
                        String pv1 = productVertex(u1, v1);
                        String pv2 = productVertex(u2, v2);
                        if (product.findEdge(pv1, pv2) == null) {
                            product.addEdge(newProductEdge(u1, v1, u2, v2), pv1, pv2);
                        }
                    }
                }
            }
        }

        // When u1=u2: connect (u,v1) to (u,v2) if v1~v2 in H
        for (String u : verticesG) {
            for (int k = 0; k < verticesH.size(); k++) {
                for (int l = k + 1; l < verticesH.size(); l++) {
                    String v1 = verticesH.get(k);
                    String v2 = verticesH.get(l);
                    if (!areAdjacent(graphH, v1, v2)) continue;
                    String pv1 = productVertex(u, v1);
                    String pv2 = productVertex(u, v2);
                    if (product.findEdge(pv1, pv2) == null) {
                        product.addEdge(newProductEdge(u, v1, u, v2), pv1, pv2);
                    }
                }
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        cache.put(ProductType.LEXICOGRAPHIC, product);
        infoCache.put(ProductType.LEXICOGRAPHIC, new ProductInfo(ProductType.LEXICOGRAPHIC,
                product.getVertexCount(), product.getEdgeCount(),
                graphG.getVertexCount(), graphG.getEdgeCount(),
                graphH.getVertexCount(), graphH.getEdgeCount(), elapsed));
        return product;
    }

    /**
     * Returns product info/metadata for a given product type.
     * Computes the product if not already cached.
     *
     * @param type the product type
     * @return product info
     */
    public ProductInfo getProductInfo(ProductType type) {
        if (!infoCache.containsKey(type)) {
            switch (type) {
                case CARTESIAN: cartesianProduct(); break;
                case TENSOR: tensorProduct(); break;
                case STRONG: strongProduct(); break;
                case LEXICOGRAPHIC: lexicographicProduct(); break;
            }
        }
        return infoCache.get(type);
    }

    /**
     * Computes all four products and returns a comparative text report.
     *
     * @return formatted multi-line report
     */
    public String getReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Graph Product Report ===\n\n");
        sb.append(String.format("Graph G: %d vertices, %d edges\n",
                graphG.getVertexCount(), graphG.getEdgeCount()));
        sb.append(String.format("Graph H: %d vertices, %d edges\n",
                graphH.getVertexCount(), graphH.getEdgeCount()));
        sb.append(String.format("Product vertex count: %d × %d = %d\n\n",
                graphG.getVertexCount(), graphH.getVertexCount(),
                graphG.getVertexCount() * graphH.getVertexCount()));

        for (ProductType type : ProductType.values()) {
            ProductInfo info = getProductInfo(type);
            sb.append(String.format("%-15s  %5d edges  density=%.4f  (%dms)\n",
                    type + ":", info.getEdgeCount(), info.getDensity(), info.getComputeTimeMs()));
        }

        sb.append("\n--- Theoretical Edge Counts ---\n");
        int nG = graphG.getVertexCount();
        int mG = graphG.getEdgeCount();
        int nH = graphH.getVertexCount();
        int mH = graphH.getEdgeCount();
        sb.append(String.format("Cartesian:      |E_G|·|V_H| + |V_G|·|E_H| = %d·%d + %d·%d = %d\n",
                mG, nH, nG, mH, mG * nH + nG * mH));
        sb.append(String.format("Tensor:         2·|E_G|·|E_H| = 2·%d·%d = %d\n",
                mG, mH, 2 * mG * mH));
        sb.append(String.format("Strong:         |E_G|·|V_H| + |V_G|·|E_H| + 2·|E_G|·|E_H| = %d\n",
                mG * nH + nG * mH + 2 * mG * mH));
        sb.append(String.format("Lexicographic:  |E_G|·|V_H|² + |V_G|·|E_H| = %d·%d + %d·%d = %d\n",
                mG, nH * nH, nG, mH, mG * nH * nH + nG * mH));

        return sb.toString();
    }

    /**
     * Returns the degree sequence of a product graph.
     *
     * @param type the product type
     * @return sorted degree sequence (descending)
     */
    public List<Integer> getDegreeSequence(ProductType type) {
        Graph<String, Edge> product;
        switch (type) {
            case CARTESIAN: product = cartesianProduct(); break;
            case TENSOR: product = tensorProduct(); break;
            case STRONG: product = strongProduct(); break;
            case LEXICOGRAPHIC: product = lexicographicProduct(); break;
            default: throw new IllegalArgumentException("Unknown type: " + type);
        }
        List<Integer> degrees = new ArrayList<Integer>();
        for (String v : product.getVertices()) {
            degrees.add(product.degree(v));
        }
        Collections.sort(degrees, Collections.reverseOrder());
        return degrees;
    }

    // --- Parsing helpers for "(u,v)" vertex labels ---

    private String parseFirst(String productVertex) {
        // "(u,v)" -> "u"
        int start = productVertex.indexOf('(') + 1;
        int comma = productVertex.lastIndexOf(',');
        return productVertex.substring(start, comma);
    }

    private String parseSecond(String productVertex) {
        // "(u,v)" -> "v"
        int comma = productVertex.lastIndexOf(',');
        int end = productVertex.lastIndexOf(')');
        return productVertex.substring(comma + 1, end);
    }
}
