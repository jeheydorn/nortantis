package nortantis;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;
import java.util.Set;

/**
 * For randomly generating settings with which to generate a map.
 *
 */
public class SettingsGenerator
{
	private static String defaultSettingsFile = "assets/internal/old_paper.properties";
	public static int minWorldSize = 2000;
	public static int maxWorldSize = 30000;
	public static int worldSizePrecision = 1000;

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
		
		setRandomSeeds(settings, rand);
		
		int hueRange = 16;
		int saturationRange = 25;
		int brightnessRange = 25;
		
		Color landColor = rand.nextInt(2) == 1 ? settings.landColor : settings.oceanColor;
		Color oceanColor = rand.nextInt(2) == 1 ? settings.landColor : settings.oceanColor;
		
		settings.landColor = MapCreator.generateColorFromBaseColor(rand, landColor, hueRange, 
				saturationRange, brightnessRange);
		
		settings.oceanColor = MapCreator.generateColorFromBaseColor(rand, oceanColor, hueRange, 
				saturationRange, brightnessRange);
		
		settings.worldSize = (rand.nextInt((maxWorldSize - minWorldSize) / worldSizePrecision) + minWorldSize / worldSizePrecision) * worldSizePrecision;
		
		Set<String> borderTypes = MapCreator.getAvailableBorderTypes();
		if (!borderTypes.isEmpty())
		{
			// Random border type.
			settings.drawBorder = true;
			int index = rand.nextInt() % borderTypes.size();
			settings.borderType = borderTypes.toArray(new String[borderTypes.size()])[index];
			settings.borderWidth = Math.abs(rand.nextInt()) % 200 + 100;
		}
				
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
