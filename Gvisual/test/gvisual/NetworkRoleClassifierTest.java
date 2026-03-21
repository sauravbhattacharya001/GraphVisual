package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link NetworkRoleClassifier}.
 *
 * <p>Covers: classification pipeline, role assignment rules, adaptive thresholds,
 * report generation, edge cases (empty graph, single node, disconnected nodes,
 * complete graph), and all structural role archetypes.</p>
 *
 * @author zalenix
 */
public class NetworkRoleClassifierTest {

    // ── Helper methods ──────────────────────────────────────────

    private static Graph<String, Edge> emptyGraph() {
        return new UndirectedSparseGraph<>();
    }

    private static void addEdge(Graph<String, Edge> g, String v1, String v2) {
        String id = v1 + "-" + v2;
        edge e = new Edge("f", v1, v2);
        e.setLabel(id);
        g.addEdge(e, v1, v2);
    }

    /**
     * Builds a star graph: center connected to all spokes, no spoke-spoke edges.
     */
    private static Graph<String, Edge> starGraph(int spokes) {
        Graph<String, Edge> g = emptyGraph();
        g.addVertex("center");
        for (int i = 1; i <= spokes; i++) {
            String spoke = "s" + i;
            addEdge(g, "center", spoke);
        }
        return g;
    }

    /**
     * Builds a bridge topology: two cliques connected by a single bridge node.
     */
    private static Graph<String, Edge> bridgeGraph() {
        Graph<String, Edge> g = emptyGraph();
        String[] cliqueA = {"A1", "A2", "A3", "A4"};
        for (int i = 0; i < cliqueA.length; i++) {
            for (int j = i + 1; j < cliqueA.length; j++) {
                addEdge(g, cliqueA[i], cliqueA[j]);
            }
        }
        String[] cliqueB = {"B1", "B2", "B3", "B4"};
        for (int i = 0; i < cliqueB.length; i++) {
            for (int j = i + 1; j < cliqueB.length; j++) {
                addEdge(g, cliqueB[i], cliqueB[j]);
            }
        }
        g.addVertex("X");
        addEdge(g, "X", "A1");
        addEdge(g, "X", "B1");
        return g;
    }

    /**
     * Builds a complete graph of n nodes (K_n).
     */
    private static Graph<String, Edge> completeGraph(int n) {
        Graph<String, Edge> g = emptyGraph();
        for (int i = 1; i <= n; i++) g.addVertex("N" + i);
        for (int i = 1; i <= n; i++) {
            for (int j = i + 1; j <= n; j++) {
                addEdge(g, "N" + i, "N" + j);
            }
        }
        return g;
    }

