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
import java.util.TreeSet;
import java.util.stream.Collectors;

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
import util.ImageHelper.ColorifyAlgorithm;

public class MapCreator
{
	private final double regionBlurColorScale = 0.7;

	
	
	private Random r;
	// This is a base width for determining how large to draw text and effects.
	private final double baseResolution = 1536;
	
	public MapCreator()
	{
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
		
        Background background = createBackgroundImages(settings, maxDimensions);
		
		double sizeMultiplyer = (background.bounds.getWidth() / baseResolution);
		
		
		TextDrawer textDrawer = settings.drawText || mapParts != null ? new TextDrawer(settings, sizeMultiplyer) : null;
		if (mapParts != null)
			mapParts.textDrawer = textDrawer;
		
		GraphImpl graph = GraphCreator.createGraph(background.bounds.getWidth(), background.bounds.getHeight(),
				settings.worldSize, settings.edgeLandToWaterProbability, settings.centerLandToWaterProbability,
				new Random(r.nextLong()),
				sizeMultiplyer);	
		if (mapParts != null)
			mapParts.graph = graph;
		
		background.doSetupThatNeedsGraph(settings, graph);
		
		IconDrawer iconDrawer = new IconDrawer(graph, r);

		iconDrawer.markMountains();
		iconDrawer.markHills();
		iconDrawer.findMountainAndHillGroups();

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
			// Combine land and ocean images.
			map = ImageHelper.maskWithColor(background.land, Color.black, landMask, false);
		}
		background.land = null;
		
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
				
				if (background.shouldDrawRegionColors)
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
					map = ImageHelper.maskWithMultipleColors(map, colors, background.regionIndexes, landBlur, true);
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
		
		if (background.shouldDrawRegionColors)
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
		
	
		Logger.println("Adding mountains and hills.");
		List<Set<Center>> mountainGroups = iconDrawer.addMountainsAndHills();
		if (mapParts != null)
			mapParts.mountainGroups = mountainGroups;

		Logger.println("Adding sand dunes.");
		iconDrawer.addSandDunes();
		
		Logger.println("Adding trees.");
		iconDrawer.addTrees();
		
		Logger.println("Drawing all icons.");
		iconDrawer.drawAllIcons(map, landBackground);
		
		Logger.println("Drawing ocean.");
		{
			if (background.ocean.getWidth() != graph.getWidth() || background.ocean.getHeight() != graph.getHeight())
			{
				throw new IllegalArgumentException("The given ocean background image does not"
						+ " have the same aspect ratio as the given land background image.");
			}

			// Needed for drawing text.
			landBackground = ImageHelper.maskWithImage(landBackground, background.ocean, landMask);
			
			map = ImageHelper.maskWithImage(map, background.ocean, landMask);
			background.ocean = null;
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
			if (background.shouldDrawRegionColors)
			{
				Graphics2D g = landBackground.createGraphics();
				g.setColor(settings.coastlineColor);
				graph.drawRegionBorders(g, sizeMultiplyer, true);
			}
						
			textDrawer.drawText(graph, map, landBackground, mountainGroups);
		}
		landBackground = null;
		
		if (settings.drawBorder)
		{
			Logger.println("Adding border.");
			map = addBorderToMap(settings, map, background, sizeMultiplyer);
		}

