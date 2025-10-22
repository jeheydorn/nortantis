package nortantis;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import nortantis.editor.CenterEdit;
import nortantis.editor.EdgeEdit;
import nortantis.editor.FreeIcon;
import nortantis.editor.MapParts;
import nortantis.editor.RegionEdit;
import nortantis.geom.Dimension;
import nortantis.geom.IntDimension;
import nortantis.geom.IntPoint;
import nortantis.geom.IntRectangle;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Edge;
import nortantis.graph.voronoi.EdgeDrawType;
import nortantis.platform.AlphaComposite;
import nortantis.platform.Color;
import nortantis.platform.DrawQuality;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.Painter;
import nortantis.swing.MapEdits;
import nortantis.util.Assets;
import nortantis.util.FileHelper;
import nortantis.util.ImageHelper;
import nortantis.util.Logger;
import nortantis.util.Range;
import nortantis.util.ThreadHelper;
import nortantis.util.Tuple2;
import nortantis.util.Tuple4;

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
	private List<String> warningMessages;
	public ConcurrentHashMap<Integer, Center> centersToRedrawLowPriority;

	public MapCreator()
	{
		warningMessages = new ArrayList<>();
		centersToRedrawLowPriority = new ConcurrentHashMap<>();
	}

	public IntRectangle incrementalUpdateText(final MapSettings settings, MapParts mapParts, Image fullSizeMap, List<MapText> textChanged)
	{
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

	public IntRectangle incrementalUpdateIcons(final MapSettings settings, MapParts mapParts, Image fullSizeMap,
			List<FreeIcon> iconsChanged)
	{
		Rectangle changeBounds = null;
		for (FreeIcon icon : iconsChanged)
		{
			IconDrawTask task = mapParts.iconDrawer.toIconDrawTask(icon);
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

	private IntRectangle incrementalUpdateMultipleBounds(final MapSettings settings, MapParts mapParts, Image fullSizeMap,
			List<Rectangle> changeBounds, boolean onlyTextChanged)
	{
		TextDrawer textDrawer = new TextDrawer(settings);
		textDrawer.setMapTexts(settings.edits.text);
		double effectsPadding = calcEffectsPadding(settings);
		mapParts.iconDrawer = new IconDrawer(mapParts.graph, new Random(), settings);

		final int paddingToAccountForIntegerTruncation = 4;
		IntRectangle bounds = null;

		HashSet<Rectangle> noDuplicates = new HashSet<>(changeBounds);
		for (Rectangle change : noDuplicates)
		{
			if (change == null)
			{
				continue;
			}
			Rectangle padded = change.pad(paddingToAccountForIntegerTruncation, paddingToAccountForIntegerTruncation);
			mapParts.iconDrawer.addOrUpdateIconsFromEdits(settings.edits, Collections.emptySet(), this);
			IntRectangle updateBounds = incrementalUpdateBounds(settings, mapParts, fullSizeMap, padded, effectsPadding, textDrawer,
					onlyTextChanged);
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
	 * @param map
	 *            The full sized map to update
	 * @param centerChanges
	 *            Edits for centers that need to be re-drawn
	 * @param edgeChanges
	 *            If edges changed, this is the list of edge edits that changed
	 * @param isLowPriorityChange
	 *            Tells whether this update was submitted as a low priority change. In theory the drawing code doesn't need to know this
	 *            because low priority changes should never change something that then requires submitting more low priority changes, but
	 *            since my code for detecting when coastlines need to be smoothed is imperfect, I added this flag.
	 */
	public IntRectangle incrementalUpdateForCentersAndEdges(final MapSettings settings, MapParts mapParts, Image fullSizedMap,
			Set<Integer> centersChangedIds, Set<Integer> edgesChangedIds, boolean isLowPriorityChange)
	{
		Set<Center> centersChanged;
		if (centersChangedIds != null)
		{
			centersChanged = new HashSet<>(
					centersChangedIds.stream().map(id -> mapParts.graph.centers.get(id)).collect(Collectors.toSet()));
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
		// Apply edge edits before center edits because applying center edits smoothes region boundaries, which depends on rivers, which are
		// edge edits.
		{
			Set<EdgeEdit> edgeEdits;
			if (edgesChangedIds != null)
			{
				edgeEdits = getEdgeEditsForEdgeIds(settings.edits, edgesChangedIds);
			}
			else
			{
				edgeEdits = new HashSet<EdgeEdit>();
			}
			if (centersChanged != null)
			{
				edgeEdits.addAll(getEdgeEditsForCenters(settings.edits, centersChanged));
			}
			applyEdgeEdits(mapParts.graph, settings.edits, edgeEdits);
		}
		Set<Center> centersChangedThatAffectedLandOrRegionBoundaries = applyCenterEdits(mapParts.graph, settings.edits,
				getCenterEditsForCenters(settings.edits, centersChanged), settings.drawRegionBoundaries || settings.drawRegionColors);

		Rectangle centersChangedBounds = WorldGraph.getBoundingBox(centersChanged);

		if (centersChangedBounds == null)
		{
			// Nothing changed
			return null;
		}

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

		mapParts.graph.updateCenterLookupTable(centersChanged);

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

			// Expand the replace bounds to include text that touches the centers that changed because that text could switch from one line
			// to two or vice versa.
			if (settings.drawText)
			{
				Rectangle textChangeBounds = textDrawer.expandBoundsToIncludeText(settings.edits.text, mapParts.graph, centersChangedBounds,
						settings);
				replaceBounds = replaceBounds.add(textChangeBounds);
			}
		}

		mapParts.iconDrawer = new IconDrawer(mapParts.graph, new Random(), settings);
		Rectangle iconChangeBounds = mapParts.iconDrawer.addOrUpdateIconsFromEdits(settings.edits, centersChanged, this);
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

	private IntRectangle incrementalUpdateBounds(final MapSettings settings, MapParts mapParts, Image fullSizedMap, Rectangle replaceBounds,
			double effectsPadding, TextDrawer textDrawer, boolean onlyTextChanged)
	{
		// The bounds of the snippet to draw. This is larger than the snippet to
		// replace because ocean/land effects expand beyond the edges
		// that draw them, and we need those to be included in the snippet to
		// replace.
		Rectangle drawBounds = replaceBounds.pad(effectsPadding, effectsPadding).floor();

		IntRectangle boundsInSourceToCopyFrom = new IntRectangle((int) replaceBounds.x - (int) drawBounds.x,
				(int) replaceBounds.y - (int) drawBounds.y, (int) replaceBounds.width, (int) replaceBounds.height);
		Image mapSnippet;
		Image textBackground;
		double sizeMultiplierRounded = calcSizeMultipilerFromResolutionScaleRounded(settings.resolution);

		Set<Center> centersToDraw = null;
		if (!onlyTextChanged)
		{
			Center searchStart = mapParts.graph.findClosestCenter(drawBounds.getCenter());
			centersToDraw = mapParts.graph.breadthFirstSearch(c -> c.isInBoundsIncludingNoisyEdges(drawBounds), searchStart);

			checkForCancel();

			List<IconDrawTask> iconsToDraw = mapParts.iconDrawer.getTasksInDrawBoundsSortedAndScaled(drawBounds);
			mapParts.background.doSetupThatNeedsGraphAndIcons(settings, mapParts.graph, iconsToDraw, centersToDraw, drawBounds,
					replaceBounds);

			checkForCancel();

			// Draw mask for land vs ocean.
			Image landMask = Image.create((int) drawBounds.width, (int) drawBounds.height, ImageType.Binary);
			{
				Painter p = landMask.createPainter();
				mapParts.graph.drawLandAndOceanBlackAndWhite(p, centersToDraw, drawBounds);
			}

			checkForCancel();

			Image landTextureSnippet = ImageHelper.copySnippet(mapParts.background.land, drawBounds.toIntRectangle());
			mapSnippet = ImageHelper.maskWithColor(landTextureSnippet, Color.black, landMask, false);

			checkForCancel();

			Image coastShading;
			Image landBackgroundColoredBeforeAddingIconColorsWithShading = null;
			{
				Tuple2<Image, Image> tuple = darkenLandNearCoastlinesAndRegionBorders(settings, mapParts.graph, settings.resolution,
						mapSnippet, landMask, mapParts.background, null, centersToDraw, drawBounds, false);
				mapSnippet = tuple.getFirst();
				coastShading = tuple.getSecond();

				if (settings.drawRegionColors)
				{
					Image landColoredBeforeAddingIconColors = ImageHelper.copySnippet(mapParts.background.landColoredBeforeAddingIconColors,
							drawBounds.toIntRectangle());
					landBackgroundColoredBeforeAddingIconColorsWithShading = darkenLandNearCoastlinesAndRegionBorders(settings,
							mapParts.graph, settings.resolution, landColoredBeforeAddingIconColors, landMask, mapParts.background,
							coastShading, centersToDraw, drawBounds, false).getFirst();
				}

			}

			checkForCancel();

			// Store the current version of mapSnippet for a background when drawing icons later.
			Image landBackground = mapSnippet.deepCopy();

			if (settings.drawRegionBoundaries)
			{
				Painter p = mapSnippet.createPainter(DrawQuality.High);
				p.setColor(settings.regionBoundaryColor);
				mapParts.graph.drawRegionBoundaries(p, settings.regionBoundaryStyle, centersToDraw, drawBounds);
			}

			checkForCancel();

			Set<Edge> edgesToDraw = mapParts.graph.getEdgesFromCenters(centersToDraw);
			drawRivers(settings, mapParts.graph, mapSnippet, edgesToDraw, drawBounds);

			checkForCancel();

			// Draw ocean
			Image oceanTextureSnippet;
			{
				oceanTextureSnippet = mapParts.background.createOceanSnippet(drawBounds);
				mapSnippet = ImageHelper.maskWithImage(mapSnippet, oceanTextureSnippet, landMask);
			}

			checkForCancel();

			// Add shading and waves to ocean along coastlines
			Image oceanWaves;
			Image oceanShading;
			Image oceanWithWavesAndShading = oceanTextureSnippet;
			{
				Tuple2<Image, Image> oceanTuple = createOceanWavesAndShading(settings, mapParts.graph, settings.resolution, landMask,
						centersToDraw, drawBounds);
				oceanWaves = oceanTuple.getFirst();
				oceanShading = oceanTuple.getSecond();
				if (oceanShading != null)
				{
					mapSnippet = ImageHelper.maskWithColor(mapSnippet, settings.oceanShadingColor, oceanShading, true);
					oceanWithWavesAndShading = ImageHelper.maskWithColor(oceanWithWavesAndShading, settings.oceanShadingColor, oceanShading,
							true);
				}
				if (oceanWaves != null)
				{
					mapSnippet = ImageHelper.maskWithColor(mapSnippet, settings.oceanWavesColor, oceanWaves, true);
					oceanWithWavesAndShading = ImageHelper.maskWithColor(oceanWithWavesAndShading, settings.oceanWavesColor, oceanWaves,
							true);
				}
			}

			checkForCancel();

			// Draw coastlines.
			{
				Painter p = mapSnippet.createPainter(DrawQuality.High);
				p.setColor(settings.coastlineColor);
				mapParts.graph.drawCoastlineWithLakeShores(p, settings.coastlineWidth * settings.resolution, centersToDraw, drawBounds);
			}

			checkForCancel();

			// Draw roads
			if (settings.drawRoads)
			{
				RoadDrawer roadDrawer = new RoadDrawer(r, settings, mapParts.graph);
				roadDrawer.drawRoads(mapSnippet, drawBounds);
			}

			checkForCancel();

			// Draw icons
			mapParts.iconDrawer.drawIcons(iconsToDraw, mapSnippet, landBackgroundColoredBeforeAddingIconColorsWithShading, landBackground,
					landTextureSnippet, oceanWithWavesAndShading, drawBounds);

			textBackground = updateLandMaskAndCreateTextBackground(settings, mapParts.graph, landMask, iconsToDraw, landTextureSnippet,
					oceanTextureSnippet, mapParts.background, oceanWaves, oceanShading, coastShading, mapParts.iconDrawer, centersToDraw,
					drawBounds);

			checkForCancel();

			// Update the snippet in textBackground because the Fonts tab uses that as part of speeding up text re-drawing.
			ImageHelper.copySnippetFromSourceAndPasteIntoTarget(mapParts.textBackground, textBackground,
					replaceBounds.upperLeftCorner().toIntPoint(), boundsInSourceToCopyFrom, 0);

			// If present, also update the cached version of the map before adding text so that the Fonts tab can draw the map faster.
			if (mapParts.mapBeforeAddingText != null)
			{
				ImageHelper.copySnippetFromSourceAndPasteIntoTarget(mapParts.mapBeforeAddingText, mapSnippet,
						replaceBounds.upperLeftCorner().toIntPoint(), boundsInSourceToCopyFrom, 0);
			}
		}
		else
		{
			mapSnippet = ImageHelper.copySnippet(mapParts.mapBeforeAddingText, drawBounds.toIntRectangle());
			textBackground = ImageHelper.copySnippet(mapParts.textBackground, drawBounds.toIntRectangle());
		}

		if (settings.drawText)
		{
			textDrawer.drawTextFromEdits(mapSnippet, textBackground, mapParts.graph, drawBounds);
		}
		textDrawer.updateTextBoundsIfNeeded(mapParts.graph);

		IntPoint drawBoundsUpperLeftCornerAdjustedForBorder = new IntPoint(
				drawBounds.upperLeftCorner().toIntPoint().x + mapParts.background.getBorderPaddingScaledByResolution(),
				drawBounds.upperLeftCorner().toIntPoint().y + mapParts.background.getBorderPaddingScaledByResolution());

		mapParts.background.drawEdgesIfBoundsTouchesThem(mapSnippet, drawBounds);
		mapParts.background.drawInsetCornersIfBoundsTouchesThem(mapSnippet, drawBounds);

		// Add grunge
		if (settings.drawGrunge && settings.grungeWidth > 0)
		{
			mapSnippet = ImageHelper.maskWithColorInRegion(mapSnippet, settings.frayedBorderColor, mapParts.grunge, true,
					drawBoundsUpperLeftCornerAdjustedForBorder);
		}

		if (DebugFlags.drawCorners())
		{
			mapParts.graph.drawCorners(mapSnippet.createPainter(), centersToDraw, drawBounds);
		}
		if (DebugFlags.drawVoronoi())
		{
			mapParts.graph.drawVoronoi(mapSnippet.createPainter(), centersToDraw, drawBounds);
		}

		IntPoint replaceBoundsUpperLeftCornerAdjustedForBorder = new IntPoint(
				replaceBounds.upperLeftCorner().toIntPoint().x + mapParts.background.getBorderPaddingScaledByResolution(),
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
				mapSnippet = ImageHelper.maskWithColorInRegion(mapSnippet, settings.frayedBorderColor, mapParts.frayedBorderBlur, true,
						drawBoundsUpperLeftCornerAdjustedForBorder);
			}
			mapSnippet = ImageHelper.setAlphaFromMaskInRegion(mapSnippet, mapParts.frayedBorderMask, true,
					drawBoundsUpperLeftCornerAdjustedForBorder);
		}

		// Update the snippet in the main map.
		ImageHelper.copySnippetFromSourceAndPasteIntoTarget(fullSizedMap, mapSnippet, replaceBoundsUpperLeftCornerAdjustedForBorder,
				boundsInSourceToCopyFrom, mapParts.background.getBorderPaddingScaledByResolution());

		if (DebugFlags.showIncrementalUpdateBounds())
		{
			Painter p = fullSizedMap.createPainter();
			int scaledBorderWidth = settings.drawBorder && settings.borderPosition == BorderPosition.Outside_map
					? (int) (settings.borderWidth * settings.resolution)
					: 0;
			p.setBasicStroke(4f);
			p.setColor(Color.red);
			{
				IntRectangle rect = new Rectangle(replaceBounds.x + scaledBorderWidth, replaceBounds.y + scaledBorderWidth,
						replaceBounds.width, replaceBounds.height).toIntRectangle();
				p.drawRect(rect.x, rect.y, rect.width, rect.height);
			}
			p.setBasicStroke(4f);
			p.setColor(Color.white);
			{
				IntRectangle rect = new Rectangle(drawBounds.x + scaledBorderWidth, drawBounds.y + scaledBorderWidth, drawBounds.width,
						drawBounds.height).toIntRectangle();
				p.drawRect(rect.x, rect.y, rect.width, rect.height);
			}
		}

		return replaceBounds.toIntRectangle();
	}

	private double calcEffectsPadding(final MapSettings settings)
	{
		double sizeMultiplier = calcSizeMultipilerFromResolutionScaleRounded(settings.resolution);

		// To handle edge/effects changes outside centersChangedBounds box
		// caused by centers in centersChanged, pad the bounds of the
		// snippet to replace to include the width of ocean effects, land
		// effects, and with widest possible line that can be drawn,
		// whichever is largest.

		double concentricWaveWidth = settings.hasConcentricWaves()
				? settings.concentricWaveCount
						* (concentricWaveLineWidth * sizeMultiplier + concentricWaveWidthBetweenWaves * sizeMultiplier)
						+ (settings.jitterToConcentricWaves ? calcJitterVarianceRange(settings.resolution) : 0)
				: 0;
		// In theory I shouldn't multiply by 0.75 below, but realistically there doesn't seem to be any visual difference and it helps a lot
		// with performance.
		double rippleWaveWidth = settings.hasRippleWaves(settings.resolution) ? (settings.oceanWavesLevel * sizeMultiplier) * 0.75 : 0;
		// There shading from gaussian blur isn't visible all the way out, so save performance by reducing the width
		// contributed by it.
		double oceanShadingWidth = 0.9 * (settings.oceanShadingLevel * sizeMultiplier);
		double coastShadingWidth = 0.9 * (settings.coastShadingLevel * sizeMultiplier);

		double effectsPadding = Math
				.ceil(Math.max(concentricWaveWidth, Math.max(rippleWaveWidth, Math.max(oceanShadingWidth, coastShadingWidth))));

		// Make sure effectsPadding is at least half the width of the maximum with any line can be drawn, which would probably be a very
		// wide river. Since there is no easy way to know what that will be, just guess.
		double buffer = 10;
		effectsPadding = Math.max(effectsPadding,
				Math.max((buffer / 2.0) * settings.resolution, (SettingsGenerator.maxLineWidthInEditor / 2.0) * settings.resolution));

		return effectsPadding;
	}

	/**
	 * Draws a map.
	 * 
	 * @param settings
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

		// If we're within resolution buffer of our estimated maximum resolution, then be conservative about memory usage.
		// My tests showed that running frayed edge and grunge calculation inline with other stuff gave a 22% speedup.
		final double resolutionBuffer = 0.5;
		boolean isLowMemoryMode = settings.resolution >= calcMaxResolutionScale() - resolutionBuffer;
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

		r = new Random(settings.randomSeed);
		Dimension mapBounds = Background.calcMapBoundsAndAdjustResolutionIfNeeded(settings, maxDimensions);
		double sizeMultiplier = calcSizeMultipilerFromResolutionScale(settings.resolution);

		Image map;
		// Kick of a job to create the graph while the background is being created.
		Future<WorldGraph> graphTask = ThreadHelper.getInstance().submit(() ->
		{
			if (mapParts == null || mapParts.graph == null)
			{
				Logger.println("Creating the graph.");
				WorldGraph graphCreated = createGraph(settings, mapBounds.width, mapBounds.height, r, settings.resolution,
						!settings.edits.isInitialized());
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

		checkForCancel();

		WorldGraph graph;
		graph = ThreadHelper.getInstance().getResult(graphTask);

		checkForCancel();

		// Kick off frayed border creation. This is started after the graph is created because of previous bugs I've found
		// where VoronoiGraph was not thread safe. I think I've fixed those, but I'm still avoiding creating graphs in
		// parallel to be safe.
		Dimension mapDimensions = background.borderBounds;
		Future<Tuple2<Image, Image>> frayedBorderTask = null;
		if (!isLowMemoryMode)
		{
			frayedBorderTask = startFrayedBorderCreation(settings, mapDimensions, sizeMultiplier, mapParts);
		}

		Image textBackground;
		List<Set<Center>> mountainGroups;
		List<IconDrawTask> cities;
		if (mapParts == null || mapParts.mapBeforeAddingText == null || !settings.edits.isInitialized())
		{
			Tuple4<Image, Image, List<Set<Center>>, List<IconDrawTask>> tuple = drawTerrainAndIcons(settings, mapParts, graph, background);

			checkForCancel();

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
			background.landColoredBeforeAddingIconColors = null;
		}

		checkForCancel();

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

		// Create the NameCreator regardless of whether we're going to use it here because the text tools needs it to be in mapParts.
		Future<NameCreator> nameCreatorTask = null;
		if (mapParts == null || mapParts.nameCreator == null)
		{
			nameCreatorTask = startNameCreatorCreation(settings);
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

			// Generate text regardless off settings.drawText because
			// the editor might be generating the map without text
			// now, but want to show the text later, so in that case we would
			// want to generate the text but not show it.
			textDrawer.generateText(graph, map, nameCreator, textBackground, mountainGroups, cities, graph.getGeneratedLakes());
		}

		textBackground = null;

		if (DebugFlags.drawCorners())
		{
			graph.drawCorners(map.createPainter(), null, null);
		}
		if (DebugFlags.drawVoronoi())
		{
			graph.drawVoronoi(map.createPainter(), null, null);
		}

		if (DebugFlags.getIndexesOfCentersToHighlight().length > 0)
		{
			Painter p = map.createPainter();
			Set<Center> toRender = new HashSet<>();
			for (Integer index : DebugFlags.getIndexesOfCentersToHighlight())
			{
				toRender.add(graph.centers.get(index));
			}
			graph.drawPolygons(p, toRender, (_) -> Color.green);
		}

		if (DebugFlags.getIndexesOfEdgesToHighlight().length > 0)
		{
			Painter p = map.createPainter();
			for (Integer index : DebugFlags.getIndexesOfEdgesToHighlight())
			{
				Edge e = graph.edges.get(index);

				p.setColor(Color.blue);
				p.setBasicStroke(1f * (float) settings.resolution);
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

		if (settings.drawBorder)
		{
			Logger.println("Adding border.");
			map = background.addBorder(map);
			if (mapParts == null)
			{
				background.borderBackground = null;
			}
		}
		background = null;

		checkForCancel();

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
			map = ImageHelper.maskWithColor(map, settings.frayedBorderColor, grunge, true);
		}

		drawOverlayImageIfNeededAndUpdateMapParts(map, settings, mapParts);

		checkForCancel();

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
				mapParts.frayedBorderColor = settings.frayedBorderColor;
			}

			if (frayedBorderBlur != null)
			{
				map = ImageHelper.maskWithColor(map, settings.frayedBorderColor, frayedBorderBlur, true);
			}
			map = ImageHelper.setAlphaFromMask(map, frayedBorderMask, true);
		}


		if (nameCreatorTask != null)
		{
			NameCreator nameCreator = ThreadHelper.getInstance().getResult(nameCreatorTask);
			if (mapParts != null)
			{
				mapParts.nameCreator = nameCreator;
			}
		}

		checkForCancel();

		double elapsedTime = System.currentTimeMillis() - startTime;
		Logger.println("Total time to generate map (in seconds): " + elapsedTime / 1000.0);

		Logger.println("Done creating map.");

		return map;
	}

	private Future<Tuple2<Image, Image>> startFrayedBorderCreation(MapSettings settings, Dimension mapDimensions, double sizeMultiplier,
			MapParts mapParts)
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
				WorldGraph frayGraph = GraphCreator.createSimpleGraph(widthToUse, heightToUse, polygonCount,
						new Random(settings.frayedBorderSeed), settings.resolution, true, settings.rightRotationCount,
						settings.flipHorizontally, settings.flipVertically);
				frayedBorderMask = Image.create(frayGraph.getWidth(), frayGraph.getHeight(), ImageType.Grayscale8Bit);
				frayGraph.drawBorderWhite(frayedBorderMask.createPainter());
				if (blurLevel > 0)
				{
					float[][] kernel = ImageHelper.createGaussianKernel(blurLevel);
					frayedBorderBlur = ImageHelper.convolveGrayscale(frayedBorderMask, kernel, true, true);
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
				grunge = FractalBGGenerator.generate(new Random(settings.backgroundRandomSeed + 104567), fractalPower,
						((int) mapDimensions.width), ((int) mapDimensions.height), 0.75f);

				checkForCancel();

				// Whiten the middle of clouds.
				darkenMiddleOfImage(settings.resolution, grunge, settings.grungeWidth);

				return grunge;
			});
		}
		else
		{
			return null;
		}
	}

	private Tuple4<Image, Image, List<Set<Center>>, List<IconDrawTask>> drawTerrainAndIcons(MapSettings settings, MapParts mapParts,
			WorldGraph graph, Background background)
	{
		applyRegionEdits(graph, settings.edits);
		// Apply edge edits before center edits because applying center edits smoothes region boundaries, which depends on rivers, which are
		// edge edits.
		applyEdgeEdits(graph, settings.edits, null);
		applyCenterEdits(graph, settings.edits, null, settings.drawRegionBoundaries || settings.drawRegionColors);

		checkForCancel();

		IconDrawer iconDrawer;
		boolean needToAddIcons;
		iconDrawer = new IconDrawer(graph, new Random(r.nextLong()), settings);
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
			iconDrawer.addOrUpdateIconsFromEdits(settings.edits, graph.centers, this);
		}

		checkForCancel();

		List<IconDrawTask> iconsToDraw = iconDrawer.getTasksInDrawBoundsSortedAndScaled(null);
		background.doSetupThatNeedsGraphAndIcons(settings, graph, iconsToDraw, null, null, null);
		if (mapParts == null)
		{
			background.landBeforeRegionColoring = null;
		}

		checkForCancel();

		// Draw mask for land vs ocean.
		Logger.println("Adding land.");
		Image landMask = Image.create(graph.getWidth(), graph.getHeight(), ImageType.Binary);
		{
			Painter g = landMask.createPainter();
			graph.drawLandAndOceanBlackAndWhite(g, graph.centers, null);
		}

		// Combine land and ocean images.
		Image map = ImageHelper.maskWithColor(background.land, Color.black, landMask, false);

		Image coastShading;
		Image landColoredBeforeAddingIconColorsWithShading = null;
		{
			{
				Tuple2<Image, Image> tuple = darkenLandNearCoastlinesAndRegionBorders(settings, graph, settings.resolution, map, landMask,
						background, null, null, null, true);
				map = tuple.getFirst();
				coastShading = tuple.getSecond();
			}
			if (settings.drawRegionColors)
			{
				landColoredBeforeAddingIconColorsWithShading = darkenLandNearCoastlinesAndRegionBorders(settings, graph,
						settings.resolution, background.landColoredBeforeAddingIconColors, landMask, background, coastShading, null, null,
						true).getFirst();
			}
		}

		checkForCancel();

		// Store the current version of the map for a background when drawing icons later.
		Image landBackground = map.deepCopy();

		checkForCancel();

		if (settings.drawRegionBoundaries)
		{
			{
				Painter g = map.createPainter(DrawQuality.High);
				g.setColor(settings.regionBoundaryColor);
				graph.drawRegionBoundaries(g, settings.regionBoundaryStyle, null, null);
			}
		}

		checkForCancel();

		// Add rivers.
		Logger.println("Adding rivers.");
		drawRivers(settings, graph, map, null, null);

		checkForCancel();

		Logger.println("Drawing ocean.");
		{
			if (background.ocean.getWidth() != graph.getWidth() || background.ocean.getHeight() != graph.getHeight())
			{
				throw new IllegalArgumentException(
						"The given ocean background image does not" + " have the same aspect ratio as the given land background image.");
			}

			map = ImageHelper.maskWithImage(map, background.ocean, landMask);
		}

		checkForCancel();

		Tuple2<Image, Image> oceanTuple = createOceanWavesAndShading(settings, graph, settings.resolution, landMask, null, null);
		Image oceanWaves = oceanTuple.getFirst();
		Image oceanShading = oceanTuple.getSecond();
		Image oceanWithWavesAndShading = background.ocean;
		if (oceanShading != null)
		{
			Logger.println("Adding shading to ocean along coastlines.");
			map = ImageHelper.maskWithColor(map, settings.oceanShadingColor, oceanShading, true);
			oceanWithWavesAndShading = ImageHelper.maskWithColor(oceanWithWavesAndShading, settings.oceanShadingColor, oceanShading, true);
		}

		if (oceanWaves != null)
		{
			Logger.println("Adding waves to ocean along coastlines.");
			map = ImageHelper.maskWithColor(map, settings.oceanWavesColor, oceanWaves, true);
			oceanWithWavesAndShading = ImageHelper.maskWithColor(oceanWithWavesAndShading, settings.oceanWavesColor, oceanWaves, true);
		}

		checkForCancel();

		// Draw coastlines.
		{
			Painter p = map.createPainter(DrawQuality.High);
			p.setColor(settings.coastlineColor);
			graph.drawCoastlineWithLakeShores(p, settings.coastlineWidth * settings.resolution, null, null);
		}

		checkForCancel();

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

		checkForCancel();

		Logger.println("Drawing all icons.");
		iconDrawer.drawIcons(iconsToDraw, map, landColoredBeforeAddingIconColorsWithShading, landBackground, background.land,
				oceanWithWavesAndShading, null);
		landBackground = null;
		landColoredBeforeAddingIconColorsWithShading = null;

		checkForCancel();

		// Needed for drawing text
		Image textBackground = updateLandMaskAndCreateTextBackground(settings, graph, landMask, iconsToDraw,
				settings.drawRegionColors ? background.landColoredBeforeAddingIconColors : background.land, background.ocean, background,
				oceanWaves, oceanShading, coastShading, iconDrawer, null, null);

		if (mapParts != null)
		{
			mapParts.mapBeforeAddingText = map.deepCopy();
			mapParts.textBackground = textBackground;
		}

		if (mapParts == null)
		{
			background.land = null;
		}
		landMask = null;

		checkForCancel();

		return new Tuple4<>(map, textBackground, mountainGroups, cities);
	}

	private Image updateLandMaskAndCreateTextBackground(MapSettings settings, WorldGraph graph, Image landMask,
			List<IconDrawTask> iconsThatDrew, Image landTexture, Image oceanTexture, Background background, Image oceanWaves,
			Image oceanShading, Image coastShading, IconDrawer iconDrawer, Collection<Center> centersToDraw, Rectangle drawBounds)
	{
		iconDrawer.drawNondecorationContentMasksOntoLandMask(landMask, iconsThatDrew, drawBounds);

		Image textBackground = ImageHelper.maskWithColor(landTexture, Color.black, landMask, false);
		textBackground = darkenLandNearCoastlinesAndRegionBorders(settings, graph, settings.resolution, textBackground, landMask,
				background, coastShading, centersToDraw, drawBounds, false).getFirst();
		textBackground = ImageHelper.maskWithImage(textBackground, oceanTexture, landMask);
		if (oceanShading != null)
		{
			textBackground = ImageHelper.maskWithColor(textBackground, settings.oceanShadingColor, oceanShading, true);
		}
		if (oceanWaves != null)
		{
			textBackground = ImageHelper.maskWithColor(textBackground, settings.oceanWavesColor, oceanWaves, true);
		}
		return textBackground;
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
	private Tuple2<Image, Image> darkenLandNearCoastlinesAndRegionBorders(MapSettings settings, WorldGraph graph, double resolutionScaled,
			Image mapOrSnippet, Image landMask, Background background, Image coastShading, Collection<Center> centersToDraw,
			Rectangle drawBounds, boolean addLoggingEntry)
	{
		double sizeMultiplier = calcSizeMultipilerFromResolutionScale(resolutionScaled);
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
				scale = scaleForDarkening
						* calcScaleToMakeConvolutionEffectsLightnessInvariantToKernelSize(settings.coastShadingLevel, sizeMultiplier)
						* calcScaleCompensateForCoastlineShadingDrawingAtAFullPixelWideAtLowerResolutions(targetStrokeWidth);
			}

			// coastShading can be passed in to save time when calling this method a second time for the text background image.
			if (coastShading == null)
			{
				float[][] kernel = ImageHelper.createGaussianKernel(blurLevel);

				Image coastlineAndLakeShoreMask = Image.create(mapOrSnippet.getWidth(), mapOrSnippet.getHeight(), ImageType.Binary);
				Painter p = coastlineAndLakeShoreMask.createPainter(DrawQuality.High);
				p.setColor(Color.white);
				graph.drawCoastlineWithLakeShores(p, targetStrokeWidth, centersToDraw, drawBounds);

				if (settings.drawRegionBoundaries)
				{
					p.setColor(Color.white);
					graph.drawRegionBoundariesSolid(p, sizeMultiplier, false, centersToDraw, drawBounds);
					coastShading = ImageHelper.convolveGrayscaleThenScale(coastlineAndLakeShoreMask, kernel, scale, true);

				}
				else
				{
					coastShading = ImageHelper.convolveGrayscaleThenScale(coastlineAndLakeShoreMask, kernel, scale, true);
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
						Color color = Color.create((int) (reg.backgroundColor.getRed() * regionBlurColorScale),
								(int) (reg.backgroundColor.getGreen() * regionBlurColorScale),
								(int) (reg.backgroundColor.getBlue() * regionBlurColorScale));
						colors.put(reg.id, color);
					}
				}
				else
				{
					colors.put(0, settings.landColor);
				}
				return new Tuple2<>(ImageHelper.maskWithMultipleColors(mapOrSnippet, colors, background.regionIndexes, coastShading, true),
						coastShading);
			}
			else
			{
				return new Tuple2<>(ImageHelper.maskWithColor(mapOrSnippet, settings.coastShadingColor, coastShading, true), coastShading);
			}
		}
		return new Tuple2<>(mapOrSnippet, null);
	}

	private Tuple2<Image, Image> createOceanWavesAndShading(MapSettings settings, WorldGraph graph, double resolutionScale, Image landMask,
			Collection<Center> centersToDraw, Rectangle drawBounds)
	{
		if (drawBounds == null)
		{
			drawBounds = graph.bounds;
		}
		double sizeMultiplier = calcSizeMultipilerFromResolutionScaleRounded(resolutionScale);

		Image oceanWaves = null;
		Image oceanShading = null;
		if (settings.hasRippleWaves(resolutionScale) || settings.hasConcentricWaves() || settings.hasOceanShading(resolutionScale))
		{
			double targetStrokeWidth = sizeMultiplier;

			if (settings.hasRippleWaves(resolutionScale))
			{
				Image coastlineMask = createCoastlineMask(settings, graph, targetStrokeWidth, false, 0, centersToDraw, drawBounds);
				float[][] kernel = ImageHelper.createPositiveSincKernel((int) (settings.oceanWavesLevel * sizeMultiplier),
						1.0 / sizeMultiplier);

				final float scaleForDarkening = coastlineShadingScale;
				float scale = scaleForDarkening
						* calcScaleToMakeConvolutionEffectsLightnessInvariantToKernelSize(settings.oceanWavesLevel, sizeMultiplier)
						* calcScaleCompensateForCoastlineShadingDrawingAtAFullPixelWideAtLowerResolutions(targetStrokeWidth);
				oceanWaves = ImageHelper.convolveGrayscaleThenScale(coastlineMask, kernel, scale, true);
				if (settings.drawOceanEffectsInLakes)
				{
					oceanWaves = removeOceanEffectsFromLand(graph, oceanWaves, landMask, centersToDraw, drawBounds);
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
				Image coastlineMask = createCoastlineMask(settings, graph, targetStrokeWidth, false, 0, centersToDraw, drawBounds);
				float[][] kernel = ImageHelper.createGaussianKernel((int) (settings.oceanShadingLevel * sizeMultiplier));

				final float scaleForDarkening = coastlineShadingScale;
				float scale = scaleForDarkening
						* calcScaleToMakeConvolutionEffectsLightnessInvariantToKernelSize(settings.oceanShadingLevel, sizeMultiplier)
						* calcScaleCompensateForCoastlineShadingDrawingAtAFullPixelWideAtLowerResolutions(targetStrokeWidth);
				oceanShading = ImageHelper.convolveGrayscaleThenScale(coastlineMask, kernel, scale, true);
				if (settings.drawOceanEffectsInLakes)
				{
					oceanShading = removeOceanEffectsFromLand(graph, oceanShading, landMask, centersToDraw, drawBounds);
				}
				else
				{
					oceanShading = removeOceanEffectsFromLandAndLandLockedLakes(graph, oceanShading, centersToDraw, drawBounds);
				}
			}
		}
		return new Tuple2<>(oceanWaves, oceanShading);
	}

	private Image createCoastlineMask(MapSettings settings, WorldGraph graph, double targetStrokeWidth,
			boolean forceUseCurvesWithinThreshold, double widthBetweenWaves, Collection<Center> centersToDraw, Rectangle drawBounds)
	{
		Image coastlineMask = Image.create((int) drawBounds.width, (int) drawBounds.height, ImageType.Binary);
		Painter g = coastlineMask.createPainter();
		g.setColor(Color.white);


		if (settings.drawOceanEffectsInLakes)
		{
			graph.drawCoastlineWithLakeShores(g, targetStrokeWidth, centersToDraw, drawBounds);
		}
		else
		{
			graph.drawCoastline(g, targetStrokeWidth, centersToDraw, drawBounds);
		}

		return coastlineMask;
	}

	private Image createConcentricWavesMask(MapSettings settings, WorldGraph graph, double resolutionScaled, Image landMask,
			Collection<Center> centersToDraw, Rectangle drawBounds)
	{
		Image oceanEffects = Image.create((int) drawBounds.width, (int) drawBounds.height, ImageType.Grayscale8Bit);
		double sizeMultiplier = calcSizeMultipilerFromResolutionScaleRounded(resolutionScaled);

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

		List<List<Edge>> shoreEdges = graph.findShoreEdges(centersToDraw, settings.drawOceanEffectsInLakes,
				settings.brokenLinesForConcentricWaves);
		Painter p = oceanEffects.createPainter(DrawQuality.High);
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
				double scaleToMakeFartherOutWavesShorter = ((((((double) SettingsGenerator.maxConcentricWaveCountInEditor - 1)
						- (waveNumber - 1))) / ((double) SettingsGenerator.maxConcentricWaveCountInEditor - 1)));
				final double scaleAtLastWave = 0.5;
				scaleToMakeFartherOutWavesShorter = 1.0 - ((1.0 - scaleToMakeFartherOutWavesShorter) * (1.0 - scaleAtLastWave));

				final double scaleForAll = 4 * settings.resolution * scaleToMakeFartherOutWavesShorter;
				final double maxNotDrawLength = 3 * scaleForAll;
				final double minNotDrawLength = 2 * scaleForAll;
				final double maxDrawLength = 24 * scaleForAll;
				final double minDrawLength = 19 * scaleForAll;
				return isDrawing ? rand.nextDouble(minDrawLength, maxDrawLength + 1)
						: rand.nextDouble(minNotDrawLength, maxNotDrawLength + 1);
			};


			int level = (int) (oceanEffects.getMaxPixelLevel() * waveOpacity);
			p.setColor(Color.create(level, level, level));
			double varianceRange = settings.jitterToConcentricWaves ? calcJitterVarianceRange(resolutionScaled) : 0.0;
			p.setStrokeToSolidLineWithNoEndDecorations((float) whiteWidth);
			graph.drawCoastlineWithVariation(p, settings.backgroundRandomSeed + i, varianceRange, widthBetweenWaves,
					settings.brokenLinesForConcentricWaves, centersToDraw, drawBounds, getNewSkipDistance, shoreEdges);

			p.setColor(Color.black);
			p.setBasicStroke((float) (whiteWidth - waveWidth));
			graph.drawCoastlineWithVariation(p, settings.backgroundRandomSeed + i, varianceRange, widthBetweenWaves, false, centersToDraw,
					drawBounds, getNewSkipDistance, shoreEdges);
		}

		if (settings.drawOceanEffectsInLakes)
		{
			oceanEffects = removeOceanEffectsFromLand(graph, oceanEffects, landMask, centersToDraw, drawBounds);
		}
		else
		{
			oceanEffects = removeOceanEffectsFromLandAndLandLockedLakes(graph, oceanEffects, centersToDraw, drawBounds);
		}

		return oceanEffects;
	}

	private double calcJitterVarianceRange(double resolutionScaled)
	{
		double sizeMultiplier = calcSizeMultipilerFromResolutionScaleRounded(resolutionScaled);
		double widthBetweenWaves = concentricWaveWidthBetweenWaves * sizeMultiplier;
		return 0.25 * widthBetweenWaves;
	}

	private static float calcScaleToMakeConvolutionEffectsLightnessInvariantToKernelSize(int kernelSize, double sizeMultiplier)
	{
		int lightnessBasedOnKernelSizesBeforeIAddedFixToMakeShadingNotGetLighterWhenItGotWider = (int) (15 * sizeMultiplier);
		return ImageHelper.getGuassianMode(lightnessBasedOnKernelSizesBeforeIAddedFixToMakeShadingNotGetLighterWhenItGotWider)
				/ ImageHelper.getGuassianMode((int) (kernelSize * sizeMultiplier));
	}

	private static Image removeOceanEffectsFromLandAndLandLockedLakes(WorldGraph graph, Image oceanEffects,
			Collection<Center> centersToDraw, Rectangle drawBounds)
	{
		// One might wonder why I'm creating a mask to black out lakes and land, when in theory I could just draw them as black into
		// oceanEffects to save CPU and memory. The reason is because the Voronoi graph has a weakness that it doesn't contain edges or
		// noisy edges for centers along the border (the edge of the map). Because of this, I need to draw border centers first, then draw
		// centers with noisy edges over them. Thus I must draw both the land and lakes, and their ocean neighbors, so I need to do the
		// drawing as a mask and then apply it onto oceanEffects.
		Image landAndLakeMask = Image.create(oceanEffects.getWidth(), oceanEffects.getHeight(), ImageType.Grayscale8Bit);
		graph.drawLandAndLakesBlackAndOceanWhite(landAndLakeMask.createPainter(), centersToDraw, drawBounds);
		return ImageHelper.maskWithColor(oceanEffects, Color.black, landAndLakeMask, false);
	}

	private static Image removeOceanEffectsFromLand(WorldGraph graph, Image oceanEffects, Image landMask, Collection<Center> centersToDraw,
			Rectangle drawBounds)
	{
		return ImageHelper.maskWithColor(oceanEffects, Color.black, landMask, true);
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
			regionColorOptions
					.add(generateRegionColor(rand, landHsb, settings.hueRange, settings.saturationRange, settings.brightnessRange));
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
		float saturation = ImageHelper.bound((int) (landHsb[1] * 255 + (rand.nextDouble() - 0.5) * saturationRange));
		float brightness = ImageHelper.bound((int) (landHsb[2] * 255 + (rand.nextDouble() - 0.5) * brightnessRange));
		return Color.createFromHSB(hue / 360f, saturation / 255f, brightness / 255f);
	}

	public static Color generateColorFromBaseColor(Random rand, Color base, float hueRange, float saturationRange, float brightnessRange)
	{
		float[] hsb = base.getHSB();
		return generateRegionColor(rand, hsb, hueRange, saturationRange, brightnessRange);
	}

	private static WorldGraph createGraph(MapSettings settings, double width, double height, Random r, double resolutionScale,
			boolean createElevationBiomesLakesAndRegions)
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

		WorldGraph graph = GraphCreator.createGraph(widthToUse, heightToUse, settings.worldSize, settings.edgeLandToWaterProbability,
				settings.centerLandToWaterProbability, new Random(r.nextLong()), resolutionScale, settings.lineStyle,
				settings.pointPrecision, createElevationBiomesLakesAndRegions, settings.lloydRelaxationsScale,
				settings.drawRegionBoundaries || settings.drawRegionColors, settings.rightRotationCount, settings.flipHorizontally,
				settings.flipVertically);

		// Setup region colors even if settings.drawRegionColors = false because
		// edits need them in case someone edits a map without region colors,
		// then later enables region colors.
		assignRandomRegionColors(graph, settings);

		return graph;
	}

	/*
	 * A constant based on the resolution for determining how large things should draw.
	 */
	public static double calcSizeMultipilerFromResolutionScale(double resoutionScale)
	{
		return (8.0 / 3.0) * resoutionScale;
	}

	/**
	 * Like calcSizeMultipilerFromResolutionScale, but rounds to the nearest tenth for use with components that have that limit on numeric
	 * precision.
	 */
	public static double calcSizeMultipilerFromResolutionScaleRounded(double resolutionScale)
	{
		return Math.round(10.0 * calcSizeMultipilerFromResolutionScale(resolutionScale)) / 10.0;
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
	private static Set<Center> applyCenterEdits(WorldGraph graph, MapEdits edits, Collection<CenterEdit> centerEditChanges,
			boolean areRegionBoundariesVisible)
	{
		if (edits == null || edits.centerEdits.isEmpty())
		{
			return Collections.emptySet();
		}

		if (edits.centerEdits.size() != graph.centers.size())
		{
			throw new IllegalArgumentException(
					"The map edits have " + edits.centerEdits.size() + " polygons, but the world size is " + graph.centers.size());
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

		needsRebuildNoisyEdges.addAll(graph.smoothCoastlinesAndRegionBoundariesIfNeeded(centersChanged, graph.noisyEdges.getLineStyle(),
				areRegionBoundariesVisible));

		for (Center center : needsRebuildNoisyEdges)
		{
			graph.rebuildNoisyEdgesForCenter(center, needsRebuildNoisyEdges);
		}

		return needsRebuildNoisyEdges;
	}

	private static void applyEdgeEdits(WorldGraph graph, MapEdits edits, Collection<EdgeEdit> edgeChanges)
	{
		if (edits == null || edits.edgeEdits.isEmpty())
		{
			return;
		}
		if (edits.edgeEdits.size() != graph.edges.size())
		{
			throw new IllegalArgumentException(
					"The map edits have " + edits.edgeEdits.size() + " edges, but graph has " + graph.edges.size() + " edges.");
		}

		if (edgeChanges == null)
		{
			edgeChanges = edits.edgeEdits;
		}

		for (EdgeEdit eEdit : edgeChanges)
		{
			Edge edge = graph.edges.get(eEdit.index);
			boolean needsRebuild = false;
			if (eEdit.riverLevel != edge.river && edge.d0 != null)
			{
				needsRebuild = true;
			}
			graph.edges.get(eEdit.index).river = eEdit.riverLevel;
			if (needsRebuild)
			{
				graph.rebuildNoisyEdgesForCenter(edge.d0);
			}
		}
	}

	/**
	 * Makes the middle area of a gray scale image darker following a Gaussian blur drop off.
	 */
	private void darkenMiddleOfImage(double resolutionScale, Image image, int grungeWidth)
	{
		// Draw a white box.

		int blurLevel = (int) (grungeWidth * resolutionScale);
		if (blurLevel == 0)
			blurLevel = 1; // Avoid an exception later.

		// Create a white non-filled in rectangle, then blur it. To be much more
		// efficient, I only create
		// the upper left corner plus 1 pixel in both directions since the
		// corners and edges are all the
		// same except rotated and the edges are all the same except their
		// length.

		int lineWidth = (int) (resolutionScale);
		if (lineWidth == 0)
		{
			lineWidth = 1;
		}
		int blurBoxWidth = blurLevel * 2 + lineWidth + 1;
		Image blurBox = Image.create(blurBoxWidth, blurBoxWidth, ImageType.Binary);
		Painter p = blurBox.createPainter();

		// Fill the image with white.
		p.setColor(Color.white);
		p.fillRect(0, 0, blurBoxWidth, blurBoxWidth);

		// Remove the white from everywhere except a lineWidth wide line along
		// the top and left sides.
		p.setColor(Color.black);
		p.fillRect(lineWidth, lineWidth, blurBoxWidth, blurBoxWidth);

		// Use Gaussian blur on the box.
		blurBox = ImageHelper.convolveGrayscale(blurBox, ImageHelper.createGaussianKernel(blurLevel), true, true);

		// Remove what was the white lines from the top and left, so we're
		// keeping only the blur that came off the white lines.
		blurBox = ImageHelper.copySnippet(blurBox, new IntRectangle(lineWidth, lineWidth, blurLevel + 1, blurLevel + 1));

		// Multiply the image by blurBox. Also remove the padded edges off of blurBox.
		assert image.getType() == ImageType.Grayscale8Bit;
		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
			{
				float imageLevel = image.getNormalizedPixelLevel(x, y);

				// Retrieve the blur level as though blurBox has all 4 quadrants
				// and middle created, even though it only only has the upper
				// left corner + 1 pixel.
				int blurBoxX1;
				if (x > blurLevel)
				{
					if (x < image.getWidth() - blurLevel)
					{
						// x is between the corners.
						blurBoxX1 = blurBox.getWidth() - 1;
					}
					else
					{
						// x is under the right corner.
						blurBoxX1 = image.getWidth() - x;
					}
				}
				else
				{
					// x is under the left corner.
					blurBoxX1 = x;
				}

				int blurBoxX2;
				int x2 = image.getWidth() - x - 1;
				if (x2 > blurLevel)
				{
					if (x2 < image.getWidth() - blurLevel)
					{
						// x2 is between the corners.
						blurBoxX2 = blurBox.getWidth() - 1;
					}
					else
					{
						// x2 is under the right corner.
						blurBoxX2 = image.getWidth() - x2;
					}
				}
				else
				{
					// x2 is under the left corner.
					blurBoxX2 = x2;
				}

				int blurBoxY1;
				if (y > blurLevel)
				{
					if (y < image.getHeight() - blurLevel)
					{
						blurBoxY1 = blurBox.getHeight() - 1;
					}
					else
					{
						blurBoxY1 = image.getHeight() - y;
					}
				}
				else
				{
					blurBoxY1 = y;
				}

				int blurBoxY2;
				int y2 = image.getHeight() - y - 1;
				if (y2 > blurLevel)
				{
					if (y2 < image.getHeight() - blurLevel)
					{
						blurBoxY2 = blurBox.getHeight() - 1;
					}
					else
					{
						blurBoxY2 = image.getHeight() - y2;
					}
				}
				else
				{
					blurBoxY2 = y2;
				}

				float blurBoxLevel = Math.max(blurBox.getGrayLevel(blurBoxX1, blurBoxY1),
						Math.max(blurBox.getGrayLevel(blurBoxX2, blurBoxY1),
								Math.max(blurBox.getGrayLevel(blurBoxX1, blurBoxY2), blurBox.getGrayLevel(blurBoxX2, blurBoxY2))));

				image.setGrayLevel(x, y, (int) (imageLevel * blurBoxLevel));
			}
	}

	public static void drawRivers(MapSettings settings, WorldGraph graph, Image map, Collection<Edge> edgesToDraw, Rectangle drawBounds)
	{
		Painter p = map.createPainter(DrawQuality.High);
		graph.drawRivers(p, edgesToDraw, drawBounds, settings.riverColor, settings.drawRegionBoundaries, settings.regionBoundaryColor);
	}

	public Image createHeightMap(MapSettings settings)
	{
		r = new Random(settings.randomSeed);
		Dimension mapBounds;
		if (settings.rightRotationCount == 1 || settings.rightRotationCount == 3)
		{
			mapBounds = new Dimension(settings.generatedHeight * settings.heightmapResolution,
					settings.generatedWidth * settings.heightmapResolution);
		}
		else
		{
			mapBounds = new Dimension(settings.generatedWidth * settings.heightmapResolution,
					settings.generatedHeight * settings.heightmapResolution);
		}
		WorldGraph graph = createGraph(settings, mapBounds.width, mapBounds.height, r, settings.resolution, true);
		return GraphCreator.createHeightMap(graph, new Random(settings.randomSeed));
	}

	private List<CenterEdit> getCenterEditsForCenters(MapEdits edits, Collection<Center> centers)
	{
		return centers.stream().map(center -> edits.centerEdits.get(center.index)).collect(Collectors.toList());
	}

	private Set<EdgeEdit> getEdgeEditsForEdgeIds(MapEdits edits, Collection<Integer> edgeIds)
	{
		return edgeIds.stream().map(id -> edits.edgeEdits.get(id)).collect(Collectors.toSet());
	}

	private Set<EdgeEdit> getEdgeEditsForCenters(MapEdits edits, Collection<Center> centers)
	{
		Set<EdgeEdit> edgeEdits = new HashSet<>();
		for (Center center : centers)
		{
			for (Edge edge : center.borders)
			{
				EdgeEdit edgeEdit = edits.edgeEdits.get(edge.index);
				if (edgeEdit != null)
				{
					edgeEdits.add(edgeEdit);
				}

			}
		}
		return edgeEdits;
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
		int nextPowerOf2 = ImageHelper.getPowerOf2EqualOrLargerThan(maxResolution / 100.0);
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

	private static void drawOverlayImageIfNeededAndUpdateMapParts(Image map, MapSettings settings, MapParts mapParts)
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
		Tuple2<IntRectangle, Image> tuple = getOverlayPositionAndImage(settings.overlayImagePath, settings.overlayScale,
				settings.overlayOffsetResolutionInvariant, settings.resolution, mapSize);
		if (tuple == null)
		{
			return;
		}

		IntRectangle overlayPosition = tuple.getFirst();
		Image overlayImage = tuple.getSecond();

		int borderWidthScaledByResolution = Background.calcBorderWidthScaledByResolution(settings);

		Painter p = mapOrSnippet.createPainter(DrawQuality.High);
		try
		{
			int x = overlayPosition.x;
			int y = overlayPosition.y;
			if (drawBounds != null)
			{
				IntRectangle drawBoundsAdjustedForBorder = new IntRectangle(
						drawBounds.upperLeftCorner().toIntPoint().x + borderWidthScaledByResolution,
						drawBounds.upperLeftCorner().toIntPoint().y + borderWidthScaledByResolution, (int) drawBounds.width,
						(int) drawBounds.height);

				x -= drawBoundsAdjustedForBorder.x;
				y -= drawBoundsAdjustedForBorder.y;
			}

			// Set the transparency level
			float alpha = (100 - settings.overlayImageTransparency) / 100.0f;
			p.setAlphaComposite(AlphaComposite.SrcAtop, alpha);

			p.drawImage(overlayImage, x, y, overlayPosition.width, overlayPosition.height);
		}
		finally
		{
			p.dispose();
		}
	}

	public static Tuple2<IntRectangle, Image> getOverlayPositionAndImage(String overlayImagePath, double overlayScale,
			Point overlayOffsetResolutionInvariant, double resolutionScale, IntDimension mapSize)
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
