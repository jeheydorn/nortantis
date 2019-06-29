package hoten.geom;

import java.io.Serializable;


/**
 * Point.java
 *
 * @author Connor
 */
@SuppressWarnings("serial")
public class Point implements Comparable<Point>, Serializable
{

    public static double distance(Point _coord, Point _coord0) {
        return Math.sqrt((_coord.x - _coord0.x) * (_coord.x - _coord0.x) + (_coord.y - _coord0.y) * (_coord.y - _coord0.y));
    }
    public double x, y;

    public Point(double x, double y) {
        this.x = x;
        this.y = y;
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
    
    public Point mult(double value)
    {
    	return new Point (x * value, y * value);
    }

    @Override
    public String toString() {
        return x + ", " + y;
    }

    public double l2() {
        return x * x + y * y;
    }

    public double length() {
        return Math.sqrt(x * x + y * y);
    }
    
    public static Point interpolate(Point p1, Point p2, double c)
    {
    	return new Point(c * (p1.x) + (1 - c) * p2.x, c * (p1.y)+ (1 - c) * p2.y);
    }
    
	@Override
	public int compareTo(Point other)
	{
		int c1 = Double.compare(x, other.x);
		if (c1 < 0)
			return -1;
		if (c1 > 0)
			return 1;
		
		int c2 = Double.compare(y, other.y);
		if (c2 < 0)
			return -1;
		if (c2 > 0)
			return 1;
	
		return 0;
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
	
	public java.awt.Point toAwtPoint()
	{
		return new java.awt.Point((int)x, (int)y);
	}
}
