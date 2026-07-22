package nortantis;

import nortantis.MapSettings.GridOverlayLayer;
import nortantis.editor.*;
import nortantis.geom.*;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.graph.voronoi.Edge;
import nortantis.graph.voronoi.EdgeDrawType;
import nortantis.platform.*;
import nortantis.swing.MapEdits;
import nortantis.swing.translation.Translation;
import nortantis.util.*;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.function.BiFunction;
import java.util.function.DoubleConsumer;
import java.util.stream.Collectors;

public class MapCreator implements WarningLogger
{
	private final double regionBlurColorScale = 0.55;
	/**
	 * Controls how dark coastlines can get, for both the land and water. Higher values are lighter.
	 */
	private static final float coastlineShadingScale = 5.27f;

	private Random r;

	private static final double concentricWaveWidthBetweenWaves = 11;
	private static final double concentricWaveLineWidth = 1.8;
	private boolean isCanceled;

	/**
	 * Optional callback invoked during a full draw ({@link #createMap}) to report progress as a fraction from 0 to 1. Set via
	 * {@link #setProgressListener}. Null (the default) means no progress reporting. Only full draws report progress; incremental draws do
	 * not, because their work is not linear enough to estimate a meaningful fraction. It is called on the draw thread at each point where the
	 * draw also checks for cancellation.
	 */
	private DoubleConsumer progressListener;

	/**
	 * The number of progress-reporting points reached so far during the current full draw. Reset at the start of {@link #createMap}.
	 */
	private int progressStepsCompleted;

	/**
	 * The approximate total number of progress-reporting points in a full draw, used as the denominator when reporting progress. This is an
	 * estimate: some points are skipped when parts of the map are cached, so the reported fraction is clamped to at most 1.
	 */
	private static final int fullDrawProgressStepCount = 21;

	private final List<String> warningMessages;
	/**
	 * City icons dropped from this draw because they landed on water (see {@link IconDrawer#getCitiesRemovedForTouchingWater()}). Captured
	 * from the icon drawer during the draw so callers (the sub-map preview) can report which shore-side cities disappeared without reaching
	 * into the draw internals. Empty unless cities were lost.
	 */
	private List<IconDrawer.CityIconRemovedForWater> citiesRemovedForTouchingWater = new ArrayList<>();
	public ConcurrentHashMap<Integer, Center> centersToRedrawLowPriority;
	private Boolean memoryModeOverride;
	/**
	 * Optional lock held only while an incremental update writes its finished snippet into the shared full-sized display map (see
	 * {@link #incrementalUpdateBounds}). The editor sets this so a background display rescale, which reads the same buffer, doesn't
	 * observe a half-written region. It is intentionally NOT held during the (much longer) snippet computation, so that computation can
	 * run in parallel with an in-flight rescale. Null (the default) means no locking - used for full draws, tests, and other contexts
	 * where nothing reads the buffer concurrently.
	 */
	private Lock incrementalMapWriteLock;

	/**
	 * See {@link #incrementalMapWriteLock}. Set before an incremental update when another thread may read the full-sized map buffer
	 * concurrently; leave unset (null) otherwise.
	 */
	public void setIncrementalMapWriteLock(Lock lock)
	{
		this.incrementalMapWriteLock = lock;
	}

	/**
	 * Override the memory mode for testing. Pass null to clear the override and resume normal behavior.
	 */
	public void overrideMemoryMode(Boolean isLowMemory)
	{
		memoryModeOverride = isLowMemory;
	}

	public MapCreator()
	{
		warningMessages = new ArrayList<>();
		centersToRedrawLowPriority = new ConcurrentHashMap<>();
	}

	public IntRectangle incrementalUpdateText(final MapSettings settings, MapParts mapParts, Image fullSizeMap, List<MapText> textChanged)
	{
		migrateLegacyRiversIfNeeded(settings, mapParts.graph);
		TextDrawer textDrawer = new TextDrawer(settings);
		textDrawer.setMapTexts(settings.edits.text);

		List<Rectangle> changeBounds = new ArrayList<>();
		for (MapText text : textChanged)
		{
			Rectangle change = textDrawer.getTextBoundingBoxFor1Or2LineSplit(text);
			if (change == null)
			{
				continue;
			}
			changeBounds.add(change);
		}

		return incrementalUpdateMultipleBounds(settings, mapParts, fullSizeMap, changeBounds, true);
	}

	public IntRectangle incrementalUpdateIcons(final MapSettings settings, MapParts mapParts, Image fullSizeMap, List<FreeIcon> iconsChanged)
	{
		migrateLegacyRiversIfNeeded(settings, mapParts.graph);
		Rectangle changeBounds = null;
		for (FreeIcon icon : iconsChanged)
		{
			FreeIcon updated = mapParts.iconDrawer.adjustForMissingAssetsIfNeeded(icon, new LoggerWarningLogger());
			if (updated == null)
			{
				continue;
			}
			IconDrawTask task = mapParts.iconDrawer.toIconDrawTask(updated);
			if (task == null)
			{
				continue;
			}
			Rectangle change = task.createBounds();
			if (change == null)
			{
				continue;
			}
			if (changeBounds == null)
			{
				changeBounds = change;
			}
			else
			{
				changeBounds = changeBounds.add(change);
			}
		}

		if (changeBounds == null)
		{
			return null;
		}

		return incrementalUpdateMultipleBounds(settings, mapParts, fullSizeMap, Arrays.asList(changeBounds), false);
	}

	private IntRectangle incrementalUpdateMultipleBounds(final MapSettings settings, MapParts mapParts, Image fullSizeMap, List<Rectangle> changeBounds, boolean onlyTextChanged)
	{
		TextDrawer textDrawer = new TextDrawer(settings);
		textDrawer.setMapTexts(settings.edits.text);
		double effectsPadding = calcEffectsPadding(settings);
		mapParts.iconDrawer = new IconDrawer(mapParts.graph, new Random(), settings);

		final int paddingToAccountForIntegerTruncation = 4;
		IntRectangle bounds = null;

		// Compute the union of all change bounds for the hint
		HashSet<Rectangle> noDuplicates = new HashSet<>(changeBounds);

		for (Rectangle change : noDuplicates)
		{
			if (change == null)
			{
				continue;
			}

			Rectangle padded = change.pad(paddingToAccountForIntegerTruncation, paddingToAccountForIntegerTruncation);
			IntRectangle updateBounds;
			mapParts.iconDrawer.addOrUpdateIconsFromEdits(settings.edits, Collections.emptySet(), padded, this);
			updateBounds = incrementalUpdateBounds(settings, mapParts, fullSizeMap, padded, effectsPadding, textDrawer, onlyTextChanged);
			if (bounds == null)
			{
				bounds = updateBounds;
			}
			else if (updateBounds != null)
			{
				bounds = bounds.add(updateBounds);
			}
		}

		if (bounds == null)
		{
			return null;
		}
		return bounds.pad(paddingToAccountForIntegerTruncation, paddingToAccountForIntegerTruncation);
	}

	/**
	 * Updates a piece of a map, given a list of centers that changed. Also updates things in mapParts.
	 *
	 * @param settings
	 *            Map settings for drawing
	 * @param mapParts
	 *            Assumed to be populated by createMap the last time the map was generated at full size
	 * @param fullSizedMap
	 *            The full sized map to update
	 * @param centersChangedIds
	 *            Ids of the edits for centers that need to be re-drawn
	 * @param edgesChangedIds
	 *            If edges changed, this is the list of ids for edge edits that changed
	 * @param isLowPriorityChange
	 *            Tells whether this update was submitted as a low priority change. In theory the drawing code doesn't need to know this
	 *            because low priority changes should never change something that then requires submitting more low priority changes, but
	 *            since my code for detecting when coastlines need to be smoothed is imperfect, I added this flag.
	 */
	public IntRectangle incrementalUpdateForCentersAndEdges(final MapSettings settings, MapParts mapParts, Image fullSizedMap, Set<Integer> centersChangedIds, Set<Integer> edgesChangedIds,
			boolean isLowPriorityChange)
	{
		Set<Center> centersChanged;
		if (centersChangedIds != null)
		{
			centersChanged = new HashSet<>(centersChangedIds.stream().map(id -> mapParts.graph.centers.get(id)).collect(Collectors.toSet()));
		}
		else
		{
			centersChanged = new HashSet<>();
		}

		if (edgesChangedIds != null)
		{
			centersChanged.addAll(mapParts.graph.getCentersFromEdgeIds(edgesChangedIds));
		}

		applyRegionEdits(mapParts.graph, settings.edits);
		// Apply river edits before center edits because applying center edits smoothes region boundaries, which depends on rivers.
		migrateLegacyRiversIfNeeded(settings, mapParts.graph);
		applyRiverEdits(mapParts.graph, settings.edits);
		Set<Center> centersChangedThatAffectedLandOrRegionBoundaries = applyCenterEdits(mapParts.graph, settings.edits, getCenterEditsForCenters(settings.edits, centersChanged),
				settings.areRegionBoundariesVisible(), settings.resolution);

		Rectangle centersChangedBounds = WorldGraph.getBoundingBox(centersChanged);

		if (centersChangedBounds == null)
		{
			// Nothing changed
			return null;
		}

		// Re-stamp river curves onto region-boundary edges now that rivers are resynced and noisy edges were rebuilt for changed centers,
		// before the region-color fill and boundary line are redrawn below, so those polygons conform to the current rivers.
		Stopwatch sw = new Stopwatch("stamps rivers");
		new RiverDrawer(settings, mapParts.graph).stampRiverCurvesOntoGraphEdges();
		sw.printElapsedTime();

		if (!centersChangedThatAffectedLandOrRegionBoundaries.isEmpty())
		{
			// Expand the centers that changed to include those that had noisy edges recalculated when applying center edits. This is
			// necessary because WorldGraph.smoothCoastlinesAndRegionBoundariesIfNeeded expands the set of centers that changed to check for
			// single polygon islands or single polygon water, and updates those noisy edges.
			centersChangedBounds = centersChangedBounds.add(WorldGraph.getBoundingBox(centersChangedThatAffectedLandOrRegionBoundaries));
		}

		double effectsPadding = calcEffectsPadding(settings);
		// The bounds to replace in the original map.
		Rectangle replaceBounds = centersChangedBounds.pad(effectsPadding, effectsPadding);

		// Refresh the center lookup (grid slice polygons and, in pixel mode, the lookup table) for every center whose noisy edges were
		// rebuilt - not just the directly-edited centers. Coastline/region-boundary smoothing can move corners on centers beyond the edited
		// set, so their noisy edges (and therefore their slice polygons) changed too; if we only refreshed the edited centers + their
		// immediate neighbors, those farther centers would keep stale geometry and findClosestCenter would return the wrong center near the
		// edited coastline.
		Set<Center> centersToRefreshLookup = new HashSet<>(centersChanged);
		centersToRefreshLookup.addAll(centersChangedThatAffectedLandOrRegionBoundaries);
		mapParts.graph.updateCenterLookupTable(centersToRefreshLookup);

		TextDrawer textDrawer = new TextDrawer(settings);
		textDrawer.setMapTexts(settings.edits.text);

		if (!centersChangedThatAffectedLandOrRegionBoundaries.isEmpty())
		{
			// Only submit low priority changes if this change is itself not one.
			if (!isLowPriorityChange)
			{
				if (settings.drawRegionBoundaries && settings.regionBoundaryStyle.type != StrokeType.Solid)
				{
					// When using non-solid region boundaries, expand the replace bounds to include region borders inside the replace bounds
					// so
					// that the dashed pattern is correct.
					List<List<Edge>> regionBoundaries = mapParts.graph.findEdgesByDrawType(centersChanged, EdgeDrawType.Region, false);

					Set<Center> regionBoundaryCenters = new HashSet<>();
					for (List<Edge> boundary : regionBoundaries)
					{
						regionBoundaryCenters.addAll(mapParts.graph.getCentersFromEdges(boundary));
					}
					if (!regionBoundaryCenters.isEmpty())
					{
						addLowPriorityCentersToRedraw(regionBoundaryCenters);
					}
				}

				// Concentric waves with random breaks need the entire coastline redrawn because a change somewhere in the
				// coastline can affect the random numbers used to draw the rest of it.
				if (settings.hasConcentricWaves() && settings.brokenLinesForConcentricWaves)
				{
					List<List<Edge>> coastlines;
					coastlines = mapParts.graph.findShoreEdges(centersChanged, settings.drawOceanEffectsInLakes, false);

					Set<Center> coastlineCenters = new HashSet<>();
					for (List<Edge> boundary : coastlines)
					{
						coastlineCenters.addAll(mapParts.graph.getCentersFromEdges(boundary));
					}
					if (!coastlineCenters.isEmpty())
					{
						addLowPriorityCentersToRedraw(coastlineCenters);
					}
				}
			}

			// Expand the replace bounds to include Auto-line-break text near the change whose line count the change would actually flip
			// between one line and two (only that requires redrawing the text outside the change region). See expandBoundsToIncludeText.
			if (settings.drawText)
			{
				Rectangle textChangeBounds = textDrawer.expandBoundsToIncludeText(settings.edits.text, centersChangedBounds, mapParts.graph, settings);
				replaceBounds = replaceBounds.add(textChangeBounds);
			}
		}

		mapParts.iconDrawer = new IconDrawer(mapParts.graph, new Random(), settings);
		Rectangle iconChangeBounds = mapParts.iconDrawer.addOrUpdateIconsFromEdits(settings.edits, centersChanged, replaceBounds, this);
		replaceBounds = Rectangle.add(replaceBounds, iconChangeBounds);

		replaceBounds = replaceBounds.floor();

		return incrementalUpdateBounds(settings, mapParts, fullSizedMap, replaceBounds, effectsPadding, textDrawer, false);
	}

