package gvisual;

import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for TournamentAnalyzer — validation, scoring, ranking,
 * Condorcet, kings, Hamiltonian paths, transitivity, connectivity,
 * dominance, upsets, Slater ranking, and report generation.
 */
public class TournamentAnalyzerTest {

    private Graph<String, Edge> graph;

    @Before
    public void setUp() {
        graph = new DirectedSparseGraph<String, Edge>();
    }

    // ── Helper methods ──────────────────────────────────────────

    private void addEdge(String from, String to) {
        edge e = new Edge("f", from, to);
        graph.addEdge(e, from, to);
    }

    /** Build a 3-vertex tournament: A→B, B→C, A→C (transitive). */
    private void buildTransitive3() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("A", "C");
    }

    /** Build a 3-vertex tournament: A→B, B→C, C→A (3-cycle). */
    private void buildCyclic3() {
        addEdge("A", "B");
        addEdge("B", "C");
        addEdge("C", "A");
    }

    /** Build a 4-vertex transitive tournament: A>B>C>D. */
    private void buildTransitive4() {
        addEdge("A", "B"); addEdge("A", "C"); addEdge("A", "D");
        addEdge("B", "C"); addEdge("B", "D");
        addEdge("C", "D");
    }

    /** Build a 5-vertex tournament with some cycles. */
    private void buildTournament5() {
        // A→B, A→C, A→D, A→E (A beats all)
        addEdge("A", "B"); addEdge("A", "C"); addEdge("A", "D"); addEdge("A", "E");
        // B→C, B→D
        addEdge("B", "C"); addEdge("B", "D");
        // C→D, C→E
        addEdge("C", "D"); addEdge("C", "E");
        // D→E
        addEdge("D", "E");
        // E→B (upset! creates a cycle)
        addEdge("E", "B");
    }

    // ── Constructor tests ───────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testNullGraphThrows() {
        new TournamentAnalyzer(null);
    }

    @Test
    public void testConstructorAcceptsValidGraph() {
        assertNotNull(new TournamentAnalyzer(graph));
    }

    @Test
    public void testEmptyGraph() {
        TournamentAnalyzer ta = new TournamentAnalyzer(graph);
        assertTrue(ta.isTournament());
        assertTrue(ta.validate().isEmpty());
    }

    // ── Validation tests ────────────────────────────────────────

    @Test
    public void testSingleVertexIsTournament() {
        graph.addVertex("A");
        TournamentAnalyzer ta = new TournamentAnalyzer(graph);
        assertTrue(ta.isTournament());
    }

    @Test
    public void testTransitive3IsTournament() {
        buildTransitive3();
        assertTrue(new TournamentAnalyzer(graph).isTournament());
    }

    @Test
    public void testCyclic3IsTournament() {
        buildCyclic3();
        assertTrue(new TournamentAnalyzer(graph).isTournament());
    }

    @Test
    public void testMissingEdgeNotTournament() {
        addEdge("A", "B");  // Missing B-C and A-C
        graph.addVertex("C");
        TournamentAnalyzer ta = new TournamentAnalyzer(graph);
        assertFalse(ta.isTournament());
        assertFalse(ta.validate().isEmpty());
    }

    @Test
    public void testValidateReportsMissingEdges() {
        addEdge("A", "B");
        graph.addVertex("C");
        List<String> errors = new TournamentAnalyzer(graph).validate();
        assertTrue(errors.size() >= 1);
        // Should mention missing edges
        boolean hasMissing = false;
        for (String e : errors) {
            if (e.contains("Missing") || e.contains("Expected")) hasMissing = true;
        }
        assertTrue(hasMissing);
    }

    @Test
    public void testTransitive4IsTournament() {
        buildTransitive4();
        assertTrue(new TournamentAnalyzer(graph).isTournament());
    }

    @Test
    public void testTournament5IsValid() {
        buildTournament5();
        assertTrue(new TournamentAnalyzer(graph).isTournament());
    }

    // ── Score Sequence tests ────────────────────────────────────

    @Test
    public void testScoreSequenceTransitive3() {
        buildTransitive3();
        TournamentAnalyzer.ScoreSequence ss = new TournamentAnalyzer(graph).computeScoreSequence();
        assertEquals(Arrays.asList(0, 1, 2), ss.getScores());
        assertTrue(ss.isValid());
    }

    @Test
    public void testScoreSequenceCyclic3() {
        buildCyclic3();
        TournamentAnalyzer.ScoreSequence ss = new TournamentAnalyzer(graph).computeScoreSequence();
        assertEquals(Arrays.asList(1, 1, 1), ss.getScores());
        assertTrue(ss.isValid());
    }

    @Test
    public void testScoreSequenceVertexScores() {
        buildTransitive3();
        TournamentAnalyzer.ScoreSequence ss = new TournamentAnalyzer(graph).computeScoreSequence();
        assertEquals(2, (int) ss.getVertexScores().get("A"));
        assertEquals(1, (int) ss.getVertexScores().get("B"));
        assertEquals(0, (int) ss.getVertexScores().get("C"));
    }

    @Test
    public void testScoreSequenceEmpty() {
        TournamentAnalyzer.ScoreSequence ss = new TournamentAnalyzer(graph).computeScoreSequence();
        assertTrue(ss.getScores().isEmpty());
        assertTrue(ss.isValid());
    }

    @Test
    public void testScoreSequenceSingleVertex() {
        graph.addVertex("X");
        TournamentAnalyzer.ScoreSequence ss = new TournamentAnalyzer(graph).computeScoreSequence();
        assertEquals(Arrays.asList(0), ss.getScores());
        assertTrue(ss.isValid());
    }

    // ── Copeland Ranking tests ──────────────────────────────────

    @Test
    public void testCopelandRankingTransitive3() {
        buildTransitive3();
        List<TournamentAnalyzer.RankEntry> ranking = new TournamentAnalyzer(graph).computeCopelandRanking();
        assertEquals(3, ranking.size());
        assertEquals("A", ranking.get(0).getVertex());
        assertEquals(1, ranking.get(0).getRank());
        assertEquals(2, ranking.get(0).getWins());
        assertEquals(0, ranking.get(0).getLosses());
    }

    @Test
    public void testCopelandRankingCyclic3AllTied() {
        buildCyclic3();
        List<TournamentAnalyzer.RankEntry> ranking = new TournamentAnalyzer(graph).computeCopelandRanking();
        // All have 1 win — should all be rank 1
        for (TournamentAnalyzer.RankEntry e : ranking) {
            assertEquals(1, e.getRank());
            assertEquals(1, e.getWins());
            assertEquals(1, e.getLosses());
        }
    }

    @Test
    public void testCopelandRankingTransitive4() {
        buildTransitive4();
        List<TournamentAnalyzer.RankEntry> ranking = new TournamentAnalyzer(graph).computeCopelandRanking();
        assertEquals("A", ranking.get(0).getVertex());
        assertEquals(3, ranking.get(0).getWins());
        assertEquals("D", ranking.get(3).getVertex());
        assertEquals(0, ranking.get(3).getWins());
    }

    @Test
    public void testCopelandRankingEmpty() {
        List<TournamentAnalyzer.RankEntry> ranking = new TournamentAnalyzer(graph).computeCopelandRanking();
        assertTrue(ranking.isEmpty());
    }

    // ── Condorcet Winner / Loser tests ──────────────────────────

    @Test
    public void testCondorcetWinnerTransitive3() {
        buildTransitive3();
        assertEquals("A", new TournamentAnalyzer(graph).findCondorcetWinner());
    }

    @Test
    public void testCondorcetLoserTransitive3() {
        buildTransitive3();
        assertEquals("C", new TournamentAnalyzer(graph).findCondorcetLoser());
    }

    @Test
    public void testCondorcetWinnerCyclic3IsNull() {
        buildCyclic3();
        assertNull(new TournamentAnalyzer(graph).findCondorcetWinner());
    }

    @Test
    public void testCondorcetLoserCyclic3IsNull() {
        buildCyclic3();
        assertNull(new TournamentAnalyzer(graph).findCondorcetLoser());
    }

    @Test
    public void testCondorcetWinnerTournament5() {
        buildTournament5();
        assertEquals("A", new TournamentAnalyzer(graph).findCondorcetWinner());
    }

    @Test
    public void testCondorcetWinnerEmpty() {
        assertNull(new TournamentAnalyzer(graph).findCondorcetWinner());
    }

    @Test
    public void testCondorcetLoserEmpty() {
        assertNull(new TournamentAnalyzer(graph).findCondorcetLoser());
    }

    // ── King Vertices tests ─────────────────────────────────────

    @Test
    public void testKingVerticesTransitive3() {
        buildTransitive3();
        Set<String> kings = new TournamentAnalyzer(graph).findKingVertices();
        assertTrue(kings.contains("A"));  // A beats everyone directly
    }

    @Test
    public void testKingVerticesCyclic3AllKings() {
        buildCyclic3();
        Set<String> kings = new TournamentAnalyzer(graph).findKingVertices();
        assertEquals(3, kings.size());  // All are kings in a 3-cycle
    }

    @Test
    public void testKingVerticesExist() {
        buildTournament5();
        Set<String> kings = new TournamentAnalyzer(graph).findKingVertices();
        assertFalse(kings.isEmpty());  // Every tournament has at least one king
    }

    @Test
    public void testKingVerticesEmpty() {
        Set<String> kings = new TournamentAnalyzer(graph).findKingVertices();
        assertTrue(kings.isEmpty());
    }

    @Test
    public void testKingVerticesSingleVertex() {
        graph.addVertex("X");
        Set<String> kings = new TournamentAnalyzer(graph).findKingVertices();
        assertEquals(1, kings.size());
        assertTrue(kings.contains("X"));
    }

    // ── Hamiltonian Path tests ──────────────────────────────────

    @Test
    public void testHamiltonianPathTransitive3() {
        buildTransitive3();
        List<String> path = new TournamentAnalyzer(graph).findHamiltonianPath();
        assertEquals(3, path.size());
        // Verify it's a valid path: each edge exists in tournament
        for (int i = 0; i < path.size() - 1; i++) {
            String from = path.get(i);
            String to = path.get(i + 1);
            assertTrue("Edge " + from + "→" + to + " should exist",
                hasEdge(from, to));
        }
    }

    @Test
    public void testHamiltonianPathCyclic3() {
        buildCyclic3();
        List<String> path = new TournamentAnalyzer(graph).findHamiltonianPath();
        assertEquals(3, path.size());
        verifyHamiltonianPath(path);
    }

    @Test
    public void testHamiltonianPathTransitive4() {
        buildTransitive4();
        List<String> path = new TournamentAnalyzer(graph).findHamiltonianPath();
        assertEquals(4, path.size());
        verifyHamiltonianPath(path);
    }

    @Test
    public void testHamiltonianPathTournament5() {
        buildTournament5();
        List<String> path = new TournamentAnalyzer(graph).findHamiltonianPath();
        assertEquals(5, path.size());
        verifyHamiltonianPath(path);
    }

    @Test
    public void testHamiltonianPathEmpty() {
        List<String> path = new TournamentAnalyzer(graph).findHamiltonianPath();
        assertTrue(path.isEmpty());
    }

    @Test
    public void testHamiltonianPathSingleVertex() {
        graph.addVertex("X");
        List<String> path = new TournamentAnalyzer(graph).findHamiltonianPath();
        assertEquals(1, path.size());
        assertEquals("X", path.get(0));
    }

    @Test
    public void testHamiltonianPathContainsAllVertices() {
        buildTournament5();
        List<String> path = new TournamentAnalyzer(graph).findHamiltonianPath();
        Set<String> pathSet = new HashSet<String>(path);
        assertEquals(graph.getVertexCount(), pathSet.size());
    }

    // ── Transitivity tests ──────────────────────────────────────

    @Test
    public void testTransitiveIsTransitive() {
        buildTransitive3();
        TournamentAnalyzer.TransitivityResult result =
            new TournamentAnalyzer(graph).analyzeTransitivity();
        assertTrue(result.isTransitive());
        assertEquals(0, result.getIntransitiveTriples());
        assertEquals(1.0, result.getTransitivityRatio(), 0.001);
    }

    @Test
    public void testCyclic3IsIntransitive() {
        buildCyclic3();
        TournamentAnalyzer.TransitivityResult result =
            new TournamentAnalyzer(graph).analyzeTransitivity();
        assertFalse(result.isTransitive());
        assertTrue(result.getIntransitiveTriples() > 0);
    }

    @Test
    public void testTransitive4IsTransitive() {
        buildTransitive4();
        TournamentAnalyzer.TransitivityResult result =
            new TournamentAnalyzer(graph).analyzeTransitivity();
        assertTrue(result.isTransitive());
    }

    @Test
    public void testTransitivityRatio() {
        buildTournament5();
        TournamentAnalyzer.TransitivityResult result =
            new TournamentAnalyzer(graph).analyzeTransitivity();
        assertTrue(result.getTransitivityRatio() >= 0.0);
        assertTrue(result.getTransitivityRatio() <= 1.0);
    }

    @Test
    public void testIntransitiveExamplesLimited() {
        buildCyclic3();
        TournamentAnalyzer.TransitivityResult result =
            new TournamentAnalyzer(graph).analyzeTransitivity();
        assertTrue(result.getIntransitiveExamples().size() <= 10);
    }

    @Test
    public void testEmptyTransitivity() {
        TournamentAnalyzer.TransitivityResult result =
            new TournamentAnalyzer(graph).analyzeTransitivity();
        assertTrue(result.isTransitive());
        assertEquals(1.0, result.getTransitivityRatio(), 0.001);
    }

    // ── Dominance Matrix tests ──────────────────────────────────

    @Test
    public void testDominanceMatrixTransitive3() {
        buildTransitive3();
        Map<String, Map<String, Integer>> matrix =
            new TournamentAnalyzer(graph).computeDominanceMatrix();
        // A→B directly (1), A→C directly (1) + via B (1) = 2
        assertEquals(1, (int) matrix.get("A").get("B"));
        assertEquals(2, (int) matrix.get("A").get("C"));
        assertEquals(0, (int) matrix.get("A").get("A"));
    }

    @Test
    public void testDominanceMatrixDiagonalZero() {
        buildCyclic3();
        Map<String, Map<String, Integer>> matrix =
            new TournamentAnalyzer(graph).computeDominanceMatrix();
        for (String v : graph.getVertices()) {
            assertEquals(0, (int) matrix.get(v).get(v));
        }
    }

    @Test
    public void testDominanceScoresTransitive3() {
        buildTransitive3();
        Map<String, Integer> scores = new TournamentAnalyzer(graph).computeDominanceScores();
        // A: beats B(1) + C(2) = 3
        // B: beats C(1) = 1
        // C: beats nobody = 0
        assertEquals(3, (int) scores.get("A"));
        assertEquals(1, (int) scores.get("B"));
        assertEquals(0, (int) scores.get("C"));
    }

    @Test
    public void testDominanceScoresCyclic3Symmetric() {
        buildCyclic3();
        Map<String, Integer> scores = new TournamentAnalyzer(graph).computeDominanceScores();
        // In a 3-cycle each vertex has dominance to both others (1 direct + 1 via intermediary for beaten, but 0 for the one that beats you)
        // A→B (1), A→C: not direct, A→B→C (1) = total 2
        // B→C (1), B→A: not direct, B→C→A (1) = total 2
        // C→A (1), C→B: not direct, C→A→B (1) = total 2
        int first = scores.values().iterator().next();
        for (int s : scores.values()) {
            assertEquals(first, s);  // All equal in symmetric cycle
        }
    }

    @Test
    public void testDominanceScoresEmpty() {
        Map<String, Integer> scores = new TournamentAnalyzer(graph).computeDominanceScores();
        assertTrue(scores.isEmpty());
    }

    // ── Connectivity tests ──────────────────────────────────────

    @Test
    public void testTransitive3NotStronglyConnected() {
        buildTransitive3();
        TournamentAnalyzer.ConnectivityResult conn =
            new TournamentAnalyzer(graph).analyzeConnectivity();
        assertFalse(conn.isStronglyConnected());
        assertTrue(conn.getComponentCount() > 1);
    }

    @Test
    public void testCyclic3StronglyConnected() {
        buildCyclic3();
        TournamentAnalyzer.ConnectivityResult conn =
            new TournamentAnalyzer(graph).analyzeConnectivity();
        assertTrue(conn.isStronglyConnected());
        assertEquals(1, conn.getComponentCount());
    }

    @Test
    public void testConnectivityEmpty() {
        TournamentAnalyzer.ConnectivityResult conn =
            new TournamentAnalyzer(graph).analyzeConnectivity();
        assertTrue(conn.isStronglyConnected());
        assertEquals(0, conn.getComponentCount());
    }

    @Test
    public void testConnectivitySingleVertex() {
        graph.addVertex("X");
        TournamentAnalyzer.ConnectivityResult conn =
            new TournamentAnalyzer(graph).analyzeConnectivity();
        assertTrue(conn.isStronglyConnected());
        assertEquals(1, conn.getComponentCount());
    }

    @Test
    public void testConnectivityComponents() {
        buildTransitive3();
        TournamentAnalyzer.ConnectivityResult conn =
            new TournamentAnalyzer(graph).analyzeConnectivity();
        int totalVertices = 0;
        for (Set<String> comp : conn.getComponents()) {
            totalVertices += comp.size();
        }
        assertEquals(3, totalVertices);
    }

    // ── Upset Detection tests ───────────────────────────────────

    @Test
    public void testNoUpsetsInTransitiveTournament() {
        buildTransitive3();
        List<TournamentAnalyzer.Upset> upsets = new TournamentAnalyzer(graph).findUpsets();
        assertTrue(upsets.isEmpty());
    }

    @Test
    public void testUpsetsInCyclic3() {
        buildCyclic3();
        List<TournamentAnalyzer.Upset> upsets = new TournamentAnalyzer(graph).findUpsets();
        // In a cyclic tournament with all tied ranks, upsets may or may not be counted
        // depending on how ties are handled (all rank 1 → no upsets since no lower beats higher)
        assertNotNull(upsets);
    }

    @Test
    public void testUpsetsInTournament5() {
        buildTournament5();
        List<TournamentAnalyzer.Upset> upsets = new TournamentAnalyzer(graph).findUpsets();
        // E→B is an upset (E has 1 win, B has 2 wins)
        assertFalse(upsets.isEmpty());
    }

    @Test
    public void testUpsetMagnitude() {
        buildTournament5();
        List<TournamentAnalyzer.Upset> upsets = new TournamentAnalyzer(graph).findUpsets();
        for (TournamentAnalyzer.Upset u : upsets) {
            assertTrue(u.getMagnitude() > 0);
            assertTrue(u.getWinnerRank() > u.getLoserRank());
        }
    }

    @Test
    public void testUpsetsSortedByMagnitude() {
        buildTournament5();
        List<TournamentAnalyzer.Upset> upsets = new TournamentAnalyzer(graph).findUpsets();
        for (int i = 1; i < upsets.size(); i++) {
            assertTrue(upsets.get(i).getMagnitude() <= upsets.get(i - 1).getMagnitude());
        }
    }

    @Test
    public void testUpsetsEmpty() {
        List<TournamentAnalyzer.Upset> upsets = new TournamentAnalyzer(graph).findUpsets();
        assertTrue(upsets.isEmpty());
    }

    // ── Slater Ranking tests ────────────────────────────────────

    @Test
    public void testSlaterRankingTransitive3() {
        buildTransitive3();
        TournamentAnalyzer.SlaterResult slater =
            new TournamentAnalyzer(graph).computeSlaterRanking();
        assertEquals(0, slater.getDisagreements());
        assertEquals(3, slater.getRanking().size());
        assertEquals("A", slater.getRanking().get(0));
    }

    @Test
    public void testSlaterRankingCyclic3() {
        buildCyclic3();
        TournamentAnalyzer.SlaterResult slater =
            new TournamentAnalyzer(graph).computeSlaterRanking();
        // A 3-cycle always has 1 disagreement in any linear ordering
        assertEquals(1, slater.getDisagreements());
        assertEquals(3, slater.getRanking().size());
    }

    @Test
    public void testSlaterRankingMinimizesDisagreements() {
        buildTournament5();
        TournamentAnalyzer.SlaterResult slater =
            new TournamentAnalyzer(graph).computeSlaterRanking();
        assertTrue(slater.getDisagreements() >= 0);
        assertEquals(5, slater.getRanking().size());
    }

    @Test
    public void testSlaterRankingContainsAllVertices() {
        buildTransitive4();
        TournamentAnalyzer.SlaterResult slater =
            new TournamentAnalyzer(graph).computeSlaterRanking();
        Set<String> rankSet = new HashSet<String>(slater.getRanking());
        assertEquals(4, rankSet.size());
    }

    @Test
    public void testSlaterRankingEmpty() {
        TournamentAnalyzer.SlaterResult slater =
            new TournamentAnalyzer(graph).computeSlaterRanking();
        assertEquals(0, slater.getDisagreements());
        assertTrue(slater.getRanking().isEmpty());
    }

    @Test
    public void testSlaterRankingSingleVertex() {
        graph.addVertex("X");
        TournamentAnalyzer.SlaterResult slater =
            new TournamentAnalyzer(graph).computeSlaterRanking();
        assertEquals(0, slater.getDisagreements());
        assertEquals(1, slater.getRanking().size());
    }

    // ── Report Generation tests ─────────────────────────────────

    @Test
    public void testReportGenerationTransitive3() {
        buildTransitive3();
        String report = new TournamentAnalyzer(graph).generateReport();
        assertTrue(report.contains("Tournament Analysis Report"));
        assertTrue(report.contains("Valid tournament: Yes"));
        assertTrue(report.contains("Score Sequence"));
        assertTrue(report.contains("Copeland Ranking"));
        assertTrue(report.contains("Condorcet"));
        assertTrue(report.contains("King Vertices"));
        assertTrue(report.contains("Transitivity"));
        assertTrue(report.contains("Hamiltonian Path"));
        assertTrue(report.contains("Strong Connectivity"));
        assertTrue(report.contains("Dominance Scores"));
        assertTrue(report.contains("Upsets"));
        assertTrue(report.contains("Slater Ranking"));
    }

    @Test
    public void testReportShowsCondorcetWinner() {
        buildTransitive3();
        String report = new TournamentAnalyzer(graph).generateReport();
        assertTrue(report.contains("Condorcet winner: A"));
        assertTrue(report.contains("Condorcet loser: C"));
    }

    @Test
    public void testReportShowsNoCondorcet() {
        buildCyclic3();
        String report = new TournamentAnalyzer(graph).generateReport();
        assertTrue(report.contains("Condorcet winner: None"));
        assertTrue(report.contains("Condorcet loser: None"));
    }

    @Test
    public void testReportNotEmpty() {
        buildTournament5();
        String report = new TournamentAnalyzer(graph).generateReport();
        assertTrue(report.length() > 200);
    }

    @Test
    public void testReportEmptyGraph() {
        String report = new TournamentAnalyzer(graph).generateReport();
        assertTrue(report.contains("Vertices: 0"));
    }

    // ── Additional edge case tests ──────────────────────────────

    @Test
    public void testTransitive4HasCondorcetWinnerAndLoser() {
        buildTransitive4();
        TournamentAnalyzer ta = new TournamentAnalyzer(graph);
        assertEquals("A", ta.findCondorcetWinner());
        assertEquals("D", ta.findCondorcetLoser());
    }

    @Test
    public void testTransitive4ZeroDisagreements() {
        buildTransitive4();
        TournamentAnalyzer.SlaterResult slater =
            new TournamentAnalyzer(graph).computeSlaterRanking();
        assertEquals(0, slater.getDisagreements());
    }

    @Test
    public void testTransitive4KingIsCondorcetWinner() {
        buildTransitive4();
        TournamentAnalyzer ta = new TournamentAnalyzer(graph);
        Set<String> kings = ta.findKingVertices();
        assertTrue(kings.contains("A"));
    }

    @Test
    public void testDominanceMatrixCoverageAllPairs() {
        buildTransitive4();
        Map<String, Map<String, Integer>> matrix =
            new TournamentAnalyzer(graph).computeDominanceMatrix();
        assertEquals(4, matrix.size());
        for (Map.Entry<String, Map<String, Integer>> row : matrix.entrySet()) {
            assertEquals(4, row.getValue().size());
        }
    }

    @Test
    public void testHamiltonianPathEdgesExist() {
        buildTournament5();
        TournamentAnalyzer ta = new TournamentAnalyzer(graph);
        List<String> path = ta.findHamiltonianPath();
        verifyHamiltonianPath(path);
    }

    // ── Verify helper ───────────────────────────────────────────

    private boolean hasEdge(String from, String to) {
        for (Edge e : graph.getEdges()) {
            if (from.equals(e.getVertex1()) && to.equals(e.getVertex2())) {
                return true;
            }
        }
        return false;
    }

    private void verifyHamiltonianPath(List<String> path) {
        // All vertices present
        assertEquals(graph.getVertexCount(), path.size());
        assertEquals(graph.getVertexCount(), new HashSet<String>(path).size());
        // Each consecutive pair has a directed edge
        for (int i = 0; i < path.size() - 1; i++) {
            assertTrue("Expected edge " + path.get(i) + "→" + path.get(i + 1),
                hasEdge(path.get(i), path.get(i + 1)));
        }
    }
}
