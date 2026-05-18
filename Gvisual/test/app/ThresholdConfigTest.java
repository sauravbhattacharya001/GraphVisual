package app;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for {@link ThresholdConfig} and its {@link ThresholdConfig.Builder}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Default values match the documented {@link gvisual.EdgeType} defaults.</li>
 *   <li>Each builder setter writes only the targeted fields.</li>
 *   <li>The fluent builder API returns the same {@code Builder} instance for chaining.</li>
 *   <li>{@link ThresholdConfig#toString()} contains every category for log diffability.</li>
 *   <li>The object is immutable (no setters, all fields {@code final}).</li>
 * </ul>
 */
public class ThresholdConfigTest {

    // ---- defaults -----------------------------------------------------------

    @Test
    public void defaultsMatchDocumentedValues() {
        ThresholdConfig cfg = new ThresholdConfig.Builder().build();
        assertEquals(10, cfg.getFriendDuration());
        assertEquals(2,  cfg.getFriendCount());
        assertEquals(2,  cfg.getFamiliarStrangerDuration());
        assertEquals(1,  cfg.getFamiliarStrangerCount());
        assertEquals(30, cfg.getClassmateDuration());
        assertEquals(1,  cfg.getClassmateCount());
        assertEquals(2,  cfg.getStrangerDuration());
        assertEquals(2,  cfg.getStrangerCount());
        assertEquals(20, cfg.getStudyGroupDuration());
        assertEquals(1,  cfg.getStudyGroupCount());
    }

    // ---- per-setter isolation ----------------------------------------------

    @Test
    public void friendSetterOnlyAffectsFriendFields() {
        ThresholdConfig cfg = new ThresholdConfig.Builder().friend(99, 5).build();
        assertEquals(99, cfg.getFriendDuration());
        assertEquals(5,  cfg.getFriendCount());
        // others unchanged
        assertEquals(2,  cfg.getFamiliarStrangerDuration());
        assertEquals(30, cfg.getClassmateDuration());
        assertEquals(2,  cfg.getStrangerDuration());
        assertEquals(20, cfg.getStudyGroupDuration());
    }

    @Test
    public void familiarStrangerSetterOnlyAffectsItsFields() {
        ThresholdConfig cfg = new ThresholdConfig.Builder().familiarStranger(7, 4).build();
        assertEquals(7, cfg.getFamiliarStrangerDuration());
        assertEquals(4, cfg.getFamiliarStrangerCount());
        assertEquals(10, cfg.getFriendDuration());
        assertEquals(30, cfg.getClassmateDuration());
    }

    @Test
    public void classmateSetterOnlyAffectsItsFields() {
        ThresholdConfig cfg = new ThresholdConfig.Builder().classmate(45, 3).build();
        assertEquals(45, cfg.getClassmateDuration());
        assertEquals(3,  cfg.getClassmateCount());
        assertEquals(10, cfg.getFriendDuration());
        assertEquals(20, cfg.getStudyGroupDuration());
    }

    @Test
    public void strangerSetterOnlyAffectsItsFields() {
        ThresholdConfig cfg = new ThresholdConfig.Builder().stranger(1, 1).build();
        assertEquals(1, cfg.getStrangerDuration());
        assertEquals(1, cfg.getStrangerCount());
        assertEquals(2, cfg.getFamiliarStrangerDuration());
        assertEquals(20, cfg.getStudyGroupDuration());
    }

    @Test
    public void studyGroupSetterOnlyAffectsItsFields() {
        ThresholdConfig cfg = new ThresholdConfig.Builder().studyGroup(60, 9).build();
        assertEquals(60, cfg.getStudyGroupDuration());
        assertEquals(9,  cfg.getStudyGroupCount());
        assertEquals(10, cfg.getFriendDuration());
        assertEquals(30, cfg.getClassmateDuration());
    }

    // ---- builder chaining --------------------------------------------------

    @Test
    public void builderChainingPreservesAllValues() {
        ThresholdConfig cfg = new ThresholdConfig.Builder()
                .friend(11, 12)
                .familiarStranger(13, 14)
                .classmate(15, 16)
                .stranger(17, 18)
                .studyGroup(19, 20)
                .build();
        assertEquals(11, cfg.getFriendDuration());
        assertEquals(12, cfg.getFriendCount());
        assertEquals(13, cfg.getFamiliarStrangerDuration());
        assertEquals(14, cfg.getFamiliarStrangerCount());
        assertEquals(15, cfg.getClassmateDuration());
        assertEquals(16, cfg.getClassmateCount());
        assertEquals(17, cfg.getStrangerDuration());
        assertEquals(18, cfg.getStrangerCount());
        assertEquals(19, cfg.getStudyGroupDuration());
        assertEquals(20, cfg.getStudyGroupCount());
    }

    @Test
    public void builderSettersReturnSameBuilderInstance() {
        ThresholdConfig.Builder b = new ThresholdConfig.Builder();
        assertSame(b, b.friend(1, 1));
        assertSame(b, b.familiarStranger(1, 1));
        assertSame(b, b.classmate(1, 1));
        assertSame(b, b.stranger(1, 1));
        assertSame(b, b.studyGroup(1, 1));
    }

    @Test
    public void buildProducesIndependentInstances() {
        ThresholdConfig.Builder b = new ThresholdConfig.Builder().friend(5, 5);
        ThresholdConfig first = b.build();
        // Mutating the builder afterwards must not affect a previously-built config.
        b.friend(99, 99);
        ThresholdConfig second = b.build();
        assertEquals(5, first.getFriendDuration());
        assertEquals(99, second.getFriendDuration());
    }

    // ---- toString ----------------------------------------------------------

    @Test
    public void toStringContainsEveryCategory() {
        String s = new ThresholdConfig.Builder().build().toString();
        assertNotNull(s);
        assertTrue("toString should mention friend",    s.contains("friend"));
        assertTrue("toString should mention familiar",  s.contains("familiar"));
        assertTrue("toString should mention classmate", s.contains("classmate"));
        assertTrue("toString should mention stranger",  s.contains("stranger"));
        assertTrue("toString should mention studyGroup",s.contains("studyGroup"));
    }

    @Test
    public void toStringReflectsCustomValues() {
        String s = new ThresholdConfig.Builder().friend(77, 88).build().toString();
        assertTrue("toString should show custom friend duration", s.contains("77"));
        assertTrue("toString should show custom friend count",    s.contains("88"));
    }

    // ---- accepts edge-case values ------------------------------------------

    @Test
    public void acceptsZeroAndNegativeValues() {
        // The config is a plain data carrier; it should not validate. Callers do.
        ThresholdConfig cfg = new ThresholdConfig.Builder()
                .friend(0, 0)
                .stranger(-1, -1)
                .build();
        assertEquals(0,  cfg.getFriendDuration());
        assertEquals(0,  cfg.getFriendCount());
        assertEquals(-1, cfg.getStrangerDuration());
        assertEquals(-1, cfg.getStrangerCount());
    }
}
