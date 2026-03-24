package gvisual;

import java.awt.Color;

/**
 * Enumeration of Edge relationship categories in the graph.
 *
 * <p>Centralises the code string, display label, colour, and default
 * threshold values that were previously scattered across Main.java as
 * parallel if/else chains and separate constant fields.</p>
 *
 * <p>Usage:</p>
 * <pre>
 *   EdgeType type = EdgeType.fromCode("f");   // FRIEND
 *   Color c = type.getColor();                // Color.GREEN
 *   String label = type.getDisplayLabel();    // "friend"
 * </pre>
 */
public enum EdgeType {

    FRIEND      ("f",  "friend",            Color.GREEN,  "images/green.jpg",  10, 2),
    CLASSMATE   ("c",  "Classmate",         Color.BLUE,   "images/blue.jpg",   30, 1),
    FAMILIAR    ("fs", "Familiar Stranger",  Color.GRAY,  "images/gray.jpg",    2, 1),
    STRANGER    ("s",  "Stranger",           Color.RED,   "images/red.jpg",     2, 2),
    STUDY_GROUP ("sg", "Study Groups",       Color.ORANGE,"images/yellow.jpg", 20, 1);

    private final String code;
    private final String displayLabel;
    private final Color color;
    private final String legendIconPath;
    private final int defaultDurationThreshold;
    private final int defaultMeetingThreshold;

    /**
     * Bit-flag for cluster layout assignment.  FRIEND=1, FAMILIAR=2,
     * CLASSMATE=4, STRANGER=8.  STUDY_GROUP is excluded from clustering.
     */
    private static final int FLAG_FRIEND    = 1;
    private static final int FLAG_FAMILIAR  = 2;
    private static final int FLAG_CLASSMATE = 4;
    private static final int FLAG_STRANGER  = 8;

    /**
     * Maps a combination of edge-type presence flags to a cluster area ID
     * (0..8) used by {@code Main.createLayout()}.
     *
     * <p>The flags are: FRIEND=1, FAMILIAR=2, CLASSMATE=4, STRANGER=8.
     * Only the 9 original cluster assignments are mapped; all other
     * combinations fall into the catch-all cluster 4.</p>
     */
    private static final java.util.Map<Integer, Integer> CLUSTER_MAP;
    static {
        CLUSTER_MAP = new java.util.HashMap<>();
        CLUSTER_MAP.put(FLAG_FRIEND,                               0); // F only
        CLUSTER_MAP.put(FLAG_FRIEND | FLAG_CLASSMATE,              1); // F + C
        CLUSTER_MAP.put(FLAG_CLASSMATE,                            2); // C only
        CLUSTER_MAP.put(FLAG_FRIEND | FLAG_FAMILIAR,               3); // F + FS
        // 4 = catch-all (handled by getOrDefault below)
        CLUSTER_MAP.put(FLAG_CLASSMATE | FLAG_STRANGER,            5); // C + S
        CLUSTER_MAP.put(FLAG_FAMILIAR,                             6); // FS only
        CLUSTER_MAP.put(FLAG_FAMILIAR | FLAG_STRANGER,             7); // FS + S
        CLUSTER_MAP.put(FLAG_STRANGER,                             8); // S only
    }

    /**
     * Returns the cluster area ID (0..8) for a set of edge-type presence
     * flags.  Replaces the 30-line if/else chain in {@code Main.createLayout()}.
     *
     * @param isF  vertex has friend edges
     * @param isFs vertex has familiar-stranger edges
     * @param isC  vertex has classmate edges
     * @param isS  vertex has stranger edges
     * @return cluster ID for layout positioning
     */
    public static int clusterIdFor(boolean isF, boolean isFs, boolean isC, boolean isS) {
        int flags = (isF  ? FLAG_FRIEND    : 0)
                  | (isFs ? FLAG_FAMILIAR  : 0)
                  | (isC  ? FLAG_CLASSMATE : 0)
                  | (isS  ? FLAG_STRANGER  : 0);
        return CLUSTER_MAP.getOrDefault(flags, 4);
    }

    EdgeType(String code, String displayLabel, Color color, String legendIconPath,
             int defaultDurationThreshold, int defaultMeetingThreshold) {
        this.code = code;
        this.displayLabel = displayLabel;
        this.color = color;
        this.legendIconPath = legendIconPath;
        this.defaultDurationThreshold = defaultDurationThreshold;
        this.defaultMeetingThreshold = defaultMeetingThreshold;
    }

    /** Returns the short code used in data files (e.g. "f", "fs", "c"). */
    public String getCode() {
        return code;
    }

    /** Returns the human-readable label for graph legends. */
    public String getDisplayLabel() {
        return displayLabel;
    }

    /** Returns the legend icon image path (e.g. "images/green.jpg"). */
    public String getLegendIconPath() {
        return legendIconPath;
    }

    /** Returns the default Edge colour for rendering. */
    public Color getColor() {
        return color;
    }

    /** Returns the default duration-of-meeting threshold (minutes). */
    public int getDefaultDurationThreshold() {
        return defaultDurationThreshold;
    }

    /** Returns the default number-of-meetings threshold. */
    public int getDefaultMeetingThreshold() {
        return defaultMeetingThreshold;
    }

    /**
     * Looks up an {@code EdgeType} by its file code string.
     *
     * @param code the Edge type code ("f", "fs", "c", "s", "sg")
     * @return the matching {@code EdgeType}, or {@code null} if unknown
     */
    public static EdgeType fromCode(String code) {
        if (code == null) {
            return null;
        }
        String lower = code.toLowerCase();
        for (EdgeType t : values()) {
            if (t.code.equals(lower)) {
                return t;
            }
        }
        return null;
    }

    /**
     * Returns the colour for a given Edge type code.
     * Falls back to {@link Color#RED} (stranger) for unknown codes.
     *
     * @param typeCode the Edge type code
     * @return the associated colour
     */
    public static Color colorForCode(String typeCode) {
        EdgeType t = fromCode(typeCode);
        return t != null ? t.color : Color.RED;
    }
}
