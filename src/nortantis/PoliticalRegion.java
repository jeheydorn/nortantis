package nortantis;

import hoten.voronoi.Center;

import java.util.*;

public class PoliticalRegion
{
	private Set<Center> centers;
	public Set<Center> getCenters() { return Collections.unmodifiableSet(centers); }
	
	public PoliticalRegion(GraphImpl graph)
	{
		this.centers = new CenterSet(graph.centers);
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
		removeAll(centers);
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
	
}
