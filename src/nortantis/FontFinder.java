package nortantis;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import nortantis.platform.Font;
import nortantis.platform.FontStyle;
import nortantis.platform.PlatformFactory;
import nortantis.swing.translation.Translation;
import nortantis.util.Assets;
import nortantis.util.Logger;

/**
 * Knows which font families this installation can draw with, where each one came from, and what to use when the family a map names will not
 * do.
 */
public class FontFinder
{
	public enum FontSource
	{
		Bundled, ArtPack, System
	}

	/**
	 * A writing system a font may or may not have glyphs for. Coverage is determined by drawing-time probing rather than by a table of
	 * which font covers what, so it cannot drift out of date and works the same for bundled, art pack and system fonts.
	 */
	public enum Script
	{
		Latin("AZaz"),
		// А б в я
		Cyrillic("Абвя"),
		// Α β γ ω
		Greek("Αβγω"),
		// 中 文 国
		Han("中文国"),
		// あ ア
		Kana("あア"),
		// 한 글
		Hangul("한글"),
		// ا ب
		Arabic("اب"),
		// א ב
		Hebrew("אב"),
		// ก ข
		Thai("กข");

		/** A few characters of this script, used to ask a font whether it covers the script at all. */
		public final String probeCharacters;

		Script(String probeCharacters)
		{
			this.probeCharacters = probeCharacters;
		}

		/**
		 * The script the given code point belongs to, or null if it belongs to no script in particular, as digits, spaces and most
		 * punctuation do.
		 */
		public static Script of(int codePoint)
		{
			Character.UnicodeScript unicodeScript;
			try
			{
				unicodeScript = Character.UnicodeScript.of(codePoint);
			}
			catch (IllegalArgumentException e)
			{
				return null;
			}

			switch (unicodeScript)
			{
			case LATIN:
				return Latin;
			case CYRILLIC:
				return Cyrillic;
			case GREEK:
				return Greek;
			case HAN:
				return Han;
			case HIRAGANA:
			case KATAKANA:
				return Kana;
			case HANGUL:
				return Hangul;
			case ARABIC:
				return Arabic;
			case HEBREW:
				return Hebrew;
			case THAI:
				return Thai;
			default:
				return null;
			}
		}
	}

	/**
	 * A font family available to this installation, along with where it came from.
	 */
	public static class AvailableFont
	{
		public final String family;
		public final FontSource source;
		/** The art pack the font came from, or null when it did not come from one. */
		public final String artPack;

		public AvailableFont(String family, FontSource source, String artPack)
		{
			this.family = family;
			this.source = source;
			this.artPack = artPack;
		}

		@Override
		public String toString()
		{
			return family + " (" + source + ")";
		}
	}

	/**
	 * The family Nortantis defaults to for a new map's text.
	 */
	public static final String houseFontFamily = "Z003";

	/**
	 * The family Nortantis draws its own text with - tool icons, canvas messages and the placeholders shown while a preview is drawing. It
	 * is a plain reading face rather than the house font, because chrome is furniture that has to stay legible at small sizes and out of the
	 * way, which is the opposite of what a map's lettering wants.
	 */
	public static final String chromeFontFamily = "EB Garamond";

	private static final String fontsFolderName = "fonts";
	private static final Set<String> fontFileExtensions = Set.of("ttf", "otf");
	private static final int probeFontSize = 12;

	/**
	 * Names that denote the same typeface as a family Nortantis bundles. An alias is only consulted when the requested family is not
	 * available, so a machine that really has the original keeps using it. Only exact equivalents belong here - a different typeface that
	 * merely looks similar must prompt instead, which is the point of the missing font dialog.
	 */
	private static final Map<String, String> aliasesByLowerCaseFamily = Map.of("urw chancery l", houseFontFamily);

	/**
	 * The family that always ships and covers Latin, Cyrillic and Greek, so it is the family to reach for when text needs a home and
	 * nothing more specific fits.
	 */
	public static final String broadCoverageFontFamily = "EB Garamond";

	/**
	 * The bundled families to reach for first when a map's font will not do, in preference order. A replacement is offered for a font
	 * nobody on this device can see any more, so matching what it looked like is not on the table; falling back to the family new maps
	 * are drawn in is, which leaves the map looking like a Nortantis map. The broad coverage family follows for text the house font has
	 * no glyphs for.
	 */
	static final List<String> preferredSubstituteFamilies = List.of(houseFontFamily, broadCoverageFontFamily);

