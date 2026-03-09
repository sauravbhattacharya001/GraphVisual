package gvisual;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;

/**
 * Tests for {@link ExportUtils} path validation (CWE-22 prevention).
 */
public class ExportUtilsTest {

    @Test
    public void allowsFileInWorkingDirectory() throws Exception {
        File safe = new File("output.txt");
        ExportUtils.validateOutputPath(safe);
        // No exception — passes
    }

    @Test
    public void allowsFileInSubdirectory() throws Exception {
        File safe = new File("subdir/output.txt");
        ExportUtils.validateOutputPath(safe);
    }

    @Test
    public void allowsFileInTempDirectory() throws Exception {
        File tmpFile = File.createTempFile("export_test_", ".txt");
        tmpFile.deleteOnExit();
        ExportUtils.validateOutputPath(tmpFile);
    }

    @Test(expected = SecurityException.class)
    public void rejectsParentDirectoryTraversal() throws Exception {
        File traversal = new File("../../etc/passwd");
        ExportUtils.validateOutputPath(traversal);
    }

    @Test(expected = SecurityException.class)
    public void rejectsAbsolutePathOutsideAllowed() throws Exception {
        // Use a path that's definitely outside CWD and temp
        String root = System.getProperty("os.name").toLowerCase().contains("win")
            ? "C:\\Windows\\System32\\evil.txt"
            : "/etc/evil.txt";
        File outside = new File(root);
        ExportUtils.validateOutputPath(outside);
    }

    @Test
    public void handlesCurrentDirectoryRef() throws Exception {
        File dotRef = new File("./output.txt");
        ExportUtils.validateOutputPath(dotRef);
    }

    @Test(expected = SecurityException.class)
    public void rejectsDeepTraversal() throws Exception {
        File deep = new File("a/b/../../../../outside.txt");
        ExportUtils.validateOutputPath(deep);
    }
}
