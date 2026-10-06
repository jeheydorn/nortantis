package nortantis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import nortantis.geom.Point;
import nortantis.platform.Font;
import nortantis.platform.FontStyle;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;
import nortantis.swing.MapEdits;
import nortantis.util.Assets;

public class MapSettingsTest
{
	@BeforeAll
	public static void setUp()
	{
		PlatformFactory.setInstance(new AwtFactory());
		nortantis.swing.translation.Translation.initialize();
	}

	@Test
	public void fontStringRoundTrips()
	{
		Font font = Font.create("Georgia", FontStyle.BoldItalic, 23);
		Font parsed = MapSettings.parseFont(MapSettings.fontToString(font));

		assertEquals("Georgia", parsed.getName());
		assertEquals(FontStyle.BoldItalic, parsed.getStyle());
		assertEquals(23f, parsed.getSize());
	}

	@Test
	public void aFontStringWrittenBeforeCategoriesWereDroppedStillParses()
	{
		// Maps saved while fonts recorded a category have a fourth value, which is ignored rather than rejected.
		assertEquals("Georgia", MapSettings.parseFont("Georgia	0	20	Display").getName());
		assertEquals(20f, MapSettings.parseFont("Georgia	0	20	Display").getSize());
	}

	@Test
	public void parsingIsFaithfulForAFamilyThisMachineDoesNotHave()
	{
		Font parsed = MapSettings.parseFont("A Font That Does Not Exist\t0\t14");

		assertEquals("A Font That Does Not Exist", parsed.getName());
		assertEquals(FontStyle.Plain, parsed.getStyle());
		assertEquals(14f, parsed.getSize());
	}

	@Test
	public void identicalThemeFamiliesProduceOneProblem()
	{
		MapSettings settings = createSettingsWithAllThemeFonts("A Font That Does Not Exist");

		MapFonts.MissingFontInfo info = MapFonts.findProblems(settings);

		assertEquals(1, info.problems.size(), "Every theme font names the same family, so there should be one problem.");
		MapFonts.FontProblem problem = info.problems.get(0);
		assertEquals("A Font That Does Not Exist", problem.family);
		assertEquals(TextType.values().length, problem.newTextTypes.size());
	}

	@Test
	public void anAliasedFamilyIsNotAProblem()
	{
		// URW Chancery L resolves to the bundled Z003, which is the same typeface under another name, so it must not prompt.
		MapSettings settings = createSettingsWithAllThemeFonts("URW Chancery L");

		assertTrue(MapFonts.findProblems(settings).isEmpty());
	}

	@Test
	public void anInstalledFontThatCannotDrawTheMapsTextIsNotAProblem()
	{
		// The author picked a font that cannot draw their own text. Nothing about the machine opening the map caused that or can fix it,
		// and they will see it the moment the map draws, so opening the map must not stop to report it.
		MapSettings settings = createSettingsWithAllThemeFonts(FontFinder.houseFontFamily);
		settings.edits = new MapEdits();
		settings.edits.text = new CopyOnWriteArrayList<>(List.of(createMapText(settings, TextType.Title, "上海", null)));

		assertTrue(MapFonts.findProblems(settings).isEmpty());
	}

	@Test
	public void aMissingFontCarriesTheTextItHadToDraw()
	{
		// The replacement offered has to be able to draw the map's labels, which means knowing what they were.
		MapSettings settings = createSettingsWithAllThemeFonts("A Font That Does Not Exist");
		settings.edits = new MapEdits();
		settings.edits.text = new CopyOnWriteArrayList<>(List.of(createMapText(settings, TextType.Title, "Atelan", null)));

		MapFonts.MissingFontInfo info = MapFonts.findProblems(settings);

		assertEquals(1, info.problems.size());
		assertEquals(Map.of(FontStyle.Plain, "Atelan"), info.problems.get(0).textToDrawByStyle);
	}

	@Test
	public void aMissingFontKeepsItsTextUnderTheStyleItIsDrawnIn()
	{
		// A family's faces need not have the same glyphs, so which face has to draw which label decides whether a replacement will do.
		MapSettings settings = createSettingsWithAllThemeFonts("A Font That Does Not Exist");
		settings.setThemeFont(TextType.Title, Font.create("A Font That Does Not Exist", FontStyle.Bold, 20));
		settings.edits = new MapEdits();
		settings.edits.text = new CopyOnWriteArrayList<>(List.of(createMapText(settings, TextType.Title, "Łódź", null),
				createMapText(settings, TextType.Region, "Atelan", null),
				createMapText(settings, TextType.City, "Vinx", Font.create("A Font That Does Not Exist", FontStyle.Italic, 12))));

		MapFonts.MissingFontInfo info = MapFonts.findProblems(settings);

		assertEquals(1, info.problems.size());
		assertEquals(Map.of(FontStyle.Bold, "Łódź", FontStyle.Plain, "Atelan", FontStyle.Italic, "Vinx"),
				info.problems.get(0).textToDrawByStyle);
	}

