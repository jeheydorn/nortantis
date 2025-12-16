package nortantis.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.MapText;
import nortantis.SettingsGenerator;
import nortantis.WarningLogger;
import nortantis.editor.FreeIcon;
import nortantis.editor.MapParts;
import nortantis.editor.MapUpdater;
import nortantis.geom.IntRectangle;
import nortantis.geom.Rectangle;
import nortantis.platform.Color;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;
import nortantis.swing.MapEdits;
import nortantis.util.Assets;
import nortantis.util.FileHelper;
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

		Assets.disableAddedArtPacksForUnitTests();

		// Create the expected images if they don't already exist.
		// Note that this means that if you haven't already created the images, you run these tests before making changes that will need to
		// be tested.

		FileHelper.createFolder(Paths.get("unit test files", "expected maps").toString());
		FileUtils.deleteDirectory(new File(Paths.get("unit test files", "failed maps").toString()));

		// For each map in the 'unit test files/map settings' folder, create the associated map in 'unit test files/expected maps'.
		String[] mapSettingsFileNames = new File(Paths.get("unit test files", "map settings").toString()).list();

		for (String settingsFileName : mapSettingsFileNames)
		{
			String expectedMapFilePath = getExpectedMapFilePath(settingsFileName);
			String filePath = Paths.get("unit test files", "map settings", settingsFileName).toString();
			if (!new File(filePath).isDirectory() && !new File(expectedMapFilePath).exists())
			{
				MapSettings settings = new MapSettings(filePath);
				MapCreator mapCreator = new MapCreator();
				Logger.println("Creating map '" + expectedMapFilePath + "'");
				Image map = mapCreator.createMap(settings, null, null);
				ImageHelper.write(map, expectedMapFilePath);
			}

		}
	}

	@Test
	public void incrementalUpdate_allTypesOfEdits()
	{
		// Load settings from the .nort file
		String settingsFileName = "allTypesOfEdits.nort";
		String settingsPath = Paths.get("unit test files", "map settings", settingsFileName).toString();
		MapSettings settings = new MapSettings(settingsPath);

		// Create the full map first (baseline)
		MapCreator mapCreator = new MapCreator();
		MapParts mapParts = new MapParts();
		Image fullMap = mapCreator.createMap(settings, null, mapParts);

		final int numberToTest = 500; // TODO lower this.
		final int diffThreshold = 10; // TODO lower this to about 10 hopefully.
		Image fullMapForUpdate = fullMap.deepCopy();
		int failCount = 0;
		int iconNumber = 0;
		for (FreeIcon icon : settings.edits.freeIcons)
		{
			iconNumber++;

			// TODO remove
			if (iconNumber != 418)
			{
				continue; 
			}
			
			IntRectangle changedBounds = mapCreator.incrementalUpdateIcons(settings, mapParts, fullMapForUpdate, Arrays.asList(icon));

			assertTrue("Incremental update should produce bounds", changedBounds != null);
			assertTrue(changedBounds.width > 0);
			assertTrue(changedBounds.height > 0);

			Image expectedSnippet = fullMap.getSubImage(changedBounds);
			Image actualSnippet = fullMapForUpdate.getSubImage(changedBounds);

			// Compare incremental result against expected
			String comparisonErrorMessage = checkIfImagesEqual(expectedSnippet, actualSnippet, diffThreshold);
			if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
			{
				FileHelper.createFolder(Paths.get("unit test files", "failed maps").toString());

				String expectedSnippetName = FilenameUtils.getBaseName(settingsFileName) + " icon " + iconNumber + " expected.png";
				Path expectedPath = Paths.get("unit test files", "failed maps", expectedSnippetName);
				ImageHelper.write(expectedSnippet, expectedPath.toString());

				String failedSnippetName = FilenameUtils.getBaseName(settingsFileName) + " icon " + iconNumber + " failed.png";
				Path failedPath = Paths.get("unit test files", "failed maps", failedSnippetName);
				ImageHelper.write(actualSnippet, failedPath.toString());

				createImageDiffIfImagesAreSameSize(expectedSnippet, actualSnippet, failedSnippetName, diffThreshold);
				failCount++;
			}

			if (iconNumber > numberToTest)
			{
				break;
			}
		}
		String comparisonErrorMessage = checkIfImagesEqual(fullMap, fullMapForUpdate, diffThreshold);
		if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
		{
			FileHelper.createFolder(Paths.get("unit test files", "failed maps").toString());
			String failedMapName = settingsFileName + " full map for incremental draw test";
			ImageHelper.write(fullMapForUpdate, getFailedMapFilePath(failedMapName));
			createImageDiffIfImagesAreSameSize(fullMap, fullMapForUpdate, failedMapName, diffThreshold);
			fail("Incremental update did not match expected image: " + comparisonErrorMessage);
		}

		if (failCount > 0)
		{
			fail(failCount + " incremental update tests failed.");
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
		MapSettings settings = SettingsGenerator.generate(new Random(1), Assets.installedArtPack, null);
		settings.resolution = 0.5;
		Tuple1<Image> mapTuple = new Tuple1<>();
		Tuple1<Boolean> doneTuple = new Tuple1<>(false);
		MapUpdater updater = new MapUpdater(true)
		{

			@Override
			protected void onFinishedDrawing(Image map, boolean anotherDrawIsQueued, int borderWidthAsDrawn,
					IntRectangle incrementalChangeArea, List<String> warningMessages)
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
			FileHelper.createFolder(Paths.get("unit test files", "failed maps").toString());
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
	public void rotatedAndFlippedTwiceWithEditsAndTransparencyTest()
	{
		generateAndCompare("rotatedAndFlippedTwiceWithEditsAndTransparency.nort");
	}

	@Test
	public void rotatedLeftWithTransparentOceanAndPartiallyGrungeTest()
	{
		generateAndCompare("rotatedLeftWithTransparentOceanAndPartiallyGrunge.nort");
	}

	@Test
	public void customImagesWithSizesInFileNames()
	{
		generateAndCompare("customImagesWithSizesInFileNames.nort");
	}

	@Test
	public void iconReplacements()
	{
		// Clear the custom images path to force icons to be replaced with images from the installed art pack.
		List<String> warnings = generateAndCompare("iconReplacements.nort", (settings -> settings.customImagesPath = null))
				.getWarningMessages();

		assertTrue(warnings.contains(
				"Unable to find the art pack 'custom' to load the mountain image group 'jagged'. The art pack 'nortantis' will be used instead."));
		assertTrue(warnings.contains(
				"Unable to find the mountain image group 'jagged' in art pack 'nortantis'. The group 'round' in that art pack will be used instead."));
		assertTrue(warnings.contains(
				"Unable to find the art pack 'custom' to load the hill image group 'jagged'. The art pack 'nortantis' will be used instead."));
		assertTrue(warnings.contains(
				"Unable to find the hill image group 'jagged' in art pack 'nortantis'. The group 'round' in that art pack will be used instead."));
		assertTrue(warnings.contains(
				"Unable to find the art pack 'custom' to load the sand image group 'dunes'. The art pack 'nortantis' will be used instead because it has the same image group folder name."));
		assertTrue(warnings.contains(
				"Unable to find the art pack 'custom' to load the hill image group 'sharp'. The art pack 'nortantis' will be used instead because it has the same image group folder name."));
		assertTrue(warnings.contains(
				"Unable to find the art pack 'custom' to load the tree image group 'generated deciduous 6'. The art pack 'nortantis' will be used instead."));
		assertTrue(warnings.contains(
				"Unable to find the tree image group 'generated deciduous 6' in art pack 'nortantis'. The group 'original pine' in that art pack will be used instead."));
		assertTrue(warnings.contains(
				"Unable to find the art pack 'custom' to load the tree image group 'pine'. The art pack 'nortantis' will be used instead because it has the same image group folder name."));
		assertTrue(warnings.contains(
				"Unable to find the art pack 'custom' to load the mountain image group 'sharp'. The art pack 'nortantis' will be used instead because it has the same image group folder name."));
		assertTrue(warnings.contains(
				"Unable to find the art pack 'custom' to load the icon 'compass 1' from decoration image group 'compasses'. The art pack 'nortantis' will be used instead because it has the same image group folder and image name."));
		assertTrue(warnings.contains(
				"Unable to find the art pack 'custom' to load the icon 'ship 6' from decoration image group 'boats'. The art pack 'nortantis' will be used instead."));
		assertTrue(warnings.contains(
				"Unable to find the decoration image group 'boats' in art pack 'custom'. The group 'ships' in art pack 'nortantis' will be used instead."));
		assertTrue(warnings.contains(
				"Unable to find the art pack 'custom' to load the icon 'small house 1' from city image group 'other'. The art pack 'nortantis' will be used instead."));
		assertTrue(warnings.contains(
				"Unable to find the city image group 'other' in art pack 'custom'. The group 'flat' in art pack 'nortantis' will be used instead."));
		assertTrue(warnings.contains(
				"Unable to find the city icon 'small house 1' in art pack 'custom'. The icon 'town on a hill' in art pack 'nortantis' will be used instead."));
		assertTrue(warnings.contains(
				"Unable to find the art pack 'custom' to load the icon 'town' from city image group 'middle ages'. The art pack 'nortantis' will be used instead because it has the same image group folder and image name."));
		assertEquals(17, warnings.size());
	}

	@Test
	public void iconReplacementsWithMissingIconTypes()
	{
		List<String> warnings = generateAndCompare("iconReplacementsWithMissingIconTypes.nort", (settings) ->
		{
			settings.customImagesPath = Paths.get("unit test files", "map settings", "empty custom images").toAbsolutePath().toString();
		}).getWarningMessages();

		assertTrue(warnings.contains(
				"The art pack 'custom' no longer has tree images, so it does not have the tree image group 'pine'. The art pack 'nortantis' will be used instead because it has the same image group folder name. These trees are not visible because they were drawn at low density, but may become visible if you change the tree height in the Effects tab."));
		assertTrue(warnings.contains(
				"The art pack 'custom' no longer has tree images, so it does not have the tree image group 'generated deciduous 6'. The art pack 'nortantis' will be used instead because it has tree images. These trees are not visible because they were drawn at low density, but may become visible if you change the tree height in the Effects tab."));
		assertTrue(warnings.contains(
				"Unable to find the tree image group 'generated deciduous 6' in art pack 'nortantis'. The group 'original pine' in that art pack will be used instead. These trees are not visible because they were drawn at low density, but may become visible if you change the tree height in the Effects tab."));
		assertTrue(warnings.contains(
				"The art pack 'custom' no longer has mountain images, so it does not have the mountain image group 'jagged'. The art pack 'nortantis' will be used instead because it has mountain images."));
		assertTrue(warnings.contains(
				"Unable to find the mountain image group 'jagged' in art pack 'nortantis'. The group 'round' in that art pack will be used instead."));
		assertTrue(warnings.contains(
				"The art pack 'custom' no longer has hill images, so it does not have the hill image group 'jagged'. The art pack 'nortantis' will be used instead because it has hill images."));
		assertTrue(warnings.contains(
				"Unable to find the hill image group 'jagged' in art pack 'nortantis'. The group 'round' in that art pack will be used instead."));
		assertTrue(warnings.contains(
				"The art pack 'custom' no longer has sand images, so it does not have the sand image group 'dunes'. The art pack 'nortantis' will be used instead because it has the same image group folder name."));
		assertTrue(warnings.contains(
				"The art pack 'custom' no longer has hill images, so it does not have the hill image group 'sharp'. The art pack 'nortantis' will be used instead because it has the same image group folder name."));
		assertTrue(warnings.contains(
				"The art pack 'custom' no longer has tree images, so it does not have the tree image group 'generated deciduous 6'. The art pack 'nortantis' will be used instead because it has tree images."));
		assertTrue(warnings.contains(
				"Unable to find the tree image group 'generated deciduous 6' in art pack 'nortantis'. The group 'original pine' in that art pack will be used instead."));
		assertTrue(warnings.contains(
				"The art pack 'custom' no longer has tree images, so it does not have the tree image group 'pine'. The art pack 'nortantis' will be used instead because it has the same image group folder name."));
		assertTrue(warnings.contains(
				"The art pack 'custom' no longer has mountain images, so it does not have the mountain image group 'sharp'. The art pack 'nortantis' will be used instead because it has the same image group folder name."));
		assertTrue(warnings.contains(
				"The art pack 'custom' no longer has decoration images, so it does not have the icon 'compass 1' from decoration image group 'compasses'. The art pack 'nortantis' will be used instead because it has the same image group folder and image name."));
		assertTrue(warnings.contains(
				"The art pack 'custom' no longer has decoration images, so it does not have the icon 'ship 6' from decoration image group 'boats'. The art pack 'nortantis' will be used instead because it has decoration images."));
		assertTrue(warnings.contains(
				"Unable to find the decoration image group 'boats' in art pack 'custom'. The group 'ships' in art pack 'nortantis' will be used instead."));
		assertTrue(warnings.contains(
				"The art pack 'custom' no longer has city images, so it does not have the icon 'small house 1' from city image group 'other'. The art pack 'nortantis' will be used instead because it has city images."));
		assertTrue(warnings.contains(
				"Unable to find the city image group 'other' in art pack 'custom'. The group 'flat' in art pack 'nortantis' will be used instead."));
		assertTrue(warnings.contains(
				"Unable to find the city icon 'small house 1' in art pack 'custom'. The icon 'town on a hill' in art pack 'nortantis' will be used instead."));
		assertTrue(warnings.contains(
				"The art pack 'custom' no longer has city images, so it does not have the icon 'town' from city image group 'middle ages'. The art pack 'nortantis' will be used instead because it has the same image group folder and image name."));
		assertTrue(warnings.contains(
				"The art pack 'custom' no longer has decoration images, so it does not have the icon 'compass 6' from decoration image group 'compasses'. The art pack 'nortantis' will be used instead because it has the same image group folder and image name."));
		assertEquals(21, warnings.size());
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
			FileHelper.createFolder(Paths.get("unit test files", "failed maps").toString());
			ImageHelper.write(actual, getFailedMapFilePath(expectedFileName));
			createImageDiffIfImagesAreSameSize(expected, actual, expectedFileName);
			fail(comparisonErrorMessage);
		}

	}

	private void generateAndCompare(String settingsFileName)
	{
		generateAndCompare(settingsFileName, null);
	}

	private WarningLogger generateAndCompare(String settingsFileName, Consumer<MapSettings> preprocessSettings)
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
		Image actual;
		actual = mapCreator.createMap(settings, null, null);

		// Test deep copy after creating the map because MapCreator sets some fields during map creation, so it's a
		// more complete test that way.
		testDeepCopy(settings);

		String comparisonErrorMessage = checkIfImagesEqual(expected, actual);
		if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
		{
			FileHelper.createFolder(Paths.get("unit test files", "failed maps").toString());
			ImageHelper.write(actual, getFailedMapFilePath(settingsFileName));
			createImageDiffIfImagesAreSameSize(expected, actual, settingsFileName);
			fail(comparisonErrorMessage);
		}

		return mapCreator;
	}

	private void testDeepCopy(MapSettings settings)
	{
		MapSettings copy = settings.deepCopy();
		assertEquals(settings, copy);
	}

	private String checkIfImagesEqual(Image image1, Image image2)
	{
		return checkIfImagesEqual(image1, image2, 0);
	}

	private String checkIfImagesEqual(Image image1, Image image2, int threshold)
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
					if (threshold == 0)
					{
						if (image1.getRGB(x, y) != image2.getRGB(x, y))
						{
							return "Images differ at pixel (" + x + ", " + y + ")";
						}
					}
					else
					{
						int diff = image1.getPixelColor(x, y).manhattanDistanceTo(image2.getPixelColor(x, y));
						if (diff > threshold)
						{
							return "Images differ at pixel (" + x + ", " + y + ") by " + diff;
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

	private void createImageDiffIfImagesAreSameSize(Image image1, Image image2, String settingsFileName)
	{
		createImageDiffIfImagesAreSameSize(image1, image2, settingsFileName, 0);
	}

	private void createImageDiffIfImagesAreSameSize(Image image1, Image image2, String settingsFileName, int threshold)
	{
		if (image1.getWidth() == image2.getWidth() && image1.getHeight() == image2.getHeight())
		{
			Image diff = Image.create(image1.getWidth(), image1.getHeight(), ImageType.RGB);
			for (int x = 0; x < image1.getWidth(); x++)
			{
				for (int y = 0; y < image1.getHeight(); y++)
				{
					if (threshold == 0)
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
					else
					{
						int difference = image1.getPixelColor(x, y).manhattanDistanceTo(image2.getPixelColor(x, y));
						if (difference > threshold)
						{
							diff.setRGB(x, y, Color.white.getRGB());
						}
						else
						{
							diff.setRGB(x, y, Color.black.getRGB());
						}
					}
				}
			}
			ImageHelper.write(diff, getDiffFilePath(settingsFileName));
		}
	}
}
