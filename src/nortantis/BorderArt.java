package nortantis;

import nortantis.geom.IntPoint;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.PixelReader;
import nortantis.platform.PixelReaderWriter;
import nortantis.platform.ImageHelper;
import nortantis.util.Assets;
import nortantis.util.ConcurrentHashMapF;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The images of one border, at the resolution they are stored at, along with reveal masks that say which of each image's transparent
 * pixels should show the map rather than the border background.
 *
 * A transparent pixel shows the map when it is connected, through transparency, to the open map area. Transparency enclosed by the art's
 * own ink, such as the inside of an inset corner box, is not connected to the map and keeps the border background behind it.
 *
 * Instances are cached per border because the masks depend only on the art, not on the border width or the resolution.
 */
public class BorderArt
{
	/**
	 * The reveal masks are not allowed to shift the border frame onto the map by more than this fraction of the border width. This is a
	 * leak detector rather than a routine limiter: the deepest art measured insets by about a quarter of the border width, so this only
	 * fires when a flood fill has escaped through an antialiased gap in the art, where the alternative is a frame that lands in the middle
	 * of the map.
	 */
	private static final double maxInsetDepthFraction = 2.0 / 3.0;

	private static final ConcurrentHashMapF<String, BorderArt> instances = new ConcurrentHashMapF<>();

	/**
	 * The edge and corner images as they were found on disk. Any of these can be missing, in which case the border draws that element by
	 * transforming one of the others.
	 */
	private final Map<BorderEdgeType, Image> loadedEdges = new EnumMap<>(BorderEdgeType.class);
	private final Map<CornerType, Image> loadedCorners = new EnumMap<>(CornerType.class);
	/**
	 * All four edges and corners, each in its own orientation, with missing ones filled in by transforming the ones that were found.
	 */
	private final Map<BorderEdgeType, Image> derivedEdges = new EnumMap<>(BorderEdgeType.class);
	private final Map<CornerType, Image> derivedCorners = new EnumMap<>(CornerType.class);
	private final Map<BorderEdgeType, Image> edgeRevealMasks = new EnumMap<>(BorderEdgeType.class);
	private final ConcurrentHashMapF<Integer, Map<CornerType, Image>> cornerRevealMasksBySeedStart = new ConcurrentHashMapF<>();
	private final int edgeOriginalWidth;
	private final int cornerOriginalWidth;
	private final double insetDepthFraction;

	private BorderArt(Path borderPath)
	{
		loadedEdges.put(BorderEdgeType.Top, loadImageWithStringInFileName(borderPath, "top_edge."));
		loadedEdges.put(BorderEdgeType.Bottom, loadImageWithStringInFileName(borderPath, "bottom_edge."));
		loadedEdges.put(BorderEdgeType.Left, loadImageWithStringInFileName(borderPath, "left_edge."));
		loadedEdges.put(BorderEdgeType.Right, loadImageWithStringInFileName(borderPath, "right_edge."));

		loadedCorners.put(CornerType.upperLeft, loadImageWithStringInFileName(borderPath, "upper_left_corner."));
		loadedCorners.put(CornerType.upperRight, loadImageWithStringInFileName(borderPath, "upper_right_corner."));
		loadedCorners.put(CornerType.lowerLeft, loadImageWithStringInFileName(borderPath, "lower_left_corner."));
		loadedCorners.put(CornerType.lowerRight, loadImageWithStringInFileName(borderPath, "lower_right_corner."));

		edgeOriginalWidth = calcEdgeOriginalWidth();
		if (edgeOriginalWidth == 0)
		{
			throw new RuntimeException("Border cannot be drawn. Couldn't find any edge images in " + borderPath);
		}
		cornerOriginalWidth = calcCornerOriginalWidth();
		if (cornerOriginalWidth == 0)
		{
			throw new RuntimeException("Border cannot be drawn. Couldn't find any corner images in " + borderPath);
		}

		deriveMissingEdgesAndCorners();
		insetDepthFraction = createEdgeRevealMasks();
	}

	public static BorderArt load(NamedResource borderResource, String customImagesPath)
	{
		// The separator cannot appear in an art pack name, a folder, or a border name, so it keeps the parts of the key from running together.
		String key = StringUtils.defaultString(borderResource.artPack) + "\u0000" + StringUtils.defaultString(customImagesPath) + "\u0000" + borderResource.name;
		return instances.computeIfAbsent(key, unused -> new BorderArt(findBorderPath(borderResource, customImagesPath)));
	}

