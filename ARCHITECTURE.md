# Architecture

This document describes the source structure, design patterns, and key components of GraphVisual.

## Project Layout

```
GraphVisual/
├── Gvisual/
│   ├── src/
│   │   ├── gvisual/           # Visualization + analysis (73 classes)
│   │   │   ├── Main.java      # Swing GUI — graph panel, timeline, controls
│   │   │   ├── edge.java      # Edge model (type, vertices, weight, label, timestamps)
│   │   │   ├── EdgeType.java  # Enum — relationship categories, colors, defaults
│   │   │   ├── GraphStats.java           # Network metrics (density, degree, hubs)
│   │   │   ├── GraphMLExporter.java      # GraphML XML export
│   │   │   ├── GraphGenerator.java       # Synthetic graph topologies
│   │   │   └── [46 analyzers + 21 utilities — see below]
│   │   └── app/               # Data pipeline — DB → edge files
│   │       ├── Network.java       # SQL → edge-list generation
│   │       ├── Util.java          # Database connection factory
│   │       ├── findMeetings.java  # Bluetooth events → meeting extraction
│   │       ├── addLocation.java   # WiFi-based meeting location classification
│   │       └── matchImei.java     # Device → IMEI mapping
│   ├── test/
│   │   ├── gvisual/           # 66 test classes (~2100+ tests total)
│   │   └── app/               # Pipeline utility tests (2 test classes)
│   ├── lib/                   # JUNG 2.0.1, PostgreSQL JDBC, Java3D, Commons IO
│   └── images/                # UI icons (play, pause, stop, legend colors)
├── pom.xml                    # Maven build (Java 11+, shade plugin for fat JAR)
├── build.xml                  # Legacy Ant build (NetBeans)
└── docs/                      # GitHub Pages site
```

## Analyzers

The analysis engine consists of 46 analyzer classes and 21 utility/infrastructure classes (67 non-Main classes). Each analyzer follows the same pattern:

1. Constructor takes a `Graph<String, edge>` (JUNG graph)
2. Validates input (null check → `IllegalArgumentException`)
3. Provides one or more analysis methods returning immutable result objects
4. Includes a `generateSummary()` method for human-readable output

### Analyzer Reference

