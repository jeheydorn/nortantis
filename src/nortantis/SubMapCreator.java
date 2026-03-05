package nortantis;

import nortantis.editor.CenterEdit;
import nortantis.editor.CenterIcon;
import nortantis.editor.CenterIconType;
import nortantis.editor.CenterTrees;
import nortantis.editor.EdgeEdit;
import nortantis.editor.FreeIcon;
import nortantis.editor.RegionEdit;
import nortantis.editor.Road;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.graph.voronoi.Edge;
import nortantis.platform.Font;
import nortantis.swing.MapEdits;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Creates a new MapSettings for a zoomed-in sub-map of an existing map. The sub-map inherits the original map's land/water shape, region colors, text, icons, and roads within the selected area.
 */
public class SubMapCreator
{
	/**
	 * Creates a new MapSettings for a sub-map of the given original map.
	 *
	 * @param originalSettings
	 *            The original map settings.
	 * @param originalGraph
	 *            The original world graph (used for land/water lookup).
	 * @param originalEdits
	 *            The original map edits (used for land/water, region, text, icons, roads).
	 * @param selectionBoundsRI
	 *            The selection bounds in resolution-invariant (RI) coordinates.
	 * @param subMapWorldSize
	 *            The number of Voronoi polygons for the sub-map.
	 * @param originalResolution
	 *            The resolution at which originalGraph was created (i.e. the display quality scale), used to convert resolution-invariant coordinates to originalGraph pixel coordinates.
	 * @return New MapSettings for the sub-map, with pre-populated edits.
	 */
	public static MapSettings createSubMapSettings(MapSettings originalSettings, WorldGraph originalGraph, MapEdits originalEdits, Rectangle selectionBoundsRI, int subMapWorldSize, double originalResolution, long seed, boolean redistributeIcons)
	{
		// Compute new dimensions and world size.
		// The largest dimension of the sub-map matches the largest dimension of the original map.
		// Whichever axis of the selection box is larger gets that max value; the other is scaled proportionally.
		int maxOriginalDimension = Math.max(originalSettings.generatedWidth, originalSettings.generatedHeight);
		int newGenWidth;
		int newGenHeight;
		if (selectionBoundsRI.width >= selectionBoundsRI.height)
		{
			newGenWidth = maxOriginalDimension;
			newGenHeight = (int) Math.round((double) maxOriginalDimension * selectionBoundsRI.height / selectionBoundsRI.width);
		}
		else
		{
			newGenHeight = maxOriginalDimension;
			newGenWidth = (int) Math.round((double) maxOriginalDimension * selectionBoundsRI.width / selectionBoundsRI.height);
		}
		newGenWidth = Math.max(1, newGenWidth);
		newGenHeight = Math.max(1, newGenHeight);

		int newWorldSize = Math.max(1, Math.min(SettingsGenerator.maxWorldSize, subMapWorldSize));

		// Deep-copy original settings, override key fields.
		MapSettings newSettings = originalSettings.deepCopyExceptEdits();
		newSettings.randomSeed = seed;
		newSettings.generatedWidth = newGenWidth;
		newSettings.generatedHeight = newGenHeight;
		newSettings.worldSize = newWorldSize;
		newSettings.imageExportPath = null;
		newSettings.heightmapExportPath = null;
		// No rotation/flip on the sub-map.
		newSettings.rightRotationCount = 0;
		newSettings.flipHorizontally = false;
		newSettings.flipVertically = false;

		// Scale font sizes to keep text proportional to the visible features.
		//
		// zoomFactor: how much the selection is magnified relative to the original map — equals 1.0
		// when the sub-map covers the entire original, and grows as the selection shrinks.
		//
		// detailRatio: how many times more (or fewer) polygons the sub-map has compared to the
		// 1× equivalent (same polygon density as the source), matching the multiplier shown in the
		// SubMapDialog detail slider. At ratio=1 the polygon density is unchanged; at ratio=2 the
		// sub-map has twice as many polygons (more detail) so features are more finely divided and
		// text should be smaller relative to them.
		//
		// Combining both: fontScale = zoomFactor / detailRatio, clamped to [1.0, …] so fonts never
		// shrink below the source map's sizes, and capped at maxFontSize to prevent illegibly huge text.
		double zoomFactor = (double) newGenWidth / selectionBoundsRI.width;
		double selectionArea = selectionBoundsRI.width * selectionBoundsRI.height;
		double originalMapArea = originalSettings.generatedWidth * (double) originalSettings.generatedHeight;
		double oneXWorldSize = originalSettings.worldSize * selectionArea / originalMapArea;
		double detailRatio = oneXWorldSize > 0 ? newWorldSize / oneXWorldSize : 1.0;
		double fontScale = Math.max(1.0, zoomFactor / Math.max(1.0, detailRatio));
		newSettings.titleFont = scaleFontSize(newSettings.titleFont, fontScale);
		newSettings.regionFont = scaleFontSize(newSettings.regionFont, fontScale);
		newSettings.mountainRangeFont = scaleFontSize(newSettings.mountainRangeFont, fontScale);
		newSettings.otherMountainsFont = scaleFontSize(newSettings.otherMountainsFont, fontScale);
		newSettings.citiesFont = scaleFontSize(newSettings.citiesFont, fontScale);
		newSettings.riverFont = scaleFontSize(newSettings.riverFont, fontScale);
		// Initialize fresh empty edits so createGraphForUnitTests will create elevation (isInitialized=false).
		newSettings.edits = new MapEdits();

		// Build the WorldGraph for the sub-map (to get center positions and count).
		// We call this with createElevationBiomesLakesAndRegions=false because land/water and icon placement will be determined by the source map, not by a new, generated world.
		// This gives us the same Voronoi structure MapCreator will use when rendering (same seed, same params).
		WorldGraph newGraph = MapCreator.createGraph(newSettings, false);

		// For each new center, use majority/plurality voting to assign water/lake/region.
		MapEdits newEdits = new MapEdits();
		Map<Integer, List<Integer>> originalRegionToNewCenters = buildCenterEdits(newGraph, originalGraph, originalEdits, selectionBoundsRI, originalResolution, newEdits);

		// Propagate coast/corner flags now that isWater/isLake are set on all centers.
		newGraph.updateCoastAndCornerFlags();
		newGraph.markLakes();

		// Build remaining MapEdits.

		transferRegionEdits(originalGraph, originalEdits, originalRegionToNewCenters, newEdits);

		transferRivers(originalGraph, originalEdits, newGraph, selectionBoundsRI, newEdits, originalResolution);

		transferText(originalEdits, selectionBoundsRI, newEdits, newGenWidth, newGenHeight, fontScale);

		transferFreeIcons(originalEdits, originalGraph, newGraph, selectionBoundsRI, originalResolution, newEdits, newGenWidth, newGenHeight, redistributeIcons, seed);
		newEdits.hasIconEdits = true;

		transferRoads(originalEdits, selectionBoundsRI, newGenWidth, newGenHeight, newEdits);

		// Attach the new edits to the new settings.
		newSettings.edits = newEdits;

		return newSettings;
	}

