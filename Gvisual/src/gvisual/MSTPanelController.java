package gvisual;

import edu.uci.ics.jung.graph.Graph;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.*;
import java.util.function.Supplier;

/**
 * Encapsulates the "Minimum Spanning Tree" analysis panel previously embedded
 * in Main.java.  Owns UI, computation lifecycle, overlay state, and result
 * formatting so that Main only needs to wire the panel into the layout.
 */
public class MSTPanelController {

    private final JPanel panel;
    private final JLabel summaryLabel;
    private final JLabel statsLabel;
    private final JLabel componentsLabel;
    private final JButton computeButton;
    private final JButton clearButton;

    private final Supplier<Graph<String, edge>> graphSupplier;
    private final Runnable onOverlayChanged;

    private boolean overlayActive;
    private final Set<edge> mstEdges = new HashSet<>();

    /**
     * @param graphSupplier    supplies the current graph
     * @param onOverlayChanged callback to refresh renderers/visualization after overlay changes
     */
    public MSTPanelController(Supplier<Graph<String, edge>> graphSupplier,
                               Runnable onOverlayChanged) {
        this.graphSupplier = graphSupplier;
        this.onOverlayChanged = onOverlayChanged;

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

        computeButton = new JButton("Compute");
        computeButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        computeButton.addActionListener(e -> runComputation());

        clearButton = new JButton("Clear");
        clearButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        clearButton.addActionListener(e -> clearOverlay());

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        buttonPanel.add(computeButton);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(clearButton);

        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
                "Minimum Spanning Tree",
                TitledBorder.CENTER,
                TitledBorder.TOP));
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
    public Set<edge> getMstEdges() { return Collections.unmodifiableSet(mstEdges); }

    private void runComputation() {
        Graph<String, edge> g = graphSupplier.get();
        if (g == null || g.getVertexCount() == 0) {
            summaryLabel.setText("<html>No graph loaded.</html>");
            return;
        }

        MinimumSpanningTree mstComputer = new MinimumSpanningTree(g);
        MinimumSpanningTree.MSTResult result = mstComputer.compute();

        overlayActive = true;
        mstEdges.clear();
        mstEdges.addAll(result.getEdges());

        // Summary
        summaryLabel.setText("<html><b style='color:#00FF00'>"
                + result.getSummary() + "</b></html>");

        // Stats
        StringBuilder stats = new StringBuilder("<html>");
        stats.append(String.format("<b>Vertices:</b> %d<br/>", result.getVertexCount()));
        stats.append(String.format("<b>MST Edges:</b> %d<br/>", result.getEdgeCount()));
        stats.append(String.format("<b>Total Weight:</b> %.1f<br/>", result.getTotalWeight()));
        stats.append(String.format("<b>Avg Weight:</b> %.1f<br/>", result.getAverageWeight()));

        if (result.getHeaviestEdge() != null) {
            edge heavy = result.getHeaviestEdge();
            stats.append(String.format("<b>Bottleneck:</b> %s\u2194%s (%.1f)<br/>",
                    heavy.getVertex1(), heavy.getVertex2(), heavy.getWeight()));
        }
        if (result.getLightestEdge() != null) {
            edge light = result.getLightestEdge();
            stats.append(String.format("<b>Lightest:</b> %s\u2194%s (%.1f)<br/>",
                    light.getVertex1(), light.getVertex2(), light.getWeight()));
        }

        // Edge type distribution
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

        // Component breakdown (for forests)
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
        onOverlayChanged.run();
    }

    private void clearOverlay() {
        overlayActive = false;
        mstEdges.clear();
        summaryLabel.setText("<html>Click 'Compute' to find the MST.</html>");
        statsLabel.setText("");
        componentsLabel.setText("");
        panel.revalidate();
        panel.repaint();
        onOverlayChanged.run();
    }

    private static String getDominantLabel(String typeCode) {
        EdgeType type = EdgeType.fromCode(typeCode);
        return type != null ? type.getDisplayLabel() : typeCode;
    }
}
