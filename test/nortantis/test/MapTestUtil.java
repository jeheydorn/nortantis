package nortantis.test;

import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.SettingsGenerator;
import nortantis.WarningLogger;
import nortantis.platform.*;
import nortantis.util.Assets;
import nortantis.util.FileHelper;
import nortantis.util.ImageHelper;
import nortantis.util.Logger;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.nio.file.Paths;
import java.util.Random;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class MapTestUtil
{
	public static String getExpectedMapFilePath(String settingsFileName)
	{
		return Paths.get("unit test files", "expected maps", FilenameUtils.getBaseName(settingsFileName) + ".png").toString();
	}

	public static String getFailedMapFilePath(String settingsFileName, String failedMapsFolderName)
	{
		return Paths.get("unit test files", failedMapsFolderName, FilenameUtils.getBaseName(settingsFileName) + ".png").toString();
	}

	private static String getDiffFilePath(String settingsFileName, String failedMapsFolderName)
	{
		return Paths.get("unit test files", failedMapsFolderName, FilenameUtils.getBaseName(settingsFileName) + " - diff.png").toString();
	}

	public static WarningLogger generateAndCompare(String settingsFileName, Consumer<MapSettings> preprocessSettings, String failedMapsFolderName)
	{
		String expectedMapFilePath = getExpectedMapFilePath(settingsFileName);
		Image expected;
		if (new File(expectedMapFilePath).exists())
		{
			expected = Assets.readImage(expectedMapFilePath);
		}
		else
		{
			expected = null;
		}

		String settingsPath = Paths.get("unit test files", "map settings", settingsFileName).toString();
		MapSettings settings = new MapSettings(settingsPath);
		if (preprocessSettings != null)
		{
			preprocessSettings.accept(settings);
		}
		MapCreator mapCreator = new MapCreator();
		Logger.println("Creating map from '" + settingsPath + "'");
		Image actual = mapCreator.createMap(settings, null, null);

		// Test deep copy after creating the map because MapCreator sets some fields during map creation, so it's a
		// more complete test that way.
		testDeepCopy(settings);

		String comparisonErrorMessage = checkIfImagesEqual(expected, actual);
		if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
		{
			FileHelper.createFolder(Paths.get("unit test files", failedMapsFolderName).toString());
			ImageHelper.write(actual, getFailedMapFilePath(settingsFileName, failedMapsFolderName));
			createImageDiffIfImagesAreSameSize(expected, actual, settingsFileName, failedMapsFolderName);
			fail(comparisonErrorMessage);
		}

		return mapCreator;
	}

	private static void testDeepCopy(MapSettings settings)
	{
		MapSettings copy = settings.deepCopy();
		assertEquals(settings, copy);
	}

	public static void createImageDiffIfImagesAreSameSize(Image image1, Image image2, String settingsFileName, String failedMapsFolderName)
	{
		createImageDiffIfImagesAreSameSize(image1, image2, settingsFileName, 0, failedMapsFolderName);
	}

	public static void createImageDiffIfImagesAreSameSize(Image image1, Image image2, String settingsFileName, int threshold, String failedMapsFolderName)
	{
		if (image1.getWidth() == image2.getWidth() && image1.getHeight() == image2.getHeight())
		{
			Image diff = Image.create(image1.getWidth(), image1.getHeight(), ImageType.RGB);
			try (PixelReader image1Pixels = image1.createPixelReader(); PixelReader image2Pixels = image2.createPixelReader(); PixelReaderWriter diffPixels = diff.createPixelReaderWriter())
			{
				for (int x = 0; x < image1.getWidth(); x++)
				{
					for (int y = 0; y < image1.getHeight(); y++)
					{
						if (threshold == 0)
						{
							if (image1Pixels.getRGB(x, y) != image2Pixels.getRGB(x, y))
							{
								diffPixels.setRGB(x, y, Color.white.getRGB());
							}
							else
							{
								diffPixels.setRGB(x, y, Color.black.getRGB());
							}
						}
						else
						{
							int difference = image1Pixels.getPixelColor(x, y).manhattanDistanceTo(image2Pixels.getPixelColor(x, y));
							if (difference > threshold)
							{
								diffPixels.setRGB(x, y, Color.white.getRGB());
							}
							else
							{
								diffPixels.setRGB(x, y, Color.black.getRGB());
							}
						}
					}
				}
			}
			ImageHelper.write(diff, getDiffFilePath(settingsFileName, failedMapsFolderName));
		}
	}

	public static void generateRandomAndCompare(long seed, String failedMapsFolderName)
	{
		String expectedFileName = "random map for seed " + seed;
		String expectedMapFilePath = getExpectedMapFilePath(expectedFileName);
		Image expected;
		if (new File(expectedMapFilePath).exists())
		{
			expected = Assets.readImage(expectedMapFilePath);
		}
		else
		{
			expected = null;
		}

		MapSettings settings = SettingsGenerator.generate(new Random(seed), Assets.installedArtPack, null);
		settings.resolution = 0.5;
		MapCreator mapCreator = new MapCreator();
		Logger.println("Creating random map to match '" + expectedFileName + "'");
		Image actual;
		actual = mapCreator.createMap(settings, null, null);

		if (expected == null)
		{
			// Create the expected map from the actual one.
			expected = actual;
			ImageHelper.write(actual, getExpectedMapFilePath(expectedFileName));
		}

		// Test deep copy after creating the map because MapCreator sets some fields during map creation, so it's a
		// more complete test that way.
		testDeepCopy(settings);

		String comparisonErrorMessage = checkIfImagesEqual(expected, actual);
		if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
		{
			FileHelper.createFolder(Paths.get("unit test files", failedMapsFolderName).toString());
			ImageHelper.write(actual, MapTestUtil.getFailedMapFilePath(expectedFileName, failedMapsFolderName));
			createImageDiffIfImagesAreSameSize(expected, actual, expectedFileName, failedMapsFolderName);
			fail(comparisonErrorMessage);
		}
	}

	public static void generateRandomHeightmapAndCompare(long seed, String failedMapsFolderName)
	{
		String expectedFileName = "random heightmap for seed " + seed;
		String expectedMapFilePath = getExpectedMapFilePath(expectedFileName);
		Image expected;
		if (new File(expectedMapFilePath).exists())
		{
			expected = Assets.readImage(expectedMapFilePath);
		}
		else
		{
			expected = null;
		}

		MapSettings settings = SettingsGenerator.generate(new Random(seed), Assets.installedArtPack, null);
		settings.resolution = 0.5;
		MapCreator mapCreator = new MapCreator();
		Logger.println("Creating random heightmap to match '" + expectedFileName + "'");
		Image actual;
		actual = mapCreator.createHeightMap(settings);

		if (expected == null)
		{
			// Create the expected map from the actual one.
			expected = actual;
			ImageHelper.write(actual, getExpectedMapFilePath(expectedFileName));
		}

		// Test deep copy after creating the map because MapCreator sets some fields during map creation, so it's a
		// more complete test that way.
		testDeepCopy(settings);

		String comparisonErrorMessage = checkIfImagesEqual(expected, actual);
		if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
		{
			FileHelper.createFolder(Paths.get("unit test files", failedMapsFolderName).toString());
			ImageHelper.write(actual, getFailedMapFilePath(expectedFileName, failedMapsFolderName));
			createImageDiffIfImagesAreSameSize(expected, actual, expectedFileName, failedMapsFolderName);
			fail(comparisonErrorMessage);
		}
	}

	public static String checkIfImagesEqual(Image image1, Image image2)
	{
		return checkIfImagesEqual(image1, image2, 0);
	}

	public static String checkIfImagesEqual(Image image1, Image image2, int threshold)
	{
		if (image1 == null)
		{
			return "Image 1 is null.";
		}

		if (image2 == null)
		{
			return "Image 2 is null.";
		}

		if (image1.getWidth() == image2.getWidth() && image1.getHeight() == image2.getHeight())
		{
			try (PixelReader image1Pixels = image1.createPixelReader(); PixelReader image2Pixels = image2.createPixelReader())
			{
				for (int x = 0; x < image1.getWidth(); x++)
				{
					for (int y = 0; y < image1.getHeight(); y++)
					{
						if (threshold == 0)
						{
							int rgb1 = image1Pixels.getRGB(x, y);
							int rgb2 = image2Pixels.getRGB(x, y);
							if (rgb1 != rgb2)
							{
								return "Images differ at pixel (" + x + ", " + y + "). Color from image1: " + Color.create(rgb1) + ". Color from image2: " + Color.create(rgb2);
							}
						}
						else
						{
							int diff = image1Pixels.getPixelColor(x, y).manhattanDistanceTo(image2Pixels.getPixelColor(x, y));
							if (diff > threshold)
							{
								return "Images differ at pixel (" + x + ", " + y + ") by " + diff;
							}
						}
					}
				}
			}
		}
		else
		{
			return "Images have differing dimensions.";
		}
		return null;
	}

}
