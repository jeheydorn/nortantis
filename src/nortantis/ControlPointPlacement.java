package nortantis;

import nortantis.editor.CenterEdit;
import nortantis.editor.PathNode;
import nortantis.editor.River;
import nortantis.editor.RiverPathNode;
import nortantis.editor.Road;
import nortantis.editor.RoadPathNode;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.graph.voronoi.Center;
import nortantis.swing.MapEdits;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Where river and road control points can be. A control point can stay where it is if it is on the map, which includes any part of the map
 * under the border, and, for a river, not on water, so that no part of a river is left hidden in an ocean or lake. Roads can be drawn over
 * water. A control point that can't stay is kept anyway if a segment joins it to one that can, which lets a river reach its mouth and a line
 * run to the edge of the map.
 */
public final class ControlPointPlacement
{
	private ControlPointPlacement()
	{
	}

	/**
	 * Returns a test for whether a control point, given in resolution-invariant coordinates, can stay where it is.
	 *
	 * @param mustBeOnLand
	 *            True for river control points, false for road control points.
	 */
	public static Predicate<Point> createCanStayTest(WorldGraph graph, MapEdits edits, boolean mustBeOnLand)
	{
		double scale = graph.resolutionScale;
		Rectangle mapBoundsRI = graph.bounds.scaleAboutOrigin(1.0 / scale);
		return locationRI ->
		{
			if (!mapBoundsRI.contains(locationRI))
			{
				return false;
			}
			if (!mustBeOnLand)
			{
				return true;
			}
			// The water-check resolution keeps the answer the same at every display quality.
			Center center = graph.findClosestCenter(locationRI.mult(scale), false, true);
			if (center == null)
			{
				return false;
			}
			// Read land and water from the edits rather than the graph, which may not have had them applied yet during a draw.
			CenterEdit centerEdit = edits == null ? null : edits.centerEdits.get(center.index);
			return centerEdit != null ? !centerEdit.isWater : !center.isWater;
		};
	}

	/**
	 * Returns the indices of the control points in {@code nodes} that fail {@code canStay} and have no neighbor that passes it.
	 */
	public static Set<Integer> findControlPointsThatCannotStay(List<? extends PathNode> nodes, Predicate<Point> canStay)
	{
		int n = nodes.size();
		boolean[] canNodeStay = new boolean[n];
		for (int i = 0; i < n; i++)
		{
			canNodeStay[i] = canStay.test(nodes.get(i).getLoc());
		}
		Set<Integer> result = new HashSet<>();
		for (int i = 0; i < n; i++)
		{
			boolean hasNeighborThatCanStay = (i > 0 && canNodeStay[i - 1]) || (i < n - 1 && canNodeStay[i + 1]);
			if (!canNodeStay[i] && !hasNeighborThatCanStay)
			{
				result.add(i);
			}
		}
		return result;
	}

	/**
	 * Returns the indices of the segments (index {@code i} is the segment from node {@code i} to node {@code i + 1}) that touch any of the
	 * given nodes in a line with {@code nodeCount} nodes.
	 */
	public static Set<Integer> findSegmentsTouching(Set<Integer> nodeIndices, int nodeCount)
	{
		Set<Integer> result = new HashSet<>();
		for (int i : nodeIndices)
		{
			if (i > 0)
			{
				result.add(i - 1);
			}
			if (i < nodeCount - 1)
			{
				result.add(i);
			}
		}
		return result;
	}

	/**
	 * Removes, from every river and road in {@code edits} except those in {@link MapEdits#linesExemptFromControlPointRemoval}, the control
	 * points that can't stay where they are. Removing a control point removes the segments touching it, splitting its line if needed.
	 *
	 * @return The node locations, in resolution-invariant coordinates, that the changed lines had before the removal.
	 */
	public static List<List<Point>> removeControlPointsThatCannotStay(MapEdits edits, WorldGraph graph)
	{
		if (edits == null || !edits.hasInitializedRivers)
		{
			return Collections.emptyList();
		}
		Set<Object> exempt = edits.linesExemptFromControlPointRemoval == null ? Collections.emptySet() : edits.linesExemptFromControlPointRemoval;
		List<List<Point>> beforePaths = new ArrayList<>();

		Predicate<Point> canRiverControlPointStay = createCanStayTest(graph, edits, true);
		for (River river : new ArrayList<>(edits.rivers))
		{
			if (exempt.contains(river))
			{
				continue;
			}
			List<RiverPathNode> nodes = river.nodes;
			Set<Integer> segmentsToRemove = findSegmentsTouching(findControlPointsThatCannotStay(nodes, canRiverControlPointStay), nodes.size());
			if (!segmentsToRemove.isEmpty())
			{
				beforePaths.add(PathOperations.toLocationList(nodes));
				RiverDrawer.replaceWithFragments(edits.rivers, river, PathOperations.applySelectionDeletes(nodes, Collections.emptySet(), segmentsToRemove, RiverDrawer.RIVER_OPS),
						new ArrayList<>());
			}
		}

		Predicate<Point> canRoadControlPointStay = createCanStayTest(graph, edits, false);
		for (Road road : new ArrayList<>(edits.roads))
		{
			if (exempt.contains(road))
			{
				continue;
			}
			List<RoadPathNode> nodes = road.nodes;
			Set<Integer> segmentsToRemove = findSegmentsTouching(findControlPointsThatCannotStay(nodes, canRoadControlPointStay), nodes.size());
			if (!segmentsToRemove.isEmpty())
			{
				beforePaths.add(PathOperations.toLocationList(nodes));
				RoadDrawer.replaceWithFragments(edits.roads, road, PathOperations.applySelectionDeletes(nodes, Collections.emptySet(), segmentsToRemove, RoadDrawer.ROAD_OPS),
						new ArrayList<>());
			}
		}
		return beforePaths;
	}

	/**
	 * Returns the centers under the given points, which are in resolution-invariant coordinates.
	 */
	public static Set<Center> findCentersAtPoints(WorldGraph graph, List<List<Point>> pointListsRI)
	{
		Set<Center> result = new HashSet<>();
		for (List<Point> points : pointListsRI)
		{
			for (Point point : points)
			{
				// Clamp the point into the map bounds before the lookup. A path (e.g. a freehand river) can have a node that lies beyond the
				// map border; the segment leading to that node still needs to draw up to the edge, where it will be covered by the border.
				// Dropping off-map nodes would leave the redraw bounds short, so the on-map part of the boundary-crossing segment would be
				// clipped during an incremental update (a full draw is unaffected because it ignores these bounds). Clamping maps the off-map
				// node to the border center where the segment exits the map, which extends the redraw bounds to the edge.
				Point pixel = point.mult(graph.resolutionScale);
				double clampedX = Math.min(Math.max(pixel.x, 0), graph.getWidth() - 1);
				double clampedY = Math.min(Math.max(pixel.y, 0), graph.getHeight() - 1);
				Center c = graph.findClosestCenter(new Point(clampedX, clampedY), true);
				if (c != null)
				{
					result.add(c);
				}
			}
		}
		return result;
	}
}
