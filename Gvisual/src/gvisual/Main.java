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
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
import javax.swing.event.ChangeEvent;
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
    private JPanel statsPanel;
    private JLabel statsNodeCount;
    private JLabel statsEdgeCount;
    private JLabel statsFriendCount;
    private JLabel statsClassmateCount;
    private JLabel statsFsCount;
    private JLabel statsStrangerCount;
    private JLabel statsStudyGCount;
    private JLabel statsDensity;
    private JLabel statsAvgDegree;
    private JLabel statsMaxDegree;
    private JLabel statsAvgWeight;
    private JLabel statsIsolated;
    private JLabel statsTopNodes;

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
                if (y.getType().equals("f")) {
                    isF = true;
                } else if (y.getType().equals("fs")) {
                    isFs = true;
                } else if (y.getType().equals("c")) {
                    isC = true;
                } else if (y.getType().equals("s")) {
                    isS = true;
                } else if (y.getType().equals("sg")) {
                    isSg = true;
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
        Random generator = new Random(System.nanoTime());


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
     * Positions a given set of vertices at certain location
     * @param vertices list of vertices to cluster
     * @param y y-position of cluster
     * @param x x-position of cluster
     */
    public void back_positionCluster(List<String> vertices, int y, int x) {
        int curX = x * 300 + 20;
        int curY = y * 200 + 20;
        for (String v : vertices) {
            graphLayout.setLocation(v, new java.awt.Point(curX, curY));
            if (curX + 30 > x * 300 + 200) {
                curX = x * 300 + 20;
                curY = curY + 30;
            } else {
                curX += 50;
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
        return new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if (!slider.getValueIsAdjusting()) {
                    try {
                        addGraph();
                    } catch (ParserConfigurationException ex) {
                        Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (IOException ex) {
                        Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (SAXException ex) {
                        Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                    }
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
        cb.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (cb.isSelected()) {
                    for (edge x : edgeList) { g.addEdge(x, x.getVertex1(), x.getVertex2()); }
                } else {
                    for (edge x : edgeList) { g.removeEdge(x); }
                }
                imagePanel.setVisible(false);
                imagePanel.setVisible(true);
            }
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

        settingsBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                row.showParams = !row.showParams;
                paintCategoryPanel();
            }
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

            //System.out.println("number of line in the graph = "+count);
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
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }



        if (g != null && prevTimeline != timeline.getValue() && g.getEdgeCount() != 0) {
            OldVertices = g.getVertices();
            prevTimeline = timeline.getValue();
        }


        g = new UndirectedSparseGraph<String, edge>();


        friendEdges.clear();
        fsEdges.clear();
        strangerEdges.clear();
        classmateEdges.clear();
        studyGEdges.clear();

        File database = new File(fileName);
        LineIterator lineIterator = null;

        try {
            lineIterator = FileUtils.lineIterator(database);
            int count = 0;   // count =0 for nodes and count = 1 for edges
            while (lineIterator.hasNext()) {
                String line = lineIterator.nextLine();
                if (line.equalsIgnoreCase("nodes")) {
                    count = 0;
                } else if (line.equalsIgnoreCase("edges")) {
                    count = 1;
                } else {
                    if (count == 0) {
                        final String[] nodeParam = line.split(" ");
                        if (nodeParam.length < 1 || nodeParam[0].isEmpty()) {
                            continue; // skip malformed node lines
                        }
                        g.addVertex(nodeParam[0]);
                        //graphLayout.setLocation(nodeParam[0], new Point(Integer.parseInt(nodeParam[1]), Integer.parseInt(nodeParam[2])));
                    } else {



                        String[] edgeParam = line.split(" ");
                        // Validate edge line: need at least 4 fields
                        // (type, vertex1, vertex2, weight)
                        if (edgeParam.length < 4) {
                            System.err.println("Skipping malformed edge line: " + line);
                            continue;
                        }
                        float weight;
                        try {
                            weight = Float.parseFloat(edgeParam[3]);
                        } catch (NumberFormatException nfe) {
                            System.err.println("Skipping edge with invalid weight: " + line);
                            continue;
                        }
                        if (Float.isNaN(weight) || Float.isInfinite(weight)) {
                            System.err.println("Skipping edge with non-finite weight: " + line);
                            continue;
                        }
                        edge curEdge = new edge(edgeParam[0], edgeParam[1], edgeParam[2]);
                        curEdge.setWeight(weight);

                        // Classify edge by type and add to the appropriate list
                        EdgeType edgeType = EdgeType.fromCode(edgeParam[0]);
                        if (edgeType != null) {
                            List<edge> targetList = getEdgeList(edgeType);
                            if (targetList != null) {
                                // Set label on first edge of each type for the legend
                                boolean alreadyLabelled = !targetList.isEmpty()
                                    && targetList.stream().anyMatch(e -> e.getLabel() != null);
                                if (!alreadyLabelled) {
                                    curEdge.setLabel(edgeType.getDisplayLabel());
                                }
                                targetList.add(curEdge);
                            }
                        }

                        if (!isEdgeTypeVisible(edgeParam[0])) {
                            continue;
                        }

                        g.addEdge(curEdge, edgeParam[1], edgeParam[2]);
                    }
                }


            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            LineIterator.closeQuietly(lineIterator);
        }

        createLayout();
        vv = new VisualizationViewer<String, edge>(graphLayout);
        vv.setSize(new Dimension(100, 0));

        DefaultModalGraphMouse gm = new DefaultModalGraphMouse();

        gm.setMode(ModalGraphMouse.Mode.TRANSFORMING);
        vv.setGraphMouse(gm);


        Transformer<edge, String> edgeLabel = new Transformer<edge, String>() {

            public String transform(edge i) {
                return i.getLabel();
            }
        };

        vv.getRenderContext().setEdgeLabelTransformer(edgeLabel);
        vv.setBackground(DEFAULT_BG_COLOR);
        Transformer<String, String> vertexLabel = new Transformer<String, String>() {

            public String transform(String i) {
                return i;
            }
        };
        vv.setForeground(Color.white);
        vv.getRenderContext().setVertexLabelTransformer(vertexLabel);


        Transformer<edge, Paint> edgePaint = new Transformer<edge, Paint>() {

            public Paint transform(edge edge) {
                // Highlight edges in the shortest path
                if (pathEdges != null && pathEdges.contains(edge)) {
                    return Color.YELLOW;
                }
                // MST overlay — highlight MST edges in bright green
                if (mstOverlayActive && mstEdges != null && mstEdges.contains(edge)) {
                    return new Color(0, 255, 100);
                }
                // Articulation overlay — highlight bridge edges in red-orange
                if (articulationOverlayActive && bridgeEdges != null && bridgeEdges.contains(edge)) {
                    return new Color(255, 80, 40);
                }
                // Community overlay mode — color edges by community
                if (communityOverlayActive && nodeCommunityMap != null) {
                    Integer c1 = nodeCommunityMap.get(edge.getVertex1());
                    Integer c2 = nodeCommunityMap.get(edge.getVertex2());
                    if (c1 != null && c2 != null && c1.equals(c2)) {
                        Color base = COMMUNITY_COLORS[c1 % COMMUNITY_COLORS.length];
                        // Slightly transparent version for edges
                        return new Color(base.getRed(), base.getGreen(), base.getBlue(), 180);
                    }
                    return new Color(100, 100, 100, 80); // cross-community edges are dim
                }
                // MST overlay — dim non-MST edges
                if (mstOverlayActive && mstEdges != null && !mstEdges.contains(edge)) {
                    return new Color(80, 80, 80, 60);
                }
                return EdgeType.colorForCode(edge.getType());
            }
        };
        vv.getRenderContext().setEdgeDrawPaintTransformer(edgePaint);

        Transformer<String, Paint> vertexPaint = new Transformer<String, Paint>() {

            public Paint transform(String vertex) {
                // Community overlay mode — color by community
                if (communityOverlayActive && nodeCommunityMap != null) {
                    Integer cid = nodeCommunityMap.get(vertex);
                    if (cid != null) {
                        return COMMUNITY_COLORS[cid % COMMUNITY_COLORS.length];
                    }
                }
                // Articulation overlay — highlight cut vertices in red-orange
                if (articulationOverlayActive && articulationPoints != null
                        && articulationPoints.contains(vertex)) {
                    return new Color(255, 80, 40);
                }
                // Highlight source node in cyan, target in magenta, path nodes in yellow
                if (pathSource != null && vertex.equals(pathSource)) {
                    return Color.CYAN;
                }
                if (pathTarget != null && vertex.equals(pathTarget)) {
                    return Color.MAGENTA;
                }
                if (pathVertices != null && pathVertices.contains(vertex)) {
                    return Color.YELLOW;
                }
                // Determine vertex colour based on connected edge types
                Set<EdgeType> connectedTypes = new HashSet<>();
                for (edge x : g.getOutEdges(vertex)) {
                    EdgeType et = EdgeType.fromCode(x.getType());
                    if (et != null) {
                        connectedTypes.add(et);
                    }
                }
                if (connectedTypes.size() > 1) {
                    return Vertex_COLOR;
                } else if (connectedTypes.size() == 1) {
                    return connectedTypes.iterator().next().getColor();
                } else {
                    return Vertex_COLOR;
                }
            }
        };
        vv.getRenderContext().setVertexFillPaintTransformer(vertexPaint);

        Transformer<String, Shape> vertexShape = new Transformer<String, Shape>() {

            public Shape transform(String vertex) {
                // Make path source/target/intermediate nodes larger and distinct
                if (pathSource != null && vertex.equals(pathSource)) {
                    return new Ellipse2D.Double(-8, -8, 16, 16);
                }
                if (pathTarget != null && vertex.equals(pathTarget)) {
                    return new Ellipse2D.Double(-8, -8, 16, 16);
                }
                if (pathVertices != null && pathVertices.contains(vertex)) {
                    return new Ellipse2D.Double(-6, -6, 12, 12);
                }
                // Enlarge articulation points
                if (articulationOverlayActive && articulationPoints != null
                        && articulationPoints.contains(vertex)) {
                    return new Ellipse2D.Double(-8, -8, 16, 16);
                }
                if (OldVertices != null) {
                    if (OldVertices.contains(vertex)) {
                        return new Ellipse2D.Double(-5, -5, 10, 10);
                    } else {
                        return new Ellipse2D.Double(-5, -5, 20, 20);
                    }
                } else {
                    return new Ellipse2D.Double(-5, -5, 10, 10);
                }
            }
        };

        vv.getRenderContext().setVertexShapeTransformer(vertexShape);

        Transformer<edge, Stroke> edgeWeight = new Transformer<edge, Stroke>() {

            public Stroke transform(edge i) {
                // Highlight path edges with thicker solid stroke
                if (pathEdges != null && pathEdges.contains(i)) {
                    return new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
                }
                // MST edges get thicker solid stroke
                if (mstOverlayActive && mstEdges != null && mstEdges.contains(i)) {
                    return new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
                }
                // Bridge edges get thick dashed stroke for visibility
                if (articulationOverlayActive && bridgeEdges != null && bridgeEdges.contains(i)) {
                    float[] dashPattern = {6.0f, 4.0f};
                    return new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                            10.0f, dashPattern, 0.0f);
                }
                float dash[] = {1.0f};
                float width = i.getWeight() / 40 + 1.0f;
                return new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 10.0f, dash, 0.0f);
            }
        };
        vv.getRenderContext().setEdgeStrokeTransformer(edgeWeight);

        imagePanel.add(vv);

        imagePanel.setVisible(false);
        imagePanel.setVisible(true);

        updateStatsPanel();

        System.out.println("added the graph");
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
        pathFindButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!pathFindingMode) {
                    enablePathFindingMode();
                } else {
                    disablePathFindingMode();
                }
            }
        });

        pathClearButton = new JButton("Clear Path");
        pathClearButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        pathClearButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                clearPath();
            }
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
        communityDetectButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                runCommunityDetection();
            }
        });

        communityClearButton = new JButton("Clear");
        communityClearButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        communityClearButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                clearCommunityOverlay();
            }
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
        mstComputeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                runMSTComputation();
            }
        });

        mstClearButton = new JButton("Clear");
        mstClearButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        mstClearButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                clearMSTOverlay();
            }
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
        centralityMetricCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (centralityActive) {
                    updateCentralityRanking();
                }
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
        centralityComputeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                runCentralityAnalysis();
            }
        });

        centralityClearButton = new JButton("Clear");
        centralityClearButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        centralityClearButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                clearCentralityAnalysis();
            }
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
        Collections.sort(sorted, new Comparator<NodeCentralityAnalyzer.CentralityResult>() {
            public int compare(NodeCentralityAnalyzer.CentralityResult a,
                               NodeCentralityAnalyzer.CentralityResult b) {
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
            }
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
        articulationComputeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                runArticulationAnalysis();
            }
        });

        articulationClearButton = new JButton("Clear");
        articulationClearButton.setAlignmentX(JButton.LEFT_ALIGNMENT);
        articulationClearButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                clearArticulationAnalysis();
            }
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
        if (vv != null) vv.repaint();
        articulationPanel.revalidate();
        articulationPanel.repaint();
    }

    /**
     * Clears the articulation point analysis and resets the panel.
     */
    private void clearArticulationAnalysis() {
        articulationOverlayActive = false;
        articulationPoints.clear();
        bridgeEdges.clear();
        articulationResilienceLabel.setText("Resilience: —");
        articulationSummaryLabel.setText("<html>Click 'Analyze' to find critical nodes and edges.</html>");
        articulationDetailsLabel.setText("");
        if (vv != null) vv.repaint();
        articulationPanel.revalidate();
        articulationPanel.repaint();
    }

    /**
     * Initializes the network statistics panel showing real-time graph metrics.
     */
    public final void initializeStatsPanel() {
        statsPanel = new JPanel();
        statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));
        statsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
                "Network Statistics",
                TitledBorder.CENTER,
                TitledBorder.TOP));

        Font labelFont = new Font("SansSerif", Font.PLAIN, 12);
        Font valueFont = new Font("SansSerif", Font.BOLD, 12);

        statsNodeCount = createStatsLabel("Nodes: 0", labelFont);
        statsEdgeCount = createStatsLabel("Edges: 0 (visible) / 0 (total)", labelFont);
        statsFriendCount = createStatsLabel("  Friends: 0", labelFont);
        statsFriendCount.setForeground(EdgeType.FRIEND.getColor());
        statsClassmateCount = createStatsLabel("  Classmates: 0", labelFont);
        statsClassmateCount.setForeground(EdgeType.CLASSMATE.getColor());
        statsFsCount = createStatsLabel("  Fam. Strangers: 0", labelFont);
        statsFsCount.setForeground(EdgeType.FAMILIAR.getColor());
        statsStrangerCount = createStatsLabel("  Strangers: 0", labelFont);
        statsStrangerCount.setForeground(EdgeType.STRANGER.getColor());
        statsStudyGCount = createStatsLabel("  Study Groups: 0", labelFont);
        statsStudyGCount.setForeground(EdgeType.STUDY_GROUP.getColor());
        statsDensity = createStatsLabel("Density: 0.000", labelFont);
        statsAvgDegree = createStatsLabel("Avg Degree: 0.00", labelFont);
        statsMaxDegree = createStatsLabel("Max Degree: 0", labelFont);
        statsAvgWeight = createStatsLabel("Avg Weight: 0.00", labelFont);
        statsIsolated = createStatsLabel("Isolated Nodes: 0", labelFont);
        statsTopNodes = createStatsLabel("<html>Top Nodes: —</html>", labelFont);

        statsPanel.add(statsNodeCount);
        statsPanel.add(statsEdgeCount);
        statsPanel.add(Box.createVerticalStrut(4));
        statsPanel.add(statsFriendCount);
        statsPanel.add(statsClassmateCount);
        statsPanel.add(statsFsCount);
        statsPanel.add(statsStrangerCount);
        statsPanel.add(statsStudyGCount);
        statsPanel.add(Box.createVerticalStrut(4));
        statsPanel.add(statsDensity);
        statsPanel.add(statsAvgDegree);
        statsPanel.add(statsMaxDegree);
        statsPanel.add(statsAvgWeight);
        statsPanel.add(statsIsolated);
        statsPanel.add(Box.createVerticalStrut(4));
        statsPanel.add(statsTopNodes);
    }

    private JLabel createStatsLabel(String text, Font font) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        return label;
    }

    /**
     * Updates the statistics panel with current graph metrics.
     */
    private void updateStatsPanel() {
        if (statsPanel == null || g == null) return;

        GraphStats stats = new GraphStats(g, friendEdges, fsEdges,
                classmateEdges, strangerEdges, studyGEdges);

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

        java.util.List<String> topNodes = stats.getTopNodes(3);
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

        statsPanel.revalidate();
        statsPanel.repaint();
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
            statsPanel,
        };
        int[] dividerLocations = { 400, 510, 640, 760, 920, 1070, 1250 };

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

        ActionListener taskPerformer = new ActionListener() {

            public void actionPerformed(ActionEvent evt) {
                int i = timeline.getValue();
                i++;
                timeline.setValue(i);
            }
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




        timeline.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                updateTime();
                timeline.setBorder(BorderFactory.createTitledBorder(timeStamp));
                if (!timeline.getValueIsAdjusting()) {
                    try {
                        addGraph();
                    } catch (ParserConfigurationException ex) {
                        Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (IOException ex) {
                        Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (SAXException ex) {
                        Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                    }
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
                    System.out.println("Play pressed");
                    timer.start();
                } else if (e.getComponent() == pauseButton) {
                    System.out.println("Stop pressed");
                    timer.stop();
                } else if (e.getComponent() == stopButton) {
                    System.out.println("Stop pressed");
                    timeline.setValue(1);
                    timer.stop();
                } else if (e.getComponent() == slowButton) {
                    System.out.println("Slow pressed");
                    DELAY = DELAY * 2;
                    timer.setDelay(DELAY);
                    System.out.println(timer.getDelay() + "");
                } else if (e.getComponent() == fastButton) {
                    System.out.println("Fast pressed");
                    DELAY = DELAY / 2;
                    timer.setDelay(DELAY);
                    System.out.println(timer.getDelay() + "");
                } else if (e.getComponent() == nextButton) {
                    try {
                        System.out.println("next pressed");
                        nextOrPrevGraph("next");
                        timer.stop();
                    } catch (Exception ex) {
                        Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else if (e.getComponent() == prevButton) {
                    try {
                        System.out.println("Prev pressed");
                        nextOrPrevGraph("prev");
                        timer.stop();
                    } catch (Exception ex) {
                        Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
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
     * initialize the toolbar
     */
    public final void initializeToolBar() {
        toolPanel = new JPanel();
        toolPanel.setPreferredSize(new Dimension(150, 610));
        toolPanel.setBackground(Color.white);
        toolPanel.setBorder(BorderFactory.createTitledBorder("Tools"));

        JButton pickMode = new JButton("<html><center>Select Node Mode<br/>Use this to<br/> select and<br/> move vertices<br/> at different <br/>position<center></html>");
        pickMode.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                DefaultModalGraphMouse gm = new DefaultModalGraphMouse();

                gm.setMode(ModalGraphMouse.Mode.PICKING);
                vv.setGraphMouse(gm);
            }
        });
        pickMode.setPreferredSize(new Dimension(140, 100));
        JButton transformMode = new JButton("<html><center>Move/rotate graph<br/>Use this to <br/>move and rotate<br/> entire graph</center></html>");
        transformMode.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                DefaultModalGraphMouse gm = new DefaultModalGraphMouse();

                gm.setMode(ModalGraphMouse.Mode.TRANSFORMING);
                vv.setGraphMouse(gm);
            }
        });
        transformMode.setPreferredSize(new Dimension(140, 100));
        toolPanel.add(pickMode);
        toolPanel.add(transformMode);

        JButton snapShotButton = new JButton("<html><center>Take a snapshot<br/>Use this to<br/> take and image<br/> of the current view<br/> of the graph</center></html>");
        snapShotButton.setPreferredSize(new Dimension(140, 100));
        snapShotButton.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {


                JFileChooser fileChooser;
                int count = 0;
                do {
                    if (count != 0) {
                        JOptionPane.showMessageDialog(null, "File with same name already exists!!!", "Error!!", 1);
                    }
                    count++;
                    fileChooser = new JFileChooser(System.getProperty("user.dir"));
                    int returnVal = fileChooser.showSaveDialog(null);
                } while (fileChooser.getSelectedFile().exists());
                File curFile = new File(fileChooser.getSelectedFile().toString() + ".png");
                try {
                    curFile.createNewFile();
                } catch (IOException ex) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }
                PNGDump dumper = new PNGDump();
                try {
                    dumper.dumpComponent(curFile, vv);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }


            }
        });
        toolPanel.add(snapShotButton);

        JButton exportButton = new JButton("<html><center>Export edgelist<br/>Export the graph<br/> edge list in<br/> CSV format.</center></html>");
        exportButton.setPreferredSize(new Dimension(140, 100));
        exportButton.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser;
                int count = 0;
                do {
                    if (count != 0) {
                        JOptionPane.showMessageDialog(null, "File with same name already exists!!!", "Error!!", 1);
                    }
                    count++;
                    fileChooser = new JFileChooser(System.getProperty("user.dir"));
                    int returnVal = fileChooser.showSaveDialog(null);
                } while (fileChooser.getSelectedFile().exists());
                try {
                    fileChooser.getSelectedFile().createNewFile();
                } catch (IOException ex) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }
                try {
                    // Use commons-io FileUtils (already a project dependency)
                    // instead of the hand-rolled byte-copy loop.
                    FileUtils.copyFile(new File("./graph.txt"), fileChooser.getSelectedFile());
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IOException ex) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
        toolPanel.add(exportButton);

        JButton graphmlButton = new JButton("<html><center>Export GraphML<br/>Export to GraphML<br/> for Gephi,<br/> Cytoscape, yEd,<br/> NetworkX</center></html>");
        graphmlButton.setPreferredSize(new Dimension(140, 100));
        graphmlButton.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                // Collect all edges from all categories
                java.util.List<edge> allEdges = new java.util.ArrayList<edge>();
                allEdges.addAll(friendEdges);
                allEdges.addAll(fsEdges);
                allEdges.addAll(classmateEdges);
                allEdges.addAll(strangerEdges);
                allEdges.addAll(studyGEdges);

                GraphMLExporter exporter = new GraphMLExporter(g, allEdges);
                exporter.setTimestamp(timeStamp);
                exporter.setDescription("GraphVisual network — student community evolution");

                JFileChooser fileChooser = new JFileChooser(System.getProperty("user.dir"));
                fileChooser.setDialogTitle("Export as GraphML");
                fileChooser.setSelectedFile(new File("graph_" + timeStamp + ".graphml"));
                int returnVal = fileChooser.showSaveDialog(null);
                if (returnVal != JFileChooser.APPROVE_OPTION) return;

                File outFile = fileChooser.getSelectedFile();
                if (!outFile.getName().endsWith(".graphml")) {
                    outFile = new File(outFile.getAbsolutePath() + ".graphml");
                }

                if (outFile.exists()) {
                    int confirm = JOptionPane.showConfirmDialog(null,
                            "File already exists. Overwrite?",
                            "Confirm Overwrite", JOptionPane.YES_NO_OPTION);
                    if (confirm != JOptionPane.YES_OPTION) return;
                }

                try {
                    exporter.export(outFile);
                    JOptionPane.showMessageDialog(null,
                            "GraphML exported successfully!\n"
                            + "Nodes: " + exporter.getVertexCount() + "\n"
                            + "Edges: " + exporter.getEdgeCount() + "\n"
                            + "File: " + outFile.getName(),
                            "Export Complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (IOException ex1) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex1);
                    JOptionPane.showMessageDialog(null,
                            "Export failed: " + ex1.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        toolPanel.add(graphmlButton);

        JButton heatmapButton = new JButton("<html><center>Adjacency Matrix<br/>View graph as a<br/> color-coded<br/> heatmap matrix<br/> with zoom/pan</center></html>");
        heatmapButton.setPreferredSize(new Dimension(140, 100));
        heatmapButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JDialog heatmapDialog = AdjacencyMatrixHeatmap.createDialog(Main.this, g);
                heatmapDialog.setVisible(true);
            }
        });
        toolPanel.add(heatmapButton);

        toolPanel.add(legendPanel);
        contentPanel.add(toolPanel, BorderLayout.WEST);
    }

    // copyfile() removed — replaced with FileUtils.copyFile() from commons-io
    // (which was already a project dependency). The hand-rolled byte-copy loop
    // duplicated well-tested library code and missed features like atomic
    // writes and proper error cleanup.

    /**
     *main function
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {

            public void run() {
                Main ex;
                try {
                    ex = new Main();
                    ex.setVisible(true);
                } catch (Exception ex1) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex1);
                }
            }
        });
    }
}
