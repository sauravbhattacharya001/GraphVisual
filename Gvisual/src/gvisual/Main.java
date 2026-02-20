/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Vector;

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
    private static final Color FRIEND_COLOR = Color.GREEN;
    private static final Color FS_COLOR = Color.GRAY;
    private static final Color CLASSMATES_COLOR = Color.BLUE;
    private static final Color STRANGER_COLOR = Color.RED;
    private static final Color STUDYG_COLOR = Color.ORANGE;
    private static final Color Vertex_COLOR = Color.WHITE;
    private static final int FRIEND_DUR_THRESHOLD = 10;
    private static final int FRIEND_NUM_MEET_THRESHOLD = 2;
    private static final int CLASSMATE_DUR_THRESHOLD = 30;
    private static final int CLASSMATE_NUM_MEET_THRESHOLD = 1;
    private static final int FS_DUR_THRESHOLD = 2;
    private static final int FS_NUM_MEET_THRESHOLD = 1;
    private static final int STRANGER_DUR_THRESHOLD = 2;
    private static final int STRANGER_NUM_MEET_THRESHOLD = 2;
    private static final int STUDY_DUR_THRESHOLD = 20;
    private static final int STUDY_NUM_MEET_THRESHOLD = 1;
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
    private Vector<edge> friendEdges = new Vector<edge>();
    private Vector<edge> fsEdges = new Vector<edge>();
    private Vector<edge> classmateEdges = new Vector<edge>();
    private Vector<edge> strangerEdges = new Vector<edge>();
    private Vector<edge> studyGEdges = new Vector<edge>();
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
    private boolean frShowParamFlag;
    private boolean fsShowParamFlag;
    private boolean cShowParamFlag;
    private boolean sShowParamFlag;
    private boolean sgShowParamFlag;
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
    private boolean fLabelled;
    private boolean fsLabelled;
    private boolean cLabelled;
    private boolean sLabelled;
    private boolean sgLabelled;
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

    // --- Community detection fields ---
    private JPanel communityPanel;
    private JButton communityDetectButton;
    private JButton communityClearButton;
    private JLabel communityCountLabel;
    private JLabel communityModularityLabel;
    private JLabel communityDetailsLabel;
    private boolean communityOverlayActive;
    private Map<String, Integer> nodeCommunityMap;
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
     * Create the layout for the graph
     */
    public void createLayout() {
        graphLayout = new StaticLayout<String, edge>(g);
        Vector<Vector<String>> clusters = new Vector<Vector<String>>();

        for (int i = 0; i < 9; i++) {
            clusters.add(new Vector<String>());
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
     * @param vertices vector of vertices to cluster
     * @param y y-position of cluster
     * @param x x-position of cluster
     */
    public void positionCluster(Vector<String> vertices, int y, int x) {
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
     * @param vertices vector of vertices to cluster
     * @param y y-position of cluster
     * @param x x-position of cluster
     */
    public void back_positionCluster(Vector<String> vertices, int y, int x) {
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

        fLabelled = false;
        fsLabelled = false;
        cLabelled = false;
        sLabelled = false;
        sgLabelled = false;
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
                        g.addVertex(nodeParam[0]);
                        //graphLayout.setLocation(nodeParam[0], new Point(Integer.parseInt(nodeParam[1]), Integer.parseInt(nodeParam[2])));
                    } else {



                        String[] edgeParam = line.split(" ");
                        edge curEdge = new edge(edgeParam[0], edgeParam[1], edgeParam[2]);
                        curEdge.setWeight(Float.parseFloat(edgeParam[3]));

                        if (edgeParam[0].equals("f")) {
                            if (!fLabelled) {
                                curEdge.setLabel("friend");
                                fLabelled = true;
                            }
                            friendEdges.add(curEdge);
                        } else if (edgeParam[0].equals("fs")) {
                            if (!fsLabelled) {
                                curEdge.setLabel("familair Stranger");
                                fsLabelled = true;
                            }
                            fsEdges.add(curEdge);
                        } else if (edgeParam[0].equals("c")) {
                            if (!cLabelled) {
                                curEdge.setLabel("Classmate");
                                cLabelled = true;
                            }
                            classmateEdges.add(curEdge);
                        } else if (edgeParam[0].equals("s")) {
                            if (!sLabelled) {
                                curEdge.setLabel("Stranger");
                                sLabelled = true;
                            }
                            strangerEdges.add(curEdge);
                        } else if (edgeParam[0].equals("sg")) {
                            if (!sgLabelled) {
                                curEdge.setLabel("Study Groups");
                                sgLabelled = true;
                            }
                            studyGEdges.add(curEdge);
                        }

                        if ((edgeParam[0].equals("f") && !showFriend.isSelected()) || (edgeParam[0].equals("fs") && !showFS.isSelected()) || (edgeParam[0].equals("c") && !showClassmate.isSelected()) || (edgeParam[0].equals("s") && !showStranger.isSelected()) || (edgeParam[0].equals("sg") && !showStudy.isSelected())) {
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
                if (edge.getType().equalsIgnoreCase("f")) {
                    return FRIEND_COLOR;
                } else if (edge.getType().equalsIgnoreCase("fs")) {
                    return FS_COLOR;
                } else if (edge.getType().equalsIgnoreCase("c")) {
                    return CLASSMATES_COLOR;
                } else if (edge.getType().equalsIgnoreCase("sg")) {
                    return STUDYG_COLOR;
                } else {
                    return STRANGER_COLOR;
                }
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
                int f = 0, c = 0, fs = 0, s = 0, sg = 0;
                for (edge x : g.getOutEdges(vertex)) {
                    if (x.getType().equals("f")) {
                        f = 1;
                    } else if (x.getType().equals("fs")) {
                        fs = 1;
                    } else if (x.getType().equals("c")) {
                        c = 1;
                    } else if (x.getType().equals("s")) {
                        s = 1;
                    } else if (x.getType().equals("sg")) {
                        sg = 1;
                    }
                }
                if (f + c + fs + s + sg > 1) {
                    return Vertex_COLOR;
                } else if (f == 1) {
                    return FRIEND_COLOR;
                } else if (fs == 1) {
                    return FS_COLOR;
                } else if (s == 1) {
                    return STRANGER_COLOR;
                } else if (c == 1) {
                    return CLASSMATES_COLOR;
                } else {
                    return STUDYG_COLOR;
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
        if ("f".equals(typeCode)) return "Friend";
        if ("fs".equals(typeCode)) return "Fam. Stranger";
        if ("c".equals(typeCode)) return "Classmate";
        if ("s".equals(typeCode)) return "Stranger";
        if ("sg".equals(typeCode)) return "Study Group";
        return typeCode;
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
        statsFriendCount.setForeground(FRIEND_COLOR);
        statsClassmateCount = createStatsLabel("  Classmates: 0", labelFont);
        statsClassmateCount.setForeground(CLASSMATES_COLOR);
        statsFsCount = createStatsLabel("  Fam. Strangers: 0", labelFont);
        statsFsCount.setForeground(FS_COLOR);
        statsStrangerCount = createStatsLabel("  Strangers: 0", labelFont);
        statsStrangerCount.setForeground(STRANGER_COLOR);
        statsStudyGCount = createStatsLabel("  Study Groups: 0", labelFont);
        statsStudyGCount.setForeground(STUDYG_COLOR);
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
    public final void showRightPane() {


        JLabel parameterHeading = new JLabel("Communities", JLabel.CENTER);
        parameterHeading.setPreferredSize(new Dimension(300, 30));
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                parameterHeading, parameterSpace);

        JSplitPane splitPane1 = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                splitPane, notesPanel);

        // Add path panel between notes and stats
        JSplitPane splitPanePath = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                splitPane1, pathPanel);

        // Add community panel below path panel
        JSplitPane splitPaneCommunity = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                splitPanePath, communityPanel);

        // Add stats panel below community panel
        JSplitPane splitPane2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                splitPaneCommunity, statsPanel);

        splitPane1.setDividerLocation(400);
        splitPanePath.setDividerLocation(510);
        splitPaneCommunity.setDividerLocation(640);
        splitPane2.setDividerLocation(820);
        add(splitPane2, BorderLayout.EAST);

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
     * repaint the category panel showing neccesary settings section
     */
    private void paintCategoryPanel() {

        categoryPanel = new Box[5];
        for (int i = 0; i < 5; i++) {
            categoryPanel[i] = Box.createVerticalBox();
            categoryPanel[i].setBorder(BorderFactory.createEtchedBorder(1));
            
        }

        categoryPanel[0].add(frHpanel);
        if (frShowParamFlag) {
            categoryPanel[0].add(friendDurThreshold);
            categoryPanel[0].add(friendNumMeetThreshold);
        }


        categoryPanel[1].add(cHpanel);
        if (cShowParamFlag) {
            categoryPanel[1].add(classmateDurThreshold);
            categoryPanel[1].add(classmateNumMeetThreshold);
        }


        categoryPanel[2].add(fsHpanel);
        if (fsShowParamFlag) {
            categoryPanel[2].add(fsDurThreshold);
            categoryPanel[2].add(fsNumMeetThreshold);
        }

        categoryPanel[3].add(sHpanel);
        if (sShowParamFlag) {
            categoryPanel[3].add(strangerDurThreshold);
            categoryPanel[3].add(strangerNumMeetThreshold);
        }

        categoryPanel[4].add(sgHpanel);
        if (sgShowParamFlag) {
            categoryPanel[4].add(studyGDurThreshold);
            categoryPanel[4].add(studyGNumMeetThreshold);
        }

        parameterSpace.removeAll();

        for (int i = 0; i < 5; i++) {
            parameterSpace.add(categoryPanel[i]);
        }
        parameterSpace.setVisible(false);
        parameterSpace.setVisible(true);
    }

    /**
     * initialize the category panel
     */
    public final void initializeCategoryPanel() {

        ////////////////////////////////////////////
        frShowParam = new JButton(new ImageIcon("./images/settings.png"));
        fsShowParam = new JButton(new ImageIcon("./images/settings.png"));
        cShowParam = new JButton(new ImageIcon("./images/settings.png"));
        sShowParam = new JButton(new ImageIcon("./images/settings.png"));
        sgShowParam = new JButton(new ImageIcon("./images/settings.png"));

        frShowParam.setBorder(null);
        fsShowParam.setBorder(null);
        cShowParam.setBorder(null);
        sShowParam.setBorder(null);
        sgShowParam.setBorder(null);

        sgShowParamFlag = false;
        frShowParamFlag = false;
        fsShowParamFlag = false;
        cShowParamFlag = false;
        sShowParamFlag = false;


        showFriend = new JCheckBox();
        showFriend.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                if (showFriend.isSelected()) {
                    for (edge x : friendEdges) {
                        System.out.println("adding edge");
                        g.addEdge(x, x.getVertex1(), x.getVertex2());
                    }
                } else {
                    for (edge x : friendEdges) {
                        System.out.println("removing edge" + x);
                        g.removeEdge(x);
                    }
                }
                imagePanel.setVisible(false);
                imagePanel.setVisible(true);
            }
        });
        showFriend.setSelected(true);

        frHpanel = new JPanel();

        JLabel friendLabel = new JLabel("FRIENDS (Location : public)", JLabel.CENTER);



        friendLabel.setForeground(FRIEND_COLOR);
        friendLabel.setFont(new Font("SANS_SERIF", 0, 14));

        friendDurThreshold = new JSlider(0, 50, FRIEND_DUR_THRESHOLD);
        friendNumMeetThreshold = new JSlider(0, 5, FRIEND_NUM_MEET_THRESHOLD);

        friendDurThreshold.setBorder(BorderFactory.createTitledBorder("Duration of meeting (min)"));
        friendNumMeetThreshold.setBorder(BorderFactory.createTitledBorder("Number of meetings in a day"));

        friendDurThreshold.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {

                if (!friendDurThreshold.getValueIsAdjusting()) {
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

        friendNumMeetThreshold.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                if (!friendNumMeetThreshold.getValueIsAdjusting()) {
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

        friendDurThreshold.setMajorTickSpacing(10);
        friendDurThreshold.setMinorTickSpacing(1);
        friendNumMeetThreshold.setMajorTickSpacing(1);
        friendNumMeetThreshold.setMinorTickSpacing(1);
        friendDurThreshold.setPaintLabels(true);
        friendNumMeetThreshold.setPaintLabels(true);
        friendDurThreshold.setPaintTicks(true);
        friendNumMeetThreshold.setPaintTicks(true);


        frHpanel.add(showFriend);
        frHpanel.add(friendLabel);
        frHpanel.add(frShowParam);
        frShowParam.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                frShowParamFlag = !frShowParamFlag;
                paintCategoryPanel();
            }
        });

        //////////////////////////////////////////

        showClassmate = new JCheckBox();
        showClassmate.setSelected(true);

        showClassmate.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                if (showClassmate.isSelected()) {
                    for (edge x : classmateEdges) {
                        g.addEdge(x, x.getVertex1(), x.getVertex2());
                    }
                } else {
                    for (edge x : classmateEdges) {
                        g.removeEdge(x);
                    }
                }
                imagePanel.setVisible(false);
                imagePanel.setVisible(true);
            }
        });
        cHpanel = new JPanel();
        JLabel classmateLabel = new JLabel("<html>CLASSMATES (Location : classroom)", JLabel.CENTER);
        classmateLabel.setForeground(CLASSMATES_COLOR);
        classmateLabel.setFont(new Font("SANS_SERIF", 0, 14));

        classmateDurThreshold = new JSlider(0, 50, CLASSMATE_DUR_THRESHOLD);
        classmateNumMeetThreshold = new JSlider(0, 5, CLASSMATE_NUM_MEET_THRESHOLD);
        classmateDurThreshold.setBorder(BorderFactory.createTitledBorder("Duration of meeting (min)"));
        classmateNumMeetThreshold.setBorder(BorderFactory.createTitledBorder("Number of meetings in a day"));
        classmateDurThreshold.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                if (!classmateDurThreshold.getValueIsAdjusting()) {
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

        classmateNumMeetThreshold.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                if (!classmateNumMeetThreshold.getValueIsAdjusting()) {
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

        classmateDurThreshold.setMajorTickSpacing(10);
        classmateNumMeetThreshold.setMajorTickSpacing(1);
        classmateNumMeetThreshold.setMinorTickSpacing(1);
        classmateDurThreshold.setMinorTickSpacing(1);
        classmateDurThreshold.setPaintLabels(true);
        classmateNumMeetThreshold.setPaintLabels(true);
        classmateDurThreshold.setPaintTicks(true);
        classmateNumMeetThreshold.setPaintTicks(true);

        cHpanel.add(showClassmate);
        cHpanel.add(classmateLabel);
        cHpanel.add(cShowParam);

        cShowParam.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                cShowParamFlag = !cShowParamFlag;
                paintCategoryPanel();
            }
        });

        /////////////////////////////////////////////////

        showFS = new JCheckBox();
        showFS.setSelected(true);

        showFS.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                if (showFS.isSelected()) {
                    for (edge x : fsEdges) {
                        g.addEdge(x, x.getVertex1(), x.getVertex2());
                    }
                } else {
                    for (edge x : fsEdges) {
                        g.removeEdge(x);
                    }
                }
                imagePanel.setVisible(false);
                imagePanel.setVisible(true);
            }
        });

        fsHpanel = new JPanel();
        JLabel fsLabel = new JLabel("FAM STRANGERS (Location : public,pathways)", JLabel.CENTER);
        fsLabel.setForeground(FS_COLOR);
        fsLabel.setFont(new Font("SANS_SERIF", 0, 14));

        fsDurThreshold = new JSlider(0, 25, FS_DUR_THRESHOLD);
        fsNumMeetThreshold = new JSlider(0, 5, FS_NUM_MEET_THRESHOLD);
        fsDurThreshold.setBorder(BorderFactory.createTitledBorder("Duration of meeting (min)"));
        fsNumMeetThreshold.setBorder(BorderFactory.createTitledBorder("Number of meetings in a day"));

        fsDurThreshold.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                if (!fsDurThreshold.getValueIsAdjusting()) {
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

        fsNumMeetThreshold.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                if (!fsNumMeetThreshold.getValueIsAdjusting()) {
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

        fsDurThreshold.setMajorTickSpacing(5);
        fsDurThreshold.setMinorTickSpacing(1);
        fsNumMeetThreshold.setMajorTickSpacing(1);
        fsNumMeetThreshold.setMinorTickSpacing(1);
        fsDurThreshold.setPaintTicks(true);
        fsNumMeetThreshold.setPaintTicks(true);
        fsDurThreshold.setPaintLabels(true);
        fsNumMeetThreshold.setPaintLabels(true);

        fsHpanel.add(showFS);
        fsHpanel.add(fsLabel);
        fsHpanel.add(fsShowParam);

        fsShowParam.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                fsShowParamFlag = !fsShowParamFlag;
                paintCategoryPanel();
            }
        });


        ////////////////////////////////////////////////////////

        showStranger = new JCheckBox();
        showStranger.setSelected(true);

        showStranger.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                if (showStranger.isSelected()) {
                    for (edge x : strangerEdges) {
                        g.addEdge(x, x.getVertex1(), x.getVertex2());
                    }
                } else {
                    for (edge x : strangerEdges) {
                        g.removeEdge(x);
                    }
                }
                imagePanel.setVisible(false);
                imagePanel.setVisible(true);
            }
        });

        sHpanel = new JPanel();
        JLabel strangerLabel = new JLabel("STRANGERS (Location : public,pathways)", JLabel.CENTER);
        strangerLabel.setForeground(STRANGER_COLOR);
        strangerLabel.setFont(new Font("SANS_SERIF", 0, 14));


        strangerDurThreshold = new JSlider(0, 25, STRANGER_DUR_THRESHOLD);
        strangerNumMeetThreshold = new JSlider(0, 5, STRANGER_NUM_MEET_THRESHOLD);
        strangerDurThreshold.setBorder(BorderFactory.createTitledBorder("Duration of meeting (min)"));
        strangerNumMeetThreshold.setBorder(BorderFactory.createTitledBorder("Number of meetings in a day"));
        strangerDurThreshold.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                if (!strangerDurThreshold.getValueIsAdjusting()) {
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

        strangerNumMeetThreshold.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                if (!strangerNumMeetThreshold.getValueIsAdjusting()) {
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

        strangerDurThreshold.setMajorTickSpacing(5);
        strangerDurThreshold.setMinorTickSpacing(1);
        strangerNumMeetThreshold.setMajorTickSpacing(1);
        strangerNumMeetThreshold.setMinorTickSpacing(1);

        strangerDurThreshold.setPaintTicks(true);
        strangerDurThreshold.setPaintLabels(true);
        strangerNumMeetThreshold.setPaintTicks(true);
        strangerNumMeetThreshold.setPaintLabels(true);

        sHpanel.add(showStranger);
        sHpanel.add(strangerLabel);
        sHpanel.add(sShowParam);

        sShowParam.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                sShowParamFlag = !sShowParamFlag;
                paintCategoryPanel();
            }
        });
        //////////////////////////////////////////////////

        showStudy = new JCheckBox();
        showStudy.setSelected(true);

        showStudy.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                if (showStudy.isSelected()) {
                    for (edge x : studyGEdges) {
                        g.addEdge(x, x.getVertex1(), x.getVertex2());
                    }
                } else {
                    for (edge x : studyGEdges) {
                        g.removeEdge(x);
                    }
                }
                imagePanel.setVisible(false);
                imagePanel.setVisible(true);
                ;
            }
        });

        sgHpanel = new JPanel();
        JLabel sgLabel = new JLabel("STUDY GROUPS (Location : public)", JLabel.CENTER);
        sgLabel.setForeground(STUDYG_COLOR);
        sgLabel.setFont(new Font("SANS_SERIF", 0, 14));

        studyGDurThreshold = new JSlider(0, 50, STUDY_DUR_THRESHOLD);
        studyGNumMeetThreshold = new JSlider(0, 5, STUDY_NUM_MEET_THRESHOLD);
        studyGDurThreshold.setBorder(BorderFactory.createTitledBorder("Duration of meeting (min)"));
        studyGNumMeetThreshold.setBorder(BorderFactory.createTitledBorder("Number of meetings in a day"));
        studyGDurThreshold.setSize(10, 100);
        studyGNumMeetThreshold.setSize(10, 100);

        studyGDurThreshold.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                if (!studyGDurThreshold.getValueIsAdjusting()) {
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

        studyGNumMeetThreshold.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                if (!studyGNumMeetThreshold.getValueIsAdjusting()) {
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

        studyGDurThreshold.setMajorTickSpacing(5);
        studyGDurThreshold.setMinorTickSpacing(1);
        studyGNumMeetThreshold.setMajorTickSpacing(1);
        studyGNumMeetThreshold.setMinorTickSpacing(1);

        studyGDurThreshold.setPaintTicks(true);
        studyGNumMeetThreshold.setPaintTicks(true);
        studyGDurThreshold.setPaintLabels(true);
        studyGNumMeetThreshold.setPaintLabels(true);

        sgHpanel.add(showStudy);
        sgHpanel.add(sgLabel);
        sgHpanel.add(sgShowParam);

        sgShowParam.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                sgShowParamFlag = !sgShowParamFlag;
                paintCategoryPanel();
            }
        });
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
                    copyfile(new File("./graph.txt"), fileChooser.getSelectedFile());
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

        toolPanel.add(legendPanel);
        contentPanel.add(toolPanel, BorderLayout.WEST);
    }

    /**
     * Copies the contents of one file to another
     * @param srFile source file from which contents are copied
     * @param dtFile destination file to which contents are copied 
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void copyfile(File srFile, File dtFile) throws FileNotFoundException, IOException {

        try (InputStream in = new FileInputStream(srFile);
             OutputStream out = new FileOutputStream(dtFile, false)) {

            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

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
