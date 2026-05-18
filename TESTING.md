# Testing Guide

This document covers GraphVisual's test suite: how to run tests, conventions to follow when writing new tests, and the architecture of the testing infrastructure.

## Quick Start

```bash
# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=BipartiteAnalyzerTest

# Run a specific test method
mvn test -Dtest=BipartiteAnalyzerTest#testMaximumMatchingCompleteBipartite

# Run tests matching a pattern
mvn test -Dtest="Graph*Test"

# Run with verbose output
mvn test -Dsurefire.useFile=false
```

## Suite Overview

| Metric | Value |
|--------|-------|
| Test classes | 129 (+ 4 in `app/`) |
| Total tests | ~4,640+ (`@Test` methods) |
| Framework | JUnit 4 |
| Source directory | `Gvisual/test/` |
| Coverage areas | Analyzers, exporters, layouts, utilities, GUI controllers |

The tests are configured via Maven Surefire in `pom.xml`:

```xml
<testSourceDirectory>Gvisual/test</testSourceDirectory>
```

## Directory Structure

```
Gvisual/test/
├── gvisual/                   # 129 test classes for core library
│   ├── ArticulationPointAnalyzerTest.java
│   ├── BipartiteAnalyzerTest.java
│   ├── ...
│   └── TopologicalSortAnalyzerTest.java
└── app/                       # 4 test classes for data pipeline
    ├── ThresholdConfigTest.java
    ├── UtilMethodsTest.java
    └── UtilTest.java
```

## Test Conventions

### Graph Construction Pattern

All analyzer tests follow a consistent pattern for building test graphs:

```java
private Graph<String, Edge> graph;

@Before
public void setUp() {
    graph = new UndirectedSparseGraph<String, Edge>();  // or DirectedSparseGraph
}

private Edge addEdge(String v1, String v2, String type) {
    Edge e = new Edge(type, v1, v2);
    e.setLabel(v1 + "-" + v2);
    graph.addEdge(e, v1, v2);
    return e;
}
```

Use `addEdge()` helpers to keep tests readable. The `type` parameter maps to `EdgeType` values (e.g., `"f"` for friends, `"c"` for classmates).

### Test Categories

Tests fall into these categories:

1. **Happy path** — Standard inputs with known correct results
2. **Edge cases** — Empty graphs, single vertices, disconnected components
3. **Null/invalid input** — Verify `IllegalArgumentException` on null graphs
4. **Algorithmic correctness** — Mathematical properties that must hold (e.g., chromatic number ≤ max degree + 1, Euler paths require 0 or 2 odd-degree vertices)
5. **Large inputs** — Performance regression tests on graphs with hundreds of vertices

### Naming Convention

Test methods use descriptive names:

```java
@Test
public void testMaximumMatchingCompleteBipartite() { ... }

@Test(expected = IllegalArgumentException.class)
public void testNullGraphThrows() { ... }

@Test
public void testEmptyGraphReturnsEmptyResult() { ... }
```

### Null Checks

Every analyzer must reject null graphs. Include this test:

```java
@Test(expected = IllegalArgumentException.class)
public void testNullGraphThrows() {
    new MyAnalyzer(null);
}
```

### Summary Output

Analyzers provide `generateSummary()` for human-readable output. Test it:

```java
@Test
public void testSummaryNotEmpty() {
    // ... build graph and run analysis ...
    String summary = analyzer.generateSummary();
    assertNotNull(summary);
    assertFalse(summary.isEmpty());
}
```

## Classes Without Tests

The following 28 source classes in `gvisual/` do not yet have dedicated test classes in `Gvisual/test/gvisual/`. Contributions welcome:

**Exporters:** `CentralityRadarExporter`, `GraphDiffHtmlExporter`, `GraphMatrixExporter`, `GraphStorytellerExporter`, `GraphTimelineExporter`, `TikzExporter`

**GUI Controllers:** `ArticulationPanelController`, `CentralityPanelController`, `CommunityPanelController`, `EgoPanelController`, `ExportActions`, `MSTPanelController`, `PathPanelController`, `ResiliencePanelController`, `StatsPanel`, `ToolbarBuilder`, `Main`, `RandomGraphDialog`

**Analyzers:** `GraphCompressor`, `GraphDrawingQualityAnalyzer`, `GraphLayoutComparer`, `GraphRegularityAnalyzer`, `GraphSpectrumAnalyzer`, `GraphStatsDashboard`, `GraphVoronoiPartitioner`

**Utilities/Models:** `AdjacencyMatrixHeatmap`, `GraphRenderers`, `SpectralLayout`

> Some of these classes also have skeleton tests under `Gvisual/src/test/gvisual/` that are not yet wired into the Surefire test source tree (`Gvisual/test/`). Promoting those into `Gvisual/test/gvisual/` (and fixing any drift against the current production APIs) is a good first contribution.

## Writing New Tests

1. Create `Gvisual/test/gvisual/YourClassTest.java` in the `gvisual` package
2. Use `@Before` to initialize a fresh graph — do not share state between tests
3. Cover: null input, empty graph, single vertex, small known graph, algorithmic invariants
4. Keep tests deterministic — avoid `Math.random()` without a fixed seed
5. Use `assertEquals(expected, actual, delta)` for floating-point comparisons (use `1e-9` tolerance)
6. Run `mvn test -Dtest=YourClassTest` to verify before committing

## CI Integration

Tests run automatically via GitHub Actions on every push and PR. See `.github/workflows/ci.yml` for the workflow configuration. The CI pipeline:

1. Checks out the code
2. Sets up JDK 11
3. Runs `mvn test` with Surefire
4. Reports failures in the PR checks
