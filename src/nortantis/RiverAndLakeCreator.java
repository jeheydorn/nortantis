package nortantis;

import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * Creates the rivers and lakes of a newly generated map from its terrain.
 * <p>
 * Rain falls on land, flows downhill across corners, and collects in basins. Each basin holds a lake whose area grows until evaporation from
 * its surface balances its inflow. A basin that fills to its rim before that happens overflows at the lowest point of its rim, and a basin
 * that receives well more than it needs to overflow carves that outlet down, which shrinks its lake. Nested basins are handled by a
 * depression hierarchy: two neighboring basins that both fill to the pass between them become one basin.
 * </p>
 * <p>
 * The results are stored in the graph: {@code Edge.river} and {@code Corner.river} hold the flow along each river, new lake centers are
 * marked as water, and carving lowers corner elevations along carved channels.
 * </p>
 */
class RiverAndLakeCreator
{
	/**
	 * The probability that a land corner contributes one unit of rain to the flow. This controls how many rivers there are.
	 */
	private static final double rainProbability = 1.0 / 14.0;

	/**
	 * The flow that one center of lake surface evaporates. A lake grows until evaporation balances its inflow.
	 */
	static final double evaporationPerLakeCenter = 2.0;

	/**
	 * The fewest centers a new lake may cover. A basin too small to hold a lake this big is treated as filled in, so water passes through
	 * it, and a basin whose inflow can't sustain a lake this big dries up.
	 */
	static final int minLakeSize = 4;

	/**
	 * The most centers a lake may cover, as a fraction of the number of centers in the map, so that lakes look about the same size at any
	 * world size. Lakes are also never bigger than {@link WorldGraph#maxLakeSize}, beyond which water is treated as ocean.
	 */
	private static final double maxLakeSizeAsFractionOfCenters = 0.003;

	/**
	 * The lower limit on the most centers a lake may cover, for small maps.
	 */
	private static final int minMaxLakeSize = 10;

	/**
	 * A basin carves its outlet only when its inflow is at least this many times what it needs to overflow.
	 */
	private static final double carveInflowFactor = 2.0;

	/**
	 * A basin carves its outlet only when its outflow is at least this much, so that small streams never carve.
	 */
	private static final double minOutflowToCarve = 15.0;

	/**
	 * Scales how deep an outlet is carved, relative to the square root of the outflow.
	 */
	private static final double carveDepthScale = 0.006;

	/**
	 * Carving changes which basins exist and how much water reaches them, so the basins are re-evaluated after each round of carving, up to
	 * this many times.
	 */
	private static final int maxCarveRounds = 4;

	/**
	 * How much lower each corner of a carved channel is than the one before it, as a fraction of its elevation, so the channel runs
	 * downhill.
	 */
	private static final double channelSlope = 0.0001;

	private final WorldGraph graph;
	/**
	 * The most centers a lake may cover.
	 */
	private final int maxLakeSize;
	private final double[] rain;
	private final boolean[] isCarved;

	/**
	 * Index of the water body each center belongs to, or -1 for land. Recomputed when lakes are added.
	 */
	private int[] waterBodyOfCenter;
	private List<WaterBody> waterBodies;

	RiverAndLakeCreator(WorldGraph graph, Random rand)
	{
		this.graph = graph;
		maxLakeSize = getMaxLakeSize(graph.centers.size());
		findWaterBodies();

		rain = new double[graph.corners.size()];
		for (Corner corner : graph.corners)
		{
			if (rand.nextDouble() < rainProbability && waterBodyOfCorner(corner) == null)
			{
				rain[corner.index] = 1.0;
			}
		}
		isCarved = new boolean[graph.corners.size()];
	}

	/**
	 * The most centers a new lake may cover in a map with the given number of centers.
	 */
	static int getMaxLakeSize(int centerCount)
	{
		return Math.min(WorldGraph.maxLakeSize, Math.max(minMaxLakeSize, (int) Math.round(centerCount * maxLakeSizeAsFractionOfCenters)));
	}

	void createRiversAndLakes()
	{
		BasinResult result = null;
		for (int round = 0; round <= maxCarveRounds; round++)
		{
			DepressionHierarchy hierarchy = new DepressionHierarchy();
			result = hierarchy.fillBasins();
			if (round == maxCarveRounds || !carveOutlets(hierarchy, result))
			{
				break;
			}
		}

		addLakes(result.lakes);
		routeAndAccumulateFlow(result);
	}

	// ---------------------------------------------------------------------------------------------------------------------------------
	// Water bodies
	// ---------------------------------------------------------------------------------------------------------------------------------

	private static class WaterBody
	{
		final List<Center> centers = new ArrayList<>();
		/**
		 * True for oceans and other water that rivers end in and that never overflows: water touching the map border, or too big to be a
		 * lake.
		 */
		boolean isTerminal;
		/**
		 * For lakes, the elevation of the lake's surface.
		 */
		double level = WorldGraph.seaLevel;
		/**
		 * For lakes, whether water flows out of the lake.
		 */
		boolean overflows;
		double evaporation;
	}

