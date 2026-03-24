/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package gvisual;
/**
 *
 * @author user
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
     * Constructor
     */
    public Edge()
    {
    }

    /**
     * Constructor
     * @param edgeType Type of the Edge
     * @param vertex1 vertex id
     * @param vertex2 vertex id
     */
    public Edge(String edgeType,String vertex1,String vertex2)
    {
        this.edgeType = edgeType;
        this.vertex1 = vertex1;
        this.vertex2 = vertex2;
    }

    public void setWeight(float weight)
    {
        this.weight = weight;
    }

    public float getWeight()
    {
        return this.weight;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

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


