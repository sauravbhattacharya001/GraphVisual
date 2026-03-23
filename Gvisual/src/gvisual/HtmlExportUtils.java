package gvisual;

/**
 * Shared HTML/JavaScript escaping utilities for file exporters.
 *
 * <p>Provides secure string escaping to prevent XSS (CWE-79) when
 * embedding user-controlled data (node IDs, labels, titles) in
 * generated HTML and JavaScript output.</p>
 *
 * @author zalenix
 */
public final class HtmlExportUtils {

    private HtmlExportUtils() { /* utility class */ }

    /**
     * Escapes a string for safe embedding inside a JavaScript double-quoted
     * string literal within an HTML {@code <script>} block.
     *
     * <p>Handles:</p>
     * <ul>
     *   <li>Backslash, double-quote, single-quote</li>
     *   <li>Forward-slash — prevents {@code </script>} tag injection (CWE-79)</li>
     *   <li>Newline and carriage return</li>
     *   <li>Backtick and {@code $} — prevents template literal injection</li>
     *   <li>Unicode line/paragraph separators (U+2028, U+2029) — valid in
     *       JSON but break JavaScript string literals in older engines</li>
     * </ul>
     *
     * @param s the string to escape (null-safe, returns empty string for null)
     * @return escaped string safe for JS string literal embedding
     */
    public static String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("'", "\\'")
                .replace("/", "\\/")         // prevent </script> breakout
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("`", "\\`")         // prevent template literal injection
                .replace("$", "\\$")         // prevent ${} interpolation
                .replace("\u2028", "\\u2028") // Unicode line separator
                .replace("\u2029", "\\u2029"); // Unicode paragraph separator
    }

    /**
     * Escapes a string for safe embedding in HTML content.
     *
     * @param s the string to escape (null-safe)
     * @return HTML-escaped string
     */
    public static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
