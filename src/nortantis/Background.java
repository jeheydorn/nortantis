package nortantis;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Paths;
import java.util.Random;

import nortantis.util.ImageHelper;
import nortantis.util.ImageHelper.ColorifyAlgorithm;

/**
 * An assortment of things needed to draw the background.
 */
public class Background
{
	BufferedImage landBeforeRegionColoring;
	BufferedImage land;
	BufferedImage ocean;
	DimensionDouble mapBounds;
	Dimension borderBounds;
	BufferedImage borderBackground;
	private boolean backgroundFromFilesNotGenerated;
	boolean shouldDrawRegionColors;
	private ImageHelper.ColorifyAlgorithm landColorifyAlgorithm;
	// regionIndexes is a gray scale image where the level of each pixel is the index of the region it is in.
	BufferedImage regionIndexes;
	private int borderWidthScaled;
	
	public Background(MapSettings settings, Dimension maxDimensions)
	{
		backgroundFromFilesNotGenerated = !settings.generateBackground && !settings.generateBackgroundFromTexture && !settings.transparentBackground;
        shouldDrawRegionColors = settings.drawRegionColors 
		&& !backgroundFromFilesNotGenerated 
		&& (!settings.generateBackgroundFromTexture || settings.colorizeLand)
		&& !settings.transparentBackground;

        BufferedImage landGeneratedBackground;
		landColorifyAlgorithm = ColorifyAlgorithm.none;
		mapBounds = calcMapBoundsAndAdjustResolutionIfNeeded(settings, maxDimensions);
		if (settings.generateBackground || settings.generateBackgroundFromTexture || settings.transparentBackground)
		{

			borderWidthScaled = settings.drawBorder ? (int) (settings.borderWidth * settings.resolution) : 0;
										
			if (settings.generateBackground)
			{
				// Fractal generated background images
				
				BufferedImage oceanGeneratedBackground = FractalBGGenerator.generate(
						new Random(settings.backgroundRandomSeed), settings.fractalPower, 
						(int)mapBounds.getWidth() + borderWidthScaled * 2, (int)mapBounds.getHeight() + borderWidthScaled * 2, 0.75f);
				landGeneratedBackground = oceanGeneratedBackground;
				landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.algorithm2;
				borderBackground = ImageHelper.colorify(oceanGeneratedBackground, settings.oceanColor, ImageHelper.ColorifyAlgorithm.algorithm2);
				
				if (settings.drawBorder)
				{
					ocean = removeBorderPadding(borderBackground);
				}
				else
				{
					ocean = borderBackground;
					borderBackground = null;
				}
				
				if (shouldDrawRegionColors)
				{
					// Drawing region colors must be done later because it depends on the graph.
					land = removeBorderPadding(landGeneratedBackground);
				}
				else
				{
					land = ImageHelper.colorify(removeBorderPadding(landGeneratedBackground), settings.landColor, landColorifyAlgorithm);
					landGeneratedBackground = null;
				}
			}
			else if (settings.generateBackgroundFromTexture)
			{
				// Generate the background images from a texture
				
				BufferedImage texture;
				try
				{
					texture = ImageCache.getInstance().getImageFromFile(Paths.get(settings.backgroundTextureImage));
				}
				catch (RuntimeException e)
				{
					throw new RuntimeException("Unable to read the texture image file name \"" + settings.backgroundTextureImage + "\"", e);
				}
				
				BufferedImage oceanGeneratedBackground;
				if (settings.colorizeOcean)
				{
					oceanGeneratedBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(
						new Random(settings.backgroundRandomSeed), ImageHelper.convertToGrayscale(texture), 
						(int)mapBounds.getHeight() + borderWidthScaled * 2, (int)mapBounds.getWidth() + borderWidthScaled * 2);
					borderBackground = ImageHelper.colorify(oceanGeneratedBackground, settings.oceanColor, ImageHelper.ColorifyAlgorithm.algorithm3);
					if (settings.drawBorder)
					{
						ocean = ImageHelper.deepCopy(removeBorderPadding(borderBackground));
					}
					else
					{
						ocean = borderBackground;
						borderBackground = null;
					}
				}
				else
				{
					oceanGeneratedBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(
							new Random(settings.backgroundRandomSeed), texture, (int)mapBounds.getHeight() + borderWidthScaled *  2, 
							(int)mapBounds.getWidth() + borderWidthScaled * 2);
					if (settings.drawBorder)
					{
						ocean = removeBorderPadding(oceanGeneratedBackground);
						borderBackground = oceanGeneratedBackground;
					}
					else
					{
						ocean = oceanGeneratedBackground;
					}
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
							land = removeBorderPadding(landGeneratedBackground);
						}
						else
						{
							land = ImageHelper.colorify(removeBorderPadding(landGeneratedBackground), settings.landColor, ImageHelper.ColorifyAlgorithm.algorithm3);
						}
					}
					else
					{
						landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.none;
						land = removeBorderPadding(landGeneratedBackground);
					}
				}
				else
				{
					if (settings.colorizeLand)
					{
						// It's necessary to generate landGeneratedBackground at a larger size including border width, then crop out the part we want because 
						// otherwise the random texture of the land won't match the texture of the ocean.
						
						landGeneratedBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(
							new Random(settings.backgroundRandomSeed), ImageHelper.convertToGrayscale(texture), 
							(int)mapBounds.getHeight() + borderWidthScaled * 2,
							(int)mapBounds.getWidth() + borderWidthScaled * 2);
						if (shouldDrawRegionColors)
						{
							// Drawing region colors must be done later because it depends on the graph.
							land = removeBorderPadding(landGeneratedBackground);
						}
						else
						{
							land = ImageHelper.colorify(removeBorderPadding(landGeneratedBackground), settings.landColor, ImageHelper.ColorifyAlgorithm.algorithm3);
						}
						landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.algorithm3;
					}
					else
					{
						landGeneratedBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(
								new Random(settings.backgroundRandomSeed), texture, 
								(int)mapBounds.getHeight() + borderWidthScaled * 2, 
								(int)mapBounds.getWidth() + borderWidthScaled * 2);
						land = removeBorderPadding(landGeneratedBackground);
						landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.none;
					}
				}
			}
			else
			{
				// Transparent background
				landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.none;
				land = ImageHelper.createWhiteTransparentImage((int)mapBounds.getWidth(), (int)mapBounds.getHeight());
				ocean = land;
				landGeneratedBackground = land;
			}
			
