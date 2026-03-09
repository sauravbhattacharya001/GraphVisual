package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Exports a JUNG graph as a self-contained interactive HTML file using D3.js
 * force-directed layout. The output requires no server or external dependencies —
 * just open the HTML file in any modern browser.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Force-directed layout with drag, zoom, and pan</li>
 *   <li>Edges colored by relationship type (friend, classmate, etc.)</li>
 *   <li>Node size proportional to degree</li>
 *   <li>Hover tooltips showing node ID, degree, and neighbor count</li>
 *   <li>Click a node to highlight its ego network</li>
 *   <li>Search/filter nodes by ID</li>
 *   <li>Legend for edge types</li>
 *   <li>Statistics sidebar with graph metrics</li>
 *   <li>Dark/light theme toggle</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 *   InteractiveHtmlExporter exporter = new InteractiveHtmlExporter(graph);
 *   exporter.setTitle("Student Network 2011");
 *   exporter.export(new File("network.html"));
 *   // or
 *   String html = exporter.exportToString();
 * </pre>
 *
 * @author zalenix
 */
public class InteractiveHtmlExporter {

    private final Graph<String, edge> graph;
    private String title = "Graph Visualization";
    private String description = "";
    private boolean showStats = true;
    private boolean showLegend = true;
    private boolean showSearch = true;
    private boolean darkMode = false;
    private int width = 960;
    private int height = 700;

    /**
     * Creates an exporter for the given graph.
     * @param graph the JUNG graph to visualize
     */
    public InteractiveHtmlExporter(Graph<String, edge> graph) {
        if (graph == null) throw new IllegalArgumentException("Graph must not be null");
        this.graph = graph;
    }

    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setShowStats(boolean show) { this.showStats = show; }
    public void setShowLegend(boolean show) { this.showLegend = show; }
    public void setShowSearch(boolean show) { this.showSearch = show; }
    public void setDarkMode(boolean dark) { this.darkMode = dark; }
    public void setWidth(int width) { this.width = width; }
    public void setHeight(int height) { this.height = height; }

    /**
     * Exports the graph to an HTML file.
     * @param file the output file
     * @throws IOException if writing fails
     */
    public void export(File file) throws IOException {
        ExportUtils.validateOutputPath(file);
        try (Writer w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            w.write(exportToString());
        }
    }

    /**
     * Exports the graph as an HTML string.
     * @return complete self-contained HTML document
     */
    public String exportToString() {
        StringBuilder sb = new StringBuilder(16384);
        List<String> nodes = new ArrayList<>(graph.getVertices());
        Collections.sort(nodes);
        Map<String, Integer> nodeIndex = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) nodeIndex.put(nodes.get(i), i);

        // Compute degrees
        Map<String, Integer> degrees = new HashMap<>();
        for (String v : nodes) degrees.put(v, graph.degree(v));
        int maxDeg = degrees.values().stream().mapToInt(Integer::intValue).max().orElse(1);

        // Collect edges
        List<int[]> edgeList = new ArrayList<>();
        List<String> edgeTypes = new ArrayList<>();
        List<Float> edgeWeights = new ArrayList<>();
        for (edge e : graph.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            if (v1 != null && v2 != null && nodeIndex.containsKey(v1) && nodeIndex.containsKey(v2)) {
                edgeList.add(new int[]{nodeIndex.get(v1), nodeIndex.get(v2)});
                edgeTypes.add(e.getType() != null ? e.getType() : "unknown");
                edgeWeights.add(e.getWeight());
            }
        }

        // Stats
        int nodeCount = nodes.size();
        int edgeCount = edgeList.size();
        double density = nodeCount > 1 ? (2.0 * edgeCount) / (nodeCount * (nodeCount - 1.0)) : 0;
        double avgDeg = nodeCount > 0 ? (2.0 * edgeCount) / nodeCount : 0;

        // Type counts
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        for (String t : edgeTypes) typeCounts.merge(t, 1, Integer::sum);

        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n");
        sb.append("<title>").append(escHtml(title)).append("</title>\n");
        sb.append("<style>\n");
        sb.append(generateCss());
        sb.append("</style>\n</head>\n<body");
        if (darkMode) sb.append(" class=\"dark\"");
        sb.append(">\n");

