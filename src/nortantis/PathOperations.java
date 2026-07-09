package nortantis;

import nortantis.editor.PathNode;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.util.OrderlessPair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generic helpers shared by road and river path handling. All methods operate on {@code List<? extends PathNode>} — only
 * {@link PathNode#getLoc()} is required from the node type, so anything that has to preserve per-segment metadata (river widths/seeds
 * during reverse or cross-endpoint merge) lives in {@link NodeMetadataOps} and is passed in by the caller.
 */
public final class PathOperations
{
	private PathOperations()
	{
	}

	/**
	 * Strategy for preserving per-segment "to-next" metadata when the path is reversed or two paths are stitched together at a shared
	 * endpoint.
	 *
	 * <p>
	 * Road nodes have no per-segment metadata, so {@link RoadDrawer#ROAD_OPS} returns the target unchanged. River nodes carry width and
	 * seed for the segment leaving them, so {@link RiverDrawer#RIVER_OPS} produces new nodes that move that metadata to the right place
	 * when the path direction or shape changes.
	 */
	public interface NodeMetadataOps<T extends PathNode>
	{
		/** Returns a node at {@code original}'s location with cleared "to-next" metadata (last-node convention). */
		T withClearedMetadata(T original);

		/** Returns a node at {@code target}'s location with "to-next" metadata copied from {@code donor}. */
		T withMetadataFrom(T target, T donor);

		/**
		 * Returns a node whose "to-next" metadata describes a stitch-bridge segment (the original outgoing segment's destination CP was
		 * removed; the new segment bridges directly to the next surviving CP and no longer follows a single Voronoi edge). For rivers this
		 * preserves width and seed but clears the Voronoi edge index; for roads, which have no per-segment metadata, the original node is
		 * returned unchanged.
		 */
		T withStitchedToNextMetadata(T original);
	}

	/**
	 * Reverses {@code path} while shifting each node's "to-next" metadata so it still describes the correct segment in the new direction.
	 * The last node of the result has cleared metadata.
	 */
	public static <T extends PathNode> List<T> reverseWithMetadata(List<T> path, NodeMetadataOps<T> ops)
	{
		int n = path.size();
		List<T> result = new ArrayList<>(n);
		for (int i = 0; i < n; i++)
		{
			T origNode = path.get(n - 1 - i);
			if (i < n - 1)
			{
				// The segment leaving the reversed-position-i node corresponds to the original
				// segment between old positions n-1-i and n-2-i. Its "to-next" metadata was stored
				// on the lower-index node in the original, which is at old position n-2-i.
				T donor = path.get(n - 2 - i);
				result.add(ops.withMetadataFrom(origNode, donor));
			}
			else
			{
				result.add(ops.withClearedMetadata(origNode));
			}
		}
		return result;
	}

	/**
	 * Removes every segment in {@code removedSegments} from {@code path}, returning the remaining sub-paths (each with at least 2 nodes). A
	 * segment matches by its unordered endpoint-location pair, so paths drawn in either direction are handled.
	 *
	 * <p>
	 * Paths that share only a node <em>location</em> with a removed segment (e.g. another river branching off a junction whose segment
	 * happens to start at the same point as a removed segment) are returned unchanged — only paths that actually contain a matching
	 * consecutive segment are split.
	 */
	public static <T extends PathNode> List<List<T>> splitAtSegments(List<T> path, Set<OrderlessPair<Point>> removedSegments)
	{
		List<List<T>> result = new ArrayList<>();
		if (path.size() < 2)
		{
			return result;
		}

		List<T> current = new ArrayList<>();
		current.add(path.get(0));
		for (int i = 0; i < path.size() - 1; i++)
		{
			T from = path.get(i);
			T to = path.get(i + 1);
			boolean segmentRemoved = removedSegments.contains(new OrderlessPair<>(from.getLoc(), to.getLoc()));
			if (segmentRemoved)
			{
				if (current.size() >= 2)
				{
					result.add(current);
				}
				current = new ArrayList<>();
				current.add(to);
			}
			else
			{
				current.add(to);
			}
		}
		if (current.size() >= 2)
		{
			result.add(current);
		}
		return result;
	}

