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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import nortantis.NamedResource;
import nortantis.platform.Image;
import nortantis.platform.PlatformFactory;

public class Assets
{
	private final static String assetsPath = "assets";
	public final static String customArtPack = "custom";
	private final static String artPacksFolder = "art packs";
	public final static String installedArtPack = "nortantis";
	public final static List<String> reservedArtPacks = Collections.unmodifiableList(Arrays.asList(installedArtPack, customArtPack, "all"));
	private static boolean disableAddedArtPacksForUnitTests;
	private static List<CachedEntry> cachedEntries;
	/**
	 * Must be lower case
	 */
	public static Set<String> allowedImageExtensions = Collections.unmodifiableSet(new HashSet<>((Arrays.asList("png", "jpg", "jpeg"))));

	static
	{
		initializeEntryCache();
	}

	public static String getAssetsPath()
	{
		return assetsPath;
	}

	public static String getInstalledArtPackPath()
	{
		return Paths.get(getAssetsPath(), "installed art pack").toString();
	}

	public static List<String> listArtPacks(boolean includeCustomArtPack)
	{
		List<String> result = new ArrayList<>();
		result.add(installedArtPack);

		// Add added art packs from the art packs folder.
		if (!disableAddedArtPacksForUnitTests)
		{
			result.addAll(ArtPacksFromArtPacksFolderCache.getArtPacksFromArtPackFolder());
		}

		// Add custom images folder if the map has one.
		if (includeCustomArtPack)
		{
			result.add(customArtPack);
		}

		result.sort(String::compareTo);
		return result;
	}

	public static boolean artPackExists(String artPack, String customImagesFolder)
	{
		return listArtPacks(!StringUtils.isEmpty(customImagesFolder)).contains(artPack);
	}

	private static class ArtPacksFromArtPacksFolderCache
	{
		private static List<String> artPacksInArtPacksFolderCache;

		public static synchronized List<String> getArtPacksFromArtPackFolder()
		{
			if (artPacksInArtPacksFolderCache == null)
			{
				artPacksInArtPacksFolderCache = listArtPacksFromArtPackFolder();
			}
			return artPacksInArtPacksFolderCache;
		}

		public static synchronized void clearCache()
		{
			artPacksInArtPacksFolderCache = null;
		}
	}

	private static List<String> listArtPacksFromArtPackFolder()
	{
		List<String> result = new ArrayList<>();
		// Add installed art packs.
		result.addAll(Assets.listNonEmptySubFolders(getArtPacksFolder().toString()).stream()
				.filter(name -> !reservedArtPacks.contains(name.toLowerCase())).toList());

		result.sort(String::compareTo);
		return result;
	}

	public static void clearArtPackCache()
	{
		ArtPacksFromArtPacksFolderCache.clearCache();
		// Don't clear cachedEntires because it's values never change while the program is running.
	}

	public static List<NamedResource> listBackgroundTexturesForAllArtPacks(String customImagesFolder)
	{
		List<NamedResource> result = new ArrayList<>();
		for (String artPack : listArtPacks(StringUtils.isNotEmpty(customImagesFolder)))
		{
			for (NamedResource textureResource : listBackgroundTexturesForArtPack(artPack,
					FileHelper.replaceHomeFolderPlaceholder(customImagesFolder)))
			{
				result.add(textureResource);
			}
		}

		return result;
	}

	public static List<NamedResource> listBackgroundTexturesForArtPack(String artPack, String customImagesFolder)
	{
		Path artPackPath = getArtPackPath(artPack, customImagesFolder);

		List<String> textureFiles;
		textureFiles = listFileNames(Paths.get(artPackPath.toString(), "background textures").toString(), allowedImageExtensions);
		return textureFiles.stream().map(fileName -> new NamedResource(artPack, fileName)).toList();
	}

	/**
	 * Gets the path to the assets of an art pack.
	 * 
	 * @param artPack
	 *            Art pack name
	 * @param customImagesFolder
	 *            The map's custom images folder. Only required if art pack is "custom".
	 * @return A path to a the art pack assets, which may be in the jar file the program is running from.
	 */
	public static Path getArtPackPath(String artPack, String customImagesFolder)
	{
		if (artPack.equals(customArtPack))
		{
			if (StringUtils.isEmpty(customImagesFolder))
			{
				return null;
			}
			return Paths.get(FileHelper.replaceHomeFolderPlaceholder(customImagesFolder));
		}

		if (artPack.equals(installedArtPack))
		{
			return Paths.get(getInstalledArtPackPath());
		}

		return Paths.get(getArtPacksFolder().toString(), artPack);
	}

