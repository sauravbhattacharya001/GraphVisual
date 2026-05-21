package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link GraphPrivacyExposureAuditor}.
 */
public class GraphPrivacyExposureAuditorTest {

    private Graph<String, Edge> star;
    private Graph<String, Edge> k8;
    private Graph<String, Edge> line;
    private Graph<String, Edge> bridge;

    private static Edge e(String u, String v) { return new Edge("c", u, v); }

    @Before
    public void setUp() {
        // Star: hub + 10 leaves
        star = new UndirectedSparseGraph<>();
        star.addVertex("hub");
        for (int i = 0; i < 10; i++) {
            String leaf = "L" + i;
            star.addVertex(leaf);
            star.addEdge(e("hub", leaf), "hub", leaf);
        }

        // K8 complete graph
        k8 = new UndirectedSparseGraph<>();
        for (int i = 0; i < 8; i++) k8.addVertex("K" + i);
        for (int i = 0; i < 8; i++)
            for (int j = i + 1; j < 8; j++)
                k8.addEdge(e("K" + i, "K" + j), "K" + i, "K" + j);

        // Line: 20 nodes in a chain
        line = new UndirectedSparseGraph<>();
        for (int i = 0; i < 20; i++) line.addVertex("N" + i);
        for (int i = 0; i < 19; i++)
            line.addEdge(e("N" + i, "N" + (i + 1)), "N" + i, "N" + (i + 1));

        // Two K4-ish clusters joined by a bridge edge across a single cut vertex
        bridge = new UndirectedSparseGraph<>();
        String[] aSide = {"a1", "a2", "a3", "a4"};
        String[] bSide = {"b1", "b2", "b3", "b4"};
        for (String s : aSide) bridge.addVertex(s);
        for (String s : bSide) bridge.addVertex(s);
        for (int i = 0; i < aSide.length; i++)
            for (int j = i + 1; j < aSide.length; j++)
                bridge.addEdge(e(aSide[i], aSide[j]), aSide[i], aSide[j]);
        for (int i = 0; i < bSide.length; i++)
            for (int j = i + 1; j < bSide.length; j++)
                bridge.addEdge(e(bSide[i], bSide[j]), bSide[i], bSide[j]);
        // Single cut vertex "cut" joins both cliques.
        bridge.addVertex("cut");
        bridge.addEdge(e("cut", "a1"), "cut", "a1");
        bridge.addEdge(e("cut", "b1"), "cut", "b1");
    }

    @Test
    public void starHubFlagsAsCriticalOrHigh() {
        GraphPrivacyExposureAuditor a = new GraphPrivacyExposureAuditor(star)
                .withRiskAppetite(GraphPrivacyExposureAuditor.RiskAppetite.BALANCED);
        GraphPrivacyExposureAuditor.Report r = a.analyze();

        GraphPrivacyExposureAuditor.Finding hub = findByNode(r, "hub");
        assertNotNull("hub missing", hub);
        assertTrue("hub should be UNIQUE_DEGREE", hub.reasons.contains("UNIQUE_DEGREE"));
        assertTrue("hub should be ARTICULATION_POINT", hub.reasons.contains("ARTICULATION_POINT"));
        assertTrue("hub band expected HIGH or CRITICAL but was " + hub.band,
                hub.band == GraphPrivacyExposureAuditor.Band.HIGH
                        || hub.band == GraphPrivacyExposureAuditor.Band.CRITICAL);

        GraphPrivacyExposureAuditor.Finding leaf = findByNode(r, "L0");
        assertNotNull(leaf);
        assertTrue("leaf should not be CRITICAL", leaf.band != GraphPrivacyExposureAuditor.Band.CRITICAL);
    }

    @Test
    public void k8IsCompletelyAnonymousGradeA() {
        GraphPrivacyExposureAuditor a = new GraphPrivacyExposureAuditor(k8);
        GraphPrivacyExposureAuditor.Report r = a.analyze();
        assertEquals("kAnonymity (degree)", 8, r.summary.kAnonymityDegree);
        assertEquals("kAnonymity (fingerprint)", 8, r.summary.kAnonymityFingerprint);
        assertEquals('A', r.grade);
        // Playbook should contain only RELEASE_AS_IS at P3.
        assertEquals(1, r.playbook.size());
        assertEquals(GraphPrivacyExposureAuditor.Priority.P3, r.playbook.get(0).priority);
        assertEquals("RELEASE_AS_IS", r.playbook.get(0).actionCode);
    }

