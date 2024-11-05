package nortantis.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
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
		textureFiles = listFileNames(artPackPath.toString());

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
		if (resource == null)
		{
			return null;
		}
		Path artPackPath = getArtPackPath(resource.artPack, customImagesFolder);
		return Paths.get(artPackPath.toString(), resource.fileName);
	}

	public static List<String> listFileNames(String path)
	{
		return listFileNames(path, null, null);
	}

	public static List<String> listFileNames(String path, String containsText, String endingText)
	{
		return listFiles(path, containsText, endingText).stream().map(filePath -> FilenameUtils.getName(filePath.toString()))
				.collect(Collectors.toList());
	}

	public static List<Path> listFiles(String folderPath, String containsText, String endingText)
	{
		if (isJarAsset(folderPath))
		{
			return listFilesFromJar(folderPath, containsText, endingText);
		}

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
					try (JarFile jarFile = jarConnection.getJarFile())
					{
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

		URL jarUrl = Assets.class.getResource(assetsPath);
		if (jarUrl == null)
		{
			throw new RuntimeException("Unable to list non-empty subfolders in path '" + folderPath
					+ "' because the URL for that path was null. assetsPath: " + assetsPath);
		}

		try
		{
			JarURLConnection jarConnection = (JarURLConnection) jarUrl.openConnection();
			try (JarFile jarFile = jarConnection.getJarFile())
			{
				jarFile.stream().filter(entry -> entry.isDirectory() && entry.getName().startsWith(assetsPath))
						.filter(entry -> jarFile.getEntry(entry.getName()).getSize() > 0)
						.map(entry -> entry.getName().substring(assetsPath.length())).collect(Collectors.toList());
			}
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
		// If not a resource, try to load from the file system

		List<String> folderNames = new ArrayList<>();
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
					try
					{
						JarURLConnection jarConnection = (JarURLConnection) resource.openConnection();
						try (JarFile jarFile = jarConnection.getJarFile())
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

	public static String readFileAsStringFromDiskOrAssets(String filePath)
	{
		InputStream inputStream = readFileAsInputStreamFromDiskOrAssets(filePath);
		try
		{
			return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
		}
		catch (IOException e)
		{
			throw new RuntimeException("Error while reading from " + filePath, e);
		}
	}

	public static InputStream readFileAsInputStreamFromDiskOrAssets(String filePath)
	{
		if (isJarAsset(filePath))
		{
			InputStream inputStream = ImageHelper.class.getResourceAsStream(Assets.convertToAssetPath(filePath));
			return inputStream;
		}
		else
		{
			try
			{
				return new FileInputStream(filePath);
			}
			catch (IOException e)
			{
				throw new RuntimeException("Can't read the file " + filePath, e);
			}
		}
	}

	/**
	 * Recursively copies the contents of a folder either from disk or packaged in the jar file this program is running from to a given
	 * location on disk.
	 * 
	 * @throws IOException
	 */
	public static void copyDirectoryToDirectory(Path sourceDir, Path destDir) throws IOException
	{
		if (isJarAsset(sourceDir.toString()))
		{
			// Copy from jar file
			URL resource = FileHelper.class.getResource(sourceDir.toString());
			if (resource != null && resource.getProtocol().equals("jar"))
			{
				JarURLConnection jarConnection = (JarURLConnection) resource.openConnection();
				try (JarFile jarFile = jarConnection.getJarFile())
				{
					Enumeration<JarEntry> entries = jarFile.entries();
					while (entries.hasMoreElements())
					{
						JarEntry entry = entries.nextElement();
						if (entry.getName().startsWith(sourceDir.toString().substring(1)))
						{
							Path destPath = destDir.resolve(entry.getName().substring(sourceDir.toString().length()));
							if (entry.isDirectory())
							{
								Files.createDirectories(destPath);
							}
							else
							{
								try (InputStream inputStream = jarFile.getInputStream(entry))
								{
									Files.copy(inputStream, destPath, StandardCopyOption.REPLACE_EXISTING);
								}
							}
						}
					}
				}
			}
		}
		else
		{
			FileUtils.copyDirectoryToDirectory(sourceDir.toFile(), destDir.toFile());
		}
	}
}
