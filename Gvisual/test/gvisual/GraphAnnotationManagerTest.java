package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.util.*;

/**
 * Tests for GraphAnnotationManager.
 */
public class GraphAnnotationManagerTest {

    private GraphAnnotationManager manager;

    @Before
    public void setUp() {
        manager = new GraphAnnotationManager();
    }

    // --- Node annotation basics ---

    @Test
    public void testAnnotateNode_createsAnnotation() {
        GraphAnnotationManager.Annotation a = manager.annotateNode("A");
        assertNotNull(a);
        assertEquals("A", a.getElementId());
        assertEquals(GraphAnnotationManager.Annotation.ElementType.NODE, a.getElementType());
    }

    @Test
    public void testAnnotateNode_returnsSameInstance() {
        GraphAnnotationManager.Annotation a1 = manager.annotateNode("A");
        GraphAnnotationManager.Annotation a2 = manager.annotateNode("A");
        assertSame(a1, a2);
    }

    @Test
    public void testGetNodeAnnotation_returnsNullWhenMissing() {
        assertNull(manager.getNodeAnnotation("X"));
    }

    @Test
    public void testRemoveNodeAnnotation() {
        manager.annotateNode("A");
        assertTrue(manager.removeNodeAnnotation("A"));
        assertNull(manager.getNodeAnnotation("A"));
        assertFalse(manager.removeNodeAnnotation("A"));
    }

    // --- Edge annotation basics ---

    @Test
    public void testAnnotateEdge_canonical() {
        GraphAnnotationManager.Annotation a1 = manager.annotateEdge("B", "A");
        GraphAnnotationManager.Annotation a2 = manager.annotateEdge("A", "B");
        assertSame(a1, a2); // canonical key
        assertEquals("A--B", a1.getElementId());
    }

    @Test
    public void testGetEdgeAnnotation() {
        manager.annotateEdge("X", "Y").setNote("link");
        assertEquals("link", manager.getEdgeAnnotation("Y", "X").getNote());
    }

    @Test
    public void testRemoveEdgeAnnotation() {
        manager.annotateEdge("A", "B");
        assertTrue(manager.removeEdgeAnnotation("B", "A"));
        assertNull(manager.getEdgeAnnotation("A", "B"));
    }

    // --- Notes ---

    @Test
    public void testSetNote() {
        GraphAnnotationManager.Annotation a = manager.annotateNode("N1");
        a.setNote("Suspicious node");
        assertEquals("Suspicious node", a.getNote());
    }

    @Test
    public void testSetNote_nullBecomesEmpty() {
        GraphAnnotationManager.Annotation a = manager.annotateNode("N1");
        a.setNote(null);
        assertEquals("", a.getNote());
    }

    // --- Tags ---

    @Test
    public void testAddTag() {
        GraphAnnotationManager.Annotation a = manager.annotateNode("N1");
        a.addTag("Important");
        assertTrue(a.hasTag("important")); // normalized
        assertTrue(a.getTags().contains("important"));
    }

    @Test
    public void testRemoveTag() {
        GraphAnnotationManager.Annotation a = manager.annotateNode("N1");
        a.addTag("test");
        a.removeTag("TEST"); // case-insensitive
        // removeTag normalizes too
        assertFalse(a.hasTag("test"));
    }

    @Test
    public void testAddTag_ignoresNullAndEmpty() {
        GraphAnnotationManager.Annotation a = manager.annotateNode("N1");
        a.addTag(null);
        a.addTag("  ");
        assertEquals(0, a.getTags().size());
    }

    // --- Color ---

    @Test
    public void testSetColor() {
        GraphAnnotationManager.Annotation a = manager.annotateNode("N1");
        a.setColor("#FF0000");
        assertEquals("#FF0000", a.getColor());
    }

    // --- Author ---

    @Test
    public void testSetAuthor() {
        GraphAnnotationManager.Annotation a = manager.annotateNode("N1");
        a.setAuthor("researcher1");
        assertEquals("researcher1", a.getAuthor());
    }

    // --- Priority ---

    @Test
    public void testSetPriority() {
        GraphAnnotationManager.Annotation a = manager.annotateNode("N1");
        a.setPriority(GraphAnnotationManager.Annotation.Priority.CRITICAL);
        assertEquals(GraphAnnotationManager.Annotation.Priority.CRITICAL, a.getPriority());
    }

