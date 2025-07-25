package nortantis;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import nortantis.geom.Dimension;
import nortantis.geom.IntPoint;
import nortantis.geom.IntRectangle;
import nortantis.geom.Rectangle;
import nortantis.graph.voronoi.Center;
import nortantis.platform.AlphaComposite;
import nortantis.platform.Color;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.Painter;
import nortantis.util.Assets;
import nortantis.util.ImageHelper;
import nortantis.util.ImageHelper.ColorifyAlgorithm;
import nortantis.util.Range;
import nortantis.util.Tuple2;

/**
 * An assortment of things needed to draw the background.
 */
public class Background
{
	Image landBeforeRegionColoring;
	Image land;
	Image ocean;
	Dimension mapBounds;
	Dimension borderBounds;
	Image borderBackground;
	private boolean shouldDrawRegionColors;
	private ImageHelper.ColorifyAlgorithm landColorifyAlgorithm;
	private ImageHelper.ColorifyAlgorithm oceanColorifyAlgorithm;
	// regionIndexes is a gray scale image where the level of each pixel is the
	// index of the region it is in.
	Image regionIndexes;
	private int borderWidthScaled;
	private NamedResource borderResouce;
	private Image upperLeftCorner;
	private Image upperRightCorner;
	private Image lowerLeftCorner;
	private Image lowerRightCorner;
	private int cornerWidth;
	private boolean hasInsetCorners;
	private String customImagesPath;