    @Test
    public void lineGraphInteriorsMonitor() {
        GraphPrivacyExposureAuditor a = new GraphPrivacyExposureAuditor(line);
        GraphPrivacyExposureAuditor.Report r = a.analyze();
        // Endpoints share degree 1 (k>=2), interior nodes share degree 2 (k=18).
        assertTrue("kAnonymity(degree) should be >=2 but was " + r.summary.kAnonymityDegree,
                r.summary.kAnonymityDegree >= 2);
        GraphPrivacyExposureAuditor.Finding mid = findByNode(r, "N10");
        assertNotNull(mid);
        // Interior should not be CRITICAL.
        assertTrue("interior should be at most MODERATE but was " + mid.band,
                mid.band != GraphPrivacyExposureAuditor.Band.CRITICAL);
    }

    @Test
    public void sensitiveNodeListBumpsVerdict() {
        // Same K8 graph; without sensitive list every node is SAFE.
        GraphPrivacyExposureAuditor base = new GraphPrivacyExposureAuditor(k8);
        GraphPrivacyExposureAuditor.Report baseR = base.analyze();
        GraphPrivacyExposureAuditor.Finding k0Base = findByNode(baseR, "K0");
        // In K8 every node is structurally identical, so K0 should not be flagged HIGH+.
        assertFalse("K0 should not be HIGH/CRITICAL baseline",
                k0Base.band == GraphPrivacyExposureAuditor.Band.HIGH
                || k0Base.band == GraphPrivacyExposureAuditor.Band.CRITICAL);
        assertFalse("K0 baseline should not be marked sensitive", k0Base.sensitive);

        Set<String> vips = new HashSet<>(Collections.singletonList("K0"));
        GraphPrivacyExposureAuditor with = new GraphPrivacyExposureAuditor(k8)
                .withSensitiveNodes(vips);
        GraphPrivacyExposureAuditor.Report withR = with.analyze();
        GraphPrivacyExposureAuditor.Finding k0With = findByNode(withR, "K0");
        assertTrue("sensitive node score should increase",
                k0With.exposureScore > k0Base.exposureScore);
        assertTrue(k0With.reasons.contains("SENSITIVE_NODE"));
    }

    @Test
    public void articulationPointDetected() {
        GraphPrivacyExposureAuditor a = new GraphPrivacyExposureAuditor(bridge);
        GraphPrivacyExposureAuditor.Report r = a.analyze();
        GraphPrivacyExposureAuditor.Finding cut = findByNode(r, "cut");
        assertNotNull(cut);
        assertTrue("cut should be an articulation point", cut.articulationPoint);
        assertTrue(cut.reasons.contains("ARTICULATION_POINT"));
        // Non-cut vertex.
        GraphPrivacyExposureAuditor.Finding a2 = findByNode(r, "a2");
        assertFalse("a2 should NOT be articulation", a2.articulationPoint);
    }

    @Test
    public void riskAppetiteAffectsFlagCount() {
        int cautious = countAtLeastHigh(new GraphPrivacyExposureAuditor(star)
                .withRiskAppetite(GraphPrivacyExposureAuditor.RiskAppetite.CAUTIOUS).analyze());
        int balanced = countAtLeastHigh(new GraphPrivacyExposureAuditor(star)
                .withRiskAppetite(GraphPrivacyExposureAuditor.RiskAppetite.BALANCED).analyze());
        int aggressive = countAtLeastHigh(new GraphPrivacyExposureAuditor(star)
                .withRiskAppetite(GraphPrivacyExposureAuditor.RiskAppetite.AGGRESSIVE).analyze());
        assertTrue("cautious(" + cautious + ") >= balanced(" + balanced + ")", cautious >= balanced);
        assertTrue("balanced(" + balanced + ") >= aggressive(" + aggressive + ")", balanced >= aggressive);
    }