	/**
	 * Applies a mixed delete plan to a single path, returning the surviving fragments (each with at least 2 nodes).
	 *
	 * <ul>
	 * <li>{@code edgesToRemove} indexes the segments to drop ({@code i} = segment from {@code nodes.get(i)} to {@code nodes.get(i + 1)});
	 * the path is split at each, and the last node of each fragment ending at a removed edge has its "to-next" metadata cleared.</li>
	 * <li>{@code isolatedNodesToRemove} indexes nodes to drop with a stitch (the surviving CP before the removed CP has its "to-next"
	 * metadata replaced via {@link NodeMetadataOps#withStitchedToNextMetadata}, since the new bridging segment doesn't follow a single
	 * Voronoi edge anymore). If the removed node was the original path's last surviving node in its fragment, the new last node's metadata
	 * is fully cleared instead.</li>
	 * </ul>
	 *
	 * The two sets are independent — a node can be removed without its surrounding edges being in {@code edgesToRemove}, and vice versa.
	 * Fragments with fewer than 2 surviving nodes are dropped (orphan CPs aren't a representable path).
	 */
	public static <T extends PathNode> List<List<T>> applySelectionDeletes(List<T> originalNodes, Set<Integer> isolatedNodesToRemove, Set<Integer> edgesToRemove, NodeMetadataOps<T> ops)
	{
		List<List<T>> fragments = new ArrayList<>();
		if (originalNodes == null || originalNodes.isEmpty())
		{
			return fragments;
		}
		List<T> current = new ArrayList<>();
		boolean justSkippedNode = false;
		int n = originalNodes.size();
		for (int i = 0; i < n; i++)
		{
			if (isolatedNodesToRemove.contains(i))
			{
				justSkippedNode = true;
				continue;
			}
			T node = originalNodes.get(i);
			if (justSkippedNode && !current.isEmpty())
			{
				T lastInCurrent = current.get(current.size() - 1);
				current.set(current.size() - 1, ops.withStitchedToNextMetadata(lastInCurrent));
			}
			justSkippedNode = false;
			current.add(node);
			if (i < n - 1 && edgesToRemove.contains(i))
			{
				if (current.size() >= 2)
				{
					T lastInCurrent = current.get(current.size() - 1);
					current.set(current.size() - 1, ops.withClearedMetadata(lastInCurrent));
					fragments.add(current);
				}
				current = new ArrayList<>();
				justSkippedNode = false;
			}
		}
		if (justSkippedNode && !current.isEmpty())
		{
			T lastInCurrent = current.get(current.size() - 1);
			current.set(current.size() - 1, ops.withClearedMetadata(lastInCurrent));
		}
		if (current.size() >= 2)
		{
			fragments.add(current);
		}
		return fragments;
	}

	/**
	 * After {@link #splitAtSegments} has been applied (or after any other operation that cuts paths at given locations), returns the
	 * locations of the "inner neighbor" (second-from-end) nodes of any path whose endpoint location matches one of the removed segments'
	 * endpoints. Callers that build incremental redraw bounds from the removed segments should include these locations too.
	 *
	 * <p>
	 * The reason: when a path is split, the segments that used to flank the cut become new end segments. End segments are typically
	 * rendered with a synthetic reflection control point in place of the real neighbor (see {@code RiverDrawer.buildSegmentPathPixels} and
	 * {@code CurveCreator.createCurve(List)}), which changes the Catmull-Rom curve shape along the whole new end segment. The redraw bounds
	 * must therefore cover both endpoints of each new end segment — otherwise the curve drawn inside the bounds (new shape) tears from the
	 * unchanged pixels outside (old shape from the previous full draw). Multi-path junctions are handled: a path that merely happened to
	 * start/end at a cut point also gets split, so its inner neighbor needs to be reported too.
	 *
	 * @param pathsAfterSplit
	 *            the node lists of all paths after the split has been applied
	 * @param removedSegments
	 *            the segments that were removed (each a 2-point list of endpoint locations)
	 * @return locations of the inner-neighbor nodes, deduplicated
	 */
	public static List<Point> findInnerNeighborsOfCutEndpoints(Iterable<? extends List<? extends PathNode>> pathsAfterSplit, List<List<Point>> removedSegments)
	{
		if (pathsAfterSplit == null || removedSegments == null || removedSegments.isEmpty())
		{
			return Collections.emptyList();
		}
		Set<Point> cutPoints = new HashSet<>();
		for (List<Point> seg : removedSegments)
		{
			cutPoints.addAll(seg);
		}
		Set<Point> result = new HashSet<>();
		for (List<? extends PathNode> nodes : pathsAfterSplit)
		{
			if (nodes == null || nodes.size() < 2)
			{
				continue;
			}
			if (cutPoints.contains(nodes.get(0).getLoc()))
			{
				result.add(nodes.get(1).getLoc());
			}
			if (cutPoints.contains(nodes.get(nodes.size() - 1).getLoc()))
			{
				result.add(nodes.get(nodes.size() - 2).getLoc());
			}
		}
		return new ArrayList<>(result);
	}

