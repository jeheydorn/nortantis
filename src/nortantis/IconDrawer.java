package nortantis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import nortantis.editor.CenterEdit;
import nortantis.editor.CenterIcon;
import nortantis.editor.CenterIconType;
import nortantis.editor.CenterTrees;
import nortantis.editor.FreeIcon;
import nortantis.geom.Dimension;
import nortantis.geom.IntDimension;
import nortantis.geom.IntRectangle;
import nortantis.geom.Point;
import nortantis.geom.Rectangle;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.platform.Color;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.swing.MapEdits;
import nortantis.util.AssetsPath;
import nortantis.util.Function;
import nortantis.util.HashMapF;
import nortantis.util.ImageHelper;
import nortantis.util.ListMap;
import nortantis.util.Logger;
import nortantis.util.ProbabilityHelper;
import nortantis.util.Range;
import nortantis.util.ThreadHelper;
import nortantis.util.Tuple2;

public class IconDrawer
{
	public static final double mountainElevationThreshold = 0.58;
	public static final double hillElevationThreshold = 0.53;
	final double meanPolygonWidth;
	final double cityScale;
	// Max gap (in polygons) between mountains for considering them a single group. Warning:
	// there tend to be long polygons along edges, so if this value is much more than 2,
	// mountains near the ocean may be connected despite long distances between them..
	private final int maxGapSizeInMountainClusters = 2;
	private final int maxGapBetweenBiomeGroups = 2;
	// For hills and mountains, if a polygon is this number times meanPolygonWidth wide, no icon will be added to it when creating a new
	// map.
	final double maxMeansToDrawGeneratedMountainOrHill = 5.0;
	final double maxSizeToDrawGeneratedMountainOrHill;
	private final double mountainScale;
	private final double hillScale;
	private final double hillSizeComparedToMountains = 0.5;
	private final double duneScale;
	private final double treeHeightScale;
	private final double treeDensityScale;
	private List<IconDrawTask> iconsToDraw;
	FreeIconCollection freeIcons;
	WorldGraph graph;
	Random rand;
	private double averageCenterWidthBetweenNeighbors;
	private String cityIconTypeForNewMaps;
	private String imagesPath;
	private double resolutionScale;

	public IconDrawer(WorldGraph graph, Random rand, MapSettings settings)
	{
		this.graph = graph;
		this.rand = rand;
		this.cityIconTypeForNewMaps = settings.cityIconTypeName;
		if (settings.customImagesPath != null && !settings.customImagesPath.isEmpty())
		{
			this.imagesPath = settings.customImagesPath;
		}
		else
		{
			this.imagesPath = AssetsPath.getInstallPath();
		}
		this.resolutionScale = settings.resolution;

		meanPolygonWidth = findMeanCenterWidth(graph);
		duneScale = settings.duneScale;

		mountainScale = settings.mountainScale;
		hillScale = settings.hillScale;
		cityScale = meanPolygonWidth * (1.0 / 11.0) * settings.cityScale; // TODO Only include scale from settings. Make a function to
																			// determine base width like I did for mountains
		treeHeightScale = settings.treeHeightScale;
		treeDensityScale = calcTreeDensityScale();
		maxSizeToDrawGeneratedMountainOrHill = meanPolygonWidth * maxMeansToDrawGeneratedMountainOrHill;

		averageCenterWidthBetweenNeighbors = findMeanCenterWidthBetweenNeighbors();
	}

	public static double findMeanCenterWidth(WorldGraph graph)
	{
		double widthSum = 0;
		int count = 0;
		for (Center center : graph.centers)
		{
			double width = center.findWidth();

			if (width > 0)
			{
				count++;
				widthSum += width;
			}
		}

		return widthSum / count;
	}

	private double findMeanCenterWidthBetweenNeighbors()
	{
		double sum = 0;
		for (Center c : graph.centers)
		{
			sum += findCenterWidthBetweenNeighbors(c);
		}
		return sum / graph.centers.size();
	}

	public void markMountains()
	{
		for (Center c : graph.centers)
		{
			if (c.elevation > mountainElevationThreshold && !c.isBorder && c.findWidth() < maxSizeToDrawGeneratedMountainOrHill)
			{
				c.isMountain = true;
			}
		}
	}

	public void markHills()
	{
		for (Center c : graph.centers)
		{
			if (c.elevation < mountainElevationThreshold && c.elevation > hillElevationThreshold
					&& c.findWidth() < maxSizeToDrawGeneratedMountainOrHill)

			{
				c.isHill = true;
			}
		}
	}

	public void markCities(double cityProbability)
	{
		for (Center c : graph.centers)
		{
			if (!c.isMountain && !c.isHill && !c.isWater && !c.isBorder && !c.neighbors.stream().anyMatch(n -> n.isBorder))
			{
				// I'm generating these numbers now instead of waiting to see if they are needed in the if statements below because
				// there is a problem in the graph such that maps generated at different resolutions can have slight differences in their
				// rivers, which appears to be caused by floating point precision issues while calculating elevation of corners.
				// Thus, the slightest change in a river on one corner could cause a center to change whether it's a river, which
				// would modify the way the random number generator is called, which would then change everything else used by that
				// random number generator after it. But this fix only reduces the issue, since other things will also change
				// when rivers move.
				double cityByRiverProbability = rand.nextDouble();
				double cityByCoastProbability = rand.nextDouble();
				double randomCityProbability = rand.nextDouble();
				double byMountainCityProbability = rand.nextDouble();
				if (c.isRiver() && cityByRiverProbability <= cityProbability * 2)
				{
					c.isCity = true;
				}
				else if (c.isCoast && cityByCoastProbability <= cityProbability * 2)
				{
					c.isCity = true;
				}
				else if (c.neighbors.stream().anyMatch(n -> n.isMountain) && byMountainCityProbability <= cityProbability * 1.6)
				{
					c.isCity = true;
				}
				else if (randomCityProbability <= cityProbability)
				{
					c.isCity = true;
				}
			}
		}
	}

	public List<Set<Center>> findMountainGroups()
	{
		List<Set<Center>> mountainGroups = findCenterGroups(graph, maxGapSizeInMountainClusters, new Function<Center, Boolean>()
		{
			public Boolean apply(Center center)
			{
				return center.isMountain;
			}
		});

		return mountainGroups;

	}

