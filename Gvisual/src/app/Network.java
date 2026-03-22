package app;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Generates an edge-list file from the meeting database table.
 *
 * <p>Edges represent social relationships (friends, classmates, study-groups,
 * strangers, familiar strangers) derived from meeting co-occurrence patterns.</p>
 *
 * @author zalenix
 */
public class Network {

    /**
     * Describes a single edge-type query: the SQL, the edge label prefix,
     * and the duration/count thresholds to bind.
     */
    private static class EdgeQuery {
        final String sql;
        final String label;
        final int durationThreshold;
        final int countThreshold;

        EdgeQuery(String sql, String label, int durationThreshold, int countThreshold) {
            this.sql = sql;
            this.label = label;
            this.durationThreshold = durationThreshold;
            this.countThreshold = countThreshold;
        }
    }

    // Query template for location-match queries (location = ?).
    private static final String LOCATION_MATCH_TEMPLATE =
              " SELECT x.id, y.id, C, d"
            + " FROM ( SELECT imei1, imei2, count(*) AS C, avg(duration) AS d"
            + "        FROM ( SELECT imei1, imei2, duration"
            + "               FROM meeting"
            + "               WHERE month = ? AND date = ? AND location = ? AND duration %s ?) AS b"
            + "        GROUP BY imei1, imei2) AS a, deviceID AS x, deviceID AS y"
            + " WHERE C %s ? AND a.imei1 = x.imei AND a.imei2 = y.imei";

    // Query template for location-exclusion queries (location NOT IN ...).
    private static final String LOCATION_EXCLUDE_TEMPLATE =
              " SELECT x.id, y.id, C, d"
            + " FROM ( SELECT imei1, imei2, count(*) AS C, avg(duration) AS d"
            + "        FROM ( SELECT imei1, imei2, duration"
            + "               FROM meeting"
            + "               WHERE month = ? AND date = ? AND location NOT IN ('class', 'unknown', '') AND duration < ?) AS b"
            + "        GROUP BY imei1, imei2) AS a, deviceID AS x, deviceID AS y"
            + " WHERE C %s ? AND a.imei1 = x.imei AND a.imei2 = y.imei";

    private static String locationMatchSql(String durationOp, String countOp) {
        return String.format(LOCATION_MATCH_TEMPLATE, durationOp, countOp);
    }

    private static String locationExcludeSql(String countOp) {
        return String.format(LOCATION_EXCLUDE_TEMPLATE, countOp);
    }

