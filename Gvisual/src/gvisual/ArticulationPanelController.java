package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.visualization.VisualizationViewer;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.util.*;
import java.util.function.Supplier;

/**
 * Encapsulates the "Articulation Points & Bridges" analysis panel previously
 * embedded in Main.java.  Owns UI, analysis lifecycle, and overlay state.
 */
public class ArticulationPanelController {

    private final JPanel panel;
    private final JLabel resilienceLabel;
    private final JLabel summaryLabel;
    private final JLabel detailsLabel;
    private final JButton analyzeButton;
    private final JButton clearButton;

    private final Supplier<Graph<String, Edge>> graphSupplier;
    private final Runnable onOverlayChanged;

    private boolean overlayActive;
    private final Set<String> articulationPoints = new HashSet<>();
    private final Set<Edge> bridgeEdges = new HashSet<>();

    /**
     * @param graphSupplier     supplies the current graph
     * @param onOverlayChanged  callback to refresh renderers/visualization after overlay changes
     */
    public ArticulationPanelController(Supplier<Graph<String, Edge>> graphSupplier,
                                        Runnable onOverlayChanged) {
        this.graphSupplier = graphSupplier;
        this.onOverlayChanged = onOverlayChanged;

        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
                "Articulation Points & Bridges",
                TitledBorder.LEFT, TitledBorder.TOP));

        resilienceLabel = new JLabel("Resilience: \u2014");
        resilienceLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        summaryLabel = new JLabel("<html>Click 'Analyze' to find critical nodes and edges.</html>");
        summaryLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        detailsLabel = new JLabel("");
        detailsLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        analyzeButton = new JButton("Analyze");
        analyzeButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        analyzeButton.addActionListener(e -> runAnalysis());

        clearButton = new JButton("Clear");
        clearButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        clearButton.addActionListener(e -> clear());

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        buttonPanel.add(analyzeButton);
        buttonPanel.add(Box.createHorizontalStrut(5));
        buttonPanel.add(clearButton);

        panel.add(resilienceLabel);
        panel.add(summaryLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(buttonPanel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(detailsLabel);
    }

    public JPanel getPanel() { return panel; }
    public boolean isOverlayActive() { return overlayActive; }
    public Set<String> getArticulationPoints() { return Collections.unmodifiableSet(articulationPoints); }
    public Set<Edge> getBridgeEdges() { return Collections.unmodifiableSet(bridgeEdges); }

    private void runAnalysis() {
        Graph<String, Edge> g = graphSupplier.get();
        if (g == null || g.getVertexCount() == 0) {
            summaryLabel.setText("<html>No graph loaded.</html>");
            return;
        }

        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(g);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();

        overlayActive = true;
        articulationPoints.clear();
        bridgeEdges.clear();
        articulationPoints.addAll(result.getArticulationPoints());
        for (ArticulationPointAnalyzer.Bridge b : result.getBridges()) {
            bridgeEdges.add(b.getEdge());
        }

        resilienceLabel.setText(String.format(
                "Resilience: %.0f/100 (%s)", result.getResilienceScore(),
                result.getVulnerabilityLevel()));

        StringBuilder sb = new StringBuilder("<html>");
        sb.append(String.format("<b>Cut vertices:</b> %d (%.1f%%)<br/>",
                result.getArticulationPointCount(), result.getArticulationPointPercentage()));
        sb.append(String.format("<b>Bridges:</b> %d<br/>", result.getBridgeCount()));
        sb.append(String.format("<b>Components:</b> %d", result.getConnectedComponents()));
        sb.append("</html>");
        summaryLabel.setText(sb.toString());

        StringBuilder details = new StringBuilder("<html>");
        List<ArticulationPointAnalyzer.ArticulationPointInfo> apDetails =
                result.getArticulationPointDetails();
        if (!apDetails.isEmpty()) {
            details.append("<b>Critical nodes:</b><br/>");
            int shown = Math.min(5, apDetails.size());
            for (int i = 0; i < shown; i++) {
                ArticulationPointAnalyzer.ArticulationPointInfo info = apDetails.get(i);
                details.append(String.format("  Node %s (deg=%d, crit=%.1f)<br/>",
                        info.getVertex(), info.getDegree(), info.getCriticality()));
            }
            if (apDetails.size() > 5) {
                details.append(String.format("  ... and %d more<br/>", apDetails.size() - 5));
            }
        }
        List<ArticulationPointAnalyzer.Bridge> bridges = result.getBridges();
        if (!bridges.isEmpty()) {
            details.append("<b>Bridges:</b><br/>");
            int shown = Math.min(5, bridges.size());
            for (int i = 0; i < shown; i++) {
                ArticulationPointAnalyzer.Bridge bridge = bridges.get(i);
                details.append(String.format("  %s\u2014%s (sev=%.2f, split=%d/%d)<br/>",
                        bridge.getEndpoint1(), bridge.getEndpoint2(),
                        bridge.getSeverity(),
                        bridge.getComponentSizeA(), bridge.getComponentSizeB()));
            }
            if (bridges.size() > 5) {
                details.append(String.format("  ... and %d more<br/>", bridges.size() - 5));
            }
        }
        if (apDetails.isEmpty() && bridges.isEmpty()) {
            details.append("<i>No critical elements \u2014 network is robust.</i>");
        }
        details.append("</html>");
        detailsLabel.setText(details.toString());

        onOverlayChanged.run();
        panel.revalidate();
        panel.repaint();
    }

    public void clear() {
        overlayActive = false;
        articulationPoints.clear();
        bridgeEdges.clear();
        resilienceLabel.setText("Resilience: \u2014");
        summaryLabel.setText("<html>Click 'Analyze' to find critical nodes and edges.</html>");
        detailsLabel.setText("");
        onOverlayChanged.run();
        panel.revalidate();
        panel.repaint();
    }
}
