package gvisual;

import edu.uci.ics.jung.graph.Graph;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Controller for the Minimum Spanning Tree panel. Manages MST computation,
 * overlay state, and the results UI.
 *
 * <p>Extracted from Main.java to reduce god-class complexity.</p>
 */
public class MSTPanelController {

    /** Callback for obtaining the graph and requesting repaints. */
    public interface GraphHost {
        Graph<String, Edge> getGraph();
        void onOverlayChanged();
    }

    private final GraphHost host;
    private final JPanel panel;

    private boolean overlayActive;
    private final Set<Edge> mstEdges = new HashSet<>();

    private final JLabel summaryLabel;
    private final JLabel statsLabel;
    private final JLabel componentsLabel;

    public MSTPanelController(GraphHost host) {
        this.host = host;
        this.overlayActive = false;

        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
                "Minimum Spanning Tree",
                TitledBorder.CENTER, TitledBorder.TOP));

        Font labelFont = new Font("SansSerif", Font.PLAIN, 12);

        summaryLabel = new JLabel("<html>Click 'Compute' to find the MST.</html>");
        summaryLabel.setFont(labelFont);
        summaryLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        statsLabel = new JLabel("");
        statsLabel.setFont(labelFont);
        statsLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        componentsLabel = new JLabel("");
        componentsLabel.setFont(labelFont);
        componentsLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        JButton computeButton = new JButton("Compute");
        computeButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        computeButton.addActionListener(e -> runComputation());

        JButton clearButton = new JButton("Clear");
        clearButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        clearButton.addActionListener(e -> clearOverlay());

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        buttonPanel.add(computeButton);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(clearButton);

        panel.add(summaryLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(buttonPanel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(statsLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(componentsLabel);
    }

    public JPanel getPanel() { return panel; }
    public boolean isOverlayActive() { return overlayActive; }
    public Set<Edge> getMstEdges() { return mstEdges; }

    private String getDominantLabel(String typeCode) {
        EdgeType type = EdgeType.fromCode(typeCode);
        return type != null ? type.getDisplayLabel() : typeCode;
    }

    private void runComputation() {
        Graph<String, Edge> g = host.getGraph();
        if (g == null || g.getVertexCount() == 0) {
            summaryLabel.setText("<html>No graph loaded.</html>");
            return;
        }

        MinimumSpanningTree mstComputer = new MinimumSpanningTree(g);
        MinimumSpanningTree.MSTResult result = mstComputer.compute();

        overlayActive = true;
        mstEdges.clear();
        mstEdges.addAll(result.getEdges());

        summaryLabel.setText("<html><b style='color:#00FF00'>"
                + result.getSummary() + "</b></html>");

        StringBuilder stats = new StringBuilder("<html>");
        stats.append(String.format("<b>Vertices:</b> %d<br/>", result.getVertexCount()));
        stats.append(String.format("<b>MST Edges:</b> %d<br/>", result.getEdgeCount()));
        stats.append(String.format("<b>Total Weight:</b> %.1f<br/>", result.getTotalWeight()));
        stats.append(String.format("<b>Avg Weight:</b> %.1f<br/>", result.getAverageWeight()));

        if (result.getHeaviestEdge() != null) {
            Edge heavy = result.getHeaviestEdge();
            stats.append(String.format("<b>Bottleneck:</b> %s\u2194%s (%.1f)<br/>",
                    heavy.getVertex1(), heavy.getVertex2(), heavy.getWeight()));
        }
        if (result.getLightestEdge() != null) {
            Edge light = result.getLightestEdge();
            stats.append(String.format("<b>Lightest:</b> %s\u2194%s (%.1f)<br/>",
                    light.getVertex1(), light.getVertex2(), light.getWeight()));
        }

        Map<String, Integer> dist = result.getEdgeTypeDistribution();
        if (!dist.isEmpty()) {
            stats.append("<b>Types:</b> ");
            boolean first = true;
            for (Map.Entry<String, Integer> entry : dist.entrySet()) {
                if (!first) stats.append(", ");
                stats.append(getDominantLabel(entry.getKey())).append("=").append(entry.getValue());
                first = false;
            }
            stats.append("<br/>");
        }
        stats.append("</html>");
        statsLabel.setText(stats.toString());

        if (result.getComponentCount() > 1) {
            StringBuilder comps = new StringBuilder("<html><b>Components:</b><br/>");
            int shown = Math.min(result.getComponents().size(), 6);
            for (int i = 0; i < shown; i++) {
                MinimumSpanningTree.MSTComponent comp = result.getComponents().get(i);
                comps.append(String.format("&nbsp;C%d: %d nodes, %d edges, wt=%.1f",
                        comp.getId(), comp.getSize(), comp.getEdges().size(), comp.getTotalWeight()));
                String dominant = comp.getDominantType();
                if (dominant != null) {
                    comps.append(" (").append(getDominantLabel(dominant)).append(")");
                }
                comps.append("<br/>");
            }
            if (result.getComponents().size() > shown) {
                comps.append("...and ").append(result.getComponents().size() - shown).append(" more<br/>");
            }
            comps.append("</html>");
            componentsLabel.setText(comps.toString());
        } else {
            componentsLabel.setText("");
        }

        panel.revalidate();
        panel.repaint();
        host.onOverlayChanged();
    }

    private void clearOverlay() {
        overlayActive = false;
        mstEdges.clear();
        summaryLabel.setText("<html>Click 'Compute' to find the MST.</html>");
        statsLabel.setText("");
        componentsLabel.setText("");
        panel.revalidate();
        panel.repaint();
        host.onOverlayChanged();
    }
}
