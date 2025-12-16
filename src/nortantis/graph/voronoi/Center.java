package nortantis.graph.voronoi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import nortantis.Biome;
import nortantis.Region;
import nortantis.TectonicPlate;
import nortantis.geom.IntPoint;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.util.ComparableCounter;
import nortantis.util.Counter;

public class Center implements Comparable<Center>
{

	public int index;
	public Point loc;
	public ArrayList<Corner> corners = new ArrayList<>();
	public ArrayList<Center> neighbors = new ArrayList<>();
	public ArrayList<Edge> borders = new ArrayList<>();
	public boolean isBorder, isWater, isCoast;
	/***
	 * Lakes are just water with no ocean effects
	 */
	public boolean isLake;
	public boolean isMountain;
	public boolean isHill;
	public boolean isCity;
	public boolean isSandDunes;
	public double elevation;
	public double moisture;
	public Biome biome;
	public double area;
	public TectonicPlate tectonicPlate;
	public Region region;
	// neighborsNotInSamePlateRatio is only here to make GraphImpl.createTectonicPlates faster.
	public float neighborsNotInSamePlateRatio;
	public Integer mountainRangeId;

	/**
	 * Used to deterministically place trees so that edits don't cause changes in other centers.
	 */
	public long treeSeed;

	public Center()
	{
	}

	public Center(Point loc)
	{
		this.loc = loc;
	}

	/**
	 * Sets loc to the centroid of the Voronoi corners.
	 * 
	 * @return Whether there was a change.
	 */
	public boolean updateLocToCentroid()
	{
		Point centroid = calcCentroid();
		boolean result = !loc.equals(centroid);
		loc = centroid;
		return result;
	}

	private Point calcCentroid()
	{
		double xSum = 0;
		double ySum = 0;
		for (Corner corner : corners)
		{
			xSum += corner.loc.x;
			ySum += corner.loc.y;
		}
		return new Point(xSum / corners.size(), ySum / corners.size());
	}

	public double findWidth()
	{
		double minX = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;

		if (corners.size() < 2)
			return 0;

		for (Corner corner : corners)
		{
			if (corner.loc.x < minX)
			{
				minX = corner.loc.x;
			}
			if (corner.loc.x > maxX)
			{
				maxX = corner.loc.x;
			}
		}

		double width = maxX - minX;
		return width;
	}

	public double findHeight()
	{
		double minY = Double.POSITIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;

		if (corners.size() < 2)
			return 0;

		for (Corner corner : corners)
		{
			if (corner.loc.y < minY)
			{
				minY = corner.loc.y;
			}
			if (corner.loc.y > maxY)
			{
				maxY = corner.loc.y;
			}
		}

		double height = maxY - minY;
		return height;
	}

	public Corner findBottom()
	{
		double maxY = Double.NEGATIVE_INFINITY;
		Corner maxCorner = null;

		for (Corner corner : corners)
		{
			if (corner.loc.y > maxY)
			{
				maxY = corner.loc.y;
				maxCorner = corner;
			}
		}
		return maxCorner;
	}

	// This is needed to give the object a deterministic hash code. If I use the object's address as the hash
	// code, it may change from one run to the next, and so HashSet iterates over the objects in a different
	// order sometimes.
	@Override
	public int hashCode()
	{
		return index;
	}

	public void updateNeighborsNotInSamePlateCount()
	{
		float neighborsNotInSamePlateCount = 0;
		float neighborsInSamePlateCount = 0;
		for (Center neighbor : neighbors)
		{
			if (tectonicPlate != neighbor.tectonicPlate)
				neighborsNotInSamePlateCount++;
			else
				neighborsInSamePlateCount++;
		}
		neighborsNotInSamePlateRatio = neighborsNotInSamePlateCount / neighborsInSamePlateCount;
	}

	public boolean isRiver()
	{
		for (Edge edge : borders)
		{
			if (edge.isRiver())
			{
				return true;
			}
		}
		return false;
	}

	public boolean isOcean()
	{
		return isWater && !isLake;
	}

	public static double distanceBetween(Center c1, Center c2)
	{
		if (c1 == null || c2 == null)
		{
			// Good luck with that journey
			return Double.POSITIVE_INFINITY;
		}
		return c1.loc.distanceTo(c2.loc);
	}

	public boolean isInBoundsIncludingNoisyEdges(Rectangle bounds)
	{
		for (Corner corner : corners)
		{
			if (bounds.contains(corner.loc))
			{
				return true;
			}
		}

		// Noisy edges can extend in theory as far as the center of neighboring centers.
		for (Center neighbor : neighbors)
		{
			if (bounds.contains(neighbor.loc))
			{
				return true;
			}
		}

		return false;
	}

