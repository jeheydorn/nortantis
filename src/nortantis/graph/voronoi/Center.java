package nortantis.graph.voronoi;

import java.awt.Polygon;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import nortantis.Biome;
import nortantis.IconDrawer;
import nortantis.Region;
import nortantis.TectonicPlate;
import nortantis.TreeType;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;

/**
 * Center.java
 *
 * @author Connor
 */
public class Center
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
	public Point originalLoc;

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

	public double findHight()
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

	public Set<TreeType> getTreeTypes()
	{
		return IconDrawer.getTreeTypesForBiome(biome);
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

	public boolean isInBounds(Rectangle bounds)
	{
		for (Corner corner : corners)
		{
			if (bounds.contains(corner.loc))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Determines whether this center contains a given point, not including noisy edges. Note - I left this code in case I want it someday,
	 * but it's not really tested.
	 * 
	 * @param point
	 * @return
	 */
	public boolean contains(Point point)
	{
		Area area = createArea();
		return area.contains(point.x, point.y);
	}

	/**
	 * Creates an Area object that for this center. This does not include noisy edges. Note - I left this code in case I want it someday,
	 * but it's not really tested.
	 */
	private Area createArea()
	{
		Polygon p = new Polygon();

		// Bug - The v0 and v1 corners of the edges returned below are not guaranteed to be in any particular order, so some might be
		// backwards.
		// This means the resulting area could be missing pieces.
		List<Edge> ordered = orderEdgesAroundCenter();
		{
			for (Edge edge : ordered)
			{
				if (edge.v0 != null)
				{
					p.addPoint((int) edge.v0.loc.x, (int) edge.v0.loc.y);
				}
			}
		}

		return new Area(p);
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
		}
		while (!remaining.isEmpty() && currentEdge != null);

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

	public boolean isSinglePolygonIsland()
	{
		if (isWater)
		{
			return false;
		}

		for (Center neighbor : neighbors)
		{
			if (!neighbor.isWater)
			{
				return false;
			}
		}

		return true;
	}

	public boolean isSinglePolygonWater()
	{
		if (!isWater)
		{
			return false;
		}

		for (Center neighbor : neighbors)
		{
			if (neighbor.isWater)
			{
				return false;
			}
		}

		return true;
	}

	/**
	 * Determines whether this center can draw without overlapping other polygons. This is defined as being able to draw a straight line
	 * from each corner to the center's center such that that line does not overlap any edges of this center.
	 */
	public boolean isWellFormedForDrawing()
	{
		for (Corner corner : corners)
		{
			Line2D.Double line = new Line2D.Double(corner.loc.x, corner.loc.y, loc.x, loc.y);
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

				Line2D.Double edgeLine = new Line2D.Double(edge.v0.loc.x, edge.v0.loc.y, edge.v1.loc.x, edge.v1.loc.y);
				if (line.intersectsLine(edgeLine))
				{
					return false;
				}
			}
		}
		return true;
	}
	
}
