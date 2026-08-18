// Annotate each edge with a noisy path, to make maps look more interesting.
// Author: amitp@cs.stanford.edu
// License: MIT

package nortantis.graph.voronoi;

import nortantis.CurveCreator;
import nortantis.MapSettings.LineStyle;
import nortantis.geom.Point;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NoisyEdges
{
	// low: jagged v-edge; high: jagged d-edge
	final double NOISY_LINE_TRADEOFF = 0.5;

	private LineStyle lineStyle;
	// These maps are rebuilt incrementally by the background draw thread (via rebuildNoisyEdgesForCenter
	// during applyCenterEdits) while the EDT reads them concurrently to draw highlight/selection outlines
	// (MapEditingPanel.drawCenterOutlines -> WorldGraph.drawEdge -> getNoisyEdge). They must be concurrent
	// maps: a get racing a put on a plain TreeMap can traverse a mid-rotation tree and return garbage
	// points or null, which showed up as briefly-mangled region boundaries during region merges. With a
	// ConcurrentHashMap a racing get returns either the complete old curve or the complete new one.
	private Map<Integer, List<Point>> paths; // edge index -> List of points in
												// that edge.

	// Maps edge index to a list of points that draw the same position in path0
	// but with curves
	private Map<Integer, List<Point>> curves;

	// Per-edge geometry overrides used where a river runs along a Voronoi edge: the edge's drawn geometry is replaced with the river's own
	// control-point curve so everything that traces the edge (the region-color fill, region boundary line, and the editor's polygon
	// highlights) conforms to the river instead of the river conforming to the polygons. Coastline edges are excluded. Stamped by
	// RiverDrawer#stampRiverCurvesOntoGraphEdges. Read through getNoisyEdge, so every consumer picks it up automatically. Replaced wholesale
	// via an atomic reference swap (volatile) so a concurrent reader on the EDT always sees a complete, self-consistent map rather than a
	// half-rebuilt one.
	private volatile Map<Integer, List<Point>> riverEdgeOverrides = Collections.emptyMap();

	// Mean polygon width of the graph (in display pixels at the graph's resolution). Used to scale the subdivision stopping threshold so that
	// jaggedness is consistent across world sizes. Larger world sizes have smaller polygons, so the threshold must be proportionally smaller
	// to subdivide them to the same relative depth.
	private double meanPolygonWidth;
	private int worldSize;
	private boolean isForFrayedBorder;
	// The graph's resolution scale, used only to choose how densely to sample spline curves (see CurveCreator#getDistanceBetweenPointsForResolution)
	// so curves keep enough points to look smooth when drawn at low display resolutions. It does not affect jagged edges.
	private double resolutionScale;
	// A uniform factor applied to every corner/center coordinate read while generating noisy edges. Normally 1.0 (use the graph's own
	// coordinates). The icon water-check builds a second NoisyEdges at a fixed canonical resolution from the same graph by setting this to
	// (canonicalResolution / graphResolution), so the generated noisy coastline shape depends only on the canonical resolution and is
	// therefore stable when the display resolution changes. See WorldGraph.findClosestCenter(Point, boolean, boolean) with useWaterCheckResolution=true.
	private double coordinateScale;

	public NoisyEdges(double meanPolygonWidth, int worldSize, LineStyle style, boolean isForFrayedBorder, double resolutionScale)
	{
		this(meanPolygonWidth, worldSize, style, isForFrayedBorder, resolutionScale, 1.0);
	}

	public NoisyEdges(double meanPolygonWidth, int worldSize, LineStyle style, boolean isForFrayedBorder, double resolutionScale, double coordinateScale)
	{
		this.meanPolygonWidth = meanPolygonWidth;
		this.worldSize = worldSize;
		paths = new ConcurrentHashMap<>();
		curves = new ConcurrentHashMap<>();
		lineStyle = style;
		this.isForFrayedBorder = isForFrayedBorder;
		this.resolutionScale = resolutionScale;
		this.coordinateScale = coordinateScale;
	}

	private Point scaled(Point p)
	{
		return coordinateScale == 1.0 ? p : p.mult(coordinateScale);
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
				Point v0 = scaled(edge.v0.loc);
				Point v1 = scaled(edge.v1.loc);
				Point d0 = scaled(edge.d0.loc);
				Point d1 = scaled(edge.d1.loc);
				Point midpoint = scaled(edge.midpoint);
				Point t = Point.interpolate(v0, d0, f);
				Point q = Point.interpolate(v0, d1, f);
				Point r = Point.interpolate(v1, d0, f);
				Point s = Point.interpolate(v1, d1, f);

				double minLength = getNoisyEdgeMinLength(edge);

				// List of points in that edge from corner v0 to the midpoint of the edge
				List<Point> path0 = buildNoisyLineSegments(rand, v0, t, midpoint, q, minLength);
				path0.add(midpoint);
				// List of points in that edge from corner v1 to the midpoint of the edge
				List<Point> path1 = buildNoisyLineSegments(rand, v1, s, midpoint, r, minLength);
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
		if (A.subtract(C).length() < minLength * meanPolygonWidth * coordinateScale || B.subtract(D).length() < minLength * meanPolygonWidth * coordinateScale)
		{
			return;
		}
		// Subdivide the quadrilateral
		// vertical (along A-D and B-C)
		double p = nextDoubleRange(random, 0.2, 0.8);
		// horizontal (along A-B and D-C)
		double q = nextDoubleRange(random, 0.2, 0.8);

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
			if (edge.d0 != null && edge.d1 != null && edge.v0 != null && edge.v1 != null && (forceRebuild || curves.get(edge.index) == null))
			{
				if (!shouldDrawEdge(edge))
				{
					curves.put(edge.index, Arrays.asList(scaled(edge.v0.loc), scaled(edge.v1.loc)));
					continue;
				}

				Point p0 = scaled(findPrevOrNextPointOnCurve(edge, edge.v0));
				Point p1 = scaled(edge.v0.loc);
				Point p2 = scaled(edge.v1.loc);
				Point p3 = scaled(findPrevOrNextPointOnCurve(edge, edge.v1));

				List<Point> curve = new LinkedList<>();
				curve.addAll(CurveCreator.createCurve(p0, p1, p2, p3, CurveCreator.getDistanceBetweenPointsForResolution(resolutionScale)));
				if (curve.isEmpty() || !curve.get(0).equals(p1))
				{
					curve.add(0, p1);
				}
				if (!curve.get(curve.size() - 1).equals(p2))
				{
					curve.add(p2);
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
	 * @param prev
	 *            Previous edge in the curve (used to avoid taking a path that immediately turns back on itself), or null
	 * @return Edge to follow, or null if there is none.
	 */
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
			Optional<Edge> optional = corner.protrudes.stream().filter((other) -> other != edge && other != prev && !other.sharesCornerWith(prev) && getEdgeDrawType(other) == EdgeDrawType.River)
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
	 * Returns the stopping threshold for noisy edge subdivision as a fraction of {@link #meanPolygonWidth}. Calibrated so that at the
	 * reference world size (~7000 cells on a 4096×4096 map at resolution 0.75) the resulting pixel threshold matches the pre-world-size-fix
	 * values, preserving the appearance of maps that already looked good at that size while giving consistent jaggedness at all world sizes.
	 */
	private double getNoisyEdgeMinLength(Edge edge)
	{
		// 1.0 at 2000 polygons (min world size), 1.5 at 32000 (max). Larger = less jagged.
		double jaggedEdgeThresholdScale = 1.0 + 0.6 * Math.min(1.0, Math.max(0.0, (worldSize - 2000.0) / 30000.0));

		EdgeDrawType type = getEdgeDrawType(edge);
		if (type.equals(EdgeDrawType.Region))
		{
			return 0.15 * jaggedEdgeThresholdScale;
		}
		if (type.equals(EdgeDrawType.Coast))
		{
			return 0.15 * jaggedEdgeThresholdScale;
		}
		if (type.equals(EdgeDrawType.River))
		{
			return 0.10 * jaggedEdgeThresholdScale;
		}
		if (type.equals(EdgeDrawType.FrayedBorder))
		{
			return 0.15 * jaggedEdgeThresholdScale;
		}

		return 1000.0; // A number big enough to not create noisy edges
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
		List<Point> override = riverEdgeOverrides.get(edgeIndex);
		if (override != null)
		{
			return override;
		}
		if (lineStyle.equals(LineStyle.Splines) || lineStyle.equals(LineStyle.SplinesWithSmoothedCoastlines))
		{
			return curves.get(edgeIndex);
		}
		else
		{
			return paths.get(edgeIndex);
		}
	}

	/**
	 * Replaces the set of river-conformed edge geometries (edge index &rarr; curve in graph-pixel coordinates). Pass an empty map to clear
	 * all overrides. The reference is swapped atomically so concurrent readers never see a partially built map.
	 */
	public void setRiverEdgeOverrides(Map<Integer, List<Point>> overrides)
	{
		riverEdgeOverrides = overrides == null ? Collections.emptyMap() : overrides;
	}

	/**
	 * The current river-conformed edge geometries, as set by {@link #setRiverEdgeOverrides}. Callers must not modify the result.
	 */
	public Map<Integer, List<Point>> getRiverEdgeOverrides()
	{
		return riverEdgeOverrides;
	}

	public LineStyle getLineStyle()
	{
		return lineStyle;
	}
}
