package nortantis;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import nortantis.geom.IntPoint;
import nortantis.geom.IntRectangle;
import nortantis.geom.Rectangle;
import nortantis.platform.Color;
import nortantis.platform.Image;
import nortantis.platform.ImageType;
import nortantis.platform.Painter;
import nortantis.platform.PixelReader;
import nortantis.platform.PixelReaderWriter;
import nortantis.util.ImageHelper;
import nortantis.util.Range;
import nortantis.util.Tuple2;

public class ImageAndMasks
{
	private static final int opaqueThreshold = 60;
	public final Image image;
	/**
	 * Used to linearly combine pixel values pulled from the background image without other icons vs the the map being drawn so far.
	 */
	private Image contentMask;
	private IntRectangle contentBounds;
	private Integer[] contentYStarts;

	/**
	 * Used to linearly combine pixel values pulled from the land background texture vs the background image without other icons. Used to
	 * blend coastline shading into icons when icons are allowed to draw over coastlines.
	 */
	private Image shadingMask;
	public final IconType iconType;
	/**
	 * Either the width encoded in the icons' file name, or a width calculated based on the size of other icons in the group and the default
	 * size of icons in the group.
	 */
	public final double widthFromFileName;
	private Image colorMask;

	public final String artPack;
	public final String fileNameWithoutParametersOrExtension;
	public final String groupId;

	public ImageAndMasks(Image image, IconType iconType, double widthFromFileName, String artPack, String groupId, String fileNameWithoutParametersOrExtension)
	{
		assert image != null;
		this.image = image;
		this.iconType = iconType;
		this.widthFromFileName = widthFromFileName;
		assert fileNameWithoutParametersOrExtension != null;
		this.fileNameWithoutParametersOrExtension = fileNameWithoutParametersOrExtension;
		assert groupId != null;
		this.groupId = groupId;
		assert artPack != null;
		this.artPack = artPack;
	}

	public ImageAndMasks(Image image, Image contentMask, IntRectangle contentBounds, Image shadingMask, IconType iconType, double widthFromFileName, String artPack, String groupId,
			String fileNameWithoutParametersOrExtension)
	{
		this(image, iconType, widthFromFileName, artPack, groupId, fileNameWithoutParametersOrExtension);
		this.contentMask = contentMask;
		this.shadingMask = shadingMask;
		this.contentBounds = contentBounds;
	}

	public synchronized Image getOrCreateContentMask()
	{
		if (image == null)
		{
			return null;
		}

		if (contentMask == null)
		{
			createContentMask();
		}

		return contentMask;
	}

	/**
	 * Generates a mask image for an icon that approximately covers the content of the icon. A mask is used when drawing an icon to
	 * determine which pixels that are transparent in the icon should draw the map background vs draw the icons already drawn behind that
	 * icon. If a pixel is transparent in the icon, and the corresponding pixel is white in the mask, then the map background is drawn for
	 * that pixel. But if the map pixel is black, then there is no special handling when drawing that pixel, so whatever was drawn in that
	 * place on the map before it will be visible.
	 */
	private synchronized void createContentMask()
	{
		try
		{
			if (iconType == IconType.cities || iconType == IconType.decorations)
			{
				// Do a flood fill since buildings in cities should have lines delimiting them on all sides.
				createContentMaskUsingfloodFillOrSilhouettes();
			}
			else
			{
				createContentMaskUsing3WaySilhouetteIntersection();
			}
		}
		finally
		{
			if (contentBounds == null)
			{
				addToContentBounds(new IntPoint(0, 0));
				addToContentBounds(new IntPoint(image.getWidth(), image.getHeight()));
			}
		}
	}

	/**
	 * Starts at the upper-left and upper-right pixels and flood fills until it hits pixels whose alpha >= opaqueThreshold.
	 * 
	 * @return A new image of type ImageType.Binary where white pixels are flood-filled and black pixels are not.
	 */
	private void createContentMaskUsingfloodFillOrSilhouettes()
	{
		contentMask = Image.create(image.getWidth(), image.getHeight(), ImageType.Binary);

		if (image.getWidth() == 0 || image.getHeight() == 0)
		{
			return;
		}

		final int narrowPassageThreshold = 4;

		floodFillContentMask(narrowPassageThreshold);

		if (narrowPassageThreshold > 0)
		{
			remove4DirectionalSilhouetteFromContentMask();
		}
	}

