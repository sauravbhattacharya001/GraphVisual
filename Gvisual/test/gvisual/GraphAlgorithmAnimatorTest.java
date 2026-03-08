package gvisual;

import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for GraphAlgorithmAnimator.
 */
public class GraphAlgorithmAnimatorTest {

    private Graph<String, edge> simpleGraph;
    private Graph<String, edge> weightedGraph;
    private Graph<String, edge> directedGraph;
    private Graph<String, edge> singleNode;
    private Graph<String, edge> disconnected;

    @Before
    public void setUp() {
        // Simple undirected: A-B-C-D (path) + A-C (shortcut)
        simpleGraph = new UndirectedSparseGraph<>();
        simpleGraph.addVertex("A");
        simpleGraph.addVertex("B");
        simpleGraph.addVertex("C");
        simpleGraph.addVertex("D");
        simpleGraph.addEdge(new edge("e", "A", "B"), "A", "B");
        simpleGraph.addEdge(new edge("e", "B", "C"), "B", "C");
        simpleGraph.addEdge(new edge("e", "C", "D"), "C", "D");
        simpleGraph.addEdge(new edge("e", "A", "C"), "A", "C");

        // Weighted graph for Dijkstra/Kruskal
        weightedGraph = new UndirectedSparseGraph<>();
        weightedGraph.addVertex("A");
        weightedGraph.addVertex("B");
        weightedGraph.addVertex("C");
        weightedGraph.addVertex("D");
        edge e1 = new edge("e", "A", "B"); e1.setWeight(1);
        edge e2 = new edge("e", "B", "C"); e2.setWeight(3);
        edge e3 = new edge("e", "A", "C"); e3.setWeight(5);
        edge e4 = new edge("e", "C", "D"); e4.setWeight(2);
        weightedGraph.addEdge(e1, "A", "B");
        weightedGraph.addEdge(e2, "B", "C");
        weightedGraph.addEdge(e3, "A", "C");
        weightedGraph.addEdge(e4, "C", "D");

        // Directed graph for PageRank
        directedGraph = new DirectedSparseGraph<>();
        directedGraph.addVertex("A");
        directedGraph.addVertex("B");
        directedGraph.addVertex("C");
        directedGraph.addEdge(new edge("e", "A", "B"), "A", "B");
        directedGraph.addEdge(new edge("e", "B", "C"), "B", "C");
        directedGraph.addEdge(new edge("e", "C", "A"), "C", "A");

        // Single node
        singleNode = new UndirectedSparseGraph<>();
        singleNode.addVertex("X");

        // Disconnected
        disconnected = new UndirectedSparseGraph<>();
        disconnected.addVertex("A");
        disconnected.addVertex("B");
        disconnected.addVertex("C");
        disconnected.addEdge(new edge("e", "A", "B"), "A", "B");
        // C is isolated
    }

    // ── Constructor tests ────────────────────────────────────────