	public static Path getArtPacksFolder()
	{
		return Paths.get(OSHelper.getAppDataPath().toString(), artPacksFolder);
	}

	public static Path getBackgroundTextureResourcePath(NamedResource resource, String customImagesFolder)
	{
		if (resource == null)
		{
			return null;
		}
		if (StringUtils.isEmpty(resource.name) || StringUtils.isEmpty(resource.artPack))
		{
			// I don't know how, but in a stack trace of a crash I found that resource.name was null, so I'm adding this to be safe.
			return null;
		}
		Path artPackPath = getArtPackPath(resource.artPack, customImagesFolder);
		if (artPackPath == null)
		{
			return null;
		}
		return Paths.get(artPackPath.toString(), "background textures", resource.name);
	}

	public static List<NamedResource> listAllBorderTypes(String customImagesFolder)
	{
		List<NamedResource> result = new ArrayList<>();
		for (String artPack : listArtPacks(StringUtils.isNotEmpty(customImagesFolder)))
		{
			result.addAll(listBorderTypesForArtPack(artPack, customImagesFolder));
		}

		return result;
	}

	public static List<NamedResource> listBorderTypesForArtPack(String artPack, String customImagesFolder)
	{
		Path artPackPath = getArtPackPath(artPack, customImagesFolder);
		List<String> borderTypes = listNonEmptySubFolders(Paths.get(artPackPath.toString(), "borders").toString());
		Collections.sort(borderTypes);
		return borderTypes.stream().map(bt -> new NamedResource(artPack, bt)).collect(Collectors.toList());
	}

	public static List<String> listFileNames(String path, Set<String> allowedExtensions)
	{
		return listFileNames(path, null, null, allowedExtensions);
	}

	public static List<String> listFileNames(String path, String containsText, String endingText, Set<String> allowedExtensions)
	{
		return listFiles(path, containsText, endingText, allowedExtensions).stream()
				.map(filePath -> FilenameUtils.getName(filePath.toString())).collect(Collectors.toList());
	}

