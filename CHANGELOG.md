# Changelog

All notable changes to GraphVisual will be documented in this file.

## [1.9.0] — 2026-02-24

### 📊 Degree Distribution Analysis

- **DegreeDistributionAnalyzer** — comprehensive degree distribution analysis:
  - Statistical moments: min, max, mean, median, mode, variance, std dev, skewness, kurtosis
  - Power-law fitting via log-log OLS regression with γ exponent and R² goodness-of-fit
  - Scale-free network detection (γ ∈ [1.5, 3.5] and R² ≥ 0.7)
  - Network type classification (8 types: Empty, Trivial, Regular, Random, Scale-Free, Hub-Dominated, Heavy-Tailed, Heterogeneous)
  - Hub detection (degree > mean + 2σ)
  - Degree assortativity (Pearson correlation at edge endpoints)
  - Percentiles (P10–P99), frequency map, PDF/CDF/CCDF
- **DegreeDistributionResult** — immutable result object with all metrics
- 55 new tests

## [1.8.0] — 2026-02-24

### 🔗 Maximal Clique Finder

- **CliqueAnalyzer** — finds all maximal cliques using Bron-Kerbosch with pivot selection
  - Clique number ω(G), clique count, average size, coverage
  - Size distribution and node participation tracking
  - Clique overlap detection between pairs
- **CliqueResult** — immutable result with summary statistics
- 47 new tests

## [1.7.0] — 2026-02-22

### 🧅 K-Core Decomposition

- **KCoreDecomposition** — identifies dense subgraphs via iterative peeling
  - Degeneracy computation, shell structure, density profile
  - Cohesion scoring and structure classification
  - Coreness mapping for all vertices
- **KCoreResult** — immutable result with shells, distribution, and analysis
- 47 new tests

## [1.6.0] — 2026-02-22

### 📈 PageRank Analysis

- **PageRankAnalyzer** — power-iteration PageRank with array-based computation
  - Configurable damping factor, tolerance, and max iterations
  - Normalized ranks, hidden influence detection
  - PageRank vs. degree comparison (position shift analysis)
- **RankedNode**, **HiddenInfluence**, **RankComparison** — typed result objects
- 45 new tests

## [1.5.0] — 2026-02-22

### 🔴 Articulation Point & Bridge Analysis

- **ArticulationPointAnalyzer** — Tarjan's DFS for cut vertices and cut edges
  - Identifies vertices and edges whose removal disconnects the graph
  - Per-bridge component size analysis
  - Per-articulation-point degree and biconnected component counts
- **AnalysisResult**, **Bridge**, **ArticulationPointInfo** — typed result objects
- 45 new tests

## [1.4.0] — 2026-02-22

### 🎨 Graph Coloring

- **GraphColoringAnalyzer** — Welsh-Powell greedy vertex coloring
  - Degree-descending heuristic for near-optimal coloring
  - Custom vertex ordering support via `computeWithOrder()`
  - Color classes, chromatic bound, validation
- **ColoringResult** — immutable result with color assignment and summary
- 45 new tests

## [1.3.0] — 2026-02-21

### 🌲 Minimum Spanning Tree

- **MinimumSpanningTree** — Kruskal's algorithm with Union-Find
  - Handles disconnected graphs (spanning forest)
  - Per-component breakdown with vertex list, edges, and weight
- **MSTComponent** — individual component spanning tree
- **EdgeType** enum — centralised edge type codes, labels, colors, and thresholds (replaces scattered if/else chains)
- 45 new tests

## [1.2.0] — 2026-02-20

### ✨ Node Centrality Analysis

- **NodeCentralityAnalyzer** — Computes three classic centrality metrics for all nodes:
  - **Degree centrality** — normalized node degree (connections / max possible)
  - **Betweenness centrality** — Brandes' algorithm (O(V*E)), measures how often a node lies on shortest paths between other pairs
  - **Closeness centrality** — inverse of average shortest-path distance (Wasserman-Faust normalization for disconnected graphs)
- **Combined score** — weighted average (0.3 degree + 0.4 betweenness + 0.3 closeness) for overall importance ranking
- **Network topology classification** — auto-classifies graph as Trivial, Disconnected, Hub-and-Spoke, Distributed, or Hierarchical
- **Interactive centrality panel** — Compute button, metric dropdown (Combined/Degree/Betweenness/Closeness), top-10 ranking with medal icons, summary stats (averages + most central nodes per metric)
- **Programmatic API** — `getResult(nodeId)`, `getRankedResults()`, `getTopNodes(n)`, `getTopByMetric(n, metric)`, centrality maps, `getSummary()`, `classifyTopology()`
- 45 new tests covering all algorithms, edge cases, auto-compute, and CentralityResult model

## [1.0.0] — 2026-02-14

### 🎉 First Release

GraphVisual is a Java desktop application for studying community evolution in student social networks using Bluetooth proximity data and JUNG graph visualization.

### Features
- Interactive graph visualization with JUNG 2.0.1 (drag, zoom, rotate)
- Timeline playback across 92 days (March–May 2011) with speed controls
- 5 relationship categories with color-coded edges (friends, classmates, study groups, familiar strangers, strangers)
- Real-time threshold adjustment for meeting duration and frequency
- Cluster-based 3×3 layout grouping nodes by relationship type
- Edge weight visualization (line thickness = frequency × duration)
- New member highlighting (larger nodes for first appearances)
- Notes panel for per-timestamp annotations
- PNG export and edge list export

### Data Pipeline
- `matchImei` — Maps Bluetooth device nodes to IMEI identifiers
- `findMeetings` — Extracts meetings from Bluetooth proximity events using configurable time windows
- `addLocation` — Classifies meeting locations (public/classroom/pathway) via WiFi access point correlation
- `Network` — Generates parameterized edge-list files from PostgreSQL meeting queries

### Security
- Environment-based database credentials (no hardcoded secrets)
- Parameterized SQL queries (PreparedStatement) throughout — no string concatenation
- Try-with-resources for all JDBC connections and result sets
- CodeQL security scanning enabled

### Infrastructure
- GitHub Actions CI (build + test on JDK 11/17)
- CodeQL automated security analysis
- JUnit 4 test suite (EdgeTest, UtilMethodsTest — 25 test cases)
- GitHub Copilot agent configuration for automated development
- Professional README with badges, architecture diagrams, and setup guide
