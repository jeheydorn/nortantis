package nortantis.test;

import nortantis.*;
import nortantis.editor.FreeIcon;
import nortantis.editor.MapParts;
import nortantis.geom.Dimension;
import nortantis.geom.IntRectangle;
import nortantis.geom.Dimension;
import nortantis.platform.*;
import nortantis.platform.awt.AwtFactory;
import nortantis.platform.skia.SkiaFactory;
import nortantis.platform.skia.SkiaImage;
import nortantis.util.Assets;
import nortantis.util.FileHelper;
import nortantis.util.ImageHelper;
import nortantis.util.Stopwatch;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Random;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.fail;

public class SkiaMapCreatorTest
{
	final static String failedMapsFolderName = "failed maps skia";
	private static final String expectedMapsFolderName = "expected maps skia";
	final int threshold = 4;

	@BeforeAll
	public static void setUpBeforeClass() throws Exception
	{
		PlatformFactory.setInstance(new SkiaFactory());
		Assets.disableAddedArtPacksForUnitTests();

		FileHelper.createFolder(Paths.get("unit test files", "expected maps").toString());
		FileUtils.deleteDirectory(new File(Paths.get("unit test files", failedMapsFolderName).toString()));
	}

	@Test
	public void simpleSmallWorld()
	{
		generateAndCompare("simpleSmallWorld.nort", (settings) -> settings.resolution = 0.25);
	}

	@Test
	public void simpleSmallWorldUpperRightQuadrant_SkiaVsAwt()
	{
		String testName = "simpleSmallWorldUpperRightQuadrant_SkiaVsAwt";
		String settingsFileName = "simpleSmallWorld.nort";
		String settingsPath = Paths.get("unit test files", "map settings", settingsFileName).toString();

		// Generate with Skia and save to file
		{
			PlatformFactory.setInstance(new SkiaFactory());
			MapSettings settings = new MapSettings(settingsPath);
			settings.resolution = 0.25;

			MapCreator mapCreator = new MapCreator();
			Image fullMap = mapCreator.createMap(settings, null, null);

			// Extract upper-right quadrant
			int quadrantWidth = fullMap.getWidth() / 2;
			int quadrantHeight = fullMap.getHeight() / 2;
			int quadrantX = fullMap.getWidth() - quadrantWidth;
			int quadrantY = 0;
			IntRectangle upperRightBounds = new IntRectangle(quadrantX, quadrantY, quadrantWidth, quadrantHeight);
			Image skiaQuadrant = fullMap.copySubImage(upperRightBounds, false);

			FileHelper.createFolder(Paths.get("unit test files", failedMapsFolderName).toString());
			ImageHelper.write(skiaQuadrant, Paths.get("unit test files", failedMapsFolderName, testName + " - skia.png").toString());
		}

		// The Skia image is saved to disk. Compare it against the expected AWT image.
		// We can't generate AWT in the same JVM because static Color constants (Color.white, etc.)
		// are platform-specific and were already initialized with Skia.
		// Instead, compare against the expected AWT image that was pre-generated.
		Image skiaQuadrant = Assets.readImage(Paths.get("unit test files", failedMapsFolderName, testName + " - skia.png").toString());
		String expectedAwtPath = Paths.get("unit test files", expectedMapsFolderName, testName + " - awt.png").toString();

		if (!new File(expectedAwtPath).exists())
		{
			fail("Expected AWT image not found at: " + expectedAwtPath +
				 ". Generate it by running MapCreatorTest.simpleSmallWorldUpperRightQuadrant() or manually.");
		}

		Image awtQuadrant = Assets.readImage(expectedAwtPath);

		// Compare Skia and AWT results
		int diffThreshold = 4;
		String comparisonErrorMessage = MapTestUtil.checkIfImagesEqual(skiaQuadrant, awtQuadrant, diffThreshold);
		if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
		{
			createImageDiffIfImagesAreSameSize(skiaQuadrant, awtQuadrant, testName, diffThreshold);
			fail("Skia and AWT results differ: " + comparisonErrorMessage);
		}
	}

