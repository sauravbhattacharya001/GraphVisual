package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Tests for {@link CommunityEvolutionTracker}.
 *
 * Each test builds a temporal graph with known community structures at
 * different time points, then verifies that the tracker detects the
 * correct evolutionary events.
 */
public class CommunityEvolutionTrackerTest {

    // ── Helper Methods ─────────────────────────────────────────────

    private Edge timedEdge(String type, String v1, String v2, long timestamp) {
        Edge e  new Edge(type, v1, v2);
        e.setTimestamp(timestamp);
        return e;
    }

    private Edge intervalEdge(String type, String v1, String v2, long start, long end) {
        Edge e  new Edge(type, v1, v2);
        e.setTimestamp(start);
        e.setEndTimestamp(end);
        return e;
    }

    private void addEdge(Graph<String, Edge> graph, Edge e) {
        String v1 = e.getVertex1();
        String v2 = e.getVertex2();
        if (!graph.containsVertex(v1)) graph.addVertex(v1);
        if (!graph.containsVertex(v2)) graph.addVertex(v2);
        graph.addEdge(e, v1, v2);
    }

    private long countEvents(List<CommunityEvolutionTracker.EvolutionEvent> events,
                              CommunityEvolutionTracker.EventType type) {
        long count = 0;
        for (CommunityEvolutionTracker.EvolutionEvent e : events) {
            if (e.getType() == type) count++;
        }
        return count;
    }

