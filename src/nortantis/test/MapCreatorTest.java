package nortantis.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import nortantis.MapCreator;
import nortantis.MapSettings;
import nortantis.util.Helper;
import nortantis.util.ImageHelper;
import nortantis.util.Logger;

public class MapCreatorTest
{

	@BeforeClass
	public static void setUpBeforeClass() throws Exception
	{
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
				BufferedImage map = mapCreator.createMap(settings, null, null);
				ImageHelper.write(map, expectedMapFilePath);
			}

		}
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
	public void allTypesOfEdits()
	{
		generateAndCompare("allTypesOfEdits.nort");
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
	public void iconsAllowedToDrawOverCoastlines()
	{
		generateAndCompare("iconsAllowedToDrawOverCoastlines.nort");
	}

	private static String getExpectedMapFilePath(String settingsFileName)
	{
		return Paths.get("unit test files", "expected maps", FilenameUtils.getBaseName(settingsFileName) + ".png").toString();
	}

	private static String getActualMapFilePath(String settingsFileName)
	{
		return Paths.get("unit test files", "failed maps", FilenameUtils.getBaseName(settingsFileName) + ".png").toString();
	}

	private static String getDiffFilePath(String settingsFileName)
	{
		return Paths.get("unit test files", "failed maps", FilenameUtils.getBaseName(settingsFileName) + " - diff.png").toString();
	}

	private void generateAndCompare(String settingsFileName)
	{
		BufferedImage expected = ImageHelper.read(getExpectedMapFilePath(settingsFileName));
		String settingsPath = Paths.get("unit test files", "map settings", settingsFileName).toString();
		MapSettings settings = new MapSettings(settingsPath);
		MapCreator mapCreator = new MapCreator();
		Logger.println("Creating map from '" + settingsPath + "'");
		BufferedImage actual;
		actual = mapCreator.createMap(settings, null, null);

		// Test deep copy after creating the map because MapCreator sets some fields during map creation, so it's a
		// more complete test that way.
		testDeepCopy(settings);

		String comparisonErrorMessage = checkIfImagesEqual(expected, actual);
		if (comparisonErrorMessage != null && !comparisonErrorMessage.isEmpty())
		{
			Helper.createFolder(Paths.get("unit test files", "failed maps").toString());
			ImageHelper.write(actual, getActualMapFilePath(settingsFileName));
			createImageDiffIfImagesAreSameSize(expected, actual, settingsFileName);
			fail(comparisonErrorMessage);
		}
	}

	private void testDeepCopy(MapSettings settings)
	{
		MapSettings copy = settings.deepCopy();
		assertEquals(settings, copy);
	}

	private String checkIfImagesEqual(BufferedImage image1, BufferedImage image2)
	{
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

	private void createImageDiffIfImagesAreSameSize(BufferedImage image1, BufferedImage image2, String settingsFileName)
	{
		if (image1.getWidth() == image2.getWidth() && image1.getHeight() == image2.getHeight())
		{
			BufferedImage diff = new BufferedImage(image1.getWidth(), image1.getHeight(), BufferedImage.TYPE_INT_RGB);
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
