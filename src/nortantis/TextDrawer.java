package nortantis;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.apache.commons.math3.exception.NoDataException;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import nortantis.graph.geom.Point;
import nortantis.graph.geom.Rectangle;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.graph.voronoi.Edge;
import nortantis.graph.voronoi.VoronoiGraph;
import nortantis.util.Helper;
import nortantis.util.ImageHelper;
import nortantis.util.Pair;
import nortantis.util.Range;
import nortantis.util.Tuple2;

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

	private BufferedImage landAndOceanBackground;
	private CopyOnWriteArrayList<MapText> mapTexts;
	private List<Area> cityAreas;
	private Area graphBounds;
	private Font titleFontScaled;
	private Font regionFontScaled;
	private Font mountainRangeFontScaled;
	private Font citiesAndOtherMountainsFontScaled;
	private Font riverFontScaled;
	private Random r;

	/**
	 * 
	 * @param settings
	 *            The map settings to use. Some of these settings are for text drawing.
	 * @param sizeMultiplyer
	 *            The font size of text drawn will be multiplied by this value. This allows the map to be scaled larger or smaller.
	 */
	public TextDrawer(MapSettings settings)
	{
		this.settings = settings;
		this.r = new Random(settings.textRandomSeed);

		if (settings.edits != null && settings.edits.text != null && settings.edits.text.size() > 0)
		{
			// Set the MapTexts in this TextDrawer to be the same object as
			// settings.edits.text.
			// This makes it so that any edits done to the settings will
			// automatically be reflected
			// in the text drawer. Also, it is necessary because the TextDrawer
			// adds the Areas to the
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

		double sizeMultiplier = MapCreator.calcSizeMultipilerFromResolutionScale(settings.resolution);
		titleFontScaled = settings.titleFont.deriveFont(settings.titleFont.getStyle(),
				(int) (settings.titleFont.getSize() * sizeMultiplier));
		regionFontScaled = settings.regionFont.deriveFont(settings.regionFont.getStyle(),
				(int) (settings.regionFont.getSize() * sizeMultiplier));
		mountainRangeFontScaled = settings.mountainRangeFont.deriveFont(settings.mountainRangeFont.getStyle(),
				(int) (settings.mountainRangeFont.getSize() * sizeMultiplier));
		citiesAndOtherMountainsFontScaled = settings.otherMountainsFont.deriveFont(settings.otherMountainsFont.getStyle(),
				(int) (settings.otherMountainsFont.getSize() * sizeMultiplier));
		riverFontScaled = settings.riverFont.deriveFont(settings.riverFont.getStyle(),
				(int) (settings.riverFont.getSize() * sizeMultiplier));

	}


	public synchronized void drawTextFromEdits(BufferedImage map, BufferedImage landAndOceanBackground, WorldGraph graph,
			Rectangle drawBounds)
	{
		this.landAndOceanBackground = landAndOceanBackground;

		drawText(map, graph, settings.edits.text, drawBounds);

		this.landAndOceanBackground = null;
	}

	public void generateText(WorldGraph graph, BufferedImage map, NameCreator nameCreator, BufferedImage landAndOceanBackground,
			List<Set<Center>> mountainGroups, List<IconDrawTask> cityDrawTasks, List<Set<Center>> lakes)
	{
		this.landAndOceanBackground = landAndOceanBackground;

		cityAreas = cityDrawTasks.stream().map(drawTask -> drawTask.createArea()).collect(Collectors.toList());

		if (mountainGroups == null)
		{
			mountainGroups = new ArrayList<>(0);
		}

		generateText(map, graph, nameCreator, mountainGroups, cityDrawTasks, lakes);

		this.landAndOceanBackground = null;
	}

	private void generateText(BufferedImage map, WorldGraph graph, NameCreator nameCreator, List<Set<Center>> mountainGroups,
			List<IconDrawTask> cityDrawTasks, List<Set<Center>> lakes)
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

			graphBounds = new Area(new java.awt.Rectangle(0, 0, graph.getWidth(), graph.getHeight()));

			Graphics2D g = ImageHelper.createGraphicsWithRenderingHints(map);
			g.setColor(settings.textColor);

			addTitle(map, graph, nameCreator, g);

			setFontForTextType(g, TextType.City);
			for (IconDrawTask city : cityDrawTasks)
			{
				Set<Point> cityLoc = new HashSet<>(1);
				cityLoc.add(city.centerLoc);
				String cityName = nameCreator.generateNameOfType(TextType.City, nameCreator.sampleCityTypesForCityFileName(city.fileName),
						true);
				double riseOffset = city.scaledHeight / 2 + (cityYNameOffset * settings.resolution);
				drawNameRotated(map, g, graph, cityName, cityLoc, riseOffset, true, TextType.City);
			}

			setFontForTextType(g, TextType.Region);
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
				drawNameFitIntoCenters(map, g, name, locations, graph, settings.drawBoldBackground, true, TextType.Region);
			}

			for (Set<Center> mountainGroup : mountainGroups)
			{
				if (mountainGroup.size() >= mountainRangeMinSize)
				{
					setFontForTextType(g, TextType.Mountain_range);
					Set<Point> locations = extractLocationsFromCenters(mountainGroup);
					drawNameRotated(map, g, graph, nameCreator.generateNameOfType(TextType.Mountain_range, null, true), locations, 0.0,
							true, TextType.Mountain_range);
				}
				else
				{
					setFontForTextType(g, TextType.Other_mountains);
					if (mountainGroup.size() >= 2)
					{
						if (mountainGroup.size() == 2)
						{
							Point location = findCentroid(extractLocationsFromCenters(mountainGroup));
							MapText text = createMapText(
									nameCreator.generateNameOfType(TextType.Other_mountains, OtherMountainsType.Peaks, true), location, 0.0,
									TextType.Other_mountains);
							if (drawNameRotated(map, g, graph, twoMountainsYOffset * settings.resolution, true, text, false, null))
							{
								mapTexts.add(text);
							}
						}
						else
						{
							drawNameRotated(map, g, graph,
									nameCreator.generateNameOfType(TextType.Other_mountains, OtherMountainsType.Mountains, true),
									extractLocationsFromCenters(mountainGroup), mountainGroupYOffset * settings.resolution, true,
									TextType.Other_mountains);
						}
					}
					else
					{
						Point location = findCentroid(extractLocationsFromCenters(mountainGroup));
						MapText text = createMapText(
								nameCreator.generateNameOfType(TextType.Other_mountains, OtherMountainsType.Peak, true), location, 0.0,
								TextType.Other_mountains);
						if (drawNameRotated(map, g, graph, singleMountainYOffset * settings.resolution, true, text, false, null))
						{
							mapTexts.add(text);
						}
					}
				}
			}

			setFontForTextType(g, TextType.River);
			for (Set<Center> lake : lakes)
			{
				String name = nameCreator.generateNameOfType(TextType.Lake, null, true);
				Set<Point> locations = extractLocationsFromCenters(lake);
				drawNameRotated(map, g, graph, name, locations, 0.0, true, TextType.Lake);
			}

			List<River> rivers = findRivers(graph);
			for (River river : rivers)
			{
				if (river.size() >= riverMinLength && river.getWidth() >= riverMinWidth)
				{
					RiverType riverType = river.getWidth() >= largeRiverWidth ? RiverType.Large : RiverType.Small;

					Set<Point> locations = extractLocationsFromEdges(river.getSegmentForPlacingText());
					drawNameRotated(map, g, graph, nameCreator.generateNameOfType(TextType.River, riverType, true), locations,
							riverNameRiseHeight * settings.resolution, true, TextType.River);
				}

			}

			g.dispose();
		}
		finally
		{
			settings.drawText = drawTextPrev;
		}

		// Now actually draw the text (if settings.drawText is true).
		drawText(map, graph, mapTexts, null);
	}

	public void doForEachTextInBounds(List<MapText> mapTexts, WorldGraph graph, Rectangle bounds, boolean onlyOneCallPerText,
			BiConsumer<MapText, Area> action)
	{
		Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		Area boundsArea = bounds == null ? null : new Area(bounds.toAwtRectangle());

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
				setFontForTextType(g, text.type);
				FontMetrics metrics = g.getFontMetrics();

				// This method of detecting which text to draw isn't very precise, as it can have false positives,
				// but we can't use the Areas in the text object because they get updated during text drawing,
				// so they aren't useful for telling whether the text will appear in 'bounds'.

				Point textLocation = new Point(text.location.x * settings.resolution, text.location.y * settings.resolution);

				// Check when the text is on one line.
				{
					java.awt.Rectangle line1Bounds = getLine1Bounds(text.value, textLocation, metrics, false);
					line1Bounds = addBackgroundBlendingPadding(line1Bounds);
					callIfMapTextIsInBounds(boundsArea, text, line1Bounds, textLocation, action);
					if (onlyOneCallPerText)
					{
						continue;
					}
				}

				// Since it wouldn't be easy from here to figure out whether the text will draw onto one line or two, also check
				// the bounds when it splits under two lines.
				if (text.value.trim().contains(" "))
				{
					Pair<String> lines = addLineBreakNearMiddle(text.value);

					java.awt.Rectangle line1Bounds = getLine1Bounds(lines.getFirst(), textLocation, metrics, true);
					line1Bounds = addBackgroundBlendingPadding(line1Bounds);

					callIfMapTextIsInBounds(boundsArea, text, line1Bounds, textLocation, action);

					java.awt.Rectangle line2Bounds = getLine2Bounds(lines.getFirst(), textLocation, metrics);
					line2Bounds = addBackgroundBlendingPadding(line2Bounds);
					callIfMapTextIsInBounds(boundsArea, text, line2Bounds, textLocation, action);
				}
			}
		}
		g.dispose();
	}

	public Rectangle getTextBoundingBoxFor1Or2LineSplit(MapText text)
	{
		if (text.value == null || text.value.trim().length() == 0)
		{
			// This text was deleted.
			return null;
		}
		Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		setFontForTextType(g, text.type);
		FontMetrics metrics = g.getFontMetrics();
		Point textLocation = new Point(text.location.x * settings.resolution, text.location.y * settings.resolution);

		// Get bounds for when the text is on one line.
		java.awt.Rectangle bounds = getLine1Bounds(text.value, textLocation, metrics, false);
		bounds = addBackgroundBlendingPadding(bounds);
		Area area = getRotatedBounds(text, bounds, textLocation);

		// Since it wouldn't be easy from here to figure out whether the text will draw onto one line or two, also add
		// the bounds when it splits under two lines.
		if (text.value.trim().contains(" "))
		{
			Pair<String> lines = addLineBreakNearMiddle(text.value);

			java.awt.Rectangle line1Bounds = getLine1Bounds(lines.getFirst(), textLocation, metrics, true);
			line1Bounds = addBackgroundBlendingPadding(line1Bounds);

			area.add(getRotatedBounds(text, line1Bounds, textLocation));

			java.awt.Rectangle line2Bounds = getLine2Bounds(lines.getFirst(), textLocation, metrics);
			line2Bounds = addBackgroundBlendingPadding(line2Bounds);
			area.add(getRotatedBounds(text, line2Bounds, textLocation));
		}

		return new Rectangle(area.getBounds());
	}

	private java.awt.Rectangle addBackgroundBlendingPadding(java.awt.Rectangle textBounds)
	{
		int padding = getBackgroundBlendingPadding(new Dimension(textBounds.width, textBounds.height));
		return new java.awt.Rectangle(textBounds.x - padding, textBounds.y - padding, textBounds.width + padding * 2,
				textBounds.height + padding * 2);
	}

	private void callIfMapTextIsInBounds(Area boundsArea, MapText text, java.awt.Rectangle lineBounds, Point pivot,
			BiConsumer<MapText, Area> action)
	{
		AffineTransform transform = new AffineTransform();
		transform.rotate(text.angle, pivot.x, pivot.y);
		Area lineArea = new Area(lineBounds);
		lineArea.transform(transform);

		if (doAreasIntersect(boundsArea, lineArea))
		{
			action.accept(text, lineArea);
		}
	}

	private Area getRotatedBounds(MapText text, java.awt.Rectangle lineBounds, Point pivot)
	{
		AffineTransform transform = new AffineTransform();
		transform.rotate(text.angle, pivot.x, pivot.y);
		Area lineArea = new Area(lineBounds);
		lineArea.transform(transform);
		return lineArea;
	}

	public Rectangle expandBoundsToIncludeText(List<MapText> mapTexts, WorldGraph graph, Rectangle bounds, MapSettings settings)
	{
		if (!settings.drawText)
		{
			return bounds;
		}

		Area boundsArea = new Area(bounds.toAwtRectangle());

		doForEachTextInBounds(mapTexts, graph, bounds, false, (text, area) ->
		{
			boundsArea.add(area);
		});
		java.awt.Rectangle rect = boundsArea.getBounds();
		return new Rectangle(rect.x, rect.y, rect.width, rect.height);
	}

	private void drawText(BufferedImage map, WorldGraph graph, List<MapText> textToDraw, Rectangle drawBounds)
	{
		Graphics2D g = ImageHelper.createGraphicsWithRenderingHints(map);

		g.setColor(settings.textColor);

		Point drawOffset = drawBounds == null ? null : drawBounds.upperLeftCorner();

		doForEachTextInBounds(textToDraw, graph, drawBounds, true, ((text, ignored) ->
		{
			setFontForTextType(g, text.type);
			if (text.type == TextType.Title)
			{
				drawNameSplitIfNeeded(map, g, graph, 0.0, false, text, settings.drawBoldBackground, true, drawOffset);
			}
			else if (text.type == TextType.City)
			{
				drawNameRotated(map, g, graph, 0, false, text, false, drawOffset);
			}
			else if (text.type == TextType.Region)
			{
				drawNameSplitIfNeeded(map, g, graph, 0.0, false, text, settings.drawBoldBackground, true, drawOffset);
			}
			else if (text.type == TextType.Mountain_range)
			{
				drawNameRotated(map, g, graph, 0, false, text, false, drawOffset);
			}
			else if (text.type == TextType.Other_mountains)
			{
				drawNameRotated(map, g, graph, 0, false, text, false, drawOffset);
			}
			else if (text.type == TextType.River)
			{
				drawNameRotated(map, g, graph, 0, false, text, false, drawOffset);
			}
			else if (text.type == TextType.Lake)
			{
				drawNameRotated(map, g, graph, 0, false, text, false, drawOffset);
			}
		}));

		g.dispose();
		
		// Only mark this flag if we drew all text, not just an incremental update.
		if (drawBounds == null || drawBounds.equals(graph.bounds))
		{
			settings.edits.hasCreatedTextBounds = true;
		}
	}


	private void setFontForTextType(Graphics2D g, TextType type)
	{
		if (type == TextType.Title)
		{
			g.setFont(titleFontScaled);
		}
		else if (type == TextType.City)
		{
			g.setFont(citiesAndOtherMountainsFontScaled);
		}
		else if (type == TextType.Region)
		{
			g.setFont(regionFontScaled);
		}
		else if (type == TextType.Mountain_range)
		{
			g.setFont(mountainRangeFontScaled);
		}
		else if (type == TextType.Other_mountains)
		{
			g.setFont(citiesAndOtherMountainsFontScaled);
		}
		else if (type == TextType.River)
		{
			g.setFont(riverFontScaled);
		}
		else if (type == TextType.Lake)
		{
			g.setFont(riverFontScaled);
		}
		else
		{
			throw new RuntimeException("Unkown text type: " + type);
		}
	}

	private void addTitle(BufferedImage map, WorldGraph graph, NameCreator nameCreator, Graphics2D g)
	{
		setFontForTextType(g, TextType.Title);

		List<Tuple2<TectonicPlate, Double>> oceanPlatesAndWidths = new ArrayList<>();
		for (TectonicPlate p : graph.plates)
			if (p.type == PlateType.Oceanic)
				oceanPlatesAndWidths.add(new Tuple2<>(p, findWidth(p.centers)));

		List<Tuple2<TectonicPlate, Double>> landPlatesAndWidths = new ArrayList<>();
		for (TectonicPlate p : graph.plates)
			if (p.type == PlateType.Continental)
				landPlatesAndWidths.add(new Tuple2<>(p, findWidth(p.centers)));

		List<Tuple2<TectonicPlate, Double>> titlePlatesAndWidths;
		if (landPlatesAndWidths.size() > 0
				&& ((double) oceanPlatesAndWidths.size()) / landPlatesAndWidths.size() < thresholdForPuttingTitleOnLand)
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
				if (drawNameFitIntoCenters(map, g, nameCreator.generateNameOfType(TextType.Title, TitleType.Decorated, true),
						extractLocationsFromCenters(plateAndWidth.getFirst().centers), graph, settings.drawBoldBackground, true,
						TextType.Title))
				{
					return;
				}

				// The title didn't fit. Try drawing it with just a name.
				if (drawNameFitIntoCenters(map, g, nameCreator.generateNameOfType(TextType.Title, TitleType.NameOnly, true),
						extractLocationsFromCenters(plateAndWidth.getFirst().centers), graph, settings.drawBoldBackground, true,
						TextType.Title))
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
		// First, follow the river in every direction it flows to find it's mouth (which is the end of the river that is the widest, which
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

		Edge lastTohead = VoronoiGraph.edgeWithCorners(last, head);
		River result = new River();
		result.add(lastTohead);

		List<Edge> riverEdges = new ArrayList<>();
		for (Edge e : head.protrudes)
		{
			if (e.isRiver() && e != lastTohead)
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
	private void drawBackgroundBlendingForText(BufferedImage map, Graphics2D g, Point textStart, Dimension textSize, double angle,
			FontMetrics metrics, String text, Point pivot)
	{
		int kernelSize = getBackgroundBlendingKernelSize(textSize);
		if (kernelSize == 0)
		{
			return;
		}
		int padding = getBackgroundBlendingPadding(textSize);

		BufferedImage textBG = new BufferedImage(textSize.width + padding * 2, textSize.height + padding * 2, BufferedImage.TYPE_BYTE_GRAY);

		Graphics2D bG = ImageHelper.createGraphicsWithRenderingHints(textBG);
		bG.setFont(g.getFont());
		bG.setColor(Color.white);
		bG.drawString(text, padding, padding + metrics.getAscent());

		// Use convolution to make a hazy background for the text.
		BufferedImage haze = ImageHelper.convolveGrayscale(textBG, ImageHelper.createGaussianKernel(kernelSize), true, false);
		// Threshold it and convolve it again to make the haze bigger.
		ImageHelper.threshold(haze, 1);
		haze = ImageHelper.convolveGrayscale(haze, ImageHelper.createGaussianKernel(kernelSize), true, false);

		ImageHelper.combineImagesWithMaskInRegion(map, landAndOceanBackground, haze, ((int) Math.round(textStart.x)) - padding,
				(int) Math.round(textStart.y) - metrics.getAscent() - padding, angle, pivot);
	}

	private int getBackgroundBlendingKernelSize(Dimension textSize)
	{
		// This magic number below is a result of trial and error to get the
		// blur levels to look right.
		int kernelSize = (int) ((13.0 / 54.0) * textSize.height);
		return kernelSize;
	}

	private int getBackgroundBlendingPadding(Dimension textSize)
	{
		return getBackgroundBlendingKernelSize(textSize) / 2;
	}

	private void drawStringWithBoldBackground(Graphics2D g, String name, Point textStart, double angle, Point pivot)
	{
		if (name.length() == 0)
			return;

		// We're assuming g's transform is already rotated. As such, we don't
		// need to handle rotation when drawing text here.

		Font original = g.getFont();
		Color originalColor = g.getColor();
		int style = original.isItalic() ? Font.BOLD | Font.ITALIC : Font.BOLD;
		Font background = g.getFont().deriveFont(style, (int) (g.getFont().getSize()));
		FontMetrics metrics = g.getFontMetrics(original);

		Point curLocNotRotated = new Point(textStart);
		for (int i : new Range(name.length()))
		{
			g.setFont(background);
			g.setColor(settings.boldBackgroundColor);
			g.drawString("" + name.charAt(i), (int) curLocNotRotated.x, (int) curLocNotRotated.y);

			g.setFont(original);
			g.setColor(originalColor);
			g.drawString("" + name.charAt(i), (int) curLocNotRotated.x, (int) curLocNotRotated.y);

			int charWidth = metrics.stringWidth("" + name.charAt(i));
			curLocNotRotated = new Point(curLocNotRotated.x + charWidth, curLocNotRotated.y);
		}
	}

	/**
	 * 
	 * Side effect: This adds a new MapText to mapTexts.
	 * 
	 * @return True iff text was drawn.
	 */
	private boolean drawNameFitIntoCenters(BufferedImage map, Graphics2D g, String name, Set<Point> centerLocations, WorldGraph graph,
			boolean boldBackground, boolean enableBoundsChecking, TextType textType)
	{
		if (name.length() == 0)
			return false;

		Point centroid = findCentroid(centerLocations);

		MapText text = createMapText(name, centroid, 0.0, textType);
		if (drawNameSplitIfNeeded(map, g, graph, 0.0, enableBoundsChecking, text, boldBackground, true, null))
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

				Point loc = Helper.maxItem(samples,
						(point1, point2) -> -Double.compare(point1.distanceTo(centroid), point2.distanceTo(centroid)));

				text = createMapText(name, loc, 0.0, textType);
				if (drawNameSplitIfNeeded(map, g, graph, 0.0, enableBoundsChecking, text, boldBackground, true, null))
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

	public static java.awt.Dimension getTextDimensions(String text, Font font)
	{
		FontMetrics metrics = getFontMetrics(font);
		return getTextDimensions(text, metrics);
	}

	private static java.awt.Dimension getTextDimensions(String text, FontMetrics metrics)
	{
		return new java.awt.Dimension(metrics.stringWidth(text), metrics.getAscent() + metrics.getDescent());
	}

	private static FontMetrics getFontMetrics(Font font)
	{
		return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics().getFontMetrics(font);
	}

	public static int getFontHeight(Font font)
	{
		FontMetrics metrics = getFontMetrics(font);
		return metrics.getAscent() + metrics.getDescent();
	}

	private static int getFontHeight(FontMetrics metrics)
	{
		return metrics.getAscent() + metrics.getDescent();
	}

	/**
	 * Draws the given name at the given location (centroid). If the name cannot be drawn on one line and still fit with the given
	 * locations, then it will be drawn on 2 lines.
	 * 
	 * The actual drawing step is skipped if settings.drawText = false.
	 * 
	 * @return True iff text was drawn.
	 */
	private boolean drawNameSplitIfNeeded(BufferedImage map, Graphics2D g, WorldGraph graph, double riseOffset,
			boolean enableBoundsChecking, MapText text, boolean boldBackground, boolean allowNegatingRizeOffset, Point drawOffset)
	{
		FontMetrics metrics = g.getFontMetrics();
		Point textLocationWithRiseOffsetIfDrawnInOneLine = getTextLocationWithRiseOffset(text, text.value, null, riseOffset, metrics);
		java.awt.Rectangle line1Bounds = getLine1Bounds(text.value, textLocationWithRiseOffsetIfDrawnInOneLine, metrics, false);
		if (text.value.trim().split(" ").length > 1
				&& overlapsRegionLakeOrCoastline(line1Bounds, textLocationWithRiseOffsetIfDrawnInOneLine, text.angle, graph))
		{
			// The text doesn't fit into centerLocations. Draw it split onto two
			// lines.
			Pair<String> lines = addLineBreakNearMiddle(text.value);
			String nameLine1 = lines.getFirst();
			String nameLine2 = lines.getSecond();

			return drawNameRotated(map, g, graph, riseOffset, enableBoundsChecking, text, boldBackground, nameLine1, nameLine2,
					allowNegatingRizeOffset, drawOffset);
		}
		else
		{
			return drawNameRotated(map, g, graph, riseOffset, enableBoundsChecking, text, boldBackground, text.value, null,
					allowNegatingRizeOffset, drawOffset);
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
	public void drawNameRotated(BufferedImage map, Graphics2D g, WorldGraph graph, String name, Set<Point> locations, double riseOffset,
			boolean enableBoundsChecking, TextType type)
	{
		if (name.length() == 0)
			return;

		Point centroid = findCentroid(locations);

		SimpleRegression regression = new SimpleRegression();
		for (Point p : locations)
		{
			regression.addObservation(new double[] { p.x }, p.y);
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
		if (drawNameRotated(map, g, graph, riseOffset, enableBoundsChecking, text, false, null))
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
	 * 
	 * @return true iff the text was drawn.
	 */
	public boolean drawNameRotated(BufferedImage map, Graphics2D g, WorldGraph graph, double riseOffset, boolean enableBoundsChecking,
			MapText text, boolean boldBackground, Point drawOffset)
	{
		return drawNameSplitIfNeeded(map, g, graph, riseOffset, enableBoundsChecking, text, boldBackground, true, drawOffset);
	}

	public boolean drawNameRotated(BufferedImage map, Graphics2D g, WorldGraph graph, double riseOffset, boolean enableBoundsChecking,
			MapText text, boolean boldBackground, String line1, String line2, boolean allowNegatingRizeOffset, Point drawOffset)
	{
		if (line2 != null && line2.equals(""))
		{
			line2 = null;
		}

		if (drawOffset == null)
		{
			drawOffset = new Point(0, 0);
		}

		FontMetrics metrics = g.getFontMetrics();
		Point pivot = getTextLocationWithRiseOffset(text, line1, line2, riseOffset, metrics);
		Point pivotMinusDrawOffset = pivot.subtract(drawOffset);

		java.awt.Rectangle bounds1 = getLine1Bounds(line1, pivot, metrics, line2 != null);
		// If the above integer conversion resulted in a truncation that resulted in a negative number, then subtract 1. This is
		// necessary because in Java, positive floating point numbers converted to integers round down, but negative numbers round up.
		if (bounds1.x < 0 && (pivot.x - (int) pivot.x != 0.0))
		{
			bounds1.x -= 1;
		}
		if (bounds1.y < 0 && (pivot.y - (int) pivot.y != 0.0))
		{
			bounds1.y -= 1;
		}
		java.awt.Rectangle bounds2 = getLine2Bounds(line2, pivot, metrics);
		if (bounds2 != null)
		{
			if (bounds2.x < 0 && (pivot.x - (int) pivot.x != 0.0))
			{
				bounds2.x -= 1;
			}
			if (bounds2.y < 0 && (pivot.y - (int) pivot.y != 0.0))
			{
				bounds2.y -= 1;
			}
		}

		Dimension line1Size = new Dimension(bounds1.width, bounds1.height);
		if (line1Size.width == 0 || line1Size.height == 0)
		{
			// The text is too small to draw.
			return false;
		}

		Dimension line2Size = bounds2 == null ? null : new Dimension(bounds2.width, bounds2.height);
		if (line2Size != null && (line2Size.width == 0 || line2Size.height == 0))
		{
			// There is a second line, and it's too small to draw.
			return false;
		}

		AffineTransform orig = g.getTransform();
		try
		{
			AffineTransform transform = new AffineTransform(g.getTransform());
			g.rotate(text.angle, pivotMinusDrawOffset.x, pivotMinusDrawOffset.y);

			// Rotate the bounds for the text. Use a new transform rather than g's transform because we need to not include drawOffset when
			// rotating.
			transform.rotate(text.angle, pivot.x, pivot.y);
			Area area1 = new Area(bounds1).createTransformedArea(transform);
			Area area2 = line2 == null ? null : new Area(bounds2).createTransformedArea(transform);
			// Make sure we don't draw on top of existing text.
			if (enableBoundsChecking)
			{
				boolean overlapsExistingTextOrCityOrIsOffMap = overlapsExistingTextOrCityOrIsOffMap(area1)
						|| (line2 != null && overlapsExistingTextOrCityOrIsOffMap(area2));
				boolean overlapsRegionLakeOrCoastline = overlapsRegionLakeOrCoastline(bounds1, pivot, text.angle, graph)
						|| overlapsRegionLakeOrCoastline(bounds2, pivot, text.angle, graph);
				boolean isTypeAllowedToCrossBoundaries = text.type == TextType.Title || text.type == TextType.Region
						|| text.type == TextType.City || text.type == TextType.Mountain_range;

				if (overlapsExistingTextOrCityOrIsOffMap || overlapsRegionLakeOrCoastline)
				{
					// If there is a riseOffset, try negating it to put the name
					// below the object instead of above.
					if (riseOffset != 0.0 && allowNegatingRizeOffset)
					{
						AffineTransform rotatedTransform = g.getTransform();
						g.setTransform(orig);
						if (drawNameSplitIfNeeded(map, g, graph, -riseOffset, enableBoundsChecking, text, boldBackground, false,
								drawOffset))
						{
							return true;
						}
						else
						{
							if (!overlapsExistingTextOrCityOrIsOffMap && isTypeAllowedToCrossBoundaries)
							{
								// Allow the text to draw, so set to transform back to the rotated one.
								g.setTransform(rotatedTransform);
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

			text.line1Area = area1;
			text.line2Area = area2;
			// Store the bounds centered at the origin so that the editor can use the bounds to draw the text boxes of text being moved
			// before the text is redrawn.
			text.line1Bounds = new java.awt.Rectangle((int) (bounds1.x - pivot.x), (int) (bounds1.y - pivot.y), bounds1.width,
					bounds1.height);
			text.line2Bounds = bounds2 == null ? null
					: new java.awt.Rectangle((int) (bounds2.x - pivot.x), (int) (bounds2.y - pivot.y), bounds2.width, bounds2.height);
			if (riseOffset != 0)
			{
				// Update the text location with the offset. This only happens when generating new text, not when making changes in the
				// editor.
				text.location = new Point(pivot.x / settings.resolution, pivot.y / settings.resolution);
			}

			if (settings.drawText)
			{
				{
					Point textStart = new Point(bounds1.x - drawOffset.x, bounds1.y - drawOffset.y + g.getFontMetrics().getAscent());
					drawBackgroundBlendingForText(map, g, textStart, line1Size, text.angle, g.getFontMetrics(), line1,
							pivotMinusDrawOffset);
					if (boldBackground)
					{
						drawStringWithBoldBackground(g, line1, textStart, text.angle, pivot);
					}
					else
					{
						g.drawString(line1, (int) textStart.x, (int) textStart.y);
					}
				}
				if (line2 != null)
				{
					Point textStart = new Point(bounds2.x - drawOffset.x, bounds2.y - drawOffset.y + g.getFontMetrics().getAscent());
					drawBackgroundBlendingForText(map, g, textStart, line2Size, text.angle, g.getFontMetrics(), line2,
							pivotMinusDrawOffset);
					if (boldBackground)
					{
						drawStringWithBoldBackground(g, line2, textStart, text.angle, pivot);
					}
					else
					{
						g.drawString(line2, (int) textStart.x, (int) textStart.y);
					}
				}
			}

			return true;
		}
		finally
		{
			g.setTransform(orig);
		}
	}

	private Point getTextLocationWithRiseOffset(MapText text, String line1, String line2, double riseOffset, FontMetrics metrics)
	{
		if (line2 != null && line2.equals(""))
		{
			line2 = null;
		}

		Point textLocation = new Point(text.location.x * settings.resolution, text.location.y * settings.resolution);

		int fontHeight = getFontHeight(metrics);
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

	private java.awt.Rectangle getLine1Bounds(String line1, Point pivot, FontMetrics metrics, boolean hasLine2)
	{
		int fontHeight = getFontHeight(metrics);
		Dimension size = getTextDimensions(line1, metrics);
		return new java.awt.Rectangle((int) (pivot.x - size.width / 2), (int) (pivot.y - size.height / 2) - (hasLine2 ? fontHeight / 2 : 0),
				size.width, size.height);
	}

	private java.awt.Rectangle getLine2Bounds(String line2, Point pivot, FontMetrics metrics)
	{
		if (line2 == null)
		{
			return null;
		}

		int fontHeight = getFontHeight(metrics);
		Dimension size = getTextDimensions(line2, metrics);
		return new java.awt.Rectangle((int) (pivot.x - size.width / 2), (int) (pivot.y - (size.height / 2) + fontHeight / 2), size.width,
				size.height);
	}
	
	/**
	 * Sets the bounds and areas of any texts for which those are null. This is needed because the editor allows making changes when a map is
	 * loaded from a file before it draws the first time. Text bounds and areas aren't set until the text is drawn the first time, and
	 * changing fields before the first draw will set an undo point, which will copy the map settings, including edits. So this function must
	 * be called each draw to make sure null bounds and areas don't get perpetuated from those undo points.
	 */
	public void updateTextBoundsIfNeeded(WorldGraph graph)
	{
		if (settings.edits.hasCreatedTextBounds)
		{
			return;
		}
		
		boolean originalDrawText = settings.drawText;
		try
		{
			settings.drawText = false;
			BufferedImage fakeMapThatNothingShouldDrawOn = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
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

	private boolean overlapsRegionLakeOrCoastline(java.awt.Rectangle textBounds, Point pivot, double angle, WorldGraph graph)
	{
		if (textBounds == null)
		{
			return false;
		}

		Center middleCenter = graph.findClosestCenter(new Point(textBounds.getCenterX(), textBounds.getCenterY()));

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
				Center c = graph.findClosestCenter(point);

				if (doCentersHaveBoundaryBetweenThem(middleCenter, c, settings))
				{
					return true;
				}
			}
		}

		return false;
	}

	private boolean doCentersHaveBoundaryBetweenThem(Center c1, Center c2, MapSettings settings)
	{
		if (c1.isWater && c2.isWater)
		{
			return false;
		}


		if (c1.isWater != c2.isWater)
		{
			return true;
		}

		if (!settings.drawRegionColors)
		{
			return false;
		}

		return c1.region != c2.region;
	}

	private boolean overlapsExistingTextOrCityOrIsOffMap(Area bounds)
	{
		for (MapText mp : mapTexts)
		{
			// Ignore empty text and ignore edited text.
			if (mp.value.length() > 0)
			{
				if (mp.line1Area != null)
				{
					if (doAreasIntersect(bounds, mp.line1Area))
					{
						return true;
					}
				}

				if (mp.line2Area != null)
				{
					if (doAreasIntersect(bounds, mp.line2Area))
					{
						return true;
					}
				}
			}
		}

		for (Area a : cityAreas)
		{
			if (doAreasIntersect(bounds, a))
			{
				return true;
			}
		}

		return !graphBounds.contains(bounds.getBounds2D());
	}

	public static boolean doAreasIntersect(Area area1, Area area2)
	{
		if (area1 == null || area2 == null)
		{
			return false;
		}

		Area copy = new Area(area1);
		copy.intersect(area2);
		return !copy.isEmpty();
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
		return new MapText(text, new Point(location.x / resolution, location.y / resolution), angle, type);
	}

	public void setMapTexts(CopyOnWriteArrayList<MapText> text)
	{
		this.mapTexts = text;
	}

}
