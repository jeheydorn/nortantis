package hoten.voronoi;

import hoten.geom.Point;

/**
 * Edge.java
 *
 * @author Connor
 */
public class Edge implements Comparable<Edge> 
{

    public int index;
    public Center d0, d1;  // Delaunay edge
    public Corner v0, v1;  // Voronoi edge
    public Point midpoint;  // halfway between v0,v1
    public int river;
    public boolean isRoad;
	public long noisyEdgeSeed;

    public void setVornoi(Corner v0, Corner v1) {
        this.v0 = v0;
        this.v1 = v1;
        midpoint = new Point((v0.loc.x + v1.loc.x) / 2, (v0.loc.y + v1.loc.y) / 2);
    }
    
	@Override
	public int compareTo(Edge other)
	{
		int c1 = compareWithNulls(v0, other.v0);
		if (c1 < 0)
			return -1;
		if (c1 > 0)
			return 1;
		
		int c2 = compareWithNulls(v1, other.v1);
		if (c2 < 0)
			return -1;
		if (c2 > 0)
			return 1;
	
		return 0;
	}
	
	private int compareWithNulls(Corner thisV, Corner otherV)
	{
		// I'm defining null objects as less than non-null objects.
		if (thisV == null && otherV == null)
			return 0;
		if (thisV == null && otherV != null)
			return -1;
		if (thisV != null && otherV == null)
			return 1;
		if (thisV != null && otherV != null)
			return thisV.loc.compareTo(otherV.loc);
		
		assert false; // impossible
		return 0;
	}
	
	public boolean isCoast()
	{
		if (d0 == null || d1 == null)
		{
			return false;
		}
		
		return d0.isWater != d1.isWater;
	}
	
	public boolean isRegionBoundary()
	{
		if (d0 == null || d1 == null)
		{
			return false;
		}
		
		return d0.region != null && d1.region != null && d0.region != d1.region;

	}
	
	@Override
	public String toString()
	{
		StringBuilder b = new StringBuilder();
		b.append("Edge: { v0: ");
		b.append(v0);
		b.append(", v1: ");
		b.append(v1);
		b.append("}");
		return b.toString();
	}
    
}
