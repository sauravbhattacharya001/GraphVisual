package gvisual;

import java.io.File;
import java.io.IOException;

/**
 * Shared security utilities for file exporters.
 *
 * <p>Provides output-path validation to prevent directory-traversal
 * attacks (CWE-22) when exporting graphs, reports, or HTML files.
 *
 * @author zalenix
 */
public final class ExportUtils {

    private ExportUtils() { /* utility class */ }

    /**
     * Escapes XML special characters and strips illegal XML 1.0 control characters.
     *
     * <p>XML 1.0 only allows: #x9 (tab), #xA (newline), #xD (carriage return),
     * and characters &gt;= #x20. All other control characters are stripped to
     * produce valid XML output.</p>
     *
     * <p>This method is shared by all XML-based exporters (GraphML, GEXF, etc.)
     * to ensure consistent and safe XML output.</p>
     *
     * @param text the input text (may be null)
     * @return XML-safe text, or empty string if input is null
     */
    public static String escapeXml(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':  sb.append("&amp;");  break;
                case '<':  sb.append("&lt;");   break;
                case '>':  sb.append("&gt;");   break;
                case '"':  sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                case '\t': // fall through — legal XML whitespace
                case '\n': // fall through
                case '\r': sb.append(c);        break;
                default:
                    if (c >= 0x20) {
                        sb.append(c);
                    }
                    // else: silently drop illegal control character
                    break;
            }
        }
        return sb.toString();
    }

    /**
     * JSON-escapes a string value, wrapping it in double quotes.
     *
     * <p>Escapes backslashes, double quotes, newlines, carriage returns,
     * tabs, and other control characters (as {@code \uXXXX}).</p>
     *
     * @param value the input string (may be null)
     * @return JSON-quoted string, or {@code "null"} if input is null
     */
    public static String jsonString(String value) {
        if (value == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    /**
     * Quotes a string for Graphviz DOT format, escaping special characters.
     *
     * <p>Escapes backslashes, double-quotes, newlines, carriage returns,
     * and tabs to prevent DOT syntax injection (CWE-74).</p>
     *
     * @param s the input string (may be null)
     * @return DOT-quoted string
     */
    public static String quoteDot(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                       .replace("\r", "\\r")
                       .replace("\t", "\\t") + "\"";
    }

    /**
     * Validates that an output file path is safe to write to.
     *
     * <p>The resolved (canonical) path must reside within the current
     * working directory or within the system temporary directory.
     * Paths that escape these directories via {@code ..} or symlinks
     * are rejected with a {@link SecurityException}.
     *
     * @param file the proposed output file
     * @throws SecurityException if the path escapes allowed directories
     * @throws IOException if canonicalization fails
     */
    public static void validateOutputPath(File file) throws IOException {
        File canonical = file.getCanonicalFile();
        File cwd = new File(".").getCanonicalFile();
        File tmpDir = new File(System.getProperty("java.io.tmpdir")).getCanonicalFile();

        if (canonical.toPath().startsWith(cwd.toPath())) {
            return; // within working directory — allowed
        }
        if (canonical.toPath().startsWith(tmpDir.toPath())) {
            return; // within temp directory — allowed
        }

        throw new SecurityException(
            "Export path must be within the working directory or temp directory. "
            + "Resolved path: " + canonical.getAbsolutePath()
            + " (CWD: " + cwd.getAbsolutePath()
            + ", TMP: " + tmpDir.getAbsolutePath() + ")");
    }
}
