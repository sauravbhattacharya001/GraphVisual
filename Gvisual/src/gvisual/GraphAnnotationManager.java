package gvisual;

import edu.uci.ics.jung.graph.Graph;
import java.io.*;
import java.util.*;

/**
 * Manages annotations (notes, tags, colors) on graph nodes and edges
 * for research documentation and collaborative analysis workflows.
 *
 * <p>Annotations are lightweight metadata overlays — they don't modify the
 * underlying graph structure. Supports JSON export/import for persistence
 * and sharing between researchers.</p>
 *
 * @author zalenix
 */
public class GraphAnnotationManager {

    /**
     * Represents an annotation on a graph element (node or edge).
     */
    public static class Annotation {
        private final String elementId;
        private final ElementType elementType;
        private String note;
        private final Set<String> tags;
        private String color;
        private String author;
        private long createdAt;
        private long updatedAt;
        private Priority priority;

        public enum ElementType { NODE, EDGE }
        public enum Priority { LOW, MEDIUM, HIGH, CRITICAL }

        public Annotation(String elementId, ElementType elementType) {
            if (elementId == null || elementId.trim().isEmpty()) {
                throw new IllegalArgumentException("Element ID cannot be null or empty");
            }
            if (elementType == null) {
                throw new IllegalArgumentException("Element type cannot be null");
            }
            this.elementId = elementId;
            this.elementType = elementType;
            this.tags = new LinkedHashSet<>();
            this.note = "";
            this.color = null;
            this.author = null;
            this.priority = Priority.MEDIUM;
            this.createdAt = System.currentTimeMillis();
            this.updatedAt = this.createdAt;
        }

        public String getElementId() { return elementId; }
        public ElementType getElementType() { return elementType; }

        public String getNote() { return note; }
        public void setNote(String note) {
            this.note = (note != null) ? note : "";
            this.updatedAt = System.currentTimeMillis();
        }

        public Set<String> getTags() { return Collections.unmodifiableSet(tags); }
        public void addTag(String tag) {
            if (tag != null && !tag.trim().isEmpty()) {
                tags.add(tag.trim().toLowerCase());
                updatedAt = System.currentTimeMillis();
            }
        }
        public void removeTag(String tag) {
            if (tag != null) {
                tags.remove(tag.trim().toLowerCase());
                updatedAt = System.currentTimeMillis();
            }
        }
        public boolean hasTag(String tag) {
            return tag != null && tags.contains(tag.trim().toLowerCase());
        }

        public String getColor() { return color; }
        public void setColor(String color) {
            this.color = color;
            this.updatedAt = System.currentTimeMillis();
        }

        public String getAuthor() { return author; }
        public void setAuthor(String author) {
            this.author = author;
            this.updatedAt = System.currentTimeMillis();
        }

        public Priority getPriority() { return priority; }
        public void setPriority(Priority priority) {
            if (priority != null) {
                this.priority = priority;
                this.updatedAt = System.currentTimeMillis();
            }
        }

        public long getCreatedAt() { return createdAt; }
        public long getUpdatedAt() { return updatedAt; }

        // For testing/deserialization
        void setCreatedAt(long ts) { this.createdAt = ts; }
        void setUpdatedAt(long ts) { this.updatedAt = ts; }
    }

    private final Map<String, Annotation> nodeAnnotations;
    private final Map<String, Annotation> edgeAnnotations;

    public GraphAnnotationManager() {
        this.nodeAnnotations = new LinkedHashMap<>();
        this.edgeAnnotations = new LinkedHashMap<>();
    }

    // --- Node annotations ---

    /**
     * Annotate a node. Creates annotation if it doesn't exist.
     */
    public Annotation annotateNode(String nodeId) {
        return nodeAnnotations.computeIfAbsent(nodeId,
            id -> new Annotation(id, Annotation.ElementType.NODE));
    }

    /**
     * Get annotation for a node, or null if none exists.
     */
    public Annotation getNodeAnnotation(String nodeId) {
        return nodeAnnotations.get(nodeId);
    }

    /**
     * Remove annotation from a node.
     */
    public boolean removeNodeAnnotation(String nodeId) {
        return nodeAnnotations.remove(nodeId) != null;
    }

    // --- Edge annotations ---

    /**
     * Build a canonical edge ID from two vertices.
     */
    public static String edgeKey(String v1, String v2) {
        if (v1.compareTo(v2) <= 0) return v1 + "--" + v2;
        return v2 + "--" + v1;
    }

    /**
     * Annotate an edge by its endpoint vertices.
     */
    public Annotation annotateEdge(String v1, String v2) {
        String key = edgeKey(v1, v2);
        return edgeAnnotations.computeIfAbsent(key,
            id -> new Annotation(id, Annotation.ElementType.EDGE));
    }