	private static void transferRegionEdits(WorldGraph originalGraph, MapEdits originalEdits, Map<Integer, List<Integer>> originalRegionToNewCenters, MapEdits newEdits)
	{
		// Copy colors for all referenced original regionIds.
		for (Integer originalRegionId : originalRegionToNewCenters.keySet())
		{
			RegionEdit originalRegionEdit = originalEdits.regionEdits.get(originalRegionId);
			if (originalRegionEdit != null)
			{
				newEdits.regionEdits.put(originalRegionId, new RegionEdit(originalRegionId, originalRegionEdit.color));
			}
			else if (originalGraph.regions.containsKey(originalRegionId))
			{
				nortantis.platform.Color color = originalGraph.regions.get(originalRegionId).backgroundColor;
				newEdits.regionEdits.put(originalRegionId, new RegionEdit(originalRegionId, color));
			}
		}
	}

	private static void transferText(MapEdits originalEdits, Rectangle selectionBoundsRI, MapEdits newEdits, int newGenWidth, int newGenHeight, double zoomFactor)
	{
		// Copy MapText entries whose location falls inside selectionBoundsRI.
		newEdits.text = new CopyOnWriteArrayList<>();
		for (MapText text : originalEdits.text)
		{
			if (selectionBoundsRI.containsOrOverlaps(text.location))
			{
				MapText newText = text.deepCopy();
				newText.location = transformRIPoint(text.location, selectionBoundsRI, newGenWidth, newGenHeight);
				// Clear bounds since they'll be recomputed at the new resolution.
				newText.line1Bounds = null;
				newText.line2Bounds = null;
				if (newText.fontOverride != null)
				{
					newText.fontOverride = scaleFontSize(newText.fontOverride, zoomFactor);
				}
				newEdits.text.add(newText);
			}
		}
	}

	private static void transferRoads(MapEdits originalEdits, Rectangle selectionBoundsRI, int newGenWidth, int newGenHeight, MapEdits newEdits)
	{
		// Clip each road to the selection boundary, inserting intersection points where
		// segments cross the edge so roads reach the map border instead of stopping short.
		for (Road road : originalEdits.roads)
		{
			for (List<Point> clippedPath : clipRoadPath(road.path, selectionBoundsRI, newGenWidth, newGenHeight))
			{
				newEdits.roads.add(new Road(clippedPath));
			}
		}
	}

