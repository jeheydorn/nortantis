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
import java.util.stream.Collectors;

import org.apache.commons.math3.exception.NoDataException;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import nortantis.graph.geom.Point;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.graph.voronoi.Edge;
import nortantis.util.AssetsPath;
import nortantis.util.Function0;
import nortantis.util.Helper;
import nortantis.util.ImageHelper;
import nortantis.util.Logger;
import nortantis.util.Pair;
import nortantis.util.ProbabilityHelper;
import nortantis.util.Range;
import nortantis.util.Tuple2;
import nortantis.util.Tuple4;

public class TextDrawer
{
	private MapSettings settings;
	private final int mountainRangeMinSize = 50;
	// y offset added to names of mountain groups smaller than a range.
	private final double mountainGroupYOffset = 67;
	// How big a river must be , in terms of edge.river, to be considered for
	// labeling.
	private final int riverMinWidth = 3;
	// Rivers shorter than this will not be named. This must be at least 3.
	private final int riverMinLength = 3;
	private final int largeRiverWidth = 4;
	// This is how far away from a river it's name will be drawn.
	private final double riverNameRiseHeight = -32;
	private final double cityYNameOffset = 18;
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
	 *            The map settings to use. Some of these settings are for text
	 *            drawing.
	 * @param sizeMultiplyer
	 *            The font size of text drawn will be multiplied by this value.
	 *            This allows the map to be scaled larger or smaller.
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

	public void drawTextFromEdits(BufferedImage map, BufferedImage landAndOceanBackground, WorldGraph graph)
	{
		this.landAndOceanBackground = landAndOceanBackground;

		drawTextFromEdits(map, graph);

		this.landAndOceanBackground = null;
	}

	public void generateText(WorldGraph graph, BufferedImage map, BufferedImage landAndOceanBackground, List<Set<Center>> mountainRanges,
			List<IconDrawTask> cityDrawTasks)
	{
		this.landAndOceanBackground = landAndOceanBackground;

		cityAreas = cityDrawTasks.stream().map(drawTask -> drawTask.createArea()).collect(Collectors.toList());

		if (mountainRanges == null)
		{
			mountainRanges = new ArrayList<>(0);
		}

		generateText(map, graph, mountainRanges, cityDrawTasks);

		this.landAndOceanBackground = null;
	}

	private void generateText(BufferedImage map, WorldGraph graph, List<Set<Center>> mountainRanges, List<IconDrawTask> cityDrawTasks)
	{
		// All text drawn must be done so in order from highest to lowest
		// priority because if I try to draw
		// text on top of other text, the latter will not be displayed.

		graphBounds = new Area(new java.awt.Rectangle(0, 0, graph.getWidth(), graph.getHeight()));

		Graphics2D g = ImageHelper.createGraphicsWithRenderingHints(map);
		g.setColor(settings.textColor);

		addTitle(map, graph, g);

		g.setFont(citiesAndOtherMountainsFontScaled);
		// Get the height of the city/mountain font.
		FontMetrics metrics = g.getFontMetrics(g.getFont());
		int cityMountainFontHeight = getFontHeight(metrics);
		for (IconDrawTask city : cityDrawTasks)
		{
			Set<Point> cityLoc = new HashSet<>(1);
			cityLoc.add(city.centerLoc);
			String cityName = generateNameOfType(TextType.City, findCityTypeFromCityFileName(city.fileName), true);
			drawNameRotated(map, g, cityName, cityLoc,
					city.scaledHeight / 2 + (cityYNameOffset + cityMountainFontHeight / 2.0) * settings.resolution, true, TextType.City);
		}

		g.setFont(regionFontScaled);
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

		for (Set<Center> mountainRange : mountainRanges)
		{
			if (mountainRange.size() >= mountainRangeMinSize)
			{
				g.setFont(mountainRangeFontScaled);
				Set<Point> locations = extractLocationsFromCenters(mountainRange);
				drawNameRotated(map, g, generateNameOfType(TextType.Mountain_range, null, true), locations, true, TextType.Mountain_range);
			}
			else
			{
				g.setFont(citiesAndOtherMountainsFontScaled);
				if (mountainRange.size() >= 2)
				{
					if (mountainRange.size() == 2)
					{
						Point location = findCentroid(extractLocationsFromCenters(mountainRange));
						MapText text = createMapText(generateNameOfType(TextType.Other_mountains, OtherMountainsType.TwinPeaks, true),
								location, 0.0, TextType.Other_mountains);
						if (drawNameRotated(map, g, mountainGroupYOffset * settings.resolution, true, text, false))
						{
							mapTexts.add(text);
						}
					}
					else
					{
						drawNameRotated(map, g, generateNameOfType(TextType.Other_mountains, OtherMountainsType.Mountains, true),
								extractLocationsFromCenters(mountainRange), mountainGroupYOffset * settings.resolution, true,
								TextType.Other_mountains);
					}
				}
				else
				{
					Point location = findCentroid(extractLocationsFromCenters(mountainRange));
					MapText text = createMapText(generateNameOfType(TextType.Other_mountains, OtherMountainsType.Peak, true), location, 0.0,
							TextType.Other_mountains);
					if (drawNameRotated(map, g, mountainGroupYOffset * settings.resolution, true, text, false))
					{
						mapTexts.add(text);
					}
				}
			}
		}

		g.setFont(riverFontScaled);
		List<River> rivers = findRivers(graph);
		for (River river : rivers)
		{
			if (river.size() >= riverMinLength)
			{
				RiverType riverType = river.getWidth() >= largeRiverWidth ? RiverType.Large : RiverType.Small;

				Set<Point> locations = extractLocationsFromCorners(river.getCorners());
				drawNameRotated(map, g, generateNameOfType(TextType.River, riverType, true), locations,
						riverNameRiseHeight * settings.resolution, true, TextType.River);
			}

		}

		g.dispose();
	}

