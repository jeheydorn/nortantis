package nortantis;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import nortantis.MapSettings.LineStyle;
import nortantis.MapSettings.OceanEffect;
import nortantis.editor.UserPreferences;
import nortantis.geom.IntDimension;
import nortantis.platform.Color;
import nortantis.util.AssetsPath;
import nortantis.util.ProbabilityHelper;
import nortantis.util.Range;

/**
 * For randomly generating settings with which to generate a map.
 *
 */
public class SettingsGenerator
{
	private static String defaultSettingsFile = Paths.get(AssetsPath.getInstallPath(), "internal/old_paper.properties").toString();
	public static int minWorldSize = 2000;
	// This is larger than minWorldSize because, when someone opens the generator for the first time to a random map, very small world sizes
	// can result to in a map that is all land or all ocean.
	public static int minWorldSizeForRandomSettings = minWorldSize + 2000;
	public static int maxWorldSize = 30000; // This must not be more than 2^16 or centerLookupTable in WorldGraph will not work.
	public static int worldSizePrecision = 1000;
	public static double maxCityProbabillity = 1.0 / 40.0;
	public static int maxFrayedEdgeSizeForUI = 15;
	public static final int maxConcentricWaveCountInEditor = 5;
	public static final int maxConcentricWaveCountToGenerate = 3;
	public static final int minConcentricWaveCountToGenerate = 2;
	public static final int defaultCoastShadingAlpha = 87;
	public static final int defaultOceanShadingAlpha = 87;
	public static final int defaultOceanRipplesAlpha = 204;
	
	public static MapSettings generate(String imagesPath)
	{
		Random rand = new Random();
		return generate(rand, imagesPath);
	}

