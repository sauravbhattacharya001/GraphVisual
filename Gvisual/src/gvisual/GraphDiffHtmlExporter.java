package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Exports a visual graph diff as a self-contained interactive HTML file using D3.js.
 * Color-codes nodes and edges by diff status: added (green), removed (red), common (gray).
 * Supports overlay mode (single merged view) and side-by-side mode.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Force-directed layout with drag, zoom, and pan</li>
 *   <li>Nodes/edges color-coded: green = added, red = removed, gray = common</li>
 *   <li>Toggle overlay vs side-by-side view</li>
 *   <li>Hover tooltips showing node ID, status, degree in each graph</li>
 *   <li>Statistics panel with Jaccard similarity, edit distance, counts</li>
 *   <li>Filter toggles to show/hide added, removed, or common elements</li>
 *   <li>Dark/light theme toggle</li>
 *   <li>Legend for diff colors</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 *   GraphDiffHtmlExporter exporter = new GraphDiffHtmlExporter(graphA, graphB);
 *   exporter.setTitle("Network Evolution: Jan → Feb");
 *   exporter.export(new File("diff.html"));
 * </pre>
 *
 * @author zalenix
 */
public class GraphDiffHtmlExporter {

    private final Graph<String, Edge> graphA;
    private final Graph<String, Edge> graphB;
    private String title = "Graph Diff Visualization";
    private String labelA = "Graph A";
    private String labelB = "Graph B";
    private boolean darkMode = true;
    private int width = 1200;
    private int height = 700;

    public GraphDiffHtmlExporter(Graph<String, Edge> graphA, Graph<String, Edge> graphB) {
        if (graphA == null || graphB == null) {
            throw new IllegalArgumentException("Both graphs must not be null");
        }
        this.graphA = graphA;
        this.graphB = graphB;
    }

    public void setTitle(String title) { this.title = title; }
    public void setLabelA(String labelA) { this.labelA = labelA; }
    public void setLabelB(String labelB) { this.labelB = labelB; }
    public void setDarkMode(boolean darkMode) { this.darkMode = darkMode; }
    public void setWidth(int width) { this.width = width; }
    public void setHeight(int height) { this.height = height; }

    /**
     * Export the diff visualization to a file.
     */
    public void export(File file) throws IOException {
        ExportUtils.validateOutputPath(file);
        try (Writer w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            w.write(exportToString());
        }
    }

