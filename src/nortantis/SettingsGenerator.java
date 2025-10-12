package nortantis;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.StringUtils;

import nortantis.MapSettings.LineStyle;
import nortantis.MapSettings.OceanWaves;
import nortantis.geom.IntDimension;
import nortantis.platform.Color;
import nortantis.util.Assets;
import nortantis.util.ProbabilityHelper;
import nortantis.util.Range;
import nortantis.util.Tuple2;

/**
 * For randomly generating settings with which to generate a map.
 *
 */
public class SettingsGenerator
{
	private static String defaultSettingsFile = Paths.get(Assets.getAssetsPath(), "internal/old_paper.properties").toString();
	public static int minWorldSize = 2000;
	// This is larger than minWorldSize because, when someone opens the generator for the first time to a random map, very small world sizes
	// can result to in a map that is all land or all ocean.
	public static int minWorldSizeForRandomSettings = minWorldSize + 2000;
	public static int maxWorldSize = 32000; // This must not be more than 2^16 or centerLookupTable in WorldGraph will not work.
	public static int worldSizePrecision = 1000;
	public static double maxCityProbabillity = 1.0 / 40.0;
	public static int maxFrayedEdgeSizeForUI = 15;
	public static final int maxConcentricWaveCountInEditor = 5;
	public static final int maxConcentricWaveCountToGenerate = 3;
	public static final int minConcentricWaveCountToGenerate = 2;
	public static final int defaultCoastShadingAlpha = 87;
	public static final int defaultOceanShadingAlpha = 87;
	public static final int defaultOceanRipplesAlpha = 204;
	public static final float maxLineWidthInEditor = 10f;

	public static MapSettings generate(String customImageFolder)
	{
		Random rand = new Random();
		String artPack = ProbabilityHelper.sampleUniform(rand, Assets.listArtPacks(!StringUtils.isEmpty(customImageFolder)));
		return generate(rand, artPack, customImageFolder);
	}