	public Background(MapSettings settings, Dimension mapBounds, WarningLogger warningLogger)
	{
		customImagesPath = settings.customImagesPath;
		shouldDrawRegionColors = settings.drawRegionColors && (!settings.generateBackgroundFromTexture || settings.colorizeLand);

		Image landGeneratedBackground;
		landColorifyAlgorithm = ColorifyAlgorithm.none;
		this.mapBounds = mapBounds;

		borderWidthScaled = calcBorderWidthScaledByResolution(settings);
		borderResouce = settings.borderResource;

		if (settings.generateBackground)
		{
			// Fractal generated background images

			final float fractalPower = 1.3f;
			Image oceanGeneratedBackground = FractalBGGenerator.generate(new Random(settings.backgroundRandomSeed), fractalPower,
					((int) mapBounds.width) + borderWidthScaled * 2, ((int) mapBounds.height) + borderWidthScaled * 2, 0.75f);
			landGeneratedBackground = oceanGeneratedBackground;
			landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.algorithm2;
			oceanColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.algorithm2;

			if (settings.borderColorOption == BorderColorOption.Ocean_color)
			{
				borderBackground = ImageHelper.colorify(oceanGeneratedBackground, settings.oceanColor, oceanColorifyAlgorithm);
				ocean = borderBackground;
			}
			else
			{
				if (settings.drawBorder)
				{
					borderBackground = ImageHelper.colorify(oceanGeneratedBackground, settings.borderColor, oceanColorifyAlgorithm,
							settings.oceanColor.hasTransparency());
				}
				ocean = ImageHelper.colorify(oceanGeneratedBackground, settings.oceanColor, oceanColorifyAlgorithm);
			}

			if (settings.drawBorder)
			{
				ocean = removeBorderPadding(ocean);
			}
			else
			{
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

			Image texture;
			Tuple2<Path, String> tuple = settings.getBackgroundImagePath();
			Path texturePath = tuple.getFirst();
			String warning = tuple.getSecond();
			if (!StringUtils.isEmpty(warning))
			{
				warningLogger.addWarningMessage(warning);
			}
			try
			{
				texture = ImageCache.getInstance(settings.backgroundTextureResource.artPack, settings.customImagesPath)
						.getImageFromFile(texturePath);
			}
			catch (RuntimeException e)
			{
				throw new RuntimeException("Unable to read the texture image file name \"" + texturePath + "\"", e);
			}

			oceanColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.algorithm3;

			Image oceanGeneratedBackground;
			if (settings.colorizeOcean)
			{
				oceanGeneratedBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(new Random(settings.backgroundRandomSeed),
						ImageHelper.convertToGrayscale(texture), ((int) mapBounds.height) + borderWidthScaled * 2,
						((int) mapBounds.width) + borderWidthScaled * 2);

				if (settings.borderColorOption == BorderColorOption.Ocean_color)
				{
					borderBackground = ImageHelper.colorify(oceanGeneratedBackground, settings.oceanColor, oceanColorifyAlgorithm);
					ocean = borderBackground;
				}
				else
				{
					if (settings.drawBorder)
					{
						borderBackground = ImageHelper.colorify(oceanGeneratedBackground, settings.borderColor, oceanColorifyAlgorithm,
								settings.oceanColor.hasTransparency());
					}
					ocean = ImageHelper.colorify(oceanGeneratedBackground, settings.oceanColor, oceanColorifyAlgorithm);
				}

				if (settings.drawBorder)
				{
					ocean = removeBorderPadding(ocean);
				}
				else
				{
					borderBackground = null;
				}
			}
			else
			{
				oceanGeneratedBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(new Random(settings.backgroundRandomSeed),
						texture, ((int) mapBounds.height) + borderWidthScaled * 2, ((int) mapBounds.width) + borderWidthScaled * 2);
				if (settings.drawBorder)
				{
					ocean = removeBorderPadding(oceanGeneratedBackground);

					if (settings.borderColorOption == BorderColorOption.Ocean_color)
					{
						borderBackground = oceanGeneratedBackground;
					}
					else
					{
						borderBackground = ImageHelper.colorify(ImageHelper.convertToGrayscale(oceanGeneratedBackground),
								settings.borderColor, oceanColorifyAlgorithm);
					}
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
							((int) mapBounds.height) + borderWidthScaled * 2, ((int) mapBounds.width) + borderWidthScaled * 2);
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
							new Random(settings.backgroundRandomSeed), texture, ((int) mapBounds.height) + borderWidthScaled * 2,
							((int) mapBounds.width) + borderWidthScaled * 2);
					land = removeBorderPadding(landGeneratedBackground);
					landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.none;
				}
			}
		}
		else if (settings.solidColorBackground)
		{
			Image background = Image.create(((int) mapBounds.width) + borderWidthScaled * 2,
					((int) mapBounds.height) + borderWidthScaled * 2, ImageType.Grayscale8Bit);
			landColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.solidColor;
			oceanColorifyAlgorithm = ImageHelper.ColorifyAlgorithm.solidColor;

			if (settings.borderColorOption == BorderColorOption.Ocean_color)
			{
				borderBackground = ImageHelper.colorify(background, settings.oceanColor, oceanColorifyAlgorithm);
				ocean = borderBackground;
			}
			else
			{
				if (settings.drawBorder)
				{
					borderBackground = ImageHelper.colorify(background, settings.borderColor, oceanColorifyAlgorithm,
							settings.oceanColor.hasTransparency());
				}
				ocean = ImageHelper.colorify(background, settings.oceanColor, oceanColorifyAlgorithm);
			}

			if (settings.drawBorder)
			{
				ocean = removeBorderPadding(ocean);
			}
			else
			{
				borderBackground = null;
			}

			if (shouldDrawRegionColors)
			{
				// Drawing region colors must be done later because it depends on the graph.
				land = removeBorderPadding(background);
			}
			else
			{
				land = ImageHelper.colorify(removeBorderPadding(background), settings.landColor, landColorifyAlgorithm);
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
			borderBounds = new Dimension(mapBounds.width, mapBounds.height);
		}
	}

	static Dimension calcMapBoundsAndAdjustResolutionIfNeeded(MapSettings settings, Dimension maxDimensions)
	{
		Dimension mapBounds;
		Dimension sizeFromSettingsAt100PercentResolution;
		if (settings.rightRotationCount == 1 || settings.rightRotationCount == 3)
		{
			sizeFromSettingsAt100PercentResolution = new Dimension(settings.generatedHeight, settings.generatedWidth);
		}
		else
		{
			sizeFromSettingsAt100PercentResolution = new Dimension(settings.generatedWidth, settings.generatedHeight);
		}
		mapBounds = sizeFromSettingsAt100PercentResolution.mult(settings.resolution);
		if (maxDimensions != null)
		{
			int borderWidth = 0;
			if (settings.drawBorder)
			{
				borderWidth = (int) (settings.borderWidth * settings.resolution);
			}
			Dimension mapBoundsPlusBorder = new Dimension(mapBounds.width + borderWidth * 2, mapBounds.height + borderWidth * 2);

			Dimension newBounds = ImageHelper.fitDimensionsWithinBoundingBox(maxDimensions, mapBoundsPlusBorder.width,
					mapBoundsPlusBorder.height);
			// Change the resolution to match the new bounds.
			settings.resolution *= ((double) newBounds.width) / mapBoundsPlusBorder.width;

			Dimension scaledMapBounds = sizeFromSettingsAt100PercentResolution.mult(settings.resolution);
			mapBounds = scaledMapBounds;
		}
		return mapBounds;
	}

