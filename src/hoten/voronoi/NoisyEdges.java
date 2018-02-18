// Annotate each edge with a noisy path, to make maps look more interesting.
// Author: amitp@cs.stanford.edu
// License: MIT

package hoten.voronoi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import hoten.geom.Point;
//import graph.*;
//import de.polygonal.math.PM_PRNG;
  
public class NoisyEdges 
{
    final double NOISY_LINE_TRADEOFF = 0.5; // low: jagged vedge; high: jagged dedge
    
    public Map<Integer, List<Point>> path0; // edge index -> Vector.<Point>
    public Map<Integer, List<Point>> path1; // edge index -> Vector.<Point>

	private double scaleMultiplyer;
    
    public NoisyEdges(double scaleMultiplyer) 
    {
       	this.scaleMultiplyer = scaleMultiplyer;
        path0 = new TreeMap<>();
    	path1 = new TreeMap<>();
    }

    // Build noisy line paths for each of the Voronoi edges. There are
    // two noisy line paths for each edge, each covering half0 the
    // distance: path0 is from v0 to the midpoint and path1 is from v1
    // to the midpoint. When drawing the polygons, one or the other
    // must be drawn in reverse order.
	public void buildNoisyEdges(VoronoiGraph map)
	{
		for (Center p : map.centers)
		{
			buildNoisyEdgesForCenter(p);
		}
	}
    
    public void buildNoisyEdgesForCenter(Center center)
    {
    	Random rand = new Random(center.noisyEdgeSeed);
		for (Edge edge : center.borders)
		{
			if (edge.d0 != null && edge.d1 != null && edge.v0 != null && edge.v1 != null
					&& path0.get(edge.index) == null)
			{
				double f = NOISY_LINE_TRADEOFF;
				Point t = Point.interpolate(edge.v0.loc, edge.d0.loc, f);
				Point q = Point.interpolate(edge.v0.loc, edge.d1.loc, f);
				Point r = Point.interpolate(edge.v1.loc, edge.d0.loc, f);
				Point s = Point.interpolate(edge.v1.loc, edge.d1.loc, f);

				int minLength = 100;
				if (!edge.d0.isWater && !edge.d1.isWater && edge.d0.regionColor != edge.d1.regionColor)
					minLength = 3;
				if (edge.d0.border != edge.d1.border)
					minLength = 3;
				if (edge.d0.isWater != edge.d1.isWater)
					minLength = 3;
				if (edge.river != 0)
					minLength = 2;

				path0.put(edge.index, buildNoisyLineSegments(rand, edge.v0.loc, t, edge.midpoint, q, minLength));
				path0.get(edge.index).add(edge.midpoint);
				path1.put(edge.index, buildNoisyLineSegments(rand, edge.v1.loc, s, edge.midpoint, r, minLength));
				path1.get(edge.index).add(edge.midpoint);
			}
		}
    }

    
	// Helper function: build a single noisy line in a quadrilateral A-B-C-D,
	// and store the output points in a Vector.
	public List<Point> buildNoisyLineSegments(Random random, Point A,
			Point B, Point C, Point D, double minLength)
	{
		List<Point> points = new ArrayList<>();

		points.add(A);
		subdivide(A, B, C, D, minLength, random, points);
		return points;
	}

	private void subdivide(Point A, Point B, Point C, Point D, double minLength, Random random, List<Point> points)
	{
		if (A.subtract(C).length() < minLength * scaleMultiplyer 
				|| B.subtract(D).length() < minLength * scaleMultiplyer) 
		{
			return;
		}
        // Subdivide the quadrilateral
        double p = nextDoubleRange(random, 0.2, 0.8); // vertical (along A-D and B-C)
        double q = nextDoubleRange(random, 0.2, 0.8); // horizontal (along A-B and D-C)
 
        // Midpoints
        Point E = Point.interpolate(A, D, p);
        Point F = Point.interpolate(B, C, p);
        Point G = Point.interpolate(A, B, q);
        Point I = Point.interpolate(D, C, q);
        
        // Central point
        Point H = Point.interpolate(E, F, q);
        
        // Divide the quad into subquads, but meet at H
        double s = 1.0 - nextDoubleRange(random, -0.4, +0.4);
        double t = 1.0 - nextDoubleRange(random, -0.4, +0.4);

        subdivide(A, Point.interpolate(G, B, s), H, Point.interpolate(E, D, t), minLength, random, points);
        points.add(H);
        subdivide(H, Point.interpolate(F, C, s), C, Point.interpolate(I, D, t), minLength, random, points);
      }

	private double nextDoubleRange(Random random, double lower, double upper)
	{
		return (random.nextDouble() * (upper - lower)) + lower;
	}
	
}

