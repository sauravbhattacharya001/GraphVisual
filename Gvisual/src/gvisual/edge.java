/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package gvisual;
/**
 *
 * @author user
 */
public class edge {
    private String edgeType;
    private String vertex1;
    private String vertex2;
    private float weight;
    private String label;
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

}


