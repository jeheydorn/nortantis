package nortantis;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
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
import java.util.stream.Collectors;

import nortantis.editor.CenterEdit;
import nortantis.editor.CenterIcon;
import nortantis.editor.CenterIconType;
import nortantis.editor.CenterTrees;
import nortantis.graph.geom.Point;
import nortantis.graph.geom.Rectangle;
import nortantis.graph.voronoi.Center;
import nortantis.graph.voronoi.Corner;
import nortantis.swing.MapEdits;
import nortantis.util.Coordinate;
import nortantis.util.Function;
import nortantis.util.HashMapF;
import nortantis.util.ImageHelper;
import nortantis.util.ListMap;
import nortantis.util.Logger;
import nortantis.util.Pair;
import nortantis.util.Range;
import nortantis.util.ThreadHelper;
import nortantis.util.Tuple2;
import nortantis.util.Tuple3;

public class IconDrawer
{
	public static final double mountainElevationThreshold = 0.58;
	public static final double hillElevationThreshold = 0.53;
	final double treeScale = 4.0 / 8.0;
	final double meanPolygonWidth;
	final int duneWidth;
	final double cityScale;
	// For hills and mountains, if a polygon is this number times meanPolygonWidth wide, no icon will be drawn on it.
	final double maxMeansToDraw = 5.0;
	double maxSizeToDrawIcon;
	// Max gap (in polygons) between mountains for considering them a single group. Warning:
	// there tend to be long polygons along edges, so if this value is much more than 2,
	// mountains near the ocean may be connected despite long distances between them..
	private final int maxGapSizeInMountainClusters = 2;
	private final int maxGapBetweenBiomeGroups = 2;
	// Mountain images are scaled by this.
	private final double mountainScale = 1.0;
	// Hill images are scaled by this.
	private final double hillScale = 0.5;
	private HashMapF<Center, List<IconDrawTask>> iconsToDraw;
	WorldGraph graph;
	Random rand;
	/**
	 * Used to store icons drawn when generating icons so they can be edited later by the editor.
	 */
	public Map<Integer, CenterIcon> centerIcons;
	public Map<Integer, CenterTrees> trees;
	private double averageCenterWidthBetweenNeighbors;
	private String cityIconType;

