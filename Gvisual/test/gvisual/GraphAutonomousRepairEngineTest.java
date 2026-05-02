package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphAutonomousRepairEngine}.
 *
 * @author zalenix
 */
public class GraphAutonomousRepairEngineTest {

    // ── Helper methods ───────────────────────────────────────────────

    private Graph<String, Edge> triangle() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addEdge(new Edge("c", "A", "B"), "A", "B");
        g.addEdge(new Edge("c", "B", "C"), "B", "C");
        g.addEdge(new Edge("c", "A", "C"), "A", "C");
        return g;
    }

    private Graph<String, Edge> path4() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C"); g.addVertex("D");
        g.addEdge(new Edge("c", "A", "B"), "A", "B");
        g.addEdge(new Edge("c", "B", "C"), "B", "C");
        g.addEdge(new Edge("c", "C", "D"), "C", "D");
        return g;
    }

    private Graph<String, Edge> star5() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("Hub");
        for (int i = 1; i <= 4; i++) {
            String leaf = "L" + i;
            g.addVertex(leaf);
            g.addEdge(new Edge("c", "Hub", leaf), "Hub", leaf);
        }
        return g;
    }

    private Graph<String, Edge> completeK5() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        String[] nodes = {"A", "B", "C", "D", "E"};
        for (String n : nodes) g.addVertex(n);
        for (int i = 0; i < nodes.length; i++)
            for (int j = i + 1; j < nodes.length; j++)
                g.addEdge(new Edge("c", nodes[i], nodes[j]), nodes[i], nodes[j]);
        return g;
    }

    private Graph<String, Edge> disconnected() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addVertex("X"); g.addVertex("Y");
        g.addEdge(new Edge("c", "A", "B"), "A", "B");
        g.addEdge(new Edge("c", "B", "C"), "B", "C");
        g.addEdge(new Edge("c", "X", "Y"), "X", "Y");
        return g;
    }

    private Graph<String, Edge> barbell() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        // Left clique: A,B,C
        g.addVertex("A"); g.addVertex("B"); g.addVertex("C");
        g.addEdge(new Edge("c", "A", "B"), "A", "B");
        g.addEdge(new Edge("c", "B", "C"), "B", "C");
        g.addEdge(new Edge("c", "A", "C"), "A", "C");
        // Right clique: D,E,F
        g.addVertex("D"); g.addVertex("E"); g.addVertex("F");
        g.addEdge(new Edge("c", "D", "E"), "D", "E");
        g.addEdge(new Edge("c", "E", "F"), "E", "F");
        g.addEdge(new Edge("c", "D", "F"), "D", "F");
        // Bridge
        g.addEdge(new Edge("c", "C", "D"), "C", "D");
        return g;
    }

    private Graph<String, Edge> singleNode() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A");
        return g;
    }

    private Graph<String, Edge> singleEdge() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("A"); g.addVertex("B");
        g.addEdge(new Edge("c", "A", "B"), "A", "B");
        return g;
    }

    private Graph<String, Edge> empty() {
        return new UndirectedSparseGraph<>();
    }

    private Graph<String, Edge> cycle6() {
        UndirectedSparseGraph<String, Edge> g = new UndirectedSparseGraph<>();
        String[] nodes = {"A", "B", "C", "D", "E", "F"};
        for (String n : nodes) g.addVertex(n);
        for (int i = 0; i < nodes.length; i++)
            g.addEdge(new Edge("c", nodes[i], nodes[(i + 1) % nodes.length]),
                       nodes[i], nodes[(i + 1) % nodes.length]);
        return g;
    }

    private GraphAutonomousRepairEngine engine() {
        return new GraphAutonomousRepairEngine().setRandomSeed(42);
    }

    // ── Empty / Trivial Graphs ──────────────────────────────────────

    @Test
    public void emptyGraph_perfectHealth() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(empty());
        assertEquals(100.0, r.healthScore, 0.01);
        assertTrue(r.vulnerabilities.isEmpty());
        assertTrue(r.repairPlan.isEmpty());
    }

    @Test
    public void singleNode_noVulnerabilities() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(singleNode());
        assertEquals(1, r.nodesAnalyzed);
        assertEquals(0, r.edgesAnalyzed);
        assertTrue(r.vulnerabilities.isEmpty());
    }

    @Test
    public void singleEdge_hasBridge() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(singleEdge());
        assertEquals(2, r.nodesAnalyzed);
        boolean hasBridge = r.vulnerabilities.stream()
                .anyMatch(v -> v.type == GraphAutonomousRepairEngine.VulnerabilityType.BRIDGE);
        assertTrue("Single edge should be a bridge", hasBridge);
    }

    // ── Triangle (Cycle K3) ─────────────────────────────────────────

    @Test
    public void triangle_noBridges() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(triangle());
        long bridgeCount = r.vulnerabilities.stream()
                .filter(v -> v.type == GraphAutonomousRepairEngine.VulnerabilityType.BRIDGE)
                .count();
        assertEquals("Triangle has no bridges", 0, bridgeCount);
    }

    @Test
    public void triangle_noArticulationPoints() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(triangle());
        long apCount = r.vulnerabilities.stream()
                .filter(v -> v.type == GraphAutonomousRepairEngine.VulnerabilityType.ARTICULATION_POINT)
                .count();
        assertEquals("Triangle has no articulation points", 0, apCount);
    }

    @Test
    public void triangle_highHealthScore() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(triangle());
        assertTrue("Triangle should have high health", r.healthScore >= 80);
    }

    // ── Path Graph ──────────────────────────────────────────────────

    @Test
    public void path4_hasBridges() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(path4());
        long bridgeCount = r.vulnerabilities.stream()
                .filter(v -> v.type == GraphAutonomousRepairEngine.VulnerabilityType.BRIDGE)
                .count();
        assertTrue("Path graph should have bridges", bridgeCount >= 2);
    }

    @Test
    public void path4_hasArticulationPoints() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(path4());
        long apCount = r.vulnerabilities.stream()
                .filter(v -> v.type == GraphAutonomousRepairEngine.VulnerabilityType.ARTICULATION_POINT)
                .count();
        assertTrue("Path graph should have articulation points", apCount >= 1);
    }

    @Test
    public void path4_repairPlanNotEmpty() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(path4());
        assertFalse("Path should generate repairs", r.repairPlan.isEmpty());
    }

    @Test
    public void path4_lowerHealthThanTriangle() {
        double pathHealth = engine().analyze(path4()).healthScore;
        double triHealth = engine().analyze(triangle()).healthScore;
        assertTrue("Path should be less healthy than triangle",
                   pathHealth < triHealth);
    }

    // ── Star Graph ──────────────────────────────────────────────────

    @Test
    public void star5_hubIsArticulationPoint() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(star5());
        boolean hubIsAP = r.vulnerabilities.stream()
                .anyMatch(v -> v.type == GraphAutonomousRepairEngine.VulnerabilityType.ARTICULATION_POINT
                        && "Hub".equals(v.node));
        assertTrue("Hub should be an articulation point", hubIsAP);
    }

    @Test
    public void star5_hasBridges() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(star5());
        long bridgeCount = r.vulnerabilities.stream()
                .filter(v -> v.type == GraphAutonomousRepairEngine.VulnerabilityType.BRIDGE)
                .count();
        assertEquals("Star has 4 bridges", 4, bridgeCount);
    }

    // ── Complete K5 ─────────────────────────────────────────────────

    @Test
    public void completeK5_noVulnerabilities() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(completeK5());
        long criticalCount = r.vulnerabilities.stream()
                .filter(v -> v.type == GraphAutonomousRepairEngine.VulnerabilityType.BRIDGE
                        || v.type == GraphAutonomousRepairEngine.VulnerabilityType.ARTICULATION_POINT)
                .count();
        assertEquals("K5 has no bridges or APs", 0, criticalCount);
    }

    @Test
    public void completeK5_highHealthScore() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(completeK5());
        assertTrue("K5 should have excellent health", r.healthScore >= 90);
    }

    @Test
    public void completeK5_noRepairsNeeded() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(completeK5());
        assertTrue("K5 needs no repairs", r.repairPlan.isEmpty());
    }

    // ── Disconnected Graph ──────────────────────────────────────────

    @Test
    public void disconnected_detectsIsolatedComponents() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(disconnected());
        long isoCount = r.vulnerabilities.stream()
                .filter(v -> v.type == GraphAutonomousRepairEngine.VulnerabilityType.ISOLATED_COMPONENT)
                .count();
        assertTrue("Should detect disconnected components", isoCount >= 1);
    }

    @Test
    public void disconnected_repairPlanMergesComponents() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(disconnected());
        boolean hasMerge = r.repairPlan.stream()
                .anyMatch(a -> a.type == GraphAutonomousRepairEngine.RepairActionType.MERGE_COMPONENTS);
        assertTrue("Should propose merging components", hasMerge);
    }

    @Test
    public void disconnected_lowerHealthScore() {
        double health = engine().analyze(disconnected()).healthScore;
        assertTrue("Disconnected graph should have lower health", health < 80);
    }

    // ── Barbell Graph ───────────────────────────────────────────────

    @Test
    public void barbell_detectsBridge() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(barbell());
        boolean hasBridge = r.vulnerabilities.stream()
                .anyMatch(v -> v.type == GraphAutonomousRepairEngine.VulnerabilityType.BRIDGE);
        assertTrue("Barbell should have a bridge", hasBridge);
    }

    @Test
    public void barbell_detectsArticulationPoints() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(barbell());
        boolean hasAP = r.vulnerabilities.stream()
                .anyMatch(v -> v.type == GraphAutonomousRepairEngine.VulnerabilityType.ARTICULATION_POINT);
        assertTrue("Barbell should have articulation points", hasAP);
    }

    @Test
    public void barbell_proposesRepairs() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(barbell());
        assertFalse("Barbell should generate repairs", r.repairPlan.isEmpty());
    }

    // ── Cycle Graph ─────────────────────────────────────────────────

    @Test
    public void cycle6_noBridges() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(cycle6());
        long bridgeCount = r.vulnerabilities.stream()
                .filter(v -> v.type == GraphAutonomousRepairEngine.VulnerabilityType.BRIDGE)
                .count();
        assertEquals("Cycle has no bridges", 0, bridgeCount);
    }

    @Test
    public void cycle6_noArticulationPoints() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(cycle6());
        long apCount = r.vulnerabilities.stream()
                .filter(v -> v.type == GraphAutonomousRepairEngine.VulnerabilityType.ARTICULATION_POINT)
                .count();
        assertEquals("Cycle has no articulation points", 0, apCount);
    }

    // ── Health Score Bounds ─────────────────────────────────────────

    @Test
    public void healthScore_alwaysBetween0And100() {
        Graph<String, Edge>[] graphs = new Graph[]{
            empty(), singleNode(), singleEdge(), triangle(), path4(),
            star5(), completeK5(), disconnected(), barbell(), cycle6()
        };
        for (Graph<String, Edge> g : graphs) {
            double score = engine().analyze(g).healthScore;
            assertTrue("Score should be >= 0: " + score, score >= 0);
            assertTrue("Score should be <= 100: " + score, score <= 100);
        }
    }

    // ── Cost Estimation ─────────────────────────────────────────────

    @Test
    public void costEstimate_notNull() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(path4());
        assertNotNull(r.costEstimate);
    }

    @Test
    public void costEstimate_totalMatchesActions() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(barbell());
        if (!r.repairPlan.isEmpty()) {
            double sumCost = r.repairPlan.stream().mapToDouble(a -> a.cost).sum();
            assertTrue("Total cost should be positive", sumCost > 0);
        }
    }

    @Test
    public void budgetLimit_trimsPlan() {
        GraphAutonomousRepairEngine.RepairReport full = engine().analyze(path4());
        GraphAutonomousRepairEngine.RepairReport capped = engine()
                .setBudgetLimit(0.5).analyze(path4());
        assertTrue("Budget-capped plan should be smaller or equal",
                   capped.repairPlan.size() <= full.repairPlan.size());
    }

    // ── Resilience Comparison ───────────────────────────────────────

    @Test
    public void resilience_notNull() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(triangle());
        assertNotNull(r.resilience);
    }

    @Test
    public void resilience_failureToleranceBounds() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(completeK5());
        assertTrue(r.resilience.failureToleranceBefore >= 0);
        assertTrue(r.resilience.failureToleranceBefore <= 1.0);
        assertTrue(r.resilience.failureToleranceAfter >= 0);
        assertTrue(r.resilience.failureToleranceAfter <= 1.0);
    }

    @Test
    public void resilience_repairImprovesOrMaintains() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(barbell());
        assertTrue("Repairs should not decrease tolerance",
                   r.resilience.failureToleranceAfter >=
                   r.resilience.failureToleranceBefore - 0.05);
    }

    @Test
    public void resilience_disconnected_improvesAfterRepair() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(disconnected());
        // After merging components, connectivity should improve
        assertTrue("After repair, connectivity should improve or stay",
                   r.resilience.connectivityAfter >= r.resilience.connectivityBefore);
    }

    // ── Insights ────────────────────────────────────────────────────

    @Test
    public void insights_notEmpty() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(path4());
        assertFalse("Should generate insights", r.insights.isEmpty());
    }

    @Test
    public void insights_healthyGraph_saysExcellent() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(completeK5());
        boolean hasExcellent = r.insights.stream()
                .anyMatch(i -> i.toLowerCase().contains("excellent"));
        assertTrue("Should mention excellent for healthy graph", hasExcellent);
    }

    // ── Text Output ─────────────────────────────────────────────────

    @Test
    public void toText_containsHeader() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(triangle());
        String text = engine().toText(r);
        assertTrue(text.contains("GRAPH AUTONOMOUS REPAIR ENGINE"));
    }

    @Test
    public void toText_containsHealthScore() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(path4());
        String text = engine().toText(r);
        assertTrue(text.contains("Health Score"));
    }

    @Test
    public void toText_containsVulnerabilities() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(barbell());
        String text = engine().toText(r);
        assertTrue(text.contains("Vulnerabilities"));
    }

    // ── HTML Export ─────────────────────────────────────────────────

    @Test
    public void htmlExport_containsDoctype() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(triangle());
        String html = engine().exportHtml(r);
        assertTrue(html.startsWith("<!DOCTYPE html>"));
    }

    @Test
    public void htmlExport_containsDashboardTitle() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(path4());
        String html = engine().exportHtml(r);
        assertTrue(html.contains("Graph Autonomous Repair Engine"));
    }

    @Test
    public void htmlExport_containsTabs() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(barbell());
        String html = engine().exportHtml(r);
        assertTrue(html.contains("Vulnerabilities"));
        assertTrue(html.contains("Repair Plan"));
        assertTrue(html.contains("Resilience"));
        assertTrue(html.contains("Insights"));
    }

    @Test
    public void htmlExport_toFile() throws IOException {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(cycle6());
        File tmp = File.createTempFile("repair-test-", ".html");
        try {
            engine().exportHtmlToFile(r, tmp);
            assertTrue("HTML file should exist", tmp.exists());
            assertTrue("HTML file should not be empty", tmp.length() > 100);
        } finally {
            tmp.delete();
        }
    }

    // ── Configuration ───────────────────────────────────────────────

    @Test
    public void configMonteCarloTrials() {
        GraphAutonomousRepairEngine e = new GraphAutonomousRepairEngine()
                .setMonteCarloTrials(10).setRandomSeed(42);
        GraphAutonomousRepairEngine.RepairReport r = e.analyze(path4());
        assertNotNull(r.resilience);
    }

    @Test
    public void configFailureFraction() {
        GraphAutonomousRepairEngine e = new GraphAutonomousRepairEngine()
                .setFailureFraction(0.5).setRandomSeed(42);
        GraphAutonomousRepairEngine.RepairReport r = e.analyze(completeK5());
        assertNotNull(r.resilience);
    }

    @Test
    public void configTargetConnectivity() {
        GraphAutonomousRepairEngine e = new GraphAutonomousRepairEngine()
                .setTargetConnectivity(3).setRandomSeed(42);
        GraphAutonomousRepairEngine.RepairReport r = e.analyze(path4());
        assertNotNull(r);
    }

    // ── Repair Action Types ─────────────────────────────────────────

    @Test
    public void repairActions_havePriorities() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(barbell());
        for (GraphAutonomousRepairEngine.RepairAction a : r.repairPlan) {
            assertTrue("Priority should be positive", a.priority > 0);
        }
    }

    @Test
    public void repairActions_haveImpactEstimates() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(path4());
        for (GraphAutonomousRepairEngine.RepairAction a : r.repairPlan) {
            assertTrue("Impact should be 0-1", a.estimatedImpact >= 0 && a.estimatedImpact <= 1);
        }
    }

    @Test
    public void repairActions_haveValidSourceAndTarget() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(disconnected());
        for (GraphAutonomousRepairEngine.RepairAction a : r.repairPlan) {
            assertNotNull("Source should not be null", a.source);
            assertNotNull("Target should not be null", a.target);
            assertFalse("Source should not be empty", a.source.isEmpty());
            assertFalse("Target should not be empty", a.target.isEmpty());
        }
    }

    // ── Severity Classification ─────────────────────────────────────

    @Test
    public void bridges_areCritical() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(singleEdge());
        boolean bridgeIsCritical = r.vulnerabilities.stream()
                .filter(v -> v.type == GraphAutonomousRepairEngine.VulnerabilityType.BRIDGE)
                .allMatch(v -> v.severity == GraphAutonomousRepairEngine.Severity.CRITICAL);
        assertTrue("Bridges should be critical", bridgeIsCritical);
    }

    // ── Node/Edge Counts ────────────────────────────────────────────

    @Test
    public void reportCounts_matchGraph() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(completeK5());
        assertEquals(5, r.nodesAnalyzed);
        assertEquals(10, r.edgesAnalyzed);
    }

    @Test
    public void reportCounts_emptyGraph() {
        GraphAutonomousRepairEngine.RepairReport r = engine().analyze(empty());
        assertEquals(0, r.nodesAnalyzed);
        assertEquals(0, r.edgesAnalyzed);
    }
}