	/**
	 * For each center in {@code newGraph}, samples its loc and all Voronoi corners in original-graph space and uses majority/plurality
	 * voting to assign water, lake, and region. Populates {@code newEdits.centerEdits} and mutates {@code newCenter.isWater} /
	 * {@code newCenter.isLake} (required before {@code updateCoastAndCornerFlags}).
	 *
	 * @return A map from original region ID to the list of new center indices assigned to that region.
	 */
	private static Map<Integer, List<Integer>> buildCenterEdits(WorldGraph newGraph, WorldGraph originalGraph, MapEdits originalEdits, Rectangle selectionBoundsRI, double originalResolution, MapEdits newEdits)
	{
		Map<Integer, List<Integer>> originalRegionToNewCenters = new HashMap<>();

		for (Center newCenter : newGraph.centers)
		{
			// Build sample points: center loc + all Voronoi corners, mapped to original-graph pixel space.
			List<Point> samplePoints = new ArrayList<>(newCenter.corners.size() + 1);
			samplePoints.add(mapToOriginalGraphPoint(newCenter.loc, newGraph, selectionBoundsRI, originalResolution));
			for (Corner corner : newCenter.corners)
			{
				samplePoints.add(mapToOriginalGraphPoint(corner.loc, newGraph, selectionBoundsRI, originalResolution));
			}

			// Tally votes from the original map for each sample point.
			int waterVotes = 0;
			int lakeVotes = 0;
			Map<Integer, Integer> regionVotes = new HashMap<>();
			for (Point samplePoint : samplePoints)
			{
				Center originalCenter = originalGraph.findClosestCenter(samplePoint, false);
				boolean sampleIsWater, sampleIsLake;
				Integer sampleRegionId;
				if (originalCenter != null && originalEdits.centerEdits.containsKey(originalCenter.index))
				{
					CenterEdit originalCenterEdit = originalEdits.centerEdits.get(originalCenter.index);
					sampleIsWater = originalCenterEdit.isWater;
					sampleIsLake = originalCenterEdit.isLake;
					sampleRegionId = originalCenterEdit.regionId;
				}
				else if (originalCenter != null)
				{
					sampleIsWater = originalCenter.isWater;
					sampleIsLake = originalCenter.isLake;
					sampleRegionId = originalCenter.region != null ? originalCenter.region.id : null;
				}
				else
				{
					sampleIsWater = true;
					sampleIsLake = false;
					sampleRegionId = null;
				}
				if (sampleIsWater) waterVotes++;
				if (sampleIsLake) lakeVotes++;
				if (sampleRegionId != null) regionVotes.merge(sampleRegionId, 1, Integer::sum);
			}

			// Majority vote: ≥50% water samples → water; ≥50% of water samples are lake → lake.
			boolean isWater = waterVotes * 2 >= samplePoints.size();
			boolean isLake = isWater && waterVotes > 0 && lakeVotes * 2 >= waterVotes;
			// Plurality vote for region: the region with the most sample-point votes wins.
			Integer regionId = null;
			int maxVotes = 0;
			for (Map.Entry<Integer, Integer> e : regionVotes.entrySet())
			{
				if (e.getValue() > maxVotes)
				{
					maxVotes = e.getValue();
					regionId = e.getKey();
				}
			}

			// Apply to the new center (required before updateCoastAndCornerFlags).
			newCenter.isWater = isWater;
			newCenter.isLake = isLake;

			if (regionId != null)
			{
				originalRegionToNewCenters.computeIfAbsent(regionId, k -> new ArrayList<>()).add(newCenter.index);
			}
			newEdits.centerEdits.put(newCenter.index, new CenterEdit(newCenter.index, isWater, isLake, regionId, null, null));
		}

		return originalRegionToNewCenters;
	}