        // Header
        sb.append("<div id=\"header\">\n");
        sb.append("  <h1>").append(escHtml(title)).append("</h1>\n");
        if (!description.isEmpty()) {
            sb.append("  <p class=\"desc\">").append(escHtml(description)).append("</p>\n");
        }
        sb.append("  <div id=\"controls\">\n");
        if (showSearch) {
            sb.append("    <input type=\"text\" id=\"search\" placeholder=\"Search nodes...\" autocomplete=\"off\">\n");
        }
        sb.append("    <button id=\"theme-toggle\" title=\"Toggle dark/light\">🌓</button>\n");
        sb.append("    <button id=\"reset-btn\" title=\"Reset view\">↺</button>\n");
        sb.append("  </div>\n");
        sb.append("</div>\n");

        // Main area
        sb.append("<div id=\"main\">\n");

        // Stats sidebar
        if (showStats) {
            sb.append("<div id=\"sidebar\">\n");
            sb.append("  <h3>Statistics</h3>\n");
            sb.append("  <div class=\"stat\"><span class=\"label\">Nodes</span><span class=\"val\">").append(nodeCount).append("</span></div>\n");
            sb.append("  <div class=\"stat\"><span class=\"label\">Edges</span><span class=\"val\">").append(edgeCount).append("</span></div>\n");
            sb.append("  <div class=\"stat\"><span class=\"label\">Density</span><span class=\"val\">").append(String.format("%.4f", density)).append("</span></div>\n");
            sb.append("  <div class=\"stat\"><span class=\"label\">Avg Degree</span><span class=\"val\">").append(String.format("%.2f", avgDeg)).append("</span></div>\n");
            sb.append("  <div class=\"stat\"><span class=\"label\">Max Degree</span><span class=\"val\">").append(maxDeg).append("</span></div>\n");

            if (showLegend && !typeCounts.isEmpty()) {
                sb.append("  <h3>Edge Types</h3>\n");
                for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
                    String t = entry.getKey();
                    sb.append("  <div class=\"legend-item\">");
                    sb.append("<span class=\"legend-color\" style=\"background:").append(typeColor(t)).append("\"></span>");
                    sb.append("<span>").append(escHtml(typeName(t))).append(" (").append(entry.getValue()).append(")</span>");
                    sb.append("</div>\n");
                }
            }