	/**
	 * Finds and marks mountain ranges, and groups smaller than ranges, and surrounding hills.
	 */
	public List<Set<Center>> findMountainAndHillGroups()
	{
		List<Set<Center>> mountainAndHillGroups = findCenterGroups(graph, maxGapSizeInMountainClusters, new Function<Center, Boolean>()
		{
			public Boolean apply(Center center)
			{
				return center.isMountain || center.isHill;
			}
		});

		// Assign mountain group ids to each center that is in a mountain group.
		int curId = 0;
		for (Set<Center> group : mountainAndHillGroups)
		{
			for (Center c : group)
			{
				c.mountainRangeId = curId;
			}
			curId++;
		}

		return mountainAndHillGroups;

	}

	// TODO Call this once I've rewritten the code for adding new icons.
	private void convertToFreeIconsIfNeeded(Collection<Center> centersToConvert, MapEdits edits, WarningLogger warningLogger)
	{
		ListMap<String, ImageAndMasks> mountainImagesById = ImageCache.getInstance(imagesPath)
				.getAllIconGroupsAndMasksForType(IconType.mountains);
		ListMap<String, ImageAndMasks> hillImagesById = ImageCache.getInstance(imagesPath).getAllIconGroupsAndMasksForType(IconType.hills);
		ListMap<String, ImageAndMasks> duneImages = ImageCache.getInstance(imagesPath).getAllIconGroupsAndMasksForType(IconType.sand);

		for (Center center : centersToConvert)
		{
			CenterEdit cEdit = edits.centerEdits.get(center.index);
			if (cEdit.icon != null)
			{
				if (cEdit.icon.iconType == CenterIconType.Mountain)
				{
					convertWidthBasedShuffledAnchoredIcon(center, cEdit, mountainImagesById, warningLogger,
							() -> findNewMountainBaseWidth(center), true);
				}
				else if (cEdit.icon.iconType == CenterIconType.Hill)
				{
					convertWidthBasedShuffledAnchoredIcon(center, cEdit, hillImagesById, warningLogger, () -> findNewHillBaseWidth(center),
							false);
				}
				else if (cEdit.icon.iconType == CenterIconType.Dune)
				{
					convertWidthBasedShuffledAnchoredIcon(center, cEdit, duneImages, warningLogger,
							() -> getBaseWidthOrHeight(IconType.sand, 0), false);
				}
				else if (cEdit.icon.iconType == CenterIconType.City)
				{
					Tuple2<String, String> groupAndName = adjustCityIconGroupAndNameIfNeeded(cEdit.icon.iconGroupId, cEdit.icon.iconName,
							warningLogger);

					if (groupAndName != null)
					{
						String groupId = groupAndName.getFirst();
						String name = groupAndName.getSecond();

						FreeIcon icon = new FreeIcon(resolutionScale, center.loc, 1.0, centerIconTypeToIconType(cEdit.icon.iconType),
								groupId, name);
						
						// TODO lock
						icon.centerIndex = cEdit.index;
						freeIcons.addOrReplace(icon);
						cEdit.icon = null;
					}
					else
					{
						// TODO lock
						cEdit.icon = null;
					}
				}

			}
		}

		convertTreesToFreeIcons(centersToConvert, edits, warningLogger);
	}

	public static Dimension getDimensionsWhenScaledByWidth(IntDimension originalDimensions, double scaledWidth)
	{
		double aspectRatio = ((double) originalDimensions.height) / originalDimensions.width;
		double ySize = scaledWidth * aspectRatio;
		return new Dimension(scaledWidth, ySize);
	}

	public static Dimension getDimensionsWhenScaledByHeight(IntDimension originalDimensions, double scaledHeight)
	{
		double aspectRatioInverse = ((double) originalDimensions.width) / originalDimensions.height;
		int xSize = (int) (aspectRatioInverse * scaledHeight);
		return new Dimension(xSize, scaledHeight);
	}


	private void convertWidthBasedShuffledAnchoredIcon(Center center, CenterEdit cEdit, ListMap<String, ImageAndMasks> iconsByGroup,
			WarningLogger warningLogger, Supplier<Double> getDrawWidth, boolean placeNearBottom)
	{
		if (cEdit.icon == null)
		{
			return;
		}

		final String groupId = changeGroupIdIfNeeded(cEdit.icon.iconGroupId, cEdit.icon.iconType.toString(), iconsByGroup, warningLogger);
		if (groupId == null || !iconsByGroup.containsKey(groupId) || iconsByGroup.get(groupId).size() == 0)
		{
			cEdit.icon = null;
			return;
		}

		double scaledWidth = getDrawWidth.get();
		ImageAndMasks imageAndMasks = iconsByGroup.get(groupId).get(cEdit.icon.iconIndex % iconsByGroup.get(groupId).size());
		Point loc;
		if (placeNearBottom)
		{
			loc = getImageCenterToDrawImageNearBottomOfCenter(imageAndMasks.image, scaledWidth, center);
		}
		else
		{
			loc = center.loc;
		}
		IconType type = centerIconTypeToIconType(cEdit.icon.iconType);
		double scale = scaledWidth / getBaseWidthOrHeight(type, 0);
		FreeIcon icon = new FreeIcon(resolutionScale, loc, scale, type, groupId, cEdit.icon.iconIndex);
		icon.centerIndex = cEdit.index;
		freeIcons.addOrReplace(icon);
		cEdit.icon = null;
	}

	/**
	 * This is used to add icon to draw tasks from map edits rather than using the generator to add them. The actual drawing of the icons is
	 * done later.
	 */
	public void addOrUpdateIconsFromEdits(MapEdits edits, Collection<Center> centersToUpdateIconsFor, double treeHeightScale,
			WarningLogger warningLogger)
	{
		for (FreeIcon icon : edits.freeIcons)
		{
			if (icon.type == IconType.mountains)
			{
				updateGroupIdAndAddShuffledFreeIcon(icon, mountainScale, warningLogger);
			}
			else if (icon.type == IconType.hills)
			{
				updateGroupIdAndAddShuffledFreeIcon(icon, hillScale, warningLogger);
			}
			else if (icon.type == IconType.sand)
			{
				updateGroupIdAndAddShuffledFreeIcon(icon, duneScale, warningLogger);
			}
			else if (icon.type == IconType.cities)
			{
				Tuple2<String, String> groupAndName = adjustCityIconGroupAndNameIfNeeded(icon.groupId, icon.iconName, warningLogger);
				if (groupAndName != null)
				{
					// TODO Lock the free icon before changing it.
					icon.groupId = groupAndName.getFirst();
					icon.iconName = groupAndName.getSecond();

					Map<String, Tuple2<ImageAndMasks, Integer>> cityImages = ImageCache.getInstance(imagesPath)
							.getIconsWithWidths(IconType.cities, icon.groupId);
					double widthFromFileName = cityImages.get(icon.iconName).getSecond();

					iconsToDraw.add(icon.toIconDrawTask(imagesPath, resolutionScale, cityScale,
							getBaseWidthOrHeight(IconType.cities, widthFromFileName)));
				}
			}
			else if (icon.type == IconType.trees)
			{
				updateGroupIdAndAddShuffledFreeIcon(icon, treeHeightScale, warningLogger);
			}
		}
	}