    // ── Constructor Tests ──────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullGraph_throws() {
        new CommunityEvolutionTracker(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void track_singleWindow_throws() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));
        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        tracker.track(1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void trackAtTimePoints_nullList_throws() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));
        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        tracker.trackAtTimePoints(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void trackAtTimePoints_singlePoint_throws() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));
        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        tracker.trackAtTimePoints(Collections.singletonList(100L));
    }

    // ── Threshold Validation ───────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void setMatchThreshold_negative_throws() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));
        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        tracker.setMatchThreshold(-0.1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setMatchThreshold_overOne_throws() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));
        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        tracker.setMatchThreshold(1.1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setChangeThreshold_negative_throws() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));
        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        tracker.setChangeThreshold(-0.1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setChangeThreshold_overOne_throws() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));
        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        tracker.setChangeThreshold(1.1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setMinCommunitySize_zero_throws() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));
        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        tracker.setMinCommunitySize(0);
    }

    @Test
    public void gettersReturnDefaults() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));
        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        assertEquals(0.3, tracker.getMatchThreshold(), 0.001);
        assertEquals(0.2, tracker.getChangeThreshold(), 0.001);
        assertEquals(2, tracker.getMinCommunitySize());
    }

    // ── Stable Community ───────────────────────────────────────────

    @Test
    public void stableCommunity_sameGroupAcrossTime() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));
        addEdge(g, intervalEdge("f", "B", "C", 100, 200));
        addEdge(g, intervalEdge("f", "A", "C", 100, 200));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L));

        assertEquals(2, result.getSnapshots().size());
        assertTrue("Should be stable or empty events",
            countEvents(result.getEvents(), CommunityEvolutionTracker.EventType.STABLE) > 0
            || result.getEvents().isEmpty());
    }

    // ── Community Birth ────────────────────────────────────────────

    @Test
    public void communityBirth_newGroupAppears() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));
        addEdge(g, intervalEdge("f", "B", "C", 100, 200));
        addEdge(g, timedEdge("f", "X", "Y", 200));
        addEdge(g, timedEdge("f", "Y", "Z", 200));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L));

        assertTrue("Should detect at least one community birth",
            countEvents(result.getEvents(), CommunityEvolutionTracker.EventType.BIRTH) >= 1);
    }

    // ── Community Death ────────────────────────────────────────────

    @Test
    public void communityDeath_groupDisappears() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, timedEdge("f", "A", "B", 100));
        addEdge(g, timedEdge("f", "B", "C", 100));
        addEdge(g, intervalEdge("f", "X", "Y", 100, 200));
        addEdge(g, intervalEdge("f", "Y", "Z", 100, 200));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L));

        assertTrue("Should detect at least one community death",
            countEvents(result.getEvents(), CommunityEvolutionTracker.EventType.DEATH) >= 1);
    }

    // ── Community Growth ───────────────────────────────────────────

    @Test
    public void communityGrowth_membersAdded() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));
        addEdge(g, timedEdge("f", "B", "C", 200));
        addEdge(g, timedEdge("f", "C", "D", 200));
        addEdge(g, timedEdge("f", "D", "E", 200));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L));

        assertTrue("Should detect community growth",
            countEvents(result.getEvents(), CommunityEvolutionTracker.EventType.GROWTH) >= 1);
    }

    // ── Community Contraction ──────────────────────────────────────

    @Test
    public void communityContraction_membersLeave() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        // A-B persists, but C, D, E only at t=100
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));
        addEdge(g, timedEdge("f", "B", "C", 100));
        addEdge(g, timedEdge("f", "C", "D", 100));
        addEdge(g, timedEdge("f", "D", "E", 100));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L));

        assertTrue("Should detect community contraction",
            countEvents(result.getEvents(), CommunityEvolutionTracker.EventType.CONTRACTION) >= 1);
    }

    // ── Community Merge ────────────────────────────────────────────

    @Test
    public void communityMerge_twoBecomesOne() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        // Two separate communities persist, bridge appears at t=200
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));
        addEdge(g, intervalEdge("f", "B", "C", 100, 200));
        addEdge(g, intervalEdge("f", "X", "Y", 100, 200));
        addEdge(g, intervalEdge("f", "Y", "Z", 100, 200));
        // Bridge connecting them at t=200
        addEdge(g, timedEdge("f", "C", "X", 200));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L));

        assertTrue("Should detect community merge",
            countEvents(result.getEvents(), CommunityEvolutionTracker.EventType.MERGE) >= 1);
    }

    // ── Community Split ────────────────────────────────────────────

    @Test
    public void communitySplit_oneBecomesTwo() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        // All connected at t=100 via persistent edges + bridge
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));
        addEdge(g, intervalEdge("f", "B", "C", 100, 200));
        addEdge(g, intervalEdge("f", "X", "Y", 100, 200));
        addEdge(g, intervalEdge("f", "Y", "Z", 100, 200));
        // Bridge only at t=100 — removed at t=200 → split
        addEdge(g, timedEdge("f", "C", "X", 100));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L));

        assertTrue("Should detect community split",
            countEvents(result.getEvents(), CommunityEvolutionTracker.EventType.SPLIT) >= 1);
    }

    // ── Jaccard Similarity ─────────────────────────────────────────

    @Test
    public void jaccard_identicalSets() {
        Set<String> a = new HashSet<>(Arrays.asList("A", "B", "C"));
        assertEquals(1.0, CommunityEvolutionTracker.jaccard(a, a), 0.001);
    }

    @Test
    public void jaccard_disjointSets() {
        Set<String> a = new HashSet<>(Arrays.asList("A", "B"));
        Set<String> b = new HashSet<>(Arrays.asList("C", "D"));
        assertEquals(0.0, CommunityEvolutionTracker.jaccard(a, b), 0.001);
    }

    @Test
    public void jaccard_partialOverlap() {
        Set<String> a = new HashSet<>(Arrays.asList("A", "B", "C"));
        Set<String> b = new HashSet<>(Arrays.asList("B", "C", "D"));
        assertEquals(0.5, CommunityEvolutionTracker.jaccard(a, b), 0.001);
    }

    @Test
    public void jaccard_emptyBothSets() {
        assertEquals(0.0, CommunityEvolutionTracker.jaccard(
            new HashSet<>(), new HashSet<>()), 0.001);
    }

    @Test
    public void jaccard_oneEmpty() {
        Set<String> a = new HashSet<>(Arrays.asList("A", "B"));
        assertEquals(0.0, CommunityEvolutionTracker.jaccard(a, new HashSet<>()), 0.001);
    }

    // ── Node Lineage ───────────────────────────────────────────────

    @Test
    public void nodeLineage_tracksNodeAcrossSnapshots() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 300));
        addEdge(g, intervalEdge("f", "B", "C", 100, 300));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L, 300L));

        List<CommunityEvolutionTracker.NodeLineageEntry> lineage =
            result.getNodeLineage("A");
        assertTrue("Node A should appear in at least 2 snapshots",
            lineage.size() >= 2);
        for (CommunityEvolutionTracker.NodeLineageEntry entry : lineage) {
            assertTrue(entry.getCommunity().getMembers().contains("A"));
        }
    }

    @Test
    public void nodeLineage_missingNode_emptyList() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L));

        List<CommunityEvolutionTracker.NodeLineageEntry> lineage =
            result.getNodeLineage("NONEXISTENT");
        assertTrue(lineage.isEmpty());
    }

    // ── Stability & Volatility Scores ──────────────────────────────

    @Test
    public void stabilityScore_inRange() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));
        addEdge(g, intervalEdge("f", "B", "C", 100, 200));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L));

        double score = result.getStabilityScore();
        assertTrue("Stability should be in [0, 1]", score >= 0.0 && score <= 1.0);
    }

    @Test
    public void volatilityScore_inRange() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, timedEdge("f", "A", "B", 100));
        addEdge(g, timedEdge("f", "B", "C", 100));
        addEdge(g, timedEdge("f", "X", "Y", 200));
        addEdge(g, timedEdge("f", "Y", "Z", 200));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L));

        double vol = result.getVolatilityScore();
        assertTrue("Volatility should be in [0, 1]", vol >= 0.0 && vol <= 1.0);
    }

    // ── Event Filtering ────────────────────────────────────────────

    @Test
    public void getEventsByType_filtersCorrectly() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, timedEdge("f", "A", "B", 100));
        addEdge(g, timedEdge("f", "B", "C", 100));
        addEdge(g, timedEdge("f", "X", "Y", 200));
        addEdge(g, timedEdge("f", "Y", "Z", 200));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L));

        for (CommunityEvolutionTracker.EventType type : CommunityEvolutionTracker.EventType.values()) {
            List<CommunityEvolutionTracker.EvolutionEvent> filtered =
                result.getEventsByType(type);
            for (CommunityEvolutionTracker.EvolutionEvent event : filtered) {
                assertEquals(type, event.getType());
            }
        }
    }

    @Test
    public void getEventsAtTransition_filtersCorrectly() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 300));
        addEdge(g, intervalEdge("f", "B", "C", 100, 300));
        addEdge(g, timedEdge("f", "X", "Y", 200));
        addEdge(g, timedEdge("f", "Y", "Z", 200));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L, 300L));

        List<CommunityEvolutionTracker.EvolutionEvent> t1Events =
            result.getEventsAtTransition(1);
        for (CommunityEvolutionTracker.EvolutionEvent event : t1Events) {
            assertEquals(1, event.getTransitionIndex());
        }
    }

    // ── Event Counts ───────────────────────────────────────────────

    @Test
    public void eventCounts_sumsToTotalEvents() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, timedEdge("f", "A", "B", 100));
        addEdge(g, timedEdge("f", "B", "C", 100));
        addEdge(g, timedEdge("f", "X", "Y", 200));
        addEdge(g, timedEdge("f", "Y", "Z", 200));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L));

        Map<CommunityEvolutionTracker.EventType, Long> counts = result.getEventCounts();
        assertNotNull(counts);
        long totalFromCounts = 0;
        for (Long v : counts.values()) totalFromCounts += v;
        assertEquals(result.getEvents().size(), totalFromCounts);
    }

    // ── Summary ────────────────────────────────────────────────────

    @Test
    public void summary_containsKeyInfo() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));
        addEdge(g, intervalEdge("f", "B", "C", 100, 200));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L));

        String summary = result.getSummary();
        assertTrue(summary.contains("Community Evolution Summary"));
        assertTrue(summary.contains("Snapshots:"));
        assertTrue(summary.contains("Stability score:"));
        assertTrue(summary.contains("Volatility score:"));
    }

    // ── Snapshot Properties ────────────────────────────────────────

    @Test
    public void snapshot_vertexAndEdgeCounts() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));
        addEdge(g, intervalEdge("f", "B", "C", 100, 200));
        addEdge(g, intervalEdge("f", "C", "A", 100, 200));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L));

        CommunityEvolutionTracker.CommunitySnapshot snap = result.getSnapshots().get(0);
        assertEquals(3, snap.getVertexCount());
        assertEquals(3, snap.getEdgeCount());
        assertTrue(snap.getCommunityCount() >= 1);
    }

    @Test
    public void snapshot_averageCommunitySize() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));
        addEdge(g, intervalEdge("f", "B", "C", 100, 200));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L));

        CommunityEvolutionTracker.CommunitySnapshot snap = result.getSnapshots().get(0);
        assertTrue(snap.getAverageCommunitySize() > 0);
        assertTrue(snap.getLargestCommunitySize() >= 2);
    }

    @Test
    public void snapshot_emptyGraph_producesEvents() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, timedEdge("f", "A", "B", 100));
        addEdge(g, timedEdge("f", "X", "Y", 300));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 300L));

        assertFalse(result.getEvents().isEmpty());
    }

    // ── Migration Analysis ─────────────────────────────────────────

    @Test
    public void topMigrants_returnsLimitedResults() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 300));
        addEdge(g, intervalEdge("f", "B", "C", 100, 300));
        addEdge(g, timedEdge("f", "X", "Y", 100));
        addEdge(g, timedEdge("f", "Y", "Z", 200));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L, 300L));

        List<Map.Entry<String, Integer>> topMigrants = result.getTopMigrants(3);
        assertTrue(topMigrants.size() <= 3);
    }

    // ── MinCommunitySize Filter ────────────────────────────────────

    @Test
    public void minCommunitySize_filtersSingletons() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, timedEdge("f", "A", "B", 100));
        addEdge(g, timedEdge("f", "X", "Y", 200));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        tracker.setMinCommunitySize(2);
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L));

        for (CommunityEvolutionTracker.CommunitySnapshot snap : result.getSnapshots()) {
            for (CommunityDetector.Community comm : snap.getCommunities()) {
                assertTrue("Community size should be >= minCommunitySize",
                    comm.getSize() >= 2);
            }
        }
    }

    @Test
    public void minCommunitySize_one_includesSingletons() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        tracker.setMinCommunitySize(1);
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L));

        assertNotNull(result);
        assertFalse(result.getSnapshots().isEmpty());
    }

    // ── Track with Windows ─────────────────────────────────────────

    @Test
    public void track_withWindows_producesSnapshots() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 400));
        addEdge(g, intervalEdge("f", "B", "C", 100, 400));
        addEdge(g, timedEdge("f", "X", "Y", 300));
        addEdge(g, timedEdge("f", "Y", "Z", 300));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result = tracker.track(3);

        assertEquals(3, result.getSnapshots().size());
        assertFalse(result.getEvents().isEmpty());
    }

    // ── Event toString ─────────────────────────────────────────────

    @Test
    public void eventToString_containsType() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, timedEdge("f", "A", "B", 100));
        addEdge(g, timedEdge("f", "B", "C", 100));
        addEdge(g, timedEdge("f", "X", "Y", 200));
        addEdge(g, timedEdge("f", "Y", "Z", 200));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L));

        for (CommunityEvolutionTracker.EvolutionEvent event : result.getEvents()) {
            String str = event.toString();
            assertTrue("toString should contain event type",
                str.contains(event.getType().name()));
        }
    }

    // ── NodeLineageEntry toString ──────────────────────────────────

    @Test
    public void nodeLineageEntry_toString() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));
        addEdge(g, intervalEdge("f", "B", "C", 100, 200));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L));

        List<CommunityEvolutionTracker.NodeLineageEntry> lineage =
            result.getNodeLineage("A");
        if (!lineage.isEmpty()) {
            String str = lineage.get(0).toString();
            assertTrue(str.contains("t="));
            assertTrue(str.contains("community"));
        }
    }

    // ── Snapshot toString ──────────────────────────────────────────

    @Test
    public void snapshotToString_containsCounts() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));
        addEdge(g, intervalEdge("f", "B", "C", 100, 200));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L));

        String str = result.getSnapshots().get(0).toString();
        assertTrue(str.contains("communities"));
        assertTrue(str.contains("vertices"));
        assertTrue(str.contains("edges"));
    }

    // ── Complex Scenario: Multi-phase Evolution ────────────────────

    @Test
    public void complexScenario_multiPhaseEvolution() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();

        // Persistent edges within sub-groups
        addEdge(g, intervalEdge("f", "Alice", "Bob", 100, 300));
        addEdge(g, intervalEdge("f", "Bob", "Carol", 100, 300));
        addEdge(g, intervalEdge("f", "Alice", "Carol", 100, 300));
        addEdge(g, intervalEdge("f", "Dave", "Eve", 100, 300));
        addEdge(g, intervalEdge("f", "Eve", "Frank", 100, 300));
        addEdge(g, intervalEdge("f", "Dave", "Frank", 100, 300));

        // Bridge only at t=200 (merge then split)
        addEdge(g, timedEdge("f", "Carol", "Dave", 200));
        // New member at t=300
        addEdge(g, timedEdge("f", "Eve", "Gina", 300));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L, 300L));

        assertEquals(3, result.getSnapshots().size());

        List<CommunityEvolutionTracker.EvolutionEvent> t1Events =
            result.getEventsAtTransition(1);
        assertFalse("Should detect events in transition 1", t1Events.isEmpty());

        List<CommunityEvolutionTracker.EvolutionEvent> t2Events =
            result.getEventsAtTransition(2);
        assertFalse("Should detect events in transition 2", t2Events.isEmpty());

        assertTrue("Complex scenario should produce multiple events",
            result.getEvents().size() >= 2);

        String summary = result.getSummary();
        assertFalse(summary.isEmpty());
    }

    // ── Custom Threshold Tests ─────────────────────────────────────

    @Test
    public void highMatchThreshold_runs() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));
        addEdge(g, intervalEdge("f", "B", "C", 100, 200));
        addEdge(g, timedEdge("f", "C", "D", 100));
        addEdge(g, timedEdge("f", "C", "E", 200));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        tracker.setMatchThreshold(0.9);
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L));

        assertNotNull(result);
    }

    @Test
    public void lowChangeThreshold_runs() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));
        addEdge(g, intervalEdge("f", "B", "C", 100, 200));
        addEdge(g, timedEdge("f", "C", "D", 200));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        tracker.setChangeThreshold(0.05);
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L));

        assertNotNull(result);
    }

    // ── Event Properties ───────────────────────────────────────────

    @Test
    public void eventProperties_accessible() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, timedEdge("f", "A", "B", 100));
        addEdge(g, timedEdge("f", "B", "C", 100));
        addEdge(g, timedEdge("f", "X", "Y", 200));
        addEdge(g, timedEdge("f", "Y", "Z", 200));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L));

        for (CommunityEvolutionTracker.EvolutionEvent event : result.getEvents()) {
            assertNotNull(event.getType());
            assertNotNull(event.getPrimaryMembers());
            assertNotNull(event.getRelatedGroups());
            assertNotNull(event.getDescription());
            assertTrue(event.getFromTimestamp() <= event.getToTimestamp());
            assertEquals(1, event.getTransitionIndex());
        }
    }

    // ── Snapshot totalMembers ──────────────────────────────────────

    @Test
    public void snapshot_totalMembers() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));
        addEdge(g, intervalEdge("f", "B", "C", 100, 200));
        addEdge(g, intervalEdge("f", "X", "Y", 100, 200));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L));

        CommunityEvolutionTracker.CommunitySnapshot snap = result.getSnapshots().get(0);
        assertTrue(snap.getTotalMembers() >= 2);
    }

    // ── NodeLineageEntry properties ────────────────────────────────

    @Test
    public void nodeLineageEntry_properties() {
        Graph<String, Edge> g = new UndirectedSparseGraph<>();
        addEdge(g, intervalEdge("f", "A", "B", 100, 200));
        addEdge(g, intervalEdge("f", "B", "C", 100, 200));

        CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(new TemporalGraph(g));
        CommunityEvolutionTracker.EvolutionResult result =
            tracker.trackAtTimePoints(Arrays.asList(100L, 200L));

        List<CommunityEvolutionTracker.NodeLineageEntry> lineage =
            result.getNodeLineage("B");
        assertFalse(lineage.isEmpty());
        CommunityEvolutionTracker.NodeLineageEntry entry = lineage.get(0);
        assertEquals(100L, entry.getTimestamp());
        assertNotNull(entry.getCommunity());
    }
}
