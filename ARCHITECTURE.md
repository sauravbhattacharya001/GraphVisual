# Architecture

This document describes the source structure, design patterns, and key components of GraphVisual.

## Project Layout

```
GraphVisual/
├── Gvisual/
│   ├── src/
│   │   ├── gvisual/           # Visualization + analysis (18 classes)
│   │   │   ├── Main.java      # Swing GUI — graph panel, timeline, controls
│   │   │   ├── edge.java      # Edge model (type, vertices, weight, label)
│   │   │   ├── EdgeType.java  # Enum — relationship categories, colors, defaults
│   │   │   ├── GraphStats.java           # Network metrics (density, degree, hubs)
│   │   │   ├── GraphMLExporter.java      # GraphML XML export
│   │   │   ├── GraphGenerator.java       # Synthetic graph topologies
│   │   │   └── [12 analyzers — see below]
│   │   └── app/               # Data pipeline — DB → edge files
│   │       ├── Network.java       # SQL → edge-list generation
│   │       ├── Util.java          # Database connection factory
│   │       ├── findMeetings.java  # Bluetooth events → meeting extraction
│   │       ├── addLocation.java   # WiFi-based meeting location classification
│   │       └── matchImei.java     # Device → IMEI mapping
│   ├── test/
│   │   ├── gvisual/           # 17 test classes (~650 tests total)
│   │   └── app/               # Pipeline utility tests
│   ├── lib/                   # JUNG 2.0.1, PostgreSQL JDBC, Java3D, Commons IO
│   └── images/                # UI icons (play, pause, stop, legend colors)
├── pom.xml                    # Maven build (Java 11+, shade plugin for fat JAR)
├── build.xml                  # Legacy Ant build (NetBeans)
└── docs/                      # GitHub Pages site
```

## Analyzers

The analysis engine consists of 12 standalone analyzer classes. Each follows the same pattern:

1. Constructor takes a `Graph<String, edge>` (JUNG graph)
2. Validates input (null check → `IllegalArgumentException`)
3. Provides one or more analysis methods returning immutable result objects
4. Includes a `generateSummary()` method for human-readable output

### Analyzer Reference

| Class | Algorithm | Complexity | Purpose |
|-------|-----------|------------|---------|
| `ArticulationPointAnalyzer` | Tarjan's DFS | O(V+E) | Cut vertices and bridges — single points of failure |
| `CliqueAnalyzer` | Bron-Kerbosch (Tomita pivot) | O(3^(n/3)) | All maximal cliques — fully connected subgroups |
| `CommunityDetector` | Connected components | O(V+E) | Community detection with size ranking and metrics |
| `DegreeDistributionAnalyzer` | Statistical analysis | O(V) | Degree histogram, power-law fit, network classification |
| `GraphColoringAnalyzer` | Welsh-Powell (greedy) | O(V² + E) | Vertex coloring — chromatic number estimation |
| `GraphDiameterAnalyzer` | All-pairs BFS | O(V(V+E)) | Diameter, radius, eccentricity, center, periphery |
| `KCoreDecomposition` | Iterative peeling | O(V+E) | K-core shells — dense subgraph hierarchy |
| `LinkPredictionAnalyzer` | Common Neighbors / Jaccard / Adamic-Adar | O(V²·d) | Predict missing edges from structural similarity |
| `MinimumSpanningTree` | Kruskal's + Union-Find | O(E log E) | MST / forest with component analysis |
| `NodeCentralityAnalyzer` | Brandes + BFS | O(VE) | Degree, betweenness, closeness centrality |
| `PageRankAnalyzer` | Power iteration | O(k(V+E)) | PageRank scores with convergence control |
| `ShortestPathFinder` | BFS / Dijkstra | O(V+E) / O((V+E) log V) | Shortest paths (unweighted and weighted) |
| `TopologicalSortAnalyzer` | Kahn's + DFS cycle detection | O(V+E) | Topological ordering, cycle detection, critical path |

### Utility Classes

| Class | Purpose |
|-------|---------|
| `GraphStats` | Aggregate network metrics: density, average/max degree, hub identification, category breakdowns. Uses cached single-pass computation and min-heap for top-N. |
| `GraphGenerator` | Creates synthetic graphs: Complete, Cycle, Star, Path, Grid, Petersen, Bipartite, Binary Tree, Random (Erdős–Rényi), Watts-Strogatz small-world. |
| `GraphMLExporter` | Exports to GraphML (XML) for interop with Gephi, Cytoscape, NetworkX, yEd. Includes edge attributes (type, weight, label). |
| `EdgeType` | Enum centralizing relationship codes (`f`, `c`, `s`, `fs`, `sg`), display labels, colors, and default thresholds. |

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

`Main.java` (2145 lines) is the Swing application entry point. Key components:

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

18 test classes with ~670 tests covering all analyzers and security:

| Test Class | Tests | Covers |
|------------|-------|--------|
| `ArticulationPointAnalyzerTest` | 34 | Cut vertices, bridges, biconnected components |
| `CliqueAnalyzerTest` | 70 | Maximal cliques, clique cover, independence number |
| `CommunityDetectorTest` | 26 | Connected components, community metrics |
| `DegreeDistributionAnalyzerTest` | 55 | Degree stats, histograms, power-law fitting |
| `EdgeTest` | 18 | Edge model getters, setters, equality |
| `GraphColoringAnalyzerTest` | 35 | Welsh-Powell coloring, chromatic bounds |
| `GraphDiameterAnalyzerTest` | 11 | Diameter, radius, eccentricity |
| `GraphGeneratorTest` | 70 | All 10 synthetic topologies |
| `GraphMLExporterTest` | 31 | XML export, attribute preservation |
| `GraphStatsTest` | 55 | Density, degree, hubs, categories, caching |
| `KCoreDecompositionTest` | 44 | K-core shells, degeneracy |
| `LinkPredictionAnalyzerTest` | 10 | Edge prediction metrics |
| `MinimumSpanningTreeTest` | 41 | Kruskal's MST, forest components |
| `NodeCentralityAnalyzerTest` | 46 | Degree, betweenness, closeness centrality |
| `PageRankAnalyzerTest` | 77 | PageRank convergence, damping factor |
| `SecurityTest` | 17 | JDBC host validation, path traversal protection |
| `ShortestPathFinderTest` | 24 | BFS and weighted shortest paths |
| `TopologicalSortAnalyzerTest` | 42 | Topo sort, cycle detection, critical path |

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