	private Tuple2<String, String> adjustCityIconGroupAndNameIfNeeded(String groupId, String name, WarningLogger warningLogger)
	{
		Map<String, Tuple2<ImageAndMasks, Integer>> cityImages = ImageCache.getInstance(imagesPath).getIconsWithWidths(IconType.cities,
				groupId);
		if (cityImages == null || cityImages.isEmpty())
		{
			String newGroupId = chooseNewGroupId(ImageCache.getInstance(imagesPath).getIconGroupNames(IconType.cities), groupId);
			if (newGroupId == null)
			{
				warningLogger.addWarningMessage(
						"Unable to find the city" + " image group '" + groupId + "'. There are no city icons, so none will be drawn.");
				// TODO Remove the icon from the edits
				return null;
			}
			cityImages = ImageCache.getInstance(imagesPath).getIconsWithWidths(IconType.cities, newGroupId);
			if (cityImages == null || cityImages.isEmpty())
			{
				// This shouldn't happens since the new group id shouldn't have been an option if it were empty or null.
				return null;
			}
			warningLogger.addWarningMessage(
					"Unable to find the city" + " image group '" + groupId + "'. The group '" + newGroupId + "' will be used instead.");
			groupId = newGroupId;
		}

		String oldName = name;
		if (!cityImages.containsKey(name) && cityImages.size() > 0)
		{
			// Either the city image is missing, or the icon set name changed. Choose a new image in a deterministic but
			// random way.
			name = chooseNewCityIconName(cityImages.keySet(), name);
			if (name != null)
			{
				warningLogger.addWarningMessage(
						"Unable to find the city icon '" + oldName.trim() + "'. The icon '" + name.trim() + "' will be used instead.");
			}
		}

		return new Tuple2<String, String>(groupId, name);
	}

	private void updateGroupIdAndAddShuffledFreeIcon(FreeIcon icon, double typeLevelScale, WarningLogger warningLogger)
	{
		ListMap<String, ImageAndMasks> iconsByGroup = ImageCache.getInstance(imagesPath).getAllIconGroupsAndMasksForType(icon.type);
		String newGroupId = changeGroupIdIfNeeded(icon.groupId, icon.type.toString(), iconsByGroup, warningLogger);
		if (!icon.groupId.equals(newGroupId) && newGroupId != null)
		{
			// TODO Lock
			icon.groupId = changeGroupIdIfNeeded(icon.groupId, icon.type.toString(), iconsByGroup, warningLogger);
		}

		if (iconsByGroup.get(icon.groupId).size() > 0)
		{
			double baseWidthOrHeight = getBaseWidthOrHeight(icon.type, 0);
			iconsToDraw.add(icon.toIconDrawTask(imagesPath, resolutionScale, typeLevelScale, baseWidthOrHeight));
		}
	}

	private double getBaseWidthOrHeight(IconType type, double cityWidth)
	{
		if (type == IconType.mountains)
		{
			return averageCenterWidthBetweenNeighbors;
		}
		else if (type == IconType.hills)
		{
			return averageCenterWidthBetweenNeighbors * hillSizeComparedToMountains;
		}
		else if (type == IconType.sand)
		{
			return meanPolygonWidth * 1.2;
		}
		else if (type == IconType.cities)
		{
			return cityWidth * resolutionScale;
		}
		else if (type == IconType.trees)
		{
			return averageCenterWidthBetweenNeighbors;
		}
		throw new IllegalArgumentException("Unrecognized icon type: " + type);
	}

	private String changeGroupIdIfNeeded(final String groupId, String iconTypeNameForWarnings, ListMap<String, ImageAndMasks> iconsByGroup,
			WarningLogger warningLogger)
	{
		if (!iconsByGroup.containsKey(groupId))
		{
			// Someone removed the icon group. Choose a new group.
			String newGroupId = chooseNewGroupId(iconsByGroup.keySet(), groupId);
			if (newGroupId == null)
			{
				warningLogger.addWarningMessage("Unable to find the " + iconTypeNameForWarnings.toLowerCase() + " image group '" + groupId
						+ "'. There are no " + iconTypeNameForWarnings.toString().toLowerCase() + " icons, so none will be drawn.");
				// TODO Lock down the edits and remove this free icon.
			}
			warningLogger.addWarningMessage("Unable to find the " + iconTypeNameForWarnings.toLowerCase() + " image group '" + groupId
					+ "'. The group '" + newGroupId + "' will be used instead.");
			return newGroupId;
		}

		return groupId;
	}

	private IconType centerIconTypeToIconType(CenterIconType type)
	{
		if (type == CenterIconType.City)
		{
			return IconType.cities;
		}
		if (type == CenterIconType.Dune)
		{
			return IconType.sand;
		}
		if (type == CenterIconType.Hill)
		{
			return IconType.hills;
		}
		if (type == CenterIconType.Mountain)
		{
			return IconType.mountains;
		}
		else
		{
			throw new IllegalArgumentException("Unable to convert CenterIconType '" + type + "' to an IconType.");
		}
	}

	public static String chooseNewCityIconName(Set<String> cityNamesToChooseFrom, String oldIconName)
	{
		List<CityType> oldTypes = NameCreator.findCityTypeFromCityFileName(oldIconName);
		List<String> compatibleCities = cityNamesToChooseFrom.stream()
				.filter(name -> NameCreator.findCityTypeFromCityFileName(name).stream().anyMatch(type -> oldTypes.contains(type)))
				.collect(Collectors.toList());
		if (compatibleCities.isEmpty())
		{
			int index = Math.abs(oldIconName.hashCode() % cityNamesToChooseFrom.size());
			return new ArrayList<>(cityNamesToChooseFrom).get(index);
		}
		int index = Math.abs(oldIconName.hashCode() % compatibleCities.size());
		return compatibleCities.get(index);
	}

