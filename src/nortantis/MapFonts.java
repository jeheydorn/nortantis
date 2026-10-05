package nortantis;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;

import nortantis.platform.Font;
import nortantis.platform.FontStyle;
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
		/** How many of the map's labels are drawn in this family, by type, in type order. Holds only the types with at least one. */
		public final Map<TextType, Integer> labelCountsByType;
		/** The types of text whose style for new text uses this family. */
		public final List<TextType> newTextTypes;
		/**
		 * The map's text this family was drawing, gathered under the style it is drawn in, which a replacement has to be able to draw too. A
		 * family's faces need not have the same glyphs, so the text a map draws in bold has to be judged against a candidate's bold face
		 * rather than against the whole family.
		 */
		public final Map<FontStyle, String> textToDrawByStyle;
		/**
		 * The art pack the map says this family came from, when that art pack is not installed. Null when the map records no art pack for
		 * it, or when the art pack is installed and simply no longer supplies the family.
		 */
		public final String missingArtPack;

		public FontProblem(String family, Map<TextType, Integer> labelCountsByType, List<TextType> newTextTypes,
				Map<FontStyle, String> textToDrawByStyle, String missingArtPack)
		{
			this.family = family;
			this.labelCountsByType = labelCountsByType;
			this.newTextTypes = newTextTypes;
			this.textToDrawByStyle = textToDrawByStyle;
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
	 * Every font family the given map names, in the order they are first met, as written in the map. That is the fonts of the styles for new
	 * text plus the font of every label, so a family appears whether it is used once or everywhere.
	 */
	public static List<String> getFamiliesUsed(MapSettings settings)
	{
		return new ArrayList<>(gatherUsage(settings).newTextTypesByFamily.keySet());
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
		for (String family : usage.newTextTypesByFamily.keySet())
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
			problems.add(new FontProblem(family, new EnumMap<>(usage.labelCountsByFamily.get(family)), new ArrayList<>(usage.newTextTypesByFamily.get(family)),
					toTextByStyle(usage.textByFamilyAndStyle.get(family)), artPack));
		}

		return new MissingFontInfo(problems);
	}

	/**
	 * Replaces every font, in the styles for new text and in every label, whose family is a key in {@code replacements}, keeping each one's
	 * own bold/italic and size.
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

		for (TextType type : TextType.values())
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
				if (text != null && text.style != null)
				{
					Font replaced = replaceFamily(text.style.font, replacementsByLowerCase);
					if (replaced != null)
					{
						text.style.font = replaced;
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
		final Map<String, List<TextType>> newTextTypesByFamily = new LinkedHashMap<>();
		/** The text each family draws, kept apart by the style it is drawn in, since a family's faces need not have the same glyphs. */
		final Map<String, Map<FontStyle, StringBuilder>> textByFamilyAndStyle = new LinkedHashMap<>();
		final Map<String, EnumMap<TextType, Integer>> labelCountsByFamily = new LinkedHashMap<>();
	}

	private static Map<FontStyle, String> toTextByStyle(Map<FontStyle, StringBuilder> textByStyle)
	{
		Map<FontStyle, String> result = new LinkedHashMap<>();
		for (Entry<FontStyle, StringBuilder> entry : textByStyle.entrySet())
		{
			result.put(entry.getKey(), entry.getValue().toString());
		}
		return result;
	}

	private static FontUsage gatherUsage(MapSettings settings)
	{
		FontUsage usage = new FontUsage();

		for (Entry<TextType, Font> entry : settings.getThemeFonts().entrySet())
		{
			if (entry.getValue() == null)
			{
				continue;
			}
			String family = recordFamily(entry.getValue().getName(), usage);
			usage.newTextTypesByFamily.get(family).add(entry.getKey());
		}

		if (settings.edits != null && settings.edits.text != null)
		{
			for (MapText text : settings.edits.text)
			{
				if (text == null || StringUtils.isEmpty(text.value))
				{
					continue;
				}

				if (text.style == null || text.style.font == null)
				{
					continue;
				}
				Font font = text.style.font;
				String family = recordFamily(font.getName(), usage);
				usage.labelCountsByFamily.get(family).merge(text.type, 1, Integer::sum);
				usage.textByFamilyAndStyle.get(family).computeIfAbsent(font.getStyle(), key -> new StringBuilder()).append(text.value);
			}
		}

		return usage;
	}

	private static String recordFamily(String familyAsWritten, FontUsage usage)
	{
		// Locale.ROOT because this key only ever groups two spellings of one family. The default locale would make that grouping depend on
		// the machine: in Turkish, "Iosevka" and "iosevka" lower case to different strings and would be asked about twice.
		String canonical = usage.familyAsWrittenByLowerCase.computeIfAbsent(familyAsWritten.toLowerCase(Locale.ROOT), key -> familyAsWritten);
		usage.newTextTypesByFamily.computeIfAbsent(canonical, key -> new ArrayList<>());
		usage.labelCountsByFamily.computeIfAbsent(canonical, key -> new EnumMap<>(TextType.class));
		usage.textByFamilyAndStyle.computeIfAbsent(canonical, key -> new LinkedHashMap<>());
		return canonical;
	}
}
