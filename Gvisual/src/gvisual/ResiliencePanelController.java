package gvisual;

import edu.uci.ics.jung.graph.Graph;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.Dimension;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

/**
 * Encapsulates the "Network Resilience" analysis panel previously embedded
 * in Main.java.  Owns its own UI components, SwingWorker lifecycle, and
 * CSV-export logic so that Main only needs to wire the panel into the
 * layout and supply a graph reference.
 */
public class ResiliencePanelController {

    private final JPanel panel;
    private final JButton analyzeButton;
    private final JButton exportButton;
    private final JLabel summaryLabel;
    private final JLabel detailsLabel;

    private final Supplier<Graph<String, Edge>> graphSupplier;
    private final JFrame parentFrame;
    private GraphResilienceAnalyzer lastAnalyzer;

    /**
     * @param graphSupplier supplies the current graph (may return null)
     * @param parentFrame   parent frame for dialogs
     */
    public ResiliencePanelController(Supplier<Graph<String, Edge>> graphSupplier,
                                     JFrame parentFrame) {
        this.graphSupplier = graphSupplier;
        this.parentFrame = parentFrame;

        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
                "Network Resilience",
                TitledBorder.LEFT, TitledBorder.TOP));

        summaryLabel = new JLabel("<html>Click 'Analyze' to simulate attack scenarios.</html>");
        summaryLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        detailsLabel = new JLabel("");
        detailsLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);

        analyzeButton = new JButton("Analyze");
        analyzeButton.addActionListener(e -> runAnalysis());

        exportButton = new JButton("Export CSV");
        exportButton.setEnabled(false);
        exportButton.addActionListener(e -> exportCSV());

        buttonPanel.add(analyzeButton);
        buttonPanel.add(Box.createHorizontalStrut(5));
        buttonPanel.add(exportButton);

        panel.add(summaryLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(buttonPanel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(detailsLabel);
    }

    /** Returns the Swing panel to embed in a layout. */
    public JPanel getPanel() {
        return panel;
    }

    /** Returns the last computed analyzer (may be null). */
    public GraphResilienceAnalyzer getLastAnalyzer() {
        return lastAnalyzer;
    }

    // ---- Analysis ----

    private void runAnalysis() {
        Graph<String, Edge> g = graphSupplier.get();
        if (g == null || g.getVertexCount() == 0) {
            summaryLabel.setText("<html>No graph loaded.</html>");
            return;
        }
        summaryLabel.setText("<html><i>Analyzing resilience...</i></html>");
        panel.repaint();

        SwingWorker<GraphResilienceAnalyzer, Void> worker =
                new SwingWorker<GraphResilienceAnalyzer, Void>() {
            @Override
            protected GraphResilienceAnalyzer doInBackground() {
                GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(g);
                analyzer.analyze();
                return analyzer;
            }

            @Override
            protected void done() {
                try {
                    GraphResilienceAnalyzer analyzer = get();
                    lastAnalyzer = analyzer;
                    displayResults(analyzer);
                    exportButton.setEnabled(true);
                } catch (Exception ex) {
                    summaryLabel.setText("<html><b>Error:</b> " + ex.getMessage() + "</html>");
                }
            }
        };
        worker.execute();
    }

    private void displayResults(GraphResilienceAnalyzer analyzer) {
        double rRandom = analyzer.computeRobustnessIndex(analyzer.getRandomAttackCurve());
        double rDegree = analyzer.computeRobustnessIndex(analyzer.getDegreeAttackCurve());
        double rBetweenness = analyzer.computeRobustnessIndex(analyzer.getBetweennessAttackCurve());

        StringBuilder summary = new StringBuilder("<html>");
        summary.append("<b>Robustness Index</b> (higher = more resilient):<br/>");
        summary.append(String.format("&nbsp;&nbsp;Random: <b>%.4f</b><br/>", rRandom));
        summary.append(String.format("&nbsp;&nbsp;Degree: <b>%.4f</b><br/>", rDegree));
        summary.append(String.format("&nbsp;&nbsp;Betweenness: <b>%.4f</b>", rBetweenness));
        summary.append("</html>");
        summaryLabel.setText(summary.toString());

        StringBuilder details = new StringBuilder("<html>");
        if (rRandom > rDegree * 1.5) {
            details.append("<b>Scale-free topology:</b> Vulnerable to<br/>targeted attacks on hubs.<br/>");
        } else if (rRandom < rDegree * 1.1) {
            details.append("<b>Homogeneous topology:</b> No dominant<br/>hub structure.<br/>");
        } else {
            details.append("<b>Moderate hub dependency.</b><br/>");
        }

        List<GraphResilienceAnalyzer.ResilienceStep> degreeCurve = analyzer.getDegreeAttackCurve();
        if (degreeCurve.size() > 1) {
            details.append("<br/><b>Most impactful removals:</b><br/>");
            int shown = Math.min(5, degreeCurve.size() - 1);
            for (int i = 1; i <= shown; i++) {
                GraphResilienceAnalyzer.ResilienceStep step = degreeCurve.get(i);
                if (step.getRemovedNode() != null) {
                    int lccDrop = degreeCurve.get(i - 1).getLargestComponentSize()
                            - step.getLargestComponentSize();
                    details.append(String.format("&nbsp;&nbsp;%s (LCC -%d)<br/>",
                            step.getRemovedNode(), lccDrop));
                }
            }
        }
        details.append("</html>");
        detailsLabel.setText(details.toString());
    }

    // ---- Export ----

    private void exportCSV() {
        if (lastAnalyzer == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("resilience_analysis.csv"));
        if (chooser.showSaveDialog(parentFrame) == JFileChooser.APPROVE_OPTION) {
            try {
                java.io.FileWriter writer = new java.io.FileWriter(chooser.getSelectedFile());
                writer.write(lastAnalyzer.exportCSV());
                writer.close();
                JOptionPane.showMessageDialog(parentFrame, "Resilience data exported.",
                        "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(parentFrame, "Export failed: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