	/**
	 * Transfers free icons from the original edits into {@code newEdits}. Cities and decorations are always copied by position.
	 * Mountains, hills, sand, and trees are either redistributed by center (if {@code redistributeIcons}) or copied by position.
	 */
	private static void transferFreeIcons(MapEdits originalEdits, WorldGraph originalGraph, WorldGraph newGraph, Rectangle selectionBoundsRI, double originalResolution, MapEdits newEdits,
			int newGenWidth, int newGenHeight, boolean redistributeIcons, long seed)
	{
		// Cities and decorations always copy by position, regardless of redistributeIcons.
		// They must be copied before redistribution so that redistribution can skip their centers.
		for (FreeIcon icon : originalEdits.freeIcons)
		{
			if (icon.type != IconType.cities && icon.type != IconType.decorations)
			{
				continue;
			}
			if (selectionBoundsRI.containsOrOverlaps(icon.locationResolutionInvariant))
			{
				Point newLoc = transformRIPoint(icon.locationResolutionInvariant, selectionBoundsRI, newGenWidth, newGenHeight);
				Integer newCenterIndex = null;
				if (icon.centerIndex != null)
				{
					Point newGraphPoint = new Point(newLoc.x * originalResolution, newLoc.y * originalResolution);
					Center nearestNewCenter = newGraph.findClosestCenter(newGraphPoint, false);
					if (nearestNewCenter != null)
					{
						newCenterIndex = nearestNewCenter.index;
					}
				}
				newEdits.freeIcons.addOrReplace(new FreeIcon(newLoc, icon.scale, icon.type, icon.artPack, icon.groupId, icon.iconIndex, icon.iconName, newCenterIndex, icon.density,
						icon.fillColor, icon.filterColor, icon.maximizeOpacity, icon.fillWithColor, icon.originalScale));
			}
		}

		if (redistributeIcons)
		{
			// Redistribute mountains, hills, sand, and trees based on per-center mapping.
			redistributeIconsByCenter(originalGraph, originalEdits, newGraph, selectionBoundsRI, originalResolution, newEdits, seed);
		}
		else
		{
			// Copy mountains, hills, sand, and trees by position (original behavior).
			for (FreeIcon icon : originalEdits.freeIcons)
			{
				if (icon.type == IconType.cities || icon.type == IconType.decorations)
				{
					continue;
				}
				if (selectionBoundsRI.containsOrOverlaps(icon.locationResolutionInvariant))
				{
					Point newLoc = transformRIPoint(icon.locationResolutionInvariant, selectionBoundsRI, newGenWidth, newGenHeight);
					Integer newCenterIndex = null;
					if (icon.centerIndex != null)
					{
						Point newGraphPoint = new Point(newLoc.x * originalResolution, newLoc.y * originalResolution);
						Center nearestNewCenter = newGraph.findClosestCenter(newGraphPoint, false);
						if (nearestNewCenter != null)
						{
							newCenterIndex = nearestNewCenter.index;
						}
					}
					newEdits.freeIcons.addOrReplace(new FreeIcon(newLoc, icon.scale, icon.type, icon.artPack, icon.groupId, icon.iconIndex, icon.iconName, newCenterIndex, icon.density,
							icon.fillColor, icon.filterColor, icon.maximizeOpacity, icon.fillWithColor, icon.originalScale));
				}
			}
		}
	}