	public static MapSettings generate(Random rand, String imagesPath)
	{
		if (!Files.exists(Paths.get(defaultSettingsFile)))
		{
			throw new IllegalArgumentException("The default settings files " + defaultSettingsFile + " does not exist");
		}
		
		// Prime the random number generator
		for (int i = 0; i < 100; i++)
		{
			rand.nextInt();
		}

		MapSettings settings = new MapSettings(defaultSettingsFile);
		settings.pointPrecision = MapSettings.defaultPointPrecision;
		settings.lloydRelaxationsScale = MapSettings.defaultLloydRelaxationsScale;

		setRandomSeeds(settings, rand);

		int hueRange = 16;
		int saturationRange = 25;
		int brightnessRange = 25;

		Color landColor = rand.nextInt(2) == 1 ? settings.landColor : settings.oceanColor;
		Color oceanColor;
		if (settings.oceanEffect == OceanEffect.Ripples)
		{
			oceanColor = settings.oceanColor;
		}
		else if (landColor == settings.landColor)
		{
			oceanColor = settings.oceanColor;
		}
		else
		{
			oceanColor = rand.nextInt(2) == 1 ? settings.landColor : settings.oceanColor;
		}
		settings.oceanEffect = ProbabilityHelper.sampleEnumUniform(rand, OceanEffect.class);
		settings.drawOceanEffectsInLakes = true;
		settings.oceanEffectsLevel = 15 + Math.abs(rand.nextInt(35));
		settings.concentricWaveCount = Math.max(minConcentricWaveCountToGenerate,
				Math.min(maxConcentricWaveCountToGenerate, Math.abs((new Random().nextInt() % maxConcentricWaveCountInEditor)) + 1));
		settings.coastShadingLevel = 15 + Math.abs(rand.nextInt(35));

		settings.landColor = MapCreator.generateColorFromBaseColor(rand, landColor, hueRange, saturationRange, brightnessRange);
		settings.regionBaseColor = settings.landColor;

		settings.oceanColor = MapCreator.generateColorFromBaseColor(rand, oceanColor, hueRange, saturationRange, brightnessRange);

		double landBlurColorScale = 0.5;
		settings.coastShadingColor = Color.create((int) (settings.landColor.getRed() * landBlurColorScale),
				(int) (settings.landColor.getGreen() * landBlurColorScale), (int) (settings.landColor.getBlue() * landBlurColorScale),
				defaultCoastShadingAlpha);

		if (settings.oceanEffect == OceanEffect.Ripples || settings.oceanEffect == OceanEffect.Blur)
		{
			final int oceanEffectsAlpha = settings.oceanEffect == OceanEffect.Ripples ? defaultOceanRipplesAlpha : settings.oceanEffect == OceanEffect.Blur ? defaultOceanShadingAlpha : 0;
			double oceanEffectsColorScale = 0.3;
			settings.oceanEffectsColor = Color.create((int) (settings.oceanColor.getRed() * oceanEffectsColorScale),
					(int) (settings.oceanColor.getGreen() * oceanEffectsColorScale),
					(int) (settings.oceanColor.getBlue() * oceanEffectsColorScale), oceanEffectsAlpha);
		}
		else
		{
			// Concentric waves
			double oceanEffectsColorScale = 0.5;
			int alpha = 255;
			settings.oceanEffectsColor = Color.create((int) (settings.oceanColor.getRed() * oceanEffectsColorScale),
					(int) (settings.oceanColor.getGreen() * oceanEffectsColorScale),
					(int) (settings.oceanColor.getBlue() * oceanEffectsColorScale), alpha);

		}
		settings.riverColor = MapCreator.generateColorFromBaseColor(rand, settings.riverColor, hueRange, saturationRange, brightnessRange);
		settings.frayedBorderColor = MapCreator.generateColorFromBaseColor(rand, settings.frayedBorderColor, hueRange, saturationRange,
				brightnessRange);

		settings.worldSize = (rand.nextInt((maxWorldSize - minWorldSizeForRandomSettings) / worldSizePrecision)
				+ minWorldSizeForRandomSettings / worldSizePrecision) * worldSizePrecision;

		settings.grungeWidth = 100 + rand.nextInt(1400);

		settings.customImagesPath = UserPreferences.getInstance().defaultCustomImagesPath;
		settings.treeHeightScale = 0.35;

		final double drawBorderProbability = 0.75;
		settings.drawBorder = rand.nextDouble() <= drawBorderProbability;
		Set<String> borderTypes = MapCreator.getAvailableBorderTypes(imagesPath);
		if (!borderTypes.isEmpty())
		{
			// Random border type.
			int index = Math.abs(rand.nextInt()) % borderTypes.size();
			settings.borderType = borderTypes.toArray(new String[borderTypes.size()])[index];
			if (settings.borderType.equals("dashes"))
			{
				settings.borderWidth = Math.abs(rand.nextInt(50)) + 25;
			}
			else if (settings.borderType.equals("dashes with inset corners"))
			{
				settings.borderWidth = Math.abs(rand.nextInt(75)) + 50;
			}
			else
			{
				settings.borderWidth = Math.abs(rand.nextInt(200)) + 100;
			}
		}

		if (settings.drawBorder)
		{
			if (settings.borderType.equals("dashes"))
			{
				settings.frayedBorder = false;
			}
			else
			{
				settings.frayedBorder = rand.nextDouble() > 0.5;
			}
		}
		else
		{
			settings.frayedBorder = true;
		}
		settings.frayedBorderBlurLevel = Math.abs(rand.nextInt(150));
		// Fray size is stored inverted with respect the the UI.
		final int maxFraySize = 6;
		settings.frayedBorderSize = maxFrayedEdgeSizeForUI - Math.abs(rand.nextInt(maxFraySize));

		settings.cityProbability = 0.25 * maxCityProbabillity;

		Set<String> cityIconTypes = ImageCache.getInstance(settings.customImagesPath).getIconGroupNames(IconType.cities);
		if (cityIconTypes.size() > 0)
		{
			settings.cityIconTypeName = ProbabilityHelper.sampleUniform(rand, new ArrayList<>(cityIconTypes));
		}

		settings.drawPoliticalRegions = rand.nextDouble() > 0.25;
		settings.drawRegionColors = rand.nextDouble() > 0.25;

		if (rand.nextDouble() > 0.25)
		{
			settings.generateBackground = true;
			settings.generateBackgroundFromTexture = false;
		}
		else
		{
			settings.generateBackground = false;
			settings.generateBackgroundFromTexture = true;

		}

		// Always set a background texture even if it is not used so that the editor doesn't give an error when switching
		// to the background texture file path field.
		Path exampleTexturesPath = Paths.get(AssetsPath.getInstallPath(), "example textures");
		List<Path> textureFiles;
		try
		{
			textureFiles = Files.list(exampleTexturesPath).filter(path -> !Files.isDirectory(path)).collect(Collectors.toList());
		}
		catch (IOException ex)
		{
			throw new RuntimeException("The example textures folder does not exist.", ex);
		}

		if (textureFiles.size() > 0)
		{
			settings.backgroundTextureImage = ProbabilityHelper.sampleUniform(rand, textureFiles).toAbsolutePath().toString();
		}

		settings.drawBoldBackground = rand.nextDouble() > 0.5;
		settings.boldBackgroundColor = MapCreator.generateColorFromBaseColor(rand, settings.boldBackgroundColor, hueRange, saturationRange,
				brightnessRange);

		// This threshold prevents large maps from having land on the edge, because such maps should be the entire world/continent.
		int noOceanOnEdgeThreshold = 15000;
		if (settings.worldSize < noOceanOnEdgeThreshold)
		{
			settings.centerLandToWaterProbability = settings.worldSize / (double) noOceanOnEdgeThreshold;
			// Make the edge and center land water probability add up to 1 so there is usually both land and ocean.
			settings.edgeLandToWaterProbability = 1.0 - settings.centerLandToWaterProbability;
		}
		else
		{
			settings.centerLandToWaterProbability = 0.75 + rand.nextDouble() * 0.25;
			settings.edgeLandToWaterProbability = 0;
		}

		settings.edgeLandToWaterProbability = Math.round(settings.edgeLandToWaterProbability * 100.0) / 100.0;
		settings.centerLandToWaterProbability = Math.round(settings.centerLandToWaterProbability * 100.0) / 100.0;

		IntDimension dimension = parseGeneratedBackgroundDimensionsFromDropdown(
				ProbabilityHelper.sampleUniform(rand, getAllowedDimmensions()));
		settings.generatedWidth = dimension.width;
		settings.generatedHeight = dimension.height;

		settings.books.clear();
		List<String> allBooks = getAllBooks();
		if (allBooks.size() < 3)
		{
			settings.books.addAll(allBooks);
		}
		else if (allBooks.size() > 0)
		{
			int numBooks = 2 + Math.abs(rand.nextInt(allBooks.size() - 1));
			List<String> booksRemaining = new ArrayList<>(allBooks);
			for (@SuppressWarnings("unused")
			int ignored : new Range(numBooks))
			{
				int index = rand.nextInt(booksRemaining.size());
				settings.books.add(booksRemaining.get(index));
				booksRemaining.remove(index);
			}
		}

		settings.lineStyle = ProbabilityHelper.sampleEnumUniform(rand, LineStyle.class);

		return settings;
	}
	