	@Test
	public void aReplacementMustDrawEachStylesOwnText()
	{
		// Almendra's regular face has a glyph for ź and its bold face does not, so it can replace a font that drew "Łódź" plainly but not
		// one that drew it in bold.
		assertTrue(FontFinder.canDisplay("Almendra", Map.of(FontStyle.Plain, "Łódź", FontStyle.Bold, "Atelan")));
		assertFalse(FontFinder.canDisplay("Almendra", Map.of(FontStyle.Plain, "Atelan", FontStyle.Bold, "Łódź")));
	}

	@Test
	public void substitutionPreservesStyleAndSizeAndRewritesOverrides()
	{
		MapSettings settings = new MapSettings();
		settings.setThemeFont(TextType.Title, Font.create("A Font That Does Not Exist", FontStyle.BoldItalic, 50));
		settings.setThemeFont(TextType.Region, Font.create("A Font That Does Not Exist", FontStyle.Plain, 20));
		settings.setThemeFont(TextType.Mountain_range, Font.create("Georgia", FontStyle.Plain, 14));
		settings.setThemeFont(TextType.Other_mountains, Font.create("Georgia", FontStyle.Plain, 11));
		settings.setThemeFont(TextType.City, Font.create("Georgia", FontStyle.Plain, 10));
		settings.setThemeFont(TextType.River, Font.create("Georgia", FontStyle.Plain, 9));
		settings.setThemeFont(TextType.Road, Font.create("Georgia", FontStyle.Plain, 6));
		settings.edits = new MapEdits();
		settings.edits.text = new CopyOnWriteArrayList<>(
				List.of(createMapText(settings, TextType.Title, "Atelan", Font.create("A Font That Does Not Exist", FontStyle.Italic, 33)),
						createMapText(settings, TextType.Region, "Vinx", Font.create("Georgia", FontStyle.Plain, 12))));

		MapFonts.applySubstitution(settings, Map.of("A Font That Does Not Exist", FontFinder.houseFontFamily));

		Font titleFont = settings.getThemeFont(TextType.Title);
		assertEquals(FontFinder.houseFontFamily, titleFont.getName());
		assertEquals(FontStyle.BoldItalic, titleFont.getStyle());
		assertEquals(50f, titleFont.getSize());

		Font regionFont = settings.getThemeFont(TextType.Region);
		assertEquals(FontFinder.houseFontFamily, regionFont.getName());
		assertEquals(20f, regionFont.getSize());

		assertEquals("Georgia", settings.getThemeFont(TextType.Mountain_range).getName(), "A family that was not replaced must be left alone.");

		assertEquals(FontFinder.houseFontFamily, settings.edits.text.get(0).style.font.getName());
		assertEquals(FontStyle.Italic, settings.edits.text.get(0).style.font.getStyle());
		assertEquals(33f, settings.edits.text.get(0).style.font.getSize());
		assertEquals("Georgia", settings.edits.text.get(1).style.font.getName());
	}

	@Test
	public void fontFamiliesUsedIncludeThemeFontsAndEveryLabelsFont()
	{
		MapSettings settings = createSettingsWithAllThemeFonts("Georgia");
		settings.setThemeFont(TextType.Title, Font.create("Palatino", FontStyle.Plain, 50));
		settings.edits = new MapEdits();
		settings.edits.text = new CopyOnWriteArrayList<>(
				List.of(createMapText(settings, TextType.Title, "Atelan", Font.create("Tangerine", FontStyle.Plain, 20)),
						createMapText(settings, TextType.Region, "Vinx", Font.create("Tangerine", FontStyle.Plain, 30)),
						createMapText(settings, TextType.City, "Bree", null)));

		List<String> used = MapFonts.getFamiliesUsed(settings);

		assertEquals(List.of("Palatino", "Georgia", "Tangerine"), used,
				"Expected the theme fonts in order, then the families only individual labels use.");
	}

