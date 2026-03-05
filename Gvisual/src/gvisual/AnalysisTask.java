package gvisual;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A lightweight wrapper that adds timeout, progress reporting, and
 * cancellation to any long-running computation.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * AnalysisTask<List<String>> task = new AnalysisTask<>(
 *     (cancelled, progress) -> {
 *         List<String> results = new ArrayList<>();
 *         for (int i = 0; i < n; i++) {
 *             if (cancelled.get()) return results; // partial
 *             progress.set(100 * i / n);
 *             results.add(computeStep(i));
 *         }
 *         return results;
 *     },
 *     30_000  // 30-second timeout
 * );
 *
 * AnalysisResult<List<String>> result = task.execute();
 * if (result.getStatus() == AnalysisResult.Status.TIMEOUT) {
 *     System.out.println("Partial: " + result.getPartialResult());
 * }
 * }</pre>
 *
 * @param <T> the result type
 */
public class AnalysisTask<T> {

    /**
     * A computation that accepts cancellation and progress handles.
     *
     * @param <T> result type
     */
    @FunctionalInterface
    public interface CancellableComputation<T> {
        /**
         * Run the computation.
         *
         * @param cancelled check this periodically; return partial result if true
         * @param progress  set 0-100 to report progress
         * @return the (possibly partial) result
         * @throws Exception on failure
         */
        T compute(AtomicBoolean cancelled, AtomicInteger progress) throws Exception;
    }

    private final CancellableComputation<T> computation;
    private final long timeoutMs;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicInteger progress = new AtomicInteger(0);
    private final AtomicReference<T> partialRef = new AtomicReference<>(null);

    /** Default timeout: 60 seconds. */
    private static final long DEFAULT_TIMEOUT_MS = 60_000;

    /**
     * Create a task with a specific timeout.
     *
     * @param computation the work to perform
     * @param timeoutMs   max wall-clock milliseconds (≤ 0 means no timeout)
     */
    public AnalysisTask(CancellableComputation<T> computation, long timeoutMs) {
        if (computation == null) throw new IllegalArgumentException("computation must not be null");
        this.computation = computation;
        this.timeoutMs = timeoutMs;
    }

    /** Create a task with the default 60-second timeout. */
    public AnalysisTask(CancellableComputation<T> computation) {
        this(computation, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Execute the computation with timeout and cancellation support.
     * This method blocks until completion, timeout, cancellation, or error.
     *
     * @return an {@link AnalysisResult} describing the outcome
     */
    public AnalysisResult<T> execute() {
        long start = System.currentTimeMillis();
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AnalysisTask-worker");
            t.setDaemon(true);
            return t;
        });

        Future<T> future = executor.submit(() -> {
            T result = computation.compute(cancelled, progress);
            partialRef.set(result);
            return result;
        });

        try {
            T result;
            if (timeoutMs > 0) {
                result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } else {
                result = future.get();
            }
            long elapsed = System.currentTimeMillis() - start;
            return AnalysisResult.completed(result, elapsed);

        } catch (TimeoutException e) {
            cancelled.set(true);
            long elapsed = System.currentTimeMillis() - start;
            // Give the thread time to check cancelled flag and set partial result
            // Don't interrupt immediately — let the cooperative cancellation work
            try { Thread.sleep(150); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            T partial = partialRef.get();
            future.cancel(true);
            return AnalysisResult.timeout(partial, elapsed);

        } catch (CancellationException e) {
            long elapsed = System.currentTimeMillis() - start;
            return AnalysisResult.cancelled(partialRef.get(), elapsed);

        } catch (ExecutionException e) {
            long elapsed = System.currentTimeMillis() - start;
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return AnalysisResult.error(cause.getMessage(), elapsed);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long elapsed = System.currentTimeMillis() - start;
            return AnalysisResult.cancelled(partialRef.get(), elapsed);

        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Request cancellation. The computation should check the cancelled
     * flag periodically and return its best partial result.
     */
    public void cancel() {
        cancelled.set(true);
    }

    /** Check if cancellation has been requested. */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /** Current progress (0-100), as reported by the computation. */
    public int getProgress() {
        return progress.get();
    }

    /** The timeout configured for this task, in milliseconds. */
    public long getTimeoutMs() {
        return timeoutMs;
    }
}