	/** Aggregates orderless pairs of consecutive node locations across a collection of paths. */
	public static Set<OrderlessPair<Point>> collectAllConnections(Iterable<? extends List<? extends PathNode>> paths)
	{
		Set<OrderlessPair<Point>> result = new HashSet<>();
		for (List<? extends PathNode> path : paths)
		{
			for (int i = 0; i < path.size() - 1; i++)
			{
				result.add(new OrderlessPair<>(path.get(i).getLoc(), path.get(i + 1).getLoc()));
			}
		}
		return result;
	}

	/** Indexed accessor over a collection of existing paths, used by {@link #tryConnectToExistingPath}. */
	public interface ExistingPathAccessor<T extends PathNode>
	{
		int count();

		List<T> get();
	}

	/**
	 * Try to merge {@code pathToAdd} into one of the paths reachable through {@code existing} by matching one of its endpoint locations to
	 * an existing endpoint. Returns the merged node list if a match was found, or {@code null} if no endpoint match exists.
	 *
	 * <p>
	 * The matched node from {@code pathToAdd} is dropped; if its segment metadata needs to be preserved across the join (the "append" /
	 * "reverse-and-append" cases), {@code ops} is used to transfer the "to-next" metadata to the surviving node so the resulting path is
	 * width/seed-consistent.
	 *
	 * @param existing
	 *            Accessor that exposes the current snapshot of each existing path. {@code null} or empty entries are skipped.
	 */
	public static <T extends PathNode> Match<T> tryConnectToExistingPath(List<T> pathToAdd, ExistingPathAccessor<T> existing, NodeMetadataOps<T> ops)
	{
		if (pathToAdd == null || pathToAdd.size() < 2)
		{
			return null;
		}
		for (int i = 0; i < existing.count(); i++)
		{
			List<T> other = existing.get();
			if (other == null || other.isEmpty() || other == pathToAdd)
			{
				continue;
			}

			Point otherStart = other.get(0).getLoc();
			Point otherEnd = other.get(other.size() - 1).getLoc();
			Point addStart = pathToAdd.get(0).getLoc();
			Point addEnd = pathToAdd.get(pathToAdd.size() - 1).getLoc();

			// Normalize each merged result so a join that happens to stitch two reversed paths (which would place the same segment
			// back-to-back) collapses to a single line instead of a double line.
			if (otherStart.isCloseEnough(addStart))
			{
				List<T> merged = mergeReverseAndPrepend(other, pathToAdd, ops);
				return new Match<>(normalizePath(merged, ops));
			}
			if (otherStart.isCloseEnough(addEnd))
			{
				List<T> merged = mergePrepend(other, pathToAdd);
				return new Match<>(normalizePath(merged, ops));
			}
			if (otherEnd.isCloseEnough(addStart))
			{
				List<T> merged = mergeAppend(other, pathToAdd, ops);
				return new Match<>(normalizePath(merged, ops));
			}
			if (otherEnd.isCloseEnough(addEnd))
			{
				List<T> merged = mergeReverseAndAppend(other, pathToAdd, ops);
				return new Match<>(normalizePath(merged, ops));
			}
		}
		return null;
	}

	/** Result of {@link #tryConnectToExistingPath}: index of the matched existing path and the merged node list. */
	public static final class Match<T extends PathNode>
	{
		public final List<T> mergedNodes;

		public Match(List<T> mergedNodes)
		{
			this.mergedNodes = mergedNodes;
		}
	}

	// existingStart == addStart: reverse pathToAdd, drop its now-last (matching) node, prepend to existing.
	private static <T extends PathNode> List<T> mergeReverseAndPrepend(List<T> existing, List<T> pathToAdd, NodeMetadataOps<T> ops)
	{
		List<T> reversed = reverseWithMetadata(pathToAdd, ops);
		List<T> dropped = reversed.subList(0, reversed.size() - 1);
		List<T> merged = new ArrayList<>(dropped.size() + existing.size());
		merged.addAll(dropped);
		merged.addAll(existing);
		return merged;
	}

