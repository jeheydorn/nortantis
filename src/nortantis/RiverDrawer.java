package nortantis;

import nortantis.editor.River;
import nortantis.editor.RiverPathNode;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.graph.voronoi.Edge;
import nortantis.platform.Color;
import nortantis.platform.DrawQuality;
import nortantis.platform.Image;
import nortantis.platform.Painter;
import nortantis.util.OrderlessPair;
import nortantis.util.Range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

public class RiverDrawer
{
	/**
	 * Per-segment metadata strategy used by {@link PathOperations} when reversing or stitching river paths. Carries width, seed, and the
	 * optional Voronoi edge index along with the segment.
	 */
	public static final PathOperations.NodeMetadataOps<RiverPathNode> RIVER_OPS = new PathOperations.NodeMetadataOps<>()
	{
		@Override
		public RiverPathNode withClearedMetadata(RiverPathNode original)
		{
			return new RiverPathNode(original.getLoc(), 0, 0L, RiverPathNode.EDGE_INDEX_NONE);
		}

		@Override
		public RiverPathNode withMetadataFrom(RiverPathNode target, RiverPathNode donor)
		{
			return new RiverPathNode(target.getLoc(), donor.getWidthLevelToNext(), donor.getSeedToNext(), donor.getEdgeIndexToNext());
		}

		@Override
		public RiverPathNode withStitchedToNextMetadata(RiverPathNode original)
		{
			return new RiverPathNode(original.getLoc(), original.getWidthLevelToNext(), original.getSeedToNext(), RiverPathNode.EDGE_INDEX_NONE);
		}
	};

	private final List<River> rivers;
	private final double resolutionScale;
	private final MapSettings.LineStyle lineStyle;
	private final Color riverColor;
	private final WorldGraph graph;

	public RiverDrawer(MapSettings settings, WorldGraph graph)
	{
		this.graph = graph;
		this.resolutionScale = settings.resolution;
		this.lineStyle = settings.lineStyle;
		this.riverColor = settings.riverColor;
		this.rivers = settings.edits != null && settings.edits.rivers != null ? settings.edits.rivers : Collections.emptyList();
	}

	public RiverDrawer(List<River> rivers, double resolutionScale, MapSettings.LineStyle lineStyle, WorldGraph graph)
	{
		this.graph = graph;
		this.resolutionScale = resolutionScale;
		this.lineStyle = lineStyle;
		this.rivers = rivers != null ? rivers : Collections.emptyList();
		this.riverColor = Color.black; // not used; callers of this constructor pass a color override to drawRivers(Painter, Color)
	}

	public void drawRivers(Image map, Rectangle drawBounds)
	{
		if (rivers.isEmpty())
		{
			return;
		}

		double jaggedAmplitudeRI = getJaggedAmplitudeRI(graph, resolutionScale);
		double minLengthRI = 2.0 / resolutionScale;
		Rectangle drawBoundsRI = drawBounds == null ? null
				: new Rectangle(drawBounds.x / resolutionScale, drawBounds.y / resolutionScale, drawBounds.width / resolutionScale, drawBounds.height / resolutionScale);

		try (Painter p = map.createPainter(DrawQuality.High))
		{
			if (drawBounds != null)
			{
				p.translate(-drawBounds.x, -drawBounds.y);
			}
			drawRiversWithPainter(p, drawBoundsRI, jaggedAmplitudeRI, minLengthRI, riverColor);
		}
	}

	/**
	 * Draws rivers onto an existing painter using the given color override. No translation or clipping is applied; the caller is
	 * responsible for the painter's transform. Pass {@code null} for {@code colorOverride} to use the settings river color.
	 */
	public void drawRivers(Painter p, Color colorOverride)
	{
		if (rivers.isEmpty())
		{
			return;
		}

		double jaggedAmplitudeRI = getJaggedAmplitudeRI(graph, resolutionScale);
		double minLengthRI = 2.0 / resolutionScale;
		drawRiversWithPainter(p, null, jaggedAmplitudeRI, minLengthRI, colorOverride != null ? colorOverride : riverColor);
	}

	/**
	 * Maximum perpendicular displacement (in resolution-invariant pixels) the jagged line style can introduce when subdividing a river
	 * segment. Other code that needs to know how far the visible river can stray from the segment centerline — e.g. edit-mode right-click
	 * hit-testing — should derive from this so the slack matches the drawing exactly.
	 */
	public static double getJaggedAmplitudeRI(WorldGraph graph, double resolutionScale)
	{
		return graph.getMeanCenterWidth() / 2.0 / resolutionScale;
	}

	private void drawRiversWithPainter(Painter p, Rectangle drawBoundsRI, double jaggedAmplitudeRI, double minLengthRI, Color color)
	{
		p.setColor(color);
		for (River river : rivers)
		{
			List<RiverPathNode> nodes = river.nodes;
			if (nodes.size() < 2)
			{
				continue;
			}
			if (drawBoundsRI != null && !PathOperations.pathOverlapsRectangle(nodes, drawBoundsRI, jaggedAmplitudeRI))
			{
				continue;
			}
			drawRiver(p, nodes, jaggedAmplitudeRI, minLengthRI);
		}
	}

	private void drawRiver(Painter p, List<RiverPathNode> nodes, double jaggedAmplitudeRI, double minLengthRI)
	{
		int numSegments = nodes.size() - 1;
		for (int i = 0; i < numSegments; i++)
		{
			RiverPathNode current = nodes.get(i);
			float currentWidth = calcRiverStrokeWidth(current.getWidthLevelToNext());
			float fromWidth = i == 0 ? findJunctionWidth(nodes, nodes.get(0).getLoc(), currentWidth) : calcRiverStrokeWidth(nodes.get(i - 1).getWidthLevelToNext());
			float toWidth = i == numSegments - 1 ? findJunctionWidth(nodes, nodes.get(numSegments).getLoc(), currentWidth) : calcRiverStrokeWidth(nodes.get(i + 1).getWidthLevelToNext());

			List<Point> segmentPathPixels = buildSegmentPathPixels(nodes, i, jaggedAmplitudeRI, minLengthRI);
			drawPathWithSmoothLineTransitions(p, segmentPathPixels, fromWidth, currentWidth, toWidth);
		}
	}

