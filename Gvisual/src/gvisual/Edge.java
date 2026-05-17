/*
 * Edge.java
 *
 * Core edge data model for GraphVisual. See class Javadoc below.
 */

package gvisual;

/**
 * Edge between two vertices in a GraphVisual graph.
 *
 * <p>An {@code Edge} carries:
 * <ul>
 *   <li>An <b>edge type</b> ({@link #getType()}) drawn from the project's
 *       relationship categories, with the canonical short codes
 *       {@code f} (friend), {@code fs} (familiar stranger),
 *       {@code c} (classmate), {@code s} (stranger), and
 *       {@code sg} (study group). See {@link EdgeType} for the full
 *       palette and metadata.</li>
 *   <li>Two endpoint identifiers ({@link #getVertex1()} and
 *       {@link #getVertex2()}). The edge is treated as <b>undirected</b>:
 *       {@code (v1, v2)} equals {@code (v2, v1)} for {@link #equals(Object)}
 *       and {@link #hashCode()} purposes.</li>
 *   <li>A scalar {@link #getWeight() weight} (typically interaction
 *       intensity — frequency × duration).</li>
 *   <li>An optional human-readable {@link #getLabel() label}.</li>
 *   <li>Optional <b>temporal extent</b> in the form of an epoch-millis
 *       start ({@link #getTimestamp()}) and end
 *       ({@link #getEndTimestamp()}). Untimed edges (both null) are
 *       considered active at all times.</li>
 * </ul>
 *
 * <p>Note for callers: the {@code vertex1} / {@code vertex2} fields stored
 * on an {@code Edge} are advisory. The authoritative endpoints for an edge
 * inside a JUNG graph are obtained via
 * {@link edu.uci.ics.jung.graph.Graph#getEndpoints(Object)}; analyzers that
 * read endpoints should prefer that API.
 *
 * <p>This class is mutable but is intended to be treated as effectively
 * immutable once inserted into a graph (changing endpoints or type after
 * insertion may corrupt analyzer state).
 *
 * @author sauravbhattacharya001
 */
public class Edge {
    private String edgeType;
    private String vertex1;
    private String vertex2;
    private float weight;
    private String label;
    private Long timestamp;       // epoch millis (null = static/untimed Edge)
    private Long endTimestamp;     // optional: for interval-based edges
    /**
     * returns the type of the Edge
     * @return returns the type of the Edge. Values in (f,fs,c,s,sg)
     */
    public String getType()
    {
        return edgeType;
    }

    /**
     * returns first vertex of the Edge
     * @return returns first vertex of the Edge
     */
    public String getVertex1()
    {
        return vertex1;
    }

    /**
     * returns second vertex of the Edge
     * @return returns second vertex of the Edge
     */
    public String getVertex2()
    {
        return vertex2;
    }

    /**
     * No-arg constructor. Leaves all fields at their defaults
     * ({@code null} for object fields, {@code 0f} for {@link #getWeight()}).
     * Mainly intended for frameworks (deserialization, bean instantiation).
     * Prefer {@link #Edge(String, String, String)} for explicit construction.
     */
    public Edge()
    {
    }

    /**
     * Constructs an edge with a type and two endpoints.
     *
     * <p><b>Argument order matters:</b> the type comes first, followed by
     * the two endpoint identifiers. Swapping the order leaves the edge
     * with garbage type / vertex fields, which has been a recurring source
     * of subtle bugs in callers.
     *
     * @param edgeType type code (e.g. {@code "f"}, {@code "c"}, {@code "s"});
     *                 see {@link EdgeType}
     * @param vertex1  first endpoint id (undirected: order is irrelevant
     *                 for equality)
     * @param vertex2  second endpoint id
     */
    public Edge(String edgeType,String vertex1,String vertex2)
    {
        this.edgeType = edgeType;
        this.vertex1 = vertex1;
        this.vertex2 = vertex2;
    }

    /**
     * Sets the scalar weight of this edge.
     * @param weight non-negative numeric weight (semantics are caller-defined,
     *               but typically encodes interaction intensity)
     */
    public void setWeight(float weight)
    {
        this.weight = weight;
    }

