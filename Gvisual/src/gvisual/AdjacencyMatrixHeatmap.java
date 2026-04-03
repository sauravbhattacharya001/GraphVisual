package gvisual;

import edu.uci.ics.jung.graph.Graph;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * Adjacency matrix heatmap visualization for graphs.
 * Displays the graph as a colored matrix where cell intensity represents
 * Edge weight/presence, with Edge-type color coding.
 * 
 * Features:
 * - Color-coded cells by Edge type (friend, classmate, familiar stranger, etc.)
 * - Zoom and pan controls
 * - Node reordering by degree, name, or community
 * - Tooltip on hover showing node pair and Edge details
 * - Export to PNG
 * 
 * @author zalenix
 */
public class AdjacencyMatrixHeatmap extends JPanel {

    private final Graph<String, Edge> graph;
    private List<String> nodeOrder;
    private final Map<String, Map<String, Edge>> adjacency;
    private int cellSize = 12;
    private int offsetX = 0;
    private int offsetY = 0;
    private Point dragStart = null;
    private String hoveredRow = null;
    private String hoveredCol = null;
    private String sortMode = "degree";

    // Edge type colors matching the main visualization
    private static final Color FRIEND_COLOR = new Color(0, 200, 0);
    private static final Color CLASSMATE_COLOR = new Color(0, 150, 255);
    private static final Color FS_COLOR = new Color(255, 200, 0);
    private static final Color STRANGER_COLOR = new Color(255, 80, 80);
    private static final Color STUDYG_COLOR = new Color(200, 0, 255);
    private static final Color DEFAULT_COLOR = new Color(180, 180, 180);
    private static final Color BG_COLOR = new Color(30, 30, 30);
    private static final Color GRID_COLOR = new Color(60, 60, 60);
    private static final Color HIGHLIGHT_COLOR = new Color(255, 255, 255, 40);
    private static final Color LABEL_COLOR = new Color(200, 200, 200);

    public AdjacencyMatrixHeatmap(Graph<String, Edge> graph) {
        this.graph = graph;
        this.adjacency = new HashMap<>();
        setBackground(BG_COLOR);
        setPreferredSize(new Dimension(600, 600));

        buildAdjacency();
        sortNodes();
        setupInteraction();
    }

    private void buildAdjacency() {
        adjacency.clear();
        for (Edge e : graph.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            adjacency.computeIfAbsent(v1, k -> new HashMap<>()).put(v2, e);
            adjacency.computeIfAbsent(v2, k -> new HashMap<>()).put(v1, e);
        }
    }

    private void sortNodes() {
        nodeOrder = new ArrayList<>(graph.getVertices());
        switch (sortMode) {
            case "degree":
                nodeOrder.sort((a, b) -> Integer.compare(graph.degree(b), graph.degree(a)));
                break;
            case "name":
                Collections.sort(nodeOrder);
                break;
            case "community":
                sortByCommunity();
                break;
            default:
                Collections.sort(nodeOrder);
        }
    }

    private void sortByCommunity() {
        // Simple greedy community grouping: place connected nodes adjacent
        if (nodeOrder.isEmpty()) return;
        List<String> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        // Start with highest degree node
        nodeOrder.sort((a, b) -> Integer.compare(graph.degree(b), graph.degree(a)));

        Queue<String> queue = new ArrayDeque<>();
        queue.add(nodeOrder.get(0));
        visited.add(nodeOrder.get(0));

        while (!queue.isEmpty()) {
            String node = queue.poll();
            sorted.add(node);
            List<String> neighbors = new ArrayList<>(graph.getNeighbors(node));
            neighbors.sort((a, b) -> Integer.compare(graph.degree(b), graph.degree(a)));
            for (String n : neighbors) {
                if (!visited.contains(n)) {
                    visited.add(n);
                    queue.add(n);
                }
            }
        }
        // Add any disconnected nodes
        for (String n : nodeOrder) {
            if (!visited.contains(n)) {
                sorted.add(n);
            }
        }
        nodeOrder = sorted;
    }