    /**
     * Get annotation for an edge, or null if none exists.
     */
    public Annotation getEdgeAnnotation(String v1, String v2) {
        return edgeAnnotations.get(edgeKey(v1, v2));
    }

    /**
     * Remove annotation from an edge.
     */
    public boolean removeEdgeAnnotation(String v1, String v2) {
        return edgeAnnotations.remove(edgeKey(v1, v2)) != null;
    }

    // --- Bulk operations ---

    /**
     * Tag multiple nodes at once.
     */
    public int bulkTagNodes(Collection<String> nodeIds, String tag) {
        int count = 0;
        for (String id : nodeIds) {
            annotateNode(id).addTag(tag);
            count++;
        }
        return count;
    }

    /**
     * Set color on multiple nodes at once.
     */
    public int bulkColorNodes(Collection<String> nodeIds, String color) {
        int count = 0;
        for (String id : nodeIds) {
            annotateNode(id).setColor(color);
            count++;
        }
        return count;
    }

    // --- Search & filtering ---

    /**
     * Find all annotations (node + edge) that have a specific tag.
     */
    public List<Annotation> findByTag(String tag) {
        String normalizedTag = tag.trim().toLowerCase();
        List<Annotation> results = new ArrayList<>();
        for (Annotation a : nodeAnnotations.values()) {
            if (a.hasTag(normalizedTag)) results.add(a);
        }
        for (Annotation a : edgeAnnotations.values()) {
            if (a.hasTag(normalizedTag)) results.add(a);
        }
        return results;
    }

    /**
     * Find all annotations whose note contains the search text (case-insensitive).
     */
    public List<Annotation> searchNotes(String query) {
        String q = query.toLowerCase();
        List<Annotation> results = new ArrayList<>();
        for (Annotation a : nodeAnnotations.values()) {
            if (a.getNote().toLowerCase().contains(q)) results.add(a);
        }
        for (Annotation a : edgeAnnotations.values()) {
            if (a.getNote().toLowerCase().contains(q)) results.add(a);
        }
        return results;
    }

    /**
     * Find all annotations with a specific priority.
     */
    public List<Annotation> findByPriority(Annotation.Priority priority) {
        List<Annotation> results = new ArrayList<>();
        for (Annotation a : nodeAnnotations.values()) {
            if (a.getPriority() == priority) results.add(a);
        }
        for (Annotation a : edgeAnnotations.values()) {
            if (a.getPriority() == priority) results.add(a);
        }
        return results;
    }

    /**
     * Find all annotations by a specific author.
     */
    public List<Annotation> findByAuthor(String author) {
        List<Annotation> results = new ArrayList<>();
        for (Annotation a : nodeAnnotations.values()) {
            if (author.equals(a.getAuthor())) results.add(a);
        }
        for (Annotation a : edgeAnnotations.values()) {
            if (author.equals(a.getAuthor())) results.add(a);
        }
        return results;
    }

    /**
     * Get all unique tags across all annotations.
     */
    public Set<String> getAllTags() {
        Set<String> tags = new TreeSet<>();
        for (Annotation a : nodeAnnotations.values()) tags.addAll(a.getTags());
        for (Annotation a : edgeAnnotations.values()) tags.addAll(a.getTags());
        return tags;
    }

    /**
     * Get tag frequency counts.
     */
    public Map<String, Integer> getTagCounts() {
        Map<String, Integer> counts = new TreeMap<>();
        for (Annotation a : nodeAnnotations.values()) {
            for (String tag : a.getTags()) counts.merge(tag, 1, Integer::sum);
        }
        for (Annotation a : edgeAnnotations.values()) {
            for (String tag : a.getTags()) counts.merge(tag, 1, Integer::sum);
        }
        return counts;
    }

    // --- Statistics ---

    public int getNodeAnnotationCount() { return nodeAnnotations.size(); }
    public int getEdgeAnnotationCount() { return edgeAnnotations.size(); }
    public int getTotalAnnotationCount() { return nodeAnnotations.size() + edgeAnnotations.size(); }

