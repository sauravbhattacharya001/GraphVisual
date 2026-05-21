# Contributing to GraphVisual

Thanks for your interest in contributing! GraphVisual is a large-scale graph theory toolkit with 175 source files, 133 test classes, and 74 documentation pages. This guide will help you navigate the codebase and contribute effectively.

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
│   ├── workflows/          # CI, CodeQL, coverage, Docker, Pages, publish,
│   │                       # auto-label, issue-triage, stale bot
│   ├── copilot-instructions.md
│   ├── copilot-setup-steps.yml
│   ├── dependabot.yml
│   └── ISSUE_TEMPLATE/     # Bug, feature, performance, CI failure, docs, dep forms
├── Gvisual/
│   ├── src/
│   │   ├── gvisual/        # 158 source files — core library
│   │   └── app/            # Data pipeline (DB → edge-list)
│   ├── test/
│   │   ├── gvisual/        # 129 test classes
│   │   └── app/            # Pipeline utility tests
│   ├── lib/                # JUNG 2.0.1, PostgreSQL JDBC, Commons IO
│   └── images/             # UI icons and legend assets
├── Vidly/                  # Separate .NET project (not part of GraphVisual core)
├── docs/                   # 74 HTML documentation pages + CSS
├── pom.xml                 # Maven build descriptor
├── Dockerfile              # Multi-stage Docker build
├── ALGORITHMS.md           # Algorithm documentation
├── ARCHITECTURE.md         # System architecture guide
├── TESTING.md              # Testing conventions and coverage gaps
├── SECURITY.md             # Security policy
└── DATABASE.md             # Database schema documentation
```

## Module Catalog

The `gvisual` package contains 158 classes organized into functional areas. Here's a map to help you find your way:

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
| `CycleAnalyzer` | Cycle detection, enumeration, and girth computation |
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
| `GraphDiameterAnalyzer` | Exact and approximate diameter computation |

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
| `GraphNeighborhoodAnalyzer` | k-hop neighborhood extraction and ego-network metrics |

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
| `GraphInfluenceCampaignPlanner` | Strategic multi-wave influence maximization planning |
| `GraphClusterQualityAnalyzer` | Modularity, silhouette, NMI |
| `DegreeDistributionAnalyzer` | Degree distribution, power-law fitting |
| `GraphHealthChecker` | Graph quality diagnostics |
| `GraphSentinel` | Autonomous graph monitoring — drift detection and alerting |
| `GraphAutoPilot` | Automated analysis pipeline — runs multiple analyzers in sequence |
| `GraphAutonomousRepairEngine` | Self-healing graph repair — reconnects components, removes anomalies |
| `NetworkImmunizationPlanner` | Targeted node immunization to halt epidemic spread |
| `GraphPercolationEngine` | Percolation threshold analysis with 7 sub-engines and health scoring |
| `GraphOpinionDynamicsEngine` | Opinion formation models (voter, DeGroot, bounded confidence) |
| `GraphGameTheoryEngine` | Game-theoretic analysis on graphs (Nash equilibria, cooperative games) |
| `GraphTopologyHypothesisTester` | Statistical hypothesis tests on graph topology |
| `GraphEvolutionSimulator` | Temporal graph evolution — growth, rewiring, and preferential attachment |
| `GraphTemporalDynamicsEngine` | Time-varying network dynamics and temporal motifs |

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
| `GraphKnowledgeExtractor` | Extract structured knowledge graphs from raw graphs |

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
| `GraphMatrixExporter` | Adjacency/incidence/Laplacian matrix export (CSV, JSON) |
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
| `GraphFileParser` | Multi-format graph file loader (edge-list, GraphML, GEXF, DOT) |
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
# Production sources only — exclude any *Test.java that may have been
# checked in under src/ by mistake (see "Common Pitfalls" below).
find src -name '*.java' ! -name '*Test.java' > sources.txt
javac -encoding UTF-8 --release 17 \
  -cp "$(find lib -name '*.jar' -not -path '*/test/*' | tr '\n' ':')" \
  -d build/classes @sources.txt
```

Notes:

- **Use `-encoding UTF-8`**. Several source files contain box-drawing characters
  (╔ ╚ ║ etc.) used in ASCII diagrams; without the flag, `javac` falls back
  to the platform default and fails on Windows (`cp1252`).
- **Target JDK 17** (`--release 17`). The codebase uses `var` (Java 10+) and
  `Stream.toList()` (Java 16+) in places, so it will not compile cleanly
  under `--release 8` or `11` even though older docs suggested it.