    @Test
    public void testConstructorBasic() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(simpleGraph);
        assertNotNull(anim);
    }

    @Test
    public void testConstructorCustomDimensions() {
        GraphAlgorithmAnimator anim =
                new GraphAlgorithmAnimator(simpleGraph, 1200, 800, 25);
        assertNotNull(anim);
    }

    @Test
    public void testConstructorWithPositions() {
        Map<String, double[]> pos = new LinkedHashMap<>();
        pos.put("A", new double[]{100, 100});
        pos.put("B", new double[]{200, 100});
        pos.put("C", new double[]{150, 200});
        pos.put("D", new double[]{250, 200});
        GraphAlgorithmAnimator anim =
                new GraphAlgorithmAnimator(simpleGraph, pos);
        assertNotNull(anim);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNullGraph() {
        new GraphAlgorithmAnimator(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNullPositions() {
        new GraphAlgorithmAnimator(simpleGraph, null);
    }

    // ── BFS tests ────────────────────────────────────────────────

    @Test
    public void testBFSBasic() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(simpleGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames = anim.animateBFS("A");
        assertNotNull(frames);
        assertTrue("BFS should produce at least 3 frames", frames.size() >= 3);
    }

    @Test
    public void testBFSStartFrame() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(simpleGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames = anim.animateBFS("A");
        GraphAlgorithmAnimator.AnimationFrame first = frames.get(0);
        assertEquals(0, first.getStepNumber());
        assertEquals("BFS", first.getAlgorithmName());
        assertTrue(first.getDescription().contains("A"));
    }

    @Test
    public void testBFSEndFrame() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(simpleGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames = anim.animateBFS("A");
        GraphAlgorithmAnimator.AnimationFrame last = frames.get(frames.size() - 1);
        assertTrue(last.getDescription().contains("complete"));
        assertTrue(last.getDescription().contains("4")); // all 4 vertices
    }

    @Test
    public void testBFSSingleNode() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(singleNode);
        List<GraphAlgorithmAnimator.AnimationFrame> frames = anim.animateBFS("X");
        assertTrue(frames.size() >= 2);
    }

    @Test
    public void testBFSDisconnected() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(disconnected);
        List<GraphAlgorithmAnimator.AnimationFrame> frames = anim.animateBFS("A");
        GraphAlgorithmAnimator.AnimationFrame last = frames.get(frames.size() - 1);
        assertTrue(last.getDescription().contains("2")); // only A and B reached
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBFSInvalidVertex() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(simpleGraph);
        anim.animateBFS("Z");
    }

    @Test
    public void testBFSFrameHasColors() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(simpleGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames = anim.animateBFS("A");
        for (GraphAlgorithmAnimator.AnimationFrame f : frames) {
            assertNotNull(f.getNodeColors());
            assertNotNull(f.getEdgeColors());
            assertEquals(4, f.getNodeColors().size());
        }
    }

    // ── DFS tests ────────────────────────────────────────────────

    @Test
    public void testDFSBasic() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(simpleGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames = anim.animateDFS("A");
        assertNotNull(frames);
        assertTrue("DFS should produce many frames (enter + backtrack)",
                frames.size() >= 5);
    }

    @Test
    public void testDFSStartFrame() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(simpleGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames = anim.animateDFS("A");
        assertEquals("DFS", frames.get(0).getAlgorithmName());
        assertTrue(frames.get(0).getDescription().contains("Start"));
    }

    @Test
    public void testDFSEndFrame() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(simpleGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames = anim.animateDFS("A");
        GraphAlgorithmAnimator.AnimationFrame last = frames.get(frames.size() - 1);
        assertTrue(last.getDescription().contains("complete"));
    }

    @Test
    public void testDFSSingleNode() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(singleNode);
        List<GraphAlgorithmAnimator.AnimationFrame> frames = anim.animateDFS("X");
        assertTrue(frames.size() >= 2);
    }

    @Test
    public void testDFSHasBacktracking() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(simpleGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames = anim.animateDFS("A");
        boolean hasBacktrack = false;
        for (GraphAlgorithmAnimator.AnimationFrame f : frames) {
            if (f.getDescription().contains("Backtrack")) {
                hasBacktrack = true;
                break;
            }
        }
        assertTrue("DFS should have backtracking frames", hasBacktrack);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDFSInvalidVertex() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(simpleGraph);
        anim.animateDFS("NOPE");
    }

    // ── Dijkstra tests ──────────────────────────────────────────

    @Test
    public void testDijkstraBasic() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(weightedGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames =
                anim.animateDijkstra("A");
        assertNotNull(frames);
        assertTrue(frames.size() >= 3);
    }

    @Test
    public void testDijkstraShowsDistances() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(weightedGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames =
                anim.animateDijkstra("A");
        GraphAlgorithmAnimator.AnimationFrame last = frames.get(frames.size() - 1);
        boolean hasDistLabel = false;
        for (String label : last.getNodeLabels().values()) {
            if (label.contains("(") && label.contains(")")) {
                hasDistLabel = true;
                break;
            }
        }
        assertTrue("Labels should show distances", hasDistLabel);
    }

    @Test
    public void testDijkstraFindsAllReachable() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(weightedGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames =
                anim.animateDijkstra("A");
        GraphAlgorithmAnimator.AnimationFrame last = frames.get(frames.size() - 1);
        assertTrue(last.getDescription().contains("4")); // All 4 reached
    }

    @Test
    public void testDijkstraDisconnected() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(disconnected);
        List<GraphAlgorithmAnimator.AnimationFrame> frames =
                anim.animateDijkstra("A");
        GraphAlgorithmAnimator.AnimationFrame last = frames.get(frames.size() - 1);
        assertTrue(last.getDescription().contains("2")); // Only A, B
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDijkstraInvalidVertex() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(simpleGraph);
        anim.animateDijkstra("INVALID");
    }

    // ── Kruskal tests ───────────────────────────────────────────

    @Test
    public void testKruskalBasic() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(weightedGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames =
                anim.animateKruskal();
        assertNotNull(frames);
        assertTrue(frames.size() >= 3);
    }

    @Test
    public void testKruskalCompletes() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(weightedGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames =
                anim.animateKruskal();
        GraphAlgorithmAnimator.AnimationFrame last = frames.get(frames.size() - 1);
        assertTrue(last.getDescription().contains("MST complete"));
        assertTrue(last.getDescription().contains("3 edges")); // n-1 edges
    }

    @Test
    public void testKruskalShowsSkips() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(weightedGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames =
                anim.animateKruskal();
        boolean hasSkip = false;
        for (GraphAlgorithmAnimator.AnimationFrame f : frames) {
            if (f.getDescription().contains("Skip")) {
                hasSkip = true;
                break;
            }
        }
        assertTrue("Kruskal should skip cycle-creating edges", hasSkip);
    }

    @Test
    public void testKruskalShowsWeights() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(weightedGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames =
                anim.animateKruskal();
        GraphAlgorithmAnimator.AnimationFrame last = frames.get(frames.size() - 1);
        assertTrue(last.getDescription().contains("weight"));
    }

    // ── PageRank tests ──────────────────────────────────────────

    @Test
    public void testPageRankBasic() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(directedGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames =
                anim.animatePageRank(0.85, 20);
        assertNotNull(frames);
        assertTrue(frames.size() >= 2);
    }

    @Test
    public void testPageRankConverges() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(directedGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames =
                anim.animatePageRank(0.85, 100);
        // Should converge before 100 iterations for a 3-node cycle
        assertTrue("PageRank should converge early",
                frames.size() < 100);
    }

    @Test
    public void testPageRankHasRankLabels() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(directedGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames =
                anim.animatePageRank(0.85, 10);
        GraphAlgorithmAnimator.AnimationFrame last = frames.get(frames.size() - 1);
        for (String label : last.getNodeLabels().values()) {
            assertTrue("Label should contain rank value: " + label,
                    label.contains("(") && label.contains(")"));
        }
    }

    @Test
    public void testPageRankSymmetricCycle() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(directedGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames =
                anim.animatePageRank(0.85, 50);
        GraphAlgorithmAnimator.AnimationFrame last = frames.get(frames.size() - 1);
        Set<String> labels = new HashSet<>(last.getNodeLabels().values());
        assertEquals("All 3 nodes should have labels", 3, labels.size());
    }

    // ── SVG rendering tests ──────────────────────────────────────

    @Test
    public void testToSVGBasic() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(simpleGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames = anim.animateBFS("A");
        String svg = anim.toSVG(frames.get(0), 800, 600);
        assertTrue(svg.startsWith("<svg"));
        assertTrue(svg.endsWith("</svg>"));
        assertTrue(svg.contains("xmlns"));
    }

    @Test
    public void testToSVGContainsNodes() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(simpleGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames = anim.animateBFS("A");
        String svg = anim.toSVG(frames.get(0), 800, 600);
        assertTrue(svg.contains("<circle"));
        assertTrue(svg.contains("<text"));
    }

    @Test
    public void testToSVGContainsEdges() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(simpleGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames = anim.animateBFS("A");
        String svg = anim.toSVG(frames.get(0), 800, 600);
        assertTrue(svg.contains("<line"));
    }

    @Test
    public void testToSVGContainsStepInfo() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(simpleGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames = anim.animateBFS("A");
        String svg = anim.toSVG(frames.get(0), 800, 600);
        assertTrue(svg.contains("BFS"));
        assertTrue(svg.contains("Step 0"));
    }

    @Test
    public void testEachFrameProducesValidSVG() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(simpleGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames = anim.animateBFS("A");
        for (GraphAlgorithmAnimator.AnimationFrame f : frames) {
            String svg = anim.toSVG(f, 800, 600);
            assertTrue(svg.startsWith("<svg"));
            assertTrue(svg.endsWith("</svg>"));
        }
    }

    // ── HTML player tests ────────────────────────────────────────

    @Test
    public void testHtmlPlayerBasic() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(simpleGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames = anim.animateBFS("A");
        String html = anim.toHtmlPlayer(frames, 800, 600);
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("</html>"));
        assertTrue(html.contains("Play"));
        assertTrue(html.contains("Prev"));
        assertTrue(html.contains("Next"));
    }

    @Test
    public void testHtmlPlayerContainsFrames() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(simpleGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames = anim.animateBFS("A");
        String html = anim.toHtmlPlayer(frames, 800, 600);
        assertTrue(html.contains("var svgs"));
        int svgCount = countOccurrences(html, "<svg");
        assertEquals("HTML should contain all frame SVGs",
                frames.size(), svgCount);
    }

    @Test
    public void testHtmlPlayerHasControls() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(simpleGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames = anim.animateBFS("A");
        String html = anim.toHtmlPlayer(frames, 800, 600);
        assertTrue(html.contains("togglePlay"));
        assertTrue(html.contains("stepFwd"));
        assertTrue(html.contains("stepBack"));
        assertTrue(html.contains("Speed"));
    }

    // ── Summary tests ────────────────────────────────────────────

    @Test
    public void testSummary() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(simpleGraph);
        String summary = anim.getSummary();
        assertTrue(summary.contains("4 vertices"));
        assertTrue(summary.contains("4 edges"));
        assertTrue(summary.contains("BFS"));
        assertTrue(summary.contains("DFS"));
        assertTrue(summary.contains("Dijkstra"));
        assertTrue(summary.contains("Kruskal"));
        assertTrue(summary.contains("PageRank"));
    }

    // ── AnimationFrame tests ─────────────────────────────────────

    @Test
    public void testFrameImmutability() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(simpleGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames = anim.animateBFS("A");
        GraphAlgorithmAnimator.AnimationFrame f = frames.get(0);
        try {
            f.getNodeColors().put("NEW", "#000");
            fail("Should not be able to modify frame colors");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testFrameProperties() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(simpleGraph);
        List<GraphAlgorithmAnimator.AnimationFrame> frames = anim.animateBFS("A");
        GraphAlgorithmAnimator.AnimationFrame f = frames.get(0);
        assertEquals(0, f.getStepNumber());
        assertEquals("BFS", f.getAlgorithmName());
        assertNotNull(f.getDescription());
        assertNotNull(f.getNodeLabels());
    }

    // ── Edge case tests ──────────────────────────────────────────

    @Test
    public void testSingleNodeBFS() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(singleNode);
        List<GraphAlgorithmAnimator.AnimationFrame> frames = anim.animateBFS("X");
        assertTrue(frames.size() >= 2);
    }

    @Test
    public void testSingleNodeDFS() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(singleNode);
        List<GraphAlgorithmAnimator.AnimationFrame> frames = anim.animateDFS("X");
        assertTrue(frames.size() >= 2);
    }

    @Test
    public void testSingleNodeDijkstra() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(singleNode);
        List<GraphAlgorithmAnimator.AnimationFrame> frames =
                anim.animateDijkstra("X");
        assertTrue(frames.size() >= 2);
    }

    @Test
    public void testSingleNodeKruskal() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(singleNode);
        List<GraphAlgorithmAnimator.AnimationFrame> frames =
                anim.animateKruskal();
        assertTrue(frames.size() >= 1);
    }

    @Test
    public void testDisconnectedDFS() {
        GraphAlgorithmAnimator anim = new GraphAlgorithmAnimator(disconnected);
        List<GraphAlgorithmAnimator.AnimationFrame> frames = anim.animateDFS("A");
        GraphAlgorithmAnimator.AnimationFrame last = frames.get(frames.size() - 1);
        assertTrue(last.getDescription().contains("2")); // Only 2 visited
    }

    // ── Utility ──────────────────────────────────────────────────

    private int countOccurrences(String text, String sub) {
        int count = 0, idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