	private CityType findCityTypeFromCityFileName(String cityFileNameNoExtension)
	{
		String name = cityFileNameNoExtension.toLowerCase();
		if (name.contains("fort") || name.contains("castle") || name.contains("keep") || name.contains("citadel"))
		{
			return CityType.Fortification;
		}
		else if (name.contains("city") || name.contains("buildings"))
		{
			return CityType.City;
		}
		else if (name.contains("town") || name.contains("village") || name.contains("houses"))
		{
			return CityType.Town;
		}
		else if (name.contains("farm") || name.contains("homestead") || name.contains("building") || name.contains("house"))
		{
			return CityType.Homestead;
		}
		else
		{
			return ProbabilityHelper.sampleEnumUniform(r, CityType.class);
		}
	}

	/**
	 * Draw text which was added by the user.
	 * 
	 * @param map
	 * @param graph
	 * @param g
	 */
	private synchronized void drawTextFromEdits(BufferedImage map, WorldGraph graph)
	{
		Graphics2D g = ImageHelper.createGraphicsWithRenderingHints(map);

		g.setColor(settings.textColor);

		// Draw all text the user has (potentially) modified.
		for (MapText text : settings.edits.text)
		{
			if (text.value == null || text.value.trim().length() == 0)
			{
				continue;
			}

			Point textLocation = new Point(text.location.x * settings.resolution, text.location.y * settings.resolution);

			if (text.type == TextType.Title)
			{
				g.setFont(titleFontScaled);
				Center center = graph.findClosestCenter(textLocation.x, textLocation.y);
				Set<Center> regionCenters;
				if (center.isWater)
				{
					regionCenters = findAllWaterCenters(graph);
				}
				else
				{
					regionCenters = center.region.getCenters();
				}
				Set<Point> locations = extractLocationsFromCenters(regionCenters);
				drawNameFitIntoCenters(map, g, locations, graph, settings.drawBoldBackground, false, text);
			}
			else if (text.type == TextType.City)
			{
				g.setFont(citiesAndOtherMountainsFontScaled);
				drawNameRotated(map, g, 0, false, text, false);
			}
			else if (text.type == TextType.Region)
			{
				g.setFont(regionFontScaled);
				Center center = graph.findClosestCenter(textLocation.x, textLocation.y);
				Set<Center> regionCenters;
				if (center.isWater)
				{
					regionCenters = findAllWaterCenters(graph);
				}
				else
				{
					regionCenters = center.region.getCenters();
				}
				Set<Point> locations = extractLocationsFromCenters(regionCenters);
				drawNameFitIntoCenters(map, g, locations, graph, settings.drawBoldBackground, false, text);
			}
			else if (text.type == TextType.Mountain_range)
			{
				g.setFont(mountainRangeFontScaled);
				drawNameRotated(map, g, 0, false, text, false);
			}
			else if (text.type == TextType.Other_mountains)
			{
				g.setFont(citiesAndOtherMountainsFontScaled);
				drawNameRotated(map, g, 0, false, text, false);
			}
			else if (text.type == TextType.River)
			{
				g.setFont(riverFontScaled);
				drawNameRotated(map, g, 0, false, text, false);
			}
		}

		g.dispose();
	}

