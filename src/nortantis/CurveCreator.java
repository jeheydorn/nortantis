package nortantis;

import java.util.ArrayList;
import java.util.List;

import nortantis.geom.Point;

/**
 * Creates curves using centripetal Catmull-Rom splines
 */
public class CurveCreator
{
	public static final double defaultDistanceBetweenPoints = 4.0;

	/**
	 * Creates a curve from p1 to p2 inclusive by creating a centripetal Catmull-Rom spline.
	 * 
	 * @param p0
	 *            Control point for controlling the curve shape
	 * @param p1
	 *            The start of the curve
	 * @param p2
	 *            The end of the curve
	 * @param p3
	 *            Control point for controlling the curve shape
	 * @return
	 */
	public static List<Point> createCurve(Point p0, Point p1, Point p2, Point p3, double distanceBetweenPoints)
	{
		// Create enough points that you can't see the lines in the curves.
		int numPoints = (int) (p1.distanceTo(p2) * (1.0 / distanceBetweenPoints));

		if (numPoints == 0)
		{
			List<Point> result = new ArrayList<>(1);
			result.add(p1);
			result.add(p2);
			return result;
		}

		List<Point> curve = new ArrayList<Point>(numPoints + 2);

		double t0 = 0.0f;
		double t1 = calcT(t0, p0, p1);
		double t2 = calcT(t1, p1, p2);
		double t3 = calcT(t2, p2, p3);

		for (double t = t1; t < t2; t += ((t2 - t1) / numPoints))
		{
			Point A1 = p0.mult((t1 - t) / (t1 - t0)).add(p1.mult((t - t0) / (t1 - t0)));
			Point A2 = p1.mult((t2 - t) / (t2 - t1)).add(p2.mult((t - t1) / (t2 - t1)));
			Point A3 = p2.mult((t3 - t) / (t3 - t2)).add(p3.mult((t - t2) / (t3 - t2)));

			Point B1 = A1.mult((t2 - t) / (t2 - t0)).add(A2.mult((t - t0) / (t2 - t0)));
			Point B2 = A2.mult((t3 - t) / (t3 - t1)).add(A3.mult((t - t1) / (t3 - t1)));

			Point C = B1.mult((t2 - t) / (t2 - t1)).add(B2.mult((t - t1) / (t2 - t1)));

			if (!Double.isNaN(C.x) && !Double.isNaN(C.y))
			{
				curve.add(C);
			}
		}

		return curve;
	}

	/**
	 * Creates a curve that passes through the given points.
	 * 
	 * @param path
	 *            List of points that defines where the curve should go.
	 * @return the curve
	 */
	public static List<Point> createCurve(List<Point> path)
	{
		return createCurve(path, defaultDistanceBetweenPoints);
	}

	/**
	 * Creates a curve that passes through the given points.
	 * 
	 * @param path
	 *            List of points that defines where the curve should go.
	 * @param distanceBetweenPoints
	 *            Determines the number of points to add to the curve.
	 * @return the curve
	 */
	public static List<Point> createCurve(List<Point> path, double distanceBetweenPoints)
	{
		if (path == null || path.size() < 2)
		{
			return new ArrayList<>();
		}

		List<Point> pathToUse;
		if (path.size() == 2)
		{
			return new ArrayList<>(path);
		}
		else
		{
			// Add control points at the beginning and end to make the last connections curve.
			// Note that for 3 point paths, 3 points is too few for this method to create a curve because we need at least two draw points
			// and two control points. So in that case, this code is functionally required.
			final double fakeControlPointWeight = 1.0;
			Point p0 = path.get(0).add(path.get(0).subtract(path.get(1)).mult(fakeControlPointWeight));
			Point p3 = path.get(path.size() - 1)
					.add(path.get(path.size() - 1).subtract(path.get(path.size() - 2)).mult(fakeControlPointWeight));

			pathToUse = new ArrayList<>();
			pathToUse.add(p0);
			pathToUse.addAll(path);
			pathToUse.add(p3);
		}

		List<Point> curve = new ArrayList<>();

		// Add the first point to the curve
		curve.add(path.get(0));

		for (int i = 0; i < pathToUse.size() - 1; i++)
		{
			Point p0 = (i == 0) ? pathToUse.get(0) : pathToUse.get(i - 1);
			Point p1 = pathToUse.get(i);
			Point p2 = pathToUse.get(i + 1);
			Point p3 = (i == pathToUse.size() - 2) ? pathToUse.get(i + 1) : pathToUse.get(i + 2);

			List<Point> segment = createCurve(p0, p1, p2, p3, distanceBetweenPoints);

			// Add the points of the segment to the curve, excluding the first one to avoid duplicates
			for (int j = 1; j < segment.size(); j++)
			{
				curve.add(segment.get(j));
			}
		}

		// Make sure the last point was added.
		if (!curve.get(curve.size() - 1).equals(path.get(path.size() - 1)))
		{
			curve.add(path.get(path.size() - 1));
		}

		return curve;
	}

	private static double calcT(double t, Point p0, Point p1)
	{
		double a = Math.pow((p1.x - p0.x), 2.0) + Math.pow((p1.y - p0.y), 2.0);
		double b = Math.pow(a, 0.5);
		final double alpha = 0.5;
		double c = Math.pow(b, alpha);

		return (c + t);
	}
}
