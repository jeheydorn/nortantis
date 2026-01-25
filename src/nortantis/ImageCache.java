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
import nortantis.platform.PixelReader;
import nortantis.platform.PixelWriter;
import nortantis.util.Assets;
import nortantis.util.ConcurrentHashMapF;
import nortantis.util.FileHelper;
import nortantis.util.Helper;
import nortantis.util.ImageHelper;
import nortantis.util.ListMap;
import nortantis.util.Logger;
import nortantis.util.Range;
import nortantis.util.Tuple2;
import nortantis.util.Tuple4;

/**
 * Caches icons in memory to avoid recreating or reloading them.
 */
public class ImageCache
{
	private static ConcurrentHashMapF<String, ImageCache> instances = new ConcurrentHashMapF<>();

	/**
	 * Maps original images, to scaled width, to scaled images.
	 */
	private ConcurrentHashMapF<Image, ConcurrentHashMapF<IntDimension, Image>> scaledCache;

	/**
	 * Maps original images, to maps from (color, filterColor, maximizeOpacity, fillWithColor) to colored images.
	 */
	private ConcurrentHashMapF<String, ConcurrentHashMapF<Tuple4<Color, HSBColor, Boolean, Boolean>, Image>> coloredCache;

	private ConcurrentHashMapF<Image, ConcurrentHashMapF<Integer, Image>> alphaCache;

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

	private final String artPack;

	/**
	 * Singleton
	 */
	private ImageCache(String imagesPath, String artPack)
	{
		this.imagesPath = imagesPath;
		scaledCache = new ConcurrentHashMapF<>();
		coloredCache = new ConcurrentHashMapF<>();
		fileCache = new ConcurrentHashMapF<>();
		iconsWithSizesCache = new ConcurrentHashMapF<>();
		iconGroupFilesNamesCache = new ConcurrentHashMapF<>();
		iconGroupNames = new ConcurrentHashMapF<>();
		alphaCache = new ConcurrentHashMapF<>();
		this.artPack = artPack;
	}

	/**
	 * Gets the image cache instance associated with the given imagesPath. When imagesPath is null or empty, then the imageCache for the
	 * installed images is given.
	 */
	public static synchronized ImageCache getInstance(String artPack, String customImagesFolder)
	{
		Path artPackPath;
		String pathWithHomeReplaced;
		if (StringUtils.isEmpty(artPack))
		{
			artPackPath = Assets.getArtPackPath(Assets.installedArtPack, null);
			pathWithHomeReplaced = artPackPath.toString();
		}
		else
		{
			artPackPath = Assets.getArtPackPath(artPack, customImagesFolder);
			pathWithHomeReplaced = FileHelper.replaceHomeFolderPlaceholder(artPackPath.toString());
		}

		// Probably not necessary, but I don't want to take a chance of accidentally creating multiple ImageCache instances.
		String normalizedPath = FilenameUtils.normalize(pathWithHomeReplaced);

		return instances.getOrCreate(normalizedPath, () -> new ImageCache(normalizedPath, artPack));
	}

	/**
	 * Either looks up in the cache, or creates, a version of the given icon with the given size.
	 * 
	 * @param icon
	 *            Original image (not scaled)
	 * @param size
	 *            The desired size
	 * @return A scaled image
	 */
	public Image getScaledImage(Image icon, IntDimension size)
	{
		// There is a small chance the 2 different threads might both add the
		// same image at the same time,
		// but if that did happen it would only result in a little bit of
		// duplicated work, not a functional
		// problem.
		return scaledCache.getOrCreate(icon, () -> new ConcurrentHashMapF<>()).getOrCreate(size, () -> ImageHelper.scale(icon, size.width, size.height, Method.QUALITY));
	}

