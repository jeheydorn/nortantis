package nortantis;

import nortantis.MapSettings.LineStyle;
import nortantis.geom.IntRectangle;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.graph.voronoi.*;
import nortantis.graph.voronoi.nodename.as3delaunay.Voronoi;
import nortantis.platform.*;
import nortantis.util.Helper;
import nortantis.util.Range;
import org.apache.commons.lang3.function.TriFunction;
import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.random.RandomGenerator;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * TestGraphImpl.java
 *
 * Supplies information for Voronoi graphing logic:
 *
 * 1) biome mapping information based on a Site's elevation and moisture
 *
 * 2) color mapping information based on biome, and for bodies of water
 *
 */
public class WorldGraph extends VoronoiGraph
{
	/**
	 * Controls which algorithm is used for center lookup.
	 */
	public enum CenterLookupMode
	{
		/** Create a new PixelReader for each lookup (slowest, but no caching issues) */
		PIXEL_UNCACHED,
		/** Use a cached PixelReader for lookups (fast, but has caching/update issues with Skia) */
		PIXEL_CACHED,
		/** Use spatial grid with geometric tests (fast, no pixel reading, but uses straight edges) */
		GRID_BASED
	}

	/**
	 * Controls which algorithm is used for findClosestCenter.
	 */
	public static CenterLookupMode centerLookupMode = CenterLookupMode.PIXEL_CACHED;

	// Debug counters for grid-based lookup
	public static long gridLookupDirectHits = 0;
	public static long gridLookupNeighborHits = 0;
	public static long gridLookupBfsFallbacks = 0;

	public static void resetGridLookupCounters()
	{
		gridLookupDirectHits = 0;
		gridLookupNeighborHits = 0;
		gridLookupBfsFallbacks = 0;
	}

	// Modify seeFloorLevel to change the number of islands in the ocean.
	public static final float oceanPlateLevel = 0.2f;
	final double continentalPlateLevel = 0.45;
	public static final float seaLevel = 0.39f;
	// Higher values will make larger plates, but fewer of them.
	private final int tectonicPlateIterationMultiplier = 30;

	// Zero is most random. Higher values make the polygons more uniform shaped.
	// This value is scaled by lloydRelaxationsScale passed into constructors.
	private final int numLloydRelaxations = 1;

	double nonBorderPlateContinentalProbability;
	// The probability that a plate touching the border will be continental.
	double borderPlateContinentalProbability;
	// This scales how much elevation is added or subtracted at
	// convergent/divergent boundaries.
	final double collisionScale = 0.4;
	// This controls how smooth the plates boundaries are. Higher is smoother. 1
	// is minimum. Larger values
	// will slow down plate generation.
	final int plateBoundarySmoothness = 26;
	final int minPoliticalRegionSize = 10;
	// During tectonic plate creation, if there are only two plates left and the
	// smaller of the two is less than this size, stop.
	// Prevents small maps from containing only one tectonic plate.
	final int minNinthtoLastPlateSize = 100;
	private Double meanCenterWidth;
	private Double meanCenterWidthBetweenNeighbors;
	private List<Set<Center>> lakes;

	// Maps plate ids to plates.
	Set<TectonicPlate> plates;
	public Map<Integer, Region> regions;
	LandShape landShape;
	int regionCount;

	public WorldGraph(Voronoi v, double lloydRelaxationsScale, Random r, double nonBorderPlateContinentalProbability, double borderPlateContinentalProbability, double sizeMultiplier,
			LineStyle lineStyle, double pointPrecision, boolean createElevationBiomesLakesAndRegions, boolean areRegionBoundariesVisible, LandShape landShape, int regionCount)
	{
		super(r, sizeMultiplier, pointPrecision);
		this.nonBorderPlateContinentalProbability = nonBorderPlateContinentalProbability;
		this.borderPlateContinentalProbability = borderPlateContinentalProbability;
		this.landShape = landShape;
		this.regionCount = regionCount;
		TectonicPlate.resetIds();
		initVoronoiGraph(v, numLloydRelaxations, lloydRelaxationsScale, createElevationBiomesLakesAndRegions);
		regions = new TreeMap<>();

		// Switch the center locations the Voronoi centroids of each center
		// because I think that
		// looks better for drawing, and it works better for smooth coastlines.
		updateCenterLocationsToCentroids();

		if (createElevationBiomesLakesAndRegions)
		{
			createPoliticalRegions();
			markLakes();
			smoothCoastlinesAndRegionBoundariesIfNeeded(centers, lineStyle, areRegionBoundariesVisible);
		}
	}

	/**
	 * This constructor doesn't create tectonic plates or elevation.
	 */
	public WorldGraph(Voronoi v, double lloydRelaxationsScale, Random r, double resolutionScale, double pointPrecision)
	{
		super(r, resolutionScale, pointPrecision);
		initVoronoiGraph(v, numLloydRelaxations, lloydRelaxationsScale, false);
	}

	private void updateCenterLocationsToCentroids()
	{
		for (Center center : centers)
		{
			center.updateLocToCentroid();
		}
	}

	public Set<Center> smoothCoastlinesAndRegionBoundariesIfNeeded(Collection<Center> centersToUpdate, LineStyle lineStyle, boolean areRegionBoundariesVisible)
	{
		if (centersToUpdate != centers)
		{
			// When doing incremental drawing, expand the centers to update to
			// include neighbors so that and single-center islands or water
			// that were expanded by the last brush stroke get reevaluated.
			addNeighbors(centersToUpdate);
		}

		Set<Center> changed = new HashSet<Center>();
		if (lineStyle == LineStyle.SplinesWithSmoothedCoastlines)
		{
			changed = smoothCoastlinesAndOptionallyRegionBoundaries(centersToUpdate, areRegionBoundariesVisible);
			for (Center center : changed)
			{
				center.updateLocToCentroid();
			}
		}
		else if (lineStyle == LineStyle.Splines && areRegionBoundariesVisible)
		{
			changed = smoothRegionBoundaries(centersToUpdate);
			for (Center center : changed)
			{
				center.updateLocToCentroid();
			}
		}

		return changed;
	}

	public void addNeighbors(Collection<Center> centers)
	{
		assert centers instanceof Set;
		Set<Center> loopOver = new HashSet<>(centers);
		for (Center c : loopOver)
		{
			centers.addAll(c.neighbors);
		}
	}

	private Set<Center> smoothCoastlinesAndOptionallyRegionBoundaries(Collection<Center> centersToUpdate, boolean smoothRegionBoundaries)
	{
		Set<Corner> cornersToUpdate = getCornersFromCenters(centersToUpdate);
		Set<Center> centersChanged = new HashSet<Center>();

		for (Corner corner : cornersToUpdate)
		{
			SmoothingResult coastlineResult = updateCornerLocationToSmoothEdges(corner, e -> e.isCoast());
			boolean isCornerChanged = coastlineResult.isCornerChanged;
			// Only smooth region boundaries for the corner if it is not a
			// coastline, because otherwise we will clear the smoothing on that
			// spot on the coastline.
			if (!coastlineResult.isSmoothed && smoothRegionBoundaries)
			{
				SmoothingResult regionResult = updateCornerLocationToSmoothEdges(corner, e -> e.isRegionBoundary() && !e.isRiver());
				isCornerChanged |= regionResult.isCornerChanged;
			}
			if (isCornerChanged)
			{
				centersChanged.addAll(corner.touches);
			}
		}
		return centersChanged;
	}

	private Set<Center> smoothRegionBoundaries(Collection<Center> centersToUpdate)
	{
		Set<Corner> cornersToUpdate = getCornersFromCenters(centersToUpdate);
		Set<Center> centersChanged = new HashSet<Center>();

		for (Corner corner : cornersToUpdate)
		{
			SmoothingResult result = updateCornerLocationToSmoothEdges(corner, c -> c.isRegionBoundary() && !c.isRiver());
			if (result.isCornerChanged)
			{
				centersChanged.addAll(corner.touches);
			}
		}
		return centersChanged;
	}

	private SmoothingResult updateCornerLocationToSmoothEdges(Corner corner, Function<Edge, Boolean> shouldSmoothEdge)
	{
		List<Edge> edgesToSmooth = null;
		for (Edge p : corner.protrudes)
		{
			if (shouldSmoothEdge.apply(p))
			{
				if (edgesToSmooth == null)
				{
					edgesToSmooth = new ArrayList<Edge>(2);
				}
				edgesToSmooth.add(p);
			}
		}

		if (edgesToSmooth == null || edgesToSmooth.size() == 0)
		{
			// This corner is not on an edge to smooth. Clear the override to
			// set the corner's location back to how it was first
			// generated.
			boolean isChanged = !corner.loc.equals(corner.originalLoc);
			corner.resetLocToOriginal();
			return new SmoothingResult(isChanged, false);
		}

		if (edgesToSmooth.size() == 2)
		{
			// Don't smooth edges on islands/regions made of only one center,
			// because it tends to make the center very small.
			if (corner.touches.stream().anyMatch(center -> isSinglePolygonToSmooth(center, shouldSmoothEdge)))
			{
				boolean isChanged = !corner.loc.equals(corner.originalLoc);
				corner.resetLocToOriginal();
				return new SmoothingResult(isChanged, false);
			}

			// Smooth the edge
			Corner otherCorner0 = edgesToSmooth.get(0).v0 == corner ? edgesToSmooth.get(0).v1 : edgesToSmooth.get(0).v0;
			Corner otherCorner1 = edgesToSmooth.get(1).v0 == corner ? edgesToSmooth.get(1).v1 : edgesToSmooth.get(1).v0;
			Point smoothedLoc = new Point((otherCorner0.originalLoc.x + otherCorner1.originalLoc.x) / 2, (otherCorner0.originalLoc.y + otherCorner1.originalLoc.y) / 2);

			corner.loc = smoothedLoc;

			return new SmoothingResult(true, true);
		}
		else
		{
			// 3 or more boundaries that should be smoothed intersect. Don't
			// smooth because it's not clear what direction to smooth.
			boolean isChanged = !corner.loc.equals(corner.originalLoc);
			corner.resetLocToOriginal();
			return new SmoothingResult(isChanged, false);
		}
	}

	private boolean isSinglePolygonToSmooth(Center center, Function<Edge, Boolean> shouldSmoothEdge)
	{
		return center.borders.stream().allMatch(e -> shouldSmoothEdge.apply(e));
	}

	private class SmoothingResult
	{
		boolean isCornerChanged;
		boolean isSmoothed;

		public SmoothingResult(boolean isChanged, boolean isSmoothed)
		{
			this.isCornerChanged = isChanged;
			this.isSmoothed = isSmoothed;
		}
	}

	public void rebuildNoisyEdgesForCenter(Center center)
	{
		rebuildNoisyEdgesForCenter(center, null);
	}

	/**
	 * Rebuilds noisy edges for a center.
	 *
	 * @param center
	 *            The center to rebuild noisy edges for.
	 * @param centersInLoop
	 *            For performance. The set of centers that the caller is already looping over, so that we don't rebuild noisy edges for
	 *            neighbors unnecessarily.
	 */
	public void rebuildNoisyEdgesForCenter(Center center, Set<Center> centersInLoop)
	{
		noisyEdges.buildNoisyEdgesForCenter(center, true);

		if (noisyEdges.getLineStyle() == LineStyle.Splines || noisyEdges.getLineStyle() == LineStyle.SplinesWithSmoothedCoastlines)
		{
			for (Center n : center.neighbors)
			{
				if (centersInLoop == null || !centersInLoop.contains(n))
				{
					noisyEdges.buildNoisyEdgesForCenter(n, true);
				}
			}
		}
	}

	public void buildNoisyEdges(LineStyle lineStyle, boolean isForFrayedBorder)
	{
		noisyEdges = new NoisyEdges(MapCreator.calcSizeMultiplierFromResolutionScale(resolutionScale), lineStyle, isForFrayedBorder);
		noisyEdges.buildNoisyEdges(this);
	}

	@SuppressWarnings("unused")
	private void testPoliticalRegions()
	{
		for (Region region : regions.values())
		{
			for (Center c : region.getCenters())
			{
				assert !c.isWater;
				assert c.region == region;
			}

			assert centers.stream().filter(c -> c.region == region).count() == region.size();
		}
		assert new HashSet<>(regions.keySet()).size() == regions.size();
		for (Center c : centers)
		{
			if (!c.isWater)
			{
				assert c.region != null;
			}
			else
			{
				assert c.region == null;
			}

			if (c.region != null)
			{
				assert regions.values().stream().filter(reg -> reg.contains(c)).count() == 1;
			}
		}
		assert regions.values().stream().mapToInt(reg -> reg.size()).sum() + centers.stream().filter(c -> c.region == null).count() == centers.size();
	}

