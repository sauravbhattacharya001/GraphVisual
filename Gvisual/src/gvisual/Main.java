package gvisual;

import app.Network;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.algorithms.layout.StaticLayout;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.screencap.PNGDump;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeListener;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.collections15.Transformer;
import org.xml.sax.SAXException;

/**
 * Main application frame for GraphVisual - an interactive social network
 * visualisation and analysis tool built on JUNG (Java Universal Network/Graph).
 *
 * <h3>Overview</h3>
 * <p>Loads Edge-list data (from flat files or a database via {@link app.Network}),
 * builds an {@link edu.uci.ics.jung.graph.UndirectedSparseGraph}, and renders it
 * in a Swing {@link edu.uci.ics.jung.visualization.VisualizationViewer} with
 * interactive controls for filtering, layout, analysis, and export.</p>
 *
 * <h3>Key Features</h3>
 * <ul>
 *   <li><b>Edge-type filtering:</b> toggle visibility of friend, classmate,
 *       familiar-stranger, stranger, and study-group edges via checkboxes
 *       and duration/frequency threshold sliders.</li>
 *   <li><b>Timeline playback:</b> animate the graph through temporal snapshots
 *       with play/pause/step controls and adjustable speed.</li>
 *   <li><b>Layout engines:</b> force-directed (default), circular
 *       ({@link CircularLayout}), and hierarchical ({@link HierarchicalLayout}).</li>
 *   <li><b>Graph algorithms:</b> shortest path (hop/weight), minimum spanning tree,
 *       community detection (Louvain), articulation points/bridges, ego-network
 *       extraction, PageRank, centrality, and many more via the analysis menu.</li>
 *   <li><b>Export:</b> GraphML, DOT, GEXF, SVG, PNG, CSV Edge-list, interactive HTML,
 *       and network reports through {@link ExportActions} and {@link ToolbarBuilder}.</li>
 * </ul>
 *
 * <h3>Architecture</h3>
 * <p>Rendering logic is delegated to {@link GraphRenderers}, statistics to
 * {@link StatsPanel}, toolbar construction to {@link ToolbarBuilder}, and
 * export actions to {@link ExportActions}. Overlay controllers
 * ({@link ArticulationPanelController}, {@link CentralityPanelController},
 * {@link EgoPanelController}, {@link ResiliencePanelController}) manage
 * their respective analysis panels and state.</p>
 *
 * @author Saurav Bhattacharya
 * @see GraphRenderers
 * @see ToolbarBuilder
 * @see ExportActions
 * @see StatsPanel
 */
public class Main extends JFrame {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final Color DEFAULT_BG_COLOR = Color.BLACK;
    private static final Color Vertex_COLOR = Color.WHITE;
    private static int DELAY = 2048;
    private JSlider friendDurThreshold;
    private JSlider friendNumMeetThreshold;
    private JSlider classmateDurThreshold;
    private JSlider classmateNumMeetThreshold;
    private JSlider fsDurThreshold;
    private JSlider fsNumMeetThreshold;
    private JSlider strangerDurThreshold;
    private JSlider strangerNumMeetThreshold;
    private JSlider studyGDurThreshold;
    private JSlider studyGNumMeetThreshold;
    private String month;
    private String date;
    private String timeStamp;
    private Graph<String, Edge> g;
    private VisualizationViewer<String, Edge> vv;
    private Layout<String, Edge> graphLayout;
    private final GraphRenderers renderers = new GraphRenderers();