| Class | Algorithm | Complexity | Purpose |
|-------|-----------|------------|---------|
| `ArticulationPointAnalyzer` | Tarjan's DFS | O(V+E) | Cut vertices and bridges — single points of failure |
| `BipartiteAnalyzer` | BFS 2-coloring + Hopcroft-Karp | O(V+E) / O(E√V) | Bipartiteness test, maximum matching, vertex cover, independent set |
| `ChordalGraphAnalyzer` | Lexicographic BFS + PEO verification | O(V+E) | Chordal (triangulated) graph detection, perfect elimination ordering |
| `CliqueAnalyzer` | Bron-Kerbosch (Tomita pivot) | O(3^(n/3)) | All maximal cliques — fully connected subgroups |
| `CommunityDetector` | Connected components | O(V+E) | Community detection with size ranking and metrics |
| `CycleAnalyzer` | DFS back-edges + BFS girth | O(V+E) | Cycle detection, girth, fundamental cycle basis, bounded enumeration |
| `DegreeDistributionAnalyzer` | Statistical analysis | O(V) | Degree histogram, power-law fit, network classification |
| `DominatingSetAnalyzer` | Greedy + verification | O(V²) | Dominating set computation and analysis |
| `EdgePersistenceAnalyzer` | Temporal overlay | O(E·T) | Classifies edges by persistence across temporal snapshots |
| `EulerianPathAnalyzer` | Hierholzer's algorithm | O(V+E) | Euler path/circuit detection, degree classification, Chinese Postman |
| `FeedbackVertexSetAnalyzer` | Greedy + cycle breaking | O(V·E) | Feedback vertex set — minimum vertices to remove to make acyclic |
| `GraphAnomalyDetector` | Z-score outlier detection | O(V+E) | Identifies nodes with statistically unusual structural properties |
| `GraphClusterQualityAnalyzer` | Modularity + metrics | O(V+E) | Evaluates clustering quality: modularity, conductance, NMI |
| `GraphColoringAnalyzer` | Welsh-Powell (greedy) | O(V² + E) | Vertex coloring — chromatic number estimation |
| `GraphDiameterAnalyzer` | All-pairs BFS | O(V(V+E)) | Diameter, radius, eccentricity, center, periphery |
| `GraphDiffAnalyzer` | Set intersection / symmetric diff | O(V+E) | Structural diff of two graphs — added/removed/shared nodes and edges |
| `GraphEntropyAnalyzer` | Shannon / Von Neumann entropy | O(V+E) | Information-theoretic graph measures |
| `GraphIsomorphismAnalyzer` | Backtracking with degree pruning | O(V!) worst | Graph isomorphism check via vertex mapping enumeration |
| `GraphPartitioner` | Spectral bisection + KL refinement | O(V² + E) | Balanced k-way graph partitioning minimizing edge cuts |
| `GraphResilienceAnalyzer` | Sequential removal simulation | O(V(V+E)) | Random, degree, and betweenness attack resilience with robustness index |
| `GraphSimilarityAnalyzer` | Entropy-based comparison | O(V+E) | Compares two graphs using structural similarity metrics |
| `GraphSparsificationAnalyzer` | Spectral / random sampling | O(E log V) | Reduces edge count while preserving structural properties |
| `GrowthRateAnalyzer` | Time-series metrics | O(T·(V+E)) | Tracks graph metric changes over time (node count, edges, density) |
| `HamiltonianAnalyzer` | Backtracking + heuristics | O(V!) worst | Hamiltonian path/cycle detection |
| `IndependentSetAnalyzer` | Greedy + backtracking | O(2^V) worst | Maximum independent set computation |
| `InfluenceSpreadSimulator` | IC / LT / SIR models | O(seeds · V · sims) | Simulates influence/contagion spread through networks |
| `KCoreDecomposition` | Iterative peeling | O(V+E) | K-core shells — dense subgraph hierarchy |
| `KTrussAnalyzer` | Triangle-based decomposition | O(E^1.5) | K-truss cohesive subgraphs based on triangle support |
| `LineGraphAnalyzer` | Edge-to-vertex transform | O(E·d) | Line graph L(G) construction and analysis |
| `LinkPredictionAnalyzer` | Common Neighbors / Jaccard / Adamic-Adar | O(V²·d) | Predict missing edges from structural similarity |
| `MaxCutAnalyzer` | Greedy + local search | O(V·E) | Maximum cut approximation for undirected graphs |
| `MetricDimensionAnalyzer` | BFS + greedy resolving set | O(V²·E) | Metric dimension — minimum resolving set of vertices |
| `MinimumSpanningTree` | Kruskal's + Union-Find | O(E log E) | MST / forest with component analysis |
| `MotifAnalyzer` | Subgraph enumeration | O(V^k) | 3/4-node motif detection, census, z-scores, network characterization |
| `NetworkFlowAnalyzer` | Edmonds-Karp (BFS augmenting paths) | O(VE²) | Max flow, min cut, flow decomposition into paths |
| `NodeCentralityAnalyzer` | Brandes + BFS | O(VE) | Degree, betweenness, closeness centrality |
| `NodeSimilarityAnalyzer` | Jaccard / Cosine / SimRank | O(V²·d) | Pairwise node similarity within a graph |
| `PageRankAnalyzer` | Power iteration | O(k(V+E)) | PageRank scores with convergence control |
| `PlanarGraphAnalyzer` | LR-planarity + face enumeration | O(V+E) | Planarity testing, face enumeration, dual graph construction |
| `RandomWalkAnalyzer` | Monte Carlo simulation | O(walks × steps) | Random walk statistics, hitting times, stationary distribution |
| `SignedGraphAnalyzer` | Balance theory + frustration | O(V+E) | Signed graph analysis — structural balance, frustration index |
| `SmallWorldAnalyzer` | Clustering + path length | O(V(V+E)) | Small-world network detection (high clustering, short paths) |
| `SpectralAnalyzer` | Eigenvalue decomposition (Jacobi) | O(V³) | Algebraic connectivity, Fiedler vector, spectral gap, Cheeger bound |
| `SteinerTreeAnalyzer` | Approximation algorithms | O(T·V·E) | Minimum Steiner tree computation for terminal vertex sets |
| `StronglyConnectedComponentsAnalyzer` | Tarjan's + Kosaraju's | O(V+E) | SCCs, condensation DAG, bridge edges, connectivity queries |
| `StructuralHoleAnalyzer` | Burt's constraint + effective size | O(V·d²) | Brokerage opportunities and structural holes in networks |
| `TopologicalSortAnalyzer` | Kahn's + DFS cycle detection | O(V+E) | Topological ordering, cycle detection, critical path |
| `TournamentAnalyzer` | Round-robin + Copeland | O(V²) | Tournament (directed complete) graph analysis |
| `TreeAnalyzer` | DFS + diameter + LCA | O(V+E) | Comprehensive tree analysis: height, balance, centroids, LCA |
| `TreewidthAnalyzer` | Min-degree + elimination | O(V·E) | Treewidth estimation and tree decomposition |
| `VertexConnectivityAnalyzer` | Max-flow reduction | O(V²·E) | Vertex and edge connectivity of undirected graphs |
| `VertexCoverAnalyzer` | Greedy + 2-approximation | O(V+E) | Minimum vertex cover computation |