	@Test
	public void fontFamiliesUsedNamesAFamilyOnceHoweverManyLabelsUseIt()
	{
		MapSettings settings = createSettingsWithAllThemeFonts("Georgia");
		settings.edits = new MapEdits();
		settings.edits.text = new CopyOnWriteArrayList<>(
				List.of(createMapText(settings, TextType.Title, "Atelan", Font.create("georgia", FontStyle.Bold, 20)),
						createMapText(settings, TextType.Region, "Vinx", Font.create("GEORGIA", FontStyle.Plain, 30))));

		// The same family named three ways is one entry, keeping the picker from listing a font once per spelling.
		assertEquals(List.of("Georgia"), MapFonts.getFamiliesUsed(settings));
	}

	@Test
	public void aFontFromAMissingArtPackReportsTheArtPack()
	{
		// The map cannot draw the font and this device cannot say why, unless the map itself recorded which art pack to ask for.
		MapSettings settings = createSettingsWithAllThemeFonts("A Font That Does Not Exist");
		settings.fontArtPacks = Map.of("A Font That Does Not Exist", "An Art Pack That Is Not Installed");

		MapSettings.MissingArtPackInfo info = settings.findMissingArtPacks();

		assertEquals(List.of("An Art Pack That Is Not Installed"), info.missingArtPacks);
		assertEquals(1, info.fontCount);
		assertTrue(info.isEmpty(), "A missing font is not something choosing another art pack can fix, so it must not raise that dialog.");

		// The missing art pack is named where the user can act on it instead.
		MapFonts.MissingFontInfo fontProblems = MapFonts.findProblems(settings);
		assertEquals(1, fontProblems.problems.size());
		assertEquals("An Art Pack That Is Not Installed", fontProblems.problems.get(0).missingArtPack);
	}

	@Test
	public void aFontFromAnInstalledArtPackIsNotMissing()
	{
		MapSettings settings = createSettingsWithAllThemeFonts(FontFinder.houseFontFamily);
		settings.fontArtPacks = Map.of(FontFinder.houseFontFamily, Assets.installedArtPack);

		assertTrue(settings.findMissingArtPacks().isEmpty());
	}

	@Test
	public void aFontRecordingNoArtPackNeverReportsAMissingArtPack()
	{
		// Maps saved before fonts recorded an art pack, and fonts that came from the device, are looked up by name as they always were.
		MapSettings settings = createSettingsWithAllThemeFonts("A Font That Does Not Exist");

		assertTrue(settings.findMissingArtPacks().isEmpty());
		MapFonts.MissingFontInfo fontProblems = MapFonts.findProblems(settings);
		assertEquals(1, fontProblems.problems.size(), "It is still a missing font.");
		assertNull(fontProblems.problems.get(0).missingArtPack, "The map records no art pack for it, so there is none to name.");
	}

	@Test
	public void theArtPackAFontCameFromIsSavedAndReadBack() throws Exception
	{
		MapSettings settings = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		settings.setThemeFont(TextType.Title, Font.create(FontFinder.houseFontFamily, FontStyle.Plain, 40));

		Path temp = Files.createTempFile("fontArtPacks", ".nort");
		try
		{
			settings.writeToFile(temp.toString());
			MapSettings reloaded = new MapSettings(temp.toString());
			assertEquals(Assets.installedArtPack, reloaded.getFontArtPack(FontFinder.houseFontFamily));
		}
		finally
		{
			Files.deleteIfExists(temp);
		}
	}

	@Test
	public void anArtPackRecordedForAFontSurvivesSavingWithoutThatArtPack() throws Exception
	{
		// Saving a map on a device that lacks the art pack must not erase which art pack to ask for, or the next person to open it loses
		// the only explanation of why the font is missing.
		MapSettings settings = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		settings.setThemeFont(TextType.Title, Font.create("A Font That Does Not Exist", FontStyle.Plain, 40));
		settings.fontArtPacks = Map.of("A Font That Does Not Exist", "An Art Pack That Is Not Installed");

		Path temp = Files.createTempFile("fontArtPacks", ".nort");
		try
		{
			settings.writeToFile(temp.toString());
			MapSettings reloaded = new MapSettings(temp.toString());
			assertEquals("An Art Pack That Is Not Installed", reloaded.getFontArtPack("A Font That Does Not Exist"));
		}
		finally
		{
			Files.deleteIfExists(temp);
		}
	}

