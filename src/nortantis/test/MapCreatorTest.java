package nortantis.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.SettingsGenerator;
import nortantis.editor.MapUpdater;
import nortantis.geom.Rectangle;
import nortantis.platform.Color;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;
import nortantis.swing.MapEdits;
import nortantis.util.Helper;
import nortantis.util.ImageHelper;
import nortantis.util.Logger;
import nortantis.util.Tuple1;

public class MapCreatorTest
{

	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
		// Tell drawing code to use AWT.
		PlatformFactory.setInstance(new AwtFactory());

		// Create the expected images if they don't already exist.
		// Note that this means that if you haven't already created the images, you run these tests before making changes that will need to
		// be tested.

		Helper.createFolder(Paths.get("unit test files", "expected maps").toString());
		FileUtils.deleteDirectory(new File(Paths.get("unit test files", "failed maps").toString()));

		// For each map in the 'unit test files/map settings' folder, create the associated map in 'unit test files/expected maps'.
		String[] mapSettingsFileNames = new File(Paths.get("unit test files", "map settings").toString()).list();

		for (String settingsFileName : mapSettingsFileNames)
		{
			String expectedMapFilePath = getExpectedMapFilePath(settingsFileName);
			if (!new File(expectedMapFilePath).exists())
			{
				MapSettings settings = new MapSettings(Paths.get("unit test files", "map settings", settingsFileName).toString());
				MapCreator mapCreator = new MapCreator();
				Logger.println("Creating map '" + expectedMapFilePath + "'");
				Image map = mapCreator.createMap(settings, null, null);
				ImageHelper.write(map, expectedMapFilePath);
			}

		}
	}

	/**
	 * Tests that a map which is drawn with no edits matches the same map drawn the second time with newly created edits. This simulates the
	 * case where you create a new map in the editor and it draws for the first time, then you do something to trigger it to do a full
	 * redraw.
	 */
	@Test
	public void drawWithoutEditsMatchesWithEdits()
	{
		MapSettings settings = SettingsGenerator.generate(new Random(1), null);
		settings.resolution = 0.5;
		Tuple1<Image> mapTuple = new Tuple1<>();
		Tuple1<Boolean> doneTuple = new Tuple1<>(false);
		MapUpdater updater = new MapUpdater(true)
		{

			@Override
			protected void onFinishedDrawing(Image map, boolean anotherDrawIsQueued, int borderWidthAsDrawn,
					Rectangle incrementalChangeArea, List<String> warningMessages)
			{
				mapTuple.set(map);
				doneTuple.set(true);
			}

			@Override
			protected void onFailedToDraw()
			{
				fail("Updater failed to draw.");
			}

			@Override
			protected void onBeginDraw()
			{
			}

			@Override
			public MapSettings getSettingsFromGUI()
			{
				return settings;
			}

			@Override
			protected MapEdits getEdits()
			{
				return settings.edits;
			}

			@Override
			protected Image getCurrentMapForIncrementalUpdate()
			{
				throw new UnsupportedOperationException();
			}
		};

		updater.setEnabled(true);

		assertTrue(!settings.edits.isInitialized());
		Image drawnWithoutEdits = createMapUsingUpdater(updater, mapTuple, doneTuple);
	
		assertTrue(settings.edits.isInitialized());
		Image drawnWithEdits = createMapUsingUpdater(updater, mapTuple, doneTuple);

		String comparisonErrorMessage = checkIfImagesEqual(drawnWithoutEdits, drawnWithEdits);
		if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
		{
			Helper.createFolder(Paths.get("unit test files", "failed maps").toString());
			drawnWithoutEdits.write(Paths.get("unit test files", "failed maps", "compareWithAndWithoutEdits_NoEdits.png").toString());
			drawnWithEdits.write(Paths.get("unit test files", "failed maps", "compareWithAndWithoutEdits_WithEdits.png").toString());
			createImageDiffIfImagesAreSameSize(drawnWithoutEdits, drawnWithEdits, "noOceanOrCoastEffects");
			fail(comparisonErrorMessage);
		}
	}
	
	private Image createMapUsingUpdater(MapUpdater updater, Tuple1<Image> mapTuple, Tuple1<Boolean> doneTuple)
	{
		doneTuple.set(false);
		mapTuple.set(null);
		updater.createAndShowMapFull();
		updater.dowWhenMapIsNotDrawing(() ->
		{
			doneTuple.set(true);
		});

		while (!doneTuple.get())
		{
			try
			{
				Thread.sleep(100);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
				break;
			}
		}
		return mapTuple.get();
	}
	
	@Test
	public void newRandomMapTest1()
	{
		generateRandomAndCompare(1);
	}
		
	@Test
	public void newRandomMapTest2()
	{
		generateRandomAndCompare(3);
	}

	@Test
	public void allTypesOfEdits()
	{
		generateAndCompare("allTypesOfEdits.nort");
	}

	@Test
	public void frayedEdge_regionColors_textureImageBackground()
	{
		generateAndCompare("frayedEdge_regionColors_textureImageBackground.nort");
	}

	@Test
	public void noText_NoRegions_SquareBackground_ConcentricWaves_WithEdits()
	{
		generateAndCompare("noText_NoRegions_SquareBackground_ConcentricWaves_WithEdits.nort");
	}

	@Test
	public void preventCreatingOnlyOneTectonicPlate()
	{
		generateAndCompare("preventCreatingOnlyOneTectonicPlate.nort");
	}

	@Test
	public void noText_WithCities_GoldenRatio()
	{
		generateAndCompare("noText_WithCities_GoldenRatio.nort");
	}

	@Test
	public void noText_WithCities_GoldenRatio_withEdits()
	{
		generateAndCompare("noText_WithCities_GoldenRatio_withEdits.nort");
	}

	@Test
	public void smallWorld_constrainedToForceGeneratingLand()
	{
		generateAndCompare("smallWorld_constrainedToForceGeneratingLand.nort");
	}

	@Test
	public void smallWorld_allTextDeletedByHand_shouldNotRegenerateText()
	{
		generateAndCompare("smallWorld_allTextDeletedByHand_shouldNotRegenerateText.nort");
	}

	@Test
	public void backgroundFromTexture_landNotColorized()
	{
		generateAndCompare("backgroundFromTexture_landNotColorized.nort");
	}

	@Test
	public void backgroundFromTexture_nothingColorized()
	{
		generateAndCompare("backgroundFromTexture_nothingColorized.nort");
	}

	@Test
	public void backgroundFromTexture_oceanNotColorized()
	{
		generateAndCompare("backgroundFromTexture_oceanNotColorized.nort");
	}

	@Test
	public void generatedSpecialCharacterInTitleAndColorChanges_replacementCharacterRemoved()
	{
		generateAndCompare("generatedSpecialCharacterInTitleAndColorChanges_replacementCharacterRemoved.nort");
	}

	@Test
	public void propertiesConversion_allColorsChanged()
	{
		generateAndCompare("propertiesConversion_allColorsChanged.properties");
	}

	@Test
	public void propertiesConversion_allTypesOfEdits()
	{
		generateAndCompare("propertiesConversion_allTypesOfEdits.properties");
	}

	@Test
	public void propertiesConversion_noText_WithCities_GoldenRatio()
	{
		generateAndCompare("propertiesConversion_noText_WithCities_GoldenRatio.properties");
	}

	@Test
	public void regressionTest_polygonsOnTopBug()
	{
		generateAndCompare("regressionTest_polygonsOnTopBug.nort");
	}

	@Test
	public void iconsDrawOverCoastlines()
	{
		generateAndCompare("iconsDrawOverCoastlines.nort");
	}

	@Test
	public void clearedMapRegionEdit0Removed()
	{
		generateAndCompare("clearedMapRegionEdit0Removed.nort");
	}

	private static String getExpectedMapFilePath(String settingsFileName)
	{
		return Paths.get("unit test files", "expected maps", FilenameUtils.getBaseName(settingsFileName) + ".png").toString();
	}

	private static String getFailedMapFilePath(String settingsFileName)
	{
		return Paths.get("unit test files", "failed maps", FilenameUtils.getBaseName(settingsFileName) + ".png").toString();
	}

	private static String getDiffFilePath(String settingsFileName)
	{
		return Paths.get("unit test files", "failed maps", FilenameUtils.getBaseName(settingsFileName) + " - diff.png").toString();
	}
	
	private void generateRandomAndCompare(long seed)
	{
		String expectedFileName = "random map for seed " + seed;
		String expectedMapFilePath = getExpectedMapFilePath(expectedFileName);
		Image expected;
		if (new File(expectedMapFilePath).exists())
		{
			expected = ImageHelper.read(expectedMapFilePath);
		}
		else
		{
			expected = null;
		}
		
		MapSettings settings = SettingsGenerator.generate(new Random(seed), null);
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
			Helper.createFolder(Paths.get("unit test files", "failed maps").toString());
			ImageHelper.write(actual, getFailedMapFilePath(expectedFileName));
			createImageDiffIfImagesAreSameSize(expected, actual, expectedFileName);
			fail(comparisonErrorMessage);
		}
		
	}

	private void generateAndCompare(String settingsFileName)
	{
		String expectedMapFilePath = getExpectedMapFilePath(settingsFileName);
		Image expected;
		if (new File(expectedMapFilePath).exists())
		{
			expected = ImageHelper.read(expectedMapFilePath);
		}
		else
		{
			expected = null;
		}
		
		String settingsPath = Paths.get("unit test files", "map settings", settingsFileName).toString();
		MapSettings settings = new MapSettings(settingsPath);
		MapCreator mapCreator = new MapCreator();
		Logger.println("Creating map from '" + settingsPath + "'");
		Image actual;
		actual = mapCreator.createMap(settings, null, null);

		// Test deep copy after creating the map because MapCreator sets some fields during map creation, so it's a
		// more complete test that way.
		testDeepCopy(settings);

		String comparisonErrorMessage = checkIfImagesEqual(expected, actual);
		if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
		{
			Helper.createFolder(Paths.get("unit test files", "failed maps").toString());
			ImageHelper.write(actual, getFailedMapFilePath(settingsFileName));
			createImageDiffIfImagesAreSameSize(expected, actual, settingsFileName);
			fail(comparisonErrorMessage);
		}
	}

	private void testDeepCopy(MapSettings settings)
	{
		MapSettings copy = settings.deepCopy();
		assertEquals(settings, copy);
	}

	private String checkIfImagesEqual(Image image1, Image image2)
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
			for (int x = 0; x < image1.getWidth(); x++)
			{
				for (int y = 0; y < image1.getHeight(); y++)
				{
					if (image1.getRGB(x, y) != image2.getRGB(x, y))
					{
						return "Images differ at pixel (" + x + ", " + y + ").";
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

	private void createImageDiffIfImagesAreSameSize(Image image1, Image image2, String settingsFileName)
	{
		if (image1.getWidth() == image2.getWidth() && image1.getHeight() == image2.getHeight())
		{
			Image diff = Image.create(image1.getWidth(), image1.getHeight(), ImageType.RGB);
			for (int x = 0; x < image1.getWidth(); x++)
			{
				for (int y = 0; y < image1.getHeight(); y++)
				{
					if (image1.getRGB(x, y) != image2.getRGB(x, y))
					{
						diff.setRGB(x, y, Color.white.getRGB());
					}
					else
					{
						diff.setRGB(x, y, Color.black.getRGB());
					}
				}
			}
			ImageHelper.write(diff, getDiffFilePath(settingsFileName));
		}
	}
}
