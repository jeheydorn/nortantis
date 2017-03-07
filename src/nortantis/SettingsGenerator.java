package nortantis;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;

/**
 * For randomly generating settings with which to generate a map.
 *
 */
public class SettingsGenerator
{
	private static String defaultSettingsFile = "assets/old_paper.properties";

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
		
		int hueRange = 16;
		int saturationRange = 25;
		int brightnessRange = 25;
		
		Color landColor = rand.nextInt(2) == 1 ? settings.landColor : settings.oceanColor;
		Color oceanColor = rand.nextInt(2) == 1 ? settings.landColor : settings.oceanColor;
		
		settings.landColor = MapCreator.generateColorFromBaseColor(rand, landColor, hueRange, 
				saturationRange, brightnessRange);
		
		settings.oceanColor = MapCreator.generateColorFromBaseColor(rand, oceanColor, hueRange, 
				saturationRange, brightnessRange);
				
		return settings;
	}
}