    // ── Null graph ──────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullGraph_throws() {
        new NetworkRoleClassifier(null);
    }

    // ── Empty graph ─────────────────────────────────────────────

    @Test
    public void classify_emptyGraph_noRoles() {
        NetworkRoleClassifier c = new NetworkRoleClassifier(emptyGraph());
        c.classify();
        assertTrue(c.getAllRoles().isEmpty());
    }

    @Test
    public void distribution_emptyGraph_allZeros() {
        NetworkRoleClassifier c = new NetworkRoleClassifier(emptyGraph());
        c.classify();
        NetworkRoleClassifier.RoleDistribution dist = c.getRoleDistribution();
        assertEquals(0, dist.getTotalNodes());
        for (NetworkRoleClassifier.StructuralRole r : NetworkRoleClassifier.StructuralRole.values()) {
            assertEquals(0, dist.getCount(r));
        }
    }

    // ── Single node ─────────────────────────────────────────────

    @Test
    public void classify_singleIsolatedNode() {
        Graph<String, Edge> g = emptyGraph();
        g.addVertex("alone");
        NetworkRoleClassifier c = new NetworkRoleClassifier(g);
        c.classify();
        NetworkRoleClassifier.NodeRole role = c.getRole("alone");
        assertNotNull(role);
        assertEquals(NetworkRoleClassifier.StructuralRole.ISOLATE, role.getRole());
        assertEquals(0, role.getDegree());
    }

    // ── Isolate detection ───────────────────────────────────────

    @Test
    public void classify_disconnectedNodes_allIsolates() {
        Graph<String, Edge> g = emptyGraph();
        g.addVertex("X");
        g.addVertex("Y");
        g.addVertex("Z");
        NetworkRoleClassifier c = new NetworkRoleClassifier(g);
        c.classify();
        for (String v : new String[]{"X", "Y", "Z"}) {
            assertEquals(NetworkRoleClassifier.StructuralRole.ISOLATE,
                    c.getRole(v).getRole());
        }
    }

    // ── Star graph: hub + peripherals ───────────────────────────

    @Test
    public void classify_starGraph_centerIsNotPeripheral() {
        Graph<String, Edge> g = starGraph(8);
        NetworkRoleClassifier c = new NetworkRoleClassifier(g);
        c.classify();
        NetworkRoleClassifier.NodeRole centerRole = c.getRole("center");
        assertNotNull(centerRole);
        assertEquals(8, centerRole.getDegree());
        assertNotEquals(NetworkRoleClassifier.StructuralRole.PERIPHERAL, centerRole.getRole());
        assertNotEquals(NetworkRoleClassifier.StructuralRole.ISOLATE, centerRole.getRole());
    }

    @Test
    public void classify_starGraph_spokesArePeripheral() {
        Graph<String, Edge> g = starGraph(8);
        NetworkRoleClassifier c = new NetworkRoleClassifier(g);
        c.classify();
        for (int i = 1; i <= 8; i++) {
            NetworkRoleClassifier.NodeRole nr = c.getRole("s" + i);
            assertEquals(1, nr.getDegree());
            assertEquals(NetworkRoleClassifier.StructuralRole.PERIPHERAL, nr.getRole());
        }
    }

    // ── Bridge topology ─────────────────────────────────────────

    @Test
    public void classify_bridgeGraph_XHighBetweenness() {
        Graph<String, Edge> g = bridgeGraph();
        NetworkRoleClassifier c = new NetworkRoleClassifier(g);
        c.classify();
        NetworkRoleClassifier.NodeRole xRole = c.getRole("X");
        assertNotNull(xRole);
        assertTrue("Bridge node X should be BRIDGE or CONNECTOR",
                xRole.getRole() == NetworkRoleClassifier.StructuralRole.BRIDGE
             || xRole.getRole() == NetworkRoleClassifier.StructuralRole.CONNECTOR);
    }

    @Test
    public void classify_bridgeGraph_cliqueNodesNotIsolate() {
        Graph<String, Edge> g = bridgeGraph();
        NetworkRoleClassifier c = new NetworkRoleClassifier(g);
        c.classify();
        for (String v : new String[]{"A1", "A2", "B1", "B2"}) {
            assertNotEquals(NetworkRoleClassifier.StructuralRole.ISOLATE,
                    c.getRole(v).getRole());
        }
    }

    // ── Complete graph: all nodes are symmetric ─────────────────

    @Test
    public void classify_completeGraph_allSameRole() {
        Graph<String, Edge> g = completeGraph(6);
        NetworkRoleClassifier c = new NetworkRoleClassifier(g);
        c.classify();
        Set<NetworkRoleClassifier.StructuralRole> seenRoles = new HashSet<>();
        for (NetworkRoleClassifier.NodeRole nr : c.getAllRoles().values()) {
            seenRoles.add(nr.getRole());
            assertEquals(5, nr.getDegree());
            assertEquals(1.0, nr.getDegreeCentrality(), 0.001);
            assertEquals(1.0, nr.getClusteringCoefficient(), 0.001);
        }
        assertEquals("All nodes in K6 should have the same role", 1, seenRoles.size());
    }

    // ── getNodesByRole ──────────────────────────────────────────

    @Test
    public void getNodesByRole_returnsCorrectNodes() {
        Graph<String, Edge> g = emptyGraph();
        g.addVertex("X");
        g.addVertex("Y");
        NetworkRoleClassifier c = new NetworkRoleClassifier(g);
        c.classify();
        List<String> isolates = c.getNodesByRole(NetworkRoleClassifier.StructuralRole.ISOLATE);
        assertEquals(2, isolates.size());
        assertTrue(isolates.contains("X"));
        assertTrue(isolates.contains("Y"));
    }

    @Test
    public void getNodesByRole_isSorted() {
        Graph<String, Edge> g = emptyGraph();
        g.addVertex("Z");
        g.addVertex("A");
        g.addVertex("M");
        NetworkRoleClassifier c = new NetworkRoleClassifier(g);
        c.classify();
        List<String> isolates = c.getNodesByRole(NetworkRoleClassifier.StructuralRole.ISOLATE);
        assertEquals(Arrays.asList("A", "M", "Z"), isolates);
    }

    @Test
    public void getNodesByRole_emptyForUnusedRole() {
        Graph<String, Edge> g = emptyGraph();
        g.addVertex("A");
        NetworkRoleClassifier c = new NetworkRoleClassifier(g);
        c.classify();
        assertTrue(c.getNodesByRole(NetworkRoleClassifier.StructuralRole.HUB).isEmpty());
    }

    // ── RoleDistribution ────────────────────────────────────────

    @Test
    public void distribution_countsMatchTotal() {
        Graph<String, Edge> g = starGraph(6);
        NetworkRoleClassifier c = new NetworkRoleClassifier(g);
        c.classify();
        NetworkRoleClassifier.RoleDistribution dist = c.getRoleDistribution();
        int sum = 0;
        for (NetworkRoleClassifier.StructuralRole r : NetworkRoleClassifier.StructuralRole.values()) {
            sum += dist.getCount(r);
        }
        assertEquals(7, dist.getTotalNodes());
        assertEquals(7, sum);
    }

    @Test
    public void distribution_percentagesSumTo100() {
        Graph<String, Edge> g = bridgeGraph();
        NetworkRoleClassifier c = new NetworkRoleClassifier(g);
        c.classify();
        NetworkRoleClassifier.RoleDistribution dist = c.getRoleDistribution();
        double total = 0;
        for (NetworkRoleClassifier.StructuralRole r : NetworkRoleClassifier.StructuralRole.values()) {
            total += dist.getPercentage(r);
        }
        assertEquals(100.0, total, 0.1);
    }

    // ── topByImportance ─────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void topByImportance_zeroThrows() {
        Graph<String, Edge> g = starGraph(3);
        NetworkRoleClassifier c = new NetworkRoleClassifier(g);
        c.classify();
        c.topByImportance(0);
    }

    @Test
    public void topByImportance_starGraph_centerFirst() {
        Graph<String, Edge> g = starGraph(6);
        NetworkRoleClassifier c = new NetworkRoleClassifier(g);
        c.classify();
        List<NetworkRoleClassifier.NodeRole> top = c.topByImportance(3);
        assertEquals("center", top.get(0).getNodeId());
        assertEquals(3, top.size());
    }

    @Test
    public void topByImportance_limitsToN() {
        Graph<String, Edge> g = starGraph(10);
        NetworkRoleClassifier c = new NetworkRoleClassifier(g);
        c.classify();
        assertEquals(2, c.topByImportance(2).size());
    }

    @Test
    public void topByImportance_requestMoreThanExists() {
        Graph<String, Edge> g = starGraph(3);
        NetworkRoleClassifier c = new NetworkRoleClassifier(g);
        c.classify();
        List<NetworkRoleClassifier.NodeRole> top = c.topByImportance(100);
        assertEquals(4, top.size());
    }

    // ── generateReport ──────────────────────────────────────────

    @Test
    public void generateReport_containsHeader() {
        Graph<String, Edge> g = starGraph(3);
        NetworkRoleClassifier c = new NetworkRoleClassifier(g);
        c.classify();
        String report = c.generateReport();
        assertTrue(report.contains("Network Role Classification Report"));
        assertTrue(report.contains("Role Distribution"));
        assertTrue(report.contains("Node Classifications"));
    }

    @Test
    public void generateReport_listsAllNodes() {
        Graph<String, Edge> g = starGraph(4);
        NetworkRoleClassifier c = new NetworkRoleClassifier(g);
        c.classify();
        String report = c.generateReport();
        assertTrue(report.contains("center"));
        for (int i = 1; i <= 4; i++) {
            assertTrue(report.contains("s" + i));
        }
    }

    @Test
    public void generateReport_emptyGraph_noErrors() {
        NetworkRoleClassifier c = new NetworkRoleClassifier(emptyGraph());
        c.classify();
        String report = c.generateReport();
        assertNotNull(report);
        assertTrue(report.contains("TOTAL"));
    }

    // ── Not classified yet ──────────────────────────────────────

    @Test(expected = IllegalStateException.class)
    public void getRole_beforeClassify_throws() {
        Graph<String, Edge> g = emptyGraph();
        g.addVertex("A");
        new NetworkRoleClassifier(g).getRole("A");
    }

    @Test(expected = IllegalStateException.class)
    public void getAllRoles_beforeClassify_throws() {
        new NetworkRoleClassifier(emptyGraph()).getAllRoles();
    }

    @Test(expected = IllegalStateException.class)
    public void getNodesByRole_beforeClassify_throws() {
        new NetworkRoleClassifier(emptyGraph())
                .getNodesByRole(NetworkRoleClassifier.StructuralRole.HUB);
    }

    @Test(expected = IllegalStateException.class)
    public void getRoleDistribution_beforeClassify_throws() {
        new NetworkRoleClassifier(emptyGraph()).getRoleDistribution();
    }

    @Test(expected = IllegalStateException.class)
    public void topByImportance_beforeClassify_throws() {
        new NetworkRoleClassifier(emptyGraph()).topByImportance(5);
    }

    @Test(expected = IllegalStateException.class)
    public void generateReport_beforeClassify_throws() {
        new NetworkRoleClassifier(emptyGraph()).generateReport();
    }

    // ── Re-classify resets results ──────────────────────────────

    @Test
    public void reclassify_resetsRoles() {
        Graph<String, Edge> g = emptyGraph();
        g.addVertex("A");
        NetworkRoleClassifier c = new NetworkRoleClassifier(g);
        c.classify();
        assertEquals(1, c.getAllRoles().size());

        g.addVertex("B");
        c.classify();
        assertEquals(2, c.getAllRoles().size());
    }

    // ── NodeRole toString ───────────────────────────────────────

    @Test
    public void nodeRole_toString_includesAllFields() {
        Graph<String, Edge> g = emptyGraph();
        g.addVertex("solo");
        NetworkRoleClassifier c = new NetworkRoleClassifier(g);
        c.classify();
        String str = c.getRole("solo").toString();
        assertTrue(str.contains("solo"));
        assertTrue(str.contains("Isolate"));
    }

    // ── StructuralRole enum ─────────────────────────────────────

    @Test
    public void structuralRole_hasLabelAndDescription() {
        for (NetworkRoleClassifier.StructuralRole r : NetworkRoleClassifier.StructuralRole.values()) {
            assertNotNull(r.getLabel());
            assertNotNull(r.getDescription());
            assertFalse(r.getLabel().isEmpty());
            assertFalse(r.getDescription().isEmpty());
        }
    }

    // ── Percentile utility ──────────────────────────────────────

    @Test
    public void percentile_emptyCollection_returnsZero() {
        assertEquals(0.0, NetworkRoleClassifier.percentile(Collections.emptyList(), 75), 0.001);
    }

    @Test
    public void percentile_singleValue_returnsThat() {
        assertEquals(42.0,
                NetworkRoleClassifier.percentile(Collections.singletonList(42.0), 50), 0.001);
    }

    @Test
    public void percentile_75th_interpolates() {
        List<Double> vals = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0);
        double p75 = NetworkRoleClassifier.percentile(vals, 75);
        assertEquals(4.0, p75, 0.001);
    }

    @Test
    public void percentile_0th_returnsMin() {
        List<Double> vals = Arrays.asList(10.0, 20.0, 30.0);
        assertEquals(10.0, NetworkRoleClassifier.percentile(vals, 0), 0.001);
    }

    @Test
    public void percentile_100th_returnsMax() {
        List<Double> vals = Arrays.asList(10.0, 20.0, 30.0);
        assertEquals(30.0, NetworkRoleClassifier.percentile(vals, 100), 0.001);
    }

    // ── Metric correctness ──────────────────────────────────────

    @Test
    public void metrics_pairGraph_symmetric() {
        Graph<String, Edge> g = emptyGraph();
        addEdge(g, "A", "B");
        NetworkRoleClassifier c = new NetworkRoleClassifier(g);
        c.classify();

        NetworkRoleClassifier.NodeRole a = c.getRole("A");
        NetworkRoleClassifier.NodeRole b = c.getRole("B");

        assertEquals(a.getDegree(), b.getDegree());
        assertEquals(a.getDegreeCentrality(), b.getDegreeCentrality(), 0.001);
        assertEquals(a.getClusteringCoefficient(), b.getClusteringCoefficient(), 0.001);
        assertEquals(a.getRole(), b.getRole());
    }

    @Test
    public void metrics_triangleGraph_fullClustering() {
        Graph<String, Edge> g = emptyGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        addEdge(g, "A", "C");
        NetworkRoleClassifier c = new NetworkRoleClassifier(g);
        c.classify();

        for (String v : new String[]{"A", "B", "C"}) {
            assertEquals(1.0, c.getRole(v).getClusteringCoefficient(), 0.001);
        }
    }

    @Test
    public void metrics_pathGraph_zeroClustering() {
        Graph<String, Edge> g = emptyGraph();
        addEdge(g, "A", "B");
        addEdge(g, "B", "C");
        NetworkRoleClassifier c = new NetworkRoleClassifier(g);
        c.classify();

        assertEquals(0.0, c.getRole("B").getClusteringCoefficient(), 0.001);
    }

    // ── Mixed topology ──────────────────────────────────────────

    @Test
    public void classify_mixedTopology_multipleRoles() {
        Graph<String, Edge> g = bridgeGraph();
        addEdge(g, "A1", "leaf1");
        addEdge(g, "B1", "leaf2");

        NetworkRoleClassifier c = new NetworkRoleClassifier(g);
        c.classify();

        Set<NetworkRoleClassifier.StructuralRole> roles = new HashSet<>();
        for (NetworkRoleClassifier.NodeRole nr : c.getAllRoles().values()) {
            roles.add(nr.getRole());
        }
        assertTrue("Mixed topology should produce multiple roles", roles.size() >= 2);
    }

    // ── getRole for nonexistent node ────────────────────────────

    @Test
    public void getRole_nonexistentNode_returnsNull() {
        Graph<String, Edge> g = emptyGraph();
        g.addVertex("A");
        NetworkRoleClassifier c = new NetworkRoleClassifier(g);
        c.classify();
        assertNull(c.getRole("does_not_exist"));
    }
}