	/**
	 * Either looks up in the cache, or creates, a version of the given icon colored the given color.
	 */
	public Image getColoredIcon(ImageAndMasks imageAndMasks, Color fillColor, HSBColor filterColor, boolean maximizeOpacity, boolean fillWithColor)
	{
		assert imageAndMasks != null;
		assert fillColor != null;
		assert filterColor != null;

		// There is a small chance the 2 different threads might both add the
		// same image at the same time,
		// but if that did happen it would only result in a little bit of
		// duplicated work, not a functional
		// problem.
		return coloredCache.getOrCreate(imageAndMasks.createFileIdentifier(), () -> new ConcurrentHashMapF<>())
				.getOrCreateWithLock(new Tuple4<>(fillColor, filterColor, maximizeOpacity, fillWithColor), () ->
				{
					float alphaScale = 0;
					{
						if (maximizeOpacity)
						{
							try (PixelReader imagePixels = imageAndMasks.image.createPixelReader())
							{
								int highestAlpha = 0;
								for (int y = 0; y < imageAndMasks.image.getHeight(); y++)
								{
									for (int x = 0; x < imageAndMasks.image.getWidth(); x++)
									{
										Color imageColor = imagePixels.getPixelColor(x, y);

										if (imageColor.getAlpha() > highestAlpha)
										{
											highestAlpha = imageColor.getAlpha();
										}
									}
								}
								alphaScale = 255f / highestAlpha;
							}
						}
					}

					float[] filterHSB = filterColor.toArray();
					float filterAlphaScale = filterColor.getAlpha() / 255f;

					if ((fillWithColor && !fillColor.equals(Color.transparentBlack)) || !filterColor.equals(MapSettings.defaultIconFilterColor) || maximizeOpacity)
					{
						Image result = Image.create(imageAndMasks.image.getWidth(), imageAndMasks.image.getHeight(), ImageType.ARGB);

						Image colorMask = imageAndMasks.getOrCreateColorMask();
						try (PixelReader imagePixels = imageAndMasks.image.createPixelReader();
								PixelReader colorMaskPixels = colorMask.createPixelReader();
								PixelWriter resultPixels = result.createPixelWriter())
						{
							for (int y = 0; y < result.getHeight(); y++)
							{
								for (int x = 0; x < result.getWidth(); x++)
								{

									Color originalColor = imagePixels.getPixelColor(x, y);

									int alpha;
									if (maximizeOpacity)
									{
										// I'm clamping the value to 255 in case of truncation errors, although I doubt that's possible.
										alpha = Math.min(255, (int) (originalColor.getAlpha() * alphaScale));
									}
									else
									{
										alpha = originalColor.getAlpha();
									}

									double fillColorAlpha = (fillWithColor ? fillColor.getAlpha() : 0) / 255.0;
									double fillColorScale;
									if (fillColorAlpha == 1.0)
									{
										// Save some time since this is a simple and common case.
										fillColorScale = 1.0;
									}
									else
									{
										// Use a curve that is 0 when fillColorAlpha is 0, 1 when fillColorAlpha is 1, and is mostly equal
										// to 1
										// but dies off
										// quickly as fillColorAlpha reaches 0. That way when the fill color is transparent, it doesn't mix
										// with
										// icon pixels
										// that are partially transparent.
										fillColorScale = 1.0 - Math.pow(1.0 - fillColorAlpha, 50);
									}

									Color filteredImageColor;
									int filteredAlpha;
									// Use filter color
									float[] hsb = originalColor.getHSB();
									filteredImageColor = Color.createFromHSB(hsb[0] + filterHSB[0] - (float) Math.floor(hsb[0] + filterHSB[0]), Helper.clamp(hsb[1] + filterHSB[1], 0f, 1f),
											Helper.clamp(hsb[2] + filterHSB[2], 0f, 1f));
									filteredAlpha = Math.min(255, (int) (alpha * filterAlphaScale));

									int r = Helper.linearComboBase255(filteredAlpha, filteredImageColor.getRed(), (int) (fillColor.getRed() * fillColorScale));
									int g = Helper.linearComboBase255(filteredAlpha, filteredImageColor.getGreen(), (int) (fillColor.getGreen() * fillColorScale));
									int b = Helper.linearComboBase255(filteredAlpha, filteredImageColor.getBlue(), (int) (fillColor.getBlue() * fillColorScale));
									int a = Math.max(filteredAlpha, Math.min(fillColor.getAlpha(), colorMaskPixels.getGrayLevel(x, y)));

									resultPixels.setRGB(x, y, r, g, b, a);
								}
							}
						}
						return result;
					}
					else
					{
						return imageAndMasks.image;
					}
				});
	}

	/**
	 * Creates a new image whose alpha is between 0 and the given alpha, by scaling all the alpha values in the image to be between that
	 * range, essentially making the given alpha be the transparency of the image, plus the transparency it already had.
	 * 
	 * The image will be cached for future calls using alphaCache.
	 * 
	 * @param image
	 *            Original image
	 * @param alpha
	 *            Alpha to apply
	 * @return image with added transparency
	 */
	public Image getImageWithAppliedAlpha(Image image, Integer alpha)
	{
		if (alpha == null || alpha == 255)
		{
			return image;
		}

		return alphaCache.getOrCreate(image, () -> new ConcurrentHashMapF<>()).getOrCreate(alpha, () ->
		{
			return ImageHelper.applyAlpha(image, alpha);
		});
	}

