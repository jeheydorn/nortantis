package nortantis.geom;

/**
 * Rectangle.java
 *
 * @author Connor
 */
public class Rectangle
{

	final public double x, y, width, height;

	public Rectangle(double x, double y, double width, double height)
	{
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	public boolean liesOnAxes(Point p, double closeEnoughDistance)
	{
		return GenUtils.closeEnough(p.x, x, closeEnoughDistance) || GenUtils.closeEnough(p.y, y, closeEnoughDistance)
				|| GenUtils.closeEnough(p.x, getRight(), closeEnoughDistance)
				|| GenUtils.closeEnough(p.y, getBottom(), closeEnoughDistance);
	}

	public boolean contains(Rectangle other)
	{
		// Check if the left edge of the other rectangle is to the right of the left edge of this rectangle
		if (other.getLeft() < this.getLeft())
		{
			return false;
		}
		// Check if the right edge of the other rectangle is to the left of the right edge of this rectangle
		if (other.getRight() > this.getRight())
		{
			return false;
		}
		// Check if the top edge of the other rectangle is below the top edge of this rectangle
		if (other.getTop() < this.getTop())
		{
			return false;
		}
		// Check if the bottom edge of the other rectangle is above the bottom edge of this rectangle
		if (other.getBottom() > this.getBottom())
		{
			return false;
		}
		// If none of the conditions are met, return true
		return true;
	}

	public boolean contains(Point p)
	{
		return contains(p.x, p.y);
	}

	public boolean contains(double x0, double y0)
	{
		if (x0 < x || x0 > getRight() || y0 < y || y0 > getBottom())
		{
			return false;
		}
		return true;
	}

	public boolean overlaps(Rectangle other)
	{
		if (contains(other.x, other.y))
		{
			return true;
		}

		if (other.contains(x, y))
		{
			return true;
		}

		if (contains(other.x + other.width, other.y))
		{
			return true;
		}

		if (other.contains(x + width, y))
		{
			return true;
		}

		if (contains(other.x, other.y + other.height))
		{
			return true;
		}

		if (other.contains(x, y + height))
		{
			return true;
		}

		if (contains(other.x + other.width, other.y + other.height))
		{
			return true;
		}

		if (other.contains(x + width, y + height))
		{
			return true;
		}

		return false;
	}

	public Rectangle add(Point point)
	{
		return add(point.x, point.y);
	}

	public Rectangle add(double xToAdd, double yToAdd)
	{
		double newX = x, newY = y, newWidth = width, newHeight = height;
		if (xToAdd > x + width)
		{
			newWidth = xToAdd - x;
		}
		else if (xToAdd < x)
		{
			newX = xToAdd;
			newWidth = width + (x - xToAdd);
		}

		if (yToAdd > y + height)
		{
			newHeight = yToAdd - y;
		}
		else if (yToAdd < y)
		{
			newY = yToAdd;
			newHeight = height + (y - yToAdd);
		}

		return new Rectangle(newX, newY, newWidth, newHeight);
	}

	/**
	 * Returns a new rectangle expanded to include both this rectangle and the one passed in.
	 */
	public Rectangle add(Rectangle other)
	{
		return add(other.x, other.y).add(other.x, other.y + other.height).add(other.x + other.width, other.y).add(other.x + other.width,
				other.y + other.height);
	}

	public Rectangle addCircle(Point loc, Double radius)
	{
		Rectangle rect = new Rectangle(loc.x - radius, loc.y - radius, radius * 2, radius * 2);
		return add(rect);
	}

	/**
	 * Returns a new rectangle with the same centroid as this one but with the width and height expanded by the given width and height.
	 */
	public Rectangle pad(double paddWidth, double paddHeight)
	{
		return new Rectangle(x - paddWidth / 2.0, y - paddHeight / 2.0, width + paddWidth, height + paddHeight);
	}

	public Rectangle floor()
	{
		return new Rectangle((int) x, (int) y, (int) width, (int) height);
	}
	
	public Rectangle translate(double xTranslation, double yTranslation)
	{
		return new Rectangle(x + xTranslation, y + yTranslation, width, height);
	}

	public Rectangle findIntersection(Rectangle r2)
	{
		double x1 = Math.max(this.x, r2.x);
		double y1 = Math.max(this.y, r2.y);
		double x2 = Math.min(this.x + this.width, r2.x + r2.width);
		double y2 = Math.min(this.y + this.height, r2.y + r2.height);

		if (x1 < x2 && y1 < y2)
		{
			return new Rectangle(x1, y1, x2 - x1, y2 - y1);
		}
		else
		{
			return null;
		}
	}

	public IntRectangle toIntRectangle()
	{
		return new IntRectangle((int) x, (int) y, (int) width, (int) height);
	}

	public Point upperLeftCorner()
	{
		return new Point(x, y);
	}

	public Point getCenter()
	{
		return new Point(x + width / 2.0, y + height / 2.0);
	}

	public double getRight()
	{
		return x + width;
	}

	public double getLeft()
	{
		return x;
	}

	public double getBottom()
	{
		return y + height;
	}

	public double getTop()
	{
		return y;
	}

	@Override
	public String toString()
	{
		return "[x=" + x + ", y=" + y + ", width=" + width + ", height=" + height + "]";
	}
}
