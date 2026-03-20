package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.visualization.VisualizationViewer;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.Dimension;
import java.util.*;
import java.util.function.Supplier;

/**
 * Encapsulates the "Ego Network" search panel previously embedded in
 * Main.java.  Manages its own UI, node-search logic, ego-network
 * computation, and overlay state.
 *
 * <p>The caller supplies a graph reference and a repaint callback so
 * this controller stays decoupled from the rest of the UI.</p>
 */
public class EgoPanelController {

    /** Callback to notify the host that overlays changed and a repaint is needed. */
    public interface OverlayCallback {
        void onOverlayChanged();
    }

    private final JPanel panel;
    private final JTextField searchField;
    private final JButton searchButton;
    private final JButton clearButton;
    private final JLabel summaryLabel;
    private final JLabel neighborListLabel;

    private final Supplier<Graph<String, edge>> graphSupplier;
    private final OverlayCallback callback;

    // Overlay state — read by GraphRenderers via getters
    private boolean overlayActive;
    private String center;
    private final Set<String> neighbors = new HashSet<>();
    private final Set<edge> edges = new HashSet<>();

    /**
     * @param graphSupplier supplies the current graph
     * @param callback      invoked after overlay state changes so the host can
     *                      call {@code syncRenderers(); vv.repaint();}
     */
    public EgoPanelController(Supplier<Graph<String, edge>> graphSupplier,
                              OverlayCallback callback) {
        this.graphSupplier = graphSupplier;
        this.callback = callback;

        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
                "Ego Network",
                TitledBorder.LEFT, TitledBorder.TOP));

        JPanel searchRow = new JPanel();
        searchRow.setLayout(new BoxLayout(searchRow, BoxLayout.X_AXIS));
        searchRow.setAlignmentX(JPanel.LEFT_ALIGNMENT);

        searchField = new JTextField(10);
        searchField.setMaximumSize(new Dimension(150, 25));
        searchField.setToolTipText("Enter node ID to explore its ego network");

        searchButton = new JButton("Search");
        searchButton.addActionListener(e -> runSearch());

        clearButton = new JButton("Clear");
        clearButton.setEnabled(false);
        clearButton.addActionListener(e -> clearOverlay());

        searchField.addActionListener(e -> runSearch());

        searchRow.add(new JLabel("Node: "));
        searchRow.add(searchField);
        searchRow.add(Box.createHorizontalStrut(5));
        searchRow.add(searchButton);
        searchRow.add(Box.createHorizontalStrut(5));
        searchRow.add(clearButton);

        summaryLabel = new JLabel("<html>Search for a node to see its ego network.</html>");
        summaryLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        neighborListLabel = new JLabel("");
        neighborListLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        panel.add(searchRow);
        panel.add(Box.createVerticalStrut(4));
        panel.add(summaryLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(neighborListLabel);
    }

    // ---- Accessors for overlay state (used by GraphRenderers) ----

    public JPanel getPanel() { return panel; }
    public boolean isOverlayActive() { return overlayActive; }
    public String getCenter() { return center; }
    public Set<String> getNeighbors() { return Collections.unmodifiableSet(neighbors); }
    public Set<edge> getEdges() { return Collections.unmodifiableSet(edges); }

    // ---- Search logic ----

    private void runSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            summaryLabel.setText("<html>Enter a node ID.</html>");
            return;
        }
        Graph<String, edge> g = graphSupplier.get();
        if (g == null || g.getVertexCount() == 0) {
            summaryLabel.setText("<html>No graph loaded.</html>");
            return;
        }

        // Find the node (exact → case-insensitive → partial)
        String foundNode = null;
        if (g.containsVertex(query)) {
            foundNode = query;
        } else {
            for (String v : g.getVertices()) {
                if (v.equalsIgnoreCase(query)) {
                    foundNode = v;
                    break;
                }
            }
            if (foundNode == null) {
                for (String v : g.getVertices()) {
                    if (v.toLowerCase().contains(query.toLowerCase())) {
                        foundNode = v;
                        break;
                    }
                }
            }
        }

        if (foundNode == null) {
            summaryLabel.setText("<html><b>Node not found:</b> " + query + "</html>");
            neighborListLabel.setText("");
            return;
        }

        // Build ego network
        center = foundNode;
        neighbors.clear();
        edges.clear();

        Collection<String> nbrs = g.getNeighbors(foundNode);
        if (nbrs != null) {
            neighbors.addAll(nbrs);
        }

        for (edge e : g.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            boolean v1InEgo = v1.equals(center) || neighbors.contains(v1);
            boolean v2InEgo = v2.equals(center) || neighbors.contains(v2);
            if (v1InEgo && v2InEgo) {
                edges.add(e);
            }
        }

        overlayActive = true;
        clearButton.setEnabled(true);
        callback.onOverlayChanged();

        // Count edge types
        Map<String, Integer> typeCounts = new HashMap<>();
        for (edge e : g.getEdges()) {
            if (e.getVertex1().equals(center) || e.getVertex2().equals(center)) {
                String typeLabel = e.getType();
                EdgeType et = EdgeType.fromCode(e.getType());
                if (et != null) typeLabel = et.getDisplayLabel();
                typeCounts.put(typeLabel, typeCounts.getOrDefault(typeLabel, 0) + 1);
            }
        }

        StringBuilder summary = new StringBuilder("<html>");
        summary.append(String.format("<b>Node:</b> %s<br/>", center));
        summary.append(String.format("<b>Degree:</b> %d<br/>", neighbors.size()));
        summary.append(String.format("<b>Ego edges:</b> %d (incl. inter-neighbor)<br/>", edges.size()));
        if (!typeCounts.isEmpty()) {
            summary.append("<b>By type:</b> ");
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
                parts.add(entry.getKey() + "=" + entry.getValue());
            }
            Collections.sort(parts);
            summary.append(String.join(", ", parts));
        }
        summary.append("</html>");
        summaryLabel.setText(summary.toString());

        // Neighbor list (limited to 20)
        List<String> sortedNeighbors = new ArrayList<>(neighbors);
        Collections.sort(sortedNeighbors);
        StringBuilder neighborHtml = new StringBuilder("<html><b>Neighbors:</b> ");
        int shown = Math.min(sortedNeighbors.size(), 20);
        for (int i = 0; i < shown; i++) {
            if (i > 0) neighborHtml.append(", ");
            neighborHtml.append(sortedNeighbors.get(i));
        }
        if (sortedNeighbors.size() > 20) {
            neighborHtml.append(String.format(" ... (+%d more)", sortedNeighbors.size() - 20));
        }
        neighborHtml.append("</html>");
        neighborListLabel.setText(neighborHtml.toString());
    }

    // ---- Clear ----

    private void clearOverlay() {
        overlayActive = false;
        center = null;
        neighbors.clear();
        edges.clear();
        clearButton.setEnabled(false);
        summaryLabel.setText("<html>Search for a node to see its ego network.</html>");
        neighborListLabel.setText("");
        callback.onOverlayChanged();
    }
}
