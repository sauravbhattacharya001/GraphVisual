package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Exports a JUNG graph as a self-contained HTML page showing the same graph
 * rendered with 4 different layout algorithms side-by-side in a 2×2 grid:
 * force-directed, circular, grid, and radial (by degree).
 *
 * <p>Users can visually compare how different layouts reveal different
 * structural properties of the same network — clusters, hubs, symmetry, etc.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>2×2 layout comparison grid (force, circular, grid, radial)</li>
 *   <li>Synchronized hover highlighting across all four views</li>
 *   <li>Edge coloring by relationship type</li>
 *   <li>Node size by degree</li>
 *   <li>Zoom and pan per panel</li>
 *   <li>Dark/light theme toggle</li>
 *   <li>Export-ready: single self-contained HTML file</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 *   GraphLayoutComparer comparer = new GraphLayoutComparer(graph);
 *   comparer.setTitle("My Network");
 *   comparer.export(new File("layout-comparison.html"));
 * </pre>
 *
 * @author zalenix
 */
public class GraphLayoutComparer {

    private final Graph<String, Edge> graph;
    private String title = "Graph Layout Comparison";

    public GraphLayoutComparer(Graph<String, Edge> graph) {
        this.graph = Objects.requireNonNull(graph, "graph must not be null");
    }

    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Exports the comparison page to the given file.
     */
    public void export(File file) throws IOException {
        ExportUtils.validateOutputPath(file);
        String html = exportToString();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write(html);
        }
    }

    /**
     * Returns the full HTML string.
     */
    public String exportToString() {
        StringBuilder sb = new StringBuilder(16384);
        Collection<String> vertices = graph.getVertices();
        Collection<Edge> edges = graph.getEdges();

        // Build JSON data
        StringBuilder nodeJson = new StringBuilder("[");
        Map<String, Integer> idMap = new HashMap<>();
        int idx = 0;
        for (String v : vertices) {
            if (idx > 0) nodeJson.append(",");
            int deg = graph.degree(v);
            idMap.put(v, idx);
            nodeJson.append("{\"id\":\"").append(escJs(v))
                    .append("\",\"deg\":").append(deg).append("}");
            idx++;
        }
        nodeJson.append("]");

        StringBuilder linkJson = new StringBuilder("[");
        boolean first = true;
        for (Edge e : edges) {
            String v1 = e.getVertex1() != null ? e.getVertex1() :
                    graph.getEndpoints(e).getFirst().toString();
            String v2 = e.getVertex2() != null ? e.getVertex2() :
                    graph.getEndpoints(e).getSecond().toString();
            if (!idMap.containsKey(v1) || !idMap.containsKey(v2)) continue;
            if (!first) linkJson.append(",");
            first = false;
            String type = e.getType() != null ? e.getType() : "unknown";
            linkJson.append("{\"source\":").append(idMap.get(v1))
                    .append(",\"target\":").append(idMap.get(v2))
                    .append(",\"type\":\"").append(escJs(type)).append("\"}");
        }
        linkJson.append("]");

        int nodeCount = vertices.size();
        int edgeCount = edges.size();

        sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">");
        sb.append("<title>").append(escHtml(title)).append("</title>");
        sb.append("<script src=\"https://d3js.org/d3.v7.min.js\"></script>");
        sb.append("<style>");
        sb.append(getCSS());
        sb.append("</style></head><body>");
        sb.append("<div id=\"header\"><h1>").append(escHtml(title)).append("</h1>");
        sb.append("<span class=\"stats\">").append(nodeCount).append(" nodes, ")
                .append(edgeCount).append(" edges</span>");
        sb.append("<button id=\"themeBtn\" onclick=\"toggleTheme()\">🌙 Dark</button></div>");
        sb.append("<div id=\"grid\">");
        sb.append("<div class=\"cell\" id=\"cell-force\"><h3>Force-Directed</h3><svg id=\"svg-force\"></svg></div>");
        sb.append("<div class=\"cell\" id=\"cell-circular\"><h3>Circular</h3><svg id=\"svg-circular\"></svg></div>");
        sb.append("<div class=\"cell\" id=\"cell-grid\"><h3>Grid</h3><svg id=\"svg-grid\"></svg></div>");
        sb.append("<div class=\"cell\" id=\"cell-radial\"><h3>Radial (by Degree)</h3><svg id=\"svg-radial\"></svg></div>");
        sb.append("</div>");
        sb.append("<script>");
        sb.append("const rawNodes=").append(nodeJson).append(";");
        sb.append("const rawLinks=").append(linkJson).append(";");
        sb.append(getJS());
        sb.append("</script></body></html>");
        return sb.toString();
    }

    private String getCSS() {
        return "*{margin:0;padding:0;box-sizing:border-box;}"
                + "body{font-family:system-ui,sans-serif;background:#f5f5f5;color:#333;transition:all .3s;}"
                + "body.dark{background:#1a1a2e;color:#eee;}"
                + "#header{display:flex;align-items:center;gap:16px;padding:12px 20px;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,.1);}"
                + "body.dark #header{background:#16213e;}"
                + "#header h1{font-size:18px;}"
                + ".stats{color:#888;font-size:13px;}"
                + "#themeBtn{margin-left:auto;cursor:pointer;border:1px solid #ccc;border-radius:6px;padding:4px 12px;background:transparent;color:inherit;}"
                + "#grid{display:grid;grid-template-columns:1fr 1fr;gap:8px;padding:8px;height:calc(100vh - 56px);}"
                + ".cell{background:#fff;border-radius:8px;overflow:hidden;display:flex;flex-direction:column;box-shadow:0 1px 3px rgba(0,0,0,.08);}"
                + "body.dark .cell{background:#16213e;}"
                + ".cell h3{font-size:13px;padding:6px 12px;border-bottom:1px solid #eee;}"
                + "body.dark .cell h3{border-color:#2a2a4a;}"
                + ".cell svg{flex:1;width:100%;}"
                + "line.link{stroke-opacity:.4;stroke-width:1;}"
                + "circle.node{stroke:#fff;stroke-width:1;cursor:pointer;}"
                + "body.dark circle.node{stroke:#1a1a2e;}"
                + ".highlight circle.node{opacity:.15;}.highlight line.link{opacity:.05;}"
                + ".highlight circle.node.active{opacity:1;stroke-width:2;}"
                + ".highlight line.link.active{opacity:.8;stroke-width:2;}";
    }

    private String getJS() {
        return "const typeColors={f:'#4fc3f7',c:'#81c784',fs:'#ffb74d',s:'#e57373',sg:'#ba68c8',unknown:'#90a4ae'};"
                + "let dark=false;"
                + "function toggleTheme(){dark=!dark;document.body.classList.toggle('dark');document.getElementById('themeBtn').textContent=dark?'☀️ Light':'🌙 Dark';}"
                + "const panels=['force','circular','grid','radial'];"
                + "const svgs={};"
                + "const W=()=>document.querySelector('.cell svg').clientWidth||400;"
                + "const H=()=>document.querySelector('.cell svg').clientHeight||350;"
                + "function cloneData(){return{nodes:rawNodes.map(d=>({...d})),links:rawLinks.map(d=>({...d}))}}"
                + "function render(id,layoutFn){"
                + "  const{nodes,links}=cloneData();"
                + "  const svg=d3.select('#svg-'+id);"
                + "  const w=W(),h=H();"
                + "  svg.attr('viewBox','0 0 '+w+' '+h);"
                + "  const g=svg.append('g');"
                + "  svg.call(d3.zoom().scaleExtent([.3,5]).on('zoom',e=>g.attr('transform',e.transform)));"
                + "  layoutFn(nodes,links,w,h);"
                + "  const maxDeg=d3.max(nodes,d=>d.deg)||1;"
                + "  const linkSel=g.selectAll('line.link').data(links).join('line').attr('class','link')"
                + "    .attr('x1',d=>nodes[d.source]?nodes[d.source].x:d.source.x).attr('y1',d=>nodes[d.source]?nodes[d.source].y:d.source.y)"
                + "    .attr('x2',d=>nodes[d.target]?nodes[d.target].x:d.target.x).attr('y2',d=>nodes[d.target]?nodes[d.target].y:d.target.y)"
                + "    .attr('stroke',d=>typeColors[d.type]||typeColors.unknown);"
                + "  const nodeSel=g.selectAll('circle.node').data(nodes).join('circle').attr('class','node')"
                + "    .attr('cx',d=>d.x).attr('cy',d=>d.y)"
                + "    .attr('r',d=>3+Math.sqrt(d.deg/maxDeg)*8)"
                + "    .attr('fill',d=>{const nb=links.filter(l=>(l.source===d||l.source===d.index||nodes[l.source]===d)&&l.type);const t=nb.length?nb[0].type:'unknown';return typeColors[t]||typeColors.unknown;})"
                + "    .on('mouseenter',(_,d)=>highlightAll(d.id))"
                + "    .on('mouseleave',()=>clearHighlight());"
                + "  nodeSel.append('title').text(d=>d.id+' (deg '+d.deg+')');"
                + "  svgs[id]={svg,g,nodeSel,linkSel,nodes,links};"
                + "}"
                + "function highlightAll(nodeId){"
                + "  panels.forEach(p=>{"
                + "    if(!svgs[p])return;"
                + "    const s=svgs[p];"
                + "    s.svg.classed('highlight',true);"
                + "    const nbSet=new Set([nodeId]);"
                + "    s.links.forEach((l,i)=>{"
                + "      const si=typeof l.source==='object'?l.source.id:s.nodes[l.source].id;"
                + "      const ti=typeof l.target==='object'?l.target.id:s.nodes[l.target].id;"
                + "      if(si===nodeId||ti===nodeId){nbSet.add(si);nbSet.add(ti);s.linkSel.filter((_,j)=>j===i).classed('active',true);}"
                + "    });"
                + "    s.nodeSel.classed('active',d=>nbSet.has(d.id));"
                + "  });"
                + "}"
                + "function clearHighlight(){panels.forEach(p=>{if(!svgs[p])return;svgs[p].svg.classed('highlight',false);svgs[p].nodeSel.classed('active',false);svgs[p].linkSel.classed('active',false);});}"
                // Force layout
                + "function layoutForce(nodes,links,w,h){"
                + "  const sim=d3.forceSimulation(nodes)"
                + "    .force('link',d3.forceLink(links).distance(40))"
                + "    .force('charge',d3.forceManyBody().strength(-60))"
                + "    .force('center',d3.forceCenter(w/2,h/2))"
                + "    .stop();"
                + "  for(let i=0;i<200;i++)sim.tick();"
                + "}"
                // Circular layout
                + "function layoutCircular(nodes,links,w,h){"
                + "  const cx=w/2,cy=h/2,r=Math.min(w,h)*0.4;"
                + "  nodes.forEach((d,i)=>{const a=2*Math.PI*i/nodes.length;d.x=cx+r*Math.cos(a);d.y=cy+r*Math.sin(a);});"
                + "}"
                // Grid layout
                + "function layoutGrid(nodes,links,w,h){"
                + "  const cols=Math.ceil(Math.sqrt(nodes.length));"
                + "  const sx=w/(cols+1),sy=h/(Math.ceil(nodes.length/cols)+1);"
                + "  nodes.forEach((d,i)=>{d.x=sx*(i%cols+1);d.y=sy*(Math.floor(i/cols)+1);});"
                + "}"
                // Radial layout (by degree)
                + "function layoutRadial(nodes,links,w,h){"
                + "  const cx=w/2,cy=h/2;"
                + "  const maxDeg=d3.max(nodes,d=>d.deg)||1;"
                + "  const sorted=[...nodes].sort((a,b)=>b.deg-a.deg);"
                + "  const shells=new Map();"
                + "  sorted.forEach(d=>{const shell=Math.floor((1-d.deg/maxDeg)*4);if(!shells.has(shell))shells.set(shell,[]);shells.get(shell).push(d);});"
                + "  shells.forEach((arr,shell)=>{"
                + "    const r=(shell+1)*Math.min(w,h)*0.1;"
                + "    arr.forEach((d,i)=>{const a=2*Math.PI*i/arr.length;d.x=cx+r*Math.cos(a);d.y=cy+r*Math.sin(a);});"
                + "  });"
                + "}"
                + "render('force',layoutForce);"
                + "render('circular',layoutCircular);"
                + "render('grid',layoutGrid);"
                + "render('radial',layoutRadial);";
    }

    private static String escJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
