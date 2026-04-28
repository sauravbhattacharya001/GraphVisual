package gvisual;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralizes export button creation for the toolbar.
 *
 * <p>Each format (GraphML, DOT, GEXF, CSV Edge list, node metrics) previously
 * had its own inline ActionListener with an identical
 * create-exporter → save-dialog → try/catch → success/error-dialog flow.
 * This class extracts that pattern into {@link #addExportButton} so adding
 * a new export format is a single method call.</p>
 *
 * @author zalenix
 */
public final class ExportActions {

    private static final Logger LOGGER = Logger.getLogger(ExportActions.class.getName());

    private ExportActions() { /* utility class */ }

    /**
     * Functional interface for an export operation that may throw IOException.
     */
    @FunctionalInterface
    public interface ExportOperation {
        /**
         * Run the export.
         *
         * @param outFile destination file chosen by the user
         * @return a human-readable success summary (shown in a dialog)
         * @throws IOException if the export fails
         */
        String export(File outFile) throws IOException;
    }

    /**
     * Creates a toolbar-sized export button and adds it to the given panel.
     *
     * @param panel           target panel to add the button to
     * @param parentComponent parent for dialogs (typically the main JFrame)
     * @param buttonHtml      HTML label for the button
     * @param dialogTitle     title for the save-file dialog
     * @param defaultNameFn   supplier for the default file name (called lazily
     *                        so timestamps are current)
     * @param extensions      accepted file extensions (e.g. ".graphml")
     * @param operation       the actual export logic
     */
    public static void addExportButton(JPanel panel,
                                       Component parentComponent,
                                       String buttonHtml,
                                       String dialogTitle,
                                       Supplier<String> defaultNameFn,
                                       String[] extensions,
                                       ExportOperation operation) {
        JButton button = new JButton(buttonHtml);
        button.setPreferredSize(new Dimension(140, 100));
        button.addActionListener(e -> {
            File outFile = showExportSaveDialog(parentComponent, dialogTitle,
                    defaultNameFn.get(), extensions);
            if (outFile == null) return;

            try {
                String summary = operation.export(outFile);
                JOptionPane.showMessageDialog(parentComponent, summary,
                        "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Export failed", ex);
                JOptionPane.showMessageDialog(parentComponent,
                        "Export failed: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        panel.add(button);
    }

    /**
     * Shows a save dialog with overwrite confirmation and automatic
     * extension appending.  Extracted from Main to be reusable.
     */
    public static File showExportSaveDialog(Component parent,
                                            String dialogTitle,
                                            String defaultName,
                                            String... extensions) {
        JFileChooser fileChooser = new JFileChooser(System.getProperty("user.dir"));
        fileChooser.setDialogTitle(dialogTitle);
        fileChooser.setSelectedFile(new File(defaultName));
        int returnVal = fileChooser.showSaveDialog(parent);
        if (returnVal != JFileChooser.APPROVE_OPTION) return null;

        File outFile = fileChooser.getSelectedFile();
        if (extensions.length > 0) {
            boolean hasExt = false;
            for (String ext : extensions) {
                if (outFile.getName().endsWith(ext)) { hasExt = true; break; }
            }
            if (!hasExt) {
                outFile = new File(outFile.getAbsolutePath() + extensions[0]);
            }
        }

        if (outFile.exists()) {
            int confirm = JOptionPane.showConfirmDialog(parent,
                    "File already exists. Overwrite?",
                    "Confirm Overwrite", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return null;
        }
        return outFile;
    }
}