	private List<Point> buildSegmentPathPixels(List<RiverPathNode> nodes, int segmentIndex, double jaggedAmplitudeRI, double minLengthRI)
	{
		RiverPathNode startNode = nodes.get(segmentIndex);
		Point riStart = startNode.getLoc();
		Point riEnd = nodes.get(segmentIndex + 1).getLoc();

		// A river's shape is defined entirely by its control points, the same way for freehand and polygon-placed
		// rivers. Where a river runs along a region boundary, the polygons conform to the river (their edge geometry
		// is overridden with the river's curve; see {@link #stampRiverCurvesOntoGraphEdges}) rather than the
		// river conforming to the polygons, so the river never kinks to follow a boundary.
		List<Point> pathRI;
		if (lineStyle == MapSettings.LineStyle.Jagged && jaggedAmplitudeRI > 0)
		{
			// Per-segment seed lives on the node, so adding/removing a control point doesn't shift
			// the randomness of unrelated segments.
			Random random = new Random(startNode.getSeedToNext());
			pathRI = new ArrayList<>();
			pathRI.add(riStart);
			subdivideInterior(riStart, riEnd, jaggedAmplitudeRI, minLengthRI, random, pathRI);
			pathRI.add(riEnd);
		}
		else if (lineStyle != MapSettings.LineStyle.Jagged)
		{
			// Smooth curve segment using centripetal Catmull-Rom with neighbor control points.
			int last = nodes.size() - 1;
			Point p0 = segmentIndex == 0 ? riStart.add(riStart.subtract(riEnd)) : nodes.get(segmentIndex - 1).getLoc();
			Point p3 = segmentIndex == last - 1 ? riEnd.add(riEnd.subtract(riStart)) : nodes.get(segmentIndex + 2).getLoc();
			// getDistanceBetweenPointsForResolution returns a spacing in graph-pixel space; dividing by resolutionScale converts it to the
			// RI space this curve is built in (the path is scaled to pixels afterward). The resolution-based spacing adds points at low
			// display resolutions so smooth rivers don't collapse toward sharp polygon lines.
			pathRI = CurveCreator.createCurve(p0, riStart, riEnd, p3, CurveCreator.getDistanceBetweenPointsForResolution(resolutionScale) / resolutionScale);
			// createCurve runs t < t2 so the endpoint may be missing; ensure it is present.
			if (pathRI.isEmpty() || !pathRI.get(pathRI.size() - 1).isCloseEnough(riEnd))
			{
				pathRI.add(riEnd);
			}
		}
		else
		{
			// Jagged with zero amplitude: straight segment.
			pathRI = List.of(riStart, riEnd);
		}

		return scaleToPixels(pathRI);
	}

	/**
	 * Where a river runs along a Voronoi edge, replaces that edge's drawn geometry with the river segment's own control-point curve, so
	 * everything that reads the edge geometry conforms exactly to the river rather than the river conforming to the edge. This makes the
	 * region-color fill and region boundary line coincide with the river where it runs along a region boundary, and makes the editor's
	 * polygon highlights (which trace the same edge geometry) coincide with the river on any river edge. Coastline and lakeshore edges are
	 * excluded, so the coast keeps its generated shape. The full override set is rebuilt from the current rivers each call, so an edge a
	 * river no longer covers reverts to its generated geometry automatically.
	 *
	 * <p>
	 * Overriding an interior river edge (one shared by two centers of the same region) does not change the region fill: both centers trace
	 * the identical curve for the shared edge, so their filled union is unchanged; only the visible region boundary, coastline, and highlight
	 * outlines are affected.
	 *
	 * <p>
	 * Must run after the graph's noisy edges are built and the rivers are (re)synced to the graph, and before any polygon fill or boundary
	 * drawing reads the geometry via {@link nortantis.graph.voronoi.NoisyEdges#getNoisyEdge(int)}.
	 */
	public void stampRiverCurvesOntoGraphEdges()
	{
		if (graph == null || graph.noisyEdges == null)
		{
			return;
		}
		Map<Integer, List<Point>> overrides = new HashMap<>();
		double jaggedAmplitudeRI = getJaggedAmplitudeRI(graph, resolutionScale);
		double minLengthRI = 2.0 / resolutionScale;
		for (River river : rivers)
		{
			List<RiverPathNode> nodes = river.nodes;
			if (nodes.size() < 2)
			{
				continue;
			}
			for (int i = 0; i < nodes.size() - 1; i++)
			{
				int edgeIndex = nodes.get(i).getEdgeIndexToNext();
				if (edgeIndex == RiverPathNode.EDGE_INDEX_NONE || edgeIndex < 0 || edgeIndex >= graph.edges.size())
				{
					continue;
				}
				Edge edge = graph.edges.get(edgeIndex);
				if (edge.v0 == null || edge.v1 == null || edge.isCoastOrLakeShore())
				{
					continue;
				}
				List<Point> curvePixels = buildSegmentPathPixels(nodes, i, jaggedAmplitudeRI, minLengthRI);
				if (curvePixels.size() < 2)
				{
					continue;
				}
				overrides.put(edgeIndex, orientCurveToEdgeCorners(curvePixels, edge));
			}
		}
		graph.noisyEdges.setRiverEdgeOverrides(overrides);
	}

	/**
	 * Orients {@code curvePixels} (a river segment's curve in graph-pixel coordinates) so it runs from {@code edge.v0} to {@code edge.v1},
	 * and snaps its endpoints exactly onto the corner locations so the polygon that concatenates this edge with its neighbors stays closed.
	 */
	private static List<Point> orientCurveToEdgeCorners(List<Point> curvePixels, Edge edge)
	{
		List<Point> result = new ArrayList<>(curvePixels);
		Point first = result.get(0);
		boolean startsAtV0 = first.distanceTo(edge.v0.loc) <= first.distanceTo(edge.v1.loc);
		if (!startsAtV0)
		{
			Collections.reverse(result);
		}
		result.set(0, edge.v0.loc);
		result.set(result.size() - 1, edge.v1.loc);
		return result;
	}

	private List<Point> scaleToPixels(List<Point> pathRI)
	{
		List<Point> pixels = new ArrayList<>(pathRI.size());
		for (Point pt : pathRI)
		{
			pixels.add(new Point(pt.x * resolutionScale, pt.y * resolutionScale));
		}
		return pixels;
	}

	private float calcRiverStrokeWidth(int widthLevel)
	{
		return (float) (resolutionScale * Math.sqrt(widthLevel * 0.5));
	}

