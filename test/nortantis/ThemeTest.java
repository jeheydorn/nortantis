package nortantis;

import nortantis.platform.Color;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;
import nortantis.util.Assets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class ThemeTest
{
	@BeforeAll
	public static void setUp()
	{
		PlatformFactory.setInstance(new AwtFactory());
		nortantis.swing.translation.Translation.initialize();
		Assets.disableAddedArtPacksForUnitTests();
	}

	@Test
	public void themeGenerationSettingsRoundTripThroughJson()
	{
		ThemeGenerationSettings gen = ThemeGenerationSettings.createDefault();
		gen.artPack = "Some Art Pack";
		gen.oceanHueVariation = 3;
		gen.landBrightnessVariation = 7;
		gen.baseLandColor = Color.create(1, 2, 3, 4);
		gen.baseGrungeWidth = 123;
		gen.allowedBorderNames.add("dashes");
		gen.allowedLineStyles.add(MapSettings.LineStyle.Splines);
		gen.oceanShadingWithWavesProbability = 0.25;

		ThemeGenerationSettings reloaded = ThemeGenerationSettings.fromJson(gen.toJson());
		assertEquals(gen, reloaded);
		assertEquals(gen.toJson(), reloaded.copy().toJson());
	}

	@Test
	public void theInstalledArtPackHasAThemeThatGeneratesAMap()
	{
		List<ThemeCatalog.Entry> themes = ThemeCatalog.listThemesForArtPack(Assets.installedArtPack, null);
		assertFalse(themes.isEmpty(), "New random maps need a theme in the installed art pack to fall back to.");
		MapSettings theme = ThemeCatalog.load(themes.get(0));
		assertNotNull(theme.themeGeneration);
		assertNotNull(theme.themeGeneration.baseLandColor);
		assertTrue(theme.edits.text.isEmpty());

		MapSettings settings = SettingsGenerator.generate(new Random(5), Assets.installedArtPack, null);
		assertEquals(MapSettings.currentVersion, settings.version);
		assertNotNull(settings.borderResource);
		assertNotNull(settings.backgroundTextureResource);
		assertNotNull(settings.getDefaultTextStyle(TextType.Road));
	}

	@Test
	public void randomizingAThemeRepeatedlyStaysNearItsBaseValues()
	{
		MapSettings settings = SettingsGenerator.generate(new Random(7), Assets.installedArtPack, null);
		ThemeGenerationSettings gen = settings.themeGeneration;
		Random rand = new Random(11);
		for (int i = 0; i < 50; i++)
		{
			SettingsGenerator.randomizeTheme(settings, rand);
		}
		assertTrue(Math.abs(settings.grungeWidth - gen.baseGrungeWidth) <= gen.grungeWidthVariation, "Re-rolling must vary around the base, not drift from it.");
		assertTrue(Math.abs(settings.coastShadingLevel - gen.baseCoastShadingLevel) <= gen.coastShadingLevelVariation);
	}

	@Test
	public void randomizingAMapWithNoRulesRecordsItsLookFirst()
	{
		MapSettings settings = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		settings.themeGeneration = null;
		int grungeWidth = settings.grungeWidth;
		SettingsGenerator.randomizeTheme(settings, new Random(3));
		assertNotNull(settings.themeGeneration);
		assertEquals(grungeWidth, settings.themeGeneration.baseGrungeWidth);
	}

	@Test
	public void onlyTheOceanAndLandOrRegionColorsVary()
	{
		for (boolean drawRegionColors : new boolean[] { true, false })
		{
			MapSettings settings = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
			settings.drawRegionColors = drawRegionColors;
			settings.themeGeneration = ThemeGenerationSettings.createDefault();
			settings.themeGeneration.setBaseValuesFrom(settings);
			settings.themeGeneration.oceanHueVariation = 90;
			settings.themeGeneration.landHueVariation = 90;
			MapSettings before = settings.deepCopyExceptEdits();

			SettingsGenerator.applyThemeRandomness(settings, settings.themeGeneration, new Random(11));

			assertNotEquals(before.oceanColor, settings.oceanColor);
			assertNotEquals(before.oceanWavesColor, settings.oceanWavesColor, "Ocean waves move with the ocean.");
			assertNotEquals(before.oceanShadingColor, settings.oceanShadingColor, "Ocean shading moves with the ocean.");
			assertNotEquals(before.borderColor, settings.borderColor, "The border moves with the land or regions.");
			if (drawRegionColors)
			{
				assertNotEquals(before.regionBaseColor, settings.regionBaseColor);
				assertEquals(before.landColor, settings.landColor, "With region colors, the land color is the theme's.");
			}
			else
			{
				assertNotEquals(before.landColor, settings.landColor);
				assertEquals(before.regionBaseColor, settings.regionBaseColor, "Without region colors, the region base color is the theme's.");
			}
			assertEquals(drawRegionColors, settings.drawRegionColors, "The land coloring method is the theme's.");
			assertEquals(before.riverColor, settings.riverColor);
			assertEquals(before.frayedBorderColor, settings.frayedBorderColor);
			assertEquals(before.grungeColor, settings.grungeColor);
		}
	}

	@Test
	public void colorOffsetsKeepAlphaAndStayInRange()
	{
		Color color = Color.create(250, 250, 250, 61);
		Color result = SettingsGenerator.applyColorOffset(color, new float[] { 10f, 0.5f, 0.5f });
		assertEquals(61, result.getAlpha());
		Color black = SettingsGenerator.applyColorOffset(Color.create(0, 0, 0, 200), new float[] { 10f, -0.05f, -0.05f });
		assertEquals(Color.create(0, 0, 0, 200), black, "Darkening black leaves it black.");
	}

	@Test
	public void themeFilesAreWrittenAndReadBack() throws Exception
	{
		MapSettings settings = new MapSettings("unit test files/map settings/allTypesOfEdits.nort");
		settings.themeGeneration = ThemeGenerationSettings.createDefault();
		settings.themeGeneration.setBaseValuesFrom(settings);
		settings.themeExportPath = "somewhere";
		settings.getDefaultTextLayout(TextType.Region).spacing = 9;

		Path temp = Files.createTempFile("theme", MapSettings.themeFileExtensionWithDot);
		try
		{
			settings.writeThemeToFile(temp.toString());
			MapSettings theme = MapSettings.readThemeFile(temp.toString());
			assertTrue(theme.edits.text.isEmpty(), "A theme has no edits.");
			assertNull(theme.themeExportPath, "A theme must not carry a path from the author's machine.");

			MapSettings target = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
			target.copyThemeFrom(theme);
			assertEquals(settings.landColor, target.landColor);
			assertEquals(settings.coastShadingAlpha, target.coastShadingAlpha);
			assertEquals(settings.textStyleDefaults, target.textStyleDefaults);
			assertEquals(settings.textLayoutDefaults, target.textLayoutDefaults);
			assertEquals(settings.themeGeneration, target.themeGeneration);
			assertNotEquals(settings.edits.text.size(), target.edits.text.size(), "Applying a theme keeps the map's own text.");
		}
		finally
		{
			Files.deleteIfExists(temp);
		}
	}

	@Test
	public void themeFilesDropABackgroundTextureFilePath() throws Exception
	{
		MapSettings settings = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		settings.backgroundTextureSource = TextureSource.File;
		settings.backgroundTextureImage = "C:/somewhere/texture.png";

		Path temp = Files.createTempFile("theme", MapSettings.themeFileExtensionWithDot);
		try
		{
			settings.writeThemeToFile(temp.toString());
			MapSettings theme = MapSettings.readThemeFile(temp.toString());
			assertNull(theme.backgroundTextureImage, "A theme must not carry a path from the author's machine.");
			assertEquals(TextureSource.Assets, theme.backgroundTextureSource);
			assertNotNull(theme.backgroundTextureResource);
		}
		finally
		{
			Files.deleteIfExists(temp);
		}
	}

	@Test
	public void oldCoastShadingColorsConvertToAnAlphaThatLooksTheSame()
	{
		Color land = Color.create(200, 160, 100);
		Color halfLand = Color.create(100, 80, 50, 87);
		assertEquals(44, MapSettings.convertCoastShadingColorToAlpha(halfLand, land, false, "3.24"));
		assertEquals(39, MapSettings.convertCoastShadingColorToAlpha(halfLand, land, true, "3.24"), "Region colors were scaled by 0.55.");
		assertEquals(87, MapSettings.convertCoastShadingColorToAlpha(Color.create(0, 0, 0, 87), land, false, "3.24"), "Black needs no change.");
	}

	@Test
	public void borderMetadataComesFromTheBordersFolder()
	{
		Assets.BorderMetadata dashes = Assets.readBorderMetadata(new NamedResource(Assets.installedArtPack, "dashes"), null);
		assertEquals(25, dashes.minWidth());
		assertEquals(75, dashes.maxWidth());
		assertFalse(dashes.allowFrayedBorder());
		assertEquals(Assets.defaultBorderMetadata, Assets.readBorderMetadata(new NamedResource(Assets.installedArtPack, "lines"), null));
	}
}
