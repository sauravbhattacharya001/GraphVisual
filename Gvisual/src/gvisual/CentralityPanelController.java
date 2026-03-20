package gvisual;

import edu.uci.ics.jung.graph.Graph;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.Dimension;
import java.awt.Font;
import java.util.*;
import java.util.function.Supplier;

/**
 * Encapsulates the "Centrality Analysis" panel previously embedded in Main.java.
 * Owns UI components, compute/clear lifecycle, and metric-based ranking display.
 */
public class CentralityPanelController {

    private final JPanel panel;
    private final JLabel topologyLabel;
    private final JLabel summaryLabel;
    private final JLabel rankingLabel;
    private final JComboBox<String> metricCombo;
    private final JButton computeButton;
    private final JButton clearButton;

    private final Supplier<Graph<String, edge>> graphSupplier;

    private boolean active;
    private final Map<String, NodeCentralityAnalyzer.CentralityResult> results = new HashMap<>();

    public CentralityPanelController(Supplier<Graph<String, edge>> graphSupplier) {
        this.graphSupplier = graphSupplier;

        Font labelFont = new Font("SansSerif", Font.PLAIN, 12);

        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
                "Centrality Analysis",
                TitledBorder.CENTER, TitledBorder.TOP));

        topologyLabel = new JLabel("Topology: \u2014");
        topologyLabel.setFont(labelFont);
        topologyLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        summaryLabel = new JLabel("<html>Click 'Compute' to analyze node centrality.</html>");
        summaryLabel.setFont(labelFont);
        summaryLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        rankingLabel = new JLabel("");
        rankingLabel.setFont(labelFont);
        rankingLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        metricCombo = new JComboBox<>(new String[]{"Combined", "Degree", "Betweenness", "Closeness"});
        metricCombo.setAlignmentX(JComboBox.LEFT_ALIGNMENT);
        metricCombo.setMaximumSize(new Dimension(200, 25));
        metricCombo.addActionListener(e -> {
            if (active) updateRanking();
        });

        JPanel metricPanel = new JPanel();
        metricPanel.setLayout(new BoxLayout(metricPanel, BoxLayout.X_AXIS));
        metricPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        JLabel sortLabel = new JLabel("Sort by: ");
        sortLabel.setFont(labelFont);
        metricPanel.add(sortLabel);
        metricPanel.add(metricCombo);

        computeButton = new JButton("Compute");
        computeButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        computeButton.addActionListener(e -> runAnalysis());

        clearButton = new JButton("Clear");
        clearButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        clearButton.addActionListener(e -> clear());

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        buttonPanel.add(computeButton);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(clearButton);

        panel.add(topologyLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(metricPanel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(buttonPanel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(summaryLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(rankingLabel);
    }

    /** Returns the Swing panel to embed in the UI. */
    public JPanel getPanel() { return panel; }

    /** Whether centrality overlay is currently active. */
    public boolean isActive() { return active; }

    /** Returns cached centrality results (node id → result). */
    public Map<String, NodeCentralityAnalyzer.CentralityResult> getResults() {
        return Collections.unmodifiableMap(results);
    }

    private void runAnalysis() {
        Graph<String, edge> g = graphSupplier.get();
        if (g == null || g.getVertexCount() == 0) {
            summaryLabel.setText("<html>No graph loaded.</html>");
            return;
        }

        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(g);
        analyzer.compute();

        active = true;
        results.clear();
        for (NodeCentralityAnalyzer.CentralityResult r : analyzer.getRankedResults()) {
            results.put(r.getNodeId(), r);
        }

        topologyLabel.setText("Topology: " + analyzer.classifyTopology());

        Map<String, Object> summary = analyzer.getSummary();
        StringBuilder sb = new StringBuilder("<html>");
        sb.append(String.format("<b>Avg Degree C:</b> %.3f<br/>", summary.get("avgDegreeCentrality")));
        sb.append(String.format("<b>Avg Betweenness C:</b> %.3f<br/>", summary.get("avgBetweennessCentrality")));
        sb.append(String.format("<b>Avg Closeness C:</b> %.3f<br/>", summary.get("avgClosenessCentrality")));
        sb.append(String.format("<b>Most Connected:</b> Node %s (%.3f)<br/>",
                summary.get("maxDegreeCentralityNode"), summary.get("maxDegreeCentrality")));
        sb.append(String.format("<b>Most Central:</b> Node %s (%.3f)<br/>",
                summary.get("maxBetweennessCentralityNode"), summary.get("maxBetweennessCentrality")));
        sb.append(String.format("<b>Most Reachable:</b> Node %s (%.3f)",
                summary.get("maxClosenessCentralityNode"), summary.get("maxClosenessCentrality")));
        sb.append("</html>");
        summaryLabel.setText(sb.toString());

        updateRanking();
        panel.revalidate();
        panel.repaint();
    }

    private void updateRanking() {
        if (!active || results.isEmpty()) return;

        String metric = (String) metricCombo.getSelectedItem();
        List<NodeCentralityAnalyzer.CentralityResult> sorted = new ArrayList<>(results.values());
        final String m = metric.toLowerCase();

        sorted.sort((a, b) -> {
            double va = metricValue(a, m);
            double vb = metricValue(b, m);
            return Double.compare(vb, va);
        });

        int shown = Math.min(sorted.size(), 10);
        StringBuilder sb = new StringBuilder("<html><b>Top " + shown + " by " + metric + ":</b><br/>");
        for (int i = 0; i < shown; i++) {
            NodeCentralityAnalyzer.CentralityResult r = sorted.get(i);
            String medal = i == 0 ? "\uD83E\uDD47" : i == 1 ? "\uD83E\uDD48" : i == 2 ? "\uD83E\uDD49" : "&nbsp;&nbsp;";
            sb.append(String.format("%s #%d Node %s: %.3f (deg=%d)<br/>",
                    medal, i + 1, r.getNodeId(), metricValue(r, m), r.getDegree()));
        }
        sb.append("</html>");
        rankingLabel.setText(sb.toString());
        panel.revalidate();
        panel.repaint();
    }

    private static double metricValue(NodeCentralityAnalyzer.CentralityResult r, String metric) {
        switch (metric) {
            case "degree":      return r.getDegreeCentrality();
            case "betweenness": return r.getBetweennessCentrality();
            case "closeness":   return r.getClosenessCentrality();
            default:            return r.getCombinedScore();
        }
    }

    /** Clears all analysis state and resets the panel. */
    public void clear() {
        active = false;
        results.clear();
        topologyLabel.setText("Topology: \u2014");
        summaryLabel.setText("<html>Click 'Compute' to analyze node centrality.</html>");
        rankingLabel.setText("");
        panel.revalidate();
        panel.repaint();
    }
}
