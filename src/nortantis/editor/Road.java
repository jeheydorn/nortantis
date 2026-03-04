package nortantis.editor;

import nortantis.geom.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class Road
{
	/**
	 * Points in the path are stored in a resolution-invariant way, meaning that changing the display quality in the editor does not change
	 * these values.
	 */
	public CopyOnWriteArrayList<Point> path;

	public Road(List<Point> path)
	{
		this.path = new CopyOnWriteArrayList<Point>(deduplicateConsecutive(path));
	}

	private static List<Point> deduplicateConsecutive(List<Point> path)
	{
		List<Point> result = new ArrayList<>(path.size());
		for (Point point : path)
		{
			if (result.isEmpty() || !result.get(result.size() - 1).isCloseEnough(point))
			{
				result.add(point);
			}
		}
		return result;
	}

	public Road(Road other)
	{
		this(other.path);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(path);
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

	@Override
	public String toString()
	{
		return "Road [path=" + path + "]";
	}

}
