package gvisual;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for {@link AnalysisResult}, the wrapper returned by long-running
 * analysis tasks that may complete, time out, be cancelled, or fail.
 *
 * <p>These tests pin down the invariants relied on by callers:
 * <ul>
 *   <li>{@code completed} sets both {@code result} and {@code partialResult}.</li>
 *   <li>{@code timeout} / {@code cancelled} expose partial data but null final result.</li>
 *   <li>{@code error} carries a message and clears both result fields.</li>
 *   <li>{@link AnalysisResult#isCompleted()} is true <em>only</em> for COMPLETED.</li>
 *   <li>{@link AnalysisResult#toString()} reports status, elapsed time, and
 *       presence (not contents) of the result objects.</li>
 * </ul>
 */
public class AnalysisResultTest {

    // ---- completed ---------------------------------------------------------

    @Test
    public void completedExposesResultAsBothFinalAndPartial() {
        AnalysisResult<String> r = AnalysisResult.completed("ok", 42L);
        assertEquals("ok", r.getResult());
        assertEquals("ok", r.getPartialResult());
        assertSame("partial should reference the same instance as result",
                r.getResult(), r.getPartialResult());
        assertEquals(AnalysisResult.Status.COMPLETED, r.getStatus());
        assertEquals(42L, r.getElapsedMs());
        assertNull(r.getError());
        assertTrue(r.isCompleted());
    }

    @Test
    public void completedAllowsNullResult() {
        AnalysisResult<Object> r = AnalysisResult.completed(null, 0L);
        assertNull(r.getResult());
        assertNull(r.getPartialResult());
        assertTrue(r.isCompleted());
        assertEquals(AnalysisResult.Status.COMPLETED, r.getStatus());
    }

    // ---- timeout -----------------------------------------------------------

    @Test
    public void timeoutHasNullFinalButKeepsPartial() {
        Integer partial = 7;
        AnalysisResult<Integer> r = AnalysisResult.timeout(partial, 1000L);
        assertNull(r.getResult());
        assertEquals(partial, r.getPartialResult());
        assertEquals(AnalysisResult.Status.TIMEOUT, r.getStatus());
        assertEquals(1000L, r.getElapsedMs());
        assertNull(r.getError());
        assertFalse(r.isCompleted());
    }

    @Test
    public void timeoutWithNullPartialIsAllowed() {
        AnalysisResult<Integer> r = AnalysisResult.timeout(null, 250L);
        assertNull(r.getResult());
        assertNull(r.getPartialResult());
        assertEquals(AnalysisResult.Status.TIMEOUT, r.getStatus());
    }

    // ---- cancelled ---------------------------------------------------------

    @Test
    public void cancelledHasNullFinalButKeepsPartial() {
        AnalysisResult<String> r = AnalysisResult.cancelled("halfway", 33L);
        assertNull(r.getResult());
        assertEquals("halfway", r.getPartialResult());
        assertEquals(AnalysisResult.Status.CANCELLED, r.getStatus());
        assertEquals(33L, r.getElapsedMs());
        assertNull(r.getError());
        assertFalse(r.isCompleted());
    }

    // ---- error -------------------------------------------------------------

    @Test
    public void errorCarriesMessageAndClearsResults() {
        AnalysisResult<String> r = AnalysisResult.error("boom", 5L);
        assertNull(r.getResult());
        assertNull(r.getPartialResult());
        assertEquals(AnalysisResult.Status.ERROR, r.getStatus());
        assertEquals(5L, r.getElapsedMs());
        assertEquals("boom", r.getError());
        assertFalse(r.isCompleted());
    }

    @Test
    public void errorAllowsNullMessage() {
        AnalysisResult<String> r = AnalysisResult.error(null, 0L);
        assertEquals(AnalysisResult.Status.ERROR, r.getStatus());
        assertNull(r.getError());
        assertFalse(r.isCompleted());
    }

    // ---- isCompleted exhaustiveness ----------------------------------------

    @Test
    public void isCompletedTrueOnlyForCompletedStatus() {
        assertTrue (AnalysisResult.completed("x", 0).isCompleted());
        assertFalse(AnalysisResult.timeout  ("x", 0).isCompleted());
        assertFalse(AnalysisResult.cancelled("x", 0).isCompleted());
        assertFalse(AnalysisResult.error    ("e", 0).isCompleted());
    }

    // ---- Status enum -------------------------------------------------------

    @Test
    public void statusEnumHasExpectedConstants() {
        // Pin the public contract: changing these names is a breaking change.
        assertEquals(4, AnalysisResult.Status.values().length);
        assertNotNull(AnalysisResult.Status.valueOf("COMPLETED"));
        assertNotNull(AnalysisResult.Status.valueOf("TIMEOUT"));
        assertNotNull(AnalysisResult.Status.valueOf("CANCELLED"));
        assertNotNull(AnalysisResult.Status.valueOf("ERROR"));
    }

    // ---- toString ----------------------------------------------------------

    @Test
    public void toStringReportsStatusAndElapsed() {
        String s = AnalysisResult.completed("payload", 123L).toString();
        assertTrue(s.contains("COMPLETED"));
        assertTrue(s.contains("123"));
        assertTrue("toString should not leak result contents for privacy",
                !s.contains("payload"));
    }

    @Test
    public void toStringReportsHasResultFlags() {
        String completed = AnalysisResult.completed("x", 0L).toString();
        assertTrue(completed.contains("hasResult=true"));
        assertTrue(completed.contains("hasPartial=true"));

        String timedOut = AnalysisResult.timeout("partial", 0L).toString();
        assertTrue(timedOut.contains("hasResult=false"));
        assertTrue(timedOut.contains("hasPartial=true"));

        String errored = AnalysisResult.error("bad", 0L).toString();
        assertTrue(errored.contains("hasResult=false"));
        assertTrue(errored.contains("hasPartial=false"));
    }

    // ---- elapsed-ms boundary cases -----------------------------------------

    @Test
    public void elapsedMsAcceptsZeroAndLargeValues() {
        assertEquals(0L,            AnalysisResult.completed("x", 0L).getElapsedMs());
        assertEquals(Long.MAX_VALUE,AnalysisResult.completed("x", Long.MAX_VALUE).getElapsedMs());
    }
}