	public boolean doesCityFitOnLand(Center center, CenterIcon cityIcon)
	{
		if (center == null || cityIcon == null)
		{
			return true;
		}

		Map<String, Tuple2<ImageAndMasks, Integer>> cityImages = ImageCache.getInstance(imagesPath).getIconsWithWidths(IconType.cities,
				cityIcon.iconGroupId);
		Tuple2<ImageAndMasks, Integer> tuple = cityImages.get(cityIcon.iconName);
		if (tuple == null)
		{
			// Not a city icon
			return false;
		}

		ImageAndMasks imageAndMasks = cityImages.get(cityIcon.iconName).getFirst();

		// Create an icon draw task just for checking if the city fits on land. It won't actually be drawn.
		IconDrawTask task = new IconDrawTask(imageAndMasks, IconType.cities, center.loc,
				(int) (cityImages.get(cityIcon.iconName).getSecond() * cityScale), cityIcon.iconName);
		return !isContentBottomTouchingWater(task);
	}

	private String chooseNewGroupId(Set<String> groupIds, String oldGroupId)
	{
		if (groupIds.isEmpty())
		{
			return null;
		}
		int index = Math.abs(oldGroupId.hashCode() % groupIds.size());
		return groupIds.toArray(new String[groupIds.size()])[index];
	}

	/**
	 * Finds groups of centers that accepted according to a given function. A group is a set of centers for which there exists a path from
	 * any member of the set to any other such that you never have to skip over more than maxGapSize centers not accepted at once to get to
	 * that other center. If distanceThreshold > 1, the result will include those centers which connect centeres that are accepted.
	 */
	private static List<Set<Center>> findCenterGroups(WorldGraph graph, int maxGapSize, Function<Center, Boolean> accept)
	{
		List<Set<Center>> groups = new ArrayList<>();
		// Contains all explored centers in this graph. This prevents me from making a new group
		// for every center.
		Set<Center> explored = new HashSet<>();
		for (Center center : graph.centers)
		{
			if (accept.apply(center) && !explored.contains(center))
			{
				// Do a breadth-first-search from that center, creating a new group.
				// "frontier" maps centers to their distance from a center of the desired biome.
				// 0 means it is of the desired biome.
				Map<Center, Integer> frontier = new HashMap<>();
				frontier.put(center, 0);
				Set<Center> group = new HashSet<>();
				group.add(center);
				while (!frontier.isEmpty())
				{
					Map<Center, Integer> nextFrontier = new HashMap<>();
					for (Map.Entry<Center, Integer> entry : frontier.entrySet())
					{
						for (Center n : entry.getKey().neighbors)
						{
							if (!explored.contains(n))
							{
								if (accept.apply(n))
								{
									explored.add(n);
									group.add(n);
									nextFrontier.put(n, 0);
								}
								else if (entry.getValue() < maxGapSize)
								{
									int newDistance = entry.getValue() + 1;
									nextFrontier.put(n, newDistance);
								}
							}
						}
					}
					frontier = nextFrontier;
				}
				groups.add(group);

			}
		}
		return groups;
	}

	/**
	 * 
	 * = * @param mask A gray scale image which is white where the background should be drawn, and black where the map should be drawn
	 * instead of the background. This is necessary so that when I draw an icon that is transparent (such as a hand drawn mountain), I
	 * cannot see other mountains through it.
	 */
	private void drawIconWithBackgroundAndMask(Image mapOrSnippet, ImageAndMasks imageAndMasks, Image backgroundOrSnippet,
			Image landTexture, int xCenter, int yCenter)
	{
		Image icon = imageAndMasks.image;
		Image contentMask = imageAndMasks.getOrCreateContentMask();

		if (mapOrSnippet.getWidth() != backgroundOrSnippet.getWidth())
			throw new IllegalArgumentException();
		if (mapOrSnippet.getHeight() != backgroundOrSnippet.getHeight())
			throw new IllegalArgumentException();
		if (contentMask.getWidth() != icon.getWidth())
			throw new IllegalArgumentException("The given content mask's width does not match the icon's width.");
		if (contentMask.getHeight() != icon.getHeight())
			throw new IllegalArgumentException("The given content mask's height does not match the icon's height.");
		Image shadingMask = null;
		shadingMask = imageAndMasks.getOrCreateShadingMask();
		if (shadingMask.getWidth() != icon.getWidth())
		{
			throw new IllegalArgumentException("The given shading mask's width does not match the icon's width.");
		}
		if (shadingMask.getHeight() != icon.getHeight())
		{
			throw new IllegalArgumentException("The given shading mask's height does not match the icon's height.");
		}

		int xLeft = xCenter - icon.getWidth() / 2;
		int yBottom = yCenter - icon.getHeight() / 2;

		for (int x : new Range(icon.getWidth()))
		{
			for (int y : new Range(icon.getHeight()))
			{
				Color iconColor = Color.create(icon.getRGB(x, y), true);
				double alpha = iconColor.getAlpha() / 255.0;
				// grey level of mask at the corresponding pixel in mask.
				float contentMaskLevel = contentMask.getNormalizedPixelLevel(x, y);
				float shadingMaskLevel = shadingMask.getNormalizedPixelLevel(x, y);
				Color bgColor;
				Color mapColor;
				Color landTextureColor;
				// Find the location on the background and map where this pixel will be drawn.
				int xLoc = xLeft + x;
				int yLoc = yBottom + y;
				try
				{
					bgColor = Color.create(backgroundOrSnippet.getRGB(xLoc, yLoc));
					mapColor = Color.create(mapOrSnippet.getRGB(xLoc, yLoc));
					landTextureColor = Color.create(landTexture.getRGB(xLoc, yLoc));
				}
				catch (IndexOutOfBoundsException e)
				{
					// Skip this pixel.
					continue;
				}

				// Use the shading mask to blend the coastline shading with the land background texture for pixels
				// with transparency in the icon and non-zero values in the content mask. This way coastline shading
				// doesn't draw through icons, since that would look weird when the icon extends over the coastline.
				// It also makes the transparent pixels in the content of the icon draw the land background texture
				// when the shading mask is white, so that icons extending into the ocean draw the land texture behind
				// them rather than the ocean texture.
				int red = (int) (alpha * (iconColor.getRed()) + (1 - alpha)
						* (contentMaskLevel * ((1 - shadingMaskLevel) * landTextureColor.getRed() + (shadingMaskLevel * bgColor.getRed()))
								+ (1 - contentMaskLevel) * mapColor.getRed()));
				int green = (int) (alpha * (iconColor.getGreen()) + (1 - alpha) * (contentMaskLevel
						* ((1 - shadingMaskLevel) * landTextureColor.getGreen() + (shadingMaskLevel * bgColor.getGreen()))
						+ (1 - contentMaskLevel) * mapColor.getGreen()));
				int blue = (int) (alpha * (iconColor.getBlue()) + (1 - alpha)
						* (contentMaskLevel * ((1 - shadingMaskLevel) * landTextureColor.getBlue() + (shadingMaskLevel * bgColor.getBlue()))
								+ (1 - contentMaskLevel) * mapColor.getBlue()));
				mapOrSnippet.setRGB(xLoc, yLoc, Color.create(red, green, blue).getRGB());
			}
		}
	}

