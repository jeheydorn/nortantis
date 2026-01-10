package nortantis.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.editor.MapParts;
import nortantis.geom.IntRectangle;
import nortantis.platform.Image;
import nortantis.platform.PlatformFactory;
import nortantis.platform.skia.SkiaFactory;
import nortantis.util.Assets;
import nortantis.util.FileHelper;
import nortantis.util.Logger;

public class SkiaMapCreatorTest
{
	@BeforeAll
	public static void setUpBeforeClass() throws Exception
	{
		PlatformFactory.setInstance(new SkiaFactory());
		Assets.disableAddedArtPacksForUnitTests();

		FileHelper.createFolder(Paths.get("unit test files", "expected maps").toString());
		FileUtils.deleteDirectory(new File(Paths.get("unit test files", "failed maps").toString()));
	}

	@Test
	public void allTypesOfEdits()
	{
		generateAndCompare("allTypesOfEdits.nort");
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

		// TODO - Once I have Skia rendering working, change this test to compare actual pixels.

		// Save it to see what it looks like
		FileHelper.createFolder(Paths.get("unit test files", "failed maps").toString());
		actual.write(Paths.get("unit test files", "failed maps", "skia_" + settingsFileName + ".png").toString());
	}
}