	// existingStart == addEnd: drop pathToAdd's last (matching) node and prepend.
	private static <T extends PathNode> List<T> mergePrepend(List<T> existing, List<T> pathToAdd)
	{
		List<T> dropped = pathToAdd.subList(0, pathToAdd.size() - 1);
		List<T> merged = new ArrayList<>(dropped.size() + existing.size());
		merged.addAll(dropped);
		merged.addAll(existing);
		return merged;
	}

	// existingEnd == addStart: drop pathToAdd's first (matching) node, transferring its to-next
	// metadata onto existing's last node so the new join segment carries the correct width/seed.
	private static <T extends PathNode> List<T> mergeAppend(List<T> existing, List<T> pathToAdd, NodeMetadataOps<T> ops)
	{
		List<T> result = new ArrayList<>(existing.size() + pathToAdd.size() - 1);
		result.addAll(existing.subList(0, existing.size() - 1));
		result.add(ops.withMetadataFrom(existing.get(existing.size() - 1), pathToAdd.get(0)));
		result.addAll(pathToAdd.subList(1, pathToAdd.size()));
		return result;
	}

	// existingEnd == addEnd: reverse pathToAdd, drop its now-first (matching) node with metadata
	// transfer to existing's last node.
	private static <T extends PathNode> List<T> mergeReverseAndAppend(List<T> existing, List<T> pathToAdd, NodeMetadataOps<T> ops)
	{
		List<T> reversed = reverseWithMetadata(pathToAdd, ops);
		List<T> result = new ArrayList<>(existing.size() + reversed.size() - 1);
		result.addAll(existing.subList(0, existing.size() - 1));
		result.add(ops.withMetadataFrom(existing.get(existing.size() - 1), reversed.get(0)));
		result.addAll(reversed.subList(1, reversed.size()));
		return result;
	}

	/**
	 * Returns true if any segment of {@code path} (or its expanded jagged envelope) overlaps the given resolution-invariant bounds. Used by
	 * both road and river drawing to skip paths that cannot contribute to the current draw region.
	 *
	 * @param expansionRI
	 *            Inflates the bounds by this much in each direction before testing. Use 0 for an exact bbox check; rivers use
	 *            {@code jaggedAmplitudeRI} to account for the bulge.
	 */
	public static boolean pathOverlapsRectangle(List<? extends PathNode> path, Rectangle boundsRI, double expansionRI)
	{
		Rectangle expanded = expansionRI == 0 ? boundsRI : new Rectangle(boundsRI.x - expansionRI, boundsRI.y - expansionRI, boundsRI.width + 2 * expansionRI, boundsRI.height + 2 * expansionRI);
		for (int i = 0; i < path.size() - 1; i++)
		{
			Point p1 = path.get(i).getLoc();
			Point p2 = path.get(i + 1).getLoc();
			Rectangle segBounds = Rectangle.fromCorners(Math.min(p1.x, p2.x), Math.min(p1.y, p2.y), Math.max(p1.x, p2.x), Math.max(p1.y, p2.y));
			if (expanded.overlaps(segBounds))
			{
				return true;
			}
		}
		return false;
	}

	/** Returns a new list containing each node's location, in order. */
	public static List<Point> toLocationList(List<? extends PathNode> path)
	{
		List<Point> result = new ArrayList<>(path.size());
		for (PathNode node : path)
		{
			result.add(node.getLoc());
		}
		return result;
	}

	/**
	 * Catmull-Rom propagation radius: a single control-point move/insert/delete in a uniform Catmull-Rom spline changes the curve shape on
	 * at most the 4 segments whose endpoints lie within 2 control-point steps of the edit (the moved CP is referenced as a tangent by
	 * segments up to 2 steps away). Callers use this when scoping incremental redraws to "the affected segments and their immediate
	 * neighbors".
	 */
	public static final int CATMULL_ROM_PROPAGATION_RADIUS = 2;

	/**
	 * Returns up to {@code 2*radius+1} node locations from {@code path}, centered on {@code centerIndex} and clamped to the path's bounds.
	 * Returns an empty list for null/empty paths or when the clamped range is empty.
	 *
	 * <p>
	 * For incremental-redraw bounds covering one control-point edit, pass {@link #CATMULL_ROM_PROPAGATION_RADIUS} as {@code radius}.
	 */
	public static List<Point> nodeLocationsAround(List<? extends PathNode> path, int centerIndex, int radius)
	{
		if (path == null || path.isEmpty())
		{
			return Collections.emptyList();
		}
		int from = Math.max(0, centerIndex - radius);
		int to = Math.min(path.size() - 1, centerIndex + radius);
		if (from > to)
		{
			return Collections.emptyList();
		}
		List<Point> result = new ArrayList<>(to - from + 1);
		for (int i = from; i <= to; i++)
		{
			result.add(path.get(i).getLoc());
		}
		return result;
	}

