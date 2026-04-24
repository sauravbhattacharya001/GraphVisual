package gvisual;

import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.control.DefaultModalGraphMouse;
import edu.uci.ics.jung.visualization.control.ModalGraphMouse;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Controller for the shortest-path-finder panel. Manages the UI, node
 * selection via mouse clicks, and path computation/highlighting.
 *
 * <p>Extracted from Main.java to reduce god-class complexity. Follows the
 * same delegate pattern as {@link ArticulationPanelController} and
 * {@link CentralityPanelController}.</p>
 */
public class PathPanelController {

    /** Callback interface for requesting graph refreshes from the host frame. */
    public interface GraphHost {
        Graph<String, Edge> getGraph();
        Layout<String, Edge> getLayout();
        VisualizationViewer<String, Edge> getViewer();
        void refreshGraph();
    }

    private final GraphHost host;
    private final JPanel panel;

    // State
    private boolean pathFindingMode;
    private String pathSource;
    private String pathTarget;
    private final Set<String> pathVertices = new HashSet<>();
    private final Set<Edge> pathEdges = new HashSet<>();

    // UI components
    private final JLabel sourceLabel;
    private final JLabel targetLabel;
    private final JLabel resultLabel;
    private final JButton findButton;
    private final JButton clearButton;
    private final JRadioButton byHops;
    private final JRadioButton byWeight;

