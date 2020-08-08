package hoten.voronoi;

import java.util.ArrayList;
import java.util.Set;

import hoten.geom.Point;
import nortantis.Biome;
import nortantis.WorldGraph;
import nortantis.IconDrawer;
import nortantis.Region;
import nortantis.TectonicPlate;
import nortantis.TreeType;

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
    
	public long treeSeed;
    
    public Center() {
    }

    public Center(Point loc) {
        this.loc = loc;
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
		assert width > 0;
		return width;
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
		float neighborsInSamePlateCOunt = 0;
 		for (Center neighbor : neighbors)
		{
			if (tectonicPlate != neighbor.tectonicPlate) 
				neighborsNotInSamePlateCount++;
			else
				neighborsInSamePlateCOunt++;
		}
 		neighborsNotInSamePlateRatio = neighborsNotInSamePlateCount / neighborsInSamePlateCOunt;
	}
	
	public boolean isRiver()
	{
		for (Edge edge : borders)
		{
			if (edge.river >= VoronoiGraph.riversThinnerThanThisWillNotBeDrawn)
			{
				return true;
			}
		}
		return false;
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

}