    /**
     * Get a summary report of all annotations.
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Graph Annotation Summary ===\n");
        sb.append(String.format("Node annotations: %d\n", nodeAnnotations.size()));
        sb.append(String.format("Edge annotations: %d\n", edgeAnnotations.size()));
        sb.append(String.format("Total annotations: %d\n", getTotalAnnotationCount()));

        Set<String> allTags = getAllTags();
        sb.append(String.format("Unique tags: %d\n", allTags.size()));
        if (!allTags.isEmpty()) {
            sb.append("Tags: ").append(String.join(", ", allTags)).append("\n");
        }

        Map<Annotation.Priority, Integer> priorityCounts = new TreeMap<>();
        for (Annotation a : nodeAnnotations.values())
            priorityCounts.merge(a.getPriority(), 1, Integer::sum);
        for (Annotation a : edgeAnnotations.values())
            priorityCounts.merge(a.getPriority(), 1, Integer::sum);
        if (!priorityCounts.isEmpty()) {
            sb.append("By priority: ");
            List<String> parts = new ArrayList<>();
            for (Map.Entry<Annotation.Priority, Integer> e : priorityCounts.entrySet()) {
                parts.add(e.getKey() + "=" + e.getValue());
            }
            sb.append(String.join(", ", parts)).append("\n");
        }

        return sb.toString();
    }

    // --- Graph integration ---

    /**
     * Auto-annotate nodes in a graph based on degree centrality.
     * Tags high-degree nodes as "hub" and low-degree as "peripheral".
     */
    public <V, E> int autoTagByDegree(Graph<V, E> graph, int hubThreshold, int peripheralThreshold) {
        int count = 0;
        for (V vertex : graph.getVertices()) {
            String id = vertex.toString();
            int degree = graph.degree(vertex);
            if (degree >= hubThreshold) {
                annotateNode(id).addTag("hub");
                annotateNode(id).setNote("High-degree node (degree=" + degree + ")");
                count++;
            } else if (degree <= peripheralThreshold) {
                annotateNode(id).addTag("peripheral");
                count++;
            }
        }
        return count;
    }

    /**
     * Auto-annotate isolated nodes (degree 0).
     */
    public <V, E> int autoTagIsolated(Graph<V, E> graph) {
        int count = 0;
        for (V vertex : graph.getVertices()) {
            if (graph.degree(vertex) == 0) {
                String id = vertex.toString();
                annotateNode(id).addTag("isolated");
                annotateNode(id).setPriority(Annotation.Priority.HIGH);
                count++;
            }
        }
        return count;
    }

    // --- JSON Export/Import ---

