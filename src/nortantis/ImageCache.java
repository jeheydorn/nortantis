package nortantis;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

import org.apache.commons.io.FilenameUtils;

import nortantis.util.AssetsPath;
import nortantis.util.ConcurrentHashMapF;
import nortantis.util.HashMapF;
import nortantis.util.ImageHelper;
import nortantis.util.ListMap;
import nortantis.util.Logger;
import nortantis.util.Range;
import nortantis.util.Tuple2;
import nortantis.util.Tuple3;

/**
 * Caches icons in memory to avoid recreating or reloading them.
 */
public class ImageCache
{
	private static HashMapF<String, ImageCache> instances = new HashMapF<>();

	/**
	 * Maps original images, to scaled width, to scaled images.
	 */
	private ConcurrentHashMapF<BufferedImage, ConcurrentHashMapF<Dimension, BufferedImage>> scaledCache;

	/**
	 * Maps file path (or any string key) to images.
	 */
	private ConcurrentHashMapF<String, BufferedImage> fileCache;

	/**
	 * Maps string keys to images generated (not loaded directly from files).
	 */
	private ConcurrentHashMapF<String, BufferedImage> generatedImageCache;

	/**
	 * Maps icon type > icon sub-type name > lists of icons of that sub-type.
	 * The first image in the tuple is the icon. The second image is the mask,
	 * which is generated based on the image loaded from disk.
	 */
	private ConcurrentHashMapF<IconType, ListMap<String, Tuple2<BufferedImage, BufferedImage>>> iconGroupsAndMasksCache;

	/**
	 * Maps icon type > icon group name > lists of icons,
	 * masks, and icon widths, of that sub-type.
	 */
	private ConcurrentHashMapF<IconType, ConcurrentHashMapF<String, Map<String, Tuple3<BufferedImage, BufferedImage, Integer>>>> iconsWithWidthsCache;

	private ConcurrentHashMapF<IconType, ConcurrentHashMapF<String, String[]>> iconGroupFilesNamesCache;

	private ConcurrentHashMapF<IconType, Set<String>> iconGroupNames;
	
	private String imagesPath;

	/**
	 * Singleton
	 */
	private ImageCache(String imagesPath)
	{
		this.imagesPath = imagesPath;
		scaledCache = new ConcurrentHashMapF<>();
		fileCache = new ConcurrentHashMapF<>();
		generatedImageCache = new ConcurrentHashMapF<>();
		iconGroupsAndMasksCache = new ConcurrentHashMapF<>();
		iconsWithWidthsCache = new ConcurrentHashMapF<>();
		iconGroupFilesNamesCache = new ConcurrentHashMapF<>();
		iconGroupNames = new ConcurrentHashMapF<>();
	}

	/**
	 * Gets the image cache instance associated with the given imagesPath.
	 * When imagesPath is null or empty, then the imageCache for the installed
	 * images is given.
	 * @param imagesPath
	 * @return
	 */
	public synchronized static ImageCache getInstance(String imagesPath)
	{
		if (imagesPath != null && !imagesPath.isEmpty())
		{
			return instances.getOrCreate(imagesPath, () -> new ImageCache(imagesPath));
		}
		
		return instances.getOrCreate(AssetsPath.getInstallPath(), () -> new ImageCache(AssetsPath.getInstallPath()));
	}

	/**
	 * Either looks up in the cache, or creates, a version of the given icon
	 * with the given width.
	 * 
	 * @param icon
	 *            Original image (not scaled)
	 * @param width
	 *            The desired width
	 * @return A scaled image
	 */
	public BufferedImage getScaledImageByWidth(BufferedImage icon, int width)
	{
		Dimension dimension = new Dimension(width, ImageHelper.getHeightWhenScaledByWidth(icon, width));
		// There is a small chance the 2 different threads might both add the
		// same image at the same time,
		// but if that did happen it would only results in a little bit of
		// duplicated work, not a functional
		// problem.
		return scaledCache.getOrCreate(icon, () -> new ConcurrentHashMapF<>()).getOrCreate(dimension,
				() -> ImageHelper.scaleByWidth(icon, width));
	}