	/** The platform's own reading face, for when nothing bundled or installed can draw a map's text. */
	private static final String logicalSerifFamily = "Serif";

	/** The platform's own interface face, for when nothing bundled can draw the language the app is running in. */
	private static final String logicalSansFamily = "SansSerif";

	/**
	 * Bundled families whose lettering is decorative enough that Nortantis never picks them on the user's behalf, even when one of them is
	 * the only bundled family covering a language's script. They are the right answer for a title someone chose them for and the wrong
	 * answer for a label or a button that nobody chose a font for at all. They still ship, and the font picker and the warning shown where
	 * text is typed both offer them.
	 */
	private static final Set<String> familiesWithADecorativeRegister = Set.of("Ma Shan Zheng");

	private static final Object initializationLock = new Object();
	private static volatile boolean isInitialized;

	// These three are replaced wholesale, never mutated in place, and only ever gain entries. They are volatile because an art pack
	// installed while the app runs replaces them after initialization, under the lock, while readers - the map drawing thread among them -
	// read them without it.
	/** Bundled and art pack fonts, keyed by lower case family name. */
	private static volatile Map<String, AvailableFont> registeredFontsByLowerCaseFamily;
	/**
	 * Lower case names of every family that can be drawn with. Cached because the platform's own lookup rebuilds and scans the whole list
	 * on every call, and the new code paths ask far more often than the old ones did.
	 */
	private static volatile Set<String> availableFamiliesLowerCase;
	private static volatile List<AvailableFont> availableFonts;

	private static final Map<String, String> substitutesByRequest = new ConcurrentHashMap<>();
	private static final String noSubstituteFound = "";

	/**
	 * Registers the fonts in every art pack that is known without a map being open. Idempotent, and safe to call from any thread. Every
	 * public method calls it, so no entry point has to remember to.
	 */
	public static void ensureInitialized()
	{
		if (isInitialized)
		{
			return;
		}

		synchronized (initializationLock)
		{
			if (isInitialized)
			{
				return;
			}

			registeredFontsByLowerCaseFamily = new LinkedHashMap<>();
			registerArtPackFonts(null);
			isInitialized = true;
		}
	}

	/**
	 * Registers the fonts in every art pack, including any pack installed since the last call and the given custom images folder, which is
	 * chosen per map rather than known when the app starts. Safe to call repeatedly and from any thread.
	 *
	 * <p>
	 * Fonts already registered stay registered: the platform has no way to unregister a font, so the set of registered families only ever
	 * grows while the app runs. Nothing may assume a registered family can stop being available. Families the device supplies itself carry
	 * no such promise, since they are re-read from the platform on each call.
	 *
	 * @param customImagesFolder
	 *            The custom images folder to look in as well, or null to look only in the installed art packs.
	 */
	public static void registerFontsFromArtPacks(String customImagesFolder)
	{
		ensureInitialized();
		synchronized (initializationLock)
		{
			registerArtPackFonts(customImagesFolder);
		}
	}

	private static void registerArtPackFonts(String customImagesFolder)
	{
		Map<String, AvailableFont> registered = new LinkedHashMap<>(registeredFontsByLowerCaseFamily);
		int countBefore = registered.size();

		registerFontsInArtPack(Assets.installedArtPack, Assets.getInstalledArtPackPath(), FontSource.Bundled, registered);
		for (String artPack : Assets.listArtPacks(false))
		{
			if (artPack.equals(Assets.installedArtPack))
			{
				continue;
			}
			Path artPackPath = Assets.getArtPackPath(artPack, null);
			if (artPackPath != null)
			{
				registerFontsInArtPack(artPack, artPackPath.toString(), FontSource.ArtPack, registered);
			}
		}

		Path customPath = Assets.getArtPackPath(Assets.customArtPack, customImagesFolder);
		if (customPath != null)
		{
			registerFontsInArtPack(Assets.customArtPack, customPath.toString(), FontSource.ArtPack, registered);
		}

		if (registered.size() == countBefore && availableFonts != null)
		{
			return;
		}

		registeredFontsByLowerCaseFamily = registered;
		rebuildAvailableFonts();
	}

