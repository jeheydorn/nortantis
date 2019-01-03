package nortantis;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import hoten.voronoi.Corner;

public class River implements Iterable<Corner>
{
	private Set<Corner> corners;
	private int width;
	
	public River()
	{
		corners = new HashSet<>();
		width = 0;
	}
	
	public void add(Corner corner)
	{
		width = Math.max(width, corner.river);
		corners.add(corner);
	}
	
	public void addAll(River other)
	{
		width = Math.max(width, other.width);
		corners.addAll(other.corners);
	}
	
	public int size()
	{
		return corners.size();
	}
	
	public int getWidth()
	{
		return width;
	}
	
	public Collection<Corner> getCorners()
	{
		return Collections.unmodifiableCollection(corners);
	}

	@Override
	public Iterator<Corner> iterator()
	{
		return corners.iterator();
	}
}