    @Test
    public void testSetPriority_nullIgnored() {
        GraphAnnotationManager.Annotation a = manager.annotateNode("N1");
        a.setPriority(null);
        assertEquals(GraphAnnotationManager.Annotation.Priority.MEDIUM, a.getPriority()); // default
    }

    // --- Timestamps ---

    @Test
    public void testTimestamps() {
        GraphAnnotationManager.Annotation a = manager.annotateNode("N1");
        assertTrue(a.getCreatedAt() > 0);
        long created = a.getCreatedAt();
        a.setNote("update");
        assertTrue(a.getUpdatedAt() >= created);
    }

    // --- Bulk operations ---

    @Test
    public void testBulkTagNodes() {
        int count = manager.bulkTagNodes(Arrays.asList("A", "B", "C"), "group1");
        assertEquals(3, count);
        assertTrue(manager.getNodeAnnotation("A").hasTag("group1"));
        assertTrue(manager.getNodeAnnotation("C").hasTag("group1"));
    }

    @Test
    public void testBulkColorNodes() {
        manager.bulkColorNodes(Arrays.asList("X", "Y"), "#00FF00");
        assertEquals("#00FF00", manager.getNodeAnnotation("X").getColor());
        assertEquals("#00FF00", manager.getNodeAnnotation("Y").getColor());
    }

    // --- Search ---

    @Test
    public void testFindByTag() {
        manager.annotateNode("A").addTag("hub");
        manager.annotateNode("B").addTag("hub");
        manager.annotateNode("C").addTag("leaf");
        manager.annotateEdge("A", "B").addTag("hub");
        List<GraphAnnotationManager.Annotation> results = manager.findByTag("hub");
        assertEquals(3, results.size());
    }

    @Test
    public void testSearchNotes() {
        manager.annotateNode("A").setNote("This is suspicious");
        manager.annotateNode("B").setNote("Normal node");
        manager.annotateEdge("A", "B").setNote("Suspicious link");
        List<GraphAnnotationManager.Annotation> results = manager.searchNotes("suspicious");
        assertEquals(2, results.size());
    }

    @Test
    public void testFindByPriority() {
        manager.annotateNode("A").setPriority(GraphAnnotationManager.Annotation.Priority.CRITICAL);
        manager.annotateNode("B").setPriority(GraphAnnotationManager.Annotation.Priority.LOW);
        manager.annotateNode("C").setPriority(GraphAnnotationManager.Annotation.Priority.CRITICAL);
        List<GraphAnnotationManager.Annotation> results =
            manager.findByPriority(GraphAnnotationManager.Annotation.Priority.CRITICAL);
        assertEquals(2, results.size());
    }

    @Test
    public void testFindByAuthor() {
        manager.annotateNode("A").setAuthor("alice");
        manager.annotateNode("B").setAuthor("bob");
        manager.annotateNode("C").setAuthor("alice");
        List<GraphAnnotationManager.Annotation> results = manager.findByAuthor("alice");
        assertEquals(2, results.size());
    }

    // --- Tag aggregation ---

    @Test
    public void testGetAllTags() {
        manager.annotateNode("A").addTag("hub");
        manager.annotateNode("B").addTag("leaf");
        manager.annotateEdge("A", "B").addTag("strong");
        Set<String> tags = manager.getAllTags();
        assertEquals(3, tags.size());
        assertTrue(tags.contains("hub"));
        assertTrue(tags.contains("leaf"));
        assertTrue(tags.contains("strong"));
    }

    @Test
    public void testGetTagCounts() {
        manager.annotateNode("A").addTag("hub");
        manager.annotateNode("B").addTag("hub");
        manager.annotateNode("C").addTag("leaf");
        Map<String, Integer> counts = manager.getTagCounts();
        assertEquals(Integer.valueOf(2), counts.get("hub"));
        assertEquals(Integer.valueOf(1), counts.get("leaf"));
    }

    // --- Statistics ---

    @Test
    public void testCounts() {
        manager.annotateNode("A");
        manager.annotateNode("B");
        manager.annotateEdge("A", "B");
        assertEquals(2, manager.getNodeAnnotationCount());
        assertEquals(1, manager.getEdgeAnnotationCount());
        assertEquals(3, manager.getTotalAnnotationCount());
    }

    @Test
    public void testGetSummary() {
        manager.annotateNode("A").addTag("test");
        String summary = manager.getSummary();
        assertTrue(summary.contains("Node annotations: 1"));
        assertTrue(summary.contains("Tags: test"));
    }

    // --- Graph integration ---

