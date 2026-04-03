package app;

/**
 * Legacy entry point for location resolution.
 *
 * <p>This class has been superseded by {@link LocationResolver}, which
 * extracts the AP-to-location mapping into a configurable {@code Map},
 * deduplicates the single-IMEI fallback query logic, and follows Java
 * naming conventions. This wrapper exists solely for backward
 * compatibility with scripts that reference {@code addLocation.main}.</p>
 *
 * @deprecated Use {@link LocationResolver} instead.
 * @author user
 */
@Deprecated
public class addLocation {

    /**
     * Delegates to {@link Util#getTimeStamp(String, String, String)}.
     *
     * @deprecated Use {@link Util#getTimeStamp(String, String, String)} directly.
     */
    @Deprecated
    public static String getTimeStamp(String month, String date, String time) {
        return Util.getTimeStamp(month, date, time);
    }

    /**
     * Delegates to {@link LocationResolver#main(String[])}.
     *
     * @param argv command-line arguments (forwarded unchanged)
     * @throws Exception if location resolution fails
     * @deprecated Use {@link LocationResolver#main(String[])} directly.
     */
    @Deprecated
    public static void main(String[] argv) throws Exception {
        LocationResolver.main(argv);
    }
}
