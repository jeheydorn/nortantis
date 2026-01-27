package nortantis.test;

import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.SettingsGenerator;
import nortantis.WarningLogger;
import nortantis.MapText;
import nortantis.editor.FreeIcon;
import nortantis.editor.MapParts;
import nortantis.platform.*;
import nortantis.util.Assets;
import nortantis.util.FileHelper;
import nortantis.util.ImageHelper;
import nortantis.util.Logger;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Random;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class MapTestUtil
{
	public static String getExpectedMapFilePath(String settingsFileName, String expectedMapsFolderName)
	{
		return Paths.get("unit test files", expectedMapsFolderName, FilenameUtils.getBaseName(settingsFileName) + ".png").toString();
	}

	public static String getFailedMapFilePath(String settingsFileName, String failedMapsFolderName)
	{
		return Paths.get("unit test files", failedMapsFolderName, FilenameUtils.getBaseName(settingsFileName) + ".png").toString();
	}

	private static String getDiffFilePath(String settingsFileName, String failedMapsFolderName)
	{
		return Paths.get("unit test files", failedMapsFolderName, FilenameUtils.getBaseName(settingsFileName) + " - diff.png").toString();
	}

	public static WarningLogger generateAndCompare(String settingsFileName, Consumer<MapSettings> preprocessSettings, String expectedMapsFolderName, String failedMapsFolderName, int threshold)
	{
		String expectedMapFilePath = getExpectedMapFilePath(settingsFileName, expectedMapsFolderName);
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
		try (Image actual = mapCreator.createMap(settings, null, null))
		{
			if (expected == null)
			{
				// Create the expected map from the actual one.
				expected = actual;
				ImageHelper.write(actual, getExpectedMapFilePath(settingsFileName, expectedMapsFolderName));
			}

			// Test deep copy after creating the map because MapCreator sets some fields during map creation, so it's a
			// more complete test that way.
			testDeepCopy(settings);

			String comparisonErrorMessage = checkIfImagesEqual(expected, actual, threshold);
			if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
			{
				FileHelper.createFolder(Paths.get("unit test files", failedMapsFolderName).toString());
				ImageHelper.write(actual, getFailedMapFilePath(settingsFileName, failedMapsFolderName));
				createImageDiffIfImagesAreSameSize(expected, actual, settingsFileName, failedMapsFolderName);
				fail(comparisonErrorMessage);
			}

			return mapCreator;
		}
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
			Image diffImage = Image.create(image1.getWidth(), image1.getHeight(), ImageType.RGB);
			try (PixelReader image1Pixels = image1.createPixelReader(); PixelReader image2Pixels = image2.createPixelReader(); PixelReaderWriter diffPixels = diffImage.createPixelReaderWriter())
			{
				for (int x = 0; x < image1.getWidth(); x++)
				{
					for (int y = 0; y < image1.getHeight(); y++)
					{
						// Skip RGB comparison if both pixels are fully transparent
						Color color1 = Color.create(image1Pixels.getRGB(x, y));
						Color color2 = Color.create(image2Pixels.getRGB(x, y));
						int diff = color1.manhattanDistanceTo(color2);

						diffPixels.setRGB(x, y, Color.create(diff, diff, diff).getRGB());
					}
				}
			}
			ImageHelper.write(diffImage, getDiffFilePath(settingsFileName, failedMapsFolderName));
		}
	}

	public static MapSettings generateRandomAndCompare(long seed, String expectedMapsFolderName, String failedMapsFolderName, int threshold)
	{
		String expectedFileName = "random map for seed " + seed;
		String expectedMapFilePath = getExpectedMapFilePath(expectedFileName, expectedMapsFolderName);
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
		settings.resolution = 0.25;
		MapCreator mapCreator = new MapCreator();
		Logger.println("Creating random map to match '" + expectedFileName + "'");
		Image actual;
		actual = mapCreator.createMap(settings, null, null);

		if (expected == null)
		{
			// Create the expected map from the actual one.
			expected = actual;
			ImageHelper.write(actual, getExpectedMapFilePath(expectedFileName, expectedMapsFolderName));
		}

		// Test deep copy after creating the map because MapCreator sets some fields during map creation, so it's a
		// more complete test that way.
		testDeepCopy(settings);

		String comparisonErrorMessage = checkIfImagesEqual(expected, actual, threshold);
		if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
		{
			FileHelper.createFolder(Paths.get("unit test files", failedMapsFolderName).toString());
			ImageHelper.write(actual, MapTestUtil.getFailedMapFilePath(expectedFileName, failedMapsFolderName));
			createImageDiffIfImagesAreSameSize(expected, actual, expectedFileName, failedMapsFolderName);
			fail(comparisonErrorMessage);
		}

		return settings;
	}

	public static void generateRandomHeightmapAndCompare(long seed, String expectedMapsFolderName, String failedMapsFolderName)
	{
		String expectedFileName = "random heightmap for seed " + seed;
		String expectedMapFilePath = getExpectedMapFilePath(expectedFileName, expectedMapsFolderName);
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
			ImageHelper.write(actual, getExpectedMapFilePath(expectedFileName, expectedMapsFolderName));
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

	public static void checkIfImagesAreEqualAndWriteToFailedIfNot(Image expected, Image actual, int threshold, String testName, String failedMapsFolderName)
	{
		String comparisonErrorMessage = MapTestUtil.checkIfImagesEqual(expected, actual, threshold);
		if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
		{
			String failedFolderPath = Paths.get("unit test files", failedMapsFolderName).toString();
			FileHelper.createFolder(failedFolderPath);
			ImageHelper.write(expected, Paths.get(failedFolderPath, testName + " expected.png").toString());
			ImageHelper.write(actual, Paths.get(failedFolderPath, testName + " actual.png").toString());
			MapTestUtil.createImageDiffIfImagesAreSameSize(expected, actual, testName, failedMapsFolderName);
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
						Color color1 = Color.create(image1Pixels.getRGB(x, y));
						Color color2 = Color.create(image2Pixels.getRGB(x, y));
						int diff = color1.manhattanDistanceTo(color2);

						if (diff > 0)
						{
							if (threshold == 0)
							{
								return "Images differ at pixel (" + x + ", " + y + "). Color from image1: " + color1 + ". Color from image2: " + color2;
							}
							else
							{
								if (diff > threshold)
								{
									return "Images differ at pixel (" + x + ", " + y + ") by " + diff;
								}
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

	// Benchmark utilities

	public static String formatTime(long nanos)
	{
		if (nanos < 1_000_000)
		{
			return String.format("%.2f µs", nanos / 1000.0);
		}
		else if (nanos < 1_000_000_000)
		{
			return String.format("%.2f ms", nanos / 1_000_000.0);
		}
		else
		{
			return String.format("%.2f s", nanos / 1_000_000_000.0);
		}
	}

	public static int countFreeIcons(MapSettings settings)
	{
		int count = 0;
		for (@SuppressWarnings("unused")
		FreeIcon icon : settings.edits.freeIcons)
		{
			count++;
		}
		return count;
	}

	public static int countMapTexts(MapSettings settings)
	{
		int count = 0;
		for (@SuppressWarnings("unused")
		MapText text : settings.edits.text)
		{
			count++;
		}
		return count;
	}

	public static void runMapCreationBenchmark(String platformName, double resolution, int warmupIterations, int benchmarkIterations) throws Exception
	{
		System.out.println("\n=== Map Creation Benchmark (" + platformName + ") ===\n");

		String settingsPath = Paths.get("unit test files", "map settings", "simpleSmallWorld.nort").toString();
		MapSettings settings = new MapSettings(settingsPath);
		settings.resolution = resolution;

		System.out.println("Settings: " + settingsPath);
		System.out.println("Resolution: " + settings.resolution);

		// Warmup
		System.out.println("\nWarmup (" + warmupIterations + " iterations)...");
		for (int i = 0; i < warmupIterations; i++)
		{
			MapCreator mapCreator = new MapCreator();
			Image map = mapCreator.createMap(settings, null, null);
			if (i == 0)
			{
				System.out.println("Map size: " + map.getWidth() + "x" + map.getHeight());
			}
			map.close();
		}

		// Benchmark
		System.out.println("\nRunning benchmark (" + benchmarkIterations + " iterations)...\n");

		long[] times = new long[benchmarkIterations];
		for (int i = 0; i < benchmarkIterations; i++)
		{
			MapCreator mapCreator = new MapCreator();

			long start = System.nanoTime();
			Image map = mapCreator.createMap(settings, null, null);
			long elapsed = System.nanoTime() - start;

			times[i] = elapsed;
			System.out.println("  Iteration " + (i + 1) + ": " + formatTime(elapsed));

			map.close();
		}

		printStatistics("Map Creation", times, benchmarkIterations);
	}

	public static void runMapCreationBenchmarkSingleIteration(String platformName, double resolution) throws Exception
	{
		System.out.println("\n=== Map Creation Benchmark - High Resolution (" + platformName + ") ===\n");

		String settingsPath = Paths.get("unit test files", "map settings", "simpleSmallWorld.nort").toString();
		MapSettings settings = new MapSettings(settingsPath);
		settings.resolution = resolution;

		System.out.println("Settings: " + settingsPath);
		System.out.println("Resolution: " + settings.resolution);

		System.out.println("\nRunning single iteration...\n");

		MapCreator mapCreator = new MapCreator();

		long start = System.nanoTime();
		Image map = mapCreator.createMap(settings, null, null);
		long elapsed = System.nanoTime() - start;

		System.out.println("Map size: " + map.getWidth() + "x" + map.getHeight());
		System.out.println("Time: " + formatTime(elapsed));

		map.close();
	}

	public static void runIncrementalDrawingBenchmark(String platformName, int warmupIterations, int benchmarkIterations) throws Exception
	{
		System.out.println("\n=== Incremental Drawing Benchmark (" + platformName + ") ===\n");

		String settingsPath = Paths.get("unit test files", "map settings", "simpleSmallWorld.nort").toString();
		MapSettings settings = new MapSettings(settingsPath);
		settings.resolution = 0.75;

		System.out.println("Settings: " + settingsPath);
		System.out.println("Resolution: " + settings.resolution);

		// Create full map first (required for incremental updates)
		System.out.println("\nCreating initial full map...");
		MapCreator mapCreator = new MapCreator();
		MapParts mapParts = new MapParts();
		Image fullMap = mapCreator.createMap(settings, null, mapParts);
		System.out.println("Map size: " + fullMap.getWidth() + "x" + fullMap.getHeight());

		int iconCount = countFreeIcons(settings);
		int textCount = countMapTexts(settings);
		System.out.println("Number of icons to update: " + iconCount);
		System.out.println("Number of text labels to update: " + textCount);

		if (iconCount == 0 && textCount == 0)
		{
			System.out.println("No icons or text found in settings - skipping benchmark");
			fullMap.close();
			return;
		}

		// Warmup
		System.out.println("\nWarmup (" + warmupIterations + " iterations)...");
		for (int i = 0; i < warmupIterations; i++)
		{
			Image mapCopy = fullMap.deepCopy();
			for (FreeIcon icon : settings.edits.freeIcons)
			{
				mapCreator.incrementalUpdateIcons(settings, mapParts, mapCopy, Arrays.asList(icon));
			}
			for (MapText text : settings.edits.text)
			{
				mapCreator.incrementalUpdateText(settings, mapParts, mapCopy, Arrays.asList(text));
			}
			mapCopy.close();
		}

		// Benchmark icons
		if (iconCount > 0)
		{
			System.out.println("\nRunning icon benchmark (" + benchmarkIterations + " iterations)...\n");

			long[] iconTimes = new long[benchmarkIterations];
			for (int i = 0; i < benchmarkIterations; i++)
			{
				Image mapCopy = fullMap.deepCopy();

				long start = System.nanoTime();
				for (FreeIcon icon : settings.edits.freeIcons)
				{
					mapCreator.incrementalUpdateIcons(settings, mapParts, mapCopy, Arrays.asList(icon));
				}
				long elapsed = System.nanoTime() - start;

				iconTimes[i] = elapsed;
				System.out.println("  Iteration " + (i + 1) + ": " + formatTime(elapsed) + " (" + iconCount + " icons, " + formatTime(elapsed / iconCount) + " per icon)");

				mapCopy.close();
			}

			printStatistics("Icon", iconTimes, benchmarkIterations, iconCount, "icon");
		}

		// Benchmark text
		if (textCount > 0)
		{
			System.out.println("\nRunning text benchmark (" + benchmarkIterations + " iterations)...\n");

			long[] textTimes = new long[benchmarkIterations];
			for (int i = 0; i < benchmarkIterations; i++)
			{
				Image mapCopy = fullMap.deepCopy();

				long start = System.nanoTime();
				for (MapText text : settings.edits.text)
				{
					mapCreator.incrementalUpdateText(settings, mapParts, mapCopy, Arrays.asList(text));
				}
				long elapsed = System.nanoTime() - start;

				textTimes[i] = elapsed;
				System.out.println("  Iteration " + (i + 1) + ": " + formatTime(elapsed) + " (" + textCount + " texts, " + formatTime(elapsed / textCount) + " per text)");

				mapCopy.close();
			}

			printStatistics("Text", textTimes, benchmarkIterations, textCount, "text");
		}

		fullMap.close();
	}

	private static void printStatistics(String label, long[] times, int iterations)
	{
		long total = 0;
		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		for (long t : times)
		{
			total += t;
			min = Math.min(min, t);
			max = Math.max(max, t);
		}
		long avg = total / iterations;

		System.out.println("\n=== " + label + " Results ===");
		System.out.println("  Average: " + formatTime(avg));
		System.out.println("  Min:     " + formatTime(min));
		System.out.println("  Max:     " + formatTime(max));
	}

	private static void printStatistics(String label, long[] times, int iterations, int itemCount, String itemName)
	{
		long total = 0;
		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		for (long t : times)
		{
			total += t;
			min = Math.min(min, t);
			max = Math.max(max, t);
		}
		long avg = total / iterations;

		System.out.println("\n=== " + label + " Results ===");
		System.out.println("  Average: " + formatTime(avg) + " (" + formatTime(avg / itemCount) + " per " + itemName + ")");
		System.out.println("  Min:     " + formatTime(min));
		System.out.println("  Max:     " + formatTime(max));
	}

}
