package nortantis.test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.nio.file.Paths;
import java.util.Random;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import nortantis.FractalBGGenerator;
import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.platform.Color;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.PixelReader;
import nortantis.platform.PixelReaderWriter;
import nortantis.platform.PlatformFactory;
import nortantis.platform.skia.SkiaFactory;
import nortantis.util.Assets;
import nortantis.util.FileHelper;
import nortantis.util.ImageHelper;
import nortantis.util.Logger;

public class SkiaMapCreatorTest
{
	final static String failedMapsFolderName = "failed maps skia";

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

	private void generateAndCompare(String settingsFileName)
	{
		String settingsPath = Paths.get("unit test files", "map settings", settingsFileName).toString();
		MapSettings settings = new MapSettings(settingsPath);

		MapCreator mapCreator = new MapCreator();
		Logger.println("Creating map from '" + settingsPath + "' using Skia");
		Image actual = mapCreator.createMap(settings, null, null);

		// For now, we just want to make sure it doesn't crash and produces an image of the right size.
		// Comparing with AWT output might be tricky due to rendering differences.
		assertTrue(actual.getWidth() > 0);
		assertTrue(actual.getHeight() > 0);

		// TODO - Once I have Skia rendering working, change this test to compare actual pixels by removing this method and replacing it with calls to MapTestUtil.generateAndCompare.

		// Save it to see what it looks like
		FileHelper.createFolder(Paths.get("unit test files", failedMapsFolderName).toString());
		actual.write(Paths.get("unit test files", failedMapsFolderName, "skia_" + settingsFileName + ".png").toString());
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

}
