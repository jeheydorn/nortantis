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

import nortantis.editor.FreeIcon;
import nortantis.geom.IntDimension;
import nortantis.platform.Color;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
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
	 * Maps original images, to color, colored images.
	 */
	private ConcurrentHashMapF<Image, ConcurrentHashMapF<Color, Image>> coloredCache;

	/**
	 * Maps file path (or any string key) to images.
	 */
	private ConcurrentHashMapF<String, Image> fileCache;

	/**
	 * Maps icon type > icon group name > lists of icons and masks.
	 */
	private ConcurrentHashMapF<IconType, ConcurrentHashMapF<String, Map<String, ImageAndMasks>>> iconsWithSizesCache;

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
		coloredCache = new ConcurrentHashMapF<>();
		fileCache = new ConcurrentHashMapF<>();
		iconsWithSizesCache = new ConcurrentHashMapF<>();
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
	
	// TODO remove this if I don't end up using it.
	/**
	 * Either looks up in the cache, or creates, a version of the given icon colored the given color.
	 */
	public Image getColoredImage(ImageAndMasks imageAndMasks, Color color)
	{
		// There is a small chance the 2 different threads might both add the
		// same image at the same time,
		// but if that did happen it would only results in a little bit of
		// duplicated work, not a functional
		// problem.
		return coloredCache.getOrCreate(imageAndMasks.image, () -> new ConcurrentHashMapF<>()).getOrCreate(color,
				() -> {
				
					Image result = Image.create(imageAndMasks.image.getWidth(), imageAndMasks.image.getHeight(), ImageType.ARGB);
					for (int y = 0; y < result.getHeight(); y++)
						for (int x = 0; x < result.getWidth(); x++)
						{
							Color originalColor = imageAndMasks.image.getPixelColor(x, y);
							int alpha = originalColor.getAlpha();
							int r = Helper.linearComboBase255(alpha, originalColor.getRed(), color.getRed());
							int g = Helper.linearComboBase255(alpha, originalColor.getGreen(), color.getGreen());
							int b = Helper.linearComboBase255(alpha, originalColor.getBlue(), color.getBlue());
							int a = Math.max(alpha, Math.min(color.getAlpha(), imageAndMasks.getOrCreateColorMask().getGrayLevel(x, y)));

							result.setPixelColor(x, y, Color.create(r, g, b, a));
						}
					return result;
				});
	}

	public Image getImageFromFile(Path path)
	{
		return fileCache.getOrCreate(path.toString(), () -> Assets.readImage(path.toString()));
	}

	public boolean containsImageFile(Path path)
	{
		return fileCache.containsKey(path.toString());
	}
	
	public ImageAndMasks getImageAndMasks(FreeIcon icon)
	{
		if (!StringUtils.isEmpty(icon.iconName))
		{
			Map<String, ImageAndMasks> map = getIconsByNameForGroup(icon.type, icon.groupId);
			if (map == null || map.isEmpty() || !map.containsKey(icon.iconName))
			{
				return null;
			}
			
			return map.get(icon.iconName);
		}
		else
		{
			List<ImageAndMasks> imagesInGroup = getIconsInGroup(icon.type, icon.groupId);
			if (imagesInGroup == null || imagesInGroup.isEmpty())
			{
				return null;
			}
			
			return imagesInGroup.get(icon.iconIndex % imagesInGroup.size());
		}
	}

	public List<ImageAndMasks> getIconsInGroup(IconType iconType, String groupName)
	{
		Map<String, ImageAndMasks> map = getIconsByNameForGroup(iconType, groupName);
		List<ImageAndMasks> result = new ArrayList<>();
		TreeSet<String> namesSorted = new TreeSet<>(map.keySet());
		for (String name : namesSorted)
		{
			result.add(map.get(name));
		}
		return result;
	}

	public ListMap<String, ImageAndMasks> getIconGroupsAsListsForType(IconType iconType)
	{
		ListMap<String, ImageAndMasks> result = new ListMap<>();
		for (String groupName : getIconGroupNames(iconType))
		{
			List<ImageAndMasks> iconsInGroup = getIconsInGroup(iconType, groupName);
			if (iconsInGroup.isEmpty())
			{
				continue;
			}
			result.put(groupName, iconsInGroup);
		}

		return result;
	}

	/**
	 * Loads icons with their respective width or height as encoded in their file names or calculated if not encoded.
	 * 
	 * @return A map from icon names (not including width or height or extension) to a tuple with the icon, mask, and width or height.
	 */
	public Map<String, ImageAndMasks> getIconsByNameForGroup(IconType iconType, String groupName)
	{
		return iconsWithSizesCache.getOrCreate(iconType, () -> new ConcurrentHashMapF<>()).getOrCreate(groupName == null ? "" : groupName,
				() -> loadIconsWithSizes(iconType, groupName));
	}

	private Map<String, ImageAndMasks> loadIconsWithSizes(IconType iconType, String groupName)
	{
		Map<String, ImageAndMasks> imagesAndMasks = new HashMap<>();
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
		Map<String, Tuple3<String, Double, String>> namesAndWidths = new TreeMap<>();
		for (String fileName : fileNames)
		{
			Tuple2<String, Double> nameAndWidth = parseBaseNameAndSize(fileName, WhichDimension.Width);
			Tuple2<String, Double> nameAndHeight = parseBaseNameAndSize(fileName, WhichDimension.Height);
			if (nameAndWidth.getSecond() == null)
			{
				// Check if height is stored in the file name.
				if (nameAndHeight.getSecond() != null)
				{
					// Convert the height to a width using the aspect ratio of the icon.
					Image icon = loadIconFromDiskOrCache(iconType, groupName, fileName);
					double width = IconDrawer.getDimensionsWhenScaledByHeight(icon.size(), nameAndHeight.getSecond()).width;
					nameAndWidth = new Tuple2<>(nameAndHeight.getFirst(), width);
				}
			}
			else
			{
				if (nameAndHeight.getSecond() != null)
				{
					throw new RuntimeException("The image " + fileName + " has both an encoded height and width. Only one is allowed.");
				}
			}

			String fileNameBaseWithoutWidth = nameAndWidth.getFirst();
			if (namesAndWidths.containsKey(fileNameBaseWithoutWidth))
			{
				throw new RuntimeException("There are multiple images for " + iconType + " named '" + fileNameBaseWithoutWidth
						+ "' whose file names only differ by their encoded width or height or extension." + " Rename one of them.");
			}

			namesAndWidths.put(fileNameBaseWithoutWidth, new Tuple3<>(nameAndWidth.getFirst(), nameAndWidth.getSecond(), fileName));
		}

		Tuple2<Image, Double> tuple = findIconToUseForReferenceWhenSizingOtherIcons(iconType, groupName, namesAndWidths);
		Image widest = tuple.getFirst();
		double widthOfWidest = tuple.getSecond();

		for (Tuple3<String, Double, String> nameAndWidth : namesAndWidths.values())
		{
			Image icon = loadIconFromDiskOrCache(iconType, groupName, nameAndWidth.getThird());

			double width;
			// If any don't have an encoded width, then calculate the width relative to the largest image that does have an encoded width.
			if (nameAndWidth.getSecond() == null)
			{
				width = icon.getWidth() * (widthOfWidest / widest.getWidth());
			}
			else
			{
				width = nameAndWidth.getSecond();
			}


			imagesAndMasks.put(nameAndWidth.getFirst(), new ImageAndMasks(icon, iconType, width));
		}


		return imagesAndMasks;
	}

	private Tuple2<Image, Double> findIconToUseForReferenceWhenSizingOtherIcons(IconType iconType, String groupName,
			Map<String, Tuple3<String, Double, String>> namesAndWidths)
	{
		Tuple3<String, Double, String> widest = Helper.maxElement(namesAndWidths, (tuple1, tuple2) ->
		{
			Double w1 = tuple1.getSecond();
			Double w2 = tuple2.getSecond();
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

		if (widest.getSecond() != null)
		{
			Image widestIcon = loadIconFromDiskOrCache(iconType, groupName, widest.getThird());
			return new Tuple2<>(widestIcon, widest.getSecond());
		}
		else
		{
			Map<String, Image> iconsByName = new TreeMap<>();
			for (Tuple3<String, Double, String> nameAndWidth : namesAndWidths.values())
			{
				Image icon = loadIconFromDiskOrCache(iconType, groupName, nameAndWidth.getThird());
				iconsByName.put(nameAndWidth.getFirst(), icon);
			}
			String nameOfWidestOrTallest = Helper.argmax(iconsByName, (icon1, icon2) ->
			{
				IntDimension d1 = icon1.size();
				IntDimension d2 = icon2.size();
				return Integer.compare(d1.width, d2.width);
			});

			Image icon = iconsByName.get(nameOfWidestOrTallest);
			double widthOrHeight = getDefaultWidthOrHeight(iconType);
			double width = isDefaultSizeByWidth(iconType) ? widthOrHeight
					: IconDrawer.getDimensionsWhenScaledByHeight(icon.size(), widthOrHeight).width;
			return new Tuple2<>(icon, width);
		}
	}
	
	
	private boolean isDefaultSizeByWidth(IconType type)
	{
		return type != IconType.trees;
	}
	
	private static double getDefaultWidthOrHeight(IconType type)
	{
		if (type == IconType.mountains)
		{
			// width
			return 20.5;
		}
		else if (type == IconType.hills)
		{
			// width
			return 10.5;
		}
		else if (type == IconType.sand)
		{
			// width
			return 13;
		}
		else if (type == IconType.cities)
		{
			// width
			return 32;
		}
		else if (type == IconType.decorations)
		{
			return 45;
		}
		else if (type == IconType.trees)
		{
			// height
			return 20;
		}
		throw new IllegalArgumentException("Unrecognized icon type for getting default width or height: " + type);
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
	 * Given a file name (without the path), this parses out the width or height encoded in the file name, along with the rest of the file
	 * name, not including the extension. The width can be encoded as "width=[number]", or as "w[number]" for short, and height can be
	 * encoded as height=[number] or h[number]. The width or height must be delimited from the rest of the file name by either a space or
	 * underscore.
	 * 
	 * Example: fileName: "large castle w2.png", dimension=Width, result: ("large castle", 2)
	 * 
	 * @param fileName
	 *            Image file name, including extension but excluding file path.
	 * @param dimension
	 *            Whether to check for width vs height.
	 * @return A 2-tuple, where the first item is the file name without the width encoded string. Note that spaces and underscores that
	 *         delimited the width or height encoded string are also removed. The second item is the width or height, or null if the file
	 *         name did not contain an encoded size.
	 */
	public static Tuple2<String, Double> parseBaseNameAndSize(String fileName, WhichDimension dimension)
	{
		if (fileName == null || fileName.isEmpty())
		{
			return new Tuple2<>(null, null);
		}

		// Remove the file extension
		int dotIndex = fileName.lastIndexOf('.');
		String baseName = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);

		// Define the regex pattern for width
		Pattern pattern;
		if (dimension == WhichDimension.Width)
		{
			pattern = Pattern.compile("(.*?)(?:\\s|_)(?:width=|w)(\\d+)");
		}
		else
		{
			pattern = Pattern.compile("(.*?)(?:\\s|_)(?:height=|h)(\\d+)");
		}
		Matcher matcher = pattern.matcher(baseName);

		if (matcher.find())
		{
			String nameWithoutWidth = matcher.group(1).trim();
			Double size = (double) Integer.parseInt(matcher.group(2));
			if (size == 0.0)
			{
				throw new RuntimeException("The image '" + fileName + "' has an encoded width or height of 0.");
			}
			
			return new Tuple2<>(nameWithoutWidth, size);
		}

		return new Tuple2<>(baseName, null);
	}

	public enum WhichDimension
	{
		Width, Height
	}

	public Set<String> getIconGroupFileNamesWithoutWidthOrExtension(IconType iconType, String groupName)
	{
		List<String> fileNames = getIconGroupFileNames(iconType, groupName);
		Set<String> result = new TreeSet<String>();
		for (int i : new Range(fileNames.size()))
		{
			Tuple2<String, Double> nameAndWidth = parseBaseNameAndSize(fileNames.get(i), WhichDimension.Width);
			if (nameAndWidth.getSecond() == null)
			{
				Tuple2<String, Double> nameAndHeight = parseBaseNameAndSize(fileNames.get(i), WhichDimension.Height);
				result.add(nameAndHeight.getFirst());
			}
			else
			{
				result.add(nameAndWidth.getFirst());
			}

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
		if (!getIconGroupNames(iconType).contains(groupName))		{
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
		return Assets.listFileNames(path, Assets.allowedImageExtensions);
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
