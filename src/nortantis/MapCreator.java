package nortantis;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import nortantis.MapSettings.OceanEffect;
import nortantis.editor.CenterEdit;
import nortantis.editor.EdgeEdit;
import nortantis.editor.FreeIcon;
import nortantis.editor.MapParts;
import nortantis.editor.RegionEdit;
import nortantis.geom.Dimension;
import nortantis.geom.IntDimension;
import nortantis.geom.IntPoint;
import nortantis.geom.IntRectangle;
import nortantis.geom.Rectangle;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Edge;
import nortantis.platform.Color;
import nortantis.platform.DrawQuality;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.Painter;
import nortantis.swing.MapEdits;
import nortantis.util.AssetsPath;
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
	// This is a base width for determining how large to draw text and effects.
	private static final double baseResolution = 1536;

	private static final int concentricWaveWidthBetweenWaves = 10;
	private static final int concentricWaveLineWidth = 2;
	private boolean isCanceled;
	private List<String> warningMessages;

	public MapCreator()
	{
		warningMessages = new ArrayList<>();
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

		return incrementalUpdateMultipleBounds(settings, mapParts, fullSizeMap, changeBounds);
	}

	// TODO Call this from IconTool
	public IntRectangle incrementalUpdateIcons(final MapSettings settings, MapParts mapParts, Image fullSizeMap,
			List<FreeIcon> iconsChanged)
	{
		IconDrawer iconDrawer = new IconDrawer(mapParts.graph, new Random(0), settings);

		List<Rectangle> changeBounds = new ArrayList<>();
		for (FreeIcon icon : iconsChanged)
		{
			IconDrawTask task = iconDrawer.toIconDrawTask(icon);
			Rectangle change = task.createBounds();
			if (change == null)
			{
				continue;
			}
			changeBounds.add(change);
		}

		return incrementalUpdateMultipleBounds(settings, mapParts, fullSizeMap, changeBounds);
	}

	private IntRectangle incrementalUpdateMultipleBounds(final MapSettings settings, MapParts mapParts, Image fullSizeMap,
			List<Rectangle> changeBounds)
	{
		TextDrawer textDrawer = new TextDrawer(settings);
		textDrawer.setMapTexts(settings.edits.text);

		IntRectangle bounds = null;
		for (Rectangle change : changeBounds)
		{
			if (change == null)
			{
				continue;
			}

			// TODO If replacing icons is too slow, I could change incrementalUpdate to take the bounds to draw rather than pull in the
			// centers touching those bounds. If I do, make sure text background blur still works too.
			Set<Center> centersInBounds = mapParts.graph.getCentersInBounds(change);

			IntRectangle updateBounds = incrementalUpdate(settings, mapParts, fullSizeMap, centersInBounds, null);
			if (bounds == null)
			{
				bounds = updateBounds;
			}
			else if (updateBounds != null)
			{
				bounds = bounds.add(updateBounds);
			}
		}

		return bounds;
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
	 */
	public IntRectangle incrementalUpdate(final MapSettings settings, MapParts mapParts, Image fullSizedMap, Set<Center> centersChanged,
			Set<Edge> edgesChanged)
	{
		// Stopwatch updateSW = new Stopwatch("incremental update");

		if (centersChanged == null)
		{
			centersChanged = new HashSet<>();
		}
		else
		{
			centersChanged = new HashSet<>(centersChanged);
		}


		// If any of the centers changed are are touching a lake, add the lake too since adding the change could have
		// changed whether the lake is land-locked, which will change how it's drawn.
		Set<Center> neighboringLakes = mapParts.graph.getNeighboringLakes(centersChanged);
		if (!neighboringLakes.isEmpty())
		{
			centersChanged.addAll(neighboringLakes);
		}

		if (edgesChanged != null)
		{
			centersChanged.addAll(getCentersFromEdges(mapParts.graph, edgesChanged));
		}
		Rectangle centersChangedBounds = WorldGraph.getBoundingBox(centersChanged);

		if (centersChangedBounds == null)
		{
			// Nothing changed
			return null;
		}

		double sizeMultiplier = calcSizeMultipilerFromResolutionScale(settings.resolution);

		// To handle edge/effects changes outside centersChangedBounds box
		// caused by centers in centersChanged, pad the bounds of the
		// snippet to replace to include the width of ocean effects, land
		// effects, and with widest possible line that can be drawn,
		// whichever is largest.
		double effectsPadding = Math.ceil(
				Math.max((settings.oceanEffect == OceanEffect.ConcentricWaves || settings.oceanEffect == OceanEffect.FadingConcentricWaves)
						? settings.concentricWaveCount * (concentricWaveLineWidth + concentricWaveWidthBetweenWaves)
						: settings.oceanEffectsLevel, settings.coastShadingLevel));
		// Increase effectsPadding by the width of a coastline, plus one pixel
		// extra just to be safe.
		effectsPadding += 2;

		// Make sure effectsPadding is at least half the width of the maximum
		// with any line can be drawn, which would probably be a very wide
		// river.
		// Since there is no easy way to know what that will be, just guess.
		effectsPadding = Math.max(effectsPadding, 8);

		effectsPadding *= sizeMultiplier;
		// The bounds to replace in the original map.
		Rectangle replaceBounds = centersChangedBounds.pad(effectsPadding, effectsPadding);
		// Expand snippetToReplaceBounds to include all icons the centers in
		// centersChanged drew the last time they were drawn.
		{
			Rectangle iconBounds = mapParts.iconDrawer.getBoundingBoxOfIconsAnchoredToCenters(centersChanged);
			if (iconBounds != null)
			{
				replaceBounds = replaceBounds.add(iconBounds);
			}
		}

		applyRegionEdits(mapParts.graph, settings.edits);
		// Apply edge edits before center edits because applying center edits smoothes region boundaries, which depends on rivers, which are
		// edge edits.
		{
			Set<EdgeEdit> edgeEdits;
			if (edgesChanged != null)
			{
				edgeEdits = getEdgeEditsForEdges(settings.edits, edgesChanged);
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
		applyCenterEdits(mapParts.graph, settings.edits, getCenterEditsForCenters(settings.edits, centersChanged),
				settings.drawRegionColors);


		mapParts.graph.updateCenterLookupTable(centersChanged);

		{
			// The reason I'm using centersInBounds below instead of centersChanged is a bit complicated, but I'll try to explain it.
			// Imagine this case:
			// 1) In the land water tool, you click the mouse down to make a polygon ocean.
			// 2) When the mouse clicks down, the tool changes the polygon to ocean and kicks off a background job to redraw the map.
			// 3) The mouse clicks up, causing the land water tool to set an undo point.
			// 4) The background job to redraw the map finishes, which causes a city next to the polygon to disappear because it now
			// overlaps ocean in a way to is disallowed.
			// 5) Now you undo that change. The city image fails to come back. Refreshing the entire map will cause it to reappear.
			// The reason this is happening is because the undue point was set before the drawing code removed the image, so the
			// edits both before and after the undue pointing still have the city image. Thus the polygon with that city, which was changed,
			// would not be included in centersChanged. But since MapParts.iconDrawer does have the change, the city image won't draw.
			//
			//
			// My somewhat hacky solution is to expand the icons to update and redraw to those in the current version of replace bounds.
			// This is unfortunately not guaranteed to work and has a small performance hit, but it's the best solution I've thought of.
			//
			// I made a fix in Undoer.undo to always use the latest settings, which makes the above scenario very unlikely, but it can still
			// happen if you really mash the undo button while drawing ocean, so I'm leaving this hack here for now.
			Rectangle bounds = replaceBounds;
			Set<Center> centersInBounds = mapParts.graph.breadthFirstSearch(c -> c.isInBounds(bounds), centersChanged.iterator().next());
			mapParts.iconDrawer.addOrUpdateIconsFromEdits(settings.edits, centersInBounds, this);
		}


		// Now that we've updated icons to draw in centersInBounds, check if we
		// need to expand replaceBounds to include those icons.
		{
			Rectangle updatedIconBounds = mapParts.iconDrawer.getBoundingBoxOfIconsAnchoredToCenters(centersChanged);
			if (updatedIconBounds != null)
			{
				replaceBounds = replaceBounds.add(updatedIconBounds);
			}
		}

		TextDrawer textDrawer = new TextDrawer(settings);
		textDrawer.setMapTexts(settings.edits.text);

		// Expand the replace bounds to include text that touches the centers that changed because that text could switch from one line to
		// two or vice versa.
		Rectangle textChangeBounds = textDrawer.expandBoundsToIncludeText(settings.edits.text, mapParts.graph, centersChangedBounds,
				settings);
		replaceBounds = replaceBounds.add(textChangeBounds).floor();

		// The bounds of the snippet to draw. This is larger than the snippet to
		// replace because ocean/land effects expand beyond the edges
		// that draw them, and we need those to be included in the snippet to
		// replace.
		Rectangle drawBounds = replaceBounds.pad(effectsPadding, effectsPadding).floor();


		Set<Center> centersToDraw = mapParts.graph.breadthFirstSearch(c -> c.isInBounds(drawBounds), centersChanged.iterator().next());

		mapParts.background.doSetupThatNeedsGraph(settings, mapParts.graph, centersToDraw, drawBounds, replaceBounds);

		// Draw mask for land vs ocean.
		Image landMask = Image.create((int) drawBounds.width, (int) drawBounds.height, ImageType.Binary);
		{
			Painter p = landMask.createPainter();
			mapParts.graph.drawLandAndOceanBlackAndWhite(p, centersToDraw, drawBounds);
		}

		Image landTextureSnippet = ImageHelper.copySnippet(mapParts.background.land, drawBounds.toIntRectangle());
		Image mapSnippet = ImageHelper.maskWithColor(landTextureSnippet, Color.black, landMask, false);


		Image coastShading;
		{
			Tuple2<Image, Image> tuple = darkenLandNearCoastlinesAndRegionBorders(settings, mapParts.graph, settings.resolution, mapSnippet,
					landMask, mapParts.background, null, centersToDraw, drawBounds, false);
			mapSnippet = tuple.getFirst();
			coastShading = tuple.getSecond();
		}

		// Store the current version of mapSnippet for a background when drawing icons later.
		Image landBackground = mapSnippet.deepCopy();

		if (settings.drawRegionColors)
		{
			Painter g = mapSnippet.createPainter();
			g.setColor(settings.coastlineColor);
			mapParts.graph.drawRegionBorders(g, sizeMultiplier, true, centersToDraw, drawBounds);
		}

		Set<Edge> edgesToDraw = getEdgesFromCenters(mapParts.graph, centersToDraw);
		drawRivers(settings, mapParts.graph, mapSnippet, edgesToDraw, drawBounds);

		// Draw ocean
		Image oceanTextureSnippet;
		{
			oceanTextureSnippet = mapParts.background.createOceanSnippet(drawBounds);
			mapSnippet = ImageHelper.maskWithImage(mapSnippet, oceanTextureSnippet, landMask);
		}

		// Add effects to ocean along coastlines
		Image oceanBlur;
		{
			oceanBlur = createOceanEffects(settings, mapParts.graph, settings.resolution, landMask, centersToDraw, drawBounds);
			if (oceanBlur != null)
			{
				mapSnippet = ImageHelper.maskWithColor(mapSnippet, settings.oceanEffectsColor, oceanBlur, true);
			}
		}

		// Draw coastlines.
		{
			Painter p = mapSnippet.createPainter(DrawQuality.High);
			p.setColor(settings.coastlineColor);
			mapParts.graph.drawCoastlineWithLakeShores(p, sizeMultiplier, centersToDraw, drawBounds);
		}

		// Draw icons
		List<IconDrawTask> iconsThatDrew = mapParts.iconDrawer.drawAllIcons(mapSnippet, landBackground, landTextureSnippet, drawBounds);

		Image textBackground = updateLandMaskAndCreateTextBackground(settings, mapParts.graph, landMask, iconsThatDrew, landTextureSnippet,
				oceanTextureSnippet, mapParts.background, oceanBlur, coastShading, mapParts.iconDrawer, centersToDraw, drawBounds);

		IntRectangle boundsInSourceToCopyFrom = new IntRectangle((int) replaceBounds.x - (int) drawBounds.x,
				(int) replaceBounds.y - (int) drawBounds.y, (int) replaceBounds.width, (int) replaceBounds.height);

		// Update the snippet in textBackground because the Fonts tab uses that as part of speeding up text re-drawing.
		ImageHelper.copySnippetFromSourceAndPasteIntoTarget(mapParts.textBackground, textBackground,
				replaceBounds.upperLeftCorner().toIntPoint(), boundsInSourceToCopyFrom, 0);

		// If present, also update the cached version of the map before adding text so that the Fonts tab can draw the map faster.
		if (mapParts.mapBeforeAddingText != null)
		{
			ImageHelper.copySnippetFromSourceAndPasteIntoTarget(mapParts.mapBeforeAddingText, mapSnippet,
					replaceBounds.upperLeftCorner().toIntPoint(), boundsInSourceToCopyFrom, 0);
		}

		if (settings.drawText)
		{
			textDrawer.drawTextFromEdits(mapSnippet, textBackground, mapParts.graph, drawBounds);
		}
		textDrawer.updateTextBoundsIfNeeded(mapParts.graph);

		IntPoint drawBoundsUpperLeftCornerAdjustedForBorder = new IntPoint(
				drawBounds.upperLeftCorner().toIntPoint().x + mapParts.background.getBorderWidthScaledByResolution(),
				drawBounds.upperLeftCorner().toIntPoint().y + mapParts.background.getBorderWidthScaledByResolution());

		// Add frayed border
		if (settings.frayedBorder)
		{
			int blurLevel = (int) (settings.frayedBorderBlurLevel * sizeMultiplier);
			mapSnippet = ImageHelper.setAlphaFromMaskInRegion(mapSnippet, mapParts.frayedBorderMask, true,
					drawBoundsUpperLeftCornerAdjustedForBorder);
			if (blurLevel > 0)
			{
				mapSnippet = ImageHelper.maskWithColorInRegion(mapSnippet, settings.frayedBorderColor, mapParts.frayedBorderBlur, true,
						drawBoundsUpperLeftCornerAdjustedForBorder);
			}
		}

		// Add grunge
		if (settings.drawGrunge && settings.grungeWidth > 0)
		{
			mapSnippet = ImageHelper.maskWithColorInRegion(mapSnippet, settings.frayedBorderColor, mapParts.grunge, true,
					drawBoundsUpperLeftCornerAdjustedForBorder);
		}

		IntPoint replaceBoundsUpperLeftCornerAdjustedForBorder = new IntPoint(
				replaceBounds.upperLeftCorner().toIntPoint().x + mapParts.background.getBorderWidthScaledByResolution(),
				replaceBounds.upperLeftCorner().toIntPoint().y + mapParts.background.getBorderWidthScaledByResolution());
		// Update the snippet in the main map.
		ImageHelper.copySnippetFromSourceAndPasteIntoTarget(fullSizedMap, mapSnippet, replaceBoundsUpperLeftCornerAdjustedForBorder,
				boundsInSourceToCopyFrom, mapParts.background.getBorderWidthScaledByResolution());

		if (DebugFlags.showIncrementalUpdateBounds())
		{
			Painter p = fullSizedMap.createPainter();
			int scaledBorderWidth = settings.drawBorder ? (int) (settings.borderWidth * settings.resolution) : 0;
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


		// Print run time
		// updateSW.printElapsedTime();

		return replaceBounds.toIntRectangle();
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

		if (!AssetsPath.getInstallPath().equals(settings.customImagesPath) && settings.customImagesPath != null
				&& !settings.customImagesPath.isEmpty())
		{
			if (!new File(settings.customImagesPath).exists())
			{
				throw new RuntimeException("The custom images folder '" + settings.customImagesPath + "' does not exist.");
			}
			Logger.println("Using custom images folder: " + settings.customImagesPath);
		}

		r = new Random(settings.randomSeed);
		Dimension mapBounds = Background.calcMapBoundsAndAdjustResolutionIfNeeded(settings, maxDimensions);
		double sizeMultiplier = calcSizeMultipilerFromResolutionScale(settings.resolution);

		// Kick of a job to create the graph while the background is being created.
		Future<WorldGraph> graphTask = ThreadHelper.getInstance().submit(() ->
		{
			if (mapParts == null || mapParts.graph == null)
			{
				Logger.println("Creating the graph.");
				WorldGraph graphCreated = createGraph(settings, mapBounds.width, mapBounds.height, r, settings.resolution,
						settings.edits.isEmpty());
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
		}, false);

		Background background;
		if (mapParts != null && mapParts.background != null)
		{
			background = mapParts.background;
		}
		else
		{
			Logger.println("Generating the background image.");
			background = new Background(settings, mapBounds);
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
		long frayedBorderSeed = r.nextLong();
		if (!isLowMemoryMode)
		{
			frayedBorderTask = startFrayedBorderCreation(frayedBorderSeed, settings, mapDimensions, sizeMultiplier, mapParts);
		}

		List<Set<Center>> lakes = null;
		if (settings.edits.text.size() == 0)
		{
			lakes = graph.markLakes();
		}

		Image map;
		Image textBackground;
		List<Set<Center>> mountainGroups;
		List<IconDrawTask> cities;
		if (mapParts == null || mapParts.mapBeforeAddingText == null || settings.edits.text.size() == 0)
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
		NameCreator nameCreator = null;
		if (mapParts == null || mapParts.nameCreator == null)
		{
			nameCreator = new NameCreator(settings);

			if (mapParts != null)
			{
				mapParts.nameCreator = nameCreator;
			}
		}
		else
		{
			nameCreator = mapParts.nameCreator;
		}

		TextDrawer textDrawer = new TextDrawer(settings);

		textDrawer.setMapTexts(settings.edits.text);

		if (settings.edits.text.size() > 0)
		{
			textDrawer.drawTextFromEdits(map, textBackground, graph, null);
		}
		else
		{
			// Generate text regardless off settings.drawText because
			// the editor might be generating the map without text
			// now, but want to show the text later, so in that case we would
			// want to generate the text but not show it.
			textDrawer.generateText(graph, map, nameCreator, textBackground, mountainGroups, cities, lakes);
		}

		textBackground = null;

		// Debug code
		// graph.drawCorners(map.createPainter());
		// graph.drawVoronoi(map.createPainter());

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

		if (settings.frayedBorder)
		{
			Image frayedBorderMask;
			Image frayedBorderBlur;
			if (isLowMemoryMode && frayedBorderTask == null)
			{
				frayedBorderTask = startFrayedBorderCreation(frayedBorderSeed, settings, mapDimensions, sizeMultiplier, mapParts);
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

			map = ImageHelper.setAlphaFromMask(map, frayedBorderMask, true);
			if (frayedBorderBlur != null)
			{
				map = ImageHelper.maskWithColor(map, settings.frayedBorderColor, frayedBorderBlur, true);
			}
		}

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

		checkForCancel();

		double elapsedTime = System.currentTimeMillis() - startTime;
		Logger.println("Total time to generate map (in seconds): " + elapsedTime / 1000.0);

		Logger.println("Done creating map.");

		return map;
	}

	private Future<Tuple2<Image, Image>> startFrayedBorderCreation(long frayedBorderSeed, MapSettings settings, Dimension mapDimensions,
			double sizeMultiplier, MapParts mapParts)
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
				WorldGraph frayGraph = GraphCreator.createSimpleGraph(mapDimensions.width, mapDimensions.height, polygonCount,
						new Random(frayedBorderSeed), settings.resolution, true);
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
			}, false);
		}
		return null;
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
			}, false);
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
		applyCenterEdits(graph, settings.edits, null, settings.drawRegionColors);

		checkForCancel();

		background.doSetupThatNeedsGraph(settings, graph, null, null, null);
		if (mapParts == null)
		{
			background.landBeforeRegionColoring = null;
		}

		IconDrawer iconDrawer;
		boolean needToAddIcons;
		if (mapParts == null || mapParts.iconDrawer == null)
		{
			iconDrawer = new IconDrawer(graph, new Random(r.nextLong()), settings);
			if (mapParts != null)
			{
				mapParts.iconDrawer = iconDrawer;
			}

			needToAddIcons = !settings.edits.hasIconEdits;
		}
		else
		{
			iconDrawer = mapParts.iconDrawer;
			needToAddIcons = !settings.edits.hasIconEdits;
			r.nextLong(); // Use the random number generator the same as if I
							// had created the icon drawer.
		}

		List<Set<Center>> mountainAndHillGroups = null;
		if (needToAddIcons)
		{
			iconDrawer.markMountains();
			iconDrawer.markHills();
			iconDrawer.markCities(settings.cityProbability);
			mountainAndHillGroups = iconDrawer.findMountainAndHillGroups();
		}
		else
		{
			iconDrawer.addOrUpdateIconsFromEdits(settings.edits, graph.centers, this);
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
		{
			Tuple2<Image, Image> tuple = darkenLandNearCoastlinesAndRegionBorders(settings, graph, settings.resolution, map, landMask,
					background, null, null, null, true);
			map = tuple.getFirst();
			coastShading = tuple.getSecond();
		}

		checkForCancel();

		// Store the current version of the map for a background when drawing icons later.
		Image landBackground = map.deepCopy();

		checkForCancel();

		if (settings.drawRegionColors)
		{
			{
				Painter g = map.createPainter();
				g.setColor(settings.coastlineColor);
				double sizeMultiplier = calcSizeMultipilerFromResolutionScale(settings.resolution);
				graph.drawRegionBorders(g, sizeMultiplier, true, null, null);
			}
		}

		checkForCancel();

		// Add rivers.
		Logger.println("Adding rivers.");
		drawRivers(settings, graph, map, null, null);

		checkForCancel();

		List<Set<Center>> mountainGroups = null;
		List<IconDrawTask> cities = null;
		if (needToAddIcons)
		{
			iconDrawer.addIcons(mountainAndHillGroups, this);
		}

		if (settings.drawRoads)
		{
			// TODO put back road drawer stuff
			// RoadDrawer roadDrawer = new RoadDrawer(r, settings, graph,
			// iconDrawer);
			// roadDrawer.markRoads();
			// roadDrawer.drawRoads(map, sizeMultiplier);
		}

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

		Image oceanBlur = createOceanEffects(settings, graph, settings.resolution, landMask, null, null);
		if (oceanBlur != null)
		{
			Logger.println("Adding effects to ocean along coastlines.");
			map = ImageHelper.maskWithColor(map, settings.oceanEffectsColor, oceanBlur, true);
		}

		checkForCancel();

		// Draw coastlines.
		{
			Painter g = map.createPainter(DrawQuality.High);
			g.setColor(settings.coastlineColor);
			double sizeMultiplier = calcSizeMultipilerFromResolutionScale(settings.resolution);
			graph.drawCoastlineWithLakeShores(g, sizeMultiplier, null, null);
		}

		checkForCancel();

		Logger.println("Drawing all icons.");
		List<IconDrawTask> iconsThatDrew = iconDrawer.drawAllIcons(map, landBackground, background.land, null);
		landBackground = null;

		// Needed for drawing text
		Image textBackground = updateLandMaskAndCreateTextBackground(settings, graph, landMask, iconsThatDrew, background.land,
				background.ocean, background, oceanBlur, coastShading, iconDrawer, null, null);

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
			List<IconDrawTask> iconsThatDrew, Image landTexture, Image oceanTexture, Background background, Image oceanBlur,
			Image coastShading, IconDrawer iconDrawer, Collection<Center> centersToDraw, Rectangle drawBounds)
	{
		iconDrawer.drawContentMasksOntoLandMask(landMask, iconsThatDrew, drawBounds);

		Image textBackground = ImageHelper.maskWithColor(landTexture, Color.black, landMask, false);
		textBackground = darkenLandNearCoastlinesAndRegionBorders(settings, graph, settings.resolution, textBackground, landMask,
				background, coastShading, centersToDraw, drawBounds, false).getFirst();
		textBackground = ImageHelper.maskWithImage(textBackground, oceanTexture, landMask);
		if (oceanBlur != null)
		{
			oceanBlur = ImageHelper.maskWithColor(oceanBlur, Color.black, landMask, true);
			textBackground = ImageHelper.maskWithColor(textBackground, settings.oceanEffectsColor, oceanBlur, true);
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

			float scale = ((float) settings.coastShadingColor.getAlpha()) / ((float) (maxPixelValue)) * scaleForDarkening
					* calcScaleToMakeConvolutionEffectsLightnessInvariantToKernelSize(settings.coastShadingLevel, sizeMultiplier)
					* calcScaleCompensateForCoastlineShadingDrawingAtAFullPixelWideAtLowerResolutions(targetStrokeWidth);

			// coastShading can be passed in to save time when calling this method a second time for the text background image.
			if (coastShading == null)
			{
				float[][] kernel = ImageHelper.createGaussianKernel(blurLevel);

				Image coastlineAndLakeShoreMask = Image.create(mapOrSnippet.getWidth(), mapOrSnippet.getHeight(), ImageType.Binary);
				Painter p = coastlineAndLakeShoreMask.createPainter(DrawQuality.High);
				p.setColor(Color.white);
				graph.drawCoastlineWithLakeShores(p, targetStrokeWidth, centersToDraw, drawBounds);

				if (settings.drawRegionColors)
				{
					p.setColor(Color.white);
					graph.drawRegionBorders(p, sizeMultiplier, false, centersToDraw, drawBounds);
					coastShading = ImageHelper.convolveGrayscaleThenScale(coastlineAndLakeShoreMask, kernel, scale, true);

				}
				else
				{
					coastShading = ImageHelper.convolveGrayscaleThenScale(coastlineAndLakeShoreMask, kernel, scale, true);
				}
			}

			if (settings.drawRegionColors)
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

	private static Image createOceanEffects(MapSettings settings, WorldGraph graph, double resolutionScaled, Image landMask,
			Collection<Center> centersToDraw, Rectangle drawBounds)
	{
		if (drawBounds == null)
		{
			drawBounds = graph.bounds;
		}
		double sizeMultiplier = calcSizeMultipilerFromResolutionScale(resolutionScaled);

		Image oceanEffects = null;
		if (((settings.oceanEffect == OceanEffect.Ripples || settings.oceanEffect == OceanEffect.Blur)
				&& (int) (settings.oceanEffectsLevel * sizeMultiplier) > 0)
				|| ((settings.oceanEffect == OceanEffect.ConcentricWaves || settings.oceanEffect == OceanEffect.FadingConcentricWaves)
						&& settings.concentricWaveCount > 0))
		{
			double targetStrokeWidth = sizeMultiplier;
			Image coastlineMask = Image.create((int) drawBounds.width, (int) drawBounds.height, ImageType.Binary);
			{
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
			}

			if (settings.oceanEffect == OceanEffect.Ripples || settings.oceanEffect == OceanEffect.Blur)
			{
				float[][] kernel;
				if (settings.oceanEffect == OceanEffect.Ripples)
				{
					kernel = ImageHelper.createPositiveSincKernel((int) (settings.oceanEffectsLevel * sizeMultiplier),
							1.0 / sizeMultiplier);
				}
				else
				{
					kernel = ImageHelper.createGaussianKernel((int) (settings.oceanEffectsLevel * sizeMultiplier));
				}
				int maxPixelValue = Image.getMaxPixelLevelForType(ImageType.Grayscale8Bit);
				final float scaleForDarkening = coastlineShadingScale;
				float scale = ((float) settings.oceanEffectsColor.getAlpha()) / ((float) (maxPixelValue)) * scaleForDarkening
						* calcScaleToMakeConvolutionEffectsLightnessInvariantToKernelSize(settings.oceanEffectsLevel, sizeMultiplier)
						* calcScaleCompensateForCoastlineShadingDrawingAtAFullPixelWideAtLowerResolutions(targetStrokeWidth);
				oceanEffects = ImageHelper.convolveGrayscaleThenScale(coastlineMask, kernel, scale, true);
				if (settings.drawOceanEffectsInLakes)
				{
					oceanEffects = removeOceanEffectsFromLand(graph, oceanEffects, landMask, centersToDraw, drawBounds);
				}
				else
				{
					oceanEffects = removeOceanEffectsFromLandAndLandLockedLakes(graph, oceanEffects, centersToDraw, drawBounds);
				}
			}
			else
			{
				oceanEffects = Image.create((int) drawBounds.width, (int) drawBounds.height, ImageType.Grayscale8Bit);
				int maxPixelValue = Image.getMaxPixelLevelForType(ImageType.Grayscale8Bit);
				// This number just needs to be big enough that the waves are sufficiently thick.
				final float scaleForDarkening = 20f;
				float scale = ((float) settings.oceanEffectsColor.getAlpha()) / ((float) (maxPixelValue)) * scaleForDarkening
						* calcScaleCompensateForCoastlineShadingDrawingAtAFullPixelWideAtLowerResolutions(targetStrokeWidth);

				if (settings.oceanEffect == OceanEffect.ConcentricWaves || settings.oceanEffect == OceanEffect.FadingConcentricWaves)
				{
					double widthBetweenWaves = concentricWaveWidthBetweenWaves * sizeMultiplier;
					double lineWidth = concentricWaveLineWidth * sizeMultiplier;
					double largestLineWidth = settings.concentricWaveCount * (widthBetweenWaves + lineWidth);
					final double opacityOfLastWave;
					if (settings.oceanEffect == OceanEffect.ConcentricWaves)
					{
						opacityOfLastWave = 1.0;
					}
					else
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

					for (int i : new Range(0, settings.concentricWaveCount))
					{
						{
							double whiteWidth = largestLineWidth - (i * (widthBetweenWaves + lineWidth));
							if (whiteWidth <= 0)
							{
								continue;
							}
							Image blur = ImageHelper.convolveGrayscaleThenScale(coastlineMask,
									ImageHelper.createGaussianKernel((int) whiteWidth), scale, true);

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

							ImageHelper.addThresholded(blur, 1, (int) (settings.oceanEffectsColor.getAlpha() * waveOpacity), oceanEffects);
						}

						{
							double blackWidth = largestLineWidth - (i * (widthBetweenWaves + lineWidth)) - lineWidth;
							if (blackWidth <= 0)
							{
								continue;
							}
							Image blur = ImageHelper.convolveGrayscaleThenScale(coastlineMask,
									ImageHelper.createGaussianKernel((int) blackWidth), scale, true);
							ImageHelper.subtractThresholded(blur, 1, oceanEffects.getMaxPixelLevel(), oceanEffects);
						}
					}
				}

				if (settings.drawOceanEffectsInLakes)
				{
					oceanEffects = removeOceanEffectsFromLand(graph, oceanEffects, landMask, centersToDraw, drawBounds);
				}
				else
				{
					oceanEffects = removeOceanEffectsFromLandAndLandLockedLakes(graph, oceanEffects, centersToDraw, drawBounds);
				}
			}
		}
		return oceanEffects;
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
		graph.drawLandAndLandLockedLakesBlackAndOceanWhite(landAndLakeMask.createPainter(), centersToDraw, drawBounds);
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
		float[] landHsb = settings.landColor.getHSB();
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
			boolean createElevationBiomesAndRegions)
	{
		WorldGraph graph = GraphCreator.createGraph(width, height, settings.worldSize, settings.edgeLandToWaterProbability,
				settings.centerLandToWaterProbability, new Random(r.nextLong()), resolutionScale, settings.lineStyle,
				settings.pointPrecision, createElevationBiomesAndRegions, settings.lloydRelaxationsScale, settings.drawRegionColors);

		// Setup region colors even if settings.drawRegionColors = false because
		// edits need them in case someone edits a map without region colors,
		// then later enables region colors.
		assignRandomRegionColors(graph, settings);

		return graph;
	}
	

	public static double calcSizeMultiplier(double mapWidth)
	{
		return mapWidth / baseResolution;
	}
	
	public static double calcSizeMultipilerFromResolutionScale(double resoutionScale)
	{
		return (8.0 / 3.0) * resoutionScale;
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

	private static void applyCenterEdits(WorldGraph graph, MapEdits edits, List<CenterEdit> centerChanges,
			boolean areRegionBoundariesVisible)
	{
		if (edits == null || edits.centerEdits.isEmpty())
		{
			return;
		}

		if (edits.centerEdits.size() != graph.centers.size())
		{
			throw new IllegalArgumentException(
					"The map edits have " + edits.centerEdits.size() + " polygons, but the world size is " + graph.centers.size());
		}

		if (centerChanges == null)
		{
			centerChanges = edits.centerEdits;
		}

		Set<Center> centersChanged = new HashSet<>();
		Set<Center> needsRebuildNoisyEdges = new HashSet<>();

		for (CenterEdit cEdit : centerChanges)
		{
			// Use a copy so that we can't hit a race condition where the editor changes the object while we're reading from it.
			cEdit = cEdit.deepCopyWithLock();

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
		p.setColor(settings.riverColor);
		graph.drawRivers(p, edgesToDraw, drawBounds);
	}

	public static Set<String> getAvailableBorderTypes(String imagesPath)
	{
		if (imagesPath == null || imagesPath.isEmpty())
		{
			imagesPath = AssetsPath.getInstallPath();
		}

		File[] directories = new File(Paths.get(imagesPath, "borders").toString()).listFiles(File::isDirectory);
		if (directories == null || directories.length == 0)
		{
			return new TreeSet<String>();
		}
		return new TreeSet<String>(Arrays.stream(directories).map(file -> file.getName()).collect(Collectors.toList()));
	}

	public Image createHeightMap(MapSettings settings)
	{
		r = new Random(settings.randomSeed);
		IntDimension mapBounds = new Dimension(settings.generatedWidth * settings.heightmapResolution,
				settings.generatedHeight * settings.heightmapResolution).toIntDimension();
		WorldGraph graph = createGraph(settings, mapBounds.width, mapBounds.height, r, settings.resolution, true);
		return GraphCreator.createHeightMap(graph, new Random(settings.randomSeed));
	}

	private Set<Center> getCentersFromEdges(WorldGraph graph, Set<Edge> edges)
	{
		Set<Center> centers = new HashSet<Center>();
		for (Edge edge : edges)
		{
			if (edge.d0 != null)
			{
				centers.add(edge.d0);
			}

			if (edge.d1 != null)
			{
				centers.add(edge.d1);
			}
		}

		return centers;
	}

	private Set<Edge> getEdgesFromCenters(WorldGraph graph, Collection<Center> centers)
	{
		Set<Edge> edges = new HashSet<>();
		for (Center center : centers)
		{
			edges.addAll(center.borders);
		}
		return edges;
	}

	private List<CenterEdit> getCenterEditsForCenters(MapEdits edits, Collection<Center> centers)
	{
		return centers.stream().map(center -> edits.centerEdits.get(center.index)).collect(Collectors.toList());
	}

	private Set<EdgeEdit> getEdgeEditsForEdges(MapEdits edits, Collection<Edge> edges)
	{
		return edges.stream().map(edge -> edits.edgeEdits.get(edge.index)).collect(Collectors.toSet());
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
}
