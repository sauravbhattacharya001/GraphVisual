/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package gvisual;
/**
 * Represents a weighted, typed edge between two vertices in a social network graph.
 *
 * <p>Each edge connects two vertices (identified by string IDs) and carries a
 * relationship type (e.g., "f" for friend, "c" for classmate), an optional numeric
 * weight, a display label, and optional temporal bounds for time-windowed analysis.</p>
 *
 * <h3>Edge Types</h3>
 * <ul>
 *   <li><b>f</b> — Friend</li>
 *   <li><b>fs</b> — Familiar Stranger</li>
 *   <li><b>c</b> — Classmate</li>
 *   <li><b>s</b> — Stranger</li>
 *   <li><b>sg</b> — Study Group</li>
 * </ul>
 *
 * @author user
 */
public class edge {
    private String edgeType;
    private String vertex1;
    private String vertex2;
    private float weight;
    private String label;
    private Long timestamp;       // epoch millis (null = static/untimed edge)
    private Long endTimestamp;     // optional: for interval-based edges
    /**
     * returns the type of the edge
     * @return returns the type of the edge. Values in (f,fs,c,s,sg)
     */
    public String getType()
    {
        return edgeType;
    }

    /**
     * returns first vertex of the edge
     * @return returns first vertex of the edge
     */
    public String getVertex1()
    {
        return vertex1;
    }

    /**
     * returns second vertex of the edge
     * @return returns second vertex of the edge
     */
    public String getVertex2()
    {
        return vertex2;
    }

    /**
     * Constructor
     */
    public edge()
    {
    }

    /**
     * Constructor
     * @param edgeType Type of the edge
     * @param vertex1 vertex id
     * @param vertex2 vertex id
     */
    public edge(String edgeType,String vertex1,String vertex2)
    {
        this.edgeType = edgeType;
        this.vertex1 = vertex1;
        this.vertex2 = vertex2;
    }

    /**
     * Sets the numeric weight of this edge.
     * Weight typically represents interaction strength (e.g., frequency × duration).
     *
     * @param weight the edge weight (higher = stronger relationship)
     */
    public void setWeight(float weight)
    {
        this.weight = weight;
    }

    /**
     * Returns the numeric weight of this edge.
     *
     * @return the edge weight
     */
    public float getWeight()
    {
        return this.weight;
    }

    /**
     * Sets a display label for this edge (shown in visualizations).
     *
     * @param label the label text, or null for no label
     */
    public void setLabel(String label)
    {
        this.label = label;
    }

    /**
     * Returns the display label for this edge.
     *
     * @return the label text, or null if none set
     */
    public String getLabel()
    {
        return this.label;
    }

    /**
     * Sets the timestamp (epoch millis) when this edge was first active.
     * @param timestamp epoch millis, or null for untimed edges
     */
    public void setTimestamp(Long timestamp)
    {
        this.timestamp = timestamp;
    }

    /**
     * Gets the timestamp (epoch millis) when this edge was first active.
     * @return epoch millis, or null if this is an untimed/static edge
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
     * @return epoch millis, or null if this is a point-in-time or open-ended edge
     */
    public Long getEndTimestamp()
    {
        return this.endTimestamp;
    }

    /**
     * Checks whether this edge is active at a specific point in time.
     * Untimed edges (null timestamp) are always considered active.
     * Point-in-time edges (no endTimestamp) are active only at their exact timestamp.
     * Interval edges are active within [timestamp, endTimestamp].
     *
     * @param time the time to check (epoch millis)
     * @return true if the edge is active at the given time
     */
    public boolean isActiveAt(long time)
    {
        if (timestamp == null) return true; // untimed = always active
        if (endTimestamp == null) return timestamp == time;
        return time >= timestamp && time <= endTimestamp;
    }

    /**
     * Checks whether this edge is active during any part of the given time range.
     * Untimed edges are always considered active.
     *
     * @param start range start (epoch millis, inclusive)
     * @param end range end (epoch millis, inclusive)
     * @return true if the edge overlaps with [start, end]
     */
    public boolean isActiveDuring(long start, long end)
    {
        if (timestamp == null) return true; // untimed = always active
        long edgeStart = timestamp;
        long edgeEnd = (endTimestamp != null) ? endTimestamp : timestamp;
        return edgeStart <= end && edgeEnd >= start;
    }

}