	/**
	 * Drops the cached art of every border. Called when the set of art packs available can have changed.
	 */
	public static void clear()
	{
		instances.clear();
	}

	private static Path findBorderPath(NamedResource borderResource, String customImagesPath)
	{
		Path artPackPath = Assets.getArtPackPath(borderResource.artPack, customImagesPath);
		if (artPackPath == null)
		{
			throw new RuntimeException(
					"Unable to draw the border because the selected border type, '" + borderResource.name + "', is from the art pack '" + borderResource.artPack + "', which does not exist.");
		}
		Path allBordersPath = Paths.get(artPackPath.toString(), "borders");
		Path borderPath = Paths.get(allBordersPath.toString(), borderResource.name);
		if (!Assets.exists(borderPath.toString()))
		{
			throw new RuntimeException("The selected border type '" + borderResource + "' does not have a folder for images in " + allBordersPath + ".");
		}
		return borderPath;
	}

	private static Image loadImageWithStringInFileName(Path path, String inFileName)
	{
		List<Path> matches = Assets.listFiles(path.toString(), inFileName, null, Assets.allowedImageExtensions);
		if (matches.isEmpty())
		{
			return null;
		}
		if (matches.size() > 1)
		{
			throw new RuntimeException("More than one file contains \"" + inFileName + "\" in the directory " + path.toAbsolutePath());
		}

		return Assets.readImage(matches.get(0).toString());
	}

	private int calcEdgeOriginalWidth()
	{
		// The band width of an edge image is the dimension that faces the map.
		if (loadedEdges.get(BorderEdgeType.Top) != null)
		{
			return loadedEdges.get(BorderEdgeType.Top).getHeight();
		}
		if (loadedEdges.get(BorderEdgeType.Bottom) != null)
		{
			return loadedEdges.get(BorderEdgeType.Bottom).getHeight();
		}
		if (loadedEdges.get(BorderEdgeType.Left) != null)
		{
			return loadedEdges.get(BorderEdgeType.Left).getWidth();
		}
		if (loadedEdges.get(BorderEdgeType.Right) != null)
		{
			return loadedEdges.get(BorderEdgeType.Right).getWidth();
		}
		return 0;
	}

	private int calcCornerOriginalWidth()
	{
		for (CornerType type : CornerType.values())
		{
			if (loadedCorners.get(type) != null)
			{
				return loadedCorners.get(type).getWidth();
			}
		}
		return 0;
	}

	/**
	 * Fills in derivedEdges and derivedCorners, using the same fallback order the border drawing code uses so that the masks describe the
	 * images that actually get drawn.
	 */
	private void deriveMissingEdgesAndCorners()
	{
		Image top = loadedEdges.get(BorderEdgeType.Top);
		if (top == null)
		{
			if (loadedEdges.get(BorderEdgeType.Right) != null)
			{
				top = createEdgeFromEdge(loadedEdges.get(BorderEdgeType.Right), BorderEdgeType.Right, BorderEdgeType.Top);
			}
			else if (loadedEdges.get(BorderEdgeType.Left) != null)
			{
				top = createEdgeFromEdge(loadedEdges.get(BorderEdgeType.Left), BorderEdgeType.Left, BorderEdgeType.Top);
			}
			else
			{
				top = createEdgeFromEdge(loadedEdges.get(BorderEdgeType.Bottom), BorderEdgeType.Bottom, BorderEdgeType.Top);
			}
		}
		derivedEdges.put(BorderEdgeType.Top, top);
		for (BorderEdgeType type : new BorderEdgeType[] { BorderEdgeType.Right, BorderEdgeType.Left, BorderEdgeType.Bottom })
		{
			Image loaded = loadedEdges.get(type);
			derivedEdges.put(type, loaded != null ? loaded : createEdgeFromEdge(top, BorderEdgeType.Top, type));
		}

		Image upperLeft = loadedCorners.get(CornerType.upperLeft);
		if (upperLeft == null)
		{
			if (loadedCorners.get(CornerType.upperRight) != null)
			{
				upperLeft = createCornerFromCornerByFlipping(loadedCorners.get(CornerType.upperRight), CornerType.upperRight, CornerType.upperLeft);
			}
			else if (loadedCorners.get(CornerType.lowerLeft) != null)
			{
				upperLeft = createCornerFromCornerByFlipping(loadedCorners.get(CornerType.lowerLeft), CornerType.lowerLeft, CornerType.upperLeft);
			}
			else
			{
				upperLeft = createCornerFromCornerByFlipping(loadedCorners.get(CornerType.lowerRight), CornerType.lowerRight, CornerType.upperLeft);
			}
		}
		derivedCorners.put(CornerType.upperLeft, upperLeft);
		for (CornerType type : new CornerType[] { CornerType.upperRight, CornerType.lowerLeft, CornerType.lowerRight })
		{
			Image loaded = loadedCorners.get(type);
			derivedCorners.put(type, loaded != null ? loaded : createCornerFromCornerByFlipping(upperLeft, CornerType.upperLeft, type));
		}
	}

