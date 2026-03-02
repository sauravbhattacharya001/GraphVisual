# Security

This document describes the security model, threat surface, and
mitigations in GraphVisual.

## Reporting Vulnerabilities

If you discover a security vulnerability, please **do not** open a public
issue. Instead, email the maintainer directly or use GitHub's private
vulnerability reporting feature.

## Security Model

GraphVisual is a desktop Swing application that processes Bluetooth
proximity data from a PostgreSQL database and renders social network
graphs. Its security posture is shaped by two distinct surfaces:

1. **Database access** — connects to PostgreSQL using credentials from
   environment variables
2. **File I/O** — reads edge-list files and writes exports (GraphML, PNG)

### Threat Categories

| Threat | Surface | Status |
|--------|---------|--------|
| SQL injection | Database queries | ✅ Mitigated — all queries use `PreparedStatement` with parameterized bindings |
| Credential exposure | Database connection | ✅ Mitigated — credentials read from `DB_HOST`, `DB_USER`, `DB_PASS` environment variables; never hardcoded |
| XML injection in exports | GraphML export | ✅ Mitigated — `GraphMLExporter.escapeXml()` escapes `&`, `<`, `>`, `"`, `'` |
| Path traversal | File output | ✅ Mitigated — `Network.generateFile()` validates output path is within working directory; uses validated canonical path for all I/O |
| JDBC connection string injection | Database connection | ✅ Mitigated — `Util.validateHost()` enforces hostname format via regex, rejecting `/`, `?`, `&`, `=`, `;` and other injection characters |
| Malformed input files | Edge-list parser | ✅ Mitigated — validates field count and weight format before constructing edge objects |
| NaN/Infinity weights | Edge-list parser | ✅ Mitigated — rejects `NaN` and `Infinity` weight values |
| Denial of service (large graphs) | Graph analysis | ⚠️ Partial — analyzers have no built-in size limits; very large graphs can exhaust memory |

## Database Security

### Parameterized Queries

All SQL queries in the `app/` package use `PreparedStatement` with `?`
placeholders. There is no string concatenation of user input into SQL:

- `Network.java` — 5 parameterized queries for relationship extraction
- `matchImei.java` — 4 parameterized queries for IMEI matching
- `addLocation.java` — 3 parameterized queries for WiFi AP lookup
- `findMeetings.java` — parameterized queries for meeting extraction

### Credential Management

Database credentials are loaded exclusively from environment variables
via `Util.java`:

```java
String host = validateHost(envOrDefault("DB_HOST", DEFAULT_HOST));
String user = requireEnv("DB_USER");  // throws if missing
String pass = requireEnv("DB_PASS");  // throws if missing
```

Missing required variables cause an immediate `IllegalStateException`
with a clear error message rather than falling through to a default
or null credential.

### Host Validation

The `DB_HOST` environment variable is validated against a strict regex
pattern (`^[a-zA-Z0-9._-]+(:[0-9]{1,5})?$`) before being interpolated
into the JDBC connection URL. This prevents **JDBC connection string
injection** attacks where a malicious host value containing `/`, `?`,
`&`, or `=` characters could inject arbitrary driver parameters.

PostgreSQL JDBC driver parameters like `socketFactory` and
`socketFactoryArg` have been used in the wild for remote code execution
via deserialization gadgets. The host validation closes this vector.

## File I/O Security

### Output Path Validation

`Network.generateFile()` validates that the output file path resolves
to a location within the current working directory using canonical path
comparison, and uses the validated `File` object for all subsequent I/O:

```java
File outputFile = new File(path).getCanonicalFile();
File workingDir = new File(".").getCanonicalFile();
if (!outputFile.toPath().startsWith(workingDir.toPath())) {
    throw new SecurityException("Output path must be within the working directory.");
}
// ... later ...
try (BufferedWriter out = new BufferedWriter(new FileWriter(outputFile))) {
    out.write(sb.toString());  // uses validated outputFile, not raw path
}
```

This prevents directory traversal attacks via paths like
`../../etc/crontab`.

### Edge-List Input Validation

The edge-list parser in `Main.java` validates each line before
constructing edge objects:

- **Field count** — requires at least 4 fields (type, vertex1, vertex2,
  weight); malformed lines are skipped with a warning
- **Weight parsing** — catches `NumberFormatException` from invalid
  weight strings
- **Non-finite weights** — rejects `NaN` and `Infinity` values that
  could corrupt analysis results

### GraphML Export

`GraphMLExporter.escapeXml()` handles all five XML special characters
(`& < > " '`), preventing injection of arbitrary XML elements or
attributes into exported files.

## Analysis Engine Security

All analyzer constructors validate their input:

```java
if (graph == null) {
    throw new IllegalArgumentException("Graph must not be null");
}
```

Result objects wrap collections in `Collections.unmodifiable*()` to
prevent mutation after creation.

## Dependencies

| Dependency | Version | Notes |
|------------|---------|-------|
| JUNG | 2.0.1 | Graph library — no known CVEs |
| PostgreSQL JDBC | 8.3-604 | Legacy driver — consider upgrading for TLS improvements |
| Commons IO | 1.4 | File utilities — consider upgrading for security patches |
| JUnit | 4.13.2 | Test-only dependency |

### Recommendations

- **Upgrade PostgreSQL JDBC** to 42.x for TLS 1.3 support and
  security fixes
- **Upgrade Commons IO** to 2.x for path traversal fixes in utility
  methods
- **Run with least-privilege database credentials** — the application
  only needs SELECT on `nic_aziala` tables and SELECT/INSERT/UPDATE on
  `nic_apps` tables

## CodeQL

This repository has [CodeQL](https://github.com/sauravbhattacharya001/GraphVisual/actions)
configured for automated security scanning on every push.

## Security Audit Trail

| Date | Finding | Severity | Fix |
|------|---------|----------|-----|
| 2026-03-02 | `Network.generateFile()` path traversal bypass — validation used canonical `outputFile` but file write used raw `path` | High | Changed file write to use validated `outputFile` |
| 2026-03-02 | `Util` JDBC connection string injection — `DB_HOST` env var concatenated into JDBC URL without sanitization, enabling parameter injection and potential RCE via `socketFactory` gadgets | High | Added `validateHost()` with strict hostname regex |