    /**
     * Returns the scalar weight of this edge.
     * @return the weight (default {@code 0f} if never set)
     */
    public float getWeight()
    {
        return this.weight;
    }

    /**
     * Sets the human-readable label for this edge.
     * Labels are display-only and do not participate in {@link #equals(Object)}.
     * @param label the label, or {@code null} to clear
     */
    public void setLabel(String label)
    {
        this.label = label;
    }

    /**
     * Returns the human-readable label for this edge.
     * @return the label, or {@code null} if none was set
     */
    public String getLabel()
    {
        return this.label;
    }

    /**
     * Sets the timestamp (epoch millis) when this Edge was first active.
     * @param timestamp epoch millis, or null for untimed edges
     */
    public void setTimestamp(Long timestamp)
    {
        this.timestamp = timestamp;
    }

    /**
     * Gets the timestamp (epoch millis) when this Edge was first active.
     * @return epoch millis, or null if this is an untimed/static Edge
     */
    public Long getTimestamp()
    {
        return this.timestamp;
    }

    /**
     * Sets the end timestamp (epoch millis) for interval-based edges.
     * @param endTimestamp epoch millis, or null for point-in-time or open-ended edges
     */
    public void setEndTimestamp(Long endTimestamp)
    {
        this.endTimestamp = endTimestamp;
    }

    /**
     * Gets the end timestamp (epoch millis) for interval-based edges.
     * @return epoch millis, or null if this is a point-in-time or open-ended Edge
     */
    public Long getEndTimestamp()
    {
        return this.endTimestamp;
    }

    /**
     * Checks whether this Edge is active at a specific point in time.
     * Untimed edges (null timestamp) are always considered active.
     * Point-in-time edges (no endTimestamp) are active only at their exact timestamp.
     * Interval edges are active within [timestamp, endTimestamp].
     *
     * @param time the time to check (epoch millis)
     * @return true if the Edge is active at the given time
     */
    public boolean isActiveAt(long time)
    {
        if (timestamp == null) return true; // untimed = always active
        if (endTimestamp == null) return timestamp == time;
        return time >= timestamp && time <= endTimestamp;
    }

    /**
     * Checks whether this Edge is active during any part of the given time range.
     * Untimed edges are always considered active.
     *
     * @param start range start (epoch millis, inclusive)
     * @param end range end (epoch millis, inclusive)
     * @return true if the Edge overlaps with [start, end]
     */
    public boolean isActiveDuring(long start, long end)
    {
        if (timestamp == null) return true; // untimed = always active
        long edgeStart = timestamp;
        long edgeEnd = (endTimestamp != null) ? endTimestamp : timestamp;
        return edgeStart <= end && edgeEnd >= start;
    }

    /**
     * Two Edges are equal if they connect the same vertices (in either order),
     * have the same type, and the same weight.
     */
    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Edge other = (Edge) obj;
        if (Float.compare(weight, other.weight) != 0) return false;
        if (!java.util.Objects.equals(edgeType, other.edgeType)) return false;
        // Undirected: (v1,v2) == (v2,v1)
        boolean sameOrder = java.util.Objects.equals(vertex1, other.vertex1)
                         && java.util.Objects.equals(vertex2, other.vertex2);
        boolean reverseOrder = java.util.Objects.equals(vertex1, other.vertex2)
                            && java.util.Objects.equals(vertex2, other.vertex1);
        return sameOrder || reverseOrder;
    }

    /**
     * Hash code consistent with {@link #equals}: order-independent on vertices.
     */
    @Override
    public int hashCode()
    {
        // Use addition so vertex order doesn't matter
        int vertexHash = (vertex1 == null ? 0 : vertex1.hashCode())
                       + (vertex2 == null ? 0 : vertex2.hashCode());
        return 31 * (31 * vertexHash + (edgeType == null ? 0 : edgeType.hashCode()))
             + Float.floatToIntBits(weight);
    }

    /**
     * Human-readable representation: "Edge[v1--v2, type=f, weight=1.0]".
     */
    @Override
    public String toString()
    {
        return String.format("Edge[%s--%s, type=%s, weight=%.1f]",
                vertex1, vertex2, edgeType, weight);
    }

}


