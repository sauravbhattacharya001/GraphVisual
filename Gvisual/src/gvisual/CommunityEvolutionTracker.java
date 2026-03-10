package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tracks how communities evolve across temporal graph snapshots.
 *
 * <p>Given a {@link TemporalGraph}, this analyzer detects communities at each
 * time window and identifies <strong>evolutionary events</strong> between
 * consecutive snapshots:</p>
 *
 * <ul>
 *   <li><b>Birth</b> — a new community appears with no significant predecessor</li>
 *   <li><b>Death</b> — a community disappears with no significant successor</li>
 *   <li><b>Growth</b> — a community gains members (&ge; 20% increase)</li>
 *   <li><b>Contraction</b> — a community loses members (&ge; 20% decrease)</li>
 *   <li><b>Merge</b> — two or more communities combine into one</li>
 *   <li><b>Split</b> — one community divides into two or more</li>
 *   <li><b>Stable</b> — a community persists with minimal membership change</li>
 * </ul>
 *
 * <p>Community matching between snapshots uses <strong>Jaccard similarity</strong>
 * on member sets. A match is established when the Jaccard coefficient exceeds
 * a configurable threshold (default 0.3). This handles the fact that community
 * IDs are arbitrary across snapshots — matching is purely by member overlap.</p>
 *
 * <h3>Usage:</h3>
 * <pre>
 *   TemporalGraph tg = new TemporalGraph(graph);
 *   CommunityEvolutionTracker tracker = new CommunityEvolutionTracker(tg);
 *
 *   // Track evolution across 5 time windows
 *   EvolutionResult result = tracker.track(5);
 *
 *   // Print all events
 *   for (EvolutionEvent event : result.getEvents()) {
 *       System.out.println(event);
 *   }
 *
 *   // Get summary statistics
 *   System.out.println(result.getSummary());
 *
 *   // Get community lineage for a specific node
 *   List&lt;CommunitySnapshot&gt; lineage = result.getNodeLineage("Alice");
 * </pre>
 *
 * @author zalenix
 */
public class CommunityEvolutionTracker {

    /** Types of evolutionary events between community snapshots. */
    public enum EventType {
        BIRTH, DEATH, GROWTH, CONTRACTION, MERGE, SPLIT, STABLE
    }

    /**
     * Minimum Jaccard similarity to consider two communities as "the same"
     * across time windows. Default 0.3 balances precision vs. recall.
     */
    private double matchThreshold = 0.3;

    /**
     * Minimum fractional change in size to count as growth/contraction.
     * Default 0.2 (20% change).
     */
    private double changeThreshold = 0.2;

    /**
     * Minimum community size to track. Singleton communities are noisy
     * and produce many spurious birth/death events.
     */
    private int minCommunitySize = 2;

    private final TemporalGraph temporalGraph;

    /**
     * Creates a tracker for the given temporal graph.
     *
     * @param temporalGraph the temporal graph to analyze
     * @throws IllegalArgumentException if temporalGraph is null
     */
    public CommunityEvolutionTracker(TemporalGraph temporalGraph) {
        if (temporalGraph == null) {
            throw new IllegalArgumentException("TemporalGraph must not be null");
        }
        this.temporalGraph = temporalGraph;
    }

    /** Sets the Jaccard similarity threshold for matching communities. */
    public void setMatchThreshold(double threshold) {
        if (threshold < 0 || threshold > 1) {
            throw new IllegalArgumentException("Match threshold must be in [0, 1]: " + threshold);
        }
        this.matchThreshold = threshold;
    }

    /** Sets the fractional change threshold for growth/contraction events. */
    public void setChangeThreshold(double threshold) {
        if (threshold < 0 || threshold > 1) {
            throw new IllegalArgumentException("Change threshold must be in [0, 1]: " + threshold);
        }
        this.changeThreshold = threshold;
    }