	@Test
	public void incrementalUpdate_simpleSmallWorld()
	{
		// Force CPU rendering to ensure consistent results between full map and incremental updates
		SkiaImage.setForceCPU(true);
		try
		{
			// Load settings from the .nort file
			String settingsFileName = "simpleSmallWorld.nort";
			String settingsPath = Paths.get("unit test files", "map settings", settingsFileName).toString();
			MapSettings settings = new MapSettings(settingsPath);
			settings.resolution = 0.25;

			// Create the full map first (baseline)
			MapCreator mapCreator = new MapCreator();
			MapParts mapParts = new MapParts();
			Image fullMap = mapCreator.createMap(settings, null, mapParts);
			final int diffThreshold = 10;
			int failCount = 0;

			{
				final int numberToTest = 100;
				Image fullMapForUpdates = fullMap.deepCopy();
				int iconNumber = 0;
				for (FreeIcon icon : settings.edits.freeIcons)
				{
					iconNumber++;
					if (iconNumber > numberToTest)
					{
						break;
					}

					// System.out.println("Running incremental icon drawing test number " + iconNumber);

					IntRectangle changedBounds = mapCreator.incrementalUpdateIcons(settings, mapParts, fullMapForUpdates, Arrays.asList(icon));

					assertTrue(changedBounds != null, "Incremental update should produce bounds");
					assertTrue(changedBounds.width > 0);
					assertTrue(changedBounds.height > 0);

					Image expectedSnippet = fullMap.getSubImage(changedBounds);
					Image actualSnippet = fullMapForUpdates.getSubImage(changedBounds);

					// Compare incremental result against expected
					String comparisonErrorMessage = MapTestUtil.checkIfImagesEqual(expectedSnippet, actualSnippet, diffThreshold);
					if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
					{
						FileHelper.createFolder(Paths.get("unit test files", failedMapsFolderName).toString());

						String expectedSnippetName = FilenameUtils.getBaseName(settingsFileName) + " icon " + iconNumber + " expected.png";
						Path expectedPath = Paths.get("unit test files", failedMapsFolderName, expectedSnippetName);
						ImageHelper.write(expectedSnippet, expectedPath.toString());

						String failedSnippetName = FilenameUtils.getBaseName(settingsFileName) + " icon " + iconNumber + " failed.png";
						Path failedPath = Paths.get("unit test files", failedMapsFolderName, failedSnippetName);
						ImageHelper.write(actualSnippet, failedPath.toString());

						createImageDiffIfImagesAreSameSize(expectedSnippet, actualSnippet, failedSnippetName, diffThreshold);
						failCount++;
					}
				}

				String comparisonErrorMessage = MapTestUtil.checkIfImagesEqual(fullMap, fullMapForUpdates, diffThreshold);
				if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
				{
					FileHelper.createFolder(Paths.get("unit test files", failedMapsFolderName).toString());
					String failedMapName = FilenameUtils.getBaseName(settingsFileName) + " full map for incremental draw test";
					ImageHelper.write(fullMapForUpdates, MapTestUtil.getFailedMapFilePath(failedMapName, failedMapsFolderName));
					createImageDiffIfImagesAreSameSize(fullMap, fullMapForUpdates, failedMapName, diffThreshold);
					fail("Incremental update did not match expected image: " + comparisonErrorMessage);
				}
			}
		}
		finally
		{
			SkiaImage.setForceCPU(false);
		}
	}

	@Test
	public void drawLandAndOceanBlackAndWhiteTest()
	{
		final String settingsFileName = "simpleSmallWorld.nort";
		MapSettings settings = new MapSettings(Paths.get("unit test files", "map settings", settingsFileName).toString());
		WorldGraph graph = MapCreator.createGraphForUnitTests(settings);

		Image landMask = Image.create(graph.getWidth(), graph.getHeight(), ImageType.Binary);
		try (Painter p = landMask.createPainter())
		{
			graph.drawLandAndOceanBlackAndWhite(p, graph.centers, null);
		}

		compareWithExpected(landMask, "drawLandAndOceanBlackAndWhiteTest", threshold);
	}

	@Test
	public void drawCoastlineWithLakeShoresTest()
	{
		final String settingsFileName = "simpleSmallWorld.nort";
		MapSettings settings = new MapSettings(Paths.get("unit test files", "map settings", settingsFileName).toString());
		WorldGraph graph = MapCreator.createGraphForUnitTests(settings);

		Image coastlineAndLakeShoreMask = Image.create(graph.getWidth(), graph.getHeight(), ImageType.Binary);
		try (Painter p = coastlineAndLakeShoreMask.createPainter(DrawQuality.High))
		{
			p.setColor(Color.white);
			graph.drawCoastlineWithLakeShores(p, settings.coastlineWidth * settings.resolution, null, null);
		}

		compareWithExpected(coastlineAndLakeShoreMask, "coastlineWithLakeShores", threshold);
	}

	@Test
	public void coastShadingTest()
	{
		final String settingsFileName = "simpleSmallWorld.nort";
		MapSettings settings = new MapSettings(Paths.get("unit test files", "map settings", settingsFileName).toString());
		WorldGraph graph = MapCreator.createGraphForUnitTests(settings);

		Image coastlineAndLakeShoreMask = Image.create(graph.getWidth(), graph.getHeight(), ImageType.Binary);
		try (Painter p = coastlineAndLakeShoreMask.createPainter(DrawQuality.High))
		{
			p.setColor(Color.white);
			graph.drawCoastlineWithLakeShores(p, settings.coastlineWidth * settings.resolution, null, null);
		}

		// Test bluing coastline and lake shores.
		double sizeMultiplier = MapCreator.calcSizeMultipilerFromResolutionScale(settings.resolution);
		int blurLevel = (int) (settings.coastShadingLevel * sizeMultiplier);
		float scale = 2.3973336f; // The actual value used when creating this map.
		Image coastShading = ImageHelper.blurAndScale(coastlineAndLakeShoreMask, blurLevel, scale, true);

		compareWithExpected(coastShading, "coastShading", threshold);
	}

