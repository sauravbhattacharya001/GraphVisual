package app;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 *
 *
 * @author zalenix
 */
public class Network {

    /**
     *
     * Connects to database and writes out the Edge-list from the meeting DB table, forming edges of kind:
     * friends, classmates, study-groups, strangers and familiar strangers (depending upon parameters).
     *
     * <p>The output path is validated to prevent directory traversal —
     * it must resolve to a location within the current working directory.</p>
     *
     * @param path output file path (must be within the working directory)
     * @param Month
     * @param Date
     * @param dThresF
     * @param CThresF
     * @param dThresFS
     * @param CThresFS
     * @param dThresC
     * @param CThresC
     * @param dThresS
     * @param CThresS
     * @param dThresSg
     * @param CThresSg
     * @throws Exception
     */
    public static void generateFile(String path, String Month, String Date, int dThresF, int CThresF, int dThresFS, int CThresFS, int dThresC, int CThresC, int dThresS, int CThresS, int dThresSg, int CThresSg) throws Exception {

        // Validate output path — prevent directory traversal attacks
        File outputFile = new File(path).getCanonicalFile();
        File workingDir = new File(".").getCanonicalFile();
        if (!outputFile.toPath().startsWith(workingDir.toPath())) {
            throw new SecurityException(
                "Output path must be within the working directory. "
                + "Resolved path: " + outputFile.getAbsolutePath());
        }

        System.out.println("connecting...");

        // Parameterized query template for location-based meeting queries.
        // Parameters: month, date, location, duration threshold, count threshold.

        // --- Friends query ---
        String friendSql = " SELECT x.id , y.id , C , d  "
                + " FROM ( SELECT imei1 , imei2, count(*) as C,avg(duration) as d"
                + "       FROM ( SELECT imei1, imei2, duration"
                + "              FROM meeting"
                + "              WHERE month = ? AND date = ? AND location= 'public' AND duration > ?) as b"
                + "       GROUP BY imei1, imei2) as a, deviceID as x, deviceID as y"
                + " WHERE C >= ? AND a.imei1= x.imei AND a.imei2 = y.imei";

        // --- Study groups query ---
        String studygSql = " SELECT x.id , y.id , C , d   "
                + " FROM ( SELECT imei1, imei2, count(*) as C,avg(duration) as d"
                + "       FROM ( SELECT imei1, imei2, duration"
                + "              FROM meeting"
                + "              WHERE month = ? AND date = ? AND location= 'class' AND duration > ?) as b"
                + "       GROUP BY imei1, imei2) as a, deviceID as x, deviceID as y"
                + " WHERE C <= ? AND a.imei1= x.imei AND a.imei2 = y.imei";

        // --- Classmates query ---
        String cmateSql = " SELECT x.id , y.id, C , d  "
                + " FROM ( SELECT imei1, imei2, count(*) as C, avg(duration) as d"
                + "       FROM ( SELECT imei1, imei2, duration"
                + "              FROM meeting"
                + "              WHERE month = ? AND date = ? AND location= 'class' AND duration > ?) as b"
                + "       GROUP BY imei1, imei2) as a, deviceID as x, deviceID as y"
                + " WHERE C >= ? AND a.imei1= x.imei AND a.imei2 = y.imei";

        // --- Strangers query ---
        // Exclude both 'class' and 'unknown' locations so only meetings with
        // a resolved location (e.g. 'public', 'path') are considered.
        String strangerSql = " SELECT x.id , y.id , C , d "
                + " FROM ( SELECT imei1, imei2, count(*) as C,avg(duration) as d"
                + "       FROM ( SELECT imei1, imei2, duration"
                + "              FROM meeting"
                + "              WHERE month = ? AND date = ? AND location NOT IN ('class', 'unknown', '') AND duration < ?) as b"
                + "       GROUP BY imei1, imei2) as a, deviceID as x, deviceID as y"
                + " WHERE C < ? AND a.imei1= x.imei AND a.imei2 = y.imei";

        // --- Familiar strangers query ---
        String famstrangerSql = " SELECT x.id , y.id , C , d  "
                + " FROM ( SELECT imei1, imei2, count(*) as C, avg(duration) as d"
                + "       FROM ( SELECT imei1, imei2, duration"
                + "              FROM meeting"
                + "              WHERE month = ? AND date = ? AND location NOT IN ('class', 'unknown', '') AND duration < ?) as b"
                + "       GROUP BY imei1, imei2) as a , deviceID as x, deviceID as y"
                + " WHERE C > ? AND a.imei1= x.imei AND a.imei2 = y.imei";

        try (Connection conn = Util.getAppConnection()) {

            // Use StringBuilder instead of String concatenation for performance
            StringBuilder sb = new StringBuilder("edges");

            // Execute each relationship query using the shared helper
            appendEdges(conn, sb, friendSql,      "f",  Month, Date, dThresF,  CThresF);
            appendEdges(conn, sb, studygSql,       "sg", Month, Date, dThresSg, CThresSg);
            appendEdges(conn, sb, cmateSql,        "c",  Month, Date, dThresC,  CThresC);
            appendEdges(conn, sb, strangerSql,     "s",  Month, Date, dThresS,  CThresS);
            appendEdges(conn, sb, famstrangerSql,  "fs", Month, Date, dThresFS, CThresFS);

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
