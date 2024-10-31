package nortantis.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
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

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import nortantis.BackgroundTextureResource;
import nortantis.ImageCache;
import nortantis.platform.Image;
import nortantis.platform.PlatformFactory;

public class Assets
{
	private final static String assetsPath = "assets";
	private final static String customArtPack = "custom";
	private final static String artPacksFolder = "art packs";
	public final static String installedArtPack = "nortantis";

	public static synchronized String getAssetsPath()
	{
		return assetsPath;
	}

	public static List<String> listArtPacks(boolean includeCustomArtPack)
	{
		List<String> result = new ArrayList<>();
		result.add(installedArtPack);

		// Add installed art packs.
		result.addAll(
				ImageCache.getInstance(null).listFolders(Paths.get(OSHelper.getAppDataPath().toString(), artPacksFolder).toString(), true));

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
			for (String textureName : Assets.listBackgroundTexturesForArtPack(artPack, customImagesFolder))
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

	public static String[] listFileNames(String path)
	{
		return listFileNames(path, null);
	}

	public static String[] listFileNames(String path, String textRequiredInFileName)
	{
		List<String> fileNames = listFiles(path, textRequiredInFileName).stream()
				.map(filePath -> FilenameUtils.getName(filePath.toString())).collect(Collectors.toList());
		return fileNames.toArray(new String[0]);
	}

	public static List<Path> listFiles(String folderPath, String textRequiredInFileName)
	{
		// Try to load as a resource from the jar
		URL resource = Assets.class.getResource(folderPath);
		Path path;
		if (resource != null)
		{
			try
			{
				path = Paths.get(resource.toURI());
			}
			catch (URISyntaxException e)
			{
				throw new RuntimeException("Error while trying to list files in folder: " + folderPath, e);
			}
		}
		else
		{
			path = Paths.get(folderPath);
		}

		File[] files = new File(path.toString()).listFiles(file -> (textRequiredInFileName == null || textRequiredInFileName.isEmpty())
				|| file.getName().contains(textRequiredInFileName));
		List<String> fileNames = Arrays.asList(files).stream().map(file -> file.getName()).collect(Collectors.toList());
		fileNames.sort(String::compareTo);

		List<Path> paths = fileNames.stream().map(name -> Paths.get(folderPath, name)).collect(Collectors.toList());

		return paths;
	}
}
