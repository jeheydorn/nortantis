package nortantis.graph.voronoi;

import java.util.Objects;

import nortantis.geom.Point;

/**
 * Edge.java
 *
 * @author Connor
 */
public class Edge implements Comparable<Edge>
{

	public int index;
	public Center d0, d1; // Delaunay edge
	public Corner v0, v1; // Voronoi edge
	public Point midpoint; // halfway between v0,v1
	public int river;
	/**
	 * Used to deterministically create noisy edges so that edits don't cause changes in other edges.
	 */
	public long noisyEdgesSeed;

	public void setVornoi(Corner v0, Corner v1)
	{
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

		if (d0.isWater == d1.isWater)
		{
			return false;
		}

		// One of the centers is land, and the other is water. It's a coast if the water is not a lake.
		if (d0.isWater)
		{
			return !d0.isLake;
		}

		return !d1.isLake;
	}

	public boolean isCoastOrLakeShore()
	{
		if (d0 == null || d1 == null)
		{
			return false;
		}

		return d0.isWater != d1.isWater;
	}

	public boolean isLakeShore()
	{
		return isCoastOrLakeShore() && !isCoast();
	}

	public boolean isOceanOrLakeOrShore()
	{
		if (d0 == null || d1 == null)
		{
			return false;
		}

		if (d0.isWater || d1.isWater || d0.isLake || d1.isLake)
		{
			return true;
		}

		return false;
	}

	public boolean isRiverTouchingOcean()
	{
		if (!isRiver())
		{
			return false;
		}

		if (v0 != null && v0.isOcean)
		{
			return true;
		}

		if (v1 != null && v1.isOcean)
		{
			return true;
		}

		return false;
	}

	public boolean isRiver()
	{
		return river > VoronoiGraph.riversThisSizeOrSmallerWillNotBeDrawn;
	}

	public boolean isRegionBoundary()
	{
		if (d0 == null || d1 == null)
		{
			return false;
		}

		return d0.region != null && d1.region != null && d0.region != d1.region;

	}

	public Corner getOtherCorner(Corner corner)
	{
		if (Objects.equals(corner, v0))
		{
			return v1;
		}
		return v0;
	}

	public Corner findCornerSharedWithEdge(Edge other)
	{
		if (v0 != null && v0.protrudesContains(other))
		{
			return v0;
		}
		if (v1 != null && v1.protrudesContains(other))
		{
			return v1;
		}
		return null;
	}

	public Corner findCornerNotSharedWithEdge(Edge other)
	{
		if (v0 != null && !v0.protrudesContains(other))
		{
			return v0;
		}
		if (v1 != null && !v1.protrudesContains(other))
		{
			return v1;
		}
		return null;
	}

	public boolean sharesCornerWith(Edge other)
	{
		if (other == null)
		{
			return false;
		}

		if (v0 != null && (v0.equals(other.v0) || v0.equals(other.v1)))
		{
			return true;
		}

		if (v1 != null && (v1.equals(other.v0) || v1.equals(other.v1)))
		{
			return true;
		}

		return false;
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

	// Override hashCode and equals methods so that HashSets order edges in a consistent order between runs. Otherwise
	// Center.orderEdgesAroundCenter can return different results based on in-memory addresses from one run to another.
	@Override
	public int hashCode()
	{
		return index;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		if (obj == null)
		{
			return false;
		}
		if (getClass() != obj.getClass())
		{
			return false;
		}
		Edge other = (Edge) obj;
		return index == other.index;
	}

}
