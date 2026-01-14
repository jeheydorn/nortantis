package nortantis.test;

import nortantis.FractalBGGenerator;
import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.WorldGraph;
import nortantis.editor.MapParts;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.Painter;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;
import nortantis.platform.skia.SkiaFactory;
import nortantis.util.*;
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

		String settingsPath = Paths.get("unit test files", "map settings", settingsFileName).toString();
		MapSettings settings = new MapSettings(settingsPath);
		MapCreator mapCreator = new MapCreator();
		Logger.println("Creating map from '" + settingsPath + "' in drawLandAndOceanBlackAndWhiteTest");

		// Create the map to populate mapParts so we can get the WorldGraph.
		MapParts mapParts = new MapParts();
		mapCreator.createMap(settings, null, mapParts);
		WorldGraph graph = mapParts.graph;

		Image landMask = Image.create(graph.getWidth(), graph.getHeight(), ImageType.Binary);
		{
			Painter g = landMask.createPainter();
			graph.drawLandAndOceanBlackAndWhite(g, graph.centers, null);
		}

		compareWithExpected(landMask, "drawLandAndOceanBlackAndWhiteTest");
	}

	private WorldGraph createGraph(String settingsFileName)
	{
		String settingsPath = Paths.get("unit test files", "map settings", settingsFileName).toString();
		MapSettings settings = new MapSettings(settingsPath);
		MapCreator mapCreator = new MapCreator();

		// Create the map to populate mapParts so we can get the WorldGraph.
		MapParts mapParts = new MapParts();
		mapCreator.createMap(settings, null, mapParts);
		WorldGraph graph = mapParts.graph;
		return graph;
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

	private void compareWithExpected(Image actual, String testName)
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

		String comparisonErrorMessage = MapTestUtil.checkIfImagesEqual(expected, actual);
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
