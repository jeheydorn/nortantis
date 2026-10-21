package nortantis;

import nortantis.editor.FreeIcon;
import nortantis.editor.RegionEdit;
import nortantis.geom.Point;
import nortantis.platform.Color;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;
import nortantis.util.Assets;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.*;

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
		gen.allowedLandColoringMethods = new LinkedHashSet<>(List.of(LandColoringMethod.ColorPoliticalRegions));

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
		settings.themeGeneration.allowedLandColoringMethods = EnumSet.of(LandColoringMethod.ColorPoliticalRegions);
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
			settings.themeGeneration.allowedLandColoringMethods = EnumSet
					.of(drawRegionColors ? LandColoringMethod.ColorPoliticalRegions : LandColoringMethod.SingleColor);
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
			assertEquals(drawRegionColors, settings.drawRegionColors, "The land coloring method is the one the theme allows.");
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

		int textureCount = Assets.listBackgroundTexturesForArtPack(settings.artPack, settings.customArtPackPath).size();
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
			assertEquals(Assets.installedArtPack, settings.backgroundTextureResource.artPack, "An art pack with no textures uses the installed art pack's.");
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
		// One region name has its own background, which the theme doesn't change.
		MapText regionWithOwnBackground = settings.edits.text.stream().filter(text -> text.type == TextType.Region).findFirst().get();
		TextStyle ownStyle = settings.resolveTextStyle(regionWithOwnBackground).copy();
		ownStyle.background.effect = TextBackgroundEffect.Banner;
		regionWithOwnBackground.styleOverrides.set(TextStylePart.Background, ownStyle);
		MapSettings base = settings.deepCopy();

		SettingsGenerator.randomizeTheme(settings, base, settings.artPack, settings.artPack, new Random(7));

		assertEquals(TextBackgroundEffect.Banner, settings.resolveTextStyle(regionWithOwnBackground).background.effect, "A region name with its own background keeps it.");
		assertEquals(TextBackgroundEffect.Scroll, settings.getDefaultTextStyle(TextType.Title).background.effect);
		TextBackgroundEffect regionEffect = settings.getDefaultTextStyle(TextType.Region).background.effect;
		assertTrue(regionEffect == TextBackgroundEffect.Glow || regionEffect == TextBackgroundEffect.Outline);
		assertEquals(base.getDefaultTextStyle(TextType.City), settings.getDefaultTextStyle(TextType.City), "Other types of text keep their style.");
		for (int i = 0; i < settings.edits.text.size(); i++)
		{
			MapText text = settings.edits.text.get(i);
			MapText baseText = base.edits.text.get(i);
			TextStyle style = settings.resolveTextStyle(text);
			assertEquals(baseText.styleOverrides, text.styleOverrides, "Text's own style is left alone.");
			if (text.styleOverrides.has(TextStylePart.Background))
			{
				assertEquals(base.resolveTextStyle(baseText).background, style.background, "Text with its own background keeps it.");
			}
			else if (text.type == TextType.Title)
			{
				assertEquals(TextBackgroundEffect.Scroll, style.background.effect, "The title on the map follows its style.");
			}
			else if (text.type == TextType.Region)
			{
				assertEquals(regionEffect, style.background.effect, "Region names on the map follow their style.");
				assertEquals(3, style.background.outlineWidth, "Region names take the effect's settings from the region style.");
			}
			else
			{
				assertEquals(base.resolveTextStyle(baseText), style, "Other text on the map keeps its style.");
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
			assertEquals(base.edits.text.get(i).styleOverrides, settings.edits.text.get(i).styleOverrides);
		}
	}

	@Test
	public void aNewMapWithTheSameThemeKeepsTextBackgroundsEvenWhenTheThemeShufflesThem()
	{
		MapSettings settings = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		settings.themeGeneration = ThemeCatalog.getDefaultThemeRules();
		settings.themeGeneration.allowedTitleBackgroundEffects = new LinkedHashSet<>(List.of(TextBackgroundEffect.Scroll));
		settings.themeGeneration.shuffleTextBackgrounds = true;
		settings.getDefaultTextStyle(TextType.Title).background.effect = TextBackgroundEffect.Glow;

		assertEquals(TextBackgroundEffect.Glow, SettingsGenerator.newMapWithSameTheme(settings).getDefaultTextStyle(TextType.Title).background.effect);
	}

	@Test
	public void aNewMapWithTheSameThemeKeepsTheCityIconTypeWhenItsArtPackHasIt()
	{
		MapSettings settings = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		settings.edits.freeIcons.clear();
		List<String> cityIconTypes = ImageCache.getInstance(settings.artPack, settings.customArtPackPath).getIconGroupNames(IconType.cities);
		assertTrue(cityIconTypes.size() > 1);
		for (String cityIconType : cityIconTypes)
		{
			settings.cityIconTypeName = cityIconType;
			assertEquals(cityIconType, SettingsGenerator.newMapWithSameTheme(settings).cityIconTypeName);
		}
		settings.cityIconTypeName = "not a city icon type";
		assertTrue(cityIconTypes.contains(SettingsGenerator.newMapWithSameTheme(settings).cityIconTypeName));
	}

	@Test
	public void aNewMapWithTheSameThemeUsesTheCityIconTypeOfMostOfTheMapsCities()
	{
		MapSettings settings = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		settings.edits.freeIcons.clear();
		settings.cityIconTypeName = "flat";
		String[] groupIds = { "middle ages", "middle ages", "flat" };
		for (int i = 0; i < groupIds.length; i++)
		{
			settings.edits.freeIcons.addOrReplace(new FreeIcon(settings.resolution, new Point(10 * i, 10 * i), 1.0, IconType.cities, Assets.installedArtPack, groupIds[i], "town", i,
					Color.create(0, 0, 0), new HSBColor(0, 0, 100, 0), false, false, false));
		}
		assertEquals("middle ages", SettingsGenerator.newMapWithSameTheme(settings).cityIconTypeName);
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
		assertEquals(settings.backgroundRandomSeed, newMap.backgroundRandomSeed);
		assertEquals(settings.regionsRandomSeed, newMap.regionsRandomSeed);
		assertEquals(settings.frayedBorderSeed, newMap.frayedBorderSeed);
		assertNotEquals(settings.randomSeed, newMap.randomSeed);
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

		MapSettings target = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		int targetTextCount = target.edits.text.size();
		target.copyThemeFrom(source);
		assertEquals(source.landColor, target.landColor);
		assertEquals(source.coastShadingAlpha, target.coastShadingAlpha);
		assertEquals(source.textStyleDefaults, target.textStyleDefaults);
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
		assertEquals(61, MapSettings.convertCoastShadingColorToAlpha(halfLand, Color.create(200, 160, 100, 0), false, "3.24"),
				"Transparent land shows white behind the map.");
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
	public void theSameThemeSeedGivesTheSameLookWhateverTheWorld()
	{
		MapSettings theme = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		theme.themeGeneration = ThemeCatalog.getDefaultThemeRules();
		MapSettings first = SettingsGenerator.generateFromTheme(new Random(1), 42, Assets.installedArtPack, theme, false, null);
		MapSettings second = SettingsGenerator.generateFromTheme(new Random(2), 42, Assets.installedArtPack, theme, false, null);
		assertNotEquals(first.randomSeed, second.randomSeed, "The worlds differ.");

		second.randomSeed = first.randomSeed;
		second.textRandomSeed = first.textRandomSeed;
		second.worldSize = first.worldSize;
		second.landShape = first.landShape;
		second.regionCount = first.regionCount;
		second.generatedWidth = first.generatedWidth;
		second.generatedHeight = first.generatedHeight;
		second.books = first.books;
		assertTrue(first.equalsIgnoringEdits(second), "Apart from the world, the maps are the same.");

		MapSettings randomized = first.deepCopy();
		SettingsGenerator.randomizeTheme(randomized, theme, Assets.installedArtPack, Assets.installedArtPack, 42);
		assertTrue(first.equalsIgnoringEdits(randomized), "Randomizing the theme with the seed a map was made from gives the same look.");
	}

	@Test
	public void theLandColoringMethodIsChosenFromThoseTheThemeAllows()
	{
		MapSettings base = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		base.themeGeneration = ThemeCatalog.getDefaultThemeRules();
		base.themeGeneration.allowedBackgroundTypes = new LinkedHashSet<>(List.of(ThemeGenerationSettings.BackgroundType.Fractal));
		for (boolean baseDrawsRegionColors : new boolean[] { true, false })
		{
			base.drawRegionColors = baseDrawsRegionColors;
			for (LandColoringMethod method : LandColoringMethod.values())
			{
				base.themeGeneration.allowedLandColoringMethods = EnumSet.of(method);
				for (long seed = 0; seed < 5; seed++)
				{
					MapSettings settings = base.deepCopy();
					SettingsGenerator.applyThemeRandomness(settings, base, base.themeGeneration, Assets.installedArtPack, Assets.installedArtPack, seed);
					assertEquals(method == LandColoringMethod.ColorPoliticalRegions, settings.drawRegionColors);
				}
			}
		}

		// An empty set allows every method, like the theme's other allowed sets.
		for (Set<LandColoringMethod> allowed : List.of(EnumSet.of(LandColoringMethod.SingleColor, LandColoringMethod.ColorPoliticalRegions),
				new LinkedHashSet<LandColoringMethod>()))
		{
			base.themeGeneration.allowedLandColoringMethods = allowed;
			Set<Boolean> chosen = new HashSet<>();
			for (long seed = 0; seed < 40; seed++)
			{
				MapSettings settings = base.deepCopy();
				SettingsGenerator.applyThemeRandomness(settings, base, base.themeGeneration, Assets.installedArtPack, Assets.installedArtPack, seed);
				chosen.add(settings.drawRegionColors);
			}
			assertEquals(Set.of(true, false), chosen, "Either allowed method can be chosen: " + allowed);
		}
	}

	@Test
	public void rulesFromBeforeThemesChoseTheirLandColoringMethodAllowTheMethodsThatExistedThen()
	{
		JSONObject json = ThemeCatalog.getDefaultThemeRules().toJson();
		json.remove("allowedLandColoringMethods");
		assertEquals(EnumSet.of(LandColoringMethod.SingleColor, LandColoringMethod.ColorPoliticalRegions),
				ThemeGenerationSettings.fromJson(json).allowedLandColoringMethods);
	}

	@Test
	public void aTextureThatDoesNotColorizeTheLandDrawsASingleLandColor()
	{
		MapSettings base = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		base.drawRegionColors = true;
		base.colorizeLand = false;
		base.themeGeneration = ThemeCatalog.getDefaultThemeRules();
		base.themeGeneration.allowedLandColoringMethods = EnumSet.of(LandColoringMethod.ColorPoliticalRegions);
		base.themeGeneration.landHueVariation = 90;

		base.themeGeneration.allowedBackgroundTypes = new LinkedHashSet<>(List.of(ThemeGenerationSettings.BackgroundType.GeneratedFromTexture));
		MapSettings fromTexture = base.deepCopy();
		SettingsGenerator.applyThemeRandomness(fromTexture, base, base.themeGeneration, Assets.installedArtPack, Assets.installedArtPack, 3L);
		assertTrue(fromTexture.generateBackgroundFromTexture);
		assertFalse(fromTexture.drawRegionColors, "The theme doesn't colorize the land, so regions can't be colored.");
		assertNotEquals(base.landColor, fromTexture.landColor, "The land color is varied, since the land is drawn in a single color.");

		base.themeGeneration.allowedBackgroundTypes = new LinkedHashSet<>(List.of(ThemeGenerationSettings.BackgroundType.Fractal));
		MapSettings fractal = base.deepCopy();
		SettingsGenerator.applyThemeRandomness(fractal, base, base.themeGeneration, Assets.installedArtPack, Assets.installedArtPack, 3L);
		assertTrue(fractal.drawRegionColors, "Colorizing the land only matters for textures.");
	}

	@Test
	public void anArtPackWithoutBordersOrTexturesGetsTheInstalledArtPacksLook()
	{
		MapSettings base = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		base.themeGeneration = ThemeCatalog.getDefaultThemeRules();
		for (long themeSeed = 0; themeSeed < 20; themeSeed++)
		{
			MapSettings installed = base.deepCopyExceptEdits();
			SettingsGenerator.applyThemeRandomness(installed, base, base.themeGeneration, Assets.installedArtPack, Assets.installedArtPack, themeSeed);
			MapSettings withoutArt = base.deepCopyExceptEdits();
			SettingsGenerator.applyThemeRandomness(withoutArt, base, base.themeGeneration, "An art pack with no borders", "An art pack with no textures", themeSeed);
			assertTrue(installed.equalsIgnoringEdits(withoutArt), "Theme seed " + themeSeed);
		}
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
