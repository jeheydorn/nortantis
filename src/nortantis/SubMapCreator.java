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
import nortantis.graph.voronoi.VoronoiGraph;
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
	 * 		The original map settings.
	 * @param originalGraph
	 * 		The original world graph (used for land/water lookup).
	 * @param originalEdits
	 * 		The original map edits (used for land/water, region, text, icons, roads).
	 * @param selectionBoundsRI
	 * 		The selection bounds in resolution-invariant (RI) coordinates.
	 * @param subMapWorldSize
	 * 		The number of Voronoi polygons for the sub-map.
	 * @param originalResolution
	 * 		The resolution at which originalGraph was created (i.e. the display quality scale), used to convert resolution-invariant coordinates to originalGraph pixel coordinates.
	 * @return New MapSettings for the sub-map, with pre-populated edits.
	 */
	public static MapSettings createSubMapSettings(MapSettings originalSettings, WorldGraph originalGraph, MapEdits originalEdits, Rectangle selectionBoundsRI, int subMapWorldSize,
			double originalResolution, long seed, boolean redistributeIcons)
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
		// Combining both: fontScale = zoomFactor / pow(detailRatio, 0.25), clamped to [1.0, …] so fonts never
		// shrink below the source map's sizes, and capped at maxFontSize to prevent illegibly huge text.
		// The 0.25 exponent reduces the suppression effect so fonts stay larger at high polygon counts.
		double zoomFactor = (double) newGenWidth / selectionBoundsRI.width;
		double selectionArea = selectionBoundsRI.width * selectionBoundsRI.height;
		double originalMapArea = originalSettings.generatedWidth * (double) originalSettings.generatedHeight;
		double oneXWorldSize = originalSettings.worldSize * selectionArea / originalMapArea;
		double detailRatio = oneXWorldSize > 0 ? newWorldSize / oneXWorldSize : 1.0;
		double fontScale = Math.max(1.0, zoomFactor / Math.max(1.0, Math.pow(detailRatio, 0.25)));
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
	 * For each center in {@code newGraph}, samples its loc and all Voronoi corners in original-graph space and uses majority/plurality voting to assign water, lake, and region. Populates
	 * {@code newEdits.centerEdits} and mutates {@code newCenter.isWater} / {@code newCenter.isLake} (required before {@code updateCoastAndCornerFlags}).
	 *
	 * @return A map from original region ID to the list of new center indices assigned to that region.
	 */
	private static Map<Integer, List<Integer>> buildCenterEdits(WorldGraph newGraph, WorldGraph originalGraph, MapEdits originalEdits, Rectangle selectionBoundsRI, double originalResolution,
			MapEdits newEdits)
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
				if (sampleIsWater)
					waterVotes++;
				if (sampleIsLake)
					lakeVotes++;
				if (sampleRegionId != null)
					regionVotes.merge(sampleRegionId, 1, Integer::sum);
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
	 * Transfers free icons from the original edits into {@code newEdits}. Cities and decorations are always copied by position. Mountains, hills, sand, and trees are either redistributed by center
	 * (if {@code redistributeIcons}) or copied by position.
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
				newEdits.freeIcons.addOrReplace(
						new FreeIcon(newLoc, icon.scale, icon.type, icon.artPack, icon.groupId, icon.iconIndex, icon.iconName, newCenterIndex, icon.density, icon.fillColor, icon.filterColor,
								icon.maximizeOpacity, icon.fillWithColor, icon.originalScale));
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
					newEdits.freeIcons.addOrReplace(
							new FreeIcon(newLoc, icon.scale, icon.type, icon.artPack, icon.groupId, icon.iconIndex, icon.iconName, newCenterIndex, icon.density, icon.fillColor, icon.filterColor,
									icon.maximizeOpacity, icon.fillWithColor, icon.originalScale));
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
			Center originalCenterAtLocation = (hasNonTreeData || hasTreeData) ? originalGraph.findClosestCenter(locationInOriginalSpace, false) : null;

			// --- Non-tree icons: Step 2 — zoom-in expansion. ---
			// For new centers not yet assigned by step 1, check whether their loc maps to an original
			// center that had an icon. If so, place a CenterIcon with a random iconIndex from the same
			// group, letting IconDrawer handle positioning and scaling during rendering.
			if (hasNonTreeData && newEdits.freeIcons.getNonTree(newCenter.index) == null && (existingEdit == null || existingEdit.icon == null) && originalCenterAtLocation != null)
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
	 * <p>
	 * River edges are collected from EdgeEdits and reconstructed as ordered polylines (connected corner chains). Each edge in a polyline is transferred individually: its corners are converted to RI
	 * space and clipped to the selection boundary using actual line intersection (so rivers exit the map at the correct position), then mapped to sub-map corners for a findPathGreedy call. River
	 * levels are scaled up by the zoom factor and capped at {@link River#MAX_RIVER_LEVEL}.
	 * </p>
	 */
	private static void transferRivers(WorldGraph originalGraph, MapEdits originalEdits, WorldGraph newGraph, Rectangle selectionBoundsRI, MapEdits newEdits, double originalResolution)
	{
		// Collect visible river edges from EdgeEdits (the authoritative source for drawn rivers).
		// Uses the RIVERS_THIS_SIZE_OR_SMALLER_WILL_NOT_BE_DRAWN threshold to exclude invisible rivers
		// that old versions of MapSettings may have stored with level > 0.
		Map<Integer, Integer> riverLevels = new HashMap<>();
		for (EdgeEdit ee : originalEdits.edgeEdits.values())
		{
			if (ee.riverLevel > River.RIVERS_THIS_SIZE_OR_SMALLER_WILL_NOT_BE_DRAWN)
			{
				riverLevels.put(ee.index, ee.riverLevel);
			}
		}
		if (riverLevels.isEmpty())
		{
			return;
		}

		// Build corner → adjacent river-edges map for chain traversal.
		Map<Integer, List<Edge>> cornerToRiverEdges = new HashMap<>();
		Map<Integer, Corner> cornerByIndex = new HashMap<>();
		for (Map.Entry<Integer, Integer> entry : riverLevels.entrySet())
		{
			Edge edge = originalGraph.edges.get(entry.getKey());
			if (edge == null || edge.v0 == null || edge.v1 == null)
			{
				continue;
			}
			cornerToRiverEdges.computeIfAbsent(edge.v0.index, k -> new ArrayList<>()).add(edge);
			cornerToRiverEdges.computeIfAbsent(edge.v1.index, k -> new ArrayList<>()).add(edge);
			cornerByIndex.put(edge.v0.index, edge.v0);
			cornerByIndex.put(edge.v1.index, edge.v1);
		}

		// Segment endpoints: corners with river degree ≠ 2 (heads, mouths, confluences).
		Set<Integer> endpointIndices = new HashSet<>();
		for (Map.Entry<Integer, List<Edge>> entry : cornerToRiverEdges.entrySet())
		{
			if (entry.getValue().size() != 2)
			{
				endpointIndices.add(entry.getKey());
			}
		}

		// Compute zoom-based river level scale: rivers should appear proportionally wider when zoomed in.
		// Width ∝ sqrt(riverLevel), so scaling width by zoomFactor requires scaling level by zoomFactor².
		// When the sub-map has higher polygon density than a 1× equivalent (detailRatio > 1), rivers
		// are widened less, matching the same attenuation used for font scaling in transferText.
		// The floor of 1.0 ensures rivers are never narrower in the sub-map than in the source.
		double originalRIWidth = originalGraph.getWidth() / originalResolution;
		double originalRIHeight = originalGraph.getHeight() / originalResolution;
		double maxOriginalDim = Math.max(originalRIWidth, originalRIHeight);
		double maxSelectionDim = Math.max(selectionBoundsRI.width, selectionBoundsRI.height);
		double zoomFactor = maxSelectionDim > 0 ? maxOriginalDim / maxSelectionDim : 1.0;
		double originalMapArea = originalRIWidth * originalRIHeight;
		double selectionArea = selectionBoundsRI.width * selectionBoundsRI.height;
		double oneXWorldSize = originalMapArea > 0 ? originalGraph.centers.size() * selectionArea / originalMapArea : 1.0;
		double detailRatio = oneXWorldSize > 0 ? newGraph.centers.size() / oneXWorldSize : 1.0;
		double riverLevelScale = Math.max(1.0, zoomFactor * zoomFactor / Math.max(1.0, Math.pow(detailRatio, 0.5)));

		// Trace ordered polylines from each endpoint corner and transfer each edge individually.
		Set<Integer> processedEdgeIndices = new HashSet<>();
		for (int endpointIdx : endpointIndices)
		{
			Corner startCorner = cornerByIndex.get(endpointIdx);
			List<Edge> startEdges = cornerToRiverEdges.get(endpointIdx);
			if (startCorner == null || startEdges == null)
			{
				continue;
			}

			for (Edge startEdge : startEdges)
			{
				if (processedEdgeIndices.contains(startEdge.index))
				{
					continue;
				}

				// Walk the degree-2 chain, building ordered corner and edge lists.
				List<Corner> polylineCorners = new ArrayList<>();
				List<Edge> polylineEdges = new ArrayList<>();
				Corner currentCorner = startCorner;
				Edge currentEdge = startEdge;
				Set<Integer> visitedCorners = new HashSet<>();
				visitedCorners.add(currentCorner.index);
				polylineCorners.add(currentCorner);

				while (true)
				{
					processedEdgeIndices.add(currentEdge.index);
					Corner nextCorner = currentEdge.v0.index == currentCorner.index ? currentEdge.v1 : currentEdge.v0;
					polylineCorners.add(nextCorner);
					polylineEdges.add(currentEdge);

					if (endpointIndices.contains(nextCorner.index) || visitedCorners.contains(nextCorner.index))
					{
						break;
					}
					visitedCorners.add(nextCorner.index);

					List<Edge> nextEdges = cornerToRiverEdges.get(nextCorner.index);
					if (nextEdges == null)
					{
						break;
					}
					Edge nextEdge = null;
					for (Edge e : nextEdges)
					{
						if (e.index != currentEdge.index)
						{
							nextEdge = e;
							break;
						}
					}
					if (nextEdge == null)
					{
						break;
					}
					currentEdge = nextEdge;
					currentCorner = nextCorner;
				}

				transferPolylineToSubMap(polylineCorners, polylineEdges, riverLevels, riverLevelScale, selectionBoundsRI, newGraph, originalEdits, newEdits, originalResolution);
			}
		}

		// Fallback for isolated loops (all corners degree 2, no endpoints — extremely rare).
		for (Map.Entry<Integer, Integer> entry : riverLevels.entrySet())
		{
			if (processedEdgeIndices.contains(entry.getKey()))
			{
				continue;
			}
			Edge edge = originalGraph.edges.get(entry.getKey());
			if (edge == null || edge.v0 == null || edge.v1 == null)
			{
				continue;
			}
			int scaledLevel = Math.min(River.MAX_RIVER_LEVEL, (int) Math.round(entry.getValue() * riverLevelScale));
			transferRiverEdgeToSubMap(new Point(edge.v0.loc.x / originalResolution, edge.v0.loc.y / originalResolution),
					new Point(edge.v1.loc.x / originalResolution, edge.v1.loc.y / originalResolution), scaledLevel, selectionBoundsRI, newGraph, newEdits);
			processedEdgeIndices.add(entry.getKey());
		}
	}

	/**
	 * Transfers each edge of an ordered river polyline to the sub-map. Each source-map edge is processed individually: its RI-space corners are clipped to the selection boundary via actual line
	 * intersection (for accuracy), then mapped to sub-map corners for a findPathGreedy call. Finger pruning is applied per source-map edge (not to the entire combined polyline) so that
	 * fingers introduced by findPathGreedy are removed without accidentally pruning legitimate subsidiary rivers that share corners with adjacent segments.
	 * <p>
	 * For the terminal source edge whose endpoint is adjacent to water in the source map (lake or ocean mouth), the new-graph corner is chosen as the closest water-adjacent corner near the
	 * mapped position, so that the river reliably terminates at a lake or ocean in the sub-map.
	 * </p>
	 */
	private static void transferPolylineToSubMap(List<Corner> polylineCorners, List<Edge> polylineEdges, Map<Integer, Integer> riverLevels, double riverLevelScale, Rectangle selectionBoundsRI,
			WorldGraph newGraph, MapEdits originalEdits, MapEdits newEdits, double originalResolution)
	{
		Map<Integer, Integer> polylineEdgeLevels = new HashMap<>();

		for (int i = 0; i < polylineEdges.size(); i++)
		{
			int edgeLevel = riverLevels.getOrDefault(polylineEdges.get(i).index, 0);
			if (edgeLevel <= 0)
			{
				continue;
			}
			Corner v0 = polylineCorners.get(i);
			Corner v1 = polylineCorners.get(i + 1);
			double v0RIx = v0.loc.x / originalResolution;
			double v0RIy = v0.loc.y / originalResolution;
			double v1RIx = v1.loc.x / originalResolution;
			double v1RIy = v1.loc.y / originalResolution;
			boolean v0Inside = selectionBoundsRI.contains(v0RIx, v0RIy);
			boolean v1Inside = selectionBoundsRI.contains(v1RIx, v1RIy);

			if (!v0Inside && !v1Inside)
			{
				// Edge entirely outside; handle the case where it passes through the selection.
				List<Point> through = segmentThroughIntersections(new Point(v0RIx, v0RIy), new Point(v1RIx, v1RIy), selectionBoundsRI);
				if (through.size() == 2)
				{
					int scaledLevel = Math.min(River.MAX_RIVER_LEVEL, (int) Math.round(edgeLevel * riverLevelScale));
					Corner c0 = riToNewCorner(through.get(0), selectionBoundsRI, newGraph);
					Corner c1 = riToNewCorner(through.get(1), selectionBoundsRI, newGraph);
					if (c0 != null && c1 != null && !c0.equals(c1))
					{
						Map<Integer, Integer> segmentEdgeLevels = new HashMap<>();
						collectGreedyPathEdges(c0, c1, scaledLevel, newGraph, segmentEdgeLevels);
						pruneFingers(segmentEdgeLevels, c0, c1, newGraph);
						segmentEdgeLevels.forEach((k, v) -> polylineEdgeLevels.merge(k, v, Math::max));
					}
				}
				continue;
			}

			Point effectiveV0, effectiveV1;
			boolean stopAfter = false;
			if (v0Inside && v1Inside)
			{
				effectiveV0 = new Point(v0RIx, v0RIy);
				effectiveV1 = new Point(v1RIx, v1RIy);
			}
			else if (v0Inside)
			{
				// River exits the selection: use the line-intersection point so the river ends at the
				// correct map-edge position rather than snapping to the nearest boundary corner.
				Point intersection = segmentBoundaryIntersection(new Point(v0RIx, v0RIy), new Point(v1RIx, v1RIy), selectionBoundsRI);
				effectiveV0 = new Point(v0RIx, v0RIy);
				effectiveV1 = intersection != null ? intersection : new Point(v1RIx, v1RIy);
				stopAfter = true;
			}
			else
			{
				// River enters the selection: use the line-intersection point for accuracy.
				Point intersection = segmentBoundaryIntersection(new Point(v0RIx, v0RIy), new Point(v1RIx, v1RIy), selectionBoundsRI);
				effectiveV0 = intersection != null ? intersection : new Point(v0RIx, v0RIy);
				effectiveV1 = new Point(v1RIx, v1RIy);
			}

			Corner c0 = riToNewCorner(effectiveV0, selectionBoundsRI, newGraph);
			// For the terminal edge of the polyline (last edge, not clipped at the selection boundary),
			// if the source terminal corner is adjacent to water, seek the closest water-adjacent corner
			// in the new graph so the river reliably terminates at a lake or ocean.
			boolean isTerminalEdge = !stopAfter && i == polylineEdges.size() - 1;
			Corner c1;
			if (isTerminalEdge && isSourceCornerAdjacentToWater(v1, originalEdits))
			{
				c1 = riToNewCornerAdjacentToWater(effectiveV1, selectionBoundsRI, newGraph, newEdits);
			}
			else
			{
				c1 = riToNewCorner(effectiveV1, selectionBoundsRI, newGraph);
			}

			if (c0 != null && c1 != null && !c0.equals(c1))
			{
				int scaledLevel = Math.min(River.MAX_RIVER_LEVEL, (int) Math.round(edgeLevel * riverLevelScale));
				Map<Integer, Integer> segmentEdgeLevels = new HashMap<>();
				collectGreedyPathEdges(c0, c1, scaledLevel, newGraph, segmentEdgeLevels);
				pruneFingers(segmentEdgeLevels, c0, c1, newGraph);
				segmentEdgeLevels.forEach((k, v) -> polylineEdgeLevels.merge(k, v, Math::max));
			}
			if (stopAfter)
			{
				break;
			}
		}

		for (Map.Entry<Integer, Integer> entry : polylineEdgeLevels.entrySet())
		{
			newEdits.edgeEdits.put(entry.getKey(), new EdgeEdit(entry.getKey(), entry.getValue()));
		}
	}

	/**
	 * Runs findPathGreedy between c0 and c1, merging all result edges into edgeLevels (keeping the max level if an edge is already present).
	 */
	private static void collectGreedyPathEdges(Corner c0, Corner c1, int scaledLevel, WorldGraph newGraph, Map<Integer, Integer> edgeLevels)
	{
		Set<Edge> pathEdges = newGraph.findPathGreedy(c0, c1);
		for (Edge e : pathEdges)
		{
			edgeLevels.merge(e.index, scaledLevel, Math::max);
		}
	}

	/**
	 * Iteratively removes edges whose one endpoint has degree 1 in edgeLevels and is not startCorner or endCorner. This prunes finger branches without touching valid river endpoints or loops.
	 */
	private static void pruneFingers(Map<Integer, Integer> edgeLevels, Corner startCorner, Corner endCorner, WorldGraph newGraph)
	{
		boolean changed = true;
		while (changed)
		{
			changed = false;
			Map<Integer, Integer> cornerDegree = new HashMap<>();
			for (int edgeIdx : edgeLevels.keySet())
			{
				Edge e = newGraph.edges.get(edgeIdx);
				if (e.v0 != null)
					cornerDegree.merge(e.v0.index, 1, Integer::sum);
				if (e.v1 != null)
					cornerDegree.merge(e.v1.index, 1, Integer::sum);
			}
			for (Map.Entry<Integer, Integer> entry : cornerDegree.entrySet())
			{
				if (entry.getValue() == 1 && entry.getKey() != startCorner.index && entry.getKey() != endCorner.index)
				{
					for (Iterator<Integer> it = edgeLevels.keySet().iterator(); it.hasNext();)
					{
						int edgeIdx = it.next();
						Edge e = newGraph.edges.get(edgeIdx);
						if ((e.v0 != null && e.v0.index == entry.getKey()) || (e.v1 != null && e.v1.index == entry.getKey()))
						{
							it.remove();
							changed = true;
							break;
						}
					}
					if (changed)
						break;
				}
			}
		}
	}

	/**
	 * Maps two RI-space endpoints to sub-map corners via {@link #riToNewCorner} and runs findPathGreedy, assigning the given scaled river level to all resulting edges.
	 */
	private static void transferRiverEdgeToSubMap(Point v0RI, Point v1RI, int scaledLevel, Rectangle selectionBoundsRI, WorldGraph newGraph, MapEdits newEdits)
	{
		Corner newCorner0 = riToNewCorner(v0RI, selectionBoundsRI, newGraph);
		Corner newCorner1 = riToNewCorner(v1RI, selectionBoundsRI, newGraph);
		if (newCorner0 == null || newCorner1 == null || newCorner0.equals(newCorner1))
		{
			return;
		}
		Set<Edge> pathEdges = newGraph.findPathGreedy(newCorner0, newCorner1);
		for (Edge pathEdge : pathEdges)
		{
			newEdits.edgeEdits.put(pathEdge.index, new EdgeEdit(pathEdge.index, scaledLevel));
		}
	}

	/**
	 * Returns true if any center adjacent to {@code sourceCorner} is water (using originalEdits where present, otherwise the center's own flag).
	 */
	private static boolean isSourceCornerAdjacentToWater(Corner sourceCorner, MapEdits originalEdits)
	{
		for (Center c : sourceCorner.touches)
		{
			CenterEdit ce = originalEdits.centerEdits.get(c.index);
			boolean isWater = ce != null ? ce.isWater : c.isWater;
			if (isWater)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns true if any center adjacent to {@code newCorner} is water according to newEdits (falling back to the center's own flag).
	 */
	private static boolean isNewCornerAdjacentToWater(Corner newCorner, MapEdits newEdits)
	{
		for (Center c : newCorner.touches)
		{
			CenterEdit ce = newEdits.centerEdits.get(c.index);
			boolean isWater = ce != null ? ce.isWater : c.isWater;
			if (isWater)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Like {@link #riToNewCorner}, but if the closest corner is not adjacent to water, searches all new-graph corners for the closest one that is adjacent to water (according to newEdits).
	 * Falls back to the plain closest corner if no water-adjacent corner exists.
	 */
	private static Corner riToNewCornerAdjacentToWater(Point riPoint, Rectangle selectionBoundsRI, WorldGraph newGraph, MapEdits newEdits)
	{
		Corner closest = riToNewCorner(riPoint, selectionBoundsRI, newGraph);
		if (closest == null || isNewCornerAdjacentToWater(closest, newEdits))
		{
			return closest;
		}
		double clampedX = Math.max(selectionBoundsRI.x, Math.min(selectionBoundsRI.x + selectionBoundsRI.width, riPoint.x));
		double clampedY = Math.max(selectionBoundsRI.y, Math.min(selectionBoundsRI.y + selectionBoundsRI.height, riPoint.y));
		double newX = (clampedX - selectionBoundsRI.x) / selectionBoundsRI.width * newGraph.getWidth();
		double newY = (clampedY - selectionBoundsRI.y) / selectionBoundsRI.height * newGraph.getHeight();
		double bestDist = Double.MAX_VALUE;
		Corner bestCorner = closest;
		for (Corner corner : newGraph.corners)
		{
			if (isNewCornerAdjacentToWater(corner, newEdits))
			{
				double dx = corner.loc.x - newX;
				double dy = corner.loc.y - newY;
				double dist = dx * dx + dy * dy;
				if (dist < bestDist)
				{
					bestDist = dist;
					bestCorner = corner;
				}
			}
		}
		return bestCorner;
	}

	/**
	 * Maps an RI-space point to the closest corner in the new graph. Clamps to the selection bounds as a safety net for floating-point edge cases from intersection calculations.
	 */
	private static Corner riToNewCorner(Point riPoint, Rectangle selectionBoundsRI, WorldGraph newGraph)
	{
		double clampedX = Math.max(selectionBoundsRI.x, Math.min(selectionBoundsRI.x + selectionBoundsRI.width, riPoint.x));
		double clampedY = Math.max(selectionBoundsRI.y, Math.min(selectionBoundsRI.y + selectionBoundsRI.height, riPoint.y));
		double newX = (clampedX - selectionBoundsRI.x) / selectionBoundsRI.width * newGraph.getWidth();
		double newY = (clampedY - selectionBoundsRI.y) / selectionBoundsRI.height * newGraph.getHeight();
		return newGraph.findClosestCorner(new Point(newX, newY));
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
	 * Clips a road's RI-coordinate path to the selection rectangle, inserting intersection points at the boundary where segments cross it. Returns a list of sub-paths (each with >= 2 points) in
	 * new-map RI coordinates, ready to become Road objects.
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
	 * When both endpoints of segment P1→P2 are outside {@code rect}, finds the two boundary intersection points (ordered from P1 to P2) if the segment passes through the rectangle. Returns an empty
	 * list if there are fewer than two distinct intersections.
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
	 * Returns the intersection point of segment P1→P2 with the boundary of {@code rect}. P1 and P2 should be on opposite sides (one inside, one outside). Returns null if no valid intersection is
	 * found.
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