	private void findWaterBodies()
	{
		waterBodyOfCenter = new int[graph.centers.size()];
		Arrays.fill(waterBodyOfCenter, -1);
		waterBodies = new ArrayList<>();
		for (Center start : graph.centers)
		{
			if (!start.isWater || waterBodyOfCenter[start.index] != -1)
			{
				continue;
			}
			WaterBody body = new WaterBody();
			int bodyIndex = waterBodies.size();
			waterBodies.add(body);
			Deque<Center> queue = new ArrayDeque<>();
			queue.add(start);
			waterBodyOfCenter[start.index] = bodyIndex;
			while (!queue.isEmpty())
			{
				Center center = queue.poll();
				body.centers.add(center);
				for (Center neighbor : center.neighbors)
				{
					if (neighbor.isWater && waterBodyOfCenter[neighbor.index] == -1)
					{
						waterBodyOfCenter[neighbor.index] = bodyIndex;
						queue.add(neighbor);
					}
				}
			}
			body.isTerminal = body.centers.size() > WorldGraph.maxLakeSize || body.centers.stream().anyMatch(c -> c.isBorder);
		}
	}

	/**
	 * Returns the water body a corner touches, or null if it touches only land. A corner touches at most one water body, since the centers
	 * around a corner are all neighbors of each other.
	 */
	private WaterBody waterBodyOfCorner(Corner corner)
	{
		int bodyIndex = waterBodyIndexOfCorner(corner);
		return bodyIndex == -1 ? null : waterBodies.get(bodyIndex);
	}

	private int waterBodyIndexOfCorner(Corner corner)
	{
		for (Center center : corner.touches)
		{
			if (waterBodyOfCenter[center.index] != -1)
			{
				return waterBodyOfCenter[center.index];
			}
		}
		return -1;
	}

	// ---------------------------------------------------------------------------------------------------------------------------------
	// Depression hierarchy
	// ---------------------------------------------------------------------------------------------------------------------------------

	/**
	 * A basin in the depression hierarchy. A leaf is the area that drains to one low point: a land corner with no lower neighbor, or an
	 * existing lake. Two basins that share their lowest pass are joined by a parent basin that starts at the elevation of that pass.
	 */
	private static class Basin
	{
		Basin child1, child2, parent;
		/**
		 * For a leaf: the land corner water collects at, or null for an existing lake or the ocean.
		 */
		Corner pit;
		/**
		 * For a leaf: the existing lake it holds, or null.
		 */
		WaterBody existingLake;
		/**
		 * The lowest elevation in the basin.
		 */
		double floor;
		/**
		 * The elevation of the pass where this basin either joins its sibling or spills into a basin that drains away. Infinite for a basin
		 * with no way out.
		 */
		double top = Double.POSITIVE_INFINITY;
		/**
		 * The corners on either side of the pass at {@link #top}. The outlet is on this basin's side.
		 */
		Corner outlet, receiver;
		/**
		 * True if this basin spills over {@link #top} into a basin that drains away, rather than joining its sibling.
		 */
		boolean spillsIntoDrainedBasin;
		int spillOrder;
		double rainInflow;
		double extraInflow;
		/**
		 * The range of leaf positions (in depth-first order) that this basin's leaves occupy, used to test whether one basin contains
		 * another.
		 */
		int leafRangeStart, leafRangeEnd;
		int depth;
		LakeGrowth lakeGrowth;

		boolean isLeaf()
		{
			return child1 == null;
		}

		boolean contains(Basin other)
		{
			return leafRangeStart <= other.leafRangeStart && other.leafRangeEnd <= leafRangeEnd;
		}

		double inflow()
		{
			return rainInflow + extraInflow;
		}
	}

	/**
	 * The centers a basin's lake covers as its water level rises, in the order they are covered.
	 */
	private static class LakeGrowth
	{
		final List<Center> centers = new ArrayList<>();
		/**
		 * The lake level needed to cover each center in {@link #centers}.
		 */
		final List<Double> levels = new ArrayList<>();
		/**
		 * The number of centers at the start of {@link #centers} that are already water.
		 */
		int existingLakeCenterCount;
		/**
		 * The most centers the lake may cover. A lake never shrinks below the lakes that already exist in its basin.
		 */
		int maxSize;

		boolean exceedsMaxSize()
		{
			return centers.size() > maxSize;
		}

		/**
		 * The number of centers the lake covers when filled to its basin's top, or zero if the basin is too small to hold a lake.
		 */
		int areaAtTopWithinMaxSize()
		{
			return canHoldLakeOfSize(centers.size()) ? Math.min(centers.size(), maxSize) : 0;
		}

		boolean canHoldLakeOfSize(int area)
		{
			return existingLakeCenterCount > 0 || (area >= minLakeSize && centers.size() >= minLakeSize);
		}
	}

	/**
	 * A lake chosen by filling basins.
	 */
	private static class Lake
	{
		Basin basin;
		List<Center> centers;
		double level;
		boolean overflows;
		double inflow;
		/**
		 * The inflow the lake needs to just overflow.
		 */
		double inflowToOverflow;
		/**
		 * True if the lake would be bigger than the maximum lake size, and so must carve its outlet.
		 */
		boolean exceedsMaxLakeSize;
		/**
		 * If {@link #exceedsMaxLakeSize}, the highest lake level at which the lake is within the maximum lake size.
		 */
		double maxLevelWithinMaxLakeSize;
	}

