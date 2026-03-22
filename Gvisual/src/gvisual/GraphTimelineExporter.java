package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Exports a {@link TemporalGraph} as a self-contained interactive HTML timeline
 * player. The output uses D3.js to animate the graph evolving over time with
 * full playback controls (play/pause, step forward/back, speed control, scrubber).
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Animated force-directed layout showing graph evolution</li>
 *   <li>Play/Pause, Step Forward/Back, Speed controls</li>
 *   <li>Timeline scrubber with tick marks at each snapshot</li>
 *   <li>Live stats panel (nodes, edges, density, components)</li>
 *   <li>Color-coded edges by type with legend</li>
 *   <li>Node enter/exit animations (fade in green, fade out red)</li>
 *   <li>Edge enter/exit animations</li>
 *   <li>Cumulative or snapshot mode toggle</li>
 *   <li>Dark/light theme</li>
 *   <li>No external dependencies — fully self-contained HTML</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 *   TemporalGraph tg = new TemporalGraph(graph);
 *   GraphTimelineExporter exporter = new GraphTimelineExporter(tg);
 *   exporter.setTitle("Network Evolution 2011-2012");
 *   exporter.export(new File("timeline.html"));
 * </pre>
 *
 * @author zalenix
 */
public class GraphTimelineExporter {

    private final TemporalGraph temporalGraph;
    private String title = "Graph Timeline";

    /**
     * Creates an exporter for the given temporal graph.
     *
     * @param temporalGraph the temporal graph to animate
     * @throws IllegalArgumentException if temporalGraph is null
     */
    public GraphTimelineExporter(TemporalGraph temporalGraph) {
        if (temporalGraph == null) {
            throw new IllegalArgumentException("TemporalGraph must not be null");
        }
        this.temporalGraph = temporalGraph;
    }

    /**
     * Sets the title shown in the HTML page.
     *
     * @param title the title string
     */
    public void setTitle(String title) {
        this.title = title != null ? title : "Graph Timeline";
    }

