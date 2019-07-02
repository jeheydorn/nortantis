package nortantis;

import java.util.ArrayList;
import java.util.List;

import hoten.geom.Point;

/**
 * Creates curves using centripetal Catmull–Rom splines
 */
public class CurveCreator
{
	/**
	 * Creates a curve from p1 to p2 inclusive by creating a centripetal Catmull–Rom spline.
	 * @param p0 Control point for controlling the curve shape
	 * @param p1 The start of the curve
	 * @param p2 The end of the curve
	 * @param p3 Control point for controlling the curve shape
	 * @param numPoints Approximate number of points to create
	 * @return
	 */
	public static List<Point> createCurve(Point p0, Point p1, Point p2, Point p3, int numPoints)
	{
		if (numPoints == 0)
		{
			return new ArrayList<Point>(0);
		}
		
		List<Point> curve = new ArrayList<Point>(numPoints + 2);

		double t0 = 0.0f;
		double t1 = calcT(t0, p0, p1);
		double t2 = calcT(t1, p1, p2);
		double t3 = calcT(t2, p2, p3);

		for (double t = t1; t < t2; t += ((t2 - t1) / numPoints))
		{
			Point A1 = p0.mult((t1-t)/(t1-t0)).add(p1.mult((t-t0)/(t1-t0)));
		    Point A2 = p1.mult((t2-t)/(t2-t1)).add(p2.mult((t-t1)/(t2-t1)));
		    Point A3 = p2.mult((t3-t)/(t3-t2)).add(p3.mult((t-t2)/(t3-t2)));
		    
		    Point B1 = A1.mult((t2-t)/(t2-t0)).add(A2.mult((t-t0)/(t2-t0)));
		    Point B2 = A2.mult((t3-t)/(t3-t1)).add(A3.mult((t-t1)/(t3-t1)));
		    
		    Point C = B1.mult((t2-t)/(t2-t1)).add(B2.mult((t-t1)/(t2-t1)));
		    
		    if (!Double.isNaN(C.x) && !Double.isNaN(C.y))
		    {
		    	curve.add(C);
		    }
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