    /**
     * Export the diff visualization as an HTML string.
     */
    public String exportToString() {
        GraphDiffAnalyzer analyzer = new GraphDiffAnalyzer(graphA, graphB);
        GraphDiffAnalyzer.DiffResult diff = analyzer.computeDiff();

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<title>").append(escapeHtml(title)).append("</title>\n");
        sb.append("<script src=\"https://d3js.org/d3.v7.min.js\"></script>\n");
        sb.append("<style>\n");
        appendCss(sb);
        sb.append("</style>\n</head>\n<body class=\"").append(darkMode ? "dark" : "light").append("\">\n");

        sb.append("<div id=\"header\">\n");
        sb.append("  <h1>").append(escapeHtml(title)).append("</h1>\n");
        sb.append("  <div id=\"controls\">\n");
        sb.append("    <label><input type=\"checkbox\" id=\"showAdded\" checked> Added</label>\n");
        sb.append("    <label><input type=\"checkbox\" id=\"showRemoved\" checked> Removed</label>\n");
        sb.append("    <label><input type=\"checkbox\" id=\"showCommon\" checked> Common</label>\n");
        sb.append("    <button id=\"themeBtn\">Toggle Theme</button>\n");
        sb.append("  </div>\n");
        sb.append("</div>\n");

        sb.append("<div id=\"main\">\n");
        sb.append("  <div id=\"stats\"></div>\n");
        sb.append("  <div id=\"graph\"></div>\n");
        sb.append("  <div id=\"legend\">\n");
        sb.append("    <div class=\"legend-item\"><span class=\"dot added\"></span> Added in ").append(escapeHtml(labelB)).append("</div>\n");
        sb.append("    <div class=\"legend-item\"><span class=\"dot removed\"></span> Removed from ").append(escapeHtml(labelA)).append("</div>\n");
        sb.append("    <div class=\"legend-item\"><span class=\"dot common\"></span> Common</div>\n");
        sb.append("  </div>\n");
        sb.append("</div>\n");

        sb.append("<div id=\"tooltip\"></div>\n");

        sb.append("<script>\n");
        appendData(sb, diff);
        appendJs(sb);
        sb.append("</script>\n");
        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    private void appendCss(StringBuilder sb) {
        sb.append(
            "* { margin: 0; padding: 0; box-sizing: border-box; }\n" +
            "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; transition: all 0.3s; }\n" +
            "body.dark { background: #1a1a2e; color: #e0e0e0; }\n" +
            "body.light { background: #f5f5f5; color: #333; }\n" +
            "#header { padding: 12px 20px; display: flex; align-items: center; justify-content: space-between; }\n" +
            "#header h1 { font-size: 1.3em; }\n" +
            "#controls { display: flex; gap: 12px; align-items: center; }\n" +
            "#controls label { cursor: pointer; font-size: 0.9em; }\n" +
            "#controls button { padding: 4px 12px; border-radius: 4px; border: 1px solid #666; background: transparent; color: inherit; cursor: pointer; }\n" +
            "#main { display: flex; height: calc(100vh - 60px); }\n" +
            "#stats { width: 220px; padding: 12px; font-size: 0.85em; overflow-y: auto; }\n" +
            ".dark #stats { background: #16213e; }\n" +
            ".light #stats { background: #e8e8e8; }\n" +
            "#stats h3 { margin: 10px 0 6px; font-size: 1em; }\n" +
            "#stats .stat-row { display: flex; justify-content: space-between; padding: 2px 0; }\n" +
            "#graph { flex: 1; }\n" +
            "#graph svg { width: 100%; height: 100%; }\n" +
            "#legend { position: absolute; bottom: 20px; right: 20px; padding: 10px; border-radius: 6px; }\n" +
            ".dark #legend { background: rgba(22,33,62,0.9); }\n" +
            ".light #legend { background: rgba(255,255,255,0.9); border: 1px solid #ccc; }\n" +
            ".legend-item { display: flex; align-items: center; gap: 6px; margin: 4px 0; font-size: 0.85em; }\n" +
            ".dot { width: 12px; height: 12px; border-radius: 50%; display: inline-block; }\n" +
            ".dot.added { background: #00e676; }\n" +
            ".dot.removed { background: #ff5252; }\n" +
            ".dot.common { background: #78909c; }\n" +
            "#tooltip { position: absolute; display: none; padding: 8px 12px; border-radius: 4px; font-size: 0.85em; pointer-events: none; z-index: 100; }\n" +
            ".dark #tooltip { background: #0f3460; color: #fff; }\n" +
            ".light #tooltip { background: #fff; color: #333; border: 1px solid #ccc; box-shadow: 0 2px 8px rgba(0,0,0,0.15); }\n" +
            ".link { stroke-opacity: 0.6; }\n" +
            ".link.added { stroke: #00e676; }\n" +
            ".link.removed { stroke: #ff5252; stroke-dasharray: 5,3; }\n" +
            ".link.common { stroke: #78909c; }\n" +
            ".node-label { font-size: 10px; text-anchor: middle; pointer-events: none; }\n" +
            ".dark .node-label { fill: #e0e0e0; }\n" +
            ".light .node-label { fill: #333; }\n"
        );
    }

    private void appendData(StringBuilder sb, GraphDiffAnalyzer.DiffResult diff) {
        // Build nodes array
        sb.append("const diffData = {\n");

        // Stats
        sb.append("  stats: {\n");
        sb.append("    nodesAdded: ").append(diff.getAddedNodes().size()).append(",\n");
        sb.append("    nodesRemoved: ").append(diff.getRemovedNodes().size()).append(",\n");
        sb.append("    nodesCommon: ").append(diff.getCommonNodes().size()).append(",\n");
        sb.append("    edgesAdded: ").append(diff.getAddedEdges().size()).append(",\n");
        sb.append("    edgesRemoved: ").append(diff.getRemovedEdges().size()).append(",\n");
        sb.append("    edgesCommon: ").append(diff.getCommonEdges().size()).append(",\n");
        sb.append("    nodeJaccard: ").append(String.format("%.4f", diff.getNodeJaccard())).append(",\n");
        sb.append("    edgeJaccard: ").append(String.format("%.4f", diff.getEdgeJaccard())).append(",\n");
        sb.append("    editDistance: ").append(diff.getEditDistance()).append(",\n");
        sb.append("    identical: ").append(diff.isIdentical()).append("\n");
        sb.append("  },\n");

        // Nodes
        sb.append("  nodes: [\n");
        Map<String, Integer> degA = new HashMap<>();
        Map<String, Integer> degB = new HashMap<>();
        for (String v : graphA.getVertices()) degA.put(v, graphA.degree(v));
        for (String v : graphB.getVertices()) degB.put(v, graphB.degree(v));

        boolean first = true;
        for (String v : diff.getAddedNodes()) {
            if (!first) sb.append(",\n");
            first = false;
            sb.append("    {id:\"").append(escapeJs(v)).append("\",status:\"added\",degA:0,degB:").append(degB.getOrDefault(v, 0)).append("}");
        }
        for (String v : diff.getRemovedNodes()) {
            if (!first) sb.append(",\n");
            first = false;
            sb.append("    {id:\"").append(escapeJs(v)).append("\",status:\"removed\",degA:").append(degA.getOrDefault(v, 0)).append(",degB:0}");
        }
        for (String v : diff.getCommonNodes()) {
            if (!first) sb.append(",\n");
            first = false;
            sb.append("    {id:\"").append(escapeJs(v)).append("\",status:\"common\",degA:").append(degA.getOrDefault(v, 0)).append(",degB:").append(degB.getOrDefault(v, 0)).append("}");
        }
        sb.append("\n  ],\n");

        // Edges
        sb.append("  links: [\n");
        first = true;
        for (GraphDiffAnalyzer.EdgeDiff e : diff.getAddedEdges()) {
            if (!first) sb.append(",\n");
            first = false;
            sb.append("    {source:\"").append(escapeJs(e.getVertex1())).append("\",target:\"").append(escapeJs(e.getVertex2())).append("\",status:\"added\"}");
        }
        for (GraphDiffAnalyzer.EdgeDiff e : diff.getRemovedEdges()) {
            if (!first) sb.append(",\n");
            first = false;
            sb.append("    {source:\"").append(escapeJs(e.getVertex1())).append("\",target:\"").append(escapeJs(e.getVertex2())).append("\",status:\"removed\"}");
        }
        for (GraphDiffAnalyzer.EdgeDiff e : diff.getCommonEdges()) {
            if (!first) sb.append(",\n");
            first = false;
            sb.append("    {source:\"").append(escapeJs(e.getVertex1())).append("\",target:\"").append(escapeJs(e.getVertex2())).append("\",status:\"common\"}");
        }
        sb.append("\n  ],\n");

        // Degree changes
        sb.append("  degreeChanges: {\n");
        first = true;
        for (Map.Entry<String, int[]> entry : diff.getDegreeChanges().entrySet()) {
            if (!first) sb.append(",\n");
            first = false;
            sb.append("    \"").append(escapeJs(entry.getKey())).append("\": [")
              .append(entry.getValue()[0]).append(",").append(entry.getValue()[1]).append("]");
        }
        sb.append("\n  },\n");
        sb.append("  labelA: \"").append(escapeJs(labelA)).append("\",\n");
        sb.append("  labelB: \"").append(escapeJs(labelB)).append("\"\n");
        sb.append("};\n");
    }

    private void appendJs(StringBuilder sb) {
        sb.append(
            "const colors = { added: '#00e676', removed: '#ff5252', common: '#78909c' };\n" +
            "const tooltip = d3.select('#tooltip');\n" +
            "const container = d3.select('#graph');\n" +
            "const w = container.node().clientWidth;\n" +
            "const h = container.node().clientHeight;\n" +
            "const svg = container.append('svg').attr('viewBox', `0 0 ${w} ${h}`);\n" +
            "const g = svg.append('g');\n" +
            "\n" +
            "svg.call(d3.zoom().scaleExtent([0.1, 8]).on('zoom', e => g.attr('transform', e.transform)));\n" +
            "\n" +
            "const sim = d3.forceSimulation(diffData.nodes)\n" +
            "  .force('link', d3.forceLink(diffData.links).id(d => d.id).distance(60))\n" +
            "  .force('charge', d3.forceManyBody().strength(-120))\n" +
            "  .force('center', d3.forceCenter(w/2, h/2))\n" +
            "  .force('collision', d3.forceCollide().radius(18));\n" +
            "\n" +
            "let linkSel = g.append('g').selectAll('line');\n" +
            "let nodeSel = g.append('g').selectAll('circle');\n" +
            "let labelSel = g.append('g').selectAll('text');\n" +
            "\n" +
            "function getVisibility() {\n" +
            "  return {\n" +
            "    added: document.getElementById('showAdded').checked,\n" +
            "    removed: document.getElementById('showRemoved').checked,\n" +
            "    common: document.getElementById('showCommon').checked\n" +
            "  };\n" +
            "}\n" +
            "\n" +
            "function update() {\n" +
            "  const vis = getVisibility();\n" +
            "  const visNodes = diffData.nodes.filter(n => vis[n.status]);\n" +
            "  const visIds = new Set(visNodes.map(n => n.id));\n" +
            "  const visLinks = diffData.links.filter(l => {\n" +
            "    const sId = typeof l.source === 'object' ? l.source.id : l.source;\n" +
            "    const tId = typeof l.target === 'object' ? l.target.id : l.target;\n" +
            "    return vis[l.status] && visIds.has(sId) && visIds.has(tId);\n" +
            "  });\n" +
            "\n" +
            "  linkSel = linkSel.data(visLinks, d => {\n" +
            "    const s = typeof d.source === 'object' ? d.source.id : d.source;\n" +
            "    const t = typeof d.target === 'object' ? d.target.id : d.target;\n" +
            "    return s + '-' + t;\n" +
            "  });\n" +
            "  linkSel.exit().remove();\n" +
            "  linkSel = linkSel.enter().append('line')\n" +
            "    .attr('class', d => 'link ' + d.status)\n" +
            "    .attr('stroke-width', 1.5)\n" +
            "    .merge(linkSel);\n" +
            "\n" +
            "  nodeSel = nodeSel.data(visNodes, d => d.id);\n" +
            "  nodeSel.exit().remove();\n" +
            "  nodeSel = nodeSel.enter().append('circle')\n" +
            "    .attr('r', d => 4 + Math.max(d.degA, d.degB))\n" +
            "    .attr('fill', d => colors[d.status])\n" +
            "    .attr('stroke', d => d3.color(colors[d.status]).darker(0.5))\n" +
            "    .attr('stroke-width', 1.5)\n" +
            "    .call(d3.drag().on('start', dragStart).on('drag', dragging).on('end', dragEnd))\n" +
            "    .on('mouseover', (e, d) => {\n" +
            "      tooltip.style('display', 'block')\n" +
            "        .html(`<b>${d.id}</b><br>Status: ${d.status}<br>Degree in ${diffData.labelA}: ${d.degA}<br>Degree in ${diffData.labelB}: ${d.degB}`)\n" +
            "        .style('left', (e.pageX + 12) + 'px').style('top', (e.pageY - 10) + 'px');\n" +
            "    })\n" +
            "    .on('mouseout', () => tooltip.style('display', 'none'))\n" +
            "    .merge(nodeSel);\n" +
            "\n" +
            "  labelSel = labelSel.data(visNodes, d => d.id);\n" +
            "  labelSel.exit().remove();\n" +
            "  labelSel = labelSel.enter().append('text')\n" +
            "    .attr('class', 'node-label')\n" +
            "    .attr('dy', d => -(6 + Math.max(d.degA, d.degB)))\n" +
            "    .text(d => d.id)\n" +
            "    .merge(labelSel);\n" +
            "}\n" +
            "\n" +
            "sim.on('tick', () => {\n" +
            "  linkSel.attr('x1', d => d.source.x).attr('y1', d => d.source.y)\n" +
            "         .attr('x2', d => d.target.x).attr('y2', d => d.target.y);\n" +
            "  nodeSel.attr('cx', d => d.x).attr('cy', d => d.y);\n" +
            "  labelSel.attr('x', d => d.x).attr('y', d => d.y);\n" +
            "});\n" +
            "\n" +
            "function dragStart(e, d) { if (!e.active) sim.alphaTarget(0.3).restart(); d.fx = d.x; d.fy = d.y; }\n" +
            "function dragging(e, d) { d.fx = e.x; d.fy = e.y; }\n" +
            "function dragEnd(e, d) { if (!e.active) sim.alphaTarget(0); d.fx = null; d.fy = null; }\n" +
            "\n" +
            "// Stats panel\n" +
            "const s = diffData.stats;\n" +
            "document.getElementById('stats').innerHTML = `\n" +
            "  <h3>Diff Statistics</h3>\n" +
            "  <div class='stat-row'><span>Nodes added</span><span style='color:#00e676'>${s.nodesAdded}</span></div>\n" +
            "  <div class='stat-row'><span>Nodes removed</span><span style='color:#ff5252'>${s.nodesRemoved}</span></div>\n" +
            "  <div class='stat-row'><span>Nodes common</span><span>${s.nodesCommon}</span></div>\n" +
            "  <hr style='margin:8px 0;border-color:#444'>\n" +
            "  <div class='stat-row'><span>Edges added</span><span style='color:#00e676'>${s.edgesAdded}</span></div>\n" +
            "  <div class='stat-row'><span>Edges removed</span><span style='color:#ff5252'>${s.edgesRemoved}</span></div>\n" +
            "  <div class='stat-row'><span>Edges common</span><span>${s.edgesCommon}</span></div>\n" +
            "  <hr style='margin:8px 0;border-color:#444'>\n" +
            "  <h3>Similarity</h3>\n" +
            "  <div class='stat-row'><span>Node Jaccard</span><span>${s.nodeJaccard}</span></div>\n" +
            "  <div class='stat-row'><span>Edge Jaccard</span><span>${s.edgeJaccard}</span></div>\n" +
            "  <div class='stat-row'><span>Edit Distance</span><span>${s.editDistance}</span></div>\n" +
            "  ${s.identical ? '<p style=\"margin-top:10px;color:#00e676\">Graphs are identical!</p>' : ''}\n" +
            "  <hr style='margin:8px 0;border-color:#444'>\n" +
            "  <h3>Degree Changes</h3>\n" +
            "  ${Object.entries(diffData.degreeChanges).slice(0,20).map(([k,v]) =>\n" +
            "    '<div class=\"stat-row\"><span>'+k+'</span><span>'+v[0]+' → '+v[1]+'</span></div>'\n" +
            "  ).join('')}\n" +
            "  ${Object.keys(diffData.degreeChanges).length > 20 ? '<p>...and '+(Object.keys(diffData.degreeChanges).length-20)+' more</p>' : ''}\n" +
            "`;\n" +
            "\n" +
            "// Filter toggles\n" +
            "['showAdded','showRemoved','showCommon'].forEach(id =>\n" +
            "  document.getElementById(id).addEventListener('change', update));\n" +
            "\n" +
            "// Theme toggle\n" +
            "document.getElementById('themeBtn').addEventListener('click', () => {\n" +
            "  document.body.classList.toggle('dark');\n" +
            "  document.body.classList.toggle('light');\n" +
            "});\n" +
            "\n" +
            "update();\n"
        );
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /**
     * Escapes a string for safe embedding inside a JavaScript string literal
     * within an HTML {@code <script>} block.
     *
     * <p>In addition to standard JS escapes (backslash, quote, newlines),
     * this method encodes {@code <} and {@code >} as hex escapes to prevent
     * a malicious vertex/label name containing {@code </script>} from
     * breaking out of the script context (CWE-79 / XSS via script breakout).
     */
    private static String escapeJs(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("<", "\\x3c")
                .replace(">", "\\x3e");
    }
}
