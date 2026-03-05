package gvisual;

/**
 * Wraps the outcome of an analysis task that may complete, time out,
 * be cancelled, or fail with an error.
 *
 * @param <T> the type of the analysis result
 */
public class AnalysisResult<T> {

    /** Possible completion statuses for an analysis task. */
    public enum Status {
        /** The analysis completed normally. */
        COMPLETED,
        /** The analysis exceeded its timeout. */
        TIMEOUT,
        /** The analysis was cancelled by the caller. */
        CANCELLED,
        /** The analysis threw an exception. */
        ERROR
    }

    private final T result;
    private final T partialResult;
    private final Status status;
    private final long elapsedMs;
    private final String error;

    private AnalysisResult(T result, T partialResult, Status status, long elapsedMs, String error) {
        this.result = result;
        this.partialResult = partialResult;
        this.status = status;
        this.elapsedMs = elapsedMs;
        this.error = error;
    }

    /** Create a completed result. */
    public static <T> AnalysisResult<T> completed(T result, long elapsedMs) {
        return new AnalysisResult<>(result, result, Status.COMPLETED, elapsedMs, null);
    }

    /** Create a timeout result with partial data. */
    public static <T> AnalysisResult<T> timeout(T partialResult, long elapsedMs) {
        return new AnalysisResult<>(null, partialResult, Status.TIMEOUT, elapsedMs, null);
    }

    /** Create a cancelled result with partial data. */
    public static <T> AnalysisResult<T> cancelled(T partialResult, long elapsedMs) {
        return new AnalysisResult<>(null, partialResult, Status.CANCELLED, elapsedMs, null);
    }

    /** Create an error result. */
    public static <T> AnalysisResult<T> error(String error, long elapsedMs) {
        return new AnalysisResult<>(null, null, Status.ERROR, elapsedMs, error);
    }

    /** The final result, or {@code null} if the task did not complete. */
    public T getResult() { return result; }

    /** Best partial result available (same as result if completed). */
    public T getPartialResult() { return partialResult; }

    /** How the task finished. */
    public Status getStatus() { return status; }

    /** Wall-clock time spent, in milliseconds. */
    public long getElapsedMs() { return elapsedMs; }

    /** Error message, or {@code null}. */
    public String getError() { return error; }

    /** True if the task finished normally. */
    public boolean isCompleted() { return status == Status.COMPLETED; }

    @Override
    public String toString() {
        return String.format("AnalysisResult{status=%s, elapsed=%dms, hasResult=%s, hasPartial=%s}",
                status, elapsedMs, result != null, partialResult != null);
    }
}