	private void addLowPriorityCentersToRedraw(Collection<Center> toAdd)
	{
		for (Center c : toAdd)
		{
			centersToRedrawLowPriority.put(c.index, c);
		}
	}

	private IntRectangle incrementalUpdateBounds(final MapSettings settings, MapParts mapParts, Image fullSizedMap, Rectangle replaceBounds, double effectsPadding, TextDrawer textDrawer,
			boolean onlyTextChanged)
	{
		// The bounds of the snippet to draw. This is larger than the snippet to
		// replace because ocean/land effects expand beyond the edges
		// that draw them, and we need those to be included in the snippet to
		// replace.
		Rectangle drawBounds = replaceBounds.pad(effectsPadding, effectsPadding).floor();

		IntRectangle boundsInSourceToCopyFrom = new IntRectangle((int) replaceBounds.x - (int) drawBounds.x, (int) replaceBounds.y - (int) drawBounds.y, (int) replaceBounds.width,
				(int) replaceBounds.height);
		Image mapSnippet;
		Image textBackground;
		double sizeMultiplierRounded = calcSizeMultiplierFromResolutionScaleRounded(settings.resolution);

		Set<Center> centersToDraw = null;
		if (!onlyTextChanged || mapParts.mapBeforeAddingText == null)
		{
			Center searchStart = mapParts.graph.findClosestCenter(drawBounds.getCenter());
			centersToDraw = mapParts.graph.breadthFirstSearch(c -> c.isInBoundsIncludingNoisyEdges(drawBounds), searchStart);

			checkForCancel();

			List<IconDrawTask> iconsToDraw = mapParts.iconDrawer.getTasksInDrawBoundsSortedAndScaled(drawBounds);
			mapParts.background.doSetupThatNeedsGraphAndIcons(mapParts.graph, iconsToDraw, centersToDraw, drawBounds, replaceBounds);

			checkForCancel();

			// Draw mask for land vs ocean.
			Image landMask = Image.create((int) drawBounds.width, (int) drawBounds.height, ImageType.Binary);
			try (Painter p = landMask.createPainter())
			{
				mapParts.graph.drawLandAndOceanBlackAndWhite(p, centersToDraw, drawBounds);
			}

			checkForCancel();

			Image landTextureSnippet;
			if (settings.landColor.hasTransparency())
			{
				landTextureSnippet = ImageHelper.getInstance().copySnippetPreservingAlphaOfTransparentPixels(mapParts.background.land, drawBounds.toIntRectangle());
			}
			else
			{
				landTextureSnippet = mapParts.background.land.copySubImage(drawBounds.toIntRectangle());
			}

			checkForCancel();

			Image coastShading;
			Image landColoredBeforeAddingIconColors = null;
			Image landBackground = null;
			{
				Tuple2<Image, Image> tuple = darkenLandNearCoastlinesAndRegionBorders(settings, mapParts.graph, settings.resolution, landTextureSnippet, mapParts.background, null, centersToDraw,
						drawBounds, false);
				Image landBackgroundWithLandInOcean = tuple.getFirst();
				coastShading = tuple.getSecond();
				mapSnippet = ImageHelper.getInstance().maskWithColor(landBackgroundWithLandInOcean, Color.black, landMask, false);

				if (settings.drawRegionColors)
				{
					landColoredBeforeAddingIconColors = mapParts.background.landColoredBeforeAddingIconColors.copySubImage(drawBounds.toIntRectangle());
					landBackground = darkenLandNearCoastlinesAndRegionBorders(settings, mapParts.graph, settings.resolution, landColoredBeforeAddingIconColors, mapParts.background, coastShading,
							centersToDraw, drawBounds, false).getFirst();
				}
				else
				{
					landBackground = landBackgroundWithLandInOcean;
				}
			}

			checkForCancel();

			if (settings.drawRegionBoundaries)
			{
				try (Painter p = mapSnippet.createPainter(DrawQuality.High))
				{
					p.setColor(settings.regionBoundaryColor);
					mapParts.graph.drawRegionBoundaries(p, settings.regionBoundaryStyle, centersToDraw, drawBounds);
				}
			}

			checkForCancel();

			new RiverDrawer(settings, mapParts.graph).drawRivers(mapSnippet, drawBounds);

			checkForCancel();

			// Draw ocean
			Image oceanTextureSnippet;
			{
				oceanTextureSnippet = mapParts.background.createOceanSnippet(drawBounds);
				mapSnippet = ImageHelper.getInstance().maskWithImage(mapSnippet, oceanTextureSnippet, landMask);
			}

			checkForCancel();

			// Add shading and waves to ocean along coastlines
			Image oceanWaves;
			Image oceanShading;
			Image oceanWithWavesAndShading = oceanTextureSnippet;
			{
				Tuple2<Image, Image> oceanTuple = createOceanWavesAndShading(settings, mapParts.graph, settings.resolution, landMask, centersToDraw, drawBounds);
				oceanWaves = oceanTuple.getFirst();
				oceanShading = oceanTuple.getSecond();
				if (oceanShading != null)
				{
					mapSnippet = ImageHelper.getInstance().maskWithColor(mapSnippet, settings.oceanShadingColor, oceanShading, true);
					oceanWithWavesAndShading = ImageHelper.getInstance().maskWithColor(oceanWithWavesAndShading, settings.oceanShadingColor, oceanShading, true);
				}
				if (oceanWaves != null)
				{
					mapSnippet = ImageHelper.getInstance().maskWithColor(mapSnippet, settings.oceanWavesColor, oceanWaves, true);
					oceanWithWavesAndShading = ImageHelper.getInstance().maskWithColor(oceanWithWavesAndShading, settings.oceanWavesColor, oceanWaves, true);
				}
			}

			checkForCancel();

			// Draw coastlines.
			{
				try (Painter p = mapSnippet.createPainter(DrawQuality.High))
				{
					p.setColor(settings.coastlineColor);
					mapParts.graph.drawCoastlineWithLakeShores(p, settings.coastlineWidth * settings.resolution, centersToDraw, drawBounds);
				}
			}

			checkForCancel();

			// Draw roads
			if (settings.drawRoads)
			{
				RoadDrawer roadDrawer = new RoadDrawer(r, settings, mapParts.graph);
				roadDrawer.drawRoads(mapSnippet, drawBounds);
			}

			checkForCancel();

			if (settings.drawGridOverlay && settings.gridOverlayLayer == GridOverlayLayer.Under_icons)
			{
				GridDrawer.drawGrid(mapSnippet, settings, drawBounds, mapParts.background.mapBounds.toIntDimension(), mapParts.graph, centersToDraw);
			}

			checkForCancel();

			// Draw icons
			mapParts.iconDrawer.drawIcons(iconsToDraw, mapSnippet, landBackground, landTextureSnippet, oceanWithWavesAndShading, drawBounds);

			checkForCancel();

			if (settings.drawGridOverlay && settings.gridOverlayLayer == GridOverlayLayer.Over_icons)
			{
				GridDrawer.drawGrid(mapSnippet, settings, drawBounds, mapParts.background.mapBounds.toIntDimension(), mapParts.graph, centersToDraw);
			}

			checkForCancel();

			textBackground = updateLandMaskAndCreateTextBackground(settings, mapParts.graph, landMask, iconsToDraw, settings.drawRegionColors ? landColoredBeforeAddingIconColors : landTextureSnippet,
					oceanTextureSnippet, mapParts.background, oceanWaves, oceanShading, coastShading, mapParts.iconDrawer, centersToDraw, drawBounds);

			checkForCancel();

			// Update the snippet in mapParts.textBackground because the Fonts tab uses that as part of speeding up text re-drawing.
			ImageHelper.getInstance().copySnippetFromSourceAndPasteIntoTarget(mapParts.textBackground, textBackground, replaceBounds.upperLeftCorner().toIntPoint(), boundsInSourceToCopyFrom, 0);

			// If present, also update the cached version of the map before adding text so that the Fonts tab can draw the map faster.
			if (mapParts.mapBeforeAddingText != null)
			{
				ImageHelper.getInstance().copySnippetFromSourceAndPasteIntoTarget(mapParts.mapBeforeAddingText, mapSnippet, replaceBounds.upperLeftCorner().toIntPoint(), boundsInSourceToCopyFrom, 0);
			}
		}
		else
		{
			mapSnippet = mapParts.mapBeforeAddingText.copySubImage(drawBounds.toIntRectangle());
			textBackground = mapParts.textBackground.copySubImage(drawBounds.toIntRectangle());
		}

		if (settings.drawText)
		{
			textDrawer.drawTextFromEdits(mapSnippet, textBackground, mapParts.graph, drawBounds);
		}
		textDrawer.updateTextBoundsIfNeeded(mapParts.graph);

		IntPoint drawBoundsUpperLeftCornerAdjustedForBorder = new IntPoint(drawBounds.upperLeftCorner().toIntPoint().x + mapParts.background.getBorderPaddingScaledByResolution(),
				drawBounds.upperLeftCorner().toIntPoint().y + mapParts.background.getBorderPaddingScaledByResolution());

		mapParts.background.drawEdgesIfBoundsTouchesThem(mapSnippet, drawBounds);
		mapParts.background.drawInsetCornersIfBoundsTouchesThem(mapSnippet, drawBounds);

		// Add grunge
		if (settings.drawGrunge && settings.grungeWidth > 0)
		{
			mapSnippet = ImageHelper.getInstance().maskWithColorInRegion(mapSnippet, settings.frayedBorderColor, mapParts.grunge, true, drawBoundsUpperLeftCornerAdjustedForBorder);
		}

		if (DebugFlags.drawCorners())
		{
			try (Painter p = mapSnippet.createPainter())
			{
				mapParts.graph.drawCorners(p, centersToDraw, drawBounds);
			}
		}
		if (DebugFlags.drawVoronoi())
		{
			try (Painter p = mapSnippet.createPainter())
			{
				p.setColor(Color.white);
				mapParts.graph.drawVoronoi(p, centersToDraw, drawBounds, false);
			}
		}

		IntPoint replaceBoundsUpperLeftCornerAdjustedForBorder = new IntPoint(replaceBounds.upperLeftCorner().toIntPoint().x + mapParts.background.getBorderPaddingScaledByResolution(),
				replaceBounds.upperLeftCorner().toIntPoint().y + mapParts.background.getBorderPaddingScaledByResolution());

		if (settings.drawOverlayImage)
		{
			drawOverlayImage(mapSnippet, settings, drawBounds, fullSizedMap.size());
		}

		// Add frayed border
		if (settings.frayedBorder)
		{
			int blurLevel = (int) (settings.frayedBorderBlurLevel * sizeMultiplierRounded);
			if (blurLevel > 0)
			{
				mapSnippet = ImageHelper.getInstance().maskWithColorInRegion(mapSnippet, settings.frayedBorderColor, mapParts.frayedBorderBlur, true, drawBoundsUpperLeftCornerAdjustedForBorder);
			}
			mapSnippet = ImageHelper.getInstance().setAlphaFromMaskInRegion(mapSnippet, mapParts.frayedBorderMask, true, drawBoundsUpperLeftCornerAdjustedForBorder);
		}

		// Write the finished snippet into the shared full-sized map. This is the only place this method touches fullSizedMap's pixels,
		// so it's the only part that must be guarded against a concurrent reader (e.g. a background display rescale). Everything above
		// built a self-contained snippet and can run in parallel with such a reader; only this brief blit needs the lock.
		if (incrementalMapWriteLock != null)
		{
			incrementalMapWriteLock.lock();
		}
		try
		{
			// Update the snippet in the main map.
			ImageHelper.getInstance().copySnippetFromSourceAndPasteIntoTarget(fullSizedMap, mapSnippet, replaceBoundsUpperLeftCornerAdjustedForBorder, boundsInSourceToCopyFrom,
					mapParts.background.getBorderPaddingScaledByResolution());

			if (DebugFlags.showIncrementalUpdateBounds())
			{
				try (Painter p = fullSizedMap.createPainter())
				{
					int scaledBorderWidth = settings.drawBorder && settings.borderPosition == BorderPosition.Outside_map ? (int) (settings.borderWidth * settings.resolution) : 0;
					p.setBasicStroke(4f);
					p.setColor(Color.red);
					{
						IntRectangle rect = new Rectangle(replaceBounds.x + scaledBorderWidth, replaceBounds.y + scaledBorderWidth, replaceBounds.width, replaceBounds.height).toIntRectangle();
						p.drawRect(rect.x, rect.y, rect.width, rect.height);
					}
					p.setBasicStroke(4f);
					p.setColor(Color.white);
					{
						IntRectangle rect = new Rectangle(drawBounds.x + scaledBorderWidth, drawBounds.y + scaledBorderWidth, drawBounds.width, drawBounds.height).toIntRectangle();
						p.drawRect(rect.x, rect.y, rect.width, rect.height);
					}
				}
			}
		}
		finally
		{
			if (incrementalMapWriteLock != null)
			{
				incrementalMapWriteLock.unlock();
			}
		}

		int scaledBorderWidth = settings.drawBorder && settings.borderPosition == BorderPosition.Outside_map ? (int) (settings.borderWidth * settings.resolution) : 0;
		IntRectangle bounds = replaceBounds.toIntRectangle();
		return new IntRectangle(bounds.x + scaledBorderWidth, bounds.y + scaledBorderWidth, bounds.width, bounds.height);
	}

