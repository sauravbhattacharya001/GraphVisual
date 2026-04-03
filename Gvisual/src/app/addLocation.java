package app;

/**
 * Resolves meeting locations by cross-referencing WiFi access point data.
 *
 * @deprecated Use {@link LocationResolver} instead. This class is a thin
 *             delegate kept only for backward compatibility with scripts
 *             that reference {@code addLocation} by name.
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
     * Entry point — delegates entirely to {@link LocationResolver#main(String[])}.
     *
     * <p>The original implementation duplicated the AP classification logic
     * (hardcoded switch statement) and fallback query code that
     * {@code LocationResolver} already encapsulates via
     * {@link LocationResolver#classifyAP(int)} and
     * {@link LocationResolver#main(String[])}. This delegation eliminates
     * ~100 lines of duplicated SQL, fallback logic, and the fragile
     * switch/case AP mapping that would drift out of sync with
     * {@code LocationResolver}'s canonical {@code AP_LOCATION_MAP}.</p>
     */
    public static void main(String[] argv) throws Exception {
        LocationResolver.main(argv);
    }
}