	private void floodFillContentMask(int narrowPassageThreshold)
	{
		Stack<IntPoint> q = new Stack<>();

		try (PixelReader imagePixels = image.createPixelReader(); PixelReaderWriter contentMaskPixels = contentMask.createPixelReaderWriter())
		{
			// Add the 4 corners of contentMask to q if their alpha is below opaqueThreshold.
			if (imagePixels.getAlpha(0, 0) < opaqueThreshold)
			{
				q.push(new IntPoint(0, 0));
			}
			if (imagePixels.getAlpha(contentMask.getWidth() - 1, 0) < opaqueThreshold)
			{
				q.push(new IntPoint(contentMask.getWidth() - 1, 0));
			}
			if (imagePixels.getAlpha(0, contentMask.getHeight() - 1) < opaqueThreshold)
			{
				q.push(new IntPoint(0, contentMask.getHeight() - 1));
			}
			if (imagePixels.getAlpha(contentMask.getWidth() - 1, contentMask.getHeight() - 1) < opaqueThreshold)
			{
				q.push(new IntPoint(contentMask.getWidth() - 1, contentMask.getHeight() - 1));
			}

			try (Painter p = contentMask.createPainter())
			{
				p.setColor(Color.white);
				p.fillRect(0, 0, contentMask.getWidth(), contentMask.getHeight());
			}

			boolean[][] visited = new boolean[image.getWidth()][image.getHeight()];
			while (!q.isEmpty())
			{
				IntPoint next = q.pop();

				int x = next.x;
				int y = next.y;

				if (x < 0 || x >= image.getWidth() || y < 0 || y >= image.getHeight() || visited[x][y])
				{
					continue;
				}

				if (visited[x][y])
				{
					continue;
				}

				visited[x][y] = true;

				if (imagePixels.getAlpha(x, y) < opaqueThreshold)
				{
					// This is part of the "background" that should be black in the mask
					contentMaskPixels.setGrayLevel(x, y, 0);

					if (!isNarrowPassage(imagePixels, x, y, narrowPassageThreshold))
					{
						// Add neighbors to the queue
						q.push(new IntPoint(x + 1, y));
						q.push(new IntPoint(x - 1, y));
						q.push(new IntPoint(x, y + 1));
						q.push(new IntPoint(x, y - 1));
					}
				}
				else
				{
					// This pixel is part of the "content", so leave it white and add it to the content bounds.
					addToContentBounds(next);
				}
			}
		}
	}

	private void remove4DirectionalSilhouetteFromContentMask()
	{
		// Remove pixels using silhouettes from each direction to clean up stuff the narrow passage check leaves behind.
		try (PixelReader imagePixels = image.createPixelReader(); PixelReaderWriter contentMaskPixels = contentMask.createPixelReaderWriter())
		{
			// From Top
			for (int x = 0; x < image.getWidth(); x++)
			{
				for (int y = 0; y < image.getHeight(); y++)
				{
					int alpha = imagePixels.getAlpha(x, y);
					if (alpha < opaqueThreshold)
					{
						contentMaskPixels.setGrayLevel(x, y, 0);
					}
					else
					{
						addToContentBounds(new IntPoint(x, y));
						break;
					}
				}
			}

			// From Bottom
			for (int x = 0; x < image.getWidth(); x++)
			{
				for (int y = image.getHeight() - 1; y >= 0; y--)
				{
					int alpha = imagePixels.getAlpha(x, y);
					if (alpha < opaqueThreshold)
					{
						contentMaskPixels.setGrayLevel(x, y, 0);
					}
					else
					{
						addToContentBounds(new IntPoint(x, y));
						break;
					}
				}
			}

			// From left side
			for (int y = 0; y < image.getHeight(); y++)
			{
				for (int x = 0; x < image.getWidth(); x++)
				{
					int alpha = imagePixels.getAlpha(x, y);
					if (alpha < opaqueThreshold)
					{
						contentMaskPixels.setGrayLevel(x, y, 0);
					}
					else
					{
						addToContentBounds(new IntPoint(x, y));
						break;
					}
				}
			}

			// From right side
			for (int y = 0; y < image.getHeight(); y++)
			{
				for (int x = image.getWidth() - 1; x >= 0; x--)
				{
					int alpha = imagePixels.getAlpha(x, y);
					if (alpha < opaqueThreshold)
					{
						contentMaskPixels.setGrayLevel(x, y, 0);
					}
					else
					{
						addToContentBounds(new IntPoint(x, y));
						break;
					}
				}
			}
		}
	}