	/**
	 * Draws all icons in iconsToDraw that touch drawBounds.
	 * 
	 * I draw all the icons at once this way so that I can sort the icons by the y-coordinate of the base of each icon. This way icons lower
	 * on the map are drawn in front of those that are higher.
	 * 
	 * @return The icons that drew.
	 */
	public List<IconDrawTask> drawAllIcons(Image mapOrSnippet, Image background, Image landTexture, Rectangle drawBounds)
	{
		List<IconDrawTask> tasks = new ArrayList<IconDrawTask>();
		for (Map.Entry<Center, List<IconDrawTask>> entry : anchoredIconsToDraw.entrySet())
		{
			if (!entry.getKey().isWater)
			{
				for (final IconDrawTask task : entry.getValue())
				{
					if (drawBounds == null || task.overlaps(drawBounds))
					{
						// Updates to the line below will will likely need to also update doesCityFitOnLand.
						if (!isContentBottomTouchingWater(task))
						{
							tasks.add(task);
						}
						else
						{
							task.failedToDraw = true;
						}
					}
				}
			}
		}
		Collections.sort(tasks);

		// Force mask creation now if it hasn't already happened so that so that multiple threads don't try to create the same masks at the
		// same time and end up repeating work.
		for (final IconDrawTask task : tasks)
		{
			task.unScaledImageAndMasks.getOrCreateContentMask();
			task.unScaledImageAndMasks.getOrCreateShadingMask();
		}

		// Scale the icons in parallel.
		{
			List<Runnable> jobs = new ArrayList<>();
			for (final IconDrawTask task : tasks)
			{
				jobs.add(new Runnable()
				{
					@Override
					public void run()
					{
						task.scaleIcon();
					}
				});
			}
			ThreadHelper.getInstance().processInParallel(jobs, true);
		}

		int xToSubtract = drawBounds == null ? 0 : (int) drawBounds.x;
		int yToSubtract = drawBounds == null ? 0 : (int) drawBounds.y;

		for (final IconDrawTask task : tasks)
		{
			drawIconWithBackgroundAndMask(mapOrSnippet, task.scaledImageAndMasks, background, landTexture,
					((int) task.centerLoc.x) - xToSubtract, ((int) task.centerLoc.y) - yToSubtract);
		}

		return tasks;
	}

	/**
	 * Draws content masks on top of the land mask so that icons that protrude over coastlines don't turn into ocean when text is drawn on
	 * top of them.
	 */
	public void drawContentMasksOntoLandMask(Image landMask, List<IconDrawTask> tasks, Rectangle drawBounds)
	{
		for (final IconDrawTask task : tasks)
		{
			if (drawBounds == null || task.overlaps(drawBounds))
			{
				int xLoc = (int) task.centerLoc.x - task.scaledWidth / 2;
				int yLoc = (int) task.centerLoc.y - task.scaledHeight / 2;

				int xToSubtract = drawBounds == null ? 0 : (int) drawBounds.x;
				int yToSubtract = drawBounds == null ? 0 : (int) drawBounds.y;

				ImageHelper.drawIfPixelValueIsGreaterThanTarget(landMask, task.scaledImageAndMasks.getOrCreateContentMask(),
						xLoc - xToSubtract, yLoc - yToSubtract);
			}
		}
	}

	/**
	 * Removes icon edits for centers if the icon failed to draw. That way it doesn't show up later when you draw more land around the
	 * center and thus give the icon enough space to draw.
	 * 
	 * @param edits
	 * @param graph
	 */
	public void removeIconEditsThatFailedToDraw(MapEdits edits, WorldGraph graph)
	{
		for (CenterEdit cEdit : edits.centerEdits)
		{
			// I only handle center icons, not trees, since I don't want to stop drawing all trees for a center just because
			// some of them ended up in the ocean.

			if (cEdit.icon == null)
			{
				continue;
			}

			Center center = graph.centers.get(cEdit.index);
			List<IconDrawTask> tasks = anchoredIconsToDraw.get(center);
			if (tasks != null && tasks.stream().anyMatch(task -> task.type != IconType.trees && task.failedToDraw))
			{
				cEdit.icon = null;
				List<IconDrawTask> tasksToRemove = tasks.stream().filter(task -> task.type != IconType.trees && task.failedToDraw)
						.collect(Collectors.toList());
				for (IconDrawTask toRemove : tasksToRemove)
				{
					tasks.remove(toRemove);
				}
			}
		}
	}

	/**
	 * Adds icon draw tasks to draw cities. Side effect � if a city is placed where it cannot be drawn, this will un-mark it as a city.
	 * 
	 * @return IconDrawTask of each city icon added. Needed to avoid drawing text on top of cities.
	 */
	public List<IconDrawTask> addOrUnmarkCities()
	{
		Map<String, Tuple2<ImageAndMasks, Integer>> cityIcons = ImageCache.getInstance(imagesPath).getIconsWithWidths(IconType.cities,
				cityIconTypeForNewMaps);
		if (cityIcons.isEmpty())
		{
			Logger.println("Cities will not be drawn because there are no city icons.");
			return new ArrayList<>(0);
		}

		List<String> cityNames = new ArrayList<>(cityIcons.keySet());

		List<IconDrawTask> cities = new ArrayList<>();

		for (Center c : graph.centers)
		{
			if (c.isCity)
			{
				String cityName = cityNames.get(rand.nextInt(cityNames.size()));
				double scaledWidth = cityIcons.get(cityName).getSecond();
				ImageAndMasks imageAndMasks = cityIcons.get(cityName).getFirst();
				FreeIcon icon = new FreeIcon(resolutionScale, c.loc,
						getDimensionsWhenScaledByWidth(imageAndMasks.image.size(), scaledWidth), IconType.cities, cityIconTypeForNewMaps,
						cityName);

				IconDrawTask task = icon.toIconDrawTask(imagesPath, resolutionScale, cityScale);
				// Updates to the line below will will likely need to also update doesCityFitOnLand.
				if (!isContentBottomTouchingWater(task) && !isNeighborACity(c))
				{
					freeIcons.addOrReplace(icon);
					cities.add(task);
				}
				else
				{
					c.isCity = false;
				}
			}
		}

		return cities;
	}

