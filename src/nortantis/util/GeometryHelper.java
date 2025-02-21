package nortantis.util;

import nortantis.geom.Point;

public class GeometryHelper
{
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
		double dx = p2.x - p1.x;
		double dy = p2.y - p1.y;
		double t = ((circleCenter.x - p1.x) * dx + (circleCenter.y - p1.y) * dy) / (dx * dx + dy * dy);

		// Clamp t to the range [0, 1]
		t = Math.max(0, Math.min(1, t));

		// Find the closest point on the line segment to the circle's center
		Point closestPoint = new Point(p1.x + t * dx, p1.y + t * dy);

		// Check if the distance from the closest point to the circle's center is less than or equal to the radius
		double distanceToClosestPoint = closestPoint.distanceTo(circleCenter);

		return distanceToClosestPoint <= radius;
	}
}