	private static void registerFontsInArtPack(String artPack, String artPackPath, FontSource source, Map<String, AvailableFont> result)
	{
		registerFontsInFolder(Paths.get(artPackPath, fontsFolderName).toString(), source, artPack, result);
	}

	/**
	 * Registers every font in an art pack's fonts folder, which holds one folder per family in the same shape as the folders that group an
	 * art pack's icons.
	 */
	private static void registerFontsInFolder(String fontsFolderPath, FontSource source, String artPack, Map<String, AvailableFont> result)
	{
		for (String familyFolderName : Assets.listNonEmptySubFolders(fontsFolderPath))
		{
			String familyFolderPath = Paths.get(fontsFolderPath, familyFolderName).toString();

			for (Path fontFile : Assets.listFiles(familyFolderPath, null, null, fontFileExtensions))
			{
				// The family name comes from the font file rather than from the folder, because that is the name the platform will
				// answer to and so the name that must go into a map's settings.
				String family = PlatformFactory.getInstance().registerFont(fontFile.toString());
				if (family == null)
				{
					// registerFont logged the file it could not load. An art pack must never be able to stop the app from starting.
					continue;
				}
				result.putIfAbsent(family.toLowerCase(Locale.ROOT), new AvailableFont(family, source, artPack));
			}
		}
	}

	private static void rebuildAvailableFonts()
	{
		List<String> installedFamilies = PlatformFactory.getInstance().listFontFamilies();

		Set<String> lowerCaseNames = new HashSet<>();
		for (String family : installedFamilies)
		{
			lowerCaseNames.add(family.toLowerCase(Locale.ROOT));
		}
		lowerCaseNames.addAll(registeredFontsByLowerCaseFamily.keySet());
		availableFamiliesLowerCase = Collections.unmodifiableSet(lowerCaseNames);

		List<AvailableFont> result = new ArrayList<>(registeredFontsByLowerCaseFamily.values());
		result.sort((font1, font2) -> font1.family.compareToIgnoreCase(font2.family));

		List<String> systemFamilies = new ArrayList<>();
		for (String family : installedFamilies)
		{
			if (!registeredFontsByLowerCaseFamily.containsKey(family.toLowerCase(Locale.ROOT)))
			{
				systemFamilies.add(family);
			}
		}
		systemFamilies.sort(String::compareToIgnoreCase);
		for (String family : systemFamilies)
		{
			result.add(new AvailableFont(family, FontSource.System, null));
		}

		availableFonts = Collections.unmodifiableList(result);
	}

	/**
	 * Every family this installation can draw with, bundled and art pack fonts first, then the machine's own.
	 */
	public static List<AvailableFont> listAvailableFonts()
	{
		ensureInitialized();
		return availableFonts;
	}

	public static boolean isAvailable(String family)
	{
		ensureInitialized();
		return isAvailableInternal(family);
	}

	private static boolean isAvailableInternal(String family)
	{
		return family != null && availableFamiliesLowerCase.contains(family.toLowerCase(Locale.ROOT));
	}

	/**
	 * Resolves a family name that denotes a bundled font under another name. Returns the given name when it is available or has no alias,
	 * so that a machine which really has the original keeps using it.
	 */
	public static String resolveAlias(String family)
	{
		ensureInitialized();
		if (family == null || isAvailableInternal(family))
		{
			return family;
		}

		String alias = aliasesByLowerCaseFamily.get(family.toLowerCase(Locale.ROOT));
		if (alias != null && isAvailableInternal(alias))
		{
			return alias;
		}
		return family;
	}

	/**
	 * Which source a family came from. A family that is both bundled and installed on the machine reports as bundled, because that is the
	 * more useful thing to tell the user: it means everyone opening the map has a font of that name. A family that is not available at all
	 * reports as a system font, so that a map's font source doesn't change depending on who opens it.
	 *
	 * <p>
	 * This says where the font Nortantis offers came from, not which file the platform draws. The platform refuses to register a family it
	 * already has installed, so on a machine with its own copy of that name, that copy is what draws.
	 */
	public static FontSource getSource(String family)
	{
		ensureInitialized();
		AvailableFont registered = getRegistered(family);
		return registered != null ? registered.source : FontSource.System;
	}