	private boolean isNeighborACity(Center center)
	{
		return center.neighbors.stream().anyMatch(c -> c.isCity);
	}

	/**
	 * Creates tasks for drawing mountains and hills.
	 * 
	 * @return
	 */
	public void addOrUnmarkMountainsAndHills(List<Set<Center>> mountainAndHillGroups)
	{
		// Maps mountain range ids (the ids in the file names) to list of mountain images and their masks.
		ListMap<String, ImageAndMasks> mountainImagesById = ImageCache.getInstance(imagesPath)
				.getAllIconGroupsAndMasksForType(IconType.mountains);

		// Maps mountain range ids (the ids in the file names) to list of hill images and their masks.
		// The hill image file names must use the same ids as the mountain ranges.
		ListMap<String, ImageAndMasks> hillImagesById = ImageCache.getInstance(imagesPath).getAllIconGroupsAndMasksForType(IconType.hills);

		// Warn if images are missing
		for (String hillGroupId : hillImagesById.keySet())
		{
			if (!mountainImagesById.containsKey(hillGroupId))
			{
				Logger.println("No mountain images found for the hill group \"" + hillGroupId + "\". Those hill images will be ignored.");
			}
		}
		for (String mountainGroupId : mountainImagesById.keySet())
		{
			if (!hillImagesById.containsKey(mountainGroupId))
			{
				Logger.println("No hill images found for the mountain group \"" + mountainGroupId
						+ "\". That mountain group will not have hills.");
			}
		}

		// Maps from the mountainRangeId of Centers to the range id's from the mountain image file names.
		Map<Integer, String> rangeMap = new TreeMap<>();

		for (Set<Center> group : mountainAndHillGroups)
		{
			for (Center c : group)
			{
				String fileNameRangeId = rangeMap.get(c.mountainRangeId);
				if ((fileNameRangeId == null))
				{
					fileNameRangeId = new ArrayList<>(mountainImagesById.keySet()).get(rand.nextInt(mountainImagesById.keySet().size()));
					rangeMap.put(c.mountainRangeId, fileNameRangeId);
				}

				if (c.isMountain)
				{
					List<ImageAndMasks> imagesInRange = mountainImagesById.get(fileNameRangeId);

					// I'm deliberately putting this line before checking center size so that the
					// random number generator is used the same no matter what resolution the map
					// is drawn at.
					int i = rand.nextInt(imagesInRange.size());

					double baseWidth = findNewMountainBaseWidth(c);

					// Make sure the image will be at least 1 pixel wide.

					Point loc = getImageCenterToDrawImageNearBottomOfCenter(imagesInRange.get(i).image, baseWidth, c);
					Dimension size = getDimensionsWhenScaledByWidth(imagesInRange.get(i).image.size(), baseWidth);
					FreeIcon icon = new FreeIcon(resolutionScale, loc, size, IconType.mountains, fileNameRangeId, i);

					IconDrawTask task = icon.toIconDrawTask(imagesPath, resolutionScale, mountainScale);

					if (!isContentBottomTouchingWater(task))
					{
						freeIcons.addOrReplace(icon);
					}
					else
					{
						c.isMountain = false;
					}
				}
				else if (c.isHill)
				{
					// TODO Do what I did with mountains to hills too
					List<ImageAndMasks> imagesInGroup = hillImagesById.get(fileNameRangeId);

					if (imagesInGroup != null && !imagesInGroup.isEmpty())
					{
						int i = rand.nextInt(imagesInGroup.size());

						int scaledSize = findNewHillBaseWidth(c);

						// Make sure the image will be at least 1 pixel wide.
						if (scaledSize >= 1)
						{
							IconDrawTask task = new IconDrawTask(imagesInGroup.get(i), IconType.hills, c.loc, scaledSize);
							if (!isContentBottomTouchingWater(task))
							{
								anchoredIconsToDraw.getOrCreate(c).addOrReplace(task);
								centerIcons.put(c.index, new CenterIcon(CenterIconType.Hill, fileNameRangeId, i));
							}
							else
							{
								c.isHill = false;
							}
						}
					}
				}
			}
		}
	}

	private Point getImageCenterToDrawImageNearBottomOfCenter(Image image, double scaledWidth, Center c)
	{
		double scaledHeight = getDimensionsWhenScaledByWidth(image.size(), scaledWidth).height;
		Corner bottom = c.findBottom();
		if (bottom == null)
		{
			// The center has no corners. This should not happen.
			return c.loc;
		}
		return new Point(c.loc.x, bottom.loc.y - (scaledHeight / 2) - (c.findHight() / 4));
	}

	private double findNewMountainBaseWidth(Center c)
	{
		// Find the center's size along the x axis.
		return findCenterWidthBetweenNeighbors(c);
	}

	private double findNewHillBaseWidth(Center c)
	{
		// Find the center's size along the x axis and reduce it to make hills smaller than mountains.
		return findNewMountainBaseWidth(c) * hillSizeComparedToMountains;
	}

	public void addSandDunes()
	{
		ListMap<String, ImageAndMasks> sandGroups = ImageCache.getInstance(imagesPath).getAllIconGroupsAndMasksForType(IconType.sand);
		if (sandGroups == null || sandGroups.isEmpty())
		{
			Logger.println("Sand dunes will not be drawn because no sand images were found.");
			return;
		}

		String groupId = ProbabilityHelper.sampleUniform(rand, sandGroups.keySet());

		// Load the sand dune images.
		List<ImageAndMasks> duneImages = sandGroups.get(groupId);

		if (duneImages == null || duneImages.isEmpty())
		{
			Logger.println("Sand dunes will not be drawn because no sand dune images were found in the group '" + groupId + "'.");
			return;
		}

		List<Set<Center>> groups = findCenterGroups(graph, maxGapBetweenBiomeGroups, new Function<Center, Boolean>()
		{
			public Boolean apply(Center center)
			{
				return center.biome.equals(Biome.TEMPERATE_DESERT);
			}
		});

		// This is the probability that a temperate desert will be a dune field.
		double duneProbabilityPerBiomeGroup = 0.6;
		double duneProbabilityPerCenter = 0.5;

		for (Set<Center> group : groups)
		{
			if (rand.nextDouble() < duneProbabilityPerBiomeGroup)
			{
				for (Center c : group)
				{
					if (rand.nextDouble() < duneProbabilityPerCenter)
					{
						c.isSandDunes = true;

						int i = rand.nextInt(duneImages.size());
						freeIcons.addOrReplace(new FreeIcon(resolutionScale, c.loc, 1.0, IconType.sand, groupId, c.index));
					}
				}
			}
		}
	}

