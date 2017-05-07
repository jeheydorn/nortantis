package nortantis;

import hoten.geom.Point;
import hoten.voronoi.Center;
import hoten.voronoi.Corner;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
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
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import javax.imageio.ImageIO;

import nortantis.GraphImpl.ColorData;

import org.apache.commons.io.FilenameUtils;

import util.Function;
import util.Helper;
import util.ImageHelper;
import util.ListMap;
import util.Logger;
import util.Pair;
import util.Range;
import util.Tuple2;

public class MapCreator
{
	final double mountainElevationThreshold = 0.58;
	final double hillElevationThreshold = 0.53;
	final double treeScale = 4.0/8.0;
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
	private final double regionBlurColorScale = 0.7;

	
	private List<IconDrawTask> iconsToDraw;
	
	private Random r;
	// This is a base width for determining how large to draw text and effects.
	private final double baseResolution = 1536;
	
	public MapCreator()
	{
		iconsToDraw = new ArrayList<>();
	}

	/**
	 * 
	 * @param settings
	 * @param maxDimensions The maximum width and height (in pixels) at which to draw the map.
	 * This is needed for creating previews. null means draw at normal resolution. Warning: If 
	 * maxDimensions is specified, then settings.resolution will be modified to fit that size.
	 * @param mapParts If not null, then parts of the map created while generating will be stored in it.
	 * @return
	 */
	public BufferedImage createMap(final MapSettings settings, Dimension maxDimensions, MapParts mapParts)
			throws IOException
	{		
		if (!Files.exists(Paths.get(settings.landBackgroundImage)))
			throw new IllegalArgumentException("Land background image file does not exists: " + settings.landBackgroundImage);
		if (!Files.exists(Paths.get(settings.oceanBackgroundImage)))
			throw new IllegalArgumentException("Ocean background image file does not exists: " + settings.oceanBackgroundImage);
		
		double startTime = System.currentTimeMillis();				
						
        Logger.println("Seed: " + settings.randomSeed);
        r = new Random(settings.randomSeed);
        
        boolean shouldDrawRegionColors = settings.drawRegionColors && settings.generateBackground;

		
		BufferedImage land;
		BufferedImage ocean;
		DimensionDouble bounds;
		BufferedImage fractalBG = null;
		if (settings.generateBackground)
		{
			bounds = new DimensionDouble(settings.generatedWidth * settings.resolution,
					settings.generatedHeight * settings.resolution);
			if (maxDimensions != null)
			{
				DimensionDouble newBounds = ImageHelper.fitDimensionsWithinBoundingBox(maxDimensions, bounds.getWidth(),
						bounds.getHeight());
				// Change the resolution to match the new bounds.
				settings.resolution *= newBounds.width / bounds.width;
				bounds = newBounds;
			}			
			
			fractalBG = FractalBGGenerator.generate(
					new Random(settings.backgroundRandomSeed), settings.fractalPower, 
					(int)bounds.getWidth(), (int)bounds.getHeight(), 0.75f);
			ocean = ImageHelper.colorify2(fractalBG, settings.oceanColor);
			if (!shouldDrawRegionColors)
			{
				land = ImageHelper.colorify2(fractalBG, settings.landColor);
				fractalBG = null;
			}
			else
			{
				// Drawing region colors must be done later because it depends on the graph.
				land = null; // To make the compiler not complain.
			}
		}
		else
		{
			try
			{
				land = ImageIO.read(new File(settings.landBackgroundImage));
				land = ImageHelper.convertToBufferedImageOfType(land, BufferedImage.TYPE_INT_RGB);
			}
			catch(IOException e)
			{
				throw new IllegalArgumentException("Cannot read land background image from " 
						+ settings.landBackgroundImage);
			}
			try
			{
				ocean = ImageIO.read(new File(settings.oceanBackgroundImage));
				ocean = ImageHelper.convertToBufferedImageOfType(ocean, BufferedImage.TYPE_INT_RGB);
			}
			catch(IOException e)
			{
				throw new IllegalArgumentException("Cannot read ocean background image from " 
						+ settings.oceanBackgroundImage);
			}

			bounds = new DimensionDouble(land.getWidth()*settings.resolution, land.getHeight()*settings.resolution);
			if (maxDimensions != null)
			{
				bounds = ImageHelper.fitDimensionsWithinBoundingBox(maxDimensions, bounds.getWidth(),
						bounds.getHeight());
			}
			land = ImageHelper.scaleByWidth(land, (int)bounds.getWidth());
			ocean = ImageHelper.scaleByWidth(ocean, (int)bounds.getWidth());
		}
		
		double sizeMultiplyer = (bounds.getWidth() / baseResolution);
		
		
		TextDrawer textDrawer = settings.drawText || mapParts != null ? new TextDrawer(settings, sizeMultiplyer) : null;
		if (mapParts != null)
			mapParts.textDrawer = textDrawer;
		
		GraphImpl graph = GraphCreator.createGraph(bounds.getWidth(), bounds.getHeight(),
				settings.worldSize, settings.edgeLandToWaterProbability, settings.centerLandToWaterProbability,
				new Random(r.nextLong()),
				sizeMultiplyer);	
		if (mapParts != null)
			mapParts.graph = graph;
		
		// regionIndexes is a gray scale image where the level of each pixel is the index of the region it is in.
		BufferedImage regionIndexes = null;
		
		if (shouldDrawRegionColors)
		{
			assignRandomRegionColors(graph, settings);
			
			regionIndexes = new BufferedImage(fractalBG.getWidth(), fractalBG.getHeight(), 
					BufferedImage.TYPE_BYTE_GRAY);
			graph.drawRegionIndexes(regionIndexes.createGraphics());
			
			land = drawRegionColors(graph, fractalBG, regionIndexes);
		}
		fractalBG = null;
		
		// Find the mean polygon width.
		meanPolygonWidth = findMeanPolygonWidth(graph);
		maxSizeToDrawIcon = meanPolygonWidth * maxMeansToDraw;

		markMountains(graph);
		markHills(graph);
		findMountainAndHillGroups(graph);

		// Draw mask for land vs ocean.
		Logger.println("Adding land.");
		BufferedImage landMask = new BufferedImage(graph.getWidth(),
				graph.getHeight(), BufferedImage.TYPE_BYTE_BINARY); 
		{
			Graphics2D g = landMask.createGraphics();
			graph.paint(g, false, false, false, true, true, false, false);
		}

		BufferedImage map = null;
		{
			if (!settings.generateBackground)
			{
				land = ImageHelper.scaleByWidth(land, graph.getWidth());
			}
	
			// Combine land and ocean images.
			map = ImageHelper.maskWithColor(land, Color.black, landMask, false);
		}
		land = null;
		
		Logger.println("Creating coastlines.");
		BufferedImage coastlineMask = new BufferedImage(graph.getWidth(),
				graph.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
		{
			Graphics2D g = coastlineMask.createGraphics();
			graph.paint(g, false, false, false, false, false, true, false, sizeMultiplyer);
		}
		
		// Darken the land next to coast lines and optionally region borders.
		{
			BufferedImage landBlur;
			int blurLevel = (int) (settings.landBlur * sizeMultiplyer);
			if (blurLevel > 0)
			{
				Logger.println("Darkening land near shores.");
				float[][] kernel = ImageHelper.createGaussianKernel(blurLevel);
				
				if (shouldDrawRegionColors)
				{
					BufferedImage coastlineAndRegionBorders = ImageHelper.deepCopy(coastlineMask);
					Graphics2D g = coastlineAndRegionBorders.createGraphics();
					g.setColor(Color.white);
					graph.drawRegionBorders(g, sizeMultiplyer, false);
					landBlur = ImageHelper.convolveGrayscale(coastlineAndRegionBorders, kernel, true);
					// Remove the land blur from the ocean side of the borders and color the blur
					// according to each region's blur color.
					landBlur = ImageHelper.maskWithColor(landBlur, Color.black, landMask, false);
					Color[] colors = graph.regions.stream().map(reg -> new Color((int)(reg.backgroundColor.getRed() * regionBlurColorScale), 
							(int)(reg.backgroundColor.getGreen() * regionBlurColorScale), (int)(reg.backgroundColor.getBlue() * regionBlurColorScale)))
							.toArray(size -> new Color[size]);
					map = ImageHelper.maskWithMultipleColors(map, colors, regionIndexes, landBlur, true);
				}
				else
				{
					landBlur = ImageHelper.convolveGrayscale(coastlineMask, kernel, true);
					// Remove the land blur from the ocean side of the borders.
					landBlur = ImageHelper.maskWithColor(landBlur, Color.black, landMask, false);
					map = ImageHelper.maskWithColor(map, settings.landBlurColor, landBlur, true);
				}
			}
		}
			
		// Store the current version of the map for a background when drawing icons later.
		BufferedImage landBackground = ImageHelper.deepCopy(map);
		
		if (shouldDrawRegionColors)
		{
			Graphics2D g = map.createGraphics();
			g.setColor(settings.coastlineColor);
			graph.drawRegionBorders(g, sizeMultiplyer, true);
		}

		// Add rivers.
		Logger.println("Adding rivers.");
//		StopWatch riverSw = new StopWatch();
		drawRivers(graph, map, sizeMultiplyer, settings.riverColor);
//		System.out.println("Time to add rivers in seconds: " + riverSw.getElapsedSeconds());
		
	
//		StopWatch mhdSw = new StopWatch();
		Logger.println("Adding mountains and hills.");
		Pair<List<Set<Center>>> pair = findMountainAndHillGroups(graph);
		// All mountain ranges and smaller groups of mountains (include mountains that are alone).
		List<Set<Center>> mountainGroups = pair.getFirst();
		// All mountain ranges and smaller groups of mountains extended to include nearby hills.
		List<Set<Center>> mountainAndHillGroups = pair.getSecond();
		addMountainsAndHills(graph, mountainAndHillGroups);
		if (mapParts != null)
			mapParts.mountainGroups = mountainGroups;

		Logger.println("Adding sand dunes.");
		addSandDunes(graph);
//		System.out.println("Time to add mountains, hills, and dunes in seconds: " + mhdSw.getElapsedSeconds());
		
//		StopWatch treeSW = new StopWatch();
		Logger.println("Adding trees.");
		addTrees(graph);
//		System.out.println("Time to add trees in seconds: " + treeSW.getElapsedSeconds());
		
		Logger.println("Drawing all icons.");
		drawAllIcons(map, landBackground);
		
		
		Logger.println("Drawing ocean.");
		{
			if (!settings.generateBackground)
			{
				ocean = ImageHelper.scaleByWidth(ocean, graph.getWidth());
			}
			if (ocean.getWidth() != graph.getWidth() || ocean.getHeight() != graph.getHeight())
			{
				throw new IllegalArgumentException("The given ocean background image does not"
						+ " have the same aspect ratio as the given land background image.");
			}

			// Needed for drawing text.
			landBackground = ImageHelper.maskWithImage(landBackground, ocean, landMask);
			
			map = ImageHelper.maskWithImage(map, ocean, landMask);
			ocean = null;
		}
		
		Logger.println("Adding effects to ocean along coastlines.");
		{
			BufferedImage oceanBlur;
			int blurLevel = (int) (settings.oceanEffects * sizeMultiplyer);
			if (blurLevel > 0)
			{
				float[][] kernel;
				if (settings.addWavesToOcean)
				{
					kernel = ImageHelper.createPositiveSincKernel(blurLevel, 1.0 / sizeMultiplyer);
				} else
				{
					kernel = ImageHelper.createGaussianKernel((int) (settings.oceanEffects * sizeMultiplyer));
				}
				oceanBlur = ImageHelper.convolveGrayscale(coastlineMask, kernel, true);
				// Remove the ocean blur from the land side of the borders.
				oceanBlur = ImageHelper.maskWithColor(oceanBlur, Color.black, landMask, true);

				map = ImageHelper.maskWithColor(map, settings.oceanEffectsColor, oceanBlur, true);
				landBackground = ImageHelper.maskWithColor(landBackground, settings.oceanEffectsColor, oceanBlur, true);
			}	
		}
		coastlineMask = null;
		
		// Draw coast lines.
		{
			Graphics2D g = map.createGraphics();
			g.setColor(settings.coastlineColor);
			graph.drawCoastline(g, sizeMultiplyer);
		}
		{
			Graphics2D g = landBackground.createGraphics();
			g.setColor(settings.coastlineColor);
			graph.drawCoastline(g, sizeMultiplyer);
		}
				
		// Add the rivers to landBackground so that the text doesn't erase them. I do this whether or not I draw text
		// because I might draw the text later.
		drawRivers(graph, landBackground, sizeMultiplyer, settings.riverColor);
		if (mapParts != null)
			mapParts.landBackground = landBackground;
		
		if (settings.drawText)
		{
			Logger.println("Adding text.");
			
			// Draw region borders into the land mask so that names don't make region borders fade away when drawn on top of them.
			if (shouldDrawRegionColors)
			{
				Graphics2D g = landBackground.createGraphics();
				g.setColor(settings.coastlineColor);
				graph.drawRegionBorders(g, sizeMultiplyer, true);
			}
						
			textDrawer.drawText(graph, map, landBackground, mountainGroups);
		}
		landBackground = null;

		if (settings.frayedBorder)
		{
			Logger.println("Adding frayed border.");
			BufferedImage borderMask = new BufferedImage(graph.getWidth(),
					graph.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
			graph.drawBorderWhite(borderMask.createGraphics());

			int blurLevel = (int) (settings.frayedBorderBlurLevel * sizeMultiplyer);
			if (blurLevel > 0)
			{
				float[][] kernel = ImageHelper.createGaussianKernel(blurLevel);
				BufferedImage borderBlur = ImageHelper.convolveGrayscale(borderMask, kernel, true);
			
				map = ImageHelper.maskWithColor(map, settings.frayedBorderColor, borderBlur, true);

			}
			map = ImageHelper.setAlphaFromMask(map, borderMask, true);
		}
		
		if (settings.grungeWidth > 0)
		{
			Logger.println("Adding grunge.");
			// 104567 is an arbitrary number added so that the grung is not the same pattern as
			// the background.
			BufferedImage clouds = FractalBGGenerator.generate(
					new Random(settings.backgroundRandomSeed + 104567), settings.fractalPower, 
					(int)bounds.getWidth(), (int)bounds.getHeight(), 0.75f);
			// Whiten the middle of clouds.
			darkenMiddleOfImage(settings.resolution, clouds, settings.grungeWidth);
			
			// Add the cloud mask to the map.
			map = ImageHelper.maskWithColor(map, settings.frayedBorderColor, clouds, true);
		}
		
		double elapsedTime = System.currentTimeMillis() - startTime;
		Logger.println("Total time to generate map (in seconds): " + elapsedTime / 1000.0);

		Logger.println("Done creating map.");
		
		ScaledIconCache.clear();
		return map;

	}
	
	/**
	 * Makes the middle area of a gray scale image darker following a Gauisian blur drop off.
	 */
	private void darkenMiddleOfImage(double resolutionScale, BufferedImage image, int grungeWidth)
	{
		// Draw a white box.
		
		int blurLevel = (int)(grungeWidth * resolutionScale);
		if (blurLevel == 0)
			blurLevel = 1; // Avoid an exception later.
		// Create a white no-filled in rectangle, then blur it. To be much more efficient, I only create
		// the upper left corner plus 1 pixel in both directions since the corners and edges are all the
		// rotated and the edges are all the same except some longer than others.
		int blurBoxWidth = blurLevel*2 + 1;
		// There is a blurLevel wide buffer below is so that in the convolution the border from one side of the box won't spread (wrap) to the other side.
		// I would be especially bad if it did because ImageHelper.convolveGrayscale pads images to be powers of 2 in the width and height.
		// The white rectangleis also drawn an extra blurLevel from blurBoxWidth, totaling blurLevel*2.
		BufferedImage blurBox = new BufferedImage(blurBoxWidth + blurLevel*2, blurBoxWidth + blurLevel*2, BufferedImage.TYPE_BYTE_BINARY);
		Graphics g = blurBox.getGraphics();
		g.setColor(Color.white);
		g.fillRect(0, 0, blurBoxWidth + blurLevel, blurBoxWidth + blurLevel);
		
		int rectWidth = (int)(resolutionScale);
		if (rectWidth == 0)
			rectWidth = 1;
		
		// Erase the white rectangle border from the right and button sides.
		g.setColor(Color.black);
		g.fillRect(rectWidth, rectWidth, blurBoxWidth + blurLevel, blurBoxWidth + blurLevel);
				
		// Use Gaussian blur on the box.
		float[][] kernel = ImageHelper.createGaussianKernel(blurLevel);
		blurBox = ImageHelper.convolveGrayscale(blurBox, kernel, true);

		// Multiply the image by blurBox. Also remove the padded edges off of blurBox.
		assert image.getType() == BufferedImage.TYPE_BYTE_GRAY;
		WritableRaster imageRaster = image.getRaster();
		Raster blurBoxRaster = blurBox.getRaster();
		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
			{
				float imageLevel = imageRaster.getSample(x, y, 0);
				
				// Retrieve the blur level as though blurBox has all 4 quadrants and middle created, even has only the upper left.
				int blurBoxX;
				if (x > blurLevel)
				{
					if (image.getWidth() - x < blurLevel)
					{
						// x is under the right corner.
						blurBoxX = image.getWidth() - x;
					}
					else
					{
						// x is between the corners.
						blurBoxX = blurBoxWidth + 1;
					}
				}
				else
				{
					// x is under the left corner.
					blurBoxX = x;
				}
				
				int blurBoxY;
				if (y > blurLevel)
				{
					if (image.getHeight() - y < blurLevel)
					{
						// y is under the right corner.
						blurBoxY = image.getHeight() - y;
					}
					else
					{
						// x is between the corners.
						blurBoxY = blurBoxWidth + 1;
					}
				}
				else
				{
					// y is under the left corner.
					blurBoxY = y;
				}
				float blurBoxLevel = blurBoxRaster.getSample(blurBoxX, blurBoxY, 0);
				
				imageRaster.setSample(x, y, 0, (imageLevel * blurBoxLevel)/255f);
			}
	}
		
	private BufferedImage drawRegionColors(GraphImpl graph, BufferedImage fractalBG, BufferedImage pixelColors)
	{	
		Color[] regionBackgroundColors = graph.regions.stream().map(
				reg -> reg.backgroundColor).toArray(size -> new Color[size]);
		
		return ImageHelper.colorify2Multi(fractalBG, regionBackgroundColors, pixelColors);
	}
	
	private void assignRandomRegionColors(GraphImpl graph, MapSettings settings)
	{
		
		float[] landHsb = new float[3];
		Color.RGBtoHSB(settings.landColor.getRed(), settings.landColor.getGreen(), settings.landColor.getBlue(), landHsb);
		
		List<Color> regionColorOptions = new ArrayList<>();
		Random rand = new Random(settings.regionsRandomSeed);
		for (@SuppressWarnings("unused") int i : new Range(graph.regions.size())) 
		{				
			regionColorOptions.add(generateRegionColor(rand, landHsb, settings.hueRange, settings.saturationRange, settings.brightnessRange));
		}
				
		// Create a new Random object so that changes 
		assignRegionColors(graph, regionColorOptions);
	}
	
	private static Color generateRegionColor(Random rand, float[] landHsb, float hueRange, float saturationRange, float brightnessRange)
	{
		float hue = (float)(landHsb[0] * 360 + (rand.nextDouble() - 0.5) * hueRange);
		float saturation = ImageHelper.bound((int)(landHsb[1] * 255 + (rand.nextDouble() - 0.5) * saturationRange));
		float brightness = ImageHelper.bound((int)(landHsb[2] * 255 + (rand.nextDouble() - 0.5) * brightnessRange));
		return ImageHelper.colorFromHSB(hue, saturation, brightness);
	}
	
	public static Color generateColorFromBaseColor(Random rand, Color base, float hueRange, float saturationRange, float brightnessRange)
	{
		float[] hsb = new float[3];
		Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), hsb);
		return generateRegionColor(rand, hsb, hueRange, saturationRange, brightnessRange);
	}
	