	/**
	 * Generates a name of the specified type. This is for when the user adds
	 * new text to the map. It is not used when the map text is first generated.
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
	 *            A sub-type specific to the type specified. null means default
	 *            type.
	 * @param requireUnique
	 *            Whether generated names must be never seen in the extracted
	 *            book names nor previously generated. If unique name generating
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
			double probabilityOfCompiledName = 0.7;
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
			double probabilityOfCompiledName = 0.5;
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
			double probabilityOfCompiledName = 0.5;
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
		case TwinPeaks:
			return "%s Twin Peaks";
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
		TwinPeaks, Mountains, Peak
	}

	private enum RiverType
	{
		Small, Large
	}

	private enum TitleType
	{
		NameOnly, Decorated
	}

	private enum CityType
	{
		Fortification, City, Town, Homestead,
	}

	public String generatePlaceName(String format, boolean requireUnique)
	{
		Function0<String> nameCreator = () -> placeNameGenerator.generateName();
		return innerCreateUniqueName(format, requireUnique, nameCreator);
	}

	public String generatePersonName(String format, boolean requireUnique)
	{
		Function0<String> nameCreator = () -> personNameGenerator.generateName();
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
		throw new RuntimeException(
				"Unable to create enough unique names. You can select more books, or shrink the world size, or try a different seed.");

	}

	private void addTitle(BufferedImage map, WorldGraph graph, Graphics2D g)
	{
		g.setFont(titleFontScaled);

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

	private Set<Point> extractLocationsFromCorners(Collection<Corner> corners)
	{
		Set<Point> result = new TreeSet<Point>();
		for (Corner c : corners)
		{
			result.add(c.loc);
		}
		return result;
	}

	/**
	 * For finding rivers.
	 */
	private List<River> findRivers(WorldGraph graph)
	{
		List<River> rivers = new ArrayList<>();
		Set<Corner> explored = new HashSet<>();
		for (Edge edge : graph.edges)
		{
			if (edge.river >= riverMinWidth && edge.v0 != null && edge.v1 != null && !explored.contains(edge.v0)
					&& !explored.contains(edge.v1))
			{
				River river = followRiver(edge.v0, edge.v1);

				// This count shouldn't be necessary. For some reason
				// followRiver(...) is returning
				// rivers which contain many Corners already in explored.
				int count = 0;
				for (Corner c : river)
				{
					if (explored.contains(c))
						count++;
				}

				explored.addAll(river.getCorners());

				if (count < 3)
					rivers.add(river);
			}
		}

		return rivers;
	}

