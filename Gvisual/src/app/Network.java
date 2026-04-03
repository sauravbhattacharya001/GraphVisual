package app;

import gvisual.ExportUtils;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Social network edge-list generator - extracts relationship graphs from
 * WiFi co-location meeting data.
 *
 * <p>Queries the meeting database to classify observed pairwise interactions
 * into five relationship categories based on configurable duration and
 * frequency thresholds:</p>
 * <ul>
 *   <li><b>Friends (f)</b> - frequent, long meetings in resolved non-class locations</li>
 *   <li><b>Study groups (sg)</b> - infrequent classroom co-presence</li>
 *   <li><b>Classmates (c)</b> - frequent classroom co-presence</li>
 *   <li><b>Strangers (s)</b> - brief, rare encounters in resolved locations</li>
 *   <li><b>Familiar strangers (fs)</b> - brief but repeated encounters</li>
 * </ul>
 *
 * <p>Output is a weighted edge-list file suitable for import into graph
 * visualization and analysis tools (e.g., GraphVisual, Gephi, NetworkX).
 * Edge weight = count × average duration.</p>
 *
 * @author zalenix
 */
public class Network {

    /**
     *
     * Connects to database and writes out the Edge-list from the meeting DB table, forming edges of kind:
     * friends, classmates, study-groups, strangers and familiar strangers (depending upon parameters).
     *
     * <p>The output path is validated to prevent directory traversal -
     * it must resolve to a location within the current working directory.</p>
     *
     * @param path      output file path for the edge-list (must be within the working directory)
     * @param Month     month filter for meeting records (e.g. "01")
     * @param Date      date filter for meeting records (e.g. "15")
     * @param dThresF   minimum average duration threshold for friend edges
     * @param CThresF   minimum meeting count threshold for friend edges
     * @param dThresFS  maximum duration threshold for familiar-stranger edges
     * @param CThresFS  minimum count threshold for familiar-stranger edges
     * @param dThresC   minimum duration threshold for classmate edges
     * @param CThresC   minimum count threshold for classmate edges
     * @param dThresS   maximum duration threshold for stranger edges
     * @param CThresS   maximum count threshold for stranger edges
     * @param dThresSg  minimum duration threshold for study-group edges
     * @param CThresSg  maximum count threshold for study-group edges
     * @throws Exception if database connection or file I/O fails
     */
    /**
     * Generates an edge-list file using a {@link ThresholdConfig} object
     * instead of 10 individual threshold parameters.
     *
     * @param path   output file path (must be within working directory)
     * @param Month  month filter for meeting records (e.g. "01")
     * @param Date   date filter for meeting records (e.g. "15")
     * @param config threshold configuration for all relationship types
     * @throws Exception if database connection or file I/O fails
     */
    public static void generateFile(String path, String Month, String Date,
                                     ThresholdConfig config) throws Exception {
        generateFile(path, Month, Date,
                config.getFriendDuration(), config.getFriendCount(),
                config.getFamiliarStrangerDuration(), config.getFamiliarStrangerCount(),
                config.getClassmateDuration(), config.getClassmateCount(),
                config.getStrangerDuration(), config.getStrangerCount(),
                config.getStudyGroupDuration(), config.getStudyGroupCount());
    }