### Utility & Infrastructure Classes

| Class | Purpose |
|-------|---------|
| `AdjacencyMatrixHeatmap` | Swing JPanel rendering an adjacency matrix as a color heatmap with BFS-ordered nodes. |
| `AnalysisResult` | Wraps analysis outcomes — handles completion, timeout, error, and partial results. |
| `AnalysisTask` | Lightweight wrapper adding timeout, progress reporting, and cancellation to analysis jobs. |
| `CsvReportExporter` | Exports comprehensive per-node metrics (degree, centrality, clustering) to CSV. |
| `edge` | Core edge model — type, endpoints, weight, label, timestamps (supports temporal/interval edges). |
| `EdgeType` | Enum centralizing relationship codes (`f`, `c`, `s`, `fs`, `sg`), display labels, colors, and default thresholds. |
| `ForceDirectedLayout` | Force-directed (Fruchterman-Reingold) 2D layout with repulsion, attraction, and cooling schedule. |
| `GraphGenerator` | Creates synthetic graphs: Complete, Cycle, Star, Path, Grid, Petersen, Bipartite, Binary Tree, Random (Erdős–Rényi), Watts-Strogatz small-world. |
| `GraphMLExporter` | Exports to GraphML (XML) for interop with Gephi, Cytoscape, NetworkX, yEd. Includes edge attributes. |
| `GraphQueryEngine` | Chainable query language for filtering and extracting subgraphs by degree, centrality, neighborhood. |
| `GraphRenderers` | Centralized rendering transformers for the Swing graph visualization (vertex shapes, colors, labels). |
| `GraphSampler` | Representative subgraph sampling: random node, random edge, snowball, forest fire strategies. |
| `GraphStats` | Aggregate network metrics: density, average/max degree, hub identification, category breakdowns. Cached single-pass computation. |
| `GraphSummarizer` | Generates human-readable narrative summaries of graph structure, topology, and key properties. |
| `GraphUtils` | Shared graph traversal utilities: adjacency maps, BFS, components, shortest paths, betweenness centrality. |
| `InteractiveHtmlExporter` | Exports graphs as self-contained interactive HTML files using D3.js force simulation. |
| `LaplacianBuilder` | Constructs various Laplacian matrices (standard, normalized, signless) from a JUNG graph. |
| `Main` | Swing application entry point (2399 lines) — graph panel, timeline, toolbar, statistics, centrality rankings. |
| `SubgraphExtractor` | Extracts focused subgraphs based on vertex criteria, k-hop neighborhoods, or edge filters. |
| `TemporalGraph` | Lightweight JUNG graph wrapper providing time-windowed views for temporal network analysis. |

## Design Patterns

### Analyzer Pattern
Every analyzer follows this contract:
```java
public class FooAnalyzer {
    private final Graph<String, edge> graph;

    public FooAnalyzer(Graph<String, edge> graph) {
        if (graph == null) throw new IllegalArgumentException("Graph must not be null");
        this.graph = graph;
    }

    // Immutable result class with getters
    public static class FooResult { ... }

    // Core analysis
    public FooResult analyze() { ... }

    // Human-readable summary
    public String generateSummary() { ... }
}
```