	public void drawRegionIndexes(Painter p, Set<Center> centersToDraw, Rectangle drawBounds)
	{
		// As we draw, if ocean centers are close to land, use the region index
		// from that land. That way
		// if the option to allow icons to draw over coastlines is true, then
		// region colors will
		// draw inside transparent pixels of icons whose content extends over
		// ocean.
		drawPolygons(p, centersToDraw, drawBounds, (c) ->
		{
			if (c.region == null)
			{
				// This needs to be far enough that no icon extends this far
				// into the ocean. The farthest I've seen any of my mountains
				// have
				// extend
				// into the ocean is 3 polygons, but I'm adding a buffer to be
				// safe. Note that increasing this is fairly expensive.
				final int maxDistanceToSearchForLand = 5;
				Center closestLand = findClosestLand(c, maxDistanceToSearchForLand);
				if (closestLand == null || closestLand.region == null)
				{
					return Color.black;
				}
				else
				{
					return storeValueAsColor(closestLand.region.id);
				}
			}
			else
			{
				return storeValueAsColor(c.region.id);
			}
		});
	}

	public static Color storeValueAsColor(int value)
	{
		int blue = value & 0xFF;
		int green = (value >> 8) & 0xFF;
		int red = (value >> 16) & 0xFF;
		return Color.create(red, green, blue);
	}

	public static int getValueFromColor(Color color)
	{
		return color.getRed() << 16 | color.getGreen() << 8 | color.getBlue();
	}

	private Center findClosestLand(Center center, int maxDistanceInPolygons)
	{
		return breadthFirstSearchForGoal((ignored1, ignored2, distanceFromStart) ->
		{
			return distanceFromStart < maxDistanceInPolygons;
		}, (c) ->
		{
			return !c.isWater;
		}, center);
	}

	/**
	 * Creates political regions. When done, all non-ocean centers will have a political region assigned.
	 */
	private void createPoliticalRegions()
	{
		List<Region> regionList = new ArrayList<>();

		// Regions start as land Centers on continental plates.
		for (TectonicPlate plate : plates)
		{
			if (plate.type == PlateType.Continental)
			{
				Region region = new Region();
				plate.centers.stream().filter(c -> !c.isWater).forEach(c -> region.addAndSetRegion(c));
				regionList.add(region);
			}
		}

		for (Region region : regionList)
		{
			// For each region, divide it by land masses separated by water.
			List<Set<Center>> dividedRegion = divideRegionByLand(region);

			if (dividedRegion.size() > 1)
			{
				// The region gets to keep only the largest land mass.
				Set<Center> biggest = dividedRegion.stream().max((l1, l2) -> Integer.compare(l1.size(), l2.size())).get();

				// then for each small land mass:
				for (Set<Center> regionPart : dividedRegion)
				{
					if (regionPart == biggest)
						continue;
					// If that small land mass is connected by land to a
					// different region, then add that land mass to that region.
					Region touchingRegion = findRegionTouching(regionPart);
					if (touchingRegion != null)
					{
						assert region != touchingRegion;
						region.removeAll(regionPart);
						touchingRegion.addAll(regionPart);
					}
					// Else leave it in this region
				}
			}
		}

		// Add to smallLandMasses any land which is not in a region.
		List<Set<Center>> smallLandMasses = new ArrayList<>(); // stores small
		// pieces of
		// land not in a
		// region.
		for (Center center : centers)
		{
			if (!center.isWater && center.region == null)
			{
				Set<Center> landMass = breadthFirstSearch(c -> !c.isWater && c.region == null, center);
				smallLandMasses.add(landMass);
			}
		}

		// For each region, if region is smaller than minPoliticalRegionSize,
		// make it not a region and add it to smallLandMasses.
		List<Integer> toRemove = new ArrayList<>();
		for (int i : new Range(regionList.size()))
		{
			if (regionList.get(i).size() < minPoliticalRegionSize)
			{
				toRemove.add(i);
				Set<Center> smallLandMass = new HashSet<>(regionList.get(i).getCenters());
				smallLandMasses.add(smallLandMass);
			}
		}
		Collections.reverse(toRemove);
		for (int i : toRemove)
		{
			regionList.get(i).clear(); // This updates the region pointers in
			// the Centers.
			regionList.remove(i);
		}

		// For each land mass in smallLandMasses, add it to the region nearest
		// its centroid.
		for (Set<Center> landMass : smallLandMasses)
		{
			Point centroid = WorldGraph.findCentroid(landMass);
			Region closest = findClosestRegion(centroid);
			if (closest != null)
			{
				closest.addAll(landMass);
			}
			else
			{
				// This will probably never happen because it means there are no
				// regions on the map at all.
				Region region = new Region();
				region.addAll(landMass);
				regionList.add(region);
			}
		}

		// Phase 5: Enforce exact region count when regionCount > 0.
		if (regionCount > 0)
		{
			// If too many regions, merge the smallest ones.
			while (regionList.size() > regionCount)
			{
				// Find the smallest region.
				Region smallest = null;
				for (Region r : regionList)
				{
					if (smallest == null || r.size() < smallest.size())
					{
						smallest = r;
					}
				}

				// Find a neighboring region to merge into.
				Region mergeTarget = findNeighboringRegion(smallest, regionList);
				if (mergeTarget == null)
				{
					// No land neighbor found; merge into the closest by centroid.
					Point centroid = WorldGraph.findCentroid(smallest.getCenters());
					for (Region r : regionList)
					{
						if (r == smallest)
						{
							continue;
						}
						if (mergeTarget == null)
						{
							mergeTarget = r;
						}
						else
						{
							Point rc = WorldGraph.findCentroid(r.getCenters());
							Point mc = WorldGraph.findCentroid(mergeTarget.getCenters());
							if (centroid.distanceTo(rc) < centroid.distanceTo(mc))
							{
								mergeTarget = r;
							}
						}
					}
				}

				if (mergeTarget != null)
				{
					mergeTarget.addAll(new HashSet<>(smallest.getCenters()));
					smallest.clear();
					regionList.remove(smallest);
				}
				else
				{
					break;
				}
			}

			// If too few regions, split the largest ones.
			while (regionList.size() < regionCount)
			{
				// Find the largest region.
				Region largest = null;
				for (Region r : regionList)
				{
					if (largest == null || r.size() > largest.size())
					{
						largest = r;
					}
				}

				if (largest == null || largest.size() < 2)
				{
					break;
				}

				// Split the largest region using two-pole BFS.
				Set<Center> regionCenters = largest.getCenters();
				Point centroid = WorldGraph.findCentroid(regionCenters);

				// Find pole A: farthest from centroid.
				Center poleA = null;
				double maxDist = -1;
				for (Center c : regionCenters)
				{
					double d = c.loc.distanceTo(centroid);
					if (d > maxDist)
					{
						maxDist = d;
						poleA = c;
					}
				}

				// Find pole B: farthest from pole A.
				Center poleB = null;
				maxDist = -1;
				for (Center c : regionCenters)
				{
					if (c == poleA)
					{
						continue;
					}
					double d = c.loc.distanceTo(poleA.loc);
					if (d > maxDist)
					{
						maxDist = d;
						poleB = c;
					}
				}

				if (poleB == null)
				{
					break;
				}

				// Simultaneous BFS from A and B within the region.
				Set<Center> groupA = new HashSet<>();
				Set<Center> groupB = new HashSet<>();
				Queue<Center> queueA = new LinkedList<>();
				Queue<Center> queueB = new LinkedList<>();
				Set<Center> visited = new HashSet<>();

				groupA.add(poleA);
				queueA.add(poleA);
				visited.add(poleA);
				groupB.add(poleB);
				queueB.add(poleB);
				visited.add(poleB);

				while (!queueA.isEmpty() || !queueB.isEmpty())
				{
					// Expand A by one level
					int sizeA = queueA.size();
					for (int step = 0; step < sizeA; step++)
					{
						Center c = queueA.poll();
						for (Center n : c.neighbors)
						{
							if (regionCenters.contains(n) && !visited.contains(n))
							{
								visited.add(n);
								groupA.add(n);
								queueA.add(n);
							}
						}
					}
					// Expand B by one level
					int sizeB = queueB.size();
					for (int step = 0; step < sizeB; step++)
					{
						Center c = queueB.poll();
						for (Center n : c.neighbors)
						{
							if (regionCenters.contains(n) && !visited.contains(n))
							{
								visited.add(n);
								groupB.add(n);
								queueB.add(n);
							}
						}
					}
				}

				// Create new regions from the two groups.
				largest.clear();
				regionList.remove(largest);

				Region regionA = new Region();
				regionA.addAll(groupA);
				regionList.add(regionA);

				if (!groupB.isEmpty())
				{
					Region regionB = new Region();
					regionB.addAll(groupB);
					regionList.add(regionB);
				}
			}
		}

		// Set the id of each region and add it to the regions map.
		for (int i : new Range(regionList.size()))
		{
			regionList.get(i).id = i;
			regions.put(i, regionList.get(i));
		}
	}

	/**
	 * Finds the region closest (in terms of Cartesian distance) to the given point.
	 */
	private Region findClosestRegion(Point point)
	{
		Optional<Center> opt = centers.stream().filter(c -> c.region != null).min((c1, c2) -> Double.compare(c1.loc.distanceTo(point), c2.loc.distanceTo(point)));

		if (opt.isPresent())
		{
			assert opt.get().region != null;
			return opt.get().region;
		}

		// This could only happen if there are no regions on the graph.
		return null;
	}

	/**
	 * Finds the smallest neighboring region of the given region. Two regions are neighbors if any center in one is a neighbor of a center
	 * in the other.
	 */
	private Region findNeighboringRegion(Region region, List<Region> allRegions)
	{
		Region smallestNeighbor = null;
		for (Center c : region.getCenters())
		{
			for (Center n : c.neighbors)
			{
				if (n.region != null && n.region != region)
				{
					if (smallestNeighbor == null || n.region.size() < smallestNeighbor.size())
					{
						smallestNeighbor = n.region;
					}
				}
			}
		}
		return smallestNeighbor;
	}

	public Corner findClosestCorner(Point point)
	{
		Center closestCenter = findClosestCenter(point);
		Optional<Corner> optional = closestCenter.corners.stream().min((c1, c2) -> Double.compare(c1.loc.distanceTo(point), c2.loc.distanceTo(point)));
		return optional.get();
	}

	public Center findClosestCenter(Point point)
	{
		return findClosestCenter(point, false);
	}

	public Center findClosestCenter(Point point, boolean returnNullIfNotOnMap)
	{
		switch (centerLookupMode)
		{
			case PIXEL_UNCACHED:
				return findClosestCenterUsingPixelsUncached(point, returnNullIfNotOnMap);
			case PIXEL_CACHED:
				return findClosestCenterUsingPixelsCached(point, returnNullIfNotOnMap);
			case GRID_BASED:
				return findClosestCenterUsingGrid(point, returnNullIfNotOnMap);
			default:
				return findClosestCenterUsingGrid(point, returnNullIfNotOnMap);
		}
	}

	private Center findClosestCenterUsingPixelsUncached(Point point, boolean returnNullIfNotOnMap)
	{
		if (point.x < getWidth() && point.y < getHeight() && point.x >= 0 && point.y >= 0)
		{
			buildCenterLookupTableIfNeeded();
			int x = (int) point.x;
			int y = (int) point.y;
			Color color;

			// Create a new PixelReader for each lookup (no caching)
			try (PixelReader reader = centerLookupTable.createPixelReader(new IntRectangle(x, y, 1, 1)))
			{
				color = Color.create(reader.getRGB(x, y));
			}

			int index = color.getRed() | (color.getGreen() << 8) | (color.getBlue() << 16);
			return centers.get(index);
		}
		else if (!returnNullIfNotOnMap)
		{
			Optional<Center> opt = centers.stream().filter(c -> c.isBorder).min((c1, c2) -> Double.compare(c1.loc.distanceTo(point), c2.loc.distanceTo(point)));
			return opt.get();
		}
		return null;
	}

	private Center findClosestCenterUsingPixelsCached(Point point, boolean returnNullIfNotOnMap)
	{
		if (returnNullIfNotOnMap && (point.x >= getWidth() || point.y >= getHeight() || point.x < 0 || point.y < 0))
		{
			return null;
		}

		if (point.x < getWidth() && point.y < getHeight() && point.x >= 0 && point.y >= 0)
		{
			buildCenterLookupTableIfNeeded();
			int x = (int) point.x;
			int y = (int) point.y;
			Color color;

			synchronized (centerLookupLock)
			{
				color = Color.create(cachedCenterLookupReader.getRGB(x, y));
			}

			int index = color.getRed() | (color.getGreen() << 8) | (color.getBlue() << 16);
			return centers.get(index);
		}
		else if (!returnNullIfNotOnMap)
		{
			Optional<Center> opt = centers.stream().filter(c -> c.isBorder).min((c1, c2) -> Double.compare(c1.loc.distanceTo(point), c2.loc.distanceTo(point)));
			return opt.get();
		}
		return null;
	}