	private double calcEffectsPadding(final MapSettings settings)
	{
		double sizeMultiplier = calcSizeMultiplierFromResolutionScaleRounded(settings.resolution);

		// To handle edge/effects changes outside centersChangedBounds box
		// caused by centers in centersChanged, pad the bounds of the
		// snippet to replace to include the width of ocean effects, land
		// effects, and with widest possible line that can be drawn,
		// whichever is largest.

		double concentricWaveWidth = settings.hasConcentricWaves()
				? settings.concentricWaveCount * (concentricWaveLineWidth * sizeMultiplier + concentricWaveWidthBetweenWaves * sizeMultiplier)
						+ (settings.jitterToConcentricWaves ? calcJitterVarianceRange(settings.resolution) : 0)
				: 0;
		// In theory, I shouldn't multiply by 0.75 below, but realistically there doesn't seem to be any visual difference and it helps a
		// lot
		// with performance.
		double rippleWaveWidth = settings.hasRippleWaves(settings.resolution) ? (settings.oceanWavesLevel * sizeMultiplier) * 0.75 : 0;
		// The shading from Gaussian blur isn't visible all the way out, so save performance by reducing the width
		// contributed by it.
		double oceanShadingWidth = 0.9 * (settings.oceanShadingLevel * sizeMultiplier);
		double coastShadingWidth = 0.9 * (settings.coastShadingLevel * sizeMultiplier);

		double effectsPadding = Math.ceil(Math.max(concentricWaveWidth, Math.max(rippleWaveWidth, Math.max(oceanShadingWidth, coastShadingWidth))));

		// Make sure effectsPadding is at least half the width of the maximum with any line can be drawn, which would probably be a very
		// wide river. Since there is no easy way to know what that will be, just guess.
		double buffer = 10;
		effectsPadding = Math.max(effectsPadding, Math.max((buffer / 2.0) * settings.resolution, (SettingsGenerator.maxLineWidthInEditor / 2.0) * settings.resolution));

		return effectsPadding;
	}