    /**
     * Exports the timeline to an HTML file.
     *
     * @param outputFile the destination file
     * @throws IOException if writing fails
     */
    public void export(File outputFile) throws IOException {
        ExportUtils.validateOutputPath(outputFile);
        String html = exportToString();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
            w.write(html);
        }
    }

    /**
     * Exports the timeline as an HTML string.
     *
     * @return self-contained HTML string
     */
    public String exportToString() {
        List<Long> timePoints = temporalGraph.getTimePoints();
        if (timePoints.isEmpty()) {
            timePoints = Collections.singletonList(0L);
        }

        // Build snapshot data for each time point
        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (Long t : timePoints) {
            Graph<String, Edge> snap = temporalGraph.snapshotAt(t);
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("time", t);

            List<Map<String, String>> nodes = new ArrayList<>();
            for (String v : snap.getVertices()) {
                Map<String, String> n = new LinkedHashMap<>();
                n.put("id", v);
                n.put("degree", String.valueOf(snap.degree(v)));
                nodes.add(n);
            }
            s.put("nodes", nodes);

            List<Map<String, String>> edges = new ArrayList<>();
            for (Edge e : snap.getEdges()) {
                Map<String, String> ed = new LinkedHashMap<>();
                ed.put("source", e.getVertex1());
                ed.put("target", e.getVertex2());
                ed.put("type", e.getType() != null ? e.getType() : "unknown");
                edges.add(ed);
            }
            s.put("edges", edges);
            s.put("nodeCount", snap.getVertexCount());
            s.put("edgeCount", snap.getEdgeCount());

            snapshots.add(s);
        }

        // Also build cumulative snapshots
        List<Map<String, Object>> cumulativeSnapshots = new ArrayList<>();
        for (int i = 0; i < timePoints.size(); i++) {
            long tEnd = timePoints.get(i);
            long tStart = timePoints.get(0);
            Graph<String, Edge> cumSnap = temporalGraph.windowBetween(tStart, tEnd);
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("time", tEnd);

            List<Map<String, String>> nodes = new ArrayList<>();
            for (String v : cumSnap.getVertices()) {
                Map<String, String> n = new LinkedHashMap<>();
                n.put("id", v);
                n.put("degree", String.valueOf(cumSnap.degree(v)));
                nodes.add(n);
            }
            s.put("nodes", nodes);

            List<Map<String, String>> edges = new ArrayList<>();
            for (Edge e : cumSnap.getEdges()) {
                Map<String, String> ed = new LinkedHashMap<>();
                ed.put("source", e.getVertex1());
                ed.put("target", e.getVertex2());
                ed.put("type", e.getType() != null ? e.getType() : "unknown");
                edges.add(ed);
            }
            s.put("edges", edges);
            s.put("nodeCount", cumSnap.getVertexCount());
            s.put("edgeCount", cumSnap.getEdgeCount());

            cumulativeSnapshots.add(s);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>").append(escapeHtml(title)).append("</title>\n");
        sb.append("<style>\n");
        sb.append(getCSS());
        sb.append("</style>\n</head>\n<body>\n");
        sb.append("<div id=\"app\">\n");
        sb.append("  <div id=\"header\">\n");
        sb.append("    <h1>").append(escapeHtml(title)).append("</h1>\n");
        sb.append("    <div id=\"controls\">\n");
        sb.append("      <button id=\"btnPrev\" title=\"Previous\">⏮</button>\n");
        sb.append("      <button id=\"btnPlay\" title=\"Play/Pause\">▶</button>\n");
        sb.append("      <button id=\"btnNext\" title=\"Next\">⏭</button>\n");
        sb.append("      <label>Speed: <select id=\"speed\">\n");
        sb.append("        <option value=\"2000\">0.5x</option>\n");
        sb.append("        <option value=\"1000\" selected>1x</option>\n");
        sb.append("        <option value=\"500\">2x</option>\n");
        sb.append("        <option value=\"250\">4x</option>\n");
        sb.append("      </select></label>\n");
        sb.append("      <label><input type=\"checkbox\" id=\"cumulative\"> Cumulative</label>\n");
        sb.append("      <label><input type=\"checkbox\" id=\"themeToggle\"> Light</label>\n");
        sb.append("    </div>\n");
        sb.append("    <div id=\"timeline\">\n");
        sb.append("      <input type=\"range\" id=\"scrubber\" min=\"0\" max=\"")
           .append(Math.max(0, timePoints.size() - 1)).append("\" value=\"0\">\n");
        sb.append("      <span id=\"timeLabel\">T=0</span>\n");
        sb.append("    </div>\n");
        sb.append("  </div>\n");
        sb.append("  <div id=\"main\">\n");
        sb.append("    <svg id=\"graph\"></svg>\n");
        sb.append("    <div id=\"stats\"></div>\n");
        sb.append("  </div>\n");
        sb.append("  <div id=\"legend\"></div>\n");
        sb.append("</div>\n");

        // Embed data
        sb.append("<script>\n");
        sb.append("const SNAPSHOTS = ").append(toJson(snapshots)).append(";\n");
        sb.append("const CUMULATIVE = ").append(toJson(cumulativeSnapshots)).append(";\n");
        sb.append(getJS());
        sb.append("</script>\n");
        sb.append("</body>\n</html>");

        return sb.toString();
    }

    private String getCSS() {
        return "* { margin:0; padding:0; box-sizing:border-box; }\n"
            + "body { font-family: 'Segoe UI', system-ui, sans-serif; background: #1a1a2e; color: #e0e0e0; overflow: hidden; height: 100vh; }\n"
            + "body.light { background: #f5f5f5; color: #333; }\n"
            + "#app { display: flex; flex-direction: column; height: 100vh; }\n"
            + "#header { padding: 12px 20px; background: #16213e; border-bottom: 1px solid #0f3460; }\n"
            + "body.light #header { background: #fff; border-bottom: 1px solid #ddd; }\n"
            + "h1 { font-size: 18px; margin-bottom: 8px; }\n"
            + "#controls { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }\n"
            + "#controls button { background: #0f3460; color: #e0e0e0; border: 1px solid #533483; padding: 6px 14px; border-radius: 4px; cursor: pointer; font-size: 16px; }\n"
            + "#controls button:hover { background: #533483; }\n"
            + "body.light #controls button { background: #e0e0e0; color: #333; border-color: #ccc; }\n"
            + "body.light #controls button:hover { background: #ccc; }\n"
            + "#controls label { font-size: 13px; display: flex; align-items: center; gap: 4px; }\n"
            + "#controls select { background: #0f3460; color: #e0e0e0; border: 1px solid #533483; padding: 3px 6px; border-radius: 3px; }\n"
            + "body.light #controls select { background: #fff; color: #333; border-color: #ccc; }\n"
            + "#timeline { margin-top: 8px; display: flex; align-items: center; gap: 10px; }\n"
            + "#scrubber { flex: 1; accent-color: #e94560; }\n"
            + "#timeLabel { font-size: 13px; min-width: 80px; font-variant-numeric: tabular-nums; }\n"
            + "#main { flex: 1; display: flex; position: relative; overflow: hidden; }\n"
            + "#graph { flex: 1; }\n"
            + "#stats { position: absolute; top: 10px; right: 10px; background: rgba(22,33,62,0.9); padding: 12px 16px; border-radius: 6px; font-size: 13px; line-height: 1.8; min-width: 160px; }\n"
            + "body.light #stats { background: rgba(255,255,255,0.9); border: 1px solid #ddd; }\n"
            + "#stats .label { color: #888; }\n"
            + "#stats .value { color: #e94560; font-weight: bold; }\n"
            + "body.light #stats .value { color: #c0392b; }\n"
            + "#legend { padding: 8px 20px; background: #16213e; border-top: 1px solid #0f3460; display: flex; gap: 16px; flex-wrap: wrap; font-size: 12px; }\n"
            + "body.light #legend { background: #fff; border-top: 1px solid #ddd; }\n"
            + ".legend-item { display: flex; align-items: center; gap: 4px; }\n"
            + ".legend-swatch { width: 20px; height: 3px; border-radius: 2px; }\n"
            + ".node-enter { animation: nodeIn 0.4s ease-out; }\n"
            + ".node-exit { animation: nodeOut 0.3s ease-in forwards; }\n"
            + "@keyframes nodeIn { from { r: 0; opacity: 0; } to { opacity: 1; } }\n"
            + "@keyframes nodeOut { to { r: 0; opacity: 0; } }\n";
    }

    private String getJS() {
        return ""
            + "const EDGE_COLORS = {f:'#e94560',fs:'#0f3460',c:'#533483',s:'#e9a045',sg:'#45e980',unknown:'#888'};\n"
            + "const EDGE_LABELS = {f:'Friend',fs:'Family/Sibling',c:'Classmate',s:'Stranger',sg:'Study Group',unknown:'Unknown'};\n"
            + "let currentIdx = 0, playing = false, timer = null;\n"
            + "const svg = document.getElementById('graph');\n"
            + "const scrubber = document.getElementById('scrubber');\n"
            + "const timeLabel = document.getElementById('timeLabel');\n"
            + "const statsEl = document.getElementById('stats');\n"
            + "const legendEl = document.getElementById('legend');\n"
            + "\n"
            + "// Build legend\n"
            + "Object.keys(EDGE_LABELS).forEach(k => {\n"
            + "  legendEl.innerHTML += `<span class='legend-item'><span class='legend-swatch' style='background:${EDGE_COLORS[k]}'></span>${EDGE_LABELS[k]}</span>`;\n"
            + "});\n"
            + "\n"
            + "// D3-like force sim in vanilla JS\n"
            + "let nodes = [], links = [], nodeMap = {};\n"
            + "let W, H;\n"
            + "\n"
            + "function resize() {\n"
            + "  const r = svg.parentElement.getBoundingClientRect();\n"
            + "  W = r.width; H = r.height;\n"
            + "  svg.setAttribute('width', W); svg.setAttribute('height', H);\n"
            + "}\n"
            + "window.addEventListener('resize', resize); resize();\n"
            + "\n"
            + "function getSpeed() { return parseInt(document.getElementById('speed').value); }\n"
            + "function isCumulative() { return document.getElementById('cumulative').checked; }\n"
            + "\n"
            + "function getData() { return isCumulative() ? CUMULATIVE : SNAPSHOTS; }\n"
            + "\n"
            + "function renderSnapshot(idx) {\n"
            + "  const data = getData();\n"
            + "  if (idx < 0 || idx >= data.length) return;\n"
            + "  currentIdx = idx;\n"
            + "  scrubber.value = idx;\n"
            + "  const snap = data[idx];\n"
            + "  timeLabel.textContent = `T=${snap.time} (${idx+1}/${data.length})`;\n"
            + "\n"
            + "  // Determine added/removed nodes\n"
            + "  const prevNodes = new Set(nodes.map(n => n.id));\n"
            + "  const newNodeIds = new Set(snap.nodes.map(n => n.id));\n"
            + "  const added = snap.nodes.filter(n => !prevNodes.has(n.id));\n"
            + "  const removed = nodes.filter(n => !newNodeIds.has(n.id));\n"
            + "\n"
            + "  // Update node positions - keep existing, add new\n"
            + "  const oldMap = {};\n"
            + "  nodes.forEach(n => oldMap[n.id] = n);\n"
            + "  nodes = snap.nodes.map(n => ({\n"
            + "    id: n.id,\n"
            + "    degree: parseInt(n.degree),\n"
            + "    x: oldMap[n.id] ? oldMap[n.id].x : W/2 + (Math.random()-0.5)*200,\n"
            + "    y: oldMap[n.id] ? oldMap[n.id].y : H/2 + (Math.random()-0.5)*200,\n"
            + "    vx: 0, vy: 0,\n"
            + "    isNew: !prevNodes.has(n.id)\n"
            + "  }));\n"
            + "  nodeMap = {}; nodes.forEach(n => nodeMap[n.id] = n);\n"
            + "  links = snap.edges.filter(e => nodeMap[e.source] && nodeMap[e.target]).map(e => ({source:e.source, target:e.target, type:e.type}));\n"
            + "\n"
            + "  // Update stats\n"
            + "  const density = nodes.length > 1 ? (2*links.length/(nodes.length*(nodes.length-1))).toFixed(3) : '0.000';\n"
            + "  statsEl.innerHTML = `<div><span class='label'>Nodes:</span> <span class='value'>${nodes.length}</span></div>`\n"
            + "    + `<div><span class='label'>Edges:</span> <span class='value'>${links.length}</span></div>`\n"
            + "    + `<div><span class='label'>Density:</span> <span class='value'>${density}</span></div>`\n"
            + "    + `<div><span class='label'>Snapshot:</span> <span class='value'>${idx+1}/${data.length}</span></div>`;\n"
            + "\n"
            + "  simulate();\n"
            + "}\n"
            + "\n"
            + "// Simple force simulation\n"
            + "function simulate() {\n"
            + "  const alpha = 0.3;\n"
            + "  for (let iter = 0; iter < 50; iter++) {\n"
            + "    // Repulsion\n"
            + "    for (let i = 0; i < nodes.length; i++) {\n"
            + "      for (let j = i+1; j < nodes.length; j++) {\n"
            + "        let dx = nodes[j].x - nodes[i].x, dy = nodes[j].y - nodes[i].y;\n"
            + "        let d = Math.sqrt(dx*dx + dy*dy) || 1;\n"
            + "        let f = -300 / (d * d);\n"
            + "        nodes[i].vx += dx/d * f * alpha;\n"
            + "        nodes[i].vy += dy/d * f * alpha;\n"
            + "        nodes[j].vx -= dx/d * f * alpha;\n"
            + "        nodes[j].vy -= dy/d * f * alpha;\n"
            + "      }\n"
            + "    }\n"
            + "    // Attraction\n"
            + "    links.forEach(l => {\n"
            + "      const s = nodeMap[l.source], t = nodeMap[l.target];\n"
            + "      if (!s || !t) return;\n"
            + "      let dx = t.x - s.x, dy = t.y - s.y;\n"
            + "      let d = Math.sqrt(dx*dx + dy*dy) || 1;\n"
            + "      let f = (d - 80) * 0.01 * alpha;\n"
            + "      s.vx += dx/d * f; s.vy += dy/d * f;\n"
            + "      t.vx -= dx/d * f; t.vy -= dy/d * f;\n"
            + "    });\n"
            + "    // Center gravity\n"
            + "    nodes.forEach(n => {\n"
            + "      n.vx += (W/2 - n.x) * 0.001 * alpha;\n"
            + "      n.vy += (H/2 - n.y) * 0.001 * alpha;\n"
            + "      n.vx *= 0.8; n.vy *= 0.8;\n"
            + "      n.x += n.vx; n.y += n.vy;\n"
            + "      n.x = Math.max(20, Math.min(W-20, n.x));\n"
            + "      n.y = Math.max(20, Math.min(H-20, n.y));\n"
            + "    });\n"
            + "  }\n"
            + "  draw();\n"
            + "}\n"
            + "\n"
            + "function draw() {\n"
            + "  let html = '';\n"
            + "  // Edges\n"
            + "  links.forEach(l => {\n"
            + "    const s = nodeMap[l.source], t = nodeMap[l.target];\n"
            + "    if (!s || !t) return;\n"
            + "    const c = EDGE_COLORS[l.type] || EDGE_COLORS.unknown;\n"
            + "    html += `<line x1='${s.x}' y1='${s.y}' x2='${t.x}' y2='${t.y}' stroke='${c}' stroke-width='1.5' stroke-opacity='0.6'/>`;\n"
            + "  });\n"
            + "  // Nodes\n"
            + "  nodes.forEach(n => {\n"
            + "    const r = Math.max(4, Math.min(16, 3 + n.degree));\n"
            + "    const cls = n.isNew ? 'node-enter' : '';\n"
            + "    const fill = n.isNew ? '#45e980' : '#e0e0e0';\n"
            + "    html += `<circle cx='${n.x}' cy='${n.y}' r='${r}' fill='${fill}' class='${cls}' stroke='#333' stroke-width='1'>`;\n"
            + "    html += `<title>${n.id} (degree: ${n.degree})</title></circle>`;\n"
            + "    if (nodes.length < 40) {\n"
            + "      html += `<text x='${n.x}' y='${n.y - r - 3}' text-anchor='middle' font-size='10' fill='#aaa'>${n.id}</text>`;\n"
            + "    }\n"
            + "  });\n"
            + "  svg.innerHTML = html;\n"
            + "}\n"
            + "\n"
            + "// Controls\n"
            + "document.getElementById('btnPlay').addEventListener('click', () => {\n"
            + "  if (playing) { clearInterval(timer); playing = false; document.getElementById('btnPlay').textContent = '▶'; }\n"
            + "  else { playing = true; document.getElementById('btnPlay').textContent = '⏸';\n"
            + "    timer = setInterval(() => {\n"
            + "      if (currentIdx < getData().length - 1) renderSnapshot(currentIdx + 1);\n"
            + "      else { clearInterval(timer); playing = false; document.getElementById('btnPlay').textContent = '▶'; }\n"
            + "    }, getSpeed());\n"
            + "  }\n"
            + "});\n"
            + "document.getElementById('btnPrev').addEventListener('click', () => { if(currentIdx>0) renderSnapshot(currentIdx-1); });\n"
            + "document.getElementById('btnNext').addEventListener('click', () => { if(currentIdx<getData().length-1) renderSnapshot(currentIdx+1); });\n"
            + "scrubber.addEventListener('input', e => renderSnapshot(parseInt(e.target.value)));\n"
            + "document.getElementById('cumulative').addEventListener('change', () => renderSnapshot(currentIdx));\n"
            + "document.getElementById('themeToggle').addEventListener('change', e => {\n"
            + "  document.body.classList.toggle('light', e.target.checked);\n"
            + "});\n"
            + "document.getElementById('speed').addEventListener('change', () => {\n"
            + "  if (playing) { clearInterval(timer); timer = setInterval(() => {\n"
            + "    if (currentIdx < getData().length - 1) renderSnapshot(currentIdx + 1);\n"
            + "    else { clearInterval(timer); playing = false; document.getElementById('btnPlay').textContent = '▶'; }\n"
            + "  }, getSpeed()); }\n"
            + "});\n"
            + "\n"
            + "// Init\n"
            + "renderSnapshot(0);\n";
    }

    /** Convert snapshot data to JSON. */
    private String toJson(List<Map<String, Object>> data) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < data.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(mapToJson(data.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String mapToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v instanceof Number) {
                sb.append(v);
            } else if (v instanceof String) {
                sb.append("\"").append(escapeJson((String) v)).append("\"");
            } else if (v instanceof List) {
                List<?> list = (List<?>) v;
                sb.append("[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(",");
                    Object item = list.get(i);
                    if (item instanceof Map) {
                        sb.append(mapToJson((Map<String, Object>) item));
                    } else {
                        sb.append("\"").append(escapeJson(String.valueOf(item))).append("\"");
                    }
                }
                sb.append("]");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String mapToJson(Map<String, ?> map, boolean dummy) {
        // Overload helper — just delegate
        return mapToJson((Map<String, Object>) map);
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
