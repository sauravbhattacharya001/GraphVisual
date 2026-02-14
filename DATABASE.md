# Database Schema Reference

GraphVisual uses two PostgreSQL databases to process Bluetooth proximity data into social network graphs. This document describes the expected schema for each database.

## Databases

| Database | Connection Method | Purpose |
|----------|------------------|---------|
| `nic_apps` | `Util.getAppConnection()` | Main application database — meetings, devices, edge generation |
| `nic_aziala` | `Util.getAzialaConnection()` | WiFi trace database — used for meeting location classification |

Both require environment variables `DB_HOST`, `DB_USER`, `DB_PASS` (see README).

---

## `nic_apps` Database

### `event_3` — Raw Bluetooth proximity events

Source table for the meeting extraction pipeline (`findMeetings`).

| Column | Type | Description |
|--------|------|-------------|
| `rcvrimei` | `text` | IMEI of the receiving device |
| `sndrimei` | `text` | IMEI of the sending device (empty string = no sender) |
| `time` | `text` | Timestamp in custom format `HH.MM:SS.mmm` |
| `month` | `text` | Zero-padded month (`"03"`, `"04"`, `"05"`) |
| `date` | `text` | Zero-padded day of month (`"01"` – `"31"`) |
| `rssi` | `int` | Received Signal Strength Indicator (dBm). Events with `rssi < -60` are filtered out |

**Key query pattern:**
```sql
SELECT DISTINCT rcvrimei, sndrimei, time
FROM event_3
WHERE month = ? AND date = ? AND sndrimei != '' AND rssi >= ?
```

### `meeting` — Extracted meetings

Populated by `findMeetings`, classified by `addLocation`, queried by `Network`.

| Column | Type | Description |
|--------|------|-------------|
| `imei1` | `text` | First IMEI (lexicographically smaller) |
| `imei2` | `text` | Second IMEI (lexicographically larger) |
| `starttime` | `text` | Meeting start in `HH.MM:SS.mmm` format |
| `endtime` | `text` | Meeting end in `HH.MM:SS.mmm` format |
| `location` | `text` | Location type: `'public'`, `'class'`, `'path'`, `'unknown'`, or `''` |
| `month` | `text` | Zero-padded month |
| `date` | `text` | Zero-padded day |
| `duration` | `int` | Meeting duration in minutes (integer, truncated from float) |

**IMEI ordering invariant:** `imei1 < imei2` (lexicographic). Both `findMeetings` and `Network` enforce this ordering before insert/query.

**Location values:**
- `'public'` — Public areas (WiFi AP ids: 7, 16, 20, 35, 38, 39)
- `'class'` — Classrooms (WiFi AP ids: 29–34, 36)
- `'path'` — Pathways / corridors (all other resolved AP ids)
- `'unknown'` — No WiFi AP matched (default before `addLocation` runs)
- `''` — Empty string (no AP data available)

### `deviceID` — Device identifier mapping

Maps IMEI numbers to human-readable node IDs for graph rendering.

| Column | Type | Description |
|--------|------|-------------|
| `imei` | `text` | Device IMEI number |
| `id` | `text` | Human-readable node identifier (displayed as vertex label in graph) |

**Used in JOIN queries** by `Network.generateFile()` to resolve IMEI pairs into node IDs for the edge list.

### `device_1` — Device node registry

Referenced by `matchImei` for IMEI-to-node matching.

| Column | Type | Description |
|--------|------|-------------|
| *(schema implied by `matchImei.java`)* | | Maps device hardware nodes to IMEI identifiers |

> **Note:** The exact columns for `device_1` are not fully visible in the current codebase. The `matchImei` class reads from this table to populate `deviceID`.

---

## `nic_aziala` Database

### `event` — WiFi scan events

Records WiFi access point observations from mobile devices.

| Column | Type | Description |
|--------|------|-------------|
| `trace` | `int` | Foreign key → `trace.id` |
| `ap` | `int` | Access point identifier |
| `ssi` | `int` | Signal strength indicator (higher = stronger) |

### `trace` — WiFi scan traces

Groups WiFi scan events by device and time.

| Column | Type | Description |
|--------|------|-------------|
| `id` | `int` | Primary key |
| `imei` | `text` | Device IMEI |
| `timestamp` | `timestamp` | When the scan was recorded (format: `YYYY-MM-DD HH:MM:SS.mmm`) |

**Location classification query** (`addLocation`):
```sql
-- Find common access points between two IMEIs in a time window
SELECT DISTINCT ap, ssi FROM event AS a, trace AS b
WHERE a.trace = b.id AND imei = ? AND timestamp >= ? AND timestamp <= ?
INTERSECT
SELECT DISTINCT ap, ssi FROM event AS a, trace AS b
WHERE a.trace = b.id AND imei = ? AND timestamp >= ? AND timestamp <= ?
ORDER BY ssi DESC LIMIT 1
```

---

## Data Pipeline Order

The tables must be populated in this sequence:

```
1. event_3        ← Raw Bluetooth data (pre-loaded)
2. device_1       ← Device registry (pre-loaded)
3. matchImei      → Populates deviceID from device_1
4. findMeetings   → Populates meeting from event_3
5. addLocation    → Updates meeting.location using nic_aziala WiFi data
6. Network        → Reads meeting + deviceID → generates edge-list files
```

## Meeting Extraction Algorithm

`findMeetings` uses a **5-minute sliding window** (`WINDOW_SIZE = 5.0`):

1. For each day (March 1 – May 31, 2011), query all Bluetooth events with `rssi >= -60`
2. Group events by device pair (IMEI₁#IMEI₂, lexicographically ordered)
3. Sort timestamps within each pair
4. If the gap between consecutive timestamps exceeds 5 minutes, the current meeting ends and a new one begins
5. Insert each meeting with calculated duration

## Relationship Classification (Network Queries)

`Network.generateFile()` classifies edges using parameterized SQL with duration and count thresholds:

| Relationship | Location Filter | Duration | Count | Edge Code |
|-------------|----------------|----------|-------|-----------|
| Friends | `location = 'public'` | `> threshold` | `>= threshold` | `f` |
| Classmates | `location = 'class'` | `> threshold` | `>= threshold` | `c` |
| Study Groups | `location = 'class'` | `> threshold` | `<= threshold` | `sg` |
| Strangers | `location NOT IN ('class','unknown','')` | `< threshold` | `< threshold` | `s` |
| Familiar Strangers | `location NOT IN ('class','unknown','')` | `< threshold` | `> threshold` | `fs` |

Default thresholds are configurable via the GUI sliders (see `Main.java` constants).

## Access Point Classification

`addLocation` maps WiFi access point IDs to location types:

| AP IDs | Location Type |
|--------|--------------|
| 7, 16, 20, 35, 38, 39 | `public` |
| 29, 30, 31, 32, 33, 34, 36 | `class` |
| All others (non-zero) | `path` |
| 0 (no AP found) | `''` (empty) |

The classification tries three strategies in order:
1. **Intersection** — Common AP between both IMEIs during meeting window
2. **Fallback IMEI₁** — AP for just the first device
3. **Fallback IMEI₂** — AP for just the second device
