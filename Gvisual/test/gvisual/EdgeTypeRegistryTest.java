package gvisual;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link EdgeTypeRegistry}.
 *
 * Covers name lookups, hex color lookups, RGB color lookups,
 * unknown type fallbacks, and bulk accessors.
 */
public class EdgeTypeRegistryTest {

    // ── getName ──────────────────────────────────────────────────────

    @Test
    public void testGetNameForAllKnownTypes() {
        assertEquals("Friend", EdgeTypeRegistry.getName(EdgeTypeRegistry.FRIEND));
        assertEquals("Friend-Stranger", EdgeTypeRegistry.getName(EdgeTypeRegistry.FAMILIAR_STRANGER));
        assertEquals("Classmate", EdgeTypeRegistry.getName(EdgeTypeRegistry.CLASSMATE));
        assertEquals("Stranger", EdgeTypeRegistry.getName(EdgeTypeRegistry.STRANGER));
        assertEquals("Study Group", EdgeTypeRegistry.getName(EdgeTypeRegistry.STUDY_GROUP));
    }

    @Test
    public void testGetNameUnknownTypeReturnsRawCode() {
        assertEquals("xyz", EdgeTypeRegistry.getName("xyz"));
    }

    @Test
    public void testGetNameNullReturnsUnknown() {
        assertEquals("Unknown", EdgeTypeRegistry.getName(null));
    }

    // ── getHexColor ─────────────────────────────────────────────────

    @Test
    public void testGetHexColorForKnownTypes() {
        assertEquals("#4CAF50", EdgeTypeRegistry.getHexColor(EdgeTypeRegistry.FRIEND));
        assertEquals("#2196F3", EdgeTypeRegistry.getHexColor(EdgeTypeRegistry.FAMILIAR_STRANGER));
        assertEquals("#FF9800", EdgeTypeRegistry.getHexColor(EdgeTypeRegistry.CLASSMATE));
        assertEquals("#F44336", EdgeTypeRegistry.getHexColor(EdgeTypeRegistry.STRANGER));
        assertEquals("#9C27B0", EdgeTypeRegistry.getHexColor(EdgeTypeRegistry.STUDY_GROUP));
    }

    @Test
    public void testGetHexColorUnknownTypeFallsBack() {
        assertEquals("#CCCCCC", EdgeTypeRegistry.getHexColor("unknown"));
    }

    @Test
    public void testGetHexColorNullFallsBack() {
        assertEquals("#CCCCCC", EdgeTypeRegistry.getHexColor(null));
    }

    // ── getRgbColor ─────────────────────────────────────────────────

    @Test
    public void testGetRgbColorForFriend() {
        int[] rgb = EdgeTypeRegistry.getRgbColor(EdgeTypeRegistry.FRIEND);
        assertArrayEquals(new int[]{0, 200, 0}, rgb);
    }

    @Test
    public void testGetRgbColorForStudyGroup() {
        int[] rgb = EdgeTypeRegistry.getRgbColor(EdgeTypeRegistry.STUDY_GROUP);
        assertArrayEquals(new int[]{255, 80, 80}, rgb);
    }

    @Test
    public void testGetRgbColorUnknownTypeFallsBack() {
        int[] rgb = EdgeTypeRegistry.getRgbColor("nope");
        assertArrayEquals(new int[]{128, 128, 128}, rgb);
    }

    @Test
    public void testGetRgbColorNullFallsBack() {
        int[] rgb = EdgeTypeRegistry.getRgbColor(null);
        assertArrayEquals(new int[]{128, 128, 128}, rgb);
    }

    // ── getAllHexColors ──────────────────────────────────────────────

    @Test
    public void testGetAllHexColorsContainsAllTypes() {
        Map<String, String> colors = EdgeTypeRegistry.getAllHexColors();
        assertEquals(5, colors.size());
        assertTrue(colors.containsKey(EdgeTypeRegistry.FRIEND));
        assertTrue(colors.containsKey(EdgeTypeRegistry.STUDY_GROUP));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetAllHexColorsIsUnmodifiable() {
        EdgeTypeRegistry.getAllHexColors().put("new", "#000000");
    }

    // ── getAllRgbColors ──────────────────────────────────────────────

    @Test
    public void testGetAllRgbColorsContainsAllTypes() {
        Map<String, int[]> colors = EdgeTypeRegistry.getAllRgbColors();
        assertEquals(5, colors.size());
        assertTrue(colors.containsKey(EdgeTypeRegistry.CLASSMATE));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetAllRgbColorsIsUnmodifiable() {
        EdgeTypeRegistry.getAllRgbColors().put("new", new int[]{0, 0, 0});
    }

    // ── getAllTypeCodes ──────────────────────────────────────────────

    @Test
    public void testGetAllTypeCodesContainsAllFive() {
        List<String> codes = EdgeTypeRegistry.getAllTypeCodes();
        assertEquals(5, codes.size());
        assertTrue(codes.contains(EdgeTypeRegistry.FRIEND));
        assertTrue(codes.contains(EdgeTypeRegistry.FAMILIAR_STRANGER));
        assertTrue(codes.contains(EdgeTypeRegistry.CLASSMATE));
        assertTrue(codes.contains(EdgeTypeRegistry.STRANGER));
        assertTrue(codes.contains(EdgeTypeRegistry.STUDY_GROUP));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetAllTypeCodesIsUnmodifiable() {
        EdgeTypeRegistry.getAllTypeCodes().add("new");
    }

    // ── Constant values ─────────────────────────────────────────────

    @Test
    public void testConstantValues() {
        assertEquals("f", EdgeTypeRegistry.FRIEND);
        assertEquals("fs", EdgeTypeRegistry.FAMILIAR_STRANGER);
        assertEquals("c", EdgeTypeRegistry.CLASSMATE);
        assertEquals("s", EdgeTypeRegistry.STRANGER);
        assertEquals("sg", EdgeTypeRegistry.STUDY_GROUP);
    }
}