- Keep `lib/test/*.jar` *off* the production classpath. JUnit symbols must
  not be resolvable from production code.

### Run Tests

```bash
mkdir -p build/test/classes
# Only canonical test files (test/**/*Test.java). See "Common Pitfalls".
find test -name '*Test.java' > test_sources.txt
javac -encoding UTF-8 --release 17 \
  -cp "build/classes:$(find lib -name '*.jar' | tr '\n' ':')" \
  -d build/test/classes @test_sources.txt

# Run specific test classes
java -cp "build/classes:build/test/classes:$(find lib -name '*.jar' | tr '\n' ':')" \
  org.junit.runner.JUnitCore gvisual.EdgeTest gvisual.GraphStatsTest

# Run all tests (use a test runner or list all test classes)
```

### Common Pitfalls

These are real footguns that have broken CI and Docker builds in the past —
please keep them in mind when contributing:

1. **Never check in `*Test.java` files under `Gvisual/src/`.** The canonical
   test root is `Gvisual/test/`. JUnit is not on the production classpath,
   so a stray test under `src/` causes `package org.junit does not exist`
   in both the Docker build and the CI "Compile source" step. If you wrote
   a test alongside a feature in your editor, move it to the matching
   package under `Gvisual/test/` before committing.
2. **Check both `src/test/` and `test/` for duplicates** when moving files.
   We had a `GraphCompressorTest` checked in under both for a while; the
   one under `src/test/` was a stale older copy. When in doubt, keep the
   version under `Gvisual/test/gvisual/` and delete the other.
3. **`Edge` has no two-arg constructor.** Use
   `new Edge("e", "a", "b")` (type code, vertex1, vertex2). The `"e"`
   type code is fine for tests that don't care about the relationship
   category.
4. **`Edge#setWeight(float)` takes a `float`, not a `double`.** Use
   `setWeight(0.9f)`, not `setWeight(0.9)` — the latter triggers "possible
   lossy conversion from double to float".
5. **`EdgeType` is a closed enum.** The valid constants are `FRIEND`,
   `CLASSMATE`, `FAMILIAR`, `STRANGER`, `STUDY_GROUP`. There is no
   `EdgeType.blue` — use the type *code* string (`"c"` for CLASSMATE)
   when constructing `Edge` instances, or `EdgeType.CLASSMATE.getColor()`
   when you need the colour.
6. **`HEALTHCHECK` cannot call the GUI.** The default `CMD` launches the
   Swing UI which needs `$DISPLAY`. The container healthcheck must verify
   something headless (we use `jar tf Gvisual.jar`).

If you hit a compile error that isn't covered here, please add it to this
list as part of your fix — future contributors will thank you.

The test suite has **133 test classes** (~4,640+ `@Test` methods) covering most analyzers and exporters. CI runs a subset against JDK 11 and 17 — see `.github/workflows/ci.yml` for the canonical list.

### Docker

```bash
# Build the image (multi-stage; compiles source, runs a fast test subset,
# then produces a fat JAR in the runtime image).
docker build -t graphvisual .

# Run the GUI with X11 forwarding from a Linux host.
docker run --rm \
  -e DISPLAY=$DISPLAY \
  -v /tmp/.X11-unix:/tmp/.X11-unix \
  graphvisual

# Sanity-check that the JAR is loadable (works without $DISPLAY).
docker run --rm --entrypoint sh graphvisual -c 'jar tf Gvisual.jar | head'
```

The Docker build performs:

1. JDK 17 source compilation (production only, no `*Test.java` from `src/`).
2. Test compilation against the canonical `test/` tree.
3. A **fast deterministic test subset** (5 stable test classes) as a smoke
   barrier — the full suite runs in CI, not in `docker build`, so image
   builds stay fast and reproducible.
4. Fat-JAR assembly with signed-JAR signatures stripped.
5. A minimal runtime image based on `eclipse-temurin:17-jre` running as a
   non-root user (`uid=1001`).

The published image is also scanned by Trivy and (on tagged releases)
signed with a SLSA build-provenance attestation via
`actions/attest-build-provenance`. See `.github/workflows/docker.yml`.

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

## Reproducing CI Locally

Before pushing, replicate exactly what CI does:

### Full Build + Test (mirrors `ci.yml`)

