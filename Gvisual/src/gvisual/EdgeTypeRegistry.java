package gvisual;

import java.util.*;

/**
 * Centralized registry of edge type metadata — names, hex colors, and
 * RGB tuples.
 *
 * <p>Before this class existed, every exporter (DOT, GEXF, JSON, HTML,
 * Timeline, CSV) maintained its own copy of the type→name and type→color
 * mappings. Adding a new edge type required updating 5+ files. Now there
 * is a single source of truth.</p>
 *
 * <p>Consumers should call the static methods rather than hard-coding
 * edge type strings.</p>
 *
 * @author zalenix
 */
public final class EdgeTypeRegistry {

    private EdgeTypeRegistry() { /* utility class */ }

    // ── Canonical edge type codes ───────────────────────────────────
    public static final String FRIEND           = "f";
    public static final String FAMILIAR_STRANGER = "fs";
    public static final String CLASSMATE        = "c";
    public static final String STRANGER         = "s";
    public static final String STUDY_GROUP      = "sg";

    // ── Display names ───────────────────────────────────────────────
    private static final Map<String, String> NAMES = new LinkedHashMap<>();
    static {
        NAMES.put(FRIEND,            "Friend");
        NAMES.put(FAMILIAR_STRANGER, "Friend-Stranger");
        NAMES.put(CLASSMATE,         "Classmate");
        NAMES.put(STRANGER,          "Stranger");
        NAMES.put(STUDY_GROUP,       "Study Group");
    }

    // ── Hex colors (for DOT, HTML, SVG, etc.) ───────────────────────
    private static final Map<String, String> HEX_COLORS = new LinkedHashMap<>();
    static {
        HEX_COLORS.put(FRIEND,            "#4CAF50"); // green
        HEX_COLORS.put(FAMILIAR_STRANGER, "#2196F3"); // blue
        HEX_COLORS.put(CLASSMATE,         "#FF9800"); // orange
        HEX_COLORS.put(STRANGER,          "#F44336"); // red
        HEX_COLORS.put(STUDY_GROUP,       "#9C27B0"); // purple
    }

    // ── RGB colors (for GEXF and formats needing integer components) ─
    private static final Map<String, int[]> RGB_COLORS = new LinkedHashMap<>();
    static {
        RGB_COLORS.put(FRIEND,            new int[]{0, 200, 0});
        RGB_COLORS.put(FAMILIAR_STRANGER, new int[]{200, 200, 0});
        RGB_COLORS.put(CLASSMATE,         new int[]{0, 150, 255});
        RGB_COLORS.put(STRANGER,          new int[]{180, 180, 180});
        RGB_COLORS.put(STUDY_GROUP,       new int[]{255, 80, 80});
    }

    /**
     * Returns the human-readable name for an edge type code.
     *
     * @param typeCode the edge type code (e.g. "f", "sg")
     * @return display name, or the raw code if unknown
     */
    public static String getName(String typeCode) {
        if (typeCode == null) return "Unknown";
        return NAMES.getOrDefault(typeCode, typeCode);
    }

    /**
     * Returns the hex color for an edge type code.
     *
     * @param typeCode the edge type code
     * @return hex color string (e.g. "#4CAF50"), or "#CCCCCC" if unknown
     */
    public static String getHexColor(String typeCode) {
        return HEX_COLORS.getOrDefault(typeCode, "#CCCCCC");
    }

    /**
     * Returns the RGB color components for an edge type code.
     *
     * @param typeCode the edge type code
     * @return int array [r, g, b], or {128,128,128} if unknown
     */
    public static int[] getRgbColor(String typeCode) {
        int[] rgb = RGB_COLORS.get(typeCode);
        return (rgb != null) ? rgb : new int[]{128, 128, 128};
    }

    /**
     * Returns an unmodifiable copy of all known hex colors.
     * Useful for exporters that need the full palette.
     *
     * @return map of type code → hex color
     */
    public static Map<String, String> getAllHexColors() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(HEX_COLORS));
    }

    /**
     * Returns an unmodifiable copy of all known RGB colors.
     *
     * @return map of type code → int[3]
     */
    public static Map<String, int[]> getAllRgbColors() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(RGB_COLORS));
    }

    /**
     * Returns all known type codes in canonical order.
     *
     * @return list of type codes
     */
    public static List<String> getAllTypeCodes() {
        return Collections.unmodifiableList(new ArrayList<>(NAMES.keySet()));
    }
}