	private static class BasinResult
	{
		final List<Lake> lakes = new ArrayList<>();
		/**
		 * Land corners at the bottom of basins where every drop of inflow evaporates without making a lake.
		 */
		final List<Corner> dryPits = new ArrayList<>();
	}

	private class DepressionHierarchy
	{
		final Basin[] leafOfCorner = new Basin[graph.corners.size()];
		/**
		 * The lowest neighbor of each land corner, if that neighbor is lower. Null for corners that touch water and corners with no lower
		 * neighbor.
		 */
		final Corner[] downhill = new Corner[graph.corners.size()];
		final Basin[] homeBasinOfCenter = new Basin[graph.centers.size()];
		final List<Basin> basins = new ArrayList<>();
		final List<Basin> leavesInOrder = new ArrayList<>();
		final Basin ocean;
		final List<Basin> topBasins = new ArrayList<>();

		DepressionHierarchy()
		{
			ocean = new Basin();
			ocean.floor = WorldGraph.seaLevel;
			ocean.top = Double.NEGATIVE_INFINITY;
			basins.add(ocean);

			createLeaves();
			joinBasinsAtPasses();
			numberLeaves();
			findHomeBasinsOfCenters();
			addUpRain();
		}

		private void createLeaves()
		{
			Basin[] leafOfWaterBody = new Basin[waterBodies.size()];
			for (Corner corner : graph.corners)
			{
				int bodyIndex = waterBodyIndexOfCorner(corner);
				if (bodyIndex == -1)
				{
					downhill[corner.index] = findLowestLowerNeighbor(corner);
				}
				else if (waterBodies.get(bodyIndex).isTerminal)
				{
					leafOfCorner[corner.index] = ocean;
				}
				else
				{
					if (leafOfWaterBody[bodyIndex] == null)
					{
						Basin leaf = new Basin();
						leaf.existingLake = waterBodies.get(bodyIndex);
						leaf.floor = WorldGraph.seaLevel;
						basins.add(leaf);
						leafOfWaterBody[bodyIndex] = leaf;
					}
					leafOfCorner[corner.index] = leafOfWaterBody[bodyIndex];
				}
			}

			for (Corner corner : graph.corners)
			{
				if (leafOfCorner[corner.index] != null)
				{
					continue;
				}
				// Walk downhill to the corner's pit, then assign the pit's leaf to every corner along the way.
				List<Corner> path = new ArrayList<>();
				Corner current = corner;
				while (leafOfCorner[current.index] == null && downhill[current.index] != null)
				{
					path.add(current);
					current = downhill[current.index];
				}
				Basin leaf = leafOfCorner[current.index];
				if (leaf == null)
				{
					leaf = new Basin();
					leaf.pit = current;
					leaf.floor = current.elevation;
					basins.add(leaf);
					leafOfCorner[current.index] = leaf;
				}
				for (Corner onPath : path)
				{
					leafOfCorner[onPath.index] = leaf;
				}
			}
		}

		private Corner findLowestLowerNeighbor(Corner corner)
		{
			Corner lowest = null;
			for (Corner neighbor : corner.adjacent)
			{
				if (neighbor != corner && neighbor.elevation < corner.elevation && (lowest == null || isLower(neighbor, lowest)))
				{
					lowest = neighbor;
				}
			}
			return lowest;
		}

		private record Pass(Corner corner1, Corner corner2, double level)
		{
		}

		/**
		 * Builds the hierarchy by visiting the passes between leaves from lowest to highest. The first pass a basin reaches is its top. If
		 * the basin on the other side already drains away, the basin spills into it. Otherwise the two basins join.
		 */
		private void joinBasinsAtPasses()
		{
			List<Pass> passes = new ArrayList<>();
			for (Corner corner : graph.corners)
			{
				for (Corner neighbor : corner.adjacent)
				{
					if (neighbor.index > corner.index && leafOfCorner[neighbor.index] != leafOfCorner[corner.index])
					{
						passes.add(new Pass(corner, neighbor, Math.max(corner.elevation, neighbor.elevation)));
					}
				}
			}
			passes.sort(Comparator.comparingDouble(Pass::level).thenComparingInt(p -> p.corner1.index).thenComparingInt(p -> p.corner2.index));

			// Union-find over basins. Each set's root is the outermost basin formed so far from the set's leaves.
			int[] unionParent = new int[basins.size()];
			for (int i = 0; i < unionParent.length; i++)
			{
				unionParent[i] = i;
			}
			List<Basin> outermostBasinOfSet = new ArrayList<>(basins);
			boolean[] setDrains = new boolean[basins.size()];
			IdentityHashMap<Basin, Integer> basinIndexes = new IdentityHashMap<>();
			for (int i = 0; i < basins.size(); i++)
			{
				basinIndexes.put(basins.get(i), i);
			}
			setDrains[basinIndexes.get(ocean)] = true;

			int spillOrder = 0;
			for (Pass pass : passes)
			{
				int set1 = find(unionParent, basinIndexes.get(leafOfCorner[pass.corner1.index]));
				int set2 = find(unionParent, basinIndexes.get(leafOfCorner[pass.corner2.index]));
				if (set1 == set2 || (setDrains[set1] && setDrains[set2]))
				{
					continue;
				}

				if (setDrains[set1] || setDrains[set2])
				{
					boolean firstDrains = setDrains[set1];
					int undrainedSet = firstDrains ? set2 : set1;
					int drainedSet = firstDrains ? set1 : set2;
					Basin spilling = outermostBasinOfSet.get(undrainedSet);
					spilling.top = pass.level;
					spilling.outlet = firstDrains ? pass.corner2 : pass.corner1;
					spilling.receiver = firstDrains ? pass.corner1 : pass.corner2;
					spilling.spillsIntoDrainedBasin = true;
					spilling.spillOrder = spillOrder++;
					topBasins.add(spilling);
					unionParent[undrainedSet] = drainedSet;
				}
				else
				{
					Basin basin1 = outermostBasinOfSet.get(set1);
					Basin basin2 = outermostBasinOfSet.get(set2);
					basin1.top = pass.level;
					basin1.outlet = pass.corner1;
					basin1.receiver = pass.corner2;
					basin2.top = pass.level;
					basin2.outlet = pass.corner2;
					basin2.receiver = pass.corner1;

					Basin joined = new Basin();
					joined.child1 = basin1;
					joined.child2 = basin2;
					joined.floor = Math.min(basin1.floor, basin2.floor);
					basin1.parent = joined;
					basin2.parent = joined;
					basins.add(joined);
					unionParent[set2] = set1;
					outermostBasinOfSet.set(set1, joined);
				}
			}

			// Basins with no way out, which only happens when there is no ocean.
			for (int i = 0; i < unionParent.length; i++)
			{
				if (find(unionParent, i) == i && !setDrains[i])
				{
					Basin basin = outermostBasinOfSet.get(i);
					basin.spillOrder = spillOrder++;
					topBasins.add(basin);
				}
			}
		}

