package app;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the pure (non-database) helpers on {@link Util}:
 *
 * <ul>
 *   <li>{@link Util#validateTimeFormat(String, String)}</li>
 *   <li>{@link Util#getTimeStamp(String, String, String)}</li>
 *   <li>{@link Util#getTimeDifference(String, String)}</li>
 * </ul>
 *
 * <p>The database-bound methods ({@code getAppConnection},
 * {@code getAzialaConnection}) are intentionally NOT exercised here \u2014
 * they require a live PostgreSQL instance and credentials from the
 * environment, which is outside the scope of unit tests.</p>
 *
 * <p>The trace format used throughout is {@code "HH.MM:SS.mmm"}:
 * dot-separated hours/minutes and seconds/millis, with a single colon
 * between the two halves.</p>
 */
public class UtilTest {

    // ============================================================
    //  validateTimeFormat
    // ============================================================

    @Test
    public void validateTimeFormat_acceptsCanonicalFormat() {
        // Should not throw.
        Util.validateTimeFormat("10.30:15.000", "endTime");
        Util.validateTimeFormat("00.00:00.000", "endTime");
        Util.validateTimeFormat("23.59:59.999", "endTime");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateTimeFormat_rejectsNull() {
        Util.validateTimeFormat(null, "endTime");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateTimeFormat_rejectsEmpty() {
        Util.validateTimeFormat("", "endTime");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateTimeFormat_rejectsMissingColon() {
        Util.validateTimeFormat("10.30.15.000", "endTime");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateTimeFormat_rejectsMultipleColons() {
        // Two colons -> split() length != 2 -> rejected.
        Util.validateTimeFormat("10:30:15.000", "endTime");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateTimeFormat_rejectsMissingDotInLeftHalf() {
        // "10:15.000" -> left half "10" has no dot -> rejected
        Util.validateTimeFormat("10:15.000", "endTime");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateTimeFormat_rejectsMissingDotInRightHalf() {
        // "10.30:15" -> right half "15" has no dot -> rejected
        Util.validateTimeFormat("10.30:15", "endTime");
    }

    @Test
    public void validateTimeFormat_errorMessageIncludesLabel() {
        try {
            Util.validateTimeFormat(null, "myCustomLabel");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            assertTrue("error message should reference the label, got: " + ex.getMessage(),
                ex.getMessage().contains("myCustomLabel"));
        }
    }

    // ============================================================
    //  getTimeStamp
    // ============================================================

    @Test
    public void getTimeStamp_basicConversion() {
        // month="03", date="15", time="10.30:15.000"
        // -> "2011-03-15 10:30:15.000"
        String ts = Util.getTimeStamp("03", "15", "10.30:15.000");
        assertEquals("2011-03-15 10:30:15.000", ts);
    }

    @Test
    public void getTimeStamp_preservesMillisecondsExactly() {
        String ts = Util.getTimeStamp("12", "31", "23.59:59.987");
        assertEquals("2011-12-31 23:59:59.987", ts);
    }

    @Test
    public void getTimeStamp_zeroPaddedZerothSecond() {
        String ts = Util.getTimeStamp("01", "01", "00.00:00.000");
        assertEquals("2011-01-01 00:00:00.000", ts);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getTimeStamp_rejectsMalformedTime() {
        // No colon \u2192 validateTimeFormat throws \u2192 propagated out.
        Util.getTimeStamp("03", "15", "not-a-time");
    }

    @Test(expected = IllegalArgumentException.class)
    public void getTimeStamp_rejectsNullTime() {
        Util.getTimeStamp("03", "15", null);
    }

    // ============================================================
    //  getTimeDifference
    // ============================================================

    @Test
    public void getTimeDifference_sameTimeIsZero() {
        assertEquals(0.0f,
            Util.getTimeDifference("10.30:15.000", "10.30:15.000"),
            1e-4f);
    }

    @Test
    public void getTimeDifference_oneHourApart() {
        // 11:30:00 - 10:30:00 = 60 minutes
        assertEquals(60.0f,
            Util.getTimeDifference("11.30:00.000", "10.30:00.000"),
            1e-3f);
    }

    @Test
    public void getTimeDifference_oneMinuteApart() {
        // 10:31:00 - 10:30:00 = 1 minute
        assertEquals(1.0f,
            Util.getTimeDifference("10.31:00.000", "10.30:00.000"),
            1e-3f);
    }

    @Test
    public void getTimeDifference_thirtySecondsApart() {
        // 30 seconds = 0.5 minutes
        assertEquals(0.5f,
            Util.getTimeDifference("10.30:30.000", "10.30:00.000"),
            1e-3f);
    }

    @Test
    public void getTimeDifference_negativeWhenEndBeforeStart() {
        // 10:30 - 11:30 = -60 minutes
        assertEquals(-60.0f,
            Util.getTimeDifference("10.30:00.000", "11.30:00.000"),
            1e-3f);
    }

    @Test
    public void getTimeDifference_combinedHoursMinutesSeconds() {
        // 11:31:30 - 10:30:00 = 60 + 1 + 0.5 = 61.5 minutes
        assertEquals(61.5f,
            Util.getTimeDifference("11.31:30.000", "10.30:00.000"),
            1e-3f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getTimeDifference_rejectsMalformedEndTime() {
        Util.getTimeDifference("not-a-time", "10.30:00.000");
    }

    @Test(expected = IllegalArgumentException.class)
    public void getTimeDifference_rejectsMalformedStartTime() {
        Util.getTimeDifference("10.30:00.000", "still-not-a-time");
    }

    @Test(expected = IllegalArgumentException.class)
    public void getTimeDifference_rejectsNullEnd() {
        Util.getTimeDifference(null, "10.30:00.000");
    }

    @Test(expected = IllegalArgumentException.class)
    public void getTimeDifference_rejectsNullStart() {
        Util.getTimeDifference("10.30:00.000", null);
    }
}
