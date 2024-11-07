package nortantis.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.util.Properties;
import java.util.TreeSet;
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
		textureFiles = listFileNames(Paths.get(artPackPath.toString(), "background textures").toString());

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
			return Paths.get(Assets.getAssetsPath());
		}

		return Paths.get(OSHelper.getAppDataPath().toString(), artPacksFolder, artPack);
	}

	public static Path getBackgroundTextureResourcePath(BackgroundTextureResource resource, String customImagesFolder)
	{
		if (resource == null)
		{
			return null;
		}
		Path artPackPath = getArtPackPath(resource.artPack, customImagesFolder);
		return Paths.get(artPackPath.toString(), "background textures", resource.fileName);
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
				.listFiles(file -> (StringUtils.isEmpty(containsText) || file.getName().contains(containsText))
						&& (StringUtils.isEmpty(endingText) || file.getName().endsWith(endingText)));
		if (files == null)
		{
			return new ArrayList<>();
		}
		List<String> fileNames = Arrays.asList(files).stream().map(file -> file.getName()).collect(Collectors.toList());
		fileNames.sort(String::compareTo);

		List<Path> paths = fileNames.stream().map(name -> Paths.get(folderPath, name)).collect(Collectors.toList());

		return paths;
	}

	private static boolean isJarAsset(String path)
	{
		return StringUtils.isNotEmpty(path) && isRunningFromJar() && path.startsWith(getAssetsPath());
	}

	public static synchronized List<Path> listFilesFromJar(String folderPath, String containsText, String endingText)
	{
		List<Path> fileNames = new ArrayList<>();
		try
		{
			String assetsPath = convertToAssetPath(folderPath);
			String assetPathInEntryFormat = addTrailingSlash(assetsPath.substring(1));
			URL jarUrl = Assets.class.getResource(assetsPath);
			if (jarUrl == null)
			{
				throw new RuntimeException("Unable to list files in path '" + folderPath
						+ "' because the URL for that path was null. assetsPath: " + assetsPath);
			}

			JarURLConnection jarConnection = (JarURLConnection) jarUrl.openConnection();
			try (JarFile jarFile = jarConnection.getJarFile())
			{
				jarFile.stream().forEach(entry ->
				{
					String entryName = entry.getName();
					if (!entry.isDirectory() && entryName.startsWith(assetPathInEntryFormat)
							&& ((StringUtils.isEmpty(containsText) || entryName.contains(containsText)))
							&& (StringUtils.isEmpty(endingText) || entryName.endsWith(endingText)))
					{
						fileNames.add(Paths.get(folderPath, FilenameUtils.getName(entryName)));
					}
				});
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException("Error reading directory from resources: " + folderPath, e);
		}

		// I don't know how it's possible, but the jar file can give me duplicates. So remove duplicates and sort.
		return new ArrayList<>(new TreeSet<>(fileNames));
	}

	public static synchronized List<String> listSubFoldersInJar(String folderPath)
	{
		List<String> subfolders = new ArrayList<>();
		String assetsPath = convertToAssetPath(folderPath);
		String assetPathInEntryFormat = addTrailingSlash(assetsPath.substring(1));

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
				subfolders = jarFile.stream()
						.filter(entry -> entry.isDirectory() && entry.getName().startsWith(assetPathInEntryFormat)
								&& !addTrailingSlash(entry.getName()).equals(assetPathInEntryFormat))
						.map(entry -> FilenameUtils.getName(removeTrailingSlash(entry.getName()))).collect(Collectors.toList());
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		return new ArrayList<>(new TreeSet<>(subfolders));
	}

	/**
	 * Recursively copies the contents of a folder either from disk or packaged in the jar file this program is running from to a given
	 * location on disk.
	 * 
	 * @throws IOException
	 */
	public static synchronized void copyDirectoryToDirectory(Path sourceDir, Path destDir) throws IOException
	{
		if (isJarAsset(sourceDir.toString()))
		{
			String sourceDirInEntryFormat = addTrailingSlash(convertToAssetPath(sourceDir.toString()).substring(1));

			// Copy from jar file
			URL resource = Assets.class.getResource(convertToAssetPath(sourceDir.toString()));
			JarURLConnection jarConnection = (JarURLConnection) resource.openConnection();
			try (JarFile jarFile = jarConnection.getJarFile())
			{
				Enumeration<JarEntry> entries = jarFile.entries();
				while (entries.hasMoreElements())
				{
					JarEntry entry = entries.nextElement();
					if (entry.getName().startsWith(sourceDirInEntryFormat) && !addTrailingSlash(entry.getName()).equals(sourceDirInEntryFormat))
					{
						String entryPathWithoutAssetsFolder = entry.getName().substring((assetsPath + "/").length());
						
						Path destPath = Paths.get(destDir.toString(), entryPathWithoutAssetsFolder);
						if (entry.isDirectory())
						{
							Files.createDirectories(destPath);
						}
						else
						{
							try (InputStream inputStream = jarFile.getInputStream(entry))
							{
								Files.copy(inputStream, destPath);
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
	
	private static String removeTrailingSlash(String path)
	{
		if (path.endsWith("/"))
		{
			return path.substring(0, path.length() - 1);
		}
		return path;
	}

	private static String addTrailingSlash(String path)
	{
		if (StringUtils.isEmpty(path))
		{
			return path;
		}

		if (path.endsWith("/"))
		{
			return path;
		}
		else
		{
			return path + "/";
		}
	}

	/**
	 * Lists sub-folders of the given path that are non-empty when path is in the file system. When the path is in a jar file, it lists all
	 * sub-folders, empty or not, with the expectation that I won't release empty folders in the assets.
	 * 
	 * @param path
	 *            Either a file path on disk or a relative path of the assets folder.
	 * @return A list of folder names (not paths).
	 */
	public static synchronized List<String> listNonEmptySubFolders(String path)
	{
		if (isJarAsset(path))
		{
			return listSubFoldersInJar(path);
		}
		// If not a resource, try to load from the file system

		List<String> folderNames = new ArrayList<>();
		String[] folderNamesArray = new File(path).list(new FilenameFilter()
		{
			@Override
			public boolean accept(File dir, String name)
			{
				File file = new File(dir, name);
				return file.isDirectory() && !FileHelper.isDirectoryEmpty(file.getAbsolutePath());
			}
		});

		if (folderNamesArray != null)
		{
			folderNames.addAll(Arrays.asList(folderNamesArray));
		}

		return folderNames;
	}

	/**
	 * Used to disable debug settings when not running from source.
	 */
	public static boolean isRunningFromJar()
	{
		String className = Assets.class.getName().replace('.', '/');
		String classJar = Assets.class.getResource("/" + className + ".class").toString();
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

	public static synchronized String readFileAsString(String filePath)
	{
		InputStream inputStream = createInputStream(filePath);
		try
		{
			return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
		}
		catch (IOException e)
		{
			throw new RuntimeException("Error while reading from " + filePath, e);
		}
	}

	private static BufferedReader createBufferedReader(String filePath) throws FileNotFoundException
	{
		if (isJarAsset(filePath))
		{
			return new BufferedReader(new InputStreamReader(createInputStream(filePath), StandardCharsets.UTF_8));
		}
		else
		{
			return new BufferedReader(new FileReader(new File(filePath)));
		}
	}

	public static List<String> readAllLines(String filePath) throws IOException
	{
		if (isJarAsset(filePath))
		{
			return readAllLinesFromFileInJar(filePath);
		}
		else
		{
			return Files.readAllLines(Paths.get(filePath), StandardCharsets.UTF_8);
		}

	}

	private static synchronized List<String> readAllLinesFromFileInJar(String filePath) throws IOException
	{
		InputStream inputStream = createInputStream(filePath);
		List<String> lines = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				lines.add(line);
			}
		}
		return lines;
	}

	private static synchronized InputStream createInputStream(String filePath)
	{
		if (isJarAsset(filePath))
		{
			return createInputStreamFromFileInJar(filePath);
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

	private static synchronized InputStream createInputStreamFromFileInJar(String filePath)
	{
		return Assets.class.getResourceAsStream(Assets.convertToAssetPath(filePath));
	}

	public static boolean exists(String filePath)
	{
		if (isJarAsset(filePath))
		{
			return existsInJar(filePath);
		}
		else
		{
			return Files.exists(Paths.get(filePath));
		}
	}

	public static synchronized boolean existsInJar(String filePath)
	{
		try (InputStream inputStream = Assets.class.getResourceAsStream(Assets.convertToAssetPath(filePath)))
		{
			return inputStream != null;
		}
		catch (IOException e)
		{
			return false;
		}
	}

	public static Image readImage(String filePath)
	{
		if (isJarAsset(filePath))
		{
			return readImageFromJar(filePath);
		}
		else
		{
			// Not an asset. Read from disk.
			return Image.read(filePath);
		}
	}

	private static synchronized Image readImageFromJar(String filePath)
	{
		try (InputStream inputStream = Assets.class.getResourceAsStream(Assets.convertToAssetPath(filePath)))
		{
			if (inputStream == null)
			{
				throw new RuntimeException("Can't read the image resource '" + filePath
						+ "' because either it doesn't or it's an unsupported format or corrupted.");
			}

			Image image = PlatformFactory.getInstance().readImage(inputStream);
			if (image == null)
			{
				throw new RuntimeException(
						"Can't read the image resource " + filePath + ". It might be in an unsupported format or corrupted.");
			}

			return image;
		}
		catch (IOException e)
		{
			throw new RuntimeException("Error while reading image from resource " + filePath, e);
		}
	}

	public static synchronized List<Pair<String>> readStringPairs(String filePath)
	{
		List<Pair<String>> result = new ArrayList<>();
		try (BufferedReader br = Assets.createBufferedReader(filePath))
		{
			int lineNum = 0;
			for (String line; (line = br.readLine()) != null;)
			{
				lineNum++;

				// Remove white space lines.
				if (!line.trim().isEmpty())
				{
					String[] parts = line.split("\t");
					if (parts.length != 2)
					{
						Logger.println("Warning: No string pair found in " + filePath + " at line " + lineNum + ".");
						continue;
					}
					result.add(new Pair<>(parts[0], parts[1]));
				}
			}
		}
		catch (FileNotFoundException e)
		{
			throw new RuntimeException(e);
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}

		return result;
	}

	public static synchronized List<String> readNameList(String filePath)
	{
		List<String> result = new ArrayList<>();
		try (BufferedReader br = Assets.createBufferedReader(filePath))
		{
			for (String line; (line = br.readLine()) != null;)
			{
				// Remove white space lines.
				if (!line.trim().isEmpty())
				{
					result.add(line);
				}
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException("Unable to read names from the file " + filePath, e);
		}

		return result;
	}

	public static synchronized Properties loadPropertiesFile(String filePath) throws IOException
	{
		final Properties props = new Properties();
		props.load(Assets.createInputStream(filePath));
		return props;
	}
}