	/**
	 * The art pack a family came from, or null for a font this computer supplies rather than an art pack.
	 */
	public static String getArtPack(String family)
	{
		ensureInitialized();
		AvailableFont registered = getRegistered(family);
		return registered == null ? null : registered.artPack;
	}

	private static AvailableFont getRegistered(String family)
	{
		if (family == null)
		{
			return null;
		}
		return registeredFontsByLowerCaseFamily.get(family.toLowerCase(Locale.ROOT));
	}

	/**
	 * The font to actually draw with. Resolves aliases, and substitutes only when the family is not installed at all. Glyph coverage is
	 * deliberately not considered: a font that is present but lacks glyphs for a map's text is returned unchanged, so that the map draws
	 * missing-glyph boxes rather than quietly rendering in a font its author did not choose.
	 */
	public static Font resolveForDrawing(Font requested)
	{
		ensureInitialized();
		if (requested == null)
		{
			return null;
		}

		String family = requested.getName();
		String resolved = resolveAlias(family);
		if (!isAvailableInternal(resolved))
		{
			String preferred = firstAvailable(preferredSubstituteFamilies);
			resolved = preferred != null ? preferred : logicalSerifFamily;
		}

		if (resolved.equals(family))
		{
			return requested;
		}
		return Font.create(resolved, requested.getStyle(), requested.getSize());
	}

	/**
	 * The best replacement to offer the user for a font that is missing or cannot draw the given text, or null if nothing available fits.
	 *
	 * @param sampleText
	 *            Text the replacement must be able to draw, or null to not require any particular coverage.
	 */
	public static String chooseSubstitute(String missingFamily, String sampleText)
	{
		ensureInitialized();

		// The answer depends on which scripts are missing rather than on the particular words, so memoizing by script keeps this off the
		// hot path when it is called per keystroke.
		String key = (missingFamily == null ? "" : missingFamily.toLowerCase(Locale.ROOT)) + "|" + getScripts(sampleText);
		String cached = substitutesByRequest.get(key);
		if (cached != null)
		{
			return cached.equals(noSubstituteFound) ? null : cached;
		}

		String substitute = findSubstitute(missingFamily, sampleText);
		substitutesByRequest.put(key, substitute == null ? noSubstituteFound : substitute);
		return substitute;
	}

	private static String findSubstitute(String missingFamily, String sampleText)
	{
		String alias = missingFamily == null ? null : aliasesByLowerCaseFamily.get(missingFamily.toLowerCase(Locale.ROOT));
		if (alias != null && isAvailableInternal(alias) && canDisplay(alias, sampleText))
		{
			return alias;
		}

		for (String family : preferredSubstituteFamilies)
		{
			if (isAvailableInternal(family) && canDisplay(family, sampleText))
			{
				return family;
			}
		}

		// availableFonts puts bundled fonts first, so this prefers a portable answer without needing to say so.
		for (AvailableFont font : availableFonts)
		{
			if (canDisplay(font.family, sampleText))
			{
				return font.family;
			}
		}

		return canDisplay(logicalSerifFamily, sampleText) ? logicalSerifFamily : null;
	}

	/**
	 * Whether the given family has glyphs for every character of the given text. Text that is null or empty is considered drawable by
	 * anything.
	 */
	public static boolean canDisplay(String family, String text)
	{
		ensureInitialized();
		if (text == null || text.isEmpty())
		{
			return true;
		}
		return createProbeFont(family).canDisplayUpTo(text) == -1;
	}

	/**
	 * The script of the first character in the given text that the given family cannot draw, or null when it can draw all of the text or
	 * when the character it cannot draw belongs to no script in particular.
	 */
	public static Script findMissingScript(String family, String text)
	{
		ensureInitialized();
		if (text == null || text.isEmpty())
		{
			return null;
		}

		int index = createProbeFont(family).canDisplayUpTo(text);
		if (index == -1)
		{
			return null;
		}
		return Script.of(text.codePointAt(index));
	}

