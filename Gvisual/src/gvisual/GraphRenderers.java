package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections15.Transformer;

/**
 * Centralised rendering transformers for the graph visualisation.
 * <p>
 * Overlay state (path highlighting, MST, community colouring, articulation
 * points) is set by {@link Main} when overlays are toggled.  Each transformer
 * reads this shared state so that rendering priority logic lives in one place
 * and can be tested independently of the UI.
 */
public class GraphRenderers {

    // ── overlay state (set externally by Main) ──────────────────────────

    private Set<edge> pathEdges;
    private Set<String> pathVertices;
    private String pathSource;
    private String pathTarget;

    private boolean mstOverlayActive;
    private Set<edge> mstEdges;

    private boolean communityOverlayActive;
    private Map<String, Integer> nodeCommunityMap;

    private boolean articulationOverlayActive;
    private Set<String> articulationPoints;
    private Set<edge> bridgeEdges;

    private Collection<String> oldVertices;

    /** Graph reference for vertex‐paint edge‐type lookup. */
    private Graph<String, edge> graph;

    // ── constants (mirrored from Main) ──────────────────────────────────

    private static final Color VERTEX_COLOR = Color.WHITE;

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

    // ── setters ─────────────────────────────────────────────────────────

    public void setPathState(Set<edge> pathEdges, Set<String> pathVertices,
                             String pathSource, String pathTarget) {
        this.pathEdges = pathEdges;
        this.pathVertices = pathVertices;
        this.pathSource = pathSource;
        this.pathTarget = pathTarget;
    }

    public void setMstState(boolean active, Set<edge> mstEdges) {
        this.mstOverlayActive = active;
        this.mstEdges = mstEdges;
    }

    public void setCommunityState(boolean active, Map<String, Integer> nodeCommunityMap) {
        this.communityOverlayActive = active;
        this.nodeCommunityMap = nodeCommunityMap;
    }

    public void setArticulationState(boolean active, Set<String> articulationPoints,
                                     Set<edge> bridgeEdges) {
        this.articulationOverlayActive = active;
        this.articulationPoints = articulationPoints;
        this.bridgeEdges = bridgeEdges;
    }

    public void setOldVertices(Collection<String> oldVertices) {
        this.oldVertices = oldVertices;
    }

    public void setGraph(Graph<String, edge> graph) {
        this.graph = graph;
    }

    // ── transformers ────────────────────────────────────────────────────

    public Transformer<edge, Paint> edgePaintTransformer() {
        return new Transformer<edge, Paint>() {
            @Override
            public Paint transform(edge e) {
                if (pathEdges != null && pathEdges.contains(e)) {
                    return Color.YELLOW;
                }
                if (mstOverlayActive && mstEdges != null && mstEdges.contains(e)) {
                    return new Color(0, 255, 100);
                }
                if (articulationOverlayActive && bridgeEdges != null && bridgeEdges.contains(e)) {
                    return new Color(255, 80, 40);
                }
                if (communityOverlayActive && nodeCommunityMap != null) {
                    Integer c1 = nodeCommunityMap.get(e.getVertex1());
                    Integer c2 = nodeCommunityMap.get(e.getVertex2());
                    if (c1 != null && c2 != null && c1.equals(c2)) {
                        Color base = COMMUNITY_COLORS[c1 % COMMUNITY_COLORS.length];
                        return new Color(base.getRed(), base.getGreen(), base.getBlue(), 180);
                    }
                    return new Color(100, 100, 100, 80);
                }
                if (mstOverlayActive && mstEdges != null && !mstEdges.contains(e)) {
                    return new Color(80, 80, 80, 60);
                }
                return EdgeType.colorForCode(e.getType());
            }
        };
    }

    public Transformer<String, Paint> vertexPaintTransformer() {
        return new Transformer<String, Paint>() {
            @Override
            public Paint transform(String vertex) {
                if (communityOverlayActive && nodeCommunityMap != null) {
                    Integer cid = nodeCommunityMap.get(vertex);
                    if (cid != null) {
                        return COMMUNITY_COLORS[cid % COMMUNITY_COLORS.length];
                    }
                }
                if (articulationOverlayActive && articulationPoints != null
                        && articulationPoints.contains(vertex)) {
                    return new Color(255, 80, 40);
                }
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
                if (graph != null) {
                    Set<EdgeType> connectedTypes = new HashSet<>();
                    for (edge x : graph.getOutEdges(vertex)) {
                        EdgeType et = EdgeType.fromCode(x.getType());
                        if (et != null) {
                            connectedTypes.add(et);
                        }
                    }
                    if (connectedTypes.size() > 1) {
                        return VERTEX_COLOR;
                    } else if (connectedTypes.size() == 1) {
                        return connectedTypes.iterator().next().getColor();
                    }
                }
                return VERTEX_COLOR;
            }
        };
    }

    public Transformer<String, Shape> vertexShapeTransformer() {
        return new Transformer<String, Shape>() {
            @Override
            public Shape transform(String vertex) {
                if (pathSource != null && vertex.equals(pathSource)) {
                    return new Ellipse2D.Double(-8, -8, 16, 16);
                }
                if (pathTarget != null && vertex.equals(pathTarget)) {
                    return new Ellipse2D.Double(-8, -8, 16, 16);
                }
                if (pathVertices != null && pathVertices.contains(vertex)) {
                    return new Ellipse2D.Double(-6, -6, 12, 12);
                }
                if (articulationOverlayActive && articulationPoints != null
                        && articulationPoints.contains(vertex)) {
                    return new Ellipse2D.Double(-8, -8, 16, 16);
                }
                if (oldVertices != null) {
                    if (oldVertices.contains(vertex)) {
                        return new Ellipse2D.Double(-5, -5, 10, 10);
                    } else {
                        return new Ellipse2D.Double(-5, -5, 20, 20);
                    }
                }
                return new Ellipse2D.Double(-5, -5, 10, 10);
            }
        };
    }

    public Transformer<edge, Stroke> edgeStrokeTransformer() {
        return new Transformer<edge, Stroke>() {
            @Override
            public Stroke transform(edge i) {
                if (pathEdges != null && pathEdges.contains(i)) {
                    return new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
                }
                if (mstOverlayActive && mstEdges != null && mstEdges.contains(i)) {
                    return new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
                }
                if (articulationOverlayActive && bridgeEdges != null && bridgeEdges.contains(i)) {
                    float[] dashPattern = {6.0f, 4.0f};
                    return new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                            10.0f, dashPattern, 0.0f);
                }
                float[] dash = {1.0f};
                float width = i.getWeight() / 40 + 1.0f;
                return new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                        10.0f, dash, 0.0f);
            }
        };
    }
}
