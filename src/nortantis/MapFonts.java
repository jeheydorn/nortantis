package nortantis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;

import nortantis.MapSettings.ThemeFontType;
import nortantis.platform.Font;
import nortantis.util.Assets;

/**
 * What a map's fonts add up to: which families it names, which of them this machine cannot supply, and how to swap one family for another.
 *
 * <p>
 * These read a map's settings rather than belong to them. A map records the font each kind of text is drawn in; which families that adds up
 * to, and whether this machine has them, depends on the machine and on every label in the map, and is worked out when something asks.
 */
public class MapFonts
{
	/**
	 * One font a map names that this machine cannot render as the author intended.
	 */
	public static class FontProblem
	{
		public final String family;
		/** The kinds of text drawn in this family. */
		public final List<ThemeFontType> usedByThemeFontTypes;
		/** How many individual labels choose this family for themselves, or 0 when none do. */
		public final int individualLabelCount;
		/** The map's text this family was drawing, which a replacement has to be able to draw too. */
		public final String textToDraw;
		/**
		 * The art pack the map says this family came from, when that art pack is not installed. Null when the map records no art pack for
		 * it, or when the art pack is installed and simply no longer supplies the family.
		 */
		public final String missingArtPack;

		public FontProblem(String family, List<ThemeFontType> usedByThemeFontTypes, int individualLabelCount, String textToDraw,
				String missingArtPack)
		{
			this.family = family;
			this.usedByThemeFontTypes = usedByThemeFontTypes;
			this.individualLabelCount = individualLabelCount;
			this.textToDraw = textToDraw;
			this.missingArtPack = missingArtPack;
		}
	}

	/**
	 * Result of {@link #findProblems(MapSettings)}.
	 */
	public static class MissingFontInfo
	{
		public final List<FontProblem> problems;

		public MissingFontInfo(List<FontProblem> problems)
		{
			this.problems = problems;
		}

		public boolean isEmpty()
		{
			return problems.isEmpty();
		}
	}

	/**
	 * Every font family the given map names, in the order they are first met, as written in the map. That is the theme fonts plus the font
	 * of every individual label that overrides its type's font, so a family appears whether it is used once or everywhere.
	 */
	public static List<String> getFamiliesUsed(MapSettings settings)
	{
		return new ArrayList<>(gatherUsage(settings).usedByByFamily.keySet());
	}

	/**
	 * Finds the font families the given map names that this machine does not have. Results are grouped by family rather than by field, so a
	 * map whose theme fonts are all the same family produces one problem rather than one per field.
	 *
	 * <p>
	 * A font that is installed but has no glyphs for some of the map's text is not reported. Nothing about this machine caused that, and
	 * nothing about this machine can fix it: the map's author chose a font that cannot draw their own text, and they will see that the
	 * moment the map draws. Interrupting everyone who opens the map to say so would be reporting the author's decision as this reader's
	 * problem.
	 */
	public static MissingFontInfo findProblems(MapSettings settings)
	{
		FontUsage usage = gatherUsage(settings);

		List<FontProblem> problems = new ArrayList<>();
		for (String family : usage.usedByByFamily.keySet())
		{
			if (FontFinder.isAvailable(FontFinder.resolveAlias(family)))
			{
				continue;
			}

			String artPack = settings.getFontArtPack(family);
			if (artPack != null && Assets.artPackExists(artPack, settings.customImagesPath))
			{
				artPack = null;
			}

			// The text this family was drawing is what a replacement has to be able to draw, so that swapping a missing font does not
			// silently trade it for one with no glyphs for the map's labels.
			problems.add(new FontProblem(family, new ArrayList<>(usage.usedByByFamily.get(family)),
					usage.overrideCountByFamily.getOrDefault(family, 0), usage.textByFamily.get(family).toString(), artPack));
		}

		return new MissingFontInfo(problems);
	}

	/**
	 * Replaces every theme font and every per-text font override whose family is a key in {@code replacements}, keeping each one's own style
	 * and size.
	 */
	public static void applySubstitution(MapSettings settings, Map<String, String> replacements)
	{
		Map<String, String> replacementsByLowerCase = new HashMap<>();
		for (Entry<String, String> entry : replacements.entrySet())
		{
			if (!StringUtils.isEmpty(entry.getValue()))
			{
				replacementsByLowerCase.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
			}
		}

		for (ThemeFontType type : ThemeFontType.values())
		{
			Font replaced = replaceFamily(settings.getThemeFont(type), replacementsByLowerCase);
			if (replaced != null)
			{
				settings.setThemeFont(type, replaced);
			}
		}

		if (settings.edits != null && settings.edits.text != null)
		{
			for (MapText text : settings.edits.text)
			{
				if (text != null && text.fontOverride != null)
				{
					Font replaced = replaceFamily(text.fontOverride, replacementsByLowerCase);
					if (replaced != null)
					{
						text.fontOverride = replaced;
					}
				}
			}
		}
	}

	private static Font replaceFamily(Font font, Map<String, String> replacementsByLowerCase)
	{
		if (font == null)
		{
			return null;
		}
		String replacement = replacementsByLowerCase.get(font.getName().toLowerCase(Locale.ROOT));
		return replacement == null ? null : Font.create(replacement, font.getStyle(), font.getSize());
	}

	/**
	 * Every font family a map names, gathered in one pass: what each is used for and the text it has to draw. Families are keyed by the name
	 * as written in the map, and one family named two ways is one entry.
	 */
	private static class FontUsage
	{
		final Map<String, String> familyAsWrittenByLowerCase = new LinkedHashMap<>();
		final Map<String, List<ThemeFontType>> usedByByFamily = new LinkedHashMap<>();
		final Map<String, StringBuilder> textByFamily = new LinkedHashMap<>();
		final Map<String, Integer> overrideCountByFamily = new LinkedHashMap<>();
	}

	private static FontUsage gatherUsage(MapSettings settings)
	{
		FontUsage usage = new FontUsage();

		for (Entry<ThemeFontType, Font> entry : settings.getThemeFonts().entrySet())
		{
			if (entry.getValue() == null)
			{
				continue;
			}
			String family = recordFamily(entry.getValue().getName(), usage);
			usage.usedByByFamily.get(family).add(entry.getKey());
		}

		if (settings.edits != null && settings.edits.text != null)
		{
			for (MapText text : settings.edits.text)
			{
				if (text == null || StringUtils.isEmpty(text.value))
				{
					continue;
				}

				String family;
				if (text.fontOverride != null)
				{
					family = recordFamily(text.fontOverride.getName(), usage);
					usage.overrideCountByFamily.merge(family, 1, Integer::sum);
				}
				else
				{
					Font themeFont = settings.getThemeFont(MapSettings.getThemeFontTypeForText(text.type));
					if (themeFont == null)
					{
						continue;
					}
					family = recordFamily(themeFont.getName(), usage);
				}
				usage.textByFamily.get(family).append(text.value);
			}
		}

		return usage;
	}

	private static String recordFamily(String familyAsWritten, FontUsage usage)
	{
		// Locale.ROOT because this key only ever groups two spellings of one family. The default locale would make that grouping depend on
		// the machine: in Turkish, "Iosevka" and "iosevka" lower case to different strings and would be asked about twice.
		String canonical = usage.familyAsWrittenByLowerCase.computeIfAbsent(familyAsWritten.toLowerCase(Locale.ROOT), key -> familyAsWritten);
		usage.usedByByFamily.computeIfAbsent(canonical, key -> new ArrayList<>());
		usage.textByFamily.computeIfAbsent(canonical, key -> new StringBuilder());
		return canonical;
	}
}
