package nortantis;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

		MapSettings.MissingFontInfo info = settings.findFontProblems();

		assertEquals(1, info.problems.size(), "Every theme font names the same family, so there should be one problem.");
		MapSettings.FontProblem problem = info.problems.get(0);
		assertEquals("A Font That Does Not Exist", problem.family);
		assertEquals(MapSettings.ThemeFontType.values().length, problem.usedBy.size());
	}

	@Test
	public void anAliasedFamilyIsNotAProblem()
	{
		// URW Chancery L resolves to the bundled Z003, which is the same typeface under another name, so it must not prompt.
		MapSettings settings = createSettingsWithAllThemeFonts("URW Chancery L");

		assertTrue(settings.findFontProblems().isEmpty());
	}

	@Test
	public void anInstalledFontThatCannotDrawTheMapsTextIsNotAProblem()
	{
		// The author picked a font that cannot draw their own text. Nothing about the machine opening the map caused that or can fix it,
		// and they will see it the moment the map draws, so opening the map must not stop to report it.
		MapSettings settings = createSettingsWithAllThemeFonts(FontFinder.houseFontFamily);
		settings.edits = new MapEdits();
		settings.edits.text = new CopyOnWriteArrayList<>(List.of(createMapText(TextType.Title, "上海", null)));

		assertTrue(settings.findFontProblems().isEmpty());
	}

	@Test
	public void aMissingFontCarriesTheTextItHadToDraw()
	{
		// The replacement offered has to be able to draw the map's labels, which means knowing what they were.
		MapSettings settings = createSettingsWithAllThemeFonts("A Font That Does Not Exist");
		settings.edits = new MapEdits();
		settings.edits.text = new CopyOnWriteArrayList<>(List.of(createMapText(TextType.Title, "Atelan", null)));

		MapSettings.MissingFontInfo info = settings.findFontProblems();

		assertEquals(1, info.problems.size());
		assertEquals("Atelan", info.problems.get(0).textToDraw);
	}

	@Test
	public void substitutionPreservesStyleAndSizeAndRewritesOverrides()
	{
		MapSettings settings = new MapSettings();
		settings.titleFont = Font.create("A Font That Does Not Exist", FontStyle.BoldItalic, 50);
		settings.regionFont = Font.create("A Font That Does Not Exist", FontStyle.Plain, 20);
		settings.mountainRangeFont = Font.create("Georgia", FontStyle.Plain, 14);
		settings.otherMountainsFont = Font.create("Georgia", FontStyle.Plain, 11);
		settings.citiesFont = Font.create("Georgia", FontStyle.Plain, 10);
		settings.riverFont = Font.create("Georgia", FontStyle.Plain, 9);
		settings.roadFont = Font.create("Georgia", FontStyle.Plain, 6);
		settings.edits = new MapEdits();
		settings.edits.text = new CopyOnWriteArrayList<>(
				List.of(createMapText(TextType.Title, "Atelan", Font.create("A Font That Does Not Exist", FontStyle.Italic, 33)),
						createMapText(TextType.Region, "Vinx", Font.create("Georgia", FontStyle.Plain, 12))));

		settings.applyFontSubstitution(Map.of("A Font That Does Not Exist", FontFinder.houseFontFamily));

		assertEquals(FontFinder.houseFontFamily, settings.titleFont.getName());
		assertEquals(FontStyle.BoldItalic, settings.titleFont.getStyle());
		assertEquals(50f, settings.titleFont.getSize());

		assertEquals(FontFinder.houseFontFamily, settings.regionFont.getName());
		assertEquals(20f, settings.regionFont.getSize());

		assertEquals("Georgia", settings.mountainRangeFont.getName(), "A family that was not replaced must be left alone.");

		assertEquals(FontFinder.houseFontFamily, settings.edits.text.get(0).fontOverride.getName());
		assertEquals(FontStyle.Italic, settings.edits.text.get(0).fontOverride.getStyle());
		assertEquals(33f, settings.edits.text.get(0).fontOverride.getSize());
		assertEquals("Georgia", settings.edits.text.get(1).fontOverride.getName());
	}

	@Test
	public void fontFamiliesUsedIncludeThemeFontsAndEveryOverride()
	{
		MapSettings settings = createSettingsWithAllThemeFonts("Georgia");
		settings.titleFont = Font.create("Palatino", FontStyle.Plain, 50);
		settings.edits = new MapEdits();
		settings.edits.text = new CopyOnWriteArrayList<>(
				List.of(createMapText(TextType.Title, "Atelan", Font.create("Tangerine", FontStyle.Plain, 20)),
						createMapText(TextType.Region, "Vinx", Font.create("Tangerine", FontStyle.Plain, 30)),
						createMapText(TextType.City, "Bree", null)));

		List<String> used = settings.getFontFamiliesUsed();

		assertEquals(List.of("Palatino", "Georgia", "Tangerine"), used,
				"Expected the theme fonts in order, then the families only individual labels use.");
	}

	@Test
	public void fontFamiliesUsedNamesAFamilyOnceHoweverManyLabelsUseIt()
	{
		MapSettings settings = createSettingsWithAllThemeFonts("Georgia");
		settings.edits = new MapEdits();
		settings.edits.text = new CopyOnWriteArrayList<>(
				List.of(createMapText(TextType.Title, "Atelan", Font.create("georgia", FontStyle.Bold, 20)),
						createMapText(TextType.Region, "Vinx", Font.create("GEORGIA", FontStyle.Plain, 30))));

		// The same family named three ways is one entry, keeping the picker from listing a font once per spelling.
		assertEquals(List.of("Georgia"), settings.getFontFamiliesUsed());
	}

	@Test
	public void everyTextTypeHasAThemeFont()
	{
		for (TextType type : TextType.values())
		{
			assertNotNull(MapSettings.getThemeFontTypeForText(type), "No theme font for text type " + type);
		}
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
		MapSettings.MissingFontInfo fontProblems = settings.findFontProblems();
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
		MapSettings.MissingFontInfo fontProblems = settings.findFontProblems();
		assertEquals(1, fontProblems.problems.size(), "It is still a missing font.");
		assertNull(fontProblems.problems.get(0).missingArtPack, "The map records no art pack for it, so there is none to name.");
	}

	@Test
	public void theArtPackAFontCameFromIsSavedAndReadBack() throws Exception
	{
		MapSettings settings = new MapSettings("unit test files/map settings/simpleSmallWorld.nort");
		settings.setThemeFont(MapSettings.ThemeFontType.Title, Font.create(FontFinder.houseFontFamily, FontStyle.Plain, 40));

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
		settings.setThemeFont(MapSettings.ThemeFontType.Title, Font.create("A Font That Does Not Exist", FontStyle.Plain, 40));
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

	private static MapSettings createSettingsWithAllThemeFonts(String family)
	{
		MapSettings settings = new MapSettings();
		for (MapSettings.ThemeFontType type : MapSettings.ThemeFontType.values())
		{
			settings.setThemeFont(type, Font.create(family, FontStyle.Plain, 20));
		}
		return settings;
	}

	private static MapText createMapText(TextType type, String value, Font fontOverride)
	{
		return new MapText(value, new Point(0, 0), 0.0, type, LineBreak.Auto, null, null, 0.0, 0, fontOverride, MapText.defaultBackgroundFade);
	}

	@Test
	public void themeFontsCoverEveryFontField()
	{
		MapSettings settings = new MapSettings();
		for (MapSettings.ThemeFontType type : MapSettings.ThemeFontType.values())
		{
			Font font = Font.create("Georgia", FontStyle.Plain, 10 + type.ordinal());
			settings.setThemeFont(type, font);
		}

		assertEquals(MapSettings.ThemeFontType.values().length, settings.getThemeFonts().size());
		for (MapSettings.ThemeFontType type : MapSettings.ThemeFontType.values())
		{
			assertEquals(10f + type.ordinal(), settings.getThemeFonts().get(type).getSize(), "Wrong font for " + type);
		}
	}
}