	/**
	 * Either looks up in the cache, or creates, a version of the given icon
	 * with the given height.
	 * 
	 * @param icon
	 *            Original image (not scaled)
	 * @param width
	 *            The desired width
	 * @return A scaled image
	 */
	public BufferedImage getScaledImageByHeight(BufferedImage icon, int height)
	{
		Dimension dimension = new Dimension(ImageHelper.getWidthWhenScaledByHeight(icon, height), height);
		// There is a small chance the 2 different threads might both add the
		// same image at the same time,
		// but if that did happen it would only results in a little bit of
		// duplicated work, not a functional
		// problem.
		return scaledCache.getOrCreate(icon, () -> new ConcurrentHashMapF<>()).getOrCreate(dimension,
				() -> ImageHelper.scaleByHeight(icon, height));
	}

	public BufferedImage getImageFromFile(Path path)
	{
		return fileCache.getOrCreate(path.toString(), () -> ImageHelper.read(path.toString()));
	}

	public boolean containsImageFile(Path path)
	{
		return fileCache.containsKey(path.toString());
	}

	/**
	 * Get an image from cache or create it using createFun.
	 */
	public BufferedImage getOrCreateImage(String key, Supplier<BufferedImage> createFun)
	{
		return generatedImageCache.getOrCreate(key.toString(), createFun);
	}

	/**
	 * Loads or retrieves cache for groups if icons of a given type.
	 * 
	 * @returns A map of icon type > icon sub-type name > lists of icons of that
	 *          sub-type. The first image in the tuple is the icon. The second
	 *          image is the mask, which is generated based on the image loaded
	 *          from disk.
	 */
	public ListMap<String, Tuple2<BufferedImage, BufferedImage>> getAllIconGroupsAndMasksForType(IconType iconType)
	{
		return iconGroupsAndMasksCache.getOrCreate(iconType, () -> loadAllIconGroupsAndMasksForType(iconType));
	}

	private ListMap<String, Tuple2<BufferedImage, BufferedImage>> loadAllIconGroupsAndMasksForType(IconType iconType)
	{
		return loadAllIconGroupsAndMasksForSetAndType(iconType);
	}

	private ListMap<String, Tuple2<BufferedImage, BufferedImage>> loadAllIconGroupsAndMasksForSetAndType(IconType iconType)
	{
		ListMap<String, Tuple2<BufferedImage, BufferedImage>> imagesPerGroup = new ListMap<>();

		Set<String> groupNames = getIconGroupNames(iconType);
		for (String groupName : groupNames)
		{
			String[] fileNames = getIconGroupFileNames(iconType, groupName);
			String groupPath = getIconGroupPath(iconType, groupName);
			if (fileNames.length == 0)
			{
				continue;
			}

			for (String fileName : fileNames)
			{
				Path path = Paths.get(groupPath, fileName);
				if (!containsImageFile(path))
				{
					Logger.println("Loading icon: " + path);
				}
				BufferedImage icon;
				BufferedImage mask;

				icon = getImageFromFile(path);
				mask = getOrCreateImage("mask " + path.toString(), () -> IconDrawer.createMask(icon));

				imagesPerGroup.add(groupName, new Tuple2<>(icon, mask));
			}
		}
		return imagesPerGroup;
	}

	public List<BufferedImage> loadIconGroup(IconType iconType, String groupName)
	{
		String[] fileNames = getIconGroupFileNames(iconType, groupName);
		String groupPath = getIconGroupPath(iconType, groupName);
		List<BufferedImage> result = new ArrayList<>();

		for (String fileName : fileNames)
		{
			Path path = Paths.get(groupPath, fileName);
			BufferedImage icon;

			icon = getImageFromFile(path);

			result.add(icon);
		}
		return result;
	}

	/**
	 * Loads icons which do not have groups, but which do have default widths in
	 * the file names.
	 * 
	 * @return A map from icon names (not including width or extension) to a
	 *         tuple with the icon, mask, and width.
	 */
	public Map<String, Tuple3<BufferedImage, BufferedImage, Integer>> getIconsWithWidths(IconType iconType, String groupName)
	{
		return iconsWithWidthsCache.getOrCreate(iconType,() -> new ConcurrentHashMapF<>())
				.getOrCreate(groupName == null ? "" : groupName, () -> loadIconsWithWidths(iconType, groupName));
	}