	/**
	 * Assigns the color of each political region.
	 */
	private void assignRegionColors(GraphImpl graph, List<Color> colorOptions)
	{
		if (colorOptions.isEmpty())
			throw new IllegalArgumentException("To draw region colors, you must specify at least one region color.");
		for (int i : new Range(graph.regions.size()))
		{
			graph.regions.get(i).backgroundColor = colorOptions.get(i % colorOptions.size());
		}
	}

	private void drawRivers(GraphImpl graph, BufferedImage map, double sizeMultiplyer, Color riverColor)
	{
		Graphics2D g = map.createGraphics();
		g.setColor(riverColor);
		// Draw rivers thin.
		graph.drawRivers(g, sizeMultiplyer/2.0);
	}
		
	private double findMeanPolygonWidth(GraphImpl graph)
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

	private void markMountains(GraphImpl graph)
	{
		for (Center c : graph.centers)
		{
			if (c.elevation > mountainElevationThreshold && !c.water
					&& !c.coast && !c.border && c.findWidth() < maxSizeToDrawIcon)
			{
				c.mountain = true;
			}
		}
	}

	private void markHills(GraphImpl graph)
	{
		for (Center c : graph.centers)
		{
			if (c.elevation < mountainElevationThreshold && c.elevation > hillElevationThreshold
		 			&& !c.water && !c.coast && c.findWidth() < maxSizeToDrawIcon)
				
			{
				c.hill = true;
			}
		}
	}

 
	
