package nortantis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import nortantis.FontFinder.FontCategory;
import nortantis.geom.Point;
import nortantis.platform.Font;
import nortantis.platform.FontStyle;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;
import nortantis.swing.MapEdits;

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
	public void fontStringWithACategoryRoundTrips()
	{
		Font font = Font.create("Georgia", FontStyle.Plain, 20);
		String written = MapSettings.fontToString(font, FontCategory.Display);

		assertEquals("Georgia\t0\t20\tDisplay", written);
		assertEquals("Georgia", MapSettings.parseFont(written).getName());
		assertEquals(20f, MapSettings.parseFont(written).getSize());
		assertEquals(FontCategory.Display, MapSettings.parseFontCategory(written));
	}

	@Test
	public void aFontStringWithNoCategoryParsesAsUnknown()
	{
		// Every font string written before categories existed has three values, and must keep loading.
		assertNull(MapSettings.parseFontCategory("Georgia\t0\t20"));
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
		assertEquals(MapSettings.FontProblem.Kind.NotInstalled, problem.kind);
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
	public void aFontThatCannotDrawTheMapsTextIsAProblem()
	{
		MapSettings settings = createSettingsWithAllThemeFonts(FontFinder.houseFontFamily);
		settings.edits = new MapEdits();
		settings.edits.text = new CopyOnWriteArrayList<>(List.of(createMapText(TextType.Title, "上海", null)));

		MapSettings.MissingFontInfo info = settings.findFontProblems();

		assertEquals(1, info.problems.size());
		assertEquals(MapSettings.FontProblem.Kind.MissingCharacters, info.problems.get(0).kind);
		assertEquals("上海", info.problems.get(0).sampleOfUndrawableText);
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
	public void everyTextTypeHasAThemeFont()
	{
		for (TextType type : TextType.values())
		{
			assertNotNull(MapSettings.getThemeFontTypeForText(type), "No theme font for text type " + type);
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
