package app;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 *
 * @author user
 */
public class addLocation {

    /**
     * Validates that a time component string matches the expected
     * format of "HH.MM:SS.mmm" (two dot-separated parts on each
     * side of the colon).
     *
     * @param time the raw time string from the database
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

        String result = "2011-" + month + "-" + date + " " + timeArr1[0] + ":" + timeArr1[1] + ":" + timeArr2[0] + "." + timeArr2[1];
        return result;
    }

    // SQL: find common access points between two IMEIs in a time window (intersection)
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
                while (rs.next()) {
                    ap = rs.getInt("ap");
                }
            }

            // Fallback: try imei1 alone
            if (ap == 0) {
                singleApPs.setString(1, imei1);
                singleApPs.setString(2, startTimeStamp);
                singleApPs.setString(3, endTimeStamp);
                try (ResultSet rs = singleApPs.executeQuery()) {
                    while (rs.next()) {
                        ap = rs.getInt("ap");
                    }
                }
            }

            // Fallback: try imei2 alone
            if (ap == 0) {
                singleApPs.setString(1, imei2);
                singleApPs.setString(2, startTimeStamp);
                singleApPs.setString(3, endTimeStamp);
                try (ResultSet rs = singleApPs.executeQuery()) {
                    while (rs.next()) {
                        ap = rs.getInt("ap");
                    }
                }
            }

            String apType;
            System.out.println("common access point # " + ap);
            switch (ap) {
                case 0:
                    apType = "";
                    break;
                case 7:
                case 16:
                case 20:
                case 35:
                case 38:
                case 39:
                    apType = "public";
                    break;
                case 29:
                case 30:
                case 31:
                case 32:
                case 33:
                case 34:
                case 36:
                    apType = "class";
                    break;
                default: // all else
                    apType = "path";
                    break;
            }

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