    /**
     * Push current overlay state to the GraphRenderers instance so that
     * transformers see up-to-date values on every render pass.
     *
     * <p>Each controller is queried only once per field via local variables,
     * improving readability and eliminating the repeated null-check chains
     * that previously made this method hard to read and maintain.</p>
     */
    private void syncRenderers() {
        renderers.setGraph(g);

        // Path overlay
        if (pathController != null) {
            renderers.setPathState(
                    pathController.getPathEdges(),
                    pathController.getPathVertices(),
                    pathController.getPathSource(),
                    pathController.getPathTarget());
        } else {
            renderers.setPathState(
                    java.util.Collections.emptySet(),
                    java.util.Collections.emptySet(),
                    null, null);
        }

        // MST overlay
        boolean mstActive = mstController != null && mstController.isOverlayActive();
        renderers.setMstState(mstActive,
                mstController != null ? mstController.getMstEdges()
                                      : java.util.Collections.emptySet());

        // Community overlay
        boolean communityActive = communityController != null
                && communityController.isOverlayActive();
        renderers.setCommunityState(communityActive,
                communityController != null ? communityController.getNodeCommunityMap()
                                            : null);

        // Articulation point overlay
        boolean artActive = articulationController != null
                && articulationController.isOverlayActive();
        renderers.setArticulationState(artActive,
                articulationController != null ? articulationController.getArticulationPoints()
                                              : java.util.Collections.emptySet(),
                articulationController != null ? articulationController.getBridgeEdges()
                                              : java.util.Collections.emptySet());

        // Ego network overlay
        renderers.setEgoState(
                egoController.isOverlayActive(),
                egoController.getCenter(),
                egoController.getNeighbors(),
                egoController.getEdges());

        renderers.setOldVertices(OldVertices);
    }
    private List<Edge> friendEdges = new ArrayList<>();
    private List<Edge> fsEdges = new ArrayList<>();
    private List<Edge> classmateEdges = new ArrayList<>();
    private List<Edge> strangerEdges = new ArrayList<>();
    private List<Edge> studyGEdges = new ArrayList<>();
    private String fileName;
    private Box parameterSpace;
    private JPanel notesPanel;
    private JPanel imagePanel;
    private JMenuBar menubar;
    private JSlider timeline;
    private JPanel contentPanel;
    private JPanel toolPanel;
    private JCheckBox showFriend;
    private JCheckBox showClassmate;
    private JCheckBox showFS;
    private JCheckBox showStranger;
    private JCheckBox showStudy;
    private Timer timer;
    private JButton playButton;
    private JButton stopButton;
    private JButton pauseButton;
    private JButton frShowParam;
    private JButton fsShowParam;
    private JButton cShowParam;
    private JButton sShowParam;
    private JButton sgShowParam;
    private Box[] categoryPanel;
    private JPanel frHpanel;
    private JPanel fsHpanel;
    private JPanel cHpanel;
    private JPanel sHpanel;
    private JPanel sgHpanel;
    private int NUM_EDGES_IMP_GRAPH = 20;
    private JButton prevButton;
    private JButton nextButton;
    private JButton slowButton;
    private JButton fastButton;
    private Collection<String> OldVertices;
    private int prevTimeline;
    private JPanel legendPanel;
    private StatsPanel statsPanel;

    // --- Shortest path (delegated to controller) ---
    private PathPanelController pathController;

    // --- MST (delegated to controller) ---
    private MSTPanelController mstController;

    // --- Centrality analysis (delegated to controller) ---
    private CentralityPanelController centralityController;

    // --- Community detection (delegated to controller) ---
    private CommunityPanelController communityController;

    // --- Articulation point analysis (delegated to controller) ---
    private ArticulationPanelController articulationController;

    // --- Resilience analysis (delegated to controller) ---
    private ResiliencePanelController resilienceController;

    // --- Ego network (delegated to controller) ---
    private EgoPanelController egoController;

    // Community colors moved to CommunityPanelController