	/**
	 * Searches along edges to find corners which are connected by a river. If
	 * the river forks, only one direction is followed.
	 * 
	 * @param last
	 *            The search will not go in the direction of this corner.
	 * @param head
	 *            The search will go in the direction of this corner.
	 * @return A set of corners which form a river.
	 */
	private River followRiver(Corner last, Corner head)
	{
		assert last != null;
		assert head != null;
		assert !head.equals(last);

		River result = new River();
		result.add(head);
		result.add(last);

		Set<Edge> riverEdges = new TreeSet<>();
		for (Edge e : head.protrudes)
			if (e.river >= riverMinWidth)
				riverEdges.add(e);

		if (riverEdges.size() == 0)
		{
			throw new IllegalArgumentException("\"last\" should be connected to head by a river edge");
		}
		if (riverEdges.size() == 1)
		{
			// base case
			return result;
		}
		if (riverEdges.size() == 2)
		{
			// Find the other river corner which is not "last".
			Corner other = null;
			for (Edge e : riverEdges)
				if (head.equals(e.v0) && !last.equals(e.v1))
				{
					other = e.v1;
				}
				else if (head.equals(e.v1) && !last.equals(e.v0))
				{
					other = e.v0;
				}

			if (other == null)
			{
				// The only direction this river can go goes to a null corner.
				// This is a base case.
				return result;
			}

			result.addAll(followRiver(head, other));
			return result;
		}
		else
		{
			// There are more than 2 river edges connected to head.

			// Sort the river edges by river width.
			List<Edge> edgeList = new ArrayList<>(riverEdges);
			Collections.sort(edgeList, new Comparator<Edge>()
			{
				public int compare(Edge e0, Edge e1)
				{
					return -Integer.compare(e0.river, e1.river);
				}
			});
			Corner nextHead = null;

			// Find which edge contains "last".
			int indexOfLast = -1;
			for (int i : new Range(edgeList.size()))
			{
				if (last == edgeList.get(i).v0 || last == edgeList.get(i).v1)
				{
					indexOfLast = i;
					break;
				}
			}
			assert indexOfLast != -1;

			// Are there 2 edges which are wider rivers than all others?
			if (edgeList.get(1).river > edgeList.get(2).river)
			{

				// If last is one of those 2.
				if (indexOfLast < 2)
				{
					// nextHead = the other larger option.
					Edge nextHeadEdge;
					if (indexOfLast == 0)
					{
						nextHeadEdge = edgeList.get(1);
					}
					else
					{
						nextHeadEdge = edgeList.get(0);
					}

					if (!head.equals(nextHeadEdge.v0))
					{
						nextHead = nextHeadEdge.v0;
						assert nextHead != null;
					}
					else if (!head.equals(nextHeadEdge.v1))
					{
						nextHead = nextHeadEdge.v1;
						assert nextHead != null;
					}
					else
					{
						assert false; // Both corners cannot be the head.
					}

				}
				else
				{
					// This river is joining a larger river. This is a base case
					// because the smaller
					// river should have a different name than the larger one.
					return result;
				}
			}
			else
			{
				// Choose the option with the largest river, avoiding choosing
				// "last".
				edgeList.remove(indexOfLast);

				nextHead = head.equals(edgeList.get(0).v0) ? edgeList.get(0).v1 : edgeList.get(0).v0;
				assert nextHead != null;
			}
			// Leave the other options for the global search to hit later.

			result.addAll(followRiver(head, nextHead));
			return result;
		}
	}

