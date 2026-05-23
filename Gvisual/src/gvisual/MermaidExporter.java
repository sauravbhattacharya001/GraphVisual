package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Exports a JUNG graph to <a href="https://mermaid.js.org/">Mermaid</a>
 * diagram syntax (a {@code graph}/{@code flowchart} block).
 *
 * <p>Mermaid is the most widely-rendered graph DSL today: it is supported
 * natively in GitHub READMEs / issues / PR descriptions, Notion, Obsidian,
 * GitLab, Bitbucket, Quarto, and any HTML page that loads
 * {@code mermaid.js}. This makes {@code MermaidExporter} the right pick
 * for "I want to paste a quick graph into a README" - DOT requires a
 * Graphviz tool, GraphML needs a viewer, and the SVG/PNG exporters lose
 * editability.</p>
 *
 * <p>Sibling to the existing {@link DotExporter}, {@link GexfExporter},
 * {@link GraphMLExporter}, {@link AdjacencyListExporter},
 * {@link JsonGraphExporter}, {@link TikzExporter},
 * {@link DimacsExporter}, {@link SvgExporter} and
 * {@link InteractiveHtmlExporter}.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Configurable orientation: {@code TD}, {@code TB}, {@code BT},
 *       {@code LR}, {@code RL}.</li>
 *   <li>Optional {@code flowchart} vs {@code graph} keyword (Mermaid 9+
 *       prefers {@code flowchart}; older renderers only know
 *       {@code graph}).</li>
 *   <li>Edge type &rarr; color mapping via {@code classDef}/{@code class}
 *       and per-link {@code linkStyle} blocks, reusing the project's
 *       {@link EdgeTypeRegistry} palette.</li>
 *   <li>Optional edge labels (uses the {@code A -->|label| B} form).</li>
 *   <li>Directed (arrow) vs undirected (open line) edges.</li>
 *   <li>Deterministic ordering (vertices sorted lexicographically,
 *       edges sorted by {@code (v1,v2)} pair) so the same graph yields
 *       byte-identical Mermaid output every run.</li>
 *   <li>Path-traversal-safe file export via
 *       {@link ExportUtils#validateOutputPath(File)}.</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 *   MermaidExporter exporter = new MermaidExporter(graph);
 *   exporter.setOrientation("LR");
 *   exporter.setColorByEdgeType(true);
 *   String md = exporter.exportToString();
 *
 *   // Wrap inside a fenced code block ready for GitHub:
 *   String mdBlock = exporter.exportToMarkdownBlock();
 * </pre>
 *
 * @author sauravbhattacharya001
 */
public class MermaidExporter {

    private static final Set<String> VALID_ORIENTATIONS =
            new LinkedHashSet<>(Arrays.asList("TD", "TB", "BT", "LR", "RL"));

    private final Graph<String, Edge> graph;
    private String orientation = "TD";
    private boolean useFlowchartKeyword = true;
    private boolean colorByEdgeType = true;
    private boolean directed = false;
    private boolean showEdgeLabels = true;
    private String title; // optional ---\ntitle: ...\n--- frontmatter block
    private String description;
    private final Map<String, String> typeColors =
            new LinkedHashMap<>(EdgeTypeRegistry.getAllHexColors());

    /**
     * Creates a new Mermaid exporter for the given graph.
     *
     * @param graph the JUNG graph to export
     * @throws IllegalArgumentException if {@code graph} is {@code null}
     */
    public MermaidExporter(Graph<String, Edge> graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph must not be null");
        }
        this.graph = graph;
    }

    /**
     * Sets the diagram orientation. Allowed values: TD, TB, BT, LR, RL.
     * Anything else is rejected.
     */
    public void setOrientation(String orientation) {
        if (orientation == null) {
            throw new IllegalArgumentException("Orientation must not be null");
        }
        String upper = orientation.trim().toUpperCase(Locale.ROOT);
        if (!VALID_ORIENTATIONS.contains(upper)) {
            throw new IllegalArgumentException(
                    "Orientation must be one of " + VALID_ORIENTATIONS + ", got: " + orientation);
        }
        this.orientation = upper;
    }

    /**
     * Toggle between {@code flowchart} (Mermaid 9+, default) and the
     * legacy {@code graph} keyword (Mermaid 8 and earlier). The diagrams
     * are functionally identical; only the leading keyword differs.
     */
    public void setUseFlowchartKeyword(boolean useFlowchart) {
        this.useFlowchartKeyword = useFlowchart;
    }

    /** Whether to color edges by their {@link EdgeType}. Default: true. */
    public void setColorByEdgeType(boolean color) { this.colorByEdgeType = color; }

    /** Render as a directed graph (arrows). Default: undirected. */
    public void setDirected(boolean directed) { this.directed = directed; }

    /** Show edge labels (the {@code A -->|label| B} form). Default: true. */
    public void setShowEdgeLabels(boolean show) { this.showEdgeLabels = show; }

    /** Optional Mermaid title (rendered as YAML frontmatter). */
    public void setTitle(String title) { this.title = title; }

    /** Optional description comment placed above the diagram body. */
    public void setDescription(String description) { this.description = description; }

    /** Override the color for a specific edge type. */
    public void setTypeColor(String edgeType, String hexColor) {
        if (edgeType == null) return;
        if (hexColor == null || hexColor.isEmpty()) return;
        typeColors.put(edgeType, hexColor);
    }

    /**
     * Exports the graph to a Mermaid file.
     *
     * @param outputFile the destination file
     * @throws IOException if the file cannot be written
     * @throws SecurityException if the path escapes allowed directories (CWE-22)
     */
    public void export(File outputFile) throws IOException {
        ExportUtils.validateOutputPath(outputFile);
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
            writer.write(exportToString());
        }
    }

    /**
     * Exports the graph wrapped in a {@code ```mermaid ... ```} fenced
     * code block, ready to be pasted into a Markdown document or a
     * GitHub README. The fence is added even when {@link #export(File)}
     * is also used because the unwrapped form is sometimes preferable
     * (Quarto, plain {@code .mmd} files).
     */
    public String exportToMarkdownBlock() {
        return "```mermaid\n" + exportToString() + "```\n";
    }

    /**
     * Exports the graph to a Mermaid-formatted string.
     */
    public String exportToString() {
        StringBuilder sb = new StringBuilder();

        // YAML frontmatter (optional)
        if (title != null && !title.isEmpty()) {
            sb.append("---\n");
            sb.append("title: ").append(escapeYaml(title)).append("\n");
            sb.append("---\n");
        }

        // Header comment(s) -- Mermaid uses %% for line comments
        sb.append("%% Generated by GraphVisual MermaidExporter\n");
        if (description != null && !description.isEmpty()) {
            sb.append("%% ").append(description.replace('\n', ' ')).append("\n");
        }
        sb.append("%% Nodes: ").append(graph.getVertexCount())
          .append(", Edges: ").append(graph.getEdgeCount())
          .append(", Direction: ").append(directed ? "directed" : "undirected")
          .append("\n");

        // Diagram opening line
        String keyword = useFlowchartKeyword ? "flowchart" : "graph";
        sb.append(keyword).append(' ').append(orientation).append('\n');

        // Stable vertex ordering
        List<String> sortedVertices = new ArrayList<>(graph.getVertices());
        Collections.sort(sortedVertices);

        // Vertex id remapping -- Mermaid ids must match [A-Za-z0-9_].
        // We keep the original as the rendered label.
        Map<String, String> idMap = new LinkedHashMap<>();
        Set<String> usedIds = new HashSet<>();
        for (String v : sortedVertices) {
            idMap.put(v, sanitizeId(v, usedIds));
        }

        // Nodes (each with explicit label so spaces/unicode survive)
        sb.append("    %% Nodes\n");
        for (String v : sortedVertices) {
            String id = idMap.get(v);
            int deg = graph.degree(v);
            sb.append("    ").append(id)
              .append("[\"").append(escapeMermaidLabel(v))
              .append("<br/><small>deg ").append(deg).append("</small>\"]")
              .append('\n');
        }

        // Edge collection -- deterministic order, dedup for undirected
        // multi-edges of the same type.
        String connector = directed ? "-->" : "---";

        List<EdgeRecord> records = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Edge e : graph.getEdges()) {
            String v1 = e.getVertex1();
            String v2 = e.getVertex2();
            if (v1 == null || v2 == null) continue;
            if (!idMap.containsKey(v1) || !idMap.containsKey(v2)) continue;
            String key;
            if (directed) {
                key = v1 + "\u0001" + v2;
            } else {
                key = v1.compareTo(v2) <= 0
                        ? v1 + "\u0001" + v2
                        : v2 + "\u0001" + v1;
            }
            String type = e.getType();
            String typeKey = (type != null) ? key + "\u0002" + type : key;
            if (!seen.add(typeKey)) continue;
            records.add(new EdgeRecord(v1, v2, e.getType(), e.getLabel(), e.getWeight()));
        }
        Collections.sort(records);

        // Edges
        sb.append("    %% Edges\n");
        List<String> linkStyleLines = new ArrayList<>();
        int edgeIndex = 0;
        for (EdgeRecord r : records) {
            String id1 = idMap.get(r.v1);
            String id2 = idMap.get(r.v2);
            sb.append("    ").append(id1).append(' ').append(connector);
            if (showEdgeLabels && r.label != null && !r.label.isEmpty()) {
                sb.append("|").append(escapeMermaidLabel(r.label)).append("|");
            }
            sb.append(' ').append(id2).append('\n');

            if (colorByEdgeType && r.type != null) {
                String hex = typeColors.getOrDefault(r.type, "#CCCCCC");
                double penWidth = 1.0 + Math.min(4.0, Math.max(0.0, r.weight) * 0.5);
                linkStyleLines.add(String.format(Locale.ROOT,
                        "    linkStyle %d stroke:%s,stroke-width:%.1fpx;",
                        edgeIndex, hex, penWidth));
            }
            edgeIndex++;
        }

        // classDef blocks per edge type that actually appears, so node
        // groupings (e.g. members of study groups) can still be styled by
        // downstream tooling. We emit one classDef per type even when not
        // used directly by nodes -- harmless and convenient.
        if (colorByEdgeType) {
            sb.append("    %% Edge type palette\n");
            for (Map.Entry<String, String> entry : typeColors.entrySet()) {
                String code = entry.getKey();
                String hex = entry.getValue();
                String name = EdgeTypeRegistry.getName(code);
                sb.append("    classDef edge_").append(code)
                  .append(" stroke:").append(hex).append(",stroke-width:2px;")
                  .append(" %% ").append(escapeMermaidLabel(name)).append('\n');
            }
            for (String line : linkStyleLines) {
                sb.append(line).append('\n');
            }
        }

        return sb.toString();
    }

    // --- Internal helpers -----------------------------------------------

    /** Sanitizes a vertex name into a Mermaid-safe id. */
    private static String sanitizeId(String name, Set<String> usedIds) {
        StringBuilder out = new StringBuilder();
        out.append('n'); // ensure leading char is a letter
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                out.append(c);
            } else {
                out.append('_');
            }
        }
        String base = out.toString();
        String candidate = base;
        int suffix = 1;
        while (usedIds.contains(candidate)) {
            candidate = base + "_" + suffix++;
        }
        usedIds.add(candidate);
        return candidate;
    }

    /**
     * Escapes a label for Mermaid. Mermaid labels are delimited by
     * {@code "} (when wrapped in {@code [" ... "]}) and by {@code |} for
     * edge labels. We escape the quote, backslash and pipe characters and
     * collapse newlines so we never break the diagram syntax.
     */
    static String escapeMermaidLabel(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  out.append("&quot;"); break;
                case '|':  out.append("&#124;"); break;
                case '<':  out.append("&lt;"); break;
                case '>':  out.append("&gt;"); break;
                case '\\': out.append("\\\\"); break;
                case '\r':
                case '\n':
                case '\t': out.append(' '); break;
                default:   out.append(c);
            }
        }
        return out.toString();
    }

    private static String escapeYaml(String s) {
        // Simple, conservative single-line YAML escape.
        return s.replace("\n", " ").replace("\"", "\\\"");
    }

    /** Internal helper -- sortable edge tuple. */
    private static final class EdgeRecord implements Comparable<EdgeRecord> {
        final String v1, v2;
        final String type;
        final String label;
        final float weight;
        EdgeRecord(String v1, String v2, String type, String label, float weight) {
            this.v1 = v1; this.v2 = v2;
            this.type = type; this.label = label;
            this.weight = weight;
        }
        @Override
        public int compareTo(EdgeRecord o) {
            int c = v1.compareTo(o.v1);
            if (c != 0) return c;
            c = v2.compareTo(o.v2);
            if (c != 0) return c;
            String t1 = type == null ? "" : type;
            String t2 = o.type == null ? "" : o.type;
            return t1.compareTo(t2);
        }
    }
}
