package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;

/**
 * Utility class for constructing various Laplacian matrices from a JUNG graph.
 *
 * <p>Provides three standard Laplacian forms:</p>
 * <ul>
 *   <li><b>Standard Laplacian</b> (L = D − A) — used for algebraic connectivity,
 *       Fiedler vector, spectral partitioning, and spanning tree counting.</li>
 *   <li><b>Normalized Laplacian</b> (L_norm = D^{-1/2} L D^{-1/2}) — eigenvalues
 *       in [0, 2], useful for spectral clustering on graphs with heterogeneous
 *       degree distributions.</li>
 *   <li><b>Random Walk Laplacian</b> (L_rw = D^{-1} L = I − D^{-1} A) — relates
 *       to random walk transition probabilities; equivalent normalized form for
 *       clustering.</li>
 * </ul>
 *
 * <p>Also provides helpers for extracting the adjacency matrix, degree matrix,
 * and building Laplacians for vertex subsets (subgraph-induced Laplacians).</p>
 *
 * @author zalenix
 */
public class LaplacianBuilder {

    private LaplacianBuilder() {
        // utility class — no instantiation
    }

    // ═════════════════════════════════════════════════════════════════
    //  Adjacency & Degree matrices
    // ═════════════════════════════════════════════════════════════════

    /**
     * Builds the adjacency matrix for the given graph using the provided
     * vertex ordering.
     *
     * @param graph      the graph
     * @param vertexList ordered list of vertices (defines row/column mapping)
     * @return n×n adjacency matrix
     */
    public static double[][] buildAdjacencyMatrix(Graph<String, edge> graph,
                                                   List<String> vertexList) {
        int n = vertexList.size();
        double[][] A = new double[n][n];
        Map<String, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < n; i++) {
            indexMap.put(vertexList.get(i), i);
        }

        for (edge e : graph.getEdges()) {
            Collection<String> endpoints = graph.getEndpoints(e);
            Iterator<String> it = endpoints.iterator();
            String u = it.next();
            String v = it.hasNext() ? it.next() : u;
            Integer ui = indexMap.get(u);
            Integer vi = indexMap.get(v);
            if (ui != null && vi != null && !ui.equals(vi)) {
                A[ui][vi] = 1.0;
                A[vi][ui] = 1.0;
            }
        }
        return A;
    }

    /**
     * Extracts the degree vector from an adjacency matrix.
     *
     * @param A adjacency matrix
     * @param n dimension
     * @return degree vector where degree[i] = sum of row i
     */
    public static double[] buildDegreeVector(double[][] A, int n) {
        double[] degree = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                degree[i] += A[i][j];
            }
        }
        return degree;
    }

    // ═════════════════════════════════════════════════════════════════
    //  Standard Laplacian (L = D − A)
    // ═════════════════════════════════════════════════════════════════

    /**
     * Builds the standard Laplacian matrix from an adjacency matrix.
     *
     * @param A adjacency matrix
     * @param n dimension
     * @return L = D − A
     */
    public static double[][] buildLaplacian(double[][] A, int n) {
        double[][] L = new double[n][n];
        for (int i = 0; i < n; i++) {
            double degree = 0.0;
            for (int j = 0; j < n; j++) {
                L[i][j] = -A[i][j];
                degree += A[i][j];
            }
            L[i][i] = degree;
        }
        return L;
    }

    /**
     * Builds the standard Laplacian for a full graph using the given vertex
     * ordering.
     *
     * @param graph      the graph
     * @param vertexList ordered list of vertices
     * @return L = D − A
     */
    public static double[][] buildLaplacian(Graph<String, edge> graph,
                                             List<String> vertexList) {
        double[][] A = buildAdjacencyMatrix(graph, vertexList);
        return buildLaplacian(A, vertexList.size());
    }

    /**
     * Builds the standard Laplacian for a subgraph induced by a vertex subset.
     * Only edges between vertices in the subset are considered.
     *
     * @param graph    the full graph
     * @param vertices ordered subset of vertices
     * @return n×n Laplacian for the induced subgraph
     */
    public static double[][] buildSubgraphLaplacian(Graph<String, edge> graph,
                                                      List<String> vertices) {
        int n = vertices.size();
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) {
            index.put(vertices.get(i), i);
        }

        double[][] laplacian = new double[n][n];
        for (int i = 0; i < n; i++) {
            String v = vertices.get(i);
            int degree = 0;
            for (String neighbor : graph.getNeighbors(v)) {
                Integer j = index.get(neighbor);
                if (j != null) {
                    laplacian[i][j] = -1.0;
                    degree++;
                }
            }
            laplacian[i][i] = degree;
        }
        return laplacian;
    }

    // ═════════════════════════════════════════════════════════════════
    //  Normalized Laplacian (D^{-1/2} L D^{-1/2})
    // ═════════════════════════════════════════════════════════════════

    /**
     * Builds the symmetric normalized Laplacian: L_norm = D^{-1/2} L D^{-1/2}.
     * Isolated vertices (degree 0) get a diagonal entry of 0.
     *
     * @param A adjacency matrix
     * @param n dimension
     * @return normalized Laplacian matrix
     */
    public static double[][] buildNormalizedLaplacian(double[][] A, int n) {
        double[] degree = buildDegreeVector(A, n);
        double[] invSqrtDeg = new double[n];
        for (int i = 0; i < n; i++) {
            invSqrtDeg[i] = degree[i] > 0 ? 1.0 / Math.sqrt(degree[i]) : 0.0;
        }

        double[][] Ln = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    Ln[i][i] = degree[i] > 0 ? 1.0 : 0.0;
                } else {
                    Ln[i][j] = -A[i][j] * invSqrtDeg[i] * invSqrtDeg[j];
                }
            }
        }
        return Ln;
    }

    /**
     * Builds the normalized Laplacian for a graph with given vertex ordering.
     *
     * @param graph      the graph
     * @param vertexList ordered list of vertices
     * @return normalized Laplacian matrix
     */
    public static double[][] buildNormalizedLaplacian(Graph<String, edge> graph,
                                                       List<String> vertexList) {
        double[][] A = buildAdjacencyMatrix(graph, vertexList);
        return buildNormalizedLaplacian(A, vertexList.size());
    }

    // ═════════════════════════════════════════════════════════════════
    //  Random Walk Laplacian (L_rw = D^{-1} L = I − D^{-1} A)
    // ═════════════════════════════════════════════════════════════════

    /**
     * Builds the random walk Laplacian: L_rw = I − D^{-1} A.
     * Isolated vertices (degree 0) get a diagonal entry of 0.
     *
     * @param A adjacency matrix
     * @param n dimension
     * @return random walk Laplacian matrix
     */
    public static double[][] buildRandomWalkLaplacian(double[][] A, int n) {
        double[] degree = buildDegreeVector(A, n);

        double[][] Lrw = new double[n][n];
        for (int i = 0; i < n; i++) {
            if (degree[i] > 0) {
                Lrw[i][i] = 1.0;
                for (int j = 0; j < n; j++) {
                    if (i != j) {
                        Lrw[i][j] = -A[i][j] / degree[i];
                    }
                }
            }
        }
        return Lrw;
    }

    /**
     * Builds the random walk Laplacian for a graph with given vertex ordering.
     *
     * @param graph      the graph
     * @param vertexList ordered list of vertices
     * @return random walk Laplacian matrix
     */
    public static double[][] buildRandomWalkLaplacian(Graph<String, edge> graph,
                                                        List<String> vertexList) {
        double[][] A = buildAdjacencyMatrix(graph, vertexList);
        return buildRandomWalkLaplacian(A, vertexList.size());
    }
}
