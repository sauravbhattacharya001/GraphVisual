package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for GraphHealthChecker.
 */
public class GraphHealthCheckerTest {

    private Graph<String, Edge> makeTriangle() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        g.addVertex("B");
        g.addVertex("C");
        g.addEdge(new Edge("e1", "A", "B"), "A", "B");
        g.addEdge(new Edge("e2", "B", "C"), "B", "C");
        g.addEdge(new Edge("e3", "A", "C"), "A", "C");
        return g;
    }

    @Test
    public void analyzeHealthyGraph() {
        Graph<String, Edge> g = makeTriangle();
        GraphHealthChecker checker = new GraphHealthChecker(g);
        GraphHealthChecker.HealthReport report = checker.analyze();

        assertEquals(3, report.nodeCount);
        assertEquals(3, report.edgeCount);
        assertTrue("Healthy triangle should have high score", report.score >= 70);
        assertTrue("No isolated nodes expected", report.isolatedNodes.isEmpty());
        assertTrue("No self loops expected", report.selfLoops.isEmpty());
        assertEquals("Single component", 1, report.componentSizes.size());
    }

    @Test
    public void detectsIsolatedNodes() {
        Graph<String, Edge> g = makeTriangle();
        g.addVertex("Lonely");
        GraphHealthChecker checker = new GraphHealthChecker(g);
        GraphHealthChecker.HealthReport report = checker.analyze();

        assertTrue("Should detect isolated node", report.isolatedNodes.contains("Lonely"));
    }

    @Test
    public void detectsSelfLoops() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        g.addVertex("B");
        Edge selfLoop = new Edge("loop", "A", "A");
        g.addEdge(selfLoop, "A", "A");
        g.addEdge(new Edge("e1", "A", "B"), "A", "B");

        GraphHealthChecker checker = new GraphHealthChecker(g);
        GraphHealthChecker.HealthReport report = checker.analyze();
        assertFalse("Should detect self-loop", report.selfLoops.isEmpty());
    }

    @Test
    public void detectsMultipleComponents() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        g.addVertex("B");
        g.addEdge(new Edge("e1", "A", "B"), "A", "B");
        g.addVertex("C");
        g.addVertex("D");
        g.addEdge(new Edge("e2", "C", "D"), "C", "D");

        GraphHealthChecker checker = new GraphHealthChecker(g);
        GraphHealthChecker.HealthReport report = checker.analyze();
        assertEquals("Should find 2 components", 2, report.componentSizes.size());
    }

    @Test
    public void detectsBridges() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        g.addVertex("B");
        g.addVertex("C");
        g.addEdge(new Edge("e1", "A", "B"), "A", "B");
        g.addEdge(new Edge("e2", "B", "C"), "B", "C");

        GraphHealthChecker checker = new GraphHealthChecker(g);
        GraphHealthChecker.HealthReport report = checker.analyze();
        assertFalse("Linear chain has bridges", report.bridges.isEmpty());
    }

    @Test
    public void toTextProducesOutput() {
        GraphHealthChecker checker = new GraphHealthChecker(makeTriangle());
        GraphHealthChecker.HealthReport report = checker.analyze();
        String text = report.toText();
        assertNotNull(text);
        assertTrue("Should mention node count", text.contains("3"));
    }

    @Test
    public void toHtmlProducesOutput() {
        GraphHealthChecker checker = new GraphHealthChecker(makeTriangle());
        GraphHealthChecker.HealthReport report = checker.analyze();
        String html = GraphHealthChecker.toHtml(report);
        assertNotNull(html);
        assertTrue("Should be HTML", html.contains("<") && html.contains(">"));
    }

    @Test
    public void emptyGraphHasLowScore() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        GraphHealthChecker checker = new GraphHealthChecker(g);
        GraphHealthChecker.HealthReport report = checker.analyze();
        assertEquals(0, report.nodeCount);
        assertEquals(0, report.edgeCount);
    }
}
