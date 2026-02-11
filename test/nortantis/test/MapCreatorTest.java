package nortantis.test;

import nortantis.*;
import nortantis.editor.*;
import nortantis.geom.IntPoint;
import nortantis.geom.IntRectangle;
import nortantis.graph.voronoi.Center;
import nortantis.platform.Image;
import nortantis.platform.ImageHelper;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;
import nortantis.swing.MapEdits;
import nortantis.util.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class MapCreatorTest
{
	static final String failedMapsFolderName = "failed maps";
	static final String expectedMapsFolderName = "expected maps";

	@BeforeAll
	public static void setUpBeforeClass() throws Exception
	{
		PlatformFactory.setInstance(new AwtFactory());
		nortantis.swing.translation.Translation.initialize();
		Assets.disableAddedArtPacksForUnitTests();

		FileHelper.createFolder(Paths.get("unit test files", "expected maps").toString());
		FileUtils.deleteDirectory(new File(Paths.get("unit test files", failedMapsFolderName).toString()));

		String[] mapSettingsFileNames = new File(Paths.get("unit test files", "map settings").toString()).list();

		for (String settingsFileName : mapSettingsFileNames)
		{
			String expectedMapFilePath = MapTestUtil.getExpectedMapFilePath(settingsFileName, expectedMapsFolderName);
			String filePath = Paths.get("unit test files", "map settings", settingsFileName).toString();
			if (!new File(filePath).isDirectory() && !new File(expectedMapFilePath).exists())
			{
				MapSettings settings = new MapSettings(filePath);
				MapCreator mapCreator = new MapCreator();
				Logger.println("Creating map '" + expectedMapFilePath + "'");
				Image map = mapCreator.createMap(settings, null, null);
				ImageHelper.getInstance().write(map, expectedMapFilePath);
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
		settings.resolution = 0.5;

		// Force high memory mode so that mapBeforeAddingText is created. The low memory fallback path
		// re-renders terrain for text-only changes, which can have tiny convolution edge differences.
		MapCreator.overrideMemoryMode(false);
		try
		{

			// Create the full map first (baseline)
			MapCreator mapCreator = new MapCreator();
			MapParts mapParts = new MapParts();
			Image fullMap = mapCreator.createMap(settings, null, mapParts);
			final int diffThreshold = 10;
			int failCount = 0;

			{
				final int numberToTest = 50;
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
						ImageHelper.getInstance().write(expectedSnippet, expectedPath.toString());

						String failedSnippetName = FilenameUtils.getBaseName(settingsFileName) + " icon " + iconNumber + " failed.png";
						Path failedPath = Paths.get("unit test files", failedMapsFolderName, failedSnippetName);
						ImageHelper.getInstance().write(actualSnippet, failedPath.toString());

						createImageDiffIfImagesAreSameSize(expectedSnippet, actualSnippet, failedSnippetName, diffThreshold);
						failCount++;
					}
				}

				String comparisonErrorMessage = MapTestUtil.checkIfImagesEqual(fullMap, fullMapForUpdates, diffThreshold);
				if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
				{
					FileHelper.createFolder(Paths.get("unit test files", failedMapsFolderName).toString());
					String failedMapName = FilenameUtils.getBaseName(settingsFileName) + " updated full map for incremental draw test";
					ImageHelper.getInstance().write(fullMapForUpdates, MapTestUtil.getFailedMapFilePath(failedMapName, failedMapsFolderName));
					String fullMapName = FilenameUtils.getBaseName(settingsFileName) + " original full map for incremental draw test";
					ImageHelper.getInstance().write(fullMap, MapTestUtil.getFailedMapFilePath(fullMapName, failedMapsFolderName));
					createImageDiffIfImagesAreSameSize(fullMap, fullMapForUpdates, failedMapName, diffThreshold);
					fail("Incremental update did not match expected image: " + comparisonErrorMessage);
				}
			}

			{
				final int numberToTest = 50;
				Image fullMapForUpdates = fullMap.deepCopy();
				int textNumber = 0;
				for (MapText text : settings.edits.text)
				{
					textNumber++;
					if (textNumber > numberToTest)
					{
						break;
					}

					// System.out.println("Running incremental text drawing test number " + textNumber);

					IntRectangle changedBounds = mapCreator.incrementalUpdateText(settings, mapParts, fullMapForUpdates, Arrays.asList(text));
					changedBounds = changedBounds.findIntersection(new IntRectangle(new IntPoint(0, 0), mapParts.background.getMapBoundsIncludingBorder().toIntDimension()));

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

						String expectedSnippetName = FilenameUtils.getBaseName(settingsFileName) + " icon " + textNumber + " expected.png";
						Path expectedPath = Paths.get("unit test files", failedMapsFolderName, expectedSnippetName);
						ImageHelper.getInstance().write(expectedSnippet, expectedPath.toString());

						String failedSnippetName = FilenameUtils.getBaseName(settingsFileName) + " icon " + textNumber + " failed.png";
						Path failedPath = Paths.get("unit test files", failedMapsFolderName, failedSnippetName);
						ImageHelper.getInstance().write(actualSnippet, failedPath.toString());

						createImageDiffIfImagesAreSameSize(expectedSnippet, actualSnippet, failedSnippetName, diffThreshold);
						failCount++;
					}
				}

				String comparisonErrorMessage = MapTestUtil.checkIfImagesEqual(fullMap, fullMapForUpdates, diffThreshold);
				if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
				{
					FileHelper.createFolder(Paths.get("unit test files", failedMapsFolderName).toString());
					String failedMapName = FilenameUtils.getBaseName(settingsFileName) + " updated full map for incremental draw test";
					ImageHelper.getInstance().write(fullMapForUpdates, MapTestUtil.getFailedMapFilePath(failedMapName, failedMapsFolderName));
					String fullMapName = FilenameUtils.getBaseName(settingsFileName) + " original full map for incremental draw test";
					ImageHelper.getInstance().write(fullMap, MapTestUtil.getFailedMapFilePath(fullMapName, failedMapsFolderName));
					createImageDiffIfImagesAreSameSize(fullMap, fullMapForUpdates, failedMapName, diffThreshold);
					fail("Incremental update did not match expected image: " + comparisonErrorMessage);
				}
			}

			if (failCount > 0)
			{
				fail(failCount + " incremental update tests failed.");
			}

		}
		finally
		{
			MapCreator.overrideMemoryMode(null);
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
			protected void onFinishedDrawingFull(Image map, boolean anotherDrawIsQueued, int borderWidthAsDrawn, List<String> warningMessages)
			{
				mapTuple.set(map);
				doneTuple.set(true);
			}

			protected void onFinishedDrawingIncremental(boolean anotherDrawIsQueued, int borderWidthAsDrawn, IntRectangle incrementalChangeArea, List<String> warningMessages)
			{
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

		String comparisonErrorMessage = MapTestUtil.checkIfImagesEqual(drawnWithoutEdits, drawnWithEdits);
		if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
		{
			FileHelper.createFolder(Paths.get("unit test files", failedMapsFolderName).toString());
			drawnWithoutEdits.write(Paths.get("unit test files", failedMapsFolderName, "compareWithAndWithoutEdits_NoEdits.png").toString());
			drawnWithEdits.write(Paths.get("unit test files", failedMapsFolderName, "compareWithAndWithoutEdits_WithEdits.png").toString());
			createImageDiffIfImagesAreSameSize(drawnWithoutEdits, drawnWithEdits, "noOceanOrCoastEffects");
			fail(comparisonErrorMessage);
		}
	}

	@Test
	public void newRandomMapTest1() throws IOException
	{
		generateRandomAndCompare(1);
	}

	@Test
	public void newRandomMapTest2()
	{
		generateRandomAndCompare(4);
	}

	@Test
	public void newRandomHeightMapTest1()
	{
		generateRandomHeightmapAndCompare(1);
	}

	@Test
	public void newRandomHeightMapTest2()
	{
		generateRandomHeightmapAndCompare(4);
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
	public void jsonSaveTestNortFile(@TempDir Path tempDir) throws IOException
	{
		runJSonSaveTest("allTypesOfEdits.nort", tempDir);
	}

	@Test
	public void jsonSaveTestPropertiesConversion(@TempDir Path tempDir) throws IOException
	{
		runJSonSaveTest("propertiesConversion_allTypesOfEdits.properties", tempDir);
	}

	/**
	 * Tests that a map can be saved and reloaded and produce the same json.
	 */
	private void runJSonSaveTest(String settingsFileName, Path tempDir) throws IOException
	{
		String settingsPath = Paths.get("unit test files", "map settings", settingsFileName).toString();
		MapSettings settings = new MapSettings(settingsPath);

		Path tempFile = tempDir.resolve(FilenameUtils.getBaseName(settingsFileName) + " copy.nort");
		settings.writeToFile(tempFile.toString());
		MapSettings actual = new MapSettings(tempFile.toString());
		assertEquals(settings, actual);
	}

	@Test
	public void iconReplacements()
	{
		// Warning message strings are locale-dependent, so only check them in English.
		org.junit.jupiter.api.Assumptions.assumeTrue(nortantis.swing.translation.Translation.getEffectiveLocale().getLanguage().equals("en"),
				"Skipping warning text verification in non-English locale");

		// Clear the custom images path to force icons to be replaced with images from the installed art pack.
		List<String> warnings = MapTestUtil.generateAndCompare("iconReplacements.nort", (settings -> settings.customImagesPath = null), expectedMapsFolderName, failedMapsFolderName, 0)
				.getWarningMessages();

		Set<String> expectedWarnings = new TreeSet<>();
		expectedWarnings.add("Unable to find the art pack 'custom' to load the mountain image group 'jagged'. The art pack 'nortantis' will be used instead.");
		expectedWarnings.add("Unable to find the mountain image group 'jagged' in art pack 'nortantis'. The group 'round' in that art pack will be used instead.");
		expectedWarnings.add("Unable to find the art pack 'custom' to load the hill image group 'jagged'. The art pack 'nortantis' will be used instead.");
		expectedWarnings.add("Unable to find the hill image group 'jagged' in art pack 'nortantis'. The group 'round' in that art pack will be used instead.");
		expectedWarnings
				.add("Unable to find the art pack 'custom' to load the sand image group 'dunes'. The art pack 'nortantis' will be used instead because it has the same image group folder name.");
		expectedWarnings
				.add("Unable to find the art pack 'custom' to load the hill image group 'sharp'. The art pack 'nortantis' will be used instead because it has the same image group folder name.");
		expectedWarnings.add("Unable to find the art pack 'custom' to load the tree image group 'generated deciduous 6'. The art pack 'nortantis' will be used instead.");
		expectedWarnings.add("Unable to find the tree image group 'generated deciduous 6' in art pack 'nortantis'. The group 'original pine' in that art pack will be used instead.");
		expectedWarnings
				.add("Unable to find the art pack 'custom' to load the tree image group 'pine'. The art pack 'nortantis' will be used instead because it has the same image group folder name.");
		expectedWarnings
				.add("Unable to find the art pack 'custom' to load the mountain image group 'sharp'. The art pack 'nortantis' will be used instead because it has the same image group folder name.");
		expectedWarnings.add("Unable to find the art pack 'custom' to load the icon 'ship 6' from decoration image group 'boats'. The art pack 'nortantis' will be used instead.");
		expectedWarnings.add("Unable to find the decoration image group 'boats' in art pack 'custom'. The group 'other' in art pack 'nortantis' will be used instead.");
		expectedWarnings.add("Unable to find the decoration icon 'ship 6' in art pack 'custom', group 'boats'. The icon 'anchor' in art pack 'nortantis', group 'other', will be used instead.");
		expectedWarnings.add("Unable to find the art pack 'custom' to load the icon 'small house 1' from city image group 'other'. The art pack 'nortantis' will be used instead.");
		expectedWarnings.add("Unable to find the city image group 'other' in art pack 'custom'. The group 'flat' in art pack 'nortantis' will be used instead.");
		expectedWarnings
				.add("Unable to find the city icon 'small house 1' in art pack 'custom', group 'other'. The icon 'town on a hill' in art pack 'nortantis', group 'flat', will be used instead.");
		expectedWarnings.add(
				"Unable to find the art pack 'custom' to load the icon 'town' from city image group 'middle ages'. The art pack 'nortantis' will be used instead because it has the same image group folder and image name.");
		expectedWarnings.add("Unable to find the art pack 'custom' to load the icon 'compass 1' from decoration image group 'compasses'. The art pack 'nortantis' will be used instead.");
		expectedWarnings.add("Unable to find the decoration image group 'compasses' in art pack 'custom'. The group 'compass roses' in art pack 'nortantis' will be used instead.");
		expectedWarnings.add(
				"Unable to find the decoration icon 'compass 1' in art pack 'custom', group 'compasses'. The icon 'simple compass rose' in art pack 'nortantis', group 'compass roses', will be used instead.");
		expectedWarnings.add("Unable to find the art pack 'custom' to load the icon 'simple_ship' from decoration image group 'boats'. The art pack 'nortantis' will be used instead.");
		expectedWarnings.add("Unable to find the decoration icon 'simple_ship' in art pack 'custom', group 'boats'. The icon 'anchor' in art pack 'nortantis', group 'other', will be used instead.");

		for (String warning : warnings)
		{
			if (!expectedWarnings.contains(warning))
			{
				fail("Unexpected warning hit: '" + warning + "'");
			}
		}

		for (String expectedWarning : expectedWarnings)
		{
			if (!warnings.contains(expectedWarning))
			{
				fail("Expected warning not hit:: '" + expectedWarning + "'");
			}
		}

		Set<String> extra = new TreeSet<>(expectedWarnings);
		extra.removeAll(warnings);
		if (extra.size() > 0)
		{
			fail("Extra warnings found: " + extra);
		}

		assertEquals(22, warnings.size());
	}

	@Test
	public void iconReplacementsWithMissingIconTypes()
	{
		// Warning message strings are locale-dependent, so only check them in English.
		org.junit.jupiter.api.Assumptions.assumeTrue(nortantis.swing.translation.Translation.getEffectiveLocale().getLanguage().equals("en"),
				"Skipping warning text verification in non-English locale");

		List<String> warnings = MapTestUtil.generateAndCompare("iconReplacementsWithMissingIconTypes.nort", (settings) ->
		{
			settings.customImagesPath = Paths.get("unit test files", "map settings", "empty custom images").toAbsolutePath().toString();
		}, expectedMapsFolderName, failedMapsFolderName, 0).getWarningMessages();

		Set<String> expectedWarnings = new TreeSet<>();
		expectedWarnings.add(
				"The art pack 'custom' no longer has tree images, so it does not have the tree image group 'pine'. The art pack 'nortantis' will be used instead because it has the same image group folder name. These trees are not visible because they were drawn at low density, but may become visible if you change the tree height in the Effects tab.");
		expectedWarnings.add(
				"The art pack 'custom' no longer has tree images, so it does not have the tree image group 'generated deciduous 6'. The art pack 'nortantis' will be used instead because it has tree images. These trees are not visible because they were drawn at low density, but may become visible if you change the tree height in the Effects tab.");
		expectedWarnings.add(
				"Unable to find the tree image group 'generated deciduous 6' in art pack 'nortantis'. The group 'original pine' in that art pack will be used instead. These trees are not visible because they were drawn at low density, but may become visible if you change the tree height in the Effects tab.");
		expectedWarnings
				.add("The art pack 'custom' no longer has hill images, so it does not have the hill image group 'jagged'. The art pack 'nortantis' will be used instead because it has hill images.");
		expectedWarnings.add("Unable to find the hill image group 'jagged' in art pack 'nortantis'. The group 'round' in that art pack will be used instead.");
		expectedWarnings.add(
				"The art pack 'custom' no longer has mountain images, so it does not have the mountain image group 'jagged'. The art pack 'nortantis' will be used instead because it has mountain images.");
		expectedWarnings.add("Unable to find the mountain image group 'jagged' in art pack 'nortantis'. The group 'round' in that art pack will be used instead.");
		expectedWarnings.add(
				"The art pack 'custom' no longer has sand images, so it does not have the sand image group 'dunes'. The art pack 'nortantis' will be used instead because it has the same image group folder name.");
		expectedWarnings.add(
				"The art pack 'custom' no longer has hill images, so it does not have the hill image group 'sharp'. The art pack 'nortantis' will be used instead because it has the same image group folder name.");
		expectedWarnings.add(
				"The art pack 'custom' no longer has city images, so it does not have the icon 'town' from city image group 'middle ages'. The art pack 'nortantis' will be used instead because it has the same image group folder and image name.");
		expectedWarnings.add(
				"The art pack 'custom' no longer has tree images, so it does not have the tree image group 'generated deciduous 6'. The art pack 'nortantis' will be used instead because it has tree images.");
		expectedWarnings.add("Unable to find the tree image group 'generated deciduous 6' in art pack 'nortantis'. The group 'original pine' in that art pack will be used instead.");
		expectedWarnings.add(
				"The art pack 'custom' no longer has tree images, so it does not have the tree image group 'pine'. The art pack 'nortantis' will be used instead because it has the same image group folder name.");
		expectedWarnings.add(
				"The art pack 'custom' no longer has mountain images, so it does not have the mountain image group 'sharp'. The art pack 'nortantis' will be used instead because it has the same image group folder name.");
		expectedWarnings.add(
				"The art pack 'custom' no longer has decoration images, so it does not have the icon 'compass 1' from decoration image group 'compasses'. The art pack 'nortantis' will be used instead because it has decoration images.");
		expectedWarnings.add("Unable to find the decoration image group 'compasses' in art pack 'custom'. The group 'compass roses' in art pack 'nortantis' will be used instead.");
		expectedWarnings.add(
				"Unable to find the decoration icon 'compass 1' in art pack 'custom', group 'compasses'. The icon 'simple compass rose' in art pack 'nortantis', group 'compass roses', will be used instead.");
		expectedWarnings.add(
				"The art pack 'custom' no longer has decoration images, so it does not have the icon 'ship 6' from decoration image group 'boats'. The art pack 'nortantis' will be used instead because it has decoration images.");
		expectedWarnings.add("Unable to find the decoration image group 'boats' in art pack 'custom'. The group 'other' in art pack 'nortantis' will be used instead.");
		expectedWarnings.add("Unable to find the decoration icon 'ship 6' in art pack 'custom', group 'boats'. The icon 'anchor' in art pack 'nortantis', group 'other', will be used instead.");
		expectedWarnings.add(
				"The art pack 'custom' no longer has city images, so it does not have the icon 'small house 1' from city image group 'other'. The art pack 'nortantis' will be used instead because it has city images.");
		expectedWarnings.add("Unable to find the city image group 'other' in art pack 'custom'. The group 'flat' in art pack 'nortantis' will be used instead.");
		expectedWarnings
				.add("Unable to find the city icon 'small house 1' in art pack 'custom', group 'other'. The icon 'town on a hill' in art pack 'nortantis', group 'flat', will be used instead.");
		expectedWarnings.add(
				"The art pack 'custom' no longer has decoration images, so it does not have the icon 'compass 6' from decoration image group 'compasses'. The art pack 'nortantis' will be used instead because it has decoration images.");
		expectedWarnings.add(
				"Unable to find the decoration icon 'compass 6' in art pack 'custom', group 'compasses'. The icon 'dragon compass rose' in art pack 'nortantis', group 'compass roses', will be used instead.");

		for (String warning : warnings)
		{
			if (!expectedWarnings.contains(warning))
			{
				fail("Unexpected warning hit: '" + warning + "'");
			}
		}

		for (String expectedWarning : expectedWarnings)
		{
			if (!warnings.contains(expectedWarning))
			{
				fail("Expected warning not hit:: '" + expectedWarning + "'");
			}
		}

		Set<String> extra = new TreeSet<>(expectedWarnings);
		extra.removeAll(warnings);
		if (extra.size() > 0)
		{
			fail("Extra warnings found: " + extra);
		}

		assertEquals(25, warnings.size());
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

	@Test
	public void incrementalUpdate_addRandomIcons_simpleSmallWorld()
	{
		String settingsFileName = "simpleSmallWorld.nort";
		String settingsPath = Paths.get("unit test files", "map settings", settingsFileName).toString();
		MapSettings settings = new MapSettings(settingsPath);
		settings.resolution = 0.5;
		// Threshold of 15 to accommodate minor AWT rendering variance at effects padding boundaries.
		final int diffThreshold = 15;

		// Create an initial map to establish the graph so we can pick land
		// centers to place icons on.
		MapCreator mapCreator = new MapCreator();
		MapParts setupParts = new MapParts();
		mapCreator.createMap(settings, null, setupParts);

		// Pick random land centers to place icons on.
		Random rand = new Random(42);
		List<Center> landCenters = new ArrayList<>();
		for (Center c : setupParts.graph.centers)
		{
			if (!c.isWater && !c.isCoast)
			{
				landCenters.add(c);
			}
		}
		assertTrue(landCenters.size() >= 10, "Need at least 10 land centers for the test");

		// Create new icons at random land positions and add them to edits.
		int iconCount = 10;
		List<FreeIcon> addedIcons = new ArrayList<>();
		for (int i = 0; i < iconCount; i++)
		{
			Center center = landCenters.get(rand.nextInt(landCenters.size()));
			nortantis.geom.Point loc = new nortantis.geom.Point(center.loc.x * settings.resolution, center.loc.y * settings.resolution);

			FreeIcon icon;
			if (i % 3 == 0)
			{
				icon = new FreeIcon(settings.resolution, loc, 1.0, IconType.mountains, Assets.installedArtPack, "round", rand.nextInt(100), center.index, nortantis.platform.Color.create(0, 0, 0),
						new HSBColor(0, 0, 100, 0), false, false);
			}
			else if (i % 3 == 1)
			{
				icon = new FreeIcon(settings.resolution, loc, 1.0, IconType.hills, Assets.installedArtPack, "round", rand.nextInt(100), center.index, nortantis.platform.Color.create(0, 0, 0),
						new HSBColor(0, 0, 100, 0), false, false);
			}
			else
			{
				icon = new FreeIcon(settings.resolution, loc, 1.0, IconType.cities, Assets.installedArtPack, "flat", "town", center.index, nortantis.platform.Color.create(0, 0, 0),
						new HSBColor(0, 0, 100, 0), false, false);
			}

			settings.edits.freeIcons.addOrReplace(icon);
			addedIcons.add(icon);
		}

		// Create the full map with all icons present (the expected result).
		// Use fresh MapParts so incremental updates share the same state.
		MapParts mapParts = new MapParts();
		Image fullMap = mapCreator.createMap(settings, null, mapParts);

		// Create a deep copy and incrementally redraw each added icon's area.
		Image fullMapForUpdates = fullMap.deepCopy();
		int failCount = 0;
		for (int i = 0; i < addedIcons.size(); i++)
		{
			FreeIcon icon = addedIcons.get(i);
			IntRectangle changedBounds = mapCreator.incrementalUpdateIcons(settings, mapParts, fullMapForUpdates, Arrays.asList(icon));

			assertTrue(changedBounds != null, "Incremental update should produce bounds for icon " + i);
			assertTrue(changedBounds.width > 0);
			assertTrue(changedBounds.height > 0);

			Image expectedSnippet = fullMap.getSubImage(changedBounds);
			Image actualSnippet = fullMapForUpdates.getSubImage(changedBounds);

			String comparisonErrorMessage = MapTestUtil.checkIfImagesEqual(expectedSnippet, actualSnippet, diffThreshold);
			if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
			{
				FileHelper.createFolder(Paths.get("unit test files", failedMapsFolderName).toString());

				String expectedSnippetName = FilenameUtils.getBaseName(settingsFileName) + " added icon " + i + " expected.png";
				ImageHelper.getInstance().write(expectedSnippet, Paths.get("unit test files", failedMapsFolderName, expectedSnippetName).toString());

				String failedSnippetName = FilenameUtils.getBaseName(settingsFileName) + " added icon " + i + " failed.png";
				ImageHelper.getInstance().write(actualSnippet, Paths.get("unit test files", failedMapsFolderName, failedSnippetName).toString());

				failCount++;
			}
		}

		String comparisonErrorMessage = MapTestUtil.checkIfImagesEqual(fullMap, fullMapForUpdates, diffThreshold);
		if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
		{
			FileHelper.createFolder(Paths.get("unit test files", failedMapsFolderName).toString());
			String failedMapName = FilenameUtils.getBaseName(settingsFileName) + " added icons full map";
			ImageHelper.getInstance().write(fullMapForUpdates, MapTestUtil.getFailedMapFilePath(failedMapName, failedMapsFolderName));
			String fullMapName = FilenameUtils.getBaseName(settingsFileName) + " added icons expected full map";
			ImageHelper.getInstance().write(fullMap, MapTestUtil.getFailedMapFilePath(fullMapName, failedMapsFolderName));
			fail("Full map after incremental icon additions did not match expected: " + comparisonErrorMessage);
		}

		if (failCount > 0)
		{
			fail(failCount + " incremental icon addition tests failed.");
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

	private MapSettings generateRandomAndCompare(long seed)
	{
		return MapTestUtil.generateRandomAndCompare(seed, expectedMapsFolderName, failedMapsFolderName, 0);
	}

	private void generateRandomHeightmapAndCompare(long seed)
	{
		MapTestUtil.generateRandomHeightmapAndCompare(seed, expectedMapsFolderName, failedMapsFolderName);
	}

	private void generateAndCompare(String settingsFileName)
	{
		MapTestUtil.generateAndCompare(settingsFileName, null, expectedMapsFolderName, failedMapsFolderName, 0);
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
