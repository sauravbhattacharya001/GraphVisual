package gvisual;

import java.awt.Color;

/**
 * Enumeration of edge relationship categories in the graph.
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

    FRIEND      ("f",  "friend",            Color.GREEN,             10, 2),
    CLASSMATE   ("c",  "Classmate",         Color.BLUE,              30, 1),
    FAMILIAR    ("fs", "Familiar Stranger",  Color.GRAY,              2, 1),
    STRANGER    ("s",  "Stranger",           Color.RED,               2, 2),
    STUDY_GROUP ("sg", "Study Groups",       Color.ORANGE,           20, 1);

    private final String code;
    private final String displayLabel;
    private final Color color;
    private final int defaultDurationThreshold;
    private final int defaultMeetingThreshold;

    EdgeType(String code, String displayLabel, Color color,
             int defaultDurationThreshold, int defaultMeetingThreshold) {
        this.code = code;
        this.displayLabel = displayLabel;
        this.color = color;
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

    /** Returns the default edge colour for rendering. */
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
     * @param code the edge type code ("f", "fs", "c", "s", "sg")
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
     * Returns the colour for a given edge type code.
     * Falls back to {@link Color#RED} (stranger) for unknown codes.
     *
     * @param typeCode the edge type code
     * @return the associated colour
     */
    public static Color colorForCode(String typeCode) {
        EdgeType t = fromCode(typeCode);
        return t != null ? t.color : Color.RED;
    }
}
