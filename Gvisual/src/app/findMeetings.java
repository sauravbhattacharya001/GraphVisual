package app;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

    // ── Meeting segment representation ──────────────────────────

    /**
     * An immutable record representing a detected meeting segment
     * between two devices — a contiguous period of co-location
     * observations with no gap exceeding the window size.
     */
    public static final class MeetingSegment {
        private final String startTime;
        private final String endTime;

        public MeetingSegment(String startTime, String endTime) {
            if (startTime == null || endTime == null) {
                throw new IllegalArgumentException("start/end time must not be null");
            }
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public String getStartTime() { return startTime; }
        public String getEndTime()   { return endTime; }

        @Override
        public String toString() {
            return "MeetingSegment[" + startTime + " -> " + endTime + "]";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MeetingSegment)) return false;
            MeetingSegment that = (MeetingSegment) o;
            return startTime.equals(that.startTime) && endTime.equals(that.endTime);
        }

        @Override
        public int hashCode() {
            return 31 * startTime.hashCode() + endTime.hashCode();
        }
    }

    // ── Core algorithm (extracted from main for testability) ────

    /**
     * Segments a sorted set of observation timestamps into meeting intervals.
     *
     * <p>Scans the times in order; whenever the gap between consecutive
     * observations exceeds {@code windowMinutes}, the current meeting ends
     * and a new one begins.  Returns an unmodifiable list of segments.</p>
     *
     * <p>This was previously inlined inside {@code main()}, making it
     * impossible to unit-test the segmentation logic independently of
     * the database.  Extracting it enables fast, deterministic tests.</p>
     *
     * @param sortedTimes   chronologically sorted observation times
     *                      in trace format ("HH.MM:SS.mmm")
     * @param windowMinutes maximum gap (in minutes) before a meeting
     *                      is considered to have ended
     * @return immutable list of meeting segments (may be empty)
     */
    public static List<MeetingSegment> segmentMeetings(
            SortedSet<String> sortedTimes, double windowMinutes) {
        if (sortedTimes == null || sortedTimes.isEmpty()) {
            return Collections.emptyList();
        }

        List<MeetingSegment> segments = new ArrayList<MeetingSegment>();
        String meetingStart = null;
        String lastTime = null;

        for (String time : sortedTimes) {
            if (lastTime == null) {
                meetingStart = time;
                lastTime = time;
            } else if (Util.getTimeDifference(time, lastTime) > windowMinutes) {
                segments.add(new MeetingSegment(meetingStart, lastTime));
                meetingStart = time;
                lastTime = time;
            } else {
                lastTime = time;
            }
        }
        // Flush the final segment
        if (meetingStart != null && lastTime != null) {
            segments.add(new MeetingSegment(meetingStart, lastTime));
        }

        return Collections.unmodifiableList(segments);
    }

    /**
     * Returns a canonical device pair key with the lexicographically smaller
     * IMEI first, separated by '#'. This ensures consistent ordering
     * regardless of which device is sender vs receiver.
     *
     * @param imei1 first IMEI
     * @param imei2 second IMEI
     * @return canonical "smaller#larger" pair key
     */
    static String canonicalPair(String imei1, String imei2) {
        return (imei1.compareTo(imei2) > 0)
                ? imei2 + "#" + imei1
                : imei1 + "#" + imei2;
    }

    /**
     * @deprecated Use {@link Util#getTimeDifference(String, String)}
     */
    @Deprecated
    public static float getTimeDifference(String endTime, String startTime) {
        return Util.getTimeDifference(endTime, startTime);
    }

    /**
     * @deprecated Use {@link Util#getTimeStamp(String, String, String)}
     */
    @Deprecated
    public static String getTimeStamp(String month, String date, String time) {
        return Util.getTimeStamp(month, date, time);
    }

    /** SQL for meeting inserts — shared by all addMeeting overloads. */
    private static final String INSERT_MEETING_SQL =
            "INSERT INTO meeting (imei1, imei2, starttime, endtime, location, month, date, duration) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    public static void addMeeting(Connection conn, String devicePair, String startTime, String endTime, String month, String date) throws Exception {
        addMeeting(conn, devicePair, startTime, endTime, month, date, "unknown");
    }

    public static void addMeeting(Connection conn, String devicePair, String startTime, String endTime, String month, String date, String locationType) throws Exception {
        try (PreparedStatement pstmt = conn.prepareStatement(INSERT_MEETING_SQL)) {
            addMeetingBatch(pstmt, devicePair, startTime, endTime, month, date, locationType);
            pstmt.executeBatch();
        }
    }

    /**
     * Adds a meeting to the batch without executing. Call
     * {@code pstmt.executeBatch()} after accumulating entries.
     *
     * <p>This avoids creating a new PreparedStatement per meeting —
     * the caller can reuse a single statement across thousands of
     * inserts, reducing connection overhead dramatically.</p>
     *
     * @param pstmt       a PreparedStatement for {@link #INSERT_MEETING_SQL}
     * @param devicePair  canonical "imei1#imei2" pair key
     * @param startTime   meeting start time
     * @param endTime     meeting end time
     * @param month       month filter
     * @param date        date filter
     * @param locationType location type (defaults to "unknown" if null/empty)
     */
    public static void addMeetingBatch(PreparedStatement pstmt, String devicePair,
                                        String startTime, String endTime,
                                        String month, String date,
                                        String locationType) throws Exception {
        String[] imeiArr = devicePair.split("#");
        String canonical = canonicalPair(imeiArr[0], imeiArr[1]);
        String[] ordered = canonical.split("#");
        String imei1 = ordered[0];
        String imei2 = ordered[1];
        String apType = (locationType != null && !locationType.isEmpty()) ? locationType : "unknown";

        pstmt.setString(1, imei1);
        pstmt.setString(2, imei2);
        pstmt.setString(3, startTime);
        pstmt.setString(4, endTime);
        pstmt.setString(5, apType);
        pstmt.setString(6, month);
        pstmt.setString(7, date);
        pstmt.setInt(8, (int) getTimeDifference(endTime, startTime));
        pstmt.addBatch();
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
                     selectSql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
             // Reuse a single PreparedStatement for all inserts instead of
             // creating one per meeting — reduces connection overhead from
             // O(meetings) round-trips to O(days) batch executions.
             PreparedStatement insertStmt = appConn.prepareStatement(INSERT_MEETING_SQL)) {

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
                    rs.beforeFirst();
                    while (rs.next()) {
                        if (rs.getRow() % 1000 == 0) {
                            System.out.println("added " + rs.getRow() + " number of entries to map");
                        }

                        String devicePair = canonicalPair(rs.getString(1), rs.getString(2));

                        String curTime = rs.getString(3);
                        if (curTime.length() == 11) {
                            curTime = "0" + curTime;
                        }

                        // computeIfAbsent avoids the redundant remove-then-put
                        // pattern — the SortedSet is a reference type, so adding
                        // to the existing set is sufficient.
                        deviceInteraction
                            .computeIfAbsent(devicePair, k -> new TreeSet<String>())
                            .add(curTime);
                    }

                    System.out.println("Hash Map created");

                    System.out.println("Total different pairs detected = " + deviceInteraction.size());

                    int batchCount = 0;
                    for (String x : deviceInteraction.keySet()) {
                        List<MeetingSegment> segments =
                                segmentMeetings(deviceInteraction.get(x), WINDOW_SIZE);
                        for (MeetingSegment seg : segments) {
                            addMeetingBatch(insertStmt, x,
                                    seg.getStartTime(), seg.getEndTime(),
                                    month, date, "unknown");
                            batchCount++;
                        }
                    }

                    // Execute all meetings for this day in one batch
                    if (batchCount > 0) {
                        insertStmt.executeBatch();
                        System.out.println("inserted " + batchCount + " meetings for " + month + "-" + date);
                    }
                }
            }
        }
    }
}
