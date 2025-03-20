// Annotate each edge with a noisy path, to make maps look more interesting.
// Author: amitp@cs.stanford.edu
// License: MIT

package nortantis.graph.voronoi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;

//import graph.*;
//import de.polygonal.math.PM_PRNG;
import nortantis.CurveCreator;
import nortantis.MapSettings.LineStyle;
import nortantis.geom.Point;

public class NoisyEdges
{
	final double NOISY_LINE_TRADEOFF = 0.5; // low: jagged v-edge; high: jagged
											// d-edge

	private LineStyle lineStyle;
	private Map<Integer, List<Point>> paths; // edge index -> List of points in
												// that edge.

	// Maps edge index to a list of points that draw the same position in path0
	// but with curves
	private Map<Integer, List<Point>> curves;

	private double scaleMultiplyer;
	private boolean isForFrayedBorder;

	public NoisyEdges(double scaleMultiplyer, LineStyle style, boolean isForFrayedBorder)
	{
		this.scaleMultiplyer = scaleMultiplyer;
		paths = new TreeMap<>();
		curves = new TreeMap<>();
		lineStyle = style;
		this.isForFrayedBorder = isForFrayedBorder;
	}

	// Build noisy line paths for each of the Voronoi edges. There are
	// two noisy line paths for each edge, each covering half0 the
	// distance: path0 is from v0 to the midpoint and path1 is from v1
	// to the midpoint. When drawing the polygons, one or the other
	// must be drawn in reverse order.
	public void buildNoisyEdges(VoronoiGraph map)
	{
		for (Center c : map.centers)
		{
			buildNoisyEdgesForCenter(c, false);
		}
	}

	public void buildNoisyEdgesForCenter(Center center, boolean forceRebuild)
	{
		if (lineStyle.equals(LineStyle.Splines) || lineStyle.equals(LineStyle.SplinesWithSmoothedCoastlines))
		{
			buildCurvesForCenter(center, forceRebuild);
		}
		else
		{
			buildNoisyLineEdgesForCenter(center, forceRebuild);
		}
	}

	public void buildNoisyLineEdgesForCenter(Center center, boolean forceRebuild)
	{
		for (Edge edge : center.borders)
		{
			if (edge.d0 != null && edge.d1 != null && edge.v0 != null && edge.v1 != null && (forceRebuild || paths.get(edge.index) == null))
			{
				Random rand = new Random(edge.noisyEdgesSeed);

				double f = NOISY_LINE_TRADEOFF;
				Point t = Point.interpolate(edge.v0.loc, edge.d0.loc, f);
				Point q = Point.interpolate(edge.v0.loc, edge.d1.loc, f);
				Point r = Point.interpolate(edge.v1.loc, edge.d0.loc, f);
				Point s = Point.interpolate(edge.v1.loc, edge.d1.loc, f);

				int minLength = getNoisyEdgeMinLength(edge);

				List<Point> path0 = buildNoisyLineSegments(rand, edge.v0.loc, t, edge.midpoint, q, minLength); // List
																												// of
																												// points
																												// in
																												// that
																												// edge
																												// from
																												// corner
																												// v0
																												// to
																												// the
																												// midpoint
																												// of
																												// the
																												// edge
				path0.add(edge.midpoint);
				List<Point> path1 = buildNoisyLineSegments(rand, edge.v1.loc, s, edge.midpoint, r, minLength); // List
																												// of
																												// points
																												// in
																												// that
																												// edge
																												// from
																												// corner
																												// v1
																												// to
																												// the
																												// midpoint
																												// of
																												// the
																												// edge
				// Ad path1 in reverse order.
				for (int i = path1.size() - 1; i >= 0; i--)
				{
					path0.add(path1.get(i));
				}
				paths.put(edge.index, path0);
			}
		}
	}

	// Helper function: build a single noisy line in a quadrilateral A-B-C-D,
	// and store the output points in a Vector.
	public List<Point> buildNoisyLineSegments(Random random, Point A, Point B, Point C, Point D, double minLength)
	{
		List<Point> points = new ArrayList<>();

		points.add(A);
		subdivide(A, B, C, D, minLength, random, points);
		return points;
	}

