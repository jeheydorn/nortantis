package nortantis;

import nortantis.geom.Point;
import nortantis.graph.voronoi.Center;
import nortantis.platform.Color;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a political region on the map.
 * 
 * @author joseph
 *
 */
public class Region
{
	private Set<Center> centers;

	public Set<Center> getCenters()
	{
		return Collections.unmodifiableSet(centers);
	}

	public int id;
	public Color backgroundColor;

	public Region()
	{
		// The set of centers is mutated by the background drawing thread (MapCreator.applyCenterEdits)
		// while the EDT reads it (e.g. hover highlighting in LandWaterTool). A concurrent set lets
		// readers iterate the unmodifiable view from getCenters() without a ConcurrentModificationException.
		this.centers = Collections.newSetFromMap(new ConcurrentHashMap<>());
	}

	public void addAll(Collection<Center> toAdd)
	{
		for (Center c : toAdd)
		{
			addAndSetRegion(c);
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

	public void addAndSetRegion(Center c)
	{
		centers.add(c);
		c.region = this;
	}

	public int size()
	{
		return centers.size();
	}

	public void remove(Center c)
	{
		centers.remove(c);
		if (c.region == this)
		{
			c.region = null;
		}
	}

	public boolean contains(Center c)
	{
		return centers.contains(c);
	}

	@Override
	public int hashCode()
	{
		return id;
	}
}