		private int find(int[] unionParent, int i)
		{
			while (unionParent[i] != i)
			{
				unionParent[i] = unionParent[unionParent[i]];
				i = unionParent[i];
			}
			return i;
		}

		private void numberLeaves()
		{
			for (Basin top : topBasins)
			{
				numberLeaves(top, 0);
			}
			ocean.leafRangeStart = leavesInOrder.size();
			leavesInOrder.add(ocean);
			ocean.leafRangeEnd = leavesInOrder.size();
		}

		private void numberLeaves(Basin root, int rootDepth)
		{
			// Iterative depth-first traversal, since the hierarchy can be deep.
			Deque<Basin> stack = new ArrayDeque<>();
			root.depth = rootDepth;
			stack.push(root);
			List<Basin> postOrder = new ArrayList<>();
			while (!stack.isEmpty())
			{
				Basin basin = stack.pop();
				postOrder.add(basin);
				if (basin.isLeaf())
				{
					basin.leafRangeStart = leavesInOrder.size();
					leavesInOrder.add(basin);
					basin.leafRangeEnd = leavesInOrder.size();
				}
				else
				{
					basin.child1.depth = basin.depth + 1;
					basin.child2.depth = basin.depth + 1;
					// Push child2 first so child1's leaves are numbered first.
					stack.push(basin.child2);
					stack.push(basin.child1);
				}
			}
			for (int i = postOrder.size() - 1; i >= 0; i--)
			{
				Basin basin = postOrder.get(i);
				if (!basin.isLeaf())
				{
					basin.leafRangeStart = basin.child1.leafRangeStart;
					basin.leafRangeEnd = basin.child2.leafRangeEnd;
				}
			}
		}

		/**
		 * A center can be part of a basin's lake only if all its corners are in that basin, which keeps lakes from touching the ocean or
		 * each other. A center's home basin is the smallest basin that contains all its corners.
		 */
		private void findHomeBasinsOfCenters()
		{
			for (Center center : graph.centers)
			{
				if (center.isBorder)
				{
					continue;
				}
				int bodyIndex = waterBodyOfCenter[center.index];
				if (bodyIndex != -1 && waterBodies.get(bodyIndex).isTerminal)
				{
					continue;
				}
				Basin home = null;
				for (Corner corner : center.corners)
				{
					Basin leaf = leafOfCorner[corner.index];
					if (leaf == ocean)
					{
						home = null;
						break;
					}
					home = home == null ? leaf : lowestCommonAncestor(home, leaf);
					if (home == null)
					{
						break;
					}
				}
				homeBasinOfCenter[center.index] = home;
			}
		}

		private Basin lowestCommonAncestor(Basin basin1, Basin basin2)
		{
			while (basin1 != null && basin2 != null && basin1 != basin2)
			{
				if (basin1.depth >= basin2.depth)
				{
					basin1 = basin1.parent;
				}
				else
				{
					basin2 = basin2.parent;
				}
			}
			return basin1 == basin2 ? basin1 : null;
		}

		private void addUpRain()
		{
			for (Corner corner : graph.corners)
			{
				if (rain[corner.index] > 0)
				{
					for (Basin basin = leafOfCorner[corner.index]; basin != null; basin = basin.parent)
					{
						basin.rainInflow += rain[corner.index];
					}
				}
			}
		}

		private boolean canHoldCenter(Basin basin, Center center)
		{
			Basin home = homeBasinOfCenter[center.index];
			return home != null && basin.contains(home);
		}

