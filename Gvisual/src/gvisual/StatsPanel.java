package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.awt.Font;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

/**
 * Self-contained panel that displays network statistics for the current graph.
 *
 * <p>Extracted from {@code Main} to reduce god-class bloat and make the stats
 * display independently testable and reusable.</p>
 */
public class StatsPanel extends JPanel {

    private static final Font LABEL_FONT = new Font("SansSerif", Font.PLAIN, 12);

    private final JLabel statsNodeCount;
    private final JLabel statsEdgeCount;
    private final JLabel statsFriendCount;
    private final JLabel statsClassmateCount;
    private final JLabel statsFsCount;
    private final JLabel statsStrangerCount;
    private final JLabel statsStudyGCount;
    private final JLabel statsDensity;
    private final JLabel statsAvgDegree;
    private final JLabel statsMaxDegree;
    private final JLabel statsAvgWeight;
    private final JLabel statsIsolated;
    private final JLabel statsTopNodes;

    public StatsPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
                "Network Statistics",
                TitledBorder.CENTER,
                TitledBorder.TOP));

        statsNodeCount = label("Nodes: 0");
        statsEdgeCount = label("Edges: 0 (visible) / 0 (total)");
        statsFriendCount = label("  Friends: 0");
        statsFriendCount.setForeground(EdgeType.FRIEND.getColor());
        statsClassmateCount = label("  Classmates: 0");
        statsClassmateCount.setForeground(EdgeType.CLASSMATE.getColor());
        statsFsCount = label("  Fam. Strangers: 0");
        statsFsCount.setForeground(EdgeType.FAMILIAR.getColor());
        statsStrangerCount = label("  Strangers: 0");
        statsStrangerCount.setForeground(EdgeType.STRANGER.getColor());
        statsStudyGCount = label("  Study Groups: 0");
        statsStudyGCount.setForeground(EdgeType.STUDY_GROUP.getColor());
        statsDensity = label("Density: 0.000");
        statsAvgDegree = label("Avg Degree: 0.00");
        statsMaxDegree = label("Max Degree: 0");
        statsAvgWeight = label("Avg Weight: 0.00");
        statsIsolated = label("Isolated Nodes: 0");
        statsTopNodes = label("<html>Top Nodes: —</html>");

        add(statsNodeCount);
        add(statsEdgeCount);
        add(Box.createVerticalStrut(4));
        add(statsFriendCount);
        add(statsClassmateCount);
        add(statsFsCount);
        add(statsStrangerCount);
        add(statsStudyGCount);
        add(Box.createVerticalStrut(4));
        add(statsDensity);
        add(statsAvgDegree);
        add(statsMaxDegree);
        add(statsAvgWeight);
        add(statsIsolated);
        add(Box.createVerticalStrut(4));
        add(statsTopNodes);
    }

    /**
     * Refreshes all labels from the supplied {@link GraphStats}.
     *
     * @param stats pre-computed graph statistics (may be {@code null} for a reset)
     */
    public void update(GraphStats stats) {
        if (stats == null) return;

        statsNodeCount.setText("Nodes: " + stats.getNodeCount());
        statsEdgeCount.setText("Edges: " + stats.getVisibleEdgeCount()
                + " (visible) / " + stats.getTotalEdgeCount() + " (total)");
        statsFriendCount.setText("  Friends: " + stats.getFriendCount());
        statsClassmateCount.setText("  Classmates: " + stats.getClassmateCount());
        statsFsCount.setText("  Fam. Strangers: " + stats.getFsCount());
        statsStrangerCount.setText("  Strangers: " + stats.getStrangerCount());
        statsStudyGCount.setText("  Study Groups: " + stats.getStudyGroupCount());
        statsDensity.setText(String.format("Density: %.4f", stats.getDensity()));
        statsAvgDegree.setText(String.format("Avg Degree: %.2f", stats.getAverageDegree()));
        statsMaxDegree.setText("Max Degree: " + stats.getMaxDegree());
        statsAvgWeight.setText(String.format("Avg Weight: %.1f", stats.getAverageWeight()));
        statsIsolated.setText("Isolated Nodes: " + stats.getIsolatedNodeCount());

        List<String> topNodes = stats.getTopNodes(3);
        if (topNodes.isEmpty()) {
            statsTopNodes.setText("<html>Top Nodes: —</html>");
        } else {
            StringBuilder sb = new StringBuilder("<html>Top Nodes:<br/>");
            for (String node : topNodes) {
                sb.append("&nbsp;&nbsp;").append(node).append("<br/>");
            }
            sb.append("</html>");
            statsTopNodes.setText(sb.toString());
        }

        revalidate();
        repaint();
    }

    private static JLabel label(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(LABEL_FONT);
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        return lbl;
    }
}