	/**
	 * Creates the reveal mask of each edge by flood filling inward from the transparent pixels along the edge's map-facing side.
	 *
	 * @return How far the deepest of the four masks reaches in from the map-facing side, as a fraction of the band's width.
	 */
	private double createEdgeRevealMasks()
	{
		double maxDepthFraction = 0.0;
		for (BorderEdgeType type : BorderEdgeType.values())
		{
			Image edge = derivedEdges.get(type);
			Image mask = createRevealMask(edge, createEdgeSeeds(edge, type));
			if (mask == null)
			{
				continue;
			}
			maxDepthFraction = Math.max(maxDepthFraction, calcEdgeDepthFraction(mask, type));
			edgeRevealMasks.put(type, mask);
		}
		return maxDepthFraction;
	}

	/**
	 * The transparent pixels along the given edge image's map-facing side, which for a top edge is its bottom row, for a left edge its
	 * right column, and so on.
	 */
	private static List<IntPoint> createEdgeSeeds(Image edge, BorderEdgeType type)
	{
		List<IntPoint> seeds = new ArrayList<>();
		if (type == BorderEdgeType.Top || type == BorderEdgeType.Bottom)
		{
			int mapFacingRow = type == BorderEdgeType.Top ? edge.getHeight() - 1 : 0;
			for (int x = 0; x < edge.getWidth(); x++)
			{
				seeds.add(new IntPoint(x, mapFacingRow));
			}
		}
		else
		{
			int mapFacingColumn = type == BorderEdgeType.Left ? edge.getWidth() - 1 : 0;
			for (int y = 0; y < edge.getHeight(); y++)
			{
				seeds.add(new IntPoint(mapFacingColumn, y));
			}
		}
		return seeds;
	}

	/**
	 * How far the given edge's mask reaches in from the edge's map-facing side, as a fraction of the band's width.
	 */
	private static double calcEdgeDepthFraction(Image mask, BorderEdgeType type)
	{
		boolean isHorizontal = type == BorderEdgeType.Top || type == BorderEdgeType.Bottom;
		int bandWidth = isHorizontal ? mask.getHeight() : mask.getWidth();
		int deepest = 0;
		try (PixelReader maskPixels = mask.createPixelReader())
		{
			for (int y = 0; y < mask.getHeight(); y++)
			{
				for (int x = 0; x < mask.getWidth(); x++)
				{
					if (maskPixels.getGrayLevel(x, y) == 0)
					{
						continue;
					}
					int depth;
					switch (type)
					{
						case Top:
							depth = mask.getHeight() - y;
							break;
						case Bottom:
							depth = y + 1;
							break;
						case Left:
							depth = mask.getWidth() - x;
							break;
						default:
							depth = x + 1;
							break;
					}
					deepest = Math.max(deepest, depth);
				}
			}
		}
		return ((double) deepest) / bandWidth;
	}

	/**
	 * Creates the reveal mask of each corner by flood filling in from the part of the corner's two inner sides that protrudes past the
	 * edge bands and so faces open map. A corner that does not protrude gets no seeds, since its inner sides are adjacent to the edge
	 * bands rather than to the map, and it touches the map only at a single point.
	 *
	 * @param seedStartInOriginalPixels
	 *            Where the edge bands end within the corner image, in the corner image's own pixels. This is the edge images' band width
	 *            except when the border width had to be clamped to keep the border from overlapping itself in the middle of the map.
	 */
	private Map<CornerType, Image> createCornerRevealMasks(int seedStartInOriginalPixels)
	{
		Map<CornerType, Image> masks = new EnumMap<>(CornerType.class);
		for (CornerType type : CornerType.values())
		{
			Image corner = derivedCorners.get(type);
			Image mask = createRevealMask(corner, createCornerSeeds(corner, type, seedStartInOriginalPixels));
			if (mask != null)
			{
				masks.put(type, mask);
			}
		}
		return masks;
	}

