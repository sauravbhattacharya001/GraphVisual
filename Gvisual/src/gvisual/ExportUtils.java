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