	public static List<Path> listFiles(String folderPath, String containsText, String endingText, Set<String> allowedExtensions)
	{
		if (isJarAsset(folderPath))
		{
			return listFilesFromJar(folderPath, containsText, endingText);
		}

		File[] files = new File(folderPath.toString())
				.listFiles(file -> !file.isDirectory() && (StringUtils.isEmpty(containsText) || file.getName().contains(containsText))
						&& (StringUtils.isEmpty(endingText) || file.getName().endsWith(endingText))
						&& (allowedExtensions == null || allowedExtensions.contains(FilenameUtils.getExtension(file.getName()).toLowerCase())));
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

	public static List<Path> listFilesFromJar(String folderPath, String containsText, String endingText)
	{
		List<Path> fileNames = new ArrayList<>();

		String assetsPath = convertToAssetPath(folderPath);
		String assetPathInEntryFormat = addTrailingSlash(assetsPath.substring(1));

		cachedEntries.stream().forEach(entry ->
		{
			String entryName = entry.name;
			if (!entry.isDirectory && entryName.startsWith(assetPathInEntryFormat)
					&& ((StringUtils.isEmpty(containsText) || entryName.contains(containsText)))
					&& (StringUtils.isEmpty(endingText) || entryName.endsWith(endingText)))
			{
				fileNames.add(Paths.get(folderPath, FilenameUtils.getName(entryName)));
			}
		});

		// I don't know how it's possible, but the jar file can give me duplicates. So remove duplicates and sort.
		return new ArrayList<>(new TreeSet<>(fileNames));
	}

	public static List<String> listSubFoldersInJar(String folderPath)
	{
		String assetsPath = convertToAssetPath(folderPath);
		String assetPathInEntryFormat = addTrailingSlash(assetsPath.substring(1));

		List<String> result = new ArrayList<>();
		for (CachedEntry entry : cachedEntries)
		{
			if (entry.isDirectory && entry.name.startsWith(assetPathInEntryFormat)
					&& !addTrailingSlash(entry.name).equals(assetPathInEntryFormat))
			{
				result.add(FilenameUtils.getName(removeTrailingSlash(entry.name)));
			}
		}

		return result;
	}

	private static void initializeEntryCache()
	{
		if (cachedEntries != null)
		{
			return;
		}

		cachedEntries = new ArrayList<>();
		String assetPathInEntryFormat = addTrailingSlash(assetsPath);

		URL jarUrl = Assets.class.getResource("/" + assetsPath);
		if (jarUrl == null)
		{
			throw new RuntimeException(
					"Unable to check installed assets" + " because the URL for the jar file was null. assetsPath: " + assetsPath);
		}

		try
		{
			JarURLConnection jarConnection = (JarURLConnection) jarUrl.openConnection();
			try (JarFile jarFile = jarConnection.getJarFile())
			{
				jarFile.stream()
						.filter(entry -> entry.getName().startsWith(assetPathInEntryFormat)
								&& !addTrailingSlash(entry.getName()).equals(assetPathInEntryFormat))
						.forEach(entry -> cachedEntries.add(new CachedEntry(entry.getName(), entry.isDirectory())));
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
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
			String sourceDirParentInEntryFormat = addTrailingSlash(convertToAssetPath(sourceDir.getParent().toString()).substring(1));
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
					if (entry.getName().startsWith(sourceDirInEntryFormat)
							&& !addTrailingSlash(entry.getName()).equals(sourceDirInEntryFormat))
					{
						String entryPathInParentFolder = entry.getName()
								.substring((addTrailingSlash(sourceDirParentInEntryFormat)).length());

						Path destPath = Paths.get(destDir.toString(), entryPathInParentFolder);
						if (entry.isDirectory())
						{
							Files.createDirectories(destPath);
						}
						else
						{
							Files.createDirectories(destPath.getParent());
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
	public static List<String> listNonEmptySubFolders(String path)
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

		if (StringUtils.isNotEmpty(filePath) && filePath.startsWith(getAssetsPath()))
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

	public static String readFileAsString(String filePath)
	{
		try (InputStream inputStream = createInputStream(filePath))
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

	private static List<String> readAllLinesFromFileInJar(String filePath) throws IOException
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

	private static InputStream createInputStream(String filePath)
	{
		if (isJarAsset(filePath))
		{
			try
			{
				return createInputStreamFromFileInJar(filePath);
			}
			catch (IOException e)
			{
				throw new RuntimeException("Can't read the file '" + filePath + "' from the jar.", e);
			}
		}
		else
		{
			try
			{
				return new FileInputStream(filePath);
			}
			catch (IOException e)
			{
				throw new RuntimeException("Can't read the file '" + filePath + "'", e);
			}
		}
	}

	private static InputStream createInputStreamFromFileInJar(String filePath) throws IOException
	{
		InputStream result = Assets.class.getResourceAsStream(convertToAssetPath(filePath));
		return result;
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

	public static boolean existsInJar(String filePath)
	{
		try (InputStream inputStream = createInputStreamFromFileInJar(filePath))
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

	private static Image readImageFromJar(String filePath)
	{
		try (InputStream inputStream = createInputStreamFromFileInJar(filePath))
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

	public static List<Pair<String>> readStringPairs(String filePath)
	{
		List<Pair<String>> result = new ArrayList<>();
		try (BufferedReader br = createBufferedReader(filePath))
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

	public static List<String> readNameList(String filePath)
	{
		List<String> result = new ArrayList<>();
		try (BufferedReader br = createBufferedReader(filePath))
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

	public static Properties loadPropertiesFile(String filePath) throws IOException
	{
		final Properties props = new Properties();
		try (InputStream inputStream = createInputStream(filePath))
		{
			props.load(inputStream);
		}

		return props;
	}

	private static class CachedEntry
	{
		public final String name;
		public final boolean isDirectory;

		public CachedEntry(String name, boolean isDirectory)
		{
			super();
			this.name = name;
			this.isDirectory = isDirectory;
		}

		@Override
		public String toString()
		{
			return "CachedEntry [name=" + name + ", isDirectory=" + isDirectory + "]";
		}

	}

	public static void disableAddedArtPacksForUnitTests()
	{
		disableAddedArtPacksForUnitTests = true;
	}
}