	/**
	 * Redistributes mountains, hills, sand, and trees across the new graph's centers.
	 * <p>
	 * <b>Non-tree icons (mountains, hills, sand)</b> use a two-step approach:
	 * <ol>
	 * <li>Direct mapping: each original icon within the selection is placed at the nearest new center as a CenterIcon, so that
	 * IconDrawer will correctly compute position and scale for the new graph during rendering.</li>
	 * <li>Zoom-in expansion: for new centers that still have no icon after step 1, the new center's loc is mapped back to the original
	 * graph to check if that original center had an icon. If so, a CenterIcon with a random iconIndex is placed. This adds extra icons
	 * when the sub-map has more polygons than the original segment, preserving per-polygon density.</li>
	 * </ol>
	 * Centers that already have a non-tree icon (e.g. a city placed earlier) are always skipped.
	 * </p>
	 * <p>
	 * <b>Trees</b>: for each new center, the original center at its loc is found. If that original center has a {@code CenterTrees}
	 * (including dormant ones), it is copied to the new center with a fresh random seed. If there is no {@code CenterTrees} but the
	 * original center has visible tree FreeIcons, a {@code CenterTrees} is derived from those icons. Direct loc mapping naturally
	 * preserves density at any zoom level: many new centers that map to the same original tree center each receive their own
	 * {@code CenterTrees}, and IconDrawer handles per-polygon placement during rendering.
	 * </p>
	 */
	private static void redistributeIconsByCenter(WorldGraph originalGraph, MapEdits originalEdits, WorldGraph newGraph, Rectangle selectionBoundsRI, double originalResolution, MapEdits newEdits,
			long seed)
	{
		// --- Non-tree icons: Step 1 — direct position mapping. ---
		// For each original mountain/hill/sand icon within the selection, find the nearest new center and
		// place a CenterIcon there. Using CenterIcon (rather than FreeIcon) lets IconDrawer correctly
		// compute position (including the mountain Y offset) and scale for the new graph during rendering.
		for (FreeIcon icon : originalEdits.freeIcons)
		{
			if (icon.type == IconType.trees || icon.type == IconType.cities || icon.type == IconType.decorations)
			{
				continue;
			}
			if (!selectionBoundsRI.containsOrOverlaps(icon.locationResolutionInvariant))
			{
				continue;
			}
			// Use the original center's loc as the reference point rather than the icon's drawn position.
			// Mountain icons are offset upward from the polygon base by getAnchoredMountainDrawPoint, so
			// icon.locationResolutionInvariant is above the polygon centroid. Using it would select a new
			// center that is higher than the ones step 2 assigns, causing the step 1 mountain to appear
			// noticeably higher. Using the original center's centroid aligns step 1 with step 2's mapping.
			Point referenceRI;
			if (icon.centerIndex != null && icon.centerIndex < originalGraph.centers.size())
			{
				Center originalCenter = originalGraph.centers.get(icon.centerIndex);
				referenceRI = new Point(originalCenter.loc.x / originalResolution, originalCenter.loc.y / originalResolution);
			}
			else
			{
				referenceRI = icon.locationResolutionInvariant;
			}
			double newGraphX = (referenceRI.x - selectionBoundsRI.x) / selectionBoundsRI.width * newGraph.bounds.width;
			double newGraphY = (referenceRI.y - selectionBoundsRI.y) / selectionBoundsRI.height * newGraph.bounds.height;
			Center nearestNew = newGraph.findClosestCenter(new Point(newGraphX, newGraphY), false);
			if (nearestNew == null)
			{
				continue;
			}
			if (newEdits.freeIcons.getNonTree(nearestNew.index) != null)
			{
				continue; // city already there
			}
			CenterEdit nearestCenterEdit = newEdits.centerEdits.get(nearestNew.index);
			if (nearestCenterEdit == null || nearestCenterEdit.isWater || nearestCenterEdit.icon != null)
			{
				continue;
			}
			CenterIcon centerIcon = new CenterIcon(IconDrawer.iconTypeToCenterIconType(icon.type), icon.artPack, icon.groupId, icon.iconIndex);
			newEdits.centerEdits.put(nearestNew.index, nearestCenterEdit.copyWithIcon(centerIcon));
		}

		// Build lookup for step 2: original center index → non-tree FreeIcons (mountains, hills, sand).
		Map<Integer, List<FreeIcon>> originalCenterToIcons = new HashMap<>();
		for (FreeIcon icon : originalEdits.freeIcons)
		{
			if (icon.type == IconType.cities || icon.type == IconType.decorations || icon.type == IconType.trees)
			{
				continue;
			}
			int originalCenterIndex;
			if (icon.centerIndex != null)
			{
				originalCenterIndex = icon.centerIndex;
			}
			else
			{
				Point scaledPoint = new Point(icon.locationResolutionInvariant.x * originalResolution, icon.locationResolutionInvariant.y * originalResolution);
				Center nearest = originalGraph.findClosestCenter(scaledPoint, false);
				if (nearest == null)
				{
					continue;
				}
				originalCenterIndex = nearest.index;
			}
			originalCenterToIcons.computeIfAbsent(originalCenterIndex, k -> new ArrayList<>()).add(icon);
		}

		// Build lookup for tree redistribution: original center index → CenterTrees (includes dormant trees).
		// This is the primary source; visible tree FreeIcons are the fallback for centers whose CenterTrees
		// was cleared after being converted to FreeIcons.
		Map<Integer, CenterTrees> originalCenterToCenterTrees = new HashMap<>();
		for (Map.Entry<Integer, CenterEdit> entry : originalEdits.centerEdits.entrySet())
		{
			if (entry.getValue().trees != null)
			{
				originalCenterToCenterTrees.put(entry.getKey(), entry.getValue().trees);
			}
		}

		// Fallback: build lookup for visible tree FreeIcons on centers with no CenterTrees.
		Map<Integer, List<FreeIcon>> originalCenterToTreeIcons = new HashMap<>();
		for (FreeIcon icon : originalEdits.freeIcons)
		{
			if (icon.type != IconType.trees)
			{
				continue;
			}
			int originalCenterIndex;
			if (icon.centerIndex != null)
			{
				originalCenterIndex = icon.centerIndex;
			}
			else
			{
				Point scaledPoint = new Point(icon.locationResolutionInvariant.x * originalResolution, icon.locationResolutionInvariant.y * originalResolution);
				Center nearest = originalGraph.findClosestCenter(scaledPoint, false);
				if (nearest == null)
				{
					continue;
				}
				originalCenterIndex = nearest.index;
			}
			if (!originalCenterToCenterTrees.containsKey(originalCenterIndex))
			{
				originalCenterToTreeIcons.computeIfAbsent(originalCenterIndex, k -> new ArrayList<>()).add(icon);
			}
		}

		boolean hasNonTreeData = !originalCenterToIcons.isEmpty();
		boolean hasTreeData = !originalCenterToCenterTrees.isEmpty() || !originalCenterToTreeIcons.isEmpty();

		for (Center newCenter : newGraph.centers)
		{
			CenterEdit existingEdit = newEdits.centerEdits.get(newCenter.index);
			if (existingEdit != null && existingEdit.isWater)
			{
				continue;
			}

			Point locationInOriginalSpace = mapToOriginalGraphPoint(newCenter.loc, newGraph, selectionBoundsRI, originalResolution);
			Center originalCenterAtLocation = (hasNonTreeData || hasTreeData)
					? originalGraph.findClosestCenter(locationInOriginalSpace, false) : null;

			// --- Non-tree icons: Step 2 — zoom-in expansion. ---
			// For new centers not yet assigned by step 1, check whether their loc maps to an original
			// center that had an icon. If so, place a CenterIcon with a random iconIndex from the same
			// group, letting IconDrawer handle positioning and scaling during rendering.
			if (hasNonTreeData && newEdits.freeIcons.getNonTree(newCenter.index) == null
					&& (existingEdit == null || existingEdit.icon == null) && originalCenterAtLocation != null)
			{
				List<FreeIcon> iconsAtLocation = originalCenterToIcons.get(originalCenterAtLocation.index);
				if (iconsAtLocation != null)
				{
					FreeIcon icon = iconsAtLocation.get(0);
					int randomIconIndex = new Random(seed + newCenter.index).nextInt(Integer.MAX_VALUE);
					CenterIcon centerIcon = new CenterIcon(IconDrawer.iconTypeToCenterIconType(icon.type), icon.artPack, icon.groupId, randomIconIndex);
					newEdits.centerEdits.put(newCenter.index, existingEdit != null ? existingEdit.copyWithIcon(centerIcon) : new CenterEdit(newCenter.index, false, false, null, centerIcon, null));
					existingEdit = newEdits.centerEdits.get(newCenter.index);
				}
			}

			// --- Trees: direct mapping from original center. ---
			// Copy CenterTrees (including dormant) from the original center at this location. IconDrawer
			// naturally places trees at the right density for the new polygon sizes during rendering.
			// Fallback: if the original center has visible tree FreeIcons but no CenterTrees, derive
			// CenterTrees from those icons.
			if (hasTreeData && originalCenterAtLocation != null)
			{
				CenterTrees originalTrees = originalCenterToCenterTrees.get(originalCenterAtLocation.index);
				if (originalTrees != null)
				{
					CenterTrees newTrees = new CenterTrees(originalTrees.artPack, originalTrees.treeType, originalTrees.density, seed + newCenter.index, originalTrees.isDormant);
					CenterEdit current = newEdits.centerEdits.get(newCenter.index);
					if (current != null)
					{
						newEdits.centerEdits.put(newCenter.index, current.copyWithTrees(newTrees));
					}
				}
				else
				{
					List<FreeIcon> treeFreeIcons = originalCenterToTreeIcons.get(originalCenterAtLocation.index);
					if (treeFreeIcons != null && !treeFreeIcons.isEmpty())
					{
						String artPack = treeFreeIcons.get(0).artPack;
						String treeType = treeFreeIcons.get(0).groupId;
						double avgDensity = treeFreeIcons.stream().mapToDouble(t -> t.density).average().getAsDouble();
						CenterTrees newTrees = new CenterTrees(artPack, treeType, avgDensity, seed + newCenter.index);
						CenterEdit current = newEdits.centerEdits.get(newCenter.index);
						if (current != null)
						{
							newEdits.centerEdits.put(newCenter.index, current.copyWithTrees(newTrees));
						}
					}
				}
			}
		}
	}

