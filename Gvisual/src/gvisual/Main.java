package gvisual;

import app.Network;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.algorithms.layout.StaticLayout;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.control.DefaultModalGraphMouse;
import edu.uci.ics.jung.visualization.control.ModalGraphMouse;
import edu.uci.ics.screencap.PNGDump;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.xml.sax.SAXException;

/**
 * Main application frame for GraphVisual - an interactive social network
 * visualisation and analysis tool built on JUNG (Java Universal Network/Graph).
 *
 * <h3>Overview</h3>
 * <p>Loads edge-list data (from flat files or a database via {@link app.Network}),
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
 *   <li><b>Export:</b> GraphML, DOT, GEXF, SVG, PNG, CSV edge-list, interactive HTML,
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
    private Graph<String, edge> g;
    private VisualizationViewer<String, edge> vv;
    private Layout<String, edge> graphLayout;
    private final GraphRenderers renderers = new GraphRenderers();

    /**
     * Push current overlay state to the GraphRenderers instance so that
     * transformers see up-to-date values on every render pass.
     */
    private void syncRenderers() {
        renderers.setGraph(g);
        renderers.setPathState(pathEdges, pathVertices, pathSource, pathTarget);
        renderers.setMstState(mstController != null && mstController.isOverlayActive(),
                mstController != null ? mstController.getMstEdges() : java.util.Collections.emptySet());
        renderers.setCommunityState(communityController != null && communityController.isOverlayActive(),
                communityController != null ? communityController.getNodeCommunityMap() : null);
        renderers.setArticulationState(articulationController != null && articulationController.isOverlayActive(), articulationController != null ? articulationController.getArticulationPoints() : java.util.Collections.emptySet(), articulationController != null ? articulationController.getBridgeEdges() : java.util.Collections.emptySet());
        renderers.setEgoState(egoController.isOverlayActive(), egoController.getCenter(), egoController.getNeighbors(), egoController.getEdges());
        renderers.setOldVertices(OldVertices);
    }
    private List<edge> friendEdges = new ArrayList<>();
    private List<edge> fsEdges = new ArrayList<>();
    private List<edge> classmateEdges = new ArrayList<>();
    private List<edge> strangerEdges = new ArrayList<>();
    private List<edge> studyGEdges = new ArrayList<>();
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

    // --- Shortest path fields ---
    private boolean pathFindingMode;
    private String pathSource;
    private String pathTarget;
    private Set<String> pathVertices;
    private Set<edge> pathEdges;
    private JPanel pathPanel;
    private JLabel pathSourceLabel;
    private JLabel pathTargetLabel;
    private JLabel pathResultLabel;
    private JButton pathFindButton;
    private JButton pathClearButton;
    private JRadioButton pathByHops;
    private JRadioButton pathByWeight;

    // --- MST analysis (delegated to controller) ---
    private MSTPanelController mstController;

    // --- Centrality analysis (delegated to controller) ---
    private CentralityPanelController centralityController;

    // --- Community detection (delegated to controller) ---
    private CommunityPanelController communityController;
    // (communityOverlayActive and nodeCommunityMap now live in CommunityPanelController)

    // --- Articulation point analysis (delegated to controller) ---
    private ArticulationPanelController articulationController;

    // --- Resilience analysis (delegated to controller) ---
    private ResiliencePanelController resilienceController;

    // --- Ego network (delegated to controller) ---
    private EgoPanelController egoController;

    /**
     * Constructor
     * @throws FileNotFoundException
     * @throws Exception
     */
    public Main() throws FileNotFoundException, Exception {

        initializeContentPanel();
        initializeLegendSpace();
        initializeStatsPanel();
        initializePathPanel();
        communityController = new CommunityPanelController(() -> g, () -> { syncRenderers(); refreshGraph(); });
        mstController = new MSTPanelController(() -> g, () -> { syncRenderers(); refreshGraph(); });
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
     * Returns the edge list for the given edge type.
     * Used to replace the cascading if/else chain in addGraph().
     */
    private List<edge> getEdgeList(EdgeType type) {
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
     * Returns whether the given edge type code is currently visible
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
        graphLayout = new StaticLayout<String, edge>(g);
        List<List<String>> clusters = new ArrayList<>();

        for (int i = 0; i < 9; i++) {
            clusters.add(new ArrayList<>());
        }

        for (String x : g.getVertices()) {
            boolean isF = false;
            boolean isFs = false;
            boolean isC = false;
            boolean isS = false;
            boolean isSg = false;
            int areaId;
            for (edge y : g.getOutEdges(x)) {
                EdgeType type = EdgeType.fromCode(y.getType());
                if (type != null) {
                    switch (type) {
                        case FRIEND:      isF = true;  break;
                        case FAMILIAR:    isFs = true; break;
                        case CLASSMATE:   isC = true;  break;
                        case STRANGER:    isS = true;  break;
                        case STUDY_GROUP: isSg = true; break;
                    }
                }
            }
            // To be added study groups
            if (isF && !isFs && !isC && !isS) {
                areaId = 0;
            } else if (isF && isFs && !isC && !isS) {
                areaId = 3;
            } else if (isF && !isFs && isC && !isS) {
                areaId = 1;
            } else if (!isF && !isFs && isC && !isS) {
                areaId = 2;
            } else if (!isF && !isFs && isC && isS) {
                areaId = 5;
            } else if (!isF && isFs && !isC && isS) {
                areaId = 7;
            } else if (!isF && isFs && !isC && !isS) {
                areaId = 6;
            } else if (!isF && !isFs && !isC && isS) {
                areaId = 8;
            } else {
                areaId = 4;
            }

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
    public void positionCluster(List<String> vertices, int y, int x) {
        int delX = 0;
        int delY = 0;
        int curX = x * 300 + 150;
        int curY = y * 200 + 100;
        int signX = 0; //0  is +ve
        int signY = 1; // 1 is -ve
        Random generator = new Random(42);


        for (String v : vertices) {
            graphLayout.setLocation(v, new java.awt.Point(curX, curY));
            delX = 5 + generator.nextInt(40);
            delY = 5 + generator.nextInt(40);
            signX = generator.nextInt(2);
            signY = generator.nextInt(2);
            if (signX == 0) {
                curX = curX + delX;
            } else if (signX == 1) {
                curX = curX - delX;
            }
            if (signY == 0) {
                curY = curY + delY;
            } else if (signY == 1) {
                curY = curY - delY;
            }
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
     * Holds the UI components for a single edge-type category row.
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
     * button) for the given edge type.  This replaces the five near-identical
     * blocks that previously lived in {@code initializeCategoryPanel()}.
     *
     * @param type        the edge type this row controls
     * @param edgeList    the corresponding edge list (e.g. {@code friendEdges})
     * @param labelText   the display text for the label
     * @param durMax      maximum value for the duration slider
     * @return a fully-initialised {@code CategoryRow}
     */
    private CategoryRow createCategoryRow(final EdgeType type,
                                          final List<edge> edgeList,
                                          String labelText,
                                          int durMax) {
        JButton settingsBtn = new JButton(new ImageIcon("./images/settings.png"));
        settingsBtn.setBorder(null);

        final JCheckBox cb = new JCheckBox();
        cb.setSelected(true);
        cb.addActionListener(e -> {
            if (cb.isSelected()) {
                for (edge x : edgeList) { g.addEdge(x, x.getVertex1(), x.getVertex2()); }
            } else {
                for (edge x : edgeList) { g.removeEdge(x); }
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
     * Creates the next/prev important graph according to the input direction
     * @param direction next/prev
     * @throws Exception
     */
    public void nextOrPrevGraph(String direction) throws Exception {
        boolean success = false;

        while (!success) {
            fileName = "./graph.txt";

            int count = 0;
            File database;
            LineIterator lineIterator = null;

            if (month.equals("05") && date.equals("31") && direction.equals("next")) {
                return;
            } else if (month.equals("03") && date.equals("01") && direction.equals("prev")) {
                return;
            } else if (direction.equals("next")) {

                timeline.setValue(timeline.getValue() + 1);
                updateTime();
            } else if (direction.equals("prev")) {
                timeline.setValue(timeline.getValue() - 1);
                updateTime();
            }



            Network.generateFile(fileName, month, date, friendDurThreshold.getValue(), friendNumMeetThreshold.getValue(), fsDurThreshold.getValue(), fsNumMeetThreshold.getValue(), classmateDurThreshold.getValue(), classmateNumMeetThreshold.getValue(), strangerDurThreshold.getValue(), strangerNumMeetThreshold.getValue(), studyGDurThreshold.getValue(), studyGNumMeetThreshold.getValue());

            database = new File(fileName);
            lineIterator = FileUtils.lineIterator(database);

            while (lineIterator.hasNext()) {
                lineIterator.next();
                count++;
            }

            if (count > NUM_EDGES_IMP_GRAPH) {
                success = true;
            }
            lineIterator.close();
        }

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

        // Populate classified edge lists from parse result
        for (EdgeType type : EdgeType.values()) {
            List<edge> list = getEdgeList(type);
            if (list != null) {
                list.clear();
                list.addAll(parseResult.getEdges(type));
            }
        }

        createLayout();
        vv = new VisualizationViewer<String, edge>(graphLayout);
        vv.setSize(new Dimension(100, 0));

        DefaultModalGraphMouse gm = new DefaultModalGraphMouse();

        gm.setMode(ModalGraphMouse.Mode.TRANSFORMING);
        vv.setGraphMouse(gm);


        Transformer<edge, String> edgeLabel = (edge i) -> {
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
    public final void initializeLegendSpace(){
        legendPanel = new JPanel();

        JLabel legendHeading = new JLabel("Legend for the Graph");

        String blueImgPath = "images/blue.jpg";
        String redImgPath = "images/red.jpg";
        String greenImgPath = "images/green.jpg";
        String yellowImgPath = "images/yellow.jpg";
        String grayImgPath = "images/gray.jpg";

        Icon blue = new ImageIcon(blueImgPath);
        Icon red = new ImageIcon(redImgPath);
        Icon green = new ImageIcon(greenImgPath);
        Icon yellow = new ImageIcon(yellowImgPath);
        Icon gray = new ImageIcon(grayImgPath);

        JLabel fLabel = new JLabel("Friend");
        JLabel fColor= new JLabel(green);
        JLabel fsLabel= new JLabel("Familiar Stranger");
        JLabel fsColor= new JLabel(gray);
        JLabel sLabel= new JLabel("Stranger");
        JLabel sColor= new JLabel(red);
        JLabel cLabel= new JLabel("Classmate");
        JLabel cColor = new JLabel(blue);
        JLabel sgLabel= new JLabel("Study Group");
        JLabel sgColor= new JLabel(yellow);

        Box HBox[] = new Box[5];
        Box VBox[] = new Box[1];

        VBox[0]= Box.createVerticalBox();

        HBox[0]= Box.createHorizontalBox();
        HBox[1]= Box.createHorizontalBox();
        HBox[2]= Box.createHorizontalBox();
        HBox[3]= Box.createHorizontalBox();
        HBox[4]= Box.createHorizontalBox();


        HBox[0].add(fColor);
        HBox[1].add(fsColor);
        HBox[2].add(sColor);
        HBox[3].add(cColor);
        HBox[4].add(sgColor);

        HBox[0].add(fLabel);
        HBox[1].add(fsLabel);
        HBox[2].add(sLabel);
        HBox[3].add(cLabel);
        HBox[4].add(sgLabel);

        VBox[0].add(HBox[0]);
        VBox[0].add(HBox[1]);
        VBox[0].add(HBox[2]);
        VBox[0].add(HBox[3]);
        VBox[0].add(HBox[4]);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                legendHeading, VBox[0]);


        legendPanel.add(splitPane);

    }

    /**
     * Initializes the shortest path finder panel with source/target selection,
     * path mode toggle, and result display.
     */
    public final void initializePathPanel() {
        pathFindingMode = false;
        pathSource = null;
        pathTarget = null;
        pathVertices = new HashSet<String>();
        pathEdges = new HashSet<edge>();

        pathPanel = new JPanel();
        pathPanel.setLayout(new BoxLayout(pathPanel, BoxLayout.Y_AXIS));
        pathPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
                "Shortest Path Finder",
                TitledBorder.CENTER,
                TitledBorder.TOP));

        Font labelFont = new Font("SansSerif", Font.PLAIN, 12);

        pathSourceLabel = new JLabel("Source: (none)");
        pathSourceLabel.setFont(labelFont);
        pathSourceLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        pathTargetLabel = new JLabel("Target: (none)");
        pathTargetLabel.setFont(labelFont);
        pathTargetLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        pathResultLabel = new JLabel("<html>Click 'Select Nodes' then click two nodes on the graph.</html>");
        pathResultLabel.setFont(labelFont);
        pathResultLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        // Path mode radio buttons
        pathByHops = new JRadioButton("Fewest hops", true);
        pathByWeight = new JRadioButton("Lowest weight");
        ButtonGroup pathModeGroup = new ButtonGroup();
        pathModeGroup.add(pathByHops);
        pathModeGroup.add(pathByWeight);

        JPanel radioPanel = new JPanel();
        radioPanel.setLayout(new BoxLayout(radioPanel, BoxLayout.X_AXIS));
        radioPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        radioPanel.add(pathByHops);
        radioPanel.add(pathByWeight);

        // Buttons
        pathFindButton = new JButton("Select Nodes");
        pathFindButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        pathFindButton.addActionListener(e -> {
                if (!pathFindingMode) {
                    enablePathFindingMode();
                } else {
                    disablePathFindingMode();
                }
            });

        pathClearButton = new JButton("Clear Path");
        pathClearButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        pathClearButton.addActionListener(e -> {
                clearPath();
            });

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        buttonPanel.add(pathFindButton);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(pathClearButton);

        pathPanel.add(pathSourceLabel);
        pathPanel.add(pathTargetLabel);
        pathPanel.add(Box.createVerticalStrut(4));
        pathPanel.add(radioPanel);
        pathPanel.add(Box.createVerticalStrut(4));
        pathPanel.add(buttonPanel);
        pathPanel.add(Box.createVerticalStrut(4));
        pathPanel.add(pathResultLabel);
    }

    /**
     * Enables path-finding mode â€" switches to PICKING mode and listens for
     * two node clicks (source, then target).
     */
    private void enablePathFindingMode() {
        pathFindingMode = true;
        pathSource = null;
        pathTarget = null;
        pathVertices.clear();
        pathEdges.clear();
        pathFindButton.setText("Cancel");
        pathSourceLabel.setText("Source: (click a node...)");
        pathTargetLabel.setText("Target: (waiting...)");
        pathResultLabel.setText("<html>Click the source node on the graph.</html>");

        // Switch to picking mode for node selection
        DefaultModalGraphMouse gm = new DefaultModalGraphMouse();
        gm.setMode(ModalGraphMouse.Mode.PICKING);
        vv.setGraphMouse(gm);

        vv.addMouseListener(pathMouseListener);
        refreshGraph();
    }

    /**
     * Disables path-finding mode and restores normal interaction.
     */
    private void disablePathFindingMode() {
        pathFindingMode = false;
        pathFindButton.setText("Select Nodes");
        vv.removeMouseListener(pathMouseListener);

        // Restore transform mode
        DefaultModalGraphMouse gm = new DefaultModalGraphMouse();
        gm.setMode(ModalGraphMouse.Mode.TRANSFORMING);
        vv.setGraphMouse(gm);
    }

    /**
     * Clears the current path highlight and resets the path panel.
     */
    private void clearPath() {
        pathSource = null;
        pathTarget = null;
        pathVertices.clear();
        pathEdges.clear();
        pathSourceLabel.setText("Source: (none)");
        pathTargetLabel.setText("Target: (none)");
        pathResultLabel.setText("<html>Click 'Select Nodes' then click two nodes on the graph.</html>");

        if (pathFindingMode) {
            disablePathFindingMode();
        }
        refreshGraph();
    }

    /**
     * Finds the closest graph vertex to the given screen coordinates.
     */
    private String findClosestVertex(int screenX, int screenY) {
        String closest = null;
        double minDist = Double.MAX_VALUE;

        for (String vertex : g.getVertices()) {
            java.awt.geom.Point2D layoutPoint = graphLayout.transform(vertex);
            java.awt.geom.Point2D screenPoint = vv.getRenderContext()
                    .getMultiLayerTransformer()
                    .transform(layoutPoint);

            double dx = screenPoint.getX() - screenX;
            double dy = screenPoint.getY() - screenY;
            double dist = Math.sqrt(dx * dx + dy * dy);

            if (dist < minDist && dist < 30) { // 30px click radius
                minDist = dist;
                closest = vertex;
            }
        }
        return closest;
    }

    /**
     * Runs the shortest path algorithm and highlights the result.
     */
    private void computeAndHighlightPath() {
        if (pathSource == null || pathTarget == null) return;

        ShortestPathFinder finder = new ShortestPathFinder(g);
        ShortestPathFinder.PathResult result;

        if (pathByWeight.isSelected()) {
            result = finder.findShortestByWeight(pathSource, pathTarget);
        } else {
            result = finder.findShortestByHops(pathSource, pathTarget);
        }

        pathVertices.clear();
        pathEdges.clear();

        if (result == null) {
            pathResultLabel.setText("<html><b style='color:red'>No path found!</b><br/>"
                    + "Nodes are in disconnected components.</html>");
        } else {
            pathVertices.addAll(result.getVertices());
            pathEdges.addAll(result.getEdges());

            String mode = pathByWeight.isSelected() ? "weight-optimal" : "hop-optimal";
            StringBuilder edgeTypes = new StringBuilder();
            for (edge e : result.getEdges()) {
                if (edgeTypes.length() > 0) edgeTypes.append("â†'");
                edgeTypes.append(e.getType());
            }

            pathResultLabel.setText(String.format(
                    "<html><b style='color:#00FF00'>Path found!</b> (%s)<br/>"
                    + "Hops: %d<br/>"
                    + "Total weight: %.1f<br/>"
                    + "Edge types: %s<br/>"
                    + "Path: %s</html>",
                    mode,
                    result.getHopCount(),
                    result.getTotalWeight(),
                    edgeTypes.toString(),
                    buildPathString(result)));
        }

        disablePathFindingMode();
        refreshGraph();
    }

    private String buildPathString(ShortestPathFinder.PathResult result) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < result.getVertices().size(); i++) {
            if (i > 0) sb.append("â†'");
            sb.append(result.getVertices().get(i));
        }
        return sb.toString();
    }

    /**
     * Refreshes the graph visualization to show/hide path highlighting.
     */
    private void refreshGraph() {
        if (imagePanel != null) {
            imagePanel.setVisible(false);
            imagePanel.setVisible(true);
        }
    }

    /**
     * Mouse listener for path node selection.
     */
    private final MouseListener pathMouseListener = new MouseListener() {
        public void mouseClicked(MouseEvent e) {
            if (!pathFindingMode) return;

            String clicked = findClosestVertex(e.getX(), e.getY());
            if (clicked == null) return;

            if (pathSource == null) {
                pathSource = clicked;
                pathSourceLabel.setText("Source: Node " + clicked);
                pathTargetLabel.setText("Target: (click another node...)");
                pathResultLabel.setText("<html>Now click the target node.</html>");
                refreshGraph();
            } else if (pathTarget == null) {
                if (clicked.equals(pathSource)) {
                    pathResultLabel.setText("<html>Same node â€" pick a different target.</html>");
                    return;
                }
                pathTarget = clicked;
                pathTargetLabel.setText("Target: Node " + clicked);
                computeAndHighlightPath();
            }
        }
        public void mousePressed(MouseEvent e) {}
        public void mouseReleased(MouseEvent e) {}
        public void mouseEntered(MouseEvent e) {}
        public void mouseExited(MouseEvent e) {}
    };

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
            pathPanel,
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




        playButton = new JButton();
        pauseButton = new JButton();
        stopButton = new JButton();
        prevButton = new JButton();
        nextButton = new JButton();
        slowButton = new JButton();
        fastButton = new JButton();

        String playImgPath = "images/play.png";
        String pauseImgPath = "images/pause.png";
        String stopImgPath = "images/stop.png";
        String slowImgPath = "images/slow.png";
        String fastImgPath = "images/fast.png";
        String prevImgPath = "images/prev.png";
        String nextImgPath = "images/next.png";

        Icon play = new ImageIcon(playImgPath);
        Icon pause = new ImageIcon(pauseImgPath);
        Icon stop = new ImageIcon(stopImgPath);
        Icon prev = new ImageIcon(prevImgPath);
        Icon next = new ImageIcon(nextImgPath);
        Icon slow = new ImageIcon(slowImgPath);
        Icon fast = new ImageIcon(fastImgPath);

        playButton.setIcon(play);
        pauseButton.setIcon(pause);
        stopButton.setIcon(stop);
        prevButton.setIcon(prev);
        nextButton.setIcon(next);
        slowButton.setIcon(slow);
        fastButton.setIcon(fast);



        MouseListener clickListener = new MouseListener() {

            public void mouseClicked(MouseEvent e) {
                //throw new UnsupportedOperationException("Not supported yet.");
            }

            public void mousePressed(MouseEvent e) {
                //throw new UnsupportedOperationException("Not supported yet.");
                if (e.getComponent() == playButton) {
                    LOGGER.fine("Play pressed");
                    timer.start();
                } else if (e.getComponent() == pauseButton) {
                    LOGGER.fine("Pause pressed");
                    timer.stop();
                } else if (e.getComponent() == stopButton) {
                    LOGGER.fine("Stop pressed");
                    timeline.setValue(1);
                    timer.stop();
                } else if (e.getComponent() == slowButton) {
                    DELAY = DELAY * 2;
                    timer.setDelay(DELAY);
                    LOGGER.fine("Slow pressed, delay=" + timer.getDelay());
                } else if (e.getComponent() == fastButton) {
                    DELAY = DELAY / 2;
                    timer.setDelay(DELAY);
                    LOGGER.fine("Fast pressed, delay=" + timer.getDelay());
                } else if (e.getComponent() == nextButton) {
                    try {
                        LOGGER.fine("Next pressed");
                        nextOrPrevGraph("next");
                        timer.stop();
                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                } else if (e.getComponent() == prevButton) {
                    try {
                        LOGGER.fine("Prev pressed");
                        nextOrPrevGraph("prev");
                        timer.stop();
                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                }
            }

            public void mouseReleased(MouseEvent e) {
                //throw new UnsupportedOperationException("Not supported yet.");
            }

            public void mouseEntered(MouseEvent e) {
                //throw new UnsupportedOperationException("Not supported yet.");
            }

            public void mouseExited(MouseEvent e) {
                //throw new UnsupportedOperationException("Not supported yet.");
            }
        };
        playButton.addMouseListener(clickListener);
        pauseButton.addMouseListener(clickListener);
        stopButton.addMouseListener(clickListener);
        slowButton.addMouseListener(clickListener);
        fastButton.addMouseListener(clickListener);
        prevButton.addMouseListener(clickListener);
        nextButton.addMouseListener(clickListener);


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
     * per edge type using the shared {@code createCategoryRow()} helper.
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
            @Override public Graph<String, edge> getGraph() { return g; }
            @Override public VisualizationViewer<String, edge> getVisualizationViewer() { return vv; }
            @Override public String getTimestamp() { return timeStamp; }
            @Override public List<edge> collectAllEdges() { return Main.this.collectAllEdges(); }
        };
        toolPanel = ToolbarBuilder.build(Main.this, ctx, legendPanel);
        contentPanel.add(toolPanel, BorderLayout.WEST);
    }

    /**
     * Collects all edges from every category into a single list.
     * Replaces the 5-line addAll() pattern duplicated across export handlers.
     */
    private List<edge> collectAllEdges() {
        List<edge> allEdges = new ArrayList<>();
        for (EdgeType type : EdgeType.values()) {
            List<edge> list = getEdgeList(type);
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