    /**
     * Executes a single edge query and appends matching edges to the StringBuilder.
     */
    private static void executeEdgeQuery(Connection conn, EdgeQuery eq,
                                          String month, String date,
                                          StringBuilder sb) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(eq.sql,
                ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)) {
            ps.setString(1, month);
            ps.setString(2, date);
            ps.setInt(3, eq.durationThreshold);
            ps.setInt(4, eq.countThreshold);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double weight = rs.getInt(3) * (double) rs.getFloat(4);
                    sb.append('\n').append(eq.label).append(' ')
                      .append(rs.getString(1)).append(' ')
                      .append(rs.getString(2)).append(' ')
                      .append(weight);
                }
            }
        }
    }

    /**
     * Connects to the database and writes out the edge-list from the meeting
     * table, forming edges of kind: friends, classmates, study-groups,
     * strangers and familiar strangers (depending upon parameters).
     *
     * <p>The output path is validated to prevent directory traversal —
     * it must resolve to a location within the current working directory.</p>
     *
     * @param path       output file path (must be within the working directory)
     * @param month      month filter
     * @param date       date filter
     * @param dThresF    duration threshold for friends
     * @param cThresF    count threshold for friends
     * @param dThresFS   duration threshold for familiar strangers
     * @param cThresFS   count threshold for familiar strangers
     * @param dThresC    duration threshold for classmates
     * @param cThresC    count threshold for classmates
     * @param dThresS    duration threshold for strangers
     * @param cThresS    count threshold for strangers
     * @param dThresSg   duration threshold for study groups
     * @param cThresSg   count threshold for study groups
     * @throws Exception if database or I/O operations fail
     */
    public static void generateFile(String path, String month, String date,
            int dThresF, int cThresF, int dThresFS, int cThresFS,
            int dThresC, int cThresC, int dThresS, int cThresS,
            int dThresSg, int cThresSg) throws Exception {

        // Validate output path — prevent directory traversal attacks
        File outputFile = new File(path).getCanonicalFile();
        File workingDir = new File(".").getCanonicalFile();
        if (!outputFile.toPath().startsWith(workingDir.toPath())) {
            throw new SecurityException(
                "Output path must be within the working directory. "
                + "Resolved path: " + outputFile.getAbsolutePath());
        }

        System.out.println("connecting...");

        // Define all edge queries — each differs only in location filter,
        // duration/count comparison operators, and threshold values.
        EdgeQuery[] queries = {
            new EdgeQuery(locationMatchSql(">", ">="), "f",  dThresF,  cThresF),   // friends (public, long duration, high count)
            new EdgeQuery(locationMatchSql(">", "<="), "sg", dThresSg, cThresSg),  // study groups (class, long duration, low count)
            new EdgeQuery(locationMatchSql(">", ">="), "c",  dThresC,  cThresC),   // classmates (class, long duration, high count)
            new EdgeQuery(locationExcludeSql("<"),      "s",  dThresS,  cThresS),   // strangers (non-class, short duration, low count)
            new EdgeQuery(locationExcludeSql(">"),      "fs", dThresFS, cThresFS),  // familiar strangers (non-class, short duration, high count)
        };

        // The friends query uses location='public', study groups and classmates use location='class'.
        // We need to bind the location parameter for LOCATION_MATCH_TEMPLATE queries.
        // Rework: use a 5-param version that includes location for match queries.

        try (Connection conn = Util.getAppConnection()) {
            StringBuilder sb = new StringBuilder("edges");

            // Friends: location = 'public'
            executeLocationMatchQuery(conn, "f", "public", ">", ">=",
                    month, date, dThresF, cThresF, sb);
            // Study groups: location = 'class'
            executeLocationMatchQuery(conn, "sg", "class", ">", "<=",
                    month, date, dThresSg, cThresSg, sb);
            // Classmates: location = 'class'
            executeLocationMatchQuery(conn, "c", "class", ">", ">=",
                    month, date, dThresC, cThresC, sb);
            // Strangers: location NOT IN (...)
            executeEdgeQuery(conn,
                    new EdgeQuery(locationExcludeSql("<"), "s", dThresS, cThresS),
                    month, date, sb);
            // Familiar strangers: location NOT IN (...)
            executeEdgeQuery(conn,
                    new EdgeQuery(locationExcludeSql(">"), "fs", dThresFS, cThresFS),
                    month, date, sb);

            // Write output file
            if (outputFile.exists()) {
                outputFile.delete();
            }
            try (BufferedWriter out = new BufferedWriter(new FileWriter(outputFile))) {
                out.write(sb.toString());
            }
        }
    }

    /**
     * Executes a location-match edge query (location = ?) with 5 bind parameters.
     */
    private static void executeLocationMatchQuery(Connection conn, String label,
            String location, String durationOp, String countOp,
            String month, String date, int durationThreshold, int countThreshold,
            StringBuilder sb) throws Exception {
        String sql = String.format(LOCATION_MATCH_TEMPLATE, durationOp, countOp);
        try (PreparedStatement ps = conn.prepareStatement(sql,
                ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)) {
            ps.setString(1, month);
            ps.setString(2, date);
            ps.setString(3, location);
            ps.setInt(4, durationThreshold);
            ps.setInt(5, countThreshold);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double weight = rs.getInt(3) * (double) rs.getFloat(4);
                    sb.append('\n').append(label).append(' ')
                      .append(rs.getString(1)).append(' ')
                      .append(rs.getString(2)).append(' ')
                      .append(weight);
                }
            }
        }
    }
}
