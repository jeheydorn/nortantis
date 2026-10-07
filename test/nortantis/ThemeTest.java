package nortantis;

import nortantis.editor.RegionEdit;
import nortantis.platform.Color;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;
import nortantis.util.Assets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
		gen.allowedBorderNames.add("dashes");
		gen.allowedLineStyles.add(MapSettings.LineStyle.Splines);
		gen.oceanShadingWithWavesProbability = 0.25;
		gen.allowFractalBackground = false;

		ThemeGenerationSettings reloaded = ThemeGenerationSettings.fromJson(gen.toJson());
		assertEquals(gen, reloaded);
		assertEquals(gen.toJson(), reloaded.copy().toJson());
	}

	@Test
	public void theInstalledArtPackHasAThemeThatGeneratesAMap()
	{
		List<ThemeCatalog.Entry> themes = ThemeCatalog.listThemesForArtPack(Assets.installedArtPack, null);
		assertFalse(themes.isEmpty(), "New random maps need a theme in the installed art pack to fall back to.");
		ThemeCatalog.load(themes.get(0));

		MapSettings settings = SettingsGenerator.generate(new Random(5), Assets.installedArtPack, null);
		assertEquals(MapSettings.currentVersion, settings.version);
		assertNotNull(settings.borderResource);
		assertNotNull(settings.backgroundTextureResource);
		assertNotNull(settings.getDefaultTextStyle(TextType.Road));
	}

	@Test
	public void randomizingAThemeRepeatedlyStaysNearTheBase()
	{
		MapSettings settings = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		settings.themeGeneration = ThemeGenerationSettings.createDefault();
		MapSettings base = settings.deepCopy();
		ThemeGenerationSettings gen = settings.themeGeneration;
		Random rand = new Random(11);
		for (int i = 0; i < 50; i++)
		{
			SettingsGenerator.randomizeTheme(settings, base, settings.artPack, rand);
		}
		assertTrue(Math.abs(settings.grungeWidth - base.grungeWidth) <= gen.grungeWidthVariation, "Randomizing must vary around the base, not drift from it.");
		assertTrue(Math.abs(settings.coastShadingLevel - base.coastShadingLevel) <= gen.coastShadingLevelVariation);
	}

	@Test
	public void randomizingGeneratesRegionColorsAroundTheRegionBaseColor()
	{
		MapSettings settings = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		settings.drawRegionColors = true;
		settings.regionBaseColor = Color.create(180, 140, 90);
		settings.hueRange = 30;
		settings.saturationRange = 20;
		settings.brightnessRange = 20;
		settings.edits.regionEdits.put(1, new RegionEdit(1, Color.create(10, 10, 200)));
		settings.edits.regionEdits.put(2, new RegionEdit(2, Color.create(10, 200, 10)));
		settings.themeGeneration = ThemeGenerationSettings.createDefault();
		MapSettings base = settings.deepCopy();

		SettingsGenerator.randomizeTheme(settings, base, settings.artPack, new Random(5));

		assertEquals(base.regionBaseColor, settings.regionBaseColor);
		float baseHue = base.regionBaseColor.getHSB()[0] * 360f;
		for (int regionId : new int[] { 1, 2 })
		{
			Color after = settings.edits.regionEdits.get(regionId).color;
			assertNotEquals(base.edits.regionEdits.get(regionId).color, after, "Region colors are kept in the edits, so they must be generated again.");
			float hueDifference = Math.abs(after.getHSB()[0] * 360f - baseHue);
			hueDifference = Math.min(hueDifference, 360f - hueDifference);
			assertTrue(hueDifference <= settings.hueRange / 2f + 1f, "A region's hue stays within the hue range around the base: " + hueDifference);
		}
		assertEquals(Color.create(10, 10, 200), base.edits.regionEdits.get(1).color, "The base is not changed.");
	}

	@Test
	public void onlyTheOceanAndLandOrRegionColorsVary()
	{
		for (boolean drawRegionColors : new boolean[] { true, false })
		{
			MapSettings settings = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
			settings.drawRegionColors = drawRegionColors;
			settings.themeGeneration = ThemeGenerationSettings.createDefault();
			settings.themeGeneration.oceanHueVariation = 90;
			settings.themeGeneration.landHueVariation = 90;
			MapSettings before = settings.deepCopyExceptEdits();

			SettingsGenerator.applyThemeRandomness(settings, before, settings.themeGeneration, settings.artPack, new Random(11));

			assertNotEquals(before.oceanColor, settings.oceanColor);
			assertNotEquals(before.oceanWavesColor, settings.oceanWavesColor, "Ocean waves move with the ocean.");
			assertNotEquals(before.oceanShadingColor, settings.oceanShadingColor, "Ocean shading moves with the ocean.");
			assertEquals(before.regionBaseColor, settings.regionBaseColor, "The region base color is the theme's.");
			if (drawRegionColors)
			{
				assertEquals(before.landColor, settings.landColor, "With region colors, the land color is the theme's.");
				assertEquals(before.borderColor, settings.borderColor, "With region colors, the border color is the theme's.");
			}
			else
			{
				assertNotEquals(before.landColor, settings.landColor);
				assertNotEquals(before.borderColor, settings.borderColor, "The border moves with the land.");
			}
			assertEquals(drawRegionColors, settings.drawRegionColors, "The land coloring method is the theme's.");
			assertEquals(before.riverColor, settings.riverColor);
			assertEquals(before.frayedBorderColor, settings.frayedBorderColor);
			assertEquals(before.grungeColor, settings.grungeColor);
		}
	}

	@Test
	public void fractalBackgroundsAreOneChoiceAmongTheTexturesWhenAllowed()
	{
		MapSettings settings = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		settings.themeGeneration = ThemeGenerationSettings.createDefault();
		int textureCount = Assets.listBackgroundTexturesForArtPack(settings.artPack, settings.customImagesPath).size();
		assertTrue(textureCount > 0);

		int fractalCount = 0;
		final int tries = 400;
		Random rand = new Random(13);
		for (int i = 0; i < tries; i++)
		{
			SettingsGenerator.applyThemeRandomness(settings, settings, settings.themeGeneration, settings.artPack, rand);
			assertNotEquals(settings.generateBackground, settings.generateBackgroundFromTexture);
			fractalCount += settings.generateBackground ? 1 : 0;
		}
		double expected = tries / (double) (textureCount + 1);
		assertTrue(Math.abs(fractalCount - expected) < expected * 0.5, "A fractal background is as likely as each texture: " + fractalCount + " of " + tries);

		settings.themeGeneration.allowFractalBackground = false;
		for (int i = 0; i < 50; i++)
		{
			SettingsGenerator.applyThemeRandomness(settings, settings, settings.themeGeneration, settings.artPack, rand);
			assertFalse(settings.generateBackground);
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
	public void copyingAThemeKeepsTheMapsOwnText()
	{
		MapSettings source = new MapSettings("unit test files/map settings/allTypesOfEdits.nort");
		source.themeGeneration = ThemeGenerationSettings.createDefault();
		source.themeGeneration.landHueVariation = 3;
		source.getDefaultTextLayout(TextType.Region).spacing = 9;

		MapSettings target = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		int targetTextCount = target.edits.text.size();
		target.copyThemeFrom(source);
		assertEquals(source.landColor, target.landColor);
		assertEquals(source.coastShadingAlpha, target.coastShadingAlpha);
		assertEquals(source.textStyleDefaults, target.textStyleDefaults);
		assertEquals(source.textLayoutDefaults, target.textLayoutDefaults);
		assertEquals(source.themeGeneration, target.themeGeneration);
		assertEquals(targetTextCount, target.edits.text.size(), "Copying a theme keeps the map's own text.");
	}

	@Test
	public void artPackThemesAreMapFiles()
	{
		assertTrue(ThemeCatalog.isThemeFile(Path.of("themes", "Parchment" + MapSettings.fileExtensionWithDot)));
		assertFalse(ThemeCatalog.isThemeFile(Path.of("themes", "Parchment.png")));
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
