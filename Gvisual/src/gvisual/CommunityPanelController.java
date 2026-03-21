package gvisual;

import edu.uci.ics.jung.graph.Graph;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for the community detection panel. Manages the UI, runs
 * Louvain community detection, and maintains the overlay state.
 *
 * <p>Extracted from Main.java to reduce god-class complexity.</p>
 */
public class CommunityPanelController {

    /** Callback for obtaining the graph and requesting repaints. */
    public interface GraphHost {
        Graph<String, edge> getGraph();
        void onOverlayChanged();
    }

    private static final Color[] COMMUNITY_COLORS = {
        new Color(0, 200, 120),
        new Color(65, 135, 255),
        new Color(255, 100, 100),
        new Color(255, 200, 50),
        new Color(200, 100, 255),
        new Color(255, 150, 50),
        new Color(100, 220, 220),
        new Color(255, 100, 200),
        new Color(180, 220, 80),
        new Color(150, 130, 255),
        new Color(255, 180, 150),
        new Color(100, 180, 150),
    };

    private final GraphHost host;
    private final JPanel panel;

    private boolean overlayActive;
    private Map<String, Integer> nodeCommunityMap;

    private final JLabel countLabel;
    private final JLabel modularityLabel;
    private final JLabel detailsLabel;

    public CommunityPanelController(GraphHost host) {
        this.host = host;
        this.overlayActive = false;

        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
                "Community Detection",
                TitledBorder.CENTER, TitledBorder.TOP));

        Font labelFont = new Font("SansSerif", Font.PLAIN, 12);

        countLabel = new JLabel("Communities: \u2014");
        countLabel.setFont(labelFont);
        countLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        modularityLabel = new JLabel("Modularity: \u2014");
        modularityLabel.setFont(labelFont);
        modularityLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        detailsLabel = new JLabel("<html>Click 'Detect' to find communities.</html>");
        detailsLabel.setFont(labelFont);
        detailsLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        JButton detectButton = new JButton("Detect");
        detectButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        detectButton.addActionListener(e -> runDetection());

        JButton clearButton = new JButton("Clear");
        clearButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        clearButton.addActionListener(e -> clearOverlay());

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        buttonPanel.add(detectButton);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(clearButton);

        panel.add(countLabel);
        panel.add(modularityLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(buttonPanel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(detailsLabel);
    }

    public JPanel getPanel() { return panel; }
    public boolean isOverlayActive() { return overlayActive; }
    public Map<String, Integer> getNodeCommunityMap() { return nodeCommunityMap; }

    private String getDominantLabel(String typeCode) {
        EdgeType type = EdgeType.fromCode(typeCode);
        return type != null ? type.getDisplayLabel() : typeCode;
    }

    private void runDetection() {
        Graph<String, edge> g = host.getGraph();
        if (g == null || g.getVertexCount() == 0) {
            detailsLabel.setText("<html>No graph loaded.</html>");
            return;
        }

        CommunityDetector detector = new CommunityDetector(g);
        CommunityDetector.DetectionResult result = detector.detect();

        overlayActive = true;
        nodeCommunityMap = new HashMap<>(result.getNodeToCommunity());

        int total = result.getCommunityCount();
        List<CommunityDetector.Community> significant = result.getSignificantCommunities(2);
        double modularity = result.getModularity(g);

        countLabel.setText("Communities: " + total
                + " (" + significant.size() + " with 2+ members)");
        modularityLabel.setText(String.format("Modularity: %.4f", modularity));

        StringBuilder details = new StringBuilder("<html>");
        if (significant.isEmpty()) {
            details.append("No communities with 2+ members found.");
        } else {
            int shown = Math.min(significant.size(), 8);
            for (int i = 0; i < shown; i++) {
                CommunityDetector.Community c = significant.get(i);
                Color col = COMMUNITY_COLORS[c.getId() % COMMUNITY_COLORS.length];
                String hex = String.format("#%02x%02x%02x", col.getRed(), col.getGreen(), col.getBlue());
                details.append(String.format(
                        "<b style='color:%s'>\u25A0</b> C%d: %d nodes, %d edges, density=%.3f<br/>"
                        + "&nbsp;&nbsp;dominant: %s, avg wt: %.1f<br/>",
                        hex, c.getId(), c.getSize(), c.getInternalEdges(),
                        c.getDensity(), getDominantLabel(c.getDominantType()),
                        c.getAverageWeight()));
            }
            if (significant.size() > shown) {
                details.append("...and ").append(significant.size() - shown).append(" more<br/>");
            }
        }
        int isolatedCount = total - significant.size();
        if (isolatedCount > 0) {
            details.append("<i>").append(isolatedCount).append(" isolated node(s)</i>");
        }
        details.append("</html>");
        detailsLabel.setText(details.toString());

        panel.revalidate();
        panel.repaint();
        host.onOverlayChanged();
    }

    private void clearOverlay() {
        overlayActive = false;
        nodeCommunityMap = null;
        countLabel.setText("Communities: \u2014");
        modularityLabel.setText("Modularity: \u2014");
        detailsLabel.setText("<html>Click 'Detect' to find communities.</html>");
        panel.revalidate();
        panel.repaint();
        host.onOverlayChanged();
    }
}