		if (settings.frayedBorder)
		{
			Logger.println("Adding frayed edges.");
			// TODO Add an option for the hard-coded number of polygons below.
			GraphImpl frayGraph = GraphCreator.createSimpleGraph(background.bounds.getWidth(), 
					background.bounds.getHeight(), 30000, new Random(r.nextLong()), sizeMultiplyer);
			BufferedImage borderMask = new BufferedImage(frayGraph.getWidth(),
					frayGraph.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
			frayGraph.drawBorderWhite(borderMask.createGraphics());

			int blurLevel = (int) (settings.frayedBorderBlurLevel * sizeMultiplyer);
			if (blurLevel > 0)
			{
				float[][] kernel = ImageHelper.createGaussianKernel(blurLevel);
				BufferedImage borderBlur = ImageHelper.convolveGrayscale(borderMask, kernel, true);
			
				map = ImageHelper.maskWithColor(map, settings.frayedBorderColor, borderBlur, true);

			}
			map = ImageHelper.setAlphaFromMask(map, borderMask, true);
		}
		else
		{
			// Use the random number generator the same whether or not we draw a frayed border.
			r.nextLong();
		}
		
		if (settings.grungeWidth > 0)
		{
			Logger.println("Adding grunge.");
			// 104567 is an arbitrary number added so that the grung is not the same pattern as
			// the background.
			BufferedImage clouds = FractalBGGenerator.generate(
					new Random(settings.backgroundRandomSeed + 104567), settings.fractalPower, 
					(int)map.getWidth(), (int)map.getHeight(), 0.75f);
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
	
	private BufferedImage addBorderToMap(MapSettings settings, BufferedImage map, Background background, double sizeMultipler)
	{
		BufferedImage mapWithoutBorder = ImageHelper.extractRegion(map, settings.borderWidth, settings.borderWidth, 
				(int)(background.bounds.getWidth() - settings.borderWidth * 2 * sizeMultipler),
				(int)(background.bounds.getHeight() - settings.borderWidth * 2 * sizeMultipler));
		background.borderBackground.getGraphics().drawImage(mapWithoutBorder, settings.borderWidth, settings.borderWidth, null);
		map = background.borderBackground;
		
		Path borderPath = Paths.get("assets", "borders", settings.borderType);
		BufferedImage upperLeftCorner = loadImageWithStringInFileName(borderPath, "upper_left_corner.", false);
		BufferedImage upperRightCorner = loadImageWithStringInFileName(borderPath, "upper_right_corner.", false);
		BufferedImage lowerLeftCorner = loadImageWithStringInFileName(borderPath, "lower_left_corner.", false);
		BufferedImage lowerRightCorner = loadImageWithStringInFileName(borderPath, "lower_right_corner.", false);
		BufferedImage edge = loadImageWithStringInFileName(borderPath, "_edge.", true);
		
		if (upperLeftCorner == null)
		{
			if (upperRightCorner != null)
			{
				upperLeftCorner = createCornerFromCornerByFlipping(upperRightCorner, CornerType.upperRight, CornerType.upperLeft);
			}
			else if (lowerLeftCorner != null)
			{
				upperLeftCorner = createCornerFromCornerByFlipping(lowerLeftCorner, CornerType.lowerLeft, CornerType.upperLeft);
			}
			else if (lowerRightCorner != null)
			{
				upperLeftCorner = createCornerFromCornerByFlipping(lowerRightCorner, CornerType.lowerRight, CornerType.upperLeft);
			}
			else
			{
				throw new RuntimeException("Couldn't find any corners in " + borderPath);
			}
		}
		
		if (upperRightCorner == null)
		{
			upperRightCorner = createCornerFromCornerByFlipping(upperLeftCorner, CornerType.upperLeft, CornerType.upperRight);
		}
		if (lowerLeftCorner == null)
		{
			lowerLeftCorner = createCornerFromCornerByFlipping(upperLeftCorner, CornerType.upperLeft, CornerType.lowerLeft);
		}
		if (lowerRightCorner == null)
		{
			lowerRightCorner = createCornerFromCornerByFlipping(upperLeftCorner, CornerType.upperLeft, CornerType.lowerRight);
		}
		
		// TODO Draw the border with the images loaded above. 

		return map;
	}
	
	private BufferedImage createCornerFromCornerByFlipping(BufferedImage cornerIn, CornerType inputCornerType, CornerType outputType)
	{
		switch (inputCornerType)
		{
		case lowerLeft:
			switch(outputType)
			{
			case lowerLeft:
				return cornerIn;
			case lowerRight:
				return ImageHelper.flipVertically(cornerIn);
			case upperLeft:
				return ImageHelper.flipHorizontally(cornerIn);
			case upperRight:
				return ImageHelper.flipVertically(ImageHelper.flipVertically(cornerIn));
			}
			break;
		case lowerRight:
			switch(outputType)
			{
			case lowerLeft:
				return ImageHelper.flipVertically(cornerIn);
			case lowerRight:
				return cornerIn;
			case upperLeft:
				return ImageHelper.flipVertically(ImageHelper.flipVertically(cornerIn));
			case upperRight:
				return ImageHelper.flipHorizontally(cornerIn);
			}
		case upperLeft:
			switch(outputType)
			{
			case lowerLeft:
				return ImageHelper.flipHorizontally(cornerIn);
			case lowerRight:
				return ImageHelper.flipVertically(ImageHelper.flipVertically(cornerIn));
			case upperLeft:
				return cornerIn;
			case upperRight:
				return ImageHelper.flipVertically(cornerIn);
			}
		case upperRight:
			switch(outputType)
			{
			case lowerLeft:
				return ImageHelper.flipVertically(ImageHelper.flipVertically(cornerIn));
			case lowerRight:
				return ImageHelper.flipHorizontally(cornerIn);
			case upperLeft:
				return ImageHelper.flipVertically(cornerIn);
			case upperRight:
				return cornerIn;
			}
		}
		
		throw new IllegalStateException("Unable to flip corner image.");
	}
	
	private enum CornerType
	{
		upperLeft,
		upperRight,
		lowerLeft,
		lowerRight
	}
	
	private BufferedImage loadImageWithStringInFileName(Path path, String inFileName, boolean throwExceptionIfMissing)
	{
		File[] cornerArray = new File(path.toString()).listFiles(file -> file.getName().contains(inFileName));
		if (cornerArray.length == 0)
		{
			if (throwExceptionIfMissing)
				throw new RuntimeException("Unable to find a file containing \"" + inFileName + "\" in the directory " + path.toAbsolutePath());
			else
				return null;
		}
		if (cornerArray.length > 1)
		{
			throw new RuntimeException("More than one file contains \"" + inFileName + "\" in the directory " + path.toAbsolutePath());			
		}
		
		return ImageHelper.read(cornerArray[0].getPath());
	}
	
	private Background createBackgroundImages(MapSettings settings, Dimension maxDimensions)
	{
		Background result = new Background(settings, maxDimensions);	
		return result;
	}
	
	/**
	 * An assortment of things needed to draw the background.
	 */
	private class Background
	{
		BufferedImage land;
		BufferedImage ocean;
		DimensionDouble bounds;
		BufferedImage borderBackground;
		private boolean backgroundFromFilesNotGenerated;
		private boolean shouldDrawRegionColors;
		private ImageHelper.ColorifyAlgorithm landColorifyAlgorithm;
		// regionIndexes is a gray scale image where the level of each pixel is the index of the region it is in.
		BufferedImage regionIndexes;

		public Background(MapSettings settings, Dimension maxDimensions)
		{
			backgroundFromFilesNotGenerated = !settings.generateBackground && !settings.generateBackgroundFromTexture && !settings.transparentBackground;
	        shouldDrawRegionColors = settings.drawRegionColors 
			&& !backgroundFromFilesNotGenerated 
			&& (!settings.generateBackgroundFromTexture || settings.colorizeLand)
			&& !settings.transparentBackground;

	        BufferedImage landGeneratedBackground;
			landColorifyAlgorithm = ColorifyAlgorithm.none;
			if (settings.generateBackground || settings.generateBackgroundFromTexture || settings.transparentBackground)
			{
				Logger.println("Generating the background image");
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
											
				if (settings.generateBackground)
				{
					// Fractal generated background images
					
					BufferedImage oceanGeneratedBackground = FractalBGGenerator.generate(
							new Random(settings.backgroundRandomSeed), settings.fractalPower, 
							(int)bounds.getWidth(), (int)bounds.getHeight(), 0.75f);
					landGeneratedBackground = oceanGeneratedBackground;
					landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.algorithm2;
					ocean = ImageHelper.colorify(oceanGeneratedBackground, settings.oceanColor, ImageHelper.ColorifyAlgorithm.algorithm2);
					
					if (shouldDrawRegionColors)
					{
						// Drawing region colors must be done later because it depends on the graph.
						land = landGeneratedBackground;
					}
					else
					{
						land = ImageHelper.colorify(landGeneratedBackground, settings.landColor, landColorifyAlgorithm);
						landGeneratedBackground = null;
					}
				}
				else if (settings.generateBackgroundFromTexture)
				{
					// Generate the background images from a texture
					
					BufferedImage texture;
					try
					{
						texture = ImageHelper.read(settings.backgroundTextureImage);
					}
					catch (RuntimeException e)
					{
						throw new RuntimeException("Unable to read the texture image file name \"" + settings.backgroundTextureImage + "\"", e);
					}
					
					BufferedImage oceanGeneratedBackground;
					if (settings.colorizeOcean)
					{
						oceanGeneratedBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(
							new Random(settings.backgroundRandomSeed), ImageHelper.convertToGrayscale(texture), (int)bounds.getHeight(), (int)bounds.getWidth());
						ocean = ImageHelper.colorify(oceanGeneratedBackground, settings.oceanColor, ImageHelper.ColorifyAlgorithm.algorithm3);
					}
					else
					{
						oceanGeneratedBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(
								new Random(settings.backgroundRandomSeed), texture, (int)bounds.getHeight(), (int)bounds.getWidth());
						ocean = oceanGeneratedBackground;
					}
					
					if (settings.colorizeLand == settings.colorizeOcean)
					{
						// Don't generate the same image twice.
						landGeneratedBackground = oceanGeneratedBackground;
						
						if (settings.colorizeLand)
						{
							landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.algorithm3;					
							if (shouldDrawRegionColors)
							{
								// Drawing region colors must be done later because it depends on the graph.
								land = landGeneratedBackground;
							}
							else
							{
								land = ImageHelper.colorify(landGeneratedBackground, settings.landColor, ImageHelper.ColorifyAlgorithm.algorithm3);
							}
						}
						else
						{
							landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.none;
							land = landGeneratedBackground;
						}
					}
					else
					{
						if (settings.colorizeLand)
						{
							landGeneratedBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(
								new Random(settings.backgroundRandomSeed), ImageHelper.convertToGrayscale(texture), (int)bounds.getHeight(), (int)bounds.getWidth());
							if (shouldDrawRegionColors)
							{
								// Drawing region colors must be done later because it depends on the graph.
								land = landGeneratedBackground;
							}
							else
							{
								land = ImageHelper.colorify(landGeneratedBackground, settings.landColor, ImageHelper.ColorifyAlgorithm.algorithm3);
							}
							landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.algorithm3;
						}
						else
						{
							landGeneratedBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(
									new Random(settings.backgroundRandomSeed), texture, (int)bounds.getHeight(), (int)bounds.getWidth());
							land = landGeneratedBackground;
							landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.none;
						}
					}
				}
				else
				{
					// Transparent background
					landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.none;
					land = ImageHelper.createWhiteTransparentImage((int)bounds.getWidth(), (int)bounds.getHeight());
					ocean = land;
					landGeneratedBackground = land;
				}
			}
			else
			{
				// The background images are from files
				
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
				// The library I'm using to scale images and make them look nice has a bug where the width or height might be one pixel off, although rarely. So here I'm making sure it is correct.
				if (land.getHeight() != (int)bounds.getHeight())
				{
					land = ImageHelper.scaleFastByHeightAndWidth(land, (int)bounds.getWidth(), (int)bounds.getHeight());
				}
				ocean = ImageHelper.scaleByWidth(ocean, (int)bounds.getWidth());
				if (ocean.getHeight() != (int)bounds.getHeight())
				{
					ocean = ImageHelper.scaleFastByHeightAndWidth(ocean, (int)bounds.getWidth(), (int)bounds.getHeight());
				}
			}
			
			if (settings.drawBorder)
			{
				borderBackground = ImageHelper.deepCopy(ocean);
			}
		}
		
		public void doSetupThatNeedsGraph(MapSettings settings, GraphImpl graph)
		{
			if (shouldDrawRegionColors)
			{
				// The image "land" is generated but doesn't yet have colors.
				
				assignRandomRegionColors(graph, settings);
				
				regionIndexes = new BufferedImage(land.getWidth(), land.getHeight(), 
						BufferedImage.TYPE_BYTE_GRAY);
				graph.drawRegionIndexes(regionIndexes.createGraphics());
				
				land = drawRegionColors(graph, land, regionIndexes, landColorifyAlgorithm);
			}
			
			// Fixes a bug where graph width or height is not exactly the same as some image width and heights due to rounding issues.
			if (backgroundFromFilesNotGenerated)
			{
				land = ImageHelper.scaleByWidth(land, graph.getWidth());
				// Fixes a bug where graph width or height is not exactly the same as some image width and heights due to rounding issues.
				ocean = ImageHelper.scaleByWidth(ocean, graph.getWidth());
				if (settings.drawBorder)
				{
					borderBackground = ImageHelper.deepCopy(ocean);
				}
			}
		}
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
		
	private BufferedImage drawRegionColors(GraphImpl graph, BufferedImage fractalBG, BufferedImage pixelColors, 
			ImageHelper.ColorifyAlgorithm colorfiyAlgorithm)
	{	
		Color[] regionBackgroundColors = graph.regions.stream().map(
				reg -> reg.backgroundColor).toArray(size -> new Color[size]);
				
		return ImageHelper.colorifyMulti(fractalBG, regionBackgroundColors, pixelColors, colorfiyAlgorithm);
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
	
	public static Set<String> getAvailableBorderTypes()
	{
		File[] directories = new File(Paths.get("assets", "borders").toString()).listFiles(File::isDirectory);
		return new TreeSet<String>(Arrays.stream(directories).map(file -> file.getName()).collect(Collectors.toList()));
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
				
		ImageHelper.openImageInSystemDefaultEditor(map, "map_" + settings.randomSeed);

	}

}



