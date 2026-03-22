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
        Edge e = new Edge();
        assertNull("type should be null after no-arg construction", e.getType());
        assertNull("vertex1 should be null after no-arg construction", e.getVertex1());
        assertNull("vertex2 should be null after no-arg construction", e.getVertex2());
        assertNull("label should be null by default", e.getLabel());
        assertEquals("weight should default to 0.0", 0.0f, e.getWeight(), 0.001f);
    }

    @Test
    public void testParameterizedConstructor() {
        Edge e = new Edge("f", "Alice", "Bob");
        assertEquals("f", e.getType());
        assertEquals("Alice", e.getVertex1());
        assertEquals("Bob", e.getVertex2());
    }

    // --- Edge type tests ---

    @Test
    public void testFriendEdgeType() {
        Edge e = new Edge("f", "A", "B");
        assertEquals("f", e.getType());
    }

    @Test
    public void testFamiliarStrangerEdgeType() {
        Edge e = new Edge("fs", "A", "B");
        assertEquals("fs", e.getType());
    }

    @Test
    public void testClassmateEdgeType() {
        Edge e = new Edge("c", "A", "B");
        assertEquals("c", e.getType());
    }

    @Test
    public void testStrangerEdgeType() {
        Edge e = new Edge("s", "A", "B");
        assertEquals("s", e.getType());
    }

    @Test
    public void testStudyGroupEdgeType() {
        Edge e = new Edge("sg", "A", "B");
        assertEquals("sg", e.getType());
    }

    // --- Weight tests ---

    @Test
    public void testSetAndGetWeight() {
        Edge e = new Edge("f", "A", "B");
        e.setWeight(42.5f);
        assertEquals(42.5f, e.getWeight(), 0.001f);
    }

    @Test
    public void testZeroWeight() {
        Edge e = new Edge("f", "A", "B");
        e.setWeight(0.0f);
        assertEquals(0.0f, e.getWeight(), 0.001f);
    }

    @Test
    public void testNegativeWeight() {
        Edge e = new Edge("f", "A", "B");
        e.setWeight(-10.0f);
        assertEquals(-10.0f, e.getWeight(), 0.001f);
    }

    @Test
    public void testLargeWeight() {
        Edge e = new Edge("f", "A", "B");
        e.setWeight(999999.99f);
        assertEquals(999999.99f, e.getWeight(), 1.0f);
    }

    // --- Label tests ---

    @Test
    public void testSetAndGetLabel() {
        Edge e = new Edge("f", "A", "B");
        e.setLabel("friend");
        assertEquals("friend", e.getLabel());
    }

    @Test
    public void testLabelCanBeOverwritten() {
        Edge e = new Edge("f", "A", "B");
        e.setLabel("friend");
        e.setLabel("best friend");
        assertEquals("best friend", e.getLabel());
    }

    @Test
    public void testLabelCanBeSetToNull() {
        Edge e = new Edge("f", "A", "B");
        e.setLabel("friend");
        e.setLabel(null);
        assertNull(e.getLabel());
    }

    @Test
    public void testEmptyLabel() {
        Edge e = new Edge("f", "A", "B");
        e.setLabel("");
        assertEquals("", e.getLabel());
    }

    // --- Vertex tests ---

    @Test
    public void testVerticesPreserveIds() {
        Edge e = new Edge("c", "node_123", "node_456");
        assertEquals("node_123", e.getVertex1());
        assertEquals("node_456", e.getVertex2());
    }

    @Test
    public void testVerticesWithSpecialCharacters() {
        Edge e = new Edge("f", "user@domain", "user#2");
        assertEquals("user@domain", e.getVertex1());
        assertEquals("user#2", e.getVertex2());
    }

    @Test
    public void testSelfLoop() {
        // edge class doesn't prevent self-loops — verify it stores them
        Edge e = new Edge("f", "X", "X");
        assertEquals(e.getVertex1(), e.getVertex2());
    }
}
