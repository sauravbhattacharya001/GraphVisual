# Algorithms Reference

A comprehensive reference for all graph algorithms implemented in GraphVisual, organized by category. Each entry lists the algorithm, complexity, source file, and typical use cases.

## Contents

- [Layout & Visualization](#layout--visualization)
- [Traversal & Shortest Paths](#traversal--shortest-paths)
- [Centrality & Importance](#centrality--importance)
- [Community & Clustering](#community--clustering)
- [Connectivity & Resilience](#connectivity--resilience)
- [Matching & Covering](#matching--covering)
- [Coloring & Planarity](#coloring--planarity)
- [Flow & Optimization](#flow--optimization)
- [Structural Analysis](#structural-analysis)
- [Comparison & Evolution](#comparison--evolution)
- [Stochastic & Prediction](#stochastic--prediction)
- [Export & Generation](#export--generation)

---

## Layout & Visualization

### Fruchterman-Reingold Force-Directed Layout
- **File:** `ForceDirectedLayout.java`
- **Complexity:** O(iterations × (V² + E))
- **Algorithm:** Iterative spring-embedding. Repulsive forces push all vertex pairs apart (Coulomb's law); attractive forces pull connected vertices together (Hooke's law). Includes gravity toward center, temperature-based cooling, and convergence detection.
- **Layout metrics:** Edge crossing count (O(E²)), edge length uniformity (CV), minimum angular resolution, stress (graph-distance vs Euclidean-distance preservation).

### Adjacency Matrix Heatmap
- **File:** `AdjacencyMatrixHeatmap.java`
- **Complexity:** O(V² + E)
- **Algorithm:** Builds a V×V adjacency matrix with edge type coloring. Useful for visualizing dense connection patterns and identifying clusters.

---

## Traversal & Shortest Paths

### BFS Shortest Path (Unweighted)
- **File:** `ShortestPathFinder.java`
- **Complexity:** O(V + E)
- **Algorithm:** Standard breadth-first search returning the hop-count-optimal path between two vertices.

### Dijkstra's Shortest Path (Weighted)
- **File:** `ShortestPathFinder.java`
- **Complexity:** O((V + E) log V) with priority queue
- **Algorithm:** Priority-queue-based relaxation using immutable PQ entries (stale-entry pattern avoids heap corruption from Java's `PriorityQueue` not supporting decrease-key).

### Topological Sort (Kahn's Algorithm)
- **File:** `TopologicalSortAnalyzer.java`
- **Complexity:** O(V + E)
- **Algorithm:** BFS-based topological ordering for DAGs. Detects cycles by checking if all vertices are ordered.

### Eulerian Path/Circuit (Hierholzer's Algorithm)
- **File:** `EulerianPathAnalyzer.java`
- **Complexity:** O(V + E)
- **Algorithm:** Finds Eulerian paths (visit every edge once) and circuits (closed Eulerian path). Checks necessary/sufficient conditions: all vertices even-degree (circuit) or exactly two odd-degree vertices (path).

### Hamiltonian Path/Cycle (Backtracking + Heuristics)
- **File:** `HamiltonianAnalyzer.java`
- **Complexity:** O(V!) worst case (exact); O(V²) for heuristic
- **Algorithm:** Exact backtracking search with pruning, plus greedy nearest-neighbor heuristic. Sufficient condition checks (Dirac's, Ore's, Chvátal's theorems) provide fast positive answers when applicable.

---

## Centrality & Importance

### Degree/Betweenness/Closeness/Eigenvector Centrality
- **File:** `NodeCentralityAnalyzer.java`
- **Complexity:**
  - Degree: O(V)
  - Betweenness: O(V × (V + E)) — Brandes' algorithm
  - Closeness: O(V × (V + E)) — BFS from each vertex
  - Eigenvector: O(iterations × E) — power iteration
- **Algorithm:** Four standard centrality measures. Betweenness uses Brandes' efficient algorithm with dependency accumulation. Eigenvector uses power iteration with L2 normalization.

### PageRank
- **File:** `PageRankAnalyzer.java`
- **Complexity:** O(iterations × (V + E))
- **Algorithm:** Power iteration with configurable damping factor, convergence tolerance, and dangling-node handling. Array-based computation with pre-built adjacency lists for cache efficiency.

---

## Community & Clustering

### Louvain Community Detection
- **File:** `CommunityDetector.java`
- **Complexity:** O(E × iterations) per phase
- **Algorithm:** Greedy modularity optimization. Each vertex moves to the community maximizing modularity gain, repeated until convergence. Multi-phase with community aggregation.

### K-Core Decomposition
- **File:** `KCoreDecomposition.java`
- **Complexity:** O(V + E)
- **Algorithm:** Iterative peeling — repeatedly removes vertices with degree < k. Identifies nested subgraphs of increasing connectivity (1-core ⊇ 2-core ⊇ ...).

### Motif Analysis (Census)
- **File:** `MotifAnalyzer.java`
- **Complexity:** O(V³) for triangle census
- **Algorithm:** Enumerates small subgraph patterns (triangles, wedges, etc.) to characterize local connectivity structure. Triangle count normalized to clustering coefficient.

### Clique Detection (Bron-Kerbosch)
- **File:** `CliqueAnalyzer.java`
- **Complexity:** O(3^(V/3)) worst case (Bron-Kerbosch with Tomita pivot)
- **Algorithm:** Finds all maximal cliques using the Bron-Kerbosch algorithm with pivot selection (Tomita variant) to minimize branching. Also provides maximum clique (largest by size), clique cover number, clique graph construction, and k-clique community detection. Fundamental for social network analysis (tight-knit groups) and bioinformatics (protein complexes).

---

## Connectivity & Resilience

### Articulation Points & Bridges (Tarjan's Algorithm)
- **File:** `ArticulationPointAnalyzer.java`
- **Complexity:** O(V + E)
- **Algorithm:** Single-DFS discovery using discovery/low-link values. A vertex is an articulation point if its removal increases the number of connected components. A bridge is an edge whose removal disconnects the graph.

### Strongly Connected Components (Kosaraju's/Tarjan's)
- **File:** `StronglyConnectedComponentsAnalyzer.java`
- **Complexity:** O(V + E)
- **Algorithm:** Finds maximal subsets of vertices where every vertex is reachable from every other vertex (in directed graphs). Used for condensation DAG construction.

### Graph Resilience (Attack Simulation)
- **File:** `GraphResilienceAnalyzer.java`
- **Complexity:** O(V × (V + E))
- **Algorithm:** Simulates node removal under three strategies (random, degree-targeted, betweenness-targeted) and tracks giant component size, diameter, and connectivity. Quantifies robustness vs. fragility.

### Cycle Detection
- **File:** `CycleAnalyzer.java`
- **Complexity:** O(V + E)
- **Algorithm:** DFS-based cycle detection with back-edge tracking. Reports all fundamental cycles (cycle basis).

---

## Matching & Covering

### Maximum Matching (Hopcroft-Karp)
- **File:** `BipartiteAnalyzer.java`
- **Complexity:** O(E × √V)
- **Algorithm:** For bipartite graphs: BFS layering + DFS augmenting paths. Also computes minimum vertex cover (König's theorem) and maximum independent set (complement of vertex cover).

### Minimum Vertex Cover (Greedy Approximation)
- **File:** `VertexCoverAnalyzer.java`
- **Complexity:** O(V + E) for 2-approximation
- **Algorithm:** Greedy edge-selection heuristic guaranteeing a cover within 2× optimal. Also provides exact solution for small graphs via brute force.

### Maximum Independent Set
- **File:** `IndependentSetAnalyzer.java`
- **Complexity:** NP-hard (exact); O(V + E) for greedy
- **Algorithm:** Greedy heuristic selects minimum-degree vertices. Complement of vertex cover provides alternative computation path.

### Dominating Set
- **File:** `DominatingSetAnalyzer.java`
- **Complexity:** NP-hard (exact); O(V log V + E) for greedy
- **Algorithm:** Greedy heuristic — repeatedly selects the vertex covering the most uncovered neighbors.

---

## Coloring & Planarity

### Graph Coloring (Greedy + DSatur)
- **File:** `GraphColoringAnalyzer.java`
- **Complexity:** O(V² + E) for DSatur
- **Algorithm:** DSatur (Degree of Saturation) heuristic — colors vertices in order of maximum saturation degree (number of distinct colors used by neighbors). Optimal for bipartite and cycle graphs.

### Chordal Graph Analysis
- **File:** `ChordalGraphAnalyzer.java`
- **Complexity:** O(V + E) for recognition
- **Algorithm:** Maximum cardinality search (MCS) for perfect elimination ordering (PEO). If a PEO exists, the graph is chordal. Provides optimal coloring, maximum clique, and minimum fill-in for chordal graphs.

### Planarity Testing
- **File:** `PlanarGraphAnalyzer.java`
- **Complexity:** O(V + E)
- **Algorithm:** Left-right planarity test with face enumeration and dual graph construction. Detects Kuratowski subgraphs (K₅ or K₃,₃ subdivisions) in non-planar graphs.

---

## Flow & Optimization

### Maximum Flow (Ford-Fulkerson / Edmonds-Karp)
- **File:** `NetworkFlowAnalyzer.java`
- **Complexity:** O(V × E²)
- **Algorithm:** BFS-based augmenting path search (Edmonds-Karp variant of Ford-Fulkerson). Computes max flow and min cut between source and sink vertices.

### Minimum Spanning Tree (Kruskal's Algorithm)
- **File:** `MinimumSpanningTree.java`
- **Complexity:** O(E log E)
- **Algorithm:** Sort edges by weight, greedily add edges that don't form cycles (Union-Find for cycle detection). Returns spanning tree edges and total weight.

### Steiner Tree (Approximation)
- **File:** `SteinerTreeAnalyzer.java`
- **Complexity:** O(|T| × V × (V + E)) for metric approximation
- **Algorithm:** Approximates minimum-cost tree connecting a subset of terminal vertices. Uses shortest-path-based metric closure and MST of the closure graph.

### Graph Partitioning (BFS / Kernighan-Lin / Spectral)
- **File:** `GraphPartitioner.java`
- **Complexity:** O(V² log V) for Kernighan-Lin; O(V³) for spectral bisection
- **Algorithm:** Partitions a graph into k balanced parts while minimizing edge cuts. Three strategies: (1) BFS-based seed growing with balanced allocation, (2) Kernighan-Lin iterative vertex-pair swapping for cut refinement (2-way, applied recursively for k-way), (3) spectral bisection using the Fiedler vector from the graph Laplacian.

### Maximum Cut (MaxCut)
- **File:** `MaxCutAnalyzer.java`
- **Complexity:** NP-hard (exact); O(V × E) for greedy heuristic
- **Algorithm:** Partitions vertices into two sets S and T to maximize crossing edges. Provides greedy heuristic (assigns each vertex to the side maximizing local cut), randomized approximation, and brute-force exact solver for small graphs. Includes cut ratio metric (fraction of edges in the cut).

### Feedback Vertex Set
- **File:** `FeedbackVertexSetAnalyzer.java`
- **Complexity:** NP-hard (exact); O(V + E) for greedy
- **Algorithm:** Finds minimum vertex set whose removal makes the graph acyclic. Greedy heuristic based on cycle participation frequency.

---

## Structural Analysis

### Graph Diameter, Radius, Center, Periphery
- **File:** `GraphDiameterAnalyzer.java`
- **Complexity:** O(V × (V + E))
- **Algorithm:** BFS from each vertex to compute eccentricities. Diameter = max eccentricity, radius = min eccentricity. Center/periphery vertices are those achieving radius/diameter.

### Degree Distribution Analysis
- **File:** `DegreeDistributionAnalyzer.java`
- **Complexity:** O(V)
- **Algorithm:** Computes degree histogram, power-law fit (log-log regression), Gini coefficient, and statistical moments. Identifies network type (scale-free, random, regular).

### Spectral Analysis
- **File:** `SpectralAnalyzer.java`
- **Complexity:** O(V³) for eigenvalue computation
- **Algorithm:** Laplacian matrix construction and eigenvalue computation. Algebraic connectivity (Fiedler value), spectral gap, and spectral clustering via Fiedler vector.

### Laplacian Matrix Builder
- **File:** `LaplacianBuilder.java`
- **Complexity:** O(V + E)
- **Algorithm:** Constructs the graph Laplacian L = D − A (degree matrix minus adjacency matrix). Supports standard, normalized (D⁻¹/²LD⁻¹/²), and random walk (I − D⁻¹A) forms. Also provides adjacency matrix extraction, degree vector computation, and subgraph-induced Laplacians for vertex subsets.

### Treewidth Analysis & Tree Decomposition
- **File:** `TreewidthAnalyzer.java`
- **Complexity:** O(V²) for greedy heuristics; NP-hard for exact
- **Algorithm:** Measures how "tree-like" a graph is. Uses greedy elimination orderings (min-degree, min-fill) to compute upper bounds on treewidth. Constructs tree decompositions — a tree of "bags" of vertices where each bag has size ≤ treewidth + 1. Many NP-hard problems become polynomial on bounded-treewidth graphs.

### Vertex & Edge Connectivity
- **File:** `VertexConnectivityAnalyzer.java`
- **Complexity:** O(V × (V + E)) for connectivity; O(V × maxflow) for edge connectivity
- **Algorithm:** Computes vertex connectivity κ (minimum vertices whose removal disconnects the graph) and edge connectivity λ (minimum edges). Uses iterative vertex removal with BFS reachability checks. Also provides minimum vertex/edge cut sets and connectivity classification (1-connected, 2-connected, etc.).

### Structural Hole Analysis
- **File:** `StructuralHoleAnalyzer.java`
- **Complexity:** O(V × avg_degree²)
- **Algorithm:** Implements Ronald Burt's structural holes theory. Computes per-vertex brokerage metrics: effective size (non-redundant contacts), constraint (dependence on single contacts), hierarchy (concentration of constraint), and efficiency (ratio of effective size to actual size). Identifies bridge nodes controlling information flow between otherwise disconnected groups.

### Graph Isomorphism
- **File:** `GraphIsomorphismAnalyzer.java`
- **Complexity:** O(V!) worst case; fast rejection via invariants
- **Algorithm:** Backtracking with degree-sequence pruning, neighbor-compatibility checks, and hash-based invariant comparison for fast rejection of non-isomorphic graphs.

### Tree Analysis
- **File:** `TreeAnalyzer.java`
- **Complexity:** O(V)
- **Algorithm:** Detects if graph is a tree (connected + acyclic), computes height, width, diameter, leaf count, center (iterative leaf removal), and LCA queries.

### Signed Graph Analysis
- **File:** `SignedGraphAnalyzer.java`
- **Complexity:** O(V + E)
- **Algorithm:** Structural balance theory — checks if a signed graph (positive/negative edges) satisfies balance conditions. Detects frustrated cycles and computes frustration index.

### Graph Entropy Analysis
- **File:** `GraphEntropyAnalyzer.java`
- **Complexity:** O(V² + E) for full computation (eigenvalue decomposition dominates)
- **Algorithm:** Computes 9 information-theoretic measures quantifying the structural complexity and randomness of a graph:
  - **Degree entropy:** Shannon entropy of the degree distribution — higher values indicate more heterogeneous connectivity.
  - **Von Neumann entropy:** Spectral entropy of the normalised Laplacian — captures global structural complexity (uses Jacobi eigenvalue iteration, no external dependencies).
  - **Edge type entropy:** Shannon entropy across edge categories — measures diversity of relationship types.
  - **Topological information content:** Automorphism-orbit-based entropy — measures structural symmetry (vertices in the same orbit are structurally equivalent).
  - **Random walk entropy rate:** Steady-state entropy of transition probabilities — quantifies how unpredictable a random walker's next step is.
  - **Chromatic entropy:** Greedy-coloring-based entropy — relates graph colorability to information content.
  - **Degree-CC mutual information:** Mutual information between vertex degree and clustering coefficient — captures degree-clustering correlation.
  - **Neighbourhood entropy:** Per-vertex entropy of neighbour degree distribution — identifies vertices with diverse vs. uniform neighbourhoods.
  - **Complexity class:** Automatic classification (minimal/low/moderate/high/very high) based on normalised degree entropy.
- **Use cases:** Network complexity assessment, anomaly detection (unusual entropy patterns), comparing structural properties of different networks.

---

## Comparison & Evolution

### Graph Diff
- **File:** `GraphDiffAnalyzer.java`
- **Complexity:** O(V + E)
- **Algorithm:** Computes symmetric difference between two graphs: added/removed/common vertices and edges. Similarity metrics include Jaccard index and edit distance.

### Graph Similarity (Entropy-Based)
- **File:** `GraphSimilarityAnalyzer.java`
- **Complexity:** O(V² + E) per graph (eigenvalue decomposition)
- **Algorithm:** Compares two graphs using information-theoretic distance measures built on the entropy framework:
  - **Jensen-Shannon divergence:** Symmetric divergence between normalised degree distributions — bounded [0, ln 2], 0 = identical distributions.
  - **Von Neumann divergence:** Spectral divergence between normalised Laplacians — captures differences in global structural properties.
  - **Entropy profile distance:** Euclidean distance between multi-dimensional entropy profiles (degree, von Neumann, edge type, topological, random walk, chromatic entropies).
  - **Composite similarity score:** Weighted combination of all three measures, normalised to [0, 1] where 1 = identical.
- **Use cases:** Comparing network snapshots over time, measuring graph edit impact, clustering graphs by structural similarity, detecting network evolution patterns.

### Temporal Graph (Time-Windowed Views)
- **File:** `TemporalGraph.java`
- **Complexity:** O(E) per snapshot
- **Algorithm:** Lightweight wrapper providing time-windowed views of a graph. Each edge has optional timestamps; snapshots filter to edges active at a specific time point or during a time range. Supports automatic window generation for dividing the timeline into equal intervals. Enables temporal analysis without modifying existing analyzers.

### Edge Persistence Analysis
- **File:** `EdgePersistenceAnalyzer.java`
- **Complexity:** O(W × E) where W = number of windows
- **Algorithm:** Classifies edges by their persistence across time windows: persistent (≥75% of windows), periodic (25–74%), or transient (<25%). Reveals relationship strength in social networks — persistent edges are strong ties, transient edges are one-time encounters.

### Network Growth Rate Analysis
- **File:** `GrowthRateAnalyzer.java`
- **Complexity:** O(W × E) where W = number of windows
- **Algorithm:** Tracks how network metrics (node count, edge count, density, clustering coefficient) change across time windows. Computes growth rates to identify expansion phases, stability periods, and network degradation.

### Network Statistics
- **File:** `GraphStats.java`
- **Complexity:** O(V + E)
- **Algorithm:** Basic network metrics — node/edge counts by category, density, degree distribution, and hub identification.

---

## Stochastic & Prediction

### Link Prediction
- **File:** `LinkPredictionAnalyzer.java`
- **Complexity:** O(V × avg_degree²) per metric
- **Algorithm:** Predicts missing edges using four similarity metrics: Common Neighbors, Jaccard Coefficient, Adamic-Adar Index, and Preferential Attachment.

### Influence Spread (Independent Cascade)
- **File:** `InfluenceSpreadSimulator.java`
- **Complexity:** O(simulations × (V + E))
- **Algorithm:** Monte Carlo simulation of information diffusion. Each activated node attempts to activate neighbors with a propagation probability. Averages over multiple simulations for seed-set evaluation.

### Random Walk Analysis
- **File:** `RandomWalkAnalyzer.java`
- **Complexity:** O(walk_length) per walk
- **Algorithm:** Simulates random walks for cover time analysis, hitting time estimation, mixing rate measurement, and random-walk-based centrality. Supports lazy random walks.

---

## Export & Generation

### GraphML Export
- **File:** `GraphMLExporter.java`
- **Complexity:** O(V + E)
- **Algorithm:** Serializes graph to GraphML XML format with node/edge attributes, compatible with Gephi, yEd, and other visualization tools.

### Synthetic Graph Generation
- **File:** `GraphGenerator.java`
- **Complexity:** Varies by topology
- **Algorithm:** Generates synthetic graphs for testing: Erdős-Rényi random, Barabási-Albert preferential attachment, Watts-Strogatz small-world, complete, cycle, path, star, grid, tree, and bipartite topologies.

---

## Complexity Summary

| Category | Algorithms | Best Complexity | Hardest Problem |
|----------|-----------|----------------|-----------------|
| Traversal | BFS, Dijkstra, Toposort | O(V + E) | — |
| Centrality | Degree, Betweenness, PageRank | O(V) | Betweenness O(V·(V+E)) |
| Community | Louvain, K-Core, Motifs, Cliques | O(V + E) | Cliques O(3^(V/3)) |
| Connectivity | Tarjan, Kosaraju, Resilience, κ/λ | O(V + E) | Resilience O(V·(V+E)) |
| Matching | Hopcroft-Karp, Vertex Cover | O(E√V) | Independent Set (NP-hard) |
| Coloring | DSatur, Chordal | O(V² + E) | General coloring (NP-hard) |
| Flow | Edmonds-Karp, Kruskal, MaxCut, Partitioning | O(E log E) | MaxCut (NP-hard) |
| Layout | Fruchterman-Reingold | O(iter·(V²+E)) | — |
| Structural | Diameter, Spectral, Treewidth, Struct. Holes, Entropy | O(V + E) | Entropy O(V²) eigenvalue |
| Comparison | Diff, Similarity, Persistence, Growth | O(V + E) | Similarity O(V²) eigenvalue |
| Temporal | TemporalGraph, Persistence, Growth | O(W × E) | — |
