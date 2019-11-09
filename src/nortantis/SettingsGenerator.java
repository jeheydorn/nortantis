package nortantis;

import java.awt.Color;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import nortantis.MapSettings.LineStyle;
import nortantis.MapSettings.OceanEffect;
import nortantis.util.AssetsPath;
import nortantis.util.ProbabilityHelper;
import nortantis.util.Range;

/**
 * For randomly generating settings with which to generate a map.
 *
 */
public class SettingsGenerator
{
	private static String defaultSettingsFile = Paths.get(AssetsPath.get(), "internal/old_paper.properties").toString();
	public static int minWorldSize = 2000;
	public static int maxWorldSize = 30000;
	public static int worldSizePrecision = 1000;
	public static double maxCityProbabillity = 1.0/40.0;

	public static MapSettings generate()
	{
		if (!Files.exists(Paths.get(defaultSettingsFile)))
		{
			throw new IllegalArgumentException("The default settings files " + defaultSettingsFile + " does not exist");
		}
		
		Random rand = new Random();
		// Prime the random number generator
		for (int i = 0; i < 100; i++)
		{
			rand.nextInt();
		}
		
		MapSettings settings = new MapSettings(defaultSettingsFile);
		settings.pointPrecision = MapSettings.defaultPointPrecision;
		
		setRandomSeeds(settings, rand);
		
		int hueRange = 16;
		int saturationRange = 25;
		int brightnessRange = 25;
		
		Color landColor = rand.nextInt(2) == 1 ? settings.landColor : settings.oceanColor;
		Color oceanColor;
		if (landColor == settings.landColor)
		{
			oceanColor = settings.oceanColor;
		}
		else
		{
			oceanColor = rand.nextInt(2) == 1 ? settings.landColor : settings.oceanColor;
		}
		settings.oceanEffect = ProbabilityHelper.sampleEnumUniform(rand, OceanEffect.class);
		settings.oceanEffectSize = 10 + Math.abs(rand.nextInt(40));
		settings.landBlur = 10 + Math.abs(rand.nextInt(40));
		
		settings.landColor = MapCreator.generateColorFromBaseColor(rand, landColor, hueRange, saturationRange, brightnessRange);
		
		settings.oceanColor = MapCreator.generateColorFromBaseColor(rand, oceanColor, hueRange, saturationRange, brightnessRange);
		
		double landBlurColorScale = 0.5;
		settings.landBlurColor = new Color((int)(settings.landColor.getRed() * landBlurColorScale), (int)(settings.landColor.getGreen() * landBlurColorScale), (int)(settings.landColor.getBlue() * landBlurColorScale));
		if (settings.oceanEffect == OceanEffect.Ripples)
		{
			settings.oceanEffectsColor = Color.black;
		}
		else if (settings.oceanEffect == OceanEffect.Blur)
		{
			double oceanEffectsColorScale = 0.3;
			settings.oceanEffectsColor = new Color((int)(settings.oceanColor.getRed() * oceanEffectsColorScale), (int)(settings.oceanColor.getGreen() * oceanEffectsColorScale), (int)(settings.oceanColor.getBlue() * oceanEffectsColorScale));
		}
		else
		{
			// Concentric waves
			double oceanEffectsColorScale = 0.5;
			int alpha = 255;
			settings.oceanEffectsColor = new Color((int)(settings.oceanColor.getRed() * oceanEffectsColorScale), (int)(settings.oceanColor.getGreen() * oceanEffectsColorScale), (int)(settings.oceanColor.getBlue() * oceanEffectsColorScale), alpha);
			
		}
		settings.riverColor = MapCreator.generateColorFromBaseColor(rand, settings.riverColor, hueRange, saturationRange, brightnessRange);
		settings.frayedBorderColor = MapCreator.generateColorFromBaseColor(rand, settings.frayedBorderColor, hueRange, saturationRange, brightnessRange);
		
		settings.worldSize = (rand.nextInt((maxWorldSize - minWorldSize) / worldSizePrecision) + minWorldSize / worldSizePrecision) * worldSizePrecision;
		
		settings.frayedBorder = rand.nextDouble() > 0.5;
		settings.frayedBorderBlurLevel = Math.abs(rand.nextInt(150));
		settings.frayedBorderSize = 100 + Math.abs(rand.nextInt(20000));
		
		settings.grungeWidth = 100 + rand.nextInt(1400);
		
		final double drawBorderProbability = 0.25;
		settings.drawBorder = rand.nextDouble() > drawBorderProbability;
		Set<String> borderTypes = MapCreator.getAvailableBorderTypes();
		if (!borderTypes.isEmpty())
		{
			// Random border type.
			int index = Math.abs(rand.nextInt()) % borderTypes.size();
			settings.borderType = borderTypes.toArray(new String[borderTypes.size()])[index];
			if (settings.borderType.equals("dashes"))
			{
				settings.frayedBorder = false;
				settings.borderWidth = Math.abs(rand.nextInt(50)) + 25;
			}
			else
			{
				settings.borderWidth = Math.abs(rand.nextInt(200)) + 100;
			}
		}
		
		if (rand.nextDouble() > 0.25)
		{
			settings.cityProbability =  0.25 * maxCityProbabillity;
		}
		else
		{
			settings.cityProbability = 0.0;
		}
		Set<String> cityIconSets = IconDrawer.getIconSets(IconDrawer.citiesName);
		if (cityIconSets.size() > 0)
		{
			settings.cityIconSetName = ProbabilityHelper.sampleUniform(rand, new ArrayList<>(cityIconSets));
		}
		
		settings.drawRegionColors = rand.nextDouble() > 0.25;
		
		if (rand.nextDouble() > 0.5)
		{
			settings.generateBackground = true;
			settings.generateBackgroundFromTexture = false;
		}
		else
		{
			settings.generateBackground = false;
			settings.generateBackgroundFromTexture = true;
			
			Path exampleTexturesPath = Paths.get(AssetsPath.get(), "example textures");
			List<Path> textureFiles;
			try
			{
				textureFiles = Files.list(exampleTexturesPath).filter(path -> !Files.isDirectory(path)).collect(Collectors.toList());
			}
			catch(IOException ex)
			{
				throw new RuntimeException("The example textures folder does not exist.", ex);
			}
			
			if (textureFiles.size() > 0)
			{
				settings.backgroundTextureImage = ProbabilityHelper.sampleUniform(rand, textureFiles).toString();
			}
		}
		
		settings.drawBoldBackground = rand.nextDouble() > 0.5;
		settings.boldBackgroundColor = MapCreator.generateColorFromBaseColor(rand, settings.boldBackgroundColor, hueRange, saturationRange, brightnessRange);
		
		// This threshold prevents large maps from having land on the edge, because such maps should be the entire world/continent.
		int noOceanOnEdgeThreshold = 15000;
		if (settings.worldSize < noOceanOnEdgeThreshold)
		{
			settings.edgeLandToWaterProbability = settings.worldSize / (double) noOceanOnEdgeThreshold;
			// Make the edge and center land water probability add up to 1 so there is usually both land and ocean.
			settings.centerLandToWaterProbability = 1.0 - settings.edgeLandToWaterProbability;
		}
		else
		{
			settings.centerLandToWaterProbability = 0.5 + rand.nextDouble() * 0.5;
			settings.edgeLandToWaterProbability = 0;
		}
		
		settings.edgeLandToWaterProbability = Math.round(settings.edgeLandToWaterProbability * 100.0) / 100.0;
		settings.centerLandToWaterProbability = Math.round(settings.centerLandToWaterProbability * 100.0) / 100.0;
		
		Dimension dimension = RunSwing.parseGenerateBackgroundDimensionsFromDropdown(ProbabilityHelper.sampleUniform(rand, RunSwing.getAllowedDimmensions()));
		settings.generatedWidth = dimension.width;
		settings.generatedHeight = dimension.height;
		
		settings.books.clear();
		List<String> allBooks = RunSwing.getAllBooks();
		if (allBooks.size() < 3)
		{
			settings.books.addAll(allBooks);
		}
		else if (allBooks.size() > 0)
		{
			int numBooks = 2 + Math.abs(rand.nextInt(allBooks.size() - 1));
			List<String> booksRemaining = new ArrayList<>(allBooks);
			for (@SuppressWarnings("unused") int ignored : new Range(numBooks))
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
}
