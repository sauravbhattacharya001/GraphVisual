package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.control.DefaultModalGraphMouse;
import edu.uci.ics.jung.visualization.control.ModalGraphMouse;
import edu.uci.ics.screencap.PNGDump;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;

/**
 * Builds the left-hand tool panel for the main application window.
 * <p>
 * Extracted from {@link Main#initializeToolBar()} to reduce the god-class
 * problem described in issue #86. This class is a pure builder — it creates
 * and returns a fully configured {@link JPanel} without holding mutable state.
 * </p>
 *
 * @see Main
 * @see ExportActions
 */
public final class ToolbarBuilder {

    private static final Logger LOGGER = Logger.getLogger(ToolbarBuilder.class.getName());

    /** Callback interface for the host to supply live graph + Edge data. */
    public interface GraphContext {
        /** Current graph (may be null before a file is loaded). */
        Graph<String, Edge> getGraph();

        /** Current visualization viewer. */
        VisualizationViewer<String, Edge> getVisualizationViewer();

        /** Current timestamp label used in export filenames. */
        String getTimestamp();

        /** Collect all edges across every category. */
        List<Edge> collectAllEdges();
    }

    private ToolbarBuilder() { /* utility */ }

    /**
     * Build the tool panel with all buttons wired to the given context.
     *
     * @param owner   parent frame for dialogs
     * @param ctx     live graph context supplier
     * @param legend  legend panel to embed at the bottom
     * @return fully configured tool panel
     */
    public static JPanel build(JFrame owner, GraphContext ctx, JPanel legend) {
        JPanel toolPanel = new JPanel();
        toolPanel.setPreferredSize(new Dimension(150, 610));
        toolPanel.setBackground(Color.WHITE);
        toolPanel.setBorder(BorderFactory.createTitledBorder("Tools"));

        addModeButtons(toolPanel, ctx);
        addSnapshotButton(toolPanel, ctx);
        addEdgelistExportButton(toolPanel);
        addGraphMLExportButton(toolPanel, owner, ctx);
        addDotExportButton(toolPanel, owner, ctx);
        addGexfExportButton(toolPanel, owner, ctx);
        addNodeMetricsExportButton(toolPanel, owner, ctx);
        addHeatmapButton(toolPanel, owner, ctx);
        addDiffHtmlButton(toolPanel, owner, ctx);
        addLayoutCompareButton(toolPanel, owner, ctx);
        addTikzExportButton(toolPanel, owner, ctx);

        if (legend != null) {
            toolPanel.add(legend);
        }

        return toolPanel;
    }

    /* ---- Mode buttons ---- */

    private static void addModeButtons(JPanel panel, GraphContext ctx) {
        JButton pickMode = new JButton(
                "<html><center>Select Node Mode<br/>Use this to<br/> select and<br/> move vertices<br/> at different <br/>position<center></html>");
        pickMode.addActionListener(e -> {
            DefaultModalGraphMouse gm = new DefaultModalGraphMouse();
            gm.setMode(ModalGraphMouse.Mode.PICKING);
            ctx.getVisualizationViewer().setGraphMouse(gm);
        });
        pickMode.setPreferredSize(new Dimension(140, 100));

        JButton transformMode = new JButton(
                "<html><center>Move/rotate graph<br/>Use this to <br/>move and rotate<br/> entire graph</center></html>");
        transformMode.addActionListener(e -> {
            DefaultModalGraphMouse gm = new DefaultModalGraphMouse();
            gm.setMode(ModalGraphMouse.Mode.TRANSFORMING);
            ctx.getVisualizationViewer().setGraphMouse(gm);
        });
        transformMode.setPreferredSize(new Dimension(140, 100));

        panel.add(pickMode);
        panel.add(transformMode);
    }

    /* ---- Snapshot ---- */