	private boolean isNarrowPassage(PixelReader imagePixels, int x, int y, int distanceToCheck)
	{
		if (distanceToCheck == 0)
		{
			return false;
		}

		boolean upFound = false;
		for (int j = 1; j <= distanceToCheck; j++)
		{
			if (y + j > image.getHeight() - 1)
			{
				break;
			}
			if (imagePixels.getAlpha(x, y + j) >= opaqueThreshold)
			{
				upFound = true;
				break;
			}
		}

		if (upFound)
		{
			// Check downward.
			for (int j = 1; j <= distanceToCheck; j++)
			{
				if (y - j < 0)
				{
					break;
				}
				if (imagePixels.getAlpha(x, y - j) >= opaqueThreshold)
				{
					return true;
				}
			}
		}

		boolean rightFound = false;
		for (int i = 1; i <= distanceToCheck; i++)
		{
			if (x + i > image.getWidth() - 1)
			{
				break;
			}
			if (imagePixels.getAlpha(x + i, y) >= opaqueThreshold)
			{
				rightFound = true;
				break;
			}
		}

		if (rightFound)
		{
			// Check left.
			for (int i = 1; i <= distanceToCheck; i++)
			{
				if (x - i < 0)
				{
					break;
				}
				if (imagePixels.getAlpha(x - i, y) >= opaqueThreshold)
				{
					return true;
				}
			}
		}

		return false;
	}

	private void createContentMaskUsing3WaySilhouetteIntersection()
	{
		try (Image topSilhouette = Image.create(image.getWidth(), image.getHeight(), ImageType.Binary);
				Image leftSilhouette = Image.create(image.getWidth(), image.getHeight(), ImageType.Binary);
				Image rightSilhouette = Image.create(image.getWidth(), image.getHeight(), ImageType.Binary))
		{
			Image bottomSilhouette = null;

		try (PixelReader imagePixels = image.createPixelReader())
		{
			// Top
			{
				List<IntPoint> points = new ArrayList<>();
				for (int x = 0; x < image.getWidth(); x++)
				{
					IntPoint point = findUppermostOpaquePixel(imagePixels, image.getHeight(), x);
					if (point != null)
					{
						addToContentBounds(point);
						if (points.isEmpty())
						{
							points.add(new IntPoint(x, image.getHeight()));
						}
						points.add(point);
					}
				}

				if (points.size() < 3)
				{
					contentMask = Image.create(image.getWidth(), image.getHeight(), ImageType.Binary);
					return;
				}
				points.add(new IntPoint(points.get(points.size() - 1).x, image.getHeight()));
				drawWhitePolygonFromPoints(topSilhouette, points);
			}

			// Bottom
			if (iconType == IconType.cities)
			{
				bottomSilhouette = Image.create(image.getWidth(), image.getHeight(), ImageType.Binary);
				List<IntPoint> points = new ArrayList<>();
				for (int x = 0; x < image.getWidth(); x++)
				{
					IntPoint point = findLowestOpaquePixel(imagePixels, image.getHeight(), x);
					if (point != null)
					{
						addToContentBounds(point);
						if (points.isEmpty())
						{
							points.add(new IntPoint(x, 0));
						}
						points.add(point);
					}
				}

				if (points.size() < 3)
				{
					contentMask = Image.create(image.getWidth(), image.getHeight(), ImageType.Binary);
					return;
				}
				points.add(new IntPoint(points.get(points.size() - 1).x, 0));
				drawWhitePolygonFromPoints(bottomSilhouette, points);
			}

			// Left side
			{
				List<IntPoint> points = new ArrayList<>();
				for (int y = 0; y < image.getHeight(); y++)
				{
					IntPoint point = findLeftmostOpaquePixel(imagePixels, image.getWidth(), y);
					addToContentBounds(point);
					if (point != null)
					{
						if (points.isEmpty())
						{
							points.add(new IntPoint(image.getWidth(), y));
						}
						points.add(point);
					}
				}

				points.add(new IntPoint(image.getWidth(), points.get(points.size() - 1).y));
				drawWhitePolygonFromPoints(leftSilhouette, points);
			}

			// Right side
			{
				List<IntPoint> points = new ArrayList<>();
				for (int y = 0; y < image.getHeight(); y++)
				{
					IntPoint point = findRightmostOpaquePixel(imagePixels, image.getWidth(), y);
					if (point != null)
					{
						if (points.isEmpty())
						{
							points.add(new IntPoint(0, y));
						}
						points.add(point);
					}
				}
				points.add(new IntPoint(0, points.get(points.size() - 1).y));
				drawWhitePolygonFromPoints(rightSilhouette, points);
			}
		}

			// The mask image is the intersection of the 3 or 4 silhouettes.

			contentMask = Image.create(image.getWidth(), image.getHeight(), ImageType.Binary);
			try (PixelReader topPixels = topSilhouette.createPixelReader();
					PixelReader leftPixels = leftSilhouette.createPixelReader();
					PixelReader rightPixels = rightSilhouette.createPixelReader();
					PixelReader bottomPixels = bottomSilhouette != null ? bottomSilhouette.createPixelReader() : null;
					PixelReaderWriter contentMaskPixels = contentMask.createPixelReaderWriter())
			{
				for (int x = 0; x < contentMask.getWidth(); x++)
				{
					for (int y = 0; y < contentMask.getHeight(); y++)
					{
						if (topPixels.getGrayLevel(x, y) > 0 && leftPixels.getGrayLevel(x, y) > 0 && rightPixels.getGrayLevel(x, y) > 0 && (bottomPixels == null || bottomPixels.getGrayLevel(x, y) > 0))
						{
							contentMaskPixels.setGrayLevel(x, y, 255);
						}
					}
				}
			}
			if (bottomSilhouette != null)
			{
				bottomSilhouette.close();
			}
		}
	}

