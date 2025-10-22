package nortantis.geom;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class RotatedRectangle
{
	final public double x, y, width, height, angle, pivotX, pivotY;

	/**
	 * 
	 * @param x
	 *            The x part of the rectangle's upper-left corner before applying rotation.
	 * @param y
	 *            The y part of the rectangle's upper-left corner before applying rotation.
	 * @param width
	 *            Rectangle's width
	 * @param height
	 *            Rectangle's height
	 * @param angle
	 *            Angle of rotation, in radians.
	 * @param pivotX
	 *            The x part of the point about which this rectangle is rotated.
	 * @param pivotY
	 *            The y part of the point about which this rectangle is rotated.
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
		this(rect.x, rect.y, rect.width, rect.height, 0, rect.x + rect.width / 2f, rect.y + rect.height / 2f);
	}

	public RotatedRectangle translate(Point t)
	{
		// To translate a rotated rectangle, we need to translate its original position (x, y) and its pivot point (pivotX, pivotY) by the
		// given translation vector (p.x, p.y). The width, height, and angle remain the same.
		return new RotatedRectangle(this.x + t.x, this.y + t.y, this.width, this.height, this.angle, this.pivotX + t.x, this.pivotY + t.y);
	}
	
	public RotatedRectangle rotateTo(double angle)
	{
		return new RotatedRectangle(x, y, width, height, angle, pivotX, pivotY);
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

	public Point upperLeftCorner()
	{
		double rotatedX = Math.cos(angle) * (x - pivotX) - Math.sin(angle) * (y - pivotY) + pivotX;
		double rotatedY = Math.sin(angle) * (x - pivotX) + Math.cos(angle) * (y - pivotY) + pivotY;
		return new Point((int) rotatedX, (int) rotatedY);
	}

	public Point upperRightCorner()
	{
		double rotatedX = Math.cos(angle) * (x + width - pivotX) - Math.sin(angle) * (y - pivotY) + pivotX;
		double rotatedY = Math.sin(angle) * (x + width - pivotX) + Math.cos(angle) * (y - pivotY) + pivotY;
		return new Point((int) rotatedX, (int) rotatedY);
	}

	public Point lowerLeftCorner()
	{
		double rotatedX = Math.cos(angle) * (x - pivotX) - Math.sin(angle) * (y + height - pivotY) + pivotX;
		double rotatedY = Math.sin(angle) * (x - pivotX) + Math.cos(angle) * (y + height - pivotY) + pivotY;
		return new Point((int) rotatedX, (int) rotatedY);
	}

	public Point lowerRightCorner()
	{
		double rotatedX = Math.cos(angle) * (x + width - pivotX) - Math.sin(angle) * (y + height - pivotY) + pivotX;
		double rotatedY = Math.sin(angle) * (x + width - pivotX) + Math.cos(angle) * (y + height - pivotY) + pivotY;
		return new Point((int) rotatedX, (int) rotatedY);
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

		if (dx > (width / 2 + radius))
		{
			return false;
		}
		if (dy > (height / 2 + radius))
		{
			return false;
		}

		if (dx <= (width / 2))
		{
			return true;
		}
		if (dy <= (height / 2))
		{
			return true;
		}

		double cornerDistanceSquared = (dx - width / 2) * (dx - width / 2) + (dy - height / 2) * (dy - height / 2);

		return (cornerDistanceSquared <= (radius * radius));
	}

	public boolean overlaps(RotatedRectangle rect)
	{
		Polygon thisPolygon = new Polygon(Arrays.asList(upperLeftCorner(), upperRightCorner(), lowerRightCorner(), lowerLeftCorner()));

		Polygon otherPolygon = new Polygon(
				Arrays.asList(rect.upperLeftCorner(), rect.upperRightCorner(), rect.lowerRightCorner(), rect.lowerLeftCorner()));

		return isPolygonsIntersecting(thisPolygon, otherPolygon);
	}

	private class Polygon
	{
		List<Point> points;

		public Polygon(List<Point> points)
		{
			this.points = points;
		}
	}

	// Adapted from an answer at
	// https://stackoverflow.com/questions/10962379/how-to-check-intersection-between-2-rotated-rectangles#:~:text=For%20each%20edge%20in%20both,found%2C%20you%20have%20an%20intersection.
	private boolean isPolygonsIntersecting(Polygon a, Polygon b)
	{
		for (Polygon polygon : new Polygon[] { a, b })
		{
			for (int i1 = 0; i1 < polygon.points.size(); i1++)
			{
				int i2 = (i1 + 1) % polygon.points.size();
				Point p1 = polygon.points.get(i1);
				Point p2 = polygon.points.get(i2);

				Point normal = new Point(p2.y - p1.y, p1.x - p2.x);

				Double minA = null, maxA = null;
				for (Point p : a.points)
				{
					double projected = normal.x * p.x + normal.y * p.y;
					if (minA == null || projected < minA)
						minA = projected;
					if (maxA == null || projected > maxA)
						maxA = projected;
				}

				Double minB = null, maxB = null;
				for (Point p : b.points)
				{
					double projected = normal.x * p.x + normal.y * p.y;
					if (minB == null || projected < minB)
						minB = projected;
					if (maxB == null || projected > maxB)
						maxB = projected;
				}

				if (maxA < minB || maxB < minA)
					return false;
			}
		}
		return true;
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
	
	public Point getPivot()
	{
		return new Point(pivotX, pivotY);
	}
	
	public RotatedRectangle addRotatedRectangleThatHasTheSameAngleAndPivot(RotatedRectangle other)
	{
		if (other == null)
		{
			return this;
		}

		assert other.angle == angle;
		assert other.pivotX == pivotX;
		assert other.pivotY == pivotY;

		nortantis.geom.Rectangle boundsNotRotated = new Rectangle(x, y, width,
				height).add(new Rectangle(other.x, other.y, other.width, other.height));
		return new RotatedRectangle(boundsNotRotated, angle, new Point(pivotX, pivotY));
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

	@Override
	public String toString()
	{
		return "RotatedRectangle [x=" + x + ", y=" + y + ", width=" + width + ", height=" + height + ", angle=" + angle + ", pivotX="
				+ pivotX + ", pivotY=" + pivotY + "]";
	}

}
