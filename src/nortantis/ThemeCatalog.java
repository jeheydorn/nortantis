package nortantis;

import nortantis.swing.translation.Translation;
import nortantis.util.Assets;
import nortantis.util.ProbabilityHelper;
import org.apache.commons.io.FilenameUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * The themes in art packs, including the installed art pack. A theme is a map (.nort) file in an art pack's themes folder, with or without
 * edits, whose look new random maps are made from. The lists are read from disk each time they are asked for, since an art pack can be
 * added while Nortantis is running.
 *
 * <p>
 * A theme may only rely on its own art pack and the installed art pack, so that it works the same whichever other art packs are installed.
 * Loading a theme that relies on another art pack fails with an {@link InvalidThemeException}.
 */
public class ThemeCatalog
{
	/**
	 * Thrown when loading a theme that relies on an art pack other than its own and the installed one. Its message is translated, and says
	 * what is wrong for whoever made the theme.
	 */
	public static class InvalidThemeException extends RuntimeException
	{
		public InvalidThemeException(String message)
		{
			super(message);
		}
	}

	/**
	 * A theme that can be loaded.
	 */
	public static final class Entry
	{
		/** The art pack the theme is inside. */
		public final String artPack;
		/** The theme's map file. */
		public final Path path;
		public final String name;

		private Entry(String artPack, Path path, String name)
		{
			this.artPack = artPack;
			this.path = path;
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}

		@Override
		public boolean equals(Object obj)
		{
			if (this == obj)
			{
				return true;
			}
			if (!(obj instanceof Entry other))
			{
				return false;
			}
			return Objects.equals(artPack, other.artPack) && Objects.equals(path, other.path);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(artPack, path);
		}
	}

	/**
	 * Whether the theme is one of those that come with Nortantis, in the installed art pack.
	 */
	public static boolean isFromInstalledArtPack(Entry entry)
	{
		return entry != null && Assets.installedArtPack.equals(entry.artPack);
	}

	public static List<Entry> listThemesForArtPack(String artPack, String customImagesFolder)
	{
		List<Entry> result = new ArrayList<>();
		Path folder = Assets.getThemesFolderForArtPack(artPack, customImagesFolder);
		if (folder == null)
		{
			return result;
		}
		for (Path path : Assets.listFiles(folder.toString(), null, null, null))
		{
			if (isThemeFile(path))
			{
				result.add(new Entry(artPack, path, FilenameUtils.getBaseName(path.toString())));
			}
		}
		result.sort((first, second) -> first.name.compareToIgnoreCase(second.name));
		return result;
	}

	/**
	 * The themes new maps with the given art pack can use: the art pack's own, or the installed art pack's if it has none.
	 */
	public static List<Entry> listThemesToChooseFrom(String artPack, String customImagesFolder)
	{
		List<Entry> themes = artPack == null ? new ArrayList<>() : listThemesForArtPack(artPack, customImagesFolder);
		if (themes.isEmpty())
		{
			themes = listThemesForArtPack(Assets.installedArtPack, customImagesFolder);
		}
		return themes;
	}

	public static boolean isThemeFile(Path path)
	{
		return path != null && FilenameUtils.getExtension(path.toString()).equalsIgnoreCase(MapSettings.fileExtension);
	}

	/**
	 * Loads a theme.
	 *
	 * @throws InvalidThemeException
	 *             If the theme relies on an art pack other than its own and the installed one.
	 */
	public static MapSettings load(Entry entry)
	{
		MapSettings theme = new MapSettings(entry.path.toString());
		List<String> problems = findArtPackProblems(entry.artPack, theme);
		if (!problems.isEmpty())
		{
			throw new InvalidThemeException(Translation.get("theme.invalid", entry.name, entry.artPack, entry.path) + "\n\n" + String.join("\n\n", problems));
		}
		return theme;
	}

	/**
	 * Describes each way the given theme relies on an art pack other than the one it's in and the installed one: its theme randomization
	 * art pack, and the art packs of the fonts for new text. The fonts of text already in the theme don't matter, since new maps don't
	 * keep it.
	 *
	 * @return The translated descriptions, or an empty list if there are none.
	 */
	public static List<String> findArtPackProblems(String themeArtPack, MapSettings theme)
	{
		List<String> problems = new ArrayList<>();
		if (theme.themeGeneration != null && theme.themeGeneration.artPack != null && !isAllowedArtPack(theme.themeGeneration.artPack, themeArtPack))
		{
			problems.add(Translation.get("theme.invalid.randomizationArtPack", theme.themeGeneration.artPack, themeArtPack, Assets.installedArtPack,
					Translation.get("menu.edit"), Translation.get("randomizeTheme.title")));
		}
		if (theme.textStyleDefaults != null)
		{
			Set<String> familiesReported = new LinkedHashSet<>();
			for (Map.Entry<TextType, TextStyle> entry : theme.textStyleDefaults.entrySet())
			{
				if (entry.getValue() == null || entry.getValue().font == null)
				{
					continue;
				}
				String family = entry.getValue().font.getFamily();
				String fontArtPack = theme.getFontArtPack(family);
				if (fontArtPack != null && !isAllowedArtPack(fontArtPack, themeArtPack) && familiesReported.add(family))
				{
					problems.add(Translation.get("theme.invalid.fontArtPack", family, fontArtPack, themeArtPack, Assets.installedArtPack));
				}
			}
		}
		return problems;
	}

	private static boolean isAllowedArtPack(String artPack, String themeArtPack)
	{
		return artPack.equals(themeArtPack) || artPack.equals(Assets.installedArtPack);
	}

	/**
	 * Chooses one of the themes new maps with the given art pack can use (see {@link #listThemesToChooseFrom}).
	 *
	 * @throws IllegalStateException
	 *             If neither art pack has a theme.
	 */
	public static Entry chooseRandomTheme(Random rand, String artPack, String customImagesFolder)
	{
		List<Entry> themes = listThemesToChooseFrom(artPack, customImagesFolder);
		if (themes.isEmpty())
		{
			throw new IllegalStateException("The installed art pack has no themes, so there is no theme to generate a map from.");
		}
		return ProbabilityHelper.sampleUniform(rand, themes);
	}
}
