<div align="center">

# 📊 GraphVisual

**Community evolution visualization for student social networks**

Built with Java and [JUNG](http://jung.sourceforge.net/) (Java Universal Network/Graph Framework)

[![CI](https://github.com/sauravbhattacharya001/GraphVisual/actions/workflows/ci.yml/badge.svg)](https://github.com/sauravbhattacharya001/GraphVisual/actions/workflows/ci.yml)
[![CodeQL](https://github.com/sauravbhattacharya001/GraphVisual/actions/workflows/codeql.yml/badge.svg)](https://github.com/sauravbhattacharya001/GraphVisual/actions/workflows/codeql.yml)
[![Java](https://img.shields.io/badge/Java-8%2B-orange?logo=openjdk)](https://openjdk.org/)
[![JUNG](https://img.shields.io/badge/JUNG-2.0.1-blue)](http://jung.sourceforge.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://github.com/sauravbhattacharya001/GraphVisual/blob/master/LICENSE)
[![Docker](https://github.com/sauravbhattacharya001/GraphVisual/actions/workflows/docker.yml/badge.svg)](https://github.com/sauravbhattacharya001/GraphVisual/actions/workflows/docker.yml)
[![Coverage](https://github.com/sauravbhattacharya001/GraphVisual/actions/workflows/coverage.yml/badge.svg)](https://github.com/sauravbhattacharya001/GraphVisual/actions/workflows/coverage.yml)
[![GitHub repo size](https://img.shields.io/github/repo-size/sauravbhattacharya001/GraphVisual)](https://github.com/sauravbhattacharya001/GraphVisual)

</div>

---

## Overview

GraphVisual is a desktop application for studying **community evolution** in student populations using Bluetooth proximity data. It processes meeting records from a PostgreSQL database, classifies social relationships (friends, classmates, strangers, study groups, familiar strangers), and renders interactive graph visualizations with timeline playback.

The tool was developed for research on **social network analysis** — specifically understanding how communities form, dissolve, and evolve over time in university settings.

## Features

- **Interactive graph visualization** — Drag, zoom, rotate nodes with JUNG's built-in graph mouse
- **Timeline playback** — Animate community graphs across 92 days (March–May 2011) with play/pause/stop controls
- **5 relationship categories** — Friends (green), Classmates (blue), Familiar Strangers (gray), Strangers (red), Study Groups (orange)
- **Adjustable thresholds** — Tune meeting duration and frequency thresholds per relationship type in real-time
- **Cluster-based layout** — Nodes auto-grouped into a 3×3 grid by relationship type, with randomized positioning
- **Edge weighting** — Line thickness reflects interaction frequency × duration
- **New member highlighting** — Nodes appearing for the first time are drawn larger
- **Notes panel** — Annotate each timestamp during analysis
- **Graph export** — Save visualizations as PNG images and edge lists
- **Network statistics panel** — Real-time metrics including node/edge counts, per-category breakdowns, graph density, average/max degree, average edge weight, isolated node count, and top-3 hub nodes
- **Centrality analysis** — Compute degree, betweenness (Brandes' algorithm), and closeness centrality for all nodes. Interactive panel with metric sorting, top-10 ranking with medals, network topology classification, and per-metric averages/maximums
- **Small-world analysis** — Test whether a graph exhibits Watts-Strogatz small-world properties. Computes local/global clustering coefficients, average path length, sigma (σ) and omega (ω) coefficients, random/lattice baselines, and classifies networks as Small-World, Random-Like, Lattice-Like, or Disconnected
- **Subgraph extraction** — Extract focused subgraphs using a fluent builder API. Filter by edge type, weight range, degree range, k-hop neighborhood, node whitelist, or time window. Export results as CSV edge lists with retention statistics and edge type breakdowns

## Architecture

GraphVisual consists of 67 source classes (~30,000+ lines), 43 graph analyzers, and a Bluetooth-to-graph data pipeline. See **[ARCHITECTURE.md](ARCHITECTURE.md)** and **[ALGORITHMS.md](ALGORITHMS.md)** for full details including the analyzer reference table, design patterns, and dependency map.

```
Gvisual/src/
├── gvisual/           # 62 classes — GUI, edge model, 43 analyzers, utilities
│   ├── Main.java                       # Swing GUI — graph panel, timeline, controls
│   ├── edge.java                       # Edge model (type, vertices, weight, label)
│   ├── EdgeType.java                   # Enum — relationship categories, colors, defaults
│   ├── GraphStats.java                 # Network metrics (density, degree, hubs)
│   ├── GraphMLExporter.java            # GraphML XML export
│   ├── GraphGenerator.java             # 10 synthetic graph topologies
│   ├── GraphUtils.java                 # BFS, connected components, utility methods
│   ├── GraphPartitioner.java           # Spectral/Kernighan-Lin partitioning
│   ├── ForceDirectedLayout.java        # Force-directed graph layout (Barnes-Hut)
│   ├── AnalysisTask.java               # Async analysis with timeout/cancellation
│   ├── AnalysisResult.java             # Analysis result container
│   │
│   │── # ─── Structural Analyzers ──────────────────
│   ├── ArticulationPointAnalyzer.java  # Cut vertices/bridges (Tarjan's)
│   ├── BipartiteAnalyzer.java          # Bipartiteness testing + 2-coloring
│   ├── ChordalGraphAnalyzer.java       # Chordal graph recognition (PEO)
│   ├── CliqueAnalyzer.java             # Maximal cliques (Bron-Kerbosch)
│   ├── CycleAnalyzer.java             # Cycle detection and enumeration
│   ├── EulerianPathAnalyzer.java       # Euler path/circuit (Hierholzer's)
│   ├── GraphIsomorphismAnalyzer.java   # Graph isomorphism testing
│   ├── LineGraphAnalyzer.java          # Line graph construction + analysis
│   ├── PlanarGraphAnalyzer.java        # Planarity testing
│   ├── TreeAnalyzer.java               # Tree properties, LCA, diameter
│   ├── TopologicalSortAnalyzer.java    # Topo sort + cycle detection
│   ├── StronglyConnectedComponentsAnalyzer.java  # SCC (Tarjan/Kosaraju)
│   │
│   │── # ─── Centrality & Ranking ──────────────────
│   ├── NodeCentralityAnalyzer.java     # Degree/betweenness/closeness
│   ├── PageRankAnalyzer.java           # PageRank (power iteration)
│   ├── DegreeDistributionAnalyzer.java # Degree stats + power-law fitting
│   │
│   │── # ─── Community & Clustering ────────────────
│   ├── CommunityDetector.java          # Connected component communities
│   ├── MotifAnalyzer.java              # Network motif detection
│   ├── SignedGraphAnalyzer.java        # Signed graph balance theory
│   ├── StructuralHoleAnalyzer.java     # Burt's structural holes
│   │
│   │── # ─── Optimization & NP-hard ────────────────
│   ├── DominatingSetAnalyzer.java      # Minimum dominating set
│   ├── FeedbackVertexSetAnalyzer.java  # Feedback vertex set
│   ├── GraphColoringAnalyzer.java      # Welsh-Powell vertex coloring
│   ├── HamiltonianAnalyzer.java        # Hamiltonian path/cycle
│   ├── IndependentSetAnalyzer.java     # Maximum independent set
│   ├── MaxCutAnalyzer.java             # Maximum cut problem
│   ├── MetricDimensionAnalyzer.java    # Metric dimension (resolving sets)
│   ├── SteinerTreeAnalyzer.java        # Steiner tree approximation
│   ├── TreewidthAnalyzer.java          # Treewidth estimation
│   ├── VertexConnectivityAnalyzer.java # Vertex connectivity
│   ├── VertexCoverAnalyzer.java        # Minimum vertex cover
│   │
│   │── # ─── Network Analysis ──────────────────────
│   ├── LinkPredictionAnalyzer.java     # Edge prediction metrics
│   ├── NetworkFlowAnalyzer.java        # Max-flow/min-cut (Ford-Fulkerson)
│   ├── GraphResilienceAnalyzer.java    # Attack/failure resilience
│   ├── GraphSparsificationAnalyzer.java # Graph sparsification algorithms
│   ├── InfluenceSpreadSimulator.java   # IC/LT influence models
│   ├── RandomWalkAnalyzer.java         # Random walks, hitting/cover times
│   ├── SmallWorldAnalyzer.java         # Small-world property testing (σ, ω)
│   ├── TemporalGraph.java              # Temporal graph evolution analysis
│   │
│   │── # ─── Metrics & Comparison ──────────────────
│   ├── AdjacencyMatrixHeatmap.java     # Adjacency matrix visualization
│   ├── GraphEntropyAnalyzer.java       # 9 entropy measures
│   ├── GraphSimilarityAnalyzer.java    # Entropy-based graph comparison
│   ├── GraphDiffAnalyzer.java          # Structural diff between graphs
│   ├── EdgePersistenceAnalyzer.java    # Edge stability over time
│   ├── GrowthRateAnalyzer.java         # Network growth modeling
│   ├── LaplacianBuilder.java           # Laplacian matrix construction
│   ├── SpectralAnalyzer.java           # Eigenvalue spectral analysis
│   │
│   │── # ─── Algorithms ────────────────────────────
│   ├── KCoreDecomposition.java         # K-core peeling
│   ├── MinimumSpanningTree.java        # Kruskal's MST
│   ├── ShortestPathFinder.java         # BFS + weighted Dijkstra
│   ├── GraphRenderers.java             # Custom graph rendering
│   └── GraphDiameterAnalyzer.java      # Diameter, radius, eccentricity
└── app/               # Data pipeline — Bluetooth → meetings → edge files
    ├── Network.java, Util.java, findMeetings.java, addLocation.java, matchImei.java
```

60 test classes with **2,500+ tests** cover all analyzers and utilities.

## Requirements

- **Java JDK 8** or later
- **PostgreSQL** database with the expected schema (`meeting`, `event_3`, `device_1`, `deviceID` tables)
- **Apache Ant** (NetBeans project build system)

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/sauravbhattacharya001/GraphVisual.git
cd GraphVisual
```

### 2. Configure database credentials

GraphVisual reads credentials from environment variables (no hardcoded secrets):

```bash
export DB_HOST=localhost    # PostgreSQL host (default: localhost)
export DB_USER=your_user    # Required
export DB_PASS=your_pass    # Required
```

### 3. Build

```bash
cd Gvisual
ant build
```

Or compile manually:

```bash
cd Gvisual
mkdir -p build/classes
find src -name '*.java' > sources.txt
javac -cp "$(find lib -name '*.jar' | tr '\n' ':')" -d build/classes @sources.txt
```

### 4. Run the data pipeline

Execute these in order to populate the meeting database:

```bash
# Step 1: Match device nodes to IMEIs
java -cp "build/classes:lib/*" app.matchImei

# Step 2: Extract meetings from Bluetooth events
java -cp "build/classes:lib/*" app.findMeetings

# Step 3: Classify meeting locations
java -cp "build/classes:lib/*" app.addLocation
```

### 5. Launch the visualizer

```bash
java -cp "build/classes:lib/*" gvisual.Main
```

## GUI Components

| Component | Description |
|-----------|-------------|
| **Image Panel** | Main graph canvas powered by JUNG. Supports drag, zoom, and rotation. |
| **Timeline Panel** | Slider (days 1–92) with play/pause/stop and skip controls. Speed adjustable. |
| **Toolbar** | Left-side tools for interaction mode (transform vs. pick), image/edge-list export. |
| **Category Panel** | Toggle visibility of each relationship type. Expand to adjust duration/frequency thresholds. |
| **Notes Pane** | Free-text area for annotating the currently viewed graph timestamp. |
| **Statistics Panel** | Live network metrics — node/edge counts, density, degree stats, and hub identification. |
| **Centrality Panel** | Compute and rank nodes by degree, betweenness, and closeness centrality with sortable metric selector. |

## Relationship Classification

| Type | Color | Location | Duration Threshold | Meeting Count |
|------|-------|----------|--------------------|---------------|
| **Friends** | 🟢 Green | Public areas | > 10 min | ≥ 2/day |
| **Classmates** | 🔵 Blue | Classrooms | > 30 min | ≥ 1/day |
| **Study Groups** | 🟠 Orange | Classrooms | > 20 min | ≤ 1/day |
| **Familiar Strangers** | ⚪ Gray | Public/paths | < 2 min | > 1/day |
| **Strangers** | 🔴 Red | Public/paths | < 2 min | < 2/day |

All thresholds are adjustable at runtime via the Category Panel sliders.

## Tech Stack

| Technology | Purpose |
|------------|---------|
| **Java 8+** | Application language |
| **JUNG 2.0.1** | Graph data structures and visualization |
| **Swing** | Desktop GUI framework |
| **PostgreSQL** | Meeting and Bluetooth event storage |
| **Apache Ant** | Build system (NetBeans) |
| **Commons IO** | File I/O utilities |
| **Java3D** | 3D graph rendering support |
| **JUnit 4** | Unit testing framework |
| **GitHub Actions** | CI/CD (build + test on JDK 11/17) |
| **CodeQL** | Automated security scanning |

## Testing

Run tests with JUnit 4:

```bash
cd Gvisual
mkdir -p build/test/classes

# Download JUnit (if not present)
curl -sL -o lib/test/junit-4.13.2.jar \
  https://repo1.maven.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar
curl -sL -o lib/test/hamcrest-core-1.3.jar \
  https://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar

# Compile and run
find test -name '*.java' > test-sources.txt
javac -cp "build/classes:$(find lib -name '*.jar' | tr '\n' ':')" \
  -d build/test/classes @test-sources.txt

java -cp "build/classes:build/test/classes:$(find lib -name '*.jar' | tr '\n' ':')" \
  org.junit.runner.JUnitCore app.UtilMethodsTest gvisual.EdgeTest
```

## Maven / GitHub Packages

GraphVisual is published to [GitHub Packages](https://github.com/sauravbhattacharya001/GraphVisual/packages) as a Maven artifact. You can use it as a library dependency or download the fat JAR directly.

### Add as a Maven dependency

1. Configure GitHub Packages in your `~/.m2/settings.xml`:

```xml
<servers>
  <server>
    <id>github</id>
    <username>YOUR_GITHUB_USERNAME</username>
    <password>YOUR_GITHUB_TOKEN</password>
  </server>
</servers>
```

2. Add the repository and dependency to your `pom.xml`:

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/sauravbhattacharya001/GraphVisual</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.sauravbhattacharya001</groupId>
  <artifactId>graphvisual</artifactId>
  <version>1.1.0</version>
</dependency>
```

### Download the fat JAR

Each [release](https://github.com/sauravbhattacharya001/GraphVisual/releases) includes a standalone `graphvisual-*-all.jar` with all dependencies bundled:

```bash
java -jar graphvisual-1.1.0-all.jar
```

### Build with Maven locally

```bash
# Install vendored local JARs first
mvn initialize -P install-local-deps

# Build the project
mvn package -B
```

## Docker

### Build

```bash
docker build -t graphvisual .
```

### Run with X11 (Linux/macOS — GUI mode)

```bash
# Allow X11 forwarding
xhost +local:docker

docker run --rm \
  -e DISPLAY=$DISPLAY \
  -v /tmp/.X11-unix:/tmp/.X11-unix \
  graphvisual
```

### Pull from GitHub Container Registry

```bash
docker pull ghcr.io/sauravbhattacharya001/graphvisual:latest
```

> **Note:** The Dockerfile builds a fat JAR with all dependencies, compiles source, runs tests during build, and packages a minimal JRE-based runtime image (~300MB). X11 libraries are included for optional GUI support via display forwarding.

## Research Context

This project was built to study **community evolution in student social networks** using Bluetooth proximity sensing. Key research questions:

- How do social communities form and evolve over a semester?
- What distinguishes friends from familiar strangers based on meeting patterns?
- How do physical spaces (classrooms vs. public areas) shape community structure?

The visualization tool enables researchers to explore these questions interactively by adjusting relationship parameters and observing how graph structures change over time.

## Contributing

Contributions are welcome! See **[CONTRIBUTING.md](CONTRIBUTING.md)** for full details on:

- Development setup and building
- Code style and architecture overview
- Testing guidelines
- Pull request process and commit conventions

Quick start: fork → branch → make changes with tests → submit PR.
