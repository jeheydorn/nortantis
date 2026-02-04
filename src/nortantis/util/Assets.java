package nortantis.util;

import nortantis.NamedResource;
import nortantis.platform.Image;
import nortantis.platform.PlatformFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Assets
{
	/**
	 * Interface for providing InputStreams on platforms where filesystem access doesn't work
	 * for bundled assets (e.g., Android, which requires AssetManager).
	 */
	public interface AssetInputStreamProvider
	{
		InputStream open(String assetPath) throws IOException;
	}

	private static AssetInputStreamProvider assetInputStreamProvider;

	public static void setAssetInputStreamProvider(AssetInputStreamProvider provider)
	{
		assetInputStreamProvider = provider;
	}

	private static final String assetsPath = "assets";
	public static final String customArtPack = "custom";
	private static final String artPacksFolder = "art packs";
	public static final String installedArtPack = "nortantis";
	public static final List<String> reservedArtPacks = Collections.unmodifiableList(Arrays.asList(installedArtPack, customArtPack, "all"));
	private static boolean disableAddedArtPacksForUnitTests;
	private static List<CachedEntry> cachedEntries;
	/**
	 * Must be lower case
	 */
	public static Set<String> allowedImageExtensions = Collections.unmodifiableSet(new HashSet<>((Arrays.asList("png", "jpg", "jpeg"))));
	private static ConcurrentHashMap<Pair<String>, Path> artPackPathCache;

	static
	{
		initializeEntryCache();
		artPackPathCache = new ConcurrentHashMap<>();
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
		result.addAll(Assets.listNonEmptySubFolders(getArtPacksFolder().toString()).stream().filter(name -> !reservedArtPacks.contains(name.toLowerCase())).toList());

		result.sort(String::compareTo);
		return result;
	}

	public static void clearArtPackCache()
	{
		ArtPacksFromArtPacksFolderCache.clearCache();
		artPackPathCache.clear();
		// Don't clear cachedEntries because it's values never change while the program is running.
	}

	public static List<NamedResource> listBackgroundTexturesForAllArtPacks(String customImagesFolder)
	{
		List<NamedResource> result = new ArrayList<>();
		for (String artPack : listArtPacks(StringUtils.isNotEmpty(customImagesFolder)))
		{
			for (NamedResource textureResource : listBackgroundTexturesForArtPack(artPack, FileHelper.replaceHomeFolderPlaceholder(customImagesFolder)))
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
	 * @return A path to the art pack assets, which may be in the jar file the program is running from.
	 */
	public static Path getArtPackPath(String artPack, String customImagesFolder)
	{
		Pair<String> key = new Pair<>(artPack, customImagesFolder);
		Path inMap = artPackPathCache.get(key);
		if (inMap != null)
		{
			return inMap;
		}

		Path result = getArtPackPathNoCache(artPack, customImagesFolder);
		artPackPathCache.put(key, result);
		return result;
	}

	private static Path getArtPackPathNoCache(String artPack, String customImagesFolder)
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
		return listFiles(path, containsText, endingText, allowedExtensions).stream().map(filePath -> FilenameUtils.getName(filePath.toString())).collect(Collectors.toList());
	}

	public static List<Path> listFiles(String folderPath, String containsText, String endingText, Set<String> allowedExtensions)
	{
		if (isPackagedAsset(folderPath))
		{
			return listFilesFromJar(folderPath, containsText, endingText);
		}

		File[] files = new File(folderPath).listFiles(
				file -> !file.isDirectory() && (StringUtils.isEmpty(containsText) || file.getName().contains(containsText)) && (StringUtils.isEmpty(endingText) || file.getName().endsWith(endingText))
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

	/**
	 * Returns true if the given path refers to a packaged asset — either in a JAR file or accessible
	 * via an AssetInputStreamProvider (e.g., Android's AssetManager).
	 */
	private static boolean isPackagedAsset(String path)
	{
		return StringUtils.isNotEmpty(path) && path.startsWith(getAssetsPath()) && (isRunningFromJar() || assetInputStreamProvider != null);
	}

	public static List<Path> listFilesFromJar(String folderPath, String containsText, String endingText)
	{
		List<Path> fileNames = new ArrayList<>();

		String assetsPath = convertToAssetPath(folderPath);
		String assetPathInEntryFormat = addTrailingSlash(assetsPath.substring(1));

		cachedEntries.stream().forEach(entry ->
		{
			String entryName = entry.name;
			if (!entry.isDirectory && entryName.startsWith(assetPathInEntryFormat) && ((StringUtils.isEmpty(containsText) || entryName.contains(containsText)))
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
			if (entry.isDirectory && entry.name.startsWith(assetPathInEntryFormat) && !addTrailingSlash(entry.name).equals(assetPathInEntryFormat))
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

		// Try to read the manifest generated at build time (works in JAR and on Android).
		try (InputStream manifestStream = Assets.class.getResourceAsStream("/assets/manifest.txt"))
		{
			if (manifestStream != null)
			{
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(manifestStream, StandardCharsets.UTF_8)))
				{
					String line;
					while ((line = reader.readLine()) != null)
					{
						if (line.isEmpty())
						{
							continue;
						}
						String[] parts = line.split("\t");
						if (parts.length >= 2)
						{
							boolean isDirectory = "D".equals(parts[1]);
							String name = isDirectory ? addTrailingSlash(parts[0]) : parts[0];
							cachedEntries.add(new CachedEntry(name, isDirectory));
						}
					}
				}
				return;
			}
		}
		catch (IOException e)
		{
			// Fall through to filesystem-based approach
		}

		// No manifest found (running from IDE / source). The filesystem paths will be used.
	}

	/**
	 * Recursively copies the contents of a folder either from disk or packaged in the jar file this program is running from to a given
	 * location on disk.
	 * 
	 * @throws IOException
	 */
	public static void copyDirectoryToDirectory(Path sourceDir, Path destDir) throws IOException
	{
		if (isPackagedAsset(sourceDir.toString()))
		{
			String sourceDirParentInEntryFormat = addTrailingSlash(convertToAssetPath(sourceDir.getParent().toString()).substring(1));
			String sourceDirInEntryFormat = addTrailingSlash(convertToAssetPath(sourceDir.toString()).substring(1));

			// Copy from cached entries using getResourceAsStream
			for (CachedEntry entry : cachedEntries)
			{
				if (entry.name.startsWith(sourceDirInEntryFormat) && !addTrailingSlash(entry.name).equals(sourceDirInEntryFormat))
				{
					String entryPathInParentFolder = entry.name.substring((addTrailingSlash(sourceDirParentInEntryFormat)).length());

					Path destPath = Paths.get(destDir.toString(), entryPathInParentFolder);
					if (entry.isDirectory)
					{
						Files.createDirectories(destPath);
					}
					else
					{
						Files.createDirectories(destPath.getParent());
						try (InputStream inputStream = createInputStream(entry.name))
						{
							if (inputStream != null)
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
		if (isPackagedAsset(path))
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
		java.net.URL classUrl = Assets.class.getResource("/" + className + ".class");
		if (classUrl == null)
		{
			return false;
		}
		return classUrl.toString().startsWith("jar:");
	}

	public static String convertToAssetPath(String filePath)
	{
		if (!isRunningFromJar() && assetInputStreamProvider == null)
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
		if (isPackagedAsset(filePath))
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
		if (isPackagedAsset(filePath))
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
		if (isPackagedAsset(filePath))
		{
			if (assetInputStreamProvider != null)
			{
				try
				{
					return assetInputStreamProvider.open(filePath);
				}
				catch (IOException e)
				{
					throw new RuntimeException("Can't read the file '" + filePath + "'", e);
				}
			}
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
		if (isPackagedAsset(filePath))
		{
			if (assetInputStreamProvider != null)
			{
				try (InputStream is = assetInputStreamProvider.open(filePath))
				{
					return is != null;
				}
				catch (IOException e)
				{
					// AssetManager.open() fails for directories. Fall back to the manifest cache.
					return existsInManifestCache(filePath);
				}
			}
			return existsInJar(filePath);
		}
		else
		{
			return Files.exists(Paths.get(filePath));
		}
	}

	private static boolean existsInManifestCache(String filePath)
	{
		if (cachedEntries == null)
		{
			return false;
		}
		String assetPath = convertToAssetPath(filePath);
		String entryFormat = assetPath.substring(1);
		String entryFormatDir = addTrailingSlash(entryFormat);
		for (CachedEntry entry : cachedEntries)
		{
			if (entry.name.equals(entryFormat) || entry.name.equals(entryFormatDir))
			{
				return true;
			}
		}
		return false;
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
		if (isPackagedAsset(filePath))
		{
			try (InputStream inputStream = createInputStream(filePath))
			{
				if (inputStream == null)
				{
					throw new RuntimeException(
							"Can't read the image resource '" + filePath + "' because either it doesn't exist or it's an unsupported format or corrupted.");
				}

				Image image = PlatformFactory.getInstance().readImage(inputStream);
				if (image == null)
				{
					throw new RuntimeException("Can't read the image resource " + filePath + ". It might be in an unsupported format or corrupted.");
				}

				return image;
			}
			catch (IOException e)
			{
				throw new RuntimeException("Error while reading image from resource " + filePath, e);
			}
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
				throw new RuntimeException("Can't read the image resource '" + filePath + "' because either it doesn't or it's an unsupported format or corrupted.");
			}

			Image image = PlatformFactory.getInstance().readImage(inputStream);
			if (image == null)
			{
				throw new RuntimeException("Can't read the image resource " + filePath + ". It might be in an unsupported format or corrupted.");
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