	/**
	 * At a junction where this river meets other rivers at {@code endpoint}, returns the stroke width this river should transition to at
	 * that endpoint. At multi-way junctions (more than two river segments), only the two rivers with the highest endpoint width levels
	 * participate in the smooth transition; all others return {@code defaultWidth}. Ties are broken by list insertion order.
	 */
	private float findJunctionWidth(List<RiverPathNode> currentNodes, Point endpoint, float defaultWidth)
	{
		int currentEndWidthLevel = getEndpointWidthLevel(currentNodes, endpoint);

		// Build junction entries using the list index as an ID. We use reference equality (==) so
		// that two rivers with identical content are still treated as distinct.
		int currentIndex = -1;
		List<int[]> junctionEntries = new ArrayList<>();
		for (int i = 0; i < rivers.size(); i++)
		{
			List<RiverPathNode> otherNodes = rivers.get(i).nodes;
			if (otherNodes == currentNodes)
			{
				currentIndex = i;
				junctionEntries.add(new int[] { currentEndWidthLevel, i });
			}
			else if (otherNodes.size() >= 2)
			{
				if (otherNodes.get(0).getLoc().isCloseEnough(endpoint))
				{
					junctionEntries.add(new int[] { otherNodes.get(0).getWidthLevelToNext(), i });
				}
				else if (otherNodes.get(otherNodes.size() - 1).getLoc().isCloseEnough(endpoint))
				{
					junctionEntries.add(new int[] { otherNodes.get(otherNodes.size() - 2).getWidthLevelToNext(), i });
				}
			}
		}

		if (junctionEntries.size() <= 1)
		{
			return defaultWidth;
		}

		junctionEntries.sort((a, b) -> a[0] != b[0] ? b[0] - a[0] : Integer.compare(a[1], b[1]));

		boolean currentIsFirst = junctionEntries.get(0)[1] == currentIndex;
		boolean currentIsSecond = junctionEntries.get(1)[1] == currentIndex;
		if (!currentIsFirst && !currentIsSecond)
		{
			return defaultWidth;
		}

		int partnerWidthLevel = currentIsFirst ? junctionEntries.get(1)[0] : junctionEntries.get(0)[0];
		return calcRiverStrokeWidth(partnerWidthLevel);
	}

	private int getEndpointWidthLevel(List<RiverPathNode> nodes, Point endpoint)
	{
		if (nodes.size() < 2)
		{
			return 0;
		}
		if (nodes.get(0).getLoc().isCloseEnough(endpoint))
		{
			return nodes.get(0).getWidthLevelToNext();
		}
		return nodes.get(nodes.size() - 2).getWidthLevelToNext();
	}

	/**
	 * Draws a path with stroke width that interpolates smoothly from {@code fromWidth} at the start to {@code currentWidth} at the midpoint
	 * to {@code toWidth} at the end. Mirrors {@code VoronoiGraph.drawPathWithSmoothLineTransitions} exactly.
	 *
	 * @param path
	 *            Points in pixel coordinates.
	 */
	private void drawPathWithSmoothLineTransitions(Painter p, List<Point> path, float fromWidth, float currentWidth, float toWidth)
	{
		if (path == null || path.size() < 2)
		{
			return;
		}

		float widthAtStart = (fromWidth + currentWidth) / 2f;
		float widthAtEnd = (toWidth + currentWidth) / 2f;
		float previousWidth = widthAtStart;
		List<Point> pathSoFar = new ArrayList<>();
		pathSoFar.add(path.get(0));

		float pathLength = getPathLength(path);
		float lengthSoFar = 0;

		for (int i = 1; i < path.size(); i++)
		{
			float width;
			float distanceRatio = lengthSoFar / pathLength;
			if (distanceRatio < 0.5f)
			{
				float ratio = distanceRatio * 2f;
				width = (1f - ratio) * widthAtStart + ratio * currentWidth;
			}
			else
			{
				float ratio = distanceRatio - 0.5f;
				width = (1f - ratio) * currentWidth + ratio * widthAtEnd;
			}

			pathSoFar.add(path.get(i));
			if (width != previousWidth)
			{
				p.setBasicStroke(width);
				drawPolyline(p, pathSoFar);
				pathSoFar = new ArrayList<>();
				pathSoFar.add(path.get(i));
				previousWidth = width;
			}

			lengthSoFar += (float) path.get(i - 1).distanceTo(path.get(i));
		}

		if (pathSoFar.size() > 1)
		{
			p.setBasicStroke(widthAtEnd);
			drawPolyline(p, pathSoFar);
		}
	}

	private float getPathLength(List<Point> path)
	{
		if (path.size() < 2)
		{
			return 0;
		}
		float length = 0;
		for (int i = 1; i < path.size(); i++)
		{
			length += (float) path.get(i - 1).distanceTo(path.get(i));
		}
		return length;
	}

	private void drawPolyline(Painter p, List<Point> line)
	{
		int[] xPoints = new int[line.size()];
		int[] yPoints = new int[line.size()];
		for (int i : new Range(line.size()))
		{
			xPoints[i] = (int) line.get(i).x;
			yPoints[i] = (int) line.get(i).y;
		}
		p.drawPolyline(xPoints, yPoints);
	}

	/**
	 * Recursively adds displaced midpoints between a and b to result. Neither a nor b are added by this method; the caller manages them.
	 */
	private void subdivideInterior(Point a, Point b, double W_level, double minLength, Random random, List<Point> result)
	{
		double segLength = a.distanceTo(b);
		if (segLength < minLength)
		{
			return;
		}

		double maxDisp = Math.min(W_level, segLength / 4.0);
		if (maxDisp <= 0)
		{
			return;
		}

		Point mid = new Point((a.x + b.x) / 2.0, (a.y + b.y) / 2.0);

		double dx = b.x - a.x;
		double dy = b.y - a.y;
		double len = Math.sqrt(dx * dx + dy * dy);
		double perpX = -dy / len;
		double perpY = dx / len;

		double displacement = (random.nextDouble() * 2 - 1) * maxDisp;
		Point displaced = new Point(mid.x + perpX * displacement, mid.y + perpY * displacement);

		double subW = W_level * 0.5;
		subdivideInterior(a, displaced, subW, minLength, random, result);
		result.add(displaced);
		subdivideInterior(displaced, b, subW, minLength, random, result);
	}