	@Test
	public void aMapSavedWithNoWavesOrShadingLoadsWithThoseSectionsOff()
	{
		// Saved at 3.22 with the None wave type, ocean shading width 0, and coast shading on.
		MapSettings settings = new MapSettings("unit test files/map settings/noText_WithCities_GoldenRatio_maskedBorder.nort");

		assertFalse(settings.drawOceanWaves);
		assertEquals(MapSettings.OceanWaves.ConcentricWaves, settings.oceanWavesType);
		assertTrue(settings.concentricWaveCount >= 1);
		assertFalse(settings.hasConcentricWaves());

		assertFalse(settings.drawOceanShading);
		assertEquals(MapSettings.defaultOceanShadingLevel, settings.oceanShadingLevel);
		assertFalse(settings.hasOceanShading(settings.resolution));

		assertTrue(settings.drawCoastShading);
		assertEquals(28, settings.coastShadingLevel);

		assertEquals(settings.frayedBorderColor, settings.grungeColor);
	}

	@Test
	public void aMapSavedWithBlurLoadsWithWavesOffAndOceanShadingOn()
	{
		MapSettings settings = new MapSettings("unit test files/map settings/smallWorld_allTextDeletedByHand_shouldNotRegenerateText.nort");

		assertFalse(settings.drawOceanWaves);
		assertEquals(MapSettings.OceanWaves.ConcentricWaves, settings.oceanWavesType);
		assertTrue(settings.drawOceanShading);
		assertEquals(16, settings.oceanShadingLevel);
	}

	@Test
	public void aMapSavedWithNoCoastShadingLoadsWithCoastShadingOff()
	{
		MapSettings settings = new MapSettings("unit test files/map settings/bottom right corner land gap.nort");

		assertFalse(settings.drawCoastShading);
		assertEquals(MapSettings.defaultCoastShadingLevel, settings.coastShadingLevel);
		assertEquals(0, settings.getDrawnCoastShadingLevel());
	}

	@Test
	public void sectionSettingsAndGrungeColorAreSavedAndReadBack() throws Exception
	{
		MapSettings settings = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		settings.drawCoastShading = false;
		settings.drawOceanShading = true;
		settings.drawOceanWaves = false;
		settings.coastShadingLevel = 12;
		settings.grungeColor = nortantis.platform.Color.create(1, 2, 3, 4);

		Path temp = Files.createTempFile("sections", ".nort");
		try
		{
			settings.writeToFile(temp.toString());
			MapSettings reloaded = new MapSettings(temp.toString());
			assertFalse(reloaded.drawCoastShading);
			assertTrue(reloaded.drawOceanShading);
			assertFalse(reloaded.drawOceanWaves);
			assertEquals(12, reloaded.coastShadingLevel);
			assertEquals(settings.grungeColor, reloaded.grungeColor);
			assertEquals(settings.frayedBorderColor, reloaded.frayedBorderColor);
			assertEquals(settings, reloaded);
		}
		finally
		{
			Files.deleteIfExists(temp);
		}
	}

	@Test
	public void oldTextSettingsConvertToAStyleOnEveryText()
	{
		// Saved before 3.25 with a bold background on, one text color, and per-text color, bold background color, and font overrides.
		MapSettings settings = new MapSettings("unit test files/map settings/allTypesOfEdits.nort");

		TextStyle regionDefault = settings.getDefaultTextStyle(TextType.Region);
		assertEquals(TextBackgroundEffect.BoldBackground, regionDefault.background.effect);
		assertEquals(nortantis.platform.Color.create(254, 230, 201, 255), regionDefault.background.color);
		assertEquals(TextBackground.defaultOutlineWidth, regionDefault.background.outlineWidth);
		assertEquals(2, TextBackground.defaultOutlineWidth, "Old maps and new maps start Outline at a width of 2.");
		assertEquals(nortantis.platform.Color.create(89, 71, 54, 255), regionDefault.color);
		assertEquals("Gabriola", regionDefault.font.getName());
		assertEquals(TextBackgroundEffect.None, settings.getDefaultTextStyle(TextType.River).background.effect,
				"Bold background was only drawn behind region and title text.");
		assertEquals(settings.getDefaultTextStyle(TextType.River), settings.getDefaultTextStyle(TextType.Lake),
				"Lakes shared the river font before they had their own style.");

		MapText boldCurved = findText(settings, "Bold background curved with negative spacing and custom colors");
		assertEquals(TextBackgroundEffect.BoldBackground, boldCurved.style.background.effect);
		assertEquals(nortantis.platform.Color.create(179, 190, 204, 255), boldCurved.style.background.color);
		assertEquals(nortantis.platform.Color.create(51, 133, 52, 255), boldCurved.style.color);
		assertEquals("Gabriola", boldCurved.style.font.getName(), "Text without its own font gets its type's font.");

		MapText riverWithColor = findText(settings, "Custom color with spacing");
		assertEquals(TextBackgroundEffect.None, riverWithColor.style.background.effect);
		assertEquals(nortantis.platform.Color.create(145, 64, 149, 255), riverWithColor.style.color);

		MapText withFont = findText(settings, "Text with font override");
		assertEquals("Jokerman", withFont.style.font.getName());
		assertEquals(FontStyle.Bold, withFont.style.font.getStyle());
		assertEquals(nortantis.platform.Color.create(89, 71, 54, 255), withFont.style.color, "Text without its own color gets the text color.");
	}

