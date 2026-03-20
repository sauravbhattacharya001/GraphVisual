package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Generates a self-contained interactive HTML page with radar/spider charts
 * for comparing node centrality profiles. Uses Chart.js (CDN-free, embedded)
 * for radar chart rendering.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Radar chart showing degree, betweenness, and closeness centrality</li>
 *   <li>Select up to 8 nodes for side-by-side comparison</li>
 *   <li>Sortable ranking table with all centrality metrics</li>
 *   <li>Click table rows to toggle nodes on the chart</li>
 *   <li>Top-N filter (top 5, 10, 20, all)</li>
 *   <li>Dark/light theme toggle</li>
 *   <li>Export chart data as JSON</li>
 *   <li>Responsive design</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 *   CentralityRadarExporter exporter = new CentralityRadarExporter(graph);
 *   exporter.export(new File("centrality-radar.html"));
 * </pre>
 *
 * @author zalenix
 */
public class CentralityRadarExporter {

    private final Graph<String, edge> graph;
    private String title = "Centrality Radar Chart";

    public CentralityRadarExporter(Graph<String, edge> graph) {
        if (graph == null) throw new IllegalArgumentException("Graph must not be null");
        this.graph = graph;
    }

    public void setTitle(String title) { this.title = title; }

    /**
     * Exports the centrality radar chart to an HTML file.
     */
    public void export(File file) throws IOException {
        ExportUtils.validateOutputPath(file);
        String html = exportToString();
        try (Writer w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            w.write(html);
        }
    }

    /**
     * Returns the full HTML as a string.
     */
    public String exportToString() {
        NodeCentralityAnalyzer analyzer = new NodeCentralityAnalyzer(graph);
        List<NodeCentralityAnalyzer.CentralityResult> results = analyzer.analyze();

        StringBuilder nodesJson = new StringBuilder("[");
        boolean first = true;
        for (NodeCentralityAnalyzer.CentralityResult r : results) {
            if (!first) nodesJson.append(",");
            first = false;
            nodesJson.append(String.format(Locale.US,
                "{\"id\":\"%s\",\"degree\":%d,\"dc\":%.6f,\"bc\":%.6f,\"cc\":%.6f,\"combined\":%.6f}",
                escapeJson(r.getNodeId()), r.getDegree(),
                r.getDegreeCentrality(), r.getBetweennessCentrality(),
                r.getClosenessCentrality(), r.getCombinedScore()));
        }
        nodesJson.append("]");

        return buildHtml(nodesJson.toString(), results.size());
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private String buildHtml(String nodesJson, int totalNodes) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>").append(title).append("</title>\n");
        sb.append("<style>\n");
        sb.append(getCSS());
        sb.append("</style>\n</head>\n<body>\n");
        sb.append("<div class=\"container\">\n");
        sb.append("<header>\n");
        sb.append("<h1>🎯 ").append(title).append("</h1>\n");
        sb.append("<p class=\"subtitle\">Compare node centrality profiles across degree, betweenness, and closeness metrics</p>\n");
        sb.append("<div class=\"controls\">\n");
        sb.append("<select id=\"topN\"><option value=\"5\">Top 5</option><option value=\"10\" selected>Top 10</option>");
        sb.append("<option value=\"20\">Top 20</option><option value=\"0\">All (").append(totalNodes).append(")</option></select>\n");
        sb.append("<select id=\"sortBy\"><option value=\"combined\">Sort: Combined</option>");
        sb.append("<option value=\"dc\">Sort: Degree</option><option value=\"bc\">Sort: Betweenness</option>");
        sb.append("<option value=\"cc\">Sort: Closeness</option></select>\n");
        sb.append("<button id=\"themeBtn\" onclick=\"toggleTheme()\">🌙 Dark</button>\n");
        sb.append("<button onclick=\"exportJson()\">📥 Export JSON</button>\n");
        sb.append("<button onclick=\"clearSelection()\">🗑️ Clear</button>\n");
        sb.append("</div>\n</header>\n");
        sb.append("<div class=\"main\">\n");
        sb.append("<div class=\"chart-panel\">\n");
        sb.append("<canvas id=\"radar\" width=\"500\" height=\"500\"></canvas>\n");
        sb.append("<p class=\"hint\">Click table rows to add/remove nodes (max 8)</p>\n");
        sb.append("</div>\n");
        sb.append("<div class=\"table-panel\">\n");
        sb.append("<input type=\"text\" id=\"search\" placeholder=\"🔍 Search nodes...\">\n");
        sb.append("<div class=\"table-wrap\"><table id=\"rankTable\">\n");
        sb.append("<thead><tr><th>#</th><th>Node</th><th>Deg</th><th>Degree C.</th><th>Between. C.</th><th>Close. C.</th><th>Combined</th></tr></thead>\n");
        sb.append("<tbody></tbody></table></div>\n");
        sb.append("</div>\n</div>\n");
        sb.append("<div class=\"stats\" id=\"stats\"></div>\n");
        sb.append("</div>\n");
        sb.append("<script>\n");
        sb.append("const DATA=").append(nodesJson).append(";\n");
        sb.append(getJS());
        sb.append("</script>\n</body>\n</html>");
        return sb.toString();
    }

