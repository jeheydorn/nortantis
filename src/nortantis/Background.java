package nortantis;

import nortantis.BorderArt.BorderEdgeType;
import nortantis.BorderArt.CornerType;
import nortantis.geom.*;
import nortantis.graph.voronoi.Center;
import nortantis.platform.*;
import nortantis.platform.ImageHelper;
import nortantis.platform.ImageHelper.ColorizeAlgorithm;
import nortantis.util.Tuple2;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.util.*;

/**
 * An assortment of things needed to draw the background.
 */
public class Background
{
	Image landBeforeRegionColoring;
	Image land;
	public Image ocean;
	Dimension mapBounds;
	Dimension borderBounds;
	Image borderBackground;
	private boolean shouldDrawRegionColors;
	private ColorizeAlgorithm landColorizeAlgorithm;
	private ColorizeAlgorithm oceanColorizeAlgorithm;
	// regionIndexes is a gray scale image where the level of each pixel is the
	// index of the region it is in.
	Image regionIndexes;
	private int borderWidthScaled;
	private NamedResource borderResource;
	private Image upperLeftCorner;
	private Image upperRightCorner;
	private Image lowerLeftCorner;
	private Image lowerRightCorner;
	private int cornerWidth;
	private boolean hasInsetCorners;
	private String customImagesPath;
	private boolean isBorderOutsideMap;
	private Image topEdge;
	private Image bottomEdge;
	private Image leftEdge;
	private Image rightEdge;
	public Image landColoredBeforeAddingIconColors;
	/**
	 * How much background the border adds to each side of the map. This is the border width less the depth of the art's soft inner edge,
	 * so that the transparent notches along that edge end up over the map rather than over flat backdrop.
	 */
	private int borderPaddingScaled;
	private BorderArt borderArt;
	/**
	 * The reveal masks of the border images, scaled to match the scaled art. A mask is null when nothing in that image shows the map.
	 */
	private final Map<BorderEdgeType, Image> edgeRevealMasks = new EnumMap<>(BorderEdgeType.class);
	private final Map<CornerType, Image> cornerRevealMasks = new EnumMap<>(CornerType.class);