	@Test
	public void fractalBGGeneratorTest()
	{
		String expectedFileName = "fractalBackground";

		final int widthAndHeight = 48;
		Image actual = FractalBGGenerator.generate(new Random(42), 1.3f, widthAndHeight, widthAndHeight, 0.75f);

		compareWithExpected(actual, expectedFileName, 0);
	}

	@Test
	public void colorizedBackgroundFromTextureCPUVsGPUTest()
	{
		String testName = "colorizedBackgroundFromTexture";
		Path texturePath = Paths.get("unit test files", "map settings", "custom images", "background textures", "grungy paper.png");
		Color color = Color.create(217, 203, 156, 255);

		// Generate with CPU
		Image cpuResult;
		SkiaImage.setForceCPU(true);
		try
		{
			Image texture = ImageCache.getInstance(Assets.installedArtPack, null).getImageFromFile(texturePath);
			Image grayScaleTexture = ImageHelper.convertToGrayscale(texture);
			cpuResult = ImageHelper.colorify(grayScaleTexture, color, ImageHelper.ColorifyAlgorithm.algorithm3);
		}
		finally
		{
			SkiaImage.setForceCPU(false);
		}

		// Generate with GPU
		Image gpuResult;
		{
			Image texture = ImageCache.getInstance(Assets.installedArtPack, null).getImageFromFile(texturePath);
			Image grayScaleTexture = ImageHelper.convertToGrayscale(texture);
			gpuResult = ImageHelper.colorify(grayScaleTexture, color, ImageHelper.ColorifyAlgorithm.algorithm3);
		}

		// Compare CPU and GPU results
		int diffThreshold = 4;
		String comparisonErrorMessage = MapTestUtil.checkIfImagesEqual(cpuResult, gpuResult, diffThreshold);
		if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
		{
			FileHelper.createFolder(Paths.get("unit test files", failedMapsFolderName).toString());
			ImageHelper.write(cpuResult, Paths.get("unit test files", failedMapsFolderName, testName + " - cpu.png").toString());
			ImageHelper.write(gpuResult, Paths.get("unit test files", failedMapsFolderName, testName + " - gpu.png").toString());
			createImageDiffIfImagesAreSameSize(cpuResult, gpuResult, testName, diffThreshold);
			fail("CPU and GPU results differ: " + comparisonErrorMessage);
		}
	}

	@Test
	public void backgroundFromTextureTest()
	{
		final String settingsFileName = "simpleSmallWorld.nort";
		MapSettings settings = new MapSettings(Paths.get("unit test files", "map settings", settingsFileName).toString());
		Dimension mapBounds = Background.calcMapBoundsAndAdjustResolutionIfNeeded(settings, null);
		Background background = new Background(settings, mapBounds, new LoggerWarningLogger());
		Image actual = background.ocean;

		compareWithExpected(actual, "backgroundFromTexture", 0);
	}

	private void compareWithExpected(Image actual, String testName, int threshold)
	{
		String expectedFilePath = getExpectedFilePath(testName);
		Image expected;

		if (new File(expectedFilePath).exists())
		{
			expected = Assets.readImage(expectedFilePath);
		}
		else
		{
			expected = actual;
			FileHelper.createFolder(Paths.get("unit test files", expectedMapsFolderName).toString());
			ImageHelper.write(actual, expectedFilePath);
			return;
		}

		String comparisonErrorMessage = MapTestUtil.checkIfImagesEqual(expected, actual, threshold);
		if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
		{
			FileHelper.createFolder(Paths.get("unit test files", failedMapsFolderName).toString());
			ImageHelper.write(actual, getFailedFilePath(testName));
			createImageDiffIfImagesAreSameSize(expected, actual, testName);
			fail("Test '" + testName + "' failed: " + comparisonErrorMessage);
		}
	}

	private static String getExpectedFilePath(String testName)
	{
		return Paths.get("unit test files", expectedMapsFolderName, testName + ".png").toString();
	}

	private static String getFailedFilePath(String testName)
	{
		return Paths.get("unit test files", failedMapsFolderName, testName + ".png").toString();
	}

	private static String getDiffFilePath(String testName)
	{
		return Paths.get("unit test files", failedMapsFolderName, testName + " - diff.png").toString();
	}

	private void generateAndCompare(String settingsFileName, Consumer<MapSettings> preprocessSettings)
	{
		MapTestUtil.generateAndCompare(settingsFileName, preprocessSettings, expectedMapsFolderName, failedMapsFolderName);
	}

	private void createImageDiffIfImagesAreSameSize(Image image1, Image image2, String settingsFileName)
	{
		MapTestUtil.createImageDiffIfImagesAreSameSize(image1, image2, settingsFileName, failedMapsFolderName);
	}

	private void createImageDiffIfImagesAreSameSize(Image image1, Image image2, String settingsFileName, int threshold)
	{
		MapTestUtil.createImageDiffIfImagesAreSameSize(image1, image2, settingsFileName, threshold, failedMapsFolderName);
	}

}
