package nortantis.geom;

import java.util.Objects;

public class IntPoint implements Comparable<IntPoint>
{

	public static double distance(IntPoint _coord, IntPoint _coord0)
	{
		return Math.sqrt((_coord.x - _coord0.x) * (_coord.x - _coord0.x) + (_coord.y - _coord0.y) * (_coord.y - _coord0.y));
	}

	public final int x, y;

	public IntPoint(int x, int y)
	{
		this.x = x;
		this.y = y;
	}

	public IntPoint(IntPoint other)
	{
		this.x = other.x;
		this.y = other.y;
	}

	public double distanceTo(IntPoint other)
	{
		return distance(this, other);
	}

	/**
	 * Returns a new IntPoint whose value is this point minus other.
	 */
	public IntPoint subtract(IntPoint other)
	{
		return new IntPoint(x - other.x, y - other.y);
	}

	public IntPoint add(IntPoint other)
	{
		return new IntPoint(x + other.x, y + other.y);
	}

	@Override
	public String toString()
	{
		return "(" + x + ", " + y + ")";
	}

	public double length()
	{
		return Math.sqrt(x * x + y * y);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(x, y);
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
		IntPoint other = (IntPoint) obj;
		return Double.doubleToLongBits(x) == Double.doubleToLongBits(other.x)
				&& Double.doubleToLongBits(y) == Double.doubleToLongBits(other.y);
	}

	@Override
	public int compareTo(IntPoint other)
	{
		int c2 = Integer.compare(y, other.y);
		if (c2 < 0)
			return -1;
		if (c2 > 0)
			return 1;

		int c1 = Integer.compare(x, other.x);
		if (c1 < 0)
			return -1;
		if (c1 > 0)
			return 1;

		return 0;
	}

}
