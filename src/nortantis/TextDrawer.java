package nortantis;

import nortantis.geom.Dimension;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.geom.RotatedRectangle;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.graph.voronoi.Edge;
import nortantis.graph.voronoi.VoronoiGraph;
import nortantis.platform.*;
import nortantis.util.*;
import org.apache.commons.math3.exception.NoDataException;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import java.util.*;
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
	private Font titleFontScaled;
	private Font regionFontScaled;
	private Font mountainRangeFontScaled;
	private Font otherMountainsFontScaled;
	private Font riverFontScaled;
	private Random r;
	private Font citiesFontScaled;
	/**
	 * The maximum angle that text can be curved. Note that changing this would require a conversion to existing maps because the editor
	 * only stores a number between -1 and 1 for text curvature, so changing this would change the angle of curved text on existing maps.
	 */
	private static final double maxTextCurveAngleRange = Math.PI;

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

		double sizeMultiplier = MapCreator.calcSizeMultiplierFromResolutionScale(settings.resolution);
		titleFontScaled = settings.titleFont.deriveFont(settings.titleFont.getStyle(), (float) (settings.titleFont.getSize() * sizeMultiplier));
		regionFontScaled = settings.regionFont.deriveFont(settings.regionFont.getStyle(), (float) (settings.regionFont.getSize() * sizeMultiplier));
		mountainRangeFontScaled = settings.mountainRangeFont.deriveFont(settings.mountainRangeFont.getStyle(), (float) (settings.mountainRangeFont.getSize() * sizeMultiplier));
		otherMountainsFontScaled = settings.otherMountainsFont.deriveFont(settings.otherMountainsFont.getStyle(), (float) (settings.otherMountainsFont.getSize() * sizeMultiplier));
		citiesFontScaled = settings.citiesFont.deriveFont(settings.citiesFont.getStyle(), (float) (settings.citiesFont.getSize() * sizeMultiplier));
		riverFontScaled = settings.riverFont.deriveFont(settings.riverFont.getStyle(), (float) (settings.riverFont.getSize() * sizeMultiplier));
	}

	public synchronized void drawTextFromEdits(Image map, Image landAndOceanBackground, WorldGraph graph, Rectangle drawBounds)
	{
		this.landAndOceanBackground = landAndOceanBackground;

		drawText(map, graph, settings.edits.text, drawBounds);

		this.landAndOceanBackground = null;
	}

	public void generateText(WorldGraph graph, Image map, NameCreator nameCreator, Image landAndOceanBackground, List<Set<Center>> mountainGroups, List<IconDrawTask> cityDrawTasks,
			List<Set<Center>> lakes)
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

		generateText(map, graph, nameCreator, mountainGroups, cityDrawTasks, lakes);

		this.landAndOceanBackground = null;
	}

	private void generateText(Image map, WorldGraph graph, NameCreator nameCreator, List<Set<Center>> mountainGroups, List<IconDrawTask> cityDrawTasks, List<Set<Center>> lakes)
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
				p.setColor(settings.textColor);

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

				setFontForTextType(p, TextType.Region);
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
					drawNameFitIntoCenters(map, p, name, locations, graph, settings.drawBoldBackground, true, TextType.Region);
				}

				for (Set<Center> mountainGroup : mountainGroups)
				{
					if (mountainGroup.size() >= mountainRangeMinSize)
					{
						setFontForTextType(p, TextType.Mountain_range);
						Set<Point> locations = extractLocationsFromCenters(mountainGroup);
						drawNameRotated(map, p, graph, nameCreator.generateNameOfType(TextType.Mountain_range, null, true), locations, 0.0, true, null, TextType.Mountain_range);
					}
					else
					{
						setFontForTextType(p, TextType.Other_mountains);
						if (mountainGroup.size() >= 2)
						{
							if (mountainGroup.size() == 2)
							{
								Point location = findCentroid(extractLocationsFromCenters(mountainGroup));
								MapText text = createMapText(nameCreator.generateNameOfType(TextType.Other_mountains, OtherMountainsType.Peaks, true), location, 0.0, TextType.Other_mountains);
								if (drawNameRotated(map, p, graph, twoMountainsYOffset * settings.resolution, true, null, text, false, null))
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
							if (drawNameRotated(map, p, graph, singleMountainYOffset * settings.resolution, true, null, text, false, null))
							{
								mapTexts.add(text);
							}
						}
					}
				}

				setFontForTextType(p, TextType.River);
				for (Set<Center> lake : lakes)
				{
					String name = nameCreator.generateNameOfType(TextType.Lake, null, true);
					Set<Point> locations = extractLocationsFromCenters(lake);
					drawNameRotated(map, p, graph, name, locations, 0.0, true, null, TextType.Lake);
				}

				List<River> rivers = findRivers(graph);
				for (River river : rivers)
				{
					if (river.size() >= riverMinLength && river.getWidth() >= riverMinWidth)
					{
						RiverType riverType = river.getWidth() >= largeRiverWidth ? RiverType.Large : RiverType.Small;

						Set<Point> locations = extractLocationsFromEdges(river.getSegmentForPlacingText());
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
					singleLineBounds = addBackgroundBlendingPadding(singleLineBounds, getFontHeight(p), text);

					Rectangle textBoundsAllLines;
					// Since it wouldn't be easy from here to figure out whether the text will draw onto one line or two, combine
					// the bounds for both cases if it's possible the text could be split.
					if ((text.lineBreak == LineBreak.Auto || text.lineBreak == LineBreak.Two_lines) && text.value.trim().contains(" "))
					{
						Pair<String> lines = addLineBreakNearMiddle(text.value);

						Rectangle line1Bounds = getLine1BoundsWithoutCurvatureOrSpacing(lines.getFirst(), textLocation, p, true);
						line1Bounds = expandBoundsToIncludeCurvatureAndSpacing(line1Bounds, text, lines.getFirst(), p);
						line1Bounds = addBackgroundBlendingPadding(line1Bounds, getFontHeight(p), text);

						Rectangle line2Bounds = getLine2BoundsWithoutCurvatureOrSpacing(lines.getSecond(), textLocation, p);
						line2Bounds = expandBoundsToIncludeCurvatureAndSpacing(line2Bounds, text, lines.getFirst(), p);
						line2Bounds = addBackgroundBlendingPadding(line2Bounds, getFontHeight(p), text);

						textBoundsAllLines = singleLineBounds.add(line1Bounds.add(line2Bounds));
					}
					else
					{
						textBoundsAllLines = singleLineBounds;
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
			bounds = addBackgroundBlendingPadding(bounds, getFontHeight(p), text);
			Rectangle boundingBox = new RotatedRectangle(bounds, text.angle, textLocation).getBounds();

			// Since it wouldn't be easy from here to figure out whether the text will draw onto one line or two, also add
			// the bounds when it splits under two lines.
			if ((text.lineBreak == LineBreak.Auto || text.lineBreak == LineBreak.Two_lines) && text.value.trim().contains(" "))
			{
				Pair<String> lines = addLineBreakNearMiddle(text.value);

				Rectangle line1Bounds = getLine1BoundsWithoutCurvatureOrSpacing(lines.getFirst(), textLocation, p, true);
				line1Bounds = expandBoundsToIncludeCurvatureAndSpacing(line1Bounds, text, lines.getFirst(), p);
				line1Bounds = addBackgroundBlendingPadding(line1Bounds, getFontHeight(p), text);

				boundingBox = boundingBox.add(new RotatedRectangle(line1Bounds, text.angle, textLocation).getBounds());

				Rectangle line2Bounds = getLine2BoundsWithoutCurvatureOrSpacing(lines.getSecond(), textLocation, p);
				line2Bounds = expandBoundsToIncludeCurvatureAndSpacing(line2Bounds, text, lines.getSecond(), p);
				boundingBox = boundingBox.add(new RotatedRectangle(line2Bounds, text.angle, textLocation).getBounds());
			}

			return boundingBox;
		}
	}

	private Rectangle addBackgroundBlendingPadding(Rectangle textBounds, int fontHeight, MapText text)
	{
		int padding = getBackgroundBlendingPadding(fontHeight, text);
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

	public Rectangle expandBoundsToIncludeText(List<MapText> mapTexts, WorldGraph graph, Rectangle bounds, MapSettings settings)
	{
		if (!settings.drawText)
		{
			return bounds;
		}

		Tuple1<Rectangle> wrapperToMakeCompilerHappy = new Tuple1<>(bounds);

		doForEachTextInBounds(mapTexts, bounds, (text, area) ->
		{
			if (text.lineBreak == LineBreak.Auto)
			{
				wrapperToMakeCompilerHappy.set(wrapperToMakeCompilerHappy.get().add(area.getBounds()));
			}
		});
		return wrapperToMakeCompilerHappy.get();
	}

	private void drawText(Image map, WorldGraph graph, List<MapText> textToDraw, Rectangle drawBounds)
	{
		try (Painter p = map.createPainter(DrawQuality.High))
		{
			Point drawOffset = drawBounds == null ? null : drawBounds.upperLeftCorner();

			doForEachTextInBounds(textToDraw, drawBounds, ((text, ignored) ->
			{
				if (text.type == TextType.Title)
				{
					drawNameSplitIfNeeded(map, p, graph, 0.0, false, null, text, settings.drawBoldBackground, true, drawOffset);
				}
				else if (text.type == TextType.City)
				{
					drawNameRotated(map, p, graph, 0, false, null, text, false, drawOffset);
				}
				else if (text.type == TextType.Region)
				{
					drawNameSplitIfNeeded(map, p, graph, 0.0, false, null, text, settings.drawBoldBackground, true, drawOffset);
				}
				else if (text.type == TextType.Mountain_range)
				{
					drawNameRotated(map, p, graph, 0, false, null, text, false, drawOffset);
				}
				else if (text.type == TextType.Other_mountains)
				{
					drawNameRotated(map, p, graph, 0, false, null, text, false, drawOffset);
				}
				else if (text.type == TextType.River)
				{
					drawNameRotated(map, p, graph, 0, false, null, text, false, drawOffset);
				}
				else if (text.type == TextType.Lake)
				{
					drawNameRotated(map, p, graph, 0, false, null, text, false, drawOffset);
				}
			}));
		}

		// Only mark this flag if we drew all text, not just an incremental update.
		if (drawBounds == null || drawBounds.equals(graph.bounds))
		{
			settings.edits.hasCreatedTextBounds = true;
		}
	}

	private void setFontForText(Painter p, MapText text)
	{
		if (text.fontOverride != null)
		{
			double sizeMultiplier = MapCreator.calcSizeMultiplierFromResolutionScale(settings.resolution);
			Font derived = text.fontOverride.deriveFont(text.fontOverride.getStyle(), (float) (text.fontOverride.getSize() * sizeMultiplier));
			p.setFont(derived);
		}
		else
		{
			setFontForTextType(p, text.type);
		}
	}

	private void setFontForTextType(Painter p, TextType type)
	{
		if (type == TextType.Title)
		{
			p.setFont(titleFontScaled);
		}
		else if (type == TextType.City)
		{
			p.setFont(citiesFontScaled);
		}
		else if (type == TextType.Region)
		{
			p.setFont(regionFontScaled);
		}
		else if (type == TextType.Mountain_range)
		{
			p.setFont(mountainRangeFontScaled);
		}
		else if (type == TextType.Other_mountains)
		{
			p.setFont(otherMountainsFontScaled);
		}
		else if (type == TextType.River)
		{
			p.setFont(riverFontScaled);
		}
		else if (type == TextType.Lake)
		{
			p.setFont(riverFontScaled);
		}
		else
		{
			throw new RuntimeException("Unknown text type: " + type);
		}
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
						settings.drawBoldBackground, true, TextType.Title))
				{
					return;
				}

				// The title didn't fit. Try drawing it with just a name.
				if (drawNameFitIntoCenters(map, p, nameCreator.generateNameOfType(TextType.Title, TitleType.NameOnly, true), extractLocationsFromCenters(plateAndWidth.getFirst().centers), graph,
						settings.drawBoldBackground, true, TextType.Title))
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

	private Set<Point> extractLocationsFromEdges(Collection<Edge> edges)
	{
		Set<Point> result = new TreeSet<Point>();
		for (Edge e : edges)
		{
			if (e.v0 != null)
			{
				result.add(e.v0.loc);
			}
			if (e.v1 != null)
			{
				result.add(e.v1.loc);
			}
		}
		return result;
	}

	/**
	 * For finding rivers.
	 */
	private List<River> findRivers(WorldGraph graph)
	{
		List<River> rivers = new ArrayList<>();
		Set<Corner> riversAlreadyFound = new HashSet<>();
		for (Corner corner : graph.corners)
		{
			if (corner.river > VoronoiGraph.riversThisSizeOrSmallerWillNotBeDrawn && !riversAlreadyFound.contains(corner))
			{
				River river = findRiver(riversAlreadyFound, corner);

				riversAlreadyFound.addAll(river.getCorners());
				rivers.add(river);
			}
		}

		return rivers;
	}

	private River findRiver(Set<Corner> riversAlreadyFound, Corner start)
	{
		// First, follow the river in every direction it flows to find its mouth (which is the end of the river that is the widest, which
		// should be at the ocean).
		List<Edge> options = new ArrayList<>();
		for (Edge e : start.protrudes)
		{
			if (e.river > VoronoiGraph.riversThisSizeOrSmallerWillNotBeDrawn && e.v0 != null && e.v1 != null)
			{
				options.add(e);
			}
		}
		sortByRiverWidth(options);
		if (options.size() == 0)
		{
			// This shouldn't happen because it means a corner is a river but is not connected to an edge that is a river.
			assert false;
			return new River();
		}
		else if (options.size() == 1)
		{
			// start is the head of a river
			Corner downStream = options.get(0).getOtherCorner(start);
			return followRiver(riversAlreadyFound, start, downStream);
		}
		else
		{
			// Follow the two directions that make the widest rivers, then combine them into one river.
			River river1 = followRiver(riversAlreadyFound, start, options.get(0).getOtherCorner(start));
			River river2 = followRiver(riversAlreadyFound, start, options.get(1).getOtherCorner(start));
			river2.reverse();
			river2.addAll(river1);
			return river2;
		}

	}

	/**
	 * Searches along edges to find corners which are connected by a river. If the river forks, only one direction is followed (the wider
	 * one).
	 *
	 * @param last
	 *            The search will not go in the direction of this corner.
	 * @param head
	 *            The search will go in the direction of this corner.
	 * @return A set of corners which form a river.
	 */
	private River followRiver(Set<Corner> riversAlreadyFound, Corner last, Corner head)
	{
		assert last != null;
		assert head != null;
		assert !head.equals(last);

		Edge lastToHead = VoronoiGraph.edgeWithCorners(last, head);
		River result = new River();
		result.add(lastToHead);

		List<Edge> riverEdges = new ArrayList<>();
		for (Edge e : head.protrudes)
		{
			if (e.isRiver() && e != lastToHead)
			{
				riverEdges.add(e);
			}
		}

		if (riverEdges.size() == 0)
		{
			// Base case. We're at the end of the river.
			return result;
		}
		else
		{
			// There are more than 2 river edges connected to head.

			// Sort the river edges by river width.
			sortByRiverWidth(riverEdges);
			Edge widest = riverEdges.get(0);

			Corner nextHead = widest.v0 == head ? widest.v1 : widest.v0;

			if (nextHead == null)
			{
				// The river goes to the edge of the map.
				return result;
			}

			if (riversAlreadyFound.contains(nextHead))
			{
				// We've run into another river that has already been found.
				result.add(widest);
				return result;
			}

			result.addAll(followRiver(riversAlreadyFound, head, nextHead));
			return result;
		}
	}

	private void sortByRiverWidth(List<Edge> edges)
	{
		if (edges.size() > 1)
		{
			Collections.sort(edges, new Comparator<Edge>()
			{
				public int compare(Edge e0, Edge e1)
				{
					return -Integer.compare(e0.river, e1.river);
				}
			});
		}
	}

	/**
	 * Draws the given name to the map with the area around the name drawn from landAndOceanBackground to make it readable when the name is
	 * drawn on top of mountains or trees.
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
				drawStringCurved(bP, text, name, drawPointForMask, false);
			}

			// Use convolution to make a hazy background for the text.
			float[][] kernel = ImageHelper.getInstance().createGaussianKernel(kernelSize);
			try (Image haze1 = ImageHelper.getInstance().convolveGrayscale(textBG, kernel, true, false))
			{
				// Threshold it and convolve it again to make the haze bigger.
				ImageHelper.getInstance().threshold(haze1, 1);
				try (Image haze2 = ImageHelper.getInstance().convolveGrayscale(haze1, kernel, true, false))
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
		int kernelSize = (int) ((13.0 / 54.0) * text.backgroundFade * fontHeight);
		return kernelSize;
	}

	private int getBackgroundBlendingPadding(int fontHeight, MapText text)
	{
		return getBackgroundBlendingKernelSize(fontHeight, text);
	}

	/**
	 * Draws a curved string.
	 *
	 * @param p
	 *            Context for drawing.
	 * @param name
	 *            Text to draw
	 * @param textStart
	 *            location to start drawing the text at (before applying curvature)
	 */
	private void drawStringCurved(Painter p, MapText text, String name, Point textStart, boolean drawBoldBackground)
	{
		if (name == null || name.isEmpty())
			return;

		if (Math.abs(text.curvature) <= 0.001 && text.spacing == 0 && !drawBoldBackground)
		{
			// Special case. Draw using this method for performance.
			p.drawString(name, textStart.x, textStart.y);
			return;
		}

		drawStringWithOptionalBoldBackground(p, name, textStart, text.curvature, text.spacing, drawBoldBackground, text.boldBackgroundColorOverride);
	}

	private void drawStringWithOptionalBoldBackground(Painter p, String text, Point textStart, double curvature, int spacing, boolean drawBoldBackground, Color boldBackgroundColorOverride)

	{
		if (text.isEmpty())

			return;

		// We're assuming p's transform is already rotated. As such, we don't
		// need to handle rotation when drawing text here.

		Font original = p.getFont();

		Color originalColor = p.getColor();

		FontStyle style = original.isItalic() ? FontStyle.BoldItalic : FontStyle.Bold;

		Font background = p.getFont().deriveFont(style, p.getFont().getSize());

		double ascent = p.getFontAscent();
		double adjustedSpacing = text.length() < 2 ? 0.0 : spacing * ascent * spacingScale;
		double startXDiffFromSpacing = (adjustedSpacing * (text.length() - 1)) / 2.0;

		if (Math.abs(curvature) <= 0.001)
		{
			// Special case: no curvature
			Point curLoc = new Point(textStart.x - startXDiffFromSpacing, textStart.y);
			for (int i : new Range(text.length()))
			{
				if (drawBoldBackground)
				{
					p.setFont(background);
					p.setColor(boldBackgroundColorOverride != null ? boldBackgroundColorOverride : settings.boldBackgroundColor);
					p.drawString("" + text.charAt(i), curLoc.x, curLoc.y);
				}

				p.setFont(original);
				p.setColor(originalColor);
				p.drawString("" + text.charAt(i), curLoc.x, curLoc.y);

				int charWidth = p.charWidth(text.charAt(i));
				curLoc = new Point(curLoc.x + charWidth + adjustedSpacing, curLoc.y);
			}
		}
		else
		{
			Transform orig = p.getTransform();
			try
			{
				double totalWidth = p.stringWidth(text) + (text.length() > 0 ? (text.length() - 1) * adjustedSpacing : 0.0);
				Point textCenter = textStart.add(new Point((totalWidth / 2.0) - startXDiffFromSpacing, 0));
				double angleRange = Math.abs(curvature * maxTextCurveAngleRange);
				double radius;
				Point circleCenter;
				radius = (totalWidth / 2.0) / angleRange;

				if (curvature > 0)
				{
					// Concave down. Curve along the baseline of the text.
					circleCenter = textCenter.add(new Point(0.0, radius));
				}
				else
				{
					// Concave up. Curve along the ascender line.
					circleCenter = textCenter.add(new Point(0.0, -radius));
				}

				double startAngle = -angleRange;
				double widthSoFar = 0.0;

				for (int i = 0; i < text.length(); i++)
				{
					char c = text.charAt(i);
					double cWidth = p.charWidth(c);
					double theta = startAngle + ((widthSoFar + cWidth / 2.0) / totalWidth) * (angleRange * 2.0);

					if (drawBoldBackground)
					{
						p.setFont(background);
						p.setColor(boldBackgroundColorOverride != null ? boldBackgroundColorOverride : settings.boldBackgroundColor);
						if (curvature > 0)
						{
							p.rotate(theta, circleCenter);
							p.drawString(c + "", textCenter.x - (cWidth / 2.0), textCenter.y);
						}
						else
						{
							p.translate(0, -ascent);
							p.rotate(-theta, circleCenter);
							p.drawString(c + "", textCenter.x - (cWidth / 2.0), textCenter.y + ascent);
						}
						p.setTransform(orig);
					}

					// Draw foreground text
					p.setFont(original);
					p.setColor(originalColor);
					if (curvature > 0)
					{
						p.rotate(theta, circleCenter);
						p.drawString(c + "", textCenter.x - (cWidth / 2.0), textCenter.y);
					}
					else
					{
						p.translate(0, -ascent);
						p.rotate(-theta, circleCenter);
						p.drawString(c + "", textCenter.x - (cWidth / 2.0), textCenter.y + ascent);
					}
					p.setTransform(orig);

					widthSoFar += p.charWidth(c) + adjustedSpacing;
				}
			}
			finally
			{
				p.setTransform(orig);
				p.setFont(original); // Ensure font and color are reset
				p.setColor(originalColor);
			}
		}
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
		double adjustedSpacing = line.length() < 2 ? 0.0 : text.spacing * ascent * spacingScale;
		double startXDiffFromSpacing = (adjustedSpacing * line.length() - 1) / 2.0;
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
		double angleRange = Math.abs(text.curvature * maxTextCurveAngleRange); // Assuming maxTextCurveAngleRange is defined
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
	private boolean drawNameFitIntoCenters(Image map, Painter p, String name, Set<Point> centerLocations, WorldGraph graph, boolean boldBackground, boolean enableBoundsChecking, TextType textType)
	{
		if (name.isEmpty())
			return false;

		Point centroid = findCentroid(centerLocations);

		MapText text = createMapText(name, centroid, 0.0, textType);
		if (drawNameSplitIfNeeded(map, p, graph, 0.0, enableBoundsChecking, null, text, boldBackground, true, null))
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
				if (drawNameSplitIfNeeded(map, p, graph, 0.0, enableBoundsChecking, null, text, boldBackground, true, null))
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
	 * Draws the given name at the given location (centroid). If the name cannot be drawn on one line and still fit with the given
	 * locations, then it will be drawn on 2 lines.
	 *
	 * The actual drawing step is skipped if settings.drawText = false.
	 *
	 * @return True iff text was drawn.
	 */
	private boolean drawNameSplitIfNeeded(Image map, Painter p, WorldGraph graph, double riseOffset, boolean enableBoundsChecking, RotatedRectangle areaToIgnoreInBoundsChecks, MapText text,
			boolean boldBackground, boolean allowNegatingRizeOffset, Point drawOffset)
	{
		boolean hasMultipleWords = text.value.trim().split(" ").length > 1;
		if (text.lineBreak == LineBreak.Auto)
		{
			setFontForText(p, text);
			Point textLocationWithRiseOffsetIfDrawnInOneLine = getTextLocationWithRiseOffset(text, text.value, null, riseOffset, p);
			Rectangle line1Bounds = getLine1BoundsWithoutCurvatureOrSpacing(text.value, textLocationWithRiseOffsetIfDrawnInOneLine, p, false);
			line1Bounds = expandBoundsToIncludeCurvatureAndSpacing(line1Bounds, text, text.value, p);
			if (hasMultipleWords && overlapsBoundaryThatShouldCauseLineSplit(line1Bounds, textLocationWithRiseOffsetIfDrawnInOneLine, text.angle, text.type, graph))
			{
				// The text doesn't fit into centerLocations. Draw it split onto two
				// lines.
				Pair<String> lines = addLineBreakNearMiddle(text.value);
				String nameLine1 = lines.getFirst();
				String nameLine2 = lines.getSecond();

				return drawNameRotated(map, p, graph, riseOffset, enableBoundsChecking, areaToIgnoreInBoundsChecks, text, boldBackground, nameLine1, nameLine2, allowNegatingRizeOffset, drawOffset);
			}
			else
			{
				return drawNameRotated(map, p, graph, riseOffset, enableBoundsChecking, areaToIgnoreInBoundsChecks, text, boldBackground, text.value, null, allowNegatingRizeOffset, drawOffset);
			}
		}
		else if (text.lineBreak == LineBreak.One_line || !hasMultipleWords)
		{
			return drawNameRotated(map, p, graph, riseOffset, enableBoundsChecking, areaToIgnoreInBoundsChecks, text, boldBackground, text.value, null, allowNegatingRizeOffset, drawOffset);
		}
		else if (text.lineBreak == LineBreak.Two_lines)
		{
			Pair<String> lines = addLineBreakNearMiddle(text.value);
			String nameLine1 = lines.getFirst();
			String nameLine2 = lines.getSecond();

			return drawNameRotated(map, p, graph, riseOffset, enableBoundsChecking, areaToIgnoreInBoundsChecks, text, boldBackground, nameLine1, nameLine2, allowNegatingRizeOffset, drawOffset);
		}
		else
		{
			throw new IllegalArgumentException("Unrecognized text line break value for text '" + text.value + "'. Line break value: " + text.lineBreak);
		}
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
	public void drawNameRotated(Image map, Painter p, WorldGraph graph, String name, Set<Point> locations, double riseOffset, boolean enableBoundsChecking, RotatedRectangle areaToIgnoreInBoundsChecks,
			TextType type)
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
		if (drawNameRotated(map, p, graph, riseOffset, enableBoundsChecking, areaToIgnoreInBoundsChecks, text, false, null))
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
	public boolean drawNameRotated(Image map, Painter p, WorldGraph graph, double riseOffset, boolean enableBoundsChecking, RotatedRectangle areaToIgnoreInBoundsChecks, MapText text,
			boolean boldBackground, Point drawOffset)
	{
		return drawNameSplitIfNeeded(map, p, graph, riseOffset, enableBoundsChecking, areaToIgnoreInBoundsChecks, text, boldBackground, true, drawOffset);
	}

	public boolean drawNameRotated(Image map, Painter p, WorldGraph graph, double riseOffset, boolean enableBoundsChecking, RotatedRectangle areaToIgnoreInBoundsChecks, MapText text,
			boolean boldBackground, String line1, String line2, boolean allowNegatingRizeOffset, Point drawOffset)
	{
		if (line2 != null && line2.isEmpty())
		{
			line2 = null;
		}

		if (drawOffset == null)
		{
			drawOffset = new Point(0, 0);
		}

		setFontForText(p, text);

		Point pivot = getTextLocationWithRiseOffset(text, line1, line2, riseOffset, p);
		Point pivotMinusDrawOffset = pivot.subtract(drawOffset);

		Rectangle bounds1WithoutCurvature = getLine1BoundsWithoutCurvatureOrSpacing(line1, pivot, p, line2 != null);
		Rectangle bounds1 = expandBoundsToIncludeCurvatureAndSpacing(bounds1WithoutCurvature, text, line1, p);
		Rectangle bounds2WithoutCurvature = getLine2BoundsWithoutCurvatureOrSpacing(line2, pivot, p);
		Rectangle bounds2 = bounds2WithoutCurvature == null ? null : expandBoundsToIncludeCurvatureAndSpacing(bounds2WithoutCurvature, text, line2, p);

		Dimension line1Size = bounds1.size();
		if (line1Size.width == 0 || line1Size.height == 0)
		{
			// The text is too small to draw.
			return false;
		}

		Dimension line2Size = bounds2 == null ? null : bounds2.size();
		if (line2Size != null && (line2Size.width == 0 || line2Size.height == 0))
		{
			// There is a second line, and it's too small to draw.
			return false;
		}

		Transform orig = p.getTransform();
		try
		{
			p.rotate(text.angle, pivotMinusDrawOffset.x, pivotMinusDrawOffset.y);

			// Rotate the bounds for the text. Use rotated rectangles rather than p's transform because we need to not include drawOffset
			// when rotating.
			RotatedRectangle area1 = new RotatedRectangle(bounds1, text.angle, pivot);
			RotatedRectangle area2 = line2 == null ? null : new RotatedRectangle(bounds2, text.angle, pivot);
			// Make sure we don't draw on top of existing text.
			if (enableBoundsChecking)
			{
				boolean overlapsExistingTextOrCityOrIsOffMap = overlapsExistingTextOrCityOrIsOffMap(area1, areaToIgnoreInBoundsChecks)
						|| (line2 != null && overlapsExistingTextOrCityOrIsOffMap(area2, areaToIgnoreInBoundsChecks));
				boolean overlapsRegionLakeOrCoastline = overlapsBoundaryThatShouldCauseLineSplit(bounds1, pivot, text.angle, text.type, graph)
						|| overlapsBoundaryThatShouldCauseLineSplit(bounds2, pivot, text.angle, text.type, graph);
				boolean isTypeAllowedToCrossBoundaries = text.type == TextType.Title || text.type == TextType.Region || text.type == TextType.City || text.type == TextType.Mountain_range;

				if (overlapsExistingTextOrCityOrIsOffMap || overlapsRegionLakeOrCoastline)
				{
					// If there is a riseOffset, try negating it to put the name
					// below the object instead of above.
					if (riseOffset != 0.0 && allowNegatingRizeOffset)
					{
						Transform rotatedTransform = p.getTransform();
						p.setTransform(orig);
						if (drawNameSplitIfNeeded(map, p, graph, -riseOffset, enableBoundsChecking, null, text, boldBackground, false, drawOffset))
						{
							return true;
						}
						else
						{
							if (!overlapsExistingTextOrCityOrIsOffMap && isTypeAllowedToCrossBoundaries)
							{
								// Allow the text to draw, so set to transform back to the rotated one.
								p.setTransform(rotatedTransform);
							}
							else
							{
								// Give up
								return false;
							}
						}
					}
					else
					{
						// I'm checking allowNegatingRizeOffset below to make sure this isn't the recursive call from above.
						if (allowNegatingRizeOffset && !overlapsExistingTextOrCityOrIsOffMap && isTypeAllowedToCrossBoundaries)
						{
							// Allow the text to draw
						}
						else
						{
							// Give up
							return false;
						}
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
				p.setColor(text.colorOverride == null ? settings.textColor : text.colorOverride);

				// Draw background blending before drawing any lines of text so that the background blending for line 2 cannot erase the
				// text from line 1.

				// The text starts are calculated based on the line bounds without curvature because the line bounds with curvature depend
				// on the text start.
				Point textStartLine1 = new Point(bounds1WithoutCurvature.x - drawOffset.x, bounds1WithoutCurvature.y - drawOffset.y + p.getFontAscent());
				drawBackgroundBlendingForText(map, p, text, textStartLine1, bounds1WithoutCurvature, bounds1, line1, pivotMinusDrawOffset);

				Point textStartLine2 = null;
				if (line2 != null)
				{
					textStartLine2 = new Point(bounds2WithoutCurvature.x - drawOffset.x, bounds2WithoutCurvature.y - drawOffset.y + p.getFontAscent());
					drawBackgroundBlendingForText(map, p, text, textStartLine2, bounds2WithoutCurvature, bounds2, line2, pivotMinusDrawOffset);
				}

				drawStringCurved(p, text, line1, textStartLine1, boldBackground);
				if (line2 != null)
				{
					drawStringCurved(p, text, line2, textStartLine2, boldBackground);
				}
			}

			return true;
		}
		finally
		{
			p.setTransform(orig);
		}
	}

	private Point getTextLocationWithRiseOffset(MapText text, String line1, String line2, double riseOffset, Painter p)
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
	 * Sets the bounds of any texts for which those are null. This is needed because the editor allows making changes when a map is loaded
	 * from a file before it draws the first time. Text bounds aren't set until the text is drawn the first time, and changing fields before
	 * the first draw will set an undo point, which will copy the map settings, including edits. So this function must be called each draw
	 * to make sure null bounds don't get perpetuated from those undo points.
	 */
	public void updateTextBoundsIfNeeded(WorldGraph graph)
	{
		if (settings.edits.hasCreatedTextBounds)
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
		if (textBounds == null)
		{
			return false;
		}

		Center middleCenter = graph.findClosestCenter(textBounds.getCenter(), true);

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
				Center c = graph.findClosestCenter(point, true);

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

	private MapText createMapText(String text, Point location, double angle, TextType type)
	{
		// Divide by settings.resolution so that the location does not depend on
		// the resolution we're drawing at.
		return createMapText(text, location, angle, type, settings.resolution);
	}

	/**
	 * Creates a new MapText, taking settings.resolution into account.
	 */
	public static MapText createMapText(String text, Point location, double angle, TextType type, double resolution)
	{
		// Divide by settings.resolution so that the location does not depend on
		// the resolution we're drawing at.
		return new MapText(text, new Point(location.x / resolution, location.y / resolution), angle, type, LineBreak.Auto, null, null, 0.0, 0, null, MapText.defaultBackgroundFade);
	}

	public void setMapTexts(CopyOnWriteArrayList<MapText> text)
	{
		this.mapTexts = text;
	}

}
