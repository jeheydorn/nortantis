package nortantis;

import nortantis.editor.*;
import nortantis.geom.*;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.platform.*;
import nortantis.platform.awt.AwtFactory;
import nortantis.swing.MapEdits;
import nortantis.swing.translation.Translation;
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
	// them.
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
	/**
	 * City icons that were removed from the most recent full icon pass because their bottom landed on water, in encounter order with
	 * duplicates kept (so callers can report both how many cities were lost and which icons they used). Populated by
	 * {@link #createDrawTasksForFreeIconsAndRemovedFailedIcons}; read via {@link #getCitiesRemovedForTouchingWater()}.
	 */
	private final List<CityIconRemovedForWater> citiesRemovedForTouchingWater = new ArrayList<>();
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
	private boolean isLowMemoryMode;

	public void setLowMemoryMode(boolean isLowMemoryMode)
	{
		this.isLowMemoryMode = isLowMemoryMode;
	}

	/**
	 * Returns the colors to draw an icon with: {@code override} when it is non-null (a {@link CenterIcon}/{@link CenterTrees} that
	 * remembers its own colors, e.g. a sub-map-redistributed or dormant icon), otherwise the map's per-type colors for {@code type} (the
	 * normal case for generated and freshly edited icons).
	 */
	private IconColors resolveIconColors(IconColors override, IconType type)
	{
		if (override != null)
		{
			return override;
		}
		return new IconColors(fillColorsByType.get(type), iconFilterColorsByType.get(type), Boolean.TRUE.equals(maximizeOpacityByType.get(type)), Boolean.TRUE.equals(fillWithColorByType.get(type)));
	}

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
		fillColorsByType = settings.copyIconFillColorsByType();
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
		List<Set<Center>> mountainGroups = findCenterGroups(graph, maxGapSizeInMountainClusters, center -> center.isMountain);

		return mountainGroups;

	}

	/**
	 * Finds and marks mountain ranges, and groups smaller than ranges, and surrounding hills.
	 */
	public List<Set<Center>> findMountainAndHillGroups()
	{
		List<Set<Center>> mountainAndHillGroups = findCenterGroups(graph, maxGapSizeInMountainClusters, center -> center.isMountain || center.isHill);

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
						IconColors colors = resolveIconColors(cEdit.icon.colors, type);
						FreeIcon icon = new FreeIcon(resolutionScale, center.loc, 1.0, type, artPack, groupId, name, cEdit.index, colors.fillColor, colors.filterColor, colors.maximizeOpacity,
								colors.fillWithColor);
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
		IconColors colors = resolveIconColors(cEdit.icon.colors, type);
		FreeIcon icon = new FreeIcon(resolutionScale, loc, scale, type, cEdit.icon.artPack, groupId, cEdit.icon.iconIndex, cEdit.index, colors.fillColor, colors.filterColor, colors.maximizeOpacity,
				colors.fillWithColor);
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
		double scaledWidth = getBaseWidth(imageAndMasks) * scale;
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
		double prevScaledHeightWithoutIconScale = getDimensionsWhenScaledByWidth(image.size(), getBaseWidth(imageAndMasks) * mountainScale).height;
		double newScaledHeightWithoutIconScale = getDimensionsWhenScaledByWidth(image.size(), getBaseWidth(imageAndMasks) * newMountainScale).height;
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
	public Rectangle addOrUpdateIconsFromEdits(MapEdits edits, Collection<Center> centersToUpdateIconsFor, Rectangle replaceBounds, WarningLogger warningLogger)
	{
		assert freeIcons == edits.freeIcons;

		return freeIcons.doWithLockAndReturnResult(() ->
		{
			Rectangle conversionBoundsOfIconsChanged = convertToFreeIconsIfNeeded(centersToUpdateIconsFor, edits, warningLogger);
			// Expand the filter bounds to include the converted icons' bounds so that nearby icons are included
			// in the draw tasks. Without this, when a converted icon (e.g. a tall mountain) extends beyond
			// replaceBounds, icons in the expanded region would be missing from the draw tasks and get erased
			// when the snippet is pasted over the expanded replaceBounds in incrementalUpdateForCentersAndEdges.
			//
			// Only filter when this is an incremental draw (replaceBounds != null). On a full draw replaceBounds is null and every free
			// icon
			// must be drawn; without this guard, when there are CenterIcons to convert (e.g. a redistributed sub-map's
			// mountains/hills/trees)
			// conversionBoundsOfIconsChanged is non-null, so filterBounds would become that bounding box and free icons outside it (such as
			// cities away from the converted terrain) would be wrongly skipped.
			Rectangle filterBounds = replaceBounds == null ? null : Rectangle.add(replaceBounds, conversionBoundsOfIconsChanged);
			Rectangle removedOrReplacedChangeBounds = createDrawTasksForFreeIconsAndRemovedFailedIcons(warningLogger, filterBounds);
			Rectangle combined = Rectangle.add(conversionBoundsOfIconsChanged, removedOrReplacedChangeBounds);
			if (combined == null)
			{
				return combined;
			}
			double paddingForIntegerTruncation = 4.0;
			return combined.pad(paddingForIntegerTruncation, paddingForIntegerTruncation);
		});
	}

	/**
	 * When trees are drawn at a low density, some places the user marked for trees produce no visible tree. To preserve the user's intended
	 * planting (so trees don't become sparse when shrunk or overly dense when grown), those places are kept as dormant {@link CenterTrees}.
	 * This rebuilds the anchored {@link CenterTrees} in {@code edits} from the current tree state so that, when redrawn, trees are
	 * replanted at the intended density and locations:
	 * <ul>
	 * <li>A {@code CenterTrees} whose center now has visible tree free icons is dropped (the visible trees take over).</li>
	 * <li>A {@code CenterTrees} with no visible trees of its own (dormant or failed-to-draw) is re-seeded as non-dormant if there is a
	 * visible tree within {@link #treeReplantVisibleTreeSearchDistance} centers (so it gets another chance to grow), or dropped otherwise
	 * (so it does not pop up far from any trees).</li>
	 * <li>Each center with visible tree free icons gets a fresh non-dormant {@code CenterTrees} carrying the most common tree type, the
	 * average density, and a representative color of those trees.</li>
	 * </ul>
	 * The {@code CenterTrees}' random seeds are drawn from {@code rand}, so pass a seeded {@link Random} when deterministic output is
	 * needed (e.g. sub-map creation). Mutates {@code edits.centerEdits}; {@code edits.freeIcons} is only read. Used both when the tree
	 * height changes in the theme panel and when a sub-map redistributes icons, so dormant trees are handled the same way in both.
	 */
	public static void rebuildAnchoredTrees(MapEdits edits, WorldGraph graph, Random rand)
	{
		edits.freeIcons.doWithLock(() ->
		{
			// Reassign the random seeds to all CenterTrees that still exist because they failed to create any visible trees, and mark them
			// not dormant so they try to draw again. Drop those that are not close to any visible tree so they don't randomly pop up.
			for (Map.Entry<Integer, CenterEdit> entry : edits.centerEdits.entrySet())
			{
				CenterTrees cTrees = entry.getValue().trees;
				if (cTrees == null)
				{
					continue;
				}
				if (edits.freeIcons.hasTrees(entry.getKey()))
				{
					// Visible trees override invisible ones.
					edits.centerEdits.put(entry.getKey(), entry.getValue().copyWithTrees(null));
				}
				else if (hasVisibleTreeWithinDistance(edits, graph, entry.getKey(), treeReplantVisibleTreeSearchDistance))
				{
					// Carry the dormant trees' remembered colors forward so they reappear with their original color rather than the current
					// per-type tree color.
					edits.centerEdits.put(entry.getKey(), entry.getValue().copyWithTrees(new CenterTrees(cTrees.artPack, cTrees.treeType, cTrees.density, rand.nextLong(), false, cTrees.colors)));
				}
				else
				{
					edits.centerEdits.put(entry.getKey(), entry.getValue().copyWithTrees(null));
				}
			}

			for (int centerIndex : edits.freeIcons.iterateTreeAnchors())
			{
				List<FreeIcon> trees = edits.freeIcons.getTrees(centerIndex);
				if (trees == null || trees.isEmpty())
				{
					continue;
				}

				Tuple2Comp<String, String> tuple = getMostCommonTreeType(trees);
				if (tuple == null)
				{
					// This shouldn't happen because we checked that trees was not null or empty.
					assert false;
					continue;
				}
				String artPack = tuple.getFirst();
				String treeType = tuple.getSecond();
				assert artPack != null;
				assert treeType != null;

				double density = trees.stream().mapToDouble(t -> t.density).average().getAsDouble();
				assert density > 0;

				// Carry the visible trees' colors onto the rebuilt CenterTrees so they keep their (possibly custom-edited) color instead of
				// snapping back to the current per-type tree color when reflowed.
				IconColors colors = getRepresentativeTreeColors(trees, artPack, treeType);
				CenterTrees cTrees = new CenterTrees(artPack, treeType, density, rand.nextLong(), false, colors);
				CenterEdit cEdit = edits.centerEdits.get(centerIndex);
				edits.centerEdits.put(centerIndex, cEdit.copyWithTrees(cTrees));
			}
		});
	}

	/**
	 * The maximum number of centers away a visible tree may be for a dormant/failed {@link CenterTrees} to be kept and replanted by
	 * {@link #rebuildAnchoredTrees}. Beyond this, the dormant trees are dropped so they don't pop up far from any visible trees.
	 */
	private static final int treeReplantVisibleTreeSearchDistance = 3;

	private static Tuple2Comp<String, String> getMostCommonTreeType(List<FreeIcon> trees)
	{
		Counter<Tuple2Comp<String, String>> counter = new ComparableCounter<>();
		trees.stream().forEach(tree -> counter.incrementCount(new Tuple2Comp<>(tree.artPack, tree.groupId)));
		return counter.argmax();
	}

	/**
	 * Returns the colors of a representative tree from {@code trees} (one matching the chosen {@code artPack}/{@code treeType} if possible,
	 * else the first), used to give a rebuilt {@link CenterTrees} the same colors as the visible trees it is re-anchoring.
	 */
	private static IconColors getRepresentativeTreeColors(List<FreeIcon> trees, String artPack, String treeType)
	{
		for (FreeIcon tree : trees)
		{
			if (Objects.equals(tree.artPack, artPack) && Objects.equals(tree.groupId, treeType))
			{
				return new IconColors(tree.fillColor, tree.filterColor, tree.maximizeOpacity, tree.fillWithColor);
			}
		}
		FreeIcon first = trees.get(0);
		return new IconColors(first.fillColor, first.filterColor, first.maximizeOpacity, first.fillWithColor);
	}

	private static boolean hasVisibleTreeWithinDistance(MapEdits edits, WorldGraph graph, int centerStartIndex, int maxSearchDistance)
	{
		Center start = graph.centers.get(centerStartIndex);
		Center found = graph.breadthFirstSearchForGoal((ignored1, ignored2, distanceFromStart) ->
		{
			return distanceFromStart < maxSearchDistance;
		}, (c) ->
		{
			return edits.freeIcons.hasTrees(c.index);
		}, start);

		return found != null;
	}

	private Rectangle createDrawTasksForFreeIconsAndRemovedFailedIcons(WarningLogger warningLogger, Rectangle replaceBounds)
	{
		iconsToDraw.clear();
		citiesRemovedForTouchingWater.clear();

		// In theory, it should be safe to just remove free icons as I iterate over the collection, but I'm leery of that because there are
		// multiple underlying iterators involved in looping over the collection, so I'm doing it afterward.
		List<FreeIcon> toRemove = new ArrayList<>();

		// Note: There's no need to update removeBounds in this loop for cases that replace an icon because removeBounds it is only needed
		// for incremental draws, and code for changing an icon because the previous icon did not exist will only be triggered during an
		// image refresh or an initial full draw, which are both full draws.
		for (FreeIcon icon : freeIcons)
		{
			if (icon == null)
			{
				continue;
			}

			if (icon.type == IconType.mountains)
			{
				checkAndAddIcon(icon, true, warningLogger, toRemove, replaceBounds);
			}
			else if (icon.type == IconType.hills)
			{
				checkAndAddIcon(icon, true, warningLogger, toRemove, replaceBounds);
			}
			else if (icon.type == IconType.sand)
			{
				checkAndAddIcon(icon, true, warningLogger, toRemove, replaceBounds);
			}
			else if (icon.type == IconType.cities)
			{
				checkAndAddIcon(icon, true, warningLogger, toRemove, replaceBounds);
			}
			else if (icon.type == IconType.decorations)
			{
				checkAndAddIcon(icon, false, warningLogger, toRemove, replaceBounds);
			}
			else if (icon.type == IconType.trees)
			{
				checkAndAddIcon(icon, true, warningLogger, toRemove, replaceBounds);
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

	/**
	 * Returns the city icons removed from the most recent full icon pass because they landed on water. The list is in encounter order and
	 * keeps duplicates, so its size is the number of cities lost and its distinct values are the icons involved.
	 */
	public List<CityIconRemovedForWater> getCitiesRemovedForTouchingWater()
	{
		return new ArrayList<>(citiesRemovedForTouchingWater);
	}

	/**
	 * A city icon that was dropped from a draw because its bottom landed on water. Carries the art pack, group, and clean file name
	 * (extension and encoded width/height/alpha parameters stripped) actually used to draw it, so callers can tell the user which cities
	 * disappeared. See {@link IconDrawer#getCitiesRemovedForTouchingWater()}.
	 */
	public static class CityIconRemovedForWater
	{
		public final String artPack;
		public final String groupId;
		public final String fileName;

		public CityIconRemovedForWater(String artPack, String groupId, String fileName)
		{
			this.artPack = artPack;
			this.groupId = groupId;
			this.fileName = fileName;
		}
	}

	private void checkAndAddIcon(FreeIcon icon, boolean checkContentBottomTouchingWater, WarningLogger warningLogger, List<FreeIcon> toRemove, Rectangle drawBounds)
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

		if (drawBounds != null && !drawBounds.overlaps(task.createBounds()))
		{
			// Skip this icon because this is an incremental draw that does not include this icon.
			return;
		}

		if (checkContentBottomTouchingWater && isContentBottomTouchingWater(task))
		{
			if (icon.type == IconType.cities && task.unScaledImageAndMasks != null)
			{
				// Record the lost city so the sub-map dialog can warn the user which cities near the shore disappeared onto water, with the
				// art pack, group, and clean file name actually used to draw it (taken from the image, so they reflect any asset replacement).
				ImageAndMasks imageAndMasks = task.unScaledImageAndMasks;
				citiesRemovedForTouchingWater
						.add(new CityIconRemovedForWater(imageAndMasks.artPack, imageAndMasks.groupId, imageAndMasks.fileNameWithoutParametersOrExtension));
			}
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

				FreeIcon updated = icon.copyWith(artPackAndGroupAndName.getFirst(), artPackAndGroupAndName.getSecond(), artPackAndGroupAndName.getThird(), icon.fillColor, icon.filterColor,
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
				warningLogger.addWarningMessage(Translation.get("warning.groupNotFound.noIcons", type.getSingularName(), groupId, artPack));
				return null;
			}
			imagesInGroup = ImageCache.getInstance(artPackToUse, customImagesPath).getIconsByNameForGroup(type, newGroupId);
			if (imagesInGroup == null || imagesInGroup.isEmpty())
			{
				// This shouldn't happen since the new group id shouldn't have
				// been an option if it were empty or null.
				assert false;
				return null;
			}
			warningLogger.addWarningMessage(Translation.get("warning.groupNotFound.replacement", type.getSingularName(), groupId, artPack, newGroupId, artPackToUse));
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
				List<String> names = new ArrayList<>(imagesInGroup.keySet());
				name = names.get(Helper.safeAbs(name.hashCode()) % names.size());
			}
			if (name != null)
			{
				warningLogger.addWarningMessage(Translation.get("warning.iconNotFound.replacement", type.getSingularName(), oldName, artPack, groupId, name, artPackToUse, newGroupId));
			}
		}

		return new Tuple3<>(artPackToUse, newGroupId, name);
	}

	private double getBaseWidth(ImageAndMasks imageAndMasks)
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
		throw new IllegalArgumentException("Unrecognized icon type for getting type-level scale: " + type);
	}

	private String getNewGroupIdIfNeeded(final String groupId, IconType type, String artPack, ListMap<String, ImageAndMasks> iconsByGroup, WarningLogger warningLogger, boolean isForDormantTrees)
	{

		String dormantTreesMessage = isForDormantTrees ? Translation.get("warning.dormantTrees") : "";

		if (!iconsByGroup.containsKey(groupId))
		{
			// Someone removed the icon group. Choose a new group.
			String newGroupId = chooseNewGroupId(iconsByGroup.keySet(), groupId);
			if (newGroupId == null)
			{
				warningLogger.addWarningMessage(Translation.get("warning.groupNotFound.noIconsInPack", type.getSingularName(), groupId, artPack));
			}
			else
			{
				warningLogger.addWarningMessage(Translation.get("warning.groupNotFound.replacementInPack", type.getSingularName(), groupId, artPack, newGroupId, dormantTreesMessage));
			}
			return newGroupId;
		}

		return groupId;
	}

	public static IconType centerIconTypeToIconType(CenterIconType type)
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

	public static CenterIconType iconTypeToCenterIconType(IconType type)
	{
		if (type == IconType.mountains)
			return CenterIconType.Mountain;
		if (type == IconType.hills)
			return CenterIconType.Hill;
		if (type == IconType.sand)
			return CenterIconType.Dune;
		throw new IllegalArgumentException("Cannot convert IconType '" + type + "' to a CenterIconType.");
	}

	public static String chooseNewCityIconName(Set<String> cityNamesToChooseFrom, String oldIconName)
	{
		List<CityType> oldTypes = NameCreator.findCityTypeFromCityFileName(oldIconName);
		List<String> compatibleCities = cityNamesToChooseFrom.stream().filter(name -> NameCreator.findCityTypeFromCityFileName(name).stream().anyMatch(type -> oldTypes.contains(type)))
				.collect(Collectors.toList());
		if (compatibleCities.isEmpty())
		{
			int index = Helper.safeAbs(oldIconName.hashCode()) % cityNamesToChooseFrom.size();
			return new ArrayList<>(cityNamesToChooseFrom).get(index);
		}
		int index = Helper.safeAbs(oldIconName.hashCode()) % compatibleCities.size();
		return compatibleCities.get(index);
	}

	private String chooseNewGroupId(Collection<String> groupIds, String oldGroupId)
	{
		if (groupIds.isEmpty())
		{
			return null;
		}
		int index = Helper.safeAbs(oldGroupId.hashCode()) % groupIds.size();
		return groupIds.toArray(new String[groupIds.size()])[index];
	}

	private String chooseNewArtPackIfNeeded(IconType type, String oldArtPack, String oldGroupId, String oldIconName, WarningLogger warningLogger, boolean isForDormantTrees)
	{
		String dormantTreesMessage = isForDormantTrees ? Translation.get("warning.dormantTrees") : "";

		List<String> allArtPacks = Assets.listArtPacks(!StringUtils.isEmpty(customImagesPath));
		if (!allArtPacks.contains(oldArtPack))
		{
			// Prefer an art pack that has an image with the same name and group name in hopes that it is the same art pack but renamed.
			for (String artPack : allArtPacks)
			{
				if (StringUtils.isEmpty(oldIconName))
				{
					if (ImageCache.getInstance(artPack, customImagesPath).hasGroupName(type, oldGroupId))
					{
						warningLogger.addWarningMessage(Translation.get("warning.artPackNotFound.sameGroup", oldArtPack, type.getSingularName(), oldGroupId, artPack, dormantTreesMessage));
						return artPack;
					}
				}
				else
				{
					if (ImageCache.getInstance(artPack, customImagesPath).hasNamedIcon(type, oldGroupId, oldIconName))
					{
						warningLogger.addWarningMessage(Translation.get("warning.artPackNotFound.sameIcon", oldArtPack, oldIconName, type.getSingularName(), oldGroupId, artPack));
						return artPack;
					}
				}
			}

			// Use the built-in art pack.
			String artPackToUse = Assets.installedArtPack;
			if (StringUtils.isEmpty(oldIconName))
			{
				warningLogger.addWarningMessage(Translation.get("warning.artPackNotFound.group", oldArtPack, type.getSingularName(), oldGroupId, artPackToUse, dormantTreesMessage));
			}
			else
			{
				warningLogger.addWarningMessage(Translation.get("warning.artPackNotFound.icon", oldArtPack, oldIconName, type.getSingularName(), oldGroupId, artPackToUse));
			}

			return artPackToUse;
		}
		else if (ImageCache.getInstance(oldArtPack, customImagesPath).getIconGroupNames(type).isEmpty())
		{
			// Prefer an art pack that has an image with the same name and group name in hopes that it is the same art pack but renamed.
			for (String artPack : allArtPacks)
			{
				if (StringUtils.isEmpty(oldIconName))
				{
					if (ImageCache.getInstance(artPack, customImagesPath).hasGroupName(type, oldGroupId))
					{
						warningLogger.addWarningMessage(Translation.get("warning.artPackNoImages.sameGroup", oldArtPack, type.getSingularName(), oldGroupId, artPack, dormantTreesMessage));
						return artPack;
					}
				}
				else
				{
					if (ImageCache.getInstance(artPack, customImagesPath).hasNamedIcon(type, oldGroupId, oldIconName))
					{
						warningLogger.addWarningMessage(Translation.get("warning.artPackNoImages.sameIcon", oldArtPack, type.getSingularName(), oldIconName, oldGroupId, artPack));
						return artPack;
					}
				}
			}

			// Prefer the installed art pack for this fallback when it has images of this type (it normally has every type). This keeps the
			// result stable and predictable rather than depending on the iteration order over the other packs.
			if (!ImageCache.getInstance(Assets.installedArtPack, customImagesPath).getIconGroupNames(type).isEmpty())
			{
				if (StringUtils.isEmpty(oldIconName))
				{
					warningLogger.addWarningMessage(Translation.get("warning.artPackNoImages.hasType.group", oldArtPack, type.getSingularName(), oldGroupId, Assets.installedArtPack, dormantTreesMessage));
				}
				else
				{
					warningLogger.addWarningMessage(Translation.get("warning.artPackNoImages.hasType.icon", oldArtPack, type.getSingularName(), oldIconName, oldGroupId, Assets.installedArtPack));
				}
				return Assets.installedArtPack;
			}

			// Otherwise take the first other art pack that has images of that type.
			for (String artPack : allArtPacks)
			{
				if (!ImageCache.getInstance(artPack, customImagesPath).getIconGroupNames(type).isEmpty())

					if (StringUtils.isEmpty(oldIconName))
					{
						warningLogger.addWarningMessage(Translation.get("warning.artPackNoImages.hasType.group", oldArtPack, type.getSingularName(), oldGroupId, artPack, dormantTreesMessage));
						return artPack;
					}
					else
					{
						warningLogger.addWarningMessage(Translation.get("warning.artPackNoImages.hasType.icon", oldArtPack, type.getSingularName(), oldIconName, oldGroupId, artPack));
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
	 * that other center. If distanceThreshold > 1, the result will include those centers which connect centers that are accepted.
	 */
	private static List<Set<Center>> findCenterGroups(WorldGraph graph, int maxGapSize, java.util.function.Predicate<Center> accept)
	{
		List<Set<Center>> groups = new ArrayList<>();
		// Contains all explored centers in this graph. This prevents me from
		// making a new group
		// for every center.
		Set<Center> explored = new HashSet<>();
		for (Center center : graph.centers)
		{
			if (accept.test(center) && !explored.contains(center))
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
								if (accept.test(n))
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
	 * This method composites an icon with land and ocean textures based on the icon's masks, ensuring that transparent areas of the icon
	 * show the appropriate background (land or ocean), and that the icon blends naturally with coastline shading. The content mask defines
	 * which pixels are part of the icon's content, and the shading mask controls how background textures blend with the icon.
	 *
	 * @param mapOrSnippet
	 *            The target image to draw onto (either a full map or a snippet). Modified in place.
	 * @param imageAndMasks
	 *            Container holding the icon image, content mask, and shading mask. The content mask defines the icon's solid areas, while
	 *            the shading mask controls texture blending.
	 * @param landBackground
	 *            The background image for land areas (without icons). Must be the same dimensions as mapOrSnippet.
	 * @param landTexture
	 *            The texture image to use for land areas. Must be the same dimensions as mapOrSnippet.
	 * @param oceanTexture
	 *            The texture image to use for ocean areas. Must be the same dimensions as mapOrSnippet.
	 * @param type
	 *            The type of icon being drawn (affects whether ocean texture is used for decorations).
	 * @param xCenter
	 *            The x-coordinate of the icon's center in mapOrSnippet coordinate space.
	 * @param yCenter
	 *            The y-coordinate of the icon's center in mapOrSnippet coordinate space.
	 * @param graphXCenter
	 *            The x-coordinate of the icon's center in the full graph coordinate space (used for water detection).
	 * @param graphYCenter
	 *            The y-coordinate of the icon's center in the full graph coordinate space (used for water detection).
	 * @throws IllegalArgumentException
	 *             If mapOrSnippet, landBackground, landTexture, or oceanTexture have mismatched dimensions, or if the content mask or
	 *             shading mask dimensions don't match the icon dimensions.
	 */
	private void drawIconWithBackgroundAndMasks(Image mapOrSnippet, ImageAndMasks imageAndMasks, Image landBackground, Image landTexture, Image oceanTexture, IconType type, int xCenter, int yCenter,
			int graphXCenter, int graphYCenter, PixelReader hoistedLandTexturePixels, PixelReader hoistedOceanTexturePixels, PixelReader hoistedLandBackgroundPixels,
			PixelReaderWriter hoistedMapPixels)
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

		// Use different code paths for AWT vs Skia because transparent land doesn't work yet with Skia. (And there may also be performance
		// disadvantages to having AWT go through the Skia code path, but I haven't checked that yet).
		if (PlatformFactory.getInstance() instanceof AwtFactory)
		{
			drawIconWithBackgroundAndMasksDirect(mapOrSnippet, imageAndMasks, landBackground, landTexture, oceanTexture, type, xLeft, yTop, graphXLeft, graphYTop, mapOrSnippetSize, icon, contentMask,
					shadingMask, hoistedLandTexturePixels, hoistedOceanTexturePixels, hoistedLandBackgroundPixels);
		}
		else if (hoistedMapPixels != null)
		{
			// High-memory non-AWT path: write straight through a map-wide PixelReaderWriter opened once for the whole drawIcons call, instead
			// of copying a snippet out and pasting it back per icon (which is a GPU readback + upload each time).
			drawIconWithBackgroundAndMasksHoisted(mapOrSnippet, landBackground, landTexture, oceanTexture, type, xLeft, yTop, graphXLeft, graphYTop, mapOrSnippetSize, icon, contentMask,
					shadingMask, hoistedLandTexturePixels, hoistedOceanTexturePixels, hoistedLandBackgroundPixels, hoistedMapPixels);
		}
		else
		{
			drawIconWithBackgroundAndMasksUsingSnippet(mapOrSnippet, imageAndMasks, landBackground, landTexture, oceanTexture, type, xLeft, yTop, graphXLeft, graphYTop, mapOrSnippetSize, icon,
					contentMask, shadingMask, hoistedLandTexturePixels, hoistedOceanTexturePixels, hoistedLandBackgroundPixels);
		}
	}

	private void drawIconWithBackgroundAndMasksDirect(Image mapOrSnippet, ImageAndMasks imageAndMasks, Image landBackground, Image landTexture, Image oceanTexture, IconType type, int xLeft, int yTop,
			int graphXLeft, int graphYTop, IntDimension mapOrSnippetSize, Image icon, Image contentMask, Image shadingMask, PixelReader hoistedLandTexturePixels, PixelReader hoistedOceanTexturePixels,
			PixelReader hoistedLandBackgroundPixels)
	{
		IntRectangle iconBoundsInMapOrSnippet = new IntRectangle(xLeft, yTop, icon.getWidth(), icon.getHeight());

		boolean useHoisted = hoistedLandTexturePixels != null;

		// Begin pixel sessions for efficient read/write.
		// If hoisted readers are provided, use them for the shared images; otherwise create bounded readers per icon.
		try (PixelReader landTexturePixels = useHoisted ? null : landTexture.createPixelReader(iconBoundsInMapOrSnippet);
				PixelReader oceanTexturePixels = useHoisted ? null : oceanTexture.createPixelReader(iconBoundsInMapOrSnippet);
				PixelReader landBackgroundPixels = useHoisted ? null : landBackground.createPixelReader(iconBoundsInMapOrSnippet);
				PixelReader contentMaskPixels = contentMask.createPixelReader();
				PixelReader shadingMaskPixels = shadingMask.createPixelReader();
				PixelReaderWriter mapOrSnippetPixels = mapOrSnippet.createPixelReaderWriter(iconBoundsInMapOrSnippet))
		{
			PixelReader effectiveLandTexturePixels = useHoisted ? hoistedLandTexturePixels : landTexturePixels;
			PixelReader effectiveOceanTexturePixels = useHoisted ? hoistedOceanTexturePixels : oceanTexturePixels;
			PixelReader effectiveLandBackgroundPixels = useHoisted ? hoistedLandBackgroundPixels : landBackgroundPixels;
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

					if (!graph.isPointOnMap(graphXLeft + x, graphYTop + y))
					{
						// The pixel isn't on the map.
						continue;
					}

					if (type == IconType.decorations)
					{
						// Decorations can sit on open water, so what shows through their transparent pixels depends on whether this pixel is
						// land or ocean. Every other icon type always blends with the land background and texture, so it needs no center
						// lookup - worth keeping out of this loop because it runs once per pixel of every icon drawn.
						Center closest = graph.findClosestCenter(new Point(graphXLeft + x, graphYTop + y), true);
						if (closest == null)
						{
							continue;
						}

						bgColorNoIcons = closest.isWater ? Color.create(effectiveOceanTexturePixels.getRGB(xLoc, yLoc), oceanTexture.hasAlpha())
								: Color.create(effectiveLandBackgroundPixels.getRGB(xLoc, yLoc), landBackground.hasAlpha());

						landTextureColor = closest.isWater ? Color.create(effectiveOceanTexturePixels.getRGB(xLoc, yLoc), oceanTexture.hasAlpha())
								: Color.create(effectiveLandBackgroundPixels.getRGB(xLoc, yLoc), landBackground.hasAlpha());
					}
					else
					{
						bgColorNoIcons = Color.create(effectiveLandBackgroundPixels.getRGB(xLoc, yLoc), landBackground.hasAlpha());

						landTextureColor = Color.create(effectiveLandTexturePixels.getRGB(xLoc, yLoc), landTexture.hasAlpha());
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

	private void drawIconWithBackgroundAndMasksUsingSnippet(Image mapOrSnippet, ImageAndMasks imageAndMasks, Image landBackground, Image landTexture, Image oceanTexture, IconType type, int xLeft,
			int yTop, int graphXLeft, int graphYTop, IntDimension mapOrSnippetSize, Image icon, Image contentMask, Image shadingMask, PixelReader hoistedLandTexturePixels,
			PixelReader hoistedOceanTexturePixels, PixelReader hoistedLandBackgroundPixels)
	{
		// Calculate the visible portion of the icon (clipped to map bounds)
		int visibleXStart = Math.max(0, xLeft);
		int visibleYStart = Math.max(0, yTop);
		int visibleXEnd = Math.min(mapOrSnippetSize.width, xLeft + icon.getWidth());
		int visibleYEnd = Math.min(mapOrSnippetSize.height, yTop + icon.getHeight());

		// If the icon is entirely off the map, skip it
		if (visibleXStart >= visibleXEnd || visibleYStart >= visibleYEnd)
		{
			return;
		}

		IntRectangle visibleBounds = new IntRectangle(visibleXStart, visibleYStart, visibleXEnd - visibleXStart, visibleYEnd - visibleYStart);

		// Extract a small snippet from mapOrSnippet for the visible region.
		// This avoids expensive GPU<->CPU sync on the entire large map image.
		// Instead, we only sync the small icon-sized region.
		Image mapSnippetForIcon = mapOrSnippet.copySubImage(visibleBounds, false);

		boolean useHoisted = hoistedLandTexturePixels != null;

		try
		{
			// Begin pixel sessions for efficient read/write.
			// If hoisted readers are provided, use them for the shared images; otherwise create bounded readers per icon.
			try (PixelReader landTexturePixels = useHoisted ? null : landTexture.createPixelReader(visibleBounds);
					PixelReader oceanTexturePixels = useHoisted ? null : oceanTexture.createPixelReader(visibleBounds);
					PixelReader landBackgroundPixels = useHoisted ? null : landBackground.createPixelReader(visibleBounds);
					PixelReader contentMaskPixels = contentMask.createPixelReader();
					PixelReader shadingMaskPixels = shadingMask.createPixelReader();
					PixelReaderWriter snippetPixels = mapSnippetForIcon.createPixelReaderWriter())
			{
				PixelReader effectiveLandTexturePixels = useHoisted ? hoistedLandTexturePixels : landTexturePixels;
				PixelReader effectiveOceanTexturePixels = useHoisted ? hoistedOceanTexturePixels : oceanTexturePixels;
				PixelReader effectiveLandBackgroundPixels = useHoisted ? hoistedLandBackgroundPixels : landBackgroundPixels;

				// Iterate only over the visible portion
				for (int y = visibleYStart - yTop; y < visibleYEnd - yTop; y++)
				{
					for (int x = visibleXStart - xLeft; x < visibleXEnd - xLeft; x++)
					{
						// grey level of mask at the corresponding pixel in mask.
						float contentMaskLevel = contentMaskPixels.getNormalizedPixelLevel(x, y);
						float shadingMaskLevel = shadingMaskPixels.getNormalizedPixelLevel(x, y);
						Color bgColorNoIcons;
						Color mapColor;
						Color landTextureColor;

						// Location in the original map coordinate system
						int xLoc = xLeft + x;
						int yLoc = yTop + y;

						// Location in the snippet coordinate system (0-based)
						int snippetX = xLoc - visibleXStart;
						int snippetY = yLoc - visibleYStart;

						if (!graph.isPointOnMap(graphXLeft + x, graphYTop + y))
						{
							// The pixel isn't on the map.
							continue;
						}

						if (type == IconType.decorations)
						{
							// Decorations can sit on open water, so what shows through their transparent pixels depends on whether this pixel
							// is land or ocean. Every other icon type always blends with the land background and texture, so it needs no
							// center lookup - worth keeping out of this loop because it runs once per pixel of every icon drawn.
							Center closest = graph.findClosestCenter(new Point(graphXLeft + x, graphYTop + y), true);
							if (closest == null)
							{
								continue;
							}

							bgColorNoIcons = closest.isWater ? Color.create(effectiveOceanTexturePixels.getRGB(xLoc, yLoc), oceanTexture.hasAlpha())
									: Color.create(effectiveLandBackgroundPixels.getRGB(xLoc, yLoc), landBackground.hasAlpha());

							landTextureColor = closest.isWater ? Color.create(effectiveOceanTexturePixels.getRGB(xLoc, yLoc), oceanTexture.hasAlpha())
									: Color.create(effectiveLandBackgroundPixels.getRGB(xLoc, yLoc), landBackground.hasAlpha());
						}
						else
						{
							bgColorNoIcons = Color.create(effectiveLandBackgroundPixels.getRGB(xLoc, yLoc), landBackground.hasAlpha());

							landTextureColor = Color.create(effectiveLandTexturePixels.getRGB(xLoc, yLoc), landTexture.hasAlpha());
						}

						mapColor = Color.create(snippetPixels.getRGB(snippetX, snippetY), mapSnippetForIcon.hasAlpha());

						// Use the shading mask to blend the coastline shading with the land background texture for pixels with transparency
						// in
						// the icon and non-zero values in the content mask. This way coastline shading doesn't draw through icons, since
						// that
						// would look weird when the icon extends over the coastline. It also makes the transparent pixels in the content of
						// the icon draw the land background texture when the shading mask is white, so that icons extending into the ocean
						// draw the land texture behind them rather than the ocean texture.
						int red = (int) (Helper.linearCombo(contentMaskLevel, Helper.linearCombo(shadingMaskLevel, bgColorNoIcons.getRed(), landTextureColor.getRed()), mapColor.getRed()));
						int green = (int) (Helper.linearCombo(contentMaskLevel, Helper.linearCombo(shadingMaskLevel, bgColorNoIcons.getGreen(), landTextureColor.getGreen()), mapColor.getGreen()));
						int blue = (int) (Helper.linearCombo(contentMaskLevel, Helper.linearCombo(shadingMaskLevel, bgColorNoIcons.getBlue(), landTextureColor.getBlue()), mapColor.getBlue()));
						int alpha = (int) (Helper.linearCombo(contentMaskLevel, (Helper.linearCombo(shadingMaskLevel, bgColorNoIcons.getAlpha(), landTextureColor.getAlpha())), mapColor.getAlpha()));
						snippetPixels.setRGB(snippetX, snippetY, red, green, blue, alpha);
					}
				}
			}

			// Draw the icon onto the snippet
			try (Painter p = mapSnippetForIcon.createPainter())
			{
				// Icon position relative to the snippet's top-left corner
				p.drawImage(imageAndMasks.image, xLeft - visibleXStart, yTop - visibleYStart);
			}

			// Paste the snippet back onto the main map
			try (Painter p = mapOrSnippet.createPainter())
			{
				p.drawImage(mapSnippetForIcon, visibleXStart, visibleYStart);
			}
		}
		finally
		{
			mapSnippetForIcon.close();
		}
	}

	/**
	 * High-memory, non-AWT variant of the icon blend. Writes directly through a map-wide {@link PixelReaderWriter} ({@code hoistedMapPixels})
	 * that {@link #drawIcons} opened ONCE for the whole call, rather than copying a snippet out of the map, blending, and pasting it back per
	 * icon. On a GPU backend that turns the per-icon glReadPixels/glTexSubImage2D round-trips into one readback + one upload for the entire
	 * call (all per-icon writes accumulate in the reader/writer's CPU buffer until it closes).
	 *
	 * <p>
	 * The background blend is identical to the snippet/direct paths. The icon image itself is composited manually with
	 * {@link #compositeSourceOver} instead of via a {@link Painter}: a Painter would draw straight to the backing image (e.g. the GPU texture),
	 * which the hoisted reader/writer's buffered flush would then clobber. {@link #compositeSourceOver} mirrors the platform painter's
	 * non-premultiplied source-over exactly, so this path is byte-identical to the snippet path (verified by the high-vs-low-memory test).
	 */
	private void drawIconWithBackgroundAndMasksHoisted(Image mapOrSnippet, Image landBackground, Image landTexture, Image oceanTexture, IconType type, int xLeft, int yTop,
			int graphXLeft, int graphYTop, IntDimension mapOrSnippetSize, Image icon, Image contentMask, Image shadingMask, PixelReader hoistedLandTexturePixels, PixelReader hoistedOceanTexturePixels,
			PixelReader hoistedLandBackgroundPixels, PixelReaderWriter hoistedMapPixels)
	{
		boolean targetOpaque = !mapOrSnippet.hasAlpha();
		try (PixelReader contentMaskPixels = contentMask.createPixelReader();
				PixelReader shadingMaskPixels = shadingMask.createPixelReader())
		{
			// Phase 1: blend the land/ocean background through the content + shading masks into the map, reading the current map color.
			for (int y : new Range(icon.getHeight()))
			{
				for (int x = 0; x < icon.getWidth(); x++)
				{
					int xLoc = xLeft + x;
					int yLoc = yTop + y;
					if (xLoc < 0 || xLoc >= mapOrSnippetSize.width || yLoc < 0 || yLoc >= mapOrSnippetSize.height)
					{
						continue;
					}

					if (!graph.isPointOnMap(graphXLeft + x, graphYTop + y))
					{
						// The pixel isn't on the map.
						continue;
					}

					float contentMaskLevel = contentMaskPixels.getNormalizedPixelLevel(x, y);
					float shadingMaskLevel = shadingMaskPixels.getNormalizedPixelLevel(x, y);
					Color bgColorNoIcons;
					Color landTextureColor;
					if (type == IconType.decorations)
					{
						// Decorations can sit on open water, so what shows through their transparent pixels depends on whether this pixel is
						// land or ocean. Every other icon type always blends with the land background and texture, so it needs no center
						// lookup - worth keeping out of this loop because it runs once per pixel of every icon drawn.
						Center closest = graph.findClosestCenter(new Point(graphXLeft + x, graphYTop + y), true);
						if (closest == null)
						{
							continue;
						}

						bgColorNoIcons = closest.isWater ? Color.create(hoistedOceanTexturePixels.getRGB(xLoc, yLoc), oceanTexture.hasAlpha())
								: Color.create(hoistedLandBackgroundPixels.getRGB(xLoc, yLoc), landBackground.hasAlpha());
						landTextureColor = closest.isWater ? Color.create(hoistedOceanTexturePixels.getRGB(xLoc, yLoc), oceanTexture.hasAlpha())
								: Color.create(hoistedLandBackgroundPixels.getRGB(xLoc, yLoc), landBackground.hasAlpha());
					}
					else
					{
						bgColorNoIcons = Color.create(hoistedLandBackgroundPixels.getRGB(xLoc, yLoc), landBackground.hasAlpha());
						landTextureColor = Color.create(hoistedLandTexturePixels.getRGB(xLoc, yLoc), landTexture.hasAlpha());
					}

					Color mapColor = Color.create(hoistedMapPixels.getRGB(xLoc, yLoc), mapOrSnippet.hasAlpha());

					int red = (int) (Helper.linearCombo(contentMaskLevel, Helper.linearCombo(shadingMaskLevel, bgColorNoIcons.getRed(), landTextureColor.getRed()), mapColor.getRed()));
					int green = (int) (Helper.linearCombo(contentMaskLevel, Helper.linearCombo(shadingMaskLevel, bgColorNoIcons.getGreen(), landTextureColor.getGreen()), mapColor.getGreen()));
					int blue = (int) (Helper.linearCombo(contentMaskLevel, Helper.linearCombo(shadingMaskLevel, bgColorNoIcons.getBlue(), landTextureColor.getBlue()), mapColor.getBlue()));
					int alpha = (int) (Helper.linearCombo(contentMaskLevel, (Helper.linearCombo(shadingMaskLevel, bgColorNoIcons.getAlpha(), landTextureColor.getAlpha())), mapColor.getAlpha()));
					hoistedMapPixels.setRGB(xLoc, yLoc, red, green, blue, alpha);
				}
			}

			// Phase 2: composite the icon image on top (source-over), matching Painter.drawImage so output matches the snippet path.
			try (PixelReader iconPixels = icon.createPixelReader())
			{
				boolean iconHasAlpha = icon.hasAlpha();
				for (int y = 0; y < icon.getHeight(); y++)
				{
					for (int x = 0; x < icon.getWidth(); x++)
					{
						int xLoc = xLeft + x;
						int yLoc = yTop + y;
						if (xLoc < 0 || xLoc >= mapOrSnippetSize.width || yLoc < 0 || yLoc >= mapOrSnippetSize.height)
						{
							continue;
						}
						Color src = Color.create(iconPixels.getRGB(x, y), iconHasAlpha);
						compositeSourceOver(hoistedMapPixels, xLoc, yLoc, src, mapOrSnippet.hasAlpha(), targetOpaque);
					}
				}
			}
		}
	}

	/**
	 * Non-premultiplied source-over of {@code src} over the current destination pixel, written through {@code dst}. This deliberately mirrors
	 * gdx2d's integer blend (the formula {@code pixmap.drawPixmap} uses for a SourceOver blit), NOT the painter's float {@code storeColorAt}:
	 * the snippet path draws the icon via {@code pixmap.drawPixmap} (the icon and map share the RGBA8888 backing, so the painter's fast blit
	 * path is taken), so matching gdx2d's exact integer rounding is what keeps the hoisted high-memory output byte-identical to the snippet
	 * (low-memory) path. gdx2d: if src alpha is 0 keep dst; if 255 (or dst fully transparent) copy src; else
	 * {@code da' = da*(255-sa)/255; a = sa + da'; c = (sc*sa + dc*da')/a} (all integer division).
	 */
	private static void compositeSourceOver(PixelReaderWriter dst, int x, int y, Color src, boolean dstHasAlpha, boolean targetOpaque)
	{
		int sa = src.getAlpha();
		if (sa == 0)
		{
			return;
		}
		int sr = src.getRed();
		int sg = src.getGreen();
		int sb = src.getBlue();
		if (sa == 255)
		{
			dst.setRGB(x, y, sr, sg, sb, 255);
			return;
		}
		Color d = Color.create(dst.getRGB(x, y), dstHasAlpha);
		int da = d.getAlpha();
		if (da == 0)
		{
			dst.setRGB(x, y, sr, sg, sb, targetOpaque ? 255 : sa);
			return;
		}
		int daAdj = (da * (255 - sa)) / 255;
		int a = sa + daAdj;
		int red = (sr * sa + d.getRed() * daAdj) / a;
		int green = (sg * sa + d.getGreen() * daAdj) / a;
		int blue = (sb * sa + d.getBlue() * daAdj) / a;
		dst.setRGB(x, y, red, green, blue, targetOpaque ? 255 : a);
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
		if (tasksToDrawSorted.isEmpty())
		{
			return;
		}

		int xToSubtract = drawBounds == null ? 0 : (int) drawBounds.x;
		int yToSubtract = drawBounds == null ? 0 : (int) drawBounds.y;

		// GPU fast path: handle non-decoration icons via shader (no CPU pixel loops, no GPU readbacks).
		// Returns the remaining icons (decorations) that still need the CPU path, or null if unsupported.
		List<IconDrawTask> tasksForCpu = PlatformFactory.getInstance().drawNonDecorationIconsGpu(tasksToDrawSorted, mapOrSnippet, landBackground, landTexture, drawBounds);
		if (tasksForCpu == null)
		{
			tasksForCpu = tasksToDrawSorted;
		}

		if (tasksForCpu.isEmpty())
		{
			return;
		}

		// CPU path: hoist background-texture readers once across all icons to avoid repeated GPU readbacks.
		// For non-AWT backends use the snippet path (mapPixels=null): each icon reads a small map snippet and
		// composites the icon via GPU Painter, which is faster than the full-map readback+CPU-compositing the
		// hoisted writer would force.
		if (!isLowMemoryMode)
		{
			boolean useAwtDirect = PlatformFactory.getInstance() instanceof AwtFactory;
			try (PixelReader landTexturePixels = landTexture.createPixelReader();
					PixelReader oceanTexturePixels = oceanWithWavesAndShading.createPixelReader();
					PixelReader landBackgroundPixels = landBackground.createPixelReader())
			{
				for (final IconDrawTask task : tasksForCpu)
				{
					drawIconWithBackgroundAndMasks(mapOrSnippet, task.scaledImageAndMasks, landBackground, landTexture, oceanWithWavesAndShading, task.type, ((int) task.centerLoc.x) - xToSubtract,
							((int) task.centerLoc.y) - yToSubtract, (int) task.centerLoc.x, (int) task.centerLoc.y, landTexturePixels, oceanTexturePixels, landBackgroundPixels,
							useAwtDirect ? null : null);
				}
			}
		}
		else
		{
			for (final IconDrawTask task : tasksForCpu)
			{
				drawIconWithBackgroundAndMasks(mapOrSnippet, task.scaledImageAndMasks, landBackground, landTexture, oceanWithWavesAndShading, task.type, ((int) task.centerLoc.x) - xToSubtract,
						((int) task.centerLoc.y) - yToSubtract, (int) task.centerLoc.x, (int) task.centerLoc.y, null, null, null, null);
			}
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

				ImageHelper.getInstance().drawIfPixelValueIsGreaterThanTarget(landMask, task.scaledImageAndMasks.getOrCreateContentMask(), xLoc - xToSubtract, yLoc - yToSubtract);
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

			createDrawTasksForFreeIconsAndRemovedFailedIcons(warningLogger, null);
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
						int i = Helper.safeAbs(rand.nextInt());

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
							int i = Helper.safeAbs(rand.nextInt());

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

		List<Set<Center>> groups = findCenterGroups(graph, maxGapBetweenBiomeGroups, center -> center.biome.equals(sandDunesBiome));

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
						int i = Helper.safeAbs(rand.nextInt());
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
		if (ImageCache.getInstance(artPackForNewMap, customImagesPath).getIconGroupsAsListsForType(IconType.trees).isEmpty())
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
				List<Set<Center>> groups = findCenterGroups(graph, maxGapBetweenBiomeGroups, center -> center.biome.equals(forest.biome));
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
					// These trees failed to draw, so they go dormant and are persisted. Stamp the colors they were drawn with (the per-type
					// colors, or their own remembered colors) so that when they reawaken (see ThemePanel.triggerRebuildAllAnchoredTrees)
					// they
					// reappear with their original color rather than whatever the per-type tree color happens to be then.
					CenterTrees dormantTrees = edits.centerEdits.get(index).trees;
					IconColors colorsUsed = resolveIconColors(dormantTrees.colors, IconType.trees);
					edits.centerEdits.put(index, edits.centerEdits.get(index).copyWithTrees(dormantTrees.copyWithIsDormant(true).copyWithColors(colorsUsed)));
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
		if (treesByCenter.isEmpty())
		{
			return null;
		}

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
				changeBounds = Rectangle.add(changeBounds, drawTreesAtCenterAndCorners(c, toUse, treesByCenter.keySet()));
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

	private Rectangle drawTreesAtCenterAndCorners(Center center, CenterTrees cTrees, Set<Integer> additionalCentersThatWillHaveTrees)
	{
		Rectangle changeBounds = getAnchoredTreeIconBoundsAt(center.index);
		freeIcons.clearTrees(center.index);

		Random rand = new Random(cTrees.randomSeed);
		// Use the colors these trees remember (e.g. dormant or sub-map-redistributed trees), falling back to the per-type tree colors.
		IconColors colors = resolveIconColors(cTrees.colors, IconType.trees);
		addTreeNearLocation(center.loc, cTrees.density, center, rand, cTrees.artPack, cTrees.treeType, colors);

		// Draw trees at the neighboring corners too.
		// Note that corners use their own Random instance because the random
		// seed of that random needs to not depend on the center or else
		// trees would be placed differently based on which center drew first.
		for (Corner corner : center.corners)
		{
			if (shouldCenterDrawTreesForCorner(center, corner, additionalCentersThatWillHaveTrees))
			{
				addTreeNearLocation(corner.loc, cTrees.density, center, rand, cTrees.artPack, cTrees.treeType, colors);
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
	}

	;

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
	private void addTreeNearLocation(Point loc, double forestDensity, Center center, Random rand, String artPack, String groupId, IconColors colors)
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
			int index = Helper.safeAbs(rand.nextInt());

			// Draw the image such that it is centered in the center of c.
			double x = loc.x;
			double y = loc.y;

			final double scale = ((meanPolygonWidth * 2.0) / 10.0);
			x += rand.nextGaussian() * scale;
			y += rand.nextGaussian() * scale;

			FreeIcon icon = new FreeIcon(resolutionScale, new Point(x, y), 1.0, IconType.trees, artPack, groupId, index, center.index, forestDensity, colors.fillColor, colors.filterColor,
					colors.maximizeOpacity, colors.fillWithColor);

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

		return icon.toIconDrawTask(customImagesPath, resolutionScale, typeLevelScale, getBaseWidth(imageAndMasks));
	}

	private boolean isContentBottomTouchingWater(IconDrawTask iconTask)
	{
		if (iconTask == null)
		{
			return false;
		}

		if (iconTask.unScaledImageAndMasks.getOrCreateContentMask().getType() != ImageType.Binary)
			throw new IllegalArgumentException("Mask type must be TYPE_BYTE_BINARY for checking whether icons touch water.");

		// Keep the icon's center as a float (centerLoc is locationResolutionInvariant * resolutionScale) instead of truncating it to an
		// integer here. Truncating before the check discards the sub-pixel part of the center, and since the discarded fraction changes with
		// resolutionScale, the sample grid below would shift by up to ~1 rendered pixel when the display quality changes - enough to flip the
		// water test near a coast and make an icon (e.g. a city) appear or disappear. Using the float position makes the samples vary
		// smoothly with resolution instead of stepping.
		//
		// Use the un-rounded scaled size (not scaledSize, which is rounded to whole pixels) for the same reason: scaledSize's rounding
		// amount changes with resolution, so deriving the sampling origin and scale factors from it would reintroduce a sub-pixel,
		// resolution-dependent wobble. The drawn icon is within half a pixel of this ideal size.
		final double scaledWidth = iconTask.unroundedScaledSize.width;
		final double scaledHeight = iconTask.unroundedScaledSize.height;
		final double imageUpperLeftX = iconTask.centerLoc.x - scaledWidth / 2.0;
		final double imageUpperLeftY = iconTask.centerLoc.y - scaledHeight / 2.0;

		Rectangle scaledContentBounds;
		double contentMidpointYInMaskSpace;
		{
			IntRectangle contentBounds = iconTask.unScaledImageAndMasks.getOrCreateContentBounds();
			if (contentBounds == null)
			{
				// The icon is fully transparent.
				return false;
			}

			contentMidpointYInMaskSpace = contentBounds.y + contentBounds.height / 2.0;

			final double xScaleToScaledIconSpace = scaledWidth / (double) iconTask.unScaledImageAndMasks.getOrCreateContentMask().getWidth();
			final double yScaleToScaledIconSpace = scaledHeight / (double) iconTask.unScaledImageAndMasks.getOrCreateContentMask().getHeight();

			scaledContentBounds = new Rectangle(contentBounds.x * xScaleToScaledIconSpace, contentBounds.y * yScaleToScaledIconSpace, contentBounds.width * xScaleToScaledIconSpace,
					contentBounds.height * yScaleToScaledIconSpace);
		}

		// The constant in this number is in number of pixels at 100% resolution. I include the resolution here so that the loop below will
		// make the same number of steps (approximately) no matter the resolution. This is to reduce the chances that icons will appear or
		// disappear when you draw the map at a different resolution.
		final double stepSize = 2.0 * resolutionScale;

		final double xScaleToMaskSpace = ((double) iconTask.unScaledImageAndMasks.getOrCreateContentMask().getWidth()) / scaledWidth;
		final double yScaleToMaskSpace = ((double) iconTask.unScaledImageAndMasks.getOrCreateContentMask().getHeight()) / scaledHeight;

		for (double x = scaledContentBounds.x; x < scaledContentBounds.x + scaledContentBounds.width; x += stepSize)
		{
			int xInMask = (int) (x * xScaleToMaskSpace);
			Integer yInMask = iconTask.unScaledImageAndMasks.getContentYStart(xInMask);
			if (yInMask == null)
			{
				continue;
			}
			// Skip columns where the content starts in the upper half of the content bounds.
			// This avoids false positives from wide features like roofs that extend over water.
			if (yInMask < contentMidpointYInMaskSpace)
			{
				continue;
			}
			double y = yInMask * (1.0 / yScaleToMaskSpace);

			// Classify against the coastline at a fixed canonical resolution (not the current display resolution) so this decision doesn't
			// change when the user changes display quality - otherwise the noisy coastline shifts slightly and a coast-hugging icon (e.g. a
			// city) can flip between land and water and be deleted. (The third arg = useWaterCheckResolution.)
			Center center = graph.findClosestCenter(new Point(imageUpperLeftX + x, imageUpperLeftY + y), true, true);
			if (center != null && center.isWater)
			{
				return true;
			}
		}

		return false;
	}
}
