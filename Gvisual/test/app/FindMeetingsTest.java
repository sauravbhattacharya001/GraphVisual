package app;

import static org.junit.Assert.*;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.Test;

/**
 * Tests for {@link findMeetings#segmentMeetings} — the core gap-based
 * meeting detection algorithm extracted from main() for testability.
 */
public class FindMeetingsTest {

    // Helper: trace-format time "HH.MM:SS.mmm"
    // e.g. "10.30:15.000" = 10h 30m 15.000s

    @Test
    public void emptyInputReturnsNoSegments() {
        TreeSet<String> times = new TreeSet<>();
        List<findMeetings.MeetingSegment> result =
                findMeetings.segmentMeetings(times, 5.0);
        assertTrue(result.isEmpty());
    }

    @Test
    public void nullInputReturnsNoSegments() {
        List<findMeetings.MeetingSegment> result =
                findMeetings.segmentMeetings(null, 5.0);
        assertTrue(result.isEmpty());
    }

    @Test
    public void singleObservationProducesSingleSegment() {
        TreeSet<String> times = new TreeSet<>();
        times.add("10.30:15.000");
        List<findMeetings.MeetingSegment> segs =
                findMeetings.segmentMeetings(times, 5.0);
        assertEquals(1, segs.size());
        assertEquals("10.30:15.000", segs.get(0).getStartTime());
        assertEquals("10.30:15.000", segs.get(0).getEndTime());
    }

    @Test
    public void continuousMeetingStaysOneSegment() {
        // 3 observations each 2 minutes apart (within 5-min window)
        TreeSet<String> times = new TreeSet<>();
        times.add("10.00:00.000"); // 10:00
        times.add("10.02:00.000"); // 10:02
        times.add("10.04:00.000"); // 10:04
        List<findMeetings.MeetingSegment> segs =
                findMeetings.segmentMeetings(times, 5.0);
        assertEquals(1, segs.size());
        assertEquals("10.00:00.000", segs.get(0).getStartTime());
        assertEquals("10.04:00.000", segs.get(0).getEndTime());
    }

    @Test
    public void gapSplitsIntoTwoSegments() {
        TreeSet<String> times = new TreeSet<>();
        times.add("10.00:00.000"); // meeting 1
        times.add("10.02:00.000");
        // 10-minute gap (> 5-min window)
        times.add("10.12:00.000"); // meeting 2
        times.add("10.14:00.000");

        List<findMeetings.MeetingSegment> segs =
                findMeetings.segmentMeetings(times, 5.0);
        assertEquals(2, segs.size());
        assertEquals("10.00:00.000", segs.get(0).getStartTime());
        assertEquals("10.02:00.000", segs.get(0).getEndTime());
        assertEquals("10.12:00.000", segs.get(1).getStartTime());
        assertEquals("10.14:00.000", segs.get(1).getEndTime());
    }

    @Test
    public void exactWindowBoundaryDoesNotSplit() {
        // Gap of exactly 5 minutes should NOT split (> not >=)
        TreeSet<String> times = new TreeSet<>();
        times.add("10.00:00.000");
        times.add("10.05:00.000"); // exactly 5 min later
        List<findMeetings.MeetingSegment> segs =
                findMeetings.segmentMeetings(times, 5.0);
        assertEquals(1, segs.size());
    }

    @Test
    public void multipleGapsProduceMultipleSegments() {
        TreeSet<String> times = new TreeSet<>();
        times.add("08.00:00.000");
        // 10-min gap
        times.add("08.10:00.000");
        times.add("08.12:00.000");
        // 20-min gap
        times.add("08.32:00.000");

        List<findMeetings.MeetingSegment> segs =
                findMeetings.segmentMeetings(times, 5.0);
        assertEquals(3, segs.size());
    }

    @Test
    public void customWindowSizeRespected() {
        TreeSet<String> times = new TreeSet<>();
        times.add("10.00:00.000");
        times.add("10.08:00.000"); // 8 min gap

        // With 5-min window: splits into 2
        assertEquals(2, findMeetings.segmentMeetings(times, 5.0).size());
        // With 10-min window: stays as 1
        assertEquals(1, findMeetings.segmentMeetings(times, 10.0).size());
    }

    @Test
    public void resultIsImmutable() {
        TreeSet<String> times = new TreeSet<>();
        times.add("10.00:00.000");
        List<findMeetings.MeetingSegment> segs =
                findMeetings.segmentMeetings(times, 5.0);
        try {
            segs.add(new findMeetings.MeetingSegment("x", "y"));
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void meetingSegmentEquality() {
        findMeetings.MeetingSegment a =
                new findMeetings.MeetingSegment("10.00:00.000", "10.05:00.000");
        findMeetings.MeetingSegment b =
                new findMeetings.MeetingSegment("10.00:00.000", "10.05:00.000");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