	private Map<String, Tuple3<BufferedImage, BufferedImage, Integer>> loadIconsWithWidths(IconType iconType, String groupName)
	{
		Map<String, Tuple3<BufferedImage, BufferedImage, Integer>> imagesAndMasks = new HashMap<>();
		String[] fileNames = getIconGroupFileNames(iconType, groupName);
		if (fileNames.length == 0)
		{
			return imagesAndMasks;
		}

		for (String fileName : fileNames)
		{
			String[] parts = FilenameUtils.getBaseName(fileName).split("width=");
			if (parts.length < 2)
			{
				throw new RuntimeException("The icon " + fileName + " of type " + iconType
						+ " must have its default width stored at the end of the file name in the format width=<number>. Example: myCityIcon width=64.png.");
			}

			String fileNameBaseWithoutWidth = getFileNameBaseWithoutWidth(fileName);
			if (imagesAndMasks.containsKey(fileNameBaseWithoutWidth))
			{
				throw new RuntimeException("There are multiple icons for " + iconType + " named '" + fileNameBaseWithoutWidth
						+ "' whose file names only differ by their width." + " Rename one of them.");
			}

			Path path = Paths.get(getIconGroupPath(iconType, groupName), fileName);
			if (!containsImageFile(path))
			{
				Logger.println("Loading icon: " + path);
			}
			BufferedImage icon;
			BufferedImage mask;

			icon = getImageFromFile(path);
			mask = getOrCreateImage("mask " + path.toString(), () -> IconDrawer.createMask(icon));

			int width;
			try
			{
				String widthStr = parts[parts.length - 1];
				width = Integer.parseInt(widthStr);
			}
			catch (RuntimeException e)
			{
				throw new RuntimeException("Unable to load icon " + path.toString()
						+ ". Make sure the default width of the image is stored at the end of the file name in the format width=<number>. Example: myCityIcon width=64.png. Error: "
						+ e.getMessage(), e);
			}
			imagesAndMasks.put(fileNameBaseWithoutWidth, new Tuple3<>(icon, mask, width));
		}

		return imagesAndMasks;
	}

	public Set<String> getIconGroupFileNamesWithoutWidthOrExtension(IconType iconType, String groupName)
	{
		String[] folderNames = getIconGroupFileNames(iconType, groupName);
		Set<String> result = new TreeSet<String>();
		for (int i : new Range(folderNames.length))
		{
			result.add(getFileNameBaseWithoutWidth(folderNames[i]));
		}
		return result;
	}

	private static String getFileNameBaseWithoutWidth(String fileName)
	{
		if (fileName.contains("width="))
		{
			return fileName.substring(0, fileName.lastIndexOf("width="));
		}
		else
		{
			return fileName;
		}
	}

	public Set<String> loadIconGroupNames(IconType iconType)
	{
		String path = Paths.get(imagesPath, "icons", iconType.toString()).toString();

		String[] folderNames = new File(path).list(new FilenameFilter()
		{
			@Override
			public boolean accept(File dir, String name)
			{
				File file = new File(dir, name);
				return file.isDirectory();
			}
		});

		if (folderNames == null)
		{
			return new TreeSet<>();
		}

		Arrays.sort(folderNames);
		Set<String> result = new TreeSet<>();
		for (String folderName : folderNames)
		{
			result.add(folderName);
		}
		return result;
	}

	/**
	 * Gets the names of icon groups, which are folders under the icon type
	 * folder which contain images files.
	 * 
	 * @param iconType
	 *            Name of a folder under assets/icons
	 * @return Array of file names sorted with no duplicates
	 */
	public Set<String> getIconGroupNames(IconType iconType)
	{
		return iconGroupNames.getOrCreate(iconType, () -> loadIconGroupNames(iconType));
	}

	private String[] getIconGroupFileNames(IconType iconType, String groupName)
	{
		String groupNameToUse = groupName == null ? "" : groupName;
		return iconGroupFilesNamesCache.getOrCreate(iconType, () -> new ConcurrentHashMapF<>())
				.getOrCreate(groupNameToUse, () -> loadIconGroupFileNames(iconType, groupNameToUse));
	}

	private String[] loadIconGroupFileNames(IconType iconType, String groupName)
	{
		String path = getIconGroupPath(iconType, groupName);

		String[] fileNames = new File(path).list(new FilenameFilter()
		{
			@Override
			public boolean accept(File dir, String name)
			{
				File file = new File(dir, name);
				return !file.isDirectory();
			}
		});

		if (fileNames == null)
		{
			return new String[] {};
		}

		Arrays.sort(fileNames);
		return fileNames;
	}

	private String getIconGroupPath(IconType iconType, String groupName)
	{
		return Paths.get(imagesPath, "icons", iconType.toString(), groupName).toString();
	}

	public static void clear()
	{
		instances.clear();
	}
}