    private void setupInteraction() {
        // Zoom with mouse wheel
        addMouseWheelListener(e -> {
            int oldSize = cellSize;
            if (e.getWheelRotation() < 0) {
                cellSize = Math.min(60, cellSize + 2);
            } else {
                cellSize = Math.max(3, cellSize - 2);
            }
            if (cellSize != oldSize) repaint();
        });

        // Pan with drag
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    dragStart = e.getPoint();
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                dragStart = null;
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart != null) {
                    offsetX += e.getX() - dragStart.x;
                    offsetY += e.getY() - dragStart.y;
                    dragStart = e.getPoint();
                    repaint();
                }
            }
            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e.getX(), e.getY());
            }
        });

        setToolTipText("");
    }

    private void updateHover(int mx, int my) {
        int labelMargin = getLabelMargin();
        int x = mx - labelMargin - offsetX;
        int y = my - labelMargin - offsetY;
        int n = nodeOrder.size();

        String oldRow = hoveredRow, oldCol = hoveredCol;
        if (x >= 0 && y >= 0 && x < n * cellSize && y < n * cellSize) {
            int col = x / cellSize;
            int row = y / cellSize;
            hoveredRow = (row < n) ? nodeOrder.get(row) : null;
            hoveredCol = (col < n) ? nodeOrder.get(col) : null;
        } else {
            hoveredRow = null;
            hoveredCol = null;
        }
        if (!Objects.equals(oldRow, hoveredRow) || !Objects.equals(oldCol, hoveredCol)) {
            repaint();
        }
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        if (hoveredRow != null && hoveredCol != null) {
            if (hoveredRow.equals(hoveredCol)) {
                return "Node: " + hoveredRow + " (degree: " + graph.degree(hoveredRow) + ")";
            }
            Map<String, Edge> rowMap = adjacency.get(hoveredRow);
            if (rowMap != null && rowMap.containsKey(hoveredCol)) {
                Edge ed = rowMap.get(hoveredCol);
                String type = getEdgeTypeName(ed.getType());
                String weight = ed.getWeight() != 0 ? ", weight: " + ed.getWeight() : "";
                return hoveredRow + " ↔ " + hoveredCol + " [" + type + weight + "]";
            }
            return hoveredRow + " ↔ " + hoveredCol + " (no Edge)";
        }
        return null;
    }

    private String getEdgeTypeName(String type) {
        if (type == null) return "unknown";
        return EdgeTypeRegistry.getName(type);
    }

    private Color getEdgeColor(Edge e) {
        if (e == null) return DEFAULT_COLOR;
        String type = e.getType();
        if (type == null) return DEFAULT_COLOR;
        switch (type) {
            case "f": return FRIEND_COLOR;
            case "c": return CLASSMATE_COLOR;
            case "fs": return FS_COLOR;
            case "s": return STRANGER_COLOR;
            case "sg": return STUDYG_COLOR;
            default: return DEFAULT_COLOR;
        }
    }

    private int getLabelMargin() {
        return Math.min(100, Math.max(30, cellSize * 3));
    }

    @Override
    protected void paintComponent(Graphics g2) {
        super.paintComponent(g2);
        Graphics2D g = (Graphics2D) g2;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int n = nodeOrder.size();
        if (n == 0) {
            g.setColor(LABEL_COLOR);
            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g.drawString("No nodes in graph", 20, 30);
            return;
        }

        int labelMargin = getLabelMargin();
        int matrixSize = n * cellSize;

        g.translate(offsetX, offsetY);

        // Draw cells
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < n; col++) {
                int cx = labelMargin + col * cellSize;
                int cy = labelMargin + row * cellSize;

                String nodeR = nodeOrder.get(row);
                String nodeC = nodeOrder.get(col);

                if (row == col) {
                    // Diagonal - show degree as brightness
                    int deg = graph.degree(nodeR);
                    int brightness = Math.min(255, 40 + deg * 15);
                    g.setColor(new Color(brightness, brightness, brightness));
                } else {
                    Map<String, Edge> rowMap = adjacency.get(nodeR);
                    if (rowMap != null && rowMap.containsKey(nodeC)) {
                        Edge e = rowMap.get(nodeC);
                        Color base = getEdgeColor(e);
                        float alpha = e.getWeight() > 0 ? Math.min(1f, 0.4f + e.getWeight() * 0.1f) : 0.85f;
                        g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(),
                                (int)(alpha * 255)));
                    } else {
                        g.setColor(BG_COLOR);
                    }
                }
                g.fillRect(cx, cy, cellSize, cellSize);
            }
        }

        // Draw grid if cells are large enough
        if (cellSize >= 6) {
            g.setColor(GRID_COLOR);
            for (int i = 0; i <= n; i++) {
                g.drawLine(labelMargin + i * cellSize, labelMargin,
                           labelMargin + i * cellSize, labelMargin + matrixSize);
                g.drawLine(labelMargin, labelMargin + i * cellSize,
                           labelMargin + matrixSize, labelMargin + i * cellSize);
            }
        }

        // Highlight hovered row/col
        if (hoveredRow != null && hoveredCol != null) {
            int rowIdx = nodeOrder.indexOf(hoveredRow);
            int colIdx = nodeOrder.indexOf(hoveredCol);
            if (rowIdx >= 0) {
                g.setColor(HIGHLIGHT_COLOR);
                g.fillRect(labelMargin, labelMargin + rowIdx * cellSize, matrixSize, cellSize);
            }
            if (colIdx >= 0) {
                g.setColor(HIGHLIGHT_COLOR);
                g.fillRect(labelMargin + colIdx * cellSize, labelMargin, cellSize, matrixSize);
            }
        }

        // Draw labels if cells are large enough
        if (cellSize >= 8) {
            g.setColor(LABEL_COLOR);
            int fontSize = Math.max(7, Math.min(12, cellSize - 2));
            g.setFont(new Font("SansSerif", Font.PLAIN, fontSize));
            FontMetrics fm = g.getFontMetrics();

            for (int i = 0; i < n; i++) {
                String label = nodeOrder.get(i);
                if (label.length() > 8) label = label.substring(0, 7) + "…";

                // Left labels (row)
                int textWidth = fm.stringWidth(label);
                g.drawString(label, labelMargin - textWidth - 4,
                        labelMargin + i * cellSize + cellSize / 2 + fm.getAscent() / 2);

                // Top labels (column) - rotated
                AffineTransform old = g.getTransform();
                g.rotate(-Math.PI / 2, labelMargin + i * cellSize + cellSize / 2, labelMargin - 4);
                g.drawString(label, labelMargin + i * cellSize + cellSize / 2 - textWidth / 2, labelMargin - 4);
                g.setTransform(old);
            }
        }

        // Legend
        int legendX = labelMargin + matrixSize + 20;
        int legendY = labelMargin;
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(LABEL_COLOR);
        g.drawString("Edge Types", legendX, legendY);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));

        String[][] legend = {
            {"Friend", "f"}, {"Classmate", "c"}, {"Familiar Stranger", "fs"},
            {"Stranger", "s"}, {"Study Group", "sg"}
        };
        for (int i = 0; i < legend.length; i++) {
            int ly = legendY + 20 + i * 22;
            Edge dummy = new Edge(legend[i][1], "", "");
            g.setColor(getEdgeColor(dummy));
            g.fillRect(legendX, ly - 10, 14, 14);
            g.setColor(LABEL_COLOR);
            g.drawString(legend[i][0], legendX + 20, ly + 2);
        }

        // Info
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.setColor(new Color(120, 120, 120));
        g.drawString("Sort: " + sortMode + " | Scroll to zoom | Right-drag to pan", labelMargin, labelMargin + matrixSize + 18);
        g.drawString("Nodes: " + n + " | Edges: " + graph.getEdgeCount(), labelMargin, labelMargin + matrixSize + 32);

        g.translate(-offsetX, -offsetY);
    }

    /**
     * Creates a dialog window containing the heatmap with controls.
     */
    public static JDialog createDialog(JFrame parent, Graph<String, Edge> graph) {
        JDialog dialog = new JDialog(parent, "Adjacency Matrix Heatmap", false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        AdjacencyMatrixHeatmap heatmap = new AdjacencyMatrixHeatmap(graph);

        // Control panel
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.setBackground(new Color(45, 45, 45));

        JLabel sortLabel = new JLabel("Sort by:");
        sortLabel.setForeground(Color.WHITE);
        controls.add(sortLabel);

        String[] sortOptions = {"degree", "name", "community"};
        JComboBox<String> sortBox = new JComboBox<>(sortOptions);
        sortBox.setSelectedItem("degree");
        sortBox.addActionListener(e -> {
            heatmap.sortMode = (String) sortBox.getSelectedItem();
            heatmap.sortNodes();
            heatmap.repaint();
        });
        controls.add(sortBox);

        JButton resetBtn = new JButton("Reset View");
        resetBtn.addActionListener(e -> {
            heatmap.offsetX = 0;
            heatmap.offsetY = 0;
            heatmap.cellSize = 12;
            heatmap.repaint();
        });
        controls.add(resetBtn);

        JButton exportBtn = new JButton("Export PNG");
        exportBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new java.io.File("heatmap.png"));
            if (fc.showSaveDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                try {
                    int w = heatmap.getWidth();
                    int h = heatmap.getHeight();
                    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D ig = img.createGraphics();
                    heatmap.paint(ig);
                    ig.dispose();
                    javax.imageio.ImageIO.write(img, "PNG", fc.getSelectedFile());
                    JOptionPane.showMessageDialog(dialog, "Exported to " + fc.getSelectedFile().getName());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dialog, "Export failed: " + ex.getMessage());
                }
            }
        });
        controls.add(exportBtn);

        dialog.setLayout(new BorderLayout());
        dialog.add(controls, BorderLayout.NORTH);
        dialog.add(new JScrollPane(heatmap), BorderLayout.CENTER);
        dialog.setSize(750, 700);
        dialog.setLocationRelativeTo(parent);

        return dialog;
    }

    /**
     * Refreshes the heatmap with current graph data.
     */
    public void refresh() {
        buildAdjacency();
        sortNodes();
        repaint();
    }
}