	private static void setRandomSeeds(MapSettings settings, Random rand)
	{
		long seed = Math.abs(rand.nextInt());
		settings.randomSeed = seed;
		settings.regionsRandomSeed = seed;
		settings.backgroundRandomSeed = seed;
		settings.textRandomSeed = seed;
	}

	public static List<String> getAllowedDimmensions()
	{
		List<String> result = new ArrayList<>();
		result.add("4096 x 4096 (square)");
		result.add("4096 x 2304 (16 by 9)");
		result.add("4096 x 2531 (golden ratio)");
		return result;
	}

	public static IntDimension parseGeneratedBackgroundDimensionsFromDropdown(String selected)
	{
		selected = selected.substring(0, selected.indexOf("("));
		String[] parts = selected.split("x");
		return new IntDimension(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
	}

	public static List<String> getAllBooks()
	{
		String[] filenames = new File(Paths.get(AssetsPath.getInstallPath(), "books").toString()).list(new FilenameFilter()
		{
			public boolean accept(File arg0, String name)
			{
				return name.endsWith("_place_names.txt");
			}
		});

		List<String> result = new ArrayList<>();
		for (String filename : filenames)
		{
			result.add(filename.replace("_place_names.txt", ""));
		}
		Collections.sort(result);
		return result;
	}
}