	/**
	 * Same as {@link #nodeLocationsAround} but operates on a list of already-extracted point locations (e.g. a pre-edit snapshot captured
	 * via {@link #toLocationList}).
	 */
	public static List<Point> pointsAround(List<Point> points, int centerIndex, int radius)
	{
		if (points == null || points.isEmpty())
		{
			return Collections.emptyList();
		}
		int from = Math.max(0, centerIndex - radius);
		int to = Math.min(points.size() - 1, centerIndex + radius);
		if (from > to)
		{
			return Collections.emptyList();
		}
		List<Point> result = new ArrayList<>(to - from + 1);
		for (int i = from; i <= to; i++)
		{
			result.add(points.get(i));
		}
		return result;
	}

	/** Deduplicates consecutive nodes whose locations are {@link Point#isCloseEnough}. */
	public static <T extends PathNode> List<T> deduplicateConsecutive(List<T> path)
	{
		if (path.isEmpty())
		{
			return Collections.emptyList();
		}
		List<T> result = new ArrayList<>(path.size());
		for (T node : path)
		{
			if (result.isEmpty() || !result.get(result.size() - 1).getLoc().isCloseEnough(node.getLoc()))
			{
				result.add(node);
			}
		}
		return result;
	}

	/**
	 * Cleans up the degeneracies that path edits (control-point deletion with stitching, or endpoint merges) can introduce when a path
	 * revisits a location - most commonly around the closing control point of a loop that was drawn start-to-start, so its first and last
	 * nodes share a location. Two things are removed:
	 * <ul>
	 * <li><b>Zero-length segments</b> - consecutive control points at the same location are collapsed to one. The later node's "to-next"
	 * metadata is kept on the survivor, so the segment leaving the collapsed location stays width/seed-correct.</li>
	 * <li><b>Backtrack spurs at either end</b> - a terminal triple {@code P, Q, P} (the path steps out to Q and immediately returns to the
	 * location it left) draws the P-Q segment twice, so the returning endpoint is dropped, leaving a single {@code P, Q}. When a trailing
	 * spur is dropped the new last node's "to-next" metadata is cleared (last-node convention). Interior triples are left alone, so a
	 * genuine closed loop like {@code A, B, C, A} is preserved.</li>
	 * </ul>
	 * The result may have fewer than 2 nodes (for example a path that was entirely a zero-length segment); callers should drop any path that
	 * no longer has at least 2 nodes, since a lone control point isn't a representable path.
	 */
	public static <T extends PathNode> List<T> normalizePath(List<T> path, NodeMetadataOps<T> ops)
	{
		if (path == null || path.isEmpty())
		{
			return Collections.emptyList();
		}

		// Collapse consecutive coincident nodes, keeping the metadata of the last node in each run (it describes the segment that
		// actually leaves the collapsed location).
		List<T> result = new ArrayList<>(path.size());
		for (T node : path)
		{
			if (!result.isEmpty() && result.get(result.size() - 1).getLoc().isCloseEnough(node.getLoc()))
			{
				int last = result.size() - 1;
				result.set(last, ops.withMetadataFrom(result.get(last), node));
			}
			else
			{
				result.add(node);
			}
		}

		// Drop trailing backtrack spurs: the last three nodes form P, Q, P. The returning node is redundant (its segment retraces the
		// previous one), so remove it and clear the new last node's "to-next" metadata.
		while (result.size() >= 3 && result.get(result.size() - 1).getLoc().isCloseEnough(result.get(result.size() - 3).getLoc()))
		{
			result.remove(result.size() - 1);
			int last = result.size() - 1;
			result.set(last, ops.withClearedMetadata(result.get(last)));
		}

		// Drop leading backtrack spurs: the first three nodes form P, Q, P. Removing the redundant head leaves the rest's metadata intact.
		while (result.size() >= 3 && result.get(0).getLoc().isCloseEnough(result.get(2).getLoc()))
		{
			result.remove(0);
		}

		return result;
	}
}