### Immutability
All result objects wrap collections in `Collections.unmodifiable*()` and copy inputs in constructors. Callers cannot mutate analysis results.

### Caching (GraphStats)
`GraphStats` computes per-vertex degree data in a single pass (`ensureVertexStatsComputed()`), cached for reuse by `getMaxDegree()`, `getIsolatedNodeCount()`, and `getTopNodes()`. Edge weight sums are similarly cached.

## Data Pipeline

The `app/` package handles the Bluetooth-to-graph pipeline:

```
Raw Bluetooth events (event_3 table, rssi ≥ -60)
    │
    ▼
findMeetings ── 5-minute sliding window ──→ meeting table
    │
    ▼
matchImei ── device_1 table ──→ deviceID mapping
    │
    ▼
addLocation ── WiFi AP intersection ──→ meeting.location (public/class/path)
    │
    ▼
Network ── parameterized SQL ──→ edge-list files (f/c/s/fs/sg types)
    │
    ▼
Main.java ── JUNG rendering ──→ interactive visualization
```

See [DATABASE.md](DATABASE.md) for full schema documentation.

## GUI (Main.java)

`Main.java` (2399 lines) is the Swing application entry point. Key components:

| Component | Description |
|-----------|-------------|
| **Image Panel** | JUNG `VisualizationViewer` canvas with drag/zoom/rotate |
| **Timeline Panel** | Day slider (1–92) with play/pause/stop and speed control |
| **Toolbar** | Interaction mode toggle (transform vs. pick), export buttons |
| **Category Panel** | Toggle edge visibility per type, adjust thresholds via sliders |
| **Notes Pane** | Per-timestamp annotation text area |
| **Statistics Panel** | Live `GraphStats` metrics display |
| **Centrality Panel** | `NodeCentralityAnalyzer` results with sortable rankings |

## Testing

68 test classes with ~2100+ tests covering all analyzers, utilities, and security:

