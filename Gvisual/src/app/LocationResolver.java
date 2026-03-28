package app;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves meeting locations by cross-referencing WiFi access point data.
 *
 * <p>For each meeting record in the database, queries the trace/event tables
 * to find the strongest common access point shared by the two participants
 * during the meeting's time window. If no common AP exists, falls back to
 * the strongest AP observed by either participant individually.</p>
 *
 * <p>Replaces the original {@code addLocation} class with:</p>
 * <ul>
 *   <li>Java naming conventions (PascalCase class name)</li>
 *   <li>AP-to-location mapping extracted to a configurable {@code Map}
 *       instead of a hard-coded switch statement</li>
 *   <li>Duplicated fallback query logic extracted to {@link #findBestAP}</li>
 * </ul>
 *
 * @author zalenix
 */
public class LocationResolver {

    /**
     * Access-point ID → location type mapping.
     * <ul>
     *   <li>"public" — common areas (cafeterias, lounges)</li>
     *   <li>"class"  — classrooms</li>
     *   <li>Unmapped APs default to "path" (corridors, transit areas)</li>
     *   <li>AP 0 (not found) maps to "" (unknown)</li>
     * </ul>
     */
    private static final Map<Integer, String> AP_LOCATION_MAP;
    static {
        Map<Integer, String> m = new HashMap<>();
        // Public-area access points
        for (int ap :/*** ap ids ***/ new int[]{7, 16, 20, 35, 38, 39}) {
            m.put(ap, "public");
        }
        // Classroom access points
        for (int ap : new int[]{29, 30, 31, 32, 33, 34, 36}) {
            m.put(ap, "class");
        }
        AP_LOCATION_MAP = Collections.unmodifiableMap(m);
    }

    /**
     * Maps an access-point ID to its location type.
     *
     * @param ap the access-point ID (0 = not found)
     * @return location type: "public", "class", "path", or "" (unknown)
     */
    static String classifyAP(int ap) {
        if (ap == 0) return "";
        return AP_LOCATION_MAP.getOrDefault(ap, "path");
    }

    /**
     * Validates that a time component string matches the expected
     * format of "HH.MM:SS.mmm" (two dot-separated parts on each
     * side of the colon).
     *
     * @param month the month string
     * @param date  the date string
     * @param time  the raw time string from the database
     * @return formatted timestamp string "2011-MM-DD HH:MM:SS.mmm"
     * @throws IllegalArgumentException if the format is invalid
     */
    public static String getTimeStamp(String month, String date, String time) {
        if (time == null || time.isEmpty()) {
            throw new IllegalArgumentException("Time string must not be null or empty");
        }
        String[] timeArr = time.split(":");
        if (timeArr.length != 2) {
            throw new IllegalArgumentException(
                "Invalid time format (expected one colon separator): " + time);
        }
        String[] timeArr1 = timeArr[0].split("\\.");
        String[] timeArr2 = timeArr[1].split("\\.");
        if (timeArr1.length < 2 || timeArr2.length < 2) {
            throw new IllegalArgumentException(
                "Invalid time format (expected dot-separated components): " + time);
        }

        return "2011-" + month + "-" + date + " "
                + timeArr1[0] + ":" + timeArr1[1] + ":"
                + timeArr2[0] + "." + timeArr2[1];
    }

    // SQL: find common access points between two IMEIs in a time window
    private static final String COMMON_AP_SQL =
            "SELECT * FROM ("
            + "SELECT DISTINCT ap, ssi FROM event AS a, trace AS b "
            + "WHERE a.trace = b.id AND imei = ? AND timestamp >= ? AND timestamp <= ? "
            + "INTERSECT "
            + "SELECT DISTINCT ap, ssi FROM event AS a, trace AS b "
            + "WHERE a.trace = b.id AND imei = ? AND timestamp >= ? AND timestamp <= ?"
            + ") AS d ORDER BY ssi DESC LIMIT 1";

    // SQL: find access points for a single IMEI in a time window
    private static final String SINGLE_AP_SQL =
            "SELECT * FROM ("
            + "SELECT DISTINCT ap, ssi FROM event AS a, trace AS b "
            + "WHERE a.trace = b.id AND imei = ? AND timestamp >= ? AND timestamp <= ?"
            + ") AS d ORDER BY ssi DESC LIMIT 1";

    // SQL: update the meeting location
    private static final String UPDATE_LOCATION_SQL =
            "UPDATE meeting SET location = ? "
            + "WHERE imei1 = ? AND imei2 = ? AND starttime = ? AND endtime = ? "
            + "AND month = ? AND date = ?";

    /**
     * Queries for the strongest AP observed by a single IMEI in a time window.
     *
     * @param ps          prepared statement for {@link #SINGLE_AP_SQL}
     * @param imei        the device IMEI
     * @param startStamp  window start timestamp
     * @param endStamp    window end timestamp
     * @return AP id, or 0 if none found
     */
    private static int findBestAP(PreparedStatement ps, String imei,
                                   String startStamp, String endStamp)
            throws Exception {
        ps.setString(1, imei);
        ps.setString(2, startStamp);
        ps.setString(3, endStamp);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("ap");
            }
        }
        return 0;
    }

    public static void main(String[] argv) throws Exception {
        try (Connection azialaConn = Util.getAzialaConnection();
             Connection appConn = Util.getAppConnection();
             java.sql.Statement appStmt = appConn.createStatement(
                     ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
             PreparedStatement commonApPs = azialaConn.prepareStatement(COMMON_AP_SQL,
                     ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
             PreparedStatement singleApPs = azialaConn.prepareStatement(SINGLE_AP_SQL,
                     ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
             PreparedStatement updatePs = appConn.prepareStatement(UPDATE_LOCATION_SQL)) {

            String query = "select * from meeting";
            ResultSet meetings = appStmt.executeQuery(query);

            int count = 0;
            while (meetings.next()) {
                count++;
                System.out.println("finding location for meeting # " + count);
                String imei1 = meetings.getString("imei1");
                String imei2 = meetings.getString("imei2");

                String startTime = meetings.getString("starttime");
                String endTime = meetings.getString("endtime");
                String month = meetings.getString("month");
                String date = meetings.getString("date");
                String startTimeStamp = getTimeStamp(month, date, startTime);
                String endTimeStamp = getTimeStamp(month, date, endTime);

                // Try intersection of both IMEIs first
                commonApPs.setString(1, imei1);
                commonApPs.setString(2, startTimeStamp);
                commonApPs.setString(3, endTimeStamp);
                commonApPs.setString(4, imei2);
                commonApPs.setString(5, startTimeStamp);
                commonApPs.setString(6, endTimeStamp);

                int ap = 0;
                try (ResultSet rs = commonApPs.executeQuery()) {
                    if (rs.next()) {
                        ap = rs.getInt("ap");
                    }
                }

                // Fallback: try each IMEI individually
                if (ap == 0) {
                    ap = findBestAP(singleApPs, imei1, startTimeStamp, endTimeStamp);
                }
                if (ap == 0) {
                    ap = findBestAP(singleApPs, imei2, startTimeStamp, endTimeStamp);
                }

                String apType = classifyAP(ap);
                System.out.println("common access point # " + ap + " → " + apType);

                updatePs.setString(1, apType);
                updatePs.setString(2, imei1);
                updatePs.setString(3, imei2);
                updatePs.setString(4, startTime);
                updatePs.setString(5, endTime);
                updatePs.setString(6, month);
                updatePs.setString(7, date);
                updatePs.executeUpdate();
            }
        }
    }
}