	private void subdivide(Point A, Point B, Point C, Point D, double minLength, Random random, List<Point> points)
	{
		if (A.subtract(C).length() < minLength * scaleMultiplyer || B.subtract(D).length() < minLength * scaleMultiplyer)
		{
			return;
		}
		// Subdivide the quadrilateral
		double p = nextDoubleRange(random, 0.2, 0.8); // vertical (along A-D and
														// B-C)
		double q = nextDoubleRange(random, 0.2, 0.8); // horizontal (along A-B
														// and D-C)

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

	private void buildCurvesForCenter(Center center, boolean forceRebuild)
	{
		for (Edge edge : center.borders)
		{
			if (edge.d0 != null && edge.d1 != null && edge.v0 != null && edge.v1 != null
					&& (forceRebuild || curves.get(edge.index) == null))
			{
				if (!shouldDrawEdge(edge))
				{
					curves.put(edge.index, Arrays.asList(edge.v0.loc, edge.v1.loc));
					continue;
				}

				Point p0 = findPrevOrNextPointOnCurve(edge, edge.v0);
				Point p1 = edge.v0.loc;
				Point p2 = edge.v1.loc;
				Point p3 = findPrevOrNextPointOnCurve(edge, edge.v1);
				
				List<Point> curve = new LinkedList<>();
				curve.addAll(CurveCreator.createCurve(p0, p1, p2, p3, CurveCreator.defaultDistanceBetweenPoints));
				if (curve.isEmpty() || !curve.get(0).equals(edge.v0.loc))
				{
					curve.add(0, edge.v0.loc);
				}
				if (!curve.get(curve.size() - 1).equals(edge.v1.loc))
				{
					curve.add(edge.v1.loc);
				}

				curves.put(edge.index, curve);
			}
		}
	}

	/**
	 * Find the previous point when drawing a curve and the curve segment currently being drawn doesn't contain that point. That point is
	 * needed to maintain C1 continuity at corners in the voronoi graph.
	 * 
	 * @param edge
	 * @param firstPointOnCurve
	 * @param corner
	 *            This must be either edge.v0 or edge.v1. Whichever is the first point in the curve.
	 * @return
	 */
	private Point findPrevOrNextPointOnCurve(Edge edge, Corner corner)
	{
		// Use the last point in the edge connecting to this one.
		Edge toFollow = findEdgeToFollow(corner, edge, null);
		if (toFollow == null)
		{
			// p1 is the first or last point in a river / coast line / region
			// boundary.
			return corner.loc;
		}

		if (toFollow.v0.equals(corner))
		{
			return toFollow.v1.loc;
		}
		else if (toFollow.v1.equals(corner))
		{
			return toFollow.v0.loc;
		}
		else
		{
			// Should be impossible
			assert false;
			return corner.loc;
		}
	}

	/**
	 * Determines which edge curves we should follow since there are always multiple directions curves can go.
	 * 
	 * @param corner
	 *            Corner to search from
	 * @param edge
	 *            Edge to follow from
	 * @return Edge to follow, or null if there is none.
	 */
	public Edge findEdgeToFollow(Corner corner, Edge edge)
	{
		return findEdgeToFollow(corner, edge, null);
	}

	public Edge findEdgeToFollow(Corner corner, Edge edge, Edge prev)
	{
		EdgeDrawType type = getEdgeDrawType(edge);

		// The reason for the checks for !other.sharesCornerWith(prev) is so that if prev is not null, we don't end up finding a path that
		// takes a turn, then immediately turns back on itself and goes another way.
		if (type.equals(EdgeDrawType.Region))
		{
			for (Edge other : corner.protrudes)
			{
				if (other != edge && other != prev && !other.sharesCornerWith(prev) && getEdgeDrawType(other) == EdgeDrawType.Region)
				{
					return other;
				}
			}
			return null;
		}
		else if (type.equals(EdgeDrawType.Coast))
		{
			for (Edge other : corner.protrudes)
			{
				if (other != edge && other != prev && !other.sharesCornerWith(prev) && getEdgeDrawType(other) == EdgeDrawType.Coast)
				{
					return other;
				}
			}
			return null;

		}
		else if (type.equals(EdgeDrawType.River))
		{
			// Follow the largest river other than the one we came from. That
			// way small rivers branch off of large ones, instead of the other
			// way round.
			Optional<Edge> optional = corner.protrudes.stream().filter((other) -> other != edge && other != prev
					&& !other.sharesCornerWith(prev) && getEdgeDrawType(other) == EdgeDrawType.River)
					.max((e1, e2) -> Integer.compare(e1.river, e2.river));
			if (optional.isPresent())
			{
				return optional.get();
			}

			return null;
		}
		else if (type.equals(EdgeDrawType.FrayedBorder))
		{
			for (Edge other : corner.protrudes)
			{
				if (other != edge && other != prev && !other.sharesCornerWith(prev) && getEdgeDrawType(other) == EdgeDrawType.FrayedBorder)
				{
					return other;
				}
			}
			return null;
		}

		assert false;
		return null;
	}

	/**
	 * Determines how small lines should be segmented to when drawing noisy edges.
	 * 
	 * @param edge
	 * @return
	 */
	private int getNoisyEdgeMinLength(Edge edge)
	{
		EdgeDrawType type = getEdgeDrawType(edge);
		if (type.equals(EdgeDrawType.Region))
		{
			return 3;
		}
		if (type.equals(EdgeDrawType.Coast))
		{
			return 3;
		}
		if (type.equals(EdgeDrawType.River))
		{
			return 2;
		}
		if (type.equals(EdgeDrawType.FrayedBorder))
		{
			return 3;
		}

		return 1000; // A number big enough to not create noisy edges
	}

	public EdgeDrawType getEdgeDrawType(Edge edge)
	{
		// Changes to this method will likely also need to update
		// MapCreator.applyCenterEdits where it sets needsRebuild.
		if (isForFrayedBorder)
		{
			if (edge.d0.isBorder != edge.d1.isBorder)
			{
				return EdgeDrawType.FrayedBorder;
			}
		}
		else
		{
			if (edge.d0.isWater != edge.d1.isWater)
			{
				return EdgeDrawType.Coast;
			}
			if (edge.isRiver() && !edge.isOceanOrLakeOrShore())
			{
				return EdgeDrawType.River;
			}
			if (((edge.d0.region == null) != (edge.d1.region == null)) || edge.d0.region != null && edge.d0.region.id != edge.d1.region.id)
			{
				return EdgeDrawType.Region;
			}
		}

		return EdgeDrawType.None;
	}

	private boolean shouldDrawEdge(Edge edge)
	{
		return getEdgeDrawType(edge) != EdgeDrawType.None;
	}

	public List<Point> getNoisyEdge(int edgeIndex)
	{
		if (lineStyle.equals(LineStyle.Splines) || lineStyle.equals(LineStyle.SplinesWithSmoothedCoastlines))
		{
			return curves.get(edgeIndex);
		}
		else
		{
			return paths.get(edgeIndex);
		}
	}

	public LineStyle getLineStyle()
	{
		return lineStyle;
	}
}