```bash
cd Gvisual

# JDK 11 build (CI primary)
mkdir -p build/classes build/test/classes
find src -name '*.java' > sources.txt
javac -source 8 -target 8 \
  -cp "$(find lib -name '*.jar' | tr '\n' ':')" \
  -d build/classes @sources.txt

# Compile tests
find test -name '*.java' > test_sources.txt
javac -cp "build/classes:$(find lib -name '*.jar' | tr '\n' ':')" \
  -d build/test/classes @test_sources.txt

# Run all tests
java -cp "build/classes:build/test/classes:$(find lib -name '*.jar' | tr '\n' ':')" \
  org.junit.runner.JUnitCore $(find build/test/classes -name '*Test.class' \
  | sed 's|build/test/classes/||;s|/|.|g;s|\.class||')
```

### Using Maven (simpler)

```bash
mvn clean compile          # Build only
mvn clean test             # Build + run tests
mvn clean verify           # Full verification
```

### Docker Build

Reproduces the full CI environment:

```bash
docker build -t graphvisual:local .
# Runs tests as part of the multi-stage build
```

### Cross-JDK Testing

CI tests on both JDK 11 and JDK 17. If you have both installed:

```bash
JAVA_HOME=/path/to/jdk11 mvn test
JAVA_HOME=/path/to/jdk17 mvn test
```

### CodeQL Locally

Install the [CodeQL CLI](https://github.com/github/codeql-action) and run:

```bash
codeql database create graphvisual-db --language=java --source-root=.
codeql database analyze graphvisual-db codeql/java-queries:codeql-suites/java-security-extended.qls --format=sarif-latest --output=results.sarif
```

## Performance Benchmarking

GraphVisual includes `GraphBenchmarkSuite` — a collection of classic network science benchmark graphs for testing algorithm performance.

### Running Benchmarks

```java
import gvisual.GraphBenchmarkSuite;

// Generate all benchmark graphs
List<GraphBenchmarkSuite.BenchmarkGraph> benchmarks = GraphBenchmarkSuite.getAllBenchmarks();

// Time your analyzer against increasing graph sizes
for (BenchmarkGraph bg : benchmarks) {
    long start = System.nanoTime();
    YourAnalyzer analyzer = new YourAnalyzer(bg.getGraph());
    analyzer.analyze();
    long elapsed = System.nanoTime() - start;
    System.out.printf("%s (%d nodes): %.2f ms%n",
        bg.getName(), bg.getGraph().getVertexCount(), elapsed / 1e6);
}
```

### Performance Guidelines

| Graph Size | Acceptable Response Time |
|------------|--------------------------|
| < 100 nodes | < 10 ms |
| 100–1,000 nodes | < 100 ms |
| 1,000–10,000 nodes | < 1 second |
| > 10,000 nodes | Document complexity |

### Profiling Tips

1. **Use `GraphBenchmarkSuite`** — don't invent random graph generators for every test
2. **Measure wall-clock time** for user-facing operations
3. **Profile before optimizing** — use VisualVM or async-profiler
4. **Document complexity** — add `@implNote` Javadoc with big-O for non-trivial algorithms
5. **Avoid premature allocation** — reuse collections across iterations when safe
6. **Precompute** adjacency sets or degree caches if your analyzer does repeated neighbor lookups

### Regression Testing

When submitting a performance PR:
1. Show before/after timings on at least 2 benchmark graphs
2. Include the graph size and your hardware specs
3. Verify correctness hasn't regressed (same outputs on same inputs)

## Dependency Management

GraphVisual uses a hybrid approach:

- **Maven (`pom.xml`)** — for CI, CodeQL, and IDE integration
- **Local JARs (`Gvisual/lib/`)** — for compilation without Maven (legacy support)

### Adding a New Dependency

1. Add to `pom.xml` with appropriate `<scope>` (compile, test, provided)
2. Place the JAR in `Gvisual/lib/` for non-Maven builds
3. Update `.github/copilot-setup-steps.yml` if the dep affects build steps
4. Document why it's needed in the PR description

### Current Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| JUNG | 2.0.1 | Graph data structures & basic algorithms |
| PostgreSQL JDBC | 42.7.x | Data pipeline database connectivity |
| Commons IO | 2.x | File/stream utilities |
| JUnit 4 | 4.13.x | Test framework |

### Updating Dependencies

Dependabot manages automated updates. To manually update:

```bash
mvn versions:display-dependency-updates
mvn versions:use-latest-releases -DallowMajorUpdates=false
```

Always run the full test suite after a dependency update.

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).

## Code of Conduct

We follow the [Contributor Covenant v2.1](https://www.contributor-covenant.org/version/2/1/code_of_conduct/). Be respectful, give constructive feedback, focus on what's best for the community, and show empathy toward other contributors.