	/**
	 * Draws a map.
	 *
	 * @param settings
	 *            Setting for the map to create
	 * @param maxDimensions
	 *            The maximum width and height (in pixels) at which to draw the map. This is needed for creating previews. null means draw
	 *            at normal resolution. Warning: If maxDimensions is specified, then settings.resolution will be modified to fit that size.
	 * @param mapParts
	 *            If not null, then parts of the map created while generating will be stored in it.
	 * @return The map
	 */
	public Image createMap(final MapSettings settings, Dimension maxDimensions, MapParts mapParts) throws CancelledException
	{
		Logger.println("Creating the map");

		double startTime = System.currentTimeMillis();

		progressStepsCompleted = 0;

		// If we're within resolutionBuffer of our estimated maximum resolution, then be conservative about memory usage.
		// My tests showed that running frayed edge and grunge calculation inline with other stuff gave a 22% speedup.
		final double resolutionBuffer = 0.5;
		boolean isLowMemoryMode = memoryModeOverride != null ? memoryModeOverride : settings.resolution >= calcMaxResolutionScale() - resolutionBuffer;
		Logger.println("Using " + (isLowMemoryMode ? "low" : "high") + " memory mode.");

		if (StringUtils.isNotEmpty(settings.customImagesPath))
		{
			String pathWithHomeReplaced = FileHelper.replaceHomeFolderPlaceholder(settings.customImagesPath);
			if (!new File(pathWithHomeReplaced).exists())
			{
				throw new RuntimeException("The custom images folder '" + pathWithHomeReplaced + "' does not exist.");
			}
			Logger.println("Using custom images folder: " + settings.customImagesPath);
		}

		if ((double) settings.generatedWidth / settings.generatedHeight > GeneratedDimension.MAX_ASPECT_RATIO
				|| (double) settings.generatedHeight / settings.generatedWidth > GeneratedDimension.MAX_ASPECT_RATIO)
		{
			throw new RuntimeException(Translation.get("mapCreator.aspectRatioTooExtreme", settings.generatedWidth, settings.generatedHeight, GeneratedDimension.MAX_ASPECT_RATIO));
		}

		r = new Random(settings.randomSeed);
		Dimension mapBounds = Background.calcMapBoundsAndAdjustResolutionIfNeeded(settings, maxDimensions);
		double sizeMultiplier = calcSizeMultiplierFromResolutionScale(settings.resolution);
		// Kick of a job to create the graph while the background is being created.
		Future<WorldGraph> graphTask = ThreadHelper.getInstance().submit(() ->
		{
			if (mapParts == null || mapParts.graph == null)
			{
				Logger.println("Creating the graph.");
				WorldGraph graphCreated = createGraph(settings, mapBounds.width, mapBounds.height, r, settings.resolution, !settings.edits.isInitialized());

				if (mapParts != null)
				{
					mapParts.graph = graphCreated;
				}
				return graphCreated;
			}
			else
			{
				return mapParts.graph;
			}
		});

		Background background;
		if (mapParts != null && mapParts.background != null)
		{
			background = mapParts.background;
		}
		else
		{
			Logger.println("Generating the background image.");
			background = new Background(settings, mapBounds, this);
		}

		if (mapParts != null)
		{
			mapParts.background = background;
		}

		reportProgressAndCheckForCancel();

		WorldGraph graph;
		graph = ThreadHelper.getInstance().getResult(graphTask);

		Image map;
		reportProgressAndCheckForCancel();

		// Kick off frayed border creation. This is started after the graph is created because of previous bugs I've found
		// where VoronoiGraph was not thread safe. I think I've fixed those, but I'm still avoiding creating graphs in
		// parallel to be safe.
		Dimension mapDimensions = background.borderBounds;
		Future<Tuple2<Image, Image>> frayedBorderTask = null;
		if (!isLowMemoryMode)
		{
			frayedBorderTask = startFrayedBorderCreation(settings, mapDimensions, sizeMultiplier, mapParts);
		}

		// Create the NameCreator regardless of whether we're going to use it here because the text tools needs it to be in mapParts.
		Future<NameCreator> nameCreatorTask = null;
		if (mapParts == null || mapParts.nameCreator == null)
		{
			nameCreatorTask = startNameCreatorCreation(settings);
		}

		Image textBackground;
		List<Set<Center>> mountainGroups;
		List<IconDrawTask> cities;
		if (mapParts == null || mapParts.mapBeforeAddingText == null || !settings.edits.isInitialized())
		{
			Tuple4<Image, Image, List<Set<Center>>, List<IconDrawTask>> tuple = drawTerrainAndIcons(settings, mapParts, graph, background, isLowMemoryMode);

			reportProgressAndCheckForCancel();

			map = tuple.getFirst();
			textBackground = tuple.getSecond();
			mountainGroups = tuple.getThird();
			cities = tuple.getFourth();
		}
		else
		{
			map = mapParts.mapBeforeAddingText.deepCopy();
			textBackground = mapParts.textBackground;
			mountainGroups = null;
			cities = null;
		}
		if (mapParts == null)
		{
			if (background.landColoredBeforeAddingIconColors != null)
			{
				background.landColoredBeforeAddingIconColors.close();
			}
			background.landColoredBeforeAddingIconColors = null;
		}

		reportProgressAndCheckForCancel();

		Future<Image> grungeTask = null;
		if (!isLowMemoryMode)
		{
			// Run the job now so it can run in parallel with other stuff.
			grungeTask = startGrungeCreation(settings, mapParts, mapDimensions);
		}

		if (settings.drawText)
		{
			Logger.println("Adding text.");
		}
		else
		{
			Logger.println("Creating text but not drawing it.");
		}

		TextDrawer textDrawer = new TextDrawer(settings);

		textDrawer.setMapTexts(settings.edits.text);

		if (settings.edits.isInitialized())
		{
			textDrawer.drawTextFromEdits(map, textBackground, graph, null);
		}
		else
		{
			NameCreator nameCreator;
			if (mapParts != null && mapParts.nameCreator != null)
			{
				nameCreator = mapParts.nameCreator;
			}
			else
			{
				nameCreator = ThreadHelper.getInstance().getResult(nameCreatorTask);
				if (mapParts != null)
				{
					mapParts.nameCreator = nameCreator;
				}
			}

			// Initialize rivers before generating text so river names can be placed.
			List<River> rivers = Collections.emptyList();
			if (settings.edits != null)
			{
				if (!settings.edits.hasInitializedRivers)
				{
					settings.edits.initializeRiversFromGraph(graph, settings.resolution);
				}
				rivers = settings.edits.rivers;
			}

			// Generate text regardless off settings.drawText because
			// the editor might be generating the map without text
			// now, but want to show the text later, so in that case we would
			// want to generate the text but not show it.
			textDrawer.generateText(graph, map, nameCreator, textBackground, mountainGroups, cities, graph.getGeneratedLakes(), rivers);
		}

		if (mapParts == null && textBackground != null)
		{
			textBackground.close();
		}
		textBackground = null;

		if (DebugFlags.drawCorners())
		{
			try (Painter p = map.createPainter())
			{
				graph.drawCorners(p, null, null);
			}
		}
		if (DebugFlags.drawVoronoi())
		{
			try (Painter p = map.createPainter())
			{
				p.setColor(Color.white);
				graph.drawVoronoi(p, null, null, false);
			}
		}

		if (DebugFlags.getIndexesOfCentersToHighlight().length > 0)
		{
			try (Painter p = map.createPainter())
			{
				Set<Center> toRender = new HashSet<>();
				for (Integer index : DebugFlags.getIndexesOfCentersToHighlight())
				{
					toRender.add(graph.centers.get(index));
				}
				graph.drawPolygons(p, toRender, (ignored) -> Color.green);
			}
		}

		if (DebugFlags.getIndexesOfEdgesToHighlight().length > 0)
		{
			try (Painter p = map.createPainter())
			{
				for (Integer index : DebugFlags.getIndexesOfEdgesToHighlight())
				{
					Edge e = graph.edges.get(index);

					p.setColor(Color.blue);
					p.setBasicStroke((float) settings.resolution);
					final int diameter = (int) (6.0 * settings.resolution);

					if (e.v0 != null)
					{
						p.drawOval((int) (e.v0.loc.x) - diameter / 2, (int) (e.v0.loc.y) - diameter / 2, diameter, diameter);
					}

					if (e.v1 != null)
					{
						p.drawOval((int) (e.v1.loc.x) - diameter / 2, (int) (e.v1.loc.y) - diameter / 2, diameter, diameter);
					}

					p.setColor(Color.cyan);
					graph.drawEdge(p, e);
				}
			}
		}

		if (DebugFlags.getIndexesOfCornersToHighlight().length > 0)
		{
			try (Painter p = map.createPainter())
			{
				p.setColor(Color.red);
				p.setBasicStroke((float) settings.resolution);
				final int diameter = (int) (16.0 * settings.resolution);
				for (Integer index : DebugFlags.getIndexesOfCornersToHighlight())
				{
					Corner c = graph.corners.get(index);
					p.drawOval((int) (c.loc.x) - diameter / 2, (int) (c.loc.y) - diameter / 2, diameter, diameter);
				}
			}
		}

		if (DebugFlags.highlightSubMapRiverWaypoints() && !DebugFlags.getSubMapRiverWaypointCornerIndexes().isEmpty())
		{
			try (Painter p = map.createPainter())
			{
				// Draw waypoints as magenta X marks so they stay distinguishable from the LandWaterTool's yellow/orange
				// river highlighting (round control-point circles and highlighted polylines), which is shown at the same time.
				// Each X is labeled with its 1-based order in the search (the order waypoints were found while routing).
				p.setColor(Color.magenta);
				p.setBasicStroke((float) (2.0 * settings.resolution));
				p.setFont(Font.create("SansSerif", FontStyle.Bold, (float) (16.0 * settings.resolution)));
				final int radius = (int) (8.0 * settings.resolution);
				List<Integer> waypointCornerIndexes = DebugFlags.getSubMapRiverWaypointCornerIndexes();
				for (int order = 0; order < waypointCornerIndexes.size(); order++)
				{
					int index = waypointCornerIndexes.get(order);
					if (index < 0 || index >= graph.corners.size())
					{
						continue;
					}
					Corner c = graph.corners.get(index);
					int x = (int) c.loc.x;
					int y = (int) c.loc.y;
					p.drawLine(x - radius, y - radius, x + radius, y + radius);
					p.drawLine(x - radius, y + radius, x + radius, y - radius);
					p.drawString(Integer.toString(order + 1), x + radius + (int) (2.0 * settings.resolution), y - radius);
				}
			}
		}

		if (settings.drawBorder)
		{
			Logger.println("Adding border.");
			Image mapOld = map;
			map = background.addBorder(map);
			if (map != mapOld)
			{
				mapOld.close();
			}
			if (mapParts == null)
			{
				background.borderBackground = null;
			}
		}
		if (mapParts == null)
		{
			background.closeImages();
		}
		background = null;

		Logger.println("Map dimensions: " + map.getWidth() + "x" + map.getHeight() + ", resolution scale: " + settings.resolution);

		reportProgressAndCheckForCancel();

		if (settings.drawGrunge && settings.grungeWidth > 0)
		{
			Logger.println("Adding grunge.");
			Image grunge;

			if (isLowMemoryMode && grungeTask == null)
			{
				// Run the job now so it can run in parallel with other stuff.
				grungeTask = startGrungeCreation(settings, mapParts, mapDimensions);
			}

			if (grungeTask != null)
			{
				grunge = ThreadHelper.getInstance().getResult(grungeTask);
			}
			else if (mapParts != null)
			{
				grunge = mapParts.grunge;
			}
			else
			{
				throw new IllegalStateException("Grunge should have been created.");
			}

			if (mapParts != null)
			{
				mapParts.grunge = grunge;
			}

			// Add the grunge to the map.
			map = ImageHelper.getInstance().maskWithColor(map, settings.frayedBorderColor, grunge, true);
		}

		drawOverlayImageIfNeededAndUpdateMapParts(map, settings);

		reportProgressAndCheckForCancel();

		if (settings.frayedBorder)
		{
			Image frayedBorderMask;
			Image frayedBorderBlur;
			if (isLowMemoryMode && frayedBorderTask == null)
			{
				frayedBorderTask = startFrayedBorderCreation(settings, mapDimensions, sizeMultiplier, mapParts);
			}

			if (frayedBorderTask != null)
			{
				Tuple2<Image, Image> tuple;
				tuple = ThreadHelper.getInstance().getResult(frayedBorderTask);

				Logger.println("Adding frayed edges.");
				frayedBorderMask = tuple.getFirst();
				frayedBorderBlur = tuple.getSecond();
			}
			else if (mapParts != null)
			{
				frayedBorderMask = mapParts.frayedBorderMask;
				frayedBorderBlur = mapParts.frayedBorderBlur;
			}
			else
			{
				throw new IllegalStateException("Frayed border should have been created.");
			}

			if (mapParts != null)
			{
				mapParts.frayedBorderMask = frayedBorderMask;
				mapParts.frayedBorderBlur = frayedBorderBlur;
			}

			if (frayedBorderBlur != null)
			{
				map = ImageHelper.getInstance().maskWithColor(map, settings.frayedBorderColor, frayedBorderBlur, true);
			}
			map = ImageHelper.getInstance().setAlphaFromMask(map, frayedBorderMask, true);
		}

		if (nameCreatorTask != null)
		{
			NameCreator nameCreator = ThreadHelper.getInstance().getResult(nameCreatorTask);
			if (mapParts != null)
			{
				mapParts.nameCreator = nameCreator;
			}
		}

		reportProgressAndCheckForCancel();

		double elapsedTime = System.currentTimeMillis() - startTime;
		Logger.println("Total time to generate map (in seconds): " + elapsedTime / 1000.0);

		Logger.println("Done creating map.");

		System.gc();
		return map;
	}

	private Future<Tuple2<Image, Image>> startFrayedBorderCreation(MapSettings settings, Dimension mapDimensions, double sizeMultiplier, MapParts mapParts)
	{
		// Use the random number generator the same whether or not we draw a frayed border.
		if (settings.frayedBorder)
		{
			if (mapParts != null && mapParts.frayedBorderBlur != null && mapParts.frayedBorderMask != null)
			{
				return null;
			}

			Logger.println("Starting job to create frayed edges.");
			return ThreadHelper.getInstance().submit(() ->
			{
				int blurLevel = (int) (settings.frayedBorderBlurLevel * sizeMultiplier);
				Image frayedBorderBlur;
				Image frayedBorderMask;
				// The frayedBorderSize is on a logarithmic scale. 0 should be the minimum value, which will give 100 polygons.
				int polygonCount = (int) (Math.pow(2, settings.frayedBorderSize) * 2 + 100);
				double widthToUse, heightToUse;
				if (settings.rightRotationCount == 1 || settings.rightRotationCount == 3)
				{
					widthToUse = mapDimensions.height;
					heightToUse = mapDimensions.width;
				}
				else
				{
					widthToUse = mapDimensions.width;
					heightToUse = mapDimensions.height;
				}
				WorldGraph frayGraph = GraphCreator.createSimpleGraph(widthToUse, heightToUse, polygonCount, new Random(settings.frayedBorderSeed), settings.resolution, true,
						settings.rightRotationCount, settings.flipHorizontally, settings.flipVertically);
				frayedBorderMask = Image.create(frayGraph.getWidth(), frayGraph.getHeight(), ImageType.Grayscale8Bit);
				try (Painter p = frayedBorderMask.createPainter())
				{
					// Default every pixel to the border color (white, which becomes transparent after the mask is inverted) before drawing
					// the polygons. Otherwise any pixel along the outer edge that the border polygons don't quite cover - which happens when
					// the mask dimensions land on exact integers - would keep the image's default black and show as an opaque line of the map
					// beneath the frayed edge.
					p.setColor(Color.white);
					p.fillRect(0, 0, frayedBorderMask.getWidth(), frayedBorderMask.getHeight());
					frayGraph.drawBorderWhite(p);
				}
				if (blurLevel > 0)
				{
					frayedBorderBlur = ImageHelper.getInstance().blur(frayedBorderMask, blurLevel, true, true);
				}
				else
				{
					frayedBorderBlur = null;
				}

				return new Tuple2<Image, Image>(frayedBorderMask, frayedBorderBlur);
			});
		}
		return null;
	}

	private Future<NameCreator> startNameCreatorCreation(MapSettings settings)
	{
		return ThreadHelper.getInstance().submit(() ->
		{
			return new NameCreator(settings);
		});
	}

	private Future<Image> startGrungeCreation(MapSettings settings, MapParts mapParts, Dimension mapDimensions)
	{
		if (settings.drawGrunge && settings.grungeWidth > 0)
		{
			if (mapParts != null && mapParts.grunge != null)
			{
				return null;
			}

			Logger.println("Starting job to create grunge.");
			return ThreadHelper.getInstance().submit(() ->
			{
				Image grunge;

				// 104567 is an arbitrary number added so that the grunge is not
				// the
				// same pattern as
				// the background.
				final float fractalPower = 1.3f;
				grunge = FractalBGGenerator.generate(new Random(settings.backgroundRandomSeed + 104567), fractalPower, ((int) mapDimensions.width), ((int) mapDimensions.height), 0.75f);

				checkForCancel();

				// Whiten the middle of clouds.
				ImageHelper.getInstance().darkenMiddleOfImage(grunge, settings.grungeWidth, settings.resolution, false);

				return grunge;
			});
		}
		else
		{
			return null;
		}
	}

