package gvisual;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the {@link Edge} class.
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
        // Edge class doesn't prevent self-loops - verify it stores them
        Edge e = new Edge("f", "X", "X");
        assertEquals(e.getVertex1(), e.getVertex2());
    }

    // --- equals / hashCode contract (order-sensitive, directed-safe) ---

    @Test
    public void testEqualsReflexive() {
        Edge e = new Edge("f", "A", "B");
        e.setWeight(1.5f);
        assertEquals(e, e);
    }

    @Test
    public void testEqualsSameFieldsAreEqual() {
        Edge a = new Edge("f", "A", "B");
        a.setWeight(2.0f);
        Edge b = new Edge("f", "A", "B");
        b.setWeight(2.0f);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void testEqualsIgnoresLabel() {
        // Labels are display-only and excluded from identity.
        Edge a = new Edge("f", "A", "B");
        a.setLabel("close");
        Edge b = new Edge("f", "A", "B");
        b.setLabel("acquaintance");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void testEqualsIsOrderSensitive() {
        // Regression: previously equals treated (A,B) == (B,A), which made
        // it impossible to store both directions of an edge in a
        // DirectedSparseGraph (JUNG's addEdge would reject the reverse).
        Edge ab = new Edge("link", "A", "B");
        Edge ba = new Edge("link", "B", "A");
        assertNotEquals(ab, ba);
        // Hash codes should also differ for the swapped-vertex case so
        // hash-based containers don't collide spuriously.
        assertNotEquals(ab.hashCode(), ba.hashCode());
    }

    @Test
    public void testEqualsDifferentTypeNotEqual() {
        Edge a = new Edge("f", "A", "B");
        Edge b = new Edge("c", "A", "B");
        assertNotEquals(a, b);
    }

    @Test
    public void testEqualsDifferentWeightNotEqual() {
        Edge a = new Edge("f", "A", "B");
        a.setWeight(1.0f);
        Edge b = new Edge("f", "A", "B");
        b.setWeight(2.0f);
        assertNotEquals(a, b);
    }

    @Test
    public void testEqualsAgainstNullAndOtherType() {
        Edge a = new Edge("f", "A", "B");
        assertNotEquals(a, null);
        assertNotEquals(a, "not-an-edge");
    }

    @Test
    public void testHashCodeStableAcrossInvocations() {
        Edge e = new Edge("f", "A", "B");
        e.setWeight(3.25f);
        int h1 = e.hashCode();
        int h2 = e.hashCode();
        int h3 = e.hashCode();
        assertEquals(h1, h2);
        assertEquals(h2, h3);
    }

    @Test
    public void testEqualsHandlesNullEndpoints() {
        Edge a = new Edge();
        Edge b = new Edge();
        // Two default-constructed edges have identical (all-null/zero) state.
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    // --- equalsUndirected (legacy symmetric comparison) ---

    @Test
    public void testEqualsUndirectedTreatsReverseAsEqual() {
        Edge ab = new Edge("f", "A", "B");
        Edge ba = new Edge("f", "B", "A");
        assertTrue(ab.equalsUndirected(ba));
        assertTrue(ba.equalsUndirected(ab));
    }

    @Test
    public void testEqualsUndirectedStillChecksTypeAndWeight() {
        Edge ab = new Edge("f", "A", "B");
        ab.setWeight(1.0f);
        Edge baDiffType = new Edge("c", "B", "A");
        baDiffType.setWeight(1.0f);
        Edge baDiffWeight = new Edge("f", "B", "A");
        baDiffWeight.setWeight(2.0f);
        assertFalse(ab.equalsUndirected(baDiffType));
        assertFalse(ab.equalsUndirected(baDiffWeight));
    }

    @Test
    public void testEqualsUndirectedHandlesNull() {
        Edge ab = new Edge("f", "A", "B");
        assertFalse(ab.equalsUndirected(null));
    }

    @Test
    public void testEqualsUndirectedReflexive() {
        Edge ab = new Edge("f", "A", "B");
        assertTrue(ab.equalsUndirected(ab));
    }

    // --- Directed-graph integration (the bug this commit fixes) ---

    @Test
    public void testDirectedGraphAcceptsBothDirections() {
        // Regression test for the equals/hashCode bug: with order-insensitive
        // equals, this addEdge throws IllegalArgumentException because
        // JUNG considers the reverse edge already present.
        edu.uci.ics.jung.graph.DirectedSparseGraph<String, Edge> g =
                new edu.uci.ics.jung.graph.DirectedSparseGraph<>();
        g.addVertex("A");
        g.addVertex("B");
        Edge ab = new Edge("link", "A", "B");
        ab.setLabel("e1");
        Edge ba = new Edge("link", "B", "A");
        ba.setLabel("e2");
        assertTrue("forward edge should be added", g.addEdge(ab, "A", "B"));
        assertTrue("reverse edge should be added in a directed graph",
                g.addEdge(ba, "B", "A"));
        assertEquals(2, g.getEdgeCount());
    }
}
