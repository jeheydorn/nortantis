package nortantis.util;

import java.awt.Point;


public class IntRectangle
{

	final public int x, y, width, height;

	public IntRectangle(int x, int y, int width, int height)
	{
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}
	
	public boolean contains(int x0, int y0)
	{
    	if (x0 < x || x0 > x + width || y0 < y || y0 > y + height) 
    	{
            return false;
    	}
        return true;
	}

	public boolean contains(Point p) 
    {
		return contains(p.x, p.y);
    }

	public boolean contains(IntRectangle other)
	{
		return contains(other.x, other.y) && contains(other.x + other.width, other.y + other.height);
	}
}
