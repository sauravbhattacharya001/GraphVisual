package app;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;

import app.MeetingQueryConfig.Comparison;
import app.MeetingQueryConfig.LocationFilter;

/**
 * Generates Edge-list files from the meeting database.
 *
 * <p>Connects to the PostgreSQL meeting database and produces an Edge-list
 * file for each relationship type (friends, classmates, study-groups,
 * strangers, familiar strangers) based on configurable duration and
 * frequency thresholds.</p>
 *
 * <p>The output path is validated to prevent directory traversal —
 * it must resolve to a location within the current working directory.</p>
 *
 * @author zalenix
 */
public class Network {

    /**
     * Generates the edge-list file from the meeting database.
     *
     * <p>Replaces the previous 13-parameter signature with a structured
     * parameter object approach.  Each relationship type is described by
     * a {@link MeetingQueryConfig} that encapsulates the SQL pattern
     * differences (location filter, comparison direction, thresholds).</p>
     *
     * @param path   output file path (must be within the working directory)
     * @param month  month filter (e.g. "03")
     * @param date   date filter (e.g. "15")
     * @param configs list of relationship query configurations
     * @throws Exception if database access or file I/O fails
     */
    public static void generateFile(String path, String month, String date,
                                     List<MeetingQueryConfig> configs) throws Exception {
        // Validate output path — prevent directory traversal attacks
        File outputFile = new File(path).getCanonicalFile();
        File workingDir = new File(".").getCanonicalFile();
        if (!outputFile.toPath().startsWith(workingDir.toPath())) {
            throw new SecurityException(
                "Output path must be within the working directory. "
                + "Resolved path: " + outputFile.getAbsolutePath());
        }

        System.out.println("connecting...");

        try (Connection conn = Util.getAppConnection()) {
            StringBuilder sb = new StringBuilder("edges");

            for (MeetingQueryConfig config : configs) {
                appendEdges(conn, sb, config.buildSql(), config.getEdgePrefix(),
                           month, date, config.getDurationThreshold(),
                           config.getCountThreshold());
            }

            // Write output file — use validated outputFile, not raw path
            if (outputFile.exists()) {
                outputFile.delete();
            }
            try (BufferedWriter out = new BufferedWriter(new FileWriter(outputFile))) {
                out.write(sb.toString());
            }
        }
    }

    /**
     * Backward-compatible overload preserving the original 13-parameter signature.
     *
     * <p>Delegates to {@link #generateFile(String, String, String, List)} by
     * constructing {@link MeetingQueryConfig} instances from the raw threshold
     * parameters.</p>
     *
     * @deprecated Use {@link #generateFile(String, String, String, List)} with
     *             explicit {@link MeetingQueryConfig} objects instead.
     */
    @Deprecated
    public static void generateFile(String path, String Month, String Date,
            int dThresF, int CThresF, int dThresFS, int CThresFS,
            int dThresC, int CThresC, int dThresS, int CThresS,
            int dThresSg, int CThresSg) throws Exception {

        String[] excludedLocations = {"class", "unknown", ""};

        List<MeetingQueryConfig> configs = Arrays.asList(
            new MeetingQueryConfig("f",  LocationFilter.EXACT,
                new String[]{"public"}, Comparison.GT, Comparison.GTE,
                dThresF, CThresF),
            new MeetingQueryConfig("sg", LocationFilter.EXACT,
                new String[]{"class"}, Comparison.GT, Comparison.LTE,
                dThresSg, CThresSg),
            new MeetingQueryConfig("c",  LocationFilter.EXACT,
                new String[]{"class"}, Comparison.GT, Comparison.GTE,
                dThresC, CThresC),
            new MeetingQueryConfig("s",  LocationFilter.EXCLUDE,
                excludedLocations, Comparison.LT, Comparison.LT,
                dThresS, CThresS),
            new MeetingQueryConfig("fs", LocationFilter.EXCLUDE,
                excludedLocations, Comparison.LT, Comparison.GT,
                dThresFS, CThresFS)
        );

        generateFile(path, Month, Date, configs);
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
