package gvisual;

import java.awt.Color;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for {@link EdgeType}.
 *
 * <p>Verifies code mapping, display labels, colour assignment, the
 * cluster-id flag combinator that replaced the legacy if/else chain
 * in {@code Main.createLayout()}, and that every enum value exposes
 * complete, non-null metadata.</p>
 */
public class EdgeTypeTest {

    @Test
    public void testFromCodeMapsKnownCodes() {
        assertSame(EdgeType.FRIEND,     EdgeType.fromCode("f"));
        assertSame(EdgeType.CLASSMATE,  EdgeType.fromCode("c"));
        assertSame(EdgeType.FAMILIAR,   EdgeType.fromCode("fs"));
        assertSame(EdgeType.STRANGER,   EdgeType.fromCode("s"));
        assertSame(EdgeType.STUDY_GROUP, EdgeType.fromCode("sg"));
    }

    @Test
    public void testFromCodeIsCaseInsensitive() {
        assertSame(EdgeType.FRIEND,    EdgeType.fromCode("F"));
        assertSame(EdgeType.FAMILIAR,  EdgeType.fromCode("FS"));
        assertSame(EdgeType.STUDY_GROUP, EdgeType.fromCode("SG"));
    }

    @Test
    public void testFromCodeUnknownReturnsNull() {
        assertNull(EdgeType.fromCode("xyz"));
        assertNull(EdgeType.fromCode(""));
        assertNull(EdgeType.fromCode(null));
    }

    @Test
    public void testColorForCodeKnown() {
        assertEquals(Color.GREEN,  EdgeType.colorForCode("f"));
        assertEquals(Color.BLUE,   EdgeType.colorForCode("c"));
        assertEquals(Color.GRAY,   EdgeType.colorForCode("fs"));
        assertEquals(Color.RED,    EdgeType.colorForCode("s"));
        assertEquals(Color.ORANGE, EdgeType.colorForCode("sg"));
    }

    @Test
    public void testColorForCodeUnknownFallsBackToRed() {
        // Documented behaviour: unknown codes return Color.RED (stranger).
        assertEquals(Color.RED, EdgeType.colorForCode("not-a-code"));
        assertEquals(Color.RED, EdgeType.colorForCode(null));
    }

    @Test
    public void testEveryValueHasCompleteMetadata() {
        for (EdgeType t : EdgeType.values()) {
            assertNotNull(t + " missing code",          t.getCode());
            assertNotNull(t + " missing displayLabel",  t.getDisplayLabel());
            assertNotNull(t + " missing color",         t.getColor());
            assertNotNull(t + " missing legend icon",   t.getLegendIconPath());
            assertTrue(t + " thresholds should be non-negative",
                    t.getDefaultDurationThreshold() >= 0
                    && t.getDefaultMeetingThreshold() >= 0);
            assertFalse(t + " code should be non-empty", t.getCode().isEmpty());
        }
    }

    @Test
    public void testCodesAreUnique() {
        java.util.Set<String> codes = new java.util.HashSet<>();
        for (EdgeType t : EdgeType.values()) {
            assertTrue("Duplicate code: " + t.getCode(), codes.add(t.getCode()));
        }
    }

    // --- Cluster mapping tests (replaces legacy if/else chain in Main) ---

    @Test
    public void testClusterIdFriendOnly() {
        assertEquals(0, EdgeType.clusterIdFor(true, false, false, false));
    }

    @Test
    public void testClusterIdFriendAndClassmate() {
        assertEquals(1, EdgeType.clusterIdFor(true, false, true, false));
    }

    @Test
    public void testClusterIdClassmateOnly() {
        assertEquals(2, EdgeType.clusterIdFor(false, false, true, false));
    }

    @Test
    public void testClusterIdFriendAndFamiliar() {
        assertEquals(3, EdgeType.clusterIdFor(true, true, false, false));
    }

    @Test
    public void testClusterIdClassmateAndStranger() {
        assertEquals(5, EdgeType.clusterIdFor(false, false, true, true));
    }

    @Test
    public void testClusterIdFamiliarOnly() {
        assertEquals(6, EdgeType.clusterIdFor(false, true, false, false));
    }

    @Test
    public void testClusterIdFamiliarAndStranger() {
        assertEquals(7, EdgeType.clusterIdFor(false, true, false, true));
    }

    @Test
    public void testClusterIdStrangerOnly() {
        assertEquals(8, EdgeType.clusterIdFor(false, false, false, true));
    }

    @Test
    public void testClusterIdCatchAllForUnmappedCombination() {
        // No flags at all - not in the map - falls into catch-all (4).
        assertEquals(4, EdgeType.clusterIdFor(false, false, false, false));
        // All four flags - also unmapped - catch-all (4).
        assertEquals(4, EdgeType.clusterIdFor(true, true, true, true));
    }
}