	private Tuple4<Image, Image, List<Set<Center>>, List<IconDrawTask>> drawTerrainAndIcons(MapSettings settings, MapParts mapParts, WorldGraph graph, Background background, boolean isLowMemoryMode)
	{
		reportProgressAndCheckForCancel();

		// Initialize rivers and stamp their curves onto region-boundary edges before any polygon fill or boundary line is drawn, so those
		// polygons conform to the rivers rather than the reverse (see RiverDrawer#stampRiverCurvesOntoGraphEdges).
		if (!settings.edits.hasInitializedRivers)
		{
			settings.edits.initializeRiversFromGraph(graph, settings.resolution);
		}
		new RiverDrawer(settings, graph).stampRiverCurvesOntoGraphEdges();

		IconDrawer iconDrawer;
		boolean needToAddIcons;
		iconDrawer = new IconDrawer(graph, new Random(r.nextLong()), settings);
		iconDrawer.setLowMemoryMode(isLowMemoryMode);
		if (mapParts != null)
		{
			mapParts.iconDrawer = iconDrawer;
		}
		needToAddIcons = !settings.edits.hasIconEdits;

		List<Set<Center>> mountainAndHillGroups = null;
		List<Set<Center>> mountainGroups = null;
		List<IconDrawTask> cities = null;
		if (needToAddIcons)
		{
			Logger.println("Adding icons.");
			iconDrawer.markMountains();
			iconDrawer.markHills();
			iconDrawer.markCities(settings.cityProbability);
			mountainAndHillGroups = iconDrawer.findMountainAndHillGroups();
			Tuple2<List<Set<Center>>, List<IconDrawTask>> tuple = iconDrawer.addIcons(mountainAndHillGroups, this);
			mountainGroups = tuple.getFirst();
			cities = tuple.getSecond();
		}
		else
		{
			Logger.println("Adding icons from edits.");
			iconDrawer.addOrUpdateIconsFromEdits(settings.edits, graph.centers, null, this);
		}

		// Capture cities lost to water during this full draw so callers (the sub-map preview) can warn about shore-side cities that
		// disappeared. Read from the local iconDrawer rather than mapParts because the sub-map preview draws without mapParts.
		citiesRemovedForTouchingWater = iconDrawer.getCitiesRemovedForTouchingWater();

		reportProgressAndCheckForCancel();

		List<IconDrawTask> iconsToDraw = iconDrawer.getTasksInDrawBoundsSortedAndScaled(null);
		background.doSetupThatNeedsGraphAndIcons(graph, iconsToDraw, null, null, null);
		if (mapParts == null)
		{
			if (background.landBeforeRegionColoring != null && background.landBeforeRegionColoring != background.land)
			{
				background.landBeforeRegionColoring.close();
			}
			background.landBeforeRegionColoring = null;
		}

		reportProgressAndCheckForCancel();

		// Draw mask for land vs ocean.
		Logger.println("Adding land.");
		Image landMask = Image.create(graph.getWidth(), graph.getHeight(), ImageType.Binary);
		{
			try (Painter g = landMask.createPainter())
			{
				graph.drawLandAndOceanBlackAndWhite(g, graph.centers, null);
			}
		}

		Image coastShading;
		Image landBackground = null;
		Image map;
		{
			Tuple2<Image, Image> tuple = darkenLandNearCoastlinesAndRegionBorders(settings, graph, settings.resolution, background.land, background, null, null, null, true);
			Image landBackgroundWithLandAndOcean = tuple.getFirst();
			coastShading = tuple.getSecond();
			map = ImageHelper.getInstance().maskWithColor(landBackgroundWithLandAndOcean, Color.black, landMask, false);

			if (settings.drawRegionColors)
			{
				landBackground = darkenLandNearCoastlinesAndRegionBorders(settings, graph, settings.resolution, background.landColoredBeforeAddingIconColors, background, coastShading, null, null,
						true).getFirst();
			}
			else
			{
				landBackground = landBackgroundWithLandAndOcean;
			}
		}

		reportProgressAndCheckForCancel();

		if (settings.drawRegionBoundaries)
		{
			try (Painter g = map.createPainter(DrawQuality.High))
			{
				g.setColor(settings.regionBoundaryColor);
				graph.drawRegionBoundaries(g, settings.regionBoundaryStyle, null, null);
			}
		}

		reportProgressAndCheckForCancel();

		// Add rivers. Rivers were already initialized and stamped onto region-boundary edges at the top of this method.
		Logger.println("Adding rivers.");
		new RiverDrawer(settings, graph).drawRivers(map, null);

		reportProgressAndCheckForCancel();

		Logger.println("Drawing ocean.");
		{
			if (background.ocean.getWidth() != graph.getWidth() || background.ocean.getHeight() != graph.getHeight())
			{
				throw new IllegalArgumentException("The given ocean background image does not" + " have the same aspect ratio as the given land background image.");
			}

			map = ImageHelper.getInstance().maskWithImage(map, background.ocean, landMask);
		}

		reportProgressAndCheckForCancel();

		Tuple2<Image, Image> oceanTuple = createOceanWavesAndShading(settings, graph, settings.resolution, landMask, null, null);
		Image oceanWaves = oceanTuple.getFirst();
		Image oceanShading = oceanTuple.getSecond();
		Image oceanWithWavesAndShading = background.ocean;
		if (oceanShading != null)
		{
			Logger.println("Adding shading to ocean along coastlines.");
			map = ImageHelper.getInstance().maskWithColor(map, settings.oceanShadingColor, oceanShading, true);
			oceanWithWavesAndShading = ImageHelper.getInstance().maskWithColor(oceanWithWavesAndShading, settings.oceanShadingColor, oceanShading, true);
		}

		if (oceanWaves != null)
		{
			Logger.println("Adding waves to ocean along coastlines.");
			map = ImageHelper.getInstance().maskWithColor(map, settings.oceanWavesColor, oceanWaves, true);
			oceanWithWavesAndShading = ImageHelper.getInstance().maskWithColor(oceanWithWavesAndShading, settings.oceanWavesColor, oceanWaves, true);
		}

		reportProgressAndCheckForCancel();

		// Draw coastlines.
		{
			try (Painter p = map.createPainter(DrawQuality.High))
			{
				p.setColor(settings.coastlineColor);
				graph.drawCoastlineWithLakeShores(p, settings.coastlineWidth * settings.resolution, null, null);
			}
		}

		reportProgressAndCheckForCancel();

		if (settings.drawRoads)
		{
			RoadDrawer roadDrawer = new RoadDrawer(r, settings, graph);
			if (settings.edits == null || !settings.edits.isInitialized())
			{
				Logger.println("Adding roads.");
				roadDrawer.createRoads();
			}
			else
			{
				Logger.println("Drawing roads.");
			}

			roadDrawer.drawRoads(map, null);

			if (DebugFlags.drawRoadDebugInfo())
			{
				roadDrawer.drawRoadDebugInfo(map);
			}
		}

		reportProgressAndCheckForCancel();

		if (settings.drawGridOverlay && settings.gridOverlayLayer == GridOverlayLayer.Under_icons)
		{
			GridDrawer.drawGrid(map, settings, null, map.size(), graph, null);
		}

		reportProgressAndCheckForCancel();

		Logger.println("Drawing all icons.");
		iconDrawer.drawIcons(iconsToDraw, map, landBackground, background.land, oceanWithWavesAndShading, null);
		landBackground = null;

		reportProgressAndCheckForCancel();

		if (settings.drawGridOverlay && settings.gridOverlayLayer == GridOverlayLayer.Over_icons)
		{
			GridDrawer.drawGrid(map, settings, null, map.size(), graph, graph.centers);
		}

		reportProgressAndCheckForCancel();

		// Needed for drawing text
		Image textBackground = updateLandMaskAndCreateTextBackground(settings, graph, landMask, iconsToDraw, settings.drawRegionColors ? background.landColoredBeforeAddingIconColors : background.land,
				background.ocean, background, oceanWaves, oceanShading, coastShading, iconDrawer, null, null);

		if (mapParts != null)
		{
			if (!isLowMemoryMode)
			{
				mapParts.mapBeforeAddingText = map.deepCopy();
			}
			mapParts.textBackground = textBackground;
		}

		if (mapParts == null)
		{
			if (background.land != null)
			{
				background.land.close();
			}
			background.land = null;
		}

		reportProgressAndCheckForCancel();

		return new Tuple4<>(map, textBackground, mountainGroups, cities);
	}

	private Image updateLandMaskAndCreateTextBackground(MapSettings settings, WorldGraph graph, Image landMask, List<IconDrawTask> iconsThatDrew, Image landTexture, Image oceanTexture,
			Background background, Image oceanWaves, Image oceanShading, Image coastShading, IconDrawer iconDrawer, Collection<Center> centersToDraw, Rectangle drawBounds)
	{
		iconDrawer.drawNondecorationContentMasksOntoLandMask(landMask, iconsThatDrew, drawBounds);

		Image textBackground = ImageHelper.getInstance().maskWithColor(landTexture, Color.black, landMask, false);
		textBackground = darkenLandNearCoastlinesAndRegionBorders(settings, graph, settings.resolution, textBackground, background, coastShading, centersToDraw, drawBounds, false).getFirst();
		textBackground = ImageHelper.getInstance().maskWithImage(textBackground, oceanTexture, landMask);
		if (oceanShading != null)
		{
			textBackground = ImageHelper.getInstance().maskWithColor(textBackground, settings.oceanShadingColor, oceanShading, true);
		}
		if (oceanWaves != null)
		{
			textBackground = ImageHelper.getInstance().maskWithColor(textBackground, settings.oceanWavesColor, oceanWaves, true);
		}
		if (settings.drawGridOverlay)
		{
			GridDrawer.drawGrid(textBackground, settings, drawBounds, background.mapBounds.toIntDimension(), graph, centersToDraw);
		}

		return textBackground;
	}

	/**
	 * Sets a callback to be notified of progress during full draws. See {@link #progressListener}.
	 */
	public void setProgressListener(DoubleConsumer progressListener)
	{
		this.progressListener = progressListener;
	}

	/**
	 * Reports the next increment of full-draw progress to {@link #progressListener} (if set) and then checks for cancellation. Called from
	 * the full-draw code path in place of {@link #checkForCancel} so that progress advances at the same points where the draw can be
	 * cancelled.
	 */
	private void reportProgressAndCheckForCancel()
	{
		checkForCancel();
		if (progressListener != null)
		{
			progressStepsCompleted++;
			progressListener.accept(Math.min(1.0, (double) progressStepsCompleted / fullDrawProgressStepCount));
		}
	}

	private void checkForCancel()
	{
		if (isCanceled)
		{
			throw new CancelledException();
		}
	}

