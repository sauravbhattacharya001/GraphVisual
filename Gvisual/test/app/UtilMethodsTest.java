package app;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for utility / helper methods in the app package.
 *
 * Tests {@link findMeetings#getTimeDifference(String, String)} and
 * {@link addLocation#getTimeStamp(String, String, String)}.
 * These are pure functions with no database dependency — ideal for unit testing.
 */
public class UtilMethodsTest {

    // ============================================================
    //  findMeetings.getTimeDifference  (time format: "HH.MM:SS.mmm")
    // ============================================================

    @Test
    public void testTimeDifference_sameTime() {
        float diff = findMeetings.getTimeDifference("10.30:00.000", "10.30:00.000");
        assertEquals(0.0f, diff, 0.01f);
    }

    @Test
    public void testTimeDifference_oneHourApart() {
        // 11:30:00 - 10:30:00 = 60 minutes
        float diff = findMeetings.getTimeDifference("11.30:00.000", "10.30:00.000");
        assertEquals(60.0f, diff, 0.01f);
    }

    @Test
    public void testTimeDifference_minutesOnly() {
        // 10:45:00 - 10:30:00 = 15 minutes
        float diff = findMeetings.getTimeDifference("10.45:00.000", "10.30:00.000");
        assertEquals(15.0f, diff, 0.01f);
    }

    @Test
    public void testTimeDifference_withSeconds() {
        // 10:30:30 - 10:30:00 → seconds contribute fractionally
        // seconds part: (30 - 0) / 60 = 0.5 minutes
        float diff = findMeetings.getTimeDifference("10.30:30.000", "10.30:00.000");
        assertEquals(0.5f, diff, 0.01f);
    }

    @Test
    public void testTimeDifference_hoursAndMinutes() {
        // 14:15:00 - 12:30:00 = 1h45m = 105 minutes
        float diff = findMeetings.getTimeDifference("14.15:00.000", "12.30:00.000");
        assertEquals(105.0f, diff, 0.01f);
    }

    @Test
    public void testTimeDifference_negativeWhenEndBeforeStart() {
        // If "end" is actually before "start", result is negative
        float diff = findMeetings.getTimeDifference("09.00:00.000", "10.00:00.000");
        assertTrue("Expected negative difference", diff < 0);
    }

    // ============================================================
    //  addLocation.getTimeStamp  (builds "YYYY-MM-DD HH:MM:SS.ms")
    // ============================================================

    @Test
    public void testGetTimeStamp_basic() {
        String ts = addLocation.getTimeStamp("03", "15", "14.30:45.500");
        assertEquals("2011-03-15 14:30:45.500", ts);
    }

    @Test
    public void testGetTimeStamp_midnight() {
        String ts = addLocation.getTimeStamp("04", "01", "00.00:00.000");
        assertEquals("2011-04-01 00:00:00.000", ts);
    }

    @Test
    public void testGetTimeStamp_endOfDay() {
        String ts = addLocation.getTimeStamp("05", "31", "23.59:59.999");
        assertEquals("2011-05-31 23:59:59.999", ts);
    }

    @Test
    public void testGetTimeStamp_sameAsFindMeetingsVersion() {
        // findMeetings has its own getTimeStamp — verify they produce the same output
        String ts1 = addLocation.getTimeStamp("03", "10", "08.15:30.250");
        String ts2 = findMeetings.getTimeStamp("03", "10", "08.15:30.250");
        assertEquals("Both getTimeStamp implementations should agree", ts1, ts2);
    }

    // ============================================================
    //  findMeetings.addMeeting ordering
    // ============================================================

    @Test
    public void testGetTimeDifference_windowBoundary() {
        // Exactly 5 minutes — the WINDOW_SIZE boundary
        float diff = findMeetings.getTimeDifference("10.35:00.000", "10.30:00.000");
        assertEquals(5.0f, diff, 0.01f);
    }

    @Test
    public void testGetTimeDifference_justOverWindow() {
        // 5 minutes + 1 second → should exceed the 5.0 window
        float diff = findMeetings.getTimeDifference("10.35:01.000", "10.30:00.000");
        assertTrue("Should exceed 5.0 minute window",
                diff > 5.0f);
    }
}
