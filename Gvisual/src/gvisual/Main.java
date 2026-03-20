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
 *
 * @author user
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
        renderers.setMstState(mstOverlayActive, mstEdges);
        renderers.setCommunityState(communityOverlayActive, nodeCommunityMap);
        renderers.setArticulationState(articulationOverlayActive, articulationPoints, bridgeEdges);
        renderers.setEgoState(egoOverlayActive, egoCenter, egoNeighbors, egoEdges);
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

    // --- MST fields ---
    private JPanel mstPanel;
    private JButton mstComputeButton;
    private JButton mstClearButton;
    private JLabel mstSummaryLabel;
    private JLabel mstStatsLabel;
    private JLabel mstComponentsLabel;
    private boolean mstOverlayActive;
    private Set<edge> mstEdges;

    // --- Centrality analysis fields ---
    private JPanel centralityPanel;
    private JButton centralityComputeButton;
    private JButton centralityClearButton;
    private JComboBox<String> centralityMetricCombo;
    private JLabel centralityTopologyLabel;
    private JLabel centralitySummaryLabel;
    private JLabel centralityRankingLabel;
    private boolean centralityActive;
    private Map<String, NodeCentralityAnalyzer.CentralityResult> centralityResults;

    // --- Community detection fields ---
    private JPanel communityPanel;
    private JButton communityDetectButton;
    private JButton communityClearButton;
    private JLabel communityCountLabel;
    private JLabel communityModularityLabel;
    private JLabel communityDetailsLabel;
    private boolean communityOverlayActive;
    private Map<String, Integer> nodeCommunityMap;

    // --- Articulation point analysis fields ---
    private JPanel articulationPanel;
    private JButton articulationComputeButton;
    private JButton articulationClearButton;
    private JLabel articulationSummaryLabel;
    private JLabel articulationResilienceLabel;
    private JLabel articulationDetailsLabel;
    private boolean articulationOverlayActive;
    private Set<String> articulationPoints;
    private Set<edge> bridgeEdges;

    // --- Resilience analysis fields ---
    private JPanel resiliencePanel;
    private JButton resilienceAnalyzeButton;
    private JButton resilienceExportButton;
    private JLabel resilienceSummaryLabel;
    private JLabel resilienceDetailsLabel;

    // --- Ego network fields ---
    private JPanel egoPanel;
    private JTextField egoSearchField;
    private JButton egoSearchButton;
    private JButton egoClearButton;
    private JLabel egoSummaryLabel;
    private JLabel egoNeighborListLabel;
    private boolean egoOverlayActive;
    private String egoCenter;
    private Set<String> egoNeighbors;
    private Set<edge> egoEdges;

    private static final Color[] COMMUNITY_COLORS = {
        new Color(0, 200, 120),    // Emerald green
        new Color(65, 135, 255),   // Bright blue
        new Color(255, 100, 100),  // Coral red
        new Color(255, 200, 50),   // Golden yellow
        new Color(200, 100, 255),  // Purple
        new Color(255, 150, 50),   // Orange
        new Color(100, 220, 220),  // Teal
        new Color(255, 100, 200),  // Pink
        new Color(180, 220, 80),   // Lime
        new Color(150, 130, 255),  // Lavender
        new Color(255, 180, 150),  // Salmon
        new Color(100, 180, 150),  // Sea green
    };

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
        initializeCommunityPanel();
        initializeMSTPanel();
        initializeCentralityPanel();
        initializeArticulationPanel();
        initializeResiliencePanel();
        initializeEgoPanel();
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
     * March 1 – May 31, 2011.  March has 31 days, April has 30,
     * May has 31.
     */
    public void updateTime() {
        int day = timeline.getValue();  // 1..92

        if (day <= 31) {
            // March: days 1–31
            month = "03";
        } else if (day <= 61) {
            // April: days 32–61 → April 1–30
            month = "04";
            day = day - 31;
        } else {
            // May: days 62–92 → May 1–31
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
     * Enables path-finding mode — switches to PICKING mode and listens for
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
                if (edgeTypes.length() > 0) edgeTypes.append("→");
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
            if (i > 0) sb.append("→");
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
                    pathResultLabel.setText("<html>Same node — pick a different target.</html>");
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

    /**
     * Initializes the community detection panel with detect/clear buttons
     * and a results display area.
     */
    public final void initializeCommunityPanel() {
        communityOverlayActive = false;
        syncRenderers();
        nodeCommunityMap = null;

        communityPanel = new JPanel();
        communityPanel.setLayout(new BoxLayout(communityPanel, BoxLayout.Y_AXIS));
        communityPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
                "Community Detection",
                TitledBorder.CENTER,
                TitledBorder.TOP));

        Font labelFont = new Font("SansSerif", Font.PLAIN, 12);

        communityCountLabel = new JLabel("Communities: —");
        communityCountLabel.setFont(labelFont);
        communityCountLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        communityModularityLabel = new JLabel("Modularity: —");
        communityModularityLabel.setFont(labelFont);
        communityModularityLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        communityDetailsLabel = new JLabel("<html>Click 'Detect' to find communities.</html>");
        communityDetailsLabel.setFont(labelFont);
        communityDetailsLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        communityDetectButton = new JButton("Detect");
        communityDetectButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        communityDetectButton.addActionListener(e -> {
                runCommunityDetection();
            });

        communityClearButton = new JButton("Clear");
        communityClearButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        communityClearButton.addActionListener(e -> {
                clearCommunityOverlay();
            });

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        buttonPanel.add(communityDetectButton);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(communityClearButton);

        communityPanel.add(communityCountLabel);
        communityPanel.add(communityModularityLabel);
        communityPanel.add(Box.createVerticalStrut(4));
        communityPanel.add(buttonPanel);
        communityPanel.add(Box.createVerticalStrut(4));
        communityPanel.add(communityDetailsLabel);
    }

    /**
     * Runs community detection on the current graph and activates the
     * community color overlay.
     */
    private void runCommunityDetection() {
        if (g == null || g.getVertexCount() == 0) {
            communityDetailsLabel.setText("<html>No graph loaded.</html>");
            return;
        }

        CommunityDetector detector = new CommunityDetector(g);
        CommunityDetector.DetectionResult result = detector.detect();

        communityOverlayActive = true;
        syncRenderers();
        nodeCommunityMap = new HashMap<String, Integer>(result.getNodeToCommunity());

        int total = result.getCommunityCount();
        List<CommunityDetector.Community> significant = result.getSignificantCommunities(2);
        double modularity = result.getModularity(g);

        communityCountLabel.setText("Communities: " + total
                + " (" + significant.size() + " with 2+ members)");
        communityModularityLabel.setText(String.format("Modularity: %.4f", modularity));

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
                        "<b style='color:%s'>■</b> C%d: %d nodes, %d edges, density=%.3f<br/>"
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
        communityDetailsLabel.setText(details.toString());

        communityPanel.revalidate();
        communityPanel.repaint();
        refreshGraph();
    }

    /**
     * Returns a human-readable label for edge type codes.
     */
    private String getDominantLabel(String typeCode) {
        EdgeType type = EdgeType.fromCode(typeCode);
        return type != null ? type.getDisplayLabel() : typeCode;
    }

    /**
     * Clears the community overlay and resets the panel.
     */
    private void clearCommunityOverlay() {
        communityOverlayActive = false;
        syncRenderers();
        nodeCommunityMap = null;
        communityCountLabel.setText("Communities: —");
        communityModularityLabel.setText("Modularity: —");
        communityDetailsLabel.setText("<html>Click 'Detect' to find communities.</html>");
        communityPanel.revalidate();
        communityPanel.repaint();
        refreshGraph();
    }

    /**
     * Initializes the Minimum Spanning Tree panel with compute/clear buttons
     * and result display.
     */
    public final void initializeMSTPanel() {
        mstOverlayActive = false;
        syncRenderers();
        mstEdges = new HashSet<edge>();

        mstPanel = new JPanel();
        mstPanel.setLayout(new BoxLayout(mstPanel, BoxLayout.Y_AXIS));
        mstPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
                "Minimum Spanning Tree",
                TitledBorder.CENTER,
                TitledBorder.TOP));

        Font labelFont = new Font("SansSerif", Font.PLAIN, 12);

        mstSummaryLabel = new JLabel("<html>Click 'Compute' to find the MST.</html>");
        mstSummaryLabel.setFont(labelFont);
        mstSummaryLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        mstStatsLabel = new JLabel("");
        mstStatsLabel.setFont(labelFont);
        mstStatsLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        mstComponentsLabel = new JLabel("");
        mstComponentsLabel.setFont(labelFont);
        mstComponentsLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        // Buttons
        mstComputeButton = new JButton("Compute");
        mstComputeButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        mstComputeButton.addActionListener(e -> {
                runMSTComputation();
            });

        mstClearButton = new JButton("Clear");
        mstClearButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        mstClearButton.addActionListener(e -> {
                clearMSTOverlay();
            });

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        buttonPanel.add(mstComputeButton);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(mstClearButton);

        mstPanel.add(mstSummaryLabel);
        mstPanel.add(Box.createVerticalStrut(4));
        mstPanel.add(buttonPanel);
        mstPanel.add(Box.createVerticalStrut(4));
        mstPanel.add(mstStatsLabel);
        mstPanel.add(Box.createVerticalStrut(4));
        mstPanel.add(mstComponentsLabel);
    }

    /**
     * Computes the MST and activates the edge highlight overlay.
     */
    private void runMSTComputation() {
        if (g == null || g.getVertexCount() == 0) {
            mstSummaryLabel.setText("<html>No graph loaded.</html>");
            return;
        }

        MinimumSpanningTree mstComputer = new MinimumSpanningTree(g);
        MinimumSpanningTree.MSTResult result = mstComputer.compute();

        mstOverlayActive = true;
        syncRenderers();
        mstEdges.clear();
        mstEdges.addAll(result.getEdges());

        // Summary
        mstSummaryLabel.setText("<html><b style='color:#00FF00'>"
                + result.getSummary() + "</b></html>");

        // Stats
        StringBuilder stats = new StringBuilder("<html>");
        stats.append(String.format("<b>Vertices:</b> %d<br/>", result.getVertexCount()));
        stats.append(String.format("<b>MST Edges:</b> %d<br/>", result.getEdgeCount()));
        stats.append(String.format("<b>Total Weight:</b> %.1f<br/>", result.getTotalWeight()));
        stats.append(String.format("<b>Avg Weight:</b> %.1f<br/>", result.getAverageWeight()));

        if (result.getHeaviestEdge() != null) {
            edge heavy = result.getHeaviestEdge();
            stats.append(String.format("<b>Bottleneck:</b> %s↔%s (%.1f)<br/>",
                    heavy.getVertex1(), heavy.getVertex2(), heavy.getWeight()));
        }
        if (result.getLightestEdge() != null) {
            edge light = result.getLightestEdge();
            stats.append(String.format("<b>Lightest:</b> %s↔%s (%.1f)<br/>",
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
        mstStatsLabel.setText(stats.toString());

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
            mstComponentsLabel.setText(comps.toString());
        } else {
            mstComponentsLabel.setText("");
        }

        mstPanel.revalidate();
        mstPanel.repaint();
        refreshGraph();
    }

    /**
     * Clears the MST overlay and resets the panel.
     */
    private void clearMSTOverlay() {
        mstOverlayActive = false;
        syncRenderers();
        mstEdges.clear();
        mstSummaryLabel.setText("<html>Click 'Compute' to find the MST.</html>");
        mstStatsLabel.setText("");
        mstComponentsLabel.setText("");
        mstPanel.revalidate();
        mstPanel.repaint();
        refreshGraph();
    }

    /**
     * Initializes the centrality analysis panel with compute/clear buttons,
     * metric selector, and ranked results display.
     */
    public final void initializeCentralityPanel() {
        centralityActive = false;
        centralityResults = new HashMap<String, NodeCentralityAnalyzer.CentralityResult>();

        centralityPanel = new JPanel();
        centralityPanel.setLayout(new BoxLayout(centralityPanel, BoxLayout.Y_AXIS));
        centralityPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
                "Centrality Analysis",
                TitledBorder.CENTER,
                TitledBorder.TOP));

        Font labelFont = new Font("SansSerif", Font.PLAIN, 12);

        centralityTopologyLabel = new JLabel("Topology: —");
        centralityTopologyLabel.setFont(labelFont);
        centralityTopologyLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        centralitySummaryLabel = new JLabel("<html>Click 'Compute' to analyze node centrality.</html>");
        centralitySummaryLabel.setFont(labelFont);
        centralitySummaryLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        centralityRankingLabel = new JLabel("");
        centralityRankingLabel.setFont(labelFont);
        centralityRankingLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        // Metric selector combo box
        centralityMetricCombo = new JComboBox<String>(
                new String[] { "Combined", "Degree", "Betweenness", "Closeness" });
        centralityMetricCombo.setAlignmentX(JComboBox.LEFT_ALIGNMENT);
        centralityMetricCombo.setMaximumSize(new Dimension(200, 25));
        centralityMetricCombo.addActionListener(e -> {
                if (centralityActive) {
                    updateCentralityRanking();
                }
            });

        JPanel metricPanel = new JPanel();
        metricPanel.setLayout(new BoxLayout(metricPanel, BoxLayout.X_AXIS));
        metricPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        JLabel sortLabel = new JLabel("Sort by: ");
        sortLabel.setFont(labelFont);
        metricPanel.add(sortLabel);
        metricPanel.add(centralityMetricCombo);

        // Buttons
        centralityComputeButton = new JButton("Compute");
        centralityComputeButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        centralityComputeButton.addActionListener(e -> {
                runCentralityAnalysis();
            });

        centralityClearButton = new JButton("Clear");
        centralityClearButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        centralityClearButton.addActionListener(e -> {
                clearCentralityAnalysis();
            });

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        buttonPanel.add(centralityComputeButton);
        buttonPanel.add(Box.createHorizontalStrut(4));
        buttonPanel.add(centralityClearButton);

        centralityPanel.add(centralityTopologyLabel);
        centralityPanel.add(Box.createVerticalStrut(4));
        centralityPanel.add(metricPanel);
        centralityPanel.add(Box.createVerticalStrut(4));
        centralityPanel.add(buttonPanel);
        centralityPanel.add(Box.createVerticalStrut(4));
        centralityPanel.add(centralitySummaryLabel);
        centralityPanel.add(Box.createVerticalStrut(4));
        centralityPanel.add(centralityRankingLabel);
    }

    /**
     * Runs centrality analysis on the current graph and displays results.
     */
    private void runCentralityAnalysis() {
        if (g == null || g.getVertexCount() == 0) {
            centralitySummaryLabel.setText("<html>No graph loaded.</html>");
            return;
        }

        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(g);
        analyzer.compute();

        centralityActive = true;
        centralityResults.clear();

        // Cache results
        for (NodeCentralityAnalyzer.CentralityResult r : analyzer.getRankedResults()) {
            centralityResults.put(r.getNodeId(), r);
        }

        // Topology classification
        String topology = analyzer.classifyTopology();
        centralityTopologyLabel.setText("Topology: " + topology);

        // Summary stats
        Map<String, Object> summary = analyzer.getSummary();
        StringBuilder sb = new StringBuilder("<html>");
        sb.append(String.format("<b>Avg Degree C:</b> %.3f<br/>", summary.get("avgDegreeCentrality")));
        sb.append(String.format("<b>Avg Betweenness C:</b> %.3f<br/>", summary.get("avgBetweennessCentrality")));
        sb.append(String.format("<b>Avg Closeness C:</b> %.3f<br/>", summary.get("avgClosenessCentrality")));
        sb.append(String.format("<b>Most Connected:</b> Node %s (%.3f)<br/>",
                summary.get("maxDegreeCentralityNode"), summary.get("maxDegreeCentrality")));
        sb.append(String.format("<b>Most Central:</b> Node %s (%.3f)<br/>",
                summary.get("maxBetweennessCentralityNode"), summary.get("maxBetweennessCentrality")));
        sb.append(String.format("<b>Most Reachable:</b> Node %s (%.3f)",
                summary.get("maxClosenessCentralityNode"), summary.get("maxClosenessCentrality")));
        sb.append("</html>");
        centralitySummaryLabel.setText(sb.toString());

        updateCentralityRanking();

        centralityPanel.revalidate();
        centralityPanel.repaint();
    }

    /**
     * Updates the centrality ranking display based on the selected metric.
     */
    private void updateCentralityRanking() {
        if (!centralityActive || centralityResults.isEmpty()) return;

        String metric = (String) centralityMetricCombo.getSelectedItem();
        List<NodeCentralityAnalyzer.CentralityResult> sorted =
                new ArrayList<NodeCentralityAnalyzer.CentralityResult>(centralityResults.values());

        final String m = metric.toLowerCase();
        Collections.sort(sorted, (NodeCentralityAnalyzer.CentralityResult a,
                               NodeCentralityAnalyzer.CentralityResult b) -> {
                double va, vb;
                if ("degree".equals(m)) {
                    va = a.getDegreeCentrality();
                    vb = b.getDegreeCentrality();
                } else if ("betweenness".equals(m)) {
                    va = a.getBetweennessCentrality();
                    vb = b.getBetweennessCentrality();
                } else if ("closeness".equals(m)) {
                    va = a.getClosenessCentrality();
                    vb = b.getClosenessCentrality();
                } else {
                    va = a.getCombinedScore();
                    vb = b.getCombinedScore();
                }
                return Double.compare(vb, va);
            });

        int shown = Math.min(sorted.size(), 10);
        StringBuilder sb = new StringBuilder("<html><b>Top " + shown + " by " + metric + ":</b><br/>");
        for (int i = 0; i < shown; i++) {
            NodeCentralityAnalyzer.CentralityResult r = sorted.get(i);
            String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : i == 2 ? "🥉" : "&nbsp;&nbsp;";
            double value;
            if ("degree".equals(m)) value = r.getDegreeCentrality();
            else if ("betweenness".equals(m)) value = r.getBetweennessCentrality();
            else if ("closeness".equals(m)) value = r.getClosenessCentrality();
            else value = r.getCombinedScore();

            sb.append(String.format("%s #%d Node %s: %.3f (deg=%d)<br/>",
                    medal, i + 1, r.getNodeId(), value, r.getDegree()));
        }
        sb.append("</html>");
        centralityRankingLabel.setText(sb.toString());

        centralityPanel.revalidate();
        centralityPanel.repaint();
    }

    /**
     * Clears the centrality analysis and resets the panel.
     */
    private void clearCentralityAnalysis() {
        centralityActive = false;
        centralityResults.clear();
        centralityTopologyLabel.setText("Topology: —");
        centralitySummaryLabel.setText("<html>Click 'Compute' to analyze node centrality.</html>");
        centralityRankingLabel.setText("");
        centralityPanel.revalidate();
        centralityPanel.repaint();
    }

    /**
     * Initializes the articulation point and bridge analysis panel.
     */
    public final void initializeArticulationPanel() {
        articulationOverlayActive = false;
        syncRenderers();
        articulationPoints = new HashSet<>();
        bridgeEdges = new HashSet<>();

        articulationPanel = new JPanel();
        articulationPanel.setLayout(new BoxLayout(articulationPanel, BoxLayout.Y_AXIS));
        articulationPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
                "Articulation Points & Bridges",
                TitledBorder.LEFT, TitledBorder.TOP));

        articulationResilienceLabel = new JLabel("Resilience: —");
        articulationResilienceLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        articulationSummaryLabel = new JLabel("<html>Click 'Analyze' to find critical nodes and edges.</html>");
        articulationSummaryLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        articulationDetailsLabel = new JLabel("");
        articulationDetailsLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);

        articulationComputeButton = new JButton("Analyze");
        articulationComputeButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        articulationComputeButton.addActionListener(e -> {
                runArticulationAnalysis();
            });

        articulationClearButton = new JButton("Clear");
        articulationClearButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        articulationClearButton.addActionListener(e -> {
                clearArticulationAnalysis();
            });

        buttonPanel.add(articulationComputeButton);
        buttonPanel.add(Box.createHorizontalStrut(5));
        buttonPanel.add(articulationClearButton);

        articulationPanel.add(articulationResilienceLabel);
        articulationPanel.add(articulationSummaryLabel);
        articulationPanel.add(Box.createVerticalStrut(4));
        articulationPanel.add(buttonPanel);
        articulationPanel.add(Box.createVerticalStrut(4));
        articulationPanel.add(articulationDetailsLabel);
    }

    /**
     * Initializes the network resilience analysis panel.
     */
    public final void initializeResiliencePanel() {
        resiliencePanel = new JPanel();
        resiliencePanel.setLayout(new BoxLayout(resiliencePanel, BoxLayout.Y_AXIS));
        resiliencePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
                "Network Resilience",
                TitledBorder.LEFT, TitledBorder.TOP));

        resilienceSummaryLabel = new JLabel("<html>Click 'Analyze' to simulate attack scenarios.</html>");
        resilienceSummaryLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        resilienceDetailsLabel = new JLabel("");
        resilienceDetailsLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);

        resilienceAnalyzeButton = new JButton("Analyze");
        resilienceAnalyzeButton.addActionListener(e -> { runResilienceAnalysis(); });

        resilienceExportButton = new JButton("Export CSV");
        resilienceExportButton.setEnabled(false);
        resilienceExportButton.addActionListener(e -> { exportResilienceCSV(); });

        buttonPanel.add(resilienceAnalyzeButton);
        buttonPanel.add(Box.createHorizontalStrut(5));
        buttonPanel.add(resilienceExportButton);

        resiliencePanel.add(resilienceSummaryLabel);
        resiliencePanel.add(Box.createVerticalStrut(4));
        resiliencePanel.add(buttonPanel);
        resiliencePanel.add(Box.createVerticalStrut(4));
        resiliencePanel.add(resilienceDetailsLabel);
    }

    private GraphResilienceAnalyzer lastResilienceAnalyzer;

    private void runResilienceAnalysis() {
        if (g == null || g.getVertexCount() == 0) {
            resilienceSummaryLabel.setText("<html>No graph loaded.</html>");
            return;
        }
        resilienceSummaryLabel.setText("<html><i>Analyzing resilience...</i></html>");
        resiliencePanel.repaint();

        SwingWorker<GraphResilienceAnalyzer, Void> worker =
                new SwingWorker<GraphResilienceAnalyzer, Void>() {
            @Override
            protected GraphResilienceAnalyzer doInBackground() {
                GraphResilienceAnalyzer analyzer = new GraphResilienceAnalyzer(g);
                analyzer.analyze();
                return analyzer;
            }
            @Override
            protected void done() {
                try {
                    GraphResilienceAnalyzer analyzer = get();
                    lastResilienceAnalyzer = analyzer;
                    displayResilienceResults(analyzer);
                    resilienceExportButton.setEnabled(true);
                } catch (Exception ex) {
                    resilienceSummaryLabel.setText("<html><b>Error:</b> " + ex.getMessage() + "</html>");
                }
            }
        };
        worker.execute();
    }

    private void displayResilienceResults(GraphResilienceAnalyzer analyzer) {
        double rRandom = analyzer.computeRobustnessIndex(analyzer.getRandomAttackCurve());
        double rDegree = analyzer.computeRobustnessIndex(analyzer.getDegreeAttackCurve());
        double rBetweenness = analyzer.computeRobustnessIndex(analyzer.getBetweennessAttackCurve());

        StringBuilder summary = new StringBuilder("<html>");
        summary.append("<b>Robustness Index</b> (higher = more resilient):<br/>");
        summary.append(String.format("&nbsp;&nbsp;Random: <b>%.4f</b><br/>", rRandom));
        summary.append(String.format("&nbsp;&nbsp;Degree: <b>%.4f</b><br/>", rDegree));
        summary.append(String.format("&nbsp;&nbsp;Betweenness: <b>%.4f</b>", rBetweenness));
        summary.append("</html>");
        resilienceSummaryLabel.setText(summary.toString());

        StringBuilder details = new StringBuilder("<html>");
        if (rRandom > rDegree * 1.5) {
            details.append("<b>Scale-free topology:</b> Vulnerable to<br/>targeted attacks on hubs.<br/>");
        } else if (rRandom < rDegree * 1.1) {
            details.append("<b>Homogeneous topology:</b> No dominant<br/>hub structure.<br/>");
        } else {
            details.append("<b>Moderate hub dependency.</b><br/>");
        }

        List<GraphResilienceAnalyzer.ResilienceStep> degreeCurve = analyzer.getDegreeAttackCurve();
        if (degreeCurve.size() > 1) {
            details.append("<br/><b>Most impactful removals:</b><br/>");
            int shown = Math.min(5, degreeCurve.size() - 1);
            for (int i = 1; i <= shown; i++) {
                GraphResilienceAnalyzer.ResilienceStep step = degreeCurve.get(i);
                if (step.getRemovedNode() != null) {
                    int lccDrop = degreeCurve.get(i - 1).getLargestComponentSize()
                            - step.getLargestComponentSize();
                    details.append(String.format("&nbsp;&nbsp;%s (LCC -%d)<br/>",
                            step.getRemovedNode(), lccDrop));
                }
            }
        }
        details.append("</html>");
        resilienceDetailsLabel.setText(details.toString());
    }

    private void exportResilienceCSV() {
        if (lastResilienceAnalyzer == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("resilience_analysis.csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                java.io.FileWriter writer = new java.io.FileWriter(chooser.getSelectedFile());
                writer.write(lastResilienceAnalyzer.exportCSV());
                writer.close();
                JOptionPane.showMessageDialog(this, "Resilience data exported.",
                        "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Initializes the ego network search panel with search field,
     * search/clear buttons, and labels for summary and neighbor list.
     */
    public final void initializeEgoPanel() {
        egoPanel = new JPanel();
        egoPanel.setLayout(new BoxLayout(egoPanel, BoxLayout.Y_AXIS));
        egoPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
                "Ego Network",
                TitledBorder.LEFT, TitledBorder.TOP));

        egoOverlayActive = false;
        egoCenter = null;
        egoNeighbors = new HashSet<>();
        egoEdges = new HashSet<>();

        JPanel searchRow = new JPanel();
        searchRow.setLayout(new BoxLayout(searchRow, BoxLayout.X_AXIS));
        searchRow.setAlignmentX(JPanel.LEFT_ALIGNMENT);

        egoSearchField = new JTextField(10);
        egoSearchField.setMaximumSize(new Dimension(150, 25));
        egoSearchField.setToolTipText("Enter node ID to explore its ego network");

        egoSearchButton = new JButton("Search");
        egoSearchButton.addActionListener(e -> { runEgoSearch(); });

        egoClearButton = new JButton("Clear");
        egoClearButton.setEnabled(false);
        egoClearButton.addActionListener(e -> { clearEgoOverlay(); });

        egoSearchField.addActionListener(e -> { runEgoSearch(); });

        searchRow.add(new JLabel("Node: "));
        searchRow.add(egoSearchField);
        searchRow.add(Box.createHorizontalStrut(5));
        searchRow.add(egoSearchButton);
        searchRow.add(Box.createHorizontalStrut(5));
        searchRow.add(egoClearButton);

        egoSummaryLabel = new JLabel("<html>Search for a node to see its ego network.</html>");
        egoSummaryLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        egoNeighborListLabel = new JLabel("");
        egoNeighborListLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);

        egoPanel.add(searchRow);
        egoPanel.add(Box.createVerticalStrut(4));
        egoPanel.add(egoSummaryLabel);
        egoPanel.add(Box.createVerticalStrut(4));
        egoPanel.add(egoNeighborListLabel);
    }

    private void runEgoSearch() {
        String query = egoSearchField.getText().trim();
        if (query.isEmpty()) {
            egoSummaryLabel.setText("<html>Enter a node ID.</html>");
            return;
        }
        if (g == null || g.getVertexCount() == 0) {
            egoSummaryLabel.setText("<html>No graph loaded.</html>");
            return;
        }

        // Find the node (exact match first, then case-insensitive, then partial)
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
            egoSummaryLabel.setText("<html><b>Node not found:</b> " + query + "</html>");
            egoNeighborListLabel.setText("");
            return;
        }

        // Build ego network
        egoCenter = foundNode;
        egoNeighbors.clear();
        egoEdges.clear();

        Collection<String> neighbors = g.getNeighbors(foundNode);
        if (neighbors != null) {
            egoNeighbors.addAll(neighbors);
        }

        // Collect edges: center-to-neighbor and neighbor-to-neighbor
        for (edge e : g.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            boolean v1InEgo = v1.equals(egoCenter) || egoNeighbors.contains(v1);
            boolean v2InEgo = v2.equals(egoCenter) || egoNeighbors.contains(v2);
            if (v1InEgo && v2InEgo) {
                egoEdges.add(e);
            }
        }

        egoOverlayActive = true;
        egoClearButton.setEnabled(true);
        syncRenderers();
        vv.repaint();

        // Count edge types
        Map<String, Integer> typeCounts = new HashMap<>();
        for (edge e : g.getEdges()) {
            if (e.getVertex1().equals(egoCenter) || e.getVertex2().equals(egoCenter)) {
                String typeLabel = e.getType();
                EdgeType et = EdgeType.fromCode(e.getType());
                if (et != null) typeLabel = et.getDisplayLabel();
                typeCounts.put(typeLabel, typeCounts.getOrDefault(typeLabel, 0) + 1);
            }
        }

        StringBuilder summary = new StringBuilder("<html>");
        summary.append(String.format("<b>Node:</b> %s<br/>", egoCenter));
        summary.append(String.format("<b>Degree:</b> %d<br/>", egoNeighbors.size()));
        summary.append(String.format("<b>Ego edges:</b> %d (incl. inter-neighbor)<br/>", egoEdges.size()));
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
        egoSummaryLabel.setText(summary.toString());

        // Neighbor list (limited to 20)
        List<String> sortedNeighbors = new ArrayList<>(egoNeighbors);
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
        egoNeighborListLabel.setText(neighborHtml.toString());
    }

    private void clearEgoOverlay() {
        egoOverlayActive = false;
        egoCenter = null;
        egoNeighbors.clear();
        egoEdges.clear();
        egoClearButton.setEnabled(false);
        egoSummaryLabel.setText("<html>Search for a node to see its ego network.</html>");
        egoNeighborListLabel.setText("");
        syncRenderers();
        vv.repaint();
    }

    /**
     * Runs the articulation point and bridge analysis.
     */
    private void runArticulationAnalysis() {
        if (g == null || g.getVertexCount() == 0) {
            articulationSummaryLabel.setText("<html>No graph loaded.</html>");
            return;
        }

        ArticulationPointAnalyzer analyzer = new ArticulationPointAnalyzer(g);
        ArticulationPointAnalyzer.AnalysisResult result = analyzer.analyze();

        articulationOverlayActive = true;
        syncRenderers();
        articulationPoints.clear();
        bridgeEdges.clear();
        articulationPoints.addAll(result.getArticulationPoints());
        for (ArticulationPointAnalyzer.Bridge b : result.getBridges()) {
            bridgeEdges.add(b.getEdge());
        }

        // Resilience label
        articulationResilienceLabel.setText(String.format(
                "Resilience: %.0f/100 (%s)", result.getResilienceScore(),
                result.getVulnerabilityLevel()));

        // Summary
        StringBuilder sb = new StringBuilder("<html>");
        sb.append(String.format("<b>Cut vertices:</b> %d (%.1f%%)<br/>",
                result.getArticulationPointCount(),
                result.getArticulationPointPercentage()));
        sb.append(String.format("<b>Bridges:</b> %d<br/>", result.getBridgeCount()));
        sb.append(String.format("<b>Components:</b> %d", result.getConnectedComponents()));
        sb.append("</html>");
        articulationSummaryLabel.setText(sb.toString());

        // Details: top articulation points and bridges
        StringBuilder details = new StringBuilder("<html>");
        List<ArticulationPointAnalyzer.ArticulationPointInfo> apDetails =
                result.getArticulationPointDetails();
        if (!apDetails.isEmpty()) {
            details.append("<b>Critical nodes:</b><br/>");
            int shown = Math.min(5, apDetails.size());
            for (int i = 0; i < shown; i++) {
                ArticulationPointAnalyzer.ArticulationPointInfo info = apDetails.get(i);
                details.append(String.format("  Node %s (deg=%d, crit=%.1f)<br/>",
                        info.getVertex(), info.getDegree(), info.getCriticality()));
            }
            if (apDetails.size() > 5) {
                details.append(String.format("  ... and %d more<br/>", apDetails.size() - 5));
            }
        }
        List<ArticulationPointAnalyzer.Bridge> bridges = result.getBridges();
        if (!bridges.isEmpty()) {
            details.append("<b>Bridges:</b><br/>");
            int shown = Math.min(5, bridges.size());
            for (int i = 0; i < shown; i++) {
                ArticulationPointAnalyzer.Bridge bridge = bridges.get(i);
                details.append(String.format("  %s—%s (sev=%.2f, split=%d/%d)<br/>",
                        bridge.getEndpoint1(), bridge.getEndpoint2(),
                        bridge.getSeverity(),
                        bridge.getComponentSizeA(), bridge.getComponentSizeB()));
            }
            if (bridges.size() > 5) {
                details.append(String.format("  ... and %d more<br/>", bridges.size() - 5));
            }
        }
        if (apDetails.isEmpty() && bridges.isEmpty()) {
            details.append("<i>No critical elements — network is robust.</i>");
        }
        details.append("</html>");
        articulationDetailsLabel.setText(details.toString());

        // Refresh visualization to highlight critical elements
        if (vv != null) { syncRenderers(); vv.repaint(); }
        articulationPanel.revalidate();
        articulationPanel.repaint();
    }

    /**
     * Clears the articulation point analysis and resets the panel.
     */
    private void clearArticulationAnalysis() {
        articulationOverlayActive = false;
        syncRenderers();
        articulationPoints.clear();
        bridgeEdges.clear();
        articulationResilienceLabel.setText("Resilience: —");
        articulationSummaryLabel.setText("<html>Click 'Analyze' to find critical nodes and edges.</html>");
        articulationDetailsLabel.setText("");
        if (vv != null) { syncRenderers(); vv.repaint(); }
        articulationPanel.revalidate();
        articulationPanel.repaint();
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
     * JSplitPane construction — adding/removing panels now requires
     * only editing the array and heights, not restructuring nesting.</p>
     */
    public final void showRightPane() {

        JLabel parameterHeading = new JLabel("Communities", JLabel.CENTER);
        parameterHeading.setPreferredSize(new Dimension(300, 30));
        JSplitPane headerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                parameterHeading, parameterSpace);

        // Panels in display order, with their preferred divider heights.
        // To add a new panel, just add an entry here — no nesting changes needed.
        java.awt.Component[] panels = {
            headerSplit,
            notesPanel,
            pathPanel,
            communityPanel,
            mstPanel,
            centralityPanel,
            articulationPanel,
            resiliencePanel,
            egoPanel,
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
     * initialize the category panel — creates one {@link CategoryRow}
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

    // copyfile() removed — replaced with FileUtils.copyFile() from commons-io
    // (which was already a project dependency). The hand-rolled byte-copy loop
    // duplicated well-tested library code and missed features like atomic
    // writes and proper error cleanup.

    /**
     *main function
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
