package nortantis;

import nortantis.editor.RoadPathNode;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.util.OrderlessPair;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PathOperationsTest
{
	private static List<RoadPathNode> road(Point... locations)
	{
		List<RoadPathNode> result = new ArrayList<>(locations.length);
		for (Point loc : locations)
		{
			result.add(new RoadPathNode(loc));
		}
		return result;
	}

	@Test
	public void toLocationList_returnsLocationsInOrder()
	{
		Point p1 = new Point(1, 2);
		Point p2 = new Point(3, 4);
		Point p3 = new Point(5, 6);
		List<Point> result = PathOperations.toLocationList(road(p1, p2, p3));
		assertEquals(Arrays.asList(p1, p2, p3), result);
	}

	@Test
	public void toLocationList_empty()
	{
		assertEquals(Collections.emptyList(), PathOperations.toLocationList(Collections.emptyList()));
	}

	@Test
	public void deduplicateConsecutive_removesAdjacentDuplicates()
	{
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		Point c = new Point(2, 0);
		List<RoadPathNode> path = road(a, a, b, b, b, c);
		List<RoadPathNode> result = PathOperations.deduplicateConsecutive(path);
		assertEquals(Arrays.asList(a, b, c), PathOperations.toLocationList(result));
	}

	@Test
	public void deduplicateConsecutive_preservesSeparatedDuplicates()
	{
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		List<RoadPathNode> path = road(a, b, a, b);
		List<RoadPathNode> result = PathOperations.deduplicateConsecutive(path);
		// No two consecutive duplicates, so list is unchanged.
		assertEquals(Arrays.asList(a, b, a, b), PathOperations.toLocationList(result));
	}

	@Test
	public void deduplicateConsecutive_empty()
	{
		assertEquals(Collections.emptyList(), PathOperations.deduplicateConsecutive(Collections.<RoadPathNode>emptyList()));
	}

	@Test
	public void normalizePath_collapsesEndpointBacktrackSpur()
	{
		// [A, B, A] is what deleting the interior CP of a loop drawn A -> B -> C -> A leaves after the stitch: it draws the A-B
		// segment twice. It should collapse to a single A -> B line.
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		List<RoadPathNode> result = PathOperations.normalizePath(road(a, b, a), RoadDrawer.ROAD_OPS);
		assertEquals(Arrays.asList(a, b), PathOperations.toLocationList(result));
	}

	@Test
	public void normalizePath_dropsZeroLengthPathToBelowTwoNodes()
	{
		// [A, A] is a zero-length phantom (two coincident CPs, no visible segment). It must collapse to a single node so callers drop it.
		Point a = new Point(5, 5);
		List<RoadPathNode> result = PathOperations.normalizePath(road(a, a), RoadDrawer.ROAD_OPS);
		assertTrue(result.size() < 2);
	}

	@Test
	public void normalizePath_preservesGenuineClosedLoop()
	{
		// A -> B -> C -> A is a real triangle (distinct middle nodes), not a backtrack, so it is left untouched.
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		Point c = new Point(1, 1);
		List<RoadPathNode> result = PathOperations.normalizePath(road(a, b, c, a), RoadDrawer.ROAD_OPS);
		assertEquals(Arrays.asList(a, b, c, a), PathOperations.toLocationList(result));
	}

	@Test
	public void normalizePath_collapsesTrailingSpurOnLongerPath()
	{
		// A -> B -> C -> B ends with a backtrack (C -> B retraces B -> C), so the returning B is dropped.
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		Point c = new Point(2, 0);
		List<RoadPathNode> result = PathOperations.normalizePath(road(a, b, c, b), RoadDrawer.ROAD_OPS);
		assertEquals(Arrays.asList(a, b, c), PathOperations.toLocationList(result));
	}

	@Test
	public void normalizePath_leavesCleanPathUnchanged()
	{
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		Point c = new Point(2, 0);
		List<RoadPathNode> result = PathOperations.normalizePath(road(a, b, c), RoadDrawer.ROAD_OPS);
		assertEquals(Arrays.asList(a, b, c), PathOperations.toLocationList(result));
	}

	@Test
	public void normalizePath_fullyCollapsesBackAndForth()
	{
		// A -> B -> A -> B is entirely a doubled segment; it should reduce to a single A -> B.
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		List<RoadPathNode> result = PathOperations.normalizePath(road(a, b, a, b), RoadDrawer.ROAD_OPS);
		assertEquals(Arrays.asList(a, b), PathOperations.toLocationList(result));
	}

	@Test
	public void normalizePath_keepsRiverSegmentWidthWhenCollapsingSpur()
	{
		// Collapsing [A, B, A] to [A, B] must keep the surviving A->B segment's width (stored on A) and clear the new last node B's
		// "to-next" metadata (last-node convention).
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		List<nortantis.editor.RiverPathNode> path = Arrays.asList(new nortantis.editor.RiverPathNode(a, 3, 100L), new nortantis.editor.RiverPathNode(b, 7, 200L),
				new nortantis.editor.RiverPathNode(a, 0, 0L));
		List<nortantis.editor.RiverPathNode> result = PathOperations.normalizePath(path, RiverDrawer.RIVER_OPS);
		assertEquals(2, result.size());
		assertEquals(a, result.get(0).getLoc());
		assertEquals(3, result.get(0).getWidthLevelToNext());
		assertEquals(100L, result.get(0).getSeedToNext());
		assertEquals(b, result.get(1).getLoc());
		assertEquals(0, result.get(1).getWidthLevelToNext());
	}

	@Test
	public void pathOverlapsRectangle_segmentInsideBounds()
	{
		List<RoadPathNode> path = road(new Point(5, 5), new Point(15, 15));
		Rectangle bounds = new Rectangle(0, 0, 20, 20);
		assertTrue(PathOperations.pathOverlapsRectangle(path, bounds, 0));
	}

	@Test
	public void pathOverlapsRectangle_segmentOutsideBounds()
	{
		List<RoadPathNode> path = road(new Point(30, 30), new Point(40, 40));
		Rectangle bounds = new Rectangle(0, 0, 20, 20);
		assertFalse(PathOperations.pathOverlapsRectangle(path, bounds, 0));
	}

	@Test
	public void pathOverlapsRectangle_expansionIncludesSegment()
	{
		List<RoadPathNode> path = road(new Point(22, 22), new Point(25, 25));
		Rectangle bounds = new Rectangle(0, 0, 20, 20);
		// Without expansion, outside.
		assertFalse(PathOperations.pathOverlapsRectangle(path, bounds, 0));
		// With expansion of 5, the bounds reach (25, 25) so the segment overlaps.
		assertTrue(PathOperations.pathOverlapsRectangle(path, bounds, 5));
	}

	@Test
	public void splitAtSegments_noRemovedSegmentsReturnsWholePath()
	{
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		Point c = new Point(2, 0);
		List<List<RoadPathNode>> result = PathOperations.splitAtSegments(road(a, b, c), new HashSet<>());
		assertEquals(1, result.size());
		assertEquals(Arrays.asList(a, b, c), PathOperations.toLocationList(result.get(0)));
	}

	@Test
	public void splitAtSegments_middleSegmentRemovedSplitsPath()
	{
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		Point c = new Point(2, 0);
		Point d = new Point(3, 0);
		Set<OrderlessPair<Point>> removed = new HashSet<>();
		removed.add(new OrderlessPair<>(b, c));
		List<List<RoadPathNode>> result = PathOperations.splitAtSegments(road(a, b, c, d), removed);
		// b→c is removed; both endpoints stay with their preserved neighbor segments.
		assertEquals(2, result.size());
		assertEquals(Arrays.asList(a, b), PathOperations.toLocationList(result.get(0)));
		assertEquals(Arrays.asList(c, d), PathOperations.toLocationList(result.get(1)));
	}

	@Test
	public void splitAtSegments_adjacentSegmentsRemovedDropDegenerateMiddle()
	{
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		Point c = new Point(2, 0);
		Point d = new Point(3, 0);
		Set<OrderlessPair<Point>> removed = new HashSet<>();
		removed.add(new OrderlessPair<>(a, b));
		removed.add(new OrderlessPair<>(b, c));
		List<List<RoadPathNode>> result = PathOperations.splitAtSegments(road(a, b, c, d), removed);
		// a→b and b→c both removed — the in-between b becomes a single-node fragment and is dropped. Only c→d survives.
		assertEquals(1, result.size());
		assertEquals(Arrays.asList(c, d), PathOperations.toLocationList(result.get(0)));
	}

	@Test
	public void splitAtSegments_singleNodePathReturnsEmpty()
	{
		List<List<RoadPathNode>> result = PathOperations.splitAtSegments(road(new Point(0, 0)), new HashSet<>());
		assertEquals(0, result.size());
	}

	@Test
	public void splitAtSegments_sharedJunctionLocationDoesNotSplitOtherPath()
	{
		// Regression: erasing a segment of one river/road must not split another river/road that merely shares an endpoint location.
		// Repro: River A = [P0,P1,P2]; River B = [P1,Q0,Q1] branches off P1; erasing (P1,Q0) used to split River A at P1 too because
		// splitAtLocations matched any node whose location appeared in the removed-segment endpoints.
		Point p0 = new Point(0, 0);
		Point p1 = new Point(1, 0);
		Point p2 = new Point(2, 0);
		Point q0 = new Point(1, 1);

		Set<OrderlessPair<Point>> removed = new HashSet<>();
		removed.add(new OrderlessPair<>(p1, q0));

		List<List<RoadPathNode>> result = PathOperations.splitAtSegments(road(p0, p1, p2), removed);
		assertEquals(1, result.size());
		assertEquals(Arrays.asList(p0, p1, p2), PathOperations.toLocationList(result.get(0)));
	}

	@Test
	public void splitAtSegments_matchesReverseDirection()
	{
		// Segment matching is orderless so a removed (a,b) erases the path's b→a step just as it erases a→b.
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		Point c = new Point(2, 0);

		Set<OrderlessPair<Point>> removed = new HashSet<>();
		removed.add(new OrderlessPair<>(b, a));

		List<List<RoadPathNode>> result = PathOperations.splitAtSegments(road(a, b, c), removed);
		// a→b removed; b is the new start of the surviving sub-path.
		assertEquals(1, result.size());
		assertEquals(Arrays.asList(b, c), PathOperations.toLocationList(result.get(0)));
	}

	@Test
	public void collectAllConnections_collectsOrderlessPairs()
	{
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		Point c = new Point(2, 0);
		Set<OrderlessPair<Point>> result = PathOperations.collectAllConnections(Arrays.asList(road(a, b, c)));
		assertEquals(2, result.size());
		assertTrue(result.contains(new OrderlessPair<>(a, b)));
		assertTrue(result.contains(new OrderlessPair<>(b, c)));
	}

	@Test
	public void collectAllConnections_treatsReverseAsSame()
	{
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		Set<OrderlessPair<Point>> result = PathOperations.collectAllConnections(Arrays.asList(road(a, b), road(b, a)));
		// OrderlessPair means {a,b} == {b,a}, so deduplicated to one entry.
		assertEquals(1, result.size());
	}

	@Test
	public void reverseWithMetadata_roadReversesLocations()
	{
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		Point c = new Point(2, 0);
		List<RoadPathNode> reversed = PathOperations.reverseWithMetadata(road(a, b, c), RoadDrawer.ROAD_OPS);
		assertEquals(Arrays.asList(c, b, a), PathOperations.toLocationList(reversed));
	}

	@Test
	public void tryConnectToExistingPath_endpointMatchAppends()
	{
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		Point c = new Point(2, 0);
		Point d = new Point(3, 0);
		List<RoadPathNode> existing = road(a, b, c);
		List<RoadPathNode> toAdd = road(c, d);
		PathOperations.Match<RoadPathNode> match = PathOperations.tryConnectToExistingPath(toAdd, singlePathAccessor(existing), RoadDrawer.ROAD_OPS);
		assertNotNull(match);
		assertEquals(Arrays.asList(a, b, c, d), PathOperations.toLocationList(match.mergedNodes));
	}

	@Test
	public void tryConnectToExistingPath_endpointMatchPrepends()
	{
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		Point c = new Point(2, 0);
		Point d = new Point(3, 0);
		List<RoadPathNode> existing = road(c, d);
		List<RoadPathNode> toAdd = road(a, b, c);
		PathOperations.Match<RoadPathNode> match = PathOperations.tryConnectToExistingPath(toAdd, singlePathAccessor(existing), RoadDrawer.ROAD_OPS);
		assertNotNull(match);
		assertEquals(Arrays.asList(a, b, c, d), PathOperations.toLocationList(match.mergedNodes));
	}

	@Test
	public void tryConnectToExistingPath_noMatchReturnsNull()
	{
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		Point c = new Point(5, 5);
		Point d = new Point(6, 6);
		List<RoadPathNode> existing = road(a, b);
		List<RoadPathNode> toAdd = road(c, d);
		assertNull(PathOperations.tryConnectToExistingPath(toAdd, singlePathAccessor(existing), RoadDrawer.ROAD_OPS));
	}

	@Test
	public void tryConnectToExistingPath_shortPathReturnsNull()
	{
		Point a = new Point(0, 0);
		assertNull(PathOperations.tryConnectToExistingPath(road(a), singlePathAccessor(road(new Point(1, 1), new Point(2, 2))), RoadDrawer.ROAD_OPS));
	}

	private static <T extends nortantis.editor.PathNode> PathOperations.ExistingPathAccessor<T> singlePathAccessor(List<T> path)
	{
		return new PathOperations.ExistingPathAccessor<>()
		{
			@Override
			public int count()
			{
				return 1;
			}

			@Override
			public List<T> get()
			{
				return path;
			}
		};
	}

	@Test
	public void tryConnectToExistingPath_reverseAndAppendMatch()
	{
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		Point c = new Point(2, 0);
		// existing: a-b-c, toAdd: d-c (end-end match → reverse-and-append).
		Point d = new Point(3, 0);
		List<RoadPathNode> existing = road(a, b, c);
		List<RoadPathNode> toAdd = road(d, c);
		PathOperations.Match<RoadPathNode> match = PathOperations.tryConnectToExistingPath(toAdd, singlePathAccessor(existing), RoadDrawer.ROAD_OPS);
		assertNotNull(match);
		assertEquals(Arrays.asList(a, b, c, d), PathOperations.toLocationList(match.mergedNodes));
	}

	@Test
	public void splitAtSegments_segmentNotInPathLeavesPathUnchanged()
	{
		// A removed segment whose endpoints don't form a consecutive pair in the path has no effect.
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		Point c = new Point(2, 0);
		Point d = new Point(9, 9);

		Set<OrderlessPair<Point>> removed = new HashSet<>();
		removed.add(new OrderlessPair<>(a, d));

		List<List<RoadPathNode>> result = PathOperations.splitAtSegments(road(a, b, c), removed);
		assertEquals(1, result.size());
		assertEquals(Arrays.asList(a, b, c), PathOperations.toLocationList(result.get(0)));
	}

	@Test
	public void deduplicateConsecutive_singleNode()
	{
		Point a = new Point(0, 0);
		List<RoadPathNode> result = PathOperations.deduplicateConsecutive(road(a));
		assertEquals(Arrays.asList(a), PathOperations.toLocationList(result));
	}

	@Test
	public void findInnerNeighborsOfCutEndpoints_middleCutFindsBothInnerNeighbors()
	{
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		Point c = new Point(2, 0);
		Point d = new Point(3, 0);
		Point e = new Point(4, 0);
		// Simulate post-split state for [a,b,c,d,e] cut at c→d → [a,b,c] and [d,e].
		List<List<RoadPathNode>> paths = Arrays.asList(road(a, b, c), road(d, e));
		List<List<Point>> removed = Arrays.asList(Arrays.asList(c, d));
		Set<Point> result = new HashSet<>(PathOperations.findInnerNeighborsOfCutEndpoints(paths, removed));
		// Inner neighbor of c (new end of first sub-path) is b; inner neighbor of d (new end of second sub-path) is e.
		assertEquals(new HashSet<>(Arrays.asList(b, e)), result);
	}

	@Test
	public void findInnerNeighborsOfCutEndpoints_ignoresOriginalEndpoints()
	{
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		Point c = new Point(2, 0);
		// Original [a,b,c] cut at a→b → [b,c]. Only b is a new endpoint matching a cut point;
		// the cut also names a (already an original endpoint), but a is no longer in the path.
		List<List<RoadPathNode>> paths = Arrays.asList(road(b, c));
		List<List<Point>> removed = Arrays.asList(Arrays.asList(a, b));
		Set<Point> result = new HashSet<>(PathOperations.findInnerNeighborsOfCutEndpoints(paths, removed));
		// b's inner neighbor is c. c is not a cut point, so its inner neighbor is not requested.
		assertEquals(new HashSet<>(Arrays.asList(c)), result);
	}

	@Test
	public void findInnerNeighborsOfCutEndpoints_handlesTwoNodeRemainder()
	{
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		Point c = new Point(2, 0);
		// Post-cut state: [a,b]. b is the new endpoint from removing b→c.
		List<List<RoadPathNode>> paths = Arrays.asList(road(a, b));
		List<List<Point>> removed = Arrays.asList(Arrays.asList(b, c));
		Set<Point> result = new HashSet<>(PathOperations.findInnerNeighborsOfCutEndpoints(paths, removed));
		assertEquals(new HashSet<>(Arrays.asList(a)), result);
	}

	@Test
	public void findInnerNeighborsOfCutEndpoints_deduplicates()
	{
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		Point c = new Point(2, 0);
		// Two paths both ending at b — should report a only once.
		List<List<RoadPathNode>> paths = Arrays.asList(road(a, b), road(a, b, c));
		List<List<Point>> removed = Arrays.asList(Arrays.asList(b, new Point(99, 99)));
		Set<Point> result = new HashSet<>(PathOperations.findInnerNeighborsOfCutEndpoints(paths, removed));
		// First path: b at end → inner is a. Second path: c at end (not in cut points); b at index 1 (not endpoint).
		assertEquals(new HashSet<>(Arrays.asList(a)), result);
	}

	@Test
	public void findInnerNeighborsOfCutEndpoints_skipsShortPaths()
	{
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		List<List<RoadPathNode>> paths = Arrays.asList(Collections.<RoadPathNode>emptyList(), road(a));
		List<List<Point>> removed = Arrays.asList(Arrays.asList(a, b));
		// Empty and single-node paths must be ignored rather than throwing on out-of-bounds access.
		assertTrue(PathOperations.findInnerNeighborsOfCutEndpoints(paths, removed).isEmpty());
	}

	@Test
	public void findInnerNeighborsOfCutEndpoints_emptyInputsReturnEmpty()
	{
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		List<List<RoadPathNode>> paths = Arrays.asList(road(a, b));
		List<List<Point>> removed = Arrays.asList(Arrays.asList(a, b));
		assertTrue(PathOperations.findInnerNeighborsOfCutEndpoints(null, removed).isEmpty());
		assertTrue(PathOperations.findInnerNeighborsOfCutEndpoints(paths, null).isEmpty());
		assertTrue(PathOperations.findInnerNeighborsOfCutEndpoints(paths, Collections.emptyList()).isEmpty());
	}

	@Test
	public void deduplicateConsecutive_returnsSameOnNoDuplicates()
	{
		Point a = new Point(0, 0);
		Point b = new Point(1, 0);
		List<RoadPathNode> path = road(a, b);
		List<RoadPathNode> result = PathOperations.deduplicateConsecutive(path);
		// Same content (deduplicateConsecutive does not necessarily return the same list instance, just same data).
		assertEquals(Arrays.asList(a, b), PathOperations.toLocationList(result));
		assertNotNull(result);
		assertSame(result.get(0), path.get(0));
	}

	@Test
	public void catmullRomPropagationRadius_isTwo()
	{
		// A change to a single uniform Catmull-Rom control point can shift the curve shape on at most the four segments whose endpoints
		// lie within 2 CPs of the edit. If this constant ever changes, the redraw scoping in LandWaterTool needs to be re-derived.
		assertEquals(2, PathOperations.CATMULL_ROM_PROPAGATION_RADIUS);
	}

	@Test
	public void nodeLocationsAround_centeredSliceFullyInsidePath()
	{
		Point p0 = new Point(0, 0);
		Point p1 = new Point(1, 0);
		Point p2 = new Point(2, 0);
		Point p3 = new Point(3, 0);
		Point p4 = new Point(4, 0);
		Point p5 = new Point(5, 0);
		Point p6 = new Point(6, 0);
		List<RoadPathNode> path = road(p0, p1, p2, p3, p4, p5, p6);

		List<Point> result = PathOperations.nodeLocationsAround(path, 3, 2);

		assertEquals(Arrays.asList(p1, p2, p3, p4, p5), result);
	}

	@Test
	public void nodeLocationsAround_clampsAtStart()
	{
		Point p0 = new Point(0, 0);
		Point p1 = new Point(1, 0);
		Point p2 = new Point(2, 0);
		Point p3 = new Point(3, 0);
		List<RoadPathNode> path = road(p0, p1, p2, p3);

		// Index 0 with radius 2: the window is [-2, 2] which clamps to [0, 2].
		List<Point> result = PathOperations.nodeLocationsAround(path, 0, 2);

		assertEquals(Arrays.asList(p0, p1, p2), result);
	}

	@Test
	public void nodeLocationsAround_clampsAtEnd()
	{
		Point p0 = new Point(0, 0);
		Point p1 = new Point(1, 0);
		Point p2 = new Point(2, 0);
		Point p3 = new Point(3, 0);
		List<RoadPathNode> path = road(p0, p1, p2, p3);

		// Last index (3) with radius 2: the window is [1, 5] which clamps to [1, 3].
		List<Point> result = PathOperations.nodeLocationsAround(path, 3, 2);

		assertEquals(Arrays.asList(p1, p2, p3), result);
	}

	@Test
	public void nodeLocationsAround_radiusZeroReturnsSingleNode()
	{
		Point p0 = new Point(0, 0);
		Point p1 = new Point(1, 0);
		Point p2 = new Point(2, 0);
		List<RoadPathNode> path = road(p0, p1, p2);

		assertEquals(Collections.singletonList(p1), PathOperations.nodeLocationsAround(path, 1, 0));
	}

	@Test
	public void nodeLocationsAround_radiusLargerThanPathReturnsWholePath()
	{
		Point p0 = new Point(0, 0);
		Point p1 = new Point(1, 0);
		Point p2 = new Point(2, 0);
		List<RoadPathNode> path = road(p0, p1, p2);

		// Radius 5 around index 1 in a 3-node path should fully clamp to the entire path.
		assertEquals(Arrays.asList(p0, p1, p2), PathOperations.nodeLocationsAround(path, 1, 5));
	}

	@Test
	public void nodeLocationsAround_indexOutOfRangeStillClampsToOverlap()
	{
		Point p0 = new Point(0, 0);
		Point p1 = new Point(1, 0);
		Point p2 = new Point(2, 0);
		Point p3 = new Point(3, 0);
		List<RoadPathNode> path = road(p0, p1, p2, p3);

		// Index past the end but radius reaches back into the path: window [2, 6] ∩ [0, 3] = [2, 3].
		assertEquals(Arrays.asList(p2, p3), PathOperations.nodeLocationsAround(path, 4, 2));
		// Index before the start but radius reaches forward into the path: window [-3, 1] ∩ [0, 3] = [0, 1].
		assertEquals(Arrays.asList(p0, p1), PathOperations.nodeLocationsAround(path, -1, 2));
	}

	@Test
	public void nodeLocationsAround_indexFarOutsidePathReturnsEmpty()
	{
		Point p0 = new Point(0, 0);
		Point p1 = new Point(1, 0);
		List<RoadPathNode> path = road(p0, p1);

		// Window [8, 12] doesn't overlap [0, 1].
		assertTrue(PathOperations.nodeLocationsAround(path, 10, 2).isEmpty());
		// Window [-12, -8] doesn't overlap [0, 1] either.
		assertTrue(PathOperations.nodeLocationsAround(path, -10, 2).isEmpty());
	}

	@Test
	public void nodeLocationsAround_emptyAndNullPathsReturnEmpty()
	{
		assertTrue(PathOperations.nodeLocationsAround(null, 0, 2).isEmpty());
		assertTrue(PathOperations.nodeLocationsAround(Collections.<RoadPathNode>emptyList(), 0, 2).isEmpty());
	}

	@Test
	public void pointsAround_centeredSliceFullyInsidePath()
	{
		Point p0 = new Point(0, 0);
		Point p1 = new Point(1, 0);
		Point p2 = new Point(2, 0);
		Point p3 = new Point(3, 0);
		Point p4 = new Point(4, 0);
		Point p5 = new Point(5, 0);
		List<Point> points = Arrays.asList(p0, p1, p2, p3, p4, p5);

		assertEquals(Arrays.asList(p1, p2, p3, p4, p5), PathOperations.pointsAround(points, 3, 2));
	}

	@Test
	public void pointsAround_clampsAtBothEnds()
	{
		Point p0 = new Point(0, 0);
		Point p1 = new Point(1, 0);
		Point p2 = new Point(2, 0);
		List<Point> points = Arrays.asList(p0, p1, p2);

		assertEquals(Arrays.asList(p0, p1, p2), PathOperations.pointsAround(points, 0, 5));
		assertEquals(Arrays.asList(p0, p1, p2), PathOperations.pointsAround(points, 2, 5));
	}

	@Test
	public void pointsAround_emptyAndNullReturnEmpty()
	{
		assertTrue(PathOperations.pointsAround(null, 0, 2).isEmpty());
		assertTrue(PathOperations.pointsAround(Collections.<Point>emptyList(), 0, 2).isEmpty());
	}

	@Test
	public void pointsAround_andNodeLocationsAround_agreeForSamePath()
	{
		// The two overloads serve the same purpose; they should produce identical slices for the same logical path and index.
		Point p0 = new Point(0, 0);
		Point p1 = new Point(1, 0);
		Point p2 = new Point(2, 0);
		Point p3 = new Point(3, 0);
		Point p4 = new Point(4, 0);
		List<RoadPathNode> nodes = road(p0, p1, p2, p3, p4);
		List<Point> points = PathOperations.toLocationList(nodes);

		for (int idx = -1; idx <= nodes.size(); idx++)
		{
			assertEquals(PathOperations.nodeLocationsAround(nodes, idx, PathOperations.CATMULL_ROM_PROPAGATION_RADIUS),
					PathOperations.pointsAround(points, idx, PathOperations.CATMULL_ROM_PROPAGATION_RADIUS), "slices differ at index " + idx);
		}
	}
}