	public synchronized Image cropToContent()
	{
		getOrCreateContentBounds();
		return image.getSubImage(contentBounds);
	}

	public synchronized Image cropToContent(Image imageToCrop)
	{
		getOrCreateContentBounds();
		return imageToCrop.getSubImage(contentBounds);
	}

	public synchronized IntRectangle getOrCreateContentBounds()
	{
		getOrCreateContentMask();
		return contentBounds;
	}

	/**
	 * Finds the lower-most y pixel value for the given x for which the content mask is not black.
	 * 
	 * @param x
	 * @return A y value, or null if the content mask is black for all y values of the given x.
	 */
	public synchronized Integer getContentYStart(int x)
	{
		if (contentYStarts == null)
		{
			createContentYStarts();
		}

		if (x < 0 || x >= contentYStarts.length)
		{
			return 0;
		}
		return contentYStarts[x];
	}

	private synchronized void createContentYStarts()
	{
		getOrCreateContentMask();
		contentYStarts = new Integer[image.getWidth()];
		try (PixelReader contentMaskPixels = contentMask.createPixelReader())
		{
			for (int x : new Range(image.getWidth()))
			{
				if (x < contentBounds.x)
				{
					contentYStarts[x] = null;
				}
				else if (x > contentBounds.x + contentBounds.width)
				{
					contentYStarts[x] = null;
				}
				else
				{
					IntPoint point = findLowermostOpaquePixelOnMask(contentMaskPixels, contentMask.getHeight(), x);
					if (point != null)
					{
						assert point.x == x;
						contentYStarts[x] = point.y;
					}
					else
					{
						contentYStarts[x] = null;
					}
				}
			}
		}
	}

	private void addToContentBounds(IntPoint point)
	{
		if (point == null)
		{
			return;
		}

		if (contentBounds == null)
		{
			contentBounds = new IntRectangle(point.x, point.y, 0, 0);
		}
		else
		{
			contentBounds = contentBounds.add(point.x, point.y);
		}
	}

	public synchronized Image getOrCreateShadingMask()
	{
		if (image == null)
		{
			return null;
		}

		synchronized (image)
		{
			if (shadingMask == null)
			{
				createShadingMask();
			}
		}

		return shadingMask;
	}