    private String getCSS() {
        return "*{margin:0;padding:0;box-sizing:border-box}\n"
            + ":root{--bg:#f5f7fa;--fg:#1a1a2e;--card:#fff;--border:#e0e0e0;--accent:#6366f1;--accent2:#ec4899;--row-hover:#f0f0ff;--selected:#e8e8ff}\n"
            + "body.dark{--bg:#0f172a;--fg:#e2e8f0;--card:#1e293b;--border:#334155;--row-hover:#1e293b;--selected:#312e81}\n"
            + "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:var(--bg);color:var(--fg);transition:all .3s}\n"
            + ".container{max-width:1200px;margin:0 auto;padding:20px}\n"
            + "header{text-align:center;margin-bottom:20px}\n"
            + "h1{font-size:1.8rem;margin-bottom:4px}\n"
            + ".subtitle{opacity:.7;margin-bottom:12px}\n"
            + ".controls{display:flex;gap:8px;justify-content:center;flex-wrap:wrap}\n"
            + ".controls select,.controls button,.controls input{padding:6px 12px;border-radius:6px;border:1px solid var(--border);background:var(--card);color:var(--fg);cursor:pointer;font-size:.85rem}\n"
            + ".controls button:hover{background:var(--accent);color:#fff}\n"
            + ".main{display:grid;grid-template-columns:1fr 1fr;gap:20px;margin-bottom:20px}\n"
            + "@media(max-width:800px){.main{grid-template-columns:1fr}}\n"
            + ".chart-panel{background:var(--card);border-radius:12px;padding:20px;border:1px solid var(--border);text-align:center}\n"
            + ".chart-panel canvas{max-width:100%;height:auto}\n"
            + ".hint{font-size:.8rem;opacity:.5;margin-top:8px}\n"
            + ".table-panel{background:var(--card);border-radius:12px;padding:16px;border:1px solid var(--border);overflow:hidden;display:flex;flex-direction:column}\n"
            + "#search{width:100%;padding:8px 12px;border-radius:6px;border:1px solid var(--border);background:var(--bg);color:var(--fg);margin-bottom:8px;font-size:.85rem}\n"
            + ".table-wrap{overflow-y:auto;max-height:420px;flex:1}\n"
            + "table{width:100%;border-collapse:collapse;font-size:.8rem}\n"
            + "th{position:sticky;top:0;background:var(--card);padding:6px 4px;text-align:left;border-bottom:2px solid var(--border);cursor:pointer;user-select:none}\n"
            + "th:hover{color:var(--accent)}\n"
            + "td{padding:5px 4px;border-bottom:1px solid var(--border)}\n"
            + "tr:hover td{background:var(--row-hover)}\n"
            + "tr.selected td{background:var(--selected);font-weight:600}\n"
            + ".stats{background:var(--card);border-radius:12px;padding:16px;border:1px solid var(--border);display:flex;gap:20px;flex-wrap:wrap;justify-content:center}\n"
            + ".stat{text-align:center}.stat .val{font-size:1.4rem;font-weight:700;color:var(--accent)}.stat .lbl{font-size:.75rem;opacity:.6}\n"
            + ".color-dot{display:inline-block;width:10px;height:10px;border-radius:50%;margin-right:4px;vertical-align:middle}\n";
    }

