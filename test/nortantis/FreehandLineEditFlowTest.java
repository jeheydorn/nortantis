package nortantis;

import nortantis.editor.River;
import nortantis.editor.RiverPathNode;
import nortantis.editor.Road;
import nortantis.editor.RoadPathNode;
import nortantis.geom.Point;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage of the control-point delete flow that {@code LandWaterTool.deleteSelectedCPs} runs, using the real static pipeline
 * ({@link PathOperations#applySelectionDeletes}, {@link PathOperations#normalizePath}, {@link RiverDrawer#mergeAdjacentRivers}). Reproduces
 * the reported bugs where deleting the control points of a freehand loop drawn A -> B -> C -> A (stored as {@code [A, B, C, A]} with a
 * duplicate CP at A) left a doubled A-B line and, eventually, a hit-testable phantom control point connected to nothing.
 *
 * <p>
 * The two short helpers below mirror {@code LandWaterTool.computeDeletePlan} and {@code applyRiverFragments}/{@code applyRoadFragments} (both
 * private and GUI-bound); the actual degeneracy-removal being verified lives in the real static methods they call.
 */
public class FreehandLineEditFlowTest
{
	private static final Point A = new Point(0, 0);
	private static final Point B = new Point(10, 0);
	private static final Point C = new Point(5, 8);

	// Mirrors LandWaterTool.computeDeletePlan: consecutive selected indices drop the segment between them; a selected index with no
	// selected neighbor is an isolated node to drop with a stitch.
	private static void classify(Set<Integer> selected, int nodeCount, Set<Integer> isolatedOut, Set<Integer> edgesOut)
	{
		for (int idx : selected)
		{
			if (idx < 0 || idx >= nodeCount)
			{
				continue;
			}
			boolean prevSelected = selected.contains(idx - 1);
			boolean nextSelected = selected.contains(idx + 1);
			if (nextSelected && idx + 1 < nodeCount)
			{
				edgesOut.add(idx);
			}
			if (!prevSelected && !nextSelected)
			{
				isolatedOut.add(idx);
			}
		}
	}

	// Mirrors LandWaterTool.deleteSelectedCPs for a single selected river: classify -> applySelectionDeletes -> normalize/drop
	// (applyRiverFragments) -> mergeAdjacentRivers.
	private static void deleteRiverCPs(River river, Set<Integer> selected, List<River> rivers)
	{
		Set<Integer> isolated = new HashSet<>();
		Set<Integer> edges = new HashSet<>();
		classify(selected, river.nodes.size(), isolated, edges);

		List<List<RiverPathNode>> fragments = PathOperations.applySelectionDeletes(river.nodes, isolated, edges, RiverDrawer.RIVER_OPS);
		List<List<RiverPathNode>> cleaned = new ArrayList<>();
		for (List<RiverPathNode> fragment : fragments)
		{
			List<RiverPathNode> normalized = PathOperations.normalizePath(fragment, RiverDrawer.RIVER_OPS);
			if (normalized.size() >= 2)
			{
				cleaned.add(normalized);
			}
		}
		List<River> changed = new ArrayList<>();
		if (cleaned.isEmpty())
		{
			rivers.remove(river);
		}
		else
		{
			river.nodes = new CopyOnWriteArrayList<>(cleaned.get(0));
			changed.add(river);
			for (int i = 1; i < cleaned.size(); i++)
			{
				River newRiver = new River(cleaned.get(i));
				rivers.add(newRiver);
				changed.add(newRiver);
			}
		}
		RiverDrawer.mergeAdjacentRivers(changed, rivers);
	}

	private static void deleteRoadCPs(Road road, Set<Integer> selected, List<Road> roads)
	{
		Set<Integer> isolated = new HashSet<>();
		Set<Integer> edges = new HashSet<>();
		classify(selected, road.nodes.size(), isolated, edges);

		List<List<RoadPathNode>> fragments = PathOperations.applySelectionDeletes(road.nodes, isolated, edges, RoadDrawer.ROAD_OPS);
		List<List<RoadPathNode>> cleaned = new ArrayList<>();
		for (List<RoadPathNode> fragment : fragments)
		{
			List<RoadPathNode> normalized = PathOperations.normalizePath(fragment, RoadDrawer.ROAD_OPS);
			if (normalized.size() >= 2)
			{
				cleaned.add(normalized);
			}
		}
		List<Road> changed = new ArrayList<>();
		if (cleaned.isEmpty())
		{
			roads.remove(road);
		}
		else
		{
			road.nodes = new CopyOnWriteArrayList<>(cleaned.get(0));
			changed.add(road);
			for (int i = 1; i < cleaned.size(); i++)
			{
				Road newRoad = new Road(cleaned.get(i));
				roads.add(newRoad);
				changed.add(newRoad);
			}
		}
		RoadDrawer.mergeAdjacentRoads(changed, roads);
	}

	private static River loopRiver()
	{
		List<RiverPathNode> nodes = List.of(new RiverPathNode(A, 4, 1L), new RiverPathNode(B, 4, 2L), new RiverPathNode(C, 4, 3L), new RiverPathNode(A, 0, 0L));
		return new River(nodes);
	}

	private static List<Point> locations(River river)
	{
		return PathOperations.toLocationList(river.nodes);
	}

	@Test
	public void deletingInteriorCPOfLoopLeavesSingleLineNotDoubleLine()
	{
		River river = loopRiver();
		List<River> rivers = new ArrayList<>(List.of(river));

		// Delete C (index 2). Before the fix this stitched into [A, B, A] - a doubled A-B line.
		deleteRiverCPs(river, new HashSet<>(Set.of(2)), rivers);

		assertEquals(1, rivers.size());
		assertEquals(List.of(A, B), locations(rivers.get(0)));
	}

	@Test
	public void deletingAllLoopCPsLeavesNoPhantom()
	{
		River river = loopRiver();
		List<River> rivers = new ArrayList<>(List.of(river));

		deleteRiverCPs(river, new HashSet<>(Set.of(2)), rivers); // delete C -> [A, B]
		deleteRiverCPs(river, new HashSet<>(Set.of(1)), rivers); // delete B -> river removed

		// No lone control point left behind (the phantom bug).
		assertTrue(rivers.isEmpty(), "Expected the river to be fully removed, but a phantom remained: " + rivers);
	}

	@Test
	public void deletingInteriorCPOfLoopWorksForRoadsToo()
	{
		Road road = new Road(List.of(new RoadPathNode(A), new RoadPathNode(B), new RoadPathNode(C), new RoadPathNode(A)));
		List<Road> roads = new ArrayList<>(List.of(road));

		deleteRoadCPs(road, new HashSet<>(Set.of(2)), roads); // delete C
		assertEquals(1, roads.size());
		assertEquals(List.of(A, B), PathOperations.toLocationList(roads.get(0).nodes));

		deleteRoadCPs(road, new HashSet<>(Set.of(1)), roads); // delete B -> removed
		assertTrue(roads.isEmpty(), "Expected the road to be fully removed, but a phantom remained: " + roads);
	}

	@Test
	public void deletingBFirstAlsoAvoidsDoubleLine()
	{
		// The user noted the interior CP could be B instead of C. Deleting B from [A, B, C, A] stitches to [A, C, A] which must
		// collapse to a single A-C line, not a doubled one.
		River river = loopRiver();
		List<River> rivers = new ArrayList<>(List.of(river));

		deleteRiverCPs(river, new HashSet<>(Set.of(1)), rivers);

		assertEquals(1, rivers.size());
		assertEquals(List.of(A, C), locations(rivers.get(0)));
	}
}
