package nortantis.util;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import nortantis.BackgroundTextureResource;
import nortantis.DebugFlags;
import nortantis.ImageCache;
import nortantis.platform.Image;
import nortantis.platform.PlatformFactory;

public class Assets
{
	private final static String assetsPath = "assets";
	private final static String customArtPack = "custom";
	private final static String artPacksFolder = "art packs";
	public final static String installedArtPack = "nortantis";

	public static String getAssetsPath()
	{
		return assetsPath;
	}

	public static List<String> listArtPacks(boolean includeCustomArtPack)
	{
		List<String> result = new ArrayList<>();
		result.add(installedArtPack);

		// Add installed art packs.
		result.addAll(listNonEmptySubFolders(Paths.get(OSHelper.getAppDataPath().toString(), artPacksFolder).toString()));

		// Add custom images folder if the map has one.
		if (includeCustomArtPack)
		{
			result.add(customArtPack);
		}

		result.sort(String::compareTo);
		return result;
	}

	public static List<BackgroundTextureResource> listBackgroundTexturesForAllArtPacks(String customImagesFolder)
	{
		List<BackgroundTextureResource> result = new ArrayList<>();
		for (String artPack : Assets.listArtPacks(StringUtils.isNotEmpty(customImagesFolder)))
		{
			for (String textureName : Assets.listBackgroundTexturesForArtPack(artPack,
					FileHelper.replaceHomeFolderPlaceholder(customImagesFolder)))
			{
				result.add(new BackgroundTextureResource(artPack, textureName));
			}
		}

		return result;
	}

	public static List<String> listBackgroundTexturesForArtPack(String artPack, String customImagesFolder)
	{
		Path artPackPath = getArtPackPath(artPack, customImagesFolder);

		List<String> textureFiles;
		try
		{
			textureFiles = Files.list(artPackPath).filter(path -> !Files.isDirectory(path))
					.map(path -> FilenameUtils.getName(path.toString())).collect(Collectors.toList());
		}
		catch (IOException ex)
		{
			throw new RuntimeException(
					"Error while reading background textures from art pack " + artPack + ". Path read from: " + artPackPath, ex);
		}

		return textureFiles;
	}

	public static Path getArtPackPath(String artPack, String customImagesFolder)
	{
		if (artPack.equals(customArtPack))
		{
			return Paths.get(customImagesFolder);
		}

		if (artPack.equals(installedArtPack))
		{
			return Paths.get(Assets.getAssetsPath(), "background textures");
		}

		return Paths.get(OSHelper.getAppDataPath().toString(), artPacksFolder, artPack);
	}

	public static Path getResourcePath(BackgroundTextureResource resource, String customImagesFolder)
	{
		Path artPackPath = getArtPackPath(resource.artPack, customImagesFolder);
		return Paths.get(artPackPath.toString(), resource.fileName);
	}

	public static String[] listFileNames(String path)
	{
		return listFileNames(path, null, null);
	}

	public static String[] listFileNames(String path, String containsText, String endingText)
	{
		List<String> fileNames = listFiles(path, containsText, endingText).stream()
				.map(filePath -> FilenameUtils.getName(filePath.toString())).collect(Collectors.toList());
		return fileNames.toArray(new String[0]);
	}

	public static List<Path> listFiles(String folderPath, String containsText, String endingText)
	{
		if (isJarAsset(folderPath))
		{
			return listFilesFromJar(folderPath, containsText, endingText);
		}
		// TODO remove
		// Try to load as a resource from the jar
		// URL resource = Assets.class.getResource(FileHelper.convertToAssetPath(folderPath));
		// Path path;
		// if (resource != null)
		// {
		// try
		// {
		// path = Paths.get(resource.toURI());
		// }
		// catch (URISyntaxException e)
		// {
		// throw new RuntimeException("Error while trying to list resource files in folder: " + folderPath, e);
		// }
		// }
		// else
		// {
		// path = Paths.get(folderPath);
		// }
		//
		// File[] files = new File(path.toString())

		File[] files = new File(folderPath.toString())
				.listFiles(file -> (containsText == null || containsText.isEmpty()) || file.getName().contains(containsText)
						&& (endingText == null || endingText.isEmpty() || file.getName().endsWith(endingText)));
		List<String> fileNames = Arrays.asList(files).stream().map(file -> file.getName()).collect(Collectors.toList());
		fileNames.sort(String::compareTo);

		List<Path> paths = fileNames.stream().map(name -> Paths.get(folderPath, name)).collect(Collectors.toList());

		return paths;
	}

	private static boolean isJarAsset(String path)
	{
		return StringUtils.isNotEmpty(path) && isRunningFromJar() && path.startsWith(getAssetsPath());
	}

