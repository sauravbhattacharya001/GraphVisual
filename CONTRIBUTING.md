# Contributing to GraphVisual

Thanks for your interest in contributing to GraphVisual! This document covers everything you need to get started.

## Table of Contents

- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [Building & Testing](#building--testing)
- [Code Style](#code-style)
- [Architecture Overview](#architecture-overview)
- [Making Changes](#making-changes)
- [Pull Request Process](#pull-request-process)
- [Commit Conventions](#commit-conventions)
- [Issue Guidelines](#issue-guidelines)

## Development Setup

### Prerequisites

- **Java JDK 8** or later (JDK 11 or 17 recommended for CI parity)
- **Git**
- **PostgreSQL** (optional — only needed for data pipeline; visualization and tests work without it)

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
java -cp "build/classes:build/test/classes:$(find lib -name '*.jar' | tr '\n' ':')" org.junit.runner.JUnitCore \
  gvisual.EdgeTest gvisual.GraphStatsTest app.UtilMethodsTest \
  gvisual.ShortestPathFinderTest gvisual.CommunityDetectorTest
```

All tests should pass without a database connection.

### Database Setup (optional)

If you're working on the data pipeline (`app/` package), configure PostgreSQL:

```bash
export DB_HOST=localhost
export DB_USER=your_user
export DB_PASS=your_pass
```

The schema expects tables: `meeting`, `event_3`, `device_1`, `deviceID`.

## Project Structure

```
Gvisual/
├── src/
│   ├── gvisual/                  # Core visualization
│   │   ├── Main.java             # Swing GUI — graph panel, timeline, controls
│   │   ├── edge.java             # Edge model (type, vertices, weight)
│   │   ├── GraphStats.java       # Network metrics (density, degree, hubs)
│   │   ├── ShortestPathFinder.java  # BFS & Dijkstra pathfinding
│   │   └── CommunityDetector.java   # Connected component analysis
│   └── app/                      # Data pipeline
│       ├── Network.java          # Edge-list generation from DB
│       ├── Util.java             # Database connection factory
│       ├── findMeetings.java     # Bluetooth → meeting extraction
│       ├── addLocation.java      # Meeting location classification
│       └── matchImei.java        # Device ↔ IMEI matching
├── test/
│   ├── gvisual/                  # Visualization tests
│   │   ├── EdgeTest.java
│   │   ├── GraphStatsTest.java
│   │   ├── ShortestPathFinderTest.java
│   │   └── CommunityDetectorTest.java
│   └── app/
│       └── UtilMethodsTest.java  # Utility method tests
├── lib/                          # JUNG 2.0.1, PostgreSQL JDBC, Commons IO
└── images/                       # UI icons and legend assets
```

### Key Packages

| Package | Responsibility |
|---------|---------------|
| `gvisual` | Graph visualization, metrics, pathfinding, community detection |
| `app` | Data ingestion pipeline (requires PostgreSQL) |

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
java -cp "build/classes:build/test/classes:$(find lib -name '*.jar' | tr '\n' ':')" org.junit.runner.JUnitCore \
  gvisual.EdgeTest gvisual.GraphStatsTest app.UtilMethodsTest \
  gvisual.ShortestPathFinderTest gvisual.CommunityDetectorTest
```

### Docker

```bash
docker build -t graphvisual .
docker run graphvisual
```

The Docker build compiles all source, runs the full test suite, and creates a fat JAR.

## Code Style

### Naming Conventions

- **Classes:** `PascalCase` (e.g., `GraphStats`, `ShortestPathFinder`)
- **Methods/variables:** `camelCase` (e.g., `calculateDensity`, `edgeWeight`)
- **Constants:** `UPPER_SNAKE_CASE`
- **Packages:** lowercase (e.g., `gvisual`, `app`)
- **Test classes:** `<ClassName>Test` (e.g., `EdgeTest`, `GraphStatsTest`)

> **Note:** Some existing classes use lowercase names (e.g., `edge.java`, `findMeetings.java`). Follow `PascalCase` for new classes but don't rename existing ones without good reason.

### General Guidelines

- Keep methods focused — one responsibility per method
- Add Javadoc to public methods and classes
- Avoid hardcoded credentials — use environment variables (see `Util.java` pattern)
- Prefer `final` for method parameters and local variables that don't change
- Use generics properly with JUNG graph types (`Graph<String, edge>`)
- Handle `null` and edge cases defensively, especially in graph algorithms

### GUI Code

- All Swing operations should happen on the Event Dispatch Thread (EDT)
- Use `SwingUtilities.invokeLater()` for cross-thread UI updates
- Keep visualization logic in `Main.java`; extract algorithms to separate classes
- Icon resources go in `images/`

### Test Code

- Use JUnit 4 (`@Test`, `@Before`, `@After` annotations)
- Test both normal cases and edge cases (empty graphs, disconnected components, single nodes)
- Tests must work without a database connection
- Use descriptive test method names: `testMethodName_scenario_expectedResult`

## Architecture Overview

### Visualization Layer

`Main.java` orchestrates the GUI:
1. Reads edge-list file (generated by `Network.java`)
2. Creates JUNG `SparseMultigraph<String, edge>` from the data
3. Applies `StaticLayout` with cluster-based positioning (3×3 grid by relationship type)
4. Renders via `VisualizationViewer` with custom vertex/edge transformers
5. Timeline controls iterate over 92-day date range, rebuilding the graph per timestamp

### Graph Algorithms

- **ShortestPathFinder:** BFS (hop-optimal) and Dijkstra (weight-optimal) pathfinding between nodes
- **CommunityDetector:** Connected component analysis with per-community metrics (density, dominant relationship type)
- **GraphStats:** Network metrics — degree distribution, density, hub identification, isolated nodes

### Data Pipeline

```
Bluetooth events → findMeetings → matchImei → addLocation → Network → edge-list → Main (GUI)
```

Each pipeline stage reads from and writes to PostgreSQL. The edge-list file bridges the pipeline to the visualizer.

## Making Changes

### Before You Start

1. Check [open issues](https://github.com/sauravbhattacharya001/GraphVisual/issues) for existing discussions
2. For significant changes, open an issue first to discuss the approach
3. Fork the repository and create a feature branch

### Branch Naming

- `feature/<description>` — new features
- `fix/<description>` — bug fixes
- `refactor/<description>` — code improvements
- `docs/<description>` — documentation changes
- `test/<description>` — test additions or fixes

### Testing Your Changes

1. **All existing tests must pass** — run the full test suite before pushing
2. **Add tests for new functionality** — especially graph algorithms and metrics
3. **GUI changes** — test manually with the visualizer (run `Main.java`)
4. **Data pipeline changes** — require a PostgreSQL instance with test data

## Pull Request Process

1. **Create a focused PR** — one logical change per PR
2. **Write a clear description** — what changed, why, and how to test it
3. **All CI checks must pass** — build, test, CodeQL security scan
4. **Keep the PR small** — large PRs are harder to review. Split if possible
5. **Update documentation** — if your change affects the README, update it

### PR Checklist

- [ ] Code compiles without warnings
- [ ] All tests pass
- [ ] New tests added for new functionality
- [ ] No hardcoded credentials or secrets
- [ ] Documentation updated if applicable
- [ ] Commit messages follow conventions (below)

## Commit Conventions

Use [Conventional Commits](https://www.conventionalcommits.org/) format:

```
<type>: <short description>

<optional body>
```

### Types

| Type | Description |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `refactor` | Code change that neither fixes a bug nor adds a feature |
| `test` | Adding or fixing tests |
| `docs` | Documentation only |
| `perf` | Performance improvement |
| `ci` | CI/CD configuration |
| `chore` | Build process or auxiliary tool changes |

### Examples

```
feat: add Dijkstra weight-optimal pathfinding
fix: prevent NullPointerException when graph has isolated nodes
test: add CommunityDetector edge case tests for single-node components
docs: update README with Docker setup instructions
```

## Issue Guidelines

### Bug Reports

Include:
- Steps to reproduce
- Expected vs actual behavior
- Java version and OS
- Stack trace if applicable
- Sample data or edge-list file if relevant

### Feature Requests

Include:
- Problem or use case description
- Proposed solution
- Impact on existing functionality
- Whether you'd like to implement it yourself

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).
