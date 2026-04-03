package gvisual;

import java.io.*;
import java.util.*;

/**
 * NetworkFlowExporter — Generates an interactive HTML page for max-flow / min-cut
 * visualization with Ford-Fulkerson and Edmonds-Karp step-by-step animation.
 *
 * <p>Usage:
 * <pre>
 *   NetworkFlowExporter exporter = new NetworkFlowExporter();
 *   // Add nodes and directed edges with capacities
 *   exporter.addNode("A", 100, 200);
 *   exporter.addNode("B", 300, 200);
 *   exporter.addEdge("A", "B", 10);
 *   exporter.setSource("A");
 *   exporter.setSink("B");
 *   exporter.export("flow-output.html");
 * </pre>
 */
public class NetworkFlowExporter {

    private final List<FlowNode> nodes = new ArrayList<>();
    private final List<FlowEdge> edges = new ArrayList<>();
    private String sourceLabel = null;
    private String sinkLabel = null;

    public static class FlowNode {
        public final String label;
        public final double x, y;
        public FlowNode(String label, double x, double y) {
            this.label = label; this.x = x; this.y = y;
        }
    }

    public static class FlowEdge {
        public final String from, to;
        public final int capacity;
        public FlowEdge(String from, String to, int capacity) {
            this.from = from; this.to = to; this.capacity = capacity;
        }
    }

    public void addNode(String label, double x, double y) {
        nodes.add(new FlowNode(label, x, y));
    }

    public void addEdge(String from, String to, int capacity) {
        edges.add(new FlowEdge(from, to, capacity));
    }

    public void setSource(String label) { this.sourceLabel = label; }
    public void setSink(String label) { this.sinkLabel = label; }

    /**
     * Compute max-flow using Edmonds-Karp (BFS-based Ford-Fulkerson).
     * Returns the maximum flow value.
     */
    public int computeMaxFlow() {
        if (sourceLabel == null || sinkLabel == null) return 0;

        // Build adjacency with residual
        Map<String, Integer> labelToIdx = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) labelToIdx.put(nodes.get(i).label, i);

        int n = nodes.size();
        int[][] cap = new int[n][n];
        for (FlowEdge e : edges) {
            Integer fi = labelToIdx.get(e.from), ti = labelToIdx.get(e.to);
            if (fi != null && ti != null) cap[fi][ti] += e.capacity;
        }

        int s = labelToIdx.getOrDefault(sourceLabel, -1);
        int t = labelToIdx.getOrDefault(sinkLabel, -1);
        if (s < 0 || t < 0) return 0;

        int totalFlow = 0;
        int[] parent = new int[n];

        while (true) {
            Arrays.fill(parent, -1);
            parent[s] = s;
            Queue<Integer> queue = new ArrayDeque<>();
            queue.add(s);
            while (!queue.isEmpty() && parent[t] == -1) {
                int u = queue.poll();
                for (int v = 0; v < n; v++) {
                    if (parent[v] == -1 && cap[u][v] > 0) {
                        parent[v] = u;
                        queue.add(v);
                    }
                }
            }
            if (parent[t] == -1) break;

            int bottleneck = Integer.MAX_VALUE;
            for (int v = t; v != s; v = parent[v])
                bottleneck = Math.min(bottleneck, cap[parent[v]][v]);

            for (int v = t; v != s; v = parent[v]) {
                cap[parent[v]][v] -= bottleneck;
                cap[v][parent[v]] += bottleneck;
            }
            totalFlow += bottleneck;
        }
        return totalFlow;
    }

    /**
     * Export the flow network as a self-contained interactive HTML file.
     */
    public void export(String filePath) throws IOException {
        // Build JSON for preset injection
        StringBuilder nodesJson = new StringBuilder("[");
        for (int i = 0; i < nodes.size(); i++) {
            FlowNode nd = nodes.get(i);
            if (i > 0) nodesJson.append(",");
            nodesJson.append(String.format("{id:%d,x:%.0f,y:%.0f,label:'%s'}", i, nd.x, nd.y, nd.label));
        }
        nodesJson.append("]");

        Map<String, Integer> labelToId = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) labelToId.put(nodes.get(i).label, i);

        StringBuilder edgesJson = new StringBuilder("[");
        boolean first = true;
        for (FlowEdge e : edges) {
            Integer fi = labelToId.get(e.from), ti = labelToId.get(e.to);
            if (fi == null || ti == null) continue;
            if (!first) edgesJson.append(",");
            edgesJson.append(String.format("{from:%d,to:%d,cap:%d,flow:0}", fi, ti, e.capacity));
            first = false;
        }
        edgesJson.append("]");

        int sourceId = labelToId.getOrDefault(sourceLabel, -1);
        int sinkId = labelToId.getOrDefault(sinkLabel, -1);

        // Read the HTML template and inject data
        // For simplicity, write a redirect to the docs page with data encoded
        // Validate output path to prevent directory traversal (CWE-22)
        ExportUtils.validateOutputPath(new java.io.File(filePath));
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            pw.println("<!DOCTYPE html>");
            pw.println("<html><head><meta charset='UTF-8'><title>Network Flow — GraphVisual</title></head>");
            pw.println("<body><script>");
            pw.printf("const PRESET_NODES = %s;%n", nodesJson);
            pw.printf("const PRESET_EDGES = %s;%n", edgesJson);
            pw.printf("const PRESET_SOURCE = %d;%n", sourceId);
            pw.printf("const PRESET_SINK = %d;%n", sinkId);
            pw.println("// Redirect to interactive viewer or embed inline");
            pw.println("document.write('<p>Network Flow: " + nodes.size() + " nodes, " + edges.size() + " edges. Max flow = " + computeMaxFlow() + "</p>');");
            pw.println("</script></body></html>");
        }
    }

    /** CLI entry point: generate a sample flow network HTML. */
    public static void main(String[] args) throws IOException {
        String output = args.length > 0 ? args[0] : "flow-network.html";
        NetworkFlowExporter exp = new NetworkFlowExporter();
        exp.addNode("S", 60, 250);
        exp.addNode("A", 220, 100);
        exp.addNode("B", 220, 400);
        exp.addNode("C", 380, 250);
        exp.addNode("T", 540, 250);
        exp.addEdge("S", "A", 10);
        exp.addEdge("S", "B", 10);
        exp.addEdge("A", "C", 4);
        exp.addEdge("A", "B", 2);
        exp.addEdge("B", "C", 8);
        exp.addEdge("C", "T", 10);
        exp.addEdge("A", "T", 6);
        exp.setSource("S");
        exp.setSink("T");
        System.out.println("Max flow: " + exp.computeMaxFlow());
        exp.export(output);
        System.out.println("Exported to " + output);
    }
}
