# Changelog

All notable changes to GraphVisual will be documented in this file.

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