	/**
	 * Builds non-overlapping {@link River} objects from an unordered set of connected Voronoi edges, adds them to {@code rivers}, and
	 * returns the changed rivers. The result contains both newly added/connected rivers and any existing rivers whose segment widths were
	 * updated because the user drew over them at a different width. Edges whose Voronoi edge index already appears in {@code rivers} are
	 * skipped from the new path (splitting the result into multiple rivers if necessary), but their existing segment's
	 * {@code widthLevelToNext} is updated to {@code riverLevel} so redrawing a wider/thinner river over an existing one takes effect.
	 * Analogous to {@link RoadDrawer#addRoadsFromEdgesInEditor}.
	 */
	public static List<River> addRiversFromEdgesInEditor(Set<Edge> edgeSet, Corner start, int riverLevel, double resolutionScale, List<River> rivers)
	{
		List<River> widthUpdated = updateExistingRiverWidthsForEdges(rivers, edgeSet, riverLevel);
		List<River> newRivers = buildRiversFromEdgeSet(edgeSet, start, riverLevel, resolutionScale, rivers);
		List<River> connected = connectAndAddRivers(newRivers, rivers);
		// Combine the width-updated existing rivers with the newly added/joined rivers, keeping
		// each river at most once.
		Set<River> seen = new HashSet<>();
		List<River> changed = new ArrayList<>(widthUpdated.size() + connected.size());
		for (River r : widthUpdated)
		{
			if (seen.add(r))
			{
				changed.add(r);
			}
		}
		for (River r : connected)
		{
			if (seen.add(r))
			{
				changed.add(r);
			}
		}
		return changed;
	}

	/**
	 * For each segment in {@code rivers} whose {@code edgeIndexToNext} is in {@code edges}, updates its width level to
	 * {@code newWidthLevel} if different. Returns the rivers whose width was actually changed. Used so that drawing a polygon-mode river
	 * over an existing river at a different width retunes the existing segments instead of leaving them at the old width.
	 */
	public static List<River> updateExistingRiverWidthsForEdges(List<River> rivers, Set<Edge> edges, int newWidthLevel)
	{
		if (rivers == null || rivers.isEmpty() || edges == null || edges.isEmpty())
		{
			return Collections.emptyList();
		}
		Set<Integer> edgeIndices = new HashSet<>(edges.size() * 2);
		for (Edge e : edges)
		{
			edgeIndices.add(e.index);
		}

		List<River> changed = new ArrayList<>();
		for (River river : rivers)
		{
			List<RiverPathNode> nodes = river.nodes;
			List<RiverPathNode> updated = null;
			for (int i = 0; i < nodes.size() - 1; i++)
			{
				RiverPathNode node = nodes.get(i);
				int edgeIndex = node.getEdgeIndexToNext();
				if (edgeIndex == RiverPathNode.EDGE_INDEX_NONE)
				{
					continue;
				}
				if (!edgeIndices.contains(edgeIndex))
				{
					continue;
				}
				if (node.getWidthLevelToNext() == newWidthLevel)
				{
					continue;
				}
				if (updated == null)
				{
					updated = new ArrayList<>(nodes);
				}
				updated.set(i, new RiverPathNode(node.getLoc(), newWidthLevel, node.getSeedToNext(), edgeIndex));
			}
			if (updated != null)
			{
				// Atomic swap so concurrent readers see a fully consistent path.
				river.nodes = new CopyOnWriteArrayList<>(updated);
				changed.add(river);
			}
		}
		return changed;
	}

	/**
	 * Clips {@code pathRI} at ocean and lake centers (dropping water sub-paths so the river ends at the coastline), splits the remaining
	 * land sub-paths at any segments that already exist in {@code rivers}, adds the resulting sub-rivers to {@code rivers}, and returns the
	 * changed rivers. A river that passes through one or more bodies of water becomes multiple sub-rivers. If the new freehand path exactly
	 * retraces a segment of an existing river at a different width (possible via the snap-to-control-point feature), the existing segment's
	 * {@code widthLevelToNext} is updated to {@code riverLevel} so re-drawing at a different width takes effect. Analogous to
	 * {@link RoadDrawer#addFreeHandRoadFromPoints}.
	 */
	public static List<River> addFreeHandRiverFromPoints(List<Point> pathRI, int riverLevel, List<River> rivers, nortantis.WorldGraph graph, double resolutionScale)
	{
		List<List<Point>> landPaths = clipPathAtWater(pathRI, graph, resolutionScale);
		if (landPaths.isEmpty())
		{
			return Collections.emptyList();
		}

		// Collect segments of the new path, then update widths on any existing river segments that
		// exactly match (e.g. when the user snapped to existing control points).
		Set<OrderlessPair<Point>> newSegments = new HashSet<>();
		for (List<Point> landPath : landPaths)
		{
			for (int i = 0; i < landPath.size() - 1; i++)
			{
				newSegments.add(new OrderlessPair<>(landPath.get(i), landPath.get(i + 1)));
			}
		}
		List<River> widthUpdated = updateExistingRiverWidthsForPointPairs(rivers, newSegments, riverLevel);

		Set<OrderlessPair<Point>> existingConnections = PathOperations.collectAllConnections(riverNodesList(rivers));
		Random random = new Random();
		List<River> newRivers = new ArrayList<>();
		for (List<Point> landPath : landPaths)
		{
			for (List<Point> subPath : nortantis.util.GeometryHelper.splitPathAtOverlaps(landPath, existingConnections))
			{
				newRivers.add(River.withUniformWidth(subPath, riverLevel, random));
			}
		}
		List<River> connected = connectAndAddRivers(newRivers, rivers);

		Set<River> seen = new HashSet<>();
		List<River> changed = new ArrayList<>(widthUpdated.size() + connected.size());
		for (River r : widthUpdated)
		{
			if (seen.add(r))
			{
				changed.add(r);
			}
		}
		for (River r : connected)
		{
			if (seen.add(r))
			{
				changed.add(r);
			}
		}
		return changed;
	}