	/**
	 * Maps a point from new-graph pixel space to original-graph pixel space.
	 */
	private static Point mapToOriginalGraphPoint(Point newGraphPoint, WorldGraph newGraph, Rectangle selectionBoundsRI, double originalResolution)
	{
		double originalX = (newGraphPoint.x / newGraph.bounds.width * selectionBoundsRI.width + selectionBoundsRI.x) * originalResolution;
		double originalY = (newGraphPoint.y / newGraph.bounds.height * selectionBoundsRI.height + selectionBoundsRI.y) * originalResolution;
		return new Point(originalX, originalY);
	}

	/**
	 * Transfers rivers from the original graph into the new graph's edge edits.
	 */
	private static void transferRivers(WorldGraph originalGraph, MapEdits originalEdits, WorldGraph newGraph, Rectangle selectionBoundsRI, MapEdits newEdits, double originalResolution)
	{
		// Transfer original river edges to the new graph.
		// Build set of river edges in the original (from edgeEdits overrides or edge.river).

		// Determine effective river level per edge in the original graph.
		Map<Integer, Integer> originalRiverLevels = new HashMap<>();
		for (Edge edge : originalGraph.edges)
		{
			if (edge.river > 0)
			{
				originalRiverLevels.put(edge.index, edge.river);
			}
		}
		for (EdgeEdit ee : originalEdits.edgeEdits.values())
		{
			if (ee.riverLevel > 0)
			{
				originalRiverLevels.put(ee.index, ee.riverLevel);
			}
			else if (ee.riverLevel == 0)
			{
				originalRiverLevels.remove(ee.index);
			}
		}

		if (originalRiverLevels.isEmpty())
		{
			return;
		}

		// For each original river edge, map its Voronoi corners (v0, v1) to the closest new-graph corners,
		// then trace the Voronoi-edge path between them. One original edge may span several new edges
		// (because the new graph is more detailed), so findPathGreedy gives the full chain. Adjacent
		// original edges share a corner; they map to the same new corner, so their paths connect.
		for (Map.Entry<Integer, Integer> entry : originalRiverLevels.entrySet())
		{
			int originalEdgeIndex = entry.getKey();
			int riverLevel = entry.getValue();

			Edge originalEdge = originalGraph.edges.get(originalEdgeIndex);
			if (originalEdge == null || originalEdge.v0 == null || originalEdge.v1 == null)
			{
				continue;
			}

			// Convert corner positions to RI space and check selection bounds.
			double corner0RIx = originalEdge.v0.loc.x / originalResolution;
			double corner0RIy = originalEdge.v0.loc.y / originalResolution;
			double corner1RIx = originalEdge.v1.loc.x / originalResolution;
			double corner1RIy = originalEdge.v1.loc.y / originalResolution;
			if (!selectionBoundsRI.contains(corner0RIx, corner0RIy) && !selectionBoundsRI.contains(corner1RIx, corner1RIy))
			{
				continue;
			}

			// Map each original corner to new-graph pixel space and find the closest new-graph corner.
			double newV0x = (corner0RIx - selectionBoundsRI.x) / selectionBoundsRI.width * newGraph.getWidth();
			double newV0y = (corner0RIy - selectionBoundsRI.y) / selectionBoundsRI.height * newGraph.getHeight();
			double newV1x = (corner1RIx - selectionBoundsRI.x) / selectionBoundsRI.width * newGraph.getWidth();
			double newV1y = (corner1RIy - selectionBoundsRI.y) / selectionBoundsRI.height * newGraph.getHeight();

			Corner newCorner0 = newGraph.findClosestCorner(new Point(newV0x, newV0y));
			Corner newCorner1 = newGraph.findClosestCorner(new Point(newV1x, newV1y));
			if (newCorner0 == null || newCorner1 == null || newCorner0.equals(newCorner1))
			{
				continue;
			}

			// Trace the Voronoi-edge path between the two new corners and mark every edge on the path
			// as a river at the original river level.
			Set<Edge> pathEdges = newGraph.findPathGreedy(newCorner0, newCorner1);
			for (Edge pathEdge : pathEdges)
			{
				newEdits.edgeEdits.put(pathEdge.index, new EdgeEdit(pathEdge.index, riverLevel));
			}
		}
	}

