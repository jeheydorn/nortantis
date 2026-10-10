package nortantis;

import nortantis.editor.RegionEdit;
import nortantis.platform.Color;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;
import nortantis.util.Assets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
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
		ThemeGenerationSettings gen = ThemeCatalog.getDefaultThemeRules();
		gen.oceanHueVariation = 3;
		gen.landBrightnessVariation = 7;
		gen.regionBaseSaturationVariation = 4;
		gen.borderWidthVariation = 30;
		gen.allowedLineStyles.add(MapSettings.LineStyle.Splines);
		gen.oceanShadingWithWavesProbability = 0.25;
		gen.allowedBackgroundTypes = new LinkedHashSet<>(List.of(ThemeGenerationSettings.BackgroundType.SolidColor));
		gen.allowedTitleBackgroundEffects = new LinkedHashSet<>(List.of(TextBackgroundEffect.Scroll, TextBackgroundEffect.Glow));
		gen.allowedRegionBackgroundEffects = new LinkedHashSet<>(List.of(TextBackgroundEffect.None));
		gen.shuffleTextBackgrounds = true;

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
		settings.themeGeneration = ThemeCatalog.getDefaultThemeRules();
		MapSettings base = settings.deepCopy();
		ThemeGenerationSettings gen = settings.themeGeneration;
		Random rand = new Random(11);
		for (int i = 0; i < 50; i++)
		{
			SettingsGenerator.randomizeTheme(settings, base, settings.artPack, settings.artPack, rand);
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
		settings.themeGeneration = ThemeCatalog.getDefaultThemeRules();
		MapSettings base = settings.deepCopy();

		SettingsGenerator.randomizeTheme(settings, base, settings.artPack, settings.artPack, new Random(5));

		float baseHue = settings.regionBaseColor.getHSB()[0] * 360f;
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
			settings.themeGeneration = ThemeCatalog.getDefaultThemeRules();
			settings.themeGeneration.oceanHueVariation = 90;
			settings.themeGeneration.landHueVariation = 90;
			settings.themeGeneration.regionBaseHueVariation = 90;
			MapSettings before = settings.deepCopyExceptEdits();

			SettingsGenerator.applyThemeRandomness(settings, before, settings.themeGeneration, settings.artPack, settings.artPack, new Random(11));

			assertNotEquals(before.oceanColor, settings.oceanColor);
			assertNotEquals(before.oceanWavesColor, settings.oceanWavesColor, "Ocean waves move with the ocean.");
			assertNotEquals(before.oceanShadingColor, settings.oceanShadingColor, "Ocean shading moves with the ocean.");
			if (drawRegionColors)
			{
				assertNotEquals(before.regionBaseColor, settings.regionBaseColor, "With region colors, the region base color varies.");
				assertEquals(before.landColor, settings.landColor, "With region colors, the land color is the theme's.");
				assertEquals(before.borderColor, settings.borderColor, "With region colors, the border color is the theme's.");
			}
			else
			{
				assertEquals(before.regionBaseColor, settings.regionBaseColor, "Without region colors, the region base color is the theme's.");
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
	public void theBorderWidthVariesAroundTheBaseForABorderWithoutItsOwnWidthRange()
	{
		MapSettings settings = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		settings.themeGeneration = ThemeCatalog.getDefaultThemeRules();
		settings.themeGeneration.borderWidthVariation = 20;
		MapSettings base = settings.deepCopyExceptEdits();
		Random rand = new Random(3);
		int linesCount = 0;
		boolean changed = false;
		for (int i = 0; i < 60; i++)
		{
			SettingsGenerator.applyThemeRandomness(settings, base, settings.themeGeneration, Assets.installedArtPack, Assets.installedArtPack, rand);
			if (settings.borderResource.name.equals("lines"))
			{
				linesCount++;
				assertTrue(Math.abs(settings.borderWidth - base.borderWidth) <= 20, "The border width stays within its variation: " + settings.borderWidth);
				changed |= settings.borderWidth != base.borderWidth;
			}
		}
		assertTrue(linesCount > 0);
		assertTrue(changed);

		settings.themeGeneration.borderWidthVariation = 0;
		do
		{
			SettingsGenerator.applyThemeRandomness(settings, base, settings.themeGeneration, Assets.installedArtPack, Assets.installedArtPack, rand);
		}
		while (!settings.borderResource.name.equals("lines"));
		assertEquals(base.borderWidth, settings.borderWidth, "At 0, the border width never changes.");
	}

	@Test
	public void aBordersOwnSettingsOverrideTheTheme()
	{
		MapSettings settings = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		settings.themeGeneration = ThemeCatalog.getDefaultThemeRules();
		settings.themeGeneration.borderWidthVariation = 0;
		settings.themeGeneration.drawBorderProbability = 1.0;
		settings.themeGeneration.frayedBorderProbability = 1.0;
		settings.borderWidth = 200;
		MapSettings base = settings.deepCopyExceptEdits();
		Random rand = new Random(5);
		int dashesCount = 0;
		for (int i = 0; i < 60; i++)
		{
			SettingsGenerator.applyThemeRandomness(settings, base, settings.themeGeneration, Assets.installedArtPack, Assets.installedArtPack, rand);
			if (settings.borderResource.name.equals("dashes"))
			{
				dashesCount++;
				assertTrue(settings.borderWidth >= 25 && settings.borderWidth < 75, "The width comes from the border's range: " + settings.borderWidth);
				assertFalse(settings.frayedBorder, "The border rules out frayed edges.");
			}
		}
		assertTrue(dashesCount > 0);
	}

	@Test
	public void backgroundTypesAreEquallyLikelyAmongThoseAllowed()
	{
		MapSettings settings = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		settings.themeGeneration = ThemeCatalog.getDefaultThemeRules();
		settings.themeGeneration.allowedBackgroundTypes.clear();

		int textureCount = Assets.listBackgroundTexturesForArtPack(settings.artPack, settings.customImagesPath).size();
		assertTrue(textureCount > 1);
		int[] counts = new int[3];
		final int tries = 600;
		Random rand = new Random(13);
		for (int i = 0; i < tries; i++)
		{
			SettingsGenerator.applyThemeRandomness(settings, settings, settings.themeGeneration, settings.artPack, settings.artPack, rand);
			int typeCount = (settings.generateBackground ? 1 : 0) + (settings.generateBackgroundFromTexture ? 1 : 0) + (settings.solidColorBackground ? 1 : 0);
			assertEquals(1, typeCount, "Exactly one kind of background is chosen.");
			counts[settings.generateBackground ? 0 : settings.generateBackgroundFromTexture ? 1 : 2]++;
		}
		double expectedPerChoice = tries / (double) (textureCount + 2);
		assertTrue(Math.abs(counts[0] - expectedPerChoice) < expectedPerChoice * 0.4, "Fractal is as likely as each texture: " + Arrays.toString(counts));
		assertTrue(Math.abs(counts[2] - expectedPerChoice) < expectedPerChoice * 0.4, "Solid color is as likely as each texture: " + Arrays.toString(counts));
		assertTrue(Math.abs(counts[1] - expectedPerChoice * textureCount) < expectedPerChoice * textureCount * 0.2, "Textures: " + Arrays.toString(counts));

		settings.themeGeneration.allowedBackgroundTypes.add(ThemeGenerationSettings.BackgroundType.GeneratedFromTexture);
		for (int i = 0; i < 30; i++)
		{
			SettingsGenerator.applyThemeRandomness(settings, settings, settings.themeGeneration, Assets.installedArtPack, "An art pack with no textures", rand);
			assertTrue(settings.generateBackgroundFromTexture);
			assertTrue(Assets.listArtPacksForNewRandomMaps(null).contains(settings.backgroundTextureResource.artPack),
					"An art pack with no textures uses those of the art packs new random maps use.");
		}
	}

	@Test
	public void titleAndRegionTextGetAnAllowedBackgroundEffect()
	{
		MapSettings settings = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		settings.themeGeneration = ThemeCatalog.getDefaultThemeRules();
		settings.themeGeneration.allowedTitleBackgroundEffects = new LinkedHashSet<>(List.of(TextBackgroundEffect.Scroll));
		settings.themeGeneration.allowedRegionBackgroundEffects = new LinkedHashSet<>(List.of(TextBackgroundEffect.Glow, TextBackgroundEffect.Outline));
		settings.themeGeneration.shuffleTextBackgrounds = true;
		assertTrue(settings.edits.text.stream().anyMatch(text -> text.type == TextType.Title));
		assertTrue(settings.edits.text.stream().anyMatch(text -> text.type == TextType.Region));
		settings.getDefaultTextStyle(TextType.Region).background.outlineWidth = 3;
		for (MapText text : settings.edits.text)
		{
			text.style.background.outlineWidth = 2;
		}
		MapSettings base = settings.deepCopy();

		SettingsGenerator.randomizeTheme(settings, base, settings.artPack, settings.artPack, new Random(7));

		assertEquals(TextBackgroundEffect.Scroll, settings.getDefaultTextStyle(TextType.Title).background.effect);
		TextBackgroundEffect regionEffect = settings.getDefaultTextStyle(TextType.Region).background.effect;
		assertTrue(regionEffect == TextBackgroundEffect.Glow || regionEffect == TextBackgroundEffect.Outline);
		assertEquals(base.getDefaultTextStyle(TextType.City), settings.getDefaultTextStyle(TextType.City), "Other types of text keep their style.");
		for (int i = 0; i < settings.edits.text.size(); i++)
		{
			MapText text = settings.edits.text.get(i);
			MapText baseText = base.edits.text.get(i);
			if (text.type == TextType.Title)
			{
				assertEquals(TextBackgroundEffect.Scroll, text.style.background.effect, "The title on the map changes too.");
			}
			else if (text.type == TextType.Region)
			{
				assertEquals(regionEffect, text.style.background.effect, "Region names on the map change too.");
				assertEquals(3, text.style.background.outlineWidth, "Region names take the effect's settings from the style for new region text.");
			}
			else
			{
				assertEquals(baseText.style, text.style, "Other text on the map keeps its style.");
			}
		}
	}

	@Test
	public void textBackgroundsStayTheSameWhenNotShuffled()
	{
		MapSettings settings = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		settings.themeGeneration = ThemeCatalog.getDefaultThemeRules();
		settings.themeGeneration.allowedTitleBackgroundEffects = new LinkedHashSet<>(List.of(TextBackgroundEffect.Scroll));
		settings.themeGeneration.allowedRegionBackgroundEffects = new LinkedHashSet<>(List.of(TextBackgroundEffect.Banner));
		settings.themeGeneration.shuffleTextBackgrounds = false;
		MapSettings base = settings.deepCopy();

		SettingsGenerator.randomizeTheme(settings, base, settings.artPack, settings.artPack, new Random(7));

		assertEquals(base.textStyleDefaults, settings.textStyleDefaults);
		for (int i = 0; i < settings.edits.text.size(); i++)
		{
			assertEquals(base.edits.text.get(i).style, settings.edits.text.get(i).style);
		}
	}

	@Test
	public void aNewMapWithTheSameThemeShufflesTextBackgroundsOnlyWhenTheThemeDoes()
	{
		MapSettings settings = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		settings.themeGeneration = ThemeCatalog.getDefaultThemeRules();
		settings.themeGeneration.allowedTitleBackgroundEffects = new LinkedHashSet<>(List.of(TextBackgroundEffect.Scroll));
		settings.themeGeneration.shuffleTextBackgrounds = false;
		settings.getDefaultTextStyle(TextType.Title).background.effect = TextBackgroundEffect.Glow;

		assertEquals(TextBackgroundEffect.Glow, SettingsGenerator.newMapWithSameTheme(settings).getDefaultTextStyle(TextType.Title).background.effect,
				"Without shuffling, new text keeps the theme's background.");
		settings.themeGeneration.shuffleTextBackgrounds = true;
		assertEquals(TextBackgroundEffect.Scroll, SettingsGenerator.newMapWithSameTheme(settings).getDefaultTextStyle(TextType.Title).background.effect);
	}

	@Test
	public void aNewMapWithTheSameThemeKeepsOnlyTheThemeAndRotation()
	{
		MapSettings settings = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		settings.resolution = MapSettings.defaultResolution + 0.5;
		settings.heightmapResolution = MapSettings.defaultHeightmapResolution + 0.5;
		settings.rightRotationCount = 1;
		settings.flipHorizontally = true;
		settings.flipVertically = true;

		MapSettings newMap = SettingsGenerator.newMapWithSameTheme(settings);
		assertEquals(MapSettings.defaultResolution, newMap.resolution);
		assertEquals(MapSettings.defaultHeightmapResolution, newMap.heightmapResolution);
		assertEquals(1, newMap.rightRotationCount);
		assertFalse(newMap.flipHorizontally);
		assertFalse(newMap.flipVertically);
		assertNotEquals(settings.backgroundRandomSeed, newMap.backgroundRandomSeed);
		assertNotEquals(settings.regionsRandomSeed, newMap.regionsRandomSeed);
		assertNotEquals(settings.frayedBorderSeed, newMap.frayedBorderSeed);
		assertEquals(settings.landColor, newMap.landColor);
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
		source.themeGeneration = ThemeCatalog.getDefaultThemeRules();
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
		assertTrue(dashes.hasWidthRange());
		assertEquals(25, dashes.minWidth());
		assertEquals(75, dashes.maxWidth());
		assertFalse(dashes.allowFrayedBorder());
		Assets.BorderMetadata lines = Assets.readBorderMetadata(new NamedResource(Assets.installedArtPack, "lines"), null);
		assertEquals(Assets.defaultBorderMetadata, lines);
		assertFalse(lines.hasWidthRange());
	}

	@Test
	public void everyThemeInTheInstalledArtPackLoads()
	{
		List<ThemeCatalog.Entry> themes = ThemeCatalog.listThemesForArtPack(Assets.installedArtPack, null);
		assertFalse(themes.isEmpty());
		for (ThemeCatalog.Entry entry : themes)
		{
			ThemeCatalog.load(entry);
		}
	}

	@Test
	public void aThemeMustHaveRules()
	{
		MapSettings theme = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		theme.fontArtPacks = new java.util.TreeMap<>();
		assertTrue(ThemeCatalog.findArtPackProblems("Some Art Pack", theme).isEmpty());
		theme.themeGeneration = null;
		assertEquals(1, ThemeCatalog.findArtPackProblems("Some Art Pack", theme).size());
	}

	@Test
	public void aThemesFontsForNewTextMustComeFromItsOwnArtPackOrTheInstalledOne()
	{
		MapSettings theme = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		String titleFamily = theme.getDefaultTextStyle(TextType.Title).font.getFamily();
		String regionFamily = theme.getDefaultTextStyle(TextType.Region).font.getFamily();

		theme.fontArtPacks = new java.util.TreeMap<>();
		assertTrue(ThemeCatalog.findArtPackProblems("Some Art Pack", theme).isEmpty(), "Fonts from the device aren't from an art pack.");
		theme.fontArtPacks.put(titleFamily, "Some Art Pack");
		theme.fontArtPacks.put(regionFamily, Assets.installedArtPack);
		assertTrue(ThemeCatalog.findArtPackProblems("Some Art Pack", theme).isEmpty());

		theme.fontArtPacks.put(titleFamily, "Another Art Pack");
		List<String> problems = ThemeCatalog.findArtPackProblems("Some Art Pack", theme);
		assertEquals(1, problems.size(), "Each font is reported once, however many kinds of text use it: " + problems);
		assertTrue(problems.get(0).contains(titleFamily));
	}

	@Test
	public void anArtPackWithoutThemesUsesTheInstalledArtPacksThemes()
	{
		assertEquals(ThemeCatalog.listThemesForArtPack(Assets.installedArtPack, null), ThemeCatalog.listThemesToChooseFrom("An Art Pack That Isn't Installed", null));
	}

	@Test
	public void aMapMadeFromAThemeKeepsItsRules()
	{
		MapSettings theme = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		theme.themeGeneration = ThemeCatalog.getDefaultThemeRules();
		theme.themeGeneration.grungeWidthVariation = 123;
		MapSettings settings = SettingsGenerator.generateFromTheme(new Random(1), Assets.installedArtPack, theme, false, null);
		assertEquals(123, settings.themeGeneration.grungeWidthVariation, "The map keeps the theme's rules.");
		assertEquals(Assets.installedArtPack, settings.borderResource.artPack, "The border comes from the chosen art pack.");

		theme.themeGeneration = null;
		assertThrows(IllegalStateException.class, () -> SettingsGenerator.generateFromTheme(new Random(1), Assets.installedArtPack, theme, false, null));
	}

	@Test
	public void mapsFromBeforeThemeRandomizationGetTheInstalledThemesRulesWhenLoaded()
	{
		MapSettings oldMap = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		assertTrue(oldMap.isFromBeforeThemeRandomization());
		assertNotNull(oldMap.themeGeneration);
		assertEquals(ThemeCatalog.getDefaultThemeRules().toJson(), oldMap.themeGeneration.toJson());
	}

	@Test
	public void aNewMapWithTheSameThemeUsesTheArtPackOfTheMapsIconsRatherThanTheIconsTools()
	{
		MapSettings map = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		map.artPack = "An Art Pack That Isn't Installed";
		assertEquals(SettingsGenerator.chooseArtPackOfMapsIcons(map), SettingsGenerator.newMapWithSameTheme(map).artPack);
		assertNotEquals(map.artPack, SettingsGenerator.newMapWithSameTheme(map).artPack);
	}

	@Test
	public void randomizingAMapsThemeChoosesFromTheArtPacksOfItsBorderAndBackgroundTexture()
	{
		MapSettings map = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		map.borderResource = new NamedResource(Assets.installedArtPack, "lines");
		assertEquals(Assets.installedArtPack, SettingsGenerator.getArtPackOfBorder(map));
		map.backgroundTextureSource = TextureSource.Assets;
		map.backgroundTextureResource = new NamedResource(Assets.installedArtPack, "texture.png");
		assertEquals(Assets.installedArtPack, SettingsGenerator.getArtPackOfBackgroundTexture(map));
		map.backgroundTextureSource = TextureSource.File;
		assertEquals(Assets.installedArtPack, SettingsGenerator.getArtPackOfBackgroundTexture(map), "A texture from a file uses the border's art pack.");

		map.borderResource = new NamedResource("An Art Pack That Isn't Installed", "lines");
		assertEquals(SettingsGenerator.chooseArtPackOfMapsIcons(map), SettingsGenerator.getArtPackOfBorder(map), "A border from an art pack that isn't installed uses the icons' art pack.");
	}
}