		/**
		 * Finds the centers a basin's lake covers as the water level rises from the basin's low points up to its top, stopping after one more
		 * than the maximum lake size.
		 */
		private LakeGrowth getLakeGrowth(Basin basin)
		{
			if (basin.lakeGrowth != null)
			{
				return basin.lakeGrowth;
			}

			LakeGrowth growth = new LakeGrowth();
			// Existing lake centers are always covered, so they sort first.
			PriorityQueue<Center> queue = new PriorityQueue<>(Comparator.comparingDouble((Center c) -> waterBodyOfCenter[c.index] != -1 ? Double.NEGATIVE_INFINITY : c.elevation)
					.thenComparingInt(c -> c.index));
			boolean[] queued = new boolean[graph.centers.size()];
			for (int i = basin.leafRangeStart; i < basin.leafRangeEnd; i++)
			{
				Basin leaf = leavesInOrder.get(i);
				if (leaf.existingLake != null)
				{
					for (Center center : leaf.existingLake.centers)
					{
						queued[center.index] = true;
						queue.add(center);
					}
				}
				else if (leaf.pit != null)
				{
					for (Center center : leaf.pit.touches)
					{
						if (!queued[center.index] && canHoldCenter(basin, center))
						{
							queued[center.index] = true;
							queue.add(center);
						}
					}
				}
			}

			double level = Double.NEGATIVE_INFINITY;
			while (!queue.isEmpty() && growth.centers.size() <= Math.max(maxLakeSize, growth.existingLakeCenterCount))
			{
				Center center = queue.poll();
				boolean isExisting = waterBodyOfCenter[center.index] != -1;
				level = Math.max(level, isExisting ? WorldGraph.seaLevel : center.elevation);
				if (!isExisting && level >= basin.top)
				{
					break;
				}
				growth.centers.add(center);
				growth.levels.add(level);
				if (isExisting)
				{
					growth.existingLakeCenterCount++;
				}
				for (Center neighbor : center.neighbors)
				{
					if (!queued[neighbor.index] && canHoldCenter(basin, neighbor))
					{
						queued[neighbor.index] = true;
						queue.add(neighbor);
					}
				}
			}
			growth.maxSize = Math.max(maxLakeSize, growth.existingLakeCenterCount);
			basin.lakeGrowth = growth;
			return growth;
		}

		/**
		 * The inflow a basin needs to fill to its top, capped at what a lake of the maximum size evaporates.
		 */
		private double inflowToFill(Basin basin)
		{
			if (basin.top == Double.POSITIVE_INFINITY)
			{
				return Double.POSITIVE_INFINITY;
			}
			return evaporationPerLakeCenter * getLakeGrowth(basin).areaAtTopWithinMaxSize();
		}

		/**
		 * Decides which basins hold lakes, overflow, or dry up. Basins are processed from upstream to downstream so that each one's inflow
		 * includes the overflow of the basins that spill into it.
		 */
		BasinResult fillBasins()
		{
			BasinResult result = new BasinResult();
			List<Basin> ordered = new ArrayList<>(topBasins);
			ordered.sort(Comparator.comparingInt((Basin b) -> b.spillOrder).reversed());
			for (Basin top : ordered)
			{
				double inflow = top.inflow();
				fill(top, inflow, new ArrayList<>(), result);
				double overflow = inflow - inflowToFill(top);
				if (top.spillsIntoDrainedBasin && overflow > 0)
				{
					for (Basin basin = leafOfCorner[top.receiver.index]; basin != null; basin = basin.parent)
					{
						basin.extraInflow += overflow;
					}
				}
			}
			return result;
		}

		private record AddedInflow(Basin leaf, double amount)
		{
		}

		/**
		 * Distributes a basin's inflow among its lakes.
		 *
		 * @param addedInflows
		 *            Inflow that reaches leaves of this basin from its siblings, beyond what {@link Basin#inflow()} includes.
		 */
		private void fill(Basin basin, double inflow, List<AddedInflow> addedInflows, BasinResult result)
		{
			LakeGrowth growth = getLakeGrowth(basin);
			double inflowToFill = inflowToFill(basin);
			if (inflow >= inflowToFill)
			{
				// The basin fills to its top and overflows.
				if (growth.areaAtTopWithinMaxSize() > 0)
				{
					addLake(basin, growth, growth.areaAtTopWithinMaxSize(), true, inflow, inflowToFill, result);
				}
				return;
			}

			int area = Math.max(growth.existingLakeCenterCount, (int) (inflow / evaporationPerLakeCenter));
			if (basin.isLeaf())
			{
				addClosedLakeOrDryPit(basin, growth, area, inflow, inflowToFill, result);
				return;
			}

			double inflow1 = inflowOf(basin.child1, addedInflows);
			double inflow2 = inflowOf(basin.child2, addedInflows);
			double overflow1 = inflow1 - inflowToFill(basin.child1);
			double overflow2 = inflow2 - inflowToFill(basin.child2);
			if (overflow1 >= 0 && overflow2 >= 0 || overflow1 >= 0 && inflow2 + overflow1 >= inflowToFill(basin.child2)
					|| overflow2 >= 0 && inflow1 + overflow2 >= inflowToFill(basin.child1))
			{
				// Both children fill to the pass between them, so they form one lake above it.
				addClosedLakeOrDryPit(basin, growth, area, inflow, inflowToFill, result);
			}
			else if (overflow1 >= 0)
			{
				fill(basin.child1, inflow1, addedInflows, result);
				fill(basin.child2, inflow2 + overflow1, withAddedInflow(addedInflows, leafOfCorner[basin.child1.receiver.index], overflow1), result);
			}
			else if (overflow2 >= 0)
			{
				fill(basin.child2, inflow2, addedInflows, result);
				fill(basin.child1, inflow1 + overflow2, withAddedInflow(addedInflows, leafOfCorner[basin.child2.receiver.index], overflow2), result);
			}
			else
			{
				fill(basin.child1, inflow1, addedInflows, result);
				fill(basin.child2, inflow2, addedInflows, result);
			}
		}