    /**
     * Export all annotations to a JSON string.
     */
    public String exportToJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"nodeAnnotations\": [\n");
        appendAnnotationsJson(sb, nodeAnnotations.values());
        sb.append("  ],\n");
        sb.append("  \"edgeAnnotations\": [\n");
        appendAnnotationsJson(sb, edgeAnnotations.values());
        sb.append("  ]\n");
        sb.append("}");
        return sb.toString();
    }

    private void appendAnnotationsJson(StringBuilder sb, Collection<Annotation> annotations) {
        Iterator<Annotation> it = annotations.iterator();
        while (it.hasNext()) {
            Annotation a = it.next();
            sb.append("    {\n");
            sb.append("      \"elementId\": ").append(jsonString(a.getElementId())).append(",\n");
            sb.append("      \"elementType\": ").append(jsonString(a.getElementType().name())).append(",\n");
            sb.append("      \"note\": ").append(jsonString(a.getNote())).append(",\n");
            sb.append("      \"tags\": [");
            Iterator<String> tagIt = a.getTags().iterator();
            while (tagIt.hasNext()) {
                sb.append(jsonString(tagIt.next()));
                if (tagIt.hasNext()) sb.append(", ");
            }
            sb.append("],\n");
            sb.append("      \"color\": ").append(a.getColor() != null ? jsonString(a.getColor()) : "null").append(",\n");
            sb.append("      \"author\": ").append(a.getAuthor() != null ? jsonString(a.getAuthor()) : "null").append(",\n");
            sb.append("      \"priority\": ").append(jsonString(a.getPriority().name())).append(",\n");
            sb.append("      \"createdAt\": ").append(a.getCreatedAt()).append(",\n");
            sb.append("      \"updatedAt\": ").append(a.getUpdatedAt()).append("\n");
            sb.append("    }");
            if (it.hasNext()) sb.append(",");
            sb.append("\n");
        }
    }

    /**
     * Import annotations from a JSON string (additive — merges with existing).
     * Simple hand-rolled parser for the known format.
     */
    public int importFromJson(String json) {
        int count = 0;
        String[] lines = json.split("\n");
        String currentSection = null;
        String elementId = null;
        String elementType = null;
        String note = null;
        List<String> tags = new ArrayList<>();
        String color = null;
        String author = null;
        String priority = null;
        long createdAt = 0;
        long updatedAt = 0;
        boolean inObject = false;

        for (String rawLine : lines) {
            String line = rawLine.trim();

            if (line.contains("\"nodeAnnotations\"")) { currentSection = "node"; continue; }
            if (line.contains("\"edgeAnnotations\"")) { currentSection = "edge"; continue; }

            if (line.equals("{") && currentSection != null) { inObject = true; continue; }

            if (line.startsWith("}") && inObject && currentSection != null) {
                // Commit annotation
                if (elementId != null && elementType != null) {
                    Annotation a;
                    if ("NODE".equals(elementType)) {
                        a = annotateNode(elementId);
                    } else {
                        // Parse edge key "v1--v2"
                        String[] parts = elementId.split("--", 2);
                        if (parts.length == 2) {
                            a = annotateEdge(parts[0], parts[1]);
                        } else {
                            a = annotateNode(elementId); // fallback
                        }
                    }
                    if (note != null) a.setNote(note);
                    for (String t : tags) a.addTag(t);
                    if (color != null) a.setColor(color);
                    if (author != null) a.setAuthor(author);
                    if (priority != null) {
                        try { a.setPriority(Annotation.Priority.valueOf(priority)); } catch (Exception e) { /* skip */ }
                    }
                    if (createdAt > 0) a.setCreatedAt(createdAt);
                    if (updatedAt > 0) a.setUpdatedAt(updatedAt);
                    count++;
                }
                // Reset
                elementId = null; elementType = null; note = null;
                tags = new ArrayList<>(); color = null; author = null; priority = null;
                createdAt = 0; updatedAt = 0; inObject = false;
                continue;
            }

            if (inObject) {
                if (line.startsWith("\"elementId\"")) elementId = extractJsonStringValue(line);
                else if (line.startsWith("\"elementType\"")) elementType = extractJsonStringValue(line);
                else if (line.startsWith("\"note\"")) note = extractJsonStringValue(line);
                else if (line.startsWith("\"color\"")) color = extractJsonStringValue(line);
                else if (line.startsWith("\"author\"")) author = extractJsonStringValue(line);
                else if (line.startsWith("\"priority\"")) priority = extractJsonStringValue(line);
                else if (line.startsWith("\"createdAt\"")) createdAt = extractJsonLongValue(line);
                else if (line.startsWith("\"updatedAt\"")) updatedAt = extractJsonLongValue(line);
                else if (line.startsWith("\"tags\"")) {
                    // Parse inline tag array
                    int bracketStart = line.indexOf('[');
                    int bracketEnd = line.indexOf(']');
                    if (bracketStart >= 0 && bracketEnd > bracketStart) {
                        String tagsPart = line.substring(bracketStart + 1, bracketEnd).trim();
                        if (!tagsPart.isEmpty()) {
                            for (String t : tagsPart.split(",")) {
                                String val = extractQuotedValue(t.trim());
                                if (val != null) tags.add(val);
                            }
                        }
                    }
                }
            }
        }

        return count;
    }

    /**
     * Export annotations to a file.
     */
    public void exportToFile(String filePath) throws IOException {
        java.io.File file = new java.io.File(filePath);
        ExportUtils.validateOutputPath(file);
        try (Writer w = new FileWriter(file)) {
            w.write(exportToJson());
        }
    }

    /**
     * Import annotations from a file.
     */
    public int importFromFile(String filePath) throws IOException {
        java.io.File file = new java.io.File(filePath);
        ExportUtils.validateOutputPath(file);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return importFromJson(sb.toString());
    }

    /**
     * Clear all annotations.
     */
    public void clear() {
        nodeAnnotations.clear();
        edgeAnnotations.clear();
    }

    // --- Helpers ---

    private static String jsonString(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\")
                          .replace("\"", "\\\"")
                          .replace("\n", "\\n")
                          .replace("\r", "\\r")
                          .replace("\t", "\\t") + "\"";
    }

    private static String extractJsonStringValue(String line) {
        // Find value after ":"
        int colonIdx = line.indexOf(':');
        if (colonIdx < 0) return null;
        String val = line.substring(colonIdx + 1).trim();
        if (val.endsWith(",")) val = val.substring(0, val.length() - 1).trim();
        if (val.equals("null")) return null;
        return extractQuotedValue(val);
    }

    private static String extractQuotedValue(String val) {
        if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
            return val.substring(1, val.length() - 1)
                      .replace("\\n", "\n")
                      .replace("\\r", "\r")
                      .replace("\\t", "\t")
                      .replace("\\\"", "\"")
                      .replace("\\\\", "\\");
        }
        return null;
    }

    private static long extractJsonLongValue(String line) {
        int colonIdx = line.indexOf(':');
        if (colonIdx < 0) return 0;
        String val = line.substring(colonIdx + 1).trim();
        if (val.endsWith(",")) val = val.substring(0, val.length() - 1).trim();
        try { return Long.parseLong(val); } catch (NumberFormatException e) { return 0; }
    }
}