	/**
	 * If land near coastlines and region borders should be darkened, then this creates a copy of mapOrSnippet but with that darkening.
	 * Otherwise, it returns mapOrSnippet in the first piece of the tuple unchanged. The second piece is the coast shading mask, which can
	 * be re-used for performance.
	 */
	private Tuple2<Image, Image> darkenLandNearCoastlinesAndRegionBorders(MapSettings settings, WorldGraph graph, double resolutionScaled, Image mapOrSnippet, Background background,
			Image coastShading, Collection<Center> centersToDraw, Rectangle drawBounds, boolean addLoggingEntry)
	{
		double sizeMultiplier = calcSizeMultiplierFromResolutionScale(resolutionScaled);
		int blurLevel = (int) (settings.coastShadingLevel * sizeMultiplier);

		final float scaleForDarkening = coastlineShadingScale;
		int maxPixelValue = Image.getMaxPixelLevelForType(ImageType.Grayscale8Bit);
		double targetStrokeWidth = sizeMultiplier;

		if (blurLevel > 0)
		{
			if (addLoggingEntry)
			{
				Logger.println("Darkening land near shores.");
			}

			boolean drawRegionColorShading = settings.drawRegionBoundaries && settings.drawRegionColors;
			float scale;

			if (drawRegionColorShading)
			{
				scale = ((float) settings.coastShadingColor.getAlpha()) / ((float) (maxPixelValue)) * scaleForDarkening
						* calcScaleToMakeConvolutionEffectsLightnessInvariantToKernelSize(settings.coastShadingLevel, sizeMultiplier)
						* calcScaleCompensateForCoastlineShadingDrawingAtAFullPixelWideAtLowerResolutions(targetStrokeWidth);
			}
			else
			{
				scale = scaleForDarkening * calcScaleToMakeConvolutionEffectsLightnessInvariantToKernelSize(settings.coastShadingLevel, sizeMultiplier)
						* calcScaleCompensateForCoastlineShadingDrawingAtAFullPixelWideAtLowerResolutions(targetStrokeWidth);
			}

			// coastShading can be passed in to save time when calling this method a second time for the text background image.
			if (coastShading == null)
			{
				try (Image coastlineAndLakeShoreMask = Image.create(mapOrSnippet.getWidth(), mapOrSnippet.getHeight(), ImageType.Binary))
				{
					try (Painter p = coastlineAndLakeShoreMask.createPainter(DrawQuality.High))
					{
						p.setColor(Color.white);
						graph.drawCoastlineWithLakeShores(p, targetStrokeWidth, centersToDraw, drawBounds);

						if (settings.drawRegionBoundaries)
						{
							p.setColor(Color.white);
							graph.drawRegionBoundariesSolid(p, sizeMultiplier, false, centersToDraw, drawBounds);
						}
					}

					if (settings.drawRegionBoundaries)
					{
						coastShading = ImageHelper.getInstance().blurAndScale(coastlineAndLakeShoreMask, blurLevel, scale, true);
					}
					else
					{
						coastShading = ImageHelper.getInstance().blurAndScale(coastlineAndLakeShoreMask, blurLevel, scale, true);
					}
				}
			}

			if (drawRegionColorShading)
			{
				// Color the blur according to each region's blur color.
				Map<Integer, Color> colors = new HashMap<>();
				if (graph.regions.size() > 0)
				{
					for (Map.Entry<Integer, Region> regionEntry : graph.regions.entrySet())
					{
						Region reg = regionEntry.getValue();
						Color color = Color.create((int) (reg.backgroundColor.getRed() * regionBlurColorScale), (int) (reg.backgroundColor.getGreen() * regionBlurColorScale),
								(int) (reg.backgroundColor.getBlue() * regionBlurColorScale));
						colors.put(reg.id, color);
					}
				}
				else
				{
					colors.put(0, settings.landColor);
				}
				return new Tuple2<>(ImageHelper.getInstance().maskWithMultipleColors(mapOrSnippet, colors, background.regionIndexes, coastShading, true), coastShading);
			}
			else
			{
				return new Tuple2<>(ImageHelper.getInstance().maskWithColor(mapOrSnippet, settings.coastShadingColor, coastShading, true), coastShading);
			}
		}
		return new Tuple2<>(mapOrSnippet, null);
	}

	private Tuple2<Image, Image> createOceanWavesAndShading(MapSettings settings, WorldGraph graph, double resolutionScale, Image landMask, Collection<Center> centersToDraw, Rectangle drawBounds)
	{
		if (drawBounds == null)
		{
			drawBounds = graph.bounds;
		}
		double sizeMultiplier = calcSizeMultiplierFromResolutionScaleRounded(resolutionScale);

		Image oceanWaves = null;
		Image oceanShading = null;
		if (settings.hasRippleWaves(resolutionScale) || settings.hasConcentricWaves() || settings.hasOceanShading(resolutionScale))
		{
			double targetStrokeWidth = sizeMultiplier;

			if (settings.hasRippleWaves(resolutionScale))
			{
				Image coastlineMask = createCoastlineMask(settings, graph, targetStrokeWidth, centersToDraw, drawBounds);
				float[][] kernel = ImageHelper.getInstance().createPositiveSincKernel((int) (settings.oceanWavesLevel * sizeMultiplier), 1.0 / sizeMultiplier);

				final float scaleForDarkening = coastlineShadingScale;
				float scale = scaleForDarkening * calcScaleToMakeConvolutionEffectsLightnessInvariantToKernelSize(settings.oceanWavesLevel, sizeMultiplier)
						* calcScaleCompensateForCoastlineShadingDrawingAtAFullPixelWideAtLowerResolutions(targetStrokeWidth);
				oceanWaves = ImageHelper.getInstance().convolveGrayscaleThenScale(coastlineMask, kernel, scale, true);
				if (settings.drawOceanEffectsInLakes)
				{
					oceanWaves = removeOceanEffectsFromLand(oceanWaves, landMask);
				}
				else
				{
					oceanWaves = removeOceanEffectsFromLandAndLandLockedLakes(graph, oceanWaves, centersToDraw, drawBounds);
				}
			}
			else if (settings.hasConcentricWaves())
			{
				oceanWaves = createConcentricWavesMask(settings, graph, resolutionScale, landMask, centersToDraw, drawBounds);
			}

			if (settings.hasOceanShading(resolutionScale))
			{
				Image coastlineMask = createCoastlineMask(settings, graph, targetStrokeWidth, centersToDraw, drawBounds);

				final float scaleForDarkening = coastlineShadingScale;
				float scale = scaleForDarkening * calcScaleToMakeConvolutionEffectsLightnessInvariantToKernelSize(settings.oceanShadingLevel, sizeMultiplier)
						* calcScaleCompensateForCoastlineShadingDrawingAtAFullPixelWideAtLowerResolutions(targetStrokeWidth);
				oceanShading = ImageHelper.getInstance().blurAndScale(coastlineMask, (int) (settings.oceanShadingLevel * sizeMultiplier), scale, true);
				if (settings.drawOceanEffectsInLakes)
				{
					oceanShading = removeOceanEffectsFromLand(oceanShading, landMask);
				}
				else
				{
					oceanShading = removeOceanEffectsFromLandAndLandLockedLakes(graph, oceanShading, centersToDraw, drawBounds);
				}
			}
		}
		return new Tuple2<>(oceanWaves, oceanShading);
	}

	private Image createCoastlineMask(MapSettings settings, WorldGraph graph, double targetStrokeWidth, Collection<Center> centersToDraw, Rectangle drawBounds)
	{
		Image coastlineMask = Image.create((int) drawBounds.width, (int) drawBounds.height, ImageType.Binary);
		try (Painter g = coastlineMask.createPainter())
		{
			g.setColor(Color.white);

			if (settings.drawOceanEffectsInLakes)
			{
				graph.drawCoastlineWithLakeShores(g, targetStrokeWidth, centersToDraw, drawBounds);
			}
			else
			{
				graph.drawCoastline(g, targetStrokeWidth, centersToDraw, drawBounds);
			}
		}

		return coastlineMask;
	}

	private Image createConcentricWavesMask(MapSettings settings, WorldGraph graph, double resolutionScaled, Image landMask, Collection<Center> centersToDraw, Rectangle drawBounds)
	{
		Image oceanEffects = Image.create((int) drawBounds.width, (int) drawBounds.height, ImageType.Grayscale8Bit);
		double sizeMultiplier = calcSizeMultiplierFromResolutionScaleRounded(resolutionScaled);

		double widthBetweenWaves = concentricWaveWidthBetweenWaves * sizeMultiplier;
		double waveWidth = concentricWaveLineWidth * sizeMultiplier;
		double largestLineWidth = settings.concentricWaveCount * (widthBetweenWaves + waveWidth);
		final double opacityOfLastWave;
		if (settings.fadeConcentricWaves)
		{
			if (settings.concentricWaveCount == 1)
			{
				opacityOfLastWave = 1.0;
			}
			else if (settings.concentricWaveCount == 2)
			{
				opacityOfLastWave = 0.35;
			}
			else if (settings.concentricWaveCount == 3)
			{
				opacityOfLastWave = 0.22;
			}
			else
			{
				opacityOfLastWave = 0.2;
			}

		}
		else
		{
			opacityOfLastWave = 1.0;
		}

		// Always search the entire graph (not just centersToDraw) so the shore-edge polylines are built and ordered identically for
		// incremental draws and full draws. Concentric waves smooth each shore polyline into a curve (edgeListToDrawPoints +
		// CurveCreator.createCurve), and that curve's shape depends on where the polyline starts and which direction it runs. If an
		// incremental update gathered only the centersToDraw portion of a coastline, the truncated/reordered polyline would produce a
		// slightly different curve, and that difference gets amplified in the offset outer wave rings, leaving a visible jog where the
		// updated region meets the rest of the map. (The plain coastline draws each edge independently, so it never has this problem.)
		List<List<Edge>> shoreEdges = graph.findShoreEdges(centersToDraw, settings.drawOceanEffectsInLakes, true);
		try (Painter p = oceanEffects.createPainter(DrawQuality.High))
		{
			for (int i : new Range(0, settings.concentricWaveCount))
			{
				double whiteWidth = largestLineWidth - (i * (widthBetweenWaves + waveWidth));
				if (whiteWidth <= 0)
				{
					continue;
				}

				double waveOpacity;
				if (settings.concentricWaveCount == 1)
				{
					waveOpacity = 1.0;
				}
				else
				{
					double percentDone = ((double) (settings.concentricWaveCount - 1 - i)) / (settings.concentricWaveCount - 1);
					waveOpacity = (percentDone * opacityOfLastWave + (1.0 - percentDone));
				}
				assert waveOpacity <= 1.0;
				assert waveOpacity >= 0.0;

				BiFunction<Boolean, Random, Double> getNewSkipDistance = (isDrawing, rand) ->
				{
					int waveNumber = settings.concentricWaveCount - i;
					double scaleToMakeFartherOutWavesShorter = ((((((double) SettingsGenerator.maxConcentricWaveCountInEditor - 1) - (waveNumber - 1)))
							/ ((double) SettingsGenerator.maxConcentricWaveCountInEditor - 1)));
					final double scaleAtLastWave = 0.5;
					scaleToMakeFartherOutWavesShorter = 1.0 - ((1.0 - scaleToMakeFartherOutWavesShorter) * (1.0 - scaleAtLastWave));

					final double scaleForAll = 4 * settings.resolution * scaleToMakeFartherOutWavesShorter;
					final double maxNotDrawLength = 3 * scaleForAll;
					final double minNotDrawLength = 2 * scaleForAll;
					final double maxDrawLength = 24 * scaleForAll;
					final double minDrawLength = 19 * scaleForAll;
					return isDrawing ? rand.nextDouble(minDrawLength, maxDrawLength + 1) : rand.nextDouble(minNotDrawLength, maxNotDrawLength + 1);
				};

				int level = (int) (oceanEffects.getMaxPixelLevel() * waveOpacity);
				p.setColor(Color.create(level, level, level));
				double varianceRange = settings.jitterToConcentricWaves ? calcJitterVarianceRange(resolutionScaled) : 0.0;
				p.setStrokeToSolidLineWithNoEndDecorations((float) whiteWidth);
				graph.drawCoastlineWithVariation(p, settings.backgroundRandomSeed + i, varianceRange, widthBetweenWaves, settings.brokenLinesForConcentricWaves, drawBounds, getNewSkipDistance,
						shoreEdges);

				p.setColor(Color.black);
				p.setBasicStroke((float) (whiteWidth - waveWidth));
				graph.drawCoastlineWithVariation(p, settings.backgroundRandomSeed + i, varianceRange, widthBetweenWaves, false, drawBounds, getNewSkipDistance, shoreEdges);
			}
		}

		if (settings.drawOceanEffectsInLakes)
		{
			oceanEffects = removeOceanEffectsFromLand(oceanEffects, landMask);
		}
		else
		{
			oceanEffects = removeOceanEffectsFromLandAndLandLockedLakes(graph, oceanEffects, centersToDraw, drawBounds);
		}

		return oceanEffects;
	}

	private double calcJitterVarianceRange(double resolutionScaled)
	{
		double sizeMultiplier = calcSizeMultiplierFromResolutionScaleRounded(resolutionScaled);
		double widthBetweenWaves = concentricWaveWidthBetweenWaves * sizeMultiplier;
		return 0.25 * widthBetweenWaves;
	}

