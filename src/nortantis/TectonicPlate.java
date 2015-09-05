package nortantis;

import hoten.geom.Point;
import hoten.voronoi.Center;

import java.util.HashSet;
import java.util.Set;

enum PlateType {Oceanic, Continental};

public class TectonicPlate
{
	PlateType type;
	double growthProbability;
	PolarCoordinate velocity;
	Set<Center> centers;
	
	// This is needed to give the object a deterministic hash code. If I use the object's address as the hash
	// code, it may change from one run to the next, and so HashSet iterates over the objects in a different
	// order sometimes.
	private int id;
	static int nextID = 0;
	public static void resetIds()
	{
		nextID = 0;
	}
	
	public TectonicPlate(double growthProbability)
	{
		this.growthProbability = growthProbability;
		this.id = nextID++;
		centers = new HashSet<>();
	}
	
	@Override
	public int hashCode()
	{
		return id;
	}
	
	public Point findCentroid()
	{
		Point centroid = new Point(0, 0);
		for (Center c : centers)
		{
			Point p = c.loc;
			centroid.x += p.x;
			centroid.y += p.y;
		}
		centroid.x /= centers.size();
		centroid.y /= centers.size();
		
		return centroid;
	}

		
}
