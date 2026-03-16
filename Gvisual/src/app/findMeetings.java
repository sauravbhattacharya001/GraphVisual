package app;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Detects face-to-face meetings between device pairs from WiFi co-location data.
 *
 * <p>Scans time-ordered trace events for pairs of IMEIs that share the same
 * access point within a configurable time window ({@code WINDOW_SIZE} minutes).
 * When a gap exceeds the window, the current meeting ends and a new one begins.
 * Detected meetings are written to the {@code meeting} database table with
 * start/end times and participant identifiers.</p>
 *
 * @author zalenix
 */
public class findMeetings {

    /**
     * defines the time window for which no interaction defines the end of a meeting
     */
    private static double WINDOW_SIZE = 5.00;

    /**
     * Validates that a time string has the expected "HH.MM:SS.mmm" format.
     *
     * @param time the time string to validate
     * @param label context label for error messages (e.g. "endTime")
     * @throws IllegalArgumentException if the format is invalid
     */
    private static void validateTimeFormat(String time, String label) {
        if (time == null || time.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be null or empty");
        }
        String[] parts = time.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                "Invalid " + label + " format (expected one colon): " + time);
        }
        if (parts[0].split("\\.").length < 2 || parts[1].split("\\.").length < 2) {
            throw new IllegalArgumentException(
                "Invalid " + label + " format (expected dot-separated components): " + time);
        }
    }

    public static float getTimeDifference(String endTime, String startTime) {
        validateTimeFormat(endTime, "endTime");
        validateTimeFormat(startTime, "startTime");

        String[] endTimeArr = endTime.split(":");
        String[] startTimeArr = startTime.split(":");

        String[] endTimeArr1 = endTimeArr[0].split("\\.");
        String[] endTimeArr2 = endTimeArr[1].split("\\.");

        String[] startTimeArr1 = startTimeArr[0].split("\\.");
        String[] startTimeArr2 = startTimeArr[1].split("\\.");

        float numMin = (Float.parseFloat(endTimeArr1[0]) - Float.parseFloat(startTimeArr1[0])) * 60 + (Float.parseFloat(endTimeArr1[1]) - Float.parseFloat(startTimeArr1[1])) + (Float.parseFloat(endTimeArr2[0]) - Float.parseFloat(startTimeArr2[0])) / 60;
        return numMin;
    }

    public static String getTimeStamp(String month, String date, String time) {
        validateTimeFormat(time, "time");

        String[] timeArr = time.split(":");
        String[] timeArr1 = timeArr[0].split("\\.");
        String[] timeArr2 = timeArr[1].split("\\.");

        String result = "2011-" + month + "-" + date + " " + timeArr1[0] + ":" + timeArr1[1] + ":" + timeArr2[0] + "." + timeArr2[1];
        return result;
    }

    public static void addMeeting(Connection conn, String devicePair, String startTime, String endTime, String month, String date) throws Exception {
        addMeeting(conn, devicePair, startTime, endTime, month, date, "unknown");
    }

    public static void addMeeting(Connection conn, String devicePair, String startTime, String endTime, String month, String date, String locationType) throws Exception {
        String[] imeiArr = devicePair.split("#");
        String imei1, imei2;
        if (imeiArr[0].compareTo(imeiArr[1]) > 0) {
            imei1 = imeiArr[1];
            imei2 = imeiArr[0];
        } else {
            imei2 = imeiArr[1];
            imei1 = imeiArr[0];
        }
        String apType = (locationType != null && !locationType.isEmpty()) ? locationType : "unknown";

        String sql = "INSERT INTO meeting (imei1, imei2, starttime, endtime, location, month, date, duration) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, imei1);
            pstmt.setString(2, imei2);
            pstmt.setString(3, startTime);
            pstmt.setString(4, endTime);
            pstmt.setString(5, apType);
            pstmt.setString(6, month);
            pstmt.setString(7, date);
            pstmt.setInt(8, (int) getTimeDifference(endTime, startTime));
            pstmt.executeUpdate();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("connecting...");

        int Thres = -60;
        int monthInt = 3;
        int dateInt;

        String selectSql = "SELECT DISTINCT rcvrimei, sndrimei, time FROM event_3 "
                         + "WHERE month = ? AND date = ? AND sndrimei != '' AND rssi >= ?";

        try (Connection appConn = Util.getAppConnection();
             PreparedStatement selectStmt = appConn.prepareStatement(
                     selectSql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)) {

            for (; monthInt <= 5; monthInt++) {
                for (dateInt = 1; dateInt <= 31; dateInt++) {

                    String month = String.format("%02d", monthInt);
                    String date = String.format("%02d", dateInt);

                    selectStmt.setString(1, month);
                    selectStmt.setString(2, date);
                    selectStmt.setInt(3, Thres);

                    ResultSet rs = selectStmt.executeQuery();

                    Map<String, SortedSet<String>> deviceInteraction = new HashMap<String, SortedSet<String>>();

                    rs.last();
                    System.out.println("fetched " + rs.getRow() + " number of entries....still working...");
                    rs.first();
                    while (rs.next()) {
                        if (rs.getRow() % 1000 == 0) {
                            System.out.println("added " + rs.getRow() + " number of entries to map");
                        }

                        String imei1, imei2;
                        if (rs.getString(1).compareTo(rs.getString(2)) > 0) {
                            imei1 = rs.getString(2);
                            imei2 = rs.getString(1);
                        } else {
                            imei1 = rs.getString(1);
                            imei2 = rs.getString(2);
                        }

                        String curTime = rs.getString(3);
                        if (curTime.length() == 11) {
                            curTime = "0" + curTime;
                        }

                        String devicePair = imei1 + "#" + imei2;

                        // computeIfAbsent avoids the redundant remove-then-put
                        // pattern — the SortedSet is a reference type, so adding
                        // to the existing set is sufficient.
                        deviceInteraction
                            .computeIfAbsent(devicePair, k -> new TreeSet<String>())
                            .add(curTime);
                    }

                    System.out.println("Hash Map created");

                    System.out.println("Total different pairs detected = " + deviceInteraction.size());

                    for (String x : deviceInteraction.keySet()) {
                        SortedSet<String> curSet = deviceInteraction.get(x);

                        String meetingStartTime = null;
                        String lastTime = null;

                        for (String y : curSet) {
                            if (lastTime == null) {
                                lastTime = y;
                                meetingStartTime = y;
                            } else if (getTimeDifference(y, lastTime) > WINDOW_SIZE) {
                                addMeeting(appConn, x, meetingStartTime, lastTime, month, date);
                                System.out.println("found a meeting and inserted successfully");
                                lastTime = y;
                                meetingStartTime = y;
                            } else {
                                lastTime = y;
                            }
                        }

                        // Flush the final meeting segment — the loop above only
                        // inserts when a gap exceeds WINDOW_SIZE, so the last
                        // meeting (from meetingStartTime to lastTime) would be
                        // silently dropped without this.
                        if (meetingStartTime != null && lastTime != null) {
                            addMeeting(appConn, x, meetingStartTime, lastTime, month, date);
                            System.out.println("found a meeting and inserted successfully");
                        }
                    }
                }
            }
        }
    }
}