	public Image removeBorderPadding(Image image)
	{
		return ImageHelper.copySnippet(image, borderWidthScaled, borderWidthScaled, (int) (image.getWidth() - borderWidthScaled * 2),
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
				regionIndexes = Image.create(land.getWidth(), land.getHeight(), ImageType.RGB);
				graph.drawRegionIndexes(regionIndexes.createPainter(), null, null);

				land = drawRegionColors(graph, landBeforeRegionColoring, regionIndexes, landColorifyAlgorithm, null);
			}
			else
			{
				// Update only a piece of the land
				regionIndexes = Image.create((int) drawBounds.width, (int) drawBounds.height, ImageType.RGB);
				graph.drawRegionIndexes(regionIndexes.createPainter(), centersToDraw, drawBounds);
				Image landSnippet = drawRegionColors(graph, landBeforeRegionColoring, regionIndexes, landColorifyAlgorithm,
						new IntPoint((int) drawBounds.x, (int) drawBounds.y));
				IntRectangle boundsInSourceToCopyFrom = new IntRectangle((int) replaceBounds.x - (int) drawBounds.x,
						(int) replaceBounds.y - (int) drawBounds.y, (int) replaceBounds.width, (int) replaceBounds.height);
				ImageHelper.copySnippetFromSourceAndPasteIntoTarget(land, landSnippet, replaceBounds.upperLeftCorner().toIntPoint(),
						boundsInSourceToCopyFrom, 0);
			}
		}
	}

	private Image drawRegionColors(WorldGraph graph, Image fractalBG, Image pixelColors, ImageHelper.ColorifyAlgorithm colorfiyAlgorithm,
			IntPoint where)
	{
		if (graph.regions.isEmpty())
		{
			return ImageHelper.convertImageToType(fractalBG, ImageType.RGB);
		}

		Map<Integer, Color> regionBackgroundColors = new HashMap<>();
		for (Map.Entry<Integer, Region> regionEntry : graph.regions.entrySet())
		{
			regionBackgroundColors.put(regionEntry.getKey(), regionEntry.getValue().backgroundColor);
		}

		return ImageHelper.colorifyMulti(fractalBG, regionBackgroundColors, pixelColors, colorfiyAlgorithm, where);
	}

	public Image createOceanSnippet(Rectangle boundsToCopyFrom)
	{
		return ImageHelper.copySnippet(ocean, boundsToCopyFrom.toIntRectangle());
	}

	public Image addBorder(Image map)
	{
		if (borderWidthScaled == 0)
		{
			return map;
		}

		Image result;
		if (map.hasAlpha() && !borderBackground.hasAlpha())
		{
			result = borderBackground.copyAndAddAlphaChanel();
		}
		else
		{
			result = borderBackground.deepCopy();
		}

		{
			Painter p = result.createPainter();
			if (result.hasAlpha())
			{
				p.setAlphaComposite(AlphaComposite.Src);
			}
			p.drawImage(map, borderWidthScaled, borderWidthScaled);
		}

		Path artPackPath = Assets.getArtPackPath(borderResouce.artPack, customImagesPath);
		if (artPackPath == null)
		{
			throw new RuntimeException("Unable to draw the border because the selected border type, '" + borderResouce.name
					+ "', is from the art pack '" + borderResouce.artPack + "', which does not exist.");
		}
		Path allBordersPath = Paths.get(artPackPath.toString(), "borders");
		Path borderPath = Paths.get(allBordersPath.toString(), borderResouce.name);
		if (!Assets.exists(borderPath.toString()))
		{
			throw new RuntimeException(
					"The selected border type '" + borderResouce + "' does not have a folder for images in " + allBordersPath + ".");
		}

		int edgeOriginalWidth = 0;
		// Edges
		Image topEdge = loadImageWithStringInFileName(borderPath, "top_edge.", false);
		if (topEdge != null)
		{
			edgeOriginalWidth = topEdge.getHeight();
			topEdge = ImageHelper.scaleByHeight(topEdge, borderWidthScaled);
		}
		Image bottomEdge = loadImageWithStringInFileName(borderPath, "bottom_edge.", false);
		if (bottomEdge != null)
		{
			edgeOriginalWidth = bottomEdge.getHeight();
			bottomEdge = ImageHelper.scaleByHeight(bottomEdge, borderWidthScaled);
		}
		Image leftEdge = loadImageWithStringInFileName(borderPath, "left_edge.", false);
		if (leftEdge != null)
		{
			edgeOriginalWidth = leftEdge.getWidth();
			leftEdge = ImageHelper.scaleByWidth(leftEdge, borderWidthScaled);
		}
		Image rightEdge = loadImageWithStringInFileName(borderPath, "right_edge.", false);
		if (rightEdge != null)
		{
			edgeOriginalWidth = rightEdge.getWidth();
			rightEdge = ImageHelper.scaleByWidth(rightEdge, borderWidthScaled);
		}

		if (topEdge == null)
		{
			if (rightEdge != null)
			{
				topEdge = createEdgeFromEdge(rightEdge, BorderEdgeType.Right, BorderEdgeType.Top);
			}
			else if (leftEdge != null)
			{
				topEdge = createEdgeFromEdge(leftEdge, BorderEdgeType.Left, BorderEdgeType.Top);
			}
			else if (bottomEdge != null)
			{
				topEdge = createEdgeFromEdge(bottomEdge, BorderEdgeType.Bottom, BorderEdgeType.Top);
			}
			else
			{
				throw new RuntimeException("Border cannot be drawn. Couldn't find any edge images in " + borderPath);
			}
		}
		if (rightEdge == null)
		{
			rightEdge = createEdgeFromEdge(topEdge, BorderEdgeType.Top, BorderEdgeType.Right);
		}
		if (leftEdge == null)
		{
			leftEdge = createEdgeFromEdge(topEdge, BorderEdgeType.Top, BorderEdgeType.Left);
		}
		if (bottomEdge == null)
		{
			bottomEdge = createEdgeFromEdge(topEdge, BorderEdgeType.Top, BorderEdgeType.Bottom);
		}

		int cornerOriginalWidth = 0;

		// Corners
		upperLeftCorner = loadImageWithStringInFileName(borderPath, "upper_left_corner.", false);
		if (upperLeftCorner != null)
		{
			cornerOriginalWidth = upperLeftCorner.getWidth();
		}
		upperRightCorner = loadImageWithStringInFileName(borderPath, "upper_right_corner.", false);
		if (upperRightCorner != null)
		{
			cornerOriginalWidth = upperRightCorner.getWidth();
		}
		lowerLeftCorner = loadImageWithStringInFileName(borderPath, "lower_left_corner.", false);
		if (lowerLeftCorner != null)
		{
			cornerOriginalWidth = lowerLeftCorner.getWidth();
		}
		lowerRightCorner = loadImageWithStringInFileName(borderPath, "lower_right_corner.", false);
		if (lowerRightCorner != null)
		{
			cornerOriginalWidth = lowerRightCorner.getWidth();
		}

		if (cornerOriginalWidth == 0)
		{
			throw new RuntimeException("Border cannot be drawn. Could not find any corner images in " + borderPath);
		}

		if (edgeOriginalWidth == 0)
		{
			throw new RuntimeException("Border cannot be drawn. Could not find any edge images in " + borderPath);
		}

		if (cornerOriginalWidth <= edgeOriginalWidth)
		{
			hasInsetCorners = false;
			cornerWidth = borderWidthScaled;
		}
		else
		{
			hasInsetCorners = true;
			cornerWidth = (int) (borderWidthScaled * (((double) cornerOriginalWidth) / ((double) edgeOriginalWidth)));
		}

		if (upperLeftCorner != null)
		{
			upperLeftCorner = ImageHelper.scaleByWidth(upperLeftCorner, cornerWidth);
		}
		if (upperRightCorner != null)
		{
			upperRightCorner = ImageHelper.scaleByWidth(upperRightCorner, cornerWidth);
		}
		if (lowerLeftCorner != null)
		{
			lowerLeftCorner = ImageHelper.scaleByWidth(lowerLeftCorner, cornerWidth);
		}
		if (lowerRightCorner != null)
		{
			lowerRightCorner = ImageHelper.scaleByWidth(lowerRightCorner, cornerWidth);
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
				throw new RuntimeException("Border cannot be drawn. Couldn't find any corner images in " + borderPath);
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

		drawUpperLeftCorner(result, new IntPoint(0, 0));
		drawUpperRightCorner(result, new IntPoint(0, 0));
		drawLowerLeftCorner(result, new IntPoint(0, 0));
		drawLowerRightCorner(result, new IntPoint(0, 0));


		// Draw the edges

		Painter p = result.createPainter();
		p.setAlphaComposite(alphaCompositeForDrawingCornersAndEdges);
		// Top and bottom edges
		for (int i : new Range(2))
		{
			Image edge = i == 0 ? topEdge : bottomEdge;
			final int y = i == 0 ? 0 : map.getHeight() + borderWidthScaled;

			int end = ((int) borderBounds.width) - cornerWidth;
			int increment = edge.getWidth();
			for (int x = cornerWidth; x < end; x += increment)
			{
				int distanceRemaining = end - x;
				if (distanceRemaining >= increment)
				{
					p.drawImage(edge, x, y);
				}
				else
				{
					// The image is too long/tall to draw in the remaining
					// space.
					Image partToDraw = ImageHelper.copySnippet(edge, 0, 0, distanceRemaining, borderWidthScaled);
					p.drawImage(partToDraw, x, y);
				}
			}
		}

		// Left and right edges
		for (int i : new Range(2))
		{
			Image edge = i == 0 ? leftEdge : rightEdge;
			final int x = i == 0 ? 0 : map.getWidth() + borderWidthScaled;

			int end = ((int) borderBounds.height) - cornerWidth;
			int increment = edge.getHeight();
			for (int y = cornerWidth; y < end; y += increment)
			{
				int distanceRemaining = end - y;
				if (distanceRemaining >= increment)
				{
					p.drawImage(edge, x, y);
				}
				else
				{
					// The image is too long/tall to draw in the remaining
					// space.
					Image partToDraw = ImageHelper.copySnippet(edge, 0, 0, borderWidthScaled, distanceRemaining);
					p.drawImage(partToDraw, x, y);
				}
			}
		}

		p.dispose();

		return result;
	}

	private final AlphaComposite alphaCompositeForDrawingCornersAndEdges = AlphaComposite.SrcOver;

	private void drawUpperLeftCorner(Image target, IntPoint drawOffset)
	{
		// If the corner protrudes into the map, then erase the map in the area the corner will be drawn on.
		if (hasInsetCorners)
		{
			ImageHelper.copySnippetFromSourceAndPasteIntoTarget(target, borderBackground, new IntPoint(0, 0).subtract(drawOffset),
					new IntRectangle(0, 0, upperLeftCorner.getWidth(), upperLeftCorner.getHeight()), 0);
		}
		Painter p = target.createPainter();
		p.setAlphaComposite(alphaCompositeForDrawingCornersAndEdges);
		p.translate(-drawOffset.x, -drawOffset.y);
		p.drawImage(upperLeftCorner, 0, 0);
	}

	private void drawUpperRightCorner(Image target, IntPoint drawOffset)
	{
		// If the corner protrudes into the map, then erase the map in the area the corner will be drawn on.
		if (hasInsetCorners)
		{
			ImageHelper.copySnippetFromSourceAndPasteIntoTarget(target, borderBackground,
					new IntPoint(((int) borderBounds.width) - cornerWidth, 0).subtract(drawOffset), new IntRectangle(
							((int) borderBounds.width) - cornerWidth, 0, upperRightCorner.getWidth(), upperRightCorner.getHeight()),
					0);
		}
		Painter p = target.createPainter();
		p.setAlphaComposite(alphaCompositeForDrawingCornersAndEdges);
		p.translate(-drawOffset.x, -drawOffset.y);
		p.drawImage(upperRightCorner, ((int) borderBounds.width) - cornerWidth, 0);
	}

	private void drawLowerLeftCorner(Image target, IntPoint drawOffset)
	{
		// If the corner protrudes into the map, then erase the map in the area the corner will be drawn on.
		if (hasInsetCorners)
		{
			ImageHelper.copySnippetFromSourceAndPasteIntoTarget(target, borderBackground,
					new IntPoint(0, ((int) borderBounds.height) - cornerWidth).subtract(drawOffset),
					new IntRectangle(0, ((int) borderBounds.height) - cornerWidth, lowerLeftCorner.getWidth(), lowerLeftCorner.getHeight()),
					0);

		}
		Painter p = target.createPainter();
		p.setAlphaComposite(alphaCompositeForDrawingCornersAndEdges);
		p.translate(-drawOffset.x, -drawOffset.y);
		p.drawImage(lowerLeftCorner, 0, ((int) borderBounds.height) - cornerWidth);
	}

	private void drawLowerRightCorner(Image target, IntPoint drawOffset)
	{
		// If the corner protrudes into the map, then erase the map in the area the corner will be drawn on.
		if (hasInsetCorners)
		{
			ImageHelper.copySnippetFromSourceAndPasteIntoTarget(target, borderBackground,
					new IntPoint(((int) borderBounds.width) - cornerWidth, ((int) borderBounds.height) - cornerWidth).subtract(drawOffset),
					new IntRectangle(((int) borderBounds.width) - cornerWidth, ((int) borderBounds.height) - cornerWidth,
							lowerRightCorner.getWidth(), lowerRightCorner.getHeight()),
					0);

		}
		Painter p = target.createPainter();
		p.setAlphaComposite(alphaCompositeForDrawingCornersAndEdges);
		p.translate(-drawOffset.x, -drawOffset.y);
		p.drawImage(lowerRightCorner, ((int) borderBounds.width) - cornerWidth, ((int) borderBounds.height) - cornerWidth);
	}

	public void drawInsetCornersIfBoundsTouchesThem(Image target, Rectangle drawBoundsBeforeBorder)
	{
		if (borderWidthScaled == 0)
		{
			return;
		}

		IntPoint drawOffset = new IntPoint(drawBoundsBeforeBorder.toIntRectangle().x + borderWidthScaled,
				drawBoundsBeforeBorder.toIntRectangle().y + borderWidthScaled);
		Rectangle bounds = drawBoundsBeforeBorder.translate(borderWidthScaled, borderWidthScaled);
		Rectangle upperLeftCornerBounds = new IntRectangle(0, 0, upperLeftCorner.getWidth(), upperLeftCorner.getHeight()).toRectangle();
		if (upperLeftCornerBounds.overlaps(bounds))
		{
			drawUpperLeftCorner(target, drawOffset);
		}
		Rectangle upperRightCornerBounds = new IntRectangle(((int) borderBounds.width) - cornerWidth, 0, upperRightCorner.getWidth(),
				upperRightCorner.getHeight()).toRectangle();
		if (upperRightCornerBounds.overlaps(bounds))
		{
			drawUpperRightCorner(target, drawOffset);
		}
		Rectangle lowerLeftCornerBounds = new IntRectangle(0, ((int) borderBounds.height) - cornerWidth, lowerLeftCorner.getWidth(),
				lowerLeftCorner.getHeight()).toRectangle();
		if (lowerLeftCornerBounds.overlaps(bounds))
		{
			drawLowerLeftCorner(target, drawOffset);
		}
		Rectangle lowerRightCornerBounds = new IntRectangle(((int) borderBounds.width) - cornerWidth,
				((int) borderBounds.height) - cornerWidth, lowerRightCorner.getWidth(), lowerRightCorner.getHeight()).toRectangle();
		if (lowerRightCornerBounds.overlaps(bounds))
		{
			drawLowerRightCorner(target, drawOffset);
		}
	}

	public Image copyMapIntoBorder(Image mapWithoutBorder, Image border)
	{
		if (borderWidthScaled == 0)
		{
			return mapWithoutBorder;
		}

		Painter p = border.createPainter();
		p.drawImage(mapWithoutBorder, borderWidthScaled, borderWidthScaled);
		p.dispose();

		return border;
	}

	private Image createEdgeFromEdge(Image edgeIn, BorderEdgeType edgeTypeIn, BorderEdgeType outputType)
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

	private enum BorderEdgeType
	{
		Top, Bottom, Left, Right
	}

	private Image createCornerFromCornerByFlipping(Image cornerIn, CornerType inputCornerType, CornerType outputType)
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

	private Image loadImageWithStringInFileName(Path path, String inFileName, boolean throwExceptionIfMissing)
	{
		List<Path> corners = Assets.listFiles(path.toString(), inFileName, null);
		if (corners.isEmpty())
		{
			if (throwExceptionIfMissing)
			{
				throw new RuntimeException(
						"Unable to find a file containing \"" + inFileName + "\" in the directory " + path.toAbsolutePath());
			}
			else
			{
				return null;
			}
		}
		if (corners.size() > 1)
		{
			throw new RuntimeException("More than one file contains \"" + inFileName + "\" in the directory " + path.toAbsolutePath());
		}

		return Assets.readImage(corners.get(0).toString());
	}

	public int getBorderWidthScaledByResolution()
	{
		return borderWidthScaled;
	}

	public static int calcBorderWidthScaledByResolution(MapSettings settings)
	{
		return settings.drawBorder ? (int) (settings.borderWidth * settings.resolution) : 0;
	}

	public Dimension getMapBoundsIncludingBorder()
	{
		return borderBounds;
	}
}