	/**
	 * Draws the given name to the map with the area around the name drawn from
	 * landAndOceanBackground to make it readable when the name is drawn on top
	 * of mountains or trees.
	 */
	private void drawBackgroundBlendingForText(BufferedImage map, Graphics2D g, Point textStart, Dimension textSize, double angle,
			FontMetrics metrics, String text, Point pivot)
	{
		// This magic number below is a result of trial and error to get the
		// blur levels to look right.
		int kernelSize = (int) ((13.0 / 54.0) * textSize.height);
		if (kernelSize == 0)
		{
			return;
		}
		int padding = kernelSize / 2;

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
		if (drawNameFitIntoCenters(map, g, centerLocations, graph, boldBackground, enableBoundsChecking, text))
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
				if (drawNameFitIntoCenters(map, g, centerLocations, graph, boldBackground, enableBoundsChecking, text))
				{
					mapTexts.add(text);
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Draws the given name at the given location (centroid). If the name cannot
	 * be drawn on one line and still fit with the given locations, then it will
	 * be drawn on 2 lines.
	 * 
	 * The actual drawing step is skipped if settings.drawText = false.
	 * 
	 * @return True iff text was drawn.
	 */
	private boolean drawNameFitIntoCenters(BufferedImage map, Graphics2D g, Set<Point> centerLocations, WorldGraph graph,
			boolean boldBackground, boolean enableBoundsChecking, MapText text)
	{
		FontMetrics metrics = g.getFontMetrics(g.getFont());
		int width = metrics.stringWidth(text.value);
		int height = getFontHeight(metrics);
		Point textLocation = new Point(text.location.x * settings.resolution, text.location.y * settings.resolution);
		Point upperLeftCornerRotated = rotate(new Point(textLocation.x - width / 2, textLocation.y - height / 2), textLocation, text.angle);
		Point lowerLeftCornerRotated = rotate(new Point(textLocation.x - width / 2, textLocation.y + height / 2), textLocation, text.angle);
		Point upperRightCornerRotated = rotate(new Point(textLocation.x + width / 2, textLocation.y - height / 2), textLocation,
				text.angle);
		Point lowerRightCornerRotated = rotate(new Point(textLocation.x + width / 2, textLocation.y + height / 2), textLocation,
				text.angle);

		if (text.value.split(" ").length > 1 && (!centerLocations.contains(graph.findClosestCenter(upperLeftCornerRotated).loc)
				|| !centerLocations.contains(graph.findClosestCenter(lowerLeftCornerRotated).loc)
				|| !centerLocations.contains(graph.findClosestCenter(upperRightCornerRotated).loc)
				|| !centerLocations.contains(graph.findClosestCenter(lowerRightCornerRotated).loc)))
		{
			// The text doesn't fit into centerLocations. Draw it split onto two
			// lines.
			Pair<String> lines = addLineBreakNearMiddleIfPossible(text.value);
			String nameLine1 = lines.getFirst();
			String nameLine2 = lines.getSecond();

			return drawNameRotated(map, g, 0.0, enableBoundsChecking, text, boldBackground, nameLine1, nameLine2, true);
		}
		else
		{
			return drawNameRotated(map, g, 0.0, enableBoundsChecking, text, boldBackground, text.value, null, true);
		}
	}

	public static Point rotate(Point point, Point pivot, double angle)
	{
		double sin = Math.sin(angle);
		double cos = Math.cos(angle);
		double newX = (cos * (point.x - pivot.x)) - (sin * (point.y - pivot.y)) + pivot.x;
		double newY = (sin * (point.x - pivot.x)) + (cos * (point.y - pivot.y)) + pivot.y;
		return new Point(newX, newY);
	}

	private Pair<String> addLineBreakNearMiddleIfPossible(String name)
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

	public void drawNameRotated(BufferedImage map, Graphics2D g, String name, Set<Point> locations, boolean enableBoundsChecking,
			TextType type)
	{
		drawNameRotated(map, g, name, locations, 0.0, enableBoundsChecking, type);
	}

	/**
	 * Draws the given name at the centroid of the given plateCenters. The angle
	 * the name is drawn at is the least squares line through the plate centers.
	 * This does not break text into multiple lines.
	 * 
	 * Side effect: This adds a new MapText to mapTexts.
	 * 
	 * @param riseOffset
	 *            The text will be raised (positive y) by this much distance
	 *            above the centroid when drawn. The rotation will be applied to
	 *            this location. If there is already a name drawn above the
	 *            object, I try negating the riseOffset to draw the name below
	 *            it. Positive y is down.
	 */
	public void drawNameRotated(BufferedImage map, Graphics2D g, String name, Set<Point> locations, double riseOffset,
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
			// Move the intercept to the origin.
			y1 -= y0;
			y0 = 0;
			angle = Math.atan(y1 / 1.0);
		}
		catch (NoDataException e)
		{
			// This happens if the regression had only 2 or fewer points.
			angle = 0;
		}

		MapText text = createMapText(name, centroid, angle, type);
		if (drawNameRotated(map, g, riseOffset, enableBoundsChecking, text, false))
		{
			mapTexts.add(text);
		}
	}

	/**
	 * Draws the given name at the given location (centroid), at the given
	 * angle. This does not break text into multiple lines.
	 * 
	 * If settings.drawText = false, then this method will not do the actual
	 * text writing, but will still update the MapText text.
	 * 
	 * @param riseOffset
	 *            The text will be raised (positive y) by this much distance
	 *            above the centroid when drawn. The rotation will be applied to
	 *            this location. If there is already a name drawn above the
	 *            object, I try negating the riseOffset to draw the name below
	 *            it. Positive y is down.
	 * 
	 * @return true iff the text was drawn.
	 */
	public boolean drawNameRotated(BufferedImage map, Graphics2D g, double riseOffset, boolean enableBoundsChecking, MapText text,
			boolean boldBackground)
	{
		return drawNameRotated(map, g, riseOffset, enableBoundsChecking, text, boldBackground, text.value, null, true);
	}

	public boolean drawNameRotated(BufferedImage map, Graphics2D g, double riseOffset, boolean enableBoundsChecking, MapText text,
			boolean boldBackground, String line1, String line2, boolean allowNegatingRizeOffset)
	{
		if (line2 != null && line2.equals(""))
		{
			line2 = null;
		}

		Point textLocation = new Point(text.location.x * settings.resolution, text.location.y * settings.resolution);
		Dimension line1Size = getTextDimensions(line1, g.getFontMetrics());
		Dimension line2Size = line2 == null ? null : getTextDimensions(line2, g.getFontMetrics());

		if (line1Size.width == 0 || line1Size.height == 0)
		{
			// The text is too small to draw.
			return false;
		}

		if (line2Size != null && (line2Size.width == 0 || line2Size.height == 0))
		{
			// There is a second line, and it's too small to draw.
			return false;
		}

		Point offset = new Point(riseOffset * Math.sin(text.angle), -riseOffset * Math.cos(text.angle));
		Point pivot = new Point(textLocation.x - offset.x, textLocation.y - offset.y);

		AffineTransform orig = g.getTransform();
		try
		{
			g.rotate(text.angle, pivot.x, pivot.y);

			int fontHeight = getFontHeight(g.getFontMetrics());
			// Make sure we don't draw on top of existing text.
			java.awt.Rectangle bounds1 = new java.awt.Rectangle((int) (pivot.x - line1Size.width / 2),
					(int) (pivot.y - line1Size.height / 2) - (line2 == null ? 0 : fontHeight / 2), line1Size.width, line1Size.height);
			java.awt.Rectangle bounds2 = line2 == null ? null
					: new java.awt.Rectangle((int) (pivot.x - line2Size.width / 2),
							(int) (pivot.y - (line2Size.height / 2) + fontHeight / 2), line2Size.width, line2Size.height);
			Area area1 = new Area(bounds1).createTransformedArea(g.getTransform());
			Area area2 = line2 == null ? null : new Area(bounds2).createTransformedArea(g.getTransform());
			if (enableBoundsChecking
					&& (overlapsExistingTextOrCityOrIsOffMap(area1) || (line2 != null && overlapsExistingTextOrCityOrIsOffMap(area2))))
			{
				// If there is a riseOffset, try negating it to put the name
				// below the object instead of above.
				if (riseOffset != 0.0 && allowNegatingRizeOffset)
				{
					g.setTransform(orig);
					return drawNameRotated(map, g, -riseOffset, enableBoundsChecking, text, boldBackground, line1, line2, false);
				}
				else
				{
					// Give up
					return false;
				}
			}
			text.line1Area = area1;
			text.line2Area = area2;
			// Store the bounds centered at the origin so that the editor can use the bounds to draw the text boxes of text being moved before the text is redrawn.
			text.line1Bounds = new java.awt.Rectangle((int)(bounds1.x - textLocation.x), (int)(bounds1.y - textLocation.y), bounds1.width, bounds1.height);
			text.line2Bounds = bounds2 == null ? null : new java.awt.Rectangle((int)(bounds2.x - textLocation.x), (int)(bounds2.y - textLocation.y), bounds2.width, bounds2.height);
			if (riseOffset != 0)
			{
				// Update the text location with the offset. This only happens when generating new text, not when making changes in the editor.
				text.location = new Point(pivot.x / settings.resolution, pivot.y / settings.resolution);
			}

			if (settings.drawText)
			{
				{
					Point textStart = new Point(bounds1.x, bounds1.y + g.getFontMetrics().getAscent());
					drawBackgroundBlendingForText(map, g, textStart, line1Size, text.angle, g.getFontMetrics(), line1, textLocation);
					if (boldBackground)
					{
						drawStringWithBoldBackground(g, line1, textStart, text.angle, textLocation);
					}
					else
					{
						g.drawString(line1, (int) textStart.x, (int) textStart.y);
					}
				}
				if (line2 != null)
				{
					Point textStart = new Point(bounds2.x, bounds2.y + g.getFontMetrics().getAscent());
					drawBackgroundBlendingForText(map, g, textStart, line2Size, text.angle, g.getFontMetrics(), line2, textLocation);
					if (boldBackground)
					{
						drawStringWithBoldBackground(g, line2, textStart, text.angle, textLocation);
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

	private Set<Center> findAllWaterCenters(final WorldGraph graph)
	{
		Set<Center> waterCenters = new HashSet<Center>();
		for (Center c : graph.centers)
		{
			if (c.isWater)
				waterCenters.add(c);
		}
		return waterCenters;
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
	 * If the given point lands within the bounding box of a piece of text, this
	 * returns the first one found. Else null is returned.
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
		Area brush = new Area(new Ellipse2D.Double(point.x - brushDiameter/2.0, point.y - brushDiameter/2.0, brushDiameter, brushDiameter));
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
