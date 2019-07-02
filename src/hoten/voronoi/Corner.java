package hoten.voronoi;

import java.util.ArrayList;

import hoten.geom.Point;

/**
 * Corner.java
 *
 * @author Connor
 */
public class Corner
{
	
    public ArrayList<Center> touches = new ArrayList<>();
    public ArrayList<Corner> adjacent = new ArrayList<>();
    public ArrayList<Edge> protrudes = new ArrayList<>();
    public Point loc;
    public int index;
    public boolean border;
    public double elevation;
    public boolean water, ocean, coast;
    public int river;
    public double moisture;
    
    public Corner lowestNeighbor;
    boolean findingRivers = false; // to avoid infinite recursion as we wind our way to the sea

	public boolean createRivers() {
		// We need to increment flags for rivers and build lakes where they need to be by making them water and raising their elevation.
		if (ocean || coast) return true; // no need to go any further, but rivers coming to me look good
		
		// Find the neighbor with an elevation lower than mine
		if (lowestNeighbor == null && !findingRivers) {
			for (Corner neighbor : adjacent) {
				// I am not sure how, but it seems possible that one of my adjacents is me! 
				if (!neighbor.findingRivers && (neighbor != this) && ((lowestNeighbor == null) 
						|| (lowestNeighbor.elevation > neighbor.elevation))) {
					lowestNeighbor = neighbor;
				}
			}
		}
		
		if (lowestNeighbor == null) return false; // if we STILL did not find a good point, we are all done
		
		if (lowestNeighbor == this) {
			return false;
		}
		
		if (lowestNeighbor.elevation >= elevation) {
			lowestNeighbor.elevation = elevation * 0.9999; // Make it a little lower than me
		}

		// recursive call
		findingRivers = true;
		boolean likesRiver = lowestNeighbor.createRivers();

		if (likesRiver) {
			river++;
			lookupEdgeFromCorner(lowestNeighbor).river++;
			findingRivers = false; // only set it back if we like our rivers
			return true;
		}
		else return false;
	}
	
	public Edge lookupEdgeFromCorner(Corner c) {
        for (Edge e : protrudes) {
            if (e.v0 == c || e.v1 == c) {
                return e;
            }
        }
        return null;
    }

	// This is needed to give the object a deterministic hash code. If I use the object's address as the hash
	// code, it may change from one run to the next, and so HashSet iterates over the objects in a different
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
		
		return index == ((Corner)other).index;
	}
}
