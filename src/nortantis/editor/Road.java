package nortantis.editor;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import nortantis.Stroke;
import nortantis.geom.Point;
import nortantis.platform.Color;

public class Road
{
	/**
	 * Points in the path are stored in a resolution-invariant way, meaning that changing the display quality in the editor does not change
	 * these values.
	 */
	public List<Point> path;

	public Road(List<Point> path)
	{
		this.path = new CopyOnWriteArrayList<Point>(path);
	}
	
	public Road(Road other)
	{
		this(other.path);
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		if (obj == null)
		{
			return false;
		}
		if (getClass() != obj.getClass())
		{
			return false;
		}
		Road other = (Road) obj;
		return Objects.equals(path, other.path);
	}

	
}