	/**
	 * The transparent pixels along the part of the given corner image's two inner sides that lies past where the edge bands end. For an
	 * upper-left corner those sides are the bottom row and the right column; the other corners are the mirror images of that.
	 */
	private static List<IntPoint> createCornerSeeds(Image corner, CornerType type, int seedStartInOriginalPixels)
	{
		boolean isUpper = type == CornerType.upperLeft || type == CornerType.upperRight;
		boolean isLeft = type == CornerType.upperLeft || type == CornerType.lowerLeft;
		// The horizontal inner side is the bottom row of an upper corner and the top row of a lower one, and it runs from where the
		// vertical edge band ends to the corner's far side.
		int horizontalSideY = isUpper ? corner.getHeight() - 1 : 0;
		int verticalSideX = isLeft ? corner.getWidth() - 1 : 0;

		List<IntPoint> seeds = new ArrayList<>();
		for (int x = 0; x < corner.getWidth(); x++)
		{
			if ((isLeft ? x : corner.getWidth() - 1 - x) >= seedStartInOriginalPixels)
			{
				seeds.add(new IntPoint(x, horizontalSideY));
			}
		}
		for (int y = 0; y < corner.getHeight(); y++)
		{
			if ((isUpper ? y : corner.getHeight() - 1 - y) >= seedStartInOriginalPixels)
			{
				seeds.add(new IntPoint(verticalSideX, y));
			}
		}
		return seeds;
	}

	/**
	 * Flood fills from the given seeds through pixels whose alpha is below {@link ImageAndMasks#opaqueThreshold}, marking every pixel it
	 * reaches. A pixel is marked when it comes off the queue, and the narrow passage check only stops the fill from expanding out of it,
	 * so a chokepoint pixel is still revealed and only what lies beyond a narrow gap is left out.
	 *
	 * @return A binary image the size of the given image in which white pixels are the ones the fill reached, or null when the fill
	 *         reached nothing.
	 */
	private static Image createRevealMask(Image image, List<IntPoint> seeds)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		if (width == 0 || height == 0 || seeds.isEmpty())
		{
			return null;
		}

		Image mask = Image.create(width, height, ImageType.Binary);
		int filledCount = 0;
		try (PixelReader imagePixels = image.createPixelReader(); PixelReaderWriter maskPixels = mask.createPixelReaderWriter())
		{
			boolean[][] visited = new boolean[width][height];

			// Coordinates are encoded as y * width + x to avoid IntPoint allocations.
			int[] queue = new int[width * height];
			int head = 0;
			int tail = 0;

			for (IntPoint seed : seeds)
			{
				if (!visited[seed.x][seed.y] && imagePixels.getAlpha(seed.x, seed.y) < ImageAndMasks.opaqueThreshold)
				{
					visited[seed.x][seed.y] = true;
					queue[tail++] = seed.y * width + seed.x;
				}
			}

			while (head < tail)
			{
				int encoded = queue[head++];
				int x = encoded % width;
				int y = encoded / width;

				maskPixels.setGrayLevel(x, y, mask.getMaxPixelLevel());
				filledCount++;

				if (ImageAndMasks.isNarrowPassage(imagePixels, width, height, x, y, ImageAndMasks.narrowPassageThreshold))
				{
					continue;
				}

				if (x + 1 < width && !visited[x + 1][y] && imagePixels.getAlpha(x + 1, y) < ImageAndMasks.opaqueThreshold)
				{
					visited[x + 1][y] = true;
					queue[tail++] = y * width + (x + 1);
				}
				if (x - 1 >= 0 && !visited[x - 1][y] && imagePixels.getAlpha(x - 1, y) < ImageAndMasks.opaqueThreshold)
				{
					visited[x - 1][y] = true;
					queue[tail++] = y * width + (x - 1);
				}
				if (y + 1 < height && !visited[x][y + 1] && imagePixels.getAlpha(x, y + 1) < ImageAndMasks.opaqueThreshold)
				{
					visited[x][y + 1] = true;
					queue[tail++] = (y + 1) * width + x;
				}
				if (y - 1 >= 0 && !visited[x][y - 1] && imagePixels.getAlpha(x, y - 1) < ImageAndMasks.opaqueThreshold)
				{
					visited[x][y - 1] = true;
					queue[tail++] = (y - 1) * width + x;
				}
			}
		}

		if (filledCount == 0)
		{
			mask.close();
			return null;
		}