	public static MapSettings generate(Random rand, String artPack, String customImagesFolder)
	{
		// Prime the random number generator
		for (int i = 0; i < 100; i++)
		{
			rand.nextInt();
		}

		MapSettings settings = new MapSettings(defaultSettingsFile);
		settings.pointPrecision = MapSettings.defaultPointPrecision;
		settings.lloydRelaxationsScale = MapSettings.defaultLloydRelaxationsScale;

		setRandomSeeds(settings, rand);

		if (artPack == null)
		{
			throw new IllegalArgumentException("artPack cannot be null.");
		}
		settings.artPack = artPack;
		settings.customImagesPath = customImagesFolder;

		List<Tuple2<Double, OceanWaves>> oceanWaveOptions = new ArrayList<>(
				Arrays.asList(new Tuple2<Double, OceanWaves>(1.0, OceanWaves.None), new Tuple2<Double, OceanWaves>(1.0, OceanWaves.Ripples),
						new Tuple2<Double, OceanWaves>(2.0, OceanWaves.ConcentricWaves)));

		settings.oceanWavesType = ProbabilityHelper.sampleCategorical(rand, oceanWaveOptions);

		Color landColor = rand.nextInt(2) == 1 ? settings.landColor : settings.oceanColor;
		Color oceanColor;
		if (settings.oceanWavesType == OceanWaves.Ripples)
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

		settings.drawOceanEffectsInLakes = true;
		settings.oceanWavesLevel = 15 + Math.abs(rand.nextInt(35));
		settings.oceanShadingLevel = 0;
		if (settings.oceanWavesType == OceanWaves.ConcentricWaves)
		{
			settings.fadeConcentricWaves = rand.nextBoolean();
			settings.jitterToConcentricWaves = rand.nextBoolean();
			settings.brokenLinesForConcentricWaves = rand.nextBoolean();
		}
		settings.concentricWaveCount = Math.max(minConcentricWaveCountToGenerate,
				Math.min(maxConcentricWaveCountToGenerate, Math.abs((rand.nextInt() % maxConcentricWaveCountInEditor)) + 1));
		settings.coastShadingLevel = 15 + Math.abs(rand.nextInt(35));

		int hueRange = 16;
		int saturationRange = 25;
		int brightnessRange = 25;
		settings.landColor = MapCreator.generateColorFromBaseColor(rand, landColor, hueRange, saturationRange, brightnessRange);
		settings.regionBaseColor = settings.landColor;
		settings.borderColor = settings.landColor;

		settings.oceanColor = MapCreator.generateColorFromBaseColor(rand, oceanColor, hueRange, saturationRange, brightnessRange);

		double landBlurColorScale = 0.5;
		settings.coastShadingColor = Color.create((int) (settings.landColor.getRed() * landBlurColorScale),
				(int) (settings.landColor.getGreen() * landBlurColorScale), (int) (settings.landColor.getBlue() * landBlurColorScale),
				defaultCoastShadingAlpha);

		{
			double oceanShadingColorScale = 0.3;
			settings.oceanShadingColor = Color.create((int) (settings.oceanColor.getRed() * oceanShadingColorScale),
					(int) (settings.oceanColor.getGreen() * oceanShadingColorScale),
					(int) (settings.oceanColor.getBlue() * oceanShadingColorScale), defaultOceanShadingAlpha);
		}

		if (settings.oceanWavesType == OceanWaves.None)
		{
			// Use ocean shading instead.
			// Not that I don't generate a map that uses both shading and waves because although it can look nice, it renders slowly, so I
			// don't encourage it.
			settings.oceanShadingLevel = 20 + Math.abs(rand.nextInt(40));
		}

		if (settings.oceanWavesType == OceanWaves.Ripples)
		{
			double ripplesColorScale = 0.3;
			settings.oceanWavesColor = Color.create((int) (settings.oceanColor.getRed() * ripplesColorScale),
					(int) (settings.oceanColor.getGreen() * ripplesColorScale), (int) (settings.oceanColor.getBlue() * ripplesColorScale),
					defaultOceanRipplesAlpha);
		}
		else
		{
			// Concentric waves
			double wavesColorScale = 0.5;
			int alpha = 255;
			settings.oceanWavesColor = Color.create((int) (settings.oceanColor.getRed() * wavesColorScale),
					(int) (settings.oceanColor.getGreen() * wavesColorScale), (int) (settings.oceanColor.getBlue() * wavesColorScale),
					alpha);

		}
		settings.riverColor = MapCreator.generateColorFromBaseColor(rand, settings.riverColor, hueRange, saturationRange, brightnessRange);
		settings.frayedBorderColor = MapCreator.generateColorFromBaseColor(rand, settings.frayedBorderColor, hueRange, saturationRange,
				brightnessRange);

		settings.worldSize = (rand.nextInt((maxWorldSize - minWorldSizeForRandomSettings) / worldSizePrecision)
				+ minWorldSizeForRandomSettings / worldSizePrecision) * worldSizePrecision;

		settings.grungeWidth = 100 + rand.nextInt(1400);

		settings.treeHeightScale = 0.4;
		settings.mountainScale = 1.2;
		settings.hillScale = 1.2;
		settings.duneScale = 1.2;
		settings.cityScale = 1.2;

		final double drawBorderProbability = 0.75;
		settings.drawBorder = rand.nextDouble() <= drawBorderProbability;
		List<NamedResource> borderTypes = Assets.listBorderTypesForArtPack(artPack, customImagesFolder);
		if (borderTypes.isEmpty())
		{
			borderTypes = Assets.listAllBorderTypes(customImagesFolder);
		}
		// Note- borderTypes shouldn't be empty since that would mean there's no border types, including installed ones.
		if (!borderTypes.isEmpty())
		{
			// Random border type.
			settings.borderResource = ProbabilityHelper.sampleUniform(rand, borderTypes);

			if (settings.borderResource.name.equals("dashes"))
			{
				settings.borderWidth = Math.abs(rand.nextInt(50)) + 25;
			}
			else if (settings.borderResource.name.equals("dashes with inset corners"))
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
			if (settings.borderResource.name.equals("dashes"))
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

		List<String> cityIconTypes = ImageCache.getInstance(settings.artPack, settings.customImagesPath).getIconGroupNames(IconType.cities);
		if (cityIconTypes.size() > 0)
		{
			settings.cityIconTypeName = ProbabilityHelper.sampleUniform(rand, new ArrayList<>(cityIconTypes));
		}

		settings.drawRegionBoundaries = rand.nextDouble() > 0.25;
		settings.drawRegionColors = rand.nextDouble() > 0.25;
		settings.regionBoundaryStyle = new Stroke(ProbabilityHelper.sampleEnumUniform(rand, StrokeType.class),
				settings.regionBoundaryStyle.width);

		if (rand.nextDouble() > 0.75)
		{
			settings.generateBackground = true;
			settings.generateBackgroundFromTexture = false;
		}
		else
		{
			settings.generateBackground = false;
			settings.generateBackgroundFromTexture = true;
		}
		settings.solidColorBackground = false;

		// Always set a background texture even if it is not used so that the editor doesn't give an error when switching
		// to the background texture file path field.
		List<NamedResource> textureFiles = Assets.listBackgroundTexturesForArtPack(artPack, settings.customImagesPath);
		if (textureFiles.isEmpty())
		{
			textureFiles = Assets.listBackgroundTexturesForAllArtPacks(settings.customImagesPath);
		}

		settings.backgroundTextureResource = ProbabilityHelper.sampleUniform(rand, textureFiles);
		settings.backgroundTextureSource = TextureSource.Assets;

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

		settings.drawRoads = true;
		// Make sure the road line style is sufficiently different from region boundary style.
		if (settings.regionBoundaryStyle.type == StrokeType.Dashes || settings.regionBoundaryStyle.type == StrokeType.Rounded_Dashes)
		{
			settings.roadStyle = new Stroke(StrokeType.Dots, settings.roadStyle.width);
		}
		else if (settings.regionBoundaryStyle.type == StrokeType.Dots)
		{
			settings.roadStyle = new Stroke(
					ProbabilityHelper.sampleUniform(rand, Arrays.asList(StrokeType.Dashes, StrokeType.Rounded_Dashes)),
					settings.roadStyle.width);
		}
		else
		{
			settings.roadStyle = new Stroke(
					ProbabilityHelper.sampleUniform(rand, Arrays.asList(StrokeType.Dashes, StrokeType.Rounded_Dashes, StrokeType.Dots)),
					settings.roadStyle.width);
		}

		return settings;
	}

	private static void setRandomSeeds(MapSettings settings, Random rand)
	{
		long seed = Math.abs(rand.nextInt());
		settings.randomSeed = seed;
		settings.regionsRandomSeed = seed;
		settings.backgroundRandomSeed = seed;
		settings.frayedBorderSeed = seed;
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
		List<String> filenames = Assets.listFileNames(Paths.get(Assets.getAssetsPath(), "books").toString(), null, "_place_names.txt",
				null);

		List<String> result = new ArrayList<>();
		for (String filename : filenames)
		{
			result.add(filename.replace("_place_names.txt", ""));
		}
		Collections.sort(result);
		return result;
	}
}