	public Background(MapSettings settings, Dimension mapBounds, WarningLogger warningLogger)
	{
		customImagesPath = settings.customImagesPath;
		shouldDrawRegionColors = settings.drawRegionColors && (!settings.generateBackgroundFromTexture || settings.colorizeLand);

		Image landGeneratedBackground;
		landColorizeAlgorithm = ColorizeAlgorithm.none;
		this.mapBounds = mapBounds;

		borderWidthScaled = calcBorderWidthScaledByResolution(settings);
		borderResource = settings.borderResource;

		isBorderOutsideMap = settings.borderPosition == BorderPosition.Outside_map;

		if (!isBorderOutsideMap)
		{
			// When the border is drawn over the map it insets from all four sides, so the border can be no wider than half the map's smaller
			// dimension. Clamp here so a wide border over an extreme aspect ratio can't overlap itself in the middle of the map.
			borderWidthScaled = Math.min(borderWidthScaled, maxOverMapBorderElementWidth());
		}

		if (settings.drawBorder)
		{
			borderArt = ImageCache.getInstance(borderResource.artPack, customImagesPath).getBorderArt(borderResource);
		}
		borderPaddingScaled = isBorderOutsideMap ? calcBorderPaddingScaledByResolution(borderArt == null ? 0.0 : borderArt.getInsetDepthFraction(), borderWidthScaled) : 0;

		if (settings.generateBackground)
		{
			// Fractal generated background images

			final float fractalPower = 1.3f;
			Image oceanGeneratedBackground = FractalBGGenerator.generate(new Random(settings.backgroundRandomSeed), fractalPower,
					((int) mapBounds.width) + (borderPaddingScaled * 2), ((int) mapBounds.height) + (borderPaddingScaled * 2), 0.75f);
			landGeneratedBackground = oceanGeneratedBackground;
			landColorizeAlgorithm = ColorizeAlgorithm.algorithm2;
			oceanColorizeAlgorithm = ColorizeAlgorithm.algorithm2;

			if (settings.borderColorOption == BorderColorOption.Ocean_color)
			{
				borderBackground = ImageHelper.getInstance().colorize(oceanGeneratedBackground, settings.oceanColor, oceanColorizeAlgorithm);
				ocean = borderBackground;
			}
			else
			{
				if (settings.drawBorder)
				{
					borderBackground = ImageHelper.getInstance().colorize(oceanGeneratedBackground, settings.borderColor, oceanColorizeAlgorithm, settings.oceanColor.hasTransparency());
				}
				ocean = ImageHelper.getInstance().colorize(oceanGeneratedBackground, settings.oceanColor, oceanColorizeAlgorithm);
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
				land = ImageHelper.getInstance().colorize(removeBorderPadding(landGeneratedBackground), settings.landColor, landColorizeAlgorithm);
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
				texture = ImageCache.getInstance(settings.backgroundTextureResource.artPack, settings.customImagesPath).getImageFromFile(texturePath);
			}
			catch (RuntimeException e)
			{
				throw new RuntimeException("Unable to read the texture image file name \"" + texturePath + "\"", e);
			}

			oceanColorizeAlgorithm = ColorizeAlgorithm.algorithm3;

			Image oceanGeneratedBackground;
			if (settings.colorizeOcean)
			{
				oceanGeneratedBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(new Random(settings.backgroundRandomSeed), ImageHelper.getInstance().convertToGrayscale(texture),
						((int) mapBounds.height) + (borderPaddingScaled * 2), ((int) mapBounds.width) + (borderPaddingScaled * 2));

				if (settings.borderColorOption == BorderColorOption.Ocean_color)
				{
					borderBackground = ImageHelper.getInstance().colorize(oceanGeneratedBackground, settings.oceanColor, oceanColorizeAlgorithm);
					ocean = borderBackground;
				}
				else
				{
					if (settings.drawBorder)
					{
						borderBackground = ImageHelper.getInstance().colorize(oceanGeneratedBackground, settings.borderColor, oceanColorizeAlgorithm, settings.oceanColor.hasTransparency());
					}
					ocean = ImageHelper.getInstance().colorize(oceanGeneratedBackground, settings.oceanColor, oceanColorizeAlgorithm);
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
				oceanGeneratedBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(new Random(settings.backgroundRandomSeed), texture,
						((int) mapBounds.height) + (borderPaddingScaled * 2), ((int) mapBounds.width) + (borderPaddingScaled * 2));
				if (settings.drawBorder)
				{
					ocean = removeBorderPadding(oceanGeneratedBackground);

					if (settings.borderColorOption == BorderColorOption.Ocean_color)
					{
						borderBackground = oceanGeneratedBackground;
					}
					else
					{
						borderBackground = ImageHelper.getInstance().colorize(ImageHelper.getInstance().convertToGrayscale(oceanGeneratedBackground), settings.borderColor, oceanColorizeAlgorithm);
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
					landColorizeAlgorithm = ColorizeAlgorithm.algorithm3;
					if (shouldDrawRegionColors)
					{
						// Drawing region colors must be done later because it
						// depends on the graph.
						land = removeBorderPadding(landGeneratedBackground);
					}
					else
					{
						land = ImageHelper.getInstance().colorize(removeBorderPadding(landGeneratedBackground), settings.landColor, ColorizeAlgorithm.algorithm3);
					}
				}
				else
				{
					landColorizeAlgorithm = ColorizeAlgorithm.none;
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

					landGeneratedBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(new Random(settings.backgroundRandomSeed), ImageHelper.getInstance().convertToGrayscale(texture),
							((int) mapBounds.height) + (borderPaddingScaled * 2), ((int) mapBounds.width) + (borderPaddingScaled * 2));
					if (shouldDrawRegionColors)
					{
						// Drawing region colors must be done later because it
						// depends on the graph.
						land = removeBorderPadding(landGeneratedBackground);
					}
					else
					{
						land = ImageHelper.getInstance().colorize(removeBorderPadding(landGeneratedBackground), settings.landColor, ColorizeAlgorithm.algorithm3);
					}
					landColorizeAlgorithm = ColorizeAlgorithm.algorithm3;
				}
				else
				{
					landGeneratedBackground = BackgroundGenerator.generateUsingWhiteNoiseConvolution(new Random(settings.backgroundRandomSeed), texture,
							((int) mapBounds.height) + (borderPaddingScaled * 2), ((int) mapBounds.width) + (borderPaddingScaled * 2));
					land = removeBorderPadding(landGeneratedBackground);
					landColorizeAlgorithm = ColorizeAlgorithm.none;
				}
			}
		}
		else if (settings.solidColorBackground)
		{
			Image background = Image.create(((int) mapBounds.width) + (borderPaddingScaled * 2), ((int) mapBounds.height) + (borderPaddingScaled * 2),
					ImageType.Grayscale8Bit);
			landColorizeAlgorithm = ColorizeAlgorithm.solidColor;
			oceanColorizeAlgorithm = ColorizeAlgorithm.solidColor;

			if (settings.borderColorOption == BorderColorOption.Ocean_color)
			{
				borderBackground = ImageHelper.getInstance().colorize(background, settings.oceanColor, oceanColorizeAlgorithm);
				ocean = borderBackground;
			}
			else
			{
				if (settings.drawBorder)
				{
					borderBackground = ImageHelper.getInstance().colorize(background, settings.borderColor, oceanColorizeAlgorithm, settings.oceanColor.hasTransparency());
				}
				ocean = ImageHelper.getInstance().colorize(background, settings.oceanColor, oceanColorizeAlgorithm);
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
				land = ImageHelper.getInstance().colorize(removeBorderPadding(background), settings.landColor, landColorizeAlgorithm);
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

	public static Dimension calcMapBoundsAndAdjustResolutionIfNeeded(MapSettings settings, Dimension maxDimensions)
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
			int borderPadding = calcBorderPaddingScaledByResolution(settings, calcBorderWidthScaledByResolution(settings));
			Dimension mapBoundsPlusBorder = new Dimension(mapBounds.width + borderPadding * 2, mapBounds.height + borderPadding * 2);

			Dimension newBounds = ImageHelper.getInstance().fitDimensionsWithinBoundingBox(maxDimensions, mapBoundsPlusBorder.width, mapBoundsPlusBorder.height);
			// Change the resolution to match the new bounds.
			settings.resolution *= ((double) newBounds.width) / mapBoundsPlusBorder.width;

			Dimension scaledMapBounds = sizeFromSettingsAt100PercentResolution.mult(settings.resolution);
			mapBounds = scaledMapBounds;
		}
		return mapBounds;
	}

	/**
	 * When the border is drawn over the map, no border element (edge or corner) may be wider than this many scaled pixels, which is half the
	 * map's smaller dimension. A wider element would make opposite sides of the border overlap in the middle of the map and can index outside
	 * the map raster while drawing.
	 */
	private int maxOverMapBorderElementWidth()
	{
		return (int) (Math.min(mapBounds.width, mapBounds.height) / 2);
	}

	public Image removeBorderPadding(Image image)
	{
		if (!isBorderOutsideMap)
		{
			// The border is drawn over the map, so there is no padding to remove.
			return image;
		}
		return image.copySubImage(new IntRectangle(borderPaddingScaled, borderPaddingScaled, (int) (image.getWidth() - borderPaddingScaled * 2), (int) (image.getHeight() - borderPaddingScaled * 2)));
	}

	public void doSetupThatNeedsGraphAndIcons(WorldGraph graph, List<IconDrawTask> tasks, Set<Center> centersToDraw, Rectangle drawBounds, Rectangle replaceBounds)
	{
		if (shouldDrawRegionColors)
		{
			// The image "land" is generated but doesn't yet have colors.

			if (drawBounds == null || centersToDraw == null)
			{
				regionIndexes = Image.create(land.getWidth(), land.getHeight(), ImageType.RGB);
				try (Painter p = regionIndexes.createPainter())
				{
					graph.drawRegionIndexes(p, null, null);
				}

				landColoredBeforeAddingIconColors = drawRegionColors(graph, landBeforeRegionColoring, regionIndexes, landColorizeAlgorithm, null);
				updateRegionIndexesAndLandWithIconShapes(graph, tasks, drawBounds);
				land = drawRegionColors(graph, landBeforeRegionColoring, regionIndexes, landColorizeAlgorithm, null);
			}
			else
			{
				// Update only a piece of the land
				regionIndexes = Image.create((int) drawBounds.width, (int) drawBounds.height, ImageType.RGB);
				try (Painter p = regionIndexes.createPainter())
				{
					graph.drawRegionIndexes(p, centersToDraw, drawBounds);
				}

				Image landSnippetColoredBeforeAddingIconColors = drawRegionColors(graph, landBeforeRegionColoring, regionIndexes, landColorizeAlgorithm,
						new IntPoint((int) drawBounds.x, (int) drawBounds.y));
				IntRectangle boundsInSourceToCopyFrom = new IntRectangle((int) replaceBounds.x - (int) drawBounds.x, (int) replaceBounds.y - (int) drawBounds.y, (int) replaceBounds.width,
						(int) replaceBounds.height);
				ImageHelper.getInstance().copySnippetFromSourceAndPasteIntoTarget(landColoredBeforeAddingIconColors, landSnippetColoredBeforeAddingIconColors,
						replaceBounds.upperLeftCorner().toIntPoint(), boundsInSourceToCopyFrom, 0);

				updateRegionIndexesAndLandWithIconShapes(graph, tasks, drawBounds);
				Image landSnippet = drawRegionColors(graph, landBeforeRegionColoring, regionIndexes, landColorizeAlgorithm, new IntPoint((int) drawBounds.x, (int) drawBounds.y));
				ImageHelper.getInstance().copySnippetFromSourceAndPasteIntoTarget(land, landSnippet, replaceBounds.upperLeftCorner().toIntPoint(), boundsInSourceToCopyFrom, 0);
			}
		}
	}

	/***
	 * Draws icons onto regionIndexes and the land background so that the color of icons is determined by the place they draw at their base,
	 * rather than letting them be multicolored when they cross region boundaries.
	 */
	private void updateRegionIndexesAndLandWithIconShapes(WorldGraph graph, List<IconDrawTask> tasks, Rectangle drawBounds)
	{
		// The image "land" is generated but doesn't yet have colors.
		for (final IconDrawTask task : tasks)
		{
			// Skip decorations
			if (task.type != IconType.decorations && (drawBounds == null || task.overlaps(drawBounds)))
			{
				IntRectangle contentBounds = task.scaledImageAndMasks.getOrCreateContentBounds();
				Point middleOfBottomOfContentBounds = new Point(task.centerLoc.x - (task.scaledSize.width / 2) + (contentBounds.x + contentBounds.width / 2),
						task.centerLoc.y - (task.scaledSize.height / 2) + (contentBounds.y + contentBounds.height));
				Point justAboveBottomMiddle = middleOfBottomOfContentBounds.add(0, -contentBounds.height / 8);
				Center center = graph.findClosestCenter(justAboveBottomMiddle, true);
				if (center == null)
				{
					continue;
				}
				if (center.region == null)
				{
					continue;
				}
				int regionIndex = center.region.id;
				Color regionIdColor = WorldGraph.storeValueAsColor(regionIndex);

				int xLoc = (int) task.centerLoc.x - task.scaledSize.width / 2;
				int yLoc = (int) task.centerLoc.y - task.scaledSize.height / 2;

				Point drawLocation = drawBounds == null ? new Point(xLoc, yLoc) : new Point(xLoc, yLoc).subtract(drawBounds.upperLeftCorner());

				ImageHelper.getInstance().drawMaskOntoImage(regionIndexes, task.scaledImageAndMasks.getOrCreateContentMask(), regionIdColor, drawLocation.toIntPoint());
			}
		}
	}

	private Image drawRegionColors(WorldGraph graph, Image fractalBG, Image pixelColors, ColorizeAlgorithm colorizeAlgorithm, IntPoint where)
	{
		if (graph.regions.isEmpty())
		{
			return ImageHelper.getInstance().convertImageToType(fractalBG, ImageType.RGB);
		}

		Map<Integer, Color> regionBackgroundColors = new HashMap<>();
		for (Map.Entry<Integer, Region> regionEntry : graph.regions.entrySet())
		{
			regionBackgroundColors.put(regionEntry.getKey(), regionEntry.getValue().backgroundColor);
		}

		return ImageHelper.getInstance().colorizeMulti(fractalBG, regionBackgroundColors, pixelColors, colorizeAlgorithm, where);
	}

	public Image createOceanSnippet(Rectangle boundsToCopyFrom)
	{
		return ocean.copySubImage(boundsToCopyFrom.toIntRectangle());
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

		try (Painter p = result.createPainter())
		{
			if (result.hasAlpha())
			{
				p.setAlphaComposite(AlphaComposite.Src);
			}

			if (isBorderOutsideMap)
			{
				p.drawImage(map, borderPaddingScaled, borderPaddingScaled);
			}
			else
			{
				p.drawImage(map, 0, 0);
			}
		}

		int edgeOriginalWidth = borderArt.getEdgeOriginalWidth();
		int cornerOriginalWidth = borderArt.getCornerOriginalWidth();

		// Edges
		topEdge = borderArt.getLoadedEdge(BorderEdgeType.Top);
		if (topEdge != null)
		{
			topEdge = ImageHelper.getInstance().scaleByHeight(topEdge, borderWidthScaled);
		}
		bottomEdge = borderArt.getLoadedEdge(BorderEdgeType.Bottom);
		if (bottomEdge != null)
		{
			bottomEdge = ImageHelper.getInstance().scaleByHeight(bottomEdge, borderWidthScaled);
		}
		leftEdge = borderArt.getLoadedEdge(BorderEdgeType.Left);
		if (leftEdge != null)
		{
			leftEdge = ImageHelper.getInstance().scaleByWidth(leftEdge, borderWidthScaled);
		}
		rightEdge = borderArt.getLoadedEdge(BorderEdgeType.Right);
		if (rightEdge != null)
		{
			rightEdge = ImageHelper.getInstance().scaleByWidth(rightEdge, borderWidthScaled);
		}

		if (topEdge == null)
		{
			if (rightEdge != null)
			{
				topEdge = BorderArt.createEdgeFromEdge(rightEdge, BorderEdgeType.Right, BorderEdgeType.Top);
			}
			else if (leftEdge != null)
			{
				topEdge = BorderArt.createEdgeFromEdge(leftEdge, BorderEdgeType.Left, BorderEdgeType.Top);
			}
			else
			{
				topEdge = BorderArt.createEdgeFromEdge(bottomEdge, BorderEdgeType.Bottom, BorderEdgeType.Top);
			}
		}
		if (rightEdge == null)
		{
			rightEdge = BorderArt.createEdgeFromEdge(topEdge, BorderEdgeType.Top, BorderEdgeType.Right);
		}
		if (leftEdge == null)
		{
			leftEdge = BorderArt.createEdgeFromEdge(topEdge, BorderEdgeType.Top, BorderEdgeType.Left);
		}
		if (bottomEdge == null)
		{
			bottomEdge = BorderArt.createEdgeFromEdge(topEdge, BorderEdgeType.Top, BorderEdgeType.Bottom);
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

		int cornerWidthBeforeClamping = cornerWidth;
		if (!isBorderOutsideMap)
		{
			// Inset corners are wider than the border width, so clamp them to the same over-the-map limit as borderWidthScaled to keep
			// opposite corners from overlapping in the middle of the map.
			cornerWidth = Math.min(cornerWidth, maxOverMapBorderElementWidth());
		}

		// Corners
		upperLeftCorner = scaleCornerByWidth(borderArt.getLoadedCorner(CornerType.upperLeft));
		upperRightCorner = scaleCornerByWidth(borderArt.getLoadedCorner(CornerType.upperRight));
		lowerLeftCorner = scaleCornerByWidth(borderArt.getLoadedCorner(CornerType.lowerLeft));
		lowerRightCorner = scaleCornerByWidth(borderArt.getLoadedCorner(CornerType.lowerRight));

		if (upperLeftCorner == null)
		{
			if (upperRightCorner != null)
			{
				upperLeftCorner = BorderArt.createCornerFromCornerByFlipping(upperRightCorner, CornerType.upperRight, CornerType.upperLeft);
			}
			else if (lowerLeftCorner != null)
			{
				upperLeftCorner = BorderArt.createCornerFromCornerByFlipping(lowerLeftCorner, CornerType.lowerLeft, CornerType.upperLeft);
			}
			else
			{
				upperLeftCorner = BorderArt.createCornerFromCornerByFlipping(lowerRightCorner, CornerType.lowerRight, CornerType.upperLeft);
			}
		}
		if (upperRightCorner == null)
		{
			upperRightCorner = BorderArt.createCornerFromCornerByFlipping(upperLeftCorner, CornerType.upperLeft, CornerType.upperRight);
		}
		if (lowerLeftCorner == null)
		{
			lowerLeftCorner = BorderArt.createCornerFromCornerByFlipping(upperLeftCorner, CornerType.upperLeft, CornerType.lowerLeft);
		}
		if (lowerRightCorner == null)
		{
			lowerRightCorner = BorderArt.createCornerFromCornerByFlipping(upperLeftCorner, CornerType.upperLeft, CornerType.lowerRight);
		}

		// The map's boundary within a corner image is where the edge bands end, which is the edge images' band width. It only moves when
		// the corner width had to be clamped, since then the corner scaled by less than the edges did.
		int cornerSeedStartInOriginalPixels = cornerWidth == cornerWidthBeforeClamping ? edgeOriginalWidth
				: (int) Math.round((((double) cornerOriginalWidth) * borderWidthScaled) / cornerWidth);
		createScaledRevealMasks(cornerSeedStartInOriginalPixels);

		for (CornerType cornerType : CornerType.values())
		{
			drawCorner(result, new IntPoint(0, 0), cornerType);
		}

		// Draw the edges
		drawEdgesIfBoundsTouchesThem(result, null);

		return result;
	}

	private Image scaleCornerByWidth(Image corner)
	{
		return corner == null ? null : ImageHelper.getInstance().scaleByWidth(corner, cornerWidth);
	}

	/**
	 * Scales the reveal masks of the border art to match the scaled edge and corner images, so they can say, pixel for pixel, where the
	 * border must not paint the border background over the map.
	 * 
	 * @param cornerSeedStartInOriginalPixels
	 *            Where the edge bands end within a corner image, in the corner images' own pixels.
	 */
	private void createScaledRevealMasks(int cornerSeedStartInOriginalPixels)
	{
		putScaledRevealMask(edgeRevealMasks, BorderEdgeType.Top, borderArt.getEdgeRevealMask(BorderEdgeType.Top), topEdge);
		putScaledRevealMask(edgeRevealMasks, BorderEdgeType.Bottom, borderArt.getEdgeRevealMask(BorderEdgeType.Bottom), bottomEdge);
		putScaledRevealMask(edgeRevealMasks, BorderEdgeType.Left, borderArt.getEdgeRevealMask(BorderEdgeType.Left), leftEdge);
		putScaledRevealMask(edgeRevealMasks, BorderEdgeType.Right, borderArt.getEdgeRevealMask(BorderEdgeType.Right), rightEdge);

		// Unlike the edge masks, the corner masks depend on the border width, so they are created for this Background rather than kept
		// with the art.
		Map<CornerType, Image> cornerMasksAtOriginalResolution = borderArt.createCornerRevealMasks(cornerSeedStartInOriginalPixels);
		for (CornerType cornerType : CornerType.values())
		{
			putScaledRevealMask(cornerRevealMasks, cornerType, cornerMasksAtOriginalResolution.get(cornerType), getCorner(cornerType));
		}
		for (Image mask : cornerMasksAtOriginalResolution.values())
		{
			mask.close();
		}
	}

	private static <T extends Enum<T>> void putScaledRevealMask(Map<T, Image> masks, T key, Image revealMaskAtOriginalResolution, Image scaledArt)
	{
		if (revealMaskAtOriginalResolution == null)
		{
			// Nothing in this border image shows the map, so there is no mask to apply and the border draws the way it always has.
			return;
		}
		masks.put(key, ImageHelper.getInstance().scaleBinaryMask(revealMaskAtOriginalResolution, scaledArt.getWidth(), scaledArt.getHeight()));
	}

	/**
	 * Draws the four border edge bands onto the given image.
	 * 
	 * @param drawBoundsBeforeBorder
	 *            The part of the map the given image holds, in coordinates that do not include the border padding, or null when the given
	 *            image is the whole map including its border.
	 */
	public void drawEdgesIfBoundsTouchesThem(Image result, Rectangle drawBoundsBeforeBorder)
	{
		Rectangle drawBounds = drawBoundsBeforeBorder == null ? null : drawBoundsBeforeBorder.translate(borderPaddingScaled, borderPaddingScaled);
		drawTopOrBottomEdgeIfBoundsTouchesIt(result, drawBounds, 0);
		drawTopOrBottomEdgeIfBoundsTouchesIt(result, drawBounds, 1);
		drawLeftOrRightEdgesIfBoundsTouchesThem(result, drawBounds, 0);
		drawLeftOrRightEdgesIfBoundsTouchesThem(result, drawBounds, 1);
	}

	/**
	 * @param drawBounds
	 *            The part of the map the given image holds, in coordinates that include the border padding, or null for the whole map.
	 */
	private void drawTopOrBottomEdgeIfBoundsTouchesIt(Image result, Rectangle drawBounds, int topVsBottom)
	{
		// Top and bottom edges
		Image edge = topVsBottom == 0 ? topEdge : bottomEdge;
		Image revealMask = edgeRevealMasks.get(topVsBottom == 0 ? BorderEdgeType.Top : BorderEdgeType.Bottom);
		final int y = topVsBottom == 0 ? 0 : ((int) borderBounds.height) - borderWidthScaled;

		int xOffset = drawBounds == null ? 0 : (int) drawBounds.x;
		int yOffset = drawBounds == null ? 0 : (int) drawBounds.y;
		int end = ((int) borderBounds.width) - cornerWidth;
		int increment = edge.getWidth();
		for (int x = cornerWidth; x < end; x += increment)
		{
			int widthToDraw = Math.min(increment, end - x);
			if (drawBounds != null && !drawBounds.overlaps(new Rectangle(x, y, widthToDraw, borderWidthScaled)))
			{
				continue;
			}

			IntPoint whereToDraw = new IntPoint(x - xOffset, y - yOffset);
			eraseMapUnderBorderElement(result, whereToDraw, new IntRectangle(x, y, widthToDraw, borderWidthScaled), revealMask, doEdgeBandsCoverMap());

			try (Painter p = result.createPainter())
			{
				p.setAlphaComposite(alphaCompositeForDrawingCornersAndEdges);
				if (widthToDraw == increment)
				{
					p.drawImage(edge, whereToDraw.x, whereToDraw.y);
				}
				else
				{
					// The image is too long to draw in the remaining space.
					try (Image partToDraw = edge.copySubImage(new IntRectangle(0, 0, widthToDraw, borderWidthScaled)))
					{
						p.drawImage(partToDraw, whereToDraw.x, whereToDraw.y);
					}
				}
			}

			tintRevealMaskIfDebugFlagIsOn(result, whereToDraw, revealMask);
		}
	}

	/**
	 * @param drawBounds
	 *            The part of the map the given image holds, in coordinates that include the border padding, or null for the whole map.
	 */
	private void drawLeftOrRightEdgesIfBoundsTouchesThem(Image result, Rectangle drawBounds, int leftVsRight)
	{
		Image edge = leftVsRight == 0 ? leftEdge : rightEdge;
		Image revealMask = edgeRevealMasks.get(leftVsRight == 0 ? BorderEdgeType.Left : BorderEdgeType.Right);
		final int x = leftVsRight == 0 ? 0 : ((int) borderBounds.width) - borderWidthScaled;

		int xOffset = drawBounds == null ? 0 : (int) drawBounds.x;
		int yOffset = drawBounds == null ? 0 : (int) drawBounds.y;
		int end = ((int) borderBounds.height) - cornerWidth;
		int increment = edge.getHeight();
		for (int y = cornerWidth; y < end; y += increment)
		{
			int heightToDraw = Math.min(increment, end - y);
			if (drawBounds != null && !drawBounds.overlaps(new Rectangle(x, y, borderWidthScaled, heightToDraw)))
			{
				continue;
			}

			IntPoint whereToDraw = new IntPoint(x - xOffset, y - yOffset);
			eraseMapUnderBorderElement(result, whereToDraw, new IntRectangle(x, y, borderWidthScaled, heightToDraw), revealMask, doEdgeBandsCoverMap());

			try (Painter p = result.createPainter())
			{
				p.setAlphaComposite(alphaCompositeForDrawingCornersAndEdges);
				if (heightToDraw == increment)
				{
					p.drawImage(edge, whereToDraw.x, whereToDraw.y);
				}
				else
				{
					// The image is too tall to draw in the remaining space.
					try (Image partToDraw = edge.copySubImage(new IntRectangle(0, 0, borderWidthScaled, heightToDraw)))
					{
						p.drawImage(partToDraw, whereToDraw.x, whereToDraw.y);
					}
				}
			}

			tintRevealMaskIfDebugFlagIsOn(result, whereToDraw, revealMask);
		}
	}

	/**
	 * Puts the border background back over the part of the map a border element is about to draw on, leaving alone the pixels the
	 * element's reveal mask marks as showing the map.
	 * 
	 * @param whereToDraw
	 *            Where the element's upper-left corner lands in target.
	 * @param elementBounds
	 *            The element's bounds in the map's coordinates including border padding, which is also where to read the border
	 *            background from.
	 * @param revealMask
	 *            The element's reveal mask, or null when nothing in the element shows the map.
	 * @param elementCoversMap
	 *            Whether any of the element lands on the map. When it doesn't, the border background is already all that is there.
	 */
	private void eraseMapUnderBorderElement(Image target, IntPoint whereToDraw, IntRectangle elementBounds, Image revealMask, boolean elementCoversMap)
	{
		if (!elementCoversMap)
		{
			return;
		}

		if (revealMask == null)
		{
			ImageHelper.getInstance().copySnippetFromSourceAndPasteIntoTarget(target, borderBackground, whereToDraw, elementBounds, 0);
		}
		else
		{
			ImageHelper.getInstance().copySnippetFromSourceAndPasteIntoTargetWhereMaskIsBlack(target, borderBackground, whereToDraw, elementBounds, revealMask);
		}
	}

	/**
	 * Whether the border's edge bands land on the map. They always do when the border is drawn inside the map, and they do when the border
	 * is outside the map but has been shifted inward so that map sits behind the art's soft inner edge.
	 */
	private boolean doEdgeBandsCoverMap()
	{
		return !isBorderOutsideMap || borderPaddingScaled < borderWidthScaled;
	}

	private void tintRevealMaskIfDebugFlagIsOn(Image target, IntPoint whereToDraw, Image revealMask)
	{
		if (revealMask != null && DebugFlags.tintBorderRevealMask())
		{
			ImageHelper.getInstance().drawMaskOntoImage(target, revealMask, Color.magenta, whereToDraw);
		}
	}

	private final AlphaComposite alphaCompositeForDrawingCornersAndEdges = AlphaComposite.SrcOver;

	private void drawCorner(Image target, IntPoint drawOffset, CornerType cornerType)
	{
		Image corner = getCorner(cornerType);
		IntPoint cornerLocation = getCornerLocation(cornerType);
		IntPoint whereToDraw = cornerLocation.subtract(drawOffset);
		Image revealMask = cornerRevealMasks.get(cornerType);

		// A corner lands on the map when it protrudes past the edge bands, and also whenever the bands themselves cover the map.
		eraseMapUnderBorderElement(target, whereToDraw, new IntRectangle(cornerLocation.x, cornerLocation.y, corner.getWidth(), corner.getHeight()), revealMask,
				hasInsetCorners || doEdgeBandsCoverMap());

		try (Painter p = target.createPainter())
		{
			p.setAlphaComposite(alphaCompositeForDrawingCornersAndEdges);
			p.translate(-drawOffset.x, -drawOffset.y);
			p.drawImage(corner, cornerLocation.x, cornerLocation.y);
		}

		tintRevealMaskIfDebugFlagIsOn(target, whereToDraw, revealMask);
	}

	private Image getCorner(CornerType cornerType)
	{
		switch (cornerType)
		{
			case upperLeft:
				return upperLeftCorner;
			case upperRight:
				return upperRightCorner;
			case lowerLeft:
				return lowerLeftCorner;
			default:
				return lowerRightCorner;
		}
	}

	/**
	 * Where the given corner's upper-left pixel lands in the map's coordinates including border padding.
	 */
	private IntPoint getCornerLocation(CornerType cornerType)
	{
		int right = ((int) borderBounds.width) - cornerWidth;
		int bottom = ((int) borderBounds.height) - cornerWidth;
		switch (cornerType)
		{
			case upperLeft:
				return new IntPoint(0, 0);
			case upperRight:
				return new IntPoint(right, 0);
			case lowerLeft:
				return new IntPoint(0, bottom);
			default:
				return new IntPoint(right, bottom);
		}
	}

	/**
	 * Draws each border corner that reaches into the part of the map the given image holds.
	 * 
	 * @param drawBoundsBeforeBorder
	 *            The part of the map the given image holds, in coordinates that do not include the border padding.
	 */
	public void drawInsetCornersIfBoundsTouchesThem(Image target, Rectangle drawBoundsBeforeBorder)
	{
		if (borderWidthScaled == 0)
		{
			return;
		}

		IntPoint drawOffset = new IntPoint(drawBoundsBeforeBorder.toIntRectangle().x + borderPaddingScaled, drawBoundsBeforeBorder.toIntRectangle().y + borderPaddingScaled);
		Rectangle bounds = drawBoundsBeforeBorder.translate(borderPaddingScaled, borderPaddingScaled);
		for (CornerType cornerType : CornerType.values())
		{
			Image corner = getCorner(cornerType);
			IntPoint cornerLocation = getCornerLocation(cornerType);
			Rectangle cornerBounds = new IntRectangle(cornerLocation.x, cornerLocation.y, corner.getWidth(), corner.getHeight()).toRectangle();
			if (cornerBounds.overlaps(bounds))
			{
				drawCorner(target, drawOffset, cornerType);
			}
		}
	}

	public int getBorderWidthScaledByResolution()
	{
		return borderWidthScaled;
	}

	public int getBorderPaddingScaledByResolution()
	{
		return borderPaddingScaled;
	}

	public static int calcBorderWidthScaledByResolution(MapSettings settings)
	{
		return settings.drawBorder ? (int) (settings.borderWidth * settings.resolution) : 0;
	}

	/**
	 * How much background the border adds to each side of the map, in pixels scaled by resolution. This is less than the border width when
	 * the border's art has a soft inner edge, because the frame moves onto the map far enough to put map behind the transparent notches
	 * along that edge.
	 * 
	 * @param borderWidthScaled
	 *            The border width scaled by the resolution the caller is asking about, which is not always the resolution in settings.
	 */
	public static int calcBorderPaddingScaledByResolution(MapSettings settings, int borderWidthScaled)
	{
		return calcBorderPaddingScaledByResolution(calcBorderInsetDepthFraction(settings), borderWidthScaled);
	}

	/**
	 * How much background the border adds to each side of the map, given how far the frame sits over the map.
	 *
	 * Callers that ask repeatedly for the same settings, such as while a resolution slider moves, should look the fraction up once with
	 * {@link #calcBorderInsetDepthFraction(MapSettings)} and call this, since the fraction does not depend on the resolution.
	 */
	public static int calcBorderPaddingScaledByResolution(double insetDepthFraction, int borderWidthScaled)
	{
		return borderWidthScaled - (int) Math.round(insetDepthFraction * borderWidthScaled);
	}

	/**
	 * How far the border frame sits over the map rather than adding to the image, as a fraction of the border width. For a border outside
	 * the map this is the depth of the transparent notches along its art's inner edge, which is 0 for art whose inner edge is opaque. It
	 * is 1 for a border drawn inside the map, since the whole width of that border is over the map.
	 */
	public static double calcBorderInsetDepthFraction(MapSettings settings)
	{
		if (!settings.drawBorder || settings.borderPosition != BorderPosition.Outside_map)
		{
			return 1.0;
		}

		try
		{
			return ImageCache.getInstance(settings.borderResource.artPack, settings.customImagesPath).getBorderArt(settings.borderResource).getInsetDepthFraction();
		}
		catch (RuntimeException e)
		{
			// The border art is missing or unreadable. Report the size the border would take without a shift; drawing the map will raise
			// the real error.
			return 0.0;
		}
	}

	public Dimension getMapBoundsIncludingBorder()
	{
		return borderBounds;
	}

	public void closeImages()
	{
		if (land != null)
		{
			land.close();
		}
		if (landBeforeRegionColoring != null && landBeforeRegionColoring != land)
		{
			landBeforeRegionColoring.close();
		}
		if (ocean != null && ocean != land)
		{
			ocean.close();
		}
		if (borderBackground != null && borderBackground != ocean && borderBackground != land)
		{
			borderBackground.close();
		}
		if (regionIndexes != null)
		{
			regionIndexes.close();
		}
		if (landColoredBeforeAddingIconColors != null)
		{
			landColoredBeforeAddingIconColors.close();
		}
		if (upperLeftCorner != null)
		{
			upperLeftCorner.close();
		}
		if (upperRightCorner != null)
		{
			upperRightCorner.close();
		}
		if (lowerLeftCorner != null)
		{
			lowerLeftCorner.close();
		}
		if (lowerRightCorner != null)
		{
			lowerRightCorner.close();
		}
		if (topEdge != null)
		{
			topEdge.close();
		}
		if (bottomEdge != null)
		{
			bottomEdge.close();
		}
		if (leftEdge != null)
		{
			leftEdge.close();
		}
		if (rightEdge != null)
		{
			rightEdge.close();
		}
		for (Image mask : edgeRevealMasks.values())
		{
			mask.close();
		}
		for (Image mask : cornerRevealMasks.values())
		{
			mask.close();
		}
	}
}