	/**
	 * For each segment in {@code rivers} whose endpoint location pair appears in {@code segmentsToMatch}, updates its width level to
	 * {@code newWidthLevel} if different. Returns the rivers whose width was actually changed. Used so that a freehand river drawn exactly
	 * over an existing river's segments (via snap-to-control-point) retunes the existing widths. Polygon-mode width updates go through
	 * {@link #updateExistingRiverWidthsForEdges} instead, which uses the deterministic edge-index match.
	 */
	public static List<River> updateExistingRiverWidthsForPointPairs(List<River> rivers, Set<OrderlessPair<Point>> segmentsToMatch, int newWidthLevel)
	{
		if (rivers == null || rivers.isEmpty() || segmentsToMatch == null || segmentsToMatch.isEmpty())
		{
			return Collections.emptyList();
		}
		List<River> changed = new ArrayList<>();
		for (River river : rivers)
		{
			List<RiverPathNode> nodes = river.nodes;
			List<RiverPathNode> updated = null;
			for (int i = 0; i < nodes.size() - 1; i++)
			{
				OrderlessPair<Point> pair = new OrderlessPair<>(nodes.get(i).getLoc(), nodes.get(i + 1).getLoc());
				if (!segmentsToMatch.contains(pair))
				{
					continue;
				}
				if (nodes.get(i).getWidthLevelToNext() == newWidthLevel)
				{
					continue;
				}
				if (updated == null)
				{
					updated = new ArrayList<>(nodes);
				}
				RiverPathNode old = updated.get(i);
				updated.set(i, new RiverPathNode(old.getLoc(), newWidthLevel, old.getSeedToNext(), old.getEdgeIndexToNext()));
			}
			if (updated != null)
			{
				river.nodes = new CopyOnWriteArrayList<>(updated);
				changed.add(river);
			}
		}
		return changed;
	}

	/**
	 * Splits {@code pathRI} at segments with both endpoints fall on ocean or lake centers, dropping those fully-submerged segments.
	 * Segments with at least one endpoint on land are kept as-is, so a river that crosses water briefly without a user-placed point on
	 * water is preserved. Each returned sub-path has at least 2 points.
	 */
	private static List<List<Point>> clipPathAtWater(List<Point> pathRI, nortantis.WorldGraph graph, double resolutionScale)
	{
		if (pathRI.size() < 2)
		{
			return Collections.emptyList();
		}

		boolean[] isWater = new boolean[pathRI.size()];
		for (int i = 0; i < pathRI.size(); i++)
		{
			isWater[i] = isPointOnWater(pathRI.get(i), graph, resolutionScale, c -> c.isWater);
		}

		List<List<Point>> result = new ArrayList<>();
		List<Point> current = new ArrayList<>();
		for (int i = 0; i < pathRI.size() - 1; i++)
		{
			if (isWater[i] && isWater[i + 1])
			{
				if (current.size() >= 2)
				{
					result.add(current);
				}
				current = new ArrayList<>();
			}
			else
			{
				if (current.isEmpty())
				{
					current.add(pathRI.get(i));
				}
				current.add(pathRI.get(i + 1));
			}
		}
		if (current.size() >= 2)
		{
			result.add(current);
		}
		return result;
	}

	private static boolean isPointOnWater(Point pathPointRI, nortantis.WorldGraph graph, double resolutionScale, Predicate<Center> isWaterCenter)
	{
		Center c = graph.findClosestCenter(pathPointRI.mult(resolutionScale));
		return c != null && isWaterCenter.test(c);
	}

	/**
	 * Returns river segments that should be removed in response to the user painting ocean or lake. Two cases are detected:
	 * <ol>
	 * <li>Segments with both endpoints in water centers (per {@code isWaterCenter}) &mdash; reuses the same water test as the freehand
	 * draw-time clipping in {@link #clipPathAtWater}.</li>
	 * <li>Polygon-style segments whose endpoints exactly match the {@link Corner#loc} of a Voronoi edge that is now a coastline or
	 * lakeshore (one adjacent Center water, one land). Rivers do not follow coastlines in real life, so these are removed.</li>
	 * </ol>
	 * A per-river bounding-box test against the combined bounds of {@code paintedCenters} skips rivers far from the painted area, so this
	 * can run cheaply on every drag tick. Within rivers that pass the bbox test, every segment is examined &mdash; required to catch
	 * segments whose two endpoints sit in centers that were painted across separate drag events.
	 *
	 * <p>
	 * The returned segments are 2-point lists; callers feed them through {@link #removeSegmentsAndSplitRivers}.
	 *
	 * @param isWaterCenter
	 *            Predicate that returns true for water centers given the user's pending {@code centerEdits} &mdash; callers should consult
	 *            edits rather than the live {@code Center.isWater} field, which is not updated until the next redraw.
	 */
	public static List<List<Point>> findRiverSegmentsToRemoveForWaterPaint(List<River> rivers, nortantis.WorldGraph graph, double resolutionScale, Set<Center> paintedCenters,
			Predicate<Center> isWaterCenter)
	{
		List<List<Point>> result = new ArrayList<>();
		if (rivers == null || rivers.isEmpty() || paintedCenters == null || paintedCenters.isEmpty())
		{
			return result;
		}

		Rectangle paintedBoundsRI = computeCentersBoundsRI(paintedCenters, resolutionScale);
		if (paintedBoundsRI == null)
		{
			return result;
		}

		for (River river : rivers)
		{
			List<RiverPathNode> nodes = river.nodes;
			if (nodes.size() < 2)
			{
				continue;
			}

			Rectangle riverBoundsRI = computeNodeBoundsRI(nodes);
			if (!paintedBoundsRI.overlaps(riverBoundsRI))
			{
				continue;
			}

			for (int i = 0; i < nodes.size() - 1; i++)
			{
				RiverPathNode nodeA = nodes.get(i);
				Point pA = nodeA.getLoc();
				Point pB = nodes.get(i + 1).getLoc();

				if (isPointOnWater(pA, graph, resolutionScale, isWaterCenter) && isPointOnWater(pB, graph, resolutionScale, isWaterCenter))
				{
					result.add(List.of(pA, pB));
					continue;
				}

				if (isSegmentOnCoastEdge(nodeA.getEdgeIndexToNext(), graph, isWaterCenter))
				{
					result.add(List.of(pA, pB));
				}
			}
		}
		return result;
	}

	private static Rectangle computeCentersBoundsRI(Set<Center> centers, double resolutionScale)
	{
		Rectangle bounds = null;
		for (Center c : centers)
		{
			for (Corner corner : c.corners)
			{
				double x = corner.loc.x / resolutionScale;
				double y = corner.loc.y / resolutionScale;
				bounds = bounds == null ? new Rectangle(x, y, 0, 0) : bounds.add(x, y);
			}
		}
		return bounds;
	}