            sb.append("  <div id=\"node-info\" style=\"display:none\">\n");
            sb.append("    <h3>Selected Node</h3>\n");
            sb.append("    <div id=\"node-detail\"></div>\n");
            sb.append("  </div>\n");
            sb.append("</div>\n");
        }

        sb.append("<div id=\"graph-container\"><svg id=\"graph\"></svg></div>\n");
        sb.append("</div>\n");

        // Tooltip
        sb.append("<div id=\"tooltip\" style=\"display:none\"></div>\n");

        // Inline data
        sb.append("<script>\n");
        sb.append("const DATA = {\n");

        // Nodes array
        sb.append("  nodes: [\n");
        for (int i = 0; i < nodes.size(); i++) {
            String n = nodes.get(i);
            sb.append("    {id:\"").append(escJs(n)).append("\",deg:").append(degrees.get(n)).append("}");
            if (i < nodes.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        // Links array
        sb.append("  links: [\n");
        for (int i = 0; i < edgeList.size(); i++) {
            int[] pair = edgeList.get(i);
            sb.append("    {source:").append(pair[0]).append(",target:").append(pair[1]);
            sb.append(",type:\"").append(escJs(edgeTypes.get(i))).append("\"");
            sb.append(",weight:").append(edgeWeights.get(i));
            sb.append("}");
            if (i < edgeList.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n};\n");

        // Type colors map
        sb.append("const TYPE_COLORS = {");
        Set<String> seen = new HashSet<>();
        for (String t : edgeTypes) {
            if (seen.add(t)) {
                sb.append("\"").append(escJs(t)).append("\":\"").append(typeColor(t)).append("\",");
            }
        }
        sb.append("\"unknown\":\"#999\"};\n");

        sb.append("const MAX_DEG = ").append(maxDeg).append(";\n");

        // D3 inline (use CDN link in a script tag — self-contained enough)
        sb.append("</script>\n");
        sb.append("<script src=\"https://d3js.org/d3.v7.min.js\"></script>\n");
        sb.append("<script>\n");
        sb.append(generateJs());
        sb.append("</script>\n");

        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    private String generateCss() {
        return "* { margin:0; padding:0; box-sizing:border-box; }\n"
            + "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; background:#f5f5f5; color:#333; }\n"
            + "body.dark { background:#1a1a2e; color:#e0e0e0; }\n"
            + "#header { padding:12px 20px; background:#fff; border-bottom:1px solid #ddd; display:flex; align-items:center; gap:16px; flex-wrap:wrap; }\n"
            + "body.dark #header { background:#16213e; border-color:#333; }\n"
            + "#header h1 { font-size:18px; font-weight:600; }\n"
            + "#header .desc { font-size:13px; color:#777; }\n"
            + "#controls { margin-left:auto; display:flex; gap:8px; align-items:center; }\n"
            + "#search { padding:6px 10px; border:1px solid #ccc; border-radius:4px; font-size:13px; width:180px; }\n"
            + "body.dark #search { background:#0f3460; border-color:#555; color:#e0e0e0; }\n"
            + "button { padding:6px 10px; border:1px solid #ccc; border-radius:4px; background:#fff; cursor:pointer; font-size:14px; }\n"
            + "body.dark button { background:#0f3460; border-color:#555; color:#e0e0e0; }\n"
            + "button:hover { background:#eee; }\n"
            + "body.dark button:hover { background:#1a1a4e; }\n"
            + "#main { display:flex; height:calc(100vh - 60px); }\n"
            + "#sidebar { width:220px; padding:16px; overflow-y:auto; background:#fff; border-right:1px solid #ddd; }\n"
            + "body.dark #sidebar { background:#16213e; border-color:#333; }\n"
            + "#sidebar h3 { font-size:13px; font-weight:600; text-transform:uppercase; letter-spacing:0.5px; color:#888; margin:12px 0 6px; }\n"
            + "#sidebar h3:first-child { margin-top:0; }\n"
            + ".stat { display:flex; justify-content:space-between; padding:3px 0; font-size:13px; }\n"
            + ".stat .val { font-weight:600; }\n"
            + ".legend-item { display:flex; align-items:center; gap:6px; padding:2px 0; font-size:12px; }\n"
            + ".legend-color { width:12px; height:12px; border-radius:2px; flex-shrink:0; }\n"
            + "#graph-container { flex:1; position:relative; }\n"
            + "#graph { width:100%; height:100%; }\n"
            + "#tooltip { position:absolute; padding:8px 12px; background:rgba(0,0,0,.85); color:#fff; border-radius:4px; font-size:12px; pointer-events:none; z-index:10; max-width:250px; }\n"
            + "#node-detail { font-size:12px; line-height:1.6; }\n";
    }

    private String generateJs() {
        return "function escH(s){return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/\"/g,'&quot;').replace(/'/g,'&#39;');}\n"
            + "(() => {\n"
            + "const svg = d3.select('#graph');\n"
            + "const container = document.getElementById('graph-container');\n"
            + "const w = container.clientWidth, h = container.clientHeight;\n"
            + "svg.attr('viewBox', [0, 0, w, h]);\n"
            + "\n"
            + "const g = svg.append('g');\n"
            + "const zoom = d3.zoom().scaleExtent([0.1, 8]).on('zoom', e => g.attr('transform', e.transform));\n"
            + "svg.call(zoom);\n"
            + "\n"
            + "const sim = d3.forceSimulation(DATA.nodes)\n"
            + "  .force('link', d3.forceLink(DATA.links).distance(60).strength(0.4))\n"
            + "  .force('charge', d3.forceManyBody().strength(-120))\n"
            + "  .force('center', d3.forceCenter(w/2, h/2))\n"
            + "  .force('collision', d3.forceCollide().radius(d => nodeRadius(d) + 2));\n"
            + "\n"
            + "const link = g.append('g').attr('class','links').selectAll('line')\n"
            + "  .data(DATA.links).join('line')\n"
            + "  .attr('stroke', d => TYPE_COLORS[d.type] || '#999')\n"
            + "  .attr('stroke-opacity', 0.5)\n"
            + "  .attr('stroke-width', d => Math.max(1, Math.min(d.weight * 2, 5)));\n"
            + "\n"
            + "const node = g.append('g').attr('class','nodes').selectAll('circle')\n"
            + "  .data(DATA.nodes).join('circle')\n"
            + "  .attr('r', d => nodeRadius(d))\n"
            + "  .attr('fill', d => nodeColor(d))\n"
            + "  .attr('stroke', '#fff').attr('stroke-width', 1.5)\n"
            + "  .call(d3.drag().on('start', dragStart).on('drag', dragging).on('end', dragEnd));\n"
            + "\n"
            + "const label = g.append('g').attr('class','labels').selectAll('text')\n"
            + "  .data(DATA.nodes).join('text')\n"
            + "  .text(d => d.id)\n"
            + "  .attr('font-size', 10).attr('dx', 8).attr('dy', 3)\n"
            + "  .attr('fill', document.body.classList.contains('dark') ? '#ccc' : '#555')\n"
            + "  .style('pointer-events', 'none');\n"
            + "\n"
            + "// Only show labels for high-degree nodes initially\n"
            + "label.style('display', d => d.deg >= Math.max(3, MAX_DEG * 0.3) ? null : 'none');\n"
            + "\n"
            + "const tooltip = document.getElementById('tooltip');\n"
            + "\n"
            + "node.on('mouseover', (ev, d) => {\n"
            + "  const neighbors = new Set();\n"
            + "  DATA.links.forEach(l => {\n"
            + "    const s = typeof l.source === 'object' ? l.source.index : l.source;\n"
            + "    const t = typeof l.target === 'object' ? l.target.index : l.target;\n"
            + "    if (s === d.index) neighbors.add(t);\n"
            + "    if (t === d.index) neighbors.add(s);\n"
            + "  });\n"
            + "  tooltip.innerHTML = '<strong>' + escH(d.id) + '</strong><br>Degree: ' + d.deg + '<br>Neighbors: ' + neighbors.size;\n"
            + "  tooltip.style.display = 'block';\n"
            + "  tooltip.style.left = (ev.pageX + 12) + 'px';\n"
            + "  tooltip.style.top = (ev.pageY - 20) + 'px';\n"
            + "}).on('mousemove', ev => {\n"
            + "  tooltip.style.left = (ev.pageX + 12) + 'px';\n"
            + "  tooltip.style.top = (ev.pageY - 20) + 'px';\n"
            + "}).on('mouseout', () => { tooltip.style.display = 'none'; });\n"
            + "\n"
            + "// Click to highlight ego network\n"
            + "let selected = null;\n"
            + "node.on('click', (ev, d) => {\n"
            + "  if (selected === d.index) { selected = null; resetHighlight(); return; }\n"
            + "  selected = d.index;\n"
            + "  const neighbors = new Set([d.index]);\n"
            + "  DATA.links.forEach(l => {\n"
            + "    const s = typeof l.source === 'object' ? l.source.index : l.source;\n"
            + "    const t = typeof l.target === 'object' ? l.target.index : l.target;\n"
            + "    if (s === d.index) neighbors.add(t);\n"
            + "    if (t === d.index) neighbors.add(s);\n"
            + "  });\n"
            + "  node.attr('opacity', n => neighbors.has(n.index) ? 1 : 0.1);\n"
            + "  link.attr('opacity', l => {\n"
            + "    const s = typeof l.source === 'object' ? l.source.index : l.source;\n"
            + "    const t = typeof l.target === 'object' ? l.target.index : l.target;\n"
            + "    return (s === d.index || t === d.index) ? 0.8 : 0.03;\n"
            + "  });\n"
            + "  label.style('display', n => neighbors.has(n.index) ? null : 'none');\n"
            + "  // Show details\n"
            + "  const info = document.getElementById('node-info');\n"
            + "  const detail = document.getElementById('node-detail');\n"
            + "  if (info && detail) {\n"
            + "    info.style.display = 'block';\n"
            + "    detail.innerHTML = '<strong>' + escH(d.id) + '</strong><br>Degree: ' + d.deg + '<br>Neighbors: ' + (neighbors.size - 1);\n"
            + "  }\n"
            + "});\n"
            + "\n"
            + "function resetHighlight() {\n"
            + "  node.attr('opacity', 1);\n"
            + "  link.attr('opacity', 0.5);\n"
            + "  label.style('display', d => d.deg >= Math.max(3, MAX_DEG * 0.3) ? null : 'none');\n"
            + "  const info = document.getElementById('node-info');\n"
            + "  if (info) info.style.display = 'none';\n"
            + "}\n"
            + "\n"
            + "// Search\n"
            + "const searchInput = document.getElementById('search');\n"
            + "if (searchInput) {\n"
            + "  searchInput.addEventListener('input', () => {\n"
            + "    const q = searchInput.value.trim().toLowerCase();\n"
            + "    if (!q) { resetHighlight(); selected = null; return; }\n"
            + "    const matches = new Set();\n"
            + "    DATA.nodes.forEach(n => { if (n.id.toLowerCase().includes(q)) matches.add(n.index); });\n"
            + "    node.attr('opacity', n => matches.has(n.index) ? 1 : 0.1);\n"
            + "    link.attr('opacity', 0.05);\n"
            + "    label.style('display', n => matches.has(n.index) ? null : 'none');\n"
            + "  });\n"
            + "}\n"
            + "\n"
            + "// Theme toggle\n"
            + "document.getElementById('theme-toggle').addEventListener('click', () => {\n"
            + "  document.body.classList.toggle('dark');\n"
            + "  label.attr('fill', document.body.classList.contains('dark') ? '#ccc' : '#555');\n"
            + "});\n"
            + "\n"
            + "// Reset button\n"
            + "document.getElementById('reset-btn').addEventListener('click', () => {\n"
            + "  svg.transition().call(zoom.transform, d3.zoomIdentity);\n"
            + "  selected = null;\n"
            + "  resetHighlight();\n"
            + "  if (searchInput) searchInput.value = '';\n"
            + "});\n"
            + "\n"
            + "sim.on('tick', () => {\n"
            + "  link.attr('x1',d=>d.source.x).attr('y1',d=>d.source.y).attr('x2',d=>d.target.x).attr('y2',d=>d.target.y);\n"
            + "  node.attr('cx',d=>d.x).attr('cy',d=>d.y);\n"
            + "  label.attr('x',d=>d.x).attr('y',d=>d.y);\n"
            + "});\n"
            + "\n"
            + "function nodeRadius(d) { return 4 + (d.deg / Math.max(MAX_DEG, 1)) * 16; }\n"
            + "function nodeColor(d) {\n"
            + "  const t = d.deg / Math.max(MAX_DEG, 1);\n"
            + "  const r = Math.round(50 + t * 180);\n"
            + "  const g = Math.round(120 - t * 60);\n"
            + "  const b = Math.round(200 - t * 120);\n"
            + "  return `rgb(${r},${g},${b})`;\n"
            + "}\n"
            + "\n"
            + "function dragStart(ev, d) { if (!ev.active) sim.alphaTarget(0.3).restart(); d.fx = d.x; d.fy = d.y; }\n"
            + "function dragging(ev, d) { d.fx = ev.x; d.fy = ev.y; }\n"
            + "function dragEnd(ev, d) { if (!ev.active) sim.alphaTarget(0); d.fx = null; d.fy = null; }\n"
            + "})();\n";
    }

    /** Maps edge type codes to human-readable names. */
    private static String typeName(String type) {
        if (type == null) return "Unknown";
        switch (type) {
            case "f":  return "Friend";
            case "fs": return "Facebook";
            case "c":  return "Classmate";
            case "s":  return "Stranger";
            case "sg": return "Study Group";
            default:   return type;
        }
    }

    /** Maps edge type codes to CSS colors. */
    private static String typeColor(String type) {
        if (type == null) return "#999";
        switch (type) {
            case "f":  return "#4CAF50";
            case "fs": return "#2196F3";
            case "c":  return "#FF9800";
            case "s":  return "#9E9E9E";
            case "sg": return "#9C27B0";
            default:   return "#607D8B";
        }
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String escJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("<", "\\x3c")    // prevent </script> breakout (CWE-79)
                .replace(">", "\\x3e");
    }
}
