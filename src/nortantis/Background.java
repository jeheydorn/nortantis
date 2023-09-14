package nortantis;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import nortantis.graph.geom.Rectangle;
import nortantis.graph.voronoi.Center;
import nortantis.util.AssetsPath;
import nortantis.util.ImageHelper;
import nortantis.util.ImageHelper.ColorifyAlgorithm;
import nortantis.util.Range;

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
	int borderWidth;
	BufferedImage borderBackground;
	private boolean backgroundFromFilesNotGenerated;
	private boolean shouldDrawRegionColors;
	private ImageHelper.ColorifyAlgorithm landColorifyAlgorithm;
	// regionIndexes is a gray scale image where the level of each pixel is the
	// index of the region it is in.
	BufferedImage regionIndexes;
	private int borderWidthScaled;
	private String borderType;
	

	public Background(MapSettings settings, DimensionDouble mapBounds)
	{
		backgroundFromFilesNotGenerated = !settings.generateBackground && !settings.generateBackgroundFromTexture;
		shouldDrawRegionColors = settings.drawRegionColors && !backgroundFromFilesNotGenerated
				&& (!settings.generateBackgroundFromTexture || settings.colorizeLand);

		BufferedImage landGeneratedBackground;
		landColorifyAlgorithm = ColorifyAlgorithm.none;
		this.mapBounds = mapBounds;

		borderWidthScaled = settings.drawBorder ? (int) (settings.borderWidth * settings.resolution) : 0;
		borderType = settings.borderType;
		
		if (settings.generateBackground)
		{
			// Fractal generated background images

			final float fractalPower = 1.3f;
			BufferedImage oceanGeneratedBackground = FractalBGGenerator.generate(new Random(settings.backgroundRandomSeed), fractalPower,
					(int) mapBounds.getWidth() + borderWidthScaled * 2, (int) mapBounds.getHeight() + borderWidthScaled * 2, 0.75f);
			landGeneratedBackground = oceanGeneratedBackground;
			landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.algorithm2;
			borderBackground = ImageHelper.colorify(oceanGeneratedBackground, settings.oceanColor,
					ImageHelper.ColorifyAlgorithm.algorithm2);

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
				// Drawing region colors must be done later because it depends
				// on the graph.
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
				oceanGeneratedBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(new Random(settings.backgroundRandomSeed),
						ImageHelper.convertToGrayscale(texture), (int) mapBounds.getHeight() + borderWidthScaled * 2,
						(int) mapBounds.getWidth() + borderWidthScaled * 2);
				borderBackground = ImageHelper.colorify(oceanGeneratedBackground, settings.oceanColor,
						ImageHelper.ColorifyAlgorithm.algorithm3);
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
				oceanGeneratedBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(new Random(settings.backgroundRandomSeed),
						texture, (int) mapBounds.getHeight() + borderWidthScaled * 2, (int) mapBounds.getWidth() + borderWidthScaled * 2);
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
						// Drawing region colors must be done later because it
						// depends on the graph.
						land = removeBorderPadding(landGeneratedBackground);
					}
					else
					{
						land = ImageHelper.colorify(removeBorderPadding(landGeneratedBackground), settings.landColor,
								ImageHelper.ColorifyAlgorithm.algorithm3);
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
					// It's necessary to generate landGeneratedBackground at a
					// larger size including border width, then crop out the
					// part we want because
					// otherwise the random texture of the land won't match the
					// texture of the ocean.

					landGeneratedBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(
							new Random(settings.backgroundRandomSeed), ImageHelper.convertToGrayscale(texture),
							(int) mapBounds.getHeight() + borderWidthScaled * 2, (int) mapBounds.getWidth() + borderWidthScaled * 2);
					if (shouldDrawRegionColors)
					{
						// Drawing region colors must be done later because it
						// depends on the graph.
						land = removeBorderPadding(landGeneratedBackground);
					}
					else
					{
						land = ImageHelper.colorify(removeBorderPadding(landGeneratedBackground), settings.landColor,
								ImageHelper.ColorifyAlgorithm.algorithm3);
					}
					landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.algorithm3;
				}
				else
				{
					landGeneratedBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(
							new Random(settings.backgroundRandomSeed), texture, (int) mapBounds.getHeight() + borderWidthScaled * 2,
							(int) mapBounds.getWidth() + borderWidthScaled * 2);
					land = removeBorderPadding(landGeneratedBackground);
					landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.none;
				}
			}
		}
		else
		{
			throw new IllegalArgumentException("Creating maps from custom land and ocean background images is no longer supported.");
		}

		if (settings.drawRegionColors)
		{
			landBeforeRegionColoring = land;
		}

		if (borderBackground != null)
		{
			borderBounds = new Dimension(borderBackground.getWidth(), borderBackground.getHeight());
		}
		else
		{
			borderBounds = new Dimension((int) mapBounds.getWidth(), (int) mapBounds.getHeight());
		}
	}

	static DimensionDouble calcMapBoundsAndAdjustResolutionIfNeeded(MapSettings settings, Dimension maxDimensions)
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
			DimensionDouble mapBoundsPlusBorder = new DimensionDouble(mapBounds.getWidth() + borderWidth * 2,
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

	public BufferedImage removeBorderPadding(BufferedImage image)
	{
		return ImageHelper.extractRegion(image, borderWidthScaled, borderWidthScaled, (int) (image.getWidth() - borderWidthScaled * 2),
				(int) (image.getHeight() - borderWidthScaled * 2));
	}

	public void doSetupThatNeedsGraph(MapSettings settings, WorldGraph graph, Set<Center> centersToDraw, Rectangle drawBounds,
			Rectangle replaceBounds)
	{
		if (shouldDrawRegionColors)
		{
			// The image "land" is generated but doesn't yet have colors.

			if (drawBounds == null || centersToDraw == null)
			{
				regionIndexes = new BufferedImage(land.getWidth(), land.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
				graph.drawRegionIndexes(regionIndexes.createGraphics(), null, null);

				land = drawRegionColors(graph, landBeforeRegionColoring, regionIndexes, landColorifyAlgorithm, null);
			}
			else
			{
				// Update only a piece of the land
				regionIndexes = new BufferedImage((int) drawBounds.width, (int) drawBounds.height, BufferedImage.TYPE_BYTE_GRAY);
				graph.drawRegionIndexes(regionIndexes.createGraphics(), centersToDraw, drawBounds);
				BufferedImage landSnippet = drawRegionColors(graph, landBeforeRegionColoring, regionIndexes, landColorifyAlgorithm,
						new java.awt.Point((int) drawBounds.x, (int) drawBounds.y));
				java.awt.Rectangle boundsInSourceToCopyFrom = new java.awt.Rectangle((int) replaceBounds.x - (int) drawBounds.x,
						(int) replaceBounds.y - (int) drawBounds.y, (int) replaceBounds.width, (int) replaceBounds.height);
				ImageHelper.copySnippetFromSourceAndPasteIntoTarget(land, landSnippet, replaceBounds.upperLeftCornerAsAwtPoint(),
						boundsInSourceToCopyFrom, 0);
			}
		}

		// Fixes a bug where graph width or height is not exactly the same as
		// image width and heights due to rounding issues.
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

	private BufferedImage drawRegionColors(WorldGraph graph, BufferedImage fractalBG, BufferedImage pixelColors,
			ImageHelper.ColorifyAlgorithm colorfiyAlgorithm, java.awt.Point where)
	{
		if (graph.regions.isEmpty())
		{
			return ImageHelper.convertImageToType(fractalBG, BufferedImage.TYPE_INT_RGB);
		}

		Map<Integer, Color> regionBackgroundColors = new HashMap<>();
		for (Map.Entry<Integer, Region> regionEntry : graph.regions.entrySet())
		{
			regionBackgroundColors.put(regionEntry.getKey(), regionEntry.getValue().backgroundColor);
		}

		return ImageHelper.colorifyMulti(fractalBG, regionBackgroundColors, pixelColors, colorfiyAlgorithm, where);
	}

	public BufferedImage createOceanSnippet(Rectangle boundsToCopyFrom)
	{
		return ImageHelper.copySnippet(ocean, boundsToCopyFrom.toAwTRectangle());
	}
	
	public BufferedImage addBorder(BufferedImage map)
	{
		if (borderWidthScaled == 0)
		{
			return map;
		}
		
		BufferedImage result = ImageHelper.deepCopy(borderBackground);
		Graphics2D g = result.createGraphics();
		g.drawImage(map, borderWidthScaled, borderWidthScaled, null);
				
		Path allBordersPath = Paths.get(AssetsPath.getOverridablePath(), "borders");
		Path borderPath = Paths.get(allBordersPath.toString(), borderType);
		if (!Files.exists(borderPath))
		{
			throw new RuntimeException(
					"The selected border type '" + borderType + "' does not have a folder for images in " + allBordersPath + ".");
		}

		// Corners
		BufferedImage upperLeftCorner = loadImageWithStringInFileName(borderPath, "upper_left_corner.", false);
		if (upperLeftCorner != null)
		{
			upperLeftCorner = ImageHelper.scaleByWidth(upperLeftCorner, borderWidthScaled);
		}
		BufferedImage upperRightCorner = loadImageWithStringInFileName(borderPath, "upper_right_corner.", false);
		if (upperRightCorner != null)
		{
			upperRightCorner = ImageHelper.scaleByWidth(upperRightCorner, borderWidthScaled);
		}
		BufferedImage lowerLeftCorner = loadImageWithStringInFileName(borderPath, "lower_left_corner.", false);
		if (lowerLeftCorner != null)
		{
			lowerLeftCorner = ImageHelper.scaleByWidth(lowerLeftCorner, borderWidthScaled);
		}
		BufferedImage lowerRightCorner = loadImageWithStringInFileName(borderPath, "lower_right_corner.", false);
		if (lowerRightCorner != null)
		{
			lowerRightCorner = ImageHelper.scaleByWidth(lowerRightCorner, borderWidthScaled);
		}

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
				throw new RuntimeException("Couldn't find any corner images in " + borderPath);
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

		g.drawImage(upperLeftCorner, 0, 0, null);
		g.drawImage(upperRightCorner, (int) borderBounds.getWidth() - borderWidthScaled, 0, null);
		g.drawImage(lowerLeftCorner, 0, (int) borderBounds.getHeight() - borderWidthScaled, null);
		g.drawImage(lowerRightCorner, (int) borderBounds.getWidth() - borderWidthScaled,
				(int) borderBounds.getHeight() - borderWidthScaled, null);

		// Edges
		BufferedImage topEdge = loadImageWithStringInFileName(borderPath, "top_edge.", false);
		if (topEdge != null)
		{
			topEdge = ImageHelper.scaleByHeight(topEdge, borderWidthScaled);
		}
		BufferedImage bottomEdge = loadImageWithStringInFileName(borderPath, "bottom_edge.", false);
		if (bottomEdge != null)
		{
			bottomEdge = ImageHelper.scaleByHeight(bottomEdge, borderWidthScaled);
		}
		BufferedImage leftEdge = loadImageWithStringInFileName(borderPath, "left_edge.", false);
		if (leftEdge != null)
		{
			leftEdge = ImageHelper.scaleByWidth(leftEdge, borderWidthScaled);
		}
		BufferedImage rightEdge = loadImageWithStringInFileName(borderPath, "right_edge.", false);
		if (rightEdge != null)
		{
			rightEdge = ImageHelper.scaleByHeight(rightEdge, borderWidthScaled);
		}

		if (topEdge == null)
		{
			if (rightEdge != null)
			{
				topEdge = createEdgeFromEdge(rightEdge, EdgeType.Right, EdgeType.Top);
			}
			else if (leftEdge != null)
			{
				topEdge = createEdgeFromEdge(leftEdge, EdgeType.Left, EdgeType.Top);
			}
			else if (bottomEdge != null)
			{
				topEdge = createEdgeFromEdge(bottomEdge, EdgeType.Bottom, EdgeType.Top);
			}
			else
			{
				throw new RuntimeException("Couldn't find any edge images in " + borderPath);
			}
		}
		if (rightEdge == null)
		{
			rightEdge = createEdgeFromEdge(topEdge, EdgeType.Top, EdgeType.Right);
		}
		if (leftEdge == null)
		{
			leftEdge = createEdgeFromEdge(topEdge, EdgeType.Top, EdgeType.Left);
		}
		if (bottomEdge == null)
		{
			bottomEdge = createEdgeFromEdge(topEdge, EdgeType.Top, EdgeType.Bottom);
		}

		// Draw the edges

		// Top and bottom edges
		for (int i : new Range(2))
		{
			BufferedImage edge = i == 0 ? topEdge : bottomEdge;
			final int y = i == 0 ? 0 : map.getHeight() + borderWidthScaled;

			int end = map.getWidth() + borderWidthScaled;
			int increment = edge.getWidth();
			for (int x = borderWidthScaled; x < end; x += increment)
			{
				int distanceRemaining = end - x;
				if (distanceRemaining >= increment)
				{
					g.drawImage(edge, x, y, null);
				}
				else
				{
					// The image is too long/tall to draw in the remaining
					// space.
					BufferedImage partToDraw = ImageHelper.extractRegion(edge, 0, 0, distanceRemaining, borderWidthScaled);
					g.drawImage(partToDraw, x, y, null);
				}
			}
		}

		// Left and right edges
		for (int i : new Range(2))
		{
			BufferedImage edge = i == 0 ? leftEdge : rightEdge;
			final int x = i == 0 ? 0 : map.getWidth() + borderWidthScaled;

			int end = map.getHeight() + borderWidthScaled;
			int increment = edge.getHeight();
			for (int y = borderWidthScaled; y < end; y += increment)
			{
				int distanceRemaining = end - y;
				if (distanceRemaining >= increment)
				{
					g.drawImage(edge, x, y, null);
				}
				else
				{
					// The image is too long/tall to draw in the remaining
					// space.
					BufferedImage partToDraw = ImageHelper.extractRegion(edge, 0, 0, borderWidthScaled, distanceRemaining);
					g.drawImage(partToDraw, x, y, null);
				}
			}
		}

		g.dispose();
		
		return result;
	}
	
	public BufferedImage copyMapIntoBorder(BufferedImage mapWithoutBorder, BufferedImage border)
	{
		if (borderWidthScaled == 0)
		{
			return mapWithoutBorder;
		}

		Graphics2D g = border.createGraphics();
		border.getGraphics().drawImage(mapWithoutBorder, borderWidthScaled, borderWidthScaled, null);
		g.dispose();
		
		return border;
	}

	private BufferedImage createEdgeFromEdge(BufferedImage edgeIn, EdgeType edgeTypeIn, EdgeType outputType)
	{
		switch (edgeTypeIn)
		{
		case Bottom:
			switch (outputType)
			{
			case Bottom:
				return edgeIn;
			case Left:
				return ImageHelper.rotate90Degrees(edgeIn, true);
			case Right:
				return ImageHelper.rotate90Degrees(edgeIn, false);
			case Top:
				return ImageHelper.flipOnYAxis(edgeIn);
			}
		case Left:
			switch (outputType)
			{
			case Bottom:
				return ImageHelper.rotate90Degrees(edgeIn, false);
			case Left:
				return edgeIn;
			case Right:
				return ImageHelper.flipOnXAxis(edgeIn);
			case Top:
				return ImageHelper.rotate90Degrees(edgeIn, true);
			}
		case Right:
			switch (outputType)
			{
			case Bottom:
				return ImageHelper.rotate90Degrees(edgeIn, true);
			case Left:
				return ImageHelper.flipOnXAxis(edgeIn);
			case Right:
				return edgeIn;
			case Top:
				return ImageHelper.rotate90Degrees(edgeIn, false);
			}
		case Top:
			switch (outputType)
			{
			case Bottom:
				return ImageHelper.flipOnYAxis(edgeIn);
			case Left:
				return ImageHelper.rotate90Degrees(edgeIn, false);
			case Right:
				return ImageHelper.rotate90Degrees(edgeIn, true);
			case Top:
				return edgeIn;
			}
		}

		throw new IllegalStateException("Unable to create a border edge from the edges given");
	}

	private enum EdgeType
	{
		Top, Bottom, Left, Right
	}

	private BufferedImage createCornerFromCornerByFlipping(BufferedImage cornerIn, CornerType inputCornerType, CornerType outputType)
	{
		switch (inputCornerType)
		{
		case lowerLeft:
			switch (outputType)
			{
			case lowerLeft:
				return cornerIn;
			case lowerRight:
				return ImageHelper.flipOnXAxis(cornerIn);
			case upperLeft:
				return ImageHelper.flipOnYAxis(cornerIn);
			case upperRight:
				return ImageHelper.flipOnXAxis(ImageHelper.flipOnYAxis(cornerIn));
			}
			break;
		case lowerRight:
			switch (outputType)
			{
			case lowerLeft:
				return ImageHelper.flipOnXAxis(cornerIn);
			case lowerRight:
				return cornerIn;
			case upperLeft:
				return ImageHelper.flipOnXAxis(ImageHelper.flipOnYAxis(cornerIn));
			case upperRight:
				return ImageHelper.flipOnYAxis(cornerIn);
			}
		case upperLeft:
			switch (outputType)
			{
			case lowerLeft:
				return ImageHelper.flipOnYAxis(cornerIn);
			case lowerRight:
				return ImageHelper.flipOnXAxis(ImageHelper.flipOnYAxis(cornerIn));
			case upperLeft:
				return cornerIn;
			case upperRight:
				return ImageHelper.flipOnXAxis(cornerIn);
			}
		case upperRight:
			switch (outputType)
			{
			case lowerLeft:
				return ImageHelper.flipOnXAxis(ImageHelper.flipOnYAxis(cornerIn));
			case lowerRight:
				return ImageHelper.flipOnYAxis(cornerIn);
			case upperLeft:
				return ImageHelper.flipOnXAxis(cornerIn);
			case upperRight:
				return cornerIn;
			}
		}

		throw new IllegalStateException("Unable to flip corner image.");
	}

	private enum CornerType
	{
		upperLeft, upperRight, lowerLeft, lowerRight
	}

	private BufferedImage loadImageWithStringInFileName(Path path, String inFileName, boolean throwExceptionIfMissing)
	{
		File[] cornerArray = new File(path.toString()).listFiles(file -> file.getName().contains(inFileName));
		if (cornerArray.length == 0)
		{
			if (throwExceptionIfMissing)
				throw new RuntimeException(
						"Unable to find a file containing \"" + inFileName + "\" in the directory " + path.toAbsolutePath());
			else
				return null;
		}
		if (cornerArray.length > 1)
		{
			throw new RuntimeException("More than one file contains \"" + inFileName + "\" in the directory " + path.toAbsolutePath());
		}

		return ImageHelper.read(cornerArray[0].getPath());
	}
	
	public int getBorderWidthScaledByResolution()
	{
		return borderWidthScaled;
	}
}