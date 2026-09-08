package nortantis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import nortantis.FontFinder.AvailableFont;
import nortantis.FontFinder.FontCategory;
import nortantis.FontFinder.FontSource;
import nortantis.FontFinder.Script;
import nortantis.platform.Font;
import nortantis.platform.FontStyle;
import nortantis.platform.PlatformFactory;
import nortantis.platform.awt.AwtFactory;
import nortantis.util.Assets;

public class FontFinderTest
{
	private static final Set<String> licenseFileNameFragments = Set.of("license", "ofl", "copying");

	@BeforeAll
	public static void setUp()
	{
		PlatformFactory.setInstance(new AwtFactory());
		FontFinder.ensureInitialized();
	}

	@Test
	public void bundledFontsRegister()
	{
		List<AvailableFont> bundled = getBundledFonts();
		assertFalse(bundled.isEmpty(), "No bundled fonts registered. Expected at least " + FontFinder.houseFontFamily + ".");
		assertTrue(FontFinder.isAvailable(FontFinder.houseFontFamily), FontFinder.houseFontFamily + " did not register.");
		assertEquals(FontSource.Bundled, FontFinder.getSource(FontFinder.houseFontFamily));
	}

	@Test
	public void theChromeFontCoversEveryLanguageNortantisIsTranslatedInto()
	{
		// The chrome font is what the app's own furniture draws with, and it may substitute silently, so a gap here is invisible until
		// someone running in that language sees boxes on a tool icon. Languages a bundled family covers must use it; a language none of
		// them covers plainly falls through to the platform's own interface face, which is why this checks coverage rather than source.
		assertEquals(FontSource.Bundled, FontFinder.getSource(FontFinder.chromeFontFamily),
				FontFinder.chromeFontFamily + " must be bundled, since the app draws its own text with it on every machine.");

		List<String> toolIconKeys = List.of("textTool.toolIcon", "iconsTool.toolIcon", "overlayTool.toolIcon", "landWaterTool.toolIcon.land",
				"landWaterTool.toolIcon.water");

		for (String language : List.of("en", "de", "es", "fr", "pt", "ru", "zh"))
		{
			String family = FontFinder.getChromeFontFamily(language);

			ResourceBundle bundle = ResourceBundle.getBundle("nortantis.swing.translation.messages", new Locale(language),
					ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
			for (String key : toolIconKeys)
			{
				String text = bundle.getString(key);
				assertTrue(FontFinder.canDisplay(family, text),
						"The chrome font for '" + language + "' (" + family + ") cannot draw " + key + ": " + text);
			}
		}
	}

	@Test
	public void everyCategoryHasAnAvailableSubstitute()
	{
		for (FontCategory category : FontCategory.values())
		{
			String substitute = FontFinder.chooseSubstitute("A Font That Does Not Exist", category, "Aa");
			assertNotNull(substitute, "No substitute available for category " + category + ".");
			assertTrue(FontFinder.isAvailable(substitute), "chooseSubstitute returned an unavailable family: " + substitute);
		}
	}

	@Test
	public void everyPreferredFamilyIsBundled()
	{
		for (FontCategory category : FontCategory.values())
		{
			for (String family : FontFinder.preferredFamiliesByCategory.get(category))
			{
				assertEquals(FontSource.Bundled, FontFinder.getSource(family),
						"The preferred substitute '" + family + "' for category " + category + " is not a bundled font.");
			}
		}
	}

	@Test
	public void enoughBundledFamiliesCoverCyrillic()
	{
		// Russian is the second language of this application by a wide margin, and Cyrillic coverage inside a family that already covers
		// Latin costs no extra bytes, so it is a selection criterion for the bundled set rather than a single fallback font.
		Set<FontCategory> categories = new HashSet<>();
		int count = 0;
		for (AvailableFont font : getBundledFonts())
		{
			if (FontFinder.covers(font.family, Script.Cyrillic))
			{
				count++;
				categories.add(font.category);
			}
		}

		assertTrue(count >= 4, "Only " + count + " bundled families cover Cyrillic. Expected at least 4.");
		assertTrue(categories.size() > 1, "Every bundled family that covers Cyrillic is in the same category: " + categories);
		assertTrue(FontFinder.covers(FontFinder.broadCoverageFontFamily, Script.Cyrillic),
				FontFinder.broadCoverageFontFamily + " must cover Cyrillic, since it is the family text falls back to.");
	}

	@Test
	public void someBundledFamiliesCoverGreek()
	{
		int count = 0;
		for (AvailableFont font : getBundledFonts())
		{
			if (FontFinder.covers(font.family, Script.Greek))
			{
				count++;
			}
		}
		assertTrue(count >= 2, "Only " + count + " bundled families cover Greek. Expected at least 2.");
		assertTrue(FontFinder.covers(FontFinder.broadCoverageFontFamily, Script.Greek));
	}

	@Test
	public void oneBundledFamilyCoversHan()
	{
		// Exactly one is the intent: a brush face so that Chinese map text has somewhere to go, not a range of CJK fonts.
		int count = 0;
		for (AvailableFont font : getBundledFonts())
		{
			if (FontFinder.covers(font.family, Script.Han))
			{
				count++;
			}
		}
		assertEquals(1, count, "Expected exactly one bundled family covering Han, found " + count + ".");
	}

	@Test
	public void everyFontFolderMapsToAKnownCategory()
	{
		String fontsFolder = Paths.get(Assets.getInstalledArtPackPath(), "fonts").toString();
		List<String> categoryFolderNames = Assets.listNonEmptySubFolders(fontsFolder);
		assertFalse(categoryFolderNames.isEmpty(), "No category folders under " + fontsFolder);

		for (String categoryFolderName : categoryFolderNames)
		{
			boolean matches = false;
			for (FontCategory category : FontCategory.values())
			{
				if (category.name().equalsIgnoreCase(categoryFolderName))
				{
					matches = true;
					break;
				}
			}
			assertTrue(matches, "The font folder '" + categoryFolderName + "' does not name a FontCategory.");
		}
	}

	@Test
	public void everyFamilyFolderHasAFontFileAndALicense()
	{
		String fontsFolder = Paths.get(Assets.getInstalledArtPackPath(), "fonts").toString();
		for (String categoryFolderName : Assets.listNonEmptySubFolders(fontsFolder))
		{
			String categoryFolder = Paths.get(fontsFolder, categoryFolderName).toString();
			for (String familyFolderName : Assets.listNonEmptySubFolders(categoryFolder))
			{
				String familyFolder = Paths.get(categoryFolder, familyFolderName).toString();
				List<Path> files = Assets.listFiles(familyFolder, null, null, null);

				boolean hasFontFile = false;
				boolean hasLicense = false;
				for (Path file : files)
				{
					String fileName = file.getFileName().toString().toLowerCase();
					if (fileName.endsWith(".ttf") || fileName.endsWith(".otf"))
					{
						hasFontFile = true;
					}
					for (String fragment : licenseFileNameFragments)
					{
						if (fileName.contains(fragment))
						{
							hasLicense = true;
						}
					}
				}

				assertTrue(hasFontFile, familyFolder + " contains no font file.");
				assertTrue(hasLicense, familyFolder + " contains no license file.");
			}
		}
	}

	@Test
	public void aliasTargetsResolve()
	{
		// An alias must name a family that is actually available, or it silently does nothing.
		assertEquals(FontFinder.houseFontFamily, FontFinder.resolveAlias("URW Chancery L"));
		assertTrue(FontFinder.isAvailable(FontFinder.resolveAlias("URW Chancery L")));
	}

	@Test
	public void anInstalledFamilyIsNotAliased()
	{
		// An alias is only consulted when the requested family is unavailable, so a machine that really has the original keeps using it.
		String installed = FontFinder.houseFontFamily;
		assertEquals(installed, FontFinder.resolveAlias(installed));
	}

	@Test
	public void aMissingFamilyWithNoAliasIsReturnedUnchanged()
	{
		assertEquals("A Font That Does Not Exist", FontFinder.resolveAlias("A Font That Does Not Exist"));
	}

	@Test
	public void drawingResolvesAnAliasedFamily()
	{
		Font requested = Font.create("URW Chancery L", FontStyle.Plain, 20);
		Font resolved = FontFinder.resolveForDrawing(requested, FontCategory.Script);
		assertEquals(FontFinder.houseFontFamily, resolved.getName());
		assertEquals(20f, resolved.getSize());
		assertEquals(FontStyle.Plain, resolved.getStyle());
	}

	@Test
	public void drawingSubstitutesOnlyWhenAFamilyIsNotInstalled()
	{
		Font present = Font.create(FontFinder.houseFontFamily, FontStyle.Bold, 14);
		assertEquals(FontFinder.houseFontFamily, FontFinder.resolveForDrawing(present, FontCategory.Script).getName());

		Font missing = Font.create("A Font That Does Not Exist", FontStyle.Bold, 14);
		Font resolved = FontFinder.resolveForDrawing(missing, FontCategory.Script);
		assertFalse(resolved.getName().equals("A Font That Does Not Exist"));
		assertTrue(FontFinder.isAvailable(resolved.getName()));
		assertEquals(FontStyle.Bold, resolved.getStyle());
		assertEquals(14f, resolved.getSize());
	}

	@Test
	public void drawingDoesNotSubstituteForMissingGlyphs()
	{
		// A font that is present but cannot draw the text is returned unchanged, so that the map draws boxes rather than quietly using a
		// font its author did not choose.
		Font font = Font.create(FontFinder.houseFontFamily, FontStyle.Plain, 12);
		Font resolved = FontFinder.resolveForDrawing(font, FontCategory.Script);
		assertEquals(FontFinder.houseFontFamily, resolved.getName());
	}

	@Test
	public void categoryComesFromTheFolderForBundledFonts()
	{
		assertEquals(FontCategory.Script, FontFinder.guessCategory(FontFinder.houseFontFamily));
	}

	@Test
	public void categoryOfWellKnownDecorativeSystemFontsIsScript()
	{
		// These are the families existing maps are full of, because of the substitution chain that used to run at load time. The name
		// heuristic has nothing to work with for them, so they are seeded.
		assertEquals(FontCategory.Script, FontFinder.guessCategory("Gabriola"));
		assertEquals(FontCategory.Script, FontFinder.guessCategory("Apple Chancery"));
		assertEquals(FontCategory.Display, FontFinder.guessCategory("Papyrus"));
	}

	@Test
	public void categoryHeuristicReadsNames()
	{
		assertEquals(FontCategory.Sans, FontFinder.guessCategory("Some Sans Family"));
		assertEquals(FontCategory.Script, FontFinder.guessCategory("Some Handwriting Family"));
		assertEquals(FontCategory.Serif, FontFinder.guessCategory("Some Unremarkable Family"));
	}

	@Test
	public void scriptOfNamesTheWritingSystem()
	{
		assertEquals(Script.Latin, Script.of('A'));
		assertEquals(Script.Cyrillic, Script.of("Я".codePointAt(0)));
		assertEquals(Script.Greek, Script.of("Ω".codePointAt(0)));
		assertEquals(Script.Han, Script.of("中".codePointAt(0)));
		assertNull(Script.of('1'), "Digits belong to no script in particular.");
	}

	@Test
	public void findMissingScriptNamesWhatAFontCannotDraw()
	{
		assertNull(FontFinder.findMissingScript(FontFinder.houseFontFamily, "Ordinary Latin text"));
	}

	@Test
	public void getScriptsFindsEveryScriptInText()
	{
		Set<Script> scripts = FontFinder.getScripts("Abc Абв 123");
		assertEquals(new HashSet<>(List.of(Script.Latin, Script.Cyrillic)), scripts);
	}

	@Test
	public void listAvailableFontsPutsBundledFirst()
	{
		List<AvailableFont> fonts = FontFinder.listAvailableFonts();
		assertFalse(fonts.isEmpty());

		boolean seenSystemFont = false;
		for (AvailableFont font : fonts)
		{
			if (font.source == FontSource.System)
			{
				seenSystemFont = true;
			}
			else
			{
				assertFalse(seenSystemFont, "Bundled font " + font.family + " came after a system font.");
			}
		}
	}

	@Test
	public void aFamilyThatIsNotAvailableAtAllReportsAsASystemFont()
	{
		// It is a system font, just not this system's. Reporting it that way keeps a map's font source steady across machines.
		assertEquals(FontSource.System, FontFinder.getSource("A Font That Does Not Exist"));
	}

	private static List<AvailableFont> getBundledFonts()
	{
		List<AvailableFont> result = new ArrayList<>();
		for (AvailableFont font : FontFinder.listAvailableFonts())
		{
			if (font.source == FontSource.Bundled)
			{
				result.add(font);
			}
		}
		return result;
	}
}