	@Test
	public void boldBackgroundColorFromBeforeGlowAndOutlineSharedItIsRead()
	{
		org.json.simple.JSONObject json = TextBackground.createDefault().toJson();
		json.remove("color");
		json.put("boldColor", MapSettings.colorToString(nortantis.platform.Color.create(10, 20, 30, 255)));
		assertEquals(nortantis.platform.Color.create(10, 20, 30, 255), TextBackground.fromJson(json).color);
	}

	@Test
	public void textStylesAreSavedAndReadBack() throws Exception
	{
		MapSettings settings = new MapSettings("unit test files/map settings/allTypesOfEdits.nort");
		TextStyle titleDefault = settings.getDefaultTextStyle(TextType.Title);
		titleDefault.background.effect = TextBackgroundEffect.Scroll;
		titleDefault.background.shapeJitter = 7;
		titleDefault.background.color = nortantis.platform.Color.create(1, 2, 3, 4);
		titleDefault.background.outlineWidth = 5;
		assertEquals(TextLayoutSettings.createDefault(), settings.getDefaultTextLayout(TextType.Region),
				"A map saved before new text had a layout gives every kind of new text the default layout.");
		settings.getDefaultTextLayout(TextType.Region).curvature = 0.35;
		settings.getDefaultTextLayout(TextType.Region).spacing = 7;
		settings.getDefaultTextLayout(TextType.Title).lineBreak = LineBreak.One_line;
		MapText text = findText(settings, "Custom color with curve");
		text.style.background.effect = TextBackgroundEffect.Glow;
		text.style.background.glowSize = 13;
		text.style.background.fade = 0.4;
		text.backgroundSeed = 12345;

		Path temp = Files.createTempFile("textStyles", ".nort");
		try
		{
			settings.writeToFile(temp.toString());
			MapSettings reloaded = new MapSettings(temp.toString());
			assertEquals(settings.textStyleDefaults, reloaded.textStyleDefaults);
			assertEquals(settings.textLayoutDefaults, reloaded.textLayoutDefaults);
			assertEquals(settings.edits.text, reloaded.edits.text);
			MapText reloadedText = findText(reloaded, "Custom color with curve");
			assertEquals(12345, reloadedText.backgroundSeed);
			assertEquals(TextBackgroundEffect.Glow, reloadedText.style.background.effect);
		}
		finally
		{
			Files.deleteIfExists(temp);
		}
	}

	@Test
	public void oldTextGetsTheSameWobbleSeedEveryTimeItLoads()
	{
		MapSettings first = new MapSettings("unit test files/map settings/allTypesOfEdits.nort");
		MapSettings second = new MapSettings("unit test files/map settings/allTypesOfEdits.nort");
		assertEquals(first.edits.text, second.edits.text);
	}

	private static MapText findText(MapSettings settings, String value)
	{
		return settings.edits.text.stream().filter(text -> value.equals(text.value)).findFirst().orElseThrow();
	}

	private static MapSettings createSettingsWithAllThemeFonts(String family)
	{
		MapSettings settings = new MapSettings();
		for (TextType type : TextType.values())
		{
			settings.setThemeFont(type, Font.create(family, FontStyle.Plain, 20));
		}
		return settings;
	}

	/**
	 * Creates text styled with the settings' style for new text of its type, except for the given font, if it isn't null.
	 */
	private static MapText createMapText(MapSettings settings, TextType type, String value, Font font)
	{
		TextStyle style = settings.getDefaultTextStyle(type).copy();
		if (font != null)
		{
			style.font = font;
		}
		return new MapText(value, new Point(0, 0), 0.0, type, LineBreak.Auto, 0.0, 0, style, 0);
	}

	@Test
	public void themeFontsCoverEveryFontField()
	{
		MapSettings settings = new MapSettings();
		for (TextType type : TextType.values())
		{
			Font font = Font.create("Georgia", FontStyle.Plain, 10 + type.ordinal());
			settings.setThemeFont(type, font);
		}

		assertEquals(TextType.values().length, settings.getThemeFonts().size());
		for (TextType type : TextType.values())
		{
			assertEquals(10f + type.ordinal(), settings.getThemeFonts().get(type).getSize(), "Wrong font for " + type);
		}
	}
}