	public List<Edge> orderEdgesAroundCenter()
	{
		List<Edge> result = new ArrayList<>(borders.size());
		HashSet<Edge> remaining = new HashSet<>(borders);

		Edge start = null;
		// Make sure we start on an edge that doesn't have all null corners. This can happen, but I don't know why.
		for (Edge startCandidate : borders)
		{
			start = startCandidate;
			if (start.v0 != null || start.v1 != null)
			{
				break;
			}
		}
		if (start == null)
		{
			// None of the edges in the polygon have corners. This shouldn't happen.
			assert false;
			return result;
		}
		Edge currentEdge = start;
		do
		{
			result.add(currentEdge);
			remaining.remove(currentEdge);

			Edge next = findConnectedEdge(currentEdge, remaining);
			if (next == null)
			{
				break;
			}
			else
			{
				currentEdge = next;
			}
		} while (!remaining.isEmpty() && currentEdge != null);

		return result;
	}

	private Edge findConnectedEdge(Edge current, Set<Edge> remaining)
	{
		for (Edge edge : remaining)
		{
			if (current.v1 != null && (edge.v0 == current.v1 || edge.v1 == current.v1)
					|| current.v0 != null && (edge.v1 == current.v0 || edge.v0 == current.v0))
			{
				return edge;
			}
		}

		return null;
	}

	public boolean isWellFormedForDrawingAsPolygon()
	{
		Counter<IntPoint> counter = new ComparableCounter<>();
		for (Edge edge : borders)
		{
			if (edge.v0 != null)
			{
				counter.incrementCount(edge.v0.loc.toIntPoint());
			}

			if (edge.v1 != null)
			{
				counter.incrementCount(edge.v1.loc.toIntPoint());
			}
		}

		IntPoint mostFrequent = counter.argmax();
		if (mostFrequent != null && counter.getCount(mostFrequent) > 2)
		{
			return false;
		}
		return true;
	}

	/**
	 * Determines whether this center can draw without overlapping other polygons. This is defined as being able to draw a straight line
	 * from each corner to the center's center such that that line does not overlap any edges of this center.
	 */
	public boolean isWellFormedForDrawingPiecewise()
	{
		Point centroid = calcCentroid();
		for (Corner corner : corners)
		{
			for (Edge edge : borders)
			{
				if (edge.v0 == null || edge.v1 == null)
				{
					continue;
				}

				if (edge.v0 == corner || edge.v1 == corner)
				{
					continue;
				}

				if (linesIntersect(corner.loc, centroid, edge.v0.loc, edge.v1.loc))
				{
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Determines whether two line segments intersect.
	 * 
	 * @param line1Start
	 *            Start of the first line.
	 * @param line1End
	 *            End of the first line.
	 * @param line2Start
	 *            Start of the second line.
	 * @param line2End
	 *            End of the second line.
	 * @return Whether the lines intersect.
	 */
	private static boolean linesIntersect(Point line1Start, Point line1End, Point line2Start, Point line2End)
	{
		int o1 = orientation(line1Start, line1End, line2Start);
		int o2 = orientation(line1Start, line1End, line2End);
		int o3 = orientation(line2Start, line2End, line1Start);
		int o4 = orientation(line2Start, line2End, line1End);

		if (o1 != o2 && o3 != o4)
		{
			return true;
		}

		if (o1 == 0 && onSegment(line1Start, line2Start, line1End))
		{
			return true;
		}

		if (o2 == 0 && onSegment(line1Start, line2End, line1End))
		{
			return true;
		}

		if (o3 == 0 && onSegment(line2Start, line1Start, line2End))
		{
			return true;
		}

		if (o4 == 0 && onSegment(line2Start, line1End, line2End))
		{
			return true;
		}

		return false;
	}

	private static int orientation(Point p, Point q, Point r)
	{
		double val = (q.y - p.y) * (r.x - q.x) - (q.x - p.x) * (r.y - q.y);
		if (val == 0)
		{
			return 0;
		}
		return (val > 0) ? 1 : 2;
	}

	private static boolean onSegment(Point p, Point q, Point r)
	{
		if (q.x <= Math.max(p.x, r.x) && q.x >= Math.min(p.x, r.x) && q.y <= Math.max(p.y, r.y) && q.y >= Math.min(p.y, r.y))
		{
			return true;
		}
		return false;
	}

	public void updateCoast()
	{
		int numOcean = 0;
		int numLand = 0;
		for (Center center : neighbors)
		{
			numOcean += center.isWater ? 1 : 0;
			numLand += !center.isWater ? 1 : 0;
		}
		isCoast = numOcean > 0 && numLand > 0;
	}

	@Override
	public int compareTo(Center o)
	{
		int locComp = loc.compareTo(o.loc);
		if (locComp == 0)
		{
			return Integer.compare(hashCode(), o.hashCode());
		}
		return locComp;
	}

}