    /**
     * @deprecated Use {@link #generateFile(String, String, String, ThresholdConfig)}
     *             for clearer, self-documenting threshold configuration.
     */
    @Deprecated
    public static void generateFile(String path, String Month, String Date, int dThresF, int CThresF, int dThresFS, int CThresFS, int dThresC, int CThresC, int dThresS, int CThresS, int dThresSg, int CThresSg) throws Exception {

        // Validate output path - prevent directory traversal attacks
        File outputFile = new File(path).getCanonicalFile();
        ExportUtils.validateOutputPath(outputFile);

        System.out.println("connecting...");

        // Parameterized query template for location-based meeting queries.
        // Parameters: month, date, location, duration threshold, count threshold.

        // --- Friends query ---
        // Use the same inclusive location filter as stranger/familiar-stranger
        // queries: any resolved, non-class location counts. The previous
        // "location = 'public'" filter silently dropped long meetings at
        // cafés, libraries, paths, etc., under-counting friend edges.
        // See: https://github.com/sauravbhattacharya001/GraphVisual/issues/134

        try (Connection conn = Util.getAppConnection()) {

            // Use StringBuilder instead of String concatenation for performance
            StringBuilder sb = new StringBuilder("edges");

            // All five relationship queries share the same structure, differing
            // only in location filter, duration comparison, and count comparison.
            // buildMeetingSql() generates the parameterized SQL from these axes,
            // eliminating the duplicated query strings.
            appendEdges(conn, sb, buildMeetingSql("NOT IN ('class', 'unknown', '')", ">", ">="),
                    "f",  Month, Date, dThresF,  CThresF);
            appendEdges(conn, sb, buildMeetingSql("= 'class'", ">", "<="),
                    "sg", Month, Date, dThresSg, CThresSg);
            appendEdges(conn, sb, buildMeetingSql("= 'class'", ">", ">="),
                    "c",  Month, Date, dThresC,  CThresC);
            appendEdges(conn, sb, buildMeetingSql("NOT IN ('class', 'unknown', '')", "<", "<"),
                    "s",  Month, Date, dThresS,  CThresS);
            appendEdges(conn, sb, buildMeetingSql("NOT IN ('class', 'unknown', '')", "<", ">"),
                    "fs", Month, Date, dThresFS, CThresFS);

            // Write output file - use validated outputFile, not raw path
            if (outputFile.exists()) {
                outputFile.delete();
            }
            try (BufferedWriter out = new BufferedWriter(new FileWriter(outputFile))) {
                out.write(sb.toString());
            }
        }
    }

    /**
     * Builds a parameterized meeting SQL query from the three axes that
     * vary between relationship types: location filter, duration comparison
     * operator, and count comparison operator.
     *
     * @param locationFilter   SQL fragment for location (e.g. {@code "= 'class'"})
     * @param durationOp       comparison operator for duration threshold
     * @param countOp          comparison operator for count threshold
     * @return parameterized SQL with 4 placeholders: month, date, duration, count
     */
    private static String buildMeetingSql(String locationFilter, String durationOp, String countOp) {
        return "SELECT x.id, y.id, C, d"
                + " FROM (SELECT imei1, imei2, count(*) AS C, avg(duration) AS d"
                + "       FROM (SELECT imei1, imei2, duration"
                + "             FROM meeting"
                + "             WHERE month = ? AND date = ? AND location " + locationFilter
                + "               AND duration " + durationOp + " ?) AS b"
                + "       GROUP BY imei1, imei2) AS a, deviceID AS x, deviceID AS y"
                + " WHERE C " + countOp + " ? AND a.imei1 = x.imei AND a.imei2 = y.imei";
    }

    /**
     * Executes a parameterized meeting query and appends edges to the output buffer.
     *
     * <p>Each query is expected to return (id1, id2, count, avg_duration).
     * The edge weight is computed as count * avg_duration.</p>
     *
     * @param conn       open database connection
     * @param sb         output buffer to append edge lines to
     * @param sql        parameterized SQL query (params: month, date, duration_threshold, count_threshold)
     * @param edgePrefix edge type prefix (e.g. "f", "sg", "c", "s", "fs")
     * @param month      month filter value
     * @param date       date filter value
     * @param dThreshold duration threshold
     * @param cThreshold count threshold
     * @throws Exception if query execution fails
     */
    private static void appendEdges(Connection conn, StringBuilder sb, String sql,
                                     String edgePrefix, String month, String date,
                                     int dThreshold, int cThreshold) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql,
                ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)) {
            ps.setString(1, month);
            ps.setString(2, date);
            ps.setInt(3, dThreshold);
            ps.setInt(4, cThreshold);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double weight = rs.getInt(3) * (double) rs.getFloat(4);
                    sb.append('\n').append(edgePrefix).append(' ')
                      .append(rs.getString(1)).append(' ')
                      .append(rs.getString(2)).append(' ').append(weight);
                }
            }
        }
    }
}
