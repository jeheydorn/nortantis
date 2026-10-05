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
 * The themes the user has: themes inside art packs, including the installed art pack, and themes the user installed in their own themes
 * folder. The lists are read from disk each time they are asked for, since a theme can be installed by another instance of Nortantis while
 * this one is running.
 */
public class ThemeCatalog
{
	/**
	 * Where a theme comes from.
	 */
	public enum Source
	{
		ArtPack, User
	}

	/**
	 * A theme that can be loaded.
	 */
	public static final class Entry
	{
		public final Source source;
		/** The art pack the theme is inside, or null if it isn't in one. */
		public final String artPack;
		/** The theme file. */
		public final Path path;
		public final String name;

		private Entry(Source source, String artPack, Path path, String name)
		{
			this.source = source;
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
			return source == other.source && Objects.equals(artPack, other.artPack) && Objects.equals(path, other.path);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(source, artPack, path);
		}
	}

	/**
	 * Whether the theme is one of those that come with Nortantis, in the installed art pack.
	 */
	public static boolean isFromInstalledArtPack(Entry entry)
	{
		return entry != null && entry.source == Source.ArtPack && Assets.installedArtPack.equals(entry.artPack);
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
				result.add(new Entry(Source.ArtPack, artPack, path, FilenameUtils.getBaseName(path.toString())));
			}
		}
		result.sort((first, second) -> first.name.compareToIgnoreCase(second.name));
		return result;
	}

	public static List<Entry> listUserThemes()
	{
		List<Entry> result = new ArrayList<>();
		Path folder = Assets.getUserThemesFolder();
		if (!folder.toFile().isDirectory())
		{
			return result;
		}
		for (Path path : Assets.listFiles(folder.toString(), null, null, null))
		{
			if (isThemeFile(path))
			{
				result.add(new Entry(Source.User, null, path, FilenameUtils.getBaseName(path.toString())));
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

	/**
	 * Every theme the user has: art pack themes grouped by art pack, then the user's own themes.
	 */
	public static List<Entry> listAllThemes(String customImagesFolder)
	{
		List<Entry> result = new ArrayList<>();
		result.addAll(listArtPackThemes(customImagesFolder));
		result.addAll(listUserThemes());
		return result;
	}

	public static boolean isThemeFile(Path path)
	{
		return path != null && FilenameUtils.getExtension(path.toString()).equalsIgnoreCase(MapSettings.themeFileExtension);
	}

	/**
	 * Loads a theme.
	 *
	 * @throws MapSettings.ThemeFromNewerVersionException
	 *             If the theme was made in a newer version of Nortantis.
	 */
	public static MapSettings load(Entry entry)
	{
		return MapSettings.readThemeFile(entry.path.toString());
	}

	/**
	 * Chooses the theme "Random" means: one of the given art pack's themes when it has any, which were designed alongside its art, and
	 * otherwise one of the installed art pack's themes. The user's own themes are only used when chosen.
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
	 * theme file is inside, otherwise null.
	 */
	public static String resolveArtPack(Entry entry, MapSettings theme, String customImagesFolder)
	{
		if (theme != null && theme.themeGeneration != null && theme.themeGeneration.artPack != null && Assets.artPackExists(theme.themeGeneration.artPack, customImagesFolder))
		{
			return theme.themeGeneration.artPack;
		}
		if (entry != null && entry.artPack != null)
		{
			return entry.artPack;
		}
		return null;
	}
}
