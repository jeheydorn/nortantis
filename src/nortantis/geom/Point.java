package nortantis.geom;

import java.io.Serializable;

/**
 * Point.java
 *
 * @author Connor
 */
@SuppressWarnings("serial")
public class Point implements Comparable<Point>, Serializable
{

	public static double distance(Point _coord, Point _coord0)
	{
		return Math.sqrt((_coord.x - _coord0.x) * (_coord.x - _coord0.x) + (_coord.y - _coord0.y) * (_coord.y - _coord0.y));
	}

	public double x, y;

	public Point(double x, double y)
	{
		this.x = x;
		this.y = y;
	}

	public Point(Point other)
	{
		this.x = other.x;
		this.y = other.y;
	}

	public double distanceTo(Point other)
	{
		return distance(this, other);
	}

	/**
	 * Returns a new point whose value is this point minus other.
	 */
	public Point subtract(Point other)
	{
		return new Point(x - other.x, y - other.y);
	}

	public Point add(Point other)
	{
		return new Point(x + other.x, y + other.y);
	}

	public Point add(IntPoint other)
	{
		return new Point(x + other.x, y + other.y);
	}

	public Point add(double x, double y)
	{
		return new Point(this.x + x, this.y + y);
	}

	public Point mult(double value)
	{
		return new Point(x * value, y * value);
	}

	public Point mult(double xScale, double yScale)
	{
		return new Point(x * xScale, y * yScale);
	}

	public IntPoint toIntPoint()
	{
		return new IntPoint((int) x, (int) y);
	}

	public IntPoint toIntPointRounded()
	{
		return new IntPoint((int) Math.round(x), (int) Math.round(y));
	}

	@Override
	public String toString()
	{
		return "(" + x + ", " + y + ")";
	}

	public String toJson()
	{
		return "(" + x + ", " + y + ")";
	}

	public static Point fromJSonValue(String value)
	{
		String[] pieces = value.replace("(", "").replace(")", "").split(",");
		double x = Double.parseDouble(pieces[0]);
		double y = Double.parseDouble(pieces[1]);
		return new Point(x, y);
	}

	public double length()
	{
		return Math.sqrt(x * x + y * y);
	}

	public static Point interpolate(Point p1, Point p2, double c)
	{
		return new Point(c * (p1.x) + (1 - c) * p2.x, c * (p1.y) + (1 - c) * p2.y);
	}

	public Point rotate(Point pivot, double angle)
	{
		double dx = this.x - pivot.x;
		double dy = this.y - pivot.y;
		double newX = pivot.x + dx * Math.cos(angle) - dy * Math.sin(angle);
		double newY = pivot.y + dx * Math.sin(angle) + dy * Math.cos(angle);
		return new Point(newX, newY);
	}

	@Override
	public int compareTo(Point other)
	{
		int c2 = Double.compare(y, other.y);
		if (c2 < 0)
			return -1;
		if (c2 > 0)
			return 1;

		int c1 = Double.compare(x, other.x);
		if (c1 < 0)
			return -1;
		if (c1 > 0)
			return 1;

		return 0;
	}

	public boolean isCloseEnough(Point other)
	{
		final double threshold = 0.00001;
		return Math.abs(x - other.x) <= threshold && Math.abs(y - other.y) <= threshold;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		long temp;
		temp = Double.doubleToLongBits(x);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(y);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Point other = (Point) obj;
		if (Double.doubleToLongBits(x) != Double.doubleToLongBits(other.x))
			return false;
		if (Double.doubleToLongBits(y) != Double.doubleToLongBits(other.y))
			return false;
		return true;
	}
}