		private List<AddedInflow> withAddedInflow(List<AddedInflow> addedInflows, Basin leaf, double amount)
		{
			List<AddedInflow> result = new ArrayList<>(addedInflows);
			result.add(new AddedInflow(leaf, amount));
			return result;
		}

		private double inflowOf(Basin basin, List<AddedInflow> addedInflows)
		{
			double inflow = basin.inflow();
			for (AddedInflow added : addedInflows)
			{
				if (basin.contains(added.leaf))
				{
					inflow += added.amount;
				}
			}
			return inflow;
		}

		private void addClosedLakeOrDryPit(Basin basin, LakeGrowth growth, int area, double inflow, double inflowToFill, BasinResult result)
		{
			if (growth.canHoldLakeOfSize(area))
			{
				addLake(basin, growth, area, false, inflow, inflowToFill, result);
				return;
			}

			Corner lowestPit = null;
			for (int i = basin.leafRangeStart; i < basin.leafRangeEnd; i++)
			{
				Corner pit = leavesInOrder.get(i).pit;
				if (pit != null && (lowestPit == null || isLower(pit, lowestPit)))
				{
					lowestPit = pit;
				}
			}
			if (lowestPit != null)
			{
				result.dryPits.add(lowestPit);
			}
		}

		private void addLake(Basin basin, LakeGrowth growth, int area, boolean overflows, double inflow, double inflowToFill, BasinResult result)
		{
			Lake lake = new Lake();
			lake.basin = basin;
			area = Math.min(Math.max(area, growth.existingLakeCenterCount), growth.centers.size());
			area = Math.min(area, growth.maxSize);
			lake.centers = new ArrayList<>(growth.centers.subList(0, area));
			lake.level = growth.levels.get(area - 1);
			lake.overflows = overflows && basin.top != Double.POSITIVE_INFINITY;
			lake.inflow = inflow;
			lake.inflowToOverflow = inflowToFill;
			lake.exceedsMaxLakeSize = growth.exceedsMaxSize() && overflows;
			if (lake.exceedsMaxLakeSize)
			{
				lake.maxLevelWithinMaxLakeSize = growth.levels.get(growth.maxSize);
			}
			result.lakes.add(lake);
		}

		/**
		 * The elevation of the water that flow entering the given corner ends up in.
		 */
		double waterLevelDownstreamOf(Corner corner, BasinResult result)
		{
			Corner current = corner;
			while (downhill[current.index] != null)
			{
				current = downhill[current.index];
			}
			Basin leaf = leafOfCorner[current.index];
			if (leaf == ocean)
			{
				return WorldGraph.seaLevel;
			}
			for (Lake lake : result.lakes)
			{
				if (lake.basin.contains(leaf))
				{
					return lake.level;
				}
			}
			return current.elevation;
		}
	}

	private static boolean isLower(Corner corner, Corner other)
	{
		return corner.elevation < other.elevation || (corner.elevation == other.elevation && corner.index < other.index);
	}

	// ---------------------------------------------------------------------------------------------------------------------------------
	// Carving
	// ---------------------------------------------------------------------------------------------------------------------------------

	/**
	 * Carves the outlets of lakes whose inflow is well past what they need to overflow, and of lakes that would otherwise be too big.
	 *
	 * @return True if anything was carved.
	 */
	private boolean carveOutlets(DepressionHierarchy hierarchy, BasinResult result)
	{
		List<Lake> toCarve = new ArrayList<>();
		for (Lake lake : result.lakes)
		{
			if (!lake.overflows || isCarved[lake.basin.outlet.index] || isCarved[lake.basin.receiver.index])
			{
				continue;
			}
			double outflow = lake.inflow - lake.inflowToOverflow;
			if (lake.exceedsMaxLakeSize || (lake.inflow >= carveInflowFactor * lake.inflowToOverflow && outflow >= minOutflowToCarve))
			{
				toCarve.add(lake);
			}
		}

		// Upstream first, since carving an outlet lowers the channel below it.
		toCarve.sort(Comparator.comparingDouble((Lake lake) -> lake.basin.top).reversed());
		boolean carvedAny = false;
		for (Lake lake : toCarve)
		{
			carvedAny |= carveOutlet(hierarchy, result, lake);
		}
		return carvedAny;
	}