    /**
     * Constructor
     * @throws FileNotFoundException
     * @throws Exception
     */
    public Main() throws FileNotFoundException, Exception {

        initializeContentPanel();
        initializeLegendSpace();
        initializeStatsPanel();
        initializePathController();
        initializeCommunityController();
        initializeMSTController();
        initializeCentralityPanel();
        initializeArticulationPanel();
        resilienceController = new ResiliencePanelController(() -> g, this);
        egoController = new EgoPanelController(() -> g, () -> { syncRenderers(); vv.repaint(); });
        initializeTimeLine();
        initializeToolBar();
        initializeParameterSpace();
        initializeImagePanel();
        initializeNotesSpace();
        setJMenuBar(menubar);
        showRightPane();


        updateTime();
        addGraph();

        setTitle("Visualization");
        setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);


    }

    /**
     * Returns the Edge list for the given Edge type.
     * Used to replace the cascading if/else chain in addGraph().
     */
    private List<Edge> getEdgeList(EdgeType type) {
        switch (type) {
            case FRIEND:      return friendEdges;
            case CLASSMATE:   return classmateEdges;
            case FAMILIAR:    return fsEdges;
            case STRANGER:    return strangerEdges;
            case STUDY_GROUP: return studyGEdges;
            default:          return null;
        }
    }

    /**
     * Returns whether the given Edge type code is currently visible
     * (its checkbox is selected).
     */
    private boolean isEdgeTypeVisible(String typeCode) {
        EdgeType type = EdgeType.fromCode(typeCode);
        if (type == null) return true; // unknown types are visible by default
        switch (type) {
            case FRIEND:      return showFriend.isSelected();
            case CLASSMATE:   return showClassmate.isSelected();
            case FAMILIAR:    return showFS.isSelected();
            case STRANGER:    return showStranger.isSelected();
            case STUDY_GROUP: return showStudy.isSelected();
            default:          return true;
        }
    }

    /**
     * Create the layout for the graph
     */
    public void createLayout() {
        graphLayout = new StaticLayout<String, Edge>(g);
        List<List<String>> clusters = new ArrayList<>();

        for (int i = 0; i < 9; i++) {
            clusters.add(new ArrayList<>());
        }

        for (String x : g.getVertices()) {
            boolean isF = false;
            boolean isFs = false;
            boolean isC = false;
            boolean isS = false;
            for (Edge y : g.getOutEdges(x)) {
                EdgeType type = EdgeType.fromCode(y.getType());
                if (type != null) {
                    switch (type) {
                        case FRIEND:      isF = true;  break;
                        case FAMILIAR:    isFs = true; break;
                        case CLASSMATE:   isC = true;  break;
                        case STRANGER:    isS = true;  break;
                        case STUDY_GROUP: break; // excluded from clustering
                    }
                }
            }

            int areaId = EdgeType.clusterIdFor(isF, isFs, isC, isS);
            clusters.get(areaId).add(x);
        }

        for (int i = 0; i < 9; i++) {
            positionCluster(clusters.get(i), i / 3, i % 3);
        }
    }

    /**
     * Positions a given set of vertices at certain location, but at random positions in that area
     * @param vertices list of vertices to cluster
     * @param y y-position of cluster
     * @param x x-position of cluster
     */
    /**
     * Positions a cluster of vertices in a random walk within a grid cell.
     *
     * <p>Each cluster cell is 300×200 pixels. Vertices are placed via a
     * deterministic random walk (seed=42) with step sizes in [5, 44] in
     * each axis, randomly signed.</p>
     *
     * @param vertices list of vertices to position
     * @param row      grid row (0-based)
     * @param col      grid column (0-based)
     */
    public void positionCluster(List<String> vertices, int row, int col) {
        int curX = col * 300 + 150;
        int curY = row * 200 + 100;
        Random generator = new Random(42);

        for (String v : vertices) {
            graphLayout.setLocation(v, new java.awt.Point(curX, curY));

            // Random walk: step ∈ [5, 44], sign ∈ {-1, +1}
            int stepX = 5 + generator.nextInt(40);
            int stepY = 5 + generator.nextInt(40);
            curX += generator.nextBoolean() ? stepX : -stepX;
            curY += generator.nextBoolean() ? stepY : -stepY;
        }
    }



    /**
     * updates the timestamp of the currently selected graph.
     *
     * The timeline slider value (1..92) maps to calendar dates
     * March 1 â€" May 31, 2011.  March has 31 days, April has 30,
     * May has 31.
     */
    public void updateTime() {
        int day = timeline.getValue();  // 1..92

        if (day <= 31) {
            // March: days 1â€"31
            month = "03";
        } else if (day <= 61) {
            // April: days 32â€"61 â†' April 1â€"30
            month = "04";
            day = day - 31;
        } else {
            // May: days 62â€"92 â†' May 1â€"31
            month = "05";
            day = day - 61;
        }

        date = (day < 10) ? ("0" + day) : Integer.toString(day);
        timeStamp = "2011-" + month + "-" + date;
    }

    /**
     * Creates a ChangeListener that refreshes the graph when the slider
     * stops adjusting.  Replaces the identical anonymous listener that was
     * duplicated 10+ times across the category sliders and timeline.
     *
     * @param slider the slider whose {@code getValueIsAdjusting()} is checked
     * @return a reusable ChangeListener
     */
    private ChangeListener createGraphRefreshListener(final JSlider slider) {
        return e -> {
            if (!slider.getValueIsAdjusting()) {
                try {
                    addGraph();
                } catch (ParserConfigurationException | IOException | SAXException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
        };
    }

    /**
     * Holds the UI components for a single Edge-type category row.
     */
    private static class CategoryRow {
        final JCheckBox checkbox;
        final JPanel headerPanel;
        final JSlider durationSlider;
        final JSlider meetingSlider;
        final JButton settingsButton;
        boolean showParams;

        CategoryRow(JCheckBox checkbox, JPanel headerPanel,
                    JSlider durationSlider, JSlider meetingSlider,
                    JButton settingsButton) {
            this.checkbox = checkbox;
            this.headerPanel = headerPanel;
            this.durationSlider = durationSlider;
            this.meetingSlider = meetingSlider;
            this.settingsButton = settingsButton;
            this.showParams = false;
        }
    }

    /** All five category rows, indexed by EdgeType ordinal. */
    private CategoryRow[] categoryRows;

    /**
     * Creates a fully-wired category row (checkbox + label + sliders + settings
     * button) for the given Edge type.  This replaces the five near-identical
     * blocks that previously lived in {@code initializeCategoryPanel()}.
     *
     * @param type        the Edge type this row controls
     * @param edgeList    the corresponding Edge list (e.g. {@code friendEdges})
     * @param labelText   the display text for the label
     * @param durMax      maximum value for the duration slider
     * @return a fully-initialised {@code CategoryRow}
     */
    private CategoryRow createCategoryRow(final EdgeType type,
                                          final List<Edge> edgeList,
                                          String labelText,
                                          int durMax) {
        JButton settingsBtn = new JButton(new ImageIcon("./images/settings.png"));
        settingsBtn.setBorder(null);

        final JCheckBox cb = new JCheckBox();
        cb.setSelected(true);
        cb.addActionListener(e -> {
            if (cb.isSelected()) {
                for (Edge x : edgeList) { g.addEdge(x, x.getVertex1(), x.getVertex2()); }
            } else {
                for (Edge x : edgeList) { g.removeEdge(x); }
            }
            imagePanel.setVisible(false);
            imagePanel.setVisible(true);
        });

        JPanel header = new JPanel();
        JLabel label = new JLabel(labelText, JLabel.CENTER);
        label.setForeground(type.getColor());
        label.setFont(new Font("SANS_SERIF", 0, 14));
        header.add(cb);
        header.add(label);
        header.add(settingsBtn);

        JSlider durSlider = new JSlider(0, durMax, type.getDefaultDurationThreshold());
        JSlider meetSlider = new JSlider(0, 5, type.getDefaultMeetingThreshold());
        durSlider.setBorder(BorderFactory.createTitledBorder("Duration of meeting (min)"));
        meetSlider.setBorder(BorderFactory.createTitledBorder("Number of meetings in a day"));

        int durMajor = durMax <= 25 ? 5 : 10;
        durSlider.setMajorTickSpacing(durMajor);
        durSlider.setMinorTickSpacing(1);
        meetSlider.setMajorTickSpacing(1);
        meetSlider.setMinorTickSpacing(1);
        durSlider.setPaintTicks(true);
        durSlider.setPaintLabels(true);
        meetSlider.setPaintTicks(true);
        meetSlider.setPaintLabels(true);

        durSlider.addChangeListener(createGraphRefreshListener(durSlider));
        meetSlider.addChangeListener(createGraphRefreshListener(meetSlider));

        final CategoryRow row = new CategoryRow(cb, header, durSlider, meetSlider, settingsBtn);

        settingsBtn.addActionListener(e -> {
            row.showParams = !row.showParams;
            paintCategoryPanel();
        });

        return row;
    }

    /**
     * Advances (or rewinds) the timeline to the next date whose generated
     * graph file contains more than {@link #NUM_EDGES_IMP_GRAPH} lines,
     * indicating a "significant" snapshot worth displaying.
     *
     * <p>Bounded to at most 92 iterations (the full timeline range) to
     * guarantee termination even when no qualifying date exists between
     * the current position and the timeline boundary.
     *
     * @param direction {@code "next"} to advance, {@code "prev"} to rewind
     * @throws Exception if file generation or graph parsing fails
     */
    public void nextOrPrevGraph(String direction) throws Exception {
        final boolean forward = "next".equals(direction);
        final int maxSteps = 92; // timeline spans days 1–92

        for (int step = 0; step < maxSteps; step++) {
            // Boundary check — can't go past the ends of the timeline
            if (forward  && month.equals("05") && date.equals("31")) return;
            if (!forward && month.equals("03") && date.equals("01")) return;

            // Advance/rewind the slider by one day
            timeline.setValue(timeline.getValue() + (forward ? 1 : -1));
            updateTime();

            // Generate the graph file for the new date
            fileName = "./graph.txt";
            Network.generateFile(fileName, month, date,
                    friendDurThreshold.getValue(), friendNumMeetThreshold.getValue(),
                    fsDurThreshold.getValue(), fsNumMeetThreshold.getValue(),
                    classmateDurThreshold.getValue(), classmateNumMeetThreshold.getValue(),
                    strangerDurThreshold.getValue(), strangerNumMeetThreshold.getValue(),
                    studyGDurThreshold.getValue(), studyGNumMeetThreshold.getValue());

            // Use GraphFileParser to count edges — avoids a redundant manual
            // line-by-line scan that addGraph() would repeat moments later.
            GraphFileParser.ParseResult probe = GraphFileParser.parse(fileName, t -> true);
            if (probe.getGraph().getEdgeCount() > NUM_EDGES_IMP_GRAPH) {
                addGraph();
                return;
            }
        }

        // No qualifying date found — stay on the current position and
        // refresh the display so the UI is consistent with the slider.
        LOGGER.info("nextOrPrevGraph: no important graph found within "
                + maxSteps + " steps (" + direction + ")");
        addGraph();
    }

    /**
     * refresh the graph
     * @throws ParserConfigurationException
     * @throws IOException
     * @throws SAXException
     */
    public void addGraph() throws ParserConfigurationException, IOException, SAXException {

        fileName = "./graph.txt";
        try {
            imagePanel.removeAll();
            imagePanel.repaint();

            timeline.setBorder(BorderFactory.createTitledBorder(timeStamp));
            Network.generateFile(fileName, month, date, friendDurThreshold.getValue(), friendNumMeetThreshold.getValue(), fsDurThreshold.getValue(), fsNumMeetThreshold.getValue(), classmateDurThreshold.getValue(), classmateNumMeetThreshold.getValue(), strangerDurThreshold.getValue(), strangerNumMeetThreshold.getValue(), studyGDurThreshold.getValue(), studyGNumMeetThreshold.getValue());


        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }



        if (g != null && prevTimeline != timeline.getValue() && g.getEdgeCount() != 0) {
            OldVertices = g.getVertices();
            prevTimeline = timeline.getValue();
        }


        // Parse graph file using extracted parser (separates I/O from UI)
        GraphFileParser.ParseResult parseResult = GraphFileParser.parse(
                fileName, this::isEdgeTypeVisible);

        g = parseResult.getGraph();

        // Populate classified Edge lists from parse result
        for (EdgeType type : EdgeType.values()) {
            List<Edge> list = getEdgeList(type);
            if (list != null) {
                list.clear();
                list.addAll(parseResult.getEdges(type));
            }
        }

        createLayout();
        vv = new VisualizationViewer<String, Edge>(graphLayout);
        vv.setSize(new Dimension(100, 0));

        DefaultModalGraphMouse gm = new DefaultModalGraphMouse();

        gm.setMode(ModalGraphMouse.Mode.TRANSFORMING);
        vv.setGraphMouse(gm);


        Transformer<Edge, String> edgeLabel = (Edge i) -> {
                return i.getLabel();
            };

        vv.getRenderContext().setEdgeLabelTransformer(edgeLabel);
        vv.setBackground(DEFAULT_BG_COLOR);
        Transformer<String, String> vertexLabel = (String i) -> {
                return i;
            };
        vv.setForeground(Color.white);
        vv.getRenderContext().setVertexLabelTransformer(vertexLabel);


        // Sync overlay state and set up rendering transformers
        syncRenderers();

        vv.getRenderContext().setEdgeDrawPaintTransformer(renderers.edgePaintTransformer());
        vv.getRenderContext().setVertexFillPaintTransformer(renderers.vertexPaintTransformer());
        vv.getRenderContext().setVertexShapeTransformer(renderers.vertexShapeTransformer());
        vv.getRenderContext().setEdgeStrokeTransformer(renderers.edgeStrokeTransformer());

        imagePanel.add(vv);

        imagePanel.setVisible(false);
        imagePanel.setVisible(true);

        updateStatsPanel();

        LOGGER.fine("Graph added");
    }

    /**
     * initialize the Legend Space
     */
    /**
     * Builds the legend panel by iterating over {@link EdgeType} values,
     * replacing the previous 50-line block of duplicated icon/label code.
     */
    public final void initializeLegendSpace() {
        legendPanel = new JPanel();

        JLabel legendHeading = new JLabel("Legend for the Graph");
        Box legendBox = Box.createVerticalBox();

        for (EdgeType type : EdgeType.values()) {
            Box row = Box.createHorizontalBox();
            row.add(new JLabel(new ImageIcon(type.getLegendIconPath())));
            row.add(new JLabel(type.getDisplayLabel()));
            legendBox.add(row);
        }

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                legendHeading, legendBox);
        legendPanel.add(splitPane);
    }

    /**
     * Initializes the shortest path finder panel with source/target selection,
     * path mode toggle, and result display.
     */
    /**
     * Initializes the path-finding panel controller.
     */
    public final void initializePathController() {
        pathController = new PathPanelController(new PathPanelController.GraphHost() {
            @Override public Graph<String, Edge> getGraph() { return g; }
            @Override public edu.uci.ics.jung.algorithms.layout.Layout<String, Edge> getLayout() { return graphLayout; }
            @Override public VisualizationViewer<String, Edge> getViewer() { return vv; }
            @Override public void refreshGraph() { Main.this.refreshGraph(); }
        });
    }

    /**
     * Refreshes the graph visualization to show/hide overlays.
     */
    private void refreshGraph() {
        syncRenderers();
        if (imagePanel != null) {
            imagePanel.setVisible(false);
            imagePanel.setVisible(true);
        }
    }

    /**
     * Initializes the community detection controller.
     */
    public final void initializeCommunityController() {
        communityController = new CommunityPanelController(new CommunityPanelController.GraphHost() {
            @Override public Graph<String, Edge> getGraph() { return g; }
            @Override public void onOverlayChanged() { syncRenderers(); refreshGraph(); }
        });
    }

    /**
     * Returns a human-readable label for Edge type codes.
     */
    private String getDominantLabel(String typeCode) {
        EdgeType type = EdgeType.fromCode(typeCode);
        return type != null ? type.getDisplayLabel() : typeCode;
    }

    /**
     * Initializes the MST controller.
     */
    public final void initializeMSTController() {
        mstController = new MSTPanelController(new MSTPanelController.GraphHost() {
            @Override public Graph<String, Edge> getGraph() { return g; }
            @Override public void onOverlayChanged() { syncRenderers(); refreshGraph(); }
        });
    }

    public final void initializeCentralityPanel() {
        centralityController = new CentralityPanelController(() -> g);
    }


    public final void initializeArticulationPanel() {
        articulationController = new ArticulationPanelController(
                () -> g, () -> { syncRenderers(); if (vv != null) vv.repaint(); });
    }


    /**
     * Initializes the network statistics panel.
     * Delegates to the extracted {@link StatsPanel} component.
     */
    public final void initializeStatsPanel() {
        statsPanel = new StatsPanel();
    }

    /**
     * Updates the statistics panel with current graph metrics.
     */
    private void updateStatsPanel() {
        if (statsPanel == null || g == null) return;

        GraphStats stats = new GraphStats(g, friendEdges, fsEdges,
                classmateEdges, strangerEdges, studyGEdges);
        statsPanel.update(stats);
    }


    /**
     * creates the right pane containing the communities and notes section
     */
    /**
     * creates the right pane containing the communities and notes section.
     *
     * <p>Uses {@link #chainSplitPanes} to avoid deeply nested manual
     * JSplitPane construction â€" adding/removing panels now requires
     * only editing the array and heights, not restructuring nesting.</p>
     */
    public final void showRightPane() {

        JLabel parameterHeading = new JLabel("Communities", JLabel.CENTER);
        parameterHeading.setPreferredSize(new Dimension(300, 30));
        JSplitPane headerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                parameterHeading, parameterSpace);

        // Panels in display order, with their preferred divider heights.
        // To add a new panel, just add an entry here â€" no nesting changes needed.
        java.awt.Component[] panels = {
            headerSplit,
            notesPanel,
            pathController.getPanel(),
            communityController.getPanel(),
            mstController.getPanel(),
            centralityController.getPanel(),
            articulationController.getPanel(),
            resilienceController.getPanel(),
            egoController.getPanel(),
            statsPanel,
        };
        int[] dividerLocations = { 400, 510, 640, 760, 920, 1070, 1250, 1420, 1580 };

        JSplitPane root = chainSplitPanes(panels, dividerLocations);
        add(root, BorderLayout.EAST);
    }

    /**
     * Chains an array of components into nested vertical JSplitPanes.
     *
     * <p>Given components [A, B, C, D], produces:
     * <pre>
     *   Split(Split(Split(A, B), C), D)
     * </pre>
     * with divider locations applied in order.</p>
     *
     * @param components       the panels to chain (at least 2)
     * @param dividerLocations divider positions; length must be components.length - 1
     * @return the outermost JSplitPane
     */
    private static JSplitPane chainSplitPanes(java.awt.Component[] components, int[] dividerLocations) {
        if (components.length < 2) {
            throw new IllegalArgumentException("Need at least 2 components to chain");
        }
        if (dividerLocations.length != components.length - 1) {
            throw new IllegalArgumentException("Need exactly (components.length - 1) divider locations");
        }

        JSplitPane current = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                components[0], components[1]);
        current.setDividerLocation(dividerLocations[0]);

        for (int i = 2; i < components.length; i++) {
            JSplitPane next = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                    current, components[i]);
            next.setDividerLocation(dividerLocations[i - 1]);
            current = next;
        }

        return current;
    }

    /**
     * creates the content panel which has the image panel, toolbar, and timeline;
     */
    public final void initializeContentPanel() {
        contentPanel = new JPanel();
        add(contentPanel, BorderLayout.WEST);
        contentPanel.setPreferredSize(new Dimension(1000, 700));
    }

    /**
     * initialize the image panel
     */
    public final void initializeImagePanel() {
        imagePanel = new JPanel(new GridLayout(1, 1));
        imagePanel.setBackground(DEFAULT_BG_COLOR);
        imagePanel.setPreferredSize(new Dimension(820, 610));
        contentPanel.add(imagePanel, BorderLayout.NORTH);
    }

    /**
     * initialize the timeline
     */
    public final void initializeTimeLine() {

        ActionListener taskPerformer = evt -> {
                int i = timeline.getValue();
                i++;
                timeline.setValue(i);
            };
        timer = new Timer(DELAY, taskPerformer);

        timeline = new JSlider(SwingConstants.HORIZONTAL, 1, 92, 1);
        timeline.setName("timeline");
        timeline.setMajorTickSpacing(10);
        timeline.setMinorTickSpacing(1);
        timeline.setPaintTicks(true);
        //timeline.setPaintLabels(true);


        //timeline.setBorder(BorderFactory.createTitledBorder("Timeline"));
        timeline.setPreferredSize(new Dimension(600, 80));




        timeline.addChangeListener(e -> {
                updateTime();
                timeline.setBorder(BorderFactory.createTitledBorder(timeStamp));
                if (!timeline.getValueIsAdjusting()) {
                    try {
                        addGraph();
                    } catch (ParserConfigurationException | IOException | SAXException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                }

            });




        playButton  = createTimelineButton("images/play.png",  "Play",  e -> {
            LOGGER.fine("Play pressed");
            timer.start();
        });
        pauseButton = createTimelineButton("images/pause.png", "Pause", e -> {
            LOGGER.fine("Pause pressed");
            timer.stop();
        });
        stopButton  = createTimelineButton("images/stop.png",  "Stop",  e -> {
            LOGGER.fine("Stop pressed");
            timeline.setValue(1);
            timer.stop();
        });
        prevButton  = createTimelineButton("images/prev.png",  "Previous important graph", e -> {
            try {
                LOGGER.fine("Prev pressed");
                nextOrPrevGraph("prev");
                timer.stop();
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        });
        nextButton  = createTimelineButton("images/next.png",  "Next important graph", e -> {
            try {
                LOGGER.fine("Next pressed");
                nextOrPrevGraph("next");
                timer.stop();
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        });
        slowButton  = createTimelineButton("images/slow.png",  "Slow down", e -> {
            DELAY = DELAY * 2;
            timer.setDelay(DELAY);
            LOGGER.fine("Slow pressed, delay=" + timer.getDelay());
        });
        fastButton  = createTimelineButton("images/fast.png",  "Speed up", e -> {
            DELAY = DELAY / 2;
            timer.setDelay(DELAY);
            LOGGER.fine("Fast pressed, delay=" + timer.getDelay());
        });


        Box box[] = new Box[1];
        //Box VBox[] = new Box[1];

        //VBox[0] = Box.createVerticalBox();
        box[ 0] = Box.createHorizontalBox();
        //box[ 1] = Box.createHorizontalBox();


        box[0].add(playButton);
        box[0].add(pauseButton);
        box[0].add(stopButton);
        box[0].add(prevButton);
        box[0].add(nextButton);
        box[0].add(slowButton);
        box[0].add(fastButton);


        //VBox[0].add(box[0]);
        //VBox[0].add(box[1]);

        contentPanel.add(box[0]);

        contentPanel.add(timeline);
    }

    /**
     * Creates a timeline control button with an icon, tooltip, and action.
     * Replaces the previous pattern of creating bare JButtons, manually
     * setting icons, and routing all clicks through a single MouseListener
     * with a component-identity if/else chain - which was fragile and
     * contrary to Swing best practices (ActionListener is the correct
     * abstraction for button clicks).
     *
     * @param iconPath  path to the button icon image
     * @param tooltip   accessible tooltip text
     * @param action    the action to perform on click
     * @return a configured JButton
     */
    private JButton createTimelineButton(String iconPath, String tooltip,
                                         ActionListener action) {
        JButton button = new JButton(new ImageIcon(iconPath));
        button.setToolTipText(tooltip);
        button.addActionListener(action);
        return button;
    }

    /**
     * initialize the notes space
     */
    public final void initializeNotesSpace() {
        notesPanel = new JPanel();

        JLabel notesHeading = new JLabel("Notes for the current timestamp");


        JTextArea notes = new JTextArea(15, 30);

        JScrollPane notesScrollPane = new JScrollPane(notes);
        notesScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);


        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                notesHeading, notesScrollPane);
        splitPane.setEnabled(false);
        splitPane.setDividerLocation(20);
        notesPanel.add(splitPane, BorderLayout.EAST);

    }

    /**
     * initialize the parameter space
     */
    public final void initializeParameterSpace() {
        //create the parameter space
        parameterSpace = new Box(BoxLayout.Y_AXIS);
        parameterSpace.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.RAISED));
        initializeCategoryPanel();
        paintCategoryPanel();
    }

    /**
     * Repaint the category panel, showing/hiding parameter sliders based on
     * each row's {@code showParams} flag.
     */
    private void paintCategoryPanel() {
        categoryPanel = new Box[5];
        for (int i = 0; i < 5; i++) {
            categoryPanel[i] = Box.createVerticalBox();
            categoryPanel[i].setBorder(BorderFactory.createEtchedBorder(1));

            categoryPanel[i].add(categoryRows[i].headerPanel);
            if (categoryRows[i].showParams) {
                categoryPanel[i].add(categoryRows[i].durationSlider);
                categoryPanel[i].add(categoryRows[i].meetingSlider);
            }
        }

        parameterSpace.removeAll();
        for (int i = 0; i < 5; i++) {
            parameterSpace.add(categoryPanel[i]);
        }
        parameterSpace.setVisible(false);
        parameterSpace.setVisible(true);
    }

    /**
     * initialize the category panel â€" creates one {@link CategoryRow}
     * per Edge type using the shared {@code createCategoryRow()} helper.
     */
    public final void initializeCategoryPanel() {
        categoryRows = new CategoryRow[5];

        categoryRows[0] = createCategoryRow(EdgeType.FRIEND,      friendEdges,
                "FRIENDS (Location : public)",                     50);
        categoryRows[1] = createCategoryRow(EdgeType.CLASSMATE,   classmateEdges,
                "<html>CLASSMATES (Location : classroom)",         50);
        categoryRows[2] = createCategoryRow(EdgeType.FAMILIAR,    fsEdges,
                "FAM STRANGERS (Location : public,pathways)",      25);
        categoryRows[3] = createCategoryRow(EdgeType.STRANGER,    strangerEdges,
                "STRANGERS (Location : public,pathways)",           25);
        categoryRows[4] = createCategoryRow(EdgeType.STUDY_GROUP, studyGEdges,
                "STUDY GROUPS (Location : public)",                 50);

        // Alias the sliders/checkboxes/panels to existing fields so that the
        // rest of Main.java (addGraph, generateFile calls, etc.) still compiles.
        showFriend              = categoryRows[0].checkbox;
        friendDurThreshold      = categoryRows[0].durationSlider;
        friendNumMeetThreshold  = categoryRows[0].meetingSlider;
        frShowParam             = categoryRows[0].settingsButton;
        frHpanel                = categoryRows[0].headerPanel;

        showClassmate               = categoryRows[1].checkbox;
        classmateDurThreshold       = categoryRows[1].durationSlider;
        classmateNumMeetThreshold   = categoryRows[1].meetingSlider;
        cShowParam                  = categoryRows[1].settingsButton;
        cHpanel                     = categoryRows[1].headerPanel;

        showFS              = categoryRows[2].checkbox;
        fsDurThreshold      = categoryRows[2].durationSlider;
        fsNumMeetThreshold  = categoryRows[2].meetingSlider;
        fsShowParam         = categoryRows[2].settingsButton;
        fsHpanel            = categoryRows[2].headerPanel;

        showStranger              = categoryRows[3].checkbox;
        strangerDurThreshold      = categoryRows[3].durationSlider;
        strangerNumMeetThreshold  = categoryRows[3].meetingSlider;
        sShowParam                = categoryRows[3].settingsButton;
        sHpanel                   = categoryRows[3].headerPanel;

        showStudy               = categoryRows[4].checkbox;
        studyGDurThreshold      = categoryRows[4].durationSlider;
        studyGNumMeetThreshold  = categoryRows[4].meetingSlider;
        sgShowParam             = categoryRows[4].settingsButton;
        sgHpanel                = categoryRows[4].headerPanel;
    }

    /**
     * Initialize the toolbar by delegating to {@link ToolbarBuilder}.
     * <p>
     * Previously this was a 220-line method that built every button inline.
     * Now the logic lives in ToolbarBuilder and Main simply wires itself as
     * the {@link ToolbarBuilder.GraphContext} supplier.
     * </p>
     *
     * @see ToolbarBuilder
     */
    public final void initializeToolBar() {
        ToolbarBuilder.GraphContext ctx = new ToolbarBuilder.GraphContext() {
            @Override public Graph<String, Edge> getGraph() { return g; }
            @Override public VisualizationViewer<String, Edge> getVisualizationViewer() { return vv; }
            @Override public String getTimestamp() { return timeStamp; }
            @Override public List<Edge> collectAllEdges() { return Main.this.collectAllEdges(); }
        };
        toolPanel = ToolbarBuilder.build(Main.this, ctx, legendPanel);
        contentPanel.add(toolPanel, BorderLayout.WEST);
    }

    /**
     * Collects all edges from every category into a single list.
     * Replaces the 5-line addAll() pattern duplicated across export handlers.
     */
    private List<Edge> collectAllEdges() {
        List<Edge> allEdges = new ArrayList<>();
        for (EdgeType type : EdgeType.values()) {
            List<Edge> list = getEdgeList(type);
            if (list != null) {
                allEdges.addAll(list);
            }
        }
        return allEdges;
    }

    // showExportSaveDialog() moved to ExportActions.showExportSaveDialog()

    // copyfile() removed â€" replaced with FileUtils.copyFile() from commons-io
    // (which was already a project dependency). The hand-rolled byte-copy loop
    // duplicated well-tested library code and missed features like atomic
    // writes and proper error cleanup.

    /**
     * Application entry point. Launches the Swing UI on the Event Dispatch
     * Thread and displays the main frame.
     *
     * @param args command-line arguments (currently unused)
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
                Main ex;
                try {
                    ex = new Main();
                    ex.setVisible(true);
                } catch (Exception ex1) {
                    LOGGER.log(Level.SEVERE, null, ex1);
                }
            });
    }
}