    public PathPanelController(GraphHost host) {
        this.host = host;
        this.pathFindingMode = false;

        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
                "Shortest Path Finder",
                TitledBorder.CENTER, TitledBorder.TOP));

        Font labelFont = new Font("SansSerif", Font.PLAIN, 12);

        sourceLabel = new JLabel("Source: (none)");
        sourceLabel.setFont(labelFont);
        sourceLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        targetLabel = new JLabel("Target: (none)");
        targetLabel.setFont(labelFont);
        targetLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        resultLabel = new JLabel("<html>Click 'Select Nodes' then click two nodes on the graph.</html>");
        resultLabel.setFont(labelFont);
        resultLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        byHops = new JRadioButton("Fewest hops", true);
        byWeight = new JRadioButton("Lowest weight");
        ButtonGroup group = new ButtonGroup();
        group.add(byHops);
        group.add(byWeight);

        JPanel radioPanel = new JPanel();
        radioPanel.setLayout(new BoxLayout(radioPanel, BoxLayout.X_AXIS));
        radioPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        radioPanel.add(byHops);
        radioPanel.add(byWeight);

        findButton = new JButton("Select Nodes");
        findButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        findButton.addActionListener(e -> {
            if (!pathFindingMode) enablePathFindingMode();
            else disablePathFindingMode();
        });

        clearButton = new JButton("Clear Path");
        clearButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        clearButton.addActionListener(e -> clearPath());

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        buttonPanel.add(findButton);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(clearButton);

        panel.add(sourceLabel);
        panel.add(targetLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(radioPanel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(buttonPanel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(resultLabel);
    }

    public JPanel getPanel() { return panel; }
    public Set<String> getPathVertices() { return pathVertices; }
    public Set<Edge> getPathEdges() { return pathEdges; }
    public String getPathSource() { return pathSource; }
    public String getPathTarget() { return pathTarget; }

    private void enablePathFindingMode() {
        pathFindingMode = true;
        pathSource = null;
        pathTarget = null;
        pathVertices.clear();
        pathEdges.clear();
        findButton.setText("Cancel");
        sourceLabel.setText("Source: (click a node...)");
        targetLabel.setText("Target: (waiting...)");
        resultLabel.setText("<html>Click the source node on the graph.</html>");

        DefaultModalGraphMouse gm = new DefaultModalGraphMouse();
        gm.setMode(ModalGraphMouse.Mode.PICKING);
        host.getViewer().setGraphMouse(gm);
        host.getViewer().addMouseListener(mouseListener);
        host.refreshGraph();
    }

    private void disablePathFindingMode() {
        pathFindingMode = false;
        findButton.setText("Select Nodes");
        host.getViewer().removeMouseListener(mouseListener);

        DefaultModalGraphMouse gm = new DefaultModalGraphMouse();
        gm.setMode(ModalGraphMouse.Mode.TRANSFORMING);
        host.getViewer().setGraphMouse(gm);
    }

    private void clearPath() {
        pathSource = null;
        pathTarget = null;
        pathVertices.clear();
        pathEdges.clear();
        sourceLabel.setText("Source: (none)");
        targetLabel.setText("Target: (none)");
        resultLabel.setText("<html>Click 'Select Nodes' then click two nodes on the graph.</html>");

        if (pathFindingMode) disablePathFindingMode();
        host.refreshGraph();
    }

    private String findClosestVertex(int screenX, int screenY) {
        String closest = null;
        double minDist = Double.MAX_VALUE;
        Graph<String, Edge> g = host.getGraph();
        Layout<String, Edge> layout = host.getLayout();
        VisualizationViewer<String, Edge> vv = host.getViewer();

        for (String vertex : g.getVertices()) {
            java.awt.geom.Point2D layoutPoint = layout.transform(vertex);
            java.awt.geom.Point2D screenPoint = vv.getRenderContext()
                    .getMultiLayerTransformer().transform(layoutPoint);
            double dx = screenPoint.getX() - screenX;
            double dy = screenPoint.getY() - screenY;
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist < minDist && dist < 30) {
                minDist = dist;
                closest = vertex;
            }
        }
        return closest;
    }

    private void computeAndHighlightPath() {
        if (pathSource == null || pathTarget == null) return;

        Graph<String, Edge> g = host.getGraph();
        ShortestPathFinder finder = new ShortestPathFinder(g);
        ShortestPathFinder.PathResult result;

        if (byWeight.isSelected()) {
            result = finder.findShortestByWeight(pathSource, pathTarget);
        } else {
            result = finder.findShortestByHops(pathSource, pathTarget);
        }

        pathVertices.clear();
        pathEdges.clear();

        if (result == null) {
            resultLabel.setText("<html><b style='color:red'>No path found!</b><br/>"
                    + "Nodes are in disconnected components.</html>");
        } else {
            pathVertices.addAll(result.getVertices());
            pathEdges.addAll(result.getEdges());

            String mode = byWeight.isSelected() ? "weight-optimal" : "hop-optimal";
            StringBuilder edgeTypes = new StringBuilder();
            for (Edge e : result.getEdges()) {
                if (edgeTypes.length() > 0) edgeTypes.append("\u2192");
                edgeTypes.append(e.getType());
            }

            StringBuilder pathStr = new StringBuilder();
            for (int i = 0; i < result.getVertices().size(); i++) {
                if (i > 0) pathStr.append("\u2192");
                pathStr.append(ExportUtils.escapeHtml(result.getVertices().get(i)));
            }

            resultLabel.setText(String.format(
                    "<html><b style='color:#00FF00'>Path found!</b> (%s)<br/>"
                    + "Hops: %d<br/>"
                    + "Total weight: %.1f<br/>"
                    + "Edge types: %s<br/>"
                    + "Path: %s</html>",
                    mode, result.getHopCount(), result.getTotalWeight(),
                    edgeTypes.toString(), pathStr.toString()));
        }

        disablePathFindingMode();
        host.refreshGraph();
    }

    private final MouseListener mouseListener = new MouseListener() {
        public void mouseClicked(MouseEvent e) {
            if (!pathFindingMode) return;
            String clicked = findClosestVertex(e.getX(), e.getY());
            if (clicked == null) return;

            if (pathSource == null) {
                pathSource = clicked;
                sourceLabel.setText("Source: Node " + clicked);
                targetLabel.setText("Target: (click another node...)");
                resultLabel.setText("<html>Now click the target node.</html>");
                host.refreshGraph();
            } else if (pathTarget == null) {
                if (clicked.equals(pathSource)) {
                    resultLabel.setText("<html>Same node \u2014 pick a different target.</html>");
                    return;
                }
                pathTarget = clicked;
                targetLabel.setText("Target: Node " + clicked);
                computeAndHighlightPath();
            }
        }
        public void mousePressed(MouseEvent e) {}
        public void mouseReleased(MouseEvent e) {}
        public void mouseEntered(MouseEvent e) {}
        public void mouseExited(MouseEvent e) {}
    };
}