	private synchronized void createShadingMask()
	{
		getOrCreateContentMask();

		if (iconType == IconType.trees || iconType == IconType.decorations || iconType == IconType.cities)
		{
			// These icons, in my opinion, look better without the feathered shading along the bottom that the shading mask gives.
			// Use a solid black image.
			shadingMask = Image.create(contentMask.getWidth(), contentMask.getHeight(), ImageType.Grayscale8Bit);
			return;
		}

		// Create a line along the bottom of the content of the image.
		List<IntPoint> points = new ArrayList<>();
		try (PixelReader contentMaskPixels = contentMask.createPixelReader())
		{
			for (int x = 0; x < contentMask.getWidth(); x++)
			{
				IntPoint point = findLowermostOpaquePixelOnMask(contentMaskPixels, contentMask.getHeight(), x);
				if (point != null)
				{
					if (points.isEmpty())
					{
						points.add(point);
					}
					points.add(point);
				}
			}
		}

		int contentHeight = contentBounds.height;
		float lineWidth = Math.max(1f, contentHeight * 0.01f);
		int blurSize = contentHeight / 4;
		int padding = blurSize;

		// Make the line extend to the edges of the image so that the edges of the shading mask aren't darker.
		if (points.size() > 0)
		{
			points.add(0, new IntPoint((int) (-Math.ceil(padding + lineWidth)), points.get(0).y));
			points.add(new IntPoint(contentMask.getWidth() - 1 + (int) (Math.ceil(padding + lineWidth)), points.get(points.size() - 1).y));
		}

		try (Image bottomSilhouette = Image.create(contentMask.getWidth() + padding * 2, contentMask.getHeight() + padding, ImageType.Binary))
		{
			try (Painter p = bottomSilhouette.createPainter())
			{
				p.setColor(Color.white);
				assert contentHeight >= 0;
				p.setBasicStroke(lineWidth);
				p.translate(padding, 0);
				p.drawPolyline(points);
			}

			// Blur the line, up to a fraction of the height of the content
			float[][] kernel = ImageHelper.createGaussianKernel(blurSize);

			// Here I used to just do convolution to blur bottomSilhouette and then maximize the contrast on that result, but the problems is
			// this often leads to the bottom of the shading mask being not quit white because some parts of the blurred line are brighter than
			// others, depending on the shape of the line. The result was that the bottom of the shading mask had a visible drop-off when
			// coloring icons,
			// which didn't look good.
			// My solution is to run the convolution with the contrast maximized, then find the pixel values where the blurred line is at its
			// darkest, then raise the contrast to make that value equal to targetMaxMinMaxPixelValue.
			Tuple2<ComplexArray, Image> tuple = ImageHelper.convolveGrayscaleThenSetContrast(bottomSilhouette, kernel, true, 0f, 1f, true);
			try (Image blurredLine = tuple.getSecond())
			{
				try (Image withoutPadding = blurredLine.copySubImage(new IntRectangle(padding, 0, contentMask.getWidth(), contentMask.getHeight())))
				{
					int minMax = getLowestHighestGrayLevelInContentColumns(withoutPadding, points);

					// THis is approximately the gray level pixel will have at the bottom of the color mask.
					final float targetMaxMinMaxPixelValue = 230f;

					// Re-use the complex array from the last run to avoid having to re-do the convolution.
					try (Image blurredLine2 = ImageHelper.realToImage(tuple.getFirst(), blurredLine.getType(), blurredLine.getWidth(), blurredLine.getHeight(), true, 0f,
							targetMaxMinMaxPixelValue / minMax, false, 0f))
					{
						try (Image withoutPadding2 = blurredLine2.copySubImage(new IntRectangle(padding, 0, contentMask.getWidth(), contentMask.getHeight())))
						{
							// Use the content mask to set non-content pixels to zero.
							shadingMask = ImageHelper.maskWithColor(withoutPadding2, Color.black, contentMask, false);
						}
					}
				}
			}
		}
	}

	private int getLowestHighestGrayLevelInContentColumns(Image image, List<IntPoint> columnsToSearch)
	{
		int minMax = 255;
		for (IntPoint point : columnsToSearch)
		{
			if (point.x < 0 || point.x >= image.getWidth())
			{
				continue;
			}
			int colMax = getHighestGrayLevelInColumn(image, point.x);
			if (colMax < minMax)
			{
				minMax = colMax;
			}
		}
		return minMax;
	}