	private Center findClosestCenterUsingGrid(Point point, boolean returnNullIfNotOnMap)
	{
		buildCenterLookupGridIfNeeded();

		if (returnNullIfNotOnMap && (point.x >= getWidth() || point.y >= getHeight() || point.x < 0 || point.y < 0))
		{
			return null;
		}

		// Get starting center from grid
		Center candidate = centerLookupGrid.getRepresentative(point);
		if (candidate == null)
		{
			assert false;
			// This shouldn't happen with a properly built grid, but use distance-based fallback
			return centers.stream().min((c1, c2) -> Double.compare(c1.loc.distanceTo(point), c2.loc.distanceTo(point))).orElse(null);
		}

		// Fast path: find which edge sector contains the point and check that edge
		Center result = findCenterFromEdgeSector(point, candidate);
		if (result != null)
		{
			if (result == candidate)
			{
				gridLookupDirectHits++;
			}
			else
			{
				gridLookupNeighborHits++;
			}
			return result;
		}

		// Fallback - walk to a new candidate and try exhaustive search again
		gridLookupBfsFallbacks++;
		Center walkResult = walkToClosestCenter(point, candidate);

		// Try exhaustive search on the walk result
		if (walkResult != candidate)
		{
			result = findCenterFromEdgeSector(point, walkResult);
			if (result != null)
			{
				return result;
			}
		}

		// Last resort: return the closest center by distance
		return walkResult;
	}

	/**
	 * Find which center contains the point by checking pie slices. Uses angular sector as a hint to try the likely edge first, then falls
	 * back to exhaustive search.
	 */
	private Center findCenterFromEdgeSector(Point point, Center candidate)
	{
		// Fast path: use angular sector to find the likely edge and test it first
		int hintEdgePos = findAngularSectorEdgePosition(point, candidate);
		if (hintEdgePos >= 0)
		{
			Edge hintEdge = candidate.borders.get(hintEdgePos);
			if (isPointInPieSlice(point, candidate, hintEdgePos))
			{
				return candidate;
			}
			// Try the neighbor across this edge
			Center neighbor = (hintEdge.d0 == candidate) ? hintEdge.d1 : hintEdge.d0;
			if (neighbor != null)
			{
				int neighborEdgePos = findEdgePosition(neighbor, hintEdge);
				if (neighborEdgePos >= 0 && isPointInPieSlice(point, neighbor, neighborEdgePos))
				{
					return neighbor;
				}
			}
		}

		// Exhaustive search: check all pie slices of candidate (skip hint edge already tested)
		for (int i = 0; i < candidate.borders.size(); i++)
		{
			if (i == hintEdgePos)
			{
				continue;
			}
			Edge edge = candidate.borders.get(i);
			if (edge.v0 == null || edge.v1 == null)
			{
				continue;
			}
			if (isPointInPieSlice(point, candidate, i))
			{
				return candidate;
			}
		}

		// Check immediate neighbors (skip hint edge already tested)
		for (Center neighbor : candidate.neighbors)
		{
			for (int i = 0; i < neighbor.borders.size(); i++)
			{
				Edge edge = neighbor.borders.get(i);
				if (edge.v0 == null || edge.v1 == null)
				{
					continue;
				}
				// Skip if this is the hint edge we already tested
				if (hintEdgePos >= 0 && edge == candidate.borders.get(hintEdgePos))
				{
					continue;
				}
				if (isPointInPieSlice(point, neighbor, i))
				{
					return neighbor;
				}
			}
		}

		return null; // Need walk fallback
	}

	/**
	 * Find which edge's angular sector contains the point (fast test using straight lines). Returns the edge position in center.borders, or
	 * -1 if no sector contains the point.
	 */
	private int findAngularSectorEdgePosition(Point point, Center center)
	{
		double qx = point.x - center.loc.x;
		double qy = point.y - center.loc.y;

		for (int i = 0; i < center.borders.size(); i++)
		{
			Edge edge = center.borders.get(i);
			if (edge.v0 == null || edge.v1 == null)
			{
				continue;
			}

			double v0x = edge.v0.loc.x - center.loc.x;
			double v0y = edge.v0.loc.y - center.loc.y;
			double v1x = edge.v1.loc.x - center.loc.x;
			double v1y = edge.v1.loc.y - center.loc.y;

			double crossV0 = v0x * qy - v0y * qx;
			double crossV1 = v1x * qy - v1y * qx;
			double crossEdge = v0x * v1y - v0y * v1x;

			boolean inSector = (crossEdge >= 0) ? (crossV0 >= 0 && crossV1 <= 0) : (crossV0 <= 0 && crossV1 >= 0);

			if (inSector)
			{
				return i;
			}
		}
		return -1;
	}

	/**
	 * Find the position of an edge in a center's borders list.
	 */
	private int findEdgePosition(Center center, Edge edge)
	{
		for (int i = 0; i < center.borders.size(); i++)
		{
			if (center.borders.get(i) == edge)
			{
				return i;
			}
		}
		return -1;
	}

	/**
	 * Walk from the starting center to the center whose loc is closest to the query point. This is faster than BFS and converges quickly
	 * since Voronoi diagrams are well-structured.
	 */
	private Center walkToClosestCenter(Point query, Center start)
	{
		Center current = start;
		double currentDist = current.loc.distanceTo(query);

		// Walk until no neighbor is closer
		while (true)
		{
			Center closest = current;
			double closestDist = currentDist;

			for (Center neighbor : current.neighbors)
			{
				double d = neighbor.loc.distanceTo(query);
				if (d < closestDist)
				{
					closest = neighbor;
					closestDist = d;
				}
			}

			if (closest == current)
			{
				// No neighbor is closer - we've found it
				return current;
			}
			current = closest;
			currentDist = closestDist;
		}
	}

	private boolean isPointInPieSlice(Point query, Center center, int edgePosition)
	{
		// Get the cached slice polygon using array lookup (faster than HashMap)
		CachedSlicePolygon cached = getSlicePolygon(center, edgePosition);
		if (cached == null)
		{
			return false;
		}

		// Quick bounding box rejection
		if (query.x < cached.minX || query.x > cached.maxX || query.y < cached.minY || query.y > cached.maxY)
		{
			return false;
		}

		// Full polygon containment test
		return isPointInPolygonArray(query.x, query.y, cached.xCoords, cached.yCoords);
	}

	/**
	 * Cached slice polygon for fast point-in-polygon tests.
	 */
	private static class CachedSlicePolygon
	{
		final double[] xCoords;
		final double[] yCoords;
		final double minX, maxX, minY, maxY;

		CachedSlicePolygon(double[] xCoords, double[] yCoords)
		{
			this.xCoords = xCoords;
			this.yCoords = yCoords;

			// Compute bounding box
			double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
			double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
			for (int i = 0; i < xCoords.length; i++)
			{
				if (xCoords[i] < minX)
					minX = xCoords[i];
				if (xCoords[i] > maxX)
					maxX = xCoords[i];
				if (yCoords[i] < minY)
					minY = yCoords[i];
				if (yCoords[i] > maxY)
					maxY = yCoords[i];
			}
			this.minX = minX;
			this.maxX = maxX;
			this.minY = minY;
			this.maxY = maxY;
		}
	}

	// Array-based cache for slice polygons: [centerIndex][edgePositionInBorders]
	// This is faster than HashMap because it avoids hash computation and lookup
	private CachedSlicePolygon[][] slicePolygonsByCenter;

	private CachedSlicePolygon getSlicePolygon(Center center, int edgePosition)
	{
		if (slicePolygonsByCenter == null || slicePolygonsByCenter[center.index] == null)
		{
			return null;
		}
		return slicePolygonsByCenter[center.index][edgePosition];
	}

	private CachedSlicePolygon buildSlicePolygon(Center center, Edge edge)
	{
		// Build the slice polygon: center.loc + noisy edge path
		List<Point> noisyPath = noisyEdges != null ? noisyEdges.getNoisyEdge(edge.index) : null;

		int size;
		double[] xCoords;
		double[] yCoords;

		if (noisyPath != null && !noisyPath.isEmpty())
		{
			size = 1 + noisyPath.size();
			xCoords = new double[size];
			yCoords = new double[size];
			xCoords[0] = center.loc.x;
			yCoords[0] = center.loc.y;
			for (int i = 0; i < noisyPath.size(); i++)
			{
				Point p = noisyPath.get(i);
				xCoords[i + 1] = p.x;
				yCoords[i + 1] = p.y;
			}
		}
		else
		{
			// Fallback to straight edge triangle
			size = 3;
			xCoords = new double[size];
			yCoords = new double[size];
			xCoords[0] = center.loc.x;
			yCoords[0] = center.loc.y;
			if (edge.v0 != null && edge.v1 != null)
			{
				xCoords[1] = edge.v0.loc.x;
				yCoords[1] = edge.v0.loc.y;
				xCoords[2] = edge.v1.loc.x;
				yCoords[2] = edge.v1.loc.y;
			}
		}

		return new CachedSlicePolygon(xCoords, yCoords);
	}

	/**
	 * Clears the slice polygon cache for the specified centers and their neighbors. Call this when centers' noisy edges have been rebuilt.
	 */
	private void clearSlicePolygonCache(Collection<Center> centers)
	{
		if (slicePolygonsByCenter == null)
		{
			return;
		}

		Set<Center> centersWithNeighbors = new HashSet<>();
		for (Center c : centers)
		{
			centersWithNeighbors.add(c);
			for (Center neighbor : c.neighbors)
			{
				centersWithNeighbors.add(neighbor);
			}
		}

		for (Center c : centersWithNeighbors)
		{
			// Clear the array for this center so it will be rebuilt
			if (c.index < slicePolygonsByCenter.length && slicePolygonsByCenter[c.index] != null)
			{
				for (int i = 0; i < slicePolygonsByCenter[c.index].length; i++)
				{
					slicePolygonsByCenter[c.index][i] = null;
				}
			}
		}
	}

