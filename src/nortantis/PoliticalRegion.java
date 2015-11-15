package nortantis;

import hoten.geom.Point;
import hoten.voronoi.Center;

import java.awt.Color;
import java.util.*;

import util.Pair;

public class PoliticalRegion
{
	private Set<Center> centers;
	public Set<Center> getCenters() { return Collections.unmodifiableSet(centers); }
	public int id;
	public Set<PoliticalRegion> neighbors;
	public Pair<Color> colors;
	
	public PoliticalRegion()
	{
		this.centers = new HashSet<>();
		this.neighbors = new HashSet<>();
	}
	
	public void addAll(Collection<Center> toAdd)
	{
		for (Center c : toAdd)
		{
			add(c);
		}
	}
	
	public void removeAll(Collection<Center> toRemove)
	{
		for (Center c : toRemove)
		{
			remove(c);
		}
	}
	
	public void clear()
	{
		for (Center c : centers)
		{
			c.region = null;		
		}
		centers.clear();
	}
	
	public void add(Center c)
	{
		boolean addResult = centers.add(c);
		assert addResult == (c.region != this);
		c.region = this;		
	}
	
	public int size() { return centers.size(); }
	
	public void remove(Center c)
	{
		boolean removeResult = centers.remove(c);
		assert removeResult == (c.region == this);
		if (c.region == this)
		{
			c.region = null;
		}
	}
	
	public boolean contains(Center c)
	{
		return centers.contains(c);
	}
	
	public Point findCentroid()
	{
		return GraphImpl.findCentroid(centers);
	}
	
	public void findNeighbors()
	{
		neighbors.clear();
		for (Center c : centers)
		{
			for (Center n : c.neighbors)
			{
				if (n.region != this && n.region != null)
				{
					neighbors.add(n.region);
				}
			}
		}
	}

}
