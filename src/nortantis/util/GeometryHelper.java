package nortantis.util;

import nortantis.geom.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class GeometryHelper
{
	/**
	 * Shortest distance from {@code p} to the line segment from {@code a} to {@code b}, clamping to the segment endpoints (i.e. distance to
	 * the closest point that actually lies on the segment, not the infinite line).
	 */
	public static double distanceFromPointToSegment(Point p, Point a, Point b)
	{
		double dx = b.x - a.x;
		double dy = b.y - a.y;
		double segLenSq = dx * dx + dy * dy;
		if (segLenSq == 0.0)
		{
			return p.distanceTo(a);
		}
		double t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / segLenSq;
		if (t < 0.0)
		{
			return p.distanceTo(a);
		}
		if (t > 1.0)
		{
			return p.distanceTo(b);
		}
		double projX = a.x + t * dx;
		double projY = a.y + t * dy;
		double ex = p.x - projX;
		double ey = p.y - projY;
		return Math.sqrt(ex * ex + ey * ey);
	}

	/**
	 * Determines if a line defined by two points overlaps with a circle.
	 *
	 * @param p1
	 *            The first point of the line.
	 * @param p2
	 *            The second point of the line.
	 * @param circleCenter
	 *            The center of the circle.
	 * @param radius
	 *            The radius of the circle.
	 * @return true if the line overlaps the circle, false otherwise.
	 */
	public static boolean doesLineOverlapCircle(Point p1, Point p2, Point circleCenter, double radius)
	{
		double dx = p2.x - p1.x;
		double dy = p2.y - p1.y;
		double segmentLengthSquared = dx * dx + dy * dy;
		if (segmentLengthSquared == 0.0)
		{
			// The segment is a single point. The math below divides by the segment length, and the resulting NaN fails every comparison,
			// which would report no overlap even for a point at the circle's center.
			return circleCenter.distanceTo(p1) <= radius;
		}

		// Calculate the coefficients of the line equation Ax + By + C = 0
		double A = p2.y - p1.y;
		double B = p1.x - p2.x;
		double C = p2.x * p1.y - p1.x * p2.y;

		// Calculate the distance from the center of the circle to the line
		double distance = Math.abs(A * circleCenter.x + B * circleCenter.y + C) / Math.sqrt(A * A + B * B);

		// Check if the distance is less than or equal to the radius
		if (distance > radius)
		{
			return false; // The line does not overlap the circle
		}

		// Check if the closest points on the line segment lie within the circle's radius
		double t = ((circleCenter.x - p1.x) * dx + (circleCenter.y - p1.y) * dy) / segmentLengthSquared;

		// Clamp t to the range [0, 1]
		t = Math.max(0, Math.min(1, t));

		// Find the closest point on the line segment to the circle's center
		Point closestPoint = new Point(p1.x + t * dx, p1.y + t * dy);

		// Check if the distance from the closest point to the circle's center is less than or equal to the radius
		double distanceToClosestPoint = closestPoint.distanceTo(circleCenter);

		return distanceToClosestPoint <= radius;
	}

	/**
	 * Splits {@code path} at any segments that already exist in {@code existingConnections}, returning the non-overlapping sub-paths. Newly
	 * seen segments are added to {@code existingConnections} as they are consumed.
	 */
	public static List<List<Point>> splitPathAtOverlaps(List<Point> path, Set<OrderlessPair<Point>> existingConnections)
	{
		List<List<Point>> result = new ArrayList<>();
		if (path.isEmpty())
		{
			return result;
		}
		List<Point> currentPath = new ArrayList<>();
		currentPath.add(path.get(0));
		for (int i = 0; i < path.size() - 1; i++)
		{
			Point from = path.get(i);
			Point to = path.get(i + 1);
			OrderlessPair<Point> pair = new OrderlessPair<>(from, to);
			if (existingConnections.contains(pair))
			{
				if (currentPath.size() >= 2)
				{
					result.add(currentPath);
				}
				currentPath = new ArrayList<>();
				currentPath.add(to);
			}
			else
			{
				existingConnections.add(pair);
				currentPath.add(to);
			}
		}
		if (currentPath.size() >= 2)
		{
			result.add(currentPath);
		}
		return result;
	}
}