	public void addTrees()
	{
		addCenterTrees();
	}

	public static Set<TreeType> getTreeTypesForBiome(Biome biome)
	{
		Set<TreeType> result = new TreeSet<TreeType>();
		for (final ForestType forest : forestTypes)
		{
			if (forest.biome == biome)
			{
				result.add(forest.treeType);
			}
		}
		return result;
	}

	public static Set<Biome> getBiomesForTreeType(TreeType type)
	{
		Set<Biome> result = new TreeSet<>();
		for (final ForestType forest : forestTypes)
		{
			if (forest.treeType == type)
			{
				result.add(forest.biome);
			}
		}
		return result;
	}

	private static List<ForestType> forestTypes;
	static
	{
		forestTypes = new ArrayList<>();
		forestTypes.add(new ForestType(TreeType.Deciduous, Biome.TEMPERATE_RAIN_FOREST, 0.5, 1.0));
		forestTypes.add(new ForestType(TreeType.Pine, Biome.TAIGA, 1.0, 1.0));
		forestTypes.add(new ForestType(TreeType.Pine, Biome.SHRUBLAND, 1.0, 1.0));
		forestTypes.add(new ForestType(TreeType.Pine, Biome.HIGH_TEMPERATE_DECIDUOUS_FOREST, 1.0, 0.25));
		forestTypes.add(new ForestType(TreeType.Cacti, Biome.HIGH_TEMPERATE_DESERT, 1.0 / 8.0, 0.1));
	}

	private void addCenterTrees()
	{
		trees.clear();

		for (final ForestType forest : forestTypes)
		{
			if (forest.biomeFrequency != 1.0)
			{
				List<Set<Center>> groups = findCenterGroups(graph, maxGapBetweenBiomeGroups, new Function<Center, Boolean>()
				{
					public Boolean apply(Center center)
					{
						return center.biome.equals(forest.biome);
					}
				});
				for (Set<Center> group : groups)
				{
					if (rand.nextDouble() < forest.biomeFrequency)
					{
						for (Center c : group)
						{
							if (canGenerateTreesOnCenter(c))
							{
								trees.put(c.index, new CenterTrees(forest.treeType.toString().toLowerCase(), forest.density, c.treeSeed));
							}
						}
					}
				}
			}
		}

		// Process forest types that don't use biome groups separately for efficiency.
		for (Center c : graph.centers)
		{
			for (ForestType forest : forestTypes)
			{
				if (forest.biomeFrequency == 1.0)
				{
					if (forest.biome.equals(c.biome))
					{
						if (canGenerateTreesOnCenter(c))
						{
							trees.put(c.index, new CenterTrees(forest.treeType.toString().toLowerCase(), forest.density, c.treeSeed));
						}
					}
				}

			}
		}
	}

	private boolean canGenerateTreesOnCenter(Center c)
	{
		return c.elevation < mountainElevationThreshold && !c.isWater && !c.isCoast;
	}

	/**
	 * Draws all trees in this.trees.
	 */
	public void convertTreesToFreeIcons(Collection<Center> centersToConvert, MapEdits edits, WarningLogger warningLogger)
	{
		// Load the images and masks.
		ListMap<String, ImageAndMasks> treesById = ImageCache.getInstance(imagesPath).getAllIconGroupsAndMasksForType(IconType.trees);
		if (treesById == null || treesById.isEmpty())
		{
			Logger.println("Trees will not be drawn because no tree images were found.");
			return;
		}

		for (Center c : centersToConvert)
		{
			CenterTrees cTrees = edits.centerEdits.get(c.index).trees;
			if (cTrees != null)
			{
				final String groupId = changeGroupIdIfNeeded(cTrees.treeType, cTrees.treeType.toString(), treesById, warningLogger);
				if (groupId == null || !treesById.containsKey(groupId) || treesById.get(groupId).size() == 0)
				{
					// Skip since there are no tree images to use.
					// TODO Lock
					edits.centerEdits.get(c.index).trees = null;
					continue;
				}

				if (!cTrees.treeType.equals(groupId))
				{
					// TODO lock
					cTrees.treeType = groupId;
				}

				drawTreesAtCenterAndCorners(graph, c, cTrees);
			}
			edits.centerEdits.get(c.index).trees = null;
		}
	}

	private double calcTreeDensityScale()
	{
		// The purpose of the number below is to make it so that adjusting the height of trees also adjusts the density so that the spacing
		// between trees remains
		// looking about the same. As for how I calculated this number, the minimum treeHeightScale is 0.1, and each tick on the tree height
		// slider increases by 0.05,
		// with the highest possible value being 0.85. So I then fitted a curve to (0.1, 12), (0.35, 2), (0.5, 1.0), (0.65, 0.6) and (0.85,
		// 0.3).
		// The first point is the minimum tree height. The second is the default. The third is the old default. The fourth is the maximum.
		return 2.0 * ((71.5152) * (treeHeightScale * treeHeightScale * treeHeightScale * treeHeightScale)
				- 178.061 * (treeHeightScale * treeHeightScale * treeHeightScale) + 164.876 * (treeHeightScale * treeHeightScale)
				- 68.633 * treeHeightScale + 11.3855);

	}

	private void drawTreesAtCenterAndCorners(WorldGraph graph, Center center, CenterTrees cTrees)
	{
		freeIcons.clearTrees(center.index);
		
		List<ImageAndMasks> unscaledImages = ImageCache.getInstance(imagesPath).getAllIconGroupsAndMasksForType(IconType.trees).get(cTrees.treeType);
		Random rand = new Random(cTrees.randomSeed);
		addTreeNearLocation(graph, unscaledImages, center.loc, cTrees.density, center, rand, cTrees.treeType);

		// Draw trees at the neighboring corners too.
		// Note that corners use their own Random instance because the random seed of that random needs to not depend on the center or else
		// trees would be placed differently based on which center drew first.
		for (Corner corner : center.corners)
		{
			if (shouldCenterDrawTreesForCorner(center, corner))
			{
				addTreeNearLocation(graph, unscaledImages, corner.loc, cTrees.density, center, rand, cTrees.treeType);
			}
		}
	}

	/**
	 * Ensures at most 1 center draws trees at each corner.
	 */
	private boolean shouldCenterDrawTreesForCorner(Center center, Corner corner)
	{
		Center centerWithSmallestIndex = null;
		for (Center t : corner.touches)
		{
			CenterTrees tTrees = trees.get(t.index);
			if (tTrees == null || tTrees.treeType == null || tTrees.treeType.equals(""))
			{
				continue;
			}

			if (centerWithSmallestIndex == null || (t.index < centerWithSmallestIndex.index))
			{
				centerWithSmallestIndex = t;
			}
		}

		if (centerWithSmallestIndex != null)
		{
			return center.index == centerWithSmallestIndex.index;
		}
		return true;
	}