	private boolean carveOutlet(DepressionHierarchy hierarchy, BasinResult result, Lake lake)
	{
		Basin basin = lake.basin;
		Corner outlet = basin.outlet;
		Corner receiver = basin.receiver;
		double rimLevel = Math.max(outlet.elevation, receiver.elevation);
		double floor = Math.max(Math.max(WorldGraph.seaLevel, hierarchy.waterLevelDownstreamOf(receiver, result)), basin.floor);
		double maxDepth = rimLevel - floor;
		if (maxDepth <= 0)
		{
			return false;
		}

		double depth = carveDepthScale * Math.sqrt(Math.max(0, lake.inflow - lake.inflowToOverflow));
		if (lake.exceedsMaxLakeSize)
		{
			depth = Math.max(depth, rimLevel - lake.maxLevelWithinMaxLakeSize);
		}
		depth = Math.min(depth, maxDepth);
		double channelLevel = rimLevel - depth;

		carveUpstream(hierarchy, basin, outlet, channelLevel);
		carveDownstream(hierarchy, receiver, channelLevel);
		return true;
	}

	/**
	 * Lowers a channel from the outlet back into the basin, along the route that climbs least, until it reaches ground at or below the
	 * channel level.
	 */
	private void carveUpstream(DepressionHierarchy hierarchy, Basin basin, Corner outlet, double channelLevel)
	{
		// Dijkstra's algorithm, where a path's cost is the highest elevation along it.
		double[] cost = new double[graph.corners.size()];
		Arrays.fill(cost, Double.POSITIVE_INFINITY);
		boolean[] done = new boolean[graph.corners.size()];
		Corner[] previous = new Corner[graph.corners.size()];
		PriorityQueue<PathEntry> queue = new PriorityQueue<>(Comparator.comparingDouble(PathEntry::cost).thenComparingInt(e -> e.corner.index));
		cost[outlet.index] = outlet.elevation;
		queue.add(new PathEntry(outlet, outlet.elevation));
		Corner end = null;
		while (!queue.isEmpty())
		{
			Corner corner = queue.poll().corner;
			if (done[corner.index])
			{
				continue;
			}
			done[corner.index] = true;
			Basin leaf = hierarchy.leafOfCorner[corner.index];
			if (corner.elevation <= channelLevel || leaf.existingLake != null)
			{
				end = corner;
				break;
			}
			for (Corner neighbor : corner.adjacent)
			{
				if (neighbor == corner || !basin.contains(hierarchy.leafOfCorner[neighbor.index]))
				{
					continue;
				}
				double neighborCost = Math.max(cost[corner.index], neighbor.elevation);
				if (neighborCost < cost[neighbor.index])
				{
					cost[neighbor.index] = neighborCost;
					previous[neighbor.index] = corner;
					queue.add(new PathEntry(neighbor, neighborCost));
				}
			}
		}

		if (end == null)
		{
			return;
		}
		for (Corner corner = previous[end.index]; corner != null; corner = previous[corner.index])
		{
			lowerTo(corner, channelLevel);
		}
		lowerTo(end, channelLevel);
	}

	private record PathEntry(Corner corner, double cost)
	{
	}

	/**
	 * Lowers the receiving side of an outlet's pass to the channel level, then lowers a channel downhill from it, keeping it just below the
	 * corner before it, until the natural terrain is lower than the channel or the channel reaches water.
	 */
	private void carveDownstream(DepressionHierarchy hierarchy, Corner receiver, double channelLevel)
	{
		lowerTo(receiver, channelLevel);
		Corner current = hierarchy.downhill[receiver.index];
		double level = channelLevel;
		while (current != null && waterBodyOfCorner(current) == null)
		{
			level *= 1.0 - channelSlope;
			if (current.elevation < level)
			{
				break;
			}
			lowerTo(current, level);
			current = hierarchy.downhill[current.index];
		}
	}

	private void lowerTo(Corner corner, double level)
	{
		if (corner.elevation > level)
		{
			corner.elevation = level;
			isCarved[corner.index] = true;
		}
	}

	// ---------------------------------------------------------------------------------------------------------------------------------
	// Final routing
	// ---------------------------------------------------------------------------------------------------------------------------------

	private void addLakes(List<Lake> lakes)
	{
		for (Lake lake : lakes)
		{
			for (Center center : lake.centers)
			{
				center.isWater = true;
			}
		}
		findWaterBodies();
		for (Lake lake : lakes)
		{
			WaterBody body = waterBodies.get(waterBodyOfCenter[lake.centers.get(0).index]);
			body.level = lake.level;
			body.overflows = lake.overflows;
			body.evaporation = evaporationPerLakeCenter * body.centers.size();
		}
	}

	private record FloodEntry(Corner corner, Corner from, double level, int tieBreak, long sequence)
	{
	}