	/**
	 * Finds and marks mountain ranges, and groups smaller than ranges, and surrounding hills.
	 */
	private Pair<List<Set<Center>>> findMountainAndHillGroups(GraphImpl graph)
	{
		List<Set<Center>> mountainGroups = findCenterGroups(graph, maxGapSizeInMountainClusters, new Function<Center, Boolean>()
				{
					public Boolean apply(Center center)
					{
						return center.mountain;
					}
				});
		
		List<Set<Center>> mountainAndHillGroups = findCenterGroups(graph, maxGapSizeInMountainClusters, new Function<Center, Boolean>()
		{
			public Boolean apply(Center center)
			{
				return center.mountain || center.hill;
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
			BufferedImage mask, BufferedImage background, int xCenter, int yCenter)
	{   	
    	if (map.getWidth() != background.getWidth())
    		throw new IllegalArgumentException();
       	if (map.getHeight() != background.getHeight())
    		throw new IllegalArgumentException();
       	if (mask.getWidth() != icon.getWidth())
       		throw new IllegalArgumentException("The given mask's width does not match the icon' width.");
       	if (mask.getHeight() != icon.getHeight())
       		throw new IllegalArgumentException("The given mask's height does not match the icon' height.");
       	
       	if (icon.getWidth() > maxSizeToDrawIcon)
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
				double maskLevel = maskRaster.getSampleDouble(x, y, 0) / 255.0;
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
	 * Stores things needed to draw an icon onto the map.
	 * @author joseph
	 *
	 */
	private class IconDrawTask implements Comparable<IconDrawTask>
	{
		BufferedImage icon;
		BufferedImage mask;
		Point centerLoc;
		int scaledWidth;
		int yBottom;
		boolean needsScale;

		public IconDrawTask(BufferedImage icon, BufferedImage mask, Point centerLoc, int scaledWidth,
				boolean needsScale)
		{
			this.icon = icon;
			this.mask = mask;
			this.centerLoc = centerLoc;
			this.scaledWidth = scaledWidth;
			this.needsScale = needsScale;
			
	   		double aspectRatio = ((double)icon.getWidth())/icon.getHeight();
	   		int scaledHeight = (int)(scaledWidth/aspectRatio);
	       	yBottom = (int)(centerLoc.y + (scaledHeight/2.0));
		}
		
		public void scaleIcon()
		{
			if (needsScale)
			{
		       	icon = ScaledIconCache.getInstance().getScaledIcon(icon, scaledWidth);
		      	mask = ScaledIconCache.getInstance().getScaledIcon(mask, scaledWidth);
			}
		}

		@Override
		public int compareTo(IconDrawTask other)
		{
			return Integer.compare(yBottom, other.yBottom);
		}
	}
	
	/**
	 * Draws all icons in iconsToDraw. I draw all the icons at once this way so that I can sort
	 * the icons by the y-coordinate of the base of each icon. This way icons lower on the map
	 * are drawn in front of those that are higher.
	 */
	private void drawAllIcons(BufferedImage map, BufferedImage background)
	{
//		StopWatch sw = new StopWatch();
		Collections.sort(iconsToDraw);

//		 Scale the icons and masks in parallel.
		List<Runnable> jobs = new ArrayList<>();
		for (final IconDrawTask task : iconsToDraw)
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
		
		for (final IconDrawTask task : iconsToDraw)
		{
			drawIconWithBackgroundAndMask(map, task.icon, task.mask, background, (int)task.centerLoc.x,
					(int)task.centerLoc.y);
		}		
//		System.out.println("Time to draw icons in seconds: " + sw.getElapsedSeconds());
	}


	private void addMountainsAndHills(GraphImpl graph, List<Set<Center>> mountainAndHillGroups)
	{		
        // Maps mountain range ids (the ids in the file names) to list of mountain images and their masks.
        ListMap<String, Tuple2<BufferedImage, BufferedImage>> mountainImagesById = loadIconGroups("mountain");

        // Maps mountain range ids (the ids in the file names) to list of hill images and their masks.
        // The hill image file names must use the same ids as the mountain ranges.
        ListMap<String, Tuple2<BufferedImage, BufferedImage>> hillImagesById = loadIconGroups("hill");

        // Maps from the mountainRangeId of Centers to the range id's from the mountain image file names.
        Map<Integer, String> rangeMap = new TreeMap<>();
        
        for (Set<Center> group : mountainAndHillGroups)
        {
        	for (Center c : group)
        	{
        		// Find the center's size along the x axis.
	        	double cSize = findCenterWidthBetweenNeighbors(c);
	        	
	        	String filenameRangeId = rangeMap.get(c.mountainRangeId);
	        	if ((filenameRangeId == null))
	        	{
	        		filenameRangeId =  new ArrayList<>(mountainImagesById.keySet()).get(
	        				r.nextInt(mountainImagesById.keySet().size()));
	        		rangeMap.put(c.mountainRangeId, filenameRangeId);
	        	}

	        	if (c.mountain)
	        	{        		
		        	List<Tuple2<BufferedImage, BufferedImage>> imagesInRange =
		        			mountainImagesById.get(filenameRangeId);


		        	// I'm deliberatly putting this line before checking center size so that the
		        	// random number generator is used the same no matter what resolution the map
		        	// is drawn at.
	           		int i = r.nextInt(imagesInRange.size());

	           		int scaledSize = (int)(cSize * mountainScale);

	           		// Make sure the image will be at least 1 pixel wide.
		           	if (scaledSize >= 1)
		           	{	
			           	// Draw the image such that it is centered in the center of c.
		           		iconsToDraw.add(new IconDrawTask(imagesInRange.get(i).getFirst(), 
		           				imagesInRange.get(i).getSecond(), c.loc, scaledSize, true));
		           	}
		        }
	         	else if (c.hill)
	         	{
		        	List<Tuple2<BufferedImage, BufferedImage>> imagesInGroup = 
		        			hillImagesById.get(filenameRangeId);

		        	int i = r.nextInt(imagesInGroup.size());

	           		int scaledSize = (int)(cSize * hillScale);
		        	
	           		// Make sure the image will be at least 1 pixel wide.
		           	if (scaledSize >= 1)
		           	{
		           		iconsToDraw.add(new IconDrawTask(imagesInGroup.get(i).getFirst(), 
		           				imagesInGroup.get(i).getSecond(), c.loc, scaledSize, true));
		           	}
	         		
	         	}
        	}
        }
   	}
	
	private void addSandDunes(GraphImpl graph)
	{
        // Load the sand dune images.
        List<Tuple2<BufferedImage, BufferedImage>> duneImages = loadIconGroups("dune").get("sand");
        
   		List<Set<Center>> groups = findCenterGroups(graph, maxGapBetweenBiomeGroups, 
				new Function<Center, Boolean>()
				{
					public Boolean apply(Center center)
					{
						return center.biome.equals(ColorData.TEMPERATE_DESERT);
					}
				});
   		
   		// This is the probability that a temperate desert will be a dune field.
   		double duneProbabilityPerBiomeGroup = 0.6;
   		double duneProbabilityPerCenter = 0.5;
   		
   		// Find the average center width.
   		double sum = 0;
   		double count = 0;
		for (Set<Center> group : groups)
		{
			for (Center c : group)
			{
				sum += c.findWidth();
				count++;
			}
		}
		double averageWidth = sum/count;
		
		int width = (int)(averageWidth * 1.5);
		if (width == 0)
			return;
		
   		
		for (Set<Center> group : groups)
		{
			// For making sure we only draw once per corner.
//			Set<Corner> cornersDrawn = new HashSet<>();
			
			if (r.nextDouble() < duneProbabilityPerBiomeGroup)
			{
				for (Center c : group)
				{	        
					if (r.nextDouble() < duneProbabilityPerCenter)
					{
						int i = r.nextInt(duneImages.size());
						
		           		iconsToDraw.add(new IconDrawTask(duneImages.get(i).getFirst(), 
		           				duneImages.get(i).getSecond(), c.loc, width, true));
					}
				}
				
			}
		}
	}
		
	private void addTrees(GraphImpl graph)
			throws IOException
	{
		// Find the average width of all Centers.
		double sum = 0;
		for (Center c : graph.centers)
		{
			sum += findCenterWidthBetweenNeighbors(c);
		}
		double avgHeight = sum / graph.centers.size();
		
        // Load the images and masks.
        ListMap<String, Tuple2<BufferedImage, BufferedImage>> treesById = loadIconGroups("tree");
        
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
   		

   		List<ForestType> forestTypes = new ArrayList<>();
        forestTypes.add(new ForestType(GraphImpl.ColorData.TEMPERATE_RAIN_FOREST, 0.5, 1.0,
        		treesById.get("deciduous")));
        forestTypes.add(new ForestType(GraphImpl.ColorData.TAIGA, 1.0, 1.0, treesById.get("pine")));
        forestTypes.add(new ForestType(GraphImpl.ColorData.SHRUBLAND, 1.0, 1.0, treesById.get("pine")));
        forestTypes.add(new ForestType(GraphImpl.ColorData.HIGH_TEMPERATE_DECIDUOUS_FOREST, 1.0, 0.25, treesById.get("pine")));
        forestTypes.add(new ForestType(GraphImpl.ColorData.HIGH_TEMPERATE_DESERT, 1.0/8.0, 0.1,
        		treesById.get("cacti")));
 
        // Store which corners have had trees drawn so that I don't draw them multiple times.
        boolean[] cornersWithTreesDrawn = new boolean[graph.corners.size()];
        
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
        			if (r.nextDouble() < forest.biomeFrequency)
        			{
        				for (Center c : group)
        				{	        	
         					drawTreesAtCenterAndCorners(graph, forest, avgHeight,
         							cornersWithTreesDrawn, c);
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
        				drawTreesAtCenterAndCorners(graph, forest, avgHeight, 
        						cornersWithTreesDrawn, c);
        			}
    			}
      			
 	        }
        }
	}

	private void drawTreesAtCenterAndCorners(GraphImpl graph,
			ForestType forest, double avgCenterHeight, boolean[] cornersWithTreesDrawn, Center center)
	{
    	if (center.biome == forest.biome && center.elevation < mountainElevationThreshold 
    			&& !center.water && !center.coast)
    	{
			drawTrees(graph, forest.imagesAndMasks, avgCenterHeight, center.loc, forest.density);
				
			// Draw trees at the neighboring corners too.
			for (Corner corner : center.corners)
			{
				if (!cornersWithTreesDrawn[corner.index])
				{
					drawTrees(graph, forest.imagesAndMasks, avgCenterHeight, corner.loc,
							forest.density);
					cornersWithTreesDrawn[corner.index] = true;
				}
			}
    	}
			
	}
	
	private class ForestType
	{
		GraphImpl.ColorData biome;
		double density;
		List<Tuple2<BufferedImage, BufferedImage>> imagesAndMasks;
		double biomeFrequency;
		
		/**
		 * @param biomeProb If this is not 1.0, groups of centers of biome type "biome" will be found
		 * and each groups will have this type of forest with probability biomeProb.
		*/
		public ForestType(GraphImpl.ColorData biome, double density, double biomeFrequency, 
				List<Tuple2<BufferedImage, BufferedImage>> imagesAndMasks)
		{
			this.biome = biome;
			this.density = density;
			this.imagesAndMasks = imagesAndMasks;
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
			double forestDensity)
	{
		// Convert the forestDensity into an integer number of tree to draw such that the expected
		// value is forestDensity.
		double fraction = forestDensity - (int)forestDensity;
		int extra = r.nextDouble() < fraction ? 1 : 0;
		int numTrees = ((int)forestDensity) + extra;
		       	
       	for (int i = 0; i < numTrees; i++)
       	{
       		int index = r.nextInt(imagesAndMasks.size());
       		BufferedImage image = imagesAndMasks.get(index).getFirst();
       		BufferedImage mask = imagesAndMasks.get(index).getSecond();
           	     
           	// Draw the image such that it is centered in the center of c.
           	int x = (int) (loc.x);
           	int y = (int) (loc.y);
           	
           	double sqrtSize = Math.sqrt(cSize);
           	x += r.nextGaussian() * sqrtSize*2.0;
           	y += r.nextGaussian() * sqrtSize*2.0;
        	
           	// Make sure we don't draw trees in water.
           	Center center = graph.getCenterAt(x + (int)image.getWidth()/2, y + image.getHeight()/2);
           	if (center.water)
           		continue;
           	center = graph.getCenterAt(x + (int)image.getWidth()/2, y - image.getHeight()/2);
           	if (center.water)
           		continue;
           	center = graph.getCenterAt(x - (int)image.getWidth()/2, y + image.getHeight()/2);
           	if (center.water)
           		continue;
           	center = graph.getCenterAt(x - (int)image.getWidth()/2, y - image.getHeight()/2);
           	if (!center.water)
           	{
           		iconsToDraw.add(new IconDrawTask(image, mask, new Point(x, y), (int)image.getWidth(), false));	           	
           	}
       	}
		
	}
	
	/**
	 * Loads groups if icons, using iconType as a key word to filter on.
	 */
	private ListMap<String, Tuple2<BufferedImage, BufferedImage>> loadIconGroups(final String iconType)
	{
		ListMap<String, Tuple2<BufferedImage, BufferedImage>> imagesPerGroup = new ListMap<>();

		String[] filenames = new File(Paths.get("assets", "icons").toString()).list(new FilenameFilter()
		{
			@Override
			public boolean accept(File dir, String name)
			{
				return !name.contains("_mask.") && name.matches(".+_" + iconType + "\\d+\\.png");
			}
		});
		
		Arrays.sort(filenames);

		for (String filename : filenames)
		{
			Path path = Paths.get("assets", "icons", filename);
			Logger.println("Loading icon: " + path);
			BufferedImage mountain;
			BufferedImage mask;
			try
			{
				mountain = ImageIO.read(path.toFile());
				String ext = FilenameUtils.getExtension(path.toString());
				String base = FilenameUtils.getBaseName(path.toString());
				Path maskPath = Paths.get(path.getParent().toString(), base + "_mask." + ext);
				if (!Files.exists(maskPath))
				{
					// Try jpg.
					maskPath = Paths.get(path.getParent().toString(), base + "_mask.jpg");
					if (!Files.exists(maskPath))
						throw new IllegalArgumentException("Unable to find a mask for image: " + path);
				}
				mask = ImageHelper.convertToGrayscale(ImageIO.read(new File(maskPath.toString())));
			} catch (IOException e)
			{
				throw new RuntimeException(e);
			}

			String rangeId = filename.replaceAll("(.+)_" + iconType + "\\d*.png", "$1");

			imagesPerGroup.add(rangeId, new Tuple2<>(mountain, mask));
		}
		return imagesPerGroup;
	}

	public static void main(String[] args) throws IOException
	{
		if (args.length > 1)
			Logger.println("usage: MapCreator.java properties_filename");
		
		String propsFilename = "map_settings.properties";
		if (args.length > 0)
			propsFilename = args[0];
		Properties props = new Properties();
		props.load(new FileInputStream(propsFilename));

		MapSettings settings = new MapSettings(propsFilename);

		// settings.randomSeed = System.currentTimeMillis();

		BufferedImage map;
		MapCreator creator = new MapCreator();
		
		try
		{
			map = creator.createMap(settings, null, null);
		} 
		finally
		{
			ImageHelper.shutdownThreadPool();
		}
		
//		Path outputPath = Paths.get("map_" + settings.randomSeed + ".png");
//		ImageIO.write(map, "png", outputPath.toFile());
//		Logger.println("Map written to " + outputPath.toAbsolutePath());
		
		ImageHelper.openImageInSystemDefaultEditor(map, "map_" + settings.randomSeed);

	}

}