	private static final float maxFontSize = 240f;

	/**
	 * Returns a copy of the given font with its size multiplied by {@code factor}, capped at {@link #maxFontSize}.
	 */
	private static Font scaleFontSize(Font font, double factor)
	{
		float newSize = Math.min(maxFontSize, (float) (font.getSize() * factor));
		return font.deriveFont(font.getStyle(), newSize);
	}

	/**
	 * Transforms a point from original RI space to new RI space.
	 */
	private static Point transformRIPoint(Point sourcePointRI, Rectangle selectionBoundsRI, int newGenWidth, int newGenHeight)
	{
		double newX = (sourcePointRI.x - selectionBoundsRI.x) / selectionBoundsRI.width * newGenWidth;
		double newY = (sourcePointRI.y - selectionBoundsRI.y) / selectionBoundsRI.height * newGenHeight;
		return new Point(newX, newY);
	}

	/**
	 * Clips a road's RI-coordinate path to the selection rectangle, inserting intersection points at the boundary where segments cross
	 * it. Returns a list of sub-paths (each with >= 2 points) in new-map RI coordinates, ready to become Road objects.
	 */
	private static List<List<Point>> clipRoadPath(List<Point> path, Rectangle selectionBounds, int newWidth, int newHeight)
	{
		List<List<Point>> result = new ArrayList<>();
		if (path.isEmpty())
		{
			return result;
		}

		List<Point> current = new ArrayList<>();
		boolean prevInside = selectionBounds.contains(path.get(0));
		if (prevInside)
		{
			current.add(transformRIPoint(path.get(0), selectionBounds, newWidth, newHeight));
		}

		for (int i = 1; i < path.size(); i++)
		{
			Point prev = path.get(i - 1);
			Point curr = path.get(i);
			boolean currInside = selectionBounds.contains(curr);

			if (prevInside && currInside)
			{
				current.add(transformRIPoint(curr, selectionBounds, newWidth, newHeight));
			}
			else if (prevInside && !currInside)
			{
				// Exiting: add exit intersection at the boundary, then close current sub-path.
				Point exit = segmentBoundaryIntersection(prev, curr, selectionBounds);
				if (exit != null)
				{
					current.add(transformRIPoint(exit, selectionBounds, newWidth, newHeight));
				}
				if (current.size() >= 2)
				{
					result.add(new ArrayList<>(current));
				}
				current.clear();
			}
			else if (!prevInside && currInside)
			{
				// Entering: start a new sub-path from the entry intersection.
				Point entry = segmentBoundaryIntersection(prev, curr, selectionBounds);
				if (entry != null)
				{
					current.add(transformRIPoint(entry, selectionBounds, newWidth, newHeight));
				}
				current.add(transformRIPoint(curr, selectionBounds, newWidth, newHeight));
			}
			else
			{
				// Both outside: the segment may still pass through the rectangle.
				List<Point> throughPoints = segmentThroughIntersections(prev, curr, selectionBounds);
				if (throughPoints.size() == 2)
				{
					List<Point> subPath = new ArrayList<>();
					subPath.add(transformRIPoint(throughPoints.get(0), selectionBounds, newWidth, newHeight));
					subPath.add(transformRIPoint(throughPoints.get(1), selectionBounds, newWidth, newHeight));
					result.add(subPath);
				}
			}

			prevInside = currInside;
		}

		if (current.size() >= 2)
		{
			result.add(current);
		}
		return result;
	}