	private static float calcScaleToMakeConvolutionEffectsLightnessInvariantToKernelSize(int kernelSize, double sizeMultiplier)
	{
		int lightnessBasedOnKernelSizesBeforeIAddedFixToMakeShadingNotGetLighterWhenItGotWider = (int) (15 * sizeMultiplier);
		return ImageHelper.getInstance().getGaussianMode(lightnessBasedOnKernelSizesBeforeIAddedFixToMakeShadingNotGetLighterWhenItGotWider)
				/ ImageHelper.getInstance().getGaussianMode((int) (kernelSize * sizeMultiplier));
	}

	private static Image removeOceanEffectsFromLandAndLandLockedLakes(WorldGraph graph, Image oceanEffects, Collection<Center> centersToDraw, Rectangle drawBounds)
	{
		// One might wonder why I'm creating a mask to black out lakes and land, when in theory I could just draw them as black into
		// oceanEffects to save CPU and memory. The reason is because the Voronoi graph has a weakness that it doesn't contain edges or
		// noisy edges for centers along the border (the edge of the map). Because of this, I need to draw border centers first, then draw
		// centers with noisy edges over them. Thus I must draw both the land and lakes, and their ocean neighbors, so I need to do the
		// drawing as a mask and then apply it onto oceanEffects.
		Image landAndLakeMask = Image.create(oceanEffects.getWidth(), oceanEffects.getHeight(), ImageType.Grayscale8Bit);
		try (Painter p = landAndLakeMask.createPainter())
		{
			graph.drawLandAndLakesBlackAndOceanWhite(p, centersToDraw, drawBounds);
		}
		return ImageHelper.getInstance().maskWithColor(oceanEffects, Color.black, landAndLakeMask, false);
	}

	private static Image removeOceanEffectsFromLand(Image oceanEffects, Image landMask)
	{
		return ImageHelper.getInstance().maskWithColor(oceanEffects, Color.black, landMask, true);
	}

	private static float calcScaleCompensateForCoastlineShadingDrawingAtAFullPixelWideAtLowerResolutions(double targetStrokeWidth)
	{
		if (targetStrokeWidth >= 1f)
		{
			return 1f;
		}

		// The stroke will be drawn a 1 pixel wide because that is the smallest
		// it can be drawn, but that will make the coastline shading relatively
		// much darker than it should be. In this case multiplying the convolved
		// shading values by the target stroke width lowers them appropriately.
		return (float) targetStrokeWidth;
	}

	private static void assignRandomRegionColors(WorldGraph graph, MapSettings settings)
	{
		float[] landHsb = settings.regionBaseColor.getHSB();
		List<Color> regionColorOptions = new ArrayList<>();
		Random rand = new Random(settings.regionsRandomSeed);
		for (@SuppressWarnings("unused")
		int i : new Range(graph.regions.size()))
		{
			regionColorOptions.add(generateRegionColor(rand, landHsb, settings.hueRange, settings.saturationRange, settings.brightnessRange));
		}

		assignRegionColors(graph, regionColorOptions);
	}

	/**
	 * Assigns the color of each political region.
	 */
	private static void assignRegionColors(WorldGraph graph, List<Color> colorOptions)
	{
		for (int i : new Range(graph.regions.size()))
		{
			graph.regions.get(i).backgroundColor = colorOptions.get(i % colorOptions.size());
		}
	}

	private static Color generateRegionColor(Random rand, float[] landHsb, float hueRange, float saturationRange, float brightnessRange)
	{
		float hue = (float) (landHsb[0] * 360 + (rand.nextDouble() - 0.5) * hueRange);
		float saturation = ImageHelper.getInstance().bound((int) (landHsb[1] * 100 + (rand.nextDouble() - 0.5) * saturationRange));
		float brightness = ImageHelper.getInstance().bound((int) (landHsb[2] * 100 + (rand.nextDouble() - 0.5) * brightnessRange));
		return Color.createFromHSB(hue / 360f, saturation / 100f, brightness / 100f);
	}

	public static Color generateColorFromBaseColor(Random rand, Color base, float hueRange, float saturationRange, float brightnessRange)
	{
		float[] hsb = base.getHSB();
		return generateRegionColor(rand, hsb, hueRange, saturationRange, brightnessRange);
	}

	public static WorldGraph createGraph(MapSettings settings, boolean createElevationBiomesLakesAndRegions)
	{
		Dimension mapBounds = Background.calcMapBoundsAndAdjustResolutionIfNeeded(settings, null);
		Random r = new Random(settings.randomSeed);
		return MapCreator.createGraph(settings, mapBounds.width, mapBounds.height, r, settings.resolution, createElevationBiomesLakesAndRegions);
	}

	/**
	 * Creates a WorldGraph and applies edge edits (e.g. river levels) from settings.edits, then derives rivers from the graph the same way
	 * the full-draw path does (see {@link #createMap}). Intended for use in unit tests that need a fully initialized graph without
	 * rendering.
	 * <p>
	 * As in the full draw, river initialization is guarded by {@link MapEdits#hasInitializedRivers}: modern {@code .nort} files already
	 * store initialized rivers, and {@link MapEdits#initializeRiversFromGraph} appends rather than replaces, so calling it unconditionally
	 * would add a second, re-derived copy of every river (which renders as overlapping duplicate segments and loops).
	 * </p>
	 */
	public static WorldGraph createGraphForUnitTests(MapSettings settings)
	{
		Dimension mapBounds = Background.calcMapBoundsAndAdjustResolutionIfNeeded(settings, null);
		Random r = new Random(settings.randomSeed);
		WorldGraph graph = MapCreator.createGraph(settings, mapBounds.width, mapBounds.height, r, settings.resolution, !settings.edits.isInitialized());
		if (!settings.edits.hasInitializedRivers)
		{
			settings.edits.initializeRiversFromGraph(graph, settings.resolution);
		}
		return graph;
	}

	private static WorldGraph createGraph(MapSettings settings, double width, double height, Random r, double resolutionScale, boolean createElevationBiomesLakesAndRegions)
	{
		double widthToUse, heightToUse;
		if (settings.rightRotationCount == 1 || settings.rightRotationCount == 3)
		{
			widthToUse = height;
			heightToUse = width;
		}
		else
		{
			widthToUse = width;
			heightToUse = height;
		}

		WorldGraph graph = GraphCreator.createGraph(widthToUse, heightToUse, settings.worldSize, settings.edgeLandToWaterProbability, settings.centerLandToWaterProbability, new Random(r.nextLong()),
				resolutionScale, settings.lineStyle, settings.pointPrecision, createElevationBiomesLakesAndRegions, settings.lloydRelaxationsScale, settings.areRegionBoundariesVisible(),
				settings.rightRotationCount, settings.flipHorizontally, settings.flipVertically, settings.landShape, settings.regionCount);

		// Setup region colors even if settings.drawRegionColors = false because
		// edits need them in case someone edits a map without region colors,
		// then later enables region colors.
		assignRandomRegionColors(graph, settings);

		applyRegionEdits(graph, settings.edits);
		// Apply river edits before center edits because applying center edits smoothes region boundaries, which depends on rivers.
		// For old files that have not yet been converted (hasInitializedRivers == false), fall back to applyEdgeEdits which reads the
		// legacy
		// edgeEdits data. initializeRiversFromGraph will run on the first full draw to seed River objects from the restored edge.river
		// values.
		if (settings.edits != null && settings.edits.hasInitializedRivers)
		{
			applyRiverEdits(graph, settings.edits);
		}
		else
		{
			applyEdgeEdits(graph, settings.edits, null);
		}
		applyCenterEdits(graph, settings.edits, null, settings.areRegionBoundariesVisible(), resolutionScale);

		return graph;
	}

	/*
	 * A constant based on the resolution for determining how large things should draw.
	 */
	public static double calcSizeMultiplierFromResolutionScale(double resolutionScale)
	{
		return (8.0 / 3.0) * resolutionScale;
	}

	/**
	 * Like calcSizeMultiplierFromResolutionScale, but rounds to the nearest tenth for use with components that have that limit on numeric
	 * precision.
	 */
	public static double calcSizeMultiplierFromResolutionScaleRounded(double resolutionScale)
	{
		return Math.round(10.0 * calcSizeMultiplierFromResolutionScale(resolutionScale)) / 10.0;
	}

	private static void applyRegionEdits(WorldGraph graph, MapEdits edits)
	{
		if (edits == null || edits.regionEdits.isEmpty())
		{
			return;
		}

		for (RegionEdit edit : edits.regionEdits.values())
		{
			Region region = graph.regions.get(edit.regionId);
			if (region == null)
			{
				region = new Region();
				region.id = edit.regionId;
				region.backgroundColor = edit.color;
				graph.regions.put(edit.regionId, region);
			}
			else
			{
				region.backgroundColor = edit.color;
			}
		}
	}

	/**
	 * Applies changes to Centers from user edits to the Center objects in the graph.
	 *
	 * @param graph
	 *            The graph being drawn
	 * @param edits
	 *            User edits
	 * @param centerEditChanges
	 *            Edits of centers that changed. Pass this in if only some of the center edits changed, avoid having to loop over all of
	 *            them.
	 * @param areRegionBoundariesVisible
	 *            whether region boundaries are visible on the map
	 * @return A set of centers whose noisy edges have been recalculated, meaning something about their terrain or region boundaries
	 *         changed.
	 */
	private static Set<Center> applyCenterEdits(WorldGraph graph, MapEdits edits, Collection<CenterEdit> centerEditChanges, boolean areRegionBoundariesVisible, double resolutionScale)
	{
		if (edits == null || edits.centerEdits.isEmpty())
		{
			return Collections.emptySet();
		}

		if (edits.centerEdits.size() != graph.centers.size())
		{
			throw new IllegalArgumentException("The map edits have " + edits.centerEdits.size() + " polygons, but the world size is " + graph.centers.size());
		}

		if (centerEditChanges == null)
		{
			centerEditChanges = edits.centerEdits.values();
		}

		Set<Center> centersChanged = new HashSet<>();
		Set<Center> needsRebuildNoisyEdges = new HashSet<>();

		for (CenterEdit cEdit : centerEditChanges)
		{
			Center center = graph.centers.get(cEdit.index);
			centersChanged.add(center);
			Integer currentRegionId = center.region == null ? null : center.region.id;
			boolean needsRebuild = center.isWater != cEdit.isWater || currentRegionId != cEdit.regionId;
			center.isWater = cEdit.isWater;
			center.isLake = cEdit.isLake;

			if (cEdit.regionId != null)
			{
				Region region = graph.regions.get(cEdit.regionId);
				// region can be null if the map is edited while drawing it. If
				// that happens, then the region color of this center will be
				// updated the next time the map draws.
				if (region != null)
				{
					if (center.region != null && center.region.id != region.id)
					{
						needsRebuild = true;
					}
					region.addAndSetRegion(center);
					// We don't know which region the center came from, so
					// remove it from all of them except the one it is in.
					for (Region r : graph.regions.values())
					{
						if (r.id != region.id)
						{
							r.remove(center);
						}
					}
				}
			}

			if (center.isWater && center.region != null)
			{
				center.region.remove(center);
				center.region = null;
				needsRebuild = true;
			}

			if (needsRebuild)
			{
				needsRebuildNoisyEdges.add(center);
			}
		}

		needsRebuildNoisyEdges.addAll(graph.smoothCoastlinesAndRegionBoundariesIfNeeded(centersChanged, graph.noisyEdges.getLineStyle(), areRegionBoundariesVisible));

		for (Center center : needsRebuildNoisyEdges)
		{
			graph.rebuildNoisyEdgesForCenter(center, needsRebuildNoisyEdges);
		}

		// Smoothing may have moved corners (a river edge no longer counts as a region boundary for
		// smoothing, so adjacent corners can shift). The river itself draws from the new corner
		// positions via the noisy-edge path, but stored river.nodes locations still point at the old
		// positions, which would cause the editor's control points to appear off the visible river.
		// Re-sync any polygon-mode nodes (those with an edgeIndexToNext) to their edge's current
		// corner locations.
		RiverDrawer.resyncRiverNodeLocationsToGraph(edits.rivers, graph, resolutionScale);

		return needsRebuildNoisyEdges;
	}

