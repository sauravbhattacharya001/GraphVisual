package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link NetworkHealthWatchdog}.
 */
public class NetworkHealthWatchdogTest {

    private Graph<String, Edge> makeGraph(String[][] edges) {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (String[] e : edges) {
            g.addVertex(e[0]);
            g.addVertex(e[1]);
            Edge edge = new Edge("f", e[0], e[1]);
            g.addEdge(edge, e[0], e[1]);
        }
        return g;
    }

    private Graph<String, Edge> emptyGraphWithNodes(String... nodes) {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        for (String n : nodes) g.addVertex(n);
        return g;
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRequiresAtLeast2Snapshots() {
        NetworkHealthWatchdog w = new NetworkHealthWatchdog();
        w.addSnapshot("t1", new UndirectedSparseGraph<>());
        w.analyze();
    }

    @Test
    public void testNoAnomaliesForStableGraph() {
        Graph<String, Edge> g = makeGraph(new String[][]{{"A","B"},{"B","C"},{"A","C"}});
        NetworkHealthWatchdog w = new NetworkHealthWatchdog();
        w.addSnapshot("t1", g);
        w.addSnapshot("t2", g);
        NetworkHealthWatchdog.Report r = w.analyze();
        assertEquals(NetworkHealthWatchdog.Grade.A, r.grade);
        assertEquals(100.0, r.healthScore, 0.01);
        assertTrue(r.anomalies.isEmpty());
        assertEquals("stable", r.trajectory);
    }

    @Test
    public void testHubDisappeared() {
        // t1: star graph with hub H
        Graph<String, Edge> g1 = new UndirectedSparseGraph<>();
        g1.addVertex("H");
        for (int i = 1; i <= 5; i++) {
            String n = "N" + i;
            g1.addVertex(n);
            g1.addEdge(new Edge("f", "H", n), "H", n);
        }
        // t2: H removed
        Graph<String, Edge> g2 = new UndirectedSparseGraph<>();
        for (int i = 1; i <= 5; i++) g2.addVertex("N" + i);
        g2.addEdge(new Edge("f", "N1", "N2"), "N1", "N2");

        NetworkHealthWatchdog w = new NetworkHealthWatchdog();
        w.addSnapshot("t1", g1);
        w.addSnapshot("t2", g2);
        NetworkHealthWatchdog.Report r = w.analyze();

        boolean foundHub = false;
        for (NetworkHealthWatchdog.Anomaly a : r.anomalies) {
            if (a.type == NetworkHealthWatchdog.AnomalyType.HUB_DISAPPEARED) {
                foundHub = true;
                assertEquals(NetworkHealthWatchdog.Priority.P0, a.priority);
                assertTrue(a.detail.contains("H"));
            }
        }
        assertTrue("Expected HUB_DISAPPEARED anomaly", foundHub);
    }

    @Test
    public void testMassEdgeLoss() {
        Graph<String, Edge> g1 = makeGraph(new String[][]{
            {"A","B"},{"B","C"},{"C","D"},{"D","E"},{"E","A"},
            {"A","C"},{"B","D"},{"C","E"},{"D","A"},{"E","B"}
        });
        // Remove 80% of edges
        Graph<String, Edge> g2 = makeGraph(new String[][]{{"A","B"},{"B","C"}});
        g2.addVertex("D"); g2.addVertex("E");

        NetworkHealthWatchdog w = new NetworkHealthWatchdog();
        w.addSnapshot("t1", g1);
        w.addSnapshot("t2", g2);
        NetworkHealthWatchdog.Report r = w.analyze();

        boolean found = false;
        for (NetworkHealthWatchdog.Anomaly a : r.anomalies) {
            if (a.type == NetworkHealthWatchdog.AnomalyType.MASS_EDGE_LOSS) {
                found = true;
                assertEquals(NetworkHealthWatchdog.Priority.P0, a.priority);
            }
        }
        assertTrue("Expected MASS_EDGE_LOSS anomaly", found);
    }

    @Test
    public void testIsolationWave() {
        Graph<String, Edge> g1 = makeGraph(new String[][]{{"A","B"},{"C","D"}});
        g1.addVertex("E"); // 1 isolated node

        Graph<String, Edge> g2 = new UndirectedSparseGraph<>();
        for (String n : new String[]{"A","B","C","D","E","F","G","H"}) g2.addVertex(n);
        g2.addEdge(new Edge("f","A","B"), "A", "B");
        // 6 isolated nodes (C,D,E,F,G,H) - but prev had 1 isolated, now 6 -> 6x

        NetworkHealthWatchdog w = new NetworkHealthWatchdog();
        w.addSnapshot("t1", g1);
        w.addSnapshot("t2", g2);
        NetworkHealthWatchdog.Report r = w.analyze();

        boolean found = false;
        for (NetworkHealthWatchdog.Anomaly a : r.anomalies) {
            if (a.type == NetworkHealthWatchdog.AnomalyType.ISOLATION_WAVE) found = true;
        }
        assertTrue("Expected ISOLATION_WAVE anomaly", found);
    }

    @Test
    public void testComponentFragmentation() {
        // t1: 1 connected component
        Graph<String, Edge> g1 = makeGraph(new String[][]{
            {"A","B"},{"B","C"},{"C","D"},{"D","E"},{"E","F"}
        });
        // t2: 4 disconnected components
        Graph<String, Edge> g2 = new UndirectedSparseGraph<>();
        g2.addVertex("A"); g2.addVertex("B");
        g2.addEdge(new Edge("f","A","B"), "A", "B");
        g2.addVertex("C"); g2.addVertex("D");
        g2.addEdge(new Edge("f","C","D"), "C", "D");
        g2.addVertex("E"); g2.addVertex("F");
        // E and F isolated = 4 components total

        NetworkHealthWatchdog w = new NetworkHealthWatchdog();
        w.addSnapshot("t1", g1);
        w.addSnapshot("t2", g2);
        NetworkHealthWatchdog.Report r = w.analyze();

        boolean found = false;
        for (NetworkHealthWatchdog.Anomaly a : r.anomalies) {
            if (a.type == NetworkHealthWatchdog.AnomalyType.COMPONENT_FRAGMENTATION) found = true;
        }
        assertTrue("Expected COMPONENT_FRAGMENTATION anomaly", found);
    }

    @Test
    public void testPlaybookGenerated() {
        Graph<String, Edge> g1 = makeGraph(new String[][]{{"A","B"},{"B","C"},{"A","C"}});
        Graph<String, Edge> g2 = emptyGraphWithNodes("A", "B", "C", "D", "E");
        // Mass edge loss + isolation

        NetworkHealthWatchdog w = new NetworkHealthWatchdog();
        w.addSnapshot("t1", g1);
        w.addSnapshot("t2", g2);
        NetworkHealthWatchdog.Report r = w.analyze();

        assertFalse("Playbook should not be empty", r.playbook.isEmpty());
        // First action should be highest priority
        for (NetworkHealthWatchdog.Action a : r.playbook) {
            assertNotNull(a.type);
            assertNotNull(a.owner);
            assertTrue(a.blastRadius >= 1 && a.blastRadius <= 5);
        }
    }

    @Test
    public void testToTextNotEmpty() {
        Graph<String, Edge> g1 = makeGraph(new String[][]{{"A","B"}});
        Graph<String, Edge> g2 = makeGraph(new String[][]{{"A","B"},{"B","C"},{"A","C"}});

        NetworkHealthWatchdog w = new NetworkHealthWatchdog();
        w.addSnapshot("t1", g1);
        w.addSnapshot("t2", g2);
        NetworkHealthWatchdog.Report r = w.analyze();

        String text = w.toText(r);
        assertTrue(text.contains("NETWORK HEALTH:"));
        assertTrue(text.contains("Grade:"));
    }

    @Test
    public void testToMarkdownHasSections() {
        Graph<String, Edge> g1 = makeGraph(new String[][]{{"A","B"}});
        Graph<String, Edge> g2 = makeGraph(new String[][]{{"A","B"}});

        NetworkHealthWatchdog w = new NetworkHealthWatchdog();
        w.addSnapshot("t1", g1);
        w.addSnapshot("t2", g2);
        NetworkHealthWatchdog.Report r = w.analyze();

        String md = w.toMarkdown(r);
        assertTrue(md.contains("## Network Health Watchdog Report"));
        assertTrue(md.contains("### Snapshot Metrics"));
    }

    @Test
    public void testToJsonValid() {
        Graph<String, Edge> g1 = makeGraph(new String[][]{{"A","B"}});
        Graph<String, Edge> g2 = makeGraph(new String[][]{{"A","B"}});

        NetworkHealthWatchdog w = new NetworkHealthWatchdog();
        w.addSnapshot("t1", g1);
        w.addSnapshot("t2", g2);
        NetworkHealthWatchdog.Report r = w.analyze();

        String json = w.toJson(r);
        assertTrue(json.startsWith("{"));
        assertTrue(json.contains("\"grade\""));
        assertTrue(json.contains("\"healthScore\""));
        assertTrue(json.contains("\"playbook\""));
    }

    @Test
    public void testTrajectoryDeclining() {
        Graph<String, Edge> g1 = makeGraph(new String[][]{
            {"A","B"},{"B","C"},{"C","D"},{"D","A"},{"A","C"},{"B","D"}
        });
        Graph<String, Edge> g2 = makeGraph(new String[][]{{"A","B"},{"B","C"},{"C","D"}});
        g2.addVertex("E"); g2.addVertex("F");
        Graph<String, Edge> g3 = makeGraph(new String[][]{{"A","B"}});
        g3.addVertex("C"); g3.addVertex("D"); g3.addVertex("E"); g3.addVertex("F");

        NetworkHealthWatchdog w = new NetworkHealthWatchdog();
        w.addSnapshot("t1", g1);
        w.addSnapshot("t2", g2);
        w.addSnapshot("t3", g3);
        NetworkHealthWatchdog.Report r = w.analyze();
        assertEquals("declining", r.trajectory);
    }

    @Test
    public void testGradeScale() {
        // Stable -> A
        Graph<String, Edge> g = makeGraph(new String[][]{{"A","B"},{"B","C"}});
        NetworkHealthWatchdog w = new NetworkHealthWatchdog();
        w.addSnapshot("t1", g);
        w.addSnapshot("t2", g);
        NetworkHealthWatchdog.Report r = w.analyze();
        assertEquals(NetworkHealthWatchdog.Grade.A, r.grade);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullLabel() {
        new NetworkHealthWatchdog().addSnapshot(null, new UndirectedSparseGraph<>());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraph() {
        new NetworkHealthWatchdog().addSnapshot("t1", null);
    }

    @Test
    public void testDensitySpikeDetected() {
        // t1: sparse, t2: much denser
        Graph<String, Edge> g1 = new UndirectedSparseGraph<>();
        for (int i = 0; i < 10; i++) g1.addVertex("N" + i);
        g1.addEdge(new Edge("f","N0","N1"), "N0", "N1");
        g1.addEdge(new Edge("f","N2","N3"), "N2", "N3");

        // t2: near-complete
        Graph<String, Edge> g2 = new UndirectedSparseGraph<>();
        for (int i = 0; i < 10; i++) g2.addVertex("N" + i);
        int edgeId = 0;
        for (int i = 0; i < 10; i++) {
            for (int j = i + 1; j < 10; j++) {
                g2.addEdge(new Edge("f","N"+i,"N"+j), "N"+i, "N"+j);
            }
        }

        NetworkHealthWatchdog w = new NetworkHealthWatchdog();
        w.addSnapshot("t1", g1);
        w.addSnapshot("t2", g2);
        NetworkHealthWatchdog.Report r = w.analyze();

        boolean found = false;
        for (NetworkHealthWatchdog.Anomaly a : r.anomalies) {
            if (a.type == NetworkHealthWatchdog.AnomalyType.DENSITY_SPIKE) found = true;
        }
        assertTrue("Expected DENSITY_SPIKE anomaly", found);
    }
}