	public static List<Path> listFilesFromJar(String path, String contains, String suffix)
	{
		List<Path> result = new ArrayList<>();
		try
		{
			Enumeration<URL> urls = Assets.class.getClassLoader().getResources(path);
			while (urls.hasMoreElements())
			{
				URL dirUrl = urls.nextElement();
				if (dirUrl != null && dirUrl.getProtocol().equals("jar"))
				{
					JarURLConnection jarConnection = (JarURLConnection) dirUrl.openConnection();
					JarFile jarFile = jarConnection.getJarFile();
					Enumeration<JarEntry> entries = jarFile.entries();
					while (entries.hasMoreElements())
					{
						JarEntry entry = entries.nextElement();
						String entryName = entry.getName();
						if (entryName.startsWith(path) && (StringUtils.isEmpty(suffix)
								|| entryName.endsWith(suffix) && (StringUtils.isEmpty(contains) || entryName.contains(contains))))
						{
							result.add(Paths.get(path, entryName.substring(path.length() + 1))); // +1 to remove the leading slash
						}
					}
				}
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException("Error reading directory from resources: " + path, e);
		}
		return result;
	}

	public static List<String> listNonEmptySubFoldersInJar(String folderPath)
	{
		List<String> subFolders = new ArrayList<>();
		String assetsPath = convertToAssetPath(folderPath);

		URL jarUrl = Assets.class.getResource(folderPath);
		if (jarUrl == null)
		{
			throw new RuntimeException("Unable to list non-empty subfolders in path '" + folderPath
					+ "' because the URL for that path was null. assetsPath: " + assetsPath);
		}

		String jarFilePath = jarUrl.getPath();
		if (jarFilePath.startsWith("jar:file:"))
		{
			jarFilePath = jarFilePath.substring(5, jarFilePath.indexOf("!"));
		}

		try (JarFile jarFile = new JarFile(jarFilePath))
		{
			jarFile.stream().filter(entry -> entry.isDirectory() && entry.getName().startsWith(assetsPath))
					.filter(entry -> jarFile.getEntry(entry.getName()).getSize() > 0)
					.map(entry -> entry.getName().substring(assetsPath.length())).collect(Collectors.toList());
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		return subFolders;
	}

	public static List<String> listNonEmptySubFolders(String path)
	{
		if (isJarAsset(path))
		{
			return listNonEmptySubFoldersInJar(path);
		}

		List<String> folderNames = new ArrayList<>();

		// TODO remove commented out code and extra scope block
		// // Try to load as a resource from the jar
		// URL resource = Assets.class.getResource(convertToAssetPath(path));
		// if (resource != null)
		// {
		// try
		// {
		// if (resource.getProtocol().equals("jar"))
		// {
		// String jarPath = resource.getPath().substring(5, resource.getPath().indexOf("!"));
		// final List<String> folderNamesFinal = folderNames;
		// try (JarFile jarFile = new JarFile(jarPath))
		// {
		// jarFile.stream().filter(e -> e.isDirectory() && e.getName().startsWith(path))
		// .forEach(e -> folderNamesFinal.add(e.getName()));
		// }
		// }
		// else
		// {
		// throw new RuntimeException("Non-jar resources are not supported. Path given: " + path);
		// }
		// }
		// catch (IOException e)
		// {
		// e.printStackTrace();// TODO remove this line.
		// throw new RuntimeException("Unable to list folders for resource for path " + path + ".", e);
		// }
		// }
		// else
		{
			// If not a resource, try to load from the file system

			String[] folderNamesArray = new File(path).list(new FilenameFilter()
			{
				@Override
				public boolean accept(File dir, String name)
				{
					File file = new File(dir, name);
					return file.isDirectory() && !isDirectoryEmpty(file.getAbsolutePath());
				}
			});

			if (folderNamesArray != null)
			{
				folderNames.addAll(Arrays.asList(folderNamesArray));
			}
		}

		return folderNames;
	}

	public static boolean isDirectoryEmpty(final String directory)
	{
		if (isJarAsset(directory))
		{
			URL resource = Assets.class.getResource(convertToAssetPath(directory));
			if (resource != null)
			{
				if (resource.getProtocol().equals("jar"))
				{
					String jarPath = resource.getPath().substring(5, resource.getPath().indexOf("!"));
					try (JarFile jarFile = new JarFile(jarPath))
					{
						Enumeration<JarEntry> entries = jarFile.entries();
						while (entries.hasMoreElements())
						{
							JarEntry entry = entries.nextElement();
							if (entry.getName().startsWith(directory) && !entry.getName().equals(directory + "/"))
							{
								return false;
							}
						}
					}
					catch (IOException e)
					{
						throw new RuntimeException("Unable to check if resource directory is empty. Directory: " + directory + ".", e);
					}
				}
				else
				{
					throw new RuntimeException("Non-jar resources are not supported. Directory given: " + directory);
				}
			}
			else
			{
				throw new RuntimeException(
						"Cannot check if resource directory is empty because it does not exist. Directory: " + directory);
			}

		}
		else
		{
			try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(Paths.get(directory)))
			{
				return !dirStream.iterator().hasNext();
			}
			catch (IOException e)
			{
				throw new RuntimeException("Unable to check if directory on disk is empty. Directory: " + directory + ".", e);
			}
		}
		return true;
	}


	/**
	 * Used to disable debug settings when not running from source.
	 */
	public static boolean isRunningFromJar()
	{
		String className = DebugFlags.class.getName().replace('.', '/');
		String classJar = DebugFlags.class.getResource("/" + className + ".class").toString();
		return classJar.startsWith("jar:");
	}

	public static String convertToAssetPath(String filePath)
	{
		if (!isRunningFromJar())
		{
			return filePath;
		}

		if (StringUtils.isNotEmpty(filePath) && filePath.startsWith(Assets.getAssetsPath()))
		{
			if (OSHelper.isWindows())
			{
				return "/" + filePath.replace('\\', '/');
			}
			else
			{
				return "/" + filePath;
			}
		}
		else
		{
			return filePath;
		}
	}
}