	/**
	 * Copies stored legacy {@link EdgeEdit} data onto the corresponding {@link Edge} objects in the graph.
	 * <p>
	 * This is a migration path for old save files. {@code EdgeEdit} originally stored river width levels, which have since moved to
	 * {@link nortantis.editor.River} inside {@link MapEdits#rivers}. On the first draw of an old file,
	 * {@link MapEdits#hasInitializedRivers} is {@code false}, so this method restores {@code edge.river} from the stored {@code edgeEdits};
	 * then {@link MapEdits#initializeRiversFromGraph} will seed the new {@code River} objects from those values. For new files,
	 * {@link #applyRiverEdits} is used instead.
	 * </p>
	 */
	/**
	 * Runs the legacy {@link EdgeEdit}-to-{@link River} migration when {@code hasInitializedRivers} is false. The full-draw path already
	 * does this during {@code createGraphAndApplyEdits} + {@code createMap}, but every incremental update entry point must also call this
	 * so that undoing past the initial load (on a pre-3.19 file) re-runs the migration on the next draw — without it, the rivers stay
	 * empty.
	 */
	private static void migrateLegacyRiversIfNeeded(MapSettings settings, WorldGraph graph)
	{
		if (settings.edits == null || settings.edits.hasInitializedRivers)
		{
			return;
		}
		applyEdgeEdits(graph, settings.edits, null);
		settings.edits.initializeRiversFromGraph(graph, settings.resolution);
	}

	// Reads EdgeEdit.riverLevel, which is the deprecated legacy river storage. This method only runs
	// for pre-3.19 save files (hasInitializedRivers == false) — see migrateLegacyRiversIfNeeded and
	// the createGraphAndApplyEdits else-branch — so the deprecation warnings here are expected.
	@SuppressWarnings("deprecation")
	private static void applyEdgeEdits(WorldGraph graph, MapEdits edits, Collection<EdgeEdit> edgeChanges)
	{
		if (edits == null || edits.edgeEdits.isEmpty())
		{
			return;
		}

		if (edgeChanges == null)
		{
			// Since edits.edgeEdits does not always contain an entry for every edge, when the list of changes to apply is null, clear out
			// the fields that can change before applying edge edits.
			for (Edge edge : graph.edges)
			{
				edge.river = 0;
			}

			edgeChanges = edits.edgeEdits.values();
		}

		for (EdgeEdit eEdit : edgeChanges)
		{
			Edge edge = graph.edges.get(eEdit.index);
			boolean needsRebuild = eEdit.riverLevel != edge.river && edge.d0 != null;
			graph.edges.get(eEdit.index).river = eEdit.riverLevel;
			if (needsRebuild)
			{
				graph.rebuildNoisyEdgesForCenter(edge.d0);
			}
		}
	}

	/**
	 * Restores {@link Edge#river} levels on the graph from the {@link River} objects in {@link MapEdits#rivers}. Clears all edge river
	 * levels first, then re-applies them from the stored River paths. Segments whose
	 * {@link nortantis.editor.RiverPathNode#getEdgeIndexToNext()} is set (polygon-mode rivers) are written back to that edge; freehand
	 * segments without an edge index are skipped because they do not lie on a single Voronoi edge.
	 */
	private static void applyRiverEdits(WorldGraph graph, MapEdits edits)
	{
		int[] oldRivers = new int[graph.edges.size()];
		for (Edge edge : graph.edges)
		{
			oldRivers[edge.index] = edge.river;
			edge.river = 0;
		}

		if (edits != null)
		{
			for (River river : edits.rivers)
			{
				List<RiverPathNode> nodes = river.nodes;
				for (int i = 0; i < nodes.size() - 1; i++)
				{
					int edgeIndex = nodes.get(i).getEdgeIndexToNext();
					if (edgeIndex == RiverPathNode.EDGE_INDEX_NONE || edgeIndex < 0 || edgeIndex >= graph.edges.size())
					{
						continue;
					}
					Edge edge = graph.edges.get(edgeIndex);
					edge.river = Math.max(edge.river, nodes.get(i).getWidthLevelToNext());
				}
			}
		}

		for (Edge edge : graph.edges)
		{
			if (edge.river != oldRivers[edge.index] && edge.d0 != null)
			{
				graph.rebuildNoisyEdgesForCenter(edge.d0);
			}
		}
	}

	public Image createHeightMap(MapSettings settings)
	{
		r = new Random(settings.randomSeed);
		Dimension mapBounds;
		if (settings.rightRotationCount == 1 || settings.rightRotationCount == 3)
		{
			mapBounds = new Dimension(settings.generatedHeight * settings.heightmapResolution, settings.generatedWidth * settings.heightmapResolution);
		}
		else
		{
			mapBounds = new Dimension(settings.generatedWidth * settings.heightmapResolution, settings.generatedHeight * settings.heightmapResolution);
		}
		WorldGraph graph = createGraph(settings, mapBounds.width, mapBounds.height, r, settings.heightmapResolution, true);
		return GraphCreator.createHeightMap(graph, new Random(settings.randomSeed));
	}

	private List<CenterEdit> getCenterEditsForCenters(MapEdits edits, Collection<Center> centers)
	{
		return centers.stream().map(center -> edits.centerEdits.get(center.index)).collect(Collectors.toList());
	}

	public void cancel()
	{
		isCanceled = true;
	}

	public boolean isCanceled()
	{
		return isCanceled;
	}

	public static int calcMaximumResolution()
	{
		// Reserve some space for the editor.
		int bytesReservedForEditor = 900 * 1024 * 1024;

		long maxBytes = Runtime.getRuntime().maxMemory() - bytesReservedForEditor;
		// The required memory is quadratic in the resolution used.
		// To generate a map at resolution 225 takes 7GB, so 7×1024^3÷(225^2)
		// = 148468.
		int maxResolution = (int) Math.sqrt(maxBytes / 148468L);

		// The FFT-based code will create arrays in powers of 2.
		int nextPowerOf2 = ImageHelper.getInstance().getJTransformsMixedRadixSizeEqualOrLargerThan((int) (maxResolution / 100.0));
		int resolutionAtNextPowerOf2 = nextPowerOf2 * 100;
		// Average with the original prediction because not all code is
		// FFT-based.
		maxResolution = (maxResolution + resolutionAtNextPowerOf2) / 2;

		if (maxResolution > 500)
		{
			// This is in case Runtime.maxMemory returns Long's max value, which
			// it says it will if it fails.
			return 1000;
		}
		if (maxResolution < 100)
		{
			return 100;
		}
		// The resolution slider uses multiples of 25.
		maxResolution -= maxResolution % 25;
		return maxResolution;
	}

	private static double calcMaxResolutionScale()
	{
		return calcMaximumResolution() / 100.0;
	}

	public void addWarningMessage(String message)
	{
		if (!warningMessages.contains(message))
		{
			Logger.println("Warning: " + message);
			warningMessages.add(message);
		}
	}

	public List<String> getWarningMessages()
	{
		return warningMessages;
	}

	/**
	 * Returns the city icons dropped from the last full draw because they landed on water. The list keeps duplicates (so its size is the
	 * number of cities lost) and is empty when no cities were lost.
	 */
	public List<IconDrawer.CityIconRemovedForWater> getCitiesRemovedForTouchingWater()
	{
		return citiesRemovedForTouchingWater;
	}

	private static void drawOverlayImageIfNeededAndUpdateMapParts(Image map, MapSettings settings)
	{
		if (settings.drawOverlayImage)
		{
			drawOverlayImage(map, settings, null, map.size());
		}
	}

	/**
	 * Draws an overlay image on top of mapOrSnippet, scaled to the maximum size it can be and still fit into the center of mapOrSnippet.
	 *
	 * @param mapOrSnippet
	 *            Either the entire map, or a snippet out of the map whose bounds is drawBounds.
	 * @param settings
	 *            Map settings
	 * @param drawBounds
	 *            For incremental updates. When not null, mapOrSnippet should be a snippet from the main map, and this is the bounds of that
	 *            snippet. Does not include border width.
	 * @param mapSize
	 *            The size of the entire map, including borders, as it is drawn.
	 */
	public static void drawOverlayImage(Image mapOrSnippet, MapSettings settings, Rectangle drawBounds, IntDimension mapSize)
	{
		Tuple2<IntRectangle, Image> tuple = getOverlayPositionAndImage(settings.overlayImagePath, settings.overlayScale, settings.overlayOffsetResolutionInvariant, settings.resolution, mapSize);
		if (tuple == null)
		{
			return;
		}

		IntRectangle overlayPosition = tuple.getFirst();
		Image overlayImage = tuple.getSecond();

		int borderWidthScaledByResolution = Background.calcBorderWidthScaledByResolution(settings);

		try (Painter p = mapOrSnippet.createPainter(DrawQuality.High))
		{
			int x = overlayPosition.x;
			int y = overlayPosition.y;
			if (drawBounds != null)
			{
				IntRectangle drawBoundsAdjustedForBorder = new IntRectangle(drawBounds.upperLeftCorner().toIntPoint().x + borderWidthScaledByResolution,
						drawBounds.upperLeftCorner().toIntPoint().y + borderWidthScaledByResolution, (int) drawBounds.width, (int) drawBounds.height);

				x -= drawBoundsAdjustedForBorder.x;
				y -= drawBoundsAdjustedForBorder.y;
			}

			// Set the transparency level
			float alpha = (100 - settings.overlayImageTransparency) / 100.0f;
			p.setAlphaComposite(AlphaComposite.SrcAtop, alpha);

			p.drawImage(overlayImage, x, y, overlayPosition.width, overlayPosition.height);
		}
	}

	public static Tuple2<IntRectangle, Image> getOverlayPositionAndImage(String overlayImagePath, double overlayScale, Point overlayOffsetResolutionInvariant, double resolutionScale,
			IntDimension mapSize)
	{
		if (StringUtils.isEmpty(overlayImagePath))
		{
			return null;
		}

		String overlayPath = FileHelper.replaceHomeFolderPlaceholder(overlayImagePath);
		File file = new File(overlayPath);
		if (!file.exists())
		{
			throw new RuntimeException("The overlay image '" + overlayPath + "' does not exist.");
		}
		if (file.isDirectory())
		{
			throw new RuntimeException("The overlay image '" + overlayPath + "' is a folder. It should be a JPG or PNG image file.");
		}

		Image overlayImage = ImageCache.getInstance(Assets.installedArtPack, null).getImageFromFile(file.toPath());

		// Calculate the maximum size the overlay can be while still fitting within the map
		double widthRatio = (double) mapSize.width / overlayImage.getWidth();
		double heightRatio = (double) mapSize.height / overlayImage.getHeight();
		double scale = Math.min(widthRatio, heightRatio) * overlayScale;

		int scaledOverlayWidth = (int) (overlayImage.getWidth() * scale);
		int scaledOverlayHeight = (int) (overlayImage.getHeight() * scale);

		// Calculate the position of the overlay on the map.
		int x = (mapSize.width - scaledOverlayWidth) / 2 + (int) (overlayOffsetResolutionInvariant.x * resolutionScale);
		int y = (mapSize.height - scaledOverlayHeight) / 2 + (int) (overlayOffsetResolutionInvariant.y * resolutionScale);
		IntRectangle overlayPosition = new IntRectangle(x, y, scaledOverlayWidth, scaledOverlayHeight);
		return new Tuple2<>(overlayPosition, overlayImage);
	}
}
