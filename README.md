<div align="center">

# 📊 GraphVisual

**Community evolution visualization for student social networks**

Built with Java and [JUNG](http://jung.sourceforge.net/) (Java Universal Network/Graph Framework)

[![CI](https://github.com/sauravbhattacharya001/GraphVisual/actions/workflows/ci.yml/badge.svg)](https://github.com/sauravbhattacharya001/GraphVisual/actions/workflows/ci.yml)
[![CodeQL](https://github.com/sauravbhattacharya001/GraphVisual/actions/workflows/codeql.yml/badge.svg)](https://github.com/sauravbhattacharya001/GraphVisual/actions/workflows/codeql.yml)
[![Java](https://img.shields.io/badge/Java-8%2B-orange?logo=openjdk)](https://openjdk.org/)
[![JUNG](https://img.shields.io/badge/JUNG-2.0.1-blue)](http://jung.sourceforge.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://github.com/sauravbhattacharya001/GraphVisual/blob/master/LICENSE)
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

## Architecture

```
GraphVisual/
├── src/
│   ├── gvisual/
│   │   ├── Main.java          # Swing GUI — graph panel, timeline, controls, legend
│   │   ├── edge.java          # Edge model (type, vertices, weight, label)
│   │   └── GraphStats.java    # Network metrics calculator (density, degree, hubs)
│   └── app/
│       ├── Network.java       # Generates edge-list files from DB meeting queries
│       ├── Util.java          # Database connection factory (env-based credentials)
│       ├── findMeetings.java  # Bluetooth event → meeting extraction pipeline
│       ├── addLocation.java   # Meeting location classification (public/class/path)
│       └── matchImei.java     # Device node → IMEI matching
├── test/
│   ├── gvisual/EdgeTest.java  # Edge model unit tests
│   ├── gvisual/GraphStatsTest.java # Network metrics unit tests
│   └── app/UtilMethodsTest.java # Utility method tests
├── lib/                       # JUNG 2.0.1, PostgreSQL JDBC, Commons IO, Java3D
└── images/                    # UI icons (play, pause, stop, etc.) and legend colors
```

### Data Pipeline

```
Bluetooth events (event_3)
    │
    ▼
findMeetings.java ──→ meeting table (imei pairs, start/end time, duration)
    │
    ▼
matchImei.java ──→ maps device nodes to IMEI identifiers
    │
    ▼
addLocation.java ──→ classifies meeting locations via WiFi access points
    │
    ▼
Network.java ──→ generates edge-list file with parameterized SQL queries
    │
    ▼
Main.java ──→ renders interactive JUNG graph visualization
```

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

## Research Context

This project was built to study **community evolution in student social networks** using Bluetooth proximity sensing. Key research questions:

- How do social communities form and evolve over a semester?
- What distinguishes friends from familiar strangers based on meeting patterns?
- How do physical spaces (classrooms vs. public areas) shape community structure?

The visualization tool enables researchers to explore these questions interactively by adjusting relationship parameters and observing how graph structures change over time.

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Make your changes with tests
4. Submit a pull request

### Development Notes

- The project uses NetBeans project structure (`nbproject/`)
- Database schema is implied by the SQL queries — no migration scripts are included
- All database credentials must be set via environment variables (`DB_HOST`, `DB_USER`, `DB_PASS`)
- CI runs on JDK 11 and 17 via GitHub Actions