    @Test
    public void testAutoTagByDegree() {
        Graph<String, Edge> graph = new UndirectedSparseGraph<>();
        graph.addVertex("hub");
        graph.addVertex("leaf1");
        graph.addVertex("leaf2");
        graph.addVertex("leaf3");
        graph.addVertex("isolated");
        graph.addEdge(new Edge("f", "hub", "leaf1"), "hub", "leaf1");
        graph.addEdge(new Edge("f", "hub", "leaf2"), "hub", "leaf2");
        graph.addEdge(new Edge("f", "hub", "leaf3"), "hub", "leaf3");

        int count = manager.autoTagByDegree(graph, 3, 0);
        assertTrue(count > 0);
        assertTrue(manager.getNodeAnnotation("hub").hasTag("hub"));
        assertTrue(manager.getNodeAnnotation("isolated").hasTag("peripheral"));
    }

    @Test
    public void testAutoTagIsolated() {
        Graph<String, Edge> graph = new UndirectedSparseGraph<>();
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        graph.addEdge(new Edge("f", "A", "B"), "A", "B");

        int count = manager.autoTagIsolated(graph);
        assertEquals(1, count);
        assertTrue(manager.getNodeAnnotation("C").hasTag("isolated"));
        assertEquals(GraphAnnotationManager.Annotation.Priority.HIGH,
            manager.getNodeAnnotation("C").getPriority());
    }

    // --- JSON export/import ---

    @Test
    public void testExportImportRoundTrip() {
        manager.annotateNode("A").setNote("Test node");
        manager.annotateNode("A").addTag("important");
        manager.annotateNode("A").setColor("#FF0000");
        manager.annotateNode("A").setAuthor("alice");
        manager.annotateNode("A").setPriority(GraphAnnotationManager.Annotation.Priority.HIGH);
        manager.annotateEdge("X", "Y").setNote("Test Edge");
        manager.annotateEdge("X", "Y").addTag("bridge");

        String json = manager.exportToJson();

        GraphAnnotationManager manager2 = new GraphAnnotationManager();
        int count = manager2.importFromJson(json);
        assertEquals(2, count);

        GraphAnnotationManager.Annotation a = manager2.getNodeAnnotation("A");
        assertNotNull(a);
        assertEquals("Test node", a.getNote());
        assertTrue(a.hasTag("important"));
        assertEquals("#FF0000", a.getColor());
        assertEquals("alice", a.getAuthor());
        assertEquals(GraphAnnotationManager.Annotation.Priority.HIGH, a.getPriority());

        GraphAnnotationManager.Annotation e = manager2.getEdgeAnnotation("X", "Y");
        assertNotNull(e);
        assertEquals("Test Edge", e.getNote());
        assertTrue(e.hasTag("bridge"));
    }

    @Test
    public void testExportImportFile() throws Exception {
        manager.annotateNode("FileTest").setNote("Persisted");
        File tmpFile = File.createTempFile("annotations", ".json");
        tmpFile.deleteOnExit();

        manager.exportToFile(tmpFile.getAbsolutePath());

        GraphAnnotationManager manager2 = new GraphAnnotationManager();
        int count = manager2.importFromFile(tmpFile.getAbsolutePath());
        assertEquals(1, count);
        assertEquals("Persisted", manager2.getNodeAnnotation("FileTest").getNote());
    }

    // --- Clear ---

    @Test
    public void testClear() {
        manager.annotateNode("A");
        manager.annotateEdge("B", "C");
        manager.clear();
        assertEquals(0, manager.getTotalAnnotationCount());
    }

    // --- Edge key ---

    @Test
    public void testEdgeKey_canonical() {
        assertEquals("A--B", GraphAnnotationManager.edgeKey("A", "B"));
        assertEquals("A--B", GraphAnnotationManager.edgeKey("B", "A"));
    }

    // --- Annotation constructor validation ---

    @Test(expected = IllegalArgumentException.class)
    public void testAnnotation_nullId() {
        new GraphAnnotationManager.Annotation(null, GraphAnnotationManager.Annotation.ElementType.NODE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAnnotation_emptyId() {
        new GraphAnnotationManager.Annotation("  ", GraphAnnotationManager.Annotation.ElementType.NODE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAnnotation_nullType() {
        new GraphAnnotationManager.Annotation("A", null);
    }

    // --- Immutable tags set ---

    @Test(expected = UnsupportedOperationException.class)
    public void testGetTags_immutable() {
        GraphAnnotationManager.Annotation a = manager.annotateNode("A");
        a.addTag("test");
        a.getTags().add("hack");
    }
}
