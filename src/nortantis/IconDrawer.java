package nortantis;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import hoten.geom.Point;
import hoten.voronoi.Center;
import hoten.voronoi.Corner;
import hoten.voronoi.VoronoiGraph;
import nortantis.Biome;
import nortantis.editor.CenterEdit;
import nortantis.editor.MapEdits;
import nortantis.util.AssetsPath;
import nortantis.util.Coordinate;
import nortantis.util.Function;
import nortantis.util.HashMapF;
import nortantis.util.Helper;
import nortantis.util.ImageHelper;
import nortantis.util.ListMap;
import nortantis.util.Logger;
import nortantis.util.Pair;
import nortantis.util.ProbabilityHelper;
import nortantis.util.Range;
import nortantis.util.Tuple2;
import nortantis.util.Tuple3;

public class IconDrawer 
{
	public static final double mountainElevationThreshold = 0.58;
	public static final double hillElevationThreshold = 0.53;
	final double treeScale = 4.0/8.0;
	public final static String mountainsName = "mountains";
	public final static String hillsName = "hills";
	public final static String sandDunesName = "sand";
	public final static String treesName = "trees";
	public final static String citiesName = "cities";
	double meanPolygonWidth;
	// If a polygon is this number times meanPolygonWidth wide, no icon will be drawn on it.
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
	GraphImpl graph;
	Random rand;
	/**
	 * Used to store icons drawn when generating icons so they can be edited later by the editor.
	 */
	public Map<Integer, CenterIcon> centerIcons;
	public Map<Integer, CenterTrees> trees;
	private String cityIconsSetName;

	public IconDrawer(GraphImpl graph, Random rand, String cityIconsSetName)
	{
		iconsToDraw = new HashMapF<>(() -> new ArrayList<>(1));
		this.graph = graph;
		this.rand = rand;
		this.cityIconsSetName = cityIconsSetName;
		
		meanPolygonWidth = findMeanPolygonWidth(graph);
		maxSizeToDrawIcon = meanPolygonWidth * maxMeansToDraw;
		centerIcons = new HashMap<>();
		trees = new HashMap<>();
	}

	public static double findMeanPolygonWidth(GraphImpl graph)
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

	public void markMountains()
	{
		for (Center c : graph.centers)
		{
			if (c.elevation > mountainElevationThreshold
					&& !c.isCoast && !c.isBorder && c.findWidth() < maxSizeToDrawIcon)
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
					&& !c.isCoast && c.findWidth() < maxSizeToDrawIcon)
				
			{
				c.isHill = true;
			}
		}
	}
	
	public void markCities(double cityProbability)
	{
//		Set<Center> rivers = new HashSet<>();
//		Set<Center> coast = new HashSet<>();
//		findRiversAndCoast(rivers, coast); // TODO decide if I need this and delete it if I don't.
		for (Center c : graph.centers)
		{
			// TODO figure out how to make the cities draw on coasts and not draw right on top of rivers.
			if (!c.isMountain && !c.isHill && !c.isWater)
			{
				if (c.isRiver() && rand.nextDouble() <= cityProbability*2)
				{
					c.isCity = true;
				}
				else if (c.isCoast && rand.nextDouble() <= cityProbability*2)
				{
					c.isCity = true;
				}
				else if (rand.nextDouble() <= cityProbability)
				{
					c.isCity = true;
				}
			}
		}
	}
	
	// TODO remove if not needed
