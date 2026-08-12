package nortantis;

import nortantis.editor.*;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.geom.RotatedRectangle;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.graph.voronoi.Edge;
import nortantis.platform.Font;
import nortantis.swing.MapEdits;
import nortantis.util.Helper;
import nortantis.util.OrderlessPair;
import nortantis.util.Tuple2;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Creates a new MapSettings for a zoomed-in sub-map of an existing map. The sub-map inherits the original map's land/water shape, region
 * colors, text, icons, and roads within the selected area.
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
	 * @param selectionBoundsRI
	 *            The selection bounds in resolution-invariant (RI) coordinates.
	 * @param subMapWorldSize
	 *            The number of Voronoi polygons for the sub-map.
	 * @param originalResolution
	 *            The resolution at which originalGraph was created (i.e. the display quality scale), used to convert resolution-invariant
	 *            coordinates to originalGraph pixel coordinates.
	 * @return New MapSettings for the sub-map, with pre-populated edits.
	 */
	public static MapSettings createSubMapSettings(MapSettings originalSettings, WorldGraph originalGraph, Rectangle selectionBoundsRI, int subMapWorldSize, double originalResolution, long seed,
			boolean redistributeIconsAndRivers)
	{
		return createSubMapSettings(originalSettings, originalGraph, selectionBoundsRI, subMapWorldSize, originalResolution, seed, redistributeIconsAndRivers, null);
	}

	public static MapSettings createSubMapSettings(MapSettings originalSettings, WorldGraph originalGraph, Rectangle selectionBoundsRI, int subMapWorldSize, double originalResolution, long seed,
			boolean redistributeIconsAndRivers, String originalFileName)
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
		// A sub-map is a brand-new map created in the current version, so it should carry the current version rather than inheriting the
		// original map's (possibly older) version from the deep copy. Otherwise opening the sub-map would treat it as an upgraded older map.
		newSettings.version = MapSettings.currentVersion;
		// Likewise, the sub-map's polygons are generated fresh, so use the current defaults rather than inheriting values the original map
		// only had for backwards compatibility.
		newSettings.lloydRelaxationsScale = MapSettings.defaultLloydRelaxationsScale;
		newSettings.pointPrecision = MapSettings.defaultPointPrecision;
		newSettings.randomSeed = seed;
		newSettings.generatedWidth = newGenWidth;
		newSettings.generatedHeight = newGenHeight;
		newSettings.worldSize = newWorldSize;
		// Export and display settings are specific to the original map and should not carry over to a brand-new sub-map.
		newSettings.imageExportPath = null;
		newSettings.heightmapExportPath = null;
		newSettings.heightmapResolution = MapSettings.defaultHeightmapResolution;
		newSettings.defaultMapExportAction = MapSettings.defaultDefaultExportAction;
		newSettings.defaultHeightmapExportAction = MapSettings.defaultDefaultExportAction;
		// No rotation/flip on the sub-map.
		newSettings.rightRotationCount = 0;
		newSettings.flipHorizontally = false;
		newSettings.flipVertically = false;

		// Record provenance so the user can recreate this sub-map later. Assign unconditionally so that if the original map was itself a
		// sub-map (carrying its own subMapInfo via deepCopyExceptEdits), it is overwritten with this sub-map's info.
		MapSettings.SubMapInfo subMapInfo = new MapSettings.SubMapInfo();
		subMapInfo.originalFileName = originalFileName;
		subMapInfo.selectionX = selectionBoundsRI.x;
		subMapInfo.selectionY = selectionBoundsRI.y;
		subMapInfo.selectionWidth = selectionBoundsRI.width;
		subMapInfo.selectionHeight = selectionBoundsRI.height;
		subMapInfo.worldSize = newWorldSize;
		subMapInfo.randomSeed = seed;
		subMapInfo.redistributeIconsAndRivers = redistributeIconsAndRivers;
		newSettings.subMapInfo = subMapInfo;

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
		newSettings.roadFont = scaleFontSize(newSettings.roadFont, fontScale);
		// Initialize fresh empty edits so createGraphForUnitTests will create elevation (isInitialized=false).
		newSettings.edits = new MapEdits();
		// The sub-map graph must be built at originalResolution so the RI ↔ pixel conversions used when transferring rivers and icons
		// (which divide graph-pixel coordinates by originalResolution) land in the correct sub-map RI space.
		newSettings.resolution = originalResolution;

		// Build the WorldGraph for the sub-map (to get center positions and count).
		// We call this with createElevationBiomesLakesAndRegions=false because land/water and icon placement will be determined by the
		// source map, not by a new, generated world.
		// This gives us the same Voronoi structure MapCreator will use when rendering (same seed, same params).
		WorldGraph newGraph = MapCreator.createGraph(newSettings, false);

		// For each new center, use majority/plurality voting to assign water/lake/region.
		MapEdits newEdits = new MapEdits();
		// Rivers are populated directly by transferRivers; prevent MapCreator from re-initializing them from the new graph.
		newEdits.hasInitializedRivers = true;
		Map<Integer, List<Integer>> originalRegionToNewCenters = buildCenterEdits(newGraph, originalGraph, originalSettings.edits, selectionBoundsRI, originalResolution, newEdits);

		// Propagate coast/corner flags now that isWater/isLake are set on all centers.
		// markLakes must run first so that updateCoastAndCornerFlags sees the correct isLake values
		// when computing numOcean (ocean = isWater && !isLake); otherwise lake-shore corners get
		// isCoast instead of isWater, which is semantically wrong even though the avoidCorner
		// predicate catches both.
		newGraph.markLakes();
		newGraph.updateCoastAndCornerFlags();

		// Smooth newGraph's coastlines and rebuild its noisy edges to match what MapCreator does when it actually renders the sub-map (see
		// applyCenterEdits, which smooths the coast after applying water edits). Without this, newGraph keeps the raw, unsmoothed
		// coastline,
		// so the exact-copy mouth anchoring (anchorExactCopyMouths in transferRivers) would snap mouths to corners on a coast in a
		// different
		// place than the one drawn, leaving them visibly short of the rendered (smoothed) ocean. Smoothing only moves corner locations; it
		// does not change which centers are water, so the transferred edits remain consistent.
		Set<Center> centersChangedBySmoothing = newGraph.smoothCoastlinesAndRegionBoundariesIfNeeded(newGraph.centers, newGraph.noisyEdges.getLineStyle(), newSettings.areRegionBoundariesVisible());
		for (Center center : centersChangedBySmoothing)
		{
			newGraph.rebuildNoisyEdgesForCenter(center, centersChangedBySmoothing);
		}
		// Build remaining MapEdits.

		transferRegionEdits(originalGraph, originalSettings.edits, originalRegionToNewCenters, newEdits);

		transferRivers(originalGraph, originalSettings.edits, newGraph, selectionBoundsRI, newEdits, originalResolution, redistributeIconsAndRivers, newGenWidth, newGenHeight);

		// City labels are repositioned to stay attached to their city icon (see transferText). iconSizeRatio is how much city icons shrink
		// in the sub-map (icon size scales with mean polygon width); sourceMeanPolygonWidthRI scales the label-to-icon association
		// distance;
		// sourceCityFontSize lets transferText estimate the label's text height so it can clear the icon.
		double iconSizeRatio = newGraph.getMeanCenterWidthBetweenNeighbors() / originalGraph.getMeanCenterWidthBetweenNeighbors();
		double sourceMeanPolygonWidthRI = originalGraph.getMeanCenterWidthBetweenNeighbors() / originalResolution;
		double sourceCityFontSize = originalSettings.citiesFont.getSize();

		transferText(originalSettings.edits, selectionBoundsRI, newEdits, newGenWidth, newGenHeight, fontScale, iconSizeRatio, sourceMeanPolygonWidthRI, sourceCityFontSize, originalResolution);

		// An IconDrawer over the source graph, used to compute each city/decoration icon's drawn bounds so icons that overlap the selection
		// are kept even when their anchor point lies just outside it (city labels sit below their icon, so a city near the top edge has its
		// icon above the selection but its image still reaching into it).
		//
		// The IconDrawer must use originalResolution, the scale originalGraph was built at, so its draw bounds (in graph pixels) convert
		// back
		// to RI correctly in doesIconOverlapSelection. originalSettings.resolution cannot be trusted here: in the editor getSettingsFromGUI
		// sets it to the export resolution, which differs from the display quality scale that originalGraph and originalResolution use.
		// Using
		// originalSettings.resolution then scaled icon positions by the wrong factor, placing every city/decoration outside the selection
		// so
		// they were all dropped. Copy the settings (sharing edits, which are already initialized so the IconDrawer does not wipe freeIcons)
		// and set the resolution to match originalGraph.
		MapSettings sourceSettingsAtGraphResolution = originalSettings.deepCopyExceptEdits();
		sourceSettingsAtGraphResolution.edits = originalSettings.edits;
		sourceSettingsAtGraphResolution.resolution = originalResolution;
		IconDrawer sourceIconDrawer = new IconDrawer(originalGraph, new Random(seed), sourceSettingsAtGraphResolution);

		transferFreeIcons(originalSettings.edits, originalGraph, newGraph, selectionBoundsRI, originalResolution, newEdits, newGenWidth, newGenHeight, redistributeIconsAndRivers, seed,
				sourceIconDrawer);
		newEdits.hasIconEdits = true;

		transferRoads(originalSettings.edits, selectionBoundsRI, newGenWidth, newGenHeight, newEdits);

		// Attach the new edits to the new settings.
		newSettings.edits = newEdits;

		// The sub-map graph work above needed newSettings.resolution set to originalResolution for its RI ↔ pixel conversions, but the
		// original map's display/export resolution should not persist on the finished sub-map. Reset it to the default used for a new map;
		// MapCreator.createMap will adjust it as needed when the sub-map is rendered.
		newSettings.resolution = MapSettings.defaultResolution;

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

	private static void transferText(MapEdits originalEdits, Rectangle selectionBoundsRI, MapEdits newEdits, int newGenWidth, int newGenHeight, double fontScale, double iconSizeRatio,
			double sourceMeanPolygonWidthRI, double sourceCityFontSize, double originalResolution)
	{
		// Copy MapText entries whose drawn extent intersects selectionBoundsRI.
		newEdits.text = new CopyOnWriteArrayList<>();
		double maxCityLabelAssociationDistanceRI = sourceMeanPolygonWidthRI * cityLabelToIconMaxAssociationWidths;
		for (MapText text : originalEdits.text)
		{
			if (doesTextOverlapSelection(text, selectionBoundsRI, originalResolution))
			{
				MapText newText = text.deepCopy();

				// A city label is positioned relative to its city icon rather than by the plain position transform, so it stays attached to
				// its icon instead of drifting away as the icon shrinks at higher detail. Its offset from the icon is scaled by the icon's
				// own size ratio, so the label hugs the icon the same way it did in the source. A small clearance is then added outward,
				// because the label's font is deliberately kept larger than the icon (fontScale > iconSizeRatio): without it the relatively
				// larger text would overlap the icon. The clearance is the extra text extent toward the icon — about half a line of text
				// times how much more the font scales than the icon. At match detail iconSizeRatio equals the position magnification and
				// the
				// clearance is zero, so this reduces to the plain transform (no change).
				FreeIcon cityIcon = text.type == TextType.City ? findNearestSourceCityIcon(text.location, originalEdits, selectionBoundsRI, maxCityLabelAssociationDistanceRI) : null;
				if (cityIcon != null)
				{
					Point sourceIconLocationRI = cityIcon.locationResolutionInvariant;
					Point newIconLocationRI = transformRIPoint(sourceIconLocationRI, selectionBoundsRI, newGenWidth, newGenHeight);
					Point sourceOffsetRI = text.location.subtract(sourceIconLocationRI);
					Point newOffsetRI = sourceOffsetRI.mult(iconSizeRatio);

					double offsetLength = Math.sqrt(sourceOffsetRI.x * sourceOffsetRI.x + sourceOffsetRI.y * sourceOffsetRI.y);
					if (offsetLength > 1e-6)
					{
						double sourceFontSize = text.fontOverride != null ? text.fontOverride.getSize() : sourceCityFontSize;
						// Rendered line height in RI units (the font size stored in the file is multiplied by
						// calcSizeMultiplierFromResolutionScale when drawn).
						double sourceLineHeightRI = sourceFontSize * MapCreator.calcSizeMultiplierFromResolutionScale(1.0);
						double clearance = cityLabelClearanceLineHeights * sourceLineHeightRI * Math.max(0, fontScale - iconSizeRatio);
						newOffsetRI = newOffsetRI.add(sourceOffsetRI.mult(clearance / offsetLength));
					}
					newText.location = newIconLocationRI.add(newOffsetRI);
				}
				else
				{
					newText.location = transformRIPoint(text.location, selectionBoundsRI, newGenWidth, newGenHeight);
				}

				// Clear bounds since they'll be recomputed at the new resolution.
				newText.line1Bounds = null;
				newText.line2Bounds = null;
				if (newText.fontOverride != null)
				{
					newText.fontOverride = scaleFontSize(newText.fontOverride, fontScale);
				}
				newEdits.text.add(newText);
			}
		}
	}

	/**
	 * Returns true if the given text should be included in the sub-map, i.e. its drawn extent intersects the selection. Text whose box
	 * straddles the selection boundary is included even though part of it extends outside the selection; only text entirely outside the
	 * selection is excluded.
	 *
	 * The text's drawn bounds ({@link MapText#line1Bounds}/{@link MapText#line2Bounds}) are a rendering cache in pixel coordinates at the
	 * draw resolution, which equals {@code originalResolution} (the scale originalGraph was created at). The selection is scaled into that
	 * same pixel space for the overlap test. If the bounds have not been populated yet (e.g. the text has never been drawn), we fall back
	 * to testing the text's center point.
	 */
	private static boolean doesTextOverlapSelection(MapText text, Rectangle selectionBoundsRI, double originalResolution)
	{
		RotatedRectangle line1Bounds = text.line1Bounds;
		RotatedRectangle line2Bounds = text.line2Bounds;
		if (line1Bounds == null && line2Bounds == null)
		{
			// No rendered bounds available, so fall back to the text's center point.
			return selectionBoundsRI.containsOrOverlaps(text.location);
		}

		RotatedRectangle selectionBoundsPixels = new RotatedRectangle(selectionBoundsRI.scaleAboutOrigin(originalResolution));
		return (line1Bounds != null && selectionBoundsPixels.overlaps(line1Bounds)) || (line2Bounds != null && selectionBoundsPixels.overlaps(line2Bounds));
	}

	/** Maximum distance, in multiples of the source mean polygon width, a city label may be from a city icon to be associated with it. */
	private static final double cityLabelToIconMaxAssociationWidths = 6.0;

	/**
	 * How much of a city label's rendered line height counts as its extent toward its icon, used to clear the label off the icon (see
	 * transferText). Roughly half a line (a centered label sits about half a line height from its near edge), tuned so labels clear their
	 * icons without drifting noticeably farther than the source.
	 */
	private static final double cityLabelClearanceLineHeights = 0.5;

	/**
	 * Returns the source city icon ({@link IconType#cities}) that {@code labelLocationRI} annotates, or {@code null} if none qualifies. The
	 * label's icon is taken to be the globally nearest city icon within {@code maxDistanceRI}; it is returned only if that icon lies inside
	 * the selection (so it is transferred and the label can be repositioned relative to it). Searching globally — rather than only among
	 * in-selection icons — is important: a label whose own icon is just outside the selection must NOT be re-paired with a different
	 * neighboring city's icon and dragged across the map, so in that case we return null and the caller leaves the label at its plain
	 * transformed position.
	 */
	private static FreeIcon findNearestSourceCityIcon(Point labelLocationRI, MapEdits originalEdits, Rectangle selectionBoundsRI, double maxDistanceRI)
	{
		FreeIcon nearest = null;
		double nearestDistance = maxDistanceRI;
		for (FreeIcon icon : originalEdits.freeIcons)
		{
			if (icon.type != IconType.cities)
			{
				continue;
			}
			double distance = labelLocationRI.distanceTo(icon.locationResolutionInvariant);
			if (distance <= nearestDistance)
			{
				nearestDistance = distance;
				nearest = icon;
			}
		}
		if (nearest != null && !selectionBoundsRI.containsOrOverlaps(nearest.locationResolutionInvariant))
		{
			return null;
		}
		return nearest;
	}

	private static void transferRoads(MapEdits originalEdits, Rectangle selectionBoundsRI, int newGenWidth, int newGenHeight, MapEdits newEdits)
	{
		// Clip each road to the selection boundary, inserting intersection points where
		// segments cross the edge so roads reach the map border instead of stopping short.
		for (Road road : originalEdits.roads)
		{
			List<Point> roadLocations = PathOperations.toLocationList(road.nodes);
			for (List<Point> clippedPath : clipRoadPath(roadLocations, selectionBoundsRI, newGenWidth, newGenHeight))
			{
				newEdits.roads.add(Road.fromLocations(clippedPath));
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
	 * Transfers free icons from the original edits into {@code newEdits}. Cities and decorations are always copied by position. Mountains,
	 * hills, sand, and trees are either redistributed by center (if {@code redistributeIconsAndRivers}) or copied by position.
	 */
	private static void transferFreeIcons(MapEdits originalEdits, WorldGraph originalGraph, WorldGraph newGraph, Rectangle selectionBoundsRI, double originalResolution, MapEdits newEdits,
			int newGenWidth, int newGenHeight, boolean redistributeIconsAndRivers, long seed, IconDrawer sourceIconDrawer)
	{
		// Cities and decorations always copy by position, regardless of redistributeIconsAndRivers.
		// They must be copied before redistribution so that redistribution can skip their centers.
		for (FreeIcon icon : originalEdits.freeIcons)
		{
			if (icon.type != IconType.cities && icon.type != IconType.decorations)
			{
				continue;
			}
			// Keep the icon if its drawn image overlaps the selection, not just its anchor point — otherwise a city whose icon sits just
			// outside the selection (e.g. above the top edge, with its label below inside it) would be dropped even though it is visible.
			if (doesIconOverlapSelection(icon, sourceIconDrawer, selectionBoundsRI, originalResolution))
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
				newEdits.freeIcons.addOrReplace(new FreeIcon(newLoc, icon.scale, icon.type, icon.artPack, icon.groupId, icon.iconIndex, icon.iconName, newCenterIndex, icon.density, icon.fillColor,
						icon.filterColor, icon.maximizeOpacity, icon.fillWithColor, icon.originalScale));
			}
		}

		if (redistributeIconsAndRivers)
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
					newEdits.freeIcons.addOrReplace(new FreeIcon(newLoc, icon.scale, icon.type, icon.artPack, icon.groupId, icon.iconIndex, icon.iconName, null, icon.density, icon.fillColor,
							icon.filterColor, icon.maximizeOpacity, icon.fillWithColor, icon.originalScale));
				}
			}
		}
	}

	/**
	 * Returns true if {@code icon}'s drawn image overlaps {@code selectionBoundsRI}. The icon's drawn bounds are computed with
	 * {@code sourceIconDrawer} (source-graph pixel coordinates) and converted to RI. If the image cannot be loaded (e.g. a missing art
	 * pack), this falls back to testing the icon's anchor point so the icon is still transferred when its anchor is inside the selection.
	 */
	private static boolean doesIconOverlapSelection(FreeIcon icon, IconDrawer sourceIconDrawer, Rectangle selectionBoundsRI, double originalResolution)
	{
		IconDrawTask task = sourceIconDrawer.toIconDrawTask(icon);
		if (task == null)
		{
			return selectionBoundsRI.containsOrOverlaps(icon.locationResolutionInvariant);
		}
		Rectangle boundsPixels = task.createBounds();
		Rectangle boundsRI = new Rectangle(boundsPixels.x / originalResolution, boundsPixels.y / originalResolution, boundsPixels.width / originalResolution, boundsPixels.height / originalResolution);
		return selectionBoundsRI.overlaps(boundsRI);
	}

	/**
	 * Redistributes mountains, hills, sand, and trees across the new graph's centers.
	 * <p>
	 * <b>Non-tree icons (mountains, hills, sand)</b> use a two-step approach:
	 * <ol>
	 * <li>Direct mapping: each original icon within the selection is placed at the nearest new center as a CenterIcon, so that IconDrawer
	 * will correctly compute position and scale for the new graph during rendering.</li>
	 * <li>Zoom-in expansion: for new centers that still have no icon after step 1, the new center's loc is mapped back to the original
	 * graph to check if that original center had an icon. If so, a CenterIcon with a random iconIndex is placed. This adds extra icons when
	 * the sub-map has more polygons than the original segment, preserving per-polygon density.</li>
	 * </ol>
	 * Centers that already have a non-tree icon (e.g. a city placed earlier) are always skipped.
	 * </p>
	 * <p>
	 * <b>Trees</b>: for each new center, the original center at its loc is found. If that original center has a {@code CenterTrees}
	 * (including dormant ones), it is copied to the new center with a fresh random seed. If there is no {@code CenterTrees} but the
	 * original center has visible tree FreeIcons, a {@code CenterTrees} is derived from those icons. Direct loc mapping naturally preserves
	 * density at any zoom level: many new centers that map to the same original tree center each receive their own {@code CenterTrees}, and
	 * IconDrawer handles per-polygon placement during rendering.
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
			CenterIcon centerIcon = new CenterIcon(IconDrawer.iconTypeToCenterIconType(icon.type), icon.artPack, icon.groupId, icon.iconIndex).copyWithColors(iconColorsOf(icon));
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

		// Replant the source's ANCHORED trees the same way the theme panel does when the tree height changes (see
		// IconDrawer.rebuildAnchoredTrees), so dormant trees (places the user marked for trees that did not grow at the source's low
		// density)
		// are handled consistently: visible trees become CenterTrees at their anchors, dormant trees near a visible tree are kept and
		// re-seeded as non-dormant so they can grow at the sub-map's polygon sizes, and dormant trees far from any visible tree are
		// dropped.
		// Work on a copy so the source edits are not mutated, and seed it so sub-map creation stays deterministic.
		MapEdits replantedSource = originalEdits.deepCopy();
		IconDrawer.rebuildAnchoredTrees(replantedSource, originalGraph, new Random(seed));

		// Build lookup for tree redistribution: original center index → replanted (non-dormant) CenterTrees.
		Map<Integer, CenterTrees> originalCenterToCenterTrees = new HashMap<>();
		for (Map.Entry<Integer, CenterEdit> entry : replantedSource.centerEdits.entrySet())
		{
			if (entry.getValue().trees != null)
			{
				originalCenterToCenterTrees.put(entry.getKey(), entry.getValue().trees);
			}
		}

		// rebuildAnchoredTrees only handles anchored trees. Unanchored tree FreeIcons (centerIndex == null) are not represented by any
		// CenterTrees, so redistribute them like mountains: map each to the original center it sits on and derive a CenterTrees there
		// (unless that center already has replanted trees). Without this, unanchored trees disappear from the sub-map.
		Map<Integer, List<FreeIcon>> originalCenterToUnanchoredTreeIcons = new HashMap<>();
		for (FreeIcon icon : originalEdits.freeIcons)
		{
			if (icon.type != IconType.trees || icon.centerIndex != null)
			{
				continue;
			}
			Point scaledPoint = new Point(icon.locationResolutionInvariant.x * originalResolution, icon.locationResolutionInvariant.y * originalResolution);
			Center nearest = originalGraph.findClosestCenter(scaledPoint, false);
			if (nearest == null)
			{
				continue;
			}
			if (!originalCenterToCenterTrees.containsKey(nearest.index))
			{
				originalCenterToUnanchoredTreeIcons.computeIfAbsent(nearest.index, k -> new ArrayList<>()).add(icon);
			}
		}

		boolean hasNonTreeData = !originalCenterToIcons.isEmpty();
		boolean hasTreeData = !originalCenterToCenterTrees.isEmpty() || !originalCenterToUnanchoredTreeIcons.isEmpty();

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
					int randomIconIndex = new Random(Helper.mixSeed(seed + newCenter.index)).nextInt(Integer.MAX_VALUE);
					CenterIcon centerIcon = new CenterIcon(IconDrawer.iconTypeToCenterIconType(icon.type), icon.artPack, icon.groupId, randomIconIndex).copyWithColors(iconColorsOf(icon));
					newEdits.centerEdits.put(newCenter.index, existingEdit != null ? existingEdit.copyWithIcon(centerIcon) : new CenterEdit(newCenter.index, false, false, null, centerIcon, null));
					existingEdit = newEdits.centerEdits.get(newCenter.index);
				}
			}

			// --- Trees: direct mapping from original center. ---
			// Copy the replanted (non-dormant) CenterTrees from the original center at this location. IconDrawer naturally places trees at
			// the right density for the new polygon sizes during rendering. Fallback: if the original center had only unanchored tree
			// FreeIcons (no CenterTrees), derive a CenterTrees from those icons.
			if (hasTreeData && originalCenterAtLocation != null)
			{
				CenterTrees originalTrees = originalCenterToCenterTrees.get(originalCenterAtLocation.index);
				if (originalTrees != null)
				{
					CenterTrees newTrees = new CenterTrees(originalTrees.artPack, originalTrees.treeType, originalTrees.density, Helper.mixSeed(seed + newCenter.index), originalTrees.isDormant,
							originalTrees.colors);
					CenterEdit current = newEdits.centerEdits.get(newCenter.index);
					if (current != null)
					{
						newEdits.centerEdits.put(newCenter.index, current.copyWithTrees(newTrees));
					}
				}
				else
				{
					List<FreeIcon> treeFreeIcons = originalCenterToUnanchoredTreeIcons.get(originalCenterAtLocation.index);
					if (treeFreeIcons != null && !treeFreeIcons.isEmpty())
					{
						String artPack = treeFreeIcons.get(0).artPack;
						String treeType = treeFreeIcons.get(0).groupId;
						double avgDensity = treeFreeIcons.stream().mapToDouble(t -> t.density).average().getAsDouble();
						// Keep the source trees' colors so the redistributed trees match instead of using the sub-map's per-type tree
						// color.
						CenterTrees newTrees = new CenterTrees(artPack, treeType, avgDensity, Helper.mixSeed(seed + newCenter.index), false, iconColorsOf(treeFreeIcons.get(0)));
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
	 * Captures the four color properties of a source {@link FreeIcon} so a redistributed {@link CenterIcon}/{@link CenterTrees} can be
	 * drawn with the colors of the icon it came from rather than the sub-map's per-type icon colors.
	 */
	private static IconColors iconColorsOf(FreeIcon icon)
	{
		return new IconColors(icon.fillColor, icon.filterColor, icon.maximizeOpacity, icon.fillWithColor);
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
	 * Transfers rivers from the original map into {@code newEdits.rivers}.
	 * <p>
	 * <b>This is intended, load-bearing behavior — do not collapse the two branches.</b> {@code redistributeIconsAndRivers} mirrors the
	 * detail-level choice in {@link nortantis.swing.SubMapDialog}: it is {@code false} for "Match source detail" and {@code true} for the
	 * custom "Choose" detail level (more polygons than the source). The two cases deliberately differ:
	 * </p>
	 * <ul>
	 * <li><b>Match source detail ({@code redistributeIconsAndRivers == false}):</b> each river path is clipped to the selection and
	 * transformed directly into sub-map RI coordinates (the same coordinate transform used for roads), reproducing the original river
	 * faithfully as a freehand {@link River} polyline. The source path is authoritative and is never reshaped or dropped here.</li>
	 * <li><b>Choose / custom detail ({@code redistributeIconsAndRivers == true}):</b> rivers are <em>redistributed</em> — each clipped
	 * sub-path is re-routed through the sub-map's finer graph via {@link #routeClippedRiverToSubMap} so they follow the new polygon
	 * topology, matching how icons are redistributed across the new polygons in this mode. This is why the Choose detail level looks
	 * different from a magnified copy.</li>
	 * </ul>
	 * <p>
	 * Either way {@link #removeDuplicateRiverSegments} trims overlaps where arms meet at a confluence.
	 * </p>
	 */
	private static void transferRivers(WorldGraph originalGraph, MapEdits originalEdits, WorldGraph newGraph, Rectangle selectionBoundsRI, MapEdits newEdits, double originalResolution,
			boolean redistributeIconsAndRivers, int newGenWidth, int newGenHeight)
	{
		if (originalEdits.rivers.isEmpty())
		{
			return;
		}

		double riverLevelScale = computeRiverLevelScale(originalGraph, originalResolution, selectionBoundsRI, newGraph);

		if (!redistributeIconsAndRivers)
		{
			// Match source detail: copy each river over exactly, clipped to the selection and transformed into sub-map coordinates. The
			// river body is reproduced faithfully — its shape is preserved and it is never dropped, even where it runs into ocean or lakes.
			// (No loop removal here: the source path is authoritative, and a complex source river can legitimately revisit points.)
			//
			// One adjustment: the sub-map is drawn on a freshly generated Voronoi grid, so its coast and lakeshore boundaries differ
			// slightly from the source. A river mouth copied at its exact source position can therefore stop short of the redrawn water,
			// which looks broken. For each river end that was a water mouth in the source, we snap it onto the nearest sub-map mouth corner
			// and anchor it there (see anchorExactCopyMouths), so it lands on the redrawn coast and tracks it across any later line-style
			// change.
			// Ends with no mouth corner nearby, and ends cut off at the selection boundary (map-edge exits, not mouths), are left alone.
			for (River river : originalEdits.rivers)
			{
				Point sourceFirst = river.nodes.get(0).getLoc();
				Point sourceLast = river.nodes.get(river.nodes.size() - 1).getLoc();
				boolean sourceStartIsMouth = selectionBoundsRI.contains(sourceFirst.x, sourceFirst.y) && isRIPointAdjacentToWater(sourceFirst, originalGraph, originalEdits, originalResolution);
				boolean sourceEndIsMouth = selectionBoundsRI.contains(sourceLast.x, sourceLast.y) && isRIPointAdjacentToWater(sourceLast, originalGraph, originalEdits, originalResolution);

				List<River> clippedSubPaths = clipRiverPath(river, selectionBoundsRI, newGenWidth, newGenHeight, riverLevelScale);
				for (int s = 0; s < clippedSubPaths.size(); s++)
				{
					boolean startIsMouth = s == 0 && sourceStartIsMouth;
					boolean endIsMouth = s == clippedSubPaths.size() - 1 && sourceEndIsMouth;
					newEdits.rivers.add(anchorExactCopyMouths(clippedSubPaths.get(s), startIsMouth, endIsMouth, newGraph, originalResolution));
				}
			}
		}
		else
		{
			// Choose / custom detail: redistribute rivers by re-routing them through the new, finer graph.
			if (DebugFlags.highlightSubMapRiverWaypoints())
			{
				DebugFlags.clearSubMapRiverWaypointCornerIndexes();
			}
			for (River river : originalEdits.rivers)
			{
				// A source endpoint is a coastal mouth if it lies inside the selection and is adjacent to water in the
				// source map. (An endpoint that exits via a selection boundary is a map-edge exit, not a mouth, and is
				// excluded by the contains() check.) Only the original river's true endpoints can be mouths — interior
				// clip boundaries are map-edge exits — so the first clipped sub-path's start and the last sub-path's end
				// are the only candidates.
				Point sourceFirst = river.nodes.get(0).getLoc();
				Point sourceLast = river.nodes.get(river.nodes.size() - 1).getLoc();
				boolean sourceStartIsMouth = selectionBoundsRI.contains(sourceFirst.x, sourceFirst.y) && isRIPointAdjacentToWater(sourceFirst, originalGraph, originalEdits, originalResolution);
				boolean sourceEndIsMouth = selectionBoundsRI.contains(sourceLast.x, sourceLast.y) && isRIPointAdjacentToWater(sourceLast, originalGraph, originalEdits, originalResolution);

				List<River> clippedSubPaths = clipRiverPath(river, selectionBoundsRI, newGenWidth, newGenHeight, riverLevelScale);
				for (int s = 0; s < clippedSubPaths.size(); s++)
				{
					boolean startIsMouth = s == 0 && sourceStartIsMouth;
					boolean endIsMouth = s == clippedSubPaths.size() - 1 && sourceEndIsMouth;
					River routedRiver = routeClippedRiverToSubMap(clippedSubPaths.get(s), startIsMouth, endIsMouth, newGraph, newEdits, originalResolution);
					if (routedRiver != null)
					{
						newEdits.rivers.add(routedRiver);
					}
				}
			}
		}

		removeDuplicateRiverSegments(newEdits.rivers);
	}

	/**
	 * Anchors an exact-copied river's mouth(s) to the sub-map's redrawn coast so it lands on — and stays on — the water.
	 * {@code startIsMouth} / {@code endIsMouth} mark which ends were water mouths in the source map (and are not selection-boundary
	 * cut-offs). For each such end, the endpoint is snapped onto the nearest sub-map mouth corner (a corner adjacent to both water and
	 * land) and anchored to it, so the mouth sits exactly on the drawn coast/lakeshore and tracks it across any later line-style change in
	 * the sub-map. A mouth with no mouth corner nearby (e.g. a small water body that did not survive the regrid) is left as-is. Returns a
	 * new {@link River} if an endpoint was changed, otherwise the input {@code clippedSubPath} unchanged.
	 */
	private static River anchorExactCopyMouths(River clippedSubPath, boolean startIsMouth, boolean endIsMouth, WorldGraph newGraph, double resolution)
	{
		List<RiverPathNode> nodes = new ArrayList<>(clippedSubPath.nodes);
		if (nodes.size() < 2)
		{
			return clippedSubPath;
		}
		boolean changed = false;

		if (startIsMouth)
		{
			changed |= anchorMouthEndpointToShore(nodes, 0, newGraph, resolution);
		}
		if (endIsMouth)
		{
			changed |= anchorMouthEndpointToShore(nodes, nodes.size() - 1, newGraph, resolution);
		}

		return changed ? new River(nodes) : clippedSubPath;
	}

	/**
	 * Snaps the river endpoint at {@code index} onto the nearest sub-map mouth corner and anchors it there, so an exact-copied mouth lands
	 * on (and stays on) the redrawn coast/lakeshore. Does nothing — leaving the endpoint exactly as copied — when no mouth corner is within
	 * {@link #mouthWaterSearchMaxCenterHops} centers, so a mouth genuinely far from the redrawn water is not dragged across the map.
	 * Returns true if the node was changed.
	 */
	private static boolean anchorMouthEndpointToShore(List<RiverPathNode> nodes, int index, WorldGraph newGraph, double resolution)
	{
		RiverPathNode node = nodes.get(index);
		Corner corner = findNearestCornerWithinCenterHops(newGraph, node.getLoc().mult(resolution), mouthWaterSearchMaxCenterHops, RiverDrawer::isMouthCorner);
		if (corner == null || corner.loc == null)
		{
			return false;
		}
		Point cornerRI = corner.loc.mult(1.0 / resolution);
		nodes.set(index, new RiverPathNode(cornerRI, node.getWidthLevelToNext(), node.getSeedToNext(), node.getEdgeIndexToNext(), corner.index));
		return true;
	}

	/**
	 * Returns the nearest corner satisfying {@code predicate} to {@code pixel} (graph/pixel coordinates), searching outward breadth-first
	 * over center neighbors up to {@code maxCenterHops} hops, or {@code null} if none is found within that bound. The hop bound keeps the
	 * search local: a river mouth whose source water body (e.g. a small lake) did not survive the regrid is left where it was rather than
	 * dragged across many polygons to a distant, unrelated shore.
	 */
	private static Corner findNearestCornerWithinCenterHops(WorldGraph graph, Point pixel, int maxCenterHops, Predicate<Corner> predicate)
	{
		Center startCenter = graph.findClosestCenter(pixel, true);
		if (startCenter == null)
		{
			return null;
		}
		Set<Center> visited = new HashSet<>();
		visited.add(startCenter);
		List<Center> frontier = new ArrayList<>();
		frontier.add(startCenter);
		Corner nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (int hop = 0; hop <= maxCenterHops; hop++)
		{
			List<Center> nextFrontier = new ArrayList<>();
			for (Center center : frontier)
			{
				for (Corner corner : center.corners)
				{
					if (corner.loc == null || !predicate.test(corner))
					{
						continue;
					}
					double distance = corner.loc.distanceTo(pixel);
					if (distance < nearestDistance)
					{
						nearestDistance = distance;
						nearest = corner;
					}
				}
				for (Center neighbor : center.neighbors)
				{
					if (visited.add(neighbor))
					{
						nextFrontier.add(neighbor);
					}
				}
			}
			frontier = nextFrontier;
		}
		return nearest;
	}

	/**
	 * Returns true if {@code pixelPoint} (graph/pixel coordinates) reaches the drawn water: it lies on a water polygon, or on land within
	 * {@link #mouthReachToleranceInPixels} pixels (sampled in the eight compass directions) of one. Polygon membership is resolved with
	 * {@link WorldGraph#findClosestCenter}, which follows the drawn (noisy-edge) coastline, so this detects a sub-polygon gap between a
	 * river mouth and the redrawn shore that corner-level water-adjacency cannot see. A center counts as water by its own {@code isWater}
	 * flag, which is set on all centers by the time rivers are transferred (and on any freshly built graph). This is the shared test used
	 * both to decide whether a mouth connector is needed and, in unit tests, to assert that mouths reach the redrawn coast.
	 */
	public static boolean doesPointReachWater(Point pixelPoint, WorldGraph graph)
	{
		Center center = graph.findClosestCenter(pixelPoint, true);
		if (center != null && center.isWater)
		{
			return true;
		}
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				if (dx == 0 && dy == 0)
				{
					continue;
				}
				Center neighbor = graph.findClosestCenter(new Point(pixelPoint.x + dx * mouthReachToleranceInPixels, pixelPoint.y + dy * mouthReachToleranceInPixels), true);
				if (neighbor != null && neighbor.isWater)
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Distance, in rendered pixels, within which a river mouth is considered to still reach the drawn water (see
	 * {@link #doesPointReachWater}).
	 */
	private static final double mouthReachToleranceInPixels = 1.0;

	/**
	 * Maximum number of Voronoi centers a river mouth is searched outward (breadth-first over center neighbors) to find a sub-map shore
	 * corner to attach to — used both when anchoring an exact-copied mouth to the redrawn coast and when snapping a redistributed mouth to
	 * a water-adjacent corner. The bound keeps the search local so a mouth whose source water body (e.g. a small lake) did not survive the
	 * regrid is left ending inland rather than connected by a long invented river to a distant, unrelated body of water.
	 */
	private static final int mouthWaterSearchMaxCenterHops = 3;

	/**
	 * Removes duplicated segments where two transferred rivers overlap. Rivers are routed through the new graph independently, so where a
	 * tributary meets a trunk its first or last segment can land on the same pair of corners the trunk already passes through. Drawn as two
	 * overlapping Catmull-Rom curves, such a duplicate renders as a small loop at the confluence. Trimming the overlapping segment from the
	 * tributary's end leaves the two rivers sharing a single confluence corner, which is the correct topology.
	 */
	/**
	 * Removes overlaps where two rivers share a segment (the same pair of control points), which would otherwise draw as two overlapping
	 * river curves. Each shared segment survives in exactly one river; every other river that used it is split there (the shared segment is
	 * dropped from it), so the segment is drawn once. This handles a shared segment whether it is terminal in one river and interior in the
	 * other (a confluence) or interior to both (two rivers crossing). The surviving copy is chosen to be a widest occurrence of the segment,
	 * so when overlapping rivers differ in width the wider one wins; ties prefer to keep the segment where it is interior (leaving the
	 * confluence's trunk whole and trimming the tributary's terminal segment).
	 */
	private static void removeDuplicateRiverSegments(List<River> rivers)
	{
		// Group every segment occurrence by its undirected endpoint pair. Each occurrence records {riverIndex, segmentIndex, width,
		// isTerminal} so the survivor can be chosen by width and interior-vs-terminal position.
		Map<OrderlessPair<Point>, List<int[]>> occurrencesBySegment = new HashMap<>();
		for (int ri = 0; ri < rivers.size(); ri++)
		{
			List<RiverPathNode> nodes = rivers.get(ri).nodes;
			for (int i = 0; i + 1 < nodes.size(); i++)
			{
				boolean isTerminal = i == 0 || i + 2 == nodes.size();
				occurrencesBySegment.computeIfAbsent(new OrderlessPair<>(nodes.get(i).getLoc(), nodes.get(i + 1).getLoc()), k -> new ArrayList<>())
						.add(new int[] { ri, i, nodes.get(i).getWidthLevelToNext(), isTerminal ? 1 : 0 });
			}
		}

		// Choose which occurrences survive. Only overlaps between different rivers are resolved: a segment used by two or more rivers keeps
		// exactly one occurrence -- widest, then interior over terminal, then earliest (occurrences are in river then segment order, so the
		// first encountered wins remaining ties). A segment used only within a single river (a self-revisit) is left intact, since an
		// exact-copied complex source river may legitimately revisit a segment and splitting it is not this method's job.
		Set<Long> survivingOccurrences = new HashSet<>();
		for (List<int[]> occurrences : occurrencesBySegment.values())
		{
			int firstRiver = occurrences.get(0)[0];
			boolean spansMultipleRivers = false;
			for (int[] candidate : occurrences)
			{
				if (candidate[0] != firstRiver)
				{
					spansMultipleRivers = true;
					break;
				}
			}

			if (!spansMultipleRivers)
			{
				for (int[] occurrence : occurrences)
				{
					survivingOccurrences.add(occurrenceToken(occurrence[0], occurrence[1]));
				}
				continue;
			}

			int[] best = occurrences.get(0);
			for (int[] candidate : occurrences)
			{
				if (candidate[2] > best[2] || (candidate[2] == best[2] && candidate[3] < best[3]))
				{
					best = candidate;
				}
			}
			survivingOccurrences.add(occurrenceToken(best[0], best[1]));
		}

		// Rebuild each river, splitting it wherever it uses a segment that survives in a different occurrence. Each maximal run of surviving
		// segments becomes a river; runs shorter than a drawable length are dropped.
		List<River> result = new ArrayList<>();
		for (int ri = 0; ri < rivers.size(); ri++)
		{
			List<RiverPathNode> nodes = rivers.get(ri).nodes;
			List<RiverPathNode> currentPiece = new ArrayList<>();
			for (int i = 0; i + 1 < nodes.size(); i++)
			{
				if (survivingOccurrences.contains(occurrenceToken(ri, i)))
				{
					if (currentPiece.isEmpty())
					{
						currentPiece.add(nodes.get(i));
					}
					currentPiece.add(nodes.get(i + 1));
				}
				else
				{
					if (currentPiece.size() >= 2)
					{
						result.add(new River(new ArrayList<>(currentPiece)));
					}
					currentPiece.clear();
				}
			}
			if (currentPiece.size() >= 2)
			{
				result.add(new River(new ArrayList<>(currentPiece)));
			}
		}

		rivers.clear();
		rivers.addAll(result);
	}

	private static long occurrenceToken(int riverIndex, int segmentIndex)
	{
		return ((long) riverIndex << 32) | (segmentIndex & 0xFFFFFFFFL);
	}

	/**
	 * Computes a scale factor for river levels when transferring from the original graph to the sub-map.
	 * <p>
	 * Rivers should appear proportionally wider when zoomed in. Width ∝ sqrt(riverLevel), so scaling width by zoomFactor requires scaling
	 * level by zoomFactor². When the sub-map has higher polygon density than a 1× equivalent (detailRatio > 1), rivers are widened less,
	 * matching the same attenuation used for font scaling in transferText. The floor of 1.0 ensures rivers are never narrower in the
	 * sub-map than in the source.
	 * </p>
	 */
	private static double computeRiverLevelScale(WorldGraph originalGraph, double originalResolution, Rectangle selectionBoundsRI, WorldGraph newGraph)
	{
		double originalRIWidth = originalGraph.getWidth() / originalResolution;
		double originalRIHeight = originalGraph.getHeight() / originalResolution;
		double maxOriginalDim = Math.max(originalRIWidth, originalRIHeight);
		double maxSelectionDim = Math.max(selectionBoundsRI.width, selectionBoundsRI.height);
		double zoomFactor = maxSelectionDim > 0 ? maxOriginalDim / maxSelectionDim : 1.0;
		double originalMapArea = originalRIWidth * originalRIHeight;
		double selectionArea = selectionBoundsRI.width * selectionBoundsRI.height;
		double oneXWorldSize = originalMapArea > 0 ? originalGraph.centers.size() * selectionArea / originalMapArea : 1.0;
		double detailRatio = oneXWorldSize > 0 ? newGraph.centers.size() / oneXWorldSize : 1.0;
		return Math.max(1.0, zoomFactor * zoomFactor / Math.max(1.0, Math.pow(detailRatio, 0.5)));
	}

	/**
	 * Cost multiplier applied to a freehand jump (a straight segment that follows no Voronoi edge) in the global river router's A* search,
	 * relative to the jump's straight-line length. The search prefers graph routes whenever an all-graph route through the remaining
	 * waypoints costs less than {@code freehandJumpPenalty}× the jump it would replace, so it only jumps where the land is genuinely
	 * disconnected or a graph detour would be more than this many times longer. Higher values make jumps rarer (and allow the search to
	 * take longer graph detours, including rerouting an earlier leg, to avoid one).
	 */
	private static final double freehandJumpPenalty = 5.0;

	/**
	 * Re-routes one clipped sub-path of a source river through the new graph as a single freehand {@link River}.
	 * <p>
	 * The sub-path's nodes are in sub-map RI coordinates with per-segment width levels (from {@link #clipRiverPath}). Each node is snapped
	 * to the closest new-graph corner to form an ordered list of <em>waypoints</em>; a start/end node that is a coastal mouth is snapped
	 * instead to the nearest water-adjacent corner so the river reliably reaches the sea. Consecutive duplicate waypoints, and waypoints
	 * whose corner was already used earlier in this river, are dropped so the waypoint list stays simple.
	 * </p>
	 * <p>
	 * The waypoints are then threaded by a single global A* search (see {@link #routeWaypointsGlobally}) rather than routing each
	 * consecutive pair independently. Independent per-leg routing can wall itself off — an earlier leg's locally-optimal path can consume
	 * the only graph corridor a later leg needs, forcing an unnecessary freehand jump — and no per-leg search can fix that because it
	 * cannot trade a longer earlier leg against a blocked later one. The global search minimizes total cost across the whole river, so it
	 * will make an earlier leg longer to keep a later one routable, and only emits a freehand jump where the land is genuinely
	 * disconnected.
	 * </p>
	 * Returns the built {@link River}, or {@code null} if fewer than two distinct waypoints survive.
	 */
	private static River routeClippedRiverToSubMap(River clippedSubPath, boolean startIsMouth, boolean endIsMouth, WorldGraph newGraph, MapEdits newEdits, double resolution)
	{
		List<RiverPathNode> clippedNodes = clippedSubPath.nodes;
		if (clippedNodes.size() < 2)
		{
			return null;
		}

		// Route in a canonical direction so a sub-path and its exact reverse produce identical geometry. The router below is deterministic
		// but direction-dependent (the A* over the ordered waypoints, and removeCornerRevisits, resolve equal-cost choices differently
		// depending on which end is the start). A source river that retraces the same corners out and back — e.g. up a valley and back down —
		// is clipped into two sub-paths that are reverses of each other; routed in opposite directions they diverge into a lens-shaped loop
		// that was not in the source. Fixing the traversal direction makes both route the same way, so removeDuplicateRiverSegments then
		// collapses the retrace to a single line. The canonical direction is the one whose first node is not lexicographically greater than
		// its last, which is stable under reversal.
		if (comparePointsLexicographically(clippedNodes.get(0).getLoc(), clippedNodes.get(clippedNodes.size() - 1).getLoc()) > 0)
		{
			clippedNodes = reverseRiverNodes(clippedNodes);
			boolean startWasMouth = startIsMouth;
			startIsMouth = endIsMouth;
			endIsMouth = startWasMouth;
		}

		// --- Snap each clipped node to a new-graph corner to form the waypoint list. ---
		Predicate<Corner> isWaterAdjacent = c -> isNewCornerAdjacentToWater(c, newEdits);
		List<Corner> waypoints = new ArrayList<>();
		// Width level for the leg leaving each waypoint, taken from the clipped node it was snapped from.
		List<Integer> waypointWidths = new ArrayList<>();
		Set<Corner> waypointCorners = new HashSet<>();
		for (int i = 0; i < clippedNodes.size(); i++)
		{
			RiverPathNode node = clippedNodes.get(i);
			// The clipped node is in sub-map RI; RI × resolution is new-graph pixel space.
			Point newGraphPoint = node.getLoc().mult(resolution);
			boolean isEndpoint = i == 0 || i == clippedNodes.size() - 1;
			boolean isMouthNode = (i == 0 && startIsMouth) || (i == clippedNodes.size() - 1 && endIsMouth);
			Corner corner;
			if (isMouthNode)
			{
				// Snap the mouth to a nearby shore corner the river can actually reach along land: one adjacent to water (so it sits on
				// the coast) AND with at least one pure-land neighbor, so the router can step onto it from a land corner. A water-adjacent
				// corner with no land neighbor is a shore point walled off by other coast/water corners; the router (which won't step
				// through coast/ocean/water except the target itself) can only reach it by a freehand jump across the water, which renders
				// as a straight line cutting to the sea. Searching outward is bounded to a few centers (the same limit exact-copy mode
				// uses): if the source water body (e.g. a small lake) did not survive the regrid, a distant shore corner would invent a
				// long river to unrelated water. When none is within reach we fall back to the plain nearest corner so the river ends
				// where its water was.
				corner = findNearestCornerWithinCenterHops(newGraph, newGraphPoint, mouthWaterSearchMaxCenterHops, c -> isWaterAdjacent.test(c) && hasLandNeighbor(c));
				if (corner == null)
				{
					corner = newGraph.findClosestCorner(newGraphPoint);
				}
			}
			else if (isEndpoint && isPointOnMapBorder(node.getLoc(), newGraph, resolution))
			{
				// A river endpoint sitting on the selection boundary is a map-edge exit: the source river continued past the selection, so
				// on the sub-map it should run off the map edge. The nearest corner is usually a polygon inside the edge (border corners are
				// sparse), which leaves the river stopping short of the edge. Snap to the nearest border corner instead so it reaches the
				// edge, falling back to the plain nearest corner if there are none.
				corner = findNearestBorderCorner(newGraph, newGraphPoint);
				if (corner == null)
				{
					corner = newGraph.findClosestCorner(newGraphPoint);
				}
			}
			else
			{
				corner = newGraph.findClosestCorner(newGraphPoint);
			}
			if (corner == null)
			{
				continue;
			}
			// Drop an interior waypoint that snapped onto a coast/ocean/water corner. The greedy router refuses to route
			// through such corners (only a start/end mouth is allowed to sit on one), so keeping it as an intermediate forces
			// the search into a dead-end turn followed by a pair of freehand hops, which renders as a tight swirl. Skipping it
			// lets the leg route cleanly from the previous inland waypoint to the next. The first and last waypoints are always
			// kept: they are the river's true endpoints (a coastal mouth or a map-edge exit).
			if (!isEndpoint && (corner.isCoast || corner.isOcean || corner.isWater))
			{
				continue;
			}
			// Collapse consecutive duplicates, and skip a corner already used earlier in this river so the path stays simple
			// even if the source sub-path revisits a location (redistribute mode may clean source loops, unlike exact-copy).
			if (!waypoints.isEmpty() && waypoints.get(waypoints.size() - 1).equals(corner))
			{
				continue;
			}
			if (waypointCorners.contains(corner))
			{
				continue;
			}
			waypoints.add(corner);
			waypointCorners.add(corner);
			waypointWidths.add(node.getWidthLevelToNext());
			if (DebugFlags.highlightSubMapRiverWaypoints())
			{
				DebugFlags.addSubMapRiverWaypointCornerIndex(corner.index);
			}
		}

		if (waypoints.size() < 2)
		{
			return null;
		}

		List<RiverPathNode> nodes = routeWaypointsGlobally(waypoints, waypointWidths, newGraph, resolution);
		if (nodes == null || nodes.size() < 2)
		{
			return null;
		}
		return new River(nodes);
	}

	/**
	 * Returns the border corner of {@code graph} nearest to {@code pixel} (graph/pixel coordinates), or {@code null} if the graph has no
	 * border corners. Used to snap a map-edge-exit river endpoint onto the map edge.
	 */
	private static Corner findNearestBorderCorner(WorldGraph graph, Point pixel)
	{
		Corner nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (Corner corner : graph.corners)
		{
			if (!corner.isBorder || corner.loc == null)
			{
				continue;
			}
			double distance = corner.loc.distanceTo(pixel);
			if (distance < nearestDistance)
			{
				nearestDistance = distance;
				nearest = corner;
			}
		}
		return nearest;
	}

	/**
	 * Returns true if {@code pointRI} (sub-map RI coordinates) lies on one of the four edges of the sub-map, within {@link #mapBorderEpsilonRI}
	 * of it. Clipping a source river to the selection places a boundary-crossing node exactly on an edge, so this identifies a river endpoint
	 * that is a map-edge exit (the river continued past the selection) rather than a mouth or an inland source.
	 */
	private static boolean isPointOnMapBorder(Point pointRI, WorldGraph newGraph, double resolution)
	{
		double widthRI = newGraph.getWidth() / resolution;
		double heightRI = newGraph.getHeight() / resolution;
		return pointRI.x <= mapBorderEpsilonRI || pointRI.x >= widthRI - mapBorderEpsilonRI || pointRI.y <= mapBorderEpsilonRI || pointRI.y >= heightRI - mapBorderEpsilonRI;
	}

	/**
	 * Tolerance, in resolution-invariant units, for treating a river endpoint as sitting on a map edge (see {@link #isPointOnMapBorder}).
	 */
	private static final double mapBorderEpsilonRI = 1.0;

	/**
	 * Orders two points by x, then y. Returns a negative number if {@code a} comes first, positive if {@code b} comes first, and 0 if they
	 * are equal. Used to pick a canonical traversal direction for a river sub-path that is stable under reversal.
	 */
	private static int comparePointsLexicographically(Point a, Point b)
	{
		int compareX = Double.compare(a.x, b.x);
		return compareX != 0 ? compareX : Double.compare(a.y, b.y);
	}

	/**
	 * Returns the reverse of {@code nodes} as a new river path. Per-segment attributes (width level, seed) are re-associated so the segment
	 * between two given locations keeps its width: each node carries the attributes for the segment leaving it, so on reversal a node's
	 * outgoing segment becomes the incoming one from the other end. The first node of the reversed path carries the width the last-but-one
	 * original node did.
	 */
	private static List<RiverPathNode> reverseRiverNodes(List<RiverPathNode> nodes)
	{
		int count = nodes.size();
		List<Point> locations = new ArrayList<>(count);
		List<Integer> widths = new ArrayList<>(Math.max(0, count - 1));
		for (int i = count - 1; i >= 0; i--)
		{
			locations.add(nodes.get(i).getLoc());
		}
		for (int i = count - 2; i >= 0; i--)
		{
			widths.add(nodes.get(i).getWidthLevelToNext());
		}
		return River.fromLocationsAndWidths(locations, widths).nodes;
	}

	/**
	 * A* search node for the global river router: a {@code (corner, nextWaypointIndex, previousCorner)} state keyed for the open set,
	 * ordered by f-score.
	 */
	private record WaypointSearchNode(long stateKey, double fScore)
	{
	}

	/**
	 * Threads a river through its ordered {@code waypoints} with a single global A* search over the new graph, instead of routing each
	 * consecutive pair independently.
	 * <p>
	 * The search state is {@code (corner, t)} where {@code t} is the index of the next waypoint still to reach. From a state the search may
	 * step to a graph-adjacent corner (cost = the edge's geometric length; water corners are skipped unless the corner is the next waypoint
	 * itself, so coastal-mouth endpoints stay reachable), advancing {@code t} when it lands on waypoint {@code t}; or it may take a
	 * <em>freehand jump</em> straight to waypoint {@code t} at cost {@link #freehandJumpPenalty}× the straight-line distance. Minimizing
	 * total cost lets the search make an earlier leg longer to keep a later one routable on the graph — the cross-leg trade-off that no
	 * per-leg search can make — and emits a jump only where the land is genuinely disconnected or any graph detour would cost more than the
	 * penalty. The heuristic is the straight-line distance to waypoint {@code t} plus the straight-line distances through the remaining
	 * waypoints, which is admissible because every graph step and every penalized jump costs at least its straight-line length.
	 * </p>
	 * Returns the river's RI-space nodes (per-segment width = the leg's source width, edge index = the routed Voronoi edge or
	 * {@link RiverPathNode#EDGE_INDEX_NONE} for a jump), or {@code null} if no route reaches the final waypoint.
	 */
	private static List<RiverPathNode> routeWaypointsGlobally(List<Corner> waypoints, List<Integer> waypointWidths, WorldGraph newGraph, double resolution)
	{
		int waypointCount = waypoints.size();
		// remainingStraight[t] = straight-line distance from waypoint t through all later waypoints to the last one.
		double[] remainingStraight = new double[waypointCount];
		for (int i = waypointCount - 2; i >= 0; i--)
		{
			remainingStraight[i] = remainingStraight[i + 1] + waypoints.get(i).loc.distanceTo(waypoints.get(i + 1).loc);
		}

		// State = (corner, t, previousCorner), encoded as one long key = cornerIndex*tBase*prevBase + t*prevBase + previousCorner,
		// with t in 0..waypointCount (waypointCount == reached the last waypoint = goal). The previousCorner component forbids
		// immediately reversing the edge just travelled, so the river flows THROUGH each interior waypoint instead of spurring out to
		// it and back (a spur revisits a corner, renders as a loop, and would be excised — dropping the waypoint).
		final long tBase = waypointCount + 1;
		final long prevBase = newGraph.corners.size() + 1;
		final int noPreviousCorner = newGraph.corners.size();

		Corner startCorner = waypoints.get(0);
		long startKey = startCorner.index * tBase * prevBase + 1 * prevBase + noPreviousCorner;

		Map<Long, Double> gScore = new HashMap<>();
		// cameFrom[stateKey] = { previousStateKey, edgeIndexOfIncomingSegment }.
		Map<Long, long[]> cameFrom = new HashMap<>();
		gScore.put(startKey, 0.0);

		PriorityQueue<WaypointSearchNode> open = new PriorityQueue<>((a, b) -> Double.compare(a.fScore(), b.fScore()));
		open.add(new WaypointSearchNode(startKey, heuristic(startCorner, 1, waypoints, remainingStraight)));

		long goalKey = -1;
		while (!open.isEmpty())
		{
			WaypointSearchNode current = open.poll();
			long stateKey = current.stateKey();
			int cornerIndex = (int) (stateKey / (tBase * prevBase));
			int t = (int) ((stateKey / prevBase) % tBase);
			int previousIndex = (int) (stateKey % prevBase);

			if (t == waypointCount)
			{
				goalKey = stateKey;
				break;
			}

			double gCurrent = gScore.get(stateKey);
			// Skip stale queue entries left over from a since-improved g-score.
			if (current.fScore() > gCurrent + heuristic(newGraph.corners.get(cornerIndex), t, waypoints, remainingStraight) + 1e-6)
			{
				continue;
			}

			Corner corner = newGraph.corners.get(cornerIndex);
			Corner target = waypoints.get(t);

			// Is there a usable neighbor other than the one we arrived from? If so we forbid reversing the last edge (so the river
			// flows through this corner rather than spurring). If not — this corner is a single-corridor dead-end (e.g. a waypoint
			// snapped to a near-coast corner whose other neighbors are water) — we allow the reversal: the resulting spur is later
			// excised by removeCornerRevisits, dropping the un-throughable waypoint cleanly rather than forcing a freehand jump.
			boolean hasForwardOption = false;
			for (Edge edge : corner.protrudes)
			{
				Corner neighbor = edgeOtherCorner(edge, corner);
				if (neighbor == null || neighbor.index == previousIndex)
				{
					continue;
				}
				if (neighbor.equals(target) || !(neighbor.isCoast || neighbor.isOcean || neighbor.isWater))
				{
					hasForwardOption = true;
					break;
				}
			}

			// Graph steps to adjacent land corners (the target waypoint is allowed even if it is water-adjacent).
			for (Edge edge : corner.protrudes)
			{
				Corner neighbor = edgeOtherCorner(edge, corner);
				if (neighbor == null || (neighbor.index == previousIndex && hasForwardOption))
				{
					continue; // null, or would immediately reverse the edge just travelled while a forward option exists (no spur).
				}
				boolean isTarget = neighbor.equals(target);
				if (!isTarget && (neighbor.isCoast || neighbor.isOcean || neighbor.isWater))
				{
					continue;
				}
				int nextT = isTarget ? t + 1 : t;
				double tentative = gCurrent + corner.loc.distanceTo(neighbor.loc);
				relaxWaypointState(neighbor, nextT, cornerIndex, tentative, stateKey, edge.index, tBase, prevBase, gScore, cameFrom, open, waypoints, remainingStraight);
			}

			// Freehand jump straight to the next waypoint (no Voronoi edge), penalized so it is a last resort.
			double jumpCost = gCurrent + corner.loc.distanceTo(target.loc) * freehandJumpPenalty;
			relaxWaypointState(target, t + 1, cornerIndex, jumpCost, stateKey, RiverPathNode.EDGE_INDEX_NONE, tBase, prevBase, gScore, cameFrom, open, waypoints, remainingStraight);
		}

		if (goalKey < 0)
		{
			return null;
		}

		// Reconstruct the state sequence from start to goal.
		List<Long> stateKeys = new ArrayList<>();
		for (long key = goalKey; key != startKey; key = cameFrom.get(key)[0])
		{
			stateKeys.add(key);
		}
		stateKeys.add(startKey);
		Collections.reverse(stateKeys);

		// Build the river nodes. The segment leaving state i carries that segment's edge index and the width of the leg it belongs to
		// (the leg toward waypoint t uses the width leaving waypoint t-1).
		List<RiverPathNode> nodes = new ArrayList<>(stateKeys.size());
		Random random = new Random();
		for (int i = 0; i < stateKeys.size(); i++)
		{
			long key = stateKeys.get(i);
			Corner corner = newGraph.corners.get((int) (key / (tBase * prevBase)));
			Point ri = corner.loc.mult(1.0 / resolution);
			if (i + 1 < stateKeys.size())
			{
				long nextKey = stateKeys.get(i + 1);
				int edgeIndex = (int) cameFrom.get(nextKey)[1];
				int sourceT = (int) ((key / prevBase) % tBase);
				int width = waypointWidths.get(sourceT - 1);
				nodes.add(new RiverPathNode(ri, width, random.nextLong(), edgeIndex));
			}
			else
			{
				nodes.add(new RiverPathNode(ri, 0, 0L, RiverPathNode.EDGE_INDEX_NONE));
			}
		}
		return removeCornerRevisits(nodes);
	}

	/**
	 * Excises any corner the route visits more than once, keeping the river a simple path. The global router minimizes total cost over the
	 * {@code (corner, waypointIndex)} state space, so it can legitimately pass the same corner twice (e.g. through a chokepoint while
	 * threading waypoints in order); drawn as one polyline that renders as a loop. When a corner reappears, the span between the two visits
	 * is dropped and the surviving earlier node inherits the second visit's outgoing segment — which leaves that node by the same Voronoi
	 * edge the second visit used, so the join stays on the graph (no freehand jump is introduced). Node identity is by location, which is
	 * derived deterministically from the corner.
	 */
	private static List<RiverPathNode> removeCornerRevisits(List<RiverPathNode> nodes)
	{
		Map<Point, Integer> firstPosition = new HashMap<>();
		List<RiverPathNode> simple = new ArrayList<>(nodes.size());
		for (RiverPathNode node : nodes)
		{
			Integer priorPosition = firstPosition.get(node.getLoc());
			if (priorPosition == null)
			{
				firstPosition.put(node.getLoc(), simple.size());
				simple.add(node);
				continue;
			}
			// Revisit: drop the loop (everything after the earlier occurrence) and give the surviving node this visit's outgoing segment.
			for (int idx = simple.size() - 1; idx > priorPosition; idx--)
			{
				firstPosition.remove(simple.remove(idx).getLoc());
			}
			RiverPathNode survivor = simple.get(priorPosition);
			simple.set(priorPosition, new RiverPathNode(survivor.getLoc(), node.getWidthLevelToNext(), node.getSeedToNext(), node.getEdgeIndexToNext()));
		}
		return simple;
	}

	/**
	 * Admissible heuristic for {@link #routeWaypointsGlobally}: straight-line distance from {@code corner} to the next waypoint plus the
	 * straight-line distances through all remaining waypoints. Returns 0 once the last waypoint has been reached.
	 */
	private static double heuristic(Corner corner, int t, List<Corner> waypoints, double[] remainingStraight)
	{
		if (t >= waypoints.size())
		{
			return 0;
		}
		return corner.loc.distanceTo(waypoints.get(t).loc) + remainingStraight[t];
	}

	/**
	 * Relaxes a candidate {@code (corner, nextT)} state in {@link #routeWaypointsGlobally}: if {@code tentativeG} improves on the best
	 * known cost to that state, records the new cost and back-pointer (with the incoming segment's edge index) and enqueues it.
	 */
	private static void relaxWaypointState(Corner corner, int nextT, int previousCornerIndex, double tentativeG, long fromStateKey, int edgeIndex, long tBase, long prevBase, Map<Long, Double> gScore,
			Map<Long, long[]> cameFrom, PriorityQueue<WaypointSearchNode> open, List<Corner> waypoints, double[] remainingStraight)
	{
		long newKey = corner.index * tBase * prevBase + nextT * prevBase + previousCornerIndex;
		Double existing = gScore.get(newKey);
		if (existing != null && tentativeG >= existing - 1e-6)
		{
			return;
		}
		gScore.put(newKey, tentativeG);
		cameFrom.put(newKey, new long[] { fromStateKey, edgeIndex });
		open.add(new WaypointSearchNode(newKey, tentativeG + heuristic(corner, nextT, waypoints, remainingStraight)));
	}


	/**
	 * Clips a river's RI-coordinate path to the selection rectangle, inserting intersection points at the boundary where segments cross it.
	 * Returns a list of {@link River} sub-paths (each with ≥ 2 points) in new-map RI coordinates, with width levels scaled by
	 * {@code riverLevelScale} and capped at {@link GraphRiver#MAX_RIVER_LEVEL}.
	 * <p>
	 * Edge indices on the original river's {@link RiverPathNode}s are intentionally <em>not</em> propagated to the result: the sub-map has
	 * its own independent {@link WorldGraph} so original edge indices would point at the wrong edges (or out of range).
	 * {@link River#fromLocationsAndWidths} creates nodes with {@link RiverPathNode#EDGE_INDEX_NONE}, which is the correct value here — the
	 * resulting sub-map river is a freehand polyline that {@link nortantis.RiverDrawer} renders directly.
	 * </p>
	 */
	private static List<River> clipRiverPath(River river, Rectangle selectionBounds, int newWidth, int newHeight, double riverLevelScale)
	{
		List<River> result = new ArrayList<>();
		List<RiverPathNode> nodes = river.nodes;
		if (nodes.isEmpty())
			return result;

		List<Point> currentPath = new ArrayList<>();
		List<Integer> currentWidths = new ArrayList<>();
		boolean prevInside = selectionBounds.contains(nodes.get(0).getLoc());
		if (prevInside)
			currentPath.add(transformRIPoint(nodes.get(0).getLoc(), selectionBounds, newWidth, newHeight));

		for (int i = 1; i < nodes.size(); i++)
		{
			Point prev = nodes.get(i - 1).getLoc();
			Point curr = nodes.get(i).getLoc();
			int rawWidth = nodes.get(i - 1).getWidthLevelToNext();
			// Snap the scaled width to the discrete set the editor's width slider can produce (this also caps at MAX_RIVER_LEVEL), so
			// sub-map river widths stay consistent with widths a user could draw or edit.
			int scaledWidth = GraphRiver.snapToAllowedRiverLevel((int) Math.round(rawWidth * riverLevelScale));
			boolean currInside = selectionBounds.contains(curr);

			if (prevInside && currInside)
			{
				currentPath.add(transformRIPoint(curr, selectionBounds, newWidth, newHeight));
				currentWidths.add(scaledWidth);
			}
			else if (prevInside && !currInside)
			{
				Point exit = segmentBoundaryIntersection(prev, curr, selectionBounds);
				if (exit != null)
				{
					currentPath.add(transformRIPoint(exit, selectionBounds, newWidth, newHeight));
					currentWidths.add(scaledWidth);
				}
				if (currentPath.size() >= 2)
					result.add(River.fromLocationsAndWidths(currentPath, currentWidths));
				currentPath = new ArrayList<>();
				currentWidths = new ArrayList<>();
			}
			else if (!prevInside && currInside)
			{
				Point entry = segmentBoundaryIntersection(prev, curr, selectionBounds);
				if (entry != null)
					currentPath.add(transformRIPoint(entry, selectionBounds, newWidth, newHeight));
				currentPath.add(transformRIPoint(curr, selectionBounds, newWidth, newHeight));
				currentWidths.add(scaledWidth);
			}
			else
			{
				// Both outside: the segment may still pass through the rectangle.
				Optional<Tuple2<Point, Point>> through = segmentThroughIntersections(prev, curr, selectionBounds);
				if (through.isPresent())
				{
					List<Point> subPath = new ArrayList<>();
					List<Integer> subWidths = new ArrayList<>();
					subPath.add(transformRIPoint(through.get().getFirst(), selectionBounds, newWidth, newHeight));
					subPath.add(transformRIPoint(through.get().getSecond(), selectionBounds, newWidth, newHeight));
					subWidths.add(scaledWidth);
					result.add(River.fromLocationsAndWidths(subPath, subWidths));
				}
			}
			prevInside = currInside;
		}

		if (currentPath.size() >= 2)
			result.add(River.fromLocationsAndWidths(currentPath, currentWidths));

		return result;
	}

	/**
	 * Returns true if the nearest original-graph corner to {@code riPoint} is adjacent to a water center (using originalEdits where
	 * present, falling back to the center's own flag). Used to determine intentional water proximity at river endpoints.
	 */
	private static boolean isRIPointAdjacentToWater(Point riPoint, WorldGraph originalGraph, MapEdits originalEdits, double originalResolution)
	{
		Point pixelPoint = new Point(riPoint.x * originalResolution, riPoint.y * originalResolution);
		Corner corner = originalGraph.findClosestCorner(pixelPoint);
		return corner != null && isSourceCornerAdjacentToWater(corner, originalEdits);
	}

	/**
	 * Returns true if {@code corner} has at least one neighboring corner that is pure land (not coast, ocean, or water). The river router
	 * only steps through such corners, so a corner with a land neighbor can be reached along graph edges from the land network, whereas one
	 * whose neighbors are all coast/water can only be reached by a freehand jump across the water.
	 */
	private static boolean hasLandNeighbor(Corner corner)
	{
		for (Edge e : corner.protrudes)
		{
			Corner neighbor = edgeOtherCorner(e, corner);
			if (neighbor != null && !(neighbor.isCoast || neighbor.isOcean || neighbor.isWater))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns the corner on the opposite end of {@code e} from {@code corner}, or {@code null} if {@code corner} is not an endpoint of
	 * {@code e}.
	 */
	private static Corner edgeOtherCorner(Edge e, Corner corner)
	{
		if (e.v0 != null && e.v0.equals(corner))
			return e.v1;
		if (e.v1 != null && e.v1.equals(corner))
			return e.v0;
		return null;
	}

	/**
	 * Returns true if any center adjacent to {@code sourceCorner} is water (using originalEdits where present, otherwise the center's own
	 * flag).
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
	 * Clips a road's RI-coordinate path to the selection rectangle, inserting intersection points at the boundary where segments cross it.
	 * Returns a list of sub-paths (each with >= 2 points) in new-map RI coordinates, ready to become Road objects.
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
				Optional<Tuple2<Point, Point>> throughPoints = segmentThroughIntersections(prev, curr, selectionBounds);
				if (throughPoints.isPresent())
				{
					List<Point> subPath = new ArrayList<>();
					subPath.add(transformRIPoint(throughPoints.get().getFirst(), selectionBounds, newWidth, newHeight));
					subPath.add(transformRIPoint(throughPoints.get().getSecond(), selectionBounds, newWidth, newHeight));
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
	 * When both endpoints of segment P1→P2 are outside {@code rect}, finds the two boundary intersection points (ordered from P1 to P2) if
	 * the segment passes through the rectangle. Returns empty if there are fewer than two distinct intersections.
	 */
	private static Optional<Tuple2<Point, Point>> segmentThroughIntersections(Point p1, Point p2, Rectangle rect)
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
			return Optional.of(new Tuple2<>(new Point(first[1], first[2]), new Point(last[1], last[2])));
		}
		return Optional.empty();
	}

	/**
	 * Returns the intersection point of segment P1→P2 with the boundary of {@code rect}. P1 and P2 should be on opposite sides (one inside,
	 * one outside). Returns null if no valid intersection is found.
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
