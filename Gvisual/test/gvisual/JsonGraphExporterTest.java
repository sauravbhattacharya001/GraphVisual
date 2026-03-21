package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link JsonGraphExporter}.
 */
public class JsonGraphExporterTest {

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new JsonGraphExporter(null, null);
    }

    @Test
    public void testNullEdgeListAccepted() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        JsonGraphExporter exporter = new JsonGraphExporter(g, null);
        String json = exporter.exportToString();
        assertNotNull(json);
        assertTrue(json.contains("\"nodes\""));
        assertTrue(json.contains("\"links\""));
    }

    @Test
    public void testEmptyGraphJson() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        JsonGraphExporter exporter = new JsonGraphExporter(g, new ArrayList<Edge>());
        exporter.setTimestamp("2011-03-15");
        String json = exporter.exportToString();

        assertTrue(json.contains("\"nodeCount\": 0"));
        assertTrue(json.contains("\"edgeCount\": 0"));
        assertTrue(json.contains("\"timestamp\": \"2011-03-15\""));
        assertTrue(json.contains("\"nodes\": ["));
        assertTrue(json.contains("\"links\": ["));
    }

    @Test
    public void testSingleEdgeGraph() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        g.addVertex("B");
        edge e = new Edge("f", "A", "B");
        e.setWeight(2.5f);
        e.setLabel("friends");
        g.addEdge(e, "A", "B");

        List<Edge> edges = new ArrayList<>();
        edges.add(e);

        JsonGraphExporter exporter = new JsonGraphExporter(g, edges);
        String json = exporter.exportToString();

        assertTrue(json.contains("\"nodeCount\": 2"));
        assertTrue(json.contains("\"edgeCount\": 1"));
        assertTrue(json.contains("\"id\": \"A\""));
        assertTrue(json.contains("\"id\": \"B\""));
        assertTrue(json.contains("\"source\": \"A\""));
        assertTrue(json.contains("\"target\": \"B\""));
        assertTrue(json.contains("\"type\": \"f\""));
        assertTrue(json.contains("\"weight\": 2.5"));
        assertTrue(json.contains("\"label\": \"friends\""));
    }

    @Test
    public void testStatsIncluded() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        g.addVertex("B");
        g.addVertex("C");
        edge e1 = new Edge("f", "A", "B");
        edge e2 = new Edge("c", "B", "C");
        g.addEdge(e1, "A", "B");
        g.addEdge(e2, "B", "C");

        JsonGraphExporter exporter = new JsonGraphExporter(g, Arrays.asList(e1, e2));
        exporter.setIncludeStats(true);
        String json = exporter.exportToString();

        assertTrue(json.contains("\"averageDegree\""));
        assertTrue(json.contains("\"maxDegree\": 2"));
        assertTrue(json.contains("\"density\""));
    }

    @Test
    public void testStatsDisabled() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("X");
        edge e = new Edge("s", "X", "X");
        // self-loop won't work in undirected sparse, just test empty stats
        JsonGraphExporter exporter = new JsonGraphExporter(g, new ArrayList<Edge>());
        exporter.setIncludeStats(false);
        String json = exporter.exportToString();

        assertFalse(json.contains("\"averageDegree\""));
        assertFalse(json.contains("\"maxDegree\""));
    }

    @Test
    public void testCompactOutput() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        JsonGraphExporter exporter = new JsonGraphExporter(g, new ArrayList<Edge>());
        exporter.setPrettyPrint(false);
        String json = exporter.exportToString();

        // Compact should not have leading spaces
        assertFalse(json.contains("  "));
    }

    @Test
    public void testTimestampOnEdge() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        g.addVertex("B");
        edge e = new Edge("f", "A", "B");
        e.setTimestamp(1300000000000L);
        e.setEndTimestamp(1300100000000L);
        g.addEdge(e, "A", "B");

        JsonGraphExporter exporter = new JsonGraphExporter(g, Arrays.asList(e));
        String json = exporter.exportToString();

        assertTrue(json.contains("\"timestamp\": 1300000000000"));
        assertTrue(json.contains("\"endTimestamp\": 1300100000000"));
    }

    @Test
    public void testSpecialCharactersEscaped() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("node\"1");
        JsonGraphExporter exporter = new JsonGraphExporter(g, new ArrayList<Edge>());
        String json = exporter.exportToString();

        assertTrue(json.contains("node\\\"1"));
    }

    @Test
    public void testFileExport() throws IOException {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        g.addVertex("B");
        edge e = new Edge("f", "A", "B");
        g.addEdge(e, "A", "B");

        JsonGraphExporter exporter = new JsonGraphExporter(g, Arrays.asList(e));
        File tmp = File.createTempFile("graphvisual-test-", ".json");
        tmp.deleteOnExit();
        exporter.export(tmp);

        String content = new String(Files.readAllBytes(tmp.toPath()), "UTF-8");
        assertTrue(content.contains("\"nodes\""));
        assertTrue(content.contains("\"links\""));
        assertTrue(content.length() > 10);
    }

    @Test
    public void testEdgeTypesPerNode() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        g.addVertex("B");
        g.addVertex("C");
        edge e1 = new Edge("f", "A", "B");
        edge e2 = new Edge("c", "A", "C");
        g.addEdge(e1, "A", "B");
        g.addEdge(e2, "A", "C");

        JsonGraphExporter exporter = new JsonGraphExporter(g, Arrays.asList(e1, e2));
        String json = exporter.exportToString();

        // Node A should have edgeTypes with both "f" and "c"
        assertTrue(json.contains("\"edgeTypes\""));
        assertTrue(json.contains("\"f\": 1"));
        assertTrue(json.contains("\"c\": 1"));
    }

    @Test
    public void testDescriptionInMetadata() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        JsonGraphExporter exporter = new JsonGraphExporter(g, new ArrayList<Edge>());
        exporter.setDescription("Test graph for unit testing");
        String json = exporter.exportToString();

        assertTrue(json.contains("\"description\": \"Test graph for unit testing\""));
    }
}