	public Image getImageFromFile(Path path)
	{
		return fileCache.getOrCreateWithLock(path.toString().intern(), () ->
		{
			return Assets.readImage(path.toString());
		});
	}

	public boolean cacheContainsImageFile(Path path)
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
		List<String> namesSorted = new ArrayList<>(map.keySet());
		Collections.sort(namesSorted);
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
		return iconsWithSizesCache.getOrCreate(iconType, () -> new ConcurrentHashMapF<>()).getOrCreate((groupName == null ? "" : groupName).intern(),
				() -> loadIconsWithSizesAndAlphas(iconType, groupName));
	}

	private record FilenameParams(String originalFileName, String fileNameBase, Double width, Integer alpha)
	{
	}

	private Map<String, ImageAndMasks> loadIconsWithSizesAndAlphas(IconType iconType, String groupName)
	{
		Map<String, ImageAndMasks> imagesAndMasks = new HashMap<>();
		if (groupName == null || groupName.isEmpty())
		{
			return imagesAndMasks;
		}

		List<String> fileNames = getIconGroupFileNames(iconType, groupName);
		if (fileNames.isEmpty())
		{
			return imagesAndMasks;
		}

		// Maps from image base without encoded parameters to (image base name without width, width, file name, alpha).
		Map<String, FilenameParams> baseNamesToParams = new TreeMap<>();
		for (String fileName : fileNames)
		{
			Tuple2<String, Double> nameAndWidth = parseBaseNameAndSize(fileName, WhichDimension.Width);
			Tuple2<String, Double> nameAndHeight = parseBaseNameAndSize(nameAndWidth.getFirst(), WhichDimension.Height);
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

			Tuple2<String, Integer> nameAndAlpha = parseAlpha(nameAndWidth.getFirst());
			String fileNameBaseWithoutEncodedParameters = nameAndAlpha.getFirst();
			if (baseNamesToParams.containsKey(fileNameBaseWithoutEncodedParameters))
			{
				throw new RuntimeException("There are multiple images for " + iconType + " named '" + fileNameBaseWithoutEncodedParameters
						+ "' whose file names only differ by their encoded width or height or extension." + " Rename one of them.");
			}

			Integer alpha = null;
			if (nameAndAlpha.getSecond() != null)
			{
				alpha = nameAndAlpha.getSecond();
			}

			baseNamesToParams.put(fileNameBaseWithoutEncodedParameters, new FilenameParams(fileName, fileNameBaseWithoutEncodedParameters, nameAndWidth.getSecond(), alpha));

		}

		Tuple2<Image, Double> tuple = findIconToUseForReferenceWhenSizingOtherIcons(iconType, groupName, baseNamesToParams);
		int defaultAlpha = findDefaultAlphaForGroup(baseNamesToParams);
		Image widest = tuple.getFirst();
		double widthOfWidest = tuple.getSecond();

		for (FilenameParams filenameParams : baseNamesToParams.values())
		{
			Image icon = loadIconFromDiskOrCache(iconType, groupName, filenameParams.originalFileName);

			if (icon == null)
			{
				// I think this happened once, but I haven't figured out how.
				assert false;
				continue;
			}

			int alpha;
			if (filenameParams.alpha != null)
			{
				alpha = filenameParams.alpha;
			}
			else
			{
				alpha = defaultAlpha;
			}
			icon = getImageWithAppliedAlpha(icon, alpha);

			double width;
			// If any don't have an encoded width, then calculate the width relative to the largest image that does have an encoded width.
			if (filenameParams.width == null)
			{
				width = icon.getWidth() * (widthOfWidest / widest.getWidth());
			}
			else
			{
				width = filenameParams.width;
			}

			imagesAndMasks.put(filenameParams.fileNameBase, new ImageAndMasks(icon, iconType, width, artPack, groupName, filenameParams.fileNameBase));
		}

		return imagesAndMasks;
	}

	private Tuple2<Image, Double> findIconToUseForReferenceWhenSizingOtherIcons(IconType iconType, String groupName, Map<String, FilenameParams> baseNamesToParams)
	{
		FilenameParams widest = Helper.maxElement(baseNamesToParams, (params1, params2) ->
		{
			Double w1 = params1.width;
			Double w2 = params2.width;
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

		if (widest.width != null)
		{
			Image widestIcon = loadIconFromDiskOrCache(iconType, groupName, widest.originalFileName);
			return new Tuple2<>(widestIcon, widest.width);
		}
		else
		{
			Map<String, Image> iconsByName = new TreeMap<>();
			for (FilenameParams params : baseNamesToParams.values())
			{
				Image icon = loadIconFromDiskOrCache(iconType, groupName, params.originalFileName);
				iconsByName.put(params.fileNameBase, icon);
			}
			String nameOfWidestOrTallest = Helper.argmax(iconsByName, (icon1, icon2) ->
			{
				IntDimension d1 = icon1.size();
				IntDimension d2 = icon2.size();
				return Integer.compare(d1.width, d2.width);
			});

			Image icon = iconsByName.get(nameOfWidestOrTallest);
			double widthOrHeight = getDefaultWidthOrHeight(iconType);
			double width = isDefaultSizeByWidth(iconType) ? widthOrHeight : IconDrawer.getDimensionsWhenScaledByHeight(icon.size(), widthOrHeight).width;
			return new Tuple2<>(icon, width);
		}
	}

	private Integer findDefaultAlphaForGroup(Map<String, FilenameParams> alphas)
	{
		Integer max = Helper.maxElement(alphas, (p1, p2) -> compareNullsFirst(p1.alpha, p2.alpha)).alpha;
		if (max == null)
		{
			return 255;
		}
		return max;
	}

	public static int compareNullsFirst(Integer o1, Integer o2)
	{
		if (o1 == null)
		{
			if (o2 == null)
			{
				return 0; // Both are null, considered equal
			}
			else
			{
				return -1; // o1 is null, o2 is not, o1 comes first (lowest)
			}
		}
		else if (o2 == null)
		{
			return 1; // o1 is not null, o2 is null, o2 comes first (lowest)
		}
		else
		{
			return Integer.compare(o1, o2); // Both are non-null, use natural Integer comparison
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

	/**
	 * Given a file name (without the path), this parses out the encoded alpha value, if present.
	 * 
	 * @return A 2-tuple in which the first parameter is the filename without the encoded alpha, and the second is the encoded alpha value.
	 */
	public static Tuple2<String, Integer> parseAlpha(String fileName)
	{
		if (fileName == null || fileName.isEmpty())
		{
			return null;
		}

		// Remove the file extension
		int dotIndex = fileName.lastIndexOf('.');
		String baseName = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);

		// Define the regex pattern for width
		Pattern pattern = Pattern.compile("(.*?)(?:\\s|_)(?:alpha=|a)(\\d+)");
		Matcher matcher = pattern.matcher(baseName);

		if (matcher.find())
		{
			String nameWithoutAlpha = matcher.group(1).trim();
			int alpha = Integer.parseInt(matcher.group(2));
			if (alpha > 255)
			{
				throw new RuntimeException("The image '" + fileName + "' has an encoded alpha greater than 255.");
			}

			if (alpha < 0)
			{
				// Not possible to hit with my regex.
				throw new RuntimeException("The image '" + fileName + "' has an encoded alpha less than 0.");
			}

			return new Tuple2<>(nameWithoutAlpha, alpha);
		}
		return new Tuple2<>(baseName, null);
	}

	public enum WhichDimension
	{
		Width, Height
	}

	public Set<String> getIconGroupFileNamesWithoutWidthOrExtensionAsSet(IconType iconType, String groupName)
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

	public List<String> getIconGroupFileNamesWithoutWidthOrExtensionAsList(IconType iconType, String groupName)
	{
		List<String> fileNames = getIconGroupFileNames(iconType, groupName);
		List<String> result = new ArrayList<String>();
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
		return iconGroupFilesNamesCache.getOrCreate(iconType, () -> new ConcurrentHashMapF<>()).getOrCreate(groupNameToUse, () -> loadIconGroupFileNames(iconType, groupNameToUse));
	}

	public boolean hasNamedIcon(IconType iconType, String groupName, String iconName)
	{
		if (!getIconGroupNames(iconType).contains(groupName))
		{
			return false;
		}

		return getIconGroupFileNamesWithoutWidthOrExtensionAsSet(iconType, groupName).contains(iconName);
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

		System.gc();
	}

	public static void clearColoredAndScaledImageCaches()
	{
		for (ImageCache cache : instances.values())
		{
			cache.coloredCache.clear();
			cache.scaledCache.clear();
		}
	}
}
