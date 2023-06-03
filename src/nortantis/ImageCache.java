package nortantis;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.commons.io.FilenameUtils;

import nortantis.util.AssetsPath;
import nortantis.util.ConcurrentHashMapF;
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
	private static ImageCache instance;
	
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
	 * The first image in the tuple is the icon. The second image is the mask, which is generated based on the image loaded from disk.
	 */
	private ConcurrentHashMapF<IconType, ListMap<String, Tuple2<BufferedImage, BufferedImage>>> iconGroupsAndMasksCache;
	
	/**
	 * Maps icon type > icon set name > icon sub-type name > lists of icons, masks, and icon widths, of that sub-type.
	 */
	private ConcurrentHashMapF<IconType, ConcurrentHashMapF<String, Map<String, Tuple3<BufferedImage, BufferedImage, Integer>>>> iconsWithWidthsCache;
	
	private ConcurrentHashMapF<IconType, Set<String>> iconSetsCache;
	
	private ConcurrentHashMapF<IconType, ConcurrentHashMapF<String, ConcurrentHashMapF<String, String[]>>> iconGroupFilesNamesCache;
	
	private ConcurrentHashMapF<IconType, String[]> iconGroupNames;

	/**
	 * Singleton
	 */
	private ImageCache()
	{
		scaledCache = new ConcurrentHashMapF<>();
		fileCache = new ConcurrentHashMapF<>();
		generatedImageCache = new ConcurrentHashMapF<>();
		iconGroupsAndMasksCache = new ConcurrentHashMapF<>();
		iconsWithWidthsCache = new ConcurrentHashMapF<>();
		iconSetsCache = new ConcurrentHashMapF<>();
		iconGroupFilesNamesCache = new ConcurrentHashMapF<>();
		iconGroupNames = new ConcurrentHashMapF<>();
	}
	
	public synchronized static ImageCache getInstance()
	{
		if (instance == null)
			instance = new ImageCache();
		return instance;
	}
	
	/**
	 * Either looks up in the cache, or creates, a version of the given icon with the given width.
	 * @param icon Original image (not scaled)
	 * @param width The desired width
	 * @return A scaled image
	 */
	public BufferedImage getScaledImageByWidth(BufferedImage icon, int width)
	{
		Dimension dimension = new Dimension(width, ImageHelper.getHeightWhenScaledByWidth(icon, width));
		// There is a small chance the 2 different threads might both add the same image at the same time, 
		// but if that did happen it would only results in a little bit of duplicated work, not a functional
		// problem.
		return scaledCache.getOrCreate(icon, () -> new ConcurrentHashMapF<>()).getOrCreate(dimension, 
				() -> ImageHelper.scaleByWidth(icon, width));
	}
	
	/**
	 * Either looks up in the cache, or creates, a version of the given icon with the given height.
	 * @param icon Original image (not scaled)
	 * @param width The desired width
	 * @return A scaled image
	 */
	public BufferedImage getScaledImageByHeight(BufferedImage icon, int height)
	{
		Dimension dimension = new Dimension(ImageHelper.getWidthWhenScaledByHeight(icon, height), height);
		// There is a small chance the 2 different threads might both add the same image at the same time, 
		// but if that did happen it would only results in a little bit of duplicated work, not a functional
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
	 * @returns A map of icon type > icon sub-type name > lists of icons of that sub-type. 
	 *          The first image in the tuple is the icon. The second image is the mask, which is generated based on the image loaded from disk.
	 */
	public ListMap<String, Tuple2<BufferedImage, BufferedImage>> getAllIconGroupsAndMasksForType(IconType iconType)
	{
		return iconGroupsAndMasksCache.getOrCreate(iconType, () -> loadAllIconGroupsAndMasksForType(iconType));
	}
	
	private ListMap<String, Tuple2<BufferedImage, BufferedImage>> loadAllIconGroupsAndMasksForType(IconType iconType)
	{
		return loadAllIconGroupsAndMasksForSetAndType(null, iconType);
	}
	
	private ListMap<String, Tuple2<BufferedImage, BufferedImage>> loadAllIconGroupsAndMasksForSetAndType(String iconSetName, 
			IconType iconType)
	{
		ListMap<String, Tuple2<BufferedImage, BufferedImage>> imagesPerGroup = new ListMap<>();

		String[] groupNames = getIconGroupNames(iconType);
		for (String groupName : groupNames)
		{
			String[] fileNames = getIconGroupFileNames(iconType, groupName, iconSetName);
			String groupPath = getIconGroupPath(iconType, groupName, iconSetName);
			if (fileNames.length == 0)
			{
				continue;
			}
	
			for (String fileName : fileNames)
			{
				Path path = Paths.get(groupPath, fileName);
				if (!ImageCache.getInstance().containsImageFile(path))
				{
					Logger.println("Loading icon: " + path);
				}
				BufferedImage icon;
				BufferedImage mask;
				
				icon = ImageCache.getInstance().getImageFromFile(path);
				mask = ImageCache.getInstance().getOrCreateImage("mask " + path.toString(), () -> IconDrawer.createMask(icon));
	
				imagesPerGroup.add(groupName, new Tuple2<>(icon, mask));
			}
		}
		return imagesPerGroup;
	}
	
	/**
	 * Loads icons which do not have groups, but which do have default widths in the file names.
	 * 
	 * @return A map from icon names (not including width or extension) to a tuple with the icon, mask, and width.
	 */
	public Map<String, Tuple3<BufferedImage, BufferedImage, Integer>> getIconsWithWidths(IconType iconType, String iconSetName)
	{	
		return iconsWithWidthsCache.getOrCreate(iconType, 
				() ->  new ConcurrentHashMapF<>()).getOrCreate(iconSetName == null ? "" : iconSetName,
				() -> loadIconsWithWidths(iconType, iconSetName));
	}

	private Map<String, Tuple3<BufferedImage, BufferedImage, Integer>> loadIconsWithWidths(IconType iconType, String iconSetName)
	{
		Map<String, Tuple3<BufferedImage, BufferedImage, Integer>> imagesAndMasks = new HashMap<>();
		String[] fileNames = getIconGroupFileNames(iconType, null, iconSetName);
		if (fileNames.length == 0)
		{
			return imagesAndMasks;
		}
		
		for (String fileName : fileNames)
		{
			String[] parts = FilenameUtils.getBaseName(fileName).split("width=");
			if (parts.length < 2)
			{
				throw new RuntimeException("The icon " + fileName + " of type " + iconType + " must have its default width stored at the end of the file name in the format width=<number>. Example: myCityIcon width=64.png.");
			}
			
			String fileNameBaseWithoutWidth = getFileNameBaseWithoutWidth(fileName);
			if (imagesAndMasks.containsKey(fileNameBaseWithoutWidth))
			{
				throw new RuntimeException("There are multiple icons for " + iconType + " named '" + fileNameBaseWithoutWidth + "' whose file names only differ by the width."
						+ " Rename one of them");
			}

			Path path = Paths.get(getIconGroupPath(iconType, null, iconSetName), fileName);
			if (!ImageCache.getInstance().containsImageFile(path))
			{
				Logger.println("Loading icon: " + path);
			}
			BufferedImage icon;
			BufferedImage mask;
			
			icon = ImageCache.getInstance().getImageFromFile(path);
			mask = ImageCache.getInstance().getOrCreateImage("mask " + path.toString(), () -> IconDrawer.createMask(icon));
			
			
			int width;
			try
			{
				String widthStr = parts[parts.length - 1];
				width = Integer.parseInt(widthStr);
			}
			catch (RuntimeException e)
			{
				throw new RuntimeException("Unable to load icon " + path.toString() + ". Make sure the default width of the image is stored at the end of the file name in the format width=<number>. Example: myCityIcon width=64.png. Error: " + e.getMessage(), e);
			}
			imagesAndMasks.put(fileNameBaseWithoutWidth, new Tuple3<>(icon, mask, width));
		}
		
		return imagesAndMasks;
	}
	
	public Set<String> getIconSets(IconType iconType)
	{
		return iconSetsCache.getOrCreate(iconType, () -> loadIconSets(iconType));
	}
	
	private Set<String> loadIconSets(IconType iconType)
	{
		if (!doesUseSets(iconType))
		{
			throw new RuntimeException("Type '" + iconType + "' does not use sets.");
		}
		
		String path = Paths.get(AssetsPath.get(), "icons", iconType.toString()).toString();
		String[] folderNames = new File(path).list(new FilenameFilter()
		{
			@Override
			public boolean accept(File dir, String name)
			{
				File file = new File(dir, name);
				return file.isDirectory();
			}
		});
		
		Set<String> result = new HashSet<>();
		result.addAll(Arrays.asList(folderNames));
		return result;
	}
	
	public Set<String> getIconGroupFileNamesWithoutWidthOrExtension(IconType iconType, String groupName, String cityIconSetName)
	{
		String[] folderNames = getIconGroupFileNames(iconType, groupName, cityIconSetName);
		Set<String> result = new HashSet<String>();
		for (int i : new Range(folderNames.length))
		{
			result.add(getFileNameBaseWithoutWidth(folderNames[i]));
		}
		return result;
	}
	
	private static String getFileNameBaseWithoutWidth(String fileName)
	{
		return fileName.substring(0, fileName.lastIndexOf("width="));
	}
	
	/**
	 * Gets the names of icon groups, which are folders under the icon type folder or the icon set folder which
	 * contain images files.
	 * @param iconType Name of a folder under assets/icons
	 * @param setName Optional - for icon types that support it, it is a folder under assets/icons/<iconType>/
	 * @return Array of file names sorted with no duplicates
	 */
	public String[] getIconGroupNames(IconType iconType, String setName)
	{
		return iconGroupNames.getOrCreate(iconType, () -> loadIconGroupNames(iconType, setName));
	}
	
	public static String[] loadIconGroupNames(IconType iconType, String setName)
	{
		String path;
		if (doesUseSets(iconType))
		{
			if (setName == null || setName.isEmpty())
			{
				return new String[] {};
			}
			path = Paths.get(AssetsPath.get(), "icons", iconType.toString(), setName).toString();
		}
		else
		{
			path = Paths.get(AssetsPath.get(), "icons", iconType.toString()).toString();
		}
		
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
			return new String[] {};
		}
		
		Arrays.sort(folderNames);
		return folderNames;
	}
	
	public String[] getIconGroupNames(IconType iconType)
	{
		if (doesUseSets(iconType))
		{
			throw new IllegalArgumentException("Icon type " + iconType + " uses sets, so a set must be passed in.");
		}
		
		return getIconGroupNames(iconType, "");
	}
	
	private String[] getIconGroupFileNames(IconType iconType, String groupName, String setName)
	{
		return iconGroupFilesNamesCache.getOrCreate(iconType, 
				() ->  new ConcurrentHashMapF<>()).getOrCreate(groupName == null ? "" : groupName,
				() -> new ConcurrentHashMapF<>()).getOrCreate(setName == null ? "" : setName, 
				() -> loadIconGroupFileNames(iconType, groupName, setName));
	}
	
	private static String[] loadIconGroupFileNames(IconType iconType, String groupName, String setName)
	{
		String path = getIconGroupPath(iconType, groupName, setName);
		
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
	
	private static String getIconGroupPath(IconType iconType, String groupName, String setName)
	{
		String path;
		if (iconType == IconType.cities)
		{
			path = Paths.get(AssetsPath.get(), "icons", iconType.toString(), setName).toString();
		}
		else
		{
			if (doesUseSets(iconType))
			{
				// Not used yet
				
				if (setName == null || setName.isEmpty())
				{
					throw new IllegalArgumentException("The icon type " + iconType + " uses sets, but no set name was given.");
				}
				path = Paths.get(AssetsPath.get(), "icons", iconType.toString(), groupName, setName).toString(); 
			}
			else
			{
				path = Paths.get(AssetsPath.get(), "icons", iconType.toString(), groupName).toString();
			}
		}
		return path;
	}
	
	/**
	 * Tells whether an icon type supports icon sets. Icon sets are an additional folder under the icon type folder
	 * which is the name of the icon set. Under the icon set folder either image files or group folders of image files.
	 * 
	 * If an icon type supports sets, it should also return a value from getSetName.
	 * @param iconType
	 * @return
	 */
	private static boolean doesUseSets(IconType iconType)
	{
		return iconType == IconType.cities;
	}
	
	public void clear()
	{
		scaledCache.clear();
		fileCache.clear();
		generatedImageCache.clear();
		iconGroupFilesNamesCache.clear();
		iconSetsCache.clear();
		iconsWithWidthsCache.clear();
		iconGroupsAndMasksCache.clear();
		iconGroupNames.clear();
	}
}
