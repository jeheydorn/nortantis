package nortantis;

import nortantis.editor.CenterEdit;
import nortantis.editor.CenterIconType;
import nortantis.editor.CenterTrees;
import nortantis.editor.FreeIcon;
import nortantis.geom.*;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.platform.*;
import nortantis.swing.MapEdits;
import nortantis.util.*;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class IconDrawer
{
	public static final double mountainElevationThreshold = 0.58;
	public static final double hillElevationThreshold = 0.53;
	final double meanPolygonWidth;
	final double cityScale;
	// Max gap (in polygons) between mountains for considering them a single
	// group. Warning:
	// there tend to be long polygons along edges, so if this value is much more
	// than 2,
	// mountains near the ocean may be connected despite long distances between
	// them..
	private final int maxGapSizeInMountainClusters = 2;
	private final int maxGapBetweenBiomeGroups = 2;
	// For hills and mountains, if a polygon is this number times
	// meanPolygonWidth wide, no icon will be added to it when creating a new
	// map.
	final double maxAverageCenterWidthsBetweenNeighborsToDrawGeneratedMountainOrHill = 5.0;
	final double maxSizeToDrawGeneratedMountainOrHill;
	private final double mountainScale;
	private final double hillScale;
	private final double duneScale;
	private final double treeHeightScale;
	private final double treeDensityScale;
	private List<IconDrawTask> iconsToDraw;
	FreeIconCollection freeIcons;
	WorldGraph graph;
	Random rand;
	private double averageCenterWidthBetweenNeighbors;
	/**
	 * This number exists because I used averageCenterWidthBetweenNeighbors, then made changes in the graph creation algorithm but changed
	 * that number, but I didn't want those changes to cause icons to scale differently, so I'm using this constant to keep them
	 * approximately the same.
	 */
	private String cityIconTypeForNewMaps;
	private double resolutionScale;
	private double decorationScale;
	private String customImagesPath;
	private String artPackForNewMap;
	public static final Biome sandDunesBiome = Biome.TEMPERATE_DESERT;
	private Map<IconType, Color> fillColorsByType;
	private Map<IconType, HSBColor> iconFilterColorsByType;
	// Implemented as a map instead of a set for concurrency.
	private Map<IconType, Boolean> maximizeOpacityByType;
	private Map<IconType, Boolean> fillWithColorByType;

	public IconDrawer(WorldGraph graph, Random rand, MapSettings settings)
	{
		this.graph = graph;
		this.rand = rand;
		this.cityIconTypeForNewMaps = settings.cityIconTypeName;
		this.customImagesPath = settings.customImagesPath;
		this.artPackForNewMap = settings.artPack;
		this.resolutionScale = settings.resolution;

		if (!settings.edits.isInitialized())
		{
			this.freeIcons = new FreeIconCollection();
			settings.edits.freeIcons = freeIcons;
		}
		else
		{
			freeIcons = settings.edits.freeIcons;
		}

		iconsToDraw = new ArrayList<>();

		meanPolygonWidth = graph.getMeanCenterWidth();
		duneScale = settings.duneScale;

		mountainScale = settings.mountainScale;
		hillScale = settings.hillScale;
		cityScale = settings.cityScale;
		// I didn't create a setting for map-level decoration scale because it didn't seem very useful.
		decorationScale = 1.0;

		treeHeightScale = settings.treeHeightScale;
		treeDensityScale = calcTreeDensityScale();

		averageCenterWidthBetweenNeighbors = graph.getMeanCenterWidthBetweenNeighbors();
		maxSizeToDrawGeneratedMountainOrHill = averageCenterWidthBetweenNeighbors * maxAverageCenterWidthsBetweenNeighborsToDrawGeneratedMountainOrHill;
		fillColorsByType = settings.copyIconColorsByType();
		iconFilterColorsByType = settings.copyIconFilterColorsByType();
		maximizeOpacityByType = settings.copymaximizeOpacityByType();
		fillWithColorByType = settings.copyFillWithColorByType();
	}

	public void markMountains()
	{
		for (Center c : graph.centers)
		{
			if (c.elevation > mountainElevationThreshold && !c.isBorder && graph.findCenterWidthBetweenNeighbors(c) < maxSizeToDrawGeneratedMountainOrHill)
			{
				c.isMountain = true;
			}
		}
	}

	public void markHills()
	{
		for (Center c : graph.centers)
		{
			if (c.elevation < mountainElevationThreshold && c.elevation > hillElevationThreshold && graph.findCenterWidthBetweenNeighbors(c) < maxSizeToDrawGeneratedMountainOrHill)

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
				// I'm generating these numbers now instead of waiting to see if
				// they are needed in the if statements below because
				// there is a problem in the graph such that maps generated at
				// different resolutions can have slight differences in their
				// rivers, which appears to be caused by floating point
				// precision issues while calculating elevation of corners.
				// Thus, the slightest change in a river on one corner could
				// cause a center to change whether it's a river, which
				// would modify the way the random number generator is called,
				// which would then change everything else used by that
				// random number generator after it. But this fix only reduces
				// the issue, since other things will also change
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

	private Rectangle convertToFreeIconsIfNeeded(Collection<Center> centersToConvert, MapEdits edits, WarningLogger warningLogger)
	{
		if (centersToConvert.isEmpty())
		{
			return null;
		}

		Rectangle changeBounds = null;

		for (Center center : centersToConvert)
		{
			CenterEdit cEdit = edits.centerEdits.get(center.index);
			if (cEdit.icon != null)
			{
				if (cEdit.icon.iconType == CenterIconType.Mountain)
				{
					ListMap<String, ImageAndMasks> mountainImagesById = ImageCache.getInstance(cEdit.icon.artPack, customImagesPath).getIconGroupsAsListsForType(IconType.mountains);
					changeBounds = Rectangle.add(changeBounds, convertNonTreeShuffledAnchoredIcon(edits, center, cEdit, mountainImagesById, warningLogger));
				}
				else if (cEdit.icon.iconType == CenterIconType.Hill)
				{
					ListMap<String, ImageAndMasks> hillImagesById = ImageCache.getInstance(cEdit.icon.artPack, customImagesPath).getIconGroupsAsListsForType(IconType.hills);
					changeBounds = Rectangle.add(changeBounds, convertNonTreeShuffledAnchoredIcon(edits, center, cEdit, hillImagesById, warningLogger));
				}
				else if (cEdit.icon.iconType == CenterIconType.Dune)
				{
					ListMap<String, ImageAndMasks> duneImages = ImageCache.getInstance(cEdit.icon.artPack, customImagesPath).getIconGroupsAsListsForType(IconType.sand);
					changeBounds = Rectangle.add(changeBounds, convertNonTreeShuffledAnchoredIcon(edits, center, cEdit, duneImages, warningLogger));
				}
				else if (cEdit.icon.iconType == CenterIconType.City)
				{
					Tuple3<String, String, String> artPackAndGroupAndName = adjustNamedIconGroupAndNameIfNeeded(IconType.cities, cEdit.icon.artPack, cEdit.icon.iconGroupId, cEdit.icon.iconName,
							warningLogger);

					if (artPackAndGroupAndName != null)
					{
						String artPack = artPackAndGroupAndName.getFirst();
						String groupId = artPackAndGroupAndName.getSecond();
						String name = artPackAndGroupAndName.getThird();

						IconType type = centerIconTypeToIconType(cEdit.icon.iconType);
						FreeIcon icon = new FreeIcon(resolutionScale, center.loc, 1.0, type, artPack, groupId, name, cEdit.index, fillColorsByType.get(type), iconFilterColorsByType.get(type),
								maximizeOpacityByType.get(type), fillWithColorByType.get(type));
						IconDrawTask drawTask = toIconDrawTask(icon);

						if (!isContentBottomTouchingWater(drawTask))
						{
							changeBounds = Rectangle.add(changeBounds, getAnchoredNonTreeIconBoundsAt(center.index));
							freeIcons.addOrReplace(icon);
							changeBounds = Rectangle.add(changeBounds, drawTask.createBounds());
						}

						edits.centerEdits.put(cEdit.index, cEdit.copyWithIcon(null));
					}
					else
					{
						edits.centerEdits.put(cEdit.index, cEdit.copyWithIcon(null));
					}
				}

			}
		}

		changeBounds = Rectangle.add(changeBounds, convertTreesFromCenterEditsToFreeIcons(centersToConvert, edits, warningLogger));
		return changeBounds;
	}

	private double getWidthScaleForNewShuffledIcon(Center center, IconType type)
	{
		if (type == IconType.mountains)
		{
			return graph.findCenterWidthBetweenNeighbors(center) / averageCenterWidthBetweenNeighbors;
		}
		else if (type == IconType.hills)
		{
			return graph.findCenterWidthBetweenNeighbors(center) / averageCenterWidthBetweenNeighbors;
		}
		else if (type == IconType.sand)
		{
			return 1.0;
		}
		else
		{
			throw new NotImplementedException("Unrecognized icon type: " + type);
		}
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

	private Rectangle convertNonTreeShuffledAnchoredIcon(MapEdits edits, Center center, CenterEdit cEdit, ListMap<String, ImageAndMasks> iconsByGroup, WarningLogger warningLogger)
	{
		if (cEdit.icon == null)
		{
			return null;
		}

		IconType type = centerIconTypeToIconType(cEdit.icon.iconType);
		final String groupId = getNewGroupIdIfNeeded(cEdit.icon.iconGroupId, type, cEdit.icon.artPack, iconsByGroup, warningLogger, false);
		if (groupId == null || !iconsByGroup.containsKey(groupId) || iconsByGroup.get(groupId).size() == 0)
		{
			edits.centerEdits.put(cEdit.index, cEdit.copyWithIcon(null));
			// There is no change bounds to return because this icon was never
			// drawn.
			return null;
		}

		Point loc;
		if (type == IconType.mountains)
		{
			loc = getAnchoredMountainDrawPoint(center, groupId, cEdit.icon.iconIndex, mountainScale, iconsByGroup);
		}
		else
		{
			loc = center.loc;
		}
		double scale = getWidthScaleForNewShuffledIcon(center, type);
		FreeIcon icon = new FreeIcon(resolutionScale, loc, scale, type, cEdit.icon.artPack, groupId, cEdit.icon.iconIndex, cEdit.index, fillColorsByType.get(type), iconFilterColorsByType.get(type),
				maximizeOpacityByType.get(type), fillWithColorByType.get(type));
		Rectangle changeBounds = null;
		IconDrawTask drawTask = toIconDrawTask(icon);
		if (!isContentBottomTouchingWater(drawTask))
		{
			changeBounds = Rectangle.add(changeBounds, getAnchoredNonTreeIconBoundsAt(center.index));
			freeIcons.addOrReplace(icon);
			changeBounds = Rectangle.add(changeBounds, drawTask.createBounds());
		}
		else if (freeIcons.getNonTree(center.index) != null)
		{
			changeBounds = Rectangle.add(changeBounds, getAnchoredNonTreeIconBoundsAt(center.index));
			freeIcons.remove(freeIcons.getNonTree(center.index));
		}

		edits.centerEdits.put(cEdit.index, cEdit.copyWithIcon(null));

		return changeBounds;
	}

	private Rectangle getAnchoredNonTreeIconBoundsAt(int centerIndex)
	{
		Rectangle changeBounds = null;
		FreeIcon icon = freeIcons.getNonTree(centerIndex);
		if (icon != null)
		{
			IconDrawTask task = toIconDrawTask(icon);
			if (task != null)
			{
				changeBounds = Rectangle.add(changeBounds, task.createBounds());
			}
		}
		return changeBounds;
	}

	private Rectangle getAnchoredTreeIconBoundsAt(int centerIndex)
	{
		Rectangle changeBounds = null;
		List<FreeIcon> icons = freeIcons.getTrees(centerIndex);
		for (FreeIcon tree : icons)
		{
			IconDrawTask task = toIconDrawTask(tree);
			if (task != null)
			{
				changeBounds = Rectangle.add(changeBounds, task.createBounds());
			}
		}
		return changeBounds;
	}

	public Point getAnchoredMountainDrawPoint(Center center, String groupId, int iconIndex, double mountainScale, ListMap<String, ImageAndMasks> iconsByGroup)
	{
		ImageAndMasks imageAndMasks = iconsByGroup.get(groupId).get(iconIndex % iconsByGroup.get(groupId).size());
		double scale = getWidthScaleForNewShuffledIcon(center, IconType.mountains);
		double scaledWidth = getBaseWidth(IconType.mountains, imageAndMasks) * scale;
		return getImageCenterToDrawImageNearBottomOfCenter(imageAndMasks.image, scaledWidth * mountainScale, center);
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
		return new Point(c.loc.x, bottom.loc.y - (scaledHeight / 2) - getOffsetFromCenterBottomToPutBottomOfMountainImageAt(c.findHeight()));
	}

	private double getOffsetFromCenterBottomToPutBottomOfMountainImageAt(double centerHeight)
	{
		return centerHeight / 4.0;
	}

	public double getUnanchoredMountainYChangeFromMountainScaleChange(FreeIcon icon, double newMountainScale, ImageAndMasks imageAndMasks)
	{
		IconDrawTask task = toIconDrawTask(icon);
		if (task == null)
		{
			return 0.0;
		}

		Image image = task.unScaledImageAndMasks.image;
		// I'm excluding icon level scaling in this calculation because icon
		// level scaling is done about the icon's center even for
		// mountains,
		// so it doesn't affect the Y offset for mountains.
		double prevScaledHeightWithoutIconScale = getDimensionsWhenScaledByWidth(image.size(), getBaseWidth(IconType.mountains, imageAndMasks) * mountainScale).height;
		double newScaledHeightWithoutIconScale = getDimensionsWhenScaledByWidth(image.size(), getBaseWidth(IconType.mountains, imageAndMasks) * newMountainScale).height;
		double offsetFromBottom = getOffsetFromCenterBottomToPutBottomOfMountainImageAt(meanPolygonWidth);
		return (prevScaledHeightWithoutIconScale / 2.0 - offsetFromBottom) - (newScaledHeightWithoutIconScale / 2.0 - offsetFromBottom);
	}

	/**
	 * This is used to add icon to draw tasks from map edits rather than using the generator to add them. Also handles Replacing the image
	 * for icons whose image does not exist, and removing icons that should not be drawn because their bottom would touch water. The actual
	 * drawing of the icons is done later.
	 * 
	 * @return The bounds of icons that changed, if any.
	 */
	public Rectangle addOrUpdateIconsFromEdits(MapEdits edits, Collection<Center> centersToUpdateIconsFor, WarningLogger warningLogger)
	{
		assert freeIcons == edits.freeIcons;

		return freeIcons.doWithLockAndReturnResult(() ->
		{
			Rectangle conversionBoundsOfIconsChanged = convertToFreeIconsIfNeeded(centersToUpdateIconsFor, edits, warningLogger);
			Rectangle removedOrReplacedChangeBounds = createDrawTasksForFreeIconsAndRemovedFailedIcons(warningLogger);
			Rectangle combined = Rectangle.add(conversionBoundsOfIconsChanged, removedOrReplacedChangeBounds);
			if (combined == null)
			{
				return combined;
			}
			double paddingForIntegerTruncation = 4.0;
			return combined.pad(paddingForIntegerTruncation, paddingForIntegerTruncation);
		});
	}

	private Rectangle createDrawTasksForFreeIconsAndRemovedFailedIcons(WarningLogger warningLogger)
	{
		// Check the performance of this method since it re-creates all icon
		// draw tasks for each incremental update, although I might not
		// have a better option.

		iconsToDraw.clear();

		// In theory it should be safe to just remove free icons as I iterate
		// over the collection, but I'm leary of it because there are
		// multiple underlying iterators involved in looping over the
		// collection, so I'm doing it afterward.
		List<FreeIcon> toRemove = new ArrayList<>();

		// Note: There's no need to update removeBounds in this loop for cases
		// that replace an icon because removeBounds it is only needed
		// for incremental draws, and code for changing an icon because the
		// previous icon did not exist will only be triggered during an
		// image refresh or an initial full draw, which are both full draws.
		for (FreeIcon icon : freeIcons)
		{
			if (icon == null)
			{
				continue;
			}

			if (icon.type == IconType.mountains)
			{
				checkAndAddIcon(icon, true, warningLogger, toRemove);
			}
			else if (icon.type == IconType.hills)
			{
				checkAndAddIcon(icon, true, warningLogger, toRemove);
			}
			else if (icon.type == IconType.sand)
			{
				checkAndAddIcon(icon, true, warningLogger, toRemove);
			}
			else if (icon.type == IconType.cities)
			{
				checkAndAddIcon(icon, true, warningLogger, toRemove);
			}
			else if (icon.type == IconType.decorations)
			{
				checkAndAddIcon(icon, false, warningLogger, toRemove);
			}
			else if (icon.type == IconType.trees)
			{
				checkAndAddIcon(icon, true, warningLogger, toRemove);
			}
		}

		Rectangle removeBounds = null;
		for (FreeIcon icon : toRemove)
		{
			IconDrawTask task = toIconDrawTask(icon);
			if (task != null)
			{
				removeBounds = Rectangle.add(removeBounds, toIconDrawTask(icon).createBounds());
			}
		}
		freeIcons.removeAll(toRemove);

		return removeBounds;
	}

	private void checkAndAddIcon(FreeIcon icon, boolean checkContentBottomTouchingWater, WarningLogger warningLogger, List<FreeIcon> toRemove)
	{
		FreeIcon updated = adjustForMissingAssetsIfNeeded(icon, warningLogger);
		if (updated == null)
		{
			toRemove.add(icon);
			return;
		}

		IconDrawTask task = toIconDrawTask(updated);

		if (task == null)
		{
			// This shouldn't happen because adjustForMissingAssetsIfNeeded should have caught any issue that caused the draw task to fail
			// to be created.
			assert false;
			toRemove.add(icon);
			return;
		}

		// Remove the icon if it is entirely off the map. I'm using the content bounds instead of the image bounds here because you can only
		// select an icon if you can mouse over its content bounds, so if its content bounds are off the map, then you cannot select the
		// icon, so it should be removed.
		if (!graph.bounds.overlaps(task.getOrCreateContentBoundsPadded()))
		{
			toRemove.add(icon);
			return;
		}

		if (checkContentBottomTouchingWater && isContentBottomTouchingWater(task))
		{
			toRemove.add(icon);
			return;
		}

		if (!icon.equals(updated))
		{
			freeIcons.replace(icon, updated);
		}
		iconsToDraw.add(toIconDrawTask(updated));
	}

	/**
	 * Replacing missing assets used by a FreeIcon.
	 * 
	 * @param icon
	 *            The original icon.
	 * @param warningLogger
	 *            Logs warnings for the user to see about which assets were replaced.
	 * @return If nothing changed, the original icon. If something changed, a new icon. If the missing assets could not be replaced, then
	 *         null.
	 */
	public FreeIcon adjustForMissingAssetsIfNeeded(FreeIcon icon, WarningLogger warningLogger)
	{
		if (icon.type == IconType.mountains || icon.type == IconType.hills || icon.type == IconType.sand || icon.type == IconType.trees)
		{
			String artPackToUse = chooseNewArtPackIfNeeded(icon.type, icon.artPack, icon.groupId, icon.iconName, warningLogger, false);
			if (!icon.artPack.equals(artPackToUse))
			{
				FreeIcon updated = icon.copyWithArtPack(artPackToUse);
				icon = updated;
			}

			ListMap<String, ImageAndMasks> iconsByGroup = ImageCache.getInstance(icon.artPack, customImagesPath).getIconGroupsAsListsForType(icon.type);
			String newGroupId = getNewGroupIdIfNeeded(icon.groupId, icon.type, artPackToUse, iconsByGroup, warningLogger, false);
			if (!icon.groupId.equals(newGroupId) && newGroupId != null)
			{
				FreeIcon updated = icon.copyWithGroupId(newGroupId);
				icon = updated;
			}

			if (icon.groupId != null && !icon.groupId.isEmpty() && iconsByGroup.get(icon.groupId) != null && iconsByGroup.get(icon.groupId).size() > 0)
			{
				return icon;
			}
			else
			{
				return null;
			}
		}
		else if (icon.type == IconType.cities || icon.type == IconType.decorations)
		{
			Tuple3<String, String, String> artPackAndGroupAndName = adjustNamedIconGroupAndNameIfNeeded(icon.type, icon.artPack, icon.groupId, icon.iconName, warningLogger);
			if (artPackAndGroupAndName != null)
			{
				if (icon.artPack.equals(artPackAndGroupAndName.getFirst()) && icon.groupId.equals(artPackAndGroupAndName.getSecond()) && icon.iconName.equals(artPackAndGroupAndName.getThird()))
				{
					// Nothing changed.
					return icon;
				}

				FreeIcon updated = icon.copyWith(artPackAndGroupAndName.getFirst(), artPackAndGroupAndName.getSecond(), artPackAndGroupAndName.getThird(), icon.color, icon.filterColor,
						icon.maximizeOpacity, icon.fillWithColor);
				return updated;
			}
			else
			{
				return null;
			}
		}
		else
		{
			throw new UnsupportedOperationException("Replacing missing assets in icon type '" + icon.type + "' has not been implemented.");
		}
	}

	private Tuple3<String, String, String> adjustNamedIconGroupAndNameIfNeeded(IconType type, String artPack, String groupId, String name, WarningLogger warningLogger)
	{
		String artPackToUse = chooseNewArtPackIfNeeded(type, artPack, groupId, name, warningLogger, false);

		Map<String, ImageAndMasks> imagesInGroup = ImageCache.getInstance(artPackToUse, customImagesPath).getIconsByNameForGroup(type, groupId);
		String newGroupId = groupId;
		if (imagesInGroup == null || imagesInGroup.isEmpty())
		{
			newGroupId = chooseNewGroupId(ImageCache.getInstance(artPackToUse, customImagesPath).getIconGroupNames(type), groupId);
			if (newGroupId == null)
			{
				warningLogger.addWarningMessage("Unable to find the " + type.getSingularName() + " image group '" + groupId + "' in art pack '" + artPack + "'. There are no " + type.getSingularName()
						+ " icons, so none will be drawn.");
				return null;
			}
			imagesInGroup = ImageCache.getInstance(artPackToUse, customImagesPath).getIconsByNameForGroup(type, newGroupId);
			if (imagesInGroup == null || imagesInGroup.isEmpty())
			{
				// This shouldn't happens since the new group id shouldn't have
				// been an option if it were empty or null.
				assert false;
				return null;
			}
			warningLogger.addWarningMessage("Unable to find the " + type.getSingularName() + " image group '" + groupId + "' in art pack '" + artPack + "'. The group '" + newGroupId
					+ "' in art pack '" + artPackToUse + "' will be used instead.");
		}

		String oldName = name;
		if (!imagesInGroup.containsKey(name) && imagesInGroup.size() > 0)
		{
			// Either the image is missing, or the icon set name changed. Choose
			// a new image in a deterministic but
			// random way.
			if (type == IconType.cities)
			{
				name = chooseNewCityIconName(imagesInGroup.keySet(), name);
			}
			else
			{
				name = ProbabilityHelper.sampleUniform(new Random(name.hashCode()), imagesInGroup.keySet());
			}
			if (name != null)
			{
				warningLogger.addWarningMessage("Unable to find the " + type.getSingularName() + " icon '" + oldName + "' in art pack '" + artPack + "', group '" + groupId + "'. The icon '" + name
						+ "' in art pack '" + artPackToUse + "', group '" + newGroupId + "', will be used instead.");
			}
		}

		return new Tuple3<>(artPackToUse, newGroupId, name);
	}

	private double getBaseWidth(IconType type, ImageAndMasks imageAndMasks)
	{
		return meanPolygonWidth * (1.0 / 11.0) * imageAndMasks.widthFromFileName;
	}

	private double getTypeLevelScale(IconType type)
	{
		if (type == IconType.mountains)
		{
			return mountainScale;
		}
		else if (type == IconType.hills)
		{
			return hillScale;
		}
		else if (type == IconType.sand)
		{
			return duneScale;
		}
		else if (type == IconType.cities)
		{
			return cityScale;
		}
		else if (type == IconType.decorations)
		{
			return decorationScale;
		}
		else if (type == IconType.trees)
		{
			return treeHeightScale;
		}
		throw new IllegalArgumentException("Unrecognized icon type for gettling type-level scale: " + type);
	}

	private String getNewGroupIdIfNeeded(final String groupId, IconType type, String artPack, ListMap<String, ImageAndMasks> iconsByGroup, WarningLogger warningLogger, boolean isForDormantTrees)
	{

		String dormantTreesMessage = isForDormantTrees ? " These trees are not visible because they were drawn at low density, but may become visible if you change the tree height in the Effects tab."
				: "";

		if (!iconsByGroup.containsKey(groupId))
		{
			// Someone removed the icon group. Choose a new group.
			String newGroupId = chooseNewGroupId(iconsByGroup.keySet(), groupId);
			if (newGroupId == null)
			{
				warningLogger.addWarningMessage("Unable to find the " + type.getSingularName() + " image group '" + groupId + "' in art pack '" + artPack + "'. There are no " + type.getSingularName()
						+ " icons in that art pack, so none will be drawn.");
			}
			else
			{
				warningLogger.addWarningMessage("Unable to find the " + type.getSingularName() + " image group '" + groupId + "' in art pack '" + artPack + "'. The group '" + newGroupId
						+ "' in that art pack will be used instead." + dormantTreesMessage);
			}
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
		List<String> compatibleCities = cityNamesToChooseFrom.stream().filter(name -> NameCreator.findCityTypeFromCityFileName(name).stream().anyMatch(type -> oldTypes.contains(type)))
				.collect(Collectors.toList());
		if (compatibleCities.isEmpty())
		{
			int index = Math.abs(oldIconName.hashCode() % cityNamesToChooseFrom.size());
			return new ArrayList<>(cityNamesToChooseFrom).get(index);
		}
		int index = Math.abs(oldIconName.hashCode() % compatibleCities.size());
		return compatibleCities.get(index);
	}

	private String chooseNewGroupId(Collection<String> groupIds, String oldGroupId)
	{
		if (groupIds.isEmpty())
		{
			return null;
		}
		int index = Math.abs(oldGroupId.hashCode() % groupIds.size());
		return groupIds.toArray(new String[groupIds.size()])[index];
	}

	private String chooseNewArtPackIfNeeded(IconType type, String oldArtPack, String oldGroupId, String oldIconName, WarningLogger warningLogger, boolean isForDormantTrees)
	{
		String dormantTreesMessage = isForDormantTrees ? " These trees are not visible because they were drawn at low density, but may become visible if you change the tree height in the Effects tab."
				: "";

		List<String> allArtPacks = Assets.listArtPacks(!StringUtils.isEmpty(customImagesPath));
		if (!allArtPacks.contains(oldArtPack))
		{
			// Prefer the custom art pack first, then ones in the art packs folder, then the installed one last.
			allArtPacks = new ArrayList<>(allArtPacks);
			Collections.reverse(allArtPacks);

			// Prefer an art pack that has an image with the same name and group name in hopes that it is the same art pack but renamed.
			for (String artPack : allArtPacks)
			{
				if (StringUtils.isEmpty(oldIconName))
				{
					if (ImageCache.getInstance(artPack, customImagesPath).hasGroupName(type, oldGroupId))
					{
						warningLogger.addWarningMessage("Unable to find the art pack '" + oldArtPack + "' to load the " + type.getSingularName() + " image group '" + oldGroupId + "'. The art pack '"
								+ artPack + "' will be used instead because it has the same image group folder name." + dormantTreesMessage);
						return artPack;
					}
				}
				else
				{
					if (ImageCache.getInstance(artPack, customImagesPath).hasNamedIcon(type, oldGroupId, oldIconName))
					{
						warningLogger.addWarningMessage("Unable to find the art pack '" + oldArtPack + "' to load the icon '" + oldIconName + "' from " + type.getSingularName() + " image group '"
								+ oldGroupId + "'. The art pack '" + artPack + "' will be used instead because it has the same image group folder and image name.");
						return artPack;
					}
				}
			}

			// Use the built-in art pack.
			String artPackToUse = Assets.installedArtPack;
			if (StringUtils.isEmpty(oldIconName))
			{
				warningLogger.addWarningMessage("Unable to find the art pack '" + oldArtPack + "' to load the " + type.getSingularName() + " image group '" + oldGroupId + "'. The art pack '"
						+ artPackToUse + "' will be used instead." + dormantTreesMessage);
			}
			else
			{
				warningLogger.addWarningMessage("Unable to find the art pack '" + oldArtPack + "' to load the icon '" + oldIconName + "' from " + type.getSingularName() + " image group '" + oldGroupId
						+ "'. The art pack '" + artPackToUse + "' will be used instead.");
			}

			return artPackToUse;
		}
		else if (ImageCache.getInstance(oldArtPack, customImagesPath).getIconGroupNames(type).isEmpty())
		{
			// Prefer the custom art pack first, then ones in the art packs folder, then the installed one last.
			allArtPacks = new ArrayList<>(allArtPacks);
			Collections.reverse(allArtPacks);

			// Prefer an art pack that has an image with the same name and group name in hopes that it is the same art pack but renamed.
			for (String artPack : allArtPacks)
			{
				if (StringUtils.isEmpty(oldIconName))
				{
					if (ImageCache.getInstance(artPack, customImagesPath).hasGroupName(type, oldGroupId))
					{
						warningLogger.addWarningMessage("The art pack '" + oldArtPack + "' no longer has " + type.getSingularName() + " images, so it does not have the " + type.getSingularName()
								+ " image group '" + oldGroupId + "'. The art pack '" + artPack + "' will be used instead because it has the same image group folder name." + dormantTreesMessage);
						return artPack;
					}
				}
				else
				{
					if (ImageCache.getInstance(artPack, customImagesPath).hasNamedIcon(type, oldGroupId, oldIconName))
					{
						warningLogger.addWarningMessage("The art pack '" + oldArtPack + "' no longer has " + type.getSingularName() + " images, so it does not have the icon '" + oldIconName
								+ "' from " + type.getSingularName() + " image group '" + oldGroupId + "'. The art pack '" + artPack
								+ "' will be used instead because it has the same image group folder and image name.");
						return artPack;
					}
				}
			}

			// Try again but take the first art pack that has images of that type.
			for (String artPack : allArtPacks)
			{
				if (!ImageCache.getInstance(artPack, customImagesPath).getIconGroupNames(type).isEmpty())

					if (StringUtils.isEmpty(oldIconName))
					{
						warningLogger.addWarningMessage("The art pack '" + oldArtPack + "' no longer has " + type.getSingularName() + " images, so it does not have the " + type.getSingularName()
								+ " image group '" + oldGroupId + "'. The art pack '" + artPack + "' will be used instead because it has " + type.getSingularName() + " images." + dormantTreesMessage);
						return artPack;
					}
					else
					{
						warningLogger.addWarningMessage("The art pack '" + oldArtPack + "' no longer has " + type.getSingularName() + " images, so it does not have the icon '" + oldIconName
								+ "' from " + type.getSingularName() + " image group '" + oldGroupId + "'. The art pack '" + artPack + "' will be used instead because it has " + type.getSingularName()
								+ " images.");
						return artPack;
					}
			}

			return oldArtPack;
		}
		else
		{
			return oldArtPack;
		}

	}

	/**
	 * Finds groups of centers that accepted according to a given function. A group is a set of centers for which there exists a path from
	 * any member of the set to any other such that you never have to skip over more than maxGapSize centers not accepted at once to get to
	 * that other center. If distanceThreshold > 1, the result will include those centers which connect centeres that are accepted.
	 */
	private static List<Set<Center>> findCenterGroups(WorldGraph graph, int maxGapSize, Function<Center, Boolean> accept)
	{
		List<Set<Center>> groups = new ArrayList<>();
		// Contains all explored centers in this graph. This prevents me from
		// making a new group
		// for every center.
		Set<Center> explored = new HashSet<>();
		for (Center center : graph.centers)
		{
			if (accept.apply(center) && !explored.contains(center))
			{
				// Do a breadth-first-search from that center, creating a new
				// group.
				// "frontier" maps centers to their distance from a center of
				// the desired biome.
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
	 * Draws an icon onto a map image with proper blending of background textures using content and shading masks.
	 *
	 * This method composites an icon with land and ocean textures based on the icon's masks, ensuring that
	 * transparent areas of the icon show the appropriate background (land or ocean), and that the icon blends
	 * naturally with coastline shading. The content mask defines which pixels are part of the icon's content,
	 * and the shading mask controls how background textures blend with the icon.
	 *
	 * @param mapOrSnippet
	 * 		The target image to draw onto (either a full map or a snippet). Modified in place.
	 * @param imageAndMasks
	 * 		Container holding the icon image, content mask, and shading mask. The content mask defines the icon's
	 * 		solid areas, while the shading mask controls texture blending.
	 * @param landBackground
	 * 		The background image for land areas (without icons). Must be the same dimensions as mapOrSnippet.
	 * @param landTexture
	 * 		The texture image to use for land areas. Must be the same dimensions as mapOrSnippet.
	 * @param oceanTexture
	 * 		The texture image to use for ocean areas. Must be the same dimensions as mapOrSnippet.
	 * @param type
	 * 		The type of icon being drawn (affects whether ocean texture is used for decorations).
	 * @param xCenter
	 * 		The x-coordinate of the icon's center in mapOrSnippet coordinate space.
	 * @param yCenter
	 * 		The y-coordinate of the icon's center in mapOrSnippet coordinate space.
	 * @param graphXCenter
	 * 		The x-coordinate of the icon's center in the full graph coordinate space (used for water detection).
	 * @param graphYCenter
	 * 		The y-coordinate of the icon's center in the full graph coordinate space (used for water detection).
	 * @throws IllegalArgumentException
	 * 		If mapOrSnippet, landBackground, landTexture, or oceanTexture have mismatched dimensions, or if
	 * 		the content mask or shading mask dimensions don't match the icon dimensions.
	 */
	private void drawIconWithBackgroundAndMasks(Image mapOrSnippet, ImageAndMasks imageAndMasks, Image landBackground, Image landTexture, Image oceanTexture, IconType type, int xCenter, int yCenter,
			int graphXCenter, int graphYCenter)
	{
		Image icon = imageAndMasks.image;
		Image contentMask = imageAndMasks.getOrCreateContentMask();

		if (mapOrSnippet.getWidth() != landBackground.getWidth())
			throw new IllegalArgumentException();
		if (mapOrSnippet.getHeight() != landBackground.getHeight())
			throw new IllegalArgumentException();
		if (contentMask.getWidth() != icon.getWidth())
			throw new IllegalArgumentException("The given content mask's width does not match the icon's width.");
		if (contentMask.getHeight() != icon.getHeight())
			throw new IllegalArgumentException("The given content mask's height does not match the icon's height.");
		Image shadingMask = imageAndMasks.getOrCreateShadingMask();
		if (shadingMask.getWidth() != icon.getWidth())
		{
			throw new IllegalArgumentException("The given shading mask's width does not match the icon's width.");
		}
		if (shadingMask.getHeight() != icon.getHeight())
		{
			throw new IllegalArgumentException("The given shading mask's height does not match the icon's height.");
		}

		int xLeft = xCenter - icon.getWidth() / 2;
		int yTop = yCenter - icon.getHeight() / 2;

		int graphXLeft = graphXCenter - icon.getWidth() / 2;
		int graphYTop = graphYCenter - icon.getHeight() / 2;

		IntDimension mapOrSnippetSize = mapOrSnippet.size();
		IntRectangle iconBoundsInMapOrSnippet = new IntRectangle(xLeft, yTop, icon.getWidth(), icon.getHeight());

		// Begin pixel sessions for efficient read/write
		try (PixelReader landTexturePixels = landTexture.createPixelReader(iconBoundsInMapOrSnippet);
				PixelReader oceanTexturePixels = oceanTexture.createPixelReader(iconBoundsInMapOrSnippet);
				PixelReader landBackgroundPixels = landBackground.createPixelReader(iconBoundsInMapOrSnippet);
				PixelReader contentMaskPixels = contentMask.createPixelReader();
				PixelReader shadingMaskPixels = shadingMask.createPixelReader();
				PixelReaderWriter mapOrSnippetPixels = mapOrSnippet.createPixelReaderWriter(iconBoundsInMapOrSnippet))
		{
			for (int y : new Range(icon.getHeight()))
			{
				for (int x = 0; x < icon.getWidth(); x++)
				{
					// grey level of mask at the corresponding pixel in mask.
					float contentMaskLevel = contentMaskPixels.getNormalizedPixelLevel(x, y);
					float shadingMaskLevel = shadingMaskPixels.getNormalizedPixelLevel(x, y);
					Color bgColorNoIcons;
					Color mapColor;
					Color landTextureColor;
					// Find the location on the background and map where this pixel
					// will be drawn.
					int xLoc = xLeft + x;
					int yLoc = yTop + y;
					if (xLoc < 0 || xLoc >= mapOrSnippetSize.width)
					{
						continue;
					}
					if (yLoc < 0 || yLoc >= mapOrSnippetSize.height)
					{
						continue;
					}

					Center closest = graph.findClosestCenter(new Point(graphXLeft + x, graphYTop + y), true);
					if (closest == null)
					{
						// The pixel isn't on the map.
						continue;
					}

					if (type == IconType.decorations)
					{
						bgColorNoIcons = closest.isWater ? Color.create(oceanTexturePixels.getRGB(xLoc, yLoc), oceanTexture.hasAlpha())
								: Color.create(landBackgroundPixels.getRGB(xLoc, yLoc), landBackground.hasAlpha());

						landTextureColor = closest.isWater ? Color.create(oceanTexturePixels.getRGB(xLoc, yLoc), oceanTexture.hasAlpha())
								: Color.create(landBackgroundPixels.getRGB(xLoc, yLoc), landBackground.hasAlpha());
					}
					else
					{
						bgColorNoIcons = Color.create(landBackgroundPixels.getRGB(xLoc, yLoc), landBackground.hasAlpha());

						landTextureColor = Color.create(landTexturePixels.getRGB(xLoc, yLoc), landTexture.hasAlpha());
					}

					mapColor = Color.create(mapOrSnippetPixels.getRGB(xLoc, yLoc), mapOrSnippet.hasAlpha());

					// Use the shading mask to blend the coastline shading with the land background texture for pixels with transparency in
					// the
					// icon and non-zero values in the content mask. This way coastline shading doesn't draw through icons, since that would
					// look weird when the icon extends over the coastline. It also makes the transparent pixels in the content of the icon
					// draw
					// the land background texture when the shading mask is white, so that icons extending into the ocean draw the land
					// texture
					// behind them rather than the ocean texture.
					int red = (int) (Helper.linearCombo(contentMaskLevel, Helper.linearCombo(shadingMaskLevel, bgColorNoIcons.getRed(), landTextureColor.getRed()), mapColor.getRed()));
					int green = (int) (Helper.linearCombo(contentMaskLevel, Helper.linearCombo(shadingMaskLevel, bgColorNoIcons.getGreen(), landTextureColor.getGreen()), mapColor.getGreen()));
					int blue = (int) (Helper.linearCombo(contentMaskLevel, Helper.linearCombo(shadingMaskLevel, bgColorNoIcons.getBlue(), landTextureColor.getBlue()), mapColor.getBlue()));
					int alpha = (int) (Helper.linearCombo(contentMaskLevel, (Helper.linearCombo(shadingMaskLevel, bgColorNoIcons.getAlpha(), landTextureColor.getAlpha())), mapColor.getAlpha()));
					mapOrSnippetPixels.setRGB(xLoc, yLoc, red, green, blue, alpha);
				}
			}
		}

		try (Painter p = mapOrSnippet.createPainter())
		{
			p.drawImage(imageAndMasks.image, xLeft, yTop);
		}
	}

	public List<IconDrawTask> getTasksInDrawBoundsSortedAndScaled(Rectangle drawBounds)
	{
		List<IconDrawTask> tasks = new ArrayList<IconDrawTask>(iconsToDraw.size());
		for (IconDrawTask task : iconsToDraw)
		{
			if (drawBounds == null || task.overlaps(drawBounds))
			{
				tasks.add(task);
			}
		}
		Collections.sort(tasks);

		// Force mask creation now if it hasn't already happened so that multiple threads don't try to create the same masks at the
		// same time and end up repeating work or create a race condition that corrupts the masks.
		for (final IconDrawTask task : tasks)
		{
			task.unScaledImageAndMasks.getOrCreateContentMask();
			task.unScaledImageAndMasks.getOrCreateShadingMask();
			if (task.fillWithColor && task.fillColor.getAlpha() > 0)
			{
				task.unScaledImageAndMasks.getOrCreateColorMask();
			}
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
						task.colorAndScaleIcon();
					}
				});
			}
			ThreadHelper.getInstance().processInParallel(jobs, true);
		}

		return tasks;
	}

	/**
	 * Draws all icons in tasksToDrawSorted. This assumes getTasksInDrawBoundsSorted was called to create tasksToDrawSorted.
	 * 
	 * I draw all the icons at once this way so that I can draw them sorted by the y-coordinate of the base of each icon. This way icons
	 * lower on the map are drawn in front of those that are higher.
	 * 
	 */
	public void drawIcons(List<IconDrawTask> tasksToDrawSorted, Image mapOrSnippet, Image landBackground, Image landTexture, Image oceanWithWavesAndShading, Rectangle drawBounds)
	{
		int xToSubtract = drawBounds == null ? 0 : (int) drawBounds.x;
		int yToSubtract = drawBounds == null ? 0 : (int) drawBounds.y;

		for (final IconDrawTask task : tasksToDrawSorted)
		{
			drawIconWithBackgroundAndMasks(mapOrSnippet, task.scaledImageAndMasks, landBackground, landTexture, oceanWithWavesAndShading, task.type, ((int) task.centerLoc.x) - xToSubtract,
					((int) task.centerLoc.y) - yToSubtract, (int) task.centerLoc.x, (int) task.centerLoc.y);
		}
	}

	/**
	 * Draws content masks on top of the land mask so that icons that protrude over coastlines don't turn into ocean when text is drawn on
	 * top of them.
	 */
	public void drawNondecorationContentMasksOntoLandMask(Image landMask, List<IconDrawTask> tasks, Rectangle drawBounds)
	{
		for (final IconDrawTask task : tasks)
		{
			// Skip decorations because the texture behind a decoration on the ocean should be ocean, as opposed to a land-based icon
			// like mountains.
			if (task.type != IconType.decorations && (drawBounds == null || task.overlaps(drawBounds)))
			{
				int xLoc = (int) task.centerLoc.x - task.scaledSize.width / 2;
				int yLoc = (int) task.centerLoc.y - task.scaledSize.height / 2;

				int xToSubtract = drawBounds == null ? 0 : (int) drawBounds.x;
				int yToSubtract = drawBounds == null ? 0 : (int) drawBounds.y;

				ImageHelper.drawIfPixelValueIsGreaterThanTarget(landMask, task.scaledImageAndMasks.getOrCreateContentMask(), xLoc - xToSubtract, yLoc - yToSubtract);
			}
		}
	}

	private boolean isNeighborACity(Center center)
	{
		return center.neighbors.stream().anyMatch(c -> c.isCity);
	}

	public Tuple2<List<Set<Center>>, List<IconDrawTask>> addIcons(List<Set<Center>> mountainAndHillGroups, WarningLogger warningLogger)
	{
		return freeIcons.doWithLockAndReturnResult(() ->
		{
			Tuple2<List<Set<Center>>, List<IconDrawTask>> result = new Tuple2<>(null, null);
			List<IconDrawTask> cities;

			Logger.println("Adding mountains and hills.");
			List<Set<Center>> mountainGroups;
			addOrUnmarkMountainsAndHills(mountainAndHillGroups);
			// I find the mountain groups after adding or unmarking mountains so
			// that mountains that get unmarked because their image
			// couldn't draw
			// don't later get labels.
			mountainGroups = findMountainGroups();
			result.setFirst(mountainGroups);

			Logger.println("Adding sand dunes.");
			addSandDunes();

			Logger.println("Adding trees.");
			addTrees();

			Logger.println("Adding cities.");
			cities = addOrUnmarkCities();
			result.setSecond(cities);

			createDrawTasksForFreeIconsAndRemovedFailedIcons(warningLogger);
			return result;
		});
	}

	/**
	 * Adds icon draw tasks to draw cities. Side effect: if a city is placed where it cannot be drawn, this will un-mark it as a city.
	 * 
	 * @return IconDrawTask of each city icon added. Needed to avoid drawing text on top of cities.
	 */
	public List<IconDrawTask> addOrUnmarkCities()
	{
		String artPackForCities;
		String cityTypeToUse;
		if (StringUtils.isEmpty(cityIconTypeForNewMaps) || ImageCache.getInstance(artPackForNewMap, customImagesPath).getIconsByNameForGroup(IconType.cities, cityIconTypeForNewMaps).isEmpty())
		{
			artPackForCities = Assets.installedArtPack;
			List<String> cityIconTypes = ImageCache.getInstance(artPackForCities, customImagesPath).getIconGroupNames(IconType.cities);
			if (cityIconTypes.size() > 0)
			{
				cityTypeToUse = ProbabilityHelper.sampleUniform(rand, new ArrayList<>(cityIconTypes));
			}
			else
			{
				// Should never happen since there are installed cities.
				Logger.println("The selected art pack, '" + artPackForNewMap + "', has no cities for the city type '" + cityIconTypeForNewMaps + ". There are also no cities in the "
						+ Assets.installedArtPack + " art pack, so none will be drawn.");
				return new ArrayList<>(0);
			}

			Logger.println("The selected art pack, '" + artPackForNewMap + "', has no cities for the city type '" + cityIconTypeForNewMaps + "'. Cities from the '" + Assets.installedArtPack
					+ "' art pack will be used instead.");
		}
		else
		{
			artPackForCities = artPackForNewMap;
			cityTypeToUse = cityIconTypeForNewMaps;
		}

		Map<String, ImageAndMasks> cityIcons = ImageCache.getInstance(artPackForCities, customImagesPath).getIconsByNameForGroup(IconType.cities, cityTypeToUse);
		if (cityIcons.isEmpty())
		{
			Logger.println("Cities will not be drawn because there are no city icons of type '" + cityTypeToUse + "'.");
			return new ArrayList<>(0);
		}

		List<String> cityNames = new ArrayList<>(cityIcons.keySet());

		List<IconDrawTask> cities = new ArrayList<>();

		for (Center c : graph.centers)
		{
			if (c.isCity)
			{
				String cityName = cityNames.get(rand.nextInt(cityNames.size()));
				FreeIcon icon = new FreeIcon(resolutionScale, c.loc, 1.0, IconType.cities, artPackForCities, cityTypeToUse, cityName, c.index, fillColorsByType.get(IconType.cities),
						iconFilterColorsByType.get(IconType.cities), maximizeOpacityByType.get(IconType.cities), fillWithColorByType.get(IconType.cities));
				IconDrawTask task = toIconDrawTask(icon);
				if (!isContentBottomTouchingWater(icon) && !isNeighborACity(c))
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

	/**
	 * Creates tasks for drawing mountains and hills.
	 * 
	 * @return
	 */
	public void addOrUnmarkMountainsAndHills(List<Set<Center>> mountainAndHillGroups)
	{
		String artPackForMountains;
		if (ImageCache.getInstance(artPackForNewMap, customImagesPath).getIconGroupsAsListsForType(IconType.mountains).isEmpty())
		{
			Logger.println("The selected art pack, '" + artPackForNewMap + "', has no mountains. Mountains from the '" + Assets.installedArtPack + "' art pack will be used instead.");
			artPackForMountains = Assets.installedArtPack;
		}
		else
		{
			artPackForMountains = artPackForNewMap;
		}

		// Maps mountain range ids (the ids in the file names) to list of
		// mountain images and their masks.
		ListMap<String, ImageAndMasks> mountainImagesById = ImageCache.getInstance(artPackForMountains, customImagesPath).getIconGroupsAsListsForType(IconType.mountains);

		if (mountainImagesById.isEmpty())
		{
			Logger.println("No mountains or hills will be added because there are no mountain images.");
		}

		String artPackForHills;
		if (ImageCache.getInstance(artPackForNewMap, customImagesPath).getIconGroupsAsListsForType(IconType.hills).isEmpty())
		{
			Logger.println("The selected art pack, '" + artPackForNewMap + "', has no hills. Hills from the '" + Assets.installedArtPack + "' art pack will be used instead.");
			artPackForHills = Assets.installedArtPack;
		}
		else
		{
			artPackForHills = artPackForNewMap;
		}

		// Maps mountain range ids (the ids in the file names) to list of hill
		// images and their masks.
		// The hill image file names must use the same ids as the mountain
		// ranges.
		ListMap<String, ImageAndMasks> hillImagesById = ImageCache.getInstance(artPackForHills, customImagesPath).getIconGroupsAsListsForType(IconType.hills);

		if (hillImagesById.isEmpty() && !mountainImagesById.isEmpty())
		{
			Logger.println("No hills will be added because there are no hill images.");
		}

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
				Logger.println("No hill images found for the mountain group \"" + mountainGroupId + "\". That mountain group will not have hills.");
			}
		}

		// Maps from the mountainRangeId of Centers to the range id's from the
		// mountain image file names.
		Map<Integer, String> rangeMap = new TreeMap<>();

		for (Set<Center> group : mountainAndHillGroups)
		{
			for (Center c : group)
			{
				String fileNameRangeId = rangeMap.get(c.mountainRangeId);
				if (fileNameRangeId == null && !mountainImagesById.isEmpty())
				{
					fileNameRangeId = new ArrayList<>(mountainImagesById.keySet()).get(rand.nextInt(mountainImagesById.keySet().size()));
					rangeMap.put(c.mountainRangeId, fileNameRangeId);
				}

				if (c.isMountain)
				{
					if (mountainImagesById.isEmpty())
					{
						c.isMountain = false;
					}
					else
					{
						// I'm deliberately putting this line before checking
						// center size so that the
						// random number generator is used the same no matter
						// what resolution the map
						// is drawn at.
						int i = Math.abs(rand.nextInt());

						double scale = getWidthScaleForNewShuffledIcon(c, IconType.mountains);
						Point loc = getAnchoredMountainDrawPoint(c, fileNameRangeId, i, mountainScale, mountainImagesById);

						FreeIcon icon = new FreeIcon(resolutionScale, loc, scale, IconType.mountains, artPackForMountains, fileNameRangeId, i, c.index, fillColorsByType.get(IconType.mountains),
								iconFilterColorsByType.get(IconType.mountains), maximizeOpacityByType.get(IconType.mountains), fillWithColorByType.get(IconType.mountains));

						IconDrawTask task = toIconDrawTask(icon);

						if (!isContentBottomTouchingWater(task))
						{
							freeIcons.addOrReplace(icon);
						}
						else
						{
							c.isMountain = false;
						}
					}
				}
				else if (c.isHill)
				{
					if (fileNameRangeId == null || hillImagesById.isEmpty())
					{
						c.isHill = false;
					}
					else
					{
						List<ImageAndMasks> imagesInGroup = hillImagesById.get(fileNameRangeId);

						if (imagesInGroup != null && !imagesInGroup.isEmpty())
						{
							// I'm deliberately putting this line before
							// checking center size so that the
							// random number generator is used the same no
							// matter what resolution the map
							// is drawn at.
							int i = Math.abs(rand.nextInt());

							double scale = getWidthScaleForNewShuffledIcon(c, IconType.hills);
							FreeIcon icon = new FreeIcon(resolutionScale, c.loc, scale, IconType.hills, artPackForHills, fileNameRangeId, i, c.index, fillColorsByType.get(IconType.hills),
									iconFilterColorsByType.get(IconType.hills), maximizeOpacityByType.get(IconType.hills), fillWithColorByType.get(IconType.hills));

							IconDrawTask task = toIconDrawTask(icon);

							if (!isContentBottomTouchingWater(task))
							{
								freeIcons.addOrReplace(icon);
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

	public void addSandDunes()
	{
		String artPackForDunes;
		if (ImageCache.getInstance(artPackForNewMap, customImagesPath).getIconGroupsAsListsForType(IconType.sand).isEmpty())
		{
			Logger.println("The selected art pack, '" + artPackForNewMap + "', has no sand dune images. Sand dunes from the '" + Assets.installedArtPack + "' art pack will be used instead.");
			artPackForDunes = Assets.installedArtPack;
		}
		else
		{
			artPackForDunes = artPackForNewMap;
		}

		ListMap<String, ImageAndMasks> sandGroups = ImageCache.getInstance(artPackForDunes, customImagesPath).getIconGroupsAsListsForType(IconType.sand);
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
				return center.biome.equals(sandDunesBiome);
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

						int i = Math.abs(rand.nextInt());
						FreeIcon icon = new FreeIcon(resolutionScale, c.loc, 1.0, IconType.sand, artPackForDunes, groupId, i, c.index, fillColorsByType.get(IconType.sand),
								iconFilterColorsByType.get(IconType.sand), maximizeOpacityByType.get(IconType.sand), fillWithColorByType.get(IconType.sand));
						if (!isContentBottomTouchingWater(icon))
						{
							freeIcons.addOrReplace(icon);
						}
					}
				}
			}
		}
	}

	public void addTrees()
	{
		String artPackForTrees;
		if (ImageCache.getInstance(artPackForNewMap, customImagesPath).getIconGroupsAsListsForType(IconType.sand).isEmpty())
		{
			Logger.println("The selected art pack, '" + artPackForNewMap + "', has no trees. Trees from the '" + Assets.installedArtPack + "' art pack will be used instead.");
			artPackForTrees = Assets.installedArtPack;
		}
		else
		{
			artPackForTrees = artPackForNewMap;
		}

		Map<Integer, CenterTrees> treesByCenter = new HashMap<>();

		for (final ForestType forest : forestTypes)
		{
			if (forest.biomeFrequency != 1.0)
			{
				String iconGroupId = getGroupIdForForestType(artPackForTrees, forest);
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
								treesByCenter.put(c.index, new CenterTrees(artPackForTrees, iconGroupId, forest.density, c.treeSeed));
							}
						}
					}
				}
			}
		}

		// Process forest types that don't use biome groups separately for
		// efficiency.
		for (Center c : graph.centers)
		{
			for (ForestType forest : forestTypes)
			{
				if (forest.biomeFrequency == 1.0)
				{
					String iconGroupId = getGroupIdForForestType(artPackForTrees, forest);
					if (forest.biome.equals(c.biome))
					{
						if (canGenerateTreesOnCenter(c))
						{
							treesByCenter.put(c.index, new CenterTrees(artPackForTrees, iconGroupId, forest.density, c.treeSeed));
						}
					}
				}

			}
		}

		convertTreesToFreeIcons(treesByCenter, new LoggerWarningLogger());
	}

	private static List<ForestType> forestTypes;
	static
	{
		forestTypes = new ArrayList<>();
		forestTypes.add(new ForestType(TreeType.Deciduous, Biome.TEMPERATE_RAIN_FOREST, 0.5, 1.0));
		forestTypes.add(new ForestType(TreeType.Pine, Biome.TAIGA, 1.0, 1.0));
		forestTypes.add(new ForestType(TreeType.Pine, Biome.SHRUBLAND, 1.0, 1.0));
		forestTypes.add(new ForestType(TreeType.Pine, Biome.HIGH_TEMPERATE_DECIDUOUS_FOREST, 1.0, 0.25));
		forestTypes.add(new ForestType(TreeType.Cacti, Biome.HIGH_TEMPERATE_DESERT, 1.0 / 16.0, 0.25));
		forestTypes.add(new ForestType(TreeType.Cacti, Biome.TEMPERATE_DESERT, 1.0 / 16.0, 0.25));
	}

	private boolean canGenerateTreesOnCenter(Center c)
	{
		return c.elevation < mountainElevationThreshold && !c.isWater && !c.isCoast;
	}

	private Rectangle convertTreesFromCenterEditsToFreeIcons(Collection<Center> centersToConvert, MapEdits edits, WarningLogger warningLogger)
	{
		if (edits.centerEdits.isEmpty())
		{
			return null;
		}

		Map<Integer, CenterTrees> treesByCenter = new HashMap<>();
		for (Center center : centersToConvert)
		{
			CenterTrees cTrees = edits.centerEdits.get(center.index).trees;
			if (cTrees != null)
			{
				CenterTrees toUse = replaceTreeAssetsIfNeeded(cTrees, warningLogger);
				if (toUse != cTrees)
				{
					edits.centerEdits.put(center.index, edits.centerEdits.get(center.index).copyWithTrees(toUse));
				}
				treesByCenter.put(center.index, toUse);
			}
		}

		Rectangle changeBounds = convertTreesToFreeIcons(treesByCenter, warningLogger);

		for (int index : treesByCenter.keySet())
		{
			if (edits.centerEdits.get(index).trees != null)
			{
				if (edits.freeIcons.hasTrees(index))
				{
					edits.centerEdits.put(index, edits.centerEdits.get(index).copyWithTrees(null));
				}
				else
				{
					edits.centerEdits.put(index, edits.centerEdits.get(index).copyWithTrees(edits.centerEdits.get(index).trees.copyWithIsDormant(true)));
				}
			}
		}

		return changeBounds;
	}

	private CenterTrees replaceTreeAssetsIfNeeded(CenterTrees cTrees, WarningLogger warningLogger)
	{
		if (cTrees == null)
		{
			return null;
		}

		String artPackToUse = chooseNewArtPackIfNeeded(IconType.trees, cTrees.artPack, cTrees.treeType, null, warningLogger, cTrees.isDormant);
		if (!cTrees.artPack.equals(artPackToUse))
		{
			cTrees = cTrees.copyWithArtPack(artPackToUse);
		}

		// Load the images and masks.
		ListMap<String, ImageAndMasks> treesById = ImageCache.getInstance(cTrees.artPack, customImagesPath).getIconGroupsAsListsForType(IconType.trees);
		if (treesById == null || treesById.isEmpty())
		{
			return cTrees;
		}

		final String groupId = getNewGroupIdIfNeeded(cTrees.treeType, IconType.trees, cTrees.artPack, treesById, warningLogger, cTrees.isDormant);
		if (groupId == null || !treesById.containsKey(groupId) || treesById.get(groupId).size() == 0)
		{
			// Skip since there are no tree images to use.
			return cTrees;
		}

		return cTrees.copyWithTreeType(groupId);
	}

	private Rectangle convertTreesToFreeIcons(Map<Integer, CenterTrees> treesByCenter, WarningLogger warningLogger)
	{
		Rectangle changeBounds = null;

		for (Entry<Integer, CenterTrees> entry : treesByCenter.entrySet())
		{
			CenterTrees cTrees = entry.getValue();
			if (cTrees != null && !cTrees.isDormant)
			{
				// This shouldn't log any warnings because replaceTreeAssetsIfNeeded has already been called on CenterTrees in
				// treesByCenter,
				// or this call is coming from drawing new trees.
				CenterTrees toUse = replaceTreeAssetsIfNeeded(cTrees, warningLogger);

				Center c = graph.centers.get(entry.getKey());
				changeBounds = Rectangle.add(changeBounds, drawTreesAtCenterAndCorners(graph, c, toUse, treesByCenter.keySet()));
			}
		}
		return changeBounds;
	}

	private double calcTreeDensityScale()
	{
		// The purpose of the number below is to make it so that adjusting the
		// height of trees also adjusts the density so that the spacing
		// between trees remains
		// looking about the same. As for how I calculated this number, the
		// minimum treeHeightScale is 0.1, and each tick on the tree height
		// slider increases by 0.05,
		// with the highest possible value being 0.85. So I then fitted a curve
		// to (0.1, 12), (0.35, 2), (0.5, 1.0), (0.65, 0.6) and (0.85,
		// 0.3).
		// The first point is the minimum tree height. The second is the
		// default. The third is the old default. The fourth is the maximum.
		return 2.0 * ((71.5152) * (treeHeightScale * treeHeightScale * treeHeightScale * treeHeightScale) - 178.061 * (treeHeightScale * treeHeightScale * treeHeightScale)
				+ 164.876 * (treeHeightScale * treeHeightScale) - 68.633 * treeHeightScale + 11.3855);

	}

	private Rectangle drawTreesAtCenterAndCorners(WorldGraph graph, Center center, CenterTrees cTrees, Set<Integer> additionalCentersThatWillHaveTrees)
	{
		Rectangle changeBounds = getAnchoredTreeIconBoundsAt(center.index);
		freeIcons.clearTrees(center.index);

		List<ImageAndMasks> unscaledImages = ImageCache.getInstance(cTrees.artPack, customImagesPath).getIconsInGroup(IconType.trees, cTrees.treeType);
		Random rand = new Random(cTrees.randomSeed);
		addTreeNearLocation(graph, unscaledImages, center.loc, cTrees.density, center, rand, cTrees.artPack, cTrees.treeType);

		// Draw trees at the neighboring corners too.
		// Note that corners use their own Random instance because the random
		// seed of that random needs to not depend on the center or else
		// trees would be placed differently based on which center drew first.
		for (Corner corner : center.corners)
		{
			if (shouldCenterDrawTreesForCorner(center, corner, additionalCentersThatWillHaveTrees))
			{
				addTreeNearLocation(graph, unscaledImages, corner.loc, cTrees.density, center, rand, cTrees.artPack, cTrees.treeType);
			}
		}

		changeBounds = Rectangle.add(changeBounds, getAnchoredTreeIconBoundsAt(center.index));
		return changeBounds;
	}

	/**
	 * Ensures at most 1 center draws trees at each corner.
	 */
	private boolean shouldCenterDrawTreesForCorner(Center center, Corner corner, Set<Integer> additionalCentersThatWillHaveTrees)
	{
		Center centerWithSmallestIndex = null;
		for (Center t : corner.touches)
		{
			boolean hasTrees = freeIcons.hasTrees(t.index) || (additionalCentersThatWillHaveTrees != null && additionalCentersThatWillHaveTrees.contains(t.index));
			if (!hasTrees)
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
		 * @param biomeFrequency
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

	private String getGroupIdForForestType(String artPack, ForestType forest)
	{
		List<String> groups = ImageCache.getInstance(artPack, customImagesPath).getIconGroupNames(IconType.trees);
		String keyWord = forest.treeType.toString().toLowerCase();

		if (groups == null || groups.isEmpty())
		{
			// No tree images.
			return keyWord;
		}

		// If there is a folder of tree images that with the exact name we want, then prefer that.
		if (groups.contains(keyWord))
		{
			return keyWord;
		}

		// Pick the first folder that contains the forest type name in the folder name.
		Optional<String> optional = groups.stream().filter(groupId -> groupId.contains(keyWord)).findFirst();
		if (optional.isPresent())
		{
			return optional.get();
		}

		// When all else fails, arbitrarily pick one of the tree types.
		return chooseNewGroupId(ImageCache.getInstance(artPack, customImagesPath).getIconGroupNames(IconType.trees), keyWord);
	}

	@SuppressWarnings("lossy-conversions")
	private void addTreeNearLocation(WorldGraph graph, List<ImageAndMasks> unscaledImages, Point loc, double forestDensity, Center center, Random rand, String artPack, String groupId)
	{
		// Convert the forestDensity into an integer number of trees to draw
		// such that the expected
		// value is forestDensity.
		double density = forestDensity * treeDensityScale;
		double fraction = density - (int) density;
		int extra = rand.nextDouble() < fraction ? 1 : 0;
		int numTrees = ((int) density) + extra;

		for (int i = 0; i < numTrees; i++)
		{
			int index = Math.abs(rand.nextInt());

			// Draw the image such that it is centered in the center of c.
			int x = (int) (loc.x);
			int y = (int) (loc.y);

			final double scale = ((meanPolygonWidth * 2.0) / 10.0);
			x += rand.nextGaussian() * scale;
			y += rand.nextGaussian() * scale;

			FreeIcon icon = new FreeIcon(resolutionScale, new Point(x, y), 1.0, IconType.trees, artPack, groupId, index, center.index, forestDensity, fillColorsByType.get(IconType.trees),
					iconFilterColorsByType.get(IconType.trees), maximizeOpacityByType.get(IconType.trees), fillWithColorByType.get(IconType.trees));

			if (!isContentBottomTouchingWater(icon))
			{
				freeIcons.addOrReplace(icon);
			}
		}
	}

	public boolean isContentBottomTouchingWater(FreeIcon icon)
	{
		return isContentBottomTouchingWater(toIconDrawTask(icon));
	}

	public IconDrawTask toIconDrawTask(FreeIcon icon)
	{
		return toIconDrawTask(icon, getTypeLevelScale(icon.type));
	}

	private IconDrawTask toIconDrawTask(FreeIcon icon, double typeLevelScale)
	{
		if (!Assets.artPackExists(icon.artPack, customImagesPath))
		{
			return null;
		}

		ImageAndMasks imageAndMasks;
		if (icon.type == IconType.cities || icon.type == IconType.decorations)
		{
			Map<String, ImageAndMasks> imagesInGroup = ImageCache.getInstance(icon.artPack, customImagesPath).getIconsByNameForGroup(icon.type, icon.groupId);

			if (imagesInGroup == null || imagesInGroup.isEmpty())
			{
				return null;
			}

			if (!imagesInGroup.containsKey(icon.iconName) || imagesInGroup.get(icon.iconName) == null)
			{
				return null;
			}

			imageAndMasks = imagesInGroup.get(icon.iconName);
		}
		else
		{
			List<ImageAndMasks> imagesInGroup = ImageCache.getInstance(icon.artPack, customImagesPath).getIconsInGroup(icon.type, icon.groupId);

			if (imagesInGroup == null || imagesInGroup.isEmpty())
			{
				return null;
			}

			imageAndMasks = imagesInGroup.get(icon.iconIndex % imagesInGroup.size());
		}

		return icon.toIconDrawTask(customImagesPath, resolutionScale, typeLevelScale, getBaseWidth(icon.type, imageAndMasks));
	}

	private boolean isContentBottomTouchingWater(IconDrawTask iconTask)
	{
		if (iconTask == null)
		{
			return false;
		}

		if (iconTask.unScaledImageAndMasks.getOrCreateContentMask().getType() != ImageType.Binary)
			throw new IllegalArgumentException("Mask type must be TYPE_BYTE_BINARY for checking whether icons touch water.");

		final int imageUpperLeftX = (int) iconTask.centerLoc.x - iconTask.scaledSize.width / 2;
		final int imageUpperLeftY = (int) iconTask.centerLoc.y - iconTask.scaledSize.height / 2;

		Rectangle scaledContentBounds;
		{
			IntRectangle contentBounds = iconTask.unScaledImageAndMasks.getOrCreateContentBounds();
			if (contentBounds == null)
			{
				// The icon is fully transparent.
				return false;
			}

			final double xScaleToScaledIconSpace = iconTask.scaledSize.width / (double) iconTask.unScaledImageAndMasks.getOrCreateContentMask().getWidth();
			final double yScaleToScaledIconSpace = iconTask.scaledSize.height / (double) iconTask.unScaledImageAndMasks.getOrCreateContentMask().getHeight();

			scaledContentBounds = new Rectangle(contentBounds.x * xScaleToScaledIconSpace, contentBounds.y * yScaleToScaledIconSpace, contentBounds.width * xScaleToScaledIconSpace,
					contentBounds.height * yScaleToScaledIconSpace);
		}

		// The constant in this number is in number of pixels at 100%
		// resolution. I include the resolution here
		// so that the loop below will make the same number of steps
		// (approximately) no matter the resolution.
		// This is to reduce the chances that icons will appear or disappear
		// when you draw the map at a different resolution.
		final double stepSize = 2.0 * resolutionScale;

		final double xScaleToMaskSpace = ((double) iconTask.unScaledImageAndMasks.getOrCreateContentMask().getWidth()) / iconTask.scaledSize.width;
		final double yScaleToMaskSpace = ((double) iconTask.unScaledImageAndMasks.getOrCreateContentMask().getHeight()) / iconTask.scaledSize.height;

		for (double x = scaledContentBounds.x; x < scaledContentBounds.x + scaledContentBounds.width; x += stepSize)
		{
			int xInMask = (int) (x * xScaleToMaskSpace);
			Integer yInMask = iconTask.unScaledImageAndMasks.getContentYStart(xInMask);
			if (yInMask == null)
			{
				continue;
			}
			int y = (int) (yInMask * (1.0 / yScaleToMaskSpace));

			Center center = graph.findClosestCenter(new Point(imageUpperLeftX + x, imageUpperLeftY + y), true);
			if (center != null && center.isWater)
			{
				return true;
			}
		}

		return false;
	}
}
