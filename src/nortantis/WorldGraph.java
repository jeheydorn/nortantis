package nortantis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.random.RandomGenerator;

import nortantis.MapSettings.LineStyle;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.graph.voronoi.Edge;
import nortantis.graph.voronoi.NoisyEdges;
import nortantis.graph.voronoi.VoronoiGraph;
import nortantis.graph.voronoi.nodename.as3delaunay.Voronoi;
import nortantis.platform.Color;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.Painter;
import nortantis.util.Helper;
import nortantis.util.Range;

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
	public boolean isForFrayedBorder;
	private Double meanCenterWidth;
	private Double meanCenterWidthBetweenNeighbors;
	private List<Set<Center>> lakes;

	// Maps plate ids to plates.
	Set<TectonicPlate> plates;
	public Map<Integer, Region> regions;

	public WorldGraph(Voronoi v, double lloydRelaxationsScale, Random r, double nonBorderPlateContinentalProbability,
			double borderPlateContinentalProbability, double sizeMultiplyer, LineStyle lineStyle, double pointPrecision,
			boolean createElevationBiomesLakesAndRegions, boolean areRegionBoundariesVisible)
	{
		super(r, sizeMultiplyer, pointPrecision);
		this.nonBorderPlateContinentalProbability = nonBorderPlateContinentalProbability;
		this.borderPlateContinentalProbability = borderPlateContinentalProbability;
		TectonicPlate.resetIds();
		initVoronoiGraph(v, numLloydRelaxations, lloydRelaxationsScale, createElevationBiomesLakesAndRegions);
		setupColors();
		regions = new TreeMap<>();
		
		// Switch the center locations the Voronoi centroids of each center because I think that
		// looks better for drawing, and it works better for smooth coastlines.
		updateCenterLocationsToCentroids();

		if (createElevationBiomesLakesAndRegions)
		{
			createPoliticalRegions();
			markLakes();
			smoothCoastlinesAndRegionBoundariesIfNeeded(centers, lineStyle, areRegionBoundariesVisible);
		}
		setupRandomSeeds(r);
	}

	/**
	 * This constructor doens't create tectonic plates or elevation.
	 */
	public WorldGraph(Voronoi v, double lloydRelaxationsScale, Random r, double resolutionScale, double pointPrecision,
			boolean isForFrayedBorder)
	{
		super(r, resolutionScale, pointPrecision);
		initVoronoiGraph(v, numLloydRelaxations, lloydRelaxationsScale, false);
		setupColors();
		setupRandomSeeds(r);
	}

	private void updateCenterLocationsToCentroids()
	{
		for (Center center : centers)
		{
			center.updateLocToCentroid();
		}
	}

	public Set<Center> smoothCoastlinesAndRegionBoundariesIfNeeded(Collection<Center> centersToUpdate, LineStyle lineStyle,
			boolean areRegionBoundariesVisible)
	{
		Set<Center> changed = new HashSet<Center>();
		if (lineStyle == LineStyle.SplinesWithSmoothedCoastlines)
		{
			for (Center center : centersToUpdate)
			{
				Set<Center> needsRebuild = smoothCoastlinesAndOptionallyRegionBoundaries(center, areRegionBoundariesVisible);
				changed.addAll(needsRebuild);
			}
			for (Center center : changed)
			{
				center.updateLocToCentroid();
			}			
		}
		else if (lineStyle == LineStyle.Splines && areRegionBoundariesVisible)
		{
			for (Center center : centersToUpdate)
			{
				Set<Center> needsRebuild = smoothRegionBoundaries(center);
				changed.addAll(needsRebuild);
			}
			for (Center center : changed)
			{
				center.updateLocToCentroid();
			}			
		}

		// Check if the smoothing caused any centers to be malformed, and if so, clear the smoothing on them.
		// I reapply this loop as a fixed-point algorithm, stopping when either it makes a pass were no
		// centers were malformed, or the last pass made no progress.
		// I also check a maximum number of loops to make sure this algorithm doesn't take a really long time for some really strange map,
		// although in practice that doesn't seem to happen.
		Set<Center> loopOver = new HashSet<>(changed);
		Set<Center> next = new HashSet<>();
		int size;
		int loopCount = 0;
		do
		{
			for (Center center : loopOver)
			{
				if (!center.isWellFormedForDrawing())
				{
					next.add(center);
					changed.add(center);
					for (Corner corner : center.corners)
					{
						corner.loc = corner.originalLoc;

						next.addAll(corner.touches);
						changed.addAll(corner.touches);
					}
				}
			}
			
			for (Center center : next)
			{
				center.updateLocToCentroid();
			}

			size = loopOver.size();
			loopOver = next;
			next = new HashSet<>();
			loopCount++;
		}
		while (size > loopOver.size() && loopOver.size() > 0 && loopCount <= 10);

		return changed;
	}

	private Set<Center> smoothCoastlinesAndOptionallyRegionBoundaries(Center center, boolean smoothRegionBoundaries)
	{
		assert center != null;
		boolean isChanged = false;
		for (Corner corner : center.corners)
		{
			SmoothingResult coastlineResult = updateCornerLocationToSmoothEdges(corner, e -> e.isCoast());
			boolean isCornerChanged = coastlineResult.isCornerChanged;
			// Only smooth region boundaries for the corner if it is not a coastline, because otherwise we will clear the smoothing on that
			// spot on the coastline.
			if (!coastlineResult.isSmoothed && smoothRegionBoundaries)
			{
				SmoothingResult regionResult = updateCornerLocationToSmoothEdges(corner, e -> e.isRegionBoundary() && !e.isRiver());
				isCornerChanged |= regionResult.isCornerChanged;
			}
			isChanged |= isCornerChanged;
		}
		Set<Center> result = new HashSet<Center>();
		if (isChanged)
		{
			result.add(center);
			result.addAll(center.neighbors);

		}
		return result;
	}

	private Set<Center> smoothRegionBoundaries(Center center)
	{
		assert center != null;
		boolean isChanged = false;
		for (Corner corner : center.corners)
		{
			SmoothingResult result = updateCornerLocationToSmoothEdges(corner, c -> c.isRegionBoundary() && !c.isRiver());
			isChanged |= result.isCornerChanged;
		}
		Set<Center> result = new HashSet<Center>();
		if (isChanged)
		{
			result.add(center);
			result.addAll(center.neighbors);

		}
		return result;
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
			// This corner is not on an edge to it smooth. Clear the override to set the corner's location back to how it was first
			// generated.
			boolean isChanged = !corner.loc.equals(corner.originalLoc);
			corner.loc = corner.originalLoc;
			return new SmoothingResult(isChanged, false);
		}

		if (edgesToSmooth.size() == 2)
		{
			// Don't smooth edges on islands made of only one center, because it tends to make the island disappear. Same for single-polygon
			// oceans or lakes.
			if (corner.touches.stream().anyMatch(center -> center.isSinglePolygonIsland() || center.isSinglePolygonWater()))
			{
				boolean isChanged = !corner.loc.equals(corner.originalLoc);
				corner.loc = corner.originalLoc;
				return new SmoothingResult(isChanged, false);
			}

			// This is a coastline.
			Corner otherCorner0 = edgesToSmooth.get(0).v0 == corner ? edgesToSmooth.get(0).v1 : edgesToSmooth.get(0).v0;
			Corner otherCorner1 = edgesToSmooth.get(1).v0 == corner ? edgesToSmooth.get(1).v1 : edgesToSmooth.get(1).v0;
			Point smoothedLoc = new Point((otherCorner0.originalLoc.x + otherCorner1.originalLoc.x) / 2,
					(otherCorner0.originalLoc.y + otherCorner1.originalLoc.y) / 2);

			corner.loc = smoothedLoc;

			return new SmoothingResult(true, true);
		}
		else
		{
			boolean isChanged = !corner.loc.equals(corner.originalLoc);
			corner.loc = corner.originalLoc;
			return new SmoothingResult(isChanged, false);
		}
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

	private void setupRandomSeeds(Random rand)
	{
		for (Center c : centers)
		{
			c.treeSeed = rand.nextLong();
		}

		for (Edge e : edges)
		{
			e.noisyEdgesSeed = rand.nextLong();
		}
	}

	private void setupColors()
	{
		OCEAN = Biome.OCEAN.color;
		LAKE = Biome.LAKE.color;
		BEACH = Biome.BEACH.color;
		RIVER = Color.create(22, 55, 88);

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
		noisyEdges = new NoisyEdges(MapCreator.calcSizeMultipilerFromResolutionScale(resolutionScale), lineStyle, isForFrayedBorder);
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
		assert regions.values().stream().mapToInt(reg -> reg.size()).sum()
				+ centers.stream().filter(c -> c.region == null).count() == centers.size();
	}

	public void drawRegionIndexes(Painter p, Set<Center> centersToDraw, Rectangle drawBounds)
	{
		// As we draw, if ocean centers are close to land, use the region index from that land. That way
		// if the option to allow icons to draw over coastlines is true, then region colors will
		// draw inside transparent pixels of icons whose content extends over ocean.
		drawPolygons(p, centersToDraw, drawBounds, (c) ->
		{
			if (c.region == null)
			{
				// This needs to be far enough that no icon extends this far into the ocean. The farthest I've seen any of my mountains have
				// extend
				// into the ocean is 3 polygons, but I'm adding a buffer to be safe. Note that increasing this is fairly expensive.
				final int maxDistanceToSearchForLand = 5;
				Center closestLand = findClosestLand(c, maxDistanceToSearchForLand);
				if (closestLand == null || closestLand.region == null)
				{
					return Color.black;
				}
				else
				{
					return Color.create(closestLand.region.id, closestLand.region.id, closestLand.region.id);
				}
			}
			else
			{
				return Color.create(c.region.id, c.region.id, c.region.id);
			}
		});
	}

	private Center findClosestLand(Center center, int maxDistanceInPolygons)
	{
		return breadthFirstSearchForGoal((c, distanceFromStart) ->
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
		Optional<Center> opt = centers.stream().filter(c -> c.region != null)
				.min((c1, c2) -> Double.compare(c1.loc.distanceTo(point), c2.loc.distanceTo(point)));

		if (opt.isPresent())
		{
			assert opt.get().region != null;
			return opt.get().region;
		}

		// This could only happen if there are no regions on the graph.
		return null;
	}

	public Corner findClosestCorner(Point point)
	{
		Center closestCenter = findClosestCenter(point);
		Optional<Corner> optional = closestCenter.corners.stream()
				.min((c1, c2) -> Double.compare(c1.loc.distanceTo(point), c2.loc.distanceTo(point)));
		return optional.get();
	}

	public Center findClosestCenter(double x, double y)
	{
		return findClosestCenter(new Point(x, y));
	}

	public Center findClosestCenter(Point point)
	{
		return findClosestCenter(point, false);
	}

	public Center findClosestCenter(Point point, boolean returnNullIfNotOnMap)
	{
		if (point.x < getWidth() && point.y < getHeight() && point.x >= 0 && point.y >= 0)
		{
			buildCenterLookupTableIfNeeded();
			Color color;
			try
			{
				color = Color.create(centerLookupTable.getRGB((int) point.x, (int) point.y));
			}
			catch (IndexOutOfBoundsException e)
			{
				color = null;
			}
			int index = color.getRed() | (color.getGreen() << 8) | (color.getBlue() << 16);
			return centers.get(index);
		}
		else if (!returnNullIfNotOnMap)
		{
			Optional<Center> opt = centers.stream().filter(c -> c.isBorder)
					.min((c1, c2) -> Double.compare(c1.loc.distanceTo(point), c2.loc.distanceTo(point)));
			return opt.get();

		}
		return null;
	}

	private Image centerLookupTable;

	public void buildCenterLookupTableIfNeeded()
	{
		if (centerLookupTable == null)
		{
			centerLookupTable = Image.create((int) bounds.width, (int) bounds.height, ImageType.RGB);
			Painter p = centerLookupTable.createPainter();
			drawPolygons(p, new Function<Center, Color>()
			{
				public Color apply(Center c)
				{
					return convertCenterIdToColor(c);
				}
			});
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
		if (centerLookupTable == null)
		{
			buildCenterLookupTableIfNeeded();
		}
		else
		{
			// Include neighbors of each center because if a center changed, that will affect its neighbors as well.
			Set<Center> centersWithNeighbors = new HashSet<>();
			for (Center c : centersToUpdate)
			{
				centersWithNeighbors.add(c);
				for (Center neighbor : c.neighbors)
				{
					centersWithNeighbors.add(neighbor);
				}
			}

			Painter p = centerLookupTable.createPainter();
			drawPolygons(p, centersWithNeighbors, new Function<Center, Color>()
			{
				public Color apply(Center c)
				{
					return convertCenterIdToColor(c);
				}
			});
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

	public Center breadthFirstSearchForGoal(BiFunction<Center, Integer, Boolean> accept, Function<Center, Boolean> isGoal, Center start)
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
					if (!explored.contains(n) && !frontier.contains(n) && accept.apply(n, distanceFromStart))
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

	public void paintElevationUsingTrianges(Painter p)
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

		// Code useful for debugging
		// g.setColor(Color.WHITE);
		// for (Corner c : corners)
		// {
		// for (Corner adjacent : c.adjacent)
		// {
		// g.drawLine((int)c.loc.x, (int)c.loc.y, (int) adjacent.loc.x,
		// (int)adjacent.loc.y);
		// }
		// }
		//
		// for (Edge e : edges)
		// {
		// g.setStroke(new BasicStroke(1));
		// g.setColor(Color.YELLOW);
		// g.drawLine((int) e.d0.loc.x, (int) e.d0.loc.y, (int) e.d1.loc.x,
		// (int) e.d1.loc.y);
		// }
	}

	public void drawLandAndLandLockedLakesBlackAndOceanWhite(Painter p, Collection<Center> centersToRender, Rectangle drawBounds)
	{
		if (centersToRender == null)
		{
			centersToRender = centers;
		}

		Set<Center> landAndLandLockedLakes = findLandAndLandLockedLakes(centersToRender);
		drawPolygons(p, centersToRender, drawBounds, new Function<Center, Color>()
		{
			public Color apply(Center c)
			{
				if (landAndLandLockedLakes.contains(c))
				{
					return Color.black;
				}
				return Color.white;
			}
		});
	}

	private Set<Center> findLandAndLandLockedLakes(Collection<Center> centersToSearch)
	{
		Set<Center> result = new HashSet<>();
		Set<Center> explored = new HashSet<>();
		for (Center center : centersToSearch)
		{
			if (explored.contains(center))
			{
				continue;
			}

			if (!center.isWater)
			{
				result.add(center);
			}

			if (center.isLake)
			{
				Set<Center> lake = breadthFirstSearch((c) -> c.isLake, center);
				if (!isLakeTouchingOcean(lake))
				{
					result.addAll(lake);
				}

				explored.addAll(lake);
			}
		}

		return result;
	}

	private boolean isLakeTouchingOcean(Set<Center> lake)
	{
		for (Center lc : lake)
		{
			if (lc.neighbors.stream().anyMatch((neighbor) -> neighbor.isWater && !neighbor.isLake))
			{
				return true;
			}
		}
		return false;
	}

	public Set<Center> getNeighboringLakes(Set<Center> centersToSearch)
	{
		Set<Center> result = new HashSet<>();
		for (Center center : centersToSearch)
		{
			for (Center neighbor : center.neighbors)
			{
				if (neighbor.isLake && !result.contains(neighbor) && !centersToSearch.contains(neighbor))
				{
					Set<Center> lake = breadthFirstSearch((c) -> c.isLake && !centersToSearch.contains(c), neighbor);
					result.addAll(lake);
				}
			}
		}
		return result;
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

			// The second condition excludes lakes that touch the edge of the map, since it's hard to tell whether those should be ocean or
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
		createTectonicPlates();
		assignOceanAndContinentalPlates();
		lowerOceanPlates();
		assignPlateCornerElivations();
	}

	private void assignPlateCornerElivations()
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
					double d0ConvergeLevel = calcLevelOfConvergence(e.d0.tectonicPlate.findCentroid(), e.d0.tectonicPlate.velocity,
							e.d1.tectonicPlate.findCentroid(), e.d1.tectonicPlate.velocity);

					// If the plates are converging, rough them up a bit by
					// calculating divergence per
					// polygon. This brakes up long snake like islands.
					if (d0ConvergeLevel > 0)
					{
						d0ConvergeLevel = calcLevelOfConvergence(e.d0.loc, e.d0.tectonicPlate.velocity, e.d1.loc,
								e.d1.tectonicPlate.velocity);
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
					if (d0ConvergeLevel > 0 && e.d0.tectonicPlate.type == PlateType.Oceanic
							&& e.d1.tectonicPlate.type == PlateType.Continental)
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

	private void createTectonicPlates()
	{
		// long startTime = System.currentTimeMillis();
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
			c.tectonicPlate = new TectonicPlate(betaDist.sample(), centers);
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
				List<Center> neighborsNotInSamePlate = Helper.filter(c.neighbors, new nortantis.util.Function<Center, Boolean>()
				{
					public Boolean apply(Center otherC)
					{
						return c.tectonicPlate != otherC.tectonicPlate;
					}
				});
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

				// Stop if there are only nine plates left and one of them is getting too small. This will usually prevent
				// creating a map this just ocean or has only tiny islands, although it isn't guaranteed since it's
				// possible all 9 plates will be assigned to oceanic.
				if (plateCounts.keySet().size() == 9 && Helper.min(plateCounts) <= minNinthtoLastPlateSize)
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

		// Logger.println("Plate time: " + (System.currentTimeMillis() -
		// startTime)/1000.0);
	}

	/**
	 * Returns the amount c1 and c2 are converging. This is between -1 and 1.
	 * 
	 * @param c1
	 *            A center along a tectonic plate border.
	 * @param c1Velocity
	 *            The velocity of the plate c1 is on.
	 * @param c2
	 *            A center along a tectonic plate border: not the same tectonic plate as c1
	 * @param c2Velocity
	 *            The velocity of the plate c2 is on.
	 */
	private double calcLevelOfConvergence(Point p1, PolarCoordinate p1Velocity, Point p2, PolarCoordinate c2Velocity)
	{
		return 0.5 * calcUnilateralLevelOfConvergence(p1, p1Velocity, p2) + 0.5 * calcUnilateralLevelOfConvergence(p2, c2Velocity, p1);
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
		}

		return bounds;
	}

	public Set<Center> getCentersInBounds(Rectangle bounds)
	{
		Set<Center> selected = new HashSet<Center>();

		if (bounds == null)
		{
			return selected;
		}

		Center center = findClosestCenter(bounds.getCenter());
		if (center == null)
		{
			return selected;
		}
		else
		{
			selected.add(center);
		}

		return breadthFirstSearch((c) -> isCenterOverlappingRectangle(c, bounds), center);
	}

	private boolean isCenterOverlappingRectangle(Center center, Rectangle rectangle)
	{
		for (Corner corner : center.corners)
		{
			if (rectangle.contains(corner.loc))
			{
				return true;
			}
		}

		return rectangle.contains(center.loc);
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
	 * @param edgeType
	 *            The type of edge to return
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
	public List<Edge> findShortestPath(Center start, Center end, Function<Edge, Double> calculateWeight)
	{
		PriorityQueue<CenterSearchNode> explored = new PriorityQueue<>((n1, n2) -> Double.compare(n1.predictedScore, n2.predictedScore));
		// Contains exactly those centers in explored, for faster checking if
		// explored contains a center.
		Set<Center> exploredCenters = new HashSet<>();
		// Maps from centers we have seen to their nodes, to allow fast lookup
		// of scores of previously seen centers.
		Map<Center, CenterSearchNode> centerNodeMap = new HashMap<>();

		explored.add(new CenterSearchNode(start, null, 0, Center.distanceBetween(start, end)));
		exploredCenters.add(start);
		centerNodeMap.put(start, explored.peek());

		while (!explored.isEmpty())
		{
			CenterSearchNode current = explored.poll();
			exploredCenters.remove(current.center);
			if (current.center.equals(end))
			{
				// The score so far doesn't matter for this case
				return createPathFromBackPointers(new CenterSearchNode(end, current, 0, Center.distanceBetween(current.center, end)));
			}

			for (Edge edge : current.center.borders)
			{
				Center neighbor = current.center.equals(edge.d0) ? edge.d1 : edge.d0;
				if (neighbor != null)
				{
					double scoreFromStartToNeighbor = current.scoreSoFar + calculateWeight.apply(edge);
					double neighborCurrentScore = centerNodeMap.containsKey(neighbor) ? centerNodeMap.get(neighbor).scoreSoFar
							: Float.POSITIVE_INFINITY;
					if (scoreFromStartToNeighbor < neighborCurrentScore)
					{
						CenterSearchNode neighborNode = new CenterSearchNode(neighbor, current, scoreFromStartToNeighbor,
								scoreFromStartToNeighbor + Center.distanceBetween(current.center, end));
						if (!exploredCenters.contains(neighbor))
						{
							centerNodeMap.put(neighbor, neighborNode);
							explored.add(neighborNode);
							exploredCenters.add(neighbor);
						}
					}
				}
			}
		}

		// The end is not reachable from the start
		return null;
	}

	private Edge findConnectingEdge(Center c1, Center c2)
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
	 * @param edgeType
	 *            The type of edge to return
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
	 * Scales the graph and everything in it to the target size.
	 */
	public void scale(double targetWidth, double targetHeight)
	{
		double widthScale = targetWidth / bounds.width;
		double heightScale = targetHeight / bounds.height;
		for (Center center : centers)
		{
			center.loc = center.loc.mult(widthScale, heightScale);
			if (center.originalLoc != null)
			{
				center.originalLoc = center.originalLoc.mult(widthScale, heightScale);
			}
		}
		for (Edge edge : edges)
		{
			if (edge.midpoint != null)
			{
				edge.midpoint = edge.midpoint.mult(widthScale, heightScale);
			}
		}
		for (Corner corner : corners)
		{
			corner.loc = corner.loc.mult(widthScale, heightScale);
			if (corner.originalLoc != null)
			{
				corner.originalLoc = corner.originalLoc.mult(widthScale, heightScale);
			}
		}

		bounds = new Rectangle(0, 0, targetWidth, targetHeight);
		meanCenterWidth = null;
		getMeanCenterWidth();
		meanCenterWidthBetweenNeighbors = null;
		getMeanCenterWidthBetweenNeighbors();
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
}