	/**
	 * The bundled family a new map's fonts should default to for the given language: the house font when it can draw that language's
	 * script, and otherwise the highest-priority bundled family that can.
	 *
	 * <p>
	 * This applies only to the built-in default. A font someone chose - a theme's font, or a font already in a map - always wins over it,
	 * because overriding an explicit choice because of the recipient's UI language would be the app second-guessing the author.
	 *
	 * <p>
	 * The UI language is not the map's language, so this cannot catch every case. It is the preventive half of the answer to fonts that
	 * cannot draw a user's script; the warning shown where text is typed is the other half.
	 */
	public static String getDefaultFamilyForLanguage(String language)
	{
		ensureInitialized();
		Script script = getScriptForLanguage(language);
		if (script == null || covers(houseFontFamily, script))
		{
			return houseFontFamily;
		}

		// A newly generated map's labels are English whatever the UI language is, so the default has to draw Latin as well.
		String family = findBundledFamilyCovering(script, true);
		return family != null ? family : houseFontFamily;
	}

	/**
	 * The bundled family Nortantis draws its own text with - tool icons, canvas messages and the placeholders shown while a preview is
	 * drawing - for the given language.
	 *
	 * <p>
	 * Chrome is allowed to fall back silently to a font that can draw it, unlike map text, because nobody chose these fonts and no map
	 * records them, and it carries no portability promise either - two people running different builds seeing slightly different button
	 * lettering costs nothing.
	 */
	public static String getChromeFontFamily(String language)
	{
		ensureInitialized();
		Script script = getScriptForLanguage(language);
		if (script == null || covers(chromeFontFamily, script))
		{
			return chromeFontFamily;
		}

		// Unlike a new map's default font, chrome only ever draws text in the user's own language, so a family's Latin doesn't matter.
		String family = findBundledFamilyCovering(script, false);
		// Nothing bundled draws this language plainly enough for furniture, and decorative lettering on a button is worse than lettering
		// that varies by machine, so the platform's own interface face is the better answer.
		return family != null ? family : logicalSansFamily;
	}

	/**
	 * The chrome font for the language the user is running Nortantis in.
	 */
	public static String getChromeFontFamily()
	{
		return getChromeFontFamily(Translation.getEffectiveLocale().getLanguage());
	}

	/**
	 * The highest-priority bundled or art pack family that covers the given script and is plain enough to be chosen on the user's behalf,
	 * or null when there is none.
	 */
	private static String findBundledFamilyCovering(Script script, boolean mustAlsoDrawLatin)
	{
		for (AvailableFont font : availableFonts)
		{
			if (font.source == FontSource.System || familiesWithADecorativeRegister.contains(font.family))
			{
				continue;
			}
			if (mustAlsoDrawLatin && !covers(font.family, Script.Latin))
			{
				continue;
			}
			if (covers(font.family, script))
			{
				return font.family;
			}
		}
		return null;
	}

	/**
	 * The script a language is normally written in, or null when it is Latin or is not one Nortantis knows about.
	 */
	private static Script getScriptForLanguage(String language)
	{
		if (language == null)
		{
			return null;
		}

		return switch (language.toLowerCase(Locale.ROOT))
		{
			case "ru", "uk", "bg", "sr", "be", "mk" -> Script.Cyrillic;
			case "el" -> Script.Greek;
			case "zh" -> Script.Han;
			case "ja" -> Script.Kana;
			case "ko" -> Script.Hangul;
			case "ar", "fa", "ur" -> Script.Arabic;
			case "he", "iw" -> Script.Hebrew;
			case "th" -> Script.Thai;
			default -> null;
		};
	}

	/**
	 * Whether the given family has glyphs for the given script.
	 */
	public static boolean covers(String family, Script script)
	{
		ensureInitialized();
		return createProbeFont(family).canDisplayUpTo(script.probeCharacters) == -1;
	}

	/**
	 * The scripts present in the given text.
	 */
	public static Set<Script> getScripts(String text)
	{
		Set<Script> scripts = EnumSet.noneOf(Script.class);
		if (text == null)
		{
			return scripts;
		}

		text.codePoints().forEach(codePoint ->
		{
			Script script = Script.of(codePoint);
			if (script != null)
			{
				scripts.add(script);
			}
		});
		return scripts;
	}

	private static Font createProbeFont(String family)
	{
		return Font.create(family, FontStyle.Plain, probeFontSize);
	}

	private static String firstAvailable(List<String> families)
	{
		if (families == null)
		{
			return null;
		}
		for (String family : families)
		{
			if (isAvailableInternal(family))
			{
				return family;
			}
		}
		return null;
	}

}
