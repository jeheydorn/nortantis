package nortantis.graph.voronoi;

import nortantis.geom.Point;

import java.util.ArrayList;

public class Corner
{

	public ArrayList<Center> touches = new ArrayList<>();
	public ArrayList<Corner> adjacent = new ArrayList<>();
	public ArrayList<Edge> protrudes = new ArrayList<>();
	public Point loc;
	public Point originalLoc;
	public int index;
	public boolean isBorder;
	public double elevation;
	public boolean isWater, isOcean, isCoast;
	public int river;
	public double moisture;

	public Edge lookupEdgeFromCorner(Corner c)
	{
		for (Edge e : protrudes)
		{
			if (e.v0 == c || e.v1 == c)
			{
				return e;
			}
		}
		return null;
	}

	public void resetLocToOriginal()
	{
		loc = originalLoc;
	}

	@Override
	public String toString()
	{
		return "Corner [loc=" + loc + ", index=" + index + "]";
	}

	// This is needed to give the object a deterministic hash code. If I use the
	// object's address as the hash
	// code, it may change from one run to the next, and so HashSet iterates
	// over the objects in a different
	// order sometimes.
	@Override
	public int hashCode()
	{
		return index;
	}

	@Override
	public boolean equals(Object other)
	{
		if (other == null || !(other instanceof Corner))
		{
			return false;
		}

		return index == ((Corner) other).index;
	}

}
