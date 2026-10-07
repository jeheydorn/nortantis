package nortantis;

import nortantis.util.Assets;
import nortantis.util.ProbabilityHelper;
import org.apache.commons.io.FilenameUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * The themes in art packs, including the installed art pack. A theme is a map (.nort) file in an art pack's themes folder, with or without
 * edits, whose look new random maps are made from. The lists are read from disk each time they are asked for, since an art pack can be
 * added while Nortantis is running.
 */
public class ThemeCatalog
{
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
	 * Every theme from every installed art pack, in art pack order.
	 */
	public static List<Entry> listArtPackThemes(String customImagesFolder)
	{
		List<Entry> result = new ArrayList<>();
		for (String artPack : Assets.listArtPacks(customImagesFolder != null && !customImagesFolder.isEmpty()))
		{
			result.addAll(listThemesForArtPack(artPack, customImagesFolder));
		}
		return result;
	}

	public static boolean isThemeFile(Path path)
	{
		return path != null && FilenameUtils.getExtension(path.toString()).equalsIgnoreCase(MapSettings.fileExtension);
	}

	/**
	 * Loads a theme.
	 */
	public static MapSettings load(Entry entry)
	{
		return new MapSettings(entry.path.toString());
	}

	/**
	 * Chooses the theme "Random" means: one of the given art pack's themes when it has any, which were designed alongside its art, and
	 * otherwise one of the installed art pack's themes.
	 *
	 * @throws IllegalStateException
	 *             If neither art pack has a theme.
	 */
	public static Entry chooseRandomTheme(Random rand, String artPack, String customImagesFolder)
	{
		List<Entry> themes = artPack == null ? new ArrayList<>() : listThemesForArtPack(artPack, customImagesFolder);
		if (themes.isEmpty())
		{
			themes = listThemesForArtPack(Assets.installedArtPack, customImagesFolder);
		}
		if (themes.isEmpty())
		{
			throw new IllegalStateException("The installed art pack has no themes, so there is no theme to generate a map from.");
		}
		return ProbabilityHelper.sampleUniform(rand, themes);
	}

	/**
	 * The art pack generating a map from the given theme should use: the theme's own art pack if it is installed, otherwise the art pack the
	 * theme file is inside.
	 */
	public static String resolveArtPack(Entry entry, MapSettings theme, String customImagesFolder)
	{
		if (theme != null && theme.themeGeneration != null && theme.themeGeneration.artPack != null && Assets.artPackExists(theme.themeGeneration.artPack, customImagesFolder))
		{
			return theme.themeGeneration.artPack;
		}
		return entry == null ? null : entry.artPack;
	}
}
