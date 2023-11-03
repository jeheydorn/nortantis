package nortantis;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
import nortantis.util.AssetsPath;
import nortantis.util.Function0;
import nortantis.util.Helper;
import nortantis.util.ImageHelper;
import nortantis.util.Logger;
import nortantis.util.Pair;
import nortantis.util.ProbabilityHelper;
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
	private final double maxWordLengthComparedToAverage = 2.0;
	private final double probabilityOfKeepingNameLength1 = 0.0;
	private final double probabilityOfKeepingNameLength2 = 0.0;
	private final double probabilityOfKeepingNameLength3 = 0.3;
	private final double thresholdForPuttingTitleOnLand = 0.3;

	private BufferedImage landAndOceanBackground;
	private CopyOnWriteArrayList<MapText> mapTexts;
	private List<Area> cityAreas;
	private Random r;
	private NameGenerator placeNameGenerator;
	private NameGenerator personNameGenerator;
	private NameCompiler nameCompiler;
	private Area graphBounds;
	private Font titleFontScaled;
	private Font regionFontScaled;
	private Font mountainRangeFontScaled;
	private Font citiesAndOtherMountainsFontScaled;
	private Font riverFontScaled;
	private Set<String> namesGenerated;

	/**
	 * 
	 * @param settings
	 *            The map settings to use. Some of these settings are for text drawing.
	 * @param sizeMultiplyer
	 *            The font size of text drawn will be multiplied by this value. This allows the map to be scaled larger or smaller.
	 */
	public TextDrawer(MapSettings settings, double sizeMultiplyer)
	{
		this.settings = settings;

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

		// I create a new Random instead of passing one in so that small
		// differences in the way
		// the random number generator is used previous to the TextDrawer do not
		// change the text.
		this.r = new Random(settings.textRandomSeed);
		this.namesGenerated = new HashSet<>();

		processBooks(settings.books);

		titleFontScaled = settings.titleFont.deriveFont(settings.titleFont.getStyle(),
				(int) (settings.titleFont.getSize() * sizeMultiplyer));
		regionFontScaled = settings.regionFont.deriveFont(settings.regionFont.getStyle(),
				(int) (settings.regionFont.getSize() * sizeMultiplyer));
		mountainRangeFontScaled = settings.mountainRangeFont.deriveFont(settings.mountainRangeFont.getStyle(),
				(int) (settings.mountainRangeFont.getSize() * sizeMultiplyer));
		citiesAndOtherMountainsFontScaled = settings.otherMountainsFont.deriveFont(settings.otherMountainsFont.getStyle(),
				(int) (settings.otherMountainsFont.getSize() * sizeMultiplyer));
		riverFontScaled = settings.riverFont.deriveFont(settings.riverFont.getStyle(),
				(int) (settings.riverFont.getSize() * sizeMultiplyer));

	}

	public void processBooks(Set<String> books)
	{
		List<String> placeNames = new ArrayList<>();
		List<String> personNames = new ArrayList<>();
		List<Pair<String>> nounAdjectivePairs = new ArrayList<>();
		List<Pair<String>> nounVerbPairs = new ArrayList<>();
		for (String book : books)
		{
			placeNames.addAll(readNameList(AssetsPath.getInstallPath() + "/books/" + book + "_place_names.txt"));
			personNames.addAll(readNameList(AssetsPath.getInstallPath() + "/books/" + book + "_person_names.txt"));
			nounAdjectivePairs.addAll(readStringPairs(AssetsPath.getInstallPath() + "/books/" + book + "_noun_adjective_pairs.txt"));
			nounVerbPairs.addAll(readStringPairs(AssetsPath.getInstallPath() + "/books/" + book + "_noun_verb_pairs.txt"));
		}

		placeNameGenerator = new NameGenerator(r, placeNames, maxWordLengthComparedToAverage, probabilityOfKeepingNameLength1,
				probabilityOfKeepingNameLength2, probabilityOfKeepingNameLength3);
		personNameGenerator = new NameGenerator(r, personNames, maxWordLengthComparedToAverage, probabilityOfKeepingNameLength1,
				probabilityOfKeepingNameLength2, probabilityOfKeepingNameLength3);

		nameCompiler = new NameCompiler(r, nounAdjectivePairs, nounVerbPairs);
	}

	private List<Pair<String>> readStringPairs(String filename)
	{
		List<Pair<String>> result = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new FileReader(new File(filename))))
		{
			int lineNum = 0;
			for (String line; (line = br.readLine()) != null;)
			{
				lineNum++;

				// Remove white space lines.
				if (!line.trim().isEmpty())
				{
					String[] parts = line.split("\t");
					if (parts.length != 2)
					{
						Logger.println("Warning: No string pair found in " + filename + " at line " + lineNum + ".");
						continue;
					}
					result.add(new Pair<>(parts[0], parts[1]));
				}
			}
		}
		catch (FileNotFoundException e)
		{
			throw new RuntimeException(e);
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}

		return result;
	}

	private List<String> readNameList(String filename)
	{
		List<String> result = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new FileReader(new File(filename))))
		{
			for (String line; (line = br.readLine()) != null;)
			{
				// Remove white space lines.
				if (!line.trim().isEmpty())
				{
					result.add(line);
				}
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException("Unable to read names from the file " + filename, e);
		}

		return result;
	}

	public void drawTextFromEdits(BufferedImage map, BufferedImage landAndOceanBackground, WorldGraph graph, Rectangle drawBounds)
	{
		this.landAndOceanBackground = landAndOceanBackground;

		drawTextFromEdits(map, graph, drawBounds);

		this.landAndOceanBackground = null;
	}

	public void generateText(WorldGraph graph, BufferedImage map, BufferedImage landAndOceanBackground, List<Set<Center>> mountainGroups,
			List<IconDrawTask> cityDrawTasks, List<Set<Center>> lakes)
	{
		this.landAndOceanBackground = landAndOceanBackground;

		cityAreas = cityDrawTasks.stream().map(drawTask -> drawTask.createArea()).collect(Collectors.toList());

		if (mountainGroups == null)
		{
			mountainGroups = new ArrayList<>(0);
		}

		generateText(map, graph, mountainGroups, cityDrawTasks, lakes);

		this.landAndOceanBackground = null;
	}

	private void generateText(BufferedImage map, WorldGraph graph, List<Set<Center>> mountainGroups, List<IconDrawTask> cityDrawTasks,
			List<Set<Center>> lakes)
	{
		// All text drawn must be done so in order from highest to lowest
		// priority because if I try to draw
		// text on top of other text, the latter will not be displayed.

		graphBounds = new Area(new java.awt.Rectangle(0, 0, graph.getWidth(), graph.getHeight()));

		Graphics2D g = ImageHelper.createGraphicsWithRenderingHints(map);
		g.setColor(settings.textColor);

		addTitle(map, graph, g);

		setFontForTextType(g, TextType.City);
		for (IconDrawTask city : cityDrawTasks)
		{
			Set<Point> cityLoc = new HashSet<>(1);
			cityLoc.add(city.centerLoc);
			String cityName = generateNameOfType(TextType.City, sampleCityTypesForCityFileName(city.fileName), true);
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
				name = generateNameOfType(TextType.Region, null, true);
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
				drawNameRotated(map, g, graph, generateNameOfType(TextType.Mountain_range, null, true), locations, 0.0, true,
						TextType.Mountain_range);
			}
			else
			{
				setFontForTextType(g, TextType.Other_mountains);
				if (mountainGroup.size() >= 2)
				{
					if (mountainGroup.size() == 2)
					{
						Point location = findCentroid(extractLocationsFromCenters(mountainGroup));
						MapText text = createMapText(generateNameOfType(TextType.Other_mountains, OtherMountainsType.Peaks, true), location,
								0.0, TextType.Other_mountains);
						if (drawNameRotated(map, g, graph, twoMountainsYOffset * settings.resolution, true, text, false, null))
						{
							mapTexts.add(text);
						}
					}
					else
					{
						drawNameRotated(map, g, graph, generateNameOfType(TextType.Other_mountains, OtherMountainsType.Mountains, true),
								extractLocationsFromCenters(mountainGroup), mountainGroupYOffset * settings.resolution, true,
								TextType.Other_mountains);
					}
				}
				else
				{
					Point location = findCentroid(extractLocationsFromCenters(mountainGroup));
					MapText text = createMapText(generateNameOfType(TextType.Other_mountains, OtherMountainsType.Peak, true), location, 0.0,
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
			String name = generateNameOfType(TextType.Lake, null, true);
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
				drawNameRotated(map, g, graph, generateNameOfType(TextType.River, riverType, true), locations,
						riverNameRiseHeight * settings.resolution, true, TextType.River);
			}

		}

		g.dispose();
	}

	public static List<CityType> findCityTypeFromCityFileName(String cityFileNameNoExtension)
	{
		List<CityType> result = new ArrayList<>();
		Set<String> words = new HashSet<String>(Arrays.asList(cityFileNameNoExtension.toLowerCase().split(" |_")));
		if (words.contains("fort") || words.contains("castle") || words.contains("keep") || words.contains("citadel")
				|| words.contains("walled"))
		{
			result.add(CityType.Fortification);
		}
		else
		{
			if (words.contains("city") || words.contains("buildings"))
			{
				result.add(CityType.City);
			}
			if (words.contains("town") || words.contains("village") || words.contains("houses"))
			{
				result.add(CityType.Town);
			}
			if (words.contains("farm") || words.contains("homestead") || words.contains("building") || words.contains("house"))
			{
				result.add(CityType.Homestead);
			}
		}

		return result;
	}

	private CityType sampleCityTypesForCityFileName(String cityFileNameNoExtension)
	{
		List<CityType> types = findCityTypeFromCityFileName(cityFileNameNoExtension);
		if (types.isEmpty())
		{
			return ProbabilityHelper.sampleEnumUniform(r, CityType.class);
		}

		return ProbabilityHelper.sampleUniform(r, types);
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

	/**
	 * Draw text which was (potentially) added or modified by the user.
	 * 
	 * @param map
	 * @param graph
	 * @param g
	 */
	private synchronized void drawTextFromEdits(BufferedImage map, WorldGraph graph, Rectangle drawBounds)
	{
		Graphics2D g = ImageHelper.createGraphicsWithRenderingHints(map);

		g.setColor(settings.textColor);

		Point drawOffset = drawBounds == null ? null : drawBounds.upperLeftCorner();

		doForEachTextInBounds(settings.edits.text, graph, drawBounds, true, ((text, ignored) ->
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

	/**
	 * Generates a name of the specified type. This is for when the user adds new text to the map. It is not used when the map text is first
	 * generated.
	 * 
	 */
	public String generateNameOfTypeForTextEditor(TextType type)
	{
		nameCompiler.setSeed(System.currentTimeMillis());
		r.setSeed(System.currentTimeMillis());

		Object subType = null;

		if (type.equals(TextType.Title))
		{
			subType = ProbabilityHelper.sampleCategorical(r, ProbabilityHelper.createUniformDistributionOverEnumValues(TitleType.values()));
		}
		else if (type.equals(TextType.Other_mountains))
		{
			subType = ProbabilityHelper.sampleCategorical(r,
					ProbabilityHelper.createUniformDistributionOverEnumValues(OtherMountainsType.values()));
		}
		else if (type.equals(TextType.River))
		{
			subType = ProbabilityHelper.sampleCategorical(r, ProbabilityHelper.createUniformDistributionOverEnumValues(RiverType.values()));
		}
		else if (type.equals(TextType.City))
		{
			// In the editor you add city icons in a different tool than city
			// text, so we have no way of knowing what icon, if any, this city
			// name is for.
			subType = ProbabilityHelper.sampleEnumUniform(r, CityType.class);
		}

		try
		{
			return generateNameOfType(type, subType, false);
		}
		catch (Exception e)
		{
			// This can happen if the selected books don't have enough names.
			return "name";
		}
	}

	/**
	 * Generate a name of a specified type.
	 * 
	 * @param type
	 *            The type of name
	 * @param subType
	 *            A sub-type specific to the type specified. null means default type.
	 * @param requireUnique
	 *            Whether generated names must be never seen in the extracted book names nor previously generated. If unique name generating
	 *            fails, an exception will be thrown.
	 */
	private String generateNameOfType(TextType type, Object subType, boolean requireUnique)
	{
		if (type.equals(TextType.Title))
		{
			TitleType titleType = subType == null ? TitleType.Decorated : (TitleType) subType;

			double probabilityOfPersonName = 0.3;
			switch (titleType)
			{
			case Decorated:
				if (r.nextDouble() < probabilityOfPersonName)
				{
					return generatePersonName("The Land of %s", requireUnique);
				}
				else
				{
					return generatePlaceName("The Land of %s", requireUnique);
				}
			case NameOnly:
				return generatePlaceName("%s", requireUnique);
			default:
				throw new IllegalArgumentException("Unknown title type: " + titleType);
			}
		}
		if (type.equals(TextType.Region))
		{
			double probabilityOfPersonName = 0.2;
			if (r.nextDouble() < probabilityOfPersonName)
			{
				String format = ProbabilityHelper.sampleCategorical(r,
						Arrays.asList(new Tuple2<>(0.2, "Kingdom of %s"), new Tuple2<>(0.04, "Empire of %s")));
				return generatePersonName(format, requireUnique);
			}
			else
			{
				String format = ProbabilityHelper.sampleCategorical(r,
						Arrays.asList(new Tuple2<>(0.1, "Kingdom of %s"), new Tuple2<>(0.02, "Empire of %s"), new Tuple2<>(0.85, "%s")));
				return generatePlaceName(format, requireUnique);
			}
		}
		else if (type.equals(TextType.Mountain_range))
		{
			double probabilityOfCompiledName = nameCompiler.isEmpty() ? 0.0 : 0.7;
			if (r.nextDouble() < probabilityOfCompiledName)
			{
				return compileName("%s Range", requireUnique);
			}
			else
			{
				return generatePlaceName("%s Range", requireUnique);
			}
		}
		else if (type.equals(TextType.Other_mountains))
		{
			OtherMountainsType mountainType = subType == null ? OtherMountainsType.Mountains : (OtherMountainsType) subType;
			String format = getOtherMountainNameFormat(mountainType);
			double probabilityOfCompiledName = nameCompiler.isEmpty() ? 0.0 : 0.5;
			if (r.nextDouble() < probabilityOfCompiledName)
			{
				return compileName(format, requireUnique);
			}
			else
			{
				double probabilityOfPersonName = 0.4;
				if (r.nextDouble() < probabilityOfPersonName)
				{
					// Person name
					// Make the name possessive.
					format = format.replace("%s", "%s's");
					return generatePersonName(format, requireUnique);
				}
				else
				{
					return generatePlaceName(format, requireUnique);
				}
			}
		}
		else if (type.equals(TextType.City))
		{
			CityType cityType = (CityType) subType;
			String structureName;
			if (cityType.equals(CityType.Fortification))
			{
				structureName = ProbabilityHelper.sampleCategorical(r, Arrays.asList(new Tuple2<>(0.2, "Castle"), new Tuple2<>(0.2, "Fort"),
						new Tuple2<>(0.2, "Fortress"), new Tuple2<>(0.2, "Keep"), new Tuple2<>(0.2, "Citadel")));
			}
			else if (cityType.equals(CityType.City))
			{
				structureName = ProbabilityHelper.sampleCategorical(r,
						Arrays.asList(new Tuple2<>(0.75, "City"), new Tuple2<>(0.25, "Town")));
			}
			else if (cityType.equals(CityType.Town))
			{
				structureName = ProbabilityHelper.sampleCategorical(r,
						Arrays.asList(new Tuple2<>(0.2, "City"), new Tuple2<>(0.4, "Village"), new Tuple2<>(0.4, "Town")));
			}
			else if (cityType.equals(CityType.Homestead))
			{
				structureName = ProbabilityHelper.sampleCategorical(r,
						Arrays.asList(new Tuple2<>(0.5, "Farm"), new Tuple2<>(0.5, "Village")));
			}
			else
			{
				throw new RuntimeException("Unknown city type: " + cityType);
			}

			double probabilityOfPersonName = 0.5;
			if (r.nextDouble() < probabilityOfPersonName)
			{
				String format = ProbabilityHelper.sampleCategorical(r,
						Arrays.asList(new Tuple2<>(0.1, structureName + " of %s"), new Tuple2<>(0.04, "%s's " + structureName),
								new Tuple2<>(0.04, structureName + " of %s"), new Tuple2<>(0.04, structureName + " of %s"),
								new Tuple2<>(0.04, "%s's " + structureName), new Tuple2<>(0.04, "%s's " + structureName)));
				return generatePersonName(format, requireUnique);
			}
			else
			{
				String format = ProbabilityHelper.sampleCategorical(r, Arrays.asList(new Tuple2<>(0.2, structureName + " of %s"),
						new Tuple2<>(0.2, "%s " + structureName), new Tuple2<>(0.02, "%s " + structureName), new Tuple2<>(0.3, "%s")));
				return generatePlaceName(format, requireUnique);
			}
		}
		else if (type.equals(TextType.River))
		{
			RiverType riverType = subType == null ? RiverType.Large : (RiverType) subType;
			String format = getRiverNameFormat(riverType);
			double probabilityOfCompiledName = nameCompiler.isEmpty() ? 0.0 : 0.5;
			if (r.nextDouble() < probabilityOfCompiledName)
			{
				return compileName(format, requireUnique);
			}
			else
			{
				double probabilityOfPersonName = 0.4;
				if (r.nextDouble() < probabilityOfPersonName)
				{
					// Person name
					// Make the name possessive.
					format = format.replace("%s", "%s's");
					return generatePersonName(format, requireUnique);
				}
				else
				{
					return generatePlaceName(format, requireUnique);
				}
			}
		}
		else if (type.equals(TextType.Lake))
		{
			final String nameBeforeLakeFormat = "%s Lake";
			String format = ProbabilityHelper.sampleCategorical(r,
					Arrays.asList(new Tuple2<>(0.6, nameBeforeLakeFormat), new Tuple2<>(0.4, "Lake %s")));

			if (format.equals(nameBeforeLakeFormat))
			{
				double probabilityOfCompiledName = nameCompiler.isEmpty() ? 0.0 : 0.5;
				if (r.nextDouble() < probabilityOfCompiledName)
				{
					return compileName(format, requireUnique);
				}
				else
				{
					double probabilityOfPersonName = 0.5;
					if (r.nextDouble() < probabilityOfPersonName)
					{
						// Person name
						// Make the name possessive.
						format = format.replace("%s", "%s's");
						return generatePersonName(format, requireUnique);
					}
					else
					{
						return generatePlaceName(format, requireUnique);
					}
				}
			}
			else
			{
				return generatePlaceName(format, requireUnique);
			}
		}
		else
		{
			throw new UnsupportedOperationException("Unknown text type: " + type);
		}
	}

	private String getOtherMountainNameFormat(OtherMountainsType mountainType)
	{
		switch (mountainType)
		{
		case Mountains:
			return "%s Mountains";
		case Peak:
			return "%s Peak";
		case Peaks:
			return "%s Peaks";
		default:
			throw new RuntimeException("Unknown mountain group type: " + mountainType);
		}

	}

	private String getRiverNameFormat(RiverType riverType)
	{
		String format;
		switch (riverType)
		{
		case Large:
			format = ProbabilityHelper.sampleCategorical(r, Arrays.asList(new Tuple2<>(0.1, "%s Wash"), new Tuple2<>(0.8, "%s River")));
			break;
		case Small:
			format = ProbabilityHelper.sampleCategorical(r, Arrays.asList(new Tuple2<>(0.1, "%s Bayou"), new Tuple2<>(0.2, "%s Creek"),
					new Tuple2<>(0.2, "%s Brook"), new Tuple2<>(0.5, "%s Stream")));
			break;
		default:
			throw new RuntimeException("Unknown river type: " + riverType);
		}

		return format;
	}

	private enum OtherMountainsType
	{
		Peaks, Mountains, Peak
	}

	private enum RiverType
	{
		Small, Large
	}

	private enum TitleType
	{
		NameOnly, Decorated
	}

	public String generatePlaceName(String format, boolean requireUnique)
	{
		return generatePlaceName(format, requireUnique, "");
	}

	public String generatePlaceName(String format, boolean requireUnique, String requiredPrefix)
	{
		if (placeNameGenerator.isEmpty() && !personNameGenerator.isEmpty())
		{
			// Switch to person names
			return generatePersonName(format, requireUnique, requiredPrefix);
		}
		Function0<String> nameCreator = () -> placeNameGenerator.generateName(requiredPrefix);
		return innerCreateUniqueName(format, requireUnique, nameCreator);
	}

	public String generatePersonName(String format, boolean requireUnique)
	{
		return generatePersonName(format, requireUnique, "");
	}

	public String generatePersonName(String format, boolean requireUnique, String requiredPrefix)
	{
		if (personNameGenerator.isEmpty() && !placeNameGenerator.isEmpty())
		{
			// Switch to place names
			return generatePlaceName(format, requireUnique, requiredPrefix);
		}
		Function0<String> nameCreator = () -> personNameGenerator.generateName(requiredPrefix);
		return innerCreateUniqueName(format, requireUnique, nameCreator);
	}

	private String compileName(String format, boolean requireUnique)
	{
		Function0<String> nameCreator = () -> nameCompiler.compileName();
		return innerCreateUniqueName(format, requireUnique, nameCreator);
	}

	private String innerCreateUniqueName(String format, boolean requireUnique, Function0<String> nameCreator)
	{
		final int maxRetries = 20;

		if (!requireUnique)
		{
			return String.format(format, nameCreator.apply());
		}

		for (@SuppressWarnings("unused")
		int retry : new Range(maxRetries))
		{
			String name = String.format(format, nameCreator.apply());
			if (!namesGenerated.contains(name))
			{
				namesGenerated.add(name);
				return name;
			}
		}
		throw new NotEnoughNamesException();

	}

	private void addTitle(BufferedImage map, WorldGraph graph, Graphics2D g)
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
				if (drawNameFitIntoCenters(map, g, generateNameOfType(TextType.Title, TitleType.Decorated, true),
						extractLocationsFromCenters(plateAndWidth.getFirst().centers), graph, settings.drawBoldBackground, true,
						TextType.Title))
				{
					return;
				}

				// The title didn't fit. Try drawing it with just a name.
				if (drawNameFitIntoCenters(map, g, generateNameOfType(TextType.Title, TitleType.NameOnly, true),
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
			if (e.river > VoronoiGraph.riversThisSizeOrSmallerWillNotBeDrawn && e != lastTohead)
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
		if (text.value.trim().split(" ").length > 1 && overlapsRegionLakeOrCoastline(line1Bounds, textLocationWithRiseOffsetIfDrawnInOneLine, text.angle, graph))
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
			g.rotate(text.angle, pivotMinusDrawOffset.x, pivotMinusDrawOffset.y);

			Area area1 = new Area(bounds1).createTransformedArea(g.getTransform());
			Area area2 = line2 == null ? null : new Area(bounds2).createTransformedArea(g.getTransform());
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
			text.line1Bounds = new java.awt.Rectangle((int) (bounds1.x - pivot.x),
					(int) (bounds1.y - pivot.y), bounds1.width, bounds1.height);
			text.line2Bounds = bounds2 == null ? null
					: new java.awt.Rectangle((int) (bounds2.x - pivot.x), (int) (bounds2.y - pivot.y),
							bounds2.width, bounds2.height);
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
					drawBackgroundBlendingForText(map, g, textStart, line1Size, text.angle, g.getFontMetrics(), line1, pivot);
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
					drawBackgroundBlendingForText(map, g, textStart, line2Size, text.angle, g.getFontMetrics(), line2, pivot);
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

	/**
	 * Creates a new MapText, taking settings.resolution into account.
	 */
	private MapText createMapText(String text, Point location, double angle, TextType type)
	{
		// Divide by settings.resolution so that the location does not depend on
		// the resolution we're drawing at.
		return new MapText(text, new Point(location.x / settings.resolution, location.y / settings.resolution), angle, type);
	}

	/**
	 * If the given point lands within the bounding box of a piece of text, this returns the first one found. Else null is returned.
	 */
	public MapText findTextPicked(nortantis.graph.geom.Point point)
	{
		java.awt.Point awtPoint = point.toAwtPoint();
		for (MapText mp : mapTexts)
		{
			if (mp.value.length() > 0)
			{
				if (mp.line1Area != null)
				{
					if (mp.line1Area.contains(awtPoint))
						return mp;
				}

				if (mp.line2Area != null)
				{
					if (mp.line2Area.contains(awtPoint))
						return mp;
				}
			}
		}
		return null;
	}

	public List<MapText> findTextSelectedByBrush(nortantis.graph.geom.Point point, double brushDiameter)
	{
		Area brush = new Area(
				new Ellipse2D.Double(point.x - brushDiameter / 2.0, point.y - brushDiameter / 2.0, brushDiameter, brushDiameter));
		List<MapText> result = new ArrayList<>();

		for (MapText mp : mapTexts)
		{
			if (mp.value.length() > 0)
			{
				if (doAreasIntersect(brush, mp.line1Area) || doAreasIntersect(brush, mp.line2Area))
				{
					result.add(mp);
				}
			}
		}
		return result;
	}

	public void setSettingsAndMapTexts(MapSettings mapSettings)
	{
		this.settings = mapSettings;
		if (mapSettings != null && mapSettings.edits != null && mapSettings.edits.text != null)
		{
			// Make sure the pointer to mapSettings.edits.text matches between
			// the one in this TextDrawer. They can be different because
			// undo/redo deep copy the map texts.
			mapTexts = mapSettings.edits.text;
		}
	}

	/**
	 * Adds text that the user is manually creating.
	 */
	public MapText createUserAddedText(TextType type, Point location)
	{
		String name = generateNameOfTypeForTextEditor(type);
		// Getting the id must be done after calling generateNameOfType because
		// said method increments textCounter
		// before generating the name.
		MapText mapText = createMapText(name, location, 0.0, type);
		return mapText;
	}

	public void setMapTexts(CopyOnWriteArrayList<MapText> text)
	{
		this.mapTexts = text;
	}

}
