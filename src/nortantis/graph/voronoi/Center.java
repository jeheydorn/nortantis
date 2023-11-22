package nortantis.graph.voronoi;

import java.util.ArrayList;
import java.util.Set;

import nortantis.Biome;
import nortantis.IconDrawer;
import nortantis.Region;
import nortantis.TectonicPlate;
import nortantis.TreeType;
import nortantis.graph.geom.Point;
import nortantis.graph.geom.Rectangle;

/**
 * Center.java
 *
 * @author Connor
 */
public class Center
{

	public int index;
	public Point loc;
	/**
	 * Centroid of Voronoi corners.
	 */
	private Point centroid;
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

	private void calcCentroid()
	{
		double xSum = 0;
		double ySum = 0;
		for (Corner corner: corners)
		{
			xSum += corner.loc.x;
			ySum += corner.loc.y;
		}
		centroid = new Point(xSum / corners.size(), ySum / corners.size());
	}
	
	/**
	 * Get's the centroid of the Voronoi corners.
	 */
	public Point getCentroid()
	{
		if (centroid == null)
		{
			calcCentroid();
		}
		return centroid;
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
			if (edge.river >= VoronoiGraph.riversThisSizeOrSmallerWillNotBeDrawn)
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
			if (bounds.inBounds(corner.loc))
			{
				return true;
			}
		}
		return false;
	}

	public Rectangle createBoundingBoxIncludingPossibleNoisyEdges()
	{
		// Start at a point we know is inside the desired bounds.
		Rectangle bounds = new Rectangle(loc.x, loc.y, 0, 0);

		// Add neighbor's centroid to the bounds. I'm doing this instead of adding each corner to the bounds because noisy edges
		// can extend as far as the centroid of a neighbor.
		for (Center neighbor : neighbors)
		{
			bounds = bounds.add(neighbor.loc);
		}

		return bounds;
	}
}