	/**
	 * Gets the highest grayscale pixel value in the given column in the given image.
	 * 
	 * @param image
	 *            The image to search
	 * @param column
	 *            The x-coordinate of the column to scan.
	 * @return The maximum gray level found in that column.
	 * @throws IllegalArgumentException
	 *             if the column index is out of bounds or the image is not grayscale/binary.
	 */
	private int getHighestGrayLevelInColumn(Image image, int column)
	{
		if (!image.isGrayscaleOrBinary())
		{
			throw new IllegalArgumentException("Image must be grayscale or binary to use this method.");
		}
		if (column < 0 || column >= image.getWidth())
		{
			throw new IllegalArgumentException("Column index out of bounds: " + column);
		}

		int max = Integer.MIN_VALUE;
		try (PixelReader imagePixels = image.createPixelReader())
		{
			for (int y = 0; y < image.getHeight(); y++)
			{
				int value = imagePixels.getGrayLevel(column, y);
				if (value > max)
				{
					max = value;
				}
			}
		}
		return max;
	}

	public synchronized Image getOrCreateColorMask()
	{
		if (image == null)
		{
			return null;
		}

		if (colorMask == null)
		{
			createColorMask();
		}

		return colorMask;
	}

	private synchronized void createColorMask()
	{
		getOrCreateContentMask();

		// Convert binary to 8-bit grayscale
		colorMask = Image.create(contentMask.getWidth(), contentMask.getHeight(), ImageType.Grayscale8Bit);
		try (Painter p = colorMask.createPainter())
		{
			p.drawImage(contentMask, 0, 0);
		}

		// Only these types use the shading mask in creating the color mask.
		if (iconType == IconType.mountains || iconType == IconType.hills || iconType == IconType.sand)
		{
			ImageHelper.subtract(colorMask, getOrCreateShadingMask());
		}
	}

	private static void drawWhitePolygonFromPoints(Image image, List<IntPoint> points)
	{
		int[] xPoints = new int[points.size()];
		int[] yPoints = new int[points.size()];
		for (int i : new Range(points.size()))
		{
			IntPoint point = points.get(i);
			xPoints[i] = point.x;
			yPoints[i] = point.y;
		}

		try (Painter p = image.createPainter())
		{
			p.setColor(Color.white);
			p.fillPolygon(xPoints, yPoints);
		}
	}

	private static IntPoint findLowermostOpaquePixelOnMask(PixelReader contentMaskPixels, int height, int x)
	{
		for (int y = height - 1; y >= 0; y--)
		{
			if (contentMaskPixels.getGrayLevel(x, y) > 0)
			{
				return new IntPoint(x, y);
			}
		}

		return null;
	}

	private static IntPoint findUppermostOpaquePixel(PixelReader iconPixels, int height, int x)
	{
		for (int y = 0; y < height; y++)
		{
			int alpha = iconPixels.getAlpha(x, y);
			if (alpha >= opaqueThreshold)
			{
				return new IntPoint(x, y);
			}
		}

		return null;
	}

	private static IntPoint findLowestOpaquePixel(PixelReader iconPixels, int height, int x)
	{
		for (int y = height - 1; y >= 0; y--)
		{
			int alpha = iconPixels.getAlpha(x, y);
			if (alpha >= opaqueThreshold)
			{
				return new IntPoint(x, y);
			}
		}

		return null;
	}

	private static IntPoint findLeftmostOpaquePixel(PixelReader iconPixels, int width, int y)
	{
		for (int x = 0; x < width; x++)
		{
			int alpha = iconPixels.getAlpha(x, y);
			if (alpha >= opaqueThreshold)
			{
				return new IntPoint(x, y);
			}
		}

		return null;
	}

	private static IntPoint findRightmostOpaquePixel(PixelReader iconPixels, int width, int y)
	{
		for (int x = width - 1; x >= 0; x--)
		{
			int alpha = iconPixels.getAlpha(x, y);
			if (alpha >= opaqueThreshold)
			{
				return new IntPoint(x, y);
			}
		}

		return null;
	}

	public static IntRectangle calcScaledContentBounds(Image originalContentMask, IntRectangle originalContentBounds, int scaledWidth, int scaledHeight)
	{
		final double xScale = (((double) scaledWidth / originalContentMask.getWidth()));
		final double yScale = (((double) scaledHeight / originalContentMask.getHeight()));

		IntRectangle scaledContentBounds = new Rectangle(originalContentBounds.x * (xScale), originalContentBounds.y * yScale, originalContentBounds.width * xScale,
				originalContentBounds.height * yScale).toEnclosingIntRectangle();
		return scaledContentBounds;
	}

	public String createFileIdentifier()
	{
		return artPack + File.separator + iconType + File.separator + groupId + File.separator + fileNameWithoutParametersOrExtension;
	}
}