//	private void findRiversAndCoast(Set<Center> rivers, Set<Center> coast)
//	{
//		for (Center c : graph.centers)
//		{
//			if (c. >= VoronoiGraph.riversThinnerThanThisWillNotBeDrawn)
//			{
//				rivers.add(c);
//			}
//			
//			if (c.isCoast)
//			{
//				coast.add(c);
//			}
//		}
//	}
	
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
	 * This is used to add icon to draw tasks from map edits rather than using the generator to add them.
	 */
	public void clearAndAddIconsFromEdits(MapEdits edits, double sizeMultiplyer)
	{
		iconsToDraw.clear();
		ListMap<String, Tuple2<BufferedImage, BufferedImage>> mountainImagesById = getAllIconGroupsAndMasksForType(mountainsName);
		ListMap<String, Tuple2<BufferedImage, BufferedImage>> hillImagesById = getAllIconGroupsAndMasksForType(hillsName);
		List<Tuple2<BufferedImage, BufferedImage>> duneImages = getAllIconGroupsAndMasksForType(sandDunesName).get("dunes");
		int duneWidth = findDuneWidth();
		Map<String, Tuple3<BufferedImage, BufferedImage, Integer>> cityImages = loadIconsWithWidths(citiesName);
		
		trees.clear();
		
		for (CenterEdit cEdit : edits.centerEdits)
		{
			Center center = graph.centers.get(cEdit.index);
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
						BufferedImage mountainImage = mountainImagesById.get(groupId).get(
								cEdit.icon.iconIndex % mountainImagesById.get(groupId).size()).getFirst();
						BufferedImage mask = mountainImagesById.get(groupId).get(
								cEdit.icon.iconIndex % mountainImagesById.get(groupId).size()).getSecond();
						iconsToDraw.getOrCreate(center).add(new IconDrawTask(mountainImage, 
			       				mask, center.loc, scaledSize, true, false));
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
						BufferedImage hillImage = hillImagesById.get(groupId).get(
								cEdit.icon.iconIndex % hillImagesById.get(groupId).size()).getFirst();
						BufferedImage mask = hillImagesById.get(groupId).get(
								cEdit.icon.iconIndex % hillImagesById.get(groupId).size()).getSecond();
						iconsToDraw.getOrCreate(center).add(new IconDrawTask(hillImage, 
			       				mask, center.loc, scaledSize, true, false));	
					}
				}
				else if (cEdit.icon.iconType == CenterIconType.Dune && duneWidth > 0 && duneImages != null && !duneImages.isEmpty())
				{
					BufferedImage duneImage = duneImages.get(
							cEdit.icon.iconIndex % duneImages.size()).getFirst();
					BufferedImage mask = duneImages.get(
							cEdit.icon.iconIndex % duneImages.size()).getSecond();
					iconsToDraw.getOrCreate(center).add(new IconDrawTask(duneImage, 
		       				mask, center.loc, duneWidth, true, false));								
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
						cityIconName = chooseNewGroupId(cityImages.keySet(), cEdit.icon.iconName);
					}
					if (cityIconName != null)
					{
						cityImage = cityImages.get(cityIconName).getFirst();
						mask = cityImages.get(cityIconName).getSecond();
						iconsToDraw.getOrCreate(center).add(
								new IconDrawTask(cityImage, mask, center.loc, 
										(int)(cityImages.get(cityIconName).getThird() * sizeMultiplyer), true, true));
					}
				}

			}
			
			if (cEdit.trees != null)
			{
				trees.put(cEdit.index, cEdit.trees);
			}
		}

		drawTreesForAllCenters();
	}
	
	private String chooseNewGroupId(Set<String> groupIds, String oldGroupId)
	{
		int randomIndex = Math.abs(oldGroupId.hashCode() % groupIds.size());
		return groupIds.toArray(new String[groupIds.size()])[randomIndex];
	}

	/**
	 * Finds groups of centers that accepted according to a given function. A group is a set of centers
	 * for which there exists a path from any member of the set to any other such that you
	 * never have to skip over more than maxGapSize centers not accepted at once
	 * to get to that other center. If distanceThreshold > 1, the result will include those
	 * centers which connect centeres that are accepted.
	 */
	private static List<Set<Center>> findCenterGroups(GraphImpl graph, int maxGapSize,
			Function<Center, Boolean> accept)
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
=	 * @param mask A gray scale image which is white where the background should be drawn, and
	 * black where the map should be drawn instead of the background. This is necessary so that
	 * when I draw an icon that is transparent (such as a hand drawn mountain), I cannot see
	 * other mountains through it.
	 */
	private void drawIconWithBackgroundAndMask(BufferedImage map, BufferedImage icon, 
			BufferedImage mask, BufferedImage background, int xCenter, int yCenter, boolean ignoreMaxSize)
	{   	
    	if (map.getWidth() != background.getWidth())
    		throw new IllegalArgumentException();
       	if (map.getHeight() != background.getHeight())
    		throw new IllegalArgumentException();
       	if (mask.getWidth() != icon.getWidth())
       		throw new IllegalArgumentException("The given mask's width does not match the icon' width.");
       	if (mask.getHeight() != icon.getHeight())
       		throw new IllegalArgumentException("The given mask's height does not match the icon' height.");
       	
       	if (!ignoreMaxSize && icon.getWidth() > maxSizeToDrawIcon)
       		return;
       	       	      	
      	int xLeft = xCenter - icon.getWidth()/2;
      	int yBottom = yCenter - icon.getHeight()/2;
      	
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
					bgColor = new Color(background.getRGB(xLoc, yLoc));
					mapColor = new Color(map.getRGB(xLoc, yLoc));
				}
				catch (IndexOutOfBoundsException e)
				{
					// Skip this pixel.
					continue;
				}
				
				int red = (int)(alpha * (iconColor.getRed()) + (1 - alpha) * (maskLevel * bgColor.getRed() + (1 - maskLevel) * mapColor.getRed()));
				int green = (int)(alpha * (iconColor.getGreen()) + (1 - alpha) * (maskLevel * bgColor.getGreen() + (1 - maskLevel) * mapColor.getGreen()));
				int blue = (int)(alpha * (iconColor.getBlue()) + (1 - alpha) * (maskLevel * bgColor.getBlue() + (1 - maskLevel) * mapColor.getBlue()));
				
				map.setRGB(xLoc, yLoc, new Color(red, green, blue).getRGB());
			}
	}

	/**
	 * Draws all icons in iconsToDraw. I draw all the icons at once this way so that I can sort
	 * the icons by the y-coordinate of the base of each icon. This way icons lower on the map
	 * are drawn in front of those that are higher.
	 */
	public void drawAllIcons(BufferedImage map, BufferedImage background)
	{	
		List<IconDrawTask> tasks = new ArrayList<IconDrawTask>(iconsToDraw.size());
		for (Map.Entry<Center, List<IconDrawTask>> entry : iconsToDraw.entrySet())
		{
			if (!entry.getKey().isWater)
			{
				tasks.addAll(entry.getValue());
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
		Helper.processInParallel(jobs);
		
		for (final IconDrawTask task : tasks)
		{
			if (!isIconTouchingWater(task))
			{
				drawIconWithBackgroundAndMask(map, task.icon, task.mask, background, (int)task.centerLoc.x,
						(int)task.centerLoc.y, task.ignoreMaxSize);
			}
		}		
	}
	
	/**
	 * Adds icon draw tasks to draw cities.
	 * @return IconDrawTask of each city icon added. Needed to void drawing text on top of cities.
	 */
	public List<IconDrawTask> addCities(double sizeMultiplyer, boolean addIconDrawTasks)
	{
		Map<String, Tuple3<BufferedImage, BufferedImage, Integer>> cityIcons = loadIconsWithWidths(citiesName);
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
				int scaledWidth = (int)(cityIcons.get(cityName).getThird() * sizeMultiplyer);
				BufferedImage icon = cityIcons.get(cityName).getFirst();
				
				IconDrawTask task = new IconDrawTask(icon, cityIcons.get(cityName).getSecond(), c.loc, scaledWidth, true, true);
				if (!isIconTouchingWater(task))
				{
					if (addIconDrawTasks)
					{
						iconsToDraw.getOrCreate(c).add(task);
		           		centerIcons.put(c.index, new CenterIcon(CenterIconType.City, cityName));
					}
	           		
	    	   		cities.add(task); 
				}
			}
		}
		
		return cities;
	}

	/**
	 * Creates tasks for drawing mountains and hills.
	 * @return
	 */
	public List<Set<Center>> addMountainsAndHills(List<Set<Center>> mountainGroups, List<Set<Center>> mountainAndHillGroups)
	{				
        // Maps mountain range ids (the ids in the file names) to list of mountain images and their masks.
        ListMap<String, Tuple2<BufferedImage, BufferedImage>> mountainImagesById = getAllIconGroupsAndMasksForType(mountainsName);
        if (mountainImagesById == null || mountainImagesById.isEmpty())
        {
        	Logger.println("No mountain images were found. Mountain images will not be drawn.");
        	return mountainGroups;
        }

        // Maps mountain range ids (the ids in the file names) to list of hill images and their masks.
        // The hill image file names must use the same ids as the mountain ranges.
        ListMap<String, Tuple2<BufferedImage, BufferedImage>> hillImagesById = getAllIconGroupsAndMasksForType(hillsName);
        
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

        // Maps from the mountainRangeId of Centers to the range id's from the mountain image file names.
        Map<Integer, String> rangeMap = new TreeMap<>();
        
        for (Set<Center> group : mountainAndHillGroups)
        {
        	for (Center c : group)
        	{	
	        	String filenameRangeId = rangeMap.get(c.mountainRangeId);
	        	if ((filenameRangeId == null))
	        	{
	        		filenameRangeId =  new ArrayList<>(mountainImagesById.keySet()).get(
	        				rand.nextInt(mountainImagesById.keySet().size()));
	        		rangeMap.put(c.mountainRangeId, filenameRangeId);
	        	}

	        	if (c.isMountain)
	        	{        		
		        	List<Tuple2<BufferedImage, BufferedImage>> imagesInRange =
		        			mountainImagesById.get(filenameRangeId);


		        	// I'm deliberatly putting this line before checking center size so that the
		        	// random number generator is used the same no matter what resolution the map
		        	// is drawn at.
	           		int i = rand.nextInt(imagesInRange.size());

	           		int scaledSize = findScaledMountainSize(c);

	           		// Make sure the image will be at least 1 pixel wide.
		           	if (scaledSize >= 1)
		           	{	
			           	// Draw the image such that it is centered in the center of c.
		           		iconsToDraw.getOrCreate(c).add(new IconDrawTask(imagesInRange.get(i).getFirst(), 
		           				imagesInRange.get(i).getSecond(), c.loc, scaledSize, true, false));
		           		centerIcons.put(c.index, new CenterIcon(CenterIconType.Mountain, filenameRangeId, i));
		           	}
		        }
	         	else if (c.isHill)
	         	{
		        	List<Tuple2<BufferedImage, BufferedImage>> imagesInGroup = 
		        			hillImagesById.get(filenameRangeId);
		        	
		        	if (imagesInGroup != null && !imagesInGroup.isEmpty())
		        	{	        		
			        	int i = rand.nextInt(imagesInGroup.size());

		           		int scaledSize = findScaledHillSize(c);
			        	
		           		// Make sure the image will be at least 1 pixel wide.
			           	if (scaledSize >= 1)
			           	{
			           		iconsToDraw.getOrCreate(c).add(new IconDrawTask(imagesInGroup.get(i).getFirst(), 
			           				imagesInGroup.get(i).getSecond(), c.loc, scaledSize, true, false));
			           		centerIcons.put(c.index, new CenterIcon(CenterIconType.Hill, filenameRangeId, i));
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
    	return (int)(cSize * mountainScale);
	}
	
	private int findScaledHillSize(Center c)
	{
		// Find the center's size along the x axis.
    	double cSize = findCenterWidthBetweenNeighbors(c);
    	return (int)(cSize * hillScale);
	}
	
	public void addSandDunes()
	{
		ListMap<String, Tuple2<BufferedImage, BufferedImage>> sandGroups = getAllIconGroupsAndMasksForType("sand");
		if (sandGroups == null || sandGroups.isEmpty())
		{
			Logger.println("Sand dunes will not be drawn because no sand images were found.");
			return;
		}
		
        // Load the sand dune images.
        List<Tuple2<BufferedImage, BufferedImage>> duneImages = sandGroups.get("sand");
        
        if (duneImages == null || duneImages.isEmpty())
        {
			Logger.println("Sand dunes will not be drawn because no sand dune images were found.");
			return;
        }
        
   		List<Set<Center>> groups = findCenterGroups(graph, maxGapBetweenBiomeGroups, 
				new Function<Center, Boolean>()
				{
					public Boolean apply(Center center)
					{
						return center.biome.equals(Biome.TEMPERATE_DESERT);
					}
				});
   		
   		// This is the probability that a temperate desert will be a dune field.
   		double duneProbabilityPerBiomeGroup = 0.6;
   		double duneProbabilityPerCenter = 0.5;
   		
		int width = findDuneWidth();
		if (width == 0)
			return;
		
   		
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
		           		iconsToDraw.getOrCreate(c).add(new IconDrawTask(duneImages.get(i).getFirst(), 
		           				duneImages.get(i).getSecond(), c.loc, width, true, false));
		           		centerIcons.put(c.index, new CenterIcon(CenterIconType.Dune, "sand", i));
					}
				}
				
			}
		}
	}
	
	private int findDuneWidth()
	{
		double averageWidth = findMeanPolygonWidth(graph);
		return (int)(averageWidth * 1.5);
	}
		
	public void addTrees() throws IOException
	{
		addCenterTrees();
		drawTreesForAllCenters();
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
	
	private static List<ForestType> forestTypes;
	static
	{
		forestTypes = new ArrayList<>();
        forestTypes.add(new ForestType(TreeType.Deciduous, Biome.TEMPERATE_RAIN_FOREST, 0.5, 1.0));
        forestTypes.add(new ForestType(TreeType.Pine, Biome.TAIGA, 1.0, 1.0));
        forestTypes.add(new ForestType(TreeType.Pine, Biome.SHRUBLAND, 1.0, 1.0));
        forestTypes.add(new ForestType(TreeType.Pine, Biome.HIGH_TEMPERATE_DECIDUOUS_FOREST, 1.0, 0.25));
        forestTypes.add(new ForestType(TreeType.Cacti, Biome.HIGH_TEMPERATE_DESERT, 1.0/8.0, 0.1));
	}
	
	private void addCenterTrees()
	{	
		trees.clear();
        
        for (final ForestType forest : forestTypes)
        {
        	if (forest.biomeFrequency != 1.0)
        	{
        		List<Set<Center>> groups = findCenterGroups(graph, maxGapBetweenBiomeGroups, 
        				new Function<Center, Boolean>()
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
	public void drawTreesForAllCenters()
	{
		// Find the average width of all Centers.
		double sum = 0;
		for (Center c : graph.centers)
		{
			sum += findCenterWidthBetweenNeighbors(c);
		}
		double avgHeight = sum / graph.centers.size();
		
	       // Load the images and masks.
        ListMap<String, Tuple2<BufferedImage, BufferedImage>> treesById = getAllIconGroupsAndMasksForType(treesName);
        if (treesById == null || treesById.isEmpty())
        {
			Logger.println("Trees will not be drawn because no tree images were found.");
			return;
        }
        
      	// Make the tree images small. I make them all the same height.
        int scaledHeight = (int)(avgHeight * treeScale);
       	if (scaledHeight == 0)
       	{
       		// Don't draw trees if they would all be size zero.
       		return;
       	}
        for (List<Tuple2<BufferedImage, BufferedImage>> imageGroup: treesById.values())
        {
        	for (Tuple2<BufferedImage, BufferedImage> tuple : imageGroup)
        	{
		       	tuple.setFirst(ImageHelper.scaleByHeight(tuple.getFirst(), scaledHeight));
		       	tuple.setSecond(ImageHelper.scaleByHeight(tuple.getSecond(), scaledHeight));
        	}
        }
		
         // Store which corners have had trees drawn so that I don't draw them multiple times.
        boolean[] cornersWithTreesDrawn = new boolean[graph.corners.size()];
        
        for (Center c : graph.centers)
        {
        	CenterTrees cTrees = trees.get(c.index);
        	if (cTrees != null)
        	{
        		if (cTrees.treeType != null && treesById.containsKey(cTrees.treeType))
        		{
		        	drawTreesAtCenterAndCorners(graph, cTrees.density, treesById.get(cTrees.treeType), avgHeight,
								cornersWithTreesDrawn, c, cTrees.randomSeed);
	        		}
        	}
        }
	}

	private void drawTreesAtCenterAndCorners(GraphImpl graph,
			double density, List<Tuple2<BufferedImage, BufferedImage>> imagesAndMasks, double avgCenterHeight, 
			boolean[] cornersWithTreesDrawn, Center center, long randomSeed)
	{
		Random rand = new Random(randomSeed);
		drawTrees(graph, imagesAndMasks, avgCenterHeight, center.loc, density, center, rand);
			
		// Draw trees at the neighboring corners too.
		for (Corner corner : center.corners)
		{
			if (!cornersWithTreesDrawn[corner.index])
			{
				drawTrees(graph, imagesAndMasks, avgCenterHeight, corner.loc,
						density, center, rand);
				cornersWithTreesDrawn[corner.index] = true;
			}
		}
	}
	
	private static class ForestType
	{
		TreeType treeType;
		Biome biome;
		double density;
		double biomeFrequency;
		
		/**
		 * @param biomeProb If this is not 1.0, groups of centers of biome type "biome" will be found
		 * and each groups will have this type of forest with probability biomeProb.
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

	private void drawTrees(GraphImpl graph,
			List<Tuple2<BufferedImage, BufferedImage>> imagesAndMasks, double cSize, Point loc,
			double forestDensity, Center center, Random rand)
	{
		if (imagesAndMasks == null || imagesAndMasks.isEmpty())
		{
			return;
		}
		
		// Convert the forestDensity into an integer number of trees to draw such that the expected
		// value is forestDensity.
		double fraction = forestDensity - (int)forestDensity;
		int extra = rand.nextDouble() < fraction ? 1 : 0;
		int numTrees = ((int)forestDensity) + extra;
		       	
       	for (int i = 0; i < numTrees; i++)
       	{
       		int index = rand.nextInt(imagesAndMasks.size());
       		BufferedImage image = imagesAndMasks.get(index).getFirst();
       		BufferedImage mask = imagesAndMasks.get(index).getSecond();
           	     
           	// Draw the image such that it is centered in the center of c.
           	int x = (int) (loc.x);
           	int y = (int) (loc.y);
           	
           	double sqrtSize = Math.sqrt(cSize);
           	x += rand.nextGaussian() * sqrtSize*2.0;
           	y += rand.nextGaussian() * sqrtSize*2.0;
        	
           	iconsToDraw.getOrCreate(center).add(new IconDrawTask(image, mask, new Point(x, y), (int)image.getWidth(), false, false));	           	
       	}
	}
	
	private boolean isIconTouchingWater(IconDrawTask iconTask)
	{       	
       	int imageUpperLeftX = (int)iconTask.centerLoc.x - iconTask.scaledWidth/2;
       	int imageUpperLeftY = (int)iconTask.centerLoc.y + iconTask.scaledHeight/2;
       	
       	// Only check precision*precision points.
       	float precision = Math.min(iconTask.scaledWidth, Math.min(iconTask.scaledHeight, 32));
       	for (int x = 0; x < precision; x++)
       	{
       		for (int y = 0; y < precision; y++)
       		{
       			Center center = graph.findClosestCenter(imageUpperLeftX + (int)(iconTask.scaledWidth * (x/precision)), 
       					(imageUpperLeftY - (int)(iconTask.scaledHeight * (y/precision))));
       	       	if (center.isWater)
       	       		return true;
       		}
       	}
       
       	return false;
	}
	
	/**
	 * Loads icons which do not have groups, but which do have default widths in the file names.
	 * 
	 * @return A map from icon names (not including width or extension) to a tuple with the icon, mask, and width.
	 */
	private Map<String, Tuple3<BufferedImage, BufferedImage, Integer>> loadIconsWithWidths(final String iconType)
	{
		Map<String, Tuple3<BufferedImage, BufferedImage, Integer>> imagesAndMasks = new HashMap<>();
		String[] fileNames = getIconGroupFileNames(iconType, null, getIconSetName(iconType));
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

			Path path = Paths.get(getIconGroupPath(iconType, null, getIconSetName(iconType)), fileName);
			if (!ImageCache.getInstance().containsImageFile(path))
			{
				Logger.println("Loading icon: " + path);
			}
			BufferedImage icon;
			BufferedImage mask;
			
			icon = ImageCache.getInstance().getImageFromFile(path);
			mask = ImageCache.getInstance().getOrCreateImage("mask " + path.toString(), () -> createMask(icon));
			
			
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
	
	/**
	 * Loads groups if icons, using iconType as a key word to filter on. 
	 * The second image in the tuples is the mask, which is generated based on the image loaded from disk.
	 */
	private ListMap<String, Tuple2<BufferedImage, BufferedImage>> getAllIconGroupsAndMasksForType(final String iconType)
	{
		ListMap<String, Tuple2<BufferedImage, BufferedImage>> imagesPerGroup = new ListMap<>();

		String[] groupNames = getIconGroupNames(iconType);
		for (String groupName : groupNames)
		{
			String[] fileNames = getIconGroupFileNames(iconType, groupName, getIconSetName(iconType));
			String groupPath = getIconGroupPath(iconType, groupName, getIconSetName(iconType));
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
				mask = ImageCache.getInstance().getOrCreateImage("mask " + path.toString(), () -> createMask(icon));
	
				imagesPerGroup.add(groupName, new Tuple2<>(icon, mask));
			}
		}
		return imagesPerGroup;
	}
	
	private String getIconSetName(String iconType)
	{
		if (iconType.equals(citiesName))
		{
			return cityIconsSetName;
		}
		return null;
	}
	
	public static Set<String> getIconSets(String iconType)
	{
		if (!doesUseSets(iconType))
		{
			throw new RuntimeException("Type '" + iconType + "' does not use sets.");
		}
		
		String path = Paths.get(AssetsPath.get(), "icons", iconType).toString();
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
	
	public static Set<String> getIconGroupFileNamesWithoutWidthOrExtension(String iconType, String groupName, String cityIconSetName)
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
	public static String[] getIconGroupNames(String iconType, String setName)
	{
		String path;
		if (doesUseSets(iconType))
		{
			if (setName == null || setName.isEmpty())
			{
				return new String[] {};
			}
			path = Paths.get(AssetsPath.get(), "icons", iconType, setName).toString();
		}
		else
		{
			path = Paths.get(AssetsPath.get(), "icons", iconType).toString();
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
	
	public String[] getIconGroupNames(String iconType)
	{
		String setName = getIconSetName(iconType);
		return getIconGroupNames(iconType, setName);
	}
	
	public static String[] getIconGroupFileNames(String iconType, String groupName, String setName)
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
	
	public static String getIconGroupPath(String iconType, String groupName, String setName)
	{
		String path;
		if (iconType.equals(citiesName))
		{
			path = Paths.get(AssetsPath.get(), "icons", iconType, setName).toString();
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
				path = Paths.get(AssetsPath.get(), "icons", iconType, groupName, setName).toString(); 
			}
			else
			{
				path = Paths.get(AssetsPath.get(), "icons", iconType, groupName).toString();
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
	private static boolean doesUseSets(String iconType)
	{
		return iconType.equals(citiesName);
	}
	
	private static final int opaqueThreshold = 50;
	
	/**
	 * Generates a mask image for an icon. A mask is used when drawing an icon to determine which pixels that are transparent in the icon
	 * should draw the map background vs draw the icons already drawn behind that icon. If a pixel is transparent in the icon, and the
	 * corresponding pixel is white in the mask, then the map background is drawn for that pixel. But if the map pixel is black, then there
	 * is no special handling when drawing that pixel, so whatever was drawn in that place on the map before it will be visible. 
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
		Graphics2D g = mask.createGraphics();
		g.setColor(Color.white);
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
		for (int x = icon.getWidth() - 1; x >= 0 ; x--)
		{
			int alpha = ImageHelper.getAlphaLevel(icon, x, y);
			if (alpha >= opaqueThreshold)
			{
				return new Coordinate(x, y);
			}
		}
		
		return null;
	}
}