    /** Sets the minimum community size to track. */
    public void setMinCommunitySize(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("Minimum community size must be >= 1: " + size);
        }
        this.minCommunitySize = size;
    }

    public double getMatchThreshold() { return matchThreshold; }
    public double getChangeThreshold() { return changeThreshold; }
    public int getMinCommunitySize() { return minCommunitySize; }

    // ── Core Tracking ──────────────────────────────────────────────

    /**
     * Tracks community evolution across a number of equal-width time windows.
     *
     * @param windowCount number of time windows to divide the timeline into
     * @return evolution result with snapshots, events, and analytics
     * @throws IllegalArgumentException if windowCount &lt; 2
     * @throws IllegalStateException if the graph has no timestamped edges
     */
    public EvolutionResult track(int windowCount) {
        if (windowCount < 2) {
            throw new IllegalArgumentException("Need at least 2 windows to track evolution");
        }

        List<Map.Entry<Long, Graph<String, edge>>> windows =
            temporalGraph.generateWindows(windowCount);

        List<CommunitySnapshot> snapshots = new ArrayList<>();
        List<EvolutionEvent> events = new ArrayList<>();

        // Detect communities at each window
        for (Map.Entry<Long, Graph<String, edge>> entry : windows) {
            long timestamp = entry.getKey();
            Graph<String, edge> windowGraph = entry.getValue();
            CommunityDetector detector = new CommunityDetector(windowGraph);
            CommunityDetector.DetectionResult detection = detector.detect();

            // Filter to significant communities
            List<CommunityDetector.Community> significant =
                detection.getSignificantCommunities(minCommunitySize);

            snapshots.add(new CommunitySnapshot(timestamp, significant, windowGraph));
        }

        // Compare consecutive snapshots to detect events
        for (int i = 1; i < snapshots.size(); i++) {
            CommunitySnapshot prev = snapshots.get(i - 1);
            CommunitySnapshot curr = snapshots.get(i);
            events.addAll(detectEvents(prev, curr, i));
        }

        return new EvolutionResult(snapshots, events, matchThreshold);
    }

    /**
     * Tracks community evolution using explicit time points (e.g., from
     * {@link TemporalGraph#getTimePoints()}).
     *
     * @param timePoints sorted list of time points to snapshot at
     * @return evolution result
     * @throws IllegalArgumentException if fewer than 2 time points
     */
    public EvolutionResult trackAtTimePoints(List<Long> timePoints) {
        if (timePoints == null || timePoints.size() < 2) {
            throw new IllegalArgumentException("Need at least 2 time points");
        }

        List<CommunitySnapshot> snapshots = new ArrayList<>();
        List<EvolutionEvent> events = new ArrayList<>();

        for (long time : timePoints) {
            Graph<String, edge> snapshot = temporalGraph.snapshotAt(time);
            CommunityDetector detector = new CommunityDetector(snapshot);
            CommunityDetector.DetectionResult detection = detector.detect();

            List<CommunityDetector.Community> significant =
                detection.getSignificantCommunities(minCommunitySize);

            snapshots.add(new CommunitySnapshot(time, significant, snapshot));
        }

        for (int i = 1; i < snapshots.size(); i++) {
            CommunitySnapshot prev = snapshots.get(i - 1);
            CommunitySnapshot curr = snapshots.get(i);
            events.addAll(detectEvents(prev, curr, i));
        }

        return new EvolutionResult(snapshots, events, matchThreshold);
    }

    // ── Event Detection ────────────────────────────────────────────

    /**
     * Detects evolutionary events between two consecutive community snapshots.
     */
    private List<EvolutionEvent> detectEvents(CommunitySnapshot prev,
                                               CommunitySnapshot curr,
                                               int transitionIndex) {
        List<EvolutionEvent> events = new ArrayList<>();

        List<CommunityDetector.Community> prevComms = prev.getCommunities();
        List<CommunityDetector.Community> currComms = curr.getCommunities();

        // Build Jaccard similarity matrix
        double[][] similarity = new double[prevComms.size()][currComms.size()];
        for (int p = 0; p < prevComms.size(); p++) {
            for (int c = 0; c < currComms.size(); c++) {
                similarity[p][c] = jaccard(
                    prevComms.get(p).getMembers(),
                    currComms.get(c).getMembers());
            }
        }

        // Forward matching: for each prev community, find best curr match(es)
        Map<Integer, List<Integer>> prevToCurr = new HashMap<>();
        for (int p = 0; p < prevComms.size(); p++) {
            List<Integer> matches = new ArrayList<>();
            for (int c = 0; c < currComms.size(); c++) {
                if (similarity[p][c] >= matchThreshold) {
                    matches.add(c);
                }
            }
            prevToCurr.put(p, matches);
        }

        // Reverse matching: for each curr community, find best prev match(es)
        Map<Integer, List<Integer>> currToPrev = new HashMap<>();
        for (int c = 0; c < currComms.size(); c++) {
            List<Integer> matches = new ArrayList<>();
            for (int p = 0; p < prevComms.size(); p++) {
                if (similarity[p][c] >= matchThreshold) {
                    matches.add(p);
                }
            }
            currToPrev.put(c, matches);
        }

        Set<Integer> handledPrev = new HashSet<>();
        Set<Integer> handledCurr = new HashSet<>();

        // Detect SPLIT: one prev → multiple curr
        for (int p = 0; p < prevComms.size(); p++) {
            List<Integer> matches = prevToCurr.get(p);
            if (matches.size() >= 2) {
                handledPrev.add(p);
                for (int c : matches) handledCurr.add(c);

                Set<String> prevMembers = prevComms.get(p).getMembers();
                List<Set<String>> fragments = matches.stream()
                    .map(c -> currComms.get(c).getMembers())
                    .collect(Collectors.toList());

                events.add(new EvolutionEvent(
                    EventType.SPLIT, transitionIndex,
                    prev.getTimestamp(), curr.getTimestamp(),
                    prevMembers, fragments,
                    String.format("Community (size %d) split into %d fragments",
                        prevMembers.size(), fragments.size())));
            }
        }

        // Detect MERGE: multiple prev → one curr
        for (int c = 0; c < currComms.size(); c++) {
            if (handledCurr.contains(c)) continue;
            List<Integer> matches = currToPrev.get(c);
            // Only count matches that haven't been handled by SPLIT
            List<Integer> unhandled = matches.stream()
                .filter(p -> !handledPrev.contains(p))
                .collect(Collectors.toList());
            if (unhandled.size() >= 2) {
                for (int p : unhandled) handledPrev.add(p);
                handledCurr.add(c);

                Set<String> merged = currComms.get(c).getMembers();
                List<Set<String>> sources = unhandled.stream()
                    .map(p -> prevComms.get(p).getMembers())
                    .collect(Collectors.toList());

                events.add(new EvolutionEvent(
                    EventType.MERGE, transitionIndex,
                    prev.getTimestamp(), curr.getTimestamp(),
                    merged, sources,
                    String.format("%d communities merged into one (size %d)",
                        sources.size(), merged.size())));
            }
        }

        // Detect GROWTH / CONTRACTION / STABLE: one-to-one matches
        for (int p = 0; p < prevComms.size(); p++) {
            if (handledPrev.contains(p)) continue;
            List<Integer> matches = prevToCurr.get(p);
            if (matches.isEmpty()) continue;

            // Find best match
            int bestC = matches.get(0);
            double bestSim = similarity[p][bestC];
            for (int c : matches) {
                if (similarity[p][c] > bestSim) {
                    bestSim = similarity[p][c];
                    bestC = c;
                }
            }

            if (handledCurr.contains(bestC)) continue;

            handledPrev.add(p);
            handledCurr.add(bestC);

            Set<String> prevMembers = prevComms.get(p).getMembers();
            Set<String> currMembers = currComms.get(bestC).getMembers();
            double sizeChange = (double)(currMembers.size() - prevMembers.size())
                                / prevMembers.size();

            EventType type;
            String desc;
            if (sizeChange >= changeThreshold) {
                type = EventType.GROWTH;
                desc = String.format("Community grew from %d to %d members (+%.0f%%)",
                    prevMembers.size(), currMembers.size(), sizeChange * 100);
            } else if (sizeChange <= -changeThreshold) {
                type = EventType.CONTRACTION;
                desc = String.format("Community shrank from %d to %d members (%.0f%%)",
                    prevMembers.size(), currMembers.size(), sizeChange * 100);
            } else {
                type = EventType.STABLE;
                desc = String.format("Community stable at ~%d members (Jaccard=%.2f)",
                    currMembers.size(), bestSim);
            }

            events.add(new EvolutionEvent(
                type, transitionIndex,
                prev.getTimestamp(), curr.getTimestamp(),
                currMembers, Collections.singletonList(prevMembers), desc));
        }

        // Detect DEATH: prev communities with no match
        for (int p = 0; p < prevComms.size(); p++) {
            if (!handledPrev.contains(p)) {
                Set<String> members = prevComms.get(p).getMembers();
                events.add(new EvolutionEvent(
                    EventType.DEATH, transitionIndex,
                    prev.getTimestamp(), curr.getTimestamp(),
                    members, Collections.emptyList(),
                    String.format("Community (size %d) dissolved", members.size())));
            }
        }

        // Detect BIRTH: curr communities with no match
        for (int c = 0; c < currComms.size(); c++) {
            if (!handledCurr.contains(c)) {
                Set<String> members = currComms.get(c).getMembers();
                events.add(new EvolutionEvent(
                    EventType.BIRTH, transitionIndex,
                    prev.getTimestamp(), curr.getTimestamp(),
                    members, Collections.emptyList(),
                    String.format("New community formed (size %d)", members.size())));
            }
        }

        return events;
    }

    // ── Jaccard Similarity ─────────────────────────────────────────

    /**
     * Computes the Jaccard similarity coefficient between two sets.
     *
     * @return |A ∩ B| / |A ∪ B|, or 0 if both sets are empty
     */
    static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    // ── Inner Classes ──────────────────────────────────────────────

    /**
     * A snapshot of community structure at a single point in time.
     */
    public static class CommunitySnapshot {
        private final long timestamp;
        private final List<CommunityDetector.Community> communities;
        private final int vertexCount;
        private final int edgeCount;

        public CommunitySnapshot(long timestamp,
                                  List<CommunityDetector.Community> communities,
                                  Graph<String, edge> graph) {
            this.timestamp = timestamp;
            this.communities = Collections.unmodifiableList(new ArrayList<>(communities));
            this.vertexCount = graph.getVertexCount();
            this.edgeCount = graph.getEdgeCount();
        }

        public long getTimestamp() { return timestamp; }
        public List<CommunityDetector.Community> getCommunities() { return communities; }
        public int getCommunityCount() { return communities.size(); }
        public int getVertexCount() { return vertexCount; }
        public int getEdgeCount() { return edgeCount; }

        /** Total members across all tracked communities. */
        public int getTotalMembers() {
            return communities.stream().mapToInt(CommunityDetector.Community::getSize).sum();
        }

        /** Average community size. */
        public double getAverageCommunitySize() {
            if (communities.isEmpty()) return 0;
            return (double) getTotalMembers() / communities.size();
        }

        /** Size of the largest community. */
        public int getLargestCommunitySize() {
            return communities.stream()
                .mapToInt(CommunityDetector.Community::getSize)
                .max().orElse(0);
        }

        @Override
        public String toString() {
            return String.format("t=%d: %d communities, %d vertices, %d edges",
                timestamp, communities.size(), vertexCount, edgeCount);
        }
    }

    /**
     * A single evolutionary event between two consecutive snapshots.
     */
    public static class EvolutionEvent {
        private final EventType type;
        private final int transitionIndex;
        private final long fromTimestamp;
        private final long toTimestamp;
        private final Set<String> primaryMembers;
        private final List<Set<String>> relatedGroups;
        private final String description;

        public EvolutionEvent(EventType type, int transitionIndex,
                               long fromTimestamp, long toTimestamp,
                               Set<String> primaryMembers,
                               List<Set<String>> relatedGroups,
                               String description) {
            this.type = type;
            this.transitionIndex = transitionIndex;
            this.fromTimestamp = fromTimestamp;
            this.toTimestamp = toTimestamp;
            this.primaryMembers = Collections.unmodifiableSet(new LinkedHashSet<>(primaryMembers));
            this.relatedGroups = relatedGroups.stream()
                .map(s -> Collections.unmodifiableSet(new LinkedHashSet<>(s)))
                .collect(Collectors.toUnmodifiableList());
            this.description = description;
        }

        public EventType getType() { return type; }
        public int getTransitionIndex() { return transitionIndex; }
        public long getFromTimestamp() { return fromTimestamp; }
        public long getToTimestamp() { return toTimestamp; }
        public Set<String> getPrimaryMembers() { return primaryMembers; }
        public List<Set<String>> getRelatedGroups() { return relatedGroups; }
        public String getDescription() { return description; }

        @Override
        public String toString() {
            return String.format("[%s] t%d→t%d: %s", type, fromTimestamp, toTimestamp, description);
        }
    }

    /**
     * Complete result of community evolution tracking.
     */
    public static class EvolutionResult {
        private final List<CommunitySnapshot> snapshots;
        private final List<EvolutionEvent> events;
        private final double migrationThreshold;

        public EvolutionResult(List<CommunitySnapshot> snapshots,
                                List<EvolutionEvent> events) {
            this(snapshots, events, 0.3);
        }

        public EvolutionResult(List<CommunitySnapshot> snapshots,
                                List<EvolutionEvent> events,
                                double migrationThreshold) {
            this.snapshots = Collections.unmodifiableList(new ArrayList<>(snapshots));
            this.events = Collections.unmodifiableList(new ArrayList<>(events));
            this.migrationThreshold = migrationThreshold;
        }

        public List<CommunitySnapshot> getSnapshots() { return snapshots; }
        public List<EvolutionEvent> getEvents() { return events; }

        /** Returns only events of a specific type. */
        public List<EvolutionEvent> getEventsByType(EventType type) {
            return events.stream()
                .filter(e -> e.getType() == type)
                .collect(Collectors.toList());
        }

        /** Returns events for a specific transition (between snapshots i-1 and i). */
        public List<EvolutionEvent> getEventsAtTransition(int index) {
            return events.stream()
                .filter(e -> e.getTransitionIndex() == index)
                .collect(Collectors.toList());
        }

        /** Count of events by type. */
        public Map<EventType, Long> getEventCounts() {
            return events.stream()
                .collect(Collectors.groupingBy(EvolutionEvent::getType,
                    Collectors.counting()));
        }

        /**
         * Tracks which community a specific node belonged to at each snapshot.
         *
         * @param nodeId the node to trace
         * @return list of snapshots where the node appeared, with its community
         */
        public List<NodeLineageEntry> getNodeLineage(String nodeId) {
            List<NodeLineageEntry> lineage = new ArrayList<>();
            for (CommunitySnapshot snap : snapshots) {
                for (CommunityDetector.Community comm : snap.getCommunities()) {
                    if (comm.getMembers().contains(nodeId)) {
                        lineage.add(new NodeLineageEntry(snap.getTimestamp(), comm));
                        break;
                    }
                }
            }
            return lineage;
        }

        /**
         * Returns the overall stability score: ratio of STABLE events
         * to total events (excluding BIRTH/DEATH). Higher = more stable network.
         */
        public double getStabilityScore() {
            long nonLifecycle = events.stream()
                .filter(e -> e.getType() != EventType.BIRTH && e.getType() != EventType.DEATH)
                .count();
            if (nonLifecycle == 0) return 1.0;
            long stable = events.stream()
                .filter(e -> e.getType() == EventType.STABLE)
                .count();
            return (double) stable / nonLifecycle;
        }

        /**
         * Returns the overall volatility score: ratio of structural events
         * (MERGE + SPLIT + BIRTH + DEATH) to total events. Higher = more volatile.
         */
        public double getVolatilityScore() {
            if (events.isEmpty()) return 0.0;
            long structural = events.stream()
                .filter(e -> e.getType() == EventType.MERGE
                    || e.getType() == EventType.SPLIT
                    || e.getType() == EventType.BIRTH
                    || e.getType() == EventType.DEATH)
                .count();
            return (double) structural / events.size();
        }

        /**
         * Finds nodes that changed communities between any two consecutive snapshots.
         *
         * @return map of node → number of community changes
         */
        public Map<String, Integer> getMigrationCounts() {
            Map<String, Integer> migrations = new HashMap<>();
            for (int i = 1; i < snapshots.size(); i++) {
                Map<String, Set<String>> prevNodeComm = buildNodeCommunityMap(snapshots.get(i - 1));
                Map<String, Set<String>> currNodeComm = buildNodeCommunityMap(snapshots.get(i));

                Set<String> allNodes = new HashSet<>(prevNodeComm.keySet());
                allNodes.addAll(currNodeComm.keySet());

                for (String node : allNodes) {
                    Set<String> prevPeers = prevNodeComm.get(node);
                    Set<String> currPeers = currNodeComm.get(node);
                    if (prevPeers != null && currPeers != null) {
                        // Check if the community changed significantly
                        if (jaccard(prevPeers, currPeers) < migrationThreshold) {
                            migrations.merge(node, 1, Integer::sum);
                        }
                    }
                }
            }
            return migrations;
        }

        /** Returns the top N most mobile nodes (most community changes). */
        public List<Map.Entry<String, Integer>> getTopMigrants(int n) {
            return getMigrationCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(n)
                .collect(Collectors.toList());
        }

        /**
         * Returns a human-readable summary of the evolution analysis.
         */
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Community Evolution Summary ===\n");
            sb.append(String.format("Snapshots: %d\n", snapshots.size()));
            sb.append(String.format("Total events: %d\n", events.size()));

            Map<EventType, Long> counts = getEventCounts();
            for (EventType type : EventType.values()) {
                long count = counts.getOrDefault(type, 0L);
                if (count > 0) {
                    sb.append(String.format("  %-12s: %d\n", type, count));
                }
            }

            sb.append(String.format("Stability score: %.2f\n", getStabilityScore()));
            sb.append(String.format("Volatility score: %.2f\n", getVolatilityScore()));

            // Snapshot progression
            sb.append("\nSnapshot progression:\n");
            for (CommunitySnapshot snap : snapshots) {
                sb.append(String.format("  %s\n", snap));
            }

            // Top migrants
            List<Map.Entry<String, Integer>> migrants = getTopMigrants(5);
            if (!migrants.isEmpty()) {
                sb.append("\nTop mobile nodes:\n");
                for (Map.Entry<String, Integer> m : migrants) {
                    sb.append(String.format("  %s: %d community changes\n",
                        m.getKey(), m.getValue()));
                }
            }

            return sb.toString();
        }

        /** Builds a map of node → set of co-community-members for Jaccard comparison. */
        private Map<String, Set<String>> buildNodeCommunityMap(CommunitySnapshot snapshot) {
            Map<String, Set<String>> map = new HashMap<>();
            for (CommunityDetector.Community comm : snapshot.getCommunities()) {
                for (String node : comm.getMembers()) {
                    map.put(node, comm.getMembers());
                }
            }
            return map;
        }
    }

    /**
     * Tracks a node's community membership at a specific time point.
     */
    public static class NodeLineageEntry {
        private final long timestamp;
        private final CommunityDetector.Community community;

        public NodeLineageEntry(long timestamp, CommunityDetector.Community community) {
            this.timestamp = timestamp;
            this.community = community;
        }

        public long getTimestamp() { return timestamp; }
        public CommunityDetector.Community getCommunity() { return community; }

        @Override
        public String toString() {
            return String.format("t=%d: community %d (size %d)",
                timestamp, community.getId(), community.getSize());
        }
    }
}
