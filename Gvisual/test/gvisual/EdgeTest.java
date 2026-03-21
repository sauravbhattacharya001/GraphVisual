package gvisual;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the {@link edge} class.
 *
 * Covers constructors, getters, weight/label properties, and
 * ensures default (no-arg) construction leaves fields null.
 */
public class EdgeTest {

    // --- Constructor tests ---

    @Test
    public void testDefaultConstructorFieldsAreNull() {
        edge e = new Edge();
        assertNull("type should be null after no-arg construction", e.getType());
        assertNull("vertex1 should be null after no-arg construction", e.getVertex1());
        assertNull("vertex2 should be null after no-arg construction", e.getVertex2());
        assertNull("label should be null by default", e.getLabel());
        assertEquals("weight should default to 0.0", 0.0f, e.getWeight(), 0.001f);
    }

    @Test
    public void testParameterizedConstructor() {
        edge e = new Edge("f", "Alice", "Bob");
        assertEquals("f", e.getType());
        assertEquals("Alice", e.getVertex1());
        assertEquals("Bob", e.getVertex2());
    }

    // --- Edge type tests ---

    @Test
    public void testFriendEdgeType() {
        edge e = new Edge("f", "A", "B");
        assertEquals("f", e.getType());
    }

    @Test
    public void testFamiliarStrangerEdgeType() {
        edge e = new Edge("fs", "A", "B");
        assertEquals("fs", e.getType());
    }

    @Test
    public void testClassmateEdgeType() {
        edge e = new Edge("c", "A", "B");
        assertEquals("c", e.getType());
    }

    @Test
    public void testStrangerEdgeType() {
        edge e = new Edge("s", "A", "B");
        assertEquals("s", e.getType());
    }

    @Test
    public void testStudyGroupEdgeType() {
        edge e = new Edge("sg", "A", "B");
        assertEquals("sg", e.getType());
    }

    // --- Weight tests ---

    @Test
    public void testSetAndGetWeight() {
        edge e = new Edge("f", "A", "B");
        e.setWeight(42.5f);
        assertEquals(42.5f, e.getWeight(), 0.001f);
    }

    @Test
    public void testZeroWeight() {
        edge e = new Edge("f", "A", "B");
        e.setWeight(0.0f);
        assertEquals(0.0f, e.getWeight(), 0.001f);
    }

    @Test
    public void testNegativeWeight() {
        edge e = new Edge("f", "A", "B");
        e.setWeight(-10.0f);
        assertEquals(-10.0f, e.getWeight(), 0.001f);
    }

    @Test
    public void testLargeWeight() {
        edge e = new Edge("f", "A", "B");
        e.setWeight(999999.99f);
        assertEquals(999999.99f, e.getWeight(), 1.0f);
    }

    // --- Label tests ---

    @Test
    public void testSetAndGetLabel() {
        edge e = new Edge("f", "A", "B");
        e.setLabel("friend");
        assertEquals("friend", e.getLabel());
    }

    @Test
    public void testLabelCanBeOverwritten() {
        edge e = new Edge("f", "A", "B");
        e.setLabel("friend");
        e.setLabel("best friend");
        assertEquals("best friend", e.getLabel());
    }

    @Test
    public void testLabelCanBeSetToNull() {
        edge e = new Edge("f", "A", "B");
        e.setLabel("friend");
        e.setLabel(null);
        assertNull(e.getLabel());
    }

    @Test
    public void testEmptyLabel() {
        edge e = new Edge("f", "A", "B");
        e.setLabel("");
        assertEquals("", e.getLabel());
    }

    // --- Vertex tests ---

    @Test
    public void testVerticesPreserveIds() {
        edge e = new Edge("c", "node_123", "node_456");
        assertEquals("node_123", e.getVertex1());
        assertEquals("node_456", e.getVertex2());
    }

    @Test
    public void testVerticesWithSpecialCharacters() {
        edge e = new Edge("f", "user@domain", "user#2");
        assertEquals("user@domain", e.getVertex1());
        assertEquals("user#2", e.getVertex2());
    }

    @Test
    public void testSelfLoop() {
        // edge class doesn't prevent self-loops — verify it stores them
        edge e = new Edge("f", "X", "X");
        assertEquals(e.getVertex1(), e.getVertex2());
    }
}
