package nortantis.test;

import nortantis.*;
import nortantis.editor.FreeIcon;
import nortantis.editor.MapParts;
import nortantis.geom.Dimension;
import nortantis.geom.IntRectangle;
import nortantis.platform.*;
import nortantis.platform.skia.SkiaFactory;
import nortantis.platform.skia.SkiaImage;
import nortantis.util.Assets;
import nortantis.util.FileHelper;
import nortantis.util.ImageHelper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Random;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public class SkiaMapCreatorTest
{
	static final String failedMapsFolderName = "failed maps skia";
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
		try
		{
			// Force CPU because GPU-generated images have a tiny amount of random variation for some reason.
			SkiaImage.setForceCPU(true);
			generateAndCompare("simpleSmallWorld.nort", (settings) -> settings.resolution = 0.25);
		}
		finally
		{
			SkiaImage.setForceCPU(false);
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
					String comparisonErrorMessage = MapTestUtil.checkIfImagesEqual(expectedSnippet, actualSnippet, threshold);
					if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
					{
						FileHelper.createFolder(Paths.get("unit test files", failedMapsFolderName).toString());

						String expectedSnippetName = FilenameUtils.getBaseName(settingsFileName) + " icon " + iconNumber + " expected.png";
						Path expectedPath = Paths.get("unit test files", failedMapsFolderName, expectedSnippetName);
						ImageHelper.write(expectedSnippet, expectedPath.toString());

						String failedSnippetName = FilenameUtils.getBaseName(settingsFileName) + " icon " + iconNumber + " failed.png";
						Path failedPath = Paths.get("unit test files", failedMapsFolderName, failedSnippetName);
						ImageHelper.write(actualSnippet, failedPath.toString());

						createImageDiffIfImagesAreSameSize(expectedSnippet, actualSnippet, failedSnippetName, threshold);
					}
				}

				String comparisonErrorMessage = MapTestUtil.checkIfImagesEqual(fullMap, fullMapForUpdates, threshold);
				if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
				{
					FileHelper.createFolder(Paths.get("unit test files", failedMapsFolderName).toString());
					String failedMapName = FilenameUtils.getBaseName(settingsFileName) + " full map for incremental draw test";
					ImageHelper.write(fullMapForUpdates, MapTestUtil.getFailedMapFilePath(failedMapName, failedMapsFolderName));
					createImageDiffIfImagesAreSameSize(fullMap, fullMapForUpdates, failedMapName, threshold);
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

		compareWithExpected(landMask, "drawLandAndOceanBlackAndWhiteTest", 0);
	}

	@Test
	public void drawCoastlineWithLakeShoresTest()
	{
		try
		{
			SkiaImage.setForceCPU(true);

			final String settingsFileName = "simpleSmallWorld.nort";
			MapSettings settings = new MapSettings(Paths.get("unit test files", "map settings", settingsFileName).toString());
			WorldGraph graph = MapCreator.createGraphForUnitTests(settings);

			Image coastlineAndLakeShoreMask = Image.create(graph.getWidth(), graph.getHeight(), ImageType.Binary);
			try (Painter p = coastlineAndLakeShoreMask.createPainter(DrawQuality.High))
			{
				p.setColor(Color.white);
				graph.drawCoastlineWithLakeShores(p, settings.coastlineWidth * settings.resolution, null, null);
			}

			compareWithExpected(coastlineAndLakeShoreMask, "coastlineWithLakeShores", 0);
		}
		finally
		{
			SkiaImage.setForceCPU(false);
		}

	}

	@Test
	public void coastShadingTest()
	{
		try
		{
			SkiaImage.setForceCPU(true);

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

			compareWithExpected(coastShading, "coastShading", 0);
		}
		finally
		{
			SkiaImage.setForceCPU(false);
		}

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
		String comparisonErrorMessage = MapTestUtil.checkIfImagesEqual(cpuResult, gpuResult, threshold);
		if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
		{
			FileHelper.createFolder(Paths.get("unit test files", failedMapsFolderName).toString());
			ImageHelper.write(cpuResult, Paths.get("unit test files", failedMapsFolderName, testName + " - cpu.png").toString());
			ImageHelper.write(gpuResult, Paths.get("unit test files", failedMapsFolderName, testName + " - gpu.png").toString());
			createImageDiffIfImagesAreSameSize(cpuResult, gpuResult, testName, threshold);
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
		MapTestUtil.generateAndCompare(settingsFileName, preprocessSettings, expectedMapsFolderName, failedMapsFolderName, 0);
	}

	private void createImageDiffIfImagesAreSameSize(Image image1, Image image2, String settingsFileName)
	{
		MapTestUtil.createImageDiffIfImagesAreSameSize(image1, image2, settingsFileName, failedMapsFolderName);
	}

	private void createImageDiffIfImagesAreSameSize(Image image1, Image image2, String settingsFileName, int threshold)
	{
		MapTestUtil.createImageDiffIfImagesAreSameSize(image1, image2, settingsFileName, threshold, failedMapsFolderName);
	}

	@Test
	public void cityContentMaskTest()
	{
		// Load a city icon
		Path iconPath = Paths.get("assets", "installed art pack", "cities", "middle ages", "walled city width=32.png");
		Image cityIcon = Assets.readImage(iconPath.toString());

		// Create ImageAndMasks with IconType.cities to trigger flood fill content mask creation
		ImageAndMasks imageAndMasks = new ImageAndMasks(cityIcon, IconType.cities, 32.0, // widthFromFileName
				"installed art pack", "middle ages", "walled city");

		// Get or create the content mask
		Image contentMask = imageAndMasks.getOrCreateContentMask();

		// Count white pixels in the content mask
		int whiteCount = 0;
		try (PixelReader maskReader = contentMask.createPixelReader())
		{
			for (int y = 0; y < contentMask.getHeight(); y++)
			{
				for (int x = 0; x < contentMask.getWidth(); x++)
				{
					if (maskReader.getGrayLevel(x, y) > 0)
						whiteCount++;
				}
			}
		}

		// The content mask should have white pixels representing the city icon's content area.
		// If flood fill fails, the mask will be all black (0 white pixels).
		assertTrue(whiteCount > 0, "Content mask should have white pixels representing the city icon content area, but found " + whiteCount);
	}

	@Test
	public void newRandomMapTest() throws IOException
	{
		try
		{
			// Force CPU because GPU-generated images have a tiny amount of random variation for some reason.
			SkiaImage.setForceCPU(true);
			MapSettings settings = MapTestUtil.generateRandomAndCompare(5, expectedMapsFolderName, failedMapsFolderName, threshold);
		}
		finally
		{
			SkiaImage.setForceCPU(false);
		}

	}
}