	private static Rectangle computeNodeBoundsRI(List<RiverPathNode> nodes)
	{
		Rectangle bounds = new Rectangle(nodes.get(0).getLoc().x, nodes.get(0).getLoc().y, 0, 0);
		for (int i = 1; i < nodes.size(); i++)
		{
			bounds = bounds.add(nodes.get(i).getLoc());
		}
		return bounds;
	}

	/**
	 * For every river node that carries a Voronoi edge index in {@code edgeIndexToNext}, snaps both endpoints of that segment back to the
	 * current {@link Corner#loc} of the edge's corners (in RI coordinates). This is needed after region-boundary smoothing moves corners:
	 * the river itself is drawn from the noisy-edge path (which uses the new corner positions), but the river's stored control points were
	 * captured at the old positions, so the editor would draw control points off the visible river. Freehand nodes
	 * ({@link RiverPathNode#EDGE_INDEX_NONE}) are left alone — they aren't tied to a specific corner.
	 *
	 * @return the rivers whose node list was actually modified.
	 */
	public static List<River> resyncRiverNodeLocationsToGraph(List<River> rivers, nortantis.WorldGraph graph, double resolutionScale)
	{
		if (rivers == null || rivers.isEmpty() || graph == null)
		{
			return Collections.emptyList();
		}
		List<River> changed = new ArrayList<>();
		for (River river : rivers)
		{
			List<RiverPathNode> nodes = river.nodes;
			if (nodes.size() < 2)
			{
				continue;
			}
			List<RiverPathNode> updated = null;
			for (int i = 0; i < nodes.size() - 1; i++)
			{
				int edgeIndex = nodes.get(i).getEdgeIndexToNext();
				if (edgeIndex == RiverPathNode.EDGE_INDEX_NONE || edgeIndex < 0 || edgeIndex >= graph.edges.size())
				{
					continue;
				}
				Edge edge = graph.edges.get(edgeIndex);
				if (edge.v0 == null || edge.v1 == null)
				{
					continue;
				}
				Point v0RI = edge.v0.loc.mult(1.0 / resolutionScale);
				Point v1RI = edge.v1.loc.mult(1.0 / resolutionScale);

				RiverPathNode startNode = updated != null ? updated.get(i) : nodes.get(i);
				RiverPathNode endNode = updated != null ? updated.get(i + 1) : nodes.get(i + 1);

				// Orient: pick the assignment of {v0RI, v1RI} → {start, end} that's closer to where
				// the nodes already sit, so we keep the river's direction intact across re-syncs.
				double forward = startNode.getLoc().distanceTo(v0RI) + endNode.getLoc().distanceTo(v1RI);
				double backward = startNode.getLoc().distanceTo(v1RI) + endNode.getLoc().distanceTo(v0RI);
				Point newStart = forward <= backward ? v0RI : v1RI;
				Point newEnd = forward <= backward ? v1RI : v0RI;

				// Only rewrite a node when it moves by a visible amount. Re-deriving a corner's location as corner.loc / resolutionScale
				// at a different resolution yields the same point apart from floating-point rounding in the last bit; rewriting for that
				// would change the stored node just enough to make the edits compare unequal (a spurious "unsaved changes" prompt) without
				// moving the river. A real coastline shift moves corners by whole pixels, well past isCloseEnough's threshold.
				if (!startNode.getLoc().isCloseEnough(newStart))
				{
					if (updated == null)
					{
						updated = new ArrayList<>(nodes);
					}
					updated.set(i, new RiverPathNode(newStart, startNode.getWidthLevelToNext(), startNode.getSeedToNext(), startNode.getEdgeIndexToNext(), startNode.getCornerIndexAnchor()));
				}
				if (!endNode.getLoc().isCloseEnough(newEnd))
				{
					if (updated == null)
					{
						updated = new ArrayList<>(nodes);
					}
					updated.set(i + 1, new RiverPathNode(newEnd, endNode.getWidthLevelToNext(), endNode.getSeedToNext(), endNode.getEdgeIndexToNext(), endNode.getCornerIndexAnchor()));
				}
			}

			// Second pass: maintain corner anchors on mouth nodes. A freehand mouth that ends exactly on a water-adjacent corner is
			// anchored to that corner so it tracks the coast/lakeshore across smoothing (an unanchored mouth would be stranded on land
			// when the coast shifts, e.g. on a line-style change). This pass is self-healing and runs every draw:
			// - it CREATES an anchor on either river endpoint that sits exactly on a mouth corner (so an anchor that some node rebuild
			// dropped, e.g. a width change using the anchor-less constructor, is restored). Exact-match keeps near-coast freehand
			// endpoints that the user deliberately placed off a corner from being grabbed;
			// - it CLEARS an anchor that is no longer applicable (the node is no longer an endpoint, or its corner is no longer a mouth
			// corner because the user grew land over it), leaving the node where it is;
			// - it SNAPS a still-anchored node onto its corner's current location. This runs after the edge pass so an explicit
			// anchor wins for the rare node that is both edge-snapped and anchored.
			for (int i = 0; i < nodes.size(); i++)
			{
				RiverPathNode node = updated != null ? updated.get(i) : nodes.get(i);
				boolean isEndpoint = i == 0 || i == nodes.size() - 1;
				int cornerIndex = node.getCornerIndexAnchor();

				if (cornerIndex != RiverPathNode.CORNER_INDEX_NONE)
				{
					Corner corner = cornerIndex >= 0 && cornerIndex < graph.corners.size() ? graph.corners.get(cornerIndex) : null;
					if (!isEndpoint || corner == null || corner.loc == null || !isMouthCorner(corner))
					{
						// Anchor no longer applies: drop it but leave the node where it is.
						if (updated == null)
						{
							updated = new ArrayList<>(nodes);
						}
						updated.set(i, new RiverPathNode(node.getLoc(), node.getWidthLevelToNext(), node.getSeedToNext(), node.getEdgeIndexToNext()));
						continue;
					}
					Point cornerRI = corner.loc.mult(1.0 / resolutionScale);
					if (!node.getLoc().isCloseEnough(cornerRI))
					{
						if (updated == null)
						{
							updated = new ArrayList<>(nodes);
						}
						updated.set(i, new RiverPathNode(cornerRI, node.getWidthLevelToNext(), node.getSeedToNext(), node.getEdgeIndexToNext(), cornerIndex));
					}
				}
				else if (isEndpoint)
				{
					Corner corner = graph.findClosestCorner(node.getLoc().mult(resolutionScale));
					if (corner != null && corner.loc != null && isMouthCorner(corner) && node.getLoc().isCloseEnough(corner.loc.mult(1.0 / resolutionScale)))
					{
						if (updated == null)
						{
							updated = new ArrayList<>(nodes);
						}
						updated.set(i, new RiverPathNode(node.getLoc(), node.getWidthLevelToNext(), node.getSeedToNext(), node.getEdgeIndexToNext(), corner.index));
					}
				}
			}
			if (updated != null)
			{
				// Atomic swap so concurrent readers see a fully consistent path.
				river.nodes = new CopyOnWriteArrayList<>(updated);
				changed.add(river);
			}
		}
		return changed;
	}