		growMaskIntoInk(mask, image, rimWidth);
		return mask;
	}

	/**
	 * How far, in pixels of the art as it is stored, the mask grows along a shape's antialiased rim. Art is rarely wider than a pixel or
	 * two of ramp, and the growth stops on its own at any pixel that is fully opaque, so this is only an upper bound for art like
	 * FA01-Temperate's, whose ink never quite reaches full opacity.
	 */
	private static final int rimWidth = 2;

	/**
	 * Grows the mask along the rims of the art's shapes, where alpha is neither clear nor solid, so those pixels show the map behind them
	 * rather than the border background. Without this the rim of every shape that meets revealed map blends toward the border color,
	 * which reads as a bright line along the shape.
	 *
	 * Growth passes only through partly transparent ink. Stopping at clear pixels keeps it from reaching into enclosed transparency, such
	 * as the inside of an inset corner's box, however thin the ink enclosing that is; stopping at solid pixels keeps it from moving the
	 * frame further onto the map for art that has a hard edge and so has no rim to correct.
	 */
	private static void growMaskIntoInk(Image mask, Image image, int distance)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		final int maxAlpha = 255;
		try (PixelReader imagePixels = image.createPixelReader(); PixelReaderWriter maskPixels = mask.createPixelReaderWriter())
		{
			boolean[][] inMask = new boolean[width][height];
			for (int y = 0; y < height; y++)
			{
				for (int x = 0; x < width; x++)
				{
					inMask[x][y] = maskPixels.getGrayLevel(x, y) > 0;
				}
			}

			for (int step = 0; step < distance; step++)
			{
				List<IntPoint> toAdd = new ArrayList<>();
				for (int y = 0; y < height; y++)
				{
					for (int x = 0; x < width; x++)
					{
						int alpha = imagePixels.getAlpha(x, y);
						if (inMask[x][y] || alpha < ImageAndMasks.opaqueThreshold || alpha == maxAlpha)
						{
							continue;
						}
						boolean touchesMask = (x + 1 < width && inMask[x + 1][y]) || (x - 1 >= 0 && inMask[x - 1][y]) || (y + 1 < height && inMask[x][y + 1])
								|| (y - 1 >= 0 && inMask[x][y - 1]);
						if (touchesMask)
						{
							toAdd.add(new IntPoint(x, y));
						}
					}
				}

				for (IntPoint point : toAdd)
				{
					inMask[point.x][point.y] = true;
					maskPixels.setGrayLevel(point.x, point.y, mask.getMaxPixelLevel());
				}
			}
		}
	}

	/**
	 * How far the border art's soft inner edge reaches in from the map-facing side of the band, as a fraction of the band's width. The
	 * clamp is a leak detector; see {@link #maxInsetDepthFraction}.
	 */
	public double getInsetDepthFraction()
	{
		return Math.min(insetDepthFraction, maxInsetDepthFraction);
	}

	/**
	 * The edge or corner image as it was found on disk, or null when the border does not ship that image.
	 */
	public Image getLoadedEdge(BorderEdgeType type)
	{
		return loadedEdges.get(type);
	}

	public Image getLoadedCorner(CornerType type)
	{
		return loadedCorners.get(type);
	}

	/**
	 * The width of the edge images' band, in the pixels the edge images are stored at.
	 */
	public int getEdgeOriginalWidth()
	{
		return edgeOriginalWidth;
	}

	/**
	 * The width of the corner images, in the pixels they are stored at. Corners wider than {@link #getEdgeOriginalWidth()} protrude past
	 * the edge bands and so sit over the map.
	 */
	public int getCornerOriginalWidth()
	{
		return cornerOriginalWidth;
	}

	/**
	 * The reveal mask of the given edge, at the resolution the edge image is stored at, or null when nothing in that edge shows the map.
	 */
	public Image getEdgeRevealMask(BorderEdgeType type)
	{
		return edgeRevealMasks.get(type);
	}

	/**
	 * The reveal mask of the given corner, at the resolution the corner image is stored at, or null when nothing in that corner shows the
	 * map.
	 *
	 * @param seedStartInOriginalPixels
	 *            Where the edge bands end within the corner image, in the corner image's own pixels.
	 */
	public Image getCornerRevealMask(CornerType type, int seedStartInOriginalPixels)
	{
		return cornerRevealMasksBySeedStart.computeIfAbsent(seedStartInOriginalPixels, unused -> createCornerRevealMasks(seedStartInOriginalPixels)).get(type);
	}

	/**
	 * Converts an edge image from one side of the border to another. Each entry has to land the input's map-facing side on the output's
	 * map-facing side: a top edge faces the map with its bottom row, a bottom edge with its top row, a left edge with its right column,
	 * and a right edge with its left column.
	 */
	public static Image createEdgeFromEdge(Image edgeIn, BorderEdgeType edgeTypeIn, BorderEdgeType outputType)
	{
		switch (edgeTypeIn)
		{
			case Bottom:
				switch (outputType)
				{
					case Bottom:
						return edgeIn;
					case Left:
						return ImageHelper.getInstance().reflectAcrossDiagonal(edgeIn, true);
					case Right:
						return ImageHelper.getInstance().reflectAcrossDiagonal(edgeIn, false);
					case Top:
						return ImageHelper.getInstance().flipOnYAxis(edgeIn);
				}
			case Left:
				switch (outputType)
				{
					case Bottom:
						return ImageHelper.getInstance().reflectAcrossDiagonal(edgeIn, true);
					case Left:
						return edgeIn;
					case Right:
						return ImageHelper.getInstance().flipOnXAxis(edgeIn);
					case Top:
						return ImageHelper.getInstance().reflectAcrossDiagonal(edgeIn, false);
				}
			case Right:
				switch (outputType)
				{
					case Bottom:
						return ImageHelper.getInstance().reflectAcrossDiagonal(edgeIn, false);
					case Left:
						return ImageHelper.getInstance().flipOnXAxis(edgeIn);
					case Right:
						return edgeIn;
					case Top:
						return ImageHelper.getInstance().reflectAcrossDiagonal(edgeIn, true);
				}
			case Top:
				switch (outputType)
				{
					case Bottom:
						return ImageHelper.getInstance().flipOnYAxis(edgeIn);
					case Left:
						return ImageHelper.getInstance().reflectAcrossDiagonal(edgeIn, false);
					case Right:
						return ImageHelper.getInstance().reflectAcrossDiagonal(edgeIn, true);
					case Top:
						return edgeIn;
				}
		}

		throw new IllegalStateException("Unable to create a border edge from the edges given");
	}

	public static Image createCornerFromCornerByFlipping(Image cornerIn, CornerType inputCornerType, CornerType outputType)
	{
		switch (inputCornerType)
		{
			case lowerLeft:
				switch (outputType)
				{
					case lowerLeft:
						return cornerIn;
					case lowerRight:
						return ImageHelper.getInstance().flipOnXAxis(cornerIn);
					case upperLeft:
						return ImageHelper.getInstance().flipOnYAxis(cornerIn);
					case upperRight:
						return ImageHelper.getInstance().flipOnXAxis(ImageHelper.getInstance().flipOnYAxis(cornerIn));
				}
				break;
			case lowerRight:
				switch (outputType)
				{
					case lowerLeft:
						return ImageHelper.getInstance().flipOnXAxis(cornerIn);
					case lowerRight:
						return cornerIn;
					case upperLeft:
						return ImageHelper.getInstance().flipOnXAxis(ImageHelper.getInstance().flipOnYAxis(cornerIn));
					case upperRight:
						return ImageHelper.getInstance().flipOnYAxis(cornerIn);
				}
			case upperLeft:
				switch (outputType)
				{
					case lowerLeft:
						return ImageHelper.getInstance().flipOnYAxis(cornerIn);
					case lowerRight:
						return ImageHelper.getInstance().flipOnXAxis(ImageHelper.getInstance().flipOnYAxis(cornerIn));
					case upperLeft:
						return cornerIn;
					case upperRight:
						return ImageHelper.getInstance().flipOnXAxis(cornerIn);
				}
			case upperRight:
				switch (outputType)
				{
					case lowerLeft:
						return ImageHelper.getInstance().flipOnXAxis(ImageHelper.getInstance().flipOnYAxis(cornerIn));
					case lowerRight:
						return ImageHelper.getInstance().flipOnYAxis(cornerIn);
					case upperLeft:
						return ImageHelper.getInstance().flipOnXAxis(cornerIn);
					case upperRight:
						return cornerIn;
				}
		}

		throw new IllegalStateException("Unable to flip corner image.");
	}

	public enum BorderEdgeType
	{
		Top, Bottom, Left, Right
	}

	public enum CornerType
	{
		upperLeft, upperRight, lowerLeft, lowerRight
	}
}
