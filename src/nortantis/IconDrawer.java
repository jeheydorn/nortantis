package nortantis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;

import nortantis.editor.CenterEdit;
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
import nortantis.util.Assets;
import nortantis.util.Function;
import nortantis.util.Helper;
import nortantis.util.ImageHelper;
import nortantis.util.ListMap;
import nortantis.util.Logger;
import nortantis.util.ProbabilityHelper;
import nortantis.util.Range;
import nortantis.util.ThreadHelper;
import nortantis.util.Tuple2;
import nortantis.util.Tuple3;

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
	private Map<IconType, Color> iconColorsByType;

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
		maxSizeToDrawGeneratedMountainOrHill = averageCenterWidthBetweenNeighbors
				* maxAverageCenterWidthsBetweenNeighborsToDrawGeneratedMountainOrHill;
		iconColorsByType = settings.copyIconColorsByType();
	}

	public void markMountains()
	{
		for (Center c : graph.centers)
		{
			if (c.elevation > mountainElevationThreshold && !c.isBorder
					&& graph.findCenterWidthBetweenNeighbors(c) < maxSizeToDrawGeneratedMountainOrHill)
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
					&& graph.findCenterWidthBetweenNeighbors(c) < maxSizeToDrawGeneratedMountainOrHill)

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
					ListMap<String, ImageAndMasks> mountainImagesById = ImageCache.getInstance(cEdit.icon.artPack, customImagesPath)
							.getIconGroupsAsListsForType(IconType.mountains);
					changeBounds = Rectangle.add(changeBounds,
							convertWidthBasedShuffledAnchoredIcon(edits, center, cEdit, mountainImagesById, warningLogger));
				}
				else if (cEdit.icon.iconType == CenterIconType.Hill)
				{
					ListMap<String, ImageAndMasks> hillImagesById = ImageCache.getInstance(cEdit.icon.artPack, customImagesPath)
							.getIconGroupsAsListsForType(IconType.hills);
					changeBounds = Rectangle.add(changeBounds,
							convertWidthBasedShuffledAnchoredIcon(edits, center, cEdit, hillImagesById, warningLogger));
				}
				else if (cEdit.icon.iconType == CenterIconType.Dune)
				{
					ListMap<String, ImageAndMasks> duneImages = ImageCache.getInstance(cEdit.icon.artPack, customImagesPath)
							.getIconGroupsAsListsForType(IconType.sand);
					changeBounds = Rectangle.add(changeBounds,
							convertWidthBasedShuffledAnchoredIcon(edits, center, cEdit, duneImages, warningLogger));
				}
				else if (cEdit.icon.iconType == CenterIconType.City)
				{
					Tuple3<String, String, String> artPackAndGroupAndName = adjustNamedIconGroupAndNameIfNeeded(IconType.cities,
							cEdit.icon.artPack, cEdit.icon.iconGroupId, cEdit.icon.iconName, warningLogger);

					if (artPackAndGroupAndName != null)
					{
						String artPack = artPackAndGroupAndName.getFirst();
						String groupId = artPackAndGroupAndName.getSecond();
						String name = artPackAndGroupAndName.getThird();

						IconType type = centerIconTypeToIconType(cEdit.icon.iconType);
						FreeIcon icon = new FreeIcon(resolutionScale, center.loc, 1.0, type, artPack, groupId, name, cEdit.index,
								iconColorsByType.get(type));
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

	private Rectangle convertWidthBasedShuffledAnchoredIcon(MapEdits edits, Center center, CenterEdit cEdit,
			ListMap<String, ImageAndMasks> iconsByGroup, WarningLogger warningLogger)
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
		FreeIcon icon = new FreeIcon(resolutionScale, loc, scale, type, cEdit.icon.artPack, groupId, cEdit.icon.iconIndex, cEdit.index,
				iconColorsByType.get(type));
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

	public Point getAnchoredMountainDrawPoint(Center center, String groupId, int iconIndex, double mountainScale,
			ListMap<String, ImageAndMasks> iconsByGroup)
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
		return new Point(c.loc.x,
				bottom.loc.y - (scaledHeight / 2) - getOffsetFromCenterBottomToPutBottomOfMountainImageAt(c.findHeight()));
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
		double prevScaledHeightWithoutIconScale = getDimensionsWhenScaledByWidth(image.size(),
				getBaseWidth(IconType.mountains, imageAndMasks) * mountainScale).height;
		double newScaledHeightWithoutIconScale = getDimensionsWhenScaledByWidth(image.size(),
				getBaseWidth(IconType.mountains, imageAndMasks) * newMountainScale).height;
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
			return Rectangle.add(conversionBoundsOfIconsChanged, removedOrReplacedChangeBounds);
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
				updateGroupIdAndAddShuffledFreeIcon(icon, mountainScale, warningLogger, toRemove);
			}
			else if (icon.type == IconType.hills)
			{
				updateGroupIdAndAddShuffledFreeIcon(icon, hillScale, warningLogger, toRemove);
			}
			else if (icon.type == IconType.sand)
			{
				updateGroupIdAndAddShuffledFreeIcon(icon, duneScale, warningLogger, toRemove);
			}
			else if (icon.type == IconType.cities)
			{
				Tuple3<String, String, String> artPackAndGroupAndName = adjustNamedIconGroupAndNameIfNeeded(icon.type, icon.artPack,
						icon.groupId, icon.iconName, warningLogger);
				if (artPackAndGroupAndName != null)
				{
					FreeIcon updated = icon.copyWith(artPackAndGroupAndName.getFirst(), artPackAndGroupAndName.getSecond(),
							artPackAndGroupAndName.getThird(), icon.color);

					IconDrawTask task = toIconDrawTask(updated);
					if (!isContentBottomTouchingWater(task))
					{
						if (!icon.equals(updated))
						{
							freeIcons.replace(icon, updated);
						}
						iconsToDraw.add(toIconDrawTask(updated));
					}
					else
					{
						toRemove.add(icon);
					}
				}
				else
				{
					toRemove.add(icon);
				}
			}
			else if (icon.type == IconType.decorations)
			{
				Tuple3<String, String, String> artPackAndGroupAndName = adjustNamedIconGroupAndNameIfNeeded(icon.type, icon.artPack,
						icon.groupId, icon.iconName, warningLogger);
				if (artPackAndGroupAndName != null)
				{
					FreeIcon updated = icon.copyWith(artPackAndGroupAndName.getFirst(), artPackAndGroupAndName.getSecond(),
							artPackAndGroupAndName.getThird(), icon.color);

					IconDrawTask task = toIconDrawTask(updated);

					// Remove the icon if it is entirely off the map.
					if (task != null && graph.bounds.overlaps(task.createBounds()))
					{
						if (!icon.equals(updated))
						{
							freeIcons.replace(icon, updated);
						}
						iconsToDraw.add(toIconDrawTask(updated));
					}
					else
					{
						toRemove.add(icon);
					}
				}
				else
				{
					toRemove.add(icon);
				}
			}
			else if (icon.type == IconType.trees)
			{
				updateGroupIdAndAddShuffledFreeIcon(icon, treeHeightScale, warningLogger, toRemove);
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

	private Tuple3<String, String, String> adjustNamedIconGroupAndNameIfNeeded(IconType type, String artPack, String groupId, String name,
			WarningLogger warningLogger)
	{
		String artPackToUse = chooseNewArtPackIfNeeded(type, artPack, groupId, name, warningLogger, false);

		Map<String, ImageAndMasks> imagesInGroup = ImageCache.getInstance(artPackToUse, customImagesPath).getIconsByNameForGroup(type,
				groupId);
		if (imagesInGroup == null || imagesInGroup.isEmpty())
		{
			String newGroupId = chooseNewGroupId(ImageCache.getInstance(artPackToUse, customImagesPath).getIconGroupNames(type), groupId);
			if (newGroupId == null)
			{
				warningLogger.addWarningMessage("Unable to find the " + type.getSingularName() + " image group '" + groupId
						+ "' in art pack '" + artPack + "'. There are no " + type.getSingularName() + " icons, so none will be drawn.");
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
			warningLogger.addWarningMessage("Unable to find the " + type.getSingularName() + " image group '" + groupId + "' in art pack '"
					+ artPack + "'. The group '" + newGroupId + "' in art pack '" + artPackToUse + "' will be used instead.");
			groupId = newGroupId;
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
				warningLogger.addWarningMessage("Unable to find the " + type.getSingularName() + " icon '" + oldName + "' in art pack '"
						+ artPack + "'. The icon '" + name + "' in art pack '" + artPackToUse + "' will be used instead.");
			}
		}

		return new Tuple3<>(artPackToUse, groupId, name);
	}

	private void updateGroupIdAndAddShuffledFreeIcon(FreeIcon icon, double typeLevelScale, WarningLogger warningLogger,
			List<FreeIcon> toRemove)
	{
		String artPackToUse = chooseNewArtPackIfNeeded(icon.type, icon.artPack, icon.groupId, icon.iconName, warningLogger, false);
		if (!icon.artPack.equals(artPackToUse))
		{
			FreeIcon updated = icon.copyWithArtPack(artPackToUse);
			freeIcons.replace(icon, updated);
			icon = updated;
		}

		ListMap<String, ImageAndMasks> iconsByGroup = ImageCache.getInstance(icon.artPack, customImagesPath)
				.getIconGroupsAsListsForType(icon.type);
		String newGroupId = getNewGroupIdIfNeeded(icon.groupId, icon.type, artPackToUse, iconsByGroup, warningLogger, false);
		if (!icon.groupId.equals(newGroupId) && newGroupId != null)
		{
			FreeIcon updated = icon.copyWithGroupId(newGroupId);
			freeIcons.replace(icon, updated);
			icon = updated;
		}

		if (icon.groupId != null && !icon.groupId.isEmpty() && iconsByGroup.get(icon.groupId) != null
				&& iconsByGroup.get(icon.groupId).size() > 0)
		{
			IconDrawTask task = toIconDrawTask(icon);
			if (!isContentBottomTouchingWater(task))
			{
				iconsToDraw.add(task);
			}
			else
			{
				toRemove.add(icon);
			}
		}
		else
		{
			toRemove.add(icon);
		}
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

	private String getNewGroupIdIfNeeded(final String groupId, IconType type, String artPack, ListMap<String, ImageAndMasks> iconsByGroup,
			WarningLogger warningLogger, boolean isForDormantTrees)
	{

		String dormantTreesMessage = isForDormantTrees
				? " These trees are not visible because they were drawn at low density, but may become visible if you change the tree height in the Effects tab."
				: "";

		if (!iconsByGroup.containsKey(groupId))
		{
			// Someone removed the icon group. Choose a new group.
			String newGroupId = chooseNewGroupId(iconsByGroup.keySet(), groupId);
			if (newGroupId == null)
			{
				warningLogger.addWarningMessage(
						"Unable to find the " + type.getSingularName() + " image group '" + groupId + "' in art pack '" + artPack
								+ "'. There are no " + type.getSingularName() + " icons in that art pack, so none will be drawn.");
			}
			else
			{
				warningLogger.addWarningMessage(
						"Unable to find the " + type.getSingularName() + " image group '" + groupId + "' in art pack '" + artPack
								+ "'. The group '" + newGroupId + "' in that art pack will be used instead." + dormantTreesMessage);
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

	private String chooseNewGroupId(Collection<String> groupIds, String oldGroupId)
	{
		if (groupIds.isEmpty())
		{
			return null;
		}
		int index = Math.abs(oldGroupId.hashCode() % groupIds.size());
		return groupIds.toArray(new String[groupIds.size()])[index];
	}

	private String chooseNewArtPackIfNeeded(IconType type, String oldArtPack, String oldGroupId, String oldIconName,
			WarningLogger warningLogger, boolean isForDormantTrees)
	{
		String dormantTreesMessage = isForDormantTrees
				? " These trees are not visible because they were drawn at low density, but may become visible if you change the tree height in the Effects tab."
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
						warningLogger.addWarningMessage("Unable to find the art pack '" + oldArtPack + "' to load the "
								+ type.getSingularName() + " image group '" + oldGroupId + "'. The art pack '" + artPack
								+ "' will be used instead because it has the same image group folder name." + dormantTreesMessage);
						return artPack;
					}
				}
				else
				{
					if (ImageCache.getInstance(artPack, customImagesPath).hasNamedIcon(type, oldGroupId, oldIconName))
					{
						warningLogger.addWarningMessage("Unable to find the art pack '" + oldArtPack + "' to load the icon '" + oldIconName
								+ "' from " + type.getSingularName() + " image group '" + oldGroupId + "'. The art pack '" + artPack
								+ "' will be used instead because it has the same image group folder and image name.");
						return artPack;
					}
				}
			}

			int index = Math.abs(oldArtPack.hashCode() % allArtPacks.size());
			String artPackToUse = allArtPacks.get(index);

			if (StringUtils.isEmpty(oldIconName))
			{
				warningLogger.addWarningMessage(
						"Unable to find the art pack '" + oldArtPack + "' to load the " + type.getSingularName() + " image group '"
								+ oldGroupId + "'. The art pack '" + artPackToUse + "' will be used instead." + dormantTreesMessage);
			}
			else
			{
				warningLogger.addWarningMessage("Unable to find the art pack '" + oldArtPack + "' to load the icon '" + oldIconName
						+ "' from " + type.getSingularName() + " image group '" + oldGroupId + "'. The art pack '" + artPackToUse
						+ "' will be used instead.");
			}

			return allArtPacks.get(index);
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
						warningLogger.addWarningMessage("The art pack '" + oldArtPack + "' no longer has " + type.getSingularName()
								+ " images, so it does not have the " + type.getSingularName() + " image group '" + oldGroupId
								+ "'. The art pack '" + artPack + "' will be used instead because it has the same image group folder name."
								+ dormantTreesMessage);
						return artPack;
					}
				}
				else
				{
					if (ImageCache.getInstance(artPack, customImagesPath).hasNamedIcon(type, oldGroupId, oldIconName))
					{
						warningLogger.addWarningMessage("The art pack '" + oldArtPack + "' no longer has " + type.getSingularName()
								+ " images, so it does not have the icon '" + oldIconName + "' from " + type.getSingularName()
								+ " image group '" + oldGroupId + "'. The art pack '" + artPack
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
						warningLogger.addWarningMessage("The art pack '" + oldArtPack + "' no longer has " + type.getSingularName()
								+ " images, so it does not have the " + type.getSingularName() + " image group '" + oldGroupId
								+ "'. The art pack '" + artPack + "' will be used instead because it has " + type.getSingularName()
								+ " images." + dormantTreesMessage);
						return artPack;
					}
					else
					{
						warningLogger.addWarningMessage("The art pack '" + oldArtPack + "' no longer has " + type.getSingularName()
								+ " images, so it does not have the icon '" + oldIconName + "' from " + type.getSingularName()
								+ " image group '" + oldGroupId + "'. The art pack '" + artPack + "' will be used instead because it has "
								+ type.getSingularName() + " images.");
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

	private void drawIconWithBackgroundAndMasks(Image mapOrSnippet, ImageAndMasks imageAndMasks, Image backgroundColoredBeforeAddingIcons,
			Image background, Image landTexture, Image oceanTexture, IconType type, int xCenter, int yCenter, int graphXCenter,
			int graphYCenter)
	{
		Image icon = imageAndMasks.image;
		Image contentMask = imageAndMasks.getOrCreateContentMask();

		if (mapOrSnippet.getWidth() != background.getWidth())
			throw new IllegalArgumentException();
		if (mapOrSnippet.getHeight() != background.getHeight())
			throw new IllegalArgumentException();
		if (backgroundColoredBeforeAddingIcons != null)
		{
			if (mapOrSnippet.getWidth() != backgroundColoredBeforeAddingIcons.getWidth())
				throw new IllegalArgumentException();
			if (mapOrSnippet.getHeight() != backgroundColoredBeforeAddingIcons.getHeight())
				throw new IllegalArgumentException();
		}
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

		for (int y : new Range(icon.getHeight()))
		{
			for (int x = 0; x < icon.getWidth(); x++)
			{
				Color iconColor = Color.create(icon.getRGB(x, y), true);
				int iconAlphaInt = iconColor.getAlpha();
				double iconAlpha = iconAlphaInt / 255.0;
				// grey level of mask at the corresponding pixel in mask.
				float contentMaskLevel = contentMask.getNormalizedPixelLevel(x, y);
				float shadingMaskLevel = shadingMask.getNormalizedPixelLevel(x, y);
				Color bgColor;
				Color bgColorNoIcons;
				Color mapColor;
				Color landTextureColor;
				// Find the location on the background and map where this pixel
				// will be drawn.
				int xLoc = xLeft + x;
				int yLoc = yTop + y;
				try
				{
					Center closest = graph.findClosestCenter(new Point(graphXLeft + x, graphYTop + y), true);
					if (closest == null)
					{
						// The pixel isn't on the map.
						continue;
					}

					if (type == IconType.decorations)
					{
						bgColor = closest.isWater ? Color.create(oceanTexture.getRGB(xLoc, yLoc), oceanTexture.hasAlpha())
								: Color.create(background.getRGB(xLoc, yLoc), background.hasAlpha());
						if (backgroundColoredBeforeAddingIcons != null)
						{
							bgColorNoIcons = closest.isWater ? Color.create(oceanTexture.getRGB(xLoc, yLoc), oceanTexture.hasAlpha())
									: Color.create(backgroundColoredBeforeAddingIcons.getRGB(xLoc, yLoc),
											backgroundColoredBeforeAddingIcons.hasAlpha());
						}
						else
						{
							bgColorNoIcons = bgColor;
						}
						landTextureColor = closest.isWater ? Color.create(oceanTexture.getRGB(xLoc, yLoc), oceanTexture.hasAlpha())
								: Color.create(background.getRGB(xLoc, yLoc), background.hasAlpha());
					}
					else
					{
						bgColor = Color.create(background.getRGB(xLoc, yLoc), background.hasAlpha());
						if (backgroundColoredBeforeAddingIcons != null)
						{
							bgColorNoIcons = Color.create(backgroundColoredBeforeAddingIcons.getRGB(xLoc, yLoc),
									backgroundColoredBeforeAddingIcons.hasAlpha());
						}
						else
						{
							bgColorNoIcons = bgColor;
						}
						landTextureColor = Color.create(landTexture.getRGB(xLoc, yLoc), landTexture.hasAlpha());
					}

					mapColor = Color.create(mapOrSnippet.getRGB(xLoc, yLoc), mapOrSnippet.hasAlpha());

				}
				catch (IndexOutOfBoundsException e)
				{
					// Skip this pixel.
					continue;
				}

				double bgColorAlpha = bgColor.getAlpha() / 255.0;
				double backgroundColorScale;
				if (bgColorAlpha == 1.0)
				{
					// Save some time since this is a simple and common case.
					backgroundColorScale = 1.0;
				}
				else
				{
					// Use a curve that is 0 when landTextureAlpha is 0, 1 when landTextureAlpha is 1, and is mostly equal to 1 but dies off
					// quickly as landTextureAlpha reaches 0. That way when the land color is transparent, it doesn't mix with icon pixels
					// that are partially transparent.
					backgroundColorScale = 1.0 - Math.pow(1.0 - bgColorAlpha, 10);
				}

				double landTextureAlpha = landTextureColor.getAlpha() / 255.0;
				double landBackgroundColorScale;
				if (landTextureAlpha == 1.0)
				{
					// Save some time since this is a simple and common case.
					landBackgroundColorScale = 1.0;
				}
				else
				{
					// Use a curve that is 0 when landTextureAlpha is 0, 1 when landTextureAlpha is 1, and is mostly equal to 1 but dies off
					// quickly as landTextureAlpha reaches 0. That way when the land color is transparent, it doesn't mix with icon pixels
					// that are partially transparent.
					landBackgroundColorScale = 1.0 - Math.pow(1.0 - landTextureAlpha, 10);
				}

				double mapAlpha = mapColor.getAlpha() / 255.0;
				double mapColorScale;
				if (mapAlpha == 1.0)
				{
					// Save some time since this is a simple and common case.
					mapColorScale = 1.0;
				}
				else
				{
					// Use a curve that is 0 when mapAlpha is 0, 1 when mapAlpha is 1, and is mostly equal to 1 but dies off
					// quickly as mapAlpha reaches 0. That way when the land color is transparent, it doesn't mix with icon pixels
					// that are partially transparent.
					mapColorScale = 1.0 - Math.pow(1.0 - mapAlpha, 50);
				}

				// Use the shading mask to blend the coastline shading with the land background texture for pixels with transparency in the
				// icon and non-zero values in the content mask. This way coastline shading doesn't draw through icons, since that would
				// look weird when the icon extends over the coastline. It also makes the transparent pixels in the content of the icon draw
				// the land background texture when the shading mask is white, so that icons extending into the ocean draw the land texture
				// behind them rather than the ocean texture.
				int red = (int) (Helper.linearCombo(iconAlpha, iconColor.getRed(), Helper.linearCombo(
						contentMaskLevel, Helper
								.linearCombo(shadingMaskLevel, backgroundColorScale * bgColorNoIcons.getRed(),
										Helper.linearCombo(shadingMaskLevel, backgroundColorScale * bgColor.getRed(),
												landBackgroundColorScale * landTextureColor.getRed())),
						mapColorScale * mapColor.getRed())));
				int green = (int) (Helper.linearCombo(iconAlpha, iconColor.getGreen(),
						Helper.linearCombo(contentMaskLevel,
								backgroundColorScale * Helper.linearCombo(shadingMaskLevel, bgColorNoIcons.getGreen(),
										Helper.linearCombo(shadingMaskLevel, bgColor.getGreen(),
												landBackgroundColorScale * landTextureColor.getGreen())),
								mapColorScale * mapColor.getGreen())));
				int blue = (int) (Helper.linearCombo(iconAlpha, iconColor.getBlue(),
						Helper.linearCombo(contentMaskLevel,
								backgroundColorScale * Helper.linearCombo(shadingMaskLevel, bgColorNoIcons.getBlue(),
										Helper.linearCombo(shadingMaskLevel, bgColor.getBlue(),
												landBackgroundColorScale * landTextureColor.getBlue())),
								mapColorScale * mapColor.getBlue())));
				int alpha = (int) (iconAlphaInt + (1.0 - iconAlpha) * (Helper.linearCombo(contentMaskLevel,
						(Helper.linearCombo(shadingMaskLevel, bgColor.getAlpha(), landTextureColor.getAlpha())), mapColor.getAlpha())));
				mapOrSnippet.setRGB(xLoc, yLoc, Color.create(red, green, blue, alpha).getRGB());
			}
		}
	}

	List<IconDrawTask> getTasksInDrawBoundsSortedAndScaled(Rectangle drawBounds)
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

		// Force mask creation now if it hasn't already happened so that so that multiple threads don't try to create the same masks at the
		// same time and end up repeating work or create a race condition that corrupts the masks.
		for (final IconDrawTask task : tasks)
		{
			task.unScaledImageAndMasks.getOrCreateContentMask();
			task.unScaledImageAndMasks.getOrCreateShadingMask();
			if (task.color.getAlpha() > 0)
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
	public void drawIcons(List<IconDrawTask> tasksToDrawSorted, Image mapOrSnippet, Image backgroundColoredBeforeAddingIcons,
			Image background, Image landTexture, Image oceanWithWavesAndShading, Rectangle drawBounds)
	{
		int xToSubtract = drawBounds == null ? 0 : (int) drawBounds.x;
		int yToSubtract = drawBounds == null ? 0 : (int) drawBounds.y;

		for (final IconDrawTask task : tasksToDrawSorted)
		{
			drawIconWithBackgroundAndMasks(mapOrSnippet, task.scaledImageAndMasks, backgroundColoredBeforeAddingIcons, background,
					landTexture, oceanWithWavesAndShading, task.type, ((int) task.centerLoc.x) - xToSubtract,
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

				ImageHelper.drawIfPixelValueIsGreaterThanTarget(landMask, task.scaledImageAndMasks.getOrCreateContentMask(),
						xLoc - xToSubtract, yLoc - yToSubtract);
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
		if (StringUtils.isEmpty(cityIconTypeForNewMaps) || ImageCache.getInstance(artPackForNewMap, customImagesPath)
				.getIconsByNameForGroup(IconType.cities, cityIconTypeForNewMaps).isEmpty())
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
				Logger.println(
						"The selected art pack, '" + artPackForNewMap + "', has no cities for the city type '" + cityIconTypeForNewMaps
								+ ". There are also no cities in the " + Assets.installedArtPack + " art pack, so none will be drawn.");
				return new ArrayList<>(0);
			}

			Logger.println("The selected art pack, '" + artPackForNewMap + "', has no cities for the city type '" + cityIconTypeForNewMaps
					+ "'. Cities from the '" + Assets.installedArtPack + "' art pack will be used instead.");
		}
		else
		{
			artPackForCities = artPackForNewMap;
			cityTypeToUse = cityIconTypeForNewMaps;
		}

		Map<String, ImageAndMasks> cityIcons = ImageCache.getInstance(artPackForCities, customImagesPath)
				.getIconsByNameForGroup(IconType.cities, cityTypeToUse);
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
				FreeIcon icon = new FreeIcon(resolutionScale, c.loc, 1.0, IconType.cities, artPackForCities, cityTypeToUse, cityName,
						c.index, iconColorsByType.get(IconType.cities));
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
			Logger.println("The selected art pack, '" + artPackForNewMap + "', has no mountains. Mountains from the '"
					+ Assets.installedArtPack + "' art pack will be used instead.");
			artPackForMountains = Assets.installedArtPack;
		}
		else
		{
			artPackForMountains = artPackForNewMap;
		}

		// Maps mountain range ids (the ids in the file names) to list of
		// mountain images and their masks.
		ListMap<String, ImageAndMasks> mountainImagesById = ImageCache.getInstance(artPackForMountains, customImagesPath)
				.getIconGroupsAsListsForType(IconType.mountains);

		if (mountainImagesById.isEmpty())
		{
			Logger.println("No mountains or hills will be added because there are no mountain images.");
		}

		String artPackForHills;
		if (ImageCache.getInstance(artPackForNewMap, customImagesPath).getIconGroupsAsListsForType(IconType.hills).isEmpty())
		{
			Logger.println("The selected art pack, '" + artPackForNewMap + "', has no hills. Hills from the '" + Assets.installedArtPack
					+ "' art pack will be used instead.");
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
		ListMap<String, ImageAndMasks> hillImagesById = ImageCache.getInstance(artPackForHills, customImagesPath)
				.getIconGroupsAsListsForType(IconType.hills);

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
				Logger.println("No hill images found for the mountain group \"" + mountainGroupId
						+ "\". That mountain group will not have hills.");
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

						FreeIcon icon = new FreeIcon(resolutionScale, loc, scale, IconType.mountains, artPackForMountains, fileNameRangeId,
								i, c.index, iconColorsByType.get(IconType.mountains));

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
							FreeIcon icon = new FreeIcon(resolutionScale, c.loc, scale, IconType.hills, artPackForHills, fileNameRangeId, i,
									c.index, iconColorsByType.get(IconType.hills));

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
			Logger.println("The selected art pack, '" + artPackForNewMap + "', has no sand dune images. Sand dunes from the '"
					+ Assets.installedArtPack + "' art pack will be used instead.");
			artPackForDunes = Assets.installedArtPack;
		}
		else
		{
			artPackForDunes = artPackForNewMap;
		}

		ListMap<String, ImageAndMasks> sandGroups = ImageCache.getInstance(artPackForDunes, customImagesPath)
				.getIconGroupsAsListsForType(IconType.sand);
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
						FreeIcon icon = new FreeIcon(resolutionScale, c.loc, 1.0, IconType.sand, artPackForDunes, groupId, i, c.index,
								iconColorsByType.get(IconType.sand));
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
			Logger.println("The selected art pack, '" + artPackForNewMap + "', has no trees. Trees from the '" + Assets.installedArtPack
					+ "' art pack will be used instead.");
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

	private Rectangle convertTreesFromCenterEditsToFreeIcons(Collection<Center> centersToConvert, MapEdits edits,
			WarningLogger warningLogger)
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
					edits.centerEdits.put(index,
							edits.centerEdits.get(index).copyWithTrees(edits.centerEdits.get(index).trees.copyWithIsDormant(true)));
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

		String artPackToUse = chooseNewArtPackIfNeeded(IconType.trees, cTrees.artPack, cTrees.treeType, null, warningLogger,
				cTrees.isDormant);
		if (!cTrees.artPack.equals(artPackToUse))
		{
			cTrees = cTrees.copyWithArtPack(artPackToUse);
		}

		// Load the images and masks.
		ListMap<String, ImageAndMasks> treesById = ImageCache.getInstance(cTrees.artPack, customImagesPath)
				.getIconGroupsAsListsForType(IconType.trees);
		if (treesById == null || treesById.isEmpty())
		{
			return cTrees;
		}

		final String groupId = getNewGroupIdIfNeeded(cTrees.treeType, IconType.trees, cTrees.artPack, treesById, warningLogger,
				cTrees.isDormant);
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
		return 2.0 * ((71.5152) * (treeHeightScale * treeHeightScale * treeHeightScale * treeHeightScale)
				- 178.061 * (treeHeightScale * treeHeightScale * treeHeightScale) + 164.876 * (treeHeightScale * treeHeightScale)
				- 68.633 * treeHeightScale + 11.3855);

	}

	private Rectangle drawTreesAtCenterAndCorners(WorldGraph graph, Center center, CenterTrees cTrees,
			Set<Integer> additionalCentersThatWillHaveTrees)
	{
		Rectangle changeBounds = getAnchoredTreeIconBoundsAt(center.index);
		freeIcons.clearTrees(center.index);

		List<ImageAndMasks> unscaledImages = ImageCache.getInstance(cTrees.artPack, customImagesPath).getIconsInGroup(IconType.trees,
				cTrees.treeType);
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
			boolean hasTrees = freeIcons.hasTrees(t.index)
					|| (additionalCentersThatWillHaveTrees != null && additionalCentersThatWillHaveTrees.contains(t.index));
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

	private void addTreeNearLocation(WorldGraph graph, List<ImageAndMasks> unscaledImages, Point loc, double forestDensity, Center center,
			Random rand, String artPack, String groupId)
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

			FreeIcon icon = new FreeIcon(resolutionScale, new Point(x, y), 1.0, IconType.trees, artPack, groupId, index, center.index,
					forestDensity, iconColorsByType.get(IconType.trees));

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
			Map<String, ImageAndMasks> imagesInGroup = ImageCache.getInstance(icon.artPack, customImagesPath)
					.getIconsByNameForGroup(icon.type, icon.groupId);

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
			List<ImageAndMasks> imagesInGroup = ImageCache.getInstance(icon.artPack, customImagesPath).getIconsInGroup(icon.type,
					icon.groupId);

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

			final double xScaleToScaledIconSpace = iconTask.scaledSize.width
					/ (double) iconTask.unScaledImageAndMasks.getOrCreateContentMask().getWidth();
			final double yScaleToScaledIconSpace = iconTask.scaledSize.height
					/ (double) iconTask.unScaledImageAndMasks.getOrCreateContentMask().getHeight();

			scaledContentBounds = new Rectangle(contentBounds.x * xScaleToScaledIconSpace, contentBounds.y * yScaleToScaledIconSpace,
					contentBounds.width * xScaleToScaledIconSpace, contentBounds.height * yScaleToScaledIconSpace);
		}

		// The constant in this number is in number of pixels at 100%
		// resolution. I include the resolution here
		// so that the loop below will make the same number of steps
		// (approximately) no matter the resolution.
		// This is to reduce the chances that icons will appear or disappear
		// when you draw the map at a different resolution.
		final double stepSize = 2.0 * resolutionScale;

		final double xScaleToMaskSpace = ((double) iconTask.unScaledImageAndMasks.getOrCreateContentMask().getWidth())
				/ iconTask.scaledSize.width;
		final double yScaleToMaskSpace = ((double) iconTask.unScaledImageAndMasks.getOrCreateContentMask().getHeight())
				/ iconTask.scaledSize.height;

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