	/**
	 * When both endpoints of segment P1→P2 are outside {@code rect}, finds the two boundary intersection points (ordered from P1 to
	 * P2) if the segment passes through the rectangle. Returns an empty list if there are fewer than two distinct intersections.
	 */
	private static List<Point> segmentThroughIntersections(Point p1, Point p2, Rectangle rect)
	{
		double dx = p2.x - p1.x;
		double dy = p2.y - p1.y;
		List<double[]> hits = new ArrayList<>(); // each entry: { t, x, y }

		// Left edge
		if (dx != 0)
		{
			double t = (rect.x - p1.x) / dx;
			if (t > 0 && t < 1)
			{
				double y = p1.y + t * dy;
				if (y >= rect.y && y <= rect.getBottom())
				{
					hits.add(new double[] { t, rect.x, y });
				}
			}
		}

		// Right edge
		if (dx != 0)
		{
			double t = (rect.getRight() - p1.x) / dx;
			if (t > 0 && t < 1)
			{
				double y = p1.y + t * dy;
				if (y >= rect.y && y <= rect.getBottom())
				{
					hits.add(new double[] { t, rect.getRight(), y });
				}
			}
		}

		// Top edge
		if (dy != 0)
		{
			double t = (rect.y - p1.y) / dy;
			if (t > 0 && t < 1)
			{
				double x = p1.x + t * dx;
				if (x >= rect.x && x <= rect.getRight())
				{
					hits.add(new double[] { t, x, rect.y });
				}
			}
		}

		// Bottom edge
		if (dy != 0)
		{
			double t = (rect.getBottom() - p1.y) / dy;
			if (t > 0 && t < 1)
			{
				double x = p1.x + t * dx;
				if (x >= rect.x && x <= rect.getRight())
				{
					hits.add(new double[] { t, x, rect.getBottom() });
				}
			}
		}

		hits.sort((a, b) -> Double.compare(a[0], b[0]));
		if (hits.size() >= 2)
		{
			double[] first = hits.get(0);
			double[] last = hits.get(hits.size() - 1);
			return List.of(new Point(first[1], first[2]), new Point(last[1], last[2]));
		}
		return List.of();
	}

	/**
	 * Returns the intersection point of segment P1→P2 with the boundary of {@code rect}. P1 and P2 should be on opposite sides (one
	 * inside, one outside). Returns null if no valid intersection is found.
	 */
	private static Point segmentBoundaryIntersection(Point p1, Point p2, Rectangle rect)
	{
		double dx = p2.x - p1.x;
		double dy = p2.y - p1.y;
		double bestT = Double.MAX_VALUE;
		Point bestPt = null;

		// Left edge: x = rect.x
		if (dx != 0)
		{
			double t = (rect.x - p1.x) / dx;
			if (t > 0 && t < 1)
			{
				double y = p1.y + t * dy;
				if (y >= rect.y && y <= rect.getBottom() && t < bestT)
				{
					bestT = t;
					bestPt = new Point(rect.x, y);
				}
			}
		}

		// Right edge: x = rect.getRight()
		if (dx != 0)
		{
			double t = (rect.getRight() - p1.x) / dx;
			if (t > 0 && t < 1)
			{
				double y = p1.y + t * dy;
				if (y >= rect.y && y <= rect.getBottom() && t < bestT)
				{
					bestT = t;
					bestPt = new Point(rect.getRight(), y);
				}
			}
		}

		// Top edge: y = rect.y
		if (dy != 0)
		{
			double t = (rect.y - p1.y) / dy;
			if (t > 0 && t < 1)
			{
				double x = p1.x + t * dx;
				if (x >= rect.x && x <= rect.getRight() && t < bestT)
				{
					bestT = t;
					bestPt = new Point(x, rect.y);
				}
			}
		}

		// Bottom edge: y = rect.getBottom()
		if (dy != 0)
		{
			double t = (rect.getBottom() - p1.y) / dy;
			if (t > 0 && t < 1)
			{
				double x = p1.x + t * dx;
				if (x >= rect.x && x <= rect.getRight() && t < bestT)
				{
					bestT = t;
					bestPt = new Point(x, rect.getBottom());
				}
			}
		}

		return bestPt;
	}
}
