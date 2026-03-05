package gvisual;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests for {@link AnalysisTask} and {@link AnalysisResult}.
 */
public class AnalysisTaskTest {

    @Test
    public void completedTaskReturnsResult() {
        AnalysisTask<Integer> task = new AnalysisTask<>(
                (cancelled, progress) -> {
                    progress.set(50);
                    int sum = 0;
                    for (int i = 1; i <= 100; i++) {
                        if (cancelled.get()) return sum;
                        sum += i;
                    }
                    progress.set(100);
                    return sum;
                },
                5000
        );

        AnalysisResult<Integer> result = task.execute();

        assertEquals(AnalysisResult.Status.COMPLETED, result.getStatus());
        assertTrue(result.isCompleted());
        assertEquals(Integer.valueOf(5050), result.getResult());
        assertEquals(Integer.valueOf(5050), result.getPartialResult());
        assertNull(result.getError());
        assertTrue(result.getElapsedMs() < 5000);
    }

    @Test
    public void timeoutReturnsPartialResult() {
        AnalysisTask<List<Integer>> task = new AnalysisTask<>(
                (cancelled, progress) -> {
                    List<Integer> results = new ArrayList<>();
                    for (int i = 0; i < 1_000_000; i++) {
                        if (cancelled.get()) return results;
                        results.add(i);
                        if (i % 100 == 0) {
                            // Slow down to ensure timeout
                            Thread.sleep(1);
                        }
                    }
                    return results;
                },
                200 // very short timeout
        );

        AnalysisResult<List<Integer>> result = task.execute();

        assertEquals(AnalysisResult.Status.TIMEOUT, result.getStatus());
        assertFalse(result.isCompleted());
        assertNull(result.getResult());
        // Partial result should have some items
        assertNotNull(result.getPartialResult());
        assertTrue(result.getPartialResult().size() > 0);
        assertTrue(result.getElapsedMs() >= 150); // at least close to timeout
    }

    @Test
    public void cancellationStopsComputation() throws Exception {
        AnalysisTask<String> task = new AnalysisTask<>(
                (cancelled, progress) -> {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < 10_000; i++) {
                        if (cancelled.get()) return sb.toString();
                        sb.append(i).append(",");
                        Thread.sleep(10);
                    }
                    return sb.toString();
                },
                30_000
        );

        // Cancel after 100ms from another thread
        Thread canceller = new Thread(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            task.cancel();
        });
        canceller.start();

        AnalysisResult<String> result = task.execute();
        canceller.join();

        // Should have finished due to cancellation check in computation
        assertTrue(result.isCompleted() || result.getStatus() == AnalysisResult.Status.CANCELLED);
        assertTrue(task.isCancelled());
    }

    @Test
    public void errorResultOnException() {
        AnalysisTask<String> task = new AnalysisTask<>(
                (cancelled, progress) -> {
                    throw new RuntimeException("simulated failure");
                },
                5000
        );

        AnalysisResult<String> result = task.execute();

        assertEquals(AnalysisResult.Status.ERROR, result.getStatus());
        assertFalse(result.isCompleted());
        assertNull(result.getResult());
        assertNotNull(result.getError());
        assertTrue(result.getError().contains("simulated failure"));
    }

    @Test
    public void progressIsTracked() throws Exception {
        AnalysisTask<Integer> task = new AnalysisTask<>(
                (cancelled, progress) -> {
                    for (int i = 0; i <= 100; i++) {
                        if (cancelled.get()) return i;
                        progress.set(i);
                        Thread.sleep(5);
                    }
                    return 100;
                },
                10_000
        );

        // Check progress from another thread
        Thread monitor = new Thread(() -> {
            try {
                Thread.sleep(100);
                assertTrue(task.getProgress() > 0);
            } catch (InterruptedException ignored) {}
        });
        monitor.start();

        AnalysisResult<Integer> result = task.execute();
        monitor.join();

        assertEquals(AnalysisResult.Status.COMPLETED, result.getStatus());
        assertEquals(Integer.valueOf(100), result.getResult());
    }

    @Test
    public void noTimeoutWhenZeroOrNegative() {
        AnalysisTask<String> task = new AnalysisTask<>(
                (cancelled, progress) -> "done",
                0 // no timeout
        );

        AnalysisResult<String> result = task.execute();
        assertEquals(AnalysisResult.Status.COMPLETED, result.getStatus());
        assertEquals("done", result.getResult());
    }

    @Test
    public void defaultTimeoutConstructor() {
        AnalysisTask<String> task = new AnalysisTask<>(
                (cancelled, progress) -> "hello"
        );

        assertEquals(60_000, task.getTimeoutMs());
        AnalysisResult<String> result = task.execute();
        assertEquals("hello", result.getResult());
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullComputationThrows() {
        new AnalysisTask<String>(null, 1000);
    }

    @Test
    public void analysisResultToStringContainsStatus() {
        AnalysisResult<String> result = AnalysisResult.completed("test", 42);
        String str = result.toString();
        assertTrue(str.contains("COMPLETED"));
        assertTrue(str.contains("42"));
    }
}