	private static class ForestType
	{
		TreeType treeType;
		Biome biome;
		double density;
		double biomeFrequency;

		/**
		 * @param biomeProb
		 *            If this is not 1.0, groups of centers of biome type "biome" will be found and each groups will have this type of
		 *            forest with probability biomeProb.
		 */
		public ForestType(TreeType treeType, Biome biome, double density, double biomeFrequency)
		{
			this.treeType = treeType;
			this.biome = biome;
			this.density = density;
			this.biomeFrequency = biomeFrequency;
		}
	};

	private double findCenterWidthBetweenNeighbors(Center c)
	{
		Center eastMostNeighbor = Collections.max(c.neighbors, new Comparator<Center>()
		{
			public int compare(Center c1, Center c2)
			{
				return Double.compare(c1.loc.x, c2.loc.x);
			}
		});
		Center westMostNeighbor = Collections.min(c.neighbors, new Comparator<Center>()
		{
			public int compare(Center c1, Center c2)
			{
				return Double.compare(c1.loc.x, c2.loc.x);
			}
		});
		double cSize = Math.abs(eastMostNeighbor.loc.x - westMostNeighbor.loc.x);
		return cSize;
	}

	private void addTreeNearLocation(WorldGraph graph, List<ImageAndMasks> unscaledImages, Point loc, double forestDensity, Center center,
			Random rand, String groupId)
	{
		// Convert the forestDensity into an integer number of trees to draw such that the expected
		// value is forestDensity.
		double density = forestDensity * treeDensityScale;
		double fraction = density - (int) density;
		int extra = rand.nextDouble() < fraction ? 1 : 0;
		int numTrees = ((int) density) + extra;

		for (int i = 0; i < numTrees; i++)
		{
			int index = rand.nextInt(unscaledImages.size());

			// Draw the image such that it is centered in the center of c.
			int x = (int) (loc.x);
			int y = (int) (loc.y);

			final double scale = (averageCenterWidthBetweenNeighbors / 10.0);
			x += rand.nextGaussian() * scale;
			y += rand.nextGaussian() * scale;
			
			FreeIcon icon = new FreeIcon(resolutionScale, new Point(x, y), 1.0, IconType.trees, groupId, index);
			freeIcons.addOrReplace(icon);
		}
	}

	private boolean isContentBottomTouchingWater(IconDrawTask iconTask)
	{
		if (iconTask.unScaledImageAndMasks.getOrCreateContentMask().getType() != ImageType.Binary)
			throw new IllegalArgumentException("Mask type must be TYPE_BYTE_BINARY for checking whether icons touch water.");

		final int imageUpperLeftX = (int) iconTask.centerLoc.x - iconTask.scaledWidth / 2;
		final int imageUpperLeftY = (int) iconTask.centerLoc.y - iconTask.scaledHeight / 2;

		Rectangle scaledContentBounds;
		{
			IntRectangle contentBounds = iconTask.unScaledImageAndMasks.getOrCreateContentBounds();
			if (contentBounds == null)
			{
				// The icon is fully transparent.
				return false;
			}

			final double xScaleToScaledIconSpace = iconTask.scaledWidth
					/ (double) iconTask.unScaledImageAndMasks.getOrCreateContentMask().getWidth();
			final double yScaleToScaledIconSpace = iconTask.scaledHeight
					/ (double) iconTask.unScaledImageAndMasks.getOrCreateContentMask().getHeight();

			scaledContentBounds = new Rectangle(contentBounds.x * xScaleToScaledIconSpace, contentBounds.y * yScaleToScaledIconSpace,
					contentBounds.width * xScaleToScaledIconSpace, contentBounds.height * yScaleToScaledIconSpace);
		}

		// The constant in this number is in number of pixels at 100% resolution. I include the resolution here
		// so that the loop below will make the same number of steps (approximately) no matter the resolution.
		// This is to reduce the chances that icons will appear or disappear when you draw the map at a different resolution.
		final double stepSize = 2.0 * resolutionScale;

		final double xScaleToMaskSpace = ((double) iconTask.unScaledImageAndMasks.getOrCreateContentMask().getWidth())
				/ iconTask.scaledWidth;
		final double yScaleToMaskSpace = ((double) iconTask.unScaledImageAndMasks.getOrCreateContentMask().getHeight())
				/ iconTask.scaledHeight;

		for (double x = scaledContentBounds.x; x < scaledContentBounds.x + scaledContentBounds.width; x += stepSize)
		{
			int xInMask = (int) (x * xScaleToMaskSpace);
			Integer yInMask = iconTask.unScaledImageAndMasks.getContentYStart(xInMask);
			if (yInMask == null)
			{
				continue;
			}
			int y = (int) (yInMask * (1.0 / yScaleToMaskSpace));

			Center center = graph.findClosestCenter(imageUpperLeftX + x, imageUpperLeftY + y);
			if (center.isWater)
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * Creates a bounding box that includes all icons drawn for a center the last time icons were drawn for it.
	 * 
	 * @param center
	 *            Center to get icons bounds for.
	 * @return A rectangle if center had icons drawn. Null otherwise.
	 */
	private Rectangle getBoundingBoxOfIconsForCenter(Center center)
	{
		if (anchoredIconsToDraw.get(center) == null)
		{
			return null;
		}

		Rectangle bounds = null;
		for (IconDrawTask iconTask : anchoredIconsToDraw.get(center))
		{
			Rectangle iconBounds = iconTask.createBounds();
			if (bounds == null)
			{
				bounds = iconBounds;
			}
			else
			{
				bounds = bounds.add(iconBounds);
			}
		}

		return bounds;
	}

	/**
	 * Creates a bounding box that includes all icons drawn for a collection of centers from the last time icons were drawn.
	 * 
	 * @param centers
	 *            A collection of centers to get icons bounds for.
	 * @return A rectangle if any of the centers had icons drawn. Null otherwise.
	 */
	public Rectangle getBoundingBoxOfIconsForCenters(Collection<Center> centers)
	{
		Rectangle bounds = null;
		for (Center center : centers)
		{
			if (bounds == null)
			{
				bounds = getBoundingBoxOfIconsForCenter(center);
			}
			else
			{
				Rectangle b = getBoundingBoxOfIconsForCenter(center);
				if (b != null)
				{
					bounds = bounds.add(b);
				}
			}
		}
		return bounds;
	}
}
