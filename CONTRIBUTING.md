# Contributing to GraphVisual

Thanks for your interest in contributing! GraphVisual is a large-scale graph theory toolkit with 147 source files, 106 test classes, and 70+ documentation pages. This guide will help you navigate the codebase and contribute effectively.

## Table of Contents

- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [Module Catalog](#module-catalog)
- [Building & Testing](#building--testing)
- [Code Style](#code-style)
- [Where to Contribute](#where-to-contribute)
- [Making Changes](#making-changes)
- [Pull Request Process](#pull-request-process)
- [Commit Conventions](#commit-conventions)
- [Issue Guidelines](#issue-guidelines)
- [Architecture Deep Dive](#architecture-deep-dive)

## Development Setup

### Prerequisites

- **Java JDK 8** or later (JDK 11 or 17 recommended — CI tests against both)
- **Git**
- **Maven 3.6+** (for dependency resolution via `pom.xml`)
- **PostgreSQL** (optional — only needed for the data ingestion pipeline)

### IDE Setup

**IntelliJ IDEA (recommended):**
1. Open → select the repo root (detects `pom.xml` automatically)
2. Mark `Gvisual/src` as Sources Root and `Gvisual/test` as Test Sources Root
3. Add all JARs in `Gvisual/lib/` as project libraries
4. Set Project SDK to JDK 11 or 17

**Eclipse:**
1. Import → Existing Maven Project → select repo root
2. Right-click `Gvisual/lib/*.jar` → Build Path → Add to Build Path
3. Ensure `Gvisual/test` is on the test classpath

**VS Code:**
1. Install the "Extension Pack for Java" extension
2. Open the repo root — the Java extension will detect `pom.xml`
3. If library resolution fails, add `Gvisual/lib/*.jar` to `.classpath` manually

### Quick Start

```bash
git clone https://github.com/sauravbhattacharya001/GraphVisual.git
cd GraphVisual/Gvisual

# Compile source
mkdir -p build/classes
find src -name '*.java' > sources.txt
javac -cp "$(find lib -name '*.jar' | tr '\n' ':')" -d build/classes @sources.txt

# Compile and run tests
mkdir -p build/test/classes
find test -name '*.java' > test_sources.txt
javac -cp "build/classes:$(find lib -name '*.jar' | tr '\n' ':')" -d build/test/classes @test_sources.txt
java -cp "build/classes:build/test/classes:$(find lib -name '*.jar' | tr '\n' ':')" \
  org.junit.runner.JUnitCore gvisual.EdgeTest gvisual.GraphStatsTest
```

### Database Setup (optional)

Only needed for the data pipeline (`app/` package):

```bash
export DB_HOST=localhost
export DB_USER=your_user
export DB_PASS=your_pass
```

The schema expects tables: `meeting`, `event_3`, `device_1`, `deviceID`.

## Project Structure

```
GraphVisual/
├── .github/
│   ├── workflows/          # CI, CodeQL, coverage, Docker, Pages, publish
│   ├── copilot-instructions.md
│   ├── copilot-setup-steps.yml
│   ├── dependabot.yml
│   └── ISSUE_TEMPLATE/     # Bug report, feature request, performance forms
├── Gvisual/
│   ├── src/
│   │   ├── gvisual/        # 140+ source files — core library
│   │   └── app/            # Data pipeline (DB → edge-list)
│   ├── test/
│   │   ├── gvisual/        # 106 test classes
│   │   └── app/            # Pipeline utility tests
│   ├── lib/                # JUNG 2.0.1, PostgreSQL JDBC, Commons IO
│   └── images/             # UI icons and legend assets
├── Vidly/                  # Separate .NET project (not part of GraphVisual core)
├── docs/                   # 70+ HTML documentation pages + CSS
├── pom.xml                 # Maven build descriptor
├── Dockerfile              # Multi-stage Docker build
├── ALGORITHMS.md           # Algorithm documentation
├── ARCHITECTURE.md         # System architecture guide
├── TESTING.md              # Testing conventions and coverage gaps
├── SECURITY.md             # Security policy
└── DATABASE.md             # Database schema documentation
```

## Module Catalog

The `gvisual` package contains 140+ classes organized into functional areas. Here's a map to help you find your way:

### Core & GUI
| Class | Purpose |
|-------|---------|
| `Main` | Swing GUI — graph panel, timeline controls, visualization viewer |
| `Edge`, `EdgeType`, `EdgeTypeRegistry` | Edge model, type definitions, type management |
| `GraphStats`, `StatsPanel`, `GraphStatsDashboard` | Network metrics, stats UI panel, dashboard |
| `ToolbarBuilder` | Toolbar construction and action wiring |
| `GraphRenderers` | Custom JUNG vertex/edge rendering transformers |
| `RandomGraphDialog`, `RandomGraphGenerator` | Graph generation UI and algorithms |
| `TemporalGraph`, `GrowthRateAnalyzer` | Time-series graph model, growth analysis |
| `QuadTree` | Spatial indexing for force-directed layout |

### Graph Algorithms — Structure & Decomposition
| Class | Purpose |
|-------|---------|
| `CommunityDetector` | Connected component analysis |
| `LouvainCommunityDetector` | Louvain modularity-based community detection |
| `CommunityEvolutionTracker` | Track community changes over time |
| `KCoreDecomposition` | k-core subgraph extraction |
| `KTrussAnalyzer` | k-truss decomposition |
| `ArticulationPointAnalyzer` | Cut vertices and biconnected components |
| `VertexConnectivityAnalyzer` | Vertex connectivity and separating sets |
| `StronglyConnectedComponentsAnalyzer` | Tarjan's SCC decomposition |
| `TreeAnalyzer`, `TreewidthAnalyzer` | Tree recognition, treewidth computation |
| `ChordalGraphAnalyzer` | Perfect elimination ordering, chordal recognition |

### Graph Algorithms — Paths & Flows
| Class | Purpose |
|-------|---------|
| `ShortestPathFinder` | BFS (unweighted) and Dijkstra (weighted) pathfinding |
| `GraphPathExplorer` | All-pairs paths, path enumeration |
| `NetworkFlowAnalyzer` | Max-flow / min-cut computation |
| `EulerianPathAnalyzer` | Euler path/circuit detection and construction |
| `HamiltonianAnalyzer` | Hamiltonian path/cycle analysis |
| `TopologicalSortAnalyzer` | DAG ordering, cycle detection |
| `RandomWalkAnalyzer` | Random walk simulation and hitting times |

### Graph Algorithms — Coloring & Covering
| Class | Purpose |
|-------|---------|
| `GraphColoringAnalyzer` | Chromatic number, greedy coloring strategies |
| `ChromaticPolynomialCalculator` | Chromatic polynomial computation |
| `CliqueAnalyzer`, `CliqueCoverAnalyzer` | Clique enumeration, clique cover |
| `IndependentSetAnalyzer` | Maximum independent set |
| `VertexCoverAnalyzer` | Minimum vertex cover |
| `DominatingSetAnalyzer` | Minimum dominating set |
| `FeedbackVertexSetAnalyzer` | Feedback vertex set computation |
| `MaxCutAnalyzer` | Maximum cut approximation |
| `MetricDimensionAnalyzer` | Resolving sets and metric dimension |

### Graph Algorithms — Centrality & Similarity
| Class | Purpose |
|-------|---------|
| `NodeCentralityAnalyzer` | Degree, betweenness, closeness, eigenvector centrality |
| `PageRankAnalyzer` | PageRank computation |
| `EdgeBetweennessAnalyzer` | Edge betweenness centrality |
| `GraphCentralityCorrelator` | Cross-centrality correlation analysis |
| `NodeSimilarityAnalyzer` | Jaccard, cosine, structural similarity |
| `LinkPredictionAnalyzer` | Link prediction (common neighbors, Adamic-Adar, etc.) |
| `RichClubAnalyzer` | Rich-club coefficient analysis |
| `StructuralHoleAnalyzer` | Burt's structural holes, constraint, effective size |

### Graph Algorithms — Spectral & Algebraic
| Class | Purpose |
|-------|---------|
| `SpectralAnalyzer` | Eigenvalue decomposition, spectral gap |
| `LaplacianBuilder` | Laplacian matrix construction |
| `GraphSpectrumAnalyzer` | Adjacency spectrum analysis |
| `GraphEntropyAnalyzer` | Graph entropy measures |
| `WienerIndexCalculator` | Wiener index (sum of all shortest paths) |
| `BandwidthMinimizer` | Graph bandwidth optimization |

### Graph Algorithms — Special Structures
| Class | Purpose |
|-------|---------|
| `BipartiteAnalyzer` | Bipartiteness testing, matching |
| `PlanarGraphAnalyzer` | Planarity testing, face enumeration |
| `PerfectGraphAnalyzer` | Perfect graph recognition |
| `TournamentAnalyzer` | Tournament properties, Hamiltonian ordering |
| `SignedGraphAnalyzer` | Signed graph balance, frustration index |
| `GraphMinorAnalyzer` | Minor testing and contraction |
| `LineGraphAnalyzer` | Line graph construction and analysis |

### Graph Algorithms — Network Analysis
| Class | Purpose |
|-------|---------|
| `GraphNetworkProfiler` | Comprehensive structural profiling |
| `NetworkRoleClassifier` | Structural role classification |
| `SmallWorldAnalyzer` | Small-world coefficient, clustering |
| `GraphResilienceAnalyzer` | Attack/failure resilience simulation |
| `GraphAnomalyDetector` | Anomalous node/edge detection |
| `InfluenceSpreadSimulator` | Influence cascade simulation (IC, LT models) |
| `GraphClusterQualityAnalyzer` | Modularity, silhouette, NMI |
| `DegreeDistributionAnalyzer` | Degree distribution, power-law fitting |
| `GraphHealthChecker` | Graph quality diagnostics |

### Graph Operations & Transformations
| Class | Purpose |
|-------|---------|
| `GraphGenerator` | Programmatic graph generation (Erdős–Rényi, BA, etc.) |
| `FamousGraphLibrary` | Named graph catalog (Petersen, K₅, etc.) |
| `GraphMerger` | Graph union/intersection/difference |
| `GraphCompressor` | Graph compression strategies |
| `GraphComplementAnalyzer` | Complement graph construction |
| `GraphProductCalculator` | Cartesian, tensor, strong, lexicographic products |
| `GraphPowerCalculator` | k-th power graph construction |
| `GraphDegreeSequenceRandomizer` | Degree-preserving rewiring |
| `SubgraphExtractor` | Induced/edge subgraph extraction |
| `SubgraphPatternMatcher` | Subgraph isomorphism matching |
| `GraphIsomorphismChecker`, `GraphIsomorphismAnalyzer` | Graph isomorphism testing |
| `GraphSampler` | Graph sampling strategies |
| `GraphSparsificationAnalyzer` | Edge sparsification |
| `GraphPartitioner` | Graph partitioning (Kernighan-Lin, spectral) |
| `GraphVoronoiPartitioner` | Voronoi-based partitioning |
| `GraphSimilarityAnalyzer` | Graph-level similarity metrics |
| `GraphDiffAnalyzer` | Structural diff between two graphs |
| `MotifAnalyzer`, `GraphMotifFinder` | Network motif detection |

### Layout Engines
| Class | Purpose |
|-------|---------|
| `ForceDirectedLayout` | Force-directed (Fruchterman-Reingold) |
| `HierarchicalLayout` | Sugiyama-style hierarchical layout |
| `CircularLayout` | Circular/radial layout |
| `SpectralLayout` | Spectral embedding layout |
| `GraphLayoutComparer` | Layout quality comparison |
| `GraphDrawingQualityAnalyzer` | Crossing number, angular resolution |

### Export & Visualization
| Class | Purpose |
|-------|---------|
| `GraphMLExporter`, `GexfExporter`, `JsonGraphExporter` | Standard graph formats |
| `DotExporter`, `DimacsExporter` | DOT (Graphviz), DIMACS formats |
| `SvgExporter`, `TikzExporter` | SVG, LaTeX TikZ export |
| `InteractiveHtmlExporter`, `GraphDiffHtmlExporter` | Interactive HTML visualizations |
| `CsvReportExporter`, `NetworkFlowExporter` | Tabular data export |
| `AdjacencyListExporter`, `AdjacencyMatrixHeatmap` | Adjacency representations |
| `CentralityRadarExporter` | Radar chart export |
| `GraphStorytellerExporter` | Natural-language graph narratives |
| `GraphTimelineExporter` | Temporal evolution export |
| `GraphAsciiRenderer` | Terminal-friendly graph rendering |
| `NetworkReportGenerator` | Comprehensive HTML reports |
| `GraphSummarizer` | Graph summary generation |

### Analysis Infrastructure
| Class | Purpose |
|-------|---------|
| `AnalysisTask`, `AnalysisResult` | Async analysis task framework |
| `GraphAlgorithmAnimator` | Step-by-step algorithm animation |
| `GraphAnnotationManager` | Vertex/edge annotation management |
| `GraphBenchmarkSuite` | Performance benchmarking |
| `GraphQueryEngine` | Graph query language (GQL-lite) |
| `GraphIntelligenceAdvisor` | AI-driven analysis recommendations |
| `GraphUtils` | Shared graph utilities |
| `ExportActions`, `ExportUtils` | Export action registry, shared helpers |
| `MinimumSpanningTree`, `SteinerTreeAnalyzer` | MST and Steiner tree |
| `EdgePersistenceAnalyzer` | Edge stability over time |
| `TimelineMetricsRecorder` | Temporal metric recording |
| `GraphDistanceDistribution` | Distance histogram analysis |
| `GraphLabelingAnalyzer` | Graph labeling (graceful, harmonious) |
| `GraphRegularityAnalyzer` | Regularity testing |
| `GraphSymmetryAnalyzer` | Automorphism group, orbit analysis |

### Panel Controllers (GUI wiring)
| Class | Purpose |
|-------|---------|
| `CentralityPanelController` | Centrality analysis panel |
| `CommunityPanelController` | Community detection panel |
| `ArticulationPanelController` | Bridges/cut-vertices panel |
| `MSTPanelController` | MST analysis panel |
| `PathPanelController` | Pathfinding panel |
| `EgoPanelController` | Ego network panel |
| `ResiliencePanelController` | Resilience analysis panel |

### Data Pipeline (`app/` package)

| Class | Purpose |
|-------|---------|
| `Network` | Edge-list generation from database |
| `Util` | Database connection factory |
| `findMeetings` | Bluetooth event → meeting extraction |
| `addLocation` | Meeting location classification |
| `matchImei` | Device ↔ IMEI matching |

## Building & Testing

### Compile

```bash
cd Gvisual
mkdir -p build/classes
find src -name '*.java' > sources.txt
javac -cp "$(find lib -name '*.jar' | tr '\n' ':')" -d build/classes @sources.txt
```

### Run Tests

```bash
mkdir -p build/test/classes
find test -name '*.java' > test_sources.txt
javac -cp "build/classes:$(find lib -name '*.jar' | tr '\n' ':')" -d build/test/classes @test_sources.txt

# Run specific test classes
java -cp "build/classes:build/test/classes:$(find lib -name '*.jar' | tr '\n' ':')" \
  org.junit.runner.JUnitCore gvisual.EdgeTest gvisual.GraphStatsTest

# Run all tests (use a test runner or list all test classes)
```

The test suite has **106 test classes** covering most analyzers and exporters. CI runs a subset against JDK 11 and 17 — see `.github/workflows/ci.yml` for the canonical list.

### Docker

```bash
docker build -t graphvisual .
docker run graphvisual
```

The Docker build compiles all source, runs the full test suite, and produces a fat JAR.

### Code Coverage

Coverage is tracked via `.github/workflows/coverage.yml`. When adding new analyzers, add corresponding test classes to maintain coverage.

## Code Style

### Naming Conventions

- **Classes:** `PascalCase` (e.g., `GraphStats`, `ShortestPathFinder`)
- **Methods/variables:** `camelCase` (e.g., `calculateDensity`, `edgeWeight`)
- **Constants:** `UPPER_SNAKE_CASE`
- **Packages:** lowercase (`gvisual`, `app`)
- **Test classes:** `<ClassName>Test` (e.g., `EdgeTest`, `GraphStatsTest`)

> **Note:** Some legacy classes use lowercase names (e.g., `findMeetings.java`). Use `PascalCase` for all new classes.

### General Guidelines

- Keep methods focused — one responsibility per method
- Add Javadoc to all public methods and classes
- No hardcoded credentials — use environment variables (see `Util.java`)
- Prefer `final` for parameters and locals that don't change
- Use generics properly with JUNG graph types (`Graph<String, Edge>`)
- Handle `null` and edge cases defensively, especially in graph algorithms
- Use `GraphUtils` for shared operations — check there before writing utility code

### GUI Code

- All Swing operations must happen on the Event Dispatch Thread (EDT)
- Use `SwingUtilities.invokeLater()` for cross-thread UI updates
- Keep visualization logic in `Main.java`; extract algorithms to separate classes
- Panel controllers wire analysis panels to the GUI — follow the existing pattern

### Test Code

- Use JUnit 4 (`@Test`, `@Before`, `@After`)
- Test normal cases, edge cases (empty graphs, disconnected components, single nodes), and boundary conditions
- Tests must work without a database connection
- Use descriptive names: `testMethodName_scenario_expectedResult`
- See [TESTING.md](TESTING.md) for coverage gaps and testing conventions

## Where to Contribute

### High-Impact Areas

1. **Test coverage expansion** — See [TESTING.md](TESTING.md) for the full inventory of untested classes. Many analyzers added recently have test classes, but some older modules or newer panel controllers may lack coverage.

2. **Algorithm improvements** — The analyzers use heuristic approximations for NP-hard problems (coloring, independent set, vertex cover, etc.). Better heuristics or tighter bounds are always welcome.

3. **Performance** — Several analyzers operate on adjacency scans that could benefit from precomputed data structures. Profile before optimizing — `GraphBenchmarkSuite` can help.

4. **New export formats** — Formats like Graph6, Sparse6, or Pajek (.net) would be useful additions.

5. **Documentation pages** — The `docs/` folder has 70+ interactive HTML pages. If you add a new analyzer, consider adding a corresponding documentation page.

6. **GUI panels** — Many analyzers run from the toolbar or menu but don't have dedicated panel controllers for interactive use.

### Good First Issues

Look for [issues labeled `good first issue`](https://github.com/sauravbhattacharya001/GraphVisual/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22). If none exist, adding tests for untested classes or improving Javadoc on public APIs are great starting points.

## Making Changes

### Before You Start

1. Check [open issues](https://github.com/sauravbhattacharya001/GraphVisual/issues) for existing discussions
2. For significant changes, open an issue first to discuss the approach
3. Fork the repository and create a feature branch

### Branch Naming

- `feature/<description>` — new features or analyzers
- `fix/<description>` — bug fixes
- `refactor/<description>` — code improvements
- `docs/<description>` — documentation changes
- `test/<description>` — test additions or fixes
- `perf/<description>` — performance improvements

### Testing Your Changes

1. **All existing tests must pass** before pushing
2. **Add tests for new classes** — every new analyzer should have a corresponding `*Test.java`
3. **GUI changes** — test manually by running `Main.java`
4. **Data pipeline changes** — require a PostgreSQL instance with test data
5. **Verify compilation** against JDK 8 compatibility (`-source 8 -target 8` in CI)

## Pull Request Process

1. **Create a focused PR** — one logical change per PR
2. **Write a clear description** — what changed, why, and how to test it
3. **All CI checks must pass** — build (JDK 11 + 17), tests, CodeQL security scan
4. **Keep the PR small** — large PRs are harder to review; split if possible
5. **Update documentation** — if your change affects the README or architecture

### PR Checklist

- [ ] Code compiles without warnings on JDK 11 and 17
- [ ] All existing tests pass
- [ ] New tests added for new functionality
- [ ] No hardcoded credentials or secrets
- [ ] Javadoc added to public methods
- [ ] Documentation updated if applicable (README, docs/, ALGORITHMS.md)
- [ ] Commit messages follow conventions

## Commit Conventions

Use [Conventional Commits](https://www.conventionalcommits.org/) format:

```
<type>: <short description>

<optional body>
```

### Types

| Type | Description |
|------|-------------|
| `feat` | New feature or analyzer |
| `fix` | Bug fix |
| `refactor` | Code restructuring (no behavior change) |
| `test` | Adding or fixing tests |
| `docs` | Documentation only |
| `perf` | Performance improvement |
| `ci` | CI/CD configuration |
| `chore` | Build process or tooling changes |

### Examples

```
feat: add Steiner tree analyzer with terminal-set heuristics
fix: prevent NPE in GraphColoringAnalyzer on disconnected graphs
perf: O(1) adjacency lookups via HashSet in GraphColoringAnalyzer
test: add 25 tests for BipartiteAnalyzer covering König's theorem
docs: add interactive planarity testing documentation page
```

## Architecture Deep Dive

### Visualization Layer

`Main.java` orchestrates the Swing GUI:
1. Reads an edge-list file (generated by the data pipeline or loaded from file)
2. Creates a JUNG `SparseMultigraph<String, Edge>` from the data
3. Applies layout algorithms (static grid, force-directed, hierarchical, circular, spectral)
4. Renders via `VisualizationViewer` with custom vertex/edge transformers
5. Timeline controls iterate over a date range, rebuilding the graph per timestamp
6. Panel controllers wire analysis tools into the sidebar

### Analysis Pipeline

Most analyzers follow this pattern:
1. Accept a `Graph<String, Edge>` (JUNG graph)
2. Compute results using graph traversals, matrix operations, or heuristics
3. Return structured results that can be displayed in panels or exported
4. Optionally generate HTML reports via the `docs/` templates

### Data Pipeline

```
Bluetooth events → findMeetings → matchImei → addLocation → Network → edge-list → Main (GUI)
```

Each pipeline stage reads from and writes to PostgreSQL. The edge-list file bridges the pipeline to the visualizer.

### Key Dependencies

- **JUNG 2.0.1** — Graph data structures and basic algorithms
- **PostgreSQL JDBC** — Database connectivity for the data pipeline
- **Commons IO** — File and stream utilities
- **JUnit 4** — Testing framework

## Issue Guidelines

### Bug Reports

Include:
- Steps to reproduce
- Expected vs actual behavior
- Java version and OS
- Stack trace if applicable
- Sample graph data if relevant (edge-list or GraphML)

### Feature Requests

Include:
- Problem or use case description
- Proposed approach (algorithm, data structure)
- Impact on existing functionality
- References (papers, existing implementations) if applicable

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).

## Code of Conduct

We follow the [Contributor Covenant v2.1](https://www.contributor-covenant.org/version/2/1/code_of_conduct/). Be respectful, give constructive feedback, focus on what's best for the community, and show empathy toward other contributors.