    private String getJS() {
        return "const COLORS=['#6366f1','#ec4899','#14b8a6','#f59e0b','#ef4444','#8b5cf6','#06b6d4','#84cc16'];\n"
            + "let selected=[],dark=false,sortKey='combined',sortAsc=false,topN=10;\n"
            + "function toggleTheme(){dark=!dark;document.body.classList.toggle('dark');document.getElementById('themeBtn').textContent=dark?'☀️ Light':'🌙 Dark';drawChart()}\n"
            + "function getFiltered(){let d=[...DATA];d.sort((a,b)=>sortAsc?a[sortKey]-b[sortKey]:b[sortKey]-a[sortKey]);if(topN>0)d=d.slice(0,topN);let q=document.getElementById('search').value.toLowerCase();if(q)d=d.filter(n=>n.id.toLowerCase().includes(q));return d}\n"
            + "function renderTable(){let tb=document.querySelector('#rankTable tbody');tb.innerHTML='';let d=getFiltered();\n"
            + "d.forEach((n,i)=>{let tr=document.createElement('tr');let si=selected.indexOf(n.id);\n"
            + "if(si>=0){tr.classList.add('selected');tr.innerHTML=`<td><span class=\"color-dot\" style=\"background:${COLORS[si%8]}\"></span>${i+1}</td>`}else{tr.innerHTML=`<td>${i+1}</td>`}\n"
            + "tr.innerHTML+=`<td>${n.id}</td><td>${n.degree}</td><td>${n.dc.toFixed(4)}</td><td>${n.bc.toFixed(4)}</td><td>${n.cc.toFixed(4)}</td><td>${n.combined.toFixed(4)}</td>`;\n"
            + "tr.style.cursor='pointer';tr.onclick=()=>toggleNode(n.id);tb.appendChild(tr)})}\n"
            + "function toggleNode(id){let i=selected.indexOf(id);if(i>=0)selected.splice(i,1);else if(selected.length<8)selected.push(id);renderTable();drawChart()}\n"
            + "function clearSelection(){selected=[];renderTable();drawChart()}\n"
            + "function drawChart(){let cv=document.getElementById('radar'),ctx=cv.getContext('2d');let W=cv.width,H=cv.height,cx=W/2,cy=H/2,R=Math.min(W,H)/2-60;\n"
            + "ctx.clearRect(0,0,W,H);let labels=['Degree','Betweenness','Closeness'];let angles=labels.map((_,i)=>-Math.PI/2+i*2*Math.PI/3);\n"
            + "let gridColor=dark?'rgba(255,255,255,0.1)':'rgba(0,0,0,0.1)';let textColor=dark?'#e2e8f0':'#1a1a2e';\n"
            + "for(let s=1;s<=5;s++){ctx.beginPath();let r=R*s/5;angles.forEach((a,i)=>{let x=cx+r*Math.cos(a),y=cy+r*Math.sin(a);i===0?ctx.moveTo(x,y):ctx.lineTo(x,y)});ctx.closePath();ctx.strokeStyle=gridColor;ctx.stroke();\n"
            + "if(s<5){ctx.fillStyle=textColor;ctx.font='10px sans-serif';ctx.fillText((s/5).toFixed(1),cx+4,cy-R*s/5+12)}}\n"
            + "angles.forEach(a=>{ctx.beginPath();ctx.moveTo(cx,cy);ctx.lineTo(cx+R*Math.cos(a),cy+R*Math.sin(a));ctx.strokeStyle=gridColor;ctx.stroke()});\n"
            + "ctx.font='bold 13px sans-serif';ctx.fillStyle=textColor;ctx.textAlign='center';\n"
            + "labels.forEach((l,i)=>{let a=angles[i],x=cx+(R+30)*Math.cos(a),y=cy+(R+30)*Math.sin(a);ctx.fillText(l,x,y+4)});\n"
            + "selected.forEach((id,si)=>{let n=DATA.find(d=>d.id===id);if(!n)return;let vals=[n.dc,n.bc,n.cc];ctx.beginPath();\n"
            + "vals.forEach((v,i)=>{let x=cx+R*v*Math.cos(angles[i]),y=cy+R*v*Math.sin(angles[i]);i===0?ctx.moveTo(x,y):ctx.lineTo(x,y)});\n"
            + "ctx.closePath();let c=COLORS[si%8];ctx.fillStyle=c+'33';ctx.fill();ctx.strokeStyle=c;ctx.lineWidth=2;ctx.stroke();\n"
            + "vals.forEach((v,i)=>{let x=cx+R*v*Math.cos(angles[i]),y=cy+R*v*Math.sin(angles[i]);ctx.beginPath();ctx.arc(x,y,4,0,Math.PI*2);ctx.fillStyle=c;ctx.fill()})});\n"
            + "if(selected.length===0){ctx.fillStyle=textColor;ctx.font='14px sans-serif';ctx.textAlign='center';ctx.fillText('Click rows in the table to compare nodes',cx,cy)}}\n"
            + "function renderStats(){let s=document.getElementById('stats');if(!DATA.length){s.innerHTML='<p>No data</p>';return}\n"
            + "let maxDC=DATA.reduce((a,b)=>a.dc>b.dc?a:b);let maxBC=DATA.reduce((a,b)=>a.bc>b.bc?a:b);let maxCC=DATA.reduce((a,b)=>a.cc>b.cc?a:b);let maxC=DATA.reduce((a,b)=>a.combined>b.combined?a:b);\n"
            + "let avgDeg=(DATA.reduce((s,n)=>s+n.degree,0)/DATA.length).toFixed(1);\n"
            + "s.innerHTML=`<div class=\"stat\"><div class=\"val\">${DATA.length}</div><div class=\"lbl\">Nodes</div></div>`\n"
            + "+`<div class=\"stat\"><div class=\"val\">${avgDeg}</div><div class=\"lbl\">Avg Degree</div></div>`\n"
            + "+`<div class=\"stat\"><div class=\"val\">${maxDC.id}</div><div class=\"lbl\">Highest Degree C.</div></div>`\n"
            + "+`<div class=\"stat\"><div class=\"val\">${maxBC.id}</div><div class=\"lbl\">Highest Betweenness</div></div>`\n"
            + "+`<div class=\"stat\"><div class=\"val\">${maxCC.id}</div><div class=\"lbl\">Highest Closeness</div></div>`\n"
            + "+`<div class=\"stat\"><div class=\"val\">${maxC.id}</div><div class=\"lbl\">Most Central Overall</div></div>`}\n"
            + "function exportJson(){let d=selected.length?DATA.filter(n=>selected.includes(n.id)):DATA;\n"
            + "let blob=new Blob([JSON.stringify(d,null,2)],{type:'application/json'});let a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='centrality-data.json';a.click()}\n"
            + "document.getElementById('topN').onchange=e=>{topN=+e.target.value;renderTable();drawChart()};\n"
            + "document.getElementById('sortBy').onchange=e=>{sortKey=e.target.value;renderTable()};\n"
            + "document.getElementById('search').oninput=()=>renderTable();\n"
            + "document.querySelectorAll('#rankTable th').forEach((th,i)=>{th.onclick=()=>{let keys=['','id','degree','dc','bc','cc','combined'];if(i===0)return;let k=keys[i];if(sortKey===k)sortAsc=!sortAsc;else{sortKey=k;sortAsc=false}renderTable()}});\n"
            + "renderTable();drawChart();renderStats();\n";
    }
}
