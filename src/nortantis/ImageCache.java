package nortantis;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.imgscalr.Scalr.Method;

import nortantis.geom.IntDimension;
import nortantis.platform.Image;
import nortantis.util.Assets;
import nortantis.util.ConcurrentHashMapF;
import nortantis.util.FileHelper;
import nortantis.util.HashMapF;
import nortantis.util.Helper;
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
	private ConcurrentHashMapF<Image, ConcurrentHashMapF<IntDimension, Image>> scaledCache;

	/**
	 * Maps file path (or any string key) to images.
	 */
	private ConcurrentHashMapF<String, Image> fileCache;

	/**
	 * Maps icon type > icon sub-type name > lists of icons of that sub-type.
	 */
	private ConcurrentHashMapF<IconType, ListMap<String, ImageAndMasks>> iconGroupsAndMasksCache;

	/**
	 * Maps icon type > icon group name > lists of icons, masks, and icon widths, of that sub-type.
	 */
	private ConcurrentHashMapF<IconType, ConcurrentHashMapF<String, Map<String, Tuple2<ImageAndMasks, Integer>>>> iconsWithWidthsCache;

	private ConcurrentHashMapF<IconType, ConcurrentHashMapF<String, List<String>>> iconGroupFilesNamesCache;

	private ConcurrentHashMapF<IconType, List<String>> iconGroupNames;

	private String imagesPath;

	/**
	 * Singleton
	 */
	private ImageCache(String imagesPath)
	{
		this.imagesPath = imagesPath;
		scaledCache = new ConcurrentHashMapF<>();
		fileCache = new ConcurrentHashMapF<>();
		iconGroupsAndMasksCache = new ConcurrentHashMapF<>();
		iconsWithWidthsCache = new ConcurrentHashMapF<>();
		iconGroupFilesNamesCache = new ConcurrentHashMapF<>();
		iconGroupNames = new ConcurrentHashMapF<>();
	}

	/**
	 * Gets the image cache instance associated with the given imagesPath. When imagesPath is null or empty, then the imageCache for the
	 * installed images is given.
	 * 
	 * @param imagesPath
	 * @return
	 */
	private synchronized static ImageCache getInstance(String imagesPath)
	{
		String pathWithHomeReplaced;
		if (StringUtils.isEmpty(imagesPath))
		{
			imagesPath = Assets.getArtPackPath(Assets.installedArtPack, null).toString();
			pathWithHomeReplaced = imagesPath;
		}
		else
		{
			pathWithHomeReplaced = FileHelper.replaceHomeFolderPlaceholder(imagesPath);
		}

		// Probably not necessary, but I don't want to take a chance of accidentally creating multiple ImageCache instances.
		String normalizedPath = FilenameUtils.normalize(pathWithHomeReplaced);

		return instances.getOrCreate(normalizedPath, () -> new ImageCache(normalizedPath));
	}

	public synchronized static ImageCache getInstance(String artPack, String customImagesFolder)
	{
		Path artPackPath = Assets.getArtPackPath(artPack, customImagesFolder);
		return getInstance(artPackPath.toString());
	}

	/**
	 * Either looks up in the cache, or creates, a version of the given icon with the given width.
	 * 
	 * @param icon
	 *            Original image (not scaled)
	 * @param width
	 *            The desired width
	 * @return A scaled image
	 */
	public Image getScaledImage(Image icon, IntDimension size)
	{
		// There is a small chance the 2 different threads might both add the
		// same image at the same time,
		// but if that did happen it would only results in a little bit of
		// duplicated work, not a functional
		// problem.
		return scaledCache.getOrCreate(icon, () -> new ConcurrentHashMapF<>()).getOrCreate(size,
				() -> ImageHelper.scale(icon, size.width, size.height, Method.QUALITY));
	}

	public Image getImageFromFile(Path path)
	{
		return fileCache.getOrCreate(path.toString(), () -> Assets.readImage(path.toString()));
	}

	public boolean containsImageFile(Path path)
	{
		return fileCache.containsKey(path.toString());
	}

	/**
	 * Loads or retrieves cache for groups if icons of a given type.
	 * 
	 * @returns A map of icon type > icon sub-type name > lists of icons of that sub-type. The first image in the tuple is the icon. The
	 *          second image is the mask, which is generated based on the image loaded from disk.
	 */
	public ListMap<String, ImageAndMasks> getAllIconGroupsAndMasksForType(IconType iconType)
	{
		return iconGroupsAndMasksCache.getOrCreate(iconType, () -> loadAllIconGroupsAndMasksForType(iconType));
	}

	private ListMap<String, ImageAndMasks> loadAllIconGroupsAndMasksForType(IconType iconType)
	{
		ListMap<String, ImageAndMasks> imagesPerGroup = new ListMap<>();

		List<String> groupNames = getIconGroupNames(iconType);
		for (String groupName : groupNames)
		{
			List<String> fileNames = getIconGroupFileNames(iconType, groupName);
			if (fileNames.size() == 0)
			{
				continue;
			}

			for (String fileName : fileNames)
			{
				Image icon = loadIconFromDiskOrCache(iconType, groupName, fileName);
				imagesPerGroup.add(groupName, new ImageAndMasks(icon, iconType));
			}
		}
		return imagesPerGroup;
	}

	public List<ImageAndMasks> loadIconGroup(IconType iconType, String groupName)
	{
		List<String> fileNames = getIconGroupFileNames(iconType, groupName);
		String groupPath = getIconGroupPath(iconType, groupName);
		List<ImageAndMasks> result = new ArrayList<>();

		for (String fileName : fileNames)
		{
			Path path = Paths.get(groupPath, fileName);
			Image icon;

			icon = getImageFromFile(path);

			result.add(new ImageAndMasks(icon, iconType));
		}
		return result;
	}

	/**
	 * Loads icons which do not have groups, but which do have default widths in the file names.
	 * 
	 * @return A map from icon names (not including width or extension) to a tuple with the icon, mask, and width.
	 */
	public Map<String, Tuple2<ImageAndMasks, Integer>> getIconsWithWidths(IconType iconType, String groupName)
	{
		return iconsWithWidthsCache.getOrCreate(iconType, () -> new ConcurrentHashMapF<>()).getOrCreate(groupName == null ? "" : groupName,
				() -> loadIconsWithWidths(iconType, groupName));
	}

	private Map<String, Tuple2<ImageAndMasks, Integer>> loadIconsWithWidths(IconType iconType, String groupName)
	{
		Map<String, Tuple2<ImageAndMasks, Integer>> imagesAndMasks = new HashMap<>();
		if (groupName == null || groupName.isEmpty())
		{
			return imagesAndMasks;
		}

		List<String> fileNames = getIconGroupFileNames(iconType, groupName);
		if (fileNames.size() == 0)
		{
			return imagesAndMasks;
		}

		// Maps from image base name without width to (image base name without width, width, file name).
		Map<String, Tuple3<String, Integer, String>> namesAndWidths = new TreeMap<>();
		for (String fileName : fileNames)
		{
			Tuple2<String, Integer> nameAndWidth = parseBaseNameAndWidth(fileName);

			String fileNameBaseWithoutWidth = nameAndWidth.getFirst();
			if (namesAndWidths.containsKey(fileNameBaseWithoutWidth))
			{
				throw new RuntimeException("There are multiple images for " + iconType + " named '" + fileNameBaseWithoutWidth
						+ "' whose file names only differ by their encoded width or extension." + " Rename one of them.");
			}

			namesAndWidths.put(fileNameBaseWithoutWidth, new Tuple3<>(nameAndWidth.getFirst(), nameAndWidth.getSecond(), fileName));
		}

		Tuple3<String, Integer, String> widest = Helper.maxElement(namesAndWidths, (tuple1, tuple2) ->
		{
			Integer w1 = tuple1.getSecond();
			Integer w2 = tuple2.getSecond();
			if (w1 == null && w2 == null)
			{
				return 0;
			}
			if (w1 == null)
			{
				return -1;
			}
			if (w2 == null)
			{
				return 1;
			}
			return w1.compareTo(w2);
		});

		if (widest.getSecond() == null)
		{
			throw new RuntimeException("The " + iconType + " image group '" + groupName
					+ "' must have at least one image with a default width encoded in either the format width=<number> or w<number>. Example: \"small town w20.png.\""
					+ " Images in that group that don't have an encoded width will use a default width calculated relative to the largest image in the group that does have an encoded width.");
		}
		Image widestIcon = loadIconFromDiskOrCache(iconType, groupName, widest.getThird());

		for (Tuple3<String, Integer, String> nameAndWidth : namesAndWidths.values())
		{
			Image icon = loadIconFromDiskOrCache(iconType, groupName, nameAndWidth.getThird());

			int width;
			// If any don't have an encoded width, then calculate the width relative to the largest image that does have an encoded width.
			if (nameAndWidth.getSecond() == null)
			{
				width = (int) (icon.getWidth() * (((double) widest.getSecond()) / widestIcon.getWidth()));
			}
			else
			{
				width = nameAndWidth.getSecond();
			}


			imagesAndMasks.put(nameAndWidth.getFirst(), new Tuple2<>(new ImageAndMasks(icon, iconType), width));
		}


		return imagesAndMasks;
	}

	private Image loadIconFromDiskOrCache(IconType iconType, String groupName, String fileName)
	{
		Path path = Paths.get(getIconGroupPath(iconType, groupName), fileName);
		if (!containsImageFile(path))
		{
			Logger.println("Loading icon: " + path);
		}
		Image icon = getImageFromFile(path);
		return icon;
	}

	/**
	 * Given a file name (without the path), this parses out the width encoded in the file name, along with the rest of the file name, not
	 * including the extension. The width can be encoded as "width=[number]", or as "w[number]" for short. The width must be delimited from
	 * the rest of the file name by either a space or underscore.
	 * 
	 * Example: fileName: "large castle w2.png" result: ("large castle", 2)
	 * 
	 * @param fileName
	 *            Image file name, including extension but excluding file path.
	 * @return A 2-tuple, where the first item is the file name without the width encoded string. Note that spaces and underscores that
	 *         delimited the width encoded string are also removed. The second item is the width, or null if the file name did not contain a
	 *         width.
	 */
	public static Tuple2<String, Integer> parseBaseNameAndWidth(String fileName)
	{
		if (fileName == null || fileName.isEmpty())
		{
			return new Tuple2<>(null, null);
		}

		// Remove the file extension
		int dotIndex = fileName.lastIndexOf('.');
		String baseName = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);

		// Define the regex pattern for width
		Pattern pattern = Pattern.compile("(.*?)(?:\\s|_)(?:width=|w)(\\d+)");
		Matcher matcher = pattern.matcher(baseName);

		if (matcher.find())
		{
			String nameWithoutWidth = matcher.group(1).trim();
			Integer width = Integer.parseInt(matcher.group(2));
			return new Tuple2<>(nameWithoutWidth, width);
		}

		return new Tuple2<>(baseName, null);
	}

	public Set<String> getIconGroupFileNamesWithoutWidthOrExtension(IconType iconType, String groupName)
	{
		List<String> fileNames = getIconGroupFileNames(iconType, groupName);
		Set<String> result = new TreeSet<String>();
		for (int i : new Range(fileNames.size()))
		{
			result.add(parseBaseNameAndWidth(fileNames.get(i)).getFirst());
		}
		return result;
	}

	public List<String> loadIconGroupNames(IconType iconType)
	{
		String path = Paths.get(imagesPath, iconType.toString()).toString();

		List<String> folderNames = Assets.listNonEmptySubFolders(path);

		if (folderNames == null)
		{
			return new ArrayList<>();
		}

		Collections.sort(folderNames, String.CASE_INSENSITIVE_ORDER);

		return folderNames;
	}

	/**
	 * Gets the names of icon groups, which are folders under the icon type folder which contain images files.
	 * 
	 * @param iconType
	 *            Name of a folder under assets/icons
	 * @return Array of file names sorted with no duplicates
	 */
	public List<String> getIconGroupNames(IconType iconType)
	{
		return iconGroupNames.getOrCreate(iconType, () -> loadIconGroupNames(iconType));
	}

	private List<String> getIconGroupFileNames(IconType iconType, String groupName)
	{
		String groupNameToUse = groupName == null ? "" : groupName;
		return iconGroupFilesNamesCache.getOrCreate(iconType, () -> new ConcurrentHashMapF<>()).getOrCreate(groupNameToUse,
				() -> loadIconGroupFileNames(iconType, groupNameToUse));
	}

	public boolean hasNamedIcon(IconType iconType, String groupName, String iconName)
	{
		if (!getIconGroupNames(iconType).contains(groupName))
		{
			return false;
		}

		return getIconGroupFileNamesWithoutWidthOrExtension(iconType, groupName).contains(iconName);
	}

	public boolean hasGroupName(IconType iconType, String groupName)
	{
		List<String> groupNames = getIconGroupNames(iconType);
		if (groupNames == null)
		{
			return false;
		}
		return groupNames.contains(groupName);
	}

	private List<String> loadIconGroupFileNames(IconType iconType, String groupName)
	{
		String path = getIconGroupPath(iconType, groupName);
		return Assets.listFileNames(path);
	}

	private String getIconGroupPath(IconType iconType, String groupName)
	{
		return Paths.get(imagesPath, iconType.toString(), groupName).toString();
	}

	public static void clear()
	{
		instances.clear();
		// Also clear the assets cache so that any change to the list of art packs becomes visible.
		Assets.clearArtPackCache();
	}
}