    @Test
    public void emptyGraphYieldsReleaseAsIs() {
        Graph<String, Edge> empty = new UndirectedSparseGraph<>();
        GraphPrivacyExposureAuditor.Report r = new GraphPrivacyExposureAuditor(empty).analyze();
        assertEquals('A', r.grade);
        assertTrue(r.findings.isEmpty());
        assertEquals(1, r.playbook.size());
        assertEquals("RELEASE_AS_IS", r.playbook.get(0).actionCode);
    }

    @Test
    public void singleNodeGracefullyHandled() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        g.addVertex("only");
        GraphPrivacyExposureAuditor.Report r = new GraphPrivacyExposureAuditor(g).analyze();
        assertEquals(1, r.summary.nodeCount);
        assertEquals(1, r.summary.kAnonymityDegree);
        assertEquals(1, r.findings.size());
    }

    @Test
    public void analyzeDeterministicJson() {
        GraphPrivacyExposureAuditor a = new GraphPrivacyExposureAuditor(bridge).withFixedClock(123456789L);
        String j1 = a.toJson(a.analyze());
        String j2 = a.toJson(a.analyze());
        assertEquals(j1, j2);
    }

    @Test
    public void playbookOrderedByPriority() {
        GraphPrivacyExposureAuditor.Report r = new GraphPrivacyExposureAuditor(star).analyze();
        int prev = -1;
        for (GraphPrivacyExposureAuditor.Recommendation rec : r.playbook) {
            int p = rec.priority.ordinal();
            assertTrue("playbook out of order: " + r.playbook, p >= prev);
            prev = p;
        }
    }

    @Test
    public void topKLimitHonored() {
        GraphPrivacyExposureAuditor a = new GraphPrivacyExposureAuditor(star).withTopK(3);
        GraphPrivacyExposureAuditor.Report r = a.analyze();
        String text = a.render(r);
        // Count finding rows: each row begins with two spaces and node id.
        int rows = 0;
        for (String line : text.split("\n")) {
            if (line.matches("^  [A-Za-z0-9_-]+\\s+score=.*$")) rows++;
        }
        assertTrue("expected at most 3 finding rows but got " + rows + "\n" + text, rows <= 3);
    }

    @Test
    public void markdownHasHeaderAndBoundedRows() {
        GraphPrivacyExposureAuditor a = new GraphPrivacyExposureAuditor(star).withTopK(4);
        String md = a.toMarkdown(a.analyze());
        assertTrue(md.startsWith("# GraphPrivacyExposureAuditor Report"));
        // Count rows in the findings table (lines starting with "| L" or "| h").
        int dataRows = 0;
        for (String line : md.split("\n")) {
            if (line.startsWith("| ") && !line.startsWith("| node") && !line.startsWith("|---")) {
                dataRows++;
            }
        }
        assertTrue("expected at most topK=4 finding rows but got " + dataRows,
                dataRows <= 4);
    }

    @Test
    public void jsonContainsRequiredTopLevelKeys() {
        GraphPrivacyExposureAuditor a = new GraphPrivacyExposureAuditor(bridge);
        String j = a.toJson(a.analyze());
        assertTrue(j.contains("\"findings\""));
        assertTrue(j.contains("\"summary\""));
        assertTrue(j.contains("\"playbook\""));
        assertTrue(j.contains("\"grade\""));
        assertTrue(j.startsWith("{") && j.endsWith("}"));
    }

    // -- helpers -----------------------------------------------------------

    private static GraphPrivacyExposureAuditor.Finding findByNode(
            GraphPrivacyExposureAuditor.Report r, String id) {
        for (GraphPrivacyExposureAuditor.Finding f : r.findings) {
            if (f.nodeId.equals(id)) return f;
        }
        return null;
    }

    private static int countAtLeastHigh(GraphPrivacyExposureAuditor.Report r) {
        int n = 0;
        for (GraphPrivacyExposureAuditor.Finding f : r.findings) {
            if (f.band == GraphPrivacyExposureAuditor.Band.HIGH
                    || f.band == GraphPrivacyExposureAuditor.Band.CRITICAL) n++;
        }
        return n;
    }
}
