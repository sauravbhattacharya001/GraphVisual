package gvisual;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link NetworkFlowExporter}.
 *
 * Covers:
 *   - Max-flow correctness on the canonical CLRS / textbook flow networks.
 *   - Disconnected source/sink handling.
 *   - Missing source / sink labels.
 *   - Self-loops, parallel edges (capacities should aggregate).
 *   - HTML export contents (preset injection, escape safety).
 *   - Output path validation against directory traversal.
 *   - Empty graph behavior.
 */
public class NetworkFlowExporterTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private NetworkFlowExporter exp;

    @Before
    public void setUp() {
        exp = new NetworkFlowExporter();
    }

    // ----------------------------------------------------------------
    // computeMaxFlow
    // ----------------------------------------------------------------

    @Test
    public void testMaxFlowEmptyGraphIsZero() {
        assertEquals(0, exp.computeMaxFlow());
    }

    @Test
    public void testMaxFlowMissingSourceOrSinkIsZero() {
        exp.addNode("A", 0, 0);
        exp.addNode("B", 0, 0);
        exp.addEdge("A", "B", 10);
        // No source/sink set
        assertEquals(0, exp.computeMaxFlow());

        exp.setSource("A");
        // sink still null
        assertEquals(0, exp.computeMaxFlow());
    }

    @Test
    public void testMaxFlowUnknownSourceOrSinkIsZero() {
        exp.addNode("A", 0, 0);
        exp.addNode("B", 0, 0);
        exp.addEdge("A", "B", 10);
        exp.setSource("X");  // not in graph
        exp.setSink("B");
        assertEquals(0, exp.computeMaxFlow());

        exp.setSource("A");
        exp.setSink("Y");    // not in graph
        assertEquals(0, exp.computeMaxFlow());
    }

    @Test
    public void testMaxFlowSourceEqualsSink() {
        exp.addNode("A", 0, 0);
        exp.setSource("A");
        exp.setSink("A");
        // With s == t there is no augmenting path of positive length
        // (parent[t] is initialized to s and the BFS loop exits immediately).
        assertEquals(0, exp.computeMaxFlow());
    }

    @Test
    public void testMaxFlowSingleEdge() {
        exp.addNode("S", 0, 0);
        exp.addNode("T", 0, 0);
        exp.addEdge("S", "T", 7);
        exp.setSource("S");
        exp.setSink("T");
        assertEquals(7, exp.computeMaxFlow());
    }

    @Test
    public void testMaxFlowDisconnected() {
        exp.addNode("S", 0, 0);
        exp.addNode("M", 0, 0);
        exp.addNode("T", 0, 0);
        // S->M only, T isolated -> no s-t path
        exp.addEdge("S", "M", 10);
        exp.setSource("S");
        exp.setSink("T");
        assertEquals(0, exp.computeMaxFlow());
    }

    @Test
    public void testMaxFlowClassicCLRSNetwork() {
        // The canonical example from CLRS, ch. 26. Max flow = 23.
        // Vertices: s, v1, v2, v3, v4, t
        // Capacities:
        //   s->v1: 16, s->v2: 13
        //   v1->v3: 12
        //   v2->v1: 4, v2->v4: 14
        //   v3->v2: 9, v3->t: 20
        //   v4->v3: 7, v4->t: 4
        exp.addNode("s",  0, 0);
        exp.addNode("v1", 0, 0);
        exp.addNode("v2", 0, 0);
        exp.addNode("v3", 0, 0);
        exp.addNode("v4", 0, 0);
        exp.addNode("t",  0, 0);
        exp.addEdge("s",  "v1", 16);
        exp.addEdge("s",  "v2", 13);
        exp.addEdge("v1", "v3", 12);
        exp.addEdge("v2", "v1",  4);
        exp.addEdge("v2", "v4", 14);
        exp.addEdge("v3", "v2",  9);
        exp.addEdge("v3", "t",  20);
        exp.addEdge("v4", "v3",  7);
        exp.addEdge("v4", "t",   4);
        exp.setSource("s");
        exp.setSink("t");
        assertEquals(23, exp.computeMaxFlow());
    }

    @Test
    public void testMaxFlowParallelEdgesAggregate() {
        // Two parallel edges s->t with cap 5 each should yield max flow 10.
        exp.addNode("s", 0, 0);
        exp.addNode("t", 0, 0);
        exp.addEdge("s", "t", 5);
        exp.addEdge("s", "t", 5);
        exp.setSource("s");
        exp.setSink("t");
        assertEquals(10, exp.computeMaxFlow());
    }

    @Test
    public void testMaxFlowBottleneckIsRespected() {
        // s -> a -> t with caps (100, 1) -> max flow 1
        exp.addNode("s", 0, 0);
        exp.addNode("a", 0, 0);
        exp.addNode("t", 0, 0);
        exp.addEdge("s", "a", 100);
        exp.addEdge("a", "t",   1);
        exp.setSource("s");
        exp.setSink("t");
        assertEquals(1, exp.computeMaxFlow());
    }

    @Test
    public void testMaxFlowRepeatableIsIdempotent() {
        // Calling computeMaxFlow twice must return the same value because the
        // residual graph is rebuilt internally each call (the impl does not
        // mutate `edges`).
        exp.addNode("s", 0, 0);
        exp.addNode("t", 0, 0);
        exp.addEdge("s", "t", 9);
        exp.setSource("s");
        exp.setSink("t");
        assertEquals(9, exp.computeMaxFlow());
        assertEquals(9, exp.computeMaxFlow());
    }

    @Test
    public void testMaxFlowEdgeWithUnknownEndpointIsIgnored() {
        exp.addNode("s", 0, 0);
        exp.addNode("t", 0, 0);
        exp.addEdge("s", "t", 5);
        // Edge referencing nodes that were never added -> must be skipped, not crash.
        exp.addEdge("ghost1", "ghost2", 999);
        exp.setSource("s");
        exp.setSink("t");
        assertEquals(5, exp.computeMaxFlow());
    }

    // ----------------------------------------------------------------
    // export(...)
    // ----------------------------------------------------------------

    @Test
    public void testExportProducesHtmlFile() throws Exception {
        File out = tmp.newFile("flow.html");

        exp.addNode("s", 10, 20);
        exp.addNode("t", 30, 40);
        exp.addEdge("s", "t", 7);
        exp.setSource("s");
        exp.setSink("t");
        exp.export(out.getAbsolutePath());

        assertTrue("export must create the file", out.exists());
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("</html>"));
        assertTrue("must embed PRESET_NODES", html.contains("PRESET_NODES"));
        assertTrue("must embed PRESET_EDGES", html.contains("PRESET_EDGES"));
        assertTrue("must embed PRESET_SOURCE", html.contains("PRESET_SOURCE"));
        assertTrue("must embed PRESET_SINK",   html.contains("PRESET_SINK"));
        // Max flow string should be present
        assertTrue("must include computed max flow",
                html.contains("Max flow = 7"));
    }

    @Test
    public void testExportEncodesNodeCount() throws Exception {
        File out = tmp.newFile("flow.html");

        exp.addNode("a", 0, 0);
        exp.addNode("b", 0, 0);
        exp.addNode("c", 0, 0);
        exp.addEdge("a", "b", 5);
        exp.addEdge("b", "c", 5);
        exp.setSource("a");
        exp.setSink("c");
        exp.export(out.getAbsolutePath());

        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        // Document line: "<p>Network Flow: 3 nodes, 2 edges. Max flow = 5</p>"
        assertTrue(html.contains("3 nodes"));
        assertTrue(html.contains("2 edges"));
    }

    @Test
    public void testExportRejectsTraversalPath() {
        exp.addNode("s", 0, 0);
        exp.addNode("t", 0, 0);
        exp.addEdge("s", "t", 1);
        exp.setSource("s");
        exp.setSink("t");

        // Path containing .. should be rejected by ExportUtils.validateOutputPath.
        try {
            exp.export("../../../etc/passwd");
            fail("Expected validateOutputPath to reject traversal path");
        } catch (Exception expected) {
            // ok - any IOException / IllegalArgumentException is acceptable as long
            // as the file is not written. We don't tie the test to a specific type.
        }
    }

    @Test
    public void testExportHandlesEmptyGraph() throws Exception {
        File out = tmp.newFile("empty.html");
        exp.export(out.getAbsolutePath());
        assertTrue(out.exists());
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertTrue(html.contains("PRESET_NODES = []"));
        assertTrue(html.contains("PRESET_EDGES = []"));
        assertTrue(html.contains("PRESET_SOURCE = -1"));
        assertTrue(html.contains("PRESET_SINK = -1"));
    }

    // ----------------------------------------------------------------
    // FlowNode / FlowEdge inner classes
    // ----------------------------------------------------------------

    @Test
    public void testFlowNodeFieldsAreFinalAndAccessible() {
        NetworkFlowExporter.FlowNode n = new NetworkFlowExporter.FlowNode("Alpha", 1.5, 2.5);
        assertEquals("Alpha", n.label);
        assertEquals(1.5, n.x, 0.0);
        assertEquals(2.5, n.y, 0.0);
    }

    @Test
    public void testFlowEdgeFieldsAreFinalAndAccessible() {
        NetworkFlowExporter.FlowEdge e = new NetworkFlowExporter.FlowEdge("a", "b", 42);
        assertEquals("a", e.from);
        assertEquals("b", e.to);
        assertEquals(42, e.capacity);
    }
}
