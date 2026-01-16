package nortantis.test;

import nortantis.FractalBGGenerator;
import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.WorldGraph;
import nortantis.platform.*;
import nortantis.platform.awt.AwtFactory;
import nortantis.platform.skia.SkiaFactory;
import nortantis.util.Assets;
import nortantis.util.FileHelper;
import nortantis.util.ImageHelper;
import nortantis.util.Logger;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.fail;

public class SkiaMapCreatorTest
{
	final static String failedMapsFolderName = "failed maps skia";
	private static final String expectedFolderName = "expected maps skia";
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
		generateAndCompare("simpleSmallWorld.nort");
	}

	@Test
	public void drawLandAndOceanBlackAndWhiteTest()
	{
		final String settingsFileName = "simpleSmallWorld.nort";
		MapSettings settings = new MapSettings(Paths.get("unit test files", "map settings", settingsFileName).toString());
		WorldGraph graph = MapCreator.createGraphForUnitTests(settings);

		Image landMask = Image.create(graph.getWidth(), graph.getHeight(), ImageType.Binary);
		{
			Painter p = landMask.createPainter();
			graph.drawLandAndOceanBlackAndWhite(p, graph.centers, null);
		}

		compareWithExpected(landMask, "drawLandAndOceanBlackAndWhiteTest", threshold);
	}

	@Test
	public void drawCoastlineWithLakeShoresAndBlurTest()
	{
		final String settingsFileName = "simpleSmallWorld.nort";
		MapSettings settings = new MapSettings(Paths.get("unit test files", "map settings", settingsFileName).toString());
		WorldGraph graph = MapCreator.createGraphForUnitTests(settings);

		Image coastlineAndLakeShoreMask = Image.create(graph.getWidth(), graph.getHeight(), ImageType.Binary);
		Painter p = coastlineAndLakeShoreMask.createPainter(DrawQuality.High);
		p.setColor(Color.white);
		graph.drawCoastlineWithLakeShores(p, settings.coastlineWidth * settings.resolution, null, null);

		compareWithExpected(coastlineAndLakeShoreMask, "coastlineWithLakeShores", threshold);
	}

	@Test
	public void coastShadingTest()
	{
		final String settingsFileName = "simpleSmallWorld.nort";
		MapSettings settings = new MapSettings(Paths.get("unit test files", "map settings", settingsFileName).toString());
		WorldGraph graph = MapCreator.createGraphForUnitTests(settings);

		Image coastlineAndLakeShoreMask = Image.create(graph.getWidth(), graph.getHeight(), ImageType.Binary);
		Painter p = coastlineAndLakeShoreMask.createPainter(DrawQuality.High);
		p.setColor(Color.white);
		graph.drawCoastlineWithLakeShores(p, settings.coastlineWidth * settings.resolution, null, null);

		// Test bluing coastline and lake shores.
		double sizeMultiplier = MapCreator.calcSizeMultipilerFromResolutionScale(settings.resolution);
		int blurLevel = (int) (settings.coastShadingLevel * sizeMultiplier);
		float scale = 2.3973336f; // The actual value used when creating this map.
		Image coastShading = ImageHelper.blurAndScale(coastlineAndLakeShoreMask, blurLevel, scale, true);

		compareWithExpected(coastShading, "coastShading", threshold);
	}

	@Test
	public void fractalBGGenerator()
	{
		String expectedFileName = "skia_fractalBGGenerator";
		String expectedMapFilePath = MapTestUtil.getExpectedMapFilePath(expectedFileName);
		Image expected;
		if (new File(expectedMapFilePath).exists())
		{
			expected = Assets.readImage(expectedMapFilePath);
		}
		else
		{
			expected = null;
		}

		final int widthAndHeight = 48;
		Logger.println("Creating fractal image " + widthAndHeight + "x" + widthAndHeight + " using Skia");
		Image actual = FractalBGGenerator.generate(new Random(42), 1.3f, widthAndHeight, widthAndHeight, 0.75f);

		if (expected == null)
		{
			// Create the expected map from the actual one.
			expected = actual;
			ImageHelper.write(actual, expectedMapFilePath);
		}

		String comparisonErrorMessage = MapTestUtil.checkIfImagesEqual(expected, actual);
		if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
		{
			FileHelper.createFolder(Paths.get("unit test files", "failed maps").toString());
			ImageHelper.write(actual, MapTestUtil.getFailedMapFilePath(expectedFileName, failedMapsFolderName));
			MapTestUtil.createImageDiffIfImagesAreSameSize(expected, actual, expectedFileName, failedMapsFolderName);
			fail(comparisonErrorMessage);
		}
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
			FileHelper.createFolder(Paths.get("unit test files", expectedFolderName).toString());
			ImageHelper.write(actual, expectedFilePath);
			return;
		}

		String comparisonErrorMessage = MapTestUtil.checkIfImagesEqual(expected, actual, threshold);
		if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
		{
			FileHelper.createFolder(Paths.get("unit test files", failedMapsFolderName).toString());
			ImageHelper.write(actual, getFailedFilePath(testName));
			MapTestUtil.createImageDiffIfImagesAreSameSize(expected, actual, testName, failedMapsFolderName);
			fail("Test '" + testName + "' failed: " + comparisonErrorMessage);
		}
	}

	private static String getExpectedFilePath(String testName)
	{
		return Paths.get("unit test files", expectedFolderName, testName + ".png").toString();
	}

	private static String getFailedFilePath(String testName)
	{
		return Paths.get("unit test files", failedMapsFolderName, testName + ".png").toString();
	}

	private static String getDiffFilePath(String testName)
	{
		return Paths.get("unit test files", failedMapsFolderName, testName + " - diff.png").toString();
	}

	private void generateAndCompare(String settingsFileName)
	{
		MapTestUtil.generateAndCompare(settingsFileName, null, failedMapsFolderName);
	}

}