	/**
	 * Routes rain to the ocean, lakes, and dry basins, and stores the flow along each edge as its river size.
	 * <p>
	 * A priority-flood from every place water ends (the ocean, lakes that don't overflow, and dry basin floors) gives each corner a filled
	 * level, which is the lowest level water there would need to rise to in order to drain, and a receiver, which is the neighbor the
	 * flood reached it from. Each land corner drains to its lowest neighbor with a lower filled level, or else to its receiver, which leads
	 * across flat ground and through overflowing lakes toward their outlets. An overflowing lake's outlet is the first of its corners the
	 * flood reaches.
	 * </p>
	 */
	private void routeAndAccumulateFlow(BasinResult result)
	{
		int cornerCount = graph.corners.size();
		double[] filledLevel = new double[cornerCount];
		Arrays.fill(filledLevel, Double.NaN);
		Corner[] floodReceiver = new Corner[cornerCount];
		List<Corner> floodOrder = new ArrayList<>(cornerCount);
		// Corners of overflowing lakes go first among corners at the same level, so that flow through a lake crosses the lake rather than
		// the land along its shore.
		PriorityQueue<FloodEntry> queue = new PriorityQueue<>(
				Comparator.comparingDouble(FloodEntry::level).thenComparingInt(FloodEntry::tieBreak).thenComparingLong(FloodEntry::sequence));
		long sequence = 0;

		for (Corner pit : result.dryPits)
		{
			queue.add(new FloodEntry(pit, null, pit.elevation, 0, sequence++));
		}
		for (Corner corner : graph.corners)
		{
			WaterBody body = waterBodyOfCorner(corner);
			if (body != null && !body.overflows)
			{
				queue.add(new FloodEntry(corner, null, body.level, 0, sequence++));
			}
		}

		while (!queue.isEmpty())
		{
			FloodEntry entry = queue.poll();
			Corner corner = entry.corner;
			if (!Double.isNaN(filledLevel[corner.index]))
			{
				continue;
			}
			filledLevel[corner.index] = entry.level;
			floodReceiver[corner.index] = entry.from;
			floodOrder.add(corner);
			for (Corner neighbor : corner.adjacent)
			{
				if (neighbor != corner && Double.isNaN(filledLevel[neighbor.index]))
				{
					WaterBody neighborBody = waterBodyOfCorner(neighbor);
					int tieBreak = neighborBody != null && neighborBody.overflows ? 0 : 1;
					queue.add(new FloodEntry(neighbor, corner, Math.max(neighbor.elevation, entry.level), tieBreak, sequence++));
				}
			}
		}

		// Where each corner sends its flow. Only land corners and the outlets of overflowing lakes send flow anywhere.
		Corner[] flowTarget = new Corner[cornerCount];
		Corner[] outletOfBody = new Corner[waterBodies.size()];
		for (Corner corner : floodOrder)
		{
			if (floodReceiver[corner.index] == null)
			{
				continue;
			}
			int bodyIndex = waterBodyIndexOfCorner(corner);
			if (bodyIndex == -1)
			{
				flowTarget[corner.index] = findFlowTarget(corner, filledLevel, floodReceiver);
			}
			else if (outletOfBody[bodyIndex] == null)
			{
				outletOfBody[bodyIndex] = corner;
				flowTarget[corner.index] = findFlowTarget(corner, filledLevel, floodReceiver);
			}
		}

		// Streams that end in a dry basin carry too little water to reach a lake, so they aren't drawn. Every corner's flow target was reached
		// by the flood before it, so going through the flood order forwards visits each corner after everything downstream of it.
		boolean[] endsInDryBasin = new boolean[cornerCount];
		for (Corner pit : result.dryPits)
		{
			endsInDryBasin[pit.index] = true;
		}
		for (Corner corner : floodOrder)
		{
			Corner target = flowTarget[corner.index];
			if (target != null && waterBodyIndexOfCorner(corner) == -1 && waterBodyIndexOfCorner(target) == -1)
			{
				endsInDryBasin[corner.index] = endsInDryBasin[target.index];
			}
		}

		// Going through the flood order backwards visits each corner after everything upstream of it.
		double[] flow = new double[cornerCount];
		double[] lakeInflow = new double[waterBodies.size()];
		for (int i = floodOrder.size() - 1; i >= 0; i--)
		{
			Corner corner = floodOrder.get(i);
			int bodyIndex = waterBodyIndexOfCorner(corner);
			if (bodyIndex == -1)
			{
				flow[corner.index] += rain[corner.index];
			}
			else
			{
				WaterBody body = waterBodies.get(bodyIndex);
				if (!body.overflows)
				{
					continue;
				}
				lakeInflow[bodyIndex] += rain[corner.index];
				if (corner != outletOfBody[bodyIndex])
				{
					continue;
				}
				flow[corner.index] = Math.max(0, lakeInflow[bodyIndex] - body.evaporation);
			}

			Corner target = flowTarget[corner.index];
			if (target == null || flow[corner.index] <= 0)
			{
				continue;
			}
			if (!endsInDryBasin[corner.index])
			{
				corner.lookupEdgeFromCorner(target).river = (int) Math.round(flow[corner.index]);
			}
			int targetBodyIndex = waterBodyIndexOfCorner(target);
			if (targetBodyIndex == -1)
			{
				flow[target.index] += flow[corner.index];
			}
			else if (waterBodies.get(targetBodyIndex).overflows)
			{
				lakeInflow[targetBodyIndex] += flow[corner.index];
			}
		}

		for (Corner corner : graph.corners)
		{
			corner.river = endsInDryBasin[corner.index] ? 0 : (int) Math.round(flow[corner.index]);
		}
	}

	/**
	 * Returns the lowest neighbor whose filled level is below the corner's, or the corner's flood receiver if no neighbor's is.
	 */
	private Corner findFlowTarget(Corner corner, double[] filledLevel, Corner[] floodReceiver)
	{
		Corner lowest = null;
		for (Corner neighbor : corner.adjacent)
		{
			if (neighbor != corner && filledLevel[neighbor.index] < filledLevel[corner.index] && (lowest == null || isLower(neighbor, lowest)))
			{
				lowest = neighbor;
			}
		}
		return lowest != null ? lowest : floodReceiver[corner.index];
	}
}
