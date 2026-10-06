package nortantis;

import nortantis.TextBackgroundDrawer.LineLayout;
import nortantis.editor.River;
import nortantis.editor.RiverPathNode;
import nortantis.geom.Dimension;
import nortantis.geom.IntPoint;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.geom.RotatedRectangle;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.graph.voronoi.Edge;
import nortantis.platform.*;
import nortantis.swing.MapEdits;
import nortantis.util.*;
import org.apache.commons.math3.exception.NoDataException;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class TextDrawer
{
	private MapSettings settings;
	private final int mountainRangeMinSize = 50;
	// y offset added to names of mountain groups smaller than a range.
	private final double mountainGroupYOffset = 45;
	private final double singleMountainYOffset = 14;
	private final double twoMountainsYOffset = 22;
	// Rivers narrower than this will not be named.
	private final int riverMinWidth = 3;
	// Rivers shorter than this will not be named. This must be at least 3.
	// Note that this is the number of corners, not the number of edges.
	private final int riverMinLength = 10;
	private final int largeRiverWidth = 4;
	// This is how far away from a river it's name will be drawn.
	private final double riverNameRiseHeight = -10;
	private final double cityYNameOffset = 4;
	private final double thresholdForPuttingTitleOnLand = 0.3;

	private Image landAndOceanBackground;
	private CopyOnWriteArrayList<MapText> mapTexts;
	private List<RotatedRectangle> cityAreas;
	private Rectangle graphBounds;
	private Random r;
	private final double sizeMultiplier;
	/**
	 * Fonts from text styles, scaled to the resolution being drawn at and resolved to families this machine has, keyed by the font in the
	 * style.
	 */
	private final Map<Font, Font> scaledFontsByStyleFont = new ConcurrentHashMap<>();
	/**
	 * How many pieces of text text generation has created, which seeds the background wobble of each one.
	 */
	private int generatedTextCount;

	/**
	 *
	 * @param settings
	 *            The map settings to use. Some of these settings are for text drawing.
	 */
	public TextDrawer(MapSettings settings)
	{
		this.settings = settings;
		this.r = new Random(settings.textRandomSeed);

		if (settings.edits != null && settings.edits.text != null && settings.edits.isInitialized())
		{
			// Set the MapTexts in this TextDrawer to be the same object as
			// settings.edits.text.
			// This makes it so that any edits done to the settings will
			// automatically be reflected
			// in the text drawer. Also, it is necessary because the TextDrawer
			// adds the bounds to the
			// map texts, which are needed to make them clickable to edit them.
			mapTexts = settings.edits.text;
		}
		else if (settings.edits != null && settings.edits.bakeGeneratedTextAsEdits)
		{
			mapTexts = new CopyOnWriteArrayList<>();
			settings.edits.text = mapTexts;
			// Clear the flag below because the text only needs to be generated
			// once in the editor
			// (although realistically it doesn't matter because the case above
			// this one will be taken
			// if text generate created at least one text, which it will).
			settings.edits.bakeGeneratedTextAsEdits = false;
		}
		else
		{
			mapTexts = new CopyOnWriteArrayList<>();
		}

		sizeMultiplier = MapCreator.calcSizeMultiplierFromResolutionScale(settings.resolution);
	}

	/**
	 * Scales a font from the settings to the resolution being drawn at, and resolves the family to one this machine actually has. Glyph
	 * coverage is not considered: a font that is present but lacks glyphs for the text leaves those characters undrawn, which is the truth
	 * about the map, rather than being quietly swapped for a font its author did not choose.
	 */
	private static Font scaleAndResolve(Font font, double sizeMultiplier)
	{
		Font scaled = font.deriveFont(font.getStyle(), (float) (font.getSize() * sizeMultiplier));
		return FontFinder.resolveForDrawing(scaled);
	}

	public synchronized void drawTextFromEdits(Image map, Image landAndOceanBackground, WorldGraph graph, Rectangle drawBounds)
	{
		this.landAndOceanBackground = landAndOceanBackground;

		drawText(map, graph, settings.edits.text, drawBounds);

		this.landAndOceanBackground = null;
	}

	public void generateText(WorldGraph graph, Image map, NameCreator nameCreator, Image landAndOceanBackground, List<Set<Center>> mountainGroups, List<IconDrawTask> cityDrawTasks,
			List<Set<Center>> lakes, List<River> rivers)
	{
		this.landAndOceanBackground = landAndOceanBackground;

		if (cityDrawTasks == null)
		{
			cityDrawTasks = new ArrayList<>();
		}

		cityAreas = cityDrawTasks.stream().map(drawTask -> drawTask.createArea()).collect(Collectors.toList());

		if (mountainGroups == null)
		{
			mountainGroups = new ArrayList<>(0);
		}

		generateText(map, graph, nameCreator, mountainGroups, cityDrawTasks, lakes, rivers);

		this.landAndOceanBackground = null;
	}

	private void generateText(Image map, WorldGraph graph, NameCreator nameCreator, List<Set<Center>> mountainGroups, List<IconDrawTask> cityDrawTasks, List<Set<Center>> lakes, List<River> rivers)
	{
		// First, generate text without drawing it. I originally drew text as I generated it, but it led to weird conditions where
		// the generator placed text, and then the editor moves it slightly when it drew again. To fix this, I draw after generating
		// so that the code path that draws the text is essentially the same for the generator and the editor.
		boolean drawTextPrev = settings.drawText;
		settings.drawText = false;
		try
		{
			// All text drawn must be done so in order from highest to lowest
			// priority because if I try to draw
			// text on top of other text, the latter will not be displayed.

			graphBounds = new Rectangle(0, 0, graph.getWidth(), graph.getHeight());

			try (Painter p = map.createPainter())
			{
				addTitle(map, graph, nameCreator, p);

				for (IconDrawTask city : cityDrawTasks)
				{
					Set<Point> cityLoc = new HashSet<>(1);
					cityLoc.add(city.centerLoc);
					String cityName = nameCreator.generateNameOfType(TextType.City, nameCreator.sampleCityTypesForCityFileName(city.fileName), true);
					double riseOffset = city.scaledSize.height / 2 + (cityYNameOffset * settings.resolution);
					RotatedRectangle cityArea = city.createArea();
					drawNameRotated(map, p, graph, cityName, cityLoc, riseOffset, true, cityArea, TextType.City);
				}

				for (Region region : graph.regions.values())
				{
					Set<Point> locations = extractLocationsFromCenters(region.getCenters());
					String name;
					try
					{
						name = nameCreator.generateNameOfType(TextType.Region, null, true);
					}
					catch (NotEnoughNamesException ex)
					{
						throw new RuntimeException(ex.getMessage());
					}
					drawNameFitIntoCenters(map, p, name, locations, graph, true, TextType.Region);
				}

				for (Set<Center> mountainGroup : mountainGroups)
				{
					if (mountainGroup.size() >= mountainRangeMinSize)
					{
						Set<Point> locations = extractLocationsFromCenters(mountainGroup);
						drawNameRotated(map, p, graph, nameCreator.generateNameOfType(TextType.Mountain_range, null, true), locations, 0.0, true, null, TextType.Mountain_range);
					}
					else
					{
						if (mountainGroup.size() >= 2)
						{
							if (mountainGroup.size() == 2)
							{
								Point location = findCentroid(extractLocationsFromCenters(mountainGroup));
								MapText text = createMapText(nameCreator.generateNameOfType(TextType.Other_mountains, OtherMountainsType.Peaks, true), location, 0.0, TextType.Other_mountains);
								if (drawNameRotated(map, p, graph, twoMountainsYOffset * settings.resolution, true, null, text, null))
								{
									mapTexts.add(text);
								}
							}
							else
							{
								drawNameRotated(map, p, graph, nameCreator.generateNameOfType(TextType.Other_mountains, OtherMountainsType.Mountains, true), extractLocationsFromCenters(mountainGroup),
										mountainGroupYOffset * settings.resolution, true, null, TextType.Other_mountains);
							}
						}
						else
						{
							Point location = findCentroid(extractLocationsFromCenters(mountainGroup));
							MapText text = createMapText(nameCreator.generateNameOfType(TextType.Other_mountains, OtherMountainsType.Peak, true), location, 0.0, TextType.Other_mountains);
							if (drawNameRotated(map, p, graph, singleMountainYOffset * settings.resolution, true, null, text, null))
							{
								mapTexts.add(text);
							}
						}
					}
				}

				for (Set<Center> lake : lakes)
				{
					String name = nameCreator.generateNameOfType(TextType.Lake, null, true);
					Set<Point> locations = extractLocationsFromCenters(lake);
					drawNameRotated(map, p, graph, name, locations, 0.0, true, null, TextType.Lake);
				}

				for (River river : rivers)
				{
					List<RiverPathNode> nodes = river.nodes;
					int numSegments = nodes.size() - 1;
					int maxWidth = 0;
					for (int i = 0; i < numSegments; i++)
					{
						maxWidth = Math.max(maxWidth, nodes.get(i).getWidthLevelToNext());
					}
					if (numSegments >= riverMinLength && maxWidth >= riverMinWidth)
					{
						RiverType riverType = maxWidth >= largeRiverWidth ? RiverType.Large : RiverType.Small;
						Set<Point> locations = extractLocationsFromRiver(river);
						drawNameRotated(map, p, graph, nameCreator.generateNameOfType(TextType.River, riverType, true), locations, riverNameRiseHeight * settings.resolution, true, null,
								TextType.River);
					}
				}
			}
		}
		finally
		{
			settings.drawText = drawTextPrev;
		}

		// Now actually draw the text (if settings.drawText is true).
		drawText(map, graph, mapTexts, null);
	}

	public void doForEachTextInBounds(List<MapText> mapTexts, Rectangle bounds, BiConsumer<MapText, RotatedRectangle> action)
	{
		try (Painter p = Image.create(1, 1, ImageType.ARGB).createPainter())
		{

			for (MapText text : mapTexts)
			{
				if (text.value == null || text.value.trim().length() == 0)
				{
					// This text was deleted.
					continue;
				}

				if (bounds == null)
				{
					action.accept(text, null);
				}
				else
				{
					setFontForText(p, text);

					// This method of detecting which text to draw isn't very precise, as it can have false positives,
					// but we can't use the Areas in the text object because they get updated during text drawing,
					// so they aren't useful for telling whether the text will appear in 'bounds'.

					Point textLocation = new Point(text.location.x * settings.resolution, text.location.y * settings.resolution);

					Rectangle singleLineBounds = getLine1BoundsWithoutCurvatureOrSpacing(text.value, textLocation, p, false);
					singleLineBounds = expandBoundsToIncludeCurvatureAndSpacing(singleLineBounds, text, text.value, p);
					singleLineBounds = addBackgroundPadding(singleLineBounds, getFontHeight(p), text);

					Rectangle textBoundsAllLines = singleLineBounds;
					// Since it wouldn't be easy from here to figure out whether the text will draw onto one line or two, combine
					// the bounds for both cases if it's possible the text could be split.
					if ((text.lineBreak == LineBreak.Auto || text.lineBreak == LineBreak.Two_lines) && text.value.trim().contains(" "))
					{
						Pair<String> lines = addLineBreakNearMiddle(text.value);

						// A split that leaves either line empty is drawn on one line, so only the single-line bounds apply.
						if (!lines.getFirst().isEmpty() && !lines.getSecond().isEmpty())
						{
							Rectangle line1Bounds = getLine1BoundsWithoutCurvatureOrSpacing(lines.getFirst(), textLocation, p, true);
							line1Bounds = expandBoundsToIncludeCurvatureAndSpacing(line1Bounds, text, lines.getFirst(), p);
							line1Bounds = addBackgroundPadding(line1Bounds, getFontHeight(p), text);

							Rectangle line2Bounds = getLine2BoundsWithoutCurvatureOrSpacing(lines.getSecond(), textLocation, p);
							line2Bounds = expandBoundsToIncludeCurvatureAndSpacing(line2Bounds, text, lines.getSecond(), p);
							line2Bounds = addBackgroundPadding(line2Bounds, getFontHeight(p), text);

							textBoundsAllLines = singleLineBounds.add(line1Bounds.add(line2Bounds));
						}
					}

					callIfMapTextIsInBounds(bounds, text, textBoundsAllLines, textLocation, action);
				}
			}
		}
	}

	public Rectangle getTextBoundingBoxFor1Or2LineSplit(MapText text)
	{
		if (text.value == null || text.value.trim().length() == 0)
		{
			// This text was deleted.
			return null;
		}
		try (Painter p = Image.create(1, 1, ImageType.ARGB).createPainter())
		{
			setFontForText(p, text);
			Point textLocation = new Point(text.location.x * settings.resolution, text.location.y * settings.resolution);

			// Get bounds for when the text is on one line.
			Rectangle bounds = getLine1BoundsWithoutCurvatureOrSpacing(text.value, textLocation, p, false);
			bounds = expandBoundsToIncludeCurvatureAndSpacing(bounds, text, text.value, p);
			bounds = addBackgroundPadding(bounds, getFontHeight(p), text);
			Rectangle boundingBox = new RotatedRectangle(bounds, text.angle, textLocation).getBounds();

			// Since it wouldn't be easy from here to figure out whether the text will draw onto one line or two, also add
			// the bounds when it splits under two lines.
			if ((text.lineBreak == LineBreak.Auto || text.lineBreak == LineBreak.Two_lines) && text.value.trim().contains(" "))
			{
				Pair<String> lines = addLineBreakNearMiddle(text.value);

				// A split that leaves either line empty is drawn on one line, so only the single-line bounds apply.
				if (!lines.getFirst().isEmpty() && !lines.getSecond().isEmpty())
				{
					Rectangle line1Bounds = getLine1BoundsWithoutCurvatureOrSpacing(lines.getFirst(), textLocation, p, true);
					line1Bounds = expandBoundsToIncludeCurvatureAndSpacing(line1Bounds, text, lines.getFirst(), p);
					line1Bounds = addBackgroundPadding(line1Bounds, getFontHeight(p), text);

					boundingBox = boundingBox.add(new RotatedRectangle(line1Bounds, text.angle, textLocation).getBounds());

					Rectangle line2Bounds = getLine2BoundsWithoutCurvatureOrSpacing(lines.getSecond(), textLocation, p);
					line2Bounds = expandBoundsToIncludeCurvatureAndSpacing(line2Bounds, text, lines.getSecond(), p);
					line2Bounds = addBackgroundPadding(line2Bounds, getFontHeight(p), text);
					boundingBox = boundingBox.add(new RotatedRectangle(line2Bounds, text.angle, textLocation).getBounds());
				}
			}

			return boundingBox;
		}
	}

	/**
	 * Grows the bounds of a text's letters to include everything its background can draw on, including fade.
	 */
	private Rectangle addBackgroundPadding(Rectangle textBounds, int fontHeight, MapText text)
	{
		double padding = getBackgroundBlendingPadding(fontHeight, text) + TextBackgroundDrawer.getMaxReach(text.style.background, fontHeight);
		return new Rectangle(textBounds.x - padding, textBounds.y - padding, textBounds.width + padding * 2, textBounds.height + padding * 2);
	}

	private void callIfMapTextIsInBounds(Rectangle boundsArea, MapText text, Rectangle lineBounds, Point pivot, BiConsumer<MapText, RotatedRectangle> action)
	{
		RotatedRectangle lineArea = new RotatedRectangle(lineBounds, text.angle, pivot);

		if (boundsArea == null || doAreasIntersect(new RotatedRectangle(boundsArea), lineArea))
		{
			action.accept(text, lineArea);
		}
	}

	/**
	 * Expands {@code bounds} (the region an incremental land/water/region-boundary change is going to redraw) to also include any
	 * {@link LineBreak#Auto} text whose line count the change could flip between one line and two. Only such a flip requires redrawing a
	 * text outside the change region (to erase the old layout); if the line count won't change, the text's pixels outside the change
	 * region are identical before and after, so leaving them out keeps the incremental redraw small - which matters a lot around large
	 * text like titles.
	 */
	public Rectangle expandBoundsToIncludeText(List<MapText> mapTexts, Rectangle bounds, WorldGraph graph, MapSettings settings)
	{
		if (!settings.drawText)
		{
			return bounds;
		}

		Tuple1<Rectangle> wrapperToMakeCompilerHappy = new Tuple1<>(bounds);

		// One scratch painter (for font metrics), shared across the texts checked below.
		try (Painter p = Image.create(1, 1, ImageType.ARGB).createPainter())
		{
			doForEachTextInBounds(mapTexts, bounds, (text, area) ->
			{
				if (text.lineBreak == LineBreak.Auto && willAutoTextLineCountChange(text, graph, p))
				{
					wrapperToMakeCompilerHappy.set(wrapperToMakeCompilerHappy.get().add(area.getBounds()));
				}
			});
		}
		return wrapperToMakeCompilerHappy.get();
	}

	/**
	 * Returns whether redrawing with the current graph would flip this {@link LineBreak#Auto} text between one line and two, compared to
	 * what is currently drawn on the map. This uses the same {@link #wouldOneLineCrossBoundary} check as {@link #chooseLines}, with
	 * {@code riseOffset} 0 as editor redraws use, so it can't drift from what the draw actually does. Returns true conservatively when the text's current layout is unknown or untrustworthy (it has never
	 * been drawn, or its bounds may be stale - see {@link MapEdits#textBoundsNeedRefresh}).
	 *
	 * @param p
	 *            A scratch painter used only for font metrics.
	 */
	private boolean willAutoTextLineCountChange(MapText text, WorldGraph graph, Painter p)
	{
		boolean hasMultipleWords = text.value.trim().split(" ").length > 1;
		if (!hasMultipleWords)
		{
			// Single-word text is always drawn on one line; it never splits, so a boundary change can't flip its line count.
			return false;
		}

		if (settings.edits.textBoundsNeedRefresh || text.line1Bounds == null)
		{
			// The stored bounds may not reflect what's actually on the map: the text was never drawn (line1Bounds == null), or the
			// bounds were just restored by undo/redo or loaded from disk and not yet redrawn at this resolution (textBoundsNeedRefresh).
			// Either way we can't trust line2Bounds as the current layout, so be conservative. The next draw refreshes every text's
			// bounds (updateTextBoundsIfNeeded) and clears the flag, so subsequent edits use the fast path.
			return true;
		}

		boolean willBeTwoLines = wouldOneLineCrossBoundary(text, 0.0, p, graph);
		// line2Bounds is non-null exactly when the last draw rendered this text on two lines.
		boolean isCurrentlyTwoLines = text.line2Bounds != null;
		return willBeTwoLines != isCurrentlyTwoLines;
	}

	private void drawText(Image map, WorldGraph graph, List<MapText> textToDraw, Rectangle drawBounds)
	{
		try (Painter p = map.createPainter(DrawQuality.High))
		{
			Point drawOffset = drawBounds == null ? null : drawBounds.upperLeftCorner();

			doForEachTextInBounds(textToDraw, drawBounds, ((text, ignored) ->
			{
				drawNameSplitIfNeeded(map, p, graph, 0.0, false, null, text, true, drawOffset);
			}));
		}

		// Only clear this flag if we drew every text (drawBounds null or equal to graph bounds);
		// an incremental update only refreshes bounds inside its draw region.
		if (drawBounds == null || drawBounds.equals(graph.bounds))
		{
			settings.edits.textBoundsNeedRefresh = false;
		}
	}

	private void setFontForText(Painter p, MapText text)
	{
		p.setFont(scaledFontsByStyleFont.computeIfAbsent(text.style.font, font -> scaleAndResolve(font, sizeMultiplier)));
	}

	private void addTitle(Image map, WorldGraph graph, NameCreator nameCreator, Painter p)
	{
		List<Tuple2<TectonicPlate, Double>> oceanPlatesAndWidths = new ArrayList<>();
		for (TectonicPlate plate : graph.plates)
			if (plate.type == PlateType.Oceanic)
				oceanPlatesAndWidths.add(new Tuple2<>(plate, findWidth(plate.centers)));

		List<Tuple2<TectonicPlate, Double>> landPlatesAndWidths = new ArrayList<>();
		for (TectonicPlate plate : graph.plates)
			if (plate.type == PlateType.Continental)
				landPlatesAndWidths.add(new Tuple2<>(plate, findWidth(plate.centers)));

		List<Tuple2<TectonicPlate, Double>> titlePlatesAndWidths;
		if (landPlatesAndWidths.size() > 0 && ((double) oceanPlatesAndWidths.size()) / landPlatesAndWidths.size() < thresholdForPuttingTitleOnLand)
		{
			titlePlatesAndWidths = landPlatesAndWidths;
		}
		else
		{
			titlePlatesAndWidths = oceanPlatesAndWidths;
		}

		// Try drawing the title in each plate in titlePlatesAndWidths, starting
		// from the widest plate to the narrowest.
		titlePlatesAndWidths.sort((t1, t2) -> -t1.getSecond().compareTo(t2.getSecond()));
		for (Tuple2<TectonicPlate, Double> plateAndWidth : titlePlatesAndWidths)
		{
			try
			{
				if (drawNameFitIntoCenters(map, p, nameCreator.generateNameOfType(TextType.Title, TitleType.Decorated, true), extractLocationsFromCenters(plateAndWidth.getFirst().centers), graph,
						true, TextType.Title))
				{
					return;
				}

				// The title didn't fit. Try drawing it with just a name.
				if (drawNameFitIntoCenters(map, p, nameCreator.generateNameOfType(TextType.Title, TitleType.NameOnly, true), extractLocationsFromCenters(plateAndWidth.getFirst().centers), graph,
						true, TextType.Title))
				{
					return;
				}
			}
			catch (NotEnoughNamesException e)
			{
				throw new RuntimeException(e.getMessage());
			}
		}

	}

	private double findWidth(Set<Center> centers)
	{
		double min = Collections.min(centers, new Comparator<Center>()
		{
			public int compare(Center c1, Center c2)
			{
				return Double.compare(c1.loc.x, c2.loc.x);
			}
		}).loc.x;
		double max = Collections.max(centers, new Comparator<Center>()
		{
			public int compare(Center c1, Center c2)
			{
				return Double.compare(c1.loc.x, c2.loc.x);
			}
		}).loc.x;
		return max - min;
	}

	private Set<Point> extractLocationsFromCenters(Set<Center> centers)
	{
		Set<Point> result = new TreeSet<Point>();
		for (Center c : centers)
		{
			result.add(c.loc);
		}
		return result;
	}

	@SuppressWarnings("unused")
	private Set<Point> extractLocationsFromCorners(Collection<Corner> corners)
	{
		Set<Point> result = new TreeSet<Point>();
		for (Corner c : corners)
		{
			result.add(c.loc);
		}
		return result;
	}

	private Set<Point> extractLocationsFromRiver(nortantis.editor.River river)
	{
		final int maxPointsToInclude = 11; // corresponds to 10 edges
		final int maxDistanceFromMouth = 2;
		List<RiverPathNode> nodes = river.nodes;
		int numSegments = nodes.size() - 1;

		int from, to;
		if (nodes.size() <= maxPointsToInclude)
		{
			from = 0;
			to = nodes.size();
		}
		else
		{
			// Place the label slightly inland from the mouth (wider end of the river).
			int distanceFromMouth = Math.min(numSegments - (maxPointsToInclude - 1), maxDistanceFromMouth);
			boolean mouthIsFirst = numSegments >= 1 && nodes.get(0).getWidthLevelToNext() > nodes.get(numSegments - 1).getWidthLevelToNext();
			if (mouthIsFirst)
			{
				from = distanceFromMouth;
				to = from + maxPointsToInclude;
			}
			else
			{
				to = nodes.size() - distanceFromMouth;
				from = to - maxPointsToInclude;
			}
		}

		Set<Point> result = new TreeSet<>();
		for (int i = from; i < to; i++)
		{
			Point ri = nodes.get(i).getLoc();
			result.add(new Point(ri.x * settings.resolution, ri.y * settings.resolution));
		}
		return result;
	}

	/**
	 * Draws the area around a line of text from landAndOceanBackground, which fades out icons, rivers, roads, and coastlines there so the
	 * text is readable when drawn on top of them.
	 */
	private void drawBackgroundBlendingForText(Image map, Painter p, MapText text, Point textStart, Rectangle textBoundsBeforeCurvatureAndSpacing, Rectangle textBounds, String name, Point pivot)
	{
		int kernelSize = getBackgroundBlendingKernelSize(getFontHeight(p), text);
		if (kernelSize == 0)
		{
			return;
		}
		int padding = getBackgroundBlendingPadding(getFontHeight(p), text);

		try (Image textBG = Image.create((int) (textBounds.width + padding * 2), (int) (textBounds.height + padding * 2), ImageType.Grayscale8Bit))
		{
			Point textStartDiffInMaskCausedByCurvatureAndSpacing;
			try (Painter bP = textBG.createPainter(DrawQuality.High))
			{
				bP.setFont(p.getFont());
				bP.setColor(Color.white);
				textStartDiffInMaskCausedByCurvatureAndSpacing = textBoundsBeforeCurvatureAndSpacing.upperLeftCorner().subtract(textBounds.upperLeftCorner());
				Point drawPointForMask = textStartDiffInMaskCausedByCurvatureAndSpacing.add(new Point(padding, padding + p.getFontAscent()));
				TextBackgroundDrawer.drawLetters(bP, TextBackgroundDrawer.layoutLine(bP, name, drawPointForMask, text.curvature, text.spacing), null, null);
			}

			// Blur to make a hazy background for the text.
			try (Image haze1 = ImageHelper.getInstance().blur(textBG, kernelSize, true, true))
			{
				// Threshold it and blur it again to make the haze bigger.
				ImageHelper.getInstance().threshold(haze1, 1);
				try (Image haze2 = ImageHelper.getInstance().blur(haze1, kernelSize, true, true))
				{
					ImageHelper.getInstance().combineImagesWithMaskInRegion(map, landAndOceanBackground, haze2,
							((int) Math.round(textStart.x - textStartDiffInMaskCausedByCurvatureAndSpacing.x)) - padding,
							(int) Math.round(textStart.y - textStartDiffInMaskCausedByCurvatureAndSpacing.y) - p.getFontAscent() - padding, text.angle, pivot);
				}
			}
		}
	}

	private int getBackgroundBlendingKernelSize(int fontHeight, MapText text)
	{
		// This magic number below is a result of trial and error to get the
		// blur levels to look right.
		int kernelSize = (int) ((13.0 / 54.0) * text.style.background.getFadeBehindToDraw() * fontHeight);
		return kernelSize;
	}

	private int getBackgroundBlendingPadding(int fontHeight, MapText text)
	{
		return getBackgroundBlendingKernelSize(fontHeight, text);
	}

	private final double spacingScale = 1.0 / 20.0;

	private Rectangle expandBoundsToIncludeCurvatureAndSpacing(Rectangle originalBounds, MapText text, String line, Painter p)
	{
		if (line == null || line.isEmpty())
		{
			return null;
		}

		setFontForText(p, text);

		double ascent = p.getFontAscent();
		double descent = p.getFontDescent();

		Point textStart = new Point(originalBounds.x, originalBounds.y + p.getFontAscent());
		double adjustedSpacing = line.length() < 2 ? 0.0 : text.spacing * ascent * TextBackgroundDrawer.spacingScale;
		double startXDiffFromSpacing = (adjustedSpacing * (line.length() - 1)) / 2.0;
		double totalWidth = p.stringWidth(line) + (line.length() > 0 ? (line.length() - 1) * adjustedSpacing : 0.0);

		if (Math.abs(text.curvature) <= 0.001)
		{
			// Special case: no curvature
			if (text.spacing == 0)
			{
				return originalBounds;
			}
			else
			{
				return new Rectangle(originalBounds.x - startXDiffFromSpacing, originalBounds.y, totalWidth, originalBounds.height);
			}
		}

		Point textCenter = textStart.add(new Point(totalWidth / 2.0 - startXDiffFromSpacing, 0));
		double angleRange = Math.abs(text.curvature * TextBackgroundDrawer.maxTextCurveAngleRange);
		double radius;
		Point circleCenter;

		Rectangle boundsSoFar = null;

		if (text.curvature > 0)
		{
			// Concave down. Curve along the baseline of the text.
			radius = (totalWidth / 2.0) / angleRange;
			circleCenter = textCenter.add(new Point(0.0, radius));

			double startAngle = -angleRange;
			double widthSoFar = 0.0;

			for (int i = 0; i < line.length(); i++)
			{
				char c = line.charAt(i);
				double cWidth = p.charWidth(c);
				double theta = startAngle + ((widthSoFar + cWidth / 2.0) / totalWidth) * (angleRange * 2.0);

				// Calculate the position of the character's bounding box
				double charX = textCenter.x - (cWidth / 2.0);
				double charY = textCenter.y - ascent; // y is baseline, so move up by ascent for top of char

				// Create a temporary RotatedRectangle for the character
				RotatedRectangle charRect = new RotatedRectangle(charX, charY, cWidth + adjustedSpacing, ascent + descent, theta, circleCenter.x, circleCenter.y);

				// Get the axis-aligned bounding box of the rotated character
				Rectangle charBounds = charRect.getBounds();
				boundsSoFar = charBounds.add(boundsSoFar);

				widthSoFar += p.charWidth(c) + adjustedSpacing;
			}
		}
		else
		{
			// Concave up. Curve along the ascender line.
			radius = (totalWidth / 2.0) / angleRange;
			circleCenter = textCenter.add(new Point(0.0, -radius));

			double startAngle = -angleRange;
			double widthSoFar = 0.0;

			for (int i = 0; i < line.length(); i++)
			{
				char c = line.charAt(i);
				double cWidth = p.charWidth(c);
				double theta = startAngle + ((widthSoFar + cWidth / 2.0) / totalWidth) * (angleRange * 2.0);

				// Calculate the position of the character's bounding box
				double charX = textCenter.x - (cWidth / 2.0);
				double charY = textCenter.y - ascent;

				// Create a temporary RotatedRectangle for the character
				// Note: The rotation is -theta for concave up as per drawStringCurved
				RotatedRectangle charRect = new RotatedRectangle(charX, charY, cWidth + adjustedSpacing, ascent + descent, -theta, circleCenter.x, circleCenter.y - ascent);

				// Get the axis-aligned bounding box of the rotated character
				Rectangle charBounds = charRect.getBounds();
				boundsSoFar = charBounds.add(boundsSoFar);

				widthSoFar += p.charWidth(c) + adjustedSpacing;
			}
		}

		return boundsSoFar;
	}

	/**
	 *
	 * Side effect: This adds a new MapText to mapTexts.
	 *
	 * @return True iff text was drawn.
	 */
	private boolean drawNameFitIntoCenters(Image map, Painter p, String name, Set<Point> centerLocations, WorldGraph graph, boolean enableBoundsChecking, TextType textType)
	{
		if (name.isEmpty())
			return false;

		Point centroid = findCentroid(centerLocations);

		MapText text = createMapText(name, centroid, 0.0, textType);
		if (drawNameSplitIfNeeded(map, p, graph, 0.0, enableBoundsChecking, null, text, true, null))
		{
			mapTexts.add(text);
			return true;
		}
		if (centerLocations.size() > 0)
		{
			// Try random locations to try to find a place to fit the text.
			Point[] locationsArray = centerLocations.toArray(new Point[centerLocations.size()]);
			for (@SuppressWarnings("unused")
			int i : new Range(30))
			{
				// Select a few random locations and choose the one closest to
				// the centroid.
				List<Point> samples = new ArrayList<>(3);
				for (@SuppressWarnings("unused")
				int sampleNumber : new Range(5))
				{
					samples.add(locationsArray[r.nextInt(locationsArray.length)]);
				}

				Point loc = Helper.maxItem(samples, (point1, point2) -> -Double.compare(point1.distanceTo(centroid), point2.distanceTo(centroid)));

				text = createMapText(name, loc, 0.0, textType);
				if (drawNameSplitIfNeeded(map, p, graph, 0.0, enableBoundsChecking, null, text, true, null))
				{
					mapTexts.add(text);
					return true;
				}
			}
		}
		return false;
	}

	public static Point rotate(Point point, Point pivot, double angle)
	{
		double sin = Math.sin(angle);
		double cos = Math.cos(angle);
		double newX = (cos * (point.x - pivot.x)) - (sin * (point.y - pivot.y)) + pivot.x;
		double newY = (sin * (point.x - pivot.x)) + (cos * (point.y - pivot.y)) + pivot.y;
		return new Point(newX, newY);
	}

	private Pair<String> addLineBreakNearMiddle(String name)
	{
		int start = name.length() / 2;
		int closestL = start;
		for (; closestL >= 0; closestL--)
			if (name.charAt(closestL) == ' ')
				break;
		int closestR = start;
		for (; closestR < name.length(); closestR++)
			if (name.charAt(closestR) == ' ')
				break;
		int pivot;
		if (Math.abs(closestL - start) < Math.abs(closestR - start))
			pivot = closestL;
		else
			pivot = closestR;
		String nameLine1 = name.substring(0, pivot);
		String nameLine2 = name.substring(pivot + 1);
		return new Pair<>(nameLine1, nameLine2);
	}

	public static int getFontHeight(Painter painter)
	{
		return painter.getFontAscent() + painter.getFontDescent();
	}

	/**
	 * The one or two lines a piece of text is drawn on.
	 *
	 * @param line2
	 *            Null when the text is drawn on one line.
	 */
	private record TextLines(String line1, String line2)
	{
	}

	/**
	 * Decides whether a piece of text is drawn on one line or two, and splits it if two. Text whose line break is Auto is split when drawing
	 * it on one line would cross a boundary (see {@link #overlapsBoundaryThatShouldCauseLineSplit}).
	 *
	 * @param graph
	 *            May be null, in which case Auto text is drawn on one line.
	 */
	private TextLines chooseLines(MapText text, double riseOffset, Painter p, WorldGraph graph)
	{
		boolean hasMultipleWords = text.value.trim().split(" ").length > 1;
		if (text.lineBreak == LineBreak.Auto)
		{
			if (hasMultipleWords && wouldOneLineCrossBoundary(text, riseOffset, p, graph))
			{
				return splitIntoTwoLines(text.value);
			}
			return new TextLines(text.value, null);
		}
		else if (text.lineBreak == LineBreak.One_line || !hasMultipleWords)
		{
			return new TextLines(text.value, null);
		}
		else if (text.lineBreak == LineBreak.Two_lines)
		{
			return splitIntoTwoLines(text.value);
		}
		else
		{
			throw new IllegalArgumentException("Unrecognized text line break value for text '" + text.value + "'. Line break value: " + text.lineBreak);
		}
	}

	private TextLines splitIntoTwoLines(String value)
	{
		Pair<String> lines = addLineBreakNearMiddle(value);
		// A split that leaves either line empty is drawn on one line.
		if (lines.getFirst().isEmpty())
		{
			return new TextLines(lines.getSecond(), null);
		}
		return new TextLines(lines.getFirst(), lines.getSecond().isEmpty() ? null : lines.getSecond());
	}

	/**
	 * Whether the given text, drawn on one line, would cross a boundary that makes Auto text split onto two lines.
	 *
	 * @param graph
	 *            May be null, in which case this returns false.
	 */
	private boolean wouldOneLineCrossBoundary(MapText text, double riseOffset, Painter p, WorldGraph graph)
	{
		if (graph == null)
		{
			return false;
		}
		setFontForText(p, text);
		Point oneLineLocation = getTextLocationWithRiseOffset(text, text.value, null, riseOffset, p);
		Rectangle oneLineBounds = getLine1BoundsWithoutCurvatureOrSpacing(text.value, oneLineLocation, p, false);
		oneLineBounds = expandBoundsToIncludeCurvatureAndSpacing(oneLineBounds, text, text.value, p);
		return overlapsBoundaryThatShouldCauseLineSplit(oneLineBounds, oneLineLocation, text.angle, text.type, graph);
	}

	/**
	 * Draws the given name at the given location (centroid). If the name cannot be drawn on one line and still fit with the given
	 * locations, then it will be drawn on 2 lines.
	 *
	 * The actual drawing step is skipped if settings.drawText = false.
	 *
	 * @return True iff text was drawn.
	 */
	private boolean drawNameSplitIfNeeded(Image map, Painter p, WorldGraph graph, double riseOffset, boolean enableBoundsChecking, RotatedRectangle areaToIgnoreInBoundsChecks, MapText text,
			boolean allowNegatingRizeOffset, Point drawOffset)
	{
		TextLines lines = chooseLines(text, riseOffset, p, graph);
		return drawNameRotated(map, p, graph, riseOffset, enableBoundsChecking, areaToIgnoreInBoundsChecks, text, lines.line1(), lines.line2(), allowNegatingRizeOffset, drawOffset);
	}

	/**
	 * Draws the given name at the centroid of the given plateCenters. The angle the name is drawn at is the least squares line through the
	 * plate centers. This does not break text into multiple lines.
	 *
	 * Side effect: This adds a new MapText to mapTexts.
	 *
	 * @param riseOffset
	 *            The text will be raised (positive y) by this much distance above the centroid when drawn. The rotation will be applied to
	 *            this location. If there is already a name drawn above the object, I try negating the riseOffset to draw the name below it.
	 *            Positive y is down.
	 */
	private void drawNameRotated(Image map, Painter p, WorldGraph graph, String name, Set<Point> locations, double riseOffset, boolean enableBoundsChecking,
			RotatedRectangle areaToIgnoreInBoundsChecks, TextType type)
	{
		if (name.isEmpty())
			return;

		Point centroid = findCentroid(locations);

		SimpleRegression regression = new SimpleRegression();
		for (Point point : locations)
		{
			regression.addObservation(new double[] { point.x }, point.y);
		}
		double angle;
		try
		{
			regression.regress();

			// Find the angle to rotate the text to.
			double y0 = regression.predict(0);
			double y1 = regression.predict(1);
			// The documentation for SimpleRegression says "If this method is invoked before a model can be estimated, Double,NaN is
			// returned."
			if (Double.isNaN(y0) || Double.isNaN(y1))
			{
				angle = Math.PI / 2.0;
			}
			else
			{
				// Move the intercept to the origin.
				y1 -= y0;
				y0 = 0;
				angle = Math.atan(y1 / 1.0);
			}
		}
		catch (NoDataException e)
		{
			// This happens if the regression had only 2 or fewer points.
			angle = 0;
		}

		MapText text = createMapText(name, centroid, angle, type);
		if (drawNameRotated(map, p, graph, riseOffset, enableBoundsChecking, areaToIgnoreInBoundsChecks, text, null))
		{
			mapTexts.add(text);
		}
	}

	/**
	 * Draws the given name at the given location (centroid), at the given angle.
	 *
	 * If settings.drawText = false, then this method will not do the actual text writing, but will still update the MapText text.
	 *
	 * @param riseOffset
	 *            The text will be raised (positive y) by this much distance above the centroid when drawn. The rotation will be applied to
	 *            this location. If there is already a name drawn above the object, I try negating the riseOffset to draw the name below it.
	 *            Positive y is down.
	 * @return true iff the text was drawn.
	 */
	private boolean drawNameRotated(Image map, Painter p, WorldGraph graph, double riseOffset, boolean enableBoundsChecking, RotatedRectangle areaToIgnoreInBoundsChecks, MapText text,
			Point drawOffset)
	{
		return drawNameSplitIfNeeded(map, p, graph, riseOffset, enableBoundsChecking, areaToIgnoreInBoundsChecks, text, true, drawOffset);
	}

	/**
	 * The bounds of a laid-out piece of text, and what its background draws, in the text's unrotated frame.
	 */
	private static final class TextGeometry
	{
		final Point pivot;
		final Rectangle bounds1WithoutCurvature;
		final Rectangle bounds1;
		final Rectangle bounds2WithoutCurvature;
		final Rectangle bounds2;
		final List<LineLayout> layouts;
		final TextBackgroundDrawer.Shape shape;
		/**
		 * Everything the text and its background draw on, not counting fade. The second is null for text on one line.
		 */
		final Rectangle extent1;
		final Rectangle extent2;
		final int fontHeight;

		TextGeometry(Point pivot, Rectangle bounds1WithoutCurvature, Rectangle bounds1, Rectangle bounds2WithoutCurvature, Rectangle bounds2, List<LineLayout> layouts,
				TextBackgroundDrawer.Shape shape, Rectangle extent1, Rectangle extent2, int fontHeight)
		{
			this.pivot = pivot;
			this.bounds1WithoutCurvature = bounds1WithoutCurvature;
			this.bounds1 = bounds1;
			this.bounds2WithoutCurvature = bounds2WithoutCurvature;
			this.bounds2 = bounds2;
			this.layouts = layouts;
			this.shape = shape;
			this.extent1 = extent1;
			this.extent2 = extent2;
			this.fontHeight = fontHeight;
		}

		Rectangle getExtent()
		{
			return extent1.add(extent2);
		}
	}

	/**
	 * Lays out a piece of text on the given lines. Returns null if the text is too small to draw.
	 */
	private TextGeometry layOutText(MapText text, String line1, String line2, double riseOffset, Painter p)
	{
		setFontForText(p, text);

		Point pivot = getTextLocationWithRiseOffset(text, line1, line2, riseOffset, p);

		Rectangle bounds1WithoutCurvature = getLine1BoundsWithoutCurvatureOrSpacing(line1, pivot, p, line2 != null);
		Rectangle bounds1 = expandBoundsToIncludeCurvatureAndSpacing(bounds1WithoutCurvature, text, line1, p);
		Rectangle bounds2WithoutCurvature = getLine2BoundsWithoutCurvatureOrSpacing(line2, pivot, p);
		Rectangle bounds2 = bounds2WithoutCurvature == null ? null : expandBoundsToIncludeCurvatureAndSpacing(bounds2WithoutCurvature, text, line2, p);

		if (bounds1 == null)
		{
			return null;
		}
		Dimension line1Size = bounds1.size();
		if (line1Size.width == 0 || line1Size.height == 0)
		{
			// The text is too small to draw.
			return null;
		}

		Dimension line2Size = bounds2 == null ? null : bounds2.size();
		if (line2Size != null && (line2Size.width == 0 || line2Size.height == 0))
		{
			// There is a second line, and it's too small to draw.
			return null;
		}

		int fontHeight = getFontHeight(p);
		// The text starts are calculated based on the line bounds without curvature because the line bounds with curvature depend on the text
		// start.
		List<LineLayout> layouts = new ArrayList<>(2);
		layouts.add(TextBackgroundDrawer.layoutLine(p, line1, new Point(bounds1WithoutCurvature.x, bounds1WithoutCurvature.y + p.getFontAscent()), text.curvature, text.spacing));
		if (line2 != null)
		{
			layouts.add(TextBackgroundDrawer.layoutLine(p, line2, new Point(bounds2WithoutCurvature.x, bounds2WithoutCurvature.y + p.getFontAscent()), text.curvature, text.spacing));
		}

		// The letters' drawn shapes can reach past the lines' bounds, such as a script font's swashes.
		Rectangle letters1 = bounds1.add(TextBackgroundDrawer.getLetterShapeBounds(p, layouts.get(0)));
		Rectangle letters2 = bounds2 == null ? null : bounds2.add(TextBackgroundDrawer.getLetterShapeBounds(p, layouts.get(1)));

		TextBackground background = text.style.background;
		TextBackgroundDrawer.Shape shape = null;
		Rectangle extent1 = letters1;
		Rectangle extent2 = letters2;
		if (background.effect.isShape())
		{
			shape = TextBackgroundDrawer.createShape(background, layouts, Arrays.asList(bounds1, bounds2), fontHeight, pivot, text.backgroundSeed);
			if (shape != null && shape.bounds != null)
			{
				// The shape surrounds both lines, so the first line's extent holds all of it.
				extent1 = shape.bounds.add(letters1).add(letters2);
			}
		}
		else if (background.effect.isHalo())
		{
			extent1 = TextBackgroundDrawer.padForHalo(letters1, background, fontHeight);
			extent2 = TextBackgroundDrawer.padForHalo(letters2, background, fontHeight);
		}

		return new TextGeometry(pivot, bounds1WithoutCurvature, bounds1, bounds2WithoutCurvature, bounds2, layouts, shape, extent1, extent2, fontHeight);
	}

	private boolean drawNameRotated(Image map, Painter p, WorldGraph graph, double riseOffset, boolean enableBoundsChecking, RotatedRectangle areaToIgnoreInBoundsChecks, MapText text,
			String line1, String line2, boolean allowNegatingRizeOffset, Point drawOffset)
	{
		if (line2 != null && line2.isEmpty())
		{
			line2 = null;
		}

		if (drawOffset == null)
		{
			drawOffset = new Point(0, 0);
		}

		TextGeometry geometry = layOutText(text, line1, line2, riseOffset, p);
		if (geometry == null)
		{
			return false;
		}
		Point pivot = geometry.pivot;

		// Rotate the bounds for the text. Use rotated rectangles rather than p's transform because we need to not include drawOffset when
		// rotating.
		RotatedRectangle area1 = new RotatedRectangle(geometry.extent1, text.angle, pivot);
		RotatedRectangle area2 = geometry.extent2 == null ? null : new RotatedRectangle(geometry.extent2, text.angle, pivot);
		// Make sure we don't draw on top of existing text.
		if (enableBoundsChecking)
		{
			boolean overlapsExistingTextOrCityOrIsOffMap = overlapsExistingTextOrCityOrIsOffMap(area1, areaToIgnoreInBoundsChecks)
					|| (area2 != null && overlapsExistingTextOrCityOrIsOffMap(area2, areaToIgnoreInBoundsChecks));
			boolean overlapsRegionLakeOrCoastline = overlapsBoundaryThatShouldCauseLineSplit(geometry.bounds1, pivot, text.angle, text.type, graph)
					|| overlapsBoundaryThatShouldCauseLineSplit(geometry.bounds2, pivot, text.angle, text.type, graph);
			boolean isTypeAllowedToCrossBoundaries = text.type == TextType.Title || text.type == TextType.Region || text.type == TextType.City || text.type == TextType.Mountain_range;

			if (overlapsExistingTextOrCityOrIsOffMap || overlapsRegionLakeOrCoastline)
			{
				// If there is a riseOffset, try negating it to put the name
				// below the object instead of above.
				if (riseOffset != 0.0 && allowNegatingRizeOffset)
				{
					if (drawNameSplitIfNeeded(map, p, graph, -riseOffset, enableBoundsChecking, null, text, false, drawOffset))
					{
						return true;
					}
					else if (overlapsExistingTextOrCityOrIsOffMap || !isTypeAllowedToCrossBoundaries)
					{
						// Give up
						return false;
					}
					// Otherwise, allow the text to draw.
				}
				else
				{
					// I'm checking allowNegatingRizeOffset below to make sure this isn't the recursive call from above.
					if (!(allowNegatingRizeOffset && !overlapsExistingTextOrCityOrIsOffMap && isTypeAllowedToCrossBoundaries))
					{
						// Give up
						return false;
					}
					// Otherwise, allow the text to draw.
				}
			}
		}

		text.line1Bounds = area1;
		text.line2Bounds = area2;
		if (riseOffset != 0)
		{
			// Update the text location with the offset. This only happens when generating new text, not when making changes in the
			// editor.
			text.location = new Point(pivot.x / settings.resolution, pivot.y / settings.resolution);
		}

		if (settings.drawText)
		{
			drawTextAndBackground(map, p, text, geometry, drawOffset, true);
		}

		return true;
	}

	/**
	 * Draws a laid-out piece of text and its background.
	 *
	 * @param drawFade
	 *            Whether to draw background fade, which needs landAndOceanBackground.
	 */
	private void drawTextAndBackground(Image map, Painter p, MapText text, TextGeometry geometry, Point drawOffset, boolean drawFade)
	{
		TextBackground background = text.style.background;
		Point pivotMinusDrawOffset = geometry.pivot.subtract(drawOffset);
		Transform orig = p.getTransform();
		try
		{
			// Draw background blending before drawing any lines of text so that the background blending for line 2 cannot erase the text
			// from line 1.
			if (drawFade && background.getFadeBehindToDraw() > 0)
			{
				p.rotate(text.angle, pivotMinusDrawOffset.x, pivotMinusDrawOffset.y);
				Point textStartLine1 = new Point(geometry.bounds1WithoutCurvature.x - drawOffset.x, geometry.bounds1WithoutCurvature.y - drawOffset.y + p.getFontAscent());
				drawBackgroundBlendingForText(map, p, text, textStartLine1, geometry.bounds1WithoutCurvature, geometry.bounds1, geometry.layouts.get(0).text, pivotMinusDrawOffset);
				if (geometry.layouts.size() > 1)
				{
					Point textStartLine2 = new Point(geometry.bounds2WithoutCurvature.x - drawOffset.x, geometry.bounds2WithoutCurvature.y - drawOffset.y + p.getFontAscent());
					drawBackgroundBlendingForText(map, p, text, textStartLine2, geometry.bounds2WithoutCurvature, geometry.bounds2, geometry.layouts.get(1).text,
							pivotMinusDrawOffset);
				}
				p.setTransform(orig);
			}

			p.rotate(text.angle, pivotMinusDrawOffset.x, pivotMinusDrawOffset.y);
			p.translate(-drawOffset.x, -drawOffset.y);

			if (background.effect.isHalo())
			{
				TextBackgroundDrawer.drawHalo(p, orig, background, geometry.layouts, geometry.getExtent(), text.angle, geometry.pivot, drawOffset, geometry.fontHeight);
			}
			else if (geometry.shape != null)
			{
				geometry.shape.draw(p);
			}

			p.setColor(text.style.color);
			Font boldFont = null;
			if (background.effect == TextBackgroundEffect.BoldBackground)
			{
				Font font = p.getFont();
				boldFont = font.deriveFont(font.isItalic() ? FontStyle.BoldItalic : FontStyle.Bold, font.getSize());
			}
			for (LineLayout layout : geometry.layouts)
			{
				TextBackgroundDrawer.drawLetters(p, layout, boldFont, background.color);
			}
		}
		finally
		{
			p.setTransform(orig);
		}
	}

	/**
	 * Draws the given text, as it would be drawn on the map with the given graph, onto a new transparent image that covers it, at the
	 * resolution of this text drawer's settings. Background fade is not drawn. Returns null if there is nothing to draw.
	 *
	 * @param graph
	 *            Decides where Auto line breaks go. May be null, in which case Auto text is drawn on one line.
	 * @return The image, and where its upper left corner is in the map.
	 */
	public Tuple2<Image, IntPoint> drawTextOntoNewImage(MapText text, WorldGraph graph)
	{
		if (text.value == null || text.value.trim().isEmpty())
		{
			return null;
		}
		try (Image scratch = Image.create(1, 1, ImageType.ARGB); Painter scratchPainter = scratch.createPainter())
		{
			TextLines lines = chooseLines(text, 0.0, scratchPainter, graph);
			TextGeometry geometry = layOutText(text, lines.line1(), lines.line2(), 0.0, scratchPainter);
			if (geometry == null)
			{
				return null;
			}
			// Halos and bold backgrounds reach a little past the extent's rounding, so leave a margin.
			Rectangle bounds = new RotatedRectangle(geometry.getExtent(), text.angle, geometry.pivot).getBounds().pad(4 + geometry.fontHeight * 0.2);
			IntPoint upperLeft = new IntPoint((int) Math.floor(bounds.x), (int) Math.floor(bounds.y));
			int width = (int) Math.ceil(bounds.width) + 1;
			int height = (int) Math.ceil(bounds.height) + 1;
			Image result = Image.create(width, height, ImageType.ARGB);
			try (Painter p = result.createPainter(DrawQuality.High))
			{
				setFontForText(p, text);
				drawTextAndBackground(result, p, text, geometry, new Point(upperLeft.x, upperLeft.y), false);
			}
			return new Tuple2<>(result, upperLeft);
		}
	}

	private Point getTextLocationWithRiseOffset(MapText text, @SuppressWarnings("unused") String line1, String line2, double riseOffset, Painter p)
	{
		if (line2 != null && line2.isEmpty())
		{
			line2 = null;
		}

		Point textLocation = new Point(text.location.x * settings.resolution, text.location.y * settings.resolution);

		int fontHeight = getFontHeight(p);
		// Increase the rise offset to account for the font size.
		double riseOffsetToUse = riseOffset;
		if (riseOffsetToUse > 0.0)
		{
			if (line2 == null)
			{
				riseOffsetToUse += fontHeight / 2;
			}
			else
			{
				riseOffsetToUse += fontHeight;
			}
		}
		else if (riseOffsetToUse < 0.0)
		{
			if (line2 == null)
			{
				riseOffsetToUse -= fontHeight / 2;
			}
			else
			{
				riseOffsetToUse -= fontHeight;
			}
		}

		Point offset = new Point(riseOffsetToUse * Math.sin(text.angle), -riseOffsetToUse * Math.cos(text.angle));
		return new Point(textLocation.x - offset.x, textLocation.y - offset.y);
	}

	private Rectangle getLine1BoundsWithoutCurvatureOrSpacing(String line1, Point pivot, Painter p, boolean hasLine2)
	{
		int fontHeight = getFontHeight(p);
		Dimension size = getTextDimensions(line1, p);
		return new Rectangle(pivot.x - size.width / 2, pivot.y - size.height / 2 - (hasLine2 ? fontHeight / 2 : 0), size.width, size.height);
	}

	private Rectangle getLine2BoundsWithoutCurvatureOrSpacing(String line2, Point pivot, Painter p)
	{
		if (line2 == null)
		{
			return null;
		}

		int fontHeight = getFontHeight(p);
		Dimension size = getTextDimensions(line2, p);
		return new Rectangle(pivot.x - size.width / 2, pivot.y - (size.height / 2) + fontHeight / 2, size.width, size.height);
	}

	private static Dimension getTextDimensions(String text, Painter painter)
	{
		return new Dimension(painter.stringWidth(text), painter.getFontAscent() + painter.getFontDescent());
	}

	public static Dimension getTextDimensions(String text, Font font)
	{
		try (Painter p = Image.create(1, 1, ImageType.ARGB).createPainter())
		{
			p.setFont(font);
			return getTextDimensions(text, p);
		}
	}

	/**
	 * Recomputes the bounds on every MapText in {@link MapEdits#text} if {@link MapEdits#textBoundsNeedRefresh} is true. Called at the end
	 * of every incremental draw (see {@code MapCreator#incrementalUpdateBounds}) so that: - texts on an edits-from-disk MapEdits get bounds
	 * before the first interactive click, - undo/redo restorations get fresh bounds at the current resolution (the just-restored bounds may
	 * have been computed at a different displayQualityScale, or may be null for a text that was pasted into a snapshot before its bounds
	 * were ever drawn).
	 */
	public void updateTextBoundsIfNeeded(WorldGraph graph)
	{
		if (!settings.edits.textBoundsNeedRefresh)
		{
			return;
		}

		boolean originalDrawText = settings.drawText;
		try (Image fakeMapThatNothingShouldDrawOn = Image.create(1, 1, ImageType.ARGB))
		{
			settings.drawText = false;
			drawText(fakeMapThatNothingShouldDrawOn, graph, settings.edits.text, null);
		}
		finally
		{
			settings.drawText = originalDrawText;
		}
	}

	public Point findCentroid(Collection<Point> plateCenters)
	{
		Point centroid = new Point(0, 0);
		for (Point p : plateCenters)
		{
			centroid.x += p.x;
			centroid.y += p.y;
		}
		centroid.x /= plateCenters.size();
		centroid.y /= plateCenters.size();

		return centroid;
	}

	private boolean overlapsBoundaryThatShouldCauseLineSplit(Rectangle textBounds, Point pivot, double angle, TextType type, WorldGraph graph)
	{
		if (textBounds == null || graph == null)
		{
			return false;
		}

		// Use the water-check (canonical) resolution so text line-splitting doesn't change when the display quality changes.
		Center middleCenter = graph.findClosestCenter(textBounds.getCenter(), true, true);

		if (middleCenter == null)
		{
			return false;
		}
		final int checkFrequency = 10;
		for (double x = 0; x < textBounds.width; x += checkFrequency * settings.resolution)
		{
			if (x + checkFrequency * settings.resolution > textBounds.width)
			{
				// This is the final iteration. Change x to be at the end of the text box.
				x = textBounds.width;
			}

			for (double y = 0; y < textBounds.height; y += checkFrequency * settings.resolution)
			{
				if (y + checkFrequency * settings.resolution > textBounds.height)
				{
					// This is the final iteration. Change y to be at the bottom of the text box.
					y = textBounds.height;
				}

				Point point = rotate(new Point(textBounds.x + x, textBounds.y + y), pivot, angle);
				Center c = graph.findClosestCenter(point, true, true);

				if (c != null)
				{
					if (doCentersHaveBoundaryBetweenThem(middleCenter, c, settings, type))
					{
						return true;
					}
				}
			}
		}

		return false;
	}

	private boolean doCentersHaveBoundaryBetweenThem(Center c1, Center c2, MapSettings settings, TextType type)
	{
		if (c1.isWater && c2.isWater)
		{
			return false;
		}

		if (c1.isWater != c2.isWater)
		{
			return true;
		}

		if (!settings.drawRegionBoundaries || (type != TextType.Region))
		{
			return false;
		}

		return c1.region != c2.region;
	}

	private boolean overlapsExistingTextOrCityOrIsOffMap(RotatedRectangle bounds, RotatedRectangle areaToIgnore)
	{
		for (MapText mp : mapTexts)
		{
			// Ignore empty text and ignore edited text.
			if (mp.value.length() > 0)
			{
				if (mp.line1Bounds != null)
				{
					if (doAreasIntersect(bounds, mp.line1Bounds))
					{
						return true;
					}
				}

				if (mp.line2Bounds != null)
				{
					if (doAreasIntersect(bounds, mp.line2Bounds))
					{
						return true;
					}
				}
			}
		}

		for (RotatedRectangle a : cityAreas)
		{
			if (areaToIgnore != null && areaToIgnore.equals(a))
			{
				continue;
			}

			if (doAreasIntersect(bounds, a))
			{
				return true;
			}
		}

		return !graphBounds.contains(bounds.getBounds());
	}

	public static boolean doAreasIntersect(RotatedRectangle area1, RotatedRectangle area2)
	{
		if (area1 == null || area2 == null)
		{
			return false;
		}

		return area1.overlaps(area2);
	}

	/**
	 * Creates a new MapText for generated text, styled with the map's style for new text of its type.
	 */
	private MapText createMapText(String text, Point location, double angle, TextType type)
	{
		long backgroundSeed = Helper.mixSeed(settings.textRandomSeed + generatedTextCount++);
		return createMapText(text, location, angle, type, settings.resolution, settings.getDefaultTextStyle(type).copy(), settings.getDefaultTextLayout(type),
				backgroundSeed);
	}

	/**
	 * Creates a new MapText, taking the resolution its location is given at into account.
	 */
	public static MapText createMapText(String text, Point location, double angle, TextType type, double resolution, TextStyle style, TextLayoutSettings layout,
			long backgroundSeed)
	{
		// Divide by resolution so that the location does not depend on the resolution we're drawing at.
		return new MapText(text, new Point(location.x / resolution, location.y / resolution), angle, type, layout.lineBreak, layout.curvature, layout.spacing, style,
				backgroundSeed);
	}

	public void setMapTexts(CopyOnWriteArrayList<MapText> text)
	{
		this.mapTexts = text;
	}

}
