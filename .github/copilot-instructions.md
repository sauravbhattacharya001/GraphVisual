# Copilot Instructions for GraphVisual

## Project Overview

GraphVisual is a Java Swing desktop application for visualizing community evolution in student/social networks using the [JUNG](http://jung.sourceforge.net/) (Java Universal Network/Graph Framework) library.

## Architecture

### Source Layout
- `Gvisual/src/gvisual/` — GUI & visualization layer
  - `Main.java` — Main application class (~1600 lines). Contains the Swing GUI, JUNG graph rendering, timeline controls, category panel, and notes pane.
  - `edge.java` — Edge data model for graph connections.
- `Gvisual/src/app/` — Data processing & network analysis layer
  - `Network.java` — Core network/graph construction logic. Builds graphs from data, computes community structures.
  - `Util.java` — Utility methods for data parsing, file I/O, and helper functions.
  - `addLocation.java` — Location-based data enrichment.
  - `findMeetings.java` — Meeting/interaction detection between users.
  - `matchImei.java` — IMEI-based device matching.
- `Gvisual/test/` — JUnit tests
  - `app/UtilMethodsTest.java` — Tests for utility methods.
  - `gvisual/EdgeTest.java` — Tests for edge model.
- `Gvisual/lib/` — Third-party JARs (JUNG 2.0.1, PostgreSQL JDBC, commons-io, etc.)
- `Gvisual/images/` — UI icons and button images.

### Build System
- **Ant** (NetBeans-generated): `Gvisual/build.xml` imports `Gvisual/nbproject/build-impl.xml`
- Build: `cd Gvisual && ant compile`
- Test: `cd Gvisual && ant test`
- JAR: `cd Gvisual && ant jar`

### Key Dependencies
- **JUNG 2.0.1** — Graph algorithms, layout, visualization
- **Java Swing** — GUI framework
- **PostgreSQL JDBC** — Database connectivity
- **commons-io 1.4** — File utilities
- **Java3D / vecmath** — 3D visualization support

## Conventions

- Package names are lowercase (`gvisual`, `app`)
- Class names follow Java conventions (PascalCase) except for `edge.java` which is lowercase
- The project was originally built with NetBeans IDE
- Java 8+ compatible (uses generics, collections framework)
- No Maven/Gradle — dependencies are committed as JARs in `lib/`

## How to Test

1. Run `cd Gvisual && ant test` for JUnit tests
2. Tests are in `Gvisual/test/` mirroring the source package structure
3. When adding new tests, place them in the corresponding test package

## Common Patterns

- Graph construction uses JUNG's `UndirectedSparseGraph<String, edge>`
- Visualization uses JUNG's `VisualizationViewer` with custom renderers
- Layout algorithms: `StaticLayout`, `FRLayout`, `KKLayout` from JUNG
- Event handling follows standard Swing `ActionListener` pattern

## Notes for AI Agents

- The `Main.java` file is very large (~1600 lines). Consider refactoring into separate classes for GUI components, graph operations, and data management.
- The `edge` class should be renamed to `Edge` to follow Java naming conventions.
- Database connection details may be hardcoded — check `Network.java` for configuration.
- The project bundles all dependencies as JARs — no dependency manager is used.
