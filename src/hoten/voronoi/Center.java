package hoten.voronoi;

import hoten.geom.Point;

import java.util.ArrayList;
import java.util.Set;

import nortantis.PoliticalRegion;
import nortantis.TectonicPlate;

/**
 * Center.java
 *
 * @author Connor
 */
public class Center
{

    public int index;
    public Point loc;
    public ArrayList<Corner> corners = new ArrayList<>();//good
    public ArrayList<Center> neighbors = new ArrayList<>();//good
    public ArrayList<Edge> borders = new ArrayList<>();
    public boolean border, ocean, water, coast;
    public boolean mountain;
    public boolean hill;
    public double elevation;
    public double moisture;
	public Enum<?> biome;
    public double area;
    public TectonicPlate tectonicPlate;
    public PoliticalRegion region;
    // neighborsNotInSamePlateCount is only here to make GraphImpl.createTectonicPlates faster.
    public int neighborsNotInSamePlateCount;
    public Integer mountainRangeId;
    
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
		neighborsNotInSamePlateCount = 0;
 		for (Center neighbor : neighbors)
		{
			if (tectonicPlate != neighbor.tectonicPlate) 
				neighborsNotInSamePlateCount++;
		}
	}
    
}