	public IconDrawer(WorldGraph graph, Random rand, String cityIconsSetName)
	{
		iconsToDraw = new HashMapF<>(() -> new ArrayList<>(1));
		this.graph = graph;
		this.rand = rand;
		this.cityIconType = cityIconsSetName;

		meanPolygonWidth = findMeanCenterWidth(graph);
		duneWidth = (int) (meanPolygonWidth * 1.5);
		maxSizeToDrawIcon = meanPolygonWidth * maxMeansToDraw;
		cityScale = meanPolygonWidth * (1.0 / 11.0);
		centerIcons = new HashMap<>();
		trees = new HashMap<>();

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
			if (c.elevation > mountainElevationThreshold && !c.isCoast && !c.isBorder && c.findWidth() < maxSizeToDrawIcon)
			{
				c.isMountain = true;
			}
		}
	}

	public void markHills()
	{
		for (Center c : graph.centers)
		{
			if (c.elevation < mountainElevationThreshold && c.elevation > hillElevationThreshold && !c.isCoast
					&& c.findWidth() < maxSizeToDrawIcon)

			{
				c.isHill = true;
			}
		}
	}

	public void markCities(double cityProbability)
	{
		for (Center c : graph.centers)
		{
			if (!c.isMountain && !c.isHill && !c.isWater)
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
				if (c.isRiver() && cityByRiverProbability <= cityProbability * 2)
				{
					c.isCity = true;
				}
				else if (c.isCoast && cityByCoastProbability <= cityProbability * 2)
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

	/**
	 * Finds and marks mountain ranges, and groups smaller than ranges, and surrounding hills.
	 */
	public Pair<List<Set<Center>>> findMountainAndHillGroups()
	{
		List<Set<Center>> mountainGroups = findCenterGroups(graph, maxGapSizeInMountainClusters, new Function<Center, Boolean>()
		{
			public Boolean apply(Center center)
			{
				return center.isMountain;
			}
		});

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

		return new Pair<>(mountainGroups, mountainAndHillGroups);

	}

	/**
	 * This is used to add icon to draw tasks from map edits rather than using the generator to add them. The actual drawing of the icons is
	 * done later.
	 */
	public void addOrUpdateIconsFromEdits(MapEdits edits, double sizeMultiplyer, Collection<Center> centersToUpdateIconsFor)
	{
		clearIconsForCenters(centersToUpdateIconsFor);

		ListMap<String, Tuple2<BufferedImage, BufferedImage>> mountainImagesById = ImageCache.getInstance()
				.getAllIconGroupsAndMasksForType(IconType.mountains);
		ListMap<String, Tuple2<BufferedImage, BufferedImage>> hillImagesById = ImageCache.getInstance()
				.getAllIconGroupsAndMasksForType(IconType.hills);
		List<Tuple2<BufferedImage, BufferedImage>> duneImages = ImageCache.getInstance().getAllIconGroupsAndMasksForType(IconType.sand)
				.get("dunes");
		Map<String, Tuple3<BufferedImage, BufferedImage, Integer>> cityImages = ImageCache.getInstance()
				.getIconsWithWidths(IconType.cities, cityIconType);

			for (Center center : centersToUpdateIconsFor)
			{
				CenterEdit cEdit = edits.centerEdits.get(center.index);
				if (cEdit.icon != null)
				{
					if (cEdit.icon.iconType == CenterIconType.Mountain && cEdit.icon.iconGroupId != null && !mountainImagesById.isEmpty())
					{
						String groupId = cEdit.icon.iconGroupId;
						if (!mountainImagesById.containsKey(groupId))
						{
							// Someone removed the icon group. Choose a new group.
							groupId = chooseNewGroupId(mountainImagesById.keySet(), groupId);
						}
						if (mountainImagesById.get(groupId).size() > 0)
						{
							int scaledSize = findScaledMountainSize(center);
							BufferedImage mountainImage = mountainImagesById.get(groupId)
									.get(cEdit.icon.iconIndex % mountainImagesById.get(groupId).size()).getFirst();
							BufferedImage mask = mountainImagesById.get(groupId)
									.get(cEdit.icon.iconIndex % mountainImagesById.get(groupId).size()).getSecond();
							iconsToDraw.getOrCreate(center)
									.add(new IconDrawTask(mountainImage, mask, IconType.mountains, center.loc, scaledSize, true, false));
						}
					}
					else if (cEdit.icon.iconType == CenterIconType.Hill && cEdit.icon.iconGroupId != null && !hillImagesById.isEmpty())
					{
						String groupId = cEdit.icon.iconGroupId;
						if (!hillImagesById.containsKey(groupId))
						{
							// Someone removed the icon group. Choose a new group.
							groupId = chooseNewGroupId(hillImagesById.keySet(), groupId);
						}
						if (hillImagesById.get(groupId).size() > 0)
						{
							int scaledSize = findScaledHillSize(center);
							BufferedImage hillImage = hillImagesById.get(groupId)
									.get(cEdit.icon.iconIndex % hillImagesById.get(groupId).size()).getFirst();
							BufferedImage mask = hillImagesById.get(groupId).get(cEdit.icon.iconIndex % hillImagesById.get(groupId).size())
									.getSecond();
							iconsToDraw.getOrCreate(center)
									.add(new IconDrawTask(hillImage, mask, IconType.hills, center.loc, scaledSize, true, false));
						}
					}
					else if (cEdit.icon.iconType == CenterIconType.Dune && duneWidth > 0 && duneImages != null && !duneImages.isEmpty())
					{
						BufferedImage duneImage = duneImages.get(cEdit.icon.iconIndex % duneImages.size()).getFirst();
						BufferedImage mask = duneImages.get(cEdit.icon.iconIndex % duneImages.size()).getSecond();
						iconsToDraw.getOrCreate(center)
								.add(new IconDrawTask(duneImage, mask, IconType.sand, center.loc, duneWidth, true, false));
					}
					else if (cEdit.icon.iconType == CenterIconType.City && cityImages != null && !cityImages.isEmpty())
					{
						BufferedImage cityImage;
						BufferedImage mask;
						String cityIconName = null;
						if (cityImages.containsKey(cEdit.icon.iconName))
						{
							cityIconName = cEdit.icon.iconName;
						}
						else if (cityImages.size() > 0)
						{
							// Either the city image is missing, or the icon set name changed. Choose a new image in a deterministic but
							// random way.
							cityIconName = chooseNewGroupId(cityImages.keySet(), cEdit.icon.iconName);
							if (cityIconName != null)
							{
								// Store the city icon name so that if someone later adds or removes other city icons, it doesn't affect
								// which one is used for this center.
								cEdit.icon.iconName = cityIconName;
							}
						}
						if (cityIconName != null)
						{
							cityImage = cityImages.get(cityIconName).getFirst();
							mask = cityImages.get(cityIconName).getSecond();
							iconsToDraw.getOrCreate(center).add(
									new IconDrawTask(
											cityImage, mask, IconType.cities, center.loc,
											(int) (cityImages.get(cityIconName).getThird() * cityScale), true, true, cityIconName
									)
							);
						}
					}

				}

				if (cEdit.trees != null)
				{
					trees.put(cEdit.index, cEdit.trees);
				}
			}

			drawTreesForCenters(centersToUpdateIconsFor);
	}

	public boolean doesCityFitOnLand(Center center, CenterIcon cityIcon)
	{
		if (center == null || cityIcon == null)
		{
			return true;
		}

		Map<String, Tuple3<BufferedImage, BufferedImage, Integer>> cityImages = ImageCache.getInstance()
				.getIconsWithWidths(IconType.cities, cityIconType);
		Tuple3<BufferedImage, BufferedImage, Integer> tuple = cityImages.get(cityIcon.iconName);
		if (tuple == null)
		{
			// Not a city icon
			return false;
		}

		BufferedImage cityImage = cityImages.get(cityIcon.iconName).getFirst();
		BufferedImage mask = cityImages.get(cityIcon.iconName).getSecond();

		// Create an icon draw task just for checking if the city fits on land. It won't actually be drawn.
		IconDrawTask task = new IconDrawTask(
				cityImage, mask, IconType.cities, center.loc, (int) (cityImages.get(cityIcon.iconName).getThird() * cityScale), true, true,
				cityIcon.iconName
		);
		return !isIconTouchingWater(task);
	}

	private void clearIconsForCenters(Collection<Center> centers)
	{
		for (Center center : centers)
		{
			if (iconsToDraw.containsKey(center))
			{
				iconsToDraw.get(center).clear();
			}

			if (trees.containsKey(center.index))
			{
				trees.put(center.index, null);
			}

			if (centerIcons.containsKey(center.index))
			{
				centerIcons.put(center.index, null);
			}
		}
	}

	private String chooseNewGroupId(Set<String> groupIds, String oldGroupId)
	{
		int randomIndex = Math.abs(oldGroupId.hashCode() % groupIds.size());
		return groupIds.toArray(new String[groupIds.size()])[randomIndex];
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
	private void drawIconWithBackgroundAndMask(BufferedImage mapOrSnippet, BufferedImage icon, BufferedImage mask,
			BufferedImage backgroundOrSnippet, int xCenter, int yCenter, boolean ignoreMaxSize)
	{
		if (mapOrSnippet.getWidth() != backgroundOrSnippet.getWidth())
			throw new IllegalArgumentException();
		if (mapOrSnippet.getHeight() != backgroundOrSnippet.getHeight())
			throw new IllegalArgumentException();
		if (mask.getWidth() != icon.getWidth())
			throw new IllegalArgumentException("The given mask's width does not match the icon' width.");
		if (mask.getHeight() != icon.getHeight())
			throw new IllegalArgumentException("The given mask's height does not match the icon' height.");

		if (!ignoreMaxSize && icon.getWidth() > maxSizeToDrawIcon)
			return;

		int xLeft = xCenter - icon.getWidth() / 2;
		int yBottom = yCenter - icon.getHeight() / 2;

		Raster maskRaster = mask.getRaster();
		for (int x : new Range(icon.getWidth()))
			for (int y : new Range(icon.getHeight()))
			{
				Color iconColor = new Color(icon.getRGB(x, y), true);
				double alpha = iconColor.getAlpha() / 255.0;
				// grey level of mask at the corresponding pixel in mask.
				double maskLevel = maskRaster.getSampleDouble(x, y, 0);
				Color bgColor;
				Color mapColor;
				// Find the location on the background and map where this pixel will be drawn.
				int xLoc = xLeft + x;
				int yLoc = yBottom + y;
				try
				{
					bgColor = new Color(backgroundOrSnippet.getRGB(xLoc, yLoc));
					mapColor = new Color(mapOrSnippet.getRGB(xLoc, yLoc));
				}
				catch (IndexOutOfBoundsException e)
				{
					// Skip this pixel.
					continue;
				}

				int red = (int) (alpha * (iconColor.getRed())
						+ (1 - alpha) * (maskLevel * bgColor.getRed() + (1 - maskLevel) * mapColor.getRed()));
				int green = (int) (alpha * (iconColor.getGreen())
						+ (1 - alpha) * (maskLevel * bgColor.getGreen() + (1 - maskLevel) * mapColor.getGreen()));
				int blue = (int) (alpha * (iconColor.getBlue())
						+ (1 - alpha) * (maskLevel * bgColor.getBlue() + (1 - maskLevel) * mapColor.getBlue()));

				mapOrSnippet.setRGB(xLoc, yLoc, new Color(red, green, blue).getRGB());
			}
	}

	/**
	 * Draws all icons in iconsToDraw that touch drawBounds.
	 * 
	 * I draw all the icons at once this way so that I can sort the icons by the y-coordinate of the base of each icon. This way icons lower
	 * on the map are drawn in front of those that are higher.
	 */
	public void drawAllIcons(BufferedImage mapOrSnippet, BufferedImage background, Rectangle drawBounds)
	{
		List<IconDrawTask> tasks = new ArrayList<IconDrawTask>();
		for (Map.Entry<Center, List<IconDrawTask>> entry : iconsToDraw.entrySet())
		{
			if (!entry.getKey().isWater)
			{
				for (final IconDrawTask task : entry.getValue())
				{
					if (drawBounds == null || task.overlaps(drawBounds))
					{
						tasks.add(task);
					}
				}
			}
		}
		Collections.sort(tasks);

		// Scale the icons in parallel.
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
		ThreadHelper.getInstance().processInParallel(jobs);

		int xToSubtract = drawBounds == null ? 0 : (int) drawBounds.x;
		int yToSubtract = drawBounds == null ? 0 : (int) drawBounds.y;

		for (final IconDrawTask task : tasks)
		{
			// Updates to the line below will will likely need to also update doesCityFitOnLand.
			if (!isIconTouchingWater(task))
			{
				drawIconWithBackgroundAndMask(
						mapOrSnippet, task.icon, task.mask, background, ((int) task.centerLoc.x) - xToSubtract,
						((int) task.centerLoc.y) - yToSubtract, task.ignoreMaxSize
				);
			}
			else
			{
				task.failedToDraw = true;
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
				List<IconDrawTask> tasks = iconsToDraw.get(center);
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
	public List<IconDrawTask> addOrUnmarkCities(double sizeMultiplyer, boolean addIconDrawTasks)
	{
		Map<String, Tuple3<BufferedImage, BufferedImage, Integer>> cityIcons = ImageCache.getInstance()
				.getIconsWithWidths(IconType.cities, cityIconType);
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
				int scaledWidth = (int) (cityIcons.get(cityName).getThird() * cityScale);
				BufferedImage icon = cityIcons.get(cityName).getFirst();

				IconDrawTask task = new IconDrawTask(
						icon, cityIcons.get(cityName).getSecond(), IconType.cities, c.loc, scaledWidth, true, true, cityName
				);
				// Updates to the line below will will likely need to also update doesCityFitOnLand.
				if (!isIconTouchingWater(task))
				{
					if (addIconDrawTasks)
					{
						iconsToDraw.getOrCreate(c).add(task);
						centerIcons.put(c.index, new CenterIcon(CenterIconType.City, cityName));
					}

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
	public List<Set<Center>> addMountainsAndHills(List<Set<Center>> mountainGroups, List<Set<Center>> mountainAndHillGroups)
	{
		// Maps mountain range ids (the ids in the file names) to list of mountain images and their masks.
		ListMap<String, Tuple2<BufferedImage, BufferedImage>> mountainImagesById = ImageCache.getInstance()
				.getAllIconGroupsAndMasksForType(IconType.mountains);
		if (mountainImagesById == null || mountainImagesById.isEmpty())
		{
			Logger.println("No mountain images were found. Mountain images will not be drawn.");
			return mountainGroups;
		}

		// Maps mountain range ids (the ids in the file names) to list of hill images and their masks.
		// The hill image file names must use the same ids as the mountain ranges.
		ListMap<String, Tuple2<BufferedImage, BufferedImage>> hillImagesById = ImageCache.getInstance()
				.getAllIconGroupsAndMasksForType(IconType.hills);

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
				Logger.println(
						"No hill images found for the mountain group \"" + mountainGroupId + "\". That mountain group will not have hills."
				);
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
					List<Tuple2<BufferedImage, BufferedImage>> imagesInRange = mountainImagesById.get(fileNameRangeId);

					// I'm deliberately putting this line before checking center size so that the
					// random number generator is used the same no matter what resolution the map
					// is drawn at.
					int i = rand.nextInt(imagesInRange.size());

					int scaledSize = findScaledMountainSize(c);

					// Make sure the image will be at least 1 pixel wide.
					if (scaledSize >= 1)
					{
						IconDrawTask task = new IconDrawTask(
								imagesInRange.get(i).getFirst(), imagesInRange.get(i).getSecond(), IconType.mountains, c.loc, scaledSize,
								true, false
						);

						// Draw the image such that it is centered in the center of c.
						iconsToDraw.getOrCreate(c).add(task);
						centerIcons.put(c.index, new CenterIcon(CenterIconType.Mountain, fileNameRangeId, i));
					}
				}
				else if (c.isHill)
				{
					List<Tuple2<BufferedImage, BufferedImage>> imagesInGroup = hillImagesById.get(fileNameRangeId);

					if (imagesInGroup != null && !imagesInGroup.isEmpty())
					{
						int i = rand.nextInt(imagesInGroup.size());

						int scaledSize = findScaledHillSize(c);

						// Make sure the image will be at least 1 pixel wide.
						if (scaledSize >= 1)
						{
							iconsToDraw.getOrCreate(c).add(
									new IconDrawTask(
											imagesInGroup.get(i).getFirst(), imagesInGroup.get(i).getSecond(), IconType.hills, c.loc,
											scaledSize, true, false
									)
							);
							centerIcons.put(c.index, new CenterIcon(CenterIconType.Hill, fileNameRangeId, i));
						}
					}
				}
			}
		}

		return mountainGroups;
	}

	private int findScaledMountainSize(Center c)
	{
		// Find the center's size along the x axis.
		double cSize = findCenterWidthBetweenNeighbors(c);
		return (int) (cSize * mountainScale);
	}

	private int findScaledHillSize(Center c)
	{
		// Find the center's size along the x axis.
		double cSize = findCenterWidthBetweenNeighbors(c);
		return (int) (cSize * hillScale);
	}

	public void addSandDunes()
	{
		ListMap<String, Tuple2<BufferedImage, BufferedImage>> sandGroups = ImageCache.getInstance()
				.getAllIconGroupsAndMasksForType(IconType.sand);
		if (sandGroups == null || sandGroups.isEmpty())
		{
			Logger.println("Sand dunes will not be drawn because no sand images were found.");
			return;
		}

		// Load the sand dune images.
		List<Tuple2<BufferedImage, BufferedImage>> duneImages = sandGroups.get("dunes");

		if (duneImages == null || duneImages.isEmpty())
		{
			Logger.println("Sand dunes will not be drawn because no sand dune images were found.");
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

		if (duneWidth == 0)
		{
			return;
		}

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
						iconsToDraw.getOrCreate(c).add(
								new IconDrawTask(
										duneImages.get(i).getFirst(), duneImages.get(i).getSecond(), IconType.sand, c.loc, duneWidth, true,
										false
								)
						);
						centerIcons.put(c.index, new CenterIcon(CenterIconType.Dune, "sand", i));
					}
				}

			}
		}
	}

	public void addTrees()
	{
		addCenterTrees();
		drawTreesForCenters(graph.centers);
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
	public void drawTreesForCenters(Collection<Center> centersToDraw)
	{
		// Load the images and masks.
		ListMap<String, Tuple2<BufferedImage, BufferedImage>> treesById = ImageCache.getInstance()
				.getAllIconGroupsAndMasksForType(IconType.trees);
		if (treesById == null || treesById.isEmpty())
		{
			Logger.println("Trees will not be drawn because no tree images were found.");
			return;
		}

		// Make the tree images small. I make them all the same height.
		int scaledHeight = (int) (averageCenterWidthBetweenNeighbors * treeScale);
		if (scaledHeight == 0)
		{
			// Don't draw trees if they would all be size zero.
			return;
		}

		// Scale the tree images. Note that images in treesById should not be modified because treesById is cached and so can be re-used.
		ListMap<String, Tuple2<BufferedImage, BufferedImage>> scaledTreesById = new ListMap<>();
		for (Map.Entry<String, List<Tuple2<BufferedImage, BufferedImage>>> entry : treesById.entrySet())
		{
			String groupName = entry.getKey();
			List<Tuple2<BufferedImage, BufferedImage>> imageGroup = entry.getValue();
			List<Tuple2<BufferedImage, BufferedImage>> scaledImageGroup = new ArrayList<>();
			scaledTreesById.put(groupName, scaledImageGroup);

			for (Tuple2<BufferedImage, BufferedImage> tuple : imageGroup)
			{
				scaledTreesById.add(
						groupName,
						new Tuple2<>(
								ImageCache.getInstance().getScaledImageByHeight(tuple.getFirst(), scaledHeight),
								ImageCache.getInstance().getScaledImageByHeight(tuple.getSecond(), scaledHeight)
						)
				);
			}
		}

		for (Center c : centersToDraw)
		{
			CenterTrees cTrees = trees.get(c.index);
			if (cTrees != null)
			{
				if (cTrees.treeType != null && scaledTreesById.containsKey(cTrees.treeType))
				{
					drawTreesAtCenterAndCorners(graph, cTrees.density, scaledTreesById.get(cTrees.treeType), c);
				}
			}
		}
	}

	private void drawTreesAtCenterAndCorners(WorldGraph graph, double density, List<Tuple2<BufferedImage, BufferedImage>> imagesAndMasks,
			Center center)
	{
		CenterTrees cTrees = trees.get(center.index);
		Random rand = new Random(cTrees.randomSeed);
		drawTrees(graph, imagesAndMasks, center.loc, density, center, rand);

		// Draw trees at the neighboring corners too.
		// Note that corners use their own Random instance because the random seed of that random needs to not depend on the center or else
		// trees would be placed differently based on which center drew first.
		for (Corner corner : center.corners)
		{
			if (shouldCenterDrawTreesForCorner(center, corner))
			{
				drawTrees(graph, imagesAndMasks, corner.loc, density, center, rand);
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

	private void drawTrees(WorldGraph graph, List<Tuple2<BufferedImage, BufferedImage>> imagesAndMasks, Point loc, double forestDensity,
			Center center, Random rand)
	{
		if (imagesAndMasks == null || imagesAndMasks.isEmpty())
		{
			return;
		}

		// Convert the forestDensity into an integer number of trees to draw such that the expected
		// value is forestDensity.
		double fraction = forestDensity - (int) forestDensity;
		int extra = rand.nextDouble() < fraction ? 1 : 0;
		int numTrees = ((int) forestDensity) + extra;

		for (int i = 0; i < numTrees; i++)
		{
			int index = rand.nextInt(imagesAndMasks.size());
			BufferedImage image = imagesAndMasks.get(index).getFirst();
			BufferedImage mask = imagesAndMasks.get(index).getSecond();

			// Draw the image such that it is centered in the center of c.
			int x = (int) (loc.x);
			int y = (int) (loc.y);

			double sqrtSize = Math.sqrt(averageCenterWidthBetweenNeighbors);
			x += rand.nextGaussian() * sqrtSize * 2.0;
			y += rand.nextGaussian() * sqrtSize * 2.0;

			iconsToDraw.getOrCreate(center)
					.add(new IconDrawTask(image, mask, IconType.trees, new Point(x, y), (int) image.getWidth(), false, false));
		}
	}

	private boolean isIconTouchingWater(IconDrawTask iconTask)
	{
		int imageUpperLeftX = (int) iconTask.centerLoc.x - iconTask.scaledWidth / 2;
		int imageUpperLeftY = (int) iconTask.centerLoc.y + iconTask.scaledHeight / 2;

		// Only check precision*precision points.
		float precision = Math.min(iconTask.scaledWidth, Math.min(iconTask.scaledHeight, 32));
		for (int x = 0; x < precision; x++)
		{
			for (int y = 0; y < precision; y++)
			{
				Center center = graph.findClosestCenter(
						imageUpperLeftX + (int) (iconTask.scaledWidth * (x / precision)),
						(imageUpperLeftY - (int) (iconTask.scaledHeight * (y / precision)))
				);
				if (center.isWater)
					return true;
			}
		}

		return false;
	}

	private static final int opaqueThreshold = 50;

	/**
	 * Generates a mask image for an icon. A mask is used when drawing an icon to determine which pixels that are transparent in the icon
	 * should draw the map background vs draw the icons already drawn behind that icon. If a pixel is transparent in the icon, and the
	 * corresponding pixel is white in the mask, then the map background is drawn for that pixel. But if the map pixel is black, then there
	 * is no special handling when drawing that pixel, so whatever was drawn in that place on the map before it will be visible.
	 * 
	 * @param icon
	 * @return
	 */
	public static BufferedImage createMask(BufferedImage icon)
	{
		// Top
		BufferedImage topSilhouette = new BufferedImage(icon.getWidth(), icon.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
		{
			List<Coordinate> points = new ArrayList<>();
			for (int x = 0; x < icon.getWidth(); x++)
			{
				Coordinate point = findUppermostOpaquePixel(icon, x);
				if (point != null)
				{
					if (points.isEmpty())
					{
						points.add(new Coordinate(x, icon.getHeight()));
					}
					points.add(point);
				}
			}

			if (points.size() < 3)
			{
				return new BufferedImage(icon.getWidth(), icon.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
			}
			points.add(new Coordinate(points.get(points.size() - 1).x, icon.getHeight()));
			drawWhitePolygonFromPoints(topSilhouette, points);
		}

		// Left side
		BufferedImage leftSilhouette = new BufferedImage(icon.getWidth(), icon.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
		{
			List<Coordinate> points = new ArrayList<>();
			for (int y = 0; y < icon.getHeight(); y++)
			{
				Coordinate point = findLeftmostOpaquePixel(icon, y);
				if (point != null)
				{
					if (points.isEmpty())
					{
						points.add(new Coordinate(icon.getWidth(), y));
					}
					points.add(point);
				}
			}
			points.add(new Coordinate(icon.getWidth(), points.get(points.size() - 1).y));
			drawWhitePolygonFromPoints(leftSilhouette, points);
		}

		// Right side
		BufferedImage rightSilhouette = new BufferedImage(icon.getWidth(), icon.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
		{
			List<Coordinate> points = new ArrayList<>();
			for (int y = 0; y < icon.getHeight(); y++)
			{
				Coordinate point = findRightmostOpaquePixel(icon, y);
				if (point != null)
				{
					if (points.isEmpty())
					{
						points.add(new Coordinate(0, y));
					}
					points.add(point);
				}
			}
			points.add(new Coordinate(0, points.get(points.size() - 1).y));
			drawWhitePolygonFromPoints(rightSilhouette, points);
		}

		// The mask image is a resolve of the intersection of the 3 silhouettes.

		BufferedImage mask = new BufferedImage(icon.getWidth(), icon.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
		WritableRaster maskRaster = mask.getRaster();
		Raster topRaster = topSilhouette.getRaster();
		Raster leftRaster = leftSilhouette.getRaster();
		Raster rightRaster = rightSilhouette.getRaster();
		for (int x = 0; x < mask.getWidth(); x++)
		{
			for (int y = 0; y < mask.getHeight(); y++)
			{
				if (topRaster.getSample(x, y, 0) > 0 && leftRaster.getSample(x, y, 0) > 0 && rightRaster.getSample(x, y, 0) > 0)
				{
					maskRaster.setSample(x, y, 0, 255);
				}
			}
		}

		return mask;
	}

	private static void drawWhitePolygonFromPoints(BufferedImage image, List<Coordinate> points)
	{
		int[] xPoints = new int[points.size()];
		int[] yPoints = new int[points.size()];
		for (int i : new Range(points.size()))
		{
			Coordinate point = points.get(i);
			xPoints[i] = point.x;
			yPoints[i] = point.y;
		}

		Graphics2D g = image.createGraphics();
		g.setColor(Color.white);
		g.fillPolygon(xPoints, yPoints, xPoints.length);
	}

	private static Coordinate findUppermostOpaquePixel(BufferedImage icon, int x)
	{
		for (int y = 0; y < icon.getHeight(); y++)
		{
			int alpha = ImageHelper.getAlphaLevel(icon, x, y);
			if (alpha >= opaqueThreshold)
			{
				return new Coordinate(x, y);
			}
		}

		return null;
	}

	private static Coordinate findLeftmostOpaquePixel(BufferedImage icon, int y)
	{
		for (int x = 0; x < icon.getWidth(); x++)
		{
			int alpha = ImageHelper.getAlphaLevel(icon, x, y);
			if (alpha >= opaqueThreshold)
			{
				return new Coordinate(x, y);
			}
		}

		return null;
	}

	private static Coordinate findRightmostOpaquePixel(BufferedImage icon, int y)
	{
		for (int x = icon.getWidth() - 1; x >= 0; x--)
		{
			int alpha = ImageHelper.getAlphaLevel(icon, x, y);
			if (alpha >= opaqueThreshold)
			{
				return new Coordinate(x, y);
			}
		}

		return null;
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
		if (iconsToDraw.get(center) == null)
		{
			return null;
		}

		Rectangle bounds = null;
		for (IconDrawTask iconTask : iconsToDraw.get(center))
		{
			if (bounds == null)
			{
				bounds = iconTask.createBounds();
			}
			else
			{
				bounds.add(iconTask.createBounds());
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
					bounds.add(b);
				}
			}
		}
		return bounds;
	}
}