	/**
	 * Fast point-in-polygon test using ray casting with array coordinates.
	 */
	private boolean isPointInPolygonArray(double px, double py, double[] xCoords, double[] yCoords)
	{
		int n = xCoords.length;
		if (n < 3)
		{
			return false;
		}

		boolean inside = false;
		for (int i = 0, j = n - 1; i < n; j = i++)
		{
			double xi = xCoords[i], yi = yCoords[i];
			double xj = xCoords[j], yj = yCoords[j];

			if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi)
			{
				inside = !inside;
			}
		}
		return inside;
	}

	private Image centerLookupTable;

	// Cached pixel reader for findClosestCenter optimization - covers the entire map
	private final Object centerLookupLock = new Object();
	private PixelReader cachedCenterLookupReader;

	// Grid-based center lookup
	private CenterLookupGrid centerLookupGrid;

	private void buildCenterLookupGridIfNeeded()
	{
		if (centerLookupGrid == null)
		{
			centerLookupGrid = new CenterLookupGrid();
			centerLookupGrid.build(centers, getWidth(), getHeight());

			// Precompute all slice polygons for faster lookups
			precomputeSlicePolygons();
		}
	}

	/**
	 * Precomputes all slice polygons for all centers and their edges using array-based storage.
	 */
	private void precomputeSlicePolygons()
	{
		// Initialize the 2D array
		slicePolygonsByCenter = new CachedSlicePolygon[centers.size()][];

		for (Center center : centers)
		{
			slicePolygonsByCenter[center.index] = new CachedSlicePolygon[center.borders.size()];
			for (int i = 0; i < center.borders.size(); i++)
			{
				Edge edge = center.borders.get(i);
				if (edge.v0 != null && edge.v1 != null)
				{
					slicePolygonsByCenter[center.index][i] = buildSlicePolygon(center, edge);
				}
			}
		}
	}

	/**
	 * Rebuilds slice polygons for the specified centers and their neighbors.
	 */
	private void precomputeSlicePolygonsForCenters(Collection<Center> centersToUpdate)
	{
		if (slicePolygonsByCenter == null)
		{
			// Initialize full array if not yet done
			precomputeSlicePolygons();
			return;
		}

		Set<Center> centersWithNeighbors = new HashSet<>();
		for (Center c : centersToUpdate)
		{
			centersWithNeighbors.add(c);
			for (Center neighbor : c.neighbors)
			{
				centersWithNeighbors.add(neighbor);
			}
		}

		for (Center center : centersWithNeighbors)
		{
			// Rebuild the slice polygons array for this center
			if (slicePolygonsByCenter[center.index] == null)
			{
				slicePolygonsByCenter[center.index] = new CachedSlicePolygon[center.borders.size()];
			}
			for (int i = 0; i < center.borders.size(); i++)
			{
				Edge edge = center.borders.get(i);
				if (edge.v0 != null && edge.v1 != null)
				{
					slicePolygonsByCenter[center.index][i] = buildSlicePolygon(center, edge);
				}
			}
		}
	}

	/**
	 * Spatial grid for efficient center lookup. Each cell stores a representative center that is close to that cell's center point.
	 */
	private class CenterLookupGrid
	{
		private Center[][] grid;
		private int cellWidth;
		private int cellHeight;
		private int gridCols;
		private int gridRows;

		void build(List<Center> centers, int mapWidth, int mapHeight)
		{
			// Calculate cell size based on center density
			double avgSpacing = Math.sqrt((double) (mapWidth * mapHeight) / centers.size());
			cellWidth = Math.max(1, (int) (avgSpacing * 2));
			cellHeight = Math.max(1, (int) (avgSpacing * 2));
			gridCols = (mapWidth / cellWidth) + 1;
			gridRows = (mapHeight / cellHeight) + 1;

			// Step 1: Create temp grid holding ALL centers per cell (O(n))
			@SuppressWarnings("unchecked")
			List<Center>[][] tempGrid = new ArrayList[gridRows][gridCols];
			for (int r = 0; r < gridRows; r++)
			{
				for (int c = 0; c < gridCols; c++)
				{
					tempGrid[r][c] = new ArrayList<>();
				}
			}

			// Add each center to the cell containing its loc
			for (Center center : centers)
			{
				int col = clamp((int) (center.loc.x / cellWidth), 0, gridCols - 1);
				int row = clamp((int) (center.loc.y / cellHeight), 0, gridRows - 1);
				tempGrid[row][col].add(center);
			}

			// Step 2: For each cell, pick the center closest to cell's center
			grid = new Center[gridRows][gridCols];
			for (int row = 0; row < gridRows; row++)
			{
				for (int col = 0; col < gridCols; col++)
				{
					Point cellCenter = new Point(col * cellWidth + cellWidth / 2.0, row * cellHeight + cellHeight / 2.0);
					grid[row][col] = findClosestFromCandidates(tempGrid, row, col, cellCenter);
				}
			}
		}

		private Center findClosestFromCandidates(List<Center>[][] tempGrid, int row, int col, Point target)
		{
			Center closest = null;
			double closestDist = Double.MAX_VALUE;

			// Check 3x3 neighborhood around (row, col)
			for (int dr = -1; dr <= 1; dr++)
			{
				for (int dc = -1; dc <= 1; dc++)
				{
					int r = row + dr;
					int c = col + dc;
					if (r >= 0 && r < gridRows && c >= 0 && c < gridCols)
					{
						for (Center center : tempGrid[r][c])
						{
							double d = center.loc.distanceTo(target);
							if (d < closestDist)
							{
								closestDist = d;
								closest = center;
							}
						}
					}
				}
			}
			return closest;
		}

		Center getRepresentative(Point query)
		{
			int col = clamp((int) (query.x / cellWidth), 0, gridCols - 1);
			int row = clamp((int) (query.y / cellHeight), 0, gridRows - 1);
			return grid[row][col];
		}

		private int clamp(int value, int min, int max)
		{
			return Math.max(min, Math.min(max, value));
		}
	}

	public void buildCenterLookupTableIfNeeded()
	{
		synchronized (centerLookupLock)
		{
			if (centerLookupTable == null)
			{
				// Force CPU mode to avoid expensive GPU-to-CPU sync during incremental updates, although that only really matters in
				// RenderingMode.HYBRID mode.
				centerLookupTable = Image.create((int) bounds.width, (int) bounds.height, ImageType.RGB, true);
				try (Painter p = centerLookupTable.createPainter())
				{
					drawPolygons(p, new Function<Center, Color>()
					{
						public Color apply(Center c)
						{
							return convertCenterIdToColor(c);
						}
					});
				}
				// Create cached reader for the entire map
				cachedCenterLookupReader = centerLookupTable.createPixelReader();
			}
		}
	}

	/**
	 * Updates the center lookup table, which is used to lookup which center draws at a given point. This needs to be done when a center
	 * potentially changed its noisy edges, such as when it switched from inland to coast.
	 *
	 * @param centersToUpdate
	 *            Centers to update
	 */
	public void updateCenterLookupTable(Collection<Center> centersToUpdate)
	{
		// Clear and rebuild slice polygon cache for affected centers (used by grid-based lookup)
		clearSlicePolygonCache(centersToUpdate);
		precomputeSlicePolygonsForCenters(centersToUpdate);

		if (centerLookupMode == CenterLookupMode.PIXEL_CACHED || centerLookupMode == CenterLookupMode.PIXEL_UNCACHED)
		{
			synchronized (centerLookupLock)
			{
				if (centerLookupTable == null)
				{
					buildCenterLookupTableIfNeeded();
				}
				else
				{
					// Include neighbors of each center because if a center changed,
					// that will affect its neighbors as well.
					Set<Center> centersWithNeighbors = new HashSet<>();
					for (Center c : centersToUpdate)
					{
						centersWithNeighbors.add(c);
						for (Center neighbor : c.neighbors)
						{
							centersWithNeighbors.add(neighbor);
						}
					}

					try (Painter p = centerLookupTable.createPainter())
					{
						drawPolygons(p, centersWithNeighbors, new Function<Center, Color>()
						{
							public Color apply(Center c)
							{
								return convertCenterIdToColor(c);
							}
						});
					}

					if (centerLookupMode == CenterLookupMode.PIXEL_CACHED)
					{
						// Refresh the cached reader with the changed region
						Rectangle changedBounds = getBoundingBox(centersWithNeighbors);
						if (changedBounds != null)
						{
							cachedCenterLookupReader.refreshRegion(changedBounds.toEnclosingIntRectangle());
						}
					}
				}
			}
		}
	}

	private Color convertCenterIdToColor(Center c)
	{
		return Color.create(c.index & 0xff, (c.index & 0xff00) >> 8, (c.index & 0xff0000) >> 16);
	}

	/**
	 * Searches for any region touching and polygon in landMass and returns it if found. Otherwise returns null.
	 *
	 * Assumes all Centers in landMass either all have the same region, or are all null.
	 */
	private Region findRegionTouching(Set<Center> landMass)
	{
		for (Center center : landMass)
		{
			for (Center n : center.neighbors)
			{
				if (n.region != center.region && n.region != null)
				{
					return n.region;
				}
			}
		}
		return null;
	}

	/**
	 * Splits apart a region by parts connect by land (not including land from another region).
	 *
	 * @param region
	 * @return
	 */
	private List<Set<Center>> divideRegionByLand(Region region)
	{
		Set<Center> remaining = new HashSet<>(region.getCenters());
		List<Set<Center>> dividedRegion = new ArrayList<>();

		// Start with the first center. Do a breadth-first search adding all
		// connected
		// centers which are of the same region and are not ocean.
		while (!remaining.isEmpty())
		{
			Set<Center> landMass = breadthFirstSearch(c -> !c.isWater && c.region == region, remaining.iterator().next());
			dividedRegion.add(landMass);
			remaining.removeAll(landMass);
		}

		return dividedRegion;
	}

	/**
	 * Performs a breadth-first search starting from the given center, exploring all connected centers that satisfy the accept predicate.
	 *
	 * @param accept
	 *            A predicate that determines whether a neighboring center should be included in the search. Returns true if the center
	 *            should be explored, false otherwise.
	 * @param start
	 *            The center to begin the search from. This center is always included in the result, regardless of the accept predicate.
	 * @return A set containing the start center and all connected centers that satisfy the accept predicate.
	 */
	public Set<Center> breadthFirstSearch(Function<Center, Boolean> accept, Center start)
	{
		Set<Center> explored = new HashSet<>();
		explored.add(start);
		Set<Center> frontier = new HashSet<>();
		frontier.add(start);
		while (!frontier.isEmpty())
		{
			Set<Center> nextFrontier = new HashSet<>();
			for (Center c : frontier)
			{
				explored.add(c);
				// Add neighbors to the frontier.
				for (Center n : c.neighbors)
				{
					if (!explored.contains(n) && !frontier.contains(n) && accept.apply(n))
					{
						nextFrontier.add(n);
					}
				}
			}
			frontier = nextFrontier;
		}

		return explored;
	}

	/**
	 * Performs a breadth-first search to find the first center that satisfies the goal predicate.
	 *
	 * @param accept
	 *            A predicate that determines whether to explore a neighboring center. Takes three arguments: the current center being
	 *            expanded, the neighbor being considered, and the distance from the start (in number of hops). Returns true if the neighbor
	 *            should be added to the search frontier, false otherwise.
	 * @param isGoal
	 *            A predicate that determines whether a center is the goal. Returns true if the center satisfies the search criteria.
	 * @param start
	 *            The center to begin the search from.
	 * @return The first center found that satisfies the isGoal predicate, or null if no such center is reachable within the constraints of
	 *         the accept predicate.
	 */
	public Center breadthFirstSearchForGoal(TriFunction<Center, Center, Integer, Boolean> accept, Function<Center, Boolean> isGoal, Center start)
	{
		if (isGoal.apply(start))
		{
			return start;
		}

		Set<Center> explored = new HashSet<>();
		explored.add(start);
		Set<Center> frontier = new HashSet<>();
		frontier.add(start);
		int distanceFromStart = 1;
		while (!frontier.isEmpty())
		{
			Set<Center> nextFrontier = new HashSet<>();
			for (Center c : frontier)
			{
				if (isGoal.apply(c))
				{
					return c;
				}

				explored.add(c);
				// Add neighbors to the frontier.
				for (Center n : c.neighbors)
				{
					if (!explored.contains(n) && !frontier.contains(n) && accept.apply(c, n, distanceFromStart))
					{
						nextFrontier.add(n);
					}
				}
			}
			frontier = nextFrontier;
			distanceFromStart++;
		}

		return null;
	}

	public void paintElevationUsingTriangles(Painter p)
	{
		super.drawElevation(p);

		// Draw plate velocities.
		// g.setColor(Color.yellow);
		// for (TectonicPlate plate : plates)
		// {
		// Point centroid = plate.findCentroid();
		// g.fillOval((int)centroid.x - 5, (int)centroid.y - 5, 10, 10);
		// PolarCoordinate vTemp = new PolarCoordinate(plate.velocity);
		// // Increase the velocity to make it visible.
		// vTemp.radius *= 100;
		// Point velocity = vTemp.toCartesian();
		// g.drawLine((int)centroid.x, (int)centroid.y, (int)(centroid.x +
		// velocity.x), (int)(centroid.y + velocity.y));
		// }
	}

	public void drawBorderWhite(Painter p)
	{
		drawPolygons(p, c -> c.isBorder ? Color.white : Color.black);
	}

	public void drawLandAndOceanBlackAndWhite(Painter p, Collection<Center> centersToRender, Rectangle drawBounds)
	{
		drawPolygons(p, centersToRender, drawBounds, new Function<Center, Color>()
		{
			public Color apply(Center c)
			{
				return c.isWater ? Color.black : Color.white;
			}
		});
	}

	public void drawLandAndLakesBlackAndOceanWhite(Painter p, Collection<Center> centersToRender, Rectangle drawBounds)
	{
		if (centersToRender == null)
		{
			centersToRender = centers;
		}

		drawPolygons(p, centersToRender, drawBounds, new Function<Center, Color>()
		{
			public Color apply(Center c)
			{
				if (!c.isWater || c.isLake)
				{
					return Color.black;
				}
				return Color.white;
			}
		});
	}

	private void markLakes()
	{
		// This threshold allows me to distinguish between lakes and oceans.
		final int maxLakeSize = 120;

		Set<Center> explored = new HashSet<>();
		lakes = new ArrayList<>();
		for (Center center : centers)
		{
			if (!center.isWater)
			{
				continue;
			}

			if (explored.contains(center))
			{
				continue;
			}

			explored.add(center);

			Set<Center> potentialLake = breadthFirstSearch((c) ->
			{
				if (explored.contains(c))
				{
					return false;
				}

				explored.add(c);

				return c.isWater;
			}, center);

			// The second condition excludes lakes that touch the edge of the
			// map, since it's hard to tell whether those should be ocean or
			// lake,
			// And the more conservative choice is to say it's not a lake.
			if (potentialLake.size() <= maxLakeSize && !potentialLake.stream().anyMatch(c -> c.isBorder))
			{
				lakes.add(potentialLake);
				for (Center l : potentialLake)
				{
					l.isLake = true;
				}
			}
		}
	}

	public List<Set<Center>> getGeneratedLakes()
	{
		return lakes;
	}

	public int getWidth()
	{
		return (int) bounds.width;
	}

	public int getHeight()
	{
		return (int) bounds.height;
	}

	@Override
	protected Color getColor(Biome biome)
	{
		return biome.color;
	}

	@Override
	protected Biome getBiome(Center p)
	{
		double elevation = Math.sqrt((p.elevation - seaLevel) / (maxElevation - seaLevel));

		if (p.isWater)
		{
			return Biome.OCEAN;
		}
		else if (p.isWater)
		{
			if (elevation < 0.1)
			{
				return Biome.MARSH;
			}
			if (elevation > 0.8)
			{
				return Biome.ICE;
			}
			return Biome.LAKE;
		}
		else if (p.isCoast)
		{
			return Biome.BEACH;
		}
		else if (elevation > 0.8)
		{
			if (p.moisture > 0.50)
			{
				return Biome.SNOW;
			}
			else if (p.moisture > 0.33)
			{
				return Biome.TUNDRA;
			}
			else if (p.moisture > 0.16)
			{
				return Biome.BARE;
			}
			else
			{
				return Biome.SCORCHED;
			}
		}
		else if (elevation > 0.6)
		{
			if (p.moisture > 0.66)
			{
				return Biome.TAIGA;
			}
			else if (p.moisture > 0.33)
			{
				return Biome.SHRUBLAND;
			}
			else
			{
				return Biome.HIGH_TEMPERATE_DESERT;
			}
		}
		else if (elevation > 0.45)
		{
			// Note: I added this else if case. It is not in Red Blob's blog.
			if (p.moisture > 0.83)
			{
				return Biome.TEMPERATE_RAIN_FOREST;
			}
			else if (p.moisture > 0.50)
			{
				return Biome.HIGH_TEMPERATE_DECIDUOUS_FOREST;
			}
			else if (p.moisture > 0.16)
			{
				return Biome.GRASSLAND;
			}
			else
			{
				return Biome.TEMPERATE_DESERT;
			}
		}
		else if (elevation > 0.3)
		{
			if (p.moisture > 0.83)
			{
				return Biome.TEMPERATE_RAIN_FOREST;
			}
			else if (p.moisture > 0.50)
			{
				return Biome.TEMPERATE_DECIDUOUS_FOREST;
			}
			else if (p.moisture > 0.16)
			{
				return Biome.GRASSLAND;
			}
			else
			{
				return Biome.TEMPERATE_DESERT;
			}
		}
		else
		{
			if (p.moisture > 0.66)
			{
				return Biome.TROPICAL_RAIN_FOREST;
			}
			else if (p.moisture > 0.33)
			{
				return Biome.TROPICAL_SEASONAL_FOREST;
			}
			else if (p.moisture > 0.16)
			{
				return Biome.GRASSLAND;
			}
			else
			{
				return Biome.SUBTROPICAL_DESERT;
			}
		}
	}

	@Override
	protected void assignCornerElevations()
	{
		if (regionCount > 0)
		{
			createTectonicPlatesForRegionCount();
		}
		else
		{
			createTectonicPlates();
			assignOceanAndContinentalPlates();
		}
		lowerOceanPlates();
		assignPlateCornerElevations();
	}

	private void assignPlateCornerElevations()
	{
		// long startTime = System.currentTimeMillis();

		for (final TectonicPlate plate : plates)
		{

			Set<Corner> explored = new HashSet<>();

			// Find all corners along plate boundaries.
			Set<Corner> plateBoundaryCorners = new HashSet<>();
			for (Edge e : edges)
			{
				if (e.d0.tectonicPlate == plate && e.d0.tectonicPlate != e.d1.tectonicPlate && e.v0 != null && e.v1 != null)
				{
					if (e.v0 != null)
						plateBoundaryCorners.add(e.v0);
					if (e.v1 != null)
						plateBoundaryCorners.add(e.v1);
				}
			}

			// Simulate tectonic plate collisions.
			for (Edge e : edges)
			{
				if (e.d0.tectonicPlate == plate && e.d0.tectonicPlate != e.d1.tectonicPlate && e.v0 != null && e.v1 != null)
				{
					double d0ConvergeLevel = calcLevelOfConvergence(e.d0.tectonicPlate.findCentroid(), e.d0.tectonicPlate.velocity, e.d1.tectonicPlate.findCentroid(), e.d1.tectonicPlate.velocity);

					// If the plates are converging, rough them up a bit by
					// calculating divergence per
					// polygon. This brakes up long snake like islands.
					if (d0ConvergeLevel > 0)
					{
						d0ConvergeLevel = calcLevelOfConvergence(e.d0.loc, e.d0.tectonicPlate.velocity, e.d1.loc, e.d1.tectonicPlate.velocity);
					}

					e.v0.elevation += d0ConvergeLevel * collisionScale;
					e.v1.elevation += d0ConvergeLevel * collisionScale;
					explored.add(e.v0);
					explored.add(e.v1);

					// Make sure the corner elevations don't go out of range.
					e.v0.elevation = Math.min(e.v0.elevation, 1.0);
					e.v0.elevation = Math.max(e.v0.elevation, 0.0);
					e.v1.elevation = Math.min(e.v1.elevation, 1.0);
					e.v1.elevation = Math.max(e.v1.elevation, 0.0);

					// Handle subduction of an ocean plate under a continental
					// one.
					if (d0ConvergeLevel > 0 && e.d0.tectonicPlate.type == PlateType.Oceanic && e.d1.tectonicPlate.type == PlateType.Continental)
					{
						for (Corner corner : e.d0.corners)
						{
							if (!plateBoundaryCorners.contains(corner))
							{
								corner.elevation -= d0ConvergeLevel * collisionScale;
								corner.elevation = Math.min(corner.elevation, 1.0);
								corner.elevation = Math.max(corner.elevation, 0.0);
								explored.add(corner);
							}
						}
					}
				}
			}

			// Do a search starting at the corners along the borders. At each
			// step, assign each corner's
			// elevation to the average of its self and its explored neighbors.
			boolean cornerFound;
			Set<Corner> exploredThisIteration = new HashSet<>();
			do
			{
				cornerFound = false;
				explored.addAll(exploredThisIteration);
				exploredThisIteration.clear();
				for (Corner exCorner : explored)
				{
					for (Corner corner : exCorner.adjacent)
					{
						if (!explored.contains(corner) && !exploredThisIteration.contains(corner))
						{

							for (Center center : corner.touches)
								if (center.tectonicPlate == plate)
								{
									// Set the corner's elevation to the average
									// of its self and its
									// explored neighbors.
									double sum = corner.elevation;
									double count = 1;
									for (Corner a : corner.adjacent)
										if (explored.contains(a) || exploredThisIteration.contains(a))
										{
											sum += a.elevation;
											count++;
										}
									corner.elevation = sum / count;

									exploredThisIteration.add(corner);
									cornerFound = true;
									continue;
								}
						}
					}
				}
			}
			while (cornerFound);

		}
	}

	/**
	 * Lower the elevation of all ocean plates so they are likely to be water.
	 */
	private void lowerOceanPlates()
	{
		for (Corner corner : corners)
		{
			int numOceanic = 0;
			for (Center center : corner.touches)
			{
				if (center.tectonicPlate.type == PlateType.Oceanic)
					numOceanic++;
			}
			double oceanicRatio = ((double) numOceanic) / corner.touches.size();
			corner.elevation = oceanicRatio * oceanPlateLevel + (1.0 - oceanicRatio) * continentalPlateLevel;
		}
	}

	@Override
	protected void assignOceanCoastAndLand()
	{
		for (Center c1 : centers)
		{
			c1.isWater = c1.elevation < seaLevel;
		}

		for (Center c : centers)
		{
			c.updateCoast();
		}

		// Copied from super.assignOceanCoastAndLand()
		// Determine if each corner is ocean, coast, or water.
		for (Corner c : corners)
		{
			int numOcean = 0;
			int numLand = 0;
			for (Center center : c.touches)
			{
				numOcean += (center.isWater && !center.isLake) ? 1 : 0;
				numLand += !center.isWater ? 1 : 0;
			}
			c.isOcean = numOcean == c.touches.size();
			c.isCoast = numOcean > 0 && numLand > 0;
			c.isWater = (numLand != c.touches.size()) && !c.isCoast;
		}
	}

	private void assignOceanAndContinentalPlates()
	{
		for (TectonicPlate plate : plates)
		{
			if (rand.nextDouble() > nonBorderPlateContinentalProbability)
			{
				plate.type = PlateType.Oceanic;
			}
			else
			{
				plate.type = PlateType.Continental;
			}
		}

		// Set the type for plates that touch the borders, overwriting any
		// settings from above.
		Set<TectonicPlate> borderPlates = new HashSet<TectonicPlate>();
		for (Center c : centers)
		{
			for (Corner corner : c.corners)
				if (corner.isBorder)
				{
					borderPlates.add(c.tectonicPlate);
					continue;
				}
		}
		for (TectonicPlate plate : borderPlates)
		{
			if (rand.nextDouble() < borderPlateContinentalProbability)
				plate.type = PlateType.Continental;
			else
				plate.type = PlateType.Oceanic;
		}
	}

	private double distFromNearestEdge(Point p)
	{
		double distLeft = p.x;
		double distRight = bounds.width - p.x;
		double distTop = p.y;
		double distBottom = bounds.height - p.y;
		return Math.min(Math.min(distLeft, distRight), Math.min(distTop, distBottom));
	}

	private void createTectonicPlatesForRegionCount()
	{
		int oceanicPlateCount = Math.max(regionCount, 4);
		int totalPlates = regionCount + oceanicPlateCount;

		// Step 1: Generate well-spaced seed points using Mitchell's best-candidate algorithm.
		// This is deterministic given the same Random instance.
		ArrayList<Point> seedPoints = new ArrayList<>();
		int candidatesPerPoint = 20;
		for (int i = 0; i < totalPlates; i++)
		{
			Point best = null;
			double bestDist = -1;
			for (int c = 0; c < candidatesPerPoint; c++)
			{
				Point candidate = new Point(rand.nextDouble() * bounds.width, rand.nextDouble() * bounds.height);
				double minDist = Double.MAX_VALUE;
				for (Point existing : seedPoints)
				{
					minDist = Math.min(minDist, candidate.distanceTo(existing));
				}
				if (seedPoints.isEmpty() || minDist > bestDist)
				{
					bestDist = minDist;
					best = candidate;
				}
			}
			seedPoints.add(best);
		}

		// Step 2: Assign continental vs oceanic based on LandShape.
		// Sort by distance from nearest edge (ascending).
		Integer[] indicesByEdgeDist = new Integer[totalPlates];
		for (int i = 0; i < totalPlates; i++)
		{
			indicesByEdgeDist[i] = i;
		}
		final ArrayList<Point> finalSeedPoints = seedPoints;
		Arrays.sort(indicesByEdgeDist, (a, b) ->
		{
			double da = distFromNearestEdge(finalSeedPoints.get(a));
			double db = distFromNearestEdge(finalSeedPoints.get(b));
			return Double.compare(da, db);
		});

		boolean[] isContinental = new boolean[totalPlates];
		if (landShape == null || landShape == LandShape.Continents)
		{
			// Farthest from edges are continental
			for (int i = totalPlates - regionCount; i < totalPlates; i++)
			{
				isContinental[indicesByEdgeDist[i]] = true;
			}
		}
		else if (landShape == LandShape.Inland_Sea)
		{
			// Closest to edges are continental
			for (int i = 0; i < regionCount; i++)
			{
				isContinental[indicesByEdgeDist[i]] = true;
			}
		}
		else
		{
			// Scattered: randomly choose regionCount to be continental
			List<Integer> allIndices = new ArrayList<>();
			for (int i = 0; i < totalPlates; i++)
			{
				allIndices.add(i);
			}
			Collections.shuffle(allIndices, rand);
			for (int i = 0; i < regionCount; i++)
			{
				isContinental[allIndices.get(i)] = true;
			}
		}

		// Step 3: Create TectonicPlates and assign growth weights.
		List<TectonicPlate> plateList = new ArrayList<>();
		for (int i = 0; i < totalPlates; i++)
		{
			double growthWeight = 0.7 + rand.nextDouble() * 0.6;
			TectonicPlate plate = new TectonicPlate(growthWeight);
			plate.type = isContinental[i] ? PlateType.Continental : PlateType.Oceanic;
			plateList.add(plate);
		}

		// Step 4: Map seed points to closest Centers in main graph.
		Center[] seedCenters = new Center[totalPlates];
		Set<Center> usedCenters = new HashSet<>();
		for (int i = 0; i < totalPlates; i++)
		{
			Point seed = seedPoints.get(i);
			Center closest = null;
			double closestDist = Double.MAX_VALUE;
			for (Center c : centers)
			{
				if (usedCenters.contains(c))
				{
					continue;
				}
				double dist = c.loc.distanceTo(seed);
				if (dist < closestDist)
				{
					closestDist = dist;
					closest = c;
				}
			}
			seedCenters[i] = closest;
			usedCenters.add(closest);
			closest.tectonicPlate = plateList.get(i);
			plateList.get(i).centers.add(closest);
		}

		// Step 5: BFS expansion using priority queue (Dijkstra-like).
		// For Continents mode, continental plates pay an extra cost to grow near map edges,
		// which naturally shapes them away from borders.
		boolean biasAwayFromEdges = landShape == null || landShape == LandShape.Continents;
		double edgeBiasDistance = Math.min(bounds.width, bounds.height) * 0.15;

		PriorityQueue<double[]> frontier = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
		for (int i = 0; i < totalPlates; i++)
		{
			// [cost, centerIndex, plateIndex]
			frontier.add(new double[] { 0, seedCenters[i].index, i });
		}

		while (!frontier.isEmpty())
		{
			double[] entry = frontier.poll();
			double cost = entry[0];
			int centerIdx = (int) entry[1];
			int plateIdx = (int) entry[2];

			Center center = centers.get(centerIdx);
			if (center.tectonicPlate != null && center != seedCenters[plateIdx])
			{
				continue; // Already assigned
			}

			if (center.tectonicPlate == null)
			{
				center.tectonicPlate = plateList.get(plateIdx);
				plateList.get(plateIdx).centers.add(center);
			}

			for (Center neighbor : center.neighbors)
			{
				if (neighbor.tectonicPlate == null)
				{
					double baseCost = 1.0 / plateList.get(plateIdx).growthProbability;

					// In Continents mode, make continental plates reluctant to grow near edges.
					if (biasAwayFromEdges && plateList.get(plateIdx).type == PlateType.Continental)
					{
						double edgeDist = distFromNearestEdge(neighbor.loc);
						if (edgeDist < edgeBiasDistance)
						{
							// The closer to the edge, the higher the penalty. Ranges from 1x (at the
							// threshold) to 5x (at the edge itself).
							double edgeFraction = 1.0 - edgeDist / edgeBiasDistance;
							baseCost *= 1.0 + 4.0 * edgeFraction;
						}
					}

					double newCost = cost + baseCost;
					frontier.add(new double[] { newCost, neighbor.index, plateIdx });
				}
			}
		}

		// Step 6: Set plate velocities and store plates.
		plates = new HashSet<>();
		for (TectonicPlate plate : plateList)
		{
			plate.velocity = new PolarCoordinate(rand.nextDouble() * 2 * Math.PI, rand.nextDouble());
			plates.add(plate);
		}
	}

	private void createTectonicPlates()
	{
		// First, assign a unique plate id and a random growth probability to
		// each center.
		RandomGenerator randomData = new JDKRandomGenerator();
		randomData.setSeed(rand.nextLong());

		// Maps tectonic plates to the number of centers in that plate.
		HashMap<TectonicPlate, Integer> plateCounts = new HashMap<>(centers.size());

		// A beta distribution is nice because (with the parameters I use) it
		// creates a few plates
		// with high growth probabilities and many with low growth
		// probabilities. This makes plate creation
		// faster and creates a larger variety of plate sizes than a uniform
		// distribution would.
		BetaDistribution betaDist = new BetaDistribution(randomData, 1, 3, BetaDistribution.DEFAULT_INVERSE_ABSOLUTE_ACCURACY);
		for (Center c : centers)
		{
			c.tectonicPlate = new TectonicPlate(betaDist.sample());
			plateCounts.put(c.tectonicPlate, 1);
		}

		for (Center c : centers)
		{
			c.updateNeighborsNotInSamePlateCount();
		}

		int numIterationsForTectonicPlateCreation = tectonicPlateIterationMultiplier * centers.size();
		for (int curIteration = 0; curIteration < numIterationsForTectonicPlateCreation; curIteration++)
		{
			// Sample some centers and choose the one with the least number of
			// neighbors which
			// are not on this plate (greater than 0). This makes the plate
			// boundaries more smooth.
			Center least = null;
			for (int i = 0; i < plateBoundarySmoothness; i++)
			{
				final Center cTemp = centers.get(rand.nextInt(centers.size()));
				if (cTemp.neighborsNotInSamePlateRatio == 0)
					continue;

				if (least == null || cTemp.neighborsNotInSamePlateRatio < least.neighborsNotInSamePlateRatio)
				{
					least = cTemp;
				}

			}
			if (least == null)
			{
				continue;
			}
			final Center c = least;

			// Keep the merge with probability equal to the score of the new
			// tectonic plate.
			if (rand.nextDouble() < c.tectonicPlate.growthProbability)
			{
				// Choose a center at random.
				// Choose one of it's neighbors not in the same plate.
				List<Center> neighborsNotInSamePlate = Helper.filter(c.neighbors, otherC -> c.tectonicPlate != otherC.tectonicPlate);
				Center neighbor = neighborsNotInSamePlate.get(rand.nextInt(neighborsNotInSamePlate.size()));

				plateCounts.put(c.tectonicPlate, plateCounts.get(c.tectonicPlate) + 1);
				plateCounts.put(neighbor.tectonicPlate, plateCounts.get(neighbor.tectonicPlate) - 1);
				if (plateCounts.get(neighbor.tectonicPlate) == 0)
				{
					plateCounts.remove(neighbor.tectonicPlate);
				}

				// Merge the neighbor into c's plate.
				neighbor.tectonicPlate = c.tectonicPlate;

				c.updateNeighborsNotInSamePlateCount();
				for (Center n : c.neighbors)
					n.updateNeighborsNotInSamePlateCount();
				neighbor.updateNeighborsNotInSamePlateCount();
				for (Center n : neighbor.neighbors)
					n.updateNeighborsNotInSamePlateCount();

				// Stop if there are only nine plates left and one of them is
				// getting too small. This will usually prevent
				// creating a map this just ocean or has only tiny islands,
				// although it isn't guaranteed since it's
				// possible all 9 plates will be assigned to oceanic.
				if (plateCounts.keySet().size() == 9 && Helper.minElement(plateCounts) <= minNinthtoLastPlateSize)
				{
					break;
				}
			}
		}

		// Find the plates still on the map.
		plates = new HashSet<TectonicPlate>();
		for (Center c : centers)
		{
			plates.add(c.tectonicPlate);
		}

		// Setup tectonic plate velocities randomly. The maximum speed of a
		// plate is 1.
		for (TectonicPlate plate : plates)
		{
			plate.velocity = new PolarCoordinate(rand.nextDouble() * 2 * Math.PI, rand.nextDouble());
		}

		// Store which Centers are in each plate.
		for (Center c : centers)
		{
			c.tectonicPlate.centers.add(c);
		}
	}

	/**
	 * Returns the amount the centers p1 and p2 are from are converging. This is between -1 and 1.
	 *
	 * @param p1
	 *            Location of a center along a tectonic plate border.
	 * @param p1Velocity
	 *            The velocity of the plate p1 is on.
	 * @param p2
	 *            Location of a center along a tectonic plate border: not the same tectonic plate as p1
	 * @param p2Velocity
	 *            The velocity of the plate p2 is on.
	 */
	private double calcLevelOfConvergence(Point p1, PolarCoordinate p1Velocity, Point p2, PolarCoordinate p2Velocity)
	{
		return 0.5 * calcUnilateralLevelOfConvergence(p1, p1Velocity, p2) + 0.5 * calcUnilateralLevelOfConvergence(p2, p2Velocity, p1);
	}

	/**
	 * Returns the amount c1 is moving toward c2, ignoring movement of c2.
	 */
	public static double calcUnilateralLevelOfConvergence(Point p1, PolarCoordinate p1Velocity, Point p2)
	{
		// Find the angle from c1.location to c2.location:
		Point cDiff = p2.subtract(p1);
		double diffAngle = Math.atan2(cDiff.y, cDiff.x);

		// If the 2 angles are the same, return at most 0.5. If they are
		// opposite, return at least -0.5.
		// If they are orthogonal, return 0.0.

		double theta = calcAngleDifference(p1Velocity.angle, diffAngle);
		double scale = p1Velocity.radius * Math.cos(theta);
		return scale;
	}

	/**
	 * Calculates the minimum distance (in radians) from angle a1 to angle a2. The result will be in the range [0, pi].
	 *
	 * @param a1
	 *            An angle in radians. This must be between 0 and 2*pi.
	 * @param a2
	 *            An angle in radians. This must be between 0 and 2*pi.
	 * @return
	 */
	private static double calcAngleDifference(double a1, double a2)
	{
		while (a1 < 0)
			a1 += 2 * Math.PI;
		while (a2 < 0)
			a2 += 2 * Math.PI;

		if (a1 < 0 || a1 > 2 * Math.PI)
			throw new IllegalArgumentException();
		if (a2 < 0 || a2 > 2 * Math.PI)
			throw new IllegalArgumentException();

		if (a1 - a2 > Math.PI)
			a1 -= 2 * Math.PI;
		else if (a2 - a1 > Math.PI)
			a2 -= 2 * Math.PI;
		double result = Math.abs(a1 - a2);
		assert result <= Math.PI;
		return result;
	}

	public static Point findCentroid(Set<Center> centers)
	{
		Point centroid = new Point(0, 0);
		for (Center c : centers)
		{
			Point p = c.loc;
			centroid.x += p.x;
			centroid.y += p.y;
		}
		centroid.x /= centers.size();
		centroid.y /= centers.size();

		return centroid;
	}

	public static Rectangle getBoundingBox(Collection<Center> centers)
	{
		if (centers.size() == 0)
		{
			return null;
		}

		// Start at a point we know is inside the desired bounds.
		Point start = centers.iterator().next().loc;
		Rectangle bounds = new Rectangle(start.x, start.y, 0, 0);

		// Add each corner to the bounds.
		for (Center center : centers)
		{

			// Use the centroid of the neighbors instead of this center's own
			// corners because noisy
			// edges/curves can extend beyond this center.
			for (Center neighbor : center.neighbors)
			{
				bounds = bounds.add(neighbor.loc);
			}

			// For centers on the edge of the map, add the corners to the bounds
			// because the center doesn't have neighbors in all
			// directions.
			if (center.isBorder)
			{
				for (Corner corner : center.corners)
				{
					bounds = bounds.add(corner.loc);
				}
			}
		}

		return bounds;
	}

	/**
	 * Greedily finds a path between the 2 given corners using Voronoi edges.
	 */
	public Set<Edge> findPathGreedy(Corner start, Corner end)
	{
		if (start.equals(end))
		{
			return new HashSet<>();
		}

		Set<CornerSearchNode> explored = new HashSet<>();
		CornerSearchNode startNode = new CornerSearchNode(start, null);
		explored.add(startNode);
		SortedSet<CornerSearchNode> frontier = new TreeSet<>(new Comparator<CornerSearchNode>()
		{
			@Override
			public int compare(CornerSearchNode n1, CornerSearchNode n2)
			{
				int distanceComp = Double.compare(end.loc.distanceTo(n1.corner.loc), end.loc.distanceTo(n2.corner.loc));
				if (distanceComp == 0)
				{
					// This ensures corners which are the same distance to the
					// target don't clobber each other in the set.
					return Integer.compare(n1.corner.index, n2.corner.index);
				}
				assert n1.corner.index != n2.corner.index;
				return distanceComp;
			}
		});

		expandFrontier(startNode, frontier, explored);

		CornerSearchNode endNode = null;
		while (true)
		{
			CornerSearchNode closest = frontier.first();
			frontier.remove(closest);
			explored.add(closest);

			if (closest.corner.equals(end))
			{
				endNode = closest;
				break;
			}

			expandFrontier(closest, frontier, explored);
		}

		return createPathFromBackPointers(endNode);
	}

	/**
	 * Expands the frontier using Voronoi edges
	 */
	private void expandFrontier(CornerSearchNode node, Set<CornerSearchNode> frontier, Set<CornerSearchNode> explored)
	{
		for (Corner c : node.corner.adjacent)
		{
			CornerSearchNode otherNode = new CornerSearchNode(c, node);
			if (!explored.contains(otherNode) && !frontier.contains(otherNode))
			{
				frontier.add(otherNode);
			}
		}
	}

	private Edge findConnectingEdge(Corner c1, Corner c2)
	{
		for (Edge edge : c1.protrudes)
		{
			if (edge.v1 != null && edge.v1.equals(c2))
			{
				return edge;
			}
			if (edge.v0 != null && edge.v0.equals(c2))
			{
				return edge;
			}
		}
		return null;
	}

	/**
	 * Create path using back pointers in search does for Voronoi edges.
	 *
	 * @param end
	 *            The end of the search
	 * @return A path
	 */
	private Set<Edge> createPathFromBackPointers(CornerSearchNode end)
	{
		Set<Edge> path = new HashSet<>();
		if (end == null)
		{
			return path;
		}

		CornerSearchNode curNode = end;
		while (curNode.cameFrom != null)
		{
			Edge edge = findConnectingEdge(curNode.corner, curNode.cameFrom.corner);
			assert edge != null;
			path.add(edge);
			curNode = curNode.cameFrom;
		}
		return path;
	}

	private class CornerSearchNode
	{
		public Corner corner; // for searching Voronoi edges
		public CornerSearchNode cameFrom;

		public CornerSearchNode(Corner corner, CornerSearchNode cameFrom)
		{
			this.corner = corner;
			this.cameFrom = cameFrom;
		}

		@Override
		public int hashCode()
		{
			return corner.hashCode();
		}

		@Override
		public boolean equals(Object other)
		{
			CornerSearchNode otherNode = (CornerSearchNode) other;
			if (otherNode.corner != null)
			{
				return corner.equals(otherNode.corner);
			}
			return corner == null;
		}
	}

	/**
	 * Uses A* search to find the shortest path between the 2 given centers using Delaunay edges.
	 *
	 * @param start
	 *            Where to begin the search
	 * @param end
	 *            The goal
	 * @param calculateWeight
	 *            Finds the weight of an edge for determining whether to explore it. This should be the weight of it the Delaunay edge.
	 *            Likely this will be calculated based on the distance from one end of the Delaunay age
	 * @return A path if one is found; null if the and is unreachable from the start.
	 */
	public List<Edge> findShortestPath(Center start, Center end, TriFunction<Edge, Center, Double, Double> calculateWeight)
	{
		PriorityQueue<CenterSearchNode> explored = new PriorityQueue<>((n1, n2) -> Double.compare(n1.predictedScore, n2.predictedScore));
		// Maps from centers we have seen to their nodes, to allow fast lookup
		// of scores of previously seen centers.
		Map<Center, CenterSearchNode> centerNodeMap = new HashMap<>();

		explored.add(new CenterSearchNode(start, null, 0, Center.distanceBetween(start, end)));
		centerNodeMap.put(start, explored.peek());

		while (!explored.isEmpty())
		{
			CenterSearchNode current = explored.poll();
			if (current.center.equals(end))
			{
				// The score so far doesn't matter for this case
				return createPathFromBackPointers(current);
			}

			for (Edge edge : current.center.borders)
			{
				Center neighbor = current.center.equals(edge.d0) ? edge.d1 : edge.d0;
				if (neighbor != null)
				{
					double scoreFromStartToNeighbor = current.scoreSoFar + calculateWeight.apply(edge, neighbor, Center.distanceBetween(current.center, end));
					double neighborCurrentScore = centerNodeMap.containsKey(neighbor) ? centerNodeMap.get(neighbor).scoreSoFar : Float.POSITIVE_INFINITY;
					if (scoreFromStartToNeighbor < neighborCurrentScore)
					{
						CenterSearchNode neighborNode = new CenterSearchNode(neighbor, current, scoreFromStartToNeighbor, scoreFromStartToNeighbor);

						centerNodeMap.put(neighbor, neighborNode);
						explored.add(neighborNode);
					}
				}
			}
		}

		// The end is not reachable from the start
		return null;
	}

	public void drawVoronoi(Painter p, Collection<Center> centersToDraw, Rectangle drawBounds, boolean onlyLand)
	{
		Transform orig = null;
		if (drawBounds != null)
		{
			orig = p.getTransform();
			p.translate(-drawBounds.x, -drawBounds.y);
		}

		Collection<Corner> cornersToDraw = centersToDraw == null ? corners : getCornersFromCenters(centersToDraw);
		for (Corner c : cornersToDraw)
		{
			for (Corner adjacent : c.adjacent)
			{
				Edge e = findConnectingEdge(c, adjacent);
				if (onlyLand && (e.isWater() || e.isCoastOrLakeShore()))
				{
					continue;
				}
				p.drawLine((int) c.loc.x, (int) c.loc.y, (int) adjacent.loc.x, (int) adjacent.loc.y);
			}
		}

		if (drawBounds != null)
		{
			p.setTransform(orig);
		}
	}

	public Edge findConnectingEdge(Center c1, Center c2)
	{
		for (Edge edge : c1.borders)
		{
			if (edge.d1 != null && edge.d1.equals(c2))
			{
				return edge;
			}
			if (edge.d0 != null && edge.d0.equals(c2))
			{
				return edge;
			}
		}
		return null;
	}

	/**
	 * Create path using back pointers in search does for Voronoi edges.
	 *
	 * @param end
	 *            The end of the search
	 * @return A path
	 */
	private List<Edge> createPathFromBackPointers(CenterSearchNode end)
	{
		List<Edge> path = new ArrayList<>();
		if (end == null)
		{
			return path;
		}

		CenterSearchNode curNode = end;
		while (curNode.cameFrom != null)
		{
			Edge edge = findConnectingEdge(curNode.center, curNode.cameFrom.center);
			assert edge != null;
			path.add(edge);
			curNode = curNode.cameFrom;
		}
		return path;
	}

	private class CenterSearchNode
	{
		public Center center; // for searching Delaunay edges
		public CenterSearchNode cameFrom;
		public double scoreSoFar;
		public double predictedScore;

		public CenterSearchNode(Center center, CenterSearchNode cameFrom, double scoreSoFar, double predictedScore)
		{
			this.center = center;
			this.cameFrom = cameFrom;
			this.scoreSoFar = scoreSoFar;
			this.predictedScore = predictedScore;
		}

		@Override
		public int hashCode()
		{
			return center.hashCode();
		}

		@Override
		public boolean equals(Object other)
		{
			CenterSearchNode otherNode = (CenterSearchNode) other;
			if (otherNode.center != null)
			{
				return center.equals(otherNode.center);
			}
			return center == null;
		}
	}

	/**
	 * Scales, rotates, and flips the graph and everything in it.
	 */
	public void scaleFlipAndRotate(double targetWidth, double targetHeight, int rightRotationCount, boolean flipHorizontally, boolean flipVertically)
	{
		double widthScale = targetWidth / bounds.width;
		double heightScale = targetHeight / bounds.height;
		double angle = (Math.PI / 2.0) * rightRotationCount;
		Point mapCenter = new Point(targetWidth / 2.0, targetHeight / 2.0);
		Point newOriginOffset;
		assert rightRotationCount <= 3;
		assert rightRotationCount >= 0;

		if (rightRotationCount == 0)
		{
			newOriginOffset = new Point(0, 0);
		}
		else if (rightRotationCount == 1)
		{
			// The lower-left corner will become the new origin.
			newOriginOffset = new Point(0, targetHeight).rotate(mapCenter, angle);
		}
		else if (rightRotationCount == 2)
		{
			// The lower-right corner will become the new origin.
			newOriginOffset = new Point(targetWidth, targetHeight).rotate(mapCenter, angle);
		}
		else
		{
			// The upper-right corner will become the new origin.
			newOriginOffset = new Point(targetWidth, 0).rotate(mapCenter, angle);
		}

		for (Center center : centers)
		{
			center.loc = scaleFlipAndRotatePoint(center.loc, widthScale, heightScale, angle, mapCenter, newOriginOffset, flipHorizontally, flipVertically);
		}
		for (Edge edge : edges)
		{
			if (edge.midpoint != null)
			{
				edge.midpoint = scaleFlipAndRotatePoint(edge.midpoint, widthScale, heightScale, angle, mapCenter, newOriginOffset, flipHorizontally, flipVertically);
			}
		}
		for (Corner corner : corners)
		{
			corner.loc = scaleFlipAndRotatePoint(corner.loc, widthScale, heightScale, angle, mapCenter, newOriginOffset, flipHorizontally, flipVertically);
			if (corner.originalLoc != null)
			{
				corner.originalLoc = scaleFlipAndRotatePoint(corner.originalLoc, widthScale, heightScale, angle, mapCenter, newOriginOffset, flipHorizontally, flipVertically);
			}
		}

		if (rightRotationCount == 1 || rightRotationCount == 3)
		{
			bounds = new Rectangle(0, 0, targetHeight, targetWidth);
		}
		else
		{
			bounds = new Rectangle(0, 0, targetWidth, targetHeight);
		}
		meanCenterWidth = null;
		getMeanCenterWidth();
		meanCenterWidthBetweenNeighbors = null;
		getMeanCenterWidthBetweenNeighbors();
	}

	private Point scaleFlipAndRotatePoint(Point point, double widthScale, double heightScale, double angle, Point mapCenter, Point newOriginOffset, boolean flipHorizontally, boolean flipVertically)
	{
		Point result = point.mult(widthScale, heightScale);

		if (flipHorizontally)
		{
			result = new Point(2 * mapCenter.x - result.x, result.y);
		}

		if (flipVertically)
		{
			result = new Point(result.x, 2 * mapCenter.y - result.y);
		}

		if (angle != 0.0)
		{
			result = result.rotate(mapCenter, angle).subtract(newOriginOffset);
		}

		return result;
	}

	public double getMeanCenterWidth()
	{
		if (meanCenterWidth == null)
		{
			meanCenterWidth = findMeanCenterWidth();
		}
		return meanCenterWidth;
	}

	public double getMeanCenterWidthBetweenNeighbors()
	{
		if (meanCenterWidthBetweenNeighbors == null)
		{
			meanCenterWidthBetweenNeighbors = findMeanCenterWidthBetweenNeighbors();
		}
		return meanCenterWidthBetweenNeighbors;
	}

	private double findMeanCenterWidth()
	{
		double widthSum = 0;
		int count = 0;
		for (Center center : centers)
		{
			double width = center.findWidth();

			if (width > 0)
			{
				count++;
				widthSum += width;
			}
		}

		return widthSum / count;
	}

	private double findMeanCenterWidthBetweenNeighbors()
	{
		double sum = 0;
		for (Center c : centers)
		{
			sum += findCenterWidthBetweenNeighbors(c);
		}
		return sum / centers.size();
	}

	public double findCenterWidthBetweenNeighbors(Center c)
	{
		if (c.neighbors == null || c.neighbors.isEmpty())
		{
			// I hit a crash somehow where c.neighbors was empty, so I'm being extra safe and handling it here.
			return 0.0;
		}

		Center eastMostNeighbor = Collections.max(c.neighbors, new Comparator<Center>()
		{
			public int compare(Center c1, Center c2)
			{
				return Double.compare(c1.loc.x, c2.loc.x);
			}
		});
		Center westMostNeighbor = Collections.min(c.neighbors, new Comparator<Center>()
		{
			public int compare(Center c1, Center c2)
			{
				return Double.compare(c1.loc.x, c2.loc.x);
			}
		});
		double cSize = Math.abs(eastMostNeighbor.loc.x - westMostNeighbor.loc.x);
		return cSize;
	}

	public void drawCoastlineWithVariation(Painter p, long randomSeed, double variationRange, double widthBetweenWaves, boolean addRandomBreaks, Rectangle drawBounds,
			BiFunction<Boolean, Random, Double> getNewSkipDistance, List<List<Edge>> shoreEdges)
	{
		Transform orig = null;
		if (drawBounds != null)
		{
			orig = p.getTransform();
			p.translate(-drawBounds.x, -drawBounds.y);
		}

		for (List<Edge> coastline : shoreEdges)
		{
			if (coastline.size() == 0)
			{
				assert false;
				continue;
			}

			final double maxDistanceToIgnoreNoisyEdgesWhenCoastlinesUseJaggedLines = widthBetweenWaves * 0.5;
			boolean isPolygon = false;
			// Use a random seed that is unique per coastline.
			Random rand = new Random(randomSeed + coastline.get(0).index);

			List<Point> drawPoints;
			if (variationRange > 0)
			{
				// Get the path without curves, except cases where jagged lines
				// might overlap with waves when the coastlines use curves.
				if (noisyEdges.getLineStyle() == LineStyle.Jagged)
				{
					drawPoints = edgeListToDrawPoints(coastline, true, maxDistanceToIgnoreNoisyEdgesWhenCoastlinesUseJaggedLines);
				}
				else
				{
					// Always ignore noisy edges because we will add the curves
					// after adding the variance.
					drawPoints = edgeListToDrawPoints(coastline, true, Double.MAX_VALUE);
				}
				isPolygon = drawPoints.size() > 2 && drawPoints.get(0).equals(drawPoints.get(drawPoints.size() - 1));
				if (!isPolygon)
				{
					addPointsOffMapToMakeWavesGoToEdgeOfMap(drawPoints);
				}
				drawPoints = addJitter(randomSeed, drawPoints, variationRange);
			}
			else
			{
				drawPoints = edgeListToDrawPoints(coastline, true, maxDistanceToIgnoreNoisyEdgesWhenCoastlinesUseJaggedLines);
				isPolygon = drawPoints.size() > 2 && drawPoints.get(0).equals(drawPoints.get(drawPoints.size() - 1));
				if (!isPolygon)
				{
					addPointsOffMapToMakeWavesGoToEdgeOfMap(drawPoints);
				}
			}

			// When drawing concentric waves with random variation, we need more
			// points in the curve at lower resolutions to make it look
			// good.
			double distanceBetweenPoints = Math.max(1.0, Math.min(CurveCreator.defaultDistanceBetweenPoints, CurveCreator.defaultDistanceBetweenPoints * resolutionScale));
			drawPoints = CurveCreator.createCurve(drawPoints, distanceBetweenPoints);

			if (drawPoints == null || drawPoints.size() <= 1)
			{
				continue;
			}

			if (addRandomBreaks)
			{
				List<List<Point>> drawPointsWithBreaks = addRandomBreaks(rand, drawPoints, getNewSkipDistance);
				for (List<Point> points : drawPointsWithBreaks)
				{
					if (isPolygon && !addRandomBreaks)
					{
						p.drawPolygon(points);
					}
					else
					{
						drawPolyline(p, points);
					}
				}
			}
			else
			{
				if (isPolygon && !addRandomBreaks)
				{
					p.drawPolygon(drawPoints);
				}
				else
				{
					drawPolyline(p, drawPoints);
				}
			}
		}

		if (drawBounds != null)
		{
			p.setTransform(orig);
		}
	}

	private void addPointsOffMapToMakeWavesGoToEdgeOfMap(List<Point> drawPoints)
	{
		if (drawPoints.size() < 2)
		{
			return;
		}

		{
			Point point = getPointToAddToMakeWavesGoToEdgeOfMap(drawPoints.get(0));
			if (point != null)
			{
				drawPoints.add(0, point);
			}
		}

		{
			Point point = getPointToAddToMakeWavesGoToEdgeOfMap(drawPoints.get(drawPoints.size() - 1));
			if (point != null)
			{
				drawPoints.add(point);
			}
		}
	}

	private Point getPointToAddToMakeWavesGoToEdgeOfMap(Point fromLine)
	{
		double length = 50 * resolutionScale;
		if (fromLine.x == 0.0)
		{
			return new Point(fromLine.x - length, fromLine.y);
		}

		if (fromLine.x == bounds.width)
		{
			return new Point(fromLine.x + length, fromLine.y);
		}

		if (fromLine.y == 0.0)
		{
			return new Point(fromLine.x, fromLine.y - length);
		}

		if (fromLine.y == bounds.width)
		{
			return new Point(fromLine.x, fromLine.y + length);
		}

		return null;
	}

	private List<Point> addJitter(long randomSeed, List<Point> points, double variationRange)
	{
		List<Point> result = points.stream().map(p ->
		{
			// Use a random seed that is close to unique for each point so that
			// incremental draws don't have to redraw the entire coastline.
			Random rand = new Random(randomSeed + (long) ((p.x * resolutionScale + p.y * resolutionScale) * 100));
			double radius = rand.nextDouble() * variationRange;
			double angle = rand.nextDouble() * 2 * Math.PI;
			Point toAdd = new Point(radius * Math.cos(angle), radius * Math.sin(angle));
			return p.add(toAdd);
		}).toList();
		return result;
	}

	List<List<Point>> addRandomBreaks(Random rand, List<Point> points, BiFunction<Boolean, Random, Double> getNewSkipDistance)
	{
		// Prime the random generator.
		for (int i = 0; i < 100; i++)
		{
			rand.nextInt();
		}
		List<List<Point>> result = new ArrayList<>();
		boolean isDrawing = rand.nextBoolean();
		double skipDistance = getNewSkipDistance.apply(isDrawing, rand);
		List<Point> curSegment = new ArrayList<>();
		Point lastPoint = points.get(0);
		for (Point point : points)
		{
			if (isDrawing)
			{
				curSegment.add(point);
			}

			double distanceFromLast = lastPoint.distanceTo(point);
			skipDistance -= distanceFromLast;
			if (skipDistance <= 0)
			{
				if (curSegment.size() > 0)
				{
					result.add(curSegment);
					curSegment = new ArrayList<>();
				}

				isDrawing = !isDrawing;
				skipDistance = getNewSkipDistance.apply(isDrawing, rand);
			}

			lastPoint = point;
		}

		if (curSegment.size() > 0)
		{
			result.add(curSegment);
		}

		return result;
	}

	public void drawCoastline(Painter p, double strokeWidth, Collection<Center> centersToDraw, Rectangle drawBounds)
	{
		drawSpecifiedEdges(p, strokeWidth, centersToDraw, drawBounds, edge -> edge.isCoast());
	}

	public void drawCoastlineWithLakeShores(Painter p, double strokeWidth, Collection<Center> centersToDraw, Rectangle drawBounds)
	{
		drawSpecifiedEdges(p, strokeWidth, centersToDraw, drawBounds, edge -> edge.isCoastOrLakeShore());
	}

	public void drawRegionBoundariesSolid(Painter g, double strokeWidth, boolean ignoreRiverEdges, Collection<Center> centersToDraw, Rectangle drawBounds)
	{
		drawSpecifiedEdges(g, strokeWidth, centersToDraw, drawBounds, edge ->
		{
			if (ignoreRiverEdges && edge.isRiver())
			{
				// Don't draw region boundaries where there are rivers.
				return false;
			}

			return edge.d0.region != edge.d1.region && !edge.isCoastOrLakeShore();
		});
	}

	public void drawRegionBoundaries(Painter p, Stroke stroke, Collection<Center> centersToDraw, Rectangle drawBounds)
	{
		Transform orig = null;
		if (drawBounds != null)
		{
			orig = p.getTransform();
			p.translate(-drawBounds.x, -drawBounds.y);
		}

		p.setStroke(stroke, resolutionScale);

		List<List<Edge>> regionBoundaries = findEdgesByDrawType(centersToDraw, EdgeDrawType.Region, stroke.type != StrokeType.Solid);
		for (List<Edge> regionBoundary : regionBoundaries)
		{
			List<Point> drawPoints = edgeListToDrawPoints(regionBoundary);

			if (drawPoints == null || drawPoints.size() <= 1)
			{
				continue;
			}

			// Enforce an order in which the region boundary is drawn so that
			// full versus incremental redraws don't draw dotted lines
			// in the opposite order and so end up drawing the dashed pattern
			// slightly differently.
			if (drawPoints.get(0).compareTo(drawPoints.get(drawPoints.size() - 1)) > 0)
			{
				Collections.reverse(drawPoints);
			}

			if (DebugFlags.drawRegionBoundaryPathJoins())
			{
				Color color = p.getColor();
				p.setBasicStroke(1f * (float) resolutionScale);

				int diameter = (int) (7.0 * resolutionScale);
				p.setColor(Color.red);
				p.drawOval((int) (drawPoints.get(0).x) - diameter / 2, (int) (drawPoints.get(0).y) - diameter / 2, diameter, diameter);

				diameter = (int) (10.0 * resolutionScale);
				p.setColor(Color.blue);
				p.drawOval((int) (drawPoints.get(drawPoints.size() - 1).x) - diameter / 2, (int) (drawPoints.get(drawPoints.size() - 1).y) - diameter / 2, diameter, diameter);

				p.setColor(color);
				p.setStroke(stroke, resolutionScale);
			}

			drawPolyline(p, drawPoints);
		}

		if (drawBounds != null)
		{
			p.setTransform(orig);
		}
	}

	public List<List<Edge>> findEdgesByDrawType(Collection<Center> centersToDraw, EdgeDrawType drawType, boolean searchEntireGraph)
	{
		return findEdges(centersToDraw, (e) -> noisyEdges.getEdgeDrawType(e) == drawType, searchEntireGraph);
	}

	public List<List<Edge>> findShoreEdges(Collection<Center> centersToDraw, boolean includeLakeShores, boolean searchEntireGraph)
	{
		if (includeLakeShores)
		{
			return findEdges(centersToDraw, (e) -> e.isCoast() || e.isLakeShore(), searchEntireGraph);
		}
		else
		{
			return findEdges(centersToDraw, (e) -> e.isCoast(), searchEntireGraph);
		}
	}

	/**
	 * Finds all edges that the 'accept' function accepts which are touching centersToDraw or are connected to an edge that touches those
	 * centers.
	 *
	 * @param centersToDraw
	 *            Only edges either in/touching this collection or connected to edges that are in it will be returned.
	 * @param accept
	 *            Function to determine what edge is to include in results.
	 * @param searchEntireGraph
	 *            When false, only centers in centersToDraw will be searched. When true, all centers will be searched. Passing this as false
	 *            is much more performant, but can cause subtle differences in the results depending on which centers are passed in. Setting
	 *            this to true enforces that the lists of edges returned are ordered and found the same way for full redraws vs incremental
	 *            for any edges that touch or pass through centersToDraw.
	 * @return
	 */
	public List<List<Edge>> findEdges(Collection<Center> centersToDraw, Function<Edge, Boolean> accept, boolean searchEntireGraph)
	{
		List<List<Edge>> result = new ArrayList<>();
		Set<Edge> explored = new HashSet<>();
		for (Center center : (centersToDraw == null || searchEntireGraph ? centers : centersToDraw))
		{
			for (Edge edge : center.borders)
			{
				if (explored.contains(edge))
				{
					continue;
				}

				List<Edge> edgePath = findPath(explored, edge, accept);
				if (edgePath != null && !edgePath.isEmpty())
				{
					if (searchEntireGraph && centersToDraw != null)
					{
						if (edgePath.stream().anyMatch(e -> e.d0 != null && centersToDraw.contains(e.d0) || e.d1 != null && centersToDraw.contains(e.d1)))
						{
							result.add(edgePath);
						}
					}
					else
					{
						result.add(edgePath);
					}
				}
			}
		}

		return result;
	}

	/**
	 * Given an edge to start at, this returns an ordered sequence of edges in the path that edge is included in.
	 *
	 * @param found
	 *            Edges that have already been searched, and so will not be searched again.
	 * @param start
	 *            Where to start to search. Not necessarily the start of the path we're searching for.
	 * @param accept
	 *            Used to test whether edges are part of the desired path. If "edge" returns false for this function, then an empty list is
	 *            returned.
	 * @return A list of edges forming a path.
	 */
	private List<Edge> findPath(Set<Edge> found, Edge start, Function<Edge, Boolean> accept)
	{
		if (start == null || !accept.apply(start))
		{
			return null;
		}

		ArrayDeque<Edge> deque = new ArrayDeque<>();
		deque.add(start);
		found.add(start);

		if (start.v0 != null)
		{
			Edge e = start;
			Edge prev = null;
			while (true)
			{
				Edge next = noisyEdges.findEdgeToFollow(e.v0, e, prev);
				if (next == null || found.contains(next))
				{
					next = noisyEdges.findEdgeToFollow(e.v1, e, prev);
				}
				if (next == null || !accept.apply(next) || found.contains(next))
				{
					break;
				}
				prev = e;
				deque.addFirst(next);
				found.add(next);
				e = next;
			}
		}

		if (start.v1 != null)
		{
			Edge e = start;
			Edge prev = null;
			while (true)
			{
				Edge next = noisyEdges.findEdgeToFollow(e.v1, e, prev);
				if (next == null || found.contains(next))
				{
					next = noisyEdges.findEdgeToFollow(e.v0, e, prev);
				}
				if (next == null || !accept.apply(next) || found.contains(next))
				{
					break;
				}
				prev = e;
				deque.addLast(next);
				found.add(next);
				e = next;
			}
		}

		return new ArrayList<>(deque);
	}
}