	/**
	 * Returns true if {@code corner} is a mouth corner: it touches at least one water {@link Center} and at least one land Center. This
	 * covers both ocean coastlines and lakeshores (water includes lakes), so a river ending exactly on such a corner is a mouth that should
	 * be anchored to it (see {@link #resyncRiverNodeLocationsToGraph}).
	 */
	static boolean isMouthCorner(Corner corner)
	{
		if (corner == null || corner.touches == null)
		{
			return false;
		}
		boolean hasWater = false;
		boolean hasLand = false;
		for (Center center : corner.touches)
		{
			if (center.isWater)
			{
				hasWater = true;
			}
			else
			{
				hasLand = true;
			}
			if (hasWater && hasLand)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns true if the segment described by {@code edgeIndex} is a coast/lakeshore (one adjacent {@link Center} water, one land per
	 * {@code isWaterCenter}). Returns false for {@link RiverPathNode#EDGE_INDEX_NONE} or any out-of-range index. Rivers do not follow
	 * coastlines in real life, so the caller drops segments for which this returns true once the user has painted water.
	 */
	private static boolean isSegmentOnCoastEdge(int edgeIndex, nortantis.WorldGraph graph, Predicate<Center> isWaterCenter)
	{
		if (edgeIndex == RiverPathNode.EDGE_INDEX_NONE || edgeIndex < 0 || edgeIndex >= graph.edges.size())
		{
			return false;
		}
		Edge edge = graph.edges.get(edgeIndex);
		return edge.d0 != null && edge.d1 != null && isWaterCenter.test(edge.d0) != isWaterCenter.test(edge.d1);
	}

	/**
	 * Splits each river in {@code rivers} at any node whose location appears as an endpoint of one of {@code segmentsToRemove}. A river
	 * that becomes multiple pieces stays in the list as its first piece, with the additional pieces appended as new {@link River} objects.
	 *
	 * @return The rivers that changed (existing rivers whose nodes were replaced, plus any newly created pieces from splits).
	 */
	public static List<River> removeSegmentsAndSplitRivers(List<River> rivers, List<List<Point>> segmentsToRemove)
	{
		Set<OrderlessPair<Point>> removedSegments = new HashSet<>();
		for (List<Point> seg : segmentsToRemove)
		{
			// Each list is a concatenation of consecutive overlapping segment pairs (e.g. [A,B,B,C,C,D]), so iterate by pairs
			// (i += 2) to remove every segment in the run, not just the first.
			for (int i = 0; i + 1 < seg.size(); i += 2)
			{
				removedSegments.add(new OrderlessPair<>(seg.get(i), seg.get(i + 1)));
			}
		}

		List<River> changed = new ArrayList<>();
		List<River> newRivers = new ArrayList<>();
		for (River river : rivers)
		{
			List<RiverPathNode> nodes = river.nodes;
			List<List<RiverPathNode>> splits = PathOperations.splitAtSegments(nodes, removedSegments);
			boolean unchanged = splits.size() == 1 && splits.get(0).size() == nodes.size();
			if (unchanged)
			{
				continue;
			}

			changed.add(river);
			if (splits.isEmpty())
			{
				river.nodes = new CopyOnWriteArrayList<>();
			}
			else
			{
				// Atomic swap: a single volatile write replaces the entire path so concurrent
				// readers (e.g. the background draw thread) never see a mid-modification state.
				river.nodes = new CopyOnWriteArrayList<>(splits.get(0));
				for (int i = 1; i < splits.size(); i++)
				{
					newRivers.add(new River(splits.get(i)));
				}
			}
		}

		rivers.addAll(newRivers);
		changed.addAll(newRivers);
		return changed;
	}

	/**
	 * For each river in {@code newRivers}, attempts to join it to an existing river in {@code rivers} whose endpoint matches. If joined,
	 * the existing river is extended and the new river is discarded (not added to {@code rivers}). If not joined, the new river is added to
	 * {@code rivers}. A second join attempt is made on the result in case the extended river can connect to yet another existing river.
	 *
	 * @return The changed rivers — either newly added rivers or existing rivers that were extended.
	 */
	private static List<River> connectAndAddRivers(List<River> newRivers, List<River> rivers)
	{
		List<River> changed = new ArrayList<>();
		for (River newRiver : newRivers)
		{
			River joined = tryConnectingRiverToExistingRiver(newRiver, rivers);
			if (joined != null)
			{
				River joined2 = tryConnectingRiverToExistingRiver(joined, rivers);
				if (joined2 != null)
				{
					rivers.remove(joined);
					changed.add(joined2);
				}
				else
				{
					changed.add(joined);
				}
			}
			else
			{
				rivers.add(newRiver);
				changed.add(newRiver);
			}
		}
		return changed;
	}

	/**
	 * Attempts to merge each river in {@code candidates} with any other river in {@code rivers} whose endpoint now matches. Used after
	 * edits that may have changed a river's endpoints (control-point deletion, segment cut, erase) — a newly-exposed endpoint may coincide
	 * with another existing river's endpoint, in which case the two should be one continuous river. Mirrors {@link #connectAndAddRivers}'s
	 * join logic but for rivers already in {@code rivers}: when a merge succeeds the candidate is removed from {@code rivers} (its data has
	 * been folded into the matched river). A second join attempt is made on the extended result in case it can also connect at its other
	 * end.
	 *
	 * @return The rivers whose nodes were extended by absorbing a candidate.
	 */
	public static List<River> mergeAdjacentRivers(java.util.Collection<River> candidates, List<River> rivers)
	{
		List<River> extended = new ArrayList<>();
		for (River candidate : candidates)
		{
			if (candidate == null || candidate.nodes.size() < 2 || !rivers.contains(candidate))
			{
				continue;
			}
			River joined = tryConnectingRiverToExistingRiver(candidate, rivers);
			if (joined == null)
			{
				continue;
			}
			rivers.remove(candidate);
			extended.add(joined);
			River joined2 = tryConnectingRiverToExistingRiver(joined, rivers);
			if (joined2 != null)
			{
				rivers.remove(joined);
				extended.add(joined2);
			}
		}
		return extended;
	}

	/**
	 * Attempts to join {@code riverToAdd} to an existing river in {@code rivers} by matching endpoints. If a match is found, the existing
	 * river's nodes are replaced with the merged node list (a single atomic volatile-field swap so concurrent readers don't see partial
	 * state), {@code riverToAdd} is NOT added to {@code rivers}, and the extended existing river is returned. Returns {@code null} if no
	 * endpoint match is found.
	 */
	public static River tryConnectingRiverToExistingRiver(River riverToAdd, List<River> rivers)
	{
		if (riverToAdd == null || riverToAdd.nodes.size() < 2)
		{
			return null;
		}
		List<RiverPathNode> toAdd = riverToAdd.nodes;
		for (River river : rivers)
		{
			if (river == riverToAdd)
			{
				continue;
			}
			List<RiverPathNode> existing = river.nodes;
			if (existing.size() < 2)
			{
				continue;
			}
			List<RiverPathNode> merged = tryMergeEndpoints(existing, toAdd);
			if (merged != null)
			{
				river.nodes = new CopyOnWriteArrayList<>(merged);
				return river;
			}
		}
		return null;
	}

	private static List<RiverPathNode> tryMergeEndpoints(List<RiverPathNode> existing, List<RiverPathNode> toAdd)
	{
		PathOperations.ExistingPathAccessor<RiverPathNode> single = new PathOperations.ExistingPathAccessor<>()
		{
			@Override
			public int count()
			{
				return 1;
			}

			@Override
			public List<RiverPathNode> get()
			{
				return existing;
			}
		};
		PathOperations.Match<RiverPathNode> match = PathOperations.tryConnectToExistingPath(toAdd, single, RIVER_OPS);
		return match == null ? null : match.mergedNodes;
	}

	public static void removeEmptyOrShortRivers(List<River> riverList)
	{
		riverList.removeIf(river -> river.nodes.size() < 2);
	}

	private static Iterable<List<RiverPathNode>> riverNodesList(List<River> rivers)
	{
		List<List<RiverPathNode>> result = new ArrayList<>(rivers.size());
		for (River river : rivers)
		{
			result.add(river.nodes);
		}
		return result;
	}

	/**
	 * Traverses {@code edgeSet} starting at {@code start}, building river(s) as {@link RiverPathNode} lists. Each node's
	 * {@link RiverPathNode#getEdgeIndexToNext()} is set to the {@link Edge#index} of the segment it begins, so later draw and lookup code
	 * can identify the Voronoi edge a polygon-mode segment lies on without re-deriving it from corner positions. Edges whose Voronoi index
	 * already appears in any existing river are skipped (committing the current path and restarting from the far corner). New edge indices
	 * encountered during this walk are also tracked so the same edge isn't added twice within a single drag.
	 */
	private static List<River> buildRiversFromEdgeSet(Set<Edge> edgeSet, Corner start, int riverLevel, double resolutionScale, List<River> existingRivers)
	{
		List<River> result = new ArrayList<>();
		if (edgeSet.isEmpty())
		{
			return result;
		}

		Set<Integer> consumedEdgeIndices = collectEdgeIndicesUsedByExistingRivers(existingRivers);

		Random random = new Random();
		Set<Edge> remaining = new HashSet<>(edgeSet);
		Corner current = start;
		List<RiverPathNode> currentNodes = new ArrayList<>();
		currentNodes.add(makeTerminalNode(current, resolutionScale));

		while (!remaining.isEmpty())
		{
			Edge next = null;
			for (Edge edge : remaining)
			{
				if ((edge.v0 != null && edge.v0 == current) || (edge.v1 != null && edge.v1 == current))
				{
					next = edge;
					break;
				}
			}

			if (next == null)
			{
				if (currentNodes.size() >= 2)
				{
					result.add(new River(currentNodes));
				}
				currentNodes = new ArrayList<>();
				Edge anyEdge = remaining.iterator().next();
				current = anyEdge.v0 != null ? anyEdge.v0 : anyEdge.v1;
				currentNodes.add(makeTerminalNode(current, resolutionScale));
			}
			else
			{
				remaining.remove(next);
				Corner nextCorner = (next.v0 != null && next.v0 == current) ? next.v1 : next.v0;
				if (nextCorner != null)
				{
					if (consumedEdgeIndices.contains(next.index))
					{
						if (currentNodes.size() >= 2)
						{
							result.add(new River(currentNodes));
						}
						currentNodes = new ArrayList<>();
						currentNodes.add(makeTerminalNode(nextCorner, resolutionScale));
					}
					else
					{
						consumedEdgeIndices.add(next.index);
						// Replace the last (terminal) node with one that carries this segment's
						// per-segment metadata, then append a fresh terminal for the far corner.
						RiverPathNode prev = currentNodes.get(currentNodes.size() - 1);
						currentNodes.set(currentNodes.size() - 1, new RiverPathNode(prev.getLoc(), riverLevel, random.nextLong(), next.index));
						currentNodes.add(makeTerminalNode(nextCorner, resolutionScale));
					}
					current = nextCorner;
				}
				else
				{
					current = null;
				}
			}
		}

		if (currentNodes.size() >= 2)
		{
			result.add(new River(currentNodes));
		}
		return result;
	}

	private static RiverPathNode makeTerminalNode(Corner corner, double resolutionScale)
	{
		return new RiverPathNode(corner.loc.mult(1.0 / resolutionScale), 0, 0L, RiverPathNode.EDGE_INDEX_NONE);
	}

	private static Set<Integer> collectEdgeIndicesUsedByExistingRivers(List<River> rivers)
	{
		Set<Integer> result = new HashSet<>();
		if (rivers == null)
		{
			return result;
		}
		for (River river : rivers)
		{
			List<RiverPathNode> nodes = river.nodes;
			for (int i = 0; i < nodes.size() - 1; i++)
			{
				int edgeIndex = nodes.get(i).getEdgeIndexToNext();
				if (edgeIndex != RiverPathNode.EDGE_INDEX_NONE)
				{
					result.add(edgeIndex);
				}
			}
		}
		return result;
	}
}