			if (settings.drawRegionColors)
			{
				landBeforeRegionColoring = land;
			}
		}
		else
		{
			// The background images are from files
			if (settings.drawBorder)
			{
				throw new IllegalArgumentException("Drawing a border with background images from files is not supported. Uncheck Border > Draw border.");
			}
			
			try
			{
				land = ImageCache.getInstance().getImageFromFile((new File(settings.landBackgroundImage).toPath()));
				land = ImageHelper.convertToBufferedImageOfType(land, BufferedImage.TYPE_INT_RGB);
			}
			catch(Exception e)
			{
				throw new IllegalArgumentException("Error while reading land background image from " 
						+ settings.landBackgroundImage + ": " + e.getMessage(), e);
			}
			try
			{
				ocean = ImageCache.getInstance().getImageFromFile((new File(settings.oceanBackgroundImage).toPath()));
				ocean = ImageHelper.convertToBufferedImageOfType(ocean, BufferedImage.TYPE_INT_RGB);
			}
			catch(Exception e)
			{
				throw new IllegalArgumentException("Error while reading ocean background image from " 
						+ settings.oceanBackgroundImage + ": " + e.getMessage(), e);
			}

			mapBounds = new DimensionDouble(land.getWidth()*settings.resolution, land.getHeight()*settings.resolution);
			if (maxDimensions != null)
			{
				mapBounds = ImageHelper.fitDimensionsWithinBoundingBox(maxDimensions, mapBounds.getWidth(),
						mapBounds.getHeight());
			}
			land = ImageHelper.scaleByWidth(land, (int)mapBounds.getWidth());
			// The library I'm using to scale images and make them look nice has a bug where the width or height might be one pixel off, although rarely. So here I'm making sure it is correct.
			if (land.getHeight() != (int)mapBounds.getHeight())
			{
				land = ImageHelper.scaleFastByHeightAndWidth(land, (int)mapBounds.getWidth(), (int)mapBounds.getHeight());
			}
			ocean = ImageHelper.scaleByWidth(ocean, (int)mapBounds.getWidth());
			if (ocean.getHeight() != (int)mapBounds.getHeight())
			{
				ocean = ImageHelper.scaleFastByHeightAndWidth(ocean, (int)mapBounds.getWidth(), (int)mapBounds.getHeight());
			}
		}
		
		if (borderBackground != null)
		{
			borderBounds = new Dimension(borderBackground.getWidth(), borderBackground.getHeight());
		}
		else
		{
			borderBounds = new Dimension((int)mapBounds.getWidth(), (int)mapBounds.getHeight());
		}
	}
			
	DimensionDouble calcMapBoundsAndAdjustResolutionIfNeeded(MapSettings settings, Dimension maxDimensions)
	{
		if (settings.generateBackground || settings.generateBackgroundFromTexture || settings.transparentBackground)
		{
			DimensionDouble mapBounds = new DimensionDouble(settings.generatedWidth * settings.resolution,
					settings.generatedHeight * settings.resolution);
			if (maxDimensions != null)
			{
				int borderWidth = 0;
				if (settings.drawBorder)
				{
					borderWidth = (int) (settings.borderWidth * settings.resolution);
				}
				DimensionDouble mapBoundsPlusBorder = new DimensionDouble(
						mapBounds.getWidth() + borderWidth * 2,
						mapBounds.getHeight() + borderWidth * 2);

				DimensionDouble newBounds = ImageHelper.fitDimensionsWithinBoundingBox(maxDimensions, mapBoundsPlusBorder.getWidth(),
						mapBoundsPlusBorder.getHeight());
				// Change the resolution to match the new bounds.
				settings.resolution *= newBounds.width / mapBoundsPlusBorder.width;
				
				DimensionDouble scaledMapBounds = new DimensionDouble(settings.generatedWidth * settings.resolution, 
						settings.generatedHeight * settings.resolution);
				mapBounds = scaledMapBounds;
			}
			return mapBounds;
		}
		else
		{
			// The background is from files.
			BufferedImage land;
			try
			{
				land = ImageCache.getInstance().getImageFromFile(new File(settings.landBackgroundImage).toPath());
			}
			catch(Exception e)
			{
				throw new IllegalArgumentException("Error while reading land background image from " 
						+ settings.landBackgroundImage + ": " + e.getMessage());
			}

			return new DimensionDouble(land.getWidth()*settings.resolution, land.getHeight()*settings.resolution);
		}
	}
	
	private BufferedImage removeBorderPadding(BufferedImage image)
	{
		return ImageHelper.extractRegion(image, borderWidthScaled, borderWidthScaled, 
				(int)(image.getWidth() - borderWidthScaled * 2),
				(int)(image.getHeight() - borderWidthScaled * 2));
	}
	
	public void doSetupThatNeedsGraph(MapSettings settings, WorldGraph graph)
	{
		if (shouldDrawRegionColors)
		{
			// The image "land" is generated but doesn't yet have colors.
			
			regionIndexes = new BufferedImage(land.getWidth(), land.getHeight(), 
					BufferedImage.TYPE_BYTE_GRAY);
			graph.drawRegionIndexes(regionIndexes.createGraphics());
			
			land = drawRegionColors(graph, landBeforeRegionColoring, regionIndexes, landColorifyAlgorithm);
		}
		
		// Fixes a bug where graph width or height is not exactly the same as image width and heights due to rounding issues.
		if (backgroundFromFilesNotGenerated)
		{
			if (land.getWidth() != graph.getWidth())
			{
				land = ImageHelper.scaleByWidth(land, graph.getWidth());
			}
			if (ocean.getWidth() != graph.getWidth())
			{
				ocean = ImageHelper.scaleByWidth(ocean, graph.getWidth());
			}
		}
	}
	
	BufferedImage drawRegionColors(WorldGraph graph, BufferedImage fractalBG, BufferedImage pixelColors, 
			ImageHelper.ColorifyAlgorithm colorfiyAlgorithm)
	{	
		if (graph.regions.isEmpty())
		{
			return ImageHelper.convertImageToType(fractalBG, BufferedImage.TYPE_INT_RGB);
		}
		
		Color[] regionBackgroundColors = graph.regions.stream().map(
				reg -> reg.backgroundColor).toArray(size -> new Color[size]);
				
		return ImageHelper.colorifyMulti(fractalBG, regionBackgroundColors, pixelColors, colorfiyAlgorithm);
	}

}