package app;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Generates graph edge-list files from meeting database records.
 *
 * <p>Queries the meeting table for different relationship types
 * (friends, classmates, study-groups, strangers, familiar strangers)
 * and writes the results as a weighted edge list file.</p>
 *
 * @author zalenix
 */
public class Network {

    /**
     * Describes a single meeting query: the SQL, edge type code,
     * duration threshold, count threshold, and whether the duration
     * comparison is {@code >} or {@code <} / count is {@code >=} or
     * other comparison.
     */
    private static class MeetingQuery {
        final String sql;
        final String edgeCode;
        final int durationThreshold;
        final int countThreshold;

        MeetingQuery(String sql, String edgeCode, int durationThreshold,
                     int countThreshold) {
            this.sql = sql;
            this.edgeCode = edgeCode;
            this.durationThreshold = durationThreshold;
            this.countThreshold = countThreshold;
        }
    }

    // ── SQL templates ──────────────────────────────────────────────
    // Each query follows the same structure: filter meetings by month,
    // date, location, and duration; group by IMEI pair; filter by count;
    // join with deviceID to resolve human-readable IDs.

    private static final String LOCATION_MEETING_SQL =
            " SELECT x.id, y.id, C, d"
            + " FROM (SELECT imei1, imei2, count(*) as C, avg(duration) as d"
            + "       FROM (SELECT imei1, imei2, duration"
            + "             FROM meeting"
            + "             WHERE month = ? AND date = ? AND %s AND duration %s ?) as b"
            + "       GROUP BY imei1, imei2) as a, deviceID as x, deviceID as y"
            + " WHERE C %s ? AND a.imei1 = x.imei AND a.imei2 = y.imei";

    private static String buildSql(String locationFilter,
                                   String durationOp,
                                   String countOp) {
        return String.format(LOCATION_MEETING_SQL,
                locationFilter, durationOp, countOp);
    }

    // Location filters
    private static final String LOC_PUBLIC = "location = 'public'";
    private static final String LOC_CLASS = "location = 'class'";
    private static final String LOC_NON_CLASS =
            "location NOT IN ('class', 'unknown', '')";

    /**
     * Connects to the database and writes out the edge-list from the
     * meeting DB table, forming edges of kind: friends, classmates,
     * study-groups, strangers and familiar strangers.
     *
     * <p>The output path is validated to prevent directory traversal —
     * it must resolve to a location within the current working directory.</p>
     *
     * @param path      output file path (must be within the working directory)
     * @param month     month filter
     * @param date      date filter
     * @param dThresF   friend duration threshold
     * @param cThresF   friend count threshold
     * @param dThresFS  familiar stranger duration threshold
     * @param cThresFS  familiar stranger count threshold
     * @param dThresC   classmate duration threshold
     * @param cThresC   classmate count threshold
     * @param dThresS   stranger duration threshold
     * @param cThresS   stranger count threshold
     * @param dThresSg  study group duration threshold
     * @param cThresSg  study group count threshold
     * @throws Exception if database or file I/O fails
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

        // Define all queries with their parameters
        MeetingQuery[] queries = {
            new MeetingQuery(
                buildSql(LOC_PUBLIC, ">", ">="),
                "f", dThresF, cThresF),
            new MeetingQuery(
                buildSql(LOC_CLASS, ">", "<="),
                "sg", dThresSg, cThresSg),
            new MeetingQuery(
                buildSql(LOC_CLASS, ">", ">="),
                "c", dThresC, cThresC),
            new MeetingQuery(
                buildSql(LOC_NON_CLASS, "<", "<"),
                "s", dThresS, cThresS),
            new MeetingQuery(
                buildSql(LOC_NON_CLASS, "<", ">"),
                "fs", dThresFS, cThresFS),
        };

        try (Connection conn = Util.getAppConnection()) {
            StringBuilder sb = new StringBuilder("edges");

            for (MeetingQuery mq : queries) {
                appendEdges(conn, sb, mq, month, date);
            }

            // Write output file — use validated outputFile, not raw path
            if (outputFile.exists()) {
                outputFile.delete();
            }
            try (BufferedWriter out = new BufferedWriter(
                    new FileWriter(outputFile))) {
                out.write(sb.toString());
            }
        }
    }

    /**
     * Executes a single meeting query and appends matching edges to the
     * StringBuilder.
     *
     * @param conn   database connection
     * @param sb     output buffer
     * @param mq     the query descriptor
     * @param month  month parameter
     * @param date   date parameter
     * @throws Exception if the query fails
     */
    private static void appendEdges(Connection conn, StringBuilder sb,
            MeetingQuery mq, String month, String date) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                mq.sql,
                ResultSet.TYPE_SCROLL_INSENSITIVE,
                ResultSet.CONCUR_READ_ONLY)) {
            ps.setString(1, month);
            ps.setString(2, date);
            ps.setInt(3, mq.durationThreshold);
            ps.setInt(4, mq.countThreshold);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double weight = rs.getInt(3) * (double) rs.getFloat(4);
                    sb.append('\n').append(mq.edgeCode).append(' ')
                      .append(rs.getString(1)).append(' ')
                      .append(rs.getString(2)).append(' ')
                      .append(weight);
                }
            }
        }
    }
}
