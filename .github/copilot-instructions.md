# Copilot Instructions for GraphVisual

## Project Overview

GraphVisual is a Java graph visualization and analysis platform built with [JUNG](http://jung.sourceforge.net/) (Java Universal Network/Graph Framework). It includes a Swing desktop GUI, 100+ graph algorithm implementations, and a docs site with interactive HTML dashboards.

## Architecture

### Source Layout (272 Java files)
- `Gvisual/src/gvisual/` — GUI, visualization, and algorithm layer (~260 classes)
  - `Main.java` — Swing GUI entry point with JUNG graph rendering, timeline controls, category panel
  - `edge.java` — Edge data model
  - Algorithm analyzers: `ArticulationPointAnalyzer`, `BipartiteAnalyzer`, `CliqueAnalyzer`, `CommunityDetector`, `LouvainCommunityDetector`, `PageRankAnalyzer`, `ShortestPathFinder`, `SpectralAnalyzer`, `NetworkFlowAnalyzer`, etc.
  - Layout engines: `CircularLayout`, `ForceDirectedLayout`, `HierarchicalLayout`
  - Exporters: `DotExporter`, `GexfExporter`, `GraphMLExporter`, `JsonGraphExporter`, `SvgExporter`, `InteractiveHtmlExporter`, `CsvReportExporter`
  - Utilities: `GraphUtils`, `GraphStats`, `GraphGenerator`, `FamousGraphLibrary`, `GraphQueryEngine`
- `Gvisual/src/app/` — Data processing & network construction
  - `Network.java` — Graph construction from data
  - `Util.java` — Parsing, file I/O, helpers
  - `ThresholdConfig.java` — Configurable thresholds
  - `LocationResolver.java`, `addLocation.java`, `findMeetings.java`, `matchImei.java`
- `Gvisual/test/` — 105 JUnit test files
- `Gvisual/lib/` — Third-party JARs (legacy; Maven preferred)
- `docs/` — GitHub Pages site with interactive graph algorithm dashboards

### Build System
- **Maven** (primary): `pom.xml` at project root
  - `mvn compile` — Build
  - `mvn test` — Run 105 JUnit test suites
  - `mvn package` — Produce JAR in `target/`
- **Java 11** (compiler source/target in pom.xml)
- Legacy **Ant** (`Gvisual/build.xml`) — still present but Maven is preferred

### Key Dependencies (from pom.xml)
- **JUNG 2.0.1** — Graph algorithms, layout, visualization
- **Java Swing** — GUI framework
- **PostgreSQL JDBC** — Database connectivity
- **JUnit 4** — Testing framework
- **commons-io** — File utilities
- **vecmath** — 3D vector math support

## Conventions

- Package names: lowercase (`gvisual`, `app`)
- Class names: PascalCase (exception: legacy `edge.java`)
- Algorithm classes follow the pattern: `XxxAnalyzer` with `analyze()` method returning results
- Exporter classes follow: `XxxExporter` with `export()` method
- Test classes: `XxxTest.java` mirroring source structure

## How to Test

```bash
mvn test                           # Run all 105 test suites
mvn test -Dtest=ShortestPathFinderTest  # Run a specific test
mvn test -pl . -Dtest="gvisual.*"      # Run all gvisual package tests
```

When adding new tests, place them in `Gvisual/test/` matching the source package.

## Common Patterns

- Graph construction: `UndirectedSparseGraph<String, edge>` (JUNG)
- Visualization: JUNG's `VisualizationViewer` with custom renderers
- Layout algorithms: `StaticLayout`, `FRLayout`, `KKLayout` from JUNG, plus custom `CircularLayout`, `ForceDirectedLayout`, `HierarchicalLayout`
- Event handling: standard Swing `ActionListener` pattern
- Algorithm results: typically returned as `Map<String, Object>` or dedicated result classes

## Notes for AI Agents

- `Main.java` is large (~1600 lines) — when modifying, focus on the specific section needed
- Many algorithm analyzers are self-contained: one class, one test file, no dependencies beyond JUNG and `GraphUtils`
- The `docs/` directory is a separate GitHub Pages site — changes there don't affect the Java build
- Database configuration may be in `Network.java` — check before modifying DB-related code
- Use `mvn compile -q` to verify compilation after changes
