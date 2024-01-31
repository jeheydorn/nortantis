package nortantis.geom;

import java.util.Objects;

public class RotatedRectangle
{
	final public double x, y, width, height, angle, pivotX, pivotY;

	/**
	 * 
	 * @param x The x part of the rectangle's upper-left corner before applying rotation.
	 * @param y The y part of the rectangle's upper-left corner before applying rotation.
	 * @param width Rectangle's width
	 * @param height Rectangle's height
	 * @param angle Angle of rotation, in radians.
	 * @param pivotX The x part of the point about which this rectangle is rotated. 
	 * @param pivotY The y part of the point about which this rectangle is rotated.
	 */
	public RotatedRectangle(double x, double y, double width, double height, double angle, double pivotX, double pivotY)
	{
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.angle = angle;
		this.pivotX = pivotX;
		this.pivotY = pivotY;
	}
	
	public RotatedRectangle(Point loc, double width, double height, double angle, Point pivot)
	{
		this(loc.x, loc.y, width, height, angle, pivot.x, pivot.y);	
	}
	
	public RotatedRectangle(Rectangle rect, double angle, Point pivot)
	{
		this(rect.x, rect.y, rect.width, rect.height, angle, pivot.x, pivot.y);
	}
	
	public RotatedRectangle(Rectangle rect)
	{
		this(rect.x, rect.y, rect.width, rect.height, 0, rect.x + rect.width/2f, rect.y + rect.height/2f);
	}
	
	public boolean contains(Point point)
	{
		return contains(point.x, point.y);
	}

	public boolean contains(double xLoc, double yLoc)
	{
	    double cos = Math.cos(-angle);
	    double sin = Math.sin(-angle);
	    double xLocRotated = (xLoc - pivotX) * cos - (yLoc - pivotY) * sin + pivotX;
	    double yLocRotated = (xLoc - pivotX) * sin + (yLoc - pivotY) * cos + pivotY;
	    return xLocRotated >= x && xLocRotated <= x + width && yLocRotated >= y && yLocRotated <= y + height;
	}
	
	public boolean overlapsCircle(Point circleCenter, double radius)
	{
	    double cos = Math.cos(-angle);
	    double sin = Math.sin(-angle);
	    // Rotate circleCenter to match this rectangle
	    double xLocRotated = (circleCenter.x - pivotX) * cos - (circleCenter.y - pivotY) * sin + pivotX;
	    double yLocRotated = (circleCenter.x - pivotX) * sin + (circleCenter.y - pivotY) * cos + pivotY;
	    // Find the distance between the rotated circle center and the center of this rectangle
	    double dx = Math.abs((x + width / 2) - xLocRotated);
	    double dy = Math.abs((y + height / 2) - yLocRotated);

	    if (dx > (width/2 + radius)) 
	    { 
	    	return false; 
	    }
	    if (dy > (height/2 + radius)) 
	    { 
	    	return false; 
	    }

	    if (dx <= (width/2)) 
	    { 
	    	return true; 
	    } 
	    if (dy <= (height/2)) 
	    { 
	    	return true; 
	    }
	    
	    double cornerDistanceSquared = (dx - width/2)* (dx - width/2) +
                (dy - height/2) * (dy - height/2);

	    return (cornerDistanceSquared <= (radius * radius));	
	}

	public boolean overlaps(RotatedRectangle rect)
	{
	    double cos = Math.cos(-angle);
	    double sin = Math.sin(-angle);
	    double[] xCoords = {x, x + width, x + width, x};
	    double[] yCoords = {y, y, y + height, y + height};
	    double[] xCoordsRotated = new double[4];
	    double[] yCoordsRotated = new double[4];
	    for (int i = 0; i < 4; i++)
	    {
	        xCoordsRotated[i] = (xCoords[i] - pivotX) * cos - (yCoords[i] - pivotY) * sin + pivotX;
	        yCoordsRotated[i] = (xCoords[i] - pivotX) * sin + (yCoords[i] - pivotY) * cos + pivotY;
	    }
	    double[] xCoordsOther = {rect.x, rect.x + rect.width, rect.x + rect.width, rect.x};
	    double[] yCoordsOther = {rect.y, rect.y, rect.y + rect.height, rect.y + rect.height};
	    double[] xCoordsOtherRotated = new double[4];
	    double[] yCoordsOtherRotated = new double[4];
	    for (int i = 0; i < 4; i++)
	    {
	        xCoordsOtherRotated[i] = (xCoordsOther[i] - rect.pivotX) * cos - (yCoordsOther[i] - rect.pivotY) * sin + rect.pivotX;
	        yCoordsOtherRotated[i] = (xCoordsOther[i] - rect.pivotX) * sin + (yCoordsOther[i] - rect.pivotY) * cos + rect.pivotY;
	    }
	    for (int i = 0; i < 4; i++)
	    {
	        if (contains(xCoordsOtherRotated[i], yCoordsOtherRotated[i]))
	        {
	            return true;
	        }
	        if (rect.contains(xCoordsRotated[i], yCoordsRotated[i]))
	        {
	            return true;
	        }
	    }
	    return false;
	}
	
	/**
	 * Returns an axis-aligned rectangle that bounds this rotated rectangle.
	 */
	public Rectangle getBounds()
	{
	    // Find the four corners of the rotated rectangle
	    Point[] corners = new Point[4];
	    corners[0] = new Point(x, y);
	    corners[1] = new Point(x + width, y);
	    corners[2] = new Point(x, y + height);
	    corners[3] = new Point(x + width, y + height);
	    
	    double sin = Math.sin(angle);
	    double cos = Math.cos(angle);
	    
	    // Apply the rotation matrix to each corner
	    for (int i = 0; i < 4; i++)
	    {
	        double dx = corners[i].x - pivotX;
	        double dy = corners[i].y - pivotY;
	        corners[i].x = pivotX + dx * cos - dy * sin;
	        corners[i].y = pivotY + dx * sin + dy * cos;
	    }
	    
	    // Find the minimum and maximum x and y values
	    double minX = corners[0].x;
	    double maxX = corners[0].x;
	    double minY = corners[0].y;
	    double maxY = corners[0].y;
	    
	    for (int i = 1; i < 4; i++)
	    {
	        if (corners[i].x < minX)
	        {
	            minX = corners[i].x;
	        }
	        else if (corners[i].x > maxX)
	        {
	            maxX = corners[i].x;
	        }
	        
	        if (corners[i].y < minY)
	        {
	            minY = corners[i].y;
	        }
	        else if (corners[i].y > maxY)
	        {
	            maxY = corners[i].y;
	        }
	    }
	    
	    // Create a new Rectangle object with the minimum x and y values as the upper-left corner
	    // and the difference between the maximum and minimum x and y values as the width and height, respectively.
	    return new Rectangle(minX, minY, maxX - minX, maxY - minY);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(angle, height, pivotX, pivotY, width, x, y);
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
		RotatedRectangle other = (RotatedRectangle) obj;
		return Double.doubleToLongBits(angle) == Double.doubleToLongBits(other.angle)
				&& Double.doubleToLongBits(height) == Double.doubleToLongBits(other.height)
				&& Double.doubleToLongBits(pivotX) == Double.doubleToLongBits(other.pivotX)
				&& Double.doubleToLongBits(pivotY) == Double.doubleToLongBits(other.pivotY)
				&& Double.doubleToLongBits(width) == Double.doubleToLongBits(other.width)
				&& Double.doubleToLongBits(x) == Double.doubleToLongBits(other.x)
				&& Double.doubleToLongBits(y) == Double.doubleToLongBits(other.y);
	}


}