    private static void addSnapshotButton(JPanel panel, GraphContext ctx) {
        JButton btn = new JButton(
                "<html><center>Take a snapshot<br/>Use this to<br/> take and image<br/> of the current view<br/> of the graph</center></html>");
        btn.setPreferredSize(new Dimension(140, 100));
        btn.addActionListener(e -> {
            JFileChooser fc;
            int count = 0;
            do {
                if (count != 0) {
                    JOptionPane.showMessageDialog(null,
                            "File with same name already exists!!!", "Error!!", 1);
                }
                count++;
                fc = new JFileChooser(System.getProperty("user.dir"));
                fc.showSaveDialog(null);
            } while (fc.getSelectedFile() != null && fc.getSelectedFile().exists());

            if (fc.getSelectedFile() == null) return;
            File curFile = new File(fc.getSelectedFile().toString() + ".png");
            try {
                curFile.createNewFile();
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
            PNGDump dumper = new PNGDump();
            try {
                dumper.dumpComponent(curFile, ctx.getVisualizationViewer());
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Snapshot failed", ex);
            }
        });
        panel.add(btn);
    }

    /* ---- Edge-list CSV export ---- */

    private static void addEdgelistExportButton(JPanel panel) {
        JButton btn = new JButton(
                "<html><center>Export edgelist<br/>Export the graph<br/> Edge list in<br/> CSV format.</center></html>");
        btn.setPreferredSize(new Dimension(140, 100));
        btn.addActionListener(e -> {
            JFileChooser fc;
            int count = 0;
            do {
                if (count != 0) {
                    JOptionPane.showMessageDialog(null,
                            "File with same name already exists!!!", "Error!!", 1);
                }
                count++;
                fc = new JFileChooser(System.getProperty("user.dir"));
                fc.showSaveDialog(null);
            } while (fc.getSelectedFile() != null && fc.getSelectedFile().exists());

            if (fc.getSelectedFile() == null) return;
            try {
                fc.getSelectedFile().createNewFile();
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
            try {
                FileUtils.copyFile(new File("./graph.txt"), fc.getSelectedFile());
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        });
        panel.add(btn);
    }

    /* ---- GraphML export ---- */

    private static void addGraphMLExportButton(JPanel panel, JFrame owner, GraphContext ctx) {
        ExportActions.addExportButton(panel, owner,
                "<html><center>Export GraphML<br/>Export to GraphML<br/> for Gephi,<br/> Cytoscape, yEd,<br/> NetworkX</center></html>",
                "Export as GraphML",
                () -> "graph_" + ctx.getTimestamp() + ".graphml",
                new String[]{".graphml"},
                outFile -> {
                    GraphMLExporter exporter = new GraphMLExporter(ctx.getGraph(), ctx.collectAllEdges());
                    exporter.setTimestamp(ctx.getTimestamp());
                    exporter.setDescription("GraphVisual network \u2014 student community evolution");
                    exporter.export(outFile);
                    return "GraphML exported successfully!\n"
                            + "Nodes: " + exporter.getVertexCount() + "\n"
                            + "Edges: " + exporter.getEdgeCount() + "\n"
                            + "File: " + outFile.getName();
                });
    }

    /* ---- DOT export ---- */

    private static void addDotExportButton(JPanel panel, JFrame owner, GraphContext ctx) {
        ExportActions.addExportButton(panel, owner,
                "<html><center>Export DOT<br/>Graphviz DOT<br/>format for dot,<br/>neato, fdp,<br/>viz-js.com</center></html>",
                "Export as Graphviz DOT",
                () -> "graph_" + ctx.getTimestamp() + ".dot",
                new String[]{".dot", ".gv"},
                outFile -> {
                    DotExporter dotExporter = new DotExporter(ctx.getGraph());
                    dotExporter.setGraphName("StudentNetwork");
                    dotExporter.setTimestamp(ctx.getTimestamp());
                    dotExporter.setDescription("GraphVisual network \u2014 student community evolution");
                    dotExporter.export(outFile);
                    return "DOT file exported successfully!\n"
                            + "Nodes: " + ctx.getGraph().getVertexCount() + "\n"
                            + "Edges: " + ctx.getGraph().getEdgeCount() + "\n"
                            + "File: " + outFile.getName() + "\n\n"
                            + "Render with: dot -Tpng " + outFile.getName() + " -o output.png\n"
                            + "Or paste into https://viz-js.com";
                });
    }

    /* ---- GEXF export ---- */

    private static void addGexfExportButton(JPanel panel, JFrame owner, GraphContext ctx) {
        ExportActions.addExportButton(panel, owner,
                "<html><center>Export GEXF<br/>Gephi native<br/>format with<br/>dynamic/temporal<br/>support</center></html>",
                "Export as GEXF (Gephi)",
                () -> "graph_" + ctx.getTimestamp() + ".gexf",
                new String[]{".gexf"},
                outFile -> {
                    GexfExporter gexfExporter = new GexfExporter(ctx.getGraph(), ctx.collectAllEdges());
                    gexfExporter.setDescription("GraphVisual network \u2014 student community evolution");
                    gexfExporter.export(outFile);
                    return "GEXF exported successfully!\n"
                            + "Nodes: " + ctx.getGraph().getVertexCount() + "\n"
                            + "Edges: " + ctx.getGraph().getEdgeCount() + "\n"
                            + "File: " + outFile.getName() + "\n\n"
                            + "Open in Gephi for advanced visualization and analysis.";
                });
    }

    /* ---- Node metrics CSV ---- */

    private static void addNodeMetricsExportButton(JPanel panel, JFrame owner, GraphContext ctx) {
        ExportActions.addExportButton(panel, owner,
                "<html><center>Node Metrics<br/>CSV Report<br/>Degree, centrality,<br/>community, clustering<br/>per node</center></html>",
                "Export Node Metrics CSV",
                () -> "node_metrics_" + ctx.getTimestamp() + ".csv",
                new String[]{".csv"},
                outFile -> {
                    CsvReportExporter exporter = new CsvReportExporter(ctx.getGraph(), ctx.collectAllEdges());
                    exporter.setTimestamp(ctx.getTimestamp());
                    exporter.export(outFile);
                    return "Node metrics CSV exported!\n"
                            + "Nodes: " + ctx.getGraph().getVertexCount() + "\n"
                            + "Columns: 13 metrics per node\n"
                            + "File: " + outFile.getName();
                });
    }

    /* ---- Adjacency heatmap ---- */

    private static void addHeatmapButton(JPanel panel, JFrame owner, GraphContext ctx) {
        JButton btn = new JButton(
                "<html><center>Adjacency Matrix<br/>View graph as a<br/> color-coded<br/> heatmap matrix<br/> with zoom/pan</center></html>");
        btn.setPreferredSize(new Dimension(140, 100));
        btn.addActionListener(e -> {
            JDialog dlg = AdjacencyMatrixHeatmap.createDialog(owner, ctx.getGraph());
            dlg.setVisible(true);
        });
        panel.add(btn);
    }

    /* ---- Diff HTML ---- */

    private static void addDiffHtmlButton(JPanel panel, JFrame owner, GraphContext ctx) {
        JButton btn = new JButton(
                "<html><center>Diff HTML<br/>Compare two<br/>graph snapshots<br/>in interactive<br/>HTML diff view</center></html>");
        btn.setPreferredSize(new Dimension(140, 100));
        btn.addActionListener(e -> {
            Graph<String, Edge> g = ctx.getGraph();
            if (g == null || g.getVertexCount() == 0) {
                JOptionPane.showMessageDialog(owner, "Load a graph first.", "No Graph",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            JFileChooser fc = new JFileChooser(System.getProperty("user.dir"));
            fc.setDialogTitle("Select second graph file to compare");
            if (fc.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) return;
            try {
                GraphFileParser.ParseResult parseResult =
                        GraphFileParser.parse(fc.getSelectedFile().getAbsolutePath());
                Graph<String, Edge> graphB = parseResult.getGraph();
                GraphDiffHtmlExporter exporter = new GraphDiffHtmlExporter(g, graphB);
                exporter.setTitle("Graph Diff: current vs " + fc.getSelectedFile().getName());
                exporter.setLabelA("Current Graph");
                exporter.setLabelB(fc.getSelectedFile().getName());
                exporter.setDarkMode(true);
                File outFile = ExportActions.showExportSaveDialog(owner, "Export Diff HTML",
                        "graph_diff_" + ctx.getTimestamp() + ".html", ".html");
                if (outFile != null) {
                    exporter.export(outFile);
                    JOptionPane.showMessageDialog(owner,
                            "Diff visualization exported!\nFile: " + outFile.getName(),
                            "Export Complete", JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(owner,
                        "Failed to generate diff: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        panel.add(btn);
    }

    /* ---- TikZ/LaTeX export ---- */

    private static void addTikzExportButton(JPanel panel, JFrame owner, GraphContext ctx) {
        ExportActions.addExportButton(panel, owner,
                "<html><center>Export TikZ<br/>LaTeX source<br/>for academic<br/>papers &amp;<br/>presentations</center></html>",
                "Export as TikZ/LaTeX",
                () -> "graph_" + ctx.getTimestamp() + ".tex",
                new String[]{".tex"},
                outFile -> {
                    TikzExporter tikz = new TikzExporter(ctx.getGraph());
                    tikz.setTitle("Network Graph \u2014 " + ctx.getTimestamp());
                    tikz.export(outFile);
                    return "TikZ/LaTeX exported!\n"
                            + "Nodes: " + ctx.getGraph().getVertexCount() + "\n"
                            + "Edges: " + ctx.getGraph().getEdgeCount() + "\n"
                            + "File: " + outFile.getName() + "\n\n"
                            + "Compile with: pdflatex " + outFile.getName();
                });
    }

    /* ---- Layout comparison ---- */

    private static void addLayoutCompareButton(JPanel panel, JFrame owner, GraphContext ctx) {
        ExportActions.addExportButton(panel, owner,
                "<html><center>Layout Compare<br/>Compare 4 layout<br/>algorithms for<br/>the same graph<br/>in one HTML page</center></html>",
                "Export Layout Comparison HTML",
                () -> "layout_compare_" + ctx.getTimestamp() + ".html",
                new String[]{".html"},
                outFile -> {
                    GraphLayoutComparer comparer = new GraphLayoutComparer(ctx.getGraph());
                    comparer.setTitle("Layout Comparison \u2014 " + ctx.getTimestamp());
                    comparer.export(outFile);
                    return "Layout comparison exported!\n"
                            + "Nodes: " + ctx.getGraph().getVertexCount() + "\n"
                            + "Edges: " + ctx.getGraph().getEdgeCount() + "\n"
                            + "Layouts: Force-Directed, Circular, Grid, Radial\n"
                            + "File: " + outFile.getName() + "\n\n"
                            + "Open in any browser. Hover a node to highlight it across all layouts.";
                });
    }
}