| Test Class | Tests | Covers |
|------------|-------|--------|
| `AnalysisTaskTest` | — | Timeout, cancellation, partial results |
| `ArticulationPointAnalyzerTest` | 34 | Cut vertices, bridges, biconnected components |
| `BipartiteAnalyzerTest` | — | Bipartiteness, matching, vertex cover |
| `ChordalGraphAnalyzerTest` | — | Chordal detection, perfect elimination ordering |
| `CliqueAnalyzerTest` | 70 | Maximal cliques, clique cover, independence number |
| `CommunityDetectorTest` | 26 | Connected components, community metrics |
| `CycleAnalyzerTest` | — | Cycle detection, girth, cycle basis |
| `DegreeDistributionAnalyzerTest` | 55 | Degree stats, histograms, power-law fitting |
| `DominatingSetAnalyzerTest` | — | Dominating set computation and verification |
| `EdgePersistenceAnalyzerTest` | — | Temporal edge persistence classification |
| `EdgeTest` | 18 | Edge model getters, setters, temporal methods |
| `EulerianPathAnalyzerTest` | — | Euler path/circuit detection |
| `FeedbackVertexSetAnalyzerTest` | — | FVS computation and cycle breaking |
| `ForceDirectedLayoutTest` | — | Layout convergence and positioning |
| `GraphAnomalyDetectorTest` | — | Structural anomaly detection |
| `GraphClusterQualityAnalyzerTest` | — | Modularity, conductance, NMI |
| `GraphColoringAnalyzerTest` | 35 | Welsh-Powell coloring, chromatic bounds |
| `GraphDiameterAnalyzerTest` | 11 | Diameter, radius, eccentricity |
| `GraphDiffAnalyzerTest` | — | Structural diff of two graphs |
| `GraphEntropyAnalyzerTest` | — | Shannon and Von Neumann entropy |
| `GraphGeneratorTest` | 70 | All 10 synthetic topologies |
| `GraphIsomorphismAnalyzerTest` | — | Isomorphism detection |
| `GraphMLExporterTest` | 31 | XML export, attribute preservation |
| `GraphPartitionerTest` | — | Balanced partitioning, edge cuts |
| `GraphQueryEngineTest` | — | Query language filtering |
| `GraphResilienceAnalyzerTest` | — | Attack resilience simulation |
| `GraphSamplerTest` | — | Subgraph sampling strategies |
| `GraphSimilarityAnalyzerTest` | — | Graph comparison metrics |
| `GraphSparsificationAnalyzerTest` | — | Edge reduction strategies |
| `GraphStatsTest` | 55 | Density, degree, hubs, categories, caching |
| `GraphUtilsTest` | 42 | BFS, components, adjacency, shortest paths |
| `GrowthRateAnalyzerTest` | — | Temporal metric tracking |
| `HamiltonianAnalyzerTest` | — | Hamiltonian path/cycle detection |
| `IndependentSetAnalyzerTest` | — | Maximum independent set |
| `InfluenceSpreadSimulatorTest` | — | IC/LT/SIR spread models |
| `KCoreDecompositionTest` | 44 | K-core shells, degeneracy |
| `KTrussAnalyzerTest` | — | K-truss decomposition |
| `LineGraphAnalyzerTest` | — | Line graph construction |
| `LinkPredictionAnalyzerTest` | 10 | Edge prediction metrics |
| `MaxCutAnalyzerTest` | — | Maximum cut approximation |
| `MetricDimensionAnalyzerTest` | — | Resolving set computation |
| `MinimumSpanningTreeTest` | 41 | Kruskal's MST, forest components |
| `MotifAnalyzerTest` | — | Motif census and z-scores |
| `NetworkFlowAnalyzerTest` | — | Max flow, min cut |
| `NodeCentralityAnalyzerTest` | 46 | Degree, betweenness, closeness centrality |
| `NodeSimilarityAnalyzerTest` | — | Jaccard, Cosine, SimRank |
| `PageRankAnalyzerTest` | 77 | PageRank convergence, damping factor |
| `PlanarGraphAnalyzerTest` | — | Planarity testing, face enumeration |
| `RandomWalkAnalyzerTest` | — | Walk statistics, hitting times |
| `SecurityTest` | 17 | JDBC host validation, path traversal protection |
| `ShortestPathFinderTest` | 24 | BFS and weighted shortest paths |
| `SignedGraphAnalyzerTest` | — | Balance theory, frustration index |
| `SmallWorldAnalyzerTest` | — | Small-world detection |
| `SpectralAnalyzerTest` | — | Eigenvalues, Fiedler vector |
| `SteinerTreeAnalyzerTest` | — | Steiner tree approximation |
| `StronglyConnectedComponentsAnalyzerTest` | — | SCCs, condensation DAG |
| `StructuralHoleAnalyzerTest` | — | Burt's constraint, brokerage |
| `SubgraphExtractorTest` | — | Subgraph extraction filters |
| `TemporalGraphTest` | — | Time-windowed graph views |
| `TopologicalSortAnalyzerTest` | 42 | Topo sort, cycle detection, critical path |
| `TournamentAnalyzerTest` | — | Tournament graph analysis |
| `TreeAnalyzerTest` | — | Tree properties, centroids, LCA |
| `TreewidthAnalyzerTest` | — | Treewidth estimation, tree decomposition |
| `UtilMethodsTest` | — | Database connection factory |
| `VertexConnectivityAnalyzerTest` | — | Vertex/edge connectivity |
| `VertexCoverAnalyzerTest` | — | Vertex cover computation |

Tests use JUnit 4 with JUNG `UndirectedSparseGraph` and `DirectedSparseGraph` for fixture construction.

## Build

### Maven (recommended)
```bash
# Install vendored JARs
mvn initialize -P install-local-deps

# Build + test
mvn package -B

# Fat JAR output
target/graphvisual-1.1.0-all.jar
```

### Ant (legacy)
```bash
cd Gvisual && ant build
```

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| JUNG | 2.0.1 | Graph data structures, algorithms, visualization |
| collections-generic | 4.01 | Generic collection interfaces (JUNG dependency) |
| Colt | 1.2.0 | Scientific computing (JUNG dependency) |
| Commons IO | 1.4 | File I/O utilities |
| PostgreSQL JDBC | 8.3-604 | Database connectivity |
| Java3D vecmath | 1.3.1 | 3D math / rendering support |
| JUnit | 4.13.2 | Unit testing (test scope) |
